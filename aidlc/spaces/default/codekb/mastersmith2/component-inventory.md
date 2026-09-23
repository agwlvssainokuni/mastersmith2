# 部品の一覧（mastersmith2）

見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。状態の欄は、healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）で示す。「読みの深さ」は今回のスキャンでの扱いである。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス。実行可能 WAR と外部のサーブレットコンテナの両方で起動できる（`SpringBootServletInitializer`）。`@ConfigurationPropertiesScan` で設定の型を集める。
- 依存: Spring Boot
- 状態: healthy ／ 読みの深さ: 深い

### config

- 場所: `config/`
- 責務: Spring Security のフィルターの連鎖（1つ、状態なし、CSRF・フォームログインなし）、応答ヘッダー（CSP など）、SPA の配信と見つからない URL への `index.html`、転送元ヘッダーの扱い、OTLP の外部エクスポートの組み立て。
- 依存: `common-security`・`common-web`・`common-observability`
- 状態: healthy ／ 読みの深さ: 深い

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: Problem Details（`code`・`traceId` 付き）のエラー応答。`BusinessException`・`ProblemType`・`ProblemTypeCatalog`・`ProblemTypeRegistry`（起動時に重複を検査）・`GlobalExceptionHandler`（`@RestControllerAdvice` の1か所）・`DefaultErrorResponseWriter`・`ErrorPathController`・問題の種類の説明ページ。
- 依存: `common-i18n`・`common-observability`
- 状態: at-risk（複数の検証エラーを返す形が無い。`code-quality-assessment.md` の C-6） ／ 読みの深さ: 深い

### common-security

- 場所: `common/security/`
- 責務: セキュリティの差し込み口（`SecurityRuleContributor`・`ApiDefaultAccess`・`ErrorResponseWriter`）と、起動時の検査（`SecurityExtensionValidator`）。
- 依存: Spring Security
- 状態: healthy ／ 読みの深さ: 深い

### common-web

- 場所: `common/web/`
- 責務: `CacheControlFilter`（`/api/**` は `no-store` など）、`RequestSizeLimitFilter`（本文の上限、既定 1MB）、`mastersmith.web.*` の設定の型。
- 依存: `common-security`（`ErrorResponseWriter`）
- 状態: at-risk（完成品の DSL の投入が 1MB を超えうる。C-7） ／ 読みの深さ: 深い

### common-health

- 場所: `common/health/`
- 責務: 制限時間付きの内部DBの確認（`SELECT 1`、既定 2 秒）。Actuator の既定の DB の確認を置き換える。
- 依存: 内部DB の `DataSource`
- 状態: at-risk（対象DB を足したときに何を見るかが未定。C-9） ／ 読みの深さ: 深い

### common-i18n

- 場所: `common/i18n/domain/`
- 責務: `Accept-Language` から表示言語（ja／en、既定 ja）を決める。
- 依存: なし
- 状態: healthy ／ 読みの深さ: 深い

### common-observability

- 場所: `common/observability/`
- 責務: トレースIDの参照（`TraceIdProvider`）、送るトレースから例外のメッセージを除く（`SanitizingSpanExporter`）、URL の問い合わせを除く、メソッドの呼び出しの追跡（`TraceAspect`。`web`・`service`・`domain`・`repository` の層の Bean の引数と戻り値を TRACE のときだけ文字列にする）。
- 依存: Micrometer Tracing・OpenTelemetry
- 状態: healthy（新しい型にも `toString` の伏せ字が要る。C-9） ／ 読みの深さ: 深い

### auth

- 場所: `auth/{domain,service,repository,web}`
- 責務: ログイン（ロックの判定、ダミーの行による存在の秘匿）、JWT（HS256）のアクセストークンの発行と検証、リフレッシュトークン（ハッシュだけを保存、Cookie で受け渡し、使うたびに作り直す）、ログアウト、使い終わったトークンの定期削除。`Clock` の Bean（UTC）を全体に提供する。Bearer の検証の決まり（order 110）を足す。
- 依存: `user`・`common-error`・`common-security`・`common-observability`・内部DB（`login_attempt_states`・`refresh_tokens`）
- 知らせる出来事: `AuthenticationEvent`（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`）
- 状態: healthy ／ 読みの深さ: 深い（`web`・`repository`・`LoginService` ほか中心のファイル。`service`・`domain` の残りは流し読み）

### access

- 場所: `access/{domain,service,web}`
- 責務: `/api/admin`・`/api/admin/**` を管理者のみにする決まり（order 210）、`/api/**` の既定をログイン必須にする（`AdminApiDefaultAccess`）、401・403・400（正規化されていないパス）の処理、アクセス拒否の出来事の通知、確認用 API `GET /api/admin/check`。DB を読まない。
- 依存: `auth`（`AuthenticatedUser`・`TokenAuthenticationEntryPoint`・`ClientInfoResolver`）、`common-security`・`common-error`・`config`（`SecurityHeaderProperties`）、`Clock` の Bean
- 知らせる出来事: `AdminAccessDeniedEvent`
- 状態: healthy ／ 読みの深さ: 深い

### audit

- 場所: `audit/{domain,service,repository}`
- 責務: 認証の出来事とアクセス拒否の出来事を受け取り、確定の後に内部DBの `audit_events` へ別トランザクション（`REQUIRES_NEW`）で1件ずつ追記する。失敗は受け止めて ERROR を1件出す。追記と読み取りだけの repository。
- 依存: `auth`（`AuthenticationEvent`）・`access`（`AdminAccessDeniedEvent`）・内部DB
- 状態: at-risk（監査の種類が4種に固定され、操作対象の列が無い。C-8） ／ 読みの深さ: 深い（`service`・`repository`・`AuditEvent`・`AuditEventType`・`AuditEventFactory`。`domain` の残りは流し読み）

### user

- 場所: `user/{domain,service,repository}`
- 責務: 利用者のエンティティ、メールアドレス・パスワードの決まり、bcrypt、パスワードの照合（利用者がいないときのダミーのハッシュ `DummyPasswordHash` がある）、初期管理者の自動作成（`SmartInitializingSingleton`）。
- 依存: 内部DB（`users`）
- 状態: healthy ／ 読みの深さ: 流し読み（クラスの宣言とトランザクション・出来事の位置だけ）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/routing/`・`app/navigation/`
- 責務: 起動（登録の読み込みと検査の後に描画）、URL の振り分け（`decideRoute`。登録した URL と完全一致で1画面、ログインと権限で振り分ける）、サイドバーの項目の組み立て。
- 依存: `frontend-registry`・`make-you-chic-ui`・react-router
- 状態: at-risk（入れ子のルートや画面の中の段階の URL が無い。C-10） ／ 読みの深さ: 深い

### frontend-registry

- 場所: `app/registry/`
- 責務: 機能の登録の型（`FeatureRegistration`）、`features/*/registration.ts` の自動の読み込み（`import.meta.glob`、eager）、重複や決まり違反の検査（起動を止める）。
- 依存: なし
- 状態: healthy ／ 読みの深さ: 深い

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/login-state/`・`app/pages/`・`app/testing/`
- 責務: レイアウト（AppShell の中 `ShellLayout`、外 `StandaloneLayout`・`LoginLayout`）、表示言語（i18next）、ログイン状態の受け取り、ホーム・「ページが見つかりません」・起動エラーの画面、テストの補助。
- 依存: `frontend-registry`・`make-you-chic-ui`
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`
- 責務: 同じオリジンの API の呼び出し、アクセストークンの付与、401 / `AUTHENTICATION_REQUIRED` での更新1回と送り直し1回（同時の 401 は1つの更新にまとめる）、エラーを `ApiError { kind, status, code? }` にする。
- 依存: なし（認証の手段は `registerAuthHandlers` で受け取る）
- 状態: at-risk（エラーの `code` 以外の中身を画面に渡さない。項目ごとの検証エラーを見せるには拡張が要る。C-6） ／ 読みの深さ: 深い

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログイン画面（`/login`）、アクセストークンのメモリ保持（`authSession`）、ログイン状態の提供元、ログアウトのメニュー、入力の検証。
- 依存: `frontend-api-client`・`frontend-registry`・`make-you-chic-ui`
- 状態: healthy ／ 読みの深さ: 流し読み（`registration.ts`・`authSession.ts` だけ深い）

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理者向け領域 `/admin`（SHELL・ADMIN、サイドバー「管理」order 200）。表示のたびに `GET /api/admin/check` を呼ぶ。中身は置き場（`AdminPlaceholder`）だけ。
- 依存: `frontend-api-client`・`frontend-registry`・`make-you-chic-ui`
- 状態: at-risk（DSL の画面を置く場所。置き方が未定。C-10） ／ 読みの深さ: 深い

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`）
- 責務: デザインシステム。公開部品は ThemeProvider・Icon・Button・FormField・TextInput・Textarea・Select・Checkbox・Switch・RadioGroup・Avatar・Badge・Card・Modal・Toast・Alert・Tooltip・Tabs・Dropdown・AppShell・Table（`DefaultCellEditor` つき）。今の画面で使っているのは AppShell・Alert・Button・FormField・TextInput・Toast・Modal と各 Provider だけ。
- 依存: React
- 状態: at-risk（ファイルの選択・差分の表示・コードエディターの部品が無い。このリポジトリからは変更できない。C-10） ／ 読みの深さ: 流し読み（公開部品の一覧だけ）

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/`・`config/`・`.github/`・`.pre-commit-config.yaml`・`.gitleaks.toml`
- 責務: 1コマンドの検査 `./gradlew verify`（段 0〜9）、WAR の組み立て（`frontend/dist` の同梱）、lockfile による版の固定、CI（`develop` へのプッシュと `v*` のタグ）、コミット前の検査。
- 依存: Gradle 9.7.1・npm（Node.js 24）・Gitleaks・OSV-Scanner
- 状態: at-risk（Testcontainers とコンテナの実行環境の前提が無い。C-4） ／ 読みの深さ: 深い

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`・`.dockerignore`
- 責務: WAR をコピーするだけの1段のイメージ、`app` のサービス（`127.0.0.1:8080`、ボリューム `mastersmith-data`、ヘルスチェック、CPU の上限 既定 4、メモリの上限 既定 1g）、profile で起動する `otel-collector`・`lgtm`。
- 依存: Docker（colima）
- 状態: at-risk（対象DB のコンテナと接続の設定が無い。C-9） ／ 読みの深さ: 深い

### perf-and-monitoring

- 場所: `perf/`・`docker/`（`perf/compose.yaml`・`monitoring/`・`otel-collector/`・`check-container-limits.sh`）
- 責務: k6 の負荷の試験と使い捨ての環境、手元の監視（Grafana のダッシュボード・警報のファイル）、コンテナの上限の確認。
- 依存: `container-runtime`
- 状態: healthy ／ 読みの深さ: 流し読み（ファイルの存在と見出しだけ）
