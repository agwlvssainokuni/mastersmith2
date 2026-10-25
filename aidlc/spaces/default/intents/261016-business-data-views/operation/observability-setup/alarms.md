# 警報（alarms）

前の Intent の記録（`aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/alarms.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。警報はファイル（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）でリポジトリに置きます。通知の先（contact point）は、配備先が決まるまで置きません。手元の Grafana の警報の一覧で見るだけです（`aidlc/spaces/default/memory/project.md` の Deployment）。

決定は `observability-setup-questions.md`（Q3: A・Q4: A・F1: A）、実測の値は `measurement-notes.md` と、この段の確かめの回（`dashboards.md` 2.1）を正とします。

## 1. 今回の差（ファイルの変更。作業のブランチ `feature/261016-business-data-views-observability`、未コミット）

警報を1つ足しました（16 件 → 17 件）。**既存の 16 件は1文字も変えていません**（差分の削除の行は 0）。

| 項目 | 中身 |
|---|---|
| uid | `ms-biz-target-db` |
| 題 | 業務データの対象DB に届かない |
| 式 | `sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/.*",status="503"}[5m])) or vector(0)` |
| 条件 | 0 を超えたとき、`for: 0s` |
| 評価の間隔・データ無しの扱い | 既存と同じ（グループの `interval: 1m`、`noDataState: OK`、`execErrState: Error`、`relativeTimeRange` 600 秒、`instant: true`） |
| 重さ | 中 |
| 注記 | 業務データの API が 5 分の間に 503 を返した。ログの WARN（対象DB の業務データを読めませんでした・書き込めませんでした）のキー `reason` で UNAVAILABLE（借りる待ちの時間切れ）と TIMEOUT（問い合わせの時間切れ）を見分け、`docker compose ps targetdb-postgres` で対象DB を確かめる |

- 状態コードで数えるため、借りる待ちの時間切れ（`BUSINESS_TARGET_DB_UNAVAILABLE`）と問い合わせの時間切れ（`BUSINESS_TARGET_DB_TIMEOUT`）の両方を拾います。割合と 60 秒ごとの点に左右されません（F1 の理由）。
- 誤って鳴るのは、対象DB が止まった・答えないときに限られる見込みです。

## 2. 足した警報の確かめ（この段の確かめの回、2026-10-25 UTC）

| 確かめること | 結果 |
|---|---|
| 1. 決まりが読み込まれ、すべて inactive で始まる | 04:05:20 に 17 件すべて `inactive`（`ms-biz-target-db` は `Normal`、評価の健全さは ok） |
| 2. 業務データの成功の要求の間は inactive のまま | 04:07:00〜04:12:02 の通常の流れ（274 回、409 を含む）の間、`inactive`。式の値は 0 |
| 3a. PG を `docker stop` して読み取りと書き込みを送ると firing | PG を止めたのは 04:12:32。式の値は 04:14:30 の点で 0 → 28.5 になり、**04:15:20 に firing**（止めてから **2分48秒**）。式の値の最大は 73.5（04:17:00） |
| 3b. PG を戻して要求が成功した後、5 分の窓を過ぎると inactive | PG が healthy になったのは 04:16:39（最後の 503 は 04:16:35 ごろ）。式の値は 04:22:00 の点で 0 になり、**04:22:20 に inactive**（戻してから **5分41秒**） |

- 止めてから firing まで約 3 分かかった理由は2つあります。
  - 指標の送信の周期（60 秒）。
  - 503 の系列は止めた後に初めて現れるため、最初の点は `increase` に数えられず、2つ目の点を待った（`project.md` の学び）。
- 同じ間の既存の警報の動き:
  - `ms-pool-pending`: 04:14:20 pending → 04:15:20 firing → 04:17:20 inactive。
  - `ms-5xx-ratio`: 04:15:20 pending → **04:20:20 firing**（PG を戻した後。5 分の窓に 503 が残り、`for: 5m` を過ぎたため）→ 04:21:20 inactive。
  - ほかの 14 件は、窓の終わり（04:27:41）まで `inactive` のままでした。

## 3. 既存の警報が業務データを拾うか（U2-NFR8.6。`measurement-notes.md` 5節・6節）

前回の使い捨ての環境の実測（2026-10-25 02:37〜03:12 UTC）の結果です。

| 警報（しきい値） | 実測の結果 | 判定 |
|---|---|---|
| `ms-pool-pending`（待ちが 0 を超える状態が 1 分） | PG を止めて 2分09秒、pause して 2分31秒で firing。式の値は最大 1。60 秒ごとの点の値のため、pause の間は inactive と firing を行き来した | **鳴った（条件つき）**。要求がまばらだと待ちの点を取りこぼす |
| `ms-5xx-ratio`（5xx の割合が 0.01 を超える状態が 5 分） | PG を止めて 7分09秒で firing。値の最大 0.742 は、ほかの要求がほとんど無い環境のため。ほかの要求（約 190 件/秒）が加わると割合は 0.0012 に下がり inactive に戻った | **鳴った（条件つき）**。業務データの 503 は1人の操作で最大 0.3 件/秒で、ほかの要求が 30 件/秒を超えると 1% を下回る |
| `ms-forbidden`（直近 1 時間の 403 が 20 件を超える） | 要る権限だけを欠く利用者からの 403 534 件（すべて `ACCESS_DENIED`）で、403 の場面の始めから 1分42秒で firing。式の値は最大 695.4 | **鳴った**（使い捨ての環境） |
| `ms-audit-fail`（監査の書き込みの失敗の行が 1 時間に 1 件以上） | 式は `sum(count_over_time({service_name="mastersmith"} \|= "監査イベントの記録に失敗しました" [1h]))` で、ログの文だけで数え、出来事の種類で絞らない（式の読み取り）。本番のコードを変えずに失敗を起こす手が無いため、起こしていない | 業務データの出来事も入ることを式の読み取りで確かめた。**鳴ることは `Unverified`** |
| `ms-audit-slow`（監査の書き込みの遅れの行が 1 日に 3 件以上） | 式は同じ形で文「監査イベントの記録に時間がかかりました」・`[1d]`。前回の実測で、業務データの書き込みの間に実際に1件数えた（しきい値に届かない） | 業務データの出来事も入ることを確かめた。**鳴ることは `Unverified`** |
| `ms-error-logs`（ERROR が 5 分に 5 件を超える） | 業務データの 503 は WARN で、ERROR は 0 行だった | 業務データの 503 は拾わない（設計どおり。ERROR は想定外の 500 だけ） |

- 503 の code は、前回・今回ともすべて `BUSINESS_TARGET_DB_UNAVAILABLE` でした。`BUSINESS_TARGET_DB_TIMEOUT` は、つないだ後に止めた状態（`docker pause`）でも 0 件でした（`measurement-notes.md` 6節）。TIMEOUT を起こす確かめは結合テスト（ServerSocket の方式）を正とします。

## 4. `ms-forbidden` のしきい値（U1-NFR8.1、Q4: A）

- **しきい値 20 は変えていません**。
- 業務データの権限の 403（権限の無いテーブル・カラム・操作の拒否）も、この警報の数に入ります（実測で確かめた。3節）。
- 業務データの 403 は**監査に残りません**（前回の実測で、403 に当たる種類の行は 0 件。`measurement-notes.md` 8節）。
- 鳴ったときの見分け方:
  1. ダッシュボードの区画の「API ごとの要求の数」（パネル 40）で、`status="403"` の系列が `/api/tables/…` に出ているかを見る。
  2. Loki で `log-queries.md` の L4（code ごとの件数）を流し、`ACCESS_DENIED`/403 の行の `trace_id` から Tempo で要求を追う。利用者は監査に残らないため、ログとトレースで追う。
  3. 管理の API の入口の 403（`uri="UNKNOWN"`）は、前の Intent と同じく監査の `ACCESS_DENIED` の `request_path` で見る。
- 警報の題「管理画面への拒否の増加」と注記（「監査ログで利用者と接続元IPを確かめる」）は、業務データの 403 には当たりません。題・注記・しきい値を見直すかは、配備先が決まり利用者が増えたときの申し送りです。
- 依頼者1人の今の使い方では、業務データの 403 が1時間に 20 件に届くことはまれな見込みです（画面は 403 の後に送り直さない。U3-NFR8.7 は Build and Test で Met）。

## 5. 配備したアプリで lgtm が止まったとき（`measurement-notes.md` 7節）

- 前回の1回目で、毎秒 1,500〜2,200 件の要求のトレースを受けた lgtm の中の受け手（otelcol）と Tempo が、メモリの上限（1536m）で止められました。
- そのとき使い捨てのアプリには、送信の失敗が次の形で出ました。
  - `io.opentelemetry.exporter.internal.http.HttpExporter` の ERROR 95 行
  - `BatchSpanProcessor` の WARN 59 行・`OtlpMeterRegistry` の WARN 17 行
- 配備したアプリの外部エクスポートを有効にして lgtm が止まると、この ERROR が `ms-error-logs`（5 分に 5 件を超える）の数に入りえます。そのときは、アプリの障害ではなく受け手の側を先に確かめます（`docker inspect` の `OOMKilled`、lgtm のコンテナの中の otelcol・Tempo の有無）。
- 今は配備したアプリの外部エクスポートを有効にしていないため、この形は起きません。配備先が決まったときに、受け手の大きさとあわせて見直します。

## 6. Build and Test から持ち越した Unverified の判定

| ID | 目標 | 判定 |
|---|---|---|
| U1-NFR8.1 | 警報 `ms-forbidden` のしきい値（20）を見直す | **しきい値 20 のまま**（Q4: A）。業務データの 403 も数に入り、監査に残らないことと見分け方を4節に書いた。見直しは申し送り |
| U2-NFR8.6 | 既存の警報（`ms-pool-pending`・`ms-audit-fail`・`ms-audit-slow`・`ms-5xx-ratio`・`ms-forbidden`）が書き込みも拾う | `ms-pool-pending`・`ms-5xx-ratio`・`ms-forbidden` は使い捨ての環境で鳴った（条件つき。3節）。`ms-audit-fail`・`ms-audit-slow` は式の読み取りで出来事の種類によらないことを確かめ、鳴ることは `Unverified`。条件つきの部分を補うため、`ms-biz-target-db` を足した（F1: A、1節・2節） |

U1-NFR8.3・U2-NFR8.5（ダッシュボードの区画）は `dashboards.md` と `slo-config.md` 3節です。

## 7. 設計との差（承認済みの文書は書き換えない）

| ID | 上流 | 上流の記載 | この段の記録 | 理由と扱い |
|---|---|---|---|---|
| A-D1 | U1・U2 の `monitoring-design.md` 2節 | 「新しい警報は足さない。拾えなければ、警報を足すかをこの段の判断事項にする」 | 警報 `ms-biz-target-db` を1つ足した | F1: A。R-05 の実測で、既存の2つの警報は条件つきでしか拾えなかったため（3節）。設計が判断をこの段に回した形のとおりで、設計の範囲の中の決定 |
| A-D2 | U1 の `monitoring-design.md` 2節 | 「`ms-pool-pending` は接続が作れないとき待ちが増えにくい」 | 接続が作れないときも、借りる待ち（3 秒）の間は待ちが 1 になり、60 秒ごとの点に当たれば鳴った | 実測で見立てを確かめた。点に当たらないと鳴らない点は同じ |

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/observability-setup-questions.md`（Q3: A・Q4: A・F1: A、やり直したまとめの確認）
- この段の `measurement-notes.md`（5節〜8節）・`dashboards.md`（2節）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/alarms.md`
- 基盤の設計: `construction/u1-businessdata-read/infrastructure-design/monitoring-design.md`（2節）・`construction/u2-businessdata-write/infrastructure-design/monitoring-design.md`（2節）
- `construction/build-and-test/build-and-test-summary.md`（U1-NFR8.1・U2-NFR8.6・U3-NFR8.7）
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`、`docker/perf/compose.yaml`、`perf/README.md`
- `backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataReadService.java`・`BusinessDataWriteService.java`（WARN の文とキー）、`businessdata/domain/BusinessDataProblemTypes.java`
- 実行したコマンドの出力（この段の確かめの回）:
  - `curl -s http://127.0.0.1:13000/api/prometheus/grafana/api/v1/rules`（30 秒ごとの記録）
  - `curl -s http://127.0.0.1:13000/api/datasources/proxy/uid/prometheus/api/v1/query?...`（足した警報の式）
  - `docker stop`・`docker start mastersmith-perf-targetdb-postgres-1`、`pmset -g log`

## Assumptions & Open Questions

- `ms-biz-target-db` が配備した環境で鳴るのは、配備したアプリの外部エクスポートを有効にし、手元の監視を動かしているときだけです。今は有効にしていないため、配備したアプリで対象DB に届かなくても、手元の監視の警報は鳴りません（画面の 503 の表示で気づく）。
- `ms-audit-fail`・`ms-audit-slow` が鳴ることは `Unverified` のままです（持ち主 feedback-optimization）。
