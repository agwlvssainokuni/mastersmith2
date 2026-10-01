# Entities — U3 利用者の管理の API（u3-user-admin-api）

出典: 単位 `aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`（U3）、契約 `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`（C1・C2・C3・C6・C8）、部品と ADR は `aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/components.md`・`decisions.md`（ADR-001・ADR-003・ADR-006・ADR-007）、この段の答え `functional-design-questions.md`（Q1〜Q7 すべて A、設計の要点 1〜14）。既存の定義は `backend/src/main/java/cherry/mastersmith/user/domain/User.java`・`user/domain/EmailAddress.java`・`user/domain/DisplayName.java`・`user/domain/Language.java`・`user/domain/RedactedText.java`・`user/domain/FieldErrorReason.java`・`user/domain/RequestOrigin.java`・`auth/domain/LoginAttemptState.java`・`auth/domain/LockPolicy.java`・`auth/repository/LoginAttemptStateRepository.java`・`audit/domain/AuditEventType.java`・`audit/domain/AuditFailureReason.java`・`access/domain/AccessProblemTypes.java` と、先に設計した U1 の `aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/functional-design/entities.md` で突き合わせた。

U3 が内部DB に新しく持つ表と列は無い（スキーマの変更は無い）。部品 UserAdministration（パッケージ `useradmin`）は自分の表を持たず（`components.md` の `entities: []`）、一覧の行も保存しない。U3 が触れる内部DB のデータは、既存の利用者（User）・ロックの状態（LoginAttemptState）・監査の記録（AuditEvent）の3つで、読み書きの仕方と、監査の種類と理由の値を足す。停止の列 `suspended` は U1 が V9 で足す（C1）。

U3 の中心は、部品の間と画面との間で受け渡す値の型である。どれもアプリが独自に持つ業務の値で、保存はしない。フレームワークが持つ仕組み（認証の文脈、トランザクション、要求の文字列の型の変換の登録、トレースの属性、エラー応答の組み立て）はエンティティにせず、`rules.md` の決まりとして書く（`project.md` の Code Style）。

```yaml
entities:
  - name: User
    description: >-
      利用者（既存の表 users、持ち主は UserAccount）。形は変えない（U1 の V9 の suspended を含む）。U3 は C8 の口で、管理者の印・
      停止の状態・氏名・言語を書き換え、一覧と検索のために読む。メールアドレス・パスワード・テーマ・文字の大きさは U3 から変えない（FR6.5）
    attributes:
      - { name: userId, type: long, required: true, unique: true, constraints: "既存。DB の連番。一覧の並びの2つ目の鍵（BR1.1）と、行の排他の順の鍵（BR3.1）" }
      - { name: email, type: string, required: true, unique: true, max: 254, constraints: "既存。保存のときに小文字にそろえてある。一覧の応答と検索の対象（BR1.5）。U3 は変えない。ログ・監査・エラー応答に出さない（BR7.4）" }
      - { name: passwordHash, type: string, required: true, max: 100, constraints: "既存。U3 は読まない・渡さない・応答に含めない（BR7.5）" }
      - { name: admin, type: boolean, required: true, constraints: "既存（列 admin_flag）。印を付ける・外す操作だけが C8 の setAdmin で書き換える（BR4.1・BR4.2）" }
      - { name: suspended, type: boolean, required: true, default: false, constraints: "U1 が V9 で足す。止める・停止を解く操作だけが C1 の setSuspended で書き換える（BR4.3・BR4.4）" }
      - { name: createdAt, type: timestamp, required: true, constraints: "既存。UTC の時点。一覧の並びの1つ目の鍵で、応答の registeredAt（BR1.1・BR1.8）" }
      - { name: displayName, type: string, required: true, max: 254, constraints: "既存（V7）。254 コードポイント・前後の空白を除いた値。氏名と言語の変更が書き換える（BR5.2）。検索の対象（BR1.5）" }
      - { name: language, type: enum, required: true, allowed: [ja, en], constraints: "既存（V7）。氏名と言語の変更が書き換える（BR5.2）" }
      - { name: theme, type: enum, required: true, allowed: [light, dark, system], constraints: "既存（V7）。U3 は変えない（FR6.5）" }
      - { name: fontSize, type: enum, required: true, allowed: [sm, md, lg], constraints: "既存（V7）。U3 は変えない（FR6.5）" }
    constraints:
      - "「有効な管理者」は admin が true かつ suspended が false の利用者。ロック中も数える（BR3.2）"
      - "有効な管理者が 0 人になる書き換え（印を外す・止める）は受け付けない（BR3.2、PM の Forbidden）"
      - "admin と suspended を変える口は C8 の setAdmin と C1 の setSuspended だけで、呼ぶのは UserAdministration の操作だけ（BR7.3）"
    relationships:
      - "User 1 ← 0..1 LoginAttemptState（subjectId。一覧のロックの判定と、失敗回数を戻す操作）"
      - "User 1 ← * AuditEvent（actor_user_id は操作した管理者、target_user_id は対象の利用者）"

  - name: LoginAttemptState
    description: >-
      ロックの状態（既存の表 login_attempt_states、持ち主は Authentication）。形は変えない。U3 は一覧のためにページの利用者の行を
      排他なしで読み（BR1.7）、失敗回数を戻す操作でこの行だけを排他して 0 と解除の予定の時刻なしに書く（BR4.5）
    attributes:
      - { name: subjectId, type: long, required: true, unique: true, constraints: "既存。利用者 ID（正の値）、ダミーの行は −1〜−8。U3 はダミーの行を読まない・書かない（BR1.7・BR2.7）" }
      - { name: consecutiveFailures, type: int, required: true, min: 0, constraints: "既存。U3 は 1 以上かどうかだけを判定に使い、値そのものを useradmin に渡さない（BR1.7）。失敗回数を戻す操作で 0 にする（BR4.5）" }
      - { name: lockedUntil, type: timestamp, required: false, constraints: "既存。今の時刻 < lockedUntil のときだけロック中（BR1.7）。失敗回数を戻す操作で無しにする（BR4.5）" }
    constraints:
      - "失敗回数を戻す操作は、行が無い利用者に行を作らない（BR4.5）"
      - "既存の決まり（LockPolicy）では lockedUntil が入る行は失敗回数がしきい値以上（1 以上）になる。ただし U3 はこの性質に頼らず、戻せるか（resettable）を「失敗回数が 1 以上、または lockedUntil がある」と決め、性質が崩れた行でも「ロック中なのに戻せない」組を生じさせない（BR1.7・BR4.5、R-05）"

  - name: AuditEvent
    description: >-
      監査の記録（既存の表 audit_events、持ち主は AuditLog）。追記だけで、列は足さない（ADR-006）。出来事の種類を5つ、失敗の理由を
      4つ足し、既存の理由 USER_NOT_FOUND・NOT_ADMIN を管理の操作にも使う（C6、BR6.1）
    attributes:
      - { name: auditEventId, type: long, required: true, unique: true, constraints: "既存" }
      - { name: occurredAt, type: timestamp, required: true, constraints: "既存。注入した時計の UTC の時点" }
      - name: eventType
        type: enum
        required: true
        max: 32
        allowed: [USER_ADMIN_GRANTED, USER_ADMIN_REVOKED, USER_SUSPENDED, USER_RESUMED, LOGIN_FAILURES_RESET]
        constraints: "既存の AuditEventType に足す5つ（ここには足す値だけを書く）。成功も失敗も同じ種類で、result で分ける（C6）。最長は LOGIN_FAILURES_RESET の 20 文字"
      - { name: result, type: enum, required: true, allowed: [SUCCESS, FAILURE], constraints: "既存の AuditResult" }
      - name: failureReason
        type: enum
        required: false
        max: 32
        allowed: [USER_NOT_FOUND, SELF_OPERATION, TARGET_SUSPENDED, NO_CHANGE, LAST_ACTIVE_ADMIN, NOT_ADMIN]
        constraints: >-
          管理の操作の失敗で使う値（ここには使う値だけを書く）。SELF_OPERATION・TARGET_SUSPENDED・NO_CHANGE・LAST_ACTIVE_ADMIN を
          既存の AuditFailureReason に足す。USER_NOT_FOUND と NOT_ADMIN は既存の値を使う（NOT_ADMIN は操作した人の確かめ直しで外れていた
          とき、BR2.6）。成功のときは空。最長は LAST_ACTIVE_ADMIN の 17 文字
      - { name: actorUserId, type: long, required: true, references: "User.userId", constraints: "既存（V6）。操作した管理者。要求の文脈（認証の主体）から読む（BR2.8）" }
      - { name: targetUserId, type: long, required: true, constraints: "既存（V7）。参照の制約は無い。要求の userId をそのまま入れる（対象がいないときも、FR7.6、BR6.2）" }
      - { name: enteredEmail, type: string, required: false, constraints: "既存。管理の操作では空（メールアドレスを残さない、BR6.2）" }
      - { name: sourceIp, type: string, required: true, max: 45, constraints: "既存。要求の送り手の情報" }
      - { name: userAgent, type: string, required: false, max: 1024, constraints: "既存" }
      - { name: traceId, type: string, required: false, max: 64, constraints: "既存" }
    constraints:
      - "管理の操作の行に、パスワード・ハッシュ値・トークン・失敗回数・検索の文字・メールアドレス・氏名を入れない（C6 の never_recorded、BR6.2）"
      - "一覧の閲覧・入力の誤り・氏名と言語の変更・排他の待ちの上限切れは記録しない（BR6.4）"

value_types:
  # --- 一覧（C3 GET /、C8 findAdminPage・lockViewsOf）
  - name: SearchText
    description: >-
      検索の文字（置き場は user.domain、既存の RedactedText の隣。Q1 A）。controller の引数の時点からこの型で受け、業務処理と
      UserAccount へもこの型のまま渡す。文字列にすると値を伏せる（BR1.3・BR7.4）
    attributes:
      - { name: value, type: string, required: true, constraints: "要求の q の生の値。型の変換では包むだけで検証しない（BR1.3）" }
    operations:
      - "前後の空白（Unicode の White_Space。半角・全角とも、氏名と同じ範囲）を除いた値を返す（BR1.4）"
      - "除いた後のコードポイントの数が 254 を超えるかを返す（BR1.4）"
      - "検索のパターンを返す: 除いた後の値を Locale.ROOT で小文字にし、\\ と % と _ をエスケープし、前後に % を付けた値を RedactedText で返す（BR1.5、R-01・R-07）。戻り値も String にしない"
    constraints:
      - "文字列にすると値を伏せる（例 ***）。等しさと並べ替えに値を使っても、ログやトレースに値が出る形にしない"
      - "record とし、変換（小文字化・エスケープ）を record の中で行う（TraceAspect は record を対象にしないため、変換の途中の値がログに出ない）"
      - "controller・業務処理・UserAccount・UserRepository の口は、この型か、ここから作った RedactedText のパターンを受け、String を受けない（BR7.4）"
  - name: UserAdminSummary
    description: >-
      UserAccount が useradmin へ渡す利用者の要約（user.service に置く）。パスワードのハッシュ値は持たない。文字列にすると
      メールアドレスと氏名を伏せる（BR7.4）
    attributes:
      - { name: userId, type: long }
      - { name: email, type: string, constraints: "小文字にそろえたメールアドレス" }
      - { name: displayName, type: string }
      - { name: language, type: enum, allowed: [ja, en] }
      - { name: admin, type: boolean }
      - { name: suspended, type: boolean }
      - { name: registeredAt, type: timestamp, constraints: "users.created_at" }
  - name: UserAdminSlice
    description: "一覧の1ページの読み出しの結果（C8 findAdminPage の戻り値）"
    attributes:
      - { name: items, type: "list of UserAdminSummary", constraints: "0〜20 件。並びは BR1.1" }
      - { name: total, type: long, min: 0, constraints: "検索の条件に当たる全体の件数（BR1.6）" }
  - name: LockView
    description: >-
      1人の利用者のロックの判定の結果（auth.domain に置き、判定は純粋な関数。C8 lockViewsOf の戻り値の値）。失敗回数そのものは
      持たない（FR1.6、BR1.7）
    attributes:
      - { name: locked, type: boolean, constraints: "解除の予定の時刻があり、今の時刻 < 解除の予定の時刻" }
      - { name: lockedUntil, type: timestamp, required: false, constraints: "locked が true のときだけ値を持つ" }
      - { name: resettable, type: boolean, constraints: "表の失敗回数が 1 以上、または解除の予定の時刻がある（解除の予定の時刻を過ぎた利用者を含む、M2 B。後者は R-05 の守り）。行が無ければ false" }
    constraints:
      - "行の無い利用者は (false, 無し, false)（BR1.7）"
  - name: AdminUser
    description: >-
      一覧の応答の1行（C3 の AdminUser、useradmin.web に置く応答の record）。決めた項目のほかを持たない。文字列にすると
      メールアドレスと氏名を伏せる（BR1.8・BR7.4）
    attributes:
      - { name: userId, type: long }
      - { name: email, type: string }
      - { name: displayName, type: string }
      - { name: language, type: enum, allowed: [ja, en] }
      - { name: admin, type: boolean }
      - { name: suspended, type: boolean }
      - { name: locked, type: boolean }
      - { name: lockedUntil, type: timestamp, required: false, constraints: "ISO 8601 の UTC（Z 付き）。locked が false なら項目を出さないか null" }
      - { name: resettable, type: boolean }
      - { name: registeredAt, type: timestamp, constraints: "ISO 8601 の UTC（Z 付き）" }
      - { name: self, type: boolean, constraints: "行の userId が操作している管理者の利用者 ID と等しいか（BR1.8）" }
  - name: AdminUserPage
    description: "一覧の応答（C3 の AdminUserPage）"
    attributes:
      - { name: items, type: "list of AdminUser" }
      - { name: page, type: int, min: 1, constraints: "受けたページの番号（指定が無ければ 1）" }
      - { name: size, type: int, constraints: "20 で固定" }
      - { name: total, type: long, min: 0 }

  # --- 操作（C3 の5つの POST、C8 の排他の口）
  - name: AdminOperation
    description: "状態を変える5つの操作の区分（useradmin.domain）。拒否の判定（BR2.1・BR2.2）と監査の種類（BR6.1）の鍵"
    allowed: [GRANT_ADMIN, REVOKE_ADMIN, SUSPEND, RESUME, RESET_LOGIN_FAILURES]
  - name: RejectionReason
    description: "業務の拒否の理由（useradmin.domain）。判定の順はこの並び（BR2.1）。code（BR2.3）と監査の理由（BR6.1）に1対1で写す"
    allowed: [USER_NOT_FOUND, SELF_OPERATION, TARGET_SUSPENDED, NO_CHANGE, LAST_ACTIVE_ADMIN]
  - name: OperationFacts
    description: >-
      拒否の判定の純粋な関数に渡す事実の組（useradmin.domain）。業務処理が排他の後に読んだ値から作る。値は真偽だけで、個人に関する
      値を持たない
    attributes:
      - { name: targetExists, type: boolean }
      - { name: targetIsOperator, type: boolean }
      - { name: targetSuspended, type: boolean }
      - { name: targetAdmin, type: boolean }
      - { name: targetResettable, type: boolean, constraints: "失敗回数を戻す操作だけで使う（LockView の resettable と同じ定義）" }
      - { name: leavesNoActiveAdmin, type: boolean, constraints: "印を外す・止めるで、排他の後の有効な管理者から対象を除くと 0 人になるか（BR3.2）" }
  - name: AdminRowsLock
    description: "C8 lockAdminRowsInIdOrder の結果（user.service）。Locked か Busy のどちらか"
    variants:
      - { name: Locked, attributes: ["target: UserAdminSummary または無し", "activeAdminIds: 利用者 ID の集合（排他の後に数えた有効な管理者）"] }
      - { name: Busy, attributes: [] }
  - name: UserRowLock
    description: "C8 lockUserRow の結果（user.service）。停止を解く操作だけが使う（Q4 A）"
    variants:
      - { name: Locked, attributes: ["target: UserAdminSummary または無し"] }
      - { name: Busy, attributes: [] }
  - name: LoginFailureResetPreparation
    description: >-
      失敗回数を戻す操作の1段目の結果（auth.service）。ロックの状態の行だけを排他して戻せるかを判定する。失敗回数そのものは持たない
      （BR4.5、functional-spec.md 8節の差 D6）
    variants:
      - { name: Ready, attributes: [] }
      - { name: NothingToReset, attributes: [], constraints: "行が無い、または失敗回数が 0 かつ解除の予定の時刻が無い（BR4.5、R-05）" }
      - { name: Busy, attributes: [], constraints: "行の排他の待ちの上限切れ" }
  - name: OperationResult
    description: "業務処理（useradmin.service）が5つの操作で返す結果。controller が場合を尽くして応答に変える（BR2.3）"
    variants:
      - { name: Done, attributes: [], constraints: "204" }
      - { name: Rejected, attributes: ["reason: RejectionReason"], constraints: "404 か 409" }
      - { name: OperatorNotAdmin, attributes: [], constraints: "操作した人の確かめ直しで外れていた。403 ACCESS_DENIED（BR2.6）" }
      - { name: Busy, attributes: [], constraints: "409 USER_ADMIN_BUSY（BR3.5）" }

  # --- 氏名と言語の変更（C3 PUT /{userId}/profile、C8 updateProfile）
  - name: ProfileRequest
    description: "氏名と言語の変更の要求の本文（useradmin.web）。知らない項目は無視する（BR5.4）。文字列にすると氏名を伏せる"
    attributes:
      - { name: displayName, type: string, required: true, constraints: "検証は自分のプリファレンスと同じ（BR5.1）" }
      - { name: language, type: string, required: true, constraints: "ja か en（小文字の完全な一致、BR5.1）" }
  - name: ProfileCommand
    description: >-
      氏名と言語の変更の入力（user.service の record。C8 updateProfile の入力）。useradmin.web が ProfileRequest から作り、業務処理と
      UserAccount へこの型のまま渡す。既存の PreferencesCommand と同じ形（BR5.2、R-01）
    attributes:
      - { name: displayName, type: string, constraints: "要求の生の値（検証の前）" }
      - { name: language, type: string, constraints: "要求の生の値（検証の前）" }
    constraints:
      - "文字列にすると氏名を伏せる（言語は出してよい）"
  - name: ProfileUpdate
    description: >-
      検証を通った氏名と言語の組（user.domain の record）。UserAccount が BR5.1 の検証の後に作り、UserRepository の更新の口が
      この型で受ける。既存の Preferences と同じ形（BR5.2、R-01）
    attributes:
      - { name: displayName, type: string, constraints: "前後の空白を除いた値。作れるのは決まりに合う値だけ" }
      - { name: language, type: "Language（既存）" }
    constraints:
      - "文字列にすると氏名を伏せる。問い合わせでは SpEL（例 :#{#profile.displayName()}）で取り出し、String の引数にしない"
  - name: ProfileUpdateResult
    description: "C8 updateProfile の結果（user.service）"
    variants:
      - { name: Updated, attributes: [] }
      - { name: NotFound, attributes: [] }
      - { name: Invalid, attributes: ["errors: 項目の名前（displayName・language）と理由（FieldErrorReason）の一覧。入れた値は持たない"] }

  # --- 監査の出来事（C6）
  - name: UserAdminAuditEvent
    description: >-
      管理の操作の監査の出来事（useradmin.domain に置く。audit が受けて記録する、ADR-001・ADR-006）。操作の1回につき1件（BR6.1）
    attributes:
      - { name: operation, type: "AdminOperation", constraints: "audit が出来事の種類5つに写す" }
      - { name: actorUserId, type: long }
      - { name: targetUserId, type: long, constraints: "要求の userId のまま" }
      - { name: succeeded, type: boolean }
      - { name: failure, type: enum, required: false, allowed: [USER_NOT_FOUND, SELF_OPERATION, TARGET_SUSPENDED, NO_CHANGE, LAST_ACTIVE_ADMIN, NOT_ADMIN], constraints: "失敗のときだけ。audit が AuditFailureReason に写す" }
      - { name: occurredAt, type: timestamp, constraints: "注入した時計" }
      - { name: sourceIp, type: string, constraints: "要求の送り手の情報（既存の RequestOrigin から）" }
      - { name: userAgent, type: string, required: false }
      - { name: traceId, type: string, required: false }
    constraints:
      - "メールアドレス・氏名・検索の文字・失敗回数・トークンを持たない（BR6.2）"
```

## エンティティの要約

| エンティティ・値 | 種類 | この単位での変化・役目 |
|---|---|---|
| User | 既存の表（形は変えない） | 管理者の印・停止・氏名・言語を C8 と C1 の口で書き換える。登録した日時の古い順に一覧・検索する。印のある行と対象の行を利用者 ID の順に排他する |
| LoginAttemptState | 既存の表（形は変えない） | 一覧のためにページの行を排他なしで読む。失敗回数を戻す操作でこの行だけを排他し、0 と解除の予定の時刻なしに書く。行を作らない |
| AuditEvent | 既存の表（列は変えない） | 種類5つ・理由4つを足し、既存の USER_NOT_FOUND・NOT_ADMIN を使う。対象は要求の利用者 ID のまま |
| SearchText | 値（新しい、user.domain） | 検索の文字を伏せ字のまま受け渡す。前後の空白を除き、254 コードポイントまで。Locale.ROOT の小文字化とエスケープをした検索のパターンを RedactedText で返す |
| ProfileCommand・ProfileUpdate | 値（新しい、user.service・user.domain） | 氏名と言語の変更の入力と、検証を通った組。どちらも氏名を伏せ字のまま repository の口まで渡す |
| UserAdminSummary・UserAdminSlice | 値（新しい、user.service） | 一覧と排他の口が返す利用者の要約。ハッシュ値を持たず、伏せ字で文字列になる |
| LockView | 値（新しい、auth.domain） | ロック中か・解除の予定の時刻・戻せるかの3つだけ |
| AdminUser・AdminUserPage・ProfileRequest | 値（新しい、useradmin.web） | 画面との受け渡し（C3）。応答に含めない値を持たない |
| AdminOperation・RejectionReason・OperationFacts・OperationResult | 値（新しい、useradmin） | 拒否の判定の純粋な関数の入出力と、業務処理の結果 |
| AdminRowsLock・UserRowLock・LoginFailureResetPreparation・ProfileUpdateResult | 値（新しい、user.service・auth.service） | C8 の口の結果。上限切れは Busy で返す |
| UserAdminAuditEvent | 値（新しい、useradmin.domain） | 管理の操作の監査の出来事。audit が受けて記録する |
