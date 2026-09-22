# Entities — U3 管理画面のアクセス制御（u3-access-control）

U3 がアプリとして独自に持つ情報の形を定める。対象は、U4 へ知らせるアクセス拒否の出来事だけである。U3 は内部DBにデータを保存しない。管理者かどうかは U2 の AuthenticatedUser（要求ごとに内部DBから読んだ管理者フラグ）を使う。判断の元は `functional-design-questions.md` の確定回答（Q1〜Q5）と、要件定義書の FR2.2、FR8.1、FR8.2、FR9.1、FR9.2 である。

API の場所ごとの利用条件（公開・ログイン必須・管理者のみ）は、フレームワークのアクセス制御の設定（Spring Security）で実現する。そのため、エンティティとしては扱わない。求める振る舞いは `rules.md` の BR1 で定める。

```yaml
entities:
  - name: AdminAccessDeniedEvent
    description: 管理者のみの API へのアクセスを拒否した出来事。U3 がアプリ内に知らせ、U4（監査ログ）が受け取って記録する。U3 は受け取る側を知らない（Domain Design の ADR-004）。項目は監査ログの記録項目（FR9.2、Domain Design の AuditEvent）にそろえる
    attributes:
      - name: eventType
        type: enum
        required: true
        allowed_values: [ACCESS_DENIED]
      - name: occurredAt
        type: datetime
        required: true
      - name: result
        type: enum
        required: true
        allowed_values: [FAILURE]
        constraints: 監査ログの「結果（失敗）」（FR9.2）にあたる
      - name: failureReason
        type: enum
        required: true
        allowed_values: [NOT_ADMIN, TOKEN_MISSING, TOKEN_MALFORMED, TOKEN_INVALID, USER_NOT_FOUND]
        constraints: NOT_ADMIN は 403（権限不足）。ほかは 401 のときの U2 が判定した理由。有効期限切れ（TOKEN_EXPIRED）の場合は出来事を作らない（Q5）
      - name: enteredEmail
        type: string
        required: false
        constraints: 利用者が特定できたとき（NOT_ADMIN）だけ。その利用者のメールアドレス
      - name: sourceIp
        type: string
        required: true
      - name: userAgent
        type: string
        required: false
      - name: traceId
        type: string
        required: false
        constraints: その要求のトレースID（U1 の functional-spec.md 6.1 の方法で取得）。どの API で拒否されたかは、このトレースIDでアプリのログと突き合わせて分かる
    constraints:
      - アクセストークンの値・パスワードなどの秘密情報を含めない（NFR3）
    relationships: []
```

## まとめ

| 情報 | 役割 | 保存先 |
|---|---|---|
| AdminAccessDeniedEvent | U4 へ知らせる、管理者のみの API へのアクセス拒否の出来事（項目は監査ログの記録項目にそろえる） | 保存しない（U4 が記録する） |
