<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
- 2026-09-25T00:14:53Z — colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
- 2026-09-25T00:14:53Z — unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。
- 2026-09-24T23:37:35Z — 計画の Step ごとのコミットの提案は、生成の担当ではなく、生成の後に依頼者の承認を得てまとめて C1〜C6 に分けて行う形にした。担当は依頼者に直接尋ねられないため。統合は計画どおり短命のブランチ fix/260924-followup-fixes から fast-forward（team.md の squash とは違う。サブモジュールの専用のコミットを残すため、計画の承認で受け入れられた）。
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
- 2026-09-25T00:14:53Z — .env.targetdb の形の値は Gitleaks の既定の規則でも .gitleaks.toml でも検出されなかった。規則を足すかは依頼者の判断。perf/README.md と scenarios.js 30 行の「1g で流す」の記述を直すか、project.md の「先に1人ずつログイン」の学びを扱うかも未決。
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
