# 仮の目標（slo-config）

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/slo-config.md` を正とする。仮の目標（稼働 99.5%・30 日など）は、配備先が決まったときに正式に決める（project.md の決まり）。

## 1. 今回の差

- DSL の操作について、新しい仮の目標は置かない。時間の目標（NFR1.4〜1.8・1.11・1.18〜1.20）は Build and Test で1回ずつ測り、すべて満たした（`construction/build-and-test/build-and-test-summary.md`）。95 パーセンタイルの目標（NFR1.10）と同時の実行（NFR1.12・U4-POOL）は Performance Validation が持つ。
- ダッシュボードの「操作ごとの時間（95 パーセンタイル）」で、生成・投入・プレビューの表示などの時間を見られる（確かめた値は生成 132ms・破棄 12ms。手元の1回ずつの操作）。

## 2. この段が持ち主の目標の判定

| 目標 | 判定 | 根拠 |
|---|---|---|
| OBS-DASH（ダッシュボードの DSL の行の式が値を返す） | Met | `dashboards.md` 1節・`alarms.md` 3節 |
| OBS-DASH（ログで `dsl.operation` を絞り込める） | **Not Met** | `log-queries.md` 2節。依頼者の判断で、後の小さな Intent で直す |

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/observability-setup-questions.md`（Q1〜Q4、確認済みの要約）
- 前の Intent の同じ段の記録（正とする）: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/`
- `docker/monitoring/dashboards/mastersmith-overview.json`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`compose.yaml`（`lgtm`）、`README.md`（「手元の監視（Grafana）」「DSL の管理」）
- `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/observability-design.md`、`construction/build-and-test/build-and-test-summary.md`（OBS-DASH・U4-STORAGE-RUN）
- 実行したコマンド: `docker compose --profile monitoring up -d lgtm`、`docker compose --profile targetdb-postgres up -d app`、`docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（すべての式を1つずつ実行）、`colima ssh -- sudo dmesg`、`docker exec mastersmith-lgtm-1 cat /sys/fs/cgroup/memory.events`

## Assumptions & Open Questions

None.
