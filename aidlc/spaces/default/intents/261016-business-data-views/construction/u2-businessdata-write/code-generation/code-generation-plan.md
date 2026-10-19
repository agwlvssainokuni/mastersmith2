# コード生成の計画 — U2 u2-businessdata-write（Bolt B4）

U2 は、機能 `businessdata` の書き込みの側である。作成・更新・削除の3本の API（`POST /api/tables/rows`・`PATCH /api/tables/rows`・`POST /api/tables/rows/delete`、どれも「ログインだけ」）、値の検証と pattern の打ち切り、`RowSqlExecutor` の書き込みの側（事前の問い合わせ・INSERT・UPDATE・DELETE・違反の後の確かめ）、`targetdb` の名前つきの書き込みの接続と対象DB 用のトランザクションの管理者と貸す形の書き込みの口、監査の出来事（3つの種類・9つの理由の値・`AuditResult.UNKNOWN`・監査の表の3列）を作る。あわせて、U1 の定義の API のカラムの項目に検証の材料を入れる（BR14.1）。画面（frontend）には触れない。

この計画は Part 1（計画）である。依頼者が計画を承認するまで、コードを書かず、ブランチも作らない。

- コード生成の進め方（`aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md` の Deviations）: 単位ごとに U1 → U4 → U5 → U2 → U3 の順で作る。U2 は4番目の単位で、Bolt B4 にあたる。U1（B2）・U4（B3）・U5（B1）は `develop` に統合済み（`develop` の先頭は 2360768）。
- 作業ブランチ: `develop` から作る `feature/261016-business-data-views-b4`。`develop` へ squash で1コミットにして戻す（`team.md` の Way of Working、`bolt-plan.md` の B4）。
- U1 のコード生成のレビューの R-03（MySQL・MariaDB の `SET time_zone` を戻さない）は、依頼者の決定でこの計画で直す（4.5 節、D5）。R-01（traceability の負荷の目標）は Code Generation の段の承認の場で直すため、この計画の範囲に入れない。

## 1. 影響の範囲

### 1.1 新しく作るもの

| 置き場 | 作るもの（名前は設計の仮の名前。生成で変えたら generation-notes.md に記録する） |
|---|---|
| `backend/src/main/java/cherry/mastersmith/targetdb/config/` | `TargetWriteDataSourceConfig`（名前つきの `targetWriteDataSource` と `targetWriteTransactionManager`。どちらも `defaultCandidate = false`）、書き込みの設定が使えるときだけ作る条件 |
| `backend/src/main/java/cherry/mastersmith/targetdb/domain/` | `TargetWriteSession`（record。DB の種類・接続・文の上限の秒・セーブポイントの操作の口）、`WriteStep`（確定・巻き戻し・セーブポイントへ戻して確かめる）、`TargetWriteOutcome`（`Done`・`NotConfigured`・`Unavailable`・`Failed`。段・SQLState・業者の番号・例外のクラスの名前だけを持つ）、`TargetWriteStage`、`TargetWriteUnexpectedException`（メッセージも原因も持たない） |
| `backend/src/main/java/cherry/mastersmith/targetdb/service/` | `TargetWriteAccess`（口）・`JdbcTargetWriteAccess`（名前つきの管理者で `TransactionTemplate` を READ COMMITTED で使い、1本を1回だけ借りて貸す。すべての例外を外側で受ける。失敗した接続は管理者が返した後に捨てる） |
| `backend/src/main/java/cherry/mastersmith/businessdata/domain/` | `WriteOperation`・`WriteRequest`・`WriteValues`・`TypedValue`（値を伏せた toString）、`ColumnWriteRules`・`ColumnWriteRulesFactory`（純粋な関数。BR3.6 の表）、`WriteRequestValidator`（純粋な関数。時計を注入）、`DeadlinePatternMatcher`（締め切りを見る包み・256 文字ごと・値ごと 100 ms・合計 300 ms、式の取っておき）、`BusinessDataChangedEvent`、`BusinessDataAuditReason`（監査の理由の値の別の列挙）と応答の code からの写し（`switch` で場合を尽くす1か所）、`AuditDetailBuilder`（`detail` を 16,384 字に収め `keyTruncated` を付ける）、`RowWriteFailure`・`RowWriteFailureReason`・`RowWriteStage` |
| `backend/src/main/java/cherry/mastersmith/businessdata/repository/` | `RowWriteSqlBuilder`（パッケージの中だけ。SQL の文字の組み立て。文は作らない）、書き込みの結果の型（`RowInsertResult`・`RowChangeResult`・`RowPrecheckResult`・`RowInspectResult` など） |
| `backend/src/main/java/cherry/mastersmith/businessdata/service/` | `BusinessDataWriteService`（判定の順の ⑤〜⑨、口を1回呼ぶ、ログの1か所、口から戻った後に出来事を出す。内部DB のトランザクションを開かない）、結果の型 `WriteResult`（sealed interface の record） |
| `backend/src/main/java/cherry/mastersmith/businessdata/web/` | `BusinessDataWriteController`（3本、`@ApiAccess(AUTHENTICATED)`）、要求と応答の record（`RowCreateRequest`・`RowUpdateRequest`・`RowDeleteRequest`・`RowCreatedResponse`・`RowWrittenResponse`。値と主キーを伏せた toString） |
| `backend/src/main/resources/db/migration/` | `V13__u2_business_data_audit_columns.sql`（`audit_events` に `target_schema VARCHAR(255)`・`target_table VARCHAR(255)`・`work_role_id BIGINT` を、空を許し既定値・参照の制約・索引を置かずに足す。`UNKNOWN` は `result VARCHAR(16)` に入り、移行は要らない） |
| `docker/targetdb/{postgres,mysql,mariadb}/` | `03-writer-account.sh`（`mastersmith_writer`。冪等。毎回パスワードと付与を当て直す。PG は `REVOKE TEMPORARY ON DATABASE business FROM PUBLIC`） |
| `backend/src/test/java/cherry/mastersmith/` | 5節の各 Step の単体・性質ベース・結合のテストと、`businessdata/testsupport` の部品（書き込みの表と付与を作る部品、別の接続で読み直す部品、書き込みの DataSource を数える列、確定の途中で止める中継の形） |

### 1.2 変える既存のファイル

| ファイル | 変えること |
|---|---|
| `targetdb/config/TargetDbProperties.java` | `write`（ユーザー名・パスワード・`pool.maximumSize`）を足す。`QueryTimeout` に `write`（既定 5 秒）を足し、compact constructor と `new QueryTimeout(null, …)` を直す。`longest()` は読み取りの4つの目的の最大のまま（書き込みの上限を入れない。読み取りのプールの通信の待ちを書き込みの上限で変えないため）。toString は書き込みの資格情報も有無だけ |
| `targetdb/config/TargetDbSettings.java` | 書き込みの資格情報の点検（読み取りが使えて、ユーザー名とパスワードの両方があるときだけ「書き込みが使える」。片方だけなら項目の名前だけを返す） |
| `targetdb/config/TargetDataSourceConfig.java` | 書き込みの片方だけの WARN（項目の名前だけ、1回）。読み取りのプールの MySQL・MariaDB に `connectionInitSql`（U1 R-03。D5） |
| `backend/src/main/resources/application.yaml` | `mastersmith.target-db.query-timeout.write`・`write.username`・`write.password`・`write.pool.maximum-size`（`infrastructure-specification.md` 1.1 の名前） |
| `businessdata/domain/`（U1 の型） | `BusinessDataProblemTypes` に5つの code、`BusinessDataFieldErrorReason` に U2 の理由、`BusinessDataFieldError` に pattern の `message`、`ColumnValidation`（`nullable` の持ち主を U2 にする。値はすべてのカラムで入れる）、`TableAccessPolicy`（`ColumnValidation` を `ColumnWriteRules` から写して入れる）、`RowQueryStage` から `SESSION_INIT` を消す（D5） |
| `businessdata/repository/` | `RowSqlExecutor` に書き込みのメソッド（文を作るのはそのメソッドの本体だけ）、読み取りの `SET_TIME_ZONE_UTC` を消す（D5）。`RowDialect`・`PostgresRowDialect`・`MysqlFamilyRowDialect` に書き込みの形（事前の問い合わせ・確かめの1文・生成された鍵の読み方）、`needsUtcSession` を消す（D5）。`ColumnValueCodec` に書き込みの型つきのバインド（時差つきは UTC）。`RowSqlFailureClassifier` に書き込みの段の表 |
| `businessdata/service/` | `TableAccessResolver`（写しの DSL から `ColumnWriteRules` を作る材料を渡す）、`BusinessDataProblemTypeCatalog` |
| `businessdata/web/` | `BusinessDataPaths`（書き込みの2つの道）、`BusinessDataWebConfig`（4 MiB・4 MiB・64 KB の道ごとの上限）、`ColumnDefinitionResponse`（検証の材料を `NON_NULL` で省かず、すべてのカラムで返す。`required`・`nullable` は必ず値を持つ。型に当たらない項目は `null`）、`BusinessDataFieldErrors`（`message` を載せる） |
| `audit/domain/` | `AuditEventType` に3つ、`AuditFailureReason` に9つ、`AuditResult` に `UNKNOWN`、`AuditEvent` に3つの列、`AuditEventFactory` に業務データの出来事の組み立て |
| `audit/service/AuditEventListener.java` | `BusinessDataChangedEvent` の受け取り（`AFTER_COMMIT`・`fallbackExecution = true`）、`logFailure` を出来事の種類で分ける（業務データでは `detail`・主キーの値・`setCause` を外し、主キーのカラムの数・`keyTruncated`・例外のクラスの名前だけ） |
| `backend/config/spotbugs-exclude.xml` | `RowSqlExecutor` の書き込みのメソッドに `SQL_` の除外を広げる（3節の A3） |
| `.env.example`・`.env.targetdb.example` | 書き込みの4項目のコメントの行（41〜73行の対象DB の節）、`MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=`。43行の「読み取りの権限だけを勧める」を読み取りと書き込みに分けて書く |
| `perf/`（`k6/scenarios.js`・`biz/make-biz-seed.sh`・`make-biz-dsl.mjs`・`dsl-timing.sh`・`README.md`） | 書き込みの5場面と setup の前提、書き込みの表と種、書き込みのアカウントへの付与、一時の環境ファイルの書き込みのパスワード |
| `README.md` | 環境変数の表、「対象DB」の節（566行・588行の読み取りだけの記述、書き込みの API の小見出し、見本の書き込みのアカウントと既存のボリュームへの足し方、消し方、残る広さ、既知の制約） |

触れないもの: `packagesJudgedByTotal` の7パッケージの本体のソース（`audit.repository`・`common.web` を含む。1.4）、`docker/monitoring/`、`.github/workflows/ci.yml`、`compose.yaml`・`docker/perf/compose.yaml`（`env_file` が `.env.targetdb` と一時の環境ファイルの全体を渡すため、変数を足すだけで届く。既定の値も変えない）、`frontend/`、`vendor/`、配備した環境（アプリ・見本の PG・Mailpit・ボリューム・`.env`。実地は Deployment Execution）。

### 1.3 影響を受ける既存のテストと E2E（検索の結果）

変える型・表・移行の番号・API・設定の作り方を使っている既存のテストを、読み取りだけの検索（`grep`）で洗い出した（`project.md` の学び）。

| 影響の元 | 既存のテスト | 扱い |
|---|---|---|
| 移行 V13 | `audit/domain/AuditMigrationCompatibilityIT`（`CURRENT_LAST_VERSION = 12`、`MIGRATIONS_AFTER_PREVIOUS`） | 13 にし、Javadoc に B4 で V13 を足したことを書き足す。前の版（V1〜V9）の互換の確かめはそのまま |
| 監査の列 | `audit/service/AuditSecretLeakIT`（`AUDIT_EVENTS` の列を `containsExactlyInAnyOrder`） | 3列を足す（理由のコメントつき） |
| 監査の列 | `audit/domain/AuditSchemaIT`（V7・V10 の列の確かめ） | V13 の3列の確かめを足す（空を許す・既定値なし・長さ） |
| 種類・理由の値 | `audit/domain/AuditEventTest#namesFitIntoTheColumns`（すべての値が 32 字以内） | 変えずに通る（足す値は最長 30 字）。BR11.6 の確かめはこれに加えて足す |
| 種類の数 | `audit/domain/AuditRoleEventFactoryTest`（`ROLE_` の接頭辞で `hasSize(7)`） | 足す種類は `BUSINESS_` で当たらない。変えない |
| `AuditResult` | `AuditResult.` を使うテスト 12 ファイル（`AuditEventFactoryTest`・`AuditEventListenerTest`・`AuditSchemaIT` など） | `UNKNOWN` を足しても値を並べて数えるテストは見当たらない。本体で `AuditResult` を `switch` で分ける箇所は Step 11 の始めに検索して直す |
| 監査の失敗の ERROR | `audit/service/AuditEventListenerTest`・`AuditWriteFailureIT`・`GroupAuditWriteFailureIT`・`UserAdminAuditWriteFailureIT` | ほかの出来事の ERROR は変えない。そのまま通ることを確かめる |
| `QueryTimeout` の引数の数（4 → 5） | `targetdb/config/TargetDbPropertiesTest`・`TargetDbSettingsTest`・`TargetDataSourceConfigTest`、`targetdb/service/TargetSchemaReaderTest`・`JdbcTargetReadAccessTest` | 作り方を直す。`longest()` の確かめは読み取りの4つのまま |
| 書き込みの設定 | `targetdb/config/TargetDbStartupIT`・`TargetDbSecretLeakIT` | 書き込みの両方・片方・無しの場合と、資格情報の伏せ字を足す |
| 読み取りのプールの初期化（D5） | `businessdata/repository/RowDialectTest`（85〜88行の `needsUtcSession`・`SET_TIME_ZONE_UTC`）、`RowSqlFailureClassifierTest`（65〜66行の `SESSION_INIT`）、`RowSqlExecutorTest`、`targetdb/config/TargetDataSourceConfigTest` | 消した口の確かめを消し、`connectionInitSql` の確かめを `TargetDataSourceConfigTest` に足す |
| 読み取りのプールの初期化（D5） | `businessdata/web/BusinessDataTimeoutIT#stallAfterConnecting`（218〜221行。MySQL は `SET time_zone` の分を数えて2つのまとまりを通す） | 貸し出しの始めの文が無くなるため、MySQL の通す数が変わる。手元で確かめて直し、数と理由を generation-notes.md に記録する（G23 の続き） |
| 読み取りのプールの初期化（D5） | `businessdata/web/AbstractBusinessDataReadIT`（時差の確かめ） | 変えずに通ることを確かめる（UTC の読みが接続を作るときの初期化で保たれる） |
| 定義の API の検証の材料 | `businessdata/web/BusinessDataReadControllerTest`（126行 `formPart()` が null）、`businessdata/domain/TableAccessPolicyTest`（274行 `validation()` が null） | 「U2 が埋める」形に直す（BR14.1・BR14.2） |
| 定義の API の応答 | `businessdata/web/AbstractBusinessDataReadIT`（定義の応答の項目） | 検証の材料の項目が出ることを足す |
| 境界テスト | `businessdata/BusinessDataBoundaryArchitectureTest`（`onlyRowSqlExecutorCreatesStatements` の見分けの確かめの名前の一覧、`nobodyDependsOnBusinessData`、`noTransactionsOnTheReadSide`） | 3節の A1・A2。書き込みの側の決まりを足す |
| 境界テスト | `targetdb/TargetDbBoundaryArchitectureTest`・`audit/AuditBoundaryArchitectureTest`・`ArchitectureTest`・`role/RoleBoundaryArchitectureTest` | 変えずに通ることを確かめる（`TransactionTemplate.execute` は spring-tx の持ち主で更新の呼び出しに当たらない。書き込みの文は `businessdata.repository` が作る） |
| API の数 | `common/testsupport/ApiEndpointCounts`（`MIN_ENDPOINTS = 70`） | 73 にする。`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` に3本が載ることを確かめる |
| code の一覧 | `businessdata/service/BusinessDataProblemTypeCatalogTest`、`common/error/service/ProblemTypeDuplicateStartupIT` | 5つの code を足す |
| 接続の数 | `businessdata/web/BusinessDataConnectionUsageIT`、`businessdata/testsupport/CountingDataSourceConfig`（`dataSource`・`targetDataSource` だけを包む） | `targetWriteDataSource` も包み、作成・更新・削除の3行を足す |
| テストのアプリ | `businessdata/testsupport/BusinessDataTestApp`・`targetdb/testsupport/TargetDbTestDatabase`・`MysqlFamilyTestDatabase`・`PostgresTestDatabase` | 書き込みの資格情報を渡す口と、テストのクラスごとの書き込みのアカウントを作る口を足す |
| 結合テストの拡張の順 | `targetdb/testsupport/ExtensionOrderArchitectureTest` | 足す IT は今の決まりの順に従う |
| E2E | `frontend/e2e/` に `/api/tables` を使うものは無い（検索で 0 件）。`frontend/src` にも無い | E2E のファイルは変えない。統合の前に全件を流す（5.1） |
| 負荷の試験 | `perf/dsl-timing.sh`（161行で読み取りのパスワードだけを書く）、`perf/README.md`（1163行の一時の環境ファイル） | 書き込みのパスワードの生成を足す。足さないと、03 の台本が空のパスワードで止まり、perf の対象DB の初期化が必ず失敗する（基盤の設計のレビュー R-05） |

### 1.4 カバレッジの今の値と手を入れるパッケージ

| パッケージ | 今の値（行・分岐） | この Bolt で |
|---|---|---|
| `businessdata.domain`・`businessdata.repository`・`businessdata.service`・`businessdata.web` | 97.7%・90.7%、94.9%・86.0%、98.6%・93.6%、97.0%・74.3%（U1 の B2 の実測） | パッケージごとの下限の対象のまま。`businessdata.web` の分岐が下限に近いため、書き込みの controller の `switch` の場合をすべてテストで通す |
| `targetdb.config`・`targetdb.domain`・`targetdb.service` | 100.0%・100.0%、100.0%・97.1%、90.5%・92.5%（同上） | 同上 |
| `audit.domain`・`audit.service` | パッケージごとの下限の対象（`packagesJudgedByTotal` に無い） | 同上。値は Step 29 で実測して記録する |
| `audit.repository`（`packagesJudgedByTotal`） | 数える行が無い（Delivery Planning の実測） | 本体のソースに手を入れない。監査の列は `audit.domain` の `AuditEvent` に足し、`AuditEventRepository` は変えない |
| `common.web`（`packagesJudgedByTotal`） | 行 96.5%・分岐 85.2%（Delivery Planning の実測） | 本体のソースに手を入れない（道ごとの上限は `businessdata.web` の `RequestBodyLimitRoute` の Bean で足す。DSL の 10MB と同じ形） |

- 生成の途中で `packagesJudgedByTotal` のパッケージの本体のソースに手を入れる必要が出たら、生成を止めて依頼者に諮り、手を入れるならテストを足して下限を満たし、一覧から外す（`team.md`、B4 の DoD）。
- カバレッジの除外は増やさない。

## Testing Contract

（この節は 2節にあたる。下の JSON の塊は `aidlc engine testing-posture render` の出力の `## Testing Contract` の塊を、一字も変えずに写したもの。）

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB へ書き込むテスト（業務データの作成・更新・削除を範囲に入れる場合）は、アプリの対象DB への接続とテストの接続が別のため、アプリのトランザクションの巻き戻しに頼らない。3種類の対象DB のすべてで、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。書いた後の値は、アプリとは別の接続で読み直して確かめる。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E をどのブランチで統合の前に流すかは Way of Working のとおりとし、リリースの前にも手元で実行する。\n- 業務データを表示する E2E は、見本の対象DB（PostgreSQL の1種類）がコンテナで動いていることを前提にする。`./gradlew e2eTest` は始める前に対象DB に届くかを確かめ、届かなければ起動の手順を示して失敗する（Mailpit と同じ扱い。起動・停止はしない）。流れが使う行は、その流れで自分で作るか、流れの専用のスキーマに置き、見本の行は架空の値だけにする。対象DB の資格情報は Playwright の `webServer.env` に置かずプロセスの環境変数で渡し、報告の json を文字列で検索して資格情報・接続先の値が入っていないことを確かめる。Intent 261016-business-data-views で足す E2E は業務データの代表の流れ1本までとし、持ち越しの直し（make-you-chic-ui の固定先の更新・axe-core の取り込み）と既存の E2E の書き換えは本数に数えない。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- 業務データの一覧・詳細（と、範囲に入れるなら作成・更新・削除）の機能では、次のテストを必ず書く（役割・権限の機能と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - 権限ごとの操作の可否: テーブルの主権限と補助権限の組ごとに、業務データの API の未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書き、403 を確かめる利用者は要る権限だけを欠く利用者にする。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わること。\n  - 主キーとビュー: 主キーの無いテーブルとビューでは、行を1件に決められない操作（更新・削除など）を、画面で隠すだけでなくサーバー側で拒否する。主キーが複数のカラムのテーブルも確かめる。★主キーの無いテーブルで詳細を見せるか\n  - カラムの出し分け: カラムの主権限が NONE（読めない）のカラムは、画面で隠すだけでなく API の応答に含めないことを確かめる。DSL の表示の設定（`list.visible`・`detail.visible`）で隠すだけのカラムの扱いは設計の段で決め、決めた側の動作を明示したテストにする。\n  - IDOR と一括代入: スキーマ名・テーブル名・主キーの値・並べ替えの列・検索の列を差し替えた要求（適用中の DSL に無い名前、権限の無いテーブル、存在しない行）は拒否され、状態が変わらないこと。作成・更新の本文に許していないカラムを足しても反映されないこと。\n  - 403 と 404 の使い分け: 権限の無いテーブル・DSL に無いテーブル・存在しない行の応答の状態コードと code をそろえるか。★使い分け（決めた側の動作を明示したテストにする）\n  - SQL の組み立て: 識別子は適用中の DSL にある名前と照らし、値はバインド変数で渡すこと。識別子・検索の値・並べ替えに `'`・`\"`・`;`・`--`・バッククォート・改行を含めても、SQL として解釈されないこと（Code Style の SQL の組み立ての決まりを確かめる）。\n  - 3種類の対象DB の境界の値: 一覧・詳細（と書き込み）を、MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。型の違い・識別子の大文字と小文字・NULL の並びと、境界の値（最大の整数・桁の多い小数・NULL）を含める。\n  - 行の数の上限と時間切れ: 一覧のページの大きさの上限の境界（上限ちょうどは受け付け、超えると拒否）。JDBC の取り出しの上限（`setMaxRows` か問い合わせの上限）を置き、上限を超える行を内部で読まないこと。応答しない対象DB での時間切れの応答（既存の ServerSocket の方式）。\n  - 部分一致の検索: 部分一致の検索を設けるなら、検索の値の `%`・`_`・エスケープの文字が一致の条件として働かない（エスケープして渡す）こと。\n  - 画面に出す値: 業務データの値とカラムの表示名を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、一覧・詳細で文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - 道に乗る主キーの値: 画面の道に乗る主キーの値（複合の主キー、`/`・`%`・`?`・`#`・空白・引用符を含む値）をエンコードし、サーバー側でデコードの後に照らすこと。\n  - 接続の数: 業務データの API が1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT`（`NavigationConnectionUsageIT` など）と同じ形の結合テストで決定的に確かめる。\n  - 性質ベースのテスト: テーブルの主キーの有無・ビューか・主権限・補助権限から許す操作の集合を求める関数と、識別子の照合の部品は純粋な関数にし、jqwik・fast-check で性質（権限を足しても許す操作は減らない、主キーの無いテーブルに行を1件に決める操作が出ない、DSL に無い名前は拒否される、引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない など）を確かめる。\n  - 漏えい: 業務データの行の値（主キーの値を含む）と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性（外部へ送る設定を有効にした結合テスト）・エラー応答に含まれないこと。JDBC の例外のメッセージが応答にもログにも出ないこと。\n  - 監査: ★業務データの操作（読み取りを含めるか）を監査に残すか、残す中身、対象DB の確定と監査の行の一方だけが残ったときの扱い。決まった側の動作を明示したテストにする。\n  - 画面: 一覧・詳細の画面ごとのアクセシビリティ検査、権限の無いテーブルへ直接移ったときの 403 の表示、操作の可否に合わせてボタンが押せない（または出ない）こと。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06) \n- 長いテスト（verify・E2E・k6）は caffeinate -dims で包み、流す前に pmset -g assertions で眠らない状態か、流した後に pmset -g log で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。DarkWake の Thermal Emergency のスリープは caffeinate -i では止められず、role-menu の B8 で画面のテストが約 660 秒止まって時間切れになった。 (learned 2026-10-13) \n- コード生成の計画の段で、変える型・表・移行の番号・API・画面の骨組みを使っている既存のテストと E2E を検索で洗い出し、影響の範囲に入れる。role-menu では移行の最後の版を前提にしたテスト（AuditMigrationCompatibilityIT）、SQL の文の数を数えるテスト（GroupAdminListQueryCountIT）、狭い幅のはみ出し（E2E の 375px）で計画の外の失敗が起き、生成が止まった。 (learned 2026-10-13) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- k6 の場面の setup は、前の場面が残した状態（ロールの割り当てなど）に頼らず、自分で前提を作るか片付けてから始める形で書く（role-menu の Performance Validation で、workRoleRead・myPermissionsTree・roleAssignmentsRead が前の場面の割り当てで 409 ROLE_NO_CHANGE や確かめの不一致になり、割り当てを外して流し直した）。 (learned 2026-10-15) \n- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) \n- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) \n- 依頼者の決定で、E2E をブランチごとの統合の前ではなく Intent の最後（Build and Test）に1回だけ流すことにした。① と ② は統合の前に流して通し、③・④ は ./gradlew verify だけで統合した。team.md の「画面・認証に関わる変更は統合の前に E2E」との差。 (learned 2026-10-15) \n- CI の 776738e の1回目が AdminForbiddenProvider.test.tsx の1件（描画の後の効果の回数を、効果が済む前に数える形）で落ち、2回目の試行で通った。手元で10回流して再現しなかったため、team.md の「手元で再現しないものは CI の再実行で通れば進めてよい」の扱いとし、見立てを記録した（同じテストが二度落ちたら直す）。 (learned 2026-10-15) \n- dslMixed の memory.peak が 94.4%・取り直し 93.1% で線（90% 未満）を超えた。peak の時の内訳は anon 68.3%・回収できるページキャッシュ 23.7% で、依頼者の決定「持ち越す」で設定を変えず Not Met のまま記録した。物差しを anon に替えると Met になるが、目標の測り方を後から緩めることになるため選ばなかった。 (learned 2026-10-15) \n- 1回目の使い捨てのアプリのログの WARN 43 行を、内訳を読む前に環境を消したため取り直せなかった（project.md の「確かめの結果を見てから片付ける」の漏れ）。memory.peak の取り直しの回では、消す前に WARN を数える指示を最初から書いた。 (learned 2026-10-15) \n- business-data-views の Delivery Planning の実測（約 13 分）で、全体の合計で判定している7パッケージのうち下限に足りないのは common.health（行 79.2%）だけで、audit.repository は数える行が無かった（全体の合計 行 98.0%・分岐 92.6%）。 (learned 2026-10-18) \n- U1・U2・U3 で捨ての試しを先に行い、前提が崩れた点（U1 は MySQL・MariaDB の重さ、応答しない MySQL の 50 秒、bit と空間の型。U2 は perf の MySQL の OOMKilled、PG の TEMP の権限、更新できないビュー、JDK 25 で重くならない式。U3 は測り終わりの時点、T3 が代わりの案にならない）を追加の質問（F）で依頼者に確かめてから文書に反映した。 (learned 2026-10-18) \n- U2 の Q7: A で perf の MySQL 1792m を短い捨ての試しで確かめ、プールを埋めた状態で上限に張りつき（anon 90%）、プール以外の量も見立ての約 614 MiB でなく約 840 MiB と分かったため、追加の質問 F1 で 2304m にした。U1 の文書の値は承認の場でそろえる。 (learned 2026-10-18)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:51e889664b3e247205a3fc601835ef28134fa4e098f9c6eb3f4c667fcf787668",
  "contract_sha256": "sha256:7b5ef641649418f9e844285e996fba87207000bd5c245758d4323958b43707d3"
}
```

Contract の層の読み替え: この単位は画面を持たないため、`Frontend behavior` は当たらない（画面は U3）。`Data model / database behavior` は、値の型（`businessdata.domain`・`targetdb.domain`）・`targetdb` の設定の型・内部DB の移行 V13 と監査の型（`audit.domain`）に読み替える。methodology（test-after）と ordering（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）はそのまま守る。名前つきの管理者の注入の結合テスト（NFR 設計のレビューの R-06）は、設定の層のテストとして、書き込みの部品（口・SQL・業務処理）より前に通す。

## 3. 依頼者の承認を得ること

計画の承認（Approve Plan）で、次の承認もあわせて得る。生成の途中で、ここに書いた範囲を超える変更が要るとわかったら、生成を止めて依頼者に諮る。

| # | 変更 | 中身 | 緩める点 |
|---|---|---|---|
| A1 | `BusinessDataBoundaryArchitectureTest#nobodyDependsOnBusinessData` | 依存してよい相手に `cherry.mastersmith.audit..` を足し、`audit` が使ってよいのは `businessdata.domain` だけ（出来事 `BusinessDataChangedEvent` と監査の理由の値 `BusinessDataAuditReason`）と書く。`audit` から `businessdata.service`・`businessdata.repository`・`businessdata.web` への依存は止めたまま。規則が依存を見分けていることの確かめ（`audit` → `businessdata.domain` が実際にある）を1行足す。テストの名前と `@DisplayName` を直す（D6: A の形。D6 が B なら、代わりに `allowedDependencies` に `audit.domain` を足す変更になる） | `businessdata` に依存できる機能が1つ増える（契約 C5 の向き。`role`・`group` などの出来事と同じ形） |
| A2 | `BusinessDataBoundaryArchitectureTest#onlyRowSqlExecutorCreatesStatements` の見分けの確かめ | 文を作るメソッドの一覧（今は `RowSqlExecutor#search`・`#detail`）に、書き込みのメソッド（見込みは `precheckUnique`・`insert`・`update`・`delete`・`inspectViolation`）を足す。`RowSqlExecutor` の外で文を作らない規則は変えない | 規則は緩めない。確かめの一覧を広げるため、透明のために載せる |
| A3 | `backend/config/spotbugs-exclude.xml` の `SQL_` の除外を広げる | 既存の `RowSqlExecutor` の Match の `<Or>` のメソッドに、書き込みのメソッド（A2 と同じ名前）を足す。パターン名は既存の2つ（`SQL_INJECTION_JDBC`・`SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING`）のまま。理由に「書き込みの文の識別子も、要求1件の権限の写しの DSL の種類ごとの名前の一覧と照らしたものだけを `SqlIdentifiers.quoteKnown` で引用し、値（作成・更新の値・主キーの値・事前の問い合わせと確かめの値）はすべてバインドする」と書き足す。報告されたメソッドの名前・パターン名が見込みと違うときは、足す前に依頼者に見せる | SpotBugs の関門の対象から、この部品の書き込みのメソッドの指摘を外す（BR13.3・team.md） |

- 足す決まり（緩めない）: `BusinessDataBoundaryArchitectureTest` に「`BusinessDataWriteService` と `businessdata` の web・domain・repository は `@Transactional`・`TransactionTemplate`・`JdbcTemplate` に依存しない」を足す（内部DB のトランザクションを開かない。BR9.4）。`targetdb.service` の `JdbcTargetWriteAccess` だけが `TransactionTemplate` を使う。
- `TargetDbBoundaryArchitectureTest` は変えない（口は `Statement` の更新系を呼ばない。セーブポイントと確定・巻き戻しは管理者と `TransactionStatus` で行う。`connectionInitSql` は設定の文字で、`Statement.execute` を呼ばない。`JdbcTemplate` も `@Transactional` も使わない）。`AuditBoundaryArchitectureTest` も変えない（`audit` が依存してよい先を絞っていない。機能設計の X6）。
- U1 のコードを変えること（D5 の R-03、`ColumnDefinitionResponse`・`ColumnValidation`・`TableAccessPolicy` の検証の材料）は、承認済みの設計と依頼者の決定にもとづく変更で、境界テストの緩めではない。

## 4. 反映した決定（承認の場の決定と、設計の文書との差）

承認済みの設計の文書は書き換えず、計画で直す（`project.md` の学び）。

### 4.1 機能設計の承認の場

| 決定 | 中身 | 反映した所 |
|---|---|---|
| R-01 | 監査の理由の値は応答の code と別の列挙にし、32 字以内（`BUSINESS_ROW_OP_NOT_ALLOWED`）。足す値がすべて 32 字以内で、監査に残す応答の code のすべてに写しがあることを単体テストで確かめる | Step 7・8・11・12 |
| R-02 | 定義の API のカラムの項目に検証の材料を U2 が足す（U1 の型の余地） | Step 7・8・13〜16 |
| R-03 | 違反のカラムの確かめは、口が貸している同じ接続の中で、巻き戻した後に行う。確かめの失敗はフォーム全体の `BUSINESS_SAVE_REJECTED` | Step 4・5・9・18（NFR 設計 Q2: A のセーブポイントの形で） |
| U1 R-03・R-05 | 書き込みの口も貸す形、名前の一覧は種類ごと | Step 4・5・9 |
| U2 R-07・U3 R-09（Approve の決定） | `nullable` は U2 が足す検証の材料。`formPart` から `lookup` を外し、`text` として返す。U3 は `nullable`・`required` が欠けたら形の崩れとみなすため、書けないカラムを含むすべてのカラムで2つを省かずに返す。書けないカラムの `required` は偽、`nullable` は DB の値のまま（BR14.1 の「既定（偽）」より、この決定を正とする） | Step 7・13・15 |
| X5 | 作成で空にした `required` のカラム（DB 由来を含む）は「必須」の誤り | Step 7・18 |

### 4.2 NFR 要件の承認の場

| 決定 | 中身 | 反映した所 |
|---|---|---|
| U2 R-01 | `detail` の切り詰めは呼び出し側（`businessdata`）で行い、`AuditEvent` の「超えたら作らない」確かめは変えない | Step 7（`AuditDetailBuilder`）・Step 22 |
| U2 R-02 | 最悪の時間は確定・巻き戻しの待ちを含めて NFR 設計で計算し直す（NFR 設計 Q4: A。結合テストの線は 25 秒以内） | Step 21 |
| Q1〜Q6: A | 文ごとの上限 5 秒、事前の問い合わせと確かめはそれぞれ1文、pattern 100 ms・300 ms、本文 4 MiB・4 MiB・64 KB、値 10,000 字・主キー 1,000 字、プール 5、⑥ の監査は補助権限を持つ人だけ、k6 の場面 | Step 2・7・9・13・15・20・22・27 |
| R-03〜R-06（Minor） | 監査の行の増え方は既知の制約、4 MiB は UTF-8 の生の送信が前提、締め切りの超えの上限、監査の移行の後方互換の結合テスト | Step 12・20・28 |

### 4.3 NFR 設計の承認の場（Major はレビューの推奨の方向でこの計画で直す）

承認の場の決定: 各単位のレビューの Major は文書を直さず、レビューの推奨の方向でコード生成の計画で直す。U2 の文書は U1 の R-01・R-02・R-04 を先に避けた形のままとする。

| 指摘 | 重さ | この計画での扱い | 反映した所 |
|---|---|---|---|
| R-01 デッドロックなどの分類の抜け | Major | 書き込みの段の分類の表に「競合による巻き戻し」の行を足す（PG 40001・40P01・55P03、MySQL・MariaDB 1213・1205）。返す code は D1。分類は、文の失敗の種類を先に見てから進む（競合はセーブポイントの確かめに進まない。セーブポイントへの戻しが失敗したときも、元の失敗の種類で分ける。MariaDB の 1213 で全体が巻き戻り、続く戻しが 1305 で失敗する経路を含む）。形の誤り（22P02・22007・22018 など）の code は D2。確定の段の遅延の制約の違反（類 23）は D3。各 DB でこれらを起こす結合テストを足す（競合は、テストの管理者の接続で行を排他して書き込みのアカウントの待ちの上限を短くした形と、トリガーで番号を出す形） | Step 9・10・18 |
| R-02 生成された鍵の読みの失敗 | Minor | 結果の読みの段の失敗は必ず巻き戻して 500（メッセージの無い例外）。値で送った主キーで鍵が空なのは失敗に数えない（W8）。テストを足す | Step 9・10・18 |
| R-03 書き込みの文の時間切れ | Minor | D3 | Step 9・21 |
| R-04 違反の後の段の時間切れ | Minor | D4 | Step 9・21 |
| R-05 捨てる前に借りられる窓 | Minor | Hikari の借りるときの生存確認に頼る前提と、起きたときの結果（その要求が類 08 で失敗し UNAVAILABLE になるだけ）を `JdbcTargetWriteAccess` の Javadoc と generation-notes.md に書く。止めた相手の後の次の要求が通ることを Step 21 で確かめる | Step 5・21 |
| R-06 名前つきの管理者の注入 | Minor | 結合テスト（管理者の Bean を型で引くと内部DB の1つだけ、既存の `@Transactional` が内部DB の管理者を使う）を、書き込みの部品より前の Step 3 に置いて先に通す | Step 3 |
| R-07 25 秒の線 | Minor | 線は承認済みの 25 秒のまま。DB ごとの実測の時間を記録する（U1 は3種類とも約 10 秒）。答えない ServerSocket（借りる段の 3 秒）と、つないだ後に止まる中継（文の段・確定の段）で、何を確かめるかを分けて書く | Step 21 |
| R-08 perf の MySQL 768m | Minor | 基盤の設計の F1: A の 2304m で解けた | Step 27 |
| R-09 `\u` のエスケープの 413 | Minor | README と `perf/README.md` に書く | Step 28 |

### 4.4 基盤の設計の承認の場

承認の場の決定: U1 の文書の perf の MySQL の値と根拠（1792m）は直さず、U2 の試しの結果の **2304m（プール 768M）** を正とする。各単位のレビューの Major は文書を直さず、レビューの推奨の方向でコード生成の計画で直す。

| 指摘 | 重さ | この計画での扱い | 反映した所 |
|---|---|---|---|
| R-01 E2E の一時のロールの道 | Major | 承認の場で「U3 の設計で答えた」とされた。U2 は E2E を足さず、`frontend/playwright.config.ts` の `SECRET_ENV_NAMES` にも触れない。U3 の計画に引き継ぐ | 9節 |
| R-02 台本の渡し方・冪等・初期化の止まり | Minor | (1) 渡し方は PG が psql の変数、MySQL・MariaDB が `02-reader-account.sh` と同じエスケープした文字列（`\` と `'` を二重にする）で、台本の先頭に分けて書く。(2) 毎回 `ALTER ROLE … PASSWORD`（MySQL・MariaDB は `ALTER USER … IDENTIFIED BY`）を当て、再実行で `.env.targetdb` の値に合わせる。(3) README に「初期化が止まったら、ボリュームを消すか、冪等な台本を `docker compose exec` で流す」を書く | Step 26・28 |
| R-03 配備した見本の PG への手順 | Minor | (1) README に、乱数を変数に入れ、表示せずにリダイレクトで `.env.targetdb` と `.env` の両方へ足す書き方を書く。(2) `--profile targetdb-postgres` を付けた `--force-recreate` を書く。(3) 「01・02 だけで起動し、03 を `exec` で2回流す」場面を結合テストに足す。(4) 戻しの README に、PUBLIC の `TEMPORARY` を戻すか（戻さない。戻すなら `GRANT TEMPORARY ON DATABASE business TO PUBLIC`）を一言書く | Step 26・28 |
| R-04 2304m の外挿・anon・k6 のコンテナ | Minor | `perf/README.md` の業務データの節に、記録だけの `anon` の最大が上限の 85% を超えたら Performance Validation が依頼者に諮る、と書く（合否の線は変えない）。VM の表の判定に k6 のコンテナの使用量の記録を足す | Step 27 |
| R-05 03 の台本と `dsl-timing.sh` | Minor | ダミーの一時の環境ファイルで、perf の PG の初期化（01〜03）が通ることを実行して確かめる（台本を読むだけにしない）。`.env.targetdb.example` と README に「書き込みのパスワードが空だと新しいボリュームの初期化が止まる」を書く | Step 26・27・28 |
| R-06 V13 の中身の書き方・traceability の基準 | Minor | V13 は3列だけ、`UNKNOWN` は移行が要らない、とこの計画とコードの Javadoc でそろえる。`traceability.json` の OK の target には実装・テストのファイルを、測っていない負荷の目標は Performance Validation への Deferred にする（U1 の R-01 の学び） | Step 11・31 |
| R-07 付与のスキーマの名前の一覧・PG 14 以前 | Minor | `03-writer-account.sh` の先頭と README に、付与するスキーマの名前の一覧（見本の `sales`。perf の種のスキーマ `bizperf` は `bizPrepare` が付与する）と、PG 14 以前の `public` の `CREATE` は結合テストの外であることを書く | Step 26・27・28 |

### 4.5 U1 のコード生成のレビュー（R-03）

| 指摘 | この計画での扱い | 反映した所 |
|---|---|---|
| R-03 MySQL・MariaDB の `SET time_zone` が戻らない | 依頼者の決定で、接続を作るときに付ける形に切り替える。読み取りのプールの MySQL・MariaDB に `connectionInitSql`（`SET time_zone = '+00:00'`）を付け、`RowSqlExecutor` の貸し出しの始めの文（`SET_TIME_ZONE_UTC`）と、`RowDialect#needsUtcSession`・`RowQueryStage.SESSION_INIT` を消す。読み取りのプールの初期化の文に `sql_mode` を入れるかは D5。初期化の文は `TargetDataSourceConfig` の定数にし、読み取りと書き込みで同じ置き場にそろえる。貸すたびの通信の待ち（U1 R-04 の形）は変えない。初期化が失敗すると借りる失敗（`UNAVAILABLE`）になる。読み取りのプールは DSL の生成・照合と共有のため、メタデータの読み取りが時刻に依存しないことを Javadoc に書く | Step 2・3・9・10・21 |
| R-01 traceability の負荷の目標 | Code Generation の段の承認の場で直すため、この計画の範囲ではない。U2 の `traceability.json` は、その学びに合わせて測っていない目標を Deferred にする | Step 31 |
| R-02・R-04〜R-08 | 記録して持ち越す（依頼者の決定）。U2 では触れない。ただし R-05（中継の止める位置の版への依存）は、U2 の中継のテストで止めた段を診断の出力に残す形にする（D7） | Step 21 |

### 4.6 計画で決めたこと（設計の文書が計画に回したもの）

| 論点 | 決め | 出典 |
|---|---|---|
| 移行の番号 | V13（`V13__u2_business_data_audit_columns.sql`） | 機能設計 8節 |
| BR11.6 の単体テストの置き場 | 応答の code から理由の値への写しと字数は `businessdata.domain` のテスト、`AuditFailureReason` と `AuditEventType` のすべての値の字数と、`BusinessDataAuditReason` から `AuditFailureReason` への写しの網羅は `audit.domain` のテスト | 機能設計 8節 |
| 監査の理由の値の受け渡し | 出来事は `BusinessDataAuditReason`（`businessdata.domain`）を持ち、`audit` が `switch` で `AuditFailureReason` に写す（`businessdata` は `audit` に依存しない） | 契約 C5、`entities.md` |
| `longest()` | 読み取りの4つの目的の最大のまま。書き込みのプールの通信の待ちは「書き込みの上限 + 5 秒」で別に計算する | 基盤の設計 2.1 |
| ログのキーの名前 | `purpose`（`WRITE`）・`stage`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`（U1 とそろえる） | 監視の設計 1節、基盤の設計の監視の 7節 |
| 中継のテストの DB の種類 | D7 | NFR 設計 9節 |
| 試し走りの範囲 | D8 | 基盤の設計 7節 |
| `ColumnDefinitionResponse` の形 | 検証の材料をすべてのカラムで返し、型に当たらない項目は `null` で出す（`NON_NULL` で省かない） | BR3.6・BR14.1・4.1 の決定 |

## 5. Step（実装 → その層のテスト → 実行）

どの Step も、コードのコメント・Javadoc は日本語、テストの説明文（`@DisplayName`）は英語、ファイルの先頭に Apache License 2.0 のヘッダー（Java・TypeScript・JavaScript は `/* ... */`、シェルと SQL は言語のコメントの形、2026、agwlvssainokuni）を置く。テストデータの値は架空の値だけにし、メールアドレスは `example.com` などの予約のドメインだけにする。各 Step のテストのコマンドは `unit-test-instructions.md` の 3.2 にある。

### 準備

- [ ] **Step 1: 作業の準備と走らせ方の確かめ**
  - `aidlc/` の下の未コミットの変更（計画を書いた時点で `aidlc-state.md` と監査ログの shard に変更がある）を確かめ、依頼者にコミットの承認を得てから進む（`project.md` の Change Control）。
  - git の対象外の生成物（`frontend/playwright-report`・`frontend/test-results`）が残っていれば消す（`project.md` の学び）。
  - `develop` から `feature/261016-business-data-views-b4` を作って移る。
  - 走らせ方の確かめ（brownfield。最初のテストの Step の前）: `unit-test-instructions.md` の 3.1 のコマンドで、既存の `targetdb` と `businessdata` と `audit` の単体テストが通ることを確かめて記録する。colima が動いていることと、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡すことも確かめる。
  - 本体で `AuditResult` を `switch` で分ける箇所と、`TargetDbProperties.QueryTimeout` の作り方を使う箇所を検索し、1.3 の一覧に足りないものがあれば generation-notes.md に足す。

### 設定の層（Contract の Data model / database behavior の設定の部分）

- [ ] **Step 2: `targetdb.config` の実装（書き込みの設定・プール・管理者と、読み取りのプールの初期化）**
  - `TargetDbProperties`: `write`（`username`・`password`・`pool.maximumSize`、既定 5）と `QueryTimeout.write`（既定 5 秒、秒の単位で 1 秒以上）を足す。空の値で null になった項目を既定値にする処理にも足す。`longest()` は読み取りの4つの最大のまま。toString は書き込みの資格情報も有無だけ（BR9.1、NFR4.3・NFR4.4）。
  - `TargetDbSettings`: 読み取りが使える（`Usable`）うえで、書き込みのユーザー名とパスワードの両方があるときだけ「書き込みが使える」を返す口。片方だけなら、足りない項目の名前だけを返す（値は持たない。BR9.1、NFR4.11）。
  - `TargetDataSourceConfig`: 書き込みの片方だけのときに、項目の名前だけを WARN で1回出す（起動と読み取りは続ける）。読み取りのプールの MySQL・MariaDB に `connectionInitSql`（定数。D5）を付ける。
  - `TargetWriteDataSourceConfig`（新規）: 名前つきの `targetWriteDataSource`（`@Bean(name, defaultCandidate = false)`、書き込みが使えるときだけ作る）。プールの名前 `mastersmith-target-db-write`、読み取り専用にしない、minIdle 0、`initializationFailTimeout -1`、上限 5（環境変数で変えられる）、借りる待ちと確かめの待ちは既存の接続の待ち（3 秒）を共用、使っていない接続を閉じるまでの時間は読み取りの `pool.idle-timeout` を共用、ドライバーの接続の待ちは接続の待ち + 2 秒、**ドライバーの通信の待ちは書き込みの上限 + 5 秒（既定 10 秒）を計算で求める**（U1 の基盤の設計のレビュー R-01 を先に避ける）。MySQL は `useAffectedRows=false`・`allowLoadLocalInfile=false`・`allowUrlInLocalInfile=false`、MariaDB は `useAffectedRows=false`（ドライバーが持つ設定の名前を生成で確かめる）・`allowLocalInfile=false`。MySQL・MariaDB の `connectionInitSql` は定数 `SET time_zone = '+00:00', sql_mode = CONCAT_WS(',', NULLIF(@@sql_mode, ''), 'STRICT_ALL_TABLES')`（NFR 設計 Q3: A・Q6: A）。名前つきの `targetWriteTransactionManager`（`DataSourceTransactionManager`、`defaultCandidate = false`）。資格情報は URL に入れない。
  - `application.yaml` に `query-timeout.write`・`write.username`・`write.password`・`write.pool.maximum-size`（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_WRITE:5s`・`MASTERSMITH_TARGET_DB_WRITE_USERNAME:`・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD:`・`MASTERSMITH_TARGET_DB_WRITE_POOL_MAXIMUM_SIZE:5`）。`.env.example` に4項目のコメントの行と、読み取りと書き込みのアカウントの勧めを分けた説明を足す。配備の `.env` は開かず、変えない。
- [ ] **Step 3: 設定の層のテスト（名前つきの管理者の注入を先に通す）**
  - **`TargetWriteTransactionManagerInjectionIT`（新規。NFR 設計のレビュー R-06）**: 書き込みの設定がある状態でアプリを起動し、型で引ける `PlatformTransactionManager`・`DataSource` が内部DB の1つだけであること、既存の `@Transactional` の業務処理（例 ロールの作成）が内部DB の管理者で確定すること、`targetWriteDataSource`・`targetWriteTransactionManager` は名前でだけ引けること、Flyway と JPA が内部DB を使い続けることを確かめる。設定が無いときは2つの Bean が作られないことも確かめる。**この IT が通るまで、Step 4 以降へ進まない**（`unit-test-instructions.md` の 3.2 の順に、ほかの設定の IT より先に流す）。
  - 単体: `TargetDbPropertiesTest`（既定値・空の値・toString の伏せ字）、`TargetDbSettingsTest`（書き込みの両方・片方・無し）、`TargetDataSourceConfigTest`（読み取りの `connectionInitSql` は MySQL・MariaDB だけ、PG には付けない。読み取りの通信の待ちは書き込みの上限を変えても変わらない）、`TargetWriteDataSourceConfigTest`（新規。書き込みの上限を変えたときに通信の待ちが「上限 + 5 秒」になる、`useAffectedRows`・LOAD DATA LOCAL の無効、読み取り専用でない、プールの名前と上限、`connectionInitSql` の定数）。1.3 の `QueryTimeout` の作り方を使うテストを直す。
  - 結合: `TargetDbStartupIT`（書き込みの両方・片方・無しで起動を続け、片方だけのときは項目の名前だけの WARN が1回）、`TargetDbSecretLeakIT`（書き込みの資格情報がログ・toString に出ない）。
  - 流して、すべて通ってから次へ進む。

### 書き込みの口（Contract の Data model と Repository の targetdb の部分）

- [ ] **Step 4: `targetdb.domain` の実装**
  - `TargetWriteSession`（record。`DatabaseProduct product`・`Connection connection`・`int queryTimeoutSeconds` と、口が作るセーブポイントの操作の口）。toString は DB の種類と秒だけ。保存しない・戻り値にしない・口の外へ持ち出さない決まりを Javadoc に書く（R-08 の答え）。
  - `WriteStep<T>`（sealed）: `Commit(T result)`・`Rollback(T result)`・`RollbackToSavepointThenInspect(Function<TargetWriteSession, T> inspect, Function<RowWriteStage, T> onInspectFailure)` の形（名前は生成で決める）。toString は答えの種類だけ。
  - `TargetWriteOutcome<T>`（sealed）: `Done(T value)`（toString は値の型の単純名だけ）・`NotConfigured`・`Unavailable(sqlState, vendorCode, exceptionClass)`（借りる段の失敗）・`Failed(TargetWriteStage stage, sqlState, vendorCode, exceptionClass)`（確定・巻き戻し・セーブポイントの段の失敗）。メッセージと原因を持たない。
  - `TargetWriteStage`（借りる・確定・セーブポイントへの戻し・巻き戻し）、`TargetWriteUnexpectedException`（メッセージも原因も持たない。U1 の `TargetReadUnexpectedException` と同じ形）。
- [ ] **Step 5: `targetdb.service` の実装（書き込みの口）**
  - `TargetWriteAccess`（口）: `<T> TargetWriteOutcome<T> withWrite(Function<TargetWriteSession, WriteStep<T>> work)` と `Optional<DatabaseProduct> product()`。
  - `JdbcTargetWriteAccess`: 書き込みが使えないか接続が無いときは処理を呼ばずに `NotConfigured`。名前つきの `targetWriteTransactionManager` の `TransactionTemplate`（分離のレベル READ COMMITTED）の中で、`DataSourceUtils.getConnection` で1本を1回だけ借り、`TargetWriteSession` を貸す。答えに従う: 確定 → 確定、巻き戻し → `setRollbackOnly`、セーブポイントへ戻して確かめる → `rollbackToSavepoint` → 同じ接続で確かめの処理 → `setRollbackOnly`。処理の非検査の例外は `TargetWriteUnexpectedException` にする。**`TransactionTemplate` の外側で `TransactionSystemException` を含むすべての例外を受け**、段と SQLState・業者の番号・クラスの名前だけの `Failed`・`Unavailable` にする（W7。口のメソッドから例外を出さない。targetdb.service は TraceAspect の対象のため）。確定・巻き戻しが失敗した接続は、管理者が返した後に `HikariDataSource.evictConnection` で捨てる（W12。開いているトランザクションの中では捨てない）。捨てる・閉じる処理の失敗は、例外のクラスの名前だけを WARN で出す。R-05 の前提（返した後に借りられる窓は、借りるときの生存確認に頼り、起きてもその要求が類 08 で失敗するだけ）を Javadoc に書く。`@Transactional` は付けない。文は作らない。
- [ ] **Step 6: 書き込みの口のテスト**
  - 単体: `TargetWriteSessionTest`・`TargetWriteOutcomeTest`・`WriteStepTest`（toString に接続・値が出ない）、`JdbcTargetWriteAccessTest`（組み込みの H2 のメモリの DB を書き込みの DataSource に見立てる。確定・巻き戻し・セーブポイントへ戻して確かめる・設定が無い・借りる失敗・確定の失敗・処理の非検査の例外・捨てる処理が管理者の後に動くこと・貸した接続が戻り値に出ないこと）。
  - `TargetDbBoundaryArchitectureTest` を変えずに流して通す。

### 値の型と domain の層（Contract の Data model と Business logic の純粋な部分）

- [ ] **Step 7: `businessdata.domain` の実装**
  - 値の型: `WriteOperation`（CREATE・UPDATE・DELETE）、`WriteRequest`・`WriteValues`・`TypedValue`（toString はカラムの名前と件数だけ。BR12.1）。主キーは U1 の `RowKey`・`KeyInput` を使い回す。
  - `ColumnWriteRulesFactory`（純粋な関数）: 写しの DSL のカラムと DB の型から `ColumnWriteRules` を作る。BR3.6 の表（`minLength`・`maxLength`・`pattern` は STRING、`min`・`max` は数・日付・時刻、`precision`・`scale` は DECIMAL、`required`・`unique` はすべて）。`maxLength` は DSL と DB の長さと 10,000 字の小さい方（NFR4.9）。`lookup` は `text`。`nullable` はすべてのカラムで DB の値。
  - `ColumnValidation`（U1 の型）: `ColumnWriteRules` から写す口を足す（BR14.2）。作成でも更新でも書けないカラムは `formPart`・`nullable` のほかを既定（`null`・偽・空）にし、`required` は偽（4.1 の決定）。
  - `WriteRequestValidator`（純粋な関数、`Clock` を注入）: BR2.1（書けないカラムは `NOT_ALLOWED`、主キーのそろえ方は `KEY_MISMATCH`）、BR2.4（空の更新は `NO_CHANGE`）、BR3.2〜BR3.5・BR3.7（型・必須（作成で送られない DB 由来の `required` も `REQUIRED`）・コードポイントの長さ（10,000 字）・主キーの 1,000 字（`INVALID_VALUE`）・DECIMAL の桁（`TOO_MANY_DIGITS`）・整数の範囲・DSL の `min`・`max`・選択肢・真偽値）、日時の秒の端数（DB の型の精度を超えたら `TOO_MANY_DIGITS`。NFR 設計 Q6: A）、時差つきの型（オフセットの無い値は UTC、`Z`・`±hh:mm` も UTC にそろえる。NFR3.6）。誤りはカラムごとに最初の1つ。照合の前に長さを当てる。
  - `DeadlinePatternMatcher`: 締め切りを見る `CharSequence` の包み（256 文字ごとに注入した時計を読む、toString は `***`）、値ごとに 100 ms・1要求の合計 300 ms、合計を使い切ったら残りは照合せず `PATTERN_TIMEOUT`、全体一致、`PatternDeadline`（メッセージもスタックトレースも持たない例外）は部品の中で `PATTERN_TIMEOUT` に変える。組み立てた式は DSL の識別（`dslHash`）ごとに数を限って取っておく（BR4・NFR4.7）。
  - `BusinessDataFieldErrorReason` に U2 の理由（`KEY_MISMATCH`・`REQUIRED`・`TOO_SHORT`・`TOO_LONG`・`OUT_OF_RANGE`・`TOO_MANY_DIGITS`・`PATTERN_MISMATCH`・`PATTERN_TIMEOUT`・`DUPLICATE`・`REFERENCE_NOT_FOUND`・`NOT_NULL`・`NOT_EDITABLE_NOT_NULL`・`NO_CHANGE`）、`BusinessDataFieldError` に pattern の `message`（DSL の ja・en。値は持たない）。
  - `BusinessDataProblemTypes` に `BUSINESS_DEFINITION_CHANGED`・`BUSINESS_WRITE_NOT_CONFIGURED`・`BUSINESS_DUPLICATE_VALUE`・`BUSINESS_REFERENCE_NOT_FOUND`・`BUSINESS_SAVE_REJECTED`（どれも 409。BR1.9。D1 が B なら1つ増える）。
  - `RowWriteStage`（借りる・事前の問い合わせ・書き込みの文・確定・セーブポイントへの戻し・確かめ・巻き戻し・結果の読み）、`RowWriteFailureReason`、`RowWriteFailure`（理由・段・SQLState・業者の番号・クラスの名前）。
  - `BusinessDataChangedEvent`（操作した人・作業ロール・スキーマ・テーブル・操作・結果（SUCCESS・FAILURE・UNKNOWN）・`BusinessDataAuditReason`・`detail` の文字・主キーのカラムの数・`keyTruncated`・要求の元の値。toString は主キーを伏せる）。`BusinessDataAuditReason`（9つの値）と、応答の code から理由の値への写し（`switch` で場合を尽くす1か所。名前が同じことに頼らない。BR11.6）、結果の値の決め方（TIMEOUT だけ UNKNOWN。D3 の結果に合わせる）。
  - `AuditDetailBuilder`: `{key: {カラム: 値}, keyTruncated: 偽}` の JSON を作り、16,384 字（UTF-16 の単位）を超えるなら値を後ろから切って収め、`keyTruncated` を真にする（BR11.4、NFR 要件の U2 R-01）。
- [ ] **Step 8: domain の層のテスト**
  - 単体（部品ごとに 5〜8 件を目安）: `WriteValueTypesTest`（伏せ字の toString・変更できない）、`ColumnWriteRulesFactoryTest`（BR3.6 の表、`lookup` → `text`、10,000 字の上限、`nullable` はすべてのカラム）、`WriteRequestValidatorTest`（作成・更新・削除の各理由、長さ・桁・範囲・秒の端数の境界（ちょうど・1つ超え）、10,000 と 10,001 字、主キーの 1,000 と 1,001 字、サロゲートペア、DB 由来の必須、空の更新、時差の3つの形）、`DeadlinePatternMatcherTest`（値ごと・合計の締め切りの境界（ちょうど・超え）を注入した時計で、重い式 `((a+)+)+c`・`(.*a){12}` を `a` の 30 字＋`!` に当てる。`(a+)+$` は重くない式として残す。F4: A）、`DeadlinePatternTimingTest`（NFR1.6: 100 カラムに重い式と長い値を置いた検証の全体が、実時間で 400 ms 以内）、`BusinessDataAuditReasonTest`（監査に残す応答の code のすべてに写しがある・写しが 32 字以内・残さない code に写しが無い）、`AuditDetailBuilderTest`（ちょうど 16,384 字・超え・複合の主キー）、`ColumnDefinitionWriteItemsConsistencyTest`（同じ DSL から作った定義の項目とサーバーの検証の境界の値を突き合わせる。BR14.2）、`TableAccessPolicyTest` の直し（検証の材料が入る）。
  - 性質ベース（jqwik。失敗の種は既存の設定で出力に残る）: `WriteRequestValidatorPropertyTest`（長さ・桁・範囲の内側は受け付け外側は拒む、書けないカラムは必ず拒む）、`DeadlinePatternMatcherPropertyTest`（任意の値と式で、合計の締め切りを超えて照合が続かない）。NFR7.6。
  - 流して、すべて通ってから次へ進む。

### repository の層（Contract の Repository / data access）

- [ ] **Step 9: `businessdata.repository` の実装**
  - `RowWriteSqlBuilder`（パッケージの中だけ）: 事前の問い合わせ（送った unique のカラムのうち値が NULL でないものを1文の `SELECT (SELECT 1 … LIMIT 1) AS c0, …`。更新は自分の行を除く）、送ったカラムだけの INSERT、送ったカラムだけを SET し主キーで行を指す UPDATE、主キーで行を指す DELETE、違反の後の確かめの1文（一意は事前の問い合わせと同じ形、外部キーは参照先ごとの `(SELECT 1 FROM 参照先 WHERE … LIMIT 1)` を並べる）。識別子は種類ごとの名前の一覧と照らしてから `SqlIdentifiers.quoteKnown` で引用し、テーブルはスキーマ名で修飾する。値はすべてバインドする（BR7.1）。
  - `RowSqlExecutor` の書き込みのメソッド（見込みは `precheckUnique`・`insert`・`update`・`delete`・`inspectViolation`）: 文を作るのは各メソッドの本体だけ。`TargetWriteSession` を受け、`setQueryTimeout(5)` を付け、`SQLException` と非検査の例外をメソッドの中で受けて結果の型（`RowWriteFailure` を含む）にする（U1 R-02 を先に避ける）。生成された鍵は主キーのカラムの名前を渡す `prepareStatement(sql, String[])` で読む（W8。PG の全部のカラムを受け取らない）。値で送った主キーで鍵が空なのは失敗に数えない。結果の読みの段の失敗は想定外の失敗にする（R-02）。件数は一致した行で数える。
  - 読み取り（D5）: `search`・`detail` の貸し出しの始めの `SET time_zone` を消し、`SET_TIME_ZONE_UTC` と `RowDialect#needsUtcSession`・`RowQueryStage.SESSION_INIT` を消す。
  - `RowDialect`・`PostgresRowDialect`・`MysqlFamilyRowDialect`: 事前の問い合わせと確かめの形、生成された鍵の読み方、主キーの条件。
  - `ColumnValueCodec`: 書き込みの型つきのバインド（`BIGINT`・`DECIMAL` は桁を保つ型、日付・日時は日時の型、真偽値、PG の `timestamptz`・`timetz` は UTC の `OffsetDateTime`・`OffsetTime`、MySQL・MariaDB の `timestamp` は UTC の `LocalDateTime`。`stringtype=unspecified` に頼らない。BINARY・OTHER は作らない）。
  - `RowSqlFailureClassifier` の書き込みの表（DB の種類と段で引く。メッセージを読まない）: reliability-design.md 2節の段ごとの表と番号の表（一意 23505/1062、外部キー 23503/1452・1451、NOT NULL 23502/1048・1364、長さ 22001/1406、桁あふれ 22003/1264、CHECK 23514/3819・4025、日時の範囲 22008/1292、文字集合 1366、NUL 22021、更新できないビュー 55000/1471、未定義の表・カラム 42P01・42703/1146・1054、時間切れ・断絶）に、4.3 の R-01 の競合（40001・40P01・55P03/1213・1205。D1）と形の誤り（22P02・22007・22018 など。D2）を足す。文の失敗の種類を先に決め、一意・外部キーだけをセーブポイントの確かめに進める。セーブポイントへの戻し・確かめ・最後の巻き戻しの失敗は、元の失敗の種類を先に見てから分ける（D4）。確定の段（D3）。
  - `spotbugs-exclude.xml` に A3 の除外を足す。
- [ ] **Step 10: repository の層のテスト**
  - 単体: `RowWriteSqlBuilderTest`（引用・修飾・バインドの位置、事前の問い合わせと確かめの1文、自分の行を除く条件（単一・複合の主キー）、`'`・`"`・`;`・`--`・バッククォート・改行を含む名前が一覧の照合を通らなければ文を作らない）、`RowSqlExecutorTest`（書き込みのメソッドの結果の型、非検査の例外、鍵の読み方。読み取りの `SET` を消した直し）、`RowSqlFailureClassifierTest`（書き込みの表のすべての行、段ごと、MariaDB の 1213 の後の 1305 が競合として分かれること、`SESSION_INIT` の行を消す）、`RowDialectTest`（書き込みの形。`needsUtcSession` の確かめを消す）、`ColumnValueCodecTest`（書き込みのバインドの型）。
  - `./gradlew :backend:spotbugsMain :backend:spotbugsGate` を流す。報告が A3 の見込みと違えば、足す前に依頼者に見せる。
  - 流して、すべて通ってから次へ進む。

### 監査（Contract の Data model / database behavior の内部DB の部分と Business logic）

- [ ] **Step 11: `audit` の実装と V13**
  - `V13__u2_business_data_audit_columns.sql`: 3列を足す（空を許す・既定値・参照の制約・索引なし）。先頭のコメントに、`UNKNOWN` は `result VARCHAR(16)` に入り移行が要らないこと、前の版は3列を読まないことを書く。
  - `AuditEventType` に `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED`、`AuditFailureReason` に9つの値、`AuditResult` に `UNKNOWN`（Javadoc に「書き込みの確定の段の時間切れで、確定したか分からない」）。
  - `AuditEvent` に `target_schema`・`target_table`・`work_role_id` の3つの項目（`@Column` の長さは 255）、業務データの出来事の組み立て（`AuditEventFactory`）。`AuditEvent` の `detail` の「超えたら作らない」確かめは変えない。
  - `AuditEventListener`: `onBusinessDataChanged`（`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`）。`BusinessDataAuditReason` から `AuditFailureReason` への写しは `switch` で場合を尽くす。`logFailure` を出来事の種類で分け、業務データの出来事では `detail`・主キーの値・`setCause` を載せず、主キーのカラムの数・`keyTruncated`・例外のクラスの名前だけを載せる（BR11.5、NFR8.4、W11）。ほかの出来事の ERROR は変えない。文（「監査イベントの記録に失敗しました」）も変えない。
  - `BusinessDataBoundaryArchitectureTest` の A1 の変更（D6）。
- [ ] **Step 12: 監査のテスト**
  - 単体: `AuditBusinessDataEventFactoryTest`（3つの種類・9つの理由・`UNKNOWN`・3列・`detail`）、`AuditBusinessDataEventListenerTest`（写しの網羅、`AuditFailureReason` と `AuditEventType` のすべての値（既存を含む）が 32 字以内、失敗の ERROR の項目に `detail`・主キー・cause が無い）、`AuditEventTest`（変えずに通す）。
  - 結合: `AuditSchemaIT`（V13 の3列）、`AuditMigrationCompatibilityIT`（`CURRENT_LAST_VERSION` 13、V13 の前に書いた行が V13 の後も読めて3列が空、V13 の後に前の版の列だけで追記できる。NFR 要件のレビューの R-06）、`AuditSecretLeakIT`（列の一覧）、既存の `AuditWriteFailureIT` などがそのまま通ること。
  - 境界: `BusinessDataBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`。
  - 流して、すべて通ってから次へ進む。

### service の層（Contract の Business logic）

- [ ] **Step 13: `businessdata.service` の実装**
  - `BusinessDataWriteService`: 要求の文脈から利用者 ID を読む。`TableAccessResolver` で写しを1回取り（内部DB の短い読み取りで取り終える）、②〜④ を U1 の `accessFor` で判定する。⑤ `definitionId`、⑥ 主キーとビューの決まり（409。監査は、その操作の補助権限を持つ人のときだけ出す。NFR4.14）、⑦ CREATE・DELETE・FULL（403）、⑧ 書き込みの設定（409）、⑨ 検証（400。値を型つきの値にする）。⑤〜⑨ で拒んだときは対象DB に触れない。書き込みの口を1回だけ呼び、処理の中で事前の問い合わせ → 書き込みの文 → 件数（1でなければ巻き戻し）→ 生成された鍵を行い、一意・外部キーの違反ならセーブポイントへ戻して確かめる答えを返す。口から戻った後に、結果の型を code に変え、**ログを出すのはこの1か所**（WARN・スタックトレースなし。`purpose`・`stage`・`reason`・`sqlState`・`vendorCode`・`exceptionClass` だけ。業務の失敗の DUPLICATE・REFERENCE_NOT_FOUND はログを出さない）。その後に `BusinessDataChangedEvent` を出す（BR11.1。口の中・トランザクションの中で出さない）。内部DB のトランザクションを開かない。結果は `WriteResult`（sealed interface の record）で返す。
  - 定義の API: `TableAccessResolver`・`TableAccessPolicy` が写しの DSL と DB の種類から `ColumnWriteRules` を作り、`ColumnValidation` に写す（BR14.1）。読み取りの判定・道・ほかの項目は変えない。
  - `BusinessDataProblemTypeCatalog` に5つの code。
- [ ] **Step 14: service の層のテスト**
  - 単体: `BusinessDataWriteServiceTest`（偽の口と写しで、判定の順 ①〜⑩ の各場合、⑥ の監査の範囲（補助権限を持つ人・持たない人）、口を1回だけ呼ぶ、出来事は口から戻った後、拒んだときは口を呼ばない、ログの1か所とキー、TIMEOUT の結果が UNKNOWN、D1〜D4 の写し）、`BusinessDataProblemTypeCatalogTest`、`TableAccessResolverTest`（検証の材料）。
  - 流して、すべて通ってから次へ進む。

### web の層（Contract の API / endpoint）

- [ ] **Step 15: `businessdata.web` の実装**
  - `BusinessDataWriteController`（`@ApiAccess(ApiAccessLevel.AUTHENTICATED)`）: `POST /api/tables/rows`（201、`definitionId` と DETAIL が許されるときだけ `key`）・`PATCH /api/tables/rows`（200）・`POST /api/tables/rows/delete`（200）。道は固定し、名前・`key`・`values`・`definitionId` は本文で受ける。値は文字か `null`。要求の record は値と主キーを伏せた toString。結果の型を `switch` で場合を尽くして応答か `BusinessException` に変える（403 は共通の `ACCESS_DENIED`、400 は `VALIDATION_FAILED` に `fieldErrors`（`field`・`reason`・pattern の `message`）。値は入れない）。
  - `BusinessDataPaths` に書き込みの道、`BusinessDataWebConfig` に道ごとの本文の上限（作成・更新 4,194,304 バイト、削除 65,536 バイト。413 `PAYLOAD_TOO_LARGE`）。
  - `ColumnDefinitionResponse`: 検証の材料をすべてのカラムで返す（`NON_NULL` で省かない。`required`・`nullable` は必ず値を持ち、型に当たらない項目は `null`）。
  - `ApiEndpointCounts.MIN_ENDPOINTS` を 73 にする。
- [ ] **Step 16: web の層のテスト**
  - 単体: `BusinessDataWriteControllerTest`（3本の成功・各拒否の code と状態コード・`fieldErrors` の形・応答に値が無い・要求の toString の伏せ字）、`BusinessDataReadControllerTest`（126行の直し。検証の材料が出る）、`ApiAccessArchitectureTest`。
  - 結合: `ApiAccessConsistencyIT`・`ProblemTypeDuplicateStartupIT`。
  - 流して、すべて通ってから次へ進む。

### 横断の結合テスト・構造の検査

- [ ] **Step 17: 結合テストの手伝い（`businessdata/testsupport`・`targetdb/testsupport`）**
  - `BusinessDataWriteTables`: テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース。名前に実行ごとに一意の値）を作って消す。表: 自動の採番の主キー（PG は `serial` と `identity` の両方）、複合の主キー、一意の制約のあるカラムと無い unique のカラム、参照先のある外部キー（`ON DELETE CASCADE` の表を含む）、NOT NULL（入力できるカラム・できないカラム）、CHECK、更新できないビュー、主キーの無い表、境界の値の型（`BIGINT`・`DECIMAL(38,10)`・時差つきと無しの日時・秒の端数）、競合を起こすトリガー（D1 の確かめ）。
  - 書き込みのアカウント: テストのクラスごとに、そのスキーマの DML（と PG の sequence の USAGE）だけを与えたアカウントを作る（DDL を与えない）。パスワードはテストの中の乱数。
  - 別の接続で読み直す部品（アプリの接続とは別。書いた後の値・件数を確かめる）。
  - `BusinessDataTestApp` に書き込みの資格情報を渡す口、`CountingDataSourceConfig` に `targetWriteDataSource` を包む列、`StallingRelay` に確定の途中で止める形（D7）と止めた段を診断の出力に残す形（U1 R-05）。
- [ ] **Step 18: 3種類の対象DB の書き込みの結合テスト**
  - `AbstractBusinessDataWriteIT` と `PostgresBusinessDataWriteIT`・`MysqlBusinessDataWriteIT`・`MariadbBusinessDataWriteIT`（版を固定したイメージ）。
  - 作成（送ったカラムだけ、既定値、自動の採番の主キーを返す、主キーの無い表の作成で `key` が空、DB 由来の必須）、更新（変えたカラムだけ、今と同じ値の更新も1行、後勝ち、空の文字列と null）、削除（`ON DELETE CASCADE` は DB に任せる）、当たった行 0 は 404・2 以上は 409 で巻き戻し、一意（事前の問い合わせと制約の違反が同じ code、カラムの決まり方）、外部キー（作成・更新は `REFERENCE_NOT_FOUND` でカラムを示す、削除は `SAVE_REJECTED`）、NOT NULL（入力できる・できないカラムの reason）、CHECK・日時の範囲・文字集合・NUL（PG）・更新できないビューは `SAVE_REJECTED`（Q6: A・F3: A）、競合と形の誤り（D1・D2）、境界の値（`BIGINT` の上限と下限・`DECIMAL(38,10)`・NULL を丸めずに書き、別の接続で読み直して1桁も変わらない）、時差（オフセットの無い値・`Z`・`+09:00` が同じ時点、JVM の既定のタイムゾーンを変えても同じ）、秒の端数の境界、識別子と値に `'`・`"`・`;`・`--`・バッククォート・改行（値は文字として保存され、識別子は 404・400）、PG の生成された鍵が主キーだけ、書き込みのアカウントの接続を使い読み取りの API は読み取りのアカウントを使う。
  - 書いた後の値は、アプリとは別の接続で読み直して確かめる。アプリのトランザクションの巻き戻しに頼らない。
- [ ] **Step 19: 認可と IDOR・一括代入の結合テスト**
  - `BusinessDataWriteAuthorizationApiIT`: 3本 × 未認証 401・要る権限だけを欠く利用者 403・持つ利用者 200 を、パラメーターを使う表で書く（403 の利用者はほかの権限を持つ）。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わる。停止中の利用者は通らない。判定の順（②〜⑨。NONE のテーブルは行の有無にかかわらず 403、⑥ の 409 が ⑦ の 403 より先、定義が古いと 409）。DSL に無いスキーマ・テーブル（404）、書けないカラムを本文に足す（400 `NOT_ALLOWED`、反映されない）、`definitionId` の差し替え（409）、無い行（404）で、状態が変わらないこと（別の接続で確かめる）と、対象DB に文が出ないこと（数える包みで文の数 0）。
- [ ] **Step 20: 本文と値の長さの結合テスト**
  - `BusinessDataWriteRequestLimitIT`: 作成・更新の 4 MiB ちょうどは受け付け、1 バイト超えは 413（400 より先）、削除の 64 KB の境界、全部のカラムを 4 バイトの文字で埋めた最大の本文が通る（NFR 要件の R-04）、値の 10,000 字と 10,001 字、主キーの 1,000 字と 1,001 字。どの応答にも値が入らない。
- [ ] **Step 21: 時間切れと失敗の結合テスト**
  - `BusinessDataWriteTimeoutIT`: 答えない ServerSocket（借りる段の 3 秒で 503 `UNAVAILABLE`）、接続できない番号、つないだ後に止まる中継で書き込みの文の段（D3）・確定の段（503 `BUSINESS_TARGET_DB_TIMEOUT`、監査は `UNKNOWN`）・違反の後の確かめの段（D4）で止め、3種類とも 25 秒以内に返ること（実測の時間を DB ごとに記録。NFR 設計の R-07）、止めを解いた後の次の要求が通ること（R-05）、応答とログに JDBC の例外のメッセージ・接続先・値が無いこと。中継を置く DB の種類は D7。
  - `BusinessDataTimeoutIT`（U1）の MySQL の通すまとまりの数を、D5 の直しに合わせて手元で確かめて直す。
  - `BusinessDataWriteNotConfiguredIT`: 書き込みの資格情報の両方・片方・無しで、書き込みは 409 `BUSINESS_WRITE_NOT_CONFIGURED`（権限の無い人には 403 のまま）、読み取りは動き続け、定義は `writeConfigured: false`、WARN は項目の名前だけ。
- [ ] **Step 22: 監査の結合テスト**
  - `BusinessDataWriteAuditIT`: 成功の3種類が、操作した人・作業ロール・3列・`detail` の主キー（主キー以外の値・前後の値は無い）とともに残る。BR11.3 の残す失敗（理由の値）と残さない拒否（401・403・400・DSL とテーブルの 404・定義が古い・設定が無い）。⑥ は補助権限を持つ人だけが残る（作成・更新・削除のそれぞれ。NFR4.14）。`detail` がちょうど 16,384 字・超え（`keyTruncated` 真）。TIMEOUT は `UNKNOWN`。監査の書き込みを失敗させても操作は成功のままで、ERROR の項目に `detail`・主キーの値・`exception`（cause）が無く、主キーのカラムの数と `keyTruncated` がある（既存の `FailingAuditEventRepositoryConfig` を使う）。
- [ ] **Step 23: 漏えいの結合テスト**
  - `BusinessDataWriteSecretLeakIT`（TRACE を有効にする）: 作成・更新・削除・違反・時間切れ・監査の書き込みの失敗・非検査の例外（不正な数・日時の文字）・結果の型を通る戻り値の場合で、ログに値・主キー・接続先・例外のメッセージが無く、ログのキーが決めた名前だけ。400・409・503 の応答に値が無い。
  - `BusinessDataWriteTraceExposureIT`（外部へ送る設定を有効にする）: トレースの属性に値・主キーが無い。
- [ ] **Step 24: 接続の数の結合テスト**
  - `BusinessDataConnectionUsageIT` に作成（違反の後の確かめの経路を含む）・更新・削除の3行と、書き込みの DataSource の列を足す。要求の始めに借りている数が 0、同時の最大が内部DB 1・対象DB の書き込み 1・読み取り 0、書き込みの接続を借りている間は内部DB が 0、口は1回だけ借りる（NFR2.6）。
- [ ] **Step 25: 構造の検査と静的解析**
  - `BusinessDataBoundaryArchitectureTest`（A1・A2 と足す決まり）、`TargetDbBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`ArchitectureTest`・`ApiAccessArchitectureTest` を流す。
  - `./gradlew :backend:spotbugsMain :backend:spotbugsGate`（`SQL_` の残り 0 件）。

### 見本の対象DB・負荷の試験の台本・文書（Contract の Environment/build configuration と Documentation）

- [ ] **Step 26: 見本の対象DB の書き込みのアカウントと、その結合テスト**
  - `docker/targetdb/{postgres,mysql,mariadb}/03-writer-account.sh`: `mastersmith_writer`。パスワードは `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD`（空なら exit 1）。渡し方は PG が psql の変数、MySQL・MariaDB がエスケープした文字列（4.4 の R-02）。無ければ作り、毎回パスワードと付与を当て直す（冪等）。PG: `USAGE ON SCHEMA`、`SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA`、`USAGE ON ALL SEQUENCES IN SCHEMA`、`ALTER DEFAULT PRIVILEGES`、`REVOKE TEMPORARY ON DATABASE business FROM PUBLIC`。MySQL・MariaDB: `SELECT, INSERT, UPDATE, DELETE ON business.*`。DDL・`CREATE TEMPORARY TABLES`・`LOCK TABLES`・`SHOW VIEW` を与えない。先頭のコメントに付与するスキーマの一覧と PG 14 以前の注意（4.4 の R-07）。
  - `.env.targetdb.example` に `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=` と「空だと新しいボリュームの初期化が止まる」の注記。
  - `TargetDbSampleInitScriptIT`（3種類。基盤の設計 Q6: A）: (a) 実際の `docker/targetdb/<種類>/` の台本を初期化の場所に写して起動し、`mastersmith_writer` は見本の表の DML が通り、CREATE・DROP・ALTER・TRUNCATE・INDEX・VIEW・SCHEMA は拒まれ、PG の一時の表も拒まれ、`mastersmith_reader` では書けない。(b) 01・02 だけで起動し、03 を `exec` で2回流して同じ結果になり、パスワードを替えて流し直すと新しいパスワードで入れて古いパスワードでは入れない（4.4 の R-02・R-03）。パスワードはテストの中の乱数。
- [ ] **Step 27: 負荷の試験の台本**
  - `perf/biz/make-biz-seed.sh` と `perf/make-biz-dsl.mjs`（1対1にそろえる）: 書き込みの場面の表（100 カラムの作成の表、自動の採番の主キー、一意の制約のあるカラム、参照先の表と外部キー）と、書き込みのアカウントへの種のスキーマの DML の付与（`bizPrepare` の中。MySQL・MariaDB は種の DB、PG は種のスキーマと sequence）。値は架空。
  - `perf/k6/scenarios.js`: `bizCreate`（10 VU・3 分）・`bizUpdate`（10 VU・3 分、VU ごとに別の行）・`bizDelete`（既定 100 回、setup で回数以上の行を作る）・`bizWritePoolLimit`（10 VU・3 分、参照先に無い値の作成で 409 を正しい答えとして checks に入れる）・`bizCreateLarge`（10 VU・短い回、4 MiB 近い成功の作成）。1回の繰り返しに要求1つ、判定は `iteration_duration{scenario:…}` の p95 < 1000 と `checks` の率 1（`bizCreateLarge` は記録だけ）、トークンは `setup()` で取る。setup の前提（DSL の適用、FULL のカラムと CREATE・DELETE の補助権限を持つロール・割り当て・作業ロール・トークン、`bizDelete` の行）は自分で作り、前の場面の状態に頼らない（U1 の基盤の設計のレビュー R-02、`project.md` の学び）。
  - `perf/dsl-timing.sh` と `perf/README.md` の一時の環境ファイルに `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` の生成を、アプリの一時の環境ファイルに `MASTERSMITH_TARGET_DB_WRITE_*` を足す（値は表示しない）。
  - `perf/README.md` の業務データの節: 書き込みの場面、5.3 の流す順（U1 の読み取りの場面の後）、`bizCreateLarge` の前にアプリのコンテナを作り直す、片付ける前に見るもの（待ちの時間切れの累計・待ちの最大・アプリの `memory.peak`・対象DB の OOMKilled と `oom_kill`・`anon` の最大・`memory.events` の `max`・WARN の件数・監査の行の数・内部DB の大きさ）、`anon` の最大が上限の 85% を超えたら諮る（4.4 の R-04）、k6 のコンテナの使用量の記録、MySQL 2304m（既にある記述を保つ）、`\u` のエスケープの 413（R-09）。
  - 確かめ: `k6 inspect --include-system-env-vars` で5場面の名前と閾値を確かめる。試し走りは D8。`docker compose -f docker/perf/compose.yaml config`（ダミーの `MASTERSMITH_PERF_ENV_FILE` と3つの `--profile`）で既定の `command` と `mem_limit` が今のままであることを確かめる（基盤の設計の (v)）。ダミーの一時の環境ファイルで perf の PG の初期化（01〜03）が通ることを実行して確かめる（4.4 の R-05。PG の1種類）。
- [ ] **Step 28: 文書**
  - `README.md`: 環境変数の表の4行。「対象DB」の節の 566行（読み取り専用だけの記述）・588行（見本の DB が持つもの）を、書き込みの接続は別のアカウントで行の操作だけを発行する、書き込みのアカウントとアプリから書いた行も持つ（作り直すと失われる）に直す。小見出し「業務データの書き込みの API」（3本の道・ログインだけ・権限はテーブルごと・書き込みの接続・code と 409・503・413 の出し分け・監査）。見本の書き込みのアカウント（既存のボリュームへの足し方: 乱数を変数に入れて表示せずに `.env.targetdb` と `.env` の両方へ足す、`--profile targetdb-postgres` の `--force-recreate`、`docker compose exec` で 03 を流す、件数で確かめる。消し方: `DROP ROLE`・付与の取り消し・PUBLIC の `TEMPORARY` を戻すか。初期化が止まったときの直し方）。残る広さ（DSL に無い表も DB の上では書ける。アプリはサーバー側で DSL の表だけに絞る）と、利用者の DB で表ごとに絞る書き方（参照先の表の SELECT も要る）、PG 14 以前の `public` の `CREATE` の取り上げ方。既知の制約（一意の制約の無いカラムの同時の重なり、同じ行の後勝ち、索引の無い unique のカラムは表の全部を読む、大きな本文の同時の書き込みが対象DB のメモリを使い切りうる、補助権限を持つ人の繰り返しの失敗で監査の行が増えうる）。
  - Javadoc と `V13` のコメントで、V13 は3列だけ・`UNKNOWN` は移行が要らないと書く（4.4 の R-06）。

### 検査と記録

- [ ] **Step 29: 1コマンドの検査とカバレッジ・時間の記録**
  - `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる（入っていたら結果を使わずに流し直す）。
  - `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）。対象DB のテストが SKIPPED でないこと。
  - 記録: verify の時間（U1 の B2 の後は 18 分 32 秒。書き込みの結合テスト・見本の初期化の台本の IT・中継で数分延びる見込み。CI の上限の7割の約 42 分を超えたら依頼者に諮る。NFR7.4）、カバレッジ（全体と `businessdata.*`・`targetdb.*`・`audit.domain`・`audit.service` のパッケージごと。実測の数字だけ）、テストの件数、`packagesJudgedByTotal` の本体のソースに手を入れていないこと。
  - 落ちたテストは、team.md の「不安定なテストと CI の失敗」の決まりで扱う。計画の外の失敗で直し方が分かれるときは、生成を止めて依頼者に諮る（G1〜）。
- [ ] **Step 30: E2E の全件**
  - U2 は業務データのパッケージの `src/main` に触れるため、統合の前に E2E の全体を流す（team.md の Way of Working）。Mailpit を `docker compose --profile mail up -d mailpit` で起動し、`caffeinate -dims` で包んで `./gradlew e2eTest` を流し、通った件数を記録する。眠りの確かめは Step 29 と同じ。
  - E2E のファイルは足さない（業務データの代表の流れは U3 が持つ）。
- [ ] **Step 31: 記録**
  - `code-summary.md`（作ったもの・計画との差・依頼者に確かめたいこと・9節の引き継ぎ）。
  - `traceability.json`（機能設計の BR・NFR 要件の枝番から、部品とテストへ。測っていない負荷の目標（NFR1.4・NFR1.5・NFR2.7・NFR2.8・NFR2.9 の実測・NFR3.4）は OK にせず、Performance Validation への Deferred にし、target に場面の名前を書く。NFR8.5・NFR8.6 は Observability Setup への Deferred）。
  - `source-manifest.json`（作った・変えた・消したアプリのソースのパスをすべて）。
  - `generation-notes.md`（計画に無い変更は、計画を書き換えずにここへ記録する。`project.md` の学び）。

### コミットと統合

- [ ] **Step 32: コミットの提案と積み（依頼者の承認）**
  - 生成の後に、7節の区切りで提案し、依頼者の承認を得てから積む。区切りごとに `./gradlew :backend:compileJava :backend:compileTestJava` でコンパイルが通ることを確かめる。
  - コード生成の段の承認の場は、最後のコードのコミットの後、`aidlc/` の下だけの記録のコミットを積む前に開く（`project.md` の学び）。
- [ ] **Step 33: 統合（依頼者の承認の後）**
  - 統合の前に `aidlc/` の未コミットの変更（監査ログを含む）をコミットしておく。
  - Step 29 の verify と Step 30 の E2E が通った状態で、`develop` へ squash で1コミットとして戻す。件名は日本語で「業務データの書き込みの API を足す（Intent 261016-business-data-views、B4）」の形。統合を終えたブランチは消す。
  - `origin` への push は依頼者が行う。CI の結果は Build and Test で試行ごと（`gh run view --json attempt,jobs`）に確かめる。

## 6. ストーリーから Step への対応

| ストーリー（U2 の受け持ち） | 主な受け入れ基準 | Step |
|---|---|---|
| US2.1 行を作成する | AC2.1.1〜AC2.1.9 | 7・8・9・13・15・18・22 |
| US2.2 行を更新する | AC2.2.2〜AC2.2.11・AC2.2.13 | 7・9・13・15・18・19 |
| US2.3 行を削除する | AC2.3.2〜AC2.3.6 | 9・13・18・22 |
| US2.4 入力の誤りをその場で直す（サーバーの側） | AC2.4.1〜AC2.4.8 | 7・8・13・15・18 |
| US2.5 同じ値の重なりを防ぐ | AC2.5.1・AC2.5.2 | 9・10・18・22 |
| US3.4 業務データの操作を監査で追える | AC3.4.1〜AC3.4.4・AC3.4.6〜AC3.4.8 | 7・11・12・22・23 |
| US3.1（書き込みの側） | AC3.1.1〜AC3.1.4・AC3.1.6・AC3.1.9・AC3.1.10 | 7・13・19 |
| US3.2（書き込みの側） | AC3.2.1〜AC3.2.4・AC3.2.6 | 13・18・19・22 |
| US3.3（書き込みの側） | AC3.3.1〜AC3.3.6・AC3.3.8〜AC3.3.10 | 2・3・5・9・13・19・21 |
| US3.5（書き込みの側） | AC3.5.1・AC3.5.2 | 15・16・19・25 |
| US3.6（書き込みの側） | AC3.6.1〜AC3.6.3・AC3.6.5〜AC3.6.9 | 2・9・10・17・18・20・23・24・26 |
| 横断（定義の API の検証の材料、BR14） | AC2.1.3・AC2.4.1・AC2.4.8 | 7・8・13・15・16 |
| 横断（D5、U1 の R-03） | — | 2・3・9・10・21 |
| 横断（NFR1.4〜NFR1.6・NFR2.6〜NFR2.9・NFR3.4 の測りの用意） | NFR1.4〜NFR1.6・NFR2.6〜NFR2.9・NFR3.4 | 8・24・27 |
| 横断（見本の書き込みのアカウント・文書） | BR13.4・NFR4.3・NFR4.16 | 26・28 |

U2 で受け持たない受け入れ基準（AC2.1.10・AC2.2.1・AC2.2.12・AC2.3.1・AC3.2.5・AC3.3.7 は U3、AC3.1.5・AC3.1.7・AC3.1.8・AC3.2.7・AC3.4.5・AC3.6.4 は U1）は、機能設計の traceability.json のとおり Deferred のまま。

## 7. コミットの区切り

場所・機能ごとに区切り、どのコミットの時点でもコンパイルが通る順にする（`project.md` の学び）。import の向き（`businessdata` → `targetdb`、`audit` → `businessdata.domain`）から、依存される側を先に積む。

| # | 中身 | コンパイルが通る理由 |
|---|---|---|
| C1 | `targetdb` の変更（`TargetDbProperties`・`TargetDbSettings`・`TargetDataSourceConfig`・`TargetWriteDataSourceConfig`・書き込みの口の domain と service）とそのテスト（直した既存のテストと `TargetWriteTransactionManagerInjectionIT` を含む）、`application.yaml`、`.env.example`、`README.md` の環境変数の表の4行 | `QueryTimeout` の作り方を使う既存のテストを同じコミットで直す。読み取りのプールの初期化を足しても、`RowSqlExecutor` の今の `SET` はまだ残り、どちらでも動く |
| C2 | `businessdata.domain`・`businessdata.repository`（書き込みの SQL・分類・型つきのバインド・D5 の `SET` の削除）、その単体テストと性質ベースのテスト、単体テストが使う `businessdata/testsupport` の部品、`spotbugs-exclude.xml`（A3）、`BusinessDataTimeoutIT` の通す数の直し | C1 の `TargetWriteSession` に依存する。D5 で消す口を使うテストを同じコミットで直す |
| C3 | `audit`（種類・理由の値・`UNKNOWN`・3列・組み立て・受け取り・失敗の ERROR）、`V13`、監査のテスト、`BusinessDataBoundaryArchitectureTest` の A1 | C2 の `BusinessDataChangedEvent`・`BusinessDataAuditReason` に依存する。依存が入るコミットで境界テストも直す |
| C4 | `businessdata.service`・`businessdata.web`（`ColumnDefinitionResponse` を含む）、その単体テスト、`BusinessDataReadControllerTest` の直し、`ApiEndpointCounts`、`BusinessDataBoundaryArchitectureTest` の A2 と足す決まり | C1〜C3 に依存する |
| C5 | 書き込みの結合テスト（3種類の DB・認可・本文の上限・時間切れ・設定の欠け・監査・漏えい・トレース・接続の数）と `businessdata/testsupport`・`targetdb/testsupport` の部品 | テストだけ |
| C6 | `docker/targetdb/*/03-writer-account.sh`、`.env.targetdb.example`、`TargetDbSampleInitScriptIT` | 台本とその IT を同じコミットにする |
| C7 | 負荷の試験の台本（`perf/k6/scenarios.js`・`perf/biz/make-biz-seed.sh`・`perf/make-biz-dsl.mjs`・`perf/dsl-timing.sh`・`perf/README.md`） | Java を含まない |
| C8 | `README.md` の「対象DB」の節の書き込みの段落 | 文書だけ |

- コミットのメッセージは日本語で、各コミットの末尾に共著者の行を付ける。
- 区切りの中身が生成の結果で変わるときは、提案のときに理由を添えて示す。

## 8. 統合

| 項目 | 扱い |
|---|---|
| ブランチ | `feature/261016-business-data-views-b4`（`develop` から作る。worktree は使わない） |
| 統合の形 | `develop` へ squash で1コミット（単位が1つの Bolt で、サブモジュールの固定先の更新を含まないため、fast-forward の例外と単位ごとの squash は当たらない） |
| 統合の前の関門 | `caffeinate -dims` で包んだ `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（コンテナの実行環境あり、対象DB のテストが飛んでいない）と、`./gradlew e2eTest` の全件。どちらも流す前後に `pmset` で眠りを確かめる |
| push | 依頼者が行う。AI は push しない |
| 統合の後 | ブランチを消す。CI の結果は Build and Test で試行ごとに記録する |

## 9. 後の段への引き継ぎ

- **Build and Test**
  - verify の時間（Step 29）と CI の時間を並べて記録する（NFR7.4。42 分の線）。
  - E2E の全件の結果（Step 30）。CI は試行ごとに記録する。
- **Performance Validation**
  - `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`・`bizCreateLarge` を、3種類の対象DB で1つずつ流して判定する（NFR1.4・NFR1.5・NFR2.7・NFR2.8・NFR3.4）。MySQL は 2304m（プール 768M）。
  - 対象DB のメモリは「OOMKilled が false、かつ `oom_kill` が 0」で判断し、`memory.peak`・`anon` の最大・`memory.events` の `max`・バッファープールの使用は記録だけ。`anon` の最大が上限の 85% を超えたら依頼者に諮る（基盤の設計のレビューの R-04）。k6 のコンテナの使用量も記録する。
  - NFR2.7 を満たせないとき、MySQL でプールを 10 に上げる案は採らない（NFR 設計 F1: A）。上限を上げる以外の扱いを諮る。
  - 片付ける前に、待ちの時間切れの累計・アプリの `memory.peak`・監査の行の数・内部DB のファイルの大きさを見て記録する（NFR2.9）。
  - MySQL・MariaDB の書き込みの表の種の SQL は、最初の回で通ることと行の数を確かめる（試し走りの範囲は D8）。
- **Observability Setup**
  - 業務データの区画に、書き込みの3本の p95・409 と 503 の数・書き込みのプールの待ちを足す（NFR8.5）。書き込みのプールの系列（`pool="mastersmith-target-db-write"`）は最初に借りるまで出ないため、式を流す前に書き込みを1回行う。
  - 既存の警報（`ms-pool-pending`・`ms-audit-fail`・`ms-audit-slow`・`ms-5xx-ratio`・`ms-forbidden`）が書き込みを拾うかを実測する（NFR8.6）。内部DB の大きさの基準の値（NFR 要件のレビューの R-03）。
- **Deployment Pipeline・Deployment Execution**
  - 基盤の設計 4節の見本の PG への手順の実地（初めての確かめ。README の Step 28 の手順で行う）と、V13 があるための配備の前の内部DB の複写。`.env` の変更の前に `.env` をリポジトリの外へ表示せずに複写する。
  - スモークテストの書き込みが見本の PG の行と監査に残ることを、依頼者に先に伝え、監査の期待の件数に入れる。
  - 戻しはイメージだけ。V13・書き込みのアカウント・`.env` の行は残る（前の版は使わない）。
- **U3（B5・B6）**
  - E2E の書き込みのアカウント（基盤の設計の Q5: A。一時のロール、`SECRET_ENV_NAMES`）と、E2E が見本の PG に届く道（基盤の設計のレビューの R-01。承認の場で U3 の設計で答えた）。
  - 定義の API のカラムの項目は平らに置き、`required`・`nullable` は必ず値を持ち、型に当たらない項目は `null`、`formPart` に `lookup` は来ない。
  - 503・409 の出し分け（D1〜D4 の結果で、`BUSINESS_TARGET_DB_UNAVAILABLE` が「保存されていない」、`BUSINESS_TARGET_DB_TIMEOUT` が「保存されたか分からない」になる場合の範囲が変わる）、413 の文、`PATTERN_TIMEOUT` の文、日時は秒まで、監査の `UNKNOWN` は画面に出さない。

## 10. 依頼者の決定（D1〜D8: A）

依頼者は D1〜D8 のすべてに推奨の A で答えた（`code-generation-questions.md`）。本文の各節は A の形で書いてある。以下は尋ねたときの選択肢の記録。

計画の承認の前に、判断が分かれる8点を依頼者に尋ねる。どれも A を推奨とし、本文は A の形で書いた。A 以外になったら、該当する Step・4節・9節を直してから計画の承認に進む。

### D1. 競合による巻き戻しの code（NFR 設計のレビュー R-01）

同時の書き込みで、デッドロック・直列化の失敗・ロックの待ち切れ（PG 40001・40P01・55P03、MySQL・MariaDB 1213・1205）が起きると、今の表ではどの行にも当たらず 500 になります。どれも書き込みは確定していません。

A. 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（保存されていない、もう一度試せる）にする。監査の理由の値も `BUSINESS_TARGET_DB_UNAVAILABLE`、ログの `reason` は `CONFLICT` で見分ける。新しい code を足さず、U3 の画面の文もそのまま使える。セーブポイントへの戻しが失敗したときは、元の失敗の種類を先に見て分ける（MariaDB の 1213 の後の 1305 も競合として扱う）（推奨）

B. 新しい code 409 `BUSINESS_WRITE_CONFLICT` と監査の理由の値（23 字）を足す。U3 の画面に文を1つ足す

C. 503 `BUSINESS_TARGET_DB_TIMEOUT`（保存されたか分からない）にする

X. Other (please specify)

### D2. 形の誤りの code（NFR 設計のレビュー R-01）

サーバーの検証を通った値を DB が型の形の誤り（PG 22P02・22007、MySQL・MariaDB 22007・22018 など）で拒んだときの code を決めます。検証と型つきのバインドで起きない見込みですが、起きたときに 500 にしないためです。

A. 409 `BUSINESS_SAVE_REJECTED`（カラムを示さない）にする。NFR 設計の Q6: A・F3: A の「DB が拒んだ値」と同じ扱い（推奨）

B. 400 `VALIDATION_FAILED`（`fieldErrors` なし）にする

C. 想定外の失敗（500。メッセージの無い例外）にする

X. Other (please specify)

### D3. 送った後の時間切れの分け方（NFR 設計のレビュー R-03・R-01 の確定の段）

承認済みの BR10.1 は、書き込みの文を送った後の時間切れ・断絶をすべて 503 `BUSINESS_TARGET_DB_TIMEOUT`（監査 `UNKNOWN`）にしています。試し（W7）では、書き込みの文の段で止めると3種類とも保存されず、確定の段で止めたときだけ保存されていました。

A. 書き込みの文（と事前の問い合わせ）の段の時間切れ・断絶は 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（保存されていない、監査 FAILURE）にし、`TIMEOUT` と `UNKNOWN` は確定の段の失敗に限る。確定の段で SQLState の類 23（遅延の制約の違反）が返ったら 409 `BUSINESS_SAVE_REJECTED` にする。BR10.1 との差を記録する（推奨）

B. 確定の段の類 23 だけを `BUSINESS_SAVE_REJECTED` にし、書き込みの文の段の時間切れは承認済みのまま `TIMEOUT`

C. 承認済みのまま（書き込みの文・確定の段とも `TIMEOUT`・`UNKNOWN`）

X. Other (please specify)

### D4. 違反の後の段の時間切れ（NFR 設計のレビュー R-04）

承認済みの BR6.4（と NFR 設計の 2節）は、違反の後のセーブポイントへの戻し・確かめ・最後の巻き戻しの失敗を、時間切れ・断絶を含めて 409 `BUSINESS_SAVE_REJECTED` にしています。対象DB が止まっても 503 の数と `ms-5xx-ratio` に現れません。どの場合も保存されていないことは確かです。

A. その段の時間切れ・断絶（類 08・57014・70100・`SQLTimeoutException`・`SocketTimeoutException`）は 503 `BUSINESS_TARGET_DB_UNAVAILABLE` にし、ほかの失敗は 409 `BUSINESS_SAVE_REJECTED` のままにする。BR6.4 との差を記録する（推奨）

B. 承認済みのまま（すべて 409 `BUSINESS_SAVE_REJECTED`）

X. Other (please specify)

### D5. U1 の R-03 の直し方（読み取りのプールの初期化）

依頼者の決定で、MySQL・MariaDB の `SET time_zone` を、貸すたびではなく接続を作るときに付ける形に切り替えます。読み取りのプールは、DSL の生成・照合の読み取りと共有です。

A. 読み取りのプールの MySQL・MariaDB に `connectionInitSql` で `SET time_zone = '+00:00'` だけを付ける（`sql_mode` は読み取りに要らないため足さない）。書き込みのプールは `time_zone` と `sql_mode`。どちらの文も `TargetDataSourceConfig` の定数に置く。`RowSqlExecutor` の `SET_TIME_ZONE_UTC` と `RowDialect#needsUtcSession`・`RowQueryStage.SESSION_INIT` を消す。貸すたびの通信の待ち（U1 R-04 の形）は変えない（推奨）

B. 読み取りのプールも書き込みと同じ文（`time_zone` と `STRICT_ALL_TABLES`）にする

X. Other (please specify)

### D6. `audit` から `businessdata.domain` への依存（境界テスト）

監査の受け取り（`AuditEventListener`）は業務データの出来事の型を読みます。今の `BusinessDataBoundaryArchitectureTest#nobodyDependsOnBusinessData` は、ほかの機能が `businessdata` に依存することを止めています。

A. `nobodyDependsOnBusinessData` を緩め、`audit` から `businessdata.domain` への依存だけを許す（3節の A1）。契約 C5 の向きで、`role`・`group` などの出来事と同じ形（推奨）

B. 出来事の型を `audit.domain` に置き、`businessdata` が `audit.domain` に依存する。`allowedDependencies` に `audit.domain` を足す変更になり、契約 C5 の向きと逆になる

X. Other (please specify)

### D7. 確定の途中で止める中継のテストを置く DB の種類

`UNKNOWN` の監査と `TIMEOUT` の応答を確かめるため、つないだ後に止まる中継で確定の段を止めます。MySQL・MariaDB は TLS の通信のため文字の目印が見えず、U1 と同じく通したまとまりの数で止めます（ドライバーの版に依る。U1 のレビューの R-05）。

A. 3種類すべてに置く。MySQL・MariaDB の通す数は手元で確かめて記録し（G23 と同じ）、止めた段を診断の出力に残す。verify が 1〜2 分延びる見込み（推奨）

B. PG だけに置く（文字の目印で止める）。MySQL・MariaDB は書き込みの文の段で止める形だけにする

C. 置かず、W7 の試しの結果に頼る

X. Other (please specify)

### D8. 負荷の試験の台本の試し走り（Step 27）の範囲と配備したアプリの扱い

試し走りは判定ではなく、場面と閾値の名前と `checks` を確かめるためのものです。

A. PostgreSQL の1種類だけ、対象DB は既定の設定で、各場面 1 VU・10 秒（`bizCreateLarge` を含む）。配備したアプリは止めない（上限の合計は perf のアプリ 2048 + perf の PG 512 + 配備したアプリ 2048 + 見本の PG 512 + Mailpit 256 = 5,376 MiB で、5,910 MiB に収まる）。MySQL・MariaDB の種の SQL は出すところまでにし、流すのは Performance Validation（U1 の D4 と同じ）（推奨）

B. 3種類すべてで流す。MySQL（2304m）の回は配備したアプリを止める（数分ずつ）

C. 試し走りをせず、`k6 inspect` と compose の確かめだけにする

X. Other (please specify)

## Sources

- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`（Step 1〜2）
- この単位の設計（どれも `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/` の下、承認済み）
  - `functional-design/functional-spec.md`・`rules.md`・`entities.md`・`traceability.json`・`functional-design-questions.md`（Q1〜Q8: A、Requested Changes Feedback、まとめの確認 Looks correct）
  - `nfr-requirements/` の `performance-requirements.md`・`security-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`・`nfr-requirements-questions.md`（Q1〜Q6: A）
  - `nfr-design/` の `logical-components.md`・`reliability-design.md`・`security-design.md`・`performance-design.md`・`scalability-design.md`・`observability-design.md`・`nfr-design-questions.md`（Q1〜Q7: A）・`nfr-design-followup-questions.md`（F1〜F4: A）
  - `infrastructure-design/` の `infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`・`infrastructure-design-questions.md`（Q1〜Q7: A、F1・F2: A）
- U1 で作ったもの: `construction/u1-businessdata-read/code-generation/code-summary.md`・`generation-notes.md`（N1〜N3・G1〜G34）・`code-generation-plan.md`・`unit-test-instructions.md`
- Inception の成果物（どれも `aidlc/spaces/default/intents/261016-business-data-views/inception/` の下）: `requirements-analysis/requirements.md`、`user-stories/stories.md`、`units-generation/unit-of-work.md`・`unit-of-work-story-map.md`、`contract-design/contract-summary.md`（C1・C2・C3・C5・C6・C7）、`delivery-planning/bolt-plan.md`（B4）・`memory.md`（カバレッジの実測）
- 承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/` の `functional-design/memory.md`・`nfr-requirements/memory.md`・`nfr-design/memory.md`・`infrastructure-design/memory.md`・`code-generation/memory.md`
- レビュー（どれも `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/` の下）: `functional-design/units/u2-businessdata-write/43883aac7901769a/1.json`・`7dacbaa3e842d0af/1.json`、`nfr-requirements/units/u2-businessdata-write/00d44a7a2452e6b7/1.json`、`nfr-design/units/u2-businessdata-write/df13e747868fc8fa/1.json`（R-01〜R-09）、`infrastructure-design/units/u2-businessdata-write/4bcccff4724084fb/1.json`（R-01〜R-07）、`code-generation/units/u1-businessdata-read/bb969d769be0cb2e/1.json`（R-01〜R-08）
- Testing Contract: `aidlc engine testing-posture render` の出力（指揮役が保存したもの）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style・Deployment）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated と学び）
- コード（読み取りだけで確かめた）: `backend/src/main/java/cherry/mastersmith/targetdb/`（`config/TargetDataSourceConfig.java`・`TargetDbProperties.java`・`TargetDbSettings.java`、`domain/TargetReadSession.java`・`TargetReadOutcome.java`、`service/JdbcTargetReadAccess.java`）、`businessdata/`（`repository/RowSqlExecutor.java`・`RowSqlFailureClassifier.java`・`RowDialect.java`、`domain/ColumnValidation.java`・`ColumnAccess.java`・`BusinessDataProblemTypes.java`・`BusinessDataFieldErrorReason.java`・`RowQueryStage.java`、`web/ColumnDefinitionResponse.java`・`BusinessDataWebConfig.java`・`BusinessDataPaths.java`・`BusinessDataReadController.java`）、`audit/`（`domain/AuditResult.java`・`AuditEvent.java`、`service/AuditEventListener.java`、`repository/AuditEventRepository.java`）、`common/web/RequestBodyLimitRoute.java`・`RequestSizeLimitFilter.java`、`backend/src/main/resources/application.yaml`・`db/migration/`（V1〜V12）、`backend/build.gradle.kts`（`packagesJudgedByTotal`）、`backend/config/spotbugs-exclude.xml`、`backend/src/test/java/cherry/mastersmith/` の `businessdata/BusinessDataBoundaryArchitectureTest.java`・`targetdb/TargetDbBoundaryArchitectureTest.java`・`audit/AuditBoundaryArchitectureTest.java`・`audit/domain/AuditMigrationCompatibilityIT.java`・`AuditEventTest.java`・`AuditRoleEventFactoryTest.java`・`audit/service/AuditSecretLeakIT.java`・`common/testsupport/ApiEndpointCounts.java`・`businessdata/web/BusinessDataTimeoutIT.java`・`businessdata/testsupport/CountingDataSourceConfig.java`・`StallingRelay.java`、`compose.yaml`・`docker/perf/compose.yaml`・`docker/targetdb/`・`.env.example`・`.env.targetdb.example`・`perf/`（`k6/scenarios.js`・`biz/make-biz-seed.sh`・`dsl-timing.sh`・`README.md`）・`frontend/playwright.config.ts`・`frontend/e2e/`

## Assumptions & Open Questions

- [assumption] 機能設計と NFR 設計の部品の名前は仮の名前で、生成で変えるときは generation-notes.md に差を記録する（書き込みのメソッドの名前が変われば、A2・A3 の名前もそれに合わせ、提案のときに示す）。
- [assumption] D1〜D8 は A の形で本文を書いた。A 以外になったら、計画の承認の前に該当の Step を直す。
- [assumption] 名前つきの書き込みの管理者を既定の候補にしなければ、内部DB の管理者は退かない（既存の `targetDataSource` で同じ仕組みが効いている）。Step 3 の結合テストで確かめ、外れたら生成を止めて依頼者に諮る（NFR 設計のレビューの R-06）。
- [assumption] MariaDB のドライバーで件数を「一致した行」に固定する設定の名前は、生成でドライバーの文書とソースで確かめる（既定は一致した行。W4）。
- [assumption] 競合の結合テストは、テストの管理者の接続で行を排他し書き込みのアカウントの待ちの上限を短くした形（PG は書き込みのアカウントの `lock_timeout`、MySQL・MariaDB は `innodb_lock_wait_timeout`）と、トリガーで番号を出す形（PG の `RAISE … USING ERRCODE`、MySQL・MariaDB の `SIGNAL`）で起こす。トリガーの形は、MariaDB の本物のデッドロックの「全体が巻き戻る」振る舞いを再現しないため、その経路は分類の部品の単体テストで確かめる。形は生成で決め、generation-notes.md に記録する。
- [assumption] 書き込みの結合テスト（3種類）・見本の初期化の台本の IT（コンテナ3つ）・中継（D7）で、verify は数分延びる見込み（U1 の B2 の後は 18 分 32 秒）。Step 29 で実測し、42 分の線を超えたら依頼者に諮る。
- [assumption] 配備した環境（アプリ・見本の PG・`.env`・`.env.targetdb`）には触れない。見本の PG への書き込みのアカウントの適用は Deployment Execution で行う。
- [assumption] `.env.targetdb` を持つ人が新しいボリュームを作り直すと、書き込みのパスワードを足すまで初期化が止まる（03 の台本の exit 1）。README と `.env.targetdb.example` に書く。
- Open: 生成の途中で、承認の範囲（3節の A1〜A3）を超える変更や、`packagesJudgedByTotal` の7パッケージの本体のソースへの変更、計画に無い既存のテストの直しが要るとわかったら、生成を止めて依頼者に諮る。
