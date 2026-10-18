# デザインシステムの対応 — business-data-views

出典: `mockups.md`・`interaction-spec.md`。make-you-chic-ui の部品はサブモジュール `vendor/make-you-chic-ui`（固定先 `a9309c4`、この Intent で `b0a1e79` に上げる）の `packages/make-you-chic-ui/src/components/` をソースで確かめた（`project.md` の学び）。

## 画面の要素と部品

| 画面の要素 | make-you-chic-ui の部品 | 使い方・注意 |
|---|---|---|
| アプリの枠・サイドバー・トップバー | AppShell（前の Intent のまま） | 今の項目（`aria-current`）はテーブルの項目のまま |
| 一覧の表 | Table | `columns`（`sortable`・`width`）・`sortState`/`onSortChange`（`aria-sort` を出す）・`page`/`pageSize`/`onPageChange`/`totalCount`・`labels`（英語の文言に差し替え）・`aria-label`。行の選択と `renderDetail` は使わない |
| 1ページの件数の選択 | Select（取り込みまで Table の外に置く） | Table に件数の選択の口が無い。依頼（5） |
| 横のスクロールの領域 | Table の枠（`overflow-x: auto`） | 枠にフォーカスとラベルが無い。依頼（5）。取り込みまでは frontend の側で `role="region"`・`aria-label`・`tabIndex` の枠で包む |
| 行の「操作」 | Dropdown | `placement="bottom-end"`。`b0a1e79` で説明のある項目の DOM の形が変わる（WCAG 2.5.3 の直し）。トリガーは Button |
| 作成・更新・削除のボタン | Button | 処理中は押せなくする |
| 検索の領域の開閉 | Button（`aria-expanded`）と frontend の領域 | 開閉の部品は無いため、ボタンと領域で作る |
| 検索・入力の項目 | FormField・TextInput（`type` を渡す）・Textarea・Select・RadioGroup・Checkbox・Switch | `TextInput` は `type`（`date`・`datetime-local`・`time`・`number`）を渡せる。日付の選択の部品は無い（ブラウザの標準の入力を使う。RQ7） |
| 理由・状態の表示 | Alert | 主キーの無いテーブル・ビュー・書き込みの設定が無い・保存されたか分からない |
| 成功の知らせ | Toast | 作成・更新・削除の成功 |
| 削除の確かめ・離れる確かめ | Modal | `role="alertdialog"`（削除）、`closeOnBackdropClick={false}`、`initialFocusRef` で取り消す側へ |
| 長い値の全体 | Tooltip | 省略したセルにポインターを重ねたとき |
| NULL の「（値なし）」 | なし（frontend の側の小さな表示） | 薄い色の文字。色だけで表さない |
| 詳細の項目と値 | なし（`dl` で組む） | Card に入れてよい |

## make-you-chic-ui に無いもの（上流へ取り込みを諮る一覧）

依頼の文面は `aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-5.md`。機能設計の承認の前に出す（team.md の Way of Working）。

| 足りない点 | 依頼（5） | 取り込みまでの手当て |
|---|---|---|
| Topbar の利用者のメニューのトリガーの名前に、見えている文字（頭文字）が含まれない（WCAG 2.5.3。E2E 080 の違反の部品と確かめたとき） | 入れる | なし（axe-core 4.14 の引き上げは取り込みの後。FR14.5） |
| Table の横のスクロールの領域に、キーボードのフォーカスとラベルが無い | 入れる | frontend の側で枠を包む |
| Table に1ページの件数の選択が無い | 入れる | Table の外に Select を置く |
| NULL の見せ方 | 入れない（業務データの画面に固有） | frontend の側で作る |
| 日付の選択の部品 | 入れない | ブラウザの標準の入力 |
| 検索の領域の開閉の部品 | 入れない | Button と領域で作る |

## デザイントークン

新しいトークンは足さない。「（値なし）」は既存の淡い文字の色（`--color-text-muted` 相当）を使い、背景との対比 4.5:1 を保つ。
