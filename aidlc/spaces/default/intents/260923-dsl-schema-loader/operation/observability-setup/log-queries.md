# ログの見方（log-queries）

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/log-queries.md` を正とし、今回の差だけを書く。

## 1. 監視のコンテナを使わないとき（`docker compose logs`）

```bash
docker compose logs app | grep 'DSL の操作を終えました'                       # DSL の操作の終わり（1操作1件）
docker compose logs app | grep 'DSL の操作を終えました' | grep '"dsl.operation":"generate"'   # 種類で絞る（標準出力の JSON にはキーと値がある）
docker compose logs app | grep '"dsl.outcome":"' | grep -v '"dsl.outcome":"success"'          # 成功以外
```

- 標準出力の JSON（1行1件）には `dsl.operation`・`dsl.outcome`・`dsl.durationMs` のキーと値が入る（Deployment Execution で確かめた）。

## 2. 監視のコンテナを使うとき（Grafana の Explore、Loki）

- **満たしていないこと（Not Met）: Loki では `dsl.operation`・`dsl.outcome` で絞り込めない。** アプリが Loki へ送る出力（`ObservabilityConfig` の `OpenTelemetryAppender`）は、ログのキーと値を送る設定（`captureKeyValuePairAttributes`）が既定の無効のままで、Loki に届くのは本文と、トレースID・スパンID・水準などだけ（2026-09-25、`categorize-labels` の応答で構造化メタデータを見て確かめた）。前の Intent から同じで、ログインなど既存のログのキーと値も Loki では絞り込めない。
- 依頼者の判断で、後の小さな Intent で直す（出力の設定を1行足し、送るキーと値に秘密情報が入らないことを点検とテストで確かめ、試験・配備し直す）。
- 代わりの見方（今できること）:

```logql
# DSL の操作の終わり（本文で絞る）
{service_name="mastersmith"} |= "DSL の操作を終えました"
# 件数（1 時間）
sum(count_over_time({service_name="mastersmith"} |= "DSL の操作を終えました" [1h]))
```

  種類ごと・結果ごとの件数と時間は、ダッシュボードの「DSL の操作」の行（指標）で見る。1つの操作の詳しい内容は、Loki の行のトレースID（`trace_id`）で Tempo のトレースを開くか、`docker compose logs app` の JSON を見る。

## 3. 起動のときの複数行のログ（Q3: A、後の Intent の課題）

- 起動のときの Hibernate の案内（`Database JDBC URL`・`Database driver` など 11 行）が、改行を含んだまま1件のログになっている（1行1件の JSON の決まりから外れる）。前の版からの動き。記録だけにし、後の Intent の課題とする。

## 4. 定期の確認に足すもの

- 内部DB のファイルの大きさ（`alarms.md` 2節の手順）。DSL の投入と適用を重ねたときに見る。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/observability-setup-questions.md`（Q1〜Q4、確認済みの要約）
- 前の Intent の同じ段の記録（正とする）: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/`
- `docker/monitoring/dashboards/mastersmith-overview.json`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`compose.yaml`（`lgtm`）、`README.md`（「手元の監視（Grafana）」「DSL の管理」）
- `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/observability-design.md`、`construction/build-and-test/build-and-test-summary.md`（OBS-DASH・U4-STORAGE-RUN）
- 実行したコマンド: `docker compose --profile monitoring up -d lgtm`、`docker compose --profile targetdb-postgres up -d app`、`docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（すべての式を1つずつ実行）、`colima ssh -- sudo dmesg`、`docker exec mastersmith-lgtm-1 cat /sys/fs/cgroup/memory.events`

## Assumptions & Open Questions

None.
