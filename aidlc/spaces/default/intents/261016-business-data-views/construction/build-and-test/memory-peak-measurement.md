# dslMixed の memory.peak の内訳の測り（U5 から引き継いだもの）

Build and Test の Q2: A（依頼者の決定）で、U5（u5-carryover-tests）の BR3.4・BR3.5・BR4.1〜BR4.3 と FR16 の測りを、`perf/README.md` の「`dslMixed` の `memory.peak` と `memory.stat` の記録」の手順と `perf/dsl-mixed-memory.sh` で行った。既定の設定で2回、`RETENTION_TIME=0` の試しで2回、それぞれ別の使い捨ての環境で流し、回ごとにアプリを作り直した（前の最大の値を消すため）。

時刻はすべて UTC（2026-10-20）。値は数値・時刻・状態だけを書く。

## 1. 4回の結果

`memory.max` はどの回も 2147483648（2048.0 MiB）。MiB は 1048576 バイトで割った値。「peak の時」は記録の TSV で `memory_current` が最大の行。

| 回 | memory.peak（MiB） | peak / max | anon の最大 / max | peak の時の anon（MiB） | peak の時の file（MiB） | peak の時の inactive_file（MiB） | peak の時の kernel（MiB） | file の伸び（MiB） | mv.db の伸び（MiB） | 判定（< 0.9） |
|---|---|---|---|---|---|---|---|---|---|---|
| default-1 | 1788.1 | 0.8731 | 0.6991 | 1431.7 | 335.0 | 330.1 | 16.0 | 335.0 | 363.9 | 0.9 未満 |
| default-2 | 1780.9 | 0.8696 | 0.6902 | 1413.5 | 343.4 | 340.0 | 16.0 | 344.0 | 354.1 | 0.9 未満 |
| retention-1 | 1515.5 | 0.7400 | 0.7030 | 1432.0 | 72.0 | 72.0 | 7.8 | 71.9 | 71.9 | 0.9 未満 |
| retention-2 | 1469.4 | 0.7175 | 0.6865 | 1403.5 | 54.3 | 54.3 | 7.6 | 74.8 | 74.8 | 0.9 未満 |

- peak の時の時刻: default-1 14:59:53・default-2 15:03:00・retention-1 15:10:43・retention-2 15:16:26（どれも `dslMixed` の間）。
- 内部DB のファイル（`mv_db_bytes`）の始めと最大: default-1 1.8 → 365.7 MiB・default-2 2.1 → 356.3 MiB・retention-1 1.8 → 73.6 MiB・retention-2 2.1 → 76.9 MiB。

### 各回の記録の確かめ

| 回 | 記録のループの行（完全な行） | dropped_rows | NA の行 | post_missing_seconds | k6 の終了コード | checks（通過・失敗） | http_req_failed | 繰り返し | ログインの利用者の行・重なり | 状態（止める前） |
|---|---|---|---|---|---|---|---|---|---|---|
| default-1 | 253 | 0 | 0 | 0 | 0 | 1840・0 | 0 | 1778 | 10・0 | running、OOMKilled false |
| default-2 | 254 | 0 | 0 | 0 | 0 | 1901・0 | 0 | 1833 | 10・0 | running、OOMKilled false |
| retention-1 | 254 | 0 | 0 | 0 | 0 | 1908・0 | 0 | 1839 | 10・0 | running、OOMKilled false |
| retention-2 | 255 | 0 | 0 | 0 | 0 | 1800・0 | 0 | 1733 | 10・0 | running、OOMKilled false |

- 時刻の差（`clock.tsv` の VM−後・VM−前）: default-1 は始め 2・2、終わり 2・2 秒。default-2 は始め 2・2、終わり 0・0 秒。retention-1・retention-2 は始めも終わりも 0・0 秒。
- k6 の時間: default-1 14:56:10〜14:59:12・default-2 15:00:47〜15:03:51・retention-1 15:08:11〜15:11:13・retention-2 15:12:45〜15:15:48。

### 使い捨てのアプリのログ（片付けの前に数えた）

| 回 | WARN | ERROR | OutOfMemoryError | WARN のロガー |
|---|---|---|---|---|
| default-1 | 2 | 0 | 0 | `org.flywaydb.core.internal.database.base.Database` 1・`org.springframework.context.support.PostProcessorRegistrationDelegate$BeanPostProcessorChecker` 1 |
| default-2 | 2 | 0 | 0 | 同じ2つが 1 ずつ |
| retention-1 | 2 | 0 | 0 | 同じ2つが 1 ずつ |
| retention-2 | 2 | 0 | 0 | 同じ2つが 1 ずつ |

WARN は起動ごとの2行（Flyway の H2 の版の案内、Bean の後処理の案内）だけで、前の Intent までの試し走りと同じ。

## 2. 判定

- **FR16・BR3.5（既定の2回）: Met**。`memory.peak / memory.max` は default-1 0.8731・default-2 0.8696 で、2回とも 0.9 未満。OOMKilled なし・OutOfMemoryError 0 件・ERROR 0 件。
- 物差しは `memory.peak / memory.max < 0.9` のまま変えていない（team.md の Testing Posture）。`anon` の最大の割合（0.6991・0.6902）は並べるだけで、判定には使っていない。
- 参考: 前の Intent 261015-deps-ui-carryover の同じ場面は 94.4%・取り直し 93.1%（Not Met）だった。今回の既定の回との差は、条件（イメージの版、内部DB の膨らみ方など）が違うため、原因は確かめていない。0.9 までの余裕は 0.027〜0.030（約 55〜62 MiB）で小さい。

## 3. 試しの回（RETENTION_TIME=0）と既定の回の差

- **設定の確かめ（BR4.1）**: 試しの環境の `env.txt` の `db_url` は `jdbc:h2:file:/app/data/mastersmith;DEFRAG_ALWAYS=TRUE;RETENTION_TIME=0`。各回の後に、peak と stat を読んでからアプリを止め、内部DB のボリュームを読み取り専用（`:ro`、`ACCESS_MODE_DATA=r`）で開いて `INFORMATION_SCHEMA.SETTINGS` を読んだ結果、retention-1・retention-2 とも `RETENTION_TIME` は **0**（`settings_exit=0`）。既定の回は default-1・default-2 とも **45000**（H2 の既定）。試しの2回は試しの結果に使える。
- **効いたか（BR4.2）**: 試しの2回は2回とも 0.9 未満（0.7400・0.7175）で、効いた。

| 値 | 既定（2回） | 試し（2回） | 差 |
|---|---|---|---|
| peak / max | 0.8731・0.8696 | 0.7400・0.7175 | 約 0.13〜0.16 下がった（約 265〜320 MiB） |
| anon の最大 / max | 0.6991・0.6902 | 0.7030・0.6865 | ほぼ同じ |
| peak の時の file（MiB） | 335.0・343.4 | 72.0・54.3 | 約 260〜290 MiB 減った |
| mv.db の伸び（MiB） | 363.9・354.1 | 71.9・74.8 | 約 280〜290 MiB 減った |
| peak の時の kernel（MiB） | 16.0・16.0 | 7.8・7.6 | 約半分 |

読み取り: 下がったのはページキャッシュ（`file`、ほぼすべて `inactive_file`）と、内部DB のファイルの伸びで、`anon`（プロセスのメモリ）は変わっていない。`file` の伸びと mv.db の伸びがほぼ同じ大きさで、ページキャッシュの大部分は内部DB のファイルへの書き込みによると見られる（見立て。確かめていない）。

- **取り込むか（BR4.2・BR4.3）は依頼者に諮る**。この測りは使い捨ての環境だけで、配備物（Dockerfile・compose・application.yaml の接続先）は変えていない。U5 の計画の D4 のとおり、`RETENTION_TIME` を短くすると書き込みの途中で PC が止まったときに壊れうるとされる点と、取り込むときの値は、諮るときにあらためて決める。コンテナの上限 2g は上げていない。

## 4. 環境

| 項目 | 値 |
|---|---|
| コミット | `develop` の 288736d（backend・frontend は d220447 と同じ。`git diff --stat d220447 HEAD -- backend frontend` が空） |
| WAR | `./gradlew :backend:bootWar` は最新の扱いで作り直されず、既存の `backend/build/libs/mastersmith.war`（SHA-256 の先頭 6426111d63cc41e9）を使った |
| 使い捨てのアプリのイメージ | `mastersmith:business-data-views`（`sha256:521c25f67f0e…`、2026-10-20 に作成）。配備したアプリのタグ `local` は上書きしていない |
| k6 | `grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34` |
| アプリのコンテナの上限 | CPU 4・メモリ 2g（`MASTERSMITH_CONTAINER_CPUS=4`・`MASTERSMITH_CONTAINER_MEMORY=2g`） |
| 場面 | `dslMixed`、VUS 10・3 分・10 MiB の DSL（`valid-10mb.yaml`）、試験用の利用者 11 名 |
| 対象DB | PostgreSQL（使い捨ての環境の profile `targetdb-postgres`。見本の PG とは別） |
| colima の VM | aarch64・CPU 4・メモリ 6GiB・ディスク 100GiB・runtime docker |
| 環境の分け方 | 既定の2回は1つの `KEEP=1` の環境（`OUT_DIR=build/perf-results/dsl-k6`）、試しの2回は既定の環境を消してから `PERF_DB_URL` を付けて作り直した環境（`build/perf-results/dsl-k6-retention`） |

- 前の Intent の結果の置き場 `build/perf-results/dsl-k6` があったため、消さずに `build/perf-results/dsl-k6-prev-261015` に名前を変えてから測った（台本は記録の置き場が既にあると止まるため）。

## 5. 配備したアプリを止めた間

| 項目 | 時刻（UTC） |
|---|---|
| 止めた（`docker compose stop app`） | 2026-10-20T14:53:31Z |
| 戻した（`docker compose start app`） | 2026-10-20T15:17:13Z |
| `/actuator/health` の 200 | 2026-10-20T15:17:26Z |

止めた時間は約 23 分 42 秒（health の 200 まで約 23 分 55 秒）。作り直していない（同じコンテナ `mastersmith-app-1`、イメージ `mastersmith:local`、戻した後 healthy）。見本の PG（`mastersmith-targetdb-postgres-1`）・Mailpit・配備した環境のボリュームには触れていない。`.env`・`.env.targetdb` は開いていない。

## 6. スリープの確かめ

- 2つの段（既定・試し）とも、台本の全体（環境の作成から2回の測りまで）を `caffeinate -dims` で包んで流した。
- 流す前（測りの直前）の `pmset -g assertions`: 2つの段とも `PreventUserIdleSystemSleep 1`・`PreventUserIdleDisplaySleep 1`、caffeinate の行あり（`PreventSystemSleep` は 0）。
- 流した後の `pmset -g log`: 2026-10-20 23:50 JST（14:50 UTC）以降に `Sleep`・`DarkWake`・`Wake` の行は 0 件（最後の Sleep・DarkWake は 2026-10-18）。4回とも使える。

## 7. 片付けの確かめ

- WARN・ERROR は各回の `warn.txt` で、片付けの前に数えた（1節）。
- 秘密の検索（各段の `OUT_DIR` だけを対象に件数だけを出した）: 仮の管理者のパスワード・署名鍵・`PERF_USER_PASSWORD`・対象DB の各パスワード（`targetdb.env` の5つ・`app-targetdb.env` の1つ）・`env.txt` と TSV の `password`・`PASSWORD`・`jdbc:mysql`・`jdbc:mariadb`・`jdbc:postgresql`・`memory/` の下の `perf-user` は、2つの段ともすべて 0 件。
- 各段の後に `down -v`（profile 3つ）と一時ディレクトリの削除を行い、一時ディレクトリの残り 0・`mastersmith-perf_` のボリューム 0・`mastersmith-perf-` のコンテナ 0 を確かめた。
- 記録（`build/perf-results/dsl-k6/memory/default-*`・`build/perf-results/dsl-k6-retention/memory/retention-*`）は git の対象外の `build/` に残した。

## 8. 想定外のこと

- 無かった。アプリは止まらず（OOMKilled なし）、極端な遅さもなく（k6 の checks の失敗 0）、試しの設定は効いていた（`RETENTION_TIME` 0）、台本は4回とも最後まで流れた（終了コード 0）。
- 手順との小さな差: `pmset -g assertions` の `PreventSystemSleep` は 0 だった（caffeinate の `-s` は AC 電源のときだけ効く）。README の目安（`PreventUserIdleSystemSleep` などが 1）は満たし、眠りの記録も無かった。
