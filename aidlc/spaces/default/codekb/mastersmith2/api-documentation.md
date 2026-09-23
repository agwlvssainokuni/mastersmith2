# API の文書（mastersmith2）

## 外部の API（REST）

すべて同じオリジンから配信する SPA が呼ぶ。エラーは RFC 9457 Problem Details に、画面が分岐に使う `code` を足した形式（`common/error/web/GlobalExceptionHandler.java`）。

| メソッドとパス | 実装 | 概要 | DB の接続（本 Intent の観点） |
|---|---|---|---|
| `POST /api/auth/login` | `AuthController.login`（98 行）→ `LoginService.login` | ログイン。成功で `TokenResponse`（アクセストークン）とリフレッシュトークンの Cookie。失敗は理由によらず `AUTHENTICATION_FAILED` | 照合の短い借用のあと、確定の後の監査で同時に**2本**（成功・失敗とも） |
| `POST /api/auth/session/refresh` | `AuthController.refresh`（114 行）→ `TokenRefreshService.refresh` | アクセストークンの更新 | 1本（出来事を publish しない） |
| `POST /api/auth/session/logout` | `AuthController.logout`（135 行）→ `LogoutService.logout` | リフレッシュトークンの無効化 | 無効化したときは同時に**2本**（`LOGGED_OUT` の監査） |
| `GET /api/admin/check` | `access/web/AdminCheckController.java` | 管理者の API に入れるかの確認（401/403/200） | 拒否のときの監査は1本（トランザクションの外で publish） |
| `GET /api/problems/{slug}` | `common/error/web/ProblemTypeController.java` | エラーの種類の説明 | なし |
| エラーの経路 | `common/error/web/ErrorPathController.java`（`@RequestMapping` 2 件） | 既定のエラーの応答の置き換え | なし |
| `GET /actuator/health` | Actuator ＋ `common/health/TimeBoundedDbHealthIndicator.java` | 状態だけを返す（詳細・部品は出さない） | 同じプールから1本借りて `SELECT 1`。2 秒で打ち切り DOWN |

画面側の呼び出し先は `frontend/src/features/auth/authApi.ts`（35〜41 行）に上の認証の3つのパスがある。

## 内部の API（アプリの出来事）

| 出来事 | 発行元 → 受け取り側 | 発行の場所 | 受け取りの形 |
|---|---|---|---|
| `AuthenticationEvent`（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`） | `auth.service` → `audit.service.AuditEventListener.onAuthenticationEvent` | トランザクションの中 | AFTER_COMMIT、同じスレッド、`AuditEventRecorder`（REQUIRES_NEW）で INSERT 1回 |
| `AdminAccessDeniedEvent`（`ACCESS_DENIED`） | `access.service.AccessDeniedEventPublisher`（53〜59 行）→ `AuditEventListener.onAdminAccessDeniedEvent` | トランザクションの外 | `fallbackExecution = true` でその場で受け取る |
| `UserCreatedEvent` | `user.service` → `auth.service.LoginAttemptStateInitializer` | トランザクションの中 | `@EventListener` ＋ `Propagation.MANDATORY`（同じトランザクションで行を作る） |

契約として守られている性質（修正でも変えてはならないもの）は `code-quality-assessment.md` の「修正が守るべき既存の決まりとテスト」に記す。

## 永続化の契約（`audit_events`）

`backend/src/main/resources/db/migration/V4__u4_audit_event.sql`。追記だけの表。主キーは DB の連番（追記の前に読み取りをしない）。列は `occurred_at`（UTC）・`event_type`・`result`・`entered_email`・`failure_reason`・`source_ip`・`user_agent`・`request_path`・`trace_id`。索引は `occurred_at` の1つだけ。スキーマの変更は前進のみ・後方互換。
