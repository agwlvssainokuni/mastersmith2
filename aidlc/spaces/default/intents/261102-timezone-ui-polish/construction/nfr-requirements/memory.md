<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T00:00:00Z — U1 の NFR 要件の「単位の開始の記録」（`unit start`）を、書き終えた後に行った（最初に行うべきところを忘れ、完了の記録が「動いている単位が無い」と断られた）。開始の記録の後に完了を記録し直した。次の段・単位は、本文の前に開始を記録する。
- 2026-11-03T00:00:00Z — U1 のレビュー（READY）の Minor 5件は、その場で直さず承認の場へ持ち越した: 追加の要件 NFR1.4.x・NFR1.5.x とブラウザの対象の ID の扱い、壁時計の時間を合否にするテストの扱い（NFR2.1.2 は NFR2.1.1 の割り算）、NFR5.2.1 のロケールの確かめ方、長さの上限の数の食い違い（26 文字と 35 文字）、`Intl` が使えないときの U1 の返し方の確定。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T13:45:00Z [unit u5-menu-icons-home] — 質問は作らなかった（機能設計と前の Intent の目標で、性能・セキュリティ・技術の選択がすべて決まっているため）。メニューの API の目標は前の Intent の p95 1 秒を変えず、画面の描画は時間でなく「訪問する項目の数 ≦ 項目の数」で決定的に確かめる形にした。
- 2026-11-03T13:45:00Z [unit u5-menu-icons-home] — 機能設計のレビュー役の指摘（D24: 未ログインは見つからないでなくログイン画面へ）は、機能設計を書き換えず、セキュリティの要件の「上流との差」に実際の動きに合わせて記録した。
- 2026-11-03T13:45:00Z [unit u5-menu-icons-home] — 本文を書く前の `unit start` を、またこの単位の機能設計で忘れ、NFR Requirements でも本文の後になった（上の U1 の記録と同じ失敗）。次の段からは、本文の前に開始を記録する。
- 2026-11-03T00:00:00Z — 変換にライブラリを足さず `Intl` だけにした（Q1: A）。依存・ライセンス・脆弱性検査の対象が増えない代わりに、ブラウザごとの IANA の版の違いを自分たちで確かめる必要が残る（NFR Design の捨ての試し）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
