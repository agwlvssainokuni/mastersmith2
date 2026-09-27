# Monitoring Design — U6 登録の完了の画面（u6-registration-ui）

U6 の監視の設計です。U6 は画面（ui）の単位で、ui の単位には観測性の設計の文書がありません。U6 での扱いは NFR 設計の `logical-components.md` の4節に従います。画面の側に独自の指標・ログの送り先・外部への送信を足さず、`console` に何も出しません。サーバーの側の記録（要求の指標・監査・ログ）は U3 の持ち物です。手元の監視は既存のとおり `grafana/otel-lgtm` を compose の profile で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 7〜9）
- 既にある仕組み: `docker/monitoring/dashboards/mastersmith-overview.json`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`

## 1. Metrics & KPIs

画面の側に独自の指標・画面の実測（RUM）は足しません。U6 に関わる値は、サーバーの側の既存の指標と、Build and Test の測定で見ます。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| リンクの確かめの要求の数と時間 | 既存の `http.server.requests`（`uri="/api/registration/verify"`、U3） | p95 1 秒（同時 10 件、U3 の NFR6.3。Performance Validation または Build and Test の k6） | 画面のフォームが出るまでの待ちの一部。持ち主は U3 |
| 登録の完了の要求の数と時間 | 既存の `http.server.requests`（`uri="/api/registration/complete"`、U3） | p95 1 秒（同時 10 件、U3 の NFR6.4） | 送信中の待ち。持ち主は U3 |
| 登録の完了の記録 | 監査の `REGISTRATION_COMPLETED`（内部DB、U3） | 数えるだけ | 画面から登録が終わったことの裏付け。持ち主は U3 |
| リンクを開いてからフォームが出るまでの時間 | E2E-1 の5回の計測（テストの注記と添付、`json` の reporter のファイル） | 5回すべて 2 秒以内（NFR6.1。記録だけで関門にしない） | 画面の単位の利用者の体感。3節の SLI |
| 画面の塊の大きさ | B5 のコード生成の記録（`dist/.vite/manifest.json`・圧縮前と gzip） | 初回の JavaScript は gzip 500KB を超えたら警告（既存の `frontendBundleSize`） | NFR6.3。塊が入口に混ざると最初の画面が遅くなる |
| 実際のブラウザの検査の結果 | U4 の検査の手伝いの結果（`json` の reporter のファイル） | 2状態×20組で違反 0 件・横のはみ出しなし | NFR7.3。回数と時間は B5 と Build and Test で実測して記録 |

## 2. Alerts

U6 だけの警報は足しません。画面の側に指標が無く、サーバーの側の登録の API の警報は U3 の決定のとおり observability-setup の段で扱います。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| （足さない）登録の API の遅れ・5xx | U3 の持ち物。observability-setup の段で既存の警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）に足すかを決める | — | 手元の Grafana の画面だけ（通知の先は配備先が決まるまで作らない） |
| （足さない）E2E-1 の失敗 | `./gradlew e2eTest` の失敗 | — | 実行した開発者の手元の出力。統合の前に直す（`team.md` の Testing Posture） |

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| キャッシュが空の新しいコンテキストで招待のリンクを開いてから、メールアドレスの欄が見えるまでの時間（テストの側の時計、移動の直前から） | 5回すべて 2 秒以内（NFR6.1）。統合の関門にしない。2 秒を超えたら確かめの API・塊と入口の読み込み・U4 のゲートの待ちを切り分けて依頼者に相談する | `./gradlew e2eTest` の1回の実行の中の5回（Build and Test が結果に写す）。運用の中での判定は `Unverified` とし、observability-setup・feedback-optimization に引き継ぐ |
| リンクの確かめの p95・登録の完了の p95 | 1 秒（U3 の NFR6.3・NFR6.4） | U3 の持ち物（k6 の実行の中）。手元の監視を常に動かしていないため運用の判定は `Unverified`（`project.md` の Deployment） |

- 時間の値はテストの側の時計のため、Playwright の待ちの間隔を含んで長めに出ます。長めに出る側で判定し、目標を緩めません（`performance-design.md` の2節）。
- 5回の計測と同じ回で行う3つの確かめ（アドレス欄に `#token=` が無い・CSP の違反が無い・ブラウザの保存にトークンが無い）は失敗の条件にします（`performance-design.md` の2.1）。

## 4. Logs & Tracing

| 対象 | 扱い |
|---|---|
| 画面のログ | 画面は `console` に何も出さない（`security-design.md` の1.3 の見張り）。ログの送り先を足さない |
| サーバーのログ・トレース | U3 の持ち物。構造化ログと分散トレースは既存のとおりで、外部エクスポートは既定で無効（`team.md` の Deployment）。トークン・招待の URL・メールアドレスを載せない（`project.md` の Forbidden、U3 の `InvitationSecretLeakIT`） |
| E2E-1 の記録 | テストの注記と添付には、5回の時間（ミリ秒）・確かめの成否・照合した項目の名前と型だけを残す。URL・トークン・アドレス・メールの本文を載せない。HTML の報告と失敗のときのトレースに開いたリンクが載ることは受け入れ、手元だけに置いて共有しない（Q2: A） |
| E2E の WAR の出力 | 既存の `webServer` の `stdout: 'ignore'`・`stderr: 'pipe'` のまま |
| ダッシュボード | U6 のパネルは足さない。登録の API のパネルを既存の `mastersmith-overview.json` に足すかは U3 の決定のとおり observability-setup の段で決め、書く前に実際に起動して指標とラベルの名前を確かめる（`project.md` の Corrections） |

## 5. 上流との差

| ID | 文書 | 承認済みの記述 | この段での扱い | 理由 |
|---|---|---|---|---|
| M-D1 | NFR 設計 `performance-design.md` の2節 | 5回の値を注記と添付で残し Build and Test の結果に写す | 読み取りは U4 の同じ段の決定（`json` の reporter のファイル）に乗る | U4 の Q2: A の決定を使う。食い違いではない |
