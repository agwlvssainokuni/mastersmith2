# 監視の設計（実装側） — u1-internal-db-platform

NFR Design の観測の設計（`observability-design.md`）を、今の監視の仕組み（Grafana の警報・ダッシュボード、アプリのログ）の上でどう実現するかを書きます。この単位は監視の設定を変えません。

## Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| `hikaricp_connections_pending`（`pool=mastersmith-db`） | HikariCP・Micrometer（OTLP でエクスポート） | 既存の警報の線のまま（`docker/monitoring/provisioning/alerting/mastersmith.yaml`） | 内部DB の接続の待ち。PostgreSQL のときも同じ名前・同じラベルで出る（NFR4.11） |
| `hikaricp_connections_active`・`hikaricp_connections_timeout_total`（`pool=mastersmith-db`） | 同上 | 既存のまま | 接続の枯渇の兆し |
| `/actuator/health` の内部DB の項目 | `TimeBoundedDbHealthIndicator` | UP・DOWN | 内部DB に届くか（NFR4.5） |

## Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 接続の待ち（既存） | `max(hikaricp_connections_pending{service_name="mastersmith"})` が線を超える | 既存のまま | 既存のまま（Grafana の警報。通知の先は配備先が決まるまで無い） |
| 新しい警報 | 足さない | — | 起動時の失敗はアプリが起動しないため警報にできない。運用者がログの ERROR（`failureKind`）と終了の状態を見る |

## SLIs / SLOs

数値の SLO は置かない（配備先が決まっていない）。見る項目は `observability-design.md` の SLI の表のとおり。

| SLI | SLO target | Measurement window |
|---|---|---|
| 内部DB の生死（health の UP） | 置かない | — |
| 接続プールの待ち | 置かない（既存の警報の線を使う） | 既存のまま |

## Logs & Tracing

| 項目 | 決めたこと |
|---|---|
| ログの出力先 | 今のまま（標準出力の構造化ログ。外部エクスポートは既定で無効） |
| 新しいログ | INFO `Internal DB kind resolved`（`internalDbKind`）・INFO `Internal DB migration finished`（`migrationMillis`・`migrationCount`）・ERROR `Internal DB startup failed`（ENT-005 の項目）。ロガーは `cherry.mastersmith.internaldb.platform` の下 |
| ログの問い合わせ | 既存の Loki の問い合わせで、ロガー名または `failureKind` の項目で絞れる。新しい問い合わせの設定は足さない |
| トレース | 起動の時点では要求が無く、トレースの ID が付かない。足さない |
| ダッシュボード | 変えない（`docker/monitoring/dashboards/mastersmith-overview.json`）。H2 固有の項目（ファイルの大きさ・詰め直し）が PostgreSQL のとき空になる可能性は、U4 の運用の文書で「PostgreSQL では使わない」と書く |

## 確かめ

- `docker/monitoring/` に差分が無いこと（コード生成の記録に書く）。
- PostgreSQL の結合テストで、`MeterRegistry` に `pool=mastersmith-db` の `hikaricp.connections` のメーターが出ること（NFR4.11。`observability-design.md`）。
- 警報の式を書き換えないため、「書く前に起動して指標の名前を確かめる」（`project.md` の Corrections）は、この単位では指標が既存と同じ名前であることの確認で足りる。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/observability-design.md`
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`・`docker/monitoring/dashboards/mastersmith-overview.json`
- `aidlc/spaces/default/memory/project.md`（Corrections: 警報・ダッシュボードの式）

## Assumptions & Open Questions

- [assumption] ダッシュボードに H2 固有の項目があるかは、U4 の文書の作業で `mastersmith-overview.json` の項目を見て確かめる（この単位は変えない）。
