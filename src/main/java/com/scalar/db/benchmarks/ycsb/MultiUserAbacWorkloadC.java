package com.scalar.db.benchmarks.ycsb;

import static com.scalar.db.benchmarks.ycsb.YcsbCommon.CONFIG_NAME;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.OPS_PER_TX;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getPassword;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getRecordCount;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getUserCount;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getUserName;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.prepareGet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import com.scalar.db.api.DistributedTransaction;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.api.Result;
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
    private final int recordCount;
    private final int opsPerTx;
    private final int userCount;
    private final ThreadLocal<KeyRange> threadLocalKeyRange;

    // メトリクス
    private final LongAdder transactionRetryCount = new LongAdder();
    private final LongAdder authorizationSuccessCount = new LongAdder();
    private final LongAdder authorizationFailureCount = new LongAdder();
    private final LongAdder transactionExecutionCount = new LongAdder();
    private final LongAdder executeEachCallCount = new LongAdder();

    // ユーザー管理
    private final List<DistributedTransactionManager> userManagers = new ArrayList<>();
    private final ThreadLocal<Integer> threadLocalUserId;

    public MultiUserAbacWorkloadC(Config config) {
        super(config);
        this.recordCount = getRecordCount(config);
        this.opsPerTx = (int) config.getUserLong(CONFIG_NAME, OPS_PER_TX, DEFAULT_OPS_PER_TX);
        this.userCount = getUserCount(config);

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
        logInfo("  Record count: " + recordCount);
        logInfo("  Ops per transaction: " + opsPerTx);
        logInfo("  User count: " + userCount);
        logInfo("  Concurrency: " + config.getConcurrency());
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

            Properties userProps = new Properties();
            userProps.putAll(baseProps);
            userProps.setProperty("scalar.db.username", username);
            userProps.setProperty("scalar.db.password", password);

            TransactionFactory factory = TransactionFactory.create(userProps);
            DistributedTransactionManager userManager = factory.getTransactionManager();
            userManagers.add(userManager);

            logInfo("Created transaction manager for user: " + username);
        }

        if (userManagers.isEmpty()) {
            throw new IllegalStateException("No user transaction managers were created. Check your configuration.");
        } else {
            logInfo("Created " + userManagers.size() + " user transaction managers");
        }
    }

    @Override
    public void executeEach() throws TransactionException {
        executeEachCallCount.increment();

        KeyRange range = threadLocalKeyRange.get();
        Random random = ThreadLocalRandom.current();

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
            throw new IllegalStateException("Invalid user index: " + userIndex + ". Check your configuration.");
        }

        // トランザクション実行
        while (true) {
            DistributedTransaction transaction = txManager.start();
            try {
                for (Integer userId : userIds) {
                    // 実際のREAD操作
                    Optional<Result> result = transaction.get(prepareGet(userId));
                    int threadId = threadLocalUserId.get();
                    if (result.isPresent()) {
                        // 認証成功のメトリクスを更新
                        authorizationSuccessCount.increment();
                        logDebug("AUTH_SUCCESS: ThreadID: " + threadId + ", userId: " + userId);
                    } else {
                        // 認証失敗のメトリクスを更新
                        authorizationFailureCount.increment();
                        logDebug("AUTH_FAILURE: ThreadID: " + threadId + ", userId: " + userId);
                    }
                }
                transaction.commit();
                transactionExecutionCount.increment();
                break;
            } catch (CrudConflictException | CommitConflictException e) {
                transaction.abort();
                transactionRetryCount.increment();
            } catch (Exception e) {
                transaction.abort();
                throw e;
            }
        }
    }

    @Override
    public void close() {
        Exception firstException = null;

        // ユーザートランザクションマネージャーを閉じる
        for (DistributedTransactionManager userManager : userManagers) {
            if (userManager != null) {
                try {
                    userManager.close();
                } catch (Exception e) {
                    if (firstException == null) {
                        firstException = e;
                    } else {
                        firstException.addSuppressed(e);
                    }
                }
            }
        }

        // メトリクスの出力
        JsonObjectBuilder stateBuilder = Json.createObjectBuilder()
                .add("transaction-retry-count", transactionRetryCount.toString())
                .add("user-count", String.valueOf(userCount));
        // 認証成功と失敗のカウントを追加
        stateBuilder
                .add("authorization-success-count", authorizationSuccessCount.toString())
                .add("authorization-failure-count", authorizationFailureCount.toString())
                .add("total-operations",
                        String.valueOf(authorizationSuccessCount.sum() + authorizationFailureCount.sum()))
                .add("transaction-execution-count", transactionExecutionCount.toString())
                .add("execute-each-call-count", executeEachCallCount.toString());
        setState(stateBuilder.build());

        // 例外が発生していた場合は再スロー
        if (firstException != null) {
            throw new RuntimeException("Failed to close resources", firstException);
        }
    }

    /**
     * 各スレッドに割り当てるキー範囲を計算します。
     * 
     * @param threadId    スレッドID
     * @param userCount   合計ユーザー数（スレッド数）
     * @param recordCount 総レコード数
     * @return キー範囲
     */
    private KeyRange calculateKeyRange(int threadId, int userCount, int recordCount) {
        if (threadId >= userCount) {
            throw new IllegalArgumentException("Thread ID must be less than user count");
        }

        int rangeSize = recordCount / userCount;
        // 最後のスレッドには端数も含める
        if (threadId == userCount - 1) {
            return new KeyRange(
                    threadId * rangeSize,
                    recordCount - 1);
        } else {
            return new KeyRange(
                    threadId * rangeSize,
                    (threadId + 1) * rangeSize - 1);
        }
    }

    /**
     * スレッドに割り当てられたキー範囲を表すクラス
     */
    private static class KeyRange {
        final int startKey; // 範囲の開始キー（含む）
        final int endKey; // 範囲の終了キー（含む）

        KeyRange(int startKey, int endKey) {
            this.startKey = startKey;
            this.endKey = endKey;
        }
    }
}
