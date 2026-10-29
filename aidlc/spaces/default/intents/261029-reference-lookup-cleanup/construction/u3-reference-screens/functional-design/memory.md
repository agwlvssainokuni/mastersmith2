<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-10-29T23:12:48Z — U1 の検索は件数を返さず hasNext だけのため、make-you-chic-ui の Table のページ送り（totalCount から総ページを出す）は、合わせた totalCount と件数を言わない pageStatus の文で使うと読んだ（D25、差 X2）。
- 2026-10-29T23:12:48Z — 作成・更新の画面の定義（purpose つき）は選択肢という参照先の行の値を含むため、一覧・詳細と共有する機能の記憶に書かず、開くたびに読むと読んだ（D2）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-29T23:12:48Z — 「決まっていること」の「入力の型は valueType から」は、U1 の PickerSearchField に valueType が無いため果たせず、文字の入力にした（D22、差 X1）。
- 2026-10-29T23:12:48Z — U2 の引き継ぎ「503 は保存されていない」は、TIMEOUT の code が確定の時間切れと共有のため、TIMEOUT だけ既存の「保存されたか分からない」のままにした（D33、差 X4）。
- 2026-10-29T23:12:48Z — interaction-spec.md のボタンの名前「{項目名}を選ぶ」は、見える文字「選ぶ…」を含まず WCAG 2.5.3 に反するため、見える文字で始める名前にした（D17、差 X3）。
- 2026-10-29T23:12:48Z — traceability の確かめ（sensor-traceability）は、ui の単位に rules.md が無いため、OK の行の target に BR が無いことと rules.md が無いことで必ず失敗する。前の Intent の ui の単位（261016-business-data-views の U3）も今の道具で同じく失敗する。rules.md は作らず、target は functional-spec.md の D・F を指した（project.md の学び）。
- 2026-10-29T23:24:56Z — 承認の場の Request Changes（依頼者の決定）で直した: R-01 はラベルの1件読みを検索と別の写し parseReferenceLabel で受け page: null を通す（D18）、R-04 は U1 が searchFields・listColumns に valueType を載せる前提で入力を既存の作成・更新の画面にそろえ X1 を解消（D22・D23）、R-05 は用途で入力できないカラムを status より先に見分け権限の文言を出さない（D5・D32）、R-02 は 2ページ目以降の 0 件で Table を描かず「前のページへ」だけ（D26・pickerPaging）、R-03 は読み上げの扱いを NFR 設計で必ず決め行き止まりは文言で受け入れる（9節）。X4 は変えない。D・F の番号は変えていない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-29T23:12:48Z — ピッカーのダイアログは開くたびに条件と結果を空から始める（D21）。参照先の行の値を画面に持ち越さない代わりに、開き直すと条件を入れ直す手間が増える。
- 2026-10-29T23:12:48Z — E2E 190 は 180 と DSL を共有し、画面の流れの前の API の準備で適用されていなければ適用する（D39）。前のファイルの状態に頼らない代わりに、180 の DSL の見本を変える。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-29T23:12:48Z — U1 に PickerSearchField の valueType を足すか、U2 で保存前の確かめの時間切れを UNAVAILABLE に写すか（functional-spec.md 10節・13節）。
- 2026-10-29T23:12:48Z — FormField の誤りの文は role="alert" で、作成の画面を開いた直後の必須の参照項目の誤り（D13）が読み上げられる。許すかを NFR 設計で確かめる（9節）。
