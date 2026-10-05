# 画面の部品（Frontend Components）— U7 app-frame-ui

出典: `functional-spec.md`（D1〜D30、W1〜W9、この文書の正）、`unit-of-work.md`（U7）、`contract-summary.md`（C2・C8・C9）、`components.md`（AppFrame・MyPermissionsUi・TablePlaceholderUi）、`mockups.md`（S1・S2・S8・10a 節）、`interaction-spec.md`、`accessibility-checklist.md`、先に確定した単位（cross-cutting・navigation・role・role-admin-ui の機能設計）、この段の答え（Q1〜Q5 A・Q6 B）、make-you-chic-ui 5bf1ffe のソース。

ファイル名・部品名は案で、コード生成で既存の名付けに合わせてよい。説明用の断片は形だけを示す。

## 1. 構成

```
frontend/src/
  main.tsx                         （B8 で data router。U7 は変えない）
  app/
    App.tsx                        （並びに WorkRoleProvider・BusinessNavigationProvider を足す）
    work-role/
      workRoleApi.ts               GET・PUT /api/me/work-role と応答の写し
      WorkRoleProvider.tsx         作業ロールの状態・切り替え・読み直し（useWorkRole）
      WorkRoleSwitcher.tsx/.css    トップバーの表示と Dropdown
      WorkRoleSwitcher.test.tsx・WorkRoleProvider.test.tsx
    navigation/
      navigationApi.ts             GET /api/me/navigation と応答の写し
      BusinessNavigationProvider.tsx  業務のメニューの状態（useBusinessNavigation）
      buildNavSections.ts          区画を作る純粋な関数（D10〜D15・D20）
      tablePlaceholderPath.ts      置き場の道を作る・読む純粋な関数（D20）
      navExpansion.ts              開閉の状態の保存と祖先の足し方（D16）
      useReloadTriggers.ts         読み直しのきっかけ（D4）
      navigationItems.ts           （既存。ユーザーメニューは残し、サイドバーの平らな一覧は buildNavSections に移す）
      *.test.ts（buildNavSections.property.test.ts を含む）
    layout/ShellLayout.tsx         （navSections・navExpandedIds・onNavExpandedChange・navLabels・topbarEnd）
    pages/HomePage.tsx             （D19 の案内）
    i18n/messages/ja.ts・en.ts     （nav.*・workRole.*・home.* を足す）
  features/
    tables/                        registration.ts・TablePlaceholderPage.tsx/.css・tableAccessApi.ts・messages.ts・*.test.tsx
    mypermissions/                 （Should）registration.ts・MyPermissionsPage.tsx/.css・myPermissionsApi.ts・messages.ts・*.test.tsx
    auth/                          （U7 の持ち物の外）registration.ts のログアウトの項目を path に、LogoutPage.tsx を足す
frontend/e2e/                      U7 の画面の検査（本数に数えない）・I の流れ・F の流れへの追加・support の見本
  support/userMenu.ts              （新しい）ユーザーメニューの開き口を、作業ロールの入れ物の外の dropdown-trigger で探す共通の関数（R-01）
  support/registeredUser.ts・080-preferences-accessibility.e2e.ts  （書き換え）開き口の操作を共通の関数に替える
```

骨組みの並び（`App.tsx`）: `ThemeProvider → … → FeatureRegistryProvider → AdminForbiddenProvider → WorkRoleProvider → BusinessNavigationProvider → AppRouter`（D2）。`BusinessNavigationProvider` は `WorkRoleProvider` の切り替えの回数を受けて読み直すため、その内側に置く。

## 2. API の型（画面の側）

```ts
// 説明用の断片（型の形だけ）
export interface WorkRoleRef { roleId: number; name: string }
export interface WorkRoleState { roles: readonly WorkRoleRef[]; current: WorkRoleRef | null }
export interface LocalizedLabel { ja: string; en: string }
export interface NavNode {
  id: string; label: LocalizedLabel; icon: string
  table: { schemaName: string; tableName: string } | null
  navigable: boolean; items: readonly NavNode[]
}
export type NavEmptyReason = 'NOT_CONFIGURED' | 'NOTHING_VISIBLE'
export interface NavigationResponse { items: readonly NavNode[]; emptyReason: NavEmptyReason | null }
```

- 置き場: `TableAccess { schemaName; tableName; displayName: LocalizedLabel; main: 'READ' | 'FULL' }`。
- 自分の権限: `MyPermissionNodes { workRole: WorkRoleRef | null; items: MyPermissionNode[] }`、`MyPermissionNode { schemaName; tableName: string | null; columnName: string | null; displayName: LocalizedLabel; effective: { main: 'NONE' | 'READ' | 'FULL'; create: boolean; delete: boolean }; inMenu: boolean; hasChildren: boolean }`。`displayName` と `create`・`delete` の型は U4 の確定の形に合わせ、違えばコード生成で直す（契約 C8 は `displayName` の形を決めていない）。
- 写しの関数（`parseWorkRole`・`parseNavigation`・`parseTableAccess`・`parseMyPermissions`）は型の項目だけを写し、崩れていれば `ApiError` の通信の失敗の種類にする（D3）。`enum` は使わず文字列の union（`team.md`）。

## 3. app/work-role

### 3.1 `WorkRoleProvider` と `useWorkRole()`

| 返す値 | 型 | 説明 |
|---|---|---|
| `status` | 文字列の union（`idle`・`loading`・`ready`・`reloading`・`switching`・`load-error`） | functional-spec 4.1（`idle` は未ログイン） |
| `roles` | `readonly WorkRoleRef[]` | 応答の順（ID の順） |
| `current` | `WorkRoleRef` か null | 有効な作業ロール |
| `revision` | 数 | 切り替えの成功・拒否のたびに 1 進む（S2・S8・業務のメニューの読み直しのきっかけ） |
| `switchTo(roleId)` | `Promise<SwitchResult>` | `'unchanged'`・`'switched'`・`'rejected'`・`'failed'` |
| `reload()` | `() => void` | 読み直し（世代を進める） |

- ログイン状態（`useLoginState()`）の知らせごと・`pathname` と `search` の変化ごとに読み直す（`useReloadTriggers`、D4）。同じ描画の中の重なりは1回にまとめる。
- `switchTo` は今のロールと同じなら要求を送らず `'unchanged'`。結果に応じて Toast と読み上げ（3.2）を出すのは部品の側。
- 未ログインになったら値と世代を捨てる。

### 3.2 `WorkRoleSwitcher`（トップバー、`topbarEnd`）

| 状態（D5） | 描くもの |
|---|---|
| loading | 文字「作業ロール: 読み込んでいます」 |
| multiple | `Dropdown`（`placement="bottom-end"`）のトリガー「作業ロール: 営業 ▼」 |
| single | `Dropdown` のトリガー「作業ロール: 営業 ▼」（開くと押せない項目で説明。Tooltip は使わない、R-03） |
| none | `Dropdown` のトリガー「作業ロール: なし ▼」 |
| load-error | 文字「作業ロール: 読み込めませんでした」 |

- 項目（D6）: `{ label: '営業（使用中）', onClick }`・`{ label: '経理', onClick }`…、S8 があれば最後に `{ label: '自分の権限を見る', href: '/me/permissions', onClick: 読み込み直しなしに移る }`。single は `{ label: '切り替える先はありません', disabled: true, description: '割り当てられたロールは営業だけのため、切り替える先はありません' }` と「自分の権限を見る」（ロールの項目は並べない）。none は `{ label: '作業ロールを選べません', disabled: true, description: '割り当てられたロールがありません。管理者に依頼してください。' }` と「自分の権限を見る」。押せない項目は矢印のキーでたどれ、`description` は Dropdown が `aria-describedby` で結ぶ（R-03）。
- 入れ物: `Dropdown` を `<div data-testid="work-role-switcher">` で包む。Dropdown はトリガーの `data-testid` を `dropdown-trigger` に上書きするため、トリガー自身には別の testid を付けられない。テストと E2E は入れ物でユーザーメニューの開き口と区別する（R-01）。
- トリガーは `button`、`aria-label` は「作業ロール: 営業」（見える文字が縮んでも同じ、D9）。見える文字は `<span>` を2つ持ち、768px 未満の `@media` で「作業ロール: 」の部分を `display: none` にする（読み上げの名前は `aria-label` で保つ）。
- 切り替え中（switching）はトリガーに `aria-disabled="true"` を付けて押下を捨てる（フォーカスは保つ）。
- 結果の伝え方: 骨組みが1つ持つ `aria-live="polite"` の区画（`WorkRoleAnnouncer`、見えない文字）に文を入れ、あわせて make-you-chic-ui の Toast を出す。成功「作業ロールを{{name}}に切り替えました」、失敗「作業ロールを切り替えられませんでした。割り当てが変わった可能性があります。」。選んだ後・閉じた後はフォーカスをトリガーに戻す（Dropdown が戻さない場合は部品が戻す。コード生成で確かめる）。
- S2 の［作業ロールを切り替える］から呼べるよう、トリガーへのフォーカスの口 `focusWorkRoleSwitcher()` を `useWorkRole()` に足す（D23）。

## 4. app/navigation

### 4.1 `BusinessNavigationProvider` と `useBusinessNavigation()`

| 返す値 | 型 | 説明 |
|---|---|---|
| `status` | `idle`・`loading`・`loaded`・`reloading`・`load-error` | functional-spec 4.2 |
| `response` | `NavigationResponse` か null | 最後に読めた応答 |
| `reload()` | 関数 | 読み直し（置き場の 403・ホームの［もう一度］から呼ぶ） |

- 読み直しのきっかけは `useWorkRole()` と同じ `useReloadTriggers` と、`revision` の変化（D4）。要求の世代で古い結果を捨てる。読み直しの失敗は前の応答を残す（D18）。

### 4.2 `buildNavSections`（純粋な関数）

```ts
// 説明用の断片（入出力の形だけ）
export interface BuildNavInput {
  loginState: LoginState
  registrations: readonly FeatureRegistration[]
  business: { status: NavStatus; response: NavigationResponse | null }
  location: { pathname: string; search: string }
  language: 'ja' | 'en'
  t: (key: string) => string
  navigate: (to: string) => void
}
export interface BuildNavOutput { sections: SidebarNavSection[]; currentAncestorIds: readonly string[] }
export function buildNavSections(input: BuildNavInput): BuildNavOutput
```

- 手順は functional-spec の W3.1・W3.2。`currentAncestorIds` は今の項目の祖先の `id`（前置きつき）で、開閉の状態に足すのに使う（D16）。
- アイコンの照らし合わせは cross-cutting が `app/registry` に置く許した名前の一覧を使う（D14）。
- `onClick` は `event.preventDefault()` の後に `navigate(href)`（今の `ShellLayout` と同じ）。

### 4.3 `tablePlaceholderPath.ts`（D20）

```ts
// 説明用の断片
export function toTablePlaceholderPath(ref: TableRef, itemId: string): string {
  const query = new URLSearchParams({ schema: ref.schemaName, table: ref.tableName, item: itemId })
  return `/tables?${query.toString()}`
}
export function readTablePlaceholderPath(search: string): { ref: TableRef | null; itemId: string | null }
```

- `readTablePlaceholderPath` は `schema`・`table` のどちらかが無い・空なら `ref` を null にする。値は読んだまま（正規化しない）。

### 4.4 `navExpansion.ts`（D16）

- `loadExpanded()`・`saveExpanded(ids)`・`clearExpanded()`: `sessionStorage` の鍵 `mastersmith.nav.expanded`（JSON の文字列の配列）。読み書きの例外は捕まえて空・何もしないにする（ブラウザが保存を止めている場合）。
- `pruneExpanded(ids, businessTree)`: 今の業務の木に無い `biz:` の `id` を捨てる（`adm:` と `home` は今の登録で照らす）。業務のメニューが loaded のときだけ呼ぶ。loading・reloading・load-error のときは呼ばず、保存も消さない（R-02）。
- `withAncestors(ids, ancestorIds)`: 祖先を足す（ほかは変えない）。

### 4.5 `ShellLayout`（広げる）

- `AppShell` に `navSections`・`navExpandedIds`・`onNavExpandedChange`・`navLabels`（D17）・`topbarEnd={<WorkRoleSwitcher />}`・既存の `user`・`userMenuItems` を渡す。`navItems` は渡さない。
- `buildNavSections` の `currentAncestorIds` の中身（文字列の並び）が前の描画と変わったときに `withAncestors` で祖先を足す。道が変わったときと、業務の木が後から届いた・読み直したときのどちらでも起きる。中身で比べるため、同じ道のまま利用者が閉じた枝を開き直さない。業務のメニューが loaded に入ったとき（読み直しの結果を含む）にだけ `pruneExpanded` を当てる（R-02）。

## 5. app/pages/HomePage（広げる）

- 既存の見出しと説明の下に、`useBusinessNavigation()` と `useWorkRole()` から D19 の案内を `role="status"` の区画で出す。make-you-chic-ui の `Alert`（種類は info）を使う。［もう一度］は `Button`、［自分の権限を見る］は S8 があるときのリンク。

## 6. features/tables（S2）

| 部品 | 中身 |
|---|---|
| `registration.ts` | `featureId: 'tables'`、route `{ path: '/tables', access: 'LOGGED_IN', layout: 'SHELL' }`、文言 |
| `TablePlaceholderPage` | 道の引数を読み（4.3）、`useTableAccess` の状態で描く |
| `useTableAccess(ref, roleKey)` | `GET /api/me/table-access` を `ref` と作業ロールの `current.roleId`・`revision` が変わるたびに読む。世代で古い結果を捨てる。403 で `useBusinessNavigation().reload()` を呼ぶ（D22） |

- 表示: available は `Card` の中に h1（表示名、D13）と「この画面は準備中です。一覧と詳細の画面は、後の更新で追加されます。」。forbidden は h1「この画面を表示する権限がありません」、「今の作業ロール: 経理」（無ければ「なし」）、［作業ロールを切り替える］（ロールが2つ以上、`focusWorkRoleSwitcher()`）、［ホームへ］（`/` へのリンク）。load-error は「読み込めませんでした。」と［もう一度］。
- 状態が変わった文は `aria-live="polite"` の区画で伝え、フォーカスは動かさない（D23）。

## 7. features/mypermissions（S8、Should）

| 部品 | 中身 |
|---|---|
| `registration.ts` | `featureId: 'mypermissions'`、route `{ path: '/me/permissions', access: 'LOGGED_IN', layout: 'SHELL' }`、ユーザーメニュー `{ id: 'mypermissions-open', labelKey: 'mypermissions.menu', path: '/me/permissions', order: 70 }`、文言 |
| `MyPermissionsPage` | 見出し・今の作業ロール・左の `SharedTreeView`・右の値の表（`MyPermissionsDetail`） |
| `useMyPermissions(roleKey)` | 最上位（`GET /api/me/permissions/schemas`）・テーブルの段（`GET /api/me/permissions/tables?schema=…`）・カラムの段（`GET /api/me/permissions/columns?schema=…&table=…`）を読み、作業ロールが変わったら選びと開閉を捨てて読み直す。名前は道に入れず `URLSearchParams` の引数で送る。道は role の確定の形に合わせる（functional-spec 1.2・11節） |

- 木: `nodes` はスキーマの節（`hasChildren: true`）、`loadChildren` はテーブルの節（`hasChildren: false`）を返す。節の id は名前の JSON の配列。`labels` は「〇〇を開く」「〇〇を閉じる」「読み込んでいます」「読み込めませんでした」「もう一度」「子がありません」の日本語と英語。`ariaLabel` は「自分の権限の対象」。
- 表: make-you-chic-ui の `Table` か素の table（見出しのセルを持つ）。列はスキーマ・テーブルを選んだとき「対象・主権限・CREATE・DELETE・業務のメニュー」、カラムは「カラム・主権限」。値は D25 の文字。`Badge` は文字と一緒に使い、色だけにしない。
- 768px 未満は縦に積み、木で選んだら表の見出しへフォーカスを移す。

## 8. features/auth（D27。U7 の持ち物の外）

- `registration.ts`: ユーザーメニューの `auth-logout` を `{ id: 'auth-logout', labelKey: 既存, path: '/logout', order: 100 }` に替え、route `{ path: '/logout', screen: LogoutPage, access: 'LOGGED_IN', layout: 'STANDALONE' }` を足す。
- `LogoutPage`: 描いたときに `useRef` の印で1回だけ `useLogout()` の関数を呼ぶ。終わるまで「ログアウトしています」（`role="status"`）。終わった後の移動は振り分けに任せる（未ログインの LOGGED_IN の画面はログインの画面へ）。
- 骨組みは未ログインの知らせで `clearExpanded()` を呼び、作業ロールと業務のメニューを捨てる。

## 9. 文言（日本語・英語）

| 鍵 | 日本語 | 英語 |
|---|---|---|
| `nav.navigationLabel` | メインメニュー | Main menu |
| `nav.expandGroup` | {{label}}を開く | Expand {{label}} |
| `nav.collapseGroup` | {{label}}を閉じる | Collapse {{label}} |
| `nav.section.business` | 業務 | Business |
| `nav.section.admin` | 管理 | Administration |
| `nav.home` | ホーム（既存） | Home（既存） |
| `nav.businessLoadError` | 業務のメニューを読み込めませんでした | Could not load the business menu |
| `nav.untitled` | （名前なし） | (Untitled) |
| `workRole.label` | 作業ロール: {{name}} | Work role: {{name}} |
| `workRole.loading` | 作業ロール: 読み込んでいます | Work role: loading |
| `workRole.none` | 作業ロール: なし | Work role: none |
| `workRole.loadError` | 作業ロール: 読み込めませんでした | Work role: could not load |
| `workRole.inUse` | {{name}}（使用中） | {{name}} (in use) |
| `workRole.singleItem` | 切り替える先はありません | Nothing to switch to |
| `workRole.singleHint` | 割り当てられたロールは{{name}}だけのため、切り替える先はありません | {{name}} is your only assigned role, so there is nothing to switch to |
| `workRole.noRoleItem` | 作業ロールを選べません | No work role to choose |
| `workRole.noRoleHint` | 割り当てられたロールがありません。管理者に依頼してください。 | No roles are assigned to you. Please ask an administrator. |
| `workRole.myPermissions` | 自分の権限を見る | View my permissions |
| `workRole.switched` | 作業ロールを{{name}}に切り替えました | Switched the work role to {{name}} |
| `workRole.switchFailed` | 作業ロールを切り替えられませんでした。割り当てが変わった可能性があります。 | Could not switch the work role. Your assignments may have changed. |
| `home.notConfigured` | 業務のメニューが設定されていません。 | No business menu has been set up. |
| `home.noRole` | ロールが割り当てられていません。管理者に依頼してください。 | No roles are assigned to you. Please ask an administrator. |
| `home.nothingVisible` | 表示できる業務のメニューがありません。作業ロールに見てよいテーブルがありません。 | There is no business menu to show. Your work role has no tables you can view. |
| `home.loadError` | 業務のメニューを読み込めませんでした。 | Could not load the business menu. |
| `home.retry` | もう一度 | Try again |
| `tables.preparing` | この画面は準備中です。一覧と詳細の画面は、後の更新で追加されます。 | This screen is under preparation. List and detail screens will be added in a later update. |
| `tables.forbidden` | この画面を表示する権限がありません | You do not have permission to view this screen |
| `tables.currentWorkRole` | 今の作業ロール: {{name}} | Current work role: {{name}} |
| `tables.switchWorkRole` | 作業ロールを切り替える | Switch work role |
| `tables.goHome` | ホームへ | Go to home |
| `mypermissions.heading` | 自分の権限 | My permissions |
| `mypermissions.menu` | 自分の権限 | My permissions |
| `mypermissions.main.READ` | READ（見られる・直せない） | READ (can view, cannot edit) |
| `mypermissions.main.FULL` | FULL（見られる・直せる） | FULL (can view and edit) |
| `mypermissions.main.NONE` | NONE（見られない） | NONE (cannot view) |
| `mypermissions.inMenu` | 業務のメニュー: 出る／出ない | Business menu: shown／hidden |
| `mypermissions.noRole` | 作業ロールがありません。すべて見られません（NONE・不可）。 | You have no work role. Nothing can be viewed (NONE, not allowed). |
| `mypermissions.empty` | 対象がありません。 | There is nothing to show. |
| `auth.loggingOut` | ログアウトしています | Logging out |

文言の具体は例で、コード生成で既存の文言の鍵の決まり（機能の鍵は `<featureId>.` で始める、骨組みの鍵と重ねない）に合わせる。

## 10. アクセシビリティ（部品ごと）

| 部品 | 実装 |
|---|---|
| WorkRoleSwitcher | トリガーは `button`（Dropdown が `aria-haspopup`・`aria-expanded` を付ける）、名前は「作業ロール: 営業」。項目は `menuitem` で選択中は名前の「（使用中）」。single・none は開いた一覧の押せない項目と `description`（`aria-describedby`）。トリガーは `work-role-switcher` の入れ物の中。結果は `aria-live="polite"`。フォーカスはトリガーへ戻す |
| サイドバー（5bf1ffe） | `nav` の名前は `navLabels.navigationLabel`、見出しは `h2`、開閉は `button` と `aria-expanded`・`aria-controls`、今の項目は `aria-current="page"` と太字・左の線、長い名前は `aria-label` と `title`。tree の役割は使わない。キーボードは Tab・Enter・Space |
| ホームの案内 | `role="status"` の区画、ボタンとリンクは文字の名前 |
| S2 | h1 の見出し、状態の文は `aria-live="polite"`、ボタンは文字の名前。フォーカスは動かさない |
| S8 | 共有の木（cross-cutting の 2.5）、表は見出しのセル、値は文字で意味を添える |
| LogoutPage | `role="status"` の文 |

`prefers-reduced-motion` のときは、この単位の CSS で動きを付けない（開閉の動きは部品の持ち物）。

## 11. make-you-chic-ui の部品の口（5bf1ffe）

- `AppShell`: `navSections?`・`navExpandedIds?: Set<string>`・`onNavExpandedChange?: (ids: Set<string>) => void`・`navLabels?: Partial<SidebarLabels>`・`topbarEnd?`・`user?`・`userMenuItems?`（`navItems?` は使わない）。
- `SidebarNavSection { id; heading?; items }`・`SidebarNavItem { id; label; icon?; href?; onClick?; current?; children? }`・`SidebarLabels { navigationLabel; expandGroup(label); collapseGroup(label) }`（パッケージの入口から型を読める）。
- `Dropdown`（e82b651 と同じ）: `trigger`・`items`（`label`・`href?`・`onClick?`・`disabled?`・`description?`）・`placement`。トリガーの `data-testid` は `dropdown-trigger` に上書きされる（作業ロールは入れ物 `work-role-switcher` で区別する、R-01）。
- そのほか: `Toast`（`ToastProvider` は骨組みにある）・`Alert`・`Card`・`Button`・`Badge`・`Table`。
- 追加の依頼（`make-you-chic-ui-request-2.md`）の3点が入った版に上げたときは、畳んだ状態と英語の表示の確かめ（6.3 の検査）を見直す。

## 12. テスト

- 画面のテストは対象と同じ場所の `*.test.ts`・`*.test.tsx`（Vitest＋Testing Library＋user-event＋vitest-axe）。部品ごとに axe を1件。描画の後の値は `waitFor`。説明文は英語。
- `buildNavSections.property.test.ts`（fast-check）: functional-spec 6.2 の性質 1〜7。生成器は、深さ 1〜5 の `NavNode` の木（`navigable` と子の組み合わせ、同じ組を指す項目の重なり、`label` に `<script>`・`&`・空の文字列・40 文字を含む任意の文字列、許した名前と許していない名前のアイコン）、登録（section ADMIN の項目）、ログイン状態（admin の真偽）、道（`/`・`/admin/...`・`/tables?...`・任意の文字列）。失敗時の種は報告に出し、`fc.assert` の `seed` で再現できるようにする。
- `tablePlaceholderPath.test.ts`: `/`・`..`・`?`・`#`・`%`・空白・`javascript:`・`https://`・`&`・`=` を含む名前で、作った道が `/tables?` で始まり、読み戻すと同じ名前になる（fast-check の往復の性質もあてる）。
- `navExpansion.test.ts`: 壊れた保存の値・保存の例外・捨てる・祖先を足す。業務のメニューが読み込み前・失敗のときに捨てない、木が道より後に届いたときに祖先を足す、同じ道で閉じた枝を開き直さない（R-02）。
- E2E の `support/userMenu.ts`: 作業ロールの Dropdown があっても1件に絞れる（strict モードで落ちない）ことを、書き換えた既存の E2E の通過で確かめる（R-01）。
- `WorkRoleProvider.test.tsx`・`BusinessNavigationProvider.test.tsx`: きっかけごとの読み直し、遅れた古い応答を捨てる、未ログインで捨てる、切り替えの4つの結果。
- `WorkRoleSwitcher.test.tsx`・`ShellLayout.test.tsx`（書き換え）・`HomePage.test.tsx`・`TablePlaceholderPage.test.tsx`・`MyPermissionsPage.test.tsx`・`LogoutPage.test.tsx`: functional-spec 6.1 の項目。
- 実際のブラウザの検査と E2E は functional-spec 6.3〜6.5。
