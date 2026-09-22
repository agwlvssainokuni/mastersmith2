<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T12:50:00Z — 依頼者の変更の依頼により、ボリュームの使用量のしきい値を、U1 の NFR1.9 の 180MB を根拠とし索引を含む U4 の見積もり（250MB 程度）を併記したうえで、ボリューム全体で 1年あたり 500MB とした。
- 2026-09-22T12:20:00Z — 当面の配備先が開発者の PC 上のコンテナだけのため、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らず、配備先が決まったときに置き換える前提で設計した; チームの進め方の Deployment に従うため。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-22T12:20:00Z — 1コマンドの検査を Gradle の1つのタスクにし（Q4）、npm と外部の道具も Gradle から PC の実行ファイルを呼ぶ形にした; Node.js を Gradle で自動で入れる仕組みは使わず、版の確認で失敗させる方を選んだ（道具を増やさないため）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-22T12:20:00Z — ヘルスチェックの起動の猶予 40 秒・コンテナの資源（メモリ 1GB・CPU 2）は見積もりで、Performance Validation で見直す。
