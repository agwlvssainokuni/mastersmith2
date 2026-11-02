# 開発担当のコードスキャン（Intent 261102-timezone-ui-polish）

- 実施日: 2026-11-03
- 記録するコミット: `fa0b01e6942b183247022169adc1aded47c4e225`（`develop` の HEAD）
- 走査の広さ・深さ: 範囲を絞ったスキャン（Focused scan）・Standard。既存の知識ベースは STALE。設計担当が構成に無いため、9 文書の候補も開発担当が書いた（`.aidlc-engine/codekb-stage-mastersmith2/`）。
- 確かめ方: ファイルの読み取り（sed・grep・find・wc）と読み取りの git（`rev-parse`・`submodule status`）だけ。ビルド・テスト・プログラムの実行、`.env`・`evidence/`・`~/.mastersmith-backup` の閲覧はしていない。`vendor/` の中は読み取りだけ（流し読み）。
- 事実と見立てを分け、見立てには「未検証」と書いた。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**:
  - `backend/src/main/java/cherry/mastersmith/businessdata/repository/ColumnValueCodec.java`
  - `backend/src/main/java/cherry/mastersmith/businessdata/repository/RowDialect.java`
  - `backend/src/main/java/cherry/mastersmith/businessdata/repository/MysqlFamilyRowDialect.java`
  - `backend/src/main/java/cherry/mastersmith/businessdata/repository/PostgresRowDialect.java`
  - `backend/src/main/java/cherry/mastersmith/businessdata/domain/ValueType.java`
  - `backend/src/main/java/cherry/mastersmith/businessdata/domain/ColumnKind.java`
  - `backend/src/main/java/cherry/mastersmith/businessdata/web/ColumnDefinitionResponse.java`
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`
  - `backend/src/main/java/cherry/mastersmith/dsl/domain/DbType.java`・`FormPart.java`・`ListFormat.java`・`DslColumn.java`・`DslTable.java`・`DslMenuItem.java`・`TableRef.java`・`DslModel.java`・`ListSetting.java`・`DetailSetting.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/generate/TypeCategoryMapping.java`・`TypeCategory.java`
  - `backend/src/main/java/cherry/mastersmith/navigation/domain/NavNode.java`・`NavTableRef.java`・`NavIconPolicy.java`・`MenuFilter.java`・`NavigationMenu.java`
  - `backend/src/main/java/cherry/mastersmith/navigation/service/NavigationService.java`
  - `backend/src/main/java/cherry/mastersmith/navigation/web/NavNodeResponse.java`・`NavTableRefResponse.java`・`NavigationController.java`
  - `backend/src/main/resources/navigation/allowed-icons.txt`
  - `frontend/index.html`
  - `frontend/src/app/registry/allowedIcons.ts`・`types.ts`
  - `frontend/src/app/navigation/buildNavSections.ts`・`navigationItems.ts`
  - `frontend/src/app/pages/HomePage.tsx`
  - `frontend/src/app/i18n/messages/ja.ts`
  - `frontend/src/app/layout/ShellLayout.tsx`（AppShell に渡す部分）
  - `frontend/src/features/admin/registration.ts`・`AdminAreaPage.tsx`・`adminApi.ts`・`adminAreaStatus.ts`・`AdminPlaceholder.tsx`
  - `frontend/src/features/tables/valueFormat.ts`・`formDiff.ts`
  - `frontend/src/features/tables/searchConditions.ts`（105〜215 行の型ごとの読み取りと範囲の向き）
  - `frontend/src/features/tables/SearchPanel.tsx`（`inputAttributes`）
  - `frontend/src/features/tables/BusinessDataFormPage.tsx`（880〜965 行の入力の部品の選び方と案内の文）
  - `frontend/src/shared/format/formatDateTime.ts`
  - `.gitmodules`
- **Skimmed only**:
  - `backend/src/main/java/cherry/mastersmith/businessdata/`（ほかのファイル。`WriteRequestValidator` の 295〜345 行・`ColumnWriteRulesFactory` の 40〜80 行・`RowSqlExecutor`・`ReferenceLookup`・`BusinessDataWriteService` の codec の呼び出しは grep）
  - `backend/src/main/java/cherry/mastersmith/targetdb/`（`TargetWriteDataSourceConfig` の 113 行、`MysqlSchemaQueries`・`PostgresSchemaQueries` の問い合わせの列は grep）
  - `backend/src/main/java/cherry/mastersmith/dsl/`（`validate/`・`service/DslModelMapper` は grep）
  - `backend/src/main/java/cherry/mastersmith/dslmanage/`（`generate/DslTreeBuilder` は grep）
  - `backend/src/main/java/cherry/mastersmith/navigation/`（`AllowedNavIconList` ほかは一覧）
  - `backend/src/main/resources/application.yaml`（時刻・時差の語の grep）
  - `backend/src/main/resources/dsl/`（`dsl-schema-v2.json` の `icon`・`format`・`view` の grep）
  - `docker/targetdb/`（3種類の `01-sample-schema.sql` の日時の列の grep）
  - `frontend/src/app/`（ほかのファイル。`App.tsx`・`routing/`・`navigation/navigationApi.ts` は一覧と grep）
  - `frontend/src/features/tables/`（`formValidation.ts` の 125〜150 行、`messages.ts` の `tables.form.utc`、ほかは一覧）
  - `frontend/src/features/dsl/`（`formatDateTime` と `Icon` の使い方の grep）
  - `frontend/src/shared/`（一覧）
  - `frontend/e2e/`（`/admin`・`admin-placeholder`・「管理」の grep）
  - `backend/src/test/java/cherry/mastersmith/`（日時の型・時差・`'list'` を確かめるテストのファイル名の grep）
  - `backend/src/main/java/cherry/mastersmith/access/web/AdminCheckController.java`・`config/WebConfig.java`（grep）
  - `Dockerfile`・`compose.yaml`・`backend/build.gradle.kts`（時差の設定の grep）
  - `perf/`（`/api/admin/check` の grep）
  - `vendor/make-you-chic-ui/`（`packages/make-you-chic-ui/src/components/Icon/registry.ts` と `AppShell/{AppShell,Topbar,Sidebar}.tsx` の props。読み取りだけ）
- **Left out**: `frontend/node_modules/`・`frontend/dist/`・`frontend/coverage/`・`frontend/test-results/`・`vendor/make-you-chic-ui/node_modules/`（依存とビルドの出力。開いていない）

### Packages Found

- `businessdata` — Spring の機能（web・service・domain・repository） — Java — 業務データの読み書き。型の対応は `repository/ColumnValueCodec` の1か所、DB の種類の差は `RowDialect`（`MysqlFamilyRowDialect`・`PostgresRowDialect`）。
- `targetdb` — Spring の機能（config・domain・repository・service） — Java — 対象DB の接続（読み取りと書き込みのプール）とスキーマの読み取り。
- `dsl` — Spring の機能（domain・parse・service・validate） — Java — DSL の型と検証。
- `dslmanage` — Spring の機能（domain・generate・repository・service・web） — Java — 既定の DSL の生成ほか。
- `navigation` — Spring の機能（domain・service・web） — Java — 業務のメニューを作業ロールで絞る（`GET /api/me/navigation`）。
- `frontend/src/app` — 画面の骨組み — TypeScript・React — 登録・サイドバーの区画・ホーム・レイアウト・文言。
- `frontend/src/features/admin` — 画面の機能 — TypeScript・React — 管理の仮の画面（`/admin`）。
- `frontend/src/features/tables` — 画面の機能 — TypeScript・React — 業務データの一覧・詳細・作成・更新。
- `frontend/src/shared` — 共通の部品 — TypeScript — `format/formatDateTime` など。

### Build System

- **Type**: Gradle（Kotlin DSL）と npm（Vite）。今回は版を読んでいない（前回の記録のまま）。
- **Config Files**: 今回の範囲では `backend/build.gradle.kts` の 118 行（テストの JVM の `user.timezone`）だけを grep した。
- **Build Dependencies**: 変わっていない（前回の記録）。

### APIs Discovered

- REST — `navigation/web/NavigationController` — `GET /api/me/navigation`（1本）。応答の項目は `id`・`label`・`icon`・`table`（`schemaName`・`tableName`）・`navigable`・`items`。テーブルかビューかの印は無い。
- REST — `access/web/AdminCheckController`（範囲の外、grep） — `GET /api/admin/check`（管理者だけ。管理の仮の画面・E2E 130・k6 の `monitorLight` が使う）。
- REST — `businessdata/web`（前回の記録） — 定義の応答の `ColumnDefinitionResponse` が `valueType`・`listFormat` を返す。秒の端数の桁の上限（`ColumnKind.fractionDigits`）は応答に載らない。

### Frameworks & Libraries

- make-you-chic-ui — 固定先 `cae5fed185541b975dc56fb034dae3212d4c2247`（`git submodule status`。前回の記録 `cab4207` から進んでいる） — デザインシステム。`Icon` の名前は `registry.ts` の 18 種、`SidebarNavItem.icon` は `IconName` だけを受ける。`AppShell` は `topbarStart`・`topbarEnd` の差し込み口を持つが、ロゴ・アプリ名の口は無い。
- java-mustache-processor — 固定先 `d61ba6dac8b4a1617ba72ee469ee0189683e42b4`（`0.1.0-6-gd61ba6d`。前回の記録 `8d44c36` から進んでいる） — 今回の範囲の外。
- JDBC ドライバー（MySQL Connector/J・MariaDB Connector/J・PgJDBC） — 版は前回の記録 — 日時の読み書きの振る舞いに関わる（下の Handoff Summary）。

### Test Coverage

- **Test Directories**: `backend/src/test/java/cherry/mastersmith/businessdata/`（`ColumnValueCodecTest`・`RowDialectTest`・`ValueTypesTest`・`AbstractBusinessDataReadIT`・`AbstractBusinessDataWriteIT`・`testsupport/BusinessDataReadTables`・`BusinessDataWriteTables` が `DATETIME_TZ`・`TIME_TZ`・`timestamptz`・`timetz` を扱う）、`backend/src/test/java/cherry/mastersmith/targetdb/config/`（`TargetDataSourceConfigTest`・`TargetWriteDataSourceConfigTest` が時差の初期化の文を扱う）、`dslmanage/generate/TypeCategoryMappingTest`。画面は `features/tables/{valueFormat,formDiff,formValidation,searchConditions,rowLabel.property,BusinessDataDetailPage}.test.*` が `+00:00`・`DATETIME_TZ` を扱う。アイコンの `'list'` は `app/layout/ShellLayout.test.tsx`・`app/navigation/{buildNavSections,navigationApi,BusinessNavigationProvider}.test.*`・`app/registry/allowedNavIcons.test.ts` ほか。どれもファイル名の grep だけで、中身は読んでいない。
- **Test Frameworks**: JUnit・jqwik・Testcontainers、Vitest・fast-check・Playwright（前回の記録）。
- **Coverage Config**: present（前回の記録）。

### Code Quality Indicators

- **Linting**: 前回の記録のまま（今回は読んでいない）。
- **CI/CD**: 前回の記録のまま。
- **Documentation**: クラス・関数の Javadoc・JSDoc は日本語で、要件・設計の ID（BR7.1 など）を引いている。`ColumnValueCodec` の頭に型の対応の1か所であることが書いてある。

### Technical Debt Signals

- 業務データの時差つきの値は、画面で UTC の文字のまま見せ、UTC で入れさせている（`valueFormat.ts` の `formatTemporal`、`formDiff.ts` の `toWireValue`、`BusinessDataFormPage.tsx` 894〜896 行の `tables.form.utc`「UTC で入れる」）。ほかの画面（DSL・招待・利用者の管理）は `shared/format/formatDateTime.ts` で端末の時差に直して略号を添えており、二系統になっている（N6 の主題）。
- 対象DB の接続の時差が DB の種類で違う: MySQL・MariaDB は読み取り・書き込みの両方のプールで接続を作るときに `SET time_zone = '+00:00'`（`TargetDataSourceConfig.java` 61・68・150・151 行、`TargetWriteDataSourceConfig.java` 113・114 行）。PostgreSQL はセッションの時差を設定せず、PgJDBC が JVM の既定（`Dockerfile` 52 行の `-Duser.timezone=Asia/Tokyo`、`compose.yaml` 37 行の `TZ: Asia/Tokyo`、`backend/build.gradle.kts` 118 行のテストの JVM）を送る（PgJDBC の振る舞いは未検証）。
- 一覧の書式 `ListFormat` の `DATE`・`DATETIME`・`TIME` は、画面の `valueFormat.ts` で使われていない（真偽値と `NUMBER_GROUPED` だけを見る）。`ListFormat.java` の頭の「書式の中身は画面の Intent で決める」のまま。
- 既定の DSL の生成は MySQL・MariaDB の `DATA_TYPE`（例 `datetime`）だけを型の名前に写し、日時の精度（`DATETIME_PRECISION`）を読まない（`MysqlSchemaQueries` の問い合わせの列に無い）。`ColumnValueCodec.fractionDigits` は名前の `(n)` → DSL の精度 → DB の種類の既定（MySQL・MariaDB は 0）の順のため、`datetime(6)` の列でも生成した DSL では端数の上限が 0 になる見込み（未検証）。
- PostgreSQL の型の名前を手で `timestamp(3) with time zone` と書くと、`RowDialect.baseName` が `(` から後を落として `timestamp` になり、DATETIME（時差なし）として扱われる（`ColumnValueCodec.java` の `valueType`。生成は `udt_name` の `timestamptz` を書くため、手書きの DSL のときだけ）。
- 管理の区画の登録 7 件（`admin`・`dsl`・`invitation`・`useradmin`・`roleadmin`・`groupadmin`・`roletransfer`）はどれも `icon` を置かず、`buildNavSections.ts` の `resolveNavIcon` が既定の `list` を当てる。業務のメニューも、生成した DSL は `icon` を書かない（`DslTreeBuilder` 86〜95 行）ため `NavIconPolicy.DEFAULT_ICON`（`list`）になる。
- 管理の仮の画面（`features/admin`）は `/api/admin/check` を呼んで「今後の管理機能はこの画面に加わります。現在は表示できる機能がありません。」を出すだけ。
- `frontend/index.html` に favicon の `<link>` が無く、`frontend/public/` も無い。`title` は `MasterSmith` に固定で、画面ごとに変えない。

## Handoff Summary

- **Intent-relevant finding**:
  - (1) N6: 日時の型の対応は `ColumnValueCodec.valueType`（`ColumnValueCodec.java` 266〜330 行）の1か所。MySQL・MariaDB は `timestamp` → `DATETIME_TZ`、`datetime` → `DATETIME`、`date` → `DATE`、`time` → `TIME`。PostgreSQL は `timestamptz`・`timestamp with time zone` → `DATETIME_TZ`、`timestamp`・`timestamp without time zone` → `DATETIME`、`timetz`・`time with time zone` → `TIME_TZ`、`time`・`time without time zone` → `TIME`、`date` → `DATE`。時差つきの2つは UTC にそろえて `+00:00` 付きの文字で返し（`format`・`readCell`）、入力はオフセットが無ければ UTC と読む（`parseDateTimeTz`・`parseTimeTz`）。MySQL・MariaDB の `timestamp` は接続の `time_zone='+00:00'` の上で時差の無い日時として読み書きし（`MysqlFamilyRowDialect.java` 88〜98 行）、日付・日時・時刻はドライバーの変換を通さず文字で読む（`readsTemporalAsText`）。PostgreSQL は `OffsetDateTime`・`OffsetTime`・`LocalDateTime` などで読み書きする（`PostgresRowDialect.java`）。画面は UTC の文字のまま `T` を空白に替えて見せ（`valueFormat.ts` 69〜75・120〜126 行）、時差つきの日時は `datetime-local` に UTC で入れさせて `+00:00` を付けて送る（`formDiff.ts` 115〜120 行、`BusinessDataFormPage.tsx` 894〜896 行）。検索も同じ（`SearchPanel.tsx` の `inputAttributes`、`searchConditions.ts` は時差つきの範囲の向きを画面で判定しない）。
  - (2)〜(6) 画面: アイコンは make-you-chic-ui の 18 種だけ（`registry.ts`、写しが `allowedIcons.ts` と `allowed-icons.txt`、一致は `allowedNavIcons.test.ts`）。業務のメニューの応答（`NavNodeResponse`）はテーブルかビューかを持たないが、`MenuFilter` は `DslTable.view` を `DslModel.findTable` で引ける位置にある。管理の区画の項目はどれも `list`。ホームは見出しと1文と空のメニューの案内だけ（`HomePage.tsx`）。管理の仮の画面は `/admin` で確認の API を呼ぶだけ。favicon は無い。
- **Risks / follow-up**:
  - 主キー・参照の値・検索の値・作成の応答の主キー・監査に残す値は、サーバーの UTC の文字のまま往復している（`RowSqlExecutor` 288 行・`BusinessDataWriteService` 458・516・825 行の `ColumnValueCodec.format`、画面の行の鍵）。表示だけを地方時に替えるなら値の往復は変えない。入力も地方時にするなら、オフセットを付けて送る（サーバーは `±hh:mm` を受け付ける）か、サーバーで直すかを設計で決める。`datetime-local` は時差を持たず、夏時間の切り替えで存在しない・二重の時刻が出うる（`Asia/Tokyo` には無いが、端末の時差に従うなら起こりうる。未検証）。
  - 時差の無い型（MySQL・MariaDB の `datetime`、PostgreSQL の `timestamp`）の値がどの時差の壁時計かは、アプリからは分からない。見本の MySQL・MariaDB は `created_at DATETIME DEFAULT CURRENT_TIMESTAMP` で、アプリの接続（`+00:00`）から作った行と、別の接続から作った行で意味が変わりうる（未検証）。N6 で「変換するか」の対象を型ごとに決める必要がある。
  - PostgreSQL のセッションの時差が JVM の既定に従う点は、`timestamptz` の読み書き（`OffsetDateTime` で UTC にそろえる）には効かないが、`timestamp` との比べ・DB の既定値（`CURRENT_TIMESTAMP` の `timestamp` への変換）には効く（未検証）。MySQL・MariaDB とそろえるかは設計の判断。
  - 秒の端数: 生成した DSL の MySQL・MariaDB の日時は端数の上限 0 になりうる（上の負債）。`formDiff.ts` の `toDateTimeInput` は更新の初めの値から端数を落とす（`datetime-local` の `step=1` は秒まで）。
  - MySQL の `time` は 24 時間を超える値・負の値を持てるが、`LocalTime.parse` で読めず `CellValue.Unreadable` になる（`readsTemporalAsText` の経路）。MySQL の `year`・PostgreSQL の `interval` は `OTHER`（読み取りだけの文字）。N6 の範囲の端として扱いを決める。
  - アイコン: テーブル・ビューらしい名前は 18 種に無く、`SidebarNavItem.icon` は `IconName` だけを受けるため、足すには make-you-chic-ui の上流への依頼と固定先の更新が要る（`team.md` の Way of Working）。足したら `allowedIcons.ts`（型で両方向を守る）と `allowed-icons.txt`（テストで一致）の両方を直す。ビューで変えるには、応答に印を足すか、DSL の `icon` で決めるかを設計で決める。
  - 管理の仮の画面を廃止するときの影響: E2E 030（`admin-placeholder` の文言と見出し「管理」）・130（サイドバーの「管理」の項目を `SIDEBAR_ITEM` に使う）・090（「管理」のリンクと `/admin` の道）・`e2e/support/userAdminDiagnostics.ts`、単体テスト（`ShellLayout.test.tsx`・`AppRouter.test.tsx`・`validateRegistrations.test.ts`・`navigationItems.test.ts`・`buildNavSections.property.test.ts`・`AdminForbiddenProvider.test.tsx`・`forbiddenHeading.test.ts`・`adminForbidden.test.ts`・機能ごとの `registration.test.tsx` など）。`GET /api/admin/check` は k6 の `monitorLight` と E2E 130 も使うため、画面を消してもサーバーの API は残せる。
  - favicon: `config/WebConfig.java` は見つからない画面の URL に `index.html` を返すため、今は `/favicon.ico` に HTML が返る見込み（未検証）。置き場（`frontend/public/` か `index.html` のデータ URL か）と WAR への同梱を設計で決める。ライセンスヘッダーの検査の対象に SVG が入るかは未確認。
  - サブモジュールの固定先が前の記録から進んでいる（make-you-chic-ui `cae5fed`、java-mustache-processor `d61ba6d`）。知識ベースの `cab4207`・`8d44c36` は古い。
