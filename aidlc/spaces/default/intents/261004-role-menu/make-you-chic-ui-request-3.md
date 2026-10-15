# make-you-chic-ui への依頼（3）: Dropdown の一覧を画面に収める口と、畳んだサイドバー・トップバーの残りの2点

依頼元: mastersmith2（Intent 261004-role-menu「F: ロールベースの権限の管理、I: メニュー・ナビゲーション（N階層）」の振り返りの持ち越し K1）
対象の版: make-you-chic-ui `5bf1ffe`（mastersmith2 の今の固定先。2026-10-15 の時点で上流の origin の先頭も `5bf1ffe`）
対象の部品:

- `Dropdown`（`packages/make-you-chic-ui/src/components/Dropdown/`）と、位置の計算 `computeFloatingPosition`（`packages/make-you-chic-ui/src/utils/computeFloatingPosition.ts`）
- `Sidebar`・`Topbar`（`packages/make-you-chic-ui/src/components/AppShell/`）

## 1. 背景

前回の依頼（2）の3点のうち、3.1（畳んだ状態の区画の `aria-labelledby`）は、mastersmith2 の実際のブラウザの axe で違反が出ませんでした（ブランドカラー4種 × テーマ2種 × 文字の大きさ、375px の幅を含む 20 組）。残る 3.2・3.3 は `5bf1ffe` に無いため、mastersmith2 では次のように扱っています。

- 3.2・3.3: 受け入れた制約として残す。英語の画面でも、トップバーの畳むボタンとユーザーメニューのトリガーの名前は日本語のまま。
- Dropdown: 部品に口が無いため、mastersmith2 のアプリの CSS で make-you-chic-ui のクラスを上書きしている（下の 2.1）。

mastersmith2 は `vendor/make-you-chic-ui` を直接変えない決まりです。そのため、アプリ側の上書きを外して部品の口に置き換えられるよう、次の3点をお願いします。優先は 3.1（Dropdown）が最も高く、3.2・3.3 は前回の依頼の再送です。

## 2. 今の作り（5bf1ffe の時点。行番号はそのコミットのファイル）

### 2.1 Dropdown の一覧の幅と位置

- `Dropdown.css` 16〜25 行: `.mycui-dropdown-menu` は `position: fixed`・`min-width: 160px` で、幅の上限（`max-width`）が無い。
- `Dropdown.tsx` 143〜150 行: 一覧は `createPortal` で body の直下に描かれ、位置は `style={{ top, left }}` で与えられる。
- `computeFloatingPosition.ts` 92〜102 行: はじめの置き方で画面からはみ出すときは、反対の置き方（`OPPOSITE[placement]`）に切り替えるだけ。切り替えた後もはみ出すときに画面の中へ詰める処理と、一覧の幅を画面の幅に抑える処理が無い。
- 起きること: mastersmith2 のトップバーの「作業ロール」の切り替え（Dropdown）で、ロールの名前が長いと、幅 375px の画面で一覧が画面の外にはみ出し、横のスクロールが出る。
- mastersmith2 の今の手当て: 768px 未満で、作業ロールのトリガーが開いている間だけ、`body:has(.work-role-switcher [aria-expanded='true']) .mycui-dropdown-menu { max-width: calc(100vw - 2 * var(--space-4)); }` で上書きしている。次の2つの弱さがある。
  - 部品の内部のクラス名に頼っている。
  - `:has()` を扱えないブラウザでは効かない。

### 2.2 畳んだ状態での今の項目の印（前回の依頼 3.2 の再送）

- `Sidebar.tsx` 175〜215 行（`renderCollapsedItem`）: 子を持たないリンクの項目には `aria-current` を付ける。一方、子を持つ1段目の項目（ボタン）には今の項目を示す印が無い。
- そのため、今の項目がその項目の子孫にあっても、畳んだ状態では見た目でも読み上げでも分からない。

### 2.3 トップバーの文言（前回の依頼 3.3 の再送）

- `Topbar.tsx` 54 行: 畳むボタンの `aria-label` が `collapsed ? 'サイドバーを開く' : 'サイドバーを折り畳む'` の日本語の固定。
- `Topbar.tsx` 69 行: ユーザーメニューのトリガーの `aria-label`（`` `${user.name}のメニュー` ``）も日本語の固定。
- `SidebarLabels`（`Sidebar.tsx` 54〜64 行）はサイドバーの中の文言だけを受け取り、トップバーの文言を渡す口が無い。

## 3. 依頼する機能

### 3.1 Dropdown の一覧を画面に収める口（優先）

- 開いた一覧が、画面（viewport）の左右と下の端からはみ出さないようにしてほしい。
- 手当ての中身:
  - 一覧の幅の上限を、画面の幅から左右の余白を引いた値に抑える。
  - 反対の置き方に切り替えてもはみ出すときは、左右の位置を画面の中へ詰める。
- 案（make-you-chic-ui の方針に合わせてよい）:
  - `.mycui-dropdown-menu` に `max-width: calc(100vw - 2 * var(--space-4))` を既定で置く。
  - `computeFloatingPosition` で、切り替えた後もはみ出すときは `left` を `[余白, 画面の幅 − 一覧の幅 − 余白]` の範囲に詰める。
  - 余白や幅の上限を利用者が変えたいときのために、`Dropdown` に `menuClassName` か `menuStyle`（一覧の要素に足す）を渡す口があると、アプリの側で内部のクラス名に頼らずに済む。
- 一覧の中の長い項目の名前は、折り返すか省略して、一覧の幅の中に収めてほしい（今は `.mycui-dropdown-item` の中の文字の扱いが決まっていない）。

### 3.2 畳んだ状態での今の項目の印（前回の依頼 3.2 と同じ）

- 畳んだ状態で、今の項目（`current`）が子を持つ1段目の項目の子孫にあるときは、その1段目の項目に「今の画面がこの枝にある」ことを示す印を付けてほしい。
- 印は、見た目（色だけに頼らない形）と読み上げの両方で分かるようにしてほしい。
- 案:
  - 見た目は、開いた状態の `current` と同じ左の線（`--color-sidebar-active-border`）を1段目のボタンに付ける。
  - 読み上げの名前の文言は `SidebarLabels` に足す（例: `containsCurrent: (label) => \`${label}（現在のページを含む）\``）。

### 3.3 トップバーの文言を外から渡す口（前回の依頼 3.3 と同じ）

- 次の名前を、言語に合わせて渡せるようにしてほしい（mastersmith2 は日本語と英語を切り替える）。
  - 畳むボタンの名前（「サイドバーを開く」「サイドバーを折り畳む」）
  - ユーザーメニューのトリガーの名前（「〇〇のメニュー」）
- 案: `AppShell` に `topbarLabels`（`expandSidebar`・`collapseSidebar`・`userMenu(name)`）を足すか、`navLabels` の型に足す。
- 未指定の項目は、今の日本語の文言のままにしてほしい（`navLabels` と同じ規約）。

## 4. 受け入れの目安

mastersmith2 の側で次を確かめます。make-you-chic-ui の側のテストにも入れていただけるとありがたいです。

- **Dropdown:**
  - 幅 375px の画面で、トリガーが左端・右端のどちらにあっても、開いた一覧が画面の中に収まり、横のスクロールが出ない。
  - 一覧の項目に長い名前（例: 全角 40 文字）があっても、上と同じく収まる。
  - 今の振る舞いが変わらない。
    - `placement`（`bottom-start`・`bottom-end`）の指定どおりに置かれる。
    - はみ出すときは反対の置き方に切り替わる。
    - キーボードの操作とフォーカスの戻りが今と同じ。
- **畳んだサイドバー:**
  - 今の項目が子孫にある1段目の項目が、色だけに頼らずに分かり、読み上げでも分かる。
  - 子を持たない1段目の項目の `aria-current` は今のまま。
- **トップバー:**
  - 英語の文言を渡すと、畳むボタンとユーザーメニューのトリガーの名前が英語になる。
  - 渡さなければ、今の日本語のまま。
- **axe:** 開いた状態と畳んだ状態、Dropdown を開いた状態で、ブランドカラーとテーマのすべての組で axe の違反が無い。

## 5. 取り込みの予定

- mastersmith2 では、この依頼を次の Intent の「画面・コードの小さな直し」の束（振り返りの第2の束）の前に送る。
- 公開されたら、`vendor/make-you-chic-ui` の固定先を更新する専用のコミット（前後のハッシュを記録）で取り込み、続けて次のように置き換える。
  - アプリの CSS の上書き（`frontend/src/app/work-role/WorkRoleSwitcher.css` の `:has()` の規則）を外し、3.1 の口に置き換える。
  - 3.2・3.3 の文言を、画面の言語（日本語・英語）で渡す。
- 間に合わないときは、この3点を除いて第2の束を進め、取り込みは公開の後の固定先の更新で行う。
