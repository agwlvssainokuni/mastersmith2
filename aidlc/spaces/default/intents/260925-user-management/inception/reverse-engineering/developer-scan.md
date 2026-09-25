# 開発担当のコードスキャン（260925-user-management）

- 対象: リポジトリ mastersmith2（ワークスペースのルート）。記録の時点のコミットは `c438dc0008b1f06d0aed533f61cdee0e18a976fd`（`git rev-parse HEAD`、develop）。
- 読み方: 依頼者の指定は Full rescan、深さ Standard。ただし実際に深く読んだのは下の「深く読んだもの」だけで、全体を深く読んではいない（`./` は深く読んだ範囲に入れない）。
- 読まなかったもの: `reference/`（Git 管理外の参考資料）、`aidlc/` と `.claude/`（ワークフローの枠組み）。
- 書き方: 「確かめた事実」はコードやファイルを読んで確かめたこと、「推測・仮説」はコードから読み取った見立てで未検証のこと。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**:
  - `backend/src/main/java/cherry/mastersmith/user/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/auth/service/`（LoginService・AccessTokenService・TokenRefreshService・LogoutService・LoginAttemptStateInitializer・SigningKeyProvider・AuthProperties・AuthClockConfig）
  - `backend/src/main/java/cherry/mastersmith/auth/web/`（AuthController・AuthSecurityContributor・AccessTokenAuthenticationProvider・RefreshCookies・OriginVerifier・ClientInfoResolver・TokenResponse・LoginRequest・CurrentUserResponse）
  - `backend/src/main/java/cherry/mastersmith/auth/domain/`（AuthenticatedUser・AuthProblemTypes・LockPolicy）
  - `backend/src/main/java/cherry/mastersmith/auth/repository/`（LoginAttemptStateRepository、RefreshTokenRepository はメソッドの宣言だけ）
  - `backend/src/main/java/cherry/mastersmith/access/domain/AdminPaths.java`
  - `backend/src/main/java/cherry/mastersmith/access/web/`（AdminSecurityContributor・AdminAuthorizationManager・AdminApiDefaultAccess・AdminCheckController・AdminAccessDeniedHandler・AccessWebSecurityConfig）
  - `backend/src/main/java/cherry/mastersmith/audit/`（domain の AuditEvent・AuditEventFactory・AuditEventType・AuditFailureReason、service の全ファイル、repository の全ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/error/domain/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/error/service/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/error/web/GlobalExceptionHandler.java`
  - `backend/src/main/java/cherry/mastersmith/common/error/web/ErrorResponseFactory.java`
  - `backend/src/main/java/cherry/mastersmith/common/security/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/i18n/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/observability/TraceAspect.java`
  - `backend/src/main/java/cherry/mastersmith/common/web/RequestBodyLimitRoute.java`
  - `backend/src/main/java/cherry/mastersmith/common/web/MastersmithWebProperties.java`
  - `backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java`
  - `backend/src/main/java/cherry/mastersmith/config/WebConfig.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/web/DslRequestContextResolver.java`（管理者の API の文脈の組み立て方の見本として）
  - `backend/src/main/resources/application.yaml`
  - `backend/src/main/resources/logback-spring.xml`
  - `backend/src/main/resources/db/migration/`（V1〜V6 の全ファイル）
  - `backend/build.gradle.kts`
  - `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`
  - `backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java`
  - `backend/src/test/java/cherry/mastersmith/access/testsupport/PublicApiTestRules.java`
  - `build.gradle.kts`
  - `settings.gradle.kts`
  - `gradle/libs.versions.toml`
  - `frontend/package.json`
  - `frontend/vite.config.ts`
  - `frontend/vitest.config.ts`
  - `frontend/src/main.tsx`
  - `frontend/src/app/App.tsx`
  - `frontend/src/app/i18n/`（I18nProvider・i18n・resolveLanguage）
  - `frontend/src/app/routing/`（全ファイル）
  - `frontend/src/app/layout/ShellLayout.tsx`
  - `frontend/src/app/login-state/LoginStateGate.tsx`
  - `frontend/src/app/registry/types.ts`
  - `frontend/src/app/registry/registrationModules.ts`
  - `frontend/src/app/navigation/navigationItems.ts`
  - `frontend/src/shared/api-client/`（全ファイル）
  - `frontend/src/features/auth/`（authSession・authApi・loginStateProvider・registration）
  - `frontend/src/features/admin/registration.ts`
  - `frontend/src/features/admin/AdminAreaPage.tsx`
  - `frontend/src/features/dsl/registration.ts`
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/`（types・validation・storage・ThemeProvider）
  - `compose.yaml`
  - `Dockerfile`
  - `.env.example`
  - `.github/workflows/ci.yml`
  - `README.md`（「スキーマの変更（Flyway）」以降の節。前半は見出しだけ）
- **Skimmed only**:
  - `backend/src/main/java/cherry/mastersmith/dsl/`（ファイルの一覧だけ）
  - `backend/src/main/java/cherry/mastersmith/dslmanage/`（ファイルの一覧と DslAdminController の一部だけ）
  - `backend/src/main/java/cherry/mastersmith/targetdb/`（ファイルの一覧だけ）
  - `backend/src/main/java/cherry/mastersmith/common/health/`
  - `backend/src/main/java/cherry/mastersmith/common/observability/`（TraceAspect 以外。Sanitizing* は伏せる項目の定義だけ確かめた）
  - `backend/src/main/java/cherry/mastersmith/common/web/`（上の2ファイル以外）
  - `backend/src/main/java/cherry/mastersmith/common/error/web/`（上の2ファイル以外）
  - `backend/src/main/java/cherry/mastersmith/access/`（上に挙げたもの以外）
  - `backend/src/main/java/cherry/mastersmith/auth/domain/`（上に挙げたもの以外）
  - `backend/src/main/java/cherry/mastersmith/config/`（上の2ファイル以外）
  - `backend/src/test/java/cherry/mastersmith/`（上の3ファイル以外。ファイルの一覧と数、AuditBoundaryArchitectureTest の DisplayName だけ）
  - `backend/config/spotbugs-exclude.xml`
  - `frontend/src/features/dsl/`（registration 以外）
  - `frontend/src/features/auth/`（LoginPage・LoginForm・validateLoginInput）
  - `frontend/src/features/admin/`（上に挙げたもの以外）
  - `frontend/src/app/`（上に挙げたもの以外。pages・layout の残り・registry の残り）
  - `frontend/e2e/`
  - `vendor/make-you-chic-ui/`（theme 以外。index.ts の公開の一覧と AppShell の localStorage の利用だけ確かめた）
  - `docker/`
  - `perf/`
  - `.pre-commit-config.yaml`
  - `config/`

### Packages Found

部品の ID は前回のコード知識ベース（component-inventory.md）の見出しと同じものを使う。アーキテクトはこの ID をそのまま component-inventory.md の見出しと Scope of Analysis の components に使える。深さの欄は、今回このスキャンで深く読んだか（deep）流し読みか（skim）を示す。

| ID | 種類 | 言語 | 置き場 | 役割 | 深さ |
|---|---|---|---|---|---|
| app-bootstrap | 起動 | Java | `backend/src/main/java/cherry/mastersmith/MastersmithApplication.java` | Spring Boot の起動クラス | skim |
| config | 設定 | Java | `backend/src/main/java/cherry/mastersmith/config/` | SecurityFilterChain の組み立て（差し込み口を順に当てる）・SPA の配信（見つからない URL に index.html）・転送元ヘッダー・観測の設定 | deep（SecurityConfig・WebConfig） |
| common-error | 共通部品 | Java | `backend/src/main/java/cherry/mastersmith/common/error/` | ProblemType（code・状態コード・日英の文言）・BusinessException・GlobalExceptionHandler・ProblemTypeRegistry（code と slug の重複で起動の失敗） | deep |
| common-security | 共通部品 | Java | `backend/src/main/java/cherry/mastersmith/common/security/` | SecurityRuleContributor（order 付きの差し込み口）・ApiDefaultAccess・ErrorResponseWriter | deep |
| common-web | 共通部品 | Java | `backend/src/main/java/cherry/mastersmith/common/web/` | 要求の本文の上限（道ごとの上限 RequestBodyLimitRoute）・Cache-Control | 一部 deep |
| common-health | 共通部品 | Java | `backend/src/main/java/cherry/mastersmith/common/health/` | 制限時間付きの内部DB のヘルスチェック | skim |
| common-i18n | 共通部品 | Java | `backend/src/main/java/cherry/mastersmith/common/i18n/` | DisplayLanguage（JA・EN）と Accept-Language からの決定（既定 JA） | deep |
| common-observability | 共通部品 | Java | `backend/src/main/java/cherry/mastersmith/common/observability/` | TraceAspect（web・service・domain・repository の引数と戻り値を TRACE で文字列化）・外部エクスポートの伏せ字・トレースID | 一部 deep |
| auth | 機能 | Java | `backend/src/main/java/cherry/mastersmith/auth/` | ログイン（ロック判定）・JWT（HS256）のアクセストークン・リフレッシュトークン（Cookie）・ログアウト・トークンの認証 | deep |
| access | 機能 | Java | `backend/src/main/java/cherry/mastersmith/access/` | `/api/admin` 以下の管理者のみの判定・401／403 の入口・アクセス拒否の出来事 | deep（web と AdminPaths） |
| audit | 機能 | Java | `backend/src/main/java/cherry/mastersmith/audit/` | 認証・アクセス拒否・DSL の操作の出来事を `audit_events` に追記（確定の後に別トランザクション） | deep |
| user | 機能 | Java | `backend/src/main/java/cherry/mastersmith/user/` | 利用者の表・パスワードの決まりと照合（bcrypt）・初期管理者の自動作成 | deep |
| targetdb | 機能 | Java | `backend/src/main/java/cherry/mastersmith/targetdb/` | 対象DB（MySQL・MariaDB・PostgreSQL）のスキーマの読み取り | skim |
| dsl | 機能 | Java | `backend/src/main/java/cherry/mastersmith/dsl/` | DSL（YAML）の安全な読み込み・JSON Schema と意味の検証・有効な DSL のモデル | skim |
| dslmanage | 機能 | Java | `backend/src/main/java/cherry/mastersmith/dslmanage/` | DSL の生成・投入・プレビュー・適用・履歴の API（`/api/admin/dsl/**`） | skim |
| backend-test-support | テストの補助 | Java | `backend/src/test/java/cherry/mastersmith/**/testsupport/` | テストの部品（HttpTestClient・JsonLogRecords・MutableClock・PublicApiTestRules など） | skim |
| frontend-app-core | 画面の骨組み | TypeScript | `frontend/src/app/App.tsx`・`frontend/src/main.tsx`・`frontend/src/app/routing/`・`frontend/src/app/login-state/` | 起動・URL の振り分け（PUBLIC・LOGGED_IN・ADMIN）・ログイン状態の受け渡し | deep |
| frontend-registry | 画面の骨組み | TypeScript | `frontend/src/app/registry/`・`frontend/src/app/navigation/` | 機能ごとの registration.ts の読み込みと検査・サイドバーとユーザーメニューの項目 | deep（types・registrationModules・navigationItems） |
| frontend-app-layout-i18n | 画面の骨組み | TypeScript | `frontend/src/app/layout/`・`frontend/src/app/i18n/` | AppShell の配置・ログイン用レイアウト・i18next による日英の文言 | deep（ShellLayout・i18n） |
| frontend-api-client | 画面の共通部品 | TypeScript | `frontend/src/shared/api-client/` | Bearer の付与・401 での更新と送り直し・Problem Details の読み取り | deep |
| frontend-feature-auth | 画面の機能 | TypeScript | `frontend/src/features/auth/` | ログイン画面・トークンをメモリに保持・ログイン状態の提供元・ログアウト | deep（画面の部品は skim） |
| frontend-feature-admin | 画面の機能 | TypeScript | `frontend/src/features/admin/` | 管理者向け領域 `/admin`（確認用 API を呼ぶだけの置き場） | 一部 deep |
| frontend-feature-dsl | 画面の機能 | TypeScript | `frontend/src/features/dsl/` | DSL の管理画面 `/admin/dsl` | skim |
| make-you-chic-ui | 外部のデザインシステム（サブモジュール） | TypeScript | `vendor/make-you-chic-ui/` | ThemeProvider（4つの軸）・AppShell・フォームの部品など | theme だけ deep |
| build-and-verify | ビルドと検査 | Kotlin DSL | `build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`.github/workflows/ci.yml` | `./gradlew verify` の 10 段・カバレッジの下限・SpotBugs の関門・OSV-Scanner・Gitleaks | deep |
| container-runtime | 実行環境 | Dockerfile・YAML | `Dockerfile`・`compose.yaml`・`.env.example` | 開発者の PC 上のコンテナでの起動（mem 2g・CPU 4・ボリューム `/app/data`） | deep（本体の app サービス） |
| perf-and-monitoring | 負荷の試験と監視 | JS・YAML | `perf/`・`docker/` | k6 の試験・otel-lgtm の手元の監視 | skim |

### Build System

- **Type**: Gradle（Kotlin DSL）のルートとサブプロジェクト `backend`。画面は npm（`frontend/`）を Gradle の Exec タスクから呼ぶ。
- **Config Files**: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vite.config.ts`・`frontend/vitest.config.ts`
- **Build Dependencies**:
  - `:backend:bootWar` → `:frontendBuild` → `vendorBuild`（サブモジュールの `npm ci` と `npm run build`）。WAR の `WEB-INF/classes/static` に `frontend/dist` を同梱する（確かめた事実）。
  - `verify` は `verifyPrepare` → `verifyFormat` → `verifyLint` → `verifyLicense` → `verifyBuild` → `verifyUnitTest` → `verifyIntegrationTest` → `verifyCoverage` → `verifySecurity` → `verifyArtifact` の順（確かめた事実）。
  - 依存の取得元は Maven Central だけ（`settings.gradle.kts` の `RepositoriesMode.FAIL_ON_PROJECT_REPOS` と `mavenCentral()`）。依存は `dependencyLocking { lockAllConfigurations() }` で固定し、追加には `resolveAndLockAll --write-locks` が要る（確かめた事実）。

### APIs Discovered

REST（Spring MVC）。`@GetMapping`・`@PostMapping`・`@DeleteMapping`・`@RequestMapping` を全体で検索して数えた（確かめた事実）。

| 置き場 | 道 | 数 | アクセス |
|---|---|---|---|
| `auth/web/AuthController.java` | `POST /api/auth/login`・`POST /api/auth/session/refresh`・`POST /api/auth/session/logout` | 3 | ログイン不要（AuthSecurityContributor が permitAll）。refresh・logout は Origin の一致を確かめる |
| `access/web/AdminCheckController.java` | `GET /api/admin/check` | 1 | 管理者のみ（204） |
| `dslmanage/web/DslAdminController.java` | `/api/admin/dsl/**`（status・preview の GET・POST・DELETE・generate・preview の download・apply・history・history の restore・applied の download） | 10 | 管理者のみ |
| `common/error/web/ProblemTypeController.java` | `GET /api/problems/{slug}` | 1 | ログイン不要 |
| `common/error/web/ErrorPathController.java` | エラーの道 | 2 | — |
| Actuator | `GET /actuator/health` | 1 | ログイン不要（公開は health だけ） |

確かめた事実:
- 利用者に関わる API（利用者の一覧・登録・自分の情報の取得・パスワードの変更・設定の保存）は1つも無い。ログイン中の利用者の情報は、ログインと更新の応答 `TokenResponse.user`（`email`・`admin` の2項目）でだけ返る。`GET /api/me` のような道は無い。
- アクセスの決まりの順序（`config/SecurityConfig.java`）: `/actuator/health` と `/api/problems/**` を permitAll → 差し込み口を order 順に当てる（auth が 110、access が 210）→ `ApiDefaultAccess` が1つあり `requireAuthentication()` が true（access の AdminApiDefaultAccess）なので `/api/**` は authenticated → それ以外（画面の URL・静的ファイル）は permitAll。
- したがって、ログインなしで呼ぶ新しい API（例: 招待を受けた人の登録の完了）は、`/api/auth/` の下に置くか、新しい SecurityRuleContributor で permitAll を足さないと 401 になる。管理者のみの API は `/api/admin/` の下に置けば判定が自動で効く（README の「API のアクセス制御（U3）」の決まり）。
- CSRF は無効、セッションは STATELESS、アクセストークンは Authorization ヘッダーだけから読む（URL とフォームの本文からは読まない）。

### Frameworks & Libraries

版は `gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json` から読んだ（確かめた事実）。

| 名前 | 版 | 用途 |
|---|---|---|
| Java | 25 | バックエンドの言語（toolchain） |
| Spring Boot | 4.1.1 | 土台（webmvc・security・actuator・data-jpa・flyway・validation・aspectj・opentelemetry・oauth2-resource-server） |
| Spring Framework | 7.0.9 | — |
| Spring Security | 7.1.1 | 認証と認可 |
| Hibernate ORM | 7.4.5.Final | JPA（`ddl-auto: validate`） |
| Flyway | 12.4.0 | スキーマの変更（前進のみ） |
| H2 | 2.4.240 | 内部DB（組み込み・ファイル保存、`DEFRAG_ALWAYS=TRUE`） |
| Nimbus JOSE JWT | 10.9.1 | JWT（HS256） |
| Tomcat | 11.0.26 | 組み込みのサーバー（Boot の管理する版から脆弱性の回避で引き上げ） |
| logstash-logback-encoder | 9.0 | 1行1件の JSON のログ |
| opentelemetry-logback-appender | 2.28.1-alpha | ログの外部エクスポート（既定で無効） |
| SnakeYAML | 2.6 | DSL の読み込み |
| networknt json-schema-validator | 3.0.6 | DSL の検証 |
| MySQL・MariaDB・PostgreSQL の JDBC | Boot の BOM | 対象DB |
| jqwik | 1.10.1 | 性質ベースのテスト |
| ArchUnit | 1.5.0 | 構造の検査 |
| Testcontainers | Boot の BOM | 対象DB の結合テスト |
| React | ^19.2.8 | 画面 |
| react-router | ^8.3.0 | URL の振り分け |
| i18next・react-i18next | ^26.4.2・^17.0.15 | 日英の文言 |
| @fontsource/noto-sans-jp | ^5.3.0 | フォント（自己ホスティング） |
| make-you-chic-ui | `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui`（サブモジュール `edb1f94`） | デザインシステム |
| Vite・Vitest・TypeScript | ^8.2.1・^4.1.11・^6.0.3 | ビルド・テスト・型 |
| Playwright | ^1.63.0 | E2E（verify と CI の外） |

確かめた事実（今回の Intent に直接関わるもの）:
- **メール送信の仕組みは無い。** `spring-boot-starter-mail`・`jakarta.mail`・SMTP の設定・メールの受け手のコンテナ（mailpit など）は、依存の一覧・lockfile・設定・compose・README のどこにも無い（`git ls-files` の全体を `mustache|jakarta.mail|spring-boot-starter-mail|smtp|mailpit|mailhog|invite|invitation|招待` で検索して該当なし）。
- **Mustache のエンジン（java-mustache-processor を含む）は依存に無い。** `backend/gradle.lockfile` にも `mustache` は無い。
- 依存の取得元が Maven Central だけに固定されているため、java-mustache-processor を使うには Maven Central に公開されている必要がある（公開されているか、ライセンス、推移依存は今回確かめていない）。

### Test Coverage

- **Test Directories**: `backend/src/test/java/cherry/mastersmith/`（対象と同じパッケージ構成、`*Test` 102 ファイル・`*IT` 70 ファイル。`git ls-files` で数えた）、`frontend/src/**/*.test.ts(x)`（47 ファイル）、`frontend/e2e/`（4 本: skeleton・auth・admin-access・dsl-admin）
- **Test Frameworks**: JUnit 5・Spring Boot Test・Spring Security Test・jqwik・ArchUnit・Testcontainers（バックエンド）、Vitest・Testing Library・user-event・vitest-axe・fast-check（画面）、Playwright（E2E）
- **Coverage Config**: 有り。
  - バックエンド: JaCoCo。全体の合計で行 80%・分岐 70%。加えてパッケージごとに同じ下限を当てるが、`packagesJudgedByTotal` の既存の 22 パッケージ（`user.domain`・`user.repository`・`user.service`・`auth.*`・`audit.*`・`access.*`・`common.*`・`config` を含む）は外す。新しいパッケージは一覧に無いので自動で下限の対象になる（`backend/build.gradle.kts`、確かめた事実）。
  - 計測から外すのは起動クラスと `*Properties` だけ。
  - 画面: `@vitest/coverage-v8` の `thresholds` で行 80%・分岐 70%（`frontend/vitest.config.ts`）。
- user の既存のテスト: `EmailAddressTest`・`PasswordPolicyTest`・`PasswordTest`・`UserRepositoryIT`・`UserSchemaIT`・`DummyPasswordHashTest`・`InitialAdminIT`・`InitialAdminInitializerTest`・`UserAccountServiceTest`（ファイル名だけ確かめた。中身は読んでいない）。
- 構造の検査（確かめた事実）:
  - `ArchitectureTest`: web は repository を直接使わない・`@Transactional` は service の層だけ・コントローラーはエンティティを返さない・コンストラクター注入だけ・Lombok なし。
  - `AuthBoundaryArchitectureTest`: auth は `user.domain` のエンティティと `user.repository` に依存しない・user は auth に依存しない・auth と user は audit に依存しない・`User.getPasswordHash` は user の中だけで呼ぶ。
  - `AuditBoundaryArchitectureTest`: 監査の repository は更新・削除を持たない・audit だけが使う・audit は web の層を持たない（DisplayName で確かめた）。
- 結合テストで `/api/**` を公開にしたいテストは `access/testsupport/PublicApiTestRules`（`mastersmith.test-fixture.public-api=true`）を使う。本番の設定には無い。

### Code Quality Indicators

- **Linting**:
  - Java: Spotless（palantir-java-format 2.98.0、ライセンスヘッダー `/* ... */`）、SpotBugs 4.10.4 ＋ FindSecBugs 1.14.0（`spotbugsGate` で priority 1 と `SQL_` を止める。除外は `backend/config/spotbugs-exclude.xml`）。
  - 画面: Prettier・oxlint・ESLint（react-hooks）・Stylelint・tsc（`frontend/` の各設定ファイル）、ライセンスヘッダーは `frontend/scripts/check-license-header.mjs`。
  - コミット前: `.pre-commit-config.yaml`（Gitleaks・Spotless・Prettier）。
- **CI/CD**: `.github/workflows/ci.yml`。`develop` へのプッシュと `v*` のタグで `./gradlew verify` を実行し、WAR を成果物として保存する。タグのときはリリースに WAR を添付する。Gitleaks と OSV-Scanner は版と SHA-256 を固定して入れる。
- **Documentation**: README は詳しい（環境変数・戻し方・差し込み口の一覧・既知の制約）。Javadoc はほぼすべてのクラスとメソッドにあり、要件の ID（BR・NFR・ADR）への参照を含む。

### Technical Debt Signals

- `TODO`・`FIXME`・`HACK` は `backend/` と `frontend/src` に1件も無い（検索で確かめた）。
- `@SuppressWarnings` 等は 2 ファイル（`common/error/web/DefaultErrorResponseWriter.java`・`common/observability/SanitizingLogRecordExporter.java`）。中身は確かめていない。
- 画面の lint の抑止は `frontend/src/types/vitest-axe-matchers.d.ts` の1ファイルだけ。
- **パッケージごとのカバレッジの下限の対象外の一覧**（`packagesJudgedByTotal`）に `user.*` が入っている。既存の user のパッケージに手を入れても、そのパッケージは全体の合計でだけ判定される（team.md の決まりどおりの扱い）。
- **監査の出来事の追加は audit の中の変更を伴う**: `AuditEventType`（enum）・`AuditEventFactory` の switch・`AuditEventListener` の受け取りのメソッドが、出来事の種類ごとに書かれている。audit は `auth.domain`・`access.domain`・`dslmanage.domain` の出来事の型に依存している（`AuditEventFactory` の import で確かめた）。project.md の DECIDED「監査記録の共通の仕組みは後続の Intent で共通化を検討する」の状態のまま。
- **`audit_events` の列**: 操作した人は `actor_user_id`（V6 で追加）、対象の人を表す専用の列は無い（`entered_email` はログインで入力されたメールアドレス用）。
- **リフレッシュトークンの一括の無効化が無い**: `RefreshTokenRepository` は1件の無効化（`revokeIfActive`）と期限切れの削除だけを持つ。利用者のすべてのリフレッシュトークンを無効にする操作は無い。
- **画面の表示言語はブラウザの設定で起動時に1回だけ決まる**: `I18nProvider` は `navigator.languages` から決め、切り替えの手段を持たない。ApiClient は `Accept-Language` を明示して付けない（ブラウザの既定の値が送られる）。サーバーのエラーの文言の言語は、その `Accept-Language` で決まる（`ErrorResponseFactory`）。
- **ユーザーメニューに表示する名前はメールアドレス**（`loginStateProvider.toLoginState` の `displayName: snapshot.user.email`）。利用者の表に名前の列は無い。

## Handoff Summary

- **Intent-relevant finding**:
  1. **利用者の表とドメインが今回の Intent の項目を持たない（確かめた事実）**: `users` の列は `user_id`・`email`・`password_hash`（NOT NULL、VARCHAR(100)）・`admin_flag`・`created_at` だけ（`backend/src/main/resources/db/migration/V2__u2_user_account.sql`）。言語・テーマ・文字の大きさ・状態（招待中・有効など）・招待の記録の列や表は無い。`User` のエンティティ（`user/domain/User.java`）も同じ5項目で、変更のメソッド（setter やパスワードの変更）を持たない。`UserAccountService` の公開の操作は `verifyPassword`・`findById`・`existsByEmail`・`createUser(email, Password, admin)` の4つだけ（`user/service/UserAccountService.java`）。
  2. **パスワードの決まりと照合は再利用できる（確かめた事実）**: 作成時の規則は 12 文字以上（コードポイント）かつ UTF-8 で 72 バイト以内（`user/domain/PasswordPolicy.java`）、ハッシュは bcrypt で cost は設定（既定 12、`user/service/UserAccountConfig.java`）。メールアドレスは前後の空白を除いて小文字にそろえ、254 文字までと簡単な形式で確かめる（`user/domain/EmailAddress.java`）。平文のパスワードは `Password` の record（`toString` は `***`）で包み、TraceAspect の文字列化で漏れないようにしている（`user/domain/Password.java`、`common/observability/TraceAspect.java`）。
  3. **利用者の作成は `UserCreatedEvent` をトランザクションの中で知らせ、auth がロックの状態の行を作る（確かめた事実）**: `LoginAttemptStateInitializer` が `Propagation.MANDATORY` で受け取る（`auth/service/LoginAttemptStateInitializer.java`）。新しい登録の経路でも `createUser` を通せば、ログインのロックの行が作られる。
  4. **ログインは `password_hash` が照合できる利用者なら誰でも通る（確かめた事実）**: `LoginService.decide` は利用者の有無・照合の結果・ロックの状態だけで判断し、利用者の状態を見ない（`auth/service/LoginService.java`）。トークンの認証も `findById` で利用者がいれば通る（`auth/web/AccessTokenAuthenticationProvider.java`）。
  5. **メール送信と Mustache のテンプレートの仕組みは無い（確かめた事実）**: 依存・設定・compose・README のどこにも無い。追加の依存は Maven Central だけから取れ、lockfile の書き直しと、Apache License 2.0 と異なるライセンスなら ADR が要る（team.md）。
  6. **テーマと文字の大きさは make-you-chic-ui の ThemeProvider が localStorage に持つ（確かめた事実）**: 軸は `theme`（`light`・`dark` の2値だけ）・`brand`（blue・green・purple・orange）・`fontFamily`（sans・serif）・`fontSize`（sm・md・lg）。`prefers-color-scheme` は保存された値が無いときの初期値にだけ使い、動いている間の OS の切り替えには追従しない。値は `localStorage` の `design-system-*` の鍵に保存し、`<html>` の `data-theme`・`data-brand`・`data-font-family`・`data-font-size` に反映する（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/`）。画面のコードは `useTheme` をまだ使っていない（`frontend/src` を検索して該当なし）。`App.tsx` は ThemeProvider を引数なしで置いている。
  7. **表示言語は起動時にブラウザから決まり、切り替えの口が無い（確かめた事実）**: `frontend/src/app/i18n/I18nProvider.tsx` は `navigator.languages` から ja・en を決める。サーバー側の言語は要求ごとの `Accept-Language`（`common/i18n/domain/AcceptLanguageResolver.java`、既定 JA）。利用者ごとの言語は、画面とサーバーのどちらにも無い。
  8. **ログインなしで呼ぶ API はアクセスの決まりの追加が要る（確かめた事実）**: `/api/**` は既定でログイン必須。permitAll は `/actuator/health`・`/api/problems/**`・`/api/auth/login`・`/api/auth/session/**` だけ（`config/SecurityConfig.java`、`auth/web/AuthSecurityContributor.java`）。差し込み口の order は auth が 100 台、access が 200 台で、重複すると起動が失敗する（`common/security/SecurityExtensionValidator.java`）。
- **Risks / follow-up**:
  - `users.password_hash` が NOT NULL のため、パスワードの無い招待中の利用者を同じ表に置くには、列の変更（Flyway の新しいファイル。適用済みの V2 は書き換えられない）か、別の表か、使えない値を入れる方法のどれかが要る。どれでも、招待中の利用者がログインできないことをログインとトークンの認証の両方で守る必要がある（推測・仮説。上の事実 4 から）。
  - 1つ前の版のアプリが動く後方互換を保つ決まり（team.md の Deployment、README の Flyway の節）があるため、列の追加は NULL を許すか既定値付きにする必要がある（推測・仮説）。前の版の `User` のエンティティは `ddl-auto: validate` なので、列が増えても検証は通ると見られる（未検証）。
  - パスワードの変更の後に、ほかの端末のリフレッシュトークンを無効にするか、アクセストークン（既定 5 分）の扱いをどうするかは決まっていない。一括の無効化の操作は今は無い（事実）。project.md の DECIDED で、アクセストークンの失効の仕組みは持たないと決まっている。
  - 招待のトークン（URL に入る値）を新しく作る場合、既存のリフレッシュトークンと同じく「値はハッシュだけを保存」「ログ・監査・エラー応答に出さない」の扱いが要る（project.md の Forbidden はトークン全般を名指ししていないが、リフレッシュトークンの実装 `RefreshTokenValues.hash` と同じ考え方が当てはまると見られる。推測・仮説）。URL は Web のアクセスログ・トレースの属性に残りうる（`UrlQueryStrippingObservationFilter` が問い合わせの部分を外す仕組みがあることはファイル名から見えるが、中身は確かめていない）。
  - 招待メールの URL を組み立てる元のベースURLには、今 `MASTERSMITH_WEB_BASE_URL`（エラー応答の `type` 用、無ければ要求から組み立てる）がある（`application.yaml`）。要求から組み立てると Host ヘッダーの偽装で別のサイトへの URL を送りうるため、メール用には設定の値を必須にするかを決める必要がある（推測・仮説）。
  - 監査に新しい出来事（利用者の登録・招待の送信・登録の完了・パスワードの変更など）を足すと、`AuditEventType`・`AuditEventFactory`・`AuditEventListener`・関係するテスト（`AuditSecretLeakIT` などの列の一覧を持つテスト）を変える必要がある（事実から。テストの中身は未確認）。project.md の DECIDED で監査の共通化は後続に回っているため、共通化するかをこの Intent で決め直すかの判断が要る。
  - 監査の記録は確定の後に業務の接続を持ったまま2本目を借りる（README の既知の制約、`audit/service/AuditEventListener.java` と `AuditEventRecorder` の `REQUIRES_NEW`）。メール送信を業務のトランザクションや同じスレッドの中で行うと、SMTP の待ちの間に内部DB の接続を持ち続けうる（推測・仮説）。project.md の「要求1件で接続を2本使う経路は負荷の試験で確かめる」の決まりが当てはまる。
  - テーマの `system` は make-you-chic-ui の `ThemeMode` に無い（`light`・`dark` だけ）。サブモジュールは直接変えられない（project.md の Forbidden）ため、`system` は frontend の側で `prefers-color-scheme` を見て `setTheme` を呼ぶ形になると見られる（推測・仮説）。また ThemeProvider は値を localStorage にも保存するため、サーバーに保存した利用者の設定と、同じブラウザの別の利用者の localStorage の値が食い違う場面を考える必要がある（推測・仮説）。
  - ブランドカラー（`brand`）とフォントファミリー（`fontFamily`）をインスタンス全体の固定の設定にするには、`application.yaml` の値を画面へ渡す道が要る。今、設定を画面に渡す API は無い（事実）。make-you-chic-ui の `brand` は4色、`fontFamily` は sans・serif の2値に限られる（事実）。
  - 画面の文字の大きさの `fontSize`（sm・md・lg）は make-you-chic-ui の3値に限られる（事実）。
  - `frontend-registry` の差し込み口は、ユーザーメニューの項目（`UserMenuItemRegistration`）とログイン不要の画面（`access: 'PUBLIC'`、`layout: 'STANDALONE'`）を足せる（事実）。プリファレンスの画面と招待を受けた人の登録の画面は、この口で足せると見られる（推測・仮説）。
  - 既存の `user.*` のパッケージはパッケージごとのカバレッジの下限の対象外のため、user に大きく足すと下限の効き方が弱くなる。新しい機能を新しいパッケージに置けば自動で下限の対象になる（事実と、その帰結）。
  - 前回のコード知識ベースの部品の ID をそのまま使った。`dsl`・`dslmanage`・`targetdb`・`common-health`・`perf-and-monitoring` などは今回流し読みのため、記録上の深い範囲からは外れる。
