# Unit Test Instructions — U1 アプリの骨格（u1-app-skeleton）

本書は、U1 のテストの枠組み、この単位に絞った実行のコマンド、テストの一覧、カバレッジの目標、モックとテストデータの扱いを定める。方法は Testing Contract の test-after（層ごとに実装してから、その層のテストを書いて実行する）、テストの量はワークフローの Test Strategy の Standard（部品ごとに 5〜8 件、主な境界に結合テスト）である。手順の番号は `code-generation-plan.md` の Step を指す。

## 1. テストの枠組みと設定

| 対象 | 道具 | 設定の場所（Step 4 で作る） |
|---|---|---|
| バックエンドの単体テスト（`*Test`） | JUnit 5、AssertJ、Mockito、jqwik（性質ベース）、ArchUnit | `backend/build.gradle.kts` の `test` タスク（名前が `Test` で終わるクラスだけ） |
| バックエンドの結合テスト（`*IT`） | Spring Boot Test、MockMvc、組み込みの H2（ファイル保存、一時ディレクトリ） | `backend/build.gradle.kts` の `integrationTest` タスク（名前が `IT` で終わるクラスだけ。単体テストと同じソースの組 `backend/src/test/java`） |
| バックエンドのカバレッジ | JaCoCo（`test` と `integrationTest` の実行記録を合わせる） | `:backend:jacocoTestReport`、`:backend:jacocoTestCoverageVerification`（行 80%・分岐 70%） |
| フロントエンドの単体・部品のテスト（`*.test.ts` / `*.test.tsx`、対象と同じ場所） | Vitest、Testing Library（jsdom）、user-event、vitest-axe、`@testing-library/jest-dom`、fast-check | `frontend/vitest.config.ts`、`frontend/vitest.setup.ts` |
| フロントエンドのカバレッジ | `@vitest/coverage-v8` | `frontend/vitest.config.ts` の `coverage.thresholds`（行 80・分岐 70） |
| ビルドした WAR での画面の確認（CSP） | Playwright | `frontend/playwright.config.ts`、`frontend/e2e/`（Vitest の対象外）。計画の P2 の決定（A）により別の Gradle タスク `e2eTest` で実行し、`./gradlew verify` と CI には入れない |

- テストの説明文（`@DisplayName`、テストのメソッド名、`describe` / `it`）は英語で書く。テストデータは日本語でよい。
- Java のテストは `backend/src/test/java` に、対象と同じパッケージで置く。U1 のテストは `cherry.mastersmith.common` と `cherry.mastersmith.config` の下と `cherry.mastersmith.ArchitectureTest` にだけ置く（下のコマンドの絞り込みの範囲）。後の単位は自分の機能のパッケージにテストを置く。
- フロントエンドの U1 のテストは `frontend/src/app/` の下にだけ置く。後の単位は `frontend/src/features/<featureId>/` に置く。
- 常に通るだけのテスト（`assertTrue(true)` など）は書かない。各部品のテストは、正しく動く場合に加えて、誤りや境界の場合を少なくとも2件含める。

## 2. この単位のテストの実行

すべてリポジトリのルートで実行する。どのコマンドも U1 のテストだけに絞っている。

### 2.1 前提の用意（初回と依存関係の更新のとき）

```bash
git submodule update --init
(cd vendor/make-you-chic-ui && npm ci && npm run build)
(cd frontend && npm ci)
```

make-you-chic-ui のビルドで作られるのは、サブモジュールの `.gitignore` が無視する `node_modules/` と `dist/` だけである。追跡されるファイルは変えない。

### 2.2 実行の枠組みの確認（Step 4 の直後に実行できるコマンド）

```bash
./gradlew :backend:testClasses
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app --passWithNoTests)
```

1つ目はテストのソースのコンパイルと依存の解決、2つ目は Vitest の設定（jsdom、`setupFiles`、`resolve.dedupe`）の読み込みを確かめる。どちらもテストの成否を判定するものではない。

### 2.3 手順ごとのコマンド

| 手順 | コマンド |
|---|---|
| Step 6 DB の振る舞い | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.common.db.*'` |
| Step 8 業務処理 | `./gradlew :backend:test --tests 'cherry.mastersmith.common.error.domain.*' --tests 'cherry.mastersmith.common.error.service.*' --tests 'cherry.mastersmith.common.i18n.*' --tests 'cherry.mastersmith.common.health.*' --tests 'cherry.mastersmith.common.observability.*' --tests 'cherry.mastersmith.common.security.*' :backend:integrationTest --tests 'cherry.mastersmith.common.error.service.*'` |
| Step 10 エラー応答と説明ページ | `./gradlew :backend:test --tests 'cherry.mastersmith.common.error.*' :backend:integrationTest --tests 'cherry.mastersmith.common.error.*'` |
| Step 12 フィルターの連鎖・配信・Actuator | `./gradlew :backend:test --tests 'cherry.mastersmith.common.web.*' :backend:integrationTest --tests 'cherry.mastersmith.common.health.*' --tests 'cherry.mastersmith.config.*'` |
| Step 14 ログ・トレース・外部エクスポート・層の構造 | `./gradlew :backend:test --tests 'cherry.mastersmith.common.observability.*' --tests 'cherry.mastersmith.ArchitectureTest' :backend:integrationTest --tests 'cherry.mastersmith.common.observability.*'` |
| Step 16 画面の判断の関数と文言 | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/i18n src/app/registry src/app/routing src/app/navigation)` |
| Step 18 画面の部品 | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app)` |
| Step 18 ビルドした WAR での画面の確認（P2 の決定: 別のタスク `e2eTest`） | `./gradlew :backend:bootWar && (cd frontend && npx playwright install chromium && npx playwright test e2e/u1-skeleton.e2e.ts)` |

Gradle の `--tests` は、その直前に書いたタスクにだけ効く。

### 2.4 U1 のテストをまとめて実行する（カバレッジの報告と下限の検証を含む）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.common.*' --tests 'cherry.mastersmith.config.*' --tests 'cherry.mastersmith.ArchitectureTest' \
  :backend:integrationTest --tests 'cherry.mastersmith.common.*' --tests 'cherry.mastersmith.config.*' \
  :backend:jacocoTestReport :backend:jacocoTestCoverageVerification
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app --coverage)
```

報告は `backend/build/reports/jacoco/` と `frontend/coverage/` に出る。統合の前の関門としては、これとは別に `./gradlew verify`（全単位の全検査）を実行する（Build and Test と統合の前）。

## 3. テストの一覧（部品ごと）

クラス名・ファイル名は計画上の名前であり、上の絞り込みの範囲の中で生成時に整えてよい。件数は目安（Standard: 部品ごとに 5〜8 件）。

### 3.1 バックエンドの単体テスト（`*Test`）

| テスト | 対象 | 主な確かめる内容 | 件数 |
|---|---|---|---|
| `cherry.mastersmith.common.error.domain.ProblemTypeTest` | `ProblemType`、`LocalizedText` | code の形（正しい・小文字・先頭が数字・空）、slug の導出（jqwik の性質: 任意の正しい code から導いた slug は小文字とハイフンだけで、code に戻せる）、状態コードの範囲の外（399・600）、日英のどちらかが欠けたら作れない | 6〜8 |
| `cherry.mastersmith.common.error.service.ProblemTypeRegistryTest` | `ProblemTypeRegistry` | 複数の定義の一覧を集める、code の重複・slug の重複で失敗し重複した値を示す、code・slug で引ける、無い slug は「無し」、U1 の定義（`VALIDATION_FAILED`・`NOT_FOUND`・`PAYLOAD_TOO_LARGE`・`INTERNAL_ERROR`、提案 P1 が採られればその4つも）がすべて日英の title・description を持つ（BR5.14） | 6〜8 |
| `cherry.mastersmith.common.i18n.domain.AcceptLanguageResolverTest` | `AcceptLanguageResolver` | `en-US` で en、`ja-JP,en;q=0.8` で ja、q 値の順（`ja;q=0.5,en;q=0.9` で en）、q=0 は除く、同じ q は書かれた順、`fr` だけ・無い・形が正しくないで ja、jqwik の性質: 任意の文字列でも例外にならず ja・en のどちらかを返す | 7〜8 |
| `cherry.mastersmith.common.health.TimeBoundedDbHealthIndicatorTest` | `TimeBoundedDbHealthIndicator` | 問い合わせの成功で UP、例外で DOWN、制限時間を超えたら DOWN（ラッチで止めた偽の接続、短い制限時間。`sleep` を使わない）、前の確認が終わっていなければ新しく始めずに DOWN、応答に内訳が無い、接続を借りる待ちも制限時間に含まれる | 6 |
| `cherry.mastersmith.common.observability.TraceIdProviderTest` | `TraceIdProvider` | 現在のスパンがあればそのトレースID、スパンが無ければ「無し」、取得で例外にならない | 3〜5 |
| `cherry.mastersmith.common.observability.SanitizingSpanExporterTest` | `SanitizingSpanExporter` | 例外の記録からメッセージとスタックトレースが除かれる、例外の型の名前は残る、例外の無いスパンは変わらない、送信の結果を包まれた側からそのまま返す、終了の処理を包まれた側へ渡す | 5 |
| `cherry.mastersmith.common.observability.UrlQueryStrippingObservationFilterTest` | 観測のフィルター | URL の属性から `?` 以降が除かれる、問い合わせの無い URL は変わらない、属性が無くても失敗しない、ほかの属性は変わらない | 4〜5 |
| `cherry.mastersmith.common.observability.JsonLogFormatTest` | `logback-spring.xml` の出力の形 | 1件が1行の JSON、必須の項目の名前、`timestamp` がタイムゾーン付き、`traceId`・`spanId` は MDC にあるときだけ、MDC のほかの値は出ない、キーと値が JSON の値として入る、改行を含む値が1行に収まる（NFR3.11）、例外があれば `exception` | 7〜8 |
| `cherry.mastersmith.common.security.SecurityExtensionValidatorTest` | `SecurityExtensionValidator` | order の小さい順に並べる、order の重複で失敗し重複した値を示す、`ApiDefaultAccess` が0個・1個は通る、2個以上で失敗する、差し込みが0個でも通る | 5〜6 |
| `cherry.mastersmith.common.error.web.ProblemBaseUrlResolverTest` | `ProblemBaseUrlResolver` | 設定のベースURLが優先、要求のスキーム・Host・ポートから組み立て、既定の番号（80・443）は URL に付けない、末尾の `/` の扱い、転送元のヘッダーは既定で無視 | 5〜6 |
| `cherry.mastersmith.common.error.web.ErrorResponseFactoryTest` | `ErrorResponseFactory` | `type`・`title`・`status`・`code`・`traceId`・`instance` が入る、`detail` に例外のメッセージが入らない、表示言語で title が変わる、`Content-Type` が `application/problem+json` | 5〜6 |
| `cherry.mastersmith.common.error.web.GlobalExceptionHandlerTest` | `GlobalExceptionHandler`（Spring を起動しない MockMvc） | 入力の検証の失敗 → 400 `VALIDATION_FAILED`、`BusinessException` → その種類、想定外の例外 → 500 `INTERNAL_ERROR` で応答に例外のメッセージが無い、4xx は WARN でスタックトレースなし、5xx は ERROR でスタックトレース付き、ログは1回だけ、提案 P1 の各例外 | 7〜8 |
| `cherry.mastersmith.common.error.web.DefaultErrorResponseWriterTest` | `DefaultErrorResponseWriter` | 状態コードと `application/problem+json`、本文の項目が変換の1か所と同じ形、`traceId` が入る、秘密情報や例外のメッセージが入らない、文字コードが UTF-8 | 5 |
| `cherry.mastersmith.common.error.web.ProblemTypeHtmlRendererTest` | `ProblemTypeHtmlRenderer` | `<`・`>`・`&`・`"`・`'` を含む定義がエスケープされる（BR5.15）、`lang` が表示言語、要求の値を受け取る口が無い、埋め込みのスタイル・スクリプトが無い、resolution が無い定義も表示できる | 5〜6 |
| `cherry.mastersmith.common.web.RequestSizeLimitFilterTest` | `RequestSizeLimitFilter` | `Content-Length` が上限ちょうどは通す、上限＋1バイトは本文を読まずに 413、`Content-Length` 無しで上限ちょうどは通す、上限＋1バイトで読むのをやめて 413、本文の無い要求は通す、413 の本文が `PAYLOAD_TOO_LARGE` の ErrorResponse | 6 |
| `cherry.mastersmith.common.web.CacheControlFilterTest` | `CacheControlFilter` | `/api/**` と `/actuator/**` は `no-store`、`/assets/**` は `public, max-age=31536000, immutable`、`/` と画面の URL は `no-cache`、`/actuator/health` も `no-store` | 5 |
| `cherry.mastersmith.ArchitectureTest` | 層の構造（ArchUnit） | `web` が `repository` を直接使わない、`@Transactional` は `service` だけ、`@Entity` を API の応答として返さない、フィールド注入・セッター注入なし、`lombok` に依存しない、空の対象を許す | 5〜6 |

### 3.2 バックエンドの結合テスト（`*IT`、組み込みの H2）

| テスト | 主な確かめる内容 | 件数 |
|---|---|---|
| `cherry.mastersmith.common.db.DatabasePersistenceIT` | 既定がファイル保存、書き込んだデータがコンテキストの作り直しの後も残る（FR1.2 の受け入れ基準1）、接続設定だけで別の H2 のファイルに切り替わる（受け入れ基準2）、Flyway の基準線が適用される、Hibernate の検証で起動する、設定した内部DBのパスワードがログに出ない（BR2.3） | 6 |
| `cherry.mastersmith.common.health.HealthEndpointIT`（ヘルスチェックの起動確認テスト） | 未ログインで `/actuator/health` が 200 で `{"status":"UP"}` だけ、内訳が無い、起動後に内部DBが応答しない状態（ヘルスの確認が使う接続を止まる偽のものに差し替え）で制限時間内に 503・DOWN、そのときも画面の配信と説明ページが応答する（NFR10.10）、`no-store` が付く | 5 |
| `cherry.mastersmith.common.error.web.ErrorResponseIT` | 存在しない `/api/...` が 404 `NOT_FOUND`、入力の検証の失敗が 400 `VALIDATION_FAILED`、想定外の例外が 500 `INTERNAL_ERROR` で応答に例外のメッセージとスタックトレースが無い、`BusinessException` がその種類になる、`type` の URL が要求の Host から組み立てられる、`X-Forwarded-Host` が既定で無視される、設定のベースURLが優先される、`traceId` が載る、提案 P1 の決定どおりの 405・406・415・壊れた本文の扱い | 8 |
| `cherry.mastersmith.common.error.web.ProblemTypePageIT` | 未ログインで見られる、Accept が HTML を優先すれば HTML、それ以外は JSON、Accept-Language が en で英語・無しや fr で日本語、未定義の slug は 404 `NOT_FOUND`（type は not-found を指す）、応答に要求の値（slug を含む）が埋め込まれない、CSP が付く | 7 |
| `cherry.mastersmith.common.error.service.ProblemTypeDuplicateStartupIT` | code が重複する定義の Bean を置くと起動が失敗する、slug が重複しても失敗する、重複した値がエラーに示される | 3 |
| `cherry.mastersmith.config.SecurityHeadersIT` | API・画面・説明ページ・health・413 の応答のそれぞれに CSP・`X-Content-Type-Options`・`X-Frame-Options`・`Referrer-Policy` が付く、`Strict-Transport-Security` が無い、キャッシュの指定が表のとおり、セッションの Cookie を作らない | 6〜7 |
| `cherry.mastersmith.config.ExposureIT` | `/actuator/env`・`/actuator/configprops`・`/actuator/heapdump`・`/actuator/metrics` に届かない、`/h2-console` が H2 のコンソールでなく `index.html` が返る、画面の URL の直接の表示で `index.html`、存在しない `/api/...` は `index.html` でなく 404 の ErrorResponse、本文の上限超えが 413 `PAYLOAD_TOO_LARGE`（`Content-Length` あり・なし） | 6〜7 |
| `cherry.mastersmith.config.SecurityExtensionIT` | U1 だけの状態で `/api/**` が許可、テスト用の `SecurityRuleContributor`（order 100・200）が order の順に当たる、`ApiDefaultAccess`（true）で `/api/**` がログイン必須になる、それでも `/actuator/health` と `/api/problems/**` は未ログインで見られる、order の重複で起動が失敗する、`ApiDefaultAccess` の2つ以上で起動が失敗する | 6 |
| `cherry.mastersmith.common.observability.TracingAndLoggingIT` | 形の正しい `traceparent` でログとエラー応答のトレースIDがヘッダーと一致（FR10.3 の受け入れ基準1）、無い要求でも新しいトレースIDがログとエラー応答で一致（受け入れ基準2）、形の正しくない `traceparent`（桁数違い・16進数でない・すべて0）で拒否されず新しいトレース、1要求の間のすべてのログが同じトレースID（FR10.2）、次の要求に持ち越さない、サンプリング率 0 でもトレースIDが入る、ログが1行1件の JSON、パスワード・トークンの値がログに出ない | 8 |
| `cherry.mastersmith.common.observability.ExternalExportIT` | 既定の設定で OTLP の送信の仕組みが作られず外部へ何も送らない（FR10.4 の受け入れ基準）、有効にして届かない送り先を指定しても要求が通常どおり応答する（NFR10.11）、有効のときは送信の仕組みが作られる | 3〜4 |
| `cherry.mastersmith.common.observability.TraceAspectIT` | 既定（INFO）で追跡のログが出ない、TRACE を有効にするとテスト用の service の Bean の入る・出る・例外が出る、ロガーが対象のクラスの名前、設定クラス・フィルターが対象にならず、フィルターを通る要求が正しく動く | 5 |

### 3.3 フロントエンドのテスト（`frontend/src/app/` の下）

| テスト | 主な確かめる内容 | 件数 |
|---|---|---|
| `i18n/resolveLanguage.test.ts` | `['en-US']` で en、`['ja-JP']` で ja、`['fr', 'en']` で en、`['fr']`・空で ja、大文字の混じり、fast-check の性質: 任意の言語の並びで ja・en のどちらかを返し、最初に当たったものを選ぶ | 6〜7 |
| `i18n/messages.test.ts` | ja と en の鍵の集まりが同じ、空の文言が無い、U1 の必要な鍵（アプリ名・見出し・「ページが見つかりません」・「ホームへ」・「ホーム」）がある、（確認事項 C2 が採られれば）機能の文言の鍵の衝突で失敗する | 4〜5 |
| `registry/loadRegistrations.test.ts` | glob の結果を登録の一覧に変える、決まった名前のエクスポートが無いファイルを示して失敗する、0件でも空の一覧、読み込んだ順を保つ | 4〜5 |
| `registry/validateRegistrations.test.ts` | URL の重複・項目の id の重複・featureId の重複・ログイン状態の提供元の2つ以上・LOGIN の画面の2つ以上で、どの登録かを示して失敗、LOGIN の画面が STANDALONE・PUBLIC でないと失敗、サイドバーの項目が登録の無い path を指すと失敗、fast-check の性質: 重複を1つ含む登録の組は必ず拒否される | 7〜8 |
| `routing/decideRoute.test.ts` | PUBLIC はそのまま、LOGGED_IN で未ログインはログイン画面へ（LOGIN の登録があればその path、無ければログイン用レイアウトだけ）、ADMIN でログイン中の非管理者は「ページが見つかりません」、管理者は表示、登録の無い URL はログイン中なら「ページが見つかりません」・未ログインならログイン画面へ、`/` はホーム | 7〜8 |
| `navigation/navigationItems.test.ts` | 先頭が「ホーム」、visibleWhen=ADMIN は管理者だけ、order の順、ユーザーメニューが order の順、未ログインでは項目を出さない | 5 |
| `i18n/I18nProvider.test.tsx` | 英語のブラウザ設定で英語の文言（FR2.3 の受け入れ基準1）、日本語でも英語でもない設定で日本語（受け入れ基準2）、`document.documentElement.lang` が合う、文言の鍵から文言を引ける、アクセシビリティ検査 | 5 |
| `login-state/LoginStateGate.test.tsx` | 提供元が無ければ未ログイン（BR7.3）、提供元の値（loggedIn・admin・displayName）を子に渡す、loggedIn=false なら admin は常に false、提供元の問い合わせが失敗したら未ログインとして扱う、アクセシビリティ検査 | 5 |
| `routing/AppRouter.test.tsx` | U1 だけの状態で `/` を開くとログイン用レイアウトだけ、未ログインで LOGGED_IN・ADMIN の画面を開くとログイン画面へ、ログイン中の非管理者に ADMIN の画面を出さない、SHELL の画面は AppShell の中・STANDALONE の画面は外、登録の無い URL の扱い、アクセシビリティ検査 | 7〜8 |
| `layout/ShellLayout.test.tsx` | サイドバーの先頭が「ホーム」、条件を満たす項目だけを order の順、項目を選ぶと画面が移る（user-event）、ユーザーメニューの項目の操作が呼ばれる、表示名、アクセシビリティ検査 | 6 |
| `layout/StandaloneLayout.test.tsx` | 子を AppShell の外に表示、サイドバー・トップバーが無い、アクセシビリティ検査 | 3〜4 |
| `layout/LoginLayout.test.tsx` | アプリ名と表示言語に応じた見出し、子（U2 の入力欄）を置く場所、子が無くても表示できる、入力欄とボタンを持たない、英語の表示、アクセシビリティ検査 | 5〜6 |
| `pages/HomePage.test.tsx` | 見出しと説明、日英の切り替え、見出しの順序、アクセシビリティ検査 | 4〜5 |
| `pages/NotFoundPage.test.tsx` | 「ページが見つかりません」、「ホームへ」のリンク（`data-testid`）を選ぶとホームへ移る、日英の切り替え、アクセシビリティ検査 | 4〜5 |
| `App.test.tsx` | U1 だけの状態で起動しログイン用レイアウトが出る、登録の重複で起動が止まり問題の登録が示される、make-you-chic-ui の Provider が組み込まれている、アクセシビリティ検査 | 4〜5 |

### 3.4 ビルドした WAR での画面の確認（`frontend/e2e/u1-skeleton.e2e.ts`）

ビルドした WAR を一時ディレクトリの内部DBで起動し、`/` を開くとログイン用レイアウトが表示されること、ブラウザに CSP 違反とスクリプトのエラーが出ないこと、応答に CSP が付くことを確かめる（NFR3.10、security-design 9章）。1〜2件。

## 4. カバレッジの目標

- バックエンド（JaCoCo）・フロントエンド（`@vitest/coverage-v8`）とも、行 80% 以上・分岐 70% 以上。下回ったらビルドを失敗させる。
- 計測から外すのは、起動クラス（`MastersmithApplication`、`frontend/src/main.tsx`）、設定値だけのクラス（`@ConfigurationProperties` の record など）、自動生成コード、`vendor/` の下に限る。ロジックを持つ設定クラス（`SecurityConfig` など）は外さない。
- 下限に届かないときは、テストを足す。下限の値や除外を変えて通すことはしない。届かない理由が設計にあるときは、差を依頼者に示す。

## 5. モックとスタブの扱い

- 内部DBはモックにしない。結合テストは本番と同じ組み込みの H2（ファイル保存）を使う。Testcontainers は U1 では使わない（コンテナの実行環境は U1 のテストの前提にならない）。
- Spring の設定の条件（外部エクスポートの有効・無効、差し込み口の数と order）は、`ApplicationContextRunner` で必要な Bean だけを組み立てて確かめる。アプリ全体の起動が要るもの（ヘッダー、公開する範囲、トレースとログ）だけを `@SpringBootTest` にする。
- ヘルスの時間の上限は、問い合わせをラッチで止める偽の `DataSource`（または接続）で作る。`Thread.sleep` と実時刻の待ちに頼らない。時刻が要る処理は固定の `Clock` を渡す。
- ログは、テストの補助（`cherry.mastersmith.common.testsupport`）で logback の出力を捕まえ、JSON として読んで項目を確かめる。標準出力の文字列の部分一致だけで判定しない。
- 外部エクスポートの送り先は、つながらない番号（使っていない localhost の番号）で「届かない」を作る。外部のサービスに接続しない。
- Mockito は、境界の外（`Tracer` の現在のスパン、偽の提供元など）の差し替えに限って使い、テスト対象そのものはモックにしない。
- フロントエンドでは、`import.meta.glob` をモックにせず、glob の結果の形のデータを `loadRegistrations` に直接渡す。ブラウザの希望言語は `navigator.languages` の取得を差し替えて与える。画面の URL は React Router の `MemoryRouter` の初期の URL で与える。ログイン状態の提供元はテストの中の偽の提供元で与える。ネットワークには接続しない（U1 の画面は API を呼ばない）。

## 6. テストデータの扱い

- 結合テストは、テストのクラスごとに一時ディレクトリ（`@TempDir`）の H2 のファイルを使い、接続の URL を `@DynamicPropertySource` で渡す。ほかのテストの実行や順番に依存させない。
- データを書くテストは、テストの中で用意し、終わりに消す（またはトランザクションを巻き戻す）。再起動の後も残ることを確かめるテストは、同じ一時ディレクトリでコンテキストを作り直す。
- 秘密情報の役のテストの値（内部DBのパスワードなど）は、テストの中で作る乱数の文字列にし、ソースに本物らしい秘密情報を書かない（Gitleaks に検出されないようにする）。その値がログ・応答に出ないことを確かめる。
- 性質ベースのテストは、失敗したときに乱数の種を出力に残す。jqwik は失敗の報告の種を `@Property(seed = "...")` に与えて再現し、fast-check は失敗の報告の `seed`（と `path`）を `fc.assert` の設定に与えて再現する。
- テストデータの文言は日本語でよい。テストの説明文は英語で書く。

## 7. チームの必須のテストとの対応（U1 に関わるもの）

| チームの進め方の必須のテスト | U1 のテスト |
|---|---|
| 構造化ログが決めた形式で出る | `JsonLogFormatTest`、`TracingAndLoggingIT` |
| トレースIDがログに含まれる | `TracingAndLoggingIT`、`ErrorResponseIT` |
| 外部エクスポートが既定で無効 | `ExternalExportIT` |
| ログにパスワード・トークンの値が含まれない | `DatabasePersistenceIT`、`TracingAndLoggingIT`（U2 以降は同じテストの補助で各自確かめる） |
| 画面の部品ごとのアクセシビリティ検査 | 3.3 の各 `*.test.tsx` |
| 失敗の場合のテスト（拒否など） | 413・404・500・DOWN・起動の失敗（重複）・`ApiDefaultAccess` によるログイン必須（`ErrorResponseIT`、`ExposureIT`、`HealthEndpointIT`、`SecurityExtensionIT`、`ProblemTypeDuplicateStartupIT`） |
| 認可をサーバー側で確かめる | U1 では、公開の決まり（health・説明ページは未ログインで見られる）と `ApiDefaultAccess` の切り替えを `SecurityExtensionIT` で確かめる。401・403・200 の確認は U3 が行う |
| DB を使うテストとヘルスチェックの起動確認テスト（Walking Skeleton） | `DatabasePersistenceIT`、`HealthEndpointIT` |

## 8. 前提条件

- JDK 25、Node.js 24（`frontend/package.json` の `engines` で確かめる）、Git のサブモジュールの取得。
- U1 のテストにコンテナの実行環境は要らない（内部DBは組み込みの H2）。Playwright の確認には Chromium を入れる（`npx playwright install chromium`）。
- `./gradlew verify` 全体を実行する場合は、あわせて Gitleaks と OSV-Scanner が要る（README の入れ方に従う）。
