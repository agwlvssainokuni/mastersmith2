# 開発担当のコードの調査（Intent 261016-business-data-views）

- 調べた時点のコミット: `develop` の HEAD `09b795d2a2801d603edbe4c1cd7a3bb5c9b85d77`（`git rev-parse HEAD` で確かめた。スナップショットの source の識別 `git:d0c91215…` とは別の値）。
- 調べ方: 依頼者の選択は Full rescan（対象はリポジトリ全体 `./`）、深さ Standard。全体の構成は `git ls-files` とディレクトリの一覧で把握し、今回の Intent に関わる範囲だけを深く読んだ。読み取りだけで行い、このファイルのほかは何も変えていない（`./gradlew`・`npm`・`docker build`・git の状態を変える操作は使っていない）。
- サブモジュールの状態: `vendor/make-you-chic-ui` は `a9309c4`（`git submodule status`）、`vendor/java-mustache-processor` は `8d44c36`（0.1.0）。`b0a1e79` はサブモジュールの手元の履歴にあり、`git diff` と `git log` で読めた。
- 部品の名前は既存の知識ベース（`aidlc/spaces/default/codekb/mastersmith2/component-inventory.md`）の ID を使った。新しい ID は足していない（業務データの機能はまだコードに無いため）。
- 所見の番号: 既存の知識ベースの最後は K-48 のため、今回は **K-49〜K-61** とした。各所見で「確かめた事実」と「見立て（未検証）」を分けて書いた。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**:
  - backend/src/main/java/cherry/mastersmith/role/domain/MainPermission.java
  - backend/src/main/java/cherry/mastersmith/role/domain/PermissionLevel.java
  - backend/src/main/java/cherry/mastersmith/role/domain/PermissionValues.java
  - backend/src/main/java/cherry/mastersmith/role/domain/EffectivePermission.java
  - backend/src/main/java/cherry/mastersmith/role/domain/EffectiveNode.java
  - backend/src/main/java/cherry/mastersmith/role/domain/PermissionTarget.java
  - backend/src/main/java/cherry/mastersmith/role/domain/PermissionInheritance.java
  - backend/src/main/java/cherry/mastersmith/role/domain/PermissionSnapshot.java
  - backend/src/main/java/cherry/mastersmith/role/service/EffectivePermissionResolver.java
  - backend/src/main/java/cherry/mastersmith/role/service/EffectivePermissionResolverImpl.java
  - backend/src/main/java/cherry/mastersmith/navigation/domain/MenuFilter.java
  - backend/src/main/java/cherry/mastersmith/navigation/domain/NavNode.java
  - backend/src/main/java/cherry/mastersmith/navigation/domain/NavTableRef.java
  - backend/src/main/java/cherry/mastersmith/navigation/domain/TableAccessResult.java
  - backend/src/main/java/cherry/mastersmith/navigation/domain/EmptyReason.java
  - backend/src/main/java/cherry/mastersmith/navigation/service/NavigationService.java
  - backend/src/main/java/cherry/mastersmith/navigation/web/NavigationController.java
  - backend/src/main/java/cherry/mastersmith/navigation/web/TableAccessResponse.java
  - backend/src/main/java/cherry/mastersmith/navigation/web/NavTableRefResponse.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DslModel.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DslSchema.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DslTable.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DslColumn.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DbType.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/FormPart.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/SearchSetting.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/SearchOperator.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/ListSetting.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/ListFormat.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DetailSetting.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/OptionSource.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/OptionSourceKind.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/Validation.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DslForeignKey.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/LookupListItem.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/LookupSearchItem.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DslMenuItem.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/TableRef.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DslFormat.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/ActiveDsl.java
  - backend/src/main/java/cherry/mastersmith/dsl/service/ActiveDslModelProvider.java
  - backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java
  - backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java
  - backend/src/main/java/cherry/mastersmith/targetdb/domain/ReadPurpose.java
  - backend/src/main/java/cherry/mastersmith/targetdb/domain/SqlIdentifiers.java
  - backend/src/main/java/cherry/mastersmith/targetdb/service/JdbcTargetSchemaReader.java
  - backend/src/main/java/cherry/mastersmith/common/security/ApiAccess.java
  - backend/src/main/java/cherry/mastersmith/common/security/ApiAccessLevel.java
  - backend/src/main/java/cherry/mastersmith/common/security/ApiDefaultAccess.java
  - backend/src/main/java/cherry/mastersmith/common/security/ErrorResponseWriter.java
  - backend/src/main/java/cherry/mastersmith/common/security/SecurityExtensionValidator.java
  - backend/src/main/java/cherry/mastersmith/common/security/SecurityRuleContributor.java
  - backend/src/main/java/cherry/mastersmith/access/web/AdminAuthorizationManager.java
  - backend/src/main/java/cherry/mastersmith/access/web/AdminSecurityContributor.java
  - backend/src/main/java/cherry/mastersmith/access/web/AdminApiDefaultAccess.java
  - backend/src/main/java/cherry/mastersmith/access/domain/AdminPaths.java
  - backend/src/main/java/cherry/mastersmith/access/domain/AccessDeniedReason.java
  - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java
  - backend/src/main/resources/db/migration/V10__u3_group.sql
  - backend/src/main/resources/db/migration/V11__u4_role.sql
  - backend/src/main/resources/db/migration/V12__u4_role_assignment.sql
  - backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java
  - backend/src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java
  - frontend/src/features/tables/registration.ts
  - frontend/src/features/tables/TablePlaceholderPage.tsx
  - frontend/src/features/tables/useTableAccess.ts
  - frontend/src/features/tables/tableAccessApi.ts
  - frontend/src/app/navigation/tablePlaceholderPath.ts
  - frontend/src/app/registry/types.ts
  - frontend/src/features/useradmin/UserRowActions.tsx
  - frontend/src/features/groupadmin/GroupRowActions.tsx
  - frontend/src/features/roleadmin/RoleRowActions.tsx
  - frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx
  - frontend/e2e/support/axe.ts
  - frontend/package.json
  - gradle/libs.versions.toml
  - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Avatar/Avatar.tsx
- **一部だけ深く読んだもの**（上の一覧に入れず、読んだ範囲を書く。Scope of Analysis では `shallow` に回す想定）:
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（import の一覧、各 `@TransactionalEventListener` の行、`onRoleAuditEvent` の前後）と `audit/service/AuditEventRecorder.java`（`REQUIRES_NEW` の行だけ）
  - `backend/src/main/java/cherry/mastersmith/role/service/RoleAdminService.java`（`barrier.*` と `publishEvent` の行だけ grep）
  - `backend/src/test/java/cherry/mastersmith/navigation/NavigationBoundaryArchitectureTest.java`（先頭の規則と説明の Javadoc）
  - `backend/src/test/java/cherry/mastersmith/role/RoleBoundaryArchitectureTest.java`（`@DisplayName` と許す依存の行だけ grep）
  - `backend/build.gradle.kts`（`packagesJudgedByTotal` とカバレッジの検証の部分）
  - `backend/src/main/resources/application.yaml`（`mastersmith.target-db` の節）
  - `frontend/src/app/navigation/buildNavSections.ts`（先頭の説明・型・`toTablePlaceholderPath` を呼ぶ行）
  - `frontend/eslint.config.js`（`no-restricted-imports` の2つの規則）
  - `.github/dependabot.yml`（npm の項の `ignore` とコメント）
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`（`a9309c4` の trigger の扱い、`cloneElement` で足す属性）
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/Topbar.tsx`（利用者のメニューのトリガーの部分、76〜97 行）
  - `perf/README.md`（「(b) 大きな入力のヒープ」の節と `--storage` の行・150 行の注意）
  - `Dockerfile`（`ENTRYPOINT` の行）
  - `frontend/node_modules/axe-core/axe.js`（4.13.0 の `labelContentNameMismatchEvaluate`・`isStringContained` と規則の定義。成果物ではなく入れた依存の読み取り）
  - 前の Intent の記録（`261015-deps-ui-carryover` の `make-you-chic-ui-request-4.md`・`generation-notes.md` の Step 10・`test-results.md` 3節・`deployment-log.md` の CI の節、`261004-role-menu` の `feedback-optimization/feedback-loop.md` の第3の束）。コードではないが、持ち越しの事実の出どころとして読んだ。
- **Skimmed only**:
  - `backend/src/main/java/cherry/mastersmith/` のうち上に無いパッケージ（`app-bootstrap`・`config`・`common/error`・`common/persistence`・`common/web`・`common/paging`・`common/health`・`common/i18n`・`common/observability`・`auth/`・`appearance/`・`invitation/`・`mail/`・`user/`・`useradmin/`・`dslmanage/`・`group/`、`role/` と `dsl/` と `targetdb/` と `navigation/` と `access/` と `audit/` の上に無いファイル）。パッケージごとのファイルの数は数えた。`user/`・`useradmin/`・`auth/` は `admin_flag`・`adminFlag`・`.admin()` の grep の行だけ読んだ。
  - `backend/src/main/resources/db/migration/` の V1〜V9、`dsl/dsl-schema-v2.json`（`primaryKey`・`view` の行だけ）、`navigation/allowed-icons.txt`、`mail/`
  - `backend/src/test/java/cherry/mastersmith/`（境界テストの一覧と、`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`・`PublicApiInventoryTest` の有無だけ）
  - `frontend/src/app/`（ファイルの一覧。`admin-forbidden/`・`navigation/`・`registry/` の上に無いファイル）、`frontend/src/shared/`（一覧）、`frontend/src/features/`（一覧。`useradmin`・`groupadmin`・`roleadmin` の `messages.ts` は操作のトリガーの文言の行だけ grep、`preferences/` は make-you-chic-ui の import の行だけ）
  - `frontend/e2e/`（ファイルの一覧。`080`・`170` は grep の行だけ）
  - `vendor/make-you-chic-ui/`（部品の一覧、`a9309c4..b0a1e79` の差の統計）、`vendor/java-mustache-processor/`
  - `docker/`（ファイルの一覧。`docker/targetdb/postgres/01-sample-schema.sql` は `CREATE TABLE`・`PRIMARY KEY`・`CREATE VIEW` の行、`02-reader-account.sh` は `GRANT` の行、`docker/monitoring/provisioning/alerting/mastersmith.yaml` は `ms-forbidden` の式だけ）
  - `compose.yaml`（`mem_limit` の行だけ）、`.env.example`・`.env.targetdb.example`（変数の名前の行だけ。値は表示していない。`.env` は開いていない）
  - `perf/k6/scenarios.js`（`dslMixed` の行だけ grep）
  - `.github/workflows/ci.yml`、`settings.gradle.kts`、`build.gradle.kts`、`README.md`（今回は開いていない。存在だけ）
- **深く読んだ部品**（既存の ID。どれも部品の一部だけを読んだ「一部」の深さ）: `role`・`navigation`・`dsl`・`targetdb`・`common-security`・`access`・`audit`・`frontend-feature-tables`・`frontend-registry`・`frontend-app-core`・`frontend-feature-useradmin`・`frontend-feature-groupadmin`・`frontend-feature-roleadmin`・`frontend-e2e`・`make-you-chic-ui`・`build-and-verify`（計 16）。`perf-and-monitoring`・`container-runtime`・`user`・`useradmin` は grep と節の読みだけのため流し読みに入れる。
- **数**: 深く読んだパスは上の一覧の 77（すべてファイル。すべて `./` の中）。

### Packages Found

- `backend`（`cherry.mastersmith`） — アプリ（実行可能 WAR） — Java 25 / Spring Boot 4.1.1 — 機能ごとのパッケージ（`auth`・`access`・`audit`・`user`・`useradmin`・`invitation`・`mail`・`appearance`・`targetdb`・`dsl`・`dslmanage`・`role`・`group`・`navigation`・`common/*`・`config`）。各機能の中を `web`・`service`・`domain`・`repository` と用途名の下位パッケージ（`role/store`・`role/transfer`・`group/store`・`dsl/parse`・`dsl/validate`・`dslmanage/generate`・`invitation/lock`・`mail/template` など）に分ける。本体のソース 736 ファイル・テスト 586 ファイル（`git ls-files` の数。リソースを含む）。
- `frontend` — SPA — TypeScript 6 / React 19.2 / React Router 8.3 / Vite 8.3 — `src/app/`（骨組み 90 ファイル）・`src/shared/`（34）・`src/features/<featureId>/`（13 機能・351 ファイル）・`e2e/`（18 本と `support/` 26）。
- `vendor/make-you-chic-ui` — Git サブモジュール（固定先 `a9309c4`） — React + TypeScript — デザインシステム。`frontend/package.json` の `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui` で使う。
- `vendor/java-mustache-processor` — Git サブモジュール（固定先 `8d44c36`、0.1.0） — Java — メールのテンプレートの Mustache のエンジン。Gradle の composite build で使う。
- `perf/`・`docker/` — 負荷の試験（k6・ブラウザの時間）と手元の監視・見本の対象DB・使い捨ての試験の環境。

### Build System

- **Type**: Gradle（Kotlin DSL）を入口にし、`frontend` の npm と外部の道具（Gitleaks・OSV-Scanner）も Gradle から呼ぶ（`./gradlew verify`。前の知識ベースの記録のとおりで、今回は `build.gradle.kts` を開いていない）。
- **Config Files**: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vite.config.ts`・`frontend/playwright.config.ts`・`frontend/eslint.config.js`・`frontend/.oxlintrc.json`・`frontend/.npmrc`・`.github/dependabot.yml`・`.github/workflows/ci.yml`・`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`
- **Build Dependencies**: `backend` の WAR は `frontend` のビルド結果（`dist`）を同梱する。`frontend` は `vendor/make-you-chic-ui` に、`backend` は `vendor/java-mustache-processor` に依存する。
- カバレッジの下限: `backend/build.gradle.kts` 230〜238 行の `packagesJudgedByTotal` は 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`team.md` の記録と一致する。新しく作るパッケージはパッケージごとの下限（行 80%・分岐 70%）の対象に自動で入る。

### APIs Discovered

- REST（Spring MVC） — `backend/src/main/java/cherry/mastersmith/*/web/` — 今回の範囲で確かめた口:
  - `GET /api/me/navigation`（作業ロールで絞った業務のメニュー）・`GET /api/me/table-access?schema=&table=`（テーブルの置き場の問い合わせ。200 は組・表示名・主権限、見られなければ 403 `ACCESS_DENIED`、引数が欠け・空なら 400 `VALIDATION_FAILED`） — `navigation/web/NavigationController.java`、`@ApiAccess(AUTHENTICATED)`。
  - 業務データ（行の一覧・詳細・作成・更新・削除）の API は**まだ無い**（`grep` と各パッケージの一覧で確かめた。K-49）。
- API の分類の印 `@ApiAccess`（`PUBLIC`・`AUTHENTICATED`・`ADMIN` の3値。`common/security/ApiAccess.java`・`ApiAccessLevel.java`）。検査はテストの `ApiAccessArchitectureTest`（印の書き忘れ・二重・道との食い違い）と `ApiAccessConsistencyIT`（印と本番の決まりの一致）。`ADMIN` の道は `/api/admin` と `/api/admin/**` だけ（`access/domain/AdminPaths.java`）。`/api/**` の既定はログインだけ（`access/web/AdminApiDefaultAccess.java`）。
- 画面の道（SPA）: `/tables?schema=&table=&item=`（テーブルの置き場。`features/tables/registration.ts`、`access: 'LOGGED_IN'`）。

### Frameworks & Libraries

- Java — 25 — `gradle/libs.versions.toml` の `java`
- Spring Boot — 4.1.1 — アプリの土台（組み込みの Tomcat は 11.0.26 に上書き、Jackson は 3.1.7 の BOM で上書き）
- SnakeYAML — 2.7 — DSL の YAML の読み込み
- networknt json-schema-validator — 3.0.6 — DSL の JSON Schema の検証（3.0.7 は Jackson を 3.2 系に上げるため据え置き）
- HikariCP — Spring Boot の管理の版 — 内部DB と対象DB（`mastersmith-target-db`、既定の上限 5）の接続のプール
- jqwik — 1.10.2 / ArchUnit — 1.5.1 — 性質ベースのテストと境界の構造の検査
- opentelemetry-logback-appender — 2.28.1-alpha（固定）
- React — ^19.2.8 / react-router — ^8.3.0 / i18next — ^26.4.2 / react-i18next — ^17.0.15 / Vite — ^8.3.4 / Vitest — ^5.0.4 / TypeScript — ^6.0.3
- @playwright/test — ^1.63.0 — E2E
- axe-core — ^4.13.0（lockfile は 4.13.0。`vitest-axe` の求める `^4.4.2` も同じ 4.13.0 の1つに寄っている） — 実際のブラウザのアクセシビリティの検査（`e2e/support/axe.ts`）と画面の部品のテスト（`vitest-axe`）
- make-you-chic-ui — サブモジュールの `a9309c4` — 画面の部品（Dropdown・AppShell・Avatar・Button など）

### Test Coverage

- **Test Directories**: `backend/src/test/java/cherry/mastersmith/`（機能ごと。単体 `XxxTest`・結合 `XxxIT`、機能ごとの `testsupport/`）、`frontend/src/**/*.test.ts(x)`（対象と同じ場所）、`frontend/e2e/`（Playwright、`./gradlew e2eTest`、`verify` と CI の外）
- **Test Frameworks**: JUnit 5・AssertJ・Spring Boot Test・Testcontainers（対象DB の MySQL・MariaDB・PostgreSQL）・jqwik・ArchUnit／Vitest・Testing Library・user-event・vitest-axe・fast-check／Playwright と axe-core
- **Coverage Config**: present（JaCoCo、`backend/build.gradle.kts` の `jacocoTestCoverageVerification`。frontend は `@vitest/coverage-v8` の `thresholds`。前の知識ベースの記録のとおり）
- 境界の構造の検査（ArchUnit）は 17 個（`ArchitectureTest`・`ApiAccessArchitectureTest` と、`access`・`appearance`・`audit`・`auth`・`dsl`・`dslmanage`（2つ）・`group`・`invitation`・`mail`・`navigation`・`role`・`targetdb`（2つ）・`user`・`useradmin` の機能ごとのもの）。業務データの機能に深く関わるのは `TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`NavigationBoundaryArchitectureTest`・`DslBoundaryArchitectureTest`（K-49・K-61）。

### Code Quality Indicators

- **Linting**: Spotless（palantir-java-format、ライセンスヘッダー）・SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`）／Prettier・oxlint・ESLint（`frontend/eslint.config.js`。機能どうしの import を `no-restricted-imports` で止め、`src/shared/` から `src/app/`・`src/features/` を読むことも止める）・Stylelint
- **CI/CD**: `.github/workflows/ci.yml`（`develop` への push で `./gradlew verify`）、`.github/dependabot.yml`（npm・gradle・github-actions・イメージ）
- **Documentation**: Javadoc・コメントが日本語で、要件・設計の ID（BR・NFR・契約の番号）と Intent の名前を細かく引いている。`frontend/src/features/README.md` に機能の作り方の決まり。`perf/README.md` に負荷の試験の手順。

### Technical Debt Signals

- 業務データの一覧・詳細の土台が無く、テーブルを指すメニューの項目は「準備中」の画面（`features/tables/TablePlaceholderPage.tsx`）へ移る（K-52）。
- 管理の可否が `users.admin_flag` だけで決まり、ロールの仕組みと二重になっている（広げてから縮める二段の「広げる」側だけが終わった状態。K-53）。
- 監査の受け手 `audit/service/AuditEventListener.java` が、各機能の出来事の型（`auth`・`access`・`dslmanage`・`group`・`invitation`・`role`・`user`・`useradmin`）をすべて import する集まりの形（K-54）。
- 不安定なテスト2件（`RoleConcurrencyIT` の #7、`AdminForbiddenProvider.test.tsx` の1件）が CI で1回ずつ落ちて再実行で通った記録がある（K-57・K-58）。
- 依存の見送り: `.github/dependabot.yml` で `axe-core` の `>= 4.14.0` を `ignore`（外す時期と理由はコメントに書いてある。K-55・K-56・K-60）。
- `targetdb/domain/SqlIdentifiers.java` は「今は使わない」と Javadoc にある識別子の引用の入り口（業務データの問い合わせで初めて使う見込み。K-51）。

## 今回の Intent に関わる所見（K-49〜K-61）

### K-49 業務データを読む・書く仕組みが無く、今の対象DB の部品は「読み取り専用・スキーマだけ」に固定されている（`targetdb`）

- 確かめた事実:
  - 対象DB の接続は読み取り専用で作る（`targetdb/config/TargetDataSourceConfig.java` の `hikariConfig` で `config.setReadOnly(true)`）。接続は既定の候補にせず（`defaultCandidate = false`）、名前 `targetDataSource` で指定して使う。プールの上限は既定 5（`TargetDbProperties.Pool`）。
  - 問い合わせの目的は `ReadPurpose` の `GENERATE`（既定の DSL の生成、1回 20 秒）と `COMPARE`（照合、1回 5 秒）の2つだけ（`targetdb/domain/ReadPurpose.java`、`TargetDbProperties.QueryTimeout`）。行の値を読む口は無い（`targetdb/service/` は `JdbcTargetSchemaReader` だけで、`readSchema(ReadPurpose)` がスキーマの情報を返す）。
  - 境界テスト `backend/src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java` が次を固定している: `targetdb` の外から `targetdb.config`・`targetdb.repository` に依存しない、`targetdb` は `dsl`・`dslmanage`・`web` を知らず `web` の層を持たない、JDBC・Spring JDBC の書き込み・DDL の呼び出し（`executeUpdate`・`update`・`batchUpdate` など）と `JdbcTemplate` を使わない、`@Transactional` を使わない。
  - 見本の対象DB の読み取りのアカウント `mastersmith_reader` は `SELECT` だけ（`docker/targetdb/postgres/02-reader-account.sh` の `GRANT` の行）。`application.yaml` のコメントも「アカウントは読み取りの権限だけにすることを勧める」。
- 今回の Intent への意味: 主権限 `FULL`（見られる・直せる）・補助権限 `CREATE`・`DELETE` を「操作の可否」に当てて実際に作成・更新・削除まで行うなら、(1) 書ける接続（読み取り専用でない別の接続か、設定の切り替え）、(2) 書けるアカウント（見本の DB と README の手順を含む）、(3) 境界テストの変更か、`targetdb` の外の新しいパッケージ、(4) 書き込みの待ちの上限（今の `QueryTimeout` に目的が無い）が要る。`team.md` の Code Style により、既存の境界テストを緩める・消すときはコード生成の計画に明記して依頼者の承認が要る。
- 見立て（未検証）: 一覧・詳細の「表示」だけを今回の範囲にし、作成・更新・削除は「操作の可否の表示（ボタンを押せる・押せない）」までに留める形も取りうる。どちらにするかは要件定義で決める論点（業務データの書き込みの有無が、接続・アカウント・監査・トランザクションの設計を大きく変える）。

### K-50 権限の解決の口は「主権限・CREATE・DELETE」を返せるが、置き場の API が返すのはテーブルの主権限だけ（`role`・`navigation`）

- 確かめた事実:
  - 解決の口 `role/service/EffectivePermissionResolver.java` は3つ: `effectiveWorkRole(userId)`、`snapshotFor(userId)`（作業ロールの設定の写し。2つ以上の対象を判定するときに使う）、`resolve(userId, target)`（対象1つ）。どれも要求ごとに内部DB から読み、キャッシュを持たない。作業ロールが無い・適用済みの DSL が無い・対象が今の DSL に無いときは `EffectivePermission.NONE`（主権限 NONE・CREATE 不可・DELETE 不可）。
  - 継承（`role/domain/PermissionInheritance.java`）: 主権限は `column ?? table ?? schema ?? NONE`、補助権限は `table ?? schema ?? false` を CREATE と DELETE で別々に求める。カラムの補助権限はそのテーブルの値。主権限と補助権限は互いに独立に求まる（例: 主権限 READ で CREATE が true の組も、設定しだいで実効の値になりうる。`PermissionValues` と表 `permission_settings` の検査の制約にも、主権限と補助権限の組み合わせを縛るものは無い。`V11__u4_role.sql`）。
  - 写し `role/domain/PermissionSnapshot.java` は `effective(target)`・`main(schema, table, column)`・`create(schema, table)`・`delete(schema, table)` を持つ。カラムごとの主権限（列の出し分け）は `main(schema, table, column)` で求まる。
  - 置き場の API（`GET /api/me/table-access`、`navigation/service/NavigationService.tableAccess`）は `resolve` を1回呼び、テーブルの主権限だけを返す（`TableAccessResponse` は `schemaName`・`tableName`・`displayName`・`main`）。CREATE・DELETE・カラムの主権限・主キーの有無は返さない。
  - `role` の境界テスト（`backend/src/test/java/cherry/mastersmith/role/RoleBoundaryArchitectureTest.java` 129 行の `@DisplayName`）は「role の外で role に依存してよいのは `audit`（`role.domain`）と `navigation`（`role.service`・`role.domain`）だけ」。`navigation` の境界テストは「どの機能も navigation に依存しない」。
- 今回の Intent への意味: 業務データの機能を新しいパッケージに置くと、解決の口を使うために `RoleBoundaryArchitectureTest` の許す相手を足す変更（境界テストを緩める変更に当たり、計画での明記と承認が要る）が要る。`navigation` を広げる形なら `role` の境界テストは変えずに済むが、`navigation` の「repository を持たず DB の部品に依存しない（読み取りだけ）」という境界テストと当たる。
- 要件定義で決める論点（事実から出てくるもの）: 主権限と補助権限の組み合わせの意味（例: READ＋CREATE、NONE の列を持つ表での作成・更新の扱い、FULL で DELETE 不可）、カラムの主権限が NONE の列を一覧・詳細・更新の要求でどう扱うか（応答から外す・要求の本文にあれば拒否する など）。

### K-51 DSL のモデルは一覧・詳細の画面に要る情報（主キー・ビュー・列の表示の設定・選択肢）を持つが、まだ誰も使っていない（`dsl`）

- 確かめた事実:
  - `dsl/domain/DslTable.java`: `primaryKey`（主キーのカラムの物理名の一覧。無ければ空）、`view`（ビューなら true）、`foreignKeys`、`columns`（DSL の順を保つ対応表、1件以上）。
  - `dsl/domain/DslColumn.java`: `dbType`（名前・長さ・精度・小数の桁・NULL の可否）、`formPart`（11種）、`search`（`enabled`・`operator`・`defaultValue`・`collapsed`）、`list`（`visible`・`order`・`width`・`sortable`・`defaultSort`・`format`）、`detail.visible`、`validations`（`required`・`unique`・長さ・範囲・`pattern`）、`options`（`FIXED`・`REFERENCE`・`LOOKUP`）。`ListFormat` の Javadoc に「書式の中身は画面の Intent で決める」とある。
  - `dsl/domain/DslModel.java`: 書式の版 2、スキーマは検証でちょうど1つ（`schema()` が1つでないと例外）。テーブルは `findTable(TableRef(schema, name))` で引く（大文字と小文字を区別する）。
  - `DslForeignKey.referencedTable`・`OptionSource.table` はテーブルの物理名だけで、スキーマ名を持たない（スキーマが1つの前提）。
  - `dsl/service/ActiveDslModelProvider.java` の Javadoc: `pattern` は正しい正規表現かを確かめただけで値に当てはめたことは無く、当てはめるときは時間の上限つきにすること。
  - 識別子の引用の入り口 `targetdb/domain/SqlIdentifiers.java`（`quote`・`quoteKnown`。DB の種類の引用符で囲み、中の引用符を二重にする。NUL を拒否）は「今は使わない。利用者の入力から識別子を作らない」。
  - 見本の対象DB（PostgreSQL の `01-sample-schema.sql`）には、単一の主キーの表（`customers`・`orders`）、複合の主キーの表（`order_items` の `(order_id, line_no)`）、引用符と空白と `;` を含む名前の表（`"Product Master; ""Quoted"" 'Name'"`）、ビュー（`customer_orders`）がある。主キーの無い表は見本に無い（`grep` で確かめた）。
- 今回の Intent への意味: 「主キーの有無による操作の可否」の判定の材料は `DslTable.primaryKey` の空かどうかと `view` で揃っている。主キーの無い表の確かめには、見本の DB かテストの表を足す必要がある。行の識別（詳細の画面の道・更新・削除の対象）は複合の主キーと特殊な文字を含む値を扱う必要があり、画面の道の組み立て（エンコード）とサーバー側の識別子の引用（`SqlIdentifiers` を DSL の名前の一覧に照らして使う）が要る。`team.md` の「信頼できない入力」の決まりは DSL の YAML について書かれており、道に乗る主キーの値・検索の値の扱いは要件・設計で決める。
- 見立て（未検証）: `DslModel` は `dsl` のパッケージの外へ渡せる変更できない値のため、業務データの機能は `ActiveDslModelProvider` だけに依存すれば足りる見込み（`dsl` の境界テストは「dsl は targetdb を知らない」で、逆向きの依存は止めていない。逆向きを止める規則があるかは今回読んでいない）。

### K-52 テーブルを指すメニューの項目の移り先は「準備中」の置き場で、画面の道・今の項目・E2E がこの形に依っている（`frontend-feature-tables`・`frontend-registry`）

- 確かめた事実:
  - 道は `/tables?schema=<スキーマ名>&table=<テーブル名>&item=<位置の道>`。作るのは `frontend/src/app/navigation/tablePlaceholderPath.ts` の `toTablePlaceholderPath` の1か所だけ（`URLSearchParams` でエンコードし、先頭は常に `/tables?`）。サイドバーの業務の項目は `buildNavSections.ts` の 158 行でこの関数から `href` を作る。
  - 置き場の画面 `features/tables/TablePlaceholderPage.tsx` は、置き場の API の 200 で見出し（表示名）と「準備中」、403・400・引数なしで同じ「権限なし」の表示（作業ロールが2つ以上なら切り替えのボタン）、それ以外で読み込みの失敗を出す。`useTableAccess.ts` は引数・作業ロール・切り替えの回数が変わるたびに問い合わせ、403 を受けると業務のメニューと作業ロールを読み直す。
  - 機能 `tables` の登録は `access: 'LOGGED_IN'`（見てよいかの判定はサーバー。`app/registry/types.ts` の `AccessLevel` の説明「表示の制御であり、サーバー側の権限の確認の代わりにはならない」）。
  - この画面に依る E2E と部品: `frontend/e2e/170-menu-navigation-flow.e2e.ts`（`/tables?` へ移ること、見られない表の名前が出ないこと、`/api/me/table-access` の本物の応答）、`frontend/e2e/160-app-frame-accessibility.e2e.ts`、`frontend/e2e/support/u7Measure.ts`（`tables-page`・`tables-heading` などの `data-testid` を grep で確かめた）。
- 今回の Intent への意味: 一覧・詳細の画面を置くと、置き場の道の形（`/tables?…` のまま中身を一覧にするか、詳細の道を足すか）、`buildNavSections` の「今の項目」の決め方（今は置き場の道の `pathname` と引数で決める）、上の E2E と画面のテストの書き換え（`team.md` により本数に数えない）が影響の範囲に入る。

### K-53 管理の可否は今も `users.admin_flag` だけで決まり、ロールの仕組みには「管理」の権限が無い（F2 の論点。`access`・`user`・`useradmin`・`auth`）

- 確かめた事実（`grep` で本体のソースを確かめた）:
  - 管理の API の判定 `access/web/AdminAuthorizationManager.java` は、要求の主体 `AuthenticatedUser.admin()` だけを見る。`AuthenticatedUser` は要求ごとに内部DB から読んだ `user.admin()` で作る（`auth/web/AccessTokenAuthenticationProvider.java` 67 行）。
  - `admin_flag` を読む・書く場所: `user/domain/User.java`（列 `admin_flag`）、`user/repository/UserRepository.java`（一覧・詳細の射影、`adminFlag = true and suspended = false` の数え（181 行）、`updateAdminFlag`（195 行））、`user/repository/UserRowLockRepository.java`（81 行、最後の管理者の保護の行の排他）、`user/service/UserAccountService.java`（`setAdmin`・初期管理者の救済の条件 464 行）、`useradmin/service/UserAdminService.java`（`GRANT_ADMIN`・`REVOKE_ADMIN`、操作する人が管理者かの判定 354 行）、`auth/web/CurrentUserResponse.java`（画面へ `admin` を返す）。画面の `LoginState.admin`（`app/registry/types.ts`）が管理のメニューの出し分けに使われる。
  - ロールの権限は対象（スキーマ・テーブル・カラム）への主権限・補助権限だけで、管理の権限を表す値も組み込みのロールも無い（`role/domain/` の型の一覧、`V11__u4_role.sql`。`Role.java` に `builtin`・`admin` の語は無い）。`V11`・`V12` の移行のコメントは「`users.admin_flag` は残し、行を移さない（管理の可否は今までどおり `admin_flag` で決める）」。
  - テストのソースで `admin_flag`・`adminFlag` を含むファイルは 34（`grep -l` の数）。
  - 前の Intent の振り返り（`261004-role-menu` の `feedback-loop.md` の F2）も「ロールでは管理の API を使えない。消すのは後の Intent で、戻したときの権限のずれの確かめを入れる」。
- 今回の Intent への意味: F2（`admin_flag` を縮める）は列を消すだけでは済まず、先に「管理の可否をロールで表す形」（管理の権限の値か組み込みのロール、最後の管理者の保護の数え直し、初期管理者の作成と救済、管理者の印の付け外しの API と監査の読み替え、画面の `admin`）を決める必要がある。`team.md` の Deployment の「広げてから縮める二段」と「前の版へ戻したときの権限のずれの確かめ」も当たる。業務データの機能とは独立に大きい。
- 気になる点: このワークフローの範囲は classic で、範囲を決める段（`scope-definition`）は `aidlc-state.md` で SKIP になっている。Intent の文の「F2 をこの Intent に入れるかは、範囲を決める段で決める」は、要件定義の段で扱うことになる見込み（指揮役に確かめてほしい）。

### K-54 業務データの操作の監査の仕組みが無い。今の監査は内部DB のトランザクションの確定の後に書く形（`audit`）

- 確かめた事実:
  - `audit/domain/AuditEventType.java` に業務データの操作の種類は無い（認証・DSL・招待・利用者の管理・初期管理者・グループ・ロールだけ）。
  - `audit/service/AuditEventListener.java` は各機能の出来事の型を import して受け取る（`auth.domain`・`access.domain`・`dslmanage.domain`・`group.domain`・`invitation.domain`・`role.domain`・`user.domain`・`useradmin.domain`）。どの受け取りも `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` で、書き込みは `AuditEventRecorder` の `@Transactional(propagation = REQUIRES_NEW)`。
  - `project.md` の Decided に「監査記録の共通の仕組みは…業務データの CRUD を扱う後続 Intent で共通化を検討する」がある。
- 今回の Intent への意味: 業務データを書き込むなら、対象DB の確定と監査の行（内部DB）の書き込みは別の DB のため、同じトランザクションにできない。今の形（内部DB の確定の後）をそのまま使うと、内部DB のトランザクションが無い要求では `fallbackExecution` で発行した時点に書かれる。対象DB の確定と監査のどちらかだけが残る場合の扱い、監査に残す中身（業務データの値・主キーの値を含めるか。`detail` の列は UTF-16 の 16,384 単位まで、`V10__u3_group.sql`）は要件・設計で決める論点。読み取り（一覧・詳細）を監査に残すかも同じ。
- 見立て（未検証）: 業務データの機能も同じ受け手に出来事の型を足す形にすると、`audit` の import がさらに1つ増える。共通化を検討する Decided の宿題はこの Intent が持ち主になる。

### K-55 行の「操作」のトリガーの名前（`useradmin`・`groupadmin`・`roleadmin`）と axe-core 4.14 の判定

- 確かめた事実:
  - 3つの行の操作（`features/useradmin/UserRowActions.tsx` 72〜80 行、`features/groupadmin/GroupRowActions.tsx` 73〜81 行、`features/roleadmin/RoleRowActions.tsx` 69〜77 行）は、make-you-chic-ui の `Dropdown` の `trigger` に `<Button variant="secondary" size="sm" loading={busy} aria-label={name}>` を渡し、見える文字は `t('<機能>.actions.button')`（「操作」・`Actions`）。`aria-label` は「`{{name}}（{{email}}）の操作`」（useradmin）・「`{{name}}の操作`」（groupadmin・roleadmin）、英語は `Actions for {{name}}…`、処理中は末尾に「（処理中）」・` (processing)`（各 `messages.ts`）。
  - `Dropdown`（`a9309c4`）は `cloneElement` でトリガーに `aria-haspopup`・`aria-expanded`・`data-testid="dropdown-trigger"` を足す（名前は変えない）。
  - 前の Intent の記録（`261015-deps-ui-carryover` の `generation-notes.md` の Step 10、`make-you-chic-ui-request-4.md` 2節）は、axe-core 4.14.0 で `label-content-name-mismatch` の違反が `dropdown-trigger`（行の操作など）に出たとし、直し方を「名前に見える文字を含める形（例: 『操作: 行の名前』、または見える文字のままにして行の名前を `aria-describedby` で添える）」としている。
  - 手元の axe-core 4.13.0 の `labelContentNameMismatchEvaluate`（`frontend/node_modules/axe-core/axe.js` 26543 行）は、名前と見える文字を小文字にし、句読点・絵文字などを除いてから「名前が見える文字を含むか」（`includes`）で判定する。この判定なら「山田（a@example.com）の操作」は「操作」を含み、`Actions for …` も `Actions` を含む。
- 見立て（未検証）: 4.13.0 の判定の仕方のままなら今の行のトリガーは違反にならないはずで、4.14.0 で判定の仕方（含むかではなく、語の区切りや先頭での一致など）が変わったか、違反の要素が行のトリガーではなく別の `dropdown-trigger`（K-56 のトップバーなど）だった可能性がある。4.14 の `axe.js` は手元に無いため確かめていない。直す前に、4.14 の規則の判定と、違反が出た要素の `target` を確かめることを勧める（直し方は「見える文字で始まる名前」にすれば、どちらの判定でも通る見込み）。

### K-56 表示の設定の画面（E2E 080）の違反は、どの画面にもあるトップバーの利用者のメニューのトリガー（make-you-chic-ui の Topbar と Avatar）の可能性がある

- 確かめた事実:
  - 表示の設定の画面（`features/preferences/`）は make-you-chic-ui の `Alert`・`Button`・`FormField`・`RadioGroup`・`TextInput` だけを使い、`Dropdown` を使わない（import の行を grep で確かめた）。
  - アプリシェルの中の画面にはいつもトップバーがあり、make-you-chic-ui の `AppShell/Topbar.tsx`（76〜97 行）は利用者のメニューを `Dropdown` で描く。トリガーは `<button aria-label={labels.userMenu(user.name)}>` の中に `<Avatar name={user.name} />` を置く。アプリは `userMenu` を「`{{name}}のメニュー`」・`Menu for {{name}}` にしている（`app/layout/ShellLayout.tsx` 137 行、`app/i18n/messages/*.ts`）。
  - `Avatar/Avatar.tsx` は画像が無いとき、名前の最初の2語の頭文字を大文字にした文字（例: `Taro Yamada` → `TY`、`山田 太郎` → `山太`）を要素の中に文字として描く（`role="img"`・`aria-label={name}`）。
  - `a9309c4..b0a1e79` で変わったのは `Dropdown.tsx`・`Dropdown.css`・`Dropdown.test.tsx` の3つだけ（`git diff --stat`）。Topbar と Avatar は変わっていない。
  - 作業ロールの切り替え（`app/work-role/WorkRoleSwitcher.tsx`）もトップバーの `Dropdown` を使う（前の Intent の記録では「作業ロール: 〇〇」で違反にならない見込みとされ、まだ確かめていない）。
- 見立て（未検証）: 頭文字が2文字の名前では、トリガーの見える文字（頭文字）が名前（「山田 太郎のメニュー」）に含まれないため、`label-content-name-mismatch` に当たる。080 の E2E の見本の氏名は2語の「検査 太郎」（`frontend/e2e/support/preferencesFixtures.ts` 27 行の `SAMPLE_DISPLAY_NAME`、確かめた事実）で、頭文字は「検太」になり、名前「検査 太郎のメニュー」に含まれない。そのため 080 の違反はこのトリガーの可能性が高い。正しければ make-you-chic-ui の側（Topbar の名前の付け方か Avatar の頭文字の扱い）の直しで、b0a1e79 では直らず、依頼（5）が要る。080 の E2E の違反の要素（`target`）を見て確かめる必要がある。

### K-57 `RoleConcurrencyIT` の #7 は、監査の行の ID の順に頼っており、コードの作りの上で順は決まっていない

- 確かめた事実:
  - テスト（`backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java` 145〜185 行）は、1件目の保存を排他の後（`AFTER_LOCK`）で止め、2件目を排他の前（`BEFORE_LOCK`）まで進めてから1件目を放す。監査の行を `ORDER BY audit_event_id` で読み、1件目（`details.get(0)`）に「`before` が null・`after` が READ」を求める（177 行）。
  - 監査は確定の後（`AFTER_COMMIT`）に、各要求のスレッドで `REQUIRES_NEW` の別のトランザクションで書く（`AuditEventListener`・`AuditEventRecorder`）。1件目の確定から1件目の監査の書き込みまでの間に、2件目は排他を得て確定し、自分の監査を書きうる。`audit_event_id` は書いた順に振られるため、2件目の行が先になりうる。
  - CI の記録（`261015-deps-ui-carryover` の `deployment-log.md`）: 1件目に後の保存の監査（`before` が READ、`after` が FULL）が来て落ちた。手元の 5 回では再現しなかった。
- 見立て: 上の作りから、CI の失敗はこの競り合いで説明できる（手元では再現していないため、確かめた原因ではない）。直し方の候補は、テストが行の順に頼らず中身で2行を見分ける（`before` が null の行と READ の行がそれぞれ1つずつあることを確かめる）か、1件目の監査が書かれたのを待ってから2件目を確定させる待ち合わせを足すこと。前者はテストだけの直しで、本番の監査の順の決まり（あるなら）は変えない。

### K-58 `AdminForbiddenProvider.test.tsx` の1件は、描画の後の効果の回数を、効果が済む前に写しうる形

- 確かめた事実: テスト「keeps the same function before and after the URL becomes forbidden」（`frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` 231〜242 行）は、`await screen.findByTestId('probe')` の直後に `effectRuns` を写し（`runsBefore`）、403 を知らせた後に `effectRuns` が変わらないことを求める。`effectRuns` は部品の `useEffect(() => { effectRuns += 1 }, [report])`（67〜69 行）で増える。CI の失敗は `expected 1 to be +0`（`test-results.md` 3節）。
- 見立て（未検証）: `findByTestId` は要素が現れた時点で解け、`useEffect` がまだ動いていないことがある（遅い CI で起きやすい）。写した後に初めの効果が動くと、写した値 0 に対して 1 になる。`team.md` の「描画の後に反映される値は `waitFor` で待って確かめる」に当たる形で、写す前に `effectRuns` が初めの1回に達したことを `waitFor` で待てば、確かめたい中身（403 の後に `report` が変わらない）は保てる見込み。

### K-59 `dslMixed` の `memory.peak` は、ページキャッシュを含むコンテナの最大で測っている

- 確かめた事実:
  - 測り方（`perf/README.md` の「(b) 大きな入力のヒープ」）: 使い捨てのアプリを作り直してから `dslMixed` を流し、`docker exec mastersmith-perf-app-1 cat /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.max` で読み、`peak / max` が 0.9 未満を合格とする。
  - 同じ README の `--storage` の行と 150 行は、`memory.current` がページキャッシュを含み、止まるかどうかは `memory.stat` の `anon` と `OOMKilled` で見ると書いている。`--storage` は `anon` と `file` を回ごとに記録するが、`dslMixed` の手順は `memory.peak` だけを読む。
  - アプリの JVM は `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError`（`Dockerfile` の `ENTRYPOINT`）、コンテナの上限は `compose.yaml` の `mem_limit: ${MASTERSMITH_CONTAINER_MEMORY:-2g}`。
  - 前の Intent の記録: 94.4%・取り直し 93.1%、peak の時の内訳は `anon` 68.3%・回収できるページキャッシュ 23.7%。物差しを `anon` に替えると Met になるが、目標の測り方を後から緩めることになるため選ばず持ち越した（`project.md` の学び）。
- 今回の Intent への意味: cgroup v2 の `memory.peak` は全体（`anon` と `file` など）の最大で、`anon` だけの最大を直接読む値は無い。`anon` の最大を物差しにするなら、`dslMixed` の間に `memory.stat` を短い間隔で読み続ける手順が要る（`--storage` の記録の仕方と同じ形）。物差しを変えることは目標の測り方の変更に当たるため、`project.md` の「目標を緩めて満たしたことにはしない」により、要件定義で依頼者が明示に決める論点（変える理由・前の値との並べ方を記録する）。測り方を変えずに、ページキャッシュを減らす（H2 のファイルの読み書き・上限の見直し）方向もある。

### K-60 make-you-chic-ui の固定先の更新と axe-core の取り込みの前提

- 確かめた事実:
  - サブモジュールは `a9309c4`。`b0a1e79`（`Dropdown: 説明付き項目でlabel-content-name-mismatch(WCAG 2.5.3)に 違反しないようDOM構造を変更`、2026-10-16）は手元の履歴にあり、`a9309c4` の次の1コミット（`git log a9309c4..b0a1e79`）。
  - `.github/dependabot.yml` の npm の項で `axe-core` の `versions: [">= 4.14.0"]` を `ignore` し、理由と外す時期（画面の側の名前の直しと make-you-chic-ui の Dropdown の直しを取り込んだ後）をコメントに書いている（65〜75 行）。
  - E2E の axe（`frontend/e2e/support/axe.ts`）はタグ `wcag2a`・`wcag2aa`・`wcag21a`・`wcag21aa` で流す。`label-content-name-mismatch` のタグは `wcag21a`・`wcag253` と `experimental`（4.13.0 の規則の定義、`axe.js` 32991 行）。4.14 で `experimental` が外れると、今のタグの指定のまま流れる。既知の違反の一覧（`KNOWN_VIOLATIONS`）は空で、どの違反も失敗にする。
  - 画面の部品のテストの `vitest-axe` も同じ `axe-core`（lockfile の 4.13.0 の1つ）を使う。
- 今回の Intent への意味: 取り込みの順は「固定先の更新（専用のコミット、fast-forward の例外）→ 行のトリガーの名前の直し → axe-core を 4.14 以降に上げて `ignore` を外す → E2E（080・100・120・130・160）で確かめる」。K-56 が正しければ、b0a1e79 だけでは 080 などの違反が残りうる。`vitest-axe` で流れる規則も変わりうるため、画面の部品のテストも確かめの範囲に入る。

### K-61 業務データの API を足すときに当たる決まり（API の分類・安全の決まりの差し込み・境界テスト）

- 確かめた事実:
  - 新しい口には同じ変更で `@ApiAccess` を付ける。`ADMIN` は `/api/admin` の下だけ、`AUTHENTICATED` は `/api/` の下で管理の道の外（`common/security/ApiAccess.java` の Javadoc）。検査は `ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`。
  - 安全の決まりの差し込み口 `SecurityRuleContributor` の order は機能の名前で 100 台ずつ（`auth` 100 台・`access` 200 台・`invitation` 300 台・`appearance` 400 台、本番は x10。`common/security/SecurityRuleContributor.java`）。重なると起動を失敗させる（`SecurityExtensionValidator`）。
  - 置き場の API の 403 は `access` に依存せず、共通の問題の種類の一覧から code `ACCESS_DENIED` で引く（`NavigationController`）。見られない理由（NONE・DSL に無い・DSL が無い）を区別しない。
  - 手元の監視の警報 `ms-forbidden` は 403 の全体の数を数える（`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`status="403"` の `increase` の合計）。前の Intent の持ち越し D2（置き場の 403 を分けるか）がある。
- 今回の Intent への意味: 業務データの API は利用者の作業ロールで判定するため `AUTHENTICATED` の分類になる見込みで、判定は API ごとにサーバー側で行う（`project.md` の Mandated）。`team.md` の「役割・権限の機能」の必須のテスト（API ごとの 401・403・200、要る権限だけを欠く利用者での 403、API の分類の網羅、割り当ての変更の直後の切り替え、IDOR・一括代入の防止）がすべて当たる。業務データの 403 が増えると `ms-forbidden` のしきい値（20）に届きやすくなる。

## Handoff Summary

- **Intent-relevant finding**: 業務データの一覧・詳細に要る材料（実効の権限の解決の口、DSL の主キー・ビュー・列の表示の設定、テーブルを指すメニューの道）はそろっているが、業務データの行を読む・書く仕組みは無い。対象DB の接続は読み取り専用で（`backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java` の `config.setReadOnly(true)`）、目的はスキーマの読み取り（`ReadPurpose` の `GENERATE`・`COMPARE`）だけ、境界テスト（`backend/src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java`）が書き込み・`JdbcTemplate`・`@Transactional`・DSL への依存を止めている。解決の口に依存してよい機能も `audit` と `navigation` だけに固定されている（`backend/src/test/java/cherry/mastersmith/role/RoleBoundaryArchitectureTest.java` 129 行）。置き場の API はテーブルの主権限だけを返す（`navigation/web/TableAccessResponse.java`）。（K-49・K-50・K-51・K-52）
- **Risks / follow-up**:
  - 作成・更新・削除まで行うかで、書ける接続とアカウント・書き込みの待ちの上限・監査と対象DB の確定の関係・既存の境界テストの変更（`team.md` により計画での明記と依頼者の承認が要る）が大きく変わる。要件定義で最初に決める論点（K-49・K-50・K-54）。
  - 主権限と補助権限は独立に求まり、組み合わせ（READ＋CREATE、FULL で DELETE 不可、NONE の列）の意味はまだ決まっていない（K-50）。
  - 行の識別は複合の主キーと特殊な文字を含む名前・値を扱う必要がある（見本の `order_items` と引用符を含む表）。主キーの無い表は見本に無く、確かめの材料を足す必要がある（K-51）。
  - F2 は `admin_flag` の列を消すだけでは済まない。ロールに管理の権限の考えが無く、管理の可否・最後の管理者の保護・初期管理者・管理者の印の API がすべて `admin_flag` に依る。範囲を決める段（`scope-definition`）はこのワークフローで SKIP のため、F2 の扱いを決める段を指揮役が確かめること（K-53）。
  - axe-core 4.14 の違反のうち、行の「操作」のトリガーが本当に違反かは、手元の 4.13 の判定では説明できず未確認（K-55）。080 などの違反は make-you-chic-ui の Topbar の利用者のメニューのトリガー（Avatar の頭文字）の可能性が高く、`b0a1e79` では直らない見込みで、make-you-chic-ui への依頼（5）が要りうる（K-56）。どちらも、4.14 で E2E を流して違反の要素の `target` を見て確かめる必要がある。
  - 不安定なテスト2件の直し方の見立て: `RoleConcurrencyIT` の #7 は監査の行の ID の順に頼る形（K-57）、`AdminForbiddenProvider.test.tsx` は効果の回数を `waitFor` で待たずに写す形（K-58）。どちらもテストの側の直しで足りる見込み（未検証）。`project.md` の「不具合を直すときは再現するテストを同じコミットに」に当たるかは、テストだけの直しのため指揮役が判断する。
  - `dslMixed` の `memory.peak` はページキャッシュを含む物差しで、`anon` を物差しにするなら測り方の変更（目標の測り方を変えること）になり、依頼者の明示の決定が要る（K-59）。
  - 範囲の外で深く読んでいないもの: `dslmanage`（DSL の適用と差し替えの出来事）、`build.gradle.kts` の `verify` の並び、`.github/workflows/ci.yml`、`perf/k6/scenarios.js` の `dslMixed` の中身。設計の段で必要になれば読み直す。
