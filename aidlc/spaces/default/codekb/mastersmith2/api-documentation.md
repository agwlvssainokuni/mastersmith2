# API（mastersmith2）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は無い）。REST（JSON、Spring MVC）で、認証は `Authorization: Bearer <アクセストークン>`。セッションと CSRF の仕組みは無い（`config/SecurityConfig.java`）。`/api/**` の既定はログインが必要（`access/web/AdminApiDefaultAccess.java`）、`/api/admin` と `/api/admin/**` は管理者だけ（`access/domain/AdminPaths.java`）。エラーは RFC 9457 Problem Details に安定した `code` を足した形（`common/error`、1つの code に1つの状態コード）。

パスは `backend/src/main/java/cherry/mastersmith/` を省いて書く。

## 外部の API（HTTP）

| メソッド・パス | 実装 | アクセス | 読みの深さ |
|---|---|---|---|
| `POST /api/auth/login` | `auth/web/AuthController.java` | 公開 | 深い。失敗は理由によらず 401 `AUTHENTICATION_FAILED`（K-1） |
| `POST /api/auth/session/refresh` | 同上 | 公開・Cookie のリフレッシュトークン・Origin の確かめ | 深い。失敗は `REFRESH_FAILED` |
| `POST /api/auth/session/logout` | 同上 | 同上 | 深い |
| `GET /api/admin/check` | `access/web/AdminCheckController.java` | 管理者だけ | 深い。204。画面の管理の入口が使う |
| `GET /api/me/preferences`・`PUT /api/me/preferences`・`POST /api/me/password` | `user/web/MeController.java` | ログイン | 流し読み（名前だけ） |
| `POST /api/admin/invitations`・`GET /api/admin/invitations?page=`・`POST /api/admin/invitations/{invitationId}/resend`・`POST /api/admin/invitations/{invitationId}/cancel` | `invitation/web/InvitationAdminController.java` | 管理者だけ | 深い（K-5 の見本） |
| `POST /api/registration/verify`・`POST /api/registration/complete` | `invitation/web/RegistrationController.java` | 公開（差し込み口 order 310） | 流し読み |
| `/api/admin/dsl` の下の 10 本（状態・プレビュー・投入・破棄・生成・ダウンロード・適用・履歴・戻し・適用済みのダウンロード） | `dslmanage/web/DslAdminController.java` | 管理者だけ | 流し読み |
| `GET /api/appearance` | `appearance/web/AppearanceController.java` | 公開 | 流し読み |
| `GET /api/problems/{slug}` | `common/error/web/ProblemTypeController.java` | 公開 | 流し読み |
| `GET /dsl/dsl-schema-v1.json`・`GET /actuator/health`・SPA の配信 | 静的・Actuator | 公開 | 流し読み |

**利用者の管理の API は無い。** 利用者の一覧・管理者の印の変更・利用停止・ロックの解除のどれも、controller・service・repository のどの層にも無い。`user/repository/UserRepository.java` の問い合わせは `findByEmail`（43 行）・`existsByRedactedEmail`（55 行）・`updatePreferences`（70 行）・`updatePasswordHashIfUnchanged`（85 行）と `JpaRepository` の標準の操作だけで、`users` の索引は主キーとメールアドレスの一意の制約だけ（V2）。

## アプリの中の口（部品の間の契約）

| 口 | 場所 | 内容 |
|---|---|---|
| `UserAccountService` | `user/service/UserAccountService.java` | `verifyPassword`・`findById`・`existsByEmail`（文字列と `RedactedText`）・`findDisplayName`・`findLanguage`・`createUser`。戻り値の `UserSummary` は `toString` でメールアドレスと氏名を伏せる（K-8）。`auth`・`invitation`・`dslmanage` が使う |
| 出来事 `UserCreatedEvent` | `user/service/UserCreatedEvent.java` → `auth/service/LoginAttemptStateInitializer.java` | 同じトランザクション（`Propagation.MANDATORY`）でロックの状態の行を作る。`user` から `auth` への逆向きの知らせの前例（K-3） |
| 監査の出来事 | `AuthenticationEvent`・`AdminAccessDeniedEvent`・`PasswordChangedEvent`・招待と登録の出来事・`DslOperationEvent` → `audit/service/AuditEventListener.java` | 確定の後に記録（`architecture.md` の Interaction Diagrams 3、K-6） |
| 安全の決まりの差し込み口 `SecurityRuleContributor` | `common/security`、各機能の `web` | order は機能ごとに 100 台（auth 110・access 210・invitation 310・appearance 410。x00・x50 はテストの決まり） |

## K-5 一覧・ページ送り・操作・結果の型の前例（`invitation` の管理の API）

確かめた事実:

- 一覧: `GET /api/admin/invitations?page=`。`page` は文字列で受け、数でなければ業務処理が `ListResult.InvalidPage` を返し、controller が 400 `VALIDATION_FAILED` にする（`invitation/web/InvitationAdminController.java` 108〜112 行）。1ページは 20 件（`invitation/domain/InvitationPaging.java` の `PAGE_SIZE`、DB を使わない純粋な関数）。応答は `InvitationPageResponse`（`items`・`page`・`size`・`total`・`unavailableReasons` ほか）。
- 操作: `POST /api/admin/invitations/{invitationId}/resend`・`/cancel` の形（動詞の下位パス）。業務処理は sealed interface の結果の型（`InviteResult`・`ListResult`・`ResendResult`・`CancelResult`）を返し、controller が網羅の `switch` で応答か `BusinessException` に変える（例 `CancelResult.Cancelled` → 204、`CancelResult.NotFound` → `INVITATION_NOT_FOUND`、141〜145 行）。
- 操作した管理者の ID と送り手の情報は `invitation/web/InvitationRequestContextResolver.java` で読み、業務処理に渡す（`service.resend(actor, context.origin(request), invitationId)`）。
- 安全の決まり: `/api/admin/**` は `access` の決まりで管理者だけになるため、招待の管理の API は決まりを足していない。`invitation/web/InvitationSecurityContributor.java` が足すのは公開の2本（登録の完了）だけ。
- 一覧の「招待した管理者の氏名」は、ページの行ごとに `UserAccountService.findDisplayName` を利用者 ID ごとに1回呼ぶ（`invitation/service/InvitationService.java` 269・286 行）。

見立て（未検証）:

- 利用者の管理の API は、同じ形（`/api/admin/<資源>?page=` の一覧、`/{id}/<動詞>` の操作、結果の型と網羅の `switch`、機能ごとの ProblemType の一覧）で `/api/admin/**` に乗せれば、安全の決まりを足さずに済む。
- 利用者の一覧で、利用者（`user` の表）とロックの状態（`auth` の表）を別々に読むと、1ページ 20 件で読みが増える。件数が少ない前提なら問題になりにくいが、どちらの部品が合わせるかは境界の決まり（K-3、`dependencies.md`）で絞られる。
- 一覧・件数・状態の絞り込みの問い合わせは `UserRepository` に足すことになる。
