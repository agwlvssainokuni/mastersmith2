# SLO の報告（slo-report）

Intent `261004-role-menu`（F: ロールベースの権限、I: N 階層のメニュー）の運用の目標の報告です。

- 仮の目標（稼働 99.5%・30 日など）は、前の Intent の記録を正とします（`operation/observability-setup/slo-config.md` の冒頭）。正式な目標は、配備先が決まったときに決めます（`team.md`・`project.md` の Deployment）。
- この Intent の承認済みの目標は、新しい API の p95 1 秒（U3〜U5）と、権限の YAML の確かめ 15 秒・適用 30 秒（U4 の NFR2.4）です（`slo-config.md` 1節）。どちらも値は緩めません。
- 手元の監視を常に動かしていないため、運用の中の判定はすべて `Unverified` です。目標を緩めて満たしたことにはしません（`project.md` の Deployment の学び）。
- パスは、断りが無ければ `aidlc/spaces/default/intents/261004-role-menu/` を省いたリポジトリのルートからの相対パスで書きます。時刻は JST です（括弧の中は UTC）。

## 1. 判定

| 目標（窓） | 判定 | 理由 |
|---|---|---|
| 稼働（5xx でない応答の割合、仮 99.5%・30 日） | Unverified | 30 日の実測が無い。初回の配備（2026-10-14 08:20）から約 1 日で、手元の監視は止めている |
| 誤りの予算と、その消費の速さ | Unverified | 同上。正式な目標と予算が無い |
| グループの管理の7つの API の p95 1000 ms（U3、運用の中） | Unverified | 運用の中の常時の値が無い。k6 の判定（Met）は Performance Validation で済んでいる（2節） |
| ロールの管理・権限の木と保存・割り当ての API の p95 1000 ms（U4） | Unverified | 同上 |
| 作業ロールの読み取りと切り替え、自分の権限の木の p95 1000 ms（U4） | Unverified | 同上 |
| 業務のメニュー・置き場の p95 1000 ms（U5） | Unverified | 同上 |
| 権限の YAML の確かめ 15 秒・適用 30 秒（U4 の NFR2.4） | Unverified | 配備したアプリでは測っていない。使い捨ての環境の上限ちょうどの YAML では Met（2節） |
| 区画の API の 5xx でない割合（配備先が決まったときに決める） | Unverified | 運用の中の常時の値が無い |
| 監査の書き込みの失敗の件数（この Intent の種類） | Unverified | 同上。配備したアプリのログに、監査の失敗の行は無い（この段の確かめ） |
| 接続プールの時間切れの累計 0 | Unverified | 運用の中の常時の値が無い。使い捨ての環境の合否の回では 0。ただし U4-NFR2.7b（書き込みの2本使いの待ちの最大 20 ms 未満）は1回目が **Not Met**（23.55 ms）のまま持ち越す（`feedback-loop.md` の P1） |
| JVM のヒープの使用率（`ms-heap` 0.85、5 分） | Unverified | 配備したアプリ（上限 2g）の常時の値が無い。大きな入力のヒープは見積もりを超えた（2節、`cost-analysis.md` 3節） |
| 画面の時間（U6・U7、承認済みの目標の 5 回の中央値） | Unverified | 配備したアプリでは測らない（E2E の 140・160 の測りで記録）。運用の中の測り方は配備先で決める（`feedback-loop.md` の D6） |

## 2. 基準の値（今ある証拠）

| 時点 | 内容 | 値 | 出典 |
|---|---|---|---|
| 初回の配備（2026-10-14 08:20〜08:21） | 起動・移行・スモークテスト S1〜S10 | 約 51 秒止めて入れ替え、healthy・`/actuator/health` 200。V10〜V12 を当てて版 12。ERROR は「適用中の DSL を読めない」1 件（版 1 の DSL のため、見込みどおり）。S8（自分の権限の木）だけ不通過 | `operation/deployment-execution/deployment-log.md` 8節・9節、`smoke-test-results.md` 2節 |
| S8 の直しの配備（2026-10-14 16:54〜16:55） | 起動・S1・S8 の確かめ直し・S11 | 約 38 秒止めて入れ替え、healthy。ERROR 0・WARN 2（前の版からの案内）。S8 は通過。S11 の複写で約 9 秒・約 11 秒止めた。最後の状態は利用者 2・管理者の印 2・停止 0、ロール・グループの確かめ用のデータ 0 | `deployment-log.md` 11節・12.5、`health-check-report.md` 1節・2節 |
| Observability Setup（2026-10-15 01:30〜01:36、使い捨ての環境・上限 1g・同時 1 件） | 手元の監視に送った値 | 区画の API の p95 は 95 ms（すべて `le="100"` の中、補間の値）。最大は権限の保存の 28.5 ms、グループの一覧 19.9 ms。約 5 分の 515 件で 5xx 0 件。監査の失敗 0 件。接続プールの待ちの最大 0・時間切れの累計 0。ヒープの最大 0.280。`ms-forbidden` と `ms-app-absent` が鳴ることを確かめた（止めてから `Alerting` まで 10 分 20 秒） | `operation/observability-setup/slo-config.md` 2節、`alarms.md` 2節・2.1 |
| DSL の画面の直しの配備（2026-10-15 07:03） | 起動・S1 | 約 22 秒止めて入れ替え、起動から約 10 秒で healthy。版 12（新しい移行なし）。39 行・INFO 36・WARN 2・ERROR 0。救済の WARN 0 | `operation/performance-validation/redeploy-log.md` 1節・4節・5節 |
| Performance Validation（2026-10-15 07:07〜08:51、使い捨ての環境・上限 2g・CPU 4） | k6 の `iteration_duration` の p95 と1回ずつの時間 | グループ 3.5〜62.5 ms。ロール 3.2〜4.7 ms（書き出し 468.1 ms、木 97.0・108.9・13.3 ms、保存 24.9 ms）。自分の権限の木 177.3〜185.2 ms。メニュー・置き場 31.1〜229.2 ms。DSL の生成 2.69 s・投入 1.16 s（10 MiB）・戻し 0.92 s。権限の YAML の上限ちょうどで確かめ 4,070 ms・適用 8,744 ms。接続プールの合否の回はグループ 4.24 ms、ロール 1 回目 **23.55 ms（Not Met）**・流し直し 8.55 ms。`ms-audit-fail`・`ms-error-logs`・`ms-pool-pending` が鳴った。BUSY は 0 件 | `operation/performance-validation/nfr-validation-matrix.md` 1節・2節、`test-results.md` 2節・3節 |
| この段（2026-10-15 11:39〜11:45（02:39〜02:45Z）、読み取りだけ） | 配備したアプリ | healthy（08:51:07 に `docker compose start app` で起動し直したまま）、再起動 0、OOMKilled なし。メモリ 520.7MiB / 2GiB（25.43%）、CPU 0.30%。08:51 の起動の後のログは 39 行・INFO 37・WARN 2・ERROR 0 | この段で実行したコマンドの出力（`drift-report.md` 1節） |

- 判定に使わない理由: 使い捨ての環境の値は、負荷の形・上限・データの量が配備と違い、30 日の窓も持たないためです。
- 止まっていた時間は、どれも依頼者の判断による計画した停止です。障害による停止はありません。
  - 初回の入れ替え 約 51 秒、S8 の直しの入れ替え 約 38 秒、S11 の複写 約 9 秒・約 11 秒
  - DSL の画面の直しの入れ替え 約 22 秒
  - Performance Validation 約 1 時間 44 分
- 配備の後に、画面の不具合が2つ見つかり、直して配備し直しました。どちらも停止や 5xx を伴っておらず、稼働の数には入りません。
  - S8: 自分の権限の木で、テーブルの節を開くと描画が止まらない（`deployment-log.md` 10節・11節）
  - DSL の管理の画面で、英語の表示に全角の記号が出る（`operation/performance-validation/fix-notes.md`）
- 配備したアプリのコンテナのログ全体（83 行・INFO 79・WARN 4・ERROR 0）は、07:03 の起動と 08:51 の起動し直しの2回分です。WARN 4 件は、起動ごとに出る案内2件（Flyway の H2 の版、Spring の Bean）が2回出たものです。

## 3. 配備先が決まったときの測り方

1. 監視を常に動かします（配備先の監視の仕組みか、手元の監視の常時の起動）。配備したアプリの外部エクスポートを有効にし、稼働（5xx でない応答の割合）を 30 日の窓で測ります。今は配備したアプリが止まっても `ms-app-absent` は鳴りません（`alarms.md` 2.1、`feedback-loop.md` の D1）。
2. 区画の API（グループ・ロール・作業ロール・自分の権限・メニュー・置き場）は、`http_server_requests_milliseconds_bucket` の `le="1000"` の割合（`dashboards.md` の R4）と区画の p95（パネル 37）を常時取ります。目標は「30 日のうち、`le="1000"` に入る要求の割合」で決めます。区画の p95 の警報を足すかも、ここで決めます（D3）。
3. 権限の YAML の確かめ・適用は、5000 ms を超えるとバケットの外（`+Inf`）のため、パネルでは見ません。`http_server_requests_max_milliseconds` の道ごとの最大か、定期の k6 で見ます（`slo-config.md` 2節）。
4. 誤りの予算と、その消費の速さの警報（1 時間・6 時間の窓）を、正式な目標とあわせて決めます。警報のしきい値は、目標を割る前に鳴る値にします（`phases/operation.md` の Observability）。
5. 接続プールは `hikaricp_connections_timeout_total` の増加を見ます。`ms-pool-pending` の式の見直し（D7）と同時に決めます。U4-NFR2.7b の原因（P1）が分かるまでは、ロールの書き込みの待ちの最大を、合否の回を複数回流して見ます。
6. JVM のヒープは、配備したアプリの上限（2g）で常時取ります。急に尽きる場合は `ms-heap` が鳴らず、`ms-app-absent` だけが拾います（止めてから `Alerting` まで約 10 分）。JVM が止まったときに自動で起き直す設定にするかを決めます（D1）。
7. 画面の時間（U6・U7）は、実際の利用者の画面で測る仕組みを配備先で決め、その仕組みで判定します（D6）。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（決まっていること、Q1〜Q4・F1、確認済みの要約）
- `operation/observability-setup/slo-config.md`（1節〜3節）・`dashboards.md`・`alarms.md`（2節・2.1・2.2）
- `operation/performance-validation/nfr-validation-matrix.md`（1節〜4節）・`test-results.md`（1節〜3節）・`redeploy-log.md`・`fix-notes.md`
- `operation/deployment-execution/deployment-log.md`（8節〜12.5）・`health-check-report.md`・`smoke-test-results.md`
- `operation/incident-response/incident-plan.md`（5節・7節）
- 前の Intent の同じ段: `aidlc/spaces/default/intents/260930-user-admin/operation/feedback-optimization/slo-report.md`
- この段で実行したコマンドの出力（2026-10-15 02:39〜02:45 UTC）: `docker inspect mastersmith-app-1`、`docker stats --no-stream`、`docker logs mastersmith-app-1` と `jq`（レベル・時刻・ロガー・キーの名前・件数だけ）

## Assumptions & Open Questions

None.
