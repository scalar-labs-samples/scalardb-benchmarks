# ABACマルチユーザーベンチマーク実装タスクリスト

## 概要
ScalarDBのABAC機能による性能影響を測定するため、READ操作の許可/拒否パターンを2つの戦略でベンチマークする。

**前提条件**: 
- ScalarDB Cluster側でABACが有効化済み
- ABAC仕様: https://scalardb.scalar-labs.com/ja-jp/docs/latest/scalardb-cluster/authorize-with-abac/
- AbacAdmin API: https://javadoc.io/static/com.scalar-labs/scalardb/3.15.3/com/scalar/db/api/AbacAdmin.html

## 主要タスク

### 1. 属性割り当て戦略の実装
- [ ] **Random戦略の実装**
  - ユーザー属性とデータ属性をランダム割り当て
- [ ] **LoadBalanced戦略の実装**
  - 重複を避けた均等分散割り当て
- [ ] **属性タイプの選択機能**
  - level/compartment/groupから1つを設定で選択

### 2. MultiUserAbacWorkloadCクラスの実装
- [ ] `src/main/java/com/scalar/db/benchmarks/ycsb/MultiUserAbacWorkloadC.java`の作成
- [ ] 既存のMultiUserWorkloadCをベースにした実装
- [ ] READ操作での属性ベースアクセス制御
- [ ] 基本メトリクス収集（スループット、レイテンシ、許可/拒否率）

### 3. MultiUserAbacLoaderの実装
- [ ] `MultiUserAbacLoader.java`の作成
- [ ] 既存のMultiUserLoaderを拡張
- [ ] ユーザーとデータへの属性割り当て
- [ ] ABACポリシーの作成と適用

### 4. 設定ファイルの作成
- [ ] `ycsb-multi-user-abac-benchmark-config.toml`の作成
- [ ] ABAC基本設定（enabled, attribute_type, strategy）
- [ ] 属性値定義とベンチマーク設定

### 5. テストと検証
- [ ] ABAC有効時 vs 無効時の性能比較テスト
- [ ] Random vs LoadBalanced戦略の比較テスト

## 実装例

### 設定ファイル例
```toml
# ABAC基本設定
[abac]
enabled = true
attribute_type = "level"  # level | compartment | group
strategy = "random"       # random | load_balanced
attribute_values = ["public", "confidential", "secret"]

# ベンチマーク設定
user_count = 4
record_count = 10000
```

### 属性戦略実装例
```java
public class RandomStrategy {
    public String assignUserAttribute(int userId, String[] values) {
        return values[random.nextInt(values.length)];
    }
    
    public String assignDataAttribute(int recordId, String[] values) {
        return values[random.nextInt(values.length)];
    }
}

public class LoadBalancedStrategy {
    public String assignUserAttribute(int userId, String[] values) {
        return values[userId % values.length];
    }
    
    public String assignDataAttribute(int recordId, String[] values) {
        return values[recordId % values.length];
    }
}
```

## 成功基準

- [ ] ABAC有効時と無効時の性能比較が可能
- [ ] Random/LoadBalanced戦略の性能差測定が可能
- [ ] 基本メトリクス（スループット、レイテンシ、許可/拒否率）が取得可能
- [ ] 設定による属性タイプの切り替えが可能
