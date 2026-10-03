# ダッシュボード（dashboards）

前の Intent の記録（`aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/dashboards.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。ダッシュボードはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`、版 3・パネル 34）でリポジトリに置き、画面からは変えません（`aidlc/spaces/default/memory/project.md` の Deployment）。決定は `observability-setup-questions.md`（Q1〜Q4 すべて A、確認済みの要約）です。

## 1. 今回の差

- **パネルを足していません**（Q3: A、U3 の `monitoring-design.md` 6節・NFR5.10）。ファイルは変えていません。
- 利用者の管理の7つの API（`/api/admin/users` の下）は、既存の全体のパネル（「要求の数」「5xx の割合」「ERROR のログ」「コネクションプールの待ち」「403 の件数」「監査の書き込みの失敗」「監査の書き込みの遅れ」）に含まれます。道ごとの値は、Grafana の Explore で次の問い合わせを流して見ます（Prometheus）。式はどれも書いた後に流し、`success` で値が返ることを確かめました（3節）。

| 見たいもの | PromQL | 確かめた値（2026-10-03 22:29:38） |
|---|---|---|
| N1 道ごと・状態コードごとの要求の数（1 時間） | `sum by (method, uri, status) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/users.*"}[1h]))` | 7つの道と状態コードの組 17 系列（GET 200 約 372・grant-admin 204 約 200・revoke-admin 204 約 200・revoke-admin 409 約 114 など） |
| N2 道ごとの時間（95 パーセンタイル、5 分） | `histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_milliseconds_bucket{service_name="mastersmith",uri=~"/api/admin/users.*"}[5m])))` | 7つとも 95 ms（すべての要求が最初のバケット `le="100"` に入り、0〜100 ms の補間の値） |
| N3 道ごとの 409 の件数（1 時間） | `sum by (method, uri) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/users.*",status="409"}[1h]))` | revoke-admin 約 114・grant-admin 約 45・suspend 約 45・resume 約 23・reset-login-failures 約 23 |
| N4 7つの API の 5xx の件数（1 時間） | `sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/users.*",status=~"5.."}[1h])) or on() vector(0)` | 0 |
| N5 403 の内訳（uri ごと、1 時間） | `sum by (uri) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",status="403"}[1h]))` | `UNKNOWN` 約 215・grant-admin 約 35（2節） |
| N7 7つの API の 1000 ms 以内の割合（5 分） | `sum(rate(http_server_requests_milliseconds_bucket{service_name="mastersmith",uri=~"/api/admin/users.*",le="1000"}[5m])) / sum(rate(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/users.*"}[5m]))` | 1 |
| N8 7つの API の 5xx でない割合（1 時間） | `1 - ((sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/users.*",status=~"5.."}[1h])) or vector(0)) / sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/admin/users.*"}[1h])))` | 1 |

- `increase` の値が整数でないのは、Prometheus が時間の幅の端を補って数えるためです。件数を正確に数えるときは、監査の記録（`log-queries.md` 3節）か Loki の件数で見ます。
- N6（`ms-5xx-ratio` の分母に占める割合）は `alarms.md` 2節に書きました。

## 2. 確かめ方と分かったこと

使い捨ての環境（`docker/perf/compose.yaml`、イメージは配備と同じ `mastersmith:local`（f386b55f16a8）、仮の署名鍵・仮の管理者、メモリの上限 1g）を起動し、外部エクスポートを使い捨てのアプリにだけ有効にして、手元の監視（profile `monitoring` の lgtm）に送らせました（Q1: A）。lgtm は使い捨ての環境の網に別名 `lgtm` でつなぎ、終わったら外しました（6節）。**配備したアプリには確かめの要求を送っていません**。配備したアプリの外部エクスポートも有効にしていません（Q4: A）。

1. 内部DB に試験用の利用者を7名（管理者 1・管理者でない 6。どれも `@example.test`）入れました。初期管理者（仮）と合わせて管理者は 2 名です。
2. 22:20〜22:27 に3回（1回分・12 回分・10 回分、回の間は 15 秒）、合わせて 1,094 件の要求を、指標の送信の周期（60 秒）を複数またいで約 7 分くり返し送りました（`project.md` の Deployment の学び）。1回分の中身は次のとおりです。
   - 一覧（検索なし・検索あり・ページの誤り 400）、氏名と言語の変更（204・入力の誤り 400・いない利用者 404）
   - 5つの操作の成功（印を付ける・外す、止める・解く、ログインを1回失敗させてから失敗回数を戻す）
   - 各理由の失敗: `NO_CHANGE`（5つの操作それぞれ）、`TARGET_SUSPENDED`（止めた利用者に印を付ける）、`SELF_OPERATION`（自分を止める・自分の印を外す）、`USER_NOT_FOUND`（いない ID に印を付ける）
   - 管理者でない利用者からの一覧の呼び出し 8 件（403）
   - **`LAST_ACTIVE_ADMIN` と `NOT_ADMIN` は、2人の管理者の同時の操作（競合）で作りました**。業務処理は拒否の理由を判定した後に操作した人を確かめ直すため（`backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminService.java`）、どちらも入口の確かめを通った後に状態が変わったときにだけ起きます。`LAST_ACTIVE_ADMIN` は2人が同時に互いの印を外したときの後の側、`NOT_ADMIN` は片方が相手の印を外すのと同時に相手が別の利用者に印を付けたときの後の側です。1回分に各 3 組を送り、終わるたびに2人を管理者に戻しました。本番のコードは変えていません。決まった手順（各理由の失敗を作る）の範囲として記録します（オーケストレーターの判断。依頼者には承認の場で伝える）。

分かったこと：

- **7つの API の `uri` の値は道の型**で、利用者 ID と検索の文字を含みません: `/api/admin/users`・`/api/admin/users/{userId}/profile`・`/api/admin/users/{userId}/grant-admin`・`.../revoke-admin`・`.../suspend`・`.../resume`・`.../reset-login-failures`。ほかのラベルは `method`・`status`・`outcome`（`SUCCESS`・`CLIENT_ERROR`）・`exception="none"`・`error="none"`。U3 の `monitoring-design.md` の見込み（M-D1）と一致しました。
- **バケットは `le` が 100・250・500・1000・2000・5000・`+Inf`** で、`+Inf` 以外のバケットがあることを p95 の式を書く前に確かめました。1000 ms は境界にあるため、1 秒の目標の割合（N7）を補間なしで出せます。今回の要求はすべて 100 ms 以内でした。
- **認可の入口（`AdminAccessDeniedHandler`）で拒否した 403 は、`uri="UNKNOWN"` の系列に入ります**（要求が道の割り当ての前に止まるため）。そのため、入口の 403 は道では分けられず、管理の API への 403 か別の管理者のみの道への 403 かは指標では区別できません。業務の層で拒否した 403（操作した人の確かめ直し、監査の `NOT_ADMIN`）だけが道の型の系列に入ります（N5）。入口の 403 の件数は、同じ時間のアプリのログの WARN「権限が足りないため要求を拒否しました」の件数（`log-queries.md` 1節の L2）と、監査の `ACCESS_DENIED` の行を併せて見ます。今回は指標の `UNKNOWN` 約 215（1 時間の補間）・WARN 219・監査 219 で合いました。
- 前の Intent で見つけた「`http_server_requests` にバケットが無く p95 が `NaN` になる」欠けは、直っていました（Intent 260928-quality-followup の FR4）。ログインの API のパネル（9）は 487.5 ms（250〜500 ms の補間）を返しました。

## 3. すべての式の確かめ（2026-10-03 22:29:34〜22:29:38）

ダッシュボードの式 24 件・警報の式 16 件（`alarms.md` 3節）と、この Intent の見方の問い合わせ 14 件（上の N1〜N8 と `log-queries.md` の L1〜L6）を、lgtm の Prometheus・Loki の API で1つずつ流しました。**54 件すべて `success`** で、書き方の誤りや名前の違いはありませんでした。ダッシュボードの式の結果：

| パネル | 取り出し先 | 結果 | 値 |
|---|---|---|---|
| 2 稼働（5xx でない応答の割合、30 日） | Prometheus | success | 1 |
| 5 要求の数（1 分あたり） | Prometheus | success | 173.989 |
| 6 5xx の割合（5 分） | Prometheus | success | 0 |
| 7 ERROR のログ（5 分あたり） | Prometheus | success | 0 |
| 9 ログインの API | Prometheus | success | 487.5 |
| 10 トークンの更新の API | Prometheus | success | （結果なし） |
| 11 確認用 API | Prometheus | success | （結果なし） |
| 13 コネクションプールの待ち | Prometheus | success | 0 |
| 14 JVM のヒープの使用率 | Prometheus | success | 0.159 |
| 15 403 の件数（1 時間） | Prometheus | success | 248.317 |
| 17〜21 監査の失敗・ロック・正規化されていないパス・送り元の不一致・監査の遅れ | Loki | success（5件とも） | （結果なし） |
| 24〜26 DSL の操作 | Prometheus | success（3件とも） | （結果なし） |
| 28・29 招待と登録の要求の数・時間 | Prometheus | success | （結果なし） |
| 30 招待と登録の 5xx の件数 | Prometheus | success | 0 |
| 32・34 メールの送信の数・時間 | Prometheus | success | （結果なし） |
| 33 失敗の種類ごとの数 | Prometheus | success | 0 |

- 「結果なし」は、直近の時間にその要求・出来事が無かったものです（トークンの更新・確認用 API・DSL・招待・メールは送っていない。Loki の5件は該当の文言が出ていない）。式としては実行できました。

## 4. 監査との突き合わせ

使い捨ての環境の内部DB の監査の件数は、送った要求の状態コードから見込んだ件数とすべて一致しました（`log-queries.md` 3節）。

## 5. 基準の値

`slo-config.md` 2節に並べました。

## 6. 片付け

`alarms.md` 5節に書きました。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/observability-setup-questions.md`（Q1〜Q4、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/dashboards.md`
- U3 の基盤の設計 `construction/u3-user-admin-api/infrastructure-design/monitoring-design.md`（1・6・8・9節）と `infrastructure-specification.md`、U4・U5 の `infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`
- U3 の NFR の設計 `construction/u3-user-admin-api/nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`・`observability-design.md`、NFR 要件 `construction/u3-user-admin-api/nfr-requirements/observability-requirements.md`（NFR5.9〜NFR5.11）
- `docker/monitoring/dashboards/mastersmith-overview.json`、`compose.yaml`（`lgtm`）、`docker/perf/compose.yaml`、`perf/README.md`、`backend/src/main/resources/application.yaml`（`management.metrics.distribution.slo`・`management.otlp.metrics.export.step`）
- `backend/src/main/java/cherry/mastersmith/useradmin/web/UserAdminController.java`・`useradmin/service/UserAdminService.java`・`useradmin/domain/RejectionPolicy.java`
- 実行したコマンドの出力: `docker compose --profile monitoring up -d --wait lgtm`、`docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait`、`docker network connect --alias lgtm mastersmith-perf_default mastersmith-lgtm-1`、`docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（式を1つずつ実行）

## Assumptions & Open Questions

None.
