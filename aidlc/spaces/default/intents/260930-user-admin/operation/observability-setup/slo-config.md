# 仮の目標（slo-config）

前の Intent の記録（`aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/slo-config.md` と、それが正とする `260922-auth-audit-base` の記録）を正とします。仮の目標（稼働 99.5%・30 日など）は、配備先が決まったときに正式に決めます（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 1. 今回の差

- **SLO は決めません**（U3 の NFR5.11、決まっていること）。手元の監視を常に動かしていないため、運用の中の判定は `Unverified` とし、この段の確かめの時点の値を基準の値として並べます。目標を緩めて満たしたことにはしません。
- 7つの API の時間の目標（U3 の NFR5.1・NFR5.3〜NFR5.6: どれも p95 1000 ms、同時 10 件。409 `USER_ADMIN_BUSY` は約 3 秒の例外）の判定は、performance-validation の段が使い捨ての環境の k6 で行います（k6 の値を正、サーバー側の指標は参考）。
- 画面の時間（U5 の NFR5.1・NFR5.2）は配備したアプリでは測らず（`./gradlew e2eTest` の 120 の中でだけ記録）、運用の中の判定は `Unverified` のまま feedback-optimization に引き継ぎます。U4 の画面の時間には SLO を置きません。

## 2. 運用の中の判定と基準の値

基準の値は、使い捨ての環境（イメージは配備と同じ `mastersmith:local`、メモリの上限 1g、CPU 4、同時 1〜2 件の要求、2026-10-03 22:20〜22:28 JST）から手元の監視に送った値です（Q4: A。配備したアプリの値は、配備の記録 `operation/deployment-execution/health-check-report.md` の healthy と 200・UP を並べる）。負荷はかけていないため、目標の判定には使いません。

| SLI | 目標 | 判定 | 基準の値（この段） | 配備先が決まったときの測り方 | 持ち主 |
|---|---|---|---|---|---|
| GET `/api/admin/users` の p95 | 1000 ms 以内（NFR5.1） | Unverified | 95 ms（すべて `le="100"` の中。補間の値） | `http_server_requests_milliseconds_bucket` の `le="1000"` の割合（`dashboards.md` の N7）と p95（N2）を、配備先の監視で常時取る | performance-validation（k6）・feedback-optimization |
| PUT `/api/admin/users/{userId}/profile` の p95 | 1000 ms 以内（NFR5.3） | Unverified | 95 ms（同上） | 同上 | 同上 |
| 5つの操作のそれぞれの p95（409 BUSY を除かない） | 1000 ms 以内、目標の負荷で BUSY 0 件（NFR5.4〜NFR5.6） | Unverified | 5つとも 95 ms（同上）。BUSY 0 件（Loki の L3） | 同上。BUSY は L3 の件数 | 同上 |
| 7つの API の 5xx でない割合 | 配備先が決まったときに決める | Unverified | 1（7 分間の約 1,090 件で 5xx 0 件、N8） | N8 を 30 日の幅で取る | feedback-optimization |
| 監査の書き込みの失敗の件数 | 配備先が決まったときに決める | Unverified | 0 件（L6）。監査の行の件数は送った要求の見込みとすべて一致（`log-queries.md` 3節） | L6 と `ms-audit-fail` | performance-validation（鳴ること）・feedback-optimization |
| 接続プール | 時間切れの累計 0（U3 の NFR6.2） | Unverified | 待ちの最大 0、借りるまでの待ちの最大 0.708 ms、時間切れの累計 0、使用中の最大 0（指標の送信の時点の値）、上限 30 | `hikaricp_connections_timeout_total` の増加（`alarms.md` 2.1 の申し送り） | performance-validation |
| JVM のヒープの使用率 | `ms-heap` 0.85 | Unverified | 最大 0.243（上限 1g のアプリ） | 配備したアプリの上限（2g）で常時取る | feedback-optimization |
| 画面の時間（U5 の NFR5.1・NFR5.2） | 2 秒・1.5 秒（記録のみ） | Unverified | 配備したアプリでは測らない | 実際の利用者の画面の時間を測る仕組みを配備先で決める | feedback-optimization |

- `increase` と `histogram_quantile` の値は、Prometheus の補間を含みます。件数の正は監査の記録とログの件数です。
- 新しく現れた系列の最初の 1 件は `increase` に数えられないため、件数の少ない道（例: いない利用者の氏名の変更）は少なく見えることがあります（前の Intent の学び）。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/observability-setup-questions.md`（決まっていること、Q4: A、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/slo-config.md`
- U3 の `construction/u3-user-admin-api/infrastructure-design/monitoring-design.md`（3節）・`infrastructure-specification.md`、`construction/u3-user-admin-api/nfr-design/performance-design.md`・`reliability-design.md`・`security-design.md`、`construction/u3-user-admin-api/nfr-requirements/performance-requirements.md`・`observability-requirements.md`（NFR5.11）
- U4・U5 の `infrastructure-design/monitoring-design.md`（3節）・`infrastructure-specification.md`
- `operation/deployment-execution/health-check-report.md`・`deployment-log.md`
- この段の `dashboards.md`・`alarms.md`・`log-queries.md`
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`（基準の値の式）

## Assumptions & Open Questions

None.
