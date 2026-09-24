# トレース（tracing-config）

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/tracing-config.md` を正とする。トレースの設定は変えていない。

## 1. 今回の確かめ

- 外部エクスポートを有効にしたアプリの DSL の操作のログに、トレースID とスパンID が付いて Loki に届くことを確かめた（`DslOperationMetrics` のログの構造化メタデータの `trace_id`・`span_id`）。ログからトレースへたどれる。
- 外部エクスポートは既定で無効のまま（確かめのあいだだけ `.env` で有効にし、確かめた後に依頼者が2行を消してアプリを作り直す。Q4: A）。

## 2. 1つの DSL の操作の追い方

1. Grafana の Explore（Loki）で `{service_name="mastersmith"} |= "DSL の操作を終えました"` を開く。
2. 行の `trace_id` から Tempo のトレースを開く。
3. `docker compose logs app` で同じ `traceId` を grep すると、標準出力の JSON（`dsl.operation`・`dsl.outcome`・`dsl.durationMs` を含む）を見られる。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/observability-setup-questions.md`（Q1〜Q4、確認済みの要約）
- 前の Intent の同じ段の記録（正とする）: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/`
- `docker/monitoring/dashboards/mastersmith-overview.json`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`compose.yaml`（`lgtm`）、`README.md`（「手元の監視（Grafana）」「DSL の管理」）
- `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/observability-design.md`、`construction/build-and-test/build-and-test-summary.md`（OBS-DASH・U4-STORAGE-RUN）
- 実行したコマンド: `docker compose --profile monitoring up -d lgtm`、`docker compose --profile targetdb-postgres up -d app`、`docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（すべての式を1つずつ実行）、`colima ssh -- sudo dmesg`、`docker exec mastersmith-lgtm-1 cat /sys/fs/cgroup/memory.events`

## Assumptions & Open Questions

None.
