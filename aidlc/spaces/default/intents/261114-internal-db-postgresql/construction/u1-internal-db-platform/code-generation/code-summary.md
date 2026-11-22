# コード生成の要約 — u1-internal-db-platform

内部DB の種類（H2・PostgreSQL）を接続先から判断し、PostgreSQL のときに PostgreSQL 用の移行で表を作って起動できるようにした。計画（15 手順、承認済み。Q1: A を含む）のとおりに作り、すべてのチェックを付けた。コミットはまだしていない（コード・設定・テストのコミットは提案して承認を得る）。

## 作った・変えたファイル

| 区分 | ファイル |
|---|---|
| 依存 | `gradle/libs.versions.toml`・`backend/build.gradle.kts`（`runtimeOnly(libs.flyway.database.postgresql)`）・`backend/gradle.lockfile`（`flyway-database-postgresql` の 1 行だけ） |
| PostgreSQL 用の移行 | `backend/src/main/resources/db/migration-postgresql/V1__u1_baseline.sql` 〜 `V14__u2_display_time_zone.sql`（14 本。H2 用と同じ名前。書き換えは `BYTEA` 4 列・`STORED` の生成列と `uk_invitations_pending_email`・`ON DELETE RESTRICT` を外した外部キー 7 つだけ。H2 用の V1〜V14 は変えていない） |
| 本体 | `backend/src/main/java/cherry/mastersmith/internaldb/platform/`: `InternalDbKind`・`InternalDbUrlResolution`・`InternalDbUrlResolver`・`InternalDbSettings`・`StartupFailureKind`・`StartupFailureReport`・`StartupFailureReporter`・`InternalDbStartupException`・`InternalDbPoolCustomizer`・`InternalDbFlywayCustomizer`・`InternalDbMigrationStrategy`・`InternalDbPlatformConfiguration`・`package-info` |
| エンティティ（Q1: A。U2 の先取り） | `dslmanage/domain/DslPreviewRecord`・`DslAppliedRevisionRecord`（`@Lob` を外し `@JdbcTypeCode(SqlTypes.LONG32VARBINARY)`） |
| ビルド・CI | `backend/build.gradle.kts`（`internalDbPostgresTest`・`integrationTest` のタグ除外と 0 件の失敗・`verifyPostgresMigrationsInWar`）、`build.gradle.kts`（`verifyArtifact` の段への追加）、`.github/workflows/ci.yml`（入力 `internal_db_postgres` と手順） |
| テスト | `backend/src/test/java/cherry/mastersmith/internaldb/`: `migration/`（H2 の固定・突き合わせ）、`platform/`（判断・設定・失敗の整形・プール・H2 の前の版・起動の失敗・TRACE の漏れ）、`postgres/`（PostgreSQL の結合テスト 5 クラス）、`testsupport/`（土台・補助）、境界テスト・設定の読み合わせ |

## 実装の決め

- 種類の判断は純粋関数 `InternalDbUrlResolver` の 1 か所（前方一致。大文字と小文字を区別。空白は取らない）。`dataSource` の `BeanPostProcessor`（`InternalDbPoolCustomizer`）と `InternalDbSettings` が同じ値で呼ぶ。
- `lock_timeout` は、Bean の名前が `dataSource` で種類が PostgreSQL のとき、プールの「接続のたびの文」（`SET lock_timeout = '2s'`）で入れる。H2・`targetDataSource`・`targetWriteDataSource` には何もしない。種類が PostgreSQL で Hikari でない・プールが始まっている場合は、黙って進まず `UNKNOWN` で起動を止める。`PriorityOrdered`（最優先）にして、ほかの後処理が `dataSource` を包んで判定が外れるのを避けた。
- 起動時の失敗は `StartupFailureReporter` が ENT-005 の項目だけを ERROR 1 件（`Internal DB startup failed`）で出し、cause の無い `InternalDbStartupException` で止める。例外の文を読むのは版の番号の数字を取り出す 1 か所だけ。
- 移行の時間は INFO `Internal DB migration finished`（`migrationMillis`・`migrationCount`）、種類は INFO `Internal DB kind resolved`（`internalDbKind`）で出す。
- PostgreSQL の結合テストは、専用の Gradle タスク `:backend:internalDbPostgresTest`（タグ `internal-db-postgres`）だけで流れ、`verify`・`verifyQuick`・`check` の依存に入らない。コンテナは JVM ごとに 1 つ（`TargetDbImages.postgres()`）、空のデータベースと専用の利用者は実行ごとに一意の名前で作り、終わりに消す。

## 上流との差（承認済み・審査済みの設計の文書は書き換えていない）

| # | 差 | 設計の文書 | この単位の扱い |
|---|---|---|---|
| 1 | 外の接続に履歴の表（`flyway_schema_history`）を握られた失敗（SQLState `55P03`）を `MIGRATION_FAILED`・版の番号なしに分類する。対象の表を握られた失敗は版の番号つき | NFR 設計の DD-5・security-design の failureKind の決め方は「移行の文の失敗があれば `MIGRATION_FAILED`」だけで、履歴の表の場合は `UNKNOWN`・版の番号なしになる（NFR 設計 R-01） | 計画で決め、承認済み。分類の順は「接続の失敗 → 移行の文の失敗（版つき）→ `55P03`（版なし）→ 検証の失敗 → `UNKNOWN`」（計画の本文は `55P03` を先としていたが、計画の表の決め（対象の表は版つき）と合うよう、移行の文の失敗を先にした） |
| 2 | `lock_timeout` を入れる対象は Bean の名前 `dataSource` だけ（プール名 `mastersmith-db` は使わない）。PostgreSQL なのに Hikari でなければ起動を止める | DD-1 は Bean 名・Hikari・プール名の 3 条件で、外れたら何もしない（NFR 設計 R-02） | 計画で決め、承認済み |
| 3 | `@Lob` の 2 列を U1 で直した（U2 の先取り） | 機能設計の「後の単位に渡す」は U2 | Q1: A（依頼者の決定）。U2 の計画は、直した後の形を前提にする |
| 4 | 版の番号の取り出し: Flyway 12.4.0 の移行の文の失敗の例外の文は `Failed to execute script V3__broken.sql`（機能設計・計画の想定の `Migration V...` ではない）。ファイル名の `V<数字>__` から数字だけを取る | BR5.3 の「`V<数字>__` から取る」とは合っている | 実装は BR5.3 のとおり |
| 5 | `integrationTest` の `isFailOnNoMatchingTests` を `-PitTests` のときだけ `false` にし、0 件の失敗を実行の終わりの確かめ（案内の文つき）に移した | D-8（実行の終わりの集計で判定）のとおり | 既存の挙動（打ち間違いで 0 件のまま通らない）は保っている |
| 6 | 計画に無いテストの補助 `LogCapture`・`InternalDbStartupProbe` を `testsupport` に足した | — | テストの補助だけ |

## テストの結果（実測）

colima の PC で `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して流した。

| 命令 | 結果 |
|---|---|
| `./gradlew :backend:test --tests 'cherry.mastersmith.internaldb.*'`（＋既存の `ArchitectureTest`） | 通過。単体 85 件＋土台の判定 4 件・境界 2 件を足した分も通過。失敗 0・飛ばし 0。既存の `ArchitectureTest` は新しいパッケージで落ちなかった（緩めていない） |
| `./gradlew :backend:integrationTest -PitTests='InternalDb*IT'` | 通過。6 件（H2 の前の版 1・起動の失敗 3・TRACE の漏れ 2）。閉じたポートで `CONNECTION_FAILED`・SQLState 08001・ERROR がちょうど 1 件。全ロガーと例外の連なりに接続先・利用者名・パスワードが出ない |
| `./gradlew :backend:internalDbPostgresTest` | 通過。13 件・失敗 0・飛ばし 0。5 クラスの合計約 26 秒（タスク全体 32〜40 秒） |
| `caffeinate -dims ./gradlew :backend:cleanIntegrationTest :backend:integrationTest`（既存の結合テストの全件、1 回） | BUILD SUCCESSFUL・24 分 36 秒・281 クラス・1722 件・失敗 0・エラー 0・飛ばし 10（対象DB の種類違いの飛ばしだけ。コンテナの実行環境が無い警告は 0 件）。新しい部品での失敗は無く、既存のテストの書き換えも無い。`pmset -g log` に Sleep・DarkWake は見つからなかったが、出力が少なく確かめは弱い |
| `./gradlew :backend:verifyPostgresMigrationsInWar` | 通過（正の確認だけ。ファイルを欠けさせた失敗の確認はしていない） |
| `-PitTests` が PostgreSQL のタグ付きだけ・存在しない名前に当たる | 案内の文つきで失敗する |

### PostgreSQL での確かめ（実測）

- 握られた移行は 2 秒台で切れた: 対象の表（`users`）を握られた場合、V14 の開始から失敗まで 2040〜2046 ms で `MIGRATION_FAILED`・`migrationVersion=14`・`sqlState=55P03`（握りを解くと次の起動は V14 まで通る）。履歴の表を握られた場合、2041〜2047 ms で `MIGRATION_FAILED`・`sqlState=55P03`・版の番号なし。
- PostgreSQL への起動で `ddl-auto: validate` が通った（Q1: A の直しを含む）。バイナリ 4 列は `bytea`、`SHOW lock_timeout` は 2s、`hikaricp.connections{pool=mastersmith-db}` のメーターがある。
- 基準（合否には使わない）: `startupToHealthMs` は 4078・7870・5983 ms の 3 回、`migrationMillis` は 226・637・375 ms（V1〜V14 の 14 本）。値の転記は Build and Test。

## NFR8 の記録（依存の追加）

- `org.flywaydb:flyway-database-postgresql` 12.4.0（Spring Boot 4.1.2 の BOM が管理。版は書かず `backend/gradle.lockfile` で固定）。足す前後の `:backend:dependencies`（`runtimeClasspath`）の比較で、増えたのは `flyway-database-postgresql` 12.4.0 だけで、既存の部品の版は上がっていない（`flyway-core` と同じ 12.4.0）。
- ライセンス: この部品の POM と jar には、ライセンスの記載が無かった。上流の Flyway Community は Apache License 2.0 と認識しているが、POM では確認できていない（設計の段は親 POM の記載を根拠にしていた）。`README.md` のライセンスの一覧に `flyway-core` の行は無く、`META-INF/third-party-licenses` に置く対象（jar にライセンスの文書が含まれない種類）に当たるかは、「依頼者に確かめたいこと」に挙げる。
- OSV-Scanner の対象に入ること・WAR への同梱（`verifyPostgresMigrationsInWar`）は、Build and Test の全件の `./gradlew verify` で確かめる。

## 実測の再現手順（Hikari のログ。NFR 設計 R-05）

閉じたポートの PostgreSQL の接続先で起動を試すテスト（`InternalDbStartupFailureIT`）が、全ロガーの出力に URL・利用者・パスワードが出ないことを毎回確かめる（Spring Boot 4.1.2・既定のログの水準）。この確かめで Hikari の `Starting...` の INFO 以外が出ないことを確認した。パスワード違いは `InternalDbPostgresStartupFailureIT`（コンテナ）が同じ網で確かめる。

## 流していないもの・Build and Test に渡すこと

- CI の式 `inputs.internal_db_postgres == true` の確かめ（手動の実行を 1 回。時間の記録も兼ねる）。流していない。
- `internaldb.platform` のパッケージごとのカバレッジ（行 80%・分岐 70%）は未測定。全件の `./gradlew verify`（`:backend:cleanTest :backend:cleanIntegrationTest` つき）で判定する。
- 検査の時間（基準 21分32秒の 1.1 倍・約 42 分の線との比較）と、`verifyPostgresMigrationsInWar` が欠けたファイルで失敗することの確認。
- `docs/verification.md` の書き足し（手動の実行の入力）は U4 に渡す。

## 依頼者に確かめたいこと

1. **Flyway が接続先を INFO に出す**: 起動のとき Flyway（`org.flywaydb.core.FlywayExecutor`）が `Database: jdbc:postgresql://<host>:<port>/<db> (PostgreSQL 18.6)` を INFO で 1 行出す（成功する起動でも出る。H2 でも今と同じ）。利用者名とパスワードは出ないが、接続先のホスト・ポート・データベース名が出る。`project.md` の Forbidden は「接続情報（接続先・ユーザー名・パスワード・資格情報つきの URL）をログに含めない」で、要件 NFR2 とずれうる。直すには `application.yaml` に Flyway のログの水準の設定を足す必要があり、計画は `application.yaml` に手を入れないとしていたため直していない。テストは、この 1 行を除いて接続先が出ないことを確かめている。直す・既知の制約として README に書く（U4）のどちらかを決めてほしい。
2. **`flyway-database-postgresql` のライセンス**: 上の NFR8 の記録のとおり、POM に記載が無い。上流の公開（Apache License 2.0）に基づくとして進めてよいか。
3. `@Lob` の直し（Q1: A）の H2 側は、既存の結合テストの全件が通ったことで確かめた。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`memory.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`・`functional-spec.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/reliability-design.md`・`security-design.md`・`performance-design.md`

## Assumptions & Open Questions

None.
