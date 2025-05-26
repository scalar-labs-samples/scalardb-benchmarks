package com.scalar.db.benchmarks.ycsb;

import static com.scalar.db.benchmarks.ycsb.YcsbCommon.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import com.scalar.db.api.DistributedTransaction;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.benchmarks.Common;
import com.scalar.db.config.DatabaseConfig;
import com.scalar.db.exception.transaction.CommitConflictException;
import com.scalar.db.exception.transaction.CrudConflictException;
import com.scalar.db.exception.transaction.TransactionException;
import com.scalar.db.service.TransactionFactory;
import com.scalar.kelpie.config.Config;
import com.scalar.kelpie.modules.TimeBasedProcessor;

/**
 * ABAC対応のマルチユーザーWorkload C: READ操作の許可/拒否パターンをベンチマーク
 * 既存のMultiUserWorkloadCを拡張してABAC機能を追加
 */
public class MultiUserAbacWorkloadC extends TimeBasedProcessor {
    private static final long DEFAULT_OPS_PER_TX = 2; // two read operations
    private final DistributedTransactionManager manager;
    private final int recordCount;
    private final int opsPerTx;
    private final int userCount;
    private final ThreadLocal<KeyRange> threadLocalKeyRange;

    // ABAC関連の設定
    private final boolean abacEnabled;
    private final AttributeAssignmentStrategy attributeStrategy;
    private final String[] attributeValues;
    private final String attributeType;

    // メトリクス
    private final LongAdder transactionRetryCount = new LongAdder();
    private final LongAdder authorizationSuccessCount = new LongAdder();
    private final LongAdder authorizationFailureCount = new LongAdder();
    private final LongAdder abacOperationTime = new LongAdder();

    // ユーザー管理
    private final List<DistributedTransactionManager> userManagers = new ArrayList<>();
    private final ThreadLocal<Integer> threadLocalUserId;

    public MultiUserAbacWorkloadC(Config config) {
        super(config);
        this.recordCount = getRecordCount(config);
        this.opsPerTx = (int) config.getUserLong(CONFIG_NAME, OPS_PER_TX, DEFAULT_OPS_PER_TX);
        this.userCount = getUserCount(config);

        // ABAC設定の初期化
        this.abacEnabled = isAbacEnabled(config);
        this.attributeType = getAbacAttributeType(config);
        this.attributeValues = getAbacAttributeValues(config);

        // 属性割り当て戦略の初期化
        String strategyName = getAbacStrategy(config);
        if (STRATEGY_LOAD_BALANCED.equals(strategyName)) {
            this.attributeStrategy = new LoadBalancedStrategy();
        } else {
            this.attributeStrategy = new RandomStrategy();
        }

        // 管理者トランザクションマネージャー
        this.manager = Common.getTransactionManager(config);

        // ユーザー用のトランザクションマネージャーを作成
        createUserManagers(config);

        // スレッドローカル変数の初期化
        this.threadLocalUserId = new ThreadLocal<>();
        this.threadLocalKeyRange = ThreadLocal.withInitial(() -> {
            int threadId = Math.abs(Thread.currentThread().getName().hashCode() % userCount);
            threadLocalUserId.set(threadId);
            return calculateKeyRange(threadId, userCount, recordCount);
        });

        logInfo("ABAC Multi-User Workload C initialized:");
        logInfo("  ABAC enabled: " + abacEnabled);
        if (abacEnabled) {
            logInfo("  Attribute type: " + attributeType);
            logInfo("  Strategy: " + strategyName);
            logInfo("  Attribute values: " + String.join(", ", attributeValues));
        }
    }

    /**
     * 各ユーザー用のトランザクションマネージャーを作成
     */
    private void createUserManagers(Config config) {
        DatabaseConfig dbConfig = Common.getDatabaseConfig(config);
        Properties baseProps = dbConfig.getProperties();

        String contactPoints = baseProps.getProperty("scalar.db.contact_points", "");
        logInfo("Creating user managers for endpoint: " + contactPoints);

        for (int i = 0; i < userCount; i++) {
            String username = getUserName(i);
            String password = getPassword(i);

            try {
                Properties userProps = new Properties();
                userProps.putAll(baseProps);
                userProps.setProperty("scalar.db.username", username);
                userProps.setProperty("scalar.db.password", password);

                TransactionFactory factory = TransactionFactory.create(userProps);
                DistributedTransactionManager userManager = factory.getTransactionManager();
                userManagers.add(userManager);

                logInfo("Created transaction manager for user: " + username);
            } catch (Exception e) {
                logWarn("Failed to create transaction manager for user " + username + ": " + e.getMessage());
            }
        }

        if (userManagers.isEmpty()) {
            logWarn("No user transaction managers created, will use admin manager");
        } else {
            logInfo("Created " + userManagers.size() + " user transaction managers");
        }
    }

    @Override
    public void executeEach() throws TransactionException {
        KeyRange range = threadLocalKeyRange.get();
        Random random = ThreadLocalRandom.current();
        long startTime = System.nanoTime();

        // READ操作の対象キーをランダムに選択
        List<Integer> userIds = new ArrayList<>(opsPerTx);
        for (int i = 0; i < opsPerTx; ++i) {
            int key = range.startKey + random.nextInt(range.endKey - range.startKey + 1);
            userIds.add(key);
        }

        // ユーザーのトランザクションマネージャーを取得
        Integer userIndex = threadLocalUserId.get();
        DistributedTransactionManager txManager;
        if (userIndex != null && userIndex < userManagers.size() && userManagers.get(userIndex) != null) {
            txManager = userManagers.get(userIndex);
        } else {
            txManager = manager;
            logWarn("Using admin transaction manager for thread " + Thread.currentThread().getName());
        }

        // トランザクション実行
        boolean authorizationSuccess = true;
        while (true) {
            DistributedTransaction transaction = txManager.start();
            try {
                for (Integer userId : userIds) {
                    if (abacEnabled) {
                        // ABAC有効時: 属性ベースアクセス制御をシミュレート
                        authorizationSuccess = simulateAbacCheck(userIndex, userId);
                        if (!authorizationSuccess) {
                            authorizationFailureCount.increment();
                            // 権限エラーの場合はスキップ（実際のABACではTransactionExceptionがスローされる）
                            continue;
                        }
                        authorizationSuccessCount.increment();
                    }

                    // 実際のREAD操作
                    transaction.get(prepareGet(userId));
                }
                transaction.commit();
                break;
            } catch (CrudConflictException | CommitConflictException e) {
                transaction.abort();
                transactionRetryCount.increment();
            } catch (Exception e) {
                transaction.abort();
                throw e;
            }
        }

        // ABAC処理時間を記録
        if (abacEnabled) {
            long processingTime = System.nanoTime() - startTime;
            abacOperationTime.add(processingTime / 1000); // マイクロ秒に変換
        }
    }

    /**
     * ABAC権限チェック
     * 実際のScalarDBのABAC機能では、トランザクション実行時に自動的に権限チェックが行われます。
     * このメソッドは主にメトリクス収集とデバッグ情報のために使用されます。
     */
    private boolean simulateAbacCheck(int userId, int recordId) {
        if (!abacEnabled) {
            return true;
        }

        // 属性情報を取得（主にログ・メトリクス目的）
        String userAttribute = attributeStrategy.assignUserAttribute(userId, attributeValues);
        String dataAttribute = attributeStrategy.assignDataAttribute(recordId, attributeValues);

        // 実際のABACでは、ScalarDBトランザクション実行時に自動的に権限チェックが行われ、
        // 権限がない場合はTransactionExceptionがスローされます。
        // ここでは期待される権限状態をシミュレートしてメトリクス収集に使用します。
        boolean hasPermission = userAttribute.equals(dataAttribute);

        // デバッグ情報とメトリクス用のログ
        if (!hasPermission) {
            logDebug("ABAC permission expected to be denied: user(" + userId + ")=" + userAttribute +
                    ", data(" + recordId + ")=" + dataAttribute);
        } else {
            logDebug("ABAC permission expected to be granted: user(" + userId + ")=" + userAttribute +
                    ", data(" + recordId + ")=" + dataAttribute);
        }

        return hasPermission;
    }

    @Override
    public void close() {
        try {
            // ユーザートランザクションマネージャーを閉じる
            for (DistributedTransactionManager userManager : userManagers) {
                try {
                    if (userManager != null) {
                        userManager.close();
                    }
                } catch (Exception e) {
                    logWarn("Failed to close user transaction manager", e);
                }
            }

            // 管理者トランザクションマネージャーを閉じる
            manager.close();
        } catch (Exception e) {
            logWarn("Failed to close the transaction manager", e);
        }

        // メトリクスの出力
        JsonObjectBuilder stateBuilder = Json.createObjectBuilder()
                .add("transaction-retry-count", transactionRetryCount.toString())
                .add("user-count", String.valueOf(userCount));

        if (abacEnabled) {
            stateBuilder
                    .add("abac-enabled", "true")
                    .add("authorization-success-count", authorizationSuccessCount.toString())
                    .add("authorization-failure-count", authorizationFailureCount.toString())
                    .add("total-operations",
                            String.valueOf(authorizationSuccessCount.sum() + authorizationFailureCount.sum()));

            long totalOperations = authorizationSuccessCount.sum() + authorizationFailureCount.sum();
            if (totalOperations > 0) {
                double successRate = (double) authorizationSuccessCount.sum() / totalOperations * 100;
                stateBuilder.add("authorization-success-rate", String.format("%.2f%%", successRate));
            }

            if (abacOperationTime.sum() > 0) {
                stateBuilder.add("avg-abac-processing-time-microseconds",
                        String.valueOf(abacOperationTime.sum() / Math.max(1, totalOperations)));
            }
        } else {
            stateBuilder.add("abac-enabled", "false");
        }

        setState(stateBuilder.build());
    }

    /**
     * 各スレッドに割り当てるキー範囲を計算
     */
    private KeyRange calculateKeyRange(int threadId, int userCount, int recordCount) {
        if (threadId >= userCount) {
            throw new IllegalArgumentException("Thread ID must be less than user count");
        }

        int rangeSize = recordCount / userCount;
        if (threadId == userCount - 1) {
            return new KeyRange(threadId * rangeSize, recordCount - 1);
        } else {
            return new KeyRange(threadId * rangeSize, (threadId + 1) * rangeSize - 1);
        }
    }

    /**
     * スレッドに割り当てられたキー範囲
     */
    private static class KeyRange {
        final int startKey;
        final int endKey;

        KeyRange(int startKey, int endKey) {
            this.startKey = startKey;
            this.endKey = endKey;
        }
    }
}
