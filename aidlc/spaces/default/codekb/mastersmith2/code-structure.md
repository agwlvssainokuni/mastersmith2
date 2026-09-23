# コードの構成（mastersmith2）

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway のスキーマ変更 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から `Exec` タスクで呼ぶ |
| `vendor/make-you-chic-ui/` | 外部のデザインシステム | Git サブモジュール。このリポジトリからは変更しない |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`）、Wrapper、ルートの検査の段（`verify`） |
| `config/` | ビルドの設定 | ライセンスヘッダーのひな形、OSV の判定で開発用の依存でも止める道具の一覧 |
| `.github/` | CI | `workflows/ci.yml`、`dependabot.yml` |
| `Dockerfile`・`compose.yaml`・`.env.example`・`docker/` | コンテナ | アプリのコンテナ、profile で起動する監視、使い捨ての負荷試験の環境 |
| `perf/` | 負荷の試験 | k6 の場面と手順（`perf/README.md`） |
| `README.md` | 文書 | 前提の道具・検査・起動・戻し方・環境変数・差し込み口の一覧 |
| `aidlc/` | ワークフローの記録 | AI-DLC の記録（アプリのソースではない） |

## バックエンドのパッケージ（`backend/src/main/java/cherry/mastersmith/`）

機能ごとのパッケージの中を、`web`（HTTP の受け渡し）・`service`（業務処理とトランザクション）・`domain`（値・判定・エンティティ）・`repository`（DB アクセス）の層に分けている。

```
cherry.mastersmith
├── MastersmithApplication          起動クラス（SpringBootServletInitializer、@ConfigurationPropertiesScan）
├── config                          フィルターの連鎖・画面の配信・転送元ヘッダー・外部エクスポート・応答ヘッダーの設定
├── common
│   ├── error/{domain,service,web}  Problem Details、BusinessException、ProblemTypeRegistry、GlobalExceptionHandler
│   ├── security                    SecurityRuleContributor・ApiDefaultAccess・ErrorResponseWriter と起動時の検査
│   ├── web                         CacheControlFilter・RequestSizeLimitFilter・mastersmith.web.* の設定の型
│   ├── health                      制限時間付きの内部DBの確認
│   ├── i18n/domain                 Accept-Language から表示言語（ja／en）を決める
│   └── observability               トレースIDの参照、送るトレースの消毒、URL の問い合わせの除去、TraceAspect
├── auth/{domain,service,repository,web}   ログイン・トークン・ロック・ログアウト・定期削除（U2）
├── access/{domain,service,web}            管理者のみの API のアクセス制御（U3）。repository は無い
├── audit/{domain,service,repository}      監査ログの追記（U4）。web は無い
└── user/{domain,service,repository}       利用者・パスワード・初期管理者（U2）。web は無い
```

各パッケージの責務と依存は `component-inventory.md` を参照。

### ファイルの分類（バックエンド）

| 分類 | 名前の決まり | 例 |
|---|---|---|
| コントローラー | `XxxController`（`web`） | `AuthController`・`AdminCheckController`・`ProblemTypeController`・`ErrorPathController` |
| 要求・応答の DTO | `XxxRequest`・`XxxResponse`（`record`、`web`） | `LoginRequest`・`TokenResponse`・`CurrentUserResponse`・`ProblemTypeResponse` |
| 業務処理 | `XxxService`（`service`） | `LoginService`・`TokenRefreshService`・`LogoutService`・`UserAccountService` |
| 設定の型 | `XxxProperties`（`record`、`@ConfigurationProperties`） | `AuthProperties`・`MastersmithWebProperties`・`HealthProperties` |
| 問題の種類 | `XxxProblemTypes`（`domain`）＋`XxxProblemTypeCatalog`（`service`、Bean） | `AuthProblemTypes`＋`AuthProblemTypeCatalog` |
| 出来事 | `XxxEvent`（`record`、`domain`） | `AuthenticationEvent`・`AdminAccessDeniedEvent` |
| セキュリティの差し込み | `XxxSecurityContributor`（`web`） | `AuthSecurityContributor`（110）・`AdminSecurityContributor`（210） |
| DB アクセス | `XxxRepository`（Spring Data JPA） | `RefreshTokenRepository`・`LoginAttemptStateRepository`・`AuditEventRepository`・`UserRepository` |
| パッケージの説明 | `package-info.java` | 各パッケージにある |

### 設定とスキーマ（`backend/src/main/resources/`）

- `application.yaml`: アプリの設定。秘密情報は環境変数の参照（`${MASTERSMITH_...:}`）だけ。独自の設定は `mastersmith.web.*`・`mastersmith.health.*`・`mastersmith.observability.*`・`mastersmith.trace.*`・`mastersmith.auth.*`・`mastersmith.security.*`。`mastersmith.target-db.*` は無い。
- `logback-spring.xml`: 1行1件の JSON のログ（logstash-logback-encoder）と、有効時だけの OTLP の送信。
- `db/migration/`: Flyway。名前は `V<番号>__<単位>_<内容>.sql`、前進のみ。
  - `V1__u1_baseline.sql`: 表を作らない基準線
  - `V2__u2_user_account.sql`: `users`
  - `V3__u2_authentication.sql`: `login_attempt_states`・`refresh_tokens`
  - `V4__u4_audit_event.sql`: `audit_events`

## テストの構成（`backend/src/test/`）

- `src/test/java/cherry/mastersmith/` は本体と同じパッケージ構成（130 ファイル）。単体テストは `*Test`（Gradle の `test`）、Spring と H2 を起動する結合テストは `*IT`（Gradle の `integrationTest`）で、名前で分けて実行する。
- `common/testsupport/`: `TestDatabase`（クラスごとの一時ディレクトリに H2 を作り、`@DynamicPropertySource` で登録）、`HttpTestClient`（`RANDOM_PORT` で起動した実際の Tomcat に HTTP を送る）ほか。
- `src/test/resources/`: `archunit.properties`、`junit-platform.properties`（jqwik の記録を `build/jqwik-database` へ）、`META-INF/spring.factories`（テスト用の署名鍵を入れる `EnvironmentPostProcessor`）、`static/`（テスト用の画面ファイル）。

## 画面の構成（`frontend/src/`）

```
frontend/src
├── main.tsx                    起動（登録の読み込みと検査の後に描画）
├── app/                        骨組み（U1）
│   ├── App.tsx
│   ├── registry/               FeatureRegistration の型・読み込み（import.meta.glob、eager）・検査
│   ├── routing/                AppRouter・decideRoute（URL と登録の完全一致で1画面）
│   ├── navigation/             サイドバーの項目の組み立て
│   ├── layout/                 ShellLayout（AppShell の中）・StandaloneLayout・LoginLayout
│   ├── i18n/                   i18next の設定
│   ├── login-state/            ログイン状態の受け取り
│   ├── pages/                  ホーム・ページが見つかりません・起動エラー
│   └── testing/                テストの補助
├── shared/api-client/          apiFetch・apiRequest・ApiError
├── features/
│   ├── auth/                   ログイン画面・トークンの保持・ログイン状態の提供元・ログアウトのメニュー（U2）
│   └── admin/                  管理者向け領域 /admin の置き場（U3）
└── types/                      vitest-axe の型
```

- 新しい機能は `features/<featureId>/registration.ts` を置くだけで読み込まれる（`frontend/src/features/README.md`）。
- テストは対象と同じ場所の `*.test.ts(x)`（29 ファイル）。E2E は `frontend/e2e/*.e2e.ts`（Playwright、3 ファイル）。
- `frontend/scripts/`: ライセンスヘッダーの検査（`check-license-header.mjs`）と初回読み込み量の確認（`check-bundle-size.mjs`）。

### ファイルの分類（画面）

| 分類 | 名前の決まり | 例 |
|---|---|---|
| 画面・部品 | PascalCase の `.tsx`、同じ場所に素の CSS | `LoginForm.tsx`・`LoginForm.css`・`AdminAreaPage.tsx` |
| API の呼び出し | `xxxApi.ts` | `authApi.ts`・`adminApi.ts` |
| 純粋な関数・状態 | camelCase の `.ts` | `validateLoginInput.ts`・`adminAreaStatus.ts`・`decideRoute.ts` |
| 機能の登録 | `registration.ts` | `features/auth/registration.ts`・`features/admin/registration.ts` |

## コードのパターン

- **差し込み口の Bean の一覧**: `List<SecurityRuleContributor>`・`List<ProblemTypeCatalog>` を受け取り、起動時に並べ・検査する（重複や決まり違反で起動を止める）。画面の登録も同じ考え方である。
- **アプリの中の出来事**: 機能は `ApplicationEventPublisher` で知らせ、受け取り側（`audit`）が `@TransactionalEventListener` で受ける。
- **明示のトランザクション**: `LoginService` は `PlatformTransactionManager` から `TransactionTemplate` を作り、照合をトランザクションの外に出している。ほかは `@Transactional`（service の層だけ）。
- **値の型で秘密情報を包む**: `Password`・`AccessTokenValue`・`RefreshTokenValue` などは `toString` で伏せ字にする（`TraceAspect` やログへの漏えいを防ぐ）。
- **注入する時計**: `Clock` の Bean を使い、テストでは差し替える。
- **構造化ログ**: `LOGGER.atInfo().addKeyValue(...)` のキー・値の API。文字列の連結はしない。
- **Javadoc の設計参照**: クラス・公開メソッドに日本語の Javadoc があり、元の設計書の番号（BR・NFR・ADR・WF）を参照している。
