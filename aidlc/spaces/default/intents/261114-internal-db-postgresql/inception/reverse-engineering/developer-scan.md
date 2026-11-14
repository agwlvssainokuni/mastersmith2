# 開発担当のコードスキャン（Intent 261114-internal-db-postgresql）

- 対象: mastersmith2（プロジェクトのルート、単一のリポジトリ）
- 記録するコミット（head_commit）: `cb3b513a783da1257feb86e90bd6eccf749eb52c`（`git rev-parse HEAD` と一致）
- 広さ・深さ: Focused scan（内部DB に関わる所）・Standard
- 調べ方: ファイルの読み取り（`cat`・`sed`・`grep`・`ls`・`wc`・`find`）と `git rev-parse`・`git log` だけ。テスト・ビルド・`./gradlew`・`npm`・`docker` は流していない。`.env`・`.env.targetdb` は開いていない。
- 書き方: **確かめた事実**（コードの場所を添える）と、**見立て（未検証）** を分けた。見立ては必ず「見立て（未検証）」と書いた。
- 始める前に読んだもの: `aidlc/spaces/default/knowledge/aidlc-shared/` の 10 ファイル（`study-internal-db-postgresql.md` を最初に、ほかの checklist-*.md・ops-notes.md）。前のコード知識ベース `aidlc/spaces/default/codekb/mastersmith2/` は、`reverse-engineering-timestamp.md` と `component-inventory.md` の見出し（部品 ID）だけを照らすために読んだ。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**（依頼のスナップショットの範囲の中だけ）:
  - `backend/src/main/resources/application.yaml`（内部DB・JPA・Flyway・ログの節を全文）
  - `backend/src/main/resources/db/migration/`（V1〜V14 の 14 本を全文）
  - `backend/src/main/java/cherry/mastersmith/common/persistence/`（`RowLockFailures`・`RowLockAttempt`・`RowLockUnavailableException`）
  - `backend/src/main/java/cherry/mastersmith/common/health/TimeBoundedDbHealthIndicator.java`
  - `backend/src/main/java/cherry/mastersmith/common/error/web/UniqueViolations.java`（と `GlobalExceptionHandler` のログの分岐 220〜245 行）
  - `backend/src/main/java/cherry/mastersmith/role/store/RoleStore.java`・`RoleStoreClassifier.java`
  - `backend/src/main/java/cherry/mastersmith/group/store/StoreFailureClassifier.java`
  - `backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java`
  - `backend/src/main/java/cherry/mastersmith/auth/repository/RefreshTokenRepository.java`（問い合わせの部分）
  - `backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java`・`UserRowLockRepository.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/repository/`（`DslPreviewRepository`・`DslAppliedRevisionRepository`）
  - `backend/src/main/java/cherry/mastersmith/invitation/repository/InvitationRepository.java`（ネイティブの問い合わせの部分）
  - `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java`・`invitation/service/InvitationService.java`・`auth/service/LoginService.java`・`useradmin/service/UserAdminService.java` の、違反と排他の失敗を受ける所（該当のメソッドだけ）
  - `backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java`
  - `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`
  - `backend/build.gradle.kts`（1〜240 行。依存・テストのタスク・`-PitTests`・`-PitForks`・JaCoCo）
  - `Dockerfile`
  - `compose.yaml`（`app` と `targetdb-*` のサービス・ボリューム）
  - `.github/workflows/ci.yml`・`.github/dependabot.yml`
  - `docs/internal-db.md`（「バックアップと戻し方」「複写を数える」の前半と「詰め直し」の節。約 90 行／196 行）
  - `.env.example`（内部DB の節）
  - `perf/h2-shell.sh`・`perf/lib/perf-db-url.sh`
- **Skimmed only**（`grep`・一部の行・数えだけ）:
  - `backend/src/main/java/cherry/mastersmith/` のほかのパッケージ（エンティティの注釈・JPQL・`order by`・`ilike` を `grep`。`targetdb`・`businessdata` は対象DB のため内部DB の観点では外した）
  - `backend/src/test/java/cherry/mastersmith/`（結合テストの数え、H2 に固有の SQL・`INFORMATION_SCHEMA` の `grep`、`targetdb/testsupport/PostgresTestDatabase.java`・`ContainerRuntimeCheck.java` の要所）
  - `backend/src/test/resources/`（一覧と `META-INF/spring.factories`・`junit-platform.properties`）
  - `backend/gradle.lockfile`（H2・PostgreSQL・Hibernate・Flyway・HikariCP・Testcontainers の行の `grep`）
  - `docker/`（`hikari-pool.sh`・`perf/compose.yaml` の `grep`、一覧）
  - `docs/operations.md`・`docs/configuration.md`・`docs/deployment.md`・`docs/verification.md`（内部DB・H2 の `grep`）
  - `perf/`（`perf/README.md`・`dsl-mixed-memory.sh`・`dsl-timing.sh` の H2 の `grep`）
  - `frontend/playwright.config.ts`（H2・内部DB の行の `grep`）
  - スナップショットの範囲の外で `grep` だけしたもの: `gradle/libs.versions.toml`（DB の部品の名前）。深く読んだ扱いにしない。
- **Left out**（開いていない）:
  - "What to Skip" に当たるもの: `backend/build/`・`build/`・`.gradle/`・`.idea/`・`node_modules/`・`vendor/`（`make-you-chic-ui`・`java-mustache-processor`）
  - `.gitignore` が外すもの: `.env`・`.env.targetdb`（秘密情報。開かない）・`reference/`・`data/`
  - AI-DLC 自身のもの: `.claude/`・`aidlc/`（上の知識の置き場と前の知識ベースの参照を除く）・`aidlc.settings*.json`
  - 今回の範囲の外として開かなかったもの: `frontend/`（`playwright.config.ts` を除く）・`scripts/`（`docs/internal-db.md` に H2 の jar で複写を数える `scripts/count-db-copy.mjs` があると書かれているだけ確かめた）・`config/`・`settings.gradle.kts`

### Packages Found

内部DB に触れるパッケージだけを挙げる（`backend/src/main/java/cherry/mastersmith/` の下。部品 ID は前の知識ベースの `component-inventory.md` の見出しに合わせた）。

- `common.persistence`（common-persistence）— Utility — Java — 行の排他の失敗の判定とログ（`RowLockFailures`）、排他の結果の型（`RowLockAttempt`）。auth・user・invitation・role・group・useradmin が使う
- `common.error.web`（common-error）— Middleware — Java — `GlobalExceptionHandler` が想定外の例外の連なりに排他の失敗・一意の違反があるとき、例外の文を出さずクラスの名前だけをログに出す（`UniqueViolations`）
- `common.health`（common-health）— Configuration — Java — 内部DB に `SELECT 1` を1回流す制限時間付きのヘルスチェック（`TimeBoundedDbHealthIndicator`）
- `auth.repository`（auth）— Repository — Java — ロックの状態（`login_attempt_states`）の排他・`SKIP LOCKED`・ネイティブの `MERGE ... USING`、リフレッシュトークンの一括の無効化・削除（ネイティブの `DELETE ... FETCH FIRST`）
- `user.repository`（user）— Repository — Java — 利用者の表。ネイティブの `SELECT ... FROM FINAL TABLE (UPDATE ...)`、`ilike ... escape '\'` の検索、管理者の行の排他（`UserRowLockRepository`）
- `invitation.repository`・`invitation.lock`（invitation）— Repository — Java — 招待の表。ネイティブの `DELETE ... FETCH FIRST`、行の排他（EntityManager を直接使う `InvitationLockQueriesImpl`）
- `dslmanage.repository`（dslmanage）— Repository — Java — DSL のプレビュー（H2 の `MERGE INTO ... KEY`）と適用の履歴（`INSERT ... SELECT` と `CAST(? AS UUID)`）。本文は `@Lob byte[]`
- `role.store`・`role.repository`（role）— Repository — Java — ロール・権限の設定・割り当て・作業ロール。H2 の `MERGE INTO ... KEY`（作業ロールの保存）、`JdbcTemplate` のバッチ（権限の YAML の適用）、違反の区分（`RoleStoreClassifier`）
- `group.store`・`group.repository`（group）— Repository — Java — グループとメンバー。違反の区分（`StoreFailureClassifier`）
- `audit.repository`・`audit.service`（audit）— Repository／Service — Java — 監査の追記（確定の後に `REQUIRES_NEW`）
- `useradmin.service`（useradmin）— Service — Java — 利用者の管理の操作（排他の失敗で Busy を返す、リフレッシュトークンの無効化の待ちの前提が「H2 の既定、約 2 秒」）
- 対象DB の `targetdb`・`businessdata` は内部DB とは別の接続（`targetDataSource`・`targetWriteDataSource`。どちらも `defaultCandidate = false`）で、今回は変えない前提のため、接続の分け方だけを確かめた（下の「内部DB の接続の設定」）

### Build System

- **Type**: Gradle（Kotlin DSL）。成果物は実行可能 WAR（フロントエンドの `dist` を同梱）
- **Config Files**: `backend/build.gradle.kts`・`backend/gradle.lockfile`（`dependencyLocking { lockAllConfigurations() }`）・`gradle/libs.versions.toml`（範囲の外。名前の `grep` だけ）
- **Build Dependencies**（内部DB に関わるもの。`backend/build.gradle.kts` 45〜93 行）:
  - `implementation`: `spring-boot-starter-data-jpa`・`spring-boot-starter-flyway`
  - `runtimeOnly`: `h2`（内部DB）、`mysql-connector-j`・`mariadb-java-client`・`postgresql`（コメントでは「対象DB（U1）の JDBC ドライバー」）
  - `testImplementation`: `testcontainers-junit-jupiter`・`testcontainers-mysql`・`testcontainers-mariadb`・`testcontainers-postgresql`
  - **事実**: lockfile の Flyway は `org.flywaydb:flyway-core:12.4.0` だけで、`flyway-database-postgresql` は無い（`backend/gradle.lockfile` 116 行）
  - **事実**: PostgreSQL の JDBC ドライバー（`org.postgresql:postgresql:42.7.14`）は既に実行時の依存にある（同 161 行）。内部DB 用に新しく足す必要は無い
- テストのタスク（`backend/build.gradle.kts` 107〜200 行）:
  - すべての `Test` に `systemProperty("h2.compactThreads", "1")`・`systemProperty("user.timezone", "Asia/Tokyo")`・`java.net.preferIPv4Stack=true`
  - `test` は `*Test`、`integrationTest` は `*IT`（説明文は「Spring と組み込みの H2 を起動する」）
  - `-PitTests=<形>`: 当たる `*IT` だけに絞り、当たらなければ失敗。絞ったときは `mastersmith.requireContainerRuntime=true`（コンテナの実行環境が無ければ対象DB のテストを飛ばさず失敗）
  - `-PitForks=<N>`: `maxParallelForks = N`
  - JaCoCo は `test.exec` と `integrationTest.exec` を合わせて判定（行 80%・分岐 70%、パッケージごとにも）

### Frameworks & Libraries

内部DB に関わるもの（版は `backend/gradle.lockfile` の値）:

- Spring Boot — 4.1.2 — 全体の枠組み（`spring-boot-flyway`・`spring-boot-hibernate` も 4.1.2）
- Hibernate ORM — 7.4.13.Final — JPA の実装。方言は設定に無く、接続から自動で決まる（`spring.jpa.properties` に `hibernate.dialect` は無い）
- Flyway — 12.4.0（`flyway-core` だけ）— スキーマの移行。`classpath:db/migration`、`validate-on-migrate: true`
- HikariCP — 7.0.2 — 内部DB の接続プール（`mastersmith-db`）
- H2 — 2.4.240 — 内部DB（組み込み・ファイル保存）
- PostgreSQL JDBC — 42.7.14 — 今は対象DB の読み書きだけに使う
- Testcontainers — 2.0.5 — 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストだけに使う
- コンテナのイメージ（版とダイジェストで固定）: `postgres:18.6@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42`（`compose.yaml` 151 行の見本の対象DB・`docker/perf/compose.yaml` 69 行・`TargetDbImages.POSTGRES_DIGEST`）。内部DB 用の PostgreSQL のイメージの固定先は、まだどこにも無い

### 内部DB の接続の設定（事実）

- `application.yaml` 190〜212 行:
  - `spring.datasource.url: ${MASTERSMITH_DB_URL:jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE}`、`username: ${MASTERSMITH_DB_USERNAME:sa}`、`password: ${MASTERSMITH_DB_PASSWORD:}`。`driver-class-name` は置かず、URL から決まる
  - `hikari`: `pool-name: mastersmith-db`・`maximum-pool-size: ${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}`・`connection-timeout: 5000`・`register-mbeans: true`・`allow-pool-suspension: true`（後の2つは H2 の詰め直しの道具 `docker/hikari-pool.sh` のため）。`connection-init-sql` は無い
- `spring.jpa`: `open-in-view: false`・`hibernate.ddl-auto: validate`・`jakarta.persistence.query.timeout: 10000`（213〜221 行）
- `spring.flyway`: `enabled: true`・`locations: classpath:db/migration`・`validate-on-migrate: true`（222〜226 行）。DB の種類ごとの置き場（`{vendor}`）は使っていない
- `spring.h2.console.enabled: false`
- ログ: `org.hibernate.orm.jdbc.error: OFF`（H2 の違反の文に重なった値が入るため）、`org.springframework.jdbc.core.StatementCreatorUtils: OFF`、対象DB のドライバーのロガー `org.postgresql: OFF`（375〜405 行）。**事実**: `org.postgresql: OFF` は対象DB の理由で置かれているが、内部DB を PostgreSQL にしたときも同じロガーが効く
- 対象DB との分け方: `targetdb/config/TargetDataSourceConfig.java` の `targetDataSource` と `TargetWriteDataSourceConfig.java` の `targetWriteDataSource` は `defaultCandidate = false` で、型で注入される `DataSource`・Flyway・JPA・`JdbcTemplate`・ヘルスチェックは Spring Boot の自動設定の内部DB の接続だけを使う（同ファイルのクラスの説明）。設定の接頭辞も別（内部DB は `spring.datasource.*`、対象DB は `mastersmith.target-db.*`）
- JVM の設定: `Dockerfile` の ENTRYPOINT に `-Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1`、テストの JVM（`backend/build.gradle.kts` 117 行）、E2E の WAR の起動（`frontend/playwright.config.ts` 140 行）。E2E は `--spring.datasource.url=jdbc:h2:file:<一時ディレクトリ>/mastersmith`（同 147 行）
- コンテナ: `compose.yaml` の `app` はボリューム `mastersmith-data:/app/data`、`.env` を読む。内部DB 用の PostgreSQL のサービスは無い。`Dockerfile` は `/app/data` を作り、利用者 10001 で権限 700
- `.env.example` 31〜39 行の「内部DB（H2）」の節に `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME`（コメントのまま）・`MASTERSMITH_DB_PASSWORD`・`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`

### 移行（Flyway）の一覧と H2 に頼る書き方

`backend/src/main/resources/db/migration/` の 14 本（最後に変わったコミットは `102608aa`）。名前の決まりは `V<番号>__<単位>_<内容>.sql`、前進のみ（V1 のコメント）。

| 版 | 中身 | H2 に頼る書き方（事実）と PostgreSQL での見込み |
|---|---|---|
| V1 | 表なし（基準線） | なし |
| V2 | `users`（`BIGINT GENERATED BY DEFAULT AS IDENTITY`・`TIMESTAMP WITH TIME ZONE`・`BOOLEAN`・`uk_users_email`） | 見立て（未検証）: そのまま通る |
| V3 | `login_attempt_states`（CHECK・ダミーの行 8 行の INSERT）、`refresh_tokens`（`token_hash BINARY(32)`・`uk_refresh_tokens_token_hash`・索引2つ・`fk_refresh_tokens_user`（ON DELETE なし）） | **`BINARY(32)`** は PostgreSQL に無い型。`bytea` への書き換えが要る（下調べ 5.2 と同じ） |
| V4 | `audit_events`（IDENTITY・`VARCHAR`・索引1つ） | 見立て（未検証）: そのまま通る |
| V5 | `dsl_previews`（`preview_id UUID`・**`yaml_bytes BINARY LARGE OBJECT`**・CHECK 2つ・`uk_dsl_previews_preview_id`）、`dsl_applied_revisions`（IDENTITY・`UUID`・**`BINARY LARGE OBJECT`**・CHECK・一意） | **`BINARY LARGE OBJECT`** は PostgreSQL に無い。`bytea` などへの書き換えが要る（下調べ 5.2 と同じ） |
| V6 | `audit_events` に4列を `ALTER TABLE ... ADD COLUMN` | 見立て（未検証）: そのまま通る |
| V7 | `users` に `language`・`theme`・`font_size`（`DEFAULT ... NOT NULL`）・`display_name`（足して `UPDATE` で埋めて `ALTER COLUMN ... SET NOT NULL`）、`audit_events` に2列 | 見立て（未検証）: そのまま通る。コメントの「H2 は DDL を巻き戻せない」は PostgreSQL には当たらない（DDL も巻き戻せる） |
| V8 | `invitations`（`token_hash BINARY(32)`・**生成列 `pending_email VARCHAR(254) GENERATED ALWAYS AS (CASE ... END)`**・`uk_invitations_pending_email UNIQUE (pending_email)`・CHECK 2つ・外部キー2つ（ON DELETE なし）） | **`BINARY(32)`** と **生成列**。見立て（未検証）: PostgreSQL の生成列は `STORED` の指定が要り、PostgreSQL 18 では指定が無いと仮想の生成列になり一意の制約を付けられない。下調べ 5.2 の「条件つきの一意の索引に置き換え、名前を `uk_invitations_pending_email` にそろえる」案と合う |
| V9 | `users.suspended BOOLEAN DEFAULT FALSE NOT NULL` | 見立て（未検証）: そのまま通る |
| V10 | `groups`・`group_members`（`pk_group_members`）、**`fk_group_members_group ... ON DELETE RESTRICT`**、`fk_group_members_user`（ON DELETE なし）、索引、`audit_events` に3列（`detail VARCHAR(16384)`） | **`ON DELETE RESTRICT`**（下調べ 2節の 23001）。表の名前 `groups` は、見立て（未検証）: PostgreSQL では予約語でないため引用符なしで作れる |
| V11 | `roles`（`uk_roles_name_key`）、`permission_settings`（`pk_permission_settings` は `VARCHAR(256)` ×3 と `role_id` の組、CHECK 4つ）、**`fk_permission_settings_role ... ON DELETE RESTRICT`** | **`ON DELETE RESTRICT`** |
| V12 | `user_role_assignments`・`group_role_assignments`・`work_role_selections`。**`ON DELETE RESTRICT` の外部キーが5つ**（`fk_user_role_assignments_role`・`fk_user_role_assignments_user`・`fk_group_role_assignments_role`・`fk_group_role_assignments_group`・`fk_work_role_selections_user`）、索引2つ | **`ON DELETE RESTRICT`** |
| V13 | `audit_events` に3列 | 見立て（未検証）: そのまま通る |
| V14 | `users.display_time_zone VARCHAR(64)` | 見立て（未検証）: そのまま通る |

- **事実**: `ON DELETE RESTRICT` の外部キーは V10〜V12 の合わせて 7 つ。ON DELETE の指定が無い外部キーは `fk_refresh_tokens_user`・`fk_invitations_invited_by`・`fk_invitations_completed_user`・`fk_group_members_user` の 4 つ。
- **事実**: アプリが親の行を消す経路は、ロールの削除（`RoleStore.java` 225 行の `entityManager.remove(role)`）・設定の行の削除（同 255 行）・グループの削除（`GroupStore.java` 149 行）。利用者の行を消す経路は `src/main` に無い（`grep` で `User` の `remove`・`delete` が無いことを確かめた）。したがって 23001 に当たりうるのは、ロール・グループの削除が問う口と排他をすり抜けたときの最後の守りだけ。
- **事実**: 制約と索引の名前はすべて移行で明示している（`uk_*`・`pk_*`・`fk_*`・`ck_*`・`ix_*`）。主キーも `pk_*` の名前を付けているのは V10〜V12 の組の主キーだけで、V2〜V5・V8・V10・V11 の単一の列の主キー（`... PRIMARY KEY`）は名前なし。
- **事実**: 移行は1つの置き場（`classpath:db/migration`）で、DB の種類ごとの書き分けは無い。適用済みの移行は書き換えない決まり（V1 のコメント、`validate-on-migrate: true`）のため、H2 の既存の内部DB の版の記録（`flyway_schema_history` のチェックサム）を保ったまま PostgreSQL 用の書き方を足すには、置き場を分ける形（下調べ 5.2 の `db/migration/{vendor}`）が要る。見立て（未検証）: H2 用の 14 本を動かすと、既存の H2 の内部DB でチェックサムは変わらないが置き場の設定が変わるため、Flyway の `locations` を DB の種類で切り替える形になる。

### APIs Discovered

- HTTP の API — 今回の Intent では形を変えない前提。内部DB の種類で応答が変わる所は、下の違反・排他の区分（409・503 などの code）だけ
- 内部DB の DB アクセスの口（`src/main`、`targetdb`・`businessdata` を除く）:
  - エンティティ 14（`@Entity`）: `User`・`RefreshToken`・`LoginAttemptState`・`AuditEvent`・`Invitation`・`Group`・`GroupMembership`・`Role`・`PermissionSetting`・`UserRoleAssignment`・`GroupRoleAssignment`・`WorkRoleSelection`・`DslPreviewRecord`・`DslAppliedRevisionRecord`
  - ネイティブの SQL 8 か所（下の表）。ほかは JPQL・Spring Data の導出の問い合わせ
  - `JdbcTemplate` は `RoleStore` の権限の YAML の適用だけ（同じ接続、文の上限 10 秒）

| 場所 | SQL（要点） | H2 に固有か（事実）／PostgreSQL での見込み |
|---|---|---|
| `user/repository/UserRepository.java` 79〜85 行 `savePreferences` | `SELECT ... FROM FINAL TABLE (UPDATE users SET ... CASE WHEN :change = TRUE THEN CAST(:zone AS VARCHAR(64)) ... WHERE user_id = :userId)` | **H2 に固有**（説明文に「H2 の `FINAL TABLE (UPDATE ...)`」）。PostgreSQL には `FINAL TABLE` が無い。見立て（未検証）: `UPDATE ... RETURNING` への書き換えが要る。**下調べの記録の 5.2 の一覧に無い** |
| `role/store/RoleStore.java` 103〜105 行 `MERGE_SELECTION` | `MERGE INTO work_role_selections (...) KEY (user_id) VALUES (...)` | **H2 に固有**（下調べ 5.2 と同じ）。見立て（未検証）: `INSERT ... ON CONFLICT (user_id) DO UPDATE` |
| `dslmanage/repository/DslPreviewRepository.java` 38〜42 行 `PLACE_SQL` | `MERGE INTO dsl_previews (...) KEY (preview_slot) VALUES (1, CAST(?1 AS UUID), ?2, ...)` | **H2 に固有**（下調べ 5.2 と同じ） |
| `auth/repository/LoginAttemptStateRepository.java` 165 行 `createIfAbsent` | `MERGE INTO login_attempt_states t USING (VALUES (CAST(?1 AS BIGINT))) s(id) ON ... WHEN NOT MATCHED THEN INSERT ...` | 標準の書き方。見立て（未検証）: PostgreSQL 15 以上で通る（下調べ 5.2 と同じ）。同時の追加で一意の違反になりうるが、呼び出し元の `LoginService.createRow`（156〜162 行）がトランザクションの外で `DataIntegrityViolationException` を受けて進む（事実） |
| `dslmanage/repository/DslAppliedRevisionRepository.java` 36〜41 行 `COPY_SQL` | `INSERT ... SELECT CAST(?1 AS UUID), p.yaml_bytes, ..., CAST(?4 AS TIMESTAMP WITH TIME ZONE) FROM dsl_previews p WHERE p.preview_id = CAST(?2 AS UUID)` | 見立て（未検証）: PostgreSQL でも通る型の書き方（下調べ 5.2 は「H2 専用の SQL」の項に挙げているが、`CAST(... AS UUID)`・`TIMESTAMP WITH TIME ZONE` は PostgreSQL にもある） |
| `auth/repository/RefreshTokenRepository.java` 72〜77 行 `deleteExpiredBefore` | `DELETE ... WHERE token_id IN (SELECT ... ORDER BY token_id FETCH FIRST :limit ROWS ONLY)` | 見立て（未検証）: PostgreSQL でも通る（`FETCH FIRST` に束縛の引数を使える） |
| `invitation/repository/InvitationRepository.java` 152〜157 行・168〜173 行 | 同じ形の `DELETE ... FETCH FIRST :limit ROWS ONLY` ×2 | 同上 |
| `role/store/RoleStore.java` の JDBC のバッチ | `DELETE FROM permission_settings WHERE role_id = ?`・`INSERT INTO permission_settings (...) VALUES (?, ...)`・`UPDATE roles SET updated_at = ? ...` | 標準。値は `statement.setObject(i + 1, row[i])`（`OffsetDateTime`・`Boolean`・`null` を含む）。見立て（未検証）: PostgreSQL のドライバーでも `null` と `OffsetDateTime` を受ける |

- **事実（エンティティの写し方）**: `DslPreviewRecord.yamlBytes`・`DslAppliedRevisionRecord.yamlBytes` は `@Lob byte[]`（各 46〜51 行）。`RefreshToken.tokenHash`・`Invitation.tokenHash` は `@JdbcTypeCode(SqlTypes.BINARY) @Column(length = 32) byte[]`。主キーは `GenerationType.IDENTITY`（User・Role・Group・Invitation・RefreshToken・AuditEvent）。日時は `Instant`、`preview_id`・`revision_id` は `UUID`。
- 見立て（未検証）: Hibernate の PostgreSQL の方言は `@Lob byte[]` を `oid`（大きなオブジェクト）として扱う。`ddl-auto: validate` のため、移行で `bytea` にすると起動時の検証で型の食い違いになりうる。`oid` にすると、DSL の履歴を消したとき（`DslAppliedRevisionRepository.deleteOlderThanNewest`）に大きなオブジェクトが残る。どちらにするかは設計で決め、起動して確かめる必要がある。**下調べの記録に無い**。
- **事実（JPQL で DB の差が出うる所）**: `UserRepository` の利用者の検索（178〜196 行）は `ilike :pattern escape '\'`。`order by` はすべて数・日時・ID の列（`grep` で確かめた。文字列の列の並べ替えは無い）。見立て（未検証）: `ilike` は Hibernate が方言ごとに訳す（PostgreSQL は `ILIKE`）。日本語・全角の大文字と小文字の扱いが H2 と変わりうる。

### 違反と排他の扱い（事実と、下調べとの照合）

- `RowLockFailures.isLockFailure`（`common/persistence/RowLockFailures.java`）: 連なりに `jakarta.persistence.LockTimeoutException`・`PessimisticLockException`・`jakarta.persistence.QueryTimeoutException` があるか、`SQLException` の誤りの番号が 50200・40001、または SQLState が `HYT00`・`40001` なら真。PostgreSQL の 55P03（待ち切れ）・40P01（行き詰まり）は一覧に無く、型で当たる（下調べ 3節の記述と合う）。`org.hibernate.QueryTimeoutException`（57014）は見ていない（下調べ 3節の最後の記述と合う）。
- `RoleStoreClassifier`・`StoreFailureClassifier`: まず `isLockFailure`、次に SQLState 23505 なら一意、23503・23506 なら外部キーとし、Hibernate の制約の名前と `SQLException` の文を大文字にして既知の名前（`UK_ROLES_NAME_KEY`・`PK_*`・`FK_*`、主キーは表の名前でも）と照らす。どれにも当たらなければ UNEXPECTED（`RoleStoreUnexpectedException`・`GroupStoreUnexpectedException` を投げる）。**23001 は外部キーの一覧に無い**ため、PostgreSQL の `ON DELETE RESTRICT` の違反は名前が文にあっても UNEXPECTED になる（下調べ 2節の結論とコードが合う）。
- `UserAccountService.isEmailUniqueViolation`（522〜530 行）・`InvitationService.isPendingEmailViolation`（220〜228 行）: 連なりの最初の Hibernate の `ConstraintViolationException` の制約の名前を大文字にして `uk_users_email`・`uk_invitations_pending_email` の定数と照らす（下調べ 2節の記述と合う）。招待中の判定は、V8 の生成列を置き換えたときに同じ名前の索引か制約にしないと当たらなくなる。
- `UniqueViolations.isUniqueViolation`（`common/error/web/`）: `DataIntegrityViolationException`・Hibernate の `ConstraintViolationException` の型か、SQLState 23505。`GlobalExceptionHandler` 227〜235 行で、当たれば例外の文を出さずにクラスの名前だけを ERROR に出す（下調べ 2節の「型と 23505 で見る」と合う）。見立て（未検証）: PostgreSQL の 23001・23503・23514 も Hibernate が `ConstraintViolationException` にするため、型で当たり、値は出ない。
- 待ちの上限: 行の排他は問い合わせのヒント `jakarta.persistence.lock.timeout` = 3,000 ms（`LoginAttemptStateRepository`・`UserRowLockRepository`・`InvitationLockQueriesImpl`・`RoleStore`・`GroupStore` の `LOCK_TIMEOUT_MILLIS`）。一意の鍵・主キーの書き込みの待ちと、リフレッシュトークンの一括の無効化の待ちは「H2 の既定（約 2 秒）」に頼ると、コードの説明文に書かれている（`RoleStore.java` 52〜53 行・`GroupStore.java` 41 行・`UserAdminService.java` 311〜313 行）。PostgreSQL の接続ごとの `lock_timeout` の設定は、今のコードと設定のどこにも無い（`connection-init-sql` も URL の `options` も無い）。下調べ 5.1 の 1 の手直しが要るという結論とコードが合う。
- `SKIP LOCKED`: `LoginAttemptStateRepository.lockDummyForUpdate` がヒント `-2`（`LockOptions.SKIP_LOCKED`）で使う（下調べ 3節で PostgreSQL でも動くと確かめ済み）。
- 排他の順: `UserRowLockRepository.lockAdminRowsAndTarget`（78〜83 行）は `select u.userId ... order by u.userId` を `PESSIMISTIC_WRITE` で読み、説明文は「H2 は主キーの走査の順に1行ずつ排他するため ... 行き詰まらない」とする。見立て（未検証）: PostgreSQL では並べ替えの後に行を排他するため順は保たれる見込みだが、行き詰まりにならないことは結合テストで確かめる必要がある（下調べ 5.3 の「排他の後に数える設計」と同じ扱い）。
- 失敗の後のトランザクション: 下調べ 4節の表のうち、`UserAdminService`（Busy で巻き戻しの印・DB に触れずに返す）、`UserAccountService.createUser`（違反を受けて `setRollbackOnly` して返す）、`InvitationService.issueWithOneRetry`（`TransactionTemplate` の外で違反を受け、読み取り専用の別のトランザクションで勝った側を読む）、`LoginService.createRow`（トランザクションの外で受ける）をコードで確かめ、記述と合った。ほかの行（`RoleStore`・`GroupStore` の呼び出し元のサービス、監査、ヘルスチェック）は今回は確かめていない（下調べの記述のまま）。

### H2 だけの運用（事実）

- 詰め直し: 接続先の `;DEFRAG_ALWAYS=TRUE`（`application.yaml` 191 行の既定）と、HikariCP の JMX の一時停止を使う道具 `docker/hikari-pool.sh`（`status`・`compact`・`resume`）と `docker/jmx/HikariPoolControl.java`。`application.yaml` の `register-mbeans`・`allow-pool-suspension` はこの道具のため（202〜212 行のコメント）。手順は `docs/internal-db.md` の「内部DBのファイルの詰め直し」。
- `-Dh2.compactThreads=1`: `Dockerfile` の ENTRYPOINT・テストの JVM・E2E の WAR の起動の3か所（`project.md` の Tech Stack と `team.md` の Deployment が「そろえる」と決めている）。
- バックアップと戻し: アプリを止めてボリューム `mastersmith_mastersmith-data` を `tar` で `~/.mastersmith-backup/` に複写し、戻すときは展開する（`docs/internal-db.md` の「内部DBのバックアップと戻し方」。`docs/deployment.md` 33〜44 行・`docs/operations.md` 130〜157 行が参照する）。`.gitignore` に `mastersmith-data-*.tgz`・`data/`・`backend/data/`。
- 複写を数える: `scripts/count-db-copy.mjs`（Gradle のキャッシュの H2 の jar で `ACCESS_MODE_DATA=r` で開く）と、`docs/internal-db.md` の `CountRows`（`INFORMATION_SCHEMA.COLUMNS` で列を確かめる）。`docs/operations.md` 130〜140 行の監査の読み方も H2 の道具で複写を開く。
- 負荷の試験: `perf/h2-shell.sh`（H2 の jar で `org.h2.tools.Shell` を動かし、使い捨ての環境の内部DB に SQL を流す。試験用の利用者の追加に使う。`perf/README.md` 88〜97 行・290 行）、`perf/lib/perf-db-url.sh`（`PERF_DB_URL` は `jdbc:h2:file:/app/data/mastersmith` で始まる形だけを受け付ける）、`perf/dsl-mixed-memory.sh`（`h2-shell.sh --read-only`）、`docker/perf/compose.yaml`（ボリューム `perf-data:/app/data`）。
- 文書: `docs/configuration.md` 14〜18 行の `MASTERSMITH_DB_*` の説明、`docs/internal-db.md` の全体、`docs/verification.md` 21 行（結合テストは「Spring と組み込みの H2 を起動する」）。
- 単一のインスタンスの前提: `role/service/RoleTransferSlot.java` 30 行の説明「単一のインスタンスの前提（組み込みの H2）のため、入口はアプリの中の1つで足りる」。見立て（未検証）: 内部DB を PostgreSQL にしても、アプリのインスタンスが1つなら前提は変わらない。複数のインスタンスにするなら別の論点になる。

### Test Coverage

- **Test Directories**: `backend/src/test/java/cherry/mastersmith/`（機能ごとのパッケージ。`*Test` 312 本・`*IT` 278 本を数えた）、`backend/src/test/resources/`（`META-INF/spring.factories`・`junit-platform.properties`・メールのテンプレート・画面の見本）
- **Test Frameworks**: JUnit 5・Spring Boot Test・jqwik・ArchUnit・Testcontainers 2.0.5（対象DB だけ）・SubEtha SMTP
- **Coverage Config**: あり（JaCoCo。`backend/build.gradle.kts` 205〜240 行。`test.exec` と `integrationTest.exec` を合わせる）
- 内部DB に頼るテスト（事実。数えた結果）:
  - `*IT` 278 本のうち 275 本が組み込みの H2 の内部DB を起動する（`TestDatabase.register`・`TestDatabase.url`・`spring.datasource.url`、またはそれを使う `BusinessDataTestApp`・`InvitationApps`・`InvitationTestProperties`・抽象の親クラス経由）。起動しないのは `mail/transport/` の3本だけ。
  - 機能ごとの `*IT` の数: role 48・businessdata 47・user 25・auth 21・invitation 21・group 18・audit 16・targetdb 14・useradmin 13・dslmanage 12・common 11・navigation 9・access 6・config 6・mail 6・appearance 2・dsl 2。
  - `@TempDir` を使う `*IT` は 249 本（テストのクラスごとに一時ディレクトリの H2 のファイルを作る形）。
  - `TestDatabase`（`common/testsupport/TestDatabase.java`）は `jdbc:h2:file:<dir>/mastersmith` を返し、`spring.datasource.url` だけを差し替える（利用者・パスワードは既定の `sa`・空のまま。`DEFRAG_ALWAYS` は付けない）。
- H2 に固有の書き方を持つテスト（事実。`grep` で見つけたもの）:
  - `INFORMATION_SCHEMA` を読むもの 12 ファイル。うち `INFORMATION_SCHEMA.SESSIONS`（実行中の文で待ち合わせる）を使うのは `auth/testsupport/H2SessionWaits.java`・`TestLoginAttemptBarrier.java`・`invitation/testsupport/TestInvitationBarrier.java`・`useradmin/testsupport/TestUserAdminBarrier.java`・`auth/repository/LoginAttemptStateRepositoryIT.java`・`user/repository/UserRowLockRepositoryIT.java`。表・列・制約の名前を大文字で読むスキーマの確かめ（`audit/domain/AuditMigrationCompatibilityIT.java`・`AuditSchemaIT`・`role/RoleSchemaIT.java`・`UserSchemaIT` など）
  - H2 の `MERGE INTO ... KEY` を使う準備の部品: `role/testsupport/RoleFixtures.java` 204 行・`useradmin/testsupport/UserAdminFixtures.java` 138 行
  - H2 の Java のソースのトリガー（`CREATE TRIGGER ... AS $$ org.h2.api.Trigger ... $$`）: `role/testsupport/FailingSettingTrigger.java`
  - H2 の関数: `DATEADD(...)`（`invitation/testsupport/InvitedPersonIdsIT.java`・`invitation/web/InvitationAdminApiIT.java`・`invitation/service/InvitationSendResultIT.java`）、`RANDOM_UUID()` と `X'00'`（`dslmanage/repository/DslManageRepositoryIT.java` 149 行）
  - 接続先の URL を直接作る: `audit/domain/AuditMigrationCompatibilityIT.java` 83 行（`jdbc:h2:file:`）ほか、移行の互換を確かめる `*MigrationCompatibilityIT`（audit・role・user の `DisplayTimeZoneMigrationCompatibilityIT`）
  - 既定の接続先の文字列を確かめる: `common/db/DatabasePersistenceIT.java` 70 行（`jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE` と一致すること）と 74 行（`spring.h2.console.enabled` が false）
  - H2 の詰め直しだけを確かめる: `config/H2CompactionByPoolSuspensionIT.java`・`config/H2DefragOnCloseTest.java`・`config/H2CompactThreadsTest.java`・`config/HikariJmxSettingsTest.java`
  - H2 の誤りの番号・文に頼る単体テストの見立て: `group/store/StoreFailureClassifierTest.java`・`role/store/RoleStoreClassificationTest.java`・`user/repository/UserRowLockRepositoryTest.java`・`invitation/lock/InvitationLockQueriesImplTest.java`・`auth/repository/LoginAttemptStateRepositoryTest.java` が H2 の名前・番号を含む（`grep` で当たっただけで中身は読んでいない）
- 対象DB の Testcontainers（事実）: `targetdb/testsupport/TargetDbImages.java` が版とダイジェストを1か所に持つ（PostgreSQL は `18.6`・`sha256:e3d24f69...`）。`PostgresTestDatabase` はクラスの静的なコンテナを1つ持ち、スキーマを作って消す形。`ContainerRuntimeCheck` がコンテナの実行環境の有無を見て、無ければ飛ばす（CI と `-PitTests` では失敗）。
- `-PitTests`・`-PitForks`: 上の Build System のとおり。CI は `./gradlew verify -PitForks="$IT_FORKS"`（既定 `IT_FORKS_DEFAULT: "3"`、手動の入力 `it_forks` は 1〜8）。

### Code Quality Indicators

- **Linting**: Java は Spotless（palantir-java-format・ライセンスヘッダー）と SpotBugs ＋ FindSecBugs（`backend/build.gradle.kts` の `spotbugsGate`）。SQL の移行の書式の検査は無い。
- **CI/CD**: `.github/workflows/ci.yml`（`develop` へのプッシュ・手動。job `verify` は `ubuntu-latest`・`timeout-minutes: 60`、`./gradlew verify -PitForks=...`。DB のサービスの定義は無く、対象DB は Testcontainers が起動する）。`release` の job は `v*` のタグのとき。`.github/dependabot.yml` の `docker-compose` の項が `compose.yaml` の `postgres:18.6` を見て、Dependabot が見ない固定先（`docker/perf/compose.yaml`・`TargetDbImages.java` など）を説明文に並べている。内部DB 用の PostgreSQL のイメージを足すときは、この一覧に足す決まり（`team.md` の Deployment）。
- **Documentation**: 内部DB の運用は `docs/internal-db.md`（H2 だけを前提）、設定は `docs/configuration.md`、配備と戻しは `docs/deployment.md`、監査ログの読み方は `docs/operations.md`、検査は `docs/verification.md`。どれも内部DB を H2 として書いている。コードの説明文（Javadoc）は H2 の振る舞い（誤りの番号・待ちの既定・排他の順・例外の文）を根拠に多く書いている。

### Technical Debt Signals

内部DB を変えたときに響く所（事実と見立てを分けた）:

1. **H2 に固有の SQL がアプリの本体に3か所**（事実）: `UserRepository.savePreferences` の `FINAL TABLE (UPDATE ...)`、`RoleStore.MERGE_SELECTION` と `DslPreviewRepository.PLACE_SQL` の `MERGE INTO ... KEY`。repository・store の層に DB の種類ごとの書き分けの仕組みは無い（対象DB の側には `SchemaQueries`・`PostgresSchemaQueries` の形がある）。
2. **移行が H2 の型と機能に頼る**（事実）: `BINARY(32)`（V3・V8）・`BINARY LARGE OBJECT`（V5）・`STORED` の無い生成列と一意の制約（V8）・`ON DELETE RESTRICT`（7 つ）。置き場は1つ。
3. **待ちの上限を H2 の既定に預けている**（事実）: 一意の鍵・主キー・一括の更新の待ちは「H2 の既定、約 2 秒」に頼ると説明文に書かれ、接続の設定で上限を決めていない。PostgreSQL の既定は上限なし（下調べ 3節）。
4. **区分の部品が SQLState の一覧に頼る**（事実）: 外部キーの違反は 23503・23506 だけ。PostgreSQL の 23001 は UNEXPECTED になる。排他の失敗の SQLState の一覧（`HYT00`・`40001`）と誤りの番号（50200・40001）は H2 の値。
5. **`@Lob byte[]` の写し方**（見立て（未検証））: PostgreSQL の方言での `oid` と `bytea` の食い違い（`ddl-auto: validate`）。
6. **Flyway の PostgreSQL の部品が無い**（事実と見立て）: lockfile には `flyway-core` だけ（事実）。見立て（未検証）: Flyway 10 以降は PostgreSQL の扱いが別の部品（`flyway-database-postgresql`）に分かれており、無いと起動時の移行で「この DB を扱えない」として止まる。新しい依存のため、ライセンスの確かめと lockfile・OSV-Scanner の対象への追加が要る（`team.md` の Code Style）。
7. **テストが H2 のファイルと H2 の機能に強く頼る**（事実）: `*IT` 275 本が組み込みの H2。待ち合わせの部品（`INFORMATION_SCHEMA.SESSIONS`）・トリガー（Java のソース）・`MERGE ... KEY`・`DATEADD`・`RANDOM_UUID()`・大文字の `INFORMATION_SCHEMA` の読み方。`team.md` の Testing Posture は「内部DB を使うテストは組み込みの H2 で行う」と決めている。
8. **H2 だけの運用の道具と文書**（事実）: `docker/hikari-pool.sh`・`docker/jmx/HikariPoolControl.java`・`perf/h2-shell.sh`・`perf/lib/perf-db-url.sh`・`scripts/count-db-copy.mjs`・`docs/internal-db.md`。`DEFRAG_ALWAYS=TRUE` と `-Dh2.compactThreads=1` の3か所。
9. **既定の接続先の文字列を確かめるテスト**（事実）: `DatabasePersistenceIT` が H2 の既定の URL と完全に一致することを確かめる。既定を変えなくても、PostgreSQL を選ぶ設定を足すと、このテストの意味（既定は H2）を保つかの判断が要る。
10. **ログの抑えの理由が H2 の文**（事実）: `org.hibernate.orm.jdbc.error: OFF` の理由は H2 の違反の文に値が入ること。見立て（未検証）: PostgreSQL の違反の文にも値が入る（下調べ 2節で `Key (email)=(...)` の形を確かめ済み）ため、抑えはそのまま要る。PostgreSQL のサーバーの側のログ（違反の文・`DETAIL`）にも値が残りうる点は、アプリの外の新しい論点。
11. **CI の時間**（事実と見立て）: 事実は CI の上限 60 分、`team.md` の線は約 42 分。見立て（未検証）: 内部DB の結合テストを PostgreSQL でも流すと、コンテナの起動とテストの数の分だけ `verify` と CI が伸びる（下調べ 5.2 と同じ）。数字は測っていない。

### 下調べの記録（study-internal-db-postgresql.md）と今のコードの照合

調べた時点のコード（`8eec0ba4`）から今（`cb3b513a`）までに、`backend/`・`Dockerfile`・`compose.yaml`・`docker/`・`.github/`・`docs/`・`perf/`・`.env.example`・`frontend/playwright.config.ts` の変更は無い（`git diff --stat 8eec0ba4 cb3b513a` が空。移行の最後の変更は `102608aa`）。

| 下調べの記述 | 今のコードとの照合 |
|---|---|
| 2節: メールアドレス・招待中の判定は制約の名前を大文字にして照らす。`UniqueViolations` は型と 23505 | 合う（`UserAccountService` 522〜530 行・`InvitationService` 220〜228 行・`UniqueViolations`） |
| 2節: `ON DELETE RESTRICT` の親の削除は 23001 で UNEXPECTED | コードの区分の一覧（23503・23506）と合う。補足: アプリに利用者の行を消す経路は無く、23001 に当たりうるのはロール・グループの削除だけ |
| 3節: `RowLockFailures` の番号と SQLState は H2 の値だけだが型で当たる。`org.hibernate.QueryTimeoutException` は見ていない | 合う（`RowLockFailures` 44〜80 行） |
| 4節: 失敗の後に同じトランザクションで SQL を続ける経路は無い | 確かめた4つの経路（`UserAdminService`・`UserAccountService.createUser`・`InvitationService`・`LoginService.createRow`）は合う。ほかは確かめていない |
| 5.1: 接続ごとの `lock_timeout` が要る。`UserAdminService.revokeAllRefreshTokens` の説明に「H2 の既定、約 2 秒」 | 合う（311〜313 行）。同じ前提は `RoleStore`（52〜53 行）・`GroupStore`（41 行）の説明文にもある |
| 5.2: H2 の移行は V1〜V14 の 14 本 | 合う |
| 5.2: `BINARY LARGE OBJECT`・`BINARY(32)` → `bytea`、V8 の生成列 → 条件つきの一意の索引 | 合う（V3・V5・V8）。補足: 本文の列の型を変えるときは、エンティティの `@Lob` の写し方（上の 5）も一緒に決める必要がある |
| 5.2: `MERGE ... KEY` の2か所（`RoleStore`・`DslPreviewRepository`）、`MERGE INTO ... USING` の1か所、`DslAppliedRevisionRepository` の `CAST` | 合う。**下調べに無い H2 に固有の SQL が1つある**: `UserRepository.savePreferences` の `FINAL TABLE (UPDATE ...)`（79〜85 行。プリファレンスの保存） |
| 5.2: H2 だけの運用（`DEFRAG_ALWAYS=TRUE`・`docker/hikari-pool.sh`・ファイルの複写・`-Dh2.compactThreads=1`） | 合う。補足: ほかに `perf/h2-shell.sh`・`perf/lib/perf-db-url.sh`・`scripts/count-db-copy.mjs`・`docs/operations.md` の監査の読み方も H2 に頼る |
| 5.2: テストと検査の決まり（`team.md`）と CI の時間 | 合う。補足: テストの側にも H2 に固有の書き方が多い（上の Test Coverage） |
| （下調べに無い） | Flyway の PostgreSQL の部品が lockfile に無い（事実）。`@Lob byte[]` の写し方（見立て）。V8 の生成列の `STORED`（見立て） |

## Handoff Summary

- **Intent-relevant finding**: 内部DB の H2 への依存は、(a) 移行（`backend/src/main/resources/db/migration/` の V3・V5・V8 の `BINARY(32)`・`BINARY LARGE OBJECT`・生成列、V10〜V12 の `ON DELETE RESTRICT` 7 つ）、(b) 本体の H2 に固有の SQL 3 か所（`user/repository/UserRepository.java` 79〜85 行の `FINAL TABLE (UPDATE ...)` ― **下調べに無い**、`role/store/RoleStore.java` 103〜105 行と `dslmanage/repository/DslPreviewRepository.java` 38〜42 行の `MERGE INTO ... KEY`）、(c) 待ちの上限を H2 の既定（約 2 秒）に預けていること（`RoleStore.java` 52 行・`GroupStore.java` 41 行・`UserAdminService.java` 311 行の説明。`lock_timeout` の設定はどこにも無い）、(d) 区分の部品が 23001 を外部キーとして扱わないこと（`RoleStoreClassifier`・`StoreFailureClassifier` の `FOREIGN_KEY_VIOLATIONS`）、(e) 結合テスト 275 本が組み込みの H2 を起動し、待ち合わせ・トリガー・準備の SQL に H2 の機能を使うこと、(f) H2 だけの運用の道具と文書、に分かれる。接続の設定（`application.yaml` 190〜226 行）は環境変数で URL を差し替えられ、対象DB の接続とは `defaultCandidate = false` で分かれているため、接続の切り替えそのものは設定で済む形になっている。
- **Risks / follow-up**:
  - Flyway の PostgreSQL の部品（`flyway-database-postgresql`）が lockfile に無い（事実）。見立て（未検証）では、無いと PostgreSQL で起動時の移行が止まる。新しい依存のため、ライセンスの確かめと lockfile・OSV-Scanner への反映が要る。
  - `@Lob byte[]`（`DslPreviewRecord`・`DslAppliedRevisionRecord`）は、見立て（未検証）では PostgreSQL の方言で `oid` として検証され、移行の `bytea` と食い違いうる。本文の列の型とエンティティの写し方を一緒に決め、起動して確かめる。
  - V8 の生成列は、見立て（未検証）では PostgreSQL 18 で `STORED` が無いと一意の制約を付けられない。下調べの案（条件つきの一意の索引に `uk_invitations_pending_email` の名前を付ける）で、`InvitationService.isPendingEmailViolation` が当たるかを確かめる必要がある。
  - 適用済みの移行は書き換えない決まりのため、既存の H2 の内部DB の版の記録を保ったまま DB の種類ごとに置き場を分ける形（`locations` の切り替え）を設計で決める。H2 から PostgreSQL へのデータの移し替えを範囲に入れるかは、要件で確かめる論点（今のコードに移し替えの道具は無い）。
  - `team.md` の「内部DB を使うテストは組み込みの H2 で行う」の決まりと、テストの側の H2 に固有の書き方（`INFORMATION_SCHEMA.SESSIONS` の待ち合わせ・Java のトリガー・`MERGE ... KEY`・`DATEADD`）をどこまで PostgreSQL でも流すかで、`verify` と CI の時間（上限 60 分、線は約 42 分）が大きく変わる。数字は測っていない。
  - `DatabasePersistenceIT` の既定の URL の確かめ、`docs/internal-db.md`・`docs/configuration.md`・`.env.example`・`perf/` の H2 の前提、`-Dh2.compactThreads=1` の3か所は、PostgreSQL を選べる形にしても H2 の既定を残すなら保つ必要がある（既定を変えるかは要件の論点）。
  - 内部DB 用の PostgreSQL のコンテナのイメージを足すなら、版とダイジェストの固定先の一覧（`.github/dependabot.yml` の説明文）と、見本の対象DB の `postgres:18.6` との関係（同じイメージを使うか、別のサービスか）を決める。
  - 対象DB 用に置いた `logging.level.org.postgresql: OFF` は、内部DB のドライバーにも効く（事実）。内部DB の接続の失敗を調べる手掛かり（ドライバーのログ）も出ないことになる。
