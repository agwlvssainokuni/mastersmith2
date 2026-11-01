# Observability Setup — Questions

Intent 261029-reference-lookup-cleanup（scope classic、深さ Standard）の Observability Setup の質問です。配備先が決まるまでの手元の監視は、`grafana/otel-lgtm`（0.38.0）を compose の profile `monitoring` で見たいときだけ起動します。ダッシュボードと警報の決まりは、ファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

各問いは、選択肢の前に推奨とその理由を書いています（`team.md` の Corrections）。

## 1. この段で確かめることになっているもの

### 1.1 Build and Test から引き継いだ `Unverified`（持ち主 observability-setup）

出典は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/build-and-test/build-and-test-summary.md` です。

| 行 | 中身 |
|---|---|
| OBS-PANELS | ダッシュボードのパネル 46・47 と区画の説明の直し。警報 `ms-biz-target-db` が、参照の道・定義の道・保存前の確かめの 503 で firing になること |
| U1-NFR2.10 | 参照の道 `POST /api/tables/reference/rows` が業務データの区画の式（`uri=~"/api/tables/.*"`）に入ることを確かめ、区画の説明に足す。書く前に `uri` の値と `+Inf` 以外のバケットを確かめる |
| U1-NFR3.8 | 参照の読み取りの 503（`TIMEOUT`・`UNAVAILABLE`）を既存の `ms-biz-target-db` で拾う。定義の取得の 503 も同じ |
| U2-NFR2.29 | 参照つきの作成・更新（`/api/tables/rows`）が既存の区画に入ることを確かめる（新しい区画・警報は足さない） |
| U2-NFR3.28 | 保存前の確かめの 503 を既存の `ms-biz-target-db` で拾う |
| REQ-NFR2・REQ-NFR3 | 上の単位の行がそろえば、監視の部分が埋まる（REQ-NFR2 の性能の判定の部分は Performance Validation） |

### 1.2 基盤の設計（`monitoring-design.md`）からの引き継ぎ

| 出典 | この段ですること |
|---|---|
| U1 の 2節・6節 | パネル 46（参照の API の要求の数）・47（参照の API の 503 の件数）を、`uri` の実際の値を確かめてから式を確定して `docker/monitoring/dashboards/mastersmith-overview.json` に書く（今は仮の式。R-02）。パネル 40・42 の説明の直し。3,000 ms のバケットを足すかを依頼者に諮る。Loki の問い合わせのキーの確かめ |
| U2 の 2節・6節 | パネル 42・43 の説明に、保存前の確かめの 503・409 を足す（U1 の直しと同じ回）。参照先に無い値の作成で 409 がパネル 43 に出て、警報が鳴らず、診断の WARN が出ないこと。U1 の WARN と U2 の WARN の見分け方 |
| U3 の「後の段に回すこと」 | 画面からの操作でも系列が入ることを1回見る（任意） |
| U5 の 7節 | 警報の決まり（17）が lgtm 0.38.0 で読み込まれ、鳴ることの確かめ |

### 1.3 読み取りだけで確かめた今の状態（2026-11-01）

- ダッシュボードのファイルには、パネル 46・47 はまだ無い（最後のパネルは id 45「対象DB の書き込みのプール」、y 98）。区画の行は id 39「業務データ（Intent 261016-business-data-views）」。
- 警報の決まりは 17（`ms-biz-target-db` を含む）。`docker/monitoring/` に、この Intent の変更はまだ無い。
- 道の定数（`backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataPaths.java`）: `DEFINITION` は `/api/tables/definition`、`REFERENCE_ROWS` は `/api/tables/reference/rows`。変数を持たない道のため、`uri` のラベルもこの値になる見込み（実際の値は起動して確かめる）。
- `http.server.requests` のバケット（`backend/src/main/resources/application.yaml` の `management.metrics.distribution.slo`）は `100ms,250ms,500ms,1000ms,2000ms,5000ms`。3,000 ms の境界は無い。
- 診断の WARN（コードで確かめた）:
  - 参照の読み取り（`ReferenceLookup` のロガー）: キー `purpose`（値 `REFERENCE`）・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`。文は「対象DB の参照先を読めませんでした」。
  - 保存前の確かめ（`BusinessDataWriteService` のロガー）: キー `purpose`（値も `REFERENCE`）・`stage`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`。文は「保存の前に対象DB の参照先を読めませんでした」。
  - 2つは `purpose` の値が同じため、見分けはロガー（Loki では `scope_name` の見込み）か `stage` のキーの有無で行う。実際のログで確かめる。
- 定義の取得（`purpose` が `CREATE`・`UPDATE`）は参照先を読むため、対象DB が止まると 503 になる（`BusinessDataReadService.definition`）。前の Intent の確かめでは、定義は対象DB に接続しないため 200 だった。今回は参照の項目を持つ表で、定義の取得も 503 の数に入る見込み。
- 実行の環境（`colima list`・`docker ps -a`・`docker stats --no-stream`・`free -m`）:
  - colima は CPU 4・メモリ 6GiB。VM の `available` は 4,818 MiB。
  - 動いているコンテナ: `mastersmith-app-1`（`mastersmith:local` = `d8a467b6`、healthy、使用 602 MiB / 上限 2g）・`mastersmith-targetdb-postgres-1`（26 MiB / 512m）・`mastersmith-mailpit-1`（12 MiB / 256m）。
  - `mastersmith-lgtm-1`（0.38.0）は止まっている（30 時間前に Exited）。
  - 上限の合計: 配備した3つで 2,816 MiB。前の Intent と同じ形の使い捨ての環境（アプリ 1g・PG 512m・lgtm 1,536m）を足すと 5,888 MiB で、VM の上限（docker から見える約 5,910 MiB）にほぼ並ぶ。Mailpit を確かめの間だけ止めると 5,632 MiB。実際の使用は上限よりずっと少ない。

## 2. 決まっていること（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 新しい警報は足さない。参照の 503・保存前の確かめの 503 は既存の `ms-biz-target-db` で拾う。409 `BUSINESS_REFERENCE_NOT_FOUND` は警報にしない | U1 の 3節（NFR2.10・NFR3.8・Q2: A）、U2 の 3節（NFR2.29・NFR3.28）、U3 の 2節 |
| パネルは 46・47 の2つだけを、既存の区画（行 id 39）の下に足す。新しい区画の行は作らない。置き場は y 106、x 0 と x 8、w 8・h 8 | U1 の 2節 |
| パネル 40・42 の説明の直し（U1）と、42・43 の説明の追記（U2）を、同じ回にまとめて書く。式・位置・種類は変えない | U1 の 2節、U2 の 2節 |
| パネル 46 の説明には、定義の取得が `VIEW` を含む（参照だけの量とは言い切れない）ことを、式を確定した後も残す | U1 の「承認の場の決定」R-02 |
| 式の名前・ラベル・`uri` の値・`le` のバケットは、書く前に起動して確かめる。書いた後にすべての式を流す。p95 の式は、先に `+Inf` 以外の `le` があることを確かめる。NaN を「要求が無いため」と決めつけない | `project.md` の Corrections・Deployment の学び |
| 数を確かめる要求は、指標の送信の周期（60 秒）を複数またいで、待ちを入れた少ない数で送る（負荷の試験の場面ではなく）。前の Intent で、待ちの無い場面を 2 VU で流して lgtm の受け手が止められた | `project.md` の Deployment の学び（perf/README.md の履歴 H27） |
| lgtm を網につないだ後に、使い捨てのアプリを起動する | `project.md` の Deployment の学び |
| 確かめの要求は配備したアプリに送らない。配備した内部DB・見本の PG・監査の行は変えない。配備したアプリの外部エクスポートは有効にしない（作り直さない） | `project.md` の Testing Posture・Deployment、前の Intent の同じ段 |
| SLO は緩めない。手元の監視を常に動かしていない間は、手元の監視での判定を `Unverified` とし、この段の値を基準の値として並べる。p95 の合否は Performance Validation の k6（`iteration_duration`） | `project.md` の Deployment の学び、各 `monitoring-design.md` 4節 |
| ログの収集・トレースの設定は変えない。新しいスパンを足さない | U1・U2 の 5節 |
| 警報の通知の先（contact point）は置かない。手元の Grafana の警報の一覧で見る | `project.md` の Deployment |
| 確かめの後は、結果を見て記録してから、使い捨ての環境を消し、lgtm を元の止めた状態に戻す | 前の Intent の同じ段、`project.md` の学び |
| k6 の結果の json を記録（`evidence/`）に置くときは、JWT の形を伏せ、`gitleaks dir` で確かめる | `project.md` の Forbidden・学び |

## Q1. 名前と数を確かめるために要求を送る環境

参照の API を送るには、参照の項目を持つ DSL・ロール・作業ロールと、対象DB（読み取りと書き込みのアカウント）が要ります。`perf/README.md` の「業務データの場面」（1B〜4B、`bizPrepare`）と「他テーブル参照の読み取りの場面」の種（`biz_code200`・`biz_refpick`・`biz_refcreate` など、値はすべて架空）でこの用意ができます。

**推奨: A**。理由:
- 前の Intent の同じ段（Q1: A）と、`project.md` の「本物のデータと監査ログを汚さない」に合います。U1・U2 の基盤の設計の確かめ方（使い捨ての環境で対象DB を止める）とも同じです。
- 配備したアプリを止めずに済みます。上限の合計（Mailpit を止めて 5,632 MiB）は VM に収まり、今の実際の使用（配備したアプリ 602 MiB）は上限よりずっと少ないです。
- 使い捨ての lgtm は 0.38.0 のため、警報の決まりが 0.38.0 で読み込まれ鳴ること（U5 の 7節）も同じ回で確かめられます。

A. 使い捨ての環境（`docker/perf/compose.yaml`、`-p mastersmith-perf`）を起動します。
   - アプリは配備と同じイメージ `mastersmith:local`（`d8a467b6`）・メモリの上限 1g、仮の署名鍵・仮の管理者・試験用の利用者 `perf-biz-01` を使います。
   - 対象DB は使い捨ての PG（上限 512m）だけで、種は `perf/biz/make-biz-seed.sh`（業務データの 100,000 行と参照の表）です。MySQL・MariaDB は使いません（指標とログの名前は対象DB の種類によらないため）。
   - 外部エクスポートはこのアプリにだけ有効にし、lgtm を使い捨ての環境の網に別名 `lgtm` で先につなぎます（画面は 127.0.0.1:13000）。ダッシュボードのファイルも読み込ませ、パネル 46・47 が Grafana に読み込まれることも確かめます。
   - 配備したアプリ・見本の PG は止めません。Mailpit は確かめの間だけ止めます。
   - 終わったら使い捨ての環境と使い捨ての lgtm を消します。所要は 1 時間ほどの見込みです
B. A と同じですが、配備したアプリも確かめの間だけ止めます。メモリの余裕は大きくなりますが、配備したアプリが使えない時間が 1 時間ほどできます
C. 配備したアプリに向けて、依頼者が画面から参照の操作を行います。配備したアプリの外部エクスポートを確かめの間だけ有効にします（作り直し 2 回）。見本の PG には参照の項目を持つ表が無いため、DSL と見本の行を足す必要があり、監査と見本の PG に確かめの操作が残ります
X. Other (please specify)

[Answer]: A

## Q2. 要求の送り方と範囲

Q1 で A か B を選んだ場合です。既存の監視の確かめの場面 `monitorLight`（`MONITOR_BIZ=1`）は定義と一覧だけを回し、参照の道（`/api/tables/reference/rows`）と保存前の確かめつきの作成を回しません。参照の k6 の場面（`refPickerEquals` など）は待ちの無い負荷の場面で、そのまま流すと lgtm の受け手が止められるおそれがあります（履歴 H27）。

**推奨: A**。理由:
- 前の Intent の同じ段と同じ形（1 VU・繰り返しごとに 1 秒待つ、リポジトリの外の台本）で、lgtm を止める心配がありません。
- 参照の用意（ロール `perf-biz-ref`・`perf-biz-refwriter` と作業ロール）は、既存の k6 の場面の setup をそのまま使えます。前の Intent も書き込みのロールの用意に `bizCreate` を 5 秒だけ流しました。
- リポジトリのコード（`perf/k6/scenarios.js`）を変えずに済み、この段で `./gradlew verify` を流し直す必要がありません。

A. リポジトリの外（scratch）の小さな k6 の台本で、1 VU・繰り返しごとに 1〜2 秒待ち、次を順に回します。送信の周期をまたいで、合わせて 5 分以上送ります。
   - 用意: `bizPrepare` と、`refPickerEquals`・`refCreateChecked` を 1 VU・5 秒だけ流してロールと作業ロールを作る（判定には使わない）
   - 成功: 定義の取得（`biz_refsrc200` の `CREATE`・`biz_refpick` の `VIEW`）・ピッカーの一致の検索・部分一致の検索・ラベルの1件読み・保存前の確かめつきの作成（`biz_refcreate`）・参照の無い作成（`biz_new`）と更新
   - 代表の失敗: 参照先に無い値での作成（409 `BUSINESS_REFERENCE_NOT_FOUND`。パネル 43 に出て、警報が鳴らず、診断の WARN が出ないこと）
   - トークンは setup で取り、表示もファイルへの書き出しもしません
B. A と同じ範囲を、既存の k6 の場面に `--rps 1`（全体の要求の数の上限）と `VUS=1` を付けて、場面ごとに 3 分ずつ流します。台本を書かずに済みますが、場面ごとに系列が分かれて時間が長くなり、409 の要求は別に送る必要があります
C. `perf/k6/scenarios.js` の `monitorLight` に参照の要求（`MONITOR_REF=1`）を足し、リポジトリの手順として残します。後の Intent でも使えますが、リポジトリのコードの変更になり、`perf/k6/thresholds-test.sh` と `./gradlew verify` を流し直してからの統合が要ります
X. Other (please specify)

監査に残るもの（A・B・C とも、使い捨ての環境の内部DB だけ）: ロール・割り当て・作業ロール・DSL の適用、作成の成功（`BUSINESS_ROW_CREATED` の `SUCCESS`）と参照先に無い値の拒否（同じ種類の `FAILURE`）、更新（`BUSINESS_ROW_UPDATED`）。参照の読み取り（定義・ピッカー・ラベル）は監査に残りません（U1 の BR1.7）。配備した環境には何も残りません。

[Answer]: A

## Q3. 503 の起こし方と `TIMEOUT` の扱い

U1-NFR3.8・U2-NFR3.28 は、対象DB を止めて警報 `ms-biz-target-db` が firing になることを確かめるとしています。前の Intent の実測では、`docker stop` でも `docker pause` でも 503 はすべて `BUSINESS_TARGET_DB_UNAVAILABLE`（借りる待ちの時間切れ）で、`TIMEOUT` は起きませんでした（`project.md` の学び）。`TIMEOUT` の経路（問い合わせの最中の時間切れ）は、U1 の結合テストと U2 の `reliability-design.md` 6節の T-T1 で確かめ済みです（Build and Test で Met）。

**推奨: A**。理由:
- 警報の式は状態コード 503 だけで数え、code（`UNAVAILABLE` か `TIMEOUT` か）によらないため、`UNAVAILABLE` で firing を確かめれば式の確かめとして足ります。
- 前の Intent で `docker pause` も `UNAVAILABLE` になると分かっており、pause を足しても新しく分かることがありません。
- `TIMEOUT` を手元で確実に起こす手は無く、試すと時間がかかるうえに起きるかが確かでありません。

A. 使い捨ての PG を `docker stop` した状態で、Q2 の参照の要求（定義の `CREATE`・ピッカー・ラベル・保存前の確かめつきの作成）を 3 分以上送ります。次を確かめます。
   - パネル 47 と 42 に 503 が出ること、警報 `ms-biz-target-db` が firing になるまでの時間
   - 定義の `VIEW` は 200 のままか（参照先を読まないため）
   - 診断の WARN のキー（`purpose`・`stage`・`reason`・`budgetExceeded`）の名前と値、U1 と U2 の WARN の見分け方（ロガーか `stage`）
   - PG を戻した後、警報が Normal に戻ること

   `TIMEOUT` は手元では起こさず、結合テストで確かめ済みとして記録します
B. A に加えて、PG をつないだ後に `docker pause` した状態でも同じ要求を 3 分送り、`UNAVAILABLE` になることを確かめ直します（約 10 分延びます）
C. A に加えて、部分一致の検索（表の全部を読む）の最中に PG を `docker pause` して、`TIMEOUT` を起こすことを試みます。時間の上限（例: 15 分）を先に決め、起きなければ A と同じ扱いにします
X. Other (please specify)

[Answer]: A

## Q4. 3,000 ms のバケットを足すか（U1 の 2節の引き継ぎ）

ピッカーの部分一致の検索の目標は p95 3,000 ms 未満ですが、`http.server.requests` のバケットの境界（2,000 と 5,000）に 3,000 ms がありません。そのため、パネル 41 のサーバー側の p95 は、3,000 ms の前後を見分けられません（合否は k6 の `iteration_duration` で決めます）。

**推奨: A**。理由:
- 合否は Performance Validation の k6 で決まり、サーバー側の p95 は参考の値です。
- 足すと `application.yaml` の変更になり、ブランチ・`./gradlew verify`・統合・WAR の作り直し・配備のやり直しが要ります。この段の範囲（ダッシュボードと警報のファイル）を超えます。
- 道・方法・状態コードの組ごとに系列が1つずつ増えます（`application.yaml` のコメントにある系列の数を絞る方針と逆向き）。

A. 足しません。`dashboards.md` に、3,000 ms の目標がバケットの境界に無いため、パネル 41 の部分一致の p95 は 2,000〜5,000 ms の間を見分けられず参考とすること、合否は k6 で決めることを書きます
B. 足します。`application.yaml` の `slo` に `3000ms` を足す変更を、この段の中で短命のブランチで行い、`./gradlew verify` を通して統合し、配備をやり直します
C. この段では足さず、Feedback & Optimization の振り返りに「足すかを後の Intent で決める」として引き継ぎます
X. Other (please specify)

[Answer]: A

## Q5. ダッシュボードの「仮の目標の一覧」（パネル id 3）

文字のパネル「仮の目標の一覧」の最後の行は「業務データの API の 95 パーセンタイル: 1 秒以内（判定は k6。下の区画の目安の線 1000 ms）」です。この Intent の参照の API の目標は、定義の取得・一致の検索・ラベル・保存前の確かめつきの作成が p95 1 秒、ピッカーの部分一致の検索だけが 3 秒です。設計（U1・U2 の `monitoring-design.md`）は、この一覧の直しに触れていません。

**推奨: A**。理由:
- 一覧は、ダッシュボードを開いた人が目標を知る唯一の場所です。部分一致の検索だけが 3 秒であることを書かないと、パネル 41 で 1 秒を超えた値を誤って問題と読むおそれがあります。
- 前の Intent の同じ段（Q5: A）と同じ扱いで、警報もしきい値も変えない、記録だけの差です（`dashboards.md` に設計との差として書きます）。

A. 一覧に1行を足します: 「他テーブル参照の API の 95 パーセンタイル: 1 秒以内、ピッカーの部分一致の検索だけ 3 秒以内（判定は k6。3 秒はバケットの境界に無い）」
B. 一覧は変えません（設計どおり）。目標は `slo-config.md` にだけ書きます
X. Other (please specify)

[Answer]: A
