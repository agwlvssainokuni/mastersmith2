# API（mastersmith2）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は無い）。REST（JSON、Spring MVC）で、認証は `Authorization: Bearer <アクセストークン>`。セッションと CSRF の仕組みは無い。`/api/**` の既定はログインが必要、`/api/admin/**` は管理者だけ。エラーは RFC 9457 Problem Details に安定した `code` を足した形（`common/error`、1つの code に1つの状態コード）。

パスは `backend/src/main/java/cherry/mastersmith/` を省いて書く。

## 外部の API（HTTP）

対応づけの注釈の数は開発担当の検索による概数（クラスの単位の `@RequestMapping` を含む）。「深い」の行は今回のコードで確かめた範囲を書いた。

| メソッド・パス | 実装 | アクセス | 読みの深さ |
|---|---|---|---|
| `POST /api/auth/login` | `auth/web/AuthController.java` → `auth/service/LoginService.java` | 公開 | 業務処理を深く（K-26、`architecture.md` の Interaction Diagrams 2）。失敗は理由によらず 401 |
| `POST /api/auth/session/refresh`・`POST /api/auth/session/logout` | 同上（`TokenRefreshService.java` ほか） | 公開・Cookie のリフレッシュトークン | 更新の停止の判定の範囲だけ |
| `GET /api/me/...`・`PUT /api/me/...`・`POST /api/me/password` | `user/web/MeController.java` | ログイン | 流し読み（4 本） |
| `GET /api/admin/users`・`PUT /api/admin/users/{userId}/profile`・`POST /api/admin/users/{userId}/grant-admin`・`/revoke-admin`・`/suspend`・`/resume`・`/reset-login-failures` | `useradmin/web/UserAdminController.java` | 管理者だけ | `Busy` から 409 `USER_ADMIN_BUSY` への変換の範囲だけ（K-27、Interaction Diagrams 3）。一覧の引数と応答の項目は読んでいない |
| `/api/admin/invitations` の下（招待・一覧・送り直し・取り消し）・`/api/registration/**`（確かめ・完了） | `invitation/web/` | 管理者だけ・公開 | 流し読み |
| `/api/admin/dsl` の下の 10 本 | `dslmanage/web/DslAdminController.java` | 管理者だけ | 流し読み |
| `GET /api/admin/check` | `access/web/` | 管理者だけ | 流し読み |
| `GET /api/appearance` | `appearance/web/` | 公開 | 流し読み |
| `GET /api/problems/{slug}` | `common/error/web/` | 公開 | 流し読み |
| `/error` | `common/error/web/ErrorPathController.java` | フィルターやコンテナで起きた例外の受け口 | 深い（K-28、Interaction Diagrams 4）。5xx は ERROR「想定外のエラーが起きました」を原因つきで出す |
| `GET /actuator/health`・SPA の配信 | Actuator・静的 | 公開 | 流し読み |

## 運用の口（HTTP の外）

| 口 | 場所 | 内容 |
|---|---|---|
| 起動時の初期管理者の作成 | `user/service/InitialAdminInitializer.java`、環境変数 `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` | 同じメールアドレスの利用者がいなければ管理者を作る。既にいる利用者を救う（停止を解く・印を付ける）口は無く、監査にも残らない（K-25） |
| HikariCP の JMX | `docker/hikari-pool.sh`・`docker/jmx`（名前だけ） | 接続プールの運用の道具（前回までの記録） |

## アプリの中の口（部品の間の契約）

| 口 | 場所 | 内容 |
|---|---|---|
| `UserAccountService` | `user/service/UserAccountService.java` | `verifyPassword`（`users` を1回読み bcrypt で照合）・`findById`・`existsByEmail`・`createUser`・`lockAdminRowsInIdOrder`・`lockUserRow` ほか。戻り値の `UserSummary` は `toString` でメールアドレスと氏名を伏せ、`suspended` を持つ |
| 出来事 `UserCreatedEvent` | `user/service/UserCreatedEvent.java` → `auth/service/LoginAttemptStateInitializer.java` | 同じトランザクションでロックの状態の行を作る。受け手はこの1つだけで、監査は受けない（K-25） |
| 監査の出来事 | 各機能の出来事 → `audit/service/AuditEventListener.java` → `audit/service/AuditEventRecorder.java` | 確定の後に `REQUIRES_NEW` で追記。失敗は呼び出し元に伝えない。種類は `audit/domain/AuditEventType.java` の 20 種類 |
| 行の排他の結果 | `common/persistence/RowLockAttempt.java`・`RowLockFailures.java` | `Acquired`・`Busy` の結果の型と、失敗の見分けと WARN（K-27） |
| 待ち合わせの口 | `useradmin/service/UserAdminBarrier.java` | 本番は何もしない（`NoOpUserAdminBarrier`）。結合テストが差し替えて同時の重なりを作る |
