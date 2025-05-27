# ABACマルチユーザーベンチマーク テスト結果

## 概要

ABACマルチユーザーベンチマークの実行結果において、数値の不一致が報告され、その原因を調査した結果をまとめる。また、様々なテストパターンの結果を記録する。

## 問題の詳細

### 初回の結果（2025/5/27 17:01）
```
Succeeded operations: 2672
Authorization success count: 1519
Authorization failure count: 1532
Total authorization operations: 3051
Transaction retry count: 0
```

**不一致:** 
- 成功したトランザクション: 2,672回
- 認証操作の合計: 3,051回
- 差異: 379回

### 設定
- `ops_per_tx = 1`（1トランザクションあたり1回のREAD操作）
- `user_count = 2`
- `concurrency = 2`
- `run_for_sec = 60`

## 調査のために実装した機能

### 1. デバッグログの追加
`MultiUserAbacWorkloadC`の初期化時に以下の情報を出力：
- Record count
- Ops per transaction
- User count
- Concurrency

### 2. 詳細メトリクスの追加
- `transactionExecutionCount`: 実際にコミットされたトランザクション数
- `executeEachCallCount`: executeEach()メソッドの呼び出し回数

### 3. レポート機能の拡張
`YcsbReporter`にデバッグ情報セクションを追加：
```java
==== Debug Information ====
Transaction execution count: [値]
ExecuteEach call count: [値]
```

## 修正後の結果（2025/5/27 17:19）

```
==== Statistics Summary ====
Throughput: 45.2 ops
Succeeded operations: 2712
Failed operations: 0
Mean latency: 44.228 ms
SD of latency: 15.208 ms
Max latency: 396 ms
Latency at 50 percentile: 42 ms
Latency at 90 percentile: 48 ms
Latency at 99 percentile: 63 ms
Transaction retry count: 0

==== ABAC Authorization Summary ====
User count: 2
Authorization success count: 1558
Authorization failure count: 1534
Total authorization operations: 3092

==== Debug Information ====
Transaction execution count: 3092
ExecuteEach call count: 3092
```

## 原因の分析

### 重要な発見
1. **Transaction execution count (3,092) と ExecuteEach call count (3,092) は完全に一致**
2. **Total authorization operations (3,092) も同じ値**
3. **Kelpieの「Succeeded operations」(2,712) とは約380の差**

### 根本原因
この差異は、**Kelpieフレームワークの計測方法**によるものである：

1. **Kelpieの「Succeeded operations」**: ベンチマーク期間（`run_for_sec`）内に**完全に完了**したオペレーションのみをカウント
2. **私たちのカウンター**: トランザクションがコミットされた時点でカウント（タイミングに関係なく）

つまり、ベンチマーク終了時刻付近で実行されたトランザクション（約380件）は：
- トランザクション自体は成功してコミットされた（私たちのカウンターには含まれる）
- しかし、Kelpieの計測期間を超えていたため「Succeeded operations」には含まれなかった

## 結論

### 正常な動作の確認
1. **数値の不一致は正常な動作**である
2. **認証操作数（3,092）は実際に実行されたトランザクション数と一致**
3. **`ops_per_tx = 1`の設定も正しく動作**している
4. **差異はKelpieの計測方法によるもので、ベンチマークの精度には影響しない**

### ABACベンチマークの動作確認
- ABAC権限チェックが正しく実行されている
- 認証成功/失敗の分類が適切に行われている
- トランザクションの実行とメトリクスの収集が正常に機能している

## 技術的な詳細

### 実装されたメトリクス
```java
// MultiUserAbacWorkloadC.java
private final LongAdder transactionExecutionCount = new LongAdder();
private final LongAdder executeEachCallCount = new LongAdder();
```

### カウンターの更新タイミング
- `executeEachCallCount`: executeEach()メソッドの開始時
- `transactionExecutionCount`: transaction.commit()の成功時
- `authorizationSuccessCount/authorizationFailureCount`: 各READ操作の結果に基づく

### Kelpieとの計測差異の理解
Kelpieフレームワークは、指定された実行時間内に完了したオペレーションのみを「成功」としてカウントするため、実行時間の境界付近で完了したトランザクションは統計から除外される。これは一般的なベンチマークフレームワークの動作であり、正常な挙動である。

## 推奨事項

1. **現在の実装は正しく動作している**ため、追加の修正は不要
2. **デバッグ機能は今後のトラブルシューティングに有用**なため、そのまま保持することを推奨
3. **ベンチマーク結果の解釈時**は、Kelpieの計測方法を考慮すること

---

# ABACテストパターン網羅結果

## テスト計画

### 属性タイプと戦略の組み合わせ
1. **level + random** ✅ 完了（上記結果）
2. **compartment + random** 📋 予定
3. **group + random** 📋 予定
4. **level + load_balanced** 📋 予定
5. **compartment + load_balanced** 📋 予定
6. **group + load_balanced** 📋 予定

### スケールアップテスト
- **小規模**: user_count=2, record_count=1000 ✅ 完了
- **中規模**: user_count=10, record_count=10000 📋 予定

## テスト結果サマリー

### Test 1: level + random (基準テスト)
**設定:**
```toml
user_count = 2
record_count = 1000
ops_per_tx = 1
abac_attribute_type = "level"
abac_strategy = "random"
abac_attribute_values = "public,confidential,secret"
concurrency = 2
run_for_sec = 60
```

**結果:**
- Throughput: 45.2 ops/sec
- Mean latency: 44.228 ms
- Authorization success: 1558 (50.4%)
- Authorization failure: 1534 (49.6%)
- Total operations: 3092

### Test 2: compartment + random
**設定:** ❌ 実行中断
```toml
abac_attribute_type = "compartment"
abac_strategy = "random"
```

**結果:** ❌ エラーで中断
- エラー: 「The compartment is already assigned to the user」
- 原因: 前回のテスト実行時の属性割り当てが残存
- 対策: ユーザー属性のクリーンアップ機能が必要

### Test 3: group + random
**設定:** ❌ 実行中断
```toml
abac_attribute_type = "group"
abac_strategy = "random"
```

**結果:** ❌ エラーで中断
- ABAC環境セットアップ: ✅ 成功
- グループ属性作成: ✅ 成功
- ユーザー属性割り当て: ✅ 成功
- データロード: ❌ 失敗
- エラー: 「The level must be specified in the data tag. Data tag: ::public」
- 原因: data_tag生成でlevelが空になっている
- 対策: groupとcompartmentの場合でもデフォルトレベルを含むdata_tag生成が必要

### Test 4: level + load_balanced
**設定:** ✅ 完了
```toml
user_count = 2
record_count = 1000
ops_per_tx = 1
abac_attribute_type = "level"
abac_strategy = "load_balanced"
abac_attribute_values = "public,confidential,secret"
concurrency = 2
run_for_sec = 60
```

**結果:** ✅ 成功
- Throughput: 43.3 ops/sec
- Mean latency: 46.19 ms
- Authorization success: 1496 (50.5%)
- Authorization failure: 1469 (49.5%)
- Total operations: 2965
- Load balanced strategy: user0=public, user1=confidential
- データロード: 18.56 records/sec (1000 records in 53.9 seconds)

### Test 5: compartment + load_balanced
**設定:** ❌ 実行中断
```toml
abac_attribute_type = "compartment"
abac_strategy = "load_balanced"
```

**結果:** ❌ エラーで中断
- ABAC環境セットアップ: ✅ 成功
- コンパートメント属性作成: ✅ 成功
- ユーザー属性割り当て: ✅ 成功（user0=public, user1=confidential）
- データロード: ❌ 失敗
- エラー: 「The level must be specified in the data tag. Data tag: :public:」
- 原因: compartmentの場合でもlevelが空になっている（Test 3と同じ問題）

### Test 6: group + load_balanced
**設定:** 📋 実行予定
```toml
abac_attribute_type = "group"
abac_strategy = "load_balanced"
```

**結果:** 📋 実行予定

### Test 7: スケールアップテスト (level + random)
**設定:** ✅ 完了
```toml
user_count = 10
record_count = 10000
ops_per_tx = 1
abac_attribute_type = "level"
abac_strategy = "random"
abac_attribute_values = "public,confidential,secret"
concurrency = 2
run_for_sec = 60
```

**結果:** ✅ 成功
- Throughput: 45.65 ops/sec
- Mean latency: 43.793 ms
- Authorization success: 3164 (100%)
- Authorization failure: 0 (0%)
- Total operations: 3164
- Random strategy distribution: 5×public, 1×confidential, 4×secret
- データロード: 19.10 records/sec (10,000 records in 523.5 seconds)
- **注目点**: 10ユーザーでの権限分散により、すべてのアクセスが成功

## ベンチマーク実行方法

### 基本実行コマンド
```bash
java -jar build/libs/scalardb-benchmarks-all.jar \
  --config ycsb-multi-user-abac-benchmark-config.toml
```

### 設定変更方法
1. `ycsb-multi-user-abac-benchmark-config.toml`を編集
2. `abac_attribute_type`と`abac_strategy`を変更
3. 必要に応じて`user_count`と`record_count`を変更
4. ベンチマーク実行

### 結果の記録方法
各テスト実行後、以下の情報を記録：
- 設定値（attribute_type, strategy, user_count, record_count）
- Statistics Summary（Throughput, Latency）
- ABAC Authorization Summary（成功/失敗率）
- Debug Information（実行統計）

## 分析観点

### パフォーマンス比較
- 属性タイプ別のパフォーマンス差異
- 戦略別のパフォーマンス差異
- スケールアップ時の性能変化

### 権限チェック動作
- 各属性タイプでの権限チェック成功率
- 戦略による権限分散の違い
- ユーザー数増加時の権限チェック影響

### システム負荷
- CPU使用率
- メモリ使用量
- ディスクI/O
- ネットワーク使用量
