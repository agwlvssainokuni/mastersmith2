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

# 使い捨ての環境（または内部DB の複写）の内部DB（組み込みの H2）に、H2 の Shell で SQL を流す（Intent 261025-deps-perf-carryover の FR3.6）。
# H2 の jar の探し方と Shell の起動をここだけに置く（perf/README.md の手順は、これを呼ぶ）。
#
# 使い方（プロジェクトのルートで）:
#   ./perf/h2-shell.sh [--read-only] <ボリュームの名前または複写のディレクトリ> <SQL>
#   <ボリューム…>  Docker のボリュームの名前（例: mastersmith-perf_perf-data）、または / で始まる複写のディレクトリ
#                  （ホームの下に置く。macOS の一時ディレクトリは colima の VM から見えない）。中に mastersmith.mv.db がある
#   --read-only    ボリュームを :ro で付け、ACCESS_MODE_DATA=r で開く（書き込まない。複写を数えるとき）
# 接続先の URL を替えるとき（app.env の MASTERSMITH_DB_URL の接続の設定を使うときなど）は、環境変数 H2_URL に渡す
# （コンテナの中のパス。例: jdbc:h2:file:/data/mastersmith;RETENTION_TIME=0）。--read-only のときは ACCESS_MODE_DATA=r を足す。
# アプリが対象の内部DB を開いている間は、H2 のファイルのロックで失敗する。先にアプリを止める（docker compose stop app）。
# SQL の結果は標準出力に出る。個人に関する値を含みうる SQL は、結果を表示しない（> /dev/null）か、件数だけを出す形にする。
set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=perf/versions.env
source perf/versions.env

mode=rw
if [ "${1:-}" = "--read-only" ]; then
    mode=ro
    shift
fi
if [ $# -ne 2 ]; then
    sed -n '/^# 使い方/,/^# SQL の結果/p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
fi
volume=$1
sql=$2

h2_jar=$(ls "${HOME}"/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar 2> /dev/null | head -n 1 || true)
if [ -z "${h2_jar}" ]; then
    echo "H2 の jar（2.4.240）が Gradle のキャッシュにありません。./gradlew :backend:classes を一度流してください。" >&2
    exit 2
fi

url=${H2_URL:-jdbc:h2:file:/data/mastersmith}
mount="${volume}:/data"
if [ "${mode}" = "ro" ]; then
    url="${url};ACCESS_MODE_DATA=r"
    mount="${mount}:ro"
fi

exec docker run --rm -u 10001:10001 -v "${mount}" -v "${h2_jar}:/h2.jar:ro" "${PERF_JRE_IMAGE}" \
    java -cp /h2.jar org.h2.tools.Shell -url "${url}" -user sa -password "" -sql "${sql}"
