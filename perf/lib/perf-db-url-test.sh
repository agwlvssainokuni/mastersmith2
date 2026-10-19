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

# PERF_DB_URL の形を確かめる関数（perf/lib/perf-db-url.sh の check_perf_db_url）の試し（Intent 261016-business-data-views の U5、
# security-design.md S1.3、計画の Step 10）。悪い入力と正しい入力を1件ずつ与え、件ごとに「止めた・通した」と期待との一致を出す。
# 止めたときの標準エラーに入力の値が出ていないことを、入力の中の目印の語の件数（0 であること）で確かめる。
# 入力の値は出さない（見本の値で秘密ではないが、形をそろえる）。外部の URL の見本は予約のドメイン（example.invalid）を使う。
#
# 使い方（プロジェクトのルートで）: bash perf/lib/perf-db-url-test.sh
# 終了コード: すべて期待どおりなら 0、1件でも外れたら 1。
set -euo pipefail

# shellcheck source=perf/lib/perf-db-url.sh
source "$(cd "$(dirname "$0")" && pwd)/perf-db-url.sh"

P=jdbc:h2:file:/app/data/mastersmith
NL=$'\n'

# 入力の表: 期待（reject・accept）| 目印の語（標準エラーに出てはならない語。正しい入力は -）| 説明 | 入力
CASES=(
  "reject|RUNSCRIPT|INIT で任意の SQL を走らせる設定|${P};INIT=RUNSCRIPT FROM 'x'"
  "reject|INIT|INIT で RETENTION_TIME を設定する形（一覧から外した）|${P};INIT=SET RETENTION_TIME 0"
  "reject|DEFRAG|;; を含む|${P};DEFRAG_ALWAYS=TRUE;;RETENTION_TIME=0"
  "reject|DEFRAG|末尾の ;|${P};DEFRAG_ALWAYS=TRUE;"
  "reject|jdbc:|前の部分の直後の ; だけ|${P};"
  "reject|tcp|前の部分が jdbc:h2:tcp:|jdbc:h2:tcp://example.invalid/x"
  "reject|postgresql|外部の URL|jdbc:postgresql://example.invalid:5432/x"
  "reject|mastersmith2|前の部分の後ろに文字が続く|${P}2"
  "reject|abc|RETENTION_TIME が数でない|${P};RETENTION_TIME=abc"
  "reject|1234567890|RETENTION_TIME が 10 桁|${P};RETENTION_TIME=1234567890"
  "reject|RETENTION|RETENTION_TIME が負の数|${P};RETENTION_TIME=-1"
  "reject|RETENTION|同じ設定の2回|${P};RETENTION_TIME=0;RETENTION_TIME=5000"
  "reject|RETENTION|改行を含む|${P};RETENTION_TIME=0${NL}DEFRAG_ALWAYS=TRUE"
  "reject|DEFRAG|空白を含む|${P};DEFRAG_ALWAYS=TRUE; RETENTION_TIME=0"
  "reject|retention|名前が小文字|${P};retention_time=0"
  "accept|-|設定なし|${P}"
  "accept|-|DEFRAG_ALWAYS=TRUE だけ|${P};DEFRAG_ALWAYS=TRUE"
  "accept|-|DEFRAG_ALWAYS=TRUE と RETENTION_TIME=0|${P};DEFRAG_ALWAYS=TRUE;RETENTION_TIME=0"
  "accept|-|RETENTION_TIME=45000 と DEFRAG_ALWAYS=TRUE|${P};RETENTION_TIME=45000;DEFRAG_ALWAYS=TRUE"
)

total=0 matched=0 leaked=0
printf '番号\t期待\t結果\t一致\t目印の件数\t値の件数\t説明\n'
for row in "${CASES[@]}"; do
  expect=${row%%|*}
  rest=${row#*|}
  marker=${rest%%|*}
  rest=${rest#*|}
  label=${rest%%|*}
  input=${rest#*|}
  total=$((total + 1))
  if err=$(check_perf_db_url "${input}" 2>&1); then
    result=accept
  else
    result=reject
  fi
  if [ "${result}" = "${expect}" ]; then
    ok=yes
    matched=$((matched + 1))
  else
    ok=NO
  fi
  # 標準エラーに入力の値（目印の語・入力の全体・jdbc: の接頭）が出ていないこと
  marker_count=0
  if [ "${marker}" != - ]; then
    marker_count=$( (grep -o -F -- "${marker}" <<< "${err}" || true) | wc -l | tr -d ' ')
  fi
  value_count=$(grep -c -F -- "jdbc:" <<< "${err}" || true)
  if [ "${marker_count}" != 0 ] || [ "${value_count}" != 0 ]; then
    leaked=$((leaked + 1))
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "${total}" "${expect}" "${result}" "${ok}" "${marker_count}" "${value_count}" "${label}"
done

printf '合計 %s 件・期待どおり %s 件・値の漏れ %s 件\n' "${total}" "${matched}" "${leaked}"
if [ "${matched}" != "${total}" ] || [ "${leaked}" != 0 ]; then
  exit 1
fi
