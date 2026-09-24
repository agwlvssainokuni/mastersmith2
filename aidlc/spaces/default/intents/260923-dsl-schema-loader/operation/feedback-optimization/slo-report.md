# 目標の達成の報告（slo-report）

Intent `260923-dsl-schema-loader` を配備した後のサービスの目標（SLO）の報告。仮の目標は前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/slo-config.md` を正とし、この Intent では DSL の操作の新しい仮の目標は置いていない（`operation/observability-setup/slo-config.md`）。決定は `feedback-optimization-questions.md`（Q1: A）。

## 1. 判定

| SLI | 仮の目標 | 30 日の実測 | 判定 | 理由 |
|---|---|---|---|---|
| 稼働（5xx でない応答の割合） | 99.5%・30 日 | 無い | **Unverified** | 手元の監視は見たいときだけ動き、配備してから数時間しか経っていない |
| ログインの API の応答時間（95 パーセンタイル） | 1 秒以内（同時 10） | 無い | **Unverified** | 同上 |
| トークンの更新の API の応答時間 | 1 秒以内（同時 10） | 無い | **Unverified** | 同上 |
| 確認用 API の応答時間 | 300ms 以内（同時 10） | 無い | **Unverified** | 同上 |
| 監査の書き込みの成功の割合 | 失敗 0 件（候補） | 無い | **Unverified** | 同上 |

- 誤りの予算の消費と、消費の速さの警報は、30 日の連続した収集が無いため計算できない（前の Intent の `slo-config.md` 3節のとおり、配備先が決まって常時の収集ができてから決める）。
- 目標を緩めて「満たした」ことにはしない（project.md の Testing Posture）。

## 2. 基準の値（今ある証拠）

配備先が決まって常時の測定を始めたときに、比べる元にする値。

| 時点 | 内容 | 値 | 出典 |
|---|---|---|---|
| 配備の直後（2026-09-25 01:20 台） | 健全性・スモークテスト | healthy（起動から約 9 秒）、ERROR 0 件、スモークテストすべて合格、監査イベントの必須項目あり | `operation/deployment-execution/health-check-report.md`・`smoke-test-results.md` |
| 手元の監視での確かめ（Observability Setup） | DSL の操作の時間（95 パーセンタイル、1回ずつの操作） | 生成 132ms・破棄 12ms。ダッシュボードと警報の 34 式すべて実行できた | `operation/observability-setup/dashboards.md`・`alarms.md` |
| 負荷の試験（使い捨ての環境、上限 2g） | 軽い API（NFR1.10） | 今の状態・履歴 1.1ms、適用 6.9ms、破棄 5.1ms（95 パーセンタイル）。失敗 0 | `operation/performance-validation/test-results.md` 2節 |
| 同 | 10MB の DSL とログイン同時 10 | 失敗 0、プロセスのメモリの最大は上限の 93%、接続プールの待ちの時間切れ 0 | 同 3節 |
| この段の確かめ（2026-09-25T07:09+09:00） | 配備したアプリの状態 | healthy、再起動 0 回、OOMKilled なし、ヘルスチェックの応答 200（12ms）。02:00 に作り直してからのログは INFO 74・WARN 4（起動の定型の2件 × 2回）・ERROR 0。メモリの使用 408MiB / 2GiB（`anon` 425MB）。内部DB のボリューム 44KB | `drift-report.md`、`docker inspect`・`docker logs`・`docker stats` |
| 止まった時間 | 依頼者の判断で止めた時間 | 配備で約 20 秒、監査イベントの確かめで数秒、Observability Setup の作り直し2回（数十秒ずつ）、Performance Validation で約 26 分 | `deployment-log.md`、`observability-setup` の記録、`performance-validation/test-results.md` 1節 |

- 障害は起きていない（`operation/incident-response/incident-plan.md` の重さ「高」「低」に当たる出来事は無い）。止まった時間はすべて計画した操作によるもの。

## 3. 配備先が決まったときの測り方

| 項目 | 手元（今の配備先のまま常時で測る場合） | 配備先が決まったとき |
|---|---|---|
| 収集 | 手元の監視（profile `monitoring`、上限 1536m）を動かし続け、`.env` の外部エクスポートの2項目を入れる。VM のメモリを約 1.5GB 余分に使う（`cost-analysis.md` 2節） | 配備先の監視の仕組みへ OpenTelemetry で送る（外部エクスポートは既定で無効、配備の設定で有効にする。team.md の Deployment） |
| 稼働の測り方 | 前の Intent の `slo-config.md` 2節の式（5xx でない応答の割合） | 同じ式を配備先の道具で。ヘルスチェックの外からの確かめ（合成の監視）を足すかを決める |
| DSL の操作 | ダッシュボードの「DSL の操作」の行（`mastersmith_dsl_operation_milliseconds`） | 同じ指標。DSL の操作に目標を置くか（例: 軽い API の 95 パーセンタイル 1 秒）を決める |
| 期間と予算 | 30 日の回る窓。予算の消費に応じた判断は配備先が決まってから | 同左 |
| 足りない指標 | 内部DB のファイルの大きさ（U4-STORAGE-RUN。README の手順で見る）、Loki でログのキーと値を絞れない件（後の Intent） | 同左を直してから（`feedback-loop.md`） |

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/feedback-optimization-questions.md`（Q1: A、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/slo-config.md`・`dashboards.md`・`alarms.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/slo-config.md`（仮の目標の正）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-log.md`・`health-check-report.md`・`smoke-test-results.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/performance-validation/test-results.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/incident-response/incident-plan.md`
- 実行したコマンド: `docker inspect mastersmith-app-1`・`curl -s http://localhost:8080/actuator/health`・`docker logs mastersmith-app-1`（水準ごとの件数）・`docker stats --no-stream`・`docker exec mastersmith-app-1 cat /sys/fs/cgroup/memory.stat`

## Assumptions & Open Questions

- [assumption] 仮の目標の正式な値と、常時の測定を始める時期は、配備先が決まったときに依頼者が決める。
