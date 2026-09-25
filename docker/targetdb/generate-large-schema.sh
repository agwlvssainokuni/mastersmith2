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

# Build and Test で読み取りの時間を測るための、テーブル N 個 × カラム M 個（既定 100 × 100）のスキーマ large を作る SQL を
# 標準出力に出す（U1 の NFR1.3）。表どうしの外部キー（1つ前の表を参照）とコメントも入れる。
# 読み取りだけのアカウント mastersmith_reader（見本の対象DB が作る）に、読み取りの権限を与える。
#
# 使い方（プロジェクトのルートで、見本の対象DB を profile で起動しておく）:
#   ./docker/targetdb/generate-large-schema.sh postgres \
#     | docker compose exec -T targetdb-postgres psql -v ON_ERROR_STOP=1 -U target_admin -d business
#   ./docker/targetdb/generate-large-schema.sh mysql \
#     | docker compose exec -T targetdb-mysql sh -c 'MYSQL_PWD="$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD" mysql --user=root'
#   ./docker/targetdb/generate-large-schema.sh mariadb \
#     | docker compose exec -T targetdb-mariadb sh -c 'MYSQL_PWD="$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD" mariadb --user=root'
# （管理者のパスワードは .env.targetdb からコンテナに渡る。MYSQL_ROOT_PASSWORD・MARIADB_ROOT_PASSWORD は起動の入口の中でだけ
#   写すため、docker compose exec の環境には無い）
# 表とカラムの数を変えるとき: ./docker/targetdb/generate-large-schema.sh postgres 50 20
# アプリで読むときは MASTERSMITH_TARGET_DB_SCHEMA=large にする（MySQL・MariaDB は MASTERSMITH_TARGET_DB_DATABASE も large）。
set -euo pipefail

kind=${1:-}
tables=${2:-100}
columns=${3:-100}

case "${kind}" in
  postgres | mysql | mariadb) ;;
  *)
    echo "使い方: $0 <postgres|mysql|mariadb> [表の数（既定 100）] [カラムの数（既定 100、3 以上）]" >&2
    exit 1
    ;;
esac
if ! [[ "${tables}" =~ ^[0-9]+$ && "${columns}" =~ ^[0-9]+$ ]] || ((tables < 1 || columns < 3)); then
  echo "表の数は 1 以上、カラムの数は 3 以上の数にしてください。" >&2
  exit 1
fi

if [ "${kind}" = postgres ]; then
  types=("VARCHAR(40)" "INTEGER" "NUMERIC(10,2)" "DATE" "TEXT")
  echo "CREATE SCHEMA large;"
  prefix="large."
else
  types=("VARCHAR(40)" "INT" "DECIMAL(10,2)" "DATE" "TEXT")
  echo "CREATE DATABASE large CHARACTER SET utf8mb4;"
  echo "USE large;"
  prefix=""
fi

for ((t = 1; t <= tables; t++)); do
  table=$(printf "t_%03d" "${t}")
  echo "CREATE TABLE ${prefix}${table} ("
  echo "  id INTEGER NOT NULL PRIMARY KEY,"
  echo "  parent_id INTEGER NULL,"
  for ((c = 3; c <= columns; c++)); do
    type=${types[$(((c - 3) % ${#types[@]}))]}
    column=$(printf "c_%03d" "${c}")
    if [ "${kind}" = postgres ]; then
      echo "  ${column} ${type} NULL,"
    else
      echo "  ${column} ${type} NULL COMMENT 'カラム ${c}',"
    fi
  done
  if ((t > 1)); then
    parent=$(printf "t_%03d" "$((t - 1))")
    echo "  CONSTRAINT fk_${table} FOREIGN KEY (parent_id) REFERENCES ${prefix}${parent} (id)"
  else
    echo "  CONSTRAINT uq_${table}_parent UNIQUE (parent_id)"
  fi
  if [ "${kind}" = postgres ]; then
    echo ");"
    echo "COMMENT ON TABLE ${prefix}${table} IS '表 ${t}';"
    for ((c = 3; c <= columns; c++)); do
      echo "COMMENT ON COLUMN ${prefix}${table}.$(printf "c_%03d" "${c}") IS 'カラム ${c}';"
    done
  else
    echo ") COMMENT '表 ${t}';"
  fi
done

if [ "${kind}" = postgres ]; then
  echo "GRANT USAGE ON SCHEMA large TO mastersmith_reader;"
  echo "GRANT SELECT ON ALL TABLES IN SCHEMA large TO mastersmith_reader;"
else
  echo "GRANT SELECT, SHOW VIEW ON large.* TO 'mastersmith_reader'@'%';"
fi
