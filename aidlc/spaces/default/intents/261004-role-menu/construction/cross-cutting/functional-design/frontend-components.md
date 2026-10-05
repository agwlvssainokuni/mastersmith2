# 画面の部品（Frontend Components）— U1 cross-cutting

出典: `unit-of-work.md`（U1、R-01・R-02・R-04）、`contract-summary.md`（C2）、`components.md`（SharedTreeView・AppFrame）、`mockups.md`（S1・S4・S8）、`interaction-spec.md`（PermissionTree・NavTree）、`design-system-mapping.md`、この段の答え（Q3〜Q6: A、まとめの確認）、`rules.md`（BR2〜BR5）。

U1 は kind が library だが、画面の部分（共有の木・登録の型・ESLint の制限・ログアウトの口）を持つため、この文書で設計する（単位の一覧の R-04 の考え方）。

## 1. make-you-chic-ui で確かめたこと

- ソース（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/`、固定先 e82b651）に、木の部品は無い（Alert・AppShell・Avatar・Badge・Button・Card・Checkbox・Dropdown・FormField・Icon・Modal・RadioGroup・Select・Switch・Table・Tabs・Textarea・TextInput・Toast・Tooltip）。共有の木は frontend の側で作る。
- `Icon` は `name`（`IconName`）と `size` を受ける。`IconName` は `Icon/registry.ts` の 18 個（menu・chevron-down・chevron-up・close・check・bell・user・search・edit・trash・download・settings・home・list・info・success・warning・danger）。登録の型の `icon` と、その照合（BR5.4）はこの一覧を使う。
- 5bf1ffe（対応完了の報告。ソースは vendor に無い）の `AppShell` の `navSections` と、Sidebar の開閉の形（`button` と `aria-expanded`、`ul` の入れ子、tree の役割なし、リンクと開閉のボタンを分ける）は、報告の記述だけを根拠にする。共有の木の節の形は、この分け方にそろえる（Q5: A）。`navSections` への組み立ては U7 が持つ。

## 2. 共有の木 `SharedTreeView`（`frontend/src/shared/tree/`）

### 2.1 構成

| ファイル | 中身 |
|---|---|
| `SharedTreeView.tsx` | 木の入口。最上位の `ul` を描き、子の読み込みの状態（`SharedTreeChildState`）を節の id ごとに持つ |
| `SharedTreeNodeItem.tsx` | 1つの節（`li`）。開閉のボタン・選ぶボタン・印・子の区画（読み込み中・失敗・0 件・子の `ul`） |
| `types.ts` | `SharedTreeNode`・`SharedTreeBadge`・`SharedTreeLabels`・`SharedTreeViewProps` |
| `SharedTreeView.css` | 段ごとの字下げ、選んだ節の見た目（太字と左の線。色だけに頼らず `aria-current` も付ける） |
| `SharedTreeView.test.tsx` | 画面のテストと axe（3.6） |

`shared/` に置くため、`app/` と `features/` を読まない（BR2.2）。文言は持たず `labels` で受ける（BR4.9）。

### 2.2 props（`SharedTreeViewProps`）

| prop | 型 | 必須 | 説明 |
|---|---|---|---|
| `nodes` | `SharedTreeNode` の配列 | はい | 最上位の節。新しい配列に替えると、持っていた子を捨てる（BR4.5） |
| `loadChildren` | `(id) => Promise<SharedTreeNode の配列>` | はい | 節を開いたときに子を読む |
| `selectedId` | 文字列か null | はい | 選んでいる節 |
| `onSelect` | `(id) => void` | はい | 選ぶボタンが押された |
| `expandedIds` | 文字列の読み取りだけの集まり | はい | 開いている節 |
| `onToggle` | `(id, expanded) => void` | はい | 開閉のボタンが押された（expanded は次の状態） |
| `labels` | `SharedTreeLabels` | はい | 文言（C2 に足す互換の変更） |
| `ariaLabel` | 文字列 | はい | 最上位の `ul` の読み上げの名前（例「権限の設定の対象」） |

```ts
// 説明用の断片（型の形だけ）
export interface SharedTreeViewProps {
  nodes: readonly SharedTreeNode[]
  loadChildren: (id: string) => Promise<readonly SharedTreeNode[]>
  selectedId: string | null
  onSelect: (id: string) => void
  expandedIds: ReadonlySet<string>
  onToggle: (id: string, expanded: boolean) => void
  labels: SharedTreeLabels
  ariaLabel: string
}
```

### 2.3 節の描き方

```
<li>
  [開閉のボタン ▸/▾]  [選ぶボタン: 表示名]  [印…]
  <ul> 子の節 … </ul>   ← 開いていて読み込み済みのとき
  読み込み中 / 失敗の文 [再試行] / 子が無い   ← 状態に応じて1つ
</li>
```

- 開閉のボタン: `hasChildren` が真の節だけ。`aria-expanded` を常に付け、名前は `labels.expand(label)`／`labels.collapse(label)`。押すと `onToggle(id, !開いている)`（BR4.2・BR4.3・BR4.8）。`hasChildren` が偽の節は、同じ幅の空きを置いて字下げをそろえる。
- 選ぶボタン: 表示名を文字として描く（BR4.1）。選んでいる節に `aria-current="true"`。押すと `onSelect(id)`（BR4.2・BR4.3）。
- 印: `badges` の `text` を文字で描き、`tone` は見た目だけ（BR4.11）。
- 子の区画: 状態に応じて、子の `ul`、「読み込み中」（`role="status"`）、失敗の文と再試行のボタン、「子が無い」のどれか1つ（BR4.5・BR4.6・BR4.12）。

### 2.4 状態

| 状態 | 持ち主 | 中身 |
|---|---|---|
| 開閉 | 呼ぶ側 | `expandedIds`（BR4.4） |
| 選び | 呼ぶ側 | `selectedId` |
| 子の読み込み | 木の中 | 節の id ごとの `SharedTreeChildState`（notLoaded・loading・loaded・failed）。遷移は `functional-spec.md` 5.1 |
| 読み込みの世代 | 木の中 | `nodes` を替えるたびに進め、古い結果を捨てる（BR4.7） |

`expandedIds` に初めから入っている節（例: 直接開いたときの祖先）も、描いた時点で未読なら読み込みを始める（BR4.5）。

### 2.5 操作とアクセシビリティ

| 項目 | 実装 |
|---|---|
| 役割 | 入れ子の `ul`・`li` と `button`。tree・treeitem の役割は使わない（BR4.10） |
| キーボード | Tab で選ぶボタンと開閉のボタンを順に移り、Enter・Space で押す。矢印のキーの決まりは持たない |
| 読み上げ | 開閉のボタンの名前に表示名を含める。選んだ節は `aria-current`。読み込み中は `role="status"` で知らせる |
| フォーカス | 開閉・選びでフォーカスを動かさない。再試行の後もフォーカスは再試行の場所（読み込み中の表示）に残す。選んだ後に表へ移すのは呼ぶ側（S4 の mobile の決まり）の持ち物 |
| 見た目 | 段ごとに字下げ。選んだ節は太字と左の線（make-you-chic-ui の Sidebar の今の項目と同じ考え方） |

### 2.6 テスト（`team.md` の Testing Posture）

- 開閉のボタンが `onToggle` を正しい次の状態で呼び、`aria-expanded` が `expandedIds` に合う。選ぶボタンが `onSelect` を呼び、`aria-current` が `selectedId` の節だけに付く。
- 子は最初に開いたときだけ読み、閉じて開き直しても読み直さない。`nodes` を替えると読み直す。読み込み中に `nodes` を替えたら古い結果を描かない。
- 失敗で文と再試行が出て、再試行で読み直す。0 件で「子が無い」。
- 表示名・印に `<`・`>`・`&`・`"`・`'` を含めても文字として出る。
- キーボードだけ（Tab・Enter・Space）で深い節まで開いて選べる。
- vitest-axe の検査を1件（開いた状態・読み込み中・失敗を含む）。描画の後の値は `waitFor` で待つ。

## 3. 画面の登録の型（`frontend/src/app/registry/types.ts`）

### 3.1 足す項目

```ts
// 説明用の断片（足す所だけ）
import type { IconName } from 'make-you-chic-ui'

/** サイドバーの項目の区画。見出しの文言と並びは骨組みが持つ */
export type SidebarSection = 'ADMIN'

export interface SidebarItemRegistration {
  // 既存: id・labelKey・path・order・visibleWhen
  section: SidebarSection
  icon?: IconName
}
```

- `LoginStateProvider` に任意の `logout?: () => Promise<void>` を足す（4節）。
- 今ある4件の登録（`features/admin`・`dsl`・`invitation`・`useradmin` の `registration.ts`）に `section: 'ADMIN'` を足す。アイコンは足さない（U6・U7 で決めてよい）（BR5.6）。
- `IconName` はパッケージの入口（`src/index.ts` 34 行の `export type { IconProps, IconName }`）から読める。実行時の照合（BR5.4）に使う名前の一覧は、入口が値として出していない（`iconRegistry` は入口から出ていない）ため、`app/registry` に 18 個の一覧を置き、型 `IconName` と一致することを型の検査で確かめる形（例: `satisfies readonly IconName[]` と、逆向きの網羅の確かめ）にする。make-you-chic-ui の固定先を上げてアイコンが増えたら、この一覧を直す。

### 3.2 登録の検査（`validateRegistrations.ts`）に足す問題

| 確かめること | 問題の文の例 |
|---|---|
| section が SidebarSection の値 | `<featureId>: サイドバーの項目 "<id>" の区画が正しくありません` |
| section ADMIN なら visibleWhen ADMIN | `<featureId>: 管理の区画の項目 "<id>" は visibleWhen を ADMIN にしてください` |
| icon が許した名前 | `<featureId>: サイドバーの項目 "<id>" のアイコン "<icon>" は使えません` |

問題があれば今までどおり `RegistrationError` で起動を止める（BR5.1・BR5.3・BR5.4）。テストは `validateRegistrations.test.ts` に足す。

### 3.3 U7 への引き渡し（組み立ては U7）

- U7 は、登録の項目を section ごとに束ね、見出しつきの区画（5bf1ffe の `navSections` の1区画）にする。見出しの文言・区画の並び・「ホーム」の置き場・項目の id の重なりを避ける形（業務のメニューの id との区別）は U7 が決める。
- `icon` が無い項目には骨組みの既定（今は `list`）を当てる（BR5.2）。
- 登録の区画と `visibleWhen` は見せ方だけで、管理の API はサーバー側で守る（BR5.5）。

## 4. ログアウトの口（既存の違反の直し）

| 部品 | 変更 |
|---|---|
| `app/registry/types.ts` | `LoginStateProvider` に任意の `logout` |
| `features/auth/loginStateProvider.ts` | `logout`（`authSession` の `logout`）を渡す |
| `app/login-state/LoginStateGate.tsx` | 受け取った提供元の `logout` を文脈で渡し、`useLogout()` を出す |
| `features/registration/useRegistration.ts` | `../auth/authSession` の import を消し、`useLogout()` を使う |

- `useLogout()` は Promise を返す関数を返す。提供元に `logout` が無ければ何もせずに終わり、`logout` の失敗は外へ出さない（BR3.2）。
- `authSession` の持ち主は `auth` のまま（Q4: A）。登録の完了の画面の「ログアウトして続ける」の流れは変えない（BR3.3）。
- テスト: `useLogout` が提供元の `logout` を呼ぶ・無いときに何もしない・失敗を外へ出さない。`auth` の登録が `logout` を持つ。既存の `RegistrationPage` のテストが通る。

## 5. ESLint の制限（`frontend/eslint.config.js`）

- `src/features/` のディレクトリの一覧を設定の中で読み、機能ごとに `files: ['src/features/<A>/**/*.{ts,tsx}']`・`ignores: ['**/*.test.ts', '**/*.test.tsx']` の決まりを作り、`no-restricted-imports` の `patterns` で A の外の兄弟の機能を指す相対の道を止める（BR2.1・BR2.3）。
- `src/shared/**` に `app/`・`features/` を指す道を止める決まりを当てる（BR2.2）。
- 誤りの文は「機能どうしは直接 import せず、共有するものは src/shared/ へ移してください」のように直し方を示す。

```js
// 説明用の断片（形だけ。patterns の書き方はコード生成で確かめる）
const features = readdirSync('src/features', { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)
const featureRules = features.map((name) => ({
  files: [`src/features/${name}/**/*.{ts,tsx}`],
  ignores: ['**/*.test.ts', '**/*.test.tsx'],
  rules: {
    'no-restricted-imports': ['error', { patterns: siblingPatterns(name, features) }],
  },
}))
```

- `siblingPatterns` は、兄弟の機能 B ごとに `../B`・`../B/*`・`../../features/B`・`../../features/B/*` などを止める（機能の中の深さは今は最大2段で、深い段からの道も含める）。機能の中の下位のディレクトリ（例 `../api/types`）は B の名前に当たらない限り止めない。止め方の正しさは、違反の見本のファイルを使わず、ESLint の `Linter` を使ったテスト（`eslint.config.test.ts` など、置き場はコード生成で決める）で、止めるべき道と許す道の両方を確かめる。

## 6. 文言

| 文言 | 持ち主 |
|---|---|
| 共有の木の開く・閉じる・読み込み中・失敗・再試行・子が無い | 呼ぶ側（U6・U7）が `labels` で日本語と英語を渡す |
| 登録の検査の問題の文 | 起動を止める開発向けの文（画面の文言ではない。今の `validateRegistrations` と同じ日本語） |
| ESLint の誤りの文 | 開発向けの日本語 |

この単位が画面に出す文言は持たない（NFR4.3 は呼ぶ側が満たす）。
