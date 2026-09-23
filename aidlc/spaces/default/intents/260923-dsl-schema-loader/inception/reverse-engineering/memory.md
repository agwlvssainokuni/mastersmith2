<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T09:03:28Z — 依頼者は全体の読み直し（Full rescan）を選んだが、記録上の範囲は実際に深く読んだものだけ（kind: partial、77パス・17部品）とした。コンテナ・負荷試験・監視の設定と README は今回の Intent に関わりが薄く流し読みにしたため、前回（colima-spec-up）より範囲が狭い（NARROWER）と判定された。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T09:03:28Z — アーキテクトが開発者のスキャンにあったパッケージ間の依存を import の検索で確かめ直し、3点（audit は common.observability に依存しない、access は config に依存する、auth は common.observability に依存する）を訂正して記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T09:03:28Z — 部品の名前を英数字の ID（auth・frontend-app-core など）にし、Scope of Analysis の components と component-inventory.md の見出しを文字どおり一致させた。照合は安定するが、前回の日本語・クラス名の部品名とは一致しなくなり、比較で部品が「失われた」と表示される。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
