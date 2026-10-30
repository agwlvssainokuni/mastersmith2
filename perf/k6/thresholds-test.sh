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

# 業務データの読み取りの場面の p95 の線の試し（Intent 261027-perf-targets-review の FR1.1・FR1.4・FR2.6）と、他テーブル参照の読み取りの
# 場面（Intent 261029-reference-lookup-cleanup の U1、NFR2.1）の線の試し。
# k6 の inspect（台本を読み込むだけ。アプリ・対象DB・ネットワークには触れない）で場面ごとの閾値を取り出し、期待の表と照らす。
# 場面名の取り違えで緩み過ぎた場面が無いことも、表の全件の一致で確かめる。
#
# 使い方（プロジェクトのルートで）: bash perf/k6/thresholds-test.sh
# 前提: Docker（colima）が動いていること、jq が使えること。Docker が無いときは飛ばさずに失敗する。
# 終了コード: すべて期待どおりなら 0、1件でも外れたら 1。
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
# shellcheck source=perf/versions.env
source "${ROOT}/perf/versions.env"

if ! docker info > /dev/null 2>&1; then
  echo "Docker が動いていません。colima start で起動し、README の DOCKER_HOST を渡してからやり直してください（飛ばしません）。" >&2
  exit 1
fi
command -v jq > /dev/null 2>&1 || {
  echo "jq が必要です。" >&2
  exit 1
}

# 期待の表: 場面名|iteration_duration の閾値
CASES=(
  "bizSearchFirst|p(95)<1000"
  "bizSearchContains|p(95)<3000"
  "bizSearchLast|p(95)<2000"
  "bizSearchLastSorted|p(95)<2000"
  "bizDetail|p(95)<1000"
  "bizDefinition|p(95)<1000"
  "bizSearchWide|max>=0"
  "refDefinition200|p(95)<1000"
  "refDefinition201|p(95)<1000"
  "refPickerEquals|p(95)<1000"
  "refLabel|p(95)<1000"
  "refPickerContains|p(95)<3000"
)

fail=0
for c in "${CASES[@]}"; do
  name=${c%%|*}
  want=${c#*|}
  # コンテナの起動の一時的な失敗で空になることがあるため、読めなかったときだけ1回やり直す
  out=''
  for _ in 1 2; do
    out=$(docker run --rm -e "SCENARIO=${name}" -v "${ROOT}/perf/k6:/scripts:ro" "${K6_IMAGE}" \
      inspect --include-system-env-vars /scripts/scenarios.js 2> /dev/null) && break
    out='{}'
  done
  got=$(jq -r --arg k "iteration_duration{scenario:${name}}" '.thresholds[$k] // [] | join(",")' <<< "${out}")
  checks=$(jq -r --arg k "checks{scenario:${name}}" '.thresholds[$k] // [] | join(",")' <<< "${out}")
  if [ "${got}" = "${want}" ] && [ "${checks}" = "rate==1" ]; then
    echo "ok   ${name}: iteration_duration ${got}, checks ${checks}"
  else
    echo "FAIL ${name}: iteration_duration 期待 ${want} 実際 '${got}', checks 期待 rate==1 実際 '${checks}'" >&2
    fail=1
  fi
done
exit "${fail}"
