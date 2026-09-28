# SLO の報告（slo-report）

Intent `260925-user-management` の運用の目標の報告です。仮の目標（稼働 99.5%・30 日など）は、前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/slo-config.md` を正とし、配備先が決まったときに正式に決めます（project.md の Deployment）。

## 1. 判定（Q1: A）

| 目標 | 判定 | 理由 |
|---|---|---|
| 稼働（5xx でない応答の割合、30 日） | Unverified | 手元の監視を常に動かしていないため、30 日の実測が無い。配備から数時間 |
| 誤りの予算の消費の速さ | Unverified | 同上 |
| API の p95（運用の中） | Unverified | アプリの指標 `http_server_requests` にバケットが無く、p95 を出せない（Observability Setup の `dashboards.md` 2節）。k6 の値は使い捨ての環境のもの |
| 画面の時間（U4〜U7 の NFR6.x、運用の中） | Unverified | 配備したアプリでは測らない（E2E の中でだけ測る設計） |
| 見た目の設定の API（U8 の NFR9.3 ほか、運用の中） | Unverified | 同上の理由（p95 を出せない） |

目標を緩めて満たしたことにはしません（project.md の Deployment）。

## 2. 基準の値（今ある証拠）

| 時点 | 内容 | 値 | 出典 |
|---|---|---|---|
| 配備の直後（2026-09-29 00:31〜00:43） | 起動・スモークテスト S1〜S12 | healthy（12 秒）、S1〜S12 すべて通過、ERROR 0 件 | `operation/deployment-execution/smoke-test-results.md`・`health-check-report.md` |
| Observability Setup（01:08〜01:27） | 式の実行（使い捨ての環境から送った値） | 42 の式すべて success。5xx 0。招待と登録の6本の API の p95（トレースから作る指標）は 0.008〜0.563 秒、送信の p95 0.042 秒 | `operation/observability-setup/alarms.md` 3節 |
| Performance Validation（01:50〜02:14、使い捨ての環境、同時 10） | k6 の 17 場面 | 21 件すべて Met。軽い API の p95 は 0.96〜91.2 ms、bcrypt を使う API は 904〜931 ms（1 秒の目標まで余裕 69〜96 ms）、パスワードの変更 1.79 s（2 秒まで余裕 0.21 秒）、招待 29.4 ms、送り直し 20.9 ms。接続の待ちの時間切れ 0、待ちの最大 12.5 ms | `operation/performance-validation/test-results.md`・`nfr-validation-matrix.md` |
| この段（02:22） | 配備したアプリの健全性 | healthy、`/actuator/health` は UP、起動の後の ERROR 0 件、招待を使える設定 `enabled: true`、内部DB の版 8、メモリ 520MiB / 2GiB・CPU 0.3% | この段で実行したコマンドの出力 |

- 止まっていた時間（依頼者の判断による計画した停止）:
  - 配備の6〜8（約1分半）
  - 配備の後のバックアップ（約7秒）
  - Observability Setup の作り直し（2回、各数十秒）
  - Performance Validation（約 24 分半）
  - 障害による停止はありません。

## 3. 配備先が決まったときの測り方

1. 監視を常に動かします（配備先の監視の仕組み、または手元の監視を常に起動）。外部エクスポートを有効にし、稼働（5xx でない応答の割合）を 30 日の窓で測ります。
2. アプリの指標にバケットを出します（次の Intent の候補、`feedback-loop.md`）。その後、API ごとの p95 と、p95 の警報3件（`ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`）を働かせます。
3. 誤りの予算の消費の速さ（1 時間・6 時間の窓）の警報を、正式な目標とあわせて決めます。
4. 画面の時間は、実際の利用者の画面で測る仕組みを配備先で決めます。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（Q1〜Q4、確認済みの要約）
- `operation/observability-setup/slo-config.md`・`dashboards.md`・`alarms.md`
- `operation/performance-validation/test-results.md`・`nfr-validation-matrix.md`
- `operation/deployment-execution/deployment-log.md`・`smoke-test-results.md`・`health-check-report.md`
- 前の Intent の仮の目標: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/slo-config.md`
- この段で実行したコマンドの出力（`docker compose ps`・`docker inspect`・`docker stats`・`curl /actuator/health`・`docker compose logs app` の件数）

## Assumptions & Open Questions

None.
