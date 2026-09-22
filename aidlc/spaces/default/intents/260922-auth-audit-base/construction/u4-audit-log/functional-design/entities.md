# Entities — U4 監査ログ（u4-audit-log）

U4 がアプリとして独自に持つ情報の形を定める。対象は、内部DBに保存する監査イベント（AuditEvent）である。受け取る出来事の形は、U2 の AuthenticationEvent と U3 の AdminAccessDeniedEvent が正本である。判断の元は `functional-design-questions.md` の確定回答（Q1〜Q4）、要件定義書の FR9.1〜FR9.5、Domain Design のデータの持ち主（AuditEvent は AuditLog）である。

```yaml
entities:
  - name: AuditEvent
    description: 監査イベント。認証とアクセス制御の出来事を1件ずつ記録する。追記のみで、変更・削除しない。持ち主は AuditLog
    attributes:
      - name: auditEventId
        type: identifier
        required: true
        unique: true
        constraints: 記録するときに U4 が割り当てる
      - name: occurredAt
        type: datetime
        required: true
        constraints: 出来事が起きた日時（受け取った出来事の日時）。書き込んだ日時は記録しない（Q4）
      - name: eventType
        type: enum
        required: true
        allowed_values: [LOGIN_SUCCEEDED, LOGIN_FAILED, LOGGED_OUT, ACCESS_DENIED]
      - name: result
        type: enum
        required: true
        allowed_values: [SUCCESS, FAILURE]
        constraints: LOGIN_SUCCEEDED と LOGGED_OUT は SUCCESS、LOGIN_FAILED と ACCESS_DENIED は FAILURE
      - name: enteredEmail
        type: string
        required: false
        max: 254
        constraints: 入力された（または特定できた利用者の）メールアドレス。254 文字を超える分は切り詰める（Q2）。内容は加工せずに保存する。利用者を参照するものではない（存在しないメールアドレスも記録する）
      - name: failureReason
        type: enum
        required: false
        allowed_values: [USER_NOT_FOUND, PASSWORD_MISMATCH, ACCOUNT_LOCKED, NOT_ADMIN, TOKEN_MISSING, TOKEN_MALFORMED, TOKEN_INVALID]
        constraints: result が FAILURE のときだけ。USER_NOT_FOUND は、ログインの失敗（入力されたメールアドレスの利用者がいない）とアクセス拒否（アクセストークンの利用者がいない）の両方で使う
      - name: sourceIp
        type: string
        required: true
        max: 45
        constraints: IPv4・IPv6 の表記
      - name: userAgent
        type: string
        required: false
        max: 512
        constraints: 512 文字を超える分は切り詰める（Q2）。内容は加工せずに保存する
      - name: traceId
        type: string
        required: false
        constraints: その要求のトレースID。アプリのログのトレースIDと一致する（FR10.2）
    constraints:
      - パスワード・トークン・ハッシュ値を含めない（NFR3）
      - 変更・削除しない（FR9.3）
    relationships: []
```

## まとめ

| 情報 | 役割 | 持ち主 | 保存先 |
|---|---|---|---|
| AuditEvent | 認証・アクセス制御の出来事の記録（追記のみ） | AuditLog | 内部DB |

受け取る出来事と AuditEvent の対応:

| 受け取る出来事 | eventType | result | 写す項目 |
|---|---|---|---|
| U2 AuthenticationEvent（LOGIN_SUCCEEDED） | LOGIN_SUCCEEDED | SUCCESS | occurredAt、enteredEmail、sourceIp、userAgent、traceId |
| U2 AuthenticationEvent（LOGIN_FAILED） | LOGIN_FAILED | FAILURE | 上に加えて failureReason |
| U2 AuthenticationEvent（LOGGED_OUT） | LOGGED_OUT | SUCCESS | occurredAt、enteredEmail、sourceIp、userAgent、traceId |
| U3 AdminAccessDeniedEvent | ACCESS_DENIED | FAILURE | occurredAt、enteredEmail（分かれば）、failureReason、sourceIp、userAgent、traceId |

U2 の出来事の userId は、監査ログの記録項目（FR9.2）に含まれないため記録しない。
