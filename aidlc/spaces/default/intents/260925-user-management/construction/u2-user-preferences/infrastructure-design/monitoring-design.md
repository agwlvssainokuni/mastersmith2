# Monitoring Design — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の監視の設計です。承認済みの `construction/u2-user-preferences/nfr-design/observability-design.md`（NFR6.8・NFR6.9・NFR2.4・NFR9.5）を、既存の手元の監視の上でどう見るかを書きます。U2 は新しい指標・警報・ダッシュボードを足しません（要点 15）。

手元の監視は grafana/otel-lgtm を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`）でリポジトリに置きます。外部エクスポートは既定で無効で、警報の通知の先は置きません（`aidlc/spaces/default/memory/project.md` の Deployment、`team.md` の Deployment）。

出典の略号は `infrastructure-specification.md` と同じ。指標の名前は、手元の監視（OTLP で送る）での実際の名前（既存の式と同じ `http_server_requests_milliseconds_*` など）で書きます（8節の M-D1）。ラベルの値は observability-setup で起動して確かめます（7節）。

## 1. Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| `http_server_requests_milliseconds_bucket`・`_count`（`uri` が `/api/me/preferences`・`/api/me/password`、`method`・`status` 別） | 既存の Spring MVC の観測（Micrometer）。U2 は足さない | p95 はプリファレンスの取得・保存 1 秒、パスワードの変更の成功（status 204）2 秒、誤り（status 400）1 秒（NFR6.1〜NFR6.4）。手元の監視では警報にしない | 3本の API の遅さと誤りの多さを見る（NFR6.8）。`uri` は利用者の識別を含まない |
| 同上（`uri` が `/api/auth/login`・`/api/auth/session/refresh`） | 既存 | p95 1 秒（既存の警報の値） | 応答の広げ（NFR6.5）の後も前の目標を保つことを見る |
| `hikaricp_connections_pending` | 既存（HikariCP） | 0 を超える状態が 1 分続くと既存の警報 | 成功のパスワードの変更は2本目の接続を借りる（NFR5.2） |
| `/actuator/metrics` の `hikaricp.connections.timeout`（累計）・`hikaricp.connections.acquire`（借りるまでの待ちの最大） | 既存。負荷の試験の使い捨ての環境にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` で `/actuator/metrics` から読む | 時間切れの累計 0、待ちの最大が 5 秒より十分小さい（NFR5.2） | プールが尽きたかを、数秒ごとの使用中の数ではなく累計と最大で判断する（`project.md` の Testing Posture）。配備したアプリの公開の範囲は変えない |
| `logback_events_total`（level error） | 既存 | 5 分で 5 件を超えると既存の警報 | 想定外の誤り（5xx）と監査の書き込みの失敗が ERROR に出る（NFR2.4・NFR9.4） |
| 監査の PASSWORD_CHANGED の件数（SUCCESS・FAILURE） | 内部DB の audit_events（複写を読み取りで開いて数える） | 負荷の試験では流した成功の件数と一致。ふだんは閾値なし（6節） | 監査の欠けの確かめ（NFR5.2）と、今のパスワードの総当たりの見つけ方（残る危険 R1） |

## 2. Alerts

新しい警報は足しません（NFR6.9）。U2 の3本と応答の広げは、次の既存の警報で見えます。どれも通知の先は無く、手元の監視の警報の一覧で見ます。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-5xx-ratio` 5xx の割合の増加 | 全体の 5xx の割合が 1% を超える状態が 5 分（`/api/me/` を含む） | 既存の値のまま | 手元の Grafana の警報の一覧（通知の先なし） |
| `ms-error-logs` ERROR のログの増加 | ERROR のログが 5 分で 5 件を超える | 既存の値のまま | 同上 |
| `ms-audit-fail` 監査の書き込みの失敗 | 「監査イベントの記録に失敗しました」が 1 時間に 0 件を超える（PASSWORD_CHANGED の記録の失敗も含む） | 既存の値のまま | 同上 |
| `ms-audit-slow` 監査の書き込みの遅れ | 「監査イベントの記録に時間がかかりました」が 1 日に 2 件を超える | 既存の値のまま | 同上 |
| `ms-login-p95` ログインの応答の遅れ | `/api/auth/login` の p95 が 1000 ms を超える状態が 5 分 | 既存の値のまま（応答の広げの後もそのまま使う） | 同上 |
| `ms-refresh-p95` トークンの更新の応答の遅れ | `/api/auth/session/refresh` の p95 が 1000 ms を超える状態が 5 分 | 既存の値のまま | 同上 |
| `ms-pool-pending` コネクションプールの待ち | `hikaricp_connections_pending` の最大が 0 を超える状態が 1 分 | 既存の値のまま | 同上 |
| `ms-app-absent` アプリの停止 | 指標が 5 分届かない（V7 の失敗で起動が止まったときも含む） | 既存の値のまま | 同上 |

- `/api/me/` の p95 の警報は足しません。承認済みの設計（NFR6.9）のとおりで、目標の確かめは performance-validation の k6 で行います。
- 今のパスワードの誤りが続くことの警報は作りません（NFR4.5、残る危険 R1）。見つけ方は6節の問い合わせです。

## 3. SLIs / SLOs

手元の監視を常に動かしていない間は、SLO の判定をせず Unverified とします（`project.md` の Deployment）。U2 の目標は performance-validation の値を基準の値として記録し、配備先が決まったら次の SLI で測ります。

| SLI | SLO target | Measurement window |
|---|---|---|
| GET・PUT `/api/me/preferences` の p95（`http_server_requests_milliseconds_bucket`） | 1 秒以内（NFR6.1・NFR6.2）。判定は Unverified | 配備先が決まったときに決める（手元では performance-validation の試験の間） |
| POST `/api/me/password` の status 204 の p95 | 2 秒以内（NFR6.3）。判定は Unverified | 同上 |
| POST `/api/me/password` の status 400 の p95 | 1 秒以内（NFR6.4）。判定は Unverified | 同上 |
| `/api/auth/login`・`/api/auth/session/refresh` の p95 | 1 秒以内（NFR6.5、前の Intent の目標）。判定は Unverified | 同上。既存のダッシュボードの「仮の目標（SLO）」の行のまま |

## 4. Logs & Tracing

| 対象 | 設計 |
|---|---|
| アプリのログ | 既存のまま（1行1件の JSON、キーと値、`traceId` を含む、`docker compose logs app`）。U2 の 4xx は既存の `GlobalExceptionHandler` の境界で WARN 1回（スタックトレースなし、`fieldErrors` の中身は載せない）、5xx は ERROR 1回（スタックトレース付き）。成功の場面では新しいログを出さない（NFR2.4） |
| 監査の失敗の ERROR | 既存の「監査イベントの記録に失敗しました」の項目に targetUserId・targetInvitationId が増える。パスワード・ハッシュ・メールアドレスは載せない（`observability-design.md` 2節） |
| ログの集め方 | 手元の監視を起動したときだけ、既存の設定で Loki に送る。外部エクスポートは既定で無効のまま。外へ送るときの伏せ字（メールアドレス・接続元 IP・User-Agent）は既存の設定のまま |
| トレース | 既存の Micrometer Tracing のまま。U2 は新しい span を作らず、属性に利用者の値を足さない。bcrypt の時間は HTTP の span に含まれる（`observability-design.md` 4節） |
| 監査とログのつながり | 監査の行の trace_id と、同じ要求のログの `traceId` が一致する。監査の行から 4xx の WARN へたどれる |

## 5. ダッシュボード

既存のダッシュボード「MasterSmith の概要」は変えません（NFR6.9）。U2 の3本は、既存のパネル（要求の数・5xx の割合・ERROR のログ・コネクションプールの待ち・監査の書き込みの失敗）の全体の値に含まれます。`/api/me/` 専用の応答時間のパネルは足しません。

## 6. 監査ログから数えるもの（今のパスワードの総当たり、残る危険 R1）

`observability-design.md` 3節の問い合わせ（PASSWORD_CHANGED・FAILURE・CURRENT_PASSWORD_MISMATCH を actor_user_id ごとに数える）を、README の「監査ログの確かめ方」（アプリを止めてボリュームを複写し、複写を読み取りで開く）の手順に並べます（要点 18）。

- 定期の実行や警報にはしない（読み取りだけの調べ物）。
- 結果は利用者 ID と件数・時刻だけを見る。複写は `~/.mastersmith-backup/`（権限 700）に置き、使い終えたら消す（`infrastructure-specification.md` 7節）。
- 列の名前は observability-setup で実際に流して確かめる。

## 7. observability-setup で確かめること

| 確かめること | やり方 |
|---|---|
| 3本の API の `uri`・`method`・`status` のラベルの実際の名前と値 | 手元の監視を起動し、3本を呼んでから Grafana で `http_server_requests_milliseconds_count` を引く（`project.md` の Corrections「式は起動して確かめる」）。呼ぶ操作が監査ログに残る場合（パスワードの変更）は、送る前に依頼者に伝える |
| 既存の警報の式が応答の広げの後も動くこと（`ms-login-p95`・`ms-refresh-p95`） | 既存の警報の式をすべて流し直す |
| 6節の問い合わせの列の名前 | 複写を読み取りで開いて流す |

## 8. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| M-D1 | `nfr-design/observability-design.md` 1節 | 「Prometheus では `http_server_requests_seconds_*`」 | 手元の監視（OTLP で送る）での実際の名前は `http_server_requests_milliseconds_*` で、既存のダッシュボードと警報の式もこの名前。この文書は実際の名前で書く | 承認済みの文書は書き換えない。どちらも同じ `http.server.requests` の観測で、送り方による名前の違い。observability-setup で起動して確かめる |
| M-D2 | 要点 15（質問の文書） | 既存の警報として `ms-5xx-ratio`・`ms-error-logs`・`ms-audit-fail`・`ms-login-p95` を挙げた | 同じく U2 に関わる既存の警報 `ms-audit-slow`・`ms-refresh-p95`・`ms-pool-pending`・`ms-app-absent` も一覧に加えた（2節） | 既存の警報の決まりのファイルを読み直して加えた。警報を足す・変えるものではない |
