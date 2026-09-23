# API（mastersmith2）

## 外部の API（HTTP）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は置いていない）。セッションは作らない。

| メソッド・パス | 実装 | 認可 | 要求 | 成功の応答 | 主なエラー |
|---|---|---|---|---|---|
| `POST /api/auth/login` | `auth.web.AuthController` | 公開 | JSON `LoginRequest { email, password }`（どちらも必須） | 200 `TokenResponse { accessToken, expiresAt, user { email, admin } }`＋リフレッシュの Cookie | 400 `VALIDATION_FAILED`、401 `AUTHENTICATION_FAILED`（理由によらず同じ） |
| `POST /api/auth/session/refresh` | `AuthController` | 公開・Origin の確認 | リフレッシュの Cookie | 200 `TokenResponse`＋新しい Cookie | 401 `REFRESH_FAILED`（Cookie の削除つき）、403 `ORIGIN_NOT_ALLOWED` |
| `POST /api/auth/session/logout` | `AuthController` | 公開・Origin の確認 | リフレッシュの Cookie（無くてもよい） | 204＋Cookie の削除 | 403 `ORIGIN_NOT_ALLOWED` |
| `GET /api/admin/check` | `access.web.AdminCheckController` | 管理者のみ | なし | 204 | 401 `AUTHENTICATION_REQUIRED`、403 `ACCESS_DENIED` |
| `GET /api/problems/{slug}` | `common.error.web.ProblemTypeController` | 公開 | `Accept` で HTML／JSON、`Accept-Language` で日英 | 問題の種類の説明（`ProblemTypeResponse` または HTML） | 404 |
| `/error`（GET・HEAD・OPTIONS・POST・PUT・PATCH・DELETE） | `common.error.web.ErrorPathController` | 公開 | サーブレットの段階のエラーの転送 | 共通の Problem Details | — |
| `GET /actuator/health` | Spring Boot Actuator＋`common.health.TimeBoundedDbHealthIndicator` | 公開 | なし | 200 `{"status":"UP"}`（状態だけ） | 503（内部DBの確認が制限時間 2 秒を超えた・失敗） |
| `/**`（画面） | `config.WebConfig` | 許可 | — | `classpath:/static/` のファイル。`/api/`・`/actuator/` 以外の見つからない URL は `index.html` | — |

### リフレッシュトークンの Cookie

名前 `mastersmith_refresh`、`HttpOnly`・`Secure`・`SameSite=Strict`・`Path=/api/auth/session`、`Max-Age` はリフレッシュトークンの有効期限（既定 24 時間）。削除は同じ名前・同じ Path で `Max-Age=0`（`auth.web.RefreshCookies`）。

### アクセストークン

JWT（HS256、Nimbus）。`Authorization: Bearer` のヘッダーからだけ取り出し、フォームの本文・URL の問い合わせからは取り出さない。`/api/auth/**` では取り出さない（期限切れのトークンが付いていても、ログイン・更新・ログアウトを妨げない）。有効期限の既定は 5 分（`mastersmith.auth.access-token-ttl`）。

### アクセスの決まりの順（`config.SecurityConfig`）

1. `/actuator/health`・`/api/problems/**` は公開（U1）
2. `auth.web.AuthSecurityContributor`（order 110）: `/api/auth/login`・`/api/auth/session/**` は公開、Bearer の検証
3. `access.web.AdminSecurityContributor`（order 210）: `/api/admin`・`/api/admin/**` は管理者のみ、401・403 の処理を U3 のものにする
4. `access.web.AdminApiDefaultAccess`: `/api/**` の残りはログイン必須
5. それ以外（画面の配信）は許可

したがって、新しい API は `/api/**` に置けば既定でログインが必要になり、`/api/admin/` の下に置けば管理者のみになる。401／403 の応答とアクセス拒否の監査も、既存の仕組みで付く。

### エラー応答の形

RFC 9457 Problem Details（`application/problem+json`）に拡張の項目を2つ足した形である（`common.error.web.ErrorResponseFactory`）。

| 項目 | 内容 |
|---|---|
| `type` | `<ベースURL>/api/problems/<slug>` |
| `title`・`detail` | `Accept-Language` に合わせた日本語／英語。`detail` は1つの文字列 |
| `status` | HTTP の状態コード |
| `instance` | 要求のパス |
| `code` | 画面が分岐に使う安定したコード（`^[A-Z][A-Z0-9_]*$`） |
| `traceId` | 要求のトレースID（ある場合） |

今ある `code` の一覧:

| code | 状態 | 持ち主 |
|---|---|---|
| `VALIDATION_FAILED`・`MALFORMED_REQUEST` | 400 | 共通 |
| `NOT_FOUND` | 404 | 共通 |
| `METHOD_NOT_ALLOWED` | 405 | 共通 |
| `NOT_ACCEPTABLE` | 406 | 共通 |
| `PAYLOAD_TOO_LARGE` | 413 | 共通（`RequestSizeLimitFilter` と `MaxUploadSizeExceededException`） |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 共通 |
| `INTERNAL_ERROR` | 500 | 共通 |
| `AUTHENTICATION_FAILED`・`AUTHENTICATION_REQUIRED`・`REFRESH_FAILED` | 401 | `auth` |
| `ORIGIN_NOT_ALLOWED` | 403 | `auth` |
| `ACCESS_DENIED` | 403 | `access` |
| `REQUEST_REJECTED` | 400 | `access`（正規化されていないパス） |

入力の検証エラー（`VALIDATION_FAILED`）も、項目ごとのエラーの一覧を持たない。応答に内部の例外メッセージ・スタックトレースは載せない（`server.error.include-*` もすべて無効）。

### 要求の本文の上限

`mastersmith.web.max-request-body-size`（既定 1MB、環境変数 `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE`）を `common.web.RequestSizeLimitFilter` がすべての要求に当て、超えたら 413 `PAYLOAD_TOO_LARGE`。マルチパートの上限の設定は `application.yaml` に無い（Spring Boot の既定のまま）。

## 画面から呼んでいる API

| 呼び出し元 | API |
|---|---|
| `frontend/src/features/auth/authApi.ts` | ログイン・更新・ログアウトの3件 |
| `frontend/src/features/admin/adminApi.ts` | `GET /api/admin/check`（管理者向け領域を表示するたび） |

画面の URL: `/login`（`features/auth`、STANDALONE・PUBLIC・role LOGIN）、`/admin`（`features/admin`、SHELL・ADMIN、サイドバー「管理」order 200、`visibleWhen: 'ADMIN'`）、ホームと「ページが見つかりません」（`app/pages`）。

## 内部の差し込み口（後の機能が使う契約）

README の「後の単位が使う差し込み口」に一覧がある。コードで確かめた型は次のとおり。

| 差し込み口 | 型・場所 | 契約 |
|---|---|---|
| セキュリティの決まり | `common.security.SecurityRuleContributor` | `getOrder()` と `contribute(HttpSecurity)`。order の小さい順に当てる。ヘッダー・セッション・CSRF は変えない |
| `/api/**` の既定 | `common.security.ApiDefaultAccess` | 0個か1個（2個以上で起動失敗）。今は `AdminApiDefaultAccess`（ログイン必須） |
| フィルターの段階のエラー応答 | `common.security.ErrorResponseWriter` | `write(request, response, ProblemType)`。共通の Problem Details を書く |
| 業務エラー | `common.error.domain.BusinessException`＋`ProblemType`＋機能ごとの `ProblemTypeCatalog` の Bean | code の重複で起動失敗。日英の title・description・resolution を持つ |
| トレースID | `common.observability.TraceIdProvider` | 今の要求のトレースID（`Optional`） |
| 時計 | `Clock` の Bean（`auth.service.AuthClockConfig`、UTC） | 時刻はここから取る |
| 認証の主体 | `auth.domain.AuthenticatedUser`（userId・email・admin） | 要求ごとに内部DBから読んだ値 |
| 認証の出来事 | `auth.domain.AuthenticationEvent` | 業務のトランザクションの中で知らされる。受け取りは確定の後 |
| アクセス拒否の出来事 | `access.domain.AdminAccessDeniedEvent` | 401／403 の処理で知らされる（トランザクションの外） |
| 画面の機能の登録 | `frontend/src/app/registry/types.ts` の `FeatureRegistration` | `featureId`・`routes`（path・screen・layout `SHELL`/`STANDALONE`・access `PUBLIC`/`LOGGED_IN`/`ADMIN`）・`sidebarItems`・`userMenuItems`・`loginStateProvider`（最大1つ）・`messages`（ja・en をそろえる、鍵は `<featureId>.` で始める） |
| API の呼び出し | `frontend/src/shared/api-client/apiClient.ts` の `apiFetch`・`apiRequest` | 同じオリジン、Bearer の付与、401 / `AUTHENTICATION_REQUIRED` で更新1回と送り直し1回。失敗は `ApiError { kind, status, code? }` |

## 無いもの

対象DB（`mastersmith.target-db.*`）・DSL の生成・読み込み・検証・プレビュー・適用の API は無い。ファイルの受け取り（マルチパート）を使う API も、今は1つも無い。
