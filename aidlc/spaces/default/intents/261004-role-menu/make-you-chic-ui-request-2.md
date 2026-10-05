# make-you-chic-ui への依頼（2）: サイドバーの N 階層のメニューの残りの3点

依頼元: mastersmith2（Intent 261004-role-menu「F: ロールベースの権限の管理、I: メニュー・ナビゲーション（N階層）」）
対象の版: make-you-chic-ui `5bf1ffe`（前回の依頼 `make-you-chic-ui-request.md` への対応の版。mastersmith2 は B9 でこの版に固定先を上げる予定）
対象の部品: `AppShell`・`Sidebar`・`Topbar`（`packages/make-you-chic-ui/src/components/AppShell/`）

## 1. 背景

前回の依頼（入れ子のサイドバー）を `5bf1ffe` で対応していただき、ありがとうございます。mastersmith2 の機能設計（U7 app-frame-ui）で、`5bf1ffe` のソースを確かめたところ、依頼の必須の項目はそろっていました。あわせて、畳んだ状態と英語の表示について、次の3点が残っていると分かりました。mastersmith2 は `vendor/make-you-chic-ui` を直接変えない決まりのため、make-you-chic-ui の側で対応してほしい点としてお送りします。

mastersmith2 はブランドカラー4種 × テーマ（light・dark）× 文字の大きさ3種と、375px の幅を合わせた 20 組で、実際のブラウザの axe を流します。畳んだ状態もこの検査の対象です。

## 2. 今の作り（5bf1ffe の時点。行番号はそのコミットのファイル）

- `Sidebar.tsx` 228〜236 行: 区画ごとに `headingId` を作り、畳んでいないときだけ `<h2 id={headingId}>` を描く（`!collapsed && section.heading && …`）。一方、区画の `<ul>` には畳んだ状態でも `aria-labelledby={headingId}` を付ける。
- `Sidebar.tsx` 175〜215 行（`renderCollapsedItem`）: 畳んだ状態では1段目の項目だけを描く。子を持たないリンクの項目には `aria-current` を付けるが、子を持つ1段目の項目（ボタン）には今の項目を示す印が無い。今の項目がその項目の子孫にあっても、畳んだ状態では分からない。
- `Topbar.tsx` 54 行: 畳むボタンの `aria-label` が `collapsed ? 'サイドバーを開く' : 'サイドバーを折り畳む'` の日本語の固定。69 行のユーザーメニューのトリガーの `aria-label`（`` `${user.name}のメニュー` ``）も日本語の固定。`navLabels` はサイドバーの中の文言だけを受け取る。

## 3. 依頼する機能

### 3.1 畳んだ状態の区画の `aria-labelledby`

- 畳んだ状態で見出しを描かないときは、区画の `<ul>` の `aria-labelledby` を外すか、見出しを見えない形（視覚的に隠す）で残して、指す先の要素がある状態にしてほしい。
- 今の形では、存在しない id を指す `aria-labelledby` が残る。axe の規則（ARIA の属性の値の確かめ）で違反になりうる（mastersmith2 では未検証）。
- 案: 見出しを視覚的に隠して残すと、畳んだ状態でも区画の名前が読み上げで伝わる。make-you-chic-ui の方針に合わせてよい。

### 3.2 畳んだ状態での今の項目の印

- 畳んだ状態で、今の項目（`current`）が子を持つ1段目の項目の子孫にあるとき、その1段目の項目に、今の画面がその枝にあることを示す印を付けてほしい。印は見た目（左の線など）と読み上げの両方で分かる形にしてほしい（例: ボタンの名前に「（現在のページを含む）」を足す、など）。
- 文言を使うときは、3.3 の口と同じく外から渡せるようにしてほしい。
- 案: 開いた状態の `current` と同じ見た目（`--color-sidebar-active-border` の左の線）を1段目のボタンに付け、読み上げの名前の文言は `SidebarLabels` に足す。

### 3.3 トップバーの文言を外から渡す口

- 畳むボタンの名前（「サイドバーを開く」「サイドバーを折り畳む」）と、ユーザーメニューのトリガーの名前（「〇〇のメニュー」）を、言語に合わせて渡せるようにしてほしい（mastersmith2 は日本語と英語を切り替える）。
- 案: `AppShell` に `topbarLabels`（`expandSidebar`・`collapseSidebar`・`userMenu(name)`）を足すか、`navLabels` の型に足す。未指定の項目は今の日本語の文言のままにする（`navLabels` と同じ規約）。

## 4. 受け入れの目安

mastersmith2 の側で次を確かめる（make-you-chic-ui の側のテストにも入れてもらえるとありがたい）。

- 畳んだ状態で、区画の `aria-labelledby` が存在する要素だけを指す（または付かない）。
- 畳んだ状態で、今の項目が子孫にある1段目の項目が、色だけに頼らずに分かり、読み上げでも分かる。
- 英語の文言を渡すと、畳むボタンとユーザーメニューのトリガーの名前が英語になる。渡さなければ今の日本語のまま。
- 開いた状態の今の振る舞い（`navSections`・`navExpandedIds`・`navLabels`・`navItems` の併用）が変わらない。
- 畳んだ状態と開いた状態の両方で、ブランドカラーとテーマのすべての組で axe の違反が無い。

## 5. 時期

- mastersmith2 では、この部品を使う作業（骨組みの画面、Bolt 9）を最後に置いている。B9 までに取り込めれば、その版を固定先にする。間に合わないときは `5bf1ffe` で進め、残る点を mastersmith2 の側の記録に差として残し、取り込みは後の固定先の更新で行う。
- 取り込みは、mastersmith2 の `vendor/make-you-chic-ui` の固定先を更新する専用のコミットで行う。
