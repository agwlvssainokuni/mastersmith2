# Entities — U3 管理画面のアクセス制御（u3-access-control）

U3 がアプリとして独自に持つ情報の形を定める。対象は、API の場所ごとの利用条件（アクセスの決まり）と、U4 へ知らせるアクセス拒否の出来事である。U3 は内部DBにデータを保存しない。管理者かどうかは U2 の AuthenticatedUser（要求ごとに内部DBから読んだ管理者フラグ）を使う。判断の元は `functional-design-questions.md` の確定回答（Q1〜Q5）と、要件定義書の FR2.2、FR8.1、FR8.2 である。

```yaml
entities:
  - name: AccessRule
    description: API の場所ごとの利用条件。アプリの定義として持ち、内部DBには保存しない（Q1、Q2）
    attributes:
      - name: pathPattern
        type: string
        required: true
        unique: true
        constraints: 要求のパスの型（例：/api/admin/ の下すべて）
      - name: requirement
        type: enum
        required: true
        allowed_values: [PUBLIC, AUTHENTICATED, ADMIN]
      - name: priority
        type: integer
        required: true
        constraints: 小さいほど先に判定する。ADMIN の決まりは PUBLIC より先
    constraints:
      - /api/admin/ の下を PUBLIC にする決まりを置かない
      - どの決まりにも当たらない /api/ の下の API は AUTHENTICATED（既定で拒否）
    relationships: []

  - name: AccessDeniedEvent
    description: 管理者のみの API へのアクセスを拒否した出来事。U3 がアプリ内に知らせ、U4（監査ログ）が受け取って記録する。U3 は受け取る側を知らない（Domain Design の ADR-004）
    attributes:
      - name: occurredAt
        type: datetime
        required: true
      - name: result
        type: enum
        required: true
        allowed_values: [DENIED]
        constraints: 監査ログの「結果（失敗）」（FR9.2）にあたる
      - name: failureReason
        type: enum
        required: true
        allowed_values: [NOT_ADMIN, NOT_AUTHENTICATED]
        constraints: NOT_ADMIN は 403、NOT_AUTHENTICATED は 401（有効期限切れを除く）
      - name: authenticationFailure
        type: enum
        required: false
        allowed_values: [TOKEN_MISSING, TOKEN_MALFORMED, TOKEN_INVALID, USER_NOT_FOUND]
        constraints: failureReason が NOT_AUTHENTICATED のときだけ。U2 が判定した 401 の理由。TOKEN_EXPIRED の場合は出来事を作らない（Q5）
      - name: userId
        type: identifier
        required: false
        constraints: 利用者が特定できたとき（NOT_ADMIN）だけ
      - name: enteredEmail
        type: string
        required: false
        constraints: 利用者が特定できたとき（NOT_ADMIN）だけ。その利用者のメールアドレス
      - name: requestMethod
        type: string
        required: true
      - name: requestPath
        type: string
        required: true
        constraints: クエリ文字列は含めない
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
      - アクセストークンの値・パスワードなどの秘密情報を含めない（NFR3）
    relationships: []
```

## まとめ

| 情報 | 役割 | 保存先 |
|---|---|---|
| AccessRule | API の場所ごとの利用条件（公開・ログイン必須・管理者のみ） | アプリの定義 |
| AccessDeniedEvent | U4 へ知らせるアクセス拒否の出来事 | 保存しない（U4 が記録する） |
