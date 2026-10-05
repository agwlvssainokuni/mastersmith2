# エンティティ（Entities）— U3 group

出典: `unit-of-work.md`（U3）、`unit-of-work-story-map.md`（US2.1・US2.2）、`requirements.md`（FR4.1・FR4.1a・FR4.3〜FR4.5・FR12・NFR1・NFR3.1・NFR5.1・C2）、`components.md`（GroupManagement・AuditLog）、`contract-summary.md`（C4・C6・C10）、ADR-001・ADR-002・ADR-006、この段の答え（`functional-design-questions.md` の Q1〜Q6 すべて A、まとめの確認 Looks correct）、`team.md`・`project.md`。

`project.md` の決まりにより、エンティティにはアプリが独自に持つデータと、単位の境界をまたぐ口の型だけを書く。Spring・Flyway・H2 の仕組み（排他の待ちの設定、移行のファイル）は決まり（`rules.md`）に書く。

```yaml
entities:
  # ---- グループとメンバー（group が持つ。内部DB） ----
  - name: Group
    description: 利用者をまとめるグループ。ロールの割り当て先になる（割り当ては U4 role が持つ）
    store: 内部DB の表 groups
    identifier: groupId
    attributes:
      - {name: groupId, type: long, required: true, unique: true, constraints: "自動で振る。作成の後は変わらない"}
      - {name: name, type: string, required: true, min_length: 1, max_length: 64, constraints: "GroupName の規則で正規化した値（前後の空白を取り除いた後、コードポイントで 1〜64、制御文字なし。BR1.1〜BR1.3）。列の長さは UTF-16 の 128 単位（64 コードポイントがすべてサロゲートペアでも収まる。承認の場の直し R-03）"}
      - {name: nameKey, type: string, required: true, unique: true, constraints: "name を大文字と小文字を区別しない形（Unicode の小文字。言語に依らない変換）にした値。Java の GroupName だけが作り、通常の列に保存して一意の制約を置く。DB の生成列にはしない（BR1.4、承認の場の直し R-04）。全角と半角は変換しない。列の長さは UTF-16 の 256 単位（サロゲートペアと、小文字化で文字が増える分を見込む。R-03）"}
      - {name: createdAt, type: instant, required: true, constraints: "UTC の時点。作成の後は変わらない"}
      - {name: updatedAt, type: instant, required: true, constraints: "UTC の時点。名前の変更で更新する。メンバーの足し外しでは変えない"}
    constraints:
      - "nameKey は表の中で一意（大文字と小文字だけが違う名前は同じ名前）"
      - "削除はメンバーが 0 で、ロールの割り当てが 0（問う口の答え）のときだけ（BR4.1）"
    relationships:
      - {to: GroupMembership, cardinality: "1 対 0..n", direction: owns}

  - name: GroupMembership
    description: グループのメンバー（1人の利用者が1つのグループに属すること）
    store: 内部DB の表 group_members
    identifier: "groupId + userId"
    attributes:
      - {name: groupId, type: long, required: true, references: Group.groupId, constraints: "外部キー（削除の制限）。最後の守り（BR5.4）"}
      - {name: userId, type: long, required: true, references: "User.userId（UserAccount が持つ）", constraints: "外部キー。登録の終わった利用者（users の行）だけ。停止中も可（BR3.1・BR3.2）"}
      - {name: addedAt, type: instant, required: true, constraints: "UTC の時点。詳細のメンバーの並びに使う（BR7.4）"}
    constraints:
      - "groupId と userId の組は一意（主キー）。重ねての追加は GROUP_NO_CHANGE（BR3.3・BR5.5）"
    relationships:
      - {to: Group, cardinality: "n 対 1", direction: "belongs to"}
      - {to: User, cardinality: "n 対 1", direction: "refers to（UserAccount の持ち物）"}

  # ---- 値の型（group.domain） ----
  - name: GroupName
    description: グループの名前の値。作るときに BR1.1〜BR1.3 で正規化と検証を行い、正しいものだけが作れる
    attributes:
      - {name: value, type: string, required: true, min_length: 1, max_length: 64}
      - {name: key, type: string, required: true, constraints: "大文字と小文字を区別しない比べのための値。nameKey を作る唯一の場所で、事前の重なりの判定と保存の両方がこの値を使う（R-04）"}
    constraints:
      - "検証の結果は INVALID_BLANK（空・空白だけ）・INVALID_TOO_LONG・INVALID_CONTROL_CHARACTER のどれかか、正しい値"

  - name: GroupOperation
    description: 監査と排他の待ち合わせの口に使う操作の区分
    attributes:
      - {name: value, type: enum, allowed_values: [CREATE, RENAME, DELETE, ADD_MEMBER, REMOVE_MEMBER]}

  - name: GroupMember
    description: 詳細の応答のメンバー1人分の値（個人に関する値を伏せ字の型で持つ）
    attributes:
      - {name: userId, type: long, required: true}
      - {name: displayName, type: "伏せ字の型（文字にすると *** になる）", required: true}
      - {name: email, type: "伏せ字の型", required: true}
      - {name: suspended, type: boolean, required: true}
      - {name: addedAt, type: instant, required: true}

  # ---- 単位の境界の口の型（group.service。契約 C4 と、この段の Q2・Q3 で足すもの） ----
  - name: GroupMembershipQuery
    description: group が出す読み取りと排他の口（U4 role が使う）
    attributes:
      - {name: groupIdsOfUser, type: "operation(userId) -> set<groupId>"}
      - {name: exists, type: "operation(groupId) -> boolean"}
      - {name: summaries, type: "operation(set<groupId>) -> list<GroupSummary(groupId, name)>"}
      - {name: lockForAssignment, type: "operation(groupId) -> GroupRowLock", constraints: "Q2 の答え A で足す。呼ぶ側のトランザクションの中だけで呼べる"}
      - {name: memberUserIds, type: "operation(set<groupId>) -> map<groupId, set<userId>>", constraints: "承認の場の直しで足す。まとめて1回で読み、存在しないグループは結果に含めない。U4 の割り当ての一覧（グループ経由の利用者）が使う"}

  - name: GroupRowLock
    description: グループの行の排他の結果
    attributes:
      - {name: variant, type: enum, allowed_values: ["Locked(exists: boolean)", "Busy"]}

  - name: GroupDeletionGuard
    description: group が定義し role が実装する、グループへのロールの割り当てを問う口（ADR-002。Q3 の答え A で数の方法を足す）
    attributes:
      - {name: canDelete, type: "operation(groupId) -> DeletionDecision"}
      - {name: assignedRoleCounts, type: "operation(set<groupId>) -> map<groupId, int>", constraints: "割り当てが無いグループは 0 を返す（キーを省かない）"}
    constraints:
      - "group は既定の実装を持たない。B3 では role のパッケージに仮の実装（数は 0・削除してよい）を置き、B5 で本物に置き換える（BR6.3）"

  - name: DeletionDecision
    attributes:
      - {name: variant, type: enum, allowed_values: ["Allowed", "Blocked(assignedRoles: int, 1 以上)"]}

  # ---- 監査の出来事（group.domain → audit が受ける） ----
  - name: GroupAuditEvent
    description: グループの操作ごとに1つ出す監査の出来事（確定の後に audit が記録する）
    attributes:
      - {name: operation, type: GroupOperation, required: true}
      - {name: actorUserId, type: long, required: true}
      - {name: targetGroupId, type: long, required: false, constraints: "作成の成功で作った ID。作成の名前の重なりでは空"}
      - {name: targetUserId, type: long, required: false, constraints: "メンバーの追加・外しだけ"}
      - {name: succeeded, type: boolean, required: true}
      - {name: failure, type: GroupAuditFailure, required: false, constraints: "成功なら空、失敗なら必須"}
      - {name: detail, type: GroupAuditDetail, required: false}
      - {name: occurredAt, type: instant, required: true}
      - {name: sourceIp, type: string, required: true}
      - {name: userAgent, type: string, required: false}
      - {name: traceId, type: string, required: false}

  - name: GroupAuditFailure
    attributes:
      - {name: value, type: enum, allowed_values: [GROUP_NOT_FOUND, USER_NOT_FOUND, GROUP_NAME_DUPLICATE, GROUP_IN_USE, NO_CHANGE]}

  - name: GroupAuditDetail
    description: 監査の detail に入れる中身。キーを決めた型だけから JSON を作る（BR8.5）
    attributes:
      - {name: variant, type: enum, allowed_values:
          ["Name(name)：作成・削除・名前の重なり・同じ名前への変更",
           "Rename(before, after)：名前の変更",
           "Membership(groupName)：メンバーの追加・外し",
           "InUse(name, members, assignedRoles)：使用中の削除"]}
    constraints:
      - "グループの名前と数だけを持つ。メールアドレス・氏名・パスワード・トークン・ハッシュ値は型に無い（BR8.7）"
      - "グループが無い（GROUP_NOT_FOUND）と利用者が無い（USER_NOT_FOUND）の拒否は detail を空にする"

  # ---- 監査の表と AuditEvent に足すもの（audit が持つ。U3 が足す、契約 C10） ----
  - name: AuditEvent（足す項目）
    description: 既存の監査の行に足す3項目。U4 role も同じ項目を使う
    store: 内部DB の表 audit_events に足す列（空を許す。参照の制約と索引は置かない）
    attributes:
      - {name: targetRoleId, type: long, required: false, constraints: "列 target_role_id。U3 では使わない（U4 が使う）"}
      - {name: targetGroupId, type: long, required: false, constraints: "列 target_group_id"}
      - {name: detail, type: string, required: false, max_length: 16384, constraints: "列 detail。JSON の文字列。上限を超える値は作らない（切り詰めない）。BR8.6"}
    constraints:
      - "既存の列と既存の行は変えない（前進のみ、前の版のアプリが動く）"

  - name: AuditEventType（足す値）
    attributes:
      - {name: value, type: enum, allowed_values: [GROUP_CREATED, GROUP_RENAMED, GROUP_DELETED, GROUP_MEMBER_ADDED, GROUP_MEMBER_REMOVED], constraints: "32 文字以内。一度決めたら変えない"}

  - name: AuditFailureReason（足す値）
    attributes:
      - {name: value, type: enum, allowed_values: [GROUP_NOT_FOUND, GROUP_NAME_DUPLICATE, GROUP_IN_USE], constraints: "USER_NOT_FOUND・NO_CHANGE は既存の値を使う。32 文字以内"}
```

## 要約

- グループの持ち物は `Group`（名前と、大文字と小文字を区別しない一意の鍵）と `GroupMembership`（グループと利用者の組）の2つの表。メンバーは利用者（UserAccount の持ち物）を外部キーで指す。
- グループへのロールの割り当ては U4 role が持ち、group は `GroupDeletionGuard`（数と削除の可否）で問うだけ。逆に U4 は `GroupMembershipQuery`（所属・有無・要約・グループの行の排他）で group を読む。依存の矢印は role → group の1本のまま。
- 監査は `GroupAuditEvent` を出し、audit が `audit_events` に記録する。U3 は監査の表に `target_role_id`・`target_group_id`・`detail`（JSON、上限 16,384 文字）の列を足し、U4 も同じ列を使う。
- 個人に関する値（メンバーの氏名・メールアドレス）は詳細の応答のためだけに読み、伏せ字の型で受け渡す。監査の `detail` には入れない。
