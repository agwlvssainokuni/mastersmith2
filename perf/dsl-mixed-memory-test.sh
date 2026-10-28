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

# perf/dsl-mixed-memory.sh の summarize（まとめの合否）の試し（Intent 261027-perf-targets-review の FR1.2・FR1.5）。
# 本体は流さず、complete_rows と summarize の関数だけを取り出して、作り物の TSV と peak.txt に与える。
# memory.peak / memory.max の比 0.9101（新しい線 0.95 で満たす・前の線 0.9 で満たさない）、0.9600（どちらも満たさない）、
# 0.8500（どちらも満たす）に、線ちょうどの 0.9500（0.95 未満ではないので満たさない）と、memory.max が欠けた入力（比は NA で judge を出さない）を足した5件で、judge（新しい線）・judge_090（前の線）・anon_max_ratio の出力を照らす。
# 数値はすべて架空の値。Docker は使わない。
#
# 使い方（プロジェクトのルートで）: bash perf/dsl-mixed-memory-test.sh
# 終了コード: すべて期待どおりなら 0、1件でも外れたら 1。
set -euo pipefail

SCRIPT="$(cd "$(dirname "$0")" && pwd)/dsl-mixed-memory.sh"
WORK=$(mktemp -d)
trap 'rm -rf "${WORK}"' EXIT

# 本体の先頭から、指定した関数の定義（行頭の「名前() {」から行頭の「}」まで）だけを取り出して読み込む
extract() { awk -v fn="$1" '$0 ~ "^" fn "\\(\\) \\{$" { on = 1 } on { print } on && /^}$/ { exit }' "${SCRIPT}"; }
eval "$(extract complete_rows)"
eval "$(extract summarize)"

MAX=1000000
HEADER=$'time_utc\tmemory_current\tmemory_peak\tanon\tfile\tinactive_file\tactive_file\tkernel\tmv_db_bytes'

# 作り物の TSV（完全な行 2 つ。anon の最大は max の 0.5）と peak.txt を作る。引数: 名前 peak の値
make_case() {
  local name=$1 peak=$2
  {
    printf '%s\n' "${HEADER}"
    printf '2026-01-01T00:00:00Z\t400000\t%s\t300000\t50000\t1000\t1000\t2000\t10\n' "${peak}"
    printf '2026-01-01T00:00:01Z\t500000\t%s\t500000\t60000\t1000\t1000\t2000\t20\n' "${peak}"
  } > "${WORK}/${name}.tsv"
  printf 'memory.peak=%s\nmemory.max=%s\n' "${peak}" "${MAX}" > "${WORK}/${name}.peak"
}

# 期待の表: 名前|peak|judge|judge_090
CASES=(
  "r09101|910100|under-0.95|not-under-0.9"
  "r09500|950000|not-under-0.95|not-under-0.9"
  "r09600|960000|not-under-0.95|not-under-0.9"
  "r08500|850000|under-0.95|under-0.9"
)

fail=0
for c in "${CASES[@]}"; do
  IFS='|' read -r name peak want_judge want_090 <<< "${c}"
  make_case "${name}" "${peak}"
  out=$(summarize "${WORK}/${name}.tsv" "${WORK}/${name}.peak")
  got_judge=$(sed -n 's/^judge=//p' <<< "${out}")
  got_090=$(sed -n 's/^judge_090=//p' <<< "${out}")
  got_anon=$(sed -n 's/^anon_max_ratio=//p' <<< "${out}")
  if [ "${got_judge}" = "${want_judge}" ] && [ "${got_090}" = "${want_090}" ] && [ "${got_anon}" = "0.5000" ]; then
    echo "ok   ${name}: judge=${got_judge} judge_090=${got_090} anon_max_ratio=${got_anon}"
  else
    echo "FAIL ${name}: judge 期待 ${want_judge} 実際 '${got_judge}', judge_090 期待 ${want_090} 実際 '${got_090}', anon_max_ratio 期待 0.5000 実際 '${got_anon}'" >&2
    fail=1
  fi
done
# memory.max が欠けた入力: 比は NA になり、judge と judge_090 の行は出ない
make_case rmissing 910100
printf 'memory.peak=910100\n' > "${WORK}/rmissing.peak"
out=$(summarize "${WORK}/rmissing.tsv" "${WORK}/rmissing.peak")
if grep -q '^peak_ratio=NA$' <<< "${out}" && ! grep -q '^judge' <<< "${out}"; then
  echo "ok   rmissing: peak_ratio=NA judge なし"
else
  echo "FAIL rmissing: memory.max が欠けたとき peak_ratio=NA で judge の行が出ない形を期待" >&2
  fail=1
fi
exit "${fail}"
