package com.scalar.db.benchmarks.ycsb;

import static com.scalar.db.benchmarks.ycsb.YcsbCommon.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.json.Json;

import com.scalar.db.api.AbacAdmin;
import com.scalar.db.benchmarks.Common;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.kelpie.config.Config;

/**
 * ABAC対応のマルチユーザーローダー
 * 既存のMultiUserLoaderを拡張してABAC属性設定機能を追加
 */
public class MultiUserAbacLoader extends MultiUserLoader {

    private final boolean abacEnabled;
    private final AttributeAssignmentStrategy attributeStrategy;
    private final String[] attributeValues;
    private final String attributeType;

    public MultiUserAbacLoader(Config config) {
        super(config);

        this.abacEnabled = isAbacEnabled(config);
        this.attributeType = getAbacAttributeType(config);
        this.attributeValues = getAbacAttributeValues(config);

        // 属性割り当て戦略を初期化
        String strategyName = getAbacStrategy(config);
        if (STRATEGY_LOAD_BALANCED.equals(strategyName)) {
            this.attributeStrategy = new LoadBalancedStrategy();
        } else {
            this.attributeStrategy = new RandomStrategy();
        }

        logInfo("ABAC Multi-User Loader initialized:");
        logInfo("  ABAC enabled: " + abacEnabled);
        if (abacEnabled) {
            logInfo("  Attribute type: " + attributeType);
            logInfo("  Strategy: " + strategyName);
            logInfo("  Attribute values: " + String.join(", ", attributeValues));
        }
    }

    @Override
    public void execute() {
        try {
            logInfo("Starting MultiUserAbacLoader");

            if (abacEnabled) {
                logInfo("ABAC is enabled - setting up ABAC environment");
                setupAbacEnvironment();
            } else {
                logInfo("ABAC is disabled - using standard multi-user setup");
            }

            // 親クラスの実行（ユーザー作成とデータロード）
            super.execute();

        } catch (Exception e) {
            logError("ABAC loader error", e);
            throw e;
        }
    }

    /**
     * ABAC環境のセットアップ
     * AbacAdmin APIを使用してABACポリシーとアクセス制御を設定
     */
    private void setupAbacEnvironment() {
        AbacAdmin abacAdmin = null;
        try {
            logInfo("Setting up ABAC environment...");

            // AbacAdminインスタンスを取得（実装待ち）
            try {
                abacAdmin = Common.getAbacAdmin(config);
                logInfo("AbacAdmin instance created successfully");
            } catch (UnsupportedOperationException e) {
                logWarn("AbacAdmin not available: " + e.getMessage());
                logInfo("Using placeholder ABAC setup");
                setupAbacPlaceholder();
                return;
            }

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
        } finally {
            // AbacAdminはAutoCloseableではないため、明示的なcloseは不要
        }
    }

    /**
     * プレースホルダーABACセットアップ
     */
    private void setupAbacPlaceholder() {
        String policyName = "ycsb_benchmark_policy";
        logInfo("Would create ABAC policy: " + policyName);
        logInfo("Would create ABAC attributes for type: " + attributeType);
        logInfo("Would apply policy to table: " + NAMESPACE + "." + TABLE);
        logInfo("ABAC environment setup completed (placeholder implementation)");
    }

    /**
     * ABACポリシーを作成
     */
    private void createAbacPolicy(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        try {
            logInfo("Creating ABAC policy: " + policyName);

            // ポリシーが既に存在するかチェック（正確なAPIを使用）
            if (abacAdmin.getPolicy(policyName).isPresent()) {
                logInfo("Policy already exists: " + policyName);
                return;
            }

            // 新しいポリシーを作成（正確なAPIシグネチャを使用）
            abacAdmin.createPolicy(policyName, null); // デフォルトのデータタグカラム名を使用
            abacAdmin.enablePolicy(policyName); // ポリシーを有効化
            logInfo("Policy created and enabled successfully: " + policyName);

        } catch (ExecutionException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                logInfo("Policy already exists, continuing: " + policyName);
            } else {
                throw e;
            }
        }
    }

    /**
     * ABAC属性を作成（レベル/コンパートメント/グループ）
     */
    private void createAbacAttributes(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating ABAC attributes for policy: " + policyName);

        try {
            if ("level".equals(attributeType)) {
                createLevelAttributes(abacAdmin, policyName);
            } else if ("compartment".equals(attributeType)) {
                createCompartmentAttributes(abacAdmin, policyName);
            } else if ("group".equals(attributeType)) {
                createGroupAttributes(abacAdmin, policyName);
            } else {
                logWarn("Unknown attribute type: " + attributeType + ", defaulting to level");
                createLevelAttributes(abacAdmin, policyName);
            }

            logInfo("ABAC attributes created successfully");

        } catch (ExecutionException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                logInfo("Attributes already exist, continuing");
            } else {
                throw e;
            }
        }
    }

    /**
     * レベル属性を作成
     */
    private void createLevelAttributes(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating level attributes");

        for (int i = 0; i < attributeValues.length; i++) {
            try {
                String shortName = attributeValues[i];
                String longName = "Level " + shortName;
                int levelNumber = i + 1; // 1から開始

                // 正確なAPIシグネチャを使用
                abacAdmin.createLevel(policyName, shortName, longName, levelNumber);
                logInfo("Created level: " + levelNumber + " (" + shortName + " - " + longName + ")");
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    logInfo("Level already exists: " + attributeValues[i]);
                } else {
                    logWarn("Failed to create level " + attributeValues[i] + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * コンパートメント属性を作成
     */
    private void createCompartmentAttributes(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating compartment attributes");

        for (String attributeValue : attributeValues) {
            try {
                String shortName = attributeValue;
                String longName = "Compartment " + attributeValue;

                // 正確なAPIシグネチャを使用
                abacAdmin.createCompartment(policyName, shortName, longName);
                logInfo("Created compartment: " + shortName + " (" + longName + ")");
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    logInfo("Compartment already exists: " + attributeValue);
                } else {
                    logWarn("Failed to create compartment " + attributeValue + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * グループ属性を作成
     */
    private void createGroupAttributes(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Creating group attributes");

        for (String attributeValue : attributeValues) {
            try {
                String shortName = attributeValue;
                String longName = "Group " + attributeValue;

                // 正確なAPIシグネチャを使用（親グループなしのトップレベルグループ）
                abacAdmin.createGroup(policyName, shortName, longName, null);
                logInfo("Created group: " + shortName + " (" + longName + ")");
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    logInfo("Group already exists: " + attributeValue);
                } else {
                    logWarn("Failed to create group " + attributeValue + ": " + e.getMessage());
                }
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

        try {
            // 正確なAPIシグネチャを使用
            abacAdmin.createTablePolicy(tablePolicyName, policyName, namespace, tableName);
            abacAdmin.enableTablePolicy(tablePolicyName);
            logInfo("Policy applied and enabled for table: " + namespace + "." + tableName);
        } catch (ExecutionException e) {
            if (e.getMessage() != null && e.getMessage().contains("already applied")) {
                logInfo("Policy already applied to table: " + namespace + "." + tableName);
            } else {
                throw e;
            }
        }
    }

    /**
     * ユーザーへの属性割り当て
     */
    private void assignAttributesToUsers(AbacAdmin abacAdmin, String policyName) throws ExecutionException {
        logInfo("Assigning attributes to users");

        int userCount = getUserCount(config);
        for (int i = 0; i < userCount; i++) {
            String username = getUserName(i);
            String userAttribute = attributeStrategy.assignUserAttribute(i, attributeValues);

            try {
                if ("level".equals(attributeType)) {
                    // レベル属性をユーザーに設定
                    abacAdmin.setLevelsToUser(policyName, username, userAttribute, userAttribute, userAttribute);
                    logInfo("Assigned level '" + userAttribute + "' to user: " + username);
                } else if ("compartment".equals(attributeType)) {
                    // まずレベルを設定（コンパートメント使用の前提条件）
                    String defaultLevel = attributeValues[0]; // 最初の値をデフォルトレベルとして使用
                    abacAdmin.setLevelsToUser(policyName, username, defaultLevel, defaultLevel, defaultLevel);
                    // コンパートメントを追加
                    abacAdmin.addCompartmentToUser(policyName, username, userAttribute,
                            AbacAdmin.AccessMode.READ_WRITE, true, true);
                    logInfo("Assigned compartment '" + userAttribute + "' to user: " + username);
                } else if ("group".equals(attributeType)) {
                    // まずレベルを設定（グループ使用の前提条件）
                    String defaultLevel = attributeValues[0]; // 最初の値をデフォルトレベルとして使用
                    abacAdmin.setLevelsToUser(policyName, username, defaultLevel, defaultLevel, defaultLevel);
                    // グループを追加
                    abacAdmin.addGroupToUser(policyName, username, userAttribute,
                            AbacAdmin.AccessMode.READ_WRITE, true, true);
                    logInfo("Assigned group '" + userAttribute + "' to user: " + username);
                }
            } catch (Exception e) {
                logWarn("Failed to assign attribute to user " + username + ": " + e.getMessage());
            }
        }
    }

    /**
     * ユーザーに属性を割り当てる
     * 
     * @param userId ユーザーID
     * @return 割り当てられた属性値
     */
    public String assignUserAttribute(int userId) {
        if (!abacEnabled) {
            return null;
        }
        return attributeStrategy.assignUserAttribute(userId, attributeValues);
    }

    /**
     * データレコードに属性を割り当てる
     * 
     * @param recordId レコードID
     * @return 割り当てられた属性値
     */
    public String assignDataAttribute(int recordId) {
        if (!abacEnabled) {
            return null;
        }
        return attributeStrategy.assignDataAttribute(recordId, attributeValues);
    }

    @Override
    public void close() {
        super.close();

        // ABAC関連のクリーンアップがあれば実行
        if (abacEnabled) {
            logInfo("ABAC loader cleanup completed");
        }
    }
}
