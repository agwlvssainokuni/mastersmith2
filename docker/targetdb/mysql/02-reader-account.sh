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

# 手元で試す対象DB（MySQL）に、読み取りの権限だけのアカウント mastersmith_reader を作る（初めて起動したときだけ動く）。
# パスワードは環境変数 MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD（見本の対象DB では .env.targetdb から、負荷の試験の環境では
# 一時の環境ファイルから compose が渡す）から取り、ここには書かない。
# 管理者のパスワードは環境変数 MYSQL_PWD でクライアントに渡し、コマンド行に出さない。
set -euo pipefail

if [ -z "${MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD:-}" ]; then
  echo "MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD が空です。.env.targetdb（負荷の試験の環境では一時の環境ファイル）に値を入れてから作り直してください。" >&2
  exit 1
fi

# SQL の文字列の中に入れるため、\ と ' を二重にする。
reader_password=${MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD//\\/\\\\}
reader_password=${reader_password//\'/\'\'}

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket --user=root <<SQL
CREATE USER 'mastersmith_reader'@'%' IDENTIFIED BY '${reader_password}';
GRANT SELECT, SHOW VIEW ON business.* TO 'mastersmith_reader'@'%';
SQL
