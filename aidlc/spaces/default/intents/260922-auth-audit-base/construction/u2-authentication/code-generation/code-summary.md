# Code Summary — U2 認証（u2-authentication）

`code-generation-plan.md` の Step 1〜19 をすべて終えた。アプリのコードはリポジトリのルート直下（`backend/`・`frontend/`・ルートの設定ファイル）に置き、`aidlc/` の下には置いていない。方法は Testing Contract の test-after（層ごとに実装してから、その層のテストを書いて実行する）に従った。要件・決まりと、実装とテストのファイルの対応は `traceability.json` にある。作った・変えたファイルの一覧は `source-manifest.json` にある。

## 作った・変えたファイル

計画の承認のコミット（d8d38d6）からの差分。変更 10 件、新規 143 件、削除なし。`vendor/make-you-chic-ui` の追跡されるファイルとサブモジュールの固定先は変えていない。

### 変更したファイル（10 件）

| ファイル | 変更 | 根拠 |
|---|---|---|
| `gradle/libs.versions.toml` | `spring-boot-starter-oauth2-resource-server` を追加（版は Spring Boot の BOM） | Step 1 |
| `backend/build.gradle.kts` | 依存の一覧に1行追加 | D1（U1 のファイルの変更・1つ目） |
| `backend/gradle.lockfile` | 上の依存の解決（`nimbus-jose-jwt` 10.9.1、`spring-security-oauth2-*` 7.1.1 を追加） | Step 1 |
| `backend/src/main/resources/application.yaml` | 全単位で共用する設定に `mastersmith.auth` のまとまりを追加 | C1（依頼者の修正依頼） |
| `backend/src/test/java/cherry/mastersmith/common/db/DatabasePersistenceIT.java` | Flyway の履歴の確認を「版 1 が成功で入っている」に変更（件数ちょうどの確認を外す） | D1 の4つ目（問題1・案 A） |
| `frontend/src/app/App.test.tsx` | 既定の登録を使う2件のテストに空の登録（`renderApp({})`）を渡す | D1 の5つ目（問題2・案 A） |
| `frontend/playwright.config.ts` | WAR の起動に、実行のたびに作る署名鍵と初期管理者の値を環境変数で渡す | D1（2つ目） |
| `frontend/e2e/u1-skeleton.e2e.ts` | 起動時のトークンの更新の 401 によるブラウザの表示だけをエラーの集計から除く | D1（3つ目） |
| `.env.example` | U2 の秘密情報と任意の設定を確定した名前で追加（値は空） | Step 17 |
| `README.md` | 環境変数の表、コンテナでの初めての起動とスモークテスト、署名鍵の交換、localhost の前提、U2 が提供する差し込み口を追加 | Step 17 |

### 新規のファイル（143 件）

- **スキーマの変更**: `backend/src/main/resources/db/migration/V2__u2_user_account.sql`、`V3__u2_authentication.sql`
- **`user`（UserAccount）**: `domain/{User, EmailAddress, PasswordPolicy, Password, package-info}`、`repository/{UserRepository, package-info}`、`service/{UserAccountService, UserSummary, PasswordVerification, UserCreatedEvent, UserAccountConfig, DummyPasswordHash, InitialAdminInitializer, PasswordProperties, InitialAdminProperties, package-info}`、`user/package-info`
- **`auth`（Authentication）**: `domain/{LoginAttemptState, RefreshToken, LockPolicy, LockState, LockDecision, LoginOutcome, TokenExpiry, RefreshTokenValues, RefreshTokenValue, AccessTokenValue, AuthProblemTypes, TokenFailureReason, TokenAuthenticationException, AuthenticatedUser, AuthenticationEvent, AuthenticationEventType, LoginFailureReason, ClientInfo, package-info}`、`repository/{LoginAttemptStateRepository, RefreshTokenRepository, package-info}`、`service/{AccessTokenService, SigningKeyProvider, LoginService, TokenRefreshService, LogoutService, RefreshTokenCleanupJob, LoginAttemptStateInitializer, AuthProblemTypeCatalog, AuthProperties, AuthClockConfig, AuthSchedulingConfig, IssuedTokens, IssuedAccessToken, LoginCommand, package-info}`、`web/{AuthController, LoginRequest, TokenResponse, CurrentUserResponse, RefreshCookies, ClientInfoResolver, OriginVerifier, AuthSecurityContributor, AccessTokenAuthenticationProvider, AuthenticatedUserToken, TokenAuthenticationEntryPoint, package-info}`、`auth/package-info`
- **バックエンドのテスト**: 単体 `auth/domain/*Test`（6）、`auth/service/*Test`（7）、`auth/web/*Test`（5）、`user/domain/*Test`（3）、`user/service/*Test`（3）、構造の検査 `auth/AuthBoundaryArchitectureTest`。結合 `auth/repository/{AuthSchemaIT, LoginAttemptStateRepositoryIT, RefreshTokenRepositoryIT}`、`auth/service/{LoginConcurrencyIT, RefreshConcurrencyIT, SigningKeyStartupIT, AuthSettingsIT}`、`auth/web/{LoginApiIT, TokenApiIT, LogoutApiIT, AccessTokenApiIT, AuthEventsIT, AuthSecretLeakIT, AuthProblemTypesIT}`、`user/repository/{UserSchemaIT, UserRepositoryIT}`、`user/service/InitialAdminIT`
- **テストの補助**: `auth/testsupport/{TestSigningKeyEnvironmentPostProcessor, AuthTestTokens, SqlStatementCounter, CountingPasswordEncoder, MutableClock, CapturedAuthenticationEvents, AuthApiTestConfig, AuthApi, ProtectedTestEndpoint}`、`backend/src/test/resources/META-INF/spring.factories`
- **画面**: `frontend/src/shared/api-client/{apiClient.ts, apiError.ts}`（＋テスト2）、`frontend/src/features/auth/{authApi.ts, authSession.ts, loginStateProvider.ts, validateLoginInput.ts, LoginPage.tsx, LoginForm.tsx, LoginForm.css, registration.ts}`（＋テスト7）、`frontend/e2e/u2-auth.e2e.ts`

## 主な実装の判断

- **設定（C1、依頼者の修正依頼）**: `mastersmith.auth.*` を全単位で共用する `application.yaml` に `${MASTERSMITH_AUTH_XXX:既定値}` の形で書いた。秘密・必須の項目（`signing-key`、`initial-admin.email`、`initial-admin.password`）は既定値を空にし、値は書いていない。Java の `@DefaultValue` と同じ既定値であることを `AuthPropertiesDefaultsTest` で確かめる。
- **API の形（C2）**: ログインと更新は、アクセストークン・有効期限・利用者を JSON の本文で返し、リフレッシュトークンは本文に入れずに `Set-Cookie`（`mastersmith_refresh`、`HttpOnly`・`Secure`・`SameSite=Strict`・`Path=/api/auth/session`・`Max-Age`＝有効期限）で返す。ログアウトは 204 と `Max-Age=0` の削除の Cookie。更新の失敗でも、例外を起こす前に削除の Cookie を応答に入れる（`TokenApiIT` で確認）。
- **ログインの手順**: 利用者の検索と照合をトランザクションと排他の外で行い、そのあと1回の短いトランザクションで、利用者の行（いなければダミーの行）を排他つきで読み、判定し、明示の更新を1回行う。3つの失敗の経路と 72 バイト超えの4通りで、SQL の種類と回数（`select users`・`select login_attempt_states for update`・`update login_attempt_states`）と照合の回数（1回）が同じであることを `LoginApiIT` で確かめた。
- **行の排他**: H2 で「待ちの上限 3 秒」（`jakarta.persistence.lock.timeout`）と「排他を持つ行を飛ばす」（`SKIP LOCKED`）がどちらも効くことを `LoginAttemptStateRepositoryIT` で確かめた。計画の C7 の代わりの方法は使っていない。
- **アクセストークン**: 発行は `NimbusJwtEncoder`、検証は HS256 だけを受け付ける `NimbusJwtDecoder` を起動時に1つ作って使い回す。有効期限は注入した時計で比べ、ずれの許容は 0（期限ちょうどで無効）。方式の確認（`alg: none`・HS512・RS256 の拒否）は復号の前に行い、失敗を `TOKEN_MALFORMED`・`TOKEN_INVALID`・`TOKEN_EXPIRED` に分けた。
- **秘密情報**: 秘密の値を包む型（`Password`・`RefreshTokenValue`・`AccessTokenValue`）と、秘密を持つ `record`（`LoginRequest`・`TokenResponse`・`AuthProperties`・`InitialAdminProperties`）の文字列化を伏せ字にした。`AuthSecretLeakIT` で、`cherry.mastersmith.auth`・`cherry.mastersmith.user` を TRACE にした起動・ログイン・失敗・更新・ログアウトのログに、パスワード・保存したハッシュ・アクセストークン・リフレッシュトークン・署名鍵が出ないことを確かめた。
- **Bearer の検証の置き場所**: `AccessTokenAuthenticationProvider` を Bean にせず `AuthSecurityContributor`（order 110）の中で作る。メソッドの呼び出しの追跡が、トークンを含む引数を文字列にしないようにするため。
- **画面**: アクセストークンと利用者はモジュールの中の変数（メモリ）だけに持つ。ApiClient は AuthUi に依存せず、登録された手段でトークンの付与と 401 の更新・送り直し（同時の 401 は1回の更新にまとめる）を行い、認証の API は対象外にする。

## テストとカバレッジ

| 対象 | テストの件数 | 行カバレッジ | 分岐カバレッジ |
|---|---|---|---|
| バックエンド全体（単体 `*Test` と結合 `*IT`。U1 の 206 件を含む） | 単体 270・結合 152（失敗 0） | 96.8%（1101/1137） | 91.2%（342/375） |
| U2 のパッケージ（`cherry.mastersmith.auth`・`cherry.mastersmith.user`） | 上の内数 | 98.6%（545/553） | 97.0%（128/132） |
| 画面全体（Vitest・Testing Library・vitest-axe・fast-check。U1 の 85 件を含む） | 125（失敗 0） | 98.9% | 94.65% |
| U2 の画面のファイル（`src/features/auth/`・`src/shared/api-client/`） | 上の内数 | 80〜100%（各ファイル） | 90.9〜100%（各ファイル） |
| ビルドした WAR での画面の確認（Playwright。U1 の 2 件を含む） | 4（失敗 0） | — | — |

- 下限（行 80%・分岐 70%）は JaCoCo と `@vitest/coverage-v8` の両方で満たした。計測の除外は U1 の範囲（起動クラス・`*Properties`・`src/main.tsx`・型の宣言）のままで、広げていない。
- 性質ベースのテスト: jqwik（メールアドレスの正規化、パスワードの 72 バイトの判定、ロックの判定、有効期限の判定）、fast-check（同時の 401 の更新が1回、空の入力の検査）。
- 画面の部品ごとに vitest-axe のアクセシビリティ検査を1件入れた。

## 1コマンドの検査（`./gradlew verify`）の結果

最後の実行で、すべての段が通った（終了コード 0）。

| 段 | 結果 |
|---|---|
| 0 準備 | 成功 |
| 1 フォーマット | 成功（Spotless、Prettier） |
| 2 リンタ | 成功（oxlint、ESLint、Stylelint） |
| 3 ライセンスヘッダー | 成功 |
| 4 ビルド | 成功（Java のコンパイル、`tsc --noEmit`、Vite のビルド） |
| 5 単体テスト | 成功（JUnit 270、Vitest 125） |
| 6 結合テスト | 成功（152） |
| 7 カバレッジの下限 | 成功（上の表） |
| 8 安全の検査 | 成功（SpotBugs の High 0 件、OSV-Scanner の失敗の条件 0 件・警告 0 件、Gitleaks の検出なし） |
| 9 成果物と量の確認 | 成功 |

- `pre-commit run --all-files`（Gitleaks・Spotless・Prettier）も通った。
- E2E は `./gradlew e2eTest` が U1 の E2E だけを実行するため、`unit-test-instructions.md` 2.3 のとおり `npx playwright test e2e/u2-auth.e2e.ts`（U1 の分とあわせて4件）で実行し、すべて通した。

## コンテナでの起動と確認

- `.env` に作った署名鍵と初期管理者を入れて `docker compose up -d --build` を行い、コンテナは healthy になった。`/actuator/health` は 200。
- 起動のログに「初期管理者を作成しました」の INFO が1件出た。ログに初期管理者のパスワードと署名鍵は出ていない。
- スモークテスト: 初期管理者でログイン（200、`admin: true`）、更新（200）、ログアウト（204）、誤ったパスワード（401）。
- この確認をした PC の colima の VM は CPU が 2 のため、確認のときだけ CPU の上限を 2 に上書きして起動した（リポジトリの `compose.yaml` は設計どおり 4 のまま。U1 と同じ扱い）。確認の後、`.env` は消した（コミットしない）。

## 承認済みの設計との違い（D3・問題3）

| 違い | 承認済みの記述 | 実装 | 決定 |
|---|---|---|---|
| ログインの手順の順番 | `functional-spec.md` WF2: ロックの状態を排他つきで読んでから照合 | 検索 → 照合（排他の外）→ 1回の短いトランザクションで排他つきの読み取り・判定・書き込み | D3-A。NFR Design（確定回答 Q1）のとおり作り、Functional Design は書き換えない。外から見える結果（応答・読み書きの回数・判定）は同じで、`LoginApiIT` で確かめた |
| 問題の種類 | `rules.md` BR9.1: 3つ | `ORIGIN_NOT_ALLOWED`（403）を加えて4つ | D3-A。NFR5.4 と `nfr-design/security-design.md` 8章による |
| 構造の検査の範囲 | 計画 Step 8・`unit-test-instructions.md`: 「`auth` は `user.domain`・`user.repository` に依存しない」 | 「`auth` は `user.domain` のエンティティ（`User`）と `user.repository` に依存しない」に狭めた | 依頼者の決定（問題3・案 A）。秘密の値の型 `user.domain.Password` を `user.service.verifyPassword` の引数として `auth` から使うため。ADR-001 の目的（パスワードのハッシュを `user` の外へ出さない）は、`User.getPasswordHash` を `user` の中からだけ呼ぶ検査で守る |

## 計画からの逸脱と、生成中に決まったこと

1. **U1 のファイルの変更が2つ増えた（依頼者の決定）**: 問題1（`DatabasePersistenceIT` の Flyway の件数の確認）と問題2（`App.test.tsx` の既定の登録）を、どちらも案 A のとおり最小限に直した。D1 の3つとあわせて、変更した U1 のファイルは5つ。
2. **`token_hash` の型の対応**: `BINARY(32)` のまま Hibernate の起動時のスキーマ検証を通すため、`RefreshToken` に `@JdbcTypeCode(SqlTypes.BINARY)` を付けた。
3. **時計と定期実行の設定を分けた**: `AuthClockConfig`（`Clock` の Bean）と `AuthSchedulingConfig`（`@EnableScheduling`）の2つに分けた。
4. **`LoginAttemptStateInitializer` を Step 7 で先に作った**: Step 8 の `InitialAdminIT`（作成と同時にロックの状態の行ができる）に必要なため、Step 9 の部品のうちこれだけを前倒しした。同じ層の中での順序の入れ替え。
5. **E2E の秘密情報の受け渡し**: `playwright.config.ts` は設定の読み込みとテストの実行で別々に評価されるため、作った値を環境変数（`E2E_SIGNING_KEY`・`E2E_ADMIN_PASSWORD`）に入れて両方で同じ値を使う形にした。値はリポジトリに置かない。
6. **E2E のエラーの集め方**: ブラウザのコンソールの文言に URL が含まれないため、メッセージの場所（`message.location().url`）で認証の API の 401 を除く形にした。U2 の E2E では、わざと誤ったパスワードで送るログインの 401 も同じ扱いにした。
7. **計画に無い小さな部品**: `AuthenticatedUserToken`（認証の結果の型）、`IssuedTokens`・`IssuedAccessToken`・`LoginCommand`（業務処理の入出力の型）、`WebSecretTypesTest`（要求・応答の伏せ字のテスト）、`AuthPropertiesDefaultsTest`（C1 の既定値の一致のテスト）、`AuthApi`・`AuthApiTestConfig`・`ProtectedTestEndpoint`（API の結合テストの補助）。
8. **性能の要件（NFR1.1〜NFR1.4、NFR1.6）**: 実測は Performance Validation に送る（`traceability.json` で Deferred）。
9. **依存関係の名前**: 計画どおり `spring-boot-starter-oauth2-resource-server` を使った（Spring Boot 4.1.1 には同じ内容の `spring-boot-starter-security-oauth2-resource-server` もある）。

## 後の単位への申し送り

- **U3（401 の入口の処理。D2）**: U2 は `cherry.mastersmith.auth.web.TokenAuthenticationEntryPoint`（401 / `AUTHENTICATION_REQUIRED` を U1 の `ErrorResponseWriter` で書き、区分を DEBUG で出す）を置き、`AuthSecurityContributor`（order 110）で「Bearer の検証の失敗」と「ログインが必要な要求の未認証」の両方の入口に設定している。U3 は order 200 台の決まりで、同じ2か所を自分の入口の処理（出来事の通知つき）に置き換えること。U2 の入口の処理は U3 の置き換えの後は使われない。
- **U3（検証済みの利用者）**: `cherry.mastersmith.auth.domain.AuthenticatedUser`（`userId`・`email`・`admin`）が、`AuthenticatedUserToken` の主体として要求ごとに置かれる（`@AuthenticationPrincipal` で受け取れる）。`admin` は要求ごとに DB から読んだ値。
- **U3（失敗の区分）**: `TokenAuthenticationException`（`OAuth2AuthenticationException` の子、つまり `AuthenticationException` の子）と `TokenFailureReason`（`TOKEN_MALFORMED`・`TOKEN_INVALID`・`TOKEN_EXPIRED`・`USER_NOT_FOUND`）。トークンが無い要求は U2 の検証を通らず、Spring Security の「認証が足りない」の例外が入口の処理へ届く（U3 が `TOKEN_MISSING` とみなす）。
- **U3（画面）**: `frontend/src/shared/api-client/` の `apiFetch`・`apiRequest` を使う。トークンの付与、401 / `AUTHENTICATION_REQUIRED` での更新（同時の 401 は1回にまとめる）と1回の送り直し、`{ status, code }` の形のエラーへの変換を行う。認証の API は対象外。
- **U4（出来事）**: `cherry.mastersmith.auth.domain.AuthenticationEvent` を U2 のトランザクションの中で知らせる。受け取りは `@TransactionalEventListener(phase = AFTER_COMMIT)` で、確定の後に同じスレッドで行う。受け取り側で例外が起きても U2 の応答は変わらない（`AuthEventsIT` で確認済み）。出来事には秘密情報の項目が無い。
- **U3・U4（時計）**: `java.time.Clock` の Bean（UTC）を `cherry.mastersmith.auth.service.AuthClockConfig` が置いている。ほかの単位は別に定義せず、この Bean を使うこと。テストでは `MutableClock` を `@Primary` で差し替える（`AuthApiTestConfig` が例）。
- **U4（表）**: U2 の表（`users`・`login_attempt_states`・`refresh_tokens`）は U2 だけが読み書きする。ほかの単位は `user.service` の公開の操作を使う。
- **Performance Validation**: NFR1.1〜NFR1.4、NFR1.6 を実測する。合わないときは、コンテナの CPU の上限か bcrypt の cost（NFR2.1 の 100〜500 ミリ秒の範囲内）を見直す。
