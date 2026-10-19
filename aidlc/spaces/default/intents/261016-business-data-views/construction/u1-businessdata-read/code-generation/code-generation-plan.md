# コード生成の計画 — U1 u1-businessdata-read（Bolt B2）

U1 は、新しい機能 `businessdata` の読み取りの側である。定義・一覧と検索・詳細の3本の API（`POST /api/tables/definition`・`POST /api/tables/rows/search`・`POST /api/tables/rows/get`、どれも「ログインだけ」）と、出し分けの純粋な関数、行の SQL を組み立てて実行する唯一の部品（`RowSqlExecutor`）と3種類の対象DB の方言、`targetdb.service` の貸す形の読み取りの口を作る。内部DB の表・移行は足さない。画面（frontend）には触れない。

この計画は Part 1（計画）である。依頼者が計画を承認するまで、コードを書かず、ブランチも作らない。

- コード生成の進め方（`aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md` の Deviations）: 単位ごとに U1 → U4 → U5 → U2 → U3 の順で作る。U1 が最初の単位で、Bolt B2 にあたる。B7 は U3 の最後に入るため、U1 には関わらない。
- 作業ブランチ: `develop` から作る `feature/261016-business-data-views-b2`。`develop` へ squash で1コミットにして戻す（`team.md` の Way of Working、`bolt-plan.md` の B2）。

## 1. 影響の範囲

### 1.1 新しく作るもの（`backend/` の下）

| 置き場 | 主なクラス（名前は設計の仮の名前。変えるときは generation-notes.md に記録する） |
|---|---|
| `businessdata`（`package-info.java`） | 機能の説明 |
| `businessdata.domain` | `DefinitionId`・`TableAccessView`・`ColumnAccess`・`ColumnValidation`（U2 が埋める置き場。U1 は null）・`KnownIdentifiers`・`AllowedOperation`・`RestrictionReason`・`ValueType`・`BoundValue`・`RowKey`・`RowValues`・`SearchCondition`・`PageRequest`・`SortKey`・`RowQueryFailure`（理由・段階・SQLState・業者の番号・例外のクラスの名前だけ）・`BusinessDataFieldErrorReason`（`NOT_ALLOWED`・`INVALID_VALUE`・`RANGE_REVERSED`・`NOT_A_CHOICE`・`KEY_MISMATCH`）・`BusinessDataProblemTypes`・`TableAccessPolicy`・`SearchOperatorPolicy`・`SearchRequestValidator`・`ColumnValueReader`（型として読む口。実装は repository の `ColumnValueCodec`）・`ContainsPattern`（部分一致のエスケープの純粋な関数） |
| `businessdata.repository` | `ColumnValueCodec`・`RowDialect`（`PostgresRowDialect`・`MysqlFamilyRowDialect`）・`RowSqlExecutor`・`RowSqlFailureClassifier`・`RowSearchResult`・`RowDetailResult`（結果の型） |
| `businessdata.service` | `TableAccessResolver`・`BusinessDataReadService`・`BusinessDataProblemTypeCatalog`・結果の型（`DefinitionResult`・`SearchResult`・`RowReadResult`）・`BusinessDataUnexpectedException`（メッセージも原因も持たない） |
| `businessdata.web` | `BusinessDataReadController`・要求と応答の record（`DefinitionRequest`・`SearchRequest`・`RowGetRequest`・`TableDefinitionResponse`・`ColumnDefinitionResponse`・`SearchFieldResponse`・`RowPageResponse`・`RowItemResponse`・`RowDetailResponse`）・`BusinessDataFieldErrors`・`BusinessDataWebConfig`（道ごとの本文の上限 64KB を3つ） |
| `targetdb.domain`（足す） | `TargetReadSession`（record。DB の種類・接続・問い合わせの上限の秒）・`TargetReadOutcome`（`Done`・`NotConfigured`・`Unavailable`） |
| `targetdb.service`（足す） | `TargetReadAccess`・`JdbcTargetReadAccess` |
| テスト（`backend/src/test/java/cherry/mastersmith/businessdata/` の下） | 6節の一覧と `unit-test-instructions.md` |
| `businessdata/testsupport` | 接続を数える包み（既存の `navigation/testsupport/ConnectionCountingDataSource` と同じ形）・つないだ後に止まる中継（`StallingRelay`）・業務データのテストの DSL と表を作る道具・認可の表の利用者とロールを作る道具 |
| `perf/` | k6 の場面（`perf/k6/scenarios.js` に足す）・種を作る台本（`perf/biz/` の下。3種類の DB の SQL を出すシェル）・業務データの DSL を作る台本（`perf/make-biz-dsl.mjs`） |

### 1.2 変える既存のファイル

| ファイル | 変えること | 根拠 |
|---|---|---|
| `backend/src/main/java/cherry/mastersmith/targetdb/domain/ReadPurpose.java` | `LIST`・`DETAIL` を足す | BR8.2、NFR4.4 |
| `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java` | `QueryTimeout` に `list`・`detail`（既定 5 秒）を足す | NFR4.4、基盤の設計 1.1 |
| `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbSettings.java` | 項目の名前の定数、点検（1 秒以上）、`queryTimeout(purpose)` の `switch` に2つを足す | NFR4.4 |
| `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java` | `driverProperties` の通信の待ちを「4つの上限の最大 + 5 秒」で求める | 基盤の設計のレビュー R-01 |
| `backend/src/main/java/cherry/mastersmith/role/domain/PermissionSnapshot.java` | 写しの DSL を読み取りだけで渡す口 `Optional<DslModel> dsl()` を足す | BR3.1・BR11.3（承認が要る。3節） |
| `backend/src/test/java/cherry/mastersmith/role/RoleBoundaryArchitectureTest.java` | `role` に依存してよい相手に `businessdata`（`role.service`・`role.domain` だけ）を足す | BR11.3・ADR-002（緩める変更。承認が要る。3節） |
| `backend/config/spotbugs-exclude.xml` | `RowSqlExecutor` のクラス・メソッド・パターン名の3つで絞った `SQL_` の除外 | BR11.4（承認が要る。3節） |
| `backend/src/test/java/cherry/mastersmith/common/testsupport/ApiEndpointCounts.java` | `MIN_ENDPOINTS` を 67 から 70 に上げる（口を足したら上げる決まり） | 既存のテストの説明 |
| `backend/src/main/resources/application.yaml` | `query-timeout` の下に `list`・`detail` を環境変数で受ける2行 | 基盤の設計 1.1（Q4: A） |
| `.env.example` | `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST`・`_DETAIL` のコメントの行と、待ちの上限の説明の行（55 行）の直し | 基盤の設計 1.1、レビュー R-01 |
| `README.md` | 環境変数の表（475〜476 行の後）に2行、「対象DB（利用者の業務データの DB）」の節に業務データの読み取りの API の1段落 | 基盤の設計 1.1 |
| `docker/perf/compose.yaml` | 3つの対象DB に、設定とメモリの上限を環境変数で受ける口（既定の値は今のまま） | 基盤の設計 2.1（Q1: A・Q2: A） |
| `perf/README.md` | 「業務データの場面（Intent 261016-business-data-views の U1）」の節 | 基盤の設計 5.2 |
| `perf/dsl-timing.sh` | 始めに業務データの試験の変数を既定の値に戻す1行（`unset`） | 基盤の設計のレビュー R-04 |
| `perf/k6/scenarios.js` | 業務データの場面（4節） | NFR1.1・NFR1.2・NFR2.5 |

`TargetDbBoundaryArchitectureTest`・`ArchitectureTest`・`ApiAccessArchitectureTest`・`docker/monitoring/`・`.github/workflows/ci.yml`・`backend/build.gradle.kts`（`packagesJudgedByTotal` を含む）・frontend は **変えない**（基盤の設計の cicd-pipeline.md 7節の (vii)）。

### 1.3 影響を受ける既存のテストと E2E（検索の結果）

`ReadPurpose`・`QueryTimeout`・`TargetDbSettings`・`TargetDbProperties`・`driverProperties`・`PermissionSnapshot`・`ApiEndpointCounts`・`/api/tables` で `backend/src` と `frontend/` を検索した（project.md の学び）。

| テスト | 影響 | 扱い（Step） |
|---|---|---|
| `targetdb/domain/TargetSchemaResultTest`（91 行） | `ReadPurpose.values()` を `GENERATE`・`COMPARE` の2つちょうどと確かめている | 4つにする（Step 5） |
| `targetdb/service/AbstractTargetSchemaReaderIT`（214 行） | `ReadPurpose.values()` のすべてでスキーマを読む。`LIST`・`DETAIL` でも読んでしまう | スキーマの読み取りの目的（`GENERATE`・`COMPARE`）だけを並べる形に直す（Step 7） |
| `targetdb/config/TargetDbSettingsTest`（140〜150 行） | `new QueryTimeout(Duration, Duration)` の2つの引数で作っている | 4つの引数に直し、`list`・`detail` の点検の境界を足す（Step 7） |
| `targetdb/service/TargetSchemaReaderTest`（79〜80 行・170 行） | 同上 | 4つの引数に直す（Step 7） |
| `targetdb/config/TargetDbPropertiesTest`（64〜68 行） | 既定値を確かめている | `list`・`detail` の既定 5 秒を足す（Step 7） |
| `targetdb/config/TargetDataSourceConfigTest`（90〜100 行） | 通信の待ちの値を確かめている | 既定のままの値で通ることと、`list` か `detail` を 20 秒より大きくしたときの値を足す（Step 7） |
| `targetdb/config/TargetDbSecretLeakIT`・`TargetDbStartupIT` | 設定の文字列と起動の確かめ | 変えずに流して確かめる（Step 7・Step 24） |
| `dslmanage/generate/TargetSchemaDslGeneratorTest`・`AbstractDefaultDslGeneratorIT`・`dslmanage/service/DslLifecycleTest` | `ReadPurpose.GENERATE`・`COMPARE` を使う | 変えずに流して確かめる（Step 24） |
| `role/domain/PermissionSnapshotTest` | 写しの口を足す | `dsl()` のテストを足す（Step 5） |
| `role/RoleBoundaryArchitectureTest` | 依存してよい相手を足す | 承認を得て直す（Step 16） |
| `ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`（`ApiEndpointCounts.MIN_ENDPOINTS`） | 口が 3 本増える | 下限を 70 に上げる（Step 14） |
| `common/error/service/ProblemTypeDuplicateStartupIT`・`ProblemTypeRegistryTest` | 問題の種類の一覧が増える | 変えずに流す（code の重なりが無いことの確かめ。Step 14） |
| E2E（`frontend/e2e/`） | `/api/tables` を使う E2E は無い。`170-menu-navigation-flow` と `support/frameApiRoute.ts` が使うのは `/api/me/table-access`（変えない） | E2E のファイルは足さず、直さない。統合の前に全件を流す（Step 25） |
| frontend の単体テスト（`features/tables/tableAccessApi.test.ts`・`TablePlaceholderPage.test.tsx`） | `/api/me/table-access` を使う（変えない） | 影響なし（frontend の書き換えは U3） |

### 1.4 カバレッジの今の値と手を入れるパッケージ

Delivery Planning の実測（2026-10-18、`:backend:cleanTest` から `:backend:jacocoTestReport`。全体の合計 行 98.0%・分岐 92.6%）の報告から、この Bolt で手を入れるパッケージの値を読んだ。

| パッケージ | 行 | 分岐 | `packagesJudgedByTotal` | 扱い |
|---|---|---|---|---|
| `targetdb.config` | 99.2% | 98.5% | 無い | パッケージごとの下限の対象のまま。足す分のテストを書く |
| `targetdb.domain` | 100.0% | 96.9% | 無い | 同上 |
| `targetdb.service` | 100.0% | 100.0% | 無い | 同上（`JdbcTargetReadAccess` の分岐をすべてテストで通す） |
| `role.domain` | 94.2% | 85.7% | 無い | 同上 |
| `businessdata.*`（新しい） | — | — | 無い | 自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる |

- `packagesJudgedByTotal` の 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）には **手を入れない**。`definitionChanged` は `BusinessException` の追加の項目（既存の口）で載せ、ACCESS_DENIED は `ProblemTypeRegistry` から code で引き、本文の上限は既存の `RequestBodyLimitRoute` を Bean で足すだけで、どれも本体のソースを変えずに済む。生成の途中でこの 7 パッケージの本体のソースに手が要るとわかったら、生成を止めて依頼者に諮る（手を入れるなら、その Bolt で下限を満たして一覧から外す決まり）。
- 計測の除外は足さない。

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

Contract の層の読み替え: この単位は内部DB の表と画面を持たないため、`Data model / database behavior` は値の型（domain）と、`targetdb` の設定の型に読み替える。`Frontend behavior` は当たらない（画面は U3）。methodology（test-after）と ordering（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）はそのまま守る。

## 3. 依頼者の承認を得ること

計画の承認（Approve Plan）で、次の3つの承認もあわせて得る。生成の途中で、ここに書いた範囲を超える変更が要るとわかったら、生成を止めて依頼者に諮る。

| # | 変更 | 中身 | 緩める点 |
|---|---|---|---|
| A1 | `RoleBoundaryArchitectureTest` の `onlyAuditAndNavigationDependOnRole` | 依存してよい相手に `cherry.mastersmith.businessdata..` を足し、`businessdata` が使ってよいのは `role.service`・`role.domain` だけと書く（`role.web`・`role.repository`・`role.store`・`role.transfer` は使わない）。規則が依存を見分けていることの確かめ（`dependsOnPackage`）に `businessdata` → `role.service` を1行足す。テストの名前と `@DisplayName` を3つの相手の形に直す | `role` に依存できる機能が1つ増える（ADR-002・BR11.3） |
| A2 | `backend/config/spotbugs-exclude.xml` に `SQL_` の除外を1つ足す | `<Class name="cherry.mastersmith.businessdata.repository.RowSqlExecutor"/>` と、`prepareStatement` を呼ぶメソッド（読み取りの `search`・`detail` の2つ）と、実際に報告されたパターン名（見込みは `SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING` と FindSecBugs の `SQL_INJECTION_JDBC`）の3つで絞る。理由に「識別子は権限の写しの DSL の種類ごとの名前の一覧と照らしたものだけを `SqlIdentifiers.quoteKnown` で引用し、値はすべてバインドする。部品は1つで、ArchUnit で `prepareStatement`・`createStatement` を呼べる場所をこの部品だけに絞る」と書く。パッケージや `SQL_` の全体では外さない。報告されたパターン名・メソッドの名前が見込みと違うときは、足す前に依頼者に見せる | SpotBugs の関門の対象から、この部品の2つのメソッドの指摘を外す（BR11.4・team.md） |
| A3 | `PermissionSnapshot` に `Optional<DslModel> dsl()` を足す | 写しが持つ DSL のモデル（不変の record）を読み取りだけで返す。判定と SQL の識別子の照合を同じ DSL で行うため（契約 C7、BR3.1） | 既存の口の形に足す（契約 C7 の「形を変えない」との差。機能設計の X6 で決めたもの） |

`TargetDbBoundaryArchitectureTest` は変えない（足す口は書き込みも `JdbcTemplate` も `@Transactional` も使わず、DSL にも web にも依存しない。`setNetworkTimeout` は文を作らない。`SET time_zone` は `businessdata.repository` の `RowSqlExecutor` が流す）。`BusinessDataBoundaryArchitectureTest` は新しく足す境界テストで、緩める変更ではない。

## 4. 反映した決定（承認の場の決定と、設計の文書との差）

承認済みの設計の文書は書き換えず、計画で直す（project.md の学び）。

### 4.1 NFR 設計の承認の場（各単位のレビューの Major は、レビューの推奨の方向でコード生成の計画で直す）

U1 のレビューは `.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json`。

| 指摘 | 重さ | この計画での直し方 | Step |
|---|---|---|---|
| R-01 読む側の型の誤りの SQLState を 400 にしない | Major | `RowSqlFailureClassifier` の入力に段階（`BIND`・`EXECUTE`・`READ`）を足す。値の型の誤りの SQLState を 400 `VALIDATION_FAILED` に写すのは、バインドと実行の段階（要求の値が原因になりうる段階）だけにする。結果を読む段階（`ResultSet` の値の読み取りと `ColumnValueCodec` の変換）の失敗は 400 にせず、依頼者の決定 **D1: A** のとおり 409 `BUSINESS_TARGET_DB_MISMATCH` にする（新しい code は足さない。ログは WARN で理由 `READ_CONVERSION`）。MySQL・MariaDB のゼロ日付（`0000-00-00`）と範囲の外の値を持つ行を、境界の値の結合テストに足す | 8・9・15 |
| R-02 非検査の例外と `Done<T>` の toString の経路 | Major | (1) `JdbcTargetReadAccess.withRead` は `work.apply` が投げた `RuntimeException` も受け、メッセージも原因も持たない `TargetReadUnexpectedException`（例外のクラスの単純名だけを持つ）に替えて投げ直す。(2) `RowSqlExecutor` の各メソッドは `SQLException` に加えて `RuntimeException` も受け、`RowQueryFailure`（理由 `UNEXPECTED`）にする。(3) `ColumnValueCodec` の値の読み取りと型として読む処理は、`NumberFormatException`・`DateTimeParseException`・`ArithmeticException` などをメソッドの中で受けて結果の型にする（例外を外へ出さない）。(4) `TargetReadOutcome.Done` の `toString` を上書きし、値の型の単純名だけを出す。(5) `BusinessDataSecretLeakIT` に、不正な数・日付の文字（非検査の例外の経路）と、`Done` を通る戻り値（TRACE の EXIT の行）の場合を足す | 2・8・9・10・12・17 |
| R-03 `SET time_zone` を流す所と失敗の扱い | Minor | `TargetDbBoundaryArchitectureTest` が `targetdb` の中の `execute` を止めているため、`withRead` では流さない。`RowSqlExecutor` の読み取りのメソッドを、1回の貸し出しで1回だけ呼ぶ形（一覧は件数とページを1つの `search` で読み、詳細は `detail`）にし、そのメソッドの始めに1回だけ流す（貸し出しごとに1回）。`SET` の失敗は、分類の表によらず `UNAVAILABLE` にする。接続の数の IT で、文が増えても接続が増えないことを確かめる | 8・18 |
| R-04 戻す処理の失敗 | Minor | HikariCP 7.0.2 のソースを読み取りで確かめた: `ProxyConnection.setNetworkTimeout` は dirty の印を付け、`close()` の `PoolBase.resetConnectionState` が通信の待ちを元の値に戻す。ただし戻す処理が失敗して接続が捨てる印になっていないと、`close()` は例外を投げつつ `recycle()` でプールに返す。そのため口は自分でも `finally` の中で元の値に戻し、戻す処理が失敗したら `HikariDataSource.evictConnection` で明示して捨ててから閉じる（WARN は例外のクラスの名前だけ）。借りるたびに通信の待ちを付け直すため、次の業務データの借り手には響かない | 10・12・19 |
| R-05 主キー以外の並べ替えの深いページ、「3 秒」の記述 | Minor | k6 に `bizSearchLastSorted`（主キー以外のカラムで並べ替えた最後のページ）を足す。依頼者の決定 **D2: A** のとおり、並べ替えのカラムに索引を作らず、悪い側の条件で測る（目標を超えたら Performance Validation で Not Met とし、扱いを諮る。目標は緩めない）。「3 秒」は、コードのコメントと README で「3 秒（MySQL で確かめが走るときは 6 秒）」と書く | 21 |
| R-06 部分一致の p95 | Minor | F1: A のまま、アプリの作りと検索の対象は変えない。部分一致が表の設定を直しても 1 秒を超える見込みが高いことを、code-summary.md の Performance Validation への引き継ぎに書く（その段で Not Met として扱いを諮る。目標は緩めない） | 26 |

設計の文書と承認の場で決めた差のうち、この計画で作りに入れるもの:

| 差 | 入れる所 | Step |
|---|---|---|
| page の上限 1〜1,000,000、読み飛ばす行の数は long（Q4: A。BR5.1 との差） | `SearchRequestValidator` | 4 |
| CHOICE の重複を除き、除いた後の数が選択肢の数を超えたら `NOT_A_CHOICE`（Q6: A。BR4.5 との差） | 同上 | 4 |
| 検索の値と主キーの値は1つ 1,000 コードポイントまで、超えたら `INVALID_VALUE`（NFR4.5。BR1.6 との差） | 同上 | 4 |
| 時差つきの型の値はオフセットが無ければ UTC、`Z`・`±hh:mm` も受け付けて UTC にそろえる（NFR3.3。BR4.7 との差） | `ColumnValueCodec` | 8 |
| BR8.6 の表に MySQL の HY000/1525 を足す | `RowSqlFailureClassifier` | 8 |
| BR7.9 を DB と型ごとに当てる（PG の `json`・`xml`・幾何の型、MySQL の `json` を EQUALS から外し、主キーに含むテーブルの DETAIL を許さない）。対象DB の設定が無く DB の種類がわからないときは、3種類の一覧の和で外す（狭める向き） | `RowDialect`（一覧）・`TableAccessPolicy`（当て方） | 4・8 |
| MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型を BINARY と同じ扱い（F3: A。BR7.1 との差） | `ColumnValueCodec` | 8 |
| 貸すたびに `setNetworkTimeout`（目的の上限 + 5 秒 = 10 秒）を付けて戻す（F2: A） | `JdbcTargetReadAccess` | 10 |
| 深いページは主キーを先に読んでからつなぐ形（複合の主キーを含む） | `RowDialect` | 8 |
| NFR4.8 の「約 13 秒」は「25 秒以内に 503」に読み替え（NFR 要件の承認の場の U1 R-01） | 時間切れの IT の合格の線 | 19 |
| BR8.5・BR11.2 に足す1行（例外は JDBC を呼ぶメソッドの中で受ける。`TargetReadSession` は `targetdb.domain`、口は `targetdb.service`） | 部品の置き場と境界テスト | 2・10・16 |

### 4.2 基盤の設計の承認の場

U1 の基盤の設計のレビューは `.aidlc-reviews/infrastructure-design/units/u1-businessdata-read/4bcccff4724084fb/1.json`。

| 決定・指摘 | 重さ | この計画での直し方 | Step |
|---|---|---|---|
| perf の MySQL の値 | 決定 | U1 の文書の 1792m（プール以外 約 614 MiB）は直さず、U2 の試しの結果の **2304m（バッファープール 768M）** を正とする。`docker/perf/compose.yaml` の口の既定は今の値（768m）のまま、README の業務データの節で export する値を 2304m にする。VM に収まるかの1行目（perf のアプリ 2048 + MySQL 2304 + 見本の PG 512 + Mailpit 256）は 5,120 MiB で、5,910 MiB に収まる | 21 |
| R-01 通信の待ちの計算に list・detail が入らない | Major | `TargetDataSourceConfig.driverProperties` の通信の待ちを「generate・compare・list・detail の上限の最大 + 5 秒」にする。上限の上限の値は置かない。`list` か `detail` を 20 秒より大きくしたとき（例 30 秒 → 35 秒）の単体テストを足す。`.env.example` の 55 行の説明を直す | 6・7・23 |
| R-02 k6 の setup の前提の担当 | Major | B2 の作業とする。準備の場面 `bizPrepare`（1 VU・1 回）が、100 カラムの表を含む DSL の投入と適用、ロールの作成とテーブルの READ の権限の設定、試験用の利用者への割り当て、1人ずつログインしての作業ロールの保存を API で行う。各場面の `setup()` は前の場面の状態に頼らず、VU ごとのアクセストークンを取り、前提がそろっているか（定義の API が 200 か）を確かめてから始める。種のスキーマへの読み取りのアカウントの GRANT は、種を入れる SQL の台本が管理者で行う。用意の操作は使い捨ての環境の監査に残ることを README に書く | 21 |
| R-03 表の大きさと並べ替えの索引 | Minor | **D2: A** のとおり、並べ替えのカラム（主キー以外の1つ）には索引を作らない。表の大きさは主キーの索引だけの形で変わらず、その形で `bizPrepare` の後に表の大きさ（データと索引）を測ることを README の手順に書く。PG は最初から 384MB の側に上げる案は採らず、基盤の設計 2.2 の決まり（80% を超えたら 128MiB ずつ上げる）で扱う | 21 |
| R-04 compose の確かめ方と `dsl-timing.sh` | Minor | 確かめは、ダミーの `MASTERSMITH_PERF_ENV_FILE` と3つの `--profile` を付けて `docker compose -f docker/perf/compose.yaml config` を読み、`command` と `mem_limit` が今の値と同じことを見る形にする。`perf/dsl-timing.sh` の始めで業務データの試験の変数を `unset` する | 21・22 |
| R-05 VM の表の数 | Minor | 承認済みの文書は書き換えず、行 3 が 6,144 MiB（1792m のとき）であることと、2304m では 6,656 MiB になることを generation-notes.md に差として書く。k6 のコンテナの使う量は、Performance Validation で `bizSearchWide` を流すときに記録する（引き継ぎ） | 26 |
| R-06 基盤の traceability の扱いのそろい | Minor | この段の `traceability.json` では、テストだけが根拠の項目にも、実装の部品（設定の口・プール・ログの口など）か、テストのクラスのどちらかを必ず target に書き、N/A は「作業が無い」項目だけにそろえる | 26 |
| R-07 プールの指標のラベル | Minor | 対象DB のプールの指標は `pool="mastersmith-target-db"` で、プールは最初に借りるときに作られるため系列も最初の借用まで出ないことを、code-summary.md の Observability Setup への引き継ぎに書く（先に API を1回呼んでから式を流す） | 26 |

### 4.3 そのほかの段の承認の場

| 段 | 決定 | この計画での扱い |
|---|---|---|
| 機能設計（Request Changes） | U1 の R-01〜R-06・U2 R-02 は承認済みの文書で直っている。定義の API のカラムの項目は `ColumnDefinition` の形で、U1 は読み取りの項目だけを返し、`nullable`・`formPart` などの検証の材料は U2（B4）が足す | Step 13 の応答の組み立ては「`validation` があれば項目に写す」形にし、U1 では項目を出さない（BR2.8） |
| NFR 要件 | U1 R-01「通信の待ち（25 秒）以内に 503」、U1 R-02「page の上限と桁あふれしない計算」 | 4.1 の表のとおり |
| コード生成（進め方） | U1 → U4 → U5 → U2 → U3、B7 は U3 の最後 | U1 を最初に作る。U2 が使う部品（`RowSqlExecutor`・`ColumnValueCodec`・`RowSqlFailureClassifier`・`TableAccessPolicy`・`TargetReadAccess` の形）は、U2 が書き換えずに足せる形にする |

### 4.4 計画で決めたこと（設計の文書が計画に回したもの）

| 論点 | 決めたこと | 理由 |
|---|---|---|
| `RowSqlExecutor` が問い合わせの上限の秒を知る方法 | `TargetReadSession` に「目的の上限の秒」を持たせる（`entities.md` の `TargetReadSession` との差） | `businessdata` は `targetdb.config` に依存できない（`TargetDbBoundaryArchitectureTest` の `configAndRepositoryStayInside`） |
| 定義の API が DB の種類を知る方法 | `TargetReadAccess` に、接続せずに設定から DB の種類を返す `Optional<DatabaseProduct> product()` を足す | 定義は対象DB に接続しない（BR8.4）が、BR7.9 と F3 の型の扱いは DB の種類で決まる |
| `setNetworkTimeout` の executor | `JdbcTargetReadAccess` が1つの daemon のスレッドの executor を持ち、Bean を閉じるときに止める | 設計の L9（口が持つ1つのスレッドプール）。MySQL・MariaDB のドライバーは同期で呼ぶため、ほぼ使われない |
| `QueryTimeout` の作り方の口 | 4つの引数の record にし、2つの引数の作り方は足さない。既存のテストを4つの引数に直す | 既定値の補いを1か所にする |
| 結果の型の置き場 | 業務処理の層の結果は sealed interface の record（team.md）。web の層が `switch` で場合を尽くして `BusinessException` に変える | team.md の Code Style |
| ログの1か所 | `BusinessDataReadService` が結果を code に変える所で WARN を1行（キー `purpose`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`）。口と `RowSqlExecutor` はログを出さない（閉じる・戻す処理の失敗の WARN だけ） | NFR8.2・監視の設計 1節 |
| 業務データの境界テストのトランザクションの規則 | `businessdata.web`・`domain`・`repository` と、読み取りのクラス（`BusinessDataReadService`・`TableAccessResolver`）は `@Transactional`・`TransactionTemplate`・`JdbcTemplate` を使わない、と書く（`businessdata` の全体を止める形にしない） | U2 が対象DB 用のトランザクションの管理者を書き込みのクラスに足すときに、この規則を緩めずに済むようにする |

## 5. Step（実装 → その層のテスト → 実行）

どの Step も、コードのコメント・Javadoc は日本語、テストの説明文（`@DisplayName`）は英語、ファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。テストデータの値は架空の値だけにし、メールアドレスは `example.com` などの予約のドメインだけにする。

### 準備

- [x] **Step 1: 作業の準備と走らせ方の確かめ**
  - `aidlc/` の下に未コミットの変更が無いことを確かめる（あれば依頼者にコミットの承認を得てから進む。project.md の Change Control）。
  - git の対象外の生成物（`frontend/playwright-report`・`frontend/test-results`）が残っていれば消す（project.md の学び）。
  - `develop` から `feature/261016-business-data-views-b2` を作って移る。
  - 走らせ方の確かめ（brownfield。最初のテストの Step の前）: `unit-test-instructions.md` の「走らせ方の確かめ」のコマンドで、既存の `targetdb` の単体テストが通ることを確かめて記録する。colima が動いていることと、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡すことも確かめる。

### 値の型と domain の層（Contract の Data model と Business logic の純粋な部分）

- [x] **Step 2: `targetdb.domain` の実装**
  - `ReadPurpose` に `LIST`（一覧。件数とページのそれぞれ）と `DETAIL`（詳細）を足す。
  - `TargetReadSession`（record。`DatabaseProduct product`・`Connection connection`・`int queryTimeoutSeconds`）。`toString` は DB の種類と秒だけを出す（BR10.1）。保存しない・戻り値にしない決まりを Javadoc に書く。
  - `TargetReadOutcome`（sealed interface）: `Done<T>(T value)`（`toString` は値の型の単純名だけ。R-02）・`NotConfigured`・`Unavailable(String sqlState, Integer vendorCode, String exceptionClass)`（メッセージと原因を持たない）。
- [x] **Step 3: `role.domain` の実装**
  - `PermissionSnapshot` に `Optional<DslModel> dsl()` を足す（A3）。`toString` は変えない。
- [x] **Step 4: `businessdata.domain` の実装**
  - 値の型（1.1 の表）。値を持つ型（`BoundValue`・`RowKey`・`RowValues`・`SearchCondition`）の `toString` はカラムの名前と件数だけ（BR10.1）。行を `Map` のまま層をまたいで渡さない。
  - `BusinessDataProblemTypes`（BR1.9 の8つ。`BUSINESS_DSL_NOT_APPLIED` 404・`BUSINESS_TABLE_NOT_FOUND` 404・`BUSINESS_ROW_NOT_FOUND` 404・`BUSINESS_ROW_OPERATION_NOT_ALLOWED` 409・`BUSINESS_ROW_NOT_UNIQUE` 409・`BUSINESS_TARGET_DB_MISMATCH` 409・`BUSINESS_TARGET_DB_UNAVAILABLE` 503・`BUSINESS_TARGET_DB_TIMEOUT` 503。D1: A のとおり新しい code は足さず、結果を読む段の型の変換の失敗も `BUSINESS_TARGET_DB_MISMATCH` を使う）。説明文に値・名前を載せない。
  - `TableAccessPolicy`（BR2.1〜BR2.7。出すカラム・入力の可否・許す操作・行を見分ける値のカラム・理由。BR7.9 と BINARY の主キーの DETAIL の取り下げを含む）。
  - `SearchOperatorPolicy`（BR4.6 の読み替えの後に BR4.9 の表を当て、表に無い組は検索の項目から外す。BR4.10）。
  - `SearchRequestValidator`（BR4.1・BR4.4・BR4.5・BR4.7・BR5.1・BR5.2・BR6.2、4.1 の差の表の page・CHOICE・長さ。型として読む処理は `ColumnValueReader` の口で受ける）。誤りはまとめて返す。
  - `ContainsPattern`（`!` → `!!`、`%` → `!%`、`_` → `!_` の順に置き換え、前後に `%` を付ける純粋な関数）。
- [x] **Step 5: domain の層のテストを書いて流す**
  - `targetdb/domain/TargetReadSessionTest`・`TargetReadOutcomeTest`（toString に接続の文字列・値が出ない、`Done` は型の名前だけ）。`TargetSchemaResultTest` の `ReadPurpose.values()` の確かめを4つにする。
  - `role/domain/PermissionSnapshotTest` に `dsl()` のテスト（DSL のある写し・無い写し）を足す。
  - `businessdata/domain/` の単体テスト（`unit-test-instructions.md` の表の件数）。
  - 流す: `unit-test-instructions.md` の「domain の層」のコマンド。すべて通ってから次へ。

### 設定と repository の層（Contract の Repository / data access）

- [x] **Step 6: `targetdb.config` の実装**
  - `TargetDbProperties.QueryTimeout` に `list`・`detail`（`@DefaultValue("5s")`、空の値で null なら 5 秒）を足す。
  - `TargetDbSettings` に `QUERY_TIMEOUT_LIST`・`QUERY_TIMEOUT_DETAIL` の項目の名前、点検（1 秒以上）、`queryTimeout(purpose)` の `switch` の2つの枝を足す。
  - `TargetDataSourceConfig.driverProperties` の通信の待ちを、4つの上限の最大 + 5 秒にする（基盤の R-01）。既定のままなら 25 秒で変わらない。
- [x] **Step 7: 設定のテストを書いて流す**
  - 1.3 の表の既存のテストを直す（`TargetDbSettingsTest`・`TargetSchemaReaderTest`・`TargetDbPropertiesTest`・`TargetDataSourceConfigTest`・`AbstractTargetSchemaReaderIT`）。
  - 足す確かめ: `list`・`detail` の既定 5 秒、0.5 秒で不正の項目の名前（値は出さない）、`list` を 30 秒にしたときの通信の待ち 35 秒（3種類のドライバーの単位の違いを含む）、`detail` を 21 秒にしたときの 26 秒。
  - 流す: 「設定」のコマンド（単体）と、`targetdb` の結合テスト（`AbstractTargetSchemaReaderIT` の3つと `TargetDbStartupIT`・`TargetDbSecretLeakIT`）。
- [x] **Step 8: `businessdata.repository` の実装**
  - `ColumnValueCodec`: DSL の `dbType` の名前（大文字と小文字を区別しない）と DB の種類から `ValueType` を決める1か所（BR7.1。`tinyint(1)`・`bit(1)` は BOOLEAN、MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型は BINARY、表に無い型は OTHER）。要求の文字を型として読む（BR4.7・BR6.2、時差つきの型は NFR3.3）。型ごとのセッター（BR7.8、OTHER は BR7.7 の DB ごとの形）。結果の値を文字にする（BR7.2・BR7.3。時差つきの型は UTC で `+00:00`、桁を失わない）。長い文字の切り詰め（コードポイントで一覧 100 字・詳細 10,000 字、`truncatedColumns`。BR7.6・NFR2.5）。読み取りと変換の失敗はメソッドの中で受けて結果の型にする（R-02）。
  - `RowDialect`（`PostgresRowDialect`・`MysqlFamilyRowDialect`）: NULL の並び（BR5.5）、`LIKE ? ESCAPE '!'`、選ぶ列の式（STRING は `SUBSTRING(列, 1, N+1)`、OTHER は `CAST` の後に `SUBSTRING`、BINARY と password は選ばない）、主キーを先に読んでからつなぐ深いページの形（複合の主キーのつなぎ方を含む）、PG の `timetz` の `(列 AT TIME ZONE 'UTC') = ?`、OTHER の渡し方（PG は `Types.OTHER`、MySQL・MariaDB は `setString`）、BR7.9 の型の一覧、貸し出しの始めの文（MySQL・MariaDB の `SET time_zone = '+00:00'`、PG は無し）。どれも定数の形の文字と、引用した識別子だけで組み立てる。
  - `RowSqlFailureClassifier`: 段階（`SESSION_INIT`・`BIND`・`EXECUTE`・`READ`）と SQLState・業者の番号・例外の型の連なりから理由を決める純粋な部品。順は reliability-design.md の 2節（時間切れ → 接続の失敗 → 定義の食い違い → 値の型の誤り → 想定外）に、R-01（値の型の誤りの 400 は `BIND`・`EXECUTE` だけ、`READ` の型の変換の失敗は D1: A のとおり理由 `READ_CONVERSION` で 409 `MISMATCH` に写す）と R-03（`SESSION_INIT` の失敗は `UNAVAILABLE`）を足す。既存の `JdbcTargetSchemaReader.classify` の時間切れの見分け方（`SQLTimeoutException`・`SocketTimeoutException`・57014）とそろえる。メッセージを読まない。
  - `RowSqlExecutor`: SQL を組み立てて実行する唯一の部品（BR3.5）。引数は `TargetReadSession`（`Connection` を直接受けない）。
    - `search(session, view, conditions, sort, page)`: 始めの文 → 件数（`setQueryTimeout`・`setMaxRows(1)`）→ 読み飛ばす行の数が件数以上なら行を読まない（BR5.7）→ ページ（`LIMIT ?`・`OFFSET ?` と `setMaxRows(size)`）を、1つのメソッドの中で読む（R-03）。
    - `detail(session, view, key)`: 始めの文 → `setMaxRows(2)` で最大2行（BR6.3）。
    - 識別子は `KnownIdentifiers` の種類ごとの一覧を渡して `SqlIdentifiers.quoteKnown` で引用し、テーブルはスキーマ名で修飾する（BR3.3）。値はすべて `ColumnValueCodec` のセッターでバインドする（BR3.4）。
    - `SQLException` と `RuntimeException` はメソッドの中で受けて `RowQueryFailure` にする（R-02）。時間切れの後は同じ接続で続けて問い合わせない。
    - `prepareStatement`・`createStatement` を呼ぶのは `search`・`detail` の本体だけにし、ラムダの中で呼ばない（SpotBugs の除外をメソッドの名前で絞るため。A2）。
- [x] **Step 9: repository の層のテストを書いて流す**
  - モックの JDBC（Mockito）で、組み立てた SQL の形（DB の種類ごとの期待の文字）、バインドの型と順、`setQueryTimeout`・`setMaxRows` の値、件数以上の読み飛ばしで2つ目の文を作らないこと、例外の経路（`SQLException`・`RuntimeException` で結果の型になり、メッセージを持たない）を確かめる。
  - `RowSqlFailureClassifierTest` は、段階 × SQLState の表（3種類の DB の試しの値: 42P01・42703・42S02/1146・42S22/1054、22P02・22003・22007・22008・42883、22001・22003・22007・22018・HY000/1366・HY000/1525、57014・`MySQLTimeoutException`・70100/1969、08 の類）をパラメーターのテストで書く。
  - 流す: 「repository の層」のコマンド。

### service の層（Contract の Business logic）

- [x] **Step 10: `targetdb.service` の実装**
  - `TargetReadAccess`: `<T> TargetReadOutcome<T> withRead(ReadPurpose purpose, Function<TargetReadSession, T> work)` と `Optional<DatabaseProduct> product()`。
  - `JdbcTargetReadAccess`（`@Service`、既定の候補にしない `targetDataSource` を `@Qualifier` の `Optional<DataSource>` で受ける。既存の `JdbcTargetSchemaReader` と同じ形）:
    - 設定が使えない・接続が無い → `NotConfigured`（処理を呼ばない）。
    - 借りる失敗（すべての `SQLException`）→ `Unavailable`（分けない。Q3: A）。
    - 借りた直後に `setNetworkTimeout(executor, 目的の上限 + 5 秒)`。付ける処理の失敗 → `Unavailable`。
    - `work.apply` の `RuntimeException` → メッセージも原因も持たない `TargetReadUnexpectedException` にして投げ直す（R-02）。
    - `finally` で元の通信の待ちに戻し、失敗したら `evictConnection` で捨て（R-04）、閉じる。戻す・閉じる処理の失敗は例外のクラスの名前だけを WARN で出す。
    - `GENERATE`・`COMPARE` の目的で呼ばれたら想定外として拒む（スキーマの読み取りは今の `JdbcTargetSchemaReader` だけが使う）。
- [x] **Step 11: `businessdata.service` の実装**
  - `TableAccessResolver`: `EffectivePermissionResolver.snapshotFor(userId)` を1回だけ呼び、写しの DSL（`PermissionSnapshot.dsl()`）で ②〜④ の判定と `TableAccessView` を作る（BR3.1・BR1.2〜BR1.4、契約 C1 の accessFor）。DB の種類は `TargetReadAccess.product()` から読む（接続しない）。
  - `BusinessDataReadService`: 定義・一覧と検索・詳細の流れ（functional-spec.md の 3節）と判定の順（2節）。内部DB のトランザクションを開かない（写しは解決の口の中の読み取りで取り終える。BR8.3）。`withRead` の結果と `RowQueryFailure` を code に変え、WARN を1行出す（4.4 のログの1か所）。想定外は `BusinessDataUnexpectedException`（メッセージも原因も持たない）にして投げる。結果は sealed interface の record で返す。
  - `BusinessDataProblemTypeCatalog`（起動時に code を集める）。
- [x] **Step 12: service の層のテストを書いて流す**
  - `JdbcTargetReadAccessTest`（モック）: 設定が無い、借りる失敗（メッセージを持たない `Unavailable`）、`LIST`・`DETAIL` で 10 秒の通信の待ちを付けて元へ戻す、戻す処理の失敗で `evictConnection`、処理の `RuntimeException` がメッセージの無い例外になる、失敗しても閉じる、`GENERATE` を拒む、`product()`。
  - `TableAccessResolverTest`・`BusinessDataReadServiceTest`（偽の解決の口・偽の読み取りの口・偽の `RowSqlExecutor`）: 判定の順の表（①〜⑧ の組をパラメーターのテストで。たとえば NONE のテーブルで入力が誤っていても 403）、写しは1回、写しを取り終えてから `withRead` を呼ぶ順、`definitionChanged` の付け方（成功と 400 だけ）、`NotConfigured`・`Unavailable` → 503 `UNAVAILABLE`、時間切れ → 503 `TIMEOUT`、ログのキーと値（値・名前・メッセージが出ない）。
  - `BusinessDataProblemTypeCatalogTest`（8つの code と状態コードの組、共通の code と重ならない）。
  - 流す: 「service の層」のコマンド。

### web の層（Contract の API / endpoint）

- [x] **Step 13: `businessdata.web` の実装**
  - `BusinessDataReadController`: 3本の `POST`（`@ApiAccess(AUTHENTICATED)`）。利用者 ID は要求の文脈（認証の主体）から読み、本文から受けない。結果の型を `switch` で場合を尽くして `BusinessException` に変える。403 は `ProblemTypeRegistry` から `ACCESS_DENIED` を code で引く（`access` に依存しない）。400 は `BusinessDataFieldErrors` で `fieldErrors`（`field`・`reason`）と `definitionChanged` を追加の項目に載せる（BR9.2）。
  - 要求と応答の record: 値を持つものは `toString` で値を伏せる（カラムの名前と件数だけ）。応答のカラムの項目は `ColumnDefinition` の形で、読み取りの項目だけ（BR2.8。`validation` があれば写す形にして U2 が書き換えずに済むようにする）。`listFormat` に `BOOLEAN_ON_OFF`、行に `truncatedColumns`、定義に `rowLabelColumns`・`restrictionReason`・`writeConfigured`（U1 では書き込みの設定が無いため常に false）。
  - `BusinessDataWebConfig`: 3本の道ごとに `RequestBodyLimitRoute("POST", 道, 65,536, PAYLOAD_TOO_LARGE)`（NFR4.5。413 が 400 より先）。
  - `ApiEndpointCounts.MIN_ENDPOINTS` を 70 に上げる。
- [x] **Step 14: web の層のテストを書いて流す**
  - 単体: `BusinessDataReadControllerTest`（結果の型ごとの状態コード・code・`definitionChanged`・`fieldErrors` の形）、要求と応答の record の `toString`。
  - 結合（PostgreSQL のコンテナ1種類）: `BusinessDataAuthorizationApiIT`。3本の API と権限の組をパラメーターを使う表で書き、未認証（401）・要る権限だけを欠く利用者（ほかのテーブルは READ を持つ。403）・持つ（200）を確かめる。停止中の利用者は通らない。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で 403／200 が切り替わる（AC1.1.5・AC3.1.4・AC3.1.5）。
  - 結合: `BusinessDataRequestLimitIT`。本文 64KB ちょうどは受け付け、超えると 413、413 が 400 より先、検索の値・主キーの値の 1,000 字ちょうどは受け付け 1,001 字は 400（`INVALID_VALUE`、`definitionChanged` つき）、page 1,000,000 は受け付け 1,000,001 は 400、どれも応答に値が入らない。
  - 流す: 「web の層」のコマンドと、`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`・`ProblemTypeDuplicateStartupIT`。

### 横断の結合テスト・構造の検査・性質ベースのテスト

- [x] **Step 15: 3種類の対象DB の結合テスト**
  - `AbstractBusinessDataReadIT` と `MysqlBusinessDataReadIT`・`MariadbBusinessDataReadIT`・`PostgresBusinessDataReadIT`（既存の `AbstractTargetSchemaReaderIT` と同じ形。`TargetDbTestDatabase` でクラスごとに名前の重ならないスキーマと読み取りだけのアカウントを作って消す。`ContainerRuntimeCheck` で、コンテナの実行環境が無ければ WARN を出して飛ばす。CI では失敗させる）。
  - 表は 100 カラム・数行の小さな表を中心にし、100,000 行の種は入れない。確かめること（NFR3.1・NFR3.3・NFR2.2・NFR2.5・NFR7.1・AC の一覧は 6節）:
    - 定義・一覧・検索・詳細の振る舞い、NONE のカラムが応答に無い、`list.visible`・`detail.visible` の出し分け
    - 型の違い・識別子の大文字と小文字・`'`・`"`・`;`・`--`・バッククォート・改行を含む識別子と値と並べ替え
    - NULL の並び（昇順で最後・降順で最初）、部分一致の `%`・`_`・`\`・`!` が文字として働く
    - 境界の値（`BIGINT` の上限と下限、`DECIMAL(38,10)` の桁の多い値、NULL と空の文字列）が1桁も変わらずに文字で返る
    - ページの境で行が重ならない（複合の主キー）、最後のページを超えたら空のページと件数、ページの件数より多い行の表で読む行の数が上限を超えない
    - 詳細の 0 行（404）・1 行・2 行（ビューの主キーの設定で重なる行。409 `BUSINESS_ROW_NOT_UNIQUE`）、`/`・`%`・`?`・`#`・空白・引用符を含む主キーの値
    - 時差つきの型（オフセットが無い値・`Z`・`+09:00` で同じ行に当たり、返す値が `+00:00`。サーバーのタイムゾーンを夏時間のある地域にする）
    - OTHER の型ごとの EQUALS と主キー（T2 の型）、BR7.9 の型が検索の項目に出ない、MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型が値を返さず `valueType: BINARY`
    - 切り詰めの境界（100 字ちょうどは切らない・101 字は切って印、10,000・10,001 字）と、数 MB の値で受け取る量が絞られていること
    - DSL にあるテーブル・カラムが対象DB に無い（409 `MISMATCH`）
    - R-01: MySQL・MariaDB のゼロ日付と範囲の外の値を持つ行で、一覧と詳細が 400 にならず、D1: A のとおり 409 `BUSINESS_TARGET_DB_MISMATCH` になり、WARN の理由が `READ_CONVERSION` になる
    - 読み取りだけのアカウントで通ること
  - 流す: 「3種類の対象DB の結合テスト」のコマンド。
- [x] **Step 16: 境界テストと SpotBugs の除外**
  - `BusinessDataBoundaryArchitectureTest`（新しく足す）:
    - `businessdata` が依存してよいのは `targetdb.service`・`targetdb.domain`・`role.service`・`role.domain`・`dsl.domain`・`dsl.service` の提供口・`common` と、Java・Spring・JDBC の部品だけ
    - `businessdata.repository` は `targetdb.service` に依存しない
    - `businessdata` の中で `java.sql.Connection` の `prepareStatement`・`createStatement` を呼べるのは `RowSqlExecutor` だけ（呼ぶ所が見つかることも確かめる）
    - 4.4 のトランザクションの規則
    - `businessdata` に依存してよい機能は無い（依存される側）
  - `RoleBoundaryArchitectureTest` を A1 のとおり直す（承認を得てから）。
  - `TargetDbBoundaryArchitectureTest`・`ArchitectureTest`・`DslBoundaryArchitectureTest` は変えずに流す。
  - `./gradlew :backend:spotbugsMain` の報告で `businessdata` の指摘を読み、A2 の範囲で除外を足して `:backend:spotbugsGate` を通す。A2 の範囲を超える指摘があれば、足す前に依頼者に見せる。
  - 流す: 「境界テストと静的解析」のコマンド。
- [x] **Step 17: 漏えいの IT**
  - `BusinessDataSecretLeakIT`（PostgreSQL のコンテナ。`businessdata` と `targetdb.service` のロガーを TRACE にする。既存の `NavigationSecretLeakIT` と同じ形）: 一覧・詳細・400・404・503・PG の 22P02 を起こす OTHER の値・不正な数と日付の文字（非検査の例外の経路）・`Done` を通る戻り値で、アプリのログ・誤りの応答に行の値・主キーの値・検索の値・接続先・資格情報・例外のメッセージが無いこと。ログの JSON の WARN のキーが `purpose`・`reason`・`sqlState`・`vendorCode`・`exceptionClass` だけであること（NFR4.2・NFR8.2）。
  - `BusinessDataTraceExposureIT`（外部へ送る設定を有効にし、トレースを手元で受ける。既存の `NavigationQueryExposureIT` と同じ形）: span の属性に値・名前が無く、`uri` が3つの固定の道だけであること。
  - 流す: 「漏えいの IT」のコマンド。
- [x] **Step 18: 接続の数の IT**
  - `BusinessDataConnectionUsageIT`（内部DB と対象DB の両方の DataSource を数える包み。PostgreSQL のコンテナ）: 定義は内部DB 1・対象DB 0、一覧は内部DB 1・対象DB 1（件数とページで1回の貸し出し）、詳細は内部DB 1・対象DB 1、対象DB を借りている間の内部DB の借りている数 0、要求の始まりの借りている数 0。MySQL では `SET time_zone` の文が増えても接続が増えないこと（R-03）は、MySQL のコンテナの行を1つ足して確かめる。U2 が書き込みの3行を足せる表の形にする（scalability-design.md の 8節）。
  - 流す: 「接続の数の IT」のコマンド。
- [x] **Step 19: 時間切れの IT**
  - `BusinessDataTimeoutIT`:
    - 応答しない対象DB（`SilentServer`）: 借りる失敗が約 3 秒で 503 `UNAVAILABLE`
    - 拒まれる番号: 503 `UNAVAILABLE`
    - 設定が無い: すぐ 503 `UNAVAILABLE`
    - 本物の遅い問い合わせ（PG の `pg_sleep`・MySQL・MariaDB の `SLEEP` を含むビュー）: 件数とページのそれぞれで約 5 秒の 503 `TIMEOUT`（件数の時間切れで要求全体が失敗する）
    - つないだ後に止まる（`StallingRelay`）: 25 秒以内に 503 `TIMEOUT`、止めを解いた後の次の要求はすぐ成功する（壊れた接続はプールが捨てる）。D3: A のとおり3種類すべてに置く（MySQL 約 20 秒・PG 約 15 秒・MariaDB 約 10 秒。verify が約 45 秒延びる）
    - どれも応答とログに接続先・メッセージが無い
  - 流す: 「時間切れの IT」のコマンド。
- [x] **Step 20: 性質ベースのテスト（jqwik）**
  - `TableAccessPolicyPropertyTest`: 権限を足しても出るカラムと許す操作が減らない、NONE のカラムが出ない、FULL でないカラムが入力にならない、主キーの無いテーブルと主キーのカラムが NONE のテーブルに DETAIL・UPDATE・DELETE が出ない、ビューに CREATE・UPDATE・DELETE が出ない（BR2.5）。
  - `IdentifierMatchingPropertyTest`: `KnownIdentifiers` の種類ごとの一覧に無い名前は拒まれる、引用符・`;`・改行を含む任意の文字列でも `quoteKnown` の結果は両端が引用符で中の引用符が二重になり、1つの識別子としてしか読めない。
  - `ContainsPatternPropertyTest`: 任意の文字列で、エスケープの後に `!` の前置の無い `%`・`_` が残らない。
  - `SearchOperatorPolicyPropertyTest`: 表に無い演算子と型の組が検索の項目に出ない、password・BINARY のカラムがどの演算子にも出ない。
  - 失敗したときの乱数の種は、既存のビルドの設定（失敗の詳細をすべて出す）で出力に残る。
  - 流す: 「性質ベースのテスト」のコマンド。

### 負荷の試験の台本・設定・文書（Contract の Environment/build configuration）

- [x] **Step 21: 負荷の試験の台本**
  - `perf/biz/`（シェル。新しい道具を入れない）: 3種類の DB ごとに、スキーマ（MySQL・MariaDB はデータベース）`bizperf` と、100 カラムの表 `biz_item`（主キー1つ）・複合の主キーの表・少しの行の表を作り、決めた種（performance-design.md の 4節。型ごとの短い値、TEXT の5本に 150〜270 字、10 行だけ 2,000,000 字、NULL を含む）から 100,000 行を入れる SQL を出す台本。読み取りだけのアカウント `mastersmith_reader` に `bizperf` の SELECT（PG は USAGE も）を与える。値はすべて架空。`bizSearchLastSorted` の並べ替えのカラム（主キー以外の1つ）には索引を作らない（D2: A。主キーの索引だけ）。
  - `perf/make-biz-dsl.mjs`（Node の標準だけ）: 種の表に合わせた DSL（版 2）を作る。部分一致に使う TEXT のカラム、並べ替えのカラム、一覧に出す 100 カラムを決めた形で書く。
  - `perf/k6/scenarios.js` に場面を足す（NFR1.2 の判定の決まり。1回の繰り返しに要求1つ、判定は `iteration_duration{scenario:…}` の p95 < 1000 と `checks` の率 1、`http_req_duration{name:…}` は並べて記録だけ、場面の長さ 3 分、トークンは `setup()` で取る）:
    - `bizPrepare`（1 VU・1 回。4.2 の R-02 の前提をそろえる）
    - `bizDefinition`（PG だけ）・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`（R-05）・`bizDetail`・`bizSearchWide`（100 件 × 100 カラム × 100 字。memory.peak の確かめ）
    - 各場面の `setup()` は前の場面の状態に頼らず、定義の API が 200 になることを確かめてから始める
  - `docker/perf/compose.yaml`: `targetdb-mysql` に `--innodb-buffer-pool-size=${MASTERSMITH_PERF_TARGETDB_MYSQL_BUFFER_POOL:-128M}` と `mem_limit: ${MASTERSMITH_PERF_TARGETDB_MYSQL_MEMORY:-768m}`、`targetdb-mariadb` に `_MARIADB_BUFFER_POOL:-128M`・`_MARIADB_MEMORY:-512m`、`targetdb-postgres` に `-c shared_buffers=${MASTERSMITH_PERF_TARGETDB_POSTGRES_SHARED_BUFFERS:-128MB}`・`_POSTGRES_MEMORY:-512m` を足す（既定の値は今の値。変数の名前はこの計画で決めた）。
  - `perf/README.md` に業務データの節を足す: 配備したアプリを止める、export する値（MySQL 768M・2304m、MariaDB 512M・1g、PG 256MB・768m）、MySQL・MariaDB の回では lgtm を動かさない（指標は使い捨てのアプリにだけ公開する actuator の metrics で読む）、対象DB の接続先は一時の環境ファイル（`MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE`）で渡す、5.3 の流す順（起動 → `bizPrepare` → 表の大きさの実測と 80% の決まり → 設定の実際の値の記録 → 暖め → 合否の回 → `bizSearchWide` の前にアプリのコンテナを作り直す → 確かめの結果を見てから片付ける）、台本の全体を `caffeinate -dims` で包み流した後に `pmset -g log` で眠りを確かめる、流した後は変数を `unset` するか別のシェルで流す、用意の操作は使い捨ての環境の監査に残る、「3 秒（MySQL で確かめが走るときは 6 秒）」。
  - `perf/dsl-timing.sh` の始めに、業務データの試験の6つの変数を `unset` する1行を足す（基盤の R-04）。
- [x] **Step 22: 台本の確かめ（k6 inspect と試し走り）**
  - `k6 inspect --include-system-env-vars` で、場面ごとに場面の名前と閾値の名前を確かめる（project.md の学び）。
  - ダミーの `MASTERSMITH_PERF_ENV_FILE` と `--profile targetdb-mysql --profile targetdb-mariadb --profile targetdb-postgres` を付けて `docker compose -f docker/perf/compose.yaml config` を読み、変数を渡さないときの `command` と `mem_limit` が今の値と同じことを見る（基盤の R-04）。
  - 試し走り（各場面 1 VU・10 秒。判定ではない）で、閾値の名前の指標が出ることと `checks` が通ることを確かめる。D4: A のとおり、試し走りは PostgreSQL の1種類だけを既定の設定（業務データの試験の値を export しない）で流し、配備したアプリは止めない（上限の合計 5,376 MiB）。PG の種の台本は試し走りの対象DB で一度流して、行の数を数える。MySQL・MariaDB の種の台本は、SQL を出すところまでを確かめ（出した SQL の形と行の数の指定を読む）、流すのは Performance Validation にする。試し走りの値は判定に使わない。使い捨ての環境は、確かめの結果（WARN の件数、ログに試験用の利用者のメールアドレスと行の値が無いこと）を見てから片付ける。
- [x] **Step 23: アプリの設定と文書**
  - `backend/src/main/resources/application.yaml` の `query-timeout` の下に `list: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST:5s}`・`detail: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_DETAIL:5s}` とコメントの行を足す（基盤の設計 1.1）。
  - `.env.example` にコメントの行2つを足し、55 行の説明を「生成 既定 20s・照合 既定 5s・一覧 既定 5s・詳細 既定 5s。ドライバーの通信の待ちは最も長い上限 + 5 秒」に直す。配備の `.env` は開かず、変えない。
  - `README.md` の環境変数の表に2行と、「対象DB（利用者の業務データの DB）」の節に業務データの読み取りの API（3本の道・ログインだけ・権限はテーブルごと・読み取りの接続だけを使う・時間切れと 503）の段落を足す。

### 検査と記録

- [x] **Step 24: 1コマンドの検査とカバレッジ・時間の記録**
  - `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる（入っていたら結果を使わずに流し直す）。
  - `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）。対象DB のテストが SKIPPED でないこと。
  - 記録: verify の時間（直近は 14 分 1 秒。D3: A の中継のテストで約 45 秒延びる見込み。CI の上限の7割の約 42 分を超えたら依頼者に諮る）、カバレッジ（全体と `businessdata.*`・`targetdb.*`・`role.domain` のパッケージごと。実測の数字だけ）、テストの件数。
  - 落ちたテストは、team.md の「不安定なテストと CI の失敗」の決まりで扱う。計画の外の失敗で直し方が分かれるときは、生成を止めて依頼者に諮る（G1〜）。
- [x] **Step 25: E2E の全件**
  - U1 は業務データのパッケージと `role` の `src/main` に触れるため、統合の前に E2E の全体を流す（team.md の Way of Working）。Mailpit を `docker compose --profile mail up -d mailpit` で起動し、`caffeinate -dims` で包んで `./gradlew e2eTest` を流し、通った件数を記録する。眠りの確かめは Step 24 と同じ。
  - E2E のファイルは足さない（業務データの代表の流れは U3 が持つ）。
- [x] **Step 26: 記録**
  - `code-summary.md`（作ったもの・計画との差・依頼者に確かめたいこと・Build and Test と Performance Validation と Observability Setup への引き継ぎ: 4.1 の R-06、4.2 の R-05・R-07、試し走りの値、2304m）。
  - `traceability.json`（機能設計の BR・NFR 要件の枝番から、部品とテストへ。4.2 の R-06 のそろえ方）。
  - `source-manifest.json`（作った・変えた・消したアプリのソースのパスをすべて）。
  - `generation-notes.md`（計画に無い変更は、計画を書き換えずにここへ記録する。project.md の学び）。

### コミットと統合

- [ ] **Step 27: コミットの提案と積み（依頼者の承認）**
  - 生成の後に、7節の区切りで提案し、依頼者の承認を得てから積む。最初のコミットの時点で型の検査（コンパイル）が通ることを、`./gradlew :backend:compileJava :backend:compileTestJava` で区切りごとに確かめる。
  - コード生成の段の承認の場は、最後のコードのコミットの後、`aidlc/` の下だけの記録のコミットを積む前に開く（project.md の学び）。
- [ ] **Step 28: 統合（依頼者の承認の後）**
  - 統合の前に `aidlc/` の未コミットの変更（監査ログを含む）をコミットしておく。
  - Step 24 の verify と Step 25 の E2E が通った状態で、`develop` へ squash で1コミットとして戻す。件名は日本語で「業務データの読み取りの API を足す（Intent 261016-business-data-views の B2・U1）」の形。統合を終えたブランチは消す。
  - `origin` への push は依頼者が行う。CI の結果は Build and Test で試行ごと（`gh run view --json attempt,jobs`）に確かめる。

## 6. ストーリーから Step への対応

| ストーリー（U1 の受け持ち） | 主な受け入れ基準 | Step |
|---|---|---|
| US1.1（一覧を開く。サーバーの側） | AC1.1.3・AC1.1.4・AC1.1.5・AC1.1.7・AC1.1.8・AC1.1.9 | 4・11・13・14・15 |
| US1.2 条件で行を絞り込む | AC1.2.1〜AC1.2.9・AC1.2.13・AC1.2.14 | 4・8・11・15・20 |
| US1.3 ページを送り、並べ替える | AC1.3.1〜AC1.3.6・AC1.3.8〜AC1.3.12・AC1.3.14・AC1.3.16（AC1.3.7 は台本） | 4・8・15・21 |
| US1.4 行の詳細を見る | AC1.4.1〜AC1.4.5・AC1.4.7・AC1.4.8 | 4・8・11・15 |
| US1.5（値の受け渡し。サーバーの側） | AC1.5.1・AC1.5.3・AC1.5.5〜AC1.5.8 | 8・15 |
| US3.1 作業ロールの権限どおりにカラムが見える | AC3.1.1・AC3.1.2・AC3.1.4〜AC3.1.10 | 4・11・14・15・20 |
| US3.2 主キーとビューで行の操作が決まる | AC3.2.1〜AC3.2.7 | 4・15・20 |
| US3.3 見られない・無いときに理由が分かる | AC3.3.1〜AC3.3.4・AC3.3.6・AC3.3.8〜AC3.3.10 | 4・8・10・11・12・15・19 |
| US3.5 API が作業ロールの権限で守られる | AC3.5.1・AC3.5.2 | 13・14・16 |
| US3.6 SQL と接続が乱れない | AC3.6.1〜AC3.6.9 | 8・9・10・15・16・17・18・20 |
| 横断（境界テスト・SpotBugs・ADR-002） | BR11.2〜BR11.4 | 3・16 |
| 横断（NFR1・NFR2.1・NFR2.4・NFR2.5・NFR3.2 の測りの用意） | NFR1.1〜NFR1.3・NFR2.1・NFR2.4・NFR2.5・NFR3.2 | 21・22 |
| 横断（時間の上限の設定） | NFR4.4・基盤の R-01 | 2・6・7・23 |

U1 で受け持たない受け入れ基準（AC1.1.1・AC1.1.2・AC1.1.6・AC1.2.10〜AC1.2.12・AC1.3.13・AC1.3.15・AC1.4.6・AC1.5.2・AC1.5.4・AC3.3.7 は U3、AC3.1.3・AC3.3.5 は U2）は、機能設計の traceability.json のとおり Deferred のまま。

## 7. コミットの区切り

場所・機能ごとに区切り、どのコミットの時点でもコンパイルが通る順にする（project.md の学び）。import の向き（`businessdata` → `targetdb`・`role` → `dsl`）から、依存される側を先に積む。

| # | 中身 | コンパイルが通る理由 |
|---|---|---|
| C1 | `targetdb` の変更（`ReadPurpose`・`TargetReadSession`・`TargetReadOutcome`・`QueryTimeout`・`TargetDbSettings`・`driverProperties`・`TargetReadAccess`・`JdbcTargetReadAccess`）とそのテスト（直した既存のテストを含む）、`application.yaml`・`.env.example`・`README.md` の環境変数の2行 | `ReadPurpose` の `switch` と `QueryTimeout` の作り方の口を同じコミットで直す |
| C2 | `role.domain` の `PermissionSnapshot.dsl()` と `PermissionSnapshotTest` | 足すだけ |
| C3 | `businessdata.domain` と `businessdata.repository`、その単体テストと性質ベースのテスト、`spotbugs-exclude.xml` の除外（A2） | C1 の `targetdb.domain` に依存する |
| C4 | `businessdata.service`・`businessdata.web`、その単体テスト、`ApiEndpointCounts`、`BusinessDataBoundaryArchitectureTest`、`RoleBoundaryArchitectureTest` の変更（A1） | C1〜C3 に依存する。`RoleBoundaryArchitectureTest` の見分けの確かめは `businessdata` → `role.service` の依存が入った後に置く |
| C5 | `businessdata` の結合テスト（3種類の DB・認可・本文の上限・漏えい・トレース・接続の数・時間切れ）と `businessdata/testsupport` | テストだけ |
| C6 | 負荷の試験の台本（`perf/k6/scenarios.js`・`perf/biz/`・`perf/make-biz-dsl.mjs`・`perf/README.md`・`perf/dsl-timing.sh`・`docker/perf/compose.yaml`） | Java を含まない |
| C7 | `README.md` の「対象DB」の節の業務データの段落 | 文書だけ |

- コミットのメッセージは日本語で、各コミットの末尾に共著者の行を付ける。
- 区切りの中身が生成の結果で変わるときは、提案のときに理由を添えて示す。

## 8. 統合

| 項目 | 扱い |
|---|---|
| ブランチ | `feature/261016-business-data-views-b2`（`develop` から作る。worktree は使わない） |
| 統合の形 | `develop` へ squash で1コミット（サブモジュールの固定先の更新を含まないため fast-forward の例外は当たらない） |
| 統合の前の関門 | `caffeinate -dims` で包んだ `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（コンテナの実行環境あり、対象DB のテストが飛んでいない）と、`./gradlew e2eTest` の全件。どちらも流す前後に `pmset` で眠りを確かめる |
| push | 依頼者が行う。AI は push しない |
| 統合の後 | ブランチを消す。CI の結果は Build and Test で試行ごとに記録する |

## 9. 依頼者の決定（D1〜D4: A）

計画の承認の前に、判断が分かれる4点を依頼者に尋ね、4つとも推奨の A に決まった（答えの記録は `code-generation-questions.md`）。決定は本文の次の所に反映した。

| 決定 | 中身 | 反映した所 |
|---|---|---|
| D1: A | 結果を読む段の型の変換の失敗は 409 `BUSINESS_TARGET_DB_MISMATCH` にする。新しい code は足さない。ログは WARN で、理由は `READ_CONVERSION` | 4.1 の R-01、Step 4・8・12・15、Assumptions |
| D2: A | `bizSearchLastSorted` の並べ替えのカラムに索引を作らず、悪い側の条件で測る。目標を超えたら Performance Validation で Not Met とし、扱いを諮る | 4.1 の R-05、4.2 の R-03、Step 21 |
| D3: A | つないだ後に止まる中継のテストを3種類すべてに置く。verify が約 45 秒延びる | Step 19・24、`unit-test-instructions.md` |
| D4: A | 試し走りは PG の1種類を既定の設定で流し、配備したアプリは止めない。MySQL・MariaDB の種の台本は、SQL を出すところまでを確かめる | Step 22、Assumptions |

尋ねたときの選択肢は、記録のため次に残す。

### D1. 結果を読む段の型の変換の失敗（NFR 設計のレビュー R-01）

MySQL・MariaDB のゼロ日付（`0000-00-00`）のように、保存済みの値を `ResultSet` から読むときに型の変換が失敗する行があると、利用者の入力が正しくても、そのテーブルの一覧・詳細を開けなくなります。この失敗は 400 にしない（R-01）としたうえで、何を返すかを決めます。

A. 409 `BUSINESS_TARGET_DB_MISMATCH` に写す（DSL の型と対象DB の値が合わない、定義との食い違いの一種として扱う）。新しい code を足さず、U3 の画面の文もそのまま使える。ログは WARN で理由 `READ_CONVERSION`（推奨）

B. 新しい code（例 409 `BUSINESS_TARGET_DB_VALUE_UNREADABLE`）を足して見分ける。U3 の画面に文を1つ足す

C. 想定外の失敗（500。メッセージの無い例外）にする

D. 行は返し、読めない値のカラムを印（例 `unreadableColumns`）で返す。契約 C2 に項目を足し、U3 に出し方の作業が増える

X. Other (please specify)


### D2. 主キー以外で並べ替えた深いページの場面（NFR 設計のレビュー R-05・基盤の設計のレビュー R-03）

`bizSearchLastSorted` で並べ替えに使うカラムに、索引を作るかを決めます。表の大きさの測り方（バッファープールの 80% の線）にも響きます。

A. 索引を作らない。DSL で `sortable` にしたカラムに索引があるとは限らないため、悪い側の条件として測る（project.md の「悪い側の条件のまま測る」）。MySQL・MariaDB は 1 秒を超える見込みが高く、その段で Not Met として扱いを諮る（推奨）

B. 並べ替えのカラムに索引を作る。表と索引の大きさが増え、PG は最初から 80% の線を超える見込み

C. この場面を足さず、`bizSearchLast`（主キーの順）だけで測る（R-05 の推奨との差を記録する）

X. Other (please specify)


### D3. つないだ後に止まる中継のテスト（`StallingRelay`）を置く DB の種類

1回で 10〜20 秒かかるテストで、DB ごとに失敗までの時間が違います（試しで MySQL 20 秒・PG 15 秒・MariaDB 10 秒）。

A. 3種類すべてに置く。`verify` が約 45 秒延びる（直近の verify は 14 分 1 秒で、42 分の線まで余裕がある）（推奨）

B. 最も長くかかる MySQL だけに置く。PG・MariaDB の確かめは NFR 設計の試しの値に頼る

C. 置かず、`SilentServer`（借りる失敗）と遅い問い合わせ（5 秒）の確かめだけにする

X. Other (please specify)


### D4. 台本の試し走り（Step 22）の範囲と配備したアプリの扱い

試し走りは判定ではなく、場面と閾値の名前と `checks` を確かめるためのものです。

A. PostgreSQL の1種類だけ、対象DB は既定の設定で流す。配備したアプリは止めない（上限の合計は perf のアプリ 2048 + perf の PG 512 + 配備したアプリ 2048 + 見本の PG 512 + Mailpit 256 = 5,376 MiB で、5,910 MiB に収まる）。MySQL・MariaDB の種の台本は、SQL を出すところまでを確かめ、流すのは Performance Validation にする（推奨）

B. 3種類すべてで流す。MySQL・MariaDB の回（業務データの試験の値）は、配備したアプリを止める（数分ずつ）

C. 試し走りを Performance Validation に回し、この段は `k6 inspect` と compose の確かめだけにする（NFR1.2 の「B2 で試し走り」との差を記録する）

X. Other (please specify)


## Sources

- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`（Step 1〜2）
- この単位の設計（どれも `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/` の下、承認済み）
  - `functional-design/functional-spec.md`・`rules.md`・`entities.md`・`traceability.json`
  - `nfr-requirements/` の `performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`・`security-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`
  - `nfr-design/` の `logical-components.md`・`reliability-design.md`・`security-design.md`・`performance-design.md`・`scalability-design.md`・`observability-design.md`・`nfr-design-followup-questions.md`（F1〜F3: A）
  - `infrastructure-design/` の `infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`
- 捨ての試しの記録: リポジトリの外の試しの結果（RESULTS.md。T1〜T9・F2）
- Inception の成果物（どれも `aidlc/spaces/default/intents/261016-business-data-views/inception/` の下）: `requirements-analysis/requirements.md`、`user-stories/stories.md`、`units-generation/unit-of-work.md`・`unit-of-work-story-map.md`、`contract-design/contract-summary.md`（C1・C2・C6・C7）、`domain-design/components.md`・`decisions.md`（ADR-001・002・005・006・007）、`delivery-planning/bolt-plan.md`（B2）・`memory.md`（カバレッジの実測）
- 承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/` の `functional-design/memory.md`・`nfr-requirements/memory.md`・`nfr-design/memory.md`・`infrastructure-design/memory.md`・`code-generation/memory.md`（Deviations）
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json`（R-01〜R-06）、`.aidlc-reviews/infrastructure-design/units/u1-businessdata-read/4bcccff4724084fb/1.json`（R-01〜R-07）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated と学び）
- コード（読み取りだけで確かめた）: `backend/src/main/java/cherry/mastersmith/targetdb/`（`config/TargetDbProperties.java`・`TargetDbSettings.java`・`TargetDataSourceConfig.java`、`domain/ReadPurpose.java`・`SqlIdentifiers.java`・`DatabaseProduct.java`、`service/JdbcTargetSchemaReader.java`）、`role/domain/PermissionSnapshot.java`・`role/service/EffectivePermissionResolver.java`、`dsl/domain/`（`DslModel`・`DslTable`・`DslColumn`・`SearchSetting`・`ListSetting`・`OptionSource`・`ListFormat`）、`common/error/`（`BusinessException`・`ErrorResponseFactory`・`GlobalExceptionHandler`）、`common/observability/TraceAspect.java`、`role/web/RoleWebConfig.java`・`RoleFieldErrors.java`、`backend/src/test/java/cherry/mastersmith/` の `targetdb/TargetDbBoundaryArchitectureTest.java`・`role/RoleBoundaryArchitectureTest.java`・`ArchitectureTest.java`・`ApiAccessArchitectureTest.java`・`common/testsupport/ApiEndpointCounts.java`・`targetdb/testsupport/`・`navigation/testsupport/`・`navigation/web/NavigationConnectionUsageIT.java`・`NavigationQueryExposureIT.java`、`backend/build.gradle.kts`（`packagesJudgedByTotal`・SpotBugs の関門・テストのタスク）、`backend/config/spotbugs-exclude.xml`、`backend/src/main/resources/application.yaml`、`.env.example`、`docker/perf/compose.yaml`、`docker/targetdb/`、`perf/`、`README.md`
- HikariCP 7.0.2 のソース（Gradle のキャッシュの sources の jar を読み取りだけ）: `ProxyConnection.close`・`setNetworkTimeout` と `PoolBase.resetConnectionState`（4.1 の R-04）

## Assumptions & Open Questions

- [assumption] 機能設計の部品の名前は仮の名前で、生成で変えるときは generation-notes.md に差を記録する。
- [assumption] `bizPrepare` の種の表の大きさは、試しの表（PG 198MB・MySQL 532MB・MariaDB 372MB）に近い値になる。違えば基盤の設計 2.2 の決まりで上げる（Performance Validation）。
- [assumption] D1: A により、`BUSINESS_TARGET_DB_MISMATCH` の意味は「DSL の定義と対象DB が合わない（テーブル・カラムが無い、または保存済みの値を DSL の型で読めない）」に広がる。U3 の文の出し分けはこの意味で書く（U3 への引き継ぎ）。
- [assumption] 試し走り（D4 の A）は判定ではなく、配備したアプリを止めずに流すため、CPU を取り合っても結果を判定に使わない。
- [assumption] 対象DB の設定が無いときの定義の API は、DB の種類がわからないため、BR7.9 と BINARY と同じ扱いの型を3種類の一覧の和で当てる（狭める向き）。一覧・詳細は 503 になる。
- 依頼者の決定 D1〜D4: A は、9節の表の所に反映した（諮る点は残っていない）。
- [assumption] D3: A で verify が約 45 秒延びる見込み。Step 24 で実測し、42 分の線を超えたら依頼者に諮る。
- Open: 生成の途中で、承認の範囲（3節の A1〜A3）を超える変更や、`packagesJudgedByTotal` の 7 パッケージの本体のソースへの変更が要るとわかったら、生成を止めて依頼者に諮る。
