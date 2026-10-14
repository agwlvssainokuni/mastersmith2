# 仮の目標（slo-config）

前の Intent の記録（`aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/slo-config.md` と、それが正とする前々回までの記録）を正とします。仮の目標（稼働 99.5%・30 日など）は、配備先が決まったときに正式に決めます（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 1. 今回の差

- この Intent の承認済みの SLO は次の2つで、どちらも**値は緩めません**。
  - 新しい API の応答時間: p95 1 秒（U3〜U5）
  - 権限の YAML の確かめ・適用: 15 秒・30 秒（U4 の NFR2.4）
- 判定は performance-validation の段が使い捨ての環境の k6 で行います（k6 の `iteration_duration` の値を正、サーバー側の指標は参考。group・role・navigation の `monitoring-design.md` 3節）。
- 手元の監視を常に動かしていないため、運用の中の判定は `Unverified` とし、この段の確かめの時点の値を基準の値として並べます（`project.md` の Deployment の学び）。
- ダッシュボードの「仮の目標の一覧」に2行を足しました（Q6: A、`dashboards.md` 1節）。区画の p95 のパネルの線 1000 ms は、この目標の目安です。

## 2. 運用の中の判定と基準の値

基準の値は、使い捨ての環境から手元の監視に送った値です。条件は次のとおりで、負荷はかけていないため、目標の判定には使いません。
- イメージは配備と同じ `mastersmith:local`、メモリの上限 1g、CPU 4
- 同時 1 件の要求
- 2026-10-15 01:30〜01:36 JST

配備したアプリの値は、配備の記録 `operation/deployment-execution/health-check-report.md`（healthy、200・UP、使用 436 MiB／上限 2g）を並べます。

| SLI | 目標（窓） | 判定 | 基準の値（この段） | 配備先が決まったときの測り方 | 持ち主 |
|---|---|---|---|---|---|
| グループの管理の7つの API の p95 | 1000 ms 以内（5 分の rate。k6 は場面ごと 3 分） | Unverified | 7つとも 95 ms（すべて `le="100"` の中。補間の値）。最大 19.9 ms（一覧） | 区画の p95（パネル 37）と 1000 ms 以内の割合（`dashboards.md` の R4）を、配備先の監視で常時取る | performance-validation（k6）・feedback-optimization |
| ロールの管理・権限の木と保存・割り当ての API の p95 | 1000 ms 以内（同上） | Unverified | 要求のあった道はすべて 95 ms。最大は権限の保存の 28.5 ms | 同上 | 同上 |
| 作業ロールの読み取りと切り替え、自分の権限の木の p95 | 1000 ms 以内（同上） | Unverified | 95 ms（同上） | 同上 | 同上 |
| 業務のメニュー・置き場の p95 | 1000 ms 以内（同上） | Unverified | 95 ms（同上。置き場の 403・400 を含む） | 同上 | 同上 |
| 権限の YAML の確かめ・適用 | 確かめ 15 秒以内・適用 30 秒以内（`iteration_duration` の最大、上限ちょうどの YAML で1回） | Unverified | 小さな YAML（252 バイト）で確かめの最大 14.1 ms。上限ちょうどの値は Build and Test の先の測り（BT-Q1c: 5 MiB の正しい書き出しで確かめ 1.11 s・適用 2.35 s） | 5000 ms を超えるとバケットの外（`+Inf`）のため、パネルでは見ない。k6 か、`http_server_requests_max_milliseconds` の道ごとの最大で見る | performance-validation（`roleTransferLarge*`） |
| 区画の API の 5xx でない割合 | 配備先が決まったときに決める（全体の仮の目標は 99.5%・30 日） | Unverified | 1（約 5 分間の 515 件で 5xx 0 件。パネル 38 は 0） | 区画の 5xx の件数と全体の稼働のパネルを 30 日の幅で取る | feedback-optimization |
| 監査の書き込みの失敗の件数（この Intent の種類） | 配備先が決まったときに決める | Unverified | 0 件（L8）。監査の行の件数は送った要求の見込みとすべて一致（`log-queries.md` 5節） | L8 と `ms-audit-fail` | performance-validation（鳴ること）・feedback-optimization |
| 接続プール | 時間切れの累計 0（group・role の合否の回、`*PoolLimit`） | Unverified | 待ちの最大 0・時間切れの累計 0・上限 30（`dashboards.md` の R6） | `hikaricp_connections_timeout_total` の増加（`alarms.md` 2.2 の申し送り） | performance-validation |
| JVM のヒープの使用率 | `ms-heap` 0.85（5 分） | Unverified | 最大 0.280（上限 1g のアプリ、直近 15 分） | 配備したアプリの上限（2g）で常時取る。尽きて止まったときは `ms-app-absent`（`alarms.md` 2.1） | feedback-optimization |
| 画面の時間（U6・U7） | 承認済みの目標（5 回の中央値） | Unverified（運用の中） | 配備したアプリでは測らない（E2E の 140・160 の測りで記録） | 実際の利用者の画面の時間を測る仕組みを配備先で決める | feedback-optimization |

- `increase` と `histogram_quantile` の値は、Prometheus の補間を含みます。件数の正は監査の記録とログの件数です。
- 新しく現れた系列の最初の 1 件は `increase` に数えられません。1回だけ送った権限の YAML の3本は、区画の要求の数で 0 に見えます（`dashboards.md` 2節）。
- 区画の p95 のパネルは、要求の無い 5 分には NaN になります。NaN を「要求が無いため」と決めつけず、R1（系列の数）と要求の数のパネルで要求の有無を確かめます（`project.md` の学び）。

## 3. 持ち越しの Unverified の判定（Build and Test から）

| ID | 目標 | この段の確かめ | 判定 |
|---|---|---|---|
| U3-NFR5.4 | 「警報は既存の決まり（5xx の率・p95）が `uri` のラベルでグループの API にも効く。新しい警報とダッシュボードは足さない」 | 5xx の率は、グループの API の要求が `ms-5xx-ratio` の分母に入ることを確かめた（R3: 区画の要求が全体の 84.5%）。p95 は、既存の p95 の警報3件が `uri` を1つの道に固定しており、グループの API に効かない（承認済みの基盤の設計の差）。代わりに区画の p95 のパネルで見る形にした | **Met（差つき）**。5xx の部分は Met。p95 の部分とダッシュボードを足したことは、基盤の設計 group の `monitoring-design.md` 7節の差（Q1: A）のとおり置き換えた。`ms-5xx-ratio` が鳴ることは `Unverified`（`alarms.md` 2節） |
| U4-NFR5.4 | 「新しい警報は足さない。既存の『5xx の割合の増加』と『コネクションプールの待ち』は、role の API と作業ロールの切り替えにも `uri` によらず効く」 | 警報を足していない。role の API と作業ロールの切り替えの要求が `ms-5xx-ratio` の分母に入ることを確かめた（R3）。`ms-pool-pending` の式はプール全体の値（`uri` のラベルを持たない）で、role の書き込みの接続も含む | **Met**。`ms-pool-pending` が鳴ることは performance-validation の `rolePoolLimit`・`groupPoolLimit` に持ち主を移した。`ms-5xx-ratio` が鳴ることは `Unverified`（`alarms.md` 2節） |

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/observability-setup-questions.md`（Q3: A・Q6: A、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/slo-config.md`
- `construction/build-and-test/build-and-test-summary.md`（U3-NFR5.4・U4-NFR5.4、U4-NFR2.4 の BT-Q1c）
- 基盤の設計: `construction/group/infrastructure-design/monitoring-design.md`（3・7節）・`infrastructure-specification.md`、`construction/role/infrastructure-design/monitoring-design.md`（3・7節）・`infrastructure-specification.md`、`construction/navigation/infrastructure-design/monitoring-design.md`（3節）・`infrastructure-specification.md`、`construction/role-admin-ui/infrastructure-design/monitoring-design.md`・`construction/app-frame-ui/infrastructure-design/monitoring-design.md`（5節）
- NFR の設計: `construction/group/nfr-design/performance-design.md`・`reliability-design.md`・`security-design.md`・`observability-design.md`、`construction/role/nfr-design/`・`construction/navigation/nfr-design/` の同じ文書、NFR 要件 `construction/group/nfr-requirements/observability-requirements.md`・`construction/role/nfr-requirements/observability-requirements.md`・`performance-requirements.md`
- `operation/deployment-execution/health-check-report.md`
- この段の `dashboards.md`・`alarms.md`・`log-queries.md`
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`（基準の値の式）

## Assumptions & Open Questions

- U3-NFR5.4 を「Met（差つき）」とする判定は、承認の場で確かめます（目標の文言の p95 の部分は、承認済みの基盤の設計の差で置き換えたもので、目標を緩めたものではない）。
