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

# コンテナのメモリの上限と JVM の起動の設定が、環境変数どおりに効くことを確かめる（F3・F4 の修正の再発防止）。
# あわせて、アプリのコンテナに見本の対象DB の値を渡さない環境変数の分け方を確かめる（260924-followup-fixes の FR6.2）。
#   実行: ./docker/check-container-limits.sh （プロジェクトのルートで。事前に ./gradlew :backend:bootWar && docker compose build app）
#   別のタグのイメージを確かめる: MASTERSMITH_IMAGE_TAG=<タグ> ./docker/check-container-limits.sh
# 確かめること:
#   1. compose.yaml・docker/perf/compose.yaml の app の mem_limit が、MASTERSMITH_CONTAINER_MEMORY なしで 2g、768m を渡すと 768m
#   2. JVM の設定の口（MASTERSMITH_JAVA_OPTIONS）なしで、最大ヒープがコンテナのメモリの上限の 75%（空の値でも同じ）
#   3. MASTERSMITH_JAVA_OPTIONS で渡した割合（60%）とヒープ以外の上限（MaxMetaspaceSize=128m）が効く
#   4. java がコンテナの PID 1 で動き（停止の合図を直接受け取る）、-Duser.timezone=Asia/Tokyo が残る
#   5. compose.yaml の app は .env だけを読み、environment に MASTERSMITH_SAMPLE_TARGETDB_* を持たない。見本の対象DB の
#      3つのサービスは .env.targetdb を読み、environment にパスワードを持たない（値は展開も表示もしない）
# アプリは起動しない（JVM の -version だけを小さなメモリの上限で動かす）。そのため秘密情報は要らず、配備したアプリを止めずに実行できる。
# .env は読まない（compose の変数の展開には空の一時ファイルを使い、env_file の中身は展開しない）。
# 期待と違う点があれば、期待の値と実際の値を出して 1 で終わる。前提（Docker・イメージ）が無いときは 2 で終わる。
set -euo pipefail

cd "$(dirname "$0")/.."

readonly IMAGE="mastersmith:${MASTERSMITH_IMAGE_TAG:-local}"
# JVM を動かすときのコンテナのメモリの上限（小さくして、今の VM でも配備したアプリと並べて動かせるようにする）。
readonly JVM_LIMIT=512m
readonly JVM_LIMIT_BYTES=536870912
# 最大ヒープは GC の区切りに合わせて丸められるため、割合から計算した値との差をこの幅まで認める。
readonly HEAP_TOLERANCE_BYTES=$((4 * 1024 * 1024))

failures=0

pass() {
    printf 'OK  %s\n' "$1"
}

fail() {
    printf 'NG  %s\n' "$1"
    failures=$((failures + 1))
}

tmp_dir=$(mktemp -d)
trap 'status=$?; rm -rf "${tmp_dir}"; exit "${status}"' EXIT
readonly EMPTY_ENV="${tmp_dir}/empty.env"
: > "${EMPTY_ENV}"

# ---- 前提の確認 ----
if ! docker info > /dev/null 2>&1; then
    echo "Docker の実行環境に接続できません（colima start などで起動してください）。" >&2
    exit 2
fi
if ! docker image inspect "${IMAGE}" > /dev/null 2>&1; then
    echo "イメージ ${IMAGE} がありません（./gradlew :backend:bootWar && docker compose build app で作ってください）。" >&2
    exit 2
fi

# ---- 1. メモリの上限（compose の展開）----

# compose のファイルを展開し、app の mem_limit（バイト数）を出す。第2引数が空なら MASTERSMITH_CONTAINER_MEMORY を消して展開する。
compose_mem_limit() {
    local compose_file=$1 memory=$2 config
    # 展開に失敗したときは空になり、呼び出し元が「取り出せない」として失敗にする（compose のエラーは標準エラーに出る）。
    if [ -n "${memory}" ]; then
        config=$(env MASTERSMITH_CONTAINER_MEMORY="${memory}" MASTERSMITH_PERF_ENV_FILE="${EMPTY_ENV}" \
            docker compose --env-file "${EMPTY_ENV}" -f "${compose_file}" config --no-env-resolution --format json app) || true
    else
        config=$(env -u MASTERSMITH_CONTAINER_MEMORY MASTERSMITH_PERF_ENV_FILE="${EMPTY_ENV}" \
            docker compose --env-file "${EMPTY_ENV}" -f "${compose_file}" config --no-env-resolution --format json app) || true
    fi
    # 展開した設定そのものは表示しない。mem_limit の数だけを取り出す。
    printf '%s\n' "${config}" | grep -o '"mem_limit": *"[0-9]*"' | grep -o '[0-9][0-9]*' || true
}

check_mem_limit() {
    local compose_file=$1 memory=$2 expected=$3 label actual
    label="${memory:-（変数なし）}"
    actual=$(compose_mem_limit "${compose_file}" "${memory}")
    if [ "${actual}" = "${expected}" ]; then
        pass "${compose_file} の mem_limit: MASTERSMITH_CONTAINER_MEMORY=${label} で ${expected}"
    else
        fail "${compose_file} の mem_limit: MASTERSMITH_CONTAINER_MEMORY=${label} で 期待 ${expected}、実際 ${actual:-（取り出せない）}"
    fi
}

for compose_file in compose.yaml docker/perf/compose.yaml; do
    check_mem_limit "${compose_file}" "" 2147483648
    check_mem_limit "${compose_file}" 768m 805306368
done

# ---- 2〜4. JVM の起動の設定（イメージの ENTRYPOINT）----

# イメージを小さなメモリの上限で起動し、JVM の設定の値・PID・システムプロパティを出して終わる（アプリは起動しない）。
# 引数は docker run の追加の指定（-e MASTERSMITH_JAVA_OPTIONS=... など）。
# JVM が起動に失敗したときも出力を返し、呼び出し元の確かめで失敗にする（エラーの内容は失敗の表示の後に出す）。
run_jvm() {
    docker run --rm --network none -m "${JVM_LIMIT}" "$@" "${IMAGE}" \
        -Xlog:os=info:stdout:pid -XX:+PrintFlagsFinal -XshowSettings:properties -version 2>&1 || true
}

# -XX:+PrintFlagsFinal の出力から、名前の一致する設定の値を出す。
flag_value() {
    printf '%s\n' "$2" | awk -v name="$1" '$2 == name && value == "" { value = $4 } END { print value }'
}

check_heap_percent() {
    local label=$1 output=$2 percent=$3 expected actual diff
    expected=$((JVM_LIMIT_BYTES * percent / 100))
    actual=$(flag_value MaxHeapSize "${output}")
    if [ -z "${actual}" ]; then
        fail "${label}: MaxHeapSize を読み取れない（JVM が起動していない見込み。出力の先頭を次に示す）"
        printf '%s\n' "${output}" | sed -n 's/^/    /; 1,5p'
        return
    fi
    diff=$((actual - expected))
    if [ "${diff#-}" -le "${HEAP_TOLERANCE_BYTES}" ]; then
        pass "${label}: 最大ヒープが上限 ${JVM_LIMIT} の ${percent}%（${actual}）"
    else
        fail "${label}: 最大ヒープが上限 ${JVM_LIMIT} の ${percent}% でない（期待 約 ${expected}、実際 ${actual}）"
    fi
}

check_flag() {
    local label=$1 output=$2 name=$3 expected actual
    expected=$4
    actual=$(flag_value "${name}" "${output}")
    if [ "${actual}" = "${expected}" ]; then
        pass "${label}: ${name} が ${expected}"
    else
        fail "${label}: ${name} が 期待 ${expected}、実際 ${actual:-（読み取れない）}"
    fi
}

# java がコンテナの PID 1 であること（sh を exec で置き換えている）と、タイムゾーンの引数が残ることを確かめる。
check_launch() {
    local label=$1 output=$2 pids
    pids=$(printf '%s\n' "${output}" | sed -n 's/^\[\([0-9][0-9]*\)\] .*/\1/p' | sort -u | tr '\n' ' ')
    if [ "${pids}" = "1 " ]; then
        pass "${label}: java が PID 1 で動く"
    else
        fail "${label}: java が PID 1 で動いていない（JVM のログの PID: ${pids:-（読み取れない）}）"
    fi
    if printf '%s\n' "${output}" | grep 'user.timezone = Asia/Tokyo' > /dev/null; then
        pass "${label}: -Duser.timezone=Asia/Tokyo が効く"
    else
        fail "${label}: -Duser.timezone=Asia/Tokyo が効いていない"
    fi
}

# 直す前のイメージは、引数をアプリの起動の後ろに渡すため、JVM を確かめる起動の代わりにアプリが起動してしまう。
# そのため、ENTRYPOINT が JVM の設定の口を持たないイメージは、起動せずに 2〜4 を失敗とする。
entrypoint=$(docker image inspect "${IMAGE}" --format '{{json .Config.Entrypoint}}')
if ! printf '%s' "${entrypoint}" | grep 'MASTERSMITH_JAVA_OPTIONS' > /dev/null; then
    fail "${IMAGE} の ENTRYPOINT に JVM の設定の口（MASTERSMITH_JAVA_OPTIONS）が無い（ENTRYPOINT: ${entrypoint}）"
    fail "${IMAGE}: 2〜4 の確かめ（最大ヒープの割合・ヒープ以外の上限・PID 1・タイムゾーン）を行えない"
else
    # 2. 既定の動作（変数なし・空の値）
    output=$(run_jvm)
    check_heap_percent "変数なし" "${output}" 75
    check_launch "変数なし" "${output}"

    output=$(run_jvm -e MASTERSMITH_JAVA_OPTIONS=)
    check_heap_percent "空の値" "${output}" 75

    # 3. JVM の設定の口（割合とヒープ以外の上限）
    output=$(run_jvm -e 'MASTERSMITH_JAVA_OPTIONS=-XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=128m')
    check_heap_percent "MASTERSMITH_JAVA_OPTIONS あり" "${output}" 60
    check_flag "MASTERSMITH_JAVA_OPTIONS あり" "${output}" MaxMetaspaceSize 134217728

    # 4. 設定の口を使っても起動の形が変わらない
    check_launch "MASTERSMITH_JAVA_OPTIONS あり" "${output}"
fi

# ---- 5. 環境変数の分け方（compose.yaml）----

# compose.yaml の1つのサービスの設定を展開する（.env は読まない。env_file の中身は展開しない）。展開した設定は表示しない。
compose_service_config() {
    docker compose --env-file "${EMPTY_ENV}" \
        --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb \
        -f compose.yaml config --no-env-resolution --format json "$1" || true
}

# 設定の env_file のパスのうち、ファイルの名前だけを並べる（例: .env）。
env_file_names() {
    # 見つからないときも失敗にせず空を返し、呼び出し元の確かめで失敗にする（set -e で途中で抜けないようにする）。
    { printf '%s\n' "$1" | grep -o '"path": *"[^"]*"' | sed 's/.*\/\([^/"]*\)"$/\1/' | tr '\n' ' '; } || true
}

# 設定の environment の項目の名前だけを並べる（値は出さない）。
environment_names() {
    {
        printf '%s\n' "$1" \
            | awk '/^      "environment": \{/ { inside = 1; next } inside && /^      \}/ { inside = 0 } inside' \
            | grep -o '^ *"[A-Za-z0-9_]*":' | tr -d ' ":' | tr '\n' ' '
    } || true
}

config=$(compose_service_config app)
files=$(env_file_names "${config}")
if [ "${files}" = ".env " ]; then
    pass "compose.yaml の app は .env だけを読む"
else
    fail "compose.yaml の app の env_file が .env だけでない（${files:-読み取れない}）"
fi
if [ -n "${config}" ] && ! printf '%s\n' "${config}" | grep 'MASTERSMITH_SAMPLE_TARGETDB_' > /dev/null; then
    pass "compose.yaml の app の設定に MASTERSMITH_SAMPLE_TARGETDB_ で始まる名前が無い"
else
    fail "compose.yaml の app の設定に MASTERSMITH_SAMPLE_TARGETDB_ で始まる名前がある（または設定を読み取れない）"
fi

for service in targetdb-postgres targetdb-mysql targetdb-mariadb; do
    config=$(compose_service_config "${service}")
    files=$(env_file_names "${config}")
    if [ "${files}" = ".env.targetdb " ]; then
        pass "compose.yaml の ${service} は .env.targetdb を読む"
    else
        fail "compose.yaml の ${service} の env_file が .env.targetdb でない（${files:-読み取れない}）"
    fi
    names=$(environment_names "${config}")
    if [ -n "${names}" ] && ! printf '%s' "${names}" | grep 'PASSWORD' > /dev/null; then
        pass "compose.yaml の ${service} の environment にパスワードが無い（${names% }）"
    else
        fail "compose.yaml の ${service} の environment にパスワードがある（または読み取れない）: ${names:-（なし）}"
    fi
done

if [ "${failures}" -gt 0 ]; then
    printf '%d 件が期待と違います（イメージ %s）。\n' "${failures}" "${IMAGE}"
    exit 1
fi
printf 'すべて期待どおりです（イメージ %s）。\n' "${IMAGE}"
