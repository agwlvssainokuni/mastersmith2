<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
- 2026-11-15T00:00:00Z — 単位の間に実行時の通信が無いため、約束を設定・表の形・テストの土台・repository の口・イメージの固定先の5種類と、変えない外部の API の1つで書いた。時間の上限や再試行の約束は持たない（起動の失敗で止める）。
- 2026-11-15T00:00:00Z — Q3: A で内部DB の結合テストは TargetDbImages を共有するため、team.md の「内部DB 用の固定先（テストの定数・compose）を一覧に足す」は、足すものが compose のサービスだけになると読んだ（食い違いではない）。
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
- 2026-11-15T00:00:00Z — ADR-005 は結合テストの明示の指定の名前を機能設計・NFR 設計に回していたが、U3 が U1 の土台を使う約束（C4）として、タスクの名前・タグ・注釈の名前をこの段で決めた（依頼者の答え Q1: A・Q2: A）。置き場のパッケージの名前は U1 の機能設計に残した。
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
