# API（mastersmith2）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は無い）。セッションは作らない。`@GetMapping`・`@PostMapping`・`@DeleteMapping`・`@RequestMapping` を全体で検索して数えた（開発担当のスキャン）。認証とアクセスの決まりは深く読み、DSL の API は流し読み（前の記録と開発担当のスキャンの数に合わせた）。

## 外部の API（HTTP）

| メソッド・パス | 実装 | アクセス | 要求 | 成功の応答 |
|---|---|---|---|---|
| `POST /api/auth/login` | `auth/web/AuthController` | 公開 | JSON（`email`・`password`） | 200 `TokenResponse`（`accessToken`・`expiresAt`・`user`（`email`・`admin`））＋リフレッシュの Cookie。失敗は理由によらず 401 `AUTHENTICATION_FAILED` |
| `POST /api/auth/session/refresh` | 同上 | 公開・Origin の一致 | リフレッシュの Cookie | 200 `TokenResponse`＋新しい Cookie。失敗は Cookie を消して 401 `REFRESH_FAILED` |
| `POST /api/auth/session/logout` | 同上 | 公開・Origin の一致 | リフレッシュの Cookie | 204＋Cookie の削除（期限切れでも呼べる） |
| `GET /api/admin/check` | `access/web/AdminCheckController` | 管理者のみ | なし | 204 |
| `/api/admin/dsl/**`（status・preview の GET・POST・DELETE・generate・preview の download・apply・history・history の restore・applied の download の 10 本） | `dslmanage/web/DslAdminController` | 管理者のみ | 投入は `application/yaml` の本文（最大 10MB） | 流し読みのため詳細は略。重い4つはアプリ全体で同時に1つ |
| `GET /api/problems/{slug}` | `common/error/web/ProblemTypeController` | 公開 | なし | 問題の種類の説明（日英） |
| エラーの道（2 本） | `common/error/web/ErrorPathController` | — | — | 共通の Problem Details |
| `GET /actuator/health` | Actuator（公開は health だけ、詳細なし） | 公開 | なし | 200 `UP` または 503 |
| `GET /dsl/dsl-schema-v1.json` ほか画面の静的なファイル | SPA の配信（`config/WebConfig.java`） | 公開 | なし | 見つからない画面の URL には `index.html` |

確かめた事実:

- 利用者に関わる API（利用者の一覧・登録・招待・自分の情報の取得・パスワードの変更・設定の保存）は1つも無い。ログイン中の利用者の情報は、ログインと更新の応答の `TokenResponse.user`（`email`・`admin` の2項目）でだけ返る。`GET /api/me` のような道は無い。
- 管理者の判定は、要求ごとに内部DB から読んだ `admin_flag` で行う（`architecture.md` の Interaction Diagrams 3）。

## アクセスの決まり（K-6）

`config/SecurityConfig.java` が次の順に当てる（確かめた事実）。

1. `/actuator/health`・`/api/problems/**` を公開。
2. 差し込み口 `SecurityRuleContributor` を order 順に当てる。`auth`（`AuthSecurityContributor`、order 110）が `/api/auth/login`・`/api/auth/session/**` を公開にし、トークンの認証を組み込む。`access`（`AdminSecurityContributor`、order 210）が `/api/admin` と `/api/admin/**` を管理者のみにする。同じ order が2つあると起動が失敗する（`common/security/SecurityExtensionValidator.java`）。
3. `ApiDefaultAccess` が1つあり（`access/web/AdminApiDefaultAccess.java`）、`requireAuthentication()` が true なので、`/api/**` の残りはログイン必須。
4. それ以外（画面の URL・静的なファイル）は公開。

K-6（今回の Intent に関わる所見）:

- 事実: ログインなしで呼ぶ新しい API（例: 招待を受けた人が登録を完了する API、招待の URL の値を確かめる API）は、上の公開の一覧に無いと 401 になる。公開にするには、新しい `SecurityRuleContributor`（空いている order）で `permitAll` を足すか、`auth` の決まりを変える必要がある。README の「API のアクセス制御（U3）」は公開の道を明示の一覧で管理する決まりで、足したら一覧も直す。
- 事実: `AuthSecurityContributor.bearerTokenResolver` は、パスが `/api/auth/` で始まる要求ではアクセストークンを**読まない**。`/api/auth/login`・`/api/auth/session/**` 以外を `/api/auth/` の下に置くと、ログイン必須のまま主体が作られず、常に 401 になる。ログインした利用者が呼ぶ API（パスワードの変更・プリファレンスの保存など）は `/api/auth/` の外に置く必要がある（アーキテクトがコードで確かめた）。
- 事実: 管理者のみの API（利用者の登録・招待の送信など）は `/api/admin/` の下に置けば、判定が自動で効く。管理者でない利用者には、存在しない管理 API も 403 になる。
- 事実: CSRF は無効で、アクセストークンは Authorization ヘッダーだけから読む（URL の問い合わせと本文からは読まない）。Cookie で認証する更新とログアウトだけ、`OriginVerifier` が Origin の一致を確かめる。
- 仮説: 招待の URL に入る値を Cookie ではなく本文で送る形なら、今の CSRF の無効のままでよいと見られる。Cookie を使うなら Origin の確かめが要る。

## 招待の URL の組み立て元（K-9）

- 事実: ベースURLの設定は `mastersmith.web.base-url`（環境変数 `MASTERSMITH_WEB_BASE_URL`、既定は空）だけで、エラー応答の `type` の URL と `OriginVerifier` の比べる相手に使う（`common/error/web/ProblemBaseUrlResolver.java`）。空のときは、要求のスキーム・`getServerName()`（Host ヘッダーの値）・ポートから組み立てる。転送元のヘッダーは `MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS=true` のときだけ使う（既定 false）。
- 仮説: 招待メールの URL を同じ仕組みで組み立てると、設定が空のときは管理者の要求の Host ヘッダーから URL が作られる。Host を偽った要求で別のサイトへの URL を送る危険を避けるには、メール用にはベースURLの設定を必須にする（無ければ送信を断るか起動を止める）ことが考えられる。
- 仮説: 招待の値を URL の問い合わせ（`?token=`）に入れると、Web のアクセスログ・トレースの属性に残りうる。問い合わせを外す仕組み（`UrlQueryStrippingObservationFilter`）はファイル名から存在が分かるが、中身は確かめていない。

## エラー応答の形

RFC 9457 Problem Details に `code` と `traceId` を足した形で、`@RestControllerAdvice` の1か所（`common/error/web/GlobalExceptionHandler.java`）と、フィルターの段階では `ErrorResponseWriter` が作る。`ProblemType` は `code`・状態コード・日英の題名・説明・対処を1つに固定し（1つの code は1つの状態コード）、機能ごとの `ProblemTypeCatalog` の Bean に置く（code と slug の重複は起動の失敗、`common/error/service/ProblemTypeRegistry.java`）。説明の言語は要求の `Accept-Language` で決まる（`common/error/web/ErrorResponseFactory.java`、既定は日本語）。応答に内部の例外メッセージを載せない。

## 内部の提供口（アプリの中の契約）

| 提供口 | 場所 | 使う側 |
|---|---|---|
| 利用者の照合・読み取り・有無・作成 | `user/service/UserAccountService`（`verifyPassword`・`findById`・`existsByEmail`・`createUser`）、戻り値は `UserSummary`・`PasswordVerification` | `auth`・`dslmanage` |
| 利用者の作成の出来事 | `user/service/UserCreatedEvent`（作成のトランザクションの中で知らせる） | `auth`（`LoginAttemptStateInitializer`） |
| 検証済みの利用者 | `auth/domain/AuthenticatedUser`（`userId`・`email`・`admin`） | `access`・`dslmanage` |
| 時計 | `java.time.Clock` の Bean（UTC、`auth/service/AuthClockConfig`） | 全体 |
| 監査の出来事 | `AuthenticationEvent`（auth）・`AdminAccessDeniedEvent`（access）・`DslOperationEvent`（dslmanage） | `audit` |
| 差し込み口 | `SecurityRuleContributor`・`ApiDefaultAccess`・`ErrorResponseWriter`・`ProblemTypeCatalog`・`RequestBodyLimitRoute`・`TraceIdProvider`（一覧は README の「後の単位が使う差し込み口」） | 各機能 |
| 画面の差し込み口 | `frontend/src/app/registry/types.ts` の `FeatureRegistration` | 画面の各機能 |
| 画面の API の呼び出し | `frontend/src/shared/api-client/`（Bearer の付与、401 `AUTHENTICATION_REQUIRED` で更新を1回して送り直す、Problem Details の `{ status, code }`）。`Accept-Language` は明示して付けない | 画面の各機能 |
