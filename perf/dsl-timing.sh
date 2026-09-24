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

# DSL の機能の1回ずつの時間を、使い捨ての環境（docker/perf/compose.yaml）で測る（Build and Test）。
# 対象DB の種類ごとに、対象DB とアプリを起動し、100 テーブル × 100 カラムのスキーマ large を作り、
# 生成・表示（照合）・ダウンロード・適用・投入・戻しの時間と、生成の内訳（DEBUG のログ）、ヒープとコンテナのメモリを記録する。
# 10MB 近くの DSL（正しいもの・誤りを含むもの）は、生成した DSL から perf/make-large-dsl.mjs で結果の置き場に作る。
#
# 使い方（プロジェクトのルートで。事前に WAR とイメージを作る。配備に使うタグ local は上書きしない）:
#   ./gradlew :backend:bootWar && docker build -t mastersmith:perf-dsl .
#   MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh postgres mysql mariadb
#   MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --ui postgres   # 画面の時間（perf/ui/dsl-ui-timing.mjs）も測る
# 追加の確かめ（どれも上の測定の後に行う。組み合わせてよい）:
#   --lang     英語のロケールのブラウザーで、照合の警告と投入の誤りの message・画面の文言が英語か（perf/ui/dsl-ui-lang.mjs、U5-LANG-E2E）
#   --pattern  重い正規表現を多数含む DSL の投入と、直後の普通の DSL の投入の時間（perf/make-pattern-dsl.mjs、U2-PATTERN-COMPILE）
#   --storage  10MB の DSL の投入→適用を 21 回くり返し、H2 のファイルの大きさとコンテナのメモリを記録し、アプリを止めて
#              起動し直した後の大きさ・止めた時間と終わり方・データの無事を記録（U4-STORAGE。最後に行う）
# 環境変数:
#   MASTERSMITH_IMAGE_TAG         使うイメージのタグ（既定 local）
#   MASTERSMITH_CONTAINER_CPUS    アプリのコンテナの CPU の上限（既定 4）
#   MASTERSMITH_CONTAINER_MEMORY  アプリのコンテナのメモリの上限（既定 2g。配備と同じ値。要件の条件は 1g）
#   REPEAT                        生成・表示を繰り返す回数（既定 3）
#   STORAGE_ROUNDS                --storage の投入→適用の回数（既定 21。履歴の上限 20 を1回超える）
#   PERF_DB_URL                   内部DB の接続先（MASTERSMITH_DB_URL）を上書きする（比べるとき。例: jdbc:h2:file:/app/data/mastersmith）
#   OUT_DIR                       結果の置き場（既定 build/perf-results/dsl-<日時>）
#   KEEP=1                        終わっても使い捨ての環境を消さない（種類は1つだけ。後で down -v と一時ディレクトリの削除を手で行う）
#
# 秘密情報（署名鍵・仮の管理者のパスワード・対象DB のパスワード）は乱数で作り、ホームの下の一時ディレクトリ（権限 700）に置く。
# 値は表示せず、終わったら消す。配備したアプリ（プロジェクト mastersmith）には触れない（別のプロジェクト名 mastersmith-perf）。
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
PERF_COMPOSE="${ROOT}/docker/perf/compose.yaml"
PROJECT=mastersmith-perf
ALL_PROFILES=(--profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb)
BASE=http://127.0.0.1:18080
API="${BASE}/api/admin/dsl"
APP=mastersmith-perf-app-1
REPEAT=${REPEAT:-3}
STORAGE_ROUNDS=${STORAGE_ROUNDS:-21}
export MASTERSMITH_IMAGE_TAG=${MASTERSMITH_IMAGE_TAG:-local}
export MASTERSMITH_CONTAINER_CPUS=${MASTERSMITH_CONTAINER_CPUS:-4}
export MASTERSMITH_CONTAINER_MEMORY=${MASTERSMITH_CONTAINER_MEMORY:-2g}
OUT_DIR=${OUT_DIR:-${ROOT}/build/perf-results/dsl-$(date +%Y%m%d-%H%M%S)}
ADMIN_EMAIL=perf-admin@example.test

compose() { docker compose -p "${PROJECT}" -f "${PERF_COMPOSE}" "$@"; }
log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
now_ms() { perl -MTime::HiRes=time -e 'printf "%d", time * 1000'; }
rand() { openssl rand -base64 24 | tr -d '/+=' | cut -c1-24; }

ui=0 pattern=0 storage=0 lang=0
kinds=()
for arg in "$@"; do
  case "${arg}" in
    --ui) ui=1 ;;
    --pattern) pattern=1 ;;
    --storage) storage=1 ;;
    --lang) lang=1 ;;
    postgres | mysql | mariadb) kinds+=("${arg}") ;;
    *)
      echo "使い方: $0 [--ui] [--lang] [--pattern] [--storage] <postgres|mysql|mariadb>..." >&2
      exit 1
      ;;
  esac
done
if [ "${KEEP:-0}" = 1 ] && ((${#kinds[@]} != 1)); then
  echo "KEEP=1 のときは対象DB の種類を1つだけ指定してください。" >&2
  exit 1
fi
if ((${#kinds[@]} == 0)); then
  echo "使い方: $0 [--ui] [--lang] [--pattern] [--storage] <postgres|mysql|mariadb>..." >&2
  exit 1
fi
if [ -n "$(compose "${ALL_PROFILES[@]}" ps -aq 2> /dev/null)" ]; then
  echo "使い捨ての環境（${PROJECT}）が既に動いています。先に down -v で片付けてください。" >&2
  exit 1
fi
if ! docker image inspect "mastersmith:${MASTERSMITH_IMAGE_TAG}" > /dev/null 2>&1; then
  echo "イメージ mastersmith:${MASTERSMITH_IMAGE_TAG} がありません。README の手順で作ってください。" >&2
  exit 1
fi

# 1. 一時ディレクトリと環境ファイル（値は表示しない）。colima の VM から見える場所が要るためホームの下に置く。
TMP=$(mktemp -d "${HOME}/.mastersmith-perf-XXXXXX")
chmod 700 "${TMP}"
cleanup() {
  if [ "${KEEP:-0}" = 1 ]; then
    log "KEEP=1 のため使い捨ての環境を残しました（一時ディレクトリ ${TMP}）"
    return
  fi
  compose "${ALL_PROFILES[@]}" down -v --remove-orphans > /dev/null 2>&1 || true
  rm -rf "${TMP}"
}
trap cleanup EXIT

admin_password=$(rand)
db_admin_password=$(rand)
db_reader_password=$(rand)
(
  umask 077
  {
    printf 'MASTERSMITH_AUTH_SIGNING_KEY=%s\n' "$(openssl rand -base64 32)"
    printf 'MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL=%s\n' "${ADMIN_EMAIL}"
    printf 'MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD=%s\n' "${admin_password}"
    # 生成の内訳（TargetSchemaDslGenerator の DEBUG）を出す。環境変数ではクラスの名前を書けないため、パッケージで指定する。
    printf 'LOGGING_LEVEL_CHERRY_MASTERSMITH_DSLMANAGE_GENERATE=DEBUG\n'
    # GC の記録（エポックのミリ秒つき）を内部DB のボリュームに書き、操作ごとのヒープの最大を読む。
    printf 'MASTERSMITH_JAVA_OPTIONS=-Xlog:gc:file=/app/data/gc.log:timemillis\n'
    # 内部DB の接続先を上書きして比べるとき（例: DEFRAG_ALWAYS なし）。資格情報を含まない H2 のファイルの URL だけを渡す。
    if [ -n "${PERF_DB_URL:-}" ]; then
      printf 'MASTERSMITH_DB_URL=%s\n' "${PERF_DB_URL}"
    fi
  } > "${TMP}/app.env"
  {
    printf 'POSTGRES_PASSWORD=%s\nMYSQL_ROOT_PASSWORD=%s\nMARIADB_ROOT_PASSWORD=%s\n' \
      "${db_admin_password}" "${db_admin_password}" "${db_admin_password}"
    printf 'MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD=%s\n' "${db_reader_password}"
  } > "${TMP}/targetdb.env"
  printf '{"email":"%s","password":"%s"}' "${ADMIN_EMAIL}" "${admin_password}" > "${TMP}/login.json"
  # 画面の時間の台本（perf/ui/dsl-ui-timing.mjs）が読む。
  printf 'PERF_ADMIN_EMAIL=%s\nPERF_ADMIN_PASSWORD=%s\n' "${ADMIN_EMAIL}" "${admin_password}" > "${TMP}/ui.env"
)
unset admin_password db_admin_password
export MASTERSMITH_PERF_ENV_FILE="${TMP}/app.env" MASTERSMITH_PERF_TARGETDB_ENV_FILE="${TMP}/targetdb.env"

mkdir -p "${OUT_DIR}"
log "結果の置き場: ${OUT_DIR}"
{
  echo "date=$(date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "commit=$(git -C "${ROOT}" rev-parse --short HEAD)"
  echo "image=mastersmith:${MASTERSMITH_IMAGE_TAG} $(docker image inspect -f '{{.Id}}' "mastersmith:${MASTERSMITH_IMAGE_TAG}")"
  echo "cpus=${MASTERSMITH_CONTAINER_CPUS} memory=${MASTERSMITH_CONTAINER_MEMORY} repeat=${REPEAT} storage_rounds=${STORAGE_ROUNDS}"
  echo "db_url=${PERF_DB_URL:-（既定。application.yaml の MASTERSMITH_DB_URL の既定値）}"
  echo "colima=$(colima list 2> /dev/null | awk 'NR==2 {print "cpus=" $4 " memory=" $5}')"
} > "${OUT_DIR}/env.txt"

# 2. 要求の道具。アクセストークンは5分で切れるため、操作ごとにログインし直す（トークンは一時ディレクトリのヘッダーのファイルに置く）。
login() {
  local token
  token=$(curl -sS -f -X POST -H 'Content-Type: application/json' -H "Origin: ${BASE}" \
    --data-binary "@${TMP}/login.json" "${BASE}/api/auth/login" | jq -r .accessToken)
  (
    umask 077
    printf 'Authorization: Bearer %s\n' "${token}" > "${TMP}/auth.header"
  )
}

# 1回の要求を測り、timings.tsv に1行足す。引数: 名前 メソッド パス [curl の引数...]
# 列: 種類 名前 HTTP 秒（curl の time_total） 受けたバイト 送ったバイト 開始（エポック ms） 終了（エポック ms）
measure() {
  local name=$1 method=$2 path=$3
  shift 3
  login
  local start end result
  start=$(now_ms)
  result=$(curl -sS -o "${KIND_DIR}/resp-${name}.body" -X "${method}" -H "@${TMP}/auth.header" -H "Origin: ${BASE}" \
    -H 'Accept-Language: ja' -w '%{http_code}\t%{time_total}\t%{size_download}\t%{size_upload}' "$@" "${API}${path}")
  end=$(now_ms)
  printf '%s\t%s\t%s\t%s\t%s\n' "${KIND}" "${name}" "${result}" "${start}" "${end}" >> "${OUT_DIR}/timings.tsv"
  log "${KIND} ${name}: HTTP・秒・受信・送信 = ${result//$'\t'/ }"
  # 重い処理は同時に1つのため、応答の後の片付け（GC など）が次の測定に重ならないよう少し待つ。
  sleep 2
}

# 3. 対象DB の種類ごとに測る。
run_kind() {
  KIND=$1
  KIND_DIR="${OUT_DIR}/${KIND}"
  mkdir -p "${KIND_DIR}"
  local type port database service client
  case "${KIND}" in
    postgres)
      type=postgresql port=5432 database=business
      client=(psql -q -v ON_ERROR_STOP=1 -U target_admin -d business)
      ;;
    mysql)
      type=mysql port=3306 database=large
      client=(sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root')
      ;;
    mariadb)
      type=mariadb port=3306 database=large
      client=(sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --user=root')
      ;;
  esac
  service="targetdb-${KIND}"
  (
    umask 077
    printf 'MASTERSMITH_TARGET_DB_TYPE=%s\nMASTERSMITH_TARGET_DB_HOST=%s\nMASTERSMITH_TARGET_DB_PORT=%s\n' "${type}" "${service}" "${port}"
    printf 'MASTERSMITH_TARGET_DB_DATABASE=%s\nMASTERSMITH_TARGET_DB_SCHEMA=large\nMASTERSMITH_TARGET_DB_USERNAME=mastersmith_reader\n' "${database}"
    printf 'MASTERSMITH_TARGET_DB_PASSWORD=%s\n' "${db_reader_password}"
  ) > "${TMP}/app-targetdb.env"
  export MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE="${TMP}/app-targetdb.env"

  log "${KIND}: 対象DB を起動し、100 × 100 のスキーマ large を作ります"
  compose --profile "${service}" up -d --wait "${service}" > /dev/null
  local s0 s1
  s0=$(now_ms)
  "${ROOT}/docker/targetdb/generate-large-schema.sh" "${KIND}" | compose exec -T "${service}" "${client[@]}" > /dev/null
  s1=$(now_ms)
  log "${KIND}: スキーマを作りました（$(((s1 - s0) / 1000)) 秒）"

  log "${KIND}: アプリを起動します"
  compose up -d --wait --force-recreate app > /dev/null
  sleep 5

  # 3.1 既定の DSL の生成（NFR1.6・NFR1.7・NFR1.4・NFR2.5）と、その後のプレビューの表示（照合、NFR1.8）。
  local i
  for ((i = 1; i <= REPEAT; i++)); do
    measure "generate-${i}" POST /preview/generate
    measure "preview-after-generate-${i}-first" GET /preview
    measure "preview-after-generate-${i}-second" GET /preview
  done
  # 3.2 プレビュー中のダウンロード（生成した DSL、NFR1.11 の想定の規模・NFR2.5 の大きさ）。
  measure download-preview-generated GET /preview/download
  cp "${KIND_DIR}/resp-download-preview-generated.body" "${KIND_DIR}/generated.yaml"
  # 3.3 適用（前の版を作る）と、想定の規模の DSL の投入・表示（NFR1.8）。
  apply_current_preview apply-generated
  measure submit-generated POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/generated.yaml"
  measure preview-after-submit-generated-first GET /preview
  measure preview-after-submit-generated-second GET /preview
  # 3.4 10MB 近くの DSL（正しいもの・誤りを含むもの）を作る。
  node "${ROOT}/perf/make-large-dsl.mjs" "${KIND_DIR}/generated.yaml" "${KIND_DIR}" > "${KIND_DIR}/large-dsl.txt"
  # 3.5 10MB の投入（NFR1.5・NFR1.8）・表示（NFR1.8）・ダウンロード（NFR1.11）・適用・適用中のダウンロード（NFR1.11）。
  measure submit-10mb-valid POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/valid-10mb.yaml"
  measure preview-after-submit-10mb-first GET /preview
  measure preview-after-submit-10mb-second GET /preview
  measure download-preview-10mb GET /preview/download
  apply_current_preview apply-10mb
  measure download-applied-10mb GET /applied/download
  # 3.6 誤りを含む 10MB の投入（NFR1.5。422 でプレビューは変わらない）。
  measure submit-10mb-invalid-many POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/invalid-many-10mb.yaml"
  measure submit-10mb-invalid-tail POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/invalid-tail-10mb.yaml"
  # 3.7 履歴からの戻し（NFR1.8）。想定の規模の版（生成した DSL を適用した版）と 10MB の版。
  measure history GET /history
  local generated_rev large_rev
  generated_rev=$(jq -r '[.[] | select(.current | not)][0].revisionId' "${KIND_DIR}/resp-history.body")
  large_rev=$(jq -r '[.[] | select(.current)][0].revisionId' "${KIND_DIR}/resp-history.body")
  measure restore-generated POST "/history/${generated_rev}/restore"
  measure preview-after-restore-generated GET /preview
  measure restore-10mb POST "/history/${large_rev}/restore"
  measure preview-after-restore-10mb GET /preview

  # 3.8 画面の時間（任意）。想定の規模のプレビュー（生成した DSL）を置いてから測る。
  if [ "${ui}" = 1 ]; then
    measure generate-for-ui POST /preview/generate
    log "${KIND}: 画面の時間を測ります"
    node "${ROOT}/perf/ui/dsl-ui-timing.mjs" --env-file "${TMP}/ui.env" --base "${BASE}" \
      --large "${KIND_DIR}/valid-10mb.yaml" --out "${KIND_DIR}/ui-timing.json" || log "${KIND}: 画面の時間の測定に失敗しました"
  fi

  # 3.9 英語のロケールの画面（任意、U5-LANG-E2E）。
  if [ "${lang}" = 1 ]; then
    log "${KIND}: 英語のロケールの画面を確かめます"
    node "${ROOT}/perf/ui/dsl-ui-lang.mjs" --env-file "${TMP}/ui.env" --base "${BASE}" \
      --out "${KIND_DIR}/ui-lang.json" || log "${KIND}: 英語のロケールの確かめに失敗しました"
  fi

  # 3.10 重い正規表現（任意、U2-PATTERN-COMPILE）。普通の DSL を3回（前）→ 重い DSL を3回 → 普通の DSL を3回（直後）。
  if [ "${pattern}" = 1 ]; then
    node "${ROOT}/perf/make-pattern-dsl.mjs" "${KIND_DIR}" > "${KIND_DIR}/pattern-dsl.txt"
    for ((i = 1; i <= 3; i++)); do
      measure "pattern-plain-before-${i}" POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/pattern-plain.yaml"
    done
    for ((i = 1; i <= 3; i++)); do
      measure "pattern-heavy-${i}" POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/pattern-heavy.yaml"
    done
    # 直後の投入は待たずに送る（measure の後の待ち 2 秒を置かず、重い投入の応答のすぐ後に普通の投入を送る回を1つ取る）。
    local_start=$(now_ms)
    login
    result=$(curl -sS -o "${KIND_DIR}/resp-pattern-heavy-4-nowait.body" -X POST -H "@${TMP}/auth.header" -H "Origin: ${BASE}" \
      -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/pattern-heavy.yaml" \
      -w '%{http_code}\t%{time_total}\t%{size_download}\t%{size_upload}' "${API}/preview?source=UPLOAD")
    printf '%s\t%s\t%s\t%s\t%s\n' "${KIND}" pattern-heavy-4-nowait "${result}" "${local_start}" "$(now_ms)" >> "${OUT_DIR}/timings.tsv"
    local_start=$(now_ms)
    result=$(curl -sS -o "${KIND_DIR}/resp-pattern-plain-immediate.body" -X POST -H "@${TMP}/auth.header" -H "Origin: ${BASE}" \
      -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/pattern-plain.yaml" \
      -w '%{http_code}\t%{time_total}\t%{size_download}\t%{size_upload}' "${API}/preview?source=UPLOAD")
    printf '%s\t%s\t%s\t%s\t%s\n' "${KIND}" pattern-plain-immediate "${result}" "${local_start}" "$(now_ms)" >> "${OUT_DIR}/timings.tsv"
    log "${KIND} pattern-plain-immediate: ${result//$'\t'/ }"
    for ((i = 1; i <= 3; i++)); do
      measure "pattern-plain-after-${i}" POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/pattern-plain.yaml"
    done
  fi

  # 3.11 保存の量（任意、U4-STORAGE）。10MB の DSL を、埋め草の先頭の行に回の番号を入れて（大きさは同じ）投入→適用を STORAGE_ROUNDS 回（既定 21）。
  # 回ごとに H2 のファイルの大きさ・コンテナのメモリ（今と最大）・履歴の件数を記録する。最後にプレビューも1件置き（最大の状態）、
  # アプリを止めて（compose stop）起動し直した後（DEFRAG_ALWAYS なら H2 が閉じるときに詰める）の大きさ・止めた時間・データの無事も記録する。
  if [ "${storage}" = 1 ]; then
    printf 'round\th2_mv_db_bytes\tdata_dir_kb\tmemory_current_bytes\tmemory_peak_bytes\tanon_bytes\tfile_bytes\thistory_count\tpreview\n' > "${KIND_DIR}/storage.tsv"
    storage_row() {
      local round=$1 bytes kb current peak anon file count preview
      bytes=$(docker exec "${APP}" stat -c %s /app/data/mastersmith.mv.db)
      kb=$(docker exec "${APP}" du -sk /app/data | cut -f1)
      current=$(docker exec "${APP}" cat /sys/fs/cgroup/memory.current)
      peak=$(docker exec "${APP}" cat /sys/fs/cgroup/memory.peak)
      # memory.current はページキャッシュ（file。回収できる）を含むため、プロセスのメモリ（anon）と分けて記録する。
      anon=$(docker exec "${APP}" awk '$1 == "anon" {print $2}' /sys/fs/cgroup/memory.stat)
      file=$(docker exec "${APP}" awk '$1 == "file" {print $2}' /sys/fs/cgroup/memory.stat)
      login
      count=$(curl -sS -f -H "@${TMP}/auth.header" "${API}/history" | jq length)
      preview=$(curl -sS -f -H "@${TMP}/auth.header" "${API}/status" | jq -r 'if .preview then "yes" else "no" end')
      printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "${round}" "${bytes}" "${kb}" "${current}" "${peak}" "${anon}" "${file}" "${count}" "${preview}" >> "${KIND_DIR}/storage.tsv"
    }
    storage_row start
    for ((i = 1; i <= STORAGE_ROUNDS; i++)); do
      ROUND=${i} perl -pe 'if (!$done && s/^# padding/sprintf("# p%06d", $ENV{ROUND})/e) { $done = 1 }' \
        "${KIND_DIR}/valid-10mb.yaml" > "${KIND_DIR}/storage-round.yaml"
      measure "storage-submit-${i}" POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/storage-round.yaml"
      apply_current_preview "storage-apply-${i}"
      storage_row "${i}"
    done
    ROUND=$((STORAGE_ROUNDS + 1)) perl -pe 'if (!$done && s/^# padding/sprintf("# p%06d", $ENV{ROUND})/e) { $done = 1 }' \
      "${KIND_DIR}/valid-10mb.yaml" > "${KIND_DIR}/storage-round.yaml"
    measure "storage-submit-$((STORAGE_ROUNDS + 1))-preview" POST '/preview?source=UPLOAD' -H 'Content-Type: application/yaml' --data-binary "@${KIND_DIR}/storage-round.yaml"
    storage_row "$((STORAGE_ROUNDS + 1))-preview"
    rm -f "${KIND_DIR}/storage-round.yaml"
    # 起動し直すと GC の記録と memory.peak が始めからになるため、先に取っておく。
    docker exec "${APP}" cat /app/data/gc.log > "${KIND_DIR}/gc-before-restart.log" 2> /dev/null || true
    docker exec "${APP}" cat /sys/fs/cgroup/memory.peak > "${KIND_DIR}/memory-peak-before-restart.txt" 2> /dev/null || true
    docker exec "${APP}" cat /sys/fs/cgroup/memory.stat > "${KIND_DIR}/memory-stat-before-restart.txt" 2> /dev/null || true
    # 止める前のデータ（適用中の DSL の SHA-256・履歴の版と件数・プレビューの previewId）を記録し、起動し直した後と比べる。
    storage_snapshot() {
      local label=$1
      login
      {
        printf 'applied_sha256=%s\n' "$(curl -sS -f -H "@${TMP}/auth.header" "${API}/applied/download" | shasum -a 256 | cut -d' ' -f1)"
        printf 'preview_sha256=%s\n' "$(curl -sS -f -H "@${TMP}/auth.header" "${API}/preview/download" | shasum -a 256 | cut -d' ' -f1)"
        curl -sS -f -H "@${TMP}/auth.header" "${API}/status" | jq -r '"preview_id=\(.preview.previewId // "none")"'
        curl -sS -f -H "@${TMP}/auth.header" "${API}/history" | jq -r '"history_count=\(length)", "history=\([.[] | "\(.revisionId):\(.dslHash)"] | join(","))", "history_current=\([.[] | select(.current) | .revisionId] | join(","))"'
      } > "${KIND_DIR}/snapshot-${label}.txt"
    }
    storage_snapshot before-stop
    # 止める（compose の stop_grace_period 45 秒の内に終わらなければ SIGKILL）。時間・終わり方（exit code・OOMKilled）・終了時のログを記録する。
    # SIGTERM で正常に終わった JVM の exit code は 143、SIGKILL（猶予切れ・OOM）は 137 になる。
    local stop_start stop_end
    stop_start=$(now_ms)
    compose stop app > /dev/null
    stop_end=$(now_ms)
    docker inspect "${APP}" --format 'ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}} Status={{.State.Status}} StartedAt={{.State.StartedAt}} FinishedAt={{.State.FinishedAt}}' > "${KIND_DIR}/stop-state.txt"
    printf 'stop_seconds=%s.%03d\n' "$(((stop_end - stop_start) / 1000))" "$(((stop_end - stop_start) % 1000))" >> "${KIND_DIR}/stop-state.txt"
    docker logs "${APP}" > "${KIND_DIR}/app-before-restart.log" 2>&1
    # 止めている間の H2 のファイル（閉じた後の大きさ）。同じイメージを一時のコンテナで起動し、ボリュームを読むだけにする。
    docker run --rm --entrypoint sh -v "${PROJECT}_perf-data:/d:ro" "mastersmith:${MASTERSMITH_IMAGE_TAG}" \
      -c 'ls -l /d; du -sk /d' > "${KIND_DIR}/data-while-stopped.txt"
    local start_start start_end
    start_start=$(now_ms)
    compose up -d --wait app > /dev/null
    start_end=$(now_ms)
    printf 'start_to_healthy_seconds=%s.%03d\nhealth=%s\n' "$(((start_end - start_start) / 1000))" "$(((start_end - start_start) % 1000))" \
      "$(docker inspect "${APP}" --format '{{.State.Health.Status}}')" >> "${KIND_DIR}/stop-state.txt"
    storage_row after-restart
    storage_snapshot after-restart
    if diff "${KIND_DIR}/snapshot-before-stop.txt" "${KIND_DIR}/snapshot-after-restart.txt" > /dev/null; then
      echo 'data_intact=yes' >> "${KIND_DIR}/stop-state.txt"
    else
      echo 'data_intact=no' >> "${KIND_DIR}/stop-state.txt"
    fi
    # 履歴のすべての版を戻し、プレビューの本文の SHA-256 が履歴の dslHash と一致するかを確かめる（詰め直しで本文が壊れていないか）。
    # 戻しはプレビューを置き換えるため、止める前後の比べの後に行う。
    local rev hash got ok=0 total=0
    login
    curl -sS -f -H "@${TMP}/auth.header" "${API}/history" | jq -r '.[] | "\(.revisionId) \(.dslHash)"' > "${KIND_DIR}/history-after-restart.txt"
    while read -r rev hash; do
      total=$((total + 1))
      login
      curl -sS -o /dev/null -X POST -H "@${TMP}/auth.header" -H "Origin: ${BASE}" "${API}/history/${rev}/restore" || true
      got=$(curl -sS -H "@${TMP}/auth.header" "${API}/preview/download" | shasum -a 256 | cut -d' ' -f1)
      if [ "${got}" = "${hash}" ]; then ok=$((ok + 1)); else log "${KIND}: 版 ${rev} の本文が dslHash と一致しません"; fi
    done < "${KIND_DIR}/history-after-restart.txt"
    printf 'restore_all_match=%s/%s\n' "${ok}" "${total}" >> "${KIND_DIR}/stop-state.txt"
    log "${KIND}: 止めて起動し直しました（$(tr '\n' ' ' < "${KIND_DIR}/stop-state.txt")）"
  fi

  # 4. 記録を集める（アプリのログ・GC の記録・コンテナの状態）。
  docker logs "${APP}" > "${KIND_DIR}/app.log" 2>&1
  { cat "${KIND_DIR}/gc-before-restart.log" 2> /dev/null || true; docker exec "${APP}" cat /app/data/gc.log 2> /dev/null || true; } > "${KIND_DIR}/gc.log"
  docker exec "${APP}" cat /sys/fs/cgroup/memory.peak > "${KIND_DIR}/memory-peak.txt" 2> /dev/null || true
  docker inspect "${APP}" --format 'OOMKilled={{.State.OOMKilled}} ExitCode={{.State.ExitCode}} Status={{.State.Status}}' > "${KIND_DIR}/state.txt"
  grep '既定の DSL の生成の内訳' "${KIND_DIR}/app.log" > "${KIND_DIR}/generate-breakdown.log" || true
  if [ "${KEEP:-0}" != 1 ]; then
    compose "${ALL_PROFILES[@]}" down -v --remove-orphans > /dev/null 2>&1
  fi
}

# 今のプレビューを適用する（状態の previewId を読んで送る）。
apply_current_preview() {
  local name=$1 preview_id
  login
  preview_id=$(curl -sS -f -H "@${TMP}/auth.header" "${API}/status" | jq -r .preview.previewId)
  printf '{"previewId":"%s"}' "${preview_id}" > "${TMP}/apply.json"
  measure "${name}" POST /apply -H 'Content-Type: application/json' --data-binary "@${TMP}/apply.json"
}

printf 'kind\tname\thttp\tseconds\tbytes_down\tbytes_up\tstart_ms\tend_ms\n' > "${OUT_DIR}/timings.tsv"
for kind in "${kinds[@]}"; do
  run_kind "${kind}"
done

# 5. まとめ（操作ごとのヒープの最大は GC の記録から、perf/dsl-timing-report.mjs で出す）。
node "${ROOT}/perf/dsl-timing-report.mjs" "${OUT_DIR}" > "${OUT_DIR}/summary.md"
log "まとめ: ${OUT_DIR}/summary.md"
