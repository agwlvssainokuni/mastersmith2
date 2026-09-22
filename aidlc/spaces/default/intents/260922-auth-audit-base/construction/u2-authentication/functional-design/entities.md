# Entities — U2 認証（u2-authentication）

U2 がアプリとして独自に持つ情報の形を定める。対象は、内部DBに保存する業務のデータ（User、RefreshToken、LoginAttemptState）、アクセストークンに載せる内容、U4 へ知らせる出来事、画面へ返す利用者の情報である。判断の元は `functional-design-questions.md` の確定回答（Q1〜Q11）、要件定義書の FR2.1、FR2.4、FR3〜FR7、Domain Design のデータの持ち主（User は UserAccount、RefreshToken と LoginAttemptState は Authentication）である。

設定値（初期管理者の設定、トークンの有効期限、ロックのしきい値と時間）は設定ファイルや環境変数で与えるもので、エンティティとしては扱わない。求める振る舞いは `rules.md` の決まりとして定める。パスワードのハッシュ方式とトークンの署名方式は NFR の段階で決める。

```yaml
entities:
  - name: User
    description: 利用者。メールアドレスで識別し、パスワードは一方向のハッシュでだけ持つ。持ち主は UserAccount
    attributes:
      - name: userId
        type: identifier
        required: true
        unique: true
        constraints: 内部で割り当てる。推測しにくい値にする必要はない（外部に認証の手段として使わない）
      - name: email
        type: string
        required: true
        unique: true
        max: 254
        constraints: 前後の空白を除き小文字にそろえた値で保存する（Q11）。一意性はこの値で判断する
      - name: passwordHash
        type: secret
        required: true
        constraints: 一方向のハッシュ。UserAccount の外へ渡さない。ログ・エラー応答・出来事に含めない（NFR3）
      - name: adminFlag
        type: boolean
        required: true
        default: false
      - name: createdAt
        type: datetime
        required: true
    relationships: []

  - name: LoginAttemptState
    description: 利用者ごとのログインの連続失敗回数とロックの状態。持ち主は Authentication（User は知らない）
    attributes:
      - name: userId
        type: identifier
        required: true
        unique: true
        references: User.userId
      - name: consecutiveFailures
        type: integer
        required: true
        default: 0
        min: 0
      - name: lockedUntil
        type: datetime
        required: false
        constraints: ロック中だけ値を持つ。現在時刻がこの時刻より前ならロック中
    constraints:
      - 存在しないメールアドレスに対しては作らない
    relationships:
      - target: User
        cardinality: one-to-one
        direction: LoginAttemptState → User

  - name: RefreshToken
    description: リフレッシュトークン。ログインごと・更新ごとに1件作る。値そのものは保存せず、ハッシュだけを保存する。持ち主は Authentication
    attributes:
      - name: tokenId
        type: identifier
        required: true
        unique: true
      - name: userId
        type: identifier
        required: true
        references: User.userId
      - name: tokenHash
        type: secret
        required: true
        unique: true
        constraints: 画面へ渡した値の一方向のハッシュ。値そのものは保存しない
      - name: issuedAt
        type: datetime
        required: true
      - name: expiresAt
        type: datetime
        required: true
        constraints: issuedAt＋リフレッシュトークンの有効期限（既定24時間）。更新で作り直したものも、作り直した時刻から数える（Q10）
      - name: revokedAt
        type: datetime
        required: false
        constraints: 使われた（作り直された）とき、またはログアウトしたときに設定する。設定後は二度と有効にならない
    constraints:
      - 有効なのは、revokedAt が無く、現在時刻が expiresAt より前のときだけ
      - 同じ利用者が複数の有効なリフレッシュトークンを持てる（複数のブラウザ・端末、Q7）
    relationships:
      - target: User
        cardinality: many-to-one
        direction: RefreshToken → User

  - name: AccessTokenClaims
    description: アクセストークンに載せる内容。署名して画面へ渡す。サーバー側には保存しない（Q8）
    attributes:
      - name: subject
        type: identifier
        required: true
        constraints: User.userId。メールアドレスや管理者フラグは載せない（Q8）
      - name: issuedAt
        type: datetime
        required: true
      - name: expiresAt
        type: datetime
        required: true
        constraints: issuedAt＋アクセストークンの有効期限（既定5分）
    relationships:
      - target: User
        cardinality: many-to-one
        direction: AccessTokenClaims → User

  - name: AuthenticatedUser
    description: 要求ごとにアクセストークンを検証した結果の利用者。U3 はこれで管理者かどうかを判断する。要求の処理中だけ存在する
    attributes:
      - name: userId
        type: identifier
        required: true
      - name: email
        type: string
        required: true
      - name: admin
        type: boolean
        required: true
        constraints: 要求のたびに内部DBの User.adminFlag から読む（Q8）
    relationships:
      - target: User
        cardinality: many-to-one
        direction: AuthenticatedUser → User

  - name: CurrentUserView
    description: ログインとトークンの更新の応答で画面へ返す、ログイン中の利用者の情報。画面のログイン状態の提供元（U1 の差し込み口4）が使う
    attributes:
      - name: email
        type: string
        required: true
      - name: admin
        type: boolean
        required: true
        constraints: 画面での表示の切り替えにだけ使う。サーバー側の判定の代わりにしない（FR8.2）
    relationships: []

  - name: AuthenticationEvent
    description: 認証の出来事。U2 がアプリ内に知らせ、U4（監査ログ）が受け取って記録する。U2 は受け取る側を知らない（Domain Design の ADR-004）
    attributes:
      - name: eventType
        type: enum
        required: true
        allowed_values: [LOGIN_SUCCEEDED, LOGIN_FAILED, LOGGED_OUT]
      - name: occurredAt
        type: datetime
        required: true
      - name: enteredEmail
        type: string
        required: false
        constraints: ログインで入力されたメールアドレス（小文字にそろえた値）。LOGGED_OUT では、そのリフレッシュトークンの利用者のメールアドレス
      - name: userId
        type: identifier
        required: false
        constraints: 利用者が特定できたときだけ
      - name: failureReason
        type: enum
        required: false
        allowed_values: [USER_NOT_FOUND, PASSWORD_MISMATCH, ACCOUNT_LOCKED]
        constraints: LOGIN_FAILED のときだけ。応答には載せない（FR4.5）
      - name: sourceIp
        type: string
        required: true
      - name: userAgent
        type: string
        required: false
      - name: traceId
        type: string
        required: false
        constraints: その要求のトレースID（U1 の functional-spec.md 6.1 の方法で取得）
    constraints:
      - パスワード・トークン・ハッシュ値を含めない（NFR3）
    relationships: []
```

## まとめ

| 情報 | 役割 | 持ち主 | 保存先 |
|---|---|---|---|
| User | 利用者（メールアドレス・パスワードのハッシュ・管理者フラグ） | UserAccount | 内部DB |
| LoginAttemptState | 連続失敗回数とロック解除の時刻 | Authentication | 内部DB |
| RefreshToken | リフレッシュトークン（ハッシュだけ保存） | Authentication | 内部DB |
| AccessTokenClaims | アクセストークンの中身（利用者ID・発行時刻・期限） | Authentication | 保存しない（署名して画面へ） |
| AuthenticatedUser | 要求ごとに検証した利用者（管理者フラグは DB から） | Authentication | 保存しない（要求の処理中だけ） |
| CurrentUserView | 画面へ返す利用者の情報（メールアドレス・管理者か） | Authentication | 保存しない（応答のみ） |
| AuthenticationEvent | U4 へ知らせる認証の出来事 | Authentication | 保存しない（U4 が記録する） |
