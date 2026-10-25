# Observability Setup — Questions

Intent 261016-business-data-views（scope classic、深さ Standard）の Observability Setup の質問です。配備先が決まるまでの手元の監視は、`grafana/otel-lgtm` を compose の profile `monitoring` で見たいときだけ起動します。ダッシュボードと警報の決まりは、ファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

この段で確かめるとされたことは次のとおりです。

- Build and Test の `Unverified` のうち、持ち主が observability-setup の4件（`construction/build-and-test/build-and-test-summary.md`）
  - U1-NFR8.1: 警報 `ms-forbidden` のしきい値（20）を見直す
  - U1-NFR8.3: ダッシュボードの業務データの区画（3本の p95・503 の数・プールの待ち）と、既存の警報で拾えること
  - U2-NFR8.5: ダッシュボードに書き込みの3本の p95・409 と 503 の数・書き込みのプールの待ちを足す
  - U2-NFR8.6: 既存の警報（`ms-pool-pending`・`ms-audit-fail`・`ms-audit-slow`・`ms-5xx-ratio`・`ms-forbidden`）が書き込みも拾う
- 基盤の設計の `monitoring-design.md` からの引き継ぎ
  - U1 の 2節の R-05: 対象DB を止めた状態と、つないだ後に止めた状態（`docker pause`）で業務データの API を送り、`ms-5xx-ratio`・`ms-pool-pending` の式の値を実測する。拾えなければ、業務データの 503 に絞った警報を足すかをこの段で決める
  - U2 の 2節: 書き込みのプールの待ち・503・監査の書き込みの失敗・403 を起こし、式の値の動きを確かめる。書き込みのプールの系列は初めて借りるまで出ないため、先に1回書き込んでから式を流す
  - U2 の 7節: 内部DB の大きさの基準の値（監査の行の増え方。NFR 要件のレビューの R-03）
  - U3 の後の段: 業務データの API の 403 を既存の警報が拾うかの実測（U3-NFR8.7）

## 1. 今の監視の設定がどこまで効いているか（読み取りだけで洗い出した。2026-10-25）

| 対象 | 今の設定で効いているもの | 効いていないもの・気になる点 |
|---|---|---|
| 業務データの6本の API（読み取り `/api/tables/definition`・`/api/tables/rows/search`・`/api/tables/rows/get`、書き込み `POST`・`PATCH` `/api/tables/rows` と `/api/tables/rows/delete`） | 全体のパネル（要求の数・5xx の割合・ERROR のログ・403 の件数）に数として入る見込み。道は固定で、値は本文で運ぶため、`uri` に値は載らない（設計） | API ごとのパネルが無い。設計で決めた区画「業務データ（Intent 261016-business-data-views）」はまだ書かれていない（`docker/monitoring/` の最後の変更は 2026-10-15 の `c0f1995`）。p95 の警報3件は `uri` を1つの道に固定しているため、業務データの API を拾わない（設計の差として承認済み） |
| 対象DB に届かない・答えない（503） | `ms-5xx-ratio`（5xx の割合が 5 分間 1% を超える、`uri` で絞らない）に入る | ほかの API の要求が多いと割合が小さく、鳴らないことがある。応答の code（`BUSINESS_TARGET_DB_UNAVAILABLE`・`BUSINESS_TARGET_DB_TIMEOUT`）は指標のラベルに無い見立てで、見分けはログ（WARN のキー `reason`）になる |
| 接続プール | `ms-pool-pending`（`max(hikaricp_connections_pending{…})`、プールで絞らない）は、対象DB の読み取りのプール `mastersmith-target-db` と書き込みのプール `mastersmith-target-db-write` も含む見込み | 対象DB に接続が作れないとき（3 秒で切れる）は、待ちが増えにくい。書き込みのプールの系列は初めて借りるまで出ない |
| 監査の書き込みの失敗・遅れ | `ms-audit-fail`・`ms-audit-slow` はログの文で数えるため、業務データの出来事でも同じ文で拾う見込み（文は変えていない） | 本番のコードを変えずに監査の書き込みを失敗させる手は無い |
| 403 | `ms-forbidden`（403 が 1 時間に 20 件を超える、`uri` で絞らない）は、業務データの権限の 403 も数える | しきい値 20 の見直しが U1-NFR8.1 に残っている。業務データの 403 は監査に残らない（承認済み） |
| 業務データの監査 | 内部DB の `audit_events` に `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED` が残る（配備の後のスモークテストで3件、3つの列 `target_schema`・`target_table`・`work_role_id` も確かめた） | 監査は指標とログに出ないため、ダッシュボードでは見られない。数え方は内部DB の複写を読み取りで開く問い合わせになる |
| ログ | 対象DB の失敗は WARN の1行（キー `purpose`・`stage`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`）が Loki に届く | パネル・警報は無い（設計どおり、ログの問い合わせで数える） |
| トレース | 変えていない。道は固定で、トレースの道の属性に値は載らない（漏えいは verify の結合テストで毎回確かめている） | — |

読み取りだけで確かめた今の実行環境（`colima list`・`docker ps -a`・`docker stats --no-stream`）:

- colima は CPU 4・メモリ 6GiB で動いている。
- 動いているコンテナ: `mastersmith-app-1`（healthy、使用 705 MiB / 上限 2g）・`mastersmith-mailpit-1`（42 MiB / 256m）・`mastersmith-targetdb-postgres-1`（24 MiB / 512m）
- `mastersmith-lgtm-1` は止まっている（10 日前に Exited）。ボリューム `mastersmith_mastersmith-monitoring` は残っている。
- メモリの上限の合計: 配備したアプリ 2g・Mailpit 256m・見本の PG 512m で 2,816 MiB。確かめの環境として次を足すと、上限の合計は 5,888 MiB で VM の 6GiB（docker から見える量 約 5,910 MiB）に近い。
  - lgtm 1,536 MiB
  - 使い捨てのアプリ 1g
  - 使い捨ての PG 512m

## 2. 決まっていること・読み取りで確かめたこと（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 独自の指標は足さない。新しい警報は、R-05 の実測で既存の警報が拾えなかったときに限り、この段で足すかを決める | U1・U2 の `monitoring-design.md` 2節 |
| ダッシュボードに区画「業務データ（Intent 261016-business-data-views）」を1つ足す。U1・U2 のパネルを同じ区画に入れる（要求の数・p95・503 の数・409 の数・読み取りのプール・書き込みのプール） | U1・U2 の `monitoring-design.md` 5節 |
| 式の名前・ラベル・`uri` の値・`le` のバケットは、書く前に起動して確かめる。式を書いた後に、すべての式を流す。p95 の式は、先に `+Inf` 以外の `le` があることを確かめる。NaN を「要求が無いため」と決めつけない | `project.md` の Corrections・Deployment の学び |
| 数を確かめる要求は、指標の送信の周期（60s）を複数またいでくり返し送る | `project.md` の Deployment の学び |
| lgtm を網につないだ後に、使い捨てのアプリを起動する | `project.md` の Deployment の学び |
| 確かめのために送る要求が監査に残る・データを変えるときは、送る前に依頼者に伝える。使い捨ての環境なら、配備した内部DB を汚さない | `project.md` の Corrections、U1・U2 の `monitoring-design.md` 5節 |
| SLO は緩めない。手元の監視を常に動かしていない間は、手元の監視の p95 の判定を `Unverified` とし、配備の直後・監視の確かめ・負荷の試験・振り返りの時点の値を基準の値として並べる。k6 の判定は performance-validation | `project.md` の Deployment の学び、各 `monitoring-design.md` 3節 |
| 画面（U3・U4）の側の監視は置かない。画面の時間・E2E の時間・axe の件数は E2E と検査の記録で見る（Build and Test で記録済み） | U3・U4 の `monitoring-design.md` |
| ログの収集・トレースの設定は変えない。外部エクスポートは既定で無効のまま。配備したアプリの外部エクスポートは有効にしない（作り直さない） | 各 `monitoring-design.md` 4節、team.md の Deployment、前の Intent の同じ段 |
| 警報の通知の先（contact point）は置かない。手元の Grafana の警報の一覧で見る | `project.md` の Deployment |
| 確かめの後は、使い捨ての環境を消し、lgtm を元の止めた状態に戻す。確かめの結果を見てから片付ける | 前の Intent の同じ段、`project.md` の学び |

## Q1. 名前と数を確かめるために要求を送る環境

区画の式を書くには、業務データの6本の API の `uri` の値・`le`・式の名前を、実際に要求を送って確かめる必要があります。業務データの API を送るには、適用した DSL・ロール・作業ロールと、対象DB（読み取りと書き込みのアカウント）が要ります。`perf/README.md` の「業務データの場面」の手順（1B〜4B、`bizPrepare`）でこの用意ができます。

A. 使い捨ての環境（`docker/perf/compose.yaml`、`-p mastersmith-perf`）を起動します。
   - アプリは配備と同じイメージ `mastersmith:local`・メモリの上限 1g、仮の署名鍵・仮の管理者・試験用の利用者 `perf-biz-01` を使います。
   - 対象DB は使い捨ての PG（既定の設定、上限 512m）で、種は `perf/biz/make-biz-seed.sh` の 100,000 行です。
   - 外部エクスポートはこのアプリにだけ有効にし、lgtm を使い捨ての環境の網に別名 `lgtm` でつなぎます（画面は 127.0.0.1:13000）。
   - 配備したアプリ・見本の PG は止めず、確かめの要求も送りません。Mailpit は確かめの間だけ止めます（上限の合計 5,632 MiB）。
   - 終わったら使い捨ての環境を消します（推奨）
B. A と同じですが、配備したアプリも確かめの間だけ止めます（Performance Validation と同じ形）。メモリの余裕は大きくなりますが、配備したアプリが使えない時間が 1 時間ほどできます
C. 配備したアプリに向けて、依頼者が画面から業務データの操作を行います。配備したアプリの外部エクスポートを確かめの間だけ有効にします（作り直し 2 回）。監査と見本の PG に確かめの操作が残ります
X. Other (please specify)

推奨の理由: 前の Intent の同じ段（Q1: A）と `project.md` の「本物のデータと監査ログを汚さない」に合います。U1・U2 の基盤の設計（R-05 の実測は使い捨ての PG で行う）とも同じです。配備したアプリを止めずに済みます。上限の合計は VM に収まり、実際の使用は上限よりずっと少ない見込みです（配備したアプリ 705 MiB）。

[Answer]: A

## Q2. 送る要求の範囲

Q1 で A か B を選んだ場合、使い捨ての環境に送る要求の範囲を決めます。

A. 区画に入る6本の API に、送信の周期をまたいで 3 分以上くり返し要求を送ります。k6 の業務データの場面を、短い長さと少ない VU で流します。中身は次のとおりです。
   - 成功: `bizSearchFirst`・`bizDetail`・`bizDefinition`、`bizCreate`・`bizUpdate`・`bizDelete`
   - 代表の失敗: 要る権限だけを欠く利用者からの 403、`bizWritePoolLimit`（409 `BUSINESS_REFERENCE_NOT_FOUND`、書き込みのプールの上限 5 に届く）
   - 503: 使い捨ての PG を `docker stop` した状態と、つないだ後に `docker pause` した状態のそれぞれで、読み取りと書き込みを 3 分以上送る（R-05）

   監査の書き込みの失敗は、本番のコードを変えずに起こせないため起こしません。`ms-audit-fail`・`ms-audit-slow` は、式がログの文だけで数えること（出来事の種類で絞らないこと）を式の読み取りで確かめ、鳴ることは `Unverified` として `alarms.md` に書きます。監査の行は、使い捨ての環境の内部DB の複写を読み取りで開き、種類と結果ごとに数えます（推奨）
B. A に加えて、監査の書き込みの失敗を起こすため、使い捨てのアプリの内部DB の接続プールの上限を下げ、書き込みを重ねて監査の書き込みの待ちを時間切れにします。起きるかは確かではなく、起きなければ A と同じ扱いにします
C. 成功の要求だけを送り、`uri` と `le` を確かめます。503・403・409 と警報の拾い方は、Build and Test の結合テストの結果を正とし、警報が鳴ることは `Unverified` のまま feedback-optimization に引き継ぎます
X. Other (please specify)

推奨の理由: 区画のパネル（数・p95・503・409・2つのプール）と、R-05 の実測（止めた状態とつないだ後に止めた状態）を、すべて実際の値で確かめられます。監査の書き込みの失敗を起こす形は、確実に起こせず、前の Intent までも起こしていません。

[Answer]: A

## Q3. 既存の警報が業務データの 503 を拾えなかったとき

R-05 の実測で、`ms-5xx-ratio`・`ms-pool-pending` が業務データの 503 を拾えない（式の値がしきい値に届かない）と分かる場合があります。たとえば、ほかの API の要求が多いと割合が 1% に届かない、接続を作れないときは待ちが増えない、などです。

A. 実測の結果を見てから、この段の追加の質問で決めます。拾えなかったときの案として、業務データの API の 503 に絞った警報（`uri=~"/api/tables/.*"`・`status="503"` の件数）を足すか、足さずに区画のパネルで見るかを、実測の値とともに尋ねます（推奨）
B. 実測の結果によらず、業務データの 503 に絞った警報を1つ足します（設計の「新しい警報は足さない」との差を `alarms.md` に書きます）
C. 実測の結果によらず、警報は足しません。拾えないときは、そのことと区画のパネルでの見方を `alarms.md` に書きます
X. Other (please specify)

推奨の理由: U1 の基盤の設計は「拾えなければ、警報を足すかをこの段の判断事項にする」としています。値を見る前に決めるより、実測の値で判断した方が誤って鳴る・鳴らないを避けられます。

[Answer]: A

## Q4. `ms-forbidden`（403 の増加）のしきい値（U1-NFR8.1）

`ms-forbidden` は、`uri` で絞らずに 403 が 1 時間に 20 件を超えたら鳴ります。この Intent で業務データの権限の 403 が加わりました（権限の無いテーブル・カラム・操作の拒否。画面は 403 の後に送り直さない、U3-NFR8.7）。業務データの 403 は監査に残りません。依頼者1人の今の使い方では、20 件に届くことはまれな見込みです。

A. しきい値 20 は変えません。`alarms.md` に、業務データの 403 も数に入ること、業務データの 403 は監査に残らず、ログと区画の要求の数のパネル（`status="403"` と `uri` ごと）で見分けることを書きます。しきい値を見直すかは、配備先が決まり利用者が増えたときの申し送りにします（推奨）
B. `ms-forbidden` の式から業務データの道（`uri=~"/api/tables/.*"`）を外します。既存の警報の変更で、設計（「既存のとおり」）との差になるため `alarms.md` に差を書きます
C. しきい値を上げます（例: 50）。全体の 403 の警報が鈍くなります
X. Other (please specify)

推奨の理由: 設計（警報は変えない）のままで、今の使い方では誤って鳴る見込みが小さいためです。見分け方を記録しておけば、鳴ったときに迷いません。前の Intent の同じ段（Q4: A）と同じ線引きです。

[Answer]: A

## Q5. ダッシュボードの「仮の目標の一覧」

ダッシュボードの文字のパネル「仮の目標の一覧」には、前の Intent でロール・グループ・メニューの API の目標を足しました。この Intent の承認済みの SLO は、業務データの6本の API の p95 1 秒（判定は k6、Performance Validation）です。

A. 一覧に「業務データの API の 95 パーセンタイル 1 秒以内（判定は k6。区画の目安の線 1000 ms）」の1行を足します。設計との差（区画だけを足すとした）を `dashboards.md` に書きます（推奨）
B. 一覧は変えません（設計どおり）。目標は `slo-config.md` にだけ書きます
X. Other (please specify)

推奨の理由: 一覧は、ダッシュボードを開いた人が目標を知る唯一の場所です。前の Intent の同じ段（Q6: A）と同じ扱いです。警報もしきい値も変えない、記録だけの差です。

[Answer]: A

## Q6. 内部DB の大きさの基準の値（U2 の NFR 要件のレビュー R-03）

業務データの書き込みは、1件ごとに監査の行（主キーの値は持たず、`target_schema`・`target_table`・`work_role_id` の3列を足した行）を内部DB に残します。U2 の基盤の設計は、内部DB の大きさの基準の値をこの段に回しました。

A. 2つの値を並べて `slo-config.md` に記録します。どちらも値（行の中身）は表示せず、大きさと件数だけを見ます。
   - 配備した内部DB の今の大きさと監査の行の数: Deployment Execution の配備の後の複写（`~/.mastersmith-backup/` の `…-after-business-data-views.tgz`）で、`mastersmith.mv.db` の大きさと `audit_events` の件数を読む
   - 書き込みでの増え方: Q2 の使い捨ての環境で、書き込みの前と後の内部DB のファイルの大きさと、`BUSINESS_ROW_*` の行の数を読み、1件あたりの増え方を見積もる

   配備したアプリは止めません（推奨）
B. A の配備した内部DB の分だけを記録し、増え方の見積もりは Performance Validation の書き込みの場面（`bizCreate` など、件数が多い）に任せます
C. 記録しません。内部DB の大きさの見方は、既存の手順（README の内部DB の詰め直し）のままにします
X. Other (please specify)

推奨の理由: 配備の後の複写がすでにあるため、配備したアプリを止めずに今の値を読めます。増え方は Q2 の書き込みの回でそのまま測れ、Performance Validation の多い件数の回の値と後で比べられます。

[Answer]: A

## F1. 業務データの 503 の警報（Q3: A の追加の質問）

使い捨ての環境での実測の結果です（`measurement-notes.md` の 5節・6節）。
- 503 は、使い捨ての PG を止めた状態と、つないだ後に止めた状態（`docker pause`）のどちらも、144 件すべて `BUSINESS_TARGET_DB_UNAVAILABLE` でした（借りる待ち 3 秒の時間切れ）。
- 既存の2つの警報は鳴りましたが、どちらも条件つきです。
  - `ms-5xx-ratio`: 止めて 7分09秒で firing になりました。ただし、業務データの 503 は1人の操作で最大 0.3 件/秒で、ほかの要求が 30 件/秒を超えると割合が 1% を下回ります。実際に、ほかの要求（約 190 件/秒）が加わると inactive に戻りました。
  - `ms-pool-pending`: 2分09秒・2分31秒で firing になりました。ただし、60 秒ごとの1点の値で決まるため、pause の状態では inactive と firing を行き来しました。
- 確実に拾えたのは、対象DB のプールの待ちの時間切れの累計（`hikaricp_connections_timeout_total`）の増え方です。503 の件数と一致しました（読み取り 192・書き込み 96）。

A. 業務データの API の 503 に絞った警報を1つ足します。
   - 式: `sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/.*",status="503"}[5m]))` が 0 を超えたとき、`for: 0s`
   - 題: 「業務データの対象DB に届かない」。注記に、ログの WARN の `reason` で UNAVAILABLE と TIMEOUT を見分けることと、`docker compose ps targetdb-postgres` で対象DB を確かめることを書きます。
   - 状態コードで数えるため、借りる待ちの時間切れ（UNAVAILABLE）と問い合わせの時間切れ（TIMEOUT）の両方を拾います。
   - 設計（「新しい警報は足さない」）との差を `alarms.md` に書きます（推奨）
B. 対象DB のプールの待ちの時間切れの累計に絞った警報を1つ足します。
   - 式: `sum(increase(hikaricp_connections_timeout_total{service_name="mastersmith",pool=~"mastersmith-target-db.*"}[5m]))` が 0 を超えたとき
   - 実測で 503 の件数と一致しました。ただし、問い合わせの時間切れ（`BUSINESS_TARGET_DB_TIMEOUT`）は数えません。
C. 警報は足しません。区画の 503 の件数と時間切れのパネルで見ることと、既存の警報が条件つきでしか鳴らないことを `alarms.md` に書きます
X. Other (please specify)

推奨の理由: 依頼者1人の使い方では、ほかの要求で割合が薄まりにくい一方で、点の取りこぼしは起こりえます。状態コードで数える形なら、どちらの時間切れも拾え、割合と 60 秒の点に左右されません。誤って鳴る見込みは、対象DB が止まったときに限られます。

[Answer]: A

## F2. 区画に足すパネル（対象DB のプールの待ちの時間切れ）

設計の区画（U1・U2 の `monitoring-design.md` 5節）は、プールの使用中と待ちのパネルだけです。実測では、待ちは 60 秒ごとの1点の値で短い詰まりを取りこぼしました。一方、待ちの時間切れの累計は 503 の件数と一致しました（`measurement-notes.md` の 4節）。

あわせて、p95 の式を U1 の設計の形（`sum by (le, uri)`）ではなく、U2 の形（`sum by (le, method, uri)`）にします。作成（POST）と更新（PATCH）が同じ `uri` で、`method` でしか見分けられないためです。これは質問にせず、設計との差として `dashboards.md` に書きます。

A. 読み取りと書き込みのプールのパネルに、待ちの時間切れの数（1 時間、`increase(hikaricp_connections_timeout_total{…,pool=~"mastersmith-target-db.*"}[1h])`）を足します（パネルの数は変えず、各プールのパネルの系列を1つ増やす）。設計との差を `dashboards.md` に書きます（推奨）
B. 設計どおり、使用中と待ちだけにします
X. Other (please specify)

推奨の理由: 実測で最も確かに詰まりを表した値です。パネルの数は増えず、F1 の警報が鳴ったときに、どちらのプールで起きたかをすぐ見られます。

[Answer]: A

## Consolidated Summary Confirmation

答え（Q1〜Q6・F1・F2: すべて A。実測の結果は `measurement-notes.md`）から、この段で行うことを次のようにまとめました。

1. **確かめの環境（Q1: A）**
   - `perf/README.md` の「業務データの場面」の 1B〜3B の手順で、使い捨ての環境（`-p mastersmith-perf`）を作ります。中身は次のとおりです。
     - アプリ: イメージ `mastersmith:local`、上限 1g、仮の署名鍵・仮の管理者・`perf-biz-01`〜
     - 対象DB: 使い捨ての PG（既定の設定、上限 512m）と 100,000 行の種
   - lgtm を使い捨ての網に別名 `lgtm` でつなぎ（画面は 127.0.0.1:13000）、その後に外部エクスポートを有効にしたアプリを起動します。
   - 配備したアプリと見本の PG は止めず、要求も送りません。Mailpit は確かめの間だけ止めます（その間、配備したアプリの招待のメールは送れません）。
2. **送る要求（Q2: A）**: k6 の業務データの場面を、短い長さ・少ない VU で、送信の周期（60s）を複数またいで流します。
   - 準備: `bizPrepare`（DSL の適用・ロール・作業ロールの用意）
   - 成功: 読み取り3本（`bizSearchFirst`・`bizDetail`・`bizDefinition`）、書き込み3本（`bizCreate`・`bizUpdate`・`bizDelete`）
   - 失敗: 要る権限だけを欠く利用者の 403、`bizWritePoolLimit`（409 と書き込みのプールの上限）
   - 503: 使い捨ての PG を `docker stop` した状態と、つないだ後に `docker pause` した状態のそれぞれで、読み取りと書き込みを 3 分以上送ります（R-05）。
   - 監査の書き込みの失敗は起こしません。`ms-audit-fail`・`ms-audit-slow` は式の読み取りで確かめ、鳴ることは `Unverified` とします。
   - 監査は使い捨ての環境にだけ残ります。片付けの前に、内部DB の複写を読み取りで開き、種類と結果ごとに数えます。
3. **式の確かめ**
   - 書く前に、実際の指標の名前・`uri`・`status`・`pool` のラベル・`le` のバケットを確かめます。
   - 区画と警報のすべての式を流し、R-05 の値（`ms-5xx-ratio`・`ms-pool-pending`・`ms-forbidden` の式の値）を記録します。
4. **警報（Q3: A・Q4: A・F1: A）**
   - 既存の警報は変えません。`ms-forbidden` のしきい値は 20 のままにし、業務データの 403 の見分け方を `alarms.md` に書きます。
   - 実測で、業務データの 503 は既存の警報で条件つきでしか拾えなかったため、警報を1つ足します（F1: A）。
     - 式: 業務データの API の 503 の件数（5 分）が 0 を超えたとき、`for: 0s`
     - 題: 「業務データの対象DB に届かない」
   - 足した警報は、使い捨ての環境で鳴ることを確かめます（lgtm に読み込ませ、使い捨ての PG を止めて 503 を送る）。
   - 設計（「新しい警報は足さない」）との差を `alarms.md` に書きます。
5. **ダッシュボード（Q5: A）**: `docker/monitoring/dashboards/mastersmith-overview.json` に、区画「業務データ（Intent 261016-business-data-views）」を足します。
   - パネル: API ごとの要求の数・p95・503 の数・409 の数、読み取りのプール、書き込みのプール
   - p95 は `method` ごとに分けます（作成と更新が同じ `uri` のため）。2つのプールのパネルには、待ちの時間切れの数（1 時間）の系列を足します（F2: A）。どちらも設計との差を `dashboards.md` に書きます。
   - 「仮の目標の一覧」に業務データの API の目標の1行を足し、設計との差を `dashboards.md` に書きます。
6. **内部DB の大きさ（Q6: A）**: 値は表示せず、大きさと件数だけを `slo-config.md` に記録します。
   - 配備の後の複写で、今の大きさと監査の行の数を読みます。
   - 使い捨ての環境で、書き込みの前後の大きさと `BUSINESS_ROW_*` の件数を読み、1件あたりの増え方を見積もります。
7. **片付けと成果物**
   - 確かめの結果を見てから、使い捨ての環境を消し、lgtm を元の止めた状態に戻し、Mailpit を起動し直します。
   - 成果物は `dashboards.md`・`alarms.md`・`slo-config.md`・`log-queries.md`・`tracing-config.md`・`anomaly-config.md` です。
   - ダッシュボードの JSON の変更は、確かめた後にコミットを提案します。

- Looks correct
- Request changes

[Answer]: Looks correct
