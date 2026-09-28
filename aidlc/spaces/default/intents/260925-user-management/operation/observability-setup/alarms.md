# 警報（alarms）

前の Intent の記録（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/alarms.md`、`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/alarms.md`）を正とし、今回の差だけを書きます。警報はファイル（`docker/monitoring/provisioning/alerting/mastersmith.yaml`、16 件）でリポジトリに置きます。知らせの先は、配備先が決まるまで作りません（Grafana の画面で見るだけ。project.md の Deployment）。

## 1. 今回の差

- **この Intent では警報を足していません**（Q2: D）。招待・登録・送信の失敗は、既存の `ms-5xx-ratio`（5xx の割合）と `ms-error-logs`（ERROR のログの増加）で拾います。
- 単位の設計の間の食い違いは、「足さない」と決めて次のとおり記録します。

| 論点 | 設計の食い違い | 決定 | 残る危険 |
|---|---|---|---|
| N3 招待の定期の削除の失敗 | 承認の場の N3 は「警報を足す」、U3 の基盤の設計（`construction/u3-invitation/infrastructure-design/monitoring-design.md`）は「足さない、配備先が決まったときに見直す」 | 足さない（Q2: D） | 失敗のログは ERROR（本文「保存期間を過ぎた招待の削除に失敗しました。次の回に再び行います」）で出るが、`ms-error-logs` は 5 分で 5 件を超えたときだけ鳴るため、1日1回の削除の失敗では鳴らない。Explore（Loki）の問い合わせで見る（`log-queries.md` 2節）。配備先が決まったときに見直す |
| 登録の完了の API の 5xx・遅れ | U6 の設計は「この段で決める」、U3 の設計は「p95 の警報は足さない（目標は performance-validation の k6）」 | 足さない（Q2: D） | 5xx は `ms-5xx-ratio` の全体の割合で拾う。遅れはダッシュボードの「API ごとの時間」で見る |
| メールの送信の失敗の増加 | U3 の設計は「送信の失敗（sendResult FAILED）の警報は作らない」 | 足さない（Q2: D） | 管理者は招待の一覧で FAILED を見て送り直す。数はダッシュボードの「失敗の種類ごとの数」で見る |

- `ms-origin`（送り元の不一致の多発）は変えていません。配備の `.env` にベース URL（`MASTERSMITH_WEB_BASE_URL=http://localhost:8080`）を入れたため、`http://127.0.0.1:8080` から開いた画面のログイン・更新・ログアウトは送り元の不一致で拒否され、この警報の数が増えます。**画面は `http://localhost:8080` で開いてください**（U3 の `monitoring-design.md` の N4・M-D3）。

## 2. 値を出さない警報（既知の欠け）

- `ms-login-p95`（ログインの応答の遅れ）・`ms-refresh-p95`（トークンの更新の応答の遅れ）・`ms-check-p95`（確認用 API の遅れ）の3件は、`http_server_requests_milliseconds_bucket` に `le="+Inf"` のバケットしか無いため、式の値が要求があっても `NaN` になり、鳴りません（`dashboards.md` 2節）。
- 3件とも `noDataState: OK` のため、Grafana の画面では「正常」に見えます。これまでの Intent でも鳴らない状態でした。
- 依頼者の決定（p95 の式: A）で、この段では3件を変えず、次の Intent でアプリの設定でバケットを出す直しをコントラストの直しと一緒に行います。直した後に、3件の式を流し直して値が出ることを確かめます。
- それまでのログインの遅れは、ダッシュボードの「要求の数」と k6（performance-validation）で見ます。

## 3. 確かめた結果（すべての式、2026-09-29 01:18:50 の時点）

使い捨ての環境から送った後に、ダッシュボードの式 26 件と警報の式 16 件を Prometheus・Loki の API で1つずつ実行しました（project.md の Corrections）。すべて `success` で、書き方の誤りや名前の違いは無かったです。Grafana の警報の評価（`/api/prometheus/grafana/api/v1/rules`）も、16 件すべて `health: ok`・`state: inactive` でした。

| 種類 | 名前 | 取り出し先 | 結果 | 値 |
|---|---|---|---|---|
| ダッシュボード（2） | 稼働（5xx でない応答の割合、30 日） | Prometheus | success | 全体=1.0 |
| ダッシュボード（5） | 要求の数（1 分あたり） | Prometheus | success | 全体=36.991 |
| ダッシュボード（6） | 5xx の割合（5 分） | Prometheus | success | 全体=0.0 |
| ダッシュボード（7） | ERROR のログ（5 分あたり） | Prometheus | success | 全体=0.0 |
| ダッシュボード（9） | ログインの API | Prometheus | success | 全体=NaN（2節） |
| ダッシュボード（10） | トークンの更新の API | Prometheus | success | （結果なし） |
| ダッシュボード（11） | 確認用 API（/api/admin/check） | Prometheus | success | （結果なし） |
| ダッシュボード（13） | コネクションプールの待ち | Prometheus | success | 全体=0.0 |
| ダッシュボード（14） | JVM のヒープの使用率 | Prometheus | success | 全体=0.179 |
| ダッシュボード（15） | 403 の件数（1 時間） | Prometheus | success | 全体=0.0 |
| ダッシュボード（17） | 監査の書き込みの失敗（1 時間） | Loki | success | （結果なし） |
| ダッシュボード（18） | アカウントのロック（1 時間） | Loki | success | （結果なし） |
| ダッシュボード（19） | 正規化されていないパスの拒否（1 時間） | Loki | success | （結果なし） |
| ダッシュボード（20） | 送り元の不一致の拒否（1 時間） | Loki | success | （結果なし） |
| ダッシュボード（21） | 監査の書き込みの遅れ（1 日） | Loki | success | （結果なし） |
| ダッシュボード（24） | 操作ごとの件数（1 時間） | Prometheus | success | （結果なし） |
| ダッシュボード（25） | 結果ごとの件数（1 時間） | Prometheus | success | （結果なし） |
| ダッシュボード（26） | 操作ごとの時間（95 パーセンタイル） | Prometheus | success | （結果なし） |
| ダッシュボード（28） | API ごとの要求の数（1 時間） | Prometheus | success | 招待 18.409・送り直し 16.412・リンクの確かめ 34.878・登録の完了 32.823・一覧 18.467・取り消し 1.127 |
| ダッシュボード（29） | API ごとの時間（95 パーセンタイル） | Prometheus | success | 招待 0.563・一覧 0.018・リンクの確かめ 0.008・登録の完了 0.486・送り直し 0.038・取り消し 0.015（秒） |
| ダッシュボード（30） | API ごとの 5xx の件数（1 時間） | Prometheus | success | 全体=0.0 |
| ダッシュボード（32） | 送信の数（1 時間） | Prometheus | success | ja sent=16.361・en sent=16.412・ja failed=0.0・en failed=0.0 |
| ダッシュボード（33） | 失敗の種類ごとの数（1 時間） | Prometheus | success | timeout=0.0・connection_failed=0.0 |
| ダッシュボード（34） | 送信の時間（95 パーセンタイル） | Prometheus | success | 全体=0.042（秒） |
| 警報（ms-app-absent） | アプリの停止（指標が届かない） | Prometheus | success | （結果なし） |
| 警報（ms-5xx-ratio） | 5xx の割合の増加 | Prometheus | success | 全体=0.0 |
| 警報（ms-error-logs） | ERROR のログの増加 | Prometheus | success | 全体=0.0 |
| 警報（ms-audit-fail） | 監査の書き込みの失敗 | Loki | success | （結果なし） |
| 警報（ms-login-p95） | ログインの応答の遅れ | Prometheus | success | 全体=NaN（2節） |
| 警報（ms-refresh-p95） | トークンの更新の応答の遅れ | Prometheus | success | （結果なし） |
| 警報（ms-check-p95） | 確認用 API の遅れ | Prometheus | success | （結果なし） |
| 警報（ms-pool-pending） | コネクションプールの待ち | Prometheus | success | 全体=0.0 |
| 警報（ms-heap） | JVM のメモリの不足の兆し | Prometheus | success | 全体=0.179 |
| 警報（ms-forbidden） | 管理画面への拒否の増加 | Prometheus | success | 全体=0.0 |
| 警報（ms-lock） | ロックの多発 | Loki | success | （結果なし） |
| 警報（ms-rejected） | 回り込みの試み | Loki | success | （結果なし） |
| 警報（ms-origin） | 送り元の不一致の多発 | Loki | success | （結果なし） |
| 警報（ms-audit-slow） | 監査の書き込みの遅れ | Loki | success | （結果なし） |
| 警報（ms-cleanup-fail） | トークンの削除の失敗 | Loki | success | （結果なし） |
| 警報（ms-bcrypt） | 照合の時間の範囲外 | Loki | success | （結果なし） |

- 「結果なし」は、その時点の直近の時間に該当の要求や出来事が無かったものです（トークンの更新・確認用 API・DSL の操作は送っていない。ログから数える式は該当の文言が出ていない）。式としては実行できました。
- `ms-app-absent` は `absent(...)` のため、アプリが動いているときは結果が空になるのが正しい動きです。

## Sources

- `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/observability-setup-questions.md`（Q2: D、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/`、`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/alarms.md`
- `construction/u3-invitation/infrastructure-design/monitoring-design.md`（N3・N4・M-D2・M-D3）、`construction/u6-registration-ui/infrastructure-design/monitoring-design.md`
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`、`docker/monitoring/dashboards/mastersmith-overview.json`
- `backend/src/main/java/cherry/mastersmith/invitation/service/InvitationCleanupJob.java`（削除の失敗のログの本文）
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（すべての式を1つずつ実行）、`curl -s http://127.0.0.1:3000/api/prometheus/grafana/api/v1/rules`

## Assumptions & Open Questions

None.
