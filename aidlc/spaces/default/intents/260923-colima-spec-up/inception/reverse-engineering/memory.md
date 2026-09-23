<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T07:10:00Z — 今回の範囲に絞ったスキャンの対象を、コンテナ・JVM・負荷試験・README・接続プールの設定・LoginService とした; F3（メモリ）と F4（CPU とログイン）に直接関わる設定と手順だけを深く読み、ほかは流し読みの扱いにした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T07:10:00Z — 専門家への依頼文に規則の束を貼り付けず、束を1つのファイルにまとめてそのパスを渡した; 束が約 58KB と大きいため。内容は同じで、最初に読むよう指示した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T07:10:00Z — 全体の再スキャンではなく範囲を絞ったスキャンを依頼者が選んだ; 前回の深い範囲（監査・接続）は再確認できないため流し読みに下げ、記録上の範囲は狭くなった（NARROWER）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T07:10:00Z — VM の性能を上げるだけでは F3 が直らない見込み（compose の mem_limit 1g が固定、Dockerfile の MaxRAMPercentage=75）。mem_limit・JVM の設定・MASTERSMITH_CONTAINER_CPUS を変えるかを要件定義で決める。
