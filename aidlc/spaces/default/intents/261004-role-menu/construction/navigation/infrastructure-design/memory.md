<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T15:15:02Z — 読み取りだけの単位のため、基盤の変更は同梱するアイコンの一覧のファイル1つだけと読み、CI と verify は既にある仕組みの記録として書いた
- 2026-10-06T15:15:02Z — traceability.json は NFR 設計の 33 の ID をすべて並べ、基盤（同梱・段・道具・監視）に当たる 17 件を OK、コードの作りと他の単位の持ち物を理由つきの N/A にした

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T15:15:02Z — Q1: A で、承認済みの scalability-design.md 1節（SQL で直接入れる・目安の DSL は既定の生成）を、role の準備の台本に乗せる形（API と import、DSL は2つとも生成の部品で対象DB なし）に替えた。差は cicd-pipeline.md 10節
- 2026-10-06T15:15:02Z — 承認済みの observability-design.md 4節の式の名前 http_server_requests_seconds_bucket は、手元の既存の式の _milliseconds_ と食い違うため、monitoring-design.md 7節に差を書き Observability Setup で確かめる形にした

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T15:15:02Z — k6 の場面を role と同じ環境で続けて流すため、流す順（準備 → 読み取りの場面 → navMenuBaseline → roleTransferLarge）を決めた。順を守れないときは準備のやり直しが要る

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T15:15:02Z — 区画の式の実際の名前（_milliseconds_ の付き方と uri の値）は Observability Setup で確かめる
