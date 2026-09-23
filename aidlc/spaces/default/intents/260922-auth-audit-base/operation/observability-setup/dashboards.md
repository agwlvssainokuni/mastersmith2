# ダッシュボード（dashboards）

Intent `260922-auth-audit-base`（auth-audit-foundation）の手元の監視のダッシュボード。入力は、U1〜U4 の `construction/<単位>/infrastructure-design/monitoring-design.md`（monitoring-design）と `infrastructure-specification.md`（infrastructure-specification）、`construction/<単位>/nfr-design/performance-design.md`（performance-design）・`security-design.md`（security-design）・`reliability-design.md`（reliability-design）である。決定は `observability-setup-questions.md`（Q1〜Q7）にある。

## 1. 置き場所と見方

| 項目 | 値 |
|---|---|
| 仕組み | `grafana/otel-lgtm:0.33.1`（Grafana 13.2.1・Prometheus 3.14.0・Loki 3.7.7・Tempo 3.0.3・OpenTelemetry Collector 0.160.0）。`compose.yaml` のサービス `lgtm`、profile `monitoring`（Q1・Q4・Q7） |
| 画面 | `http://localhost:3000/`（`127.0.0.1` にだけ結び付け）→ ダッシュボード「MasterSmith」フォルダーの「MasterSmith の概要」（uid `mastersmith-overview`） |
| 定義のファイル | `docker/monitoring/dashboards/mastersmith-overview.json`（読み込みの設定は `docker/monitoring/provisioning/dashboards/mastersmith.yaml`）。画面からは変えられない（`allowUiUpdates: false`） |
| 見る権限 | ログインなしの閲覧だけ（`GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer`、ログインの画面は出さない） |
| 起動と停止の手順 | README の「手元の監視（Grafana）」 |

承認済みの設計（U1 の `monitoring-design.md` 4章）は「ダッシュボードは持たない。配備先が決まったら Observability Setup で稼働・エラー・応答時間・資源の4つの面を作る」としていた。依頼者の決定（Q1）により、配備先が決まる前に手元に作った。

## 2. 面と表示（22 のパネル）

| 行 | パネル | 問い合わせ（要点） | 線（しきい値） | 由来 |
|---|---|---|---|---|
| 仮の目標 | 稼働（30 日） | `1 - 5xx の件数 / 全件数`（`http_server_requests_milliseconds_count`） | 99.5% | U1 の monitoring-design 3章 |
| 仮の目標 | 仮の目標の一覧（文字） | — | — | `slo-config.md` |
| 稼働とエラー | 要求の数（1 分あたり） | `rate(..._count[5m]) * 60` | — | U1 の monitoring-design 1章 |
| 稼働とエラー | 5xx の割合（5 分） | 5xx ÷ 全件 | 1% | 同上（NFR10.9） |
| 稼働とエラー | ERROR のログ（5 分） | `increase(logback_events_total{level="error"}[5m])` | 5 件 | 同上 |
| 応答時間 | ログインの API（95 パーセンタイル） | `histogram_quantile(0.95, ..._bucket{uri="/api/auth/login"})` | 1000 ms | U2 の NFR1.1 |
| 応答時間 | トークンの更新の API | 同上（`/api/auth/session/refresh`） | 1000 ms | U2 の NFR1.2 |
| 応答時間 | 確認用 API | 同上（`/api/admin/check`） | 300 ms | U3 の NFR1.1 |
| 資源 | コネクションプールの待ち | `max(hikaricp_connections_pending)` | 0 超 | U1 の monitoring-design 1章 |
| 資源 | JVM のヒープの使用率 | `jvm_memory_used_bytes{area="heap"} ÷ jvm_memory_max_bytes{area="heap"}` | 85% | 同上 |
| 資源 | 403 の件数（1 時間） | `increase(..._count{status="403"}[1h])` | 20 件 | U3 の monitoring-design 1章 |
| 認証・拒否・監査 | 監査の書き込みの失敗・アカウントのロック・正規化されていないパスの拒否・送り元の不一致の拒否・監査の書き込みの遅れ | Loki で、アプリのログの決まった文言を数える（`log-queries.md` 2節） | 各 `alarms.md` のしきい値 | U2〜U4 の monitoring-design |
| 認証・拒否・監査 | 監査ログから数えるもの（文字） | ログインの失敗の割合・拒否の件数は監査ログ（内部DB）から数える | — | U2-NFR10.7、U3-NFR10.5 |

すべての問い合わせを、手元で Grafana を通して実行し、エラーが無いことを確かめた（12:55）。出来事がまだ無い面（ロック・監査の失敗など）は「データなし」になる。

## 3. 限界と注意

- **フィルターの段階で返した 401・403 は `uri="UNKNOWN"` として数えられる**（実測: 未ログインの `/api/admin/check` 20 件が `status="401", uri="UNKNOWN"`）。そのため「確認用 API」の応答時間は、アプリの処理まで届いた要求（管理者の成功）だけを表す。U3 の SLI の「成功・403 とも」は、この面では測れない。Performance Validation で負荷の試験の側から測る。
- 403 の件数は状態コードだけで数えるため、管理画面以外の 403（送り元の不一致など）も含む。
- ログ（Loki）には、ロガーの名前・文言・レベル・トレースIDが入り、キーと値の項目（`code` など）は入らない。そのため、ログの面は文言で数える。文言を変えたときは、面と警報も直す。
- 指標は 60 秒ごとに届く。見ていない時間（監視のコンテナを止めている間）のデータは無い。「30 日」の面は、見ていた時間の中だけの値である。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/observability-setup-questions.md`（Q1〜Q7、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `compose.yaml`、`docker/monitoring/`、`README.md` の「手元の監視（Grafana）」
- 2026-09-23 12:43〜12:57 に手元で実行した `docker compose`・Grafana の API・Prometheus と Loki の問い合わせ・H2 の Shell の出力

## Assumptions & Open Questions

- 画面の文言の表示の言語（`GF_USERS_DEFAULT_LANGUAGE=ja-JP`）は、Grafana の翻訳の範囲に依存する。
