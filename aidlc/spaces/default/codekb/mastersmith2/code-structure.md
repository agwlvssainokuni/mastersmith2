# コードの構成（mastersmith2）

各部品の責務と状態は `component-inventory.md`、部品の間の依存は `dependencies.md` に書き、ここでは置き場と決まりだけを書く。

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway のスキーマ変更・SpotBugs の除外 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から呼ぶ |
| `vendor/make-you-chic-ui/` | 外部のデザインシステム | Git サブモジュール。このリポジトリからは変更しない（`project.md` の Forbidden） |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`）、Wrapper、1コマンドの検査 `verify` |
| `config/` | ビルドの設定 | ライセンスヘッダーのひな形、OSV の判定で止める道具の一覧（流し読み） |
| `.github/` | CI | `workflows/ci.yml`・Dependabot |
| `Dockerfile`・`compose.yaml`・`.env.example` | コンテナ | アプリのイメージとサービス、profile で起動する監視と見本の対象DB |
| `docker/`・`perf/` | 付属の環境と試験 | OTLP の受け手・監視・見本の対象DB の初期化、k6 の負荷の試験（流し読み） |
| `README.md` | 文書 | 起動・環境変数・既知の制約・戻し方・差し込み口の一覧 |
| `aidlc/`・`.claude/` | ワークフローの記録と枠組み | アプリのソースではない |

## バックエンドのパッケージ（`backend/src/main/java/cherry/mastersmith/`、279 ファイル）

```
cherry.mastersmith
├── MastersmithApplication
├── config                                          6   Security の連鎖の組み立て・画面の配信・観測
├── common/{error,health,i18n,observability,security,web}   38
├── auth/{domain,service,repository,web}            50  ログイン・トークン・ロック・ログアウト
├── access/{domain,service,web}                     20  管理者のみの API の認可と 401・403
├── audit/{domain,service,repository}               14  監査イベントの追記（web の層は持たない）
├── user/{domain,service,repository}                18  利用者・パスワードの決まりと照合・初期管理者
├── targetdb/{config,domain,repository,service}     26  対象DB の接続とスキーマの読み取り
├── dsl/{domain,parse,validate,service}             53  DSL の型・安全な読み込み・検証・適用中のモデル
└── dslmanage/{domain,generate,repository,service,web}   53  既定の DSL の生成・投入・プレビュー・適用・履歴
```

数字は `git ls-files` で数えた Java のファイル数（`package-info.java` を含む）。

- 層は `web`（HTTP の受け渡し）・`service`（業務処理とトランザクション）・`domain`（値・判定・エンティティ・出来事の型）・`repository`（DB アクセス）。決まりは `team.md` の Code Style のとおりで、ArchUnit で確かめている（`code-quality-assessment.md`）。
- 機能だけに効く設定（`@Configuration`・`XxxProperties`）は機能のパッケージの中に置く（例: `user/service/UserAccountConfig.java`・`PasswordProperties.java`・`InitialAdminProperties.java`、`auth/service/AuthProperties.java`）。
- 想定内のエラーの種類（`ProblemType`）は機能ごとの `XxxProblemTypes`（domain）と `XxxProblemTypeCatalog`（service の Bean）に置く。

### 今回の Intent に関わるファイル（深く読んだもの）

| 場所 | 役割 |
|---|---|
| `user/domain/User.java` | 利用者のエンティティ（5 項目、変更のメソッドなし） |
| `user/domain/EmailAddress.java`・`Password.java`・`PasswordPolicy.java` | メールアドレスの正規化、平文のパスワードの包み（`toString` は伏せ字）、作成時のパスワードの規則 |
| `user/service/UserAccountService.java` | 照合・読み取り・有無・作成の4つの公開の操作 |
| `user/service/InitialAdminInitializer.java`・`DummyPasswordHash.java` | 起動時の初期管理者の作成、存在しない利用者の照合の時間をそろえるダミーのハッシュ |
| `user/service/UserCreatedEvent.java`・`UserSummary.java`・`PasswordVerification.java` | 作成の出来事、ほかの機能へ渡す利用者の要約、照合の結果 |
| `auth/service/LoginService.java`・`TokenRefreshService.java`・`LogoutService.java`・`AccessTokenService.java` | ログイン・更新・ログアウト・JWT の発行と検証 |
| `auth/service/LoginAttemptStateInitializer.java` | 利用者の作成の出来事でロックの状態の行を作る |
| `auth/web/AuthController.java`・`AuthSecurityContributor.java`・`AccessTokenAuthenticationProvider.java`・`RefreshCookies.java`・`OriginVerifier.java` | 認証の API、アクセスの決まり（order 110）、トークンの認証、Cookie、Origin の確かめ |
| `access/web/AdminSecurityContributor.java`・`AdminAuthorizationManager.java`・`AdminApiDefaultAccess.java` | 管理者のみの決まり（order 210）と `/api/**` の既定のログイン必須 |
| `audit/domain/AuditEventType.java`・`AuditEventFactory.java`、`audit/service/AuditEventListener.java`・`AuditEventRecorder.java` | 出来事の種類、組み立て、確定の後の受け取り、`REQUIRES_NEW` の記録 |
| `common/security/`・`common/error/`・`common/i18n/` | 差し込み口と起動時の検査、Problem Details、表示言語 |
| `config/SecurityConfig.java`・`WebConfig.java` | 連鎖の組み立て、SPA の配信 |

### 設定とスキーマ（`backend/src/main/resources/`）

- `application.yaml`: 独自の設定は `mastersmith.*`（`web.base-url`・`auth.*`（トークンの期限・ロック・bcrypt・初期管理者）・`target-db.*`・`dsl.*`・`security.content-security-policy`）。メールの設定は無い。
- `logback-spring.xml`: 1行1件の JSON のログ。
- `db/migration/`: Flyway、前進のみ。V1 基準線、V2 `users`、V3 `login_attempt_states`・`refresh_tokens`、V4 `audit_events`、V5 `dsl_previews`・`dsl_applied_revisions`、V6 `audit_events` への列の追加（`actor_user_id`・`dsl_hash`・`dsl_source`・`rejection_kind`）。次に足すのは V7 である。
- `dsl/`: DSL の JSON Schema（流し読み）。

## テストの構成

- `backend/src/test/java/cherry/mastersmith/`: 本体と同じパッケージ構成。単体は `*Test`（102 ファイル、タスク `test`）、Spring と H2 を起動する結合は `*IT`（70 ファイル、タスク `integrationTest`）。対象DB の結合テストは Testcontainers。
- 構造の検査は `ArchitectureTest`（全体）と機能ごとの `*BoundaryArchitectureTest`（`auth`・`audit`・`dsl`・`dslmanage`（2つ）・`targetdb`）。今回中身を読んだのは `ArchitectureTest`・`AuthBoundaryArchitectureTest` で、`AuditBoundaryArchitectureTest` は DisplayName だけ。
- テストの補助は各機能の `testsupport/`（`backend-test-support`）。`/api/**` を公開にしたい結合テストは `access/testsupport/PublicApiTestRules` を使う（本番の設定には無い）。
- 画面は対象と同じ場所の `*.test.ts(x)`（47 ファイル）、E2E は `frontend/e2e/`（4 ファイル: skeleton・auth・admin-access・dsl-admin）。

件数はファイルを数えた値で、実行の件数ではない。

## 画面の構成（`frontend/src/`）

```
frontend/src
├── main.tsx
├── app/
│   ├── App.tsx              ThemeProvider・ToastProvider・ModalStackProvider・I18nProvider・登録・ログイン状態の順に包む
│   ├── registry/            FeatureRegistration の型・自動の読み込み・検査
│   ├── routing/             URL の振り分け（PUBLIC・LOGGED_IN・ADMIN）
│   ├── navigation/          サイドバーとユーザーメニューの項目
│   ├── layout/              AppShell の配置（SHELL）とログイン用・単独の画面（STANDALONE）
│   ├── i18n/                i18next の初期化と表示言語の決定
│   ├── login-state/         ログイン状態の受け渡し
│   └── pages/               共通の画面（流し読み）
├── shared/api-client/       同じオリジンの API の呼び出し
└── features/{auth,admin,dsl}   機能ごとの画面と registration.ts
```

新しい機能は `features/<featureId>/registration.ts` を置くだけで読み込まれる（`app/registry/registrationModules.ts`）。登録できるのは画面（`access` は `PUBLIC`・`LOGGED_IN`・`ADMIN`、`layout` は `SHELL`・`STANDALONE`）・サイドバーの項目・ユーザーメニューの項目・ログイン状態の提供元・文言（鍵は `<featureId>.` で始める）である（`app/registry/types.ts`）。
