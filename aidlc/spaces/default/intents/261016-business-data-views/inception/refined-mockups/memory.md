<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-18T03:30:00Z — classic の範囲で Ideation のラフな画面イメージと利用者の流れが無いため、画面イメージはストーリーと要件から直接作った。書く前に、ストーリーの受け入れ基準 146 件を画面（S1〜S8）とサーバー側だけのものに振り分けて照らし合わせた（前の Intent の学び）。WCAG・確かめの表示・一覧の状態の持ち方（SM5）などはストーリーで決まっているとして質問にせず、画面の組み立ての8問にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-18T03:30:00Z — make-you-chic-ui の部品の口は報告ではなくソース（Table・Dropdown・TextInput・Modal・FormField）で確かめた。Table は並べ替え・ページ送り・文言の差し替えを持つが、1ページの件数の選択と横のスクロールの領域のフォーカス・ラベルを持たないため、依頼（5）の文面を `make-you-chic-ui-request-5.md` に置いた。Topbar の項目は、E2E 080 の違反の部品を axe-core 4.14 で確かめてから確定して送る。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-18T03:30:00Z — 一覧・詳細・作成・更新を別の画面にした（RQ1: A）。100 カラムまでの入力を Modal に詰めずに済む代わりに、画面と道が4つになり、一覧の状態を画面の中で保つ仕組み（SM5）が要る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-18T03:30:00Z — 行を指す主キーの値を道に置くか、主キーが列に無いときの代表のカラム、何も変えない保存の扱いは機能設計で決める。依頼（5）が間に合わないときに frontend の側で自前で作る形を続けるかは、Functional Design の承認の場で諮る。
