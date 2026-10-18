# make-you-chic-ui への依頼（5）

依頼元: MasterSmith（Intent 261016-business-data-views、業務データの一覧・詳細の画面）
置き場の決まり: `aidlc/spaces/default/memory/team.md` の Way of Working（画面イメージの段で一覧にし、機能設計の承認の前に出す。上流の対応は上流のソースで確かめてから固定先を上げる）
今の固定先: `a9309c4`（この Intent で `b0a1e79` に上げる）

## 1. Topbar の利用者のメニューのトリガーの名前（WCAG 2.5.3）

- **困っていること**: トップバーの利用者のメニューのトリガー（Avatar の頭文字を見せるボタン）の読み上げの名前に、見えている文字（頭文字。例: 氏名「検査 太郎」の「検太」）が含まれない見込み。axe-core 4.14 以降の `label-content-name-mismatch` の規則で違反になり、MasterSmith は axe-core を上げられない。
- **確かめの状態**: MasterSmith の E2E（表示の設定の画面）で axe-core 4.14 を当てて違反の要素を確かめた後に、この項目を確定して送る（違反の部品が別だったら、この項目は送らない）。
- **お願いしたい形**: トリガーの読み上げの名前が、見えている文字で始まる（例: 「検太 利用者のメニュー」）。`b0a1e79` の Dropdown の直しと同じ考え方。

## 2. Table の横のスクロールの領域へのキーボードのフォーカスとラベル

- **困っていること**: Table の枠（`.mycui-table-wrapper`、`overflow-x: auto`）は、表が広いと横にスクロールするが、キーボードでフォーカスできずラベルも無い。100 カラムまでの表を、キーボードだけの利用者が横にたどれない（WCAG 2.1.1・axe の `scrollable-region-focusable`）。
- **お願いしたい形**: 枠に `role="region"`・`tabIndex={0}`・ラベル（`aria-label` か `aria-labelledby` を渡せる口。既定は表の `aria-label` を使うなど）を付ける。表がはみ出さないときにフォーカスの止まりを増やさない形でもよい。
- **取り込みまでの手当て**: MasterSmith の側で枠を包む。

## 3. Table の1ページの件数の選択

- **困っていること**: Table は `pageSize` を受け取るが、利用者が1ページの件数を選ぶ部品が無い。MasterSmith は 20・50・100 から選ばせたい（Should）。
- **お願いしたい形**: ページ送りの並びに、件数の選択（選べる件数の一覧と、変えたときの通知 `onPageSizeChange`、文言は `labels` で差し替え）を置ける口。
- **取り込みまでの手当て**: MasterSmith の側で Table の外に Select を置く。

## 依頼しないもの

- NULL の見せ方（業務データの画面に固有のため MasterSmith で作る）
- 日付の選択の部品（ブラウザの標準の入力を使う）
- 検索の領域の開閉の部品（Button と領域で作る）

## 対応の確かめ

上流の対応は、報告ではなく上流のソース（読み取りだけの `git show`）で口の形を確かめてから固定先を上げる。固定先の更新は承認を得た専用のコミットで行い、前後のコミットハッシュ・上流の `main` から辿れること・vendor の lockfile の差（`hasInstallScript` の増減）を記録する。
