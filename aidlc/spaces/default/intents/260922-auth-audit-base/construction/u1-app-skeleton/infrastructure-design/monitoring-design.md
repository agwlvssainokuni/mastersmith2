# Monitoring Design — U1 アプリの骨格（u1-app-skeleton）

U1 の `observability-design.md`（NFR Design）の方針を、当面の配備先（開発者の PC 上のコンテナ）で実現する設計。性能・信頼性の目標は `performance-design.md`・`reliability-design.md`、安全は `security-design.md`、容量は `scalability-design.md`、部品は `logical-components.md` と Domain Design の `components.md`、処理の流れは `functional-spec.md` に従う。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。

当面は常時の監視の仕組み（収集・保存・警報の基盤）を持たない。本番の目標（SLO）と警報、ダッシュボードの置き場所は、配備先が決まったときに Operation の段階（Observability Setup）で定める。本書は、そのときに使う指標・しきい値の候補と、当面の確かめ方を定める。

## 1. 指標（Metrics & KPIs）

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| ヘルスの状態 | `/actuator/health`（コンテナのヘルスチェック） | DOWN が2回続く | 稼働しているかの指標（NFR10.8） |
| 5xx の割合 | `http.server.requests`（状態コード別の件数） | 5 分間で 1% 超 | エラーの割合の指標（NFR10.9） |
| ERROR のログの件数 | `logback.events`（level=error） | 5 分間で 5 件超 | 想定外のエラー、監査の書き込みの失敗（U4） |
| ログインの API の応答時間 | `http.server.requests`（uri=/api/auth/login の 95 パーセンタイル） | 1 秒超（U2 の NFR1.1 の目標） | 利用者の体感 |
| コネクションプールの待ち | `hikaricp.connections.pending`・`hikaricp.connections.acquire` | 待ちが 0 でない状態が 1 分続く | 接続の不足の兆し（U1 の `scalability-design.md`） |
| JVM のメモリ | `jvm.memory.used`（heap） | 上限の 85% 超が 5 分続く | メモリの不足の兆し |
| 起動の時間 | コンテナの起動の開始から health が UP まで | 30 秒超 | NFR1.4 |
| ボリュームの使用量 | コンテナの外（`docker system df -v`） | 見積もりの2倍（監査ログは U1 の NFR1.9 の 1年 約 180MB（1日 500 件 × 1KB × 365 日）。索引を含めた大きさは U4 の見積もりで 250MB 程度のため、しきい値はボリューム全体で 1年あたり 500MB とする） | 監査ログの増え方の見直し（NFR1.9） |

指標は、外部エクスポートを有効にしたときだけ OTLP で送る（Q3・Q5 の NFR Design の決定）。当面は、確認用の OTLP の受け手（`infrastructure-specification.md` 2章）の標準出力と、Performance Validation の測定で確かめる。

## 2. 警報（Alerts）

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| アプリの停止 | コンテナのヘルスチェックが unhealthy | 高 | 当面: 開発者が `docker compose ps` で確認。配備先が決まったら通知の先を決める |
| エラーの増加 | 5xx の割合が 5 分間で 1% 超、または ERROR が 5 分間で 5 件超 | 中 | 同上 |
| 監査の書き込みの失敗 | U4 の ERROR（監査イベントの記録に失敗）が1件でも出る | 中 | 同上（記録の内容はログから補う） |
| 応答の遅れ | ログインの API の 95 パーセンタイルが 1 秒超 | 低 | 同上 |

しきい値は、目標（SLO）を割る前に気づけるように置いた候補である。配備先が決まったら、実際の量を見て見直す。

## 3. 目標（SLIs / SLOs）

| SLI | SLO target | Measurement window |
|---|---|---|
| 稼働（5xx でない応答の割合） | 配備先が決まったときに決める（候補: 99.5%） | 30 日 |
| ログインの API の応答時間（95 パーセンタイル） | 1 秒以内（U2 の NFR1.1） | 30 日 |
| ヘルスチェックの UP の割合 | 配備先が決まったときに決める | 30 日 |

当面の開発者の PC では目標を測らない。Performance Validation で、応答時間の目標を負荷の試験で確かめる。

## 4. ログとトレース

| 項目 | 当面の設計 | 配備先が決まったとき |
|---|---|---|
| ログの集め方 | コンテナの標準出力（1行1件の JSON）。`docker compose logs` で見る。`traceId` で絞り込む | コンテナの実行環境のログの仕組みか、OTLP の受け手へ送る |
| ログの保存と回し | コンテナの実行環境の既定（json-file のドライバー。大きさの上限を compose で 10MB × 3 に設定） | 配備先の仕組みで保存期間を決める |
| トレース | 常に有効。外部エクスポートの有効時はサンプリング率 100% で送る | 送り先を配備先で決める |
| 確認の手順 | `docker compose --profile observability up` で OTLP の受け手を起動し、外部エクスポートを有効にして、トレース・ログ・指標が届くことと秘密情報が載らないことを確かめる | — |
| ダッシュボード | 持たない | Observability Setup で、1章の指標から「稼働・エラー・応答時間・資源」の4つの面を作る |
