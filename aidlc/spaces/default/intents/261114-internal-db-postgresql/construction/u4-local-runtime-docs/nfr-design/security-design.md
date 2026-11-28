# セキュリティと配備の設計 — u4-local-runtime-docs

NFR 要件（`security-requirements.md`・`tech-stack-decisions.md`）の NFR1.1・NFR2.1〜NFR2.6・NFR3.1・NFR4.1・NFR4.2・NFR5.1・NFR6.1・NFR7.1・NFR8.1・NFR9.1 を、手元の compose と文書の設計に落とします。この単位は種類 packaging で、性能・拡張性・信頼性・観測の設計の文書は作りません（それらの設計は、この文書の決定に含めます）。「DD-n」は設計の決定の番号です。審査（NFR 要件のレビュー）の N-1〜N-4 の答えを、DD-3・DD-4・DD-8・DD-9 に書きます。

## 事実の確かめ（質問ではありません）

- 見本の対象DB `targetdb-postgres`（`compose.yaml`）は、`postgres:18.6@sha256:e3d24f69…`・`profiles: [targetdb-postgres]`・ポートを開けない・`mem_limit: 512m`・ボリューム `mastersmith-targetdb-postgres`（`/var/lib/postgresql` にマウント。PostgreSQL 18 の置き場の形）・資格情報は `.env.targetdb` の形。内部DB 用はこの形に合わせ、サービス・ボリューム・資格情報を別にする。
- U1 のテストの土台 `InternalDbPostgresContainer` は、`TargetDbImages.postgres()` を使う。内部DB 用の「テストの定数」の固定先は新しく作らず、既存の `TargetDbImages` が持つ（NFR8.1 の「テストの定数」は新規でなく、既存の値と揃える）。
- Docker Compose は 5.8.1。`depends_on` に `required: false`（profile で起動しないサービスを依存から外す書き方）が使える。
- `.github/dependabot.yml` の docker-compose の項は、`compose.yaml` のイメージを見る（Dependabot が見る固定先）。`TargetDbImages`・`docker/perf/compose.yaml`・docs は見ない固定先で、手で揃える。
- アプリの内部DB の設定は `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD`（`application.yaml`）。U1 が接続先から種類を判断する。

## 設計の決定

| ID | 決定 | 満たす NFR | 理由 |
|---|---|---|---|
| DD-1 | compose にサービス `internal-db-postgres` を足す。`profiles: [internal-db-postgres]`、イメージは `postgres:18.6@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42`（`targetdb-postgres` と同じ版とダイジェスト）、`mem_limit: 512m`、`restart: "no"`、ログは json-file（10m・3 本。他のサービスと同じ）。`ports` を置かない。ボリュームは新しい `mastersmith-internal-db-postgres`（`/var/lib/postgresql` にマウント）。見本の対象DB と別のサービス・別のボリューム。起動: `docker compose --profile internal-db-postgres up -d`。 | NFR2.2・NFR2.3・NFR2.6・NFR3.1・NFR7.1 | 見本の対象DB と同じ形で、profile で選んだときだけ起動する（FR4.1）。既定の配備（H2）の設定は変えない |
| DD-2 | 資格情報の出どころは `.env` だけ。コンテナ側の変数 `POSTGRES_USER`・`POSTGRES_PASSWORD`・`POSTGRES_DB` は、`.env` の `MASTERSMITH_INTERNAL_DB_POSTGRES_USER`・`MASTERSMITH_INTERNAL_DB_POSTGRES_PASSWORD`・`MASTERSMITH_INTERNAL_DB_POSTGRES_DATABASE` から `${…}`（既定値なし）で渡す。`.env.example` に 3 項目を、値を空のまま、コメントで説明して足す（`MASTERSMITH_DB_*` の項の近く）。`compose.yaml` にも文書にも値を書かない。見本の対象DB の `.env.targetdb` の値・変数名と重ねない。 | NFR2.1・NFR2.3 | 秘密を設定ファイルに直接書かない（`project.md` の Forbidden・Mandated）。値が空のまま profile を選ぶと、PostgreSQL のコンテナが起動を拒んで失敗する（空のパスワードで動かない） |
| DD-3 | アプリが内部DB 用の PostgreSQL に接続する形: `.env` の `MASTERSMITH_DB_URL=jdbc:postgresql://internal-db-postgres:5432/<データベース名>`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD` を、上の 3 つの値と同じにして設定する（文書の手順。compose の `environment` で組み立てない）。アプリの利用者は、コンテナの `POSTGRES_USER`（データベースの所有者）と同じ 1 つのアカウントで、管理用と接続用に分けない。手元・単一インスタンスの配備で、利用者の入力が届かない内部DB のため。権限の分離（所有者と実行用の分離）は、配備先が決まったときに決める（既知の制約として文書に書く）。 | NFR2.1・NFR2.3 | 審査 N-2 の答え。アプリの設定の仕組み（U1 の種類の判断）は変えず、`.env` の値だけで切り替える。H2 に戻すときは、これらの 3 行をコメントに戻す |
| DD-4 | 起動の順: アプリの `depends_on` に `internal-db-postgres: {condition: service_healthy, required: false}` を足す。profile を選ばないとき（H2）は依存が外れ、起動は変わらない。選んだときは、PostgreSQL が `pg_isready` で健全になってからアプリを起動する。サービスの `healthcheck` は `pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}`（`interval: 5s`・`timeout: 5s`・`retries: 12`・`start_period: 10s`）。アプリ側の再試行は足さない（`src/main` を変えない）。 | NFR4.1・NFR1.1 | 審査 N-1 の答え。`required: false` で既定の H2 の起動が壊れない。`docker compose config`（profile なし）で内部DB 用のサービスと依存が出ないことを確かめる |
| DD-5 | 文書は 3 つの置き場に書く。(1) `README.md`: 内部DB の節に、種類（H2 が既定・PostgreSQL は profile で選ぶ）と、`docs/internal-db.md` への案内。(2) `docs/internal-db.md`: PostgreSQL の節（接続先の設定・compose の選び方・対応する版は 18 系だけ・既知の制約・バックアップと戻し・H2 だけの運用の明記・検索の Unicode の端の差）。(3) `docs/operations.md`・`docs/configuration.md`・`docs/deployment.md`・`docs/verification.md`: 該当する項に PostgreSQL のときの扱いと、内部DB の PostgreSQL の結合テストの流し方（`internalDbPostgresTest`・CI の手動の入力 `internal_db_postgres`）を足す。文書ごとのチェックリスト（FR4.4 の H2 専用の 5 項目・FR4.5 の 3 項目）は、Code Generation の計画に書く。 | NFR2.4・NFR3.1 | FR4.4・FR4.5。審査 N-5 の答え（数えられる形にする） |
| DD-6 | バックアップと戻し（FR4.4）の手順: アプリを止めてから、`docker compose exec internal-db-postgres pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" > ~/.mastersmith-backup/<名前>.dump` のように、コンテナの中の環境変数（`POSTGRES_USER`・`POSTGRES_DB`）を使い、パスワードを引数に書かない（コンテナの中の接続は信頼設定のソケットまたは環境変数から）。置き場は `~/.mastersmith-backup/`（権限 700）。戻しは、アプリを止め、`pg_restore --clean --if-exists` で同じ形に戻し、件数（利用者・ロール・監査の種類ごと）を数えて一致を確かめる。複写はコミットしない。Deployment Execution で使い捨ての内部DB に対して 1 回練習する。手順の例は `$POSTGRES_PASSWORD` を書かない。 | NFR2.4・NFR2.5・NFR4.2 | `team.md` の Deployment（複写はリポジトリの外）と `project.md` の Forbidden |
| DD-7 | H2 だけの運用（FR4.4 の 5 項目: ファイルの詰め直し `DEFRAG_ALWAYS`・`docker/hikari-pool.sh` の compact・`perf/h2-shell.sh`・ボリュームの `tar` の戻し・`-Dh2.compactThreads=1`）は、`docs/internal-db.md` の該当の節の見出しの直下に「H2 のときだけ」と明記し、`docs/operations.md`・`docs/deployment.md` の同じ項にも 1 行の注意を足す。PostgreSQL のときの同等の運用は、別の節に置く。 | NFR2.4 | FR4.4 |
| DD-8 | 性能（審査 N-3 の答え）: この単位は PostgreSQL のときの性能を新しく測らない。基準として記録するのは U1 の `INTERNAL_DB_PG_BASELINE`（起動からヘルス UP までと移行の時間）だけで、Deployment Execution の起動確認とスモークテストの時間を並べるに留める。合否には使わない。`NFR2.6` の仮定（メモリ 512m）は、Deployment Execution の起動確認で `docker stats` の値を 1 回記録して確かめる。 | NFR1.1・NFR2.6 | 要件 NFR1（H2 の目標は変えない。PostgreSQL の値は基準として記録するだけ） |
| DD-9 | イメージの固定先（審査 N-4 の答え）: 内部DB 用のサービスは `compose.yaml` のイメージとして、Dependabot が**見る**固定先に入る（docker-compose の項）。`.github/dependabot.yml` の「次のイメージは…固定している」の一覧の `対象DB（mysql・mariadb・postgres）と Mailpit` の行に、内部DB 用の `postgres`（`internal-db-postgres` のサービス。対象DB の `postgres` と同じ版・ダイジェストで、上げるときは一緒に上げる）を足す。テストの定数は新しく足さず、既存の `TargetDbImages.postgres()` を内部DB の結合テストも使う（Dependabot が見ない固定先の既存の行）。`docs/` の文書にイメージの版の文字列を書かない（書くときは「`compose.yaml` を見る」とだけ書く）。 | NFR8.1・NFR3.1 | FR4.2。版を重ねて書かず、揃えるものを 2 つ（compose の 2 サービスと `TargetDbImages`）にする |
| DD-10 | ライセンスの記録（NFR8.1）: `postgres` のイメージは PostgreSQL License（寛容なライセンス）。対象DB の同じイメージを既に使っており、採用の理由（新しい種類の依存ではない・版を同じにして上げる手間を増やさない）を、この設計の記録に残す（`docs/` には書かない）。`flyway-database-postgresql` のライセンスは U1 から Build and Test に渡している。 | NFR8.1 | `team.md` の「新しい依存を足すときはライセンスを確かめる」 |
| DD-11 | 変えないものの確かめ: `docker compose config`（profile なし）の結果が、この単位の変更の前と、内部DB 用のサービス・ボリューム・依存を除いて同じであること。`backend/` と `src/test` に差分が無いこと。`.gitleaks.toml` の除外を足さないこと。コード生成の確かめに入れる。 | NFR1.1・NFR5.1・NFR6.1・NFR9.1 | `unit-of-work.md` の U4 の境界 |

## 上流との差

- NFR 要件の NFR8.1 は「内部DB 用の固定先（テストの定数・compose）を `dependabot.yml` の一覧に足す」と読めるが、テストの定数は新しく作らず既存の `TargetDbImages` を使う（DD-9）。
- NFR 要件の NFR4.1 の「アプリ側の起動の待ち。待ちの上限は設計で決める」は、アプリ側に再試行を足さず、compose の `depends_on`（健全になるまで待つ）で行う（DD-4）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`・`reviews/review-01.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（FR4・NFR1〜NFR9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`（U4 の節）
- `aidlc/spaces/default/memory/team.md`（Deployment）・`project.md`（Forbidden・Mandated）
- `compose.yaml`・`.env.example`・`.github/dependabot.yml`・`docs/internal-db.md`（読み取り）

## Assumptions & Open Questions

- [assumption] アプリの内部DB の利用者が PostgreSQL の所有者（`POSTGRES_USER`、スーパーユーザー）であることを、手元の単一インスタンスの前提として受け入れる（DD-3）。権限を分けた形は、配備先が決まるまで決めない。
- [assumption] `pg_dump`・`pg_restore` をコンテナの中で動かし、パスワードを引数に出さない形（コンテナの中の環境変数・ソケットの信頼設定）で動くことは、Code Generation の最初の確かめ（使い捨てのコンテナ）で確かめる。
