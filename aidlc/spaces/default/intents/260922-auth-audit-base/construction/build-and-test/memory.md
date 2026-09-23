<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
- 2026-09-23 — Test Strategy は Standard だが、単位が測れる性能・安全の要件（NFR1 系・NFR3 系）を持つため、ステージの `produces` のとおり performance / security の手順書も作った。Standard の既定（結合テストのみ）を超えるのは、要件が求めるためである。
- 2026-09-23 — 要件定義の FR/NFR は、Code Generation の traceability.json では BR と単位ごとの NFR の枝番に置き換わっている。そのため Step 10 の照合は、要件 → functional-design（FR→BR）／nfr-requirements（NFR→枝番）→ code-generation の2段の連鎖でたどった。
- 2026-09-23 — 性能の目標（応答時間のパーセンタイル、同時 10 件）は負荷の環境が要るため、`Unverified`（持ち主: Performance Validation）とした。ステージ定義の「後の段が明示的に持つときだけ deferred」に当たる。

## Deviations
- 2026-09-23 — `./gradlew verify` を実行したところ、テストのタスクが UP-TO-DATE で飛ばされ、実際の件数が得られなかった。`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測し直した。報告する数字は実測のみとする。

## Tradeoffs
- 2026-09-23 — 単位ごとの絞り込んだコマンドは、全体の関門（`./gradlew verify`）と重なる部分が多いが、単位ごとの合否とカバレッジを報告するために別に実行した。E2E と全体の回帰の重複コマンドは1回に畳んだ。

## Open questions
- 2026-09-23 — U1 の `unit-test-instructions.md` 2.4 節のコマンドは、単位に絞った実行に全体のカバレッジの判定（`jacocoTestCoverageVerification` とフロントエンドの全体しきい値）を含めているため、そのままでは必ず失敗する。U2〜U4 の同じ節は意図的に外している。U1 の手順書を直すかどうかは人の判断を仰ぐ。
- 2026-09-23 — `SecurityHeadersIT` の一過性の失敗（U4 の code-summary.md 6章）は本ステージの実行では再現しなかった。監視を続けるかどうかは人の判断。
