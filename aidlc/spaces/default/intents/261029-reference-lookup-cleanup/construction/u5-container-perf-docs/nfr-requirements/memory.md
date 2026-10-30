<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-10-30T00:00:00Z — packaging の単位のため functional-spec.md・rules.md は無く、要件 FR6・FR7・NFR5・NFR8・NFR9 と US4.2・US4.3 を直接の出典にした。セキュリティは NFR8.81〜.90、供給網は NFR9.81〜.82、統合の前の検査は NFR5.81 に寄せた。
- 2026-10-30T00:00:00Z — `k6 inspect --include-system-env-vars` はシェルの環境変数を出力に含むため、資格情報を k6 に渡さず、出力は外に置き記録には差の有無だけを書く要件（NFR8.87）にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-30T00:00:00Z — 依頼の文の `perf/thresholds-test.sh` は実際には `perf/k6/thresholds-test.sh` にあり、`./gradlew verify` に入っていないため、整理の後に手で流して記録する要件（NFR8.88）にした。
- 2026-10-30T00:30:00Z — 承認の場の Request Changes（依頼者の決定: 指摘ごとの提案どおりに直す）で、k6 inspect の流し方を K6_IMAGE の docker run（--env-file なし）に固定し閾値と場面の定義だけを比べる形（NFR8.87）、dsl-mixed-memory.sh と README の線の値の前後の差（NFR8.88）、作り直しの前後の docker image ls（NFR8.83）、入れ替えの前のコンテナの確かめと停止・lgtm のボリュームの引き継ぎの確かめと 0.34.0 への戻し（NFR8.82）、README の整理の後の検索（NFR8.89）、アプリが止まっていたときは起動して確かめる既定（NFR8.84）を足した。ID は増やしていない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
