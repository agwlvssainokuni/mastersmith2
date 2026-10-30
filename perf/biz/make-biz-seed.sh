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

# 業務データの負荷の試験（Intent 261016-business-data-views の U1、Bolt B2）の種を入れる SQL を標準出力に出す。
# 使い捨ての対象DB（docker/perf/compose.yaml の targetdb-*）に、スキーマ bizperf（MySQL・MariaDB ではデータベース）と次の3つの表を作る。
#   biz_item  100 カラム（主キー id の1つと c_001〜c_099）に、決めた種から ROWS 行（既定 100,000 行）を入れる悪い側の表（NFR2.1）
#   biz_pair  複合の主キー（region_code・item_code）の表。1,000 行
#   biz_wide  主キー id と、100 字を超える文字の w_001〜w_099 の表。200 行（bizSearchWide の 100 件 × 100 カラム × 100 字。NFR2.5）
# 書き込みの場面（Intent 261016-business-data-views の U2、Bolt B4）の2つの表も作る。
#   biz_ref   外部キーの参照先（主キー id の 1〜100 の 100 行）
#   biz_new   100 カラム（自動の採番の主キー id・一意の制約のある u_code・biz_ref を指す外部キー ref_id・文字の n_001〜n_097）。
#             行は入れない（bizCreate・bizCreateLarge が作り、bizUpdate・bizDelete は setup で自分の行を作る）
# 他テーブル参照の読み取りの場面（Intent 261029-reference-lookup-cleanup の U1、Bolt B2。NFR2.4）の表も作る。
#   biz_code200・biz_code201  REFERENCE の参照先のコードの表（主キー id・短い文字のコード code・短い文字の名前 name）。
#             ちょうど 200 行と 201 行（refDefinition200 は選択肢 200 件、refDefinition201 は TOO_MANY）
#   biz_refsrc200・biz_refsrc201  それぞれを REFERENCE で指すカラム code を持つ参照元（10 行。定義の取得は行を読まない）
#   biz_refpick  biz_item を LOOKUP で指すカラム item_id を持つ参照元（10 行。ピッカーの検索とラベルの1件読みの場面）
#   後の単位（U2）が足す表も、この後・最後の GRANT の前に足す（GRANT ON ALL TABLES に入るため）。
# 読み取りだけのアカウント mastersmith_reader（対象DB の初期化の台本が作る）に、bizperf の読み取りの権限を与える。書き込みの
# アカウント mastersmith_writer（同じく 03-writer-account.sh が作る）に、bizperf の行の操作（PG は sequence の USAGE も）を与える。
# 値はすべて架空で、行の番号 n とカラムの番号 i だけから決まる（乱数を使わない。何度流しても同じ行になる）。
#   c_010・c_030・c_050・c_070・c_090 は文字の長い型（TEXT。MySQL・MariaDB の c_010 は LONGTEXT）で、150〜270 字に行の番号を付けた値。
#     n が 100 の倍数の行は、値の中に目印の語「目印」を含む（部分一致の場面 bizSearchContains の当たり）。
#     c_010 の n % 10000 = 5 の 10 行だけは 2,000,000 字（大きな値。一覧と詳細で切る長さより長い）。
#   ほかの c_<i> は i % 7 で型を回す: 0 文字（VARCHAR(40)）・1 整数・2 長い整数・3 小数・4 日付・5 日時・6 真偽値。
#   c_001（整数。値は 0〜99,999 を1回ずつ取り、主キーの順とは違う並び）は並べ替えの場面 bizSearchLastSorted のカラムで、
#   索引を作らない（主キーの索引だけ。計画の D2: A）。
#   (n + i) % 11 = 0 の値は NULL（大きな値の 10 行を除く）。
# この形は perf/make-biz-dsl.mjs の DSL と同じでなければならない（片方を変えるときは両方を変える）。
#
# 使い方（プロジェクトのルートで。使い捨ての対象DB を profile で起動しておく。手順は perf/README.md の「業務データの場面」）:
#   ./perf/biz/make-biz-seed.sh postgres \
#     | docker compose -p mastersmith-perf -f docker/perf/compose.yaml exec -T targetdb-postgres \
#         psql -q -v ON_ERROR_STOP=1 -U target_admin -d business
#   ./perf/biz/make-biz-seed.sh mysql \
#     | docker compose -p mastersmith-perf -f docker/perf/compose.yaml exec -T targetdb-mysql \
#         sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root'
#   ./perf/biz/make-biz-seed.sh mariadb \
#     | docker compose -p mastersmith-perf -f docker/perf/compose.yaml exec -T targetdb-mariadb \
#         sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --user=root'
# 行の数を変えるとき（試し走りなど）: ./perf/biz/make-biz-seed.sh postgres 1000
# アプリで読むときは MASTERSMITH_TARGET_DB_SCHEMA=bizperf にする（MySQL・MariaDB は MASTERSMITH_TARGET_DB_DATABASE も bizperf）。
set -euo pipefail

kind=${1:-}
rows=${2:-100000}

case "${kind}" in
  postgres | mysql | mariadb) ;;
  *)
    echo "使い方: $0 <postgres|mysql|mariadb> [biz_item の行の数（既定 100000、1〜1000000）]" >&2
    exit 1
    ;;
esac
if ! [[ "${rows}" =~ ^[0-9]+$ ]] || ((rows < 1 || rows > 1000000)); then
  echo "行の数は 1〜1000000 の数にしてください。" >&2
  exit 1
fi

# 文字の長い値の詰め物（架空の文字列）と目印の語
FILL='いろはにほへとちりぬるをわかよたれそつねならむうゐのおくやまけふこえて'
MARK='目印'
BIG_CHARS=2000000

pg=false
[ "${kind}" = postgres ] && pg=true

# 型の名前（DB の種類ごと）
if ${pg}; then
  T_VARCHAR="VARCHAR(40)" T_INT="INTEGER" T_BIGINT="BIGINT" T_DECIMAL="NUMERIC(12,2)" T_DATE="DATE"
  T_DATETIME="TIMESTAMP" T_BOOL="BOOLEAN" T_TEXT="TEXT" T_LONGTEXT="TEXT"
else
  T_VARCHAR="VARCHAR(40)" T_INT="INT" T_BIGINT="BIGINT" T_DECIMAL="DECIMAL(12,2)" T_DATE="DATE"
  T_DATETIME="DATETIME" T_BOOL="TINYINT(1)" T_TEXT="TEXT" T_LONGTEXT="LONGTEXT"
fi

# c_<i> の型（make-biz-dsl.mjs の kindOf と同じ）
kind_of() {
  local i=$1
  case "${i}" in
    10 | 30 | 50 | 70 | 90) echo text ;;
    *)
      local k=(varchar int bigint decimal date datetime bool)
      echo "${k[$((i % 7))]}"
      ;;
  esac
}

type_of() {
  case "$(kind_of "$1")" in
    text) if [ "$1" = 10 ]; then echo "${T_LONGTEXT}"; else echo "${T_TEXT}"; fi ;;
    varchar) echo "${T_VARCHAR}" ;;
    int) echo "${T_INT}" ;;
    bigint) echo "${T_BIGINT}" ;;
    decimal) echo "${T_DECIMAL}" ;;
    date) echo "${T_DATE}" ;;
    datetime) echo "${T_DATETIME}" ;;
    bool) echo "${T_BOOL}" ;;
  esac
}

# 文字の連結（PG は ||、MySQL・MariaDB は CONCAT）
concat() {
  if ${pg}; then
    local out=""
    for part in "$@"; do out="${out:+${out} || }${part}"; done
    echo "(${out})"
  else
    local out=""
    for part in "$@"; do out="${out:+${out}, }${part}"; done
    echo "CONCAT(${out})"
  fi
}

# 数を文字にする
str() {
  if ${pg}; then echo "CAST($1 AS TEXT)"; else echo "CAST($1 AS CHAR)"; fi
}

# 行の番号 n とカラムの番号 i から決まる値の式（NULL を含まない）
value_of() {
  local i=$1
  case "$(kind_of "${i}")" in
    varchar) concat "'値${i}-'" "$(str "MOD(n, 997)")" ;;
    int) echo "MOD(n * 7919 + ${i}, 100000)" ;;
    bigint)
      if ${pg}; then echo "(CAST(n AS BIGINT) * 1000003 + ${i})"; else echo "(CAST(n AS SIGNED) * 1000003 + ${i})"; fi
      ;;
    decimal) echo "(MOD(n * 31 + ${i}, 1000000) / 100.0)" ;;
    date)
      if ${pg}; then echo "(DATE '2020-01-01' + MOD(n + ${i}, 3650))"; else echo "DATE_ADD(DATE '2020-01-01', INTERVAL MOD(n + ${i}, 3650) DAY)"; fi
      ;;
    datetime)
      if ${pg}; then
        echo "(TIMESTAMP '2020-01-01 00:00:00' + MOD(n * 37 + ${i}, 315360000) * INTERVAL '1 second')"
      else
        echo "DATE_ADD(TIMESTAMP '2020-01-01 00:00:00', INTERVAL MOD(n * 37 + ${i}, 315360000) SECOND)"
      fi
      ;;
    bool)
      if ${pg}; then echo "(MOD(n + ${i}, 2) = 0)"; else echo "(CASE WHEN MOD(n + ${i}, 2) = 0 THEN 1 ELSE 0 END)"; fi
      ;;
    text)
      local mark
      mark="CASE WHEN MOD(n, 100) = 0 THEN '${MARK} ' ELSE '' END"
      concat "'行'" "$(str n)" "'-${i} '" "${mark}" "RPAD('', 150 + MOD(n * 7 + ${i}, 121), '${FILL}')"
      ;;
  esac
}

# NULL と大きな値を含めた c_<i> の式
cell_of() {
  local i=$1
  local big=""
  if [ "${i}" = 10 ]; then
    big="WHEN MOD(n, 10000) = 5 THEN RPAD('', ${BIG_CHARS}, '${FILL}') "
  fi
  echo "CASE ${big}WHEN MOD(n + ${i}, 11) = 0 THEN NULL ELSE $(value_of "${i}") END"
}

# 1〜rows の番号の表（PG は generate_series、MySQL・MariaDB は数字の表の掛け合わせ）
numbers() {
  local limit=$1
  if ${pg}; then
    echo "SELECT g AS n FROM generate_series(1, ${limit}) AS g"
  else
    local digits=1 span=10
    while ((span < limit)); do
      digits=$((digits + 1))
      span=$((span * 10))
    done
    local from="" expr="1"
    local d
    for ((d = 0; d < digits; d++)); do
      from="${from:+${from} CROSS JOIN }bizperf.seq_digit d${d}"
      expr="${expr} + d${d}.d * $((10 ** d))"
    done
    echo "SELECT n FROM (SELECT ${expr} AS n FROM ${from}) AS seq WHERE n <= ${limit}"
  fi
}

if ${pg}; then
  echo "\\set ON_ERROR_STOP on"
  echo "DROP SCHEMA IF EXISTS bizperf CASCADE;"
  echo "CREATE SCHEMA bizperf;"
  P="bizperf."
else
  echo "DROP DATABASE IF EXISTS bizperf;"
  echo "CREATE DATABASE bizperf CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"
  echo "USE bizperf;"
  echo "CREATE TABLE bizperf.seq_digit (d INT NOT NULL PRIMARY KEY);"
  echo "INSERT INTO bizperf.seq_digit (d) VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);"
  P="bizperf."
fi

# biz_item（100 カラム、ROWS 行）
echo "CREATE TABLE ${P}biz_item ("
echo "  id BIGINT NOT NULL PRIMARY KEY,"
for ((i = 1; i <= 99; i++)); do
  sep=","
  ((i == 99)) && sep=""
  printf '  c_%03d %s NULL%s\n' "${i}" "$(type_of "${i}")" "${sep}"
done
echo ");"
cols="id"
exprs="n"
for ((i = 1; i <= 99; i++)); do
  cols="${cols}, $(printf 'c_%03d' "${i}")"
  exprs="${exprs}, $(cell_of "${i}")"
done
echo "INSERT INTO ${P}biz_item (${cols}) SELECT ${exprs} FROM ($(numbers "${rows}")) AS src;"

# biz_pair（複合の主キー、1,000 行）
echo "CREATE TABLE ${P}biz_pair ("
echo "  region_code INTEGER NOT NULL,"
echo "  item_code VARCHAR(20) NOT NULL,"
echo "  title VARCHAR(40) NULL,"
echo "  amount ${T_DECIMAL} NULL,"
echo "  updated_on DATE NULL,"
echo "  PRIMARY KEY (region_code, item_code)"
echo ");"
echo "INSERT INTO ${P}biz_pair (region_code, item_code, title, amount, updated_on) SELECT" \
  "MOD(n - 1, 10) + 1, $(concat "'品-'" "$(str "n")"), $(concat "'組の行 '" "$(str n)")," \
  "CASE WHEN MOD(n, 13) = 0 THEN NULL ELSE (MOD(n * 17, 100000) / 100.0) END," \
  "$(${pg} && echo "(DATE '2020-01-01' + MOD(n, 3650))" || echo "DATE_ADD(DATE '2020-01-01', INTERVAL MOD(n, 3650) DAY)")" \
  "FROM ($(numbers 1000)) AS src;"

# biz_wide（100 字を超える文字の 99 カラム、200 行）
echo "CREATE TABLE ${P}biz_wide ("
echo "  id INTEGER NOT NULL PRIMARY KEY,"
for ((i = 1; i <= 99; i++)); do
  sep=","
  ((i == 99)) && sep=""
  printf '  w_%03d %s NULL%s\n' "${i}" "${T_TEXT}" "${sep}"
done
echo ");"
cols="id"
exprs="n"
for ((i = 1; i <= 99; i++)); do
  cols="${cols}, $(printf 'w_%03d' "${i}")"
  exprs="${exprs}, $(concat "'幅'" "$(str n)" "'-${i} '" "RPAD('', 120, '${FILL}')")"
done
echo "INSERT INTO ${P}biz_wide (${cols}) SELECT ${exprs} FROM ($(numbers 200)) AS src;"

# biz_ref（外部キーの参照先、100 行）と biz_new（書き込みの場面の 100 カラムの表。行は入れない）。U2。
echo "CREATE TABLE ${P}biz_ref (id INTEGER NOT NULL PRIMARY KEY, name VARCHAR(40) NULL);"
echo "INSERT INTO ${P}biz_ref (id, name) SELECT n, $(concat "'参照 '" "$(str n)") FROM ($(numbers 100)) AS src;"
echo "CREATE TABLE ${P}biz_new ("
if ${pg}; then
  echo "  id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
else
  echo "  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
fi
echo "  u_code VARCHAR(40) NULL UNIQUE,"
echo "  ref_id INTEGER NULL,"
for ((i = 1; i <= 97; i++)); do
  printf '  n_%03d %s NULL,\n' "${i}" "${T_TEXT}"
done
echo "  CONSTRAINT fk_biz_new_ref FOREIGN KEY (ref_id) REFERENCES ${P}biz_ref (id)"
echo ");"

# 他テーブル参照の読み取りの場面の表（U1。値は行の番号だけから決まる架空の値）
for count in 200 201; do
  echo "CREATE TABLE ${P}biz_code${count} (id INTEGER NOT NULL PRIMARY KEY, code VARCHAR(40) NULL, name VARCHAR(40) NULL);"
  echo "INSERT INTO ${P}biz_code${count} (id, code, name) SELECT n, $(concat "'K'" "$(str n)"), $(concat "'区分 '" "$(str n)")" \
    "FROM ($(numbers "${count}")) AS src;"
  echo "CREATE TABLE ${P}biz_refsrc${count} (id INTEGER NOT NULL PRIMARY KEY, code VARCHAR(40) NULL);"
  echo "INSERT INTO ${P}biz_refsrc${count} (id, code) SELECT n, $(concat "'K'" "$(str n)") FROM ($(numbers 10)) AS src;"
done
echo "CREATE TABLE ${P}biz_refpick (id INTEGER NOT NULL PRIMARY KEY, item_id BIGINT NULL);"
echo "INSERT INTO ${P}biz_refpick (id, item_id) SELECT n, n FROM ($(numbers 10)) AS src;"

# 行の数（表ごと）を出す問い合わせ（NFR2.1・U1 の NFR2.4 の記録）
COUNTS="SELECT 'biz_item' AS t, COUNT(*) FROM bizperf.biz_item UNION ALL SELECT 'biz_pair', COUNT(*) FROM bizperf.biz_pair \
UNION ALL SELECT 'biz_wide', COUNT(*) FROM bizperf.biz_wide UNION ALL SELECT 'biz_ref', COUNT(*) FROM bizperf.biz_ref \
UNION ALL SELECT 'biz_code200', COUNT(*) FROM bizperf.biz_code200 UNION ALL SELECT 'biz_code201', COUNT(*) FROM bizperf.biz_code201;"

if ${pg}; then
  echo "ANALYZE bizperf.biz_item;"
  echo "ANALYZE bizperf.biz_pair;"
  echo "ANALYZE bizperf.biz_wide;"
  echo "GRANT USAGE ON SCHEMA bizperf TO mastersmith_reader;"
  echo "GRANT SELECT ON ALL TABLES IN SCHEMA bizperf TO mastersmith_reader;"
  echo "GRANT USAGE ON SCHEMA bizperf TO mastersmith_writer;"
  echo "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA bizperf TO mastersmith_writer;"
  echo "GRANT USAGE ON ALL SEQUENCES IN SCHEMA bizperf TO mastersmith_writer;"
  # 行の数（表ごと）を出す（NFR2.1 の記録）
  echo "${COUNTS}"
else
  echo "DROP TABLE bizperf.seq_digit;"
  echo "ANALYZE TABLE bizperf.biz_item, bizperf.biz_pair, bizperf.biz_wide;"
  echo "GRANT SELECT, SHOW VIEW ON bizperf.* TO 'mastersmith_reader'@'%';"
  echo "GRANT SELECT, INSERT, UPDATE, DELETE ON bizperf.* TO 'mastersmith_writer'@'%';"
  echo "${COUNTS}"
fi
