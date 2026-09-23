<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23 — この段の `ci-config.md`・`quality-gates.md` は、新しい CI を作る文書ではなく、U1 の Code Generation で既に作られた `.github/workflows/ci.yml` と `./gradlew verify` の**記録**として書いた（確認済みの要約 `ci-pipeline-questions.md` 5節の合意）。
- 2026-09-23 — 「CI の関門が Build and Test のコマンドを実行しているか」の確認は、CI の YAML に検査が書かれていないため、`test-results.md` 1節の C1〜C12 と `verify` の段との対応で判断した。検査の中身が Gradle の1か所にあるため、対応は 1 対 1 で取れた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23 — `ci-pipeline-questions.md` 3節の表は 9 の段の失敗の基準に「上限（500KB）超過」を含めているが、実装（`check-bundle-size.mjs`）と承認済みの設計はどちらも「量の超過は失敗にしない」。要約ではなく実装と設計に合わせて記録し、差を `ci-config.md` 8節・`quality-gates.md` 8節に明記した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23 — U3 の `traceability.json` に残る `OPEN` の行は、実体が解決済みであることを4つの証拠で確かめたうえで、確定済みの成果物を書き換えずに「記録の食い違い」として `phase-check-construction.md` に明記する形にした。境界の判定は合格としつつ、食い違いは隠さない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23 — `Unverified` の U1-NFR1.9（内部DBのファイルの増え方）は、表の `Owning Stage` が `performance-validation`、集計の分類が「運用の見積もり」で食い違う。どちらの段が測るかは `performance-validation` の入口で確定させる。
- 2026-09-23 — CI が実際に緑であることは、依頼者が `origin` へプッシュした後でなければ確認できない（AI はプッシュしない）。本段では YAML と `verify` の対応までを確認した。
