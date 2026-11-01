# SLO の報告（slo-report）

Intent 261029-reference-lookup-cleanup の振り返りの段の SLO の報告です。配備先は開発者の PC 上のコンテナのままで、手元の監視（lgtm）を常に動かしていません。そのため、運用の中の SLO の判定はすべて `Unverified` とし、配備の直後・監視の確かめ・負荷の試験・この段の時点の値を基準の値として並べます（`aidlc/spaces/default/memory/project.md` の Deployment の学び）。目標は緩めていません。

- 仮の目標（稼働 99.5%・30 日など）は前の Intent までの記録のとおりで、配備先が決まったときに正式に決めます（`operation/observability-setup/slo-config.md`）。
- この Intent の承認済みの目標は、参照の API（選択肢つきの定義の取得・ピッカーの一致の検索・ラベルの1件読み・保存前の確かめつきの作成）の p95 1,000 ms 未満と、ピッカーの部分一致の検索の p95 3,000 ms 未満です（U1 の NFR2.1、U2 の NFR2.21）。負荷の下の判定は Performance Validation の k6（`iteration_duration`）が持ちました。

## 1. 判定

| SLI | 目標（窓） | 運用の中の判定 | 負荷の試験の判定（Performance Validation） |
|---|---|---|---|
| 参照の API（定義・一致の検索・ラベル・保存前の確かめつきの作成）の p95 | 1,000 ms 未満 | Unverified | Met。3種類 × 2回のすべてで満たした（PG の `refLabel` 84.31 ms が最大の側。MySQL・MariaDB は 15 ms 未満） |
| ピッカーの部分一致の検索（`refPickerContains`）の p95 | 3,000 ms 未満 | Unverified | Met。PG 475〜496 ms、MySQL 1,337〜1,385 ms、MariaDB 1,141〜1,464 ms |
| 既存の書き込みの場面（`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`）の p95 | 1,000 ms 未満・意味を保つ（U2-NFR2.23） | Unverified | Met。p95 の最大 15.87 ms。前の Intent より遅くなっていない |
| 読み取り・書き込みのプールの時間切れ・503 | 増分 0・503 0（10 VU、プール 5） | Unverified | Met。全場面で時間切れの増分 0、503 0 件 |
| アプリのメモリ（`memory.peak`） | 上限 2g の 90% 未満 | Unverified | Met。最大 45.8%（MariaDB の `bizSearchWide` の1回目） |
| 対象DB のメモリ（`anon` の最大） | 92% 以下・OOMKilled なし | — | Met。PG 14.0%・MySQL 78.5%・MariaDB 87.0%（2回目。前の Intent 85.01% より高い。`feedback-loop.md` の P2） |
| 区画の API の 5xx でない割合 | 配備先が決まったときに決める（全体の仮の目標は 99.5%・30 日） | Unverified | — |
| 内部DB の大きさ | 承認済みの目標なし（基準の値を記録） | 基準の値を記録 | — |

- 運用の中の誤りの予算（error budget）と燃え方（burn rate）は、常に動く監視が無いため出せません。配備先が決まったときに 30 日の窓で取ります（3節）。
- 判定の詳細は `operation/performance-validation/nfr-validation-matrix.md`（Met 10・Not Met 0・Unverified 0）です。

## 2. 基準の値（今ある証拠）

| 時点 | 値 | 出どころ |
|---|---|---|
| 配備の直後（2026-11-01 05:01Z） | 入れ替えの停止 約 27 秒（05:00:57Z〜05:01:24Z）。healthy・`/actuator/health` 200、ERROR 0 件。スモークテスト S1〜S9 がすべて通過。配備の後の複写で `BUSINESS_ROW_CREATED`・`UPDATED`・`DELETED` の `SUCCESS` が1件ずつ | `operation/deployment-execution/deployment-log.md` 2節・3節、`smoke-test-results.md`、`health-check-report.md` |
| 監視の確かめ（2026-11-01 07:27〜07:48Z、使い捨ての環境、1 VU・待ちつき） | パネル 41 の p95 は通常の間 95 前後（`le="100"` の中の補間）。参照の道の系列は部分一致を含めて 227.5〜417.1。PG を止めると 503（定義 6・参照の道 18・作成 21）が出て、`ms-biz-target-db` は 81 秒で firing、戻して 4分50秒で inactive。通常の流れの 503 とプールの時間切れは 0 | `operation/observability-setup/slo-config.md` 2節、`alarms.md` 2節・5節、`dashboards.md` |
| 負荷の試験（2026-11-01 08:42〜11:57Z、使い捨ての環境、10 VU・3 分） | 1節の値。配備したアプリは 08:41:42Z〜11:58:54Z（約 3 時間 17 分）止めた | `operation/performance-validation/test-results.md` 1節・3節〜5節 |
| この段の時点（2026-11-01 12:15Z ごろ） | 配備したアプリは healthy、再起動 0 回、OOMKilled なし。メモリ 546MiB / 2GiB。起こし直した後のログは INFO 37・WARN 2・ERROR 0 | この段の `docker inspect`・`docker stats`・`docker logs`（レベルの件数だけ） |

- Performance Validation の停止（約 3 時間 17 分）は、依頼者1人の手元の環境の計画した停止として、仮の稼働の目標の数には入れていません（数えるなら、30 日の 0.46% に当たり、99.5% の予算 0.5% の大半を使います）。
- 負荷の試験の値は、配備したアプリではなく、同じ WAR（`4cd5c416…`）の試験のイメージの使い捨てのアプリの値です。

## 3. 配備先が決まったときの測り方

| SLI | 測り方 |
|---|---|
| 参照の API の p95 | パネル 41 の p95 と、`le="1000"` の割合を 30 日の窓で常時取る。部分一致の 3,000 ms はバケットの境界に無いため、足すバケットを決め直すか k6 で別に取る（`anomaly-config.md` 3節、`feedback-loop.md` の E11） |
| 5xx でない割合 | パネル 42・47 の 503 の件数と全体の稼働のパネルを 30 日の窓で取り、誤りの予算と燃え方（1 時間・6 時間の窓）を出す |
| 対象DB のプールの時間切れ | パネル 44・45 と警報 `ms-biz-target-db` |
| 内部DB の大きさ | 配備ごとに `mastersmith.mv.db` の大きさと `audit_events` の件数を記録する（`slo-config.md` 3節） |
| 計画した停止 | 停止の始めと終わりを記録し、稼働の数から除くか含めるかを SLO を決めるときに決める |

前提として、配備したアプリの外部エクスポートと通知の先を置く必要があります（`operation/incident-response/incident-plan.md` 7節、`feedback-loop.md` の E1）。

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/feedback-optimization/feedback-optimization-questions.md`（Q1〜Q4: A）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/slo-config.md`（1節〜4節）・`alarms.md`（2節・5節）・`dashboards.md`・`anomaly-config.md`（3節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-execution/deployment-log.md`（2節・3節）・`smoke-test-results.md`・`health-check-report.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/performance-validation/test-results.md`（1節・3節〜6節）・`nfr-validation-matrix.md`（1節〜3節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/incident-response/incident-plan.md`（7節）
- 前の Intent の記録: `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/slo-report.md`
- 読み取りだけの確かめ（2026-11-01 12:15 ごろ UTC）: `docker inspect mastersmith-app-1`・`docker stats --no-stream`・`docker logs --since`（レベルの件数だけ）

## Assumptions & Open Questions

- 計画した停止（配備と負荷の試験）を稼働の数に入れるかは決まっていません。SLO の正式な値を決めるときに決めます（`feedback-loop.md` の E4）。
- 監視の確かめのパネル 41 の p95 は、100 ms 未満の要求がすべて 95 前後に見える補間の値です。運用の中の p95 の判定には、より細かいバケットか k6 の値が要ります。
