<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T14:43:10Z — 配備・内部DB・接続プール・設定は変えず、足すのは移行1つ（見込み V10__u3_group.sql）とダッシュボードの区画だけと読んだ。CI と verify は既にある仕組みの記録として書いた（project.md の学び）
- 2026-10-06T14:43:10Z — traceability.json は NFR 設計の 32 の ID をすべて並べ、基盤（設定・移行・道具・監視・段）に当たるものを OK、コードの作りだけで決まるものを理由と確かめの段つきの N/A にした

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T14:43:10Z — 承認済みの observability-design.md 4節の「既存の p95 の警報がグループの API にも効く」は、mastersmith.yaml の p95 の警報3つが uri を1つの道に固定しているため事実と食い違った。文書は書き換えず、Q1: A（区画を足し警報は足さない）で monitoring-design.md 7節に差を記録した
- 2026-10-06T14:43:10Z — performance-design.md 2.1 はデータの用意を setup() と書くが、利用者を作る API が無いため、利用者だけは perf/README の SQL の手順を広げる形にした（cicd-pipeline.md 10節）

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T14:43:10Z — 警報を足さずダッシュボードの区画だけにした。鳴らす先の無い警報と要求の少ない管理の API の p95 の揺れを避ける代わりに、SLO の破れは起動して見るか k6 で測るまで分からない（SLO は Unverified）
- 2026-10-06T14:43:10Z — 区画の p95 はアプリの指標 http_server_requests のバケットで作る。招待の区画（トレースの traces_spanmetrics_latency）と元の指標が違うため、パネルの説明に書く

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T14:43:10Z — ダッシュボードの区画の式の名前（uri・le・service_name）は Observability Setup で起動して確かめる
- 2026-10-06T14:43:10Z — 配備の前の内部DB の複写と前の版での戻しの練習は、U4 の移行と合わせて deployment-pipeline で1回にまとめる
