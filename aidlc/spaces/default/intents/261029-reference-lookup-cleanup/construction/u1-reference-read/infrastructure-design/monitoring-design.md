# 監視の設計 — U1 u1-reference-read

NFR 設計の監視の考え（`observability-design.md`）を、手元の監視（grafana/otel-lgtm。compose の profile で見たいときだけ起動）の具体の式とパネルに落とす。ダッシュボードと警報の決まりはリポジトリのファイルで持ち、画面からは変えない（`project.md` の学び）。警報は足さない（NFR2.10・Q2: A）。

## 出典

- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-design-questions.md`（Q2: A）
- この単位の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/observability-design.md`（1節〜5節）・`reliability-design.md`（2節）・`logical-components.md`（3節）
- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/observability-requirements.md`（NFR1.15・NFR2.10・NFR3.8）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md`（道）
- 読み取りだけで確かめた既存の設定: `docker/monitoring/dashboards/mastersmith-overview.json`（業務データの区画 id 39〜45、指標の名前 `http_server_requests_milliseconds_count`・`_bucket`、ラベル `service_name`・`method`・`uri`・`status`）、`docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-biz-target-db`）、`backend/src/main/resources/application.yaml`（`management.metrics.distribution.slo` の `http.server.requests: 100ms,250ms,500ms,1000ms,2000ms,5000ms`）

## 1. 指標と KPI

指標の名前は、このリポジトリの既存の式で使われている `http_server_requests_milliseconds_*`（`_seconds_` ではない）。道のラベル `uri` は Spring MVC の道の雛形で、参照の2本の道は変数を持たないため、値はそのまま `/api/tables/definition` と `/api/tables/reference/rows` になる見込み（Observability Setup で使い捨てのアプリで確かめる）。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 参照の API の要求の数（道・方法・状態コードごと） | `http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/(definition\|reference/rows)"}` | なし（量を見る） | 参照の API の量を、業務データの一覧・詳細・書き込みと分けて見る（Q2: A） |
| 参照の API の 503 の数 | 同じ系列の `status="503"` | 1 件でも（警報は既存の `ms-biz-target-db`） | 対象DB に届かない・答えない（`BUSINESS_TARGET_DB_UNAVAILABLE`・`BUSINESS_TARGET_DB_TIMEOUT`）の量 |
| 業務データの API の時間（95 パーセンタイル） | 既存のパネル id 41（`uri=~"/api/tables/.*"`）。新しい道が自動で入る | 目安の線 1,000 ms（判定は k6） | 部分一致の線 3,000 ms はバケットの境界に無い（2節の最後） |
| 対象DB の読み取りのプールの使用中・待ち・時間切れ | 既存のパネル id 44（`pool="mastersmith-target-db"`） | 時間切れが 0 でない | 参照の読み取りは一覧・詳細と同じプールを使う |
| 参照の読み取りの失敗の種類 | アプリのログ（Loki）の診断の WARN（ロガー `ReferenceLookup`、キー `purpose=REFERENCE`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`） | なし（調べるときに見る） | 時間切れ・借りられない・型の食い違い・全体の上限の超過を見分ける（`observability-design.md` 2節） |

## 2. ダッシュボード（Q2: A）

既存の `docker/monitoring/dashboards/mastersmith-overview.json` の「業務データ（Intent 261016-business-data-views）」の区画（行 id 39、最後の区画）の下に、パネルを2つ足す。新しい区画の行は作らない。

| id | 題 | 種類・置き場 | 式 | 説明（description に書く） |
|---|---|---|---|---|
| 46 | 参照の API の要求の数（1 時間） | timeseries、`gridPos` x 0・y 106・w 8・h 8 | `sum by (method, uri, status) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/(definition\|reference/rows)"}[1h]))` | 定義の取得（`POST /api/tables/definition`。選択肢を読む `purpose` の `CREATE`・`UPDATE` と、読まない `VIEW` を合わせた数）と、ピッカーの検索・ラベルの1件読み（`POST /api/tables/reference/rows`。2つは同じ道）の要求の数。`purpose` と検索かラベルかは本文にあり、指標では分けられない |
| 47 | 参照の API の 503 の件数（1 時間） | timeseries、`gridPos` x 8・y 106・w 8・h 8 | `sum by (method, uri) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/(definition\|reference/rows)",status="503"}[1h])) or on() vector(0)` | 対象DB に届かない・答えない（`UNAVAILABLE`・`TIMEOUT`、全体の上限の超過を含む）。code は指標のラベルに無いため、見分けはログの WARN のキー `reason`・`budgetExceeded` で行う。警報 `ms-biz-target-db` が同じ 503 を 5 分の窓で数える |

パネル 46・47 の式は**仮のもの**とする。`uri` のラベルの値（`/api/tables/definition`・`/api/tables/reference/rows`）は新しい道がまだ無いため確かめておらず、Observability Setup で使い捨てのアプリの実際の値を確かめてから式を確定して `mastersmith-overview.json` に書く（6節の順 1。R-02）。値が違えば式の `uri` の条件をその値に直す。説明の「定義の取得は `VIEW` を含む」（参照だけの量・失敗とは言い切れない）は、確定した後も残す。

表の中の `\|` は Markdown の表のための書き方で、JSON の式の中では `|` 1つ（`uri=~"/api/tables/(definition|reference/rows)"`）。

既存のパネルの説明の直し:

| id | 直し |
|---|---|
| 40 | 「業務データの6本の API」に、参照先の検索とラベル（`POST /api/tables/reference/rows`）を足して7本にし、定義の取得が `purpose` によって対象DB を読むことを書く |
| 42 | 503 に参照の読み取り（定義の取得・参照先の検索とラベル）を含むことを足す |

部分一致の線 3,000 ms は `http.server.requests` のバケットの境界（2,000 と 5,000 の間）に無いため、id 41 のサーバーの側の p95 は参考とし、合否は k6 の `iteration_duration` で決める。3,000 ms のバケットを足すかは Observability Setup で依頼者に諮る（NFR 要件からの引き継ぎ）。足すと `application.yaml` の `slo` を変えることになり、道・方法・状態コードの組ごとに系列が1つずつ増える。

## 3. 警報

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-biz-target-db`（既存・変えない） | `sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/.*",status="503"}[5m])) or vector(0)` が 1 以上、`for: 0s` | 中 | 手元の Grafana の警報の一覧だけ（通知の先は配備先が決まるまで置かない）。新しい道 `/api/tables/reference/rows` と定義の道の 503 は、この式の `uri=~"/api/tables/.*"` に入る |
| `ms-pool-pending`（既存・変えない） | 内部DB・対象DB のプールの待ちが 0 でない状態が 1 分 | 既存のまま | 同上 |
| `ms-5xx-ratio`・`ms-error-logs`（既存・変えない） | 5xx の割合・ERROR のログの数 | 既存のまま | 同上。想定外（500）は既存の ERROR で入る |

新しい警報は足さない（NFR2.10・NFR3.8・Q2: A）。参照先ごとの遅さは区画では見分けない（受け入れた制約。道は固定で値は本文で運ぶため、参照先ごとに系列を作らない）。

## 4. SLI と SLO

手元の監視を常に動かしていない間は、SLO を新しく置かず、判定は `Unverified` とする（`project.md` の学び）。目安は次のとおりで、合否は Performance Validation の k6 で決める。

| SLI | SLO target | Measurement window |
|---|---|---|
| 選択肢つきの定義の取得（200 行・201 行）・ピッカーの一致の検索・ラベルの1件読みの `iteration_duration` の p95 | 1,000 ms 未満（NFR2.1。緩めない） | k6 の場面ごと 3 分・10 VU、3種類の対象DB |
| ピッカーの部分一致の検索の `iteration_duration` の p95 | 3,000 ms 未満（NFR2.1） | 同上 |
| 参照の API の 503 の数 | 平常で 0（警報 `ms-biz-target-db` の 5 分の窓） | 手元の監視を動かしている間 |

## 5. ログとトレース

| 項目 | 決めたこと |
|---|---|
| ログの集め方 | 既存のまま。アプリの JSON のログを otel-collector 経由で Loki へ（外部エクスポートは既定で無効、手元の監視を起動したときだけ） |
| 診断の WARN | 1つの失敗について `ReferenceLookup` のロガーで1行、スタックトレースなし。既存の `GlobalExceptionHandler` の変換の WARN が別の種類で1行出る（`observability-design.md` 2.1） |
| 出さないもの | スキーマ・テーブル・カラムの名前、値・ラベル・セル、接続先、例外のメッセージ（`project.md` の Forbidden） |
| Loki の問い合わせ（調べるとき） | `{service_name="mastersmith"} \| json \| purpose="REFERENCE"`。キーの名前は Observability Setup で実際のログで確かめてから手順書に書く |
| トレース | 既存の Micrometer Tracing の範囲のまま、新しいスパンを足さない。道の属性は固定の道だけで値を持たない（BR10.3） |

## 6. 確かめ方（Observability Setup）

| 順 | 確かめ |
|---|---|
| 1 | 書く前に使い捨てのアプリで、`uri` のラベルの値（`/api/tables/definition`・`/api/tables/reference/rows`）と `+Inf` 以外のバケット（`le`）を確かめる。確かめた値で 2節のパネル 46・47 の仮の式を確定する（R-02） |
| 2 | 要求は指標の送信の周期（1 分）を複数またいで、待ちを入れた少ない数で送る（負荷の試験の場面ではなく。lgtm の受け手がメモリの上限で止まった学び） |
| 3 | 2節の2つのパネルと既存の id 40〜44 の式を流し、新しい道の系列が入ることを確かめる |
| 4 | 使い捨ての環境で対象DB を止め、新しい道と定義の道の 503 で `ms-biz-target-db` が firing になることを確かめる。`docker pause` では借りる待ちの `UNAVAILABLE` になり、`TIMEOUT` は問い合わせの最中に止めないと起きない |
| 5 | lgtm を網につないだ後にアプリを起動する（先に起動するとログの送信の失敗の ERROR が出る） |
| 6 | 3,000 ms のバケットを足すかを依頼者に諮る |

確かめのために送る要求で監査に残るものは無い（参照の読み取りは監査に残さない。BR1.7）。前提を作る操作（ロール・割り当て・DSL の適用）は使い捨ての環境の監査に残る。

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| NFR 設計 `observability-design.md` 1節（区画の説明に新しい道を足すだけ） | Q2: A で、要求の数と 503 の数のパネルを2つ足す | 承認済みの NFR 設計は書き換えない。この段の答えで決めた |
| NFR 要件 NFR2.10（新しい道 `POST /api/tables/reference/rows` が既存の式に入る） | 定義の道 `/api/tables/definition` も参照の読み取りの入口として、新しいパネルの式に入れる。ただし `VIEW` の定義の取得と分けられない | パネルの説明に書く（2節） |

## 後の段に回すこと

- **Observability Setup**: 6節の確かめ、2節の2つのパネルと既存の説明の直しを `mastersmith-overview.json` に書く（名前を確かめてから）、3,000 ms のバケットの要否を依頼者に諮る、Loki の問い合わせのキーの確かめ。
- **Code Generation**: 診断の WARN の1行とそのテスト（`ReferenceSecretLeakIT` の中。`observability-design.md` 2.1）。
- **Incident Response**: 参照の 503 の見分け方（ログの `reason`・`budgetExceeded`）を手順書に足す。

## 承認の場の決定

段の承認の場で、依頼者は Request Changes を選び、レビュー（`reviews/review-01.md`）の3つの Minor の指摘をすべて示された直し方で直すと決めた。この文書に関わるのは R-02。

| 指摘 | 決定 | 反映した場所 |
|---|---|---|
| R-02（パネル 46・47 の `uri` の値が未確認、定義の道は `VIEW` を含む） | パネルの説明に定義の取得が `VIEW` を含むことを書いたまま、式は Observability Setup で `uri` の実際の値を確かめるまで仮とする | 2節の表の後の注記、6節の順 1 |
| R-01・R-03 | `cicd-pipeline.md`・`infrastructure-specification.md` で直した（新しい警報は足さない判断は 3節のまま） | 各文書の「承認の場の決定」 |
