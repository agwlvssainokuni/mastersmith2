<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-15T00:00:00Z — 段の条件（新しい部品が要るとき）について、内部DB の種類を判断する部品が新しく要るため行うと判断した。既存のアプリの全体ではなく、新しく作る部品と手を入れる部品だけを components.md に載せた（全体は codekb の component-inventory.md を正とする）。
- 2026-11-15T00:00:00Z — classic の範囲に Feasibility が無いため、実現できるかの判断（単一のインスタンス・直接の接続・確かめた版だけ）を ADR-006 として扱った（project.md の Way of Working）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-15T00:00:00Z — 文書の変更（FR4.4・FR4.5）は部品を持たないため、traceability.json で units-generation への Deferred にした。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-15T00:00:00Z — 移行を種類ごとに一式持つ形（Q1: A）は H2 に触れずに済む代わりに、表の形の定義が2つになりずれうる。普段の verify で版の番号の一致を確かめる手立ては機能設計で決める。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-15T00:00:00Z — 標準の MERGE の同時の実行の振る舞い、@Lob byte[] と bytea、V8 の生成列、lock_timeout と移行の関係は、機能設計・NFR 設計の前に捨ての試しで確かめる。
