# 監視の設計 — U1 u1-businessdata-read

この文書は、承認済みの `observability-design.md`（NFR 設計）の監視を、手元の監視の上でどう見るかを表にします。手元の監視は `grafana/otel-lgtm` を compose の profile `monitoring` で、見たいときだけ起動する形です。

- 独自の指標と新しい警報は足しません。
- ダッシュボードの「業務データ」の区画は、Observability Setup が足します。B2・B4 は `docker/monitoring/` に触れません（決まっていること）。

## 1. Metrics & KPIs

指標の名前は、手元の監視（Prometheus）での名前です。実際の名前・ラベル・`le` のバケットは、Observability Setup で起動して確かめてから式を書きます（`project.md` の学び）。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 3本の API の要求の数（`http_server_requests_milliseconds_count`、`uri` は `/api/tables/definition`・`/api/tables/rows/search`・`/api/tables/rows/get`、`status` ごと） | Spring Boot の `http.server.requests`（既存）。道は固定で、値は本文で運ぶため `uri` に値は載らない | なし（見るだけ） | 読み取りの量と、403・400・503 の傾向を見る |
| 3本の API の p95（`http_server_requests_milliseconds_bucket`） | 同上。`slo` のバケットに 1000 ms の境界がある（`application.yaml` 289行） | 目安の線 1000 ms | SLO の基準の値を見る。判定は k6（3節） |
| 503 の数（`status="503"` と `uri` ごと） | 同上。応答の code のラベルは無い見立て（Observability Setup で確かめる） | なし | 対象DB に届かない・答えないこと（UNAVAILABLE・TIMEOUT）を見る |
| 対象DB のプールの使用中・待ち・待ちの時間切れの累計（`hikaricp_connections_active`・`hikaricp_connections_pending`・`hikaricp_connections_timeout_total`、`pool="mastersmith-target-db"`） | HikariCP の指標（既存）。プールの名前は `TargetDataSourceConfig.java` 43行 | なし（見るだけ）。負荷の試験の合否は、待ちの時間切れの累計 0 | 上限 5 に届いたときの詰まりと、応答しない対象DB で借りられたままの接続を見る |

## 2. Alerts

新しい警報は足しません（決まっていること）。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-forbidden`（拒否の増加） | 全体の 403 の件数。テーブルの主権限が NONE の業務データの 403 も入る | 既存のとおり | 手元の Grafana の画面だけ（通知の先は作らない）。しきい値（20）の見直しは Observability Setup（NFR8.1） |
| `ms-5xx-ratio`（5xx の割合の増加） | 全体の 5xx の割合。業務データの 503 も入る | 既存のとおり | 同上。ほかの API の要求が多いと割合が小さく、鳴らないことがある。R-05 で実測する |
| `ms-pool-pending`（プールの待ち） | 全プールの待ちの最大。対象DB のプールも入る | 既存のとおり | 同上。接続が作れないとき（3 秒で切れる）は待ちが増えにくい。R-05 で実測する |
| `ms-error-logs`（ERROR のログの増加） | 想定外の失敗（500）の ERROR | 既存のとおり | 同上 |
| （拾わない）3本の API の p95 | 既存の p95 の警報は、`uri` を1つの道に固定している | — | 足さない |

**R-05 の実測（Observability Setup）**
- 使い捨ての環境の PG（768m。`infrastructure-specification.md` 2.3）を、次の2つの状態にして業務データの API を送る。
  - 止めた状態
  - つないだ後に止めた状態（`docker pause`）
- 要求は、指標の送信の周期（1 分）を複数またいで送る。
- そのうえで、`ms-5xx-ratio`・`ms-pool-pending` の式の値を実測する。拾えなければ、業務データの 503 に絞った警報を足すかを、その段の判断事項にする。
- lgtm を網につないだ後に、使い捨てのアプリを起動する（`project.md` の学び）。

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 一覧・詳細・定義の応答時間（k6 の `iteration_duration{scenario:…}`。場面は `bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizDetail`・`bizSearchWide`・`bizDefinition`） | p95 < 1000 ms、`checks` の率 = 1 | Performance Validation の場面ごと（10 VU）。3種類の対象DB で1つずつ。表が入る設定で測る |
| 対象DB のプールの待ちの時間切れの累計（`hikaricp_connections_timeout_total`、actuator の metrics で読む） | 0 | 同上。待ちの最大は記録だけ。揺れるため複数回流す |
| 503 の件数（k6 の状態コードの数え） | 0 | 同上 |
| アプリのコンテナの `memory.peak`（`bizSearchWide`） | 2g の 90% 未満。基準の値と `anon` を並べて記録する | 同上。流す前にコンテナを作り直す |
| 手元の監視の3本の API の p95 | 目安 1000 ms（判定に使わない） | 手元の監視を常に動かしていない間は `Unverified`。配備の直後・監視の確かめ・振り返りの時点の値を、基準の値として並べる |

SLO の値は緩めません。表が入る設定でも超える場面は Not Met として、Performance Validation で扱いを諮ります（NFR 設計 F1: A）。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| アプリのログ | 対象DB の失敗と値の型の誤りは WARN の1行（スタックトレースなし）。キーは `purpose`・`reason`・`sqlState`・`vendorCode`・`exceptionClass` だけ（NFR8.2）。出すのは `BusinessDataReadService` の1か所 |
| 出さないもの | 値・主キー・検索の値・接続先・例外のメッセージ。スキーマ名とテーブル名もキーに出さない |
| 口の中の WARN | 接続を閉じる処理の失敗と、通信の待ちを戻す処理の失敗だけ。例外のクラスの名前だけを出す |
| ログの集め方 | 既存のまま。外部エクスポートは既定で無効で、手元の監視を見るときだけ `.env` で有効にする |
| トレース | 道は固定で、`uri`・`http.route` の属性に値は載らない。JDBC の文は属性に載せない（JDBC の観測の仕組みを足さない） |
| 監査 | 読み取りは監査に残さない（承認済み） |
| 漏えいの確かめ | `BusinessDataSecretLeakIT`（TRACE を有効にしたログ）と、外部へ送る設定を有効にした結合テスト（トレースの属性）を verify で毎回流す |

## 5. ダッシュボードの区画（持ち主は Observability Setup）

`docker/monitoring/dashboards/mastersmith-overview.json` に、区画「業務データ（Intent 261016-business-data-views）」を1つ足します。

| パネル | 式の形（名前は起動して確かめてから書く） | 単位 |
|---|---|---|
| API ごとの要求の数（1 時間） | `sum by (method, uri, status) (increase(http_server_requests_milliseconds_count{…,uri=~"/api/tables/.*"}[1h]))` | U1（U2 の3本も同じ正規表現で入る） |
| API ごとの時間（95 パーセンタイル） | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_milliseconds_bucket{…}[5m])))` | U1・U2 |
| API ごとの 503 の件数（1 時間） | `status="503"` と `uri` ごと | U1・U2 |
| 対象DB の読み取りのプール | `pool="mastersmith-target-db"` の使用中・待ち・待ちの時間切れの累計 | U1 |
| 対象DB の書き込みのプール | U2 の書き込みのプールの名前 | U2 |

Observability Setup での確かめ:
- `+Inf` 以外の `le` があることを確かめる。
- 要求を指標の送信の周期（1 分）を複数またいで送ってから、すべての式を流す。
- NaN を、要求が無いためと決めつけない。
- 業務データの API を読むには、適用した DSL・ロール・作業ロールと、対象DB の接続先が要る。用意の操作は監査に残るため、前もって依頼者に伝える（使い捨ての環境なら、配備した内部DB を汚さない）。

## 6. 後の段に回すこと

- **コード生成の計画（B2）**
  - ログのキーの名前を決める。
  - `BusinessDataSecretLeakIT` に、ログの JSON のキーと値が無いことの確かめを入れる。
  - 環境変数の名前・README の節・k6 の場面・種、SpotBugs と境界テストの承認（`cicd-pipeline.md`）。
- **Performance Validation**
  - 3節の SLO の合否の回
  - 待ちの時間切れの累計・待ちの最大・`memory.peak` の記録
  - その回の対象DB の設定の記録（`infrastructure-specification.md` 2.2）
- **Observability Setup**
  - 5節の区画を書いて、式を確かめる。
  - 2節の R-05 の実測と、警報を足すかの判断
  - NFR8.1 の `ms-forbidden` のしきい値の見直し

## 7. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `observability-design.md` 2節 | 元の指標を `http.server.requests`、プールを `hikaricp.connections.*` と書く | 手元の監視の名前は `http_server_requests_milliseconds_*`・`hikaricp_connections_*`。式は起動して実際の名前を確かめてから書く | 既存の式（`docker/monitoring/provisioning/alerting/mastersmith.yaml`・`mastersmith-overview.json`）の名前。確かめる中身は同じ |
| `nfr-design/logical-components.md` 5節 | 「`.env` の項目は増えない」 | 一覧・詳細の時間の上限を環境変数で受ける（`infrastructure-specification.md` 1.1） | Q4: A。監視には響かない |
| レビューの R-06 | PG の共有バッファーの変える中身が書かれていない | PG は `shared_buffers` 256MB・上限 768m で測る | Q2: A。3節の SLO の測る環境の条件 |

## 8. U2・U3 とそろえる点

- **U2**
  - 書き込みの失敗のログも、同じキーと WARN にそろえる。
  - 区画は1つにまとめ、書き込みの3本と書き込みのプールのパネルを足す。
  - 書き込みのプールの SLO も、同じ物差し（待ちの時間切れの累計 0・503 の件数 0、待ちの最大は記録だけ）にする。
  - 対象DB の設定は、同じ値で測る。
- **U3**
  - 画面の誤りはサーバーのログに出ない（画面の側の扱い）。
  - 画面の時間の目標は、U1 の Performance Validation の結果に合わせる。

## 9. 未確認の点

- **VM の中の空き**: lgtm と使い捨ての環境を同時に動かす R-05 の実測（上限の合計 5,120 MiB）は、VM の OS と dockerd の使う量を測っていない。余裕が足りなければ、見本の PG・Mailpit も止める。
- **MariaDB の実測**: MariaDB の上限 1g は見立ての値。3節の回の対象DB の `memory.peak` で確かめる。
- **MySQL の丸め**: バッファープールの実際の値は、起動して確かめて記録する。
- 応答の code ごとのラベルが `http.server.requests` に無いことは確かめていない（見立て）。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q4: A、決まっていることのダッシュボードの区画、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u1-businessdata-read/nfr-design/observability-design.md`（1〜7節）
  - `construction/u1-businessdata-read/nfr-design/scalability-design.md`（4節・5.3）、`performance-design.md`（4節）、`reliability-design.md`（1.2）
  - `construction/u1-businessdata-read/nfr-requirements/observability-requirements.md`（NFR8.1〜NFR8.3）、`performance-requirements.md`（NFR1.1・NFR1.2）
  - `.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json`（R-05・R-06）
- 既存のもの（正とする。読むだけ）: `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-5xx-ratio`・`ms-pool-pending`・`ms-forbidden`）・`docker/monitoring/dashboards/mastersmith-overview.json`・`backend/src/main/resources/application.yaml`（287〜291行）・`backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`・`compose.yaml`（lgtm）
- 決まり: `aidlc/spaces/default/memory/project.md`（式は書く前に起動して確かめる、p95 のバケット、送信の周期を複数またぐ、lgtm を先につなぐ）

## Assumptions & Open Questions

- 前提: 業務データの3本の API は、既存の `http.server.requests` の `slo` のバケットに入る（道ごとの設定ではない）。
- 前提: 対象DB のプールの指標は、`pool` のラベルで内部DB のプールと見分けられる（Observability Setup で確かめる）。
- 依頼者の決定（区画の持ち主は Observability Setup、Q3: A の lgtm の扱い）は、2節・5節に反映した。
- 諮る点は無い。R-05 の判断は Observability Setup に置いた。
