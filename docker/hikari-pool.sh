#!/usr/bin/env bash
# Copyright 2026 agwlvssainokuni
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# アプリを止めずに内部DB（組み込みの H2）のファイルを詰め直す運用の道具（Intent 260925-storage-memory-fixes の FR1）。
# HikariCP のプールの MBean の標準の操作だけを、JMX で呼ぶ（アプリのコードは使わない。操作は監査ログ・アプリのログに残らない）。
#
# 実行（プロジェクトのルートで）:
#   ./docker/hikari-pool.sh status                 接続の本数と内部DB のファイルの大きさを出す
#   ./docker/hikari-pool.sh compact                一時停止 → 接続の破棄 → 0 本を待つ → ファイルが落ち着くのを待つ → 再開
#   ./docker/hikari-pool.sh resume                 再開だけ行う（再開し忘れ・道具の途中の終わりからの戻し）
# 指定（どの副コマンドにも付けられる。compact の上限は秒）:
#   --container <名前>     対象のコンテナ（既定 mastersmith-app-1。使い捨ての環境は mastersmith-perf-app-1）
#   --zero-wait <秒>       借りている接続が 0 本になるまでの待ちの上限（既定 10）。超えたら詰め直さずに再開して 1 で終わる
#   --settle-wait <秒>     0 本になった後、ファイルが落ち着くまでの待ちの上限（既定 30）
#   --total-limit <秒>     一時停止から再開までの全体の上限（既定 45）。超えそうなら再開を優先する
#   --stable <秒>          ファイルの大きさがこの秒数変わらなければ、詰め直しの終わりとみる（既定 3）
#   --db-file <パス>       内部DB のファイル（既定 /proc/1/root/app/data/mastersmith.mv.db。コンテナの中から見たパス）
#   --pool <名前>          プールの名前（既定 mastersmith-db）
#
# しくみ: アプリと同じ版の JDK のイメージ（JRE のアプリのイメージには attach の道具が無いため）の一時のコンテナを、アプリの
# コンテナと PID・ネットワークの名前空間を共有し、同じ利用者の番号（10001）で動かす。docker/jmx/HikariPoolControl.java を
# 標準入力で渡して実行し、アプリの JVM（PID 1）に attach して、JVM の中だけの JMX の接続（ループバックだけで待ち受け）で操作する。
# JMX を外（PC・ネットワーク）に公開しない。この PC で Docker を使える人だけが操作できる。
#
# 一時停止の間、内部DB を使う要求（ログイン・トークンの更新・監査の記録・DSL の操作・健全性の確認）は再開まで待つ。
# 一時停止が約 30 秒を超えると、健全性の確認が 503 になり、コンテナが unhealthy になる（再開で戻る）。
# compact は同時に1つだけ動く（この PC の上のロック）。途中で失敗・中断（Ctrl-C）しても、再開を必ず試みる。
# 再開に失敗したときは、resume を試し、だめなら docker compose restart app で起動し直す（README の「内部DBのファイルの詰め直し」）。
#
# 終わりの値: 0 成功、1 詰め直していない・終わりを確かめられない（再開は済み）、2 使い方・前提の誤り、3 再開に失敗した。
# 出力は接続の本数・ファイルの大きさ・時間だけで、秘密情報は含まない。
set -euo pipefail

cd "$(dirname "$0")/.."

readonly JDK_IMAGE=eclipse-temurin:25.0.4_7-jdk-noble
readonly APP_UID=10001:10001
readonly TOOL_SOURCE=docker/jmx/HikariPoolControl.java

usage() {
    sed -n '/^# 実行/,/^# 出力は/p' "$0" | sed 's/^# \{0,1\}//' >&2
}

if [ $# -lt 1 ]; then
    usage
    exit 2
fi

command=$1
shift
case "${command}" in
    status | compact | resume) ;;
    -h | --help | help)
        usage
        exit 0
        ;;
    *)
        echo "知らない副コマンドです: ${command}" >&2
        usage
        exit 2
        ;;
esac

container=mastersmith-app-1
tool_args=()
# 念のための再開に渡す指定（プールの名前だけ）。
resume_args=()
while [ $# -gt 0 ]; do
    case "$1" in
        --container)
            [ $# -ge 2 ] || { echo "--container の値がありません" >&2; exit 2; }
            container=$2
            shift 2
            ;;
        --zero-wait | --settle-wait | --total-limit | --stable | --db-file | --pool)
            [ $# -ge 2 ] || { echo "$1 の値がありません" >&2; exit 2; }
            tool_args+=("$1" "$2")
            if [ "$1" = "--pool" ]; then
                resume_args+=("$1" "$2")
            fi
            shift 2
            ;;
        *)
            echo "知らない指定です: $1" >&2
            exit 2
            ;;
    esac
done

if ! printf '%s' "${container}" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9_.-]*$'; then
    echo "--container は英数字と _ . - だけで指定してください" >&2
    exit 2
fi

# ---- 前提の確認 ----
if ! docker info > /dev/null 2>&1; then
    echo "Docker の実行環境に接続できません（colima start などで起動してください）。" >&2
    exit 2
fi
if [ "$(docker inspect --format '{{.State.Running}}' "${container}" 2> /dev/null || true)" != "true" ]; then
    echo "コンテナ ${container} が動いていません。" >&2
    exit 2
fi

# 道具を1回動かす。引数は副コマンド。
run_tool() {
    docker run --rm -i \
        --pid "container:${container}" \
        --network "container:${container}" \
        -u "${APP_UID}" \
        -e TZ=Asia/Tokyo \
        --entrypoint sh \
        "${JDK_IMAGE}" \
        -c 'cat > /tmp/HikariPoolControl.java && exec java /tmp/HikariPoolControl.java "$@"' \
        hikari-pool "$@" < "${TOOL_SOURCE}"
}

if [ "${command}" != "compact" ]; then
    set +e
    run_tool "${command}" ${tool_args[@]+"${tool_args[@]}"}
    exit $?
fi

# ---- compact: 同時に1つだけ（この PC の上のロック。mkdir は作れた1つだけが成功する）----
lock_dir="${TMPDIR:-/tmp}/mastersmith-hikari-pool-${container}.lock"
if ! mkdir "${lock_dir}" 2> /dev/null; then
    echo "同じコンテナ（${container}）の compact がほかで動いています（ロック ${lock_dir}）。" >&2
    echo "動いていないのにこの表示が出るときは、前回の compact が途中で強制終了されています。" >&2
    echo "  ./docker/hikari-pool.sh resume --container ${container} で再開してから、ロックのディレクトリを消してください。" >&2
    exit 2
fi
interrupted=0
trap 'interrupted=1' INT TERM
trap 'rmdir "${lock_dir}" 2> /dev/null || true' EXIT

set +e
run_tool compact ${tool_args[@]+"${tool_args[@]}"}
status=$?
set -e

# 道具が失敗・中断で終わったときは、念のためもう一度再開を試みる（再開は何度呼んでも同じ結果になる）。
# 終わりの値 2（使い方・前提の誤り）は一時停止の前に終わっているため、再開は要らない。
if { [ "${status}" -ne 0 ] && [ "${status}" -ne 2 ]; } || [ "${interrupted}" -ne 0 ]; then
    echo "compact が 0 以外（${status}）で終わったか中断されたため、念のため再開を試みます。" >&2
    set +e
    run_tool resume ${resume_args[@]+"${resume_args[@]}"}
    resume_status=$?
    set -e
    if [ "${resume_status}" -ne 0 ]; then
        echo "再開できませんでした。docker compose restart app で起動し直してください（README の「内部DBのファイルの詰め直し」）。" >&2
        exit 3
    fi
    # 再開は済んだため、中断や想定の外の終わりの値（130 など）は「詰め直していない・終わりを確かめられない」（1）にそろえる。
    if [ "${interrupted}" -ne 0 ] || { [ "${status}" -ne 1 ] && [ "${status}" -ne 3 ]; }; then
        status=1
    fi
fi
exit "${status}"
