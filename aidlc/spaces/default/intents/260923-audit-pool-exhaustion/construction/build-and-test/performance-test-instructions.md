# 性能の試験の手順（performance-test-instructions）

本 Intent の Test Strategy は Minimal で、性能の目標値（応答時間のパーセンタイルなど）は要件に無い。負荷をかけた確かめは、要件 FR6.2 の「配備した後、使い捨ての環境で k6 を使い、同時 10 件の成功のログインを流し直す」だけであり、その持ち主は Deployment Execution の段である。本段では実行しない。

## 1. 道具

- k6（`perf/k6/scenarios.js`、`constant-vus`）。手順は `perf/README.md`。
- 使い捨ての環境: 配備した環境とは別に起動し、仮の署名鍵・仮の利用者を使い、終わったら消す（`project.md` の Testing Posture）。本物のデータと監査ログを汚さない。

## 2. 確かめること（Deployment Execution の段で行う）

| 目標 | 合格の条件 | 要件 |
|---|---|---|
| 同時 10 件の成功のログインで監査が欠けない | 流したログインの数と `LOGIN_SUCCEEDED` の行の数が一致する。アプリのログに「Connection is not available」と ERROR「監査イベントの記録に失敗しました」が1件も無い | FR6.2 |
| 資源の上限の内側 | 既定値 30 で、アプリのコンテナのメモリの上限（`mem_limit: 1g`）を変えずに、起動・スモークテスト・上の確かめが通る | NFR2 |

## 3. 結果の扱い

- 結果は Deployment Execution の段の記録に置く。
- アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する（`project.md` の Testing Posture）。
