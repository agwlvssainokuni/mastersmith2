# Performance Validation — 質問

Intent `260930-user-admin` の性能の目標を、負荷をかけて確かめるための質問です。前の Intent の同じ段（`aidlc/spaces/default/intents/260925-user-management/operation/performance-validation/`）の進め方と `perf/README.md` の手順を正とし、今回の差だけを質問にします。

## 決まっていること・読み取りで確かめたこと（質問にしない）

### この段が持ち主の項目

- **Build and Test から引き継いだ Unverified は 16 件**です（`construction/build-and-test/build-and-test-summary.md` のうち performance-validation の分）。
  - 同時 10 件の p95 1 秒（k6 の値を正とする）:
    - U3 の NFR5.1（一覧。利用者 1,000 名で区分 a〜d ごと）、NFR5.3（氏名と言語の変更）、NFR5.4（5つの操作を操作ごと）、NFR5.5（止める操作の悪い側。未無効 100 件・無効 1,000 件）、NFR5.6（目標の負荷で BUSY 0 件）
    - U1 の NFR5.2（U3 の NFR5.5 と同じ場面）
    - 既存の目標を保つこと: U1 の NFR5.1b（ログイン・更新）、U4 の NFR5.1b（更新）、U2 の NFR5.1b（招待の一覧の1ページ目と最後のページ）
  - 接続プール: U3 の NFR6.2（上限 30 で `userAdminPool`）、NFR6.3（上限 10 で (A) 同時 5・(B) 同時 10）、U1 の NFR6.1b（既存のログインの負荷でプールの使い方が変わらない）
  - 規模: U1 の NFR5.4、U3 の NFR5.8（上の場面の結果で判定する）
  - 画面の時間の本番での判定: U5 の NFR5.1（一覧 2 秒）・NFR5.2（次のページ 1.5 秒）。observability-setup・feedback-optimization と共同の持ち主（Q4）
  - U3 の NFR5.11（SLO の基準の値）は observability-setup と共同。この段の k6 の p95 を基準の値として記録します。
- **Observability Setup から移された項目**（`operation/observability-setup/alarms.md` 2節、`operation/incident-response/runbooks.md` の RB-24・RB-25）:
  - `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` が U3 の失敗で鳴ること。上限 10 の (B) の場面で、手元の監視を起動したまま確かめる予定になっています（Q3）。
  - 409 `USER_ADMIN_BUSY` を実際に起こしたときの2行（L3 の応答の変換と L4 の排他の WARN）の `traceId` での結び付き（`log-queries.md` 2節）。

### 前の Intent と project.md で決まっている進め方

- 負荷の試験は、配備した環境とは別の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト名 `mastersmith-perf`、仮の署名鍵・仮の管理者・試験用の利用者、終わったら `down -v` で消す）で行います。本物のデータと監査ログには触れません。
- 閾値は出典の値のままで、緩めません（p95 1000 ms と `checks` の率 1）。届かないときは Not Met とし、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録し、承認の場で相談します。一覧が NFR5.1 に届かなければ、`users (created_at, user_id)` の索引を足す直しを諮ります（`perf/README.md`）。
- 台本の全体を `caffeinate -i` で包みます（場面ごとに起こし直さない）。遅れが出たら `pmset -g log` でスリープを確かめます。
- 最初に `k6 inspect --include-system-env-vars` を流し直し、5つの場面と既存の場面が読み込めることを確かめてから測ります（Build and Test の申し送り）。これは配備したアプリを止める前に行えます。
- 接続プールは、使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`hikaricp.connections.timeout`（待ちの時間切れの累計）と `hikaricp.connections.acquire`（借りるまでの待ちの最大）で判断します。数秒ごとの使用中の数では判断しません。上限に届く形（上限 10 の (A)・(B)）を必ず含めます。
- 監査の件数の突き合わせは、アプリを止めて内部DB を読み取りで開いて行い、数え終わるまで環境を消しません。場面を分けるため、上限 10 の場面に入る前（上限を変えて起動し直すためアプリを止める時点）に一度読み、(A) の後は時間切れの累計と ERROR のログの件数を止めずに読み、最後にもう一度読みます。
- 止める悪い側（`userAdminSuspendWorst`）は対象のトークンを使い切るため、流し直すときは環境を作り直します。
- 秘密が出ていないこと（試験用の利用者の名前の部分・招待のリンク・メールアドレス・`MVStoreException`）を、ログと k6 の結果の件数だけで確かめます（値は表示しない）。
- 5つの操作の準備のログインの失敗（`{name:userAdminPrepLogin}`）は p95 と `checks` に数えません。

### 読み取りで確かめた今の環境（2026-10-03）

- colima の VM は CPU 4・メモリ 6GiB（`docker info` で 4 CPU / 約 5.8GiB）です。
- 動いているもの: 配備したアプリ（`mastersmith-app-1`、`mastersmith:local` = `f386b55f16a8`、上限 2g、healthy）、配備した環境の Mailpit、見本の PostgreSQL（512MiB）。手元の監視（`mastersmith-lgtm-1`）は止まっています。使い捨ての環境のボリュームはありません。
- `cc28d1f`（配備した版）の後に `aidlc/` の外の変更はありません。そのため、使い捨ての環境のイメージは、作り直さずに配備と同じ `mastersmith:local`（`f386b55f16a8`）を使います（`perf/README.md` の手順 0 の作り直しは行いません。配備したイメージのタグを上書きしないため）。
- `perf/` と `docker/perf/` は B4（`4bd600d`）の後に変わっていません。
- サーバー側の `http_server_requests` には、Intent 260928-quality-followup の直しでバケット（`le` 100〜5000）があります（`observability-setup/dashboards.md` 2節）。手元の監視を動かしていれば、サーバー側の p95 を参考として並べられます。

### 今回の差（手順書だけでは決まらない点）

- `perf/README.md` の「利用者の管理の場面」（手順 2''）は、利用者の管理の試験用の利用者だけを入れ、Mailpit を起動しません。既存の `loginSuccess`・`refresh` が使う `perf-user01`〜`10` と、`invitationList` の用意（招待を出し、送信の結果 `SENT` を求めるため Mailpit とメールの設定が要る）は、この手順に含まれていません（Q2）。
- `ms-pool-pending` は「待ちの数が 0 を超える状態が 1 分」の警報で、指標の送信は 1 分ごとです。既定の (B)（`VUS=10`・1つの VU が 10 回）は短く終わる見込みで、そのままでは鳴らない見込みです（`alarms.md` 2.1節）。
- BUSY は、(B) でも起きない見込みです。2本目の接続は確定の後に借りるため、待つ間に行の排他を持っていないからです。本番のコードを変えずに起こす手は見つかっていません。

## Q1. 試験の間の配備したアプリ

A. k6 を流す間は配備したアプリを止め（`docker compose stop app`。見本の PostgreSQL と配備した環境の Mailpit は動かしたまま）、終わったら `docker compose start app` で起動し直して healthy を確かめる。止める直前と起動し直す前にお知らせする。止まっている時間は 45〜60 分ほど（推奨）
B. 配備したアプリは止めない（待機中のため CPU の取り合いは小さいが、測った値に影響が混ざりうることを記録する。Q3 で手元の監視も動かすと、VM のメモリが上限に近づく）
X. Other (please specify)

[Answer]: A

推奨の理由: CPU 4 を分け合わずに測れ、前の Intent と同じ条件で比べられます。手元の監視（1.5GB）を同時に動かす分のメモリの余裕も、配備したアプリを止めることで作れます。

## Q2. 既存の場面（`loginSuccess`・`refresh`・`invitationList`）の流し方

U1 で停止の判定をログイン・更新・アクセストークンの認証に足し、U2 でページ送りを共通に移したため、既存の3つの場面は流し直して目標を確かめる必要があります。

A. 1つの使い捨ての環境にまとめる。profile `mail` で Mailpit ごと起動し、`app.env` にメールの受け手・差出人・ベース URL の行（`perf/README.md` の手順 1'）を足し、手順 2'' の SQL に `perf-user01`〜`11` を加える。利用者 1,000 名とトークンの行 110,000 件が入った、内部DB が膨らんだ状態（悪い側の条件）のまま3つの場面を先に流し、`loginSuccess` の前後で hikaricp の値を読む（U1 の NFR6.1b）（推奨）
B. 別の使い捨ての環境（`perf/README.md` の「利用者の設定と招待の場面」の手順 2'）を作り直して、3つの場面だけを流す。手順書のままの形になるが、環境を2回作るため 10 分ほど延び、空に近い内部DB で測ることになる
X. Other (please specify)

[Answer]: A

推奨の理由: 用意が1回で済み、軽い API は内部DB が膨らんだ悪い側の条件で測る決まり（`project.md` の Testing Posture）にも合います。

## Q3. 警報が鳴ることの確かめ（`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail`）と手元の監視

A. 上限 10 の場面（(A)・(B)）だけ手元の監視を使う。上限を 10 にして使い捨てのアプリを作り直すついでに、外部エクスポートを使い捨てのアプリにだけ有効にし（送り先 `http://lgtm:4318`）、`lgtm` を起動して使い捨ての環境の網に別名 `lgtm` でつなぐ（Observability Setup と同じ形。終わったら外して止める）。(B) は 1つの VU の回数（`UA_ROUNDS`）を増やして 5 分以上続け、3つの警報が `Alerting` になるまで（最長で終わってから 5 分）見る。p95 を判定する上限 30 の場面は、前の Intent と同じく手元の監視を止めたまま測る。BUSY は (B) で起きたときだけ2行の結び付きを確かめ、起きなければ Unverified のまま feedback-optimization へ渡す（推奨）
B. すべての場面で手元の監視を動かし、外部エクスポートも全体で有効にする。サーバー側の p95 を k6 の値の隣に参考として並べられ、SLO の基準の値もサーバー側で取れる。代わりに lgtm（1.5GB）とエクスポートが CPU とメモリを分け合い、p95 に影響が混ざりうる（VM の使用は 5GB ほど）
C. 警報が鳴ることは確かめず、(B) の時間切れの累計・ERROR のログの件数・欠けた監査の件数だけを記録する。3つの警報は Unverified のまま feedback-optimization へ渡す
X. Other (please specify)

[Answer]: A

推奨の理由: 判定の値（上限 30 の p95）は前の Intent と同じ条件のまま保ち、警報は失敗を起こす (B) の場面で、送信の周期（1 分）を複数またぐ長さで確実に確かめられます。

## Q4. 画面の時間の本番での判定（U5 の NFR5.1・NFR5.2）

Build and Test では、E2E（WAR、空に近い内部DB）の中の測りで、一覧 最大 103 ms・次のページ 最大 80 ms を記録だけしました。本番での判定は、この段・observability-setup・feedback-optimization の共同の持ち主です。

A. この段では測らず、E2E の値を参考として記録し、本番での判定は Unverified のまま feedback-optimization（配備先が決まったとき）へ渡す。利用者 1,000 名の一覧の API の時間は U3 の NFR5.1 の k6 で押さえる（推奨）
B. 配備したアプリ（Q1 で起動し直した後）を依頼者がブラウザで開き、「利用者の管理」を選んでから一覧の最初の行が出るまでと、「次へ」を押してから2ページ目の行が出るまでを、開発者ツールで 5 回ずつ測って記録する（配備した内部DB の利用者の数のまま。次のページは利用者が 21 名以上いるときだけ測れる）
X. Other (please specify)

[Answer]: A

推奨の理由: 配備先が PC 上のコンテナのままで本番の条件（端末・回線・利用者の数）が無く、測った値が判定の根拠になりにくいためです。目標は緩めず、持ち主を明記して引き継ぎます。

## 出典

- `aidlc/spaces/default/intents/260930-user-admin/construction/build-and-test/build-and-test-summary.md`（Unverified の一覧と5節の申し送り）・`performance-test-instructions.md`・`test-results.md` 5節
- `aidlc/spaces/default/intents/260930-user-admin/construction/u3-user-admin-api/nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`、`construction/u5-user-admin-ui/nfr-requirements/performance-requirements.md`、`construction/u3-user-admin-api/code-generation/code-summary.md`
- `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/alarms.md`・`dashboards.md`・`log-queries.md`・`slo-config.md`、`operation/incident-response/runbooks.md`
- `aidlc/spaces/default/intents/260925-user-management/operation/performance-validation/`（前の Intent の同じ段）
- `perf/README.md`・`perf/k6/scenarios.js`・`docker/perf/compose.yaml`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`（読み取り）
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`（Testing Posture・Deployment）
- 読み取りだけの確かめ: `colima list`・`docker info`・`docker ps -a`・`docker images`・`docker volume ls`、`git log cc28d1f..HEAD -- . ':!aidlc'`（0 件）

## Consolidated Summary Confirmation

- 冒頭の「決まっていること」のとおり、使い捨ての環境で測り、終わったら消す。イメージは作り直さず、配備と同じ `mastersmith:local` を使う（`perf/README.md` の手順 0 との差として記録する）。目標は緩めず、届かなければ Not Met とし原因を確かめて依頼者に相談する。`caffeinate -i` で台本の全体を包み、最初に `k6 inspect --include-system-env-vars` を流し直す。
- k6 を流す間は配備したアプリを止め（45〜60 分）、終わったら `docker compose start app` で起動し直して healthy を確かめる。見本の PostgreSQL と配備した環境の Mailpit は動かしたまま（Q1: A）。止める直前にもう一度依頼者に知らせる。
- 既存の場面（`loginSuccess`・`refresh`・`invitationList`）は、利用者の管理の場面と1つの使い捨ての環境にまとめ、内部DB が膨らんだ悪い側の条件のまま流す。足りない試験用の利用者と Mailpit を足す（Q2: A）。
- 警報（`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail`）が鳴ることは、接続プールの上限 10 の (A)・(B) の場面だけ手元の監視を動かして確かめ、(B) を 5 分以上続ける。p95 を判定する上限 30 の場面は手元の監視を止めたまま測る。BUSY は起きたときだけ2行の結び付きを確かめ、起きなければ Unverified のまま feedback-optimization へ渡す（Q3: A）。
- U5 の画面の時間はこの段では測らず、E2E の値を参考に記録して Unverified のまま feedback-optimization へ渡す（Q4: A）。
- 結果は `load-test-plan.md`・`test-results.md`・`nfr-validation-matrix.md` に記録する。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
