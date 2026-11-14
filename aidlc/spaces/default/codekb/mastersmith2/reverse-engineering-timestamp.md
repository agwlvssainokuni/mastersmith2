# Reverse Engineering Timestamp

## Run Record

- Date: 2026-11-14
- Commit: cb3b513a783da1257feb86e90bd6eccf749eb52c

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-11-14 |
| Intent | `261114-internal-db-postgresql`（内部DB（組み込みの H2）を PostgreSQL でも動かせるようにする） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `cb3b513a783da1257feb86e90bd6eccf749eb52c`（この文書を書く前に `git rev-parse HEAD` で取り直して確かめた。依頼で渡された `head_commit` と同じ値） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Standard。既存の知識ベースの判定は STALE（`git diff --stat 7f36d8a cb3b513a` で、AI-DLC の外では前の Intent `261110-deps-forks-review` のコード生成の変更（`.github/`・`backend/gradle.lockfile`・`TargetDbImages.java`・`compose.yaml`・`docker/perf/compose.yaml`・`docs/verification.md`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/playwright.config.ts`・`gradle/` ほか 20 ファイル）が積まれている。今回はこのうち内部DB に関わるものだけを読み直した）。前の散文を残して、今回の範囲（内部DB）の節を直し・足した |
| 手順 | 開発担当が1人でコードスキャン（`aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/reverse-engineering/developer-scan.md`）と9文書の更新を行った（協力者は無効で、設計担当は呼ばれていない） |

## 確かめ方

- 確かめた事実: ファイルの読み取り（Read・`cat`・`sed`・`grep`・`ls`・`wc`・`find`）と、`git rev-parse`・`git log`・`git diff --stat`・`git ls-files` の読み取りだけ。テスト・ビルド・`npm`・`./gradlew`・`docker` は流していない。外のサイト（Maven Central・Docker Hub）は見ていない。`.env`・`.env.targetdb` は開いていない。
- 始める前に知識の置き場 `aidlc/spaces/default/knowledge/aidlc-shared/` の 10 ファイル（`study-internal-db-postgresql.md` を最初に）を読み、下調べの記録と今のコードを照らした（`code-quality-assessment.md` の K-153）。
- 事実と見立てを分け、見立てには「見立て（未検証）」と書いた（PostgreSQL での SQL・移行・方言・待ちの振る舞い、`@Lob byte[]` の写し方、Flyway の部品が無いときの止まり方、CI の時間など。どれも流して確かめていない）。
- 9文書を書く段で、次を読み直して確かめた: `RoleStore.java`（40〜60・200〜265 行）・`RoleStoreClassifier.java` の SQLState の一覧（`grep`）・`postgres:18.6` のダイジェストの3か所（`grep`。前の知識ベースの `technology-stack.md` の頭 `3a426452…` は古く、今は `e3d24f69…`）・`common.persistence` を使う `src/main` のファイル（`grep`）・`.github/` の追跡するファイル（`git ls-files`）。
- 深く読んだもののうち、次は一部だけを読んだ（ファイルを単位にした `analyzed.paths` に入れたが、読みの範囲はこのとおり）: `backend/build.gradle.kts`（1〜240 行。依存・テストのタスク・`-PitTests`・`-PitForks`・JaCoCo）、`application.yaml`（内部DB・JPA・Flyway・ログの節）、`compose.yaml`（`app` と `targetdb-*` のサービス・ボリューム）、`.env.example`（内部DB の節）、`docs/internal-db.md`（「バックアップと戻し方」「複写を数える」の前半と「詰め直し」の節。約 90 行／196 行）、`auth/repository/RefreshTokenRepository.java` と `invitation/repository/InvitationRepository.java`（問い合わせの部分）。
- ステージングの9文書は、前の知識ベースの9文書を Read で読み、今回の範囲の節を直し・足して Write と Edit で書いた（範囲の外の節は前の文のまま）。この文書は新しく書いた。

## 前の記録との関係

- 前のコード知識ベースは Intent `261110-deps-forks-review` のもの（記録のコミット `7f36d8a`）。判定が STALE のため、今回の `analyzed.paths` と `analyzed.components` は今回の走査の分だけにした。
- 前の `analyzed.paths`（11）のうち、今回も深く読んだのは `backend/build.gradle.kts`・`Dockerfile`・`compose.yaml` と、`.github/` の2ファイル（`.github/workflows/ci.yml`・`.github/dependabot.yml`。`.github/` の追跡するファイルはこの2つだけのため、ディレクトリは `shallow.paths` に置かない）。今回深く読まなかった `gradle/`・`settings.gradle.kts`・`settings-gradle.lockfile`・`frontend/package.json`・`config/`・`docs/verification.md`（今回は `grep` だけ）と、`backend/src/main/java/cherry/mastersmith/common/health/`（今回は `TimeBoundedDbHealthIndicator.java` だけを深く読んだ）は `shallow.paths` に下げた。
- 前の `shallow.paths` は、今回深く読んだもの（`backend/src/main/resources/application.yaml`・`docs/internal-db.md`・`.env.example`・`backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`）を除いて残した。今回の流し読み（該当のメソッド・`grep` だけ）のものを足した: 4 つのサービス（`UserAccountService`・`InvitationService`・`LoginService`・`UserAdminService`。違反と排他の失敗を受けるメソッドだけを読んだため、開発担当の Scan Coverage の「深く」から、記録の上では流し読みに下げた）・`GroupStore.java`・`invitation/lock/`・`audit/`・`PostgresTestDatabase.java`・`backend/src/test/resources/`・`docs/configuration.md`・`docs/deployment.md`・`perf/dsl-mixed-memory.sh`・`perf/dsl-timing.sh`。
- 部品 ID は 50 個（`internal-db-migration` を足した。前は 49 個）。今回の `analyzed.components` は 15 個（`config`・`internal-db-migration`・`common-persistence`・`common-error`・`common-health`・`auth`・`user`・`invitation`・`dslmanage`・`role`・`group`・`backend-test-support`・`build-and-verify`・`container-runtime`・`perf-and-monitoring`）。どれも部品の一部を深く読んだ（`component-inventory.md` の各部品の行）。`useradmin`・`audit`・`targetdb` は流し読みのため入れていない。2026-11-11 に深く読んだ `build-and-verify`・`container-runtime`・`common-health` は、今回も深く読んだ範囲がある。
- 所見は前回までの K-62〜K-143 に、今回 K-144〜K-154 を足した（一覧は `business-overview.md`）。K-143 の `project.md` の分は、`project.md` の書き換えで解消の見込み（`code-quality-assessment.md`）。

## 範囲の要約

- 深く読んだ: 25 の道（ファイル 22 とディレクトリ 3）、15 部品。依頼のスナップショットの範囲（`backend/src/main/java/cherry/mastersmith/`・`backend/src/main/resources/`・`backend/src/test/java/cherry/mastersmith/`・`backend/src/test/resources/`・`backend/build.gradle.kts`・`backend/gradle.lockfile`・`Dockerfile`・`compose.yaml`・`docker/`・`.github/`・`docs/internal-db.md`・`docs/operations.md`・`docs/configuration.md`・`docs/deployment.md`・`docs/verification.md`・`.env.example`・`frontend/playwright.config.ts`・`perf/`）の中だけ。
- スナップショットの範囲の外で深く読んだものは無い（`gradle/libs.versions.toml` は DB の部品の名前の `grep`、`scripts/count-db-copy.mjs` は存在の確かめだけ）。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。`intent` は `aidlc engine workspace codekb --repo mastersmith2 --json` の `intent`（`internal-db-postgresql`）。
- `fingerprint` は、最終の `analyzed.paths`（25）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --repo mastersmith2 --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: internal-db-postgresql
fingerprint: d0665b04f6e980131cc1d1d7a6f947a5c300300b
analyzed:
  paths:
    - backend/src/main/resources/application.yaml
    - backend/src/main/resources/db/migration/
    - backend/src/main/java/cherry/mastersmith/common/persistence/
    - backend/src/main/java/cherry/mastersmith/common/health/TimeBoundedDbHealthIndicator.java
    - backend/src/main/java/cherry/mastersmith/common/error/web/UniqueViolations.java
    - backend/src/main/java/cherry/mastersmith/role/store/RoleStore.java
    - backend/src/main/java/cherry/mastersmith/role/store/RoleStoreClassifier.java
    - backend/src/main/java/cherry/mastersmith/group/store/StoreFailureClassifier.java
    - backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java
    - backend/src/main/java/cherry/mastersmith/auth/repository/RefreshTokenRepository.java
    - backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java
    - backend/src/main/java/cherry/mastersmith/user/repository/UserRowLockRepository.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/repository/
    - backend/src/main/java/cherry/mastersmith/invitation/repository/InvitationRepository.java
    - backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java
    - backend/build.gradle.kts
    - Dockerfile
    - compose.yaml
    - .github/workflows/ci.yml
    - .github/dependabot.yml
    - docs/internal-db.md
    - .env.example
    - perf/h2-shell.sh
    - perf/lib/perf-db-url.sh
  components:
    - config
    - internal-db-migration
    - common-persistence
    - common-error
    - common-health
    - auth
    - user
    - invitation
    - dslmanage
    - role
    - group
    - backend-test-support
    - build-and-verify
    - container-runtime
    - perf-and-monitoring
shallow:
  paths:
    - gradle/
    - settings.gradle.kts
    - settings-gradle.lockfile
    - frontend/package.json
    - config/
    - docs/verification.md
    - backend/src/main/java/cherry/mastersmith/common/health/
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java
    - backend/src/main/java/cherry/mastersmith/invitation/service/InvitationService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
    - backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminService.java
    - backend/src/main/java/cherry/mastersmith/group/store/GroupStore.java
    - backend/src/main/java/cherry/mastersmith/invitation/lock/
    - backend/src/main/java/cherry/mastersmith/audit/
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/PostgresTestDatabase.java
    - backend/src/test/resources/
    - docs/configuration.md
    - docs/deployment.md
    - perf/dsl-mixed-memory.sh
    - perf/dsl-timing.sh
    - scripts/
    - frontend/scripts/check-license-header.mjs
    - build.gradle.kts
    - backend/src/test/java/cherry/mastersmith/common/health/
    - backend/src/main/java/cherry/mastersmith/access/service/
    - backend/src/test/java/cherry/mastersmith/access/service/
    - backend/src/main/java/cherry/mastersmith/audit/repository/
    - backend/src/test/java/cherry/mastersmith/audit/repository/
    - backend/src/main/java/cherry/mastersmith/common/error/domain/
    - backend/src/test/java/cherry/mastersmith/common/error/domain/
    - backend/src/main/java/cherry/mastersmith/common/error/service/
    - backend/src/test/java/cherry/mastersmith/common/error/service/
    - backend/src/main/java/cherry/mastersmith/common/i18n/domain/
    - backend/src/test/java/cherry/mastersmith/common/i18n/domain/
    - backend/src/main/java/cherry/mastersmith/common/web/
    - backend/src/test/java/cherry/mastersmith/common/web/
    - backend/src/test/java/cherry/mastersmith/config/H2CompactThreadsTest.java
    - docs/dsl.md
    - docs/operations.md
    - docs/mail.md
    - frontend/scripts/check-bundle-size.mjs
    - frontend/scripts/check-dist-assets.mjs
    - frontend/scripts/make-ico.mjs
    - frontend/vitest.config.ts
    - frontend/tsconfig.perf-tool.json
    - backend/src/main/java/cherry/mastersmith/common/error/web/
    - backend/src/test/java/cherry/mastersmith/common/error/web/
    - README.md
    - frontend/scripts/check-svg-safety.mjs
    - frontend/scripts/icon-binary.mjs
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbTestDatabase.java
    - backend/src/test/resources/junit-platform.properties
    - frontend/src/features/preferences/buildTimeZoneOptions.ts
    - frontend/src/features/preferences/buildTimeZoneOptions.test.ts
    - frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts
    - frontend/src/features/preferences/TimeZoneSelect.tsx
    - frontend/src/features/preferences/registration.ts
    - frontend/src/features/preferences/PreferencesPage.tsx
    - frontend/src/features/preferences/preferencesApi.ts
    - frontend/src/features/preferences/failure.ts
    - frontend/src/features/preferences/errorMessages.ts
    - frontend/src/features/preferences/formChecks.ts
    - frontend/src/shared/format/timeZone/index.ts
    - frontend/src/shared/format/timeZone/timeZoneGate.ts
    - frontend/src/shared/format/timeZone/formatInstant.ts
    - frontend/src/shared/format/timeZone/wallClock.ts
    - frontend/src/shared/format/timeZone/instantText.ts
    - frontend/src/shared/format/timeZone/parseLocalDateTime.ts
    - frontend/src/shared/format/timeZone/types.ts
    - frontend/src/shared/format/timeZone/timeZoneGate.test.ts
    - frontend/src/shared/format/timeZone/conversionPerformance.test.ts
    - frontend/src/shared/format/formatInstantToMinutes.ts
    - frontend/src/shared/format/dateTimeInput.ts
    - frontend/src/features/preferences/
    - frontend/src/shared/format/
    - frontend/src/app/display-settings/
    - frontend/src/features/useradmin/
    - frontend/eslint-rules/timeZoneSelectors.js
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/ColumnValueCodec.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/RowDialect.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/MysqlFamilyRowDialect.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/PostgresRowDialect.java
    - backend/src/main/java/cherry/mastersmith/businessdata/domain/ValueType.java
    - backend/src/main/java/cherry/mastersmith/businessdata/domain/ColumnKind.java
    - backend/src/main/java/cherry/mastersmith/businessdata/web/ColumnDefinitionResponse.java
    - backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DbType.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/FormPart.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/ListFormat.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslColumn.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslTable.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslMenuItem.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/TableRef.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslModel.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/ListSetting.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DetailSetting.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/generate/TypeCategoryMapping.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/generate/TypeCategory.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavNode.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavTableRef.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavIconPolicy.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/MenuFilter.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavigationMenu.java
    - backend/src/main/java/cherry/mastersmith/navigation/service/NavigationService.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/NavNodeResponse.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/NavTableRefResponse.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/NavigationController.java
    - backend/src/main/resources/navigation/allowed-icons.txt
    - frontend/index.html
    - frontend/src/app/registry/allowedIcons.ts
    - frontend/src/app/registry/types.ts
    - frontend/src/app/navigation/buildNavSections.ts
    - frontend/src/app/navigation/navigationItems.ts
    - frontend/src/app/pages/HomePage.tsx
    - frontend/src/app/i18n/messages/ja.ts
    - frontend/src/app/layout/ShellLayout.tsx
    - frontend/src/features/admin/registration.ts
    - frontend/src/features/admin/AdminAreaPage.tsx
    - frontend/src/features/admin/adminApi.ts
    - frontend/src/features/admin/adminAreaStatus.ts
    - frontend/src/features/admin/AdminPlaceholder.tsx
    - frontend/src/features/tables/valueFormat.ts
    - frontend/src/features/tables/formDiff.ts
    - frontend/src/features/tables/searchConditions.ts
    - frontend/src/features/tables/SearchPanel.tsx
    - frontend/src/features/tables/BusinessDataFormPage.tsx
    - frontend/src/shared/format/formatDateTime.ts
    - .gitmodules
    - backend/gradle.lockfile
    - backend/config/spotbugs-exclude.xml
    - frontend/package-lock.json
    - frontend/.npmrc
    - frontend/vitest.setup.ts
    - frontend/vite.config.ts
    - docker/perf/compose.yaml
    - docker/e2e/compose.targetdb-port.yaml
    - docker/hikari-pool.sh
    - perf/README.md
    - perf/versions.env
    - perf/redact-k6-evidence.mjs
    - backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlBuilder.java
    - backend/src/main/java/cherry/mastersmith/businessdata/
    - backend/src/main/java/cherry/mastersmith/dsl/
    - backend/src/main/java/cherry/mastersmith/targetdb/
    - backend/src/main/java/cherry/mastersmith/role/
    - backend/src/main/resources/
    - frontend/src/features/tables/
    - frontend/src/shared/
    - perf/
    - docker/
    - docker/perf/
    - frontend/e2e/
    - frontend/scripts/
    - frontend/playwright.config.ts
    - frontend/eslint.config.js
    - frontend/tsconfig.json
    - frontend/src/
    - frontend/src/app/
    - frontend/src/features/
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/test/java/cherry/mastersmith/
    - backend/config/
    - .pre-commit-config.yaml
    - backend/src/main/java/cherry/mastersmith/dslmanage/
    - backend/src/main/java/cherry/mastersmith/navigation/
    - backend/src/main/java/cherry/mastersmith/access/web/AdminCheckController.java
    - backend/src/main/java/cherry/mastersmith/config/WebConfig.java
    - backend/src/main/resources/dsl/
    - docker/targetdb/
    - frontend/src/features/admin/
    - frontend/src/features/dsl/
    - backend/src/test/java/cherry/mastersmith/businessdata/
```
