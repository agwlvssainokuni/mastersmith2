# Environment Provisioning の質問（261016-business-data-views）

配備先は、これまでどおり開発者の PC 上のコンテナです。この段は、Docker の実行環境（colima）・イメージ・ボリューム・`.env`・`compose.yaml` の設定と読み替え、設計の値を1つずつ確かめます（`project.md` の Deployment の学び）。クラウドの基盤は作りません。前の Intent の同じ段の記録（直近は `aidlc/spaces/default/intents/261004-role-menu/operation/environment-provisioning/`。`261015-deps-ui-carryover` にはこの段が無い）と、この Intent の承認済みの配備の手順（`operation/deployment-pipeline/` の `cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`、Q1〜Q5 すべて A）を正とし、今回の差だけを扱います。WAR の作り直し・アプリの入れ替え・`.env` の書き足し・見本の PG の作り直しは、次の Deployment Execution の段で行います。

## この PC の実行環境（読み取りで確かめた結果）

2026-10-21 に読み取りだけで確かめました。値（秘密・個人に関する値）は表示していません。`.env`・`.env.targetdb` は開かず、項目の名前と件数だけを数えました。コンテナは止めていません。

| 対象 | 結果 |
|---|---|
| colima の VM | CPU 4・メモリ 6GiB・ディスク 100GiB（`aarch64`、runtime docker）。VM の中のメモリは全体 5,910 MiB・使用 1,144 MiB・使える量 4,766 MiB。ディスクは 96G 中 27G 使用（空き 69G） |
| PC | ディスクの空き 35GiB（使用 92%。前の Intent `261004-role-menu` の 40GiB より減った）。電源はバッテリー駆動（100%） |
| Docker のディスクの使用 | イメージ 24 個・9.85GB（片付けられる 4.57GB）、コンテナ 4 個、ボリューム 64 個・10.15GB（片付けられる 9.26GB）、ビルドのキャッシュ 2.06GB（片付けられる 0.70GB）。名前の無いイメージ（`dangling`）は 0 個 |
| 動いているコンテナ | `mastersmith-app-1`（`mastersmith:local` = `f103aee1702a`、healthy、2026-10-20 15:17 UTC 起動）・`mastersmith-mailpit-1`（v1.31.5、healthy）・`mastersmith-targetdb-postgres-1`（`postgres:18.6` を digest で固定、Up）。`mastersmith-lgtm-1` は止まっている（Exited） |
| 実際の使用量と上限 | app 642MiB / 2GiB（`docker inspect`: Memory 2,147,483,648・NanoCpus 4,000,000,000・restart `no`）、Mailpit 42MiB / 256MiB、見本の PG 26MiB / 512MiB |
| `docker compose config`（`--profile targetdb-postgres`） | `app`: `mastersmith:local`、`mem_limit` 2g、`cpus` 4、`127.0.0.1:8080`、restart `no`、ヘルスチェックは bash の `/dev/tcp` で `/actuator/health` の 200。環境の項目 17 個で、名前に `WRITE` を含むものは 0。`targetdb-postgres`: `mem_limit` 512m、PC に番号を開けない（ports 空）、初期化の台本 `docker/targetdb/postgres` を読み取りでつなぐ、`.env.targetdb` を `env_file`（`required: false`）で読む |
| `.env` の項目 | 16 項目（権限 `-rw-------`）。書き込みの項目 `MASTERSMITH_TARGET_DB_WRITE_*` は 0 件。対象DB の読み取りの7項目（`TYPE`・`HOST`・`PORT`・`DATABASE`・`SCHEMA`・`USERNAME`・`PASSWORD`）・`MASTERSMITH_WEB_BASE_URL`・`MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_CONTAINER_CPUS` は各1行ある。`MASTERSMITH_JAVA_OPTIONS` は 0 件（`MaxRAMPercentage` 50 と `ExitOnOutOfMemoryError` が上書きされない）。`.env.example` にあって `.env` に無いのは `MASTERSMITH_DB_PASSWORD` だけ（前からの決定で入れない） |
| `.env.targetdb` の項目 | 2 項目（管理者・読み取りのパスワード、権限 `-rw-------`）。`.env.targetdb.example` にあって無いのは `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` だけ（足す予定の項目） |
| 見本の PG の中 | `mastersmith_writer` の件数 0、`mastersmith_reader` 1、`has_database_privilege('public','business','TEMP')` は `f`（B6 の取り上げは済み）。`pg_%` 以外のロールは 2 つ、`sales`・`public` 以外の利用者のスキーマは 0（E2E の `e2e_biz_*`・`mastersmith_e2e_*` の残りは無い）。`sales` の表は 4 つとビュー `customer_orders`、`customers` は 2 行。ボリューム `mastersmith_mastersmith-targetdb-postgres` は 2026-09-25 作成（初期化は済んでいて、台本は自動では動かない）。コンテナの環境の項目は `POSTGRES_DB`・`POSTGRES_USER`・管理者と読み取りのパスワードの4つで、書き込みのパスワードはまだ渡っていない。ローカルのソケットは `trust`（`exec` で台本を流せる） |
| 書き込みのアカウントの台本 | `docker/targetdb/postgres/03-writer-account.sh`（README の「既にある見本の DB に書き込みのアカウントを足す（U2）」の手順で使う）。書き込みのパスワードが空なら止まる。ロールが無ければ作り、`sales` の行の操作（`SELECT`・`INSERT`・`UPDATE`・`DELETE`）と順序の `USAGE` だけを与え、`TEMPORARY` を PUBLIC から取り上げる（DDL の権限は与えない。`project.md` の Mandated と合う） |
| 今の WAR | `backend/build/libs/mastersmith.war`（2026-10-20 22:56:37 作成、SHA-256 `6426111d63cc…`）。中に `businessdata` のファイル 178 個・`V13__u2_business_data_audit_columns.sql`・画面のビルドがある。ただし作成は B7 の統合のコミット `d220447`（23:31:57）より前で、`d220447` の中身と同じかは確かめられない |
| `Dockerfile` | `COPY backend/build/libs/mastersmith.war` で手元の WAR を写すだけ。`docker compose up --build` は WAR を作り直さない。`HEALTHCHECK` は無い |
| アプリのソースの差 | `git diff --stat d220447 HEAD -- . ':!aidlc'` は `22de65f` の4ファイル（README・`AbstractBusinessDataReadIT.java`・`frontend/vitest.config.ts`・`perf/README.md`）だけ。`develop` の先頭は `a0e2ddd`（`aidlc/` だけのコミット）。未コミットは `aidlc/` の下だけ |
| イメージ | `mastersmith:local` = `f103aee1702a`（2026-10-16 作成、前の Intent で配備した版）。戻し先のタグ `mastersmith:pre-business-data-views` はまだ無い（`cd-config.md` の順 3 で付ける）。ほかに `mastersmith:pre-deps-ui-carryover`・`pre-role-menu`・`deps-ui-carryover`・`role-menu-before-dslfix`・`role-menu-before-s8fix`・`business-data-views`（Build and Test の試験用、10-20 23:52）・`business-data-views-b4` がある |
| 戻し先のイメージ `f103aee1702a` | `Config` に `Healthcheck` が無い（イメージの HEALTHCHECK は無い）。Entrypoint は `MaxRAMPercentage` 50・`ExitOnOutOfMemoryError`・`h2.compactThreads=1`、利用者 10001:10001 |
| 戻しの練習の環境（`docker/perf/compose.yaml`） | `app` は `mastersmith:${MASTERSMITH_IMAGE_TAG:-local}`・`127.0.0.1:18080`・ボリューム `perf-data`・compose のヘルスチェック（`/dev/tcp` で 200）・`mem_limit` は `MASTERSMITH_CONTAINER_MEMORY`（既定 2g）。`env_file` は `MASTERSMITH_PERF_ENV_FILE` の後に `MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE`（後の値が効く）。`targetdb-postgres` は `MASTERSMITH_PERF_TARGETDB_ENV_FILE` の `POSTGRES_PASSWORD` を直接読み、同じ初期化の台本（01〜03）を使い、pg_isready のヘルスチェックと `mem_limit` 512m（既定）。`rollback-runbook.md` 6.1 の3つの一時の環境ファイルの形と合う。PC の 18080 は空いている。`mastersmith-rollback_*`・`perf*` のボリュームは残っていない |
| 複写の置き場 | `~/.mastersmith-backup`（権限 `drwx------`、中に 18 個、最新は前の Intent の `…-after-deps-ui-carryover.tgz`）。`~/.mastersmith-env-before-writer`・`~/.mastersmith-rollback-practice` はまだ無い。複写と展開に使う `eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23…` は手元にある |

## 決まっていること（質問にしない）

- **設計の値はどれも満たす**。アプリの上限（CPU 4・メモリ 2g・最大ヒープ 50%）、見本の PG の 512m と番号を開けないこと、Mailpit、`restart: "no"` は `compose.yaml` と動いているコンテナで一致する。`Dockerfile`・`compose.yaml` は変わらない（`cd-config.md` 1節）。
- **`.env`・`.env.targetdb` に足すのは決まった3項目だけ**。今は3つとも 0 件で、足す前提がそろっている。足し方は `cd-config.md` 3.2（Q1: A・Q2: A）で、Deployment Execution の順 6 で流す前にコマンドを依頼者に見せて承認を得る。時間の上限と書き込みのプールの上限は足さず既定のまま。
- **見本の PG は作り直しで書き込みのパスワードを受け取る**。今のコンテナには書き込みのパスワードが渡っていないため、順 7 の `--force-recreate --no-deps targetdb-postgres` で `.env.targetdb` を読み直させてから台本を流す（ボリュームは残す）。台本と確かめの SQL は `cd-config.md` 3.2 のとおりで、期待は `1|t|t|t|f|f|f`。
- **WAR は作り直す**。今の WAR は `d220447` より前に作られ、中身が配備する版と同じか確かめられないため、`cd-config.md` の順 2 のとおり停止の前に `./gradlew :backend:bootWar --rerun-tasks` で作り直し、SHA-256 と中身（`businessdata` のクラス・V13・画面のビルド）を確かめる（`project.md` の学び）。この段では作り直さない。
- **戻し先のイメージに HEALTHCHECK が無いことは手順で吸収済み**。戻し（`rollback-runbook.md` 2節）も練習（6.1 の 4）も compose で起動し、compose のヘルスチェック（`/dev/tcp` で 200）で healthy を待つため、`running` を健全と読むことは無い（`project.md` の学び）。
- **戻しの練習で、配備したアプリを止めずに前の版と練習の対象DB を同時に動かせる**。上限の合計は、配備したアプリ 2,048 MiB・Mailpit 256 MiB・見本の PG 512 MiB・練習のアプリ 2,048 MiB・練習の PG 512 MiB の 5,376 MiB で、VM の全体 5,910 MiB に入る（今のコンテナの外の使用は約 430 MiB）。余裕は小さいため、前の Intent と同じく**手元の監視（`lgtm`、上限 1,536 MiB）は練習の間は起動しない**。実際の使用は上限よりかなり小さい（今の app は 642 MiB）。
- **ディスクは足りる**。WAR の作り直し（約 100MB）・イメージの作り直し（約 660MB）・配備の前と後の複写・練習のボリュームに対して、VM の空き 69G・PC の空き 35GiB がある。
- 戻し先のタグ・内部DB の複写・`.env` の書き足し・見本の PG の作り直し・入れ替え・確かめは、`cd-config.md` の順 2〜11 で Deployment Execution の段が行う。この段では、上の値を `environment-inventory.md` と `validation-report.md` に記録する。

## Q1. 古いイメージとビルドのキャッシュの片付け

Docker の中に、前の Intent までの戻し先などのタグ付きのイメージ（`mastersmith:pre-role-menu`・`role-menu-before-dslfix`・`role-menu-before-s8fix`・`pre-deps-ui-carryover`・`deps-ui-carryover`）と、この Intent の Construction で作ったイメージ（`mastersmith:business-data-views`・`business-data-views-b4`）が残っています。名前の無いイメージは 0 個のため、前の Intent の片付け方（`docker image prune`）では何も消えず、片付けられるのは実質ビルドのキャッシュ（0.70GB）だけです。ディスクは足りています（VM の空き 69G・PC の空き 35GiB。PC は前の Intent より 5GiB 減った）。この段で片付けますか？

A. 前の Intent（`261004-role-menu` の Q2: A）と同じ扱いにし、使われていないビルドのキャッシュだけを片付ける（`docker builder prune`）。タグ付きのイメージ・ボリュームは残す。片付ける前後の量を記録する
B. 片付けない（今の量を記録だけする）
C. A に加えて、2つ前より古い版の `mastersmith:*` のタグ付きのイメージ（`pre-role-menu`・`role-menu-before-dslfix`・`role-menu-before-s8fix`・`business-data-views-b4`）を消す。`local`・`pre-deps-ui-carryover`・`deps-ui-carryover`・`business-data-views` は残す。消す一覧を先に見せて、あなたの承認を得てから消す
X. Other (please specify)

推奨は A です。前の Intent と同じ扱いで、戻し先に使えるタグ付きのイメージと内部DB などのボリュームに触れずに済み、ディスクも足りているためです。代わりに、PC のディスクの使用は 92% のまま残ります。

[Answer]: A

## Sources

- この Intent の配備の段: `operation/deployment-pipeline/cd-config.md`（1節・3節の順 1〜11・3.2・4節）・`deployment-strategy.md`・`rollback-runbook.md`（2節・6節・7節）・`deployment-pipeline-questions.md`（Q1〜Q5）
- この Intent の基盤の設計: `construction/u2-businessdata-write/infrastructure-design/infrastructure-specification.md`（1節・2節・Rollback の行）、`construction/u1-businessdata-read/infrastructure-design/infrastructure-specification.md`、`construction/u3-businessdata-screens/infrastructure-design/infrastructure-specification.md`（見本の PG の 512m・E2E のスキーマとロール）
- 前の同じ段: `aidlc/spaces/default/intents/261004-role-menu/operation/environment-provisioning/environment-provisioning-questions.md`（Q1・Q2）
- リポジトリ（読むだけ）: `Dockerfile`、`compose.yaml`（`app`・`targetdb-postgres`）、`docker/perf/compose.yaml`、`docker/targetdb/postgres/03-writer-account.sh`、`.env.example`・`.env.targetdb.example`、`git diff --stat d220447 HEAD -- . ':!aidlc'`、`git show --stat d220447`、`backend/build/libs/mastersmith.war`（作成の時刻・SHA-256・`unzip -l` の一覧）
- 実行環境（読み取りだけ、2026-10-21）: `colima list`、`colima ssh -- free -m`・`df -h /`、`df -h`（PC）、`docker system df`、`docker ps -a`、`docker stats --no-stream`、`docker inspect`（`mastersmith-app-1`・`mastersmith-targetdb-postgres-1`・`mastersmith-mailpit-1`）、`docker image inspect f103aee1702a`、`docker images`（`dangling=true` を含む）、`docker volume ls`・`docker volume inspect`、`docker compose --profile targetdb-postgres config`（値を出さない形）、見本の PG の件数と真偽値の SELECT（値は出していない）とコンテナの環境の項目の名前、`.env`・`.env.targetdb` の項目の名前と件数、`lsof`（PC の待ち受けの番号）、`ls -l ~/.mastersmith-backup`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections・Mandated の学び）

## Assumptions & Open Questions

- [assumption] `.env` の対象DB の読み取りの7項目は見本の PG（`targetdb-postgres`・`business`・`sales`・`mastersmith_reader`）を指す（値は開いていない。`cd-config.md` の前提と同じ）。練習の環境でも同じ名前の `targetdb-postgres` が練習の PG に解決される。
- [assumption] 戻しの練習の間の実際のメモリの使用は上限の合計よりかなり小さく、VM に収まる。練習の直前に `docker stats --no-stream` で確かめ、足りなければ依頼者に諮る。
- 片付けの範囲は Q1 で決める。

## Consolidated Summary Confirmation

**environment-inventory**
- この PC の実行環境（colima の VM: CPU 4・メモリ 6GiB・ディスクの空き 69G、PC の空き 35GiB）、動いているコンテナ（配備したアプリ `mastersmith:local` = `f103aee1702a`・見本の PG・Mailpit、lgtm は停止）、イメージ・ボリューム・`compose config` の上限、`.env`・`.env.targetdb` の項目の有無（値は見ない。足す3項目は 0 件）を一覧にする。
- 見本の PG は `mastersmith_writer` が 0 件、TEMP の取り上げは済み（`f`）、台本 `03-writer-account.sh` は DDL の権限を与えない。

**validation-report**
- 設計の値（上限・番号・健全性の確かめ・WAR の写し方）を実際のコンテナで1つずつ確かめた結果を書く。設計の値を満たせない点は無い。
- 今の WAR は d220447 より前に作られたため、配備の手順の順 2 で作り直して SHA-256 と中身を確かめる（cd-config.md のとおり）。
- 戻し先のイメージ `f103aee1702a` は HEALTHCHECK を持たないが、戻しと練習は compose のヘルスチェックで健全を待つ。戻しの練習を配備したアプリと並べると上限の合計は 5,376 MiB で VM に収まるが余裕は約 100 MiB のため、練習の間は lgtm を起動しない。
- 片付けは、使われていないビルドのキャッシュだけ（`docker builder prune`）を行い、タグ付きのイメージとボリュームは残し、前後の量を記録する（Q1: A）。

Does this all look correct before I generate the environment provisioning artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
