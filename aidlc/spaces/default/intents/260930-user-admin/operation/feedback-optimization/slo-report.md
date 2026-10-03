# SLO の報告（slo-report）

Intent `260930-user-admin`（利用者の管理の画面）の運用の目標の報告です。

- この Intent では SLO を決めていません（U3 の NFR5.11）。
- 仮の目標（稼働 99.5%・30 日など）は、前の Intent の記録を正とします（`operation/observability-setup/slo-config.md` の冒頭）。正式な目標は、配備先が決まったときに決めます（`project.md` の Deployment）。
- 手元の監視を常に動かしていないため、判定はすべて `Unverified` です。目標を緩めて満たしたことにはしません（`project.md` の Deployment の学び）。

## 1. 判定

| 目標 | 判定 | 理由 |
|---|---|---|
| 稼働（5xx でない応答の割合、30 日） | Unverified | 30 日の実測が無い。配備（2026-10-03 21:54 JST）から約 2 時間で、手元の監視は止めている |
| 誤りの予算と、その消費の速さ | Unverified | 同上。正式な目標と予算が無い |
| 7つの API の p95 1000 ms（U3 の NFR5.1・NFR5.3〜NFR5.6、運用の中） | Unverified | 運用の中の常時の値が無い。k6 の値は使い捨ての環境のもので、目標の判定（Met）は Performance Validation の段で済んでいる（2節） |
| 409 `USER_ADMIN_BUSY` が目標の負荷で 0 件 | Unverified | 運用の中の常時の値が無い。使い捨ての環境では 0 件 |
| 監査の書き込みの失敗の件数 | Unverified | 同上。配備した環境のログには、監査の失敗の行は無い（この段の確かめ） |
| 接続プールの時間切れの累計 0（U3 の NFR6.2） | Unverified | 運用の中の常時の値が無い。使い捨ての環境では 0 |
| JVM のヒープの使用率（`ms-heap` 0.85） | Unverified | 配備したアプリ（上限 2g）の常時の値が無い |
| 画面の時間（U5 の NFR5.1 2 秒・NFR5.2 1.5 秒） | Unverified | 配備したアプリでは測らない（Performance Validation の Q4: A）。配備先が決まったときに、本番の条件で判定する |

## 2. 基準の値（今ある証拠）

| 時点 | 内容 | 値 | 出典 |
|---|---|---|---|
| 配備の直後（2026-10-03 21:54〜22:05 JST） | 起動・ヘルスチェック・スモークテスト S1〜S5 | 入れ替えの開始から約 17 秒で healthy。`/actuator/health` は 200・UP。S1〜S5 は合格（S4 は既知の不具合つき、S5 は予定との差あり）。起動のログに ERROR 0 件。V9 を当てた | `operation/deployment-execution/deployment-log.md`・`health-check-report.md`・`smoke-test-results.md` |
| Observability Setup（22:20〜22:28 JST、使い捨ての環境・上限 1g・同時 1〜2 件） | 手元の監視に送った値 | 7つの API の p95 は 95 ms（すべて `le="100"` の中、補間の値）。7つの API の約 1,090 件で 5xx 0 件。監査の失敗 0 件。BUSY 0 件。借りるまでの待ちの最大 0.708 ms、時間切れの累計 0。ヒープの最大 0.243 | `operation/observability-setup/slo-config.md` 2節・`dashboards.md`・`alarms.md` |
| Performance Validation（23:08〜23:33 JST、使い捨ての環境・上限 2g・同時 10） | k6 の p95 | 一覧 3.0〜6.1 ms。氏名と言語の変更 3.1〜3.9 ms。5つの操作 4.8〜7.4 ms。止める悪い側 106.9 ms。ログイン 939.6 ms（目標まで 60 ms）。更新 2.8 ms。招待の一覧 2.6〜2.7 ms。上限 30 の場面は時間切れ 0・待ちの最大 9 ms 以下 | `operation/performance-validation/test-results.md` 2節・3節、`nfr-validation-matrix.md` 1節 |
| この段（23:49〜23:55 JST） | 配備したアプリ（読み取りだけ） | healthy（23:32:43 JST に起動し直した後、再起動 0 回、OOMKilled なし）。メモリ 427.3MiB / 2GiB（20.86%）、CPU 0.33%。23:32 の起動の後、ログに ERROR 0 件 | この段で実行したコマンドの出力 |

- 判定に使わない理由: 使い捨ての環境の値は、負荷の形や上限が配備と違い、30 日の窓も持たないためです。
- 止まっていた時間（依頼者の判断による計画した停止）は次のとおりです。障害による停止はありません。
  - 配備の入れ替え（約 17 秒）
  - スモークテストの S5（内部DB の複写、約 10 秒）
  - Performance Validation（約 24 分 32 秒）
- 起動のログには、`AUTHENTICATION_FAILED`（401）の WARN が1件あります（21:59 JST）。スモークテストの S5 で数えた `LOGIN_FAILED` の1件（依頼者の操作）に当たり、障害ではありません。

## 3. 配備先が決まったときの測り方

1. 監視を常に動かします（配備先の監視の仕組みか、手元の監視の常時の起動）。外部エクスポートを有効にし、稼働（5xx でない応答の割合）を 30 日の窓で測ります。
2. 7つの API は、`http_server_requests_milliseconds_bucket` の `le="1000"` の割合（`dashboards.md` の N7）と p95（N2）を常時取ります。目標は「30 日のうち、`le="1000"` に入る要求の割合」で決めます。
3. 誤りの予算と、その消費の速さの警報（1 時間・6 時間の窓）を、正式な目標とあわせて決めます。警報のしきい値は、目標を割る前に鳴る値にします（`phases/operation.md` の Observability）。
4. BUSY は Loki の L3 の件数を、監査の失敗は L6 と `ms-audit-fail` を、接続プールは `hikaricp_connections_timeout_total` の増加を見ます。`ms-pool-pending` の式の見直し（D1）と同時に決めます。
5. 画面の時間は、実際の利用者の画面で測る仕組みを配備先で決めます。U5 の NFR5.1・NFR5.2 は、その仕組みで判定します。
6. 管理の API の専用のパネルを置くかは、正式な目標と一緒に決めます（`feedback-loop.md` の D4）。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（決まっていること、Q1〜Q3、確認済みの要約）
- `operation/observability-setup/slo-config.md`・`dashboards.md`・`alarms.md`
- `operation/performance-validation/test-results.md`・`nfr-validation-matrix.md`
- `operation/deployment-execution/deployment-log.md`・`health-check-report.md`・`smoke-test-results.md`
- `operation/incident-response/incident-plan.md`（7節、配備先が決まったときに見直すこと）
- この段で実行したコマンドの出力（2026-10-03 23:49〜23:55 JST）: `docker inspect`・`docker stats --no-stream`・`docker logs` の件数と Flyway の版とキーの名前だけ

## Assumptions & Open Questions

None.
