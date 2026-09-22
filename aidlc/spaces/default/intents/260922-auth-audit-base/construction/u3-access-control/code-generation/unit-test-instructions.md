# Unit Test Instructions — U3 管理画面のアクセス制御（u3-access-control）

本書は、U3 のテストの枠組み、この単位に絞った実行のコマンド、テストの一覧、カバレッジの目標、モックとテストデータの扱いを定める。方法は Testing Contract の test-after（層ごとに実装してから、その層のテストを書いて実行する）、テストの量はワークフローの Test Strategy の Standard（部品ごとに 5〜8 件、主な境界に結合テスト）に、チームの進め方の必須の認可のテストを加えたものである。手順の番号は `code-generation-plan.md` の Step を指す。

U3 は内部DBに表を持たないため、データモデルと repository の段のテストは無い（計画の 4.3）。代わりに「U3 が DB への問い合わせを増やさないこと」（NFR1.2）を Step 6 の結合テストで確かめる。

## 1. テストの枠組みと設定

テストの枠組みは U1 が、認証のテストの補助は U2 が用意済みで、U3 は新しい道具を入れない（Step 2 でこの単位のコマンドが動くことを確かめる）。

| 対象 | 道具 | 設定の場所（U1 が用意済み） |
|---|---|---|
| バックエンドの単体テスト（`*Test`） | JUnit 5、AssertJ、Mockito、jqwik（性質ベース）、ArchUnit | `backend/build.gradle.kts` の `test` タスク（名前が `Test` で終わるクラスだけ） |
| バックエンドの結合テスト（`*IT`） | Spring Boot Test、組み込みの H2（ファイル保存、一時ディレクトリ）、U1 の `HttpTestClient` | `backend/build.gradle.kts` の `integrationTest` タスク（名前が `IT` で終わるクラスだけ） |
| バックエンドのカバレッジ | JaCoCo（`test` と `integrationTest` の実行記録を合わせる） | `:backend:jacocoTestReport`、`:backend:jacocoTestCoverageVerification`（行 80%・分岐 70%） |
| フロントエンドの単体・部品のテスト（`*.test.ts` / `*.test.tsx`、対象と同じ場所） | Vitest、Testing Library（jsdom）、user-event、vitest-axe、`@testing-library/jest-dom`、fast-check | `frontend/vitest.config.ts`、`frontend/vitest.setup.ts` |
| フロントエンドのカバレッジ | `@vitest/coverage-v8` | `frontend/vitest.config.ts` の `coverage.thresholds`（行 80・分岐 70） |
| ビルドした WAR での画面の確認 | Playwright（Chromium） | `frontend/playwright.config.ts`（U2 が署名鍵と初期管理者の値を渡す形にしてある）、Gradle の `e2eTest` タスク（Step 12 で対象を `e2e/` 全体にする。計画の D1） |

U2 が用意したテストの補助のうち、U3 が使うもの（`cherry.mastersmith.auth.testsupport`）:

| 補助 | 用途 |
|---|---|
| `TestSigningKeyEnvironmentPostProcessor`（`spring.factories`） | Spring を起動するすべてのテストで、実行のたびに作る署名鍵を入れる（U3 の結合テストでも自動で効く） |
| `AuthTestTokens` | 形の崩れたトークン・改ざん・`alg: none`・期限切れのトークンを作る（401 の理由ごとの出来事の確認） |
| `SqlStatementCounter` | 1要求で発行された SQL の種類と回数を数える（NFR1.2） |
| `MutableClock` | 時刻を進める（期限切れの 401 で出来事を作らないことの確認） |
| `AuthApi`・`AuthApiTestConfig` | ログインしてアクセストークンを得る |

U3 が Step 2 で足すテストの補助（テストのソースの中だけ。`cherry.mastersmith.access.testsupport`）:

| 補助 | 用途 |
|---|---|
| `PublicApiTestRules` | `mastersmith.test-fixture.public-api=true` のときだけ有効な決まり（order 250、`/api/**` を許可）。U1 の既存の結合テストが、アクセス制御ではなく本来の対象を確かめ続けられるようにする（計画の C7・D1） |
| `AdminAccessTestConfig`・`AdminTestUsers` | 管理者・管理者でない利用者を作り、ログインしてアクセストークンを得る。`MutableClock` を `@Primary` で差し替える |
| `CapturedAccessDeniedEvents` | アクセス拒否の出来事を集めるテスト用の受け取り（`@EventListener`）と、受け取りで例外を起こす役 |

- テストの説明文（`@DisplayName`、テストのメソッド名、`describe` / `it`）は英語で書く。テストデータは日本語でよい。
- Java のテストは `backend/src/test/java` の `cherry.mastersmith.access` の下に、対象と同じパッケージで置く。フロントエンドのテストは `frontend/src/features/admin/` の下に、対象と同じ場所に置く。E2E は `frontend/e2e/u3-admin-access.e2e.ts`。
- 常に通るだけのテストは書かない。各部品のテストは、正しく動く場合に加えて、誤りや境界の場合を少なくとも2件含める。

## 2. この単位のテストの実行

すべてリポジトリのルートで実行する。どのコマンドも U3 のテストだけに絞っている（Step 9 の回帰だけは全体を実行する）。Gradle の `--tests` は、その直前に書いたタスクにだけ効く。`--tests 'cherry.mastersmith.access.*'` は下のパッケージ（`access.domain` など）も含む。

### 2.1 前提の用意（初回と依存関係の更新のとき）

```bash
git submodule update --init
(cd vendor/make-you-chic-ui && npm ci && npm run build)
(cd frontend && npm ci)
```

### 2.2 実行の枠組みの確認（Step 2 で実行する。U3 のテストが無い段階でも動く）

```bash
./gradlew :backend:testClasses
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/admin --passWithNoTests)
```

1つ目はテストのソースのコンパイル（U3 のテストの補助を含む）、2つ目は Vitest の設定が U3 の場所で読み込めることを確かめる。どちらもテストの成否を判定するものではない。

### 2.3 手順ごとのコマンド

| 手順 | コマンド |
|---|---|
| Step 4 判定の決まり・出来事・問題の種類 | `./gradlew :backend:test --tests 'cherry.mastersmith.access.domain.*' --tests 'cherry.mastersmith.access.service.*'` |
| Step 6 判定・401／403・確認用 API | `./gradlew :backend:test --tests 'cherry.mastersmith.access.web.*' :backend:integrationTest --tests 'cherry.mastersmith.access.web.AdminAccessIT' --tests 'cherry.mastersmith.access.web.ApiDefaultAccessIT' --tests 'cherry.mastersmith.access.web.AccessDeniedEventsIT' --tests 'cherry.mastersmith.access.web.AccessSecretLeakIT' --tests 'cherry.mastersmith.access.web.AccessProblemTypesIT'` |
| Step 8 正規化されていないパスの拒否 | `./gradlew :backend:test --tests 'cherry.mastersmith.access.web.AccessRequestRejectedHandlerTest' :backend:integrationTest --tests 'cherry.mastersmith.access.web.AdminPathBoundaryIT'` |
| Step 9 全体の回帰（U1・U2 を含む） | `./gradlew :backend:test :backend:integrationTest` と `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run)` |
| Step 11 画面 | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/admin)` |
| Step 12 ビルドした WAR での画面の確認 | `./gradlew :backend:bootWar && (cd frontend && npx playwright install chromium && npx playwright test)`（U1・U2・U3 の E2E をまとめて実行する。`./gradlew e2eTest` も同じ対象になる） |

### 2.4 U3 のテストをまとめて実行する（カバレッジの報告を含む）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.access.*' \
  :backend:integrationTest --tests 'cherry.mastersmith.access.*' \
  :backend:jacocoTestReport
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/admin \
  --coverage --coverage.include='src/features/admin/**')
```

- バックエンドの報告は `backend/build/reports/jacoco/test/html/index.html`（パッケージごとの表で `cherry.mastersmith.access.*` を見る）。このコマンドは U3 のテストだけを実行するため、アプリ全体に対する `jacocoTestCoverageVerification` は含めない（U1・U2 のクラスが測られず、下限の判定が意味を持たないため）。
- フロントエンドは、計測の対象を U3 のファイルに絞るため、`vitest.config.ts` の `thresholds`（行 80・分岐 70）がそのまま U3 のファイルに効く。
- 全体の下限の判定（バックエンド・フロントエンドとも行 80%・分岐 70%）は、統合の前の関門 `./gradlew verify`（全単位の全検査）で行う（Step 12 と Build and Test）。

## 3. テストの一覧（部品ごと）

件数は目安（Standard: 部品ごとに 5〜8 件）。性質ベースのテストは1つのプロパティを1件と数える。

### 3.1 バックエンドの単体テスト（`*Test`）

| テスト | 対象 | 主な確認 | 件数 |
|---|---|---|---|
| `access.domain.AdminPathsTest` | 管理者のみのパスの照合 | jqwik: `/api/admin/` の後ろに何が続いても真、それ以外で始まるパスは偽。明示の例: `/api/admin`・`/api/admin/`・`/api/admin/check`・`/api/admin/nothing` は真、`/API/admin/check`（大文字）・`/api/administrators`・`/api/auth/login`・`/actuator/health` は偽 | 8 |
| `access.domain.AccessDeniedReasonTest` | 401 の理由の変換 | `TOKEN_EXPIRED` は出来事にしない（空を返す）、`TOKEN_MALFORMED`・`TOKEN_INVALID`・`USER_NOT_FOUND` はそれぞれに対応、例外が `TokenAuthenticationException` でなければ `TOKEN_MISSING`、U2 の `TokenFailureReason` の値を網羅する | 6 |
| `access.domain.AdminAccessDeniedEventTest` | アクセス拒否の出来事 | 項目がそろう（種類・日時・結果・理由・メールアドレス・接続元IP・User-Agent・要求のパス・トレースID）、メールアドレスが無い場合、User-Agent と要求のパスが 512 文字に切られている、要求のパスに問い合わせの部分が含まれない、トークンの項目を持たない、`toString` に秘密が出ない | 5 |
| `access.domain.AccessProblemTypesTest`・`access.service.AccessProblemTypeCatalogTest` | U3 の問題の種類 | `ACCESS_DENIED`（403）・`REQUEST_REJECTED`（400）の code と状態コード、日英がそろう、カタログが2つを返す、説明に内部の情報・パスが無い | 5 |
| `access.service.AccessDeniedEventPublisherTest` | 出来事の通知 | 知らせる、受け取り側の例外を WARN で受け止めて呼び出し元に伝えない、WARN に例外のメッセージを出さない、通知は1回だけ、例外を黙って捨てない | 5 |
| `access.web.AdminAuthorizationManagerTest` | 管理者の判定 | 管理者は許可、管理者でない利用者は拒否、未認証は拒否、主体が `AuthenticatedUser` でなければ拒否、判定に DB を使わない | 5 |
| `access.web.AdminAuthenticationEntryPointTest` | 401 の処理 | 管理者のみのパスで期限切れ以外は出来事、期限切れは出来事なし、管理者のみ以外のパスは出来事なし、例外が `TokenAuthenticationException` でなければ `TOKEN_MISSING`、応答の書き出しを U2 の入口の処理に任せる（計画の D2）、出来事の通知が失敗しても応答を書く | 7 |
| `access.web.AdminAccessDeniedHandlerTest` | 403 の処理 | 403 / `ACCESS_DENIED` を U1 の `ErrorResponseWriter` で書く、理由 `NOT_ADMIN` の出来事にメールアドレスを添える、WARN は `code` だけ（メールアドレス・パス・トークンを出さない）、通知の例外を受け止めて応答を変えない | 6 |
| `access.web.AccessRequestRejectedHandlerTest` | 拒否の処理 | 400 / `REQUEST_REJECTED` を U1 の書き手で書く、U1 の設定の値で4つのヘッダー（CSP・`X-Content-Type-Options`・`X-Frame-Options`・`Referrer-Policy`）を付ける、WARN に `code` とトレースIDだけを出し拒否したパスを出さない、出来事を作らない | 6 |

### 3.2 バックエンドの結合テスト（`*IT`、組み込みの H2）

| テスト | 主な確認 |
|---|---|
| `access.web.AdminAccessIT` | `/api/admin/check` が未ログインで 401 / `AUTHENTICATION_REQUIRED`、管理者でない利用者で 403 / `ACCESS_DENIED`、管理者で 204（必須の認可のテスト）。画面を介さない直接の呼び出しでも同じ。管理者でない利用者の `/api/admin/nothing` は 403・管理者は 404 / `NOT_FOUND`。管理者フラグを DB で外した直後の要求が 403。確認用 API の1要求の SQL が U2 の利用者の読み取り1回だけ（`SqlStatementCounter`）。応答が U1 の共通の形（`type`・`code`・`traceId`）で内部の情報を含まない |
| `access.web.ApiDefaultAccessIT` | 公開の一覧（`/actuator/health`・`/api/problems/**`・`/api/auth/login`・`/api/auth/session/**`）はログインなしで通る、一覧に無い `/api/**` はログインなしで 401、画面の配信（`/`）は 200、`/actuator/health` 以外の Actuator に外部から届かない |
| `access.web.AdminPathBoundaryIT` | `/api/admin`・`/api/admin/` は管理者でない利用者に 403。`/api/admin/..;/` とエンコードされた区切りを含むパスは 400 / `REQUEST_REJECTED`（共通の形・ヘッダー・traceId が付き、同じトレースIDが WARN のログにも出る。ログにパスが出ない）。`/API/admin/check` はログイン中 404・未ログイン 401 |
| `access.web.AccessDeniedEventsIT` | 403 で `NOT_ADMIN` の出来事（項目がそろう）。トークン無し・形の崩れ・改ざん・利用者が DB にいない、の 401 でそれぞれの理由の出来事。期限切れの 401（`MutableClock`）では出来事なし。管理者のみ以外のパスの 401 では出来事なし。受け取り側で例外を起こしても 401／403 の応答（状態コード・`code`・本文）が変わらない |
| `access.web.AccessSecretLeakIT` | `cherry.mastersmith.access` を TRACE にして 401・403・400 を起こし、アクセストークンの値・Authorization ヘッダー・メールアドレス・拒否したパスがアプリのログに出ない。出来事とエラー応答にも秘密情報が無い。すべての行が JSON でトレースIDが付く（`JsonLogRecords`） |
| `access.web.AccessProblemTypesIT` | U3 の2つの問題の種類が U1 の説明ページ（`/api/problems/...`）に日英で出る |

### 3.3 フロントエンドのテスト

| テスト | 対象 | 主な確認 | 件数 |
|---|---|---|---|
| `src/features/admin/adminApi.test.ts` | 確認用 API の呼び出し | パスとメソッド、204 の成功、403・5xx・通信の失敗の変換、U2 の ApiClient を通すこと | 5 |
| `src/features/admin/adminAreaStatus.test.ts` | 表示の状態を決める純粋な関数 | fast-check: 403 は必ず `NotFound`、それ以外の状態コードは `Error`、通信の失敗は `Error`。明示の例（204・403・500） | 5 |
| `src/features/admin/AdminAreaPage.test.tsx` | 管理者向け領域 | 確認中は中身を出さず確認中であることを伝える（`aria-busy`）、204 で見出しと説明、403 で「ページが見つかりません」、5xx と通信の失敗で一般的な文言、表示のたびに呼び直す、英語の表示で英語の文言、vitest-axe で違反なし（確認中の状態も） | 8 |
| `src/features/admin/AdminPlaceholder.test.tsx` | プレースホルダ | 見出しと説明、日英、vitest-axe | 3 |
| `src/features/admin/registration.test.ts` | 登録 | U1 の `validateRegistrations` を通る（`access=ADMIN`・`SHELL`、URL が重複しない）、サイドバーの項目が `visibleWhen=ADMIN` でホームの後に並ぶ、管理者でないログイン状態では項目が出ない、文言の鍵が日英でそろう | 5 |

### 3.4 ビルドした WAR での画面の確認（`frontend/e2e/u3-admin-access.e2e.ts`）

初期管理者でログイン → サイドバーに「管理」が出る → 選ぶと管理者向け領域（見出しと説明）が表示される → ログアウト → ログイン画面に戻る。これでチームの代表の流れ「ログイン → 管理画面に入れるか → ログアウト」が完成する。CSP の違反とスクリプトのエラーが無いこと（U2 と同じく認証の API の 401 の表示だけを除く）。U1 の `u1-skeleton.e2e.ts` と U2 の `u2-auth.e2e.ts` も同じ実行で通ることを確かめる。

## 4. カバレッジの目標

| 対象 | 目標 | 確かめ方 |
|---|---|---|
| バックエンドの U3 のパッケージ（`cherry.mastersmith.access`） | 行 80% 以上・分岐 70% 以上 | 2.4 の JaCoCo の報告のパッケージごとの値 |
| フロントエンドの U3 のファイル（`src/features/admin/`） | 行 80% 以上・分岐 70% 以上 | 2.4 のコマンド（`thresholds` で下回れば失敗） |
| アプリ全体 | 行 80% 以上・分岐 70% 以上（U1・U2 の値を下回らない） | `./gradlew verify` の 7 の段 |

- 計測から外すのは、U1 が決めた範囲（起動クラス・設定値だけの record（`*Properties`）・型の宣言・`src/main.tsx`）に限る。U3 は除外を増やさない。U3 は設定の型を持たない。
- 下限に届かないときは、下限を下げずにテストを足すか、差を依頼者に示す。

## 5. モックとスタブの扱い

- 単体テストでは、出来事の通知（`ApplicationEventPublisher`）・U1 の `ErrorResponseWriter`・U2 の `TokenAuthenticationEntryPoint`・`ClientInfoResolver` を Mockito で置き換え、`Clock` は `Clock.fixed` を使う。要求と応答は Spring の `MockHttpServletRequest` / `MockHttpServletResponse` を使う。
- 結合テストでは、フィルターの連鎖・判定・DB を実物で動かし、モックにしない（判定の抜け道を見落とさないため）。実際の番号で待ち受けるアプリに U1 の `HttpTestClient` で送り、画面を介さない呼び出しとして扱う。時刻は U2 の `MutableClock` を `@Primary` の Bean で差し替えて進める（`sleep` や実時間に頼らない）。
- 出来事の受け取り側（U4）は U3 のテストには無いため、`CapturedAccessDeniedEvents` で受け取りを確かめる。受け取り側の失敗は、例外を投げる受け取り（`@EventListener`）で再現する。
- フロントエンドでは `fetch` を `vi.fn` で置き換える（外部の道具は入れない）。U2 の ApiClient はモックにせず実物を通し、登録した手段（トークンの取得・更新）をテスト用の関数にする。
- U1 の既存の結合テストに足す `mastersmith.test-fixture.public-api=true` は、テストのソースの中の決まりだけを有効にするもので、本番の設定には無い。

## 6. テストデータの扱い

- 管理者・管理者でない利用者は、テストの中で `user.service.UserAccountService.createUser` により作る（メールアドレスは一意になる値、パスワードは明らかにテスト用と分かる 12 文字以上の値）。本物らしい秘密情報をソースに書かない。
- 結合テストはクラスごとに一時ディレクトリの H2 を使い、テストごとに必要な利用者とトークンを作る。実行の順番に依存させない。
- 署名鍵はテストの実行のたびに作られる（U2 の仕組み）。テストに `.env` などの秘密情報の設定は要らない。
- 細工されたパス（`/api/admin/..;/`・エンコードされた区切り）は、Gitleaks の誤検出を招かない形の文字列で書く（`.gitleaks.toml` の除外は増やさない）。
- jqwik の失敗時の乱数の種はテストの出力に出る（U1 の設定）。再現は `@Property(seed = "...")` で行う。fast-check は失敗時の `seed` と `path` を出力に残し、`fc.assert(..., { seed, path })` で再現する。

## 7. チームの必須のテストとの対応（U3 に関わるもの）

| team.md の必須のテスト | テスト |
|---|---|
| 認可: 未認証 401 | `AdminAccessIT`、`ApiDefaultAccessIT` |
| 認可: 管理者フラグなし 403 | `AdminAccessIT`、`AdminPathBoundaryIT` |
| 認可: 管理者は通る（204） | `AdminAccessIT`、`u3-admin-access.e2e.ts` |
| 認可: 画面で管理メニューを隠すことをサーバー側の検査の代わりにしない | `AdminAccessIT`（画面を介さない直接の呼び出し）、`registration.test.ts`（表示の切り替えだけであること） |
| トークン: 署名の改ざん・`alg: none`・期限切れの扱い（U3 の観点は出来事の要否） | `AccessDeniedEventsIT`（理由ごとの出来事、期限切れは作らない） |
| 監査ログ: 対象イベントごとに必須項目が記録されること（U3 は通知まで） | `AdminAccessDeniedEventTest`、`AccessDeniedEventsIT` |
| 秘密情報の漏えい: ログ・出来事・エラー応答にトークン・パスワードが無い | `AccessSecretLeakIT`、`AdminAccessDeniedEventTest`、`AccessRequestRejectedHandlerTest` |
| 構造化ログ・分散トレース: 決めた形式・トレースID | `AccessSecretLeakIT`（すべての行が JSON）、`AdminPathBoundaryIT`（応答と WARN の同じトレースID） |
| 画面: 部品ごとのアクセシビリティ検査 | `AdminAreaPage.test.tsx`、`AdminPlaceholder.test.tsx` |
| 画面からの一連の操作（E2E、代表の流れ） | `u3-admin-access.e2e.ts`（ログイン → 管理画面に入れるか → ログアウト） |
| ロック・ログイン・初期管理者に関する必須のテスト | U2 の単位で実施済み（U3 の対象外） |

## 8. 前提条件

- Java 25（Temurin）、Node.js 24、Git のサブモジュール（`vendor/make-you-chic-ui`）の取得。
- 内部DBのテストは組み込みの H2 を使い、コンテナの実行環境は要らない（team.md の学び）。E2E は Playwright の Chromium（`npx playwright install chromium`）と、ビルドした WAR（`./gradlew :backend:bootWar`）が要る。
- テストに秘密情報の設定（`.env` など）は要らない。署名鍵と初期管理者の値はテストの実行のたびに作る（U1・U2 の仕組み）。
- Step 9 の回帰は U1・U2 のテストをすべて含むため、実行に時間がかかる。手順の途中では 2.3 の絞り込んだコマンドを使う。
