# ダッシュボード（dashboards）

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/dashboards.md` を正とし、今回の差だけを書く。ダッシュボードはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`）でリポジトリに置き、画面からは変えない（project.md の決まり）。

## 1. 今回の差

- U4 のコード生成で「DSL の操作（Intent 260923-dsl-schema-loader の U4）」の行を足した（パネル 3 つ）。この段で式の中身は変えていない。

| パネル | 式の要点 | 確かめた値（2026-09-25、依頼者が画面で生成と破棄を行った後） |
|---|---|---|
| 操作ごとの件数（1 時間） | `sum by (operation) (increase(mastersmith_dsl_operation_milliseconds_count{service_name="mastersmith"}[1h]))` | `generate` 約 3.3・`discard` 約 3.3（increase の外挿のため整数にならない） |
| 結果ごとの件数（1 時間） | `sum by (outcome) (...)` | `success` 約 6.5 |
| 操作ごとの時間（95 パーセンタイル） | `histogram_quantile(0.95, sum by (le, operation) (rate(mastersmith_dsl_operation_milliseconds_bucket...[5m])))` | `generate` 132ms・`discard` 12ms |

## 2. 確かめ方

手元の監視（profile `monitoring`）を起動し、外部エクスポートを有効にしたアプリから指標を送り、ダッシュボードと警報のすべての式（34 件）を Prometheus・Loki の API で1つずつ実行した（project.md の決まり）。結果は `alarms.md` 3節の表。すべて `success` で、書き方の誤りや名前の違いは無かった。

## 3. 限界と注意

- 手元の監視のコンテナの上限を 900m から 1536m に上げた（`compose.yaml`・README）。900m では、DSL の行を含むダッシュボードを開いたときに Grafana のプロセスがメモリの上限で止められた（VM のカーネルのログ `Memory cgroup out of memory: Killed process ... (grafana)`、コンテナの `memory.events` の `oom_kill 1`。コンテナは動き続け、画面だけが応答しなくなった）。1536m で起動し直した後は応答した。
- ログから数えるパネル（5 件）は、確かめの時間に該当の出来事が無かったため結果が空。式としては実行できた。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/observability-setup-questions.md`（Q1〜Q4、確認済みの要約）
- 前の Intent の同じ段の記録（正とする）: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/`
- `docker/monitoring/dashboards/mastersmith-overview.json`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`compose.yaml`（`lgtm`）、`README.md`（「手元の監視（Grafana）」「DSL の管理」）
- `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/observability-design.md`、`construction/build-and-test/build-and-test-summary.md`（OBS-DASH・U4-STORAGE-RUN）
- 実行したコマンド: `docker compose --profile monitoring up -d lgtm`、`docker compose --profile targetdb-postgres up -d app`、`docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（すべての式を1つずつ実行）、`colima ssh -- sudo dmesg`、`docker exec mastersmith-lgtm-1 cat /sys/fs/cgroup/memory.events`

## Assumptions & Open Questions

None.
