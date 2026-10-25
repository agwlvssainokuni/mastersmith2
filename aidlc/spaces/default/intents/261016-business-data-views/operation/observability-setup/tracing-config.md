# トレース（tracing-config）

前の Intent の記録（`aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/tracing-config.md` と、それが正とする前々回までの記録）を正とします。**トレースの設定は変えていません**。
- Micrometer Tracing、`MASTERSMITH_TRACING_SAMPLING_PROBABILITY` の既定 1.0、W3C の伝え方
- この Intent で新しいスパン・属性は無い。業務データの API の道は固定で、行の値と主キーは本文で運ぶ（U1・U2 の `monitoring-design.md` 4節、契約 CQ1: A）

## 1. 今回の確かめ（使い捨ての環境、`measurement-notes.md` 9節）

前回の2回目の窓（2026-10-25 02:37〜03:15 UTC）で、Tempo を `{ span.uri =~ "/api/tables.*" }` で検索しました。問い合わせの上限の 2,000 件のトレースを読み、名前と道の名前と件数だけを見ています。

| span の名前 | 件数 |
|---|---|
| `http post /api/tables/rows/get` | 350 |
| `http post /api/tables/rows/search` | 348 |
| `http post /api/tables/rows` | 528 |
| `http post /api/tables/definition` | 255 |
| `http post /api/tables/rows/delete` | 260 |
| `http patch /api/tables/rows` | 259 |

- span の名前は6つの固定の道の形だけでした。
- 属性 `uri` と `http.url` の値は、5つの固定の道だけでした: `/api/tables/definition`・`/api/tables/rows`・`/api/tables/rows/search`・`/api/tables/rows/get`・`/api/tables/rows/delete`。**固定の道でない値（問い合わせの部分や値を含むもの）は 0 件**です。
- 属性 `status` は 200・201・403・409・503、`method` は POST・PATCH でした。
- Tempo の span の属性の名前（タグの一覧）は次のとおりで、行の値・資格情報に当たる名前はありません。
  - `uri`・`http.url`・`method`・`status`・`outcome`・`exception`
  - Spring Security の決まりの名前（`spring.security.*`）
- 漏えいの確かめの正は、外部エクスポートを有効にした結合テスト（verify で毎回）です。この段の確かめは、手元の監視に届いた形の確かめです。

## 2. 要求1件の追い方

1. Grafana の Explore（Loki）で、`log-queries.md` 2節の L1・L2（503）や L4（403・409）から気になる行を開く。
2. 行の `trace_id` から Tempo のトレースを開き、時間と状態（`status`）を見る。対象DB に届かない 503 のトレースは、約 3 秒（借りる待ちの上限）でした（例: 詳細 `http post /api/tables/rows/get` の 503 で 3,013 ms）。
3. 監査の行（`BUSINESS_ROW_*`）の `trace_id` は、同じ要求のログの `trace_id` と一致します（前の Intent までの記録のとおり）。業務データの 403 は監査に残らないため、ログとトレースだけで追います。

## 3. 外部エクスポート（決まっていること）

- 配備したアプリの外部エクスポートは**有効にしていません**。配備した `.env` は開いておらず、アプリも作り直していません。
- 確かめでは、使い捨てのアプリにだけ、ホームの下の一時の環境ファイル（権限 600）で `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318` を渡しました。
- 使い捨ての lgtm（`mastersmith-perf-lgtm`）は使い捨ての網に別名 `lgtm` でつなぎ、**網につないだ後にアプリを起動しました**。前回・この段とも、lgtm を網につなぐ前の送信の失敗の ERROR は出ていません。
- 毎秒 1,500〜2,200 件の要求のトレースを送ると、lgtm の中の受け手（otelcol）と Tempo がメモリの上限（1536m）で止められました（前回の1回目、`measurement-notes.md` 7節）。負荷の試験の間に lgtm を動かすときは、要求の量を絞るか、トレースの量の見込みを先に立てます。

## 4. 設計との差（承認済みの文書は書き換えない）

| ID | 上流 | 上流の記載 | この段の記録 | 理由と扱い |
|---|---|---|---|---|
| T-D1 | 依頼の確かめの項目（span の名前と `http.route` の値） | 属性 `http.route` で道を確かめる | Tempo の span に `http.route` の属性は無かった。道は属性 `uri` と `http.url` にある | 実機のタグの一覧で確かめた（1節）。確かめは `uri`・`http.url` で行った |
| T-D2 | 前の Intent の `dashboards.md`（招待と登録の行が `traces_spanmetrics_latency` を使う） | トレースから作る指標で道ごとの時間を見られる | 前回の2回目で、`traces_spanmetrics_calls_total` には6本のうち4本（作成・一覧・詳細・定義）の span の名前しか出ず、`http patch /api/tables/rows`・`http post /api/tables/rows/delete` は無かった（理由は確かめていない） | 業務データの区画は `traces_spanmetrics_*` を使わず、6本とも揃う `http_server_requests_*` で書いた（`dashboards.md`） |

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/observability-setup-questions.md`（決まっていること、Q1: A）
- この段の `measurement-notes.md`（7節・9節）・`dashboards.md`・`log-queries.md`
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/tracing-config.md`
- 基盤の設計: `construction/u1-businessdata-read/infrastructure-design/monitoring-design.md`（4節）・`construction/u2-businessdata-write/infrastructure-design/monitoring-design.md`（4節）
- `backend/src/main/resources/application.yaml`（`management.tracing`・`mastersmith.observability.export`）、`docker/perf/compose.yaml`
- 実行したコマンドの出力: `curl -s http://127.0.0.1:13000/api/datasources/proxy/uid/tempo/api/search?...`・`api/v2/search/tags`（名前と道の名前と件数だけを見た）、`curl -s http://127.0.0.1:13000/api/datasources/proxy/uid/prometheus/api/v1/query?...`（`traces_spanmetrics_calls_total` の span の名前）

## Assumptions & Open Questions

- `traces_spanmetrics_calls_total` に PATCH と rows/delete が出なかった理由は確かめていません（`Unverified`）。区画は `http_server_requests_*` を使うため、影響はありません。
