# 部品の一覧（mastersmith2）

見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回のコード知識ベースに合わせ、前回の後に作られた `targetdb`・`dsl`・`dslmanage`・`frontend-feature-dsl` を足した。

状態は healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。TD の番号は `code-quality-assessment.md` の「技術的負債」を指す。「読みの深さ」は今回のスキャンでの扱いで、深いは一部のファイルだけを深く読んだものを含む。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス（実行可能 WAR と外部のサーブレットコンテナの両方で起動）。
- 依存: Spring Boot
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて設定の `application.yaml`・`logback-spring.xml`）
- 責務: Spring Security のフィルターの連鎖、画面の配信、転送元ヘッダー、外部エクスポートの組み立て（`ObservabilityConfig`: 有効時だけ OTLP のログの出力をルートのロガーに足す）。ログの形式と水準の設定。
- 依存: `common-security`・`common-web`・`common-observability`
- 状態: at-risk（TD-1 キーと値を送らない、TD-4 Hibernate の案内の水準の指定が無い） ／ 読みの深さ: 深い（`ObservabilityConfig.java`・`application.yaml`・`logback-spring.xml`。ほかは流し読み）

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: Problem Details（`code`・`traceId` 付き）、`BusinessException`、`ProblemTypeRegistry`、`GlobalExceptionHandler`（`@RestControllerAdvice` の1か所）。
- 依存: `common-i18n`・`common-observability`
- 状態: healthy ／ 読みの深さ: 流し読み

### common-security

- 場所: `common/security/`
- 責務: セキュリティの差し込み口（`SecurityRuleContributor`・`ApiDefaultAccess`・`ErrorResponseWriter`）と起動時の検査。
- 依存: Spring Security
- 状態: healthy ／ 読みの深さ: 流し読み

### common-web

- 場所: `common/web/`
- 責務: キャッシュの指定のフィルター、要求の本文の大きさの上限、`mastersmith.web.*` の設定の型。
- 依存: `common-security`
- 状態: healthy ／ 読みの深さ: 流し読み

### common-health

- 場所: `common/health/`
- 責務: 制限時間付きの内部DB の確認（`/actuator/health`）。
- 依存: 内部DB
- 状態: healthy ／ 読みの深さ: 流し読み

### common-i18n

- 場所: `common/i18n/`
- 責務: `Accept-Language` から表示言語（ja・en）を決める。
- 依存: なし
- 状態: healthy ／ 読みの深さ: 流し読み

### common-observability

- 場所: `common/observability/`（テストの `common/observability/ExternalExportIT`・`common/testsupport/LogEvents` を含む）
- 責務: トレースIDの参照、送るトレースの消毒、メソッドの呼び出しの追跡。
- 依存: Micrometer Tracing・OpenTelemetry
- 状態: at-risk（`ExternalExportIT` は `/v1/logs` が届くことだけを確かめ、秘密の値が入らないことは `/v1/traces` だけで確かめる。TD-1） ／ 読みの深さ: 流し読み（`ExternalExportIT` の外部エクスポートの部分だけ深い）

### auth

- 場所: `auth/{domain,service,repository,web}`
- 責務: ログイン（ロックの判定、ダミーの行による存在の秘匿）、アクセストークンとリフレッシュトークン、ログアウト。ロックの状態の行の作成（`LoginAttemptStateInitializer`、利用者の作成と同じトランザクション）と、行が無いときのその場の作成（`LoginService.lockUserRow`）。
- 依存: `user`・`common-error`・`common-security`・`common-observability`・内部DB（`login_attempt_states`・`refresh_tokens`）
- 知らせる出来事: `AuthenticationEvent`
- 状態: degraded（TD-2 行が無い利用者の同時の初めてのログインが 500 になりうる） ／ 読みの深さ: 深い（`LoginService`・`LoginAttemptStateInitializer`・`LoginAttemptStateRepository`・V3 の該当部分。ほかは流し読み）

### access

- 場所: `access/{domain,service,web}`
- 責務: `/api/admin/**` を管理者のみにする決まり、`/api/**` の既定をログイン必須にする、401・403 の処理、アクセス拒否の出来事。
- 依存: `auth`・`common-error`・`common-security`・`config`
- 知らせる出来事: `AdminAccessDeniedEvent`
- 状態: healthy ／ 読みの深さ: 流し読み

### audit

- 場所: `audit/{domain,service,repository}`
- 責務: 認証・アクセス拒否・DSL の操作の出来事を受け、確定の後に `audit_events` へ別トランザクションで追記する。書き込みの失敗は受け止めて ERROR を1件出す（記録しようとした全項目をキーと値で載せる）。
- 依存: `auth`・`access`・`dslmanage` の出来事の型、内部DB
- 状態: at-risk（TD-1 でキーと値を外へ送ると、失敗の ERROR の入力されたメールアドレス・送り元の IP・User-Agent が送られる） ／ 読みの深さ: 深い（`AuditEventListener.java` の 100〜175 行だけ。ほかは流し読み）

### user

- 場所: `user/{domain,service,repository}`
- 責務: 利用者、パスワードの照合、初期管理者の自動作成（`InitialAdminInitializer`）。
- 依存: 内部DB（`users`）
- 状態: at-risk（TD-1 でキーと値を外へ送ると、初期管理者のメールアドレスの INFO が送られる） ／ 読みの深さ: 流し読み（`InitialAdminInitializer.createIfNeeded` だけ深い）

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`
- 責務: 対象DB（MySQL・MariaDB・PostgreSQL）の接続（`TargetDataSourceConfig`、設定が不正でも起動を続ける）とスキーマの読み取り（`JdbcTargetSchemaReader`）。
- 依存: 対象DB（`MASTERSMITH_TARGET_DB_*`）
- 状態: healthy（TD-6 の見本の対象DB の秘密情報はコンテナの設定の問題で、この部品のコードではない） ／ 読みの深さ: 流し読み

### dsl

- 場所: `dsl/{domain,parse,validate,service}`
- 責務: DSL のモデルの型、YAML の安全な読み込み（大きさ・深さ・別名・タグの上限）、JSON Schema と意味の検証、適用中のモデルの保持と提供。
- 依存: アプリの中の依存は無い（SnakeYAML・JSON Schema の検証の部品）
- 状態: healthy ／ 読みの深さ: 流し読み

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`
- 責務: 既定の DSL の生成、投入・プレビュー・適用・破棄・履歴・ダウンロード（`DslLifecycle`・`DslRecordStore`）、重い操作の同時の数の制限、操作ごとの指標とログ（`DslOperationMetrics`）。
- 依存: `dsl`・`targetdb`・`user`・`auth`・`common-error`・`common-web`・`common-i18n`・内部DB（`dsl_previews`・`dsl_applied_revisions`）
- 知らせる出来事: `DslOperationEvent`
- 状態: healthy（TD-1 で Loki から絞り込めないのは `DslOperationMetrics` のキーと値） ／ 読みの深さ: 流し読み（`DslOperationMetrics.record` だけ深い）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/routing/`・`app/navigation/`
- 責務: 起動、URL の振り分け、サイドバーの組み立て。
- 依存: `frontend-registry`・`make-you-chic-ui`
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-registry

- 場所: `app/registry/`
- 責務: 機能の登録の型と自動の読み込み、決まり違反の検査。
- 依存: なし
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/login-state/`・`app/pages/`・`app/testing/`
- 責務: レイアウト（AppShell の中と外）、表示言語、ログイン状態、共通の画面。
- 依存: `frontend-registry`・`make-you-chic-ui`
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`
- 責務: 同じオリジンの API の呼び出し、トークンの付与と更新・送り直し、`ApiError`。
- 依存: なし
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログイン画面、トークンの保持、ログアウト。
- 依存: `frontend-api-client`・`frontend-registry`・`make-you-chic-ui`
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理者向け領域 `/admin`。
- 依存: `frontend-api-client`・`frontend-registry`・`make-you-chic-ui`
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-dsl

- 場所: `features/dsl/`
- 責務: DSL の管理画面（`DslAdminPage`、状態・投入・プレビュー・違いの表・誤りの一覧・履歴、確かめの表示 `DslConfirmDialog`）。
- 依存: `frontend-api-client`・`frontend-registry`・`make-you-chic-ui`（`Modal` は `DslConfirmDialog.tsx` 108 行だけ、閉じるボタンの出る `Alert` は `DslAdminPage.tsx` 134 行だけ）
- 状態: at-risk（TD-7 Modal・Alert の直しの取り込みと使い方の変更。`DslAdminPage.test.tsx` 535 行が閉じるボタンの名前「閉じる」に依存） ／ 読みの深さ: 深い（`DslConfirmDialog.tsx` だけ。ほかは流し読み）

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール。checkout のコミット `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`）
- 責務: デザインシステム（AppShell・Button・FormField・Modal・Alert・Toast・Table など）。
- 依存: React
- 状態: at-risk（TD-7 今の checkout の `Modal`・`Alert` は閉じるボタンが `aria-label="閉じる"` 固定、`Modal` に `aria-describedby` の口が無い。直した版への固定先の更新が要る） ／ 読みの深さ: 流し読み（`Modal.tsx`・`Alert.tsx` の Props と閉じるボタンの行だけ）

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/`・`config/`・`.github/`・`.gitmodules`・`frontend/package.json`・`frontend/vite.config.ts`・`frontend/vitest.config.ts`
- 責務: 1コマンドの検査 `./gradlew verify`（段 0〜9）、サブモジュールのビルドと変更の無さの確認（`vendorBuild`・`vendorUnchanged`）、WAR の組み立て、lockfile による版の固定、CI。
- 依存: Gradle 9.7.1・npm（Node.js 24）・Gitleaks・OSV-Scanner・コンテナの実行環境（対象DB の結合テスト）
- 状態: healthy ／ 読みの深さ: 深い

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`・`.dockerignore`
- 責務: WAR をコピーするだけの1段のイメージ、`app` のサービス（`127.0.0.1:8080`、ボリューム、ヘルスチェック、CPU 既定 4、メモリ 既定 1g、`.env` の全体を `env_file` で読む）、profile で起動する `otel-collector`・`lgtm`・見本の対象DB 3種類。
- 依存: Docker（colima）
- 状態: at-risk（TD-5 メモリの上限の既定 1g、TD-6 `app` が見本の対象DB の管理者のパスワードを環境変数で持つ） ／ 読みの深さ: 深い

### perf-and-monitoring

- 場所: `perf/`・`docker/`（`perf/compose.yaml`・`otel-collector/`・`monitoring/`・`targetdb/`・`check-container-limits.sh`）
- 責務: k6 の負荷の試験と使い捨ての環境、OTLP の受け手、手元の監視（ダッシュボードと警報）、見本の対象DB の初期化、コンテナの上限の確かめ。
- 依存: `container-runtime`
- 状態: degraded（TD-3 `dslMixed` が同じ利用者を2つの VU に割り当てうる。あわせて TD-5 の既定 1g を前提にした確かめ） ／ 読みの深さ: 深い（`perf/k6/scenarios.js`・`docker/perf/compose.yaml`・`docker/otel-collector/config.yaml`。ほかは流し読み）
