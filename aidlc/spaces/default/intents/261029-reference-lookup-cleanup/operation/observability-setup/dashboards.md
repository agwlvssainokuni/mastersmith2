# ダッシュボード（dashboards）

前の Intent の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/dashboards.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。ダッシュボードはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`）でリポジトリに置き、画面からは変えません（`aidlc/spaces/default/memory/project.md` の Deployment）。

決定は `observability-setup-questions.md`（Q1〜Q5 すべて A）、設計は U1 の `monitoring-design.md` 2節・U2 の `monitoring-design.md` 2節・U3 の `monitoring-design.md` 1節を正とします。

## 1. 今回の差（ファイルの変更。作業のブランチ `fix/261029-reference-lookup-cleanup-obs`、未コミット）

`docker/monitoring/dashboards/mastersmith-overview.json` だけを変えました（71 行足し、5 行直し）。式・位置・種類を変えた既存のパネルはありません。

| 変更 | 中身 | 出典 |
|---|---|---|
| パネル 46 を足した | 題「参照の API の要求の数（1 時間）」、timeseries、`gridPos` x 0・y 106・w 8・h 8。式 `sum by (method, uri, status) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/(definition\|reference/rows)"}[1h]))`、凡例 `{{method}} {{uri}} {{status}}` | U1 の 2節（R-02 の仮の式を、2節の実際の値で確定） |
| パネル 47 を足した | 題「参照の API の 503 の件数（1 時間）」、x 8・y 106・w 8・h 8。式 `sum by (method, uri) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/(definition\|reference/rows)",status="503"}[1h])) or on() vector(0)` | 同上 |
| パネル 40 の説明 | 「6本」→「7本」にし、参照先の検索とラベル（`POST /api/tables/reference/rows`）を足した。定義の取得は `purpose` が `CREATE`・`UPDATE` のとき対象DB を読む（`VIEW` は読まない）ことを書いた | U1 の 2節 |
| パネル 42 の説明 | 参照の読み取り（定義の取得の `CREATE`・`UPDATE` と参照の道）の 503 を含むこと（U1）、作成・更新の 503 に保存前の確かめの時間切れ・接続の失敗が入ること（U2）を足した。見分けはログの WARN のキー `reason`、保存前の確かめか書き込みかはキー `purpose`（`REFERENCE`・`WRITE`）と `stage` | U1・U2 の 2節。キーの値は 2.4節で確かめた |
| パネル 43 の説明 | 作成・更新の `BUSINESS_REFERENCE_NOT_FOUND` が保存前の確かめの拒否も含むこと、`BUSINESS_TARGET_DB_MISMATCH` も 409 に入ること、警報にしないことを足した | U2 の 2節 |
| パネル 3「仮の目標の一覧」 | 1行を足した: 「他テーブル参照の API の 95 パーセンタイル: 1 秒以内。ピッカーの部分一致の検索だけ 3 秒以内（判定は k6。3 秒はバケットの境界に無い）」 | Q5: A |
| `version` | 5 → 6 | 前回の変更と同じ扱い |

- 表の中の `\|` は Markdown の表のための書き方で、JSON の式の中では `|` 1つです。
- パネル 46 の説明には、定義の取得が `VIEW` を含み参照だけの量とは言い切れないこと、ピッカーとラベルが同じ道で指標では分けられないこと、参照つきの作成・更新は入らないことを残しました（U1 の R-02 の決定、U2 の 2節）。
- 区画の行（id 39「業務データ（Intent 261016-business-data-views）」）の題は変えず、新しい区画の行も作っていません（U1 の 2節）。

## 2. 確かめ方と結果

### 2.1 確かめの環境（Q1: A・Q2: A）

| 項目 | 中身 |
|---|---|
| 使い捨ての環境 | `docker/perf/compose.yaml`（`-p mastersmith-perf`）。アプリは配備と同じイメージ `mastersmith:local`（`d8a467b60f6f`）、上限 1g・CPU 4。仮の署名鍵・仮の管理者・試験用の利用者（`perf-biz-01`〜`10`・`perf-roleop01`）。アクセストークンの有効期限は 15 分（使い捨てのアプリだけ） |
| 対象DB | 使い捨ての PG（`postgres:18.6`、上限 512m）。種は `perf/biz/make-biz-seed.sh postgres`（`biz_item` 100,000 行、参照の表 `biz_code200` 200・`biz_code201` 201・`biz_refpick`・`biz_refsrc200`・`biz_refsrc201` 各 10・`biz_refcreate` 0）。目印の語の当たり 909 行（0 でない） |
| 手元の監視 | 使い捨ての lgtm `mastersmith-perf-lgtm`（`grafana/otel-lgtm:0.38.0`、イメージ `75cdf892daff`、上限 1536m、画面 127.0.0.1:13000）。使い捨ての網に別名 `lgtm` で**先に**つなぎ、その後にアプリを起動した。警報の決まり・ダッシュボードの差し込みの設定・ダッシュボードのファイルを読み込み専用で差し込んだ |
| 配備した環境 | 配備したアプリ（`mastersmith-app-1`）・見本の PG は止めず、要求も送っていない。配備した Mailpit だけを 07:23:34 UTC に止め、片付けの後 07:50 UTC に `docker compose start mailpit` で戻して healthy を確かめた。配備した `mastersmith-lgtm-1` は止めたまま |
| 要求 | リポジトリの外の k6 の台本（1 VU、要求ごとに 1〜2 秒の待ち。値・トークンは出さず、出すのは「名前・状態コード・code」だけ）。用意は `bizPrepare`（DSL の適用・ロール `perf-biz-reader`）と、台本の setup のロール `perf-obs-ref`（`bizperf` を READ、`biz_refsrc200`・`biz_refpick` を FULL、`biz_refcreate`・`biz_new` を FULL と CREATE、`biz_code200` を READ）を `perf-biz-02` に割り当てて作業ロールを保存 |
| 1回の繰り返し | 定義（`biz_refsrc200` の `CREATE`）・定義（`biz_refpick` の `VIEW`）・ピッカーの一致の検索・部分一致の検索・ラベルの1件読み・保存前の確かめつきの作成（`biz_refcreate`、`K1`〜`K200`）・参照先に無い値の作成（`K999`）・参照の無い作成（`biz_new`）・その行の更新 |
| 眠りの守り | 台本の全体を `caffeinate -dims` で包んだ。流す前に `pmset -g assertions` で `PreventUserIdleSystemSleep` 1、流した後に `pmset -g log` で 16:24〜16:49（JST）の窓に `Sleep`・`DarkWake`・`Wake` が 0 件 |

| 時刻（UTC） | 送ったもの | 結果（台本が数えた件数） |
|---|---|---|
| 07:25〜07:26 | `bizPrepare`、台本の 20 秒の試し | checks の失敗 0。試しは 2 回の繰り返しがすべて期待どおり |
| 07:27:19〜07:33:19 | 通常の流れ | 26 回。定義・ピッカー・ラベルは 200、作成は 201、更新は 200、`K999` は 409 `BUSINESS_REFERENCE_NOT_FOUND`（25 件） |
| 07:33:19〜07:36:43 | 使い捨ての PG を `docker stop` し、同じ流れを送った（Q3: A） | 6 回。定義の `VIEW` は 6 件すべて 200。それ以外の 49 件はすべて 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（定義の `CREATE` 6・一致 6・部分一致 6・ラベル 6・保存前の確かめつきの作成 7・`K999` 6・参照の無い作成 6。更新は作成が 503 のため送っていない） |
| 07:36:43〜07:46:24 | PG を戻し（07:36:43 healthy）、同じ流れを送った | 41 回。戻した直後の1回（07:36:49）だけ、参照の無い作成と `K999` の作成が 503（各 1 件）。ほかはすべて期待どおり |
| 07:46:24〜07:48:36 | 何も送らない | — |

- lgtm は止められませんでした（`OOMKilled` false・再起動 0、終わりの使用 1.119 GiB／1.5 GiB）。使い捨てのアプリも `OOMKilled` false・再起動 0（543 MiB／1 GiB）。
- 台本の誤りの行（`level=error`）は 0 件でした。

### 2.2 Grafana に読み込まれたこと

- 0.38.0 の lgtm で `/api/health` は 200・`database` `ok`（Grafana 13.2.4）。`/api/search?dashboardUIDs=mastersmith-overview` は 1 件（U5 の 2節の確かめと同じ形）。
- 書き換える前のファイルではパネル 45 件。書き換えた後、ファイルの差し込みが読み直し、`/api/dashboards/uid/mastersmith-overview` でパネル **47 件**、id 46・47 の題が 1節の表のとおりであることを確かめました。

### 2.3 名前の確かめ（書く前、07:26 UTC。Q2: A の試しの要求の後）

| 確かめたこと | 結果 |
|---|---|
| 指標の名前 | `http_server_requests_milliseconds_count`（`_seconds_` ではない） |
| `uri` の値 | 参照の道 `/api/tables/reference/rows`、定義の道 `/api/tables/definition`（`BusinessDataPaths` の定数のとおり、変数を持たない）。作成と更新は `/api/tables/rows` で、`method` が `POST`・`PATCH`（新しい `uri` の値は増えていない） |
| ラベル | `service_name`・`method`・`uri`・`status` |
| バケット（`le`） | 参照の道に `100`・`250`・`500`・`1000`・`2000`・`5000`・`+Inf`。`+Inf` 以外がある |

### 2.4 区画のパネルの式の結果（07:25〜07:49 UTC、60 秒ごと）

区画のパネル 40〜47 の 15 の式（44・45 は3つずつ）を、Grafana のデータソースの代理（`/api/datasources/proxy/uid/prometheus/api/v1/query_range`）で流しました。**15 件すべて `success`** で、値が返り、NaN は 0 件でした。

| パネル | 系列 | 値の最大（`increase` の外挿を含む） | 実際の件数との比べ |
|---|---|---|---|
| 40 要求の数 | 8 系列（定義 200・503、参照の道 200・503、作成 201・409・503、更新 200） | 参照の道 200 は 213.1、定義 200 は 151.9、作成 201 は 139.6、409 は 69.8、503 は定義 6.2・参照の道 16.4・作成 24.0 | 台本の数（試しと setup を含む）: 参照の道 200 は 207、409 は 68、作成 201 は 136、503 は定義 6・参照の道 18・作成 21。前の Intent と同じく、外挿で多めに、新しい系列の最初の点の分だけ少なめに出る |
| 41 p95 | 4 系列（`PATCH`・`POST` の rows、定義、参照の道） | 通常の間は 95 前後（すべて `le="100"` の中の補間の値。部分一致の検索を含む参照の道は 227.5〜417.1）。PG を止めた間は 4,437〜4,803 ms（借りる待ちの 3 秒が `le="5000"` に入る） | 前の Intent と同じ見え方 |
| 42 503 の件数 | `vector(0)` の1系列と、定義・参照の道・rows の3系列 | 定義 6.2・参照の道 16.4・rows 24.0 | 上の 40 と同じ値（同じ式の 503 だけ） |
| 43 409 の件数 | `vector(0)` の1系列と rows の1系列 | 69.8 | 台本の 409 は 68（`K999` の作成）。409 は rows（作成）にだけ出る |
| 44 読み取りのプール | 使用中・待ち・時間切れ（1 時間） | 使用中 1・待ち 1・時間切れ 40.0 | PG を止めた間の参照の読み取りと保存前の確かめの 503 は 38 件（定義 6・参照の道 18・作成 14）。時間切れの数と合う |
| 45 書き込みのプール | 同上 | 使用中 0・待ち 0・時間切れ 7.4 | 参照の無い作成の 503 は 7 件（書き込みの接続を借りる待ち）。合う |
| 46 参照の API の要求の数 | 4 系列（定義 200・503、参照の道 200・503） | 40 の同じ系列と同じ値 | 期待どおり（参照の2つの道だけを数える） |
| 47 参照の API の 503 | `vector(0)` と、定義・参照の道の2系列 | 定義 6.2・参照の道 16.4 | 期待どおり。PG を戻した後も `vector(0)` の系列が残る |

- パネル 41 では、部分一致の検索の目標 3,000 ms がバケットの境界（2,000・5,000）に無いため、2,000〜5,000 ms の間を見分けられません。サーバー側の p95 は参考とし、合否は Performance Validation の k6 の `iteration_duration` で決めます（Q4: A、3節）。

## 3. 3,000 ms のバケット（Q4: A）

`application.yaml` の `management.metrics.distribution.slo` の `http.server.requests`（`100ms,250ms,500ms,1000ms,2000ms,5000ms`）は変えていません。足すと、ブランチ・`./gradlew verify`・統合・WAR の作り直し・配備のやり直しが要り、道・方法・状態コードの組ごとに系列が1つずつ増えます。部分一致の検索の 3,000 ms の目標は k6 で判定し、パネル 41 の値は参考です。配備先が決まり監視を常に動かすようになったときに、足すかを決め直します（`anomaly-config.md` 3節）。

## 4. 設計との差（承認済みの文書は書き換えない）

| ID | 上流 | 上流の記載 | この段の記録 | 理由と扱い |
|---|---|---|---|---|
| D-D1 | U1 の 2節 | パネル 46・47 の式は仮で、`uri` の値を確かめてから確定する | 確かめた値（2.3節）が仮の式と同じで、式は変えずに確定した | 設計の手順どおり |
| D-D2 | U1・U2 の 2節（「仮の目標の一覧」に触れていない） | — | 「仮の目標の一覧」に1行を足した | Q5: A。警報もしきい値も変えない、記録だけの差 |
| D-D3 | U2 の 2節（42 の追記の文） | 見分けは「ログの WARN のキー `purpose`・`stage`」 | 42 の説明に `purpose` の値（`REFERENCE`・`WRITE`）を書いた | 2.4節と `log-queries.md` 1節で、保存前の確かめの WARN は `purpose=REFERENCE`・`stage`、書き込みの WARN は `purpose=WRITE`・`stage=BORROW` と確かめた |
| D-D4 | `observability-setup-questions.md` Q2: A の用意 | `refPickerEquals`・`refCreateChecked` を 5 秒だけ流してロールを作る | 台本の setup で1つのロール `perf-obs-ref` を作り `perf-biz-02` に割り当てた | 1人の利用者で参照の読み取りと保存前の確かめつきの作成を同じ作業ロールで回すため（既存の場面は場面ごとに別のロールで、作業ロールが1つしか持てない）。使い捨ての環境の監査に残る操作の種類は同じ |
| D-D5 | U3 の「後の段に回すこと」 | 画面からの操作でも系列が入ることを1回見る（任意） | 行っていない | 台本の要求が画面と同じ道を通るため（`observability-setup-questions.md` の Q2 のあとの注記） |

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/observability-setup-questions.md`（1節・2節、Q1〜Q5 すべて A）
- 基盤の設計: `construction/u1-reference-read/infrastructure-design/monitoring-design.md`（2節・6節）・`construction/u2-reference-write-check/infrastructure-design/monitoring-design.md`（2節・6節）・`construction/u3-reference-screens/infrastructure-design/monitoring-design.md`（1節）・`construction/u5-container-perf-docs/infrastructure-design/monitoring-design.md`（2節・7節）、U1・U2・U5 の `infrastructure-specification.md`（VM の余裕）
- NFR 設計: U1・U2 の `performance-design.md`（目標の 1,000 ms・3,000 ms）・`reliability-design.md`（503 と 409 への写し）・`security-design.md`（道は固定）
- Build and Test: `construction/build-and-test/build-and-test-summary.md`（OBS-PANELS ほか）
- 前の Intent の同じ段: `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/dashboards.md`
- 変えたファイル: `docker/monitoring/dashboards/mastersmith-overview.json`。読み取りで確かめたファイル: `docker/monitoring/provisioning/dashboards/mastersmith.yaml`・`backend/src/main/resources/application.yaml`・`backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataPaths.java`・`docker/perf/compose.yaml`・`perf/README.md`
- 実行したコマンドの出力（2026-11-01 UTC）: Grafana の `/api/health`・`/api/search`・`/api/dashboards/uid/mastersmith-overview`、Prometheus の代理の `query`・`query_range`（リポジトリの外の `~/.mastersmith-tools/pq.sh`）、`docker inspect`・`docker stats --no-stream`、`pmset -g assertions`・`pmset -g log`

## Assumptions & Open Questions

- 台本の件数とパネルの値の差は、`increase` の外挿と新しい系列の最初の点の扱いによるものと読みました（前の Intent と同じ）。件数の正は台本の数と監査の行（`log-queries.md` 3節）です。
