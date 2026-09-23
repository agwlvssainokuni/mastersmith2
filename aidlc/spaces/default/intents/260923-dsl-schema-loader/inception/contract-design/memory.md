<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T11:51:28Z — 外部に公開する API が無いため、契約は依存の6本に、画面との HTTP（C6）・監査への出来事（C7）・後続の Intent への提供口（C8）を加えた8本とした。後続の Intent への提供口は、外部の利用者として扱った。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T11:51:28Z — 既存のエラー応答の仕組み（ProblemType は code と状態コードを1つに固定、GlobalExceptionHandler は BusinessException から応答を作る）を十分に確かめずに契約を書き、同じ code を 404 と 409 の2つで使う形と、結果の型から応答への変換の経路が決まっていない形になった。レビューで指摘された。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T11:51:28Z — アプリの中の部品の間では、想定内の失敗を結果の型で返す形（Q5: A）を選んだ。場合分けの漏れを型で防げるが、既存の例外から応答を作る仕組みとの橋渡し（U4 の中の変換）が要る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
