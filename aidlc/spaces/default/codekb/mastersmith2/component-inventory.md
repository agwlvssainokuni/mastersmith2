# 部品の一覧（mastersmith2）

健全性の評価: healthy（問題なし）／at-risk（条件しだいで問題が出る）／degraded（既知の不具合あり）。深く読んだ部品は見出しの名前で記す（`reverse-engineering-timestamp.md` の Scope of Analysis の `components` と同じ名前）。流し読みの部品は末尾の表にまとめる。

## 深く読んだ部品

### LoginService
- 場所: `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`
- 責務: パスワードの照合（トランザクションの外）→ `TransactionTemplate` で行の排他・ロックの判定・失敗回数の更新・トークンの発行 → `AuthenticationEvent` の publish。
- 依存: UserAccountService、LoginAttemptStateRepository、RefreshTokenRepository、`AccessTokenService`、`ApplicationEventPublisher`、`Clock`。
- 健全性: **degraded**（F2。確定の後の監査で接続を2本同時に持つ）

### LogoutService
- 場所: `backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java`
- 責務: リフレッシュトークンの無効化と `LOGGED_OUT` の publish（`@Transactional` の中）。
- 依存: RefreshTokenRepository、UserAccountService、`ApplicationEventPublisher`。
- 健全性: **at-risk**（F2 と同じ形の2本使い。同時のログアウトが多いと同じく尽きうる）

### TokenRefreshService
- 場所: `backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java`
- 責務: リフレッシュトークンでアクセストークンを更新する（`@Transactional`、出来事なし）。
- 依存: RefreshTokenRepository、UserAccountService、LoginService（トークンの発行の再利用）、`Clock`。
- 健全性: healthy（接続は1本。ただしプールが尽きている間は巻き込まれる）

### AuthController
- 場所: `backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java`
- 責務: `/api/auth` の3つの API の HTTP の受け渡し（DTO の変換、Cookie、状態コード）。
- 依存: LoginService、TokenRefreshService、LogoutService。
- 健全性: healthy

### LoginAttemptStateRepository
- 場所: `backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java`
- 責務: `login_attempt_states` の行の排他つきの読み取り（`lockForUpdate`・`lockDummyForUpdate`）と更新。
- 健全性: healthy

### RefreshTokenRepository
- 場所: `backend/src/main/java/cherry/mastersmith/auth/repository/RefreshTokenRepository.java`
- 責務: `refresh_tokens` の保存・ハッシュでの検索・無効化（`revokeIfActive`）。
- 健全性: healthy

### AuditEventListener
- 場所: `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`
- 責務: `AuthenticationEvent`・`AdminAccessDeniedEvent` を AFTER_COMMIT（`fallbackExecution = true`、`@Order(HIGHEST_PRECEDENCE)`）で同じスレッドで受け取り、監査イベントを組み立てて追記する。失敗を受け止めて ERROR 1件、200ms 超で WARN。
- 依存: AuditEventRecorder、`LongSupplier`（計時）。
- 健全性: **degraded**（F2 の2本目の借用の起点）

### AuditEventRecorder
- 場所: `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java`
- 責務: `REQUIRES_NEW` で監査イベントを1件追記する（例外は受け止めない）。
- 依存: AuditEventRepository。
- 健全性: **degraded**（元の接続を持ったまま新しい接続を借りる）

### AuditEventRepository
- 場所: `backend/src/main/java/cherry/mastersmith/audit/repository/AuditEventRepository.java`
- 責務: `audit_events` への追記（`save`）。変更・削除は持たない。
- 健全性: healthy

### AuditConfig
- 場所: `backend/src/main/java/cherry/mastersmith/audit/service/AuditConfig.java`
- 責務: 監査の部品の設定（遅い書き込みの計時の差し替え口）。
- 健全性: healthy

### UserAccountService
- 場所: `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java`
- 責務: パスワードの照合（`verifyPassword`、トランザクションなし）、利用者の検索・作成。
- 依存: UserRepository、`PasswordEncoder`、`DummyPasswordHash`（存在しない利用者でも照合の時間をそろえる）、`ApplicationEventPublisher`、`Clock`。
- 健全性: healthy

### UserRepository
- 場所: `backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java`
- 責務: `users` の読み書き。
- 健全性: healthy

### AccessDeniedEventPublisher
- 場所: `backend/src/main/java/cherry/mastersmith/access/service/AccessDeniedEventPublisher.java`
- 責務: 管理者の API へのアクセス拒否の出来事をトランザクションの外で publish する。
- 健全性: healthy（接続は1本）

### mastersmith-db コネクションプール
- 場所: `backend/src/main/resources/application.yaml` 97〜105 行（HikariCP 7.0.2、Spring Boot の自動設定）
- 責務: 内部DB（組み込み H2）への接続のプール。最大 10 本、借りる待ち 5000ms。業務・監査・ヘルスチェックで共有。
- 健全性: **at-risk**（2本使いの経路と組み合わさると枯渇する。設定の詳細は `architecture.md` の「接続のプールの現在の設定」）

## 流し読みの部品（参考）

| 部品 | 場所 | 本 Intent との関係 |
|---|---|---|
| TimeBoundedDbHealthIndicator | `backend/src/main/java/cherry/mastersmith/common/health/` | 同じプールから1本借りる。枯渇中は DOWN になりうる（at-risk） |
| GlobalExceptionHandler ほか error | `backend/src/main/java/cherry/mastersmith/common/error/` | Problem Details の応答 |
| TraceAspect ほか observability | `backend/src/main/java/cherry/mastersmith/common/observability/` | 監査の行のトレースIDの元 |
| LoginAttemptStateInitializer | `backend/src/main/java/cherry/mastersmith/auth/service/` | `UserCreatedEvent` の受け取り（MANDATORY） |
| InitialAdminInitializer | `backend/src/main/java/cherry/mastersmith/user/service/` | 起動時の初期管理者の作成 |
| RefreshTokenCleanupJob | `backend/src/main/java/cherry/mastersmith/auth/service/` | 期限切れのリフレッシュトークンの掃除（定期） |
| access の web の部品 | `backend/src/main/java/cherry/mastersmith/access/web/` | 認可と 401/403 |
| SecurityConfig ほか | `backend/src/main/java/cherry/mastersmith/config/` | SecurityFilterChain など |
| SPA | `frontend/src/` | 画面（`app`・`features/auth`・`features/admin`・`shared/api-client`） |
