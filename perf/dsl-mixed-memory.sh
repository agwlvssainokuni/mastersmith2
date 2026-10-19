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

# dslMixed の1回の測りで、コンテナのメモリ（cgroup v2 の memory.peak と memory.stat の内訳）と内部DB のファイルの大きさを
# 1秒ごとに記録する（Intent 261016-business-data-views の U5、機能設計 3節、cicd-pipeline.md 4節、計画の Step 11）。
# 1回分だけを受け持つ。配備したアプリを止める・起動し直す、caffeinate -dims、pmset の確かめ、環境の作り分け（既定・試し）、
# 回の数え方、片付けは perf/README.md の (b) 節の手順で行う。
#
# 使い方（プロジェクトのルートで。perf/README.md の (b) 節の手順の中で呼ぶ）:
#   K6_IMAGE=<perf/README.md の k6d と同じ固定のイメージ> MASTERSMITH_IMAGE_TAG=<タグ> \
#     ./perf/dsl-mixed-memory.sh <一時ディレクトリ> <回の名前> <DSL の置き場>
#   <一時ディレクトリ>  KEEP=1 の perf/dsl-timing.sh が作ったもの（app.env・targetdb.env・app-targetdb.env、
#                       PERF_USER_PASSWORD を足した ui.env がある）
#   <回の名前>          例: default-1・default-2・retention-1・retention-2（英数字と - だけ）
#   <DSL の置き場>      valid-10mb.yaml がある所（例: build/perf-results/dsl-k6/postgres）。記録はその隣の memory/<回の名前>/ に置く
#   記録のループだけを流すとき（Step 12 の空回し。読むだけで、コンテナを止めない・書かない）:
#   ./perf/dsl-mixed-memory.sh --loop-only <コンテナの名前> <秒> <TSV の置き場>
#   記録の TSV の完全な行の数・捨てた行の数・最後の完全な行の時刻とまとめだけを出すとき（切れた行の扱いの確かめ。Docker を使わない）:
#   ./perf/dsl-mixed-memory.sh --tsv-check <TSV の置き場>
#
# 流れ: 作り直し → 始める前の確かめ → 時刻の差（始め）→ 記録のループを後ろで起こす → 20 秒後に dslMixed（VUS 10・3 分）
#   → 終わりの後 60 秒 → ループを止める → 時刻の差（終わり）→ memory.peak・memory.max・状態 → 内訳のまとめ → WARN の件数
#   → アプリを止めて内部DB の設定の値（RETENTION_TIME）を読み取りだけで読む（peak を読んだ後に止める。止めると peak が消えるため）
#
# 記録に書くのは数値・時刻・状態・ロガーの名前と件数だけ（NFR4.6）。秘密は読まない（k6 には --env-file で ui.env を渡すだけ）。
# 記録のループは docker exec 1回で回し、--privileged・--user・追加の権限は付けない（NFR4.9）。
#
# 環境変数:
#   K6_IMAGE                     k6 のイメージ（必須。perf/README.md の k6d と同じ版とダイジェスト。固定先を増やさないため既定を置かない）
#   MASTERSMITH_IMAGE_TAG        使い捨てのアプリのイメージのタグ（必須。perf/dsl-timing.sh に渡したもの）
#   MASTERSMITH_CONTAINER_CPUS   アプリのコンテナの CPU の上限（既定 4）
#   MASTERSMITH_CONTAINER_MEMORY アプリのコンテナのメモリの上限（既定 2g。上げない。BR4.3）
#   VUS・DURATION                dslMixed のログインの同時の数と長さ（既定 10・3m）
#   LOOP_LIMIT_SECONDS           記録のループの秒の上限（既定 320 = 前置き 20 + 場面 180 + 後置き 60 + 余裕 60）
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
PERF_COMPOSE="${ROOT}/docker/perf/compose.yaml"
PROJECT=mastersmith-perf
APP=mastersmith-perf-app-1
PRE_SECONDS=20
POST_SECONDS=60
STAT_KEYS='anon file inactive_file active_file kernel'
TSV_HEADER=$'time_utc\tmemory_current\tmemory_peak\tanon\tfile\tinactive_file\tactive_file\tkernel\tmv_db_bytes'

log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }

# コンテナの中で回す記録のループ（bash）。引数: 秒の上限。1秒ごとに1行（列は TSV_HEADER の順）を標準出力に書く。
# memory.stat は名前で引いて固定の順に出し（ファイルの並びに頼らない）、無いキー・読めないファイルは NA にする。
# 出力先が閉じたら（手元の側を止めたら）、次の書き込みで終わる。
read -r -d '' LOOP_SCRIPT << 'EOS' || true
limit=$1
end=$(($(date +%s) + limit))
while [ "$(date +%s)" -lt "${end}" ]; do
  t=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  cur=$(cat /sys/fs/cgroup/memory.current 2> /dev/null || echo NA)
  peak=$(cat /sys/fs/cgroup/memory.peak 2> /dev/null || echo NA)
  stat_cols=$(awk 'BEGIN { n = split("anon file inactive_file active_file kernel", k, " ") }
    { v[$1] = $2 }
    END { for (i = 1; i <= n; i++) printf "\t%s", ((k[i] in v) ? v[k[i]] : "NA") }' /sys/fs/cgroup/memory.stat 2> /dev/null) ||
    stat_cols=$'\tNA\tNA\tNA\tNA\tNA'
  [ -n "${stat_cols}" ] || stat_cols=$'\tNA\tNA\tNA\tNA\tNA'
  size=$(stat -c %s /app/data/mastersmith.mv.db 2> /dev/null || echo NA)
  printf '%s\t%s\t%s%s\t%s\n' "${t}" "${cur}" "${peak}" "${stat_cols}" "${size}" || exit 0
  sleep 1
done
EOS

# 始める前の確かめ（コンテナの中で回す）。外れたものを1行ずつ出す（ファイル・キーの名前だけ）。
read -r -d '' PRECHECK_SCRIPT << 'EOS' || true
for f in memory.peak memory.current memory.max memory.stat; do
  [ -r "/sys/fs/cgroup/${f}" ] || echo "file ${f}"
done
for k in anon file inactive_file active_file kernel; do
  grep -q "^${k} " /sys/fs/cgroup/memory.stat 2> /dev/null || echo "key ${k}"
done
[ -e /app/data/mastersmith.mv.db ] || echo "file mastersmith.mv.db"
for c in bash awk stat date; do
  command -v "${c}" > /dev/null 2>&1 || echo "command ${c}"
done
EOS

# 記録のループを後ろで起こす。引数: コンテナ 秒 TSV の置き場。見出しの行は手元の側で書く。
# docker exec の処理を直接後ろで起こし、その PID を loop_pid に持つ（trap と stop_loop が止める）。
# ループには、この台本の1回ごとに一意の目印を引数で渡す（コンテナの中で自分のループだけを見分けて止めるため）。
loop_pid=""
loop_container=""
LOOP_MARKER="mastersmith-memloop-$$-${RANDOM}${RANDOM}"
start_loop() {
  local container=$1 seconds=$2 tsv=$3
  printf '%s\n' "${TSV_HEADER}" > "${tsv}"
  loop_container=${container}
  docker exec "${container}" bash -c "${LOOP_SCRIPT}" _ "${seconds}" "${LOOP_MARKER}" >> "${tsv}" &
  loop_pid=$!
}

# コンテナの中で、引数に目印を持つ処理（この台本が起こした記録のループ）だけを止める。自分自身は除く。アプリの処理には触れない。
read -r -d '' LOOP_KILL_SCRIPT << 'EOS' || true
marker=$1
for d in /proc/[0-9]*; do
  p=${d#/proc/}
  [ "${p}" = "$$" ] && continue
  if tr '\0' '\n' < "${d}/cmdline" 2> /dev/null | grep -qxF -- "${marker}"; then
    kill "${p}" 2> /dev/null || true
  fi
done
EOS

# 後ろで起こした記録のループを止める（何度呼んでもよい）。手元の docker exec を止めても、コンテナの中のループは
# すぐには終わらないため、コンテナの中の目印を持つ処理も止める（コードのレビューの R-03）。
stop_loop() {
  if [ -n "${loop_pid}" ]; then
    kill "${loop_pid}" 2> /dev/null || true
    wait "${loop_pid}" 2> /dev/null || true
    loop_pid=""
  fi
  if [ -n "${loop_container}" ]; then
    docker exec "${loop_container}" bash -c "${LOOP_KILL_SCRIPT}" _ "${LOOP_MARKER}" > /dev/null 2>&1 || true
    loop_container=""
  fi
}

# 途中で止まっても（失敗・Ctrl-C・TERM）、後ろで起こした記録のループを必ず止める（コードのレビューの R-03）。
trap 'stop_loop' EXIT
trap 'stop_loop; exit 130' INT
trap 'stop_loop; exit 143' TERM

# 記録の TSV から完全な行だけを出す（見出しは出さない）。引数: TSV の置き場。
# kill で書きかけのまま切れた最後の行（改行で終わらない行）と、9 列・時刻と数か NA の形でない行を捨てる。
complete_rows() {
  local tsv=$1
  if [ -s "${tsv}" ] && [ "$(tail -c 1 "${tsv}" | od -An -tx1 | tr -d ' \n')" != 0a ]; then
    sed '$d' "${tsv}"
  else
    cat "${tsv}"
  fi | awk -F '\t' '
    NF == 9 && $1 ~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z$/ {
      ok = 1
      for (i = 2; i <= 9; i++) if ($i !~ /^([0-9]+|NA)$/) ok = 0
      if (ok) print
    }'
}

# まとめを出す（合否は memory.peak / memory.max < 0.9 だけ。並べる値は peak の時の内訳・anon の最大の割合・file と内部DB の
# ファイルの伸び）。引数: TSV の置き場 peak.txt の置き場（無ければ /dev/null。比は NA になる）。
summarize() {
  local tsv=$1 peakfile=$2 all complete
  all=$(awk 'END { print NR }' "${tsv}")
  complete=$(complete_rows "${tsv}" | awk 'END { print NR }')
  echo "dropped_rows=$((all - 1 - complete > 0 ? all - 1 - complete : 0))"
  complete_rows "${tsv}" | awk -F '\t' -v peakfile="${peakfile}" '
    BEGIN {
      while ((getline line < peakfile) > 0) {
        split(line, kv, "=")
        if (kv[1] == "memory.peak") peak = kv[2]
        if (kv[1] == "memory.max") max = kv[2]
      }
    }
    $2 == "NA" { na++; next }
    {
      rows++
      if (rows == 1) { file0 = $5; mv0 = $9 }
      if ($2 + 0 > curmax) { curmax = $2 + 0; at = $1; a = $4; f = $5; inf = $6; ker = $8 }
      if ($4 + 0 > anonmax) anonmax = $4 + 0
      if ($5 + 0 > filemax) filemax = $5 + 0
      if ($9 != "NA" && $9 + 0 > mvmax) mvmax = $9 + 0
    }
    END {
      printf "rows=%d na_rows=%d\n", rows, na
      if (max ~ /^[0-9]+$/ && max > 0) {
        printf "peak_ratio=%.4f\n", peak / max
        printf "judge=%s\n", (peak / max < 0.9 ? "under-0.9" : "not-under-0.9")
        printf "anon_max_ratio=%.4f\n", anonmax / max
      } else {
        print "peak_ratio=NA"
      }
      printf "current_max_at=%s current_max=%d anon=%s file=%s inactive_file=%s kernel=%s\n", at, curmax, a, f, inf, ker
      printf "anon_max=%d file_first=%s file_max=%d file_growth=%d\n", anonmax, file0, filemax, filemax - file0
      printf "mv_db_first=%s mv_db_max=%d mv_db_growth=%d\n", mv0, mvmax, mvmax - mv0
    }'
}

# 最後の完全な行の時刻（UTC）を出す。無ければ空。引数: TSV の置き場
last_complete_utc() {
  complete_rows "$1" | tail -n 1 | cut -f1
}

if [ "${1-}" = --loop-only ]; then
  if (($# != 4)) || ! [[ $3 =~ ^[0-9]{1,4}$ ]]; then
    echo "使い方: $0 --loop-only <コンテナの名前> <秒> <TSV の置き場>" >&2
    exit 1
  fi
  start_loop "$2" "$3" "$4"
  wait "${loop_pid}" || true
  loop_pid=""
  exit 0
fi

if [ "${1-}" = --tsv-check ]; then
  if (($# != 2)) || [ ! -f "$2" ]; then
    echo "使い方: $0 --tsv-check <TSV の置き場>" >&2
    exit 1
  fi
  echo "complete_rows=$(complete_rows "$2" | awk 'END { print NR }')"
  echo "last_complete_utc=$(last_complete_utc "$2")"
  summarize "$2" /dev/null
  exit 0
fi

if (($# != 3)); then
  echo "使い方: $0 <一時ディレクトリ> <回の名前> <DSL の置き場>（--loop-only は台本の冒頭を見てください）" >&2
  exit 1
fi
D=$1 RUN=$2 DATA_DIR=$(cd "$3" && pwd)
: "${K6_IMAGE:?k6 のイメージを K6_IMAGE で渡してください（perf/README.md の k6d と同じ固定の値）}"
: "${MASTERSMITH_IMAGE_TAG:?使い捨てのアプリのイメージのタグを MASTERSMITH_IMAGE_TAG で渡してください}"
if ! [[ ${RUN} =~ ^[A-Za-z0-9-]{1,40}$ ]]; then
  echo "回の名前は英数字と - だけにしてください。" >&2
  exit 1
fi
for f in app.env targetdb.env ui.env; do
  if [ ! -f "${D}/${f}" ]; then
    echo "一時ディレクトリに ${f} がありません。KEEP=1 の perf/dsl-timing.sh で作った場所を渡してください。" >&2
    exit 1
  fi
done
if ! grep -q '^PERF_USER_PASSWORD=' "${D}/ui.env"; then
  echo "ui.env に PERF_USER_PASSWORD がありません。perf/README.md の手順で試験用の利用者を入れて足してください。" >&2
  exit 1
fi
if [ ! -f "${DATA_DIR}/valid-10mb.yaml" ]; then
  echo "DSL の置き場に valid-10mb.yaml がありません。" >&2
  exit 1
fi
OUT=$(dirname "${DATA_DIR}")/memory/${RUN}
if [ -e "${OUT}" ]; then
  echo "記録の置き場 memory/${RUN} が既にあります。回の名前を変えるか、前の記録を移してください。" >&2
  exit 1
fi
mkdir -p "${OUT}"

export MASTERSMITH_PERF_ENV_FILE="${D}/app.env" MASTERSMITH_PERF_TARGETDB_ENV_FILE="${D}/targetdb.env"
if [ -f "${D}/app-targetdb.env" ]; then
  export MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE="${D}/app-targetdb.env"
fi
export MASTERSMITH_CONTAINER_CPUS=${MASTERSMITH_CONTAINER_CPUS:-4}
export MASTERSMITH_CONTAINER_MEMORY=${MASTERSMITH_CONTAINER_MEMORY:-2g}
VUS=${VUS:-10}
DURATION=${DURATION:-3m}
LOOP_LIMIT_SECONDS=${LOOP_LIMIT_SECONDS:-320}

compose() { docker compose -p "${PROJECT}" -f "${PERF_COMPOSE}" "$@"; }

# 手元の時刻（前）・VM の時刻・手元の時刻（後）を読み、範囲（VM−後、VM−前）を記録する。引数: 名前
clock() {
  local h0 v h1
  h0=$(date +%s)
  v=$(docker exec "${APP}" date +%s)
  h1=$(date +%s)
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "${h0}" "${v}" "${h1}" "$((v - h1))" "$((v - h0))" >> "${OUT}/clock.tsv"
}

{
  echo "run=${RUN}"
  echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "commit=$(git -C "${ROOT}" rev-parse --short HEAD)"
  echo "image=mastersmith:${MASTERSMITH_IMAGE_TAG}"
  echo "cpus=${MASTERSMITH_CONTAINER_CPUS} memory=${MASTERSMITH_CONTAINER_MEMORY} vus=${VUS} duration=${DURATION}"
  echo "loop_limit_seconds=${LOOP_LIMIT_SECONDS} pre_seconds=${PRE_SECONDS} post_seconds=${POST_SECONDS}"
} > "${OUT}/run.txt"

# 1. 作り直し（前の memory.peak を消す）
log "${RUN}: 使い捨てのアプリを作り直します"
compose up -d --wait --force-recreate app > /dev/null

# 2. 始める前の確かめ。外れたら、その回を無効として理由（ファイル・キー・道具の名前）を書き、測りを始めない
missing=$(docker exec "${APP}" bash -c "${PRECHECK_SCRIPT}")
if [ -n "${missing}" ]; then
  printf '%s\n' "${missing}" > "${OUT}/invalid.txt"
  log "${RUN}: 始める前の確かめに外れたため、この回は無効です（invalid.txt）"
  exit 3
fi

# 3. 時刻の差（始め）と、記録のループを後ろで起こす
printf 'name\thost_before\tvm\thost_after\tvm_minus_after\tvm_minus_before\n' > "${OUT}/clock.tsv"
clock start
start_loop "${APP}" "${LOOP_LIMIT_SECONDS}" "${OUT}/memory.tsv"
log "${RUN}: 記録のループを始めました（上限 ${LOOP_LIMIT_SECONDS} 秒）。${PRE_SECONDS} 秒後に dslMixed を流します"
sleep "${PRE_SECONDS}"

# 4. dslMixed（k6 の出力は利用者の名前を含むため記録に残さず、一時ディレクトリに置いて件数だけを数える）
k6_start_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
set +e
docker run --rm --network mastersmith-perf_default --env-file "${D}/ui.env" \
  -e SCENARIO=dslMixed -e VUS="${VUS}" -e DURATION="${DURATION}" -e DSL_FILE=/data/valid-10mb.yaml \
  -v "${DATA_DIR}:/data:ro" -v "${ROOT}/perf/k6:/scripts:ro" -v "${OUT}:/out" "${K6_IMAGE}" \
  run --quiet --summary-export=/out/dslMixed.json /scripts/scenarios.js > "${D}/k6-${RUN}.out" 2>&1
k6_exit=$?
set -e
k6_end_vm=$(docker exec "${APP}" date +%s 2> /dev/null || echo NA)
k6_end_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
login_lines=$(grep -o 'loginLoop-user vu=[0-9]* user=[^ "]*' "${D}/k6-${RUN}.out" | wc -l | tr -d ' ' || true)
login_dups=$( (grep -o 'loginLoop-user vu=[0-9]* user=[^ "]*' "${D}/k6-${RUN}.out" || true) | sed 's/.* user=//' | sort | uniq -d | wc -l | tr -d ' ')
rm -f "${D}/k6-${RUN}.out"
{
  echo "k6_start_utc=${k6_start_utc}"
  echo "k6_end_utc=${k6_end_utc}"
  echo "k6_exit=${k6_exit}"
  echo "login_user_lines=${login_lines}"
  echo "login_user_duplicates=${login_dups}"
} > "${OUT}/k6.txt"
log "${RUN}: dslMixed が終わりました（終了コード ${k6_exit}）。${POST_SECONDS} 秒の後置きを記録します"

# 5. 後置きの後にループを止める（macOS には標準の timeout が無いため、PID を kill して wait する）
sleep "${POST_SECONDS}"
stop_loop

# 6. memory.peak・memory.max・memory.stat の5つのキーと状態（止める前に読む。後の計算が失敗しても欠けないよう先に読む）
{
  echo "memory.peak=$(docker exec "${APP}" cat /sys/fs/cgroup/memory.peak)"
  echo "memory.max=$(docker exec "${APP}" cat /sys/fs/cgroup/memory.max)"
  # shellcheck disable=SC2016
  docker exec "${APP}" awk -v keys="${STAT_KEYS}" \
    'BEGIN { n = split(keys, k, " ") } { v[$1] = $2 } END { for (i = 1; i <= n; i++) printf "stat.%s=%s\n", k[i], ((k[i] in v) ? v[k[i]] : "NA") }' \
    /sys/fs/cgroup/memory.stat
  echo "state=$(docker inspect --format '{{.State.Status}} exit={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}}' "${APP}")"
} > "${OUT}/peak.txt"

# 時刻の差（終わり）と後置きの欠け。最後の行が kill で切れていても止まらないよう、完全な行だけで計算し、
# 時刻が読めなければ NA にして続ける（コードのレビューの R-03）。
clock end
last_utc=$(last_complete_utc "${OUT}/memory.tsv")
post_missing=NA
if [ "${k6_end_vm}" != NA ] && [ -n "${last_utc}" ] &&
  last_epoch=$(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "${last_utc}" +%s 2> /dev/null); then
  short=$((k6_end_vm + POST_SECONDS - last_epoch))
  if ((short > 1)); then
    post_missing=${short}
  else
    post_missing=0
  fi
fi
echo "post_missing_seconds=${post_missing}" >> "${OUT}/k6.txt"

# 7. まとめ（完全な行だけを使い、捨てた行の数も出す）
summarize "${OUT}/memory.tsv" "${OUT}/peak.txt" > "${OUT}/summary.txt"

# 8. WARN の件数とロガーごとの内訳（片付けの前に数える。本文は記録しない）
{
  echo "warn_lines=$(docker logs "${APP}" 2>&1 | grep -c '"level":"WARN"' || true)"
  echo "error_lines=$(docker logs "${APP}" 2>&1 | grep -c '"level":"ERROR"' || true)"
  echo "oome_lines=$(docker logs "${APP}" 2>&1 | grep -c 'OutOfMemoryError' || true)"
  (docker logs "${APP}" 2>&1 | grep '"level":"WARN"' | grep -o '"logger":"[^"]*"' || true) | sort | uniq -c | sort -rn
} > "${OUT}/warn.txt"

# 9. アプリを止めて、内部DB の設定の値（RETENTION_TIME）を読み取りだけで読む（cicd-pipeline.md 4節、計画の 4.4 の R-02）。
#    ボリュームを :ro で付け、ACCESS_MODE_DATA=r で開く。Shell は測るアプリのイメージ（同じ JRE）で動かし、イメージの固定先を増やさない。
log "${RUN}: アプリを止めて、内部DB の設定の値を読みます"
compose stop app > /dev/null
h2_jar=$(ls "${HOME}"/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar | head -n 1)
cp "${h2_jar}" "${D}/h2.jar"
set +e
docker run --rm -u 10001:10001 --entrypoint java -v mastersmith-perf_perf-data:/data:ro -v "${D}/h2.jar:/h2.jar:ro" \
  "mastersmith:${MASTERSMITH_IMAGE_TAG}" -cp /h2.jar org.h2.tools.Shell \
  -url 'jdbc:h2:file:/data/mastersmith;ACCESS_MODE_DATA=r' -user sa -password '' \
  -sql "SELECT SETTING_NAME, SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME IN ('RETENTION_TIME')" \
  > "${OUT}/settings.txt" 2>&1
settings_exit=$?
set -e
rm -f "${D}/h2.jar"
echo "settings_exit=${settings_exit}" >> "${OUT}/settings.txt"

log "${RUN}: 終わりました。記録は ${OUT}（アプリは止めたままです。次の回は作り直しから始まります）"
