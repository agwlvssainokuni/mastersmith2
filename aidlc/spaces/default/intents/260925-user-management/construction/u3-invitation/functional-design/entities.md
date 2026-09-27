# Entities — U3 招待と登録の完了（u3-invitation）

出典: 単位 `aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`（U3）、部品 `aidlc/spaces/default/intents/260925-user-management/inception/domain-design/components.md`（Invitation）、ADR-001・ADR-008・ADR-009・ADR-010・ADR-011（`aidlc/spaces/default/intents/260925-user-management/inception/domain-design/decisions.md`）、契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`（C1・C2・C5・C6・C8・C10）、この段の答え `functional-design-questions.md`（設計の要点 24 件・Q1 C・Q2 C・Q3 A・Q4 A）。依存する単位の決まりは `aidlc/spaces/default/intents/260925-user-management/construction/u1-mail/functional-design/`・`construction/u2-user-preferences/functional-design/` と突き合わせた。既存のトークンの作り方は `backend/src/main/java/cherry/mastersmith/auth/domain/RefreshTokenValues.java`・`TokenExpiry.java`、定期の削除は `auth/service/RefreshTokenCleanupJob.java` を参照した。

U3 が内部DB に新しく持つデータは招待（Invitation）の1つだけである。利用者（User）と監査の記録（AuditEvent）の列は U2 が持ち、U3 は監査の出来事の種類と失敗の理由の値を足すだけである。ベース URL・SMTP の設定・有効期限の長さ・保存の日数・定期の処理の時刻は設定の値でありエンティティにしない（`rules.md` の BR1.3・BR1.4・BR1.6 と BR11.1 に書く、`project.md` の Code Style）。招待のトークンそのものは保存せず、招待メールの本文にだけ載る。

```yaml
entities:
  - name: Invitation
    description: >-
      招待（新しい表、持ち主は Invitation、パッケージ invitation）。利用者の表とは別に持ち、登録の完了まで利用者を作らない（ADR-001）。
      状態（state）は PENDING・COMPLETED・CANCELLED・REPLACED の4つを保存し、期限切れは保存せず「state が PENDING かつ 時計の今 ≧ expiresAt」
      として読むたびに注入した時計で決める（BR3.3）。状態の移り変わりの正本は functional-spec.md の1節
    attributes:
      - { name: invitationId, type: long, required: true, unique: true, constraints: "DB の連番。外へ渡す識別（C5 の invitationId、監査の targetInvitationId）" }
      - name: email
        type: string
        required: true
        max: 254
        constraints: >-
          招待先のメールアドレス。前後の空白を除き小文字にそろえた値（既存の EmailAddress.normalize と同じ決まり、BR1.1）。
          同じ email で state が PENDING の行は常に1件まで（期限切れを含む、下の constraints）。送り直しでも変えない
      - { name: language, type: enum, required: true, allowed: [ja, en], constraints: "招待の言語。招待メールの言語と、登録の完了の画面の言語・言語の初期値に使う（CR1.4、FR4.3）。送り直しでも変えない" }
      - name: tokenHash
        type: binary
        required: true
        unique: true
        min: 32
        max: 32
        constraints: >-
          今のトークンの SHA-256 のハッシュ（32 バイト）だけを保存する（project.md の Mandated、NFR1、BR3.1）。送り直しで新しい値に置き換わり、
          前のトークンはその時点で見つからなくなる（BR6.1）。終わった状態（COMPLETED・CANCELLED・REPLACED）になっても消さずに残す
          （使用済み・取り消し済み・置き換え済みのリンクの監査の失敗の理由を分けるため、BR7.6）
      - { name: invitedByUserId, type: long, required: true, references: "User.userId", constraints: "招待した管理者。送り直しでも変えない（AC2.2.12 で新しくなるのは有効期限と送信の結果だけ、要点 3）" }
      - { name: invitedAt, type: timestamp, required: true, constraints: "招待した時点（UTC、注入した時計の値）。一覧の並びの鍵。送り直しでも変えない" }
      - name: expiresAt
        type: timestamp
        required: true
        constraints: >-
          有効期限（UTC）。作成・送り直しの時点の時計の値 ＋ 有効期限の長さ（★既定 24 時間、BR1.6）。有効は「今 < expiresAt」だけで、
          時刻ちょうどは無効（BR3.3）
      - name: sendResult
        type: enum
        required: true
        allowed: [PENDING, SENT, FAILED]
        default: PENDING
        constraints: >-
          招待メールの送信の結果（FR1.6・FR2.4）。確定の時点で PENDING（送信中・結果不明）とし、送信の後に SENT か FAILED に書き換える（Q2 C、BR4.3・BR4.4）。
          API（C5）へは PENDING を FAILED として渡し、契約の値（SENT・FAILED）は変えない。招待の状態 state の PENDING とは別の値の集合である
      - { name: state, type: enum, required: true, allowed: [PENDING, COMPLETED, CANCELLED, REPLACED], default: PENDING, constraints: "招待の状態。PENDING（招待中、期限切れを含む）・COMPLETED（登録を完了）・CANCELLED（取り消し）・REPLACED（期限切れのまま同じ email に新しく招待されて置き換わった、AC2.2.6）。終わった3つからは動かない" }
      - { name: endedAt, type: timestamp, required: false, constraints: "PENDING から終わった状態に移った時点（UTC、時計の値）。COMPLETED では登録を完了した時点、CANCELLED では取り消した時点、REPLACED では置き換えた時点。保存期間の起点（BR11.1）。PENDING では空" }
      - { name: completedUserId, type: long, required: false, references: "User.userId", constraints: "登録の完了で作った利用者。state が COMPLETED のときだけ入る（BR7.3）" }
    constraints:
      - "同じ email で state が PENDING の行は常に1件まで（期限切れを含む）。1件に限る仕組み（索引か行の排他か）は NFR 設計で決める（BR2.5、ADR-010）"
      - "tokenHash はすべての行で一意"
      - "endedAt は state が PENDING でないときだけ入り、completedUserId は state が COMPLETED のときだけ入る"
      - "expiresAt は invitedAt より後（作成では invitedAt ＋ 有効期限の長さ、送り直しでは送り直しの時点 ＋ 有効期限の長さ）"
      - "終わった状態（COMPLETED・CANCELLED・REPLACED）の行は、定期の削除（BR11.1）で消えるまで値を変えない"
      - "トークンの値・招待の URL は保存しない"
    relationships:
      - "User 1 ← * Invitation（invitedByUserId。招待した管理者）"
      - "User 0..1 ← 0..1 Invitation（completedUserId。登録の完了で作った利用者）"
      - "Invitation 1 ← * AuditEvent（targetInvitationId。参照の制約は置かない。招待の行が定期の削除で消えても監査の記録は残る、U2 の BR9.2）"

  - name: InvitationEvent
    description: >-
      招待と登録の出来事（アプリの中の知らせ、保存しない。C8 の U3 の5種類）。Invitation が出し、既存の AuditLog が確定の後に受けて
      AuditEvent を1件追記する（ADR-008、BR8.1〜BR8.3）。U2 の PasswordChangedEvent と同じ受け取り方
    attributes:
      - { name: type, type: enum, required: true, allowed: [INVITATION_ISSUED, INVITATION_RESENT, INVITATION_CANCELLED, REGISTRATION_COMPLETED, REGISTRATION_FAILED] }
      - { name: actorUserId, type: long, required: false, references: "User.userId", constraints: "操作した管理者。招待・送り直し・取り消しだけ。登録の完了・失敗では空（未ログインの操作、C8）" }
      - { name: invitationId, type: long, required: false, references: "Invitation.invitationId", constraints: "対象の招待（AuditEvent.targetInvitationId）。REGISTRATION_FAILED でトークンから招待が見つからないときだけ空" }
      - { name: targetUserId, type: long, required: false, references: "User.userId", constraints: "REGISTRATION_COMPLETED で作った利用者だけ" }
      - { name: result, type: enum, required: true, allowed: [SUCCESS, FAILURE], constraints: "REGISTRATION_FAILED だけ FAILURE、ほかは SUCCESS" }
      - name: failureReason
        type: enum
        required: false
        allowed: [INVITATION_EXPIRED, INVITATION_ALREADY_USED, INVITATION_CANCELLED, INVITATION_NOT_FOUND, EMAIL_ALREADY_REGISTERED]
        constraints: "REGISTRATION_FAILED のときだけ（BR7.6）。EMAIL_ALREADY_REGISTERED は Q4 A で契約 C8 に足す値"
      - { name: occurredAt, type: timestamp, required: true, constraints: "注入した時計の値" }
      - { name: sourceIp, type: string, required: true, constraints: "既存の送り手の情報（ClientInfo）と同じ取り方" }
      - { name: userAgent, type: string, required: false }
      - { name: traceId, type: string, required: false, constraints: "既存の出来事と同じ取り方" }
    constraints:
      - "トークンの値・トークンのハッシュ・招待の URL・パスワード・メールアドレスを持たない（文字列にしても出ない、BR8.6）"
      - "管理者の操作の拒否（400・404・409・503）、送信の失敗、リンクの確かめ（verify）の失敗、登録の完了の入力の誤りでは出さない（BR8.1・BR8.3）"

  - name: AuditEvent
    description: >-
      監査の記録（既存の表 audit_events、持ち主は AuditLog、足す列の一覧の正は U2）。U3 は列を足さず、出来事の種類と失敗の理由の値だけを足す（C8）。
      ここには U3 が触れる列と値だけを書く。ほかの列は U2 の entities.md のとおり
    attributes:
      - { name: eventType, type: enum, required: true, max: 32, allowed_added: [INVITATION_ISSUED, INVITATION_RESENT, INVITATION_CANCELLED, REGISTRATION_COMPLETED, REGISTRATION_FAILED], constraints: "既存の AuditEventType に足す。最長 REGISTRATION_COMPLETED 22 文字で 32 に収まる" }
      - { name: result, type: enum, required: true, allowed: [SUCCESS, FAILURE], constraints: "既存の AuditResult。値は足さない" }
      - { name: failureReason, type: enum, required: false, max: 32, allowed_added: [INVITATION_EXPIRED, INVITATION_ALREADY_USED, INVITATION_CANCELLED, INVITATION_NOT_FOUND, EMAIL_ALREADY_REGISTERED], constraints: "既存の AuditFailureReason に足す。最長 EMAIL_ALREADY_REGISTERED 24 文字・INVITATION_ALREADY_USED 23 文字で 32 に収まる" }
      - { name: actorUserId, type: long, required: false, constraints: "既存（V6）。招待・送り直し・取り消しで操作した管理者" }
      - { name: targetUserId, type: long, required: false, constraints: "U2 が足す列。REGISTRATION_COMPLETED で作った利用者" }
      - { name: targetInvitationId, type: long, required: false, constraints: "U2 が足す列。U3 の5種類の対象の招待（見つからないときは空）" }
      - { name: enteredEmail, type: string, required: false, constraints: "既存。U3 の出来事では入れない（空）" }
    constraints:
      - "追記だけで変えない・消さない（既存の決まり）。招待の定期の削除では消さない"

value_types:
  - name: InvitationToken
    description: "招待のトークンの値。暗号学的な乱数 32 バイトを URL で使える Base64（埋め草なし、43 文字）にした文字列（BR3.1）。文字列にすると伏せる。保存しない"
  - name: InvitationAvailability
    description: "招待を使える設定かの判定の結果（BR1.4）。起動のときに1回決まり、動いている間は変わらない"
    attributes:
      - { name: enabled, type: boolean }
      - { name: unavailableReasons, type: "list of enum [BASE_URL_NOT_CONFIGURED, SMTP_NOT_CONFIGURED]", constraints: "enabled が false のときだけ1つ以上。並びは BASE_URL_NOT_CONFIGURED、SMTP_NOT_CONFIGURED の順" }
  - name: InvitationSummary
    description: "一覧・招待・送り直しの応答に渡す招待の要約（C5 の Invitation）。invitationId・email・language・invitedBy・invitedAt・expiresAt・sendResult（SENT・FAILED）・expired。トークン・ハッシュ・URL を持たない（BR5.4）"
  - name: InvitationPage
    description: "一覧の1ページ（C5 の InvitationPage）。items・page・size（20）・total・invitationEnabled・unavailableReasons"
  - name: InvitationView
    description: "リンクの確かめの結果（C6 の InvitationView）。email・language だけ"
  - name: RegistrationInput
    description: "登録の完了の入力（C6 の CompleteRequest）。token・displayName・password・passwordConfirmation・language・theme・fontSize。password と passwordConfirmation は文字列にすると伏せる"
  - name: LinkRejection
    description: "リンクを拒否した理由（内部だけ）。監査の failureReason にだけ使い、応答は理由によらず REGISTRATION_LINK_INVALID（BR7.5・BR7.6）"
    allowed: [INVITATION_NOT_FOUND, INVITATION_ALREADY_USED, INVITATION_CANCELLED, INVITATION_EXPIRED, EMAIL_ALREADY_REGISTERED]
```

## エンティティの要約

| エンティティ | 種類 | 内容 |
|---|---|---|
| Invitation | 新しい表 | 招待先のメールアドレス（正規化済み）・言語・トークンのハッシュ（一意）・招待した管理者・招待した日時・有効期限・送信の結果（内部は PENDING・SENT・FAILED）・状態（PENDING・COMPLETED・CANCELLED・REPLACED）・終わった日時・作った利用者。期限切れは保存せず時計で決める。同じメールアドレスの PENDING は1件まで |
| InvitationEvent | 新しい出来事（保存しない） | 招待・送り直し・取り消し・登録の完了・登録の失敗の5種類。AuditLog が確定の後に記録する |
| AuditEvent | 既存の表（値だけ足す） | 出来事の種類5つと失敗の理由5つ（うち EMAIL_ALREADY_REGISTERED は Q4 A で足す）。列は U2 が足した対象の2列を使う |
| InvitationToken・InvitationAvailability・InvitationSummary・InvitationPage・InvitationView・RegistrationInput・LinkRejection | 値の型 | トークンの値（保存しない）、招待を使える設定か、応答の形、登録の完了の入力、拒否の理由（監査だけに使う） |
