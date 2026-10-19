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

# 手元で試す対象DB（PostgreSQL）に、業務データの書き込みのアカウント mastersmith_writer を作り、権限を当てる（Intent
# 261016-business-data-views の U2）。初めて起動したときに動き、既にあるボリュームには docker compose exec でこの台本を流す（README）。
# 何度流しても同じ結果になる（無ければ作り、毎回パスワードと付与を当て直す）。
#
# 付与するスキーマ: sales（見本のスキーマ。ほかのスキーマを DSL に足すときは、同じ形の付与を足す）。
# 与える権限: スキーマの USAGE、表の SELECT・INSERT・UPDATE・DELETE、sequence の USAGE（自動の採番）と、後から作る表・sequence の
# 既定の権限。DDL（CREATE・DROP・ALTER・TRUNCATE・索引・ビュー・スキーマ）と一時の表は与えない。一時の表を作れないよう、DB の
# TEMPORARY を PUBLIC から取り上げる（読み取りのアカウントも一時の表を使わない）。
# PostgreSQL 14 以前では、public スキーマの CREATE が PUBLIC に与えられているため、別に REVOKE CREATE ON SCHEMA public FROM PUBLIC
# が要る（この見本の 18 では既定で与えられていない）。
#
# パスワードは環境変数 MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD（見本の対象DB では .env.targetdb から、負荷の試験の環境では
# 一時の環境ファイルから compose が渡す）から取り、ここには書かない。値は psql の変数で渡し、コマンド行や SQL の文字列の連結に入れない。
set -euo pipefail

if [ -z "${MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD:-}" ]; then
  echo "MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD が空です。.env.targetdb（負荷の試験の環境では一時の環境ファイル）に値を入れてから作り直してください。" >&2
  exit 1
fi

psql -v ON_ERROR_STOP=1 -v writer_password="${MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD}" \
  --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<'SQL'
SELECT 'CREATE ROLE mastersmith_writer LOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mastersmith_writer') \gexec
ALTER ROLE mastersmith_writer WITH LOGIN PASSWORD :'writer_password';
GRANT USAGE ON SCHEMA sales TO mastersmith_writer;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA sales TO mastersmith_writer;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA sales TO mastersmith_writer;
ALTER DEFAULT PRIVILEGES IN SCHEMA sales GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mastersmith_writer;
ALTER DEFAULT PRIVILEGES IN SCHEMA sales GRANT USAGE ON SEQUENCES TO mastersmith_writer;
SELECT format('REVOKE TEMPORARY ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SQL
