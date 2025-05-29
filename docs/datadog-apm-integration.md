# Datadog APM統合機能

## 概要

`run-abac-benchmarks-all.sh` スクリプトにDatadog APM統合機能が追加されました。この機能により、ベンチマーク実行時のパフォーマンスデータをDatadogに送信し、詳細な分析とモニタリングが可能になります。

## 機能

- Datadog Java Agentの自動ダウンロード
- コマンドラインオプションによる柔軟な設定
- 環境変数による設定サポート
- 既存のJAVA_OPTS設定との統合
- 適切なエラーハンドリング
- 後方互換性の維持

## 使用方法

### 基本的な使用例

```bash
# APMを有効にしてベンチマーク実行
./run-abac-benchmarks-all.sh --enable-datadog-apm

# カスタムサービス名と環境を指定
./run-abac-benchmarks-all.sh --enable-datadog-apm --datadog-service "my-benchmark" --datadog-env "production"

# ログインジェクションを無効にする
./run-abac-benchmarks-all.sh --enable-datadog-apm --disable-logs-injection
```

### 環境変数による設定

```bash
# 環境変数で設定
export DATADOG_APM_ENABLED=true
export DATADOG_SERVICE="scalardb-performance-test"
export DATADOG_ENV="staging"

./run-abac-benchmarks-all.sh
```

### コマンドラインオプション

| オプション | 説明 | デフォルト値 |
|-----------|------|------------|
| `--enable-datadog-apm` | Datadog APMを有効にする | false |
| `--datadog-service SERVICE` | Datadogサービス名を指定 | scalardb-benchmarks |
| `--datadog-env ENV` | Datadog環境名を指定 | dev |
| `--datadog-agent-path PATH` | Datadog Agentファイルパス | ./dd-java-agent.jar |
| `--disable-logs-injection` | ログインジェクションを無効 | false |
| `-h, --help` | ヘルプを表示 | - |

### 環境変数

| 環境変数 | 説明 |
|----------|------|
| `DATADOG_APM_ENABLED` | APM有効化 (true/false) |
| `DATADOG_SERVICE` | サービス名 |
| `DATADOG_ENV` | 環境名 |
| `DATADOG_AGENT_PATH` | Agentファイルパス |

## Datadog設定パラメータ

APM有効時に自動的に設定されるJavaシステムプロパティ：

- `-javaagent:./dd-java-agent.jar` - Datadog Agentの有効化
- `-Ddd.service=<service-name>` - サービス名
- `-Ddd.env=<environment>` - 環境名
- `-Ddd.logs.injection=true` - ログインジェクション（設定により制御）
- `-Ddd.profiling.enabled=true` - プロファイリング有効化
- `-XX:FlightRecorderOptions=stackdepth=256` - Java Flight Recorderのスタック深度設定
- `-Ddd.trace.enabled=true` - トレーシング有効化

## エラーハンドリング

- Datadog Agentのダウンロードに失敗した場合、APMなしでベンチマークを続行
- wget/curlが利用できない場合、手動ダウンロードの指示を表示
- ダウンロード失敗時の適切なエラーメッセージ

## ABAC ベンチマークでの活用

ABAC（Attribute-Based Access Control）環境でのベンチマーク実行時に、以下の詳細な分析が可能になります：

- 権限チェック処理のレイテンシー
- ABAC有効/無効時のパフォーマンス比較
- マルチユーザー環境でのスケーラビリティ分析
- ScalarDB固有の処理パターンの可視化

## 出力例

```
[INFO] ABACベンチマーク実行スクリプトを開始します
[INFO] === Datadog APM設定 ===
[INFO] APM有効: YES
[INFO] サービス名: scalardb-benchmarks
[INFO] 環境名: dev
[INFO] Agentパス: ./dd-java-agent.jar
[INFO] ログインジェクション: true
[INFO] ========================
[SUCCESS] Datadog Agentのダウンロードが完了しました: ./dd-java-agent.jar
[INFO] 物理メモリサイズ: 31722 MB
[INFO] Javaヒープサイズ（80%）: 25377m
[INFO] JAVA_OPTS設定: -Xmx25377m -Xms25377m -javaagent:./dd-java-agent.jar -Ddd.service=scalardb-benchmarks -Ddd.env=dev -Ddd.logs.injection=true -Ddd.profiling.enabled=true -Ddd.trace.enabled=true
[SUCCESS] Datadog APMが有効になりました
```

## 結果サマリー

ベンチマーク完了時の結果サマリーにもDatadog APM設定が表示されます：

```
========================================
ABAC Benchmark Results Summary
========================================
実行時刻: Wed May 29 07:06:19 UTC 2025
物理メモリ: 31722 MB
Javaヒープサイズ: 25377m
Datadog APM: 有効 (サービス: scalardb-benchmarks, 環境: dev)
========================================
```

## 前提条件

- wget または curl が利用可能であること
- インターネット接続（Datadog Agentダウンロード用）
- Datadog アカウントとAPI キー設定（実際のデータ送信時）

## 注意事項

- デフォルトではAPMは無効です
- 初回実行時にDatadog Java Agentが自動ダウンロードされます（約31MB）
- 既存のベンチマーク機能には影響しません
- APM有効時は若干のパフォーマンスオーバーヘッドがあります
