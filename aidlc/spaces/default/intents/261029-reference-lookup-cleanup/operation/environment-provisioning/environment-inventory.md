# 環境の一覧（environment-inventory）

Intent 261029-reference-lookup-cleanup（他テーブル参照の REFERENCE・LOOKUP の読み取り・保存前の確かめ・画面、perf の記録の伏せ字の道具、コンテナと perf の文書の整理。U1〜U5）の配備先の環境です。配備先は、これまでどおり開発者の PC 上のコンテナです（`aidlc/spaces/default/memory/project.md` の Deployment）。クラウドの基盤（IaC・検証環境）は作りません。

- 決定は `environment-provisioning-questions.md` の Q1: A（古い `.env` の複写を消す）です。7節に結果を書きました。
- 書き方は前の同じ段の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/environment-provisioning/environment-inventory.md`。以下「前の記録」）を正とし、今回の差を中心に書きます。`261025-deps-perf-carryover`・`261027-perf-targets-review` にはこの段がありません。
- 値は 2026-11-01 04:35〜04:45 UTC に読み取りだけで確かめました。コンテナは止めず、イメージ・ボリューム・`.env` は作る・変える・消す操作をしていません。この段で行った変更は、Q1: A による古い `.env` の複写の削除（7節）だけです。
- 秘密の値と個人に関する値は書きません。`.env`・`.env.targetdb` は開かず、項目の名前と件数だけを数えました。

## 1. 実行環境

| 項目 | 値 | 前の記録との差 |
|---|---|---|
| コンテナの実行環境 | colima（プロファイル `default`、aarch64、ランタイム docker）。Running | 同じ |
| VM の資源 | CPU 4・メモリ 6GiB・ディスク 100GiB | 同じ |
| VM の中のメモリ | 全体 5,911 MiB・使用 972 MiB・使える量 4,938 MiB | 使用が 1,144 MiB から減った |
| VM の中のディスク | 96G のうち 40G 使用（42%）、空き 57G | 使用が 27G から増えた |
| Docker | クライアント・サーバーとも 29.9.2 | 29.8.1 から上がった（今日 01:07Z 頃の VM の apt の更新で docker が再起動） |
| PC のディスク | `/System/Volumes/Data` が 460GiB のうち 388GiB 使用、空き 47GiB（90%） | 空きが 35GiB から 47GiB に増えた |
| PC の電源 | バッテリー駆動（77%、放電中） | WAR の作り直しなど長い操作は `caffeinate` で包む（`project.md` の学び） |
| compose のプロジェクト | `mastersmith`（配備した環境、`compose.yaml`）。見本の PG だけは `docker/e2e/compose.targetdb-port.yaml` を重ねて起動している | 上乗せが新しい（今日の docker の再起動の後に E2E のために作り直した） |

## 2. コンテナ（今）

| コンテナ | イメージ | 状態 | 上限 | 今の使用 | PC に開く番号 |
|---|---|---|---|---|---|
| `mastersmith-app-1` | `mastersmith:local`（`e0ede6ddd5c4`） | Up・healthy（2026-11-01 01:52:57Z 起動、再起動 0 回、OOMKilled なし） | メモリ 2GiB（2,147,483,648 B）・CPU 4 | 505 MiB | `127.0.0.1:8080` |
| `mastersmith-mailpit-1` | `axllent/mailpit:v1.32.0`（ダイジェスト固定 `0790692b…`） | Up・healthy（profile `mail`、01:19:34Z 起動） | 256MiB | 12 MiB | `127.0.0.1:8025`・`127.0.0.1:1025` |
| `mastersmith-targetdb-postgres-1` | `postgres:18.6`（ダイジェスト固定 `5a5a84b1…`） | Up（profile `targetdb-postgres`、01:19:34Z 起動。E2E の上乗せ付き） | 512MiB | 26 MiB | `127.0.0.1:15432`（上乗せ） |
| `mastersmith-lgtm-1` | `grafana/otel-lgtm:0.38.0`（ダイジェスト固定 `75cdf892…`） | Exited (0)（2026-10-31 に止めた。profile `monitoring`） | 1,536MiB | — | `127.0.0.1:3000`（起動したときだけ） |

- `mastersmith-app-1` は前の Intent（`261027-perf-targets-review`）で配備した版のままです（`cd-config.md` の 1節と同じイメージ ID）。
- 3つの動いているコンテナとも、イメージ ID は `compose.yaml` のダイジェストと一致します（`team.md` の Deployment の確かめ）。
- アプリのコンテナは root でない利用者 `10001:10001` で動き、特権なし・再起動しない（`restart: "no"`）設定です。

## 3. アプリのコンテナの設定

| 項目 | 今（`e0ede6dd` の版） | 配備の後（この Intent の版） |
|---|---|---|
| 起動の入口 | `Dockerfile` の ENTRYPOINT（`MaxRAMPercentage=50.0`・`ExitOnOutOfMemoryError`・`-Dh2.compactThreads=1`・`${MASTERSMITH_JAVA_OPTIONS:-}`） | 同じ（`Dockerfile` は変わらない） |
| 最大ヒープ | 1,024 MB（2g の 50%） | 同じ。`.env` に `MASTERSMITH_JAVA_OPTIONS` が無い（0 件） |
| ヘルスチェック | イメージには HEALTHCHECK が無い。`compose.yaml` の bash の `/dev/tcp` で `GET /actuator/health` の 200 | 同じ |
| ボリュームの結び付け | `mastersmith_mastersmith-data` → `/app/data`（読み書き、2026-09-23 作成） | 同じ |
| 環境変数（名前だけ） | `.env` から来る 18 項目と `TZ`（`docker compose config` で 19 項目、コンテナの中も同じ 18＋`TZ`） | 同じ（2つの新しい設定は `.env` に足さず既定 5s・10s で動く） |
| 対象DB の接続 | 読み取りのプール（既定の上限 10）と書き込みのプール（既定の上限 5） | 同じ。作成・更新の定義の取得と保存前の確かめで読み取りの接続を1本借りる（U1・U2） |
| 内部DB のスキーマ | V13 | 同じ（移行なし） |
| `depends_on` | 無い | 同じ（`up -d app` は見本の PG・Mailpit に触れない） |

## 4. 設定のファイル（値は書かない）

| ファイル | 権限・管理 | 項目 | この Intent での差 |
|---|---|---|---|
| `.env` | 600、Git の管理外（`.gitignore` の 33 行目 `.env`）。最後の更新は 2026-10-21 08:48 | 18 項目（初期管理者の2つ・署名鍵・コンテナの CPU とメモリ・ベース URL・メール3つ・対象DB の読み取りの7つ・書き込みの2つ）。どれも1件ずつ | 変えない。`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` は 0 件のまま（既定で動く） |
| `.env.targetdb` | 600、Git の管理外（34 行目 `.env.*`）。最後の更新は 2026-10-21 08:48 | 3 項目（見本の PG の管理者・読み取り・書き込みのパスワード） | 変えない |
| `.env.example` | Git の管理 | — | 2つの設定のコメントの行を足した（U1） |
| `compose.yaml`・`Dockerfile` | Git の管理 | — | 配備に効く差は無い。`compose.yaml` の Mailpit・lgtm の版は U5 で上げ済み |
| `backend/src/main/resources/application.yaml` | Git の管理（WAR に入る） | — | `query-timeout.reference`（既定 5s）・`reference-read-total`（既定 10s）を足した |

## 5. 配備に使う WAR

| 項目 | 値 |
|---|---|
| 今のファイル | `backend/build/libs/mastersmith.war`（2026-11-01 12:26 JST 作成、106,119,824 B） |
| 今の SHA-256 | `4cd5c41626f4dd0513656e551b39ec1a71038fedb5622901ea5db8543008222f` |
| 今の中身（読み取り） | `WEB-INF/classes/application.yaml` に `reference: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE:5s}` と `reference-read-total: ${MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL:10s}` がある |
| 配備する版との関係 | 作成は固定先の更新のコミット `cb23466b`（12:22:56 JST）の後。`cb23466b` の後のアプリのソースの差は 0（`aidlc/` の外の差なし）。同じ中身の見込みだが、作り直さずには確かめられない |
| 使い方 | **使わない**。`cd-config.md` の順 2 のとおり、アプリを止める前に `./gradlew :backend:bootWar --rerun-tasks` で作り直し、SHA-256 と中身（2つの設定、画面のビルドの参照の部品の文言）を確かめる。`Dockerfile` は手元の WAR を写すだけ |

## 6. イメージ

### 6.1 `mastersmith`（アプリ）

| タグ | ID | 作成 | 使い道 |
|---|---|---|---|
| `local` | `e0ede6ddd5c4` | 5 日前 | 今配備している版。`cd-config.md` の順 3 で `pre-reference-lookup-cleanup` のタグを付けて戻し先にする（**まだ付けていない**） |
| `reference-lookup-cleanup` | `eab1676f26de` | 2026-11-01 10:38 JST | この Intent の Build and Test の試験のイメージ。使っているコンテナは 0。順 12 で消す（Q4: A） |
| `pre-perf-targets-review` | `3075065a2653` | 5 日前 | 前の Intent の戻し先 |
| `carryover` | `009a757de120` | 6 日前 | 前の Intent（`261025-deps-perf-carryover` の頃）の試験のイメージと見られる |
| `pre-deps-perf-carryover` | `4cec01e79026` | 11 日前 | 前の戻し先 |
| `pre-business-data-views` | `f103aee1702a` | 2 週間前 | それより前の戻し先 |

### 6.2 戻し先のイメージ `e0ede6ddd5c4` の設定

| 項目 | 値 |
|---|---|
| HEALTHCHECK | 無い（`Config` に `Healthcheck` の鍵が無い） |
| 健全性の見方 | 戻し（`rollback-runbook.md` 2節）は compose で起動し、compose のヘルスチェック（`/dev/tcp` で 200）で healthy を待つ |
| 今の内部DB との組み合わせ | 今まさにこの組み合わせで動いている（V13） |

### 6.3 配備と確かめで使うほかのイメージ

- 複写の道具: `eclipse-temurin:25.0.4.1_1-jre-noble`（ダイジェスト `d9a39a23…`）。手元にある（`docker images` でタグは `<none>` と出るがダイジェスト固定のもの）。
- 名前の無いイメージ（dangling）は 0 個。
- `compose.yaml` の `otel-collector`（profile `observability`、`0.163.2`）は手元に無い（手元は `0.162.0`）。配備・確かめで使わない。

### 6.4 量

| 種類 | 量 |
|---|---|
| イメージ | 28 個・21.08GB（回収できる 16GB。古い lgtm・mysql などのタグなし・使われていないイメージ） |
| コンテナ | 4 個（動いている 3） |
| ボリューム | 74 個・13.39GB（回収できる 12.56GB） |
| ビルドのキャッシュ | 13 個・823.2MB（回収できる 106.2MB） |

## 7. ボリューム・バックアップ・リポジトリの外のファイル

| 名前 | 中身 | 状態 |
|---|---|---|
| `mastersmith_mastersmith-data` | 内部DB（H2、V13） | 残す |
| `mastersmith_mastersmith-targetdb-postgres` | 見本の対象DB | 残す |
| `mastersmith_mastersmith-monitoring` | 手元の監視のデータ | 残す |
| ほかのボリューム（71 個） | 名前の無いボリューム 60 個（使い捨ての環境・テストの名残）とほかのプロジェクトのもの | 消さない |
| `~/.mastersmith-backup/`（700） | 内部DB の複写 20 件（すべて 600。最新は `…-after-perf-targets-review.tgz`）と、数える道具 `count-db.sh`・`count-db2.sh`（700） | この Intent で `…-pre-reference-lookup-cleanup.tgz`・`…-after-reference-lookup-cleanup.tgz` の2件が増える（600） |
| `~/.mastersmith-tools/`（700） | 数える道具 `Cols.java`・`SinceCount.java`（644。置き場が 700 のため他の利用者は読めない）。H2 2.4.240 の JDBC は Gradle のキャッシュにある | 4.1 の数えに使う |
| `~/.mastersmith-env-before-user-management` | 古い `.env` の複写（600、2026-09-29） | **消した**（Q1: A）。2026-11-01T04:41:22Z に中身を開かずに `rm -f` で消し、`ls` で No such file or directory を確かめた |
| `~/.mastersmith-rollback-practice` | 無い | 戻しの練習はしない |

## 8. 見本の対象DB（`mastersmith-targetdb-postgres-1`）

件数と真偽値だけを確かめました（値は出していません）。

| 項目 | 今 |
|---|---|
| `pg_%` 以外のロール | 3 つ（管理者・`mastersmith_reader`・`mastersmith_writer`） |
| PUBLIC の `TEMP` | `f`（取り上げ済み） |
| `pg_`・`information_schema` 以外のスキーマ | `public`・`sales`（E2E の `e2e_biz_*` の残りは無い） |
| `sales.customers`・`sales.orders` の行 | 2 行・1 行 |
| 外部キー `sales.orders` → `sales.customers` | ある（既定の DSL の生成で `orders.customer_id` が `REFERENCE` になる元） |
| コンテナの環境の項目（名前だけ） | `POSTGRES_DB`・`POSTGRES_USER`・管理者・読み取り・書き込みのパスワード |

## 9. 番号（PC の側の待ち受け）

| 番号 | 使うもの | 今 |
|---|---|---|
| 8080 | アプリ | `127.0.0.1` |
| 8025・1025 | Mailpit | `127.0.0.1` |
| 15432 | 見本の対象DB（E2E の上乗せ） | `127.0.0.1` |
| 3000 | Grafana | 空き（止めている） |

## 10. メモリの見積もり

| 場面 | 上限の合計 | 余裕（VM の全体 5,911 MiB − 上限の合計 − コンテナの外の使用 約 429 MiB） |
|---|---|---|
| 今と配備の後（app 2,048・Mailpit 256・見本の PG 512） | 2,816 MiB | 約 2,666 MiB |
| 戻し（イメージだけ、同じ上限で入れ替え） | 2,816 MiB | 約 2,666 MiB |
| 参考: 上に lgtm（1,536）を足したとき | 4,352 MiB | 約 1,130 MiB |

- 配備と戻しはアプリのコンテナを同じ上限で入れ替えるだけで、2つのアプリを並べません。戻しの練習もしません（`rollback-runbook.md`）。
- コンテナの外の使用は、VM の使用 972 MiB から3つのコンテナの使用 543 MiB を引いた値です。

## Sources

- 読み取りの結果（2026-11-01）: `colima list`、`colima ssh -- free -m`・`df -h /`、`df -h`、`pmset -g batt`、`docker version`、`docker ps -a`、`docker stats --no-stream`、`docker inspect`（4つのコンテナ）、`docker image inspect`（`mastersmith:local`・`mastersmith:reference-lookup-cleanup`）、`docker images`（`dangling=true` を含む）、`docker volume ls`・`docker volume inspect`、`docker system df`、`docker builder du`、`docker compose --profile targetdb-postgres config --format json`（`jq` で項目を選び値を出さない形）、見本の PG の件数と真偽値の SELECT とコンテナの環境の項目の名前、`.env`・`.env.targetdb` の項目の名前と件数、`ls -l`（`~/.mastersmith-*`）、`git check-ignore -v`、`shasum -a 256`・`unzip -p`（WAR の `application.yaml` の該当の行）、`git log`・`git diff --stat cb23466b HEAD -- . ':!aidlc'`、`git submodule status`
- `environment-provisioning-questions.md`
- `operation/deployment-pipeline/cd-config.md`（1節・3節・4節・4.1）・`deployment-strategy.md`・`rollback-runbook.md`（2節・3節）
- 各単位の `construction/u*/infrastructure-design/infrastructure-specification.md`（U1・U2・U3・U5 の1節・2節）
- 前の記録: `aidlc/spaces/default/intents/261016-business-data-views/operation/environment-provisioning/environment-inventory.md`
- リポジトリ（読むだけ）: `Dockerfile`、`compose.yaml`、`docker/e2e/compose.targetdb-port.yaml`、`.gitignore`

## Assumptions & Open Questions

- [assumption] `.env` の対象DB の項目は見本の PG を指す（値は開いていない。`cd-config.md` の前提と同じ）。
- [assumption] `mastersmith:carryover` は前の Intent の試験のイメージで、この Intent の配備と戻しには使わない（名前と作成日からの見立て）。
- [assumption] 順 2 で作り直した WAR を、順 6 の入れ替えまで作り直さない。
- 古い `.env` の複写は Q1: A で消した（7節）。
