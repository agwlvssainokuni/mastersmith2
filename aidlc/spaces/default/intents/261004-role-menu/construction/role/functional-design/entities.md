# エンティティ — U4 role

出典: `functional-design-questions.md`（Q1〜Q9 の答えと、まとめの確認で承認した「この段で決める設計の要点」）、`components.md`（RoleManagement と Entity の注）、`contract-summary.md`（C4・C5・C7・C8・C10）、先に確定した単位の設計（`construction/group/functional-design/`・`construction/dsl-v2/functional-design/`）。

`project.md` の決まりにより、エンティティにはアプリが独自に持つデータと、単位の境界をまたぐ口・受け渡しの型だけを書く。Spring・Flyway・H2 の仕組み（排他の待ちの設定、移行のファイル、本文の大きさの上限の部品）は決まり（`rules.md`）に書く。型は論理の型で、列の長さは DB の文字の列（UTF-16 の文字の数）で数える。

```yaml
entities:
  # ---------- 内部DB に持つもの ----------
  - name: Role
    description: ロール。業務データの権限の設定のまとまりで、管理の権限は与えない（要件 C1）。組み込みのロールは無い（SM6 A）。
    attributes:
      - {name: roleId, type: long, required: true, unique: true, constraints: "内部DB が振る正の整数。並び・「最初のロール」の順に使う（BR7.1）"}
      - {name: name, type: string, required: true, min: 1, max: 64, constraints: "前後の空白を取り除いた後のコードポイントの数。制御文字を含まない（BR1.1〜BR1.3）。列は 128 文字（サロゲートペアを見込む）"}
      - {name: nameKey, type: string, required: true, unique: true, constraints: "name を大文字と小文字をそろえた値（Unicode の小文字、言語に依らない変換）。Java で1か所で作り通常の列に保存する（生成列にしない。group の直しで Group.nameKey も同じ作りにそろえる）。列は 256 文字（小文字化の膨らみを見込む）（BR1.4）"}
      - {name: createdAt, type: instant, required: true, constraints: "UTC の時点"}
      - {name: updatedAt, type: instant, required: true, constraints: "UTC の時点。名前の変更・権限の保存・import の置き換えで更新する"}
    constraints:
      - "nameKey は表の中で一意（大文字と小文字だけが違う名前は同じ名前）"
    relationships:
      - {to: PermissionSetting, cardinality: "0..n", direction: owns}
      - {to: UserRoleAssignment, cardinality: "0..n", direction: "referenced by"}
      - {to: GroupRoleAssignment, cardinality: "0..n", direction: "referenced by"}

  - name: PermissionSetting
    description: ロールの1つの対象（スキーマ・テーブル・カラム）への明示の設定。少なくとも1つの値が設定されている対象だけ行を持つ（BR4.2）。対象は名前で持ち、DSL への参照は持たない（ADR-004）。
    attributes:
      - {name: roleId, type: long, required: true, references: Role.roleId}
      - {name: schemaName, type: string, required: true, min: 1, max: 128, constraints: "コードポイントの数。大文字と小文字を区別する。列は 256 文字（BR4.3）"}
      - {name: tableName, type: string, required: true, min: 0, max: 128, constraints: "空の文字列はスキーマの階層の行（DSL の名前は1文字以上のため実の名前と重ならない。Q2: A）"}
      - {name: columnName, type: string, required: true, min: 0, max: 128, constraints: "空の文字列はスキーマ・テーブルの階層の行。tableName が空なら columnName も空"}
      - {name: level, type: "derived enum", allowed: [SCHEMA, TABLE, COLUMN], constraints: "保存しない。tableName と columnName の空かどうかから導く"}
      - {name: mainPermission, type: "enum or null", allowed: [NONE, READ, FULL], required: false, default: null, constraints: "null は「設定なし」"}
      - {name: createPermission, type: "boolean or null", required: false, default: null, constraints: "null は「設定なし」、true は可、false は不可。COLUMN の行では必ず null"}
      - {name: deletePermission, type: "boolean or null", required: false, default: null, constraints: "createPermission と同じ"}
      - {name: updatedAt, type: instant, required: true}
    constraints:
      - "主キーは (roleId, schemaName, tableName, columnName)（Q2: A）"
      - "mainPermission・createPermission・deletePermission の少なくとも1つが null でない"
      - "tableName が空なら columnName も空（表の制約）"
      - "columnName が空でない行は createPermission と deletePermission が null（表の制約）"
      - "Role への外部キー。ロールの削除では同じトランザクションの中で先に消す（BR3.3）"

  - name: UserRoleAssignment
    description: 利用者への直接のロールの割り当て（Q2: A。components.md の RoleAssignment を相手ごとに分けた片方）。
    attributes:
      - {name: roleId, type: long, required: true, references: Role.roleId}
      - {name: userId, type: long, required: true, references: "User.userId（UserAccount）"}
      - {name: assignedAt, type: instant, required: true}
    constraints:
      - "主キーは (roleId, userId)。主キーの違反は重ねての割り当て（BR6.3）"
      - "Role と users への外部キー（削除の制限）。利用者の削除の仕組みは今のアプリに無い"
      - "利用者の ID からの読み取りのため userId の索引を置く"

  - name: GroupRoleAssignment
    description: グループへのロールの割り当て（Q2: A）。
    attributes:
      - {name: roleId, type: long, required: true, references: Role.roleId}
      - {name: groupId, type: long, required: true, references: "Group.groupId（GroupManagement）"}
      - {name: assignedAt, type: instant, required: true}
    constraints:
      - "主キーは (roleId, groupId)"
      - "Role と groups への外部キー（削除の制限。group の BR5.4 の最後の守り）"
      - "グループの ID からの読み取り（数・問う口）のため groupId の索引を置く"

  - name: WorkRoleSelection
    description: 利用者が選んだ作業ロールの保存。読み取りの要求では書き換えない（契約 C5、Q9: A）。
    attributes:
      - {name: userId, type: long, required: true, unique: true, references: "User.userId（UserAccount）"}
      - {name: roleId, type: long, required: true, constraints: "Role への外部キーは置かない（Q3: A）。割り当ての外にあるロール・消えたロールを指すことがあり、そのときは読みで最初のロールに読み替える（BR7.2）"}
      - {name: updatedAt, type: instant, required: true}
    constraints:
      - "主キーは userId（1人に1行）"
      - "users への外部キーを置く"
      - "ロールの削除では、そのロールを指す行を同じトランザクションの中で消す（BR3.3）"

  # ---------- 値の型 ----------
  - name: RoleName
    description: ロールの名前の値（BR1.1〜BR1.4）。group の GroupName と同じ規則。
    attributes:
      - {name: value, type: string, required: true, constraints: "前後の空白を取り除いた後の値"}
      - {name: key, type: string, required: true, constraints: "nameKey と同じ作り（1か所の関数）"}

  - name: PermissionTarget
    description: 設定の対象の名前の組。
    attributes:
      - {name: schemaName, type: string, required: true}
      - {name: tableName, type: "string or null", required: false}
      - {name: columnName, type: "string or null", required: false, constraints: "tableName が null なら null"}

  - name: MainPermission
    allowed: [NONE, READ, FULL]

  - name: InheritedFrom
    description: 実効の値の出どころ（契約 C7 の inheritedFrom）。
    allowed: [EXPLICIT, TABLE, SCHEMA, DEFAULT]

  # ---------- 解決の口（契約 C5） ----------
  - name: WorkRoleRef
    attributes:
      - {name: roleId, type: long, required: true}
      - {name: name, type: string, required: true}

  - name: EffectivePermission
    attributes:
      - {name: main, type: MainPermission, required: true}
      - {name: create, type: boolean, required: true}
      - {name: delete, type: boolean, required: true}
    constraints:
      - "カラムの create・delete は、そのカラムのテーブルの実効の値（カラムは補助権限を持たない）"

  - name: PermissionSnapshot
    description: 1回の要求の中だけで使う、有効な作業ロールの設定の写し（契約 C5）。要求をまたいで持ち越さない。
    attributes:
      - {name: workRole, type: "WorkRoleRef or null", required: false}
      - {name: dslHash, type: "string or null", required: false, constraints: "読んだ時点の適用中の DSL の識別（Absent なら null）"}
      - {name: main, type: "operation(schemaName, tableName, columnName|null) -> MainPermission"}
      - {name: create, type: "operation(schemaName, tableName) -> boolean"}
      - {name: delete, type: "operation(schemaName, tableName) -> boolean"}
    constraints:
      - "workRole が null・DSL が Absent・対象が今の DSL に無いときは NONE・不可（BR5.3）"

  - name: UserRoleView
    description: 利用者のロールと出どころ（C7 の UserRole、C8 の roles の元）。
    attributes:
      - {name: roleId, type: long, required: true}
      - {name: name, type: string, required: true}
      - {name: sources, type: "list<RoleSource>", required: true, min: 1, constraints: "DIRECT を先に、グループはグループの ID の順"}

  - name: RoleDetail
    description: "GET /api/admin/roles/{roleId} の応答（契約 C7 の Role と同じ形。承認の場の直しで足した読み取り）"
    attributes: [roleId, name, createdAt, updatedAt]

  - name: RoleSource
    variants:
      - {name: DIRECT}
      - {name: GROUP, attributes: [groupId, groupName]}

  # ---------- 権限の YAML（Q4: A） ----------
  - name: RoleTransferDocument
    description: 書き出し・読み込みの YAML の形。知らない項目は誤り（BR9.5）。
    attributes:
      - {name: version, type: integer, required: true, allowed: [1]}
      - {name: roles, type: "map<roleName, RoleTransferRole>", required: true, constraints: "空の対応表を許す。キーはロールの名前（BR1 の規則）。鍵の重なりは中身の誤り"}
  - name: RoleTransferRole
    attributes:
      - {name: schemas, type: "map<schemaName, RoleTransferSchema>", required: false, default: "空"}
  - name: RoleTransferSchema
    attributes:
      - {name: main, type: "MainPermission", required: false}
      - {name: create, type: boolean, required: false}
      - {name: delete, type: boolean, required: false}
      - {name: tables, type: "map<tableName, RoleTransferTable>", required: false}
  - name: RoleTransferTable
    attributes:
      - {name: main, type: "MainPermission", required: false}
      - {name: create, type: boolean, required: false}
      - {name: delete, type: boolean, required: false}
      - {name: columns, type: "map<columnName, MainPermission>", required: false, constraints: "カラムは主権限の値だけ（対応表にしない）"}

  - name: RoleTransferLimits
    description: SafeYamlReader に渡す上限（Q5: A）。
    attributes:
      - {name: maxBytes, type: integer, default: 10485760, constraints: "仮の値。NFR 要件の段で応答時間の目標とあわせて確定（NFR2.4）"}
      - {name: maxDepth, type: integer, default: 10}
      - {name: maxAliases, type: integer, default: 0}
      - {name: maxExpandedNodes, type: integer, default: 1000000}

  - name: TransferError
    description: 確かめの拒否（ROLE_TRANSFER_INVALID）の errors の1件。入れた値は載せない。
    attributes:
      - {name: reason, type: enum, required: true, allowed: [TOO_DEEP, TOO_MANY_ALIASES, TAG_NOT_ALLOWED, DUPLICATE_KEY, SYNTAX, UNSUPPORTED_VERSION, MISSING_KEY, UNKNOWN_KEY, INVALID_VALUE, INVALID_ROLE_NAME, DUPLICATE_ROLE, INVALID_TARGET_NAME, COLUMN_AUX_NOT_ALLOWED]}
      - {name: line, type: "integer or null"}
      - {name: column, type: "integer or null"}
      - {name: path, type: "string or null", constraints: "YAML の中の道（例 /roles/営業/schemas/SALES/tables/T）"}
    constraints:
      - "1回の応答で最大 100 件（DSL の誤りの一覧と同じ上限）"

  # ---------- 確かめの一覧と指紋（契約 C7） ----------
  - name: TransferPlan
    description: 確かめ・適用で作る変わる点の一覧。サーバーは保存しない（CQ1: A）。
    attributes:
      - {name: fingerprint, type: string, required: true, constraints: "SHA-256 の 16 進数の小文字 64 文字（BR9.9）"}
      - {name: roles, type: "list<TransferRolePlan>", required: true, constraints: "ロールの鍵のコードポイントの順"}
      - {name: untouchedRoles, type: "list<string>", required: true, constraints: "ファイルに無い今のロールの名前（ロールの ID の順）"}
  - name: TransferRolePlan
    attributes:
      - {name: name, type: string, required: true}
      - {name: roleId, type: "long or null", constraints: "置き換えるロールの ID（作るときは null）"}
      - {name: action, type: enum, allowed: [CREATE, REPLACE]}
      - {name: added, type: integer}
      - {name: changed, type: integer}
      - {name: cleared, type: integer}
      - {name: notInDsl, type: integer, constraints: "変わる対象のうち今の DSL に無いものの数"}
      - {name: changes, type: "list<TransferChange>"}
  - name: TransferChange
    attributes:
      - {name: target, type: PermissionTarget}
      - {name: before, type: "{main, create, delete}（それぞれ null は設定なし）"}
      - {name: after, type: "{main, create, delete}"}
      - {name: reason, type: enum, allowed: [ADDED, CHANGED, CLEARED], constraints: "CLEARED はファイルに無い対象の設定が消えるもの"}
      - {name: inCurrentDsl, type: boolean}

  # ---------- 監査の出来事（契約 C10） ----------
  - name: RoleAuditEvent
    description: role が出す監査の出来事（Spring の出来事）。audit が確定の後に記録する。audit は role.domain のこの型に依存する。
    attributes:
      - {name: type, type: enum, allowed: [ROLE_CREATED, ROLE_RENAMED, ROLE_DELETED, ROLE_PERMISSION_CHANGED, ROLE_ASSIGNED, ROLE_UNASSIGNED, ROLE_TRANSFER_APPLIED, WORK_ROLE_SWITCHED]}
      - {name: actorUserId, type: long, required: true}
      - {name: targetRoleId, type: "long or null"}
      - {name: targetUserId, type: "long or null"}
      - {name: targetGroupId, type: "long or null"}
      - {name: result, type: enum, allowed: [SUCCESS, FAILURE]}
      - {name: failureReason, type: "enum or null", allowed: [ROLE_NOT_FOUND, ROLE_NAME_DUPLICATE, ROLE_IN_USE, ROLE_NOT_ASSIGNED, PERMISSION_TARGET_NOT_IN_DSL, DSL_NOT_APPLIED, ROLE_TRANSFER_STALE, USER_NOT_FOUND, GROUP_NOT_FOUND, NO_CHANGE]}
      - {name: detail, type: "RoleAuditDetail or null"}
      - {name: origin, type: "RequestOrigin（既存。送り手の IP・User-Agent・道・トレース ID）"}

  - name: RoleAuditDetail
    description: detail の型（キーを決めた record だけ。自由な Map は受けない）。JSON の文字列にして 16,384 文字以内（BR11.5〜BR11.7）。
    variants:
      - {name: Name, attributes: [name], applies: "ROLE_CREATED・ROLE_DELETED・それらの失敗"}
      - {name: Rename, attributes: [before, after]}
      - {name: InUse, attributes: [name, assignedUsers, assignedGroups]}
      - {name: PermissionChanges, attributes: [roleName, changes: "list<target, before, after>"]}
      - {name: PermissionChangesSummary, attributes: [roleName, changedCount, firstChanges: "list（先頭の何件か）"]}
      - {name: Assignment, attributes: [roleName, groupName: "or null"]}
      - {name: Transfer, attributes: [fileSha256, fingerprint, roles: "list<name, roleId, action, added, changed, cleared, notInDsl>"]}
      - {name: TransferSummary, attributes: [fileSha256, fingerprint, roleCount, firstRoles: "list（先頭の何件か）"]}
      - {name: WorkRoleSwitch, attributes: [fromRoleId: "or null", fromRoleName: "or null", toRoleName, storedBeforeRoleId: "or null（書く前の保存のロール ID。有効な作業ロールと同じロールを明示で選び、読み替え中の保存を書き直したときに from と to が同じになる。BR7.6）"]}
    constraints:
      - "パスワード・トークン・ハッシュ値（ファイルの SHA-256 と指紋は秘密でない内容の識別）・メールアドレス・氏名を入れない"
```

## 要約

- **内部DB の表**: ロール（`Role`）、権限の設定（`PermissionSetting`。1つの表で、下位の名前の無い階層は空の文字列）、割り当て（利用者用の `UserRoleAssignment` とグループ用の `GroupRoleAssignment` の2つの表。組が主キー）、作業ロールの保存（`WorkRoleSelection`。利用者ごとに1行、ロールへの外部キーなし）。
- **解決の口の型**: `WorkRoleRef`・`PermissionSnapshot`・`EffectivePermission`（契約 C5）。写しは1回の要求の中だけ。
- **YAML**: `version: 1` と、ロールの名前をキーにした入れ子の対応表（Q4: A）。上限は深さ 10・別名 0・節 1,000,000・大きさは仮に 10 MiB（Q5: A）。
- **確かめの一覧**: `TransferPlan`。指紋は保存しない。
- **監査**: `RoleAuditEvent` と、決めた型だけの `RoleAuditDetail`。

## components.md との差（承認済みの文書は書き換えない）

- `RoleAssignment`（識別子 `assignmentId`、相手は利用者かグループの一方）を、`UserRoleAssignment`・`GroupRoleAssignment` の2つの表に分け、`assignmentId` を持たない（Q2: A）。API は割り当ての ID を使わず、ロールと相手の ID の組で指すため、AC2.2.10 の「別の利用者に付いた割り当ての ID」は、ロールと相手の組の差し替えとして確かめる。
- `PermissionSetting` の識別子は components.md のとおり (roleId, schemaName, tableName, columnName) で、下位の名前が無いときは空の文字列（Entity の注の決着）。
- `WorkRoleSelection` の `roleId` は Role への参照だが、外部キーは置かない（Q3: A）。
- `Role` に重なりの判定の鍵 `nameKey` を足した。Java の1か所の関数で作り通常の列に保存する作りで、承認の場の直しで group の Group.nameKey も同じ作り（生成列にしない）にそろえる（role のレビュー R-10、group のレビュー R-04）。
