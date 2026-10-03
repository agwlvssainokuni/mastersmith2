# Observability Setup — Questions

配備先が決まるまでの手元の監視は、`grafana/otel-lgtm` を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。profile `observability` は外部の送り先の見本の `otel-collector` のもので、手元の監視には使いません（`compose.yaml`）。

今の監視の設定にあるもの：
- ダッシュボード `docker/monitoring/dashboards/mastersmith-overview.json`：行は「仮の目標」「稼働とエラー」「応答時間（95 パーセンタイル）」「資源」「認証・拒否・監査」「DSL の操作」「招待と登録」「メールの送信」。利用者の管理の API（`/api/admin/users` の7つ）は、全体のパネル（要求の数・5xx の割合・ERROR のログ・コネクションプールの待ち・403 の件数・監査の書き込みの失敗と遅れ）にだけ含まれます。
- 警報の決まり `docker/monitoring/provisioning/alerting/mastersmith.yaml`：16 個（`ms-app-absent`・`ms-5xx-ratio`・`ms-error-logs`・`ms-audit-fail`・`ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`・`ms-pool-pending`・`ms-heap`・`ms-forbidden`・`ms-lock`・`ms-rejected`・`ms-origin`・`ms-audit-slow`・`ms-cleanup-fail`・`ms-bcrypt`）。この Intent では変えていません（U3 の `code-summary.md` 1節）。

この段で確かめるとされたこと（Build and Test の `Unverified` のうち持ち主が observability-setup のもの、U3 の基盤の設計の `monitoring-design.md` 8節）：
- U3-NFR5.9: 7つの API の `uri`・`method`・`status` のラベルの実際の値と、`le` のバケット
- U3-NFR5.10: 「拾う」と書いた既存の警報が、状態コードとログのレベルで U3 の失敗を数えること、しきい値を超えたときに鳴ること
- U3-NFR5.11: SLO は決めず、基準の値を記録すること（performance-validation と共同）
- 監査の種類5つと理由の名前（B4 の後の定義で）
- U5-NFR5.1・NFR5.2（画面の時間）の運用の中の判定（performance-validation・feedback-optimization と共同）

## 決まっていること・読み取りで確かめたこと（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 新しい警報は足さない。409（`USER_ADMIN_BUSY`・`USER_ADMIN_LAST_ADMIN` など）は警報にしない。7つの API の p95 の警報も足さない（目標の判定は performance-validation の k6） | U3 の NFR5.10、U3 の `monitoring-design.md` 2節、`observability-requirements.md` の「選ばなかったもの」 |
| `ms-pool-pending` の式を時間切れの累計（`hikaricp_connections_timeout_total`）の増加を見る式に見直すことは、この段では行わない。配備先が決まったときの申し送りのまま `alarms.md` に記録する | `construction/code-generation/gate-decisions.md` 4節、U3 の `code-summary.md` 9節 |
| SLO は決めず、判定は `Unverified`。この段の確かめの時点の値を基準の値として並べ、配備先が決まったときの測り方を書く。目標を緩めて満たしたことにはしない | U3 の NFR5.11、`project.md` の Deployment・Testing Posture |
| 画面の時間（U5 の NFR5.1・NFR5.2）は、配備したアプリでは測らない（`./gradlew e2eTest` の 120 の中でだけ記録）。運用の中の判定は `Unverified` のまま feedback-optimization に引き継ぐ。U4 の画面の時間には SLO を置かない | U5・U4 の `monitoring-design.md` 3節 |
| 式は、書く前に実際に起動して指標・ラベル・ログの項目の名前を確かめ、書いた後にすべての式を流す。p95 の式は、`+Inf` 以外のバケットがあることを先に確かめる（`application.yaml` の `management.metrics.distribution.slo` は `http.server.requests` に 100・250・500・1000・2000・5000 ms を持つ。1000 ms は境界にある） | `project.md` の Corrections・Deployment の学び |
| 数の値を確かめる要求は、指標の送信の周期（`management.otlp.metrics.export.step` の 60s）を複数またいでくり返し送る（新しい系列の最初の 1 件は `increase` に数えられない） | `project.md` の Deployment の学び |
| 確かめのために送る要求が監査に残る・データを変えるときは、送る前に依頼者に伝える | `project.md` の Corrections |
| 監査の種類と理由の名前は、`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`・`AuditFailureReason.java` で読み取りで確かめた。種類は `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET`、U3 で足した理由は `SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`（既存の `USER_NOT_FOUND`・`NOT_ADMIN` も使う）。U3 の `monitoring-design.md` 5節と一致する。実際の行での確かめは Q1 で選ぶ環境で行う | ソースの読み取り（2026-10-03） |
| 配備したアプリでは、スモークテストの間に依頼者が画面で行った操作で `USER_ADMIN_GRANTED` 2・`USER_ADMIN_REVOKED` 1・`USER_SUSPENDED` 1・`USER_RESUMED` 1 が記録済み。`LOGIN_FAILURES_RESET` と失敗の理由の行は、配備したアプリではまだ見ていない | `operation/deployment-execution/smoke-test-results.md` の S5 |
| 後の Intent の持ち物（Q-H 一意の制約の違反の例外の文、出力を捕まえるテストの範囲の弱さ、N-19 閉じた後のフォーカス）は、監視の警報・パネルに足さない。Q-H は TRACE のときにだけ出うるもので、既定のログのレベルでは拾う対象が無い | `construction/code-generation/gate-decisions.md` 4節 |
| 409 `USER_ADMIN_BUSY` の WARN（同じトレースID の排他の口の WARN と結び付く）と、監査の `failure_reason` が `LAST_ACTIVE_ADMIN` の行の見つけ方は、警報ではなく `log-queries.md` の問い合わせとして書く | U3 の `monitoring-design.md` 4節・7節 |
| トレースの設定は変えない（Micrometer Tracing、`MASTERSMITH_TRACING_SAMPLING_PROBABILITY` の既定 1.0）。新しいスパン・属性は無い | U3 の `monitoring-design.md` 4節 |
| 警報の通知の先（contact point）は置かない。手元の Grafana の警報の一覧で見る | `project.md` の Deployment |

読み取りだけで確かめた今の実行環境（2026-10-03）：
- colima は CPU 4・メモリ 6GiB で動いている。動いているコンテナは `mastersmith-app-1`（`mastersmith:local`、healthy）・`mastersmith-mailpit-1`・`mastersmith-targetdb-postgres-1`。lgtm は止まっている（ボリューム `mastersmith_mastersmith-monitoring` は残っていて、前の Intent の確かめのデータを含む）。
- 配備した `.env` には、外部エクスポートの2行（`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED`・`_ENDPOINT`）がコメントの形で 2 行あり、有効な行は 0 行（値は表示していない。行の数だけを数えた）。
- メモリの上限の合計: 配備したアプリ 2g・Mailpit 256m・見本の対象DB 512m・lgtm 1536m で約 4.3GB。使い捨ての環境のアプリ（既定 2g、前の Intent は 1g で起動）を足すと、VM の 6GiB に近づく。

## Q1. 名前と数を確かめるために要求を送る環境

7つの API のラベル（`uri`・`status`）と `le`、監査の行の種類と理由を確かめるには、実際に要求を送る必要があります。5つの操作は監査に残り、利用者の状態を変えます。氏名と言語の変更はデータを変えます。理由の行（`SELF_OPERATION`・`NO_CHANGE`・`TARGET_SUSPENDED`・`LAST_ACTIVE_ADMIN`・`USER_NOT_FOUND`・`NOT_ADMIN`）を出すには、管理者が2人以上と管理者でない利用者が要ります。配備した環境の利用者は 2 人です。

A. 使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の利用者、アプリのメモリの上限 1g）を起動して lgtm に送らせる。7つの API と、5つの操作の成功・各理由の失敗を、周期をまたいで 3 分以上くり返し送る。監査の行は、その環境の内部DB の複写を読み取りで開いて数える。終わったら環境を消す。配備したアプリには確かめの要求を送らない（推奨）
B. 配備したアプリに向けて、依頼者が画面から一覧・氏名の変更・5つの操作を行う。理由の失敗の行は、配備した環境で作れるもの（`SELF_OPERATION`・`NO_CHANGE` など）だけにする。監査とデータに確かめの操作が残る
C. A に加えて、配備したアプリでは依頼者が一覧を開くだけ（監査に残らない GET）を行い、配備した環境の `uri` の値も確かめる
X. Other (please specify)

推奨の理由: 前の Intent の同じ段（Q5: C）と `project.md` の「本物のデータと監査ログを汚さない」に合い、理由の行をすべて作れるため。

[Answer]: A

## Q2. 「拾う」と書いた警報が鳴ることの確かめの範囲

U3 の NFR5.10 は、既存の警報が U3 の失敗を数えることに加えて、しきい値を超えたときに鳴ることまで確かめるとしています。読み取りで確かめたところ、`ms-5xx-ratio`・`ms-error-logs`・`ms-forbidden`・`ms-pool-pending` の式は道（`uri`）で絞らないため、U3 の道の要求も数に入ります。ただし、U3 の経路で 5xx・ERROR のログ・監査の書き込みの失敗と遅れを、本番のコードを変えずに起こす手は限られます。performance-validation の上限 10 の (B) の場面（`perf/README.md`、`userAdminOps` を `VUS=10`）は、2本目の接続の待ちを起こし、欠けた監査と ERROR を出す見込みの場面です。

A. この段では、使い捨ての環境で管理者でない利用者から管理の API へ 403 を直近 1 時間に 21 件以上送り、`ms-forbidden` が鳴ることを確かめる。`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` が鳴ることは、performance-validation の上限 10 の (B) の場面で lgtm を起動したまま確かめる（持ち主を performance-validation に移し、`alarms.md` に書く）。`ms-5xx-ratio`・`ms-audit-slow` は、U3 の道の要求が式の数に入ることを式の評価で確かめ、鳴ることは `Unverified` として記録する（推奨）
B. A の内容をすべてこの段で行う。使い捨ての環境のプールの上限を 10 にして `userAdminOps` を `VUS=10` で lgtm を起動したまま流す。`ms-5xx-ratio`・`ms-audit-slow` は、しきい値を下げた警報の決まりの写しを lgtm に一時的に読み込ませて鳴らし、終わったら元のファイルで読み込み直す（U3 の経路で 5xx・遅れを起こせないため、確かめられるのは鳴る仕組みだけで、しきい値の値そのものでは鳴らない。この段の中で負荷をかけ、VM のメモリが詰まる）
C. 式の評価で数えることだけを確かめる。鳴ることはすべて `Unverified` として feedback-optimization に引き継ぐ
X. Other (please specify)

推奨の理由: 実際の失敗で鳴らせるものは実際の失敗で確かめ、負荷を伴う確かめは負荷の段（同じ場面を流す performance-validation）にまとめると、配備したアプリを止める回数とメモリの詰まりを増やさないため。

[Answer]: A

## Q3. 利用者の管理の API のパネル

承認済みの設計（U3 の NFR5.10、`monitoring-design.md` 6節）は「既存のダッシュボードは変えない。管理の API の専用のパネルは、配備先が決まったときに SLI と合わせて考える」としています。一方、`http.server.requests` にはバケットがあるため、7つの API の p95 を引く式は今は書けます。

A. パネルを足さない。7つの API は全体のパネルで見て、道ごとの値の見方は `log-queries.md`・`dashboards.md` に Explore の問い合わせとして書く（設計どおり、推奨）
B. 「利用者の管理（Intent 260930-user-admin の U3）」の行を足す（7つの API ごとの要求の数・p95（`http_server_requests_milliseconds_bucket`）・409 と 5xx の件数）。NFR5.10 との差を `dashboards.md` に記録する
C. B に加えて、「招待と登録」の行の p95 を、トレースから作る指標（`traces_spanmetrics_latency`、2 倍刻み）から `http_server_requests_milliseconds_bucket` に置き換える（この Intent の設計の外の変更）
X. Other (please specify)

推奨の理由: 承認済みの設計で決まっていて、管理の操作はまれなため、配備先が決まったときに SLI と合わせて決めても失うものが小さいため。

[Answer]: A

## Q4. 配備したアプリの外部エクスポート

前の Intent では、この段の確かめの間だけ配備した `.env` の2行のコメントを外し、終わったらコメントに戻してアプリを起動し直しました（2回の作り直し）。Q1 で A を選ぶと、名前と数の確かめには配備したアプリの送信は要りません。

A. 有効にしない。配備したアプリは作り直さず、基準の値は使い捨ての環境の値と、配備の記録の値（`deployment-log.md`・`health-check-report.md`）を並べる（推奨）
B. この段の確かめの間だけ有効にする（コメントの切り替え、前の Intent と同じ）。配備したアプリの何もしていないときの値（ヒープの使用率・プールの待ち・`ms-app-absent` が鳴らないこと）を基準の値として記録する。アプリの作り直しは2回
X. Other (please specify)

推奨の理由: 配備したアプリを止めずに済み、基準の値の目的（配備先が決まったときに比べる）には使い捨ての環境の値で足りるため。

[Answer]: A

## Consolidated Summary Confirmation

- 冒頭の「決まっていること」のとおり、新しい警報は足さず、409 と利用者の管理の API の p95 も警報にしない。`ms-pool-pending` の式の見直しは配備先が決まったときの申し送り。SLO は決めずに Unverified とし、基準の値を並べる。トレースの設定と警報の通知の先は変えない。
- 指標・ログ・監査の名前と数は、使い捨ての環境（`docker/perf/compose.yaml`）を手元の監視（profile `monitoring` の lgtm）に送らせて確かめる。利用者の管理の7つの API と、5つの操作の成功・各理由の失敗を、送信の周期をまたいで 3 分以上くり返し送る。監査は使い捨ての環境の内部DB の複写を読み取りで開いて数える。配備したアプリには送らない（Q1: A）。
- 警報が鳴ることは、`ms-forbidden` をこの段で 403 を送って鳴らして確かめる。`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` は Performance Validation の負荷の場面で lgtm を動かしたまま確かめる（持ち主を移す）。`ms-5xx-ratio`・`ms-audit-slow` は式の評価で U3 の要求が数に入ることだけを確かめ、鳴ることは Unverified（Q2: A）。
- 利用者の管理の API のパネルは足さず、見方を `log-queries.md` などに問い合わせとして記録する（Q3: A）。
- 配備したアプリの外部エクスポートは有効にしない。基準の値は使い捨ての環境の値と配備の記録を並べる（Q4: A）。
- 式は書く前に起動して名前を確かめ、書いた後にすべての式を流す。使い捨ての環境とアプリのメモリ（VM 6GiB）に気を付け、終わったら使い捨ての環境と lgtm を止める（lgtm は元どおり止めた状態に戻す）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
