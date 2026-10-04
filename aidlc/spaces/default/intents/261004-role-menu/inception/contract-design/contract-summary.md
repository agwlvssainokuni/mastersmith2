# 契約の一覧（Contract Design）— role-menu

出典: 単位 `aidlc/spaces/default/intents/261004-role-menu/inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`（7単位）、部品の一覧 `components.md`、ADR-001〜008（`decisions.md`）、要件 `requirements.md`、ストーリー `stories.md`、画面 `mockups.md`、この段の答え `contract-design-questions.md`（CQ1〜CQ3）。

書く前に既存のコードで確かめた事実: 適用中の DSL の提供口は `dsl.service.ActiveDslModelProvider`（インターフェース）で、`current()` は sealed interface の `dsl.domain.ActiveDsl`（`Present(model, dslHash)` か `Absent`）を返す。モデルは `dsl.domain.DslModel(dslHash, formatVersion, menus, tables)`（tables はテーブルの物理名をキーにした平らな Map、`formatVersion` が `DslFormat.CURRENT_VERSION`（1）でなければ例外、`DslMenuItem.table` は文字列）。安全な YAML の読み込みは `dsl.parse.SafeYamlParser.parse(byte[])` で、上限は `DslFormat` の定数、結果は `YamlParseResult`（`Parsed` か `Rejected(DslError)`）。監査の記録は `audit.domain.AuditEvent`（`actorUserId`・`targetUserId`・`targetInvitationId`・`dslDigest` など。ロール・グループ・前後の値の列は無い）。管理の API の判定は `access.domain.AdminPaths`。DSL の提供口とモデルの既存の利用者は `dslmanage.service` の `DslLifecycle`・`DslPreviewAnalysis`・`DslPreviewCache`・`DslReconciler`・`DslDiffCalculator`・`DslSummaryCalculator` と、DSL の画面の API。監査の種類は `audit.domain.AuditEventType`（列挙、名前は 32 文字以内の列）。安全の決まりの差し込み口は `common.security.SecurityRuleContributor`、`/api/**` の既定は `common.security.ApiDefaultAccess`。

## 契約の表

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | U1 cross-cutting | U2 dsl-v2・U3 group・U4 role・U5 navigation（と既存のすべての API） | 共有の注釈（Java、`common/security`） | U1 |
| C2 | U1 cross-cutting | U6 role-admin-ui・U7 app-frame-ui | 共有の型と部品（TypeScript、`app/registry/types.ts`・`shared/tree`） | U1 |
| C3 | U2 dsl-v2 | U4 role・U5 navigation | アプリの中の口（Java、同期） | U2 |
| C4 | U3 group | U4 role | アプリの中の口とインターフェースの実装（Java、同期） | U3 |
| C5 | U4 role | U5 navigation・External: 後の Intent（業務データの画面 J・K） | アプリの中の口（Java、同期） | U4 |
| C6 | U3 group | U6 role-admin-ui | HTTP（REST、JSON） | U3 |
| C7 | U4 role | U6 role-admin-ui | HTTP（REST、JSON）・YAML のファイル | U4 |
| C8 | U4 role | U7 app-frame-ui | HTTP（REST、JSON） | U4 |
| C9 | U5 navigation | U7 app-frame-ui | HTTP（REST、JSON） | U5 |
| C10 | U3 group・U4 role | 既存の audit（監査の記録） | Spring の出来事（同期の発行、確定の後に記録） | U3・U4（種類の追加は各単位、記録の仕組みは既存の audit） |

## 共通の決まり（すべての HTTP の契約）

- 誤りは Problem Details（`type`・`title`・`status`・`detail`・`instance`）に安定した `code` を足した形。1つの code に1つの状態コードを固定する。入力の誤りは既存の `VALIDATION_FAILED`（400）、未認証は既存の `AUTHENTICATION_REQUIRED`（401）、管理者でない・停止中は既存の `ACCESS_DENIED`（403）。応答に内部の例外の文や YAML の部品の例外の文を載せない（PM の Forbidden）。
- 一覧は `{ items, page, size, total }`。日時は ISO 8601 の時点。項目名は camelCase。作成は 201（本文は作ったもの）、変更・削除・割り当ては本文なしの 204、読み取りは 200。
- 管理の API（`/api/admin/**`）は管理者の印で守り（既存の決まり）、自分の API（`/api/me/**`）はログインだけ。どの API にも C1 の分類の印を付ける。
- メールアドレス・氏名・検索の文字は、TRACE のログで伏せ字になる型で受け渡す（`project.md` の学び）。応答に、パスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値を含めない（PM の Forbidden）。

## 業務の理由の拒否の code（状態コードを固定）

| code | 状態 | 使う所 |
|---|---|---|
| ROLE_NOT_FOUND | 404 | ロールが無い（名前の変更・削除・設定・割り当て・作業ロールの切り替え） |
| ROLE_NAME_DUPLICATE | 409 | ロールの名前の重なり（作成・名前の変更・import の中の作成は C7 の確かめで誤りとして返す） |
| ROLE_IN_USE | 409 | 割り当てが残るロールの削除（応答の `assignedUsers`・`assignedGroups` に残りの数） |
| ROLE_NO_CHANGE | 409 | 変えるものが無い管理の操作（同じ名前への変更・同じ値での保存・重ねての割り当て・割り当てていないものの外し） |
| ROLE_NOT_ASSIGNED | 409 | 自分に割り当てられていないロール、または存在しないロールへの作業ロールの切り替え（ロールの ID の有無を数え上げられないよう、どちらも同じ応答にする） |
| PAYLOAD_TOO_LARGE | 413 | 既存の共通の code を使い回す。権限の YAML の大きさの上限の超過（multipart の上限の設定は U4 が持つ） |
| PERMISSION_TARGET_NOT_IN_DSL | 409 | 今の DSL に無い対象の設定の保存（画面を開いた後に DSL が変わった、AC1.2.13）。消す操作は受け付ける |
| DSL_NOT_APPLIED | 409 | 適用済みの DSL が無いときの権限の設定・import（FR2.3・FR7.3b） |
| ROLE_TRANSFER_INVALID | 400 | 権限の YAML の検証の誤り（上限・タグ・重複キー・中身の誤り。`errors` に位置と理由の一覧） |
| ROLE_TRANSFER_STALE | 409 | 適用のときに作り直した一覧の指紋が、確かめの指紋と一致しない（FR7.3a） |
| GROUP_NOT_FOUND | 404 | グループが無い |
| GROUP_NAME_DUPLICATE | 409 | グループの名前の重なり |
| GROUP_IN_USE | 409 | メンバーかロールの割り当てが残るグループの削除（応答に `members`・`assignedRoles` の数） |
| GROUP_NO_CHANGE | 409 | 変えるものが無いグループの操作（同じ名前・重ねてのメンバーの追加・メンバーでない利用者の外し） |
| USER_NOT_FOUND | 404 | 利用者が無い（既存の code があれば使い回す。機能設計で確かめる） |

code の最終の名前（既存の code との重なりの確かめ）は機能設計で確かめ、ここに書いた意味と状態コードは変えない。

---

## C1 API の分類の注釈（U1 → すべての API）

```yaml
shared-schema: api-access-annotation
owner: U1 cross-cutting
location: backend/src/main/java/cherry/mastersmith/common/security/ApiAccess.java   # 名前は機能設計で確定
kind: Java annotation (RUNTIME, TYPE と METHOD)
values:
  - PUBLIC         # ログイン不要（例: ログイン・登録の完了の確かめ）
  - AUTHENTICATED  # ログインだけ（/api/** の既定、例: /api/me/**）
  - ADMIN          # 管理者の印（/api/admin/** の下）
rules:
  - すべてのコントローラーの API（RequestMapping を持つ口）は、型か方法に印を1つだけ持つ
  - ADMIN の API の道は /api/admin/ で始まり、/api/admin/ の下の API は ADMIN を持つ（印と安全の決まりの食い違いを落とす）
  - 印の無い API・食い違いは、全体の構造の検査（ArchitectureTest と同じ置き場）で落ちる
  - 食い違いの検査は access.domain.AdminPaths の判定を正とし、両方向を確かめる（AdminPaths が管理者だけとする道の API は ADMIN を持ち、ADMIN を持つ API の道は AdminPaths が管理者だけとする道）
  - 印の対象外の入口（actuator・静的配信・フィルターが受ける入口）の扱い、既存の PUBLIC の API の一覧、AUTHENTICATED と ApiDefaultAccess の整合は、U1 の機能設計の必須の入力とする
```

## C2 画面の登録の型と共有の木（U1 → U6・U7）

```yaml
shared-schema: frontend-shared
owner: U1 cross-cutting
registry-types:
  file: frontend/src/app/registry/types.ts
  change: 管理のメニューへの登録（ロール・グループ・権限の受け渡し）を、管理者の印（既存の AccessLevel の ADMIN）で出す。業務のメニューは登録ではなく C9 の API から作るため、登録の型に業務のメニューは足さない。足す項目の名前（見出しの区分など）は機能設計で確定する
shared-tree:
  module: frontend/src/shared/tree
  props:
    nodes: 木の節の配列（id・label・hasChildren・badges）
    loadChildren: 節を開いたときに子を読む関数（Promise）
    selectedId: 選んでいる節
    onSelect: 節を選んだとき
    expandedIds / onToggle: 開閉の状態を外から渡す・受け取る
  a11y: 開閉のボタン（aria-expanded）、選択（aria-current）、tree の役割は使わない
```

## C3 DSL の版 2 の提供口と安全な YAML の読み込み（U2 → U4・U5）

```yaml
shared-schema: dsl-v2-service-ports
owner: U2 dsl-v2
ActiveDslModelProvider:   # 既存のインターフェース（形は変えない）
  current(): ActiveDsl              # 既存の sealed interface
ActiveDsl:
  Present(model: DslModel, dslHash: string)
  Absent()                          # 適用中の DSL が無い・版 1 で読めない
DslModel (format version 2):        # 既存の record を版 2 に広げる（CURRENT_VERSION を 2 にする）
  dslHash: string
  formatVersion: 2
  schemas: [DslSchema]              # 版 2 で足す
  menus: [DslMenuItem]
DslSchema:
  name: string                      # スキーマ名（中身は対象DB の設定のスキーマ1つ）
  displayName: string
  tables: map<tableName, DslTable>  # テーブル名はスキーマの中で一意
DslTable:
  name / displayName / columns: [DslColumn(name, displayName, ...)]   # 既存の項目はそのまま
DslMenuItem:
  label: string
  icon: string|null
  table: {schema: string, name: string} | null    # 版 2 ではスキーマ名とテーブル名の組で指す
  items: [DslMenuItem]
SafeYamlReader:           # dsl.service に足す口（ADR-007）。role は dsl.parse を直接使わない
  read(bytes, limits) -> SafeYamlResult
  limits: {maxBytes, maxDepth, maxAliases}        # 値は呼ぶ側（role）が渡す。既存の DslFormat の値は DSL の既定のまま
  SafeYamlResult: Parsed(tree, positions) | Rejected(kind, position)
  kind: TOO_LARGE | TOO_DEEP | TOO_MANY_ALIASES | TAG_NOT_ALLOWED | DUPLICATE_KEY | SYNTAX   # DSL の誤りの型（DslError）に依らない区分
  rules:
    - 既存の dsl.parse.SafeYamlParser・LimitingParser・YamlParseResult を包み、上限を引数にする（守りの実装は1か所のまま。DSL の読み込みは今の定数で同じ部品を使う）
    - タグと任意の型の生成を拒否し、重複キーを誤りにし、外部の参照を取りに行かない
    - 例外の文を外に出さない（区分と位置だけ）
    - DSL の JSON Schema の検証はこの口に含めない（DSL の都合を混ぜない）
failure:
  - 適用中の DSL が無い・版 1 で読めない: current() は Absent（呼ぶ側は DSL が無い扱い）
u2-work:                  # 版 2 で U2 が直す既存の利用者（R-01）
  - dsl: DslModel・DslMenuItem・JSON Schema・意味の検証・DslFormat.CURRENT_VERSION を版 2 に（版 1 は書式の版の誤り）
  - dsl の適用中のモデルの保持と起動時の読み直し（版 1 の適用中の DSL は Absent になる、AC6.1.6）
  - dslmanage: 既定の DSL の生成（DslTreeBuilder）・プレビュー（DslPreviewAnalysis・DslPreviewCache）・照合（DslReconciler）・違い（DslDiffCalculator）・要約（DslSummaryCalculator）・適用（DslLifecycle）をスキーマの階層に合わせる。版 1 の履歴の復元は 4xx
  - DSL の画面の API と画面（features/dsl）のスキーマの階層の表示
```

## C4 グループのメンバーと削除の問い合わせ（U3 → U4）

```yaml
shared-schema: group-service-ports
owner: U3 group
GroupMembershipQuery:          # group.service が出す読み取りの口
  groupIdsOfUser(userId) -> Set<groupId>
  exists(groupId) -> boolean
  summaries(groupIds) -> [GroupSummary(groupId, name)]
GroupDeletionGuard:            # group.service が定義し、role が実装する（ADR-002）
  canDelete(groupId) -> DeletionDecision
  DeletionDecision: Allowed | Blocked(assignedRoles: int)
  rules:
    - group は実装の中身を知らない。実装が無いと起動に失敗する（既定の「削除してよい」は置かない）
    - 削除の確かめと割り当ての追加の同時の重なりの守り方は機能設計・NFR 設計で決める（要件の確かめの R-08）
```

## C5 実効の権限の解決の口（U4 → U5・後の Intent）

```yaml
shared-schema: effective-permission-ports
owner: U4 role
EffectivePermissionResolver:   # role.service が出す口（CQ2: C）
  effectiveWorkRole(userId) -> WorkRoleRef | null    # 「有効な作業ロール」を決める唯一の持ち主（R-03）
  snapshotFor(userId) -> PermissionSnapshot          # 1回の要求で作業ロールの設定をまとめて読む（中で effectiveWorkRole を使う）
  resolve(userId, target) -> EffectivePermission     # 対象ごと（中で snapshotFor を使う）
effective-work-role-rules:
  - 保存した作業ロールが利用者のロール（直接とグループ経由の和）に含まれれば、それが有効な作業ロール
  - 含まれなければ、決めた順の最初のロールを有効な作業ロールとして読み替える（保存は書き換えない。読み取りの要求で状態を変えない）
  - ロールが無ければ null（すべて NONE・不可）
  - 「最初のロール」の順序は機能設計の必須の入力（AC4.1.16）
  - C8 の GET・C9・後の Intent は、すべてこの口の結果を使い、画面ごとに結果が食い違わない
PermissionSnapshot:
  workRoleId: long | null       # 有効な作業ロール（無ければ null で、すべて NONE・不可）
  main(schemaName, tableName, columnName|null) -> NONE|READ|FULL
  create(schemaName, tableName) -> boolean
  delete(schemaName, tableName) -> boolean
EffectivePermission: {main: NONE|READ|FULL, create: boolean, delete: boolean}
rules:
  - 要求ごとに内部DB から求め、トークンや画面の値を使わない（ADR-003、PM の Mandated）
  - 継承: 直近の上位の明示の値。全階層が設定なしなら NONE・不可
  - 今の DSL に無い対象は NONE・不可（C3 の current() が Absent ならすべて、Present なら名前で照らし合わせる）
  - 写しは1回の要求の中だけで使い、要求をまたいで持ち越さない（変更が次の要求から効く）
  - 失敗（DB の誤り）は例外のまま投げる（想定外の失敗）
```

## C6 グループの管理の API（U3 → U6）

```yaml
openapi: 3.0.3
info: {title: group admin API, version: unversioned}
paths:
  /api/admin/groups:
    get:  {summary: グループの一覧（名前・メンバーの数・割り当てたロールの数）, parameters: [page, size], responses: {'200': GroupPage}}
    post: {summary: 作成, requestBody: {name}, responses: {'201': Group, '400': VALIDATION_FAILED, '409': GROUP_NAME_DUPLICATE}}
  /api/admin/groups/{groupId}:
    get:    {summary: 詳細（名前・メンバーの一覧）, responses: {'200': GroupDetail, '404': GROUP_NOT_FOUND}}
    put:    {summary: 名前の変更, requestBody: {name}, responses: {'204': ok, '404': GROUP_NOT_FOUND, '409': 'GROUP_NAME_DUPLICATE・GROUP_NO_CHANGE'}}
    delete: {summary: 削除, responses: {'204': ok, '404': GROUP_NOT_FOUND, '409': GROUP_IN_USE}}
  /api/admin/groups/{groupId}/members:
    post:   {summary: メンバーを足す, requestBody: {userId}, responses: {'204': ok, '404': 'GROUP_NOT_FOUND・USER_NOT_FOUND', '409': GROUP_NO_CHANGE}}
  /api/admin/groups/{groupId}/members/{userId}:
    delete: {summary: メンバーを外す, responses: {'204': ok, '404': GROUP_NOT_FOUND, '409': GROUP_NO_CHANGE}}
components:
  schemas:
    Group: {groupId: integer, name: string, createdAt: string, updatedAt: string}
    GroupPage: {items: [GroupRow(groupId, name, memberCount, assignedRoleCount)], page: integer, size: integer, total: integer}
    GroupDetail: {groupId, name, members: [Member(userId, displayName, email, suspended)]}
notes:
  - 割り当てたロールの読み取りは C7 の /api/admin/groups/{groupId}/roles（role が答える）
  - メンバーの候補の検索は既存の利用者の管理の一覧の API（/api/admin/users）を使う（招待中の人は利用者の行が無く出ない）
```

## C7 役割・権限の管理の API（U4 → U6）

```yaml
openapi: 3.0.3
info: {title: role admin API, version: unversioned}
paths:
  /api/admin/roles:
    get:  {summary: ロールの一覧（名前・利用者の数・グループの数）, responses: {'200': RolePage}}
    post: {summary: 作成, requestBody: {name}, responses: {'201': Role, '400': VALIDATION_FAILED, '409': ROLE_NAME_DUPLICATE}}
  /api/admin/roles/{roleId}:
    put:    {summary: 名前の変更, requestBody: {name}, responses: {'204': ok, '404': ROLE_NOT_FOUND, '409': 'ROLE_NAME_DUPLICATE・ROLE_NO_CHANGE'}}
    delete: {summary: 削除, responses: {'204': ok, '404': ROLE_NOT_FOUND, '409': ROLE_IN_USE}}
  /api/admin/roles/{roleId}/permissions/schemas:
    get: {summary: 木の1段目（スキーマと設定・実効の値）, responses: {'200': PermissionNodes, '404': ROLE_NOT_FOUND, '409': DSL_NOT_APPLIED}}
  /api/admin/roles/{roleId}/permissions/schemas/{schemaName}/tables:
    get: {summary: スキーマの下のテーブル（設定・実効・メニューに出るか・今の DSL に無い）, responses: {'200': PermissionNodes}}
  /api/admin/roles/{roleId}/permissions/schemas/{schemaName}/tables/{tableName}/columns:
    get: {summary: テーブルの下のカラム, responses: {'200': PermissionNodes}}
  /api/admin/roles/{roleId}/permissions:
    put:
      summary: 1つの表（スキーマかテーブル）の分をまとめて保存（画面の段の RQ5: A）
      requestBody: {scope: {schemaName, tableName|null}, entries: [{schemaName, tableName|null, columnName|null, main|null, create|null, delete|null}]}
      responses: {'204': ok, '400': VALIDATION_FAILED, '404': ROLE_NOT_FOUND, '409': 'ROLE_NO_CHANGE・DSL_NOT_APPLIED・PERMISSION_TARGET_NOT_IN_DSL'}
      notes: null は「設定なし」。カラムに create・delete を送ると 400。同時の保存は後勝ち（監査に前後の値）
  /api/admin/roles/{roleId}/permissions/clear:
    post:
      summary: 今の DSL に無い対象の設定を消す（Should。DELETE に本文を載せないため下位パスへの POST）
      requestBody: {targets: [{schemaName, tableName|null, columnName|null}]}
      responses: {'204': ok, '400': VALIDATION_FAILED, '404': ROLE_NOT_FOUND}
  /api/admin/roles/{roleId}/assignments:
    get:  {summary: 割り当て（利用者は出どころつき、グループ）, responses: {'200': RoleAssignments}}
    post: {summary: 割り当て, requestBody: {userId|null, groupId|null}, responses: {'204': ok, '400': VALIDATION_FAILED, '404': 'ROLE_NOT_FOUND・USER_NOT_FOUND・GROUP_NOT_FOUND', '409': ROLE_NO_CHANGE}}
  /api/admin/roles/{roleId}/assignments/users/{userId}:
    delete: {summary: 利用者への直接の割り当てを外す, responses: {'204': ok, '404': ROLE_NOT_FOUND, '409': ROLE_NO_CHANGE}}
  /api/admin/roles/{roleId}/assignments/groups/{groupId}:
    delete: {summary: グループへの割り当てを外す, responses: {'204': ok, '404': ROLE_NOT_FOUND, '409': ROLE_NO_CHANGE}}
  /api/admin/groups/{groupId}/roles:
    get: {summary: グループに割り当てたロール（画面 S6 の読み取り）, responses: {'200': [RoleRef], '404': GROUP_NOT_FOUND}}
  /api/admin/users/{userId}/roles:
    get: {summary: 利用者のロールと出どころ（画面 S9 の読み取り）, responses: {'200': [UserRole(roleId, name, sources: [DIRECT | GROUP(groupId, name)])], '404': USER_NOT_FOUND}}
  /api/admin/role-transfer/export:
    get: {summary: ロールと権限の設定の YAML を書き出す, responses: {'200': 'application/yaml（添付のファイル名つき）'}}
  /api/admin/role-transfer/check:
    post:
      summary: YAML を検証して変わる点の一覧と指紋を返す（CQ1: A。サーバーは保存しない）
      requestBody: 'multipart/form-data の file（大きさの上限は NFR 要件で決める）'
      responses: {'200': TransferCheck, '400': ROLE_TRANSFER_INVALID, '409': DSL_NOT_APPLIED, '413': PAYLOAD_TOO_LARGE}
  /api/admin/role-transfer/apply:
    post:
      summary: 同じ YAML と指紋を送り、検証し直して一致すれば適用する
      requestBody: 'multipart/form-data の file と fingerprint'
      responses: {'200': TransferResult, '400': ROLE_TRANSFER_INVALID, '409': 'ROLE_TRANSFER_STALE・DSL_NOT_APPLIED・ROLE_NO_CHANGE', '413': PAYLOAD_TOO_LARGE}
components:
  schemas:
    Role: {roleId: integer, name: string, createdAt: string, updatedAt: string}
    RolePage: {items: [RoleRow(roleId, name, userCount, groupCount)], page, size, total}
    PermissionNodes:
      items: [{schemaName, tableName|null, columnName|null, displayName, explicit: {main|null, create|null, delete|null}, effective: {main, create, delete}, inheritedFrom: SCHEMA|TABLE|EXPLICIT|DEFAULT, inMenu: boolean, inCurrentDsl: boolean, hasChildren: boolean}]
    RoleAssignments: {users: [{userId, displayName, email, suspended, sources: [DIRECT | GROUP(groupId, name)]}], groups: [{groupId, name}]}
    TransferCheck: {fingerprint: string, roles: [{name, action: CREATE|REPLACE, added: integer, changed: integer, cleared: integer, notInDsl: integer, changes: [{target, before, after, reason}]}], untouchedRoles: [string]}
    TransferResult: {created: integer, replaced: integer}
notes:
  - 指紋は、作り直した変わる点の一覧（ロールの名前・対象・前後の値）を決まった順に並べたもののハッシュ。並べ方は機能設計で決める
  - 適用は1つのトランザクションでまとめて確定し、途中で失敗したら設定は変わらない（NFR3.2）
```

## C8 自分の作業ロールと権限の API（U4 → U7）

```yaml
openapi: 3.0.3
info: {title: me role API, version: unversioned}
paths:
  /api/me/work-role:
    get:
      summary: 自分のロールの一覧（直接とグループ経由の和）と今の作業ロール（CQ3: B）
      responses: {'200': {roles: [{roleId, name}], current: {roleId, name} | null}}
      notes: current は C5 の effectiveWorkRole の結果をそのまま返す（読み取りだけで、保存は書き換えない。読み替えは監査しない）
    put:
      summary: 作業ロールを切り替える
      requestBody: {roleId}
      responses: {'204': ok, '400': VALIDATION_FAILED, '409': ROLE_NOT_ASSIGNED}
      notes2: 存在しないロールも自分に無いロールと同じ ROLE_NOT_ASSIGNED（409）にする（R-06）
      notes: 今と同じロールは何もしない成功（204、監査に残さない）。利用者を指す値は受け取らず、主体は要求の文脈から読む
  /api/me/permissions/schemas:
    get: {summary: 自分の権限の木の1段目（Should）, responses: {'200': MyPermissionNodes}}
  /api/me/permissions/schemas/{schemaName}/tables:
    get: {summary: スキーマの下のテーブル, responses: {'200': MyPermissionNodes}}
  /api/me/permissions/schemas/{schemaName}/tables/{tableName}/columns:
    get: {summary: テーブルの下のカラム, responses: {'200': MyPermissionNodes}}
components:
  schemas:
    MyPermissionNodes: {workRole: {roleId, name} | null, items: [{schemaName, tableName|null, columnName|null, displayName, effective: {main, create, delete}, inMenu: boolean, hasChildren: boolean}]}
notes:
  - 他人を指す値は受け取らない（自分の分だけ）。適用済みの DSL が無いときは items が空
```

## C9 業務のメニューとテーブルの置き場の API（U5 → U7）

```yaml
openapi: 3.0.3
info: {title: navigation API, version: unversioned}
paths:
  /api/me/navigation:
    get:
      summary: 作業ロールの実効の主権限で絞った業務のメニューの木（管理のメニューは含めない）
      responses: {'200': {items: [NavNode]}}
      notes: 作業ロールが無い・DSL が無い・menus が空なら items は空（誤りにしない）
  /api/me/tables/{schemaName}/{tableName}/access:
    get:
      summary: テーブルの画面の置き場の権限の問い合わせ
      responses: {'200': {schemaName, tableName, displayName, main: READ|FULL}, '403': ACCESS_DENIED}
      notes: NONE と今の DSL に無いテーブルは同じ 403（表示名も返さない）
components:
  schemas:
    NavNode: {label: string, icon: string, table: {schemaName, tableName} | null, navigable: boolean, items: [NavNode]}
notes:
  - label はそのまま返し、画面がエスケープして出す。icon は許した名前でなければ既定（list）に置き換えて返す
  - navigable は、テーブルを指し実効が READ・FULL のとき true。テーブルが NONE で子が見えるまとまりは table を null にして返す
```

## C10 監査の出来事（U3・U4 → audit）

```yaml
asyncapi: 2.6.0
info: {title: role and group audit events (in-process Spring events), version: unversioned}
channels:
  role-audit:
    publish:
      message:
        payload:
          type: ROLE_CREATED | ROLE_RENAMED | ROLE_DELETED | ROLE_PERMISSION_CHANGED | ROLE_ASSIGNED | ROLE_UNASSIGNED | ROLE_TRANSFER_APPLIED | WORK_ROLE_SWITCHED   # 名前は機能設計で確定（32 文字以内、一度決めたら変えない）
          actorUserId: integer
          targetRoleId: integer | null
          targetUserId: integer | null
          targetGroupId: integer | null
          result: SUCCESS | FAILURE
          failureReason: string | null      # 業務の拒否を残すかは機能設計（FR12.2）
          detail: object                    # 変えた中身（前後の値・数）。import の残し方は機能設計（FR12.3）
  group-audit:
    publish:
      message:
        payload:
          type: GROUP_CREATED | GROUP_RENAMED | GROUP_DELETED | GROUP_MEMBER_ADDED | GROUP_MEMBER_REMOVED
          actorUserId / targetGroupId / targetUserId / result / failureReason / detail: 同上
rules:
  - 既存の監査の仕組みと同じく、確定の後に別のトランザクションで記録する
  - 出来事と監査の行に、パスワード・トークン・ハッシュ値を含めない
audit-changes-owner:     # 監査の表と AuditEvent の変更の持ち主（R-02）
  U3 group（先に作る単位）:
    - 監査の表に targetRoleId・targetGroupId・detail の列を足す Flyway の移行（前進のみ、前の版のアプリが動く形。列は空を許す）
    - AuditEvent に同じ項目と、ロール・グループ向けのファクトリーを足す
    - detail の形（JSON の文字列）と大きさの上限、伏せる規則（パスワード・トークン・ハッシュ値・メールアドレスを入れない。前後の値は権限の値と名前だけ）を決め、テスト（既存の *SecretLeakIT と同じ形）を置く
    - AuditEventType にグループの種類を足す（名前は 32 文字以内）
  U4 role:
    - AuditEventType に役割・権限・割り当て・import・作業ロールの種類を足し、U3 が足したファクトリーと列を使う
    - import の detail（多くの対象が変わるとき）の残し方（要約か全件か）は U4 の機能設計で決める
```

## 契約の持ち主の決まり

- 各契約の持ち主は表の Owner の単位で、契約の変更は持ち主が行い、使う側の単位の設計（機能設計）で受け入れを確かめる。
- 項目を足すだけの変更は互換とし、使う側は知らない項目を無視する（画面は TypeScript の型を足すだけで済む）。項目の名前の変更・削除・意味の変更は壊す変更で、使う側の単位を同じ Bolt で直す。
- code は一度決めたら名前と状態コードを変えない（`team.md` の Code Style）。
- C5 は後の Intent（J・K）も使うため、壊す変更は避け、足す形で広げる。

## 未決の論点

| Contract | Question | Blocks |
|---|---|---|
| C1 | 注釈の名前と、印の対象外の入口（actuator・静的配信・フィルターの入口）の扱い | U1 |
| C3 | 版 2 の JSON Schema の具体の形（schemas の書き方）、AC6.1.4 の2つ目のスキーマ・名前の違うスキーマを受け付けるか | U2・U4・U5 |
| C4・C7 | グループの削除と割り当ての追加の同時の重なりの守り方（行の排他か条件つきの削除か） | U3・U4 |
| C7 | 指紋の並べ方、YAML の形と大きさ・入れ子・別名の上限の値、import の応答時間の目標 | U4・U6 |
| C7・C10 | 業務の拒否と export を監査に残すか、import の「変えた中身」の残し方 | U4 |
| C5・C8 | 作業ロールの「最初のロール」の順序（AC4.1.16。機能設計の必須の入力） | U4 |
| C10 | detail の形と大きさの上限、伏せる規則（U3 が決める） | U3・U4 |
| C9 | icon の許した名前の一覧（make-you-chic-ui のアイコン） | U5・U7 |
| 共通 | USER_NOT_FOUND など既存の code との重なりの確かめ | U3・U4 |
