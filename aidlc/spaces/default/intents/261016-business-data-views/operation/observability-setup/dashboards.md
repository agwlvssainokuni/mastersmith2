# ダッシュボード（dashboards）

前の Intent の記録（`aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/dashboards.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。ダッシュボードはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`）でリポジトリに置き、画面からは変えません（`aidlc/spaces/default/memory/project.md` の Deployment）。

決定は `observability-setup-questions.md` にあります（Q1〜Q6・F1・F2 すべて A、やり直したまとめの確認 Looks correct）。実測の値は `measurement-notes.md`（2026-10-25 01:58〜03:15 UTC）と、この段の確かめの回（同日 04:03〜04:29 UTC、2節）を正とします。

## 1. 今回の差（ファイルの変更。作業のブランチ `feature/261016-business-data-views-observability`、未コミット）

`docker/monitoring/dashboards/mastersmith-overview.json` を次のとおり変えました（版 4 → 5、パネル 38 → 45）。既存のパネルの式・配置は変えていません。

| # | 変更 | 中身 | 出典 |
|---|---|---|---|
| 1 | 行「業務データ（Intent 261016-business-data-views）」（id 39、y 89）と6パネル（id 40〜45）を最後に足した | 下の表。形は前の Intent の行「ロール・グループ・メニュー」（id 35〜38）にそろえた（幅 8・高さ 8 を3つずつ2段） | U1・U2 の `monitoring-design.md` 5節 |
| 2 | 「仮の目標の一覧」（id 3）に1行を足した | 「業務データの API の 95 パーセンタイル: 1 秒以内（判定は k6。下の区画の目安の線 1000 ms）」 | Q5: A |

区画の6パネルが対象にする `uri` は、どれも `uri=~"/api/tables/.*"` です。実測で当たった道は次の5つの固定の道だけでした（行の値と主キーは本文で運ぶため、道に値は載らない。`measurement-notes.md` 3節）。

| method | uri | API |
|---|---|---|
| POST | `/api/tables/definition` | 定義 |
| POST | `/api/tables/rows/search` | 一覧 |
| POST | `/api/tables/rows/get` | 詳細 |
| POST | `/api/tables/rows` | 作成 |
| PATCH | `/api/tables/rows` | 更新 |
| POST | `/api/tables/rows/delete` | 削除 |

`{…}` は `service_name="mastersmith"` です。

| id | パネル | 式 | 目安の線 |
|---|---|---|---|
| 40 | API ごとの要求の数（1 時間） | `sum by (method, uri, status) (increase(http_server_requests_milliseconds_count{…,uri=~"/api/tables/.*"}[1h]))` | なし |
| 41 | API ごとの時間（95 パーセンタイル） | `histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_milliseconds_bucket{…,uri=~"/api/tables/.*"}[5m])))` | 1000 ms（黄の線） |
| 42 | API ごとの 503 の件数（1 時間） | `sum by (method, uri) (increase(http_server_requests_milliseconds_count{…,uri=~"/api/tables/.*",status="503"}[1h])) or on() vector(0)` | なし |
| 43 | API ごとの 409 の件数（1 時間） | 42 と同じ形で `status="409"` | なし |
| 44 | 対象DB の読み取りのプール | 使用中 `max(hikaricp_connections_active{…,pool="mastersmith-target-db"})`・待ち `max(hikaricp_connections_pending{…})`・待ちの時間切れ（1 時間）`sum(increase(hikaricp_connections_timeout_total{…}[1h]))` の3系列 | なし |
| 45 | 対象DB の書き込みのプール | 44 と同じ形で `pool="mastersmith-target-db-write"` | なし |

パネルの説明に書いたこと:
- 40: 作成と更新は同じ `uri` で `method` で見分ける。403（権限の無いテーブル・カラム・操作）もここに出て、監査には残らない。`increase` は外挿のため整数にならず、実際より多めに出ることがある。
- 41: バケットは 100・250・500・1000・2000・5000 ms。判定は k6 で、線は目安。**最小のバケットが 100 ms のため、100 ms 未満の要求はすべて 95 前後に見える**。5 分間に要求が無いと NaN。対象DB に届かない 503 は借りる待ちの 3 秒で返るため、2000〜5000 ms のバケットに入る。
- 42: 応答の code は指標のラベルに無い。`BUSINESS_TARGET_DB_UNAVAILABLE` と `BUSINESS_TARGET_DB_TIMEOUT` は、ログの WARN のキー `reason` で見分ける。警報 `ms-biz-target-db` が同じ 503 を 5 分の窓で数える。
- 43: 業務の理由の 409 の例（参照先が無い・一意の重なり・定義が変わった・行を1件に決められない・対象DB と DSL が合わない・書き込みの接続の設定が無い）。code はログの「要求をエラー応答に変換しました」の行の `code` で見分ける。
- 44・45: 待ちは 60 秒ごとの1点の値のため、短い詰まりは見えない。待ちの時間切れは、対象DB に届かない 503（UNAVAILABLE）の件数と一致した。系列は最初に借りるまで出ない。

## 2. 確かめ方と結果

### 2.1 確かめの環境（Q1: A。`measurement-notes.md` 2節と同じ形）

- 使い捨ての環境 `-p mastersmith-perf` を、次の形で動かしました。
  - アプリ: イメージ `mastersmith:local`（配備と同じ。作り直していない）・上限 1g・CPU 4。外部エクスポートと指標の公開は、このアプリにだけ有効にした。
  - 対象DB: 使い捨ての PG（既定の設定・上限 512m、種 100,000 行）。
  - lgtm: `grafana/otel-lgtm:0.36.0`・上限 1536m・127.0.0.1:13000。変えた2つのファイル（このダッシュボードと警報の決まり）を読み取りだけでマウントした。
- lgtm を網につないだ後（04:04:34 UTC）に、アプリを起動しました（04:04:53 に healthy）。
- 配備したアプリと見本の PG は、止めず、作り直さず、要求も送っていません。Mailpit は 04:03:52〜04:29 の間だけ止めました。
- 要求は、1 VU で繰り返しごとに 1 秒待つ使い捨ての台本（リポジトリの外）で送りました。書き込みのロールの用意に限り、`bizCreate` を 1 VU・5 秒だけ流しました（1,247 件）。前回の1回目のように lgtm の受け手が止められることはなく、lgtm の使用は最大でも上限の半分ほど（終わりに 693 MiB）でした。
- 台本の全体を `caffeinate -dims` で包みました。流した後に `pmset -g log` で、13:03〜13:29（JST）の窓に `Sleep`・`DarkWake`・`Wake` が 0 件であることを確かめました。

| 時刻（UTC） | 送ったもの | 結果 |
|---|---|---|
| 04:05:49 | `bizPrepare`（DSL の適用・ロール・作業ロール） | checks の失敗 0 |
| 04:06:24〜04:06:30 | 書き込みのロールの用意（`bizCreate`、1 VU・5 秒） | 1,247 件、checks の失敗 0 |
| 04:07:00〜04:12:02 | 通常の流れ（1回に定義・一覧・詳細・作成・更新・削除と、参照先の無い作成） | 274 回。すべて期待どおり（200・201・409 `BUSINESS_REFERENCE_NOT_FOUND`） |
| 04:12:32〜04:16:35 | 使い捨ての PG を `docker stop` し、同じ流れを送った | 24 回。定義は 200（対象DB に接続しない）。一覧・詳細・作成は 24 件ずつすべて 503 `BUSINESS_TARGET_DB_UNAVAILABLE` |
| 04:16:35〜04:25:41 | PG を戻し（04:16:39 に healthy）、通常の流れを送った | 497 回。すべて期待どおり |
| 04:25:41〜04:27:41 | 何も送らない | — |

### 2.2 Grafana に読み込まれたこと

`/api/dashboards/uid/mastersmith-overview` で、次を確かめました。
- パネル 45 件、行 id 39「業務データ（Intent 261016-business-data-views）」と、パネル 40〜45 の題（1節の表のとおり）。
- 「仮の目標の一覧」の最後の行が、足した1行であること。

### 2.3 6つのパネルの式の結果（04:05〜04:29 UTC、60 秒ごと）

10 の式（パネル 44・45 は3つずつ）を、Grafana のデータソースの代理（`/api/datasources/proxy/uid/prometheus/api/v1/query_range`）で流しました。**10 件すべて `success`** で、値が返りました。

| パネル | 系列 | 値の最大 | 実際の件数との比べ |
|---|---|---|---|
| 40 要求の数 | 10 系列（成功の6つ・409・503 の3つ） | 成功と 409 は 792.8、503 は 24.5 | 通常の流れは 771 回ずつ、503 は 24 件ずつ。用意の `bizCreate` の 1,247 件は、新しい系列の最初の点に入ったため数えられていない（`project.md` の学び） |
| 41 p95 | 6 系列 | 成功の間は 95.0〜95.9 ms。503 の間は 4,850 ms（一覧・詳細・作成） | 要求の無い 5 分の点だけ NaN（更新・削除で各 1 点。PG を止めている間は更新・削除を送っていない） |
| 42 503 | 3 系列（503 の前は `0` の1系列） | 24.5 | 24 件ずつ |
| 43 409 | 1 系列（409 の前は `0` の1系列） | 792.8 | 771 件 |
| 44 読み取りのプール | 使用中・待ち・時間切れ | 使用中 0・待ち 1・時間切れ 50.6 | 時間切れは一覧 24＋詳細 24 = 48 件 |
| 45 書き込みのプール | 使用中・待ち・時間切れ | 使用中 0・待ち 0・時間切れ 25.4 | 時間切れは作成 24 件 |

- NaN は、その 5 分に要求が無かった点だけでした（要求を送った間は値が出た）。
- 使用中は 1 VU のため 60 秒ごとの点ではほぼ 0 に見え、待ちも 1 点だけでした。詰まりを確かに表したのは、時間切れの系列です（F2: A の理由と同じ）。

## 3. 前回の実測から分かっていること（`measurement-notes.md` 3節・4節）

- 名前は `http_server_requests_milliseconds_count`・`_bucket` です（`_seconds_` ではない）。
- `le` は 7つ（`100`・`250`・`500`・`1000`・`2000`・`5000`・`+Inf`）。
- ラベルは `application`・`error`・`exception`・`job`・`method`・`outcome`・`service_name`・`status`・`uri` で、**応答の code のラベルは無い**。
- プールの `pool` の値は `mastersmith-db`（内部DB）・`mastersmith-target-db`（読み取り）・`mastersmith-target-db-write`（書き込み）。
- 前回の実測で、時間切れの累計の増え方は 503 の件数と一致しました（読み取り 192・書き込み 96）。今回も一致しました（2.3）。

## 4. 設計との差（承認済みの文書は書き換えない）

| ID | 上流 | 上流の記載 | この段の記録 | 理由と扱い |
|---|---|---|---|---|
| D-D1 | U1 の `monitoring-design.md` 5節 | p95 の式 `histogram_quantile(0.95, sum by (le, uri) (…))` | `sum by (le, method, uri)` にした | 作成（POST）と更新（PATCH）が同じ `uri` `/api/tables/rows` で、`method` でしか見分けられないため（実測で確かめた）。U2 の `monitoring-design.md` 5節の形と同じ。質問にせず差として書いた（F2 の前置き） |
| D-D2 | U1・U2 の `monitoring-design.md` 5節 | プールのパネルは「使用中・待ち・待ちの時間切れの累計」 | 時間切れは累計ではなく、1 時間の数（`increase(hikaricp_connections_timeout_total{…}[1h])`）の系列にした | F2: A。累計はアプリを起動し直すと 0 に戻り、増え方が読みにくい。1 時間の数は 503 の件数と一致した（2.3）。パネルの数は変えていない |
| D-D3 | U1・U2 の `monitoring-design.md` 5節 | 区画を1つ足す | 区画に加えて「仮の目標の一覧」に1行を足した | Q5: A。警報もしきい値も変えない記録だけの差 |
| D-D4 | U1 の `monitoring-design.md` 5節 | 区画のパネルの表に 409 の件数が無い（U2 の 5節が「409・503 の数」を書く） | 503 と 409 を別のパネルにした | U2 の 5節の「status が 409 のものと 503 のものを数える」を、見分けやすさのために2つに分けた。式の形は同じ |

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/observability-setup-questions.md`（Q1〜Q6・F1・F2、やり直したまとめの確認）
- この段の `measurement-notes.md`（3節・4節・7節）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/dashboards.md`
- 基盤の設計: `construction/u1-businessdata-read/infrastructure-design/monitoring-design.md`（5節）・`construction/u2-businessdata-write/infrastructure-design/monitoring-design.md`（5節）
- `construction/build-and-test/build-and-test-summary.md`（U1-NFR8.3・U2-NFR8.5）
- `docker/monitoring/dashboards/mastersmith-overview.json`・`docker/monitoring/provisioning/dashboards/mastersmith.yaml`、`docker/perf/compose.yaml`、`perf/README.md`、`perf/k6/scenarios.js`
- `backend/src/main/java/cherry/mastersmith/businessdata/domain/BusinessDataProblemTypes.java`（409・503 の code）、`businessdata/service/BusinessDataReadService.java`・`BusinessDataWriteService.java`（WARN の文とキー）、`common/error/web/GlobalExceptionHandler.java`
- 実行したコマンドの出力（この段の確かめの回）:
  - `curl -s http://127.0.0.1:13000/api/dashboards/uid/mastersmith-overview`
  - `curl -s http://127.0.0.1:13000/api/datasources/proxy/uid/prometheus/api/v1/query_range?...`（10 の式）
  - 使い捨ての台本の出力（状態コードと code の件数だけ）、`pmset -g log`

## Assumptions & Open Questions

- 確かめた値は、負荷をかけない使い捨ての環境（上限 1g、1 VU）の値で、目標の判定には使いません（判定は performance-validation の k6）。
- D-D4（503 と 409 を別のパネルにした）は承認の場で確かめます。
