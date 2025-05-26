# ABACマルチユーザーベンチマーク実装方針

## 1. 設計背景と目的

### 1.1 目的
ScalarDBのABAC機能による性能影響を測定するため、READ操作の許可/拒否パターンを2つの戦略でベンチマークする。

**測定対象**:
- ABAC有効時 vs 無効時の性能比較
- 許可/拒否パターンによる性能差
- 属性割り当て戦略の影響

**戦略**:
- **Random**: ユーザー属性とデータ属性をランダム割り当て
- **Load Balanced**: 重複を避けた均等割り当て

## 2. シンプル設計

### 2.1 基本コンポーネント

```
MultiUserAbacLoader    →    MultiUserAbacWorkloadC    →    YcsbReporter
  ユーザー・データ属性割り当て       READ操作ベンチマーク            結果分析
```

### 2.2 属性管理の単純化

**属性タイプ（1つを選択）**:
- `level`: レベルベース制御（例: public, confidential, secret）
- `compartment`: 部門ベース制御（例: hr, sales, engineering）
- `group`: グループベース制御（例: team_a, team_b, team_c）

**割り当て戦略**:
- `Random`: ランダム割り当て
- `LoadBalanced`: 均等分散割り当て

## 3. 実装アプローチ

### 3.1 属性割り当て戦略

#### Random戦略
```java
public class RandomStrategy {
    public String assignUserAttribute(int userId, String[] values) {
        return values[random.nextInt(values.length)];
    }
    
    public String assignDataAttribute(int recordId, String[] values) {
        return values[random.nextInt(values.length)];
    }
}
```

#### LoadBalanced戦略
```java
public class LoadBalancedStrategy {
    public String assignUserAttribute(int userId, String[] values) {
        return values[userId % values.length];  // 均等分散
    }
    
    public String assignDataAttribute(int recordId, String[] values) {
        return values[recordId % values.length];  // 均等分散
    }
}
```

### 3.2 設定例

```toml
# ABAC基本設定
[abac]
enabled = true
attribute_type = "level"  # level | compartment | group
strategy = "random"       # random | load_balanced

# 属性値定義
attribute_values = ["public", "confidential", "secret"]

# ベンチマーク設定
user_count = 4
record_count = 10000
```

## 4. 測定とメトリクス

### 4.1 基本メトリクス
- **スループット (TPS)**: ABAC有効時 vs 無効時
- **レイテンシ**: 権限チェックによる遅延
- **許可/拒否率**: アクセス成功率の測定

### 4.2 測定例
```toml
# 比較測定
[baseline]
abac_enabled = false

[abac_test]
abac_enabled = true
attribute_type = "level"
strategy = "random"
attribute_values = ["public", "confidential", "secret"]
```

## 5. まとめ

シンプルなABACベンチマークツールにより、ScalarDBのABAC機能による性能影響を効率的に測定できます。

**核心機能**:
1. ユーザーとデータに属性を割り当て
2. READ操作の許可/拒否パターンをベンチマーク
3. Random/LoadBalanced戦略で性能差を測定
4. level/compartment/groupから1つを選択して単純化
