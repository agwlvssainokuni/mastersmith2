<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T08:25:00Z — .env の2行の変更は、値を表示せずにキーで行を置き換え・追加し、確かめは2つのキーの行と docker compose config の値だけを表示した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T08:25:00Z — 監査イベントの確認の複写を最初は mktemp -d（macOS の一時ディレクトリ）に置いたが、colima の VM から見えず空振りし、アプリを2回止めることになった; colima の VM とのファイルの受け渡しはホームの下の場所を使う。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T08:25:00Z — 配備した環境には負荷をかけず、性能は Build and Test の同じソース・同じ上限の結果を正とした（Deployment Pipeline の Q1: A）; 配備したデータと監査ログを汚さないため。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T08:25:00Z — 戻しの練習は行っていない（Q3: A）。第一の手・第二の手が動くかは次の配備で確かめる。
