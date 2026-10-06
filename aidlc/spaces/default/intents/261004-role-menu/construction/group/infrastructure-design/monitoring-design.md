# 監視の設計 — U3 group

この文書は、承認済みの `observability-design.md`（NFR 設計）を、手元の監視（`grafana/otel-lgtm` を compose の profile で見たいときだけ起動する形）の上でどう見るかを表にします。

- 独自の指標と新しい警報は足しません。
- 足すのは、ダッシュボードの区画を1つだけです（Q1: A）。区画を書いて式を確かめるのは Observability Setup で、B3 のコード生成は `docker/monitoring/` に触れません。
- 配備先が決まるまでは、警報の通知の先を作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/construction/group/` の下）:
  - `nfr-design/observability-design.md`（1〜5節）
  - `nfr-design/performance-design.md`（2節の k6 の場面）
  - `nfr-design/scalability-design.md`（2節の接続プール）
  - `nfr-design/reliability-design.md`（2.2 の `GROUP_BUSY`）
  - `nfr-requirements/observability-requirements.md`（NFR5.1〜NFR5.5）
- 既存のもの（正とする。読むだけ）:
  - `docker/monitoring/provisioning/alerting/mastersmith.yaml`（警報の決まり 16 件）
  - `docker/monitoring/dashboards/mastersmith-overview.json`
  - `backend/src/main/resources/application.yaml`（`management.metrics.distribution.slo` の `http.server.requests`: 100・250・500・1000・2000・5000 ms）

## 1. Metrics & KPIs

指標の名前は、手元の監視（Prometheus）での名前です。実際の名前・ラベル・`le` のバケットは、Observability Setup で起動して確かめます（`project.md` の学び）。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| グループの API の要求の数（`http_server_requests_milliseconds_count`、`uri=~"/api/admin/groups.*"`、`method`・`status` ごと） | Spring Boot の `http.server.requests`（既存）。`uri` は道の型（`/api/admin/groups`・`/api/admin/groups/{groupId}`・`/api/admin/groups/{groupId}/members`・`/api/admin/groups/{groupId}/members/{userId}`） | なし（見るだけ） | 使われ方と、409（`GROUP_BUSY`・`GROUP_NAME_DUPLICATE` など）・404 の数の傾向を見る |
| グループの API の p95（`histogram_quantile(0.95, …http_server_requests_milliseconds_bucket…)`） | 同上。`slo` のバケットがある | 目安の線 1000 ms（SLO の p95 1 秒） | SLO の基準の値を見る。判定は k6 で行う（3節） |
| グループの API の 5xx の数 | 同上（`status=~"5.."`） | なし（見るだけ。割合は既存の全体の警報が拾う） | 想定外の失敗（包み直した例外）がどの API で出たかを見る |
| 接続プールの待ちと時間切れ（`hikaricp_connections_pending`・`hikaricp_connections_timeout_total`・`hikaricp_connections_acquire`） | HikariCP（既存） | 既存の警報 `ms-pool-pending` のまま | 書き込みの2本使いで上限に届く既知の制約を見る（`scalability-design.md` 2節） |
| `GROUP_BUSY` の WARN の件数 | アプリのログ（Loki）。`RowLockFailures.warn` の `lockKind`（`GROUP_ROW`・`GROUP_NAME_KEY`・`GROUP_MEMBER_KEY`） | なし（見るだけ） | 待ちの上限切れが多いかを、ログの問い合わせで数える（`observability-design.md` 3節） |

## 2. Alerts

新しい警報は足しません。グループの失敗を拾う既存の警報と、拾わないものは次のとおりです。式は `mastersmith.yaml` で確かめました。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-5xx-ratio`（5xx の割合の増加） | `uri` で絞らない全体の 5xx の割合。グループの API の 5xx も含む | 既存のとおり | 手元の Grafana の画面だけ（通知の先は作らない） |
| `ms-error-logs`（ERROR のログの増加） | 想定外の失敗の ERROR（`@RestControllerAdvice`）がグループの API でも数えられる | 既存のとおり | 同上 |
| `ms-audit-fail`・`ms-audit-slow`（監査の書き込みの失敗・遅れ） | グループの監査の記録も既存の `AuditEventListener` を通るため含む | 既存のとおり | 同上 |
| `ms-pool-pending`（コネクションプールの待ち） | `max(hikaricp_connections_pending)`。グループの書き込みの2本使いで上限に届いたときも鳴りうる | 既存のとおり（式の見直しは配備先が決まるまでの申し送りのまま） | 同上 |
| `ms-forbidden`（管理画面への拒否の増加） | 全体の 403 の件数。グループの API の 403 も含む | 既存のとおり | 同上 |
| （拾わない）グループの API の p95 | 既存の p95 の警報（`ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`）は、どれも `uri` を1つの道に固定しており、グループの API を含まない | — | 足さない（Q1: A）。p95 は 3節の k6 で判定し、5節の区画で見る |
| （拾わない）`GROUP_BUSY`・業務の 409・404 | 業務の拒否と待ちの上限切れは警報にしない | — | 1節のログの問い合わせと 5節の区画で見る |

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| グループの管理の7つの API の応答時間（k6 の `iteration_duration{scenario:…}`、1回の繰り返しに要求1つ） | p95 < 1000 ms、`checks` の率 = 1（場面ごと） | Performance Validation の場面ごと（3 分、または 10 VU × 100 回）。判定は `Unverified` から始め、Performance Validation の値で判定する |
| 同じ API の応答時間（手元の監視の `http_server_requests` の p95） | 目安 1000 ms（判定に使わない） | 5 分の rate。手元の監視を常に動かしていない間は `Unverified` とし、配備の直後・監視の確かめ・負荷の試験・振り返りの時点の値を基準の値として並べる（`project.md` の学び） |
| 接続プールの合否の回（`groupPoolLimit`、上限 11・書き込み 5 VU・2 分） | 時間切れの累計 0、500 が 0 件、`hikaricp.connections.acquire` の最大 20 ms 未満 | Performance Validation の1回。上限 10・20 VU の回は記録だけ（`scalability-design.md` 2.2） |

- SLO の値は緩めません。測れない段では `Unverified` とし、持ち主の段（performance-validation・observability-setup）を明記して引き継ぎます。
- k6 の台本は B3 が書き、Performance Validation が使い捨ての環境で流します（`cicd-pipeline.md` 5節）。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| アプリのログ | 既存の構造化ログ（JSON、キーと値）。足すのは `GROUP_BUSY` の WARN だけ（キーは `lockKind`・`exceptionClass`・トレースID。名前・ID・例外の文・スタックトレースは出さない）。成功・業務の拒否・違反の読み替えは出さない（監査が記録する）。想定外の失敗は既存の ERROR で1回 |
| 漏えいの守り | `org.hibernate.orm.jdbc.error: OFF` を変えない（違反の文に値が入るため）。`GroupUniqueViolationSecretLeakIT`・`GroupSecretLeakIT`・`GroupBusyLogIT` が `verify` で毎回守る |
| ログの収集 | 変えない（手元の監視の profile を起動したときだけ、OTLP で lgtm へ送る。外部エクスポートは既定で無効） |
| トレース | 変えない。グループの API の要求にも既存のトレースIDが付き、WARN・ERROR に入る |
| 監査 | 内部DB の `audit_events`（既存の仕組み）。アプリのログで代用しない。種類は `GROUP_CREATED`・`GROUP_RENAMED`・`GROUP_DELETED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED` |

## 5. ダッシュボードの区画（Q1: A）

`docker/monitoring/dashboards/mastersmith-overview.json` に、この Intent の管理の API の区画を1つ足します。形は、既存の「招待と登録（Intent 260925-user-management の U3）」の区画と同じです。U4 role・U5 navigation も同じ区画に自分の `uri` を足します。持ち主は Observability Setup です。

| 区画・パネル | 中身（式の形。名前は起動して確かめてから書く） | 目安の線 |
|---|---|---|
| 区画の見出し | 「ロール・グループ・メニュー（Intent 261004-role-menu）」 | — |
| API ごとの要求の数（1 時間） | `sum by (method, uri, status) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/groups.*"}[1h]))` | なし |
| API ごとの時間（95 パーセンタイル） | `histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_milliseconds_bucket{service_name="mastersmith",uri=~"/api/admin/groups.*"}[5m])))` | 1000 ms（目安。判定は k6） |
| API ごとの 5xx の件数（1 時間） | `sum by (method, uri) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/groups.*",status=~"5.."}[1h])) or on() vector(0)` | なし |

Observability Setup での確かめ（`project.md` の学び）:

1. 起動して、`http_server_requests_milliseconds_bucket` に、グループの `uri` の系列で `+Inf` 以外の `le`（100〜5000）があることを確かめます。式が NaN を返すときは、要求が無いためと決めつけず、要求を送ってから流し直します。
2. グループの API に、指標の送信の周期（1 分）を複数またいで要求を送ります。系列の最初の1件は `increase` に数えられないためです。そのうえで、3つのパネルの式をすべて流して値を確かめます。
3. グループの操作の要求は監査に残るため、送る前に依頼者に伝えます。使い捨ての環境で行うなら、配備した内部DB を汚しません。
4. p95 のパネルは、アプリの指標 `http_server_requests` のバケットで作ります。既存の招待の区画が使うトレースの値（`traces_spanmetrics_latency`）とは、元の指標が違うことを、パネルの説明に書きます。

## 6. 運用の手順への引き継ぎ

- `GROUP_BUSY` の WARN が続くとき（同じグループへの操作の重なり）の見方を、README の「警報と対応の手順」に足すかは incident-response の段で決めます。この段では、見るもの（1節のログの問い合わせと 5節の区画）だけを書きます。

## 7. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `observability-design.md` 4節 | 「新しい警報とダッシュボードは足さない。既存の決まり（5xx の率・p95）が `uri` のラベルでグループの API にも効く」 | 5xx の割合は全体の式で効く（設計のとおり）。p95 の既存の警報は、どれも `uri` を1つの道（login・refresh・admin/check）に固定しており、グループの API を拾わない。警報は足さず、ダッシュボードの区画を1つ足す（5節） | 既存の警報の決まりを読み取りで確かめた事実との食い違い。依頼者の決定（Q1: A） |
| `observability-design.md` 4節の Observability Setup への引き継ぎ | 「グループの API の p95 1 秒を拾う既存の警報の式を名指しし、しきい値との関係を確かめる」 | 名指しする警報が無いため、引き継ぎを「5節の区画の式を起動して確かめる」に置き換える | 同上 |
