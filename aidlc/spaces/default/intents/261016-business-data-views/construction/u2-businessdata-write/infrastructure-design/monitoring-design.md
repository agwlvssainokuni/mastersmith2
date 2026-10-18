# 監視の設計 — U2 u2-businessdata-write

この文書は、承認済みの `observability-design.md`（NFR 設計）の監視を、手元の監視の上でどう見るかを表にします。手元の監視は、`grafana/otel-lgtm` を compose の profile `monitoring` で、見たいときだけ起動する形です。

- 独自の指標と新しい警報は足しません。
- ダッシュボードの「業務データ」の区画は、Observability Setup が足します（U1 と1つの区画）。B4 は `docker/monitoring/` に触れません。

## 1. Metrics & KPIs

指標の名前は、手元の監視（Prometheus）での名前です。実際の名前・ラベル・`le` のバケットは、Observability Setup で起動して確かめてから式を書きます（`project.md` の学び）。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 書き込みの3本の要求の数（`http_server_requests_milliseconds_count`。`uri` は `/api/tables/rows`（`method` が POST・PATCH）と `/api/tables/rows/delete`、`status` ごと） | Spring Boot の `http.server.requests`（既存）。道は固定で、値は本文で運ぶため、`uri` に値は載らない | なし（見るだけ） | 書き込みの量と、400・403・409・413・503 の傾向を見る |
| 書き込みの3本の p95（`http_server_requests_milliseconds_bucket`） | 同上。`slo` のバケットに 1000 ms の境界がある（U1 の基盤の設計で確かめた） | 目安の線 1000 ms | SLO の基準の値を見る。判定は k6（3節） |
| 409・503 の数（`status` と `uri` ごと） | 同上。応答の code のラベルは無い見立て（Observability Setup で確かめる） | なし | 値の拒否（409）と、対象DB に届かない・答えないこと（503）を見る |
| 書き込みのプールの使用中・待ち・待ちの時間切れの累計（`hikaricp_connections_active`・`hikaricp_connections_pending`・`hikaricp_connections_timeout_total`、`pool="mastersmith-target-db-write"`） | HikariCP の指標（既存の仕組み）。系列は初めて借りるまで出ない（minIdle 0・起動時に接続しない） | なし（見るだけ）。負荷の試験の合否は、待ちの時間切れの累計 0 | 上限 5 に届いたときの詰まりと、止まった対象DB で 10〜20 秒借りられたままの接続を見る |
| 監査の `UNKNOWN` の件数（内部DB の `audit_events.result`） | 内部DB の監査の確かめの手順（複写を読み取りで開き、件数だけを数える。値は出さない） | なし | 確定したか分からない書き込み（503 `BUSINESS_TARGET_DB_TIMEOUT`）を数える。code のラベルが無く TIMEOUT と UNAVAILABLE を見分けられない場合の手がかり |
| 対象DB のコンテナのメモリ（負荷の試験の間だけ） | cgroup の `memory.peak`・`memory.stat` の `anon`・`memory.events` | 合否は OOMKilled が false、かつ `oom_kill` が 0（F2: A） | 大きな本文の同時の書き込みで、対象DB のメモリが尽きないかを見る |

## 2. Alerts

新しい警報は足しません（決まっていること）。既存の警報が書き込みも拾うことを、Observability Setup で実測します。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-pool-pending`（プールの待ち） | `max(hikaricp_connections_pending{service_name="mastersmith"})`。プールで絞っていないため、書き込みのプールも入る | 既存のとおり | 手元の Grafana の画面だけ（通知の先は作らない） |
| `ms-5xx-ratio`（5xx の割合の増加） | 全体の 5xx の割合。書き込みの 503 も入る | 既存のとおり | 同上。ほかの API の要求が多いと割合が小さく、鳴らないことがある |
| `ms-audit-fail`（監査の書き込みの失敗） | ログの「監査イベントの記録に失敗しました」。業務データの出来事でも文は変えない | 既存のとおり | 同上 |
| `ms-audit-slow`（監査の書き込みの遅れ） | ログの「監査イベントの記録に時間がかかりました」 | 既存のとおり | 同上 |
| `ms-forbidden`（拒否の増加） | 全体の 403。書き込みの 403 も入る。しきい値の見直しは U1 の NFR8.1 と同じ回 | 既存のとおり | 同上 |
| `ms-error-logs`（ERROR のログの増加） | 想定外の失敗（500）の ERROR | 既存のとおり | 同上 |
| （拾わない）書き込みの3本の p95 | 既存の p95 の警報は、`uri` を1つの道に固定している | — | 足さない |

**Observability Setup での実測**
- 使い捨ての環境で、次の4つを起こし、式の値の動きを確かめます。
  - 書き込みのプールの待ち（`bizWritePoolLimit` の形）
  - 503（対象DB を `docker pause` で止める）
  - 監査の書き込みの失敗
  - 403
- 書き込みのプールの系列は、初めて借りるまで出ません。**先に1回書き込んでから、式を流します**（U1 の基盤の設計のレビューの R-07 を先に避ける）。
- 要求は、指標の送信の周期（1 分）を複数またいで送ります。lgtm を網につないだ後に、使い捨てのアプリを起動します（`project.md` の学び）。
- VM に収めるため、実測は PG（768m）で行います（`infrastructure-specification.md` 2.3、U1 と同じ）。
- 拾えなければ、警報を足すかを、その段の判断事項にします。

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 作成・更新・削除の応答時間（k6 の `iteration_duration{scenario:…}`。場面は `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`） | p95 < 1000 ms、`checks` の率 = 1 | Performance Validation の場面ごと（10 VU・3 分、`bizDelete` は既定 100 回）。3種類の対象DB で1つずつ。`infrastructure-specification.md` 2.2 の値で測る |
| 書き込みのプールの待ちの時間切れの累計（`hikaricp_connections_timeout_total{pool="mastersmith-target-db-write"}`。使い捨てのアプリにだけ公開する actuator の metrics で読む） | 0 | 同上。待ちの最大は記録だけ。揺れるため複数回流す |
| 503 の件数（k6 の状態コードの数え） | 0 | 同上 |
| アプリのコンテナの `memory.peak`（`bizCreateLarge`） | 2g の 90% 未満。作り直した直後の基準の値と `anon` を並べて記録する | 同上。流す前にアプリのコンテナを作り直す |
| 対象DB のコンテナのメモリ（`bizCreateLarge` を含むすべての場面） | OOMKilled が false、かつ `memory.events` の `oom_kill` が 0（F2: A） | 同上。`memory.peak`・`anon` の最大・`memory.events` の `max`・バッファープールの使用は記録だけ |
| 手元の監視の書き込みの3本の p95 | 目安 1000 ms（判定に使わない） | 手元の監視を常に動かしていない間は `Unverified`。配備の直後・監視の確かめ・振り返りの時点の値を、基準の値として並べる |

SLO の値は緩めません。満たさない場面は Not Met として、Performance Validation で扱いを諮ります。書き込みのプールを 10 に上げる案は採りません（F1: A。NFR 設計）。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| アプリのログ | 503・409（DB が拒んだもの）・違反の後の確かめの失敗は WARN の1行（スタックトレースなし）。キーは `purpose`（WRITE）・`stage`・`reason`・`sqlState`・`vendorCode`・`exceptionClass` だけ（NFR8.4）。出すのは `BusinessDataWriteService` の1か所 |
| 出さないもの | 値・主キー・接続先・例外のメッセージ。409 の業務の失敗（`DUPLICATE_VALUE`・`REFERENCE_NOT_FOUND`）はログに出さず、監査に残す |
| 口の中の WARN | 接続を捨てる処理と閉じる処理の失敗だけ。例外のクラスの名前だけを出す |
| 監査の書き込みの失敗の ERROR | 文は今のまま。業務データの出来事では `detail`・主キーの値・例外（cause）を外し、主キーのカラムの数・`keyTruncated`・例外のクラスの名前だけを出す（試しの W11） |
| 設定の欠け | 書き込みの資格情報の片方だけのときは、項目の名前だけを WARN で1回出す（NFR4.11） |
| ログの集め方 | 既存のまま。外部エクスポートは既定で無効で、手元の監視を見るときだけ `.env` で有効にする |
| トレース | 道は固定で、`uri`・`http.route` の属性に値は載らない。JDBC の文は属性に載せない（JDBC の観測の仕組みを足さない） |
| 漏えいの確かめ | `BusinessDataWriteSecretLeakIT`（TRACE を有効にしたログ）と、外部へ送る設定を有効にした結合テスト（トレースの属性）を verify で毎回流す |

## 5. ダッシュボードの区画（持ち主は Observability Setup）

`docker/monitoring/dashboards/mastersmith-overview.json` の区画「業務データ（Intent 261016-business-data-views）」に、U2 のパネルを3つ入れます。U1 のパネルと同じ区画です。

| パネル | 式の形（名前は起動して確かめてから書く） |
|---|---|
| 書き込みの3本の p95 | `histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_milliseconds_bucket{…,uri=~"/api/tables/rows.*"}[5m])))` |
| 409・503 の数（1 時間） | `status` が 409 のものと 503 のものを、`method`・`uri` ごとに数える |
| 書き込みのプール | `pool="mastersmith-target-db-write"` の使用中・待ち・待ちの時間切れの累計 |

Observability Setup での確かめ:
- `+Inf` 以外の `le` があることを確かめる。
- NaN を、要求が無いためと決めつけない。
- 書き込みを送るには、適用した DSL・ロール・作業ロールと、対象DB の書き込みの資格情報が要る。用意の操作と書き込みは監査に残るため、前もって依頼者に伝える（使い捨ての環境なら、配備した内部DB を汚さない）。

## 6. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `nfr-design/observability-design.md` 2節 | 元の指標を `http.server.requests`・`hikaricp.connections.*` と書く | 手元の監視の名前は `http_server_requests_milliseconds_*`・`hikaricp_connections_*`。式は起動して確かめてから書く | 既存の式（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）の名前。中身は同じ |
| `nfr-design/logical-components.md` L11 | 書き込みのプールの socketTimeout 10 秒 | 「書き込みの上限 + 5 秒」で計算する（既定は 10 秒で同じ） | 止まった対象DB で接続を持ち続ける時間（10〜20 秒）の読み方は変わらない |
| `nfr-design/logical-components.md` 5節 | `.env.example` の項目は後の段で決める | `.env` の項目が4つ増える（`infrastructure-specification.md` 1.1） | Q1: A。監視には響かない |
| NFR4.16 | 付与を絞れるかを確かめる | 絞れる。見本はスキーマ全体に付与する（Q3: A） | 監視には響かない |
| `README.md` の3か所 | 読み取りだけの記述 | 書き込みのアカウントの記述に直す（`infrastructure-specification.md` 6節） | 事実と合わなくなる |
| U1 の `infrastructure-design/infrastructure-specification.md` 2.1・2.3 | perf の MySQL 1792m。根拠は「プール以外 約 614 MiB」 | 2304m。プール以外は約 840 MiB | 試しで崩れた（F1: A）。U1 の承認の場でそろえる |
| U1 の `infrastructure-design/monitoring-design.md` 3節 | 対象DB のメモリの判断の線が無い | 対象DB は「OOMKilled false、かつ `oom_kill` 0」で判断し、`memory.peak` は記録だけ（3節） | F2: A。対象DB の `memory.peak` は、捨てられるページキャッシュを含み、問題が無くても上限に張りつく（試しで 4,326 回） |

## 7. 後の段に回すこと

- **コード生成の計画（B4）**
  - ログのキーの名前を決める。`BusinessDataWriteSecretLeakIT` に、ログの JSON のキーと値が無いことの確かめを入れる。
  - `AuditEventListener` の `logFailure` を、出来事の種類で分ける変更と、`AuditSecretLeakIT` などへの影響
  - 名前・台本・k6 の setup の前提・SpotBugs の承認・名前つきの管理者の IT（`cicd-pipeline.md` 7節）
- **Deployment Execution**
  - 配備の後のスモークテストの書き込みが、監査に残ることを依頼者に先に伝え、監査の確かめの期待の件数に入れる。
- **Performance Validation**
  - 3節の SLO の合否の回。待ちの時間切れの累計・待ちの最大・アプリと対象DB のメモリの記録（片付ける前に結果を見る）
- **Observability Setup**
  - 5節の区画を書いて、式を確かめる。
  - 2節の実測と、警報を足すかの判断
  - 内部DB の大きさの基準の値（監査の行の増え方。NFR 要件のレビューの R-03）

## 8. U1・U3 とそろえる点

- **U1**
  - 区画は1つにまとめ、書き込みの3本と書き込みのプールのパネルを足す。
  - 書き込みのプールの SLO も、同じ物差し（待ちの時間切れの累計 0・503 の件数 0、待ちの最大は記録だけ）にする。
  - 対象DB のメモリの判断（F2: A）は U1 の場面にも当てる。U1 の承認の場でそろえる。
  - ログのキーと WARN の形をそろえる。
- **U3**
  - 503 の出し分け（TIMEOUT は「保存されたか分からない」、UNAVAILABLE は「保存されていない」）。
  - 監査の `UNKNOWN` は画面に出さない。画面の誤りはサーバーのログに出ない。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q7: A、F1: A、F2: A）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u2-businessdata-write/nfr-design/observability-design.md`（1節〜3節）・`scalability-design.md`（2節〜4節）・`performance-design.md`（4節）・`reliability-design.md`（4節・5節）・`logical-components.md`（L11）
  - `construction/u2-businessdata-write/nfr-requirements/observability-requirements.md`（NFR8.4〜NFR8.6）
  - `construction/u1-businessdata-read/infrastructure-design/monitoring-design.md`（1節〜5節）
  - `.aidlc-reviews/infrastructure-design/units/u1-businessdata-read/4bcccff4724084fb/1.json`（R-07）
- 既存のもの（正とする。読むだけ）: `docker/monitoring/provisioning/alerting/mastersmith.yaml`（75行・268行など）、`docker/monitoring/dashboards/mastersmith-overview.json`、`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`
- 決まり: `aidlc/spaces/default/memory/project.md`（式は書く前に名前を確かめ書いた後に流す、p95 のバケット、送信の周期をまたぐ、lgtm を先に網につなぐ、片付けは確かめた後）
- 捨ての試し: `infrastructure-specification.md` 5節

## Assumptions & Open Questions

- 前提: `http.server.requests` に応答の code のラベルは無い（見立て）。無ければ、TIMEOUT と UNAVAILABLE は監査の `UNKNOWN` の件数で見分ける。
- 前提: `ms-pool-pending` は書き込みのプールを拾う（式がプールで絞っていないことは警報の決まりのファイルで確かめた）。値の動きは Observability Setup で実測する。
- 未確認: 対象DB のコンテナのメモリを、使い捨ての環境で cgroup から読めること（試しの `docker run` では `docker exec` で読めた。perf の compose のコンテナでも同じと見立てた）。
- 諮る点は無い。
