# 部品の一覧（mastersmith2）

健全性の評価: healthy（問題なし）／at-risk（条件しだいで問題が出る）／degraded（既知の不具合あり）。部品は見出しの名前で記す。今回（Intent `260923-colima-spec-up`）深く読んだ部品の見出しは、`reverse-engineering-timestamp.md` の Scope of Analysis の `components` と同じ名前である。前回（Intent `260923-audit-pool-exhaustion`）に深く読み、今回は再確認していない部品は、その次の節にまとめる。

## 今回深く読んだ部品

### LoginService
- 場所: `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`
- 責務: パスワードの照合（118 行、トランザクションと排他の外）→ `TransactionTemplate`（119 行）で行の排他・ロックの判定・失敗回数の更新・トークンの発行 → `AuthenticationEvent` の publish。
- 依存: UserAccountService、LoginAttemptStateRepository、RefreshTokenRepository、`AccessTokenService`、`ApplicationEventPublisher`、`Clock`。
- 資源: 照合1回（bcrypt cost 既定 12、約 278ms）が CPU の重い部分のすべて。同時の数が使える CPU の数を超えると、照合が順番待ちになり応答が伸びる。
- 健全性: **at-risk**（F4。CPU 2 で同時 10 件の p95 が約 1.6 秒。接続の2本使い（F2）はプールの上限 30 で緩和済みだが形は残る）

### mastersmith-db コネクションプール
- 場所: `backend/src/main/resources/application.yaml` 101〜108 行（HikariCP、Spring Boot の自動設定）
- 責務: 内部DB（組み込み H2）への接続のプール。上限 `${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}`、借りる待ち 5000ms。業務・監査・ヘルスチェックで共有。
- 健全性: **at-risk**（同時の数が上限 30 に達すると F2 が再び起きうる。README の既知の制約。設定の一覧は `architecture.md` の「接続のプールの現在の設定」）

### app コンテナ
- 場所: `Dockerfile`、`compose.yaml` の `app`（24〜67 行）、`.dockerignore`
- 責務: 実行可能 WAR を Temurin JRE 25 で動かす。専用の利用者 10001、`/app/data` をボリューム `mastersmith-data` に保存、`127.0.0.1:8080` に公開、ヘルスチェックは bash で `/actuator/health`。
- 資源の設定: `cpus: ${MASTERSMITH_CONTAINER_CPUS:-4}`、`mem_limit: 1g`（固定）、JVM は `-XX:MaxRAMPercentage=75.0`（最大ヒープ 768MB）だけ。`restart: "no"`。
- 依存: colima の VM（CPU 2・メモリ 2GiB）、`.env`（任意）。
- 健全性: **degraded**（F3。高い負荷でメモリの上限に達し OOMKilled。既定の `cpus` 4 は現状の VM の CPU 2 では起動できない値）

### mastersmith-perf 負荷試験環境
- 場所: `docker/perf/compose.yaml`、`perf/README.md`、`perf/k6/scenarios.js`
- 責務: 配備と同じイメージ・同じ上限の使い捨ての環境（別のプロジェクト名・別のボリューム・`127.0.0.1:18080`）を作り、k6（`grafana/k6:2.3.0`、同じ VM の中のコンテナ）で6つの場面に同時 10・60 秒の負荷をかけ、p95 を出す。仮の署名鍵・仮の利用者はリポジトリの外の一時ファイル。
- 依存: app コンテナのイメージ、H2 2.4.240 の jar（利用者の投入）、`htpasswd`、`openssl`。
- 健全性: **at-risk**（手順が CPU 2 を固定で書き、VM が 2GiB のため配備したアプリを止めて行う。k6 と対象が同じ VM の CPU を分け合う。末尾の F3 の注記は直した後に更新が要る）

### lgtm 監視コンテナ
- 場所: `compose.yaml` の `lgtm`（79〜103 行）、`docker/monitoring/`
- 責務: `grafana/otel-lgtm:0.33.1` で OTLP の受け手・Prometheus・Loki・Tempo・Grafana を1つで動かし、ファイルで入れたダッシュボードと警報（15 件）で手元の監視をする。profile `monitoring`、`127.0.0.1:3000`、閲覧だけ。
- 資源の設定: `mem_limit: 900m`（アプリ 1g と VM 約 2GiB の中で同居する前提のコメント）。
- 関係する警報: `ms-heap`（ヒープの使用率 > 0.85 が 5 分）、`ms-login-p95`（ログインの p95 > 1000ms が 5 分）、`ms-app-absent`（指標が 5 分届かない）、`ms-pool-pending`（プールの待ち）。コンテナのメモリ・CPU の指標は無い。
- 健全性: **at-risk**（VM 2GiB では余裕が少ない。README 232 行。ヒープ以外を含むメモリの上限への接近を捉えられない）

### otel-collector コンテナ
- 場所: `compose.yaml` の `otel-collector`（69〜74 行）、`docker/otel-collector/config.yaml`
- 責務: 外部エクスポートの確認用の受け手。`otel/opentelemetry-collector:0.161.0`、profile `observability`、HTTP 4318 で受けて debug に出すだけ。
- 資源の設定: 上限なし。
- 健全性: healthy（常時は起動しない）

### SecurityConfig
- 場所: `backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java`
- 責務: Spring Security のフィルターの連鎖（1つ、状態を持たない）と、U2・U3 が決まりを足す差し込み口。`/actuator/health`・`/api/problems/**` は認証なし。応答に CSP などのヘッダーを付ける。
- 健全性: healthy（資源の設定は持たない）

### WebConfig
- 場所: `backend/src/main/java/cherry/mastersmith/config/WebConfig.java`
- 責務: ビルドした SPA の配信。見つからない画面の URL は `/api/`・`/actuator/` の下でなければ `index.html` を返す。
- 健全性: healthy

### ObservabilityConfig
- 場所: `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java`
- 責務: OTLP の送信（トレース・ログ・指標）の組み立て。`mastersmith.observability.export.enabled`（既定 false）1つで切り替え、送信は上限付きの待ち行列（あふれたら捨てる）から要求と切り離して行う。送信の失敗は警告のログだけ。
- 健全性: healthy（待ち行列に上限があり、送信でメモリが際限なく増える形ではない）

### ForwardedHeaderConfig
- 場所: `backend/src/main/java/cherry/mastersmith/config/ForwardedHeaderConfig.java`
- 責務: `mastersmith.web.trust-forwarded-headers=true` のときだけ `ForwardedHeaderFilter` を登録する。既定では転送元のヘッダーを使わない。
- 健全性: healthy

### SecurityHeaderProperties
- 場所: `backend/src/main/java/cherry/mastersmith/config/SecurityHeaderProperties.java`
- 責務: `mastersmith.security.content-security-policy` の値を受ける `record`。
- 健全性: healthy

## 前回に深く読んだ部品（今回は再確認していない）

内容は前回の記録（コミット `6afbf97` 時点）のまま残す。F2 の修正（プールの上限 30）以外に変わったかは確かめていない。

| 部品 | 場所 | 責務 | 健全性（前回の評価と注記） |
|---|---|---|---|
| LogoutService | `backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java` | リフレッシュトークンの無効化と `LOGGED_OUT` の publish（`@Transactional` の中） | at-risk（F2 と同じ形の2本使い） |
| TokenRefreshService | `backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java` | リフレッシュトークンでアクセストークンを更新する（`@Transactional`、出来事なし） | healthy（接続は1本）。F3 が起きた `refresh` の場面の経路。メモリの伸びとの関係は未確認 |
| AuthController | `backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java` | `/api/auth` の3つの API の HTTP の受け渡し | healthy |
| LoginAttemptStateRepository | `backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java` | `login_attempt_states` の排他つきの読み取りと更新 | healthy |
| RefreshTokenRepository | `backend/src/main/java/cherry/mastersmith/auth/repository/RefreshTokenRepository.java` | `refresh_tokens` の保存・検索・無効化 | healthy |
| AuditEventListener | `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` | AFTER_COMMIT で同じスレッドで受け取り、追記する。失敗は ERROR 1件、200ms 超で WARN | at-risk（2本目の借用の起点。上限 30 で緩和） |
| AuditEventRecorder | `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java` | `REQUIRES_NEW` で1件追記する | at-risk（同上） |
| AuditEventRepository | `backend/src/main/java/cherry/mastersmith/audit/repository/AuditEventRepository.java` | `audit_events` への追記だけ | healthy |
| AuditConfig | `backend/src/main/java/cherry/mastersmith/audit/service/AuditConfig.java` | 監査の部品の設定（計時の差し替え口） | healthy |
| UserAccountService | `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java` | パスワードの照合（`verifyPassword`、トランザクションなし）、利用者の検索・作成。存在しない利用者でもダミーのハッシュで照合の時間をそろえる | healthy（照合の CPU 時間は LoginService の項を参照） |
| UserRepository | `backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java` | `users` の読み書き | healthy |
| AccessDeniedEventPublisher | `backend/src/main/java/cherry/mastersmith/access/service/AccessDeniedEventPublisher.java` | アクセス拒否の出来事をトランザクションの外で publish | healthy |

## 流し読みの部品（参考）

| 部品 | 場所 | 関係 |
|---|---|---|
| UserAccountConfig | `backend/src/main/java/cherry/mastersmith/user/service/` | `BCryptPasswordEncoder(properties.bcryptCost())` を作る（照合の cost の出どころ） |
| TimeBoundedDbHealthIndicator | `backend/src/main/java/cherry/mastersmith/common/health/` | 同じプールから1本借りる。枯渇中は DOWN になりうる（at-risk） |
| GlobalExceptionHandler ほか error | `backend/src/main/java/cherry/mastersmith/common/error/` | Problem Details の応答 |
| TraceAspect ほか observability | `backend/src/main/java/cherry/mastersmith/common/observability/` | トレースIDの元 |
| LoginAttemptStateInitializer | `backend/src/main/java/cherry/mastersmith/auth/service/` | `UserCreatedEvent` の受け取り（MANDATORY） |
| InitialAdminInitializer | `backend/src/main/java/cherry/mastersmith/user/service/` | 起動時の初期管理者の作成 |
| RefreshTokenCleanupJob | `backend/src/main/java/cherry/mastersmith/auth/service/` | 期限切れのリフレッシュトークンの掃除（定期） |
| access の web の部品 | `backend/src/main/java/cherry/mastersmith/access/web/` | 認可と 401/403 |
| SPA | `frontend/src/` | 画面 |
