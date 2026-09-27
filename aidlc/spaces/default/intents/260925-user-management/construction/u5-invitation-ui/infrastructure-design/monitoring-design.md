# Monitoring Design — U5 招待の管理の画面（u5-invitation-ui）

U5 の監視の設計です。U5 は画面（ui）の単位で、サーバーの側の資源を持たないため、画面の側に独自の指標・ログの送り先・画面の実測（RUM）・外部への送信を足しません（`logical-components.md` の4節）。手元の監視は grafana/otel-lgtm を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 8〜10）
- 上流: `construction/u5-invitation-ui/nfr-design/performance-design.md`（4節）・`logical-components.md`（4節・5.5）、同じ段の `construction/u3-invitation/infrastructure-design/monitoring-design.md`・`construction/u4-display-foundation/infrastructure-design/monitoring-design.md`（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）

## 1. Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 一覧の1ページ目の時間（サイドバーの「利用者の招待」を押してから一覧の 20 行目が見えるまで） | `./gradlew e2eTest` の中の U5 の測定のテスト（5回、テストの側の時計）。注記と添付で残し、`json` の reporter の結果（`frontend/test-results/` の下）から Build and Test が写す | 2 秒以内（NFR6.1）。時間で失敗させない | 利用者の体感の時間。API の時間に、画面の塊の読み込みと描画を足した値 |
| 次のページの時間（「次へ」を押してから2ページ目の最初の行が見えるまで） | 同上 | 1.5 秒以内（NFR6.2）。時間で失敗させない | ページ送りの体感の時間 |
| 招待の API の要求の数と時間（`http_server_requests_milliseconds_*`、`uri` が `/api/admin/invitations` ほか） | 既存の Spring MVC の観測。U3 の `monitoring-design.md` の指標の表 | U3 の NFR6.1・NFR6.3（U5 は足さない） | 画面の時間が遅いときに、API と画面の描画のどちらが遅いかを分ける |
| 初回の JavaScript の大きさ | 既存の `frontendBundleSize`（`./gradlew verify` の中） | gzip で 500KB を超えたら警告だけ（NFR6.6） | U5 の画面が入口のファイルを増やしていないことを見る |

## 2. Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 足さない | U5 だけの警報は置かない。招待の API の警報は U3 の決定のとおり手元の監視では置かない | — | 警報の通知の先は、配備先が決まるまで置かない（`project.md` の Deployment） |

- 画面の時間が目標を超えたときは、目標を緩めず、要求の一覧で一覧の API の時間と画面の描画のどちらが遅いかを確かめ、結果とともに依頼者に相談します（NFR 要件の2節、`performance-design.md` の4.4）。

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 一覧の1ページ目の時間（1節） | 5回すべて 2 秒以内（NFR6.1）。`e2eTest` の値は Build and Test が記録し、運用の中での判定は `Unverified` | `./gradlew e2eTest` の1回の実行（5回の繰り返し）。運用の中の測り方は配備先が決まったときに observability-setup・feedback-optimization で決める |
| 次のページの時間（1節） | 5回すべて 1.5 秒以内（NFR6.2）。判定は上と同じ | 同上 |

- 測定は招待を 21 件置き、メールを 21 通送り、監査に残すため、配備したアプリに向けては流しません。`e2eTest` の中だけで測ります（U4 の Q1: A と同じ考え方、要点 10）。
- 招待を使える設定が無く測定を飛ばしたときは、Build and Test で `Unverified` とし、持ち主（Build and Test）を明記して引き継ぎます（`performance-design.md` の4.2、要点 14）。
- 手元の監視を常に動かしていない間は SLO の判定をせず、目標を緩めて満たしたことにはしません（`project.md` の Testing Posture・Deployment）。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| 画面のログ | U5 のコードは `console` を呼ばない。画面部品のテストで5つの関数（`log`・`info`・`warn`・`error`・`debug`）を見張る（`security-design.md` の2節） |
| サーバーのログとトレース | U5 は足さない。招待の API のログとトレースは U3・U1 の観測性の設計のとおり（招待のトークン・URL・メールアドレスを出さない） |
| ダッシュボード | U5 だけのパネルは足さない。招待の API のパネルは U3 の決定のとおり observability-setup で起動して確かめる |
| 検査の結果 | 組・状態ごとの成否・違反の件数・規則の名前・`incomplete` と時間の値を、`json` の reporter の結果から Build and Test が写す。アクセストークン・パスワードは注記・添付に出さない |

## 5. 上流との差

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| 結果の読み取り | NFR 設計は「注記と添付で残し、Build and Test が写す」 | U4 の同じ段の決定（Q2: A）の `json` の reporter の結果から写す | U4 の決定の適用。食い違いではない |
| 運用の中での判定 | NFR 要件は Build and Test で記録し関門にしない | 運用の判定は `Unverified` とし、持ち主の段を observability-setup・feedback-optimization と明記した | `project.md` の Testing Posture・Deployment で埋めた追加 |
