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

# 手元で試す対象DB（MariaDB）に、業務データの書き込みのアカウント mastersmith_writer を作り、権限を当てる（Intent
# 261016-business-data-views の U2）。初めて起動したときに動き、既にあるボリュームには docker compose exec でこの台本を流す（README）。
# 何度流しても同じ結果になる（無ければ作り、毎回パスワードと付与を当て直す）。
#
# 付与するスキーマ: business（見本の DB。ほかの DB を DSL に足すときは、同じ形の付与を足す）。
# 与える権限: SELECT・INSERT・UPDATE・DELETE だけ。DDL（CREATE・DROP・ALTER・INDEX）・CREATE TEMPORARY TABLES・LOCK TABLES・
# SHOW VIEW は与えない。
#
# パスワードは環境変数 MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD（見本の対象DB では .env.targetdb から、負荷の試験の環境では
# 一時の環境ファイルから compose が渡す）から取り、ここには書かない。管理者のパスワードは環境変数 MYSQL_PWD でクライアントに渡し、
# コマンド行に出さない（docker compose exec では MARIADB_ROOT_PASSWORD が無いため、.env.targetdb の管理者のパスワードを使う）。
set -euo pipefail

if [ -z "${MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD:-}" ]; then
  echo "MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD が空です。.env.targetdb（負荷の試験の環境では一時の環境ファイル）に値を入れてから作り直してください。" >&2
  exit 1
fi

# SQL の文字列の中に入れるため、\ と ' を二重にする。
writer_password=${MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD//\\/\\\\}
writer_password=${writer_password//\'/\'\'}

MYSQL_PWD="${MARIADB_ROOT_PASSWORD:-${MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD:-}}" mariadb --protocol=socket --user=root <<SQL
CREATE USER IF NOT EXISTS 'mastersmith_writer'@'%' IDENTIFIED BY '${writer_password}';
ALTER USER 'mastersmith_writer'@'%' IDENTIFIED BY '${writer_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON business.* TO 'mastersmith_writer'@'%';
SQL
