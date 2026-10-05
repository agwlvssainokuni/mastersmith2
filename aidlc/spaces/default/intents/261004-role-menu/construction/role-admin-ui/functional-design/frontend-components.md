# 画面の部品（Frontend Components）— U6 role-admin-ui

出典: `functional-spec.md`（この単位の画面の流れと状態の遷移の正。D1〜D32・W3.1〜W9.1）、`functional-design-questions.md`（Q1 A・Q2 B・Q3〜Q6 A、まとめの確認）、`aidlc/spaces/default/intents/261004-role-menu/inception/units-generation/unit-of-work.md`（U6）、`inception/domain-design/components.md`（RoleAdminUi・GroupAdminUi・RoleTransferUi・UserAdminUi）、`inception/contract-design/contract-summary.md`（C2・C6・C7）、`inception/refined-mockups/`（mockups・interaction-spec・accessibility-checklist・design-system-mapping）、`construction/cross-cutting/functional-design/frontend-components.md`（共有の木・登録の型）、既存の `frontend/src/features/useradmin/`・`features/dsl/`・`frontend/src/shared/`、`vendor/make-you-chic-ui`（固定先 e82b651）。

ファイルの名前・関数の名前は案で、コード生成で確かめて決める。コードは形を示す断片だけ。

## 1. make-you-chic-ui の部品と、使う口（ソースで確かめた）

| 部品 | 使う所 | 使う口 | 注意 |
|---|---|---|---|
| Table | S3・S6 の一覧 | `columns`・`data`・`totalCount`・`page`・`pageSize`・`onPageChange`・`getRowId`・`aria-label`・`labels` | ページ送りを押せなくする口が無いため、読み直しの間の押下は `onPageChange` の側で捨てる（既存の UserTable と同じ）。`renderDetail` は使わない（functional-spec 9節の (e)） |
| Dropdown | 一覧の行の操作、S9 の行の操作 | `trigger`・`items`（`label`・`onClick`・`disabled`・`description`）・`placement="bottom-end"` | trigger の ref は置き換えられるため、フォーカスを戻す先は包む要素から探す（既存の UserRowActions と同じ） |
| Modal | 名前の入力・削除と適用の確かめ・候補・未保存の確かめ・S9 のロール | `open`・`onClose`・`title`・`size`・`initialFocusRef`・`finalFocusRef`・`closeLabel`・`closeOnBackdropClick={false}`・`role="alertdialog"`（確かめ） | 閉じた後の処理は `shared/modal/afterModalClosed` を使う |
| Tabs | S4・S5 のタブ | `items`・`activeIndex`・`onChange`・`aria-label` | タブは道で決め、`onChange` で道を変える（未保存の確かめは `useBlocker` が受ける） |
| Select | S4 の値 | `options`・`value`・`onChange`・`aria-label` | 名前に対象を含める（「受注番号の主権限」） |
| Button・Alert・Badge・FormField・TextInput | 全体 | 既存のとおり | Alert の `dismissLabel` は画面の言語で渡す |

共有の木は U1 の `frontend/src/shared/tree` の `SharedTreeView`（`nodes`・`loadChildren`・`selectedId`・`onSelect`・`expandedIds`・`onToggle`・`labels`・`ariaLabel`）。U6 が make-you-chic-ui に新しく頼む部品は無い。

## 2. 機能 roleadmin（`frontend/src/features/roleadmin/`）

### 2.1 構成

| ファイル | 中身 |
|---|---|
| `registration.ts` | 道 `/admin/roles`・`/admin/roles/:roleId`・`/admin/roles/:roleId/assignments` と、サイドバーの「ロール」（240、`section: 'ADMIN'`）。画面は `lazy` |
| `messages.ts` | 文言（鍵は `roleadmin.`、ja・en） |
| `api/types.ts`・`api/roleAdminApi.ts` | 型と API の関数（3節） |
| `RoleListPage.tsx`・`useRoleList.ts` | S3（W3.1〜W3.6） |
| `RoleNameDialog.tsx` | 作成・名前の変更の Modal（D7）。groupadmin にも同じ形を持つ（機能をまたいで import しない） |
| `DeleteConfirmDialog.tsx` | 削除の確かめ（D8） |
| `RoleRowActions.tsx` | 行の「開く」と Dropdown（D6） |
| `RoleDetailPage.tsx`・`useRole.ts` | S4・S5 の枠（見出し・「← ロールの一覧」・Tabs・D32） |
| `PermissionPanel.tsx`・`useRolePermissions.ts` | S4 の木と表（W4.1〜W4.7、4.3 の状態） |
| `PermissionTable.tsx` | 右の表（スキーマ・テーブルの設定とカラム） |
| `permissionDraft.ts` | 写しと `entries` を作る純粋な関数（D12） |
| `permissionLabels.ts` | 実効の値の文字を作る純粋な関数（D11） |
| `LeaveConfirmDialog.tsx`・`useUnsavedGuard.ts` | 未保存の確かめ（D15、`useBlocker` と `beforeunload`） |
| `AssignmentPanel.tsx`・`useRoleAssignments.ts` | S5（W5.1〜W5.5） |
| `CandidateDialog.tsx` | 候補の Modal（D17・D18。利用者とグループの両方の形を props で受ける） |
| `removalImpact.ts` | D19 の判定の純粋な関数 |
| `failureMessage.ts` | code から文言の鍵を選ぶ（D3、5節の表） |
| `useRoleAdminText.ts` | 文言を引く（既存の useUserAdminText と同じ形） |
| `testing/fixtures.ts`・`testing/renderRoleAdmin.tsx` | テストの見本と描き方 |
| `*.css` | 部品と同じ場所の素の CSS |

### 2.2 主な部品の props と状態

**RoleListPage**（props: `api?: RoleAdminApi`）
- フック `useRoleList({ api, t, reportForbidden })` が、`view`（loading・populated・empty・reloading・load-error）・`list`・`page`・`dialog`（none・create・rename(row)・delete(row)）・`failure`（Alert の鍵と値）・`liveMessage`・操作（`goToPage`・`openCreate`・`submitName`・`confirmDelete`・`retry`）を持つ（functional-spec 4.1・4.2）。

**RoleNameDialog**
| prop | 型 | 説明 |
|---|---|---|
| `mode` | `'create'` か `'rename'` | 題とボタンの文言 |
| `initialName` | 文字列 | 名前の変更のときの今の名前 |
| `submitting` | 真偽 | 送信中 |
| `serverProblem` | 文言の鍵か undefined | サーバーの拒否（D7） |
| `onSubmit` | `(name) => void` | 画面の検査を通った名前 |
| `onCancel` | `() => void` | |
| `finalFocusRef` | ref | 閉じた後の行き先 |

- 画面の検査は `shared/validation/validateAdminName.ts`（新規、`trimDisplayName`・`countCodePoints` を使う）の `validateAdminName(value) → 'required' か 'tooLong' か 'invalidCharacter' か undefined`。残りの文字数は「残り 12 文字」。

**RoleDetailPage**
- 道の `roleId` を読み（`useParams`）、正の整数でなければ D32。`useRole(roleId)` が `GET /api/admin/roles/{roleId}` の結果（loading・ready・missing・load-error）を持つ。
- `Tabs` の `activeIndex` は道（末尾が `/assignments` なら 1）から決め、`onChange` は `navigate` で道を変える。

**PermissionPanel**（props: `roleId: number`、`api`）
- フック `useRolePermissions` の状態:

| 状態 | 型 | 説明 |
|---|---|---|
| `phase` | `'loading'`・`'noDsl'`・`'ready'`・`'missing'`・`'load-error'` | 4.3 の外側 |
| `schemas` | `PermissionNode[]` | 1段目 |
| `tablesBySchema` | 名前ごとの `PermissionNode[]` | 開いたスキーマの子（木の `loadChildren` で読み、表の値にも使う） |
| `columns` | `{ phase, items }` | 選んだテーブルのカラム |
| `selectedId`・`expandedIds` | 文字列・文字列の集まり | 木の選びと開閉（D10） |
| `draft` | `PermissionDraft` | 写し（D12） |
| `saving` | 真偽 | |
| `notice` | Alert の鍵と値 | 保存・拒否の結果（D13・D14） |

- 操作: `select(id)`（写しがあれば確かめを出す）・`toggle(id, expanded)`・`change(target, field, value)`・`discard()`・`save()`・`clearOrphan(target)`。

**PermissionTable**（props: `scope`（スキーマかテーブル）、`node`（設定の節）、`columns`（テーブルのときだけ）、`draft`、`readOnly`（S8 の流用はしない。U6 では常に false）、`onChange`、`busy`）
- 行ごとに、対象の名前（表示名と物理名）・Select（主権限、スキーマとテーブルは CREATE・DELETE も）・実効の値の文字・〔Should〕印（業務のメニュー・今の DSL に無い）。カラムの行は主権限だけ。

**permissionDraft.ts**（純粋な関数、fast-check の対象）

```ts
// 説明用の断片（形だけ）
export type Explicit = { main: MainValue | null; create: boolean | null; delete: boolean | null }
export type PermissionDraft = ReadonlyMap<string, { target: PermissionTarget; explicit: Explicit }>
export function applyChange(draft: PermissionDraft, server: Explicit, target: PermissionTarget,
  next: Explicit): PermissionDraft            // サーバーの値と同じなら写しから消す
export function toEntries(draft: PermissionDraft, scope: PermissionScope): PermissionEntry[]
```

- 鍵は対象の名前の JSON の配列（D10 と同じ考え方）。`toEntries` は `scope` の中の対象だけを返し、同じ対象は1つ。

**permissionLabels.ts**: `effectiveLabel(node, kind, names) → { valueKey, sourceKey, sourceName? }`（「READ（スキーマ 販売DB から継承）」の部品）。`inheritedFrom` の `SCHEMA`・`TABLE` は親の表示名を添え、`EXPLICIT` は「明示」、`DEFAULT` は「既定」。

**useUnsavedGuard**（props: `dirty: boolean`）
- `useBlocker` に「`dirty` で、移る先の道が今の道と違う」を渡し、`blocked` のとき `LeaveConfirmDialog` を開く。［留まる］は `reset()`、［移る］は写しを捨てて `proceed()`。`dirty` の間は `beforeunload` を張る（functional-spec 7.2）。7.3 で切り替えたときは `useBlocker` を使わず、画面の中の移動だけを確かめる。

**AssignmentPanel**（props: `roleId`、`roleName`、`api`）
- `useRoleAssignments` が `assignments`（users・groups）・`picker`（none・user・group）・`removing`（userId か groupId）・`confirmRemoval`（D19）を持つ。
- 利用者の表の行: 氏名・メールアドレス（折り返す）・出どころの文字（「直接・グループ 営業部」）・「外す」（直接があるときだけ）。

**CandidateDialog**
| prop | 型 | 説明 |
|---|---|---|
| `kind` | `'user'` か `'group'` | 利用者は検索、グループはページ送り |
| `title` | 文字列 | 「利用者を足す」など |
| `isTaken` | `(id) => boolean` | 済みか（割り当て済み・メンバー済み） |
| `takenLabel` | 文字列 | 「割り当て済み」か「メンバー済み」 |
| `search` | `(text) => Promise<Candidate[]>`（user） | `GET /api/admin/users?page=1&q=` |
| `loadPage` | `(page) => Promise<CandidatePage>`（group） | `GET /api/admin/groups?page=` |
| `onAdd` | `(id) => Promise<AddResult>` | 1件を送る。結果で候補の印と `role="status"` を変える |
| `onClose` | `() => void` | 閉じたら呼ぶ側が読み直す |

- 候補の状態: idle・searching・results・adding(id)・failed。検索の文字は既存の `features/useradmin/searchInput.ts` と同じ考え方で画面でも確かめる（機能をまたいで import しないため、groupadmin・roleadmin の中に持つか、`shared/validation` に移す。コード生成で決める）。

**removalImpact.ts**（純粋な関数、fast-check の対象）: `removalImpact(roles: UserRole[], roleId) → { losesRole: boolean, nextWorkRole: {roleId, name} か null }`。出どころに直接以外があれば `losesRole` は偽。残りのロールの ID の最小が `nextWorkRole`。

## 3. API の型と関数（roleadmin）

`api/types.ts`（応答は型の項目だけを写す。知らない項目は捨てる）:

```ts
// 説明用の断片（形だけ）
export type MainValue = 'NONE' | 'READ' | 'FULL'
export type InheritedFrom = 'EXPLICIT' | 'TABLE' | 'SCHEMA' | 'DEFAULT'
export interface PermissionNode { schemaName: string; tableName: string | null; columnName: string | null
  displayName: string; explicit: Explicit; effective: { main: MainValue; create: boolean; delete: boolean }
  inheritedFrom: InheritedFrom; inMenu: boolean; inCurrentDsl: boolean; hasChildren: boolean }
export type AssignmentSource = { kind: 'DIRECT' } | { kind: 'GROUP'; groupId: number; name: string }
```

- ほかの型: `Role`・`RoleRow`・`RolePage`・`PermissionScope`・`PermissionEntry`・`AssignedUser`（userId・displayName・email・suspended・sources）・`AssignedGroup`・`UserRole`（roleId・name・sources）・`Candidate`（userId か groupId・名前・email・suspended）。
- `roleAdminApi` の関数: `listRoles(page)`・`getRole(roleId)`・`createRole(name)`・`renameRole(roleId, name)`・`deleteRole(roleId)`・`listSchemas(roleId)`・`listTables(roleId, schema)`・`listColumns(roleId, schema, table)`・`savePermissions(roleId, scope, entries)`・`clearPermissions(roleId, targets)`・`getAssignments(roleId)`・`assignUser(roleId, userId)`・`assignGroup(roleId, groupId)`・`unassignUser(roleId, userId)`・`unassignGroup(roleId, groupId)`・`listGroups(page)`（候補）・`searchUsers(text)`（候補）・`getUserRoles(userId)`。
- 権限の木の関数の道（承認の場の直し、functional-spec.md 13節）: `listSchemas` は `/api/admin/roles/{roleId}/permissions/schemas`、`listTables` は `/api/admin/roles/{roleId}/permissions/tables?schema=…`、`listColumns` は `/api/admin/roles/{roleId}/permissions/columns?schema=…&table=…`。名前は道に入れず問い合わせの引数で渡す（`URLSearchParams` で組む）。この形は直しの例で、正確な道と引数の名前は role の `functional-spec.md` の確定の形に合わせる。`getRole` の `GET /api/admin/roles/{roleId}` は承認の場の決定で role の設計に入った。
- 道の中の値はすべて `encodeURIComponent`。`useAdminForbidden` に渡す道も同じ関数で作る。失敗は ApiClient の `ApiError` のまま投げ、`ROLE_IN_USE` の数は `problem` の `assignedUsers`・`assignedGroups` を整数のときだけ読む。
- 応答の形の誤り（必要な項目が無い・型が違う・`inheritedFrom` が知らない値）は通信の失敗として扱う。

## 4. 機能 groupadmin（`frontend/src/features/groupadmin/`）

| ファイル | 中身 |
|---|---|
| `registration.ts` | 道 `/admin/groups`・`/admin/groups/:groupId`、サイドバーの「グループ」（250） |
| `messages.ts`・`useGroupAdminText.ts`・`failureMessage.ts` | 文言（鍵は `groupadmin.`） |
| `api/types.ts`・`api/groupAdminApi.ts` | `Group`・`GroupRow`・`GroupPage`・`GroupDetail`・`Member`・`GroupRoleRef`・`UserRole`。関数は `listGroups`・`createGroup`・`getGroup`・`renameGroup`・`deleteGroup`・`addMember`・`removeMember`・`getGroupRoles`・`getUserRoles`・`searchUsers` |
| `GroupListPage.tsx`・`useGroupList.ts` | S6 の一覧（roleadmin の一覧と同じ形） |
| `GroupNameDialog.tsx`・`DeleteConfirmDialog.tsx`・`GroupRowActions.tsx` | 作成・名前の変更・削除（D7〜D9） |
| `GroupDetailPage.tsx`・`useGroupDetail.ts` | S6 の詳細（W6.2〜W6.4、D20・D21、D32 と同じ形の missing） |
| `MemberTable.tsx` | メンバーの表 |
| `CandidateDialog.tsx` | 候補（利用者の検索の形だけ） |
| `removedRoles.ts` | D20 の「無くなったロール」を求める純粋な関数（グループのロールと、外した後の利用者のロールの差） |
| `testing/` | 見本と描き方 |

- 割り当てたロールの各リンクは `/admin/roles/{roleId}/assignments`（道の文字列）。

## 5. 機能 roletransfer（`frontend/src/features/roletransfer/`）

| ファイル | 中身 |
|---|---|
| `registration.ts` | 道 `/admin/role-transfer`、サイドバーの「権限の受け渡し」（260） |
| `messages.ts`・`useRoleTransferText.ts`・`failureMessage.ts` | 文言（鍵は `roletransfer.`）。`reason` の 13 個の文言を ja・en で持つ |
| `api/types.ts`・`api/roleTransferApi.ts` | `TransferCheck`・`TransferRolePlan`・`TransferChange`・`TransferResult`・`TransferError`。関数は `exportRoles()`（`apiDownload`）・`checkTransfer(text)`・`applyTransfer(text, fingerprint)` |
| `saveFile.ts` | 受けたファイルを保存させる小さな関数（DSL と同じ形。`features/dsl` は import しない） |
| `RoleTransferPage.tsx`・`useRoleTransfer.ts` | S7（W7.1〜W7.4、functional-spec 4.5 の状態） |
| `TransferFilePicker.tsx` | ファイルの選択と大きさの確かめ（D23。`MAX_TRANSFER_BYTES = 10 * 1024 * 1024`、仮の値で NFR 要件の段で確定） |
| `TransferCheckTable.tsx` | 結果の表（D24。素の table、行ごとの開閉のボタン、開いた行の 100 件ずつのページ送り） |
| `TransferErrorList.tsx` | 誤りの一覧（D26。DSL の誤りの一覧と同じ見た目） |
| `ApplyConfirmDialog.tsx` | 適用の確かめ（D25） |
| `changePaging.ts` | 開いた行の 100 件ずつの範囲を求める純粋な関数 |

- `checkTransfer`・`applyTransfer` は `apiRequest` に `method: 'POST'`・`body: text`・`headers: {'Content-Type': 'application/yaml'}`（適用は `X-Role-Transfer-Fingerprint` も）を渡す。ファイルは `File.text()` で読む。
- 指紋・本文・結果は画面の状態だけに持ち、ブラウザの保存・コンソールに出さない。
- `TransferCheckTable` の行の開閉のボタンの名前は「{{name}}の変わる点を開く」「{{name}}の変わる点を閉じる」（`aria-expanded`・`aria-controls`）。表は外枠の中で横に動き、ロールの列を固定する（768px 未満）。

## 6. useradmin の追加（`frontend/src/features/useradmin/`）

| ファイル | 変更 |
|---|---|
| `rowActions.ts` | 項目の種類に `viewRoles` を足す（どの行でも押せる、確かめなし）。並びは最後 |
| `UserRowActions.tsx` | 変えない（項目は `rowActions` から作る） |
| `useUserAdmin.ts`・`UserAdminPage.tsx` | `viewRoles` を選んだら `UserRolesDialog` を開く。閉じたら行の「操作」へフォーカスを戻す（既存の `dialogReturnRef`） |
| `UserRolesDialog.tsx`（新規） | 読み取りの Modal（題「{{name}}さんのロール」）。開いたときに `getUserRoles` を1回読み、loading・ready・empty・failed を出す。行は「営業（直接・グループ 営業部）」。下にロールの一覧へのリンク（`/admin/roles`） |
| `api/userAdminApi.ts`・`api/types.ts` | `getUserRoles(userId)` と `UserRole` を足す（roleadmin の型は import しない） |
| `messages.ts` | `useradmin.menu.viewRoles`・`useradmin.roles.*` を足す |

- 既存の表の列・操作と、既存のテストの期待は変えない（メニューの項目の数のテストは1つ増える形に直す）。`useradmin` の本体に手が入るため、`frontend` のカバレッジの下限（行 80%・分岐 70%）を新しいファイルを含めて満たす。

## 7. 文言（主なもの）

| 鍵（例） | ja | en |
|---|---|---|
| `roleadmin.nav.label` | ロール | Roles |
| `roleadmin.title` | ロール | Roles |
| `roleadmin.action.create` | ロールを作る | Create role |
| `roleadmin.detail.heading` | ロール: {{name}} | Role: {{name}} |
| `roleadmin.tab.permissions`・`roleadmin.tab.assignments` | 権限の設定・割り当て | Permissions・Assignments |
| `roleadmin.delete.confirm` | ロール「{{name}}」を削除します。権限の設定も消え、元に戻せません。 | Delete the role "{{name}}". Its permission settings are also removed and cannot be restored. |
| `roleadmin.inUse` | ロール「{{name}}」は利用者 {{users}} 人とグループ {{groups}} つに割り当てられているため削除できません。先に割り当てを外してください。 | The role "{{name}}" cannot be deleted because it is assigned to {{users}} users and {{groups}} groups. Remove the assignments first. |
| `roleadmin.effective.explicit` | {{value}}（明示） | {{value}} (explicit) |
| `roleadmin.effective.inherited` | {{value}}（{{level}} {{name}} から継承） | {{value}} (inherited from {{level}} {{name}}) |
| `roleadmin.effective.default` | {{value}}（既定） | {{value}} (default) |
| `roleadmin.select.main` | {{target}}の主権限 | Main permission of {{target}} |
| `roleadmin.unsaved.count` | 保存していない変更: {{count}} 件 | Unsaved changes: {{count}} |
| `roleadmin.unsaved.leave` | 保存していない変更があります。移ると変更は保存されません。 | You have unsaved changes. If you leave, they will not be saved. |
| `roleadmin.noDsl` | 権限を設定する対象がありません。DSL の管理の画面で DSL を適用してください。 | There is nothing to set permissions for. Apply a DSL on the DSL management screen. |
| `roleadmin.orphan` | ⚠ 今の DSL に無い | ⚠ Not in the current DSL |
| `roleadmin.removal.confirm` | {{user}}さんの作業ロールがこのロールなら、外した後は {{next}} に変わります。 | If {{user}}'s working role is this role, it will change to {{next}} after removal. |
| `roleadmin.busy`（各機能に同じ形） | ほかの操作と重なりました。少し待ってからもう一度お試しください。 | Another operation was in progress. Wait a moment and try again. |
| `groupadmin.memberRemoved.roles` | {{user}}さんは{{group}}から外れました。グループ経由のロール: {{roles}} も外れます。 | {{user}} was removed from {{group}}. Roles granted through the group are also removed: {{roles}}. |
| `roletransfer.stale` | 確かめた後にロールの設定が変わったため、適用できませんでした。もう一度[確かめる]を押してください。 | The role settings changed after the check, so they were not applied. Press [Check] again. |
| `roletransfer.reason.UNKNOWN_KEY` | 知らない項目です | Unknown key |

- Table の `labels`・Modal の `closeLabel`・Alert の `dismissLabel`・共有の木の `labels`（開く・閉じる・読み込み中・失敗・再試行・子が無い）も各機能の文言に持ち、en の画面に部品の日本語の既定を出さない。
- 差し込む値（名前・氏名）は React の文字として描き、HTML として解釈しない。

## 8. アクセシビリティ（部品ごと）

| 部品 | 決まり |
|---|---|
| 一覧 | h1 と、一覧の見出し（h2、`tabIndex=-1`、フォーカスの行き先）、`role="status"` の読み込み、Table の `aria-label` |
| 行の操作 | trigger の名前に対象の名前（「営業の操作」）、busy のとき「（処理中）」 |
| 名前の Modal | ラベル付きの入力、誤りを `aria-describedby`、残りの文字数を読み上げに含める |
| 確かめの Modal | `role="alertdialog"`、はじめのフォーカスは「やめる」（未保存は「留まる」）、閉じたら開いた所へ戻す |
| 木 | 共有の木の決まり（`aria-expanded`・`aria-current`、tree の役割なし） |
| 権限の表 | `table` と見出しのセル、Select の名前に対象、実効の値は文字、印は Badge の文字 |
| 保存 | 押せない理由を文字で隣に出し `aria-describedby` で結ぶ。結果は Alert と `aria-live="polite"` |
| 候補 | 検索欄にラベル、結果の件数を `role="status"`、済みの印は文字、足した結果を `role="status"` |
| 結果の表（S7） | 行の開閉のボタンの名前にロールの名前、`aria-expanded`・`aria-controls` |
| 誤りの一覧（S7） | 件数の警告は `role="alert"`・`tabindex="-1"` で、出たときにフォーカス |
| S9 の Modal | 題に氏名、一覧は `ul`、読み込みは `role="status"` |

- 色だけで状態を表さない（明示・継承・既定・今の DSL に無い・メニューに出る・利用停止・済み）。`prefers-reduced-motion` のときは開閉の動きを止める。

## 9. テスト

- 置き場は対象と同じ場所の `*.test.ts`・`*.test.tsx`。Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe。部品ごとに axe を1件。説明文は英語。
- 描画の後に反映される値（読み込みの後の表・保存の後の読み直し・Alert）は `waitFor`・`findBy*` で待つ。時間の上限は延ばさない。
- `useBlocker` を使う部品（`PermissionPanel`・`RoleDetailPage`）のテストは `createMemoryRouter`＋`RouterProvider` で描き、道の移動で確かめが出る・留まると道が変わらない・移ると道が変わることを確かめる。
- fast-check（失敗時の種を記録）: `permissionDraft.ts`（`applyChange`・`toEntries`）、`removalImpact.ts`、`groupadmin/removedRoles.ts`、`roletransfer/changePaging.ts`。
- 個々の確かめの一覧は `functional-spec.md` の 8.1。ブラウザの検査と流れの E2E は 8.2・8.3。

## 10. 登録の形（説明用の断片）

```ts
// 説明用の断片（roleadmin/registration.ts の形）
export const registration: FeatureRegistration = {
  featureId: 'roleadmin',
  routes: [ROLE_LIST, ROLE_DETAIL, ROLE_ASSIGNMENTS].map((path) => ({
    path, screen: path === ROLE_LIST ? RoleListPage : RoleDetailPage, layout: 'SHELL', access: 'ADMIN',
  })),
  sidebarItems: [{ id: 'roleadmin', labelKey: 'roleadmin.nav.label', path: ROLE_LIST,
    order: 240, visibleWhen: 'ADMIN', section: 'ADMIN' }],
  messages: roleAdminMessages,
}
```
