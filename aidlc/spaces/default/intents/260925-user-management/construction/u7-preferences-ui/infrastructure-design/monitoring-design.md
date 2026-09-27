# Monitoring Design — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

U7 の監視の設計です。U7 は画面（ui）の単位です。ui の単位には観測性の設計の文書が無いため、U7 での扱いは NFR 設計の `logical-components.md` の4節に従います（画面の側に独自の指標・ログの送り先・外部への送信を足さない）。

手元の監視は既存のとおりです。`grafana/otel-lgtm` を compose の profile `monitoring` で、見たいときだけ起動します。ダッシュボードと警報の決まりはファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 7〜9、質問なし、Looks correct）
- 上流: `construction/u7-preferences-ui/nfr-design/`（`performance-design.md` の1節・4節、`logical-components.md` の4節）。同じ段の `construction/u2-user-preferences/infrastructure-design/monitoring-design.md`・`construction/u4-display-foundation/infrastructure-design/monitoring-design.md`
- 既にある仕組み: `docker/monitoring/dashboards/mastersmith-overview.json`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`

## 1. Metrics & KPIs

画面の側に、独自の指標・画面の実測（RUM）・外部への送信は足しません。U7 に関わる値は、2つの方法で見ます。

- サーバーの側: 既存の指標（U2 の持ち物）
- 画面の時間: Build and Test の測り（`./gradlew e2eTest`）

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| `GET`・`PUT /api/me/preferences` の要求の数・`status` ごとの数・p95 | 既存の `http.server.requests`（Prometheus では `http_server_requests_milliseconds_*`）の `uri="/api/me/preferences"`（U2 の `monitoring-design.md`） | p95 1 秒（U2 の NFR6.1・NFR6.2） | プリファレンスの画面を開く時間と保存の時間の大半を占める |
| `POST /api/me/password` の `status` 204・400 ごとの p95 | 既存の `http.server.requests` の `uri="/api/me/password"`（U2） | 204 は p95 2 秒・400 は p95 1 秒（U2 の NFR6.3・NFR6.4） | パスワードの変更の時間の大半を占める（bcrypt 2回） |
| プリファレンスの画面・パスワードの変更の画面を開く時間 | `./gradlew e2eTest` の U7 の測りのテスト（画面ごとに5回）。結果は `frontend/test-results/` の JSON の報告（`cicd-pipeline.md` の 3.2） | 5回すべて 2 秒以内（NFR6.1） | 利用者の体感の時間。遅延読み込みの塊を含む。統合の関門にしない |
| 保存の時間 | 同上（5回） | 5回すべて 1.5 秒以内（NFR6.2） | 同上 |
| パスワードの変更の時間 | 同上（5回） | 5回すべて 2.5 秒以内（NFR6.3） | 同上 |
| 初回の JavaScript の大きさ（gzip） | 既存の `frontendBundleSize`（`./gradlew verify` の中） | 500KB を超えたら警告だけ（NFR6.6） | U7 の変更の前後の値をコード生成で記録する |

- `uri` のラベルは利用者の識別を含みません（U2 の `monitoring-design.md`）。

## 2. Alerts

U7 だけの警報は足しません。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 足さない（U7 だけの警報） | — | — | — |
| 既存の警報（アプリの停止・5xx の割合・ERROR のログ） | 既存の `docker/monitoring/provisioning/alerting/mastersmith.yaml` のとおり。`/api/me/` の 5xx も全体の 5xx の割合に含まれる | 既存のとおり | 手元の監視の画面だけ（通知の先は配備先が決まるまで作らない） |

- `/api/me/` の p95 の警報・専用のパネルは足しません（U2 の決定）。目標の確かめは、U2 の k6 と、U7 の画面の時間の測りで行います。

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| ユーザーメニューの項目を押す直前から、「保存する」または「変更する」のボタンが見えるまでの時間。キャッシュの空の新しいコンテキストで、テストの側の時計を使い、長めに出る側で判定する | 画面ごとに5回すべてが 2 秒以内（NFR6.1） | Build and Test の `./gradlew e2eTest` の1回の実行。画面・認証に関わる変更を統合する前（B5）とリリースの前 |
| 「保存する」を押す直前から、Toast「保存しました」が見えるまでの時間 | 5回すべてが 1.5 秒以内（NFR6.2） | 同上 |
| 「変更する」を押す直前から、Toast「パスワードを変更しました」が見えるまでの時間 | 5回すべてが 2.5 秒以内（NFR6.3） | 同上 |
| U2 の3本の API の応答時間 p95 | U2 の目標（NFR6.4 で参照） | U2 の段のとおり（使い捨ての環境の k6） |

- 測る場は `./gradlew e2eTest` の中だけです。配備したアプリでは画面の時間を測りません（U4 の Q1: A と同じ扱い）。
- E2E の WAR は PC の上の `java -jar` で動き、内部DB は実行ごとに空です。配備したアプリはコンテナの上限の中で動き、内部DB は使い続けたファイルです。この違いを Build and Test の記録に明記します。
- 手元の監視を常に動かしていないため、運用の中での判定は `Unverified` とし、持ち主の段（observability-setup・feedback-optimization）に引き継ぎます。目標を緩めて満たしたことにはしません（`project.md` の Testing Posture・Deployment）。
- 測りを飛ばしたとき（招待を使えない・受け手に届かない。通常の実行では起きない。`cicd-pipeline.md` の 3.3）は、Build and Test が NFR6.1〜NFR6.3 を `Unverified` とし、持ち主と理由を明記します。
- 目標を超えたときは、要求の一覧と時刻で、どこが遅いかを切り分けて依頼者に相談します（NFR 設計 `performance-design.md` の4.4）。切り分ける先は、API の時間・画面の塊の読み込み・描画の3つです。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| 画面のログ | 送り先を足さない。`console` に氏名・パスワード・応答の値を出さない（NFR2.1・NFR9.1） |
| サーバーのログ | U7 はサーバーのログを足さない。3本の API のログは U2 の持ち物 |
| トレース | 既存の Micrometer Tracing のまま。画面の側からトレースの情報を送らない。外部エクスポートは既定で無効（`team.md` の Deployment） |
| 監査 | U7 は監査の出来事を出さない。利用者の設定の保存とパスワードの変更の監査は U2 の持ち物 |
| ダッシュボード | U7 のためのパネルは足さない（2節） |
| 検査と測りの結果 | 注記と添付（JSON）には、次のものだけを入れる: 時間の値・組と状態の名前・成否・違反の件数と規則の名前・`incomplete`。秘密と個人に関する値は入れない（`cicd-pipeline.md` の4節） |

## 5. 上流との差

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| NFR6.1〜NFR6.3 の運用の中の判定 | e2eTest の中で5回測って記録し、関門にしない（`performance-design.md` の4節） | そのまま。配備したアプリでは測らず、運用の中の判定は `Unverified` として引き継ぐ | 食い違いではない。U4 の Q1: A と同じ扱いを明記した追加（要点 9） |
