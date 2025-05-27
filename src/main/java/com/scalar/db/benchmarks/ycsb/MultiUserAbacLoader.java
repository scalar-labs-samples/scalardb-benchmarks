package com.scalar.db.benchmarks.ycsb;

import static com.scalar.db.benchmarks.ycsb.YcsbCommon.DATA_TAG;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.NAMESPACE;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.PAYLOAD;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.TABLE;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.YCSB_KEY;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.generateDataTag;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getAbacAttributeValueRandom;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getAbacAttributeValues;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getLoadConcurrency;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getPassword;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getPayloadSize;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getRecordCount;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getUserCount;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.getUserName;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.prepareInsertWithDataTag;
import static com.scalar.db.benchmarks.ycsb.YcsbCommon.randomFastChars;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;

import com.scalar.db.api.AbacAdmin;
import com.scalar.db.api.AuthAdmin.Privilege;
import com.scalar.db.api.DistributedTransaction;
import com.scalar.db.api.DistributedTransactionAdmin;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.api.TableMetadata;
import com.scalar.db.benchmarks.Common;
import com.scalar.db.benchmarks.ycsb.YcsbCommon.AttributeType;
import com.scalar.db.config.DatabaseConfig;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.io.DataType;
import com.scalar.db.service.TransactionFactory;
import com.scalar.kelpie.config.Config;
import com.scalar.kelpie.modules.PreProcessor;

/**
 * ABAC専用マルチユーザーローダー
 * ABAC環境でのベンチマーク用にinsertを使用したデータロードを行う
 */
public class MultiUserAbacLoader extends PreProcessor {
    private static final int REPORTING_INTERVAL = 10000;

    private final DatabaseConfig dbConfig;
    private final int recordCount;
    private final int loadConcurrency;
    private final int payloadSize;
    private final int userCount;
    private final AtomicBoolean canceled = new AtomicBoolean(false);
    private final AtomicInteger numFinished = new AtomicInteger(0);

    public MultiUserAbacLoader(Config config) {
        super(config);

        dbConfig = Common.getDatabaseConfig(config);
        loadConcurrency = getLoadConcurrency(config);
        recordCount = getRecordCount(config);
        payloadSize = getPayloadSize(config);
        userCount = getUserCount(config);

        logInfo("ABAC Multi-User Loader initialized:");
        logInfo("  User count: " + userCount);
        logInfo("  Record count: " + recordCount);
    }

    @Override
    public void execute() {
        try {
            logInfo("Starting ABAC MultiUserLoader");
            ExecutorService es = Executors.newFixedThreadPool(loadConcurrency);

            // テーブル削除・再作成（毎回フレッシュなテーブルで開始）
            dropAndRecreateTable();

            // ScalarDBユーザーの作成
            createScalarDbUsers();

            // ABAC環境のセットアップ
            setupAbacEnvironment();

            // レコードのロード（insertを使用）
            loadRecords(es);

            logInfo("Finished ABAC loading");

        } catch (Exception e) {
            logError("ABAC loader error", e);
            throw new RuntimeException("ABAC loader failed", e);
        }

        setState(Json.createObjectBuilder().build());
    }

    @Override
    public void close() {
        logInfo("ABAC loader cleanup completed");
    }

    /**
     * テーブルを削除して再作成します
     * ベンチマーク開始時に毎回フレッシュなテーブルでテストを開始するために使用
     */
    private void dropAndRecreateTable() throws Exception {
        logInfo("Dropping and recreating table: " + NAMESPACE + "." + TABLE);

        TransactionFactory factory = TransactionFactory.create(dbConfig.getProperties());
        DistributedTransactionAdmin admin = factory.getTransactionAdmin();

        try {
            // 既存のテーブルを削除（存在しない場合のエラーは無視）
            logInfo("Dropping existing table: " + NAMESPACE + "." + TABLE);
            admin.dropTable(NAMESPACE, TABLE);
            logInfo("Successfully dropped table: " + NAMESPACE + "." + TABLE);
        } catch (Exception e) {
            logInfo("Table " + NAMESPACE + "." + TABLE + " might not exist, proceeding to create it");
        }

        try {
            // 名前空間を作成（存在しない場合）
            admin.createNamespace(NAMESPACE);
            logInfo("Created namespace: " + NAMESPACE);
        } catch (Exception e) {
            logInfo("Namespace " + NAMESPACE + " might already exist, continuing");
        }

        // テーブルを作成（ABAC用にdata_tagカラムを追加）
        logInfo("Creating table: " + NAMESPACE + "." + TABLE);
        TableMetadata tableMetadata = TableMetadata.newBuilder()
                .addPartitionKey(YCSB_KEY)
                .addColumn(YCSB_KEY, DataType.INT)
                .addColumn(PAYLOAD, DataType.TEXT)
                .build();

        try {
            admin.createTable(NAMESPACE, TABLE, tableMetadata);
            logInfo("Successfully created table: " + NAMESPACE + "." + TABLE + " with data_tag column for ABAC");
        } finally {
            admin.close();
        }
    }

    /**
     * ScalarDBユーザーを作成します
     */
    private void createScalarDbUsers() throws Exception {
        logInfo("Creating ScalarDB users: " + userCount);

        TransactionFactory factory = TransactionFactory.create(dbConfig.getProperties());
        DistributedTransactionAdmin admin = factory.getTransactionAdmin();

        try {
            // 各ユーザーを作成
            for (int i = 0; i < userCount; i++) {
                String username = getUserName(i);
                String password = getPassword(i);

                // 既存ユーザーを削除（存在しない場合のエラーは無視）
                logInfo("Dropping existing user: " + username);
                try {
                    admin.dropUser(username);
                } catch (Exception e) {
                    // ユーザーが存在しない場合は無視
                    logInfo("User " + username + " might not exist, proceeding to create it");
                }

                // ユーザー作成
                logInfo("Creating user: " + username);
                admin.createUser(username, password);

                // テーブルへの権限付与
                logInfo("Granting table privileges to " + username + " on " + NAMESPACE + "." + TABLE);
                admin.grant(username, NAMESPACE, TABLE, Privilege.READ, Privilege.WRITE);

                logInfo("Successfully created user: " + username + " with all required permissions");
            }
        } finally {
            admin.close();
        }

        logInfo("Created " + userCount + " ScalarDB users");
    }

    /**
     * ABAC環境のセットアップ
     * AbacAdmin APIを使用してABACポリシーとアクセス制御を設定
     */
    private void setupAbacEnvironment() {
        AbacAdmin abacAdmin = null;
        try {
            logInfo("Setting up ABAC environment...");

            // AbacAdminインスタンスを取得
            abacAdmin = Common.getAbacAdmin(config);
            logInfo("AbacAdmin instance created successfully");

            // 1. ポリシーの作成
            String policyName = "ycsb_benchmark_policy";
            createAbacPolicy(abacAdmin, policyName);

            // 2. 属性定義の作成
            createAbacAttributes(abacAdmin, policyName);

            // 3. テーブルへのポリシー適用
            applyPolicyToTable(abacAdmin, policyName);

            // 4. ユーザーへの属性割り当て
            assignAttributesToUsers(abacAdmin, policyName);

            logInfo("ABAC environment setup completed successfully");

        } catch (Exception e) {
            logError("Failed to setup ABAC environment", e);
            throw new RuntimeException("ABAC setup failed", e);
        }
    }

    /**
     * ABACポリシーを作成
     */
    private void createAbacPolicy(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating ABAC policy: " + policyName);

        // ポリシーが既に存在するかチェック
        if (abacAdmin.getPolicy(policyName).isPresent()) {
            logInfo("Policy already exists: " + policyName);
        } else {
            // 新しいポリシーを作成（data_tagカラム名を指定）
            abacAdmin.createPolicy(policyName, DATA_TAG);
            logInfo("Policy created successfully: " + policyName + " with data_tag column: " + DATA_TAG);
        }

        // ポリシーが存在する場合でも、必ずEnableを実行
        abacAdmin.enablePolicy(policyName);
        logInfo("Policy enabled successfully: " + policyName);
    }

    /**
     * ABAC属性を作成（レベル/コンパートメント/グループ）
     */
    private void createAbacAttributes(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating ABAC attributes for policy: " + policyName);

        createLevelAttributes(abacAdmin, policyName);
        createCompartmentAttributes(abacAdmin, policyName);
        createGroupAttributes(abacAdmin, policyName);

        logInfo("ABAC attributes created successfully");
    }

    /**
     * レベル属性を作成
     */
    private void createLevelAttributes(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating level attributes");

        String[] attributeValues = getAbacAttributeValues(AttributeType.ATTRIBUTE_TYPE_LEVEL);
        for (int i = 0; i < attributeValues.length; i++) {
            String shortName = attributeValues[i];
            String longName = "Level " + shortName;
            int levelNumber = i + 1;

            // 既に存在するかチェック
            if (abacAdmin.getLevel(policyName, shortName).isPresent()) {
                logInfo("Level already exists: " + shortName + ", skipping creation");
            } else {
                abacAdmin.createLevel(policyName, shortName, longName, levelNumber);
                logInfo("Created level: " + levelNumber + " (" + shortName + " - " + longName + ")");
            }
        }
    }

    /**
     * コンパートメント属性を作成
     */
    private void createCompartmentAttributes(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating compartment attributes");

        String[] attributeValues = getAbacAttributeValues(AttributeType.ATTRIBUTE_TYPE_COMPARTMENT);
        for (String attributeValue : attributeValues) {
            String shortName = attributeValue;
            String longName = "Compartment " + attributeValue;

            // 既に存在するかチェック
            if (abacAdmin.getCompartment(policyName, shortName).isPresent()) {
                logInfo("Compartment already exists: " + shortName + ", skipping creation");
            } else {
                abacAdmin.createCompartment(policyName, shortName, longName);
                logInfo("Created compartment: " + shortName + " (" + longName + ")");
            }
        }
    }

    /**
     * グループ属性を作成
     */
    private void createGroupAttributes(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating group attributes");

        String[] attributeValues = getAbacAttributeValues(AttributeType.ATTRIBUTE_TYPE_GROUP);
        for (String attributeValue : attributeValues) {
            String shortName = attributeValue;
            String longName = "Group " + attributeValue;

            // 既に存在するかチェック
            if (abacAdmin.getGroup(policyName, shortName).isPresent()) {
                logInfo("Group already exists: " + shortName + ", skipping creation");
            } else {
                abacAdmin.createGroup(policyName, shortName, longName, null);
                logInfo("Created group: " + shortName + " (" + longName + ")");
            }
        }
    }

    /**
     * テーブルにポリシーを適用
     */
    private void applyPolicyToTable(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        String namespace = NAMESPACE;
        String tableName = TABLE;
        String tablePolicyName = policyName + "_" + namespace + "_" + tableName;

        logInfo("Applying policy to table: " + namespace + "." + tableName);

        // テーブルポリシーが既に存在するかチェック
        if (abacAdmin.getTablePolicy(tablePolicyName).isPresent()) {
            logInfo("Table policy already exists: " + tablePolicyName);
        } else {
            // 新しいテーブルポリシーを作成
            abacAdmin.createTablePolicy(tablePolicyName, policyName, namespace, tableName);
            logInfo("Table policy created successfully: " + tablePolicyName);
        }

        // テーブルポリシーが存在する場合でも、必ずEnableを実行
        abacAdmin.enableTablePolicy(tablePolicyName);
        logInfo("Table policy enabled successfully: " + tablePolicyName);
    }

    /**
     * ユーザーへの属性割り当て
     */
    private void assignAttributesToUsers(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Assigning attributes to users");

        Random random = new Random();

        for (int i = 0; i < userCount; i++) {
            String username = getUserName(i);

            String userLevelString = getAbacAttributeValueRandom(AttributeType.ATTRIBUTE_TYPE_LEVEL, random);
            String userCompartmentString = getAbacAttributeValueRandom(
                    AttributeType.ATTRIBUTE_TYPE_COMPARTMENT, random);
            String userGroupString = getAbacAttributeValueRandom(AttributeType.ATTRIBUTE_TYPE_GROUP, random);

            abacAdmin.setLevelsToUser(policyName, username, userLevelString, userLevelString, userLevelString);
            logInfo("Assigned level '" + userLevelString + "' to user: " + username);

            abacAdmin.removeCompartmentFromUser(policyName, username, userCompartmentString);
            abacAdmin.addCompartmentToUser(policyName, username, userCompartmentString,
                    AbacAdmin.AccessMode.READ_WRITE, true, true);
            logInfo("Assigned compartment '" + userCompartmentString + "' to user: " + username);

            abacAdmin.removeGroupFromUser(policyName, username, policyName);
            abacAdmin.addGroupToUser(policyName, username, userGroupString,
                    AbacAdmin.AccessMode.READ_WRITE, true, true);
            logInfo("Assigned group '" + userGroupString + "' to user: " + username);
        }
    }

    /**
     * insertを使用したレコードロード（ABAC専用）
     */
    private void loadRecords(ExecutorService es) {
        logInfo("Loading " + recordCount + " records with concurrency " + loadConcurrency + " (using INSERT for ABAC)");
        int numThreads = loadConcurrency;
        int recordsPerThread = recordCount / numThreads;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            final int start = i * recordsPerThread;
            final int end = (i == numThreads - 1) ? recordCount : (i + 1) * recordsPerThread;

            futures.add(
                    CompletableFuture.runAsync(
                            () -> loadRange(threadId, start, end), es));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        try {
            allFutures.join();
        } catch (Exception e) {
            canceled.set(true);
            throw e;
        } finally {
            es.shutdown();
        }
    }

    /**
     * insertを使用したレンジロード（ABAC対応）
     */
    private void loadRange(int threadId, int startInclusive, int endExclusive) {
        long startTime = System.currentTimeMillis();
        logInfo(
                "Thread "
                        + threadId
                        + " loading records from "
                        + startInclusive
                        + " to "
                        + (endExclusive - 1)
                        + " (using INSERT for ABAC)");

        Random random = new Random();
        int remaining = endExclusive - startInclusive;
        int loaded = 0;

        // 管理者として接続してデータロード
        TransactionFactory factory = TransactionFactory.create(dbConfig.getProperties());
        DistributedTransactionManager manager = factory.getTransactionManager();
        try {
            // insertを使用してデータロード
            for (int i = startInclusive; i < endExclusive; i++) {
                char[] payload = new char[payloadSize];
                randomFastChars(random, payload);

                try {
                    DistributedTransaction tx = manager.start();
                    try {
                        // ABACではinsertを使用（putは使用不可）
                        // 各レコードに適切なdata_tagを生成
                        String dataLevelString = getAbacAttributeValueRandom(AttributeType.ATTRIBUTE_TYPE_LEVEL,
                                random);
                        String dataCompartmentString = getAbacAttributeValueRandom(
                                AttributeType.ATTRIBUTE_TYPE_COMPARTMENT, random);
                        String dataGroupString = getAbacAttributeValueRandom(AttributeType.ATTRIBUTE_TYPE_GROUP,
                                random);

                        String dataTag = generateDataTag(
                                dataLevelString,
                                new String[] { dataCompartmentString },
                                new String[] { dataGroupString });

                        tx.insert(prepareInsertWithDataTag(i, String.valueOf(payload), dataTag));
                        tx.commit();

                        // 進捗報告
                        loaded++;
                        remaining--;
                        if (loaded % REPORTING_INTERVAL == 0) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double rate = loaded * 1000.0 / elapsed;
                            logInfo(
                                    "Thread "
                                            + threadId
                                            + " loaded "
                                            + loaded
                                            + " records, "
                                            + remaining
                                            + " remaining"
                                            + String.format(" (%.2f records/second)", rate));
                        }
                    } catch (Exception e) {
                        tx.abort();
                        throw e;
                    }
                } catch (Exception e) {
                    if (canceled.get()) {
                        logInfo("Thread " + threadId + " cancelled");
                        return;
                    }
                    throw e;
                }
            }

            // 完了報告
            int finished = numFinished.incrementAndGet();
            logInfo("Thread " + threadId + " finished loading.");
            if (finished == loadConcurrency) {
                long elapsed = System.currentTimeMillis() - startTime;
                double rate = (endExclusive - startInclusive) * 1000.0 / elapsed;
                logInfo(
                        "Loading complete: " + (endExclusive - startInclusive) + " records loaded in "
                                + elapsed / 1000.0 + " seconds"
                                + String.format(" (%.2f records/second)", rate));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error loading data for thread " + threadId, e);
        } finally {
            try {
                manager.close();
            } catch (Exception e) {
                logWarn("Failed to close transaction manager for thread " + threadId);
            }
        }
    }
}
