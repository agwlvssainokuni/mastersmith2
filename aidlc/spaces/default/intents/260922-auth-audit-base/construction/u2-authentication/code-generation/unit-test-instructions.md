# Unit Test Instructions — U2 認証（u2-authentication）

本書は、U2 のテストの枠組み、この単位に絞った実行のコマンド、テストの一覧、カバレッジの目標、モックとテストデータの扱いを定める。方法は Testing Contract の test-after（層ごとに実装してから、その層のテストを書いて実行する）、テストの量はワークフローの Test Strategy の Standard（部品ごとに 5〜8 件、主な境界に結合テスト）に、チームの進め方の必須の認証のテストを加えたものである。手順の番号は `code-generation-plan.md` の Step を指す。

## 1. テストの枠組みと設定

テストの枠組みは U1 が用意済みで、U2 は新しい道具を入れない（Step 2 でこの単位のコマンドが動くことを確かめる）。

| 対象 | 道具 | 設定の場所（U1 が用意済み） |
|---|---|---|
| バックエンドの単体テスト（`*Test`） | JUnit 5、AssertJ、Mockito、jqwik（性質ベース）、ArchUnit | `backend/build.gradle.kts` の `test` タスク（名前が `Test` で終わるクラスだけ） |
| バックエンドの結合テスト（`*IT`） | Spring Boot Test、組み込みの H2（ファイル保存、一時ディレクトリ）、U1 の `HttpTestClient` | `backend/build.gradle.kts` の `integrationTest` タスク（名前が `IT` で終わるクラスだけ） |
| バックエンドのカバレッジ | JaCoCo（`test` と `integrationTest` の実行記録を合わせる） | `:backend:jacocoTestReport`、`:backend:jacocoTestCoverageVerification`（行 80%・分岐 70%） |
| フロントエンドの単体・部品のテスト（`*.test.ts` / `*.test.tsx`、対象と同じ場所） | Vitest、Testing Library（jsdom）、user-event、vitest-axe、`@testing-library/jest-dom`、fast-check | `frontend/vitest.config.ts`、`frontend/vitest.setup.ts` |
| フロントエンドのカバレッジ | `@vitest/coverage-v8` | `frontend/vitest.config.ts` の `coverage.thresholds`（行 80・分岐 70） |
| ビルドした WAR での画面の確認 | Playwright（Chromium） | `frontend/playwright.config.ts`（Step 18 で署名鍵と初期管理者の値を渡す。計画の D1）、Gradle の `e2eTest` タスク |

U2 が Step 2 で足すテストの補助（テストのソースの中だけ）:

| 補助 | 置き場所 | 用途 |
|---|---|---|
| `TestSigningKeyEnvironmentPostProcessor` と `backend/src/test/resources/META-INF/spring.factories` | `cherry.mastersmith.auth.testsupport` | Spring を起動するすべてのテスト（U1 の結合テストを含む）で、署名鍵が無ければ実行のたびに作る 32 バイトの乱数の鍵を入れる |
| `AuthTestTokens` | 同上 | テストの中で作った鍵で、正しいトークン・改ざん・`alg: none`・HS512・RS256・期限切れのトークンを作る |
| `SqlStatementCounter` | 同上 | Hibernate の `StatementInspector` で、スレッドごとに発行した SQL の種類と回数を数える（BR2.7） |
| `CountingPasswordEncoder` | 同上 | パスワードの照合の回数を数える包み（BR2.5） |
| `MutableClock` | 同上 | テストで時刻を進める `Clock`（`@Primary` の Bean として差し替える） |
| `CapturedAuthenticationEvents` | 同上 | 出来事を集めるテスト用の受け取り（`@TransactionalEventListener(phase = AFTER_COMMIT)`） |

- テストの説明文（`@DisplayName`、テストのメソッド名、`describe` / `it`）は英語で書く。テストデータは日本語でよい。
- Java のテストは `backend/src/test/java` の `cherry.mastersmith.auth` と `cherry.mastersmith.user` の下に、対象と同じパッケージで置く。フロントエンドのテストは `frontend/src/features/auth/` と `frontend/src/shared/api-client/` の下に、対象と同じ場所に置く。E2E は `frontend/e2e/u2-auth.e2e.ts`。
- 常に通るだけのテストは書かない。各部品のテストは、正しく動く場合に加えて、誤りや境界の場合を少なくとも2件含める。

## 2. この単位のテストの実行

すべてリポジトリのルートで実行する。どのコマンドも U2 のテストだけに絞っている。Gradle の `--tests` は、その直前に書いたタスクにだけ効く。`--tests 'cherry.mastersmith.auth.*'` は下のパッケージ（`auth.domain` など）も含む。

### 2.1 前提の用意（初回と依存関係の更新のとき）

```bash
git submodule update --init
(cd vendor/make-you-chic-ui && npm ci && npm run build)
(cd frontend && npm ci)
```

### 2.2 実行の枠組みの確認（Step 2 で実行する。U2 のテストが無い段階でも動く）

```bash
./gradlew :backend:testClasses
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/auth src/shared/api-client --passWithNoTests)
```

1つ目は依存関係（Step 1 で足した `spring-boot-starter-oauth2-resource-server` を含む）の解決とテストのソースのコンパイル、2つ目は Vitest の設定が U2 の場所で読み込めることを確かめる。どちらもテストの成否を判定するものではない。

### 2.3 手順ごとのコマンド

| 手順 | コマンド |
|---|---|
| Step 4 データモデル | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.repository.UserSchemaIT' --tests 'cherry.mastersmith.auth.repository.AuthSchemaIT'` |
| Step 6 repository | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.repository.*' --tests 'cherry.mastersmith.auth.repository.*'` |
| Step 8 ドメインの決まりと UserAccount | `./gradlew :backend:test --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.auth.domain.*' --tests 'cherry.mastersmith.auth.service.AuthProblemTypeCatalogTest' --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest' :backend:integrationTest --tests 'cherry.mastersmith.user.*'` |
| Step 10 Authentication の業務処理 | `./gradlew :backend:test --tests 'cherry.mastersmith.auth.service.*' :backend:integrationTest --tests 'cherry.mastersmith.auth.service.*'` |
| Step 12 API | `./gradlew :backend:test --tests 'cherry.mastersmith.auth.web.*' :backend:integrationTest --tests 'cherry.mastersmith.auth.web.*'` |
| Step 14 ApiClient と AuthSession | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/api-client src/features/auth/authSession.test.ts src/features/auth/authApi.test.ts src/features/auth/loginStateProvider.test.ts src/features/auth/validateLoginInput.test.ts)` |
| Step 16 ログイン画面と登録 | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/auth)` |
| Step 18 ビルドした WAR での画面の確認 | `./gradlew :backend:bootWar && (cd frontend && npx playwright install chromium && npx playwright test e2e/u2-auth.e2e.ts)` |

### 2.4 U2 のテストをまとめて実行する（カバレッジの報告を含む）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.user.*' \
  :backend:integrationTest --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.user.*' \
  :backend:jacocoTestReport
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/auth src/shared/api-client \
  --coverage --coverage.include='src/features/auth/**' --coverage.include='src/shared/api-client/**')
```

- バックエンドの報告は `backend/build/reports/jacoco/test/html/index.html`（パッケージごとの表で `cherry.mastersmith.auth.*` と `cherry.mastersmith.user.*` を見る）。このコマンドは U2 のテストだけを実行するため、アプリ全体に対する `jacocoTestCoverageVerification` は含めない（U1 のクラスが測られず、下限の判定が意味を持たないため）。
- フロントエンドは、計測の対象を U2 のファイルに絞るため、`vitest.config.ts` の `thresholds`（行 80・分岐 70）がそのまま U2 のファイルに効く。
- 全体の下限の判定（バックエンド・フロントエンドとも行 80%・分岐 70%）は、統合の前の関門 `./gradlew verify`（全単位の全検査）で行う（Step 18 と Build and Test）。

## 3. テストの一覧（部品ごと）

件数は目安（Standard: 部品ごとに 5〜8 件）。性質ベースのテストは1つのプロパティを1件と数える。

### 3.1 バックエンドの単体テスト（`*Test`）

| テスト | 対象 | 主な確認 | 件数 |
|---|---|---|---|
| `user.domain.EmailAddressTest` | メールアドレスの正規化と形式 | jqwik: 2回正規化しても同じ・前後の空白と大文字によらず同じ。空白だけ、形式の誤り | 5 |
| `user.domain.PasswordPolicyTest` | パスワードの規則 | 11 文字・12 文字、72 バイト・73 バイト、多バイト文字の境界。jqwik: 72 バイトの判定が UTF-8 のバイト数と一致 | 6 |
| `user.domain.PasswordTest` ほか `auth.domain.SecretTypesTest` | 秘密の値の型と、秘密を持つ要求・応答・設定の `toString` | 値が出ない（`Password`・`RefreshTokenValue`・`AccessTokenValue`・`LoginRequest`・`TokenResponse`・`AuthProperties`・`InitialAdminProperties`）、null の扱い | 7 |
| `auth.domain.LockPolicyTest` | ロックの判定 | jqwik: しきい値−1 回まではロックしない・しきい値ちょうどでロック・ロック中は一致しても拒否で状態不変・解除時刻以後は 0 から判定・成功で 0。明示の例（4回目・5回目、30 分ちょうど、解除後の誤りで 1） | 8 |
| `auth.domain.TokenExpiryTest` | 有効期限の判定 | jqwik: `now < expiresAt` と同値。4分59秒・5分ちょうど、23時間59分59秒・24時間ちょうど | 5 |
| `auth.domain.RefreshTokenValuesTest` | リフレッシュトークンの値の生成とハッシュ | 32 バイト相当の長さ、URL で使える文字、2回で異なる、ハッシュは 32 バイトで決定的 | 5 |
| `auth.domain.AuthProblemTypesTest`・`auth.service.AuthProblemTypeCatalogTest` | U2 の問題の種類 | 4つの code と状態コード、日英がそろう、カタログが4つを返す | 5 |
| `auth.domain.TokenAuthenticationExceptionTest` | U3 との約束の例外 | `AuthenticationException` の子、区分を返す、メッセージにトークンが無い、4つの区分 | 5 |
| `user.service.UserAccountServiceTest` | 照合と利用者の操作 | いる・一致／不一致、いない（ダミーで照合1回）、73 バイト（照合の仕組みに渡さない）、ハッシュを返さない、作成で `UserCreatedEvent`、`findById` | 8 |
| `user.service.InitialAdminInitializerTest` | 初期管理者の自動作成 | 無い・形式の誤り・11 文字・73 バイトで作らずに WARN（理由と直し方）、どのログにもパスワードが無い、既にいれば何もしない、作れば INFO | 7 |
| `user.service.DummyPasswordHashTest` | ダミーのハッシュと照合の時間のログ | 範囲内で INFO、範囲外で WARN、設定の cost で作る | 4 |
| `auth.AuthBoundaryArchitectureTest` | ADR-001・ADR-004 の境界 | `auth` → `user.domain`・`user.repository` の依存なし、`user` → `auth` なし、`auth`・`user` → `audit` なし、パスワードのハッシュを読むのは `user` の中だけ | 4 |
| `auth.service.SigningKeyProviderTest` | 署名鍵 | 32 バイトは使える、31 バイト・無い・Base64 でないで失敗、例外に値が無い | 5 |
| `auth.service.AccessTokenServiceTest` | アクセストークンの発行と検証 | 中身が `sub`・`iat`・`exp` だけ、4分59秒は有効・5分ちょうどは `TOKEN_EXPIRED`、署名の改ざん・別の鍵は `TOKEN_INVALID`、`alg: none`・HS512・RS256 は `TOKEN_INVALID`、形の崩れは `TOKEN_MALFORMED` | 8 |
| `auth.service.LoginServiceTest` | ログインの処理 | 成功（回数 0・トークン2つ・`LOGIN_SUCCEEDED`）、誤り（回数＋1・`PASSWORD_MISMATCH`）、しきい値でロックと INFO（`userId` だけ）、ロック中（`ACCOUNT_LOCKED`・値を変えない書き込み1回）、解除後、いない（ダミーの行・`USER_NOT_FOUND`）、出来事の項目、失敗の例外が理由によらず同じ | 8 |
| `auth.service.TokenRefreshServiceTest` | トークンの更新 | 成功で古い行の無効化と新しい行（今から 24 時間）、24 時間ちょうどで失敗、無効化の結果 0 で失敗、存在しない・使用済みで失敗しほかの行を触らない | 6 |
| `auth.service.LogoutServiceTest` | ログアウト | 有効なら無効化と `LOGGED_OUT`、無い・無効・期限切れで何もせず出来事なし、ほかの行を触らない | 5 |
| `auth.service.RefreshTokenCleanupJobTest` | 使い終わったトークンの削除 | 件数の INFO、失敗は ERROR で例外を外へ出さない、件数の上限ごとの繰り返し | 4 |
| `auth.web.ClientInfoTest`・`auth.web.OriginVerifierTest`・`auth.web.RefreshCookiesTest` | 要求の情報、Origin、Cookie | User-Agent を 512 文字で切る・トレースIDが無ければ無し、Origin の一致・不一致・無し・ポートの違い、Cookie の属性と Path、削除の Cookie が同じ名前・Path・`Max-Age=0` | 8 |
| `auth.web.TokenAuthenticationEntryPointTest` | 401 の入口の処理 | 401 / `AUTHENTICATION_REQUIRED` を `ErrorResponseWriter` で書く、区分を DEBUG（`TokenAuthenticationException` なら区分、そうでなければ `TOKEN_MISSING`）、トークンの値が無い | 4 |

### 3.2 バックエンドの結合テスト（`*IT`、組み込みの H2）

| テスト | 主な確認 |
|---|---|
| `user.repository.UserSchemaIT`、`auth.repository.AuthSchemaIT` | スキーマの変更の適用とエンティティの一致、一意の制約、検査の制約、ダミーの行 8 行、時刻が時点として保存される |
| `user.repository.UserRepositoryIT` | 小文字の値で見つかる、無ければ空、ID で探せる |
| `auth.repository.LoginAttemptStateRepositoryIT` | 排他つきの読み取りで同じ行は待つ（合図でそろえる）、待ちの上限 3 秒、別の行・ダミーの行どうしは待たない、値が同じでも明示の更新1回、行の作成は2回目で何もしない |
| `auth.repository.RefreshTokenRepositoryIT` | ハッシュで見つかる、条件付きの無効化が 1 → 0、同時の無効化で1つだけ 1、削除が対象だけ・件数の上限ごと |
| `user.service.InitialAdminIT` | 2回起動しても1人、bcrypt の形式で cost 12、設定が無くても起動、小文字で保存、ロックの状態の行ができる |
| `auth.service.LoginConcurrencyIT` | 同時 5 回の失敗で回数 5 とロック、同時 4 回ではロックしない、別の利用者は待たされない |
| `auth.service.RefreshConcurrencyIT` | 同じトークンの同時2回の更新で1つだけ成功 |
| `auth.service.SigningKeyStartupIT` | 鍵が無い・短いと起動が失敗し、出力に鍵の値が無い |
| `auth.service.AuthSettingsIT` | 設定を変えた起動が効く、範囲外の値で起動が失敗する |
| `auth.web.LoginApiIT` | 成功の応答と Cookie、空の入力で 400 / `VALIDATION_FAILED`（パスワードを返さない）、4通りの失敗で応答が同じ、4通りで SQL と照合の回数が同じ、しきい値の境界、ロック中は正しいパスワードでも拒否、30 分後に成功、成功で回数 0 |
| `auth.web.TokenApiIT` | 更新の成功と作り直し、使用済み・Cookie 無し・期限切れで 401 / `REFRESH_FAILED` と同じ名前・Path の削除、ほかのブラウザのトークンは有効、Origin の一致・不一致・無し（更新とログアウト） |
| `auth.web.LogoutApiIT` | 204 と Cookie の削除、ログアウト後のリフレッシュトークンの拒否、ログアウト後も期限内のアクセストークンは使える（BR4.6 を決定済みの仕様として明示）、Cookie 無しでも 204、ほかのブラウザは有効のまま |
| `auth.web.AccessTokenApiIT` | テスト用の保護された窓口で、無し・形の崩れ・改ざん・`alg: none`・期限切れ（5 分ちょうど）・利用者が DB にいないで 401 / `AUTHENTICATION_REQUIRED`、4分59秒は通る、管理者のフラグの変更がすぐ反映、DEBUG の区分、入口に届く例外が `TokenAuthenticationException` |
| `auth.web.AuthEventsIT` | 出来事の項目、存在しないメールアドレスの失敗も知らせる、無効な Cookie のログアウトでは知らせない、確定の後に例外を起こす受け取り側があっても応答が変わらない |
| `auth.web.AuthSecretLeakIT` | TRACE を有効にして、起動・ログイン・失敗・更新・ログアウトのログに、パスワード・パスワードのハッシュ・アクセストークン・リフレッシュトークン・署名鍵の値が無い。エラー応答と出来事にも無い |
| `auth.web.AuthProblemTypesIT` | U2 の4つの問題の種類が U1 の説明ページに日英で出る |

### 3.3 フロントエンドのテスト

| テスト | 対象 | 主な確認 | 件数 |
|---|---|---|---|
| `src/shared/api-client/apiClient.test.ts` | ApiClient | トークンの付与、認証の API の除外、401 で更新して1回送り直す、同時の 401 で更新1回（fast-check）、送り直しの 401・更新の失敗で `onUnauthenticated`、`AUTHENTICATION_REQUIRED` 以外の 401・403 は更新しない | 8 |
| `src/shared/api-client/apiError.test.ts` | エラーの変換 | Problem Details、そうでない本文、通信の失敗 | 4 |
| `src/features/auth/authApi.test.ts` | 認証の API の呼び出し | パスとメソッド、`credentials`、成功と失敗の変換 | 5 |
| `src/features/auth/authSession.test.ts` | AuthSession | 復元の成功・失敗、ログインでメモリに持ち localStorage・sessionStorage に無い、ログアウトは API の失敗でも破棄、更新の失敗で LoggedOut、状態の変化の知らせ | 8 |
| `src/features/auth/loginStateProvider.test.ts` | ログイン状態の提供元 | 復元を待って返す、未ログインで admin は false、表示名、`subscribe` の解除 | 5 |
| `src/features/auth/validateLoginInput.test.ts` | 空の入力の検査 | fast-check: 空白だけ・空は拒否、それ以外は受け付け、パスワードの長さは検査しない | 5 |
| `src/features/auth/LoginForm.test.tsx` | ログインのフォーム | 空の入力で送らない、成功で `/` へ、失敗で1種類の文言とパスワード欄が空（ロック中の応答も同じ）、通信の失敗の文言、送信中はボタンを押せない、英語の文言、vitest-axe | 8 |
| `src/features/auth/LoginPage.test.tsx` | ログイン画面 | ログイン用レイアウトの中のフォーム、vitest-axe | 3 |
| `src/features/auth/registration.test.ts` | 登録 | U1 の `validateRegistrations` を通る、文言の鍵が日英でそろう、ログアウトの項目で破棄 | 5 |

### 3.4 ビルドした WAR での画面の確認（`frontend/e2e/u2-auth.e2e.ts`）

初期管理者でログイン → ホーム → 再読み込みしてもログインしたまま → ユーザーメニューのログアウト → ログイン画面 → 再読み込みしても未ログイン。誤ったパスワードで1種類の文言。CSP の違反とスクリプトのエラーが無い（起動時の更新の 401 を除く）。U1 の `u1-skeleton.e2e.ts` も同じ実行で通ることを確かめる。

## 4. カバレッジの目標

| 対象 | 目標 | 確かめ方 |
|---|---|---|
| バックエンドの U2 のパッケージ（`cherry.mastersmith.auth`・`cherry.mastersmith.user`） | 行 80% 以上・分岐 70% 以上 | 2.4 の JaCoCo の報告のパッケージごとの値 |
| フロントエンドの U2 のファイル（`src/features/auth/`・`src/shared/api-client/`） | 行 80% 以上・分岐 70% 以上 | 2.4 のコマンド（`thresholds` で下回れば失敗） |
| アプリ全体 | 行 80% 以上・分岐 70% 以上（U1 の値を下回らない） | `./gradlew verify` の 7 の段 |

- 計測から外すのは、U1 が決めた起動クラスと設定値だけのクラス（`*Properties`）、型の宣言、入口の `src/main.tsx` に限る。U2 は除外を増やさない。設定の `record` に検証の処理を書かない（`SigningKeyProvider` などの別の型に置き、測る）。
- 下限に届かないときは、下限を下げずにテストを足すか、差を依頼者に示す。

## 5. モックとスタブの扱い

- 単体テストでは、repository・`PasswordEncoder`・`UserAccountService`・`ApplicationEventPublisher` を Mockito で置き換え、`Clock` は `Clock.fixed` を使う。bcrypt の実物は `UserAccountServiceTest` 以外では使わない（遅いため。使う場合は cost 4 の設定で）。
- 結合テストでは、DB は実物の組み込みの H2（`TestDatabase` で一時ディレクトリ）を使い、モックにしない。時刻は `MutableClock` を `@Primary` の Bean で差し替えて進める（`sleep` や実時間に頼らない）。照合の回数は `CountingPasswordEncoder`、SQL の回数は `SqlStatementCounter` で数える。
- 同時の処理のテストは、`CountDownLatch` などの合図でスレッドの開始をそろえ、時間の待ちに頼らない。結果が揺れる場合は原因を直すまで統合しない。
- 出来事の受け取り側（U4）は U2 のテストには無いため、`CapturedAuthenticationEvents` で受け取りを確かめる。受け取り側の失敗は、テスト用の `@TransactionalEventListener(phase = AFTER_COMMIT)` で例外を起こして再現する。
- フロントエンドでは `fetch` を `vi.fn` で置き換える（外部の道具は入れない）。`localStorage`・`sessionStorage` は jsdom の実物を使い、テストの後に空にする。AuthSession はモジュールの状態を持つため、テストごとに作り直す手段（テスト用の初期化の関数）を使う。

## 6. テストデータの扱い

- 署名鍵・パスワード・メールアドレスは、テストの中で作る（`TestDatabase.randomSecret()`、`SecureRandom`）か、明らかにテスト用と分かる値にする。本物らしい秘密情報をソースに書かない。Gitleaks が誤って検出した場合は、値の形を変えて避ける（`.gitleaks.toml` の除外は増やさない）。
- 結合テストはクラスごとに一時ディレクトリの H2 を使い、テストごとに必要な利用者・トークンを作る。実行の順番に依存させない（表の中身をテストの前に消す、または一意なメールアドレスを使う）。
- 日本語を含むメールアドレス・パスワード（多バイト文字の 72 バイトの境界）をテストデータに使う。
- jqwik の失敗時の乱数の種はテストの出力に出る（U1 の設定）。再現は `@Property(seed = "...")` で行う。fast-check は失敗時の `seed` と `path` を出力に残し、`fc.assert(..., { seed, path })` で再現する。

## 7. チームの必須のテストとの対応（U2 に関わるもの）

| team.md の必須のテスト | テスト |
|---|---|
| ロック: しきい値−1 回ではロックされない／しきい値ちょうどでロック | `LockPolicyTest`、`LoginServiceTest`、`LoginApiIT`、`LoginConcurrencyIT` |
| ロック: ロック中は正しいパスワードでも拒否 | `LockPolicyTest`、`LoginServiceTest`、`LoginApiIT` |
| ロック: ログイン成功時の失敗回数の扱い（0 に戻す） | `LockPolicyTest`、`LoginServiceTest`、`LoginApiIT` |
| ログイン: 存在しないユーザーとパスワード誤りで推測できない | `LoginApiIT`（応答・SQL の回数・照合の回数が同じ）、`UserAccountServiceTest` |
| トークン: 有効期限の境界（直前は有効／直後は無効） | `TokenExpiryTest`、`AccessTokenServiceTest`、`AccessTokenApiIT`、`TokenRefreshServiceTest`、`TokenApiIT` |
| トークン: 署名の改ざん、`alg: none` 等 | `AccessTokenServiceTest`、`AccessTokenApiIT` |
| トークン: ログアウト後のリフレッシュトークンの拒否 | `LogoutApiIT` |
| トークン: ログアウト後もアクセストークンが期限まで使える（決定済みの仕様） | `LogoutApiIT` |
| 初期管理者: 2回目以降の起動で重複しない | `InitialAdminIT` |
| 初期管理者: 設定が無い／不正なときの動作 | `InitialAdminInitializerTest`、`InitialAdminIT` |
| 初期管理者: パスワードがログに出ない | `InitialAdminInitializerTest`、`AuthSecretLeakIT` |
| 秘密情報の漏えい: ログ・出来事・エラー応答にパスワード・トークンが無い | `SecretTypesTest`、`AuthSecretLeakIT`、`LoginApiIT` |
| 構造化ログ: 決めた形式・トレースID | `AuthSecretLeakIT`（`JsonLogRecords.assertAllLinesAreJson`）、`AuthEventsIT`（出来事のトレースID） |
| 画面: ログイン画面のアクセシビリティ | `LoginForm.test.tsx`、`LoginPage.test.tsx` |
| 画面: ロック時のメッセージ表示 | `LoginForm.test.tsx`（ロック中の応答で同じ1種類の文言）、`u2-auth.e2e.ts` |
| 画面: ログアウトでトークンが破棄される | `authSession.test.ts`、`registration.test.ts`、`u2-auth.e2e.ts` |
| 認可（401・403・200）、監査ログの記録 | U3・U4 の単位で書く。U2 は 401 の前提（`AccessTokenApiIT`）と出来事の項目（`AuthEventsIT`）を受け持つ |

## 8. 前提条件

- Java 25（Temurin）、Node.js 24、Git のサブモジュール（`vendor/make-you-chic-ui`）の取得。
- 内部DBのテストは組み込みの H2 を使い、コンテナの実行環境は要らない（team.md の学び）。E2E は Playwright の Chromium（`npx playwright install chromium`）と、ビルドした WAR（`./gradlew :backend:bootWar`）が要る。
- テストに秘密情報の設定（`.env` など）は要らない。署名鍵と初期管理者の値はテストの実行のたびに作る。
