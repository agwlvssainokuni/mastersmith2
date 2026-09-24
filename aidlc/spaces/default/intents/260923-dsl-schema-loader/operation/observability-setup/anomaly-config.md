# 異常の検出（anomaly-config）

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/anomaly-config.md` を正とする（機械学習による異常の検出は置かず、固定の閾値の警報とダッシュボードで見る。配備先が決まったときに見直す）。

## 1. 今回の差

- DSL の操作について、異常の検出は置かない。操作の件数と時間はダッシュボードの「DSL の操作」の行で見る。
- 内部DB のファイルの大きさの伸び（U4-STORAGE-RUN）は、運用の手順で見る（`alarms.md` 2節）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/observability-setup-questions.md`（Q1〜Q4、確認済みの要約）
- 前の Intent の同じ段の記録（正とする）: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/`
- `docker/monitoring/dashboards/mastersmith-overview.json`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`compose.yaml`（`lgtm`）、`README.md`（「手元の監視（Grafana）」「DSL の管理」）
- `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/observability-design.md`、`construction/build-and-test/build-and-test-summary.md`（OBS-DASH・U4-STORAGE-RUN）
- 実行したコマンド: `docker compose --profile monitoring up -d lgtm`、`docker compose --profile targetdb-postgres up -d app`、`docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（すべての式を1つずつ実行）、`colima ssh -- sudo dmesg`、`docker exec mastersmith-lgtm-1 cat /sys/fs/cgroup/memory.events`

## Assumptions & Open Questions

None.
