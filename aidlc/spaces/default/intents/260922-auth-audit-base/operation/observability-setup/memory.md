<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T04:00:00Z — ステージの手順は CloudWatch・X-Ray を前提にしているが、配備先が PC 上のコンテナのため、依頼者の決定で grafana/otel-lgtm（Prometheus・Loki・Tempo・Grafana）に読み替えた。ダッシュボードと警報はファイルでリポジトリに置き、画面からは変えられないようにした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T04:00:00Z — 承認済みの監視の設計の「当面は常時の監視の仕組みを持たない」を、依頼者の決定（Q1）で変え、見たいときだけ起動する手元の監視を作った。設計にあったヘルスの DOWN・起動の時間・ボリュームの使用量の警報は、指標として送られないため作らず、代わりの方法を alarms.md に書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T04:00:00Z — 警報の式を書く前に、実際に起動して Prometheus と Loki の名前とラベルを確かめた（uri・status・area、Loki にキーと値が入らないこと、フィルターで返した 401 が uri=UNKNOWN になること）。設計の指標名のままでは動かない式を避けられた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T04:00:00Z — 監視のコンテナが上限 900MiB のうち約 810MiB を使い、VM の空きも約 900MB と余裕が少ない。長く動かすと止まるおそれがあり、そのときは colima のメモリを増やす。
- 2026-09-23T04:00:00Z — 本段の確認で送った未ログインの /api/admin/check 20 件が監査ログに ACCESS_DENIED として残った。監査ログに残る要求を確認に使うときは、事前に依頼者へ伝えるべきだった。
