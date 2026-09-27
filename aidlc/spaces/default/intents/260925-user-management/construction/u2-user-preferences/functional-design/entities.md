# Entities — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

出典: 単位 `aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`（U2）、契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`（C2・C3・C4・C8）、ADR-003・ADR-004・ADR-008（`aidlc/spaces/default/intents/260925-user-management/inception/domain-design/decisions.md`）、この段の答え `functional-design-questions.md`（Q1 A・Q2 B・Q3 A・Q4 A）。既存の定義は `backend/src/main/java/cherry/mastersmith/user/domain/User.java`・`audit/domain/AuditEvent.java`・`AuditEventType.java`・`AuditFailureReason.java`・`AuditResult.java` と `backend/src/main/resources/db/migration/`（V2・V4・V6）で突き合わせた。

U2 が内部DB に持つデータは、既存の利用者（User）に足す4つの属性と、既存の監査の記録（AuditEvent）に足す対象の2列だけである。パスワードの変更の知らせは保存しない出来事で、AuditLog が確定の後に受けて記録する。ログインの状態・トークン・ロックの状態は既存の Authentication の持ち物で、U2 は形を変えない。フレームワークが持つ仕組み（認証の文脈、トレースの情報、接続の設定）はエンティティにせず、`rules.md` の決まりとして書く（`project.md` の Code Style）。

```yaml
entities:
  - name: User
    description: >-
      利用者（既存の表 users、持ち主は UserAccount）。この単位で氏名と表示の設定の4つを足す（ADR-003）。
      パスワードはハッシュだけを持ち、ハッシュは UserAccount の外へ出さない（既存の ADR-001 の決まり）
    attributes:
      # --- 既存（変えない）
      - { name: userId, type: long, required: true, unique: true, constraints: "既存。DB の連番。外へ渡す識別" }
      - { name: email, type: string, required: true, unique: true, max: 254, constraints: "既存。前後の空白を除き小文字にそろえた値（EmailAddress.normalize）。この単位では変えない" }
      - { name: passwordHash, type: string, required: true, max: 100, constraints: "既存。bcrypt の形式の文字列だけ。パスワードの変更（BR4.3）だけが書き換える" }
      - { name: admin, type: boolean, required: true, constraints: "既存（列 admin_flag）。この単位では変えない。招待からの作成は常に false（C2）" }
      - { name: createdAt, type: timestamp, required: true, constraints: "既存。UTC の時点" }
      # --- 足す（V7、BR9.1）
      - name: displayName
        type: string
        required: true
        min: 1
        max: 254
        constraints: >-
          氏名。前後の空白（Unicode の White_Space の文字）を除いた値を保存する（BR1.1）。除いた後の長さを
          コードポイントで数えて 1〜254（BR1.2・BR1.3）。制御文字（Cc）と見えない書式の文字（Cf）を含まない（BR1.4）。
          Unicode の正規化はしない（BR1.5）。保存の長さは UTF-16 の単位で最大 508（サロゲートペアを含む値が上限の2倍になりうるため。既存の監査の表と同じ考え方）
        default: "既存の利用者はメールアドレス（BR9.1）"
      - { name: language, type: enum, required: true, allowed: [ja, en], default: ja, constraints: "表示とメールの言語（FR5.2）。値は小文字の文字列そのまま" }
      - { name: theme, type: enum, required: true, allowed: [light, dark, system], default: system, constraints: "テーマ（FR5.2）。system の解決は画面（U4）が行い、サーバーは値をそのまま持つ" }
      - { name: fontSize, type: enum, required: true, allowed: [sm, md, lg], default: md, constraints: "文字の大きさ（FR5.2、make-you-chic-ui の3段階）" }
    constraints:
      - "email は一意（既存の一意の制約）。作成で重なれば EmailAlreadyUsed（BR5.2）"
      - "displayName・language・theme・fontSize の4つは、プリファレンスの保存（BR3.3）でまとめて置き換わり、一部だけが変わることは無い"
      - "プリファレンスの保存は4つの列だけを、パスワードの変更は passwordHash だけを書き換え、ほかの列を読み直した値で上書きしない（BR3.4・BR4.3）"
      - "版による排他は持たない。同じ列の同時の書き込みは後に確定したものが勝つ（BR3.4）"
    relationships:
      - "User 1 ← * AuditEvent（actor_user_id。操作した人。既存の V6 の列）"
      - "User 1 ← * AuditEvent（target_user_id。対象の利用者。この単位で足す）"
      - "User 1 ← 0..1 LoginAttemptState（既存。UserCreatedEvent で Authentication が作る）"
      - "User 1 ← * RefreshToken（既存。この単位では触れない）"

  - name: AuditEvent
    description: >-
      監査の記録（既存の表 audit_events、持ち主は AuditLog）。追記だけで変えない。この単位で対象の2列と、
      出来事の種類・失敗の理由の値を足す（ADR-008、C8）。足す列の一覧の正は U2 が持つ
    attributes:
      # --- 既存（変えない。C8 の existing_columns_used と既存の定義のとおり）
      - { name: auditEventId, type: long, required: true, unique: true, constraints: "既存。DB の連番" }
      - { name: occurredAt, type: timestamp, required: true, constraints: "既存。UTC の時点" }
      - { name: eventType, type: enum, required: true, max: 32, allowed: [LOGIN_SUCCEEDED, LOGIN_FAILED, LOGGED_OUT, ACCESS_DENIED, DSL_GENERATED, DSL_SUBMITTED, DSL_SUBMISSION_REJECTED, DSL_APPLIED, DSL_PREVIEW_DISCARDED, PASSWORD_CHANGED], constraints: "既存の AuditEventType に PASSWORD_CHANGED（16 文字）を足す。U3 の招待・登録の種類は U3 が足す（C8）" }
      - { name: result, type: enum, required: true, max: 16, allowed: [SUCCESS, FAILURE], constraints: "既存の AuditResult。値は足さない" }
      - { name: enteredEmail, type: string, required: false, max: 508, constraints: "既存。PASSWORD_CHANGED では入れない（空）" }
      - { name: failureReason, type: enum, required: false, max: 32, allowed: [USER_NOT_FOUND, PASSWORD_MISMATCH, ACCOUNT_LOCKED, NOT_ADMIN, TOKEN_MISSING, TOKEN_MALFORMED, TOKEN_INVALID, CURRENT_PASSWORD_MISMATCH], constraints: "既存の AuditFailureReason に CURRENT_PASSWORD_MISMATCH（25 文字）を足す。result が FAILURE のときだけ入る。U3 の値は U3 が足す" }
      - { name: sourceIp, type: string, required: true, max: 45, constraints: "既存。接続元IP" }
      - { name: userAgent, type: string, required: false, max: 1024, constraints: "既存" }
      - { name: requestPath, type: string, required: false, max: 1024, constraints: "既存。アクセスの拒否のときだけ。PASSWORD_CHANGED では空" }
      - { name: traceId, type: string, required: false, max: 64, constraints: "既存" }
      - { name: actorUserId, type: long, required: false, references: "User.userId", constraints: "既存（V6）。PASSWORD_CHANGED では本人" }
      - { name: dslHash, type: string, required: false, max: 64, constraints: "既存（V6）。DSL の出来事だけ。この単位では使わない" }
      - { name: dslSource, type: string, required: false, max: 16, constraints: "既存（V6）。DSL の出来事だけ。この単位では使わない" }
      - { name: rejectionKind, type: string, required: false, max: 32, constraints: "既存（V6）。DSL の受け付けなかった投入の理由だけ。今回の出来事の失敗の理由には使わない（BR7.1）" }
      # --- 足す（V7、BR9.2。C8 の columns_added_by_U2）
      - { name: targetUserId, type: long, required: false, references: "User.userId（参照の制約は置かない。追記だけの記録のため）", constraints: "対象の利用者。PASSWORD_CHANGED では本人。U3 は REGISTRATION_COMPLETED で使う" }
      - { name: targetInvitationId, type: long, required: false, constraints: "対象の招待。U2 は使わず、U3 の招待・登録の出来事が使う" }
    constraints:
      - "追記だけで、作った後に値を変えない・消さない（既存の決まり）"
      - "足す2列はどちらも空を許し、既存の出来事と1つ前の版のアプリの追記では空のまま（後方互換）"
      - "パスワード（今・新しい・確かめの値）とそのハッシュ、トークンの値はどの列にも入れない（BR7.4）"
    relationships:
      - "AuditEvent * → 0..1 User（actorUserId）"
      - "AuditEvent * → 0..1 User（targetUserId）"

  - name: PasswordChangedEvent
    description: >-
      パスワードの変更の出来事（アプリの中の知らせ、保存しない。C8 の PASSWORD_CHANGED）。UserAccount が出し、
      AuditLog が確定の後に受けて AuditEvent を1件追記する（BR7.2・BR7.3）
    attributes:
      - { name: userId, type: long, required: true, references: "User.userId", constraints: "変えた（変えようとした）本人。actorUserId と targetUserId の両方に入る" }
      - { name: result, type: enum, required: true, allowed: [SUCCESS, FAILURE] }
      - { name: failureReason, type: enum, required: false, allowed: [CURRENT_PASSWORD_MISMATCH], constraints: "result が FAILURE のときだけ" }
      - { name: occurredAt, type: timestamp, required: true, constraints: "注入できる時計の値" }
      - { name: sourceIp, type: string, required: true, constraints: "既存の送り手の情報（ClientInfo）と同じ取り方" }
      - { name: userAgent, type: string, required: false }
      - { name: traceId, type: string, required: false, constraints: "既存の認証の出来事と同じ取り方" }
    constraints:
      - "パスワードの値・ハッシュ・メールアドレスを持たない（文字列にしても出ない）"
      - "入力の誤り（BR4.1）で拒否した要求では出さない"

value_types:
  - name: DisplayName
    description: "氏名の決まりをまとめた純粋な関数（EmailAddress・PasswordPolicy と同じ形）。プリファレンスの保存と利用者の作成の両方で使う（BR1.6）"
    attributes:
      - { name: value, type: string, constraints: "BR1.1〜BR1.5 を満たした、前後の空白を除いた値" }
  - name: Preferences
    description: "氏名・言語・テーマ・文字の大きさの4つの組（C4 の Preferences と同じ形）。読み書きの単位"
    attributes:
      - { name: displayName, type: DisplayName }
      - { name: language, type: "enum [ja, en]" }
      - { name: theme, type: "enum [light, dark, system]" }
      - { name: fontSize, type: "enum [sm, md, lg]" }
  - name: NewUser
    description: "利用者の作成の入力（C2）。メールアドレス・氏名・パスワード・言語・テーマ・文字の大きさ・管理者か"
    attributes:
      - { name: email, type: string, constraints: "そろえる前の値でよい。作成の中で EmailAddress.normalize でそろえる" }
      - { name: displayName, type: DisplayName }
      - { name: password, type: Password, constraints: "既存の型（文字列にすると伏せる）。PasswordPolicy の作成時の規則に合う値" }
      - { name: language, type: "enum [ja, en]" }
      - { name: theme, type: "enum [light, dark, system]" }
      - { name: fontSize, type: "enum [sm, md, lg]" }
      - { name: admin, type: boolean, constraints: "招待からの作成は false、初期管理者は true" }
  - name: CreateUserResult
    description: "利用者の作成の結果の型（C2）"
    allowed: ["Created { userId }", "EmailAlreadyUsed {}"]
  - name: UserSummary
    description: "UserAccount の外へ渡す利用者の要約（既存）。userId・email・admin に displayName・language・theme・fontSize を足す。パスワードのハッシュを含まない"
```

## エンティティの要約

| エンティティ | 種類 | この単位での変化 |
|---|---|---|
| User | 既存の表を広げる | 氏名（1〜254 コードポイント）・言語（ja・en）・テーマ（light・dark・system）・文字の大きさ（sm・md・lg）を足す。既存の利用者はメールアドレス・ja・system・md |
| AuditEvent | 既存の表を広げる | 対象の2列（targetUserId・targetInvitationId、空を許す）と、種類 PASSWORD_CHANGED・失敗の理由 CURRENT_PASSWORD_MISMATCH を足す。result・failureReason・actorUserId は既存のものを使う |
| PasswordChangedEvent | 新しい出来事（保存しない） | 成功と今のパスワードの誤りのときに出し、AuditLog が確定の後に記録する |
| DisplayName・Preferences・NewUser・CreateUserResult・UserSummary | 値の型 | 氏名の決まり、4つの組、作成の入力と結果の型、外へ渡す要約の広げ |
