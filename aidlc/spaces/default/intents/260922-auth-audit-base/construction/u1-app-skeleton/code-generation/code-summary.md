# Code Summary — U1 アプリの骨格（u1-app-skeleton）

`code-generation-plan.md` の Step 1〜23 をすべて終えた。アプリのコードはリポジトリのルート直下（`backend/`・`frontend/`・ルートの設定ファイル・`.github/`・`docker/`）に置き、`aidlc/` の下には置いていない。方法は Testing Contract の test-after（層ごとに実装してから、その層のテストを書いて実行する）に従った。要件・決まりと、実装とテストのファイルの対応は `traceability.json` にある。

## 作った・変えたファイル

計画の承認のコミット（e7de6c1）からの差分。変更 1 件（`.gitignore`）、新規 168 件、削除なし。`vendor/make-you-chic-ui` の追跡されるファイルとサブモジュールの固定先は変えていない。

### ルートとビルドの設定

- `.editorconfig`、`.gitattributes`、`.gitignore`（変更: 秘密情報のファイル・Gradle・内部DB・テストの出力の除外を追加）
- `settings.gradle.kts`、`build.gradle.kts`（1コマンドの検査 `verify`、npm・Gitleaks・OSV-Scanner の呼び出し、`e2eTest`）、`settings-gradle.lockfile`
- `gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.jar`、`gradle/wrapper/gradle-wrapper.properties`、`gradlew`、`gradlew.bat`
- `config/license-header.txt`（ライセンスヘッダーのひな形）、`config/npm-build-tools.txt`（成果物を作る道具の一覧）
- `.gitleaks.toml`、`.pre-commit-config.yaml`
- `Dockerfile`、`.dockerignore`、`compose.yaml`、`docker/otel-collector/config.yaml`、`.env.example`
- `.github/workflows/ci.yml`、`.github/dependabot.yml`
- `README.md`

### バックエンド（`backend/`）

- ビルドと資源: `build.gradle.kts`、`gradle.lockfile`、`config/spotbugs-exclude.xml`、`src/main/resources/application.yaml`、`src/main/resources/logback-spring.xml`、`src/main/resources/db/migration/V1__u1_baseline.sql`
- 起動クラス: `src/main/java/cherry/mastersmith/MastersmithApplication.java`
- `config`（Spring の設定）: `SecurityConfig`、`SecurityHeaderProperties`、`WebConfig`、`ForwardedHeaderConfig`、`ObservabilityConfig`、`package-info`
- `common.error.domain`: `ProblemType`、`LocalizedText`、`ProblemTypeCatalog`、`BusinessException`、`CommonProblemTypes`
- `common.error.service`: `ProblemTypeRegistry`、`CommonProblemTypeCatalog`
- `common.error.web`: `ErrorResponseFactory`、`GlobalExceptionHandler`、`DefaultErrorResponseWriter`、`ErrorPathController`、`ProblemBaseUrlResolver`、`ProblemTypeController`、`ProblemTypeHtmlRenderer`、`ProblemTypeResponse`
- `common.health`: `TimeBoundedDbHealthIndicator`、`HealthProperties`
- `common.i18n.domain`: `AcceptLanguageResolver`、`DisplayLanguage`
- `common.observability`: `TraceIdProvider`、`SanitizingSpanExporter`、`UrlQueryStrippingObservationFilter`、`TraceAspect`、`TraceProperties`、`ObservabilityProperties`
- `common.security`（差し込み口）: `SecurityRuleContributor`、`ApiDefaultAccess`、`ErrorResponseWriter`、`SecurityExtensionValidator`
- `common.web`: `RequestSizeLimitFilter`、`CacheControlFilter`、`MastersmithWebProperties`、`common/package-info`
- テスト（`src/test/java/cherry/mastersmith/`）: `ArchitectureTest`、`common/db/DatabasePersistenceIT`、`common/error/domain/ProblemTypeTest`、`common/error/service/{ProblemTypeRegistryTest, ProblemTypeDuplicateStartupIT}`、`common/error/web/{ProblemBaseUrlResolverTest, ErrorResponseFactoryTest, GlobalExceptionHandlerTest, DefaultErrorResponseWriterTest, ErrorPathControllerTest, ProblemTypeHtmlRendererTest, ErrorResponseIT, ProblemTypePageIT}`、`common/health/{TimeBoundedDbHealthIndicatorTest, HealthEndpointIT}`、`common/i18n/domain/AcceptLanguageResolverTest`、`common/observability/{TraceIdProviderTest, SanitizingSpanExporterTest, UrlQueryStrippingObservationFilterTest, JsonLogFormatTest, TracingAndLoggingIT, ExternalExportIT, TraceAspectIT}`、`common/security/SecurityExtensionValidatorTest`、`common/web/{RequestSizeLimitFilterTest, CacheControlFilterTest}`、`config/{SecurityHeadersIT, ExposureIT, SecurityExtensionIT}`
- テストの補助（U2 以降も使う）: `common/testsupport/{JsonLogRecords, LogEvents, TestDatabase, HttpTestClient, TestFixtureEndpoints, TestSecurityExtensions, service/TraceTargetService}`
- テストの資源: `src/test/resources/{archunit.properties, junit-platform.properties, static/index.html, static/assets/app-test.js}`

### 画面（`frontend/`）

- 設定: `package.json`、`package-lock.json`、`.npmrc`、`tsconfig.json`、`vite.config.ts`、`vitest.config.ts`、`vitest.setup.ts`、`playwright.config.ts`、`.prettierrc.json`、`.prettierignore`、`.oxlintrc.json`、`eslint.config.js`、`.stylelintrc.json`、`index.html`
- スクリプト: `scripts/check-license-header.mjs`、`scripts/check-bundle-size.mjs`
- 入口: `src/main.tsx`、`src/types/vitest-axe-matchers.d.ts`、`src/features/README.md`（後の単位の登録の置き場所）
- `src/app/`: `App.tsx`、`i18n/{i18n.ts, I18nProvider.tsx, resolveLanguage.ts, messages/ja.ts, messages/en.ts}`、`registry/{types.ts, loadRegistrations.ts, registrationModules.ts, validateRegistrations.ts, FeatureRegistryContext.tsx}`、`routing/{decideRoute.ts, AppRouter.tsx}`、`navigation/navigationItems.ts`、`login-state/LoginStateGate.tsx`、`layout/{ShellLayout.tsx, StandaloneLayout.tsx, StandaloneLayout.css, LoginLayout.tsx, LoginLayout.css}`、`pages/{HomePage.tsx, NotFoundPage.tsx, StartupErrorPage.tsx, Page.css}`、`testing/renderWithProviders.tsx`
- テスト: `src/app/` の下の `*.test.ts` / `*.test.tsx`（15 ファイル）、`e2e/u1-skeleton.e2e.ts`

## 主な実装の判断

- **版**: Java 25（Temurin 25.0.4）、Spring Boot 4.1.1、Gradle 9.7.1、Node.js 24、React 19、Vite 8、Vitest 4、TypeScript 6。Gradle はバージョンカタログと lockfile、npm は `package-lock.json` で固定した。起動・1行1件の JSON のログ（logstash-logback-encoder 9.0）・Flyway の H2 への適用を、この組み合わせで確かめた。
- **エラー応答**: Spring の `ProblemDetail` に `code` と `traceId` を足した形を、`ErrorResponseFactory` で組み立てる。コントローラーの中の例外は `GlobalExceptionHandler`（1か所）、フィルターの段階は `DefaultErrorResponseWriter`、MVC の外（`/error`）は `ErrorPathController` で、いずれも Spring MVC と同じ変換器で書くため同じ形になる。
- **ヘルスチェック**: Actuator の既定の DB の確認を、専用のスレッド1本・制限時間付き・前の確認が終わるまで次を始めない `TimeBoundedDbHealthIndicator` に置き換えた。
- **フィルターの連鎖**: `SecurityFilterChain` は1つだけ。U1 の公開の決まり → `SecurityRuleContributor`（order 順）→ `ApiDefaultAccess` → 画面の配信の許可、の順。本文の大きさの確認は連鎖の中（ヘッダーを書く処理の後）、キャッシュの指定は連鎖より前に置き、拒否の応答にもヘッダーが付く。
- **トレースと外部エクスポート**: トレースの仕組みは常に有効で、`mastersmith.observability.export.enabled`（既定 false）1つで、Spring Boot の OTLP の送信（トレース・ログ・指標）を切り替える。外部へ送るトレースからは例外のメッセージ・スタックトレース・URL の問い合わせの部分を取り除く。
- **画面の差し込み口**: `import.meta.glob` の結果を純粋な関数（`loadRegistrations`・`validateRegistrations`）に渡して検査し、URL ごとの振り分けは純粋な関数 `decideRoute` で決める。
- **1コマンドの検査**: ルートの `verify` が 0〜9 の段を順に実行する。CI も同じタスクを呼ぶ。

## テストとカバレッジ

| 対象 | テストの件数 | 行カバレッジ | 分岐カバレッジ |
|---|---|---|---|
| バックエンド（JUnit・jqwik・ArchUnit・Spring Boot Test。単体 `*Test` と結合 `*IT`） | 206（失敗 0） | 95.2%（556/584） | 88.1%（214/243） |
| 画面（Vitest・Testing Library・vitest-axe・fast-check） | 85（失敗 0） | 99.11%（223/225） | 94.02%（126/134） |
| ビルドした WAR での画面の確認（Playwright、`./gradlew e2eTest`） | 2（失敗 0） | — | — |

- 下限（行 80%・分岐 70%）は、JaCoCo と `@vitest/coverage-v8` の両方で満たした。計測の除外は、起動クラス・設定値だけの record（`*Properties`）・`src/main.tsx`・型の宣言に限り、広げていない。
- 性質ベースのテスト: jqwik（code と slug の導出、Accept-Language の解決）、fast-check（表示言語の決定、画面の URL の重複の拒否）。
- 画面の部品ごとに vitest-axe のアクセシビリティ検査を1件入れた。

## 1コマンドの検査（`./gradlew verify`）の結果

最後の実行で、すべての段が通った（終了コード 0）。

| 段 | 結果 |
|---|---|
| 0 準備 | 成功（Node.js 24、make-you-chic-ui のビルド、サブモジュールの追跡されるファイルに変化なし、`npm ci`） |
| 1 フォーマット | 成功（Spotless、Prettier） |
| 2 リンタ | 成功（oxlint、ESLint、Stylelint） |
| 3 ライセンスヘッダー | 成功（画面のファイル。Java・Gradle の Kotlin DSL は 1 の段の Spotless が確かめる） |
| 4 ビルド | 成功（Java のコンパイル、`tsc --noEmit`、Vite のビルド） |
| 5 単体テスト | 成功 |
| 6 結合テスト | 成功 |
| 7 カバレッジの下限 | 成功（上の表） |
| 8 安全の検査 | 成功（SpotBugs の High 0 件・警告 23 件、OSV-Scanner の失敗の条件 0 件・警告 8 件、Gitleaks の検出なし） |
| 9 成果物と量の確認 | 成功（画面を同梱した実行可能 WAR、初回の読み込みの JavaScript は gzip 後 102.6KB） |

あわせて `pre-commit run --all-files`（Gitleaks・Spotless・Prettier）がすべて通った。

## コンテナでの起動の確認

- `docker compose up` で、コンテナは 6 秒で healthy になった（アプリの起動は 3.5 秒。NFR1.4 の 30 秒以内）。
- `/actuator/health` は 200 `{"status":"UP"}`。`/` は 200 で CSP のヘッダーが付く。Chromium で開くと、ログイン用レイアウト（アプリ名 MasterSmith、見出し「ログイン」）が表示され、CSP の違反とスクリプトのエラーは無かった。
- コンテナの中は、利用者 UID 10001、`/app/data` は 700、タイムゾーンは JST。メモリ 1GB、再起動なし、停止の猶予 45 秒。
- profile `observability` で外部エクスポートを有効にすると、トレース・ログ・指標（`http.server.requests` を含む 106 の指標）が受け手に届き、要求に入れた秘密の役の値と URL の問い合わせの部分は受け手の出力に無かった。
- この確認をした PC の colima の VM は CPU が 2 つのため、確認のときだけ CPU の上限を 2 に上書きして起動した（リポジトリの `compose.yaml` は設計どおり 4）。

## 計画からの逸脱と、生成中に決まったこと

1. **ログの外部エクスポートの部品の版（依頼者の決定 A）**: `opentelemetry-logback-appender-1.0` の最新版 2.31.1-alpha は `opentelemetry-api` を 1.65.0 に引き上げ、Spring Boot 4.1.1 が管理する OpenTelemetry SDK 1.62.0 と食い違って、外部エクスポートを有効にしたときに `NoClassDefFoundError` になった。SDK 1.62 に合う 2.28.1-alpha に固定した（Spring Boot を上げるときに見直す旨を `libs.versions.toml` に書いた）。
2. **Tomcat の版の上書き**: Spring Boot 4.1.1 が管理する Tomcat 11.0.24 に CVSS 9.1〜9.8 の脆弱性（11.0.25 で修正）があったため、11.0 系の最新の修正版 11.0.26 に上げた（`libs.versions.toml` と `backend/build.gradle.kts`）。Spring Boot を上げるときに、この固定が要るか見直す。
3. **Step 11 の実装を Step 10 のテストより先に行った**: `SecurityFilterChain` が無いと Spring Boot の既定のセキュリティが全要求にログインを求め、Step 10 の結合テストが通らないため。同じ API の層の中での順序の入れ替え。
4. **計画に無い部品・設定**:
   - `ErrorPathController`（MVC の外のエラー `/error` も共通の形にする）
   - `ForwardedHeaderConfig`（信頼の設定が true のときだけ `ForwardedHeaderFilter` を登録）
   - `UserDetailsServiceAutoConfiguration` を外した（生成したパスワードがログに出るため）
   - `spring.web.resources.add-mappings=false`（画面の配信は `WebConfig` で行う）
   - Actuator の一覧のページ（`/actuator`）を無効にした
5. **テストのための工夫**: `TimeBoundedDbHealthIndicator` と `DefaultErrorResponseWriter` に、パッケージ内だけのテスト用のコンストラクターを足した。テスト用の Bean は `mastersmith.test-fixture.*` の条件を付け、ほかのテストの起動に入らないようにした。Host ヘッダーの確認のため、テストの JVM に `jdk.httpclient.allowRestrictedHeaders=host` を設定した。`ExternalExportIT` は、Spring Boot の自動設定との組み合わせを確かめるため、`ApplicationContextRunner` ではなく `@SpringBootTest` にした。
6. **`AppRouter` の作り方**: 組み込みガイドの「レイアウトルート」の形は文字どおりには使わず、`decideRoute` の判断で `ShellLayout` と `StandaloneLayout` を振り分けた（未ログインでログインが要る画面を開いたとき、アプリシェルの外にログイン用レイアウトを出すため）。登録された画面は、その URL の形の `<Route>` の中で表示し、`useParams` が使える。
7. **計画に無い画面の部品**: `StartupErrorPage`（登録の問題を示す画面の起動の失敗の表示）と、テストの補助 `src/app/testing/renderWithProviders.tsx`（計測の対象のまま）。
8. **ログイン状態の提供元の形**: `LoginStateProvider` を `getLoginState()`（同期・非同期のどちらでもよい）と任意の `subscribe()`（状態が変わったことの知らせ）にした。設計では項目だけが決まっていたため。
9. **アプリ名**: 要件定義に合わせて `MasterSmith` にした（`index.html` の title を含む）。
10. **1コマンドの検査の準備の段（0）**: 計画の9つの段の前に、道具の確認・make-you-chic-ui のビルド・`npm ci` を行う準備の段を足した。
11. **3 の段（ライセンスヘッダー）の範囲**: Java と Gradle の Kotlin DSL のヘッダーは、同じ Spotless の検査が 1 の段で確かめる（3 の段に置くと依存が循環するため、3 の段は画面のファイルだけ）。
12. **コンテナのヘルスチェック**: イメージに curl・wget が無いため、bash の `/dev/tcp` で `/actuator/health` を呼び、200 を確かめる形にした。
13. **CI のリリース**: GitHub のリリースへの WAR の添付は、外部の Action を使わず `gh release create` で行う。
14. **Gitleaks の誤検出の除外**: AI-DLC の記録（`aidlc/…/memory.md`）の目印 `aidlc-wave-memory:<単位>:<64桁の16進数>` が秘密情報として検出されたため、「aidlc の下の memory.md」かつ「その形の値」の両方に当たるものだけを `.gitleaks.toml` で除外した。本物の秘密情報が引き続き検出されることを確かめた。
15. **SpotBugs の High の修正**: `ErrorPathController` の `@RequestMapping` にメソッドの指定が無かった（`SPRING_CSRF_UNRESTRICTED_REQUEST_MAPPING`）。読み取り（GET・HEAD・OPTIONS）と更新（POST・PUT・PATCH・DELETE）の2つの処理に分けた。関門の基準は変えていない。
16. **npm の依存関係の脆弱性の判定（依頼者の決定）**: team.md の「重大度 High 以上で統合を止める」と cicd-pipeline.md の 8 の段を、次のとおり細かくした。
    - バックエンド（Gradle）と、画面の実行時の依存関係（make-you-chic-ui の実行時の依存関係を含む）: High 以上で失敗。
    - 画面の開発用の依存関係: 警告だけ。ただし、成果物を作る道具（`config/npm-build-tools.txt`）の High 以上は失敗。
    - 悪意のあるパッケージ（OSV の ID が `MAL-` で始まる）: 重大度によらず失敗。
    - 実行時か開発用かは lockfile の `dev` の印で判定し、見つからないものは実行時として扱う。`./gradlew verify` の中の判定のため、CI でも同じ。README に決まりを書いた。
17. **TraceAspect の既定値（依頼者の指定）**: `mastersmith.trace.*`（`use-dynamic-logger`・`hide-proxy-class-names`・`log-exception-stack-trace`・`enter-message`・`exit-message`・`exception-message`）を、環境変数 `MASTERSMITH_TRACE_*` で上書きできる形で置き、`CustomizableTraceInterceptor` の同じ名前の設定に渡した。`TraceAspectIT` で既定値が効くことを確かめ、`.env.example` と README の一覧に書いた。
18. **コンテナの CPU**: `compose.yaml` の CPU の上限は設計どおり 4。colima を使う場合は VM に CPU を 4 つ以上割り当てる必要がある（README に `colima start --cpu 4` を書いた）。
19. **現在の OSV-Scanner の警告 8 件**: すべて `vendor/make-you-chic-ui` の開発用の依存関係（fast-uri 3.1.5・js-yaml 4.3.1 の High、vitest 4.1.10 ほか）。make-you-chic-ui のリポジトリ側で直し、承認を得た専用のコミットでサブモジュールの固定先を更新することを勧める。

## 後の単位への申し送り

- **U3**: 401・403 の応答は、`ErrorResponseWriter` を使って自分で書くこと。`ErrorPathController` は、対応を決めていない状態コード（`sendError` による 401・403 など）を 500 / `INTERNAL_ERROR` にする。U1 の既定のログインの入口は、本文なしの 401 を返す（`HttpStatusEntryPoint`）。
- **U2・U3**: 決まりは `SecurityRuleContributor`（U2 は order 100 台、U3 は 200 台）で足し、ヘッダー・セッション・CSRF の設定は変えない。`ApiDefaultAccess` は U3 が1つだけ置く。
- **U2 以降**: 秘密情報を持つ型は文字列化で伏せ字にし（メソッドの呼び出しの追跡が引数と戻り値を文字列にするため）、`cherry.mastersmith.common.testsupport.JsonLogRecords` でログに秘密情報が出ないことを確かめる。
- **U2**: ログイン画面は `LoginLayout` を使い、`frontend/src/features/<featureId>/registration.ts` に role=LOGIN・STANDALONE・PUBLIC の画面として登録する。ログイン状態の提供元は `getLoginState()` と `subscribe()` で渡す。署名鍵・初期管理者の環境変数の名前を決めて、`.env.example` と README に足す。
- **U2・U4**: スキーマの変更は `backend/src/main/resources/db/migration` に `V<番号>__<単位>_<内容>.sql` で置く。
- **Performance Validation**: NFR1.1（ヘルスチェックの 95 パーセンタイル）、NFR1.3（共通の処理の時間）、NFR1.9（ボリュームの増え方）を測る（`traceability.json` で Deferred）。
