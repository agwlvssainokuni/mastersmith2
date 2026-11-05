# コードの構成（mastersmith2）

> 2026-11-06（記録のコミット `a789a6f`）の Intent `261104-tz-options-perf` の Focused scan で、「表示のタイムゾーンと日時の変換の置き場」の節を足した（`frontend/src/features/preferences/`・`frontend/src/shared/format/`・`frontend/vitest.config.ts`）。ほかの節は前回以前の記録による。下の「日時の型の置き場」の `shared/format/formatDateTime.ts` は今は無い（K-110）。
>
> 前回の記録: 2026-11-03（記録のコミット `fa0b01e`）の Intent `261102-timezone-ui-polish` の Focused scan で、「画面の骨組みの仕上げ」の節（サイドバーのアイコン・ホーム・管理の仮の画面・ページの頭。K-100・K-102〜K-104）と、日時の型の置き場の節を足した。ほかの節は前回以前の記録による。
>
> 前回の記録: 2026-11-01（記録のコミット `6079f13`）の Intent `261101-maintenance-update` の Focused scan で、リポジトリの最上位の表（サブモジュールの固定先・E2E の本数・`perf/` と `docker/` の中身・版の定義のファイル）を今の値に直した。パッケージのファイル数は前回までの数のまま（今回は数え直していない。`businessdata` は 2026-11-01 に 149 ファイル）。
>
> 前回の記録: 2026-10-29（記録のコミット `15a25aa`）の Intent `261029-reference-lookup-cleanup` の Focused scan で更新した。今回は `businessdata`・`dsl`・`targetdb`・`role` の選択肢に関わる型の位置と、`frontend/src/features/tables` の構成（約 5,300 行、テストを含む）と `frontend/src/shared` の一覧を確かめて足した。ほかのパッケージのファイル数は前回（2026-10-25、`find` と `git ls-files`）の数のまま。所見の本文はこの文書には無く、場所は `business-overview.md` の一覧。

## リポジトリの最上位

| 場所 | 中身 |
|---|---|
| `backend/` | Java のアプリ（Gradle、実行可能 WAR）。Java のパッケージ配下に 16 パッケージ（下表）。`git ls-files backend/src/main` は 867、`backend/src/test` は 669（リソースを含む） |
| `frontend/` | React + TypeScript の SPA。`src/app/`（92）・`src/shared/`（51）・`src/features/`（417）（前回までの数）・`e2e/`（`*.e2e.ts` は 22 本、010〜195、2026-11-01）・`scripts/`（`check-license-header.mjs`・`check-bundle-size.mjs`）。設定は `package.json`・`.npmrc`（`engine-strict`・`ignore-scripts`）・`vite.config.ts`・`vitest.config.ts`・`vitest.setup.ts`・`playwright.config.ts`・`eslint.config.js`・`tsconfig.json` |
| `vendor/make-you-chic-ui/` | デザインシステム（Git サブモジュール、固定先 `cae5fed`、2026-11-03 の `git submodule status`。K-105） |
| `vendor/java-mustache-processor/` | Mustache のエンジン（Git サブモジュール、固定先 `d61ba6d`、`0.1.0-6`。2026-11-03。K-105） |
| `docker/` | `monitoring/`・`otel-collector/`・`perf/`（使い捨ての試験の環境）・`targetdb/`（見本の対象DB の初期化。種類ごとに表・読み取り・書き込みのアカウント）・`e2e/`（E2E の見本の PG の番号を開ける上乗せ）・`jmx/` と `hikari-pool.sh`（接続のプールの詰め直しの道具）・`check-container-limits.sh`（約 3,900 行） |
| `perf/` | k6 の負荷の試験（`perf/k6/scenarios.js` 2,848 行（2026-11-01）、伏せ字の道具 `perf/redact-k6-evidence.mjs`（907 行、K-90）、種 `perf/biz/make-biz-seed.sh`、DSL `perf/make-biz-dsl.mjs`・`make-*-dsl.mjs`）、`perf/dsl-mixed-memory.sh`（`memory.peak` の内訳の記録）、`perf/versions.env`（k6・JRE の版）、`perf/lib/`（接続先の組み立て）、`perf/ui/`、手順（`perf/README.md` 1,753 行、2026-11-01。K-89）。k6 の呼び出し（`--summary-export`）は README の約 15 か所と `perf/dsl-mixed-memory.sh`（K-77） |
| `config/` | 検査の設定（`npm-build-tools.txt`・`license-header.txt` など） |
| `.github/` | CI（`workflows/ci.yml`）と Dependabot（`dependabot.yml`） |
| 最上位のファイル | `settings.gradle.kts`・`settings-gradle.lockfile`・`build.gradle.kts`・`gradle/libs.versions.toml`・`gradle/wrapper/`（Gradle 9.8.2）・`Dockerfile`・`compose.yaml`・`.env.example`・`.env.targetdb.example`・`.gitmodules`・`.gitleaks.toml`・`.pre-commit-config.yaml`・`.editorconfig`・`.gitattributes`・`.dockerignore`・`README.md`（1,678 行） |
| `aidlc/` | AI-DLC の記録（コードではない） |

## バックエンドのパッケージ（`backend/src/main/java/cherry/mastersmith/`）

| パッケージ | ファイル数 | 下位のパッケージ |
|---|---|---|
| `access` | 20 | `domain`・`service`・`web` |
| `appearance` | 14 | `config`・`service`・`web` |
| `audit` | 15 | `domain`・`repository`・`service` |
| `auth` | 58 | `domain`・`repository`・`service`・`web` |
| `businessdata` | 113 | `domain`・`repository`・`service`・`web` |
| `common` | 47 | `error`・`health`・`i18n`・`observability`・`paging`・`persistence`・`security`・`web` |
| `config` | 6 | なし |
| `dsl` | 64 | `domain`・`parse`・`service`・`validate` |
| `dslmanage` | 54 | `domain`・`generate`・`repository`・`service`・`web` |
| `group` | 55 | `domain`・`repository`・`service`・`store`・`web` |
| `invitation` | 65 | `domain`・`lock`・`repository`・`service`・`web` |
| `mail` | 28 | `config`・`domain`・`service`・`template`・`transport` |
| `navigation` | 23 | `domain`・`service`・`web` |
| `role` | 143 | `domain`・`repository`・`service`・`store`・`transfer`・`web` |
| `targetdb` | 43 | `config`・`domain`・`repository`・`service` |
| `user` | 70 | `domain`・`repository`・`service`・`web` |
| `useradmin` | 27 | `domain`・`service`・`web` |

- 起動クラスは `MastersmithApplication.java`。リソースは `backend/src/main/resources/`: `db/migration/`（Flyway の V1〜V13。V13 は業務データの監査の列）・`dsl/dsl-schema-v2.json`・`navigation/allowed-icons.txt`・`mail/templates/`・`application.yaml`・`logback-spring.xml`。

## 今回深く読んだ `businessdata` と `targetdb` の中身

- `businessdata/repository/`: `RowSqlBuilder`（`where`・`count`・`page`・`detail` の SQL）、`RowSqlExecutor`（実行。`search`・詳細・書き込み）、`RowDialect`（`MysqlFamilyRowDialect`・`PostgresRowDialect`）、`RowSqlFailureClassifier`、`RowWriteSqlBuilder`、`ColumnValueCodec`、`RowSearchResult`（`Page(long total, rows)` か `Failed`）。
- `businessdata/service/`: `BusinessDataReadService`（`definition`・`search`・詳細）、`BusinessDataWriteService`、`TableAccessResolver`、`SearchResult`・`WriteResult`、`BusinessDataProblemTypeCatalog`。
- `businessdata/web/`: `BusinessDataReadController`・`BusinessDataWriteController`・`BusinessDataPaths`、要求・応答の `record`（`RowPageResponse`・`SearchRequest` ほか）。
- `businessdata/domain/`: `PageRequest`（ページの大きさ 20・50・100）、`ContainsPattern`、`SearchRequestValidator`、`TableAccessView`、`ReadRejection`、`BusinessDataChangedEvent` ほか。
- `targetdb/config/`: `TargetDataSourceConfig`（読み取り）・`TargetWriteDataSourceConfig`（書き込み）・`TargetDbProperties`（`Pool`・`QueryTimeout`・`Write`）・`TargetDbSettings`・`TargetDbUsableCondition`。`targetdb/service/`: `TargetReadAccess`・`JdbcTargetReadAccess`・`TargetWriteAccess`・`JdbcTargetWriteAccess`・`TargetSchemaReader`・`JdbcTargetSchemaReader`。

## 選択肢（REFERENCE・LOOKUP）に関わる型の位置（K-74、2026-10-29）

- DSL の型: `dsl/domain/{OptionSource,OptionSourceKind,FormPart,LookupSearchItem,LookupListItem}`。写し: `dsl/service/DslModelMapper`（160〜180 行）。検証: `dsl/validate/DslSemanticValidator`（選択肢の節は 230〜290・375〜380 行）。スキーマ: `resources/dsl/dsl-schema-v2.json`（`options` は 271〜335 行、`formPart` の `lookup` は 160 行）。生成: `dslmanage/generate/DslTreeBuilder.reference()`（241〜250 行、REFERENCE だけ）。
- 業務データ側: `businessdata/domain/ColumnWriteRulesFactory`（`lookup` → `text` の置き換え、`choices()`）・`ColumnWriteRules`・`SearchOperatorPolicy`・`TableAccessPolicy`、応答は `businessdata/web/{ColumnDefinitionResponse,OptionResponse}`。外部キー違反の写し先は `RowWriteFailureReason` → `WriteRejection` → `BusinessDataWriteService`（245〜292 行）。
- 画面側: `frontend/src/features/tables/BusinessDataFormPage.tsx`（部品の選び方、780〜870 行）・`SearchPanel.tsx`・`BusinessDataTable.tsx`・`businessDataTypes.ts`。共有部品 `frontend/src/shared/`: `api-client`・`format`・`modal`・`null-value`・`page-size`・`paging`・`row-actions`・`scroll-region`・`slow-notice`・`tree`・`unsaved-guard`・`validation`。
- 境界の検査の位置: `backend/src/test/java/cherry/mastersmith/businessdata/` の `BusinessDataBoundaryArchitectureTest`（許可の一覧 `allowedDependencies` と `ALLOWED_STATEMENT_OWNERS`）、`targetdb` の `TargetDbBoundaryArchitectureTest`、`role` の `RoleBoundaryArchitectureTest`。今回はテストの本体を読まず、許可の一覧とファイル名の検索だけ。

## 層と型の決まり（`team.md` の Code Style と一致する形）

- `web`: controller と `record` の DTO。口ごとに `@ApiAccess`。個人に関する値・行の値を持つ型は `toString` を上書きして伏せる。
- `service`: トランザクションの境界。新しい機能は想定内の失敗を結果の型（sealed interface）で返し、controller が `switch` で `BusinessException` に変える（`SearchResult`・`WriteResult` はこの形）。
- `domain`: 変更できない値と列挙。問題の種類は `<機能>.domain.XxxProblemTypes`。
- `repository`: 内部DB は Spring Data JPA、対象DB の SQL は `businessdata/repository` の `RowSqlExecutor` に閉じる。

## 画面の構成（`frontend/src/`）

- `app/`: 骨組み（`registry/`・`navigation/`・`layout/`・`i18n/`・`routing/`・`work-role/` ほか）。
- `shared/`: 機能をまたぐ部品（`api-client/`・`format/`・`modal/`・`null-value/`・`page-size/`・`paging/`・`row-actions/`・`scroll-region/`・`slow-notice/`・`tree/`・`unsaved-guard/`・`validation/`）。
- `features/<featureId>/`: `admin`・`auth`・`dsl`・`groupadmin`・`invitation`・`mypermissions`・`preferences`・`registration`・`roleadmin`・`roletransfer`・`tables`・`useradmin` の 12 個と `README.md`。業務データの画面は `tables`（一覧 `BusinessDataListPage`・詳細・作成/更新の `BusinessDataFormPage`・表 `BusinessDataTable`・検索 `SearchPanel`・ページの計算 `listPaging.ts`・API の呼び出し `businessDataApi.ts`）。
- テストは対象と同じ場所に `*.test.ts(x)`。E2E は `frontend/e2e/NNN-*.e2e.ts`（010〜195、22 本。2026-11-01）と `support/`。

## 日時の型の置き場（K-94〜K-99、2026-11-03）

本文は `architecture.md` の「日時の値の流れ」。ファイルの位置だけをここに置く。

- サーバー: 受け渡しの型 `businessdata/domain/ValueType.java`（`DATE`・`DATETIME`・`DATETIME_TZ`・`TIME`・`TIME_TZ` ほか 12 値）と `ColumnKind.java`（`fractionDigits`）、対応と読み書きの1か所 `businessdata/repository/ColumnValueCodec.java`（604 行）、DB の種類の差 `RowDialect.java`・`MysqlFamilyRowDialect.java`・`PostgresRowDialect.java`、接続の時差 `targetdb/config/TargetDataSourceConfig.java`・`TargetWriteDataSourceConfig.java`、生成の型の分類 `dslmanage/generate/TypeCategoryMapping.java`（日時は `datetime`・`timestamp`・`timestamptz`、時刻は `time`・`timetz`。初期値は日時が `formPart: datetime`・`list.format: DATETIME`、時刻が `formPart: text`・`list.format: TIME`、日付が `formPart: date`・`list.format: DATE`）、DSL の型 `dsl/domain/{DbType,FormPart,ListFormat}.java`。
- 画面: 見せ方 `features/tables/valueFormat.ts`、入力の初めの値と送る形 `features/tables/formDiff.ts`、入力の部品 `features/tables/BusinessDataFormPage.tsx`（880〜965 行）、検索 `features/tables/SearchPanel.tsx`・`searchConditions.ts`、入力の確かめ `features/tables/formValidation.ts`、文言 `features/tables/messages.ts`（`tables.form.utc`）。別の系統の書式 `shared/format/formatDateTime.ts`（DSL・招待・利用者の管理が使う）。
- テスト（ファイル名の grep だけ）: サーバーは `businessdata/repository/{ColumnValueCodecTest,RowDialectTest}`・`businessdata/domain/ValueTypesTest`・`businessdata/web/{AbstractBusinessDataReadIT,AbstractBusinessDataWriteIT}`・`businessdata/testsupport/{BusinessDataReadTables,BusinessDataWriteTables}`・`targetdb/config/{TargetDataSourceConfigTest,TargetWriteDataSourceConfigTest}`・`dslmanage/generate/TypeCategoryMappingTest`。画面は `features/tables/{valueFormat,formDiff,formValidation,searchConditions,rowLabel.property,BusinessDataDetailPage}.test.*`。

## 表示のタイムゾーンと日時の変換の置き場（K-106〜K-110、2026-11-06）

本文は `architecture.md` の「表示のタイムゾーンの選択肢の組み立て」と `code-quality-assessment.md` の K-108・K-109。ファイルの位置と分類だけをここに置く（パスは `frontend/` の下）。

| 場所 | 分類 | 中身 |
|---|---|---|
| `src/shared/format/timeZone/index.ts` | 公開の入口 | `formatInstant`・`compareInstants`・`preserveInstant`・`parseLocalDateTime`・`canInterpretTimeZone` と結果の型だけを名前付きで出す |
| `src/shared/format/timeZone/timeZoneGate.ts`（98 行） | Utility | 名前の検査と `Intl.DateTimeFormat` のキャッシュ（上限 128）。`getFormatter`・`clearFormatterCache`・`formatterCacheSize` は入口から出さない（テストは直接読む） |
| `src/shared/format/timeZone/wallClock.ts`（173 行） | Utility | 壁時計と時差の秒（`wallAt`・`offsetSecondsAt`）、壁時計 → 候補の時点（`instantCandidates`）、先発グレゴリオ暦の日数の計算 |
| `src/shared/format/timeZone/formatInstant.ts`（76 行）・`instantText.ts`（131 行）・`parseLocalDateTime.ts`（107 行）・`types.ts`（43 行） | Utility・Model | 時点 → 表示の文字、時点の文字の読み書きと比べ、ローカル時刻 → 時点、結果の union の型 |
| `src/shared/format/formatInstantToMinutes.ts`・`dateTimeInput.ts` | Utility | `formatInstant`・`parseLocalDateTime` の上の薄い関数（分までの表示、業務データの日時の入力） |
| `src/features/preferences/buildTimeZoneOptions.ts`（101 行） | Utility（純粋な関数） | 表示のタイムゾーンの選択肢の組み立てと名前の一覧の取り出し |
| `src/features/preferences/TimeZoneSelect.tsx`（85 行） | 画面の部品 | 選択（make-you-chic-ui の `FormField`・`Select`・`Alert`）。`PreferencesForm.tsx` が置く |
| `src/features/preferences/` のほか | 画面・フック・API・純粋な関数 | `registration.ts`（`/me/preferences`・`/me/password`、遅延読み込み）・`PreferencesPage.tsx`・`PreferencesForm.tsx`・`usePreferencesForm.ts`・`preferencesApi.ts`・`errorMessages.ts`・`formChecks.ts`・`failure.ts`・`messages.ts`・パスワードの変更の部品・`testing/`（描画の補助と見本） |
| テスト | Test | `src/features/preferences/{buildTimeZoneOptions,TimeZoneOptionsPerformance,TimeZoneSelect}.test.*` ほか、`src/shared/format/timeZone/*.test.ts`（`conversionPerformance`・`conversionProperties`・`conversionBoundary`・`timeZoneGate` ほか） |
| `vitest.config.ts`（80 行） | Configuration | projects 2 つ（`eslint-config`・`unit`）、V8 のカバレッジ、`thresholds` 80・70 |

- 時差の ESLint の決まり: `eslint-rules/timeZoneSelectors.js`（S1〜S4。`Intl.DateTimeFormat` は `timeZone` を明示し、時刻の整形は `src/shared/format/timeZone` を使う）と、`eslint.config.js` の `timeZoneConversionRules`（211 行から。`src/shared/format/timeZone/**/*.ts` は純粋な関数だけの置き場）。

## 画面の骨組みの仕上げ（K-100・K-102〜K-104、2026-11-03）

### K-100 サイドバーのアイコン

- 使えるアイコンは make-you-chic-ui の `Icon` の 18 種（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Icon/registry.ts`: `menu`・`chevron-down`・`chevron-up`・`close`・`check`・`bell`・`user`・`search`・`edit`・`trash`・`download`・`settings`・`home`・`list`・`info`・`success`・`warning`・`danger`）。テーブル・ビュー・データベースらしい名前は無い。`SidebarNavItem.icon` の型は `IconName` で、ほかの絵は渡せない。
- 写しは2か所: 画面の `app/registry/allowedIcons.ts`（`satisfies readonly IconName[]` と `ALL_ICONS_COVERED` で両方向を型で守る）と、サーバーの `backend/src/main/resources/navigation/allowed-icons.txt`（`AllowedNavIconList` が読み、`NavIconPolicy` が照らす）。一致は `app/registry/allowedNavIcons.test.ts` が確かめる。既定は `list`（`NavIconPolicy.DEFAULT_ICON`・`buildNavSections.ts` の `DEFAULT_ICON`）。
- 管理の区画の項目は、機能の登録の `sidebarItems`（`section: 'ADMIN'`）から作る。7 件（`admin` 200「管理」・`dsl` 210・`invitation` 220・`useradmin` 230・`roleadmin` 240・`groupadmin` 250・`roletransfer` 260。数字は `order`）のどれも `icon` を置かないため、すべて `list`。`SidebarItemRegistration.icon` は任意の `IconName`（`app/registry/types.ts`）。
- 業務のメニューは DSL の `menus[].icon`（任意、`dsl-schema-v2.json` 77 行）。既定の DSL の生成（`dslmanage/generate/DslTreeBuilder` 86〜95 行）は `icon` を書かないため、生成したままでは `list`。ホームの項目だけが `home`（`buildNavSections.ts`）。

### K-102 アプリのアイコンとページの頭

- `frontend/index.html` は `<title>MasterSmith</title>` だけで、favicon の `<link rel="icon">` が無い。`frontend/public/` も無い。画面ごとに `document.title` を変える処理は無い（grep）。
- サーバーの `config/WebConfig.java`（流し読み）は、見つからない画面の URL に `index.html` を返す。`/favicon.ico` への要求には HTML が返る見込み（未検証）。
- アプリの名前の文字 `app.name`（`MasterSmith`）はログインの画面（`app/layout/LoginLayout.tsx`）と登録の完了の画面だけが出す。アプリシェル（`ShellLayout.tsx` → make-you-chic-ui の `AppShell`）にはロゴ・アプリ名の口が無く、トップバーの `topbarStart`・`topbarEnd` の差し込み口だけ（今は `topbarEnd` に作業ロールの切り替え）。
- SVG を置くときのライセンスヘッダーの検査の扱いは確かめていない。

### K-103 ホームの画面

- `app/pages/HomePage.tsx`（88 行）: 見出し「ホーム」と説明「MasterSmith へようこそ。左のメニューから機能を選んでください。」に、業務のメニューの状態で案内（未設定・作業ロールが無い・見てよいテーブルが無い・読み込みの失敗と［もう一度］）を足すだけ。業務のメニューがあるときは、見出しと1文だけになる。
- 使える材料（骨組みの文脈）: `useBusinessNavigation()`（業務のメニューの木と状態）、`useWorkRole()`（作業ロール）、`useHasMyPermissions()`（自分の権限の画面の有無）、登録の一覧（`useFeatureRegistry` 相当。流し読み）。ホームの道 `/` は骨組みが持つ（`app/registry/validateRegistrations.ts` の `HOME_PATH`、`app/routing/AppRouter.tsx`）。

### K-104 管理の仮の画面

- `features/admin/`: `registration.ts`（道 `/admin`、`access: 'ADMIN'`、サイドバーの管理の区画の先頭 `order: 200`、ラベル「管理」＝区画の見出しと同じ名前）、`AdminAreaPage.tsx`（表示のたびに `GET /api/admin/check` を呼び、成功で `AdminPlaceholder`、403 は骨組みの `useAdminForbidden` に渡す、ほかの失敗は Alert）、`adminApi.ts`・`adminAreaStatus.ts`、`AdminPlaceholder.tsx`（見出し「管理」と「今後の管理機能はこの画面に加わります。現在は表示できる機能がありません。」）。
- 頼っているもの: E2E の 030（`admin-area-page`・`admin-placeholder` と文言「今後の管理機能」、見出し「管理」）・130（サイドバーの項目「管理」を `SIDEBAR_ITEM` に使い、`/api/admin/check` を 403 に差し替える）・090（「管理」のリンクと `/admin` の道）と `e2e/support/userAdminDiagnostics.ts`。画面のテストは `app/layout/ShellLayout.test.tsx`・`app/routing/AppRouter.test.tsx`・`app/registry/validateRegistrations.test.ts`・`app/navigation/{navigationItems.test,buildNavSections.property.test}.ts`・`app/admin-forbidden/{AdminForbiddenProvider.test.tsx,forbiddenHeading.test.ts}`・`shared/api-client/adminForbidden.test.ts`・`features/{dsl,invitation,useradmin}/registration.test.tsx`（どれも grep の当たり。中身は読んでいない）。
- サーバーの `access/web/AdminCheckController`（`GET /api/admin/check`、`@ApiAccess(ADMIN)`）は k6 の `monitorLight`（`perf/k6/scenarios.js`）と E2E 130 も使うため、画面を消しても API は残せる。管理の 403 の画面の見出し `adminForbidden.heading` も「管理」。

## Sources

- 2026-11-06: 開発担当の調査 `aidlc/spaces/default/intents/261104-tz-options-perf/inception/reverse-engineering/developer-scan.md`（ファイルの読み取り・`ls`・`wc`・grep）。
- 2026-11-03: 開発担当の調査 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/reverse-engineering/developer-scan.md`（ファイルの読み取りと grep）。
- 2026-11-01: `ls`・`wc`・`git submodule status`（開発担当の調査 `aidlc/spaces/default/intents/261101-maintenance-update/inception/reverse-engineering/developer-scan.md`）。
- `find`・`ls`・`git ls-files` の数（アーキテクト、2026-10-25）と開発担当の Packages Found。
