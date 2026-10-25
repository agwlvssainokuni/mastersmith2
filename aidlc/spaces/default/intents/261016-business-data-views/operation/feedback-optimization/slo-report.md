# SLO の報告（slo-report）

Intent 261016-business-data-views の振り返りの段の SLO の報告です。配備先は開発者の PC 上のコンテナのままで、手元の監視（lgtm）を常に動かしていません。そのため、運用の中の SLO の判定はすべて `Unverified` とし、配備の直後・監視の確かめ・負荷の試験・この段の時点の値を基準の値として並べます（`aidlc/spaces/default/memory/project.md` の Deployment の学び）。目標は緩めていません。

- 仮の目標（稼働 99.5%・30 日など）は前の Intent までの記録のとおりで、配備先が決まったときに正式に決めます（`operation/observability-setup/slo-config.md`）。
- この Intent の承認済みの SLO は、業務データの API の応答時間 p95 1 秒（U1 の NFR1.1〜NFR1.3、U2 の NFR1.4・NFR1.5）です。負荷の下の判定は Performance Validation の k6（`iteration_duration`）が持ちました。

## 1. 判定

| SLI | 目標（窓） | 運用の中の判定 | 負荷の試験の判定（Performance Validation） |
|---|---|---|---|
| 読み取り（定義・一覧・詳細）の p95 | 1000 ms 以内 | Unverified | **Not Met**（U1-NFR1.1・NFR1.3・NFR3.2）。PostgreSQL は全場面が満たした。MySQL の `bizSearchContains`、MariaDB の `bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted` が 1 秒を超えた |
| 読み取りのプールの時間切れ・503 | 増分 0・503 0（10 VU、プール 5） | Unverified | **Not Met**（U1-NFR2.4）。MariaDB の `bizSearchContains` で 1・2 件、MySQL の2回目で 42 件 |
| 書き込み（作成・更新・削除・プールの上限）の p95 | 1000 ms 以内 | Unverified | Met（U2-NFR1.4・NFR1.5・NFR3.4）。3種類 × 2回のすべてで満たした（p95 の最大 57.1 ms） |
| 書き込みのプールの時間切れ・503 | 増分 0・503 0 | Unverified | Met（U2-NFR2.7） |
| アプリのメモリ（`memory.peak`） | 上限 2g の 90% 未満 | Unverified | Met（U1-NFR2.5b・U2-NFR2.8）。最大 969.7 MiB |
| 区画の API の 5xx でない割合 | 配備先が決まったときに決める（全体の仮の目標は 99.5%・30 日） | Unverified | — |
| 内部DB の大きさ | 承認済みの目標なし（基準の値を記録） | 基準の値を記録 | 監査の行 1件 約 108 B（U2-NFR2.9、記録の目標） |

- 運用の中の誤りの予算（error budget）と燃え方（burn rate）は、常に動く監視が無いため出せません。配備先が決まったときに 30 日の窓で取ります（3節）。
- Not Met の4件の扱いは、Performance Validation の承認の場で「この段に引き継ぎ、直し方は後の Intent で決める」と決まりました（`operation/performance-validation/nfr-validation-matrix.md` 4節）。次の Intent の第2の束です（`feedback-loop.md` 2節）。

## 2. 基準の値（今ある証拠）

| 時点 | 値 | 出どころ |
|---|---|---|
| 配備の直後（2026-10-20 23:49Z） | healthy・`/actuator/health` 200。入れ替えの停止 約 53 秒。スモークテスト S1〜S13 がすべて通った。配備の後の複写で `BUSINESS_ROW_*` の SUCCESS が作成・更新・削除の1件ずつ、FAILURE・UNKNOWN は 0 件 | `operation/deployment-execution/deployment-log.md` 2節・5節、`smoke-test-results.md` |
| 監視の確かめ（2026-10-25 04:05〜04:28Z、使い捨ての環境、1 VU） | 区画の p95 は成功の間 95.0〜95.9 ms（`le="100"` の中の補間）。PG を止めると 503 が借りる待ちの約 3 秒で返り、`ms-biz-target-db` は止めてから 2分48秒で firing、戻してから 5分41秒で inactive。通常の流れの 503 は 0 件 | `operation/observability-setup/slo-config.md` 2節、`alarms.md` 2節、`dashboards.md` |
| 負荷の試験（2026-10-25 05:33〜09:55Z、使い捨ての環境、10 VU・3 分） | 読み取りの p95: PG 最大 732.6 ms、MySQL `bizSearchContains` 1,627.0・5,513.4 ms、MariaDB `bizSearchContains` 4,698.2・4,708.5 ms・`bizSearchLast` 2,026.8・2,417.1 ms・`bizSearchLastSorted` 2,964.3・2,212.3 ms。書き込みの p95 最大 57.1 ms。アプリの `memory.peak` 最大 969.7 MiB。対象DB の `anon` の最大は MariaDB 85.01%・MySQL 90.3%（OOMKilled false・`oom_kill` 0） | `operation/performance-validation/test-results.md`・`nfr-validation-matrix.md` 1節 |
| この段の時点（2026-10-25 10:10Z） | 配備したアプリは healthy・200、再起動 0 回、OOMKilled なし。メモリ 536.4MiB / 2GiB。起動の後のログは INFO 37・WARN 2・ERROR 0 | この段の `docker inspect`・`docker stats`・`docker logs`（レベルの件数だけ） |

- 配備したアプリは、Performance Validation の間（2026-10-25 05:31:51Z〜09:59:22Z、約 4 時間 27 分）止めていました。依頼者1人の手元の環境のため、この停止は計画した停止として、仮の稼働の目標の数には入れていません（数えるなら、30 日の 0.62% に当たり、99.5% の予算 0.5% を超えます）。
- 負荷の試験の値は、配備したアプリではなく、同じイメージ（`mastersmith:local` の別名）の使い捨てのアプリの値です。

## 3. 配備先が決まったときの測り方

| SLI | 測り方 |
|---|---|
| 業務データの API の p95 | 区画のパネル（`dashboards.md` のパネル 41〜43）の p95 と、`le="1000"` の割合を 30 日の窓で常時取る。区画の p95 の最小のバケットが 100 ms のため、細かい値は k6 で別に取る |
| 5xx でない割合 | 区画の 503 の件数と全体の稼働のパネルを 30 日の窓で取り、誤りの予算と燃え方（1 時間・6 時間の窓）を出す |
| 対象DB のプールの時間切れ | 区画のプールのパネル（時間切れの系列）と警報 `ms-biz-target-db` |
| 内部DB の大きさ | 配備ごとに `mastersmith.mv.db` の大きさと `audit_events` の件数（`BUSINESS_ROW_*` を含む）を記録する（`slo-config.md` 3節） |
| 計画した停止 | 停止の始めと終わりを記録し、稼働の数から除くか含めるかを SLO を決めるときに決める |

前提として、配備したアプリの外部エクスポートと通知の先を置く必要があります（`feedback-loop.md` の E1）。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-optimization-questions.md`（Q1〜Q4: A、まとめの確認 Looks correct）
- `operation/observability-setup/slo-config.md`（1節〜4節）・`alarms.md`（1節〜3節）・`dashboards.md`
- `operation/deployment-execution/deployment-log.md`（2節・5節）・`smoke-test-results.md`
- `operation/performance-validation/nfr-validation-matrix.md`（1節・4節）・`test-results.md`（1節・6節）
- `operation/incident-response/incident-plan.md`（8節）
- 前の Intent の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/slo-report.md`
- 読み取りだけの確かめ（2026-10-25 10:10 ごろ UTC）: `docker inspect mastersmith-app-1`・`docker stats --no-stream`・`docker logs mastersmith-app-1`（レベルの件数だけ）・`curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health`

## Assumptions & Open Questions

- 計画した停止を稼働の数に入れるかは決まっていません。SLO の正式な値を決めるときに決めます（`feedback-loop.md` の E4）。
- 監視の確かめの区画の p95 は、100 ms 未満の要求がすべて 95 前後に見える補間の値です。運用の中の p95 の判定には、より細かいバケットか k6 の値が要ります。
