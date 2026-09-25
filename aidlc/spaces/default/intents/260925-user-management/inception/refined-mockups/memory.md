<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T13:53:55Z — classic の範囲で Ideation のラフな画面イメージと利用者の流れが無いため、画面イメージはストーリーと要件から直接作る。WCAG 2.1 AA は前の Intent と同じとして質問にせず、ストーリーの段から回された画面の論点（異論の食い違い・言語の切り替えの置き場・パスワードの上限の伝え方）と画面の組み立てを7問にした。
- 2026-09-25T14:07:37Z — S2 のログインの画面へのメールアドレスの持ち越しと、S4 の未保存の確かめを出さないことは、要件・ストーリーに無い細部として [assumption] にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T14:07:37Z — 画面イメージを書く前に、統合で増えたストーリーの受け入れ基準（84 件）を1件ずつ突き合わせず、AC1.1.8（成功の後もフォームが開いたまま）・AC1.1.4（案内から一覧の行へ移れる）・AC3.2.16（autocomplete="username"）と食い違い・抜けが出て、レビューで指摘された。画面の段では、書く前に対象の受け入れ基準を画面ごとに一覧にして照らし合わせる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T14:07:37Z — Q1 B（入力は Modal）と AC1.1.8（成功の後もフォームに残る）が食い違う。Modal のまま成功で閉じるか、成功の後も Modal を開いたまま次の招待に備えるかは、承認の場で依頼者が決める。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
