<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-29T13:30:00Z — 依頼者は Focused scan を選んだ（古い store のため再利用の選択肢は出さず）。範囲は REFERENCE/LOOKUP・businessdata・dsl・targetdb・role・features/tables・shared・perf・docker/perf・compose.yaml・.github・依存の版・README に絞った。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-29T13:30:00Z — 最初のスナップショット（16パス）のパスは末尾の / 無しで、候補の analyzed.paths（末尾 / 有り・docker/perf/ に絞った）を覆わず公開が拒否されたため、候補の paths で撮り直して公開した。範囲の比較は NARROWER（gradle/libs.versions.toml が浅い扱いに降格）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-29T13:30:00Z — 規則の束は各依頼に貼らず、規則ファイルのパスを読ませる形にした（束が非常に大きいため）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-29T13:30:00Z — 未検証: 稼働中の Mailpit のダイジェストが compose.yaml と一致するか、k6 の handleSummary で --summary-export を上書きできるか、JWT が k6 のログにも出るか、i18next #81・#82 のリリースノート。要件定義で確かめる。
