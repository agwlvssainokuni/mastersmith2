# Monitoring Design — U4 画面の表示の設定の土台（u4-display-foundation）

U4 の監視の設計です。U4 は画面（ui）の単位で、ui の単位には観測性の設計の文書がありません。U4 での扱いは NFR 設計の `logical-components.md` の4節（画面の側に独自の指標・ログの送り先・外部への送信を足さない）に従います。手元の監視は既存のとおり `grafana/otel-lgtm` を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 8〜10、Q1: A）
- 既にある仕組み: `docker/monitoring/dashboards/mastersmith-overview.json`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`

## 1. Metrics & KPIs

画面の側に独自の指標・画面の実測（RUM）・外部への送信は足しません。U4 に関わる値は、サーバーの側の既存の指標と、Build and Test の測定で見ます。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| `GET /api/appearance` の要求の数・`status` ごとの数・p95 | 既存の `http.server.requests`（Prometheus では `http_server_requests_milliseconds_*`）の `uri="/api/appearance"`（U8 の持ち物） | p95 300 ミリ秒（U8 の NFR6.1） | 画面を開くたびに1回呼ばれ、最初の描画の待ちに入る。待ちの上限を置かないため、遅れは全画面の最初の描画に及ぶ |
| トークンの更新の p95 | 既存の `http.server.requests` の更新の `uri` | p95 1 秒（U2 の NFR6.5） | 最初の描画の待ちの大半を占める |
| 最初の画面（ログインの画面）が出るまでの時間 | `./gradlew e2eTest` の測定のテスト（5回）。結果は `frontend/test-results/` の JSON の報告（`cicd-pipeline.md` の4節） | 5回すべて 2 秒以内（NFR6.1） | 利用者の体感の時間。統合の関門にしない |
| 初回の JavaScript の大きさ（gzip） | 既存の `frontendBundleSize`（`./gradlew verify` の中） | 500KB を超えたら警告だけ（NFR6.3） | 最初の描画の読み込みの時間に効く |
| 配信物の大きさ（フォント・`dist`・WAR） | コード生成で測って記録する（`infrastructure-specification.md` の3節） | 上限なし（NFR6.5） | Noto Serif JP を足した増え方を残す |

## 2. Alerts

U4 だけの警報は足しません。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 足さない（U4 だけの警報） | — | — | — |
| 既存の警報（アプリの停止・5xx の割合・ERROR のログ） | 既存の `docker/monitoring/provisioning/alerting/mastersmith.yaml` のとおり | 既存のとおり | 手元の監視の画面だけ（通知の先は配備先が決まるまで作らない） |

- `GET /api/appearance` の p95 のパネルを既存のダッシュボードに足すかは、U8 の決定のとおり observability-setup の段で決めます。式は、実際に起動して `uri` のラベルの値を確かめてから書き、書いた後にすべて実行して確かめます（`project.md` の Corrections）。

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| キャッシュが空の新しいブラウザのコンテキストで、画面を開いてからログインの画面の見出しが見えるまでの時間（テストの側の時計、長めに出る側で判定） | 5回すべてが 2 秒以内（NFR6.1） | Build and Test の `./gradlew e2eTest` の1回の実行（5回の測定）。画面・認証に関わる変更を統合する前とリリースの前 |
| `GET /api/appearance` の応答時間 p95 | 300 ミリ秒以内（同時 10 件、U8 の NFR6.1） | U8 の段のとおり（使い捨ての環境の k6） |

- 測る場は `./gradlew e2eTest` の中だけです（Q1: A）。配備したアプリでは画面の時間を測りません。配備したアプリの API の時間は U8 の k6 と既存のスモークテストで押さえます。
- 手元の監視を常に動かしていないため、運用の中での画面の時間の判定は `Unverified` とし、持ち主の段（observability-setup・feedback-optimization）に引き継ぎます。目標を緩めて満たしたことにはしません（`project.md` の Testing Posture・Deployment）。
- 2 秒を超えたときは、2つの API・JavaScript の読み込み・描画のどれが遅いかを要求の一覧と時刻で切り分け、依頼者に相談します（`performance-design.md` の3.2）。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| 画面のログ | 送り先を足さない。`console` にトークン・メールアドレス・氏名を出さない |
| サーバーのログ | U4 はサーバーのログを足さない。`/api/appearance` の要求ごとのログも無い（U8） |
| トレース | 既存の Micrometer Tracing のまま。画面の側からトレースの情報を送らない。外部エクスポートは既定で無効（`team.md` の Deployment） |
| 監査 | U4 の操作は監査の出来事を出さない（表示の設定はブラウザの中だけ。利用者の設定の保存の監査は U2） |
| ダッシュボード | U4 のためのパネルは足さない（2節） |

## 5. 上流との差

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| NFR6.1 の測る場 | 「Build and Test で、既存の E2E の中で測って記録する」 | そのまま。配備したアプリでは測らず、運用の中での判定は `Unverified` として引き継ぐ（Q1: A） | 食い違いではない。運用の中での判定の扱いを明記した追加 |
