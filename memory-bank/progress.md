# ScalarDB ベンチマークツール: 進捗状況

## 現在動作している機能

### TPC-C ベンチマーク
- ✅ TPC-Cスキーマの完全実装
- ✅ データ生成・ロード機能
- ✅ 5つのトランザクションタイプ全ての実装
  - New Order
  - Payment
  - Order Status
  - Delivery
  - Stock Level
- ✅ トランザクションミックス比率のカスタマイズ機能
- ✅ スケーラビリティテスト用のウェアハウス数調整機能
- ✅ 競合発生時のバックオフ設定

### YCSB ベンチマーク
- ✅ 基本的なYCSBスキーマの実装
- ✅ データ生成・ロード機能
- ✅ 主要ワークロードの実装
  - ワークロードA (50%リード・50%アップデート)
  - ワークロードC (100%リード)
  - ワークロードF (読取り-変更-書込み)
- ✅ バッチ処理によるロードパフォーマンスの最適化
- ✅ レコード数とペイロードサイズのカスタマイズ

### マルチストレージ YCSB
- ✅ 複数ストレージ環境用のスキーマ定義
- ✅ 主要ワークロードの実装
  - ワークロードC (100%リード)
  - ワークロードF (読取り-変更-書込み)
- ✅ 複数名前空間（ycsb_primary, ycsb_secondary）へのバランスの取れた操作

### ABACマルチユーザー YCSB ✅ 完全実装
- ✅ ScalarDBユーザーの自動作成・権限付与機能
- ✅ キー範囲分割による並列アクセス機能
- ✅ ユーザー固有のトランザクションマネージャー管理
- ✅ ABAC環境セットアップ（AbacAdmin API使用）
- ✅ 属性ベースアクセス制御による権限チェック
- ✅ ワークロードの実装
  - ワークロードC (100%リード + ABAC権限チェック)
- ✅ ユーザー数とレコード数の柔軟な設定
- ✅ 詳細メトリクス（認証成功率、失敗率、デバッグ情報）

**注意**: 通常のマルチユーザーYCSBは削除され、ABACマルチユーザーに統合されました。

### 設定と実行
- ✅ TOML設定ファイルによるパラメータ指定
- ✅ ScalarDB接続設定の柔軟な指定方法
- ✅ Kelpieフレームワークとの統合
- ✅ 詳細なコマンドラインオプション
- ✅ データロードと実行の分離オプション

### レポーティング
- ✅ 基本的なパフォーマンス指標の出力
- ✅ 競合・リトライ情報の記録
- ✅ 実行時間とスループットの測定

## 開発中の機能

現在、マルチユーザーモードの拡張が進行中です：
- マルチユーザーモードにおける他のワークロード（A、F）の実装
- パフォーマンス指標の拡充とユーザー数に応じた分析機能

## 未実装の機能と課題

### YCSB拡張
- ⬜ 他のYCSBワークロードの実装（B、D、E）
- ⬜ カスタムYCSBワークロードの定義機能
- ⬜ マルチユーザーモードの他のワークロード対応（A、F）

### ABACマルチユーザーベンチマーク（完全実装完了）
- ✅ AttributeAssignmentStrategyインターフェースの実装
- ✅ RandomStrategy（ランダム属性割り当て戦略）の実装
- ✅ LoadBalancedStrategy（負荷分散属性割り当て戦略）の実装
- ✅ MultiUserAbacWorkloadC（ABAC対応ワークロードC）の完全実装
- ✅ MultiUserAbacLoader（ABAC対応ローダー）の完全実装
- ✅ YcsbCommonへのABAC設定パラメータ追加
- ✅ ycsb-multi-user-abac-benchmark-config.toml設定ファイルの作成
- ✅ 全体的なビルドテスト完了（コンパイルエラー解決）
- ✅ **本格実装完了**: AbacAdmin API使用の完全実装
  - ✅ MultiUserAbacLoader.setupAbacEnvironment()メソッド: AbacAdmin APIを使用した完全実装
    - ポリシー作成・有効化（createPolicy, enablePolicy）
    - 属性定義作成（createLevel, createCompartment, createGroup）
    - テーブルポリシー適用（createTablePolicy, enableTablePolicy）
    - ユーザー属性割り当て（setLevelsToUser, addCompartmentToUser, addGroupToUser）
  - ✅ MultiUserAbacWorkloadC.simulateAbacCheck()メソッド: ABAC権限チェック実装
  - ✅ Common.getAbacAdmin()メソッド: AbacAdminインスタンス取得実装
  - ✅ 実際のScalarDB ABAC機能との統合完了

### 拡張機能
- ⬜ グラフィカルな結果レポート
- ⬜ より詳細なリソース使用状況の監視
- ⬜ JDK 8以外のバージョンサポート
- ⬜ 分散環境での協調ベンチマーク機能

### 改善ポイント
- ⬜ より詳細なレポーティング機能
- ⬜ CI/CD環境での自動ベンチマーク
- ⬜ クラウド環境向けのセットアップガイド

## プロジェクトの進化

### 初期の設計
- 基本的なベンチマーク機能の実装
- ScalarDBのTransactionManagerを使用したトランザクション処理
- Kelpieフレームワークを活用したベンチマーク実行環境

### 現在の状態
- 完全に機能する4種類のベンチマークを提供
  - TPC-C（完全実装）
  - YCSB（ワークロードA、C、F）
  - マルチストレージYCSB（ワークロードC、F）
  - マルチユーザーYCSB（ワークロードC）
- 柔軟なパラメータ設定で様々な環境に対応
- 業界標準ベンチマークのリファレンス実装としての役割を果たす
- マルチユーザーモードによるユーザー数スケーラビリティテスト機能
- 実運用レベルの安定性と精度を持つベンチマーク環境

### 将来の方向性
- よりユーザーフレンドリーなインターフェース
- 多様なデータベース環境とのより広範な互換性確保
- パフォーマンス分析と視覚化の強化
- マルチユーザーモードの拡張とさらなる最適化

## 既知の問題

### 技術的制約
- JDK 8のみのサポート
- 特定のScalarDBバージョンへの依存性
- メモリ消費量の最適化の余地

### パフォーマンス関連
- 大規模なデータセットでのメモリ使用量
- トランザクション競合時のリトライ戦略の最適化
- バックオフ戦略のさらなる調整の必要性
- 多数のユーザーを作成する際のオーバーヘッド

## 次のマイルストーン

短期的には、以下の機能拡充を進めています：

1. マルチユーザーモードの他のワークロードへの拡張
2. ユーザー数スケーラビリティ分析機能の強化

長期的な改善としては：

1. より多くのカスタマイズオプションの提供
2. パフォーマンス指標の拡充
3. 最新のJava言語機能の活用
4. よりモジュラーなアーキテクチャへの進化
