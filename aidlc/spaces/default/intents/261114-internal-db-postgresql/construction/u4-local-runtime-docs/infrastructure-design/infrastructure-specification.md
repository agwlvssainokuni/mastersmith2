# インフラの仕様 — u4-local-runtime-docs

この単位は、手元の compose に内部DB 用の PostgreSQL を足し（既定では起動しない）、`.env.example` に項目を足します。クラウドの基盤・IaC・検証環境は、配備先が決まるまで作りません（`project.md` の Deployment）。ここに書く具体の設定は、NFR 設計（`security-design.md` の DD-1〜DD-11）に、その審査（N-01〜N-06）の答えを反映した形です。レビュー済みの NFR 設計の文書は書き換えず、差を「NFR 設計との差」に書きます。

## Deployment

| 項目 | 決めたこと | 理由 |
|---|---|---|
| サービス | `internal-db-postgres`（`compose.yaml`）。`profiles: [internal-db-postgres]`。`image: postgres:18.6@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42`（`targetdb-postgres` と同じ版とダイジェスト）。`ports` を置かない。`mem_limit: 512m`。`restart: "no"`。ログは json-file（10m・3 本。app・lgtm・mailpit と同じ形） | FR4.1・NFR2.2・NFR2.6・NFR3.1・NFR7.1 |
| ボリューム | 新しい `mastersmith-internal-db-postgres` を `/var/lib/postgresql` にマウントする（PostgreSQL 18 の置き場。`targetdb-postgres` と同じ形）。`volumes:` の一覧に足す | NFR2.3（別のボリューム） |
| 環境変数（コンテナ側） | `POSTGRES_USER: ${MASTERSMITH_INTERNAL_DB_POSTGRES_USER:-}`・`POSTGRES_PASSWORD: ${MASTERSMITH_INTERNAL_DB_POSTGRES_PASSWORD:-}`・`POSTGRES_DB: ${MASTERSMITH_INTERNAL_DB_POSTGRES_DATABASE:-}`。値は書かない。空のまま profile を選ぶと、PostgreSQL のコンテナが起動を拒む（空のパスワードで動かない）ことで失敗を知らせる | NFR2.1。既定値なしの `${…}` は H2 の利用者の `docker compose` の実行でも警告が出るため、空の既定値（`:-`）にする（NFR 設計の審査 N-04 の答え） |
| 起動確認 | `healthcheck.test: [CMD-SHELL, 'pg_isready -h 127.0.0.1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"']`（`interval: 5s`・`timeout: 5s`・`retries: 12`・`start_period: 10s`）。`-h 127.0.0.1` は、公式イメージの初回の初期化の間に一時サーバーが unix ソケットだけで動く状態を「健全」と誤判定しないため。`$$` は `CMD-SHELL` の形でだけ効く（コンテナの中のシェルで展開する） | NFR4.1（NFR 設計の審査 N-01 の答え） |
| アプリの依存 | `app` の `depends_on` に `internal-db-postgres: {condition: service_healthy, required: false}` を足す。profile を選ばないとき（H2）は依存が外れて起動が変わらない（`docker compose config` で確かめる） | NFR4.1・NFR1.1 |
| アプリの接続 | compose の `app` の設定は変えず、`.env` の `MASTERSMITH_DB_URL=jdbc:postgresql://internal-db-postgres:5432/<データベース名>`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD` を、上の 3 つの値と同じにして設定する（文書の手順）。H2 に戻すときは、`.env` の `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME` の行を消す（またはコメントにする）。`MASTERSMITH_DB_PASSWORD` の行は `.env.example` では空の値として元から有効なので、H2 のときは空に戻す（H2 は空のパスワードを使う今の既定） | NFR2.1（審査 N-05 の答え: `.env.example` の `MASTERSMITH_DB_PASSWORD=` は今コメントでない） |
| 環境 | 手元の開発者の PC だけ（colima の VM。CPU 4・メモリ 6GiB）。検証環境・本番は無い | `team.md` の Deployment |
| IaC | 無し | 配備先が決まるまで基盤を作らない |
| 資源の大きさ | `mem_limit: 512m`。アプリ（既定 2g）・手元の監視（1536m）・見本の対象DB（512m）と同時に動かすと VM の 6GiB の近くになるため、内部DB 用を使うときは、使わない対象DB・監視・負荷の試験を止める（`docs/operations.md` に 1 行） | NFR2.6 |
| 秘密情報 | 新しい秘密情報・環境変数の値を足さない。`.env.example` の項目名（値は空）: `MASTERSMITH_INTERNAL_DB_POSTGRES_USER`・`MASTERSMITH_INTERNAL_DB_POSTGRES_PASSWORD`・`MASTERSMITH_INTERNAL_DB_POSTGRES_DATABASE` | NFR2.1 |

## Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| `internal-db-postgres`（compose の profile） | アプリの内部DB（任意。既定は H2） | 上の Deployment のとおり | 起動: `docker compose --profile internal-db-postgres up -d`。止める: `docker compose stop internal-db-postgres`。消す: `docker compose rm -f internal-db-postgres`（ボリュームは `docker volume rm mastersmith_mastersmith-internal-db-postgres` で別に消す） |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | 無し | — | 1 インスタンス・開発者の PC の前提で当たらない |

## Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| `compose.yaml` の `app` サービスの `depends_on` | u4-local-runtime-docs | （既存の配備） | 追加の 1 行だけ。既存の設定を変えない |
| イメージ `postgres:18.6@sha256:…`（`targetdb-postgres` と同じ値） | （既存。対象DB が持つ） | u4-local-runtime-docs・u1-internal-db-platform（`TargetDbImages.postgres()`） | 同じ版・ダイジェストで一緒に上げる |

## NFR 設計との差（レビュー済みの文書は書き換えず、ここに記録する）

1. DD-4 の healthcheck は `CMD-SHELL` と `-h 127.0.0.1` に決める（審査 N-01）。
2. DD-2 の既定値なしの `${…}` を、空の既定値（`${…:-}`）に変える（審査 N-04）。
3. DD-3 の「3 行をコメントに戻す」は、`MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME` の行を消す（またはコメントにする）・`MASTERSMITH_DB_PASSWORD` を空に戻す、に読み替える（審査 N-05）。
4. DD-1 の「ログは json-file（他のサービスと同じ）」は、app・lgtm・mailpit と同じ形であり、`targetdb-postgres` とは同じではない（審査 N-06）。
5. DD-6 の `pg_dump` の例は、`monitoring-design.md`・`cicd-pipeline.md` の運用の節で `docker compose exec -T internal-db-postgres sh -c '…'` の形に直す（審査 N-02）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/security-design.md`・`reviews/review-01.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-requirements/tech-stack-decisions.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`（U4 の節）
- `compose.yaml`・`.env.example`（読み取り）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment）

## Assumptions & Open Questions

- [assumption] 公式の `postgres:18.6` イメージで、`pg_isready -h 127.0.0.1` が初回の初期化の間は失敗し、本起動の後に成功する。Code Generation の最初の確かめ（使い捨てのコンテナ）で確かめる。
