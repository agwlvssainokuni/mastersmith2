# 環境の一覧（environment-inventory）

Intent 261016-business-data-views（業務データの一覧・詳細・作成・更新・削除の画面と、持ち越しの直し。U1〜U5、Bolt B1〜B7）の配備先の環境です。配備先は、これまでどおり開発者の PC 上のコンテナです（`aidlc/spaces/default/memory/project.md` の Deployment）。クラウドの基盤（IaC・検証環境）は作りません。

- 書き方は前の Intent の同じ段の記録（`aidlc/spaces/default/intents/261004-role-menu/operation/environment-provisioning/environment-inventory.md`。以下「前の記録」）を正とし、今回の差を中心に書きます。`261015-deps-ui-carryover` にはこの段がありません。
- 値は 2026-10-21 に読み取りだけで確かめました。この段で行った変更は、使われていないビルドのキャッシュの片付け（Q1: A、`validation-report.md` の 5節）だけです。コンテナは止めず、イメージ・ボリュームは作る・消す操作をしていません。
- 秘密の値と個人に関する値は書きません。`.env`・`.env.targetdb` は開かず、項目の名前と件数だけを数えました。
- 決定は `environment-provisioning-questions.md`（Q1: A、まとめの確認 Looks correct）です。

## 1. 実行環境

| 項目 | 値 | 前の記録との差 |
|---|---|---|
| コンテナの実行環境 | colima（プロファイル `default`、aarch64、ランタイム docker）。Running | 同じ |
| VM の資源 | CPU 4・メモリ 6GiB・ディスク 100GiB | 同じ |
| VM の中のメモリ | 全体 5,910 MiB・使用 1,144 MiB・使える量 4,766 MiB | 使用が 988 MiB から増えた |
| VM の中のディスク | 96G のうち 27G 使用、空き 69G | 同じ |
| Docker | クライアント・サーバーとも 29.8.1 | 同じ |
| PC のディスク | `/System/Volumes/Data` が 460GiB のうち 383GiB 使用、空き 35GiB（92%） | 空きが 40GiB から 35GiB に減った |
| PC の電源 | バッテリー駆動（100%） | 新しく記録。長い操作（WAR の作り直しなど）は `caffeinate` で包む（`project.md` の学び） |
| compose のプロジェクト | `mastersmith`（配備した環境、`compose.yaml`）。一時の環境は `docker/perf/compose.yaml`（負荷の試験と戻しの練習。練習は `-p mastersmith-rollback`）と E2E（WAR を PC の上で直接起動） | 同じ |

## 2. コンテナ（今）

| コンテナ | イメージ | 状態 | 上限 | 今の使用 | PC に開く番号 |
|---|---|---|---|---|---|
| `mastersmith-app-1` | `mastersmith:local`（`f103aee1702a`） | Up・healthy（2026-10-20 15:17 UTC に起動、再起動 0 回、OOMKilled なし） | メモリ 2GiB（2,147,483,648 B）・CPU 4 | 642 MiB | `127.0.0.1:8080` |
| `mastersmith-mailpit-1` | `axllent/mailpit:v1.31.5`（ダイジェスト固定 `94f6b70c…`） | Up・healthy（profile `mail`） | 256MiB | 42 MiB | `127.0.0.1:8025`・`127.0.0.1:1025` |
| `mastersmith-targetdb-postgres-1` | `postgres:18.6`（ダイジェスト固定 `5a5a84b1…`） | Up（profile `targetdb-postgres`、2026-10-20 14:11 UTC に起動） | 512MiB | 26 MiB | 開かない（ports 空） |
| `mastersmith-lgtm-1` | `grafana/otel-lgtm:0.34.0` | Exited（6 日前に止めた。profile `monitoring`） | 1,536MiB | — | `127.0.0.1:3000`（起動したときだけ） |

- `mastersmith-app-1` は、前の Intent（261015-deps-ui-carryover）で配備した `main` の先頭 `6dd9bb0`（アプリのコードは `776738e`）の版のままです（`operation/deployment-pipeline/cd-config.md` の 1節と同じイメージ ID）。
- アプリのコンテナは root でない利用者 `10001:10001` で動き、特権なし・再起動しない（`restart: "no"`）設定です。

## 3. アプリのコンテナの設定

| 項目 | 今（`6dd9bb0` の版） | 配備の後（この Intent の版） |
|---|---|---|
| 起動の入口 | `java -XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1 ${MASTERSMITH_JAVA_OPTIONS:-} -jar /app/mastersmith.war` | 同じ（`Dockerfile` は変わらない） |
| 最大ヒープ | 1,024 MB（2g の 50%） | 同じ。`.env` に `MASTERSMITH_JAVA_OPTIONS` が無い（0 件）ため上書きされない |
| ヘルスチェック | イメージには HEALTHCHECK が無い。`compose.yaml` の bash の `/dev/tcp` で `GET /actuator/health` の 200 | 同じ（`compose.yaml` は変わらない） |
| ボリュームの結び付け | `mastersmith_mastersmith-data` → `/app/data`（読み書き） | 同じ |
| 環境変数（名前だけ） | `.env` から来る 16 項目と `TZ`（`docker compose config` で 17 項目）。名前に `WRITE` を含むものは 0 | `.env` の書き込みの2項目が加わり 19 項目（Deployment Execution の順 6・順 8） |
| 対象DB の接続 | 読み取りのプール `mastersmith-target-db` だけ | 書き込みのプール `mastersmith-target-db-write`（上限 5、起動時は接続しない）が増える。`.env` の2項目が両方あるときだけ使う |
| 内部DB のスキーマ | V12 | V13（`audit_events` に空を許す3列。入れ替えの起動で当たる） |

## 4. 設定のファイル（値は書かない）

| ファイル | 権限・管理 | 項目 | この Intent での差 |
|---|---|---|---|
| `.env` | 600、Git の管理外（`.gitignore` の 90 行目 `.env`）。最後の更新は 2026-09-30 23:48 | 16 項目（前の記録と同じ名前。初期管理者の2つ・署名鍵・コンテナの CPU とメモリ・ベース URL・メール3つ・対象DB の読み取りの7つ）。どれも1件ずつ。`MASTERSMITH_TARGET_DB_WRITE_*` は 0 件 | `MASTERSMITH_TARGET_DB_WRITE_USERNAME`（値 `mastersmith_writer`）・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD` を足す（`cd-config.md` の 3.2、Deployment Execution の順 6）。時間の上限と書き込みのプールの上限は足さず既定（5s・5） |
| `.env` に無い項目 | — | `MASTERSMITH_DB_PASSWORD`（Intent `260922-auth-audit-base` の決定で内部DB は空のパスワードで作られた。入れると開けなくなる）・`MASTERSMITH_JAVA_OPTIONS`（空）など、`.env.example` でコメントの行のもの | 同じ |
| `.env.targetdb` | 600、Git の管理外（`.gitignore` の 91 行目 `.env.*`）。最後の更新は 2026-09-25 11:29 | 2 項目（見本の PG の管理者・読み取りのパスワード）。アプリのコンテナには渡らない | `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` を足す（`.env` の書き込みのパスワードと同じ値。順 6）。今は 0 件 |
| `.env` の複写 | `$HOME/.mastersmith-env-before-writer` | まだ無い | 順 6 で権限 600 で作る（戻しで書き込みを止めるときに戻す。`rollback-runbook.md` 3.1） |
| `.env.example`・`.env.targetdb.example` | Git の管理（Git が持つ `.env` の名前のファイルはこの2つだけ） | — | 書き込みの項目を足した（コメントの行と1項目） |
| `Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml` | Git の管理 | — | 配備に効く差は無い（`cd-config.md` 1節） |
| `docker/targetdb/postgres/03-writer-account.sh` | Git の管理 | — | 新しく足した。既にあるボリュームでは自動で動かないため、順 7 で手で流す |
| `backend/src/main/resources/db/migration/V13__u2_business_data_audit_columns.sql` | Git の管理（WAR に入る） | — | 新しく足した（前進のみ・後方互換） |

## 5. 配備に使う WAR

| 項目 | 値 |
|---|---|
| 今のファイル | `backend/build/libs/mastersmith.war`（2026-10-20 22:56:37 作成、104,067,859 B） |
| 今の SHA-256 | `6426111d63cc41e9f1a8472f7c91adba98be33c1ddc93ab7debfcf69bfe041f4` |
| 今の中身 | `cherry/mastersmith/businessdata/` の項目 178 個、`V12__u4_role_assignment.sql`・`V13__u2_business_data_audit_columns.sql`、画面のビルド（`static/index.html` は `frontend/dist/index.html`（2026-10-20 22:28 作成）と同じ SHA-256） |
| 配備する版との関係 | 作成は B7 の統合のコミット `d220447`（2026-10-20 23:31:57）より前で、`d220447` の中身と同じか確かめられない。`d220447` の後のアプリのソースの差は `22de65f` の4ファイル（README・テスト・`vitest.config.ts`・`perf/README.md`）だけ |
| 使い方 | **使わない**。`cd-config.md` の順 2 のとおり、アプリを止める前に `./gradlew :backend:bootWar --rerun-tasks` で作り直し、SHA-256 と中身（`businessdata` の web・service・repository のクラス、V13、画面のビルド）を確かめる。`Dockerfile` は手元の WAR を写すだけで、`docker compose ... up -d --build app` は WAR を作り直さない |

## 6. イメージ

### 6.1 `mastersmith`（アプリ）

| タグ | ID | 作成 | 使い道 |
|---|---|---|---|
| `local` | `f103aee1702a` | 2026-10-16 | 今配備している版（`6dd9bb0`）。Deployment Execution の順 3 で `pre-business-data-views` のタグを付けて戻し先にする（**まだ付けていない**） |
| `business-data-views` | `521c25f67f0e` | 2026-10-20 | この Intent の Build and Test の試験のイメージ |
| `business-data-views-b4` | `d667682f9dcc` | 2026-10-20 | この Intent の Construction の試験のイメージ |
| `deps-ui-carryover`・`pre-deps-ui-carryover` | `66d84759b6a6`・`e0a5825d257c` | 2026-10-16・10-15 | 前の Intent の試し・戻し先 |
| `pre-role-menu`・`role-menu-before-dslfix`・`role-menu-before-s8fix` | — | 2026-10-04〜10-14 | それより前の戻し先・試し |

7 個のタグはすべて残しました（Q1: A はタグ付きのイメージを消さない）。

### 6.2 戻し先のイメージ `f103aee1702a` の設定

| 項目 | 値 |
|---|---|
| HEALTHCHECK | 無い（`Config` に `Healthcheck` の鍵が無い） |
| Entrypoint | 3節の今の起動の入口と同じ |
| 利用者 | `10001:10001` |
| 環境変数（名前だけ） | `PATH`・`JAVA_HOME`・`JAVA_VERSION`・`LANG`・`LANGUAGE`・`LC_ALL` |
| 健全性の見方 | 戻し（`rollback-runbook.md` 2節）も練習（6.1 の 4）も compose で起動し、compose のヘルスチェック（`/dev/tcp` で 200）で healthy を待つ |

### 6.3 配備と確かめで使うほかのイメージ

- 土台・複写の道具: `eclipse-temurin:25.0.4.1_1-jre-noble`（ダイジェスト `d9a39a23…`、`Dockerfile` と `cd-config.md` の 4節のコマンドで使う）。手元にある。
- Mailpit・postgres・mysql・mariadb のダイジェスト固定のイメージ、`grafana/otel-lgtm:0.34.0`、`grafana/k6:2.3.0`。ダイジェスト固定のイメージは `docker images` でタグが `<none>` と出るが、名前の無いイメージ（dangling）ではない。名前の無いイメージは 0 個（`docker images -f dangling=true`）。

### 6.4 量（片付けの後）

| 種類 | 量 |
|---|---|
| イメージ | 24 個・9.851GB（回収できる 4.573GB。タグ付きの使われていないイメージの分） |
| コンテナ | 4 個（動いている 3） |
| ボリューム | 64 個・10.15GB（回収できる 9.262GB） |
| ビルドのキャッシュ | 18 個・1.358GB（`docker system df` で回収できる 0B。片付けの前は 24 個・2.059GB・回収できる 700.8MB。`validation-report.md` の 5節） |

## 7. ボリュームとバックアップ

| 名前 | 中身 | 状態 |
|---|---|---|
| `mastersmith_mastersmith-data` | 内部DB（H2）。今はスキーマ V12。V13 は入れ替えの起動で当たる | 残す |
| `mastersmith_mastersmith-targetdb-postgres` | 見本の対象DB（2026-09-25 作成）。初期化は済んでいる | 残す（順 7 はコンテナだけを作り直す） |
| `mastersmith_mastersmith-monitoring` | 手元の監視のデータ | 残す |
| ほかのボリューム（61 個） | 名前の無いボリューム 50 個（使い捨ての環境・テストの名残）とほかのプロジェクトのもの | 消さない（Q1: A） |
| 練習の名前のボリューム（`mastersmith-rollback_*`・`perf*`） | 無い（前の練習の残りは無い） | — |
| `~/.mastersmith-backup/`（権限 700） | 前の Intent までの内部DB の複写 17 件と、数える道具 `count-db.sh`（700）。この Intent では、入れ替えの前（`…-pre-business-data-views.tgz`）と配備の後（`…-after-business-data-views.tgz`）の2件が増える（`cd-config.md` の 4節・`deployment-strategy.md` の S13） | 2件が権限 644（`validation-report.md` の N2） |
| `~/.mastersmith-rollback-practice` | 無い | 練習で作り、練習の後に消す（`rollback-runbook.md` 6.3） |

## 8. 見本の対象DB（`mastersmith-targetdb-postgres-1`）

件数と真偽値だけを確かめました（値は出していません）。

| 項目 | 今 | 配備の後の見込み |
|---|---|---|
| `mastersmith_writer` | 0 件 | 1 件（順 7 の台本で作る） |
| `mastersmith_reader` | 1 件 | 同じ |
| PUBLIC の `TEMP`（`has_database_privilege('public','business','TEMP')`） | `f`（B6 で取り上げ済み） | 同じ（台本も取り上げを流し直す） |
| `pg_%` 以外のロール | 2 つ（管理者・読み取り） | 3 つ |
| `sales`・`public` 以外の利用者のスキーマ | 0（E2E の `e2e_biz_*` の残りは無い） | 同じ |
| `sales` の表 | 表 4 つ（`customers`・`orders`・`order_items`・引用符を含む名前の表）とビュー `customer_orders` | 同じ |
| `sales.customers` の行 | 2 行 | スモークテストで作った行は消す（`deployment-strategy.md` の Q4: A） |
| コンテナの環境の項目（名前だけ） | `POSTGRES_DB`・`POSTGRES_USER`・管理者と読み取りのパスワード | 書き込みのパスワードが加わる（順 7 の作り直しで `.env.targetdb` を読み直す） |
| ローカルのソケットの認証 | `trust`（`docker compose exec` で台本を流せる） | 同じ |
| 台本 `03-writer-account.sh` | 書き込みのパスワードが空なら止まる。`sales` の行の操作（`SELECT`・`INSERT`・`UPDATE`・`DELETE`）と順序の `USAGE` だけを与え、DDL の権限は与えない | 順 7 の確かめで `1|t|t|t|f|f|f` |

## 9. 番号（PC の側の待ち受け）

| 番号 | 使うもの | 今 |
|---|---|---|
| 8080 | アプリ | `127.0.0.1` で待ち受け中 |
| 8025・1025 | Mailpit | `127.0.0.1` で待ち受け中 |
| 3000 | Grafana（profile `monitoring`） | 空き（止めている） |
| 18080 | 戻しの練習（`docker/perf/compose.yaml`） | 空き |
| 5432・15432 | 見本の対象DB（15432 は E2E の上乗せのときだけ） | 開かない |

## 10. 戻しの練習のメモリの見積もり

`rollback-runbook.md` の 6節の練習は、配備したアプリを止めずに、前の版（`mastersmith:pre-business-data-views`）と練習の対象DB を `docker/perf/compose.yaml` で起動します。

| コンテナ | メモリの上限 |
|---|---|
| 配備したアプリ（`mastersmith-app-1`） | 2,048 MiB |
| Mailpit | 256 MiB |
| 見本の対象DB | 512 MiB |
| 練習のアプリ（`MASTERSMITH_CONTAINER_MEMORY=2g`） | 2,048 MiB |
| 練習の対象DB（`MASTERSMITH_PERF_TARGETDB_POSTGRES_MEMORY` の既定） | 512 MiB |
| **合計** | **5,376 MiB** |

- VM の全体 5,910 MiB に入ります。今のコンテナの外の使用は約 430 MiB（使用 1,144 MiB から3つのコンテナの使用 710 MiB を引いた値）で、上限の合計と合わせて約 5,806 MiB です。余裕は約 100 MiB です。
- 上限はコンテナが使える最大で、実際の使用はこれより小さい見込みです（今は配備したアプリで 642 MiB）。練習では大きな入力を流しません。
- **条件: 手元の監視（`lgtm`、上限 1,536 MiB）は練習の間に起動しない**。同じ理由で、練習の間は見本の対象DB のほかの種類（`targetdb-mysql`・`targetdb-mariadb`）や k6 を起動しません。練習の直前に `mastersmith-lgtm-1` が Exited であることと、`docker stats --no-stream` の値を確かめます。

## Sources

- 読み取りの結果（2026-10-21）: `colima list`、`colima ssh -- free -m`・`df -h /`、`docker version`、`docker ps -a`、`docker stats --no-stream`、`docker inspect`（`mastersmith-app-1`・`mastersmith-mailpit-1`・`mastersmith-targetdb-postgres-1`）、`docker image inspect f103aee1702a`・`mastersmith:business-data-views`、`docker images`（`dangling=true`・`--digests` を含む）、`docker volume ls`・`docker volume inspect`、`docker system df`、`docker builder du`、`docker compose --profile targetdb-postgres config`（値を出さない形）、見本の PG の件数と真偽値の SELECT とコンテナの環境の項目の名前、`lsof`、`df -h`、`ls -l`、`git check-ignore -v`、`git ls-files`、`.env`・`.env.targetdb` の項目の名前と件数、`shasum -a 256`・`unzip -l`（WAR）、`git diff --stat d220447 HEAD -- . ':!aidlc'`、`git show --stat d220447`、`curl`（`/actuator/health`・`/actuator/metrics`）
- `environment-provisioning-questions.md`（この PC の実行環境、決まっていること、Q1: A、まとめの確認）
- `operation/deployment-pipeline/cd-config.md`（1節・3節・3.2・4節）・`deployment-strategy.md`（S13）・`rollback-runbook.md`（2節・3.1・6節）
- `construction/u2-businessdata-write/infrastructure-design/infrastructure-specification.md`（1節・2節）、`construction/u1-businessdata-read/infrastructure-design/infrastructure-specification.md`（1節）、`construction/u3-businessdata-screens/infrastructure-design/infrastructure-specification.md`（1節・2節）
- 前の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/environment-provisioning/environment-inventory.md`
- リポジトリ（読むだけ）: `Dockerfile`、`compose.yaml`、`docker/perf/compose.yaml`、`docker/targetdb/postgres/03-writer-account.sh`、`.env.example`、`.env.targetdb.example`、`.gitignore`

## Assumptions & Open Questions

- [assumption] `.env` の対象DB の読み取りの7項目は見本の PG（`targetdb-postgres`・`business`・`sales`・`mastersmith_reader`）を指す（値は開いていない）。書き込みは同じ接続先を別の資格情報で使う。
- [assumption] 練習のアプリの実際のメモリの使用は、配備したアプリと同じくらい（数百 MiB）に収まる。実際の値は Deployment Execution の練習で `docker stats` で記録する。
- [assumption] 順 2 で作り直した WAR を、順 8 の入れ替えまで作り直さない。順 2 の後に `./gradlew` を流さない。
