# Contract Summary — user-admin

出典:
- 単位: `aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`
- 依存: `.../units-generation/unit-of-work-dependency.md`（以下 `...` は `aidlc/spaces/default/intents/260930-user-admin/inception`）
- 部品と ADR: `.../domain-design/components.md`・`decisions.md`
- 要件: `.../requirements-analysis/requirements.md`
- ストーリー: `.../user-stories/stories.md`
- 画面の操作: `.../refined-mockups/interaction-spec.md`
- この段の答え: `contract-design-questions.md`（Q1〜Q4、すべて A。まとめの確認は Looks correct）

既存の形は次のコードで確かめた。招待の管理の API は `InvitationAdminController`・`InvitationPageResponse`・`InvitationResponse`。誤りの形は `ProblemType`・`AccessProblemTypes`。監査は `AuditEventType`・`AuditFailureReason` と、V6・V7 の `actor_user_id`・`target_user_id`。トレースは `TraceAspect`・`UrlQueryStrippingObservationFilter`・`AdminAccessDeniedEvent`。画面は `apiClient.ts`・`DisplaySettingsProvider.tsx`。

## 共通の決まり

- **エラー応答**: 既存の RFC 9457 Problem Details に `code` と `traceId` を足した形で、`@RestControllerAdvice` の1か所で作る。
  - 1つの `code` は1つの状態コードに固定し、機能ごとの `XxxProblemTypes` と `XxxProblemTypeCatalog` に置く。
  - 説明文は要求の `Accept-Language`（ja・en）で決まる。
  - 応答に載せないもの: 内部の例外のメッセージ、パスワードのハッシュ値、トークン、ロックの判定の生の値（`project.md` の Forbidden、FR1.6）。
- **業務の拒否の状態コード**（Q3 A）: 対象の利用者がいないときだけ 404、それ以外の業務の拒否は 409。403 は既存の `ACCESS_DENIED`（管理者でない）だけに使い、画面の「権限が無い」の扱い（C4）と混ざらないようにする。入力の誤りは既存の 400 `VALIDATION_FAILED`。
- **版**: URL に版を入れない。画面と API は同じ WAR で同時に変わる。外部の利用者は無い。
- **アプリの中の部品の間**: 想定内の失敗は結果の型（sealed interface）で返し、controller が網羅の `switch` で応答か `BusinessException` に変える（招待の管理の API の前例）。想定外の失敗だけを例外にする。
- **個人に関する値の受け渡し**: `TraceAspect` は web・service・domain・repository の層の引数と戻り値を TRACE で文字列にする。そのため、メールアドレス・氏名・検索の文字は、文字列にすると伏せ字になる型（`RedactedText` の形）か、`toString` で伏せる record で受け渡す（AC1.1.6）。
- **認可**: `/api/admin/users` の下は既存の AccessControl で管理者だけに許す（未認証は 401、管理者でないは 403 `ACCESS_DENIED`、FR8.2）。停止中の管理者は U1 の入口で 401 になる（FR3.8）。
- **時刻**: 日時は ISO 8601 の UTC（`Z` 付き）で渡し、画面が利用者の言語の書式とブラウザの時間帯で表示する。

## Contracts

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | U1 u1-user-suspension | U3 u3-user-admin-api | アプリの中の呼び出し（Java の型、同じトランザクション） | U1 |
| C2 | U2 u2-shared-paging | U3 u3-user-admin-api（と既存の Invitation） | アプリの中の呼び出し（純粋な関数） | U2 |
| C3 | U3 u3-user-admin-api | U5 u5-user-admin-ui | HTTP（REST、JSON、管理者だけ） | U3 |
| C4 | U4 u4-admin-forbidden-ui | U5 u5-user-admin-ui（と既存の AdminArea・DslAdminUi・InvitationUi） | 画面の中の呼び出し（TypeScript の関数・部品） | U4 |
| C5 | U2 u2-shared-paging | U5 u5-user-admin-ui（と既存の InvitationUi） | 画面の中の呼び出し（純粋な関数） | U2 |
| C6 | U3 u3-user-admin-api | 既存の AuditLog | アプリの中の出来事（確定の後に記録） | U3 |
| C7 | U1 u1-user-suspension | 既存の AuditLog | アプリの中の出来事（既存のログインの失敗の出来事に理由を足す） | U1 |
| C8 | U3 u3-user-admin-api（既存の UserAccount・Authentication に足す口） | U3 の UserAdministration | アプリの中の呼び出し（Java の型、同じトランザクション） | U3 |

文字の代替: 依存の向きは `unit-of-work-dependency.md` と同じ。U3 は C1・C2 を使い、U5 は C3・C4・C5 を使う。C6・C7 は既存の AuditLog へ知らせる。C8 は U3 の中の部品の境界（`useradmin` から `user`・`auth` への向き、ADR-001）で、単位の間の依存ではないが、`auth.repository` などに手が入る口のため契約として決める（レビューの R-02）。

## C1: 利用停止の状態とトークンのまとめての無効化（U1 → U3）

```yaml
contract: user-suspension
kind: java-interface
provider: U1
operations:
  UserAccount（user パッケージ。置き場の細部は U1 の機能設計）:
    isSuspended:
      description: 利用者の今の停止の状態。U1 が users に足す列（V9、既存の利用者は false）
      returns: boolean
    setSuspended:
      description: 停止の状態を変える。呼び出し元のトランザクションに入る。拒否の判定はしない（判定は U3 の UserAdministration）
      input: { userId: long, suspended: boolean }
      returns: void
  Authentication（auth パッケージ）:
    revokeAllRefreshTokens:
      description: 利用者のリフレッシュトークンをすべて無効にする（ADR-003、FR3.3）
      input: { userId: long }
      returns: { revoked: int }    # 無効にした件数。0 でも成功
      transaction: 呼び出し元と同じ（MANDATORY。止める操作と同じ確定に入る）
behaviour:
  - 3つの入口（ログインの照合・トークンの更新・アクセストークンの認証）は U1 の中で停止を判定し、U3 からは呼ばない
  - 停止を解いても、止める前のリフレッシュトークンは戻らない（FR3.3）
  - 止める操作とトークンの更新が同時に重なったときの隙は塞がない（ストーリーの M8 B、ADR-003）
  - 個人に関する値（メールアドレス・氏名）を受け渡さず、利用者 ID だけを渡す
```

- U3 は C1 を UserAdministration の止める・解く処理からだけ呼ぶ。ほかの機能から停止の状態を変える口は作らない（FR8.3）。
- 有効な管理者の数え上げと、管理者の行を決まった順で排他する口は U3 が UserAccount に足す（`unit-of-work.md` の U3 の境界）。C1 には含めず、C8 で決める。

## C2: ページ送りの計算（U2 → U3・Invitation）

```yaml
contract: paging
kind: java-pure-functions
package: cherry.mastersmith.common.paging
moved_from: cherry.mastersmith.invitation の InvitationPaging（4つの口をそのまま移す。レビューの R-03）
operations:
  Paging.parsePage:
    description: 要求の page の文字列を検証する
    input: { raw: "string?" }
    returns: OptionalInt
    rules:
      - raw が null（page の指定なし）なら 1
      - 1〜9 桁の数字だけを受け、1 以上なら その値
      - それ以外（空・数字でない・10 桁以上の桁あふれ・0）は empty。呼び出し元が VALIDATION_FAILED にする（AC1.1.5）
  Paging.pageOf:
    description: 一覧の中の位置（1 始まり）が載るページの番号（招待の INVITATION_ALREADY_PENDING の page に使う既存の口）
    input: { position: long }   # 1 以上。1 未満は IllegalArgumentException
    returns: int
  Paging.offsetOf:
    description: ページの番号から読み始めの位置を決める
    input: { page: int }        # 1 以上。1 未満は IllegalArgumentException
    returns: long               # (page - 1) * 20
constants:
  PAGE_SIZE: 20
behaviour:
  - 最後のページより後のページの番号は拒否せず、全体の件数つきの空の一覧になる（呼び出し元が offsetOf で読んだ結果。AC1.1.5、差7）
  - DB に触れず、時刻にも依存しない（性質ベースのテストの対象）
```

- 口の名前・引数・結果・例外は、既存の `InvitationPaging` と同じにする。招待は `InvitationPaging` を消して `Paging` を呼ぶ形に切り替え、招待の一覧の振る舞い（入力の誤り・空のページ・招待中の行のページ）を変えない。招待の既存のテストが通ったままであることで確かめる。

## C3: 利用者の管理の API（U3 → U5）

```yaml
openapi: 3.1.0
info: { title: MasterSmith 利用者の管理の API（管理者だけ）, version: "1" }
servers:
  - url: /api/admin/users
components:
  securitySchemes:
    bearer: { type: http, scheme: bearer }
  schemas:
    AdminUser:
      type: object
      required: [userId, email, displayName, language, admin, suspended, locked, resettable, registeredAt, self]
      properties:
        userId: { type: integer }
        email: { type: string }
        displayName: { type: string }
        language: { type: string, enum: [ja, en] }
        admin: { type: boolean, description: 管理者の印 }
        suspended: { type: boolean, description: 利用停止中か }
        locked: { type: boolean, description: 今の時刻で判定したロック中か（FR1.2） }
        lockedUntil: { type: string, format: date-time, description: ロック中のときだけ。解除の予定の時刻 }
        resettable: { type: boolean, description: 失敗回数を戻す操作を出せるか（失敗回数が1回以上。M2 B） }
        registeredAt: { type: string, format: date-time }
        self: { type: boolean, description: 操作している管理者自身の行か（M6 B の押せない表示に使う） }
      additionalProperties: false   # 失敗回数・ハッシュ値・トークン・ダミーの行の印は持たない（FR1.6）
    AdminUserPage:
      type: object
      required: [items, page, size, total]
      properties:
        items: { type: array, items: { $ref: "#/components/schemas/AdminUser" } }
        page: { type: integer, minimum: 1 }
        size: { type: integer, const: 20 }
        total: { type: integer, minimum: 0, description: 検索の条件に当たる全体の件数 }
    ProfileRequest:
      type: object
      required: [displayName, language]
      properties:
        displayName: { type: string, description: 自分のプリファレンスと同じ検証（空にできない・長さの上限。FR6.2） }
        language: { type: string, enum: [ja, en] }
    Problem:
      description: 既存の Problem Details（type・title・status・detail・instance・code・traceId）
      type: object
      required: [code]
      properties:
        code: { type: string }
  responses:                     # 各操作に共通の応答（レビューの R-05）
    Unauthenticated: { description: "未認証・無効なトークン・停止中の利用者（401 AUTHENTICATION_REQUIRED、FR3.4・FR3.8）" }
    Forbidden: { description: "管理者でない（403 ACCESS_DENIED）。監査に「アクセスの拒否」として残る（FR7.3）" }
    BadUserId: { description: "userId が整数でない（400 VALIDATION_FAILED、TypeMismatchException）。監査に残さない" }
security:
  - bearer: []
paths:
  /:
    get:
      description: 利用者を登録した順（古い順）に 20 件ずつ。検索は大文字小文字を区別しない部分一致で、% と _ は文字どおり（FR1.3・FR1.4）。閲覧は監査に残さない
      parameters:
        - { name: page, in: query, schema: { type: string, default: "1" }, description: C2 の parsePage で検証する }
        - { name: q, in: query, required: false, schema: { type: string }, description: "メールアドレスか氏名の一部。長さの上限は機能設計で決める（FR1.5）。controller の引数は String ではなく伏せ字の型（SearchText）で受ける（Q4 A、レビューの R-01）。空なら絞らない。〔Should〕" }
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/AdminUserPage" } } } }
        "400": { description: "ページの番号・検索の文字の誤り（VALIDATION_FAILED）。監査に残さない" }
        "401": { $ref: "#/components/responses/Unauthenticated" }
        "403": { $ref: "#/components/responses/Forbidden" }
  /{userId}/grant-admin:
    post:
      description: 管理者の印を付ける（FR2.1）
      responses:
        "204": { description: 付けた }
        "400": { $ref: "#/components/responses/BadUserId" }
        "401": { $ref: "#/components/responses/Unauthenticated" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { description: USER_NOT_FOUND }
        "409": { description: "USER_ADMIN_SELF_OPERATION・USER_ADMIN_TARGET_SUSPENDED・USER_ADMIN_NO_CHANGE（すでに管理者）・USER_ADMIN_BUSY" }
  /{userId}/revoke-admin:
    post:
      description: 管理者の印を外す（FR2.1・FR2.4・FR2.5）
      responses:
        "204": { description: 外した }
        "400": { $ref: "#/components/responses/BadUserId" }
        "401": { $ref: "#/components/responses/Unauthenticated" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { description: USER_NOT_FOUND }
        "409": { description: "USER_ADMIN_SELF_OPERATION・USER_ADMIN_TARGET_SUSPENDED・USER_ADMIN_NO_CHANGE（管理者でない）・USER_ADMIN_LAST_ADMIN・USER_ADMIN_BUSY" }
  /{userId}/suspend:
    post:
      description: 利用を止め、リフレッシュトークンをすべて無効にする（FR3.1・FR3.3、C1）
      responses:
        "204": { description: 止めた }
        "400": { $ref: "#/components/responses/BadUserId" }
        "401": { $ref: "#/components/responses/Unauthenticated" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { description: USER_NOT_FOUND }
        "409": { description: "USER_ADMIN_SELF_OPERATION・USER_ADMIN_NO_CHANGE（すでに停止中）・USER_ADMIN_LAST_ADMIN・USER_ADMIN_BUSY" }
  /{userId}/resume:
    post:
      description: 停止を解く（FR3.1・FR3.5）
      responses:
        "204": { description: 解いた }
        "400": { $ref: "#/components/responses/BadUserId" }
        "401": { $ref: "#/components/responses/Unauthenticated" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { description: USER_NOT_FOUND }
        "409": { description: "USER_ADMIN_SELF_OPERATION・USER_ADMIN_NO_CHANGE（停止していない）・USER_ADMIN_BUSY" }
  /{userId}/reset-login-failures:
    post:
      description: ログインの失敗回数を 0 に戻し、ロック中ならロックも解く（FR5.1）。ロックの状態の行が無い利用者に行を作らない
      responses:
        "204": { description: 戻した }
        "400": { $ref: "#/components/responses/BadUserId" }
        "401": { $ref: "#/components/responses/Unauthenticated" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { description: USER_NOT_FOUND }
        "409": { description: "USER_ADMIN_NO_CHANGE（行が無い・失敗回数が 0、FR5.2）・USER_ADMIN_BUSY（排他の待ちの上限切れ、AC4.1.11）" }
  /{userId}/profile:
    put:
      description: 氏名と言語を変える（FR6.1）。自分自身も変えられる。今と同じ値でも成功（FR6.6）。監査に残さない（FR6.4）
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/ProfileRequest" } } } }
      responses:
        "204": { description: 変えた（Q2 A。画面は送った値を知っており、一覧を読み直す） }
        "400": { description: "入力の誤り・userId が整数でない（VALIDATION_FAILED、既存の項目ごとの誤りの形）。監査に残さない" }
        "401": { $ref: "#/components/responses/Unauthenticated" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { description: USER_NOT_FOUND }
```

- **拒否の判定の順**（FR7.5）: 対象の利用者がいない → 自分自身への操作 → 対象の利用者が停止中（印の付け外しだけ、M7 B）→ 変えるものが無い → 最後の有効な管理者の保護。最初に当たった理由1つの code で応答し、同じ理由を C6 で監査に残す。
- **排他の待ちの上限切れ**（`USER_ADMIN_BUSY`）は業務の理由ではなく、判定の順の外にある。行の排他（C8）を取れなかった時点で、判定の前に止まる。状態は変わらない。監査に残すか、残すならどの理由かは U3 の機能設計で決める（レビューの R-04、Open questions）。
- **当たる理由の組み合わせ**: 各操作で返りうる code は上の表のとおり。
  - 自分自身への失敗回数の戻しは、自分は失敗回数を持ってログインしている前提で拒否しない。氏名と言語の変更も自分自身に許す（FR6.1）。
  - 停止中の利用者への停止の解除・失敗回数の戻しは許す（M7 B は印の付け外しだけ）。
- **成功の応答**（Q2 A）: 操作はすべて本文なしの 204。画面は成功・失敗のどちらでも一覧を読み直す（S5）。
- **ページの番号の型**: `page` は既存の招待の一覧と同じく文字列で受け、C2 で検証する（1 未満・空・桁あふれの判定を C2 の1か所にまとめるため。整数でない値の結び付けの誤りは、既存の `GlobalExceptionHandler` が `TypeMismatchException` を `VALIDATION_FAILED` にする）。
- **漏えい**（AC1.1.6）: `AdminUser` の応答の record は `toString` でメールアドレスと氏名を伏せる（`InvitationResponse` と同じ）。
  - 検索の文字 `q` は、controller のメソッドの引数の時点で伏せ字の型 `SearchText`（`toString` が値を伏せる record）として受ける（レビューの R-01）。`TraceAspect` は web の層の引数も TRACE で文字列にするため、`String` で受けて中で包む形ではログに出る。要求の文字列から `SearchText` へは Spring の型の変換（`Converter<String, SearchText>`）で変え、変換の中では値をログに出さない。業務処理・UserAccount（C8）へも `SearchText` のまま渡す。置き場（`common` か `useradmin.domain` か）は U3 の機能設計で決める。
  - トレースの属性の URL の問い合わせの部分は、既存の `UrlQueryStrippingObservationFilter` が消す。403 の監査（`AdminAccessDeniedEvent`）は、問い合わせの部分を除いたパスを残す。どちらも U3 の漏えいのテストで、`q` を付けた要求で確かめる。
- **`userId` が整数でない**とき: 既存の `GlobalExceptionHandler` が `TypeMismatchException` を 400 `VALIDATION_FAILED` にする（招待の `{invitationId}` と同じ `long` の受け方）。監査には残さない（入力の誤り、FR7.3）。

## C8: 利用者の管理のために UserAccount・Authentication に足す口（U3 の中の部品の境界）

```yaml
contract: user-admin-ports
kind: java-interface
provider: U3（既存の user・auth パッケージに足す。置き場の細部とクラスの名前は U3 の機能設計）
consumer: U3 の UserAdministration（useradmin パッケージ）
transaction: 一覧は読み取りだけのトランザクション。それ以外は UserAdministration の業務処理のトランザクションに入る（MANDATORY）
operations:
  UserAccount（user パッケージ）:
    findAdminPage:
      description: 利用者の要約の一覧（FR1.3・FR1.4）。登録した順（古い順）、同じ時刻は利用者 ID の順
      input: { search: "SearchText?", offset: long, limit: int }   # search は大文字小文字を区別しない部分一致、% と _ は文字どおり
      returns:
        UserAdminSlice: { items: "List<UserAdminSummary>", total: long }
        UserAdminSummary: { userId: long, email: EmailAddress, displayName: DisplayName, language: Language, admin: boolean, suspended: boolean, registeredAt: Instant }
      # パスワードのハッシュ値は読まない。toString はメールアドレスと氏名を伏せる
    lockAdminRowsInIdOrder:
      description: 管理者の印を持つ利用者の行と、対象の利用者の行を、利用者 ID の小さい順にまとめて排他する（ADR-007。順をそろえて行き詰まりを防ぐ）
      input: { targetUserId: long }
      returns:
        oneOf:
          - Locked: { target: "Optional<UserAdminSummary>", activeAdminIds: "Set<Long>" }   # 対象がいなければ empty。activeAdminIds は排他した後に数えた有効な管理者（印あり・停止中でない、FR4.1）
          - Busy: {}   # 排他の待ちの上限切れ（値は U3 の NFR 設計。既存の行の排他は 3000 ミリ秒）
    lockUserRow:
      description: 対象の利用者の行だけを排他する（停止を解く・失敗回数を戻すなど、最後の管理者の保護が要らない操作）
      input: { targetUserId: long }
      returns:
        oneOf:
          - Locked: { target: "Optional<UserAdminSummary>" }
          - Busy: {}
    setAdmin:
      description: 管理者の印を変える。拒否の判定はしない（判定は UserAdministration）
      input: { userId: long, admin: boolean }
      returns: void
    updateProfile:
      description: 氏名と言語を変える。検証は自分のプリファレンスと同じ規則（FR6.2）
      input: { userId: long, displayName: string, language: string }
      returns:
        oneOf:
          - Updated: {}
          - NotFound: {}
          - Invalid: { errors: "List<FieldError>" }   # 既存の項目ごとの誤りの形
  Authentication（auth パッケージ）:
    lockViewsOf:
      description: 一覧の行のロックの判定の結果（FR1.2・FR1.6）。今の時刻は注入した Clock から取る
      input: { userIds: "Collection<Long>" }
      returns: "Map<Long, LockView>"   # 行の無い利用者は LockView(false, null, false)
      LockView: { locked: boolean, lockedUntil: "Instant?", resettable: boolean }   # locked は今の時刻 < 解除の予定の時刻。resettable は失敗回数 1 以上（M2 B）。失敗回数そのものは持たない
    resetLoginFailures:
      description: 失敗回数を 0 にし、解除の予定の時刻を無くす（FR5.1）。ログインの判定と同じ行を排他する（FR5.4）。行の無い利用者に行を作らない
      input: { userId: long }
      returns:
        oneOf:
          - Reset: {}
          - NothingToReset: {}   # 行が無い・失敗回数が 0（FR5.2）。UserAdministration が USER_ADMIN_NO_CHANGE にする
          - Busy: {}             # 行の排他の待ちの上限切れ（AC4.1.11）。既存の LoginAttemptStateRepository の上限 3000 ミリ秒の扱いを機能設計で決める
behaviour:
  - 排他の待ちの上限切れは例外のまま投げず、Busy として返す。UserAdministration が USER_ADMIN_BUSY にする
  - 操作する管理者が今も有効な管理者かは、lockAdminRowsInIdOrder の activeAdminIds で確かめ直す（ADR-007）。外れていれば、既存の ACCESS_DENIED と同じ扱いにする（細部は U3 の機能設計）
  - 生の失敗回数・ダミーの行の印・パスワードのハッシュ値は、どの口からも useradmin へ渡さない（FR1.6）
```

- 口の名前と、結果の型の細部（record の名前・置き場）は U3 の機能設計で決めてよい。決めるのは、入出力の値の種類・トランザクション・排他の順・上限切れを `Busy` で返すこと・内部の値を渡さないこと。
- `auth.repository`（ロックの状態の行の読み書き）と `auth.domain`（`LockView`）に手が入る。どちらも `packagesJudgedByTotal` の一覧にあるため、U3 の Bolt で下限を満たして一覧から外す作業が付く（`team.md`、ADR-003）。

## C4: 管理の画面の 403 の共通の扱いと、自分の氏名と言語の反映（U4 → U5・既存の管理の画面）

```yaml
contract: admin-forbidden-and-own-profile
kind: typescript-module
location: frontend/src/shared/api-client/（判定）と frontend/src/app/（表示・反映。置き場の細部は U4 の機能設計）
exports:
  isAdminForbidden:
    signature: "(path: string, error: ApiError) => boolean"
    description: "path が /api/admin/ で始まり、状態 403 で code が ACCESS_DENIED のときだけ true（ADR-005）"
  AdminForbiddenView:
    description: 「この画面を使う権限がありません」と「ホームへ戻る」を出す共通の表示。見出しに移す（画面イメージ S6）
  useAdminForbidden:
    signature: "() => (error: ApiError, path: string) => boolean"
    description: 管理の画面が API の失敗を渡す口。権限が無いときは AppFrame の表示を AdminForbiddenView に切り替え、ログインの状態（管理者の印）を読み直して管理のメニューを消し、true を返す。ほかの誤りは false（呼び出し元が自分で扱う）
  useApplyOwnProfile:
    signature: "() => (profile: { displayName: string, language: 'ja' | 'en' }) => void"
    description: 自分の氏名と言語だけを画面に当てる（上の帯の氏名・画面の言語・Accept-Language）。テーマと文字の大きさは変えない
behaviour:
  - 既存の applyUserPreferences はテーマと文字の大きさも要るため、氏名と言語だけを当てる口を足す（U5 が管理の画面で自分の行を変えたときに使う、FR6.3）
  - 管理の入口・DSL・招待・利用者の管理の画面すべてが useAdminForbidden を使い、403 の表示を同じにする（M5 B）
  - 401 の扱い（トークンの更新とログインの画面への移動）は既存の ApiClient のまま変えない（AC3.2.9）
```

- 関数・部品の名前は U4 の機能設計で変えてよい。変えたときは U5 の機能設計に伝える。決めるのは、判定の条件（パス・状態・code）、切り替えの振る舞い、氏名と言語だけを当てる口があること。

## C5: 画面のページ送りの計算（U2 → U5・InvitationUi）

```yaml
contract: ui-paging
kind: typescript-pure-functions
location: frontend/src/shared/（ADR-004）
moved_from: frontend/src/features/invitation/paging.ts（すべての export をそのまま移す。レビューの R-03）
exports:
  PAGE_SIZE: "20"
  PagerDirection: "'prev' | 'next'"
  PageRange: "{ from: number, to: number }"
  pageCount:
    signature: "(total: number) => number"
    description: ページの数。total が 0 以下なら 0
  pageRange:
    signature: "(page: number, total: number) => PageRange"
    description: 「n〜m 件目」の元の値。total が 0 以下なら from・to とも 0
  correctedPage:
    signature: "(page: number, total: number, itemCount: number) => number | undefined"
    description: 読んだページが空で全体の件数が 1 以上のとき（操作の後に件数が減ったときなど）、移るべき最後のページ。移る必要が無ければ undefined
  pagerButtonDisabledAfter:
    signature: "(direction: PagerDirection, page: number, total: number) => boolean"
    description: 前へは page が 1 以下、次へは page がページの数以上で押せない
behaviour:
  - 既存の paging.ts の振る舞いをそのまま移す（招待の画面の表示は変えない）
  - 利用者の管理の画面は、操作の後の読み直しで行が減ってページが空になったとき correctedPage で移る（画面イメージ S5）
  - 性質ベースのテスト（fast-check）の対象
```

- 名前・引数・結果は既存の `paging.ts` と同じにする。招待の画面は `src/shared/` の口を呼ぶ形に切り替え、招待の画面の既存のテストが通ったままであることで確かめる。

## C6: 管理の操作の監査の出来事（U3 → 既存の AuditLog）

```yaml
contract: user-admin-audit-events
kind: spring-application-event（確定の後に記録、既存の決まり。業務の拒否で巻き戻しても失敗の行は残す、AuditRollbackIT）
existing_columns_used:          # 既存の audit_events の列をそのまま使う。列は足さない（ADR-006）
  - event_type: AuditEventType           # 既存の列挙に定数を足す
  - result: AuditResult [SUCCESS, FAILURE]
  - failure_reason: AuditFailureReason   # 既存の列挙に値を足す
  - actor_user_id: long                  # 操作した管理者（V6）
  - target_user_id: long                 # 対象の利用者（V7）。対象がいないときは要求の userId をそのまま（FR7.6）。V7 の列は参照の制約を持たないため、実在しない ID も入る（要件の未解決 R-07 をこの段で確かめた）
  - 送り手の情報（既存の RequestOrigin の列）
event_types_added:              # UPPER_SNAKE_CASE、32 文字以内（K-6）
  USER_ADMIN_GRANTED:     { operation: grant-admin }
  USER_ADMIN_REVOKED:     { operation: revoke-admin }
  USER_SUSPENDED:         { operation: suspend }
  USER_RESUMED:           { operation: resume }
  LOGIN_FAILURES_RESET:   { operation: reset-login-failures }
  # 成功も失敗も同じ種類で、result で分ける（PASSWORD_CHANGED の前例）
failure_reasons:                # 業務の拒否の理由（FR7.2。C3 の code と1対1）
  USER_NOT_FOUND:     { code: USER_NOT_FOUND, note: 既存の値を使う（ログインの失敗と同じ名前・同じ意味） }
  SELF_OPERATION:     { code: USER_ADMIN_SELF_OPERATION }
  TARGET_SUSPENDED:   { code: USER_ADMIN_TARGET_SUSPENDED }
  NO_CHANGE:          { code: USER_ADMIN_NO_CHANGE }
  LAST_ACTIVE_ADMIN:  { code: USER_ADMIN_LAST_ADMIN }
never_recorded: [password, passwordHash, refreshToken, loginFailureCount, searchText, email, displayName]
not_recorded:
  - 一覧の閲覧・入力の誤り（FR7.3）
  - 氏名と言語の変更（FR6.4）
  - 排他の待ちの上限切れ（USER_ADMIN_BUSY）の扱いは機能設計・NFR 設計で決める（Open questions）
```

- ADR-006 は種類と理由の名前を機能設計で決めるとしていた。前の Intent の確認の指摘（user-management の Contract Design の R-02）にならい、この段で定数の名前の案まで決める。機能設計で変えるときは、この契約を直す（持ち主は U3）。
- 監査の書き込みに失敗しても、元の操作は失敗させない（`AuditWriteFailureIT` の既存の決まり）。
- 出来事のクラス（Java の型）の名前と形は U3 の機能設計で決める。

## C7: 停止中のログインの失敗の理由（U1 → 既存の AuditLog）

```yaml
contract: suspended-login-audit
kind: spring-application-event（既存のログインの失敗の出来事 LOGIN_FAILED）
failure_reason_added:
  ACCOUNT_SUSPENDED:
    description: 停止中の利用者のログインの照合の失敗（ストーリーの差5、AC3.2.8）。パスワードの正誤にかかわらずこの理由
response: 401 AUTHENTICATION_FAILED のまま（ほかの失敗と区別しない、FR3.4）
not_recorded:
  - トークンの更新・アクセストークンの認証で停止中だったこと（今の入口の決まりどおり、無効なトークンと同じ扱い。新しい監査は足さない）
```

## エラーの code の一覧（今回足すもの）

| code | 状態 | 置き場 | 使う API |
|---|---|---|---|
| USER_NOT_FOUND | 404 | UserAdminProblemTypes（新しく作る、`useradmin`） | C3 の操作すべて |
| USER_ADMIN_SELF_OPERATION | 409 | UserAdminProblemTypes | C3 grant-admin・revoke-admin・suspend・resume |
| USER_ADMIN_TARGET_SUSPENDED | 409 | UserAdminProblemTypes | C3 grant-admin・revoke-admin |
| USER_ADMIN_NO_CHANGE | 409 | UserAdminProblemTypes | C3 grant-admin・revoke-admin・suspend・resume・reset-login-failures |
| USER_ADMIN_LAST_ADMIN | 409 | UserAdminProblemTypes | C3 revoke-admin・suspend |
| USER_ADMIN_BUSY | 409 | UserAdminProblemTypes | C3 の状態を変える操作（排他の待ちの上限切れ、AC4.1.11） |

- 既存の code と重ならないことを確かめた（`DSL_BUSY` などの既存の 29 個）。
- 入力の誤りは既存の `VALIDATION_FAILED`（400）、未認証は既存の `AUTHENTICATION_REQUIRED`（401）、管理者でないは既存の `ACCESS_DENIED`（403）を使う。
- 説明文（ja・en）には、対象の利用者のメールアドレス・氏名・ID を載せない。

## 契約の持ち主と変え方の決まり

- 各契約の持ち主は Contracts の表の Owner の単位。持ち主の単位が形を決め、使う側の単位は持ち主の変更に合わせる。
- 項目の追加は安全な変更で、持ち主の単位だけで行える。受け側は知らない項目を無視する。ただし C3 の `AdminUser` は `additionalProperties: false` とし、項目を足すときは FR1.6（応答に含めない値）に当たらないことを確かめる。
- 項目の削除、名前や型の変更、`code` や状態コードの変更は壊れる変更で、使う側の単位の変更を同じ統合に含める（画面と API は同じ WAR で同時に変わる）。
- C2・C5 の移設では、招待の一覧の既存の振る舞いとテストを変えない。変える必要が出たときは、U2 の計画に書いて依頼者に確かめる。

## 直しの記録

承認の場の Request Changes（レビューの指摘 R-01〜R-05 のうち、R-04 を除く4件を直す）で、次のとおり直した。

| 指摘 | 直したこと |
|---|---|
| R-01 | 検索の文字 `q` を、controller の引数の時点で伏せ字の型 `SearchText` で受けると書いた（C3）。前の版は「controller で包む」とだけ書き、`String` で受ける形を許していた |
| R-02 | U3 が UserAccount・Authentication に足す口を C8 として足した（一覧・行の排他・印と氏名と言語の変更・ロックの判定の結果・失敗回数の取り消し、上限切れの `Busy`） |
| R-03 | C2・C5 を、既存の `InvitationPaging`（`parsePage`・`pageOf`・`offsetOf`）と `paging.ts`（`pageCount`・`pageRange`・`correctedPage`・`pagerButtonDisabledAfter`）の口をそのまま移す形に書き直した。前の版は既存に無い口（`window`・`pagingState`）を書いていた |
| R-04 | 直さず、`USER_ADMIN_BUSY` が判定の順の外にあることだけを C3 に書き、監査の扱いは Open questions に残した（U3 の機能設計） |
| R-05 | C3 の各操作に 401・403 と、`userId` が整数でないときの 400 を足した |

## Open questions

| Contract | Question | Blocks |
|---|---|---|
| C3・C6 | 排他の待ちの上限切れ（`USER_ADMIN_BUSY`）を監査に残すか、残すならどの理由か。待ちの上限の値（AC4.1.11） | U3 の機能設計・NFR 設計 |
| C3 | 検索の文字 `q` の長さの上限（FR1.5） | U3 の機能設計 |
| C4 | 関数・部品の名前と置き場（`src/shared/` か `src/app/` か） | U4 の機能設計 |
