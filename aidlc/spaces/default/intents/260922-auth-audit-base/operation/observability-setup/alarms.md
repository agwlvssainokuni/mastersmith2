# 警報（alarms）

Intent `260922-auth-audit-base`（auth-audit-foundation）の手元の監視の警報の決まり。入力は、U1〜U4 の `construction/<単位>/infrastructure-design/monitoring-design.md`（monitoring-design）と `infrastructure-specification.md`（infrastructure-specification）、`construction/<単位>/nfr-design/performance-design.md`（performance-design）・`security-design.md`（security-design）・`reliability-design.md`（reliability-design）である。決定は `observability-setup-questions.md`（Q1〜Q7）にある。

## 1. 置き場所と知らせ方

| 項目 | 値 |
|---|---|
| 定義のファイル | `docker/monitoring/provisioning/alerting/mastersmith.yaml`（Grafana の起動時に読み込む。フォルダー MasterSmith、グループ `mastersmith`、1 分ごとに評価） |
| 知らせ先 | **外へは知らせない**（Q6）。Grafana の「Alerting」→「Alert rules」で状態（Normal / Pending / Firing）を見る |
| 動く時間 | 監視のコンテナを起動している間だけ（Q7）。止めている間は評価されない |
| データが無いとき | `noDataState: OK`（出来事が無いことを異常にしない）。評価の失敗は `Error` |

承認済みの設計の「警報の送り先は配備先が決まったら決める」（U1〜U4 の monitoring-design 2章）は、今も未定のまま（画面で見るだけ）である。

## 2. 警報の一覧（16 件）

| uid | 名前 | 条件 | 待ち | 重さ | 由来 |
|---|---|---|---|---|---|
| ms-app-absent | アプリの停止（指標が届かない） | `absent(process_uptime_milliseconds)` | 5 分 | 高 | U1 の「アプリの停止」。ヘルスチェックの状態は指標に無いため、指標が届かないことで代える |
| ms-5xx-ratio | 5xx の割合の増加 | 5 分の 5xx の割合 > 1% | 5 分 | 中 | U1（NFR10.9） |
| ms-error-logs | ERROR のログの増加 | 5 分の ERROR > 5 件 | すぐ | 中 | U1 |
| ms-audit-fail | 監査の書き込みの失敗 | 1 時間に「監査イベントの記録に失敗しました」> 0 件 | すぐ | 中 | U4（NFR10.3・NFR10.5） |
| ms-login-p95 | ログインの応答の遅れ | 95 パーセンタイル > 1000 ms | 5 分 | 低 | U1・U2（NFR1.1） |
| ms-refresh-p95 | トークンの更新の応答の遅れ | 95 パーセンタイル > 1000 ms | 5 分 | 低 | U2（NFR1.2） |
| ms-check-p95 | 確認用 API の遅れ | 95 パーセンタイル > 300 ms | 5 分 | 低 | U3（NFR1.1） |
| ms-pool-pending | コネクションプールの待ち | 待ち > 0 | 1 分 | 低 | U1 |
| ms-heap | JVM のメモリの不足の兆し | ヒープの使用率 > 85% | 5 分 | 低 | U1 |
| ms-forbidden | 管理画面への拒否の増加 | 1 時間の 403 > 20 件 | すぐ | 中 | U3 |
| ms-lock | ロックの多発 | 1 時間に「アカウントをロックしました」> 5 件 | すぐ | 中 | U2 |
| ms-rejected | 回り込みの試み | 1 時間に「正規化されていないパスの要求を拒否しました」> 10 件 | すぐ | 中 | U3（NFR3.3） |
| ms-origin | 送り元の不一致の多発 | 1 時間に「要求の送り元が自分の配信元と一致しないため拒否しました」> 10 件 | すぐ | 低 | U2 |
| ms-audit-slow | 監査の書き込みの遅れ | 1 日に「監査イベントの記録に時間がかかりました」≥ 3 件 | すぐ | 低 | U4（設計の「1 日に数件以上」を 3 件と置いた） |
| ms-cleanup-fail | トークンの削除の失敗 | 1 日に削除の失敗の ERROR > 0 件 | すぐ | 低 | U2（NFR1.7） |
| ms-bcrypt | 照合の時間の範囲外 | 1 日に照合の時間の WARN > 0 件 | すぐ | 低 | U2（NFR2.1） |

設計の候補のうち、次は作らなかった: 「ヘルスの状態が DOWN を2回」（ヘルスチェックの結果は指標として送られないため、ms-app-absent で代える）、「起動の時間 30 秒超」（1回ごとの値で、Performance Validation で測る）、「ボリュームの使用量」（コンテナの外の値で、送られないため。`log-queries.md` 4節の月1回の確認で見る）。

## 3. 確かめた結果

| 確かめたこと | 結果 |
|---|---|
| 16 件が読み込まれ、評価でエラーが出ない | 12:54 にすべて `inactive / ok` |
| 警報が実際に鳴る | 送り元の違う要求を 11 件送り（監査ログには残らない要求）、ms-origin が 12:56:30（日本時間）に Firing になり、警報の一覧（Alertmanager）に `active` で出た |

## 4. 配備先が決まったときに見直すもの

- 知らせ先（メール・チャットなど）と、重さごとの知らせ方。
- しきい値（利用者 50 名の想定から置いた値。実際の量を見て見直す。U3 の monitoring-design 1章）。
- 目標（`slo-config.md`）の消費の速さ（バーンレート）による警報。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/observability-setup-questions.md`（Q1〜Q7、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `compose.yaml`、`docker/monitoring/`、`README.md` の「手元の監視（Grafana）」
- 2026-09-23 12:43〜12:57 に手元で実行した `docker compose`・Grafana の API・Prometheus と Loki の問い合わせ・H2 の Shell の出力

## Assumptions & Open Questions

- Grafana の既定の知らせ先（メール）は送信の設定が無いため、警報が鳴っても外へは届かない。これは Q6 の決定どおりである。
