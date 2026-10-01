# Contract Design の質問

承認済みの単位（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/`）の依存の5本と、画面と管理の API の境界について、契約（何を・どの形で・どの手段で受け渡し、失敗したらどうするか）を決めます。書く前に、既存のコードの形を確かめました（`project.md` の学び: 既存の表・列挙・誤りの仕組みに触れる契約は、書く前にコードで確かめる）。

## 決まっていること（質問にしない）

- **外部の利用者は無い:** 管理の API を使うのは、このアプリの画面だけ。版の番号は付けず、画面と同じ WAR で一緒に変える（前の Intent と同じ）。
- **誤りの形:** RFC 9457 の Problem Details に、安定した `code` を足した形。1つの code に1つの状態コードを固定する（`common/error/domain/ProblemType`）。例外は `@RestControllerAdvice` の1か所で応答に変える。入力の誤りは既存の 400 `VALIDATION_FAILED`。
- **業務処理の結果:** 想定内の失敗は結果の型（sealed interface）で返し、controller が網羅の `switch` で応答か `BusinessException` に変える（招待の管理の API の前例、`team.md` の Code Style）。
- **応答の形の前例:** 招待の一覧は `{ items, page, size, total, ... }`、日時は ISO 8601 の時点（`Instant`）、項目名は camelCase（`InvitationPageResponse`・`InvitationResponse`）。
- **ログの決まり:** `TraceAspect` は web・service・domain・repository の層の引数と戻り値を TRACE で文字列にする。controller の引数も対象になるため、検索の文字やメールアドレスは、文字列にすると伏せ字になる型（既存の `RedactedText` の形）で受け渡す。
- **単位の間（同じアプリの中）の契約:** service の口（Java の型）と、出来事（Spring の出来事）で受け渡す。監査は出来事を受けて確定の後に記録する（ADR-006）。
- **403 の扱い:** `/api/admin/` の下で code が `ACCESS_DENIED` のときだけ、画面の骨組みが「権限が無い」として扱う（ドメイン設計の ApiClient）。

質問は4問です。

---

## Q1. 管理の操作の API の形

A. 招待の管理と同じく、操作ごとの下位パスへの POST にする（例: `POST /api/admin/users/{userId}/grant-admin`・`/revoke-admin`・`/suspend`・`/resume`・`/reset-login-failures`）。氏名・言語は `PUT /api/admin/users/{userId}/profile`
B. 状態ごとの資源への PUT にする（例: `PUT /api/admin/users/{userId}/admin` に `{ "admin": true }`、`PUT /api/admin/users/{userId}/suspended` に `{ "suspended": true }`）
X. Other (please specify)

[Answer]: A

## Q2. 操作の成功の応答

成功・失敗のどちらでも、画面は操作の後に一覧を読み直します（画面イメージの S5）。

A. 成功は本文なしの 204 にする（招待の取り消しと同じ。画面は一覧を読み直して最新の状態を見る）
B. 成功は 200 で、操作の後の利用者の行（一覧と同じ形）を返す
X. Other (please specify)

[Answer]: A

## Q3. 業務の理由の拒否の状態コード

拒否の理由ごとに別の code を持ち、1つの code に1つの状態コードを固定します。

A. 対象の利用者がいない は 404、それ以外（自分自身への操作・対象の利用者が停止中・変えるものが無い・最後の有効な管理者の保護・排他の待ちの上限切れ）は 409
B. 対象の利用者がいない は 404、自分自身への操作 は 403、それ以外は 409
X. Other (please specify)

[Answer]: A

## Q4. 一覧の検索の文字の受け渡し

検索の文字にはメールアドレスが入りうるため、アプリのログ・トレースの属性・監査に出さない決まりです（要件の NFR3）。

A. `GET /api/admin/users?page=1&q=...` の問い合わせの文字列で受け、controller で伏せ字の型に包んで受け渡す（GET のまま。要求の URL がログやトレースに出ないことを確かめる）
B. `POST /api/admin/users/search` の本文で受ける（URL に検索の文字が出ない。一覧なのに POST になる）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、契約の計画:

- Q1 A: 管理の API は `/api/admin/users` の下に置く。一覧は `GET /api/admin/users`。操作は操作ごとの下位パスへの POST（`/{userId}/grant-admin`・`/revoke-admin`・`/suspend`・`/resume`・`/reset-login-failures`）、氏名と言語は `PUT /{userId}/profile`（`{ displayName, language }`）
- Q2 A: 操作の成功は本文なしの 204。氏名と言語の変更も 204 にそろえる（画面は送った値を知っており、一覧を読み直す）
- Q3 A: 業務の拒否は理由ごとに code を分け、対象の利用者がいない は 404、それ以外（自分自身への操作・対象の利用者が停止中・変えるものが無い・最後の有効な管理者の保護・排他の待ちの上限切れ）は 409。403 は既存の `ACCESS_DENIED` だけに使う。code の名前の案: `USER_NOT_FOUND`・`USER_ADMIN_SELF_OPERATION`・`USER_ADMIN_TARGET_SUSPENDED`・`USER_ADMIN_NO_CHANGE`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_BUSY`（既存の code と重ならないことを確かめた）
- Q4 A: 検索は `GET /api/admin/users?page=1&q=...`。controller で伏せ字の型に包んで業務処理へ渡す。トレースの属性の URL の問い合わせの部分は既存の `UrlQueryStrippingObservationFilter` が消し、403 の監査は既存の `AdminAccessDeniedEvent` が問い合わせの部分を除いたパスを残すため、これを漏えいのテストで確かめる
- 一覧の応答: `{ items, page, size, total }`。行は `userId`・`email`・`displayName`・`language`・`admin`・`suspended`・`locked`・`lockedUntil`（ロック中だけ）・`resettable`・`registeredAt`・`self`（自分の行。M6 の押せない表示に使う）。失敗回数・ハッシュ値・トークンは持たない（FR1.6）。`toString` はメールアドレスと氏名を伏せる
- 単位の間の契約（7本）: C1 U3→U1（停止の状態の変更と、利用者のリフレッシュトークンのまとめての無効化の口。同じトランザクション）、C2 U3→U2（Paging の純粋な関数）、C3 U5→U3（上の HTTP の API）、C4 U5→U4（ApiClient の 403 の扱い・AppFrame の表示・自分の氏名と言語だけを当てる口。既存の `applyUserPreferences` はテーマと文字の大きさも要るため、氏名と言語だけの口を足す）、C5 U5→U2（UiPaging）、C6 U3→AuditLog（管理の操作の出来事。種類5つ・失敗の理由5つ。名前は ADR-006 のとおり機能設計で決め、この契約では項目と送る時点だけを決める）、C7 U1→AuditLog（ログインの失敗の理由「停止中」）
- 外部の利用者は無く、版の番号は付けない。誤りは Problem Details と `code`、入力の誤りは 400 `VALIDATION_FAILED`

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
