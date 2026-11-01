# k6 の試し走りの記録（Build and Test、Q3: A）

Intent 261029-reference-lookup-cleanup の Build and Test で、使い捨ての環境（`docker/perf/compose.yaml`）を3種類の対象DB で起動し、種・`k6 inspect`・参照の6場面の試し走り・種の 0 行の確かめ・MySQL の 1038 の確かめ・H4 を行った記録。手順は `perf/README.md` の「業務データの場面」（1B〜4B）・「他テーブル参照の読み取りの場面」・「他テーブル参照の保存前の確かめの場面」・「記録（evidence/）に置く前の伏せ字」に従った（違いは 8節）。時刻はすべて UTC。秘密の値（パスワード・トークン・`.env` の値）と行の値は記録しない。

この段の試し走りは **判定ではない**（1 VU・10 秒）。3種類の対象DB での合否（U1 の NFR2.1・NFR2.3・NFR2.5、U2 の NFR2.21・NFR2.25）は **Performance Validation** が持ち主で、この段では `Unverified` とする。

## 1. 版と前提

| 項目 | 値 |
|---|---|
| ソース | `develop` の `df79634`（Q2 の直しを含む） |
| WAR | `backend/build/libs/mastersmith.war`、SHA-256 の先頭 `36711d7a84770551`（`./gradlew :backend:bootWar` は UP-TO-DATE。Q2 で消した文言の鍵 `tables.reference.loading` が WAR の中に 0 件であることを確かめた） |
| 試験のイメージ | `mastersmith:reference-lookup-cleanup`（`sha256:eab1676f26de…`）。使い捨てのアプリのコンテナがこのイメージで動いたことを `docker inspect` で確かめた |
| 配備したアプリのイメージ | `mastersmith:local`（`sha256:e0ede6ddd5c4…`、2026-10-26T23:45:11Z 作成）。試験のイメージとは別のタグ（README の手順 0） |
| k6 | `perf/versions.env` の `K6_IMAGE`（`grafana/k6:2.3.0`） |
| 対象DB の版 | PostgreSQL 18.6・MySQL 26.10.0・MariaDB 13.0.2（`docker/perf/compose.yaml` の固定のダイジェスト） |
| colima の VM | CPU 4・メモリ 6GiB。止める前の `free -m`: total 5,911・used 1,046・free 3,500・available 4,865（MiB） |
| 対象DB の設定 | 試し走りのため、業務データの試験の値（`MASTERSMITH_PERF_TARGETDB_*`）は export せず既定のまま（README の「試し走りでは export しない。計画の D4: A」）。PG 512m・MySQL 768m（バッファープール 128M）・MariaDB 512m（同 128M） |
| 読み取りのプールの上限 | アプリの一時の環境ファイルに `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE=5` を書き、暖めの後に `hikaricp.connections.max`（`pool=mastersmith-target-db`）が **3種類とも 5.0** であることを確かめてから流した（中止の条件 R-03） |
| 眠りの守り | `caffeinate -dims` を作業の全体（01:39Z〜01:53Z）に掛け、k6 の台本もそれぞれ `caffeinate -dims` で包んだ。始める前の `pmset -g assertions` で `PreventUserIdleSystemSleep` と `PreventUserIdleDisplaySleep` が 1 |

## 2. 配備したアプリの停止と起動

| 時点 | 時刻（UTC） | 状態 |
|---|---|---|
| 止める前 | 01:38:20 | `mastersmith-app-1` は running・healthy、RestartCount 0、OOMKilled false、起動 01:19:34。Mailpit と見本の PG（`mastersmith-targetdb-postgres-1`）は動いたまま、lgtm は止まったまま（どちらも触れていない） |
| 停止（`docker compose stop app`） | 01:39:16 | exited（終了コード 143、SIGTERM での正常な停止）。コンテナは消さず作り直さない |
| 起動（`docker compose start app`） | 01:52:57 | — |
| healthy | 01:53:08 | コンテナの中から `/actuator/health` が `HTTP/1.1 200` |

- **止めていた時間**: 01:39:16〜01:53:08 の **約 13 分 52 秒**（見込みの約 1〜1.5 時間より短い）。
- **イメージ**: 起動し直した後のコンテナのイメージ ID は `sha256:e0ede6ddd5c4…` で、止める前と同じ（変わっていない）。RestartCount 0。
- **起動のログの WARN**（ロガーの名前ごとの件数。値は表示していない）: `org.flywaydb.core.internal.database.base.Database` 1・`org.springframework.context.support.PostProcessorRegistrationDelegate$BeanPostProcessorChecker` 1。ERROR 0。
- **救済の WARN**（U5 の NFR8.84・U5 R-03 の未計測を埋める）: `InitialAdmin`・`Rescue` を名前に含むロガーの WARN は **0 件**。`cherry.mastersmith.user.service.InitialAdminInitializer` の INFO が 1 件だけ（中身は表示していない）。

## 3. k6 inspect と閾値の台本（BT-K6-INSPECT）

| 確かめ | 結果 |
|---|---|
| `k6 inspect --include-system-env-vars`（参照の6場面） | 6場面とも終了コード 0。場面の名前はそれぞれ1つで `undefined` は無い |
| `bash perf/k6/thresholds-test.sh` | 終了コード 0（`ok` の行 13、それ以外の行 0） |
| `bash perf/dsl-mixed-memory-test.sh` | 終了コード 0（`ok` の行 5、それ以外の行 0） |

inspect で読んだ閾値（3種類で同じ。README の「今の基準の一覧」と一致）:

| 場面 | 判定の閾値 | 記録だけの閾値 |
|---|---|---|
| `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refCreateChecked` | `iteration_duration{scenario:<場面>}` `p(95)<1000`・`checks{scenario:<場面>}` `rate==1` | `http_req_duration{name:<場面>}` `max>=0` |
| `refPickerContains` | `iteration_duration{scenario:<場面>}` `p(95)<3000`・`checks{scenario:<場面>}` `rate==1` | `http_req_duration{name:<場面>}` `max>=0` |

## 4. 対象DB ごとの結果

### 4.1 種と種の後の確かめ（BT-K6-SEED・NFR2.4）

| 対象DB | 種の終了コード | `biz_item` | `biz_code200` | `biz_code201` | `biz_refcreate` | 目印の当たり | 最長の文字数 | 種の時間 |
|---|---|---|---|---|---|---|---|---|
| PostgreSQL | 0 | 100,000 | 200 | 201 | 0 | 909 | 2,000,000 | 約 6 秒（01:41:29〜01:41:35） |
| MySQL（`--default-character-set=utf8mb4`） | 0 | 100,000 | 200 | 201 | 0 | 909 | 2,000,000 | 約 10 秒（01:45:21〜01:45:31） |
| MariaDB（`--default-character-set=utf8mb4`） | 0 | 100,000 | 200 | 201 | 0 | 909 | 2,000,000 | 約 5 秒（01:50:24〜01:50:29） |

- 目印の当たりが3種類で同じ 909 で、MySQL・MariaDB でも二重の符号化は起きていない（履歴 H50 の誤りは出ていない）。
- PG の種の標準エラーは1行で、`NOTICE: schema "bizperf" does not exist, skipping`（初めての起動の `DROP SCHEMA IF EXISTS` の知らせ）。MySQL・MariaDB は 0 行。

表の大きさ（バイト。データと索引。判定ではなく記録。プールの 80% の決まりの確かめは Performance Validation で業務データの試験の値で起動して行う）:

| 表 | PostgreSQL（`pg_table_size`・`pg_indexes_size`） | MySQL（`DATA_LENGTH`・`INDEX_LENGTH`） | MariaDB（同） |
|---|---|---|---|
| `biz_item` | 205,570,048・2,260,992 | 742,391,808・0 | 472,907,776・0 |
| `biz_wide` | 4,489,216・16,384 | 275,267,584・0 | 274,219,008・0 |
| `biz_code200`・`biz_code201` | 各 40,960・16,384 | 各 16,384・0 | 各 16,384・0 |
| `biz_refsrc200`・`biz_refsrc201`・`biz_refpick` | 各 8,192・16,384 | 各 16,384・0 | 各 16,384・0 |
| `biz_refcreate` | 0・8,192 | 16,384・0 | 16,384・0 |

MySQL の `biz_item` は、前の Intent の記録（履歴 H50 の 1,017,806,848 B）と違う値が出た。`information_schema.TABLES` の値は推計で、測る時点（種の直後の `ANALYZE` の後）とバッファープールの大きさで変わる。Performance Validation の流す順の 3（表の大きさの実測と 80% の決まり）で、業務データの試験の値で起動して測り直す。

### 4.2 参照の6場面の試し走り（BT-K6-DRYRUN・BT-K6-REFCREATE。1 VU・10 秒、判定ではない）

どの対象DB でも、`bizPrepare`（1 VU・1 回）→ 暖め（`refPickerEquals`、1 VU・5 秒）→ 5つの読み取りの場面 → `refCreateChecked` の順で流した（README の流す順）。

| 対象DB | `bizPrepare` | 暖め | `refDefinition200` | `refDefinition201` | `refPickerEquals` | `refLabel` | `refPickerContains` | `refCreateChecked` |
|---|---|---|---|---|---|---|---|---|
| PostgreSQL | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| MySQL | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| MariaDB | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

（数は k6 の終了コード。流した時刻は PG 01:42:40〜01:43:52、MySQL 01:46:18〜01:47:31、MariaDB 01:51:07〜01:52:19）

閾値の名前の指標（NFR2.2）は、3種類の 18 個の summary json のすべてに `iteration_duration{scenario:<場面>}`・`checks{scenario:<場面>}`・`http_req_duration{name:<場面>}` の3つがそろって出た（欠けは 0）。`bizPrepare` は3種類とも `checks{scenario:bizPrepare}` の率 1（5 件すべて通過）。

参考の値（1 VU・10 秒。判定に使わない。単位はミリ秒、`iteration_duration` の p95 / 最大、`checks` はすべて率 1・失敗 0）:

| 場面 | PostgreSQL | MySQL | MariaDB |
|---|---|---|---|
| `refDefinition200` | 4 / 15（3,312 回） | 2 / 13（6,590 回） | 1 / 9（6,954 回） |
| `refDefinition201` | 2 / 11（4,621 回） | 1 / 7（11,157 回） | 1 / 27（10,646 回） |
| `refPickerEquals` | 25 / 27（434 回） | 0 / 36（14,636 回） | 0 / 9（16,165 回） |
| `refLabel` | 24 / 28（438 回） | 0 / 4（15,441 回） | 0 / 4（17,429 回） |
| `refPickerContains` | 135 / 139（76 回） | 1,814 / 1,819（6 回） | 551 / 763（19 回） |
| `refCreateChecked` | 3 / 41（3,815 回） | 3 / 37（4,471 回） | 1 / 39（6,761 回） |

（値は切り捨ての整数。0 は 1 ミリ秒未満）

- 2つのプールの待ちの時間切れ（`hikaricp.connections.timeout`）: 読み取りのプールは3種類のどの場面の前後も 0.0 → 0.0。書き込みのプールは `refCreateChecked` の前はまだ作られておらず値が無く（最初に借りるまで出ない）、後は 0.0。
- 1 VU の値のため、`refPickerContains` の MySQL の 1,814 ms は判定の線（3,000 ms）の6割にあたる参考の値にとどまる。10 VU・プールの上限 5 での値は Performance Validation で測る。

### 4.3 種の 0 行の確かめ（BT-K6-SEED0、U2 R-03 の直し）

| 対象DB | 種の後の 0 行の確かめ（行が無いとき） | 行があるときに台本の最後の確かめだけを流した結果 | 入れ直し |
|---|---|---|---|
| PostgreSQL | 種の終了コード 0（`biz_refcreate` 0 行） | 終了コード 3（psql の `ON_ERROR_STOP`）。標準エラーに目印 `biz_refcreate_must_be_empty` が 1 件、2桁以上の数は 0 件（行の数を出していない） | 種から入れ直して終了コード 0・`biz_refcreate` 0 行 |
| MySQL | 種の終了コード 0（`biz_refcreate` 0 行、最後の確かめの出力は 1） | 終了コード 1。標準エラーは `ERROR 1242`（Subquery returns more than 1 row）で 1 件、標準出力は 0 行 | 同上（終了コード 0・0 行・確かめの出力 1） |
| MariaDB | 同上 | 終了コード 1。`ERROR 1242` で 1 件、標準出力は 0 行 | 同上 |

- 行があるときの確かめは、`refCreateChecked` の試し走りの後（`biz_refcreate` に行が残った状態。行の数は表示せず「0 より多い」だけを確かめた）に、`make-biz-seed.sh <種類> | tail -n 1`（台本の最後の確かめの文だけ）を流して行った。
- MySQL・MariaDB の失敗は、台本の注記のとおり 1242 で止まる形で、誤りの文に目印は出ない（目印は成功のときの列の名前）。PG は誤りの文に目印が出る。どちらも行の値と数は出ない。
- PG は B7 で確かめ済みだったが、3種類で同じ手順で流し直した。

### 4.4 MySQL の 1038 の確かめ（BT-MYSQL-1038、U1 の NFR 設計 R-04）

**結果: 1038 は起きなかった**（並べ替えの3つの要求がどれも 200）。

| 並べたカラム | 照合 | 状態コード | `code` | 返った行 / 全体 |
|---|---|---|---|---|
| `id`（対照） | — | 200 | なし | 20 / 2,000 |
| `s_ai` | `utf8mb4_0900_ai_ci`（MySQL の既定、NO PAD） | 200 | なし | 20 / 2,000 |
| `s_bin` | `utf8mb4_bin`（種の DB の照合） | 200 | なし | 20 / 2,000 |

- **形**: 使い捨ての環境の MySQL 26.10.0（`sort_buffer_size` 262,144・`max_sort_length` 1,024・バッファープール 128M）に、確かめ専用の表 `bizperf.biz_sort1038`（主キー `id` と `TEXT` の2カラム、2,000 行。10 行に1行の 200 行が 10,000 字、ほかは短い値。値はすべて架空）を作り、DSL の写しに同じ形の表を足して（2つの文字のカラムは `sortable: true`）`bizPrepare` で適用し（`checks` の率 1）、`perf-biz-01` でログインして既存の業務データの一覧の API（`POST /api/tables/rows/search`、1ページ 20 件）に並べ替えを付けて1回ずつ送った。試しの形は `trial-results.md` 4.1 の「約 10,000 字の値が 10 行に1行（200 行）」と同じ件数と長さ。
- **並べ替えが効いたこと**: `s_ai` の並びの1ページ目は `id` の並びと違う順で、10,000 字の値の行（一覧では 100 字に切られる）を含んだ（行の値は記録していない。長さと順の違いだけを確かめた）。
- **ログ**: 要求を送った 01:49:14 の後に、使い捨てのアプリのログの行は 0 行（WARN・ERROR も 0。`sqlState`・`vendorCode` を出す行は無い）。
- **見立て（未検証）**: 試し（`trial-results.md` 4.1）で 1038 になったのは並びの全体を読む問い合わせの形で、既存の一覧は `LIMIT ? OFFSET ?` の付いた形（`RowSqlBuilder` の一覧の文）のため、MySQL が上位 N 件の並べ替え（優先度の待ち行列）で済ませ、並べ替えの領域が足りた見込み。深いページ（`OFFSET` が大きい）や1ページの件数を増やした形では起きうるかは確かめていない。
- **扱い**: 直すかは後の Intent の要件で決める（F2: A のまま）。Feedback & Optimization の振り返りに「今回の形では 1038 が再現しなかった」ことと上の見立てを載せる。

## 5. H4（BT-K6-H4、U4 の NFR8.69・AC4.1.6・BR4.3）

実際の summary json（PostgreSQL の `refCreateChecked`、`setup_data` を持ち、伏せる前の中に JWT の形が 1 件）を、道具の JSON の口で記録の置き場に置いた。

| 手順 | 結果 |
|---|---|
| `node perf/redact-k6-evidence.mjs build/perf-results/ref-postgres-refCreateChecked.json <この段の記録>/evidence/ref-postgres-refCreateChecked.json` | 終了コード 0。「置き換えた JWT の形: 0 件、`[REDACTED-SETUP]` にした値: 2 件」（JWT の形は `setup_data` の下にあったため、`[REDACTED-SETUP]` の側で伏せられた） |
| `node perf/redact-k6-evidence.mjs --scan <この段の記録>/evidence` | 終了コード 0（調べたファイル 1・検出 0・入力の誤り 0） |
| `gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 <この段の記録>/evidence` | 終了コード 0（no leaks found、約 4,154 バイト） |
| `eyJ` の検索（`evidence/` の下） | 0 件 |

置いたファイル: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/build-and-test/evidence/ref-postgres-refCreateChecked.json`（4,154 バイト）。ほかの summary json とログは git の対象外の `build/perf-results/` に残り、記録には置いていない。

## 6. 使い捨てのアプリのログ（片付ける前に数えた。ロガーの名前ごとの件数だけ）

| 対象DB | 行 | JSON でない行 | WARN（ロガー・件数） | ERROR | 試験用の利用者の名前 | 行の値の目印 | `eyJ` |
|---|---|---|---|---|---|---|---|
| PostgreSQL | 129 | 0 | `common.error.web.GlobalExceptionHandler` 25（code はすべて `ROLE_NO_CHANGE`）・`flywaydb…Database` 2・`BeanPostProcessorChecker` 2 | 0 | 0 | 0 | 0 |
| MySQL | 142 | 0 | `GlobalExceptionHandler` 36（すべて `ROLE_NO_CHANGE`）・`flywaydb…Database` 2・`BeanPostProcessorChecker` 2 | 0 | 0 | 0 | 0 |
| MariaDB | 129 | 0 | `GlobalExceptionHandler` 25（すべて `ROLE_NO_CHANGE`）・`flywaydb…Database` 2・`BeanPostProcessorChecker` 2 | 0 | 0 | 0 | 0 |

- `ROLE_NO_CHANGE`（409）は、場面ごとの setup が前の場面の残したロールと割り当てを受け入れる形（README の setup の前提）で出る想定内の業務の誤り。MySQL が 11 件多いのは、1038 の確かめのための2回目の `bizPrepare` の分。
- Flyway と `BeanPostProcessorChecker` の WARN は起動のたびに1件ずつ（使い捨てのアプリは種類ごとに2回起動した）。
- コンテナのメモリ（片付ける前、`OOMKilled` と `memory.events`）:

| コンテナ | OOMKilled | `oom_kill` | `max` の回数 | `memory.peak`（バイト） |
|---|---|---|---|---|
| PG の回の対象DB・アプリ | false・false | — | — | —（PG の回は OOMKilled だけを確かめた） |
| MySQL（上限 768m） | false | 0 | 12,009 | 805,306,368（上限に届いた） |
| MySQL の回のアプリ（上限 2g） | false | 0 | 0 | 728,817,664 |
| MariaDB（上限 512m） | false | 0 | 0 | 320,319,488 |
| MariaDB の回のアプリ（上限 2g） | false | 0 | 0 | 789,618,688 |

MySQL は試し走りの既定の上限（768m）に張りついた（`max` の回数 12,009、`oom_kill` 0）。業務データの試験の値（2688m・バッファープール 1280M）では起動していないため、判定の材料にはしない。Performance Validation は README の業務データの試験の値で起動する。

片付け: 3種類とも、数え終わった後に `docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile targetdb-<種類> down -v` で消した（使い捨てのボリューム・網の残りは 0）。一時の環境ファイルのディレクトリ（ホームの下、権限 700）も消した。

## 7. 眠りの確かめ

`pmset -g log` の 2026-11-01 10:38〜10:54（JST。UTC 01:38〜01:54）の窓に `Sleep`・`DarkWake` は 0 件。最後の `Wake` は 09:00:05 JST（作業の前）。

## 8. 手順書との違い

| # | 手順書（`perf/README.md`） | 実際 | 理由・影響 |
|---|---|---|---|
| D1 | 手順 0: 配備したアプリを止めてから WAR とイメージを用意する | WAR（UP-TO-DATE）とイメージ `mastersmith:reference-lookup-cleanup` を止める前に作った | 止める時間を短くするため。止めるのは測定の CPU の取り合いを避けるためで、作るのを先にしても試験の値に影響しない |
| D2 | 2B・2B'・確かめで `perfu exec -T "targetdb-$KIND" "${client[@]}"` | `docker exec -i mastersmith-perf-targetdb-<種類>-1 <同じ道具と引数>` | この作業の環境の決まりで、関数の `exec` の形を流せなかったため。同じコンテナに同じ道具・同じ引数（MySQL・MariaDB は `--default-character-set=utf8mb4`）で流しており、結果は同じ |
| D3 | 4B の `biz-run.sh` の `bizPrepare` と、`ref-run.sh`・`refw-run.sh` を別々に流す | 1つの台本（リポジトリの外の一時の置き場）で、`bizPrepare` → `ref-run.sh` の中身 → `refw-run.sh` の中身を続けて流し、場面ごとに k6 の終了コードを記録し、k6 の標準出力を `build/perf-results/<名前>.log` に受けた | 場面ごとの終了コードを記録するため。場面・順・引数（試し走りの `-e VUS=1 -e BIZ_DURATION=10s`）・中止の条件は README と同じ |
| D4 | `caffeinate -dims` で台本の全体を包む | 台本ごとの `caffeinate -dims` に加え、作業の全体（01:39〜01:53）にも `caffeinate -dims` を掛けた | 守りを切らさないため（README の趣旨と同じ） |
| D5 | MySQL の 1038 の確かめは README に手順が無い（`logical-components.md` 6節・`trial-results.md` 4節の形） | 4.4 のとおり、確かめ専用の表と DSL の写し（`build/perf-results/biz-data/dsl-1038.yaml`、git の対象外）を使い捨ての環境にだけ置き、ホストから `curl` で3つの要求を送った（Origin は使い捨てのアプリの `http://127.0.0.1:18080`） | 既存の種に「約 10,000 字の値を数十行以上持つ並べ替えのできる文字のカラム」が無いため。照合を2つ（既定と種の DB の照合）並べ、`id` の並びを対照に置いた |
| D6 | 0 行の確かめの失敗側の手順は README に無い | 行が残った後に、種の台本の最後の1文だけを流した（4.3） | 台本は毎回スキーマを作り直すため、台本の全体を流すと必ず通る。最後の確かめの文だけを流すことで失敗側を確かめた |
| D7 | 目録（`target-inventory-draft.md` BT-K6-INSPECT）は 78 場面の `k6 inspect` | この段の `k6 inspect` は参照の6場面。全体は `perf/k6/thresholds-test.sh`（期待の表との照合、`ok` 13 行）で確かめた | 依頼の範囲（参照の場面）に合わせた。78 場面の inspect を1件ずつ流すかは、承認の場で確かめる |

## 9. 比べる相手（Q4: A）と持ち越し

- 比べる相手は、Intent 261016-business-data-views の Performance Validation（読み取りのプールの上限 5）の結果とする。この段の値は 1 VU・10 秒の試し走りで、並べて比べない。上限 10 の回は流さない（README 履歴 H61）。
- Performance Validation に引き継ぐこと: 3種類の判定の回（10 VU・3 分、業務データの試験の値で起動、合否の回は 2 回）、表の大きさの実測と 80% の決まり（4.1 の MySQL の値の違いを含む）、`bizSearchWide`・`bizCreateLarge` の `memory.peak`（90% 未満、Q5: B）、MySQL の 1038 が今回の形で再現しなかったことの振り返りへの記載（Feedback & Optimization）。
