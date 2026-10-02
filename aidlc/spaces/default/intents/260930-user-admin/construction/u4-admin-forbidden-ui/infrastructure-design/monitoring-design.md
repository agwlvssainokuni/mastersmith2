# Monitoring Design — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 の監視の設計です。U4 は画面（ui）の単位で、サーバーの側の資源を持たないため、画面の側に独自の指標・ログの送り先・画面の時間の計測と送信（RUM）・外部への送信を足しません（`performance-design.md` 4節・6節）。画面の 403 と、その後の読み直しは、サーバーの既存の指標・警報・ログ・監査で見えるため、U4 のために警報とパネルを足しません（要点 3）。手元の監視は grafana/otel-lgtm を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 3）
- 上流: `construction/u4-admin-forbidden-ui/nfr-design/performance-design.md`（3.2・4節・6節）・`security-design.md`（8節の R3・R4）、`construction/u4-admin-forbidden-ui/nfr-requirements/performance-requirements.md`（NFR5.1・NFR9.3）（どれも `aidlc/spaces/default/intents/260930-user-admin/` の下）
- 既にある仕組み（正とする、読むだけ）: `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-forbidden`・`ms-refresh-p95`）・`docker/monitoring/dashboards/mastersmith-overview.json`・`backend/src/main/resources/application.yaml`（`http.server.requests` の境界 100ms〜5000ms）・`backend/src/main/java/cherry/mastersmith/access/web/AdminAccessDeniedHandler.java`

## 1. Metrics & KPIs

U4 は新しい指標を足しません。次の既存の指標で U4 の動きが見えます。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 403 の件数（`http_server_requests_milliseconds_count`、`status="403"`） | 既存の Spring MVC の観測。ダッシュボードの「403 の件数（1 時間）」 | 1 時間に 20 件を超えたら警報 `ms-forbidden`（既存） | 画面が S6 に置き換わるきっかけ（管理の API の 403）を含む件数。印を外された利用者の操作や、管理者でない人の試みが見える。ただし式は `status="403"` のすべての要求を数えるため、管理者のみのパス以外の 403 も含む（手元の E2E の実要求の 403 も入る） |
| トークンの更新の API の時間（`http_server_requests_milliseconds_bucket`、`uri="/api/auth/session/refresh"`） | 既存の観測。ダッシュボードの「トークンの更新の API」 | p95 1 秒（既存の警報 `ms-refresh-p95`、境界 1000ms はバケットにある） | 403 の後の読み直しの時間そのもの（NFR5.1）。管理のメニューが消えるまでの待ちはこの時間で決まる |
| トークンの更新の API の要求の数 | 同上（ダッシュボードの「要求の数（1 分あたり）」） | 置かない | U4 が増やすのは 403 を受けた URL ごとに1回だけで、負荷の見積もりは変わらない（`performance-design.md` 3.2） |
| 初回の JavaScript の大きさ | 既存の `frontendBundleSize`（`./gradlew verify` の段 9） | gzip で 500KB を超えたら警告だけ（NFR9.4） | U4 は骨組みの部品のため初回の読み込みに入る。前後の値をコード生成で記録する |

## 2. Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-forbidden`「管理画面への拒否の増加」（既存、変えない） | 403 が 1 時間に 20 件を超えた | 中 | 手元の Grafana の表示だけ。通知の先は配備先が決まるまで置かない（`project.md` の Deployment） |
| `ms-refresh-p95`「トークンの更新の応答の遅れ」（既存、変えない） | 更新の API の p95 が 1 秒を超えた状態が 5 分続いた | 低 | 同上 |
| U4 だけの警報 | 足さない | — | — |

- `ms-forbidden` が鳴ったときは、警報の説明（`summary`）のとおり監査ログで利用者と接続元 IP を確かめます。監査の「アクセスの拒否」の行（4節）の数は、管理者のみの API の 403 の回数の目安です。S6 を見た利用者の数ではありません。ForbiddenByRoute の S6 は API を呼ばないため行が残らず、1つの画面が複数の管理の API を呼べば1人で複数行になります。
- `ms-forbidden`・ダッシュボードの「403 の件数（1 時間）」・`AdminAccessDeniedHandler` の WARN（`code=ACCESS_DENIED`）は、管理者のみのパス以外も含む `status="403"` のすべてを数えます（式としきい値は変えません）。
- 更新の API が目標を超えたときも、目標を緩めて「満たした」ことにはしません（`project.md` の Testing Posture）。

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 403 から S6 の見出しが見えるまで・管理のメニューが消えるまでの画面の時間 | 置かない（NFR9.3）。S6 は待ちなしで出て、測る値はほぼ更新の API の時間と同じになるため | 測らない（130 でも測らず記録もしない） |
| トークンの更新の API の時間（読み直し） | 既存の目標 p95 1 秒（同時 10 件、NFR5.1）。U4 のために測り直さない | performance-validation の既存の k6 の更新の場面（U1 の NFR5 の持ち物）。運用の中の判定は、手元の監視を常に動かしていない間は `Unverified` |

- 画面の時間に目標を置く必要が出たときは、目標を緩めた形にせず、依頼者に諮って新しい要件として決めます（NFR9.3）。
- 運用の中での測り方は、配備先が決まったときに observability-setup・feedback-optimization で決めます（`project.md` の Deployment）。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| 画面のログ | U4 の部品と関数は `console` を呼ばない。画面部品のテストで5つの関数（`log`・`info`・`warn`・`error`・`debug`）が呼ばれないことを確かめる（NFR3.1）。画面のログの送り先は作らない |
| サーバーのログ | 既存の `AdminAccessDeniedHandler` が 403 ごとに WARN を1行出す（キーと値は `code=ACCESS_DENIED` だけで、メールアドレス・パス・トークンを出さない）。この WARN は管理者のみのパス以外の 403 にも出る。U4 は足さない |
| 監査 | 既存の「アクセスの拒否」の出来事（`AdminAccessDeniedEvent`、`eventType`＝`ACCESS_DENIED`・`result`＝`FAILURE`・`requestPath`・`traceId` など）が、管理者のみのパスの 403 ごとに残る。U4 は監査を書かない |
| トレース | 既存の Micrometer Tracing のまま。403 の WARN と監査の行は `traceId` で結び付く。U4 は画面の側にトレースを足さない |
| ダッシュボード | U4 だけのパネルは足さない。「403 の件数（1 時間）」と「トークンの更新の API」の既存のパネルで見る |
| 見えないもの（受け入れ済み） | 管理者でない利用者が管理の画面の URL を開いたとき（ForbiddenByRoute）は API を呼ばないため、サーバーには何も残らない。残る危険 R3・R4（`security-design.md` 8節）として受け入れ、観測は足さない。印が古いまま S6 が出る R4 も同じく見えない |
| 検査の結果 | 130 の組ごとの成否・違反の件数・規則の名前・はみ出しの有無・Avatar の文字の数・除いたコンソールの表示の件数を、json の報告（`frontend/test-results/e2e-results.json`）から Build and Test が写す。時間の値は無い（`cicd-pipeline.md` 4.4） |

## 5. 上流との差

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| 403 の観測 | NFR 設計は「観測の指標も足さない（U4 は観測の設計の対象外）」 | 既存の `ms-forbidden`・パネル・WARN・監査で見えることを、ソース（警報の決まり・`AdminAccessDeniedHandler`）で確かめて記録した | 既存の仕組みの記録。食い違いではない |
| 更新の API の警報 | NFR5.1 は既存の目標を当てるとだけ書く | `ms-refresh-p95` の境界 1000ms が `http.server.requests` のバケットにあり、警報が働く形であることを確かめて記録した | 既存の仕組みの確かめ（`project.md` の学び: p95 の式の前にバケットを確かめる）。食い違いではない |
| ForbiddenByRoute の観測 | 受け入れ済みの残る危険 R3・R4 | 観測を足さないことを明記した | 要点 3 のとおり |
