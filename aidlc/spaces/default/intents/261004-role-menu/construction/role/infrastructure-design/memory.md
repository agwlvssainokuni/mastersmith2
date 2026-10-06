<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T15:00:45Z — 配備・接続プール・compose・依存は変えず、増えるのは移行2つ（見込み V11・V12）と application.yaml の2行（B6 の StatementCreatorUtils: OFF、B1 で足す共通の max-swallow-size）だけと読んだ
- 2026-10-06T15:00:45Z — traceability.json は NFR 設計の 45 の ID をすべて並べ、基盤（設定・移行・道具・監視・段）に当たる 29 件を OK、コードの作りと画面の単位の持ち物を理由つきの N/A にした

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T15:00:45Z — 承認済みの jqwik のテスト4つの名前（*Properties）は Gradle の test・integrationTest のどちらにも当たらず動かないため、*PropertyTest に直した（group の読み直しの R-01 の先取り）。差は cicd-pipeline.md 11節に記録した
- 2026-10-06T15:00:45Z — Q1: A で、Tomcat の max-swallow-size を DSL と共通の設定として B1 で足し、B6 で実際の Tomcat で 10 MiB の2つ目に 409 が届くことを確かめる形にした。B1 で入らなかったときは B6 で足して差を記録する

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T15:00:45Z — Q2: A で悪い側のデータを DSL の管理の API と role の import の API で入れる形にした。アプリの検証を通ったデータだけが入る代わりに、準備が B6 の import の口に頼り、照合の対象DB の要否は B4 の計画まで決まらない
- 2026-10-06T15:00:45Z — max-swallow-size を広げると、断る要求でもサーバーが最大 10 MiB を読み捨てる。ヒープは使わないが、帯域と時間はかかる

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T15:00:45Z — 照合の対象DB が DSL の準備に要るか（B4 の計画）
- 2026-10-06T15:00:45Z — dsl-v2 の承認の場で max-swallow-size の扱いが違う形に決まったときは、role の Q1 A とそろえ直す
- 2026-10-06T15:00:45Z — 手元の監視での hikaricp.connections.acquire の系列の名前と単位は Observability Setup で確かめる
