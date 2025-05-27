# ScalarDB ベンチマークツール

ScalarDBのパフォーマンスを測定するための包括的なベンチマークツール集です。TPC-C、YCSB、マルチユーザー、ABAC対応など、様々なベンチマークパターンをサポートしています。

## 目次

- [機能概要](#機能概要)
- [前提条件](#前提条件)
- [Java 8 セットアップ（macOS）](#java-8-セットアップmacos)
- [プロジェクトのセットアップ](#プロジェクトのセットアップ)
- [ベンチマーク実行方法](#ベンチマーク実行方法)
  - [TPC-C ベンチマーク](#tpc-c-ベンチマーク)
  - [YCSB ベンチマーク](#ycsb-ベンチマーク)
  - [ABAC マルチユーザーベンチマーク](#abac-マルチユーザーベンチマーク)
- [設定ファイル](#設定ファイル)
- [トラブルシューティング](#トラブルシューティング)

## 機能概要

### 実装済みベンチマーク

| ベンチマーク             | 説明                                         | 用途                                           |
| ------------------------ | -------------------------------------------- | ---------------------------------------------- |
| **TPC-C**                | 完全なTPC-C実装                              | OLTP性能評価                                   |
| **YCSB**                 | ワークロードA、C、F                          | NoSQL/分散DB評価                               |
| **マルチストレージYCSB** | 複数ストレージ環境                           | マルチテナント評価                             |
| **ABACマルチユーザー**   | 属性ベースアクセス制御と複数ユーザー並列実行 | セキュリティ性能影響測定・スケーラビリティ評価 |

### 主要な特徴

- **スケーラビリティテスト**: 複数ユーザーでの並列実行
- **セキュリティ評価**: ABAC（属性ベースアクセス制御）の性能影響測定
- **柔軟な設定**: TOML形式の設定ファイル
- **詳細なメトリクス**: スループット、レイテンシ、エラー率の測定
- **リアルタイム監視**: 実行中のパフォーマンス監視

## 前提条件

- **Java 8** (必須) - Java 9以降では動作しません
- **ScalarDB Cluster** - ベンチマーク対象のScalarDBクラスター
- **Gradle** - ビルドツール（Gradle Wrapper使用）

## Java 8 セットアップ（macOS）

### 1. Homebrewを使用したインストール

```bash
# Homebrewがインストールされていない場合
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Java 8 (Temurin) をインストール
brew install --cask temurin@8
```

### 2. インストール確認

```bash
# インストールされたJavaバージョンを確認
/usr/libexec/java_home -V

# Java 8のパスを確認
/usr/libexec/java_home -v 1.8

# 出力例: /Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
```

### 3. 環境変数設定（オプション）

デフォルトでJava 8を使用したい場合:

```bash
# ~/.zshrc または ~/.bash_profile に追加
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
export PATH=$JAVA_HOME/bin:$PATH

# 設定を反映
source ~/.zshrc  # または source ~/.bash_profile
```

## プロジェクトのセットアップ

### 1. リポジトリのクローン

```bash
git clone <リポジトリURL>
cd scalardb-benchmarks
```

### 2. プロジェクトのビルド

```bash
# 依存関係の解決とビルド
./gradlew build

# 実行可能JARの作成
./gradlew shadowJar
```

### 3. ScalarDB接続設定

`scalardb.properties`ファイルを編集して、ScalarDBクラスターへの接続情報を設定:

```properties
scalar.db.transaction_manager=cluster
scalar.db.contact_points=your-scalardb-cluster-endpoint
scalar.db.contact_port=60053
scalar.db.cluster.tls.enabled=true
scalar.db.cluster.auth.enabled=true
scalar.db.username=admin
scalar.db.password=admin
```

## ベンチマーク実行方法

### 基本的な実行コマンド

```bash
/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java \
  -cp build/libs/scalardb-benchmarks-all.jar \
  com.scalar.kelpie.Kelpie \
  --config=<設定ファイル名>
```

### TPC-C ベンチマーク

```bash
# TPC-C ベンチマーク実行
/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java \
  -cp build/libs/scalardb-benchmarks-all.jar \
  com.scalar.kelpie.Kelpie \
  --config=tpcc-benchmark-config.toml
```

### YCSB ベンチマーク

```bash
# YCSB ベンチマーク実行
/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java \
  -cp build/libs/scalardb-benchmarks-all.jar \
  com.scalar.kelpie.Kelpie \
  --config=ycsb-benchmark-config.toml
```

### ABAC マルチユーザーベンチマーク

ABAC（属性ベースアクセス制御）環境でのマルチユーザーベンチマークです。複数のScalarDBユーザーが並列でアクセスし、属性ベースの権限チェックによる性能影響を測定します。

**特徴:**
- 複数ユーザーによる並列実行（スケーラビリティテスト）
- 属性ベースアクセス制御による権限チェック
- READ操作の許可/拒否パターンの分析
- 詳細なメトリクス（認証成功率、失敗率、スループット）

```bash
# ABAC マルチユーザーベンチマーク実行
/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java \
  -cp build/libs/scalardb-benchmarks-all.jar \
  com.scalar.kelpie.Kelpie \
  --config=ycsb-multi-user-abac-benchmark-config.toml
```

## 設定ファイル

### ABAC マルチユーザーベンチマーク設定例

`ycsb-multi-user-abac-benchmark-config.toml`:

```toml
[modules]
[modules.preprocessor]
name = "com.scalar.db.benchmarks.ycsb.MultiUserAbacLoader"
path = "./build/libs/scalardb-benchmarks-all.jar"
[modules.processor]
name = "com.scalar.db.benchmarks.ycsb.MultiUserAbacWorkloadC"
path = "./build/libs/scalardb-benchmarks-all.jar"
[modules.postprocessor]
name = "com.scalar.db.benchmarks.ycsb.YcsbReporter"
path = "./build/libs/scalardb-benchmarks-all.jar"

[common]
concurrency = 4          # 並行実行数
run_for_sec = 300        # 実行時間（秒）
ramp_for_sec = 60        # ウォームアップ時間（秒）

[stats]
realtime_report_enabled = true

[ycsb_config]
user_count = 4           # ScalarDBユーザー数
record_count = 10000     # レコード数
ops_per_tx = 2           # トランザクションあたりの操作数
load_concurrency = 2     # データロード時の並列度

# ABAC設定は実装内でハードコードされています
# - 属性タイプ: level, compartment, group
# - 属性値: public, confidential, secret など
# - 戦略: random（ランダム割り当て）

[database_config]
config_file = "scalardb.properties"
```

### 主要パラメータ

| パラメータ         | 説明                           | デフォルト値      |
| ------------------ | ------------------------------ | ----------------- |
| `concurrency`      | 並行実行スレッド数             | 1                 |
| `run_for_sec`      | ベンチマーク実行時間（秒）     | 60                |
| `ramp_for_sec`     | ウォームアップ時間（秒）       | 10                |
| `user_count`       | ScalarDBユーザー数             | concurrencyと同じ |
| `record_count`     | テーブル内のレコード数         | 1000              |
| `ops_per_tx`       | トランザクションあたりの操作数 | 2                 |
| `load_concurrency` | データロード時の並列度         | 1                 |

## トラブルシューティング

### よくある問題と解決方法

#### 1. Java バージョンエラー

**エラー**: `ClassCastException` または `ModuleLoadException`

**解決方法**: Java 8を使用していることを確認

```bash
# 現在のJavaバージョン確認
java -version

# Java 8で明示的に実行
/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java -version
```

#### 2. メインマニフェスト属性エラー

**エラー**: `build/libs/scalardb-benchmarks-all.jarにメイン・マニフェスト属性がありません`

**解決方法**: shadowJarでビルドし直す

```bash
./gradlew clean shadowJar
```

#### 3. ログファイルエラー

**エラー**: `/var/log/kelpie/kelpie.log (No such file or directory)`

**解決方法**: ログディレクトリを作成（オプション）

```bash
sudo mkdir -p /var/log/kelpie
sudo chmod 777 /var/log/kelpie
```

または、エラーを無視して実行を続行（機能には影響しません）

#### 4. ScalarDB接続エラー

**エラー**: 接続タイムアウトまたは認証エラー

**解決方法**:
1. `scalardb.properties`の設定を確認
2. ネットワーク接続を確認
3. 認証情報を確認

#### 5. ユーザー作成エラー

**エラー**: `Failed to create user`

**解決方法**:
1. 管理者権限でScalarDBに接続していることを確認
2. ユーザー管理機能が有効になっていることを確認

### パフォーマンスチューニング

#### JVMオプション

大規模なベンチマークを実行する場合のJVMオプション例:

```bash
/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java \
  -Xmx4g -Xms2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -cp build/libs/scalardb-benchmarks-all.jar \
  com.scalar.kelpie.Kelpie \
  --config=ycsb-multi-user-abac-benchmark-config.toml
```

#### 推奨設定値

| 用途             | concurrency | record_count | run_for_sec |
| ---------------- | ----------- | ------------ | ----------- |
| **開発・テスト** | 2           | 1,000        | 60          |
| **性能評価**     | 8           | 100,000      | 600         |
| **負荷テスト**   | 16+         | 1,000,000+   | 1800+       |

## サポート

問題が発生した場合：

1. このREADMEのトラブルシューティングを確認
2. ログファイル（`kelpie.log`）を確認
3. ScalarDBクラスターの状態を確認
4. 設定ファイルの構文を確認

## 参考リンク

- [ScalarDB Documentation](https://scalardb.scalar-labs.com/)
- [ABAC仕様](https://scalardb.scalar-labs.com/ja-jp/docs/latest/scalardb-cluster/authorize-with-abac/)
- [Kelpie Framework](https://github.com/scalar-labs/kelpie)
