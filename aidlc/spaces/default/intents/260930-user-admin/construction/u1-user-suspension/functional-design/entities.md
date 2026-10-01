# Entities — U1 利用停止の状態と3つの入口（u1-user-suspension）

出典: 単位 `aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`（U1）、契約 `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`（C1・C7）、ADR-002・ADR-003・ADR-007 の項目4・ADR-008（`aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/decisions.md`）、この段の答え `functional-design-questions.md`（Q1 A・Q2 A・Q3 A）。既存の定義は `backend/src/main/java/cherry/mastersmith/user/domain/User.java`・`user/service/UserSummary.java`・`auth/domain/RefreshToken.java`・`auth/domain/LoginAttemptState.java`・`auth/domain/LoginFailureReason.java`・`auth/domain/TokenFailureReason.java`・`access/domain/AccessDeniedReason.java`・`audit/domain/AuditFailureReason.java`・`audit/domain/AuditEventType.java` と `backend/src/main/resources/db/migration/`（V1〜V8）で突き合わせた。

U1 が内部DB に新しく持つデータは、既存の利用者（User）に足す停止の状態の1列だけである。リフレッシュトークン（RefreshToken）とロックの状態（LoginAttemptState）は既存の Authentication の持ち物で、形を変えずに読み書きの仕方だけを足す。監査の記録（AuditEvent）は列を足さず、失敗の理由の値を1つ足す。フレームワークが持つ仕組み（認証の文脈、401 の応答を書く入口、トランザクションの伝わり方、トレースの情報）はエンティティにせず、`rules.md` の決まりとして書く（`project.md` の Code Style）。

```yaml
entities:
  - name: User
    description: >-
      利用者（既存の表 users、持ち主は UserAccount）。この単位で停止の状態 suspended を足す（ADR-002、C1）。
      停止の理由・止めた人・止めた時刻は持たない（理由は記録しない RQ12、誰がいつは監査ログに残る FR3.1）
    attributes:
      # --- 既存（変えない）
      - { name: userId, type: long, required: true, unique: true, constraints: "既存。DB の連番。3つの入口と C1 の口が受け渡す識別" }
      - { name: email, type: string, required: true, unique: true, max: 254, constraints: "既存。停止中も登録済みのまま（BR6.4）" }
      - { name: passwordHash, type: string, required: true, max: 100, constraints: "既存。停止中も照合に使う（BR2.3）。UserAccount の外へ出さない" }
      - { name: admin, type: boolean, required: true, constraints: "既存（列 admin_flag）。この単位では変えない" }
      - { name: createdAt, type: timestamp, required: true, constraints: "既存。UTC の時点" }
      - { name: displayName, type: string, required: true, max: 254, constraints: "既存（V7）。この単位では変えない" }
      - { name: language, type: enum, required: true, allowed: [ja, en], constraints: "既存（V7）" }
      - { name: theme, type: enum, required: true, allowed: [light, dark, system], constraints: "既存（V7）" }
      - { name: fontSize, type: enum, required: true, allowed: [sm, md, lg], constraints: "既存（V7）" }
      # --- 足す（V9、BR1.2）
      - name: suspended
        type: boolean
        required: true
        default: false
        constraints: >-
          利用停止の状態。true は停止中、false は有効（停止していない）。既存の利用者と、列を書かない作成の経路
          （初期管理者の自動作成・登録の完了）は既定の false になる（BR1.1・BR1.2）。書き換えるのは C1 の setSuspended だけ（BR1.5・BR1.6）
    constraints:
      - "suspended は3つの入口（ログインの照合・トークンの更新・アクセストークンの認証）がすべて同じ利用者の要約から読む（BR1.3）"
      - "setSuspended は suspended の列だけを書き換え、ほかの列を読み直した値で上書きしない（BR1.5）"
      - "C1 の口 isSuspended・setSuspended は既存の UserAccountService（user.service）に置く。setSuspended の更新は書いた後の読み取りが古い値を返さない形にする（BR1.4・BR1.5）"
      - "停止の状態を変えても、ロックの状態・管理者の印・氏名と表示の設定は変わらない（BR1.5・BR2.2）"
    relationships:
      - "User 1 ← * RefreshToken（既存。止めるときに BR5.1 でまとめて無効にする）"
      - "User 1 ← 0..1 LoginAttemptState（既存。停止中のログインでも読んだ値のまま書き戻す、BR2.2）"
      - "User 1 ← * AuditEvent（actor_user_id。停止中のログインの失敗では本人、BR2.5）"

  - name: RefreshToken
    description: >-
      リフレッシュトークン（既存の表 refresh_tokens、持ち主は Authentication）。形は変えない。この単位で
      「利用者 ID のまだ無効にしていない行をまとめて無効にする」書き方を足す（ADR-003、C1 の revokeAllRefreshTokens）
    attributes:
      - { name: tokenId, type: long, required: true, unique: true, constraints: "既存。DB の連番" }
      - { name: userId, type: long, required: true, references: "User.userId", constraints: "既存。既存の索引 ix_refresh_tokens_user_id で引く" }
      - { name: tokenHash, type: bytes, required: true, unique: true, max: 32, constraints: "既存。トークンの値のハッシュだけを持つ。ログ・監査・応答に出さない" }
      - { name: issuedAt, type: timestamp, required: true, constraints: "既存" }
      - { name: expiresAt, type: timestamp, required: true, constraints: "既存。まとめての無効化は期限切れの行も対象に含める（BR5.1）" }
      - { name: revokedAt, type: timestamp, required: false, constraints: "既存。空なら未無効。まとめての無効化は空の行だけに注入した時計の今の時刻を入れ、入っている行は書き換えない（BR5.1）。一度入れたら戻さない（BR5.4）" }
    constraints:
      - "無効にした行は停止を解いても有効に戻らない（BR5.4）"
      - "停止中のトークンの更新の拒否では、出されたトークンの revokedAt は確定しない（巻き戻す、BR3.1）"

  - name: LoginAttemptState
    description: >-
      ロックの状態（既存の表 login_attempt_states、持ち主は Authentication）。形も書き方も変えない。停止中のログインでは
      本人の行を排他つきで読み、読んだ値のまま書き戻す（Q1 A、BR2.2）
    attributes:
      - { name: subjectId, type: long, required: true, unique: true, constraints: "既存。利用者 ID（正の値）、ダミーの行は −1〜−8" }
      - { name: consecutiveFailures, type: int, required: true, min: 0, constraints: "既存。停止中のログインでは変わらない（BR2.2）" }
      - { name: lockedUntil, type: timestamp, required: false, constraints: "既存。停止中のログインでは変わらない（過ぎていても読んだ値のまま、BR2.2）" }
    constraints:
      - "停止中のログインの書き込みは、パスワードの誤りと同じ1回の更新で、値は読んだ値のまま（BR2.2・BR2.3）"

  - name: AuditEvent
    description: >-
      監査の記録（既存の表 audit_events、持ち主は AuditLog）。追記だけで変えない。列は足さず、失敗の理由の値
      ACCOUNT_SUSPENDED を1つ足す（C7）
    attributes:
      - { name: auditEventId, type: long, required: true, unique: true, constraints: "既存" }
      - { name: occurredAt, type: timestamp, required: true, constraints: "既存。UTC の時点" }
      - { name: eventType, type: enum, required: true, max: 32, constraints: "既存の AuditEventType。停止中のログインは既存の LOGIN_FAILED のまま。この単位では種類を足さない（止める・解く種類は U3、C6）" }
      - { name: result, type: enum, required: true, allowed: [SUCCESS, FAILURE], constraints: "既存。停止中のログインは FAILURE" }
      - { name: enteredEmail, type: string, required: false, max: 508, constraints: "既存。ログインの失敗では今までどおり入れたメールアドレス（既存の決まりのまま）" }
      - name: failureReason
        type: enum
        required: false
        max: 32
        allowed: [USER_NOT_FOUND, PASSWORD_MISMATCH, ACCOUNT_LOCKED, NOT_ADMIN, TOKEN_MISSING, TOKEN_MALFORMED, TOKEN_INVALID, CURRENT_PASSWORD_MISMATCH, INVITATION_EXPIRED, INVITATION_ALREADY_USED, INVITATION_CANCELLED, INVITATION_NOT_FOUND, EMAIL_ALREADY_REGISTERED, ACCOUNT_SUSPENDED]
        constraints: "既存の AuditFailureReason に ACCOUNT_SUSPENDED（17 文字）を足す（BR2.5・BR7.2）。U3 の管理の操作の理由は U3 が足す（C6）"
      - { name: actorUserId, type: long, required: false, references: "User.userId", constraints: "既存（V6）。停止中のログインの失敗では本人の利用者 ID" }
      - { name: sourceIp, type: string, required: true, max: 45, constraints: "既存" }
      - { name: userAgent, type: string, required: false, max: 1024, constraints: "既存" }
      - { name: traceId, type: string, required: false, max: 64, constraints: "既存" }
    constraints:
      - "トークンの更新・アクセストークンの認証で停止中だったことは記録しない（C7 の not_recorded、BR3.2・BR4.2）"
      - "パスワード・トークンの値とハッシュはどの列にも入れない（既存の決まり）"

value_types:
  - name: UserSummary
    description: >-
      UserAccount の外（auth）へ渡す利用者の要約（既存）。userId・email・admin・displayName・language・theme・fontSize に
      suspended を足す（BR1.3）。作るのは UserAccount の1か所だけ。toString は今までどおりメールアドレスと氏名を伏せる
    attributes:
      - { name: suspended, type: boolean, constraints: "足す。3つの入口が停止を判定する値。認証の応答（ログイン・更新・自分の情報）には載せない（BR6.1）" }
  - name: LoginFailureReason
    description: "ログインの失敗の区分（既存、auth の出来事が持つ）。ACCOUNT_SUSPENDED を足す（C7）"
    allowed: [USER_NOT_FOUND, PASSWORD_MISMATCH, ACCOUNT_LOCKED, ACCOUNT_SUSPENDED]
  - name: TokenFailureReason
    description: "アクセストークンの認証の失敗の区分（既存）。USER_SUSPENDED（14 文字）を足す（Q3 A、BR4.1）"
    allowed: [TOKEN_MALFORMED, TOKEN_INVALID, TOKEN_EXPIRED, USER_NOT_FOUND, USER_SUSPENDED]
  - name: AccessDeniedReason
    description: >-
      管理の API のアクセスの拒否の理由（既存、access が持つ）。値は足さない。TokenFailureReason からの変換で、
      USER_SUSPENDED は TOKEN_EXPIRED と同じく「理由なし（記録しない）」にする（Q3 A、BR4.2）
    allowed: [NOT_ADMIN, TOKEN_MISSING, TOKEN_MALFORMED, TOKEN_INVALID, USER_NOT_FOUND]
  - name: RevokeAllResult
    description: >-
      C1 の revokeAllRefreshTokens の結果（新しい record、auth.service に置く）。無効にした件数（0 以上の整数）だけを持つ（BR5.1）。
      口は auth.service に新しく作る RefreshTokenRevocationService の revokeAllRefreshTokens(long userId)（MANDATORY、BR5.2）
    attributes:
      - { name: revoked, type: int, constraints: "0 以上。0 でも成功" }
```

## エンティティの要約

| エンティティ・値 | 種類 | この単位での変化 |
|---|---|---|
| User | 既存の表を広げる | 停止の状態 suspended（真偽・必須・既定 false）を V9 で足す。既存の利用者は有効。書き換えは C1 の setSuspended だけ |
| RefreshToken | 既存の表（形は変えない） | 利用者 ID の未無効の行をまとめて無効にする書き方を足す（期限切れの行も含む、件数を返す） |
| LoginAttemptState | 既存の表（形も書き方も変えない） | 停止中のログインでは本人の行を排他つきで読み、読んだ値のまま書き戻す |
| AuditEvent | 既存の表（列は変えない） | 失敗の理由 ACCOUNT_SUSPENDED を足す。出来事の種類は LOGIN_FAILED のまま |
| UserSummary | 値の型（既存を広げる） | suspended を足し、3つの入口が同じ値を読む。応答には載せない |
| LoginFailureReason・TokenFailureReason | 値の型（既存を広げる） | ACCOUNT_SUSPENDED・USER_SUSPENDED を足す |
| AccessDeniedReason | 値の型（既存、値は足さない） | USER_SUSPENDED からの変換を「記録しない」にする1行を足す |
| RevokeAllResult | 値の型（新しい、auth.service） | まとめての無効化の件数（RefreshTokenRevocationService の戻り値） |
