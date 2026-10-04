# API（mastersmith2）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は無い）。REST（JSON、Spring MVC）で、認証は `Authorization: Bearer <アクセストークン>`。セッションと CSRF の仕組みは無い。`/api/**` の既定はログインが必要、`/api/admin/**` は管理者だけ（決まりの並びと判定は `architecture.md` の Interaction Diagrams 1、K-33）。エラーは RFC 9457 Problem Details に安定した `code` を足した形（`common/error`、1つの code に1つの状態コード）。

パスは `backend/src/main/java/cherry/mastersmith/` を省いて書く。

## 外部の API（HTTP）

本数は controller ごとの対応づけの注釈（`@GetMapping` などメソッドの単位）を数えた値で、合わせて **32 本**（エラーの経路 `/error` と Actuator・画面の配信を除く）。開発担当の記録は総数を「29 本」と書いているが、内訳の合計は 32 で、今回の数え直しも 32 だった。

| まとまり | 実装 | 本数 | アクセス | 今回の読みの深さ |
|---|---|---|---|---|
| 認証 `POST /api/auth/login`・`/api/auth/session/refresh`・`/api/auth/session/logout` | `auth/web/AuthController.java` | 3 | 公開（更新とログアウトは Cookie のリフレッシュトークン） | 応答の形だけ深く。ログインと更新の応答 `TokenResponse` にログイン中の利用者 `CurrentUserResponse`（`email`・`admin`・`displayName`・`language`・`theme`・`fontSize`）を含む。ログイン中の利用者を返す単独の API（`GET /api/me` など）は無い（K-35） |
| 自分の設定 `GET`・`PUT /api/me/preferences`、`POST /api/me/password` | `user/web/MeController.java` | 3 | ログイン | 流し読み |
| 管理者の確かめ `GET /api/admin/check`（204） | `access/web/AdminCheckController.java` | 1 | 管理者だけ | 深い |
| 利用者の管理 `GET /api/admin/users`、`PUT .../{userId}/profile`、`POST .../{userId}/grant-admin`・`revoke-admin`・`suspend`・`resume`・`reset-login-failures` | `useradmin/web/UserAdminController.java` | 7 | 管理者だけ | 印と停止の業務の流れ（K-34）。引数と応答の項目は流し読み |
| 招待の管理 `/api/admin/invitations` の作成・一覧・送り直し・取り消し | `invitation/web/InvitationAdminController.java` | 4 | 管理者だけ | 流し読み |
| 登録 `POST /api/registration/verify`・`/complete` | `invitation/web/RegistrationController.java` | 2 | 公開（`InvitationSecurityContributor`、order 310） | 流し読み（決まりの部分だけ深く） |
| DSL の管理 `/api/admin/dsl/` の status・preview・generate・download・apply・history・restore・applied/download | `dslmanage/web/DslAdminController.java` | 10 | 管理者だけ | 流し読み |
| 見た目 `GET /api/appearance` | `appearance/web/AppearanceController.java` | 1 | 公開（`AppearanceSecurityContributor`、order 410） | 流し読み（決まりの部分だけ深く） |
| 問題の種類 `GET /api/problems/{slug}` | `common/error/web/ProblemTypeController.java` | 1 | 公開（`SecurityConfig` の公開の決まり） | 流し読み |

- 管理者だけの API は 22 本（check・利用者・招待・DSL）で、どれも「管理者なら全部できる」。操作ごと・画面ごとの権限の区別は無い（K-33）。
- 拒否の応答: 未認証・トークンの誤りは 401、管理者でない利用者の管理の API は 403（code `ACCESS_DENIED`、監査の理由 `NOT_ADMIN`）。403 の理由はこの1つだけ（`access/domain/AccessDeniedReason.java`）。
- メニュー・ナビゲーションを画面に渡す API は無い（K-37）。

## アプリの中の口（部品の間の契約）

| 口 | 場所 | 内容 |
|---|---|---|
| `SecurityRuleContributor` | `common/security/SecurityRuleContributor.java` | 機能ごとの追加のアクセスの決まり。`SecurityConfig` が order の小さい順に当てる。本番は auth 110・access 210・invitation 310・appearance 410（K-33） |
| `ApiDefaultAccess` | `common/security/ApiDefaultAccess.java` | `/api/**` の既定。実装は `access/web/AdminApiDefaultAccess.java` の1つだけで、ログイン必須 |
| 主体 `AuthenticatedUser` | `auth/domain/AuthenticatedUser.java` | `userId`・`email`・`admin`。要求ごとに DB から作る（K-32） |
| `UserAccountService` | `user/service/UserAccountService.java` | `findById`（認証の入口が使う）ほか。戻り値の `UserSummary` は `toString` でメールアドレスと氏名を伏せ、`admin`・`suspended` を持つ（前回までの記録と今回の `UserSummary.java`） |
| 管理者の行の排他と有効な管理者の集合 | `user/repository/UserRepository.java` の `findActiveAdminIds`（182 行）ほか | 最後の管理者の保護に使う（K-34） |
| 監査の出来事 | 各機能の出来事 → `audit/service/AuditEventListener.java` | 確定の後に追記。種類は `audit/domain/AuditEventType.java` の 22 個（K-38） |
| `ActiveDslModelProvider` | `dsl/service/ActiveDslModelProvider.java` | 適用中の DSL のモデル（下の K-37） |
| 画面の差し込み口 | `frontend/src/app/registry/types.ts` | 画面（ルート）・サイドバーの項目・ユーザーメニューの項目・ログイン状態の提供元の4つ（K-35） |

### K-37 N 階層のメニューの定義は、すでに DSL の中にある

確かめた事実:

- DSL の JSON Schema（`backend/src/main/resources/dsl/dsl-schema-v1.json`）の `menus` は「最上位のメニュー（N 階層）」の配列で、項目 `menuItem` は `label`（必須、表示名 ja・en）・`icon`（文字列か null）・`table`（文字列か null）・`items`（子の配列か null）を持ち、`additionalProperties: false`。
- モデルは `DslMenuItem(DisplayName label, String icon, String table, List<DslMenuItem> items)`（`dsl/domain/DslMenuItem.java` 29 行、Javadoc に「N 階層」）で、テーブルか子の少なくとも一方を持つ。意味の検証（`dsl/validate/DslSemanticValidator.java` 102〜117 行）は、指すテーブルがあること（`SEMANTIC_MENU_UNKNOWN_TABLE`）と空のまとまりでないこと（`SEMANTIC_MENU_EMPTY`）だけ。
- 深さの上限は DSL 全体の入れ子の深さ `DslFormat.MAX_DEPTH = 50`（`dsl/domain/DslFormat.java` 35 行）だけで、メニュー専用の上限は無い。`icon` は make-you-chic-ui のアイコンの名前の一覧（18 種類、K-36）と照らしていない。
- 既定の DSL の生成は、テーブルごとに1段のメニュー（`label` と `table`。アイコンなし）を作るだけ（`dslmanage/generate/DslTreeBuilder.java` 70〜77 行）。
- 適用中のモデルは `ActiveDslModelProvider.current()` で読める（契約 C8、Javadoc に「後続の Intent I・J・K が使う」）。アプリの中の読み手は `dsl` の内側（`ActiveDslModelStore`・`ActiveDslModelHolder`）を除くと `dslmanage/service/DslPreviewAnalysis.java` だけで、画面のナビゲーションには使われていない。適用で丸ごと差し替わる（読み手から見て一度に切り替わる）。
- 画面の DSL の管理画面は、プレビューのメニューの木を `MenuNode { label, table, children }`（`frontend/src/features/dsl/api/types.ts` 67〜72 行。`icon` は無い）で受け、`features/dsl/DslMenuTree.tsx` で描く。
- メニューの項目が指す「テーブルの画面」（業務データの一覧・詳細）はまだ無い（画面の経路の登録に該当が無い）。DSL の項目に権限（役割）の指定は無い。

見立て（未検証）:

- Intent I のメニューは、骨組みの静的な登録（機能の `sidebarItems`）と、DSL の動的なメニュー（テーブルへの項目）の2つの出どころを持つことになりうる。DSL のメニューを画面に渡す API（今は無い）、まだ無いテーブルの画面への行き先の扱い、適用で差し替わったときの画面の追従が論点になる。
- DSL の項目に役割の指定を足すかどうか（Intent F と I の結び付け方）は、JSON Schema の `additionalProperties: false` のため、Schema の版と既存の DSL の扱いに関わる。
- `icon` の値を make-you-chic-ui の名前に限るなら、検証を `DslSemanticValidator` に足す形と、画面で知らない名前を代わりのアイコンにする形がある。
