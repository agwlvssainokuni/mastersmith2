# Contract Summary — user-management

出典: 単位 `aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、依存 `aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work-dependency.md`、部品 `aidlc/spaces/default/intents/260925-user-management/inception/domain-design/components.md`、要件 `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、この段の答え `contract-design-questions.md`（Q1〜Q3）。既存の API とエラー応答は `aidlc/spaces/default/codekb/mastersmith2/api-documentation.md`。

## 共通の決まり

- **エラー応答**: 既存の RFC 9457 Problem Details に `code` と `traceId` を足した形（`@RestControllerAdvice` の1か所で作る）。1つの `code` は1つの状態コードに固定し、機能ごとの `ProblemTypeCatalog` に置く。説明文は要求の `Accept-Language`（ja・en）で決まる。内部の例外のメッセージ、メールの部品の例外、SMTP の応答、パスワード、招待のトークンと URL、宛先のメールアドレスは応答に載せない（`project.md` の Forbidden）。
- **版**: URL に版を入れない。画面と API は同じ WAR で同時に変わる。項目の追加は安全な変更で、受け側は知らない項目を無視する。
- **アプリの中の部品の間**: 想定内の失敗は結果の型（成功の値、または失敗の種類）で返し、想定外の失敗だけを例外にする。API の側で結果を見て Problem Details に変える。
- **要求の言語**: 画面（ApiClient）は、ログインの前は画面の言語、ログインの後は利用者の言語を `Accept-Language` に入れて送る（ADR-005）。
- **認可**: `/api/admin/` の下は管理者だけ（未認証 401・管理者でない 403、既存の AccessControl）。`/api/me/` の下は `/api/` の既定のログイン必須（未認証 401）。`/api/registration/` と `/api/appearance` は差し込み口（SecurityRuleContributor）で公開にする。`/api/auth/` の下には新しい API を置かない（K-6、ADR-011）。
- **時刻**: 日時は ISO 8601 の UTC（`Z` 付き）で渡し、画面がブラウザのタイムゾーンで表示する。

## Contracts

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | U1 u1-mail | U3 u3-invitation | アプリの中の呼び出し（Java インターフェース） | U1 |
| C2 | U2 u2-user-preferences | U3 u3-invitation | アプリの中の呼び出し（Java インターフェース） | U2 |
| C3 | U2 u2-user-preferences | U4 u4-display-foundation（と既存の AuthUi） | HTTP（既存のログインと更新の応答を広げる） | U2 |
| C4 | U2 u2-user-preferences | U7 u7-preferences-ui | HTTP（REST、JSON） | U2 |
| C5 | U3 u3-invitation | U5 u5-invitation-ui | HTTP（REST、JSON、管理者だけ） | U3 |
| C6 | U3 u3-invitation | U6 u6-registration-ui | HTTP（REST、JSON、公開） | U3 |
| C7 | U8 u8-instance-appearance | U4 u4-display-foundation | HTTP（REST、JSON、公開） | U8 |
| C8 | U2・U3 | 既存の AuditLog | アプリの中の出来事（確定の後） | U2（出来事の列の一覧）・U3（招待と登録の出来事の型） |
| C9 | U4 u4-display-foundation | U5・U6・U7 | 画面の中の呼び出し（TypeScript の関数・フック） | U4 |
| C10 | U3 u3-invitation | External: 招待された人のメールの受け手 | メール（HTML、SMTP） | U3 |

## C1: メールの描画と送信（U1 → U3）

```yaml
contract: MailSender
kind: java-interface
package: cherry.mastersmith.mail.service
operations:
  isConfigured:
    returns: boolean   # SMTP の接続先が設定されているか（資格情報の値は返さない）
  send:
    input:
      MailRequest:
        templateId: string        # 例: invitation（テンプレートの置き場と名前は U1 の機能設計）
        language: [ja, en]
        to: EmailAddress          # 既存の値の型。改行を含む値は受け付けない
        variables: Map<String, String>   # 差し込む値。エスケープされる差し込みだけで入る
    returns:
      MailSendResult:
        oneOf:
          - Sent: {}
          - Failed:
              kind: [NOT_CONFIGURED, CONNECTION_FAILED, TIMEOUT, REJECTED, INVALID_INPUT, TEMPLATE_ERROR]
              # 例外のメッセージ・SMTP の応答・宛先は含めない
behaviour:
  - 件名は描いたテンプレートの <title> の文面
  - 送信は1回だけ。自動の再試行はしない
  - 接続と読み取りに時間切れを持つ（値は U1 の NFR 要件）
  - 呼び出し元はトランザクションの外で呼ぶ（ADR-009）
  - 内部DB に触れない
```

## C2: 利用者の確かめと作成（U2 → U3）

```yaml
contract: UserAccountService（広げる）
kind: java-interface
package: cherry.mastersmith.user.service
operations:
  existsByEmail:
    input: { email: EmailAddress }
    returns: boolean
  createUser:          # 既存の作成を広げる
    input:
      NewUser:
        email: EmailAddress
        displayName: DisplayName     # 空にできない（長さは U2 の機能設計）
        password: Password           # 規則は既存の PasswordPolicy
        language: [ja, en]
        theme: [light, dark, system]
        fontSize: [sm, md, lg]
        admin: false                 # 招待からの作成は常に false
    returns:
      oneOf:
        - Created: { userId: long }
        - EmailAlreadyUsed: {}
    transaction: 呼び出し元のトランザクションに参加する（招待の使用済みと同じトランザクション）
    events: 既存の UserCreatedEvent（ロックの状態の用意）
  findDisplayName:
    input: { userId: long }
    returns: Optional<String>     # 招待の一覧の「招待した管理者」の表示に使う
  findLanguage:
    input: { userId: long }
    returns: [ja, en]             # 招待の言語の初期値（管理者自身の言語）。画面は C3 の値を使うため、サーバーでの利用は任意
```

## C3: ログインと更新の応答の利用者の情報（U2 → U4・AuthUi）

既存の `POST /api/auth/login` と `POST /api/auth/session/refresh` の応答 `TokenResponse.user` に項目を足す（安全な追加）。

```yaml
openapi: 3.1.0
info: { title: 既存の認証の応答の追加の項目, version: "1" }
paths: {}
components:
  schemas:
    CurrentUserResponse:
      type: object
      required: [email, admin, displayName, language, theme, fontSize]
      properties:
        email: { type: string }
        admin: { type: boolean }
        displayName: { type: string }                       # 追加
        language: { type: string, enum: [ja, en] }          # 追加
        theme: { type: string, enum: [light, dark, system] } # 追加
        fontSize: { type: string, enum: [sm, md, lg] }      # 追加
```

## C4: 自分のプリファレンスとパスワードの変更の API（U2 → U7）

```yaml
openapi: 3.1.0
info: { title: MasterSmith 自分の設定の API（ログインした利用者）, version: "1" }
servers:
  - url: /api/me
components:
  securitySchemes:
    bearer: { type: http, scheme: bearer }
  schemas:
    Preferences:
      type: object
      required: [displayName, language, theme, fontSize]
      properties:
        displayName: { type: string, minLength: 1 }     # 最大長は U2 の機能設計
        language: { type: string, enum: [ja, en] }
        theme: { type: string, enum: [light, dark, system] }
        fontSize: { type: string, enum: [sm, md, lg] }
    PasswordChangeRequest:
      type: object
      required: [currentPassword, newPassword, newPasswordConfirmation]
      properties:
        currentPassword: { type: string }
        newPassword: { type: string }
        newPasswordConfirmation: { type: string }
security:
  - bearer: []
paths:
  /preferences:
    get:
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Preferences" } } } }
        "401": { description: 未認証（既存の AUTHENTICATION_REQUIRED） }
    put:
      description: 4つをまとめて置き換える。監査しない（FR9.2）
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/Preferences" } } } }
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Preferences" } } } }
        "400": { description: 入力の誤り（既存の VALIDATION_FAILED） }
        "401": { description: 未認証 }
  /password:
    post:
      description: 今のパスワードを確かめて変える。リフレッシュトークンは無効にしない（FR6.3）。成功と今のパスワードの誤りを監査する
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/PasswordChangeRequest" } } } }
      responses:
        "204": { description: 変更した }
        "400": { description: "入力の誤り（VALIDATION_FAILED: 規則・2回入力の不一致）、または今のパスワードの誤り（PASSWORD_CURRENT_MISMATCH）" }
        "401": { description: 未認証 }
```

## C5: 招待の管理の API（U3 → U5）

```yaml
openapi: 3.1.0
info: { title: MasterSmith 招待の管理の API（管理者だけ）, version: "1" }
servers:
  - url: /api/admin/invitations
components:
  securitySchemes:
    bearer: { type: http, scheme: bearer }
  schemas:
    InvitationRequest:
      type: object
      required: [email, language]
      properties:
        email: { type: string }
        language: { type: string, enum: [ja, en] }
    Invitation:
      type: object
      required: [invitationId, email, language, invitedBy, invitedAt, expiresAt, sendResult, expired]
      properties:
        invitationId: { type: integer }
        email: { type: string }
        language: { type: string, enum: [ja, en] }
        invitedBy: { type: string, description: 招待した管理者の氏名（無ければメールアドレス） }
        invitedAt: { type: string, format: date-time }
        expiresAt: { type: string, format: date-time }
        sendResult: { type: string, enum: [SENT, FAILED] }
        expired: { type: boolean }
    InvitationPage:
      type: object
      required: [items, page, size, total, invitationEnabled, unavailableReasons]
      properties:
        items: { type: array, items: { $ref: "#/components/schemas/Invitation" } }
        page: { type: integer, minimum: 1 }
        size: { type: integer, const: 20 }
        total: { type: integer, minimum: 0 }
        invitationEnabled: { type: boolean }
        unavailableReasons:
          type: array
          items: { type: string, enum: [BASE_URL_NOT_CONFIGURED, SMTP_NOT_CONFIGURED] }
    InvitationNotConfiguredProblem:
      description: 既存の Problem Details に項目を足した形。足りない設定を示す（FR1.8、確認の指摘 R-03）
      type: object
      required: [code, unavailableReasons]
      properties:
        code: { type: string, const: INVITATION_NOT_CONFIGURED }
        unavailableReasons:
          type: array
          minItems: 1
          items: { type: string, enum: [BASE_URL_NOT_CONFIGURED, SMTP_NOT_CONFIGURED] }
    InvitationAlreadyPendingProblem:
      description: 既存の Problem Details に項目を足した形（前の Intent の誤りの一覧と同じ口）
      type: object
      required: [code, invitationId, page]
      properties:
        code: { type: string, const: INVITATION_ALREADY_PENDING }
        invitationId: { type: integer }
        page: { type: integer, description: その招待が載る一覧のページ（AC1.1.4 の行へ移る操作に使う） }
security:
  - bearer: []
paths:
  /:
    get:
      parameters:
        - { name: page, in: query, schema: { type: integer, minimum: 1, default: 1 } }
      description: 完了も取り消しもしていない招待（期限切れを含む）を招待した日時の新しい順に 20 件ずつ
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/InvitationPage" } } } }
    post:
      description: 招待を作り、確定の後にトランザクションの外で招待メールを送る。送信に失敗しても招待は作られる（FR2.4、Q2: A）
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/InvitationRequest" } } } }
      responses:
        "201": { description: 招待を作った（sendResult は SENT か FAILED）, content: { application/json: { schema: { $ref: "#/components/schemas/Invitation" } } } }
        "400": { description: 入力の誤り（VALIDATION_FAILED） }
        "409": { description: "登録済み（INVITATION_EMAIL_REGISTERED）、または招待中（INVITATION_ALREADY_PENDING、invitationId と page を付ける）" }
        "503": { description: 招待を使えない設定（INVITATION_NOT_CONFIGURED、unavailableReasons を付ける）, content: { application/problem+json: { schema: { $ref: "#/components/schemas/InvitationNotConfiguredProblem" } } } }
  /{invitationId}/resend:
    post:
      description: 前のトークンを無効にし、新しいトークンと有効期限（24 時間）で送り直す。期限切れの招待も送り直せる
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Invitation" } } } }
        "404": { description: 見つからない（取り消し済み・完了済みを含む、INVITATION_NOT_FOUND） }
        "503": { description: 招待を使えない設定（INVITATION_NOT_CONFIGURED、unavailableReasons を付ける）, content: { application/problem+json: { schema: { $ref: "#/components/schemas/InvitationNotConfiguredProblem" } } } }
  /{invitationId}/cancel:
    post:
      responses:
        "204": { description: 取り消した }
        "404": { description: 見つからない（INVITATION_NOT_FOUND） }
```

## C6: 登録の完了の API（U3 → U6、公開）

招待のリンクは `https://<ベース URL>/register#token=<トークン>`（Q1: A）。画面はトークンを取り出し、要求の本文に入れて呼ぶ。トークンは URL のパスと問い合わせに入れない。

```yaml
openapi: 3.1.0
info: { title: MasterSmith 登録の完了の API（ログインなし）, version: "1" }
servers:
  - url: /api/registration
components:
  schemas:
    TokenRequest:
      type: object
      required: [token]
      properties:
        token: { type: string }
    InvitationView:
      type: object
      required: [email, language]
      properties:
        email: { type: string, description: 招待のメールアドレス（氏名の初期値とログインの案内に使う） }
        language: { type: string, enum: [ja, en], description: 招待の言語 }
    CompleteRequest:
      type: object
      required: [token, displayName, password, passwordConfirmation, language, theme, fontSize]
      properties:
        token: { type: string }
        displayName: { type: string, minLength: 1 }
        password: { type: string }
        passwordConfirmation: { type: string }
        language: { type: string, enum: [ja, en] }
        theme: { type: string, enum: [light, dark, system] }
        fontSize: { type: string, enum: [sm, md, lg] }
paths:
  /verify:
    post:
      description: リンクが使えるかを確かめ、画面の表示に要る値を返す。招待は消費しない。監査しない（開いただけの失敗は残さない）
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/TokenRequest" } } } }
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/InvitationView" } } } }
        "404": { description: 使えないリンク（期限切れ・使用済み・取り消し済み・存在しない・改ざん）。どの理由でも同じ（REGISTRATION_LINK_INVALID） }
  /complete:
    post:
      description: 利用者を作り、招待を使用済みにする。登録の完了と失敗を監査する。自動ではログインしない
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/CompleteRequest" } } } }
      responses:
        "204": { description: 登録を完了した }
        "400": { description: 入力の誤り（VALIDATION_FAILED: パスワードの規則・2回入力の不一致・空の氏名など） }
        "404": { description: "使えないリンク。完了の時点で期限切れ・同時に完了された・同じメールアドレスの利用者がいる場合も同じ（REGISTRATION_LINK_INVALID）" }
```

## C7: インスタンスの見た目の設定の API（U8 → U4、公開）

```yaml
openapi: 3.1.0
info: { title: MasterSmith 見た目の設定の API（ログインなし）, version: "1" }
paths:
  /api/appearance:
    get:
      responses:
        "200":
          content:
            application/json:
              schema:
                type: object
                required: [brandColor, fontFamily]
                properties:
                  brandColor: { type: string, enum: [blue, green, purple, orange] }
                  fontFamily: { type: string, enum: [sans, serif] }
```

- 設定が無い・許されない値のときは blue・sans を返す（許されない値のときは起動時に警告のログを1回）。秘密は含まない。

## C8: 監査の出来事（U2・U3 → 既存の AuditLog）

```yaml
contract: audit-events
kind: spring-application-event（確定の後に記録、既存の決まり）
existing_columns_used:          # 既存の audit_events の列をそのまま使う（新しく足さない）
  - result: AuditResult [SUCCESS, FAILURE]          # 既存の列挙
  - failure_reason: AuditFailureReason              # 既存の列挙に値を足す（下の追加の値）
  - actor_user_id: long?                            # 既存（V6）
columns_added_by_U2:            # 監査の表に足す列はこの2つだけ（一覧の正は U2）
  - target_user_id: long?        # 対象の利用者
  - target_invitation_id: long?  # 対象の招待
event_types_added:              # 既存の AuditEventType（UPPER_SNAKE_CASE）に足す定数
  PASSWORD_CHANGED:              # U2。成功も失敗もこの種類で、result で分ける
    actor_user_id: long
    result: [SUCCESS, FAILURE]
    failure_reason: [CURRENT_PASSWORD_MISMATCH]    # 失敗のとき
  INVITATION_ISSUED:             # U3
    actor_user_id: long
    target_invitation_id: long
    result: SUCCESS
  INVITATION_RESENT:             # U3
    actor_user_id: long
    target_invitation_id: long
    result: SUCCESS
  INVITATION_CANCELLED:          # U3
    actor_user_id: long
    target_invitation_id: long
    result: SUCCESS
  REGISTRATION_COMPLETED:        # U3
    target_invitation_id: long
    target_user_id: long
    result: SUCCESS
  REGISTRATION_FAILED:           # U3（完了の要求の失敗だけ。verify の失敗は記録しない）
    target_invitation_id: long?  # 存在しない・改ざんのときは無い
    result: FAILURE
    failure_reason: [INVITATION_EXPIRED, INVITATION_ALREADY_USED, INVITATION_CANCELLED, INVITATION_NOT_FOUND]   # 改ざんは INVITATION_NOT_FOUND に含める
failure_reasons_added:          # 既存の AuditFailureReason に足す値
  - CURRENT_PASSWORD_MISMATCH
  - INVITATION_EXPIRED
  - INVITATION_ALREADY_USED
  - INVITATION_CANCELLED
  - INVITATION_NOT_FOUND
never_recorded: [password, invitationToken, invitationUrl]
not_recorded_events: [招待メールの送信の失敗, プリファレンス・氏名の変更]
```

- 出来事の種類・失敗の理由の名前は、既存の `AuditEventType`・`AuditFailureReason` の定数の書き方（UPPER_SNAKE_CASE）にそろえた実際の定数名とする（確認の指摘 R-02）。列・値は既存の `audit_events` の `result`（`AuditResult` の `SUCCESS`・`FAILURE`）と `failure_reason` をそのまま使い、足す列は対象の2列だけとする（確認の指摘 R-01。前の案では既存の列を新しく足す列と書き、値も食い違っていた）。
- 未ログインの操作（登録の完了）の「操作した人」（`actor_user_id`）は空とし、対象で示す。
- アプリの中で知らせる出来事のクラス（Java の型）の名前と形は、U2・U3 の機能設計で決める。

## C9: 表示の設定の口（U4 → U5・U6・U7）

```yaml
contract: display-settings
kind: typescript-module
location: frontend/src/app/（AppFrame の側。置き場は U4 の機能設計）
exports:
  useDisplaySettings: "() => { language, theme, fontSize, setPreview(theme?, fontSize?), clearPreview(), applyUserPreferences(prefs), setLanguage(lang) }"
  saveBrowserDisplaySettings: "(settings: { language, theme, fontSize }) => void"   # 登録の完了（M4）で使う
behaviour:
  - setPreview は保存せずに画面に当てる（S2・S4 の選んだ時点の反映）
  - applyUserPreferences は画面に当ててブラウザにも保存する（ログインの後・保存の後）
  - setLanguage は画面の言語・<html lang>・要求の Accept-Language を変える
  - テーマ system は OS の配色の設定を見て make-you-chic-ui に light・dark を渡す
  - ユーザーメニューの氏名は applyUserPreferences で更新する
```

- 既存の機能の登録（`features/<featureId>/registration.ts`）はそのまま使う。

## C10: 招待メール（U3 → 招待された人）

```yaml
contract: invitation-mail
kind: html-mail
template: invitation（ja・en の2つ。置き場は U1・U3 の機能設計）
subject: テンプレートの <title> の文面
variables:
  registrationUrl: "https://<ベース URL>/register#token=<トークン>"   # ベース URL だけから組み立てる
content_rules:
  - 有効期限は日時を書かず「このリンクは 24 時間有効です」とだけ書く（M1: C）
  - 招待した管理者の氏名は載せない（M5: A）
  - ボタンの形のリンクと、URL の文字の両方を載せる
  - ライセンスヘッダーは {{! ... }} で書き、本文に出さない
```

## エラーの code の一覧（今回足すもの）

| code | 状態 | 置き場 | 使う API |
|---|---|---|---|
| INVITATION_EMAIL_REGISTERED | 409 | InvitationProblemTypes | C5 招待 |
| INVITATION_ALREADY_PENDING | 409 | InvitationProblemTypes | C5 招待（invitationId・page を付ける） |
| INVITATION_NOT_CONFIGURED | 503 | InvitationProblemTypes | C5 招待・送り直し（unavailableReasons を付ける） |
| INVITATION_NOT_FOUND | 404 | InvitationProblemTypes | C5 送り直し・取り消し |
| REGISTRATION_LINK_INVALID | 404 | InvitationProblemTypes | C6 確かめ・完了 |
| PASSWORD_CURRENT_MISMATCH | 400 | UserProblemTypes（新しく作る） | C4 パスワードの変更 |

入力の誤りは既存の `VALIDATION_FAILED`（400）、未認証は既存の `AUTHENTICATION_REQUIRED`（401）、管理者でないは既存の `ACCESS_DENIED`（403）を使う。

## 契約の持ち主と変え方の決まり

- 各契約の持ち主は Contracts の表の Owner の単位。持ち主の単位が形を決め、使う側の単位は持ち主の変更に合わせる。
- 項目の追加は安全な変更として、持ち主の単位だけで行える。受け側は知らない項目を無視する。
- 項目の削除・名前や型の変更・`code` や状態コードの変更は壊れる変更で、使う側の単位の変更を同じ統合に含める（画面と API は同じ WAR で同時に変わる）。
- C8 の監査の列の一覧は U2 が正を持ち、U3 は U2 が足した列だけを使う。

## Open questions

| Contract | Question | Blocks |
|---|---|---|
| C1 | テンプレートの置き場（言語ごとのファイルの持ち方）と templateId の決め方、SMTP の時間切れの値 | U1 |
| C1 | テスト用の SMTP の受け手の道具とライセンス | U1 |
| C2・C4 | 氏名の最大長と使える文字、空白だけの氏名の扱い | U2 |
| C4 | 今のパスワードの誤りが続いたときの制限（ロックとの関係） | U2 |
| C5 | 同じメールアドレスの招待中を1件に限る作り（H2 の索引か業務処理の行ロックか） | U3 |
| C5 | 送信の途中でアプリが止まったときの sendResult の表し方（送信中を持つか） | U3 |
| C6 | 登録の完了の公開の API の回数の制限を設けるか | U3 |
| C7 | 読み終わるまでに前に保存された値で一瞬描かれることの防ぎ方 | U4・U8 |
| C8 | 足す失敗の理由の値が既存の failure_reason の列の長さ（32）に収まるかの確かめと、既存の rejection_kind の列との関係 | U2 |
| C9 | 表示の設定の口の置き場とテーマ system の開いている間の追従 | U4 |
