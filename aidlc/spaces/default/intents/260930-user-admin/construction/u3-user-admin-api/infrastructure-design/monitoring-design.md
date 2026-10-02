# Monitoring Design — U3 利用者の管理の API（u3-user-admin-api）

U3 の監視の設計です。承認済みの `construction/u3-user-admin-api/nfr-design/observability-design.md`（NFR5.9〜NFR5.11・NFR3.4・NFR9.3）を、既存の手元の監視の上でどう見るかを書きます。U3 は新しい指標・警報・ダッシュボードを足さず、`docker/monitoring/` を変えません（要点 6）。

手元の監視は grafana/otel-lgtm を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`）でリポジトリに置きます。外部エクスポートは既定で無効（`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED` の既定 false）で、警報の通知の先（contact point）は置いていません（`aidlc/spaces/default/memory/project.md` の Deployment、`team.md` の Deployment）。

指標と警報の名前・式・しきい値は、2026-10-02 に `docker/monitoring/provisioning/alerting/mastersmith.yaml` と `backend/src/main/resources/application.yaml` の実物を読んで書きました。手元の監視（OTLP で送る）での指標の名前は、既存の警報の式と同じ `http_server_requests_milliseconds_*` の形です（9節の M-D1）。`uri` のラベルの実際の値は observability-setup で起動して確かめます（8節）。

出典の略号は `infrastructure-specification.md` と同じ。

## 1. Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| `http_server_requests_milliseconds_bucket`・`_count`（`uri` が `/api/admin/users`・`/api/admin/users/{userId}/profile`・`/api/admin/users/{userId}/grant-admin`・`.../revoke-admin`・`.../suspend`・`.../resume`・`.../reset-login-failures` の見込み、`method`・`status` 別） | 既存の Spring MVC の観測（Micrometer の `http.server.requests`）。`application.yaml` の `management.metrics.distribution.slo` で 100・250・500・1000・2000・5000 ms のバケットを持つ。U3 は足さない | 7つの API とも p95 1000 ms（NFR5.1・NFR5.3・NFR5.4）。409 USER_ADMIN_BUSY だけは約 3 秒の例外（NFR5.6）。手元の監視では警報にしない | 一覧と操作の遅さと誤りの多さを見る（NFR5.9）。1000 ms はバケットの境界にあるため p95 を判定できる。`uri` は道の型で、利用者 ID と検索の文字を含まない |
| `hikaricp_connections_pending` | 既存（HikariCP） | 最大が 0 を超える状態が 1 分続くと `ms-pool-pending` | 5つの操作は確定の後の監査で2本目を借りる（NFR6.1）。借りる待ちは 5000 ms で時間切れになるため短い枯渇はこの指標では見えない |
| `/actuator/metrics` の `hikaricp.connections.timeout`（累計）・`hikaricp.connections.acquire`（借りるまでの待ちの最大） | 既存。負荷の試験の使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` で読む | NFR6.2 と NFR6.3 (A) は時間切れの累計 0。NFR6.3 (B) は待ちが出ること | 接続プールが尽きたかを累計と最大で判断する（`project.md` の Testing Posture）。配備したアプリの公開の範囲（health だけ）は変えない |
| `logback_events_total`（`level="error"`） | 既存 | 5 分の増加が 5 件を超えると `ms-error-logs` | 想定外の誤り（500）、監査の書き込みの失敗、既存の経路と書き込みの問い合わせの上限切れ（B4 の後も ERROR は1件出る）が数に入る |
| `process_uptime_milliseconds` | 既存 | 届かない状態が 5 分で `ms-app-absent` | アプリの停止。U3 は起動の手順を変えない |
| 監査の5つの種類の件数、`failure_reason` ごとの件数（とくに `LAST_ACTIVE_ADMIN`・`NOT_ADMIN`） | 内部DB の `audit_events`（README の「監査ログの確かめ方」の手順で、アプリを止めて複写を読み取りで開いて数える） | 負荷の試験では、流した5つの操作の成功の件数と一致（NFR6.2）。ふだんは閾値なし | 誰が誰の権限・状態を変えたか、最後の管理者の保護で拒否された操作を追う（NFR9.3） |

## 2. Alerts

新しい警報は足しません（NFR5.10）。既存の警報は、続けて起きる異常・数の多い異常のときにだけ U3 の失敗を拾います。1件・数件の失敗は警報では拾えず、監査の記録とアプリのログで見ます（7節）。どの警報も通知の先は無く、手元の Grafana の警報の一覧で見ます。重さ（Severity）は警報の決まりのラベル `severity` の値です。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-forbidden` 管理画面への拒否の増加 | `sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",status="403"}[1h]))` が 20 を超える（`for: 0s`）。U3 の確かめ直しの 403 と認可の入口の 403 を含む、すべての 403 を数える | 中 | 手元の Grafana の警報の一覧（通知の先なし） |
| `ms-5xx-ratio` 5xx の割合の増加 | 全体の要求に対する 5xx の割合が 0.01 を超える状態が 5 分（`for: 5m`）。U3 の想定外の誤りと、既存の経路・書き込みの問い合わせの上限切れの 500 を含む | 中 | 同上 |
| `ms-error-logs` ERROR のログの増加 | `sum(increase(logback_events_total{service_name="mastersmith",level="error"}[5m]))` が 5 を超える（`for: 0s`） | 中 | 同上 |
| `ms-audit-fail` 監査の書き込みの失敗 | Loki で「監査イベントの記録に失敗しました」が直近 1 時間に 1 件でもある（`for: 0s`）。手元の監視を起動しているときだけ見える | 中 | 同上 |
| `ms-audit-slow` 監査の書き込みの遅れ | Loki で「監査イベントの記録に時間がかかりました」が直近 1 日に 2 件を超える（`for: 0s`）。同上 | 低 | 同上 |
| `ms-pool-pending` コネクションプールの待ち | `max(hikaricp_connections_pending{service_name="mastersmith"})` が 0 を超える状態が 1 分（`for: 1m`）。短い枯渇では鳴らない | 低 | 同上 |
| `ms-app-absent` アプリの停止 | `absent(process_uptime_milliseconds{service_name="mastersmith"})` が 5 分（`for: 5m`） | 高 | 同上 |

- 409（USER_ADMIN_BUSY・USER_ADMIN_LAST_ADMIN など）は警報にしない（NFR5.10、`observability-requirements.md` の「選ばなかったもの」）。
- 7つの API の p95 の警報は足さない。既存の p95 の警報（`ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`）は道を1つずつ指定する式で、U3 の道を含まない。目標の確かめは performance-validation の k6 で行う。
- `ms-pool-pending` を時間切れの累計（`hikaricp_connections_timeout_total`）の増加を見る式に見直すことは、配備先が決まるまでの申し送りのまま（NFR5.10）。
- 既存のロックの警報 `ms-lock`（「アカウントをロックしました」の件数）は、失敗回数を戻す操作では増えない（戻す操作はロックを作らない）。

## 3. SLIs / SLOs

手元の監視を常に動かしていない間は、SLO を決めず判定を Unverified とします（NFR5.11、`project.md` の Deployment）。performance-validation と observability-setup の値を基準の値として記録し、配備先が決まったときに SLO と測り方を決め直します。目標を緩めて満たしたことにはしません。

| SLI | SLO target | Measurement window |
|---|---|---|
| GET `/api/admin/users` の p95（`http_server_requests_milliseconds_bucket`） | 1000 ms 以内（NFR5.1 の目標）。判定は Unverified、持ち主は performance-validation（k6 の値を正、サーバー側の指標は参考） | 配備先が決まったときに決める（手元では performance-validation の試験の間） |
| PUT `/api/admin/users/{userId}/profile` の p95 | 1000 ms 以内（NFR5.3）。判定は Unverified | 同上 |
| 5つの操作のそれぞれの p95（409 USER_ADMIN_BUSY を除かない） | 1000 ms 以内、目標の負荷で BUSY が 0 件（NFR5.4〜NFR5.6）。判定は Unverified | 同上 |
| 7つの API の 5xx でない割合 | 配備先が決まったときに決める | 同上 |
| 監査の書き込みの失敗の件数 | 配備先が決まったときに決める | 同上 |

## 4. Logs & Tracing

| 対象 | 設計 |
|---|---|
| アプリのログ | 既存のまま（1行1件の JSON、キーと値、`traceId` を含む、`docker compose logs app`）。ログの設定（`logback-spring.xml`・`logging.level`・`mastersmith.trace.*`）は変えない |
| 409 USER_ADMIN_BUSY | 排他の口の repository の WARN（排他の種類 例 `ADMIN_ROWS`・`USER_ROW`・`LOGIN_ATTEMPT_ROW` と `exceptionClass`）と、`GlobalExceptionHandler` の WARN（code）の2行。同じトレースID で結び付く（`security-design.md` 12節の SD-5） |
| 既存の E1〜E4 の上限切れ（B4 の後） | repository の WARN（排他の種類とクラスの名前）と、値を含まない例外の ERROR（スタックトレース付き）の2行。応答は今までどおり 500 |
| 書き込みの問い合わせの上限切れ（B4 の後、`infrastructure-specification.md` 6.2） | `GlobalExceptionHandler` の ERROR は今までどおり1件出るが、排他の失敗の連なりを持つ例外は原因をつながず型の名前だけになる。TRACE のときの `TraceAspect` の EXCEPTION の行も同じ。`MASTERSMITH_TRACE_*` の値によらない |
| ほかの 4xx・想定外の誤り | 既存の `GlobalExceptionHandler` の境界で、4xx は WARN 以下（スタックトレースなし）、5xx は ERROR（スタックトレース付き） |
| 成功と業務の拒否 | 新しいログを出さない。監査ログで見る（監査をアプリのログで代用しない） |
| 出してはいけない値 | 検索の文字・メールアドレス・氏名・トークン・ハッシュ値・失敗回数、排他されていた行の値と `MVStoreException` の文。確かめは `UserAdminSecretLeakIT` と既存の経路・書き込みの問い合わせの漏えいのテスト（TRACE と INFO の両方） |
| ログの集め方 | 手元の監視を起動したときだけ、既存の設定で Loki に送る。外部エクスポートは既定で無効のまま |
| トレース | 既存の Micrometer Tracing のまま（`management.tracing.sampling.probability` の既定 1.0、W3C の伝え方）。新しいスパンと属性は足さない。URL の問い合わせの部分（q）はトレースの属性に入らない（BR7.4） |
| 監査とログのつながり | 監査の行の `trace_id` と、同じ要求のログの `traceId` が一致する |

## 5. 監査ログ（NFR9.3）

監査の種類と理由は、今のコードには無く B4 で足します。2026-10-02 の時点の `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` は `LOGIN_SUCCEEDED` から招待・登録までの種類だけを持ち、`AuditFailureReason.java` の既存の値のうち U3 が使うのは `USER_NOT_FOUND`・`NOT_ADMIN` だけです。手順書・スモークテスト・ログの問い合わせに名前を書くときは、B4 の後の定義で確かめてから書きます（`project.md` の学び）。

| 項目 | 値 |
|---|---|
| 種類（`event_type`、B4 で足す） | `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET` |
| 結果（`result`） | 既存の `SUCCESS`・`FAILURE` |
| 理由（`failure_reason`） | 既存の `USER_NOT_FOUND`・`NOT_ADMIN`、B4 で足す `SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN` |
| 操作した人・対象 | `actor_user_id` は認証の主体、`target_user_id` は要求の利用者 ID（対象がいないときもそのまま） |
| 残さないもの | 一覧・氏名と言語の変更・BUSY・入力の誤り・認可の入口の 401 と 403（403 は既存のアクセスの拒否だけ）。メールアドレス・氏名・検索の文字・失敗回数・ハッシュ値 |
| 見る手順 | README の「監査ログの確かめ方」（アプリを止めてボリュームを複写し、複写を読み取りで開く）のまま。複写は `~/.mastersmith-backup/`（権限 700）に置き、使い終えたら消す |

## 6. ダッシュボード

既存のダッシュボード「MasterSmith の概要」は変えません（NFR5.10）。U3 の API は、既存のパネルのうち全体の値（「要求の数（1 分あたり）」「5xx の割合（5 分）」「ERROR のログ（5 分あたり）」「コネクションプールの待ち」「403 の件数（1 時間）」「監査の書き込みの失敗（1 時間）」「監査の書き込みの遅れ（1 日）」）に含まれます。道ごとのパネル（ログイン・トークンの更新・確認用 API・DSL・招待と登録・メールの送信）に U3 の道は入りません。管理の API の専用のパネルは、配備先が決まったときに SLI と合わせて考えます。

## 7. 何を見れば分かるか（運用）

| 知りたいこと | 見るもの |
|---|---|
| 一覧・操作が遅い、誤りが多い | `http_server_requests_milliseconds_*` の `/api/admin/users` の道ごとの応答時間と状態コード |
| 誰が誰の印を付けた・外した、利用を止めた・解いた、失敗回数を戻した | 監査ログの5つの種類を `actor_user_id`・`target_user_id` で絞る |
| 最後の管理者の保護で拒否された操作 | 監査ログの `failure_reason` が `LAST_ACTIVE_ADMIN` |
| 排他の待ちが上限に届いた | アプリのログの WARN の code `USER_ADMIN_BUSY` と、同じトレースID の排他の口の WARN（監査には残らない） |
| 既存の経路・書き込みの問い合わせの上限切れ | アプリのログの ERROR と、同じトレースID の WARN（排他の種類とクラスの名前）。数が多ければ `ms-error-logs`・`ms-5xx-ratio` |
| 権限を失った管理者の操作・管理者でない利用者の呼び出し | 1件ずつは監査の `NOT_ADMIN` と既存のアクセスの拒否。直近 1 時間に 403 が 20 件を超えたときだけ `ms-forbidden` |
| 監査の書き込みが失敗した・遅い | アプリのログの ERROR・WARN。手元の監視を起動しているときは `ms-audit-fail`・`ms-audit-slow` |
| 接続プールが尽きかけている | 待ちが 1 分続いたときだけ `ms-pool-pending`。短い枯渇は、2本目の時間切れによる監査の記録の失敗の ERROR で見る |

- 警報が鳴ったときの対応の手順（BUSY の WARN・最後の管理者の拒否の見方など）を README の「警報と対応の手順」に足すかは incident-response の段で決める。

## 8. observability-setup で確かめること

| 確かめること | やり方 |
|---|---|
| 7つの API の `uri`・`method`・`status` のラベルの実際の名前と値、`le` のバケット | 手元の監視を起動し、7つの API に要求を指標の送信の周期（`management.otlp.metrics.export.step` の 60s）を複数またいでくり返し送ってから、Grafana で `http_server_requests_milliseconds_count`・`_bucket` を引く（`project.md` の Corrections・Deployment の学び）。5つの操作は監査に残り、氏名の変更はデータを変えるため、送る前に依頼者に伝える |
| 2節で「拾う」と書いた警報が実際に数えて鳴ること | 状態コードとログのレベルに加えて、しきい値を超えたときに鳴ることまで確かめる（NFR5.10）。既存の警報の式をすべて流し直す |
| 5節の監査の種類と理由の名前 | B4 の後の `AuditEventType`・`AuditFailureReason` の定義と、複写を読み取りで開いた `audit_events` で確かめる |
| 基準の値の記録 | 配備の直後・監視の確かめの時点の値を、performance-validation の値と並べて記録する（NFR5.11） |

## 9. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| M-D1 | `nfr-design/observability-design.md` 1節 | 指標は `http.server.requests`（`uri`・`status`・`method` のラベル） | 手元の監視での実際の名前 `http_server_requests_milliseconds_*` で書いた。`uri` の値は見込みで、observability-setup で起動して確かめる | 既存の警報の式と同じ形にそろえた（前の Intent の基盤の設計と同じ扱い）。承認済みの文書は書き換えない |
| M-D2 | `nfr-design/observability-design.md` 3節 | 上限切れのログは、U3 の排他の口と既存の E1〜E4 の WARN と ERROR だけを挙げる | 書き込みの問い合わせの上限切れの ERROR と TRACE の出力も、B4 の中央の手当てで型の名前だけになることを4節に足した | Q2 A（`infrastructure-specification.md` 10節の I-D1）。ERROR の件数は変わらないため、`ms-error-logs` の数え方は変わらない |
