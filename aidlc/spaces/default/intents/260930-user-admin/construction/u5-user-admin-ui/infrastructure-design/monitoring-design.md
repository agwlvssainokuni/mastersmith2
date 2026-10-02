# Monitoring Design — U5 利用者の管理の画面（u5-user-admin-ui）

U5 の監視の設計です。U5 は画面（ui）の単位で、サーバーの側の資源を持たないため、画面の側に独自の指標・ログの送り先・画面の時間の計測と送信（RUM）・外部への送信を足しません（`logical-components.md` 5節、要点 3）。管理の API の遅さ・誤り・403 は、U3 の基盤の設計の `monitoring-design.md` の指標・警報と監査で見ます。U5 のために警報とパネルを足しません。画面の時間（NFR5.1・NFR5.2）は手元の 120 の測りのテストで記録するだけで、成否にしません。この記録で NFR5.1・NFR5.2 を満たしたことにはせず、本番での判定は `Unverified` として持ち主の段（performance-validation・observability-setup・feedback-optimization）に引き継ぎます（承認の場の決定 R-01、`traceability.json` は `Deferred`）。特に `nextPage` は見本の応答で測るため API の時間を含みません。手元の監視は grafana/otel-lgtm を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（決まっていること、要点 3・7）
- 上流: `construction/u5-user-admin-ui/nfr-design/performance-design.md`（1節・5節・6節）・`security-design.md`（3.5・3.6）・`logical-components.md`（5節）、`construction/u5-user-admin-ui/nfr-requirements/performance-requirements.md`（NFR5.1〜NFR5.3・NFR5.6）、同じ段の `construction/u3-user-admin-api/infrastructure-design/monitoring-design.md`（1節・2節・8節）（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）
- 既にある仕組み（正とする、読むだけ）: `docker/monitoring/provisioning/alerting/mastersmith.yaml`・`docker/monitoring/dashboards/`・`backend/src/main/resources/application.yaml`（`http.server.requests` の `slo` のバケット）・`frontend/scripts/check-bundle-size.mjs`

## 1. Metrics & KPIs

U5 は新しい指標を足しません。次の値で U5 の動きを見ます。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 一覧を開くまでの時間（サイドバーの「利用者の管理」を選ぶ直前から、一覧の最初の行が見えるまで） | `./gradlew e2eTest` の中の 120 の測りのテスト1件（既定の1組、5 回、テストの側の `Date.now()`）。添付 `user-admin-screen-ms` の `list` に数だけを残す | 目標は 5 回すべて 2 秒以内（NFR5.1）。記録のみで時間では失敗させない。本番での判定は `Unverified` | 利用者の体感の時間。一覧の GET は本物へ通すため、本物の API の時間と差し替えの口の上乗せ（1往復）を含む（`performance-design.md` 5.5） |
| 次のページまでの時間（「次へ」を押す直前から、2ページ目の最初の行が見えるまで） | 同じテストの後半。添付 `user-admin-screen-ms` の `nextPage` | 目標は 5 回すべて 1.5 秒以内（NFR5.2）。記録のみで時間では失敗させない。本番での判定は `Unverified` | 画面の描画とページ送りの扱いの時間。見本の応答で測るため、API の時間を含まない（`apiTimeIncluded` は偽、承認の場の R-05）。この値だけで NFR5.2 を満たしたことにはしない |
| 利用者の管理の API の要求の数と時間（`http_server_requests_milliseconds_bucket`・`_count`、`uri` が `/api/admin/users` ほか7つの API の見込み、`method`・`status` 別） | 既存の Spring MVC の観測。U3 の `monitoring-design.md` 1節の表 | p95 1000 ms（U3 の NFR5.1・NFR5.3・NFR5.4）。U5 は足さず、測り直さない（NFR5.3） | 画面の時間が遅いときに、API と画面の描画のどちらが遅いかを分ける。`uri` は道の型で、利用者 ID と検索の文字を含まない |
| 初回の JavaScript の大きさ | 既存の `frontendBundleSize`（`./gradlew verify` の段 9） | gzip で 500KB を超えたら警告だけ（NFR5.6） | 画面を `lazy` で読むため入口のファイルを増やさないことを見る。固定先を上げる前と U5 の後の値をコード生成で記録する |

## 2. Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-forbidden`「管理画面への拒否の増加」（既存、変えない） | 403 が 1 時間に 20 件を超えた | 中 | 手元の Grafana の警報の一覧だけ。通知の先は配備先が決まるまで置かない（`project.md` の Deployment） |
| `ms-5xx-ratio`「5xx の割合の増加」（既存、変えない） | 全体の要求に対する 5xx の割合が 0.01 を超える状態が 5 分 | 中 | 同上 |
| `ms-error-logs`「ERROR のログの増加」（既存、変えない） | ERROR のログが 5 分に 5 件を超えた | 中 | 同上 |
| U5 だけの警報 | 足さない | — | — |

- 管理の API の遅さは、U3 の決定のとおり手元の監視では警報にしません（U3 の `monitoring-design.md` 1節）。
- 画面の時間が目標を超えたときは、目標を緩めず、一覧の API の時間（1節の3行目）か画面の描画かを確かめて依頼者に相談します（`performance-design.md` 1節、`project.md` の Testing Posture）。

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 一覧を開くまでの時間（1節） | SLO は置かない。目標は 5 回すべて 2 秒以内（NFR5.1）で、`e2eTest` の値を記録するだけ（記録のみ）。本番・運用の中での判定は `Unverified`（持ち主は performance-validation・observability-setup・feedback-optimization） | `./gradlew e2eTest` の1回の実行（5 回、1回目と 2〜5 回目を分けて記録） |
| 次のページまでの時間（1節） | 同上。目標は 5 回すべて 1.5 秒以内（NFR5.2）。API の時間を含まない値として読み、この値で NFR5.2 を満たしたことにはしない。本番での判定は `Unverified`（持ち主は同上） | 同上 |
| 利用者の管理の API の時間 | U3 の目標（p95 1 秒、同時 10 件）のまま。U5 のために測り直さない | performance-validation の k6（U3 の持ち物）。運用の中の判定は、手元の監視を常に動かしていない間は `Unverified` |

- 測りは 120 の中だけで行い、配備したアプリでは測りません（E2E の一時の内部DB と WAR を相手にするため）。
- 手元の監視を常に動かしていない間は SLO の判定をせず、目標を緩めて満たしたことにはしません。運用の中での測り方は、配備先が決まったときに observability-setup・feedback-optimization で決めます（`project.md` の Testing Posture・Deployment）。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| 画面のログ | U5 のコードは `console` を呼ばない。画面部品のテストで5つの関数（`log`・`info`・`warn`・`error`・`debug`）を見張る（NFR3.1、`security-design.md` 6.1）。画面のログの送り先は作らない |
| サーバーのログ・トレース・監査 | U5 は足さない。管理の API のログ（409 `USER_ADMIN_BUSY` の WARN など）・トレース・監査の行は U3 の `monitoring-design.md` 4節・5節のとおり |
| ダッシュボード | U5 だけのパネルは足さない。管理の API のパネルは U3 の決定のとおり observability-setup で起動して確かめる |
| 測りの記録 | 添付 `user-admin-screen-ms`（`list`・`nextPage` の `first`・`rest`・`max`・`withinTarget`・`targetMs`・`apiTimeIncluded`・`note`）。数と決まった文だけで値を含まない。Build and Test が json の報告（`frontend/test-results/e2e-results.json`）から添付を復号して写し、`nextPage` は API の時間を含まないこと、`list` は差し替えの口の上乗せを含むことを書く。コード生成（B5）の記録にも写す |
| 失敗のときの手がかり | 110・120 が失敗したときだけ、値を伏せた記録を注記と添付 `user-admin-diagnostics` に残す（通った手順の題、要求のメソッドと道の型と状態コード、画面の問題の件数、画面の道の型、行の数）。trace は既定で残さない（`security-design.md` 3.6） |
| 報告の部品の出力 | 値は表示せず、値の種類（`signingKey`・`adminEmail`・`adminPassword`・`email`・`password`・`displayName`・`runTag`）とファイルの種類ごとの件数だけを出す（`security-design.md` 3.5 の 8） |
| 120 の検査の結果 | 組と状態ごとの成否・違反の件数・規則の名前・`missingRequiredRules`・横のはみ出しの有無・CSP の違反の件数・差し替えの口が受けた件数と打ち切った件数を、json の報告から Build and Test が写す |

## 5. 上流との差

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| 管理の API の観測 | NFR 設計は「画面の側に独自の指標を足さない」 | U3 の基盤の設計の指標と既存の警報（`ms-forbidden`・`ms-5xx-ratio`・`ms-error-logs`）で見えることを記録した | 既存の仕組みと同じ段の U3 の決定の記録。食い違いではない |
| 運用の中での判定 | NFR 要件は Build and Test で記録し関門にしない | 運用の判定は `Unverified` とし、持ち主の段を observability-setup・feedback-optimization と明記した | `project.md` の Testing Posture・Deployment で埋めた追加 |
| NFR5.1・NFR5.2 の読み方 | NFR 設計は 120 で測り記録する | 記録のみとし、`traceability.json` を `Deferred`（本番での判定は `Unverified`、持ち主の段を明記）にした。`nextPage` は API の時間を含まないことを明記した | 承認の場の決定 R-01（`cicd-pipeline.md` の「承認の場の決定」の節） |
