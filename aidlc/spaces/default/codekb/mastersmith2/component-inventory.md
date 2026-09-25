# 部品の一覧（mastersmith2）

## 読み方

見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回のコード知識ベースと同じ 27 個である。

- 状態: healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ: 深い（今回深く読んだ。一部のファイルだけのものは括弧で示す）・流し読み。
- K の番号は `business-overview.md` の所見の一覧を指す。所見の本文は、下の各部品に1回だけ書いた（K-1・K-4・K-5・K-7・K-8）。
- 依存は、バックエンドは `import` の検索で確かめた向き（詳細は `dependencies.md`）。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて `application.yaml`・`logback-spring.xml`）
- 責務: Spring Security の連鎖の組み立て（差し込み口を order 順に当て、`/api/**` の既定の扱いを決める）、SPA の配信（見つからない画面の URL に `index.html`）、転送元のヘッダー、観測の設定。安全のためのヘッダー（CSP・`frame-ancestors 'none'`・Referrer-Policy）もここで付ける。
- 依存: `common-security`・`common-web`・`common-observability`
- 状態: healthy ／ 読みの深さ: 深い（`SecurityConfig`・`WebConfig`・`application.yaml`・`logback-spring.xml`）

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: `ProblemType`（code・状態コード・日英の文言）、`BusinessException`、`ProblemTypeRegistry`（code と slug の重複で起動の失敗）、`GlobalExceptionHandler`・`ErrorResponseFactory`（Problem Details、`Accept-Language` で日英）、`ProblemBaseUrlResolver`（K-9、`api-documentation.md`）。
- 状態: healthy ／ 読みの深さ: 深い（domain・service の全部と web の2ファイル）

### common-security

- 場所: `common/security/`
- 責務: 差し込み口 `SecurityRuleContributor`（order 付き）・`ApiDefaultAccess`（0 個か 1 個）・`ErrorResponseWriter` と、起動時の検査（`SecurityExtensionValidator`）。
- 状態: healthy ／ 読みの深さ: 深い

### common-web

- 場所: `common/web/`
- 責務: 要求の本文の大きさの上限（既定 1MB、道ごとの上限は `RequestBodyLimitRoute`）、`mastersmith.web.*` の設定（`MastersmithWebProperties`）、Cache-Control。
- 状態: healthy ／ 読みの深さ: 深い（`RequestBodyLimitRoute`・`MastersmithWebProperties` だけ）

### common-health

- 場所: `common/health/`
- 責務: 制限時間付きの内部DB のヘルスチェック。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-i18n

- 場所: `common/i18n/`
- 責務: `DisplayLanguage`（JA・EN）と、要求の `Accept-Language` からの決定（`AcceptLanguageResolver`、既定 JA）。利用者ごとの言語は持たない（K-5 の続き）。
- 状態: healthy ／ 読みの深さ: 深い

### common-observability

- 場所: `common/observability/`
- 責務: `TraceAspect`（web・service・domain・repository の引数と戻り値を TRACE で文字列化）、外部エクスポートの伏せ字（`Sanitizing*`）、トレースIDの提供。
- 注意: 秘密を持つ型は `toString` で伏せ字にしないと `TraceAspect` の文字列化でログに出る（`user/domain/Password.java` が見本）。招待の値などの新しい秘密の型にも同じ扱いが要る。
- 状態: healthy ／ 読みの深さ: 深い（`TraceAspect` だけ。`Sanitizing*` は伏せる項目の定義だけ）

### auth

- 場所: `auth/{domain,service,repository,web}`
- 責務: ログイン（ロック判定 `LockPolicy`、しきい値 既定 5・期間 既定 30 分）、JWT（HS256、既定 5 分）のアクセストークン、リフレッシュトークン（既定 24 時間、ハッシュだけを保存、使うたびに作り直す、Cookie）、ログアウト、トークンの認証（`AccessTokenAuthenticationProvider`）、期限切れのリフレッシュトークンの定期の削除。利用者の作成の出来事でロックの状態の行を作る（`LoginAttemptStateInitializer`、`Propagation.MANDATORY`）。
- 依存: `user.service`・`user.domain`（`Password` の値だけ）・`common-error`・`common-security`・`common-observability`・内部DB（`login_attempt_states`・`refresh_tokens`）
- K-8（確かめた事実）: `RefreshTokenRepository` の変更の操作は、1件の無効化（`revokeIfActive`）と期限切れの削除だけである。利用者のすべてのリフレッシュトークンを無効にする操作は無い。アクセストークンは失効の仕組みを持たない（`project.md` の DECIDED）。パスワードの変更の後にほかの端末のリフレッシュトークンを無効にするなら、一括の無効化の操作を足す必要があり、アクセストークンは期限（既定 5 分）まで使える。どこまで無効にするかは決まっていない。
- 状態: at-risk（K-2・K-8） ／ 読みの深さ: 深い（service の8ファイル、web の9ファイル、domain の3ファイル、repository）

### access

- 場所: `access/{domain,service,web}`
- 責務: `/api/admin` と `/api/admin/**` を管理者のみにする決まり（`AdminSecurityContributor`、order 210）、`/api/**` の既定のログイン必須（`AdminApiDefaultAccess`）、401・403 の入口、アクセス拒否の出来事（`AdminAccessDeniedEvent`）。役割・権限の判定を足す置き場は `AdminAuthorizationManager`。
- 依存: `auth.domain`・`auth.web`・`common-error`・`common-security`・`config`
- 状態: healthy ／ 読みの深さ: 深い（web の6ファイルと `AdminPaths`）

### audit

- 場所: `audit/{domain,service,repository}`
- 責務: 認証・アクセス拒否・DSL の操作の出来事を受け、確定の後に `audit_events` へ別トランザクション（`REQUIRES_NEW`）で追記する。更新・削除の操作は持たない。流れは `architecture.md` の Interaction Diagrams 4。
- 依存: `auth.domain`・`access.domain`・`dslmanage.domain`（出来事の型）・内部DB（`audit_events`）
- K-7（確かめた事実）: 出来事の種類は `AuditEventType`（enum、今は `LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED` と `DSL_` で始まる5つの計9つ）で、`AuditEventFactory` が出来事の型ごとに組み立て、`AuditEventListener` が出来事の型ごとの受け取りのメソッドを持つ。新しい出来事（利用者の登録・招待の送信・登録の完了・パスワードやプリファレンスの変更など）を足すと、この3つと、列の一覧を持つテスト（`AuditSecretLeakIT` など、中身は未確認）を変える必要がある。`audit` が新しい機能の出来事の型に依存する形になる。`audit_events` には操作した人の `actor_user_id`（V6）はあるが、操作の対象の利用者を表す専用の列は無い（`entered_email` はログインで入力されたメールアドレス用、VARCHAR(508)）。`project.md` の DECIDED で監査の共通化は後続の Intent に回っている。
- 状態: at-risk（K-7・K-10） ／ 読みの深さ: 深い（domain の4ファイル、service・repository の全部）

### user

- 場所: `user/{domain,service,repository}`
- 責務: 利用者のエンティティ、メールアドレスの正規化、パスワードの規則と bcrypt の照合、初期管理者の自動作成、利用者の作成の出来事。
- 依存: アプリの中のほかのパッケージを import しない（`auth` に依存しないことは `AuthBoundaryArchitectureTest` で確かめている）。内部DB（`users`）
- 再利用できるもの（確かめた事実）: `PasswordPolicy`（12 文字以上・UTF-8 で 72 バイト以内）、bcrypt の cost の設定（既定 12）、`EmailAddress`（小文字・254 文字）、`Password`（`toString` は `***`）、`DummyPasswordHash`（存在しない利用者の照合の時間をそろえる）。`createUser` は `UserCreatedEvent` を同じトランザクションで知らせる。
- K-1（確かめた事実）: `users` の列は `user_id`・`email`・`password_hash`（NOT NULL、VARCHAR(100)）・`admin_flag`・`created_at` の5つだけ（`V2__u2_user_account.sql`）。言語・テーマ・文字の大きさ・状態（招待中・有効など）・招待の記録の列や表は無い。`User` のエンティティも同じ5項目で、変更のメソッド（setter やパスワードの変更）を持たない。`UserAccountService` の公開の操作は `verifyPassword`・`findById`・`existsByEmail`・`createUser(email, Password, admin)` の4つだけで、パスワードの変更・利用者の一覧・状態の変更は無い。ほかの機能へ渡す `UserSummary` は `userId`・`email`・`admin` だけ。
- K-1 の帰結（仮説）: パスワードの無い招待中の利用者を同じ表に置くには、V7 以降で `password_hash` の NOT NULL を外す・別の表に置く・使えない値を入れる、のどれかが要る。どれでも、招待中の利用者がログインできないことをログインとトークンの認証の両方で守る必要がある（K-2）。1つ前の版のアプリが動く後方互換の決まり（`team.md` の Deployment）から、足す列は NULL を許すか既定値付きにする必要があると見られる。前の版は `ddl-auto: validate` のため、列が増えても検証は通ると見られる（未検証）。
- 状態: at-risk（K-1） ／ 読みの深さ: 深い（全ファイル）

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`
- 責務: 対象DB（MySQL・MariaDB・PostgreSQL）の接続とスキーマの読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み

### dsl

- 場所: `dsl/{domain,parse,validate,service}`
- 責務: DSL（YAML）の安全な読み込み（大きさ・深さ・別名・タグの上限）、JSON Schema と意味の検証、適用中の DSL のモデルの保持と提供口。
- 状態: healthy ／ 読みの深さ: 流し読み

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`
- 責務: 既定の DSL の生成、投入・プレビュー・適用・破棄・履歴・ダウンロードの API（`/api/admin/dsl/**`）。管理者の API で「操作した人」を組み立てる見本として `web/DslRequestContextResolver.java` を読んだ。
- 依存: `dsl`・`targetdb`・`user.service`・`auth.domain`・`auth.web`・`common-*`・内部DB
- 状態: healthy ／ 読みの深さ: 流し読み（`DslRequestContextResolver` だけ深い）

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/**/testsupport/`（`HttpTestClient`・`TestDatabase`・`JsonLogRecords`・`MutableClock`・`AuthApi`・`PublicApiTestRules` など）
- 責務: 結合テストの HTTP の送り手、内部DB のテストデータの用意と片付け、ログの読み取り、時計の差し替え、テスト用の公開の決まり。
- 状態: healthy ／ 読みの深さ: 流し読み（`PublicApiTestRules` だけ深い）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/routing/`・`app/login-state/`
- 責務: 起動（`ThemeProvider` → `ToastProvider` → `ModalStackProvider` → `I18nProvider` → 登録 → ログイン状態 → 振り分けの順に包む）、URL の振り分け（`PUBLIC`・`LOGGED_IN`・`ADMIN`）、ログイン状態の受け渡し。
- 状態: healthy ／ 読みの深さ: 深い

### frontend-registry

- 場所: `app/registry/`・`app/navigation/`
- 責務: 機能ごとの `registration.ts` の読み込みと検査（重複は起動の失敗）、サイドバーとユーザーメニューの項目。ログイン不要の単独の画面（`access: 'PUBLIC'`、`layout: 'STANDALONE'`）とユーザーメニューの項目（`UserMenuItemRegistration`）を足せる。プリファレンスの画面と招待を受けた人の登録の画面は、この口で足せると見られる（仮説）。
- 状態: healthy ／ 読みの深さ: 深い（`types.ts`・`registrationModules.ts`・`navigationItems.ts`）

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`
- 責務: AppShell の配置、ログイン用・単独の画面のレイアウト、i18next による日英の文言。
- K-5（確かめた事実）: `I18nProvider` は `navigator.languages` から ja・en を起動時に1回だけ決め、切り替えの手段を持たない（`useMemo` の元は起動時の値だけ）。`<html lang>` もその値で決まる。`frontend-api-client` は `Accept-Language` を明示して付けないため、サーバーのエラーの文言はブラウザの既定の値で決まる。利用者ごとの言語は画面とサーバーのどちらにも無い。利用者の言語で表示するには、ログインの後に言語を切り替える口と、サーバーのエラーの言語をそろえる手段（`Accept-Language` を付けるなど）が要る（後半は仮説）。
- 状態: at-risk（K-5） ／ 読みの深さ: 深い（`ShellLayout`・`I18nProvider`・`i18n`・`resolveLanguage`）

### frontend-api-client

- 場所: `shared/api-client/`
- 責務: 同じオリジンの API の呼び出し、Bearer の付与、401 `AUTHENTICATION_REQUIRED` で更新を1回（同時の 401 は1つにまとめる）して送り直す、Problem Details の読み取り、ダウンロード。
- 状態: healthy ／ 読みの深さ: 深い

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログイン画面、アクセストークンと利用者をメモリだけに持つ（localStorage・sessionStorage・Cookie に置かない、`authSession.ts`）、起動時の更新によるログイン状態の復元、ログイン状態の提供元、ユーザーメニューのログアウト。ユーザーメニューに出す名前はメールアドレス（`loginStateProvider.ts` の `displayName`）で、利用者の表に名前の列は無い。
- 状態: healthy ／ 読みの深さ: 深い（`authSession`・`authApi`・`loginStateProvider`・`registration`。画面の部品は流し読み）

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理者向け領域 `/admin`（確認用の API を呼ぶだけの置き場）とサイドバーの項目。利用者の管理の画面は無い。
- 状態: healthy ／ 読みの深さ: 深い（`registration.ts`・`AdminAreaPage.tsx` だけ）

### frontend-feature-dsl

- 場所: `features/dsl/`
- 責務: DSL の管理画面 `/admin/dsl`。
- 状態: healthy ／ 読みの深さ: 流し読み（`registration.ts` だけ深い）

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `edb1f943c0e66293494fa974605f34fcd7e258d7`）。画面からは `file:` の依存で使う。
- 責務: デザインシステム（`ThemeProvider`・`useTheme`・AppShell・フォームの部品・Toast・Modal など）。
- K-4（確かめた事実）: `ThemeProvider` の軸は `theme`（`light`・`dark` の2値だけ、`ThemeMode`）・`brand`（blue・green・purple・orange）・`fontFamily`（sans・serif）・`fontSize`（sm・md・lg）。値は `localStorage` の `design-system-theme`・`design-system-brand`・`design-system-font-family`・`design-system-font-size` に軸ごとに保存し、`<html>` の `data-theme`（light のときは付けない）・`data-brand`・`data-font-family`・`data-font-size` に反映する。別のタブの変更は `storage` の出来事で追う。`prefers-color-scheme` は保存された値が無いときの初期値にだけ使い、動いている間の OS の切り替えには追従しない。画面のコードは `useTheme` をまだ使っておらず、`App.tsx` は `ThemeProvider` を引数なしで置いている。設定の値を画面に渡す API は無い。
- K-4 の帰結（仮説）: テーマの `system` は `ThemeMode` に無く、サブモジュールは直接変えられない（`project.md` の Forbidden）ため、frontend の側で `prefers-color-scheme` を見て `setTheme` を呼ぶ形になると見られる。`ThemeProvider` が値を localStorage にも保存するため、サーバーに保存した利用者の設定と、同じブラウザで前に使った別の利用者の値が食い違う場面を考える必要がある。ブランドカラーとフォントファミリーをインスタンス全体の固定の設定にするには、`application.yaml` の値を画面へ渡す道（ログインなしで読める API か、配信する `index.html` への埋め込みなど）が要り、値は make-you-chic-ui の4色・2値に限られる。文字の大きさも3値に限られる。
- 状態: at-risk（K-4） ／ 読みの深さ: 深い（`src/theme/` の `types`・`validation`・`storage`・`ThemeProvider` だけ。ほかは公開の一覧だけ）

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`.github/workflows/ci.yml`
- 責務: 1コマンドの検査 `./gradlew verify`（10 段）、WAR の組み立て（画面の `dist` を同梱）、lockfile による版の固定、依存の取得元を Maven Central だけに固定、カバレッジの下限、SpotBugs の関門、OSV-Scanner・Gitleaks、CI。
- 状態: healthy ／ 読みの深さ: 深い

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`
- 責務: WAR をコピーするだけのイメージ（`eclipse-temurin:25.0.4_7-jre-noble`、利用者 10001、JVM は `-XX:MaxRAMPercentage=50.0 -Duser.timezone=Asia/Tokyo`、追加の指定は `MASTERSMITH_JAVA_OPTIONS`）、`app` のサービス（CPU 既定 4、メモリ 既定 2g、ボリューム `mastersmith-data`、`.env` を env_file で読む、ヘルスチェック）、profile で起動する監視と見本の対象DB。メールの受け手のコンテナは無い。
- 状態: healthy ／ 読みの深さ: 深い（`app` のサービス）

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験と使い捨ての環境、otel-lgtm の手元の監視。
- 状態: healthy ／ 読みの深さ: 流し読み
