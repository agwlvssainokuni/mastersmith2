# ダッシュボード（dashboards）

前の Intent の記録（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/dashboards.md`、`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/dashboards.md`）を正とし、今回の差だけを書きます。ダッシュボードはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`）でリポジトリに置き、画面からは変えません（project.md の Deployment）。決定は `observability-setup-questions.md`（Q1: A）と、この段の追加の確認（p95 の式: A）です。

## 1. 今回の差

ダッシュボードの版を 2 から 3 に上げ、パネルを 26 から 34 にしました（2つの行を足した）。

### 1.1 招待と登録（Intent 260925-user-management の U3）

| パネル | 式の要点 | 確かめた値（2026-09-29 01:18:50 の時点） |
|---|---|---|
| API ごとの要求の数（1 時間） | `sum by (method, uri) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/invitations.*\|/api/registration/.*"}[1h]))` | 6本の API ごとに値が出た。招待 約 18.4・送り直し 約 16.4・一覧 約 18.5・取り消し 約 1.1・リンクの確かめ 約 34.9・登録の完了 約 32.8 |
| API ごとの時間（95 パーセンタイル） | `histogram_quantile(0.95, sum by (le, span_name) (rate(traces_spanmetrics_latency_bucket{service="mastersmith",span_kind="SPAN_KIND_SERVER",span_name=~"http (get\|post) /api/(admin/invitations\|registration/).*"}[5m])))`。目標の線は 1 秒（黄）と 5 秒（赤） | 招待 0.563 秒・一覧 0.018 秒・リンクの確かめ 0.008 秒・登録の完了 0.486 秒・送り直し 0.038 秒・取り消し 0.015 秒 |
| API ごとの 5xx の件数（1 時間） | `sum by (method, uri) (increase(...{...,status=~"5.."}[1h])) or on() vector(0)` | 0（5xx は無かった） |

### 1.2 メールの送信（Intent 260925-user-management の U1）

| パネル | 式の要点 | 確かめた値 |
|---|---|---|
| 送信の数（1 時間） | `sum by (mail_template, mail_language, mail_outcome) (increase(mastersmith_mail_send_milliseconds_count{service_name="mastersmith"}[1h]))` | `invitation ja sent` 約 16.4・`invitation en sent` 約 16.4・`failed` は 0（2節） |
| 失敗の種類ごとの数（1 時間） | `sum by (mail_failure_kind) (increase(...{...,mail_outcome="failed"}[1h])) or on() vector(0)` | 系列 `timeout`・`connection_failed` が出た。値は 0（2節） |
| 送信の時間（95 パーセンタイル） | `histogram_quantile(0.95, sum by (le) (rate(traces_spanmetrics_latency_bucket{service="mastersmith",span_name="mastersmith.mail.send"}[5m])))`。目標の線は SMTP の時間切れの 3 秒 | 0.042 秒 |

- 見た目の設定の API（`GET /api/appearance`）のパネルは足していません（Q1: A）。要求の数と 5xx は、既存の全体のパネルに含まれます。
- 5xx の件数と失敗の種類のパネルは `or on() vector(0)` にしました。左の式に系列があるときは 0 の線を足さず、系列が無いときだけ 0 を出します（既存の `or vector(0)` の形では、系列があっても 0 の線が1本増えるため）。

## 2. 確かめ方と分かったこと

使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の管理者、profile `mail` の Mailpit、メモリの上限 1g）を2回起動し、lgtm に送らせました（Q5: C）。配備したアプリには確かめの要求を送っていません。

1. 1回目（01:09〜01:13）: 招待・送り直し・一覧・取り消し・リンクの確かめ（正しい・誤り）・登録の完了（成功・入力の誤り・使い終えた招待）・パスワードの変更（成功・今のパスワードの誤り・入力の誤り）・プリファレンスの取得と保存（成功・誤り）・見た目の設定を1回ずつ送り、Mailpit を止めた状態で招待を1件送りました。
2. 2回目（01:15〜01:19）: 数の値を確かめるため、招待から登録の完了までを 8 秒おきに 20 回くり返しました（うち2回は Mailpit を止めて送信を失敗させた）。

分かったこと：

- **95 パーセンタイルを出せない指標がある。** アプリの指標 `http_server_requests_milliseconds_bucket` と `mastersmith_mail_send_milliseconds_bucket` は、バケットが `le="+Inf"` の1つしかありません。アプリの設定でヒストグラムを有効にしていないためです（有効にしているのは DSL の操作の指標 `DslOperationMetrics` だけ）。そのため、この2つの指標の 95 パーセンタイルの式は、要求があっても `NaN` になります。
- 前の Intent の記録（`260923-dsl-schema-loader` の `alarms.md` 3節）は、ログイン・トークンの更新・確認用 API の p95 が `NaN` の理由を「直近 5 分に要求が無いため」としていました。今回、直近 5 分にログインがあっても `NaN` だったため、その理由は誤りで、バケットが無いことが原因と分かりました。前からある3つのパネル（ログインの API・トークンの更新の API・確認用 API）は、これまで値を出していなかったことになります。警報への影響は `alarms.md` 2節です。
- 依頼者の決定（p95 の式: A）で、今回足した2つの時間のパネルは、lgtm がトレースから作る時間の指標 `traces_spanmetrics_latency_bucket` で書きました。この指標にはバケットがあり、値が出ることを確かめました。ただし、境目が 2 倍刻み（0.512・1.024・2.048 秒…）で粗く、トレースの割合（`MASTERSMITH_TRACING_SAMPLING_PROBABILITY`、既定 1.0）にも左右されます。送信のスパンは送信が失敗しても状態が OK のため、結果（sent・failed）では分けられません。
- 前からある p95 のパネル3つは変えていません。アプリの設定でバケットを出す直しは、次の Intent でコントラストの直しと一緒に行います（依頼者の決定）。
- **新しく現れた系列の最初の 1 件は数えられない。** Prometheus の `increase` は、系列の最初の値の前を知らないため、系列が初めて現れたときの 1 件を数えません。2回目の送信の失敗（ja・en で1件ずつ）は、それぞれの系列の最初の値だったため、送信の数と失敗の種類の数が 0 になりました。取り消し（2件）も 約 1.1 になりました。件数が少ない間は、実際より少なく見えます。README の「手元の監視（Grafana）」に書きました。
- 送信の失敗の種類は、Mailpit を止めた（名前が引けない）ときに1回目は `timeout`、2回目は `connection_failed` として記録されました。

## 3. Explore と保存の確かめ

- lgtm の環境変数に `GF_USERS_VIEWERS_CAN_EDIT: "true"` を足しました（`compose.yaml`）。
- Grafana の設定の API（`/api/frontend/settings`）で `viewersCanEdit: true`・`exploreEnabled: true` を確かめました。
- ログインなしでダッシュボードを保存する要求（`POST /api/dashboards/db`）は `403`（`dashboards:create`・`dashboards:write` が無い）で拒否され、ダッシュボードは作られませんでした。読み込み直した後のダッシュボードは `canSave: false`・`canEdit: true` です（画面で一時的にいじれるが、保存はできない）。
- 依頼者が画面で、左のメニューに「Explore」が出ることと、ダッシュボード「MasterSmith の概要」の下に行「招待と登録」と「メールの送信」があることを確かめました（「両方見えた」）。

## 4. 確かめの後の片付け（Q4: A）

- 01:27 に、配備した `.env` の2行をコメントに戻し（有効 0 件・コメント 1 件）、lgtm を止め、アプリを起動し直しました（26 秒で healthy、`/actuator/health` は UP、ERROR のログ 0 件、アプリのコンテナに外部エクスポートの環境変数は 0 件）。
- 使い捨ての環境（コンテナ・ボリューム・網）と、ホームの下の一時の場所（仮の署名鍵・仮の管理者のパスワード・内部DB の複写）は、2回とも消しました。
- lgtm のボリューム（`mastersmith_mastersmith-monitoring`）には、使い捨ての環境から送った指標・ログ・トレースが、配備したアプリのものと同じ `service_name="mastersmith"` で残っています（個人に関する値は含まない。仮の宛先 `@example.test` は伏せ字で届いた）。

## Sources

- `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/observability-setup-questions.md`（Q1〜Q5、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/`、`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/`
- 各単位の基盤の設計: `construction/u3-invitation/infrastructure-design/monitoring-design.md`、`construction/u1-mail/infrastructure-design/cicd-pipeline.md`、`construction/u8-instance-appearance/infrastructure-design/monitoring-design.md`
- `construction/u3-invitation/nfr-requirements/performance-requirements.md`（NFR6.1〜NFR6.5）、`construction/u1-mail/nfr-requirements/`（SMTP の時間切れ 3 秒）
- `docker/monitoring/dashboards/mastersmith-overview.json`、`compose.yaml`（`lgtm`）、`docker/perf/compose.yaml`、`README.md`（「手元の監視（Grafana）」）
- `backend/src/main/java/cherry/mastersmith/mail/service/SmtpMailSender.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`
- 実行したコマンドの出力: `docker compose --profile monitoring up -d --wait lgtm`、`docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile mail up -d --wait app mailpit`、`docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`（すべての式を1つずつ実行）、`curl -s http://127.0.0.1:3000/api/frontend/settings`・`/api/dashboards/db`・`/api/dashboards/uid/mastersmith-overview`

## Assumptions & Open Questions

None.
