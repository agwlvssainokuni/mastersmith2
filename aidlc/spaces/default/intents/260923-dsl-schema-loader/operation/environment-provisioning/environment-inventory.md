# 環境の一覧（environment-inventory）

配備先は開発者の PC 上のコンテナのため、環境は colima の VM・Docker のイメージ・ボリューム・`.env`・`compose.yaml` の設定と読み替える（`aidlc/spaces/default/memory/project.md` の Deployment）。クラウドの基盤（IaC・検証環境・警報の通知先）は作らない（配備先が決まるまで）。確かめた日時は 2026-09-25（UTC では 2026-09-24T16 時台）。

## 1. 実行環境

| 項目 | 値 | 根拠 |
|---|---|---|
| colima の VM | プロファイル `default`、aarch64、CPU 4・メモリ 6GiB・ディスク 100GiB、ランタイム docker | `colima list` |
| VM のディスク | 96G のうち 32G 使用・64G 空き（34%） | `colima ssh -- df -h /` |
| VM のメモリ（見本の DB の起動の後） | total 5,910MiB・used 905MiB・available 5,004MiB | `colima ssh -- free -m` |
| Compose のプロジェクト | `mastersmith`（`compose.yaml` の `name`） | `compose.yaml` |

## 2. コンテナ

| コンテナ | イメージ | 状態 | 上限 | 役割 |
|---|---|---|---|---|
| `mastersmith-app-1` | `mastersmith:local`（`sha256:8441534a745f…`、版 `10742a3`） | healthy（この段では触っていない） | メモリ 2GiB（使用 432MiB） | 今動いているアプリ。次の段で新しい版に替える |
| `mastersmith-targetdb-postgres-1` | `postgres:18.6@sha256:86c951e05bf56c93d95d397747fb8820ac76cc3bedb78f43abd83eedbe3666ae` | Up（この段で起動。`restart: "no"`） | メモリ 512MiB（使用 31MiB） | 配備したアプリにつなぐ見本の対象DB（Deployment Pipeline の Q1: B・F1: A） |

## 3. ボリューム

| ボリューム | 中身 | 状態 |
|---|---|---|
| `mastersmith_mastersmith-data` | 内部DB（H2） | 約 72KB。次の段の配備の前に `~/.mastersmith-backup/` へ複写する |
| `mastersmith_mastersmith-targetdb-postgres` | 見本の対象DB | この段の初めての起動で作った（見本のスキーマ `sales` と `mastersmith_reader`） |
| `mastersmith_mastersmith-monitoring` | 手元の監視（grafana/otel-lgtm） | 既にある。この段では触っていない |

## 4. `.env`（中身は開かず、項目があるかどうかと、2つの値が同じかどうかだけを確かめた）

| 項目 | 状態 |
|---|---|
| `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` | ある（依頼者が入れた） |
| `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` | ある（依頼者が入れた） |
| `MASTERSMITH_TARGET_DB_TYPE`・`HOST`・`PORT`・`DATABASE`・`SCHEMA`・`USERNAME` | ある。値は決めたとおり（`postgresql`・`targetdb-postgres`・`5432`・`business`・`sales`・`mastersmith_reader`） |
| `MASTERSMITH_TARGET_DB_PASSWORD` | ある。読み取りのパスワードと同じ値であることを、値を出さずにハッシュの比べで確かめた（1回目は1文字違い、依頼者が直した） |
| `MASTERSMITH_AUTH_SIGNING_KEY`・`MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_CONTAINER_CPUS` | ある（前の Intent から） |
| `MASTERSMITH_DB_URL` | 無い（既定の `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE` が効く） |

## 5. 範囲外として記録だけするもの

- Docker のボリュームは 60 個（うち使われていないもの 約 9.1GB）、イメージは 31 個（使われていないもの 約 11.3GB）。Testcontainers や負荷の試験の残りと見られるが、今回は消さない（`docker system df`）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/environment-provisioning/environment-provisioning-questions.md`（Q1: A・Q2: A、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`（見本の対象DB・バックアップ・戻し用のタグ）
- `compose.yaml`（`targetdb-postgres`・`app`）、`docker/targetdb/postgres/01-sample-schema.sql`・`02-reader-account.sh`、`README.md`（「コンテナでの起動と確認」「手元で試す対象DB（compose の profile）」）
- 実行したコマンド: `colima list`・`colima ssh -- df -h /`・`colima ssh -- free -m`・`docker system df`・`docker volume ls`・`docker ps`・`docker inspect`・`docker stats --no-stream`・`grep -c '^<項目>=.' .env`

## Assumptions & Open Questions

None.
