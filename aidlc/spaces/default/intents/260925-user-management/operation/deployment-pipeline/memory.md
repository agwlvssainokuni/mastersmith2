<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-29T00:30:00Z — 質問 Q4 の C（プリファレンスの保存）に「監査が残る」と書いたが、AuditEventType にプリファレンスの保存の種類は無く、監査には残らなかった。deployment-strategy.md の S8 に誤りを明記した。確かめの項目に監査の種類を書く前に、AuditEventType の定義で確かめる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-29T00:35:00Z — 配備の前の k6 は、Build and Test で負荷をかけていないが、依頼者の決定（Q2: B）で流さず performance-validation に任せた。project.md の「試験済みのときだけ省く」との差を cd-config.md に記録し、問題が出たら第一の手で戻す。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-28T17:00:00Z — Observability Setup への引き継ぎ（依頼者の決定「a, observability setupで」）: Grafana の左メニューに Explore が出ないのは匿名の Viewer の役割のためと見立て、lgtm の環境変数に GF_USERS_VIEWERS_CAN_EDIT: "true" を足して Explore を使えるようにする（案 A）。ログを Grafana へ流す .env の2行（MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED・_ENDPOINT）も、この Intent の版を配備した後に Observability Setup で扱う。見立ては Grafana の中では未確認。
