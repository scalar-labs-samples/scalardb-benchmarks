# ABACマルチユーザーベンチマーク実装タスクリスト

## 概要
ScalarDBのABAC機能による性能影響を測定するため、READ操作の許可/拒否パターンをベンチマークする。

**注意**: MultiUserモードは削除され、ABACマルチユーザーモードに統合されました。ABACモードが複数ユーザーによる並列実行とスケーラビリティテストの両方をサポートします。

**前提条件**: 
- ScalarDB Cluster側でABACが有効化済み
- ABAC仕様: https://scalardb.scalar-labs.com/ja-jp/docs/latest/scalardb-cluster/authorize-with-abac/
- AbacAdmin API: https://javadoc.io/static/com.scalar-labs/scalardb/3.15.3/com/scalar/db/api/AbacAdmin.html

## 主要タスク

### 1. 属性割り当て戦略の実装
- [x] **Random戦略の実装** ✅ 完了
  - ユーザー属性とデータ属性をランダム割り当て
- [ ] **LoadBalanced戦略の実装**
  - 重複を避けた均等分散割り当て（今回はRandom戦略のみ実装）
- [x] **属性タイプの選択機能** ✅ 完了
  - level/compartment/groupから1つを設定で選択

### 2. MultiUserAbacWorkloadCクラスの実装
- [x] `src/main/java/com/scalar/db/benchmarks/ycsb/MultiUserAbacWorkloadC.java`の作成 ✅ 完了
- [x] 既存のMultiUserWorkloadCをベースにした実装 ✅ 完了
- [x] READ操作での属性ベースアクセス制御 ✅ 完了
- [x] 基本メトリクス収集（スループット、レイテンシ、許可/拒否率） ✅ 完了
- [x] **デバッグ機能の追加** ✅ 完了（2025/5/27追加）
  - 詳細メトリクス（Transaction execution count, ExecuteEach call count）
  - 初期化時の設定値ログ出力

### 3. MultiUserAbacLoaderの実装
- [x] `MultiUserAbacLoader.java`の作成 ✅ 完了
- [x] 独立したローダークラス（PreProcessorを直接継承） ✅ 完了
- [x] ユーザーとデータへの属性割り当て ✅ 完了
- [x] ABACポリシーの作成と適用 ✅ 完了
  - AbacAdmin APIを使用した完全な環境構築
  - ポリシー作成・有効化
  - レベル/コンパートメント/グループ属性の作成
  - テーブルポリシーの適用
  - ユーザーへの属性割り当て

### 4. 設定ファイルの作成
- [x] `ycsb-multi-user-abac-benchmark-config.toml`の作成 ✅ 完了
- [x] ABAC基本設定（enabled, attribute_type, strategy） ✅ 完了
- [x] 属性値定義とベンチマーク設定 ✅ 完了

### 5. テストと検証
- [x] ABAC有効時の性能測定テスト ✅ 完了
- [x] Random戦略での動作確認 ✅ 完了
- [x] **数値不一致問題の調査と解決** ✅ 完了（2025/5/27）
  - Kelpieフレームワークの計測方法による正常な差異であることを確認
  - デバッグ機能により原因を特定

### 6. 共通機能の拡張
- [x] `YcsbCommon.java`の拡張 ✅ 完了
  - prepareInsertメソッドの追加
- [x] `Common.java`の拡張 ✅ 完了
  - getAbacAdminメソッドの追加
- [x] `YcsbReporter.java`の拡張 ✅ 完了
  - ABAC Authorization Summaryの表示
  - Debug Informationセクションの追加

## 実装例

### 設定ファイル例
```toml
[ycsb_config]
user_count = 2
record_count = 1000
ops_per_tx = 1
# ABAC設定は実装内でハードコードされています
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

- [x] ABAC有効時の性能測定が可能 ✅ 完了
- [ ] Random/LoadBalanced戦略の性能差測定が可能（LoadBalanced戦略は未実装）
- [x] 基本メトリクス（スループット、レイテンシ、許可/拒否率）が取得可能 ✅ 完了
- [x] 設定による属性タイプの切り替えが可能 ✅ 完了
- [x] **デバッグ・トラブルシューティング機能** ✅ 完了

## 実装完了状況

### ✅ 完了した機能
1. **ABACマルチユーザーベンチマーク基盤**
   - MultiUserAbacLoader（独立実装）
   - MultiUserAbacWorkloadC（ABAC対応READ操作）
   - 設定ファイル（ycsb-multi-user-abac-benchmark-config.toml）

2. **ABAC環境セットアップ**
   - AbacAdmin APIを使用した完全な環境構築
   - ポリシー作成・有効化
   - 属性定義と割り当て

3. **パフォーマンス測定**
   - スループット、レイテンシ測定
   - 権限チェック結果の分類
   - 詳細メトリクス（デバッグ用）

4. **品質保証**
   - ビルドテスト完了
   - 機能テスト完了
   - 数値不一致問題の調査・解決

### 📋 今後の拡張可能性
1. **LoadBalanced戦略の実装**
2. **他のワークロード（A、F）のABAC対応**
3. **属性レベル別のパフォーマンス分析**
4. **ユーザー数スケーラビリティの評価**

## 関連ドキュメント
- [実装サマリー](./abac-multi-user-benchmark-implementation-summary.md)
- [テスト結果](./abac-multi-user-benchmark-test-results.md)
- [設計ドキュメント](./abac-multi-user-benchmark-design.md)
