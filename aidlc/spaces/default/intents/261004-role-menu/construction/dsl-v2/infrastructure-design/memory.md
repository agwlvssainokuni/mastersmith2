<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T14:31:11Z — JSON Schema の v2 への替えで名指しを直す箇所を、コードの検索で6つ（build.gradle.kts の2か所・DslFormat・DslSchemaPublicationIT・画面の2ファイル・README）に絞って一覧にした
- 2026-10-06T14:31:11Z — traceability.json は NFR 設計の 25 の ID をすべて並べ、検査・ビルド・道具・測る段に当たるものを OK、コードの作りだけで決まるものを N/A にした

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T14:31:11Z — Q1: A で、本番と同じヒープでの同時の投入の確かめを、B1 が perf/dsl-timing.sh に足す口で行う形にした。承認済みの設計は持ち主（Build and Test）だけを決めていたため、差を cicd-pipeline.md 12節に書いた

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T14:31:11Z — 同時の投入は重なり方が要求の届く順で変わるため、成功1つと 503 DSL_BUSY 1つの組で判定し、両方が通った回は数えずにやり直す形にした。確実に重ねる形（テストの部品での待ち合わせ）は DslConcurrencyIT に任せ、本番のコンテナではアプリが止まらないことだけを見る

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T14:31:11Z — 同時の投入の口のオプションの名前とやり直しの回数は Code Generation で決める
- 2026-10-06T14:31:11Z — colima の VM 6GiB で使い捨ての環境と配備したアプリを同時に動かすか止めるかは、Build and Test の手順で決める
