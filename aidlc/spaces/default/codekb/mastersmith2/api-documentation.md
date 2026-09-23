# API の文書（mastersmith2）

## 外部の API（REST）

すべて同じオリジンから配信する SPA が呼ぶ。エラーは RFC 9457 Problem Details に、画面が分岐に使う `code` を足した形式（`common/error/web/GlobalExceptionHandler.java`）。今回の範囲で新しい API は無い。

| メソッドとパス | 実装 | 概要 | DB の接続 | CPU・メモリの観点（今回） | k6 の場面 |
|---|---|---|---|---|---|
| `POST /api/auth/login` | `AuthController.login` → `LoginService.login` | ログイン。成功で `TokenResponse`（アクセストークン）とリフレッシュトークンの Cookie。失敗は理由によらず `AUTHENTICATION_FAILED` | 照合の短い借用のあと、確定の後の監査で同時に**2本**（成功・失敗とも） | bcrypt の照合1回（cost 12、約 278ms の CPU 時間）。F4 の対象（目標 p95 1 秒） | `loginSuccess`・`loginFailure` |
| `POST /api/auth/session/refresh` | `AuthController.refresh` → `TokenRefreshService.refresh` | アクセストークンの更新 | 1本（出来事を publish しない） | 高い負荷（毎秒約 3,000 件）で F3 が起きた場面。経路のコードは今回読んでいない | `refresh` |
| `POST /api/auth/session/logout` | `AuthController.logout` → `LogoutService.logout` | リフレッシュトークンの無効化 | 無効化したときは同時に**2本**（`LOGGED_OUT` の監査） | — | なし |
| `GET /api/admin/check` | `access/web/AdminCheckController.java` | 管理者の API に入れるかの確認（401/403/200） | 拒否のときの監査は1本（トランザクションの外で publish） | — | `adminCheck`・`forbidden` |
| `GET /api/problems/{slug}` | `common/error/web/ProblemTypeController.java` | エラーの種類の説明 | なし | — | なし |
| エラーの経路 | `common/error/web/ErrorPathController.java` | 既定のエラーの応答の置き換え | なし | — | なし |
| `GET /actuator/health` | Actuator ＋ `common/health/TimeBoundedDbHealthIndicator.java` | 状態だけを返す（詳細・部品は出さない。`application.yaml` 144〜167 行） | 同じプールから1本借りて `SELECT 1`。2 秒で打ち切り DOWN | コンテナのヘルスチェック（`compose.yaml` 40〜54 行、bash で `/dev/tcp` から呼ぶ）と起動の待ち（`--wait`）に使う | `health` |

画面側の呼び出し先は `frontend/src/features/auth/authApi.ts` に認証の3つのパスがある（前回の記録）。Actuator で Web に公開するのは `health` だけで、指標はプルではなく OTLP で送る（既定は無効）。

## 内部の API（アプリの出来事）

| 出来事 | 発行元 → 受け取り側 | 発行の場所 | 受け取りの形 |
|---|---|---|---|
| `AuthenticationEvent`（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`） | `auth.service` → `audit.service.AuditEventListener.onAuthenticationEvent` | トランザクションの中 | AFTER_COMMIT、同じスレッド、`AuditEventRecorder`（REQUIRES_NEW）で INSERT 1回 |
| `AdminAccessDeniedEvent`（`ACCESS_DENIED`） | `access.service.AccessDeniedEventPublisher` → `AuditEventListener.onAdminAccessDeniedEvent` | トランザクションの外 | `fallbackExecution = true` でその場で受け取る |
| `UserCreatedEvent` | `user.service` → `auth.service.LoginAttemptStateInitializer` | トランザクションの中 | `@EventListener` ＋ `Propagation.MANDATORY` |

契約として守られている性質（変えてはならないもの）は `code-quality-assessment.md` の「監査の修正が守るべき既存の決まりとテスト」に記す。

## 運用の口（環境変数。資源と性能に関わるもの）

アプリと compose が読む環境変数のうち、今回の範囲に関わるものだけを挙げる。全体の表は `README.md` の環境変数の節（165・169 行ほか）。

| 変数 | 既定 | 読む場所 | 影響 |
|---|---|---|---|
| `MASTERSMITH_CONTAINER_CPUS` | `4` | `compose.yaml` 59 行、`docker/perf/compose.yaml` 50 行 | コンテナの CPU の上限。VM の CPU より大きい値は Docker が受け付けない |
| `MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST` | `12` | `application.yaml` 64 行 | 照合1回の CPU 時間（cost が1増えると約2倍） |
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` | `30` | `application.yaml` 106 行 | 接続の数の上限 |
| `MASTERSMITH_IMAGE_TAG` | `local` | 両 compose | 使うイメージのタグ |
| `MASTERSMITH_PERF_ENV_FILE` | なし（必須） | `docker/perf/compose.yaml` 28 行 | 使い捨ての環境の一時の環境ファイル |
| `VUS`・`DURATION`・`SCENARIO` | `10`・`60s`・— | `perf/k6/scenarios.js` | k6 の同時の数・時間・場面 |
| （無し）メモリの上限・JVM の引数 | — | — | 変数の口が無い。`mem_limit: 1g` と `-XX:MaxRAMPercentage=75.0` はファイルに直書き |

## 永続化の契約（`audit_events`）

`backend/src/main/resources/db/migration/V4__u4_audit_event.sql`（前回の記録）。追記だけの表。主キーは DB の連番。列は `occurred_at`（UTC）・`event_type`・`result`・`entered_email`・`failure_reason`・`source_ip`・`user_agent`・`request_path`・`trace_id`。索引は `occurred_at` の1つだけ。スキーマの変更は前進のみ・後方互換。
