# Monitoring Design — U8 インスタンスの見た目の設定（u8-instance-appearance）

`nfr-design/observability-design.md` の方針を、手元の監視の仕組みに当てた設計です。手元の監視は `grafana/otel-lgtm` を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイル（`docker/monitoring/dashboards/`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`）でリポジトリに置く（`aidlc/spaces/default/memory/project.md` の Deployment）。答えは `infrastructure-design-questions.md`（Consolidated Summary Confirmation: Looks correct）。

出典の略号は `infrastructure-specification.md` と同じ。

## 1. Metrics & KPIs

U8 は独自の指標を足さない。既存の HTTP の要求の指標で見る（NFR6.5、要点 8）。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 要求の数（`uri="/api/appearance"`） | 既存の `http.server.requests`（Prometheus では `http_server_requests_milliseconds_count`） | 置かない（画面を開く回数に比例） | U4 が画面を開くたびに1回呼ぶため、画面の利用の量の目安になる |
| 応答時間の 95 パーセンタイル（`uri="/api/appearance"`） | `http_server_requests_milliseconds_bucket` | 300 ミリ秒（同時 10 件、NFR6.1） | すべての画面の最初の描画がこの応答を待つ（U4 W2） |
| `status` ごとの数（`uri="/api/appearance"`） | `http_server_requests_milliseconds_count` の `status`・`outcome` | 5xx は 0 が期待値。401・405 は GET 以外の要求で起きる | 5xx は想定外の失敗で、画面は既定のまま描く（NFR9.2、U4 W3） |
| 起動時の設定の警告の件数 | アプリのログ（Loki）の WARN | 置かない（0 が期待値） | 許されない値を設定したことに気づく（NFR9.4） |
| 内部DB の接続を借りた回数 | 運用には置かない | — | 「借りない」ことは結合テストで確かめる（NFR5.2、`reliability-design.md` の3.2節） |

ダッシュボードのパネルの候補（足すかは observability-setup の段で決める。`observability-design.md` の1節）: 既存の「ログインの API」「確認用 API（/api/admin/check）」と同じ形の p95 の式を、`uri` を `/api/appearance` にして1つ足す。

```
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_milliseconds_bucket{service_name="mastersmith",uri="/api/appearance"}[5m])))
```

式は observability-setup の段で実際に起動して `uri` のラベルの値を確かめてから書き、書いた後にすべての式を実行して確かめる（`project.md` の Corrections）。

## 2. Alerts

U8 だけの警報は置かない（`observability-design.md` の4節）。アプリ全体の既存の警報で拾う。U8 だけの警報を足すかは observability-setup の段で決める。通知の先は、配備先が決まるまで置かない（Grafana の画面で見る）。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| アプリの停止（既存） | `process_uptime_milliseconds` が届かない | 既存のまま | Grafana の画面（通知の先なし） |
| 5xx の割合の増加（既存） | アプリ全体の 5xx の割合（`/api/appearance` の 5xx も含まれる） | 既存のまま | Grafana の画面（通知の先なし） |
| ERROR のログの増加（既存） | `logback_events_total` の error の増加 | 既存のまま | Grafana の画面（通知の先なし） |

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| `uri="/api/appearance"` の応答時間の 95 パーセンタイル | 300 ミリ秒以内（同時 10 件、NFR6.1） | performance-validation の k6 の試験の間（`cicd-pipeline.md` の3節）。運用では 5 分の rate |
| `uri="/api/appearance"` の 200 の割合 | U8 だけの目標は置かない。アプリ全体の可用性の目標に含める | 判定は `Unverified`（手元の監視を常に動かしていないため）。持ち主は observability-setup・feedback-optimization（NFR9.3） |

目標を緩めて満たしたことにはしない。手元の監視を常に動かしていない間は、配備の直後・監視の確かめ・負荷の試験の時点の値を基準の値として並べる（`project.md` の Deployment）。

## 4. Logs & Tracing

| 項目 | 設計 | 出典 |
|---|---|---|
| 起動時の警告 | 業務処理の Bean を作るときに、許されない値の項目ごとに WARN を1件（スタックトレースなし）。キー・値の API で項目の名前・使った既定・許される値の一覧を出し、設定された値は出さない。要求の外のためトレース ID は付かない。本番は起動につき1回 | NFR9.4、BR2.1 |
| 要求ごとのログ | 足さない。想定外の失敗は既存の共通のエラー応答の境界で1回だけ出る | `team.md` の Code Style |
| ログの集め方 | 既存のまま（コンテナの標準出力の JSON と、外部エクスポートを有効にしたときの Loki）。外部エクスポートは既定で無効 | `team.md` の Deployment |
| トレース | 既存の Micrometer Tracing（W3C Trace Context）のまま。U8 の要求は既存の HTTP のスパンとして記録され、独自のスパン・属性は足さない | NFR6.6 |
| 監査 | 読み取りは監査の出来事を出さない。監査をログで代用しない | NFR9.5 |
| ダッシュボード | 既存の `docker/monitoring/dashboards/mastersmith-overview.json` のまま。1節のパネルの候補を足すかは observability-setup で決める | NFR6.5 |

## 5. 上流との差

承認済みの文書と食い違う点は無い。パネル・警報を足すかの決定は、承認済みの `observability-design.md` のとおり observability-setup の段に残し、この段では候補の式だけを記録した。
