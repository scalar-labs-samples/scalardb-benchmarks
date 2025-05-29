#!/bin/bash

# ABACベンチマーク（min, mid, max）を順に実行するスクリプト
# 物理メモリの80%をJavaヒープサイズとして使用
# Datadog APM統合サポート

set -e

# Datadog APM設定のデフォルト値
DATADOG_APM_ENABLED=false
DATADOG_SERVICE="scalardb-benchmarks"
DATADOG_ENV="dev"
DATADOG_LOGS_INJECTION=true
DATADOG_AGENT_PATH="./dd-java-agent.jar"

# 色付きの出力用
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ログ関数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# ヘルプメッセージの表示
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "ABACベンチマーク実行スクリプト - Datadog APM統合サポート"
    echo ""
    echo "OPTIONS:"
    echo "  --enable-datadog-apm        Datadog APMを有効にする"
    echo "  --datadog-service SERVICE   Datadogサービス名を指定 (デフォルト: scalardb-benchmarks)"
    echo "  --datadog-env ENV          Datadog環境名を指定 (デフォルト: dev)"
    echo "  --datadog-agent-path PATH  Datadog Agentファイルパスを指定 (デフォルト: ./dd-java-agent.jar)"
    echo "  --disable-logs-injection   Datadogログインジェクションを無効にする"
    echo "  -h, --help                 このヘルプを表示"
    echo ""
    echo "環境変数:"
    echo "  DATADOG_APM_ENABLED        APM有効化 (true/false)"
    echo "  DATADOG_SERVICE            サービス名"
    echo "  DATADOG_ENV               環境名"
    echo "  DATADOG_AGENT_PATH        Agentファイルパス"
    echo ""
}

# コマンドライン引数の処理
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --enable-datadog-apm)
                DATADOG_APM_ENABLED=true
                shift
                ;;
            --datadog-service)
                DATADOG_SERVICE="$2"
                shift 2
                ;;
            --datadog-env)
                DATADOG_ENV="$2"
                shift 2
                ;;
            --datadog-agent-path)
                DATADOG_AGENT_PATH="$2"
                shift 2
                ;;
            --disable-logs-injection)
                DATADOG_LOGS_INJECTION=false
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "不明なオプション: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 環境変数による設定のオーバーライド
    if [[ -n "${DATADOG_APM_ENABLED:-}" ]] && [[ "${DATADOG_APM_ENABLED}" == "true" ]]; then
        DATADOG_APM_ENABLED=true
    fi
    if [[ -n "${DATADOG_SERVICE:-}" ]]; then
        DATADOG_SERVICE="${DATADOG_SERVICE}"
    fi
    if [[ -n "${DATADOG_ENV:-}" ]]; then
        DATADOG_ENV="${DATADOG_ENV}"
    fi
    if [[ -n "${DATADOG_AGENT_PATH:-}" ]]; then
        DATADOG_AGENT_PATH="${DATADOG_AGENT_PATH}"
    fi
}

# Datadog Agentのダウンロード
setup_datadog_agent() {
    if [[ "$DATADOG_APM_ENABLED" != "true" ]]; then
        return 0
    fi
    
    log_info "Datadog APM設定を開始します"
    
    # Agentファイルの存在確認
    if [[ -f "$DATADOG_AGENT_PATH" ]]; then
        log_info "Datadog Agentファイルが既に存在します: $DATADOG_AGENT_PATH"
        return 0
    fi
    
    log_info "Datadog Java Agentをダウンロードしています..."
    
    # wgetまたはcurlの確認
    if command -v wget >/dev/null 2>&1; then
        if wget -O "$DATADOG_AGENT_PATH" 'https://dtdg.co/latest-java-tracer' >/dev/null 2>&1; then
            log_success "Datadog Agentのダウンロードが完了しました: $DATADOG_AGENT_PATH"
        else
            log_error "wgetでのダウンロードに失敗しました"
            return 1
        fi
    elif command -v curl >/dev/null 2>&1; then
        if curl -L -o "$DATADOG_AGENT_PATH" 'https://dtdg.co/latest-java-tracer' >/dev/null 2>&1; then
            log_success "Datadog Agentのダウンロードが完了しました: $DATADOG_AGENT_PATH"
        else
            log_error "curlでのダウンロードに失敗しました"
            return 1
        fi
    else
        log_error "wgetまたはcurlが見つかりません。手動でDatadog Agentをダウンロードしてください:"
        log_error "wget -O $DATADOG_AGENT_PATH 'https://dtdg.co/latest-java-tracer'"
        return 1
    fi
    
    # ファイルサイズの確認（破損チェック）
    if [[ ! -s "$DATADOG_AGENT_PATH" ]]; then
        log_error "ダウンロードされたファイルが空または破損している可能性があります"
        rm -f "$DATADOG_AGENT_PATH"
        return 1
    fi
    
    return 0
}

# Datadog APMオプションの構成
configure_datadog_options() {
    if [[ "$DATADOG_APM_ENABLED" != "true" ]]; then
        return 0
    fi
    
    local datadog_opts=""
    datadog_opts="$datadog_opts -javaagent:$DATADOG_AGENT_PATH"
    datadog_opts="$datadog_opts -Ddd.service=$DATADOG_SERVICE"
    datadog_opts="$datadog_opts -Ddd.env=$DATADOG_ENV"
    
    if [[ "$DATADOG_LOGS_INJECTION" == "true" ]]; then
        datadog_opts="$datadog_opts -Ddd.logs.injection=true"
    fi
    
    # 追加のDatadog設定
    datadog_opts="$datadog_opts -Ddd.profiling.enabled=true"
    datadog_opts="$datadog_opts -XX:FlightRecorderOptions=stackdepth=256"
    datadog_opts="$datadog_opts -Ddd.trace.enabled=true"
    
    echo "$datadog_opts"
}

# 物理メモリサイズを取得（KB単位）
get_memory_size() {
    local mem_kb=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    echo $mem_kb
}

# メモリの80%を計算してJavaヒープサイズを設定
calculate_heap_size() {
    local mem_kb=$1
    local mem_mb=$((mem_kb / 1024))
    local heap_mb=$((mem_mb * 80 / 100))
    echo "${heap_mb}m"
}

# 結果ディレクトリの作成
create_results_dir() {
    local results_dir="benchmark-results"
    if [ ! -d "$results_dir" ]; then
        mkdir -p "$results_dir"
        log_info "結果ディレクトリを作成しました: $results_dir"
    fi
}

# ベンチマーク実行関数
run_benchmark() {
    local config_file=$1
    local result_file=$2
    local description=$3
    
    log_info "=== $description の実行を開始します ==="
    log_info "設定ファイル: $config_file"
    log_info "結果ファイル: $result_file"
    
    # Kelpieでベンチマーク実行
    if ./kelpie/bin/kelpie --config "$config_file" > "$result_file" 2>&1; then
        log_success "$description の実行が完了しました"
    else
        log_error "$description の実行中にエラーが発生しました"
        log_error "詳細は $result_file を確認してください"
        return 1
    fi
}

# 結果から主要メトリクスを抽出
extract_metrics() {
    local result_file=$1
    local config_name=$2
    
    if [ ! -f "$result_file" ]; then
        echo "[$config_name] 結果ファイルが見つかりません: $result_file"
        return
    fi
    
    # スループット（ops/sec）を抽出
    local throughput=$(grep "Throughput:" "$result_file" | tail -1 | grep -oE '[0-9]+\.?[0-9]*' | head -1)
    
    # 平均レイテンシー（ms）を抽出
    local avg_latency=$(grep "Mean latency:" "$result_file" | grep -oE '[0-9]+\.?[0-9]*' | head -1)
    
    # 99パーセンタイルレイテンシー（ms）を抽出（95パーセンタイルがないため）
    local p99_latency=$(grep "Latency at 99 percentile:" "$result_file" | grep -oE '[0-9]+\.?[0-9]*' | head -1)
    
    # ユーザー数を設定ファイルから抽出
    local config_file=""
    case $config_name in
        "MIN") config_file="ycsb-multi-user-abac-benchmark-config-min.toml" ;;
        "MID") config_file="ycsb-multi-user-abac-benchmark-config-mid.toml" ;;
        "MAX") config_file="ycsb-multi-user-abac-benchmark-config-max.toml" ;;
    esac
    
    local user_count=""
    local duration=""
    if [ -f "$config_file" ]; then
        user_count=$(grep "user_count" "$config_file" | grep -oE '[0-9]+' | head -1)
        duration=$(grep "run_for_sec" "$config_file" | grep -oE '[0-9]+' | head -1)
    fi
    
    echo "[$config_name Configuration]"
    echo "- Users: ${user_count:-N/A}"
    echo "- Duration: ${duration:-N/A} sec"
    echo "- Throughput: ${throughput:-N/A} ops/sec"
    echo "- Avg Latency: ${avg_latency:-N/A} ms"
    echo "- P99 Latency: ${p99_latency:-N/A} ms"
    echo ""
}

# メイン処理
main() {
    # コマンドライン引数の処理
    parse_arguments "$@"
    
    log_info "ABACベンチマーク実行スクリプトを開始します"
    
    # Datadog APM設定の表示
    if [[ "$DATADOG_APM_ENABLED" == "true" ]]; then
        log_info "=== Datadog APM設定 ==="
        log_info "APM有効: YES"
        log_info "サービス名: $DATADOG_SERVICE"
        log_info "環境名: $DATADOG_ENV"
        log_info "Agentパス: $DATADOG_AGENT_PATH"
        log_info "ログインジェクション: $DATADOG_LOGS_INJECTION"
        log_info "========================"
    else
        log_info "Datadog APM: 無効"
    fi
    
    # Datadog Agentのセットアップ
    if ! setup_datadog_agent; then
        log_warning "Datadog Agentのセットアップに失敗しました。APMなしで続行します。"
        DATADOG_APM_ENABLED=false
    fi
    
    # 物理メモリサイズを取得
    local mem_kb=$(get_memory_size)
    local mem_mb=$((mem_kb / 1024))
    local heap_size=$(calculate_heap_size $mem_kb)
    
    log_info "物理メモリサイズ: ${mem_mb} MB"
    log_info "Javaヒープサイズ（80%）: $heap_size"
    
    # JAVA_OPTSを設定（ヒープサイズ + Datadog設定）
    local base_opts="-Xmx$heap_size -Xms$heap_size"
    local datadog_opts=$(configure_datadog_options)
    
    if [[ -n "$datadog_opts" ]]; then
        export JAVA_OPTS="$base_opts $datadog_opts"
        log_info "JAVA_OPTS設定: $JAVA_OPTS"
        log_success "Datadog APMが有効になりました"
    else
        export JAVA_OPTS="$base_opts"
        log_info "JAVA_OPTS設定: $JAVA_OPTS"
    fi
    
    # 結果ディレクトリの作成
    create_results_dir
    
    # プロジェクトのビルド
    log_info "プロジェクトをビルドします..."
    if ./gradlew shadowJar > build.log 2>&1; then
        log_success "ビルドが完了しました"
    else
        log_error "ビルドに失敗しました。build.logを確認してください"
        exit 1
    fi
    
    # ベンチマーク設定
    local configs=(
        "ycsb-multi-user-abac-benchmark-config-min.toml:benchmark-results/abac-min-result.txt:MIN Configuration"
        "ycsb-multi-user-abac-benchmark-config-mid.toml:benchmark-results/abac-mid-result.txt:MID Configuration"
        "ycsb-multi-user-abac-benchmark-config-max.toml:benchmark-results/abac-max-result.txt:MAX Configuration"
    )
    
    # 各ベンチマークを順次実行
    for config_info in "${configs[@]}"; do
        IFS=':' read -r config_file result_file description <<< "$config_info"
        
        if [ ! -f "$config_file" ]; then
            log_error "設定ファイルが見つかりません: $config_file"
            continue
        fi
        
        run_benchmark "$config_file" "$result_file" "$description"
        
        # 実行間に少し待機
        log_info "次のベンチマークまで10秒待機します..."
        sleep 10
    done
    
    # 結果サマリーの表示
    echo ""
    echo "========================================"
    echo "ABAC Benchmark Results Summary"
    echo "========================================"
    echo "実行時刻: $(date)"
    echo "物理メモリ: ${mem_mb} MB"
    echo "Javaヒープサイズ: $heap_size"
    if [[ "$DATADOG_APM_ENABLED" == "true" ]]; then
        echo "Datadog APM: 有効 (サービス: $DATADOG_SERVICE, 環境: $DATADOG_ENV)"
    else
        echo "Datadog APM: 無効"
    fi
    echo "========================================"
    echo ""
    
    extract_metrics "benchmark-results/abac-min-result.txt" "MIN"
    extract_metrics "benchmark-results/abac-mid-result.txt" "MID"
    extract_metrics "benchmark-results/abac-max-result.txt" "MAX"
    
    echo "========================================"
    echo "詳細な結果は以下のファイルを確認してください:"
    echo "- MIN: benchmark-results/abac-min-result.txt"
    echo "- MID: benchmark-results/abac-mid-result.txt"
    echo "- MAX: benchmark-results/abac-max-result.txt"
    echo "========================================"
    
    log_success "全てのベンチマークが完了しました"
}

# スクリプト実行
main "$@"
