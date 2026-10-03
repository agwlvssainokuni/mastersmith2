# 環境の一覧（environment-inventory）

Intent 260930-user-admin（利用者の管理の画面）の配備先の環境です。配備先は、これまでどおり開発者の PC 上のコンテナです（`aidlc/spaces/default/memory/project.md` の Deployment）。クラウドの基盤（IaC・検証環境）は作りません。

- 値は 2026-10-03 21:36 ごろ（JST）に読み取りで確かめました。この段で行った書き込みは、依頼者が承認した片付け（Q1: B）の2つのコマンドだけです（`validation-report.md` の 4節）。
- 秘密の値と個人に関する値は書きません。`.env` は開かず、項目の名前ごとの件数だけを数えました。
- 突き合わせた設計の値は、各単位の `infrastructure-design/infrastructure-specification.md`（U3〜U5）と、`operation/deployment-pipeline/cd-config.md` から取りました。

## 1. 実行環境

| 項目 | 値 |
|---|---|
| コンテナの実行環境 | colima（プロファイル `default`、aarch64、ランタイム docker）。Running |
| VM の資源 | CPU 4・メモリ 6GiB・ディスク 100GiB |
| Docker | クライアント・サーバーとも 29.8.1。イメージの置き場は containerd の snapshotter（overlayfs） |
| PC のディスク | `/System/Volumes/Data` が 460GiB のうち 389GiB を使用、空き 30GiB（93%）。片付けの前は空き 29GiB |
| compose のプロジェクト | `mastersmith`（配備した環境、`compose.yaml`）。一時の環境は `docker/perf/compose.yaml`（負荷の試験・戻しの練習）と E2E（WAR を PC の上で直接起動） |

## 2. コンテナ（今）

| コンテナ | イメージ | 状態 | メモリの上限 | PC に開く番号 |
|---|---|---|---|---|
| `mastersmith-app-1` | `mastersmith:local`（`c77c1bb247f9`） | Up・healthy（2026-09-30 23:50 JST に作成・起動、再起動 0 回、OOMKilled なし） | 2GiB（2,147,483,648 B）。CPU の上限 4 | `127.0.0.1:8080` |
| `mastersmith-mailpit-1` | `axllent/mailpit:v1.31.2`（ダイジェスト固定 `74d609a4…`） | Up・healthy（profile `mail`） | 256MiB | `127.0.0.1:8025`・`127.0.0.1:1025` |
| `mastersmith-targetdb-postgres-1` | `postgres:18.6`（ダイジェスト固定 `5a5a84b1…`） | Up（profile `targetdb-postgres`、ヘルスチェックなし） | 512MiB | 開かない |
| `mastersmith-lgtm-1` | `grafana/otel-lgtm:0.34.0` | Exited（2日前に止めた。profile `monitoring`、見たいときだけ起動） | 1.5GiB（1536m） | `127.0.0.1:3000`（起動したときだけ） |

- 動いている3つの上限の合計は約 2.75GiB で、VM の 6GiB に入ります。配備の入れ替え（`app` を作り直す）では合計は変わりません。
- アプリのコンテナは root でない利用者 `10001:10001` で動き、特権なし・再起動しない（`restart: "no"`）設定です。
- `mastersmith-app-1` は、前の Intent（260929-log-deps-cleanup）で配備した版のままです（`cd-config.md` の 1節と同じイメージ ID）。

## 3. アプリのコンテナの設定（`docker inspect`）

| 項目 | 値 |
|---|---|
| 起動の入口 | `java -XX:MaxRAMPercentage=50.0 -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1 ${MASTERSMITH_JAVA_OPTIONS:-} -jar /app/mastersmith.war`（`Dockerfile` の ENTRYPOINT） |
| ヘルスチェック | bash の `/dev/tcp` で `GET /actuator/health` の 200 を見る。間隔 30 秒・時間切れ 5 秒・3回・始めの猶予 40 秒・始めの間隔 2 秒 |
| ボリュームの結び付け | `mastersmith_mastersmith-data` → `/app/data`（読み書き） |
| ログ | json-file、10m × 3 |
| 環境変数（名前だけ） | `.env` から来る 16 項目（4節）と `TZ`、イメージの `PATH`・`JAVA_HOME`・`JAVA_VERSION`・`LANG`・`LANGUAGE`・`LC_ALL` |

## 4. 設定のファイル（値は書かない）

| ファイル | 権限・管理 | 項目 |
|---|---|---|
| `.env` | 600、Git の管理外（`.gitignore` の `.env`）。最後の更新は 2026-09-30 23:48（今のアプリのコンテナの作成より前） | 16 項目。初期管理者の2つ（`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`）・署名鍵（`MASTERSMITH_AUTH_SIGNING_KEY`）・コンテナの CPU とメモリ（`MASTERSMITH_CONTAINER_CPUS`・`MASTERSMITH_CONTAINER_MEMORY`）・ベース URL（`MASTERSMITH_WEB_BASE_URL`）・メール3つ（`SPRING_MAIL_HOST`・`SPRING_MAIL_PORT`・`MASTERSMITH_MAIL_FROM`）・対象DB の7つ（`MASTERSMITH_TARGET_DB_TYPE`・`_HOST`・`_PORT`・`_DATABASE`・`_SCHEMA`・`_USERNAME`・`_PASSWORD`）。どれも1件ずつ |
| `.env` に無い項目 | — | `MASTERSMITH_DB_MAXIMUM_POOL_SIZE`（既定 30 が効く）・`MASTERSMITH_JAVA_OPTIONS`（空）・`SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD`（Mailpit は資格情報なし） |
| `.env.targetdb` | 600、Git の管理外（`.gitignore` の `.env.*`） | 見本の対象DB の2項目（`MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`）。アプリのコンテナには渡らない |
| `.env.example` | Git の管理 | この Intent では変えていない |
| `compose.yaml` | Git の管理 | profile `observability`・`monitoring`・`mail`・`targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb`。この Intent では変えていない。`docker compose config --quiet` が通る |
| `docker/perf/compose.yaml` | Git の管理 | 使い捨ての環境と戻しの練習（`127.0.0.1:18080`）。一時の環境ファイルの場所 `MASTERSMITH_PERF_ENV_FILE` を必ず求める作り。この Intent では変えていない。場所を仮に与えると `config --quiet` が通る |

この Intent の差（`git diff 31b980b develop`）: `compose.yaml`・`Dockerfile`・`.env.example`・`backend/src/main/resources/application*.yaml`・`docker/` は差が無い。`backend/src/main/resources/` の差は `db/migration/V9__u1_user_suspension.sql` の追加だけ（配備のときに Flyway が当てる。`cd-config.md` の 2節）。

## 5. イメージ

### 5.1 `mastersmith`（アプリ）

| タグ | ID | 作成 | 使い道 |
|---|---|---|---|
| `local` | `c77c1bb247f9` | 3日前 | 今配備している版（ソース `8489241`）。Deployment Execution で、入れ替えの前に `pre-user-admin` のタグを付けて戻し先にする（**まだ付けていない**） |
| `pre-log-deps-cleanup` | `252bc44e8108` | 4日前 | 前の Intent の戻し先 |
| `pre-quality-followup` | `9e5243a30b77` | 4日前 | 前々の Intent の戻し先 |
| `pre-user-management`・`pre-storage-memory`・`pre-followup`・`pre-dsl` | — | 8〜10日前 | それより前の戻し先 |
| `storage-memory-fix`・`storage-memory-base`・`followup-fixes` | — | 8日前 | 前の Intent の試験のイメージ |

10 個のタグはすべて残しました（Q1: B）。

### 5.2 配備と確かめで使うほかのイメージ（手元にある）

| イメージ | 使うところ |
|---|---|
| `eclipse-temurin:25.0.4_7-jre-noble` | `Dockerfile` の土台（入れ替えのビルド） |
| `axllent/mailpit` のダイジェスト `74d609a4…` | `compose.yaml` の `mailpit`（動いている） |
| `postgres` のダイジェスト `5a5a84b1…` | `targetdb-postgres`（動いている）と結合テスト |
| `mysql` のダイジェスト `ade067ae…`・`mariadb` のダイジェスト `d4fdec05…` | 結合テスト（`./gradlew verify`）と profile `targetdb-mysql`・`targetdb-mariadb` |
| `grafana/otel-lgtm:0.34.0` | 手元の監視（profile `monitoring`） |
| `grafana/k6:2.3.0` | 負荷の試験（performance-validation） |

ダイジェスト固定のイメージは `docker images` ではタグが `<none>` と出ますが、名前の無いイメージ（dangling）ではなく、片付けの対象になりませんでした。

### 5.3 量

| 時点 | イメージ | ビルドのキャッシュ |
|---|---|---|
| 片付けの前 | 47 個・22.1GB（回収できる 16.37GB） | 24 個・2.595GB（回収できる 2.142GB） |
| 片付けの後 | 46 個・21.43GB（回収できる 15.7GB） | 9 個・453.2MB（回収できる 0B） |

## 6. ボリュームとバックアップ

| 名前 | 中身 | 大きさ |
|---|---|---|
| `mastersmith_mastersmith-data` | 内部DB（H2）。今はスキーマ V8。V9 は配備のときに当たる | 65.54kB |
| `mastersmith_mastersmith-targetdb-postgres` | 見本の対象DB | 65.77MB |
| `mastersmith_mastersmith-monitoring` | 手元の監視のデータ | 744.5MB |
| ほかのボリューム（61 個） | 名前の無いボリューム（使い捨ての環境・テストの名残）と、ほかのプロジェクトのもの（`devenv_*`・`mastermeister-devenv_*`・`sqlapp2_*`） | 合計で約 9.26GB が回収できる。今回は消していない（Q1: B） |
| `~/.mastersmith-backup/`（権限 700） | 前の Intent までの内部DB のバックアップ（6 件）。この Intent はバックアップを取らない（`cd-config.md` の 2節、Q1: B） | — |

ボリュームは片付けの前後とも 64 個で、変わっていません。

## 7. 番号（PC の側の待ち受け）

| 番号 | 使うもの | 今 |
|---|---|---|
| 8080 | アプリ | `127.0.0.1` で待ち受け中（colima の転送） |
| 8025・1025 | Mailpit | `127.0.0.1` で待ち受け中（colima の転送） |
| 3000 | Grafana（profile `monitoring`） | 空き（止めている） |
| 18080 | 使い捨ての環境・戻しの練習 | 空き |
| 18081 | E2E の WAR | 空き |
| 5432 | 見本の対象DB | PC に開かない（compose の中の名前だけで接続） |

## Sources

- 読み取りの結果（2026-10-03）: `colima list`・`docker version`・`docker info`・`docker compose -p mastersmith ps`・`docker ps -a`・`docker inspect`・`docker image inspect`・`docker images`・`docker volume ls`・`docker system df`（`-v` を含む）・`docker compose config --quiet` と `--format json` の値を伏せた項目・`df -h`・`lsof`・`ls -l`・`git check-ignore`・`grep -c '^NAME=' .env`・`git diff 31b980b develop`
- `construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`（1節〜3節・5節）
- `construction/u4-admin-forbidden-ui/infrastructure-design/infrastructure-specification.md`（1節・4節）
- `construction/u5-user-admin-ui/infrastructure-design/infrastructure-specification.md`（1節・3節・5節）
- `operation/deployment-pipeline/cd-config.md`（1節・2節）
- `aidlc/spaces/default/intents/260925-user-management/operation/environment-provisioning/`（前の記録の形と値）
- `compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`（読むだけ）

## Assumptions & Open Questions

- [assumption] `.env` の `MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_CONTAINER_CPUS` の値は開いていません。`docker inspect` と `docker compose config` の上限が 2GiB・CPU 4 であることから、既定と同じ値と見ています。
- 片付けなかったボリューム（約 9.26GB）と古いタグのイメージの整理は、依頼者の判断で後で行います（この Intent では行わない）。
