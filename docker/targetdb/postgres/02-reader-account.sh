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

# 手元で試す対象DB（PostgreSQL）に、読み取りの権限だけのアカウント mastersmith_reader を作る（初めて起動したときだけ動く）。
# パスワードは環境変数 MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD（.env から compose が渡す）から取り、ここには書かない。
# 値は psql の変数で渡し、コマンド行や SQL の文字列の連結に入れない。
set -euo pipefail

if [ -z "${MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD:-}" ]; then
  echo "MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD が空です。.env に値を入れてから作り直してください。" >&2
  exit 1
fi

psql -v ON_ERROR_STOP=1 -v reader_password="${MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD}" \
  --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<'SQL'
CREATE ROLE mastersmith_reader LOGIN PASSWORD :'reader_password';
GRANT USAGE ON SCHEMA sales TO mastersmith_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA sales TO mastersmith_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA sales GRANT SELECT ON TABLES TO mastersmith_reader;
SQL
