# 警報（alarms）

前の Intent の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/alarms.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。警報はファイル（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）でリポジトリに置きます。通知の先（contact point）は、配備先が決まるまで置きません。手元の Grafana の警報の一覧で見るだけです（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 1. 今回の差

**警報の決まりのファイルは変えていません**（17 件のまま）。新しい警報は足さない設計です（U1 の `monitoring-design.md` 3節、U2 の `monitoring-design.md` 3節、U3 の `monitoring-design.md` 2節。NFR2.10・NFR3.8・NFR2.29・NFR3.28）。参照の道・定義の道・保存前の確かめの 503 は、既存の `ms-biz-target-db`（式 `sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"/api/tables/.*",status="503"}[5m])) or vector(0)`、0 を超えたら `for: 0s`）で拾います。2節でその firing を確かめました。

## 2. 確かめ（使い捨ての環境、2026-11-01 UTC。環境と送った要求は `dashboards.md` 2.1節）

| 確かめること | 結果 |
|---|---|
| 1. 0.38.0 の lgtm で警報の決まりが読み込まれる（U5 の `monitoring-design.md` 7節からの引き継ぎ） | 07:24:47 に **17 件**が読み込まれ、すべて `inactive`、評価の健全さ（`health`）は 17 件とも `ok` |
| 2. 通常の流れ（409 を含む）の間は `ms-biz-target-db` が鳴らない | 07:27:19〜07:33:19 の通常の流れ（409 `BUSINESS_REFERENCE_NOT_FOUND` 25 件を含む）の間、`inactive` のまま。**409 では鳴らない**（式は 503 だけを数える） |
| 3. PG を `docker stop` して参照の道・定義の道・保存前の確かめつきの作成の 503 を送ると firing | PG を止めたのは 07:33:19、最初の 503 は 07:33:22。**07:34:40 に firing**（止めてから **81 秒**） |
| 4. PG を戻した後、5 分の窓を過ぎると Normal（inactive）に戻る | PG を戻したのは 07:36:43（最後の 503 は 07:36:49）。**07:41:33 に inactive**（戻してから **4分50秒**、最後の 503 から 4分44秒） |
| 5. 503 の code | すべて `BUSINESS_TARGET_DB_UNAVAILABLE`（借りる待ちの時間切れ）。`BUSINESS_TARGET_DB_TIMEOUT` は 0 件（Q3: A のとおり、`docker stop` では起きない） |

- 警報の状態は、Grafana の `/api/prometheus/grafana/api/v1/rules` の state を 10 秒ごとに読みました（題と状態だけ）。
- 止めてから firing までの 81 秒は、前の Intent の 2分48秒より短い値でした。指標の送信の周期（60 秒）と評価の間隔（1 分）の位相によると見ています（確かめていない）。前の Intent と合わせて、**止めてから 1分半〜3 分**で鳴る見込みとして記録します。
- firing は業務データの API の 503 の合計で決まるため、道ごとに分けて鳴らしてはいません。式の `uri=~"/api/tables/.*"` に参照の道（`/api/tables/reference/rows`）・定義の道（`/api/tables/definition`）・作成と更新の道（`/api/tables/rows`）が入ることは、パネル 42 の系列で確かめました（`dashboards.md` 2.4節）。どの道の 503 でも1件で鳴ります。
- `TIMEOUT`（問い合わせの最中の時間切れ）は手元では起こしていません。式は状態コード 503 だけで数え code によらないため、`TIMEOUT` の 503 も同じ式で拾います。`TIMEOUT` の経路そのものは、U1 の結合テストと U2 の `reliability-design.md` 6節の T-T1 で確かめ済みです（Build and Test で Met）。

### 2.1 同じ間の既存の警報の動き

| 警報 | 動き | 読み方 |
|---|---|---|
| `ms-pool-pending` | 07:33:41 pending → 07:34:41 inactive、07:35:41 pending → 07:36:42 inactive。firing にはならなかった | 60 秒ごとの1点で待ちが見えたり見えなかったりする（前の Intent と同じ。1 VU で待ちの数は最大 1） |
| `ms-5xx-ratio` | 07:34:31 pending → **07:39:33 firing**（PG を戻した後。5 分の窓に 503 が残り `for: 5m` を過ぎた）→ 07:41:33 inactive | 使い捨ての環境では業務データのほかの要求がほとんど無いため割合が高い。配備した環境では、ほかの要求で割合が薄まりうる（前の Intent と同じ） |
| ほかの 14 件 | 窓の終わり（07:48:06）まで `inactive` | `ms-error-logs` も鳴っていない（ERROR のログは 0 件。`log-queries.md` 1節） |

## 3. 409 を警報にしない（U2 の `monitoring-design.md` 3節）

409 `BUSINESS_REFERENCE_NOT_FOUND`（保存前の確かめの拒否・外部キーの違反）は、利用者の入力で起きる業務の誤りで、対象DB の不調ではありません。2節の2で、409 が続いても警報が鳴らないこと、診断の WARN（`ReferenceLookup`・`BusinessDataWriteService`）が出ず変換の WARN（`GlobalExceptionHandler`、`status=409`）だけが出ることを確かめました（`log-queries.md` 1節）。急に増えたときは、パネル 43 と監査の行で調べます（Incident Response の手順書で扱う）。

## 4. 配備した環境で鳴る条件（前の Intent と同じ）

- `ms-biz-target-db` が配備した環境で鳴るのは、配備したアプリの外部エクスポートを有効にし、手元の監視を動かしているときだけです。今は有効にしていないため、配備したアプリで対象DB に届かなくても、手元の監視の警報は鳴りません（画面の「検索できません」・作成と更新の画面が開けない、などで気づく）。

## 5. Build and Test から持ち越した Unverified の判定（警報の行）

| ID | 目標 | この段の確かめ | 判定 |
|---|---|---|---|
| U1-NFR3.8 | 参照の読み取りの 503（`TIMEOUT`・`UNAVAILABLE`）を既存の `ms-biz-target-db` で拾う。定義の取得の 503 も同じ | 2節の3・4。参照の道 18 件・定義の道 6 件の 503（`UNAVAILABLE`）を含む 503 で 81 秒で firing、戻して 4分50秒で inactive。`TIMEOUT` は式が状態コードだけで数えるため同じ式で拾い、経路は結合テストで確かめ済み（Q3: A） | **Met** |
| U2-NFR3.28 | 保存前の確かめの 503 を既存の `ms-biz-target-db` で拾う | 同じ回。保存前の確かめつきの作成の 503 は 14 件（ログの WARN が `purpose=REFERENCE`・`stage=BORROW`、`log-queries.md` 1節）で、同じ `uri="/api/tables/rows"` の 503 の系列に入った | **Met** |
| OBS-PANELS の警報の部分 | 警報 `ms-biz-target-db` が新しい道・定義の道・保存前の確かめの 503 で firing | 上の2行 | **Met**（パネルの部分は `dashboards.md`、全体の判定は `slo-config.md` 4節） |

## 6. 設計との差（承認済みの文書は書き換えない）

| ID | 上流 | 上流の記載 | この段の記録 | 理由と扱い |
|---|---|---|---|---|
| A-D1 | U1・U2 の `monitoring-design.md` 6節 | `docker pause` では `UNAVAILABLE` になり、`TIMEOUT` は問い合わせの最中に止めないと起きない | `docker stop` だけで確かめ、`docker pause` は行っていない | Q3: A。前の Intent で pause も `UNAVAILABLE` になると分かっており、新しく分かることが無いため |

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/observability-setup-questions.md`（2節、Q1: A・Q2: A・Q3: A）
- この段の `dashboards.md`（2.1節・2.4節）・`log-queries.md`・`slo-config.md`
- 基盤の設計: `construction/u1-reference-read/infrastructure-design/monitoring-design.md`（3節・6節）・`construction/u2-reference-write-check/infrastructure-design/monitoring-design.md`（3節・6節）・`construction/u3-reference-screens/infrastructure-design/monitoring-design.md`（2節）・`construction/u5-container-perf-docs/infrastructure-design/monitoring-design.md`（3節・7節）
- NFR 設計: U1・U2 の `reliability-design.md`（503 への写し、T-T1）
- Build and Test: `construction/build-and-test/build-and-test-summary.md`（OBS-PANELS・U1-NFR3.8・U2-NFR3.28）
- 前の Intent の同じ段: `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/alarms.md`
- 読み取りで確かめたファイル: `docker/monitoring/provisioning/alerting/mastersmith.yaml`（変えていない）
- 実行したコマンドの出力: Grafana の `/api/prometheus/grafana/api/v1/rules`（10 秒ごと、題と状態だけ）

## Assumptions & Open Questions

- 止めてから firing までの時間の差（81 秒と前回の 2分48秒）の理由は確かめていません。判定には影響しません。
