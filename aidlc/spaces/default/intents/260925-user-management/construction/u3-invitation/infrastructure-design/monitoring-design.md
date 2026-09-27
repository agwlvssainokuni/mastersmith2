# Monitoring Design — U3 招待と登録の完了（u3-invitation）

U3 の監視の設計です。承認済みの `aidlc/spaces/default/intents/260925-user-management/construction/u3-invitation/nfr-design/observability-design.md`（NFR6.8・NFR6.9・NFR2.3・NFR9.5）を、既存の手元の監視の上でどう見るかを書きます。U3 は新しい指標・警報・ダッシュボードを足しません（要点 16）。

手元の監視は grafana/otel-lgtm を compose の profile `monitoring` で見たいときだけ起動し、ダッシュボードと警報の決まりはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`）でリポジトリに置きます。外部エクスポートは既定で無効で、警報の通知の先は置きません（`aidlc/spaces/default/memory/project.md` の Deployment、`team.md` の Deployment）。

出典の略号は `infrastructure-specification.md` と同じ。指標の名前は、手元の監視（OTLP で送る）での実際の名前（既存の式と同じ `http_server_requests_milliseconds_*` など）で書きます（9節の M-D1、U2 の基盤の設計と同じ扱い）。ラベルの値は observability-setup で起動して確かめます（8節）。

## 1. Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| `http_server_requests_milliseconds_bucket`・`_count`（`uri` が `/api/admin/invitations`・`/api/admin/invitations/{invitationId}/resend`・`/api/admin/invitations/{invitationId}/cancel`、`method`・`status` 別） | 既存の Spring MVC の観測（Micrometer）。U3 は足さない | p95 は招待・送り直し 5 秒（NFR6.1）、一覧・取り消し 1 秒（NFR6.3）。手元の監視では警報にしない | 招待の管理の遅さと誤りの多さを見る（NFR6.8）。`uri` は道の型で、招待の ID・トークン・メールアドレスを含まない |
| 同上（`uri` が `/api/registration/verify`・`/api/registration/complete`） | 既存 | p95 1 秒（NFR6.3〜NFR6.5）。登録の完了は status 204 と 400・404 を分けて見る | 公開の2つの API の遅さと拒否の多さを見る。トークンは要求の本文で受け、道に置かない |
| `mastersmith_mail_send_milliseconds_*`（U1 の Observation `mastersmith.mail.send`、タグ `mail.template` が `invitation`・`mail.outcome`・`mail.failure.kind`） | U1 が足す送信の観測。U3 は足さない | 閾値なし（U1 の時間切れ 3 秒が上限の目安） | 招待・送り直しの遅さのうち送信の分を分ける。失敗の種類の数を見る。実際の名前は observability-setup で確かめる（8節） |
| `hikaricp_connections_pending` | 既存（HikariCP） | 0 を超える状態が 1 分続くと既存の警報 | 招待・送り直し・取り消し・登録の完了の成功は2本目の接続を借りる（NFR5.3・NFR5.4） |
| `/actuator/metrics` の `hikaricp.connections.timeout`（累計）・`hikaricp.connections.acquire`（借りるまでの待ちの最大） | 既存。負荷の試験の使い捨ての環境にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` で読む | 時間切れの累計 0、待ちの最大が 5 秒より十分小さい（NFR5.3） | プールが尽きたかを累計と最大で判断する（`project.md` の Testing Posture）。配備したアプリの公開の範囲は変えない |
| `logback_events_total`（level error） | 既存 | 5 分で 5 件を超えると既存の警報 | 想定外の誤り（行の排他の時間切れ・結果の記録の失敗・送信の入口の確かめ）と、定期の削除・監査の書き込みの失敗が ERROR に出る |
| 招待中の数・送信の結果 FAILED の数 | 招待の一覧の API（件数と sendResult）。指標にしない | 閾値なし | 送れていない招待を管理者が見つけて送り直す（NFR6.9、`observability-design.md` 1節） |
| 監査の招待の5つの種類の件数、REGISTRATION_FAILED の failure_reason・source_ip ごとの件数 | 内部DB の `audit_events`（複写を読み取りで開いて数える、6節） | 負荷の試験では流した成功の件数と一致。ふだんは閾値なし | 監査の欠けの確かめ（NFR5.3）と、誤ったトークンの総当たりの見つけ方（残る危険 R1） |

## 2. Alerts

新しい警報は足しません（NFR6.9）。U3 の API と処理は、次の既存の警報で見えます。どれも通知の先は無く、手元の監視の警報の一覧で見ます。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-5xx-ratio` 5xx の割合の増加 | 全体の 5xx の割合が既存の値を超える（招待・登録の API を含む） | 既存の値のまま | 手元の Grafana の警報の一覧（通知の先なし） |
| `ms-error-logs` ERROR のログの増加 | ERROR のログが 5 分で 5 件を超える（定期の削除の失敗・想定外の誤りを含む） | 既存の値のまま | 同上 |
| `ms-audit-fail` 監査の書き込みの失敗 | 「監査イベントの記録に失敗しました」が出る（招待の5つの出来事の記録の失敗も含む） | 既存の値のまま | 同上 |
| `ms-audit-slow` 監査の書き込みの遅れ | 「監査イベントの記録に時間がかかりました」が既存の値を超える | 既存の値のまま | 同上 |
| `ms-pool-pending` コネクションプールの待ち | `hikaricp_connections_pending` の最大が 0 を超える状態が 1 分 | 既存の値のまま | 同上 |
| `ms-origin` 送り元の不一致の多発 | 「要求の送り元が自分の配信元と一致しないため拒否しました」が 1 時間に 10 件を超える | 既存の値のまま（低） | 同上。配備した環境に `MASTERSMITH_WEB_BASE_URL` を入れた後は、`http://127.0.0.1:8080/` から開いたログインでも増える（`infrastructure-specification.md` 3.1）。警報の要約の「ベースURLの設定の誤り」の場合に当たる |
| `ms-app-absent` アプリの停止 | 指標が届かない（V8 の失敗で起動が止まったときも含む） | 既存の値のまま | 同上 |

- 招待・登録の API の p95 の警報は足しません。目標の確かめは performance-validation の k6 で行います。
- 送信の失敗（sendResult FAILED）の警報は作りません。U1 の WARN と U3 の INFO（4節）、招待の一覧で見つけます。
- 招待の定期の削除の失敗は、既存の `ms-cleanup-fail`（リフレッシュトークンの削除の失敗の文言だけを数える）には当たりません。ERROR のログ（`ms-error-logs` は 5 分で 5 件を超えたとき）と、次の回の INFO の消した件数で見ます（9節の M-D2）。
- 誤ったトークンの登録の完了が増えることの警報は作りません（NFR4.5、R1）。見つけ方は6節の問い合わせです。

## 3. SLIs / SLOs

手元の監視を常に動かしていない間は、SLO の判定をせず Unverified とします（`project.md` の Deployment）。U3 の目標は performance-validation の値を基準の値として記録し、配備先が決まったら次の SLI で測り、あわせて R1 の数え上げを警報にするかを考えます（`observability-design.md` 5節）。

| SLI | SLO target | Measurement window |
|---|---|---|
| POST `/api/admin/invitations`（201）・`.../resend`（200）の p95（`http_server_requests_milliseconds_bucket`） | 5 秒以内（NFR6.1）。判定は Unverified | 配備先が決まったときに決める（手元では performance-validation の試験の間） |
| GET `/api/admin/invitations`・POST `.../cancel`・POST `/api/registration/verify` の p95 | 1 秒以内（NFR6.3）。判定は Unverified | 同上 |
| POST `/api/registration/complete` の status 204 の p95 | 1 秒以内（NFR6.4）。判定は Unverified | 同上 |
| POST `/api/registration/complete` の status 400・404 の p95 | 1 秒以内（NFR6.5、BR7.4 の経路は対象外）。判定は Unverified | 同上 |
| 招待・登録の完了の成功の割合（5xx でない割合） | 配備先が決まったときに決める | 同上 |

## 4. Logs & Tracing

| 対象 | 設計 |
|---|---|
| アプリのログ | 既存のまま（1行1件の JSON、キーと値、`traceId` を含む、`docker compose logs app`）。ログの設定（`logback-spring.xml`）は変えない（NFR 設計の Q5 A） |
| 送信の失敗 | WARN は U1 の1件だけ（templateId・language・failureKind・exceptionType）。U3 は結果を記録した後、失敗のときだけ INFO を1件（`invitationId`・`operation`（INVITE・RESEND）・`failureKind`）。2つは同じ要求のトレースIDでつながる（NFR2.3） |
| 起動のとき | 招待の使える設定の INFO 1件（enabled と unavailableReasons）、ベース URL の形の誤りは項目の名前だけの WARN 1件（値は出さない、BR1.3） |
| 想定内・想定外の誤り | 既存の `GlobalExceptionHandler` の境界で、4xx は WARN 1回（スタックトレースなし）、5xx は ERROR 1回（スタックトレース付き）。`fieldErrors` の中身・招待のメールアドレスを載せない |
| 定期の削除 | 消した件数を INFO、失敗は ERROR（スタックトレース付き） |
| 出してはいけない値 | トークン・ハッシュ・招待の URL・メールアドレス・パスワード・SMTP の応答（`security-design.md` 5節・8節）。確かめは `InvitationSecretLeakIT` と、配備の後・負荷の試験の後のログの件数の確かめ（`infrastructure-specification.md` 7節） |
| ログの集め方 | 手元の監視を起動したときだけ、既存の設定で Loki に送る。外部エクスポートは既定で無効のまま |
| トレース | 既存の Micrometer Tracing のまま。U3 は新しい span を作らない。U1 の送信の span は招待・送り直しの要求の span の子になる。属性に宛先・URL・トークンを持たない |
| 監査とログのつながり | 監査の行の trace_id と、同じ要求のログの `traceId` が一致する |

## 5. ダッシュボード

既存のダッシュボード「MasterSmith の概要」は変えません（NFR6.9）。U3 の API は既存のパネル（要求の数・5xx の割合・ERROR のログ・コネクションプールの待ち・監査の書き込みの失敗）の全体の値に含まれます。招待・登録の専用のパネルと送信のパネルは足さず、U1 の送信の行の扱いと合わせて observability-setup に引き継ぎます（NFR 設計の承認の場の U3 R-02 の引き継ぎ）。

## 6. 監査ログから数えるもの（誤ったトークンの総当たり、残る危険 R1）

`observability-design.md` 3.1 の問い合わせ（REGISTRATION_FAILED を failure_reason・source_ip ごとに数える）を、README の「監査ログの確かめ方」（アプリを止めてボリュームを複写し、複写を読み取りで開く）の手順に、U2 の問い合わせと並べて置きます（要点 18）。

- 定期の実行や警報にはしない（読み取りだけの調べ物）。
- とくに `INVITATION_NOT_FOUND` が1つの送り元から多いときは総当たりの疑いとする。
- 結果は理由・送り元・件数・時刻だけを見る。複写は `~/.mastersmith-backup/`（権限 700）に置き、使い終えたら消す。
- 列の名前は observability-setup で実際に流して確かめる。

## 7. 招待の送信の見方（運用）

| 知りたいこと | 見るもの |
|---|---|
| 招待メールが送れていない | 招待の一覧の sendResult FAILED（PENDING を含む）、U1 の WARN と U3 の INFO（invitationId） |
| 送れない理由が受け手の停止か | U1 の WARN の failureKind。配備した環境では profile `mail` の Mailpit が動いているか（`docker compose ps mailpit`） |
| 招待・送り直しの遅さが送信のせいか | U1 の送信の時間と、要求の span の中の送信の span |
| 招待を使えない設定か | 起動のときの INFO（unavailableReasons）、一覧の invitationEnabled |

## 8. observability-setup で確かめること

| 確かめること | やり方 |
|---|---|
| 6本の API の `uri`・`method`・`status` のラベルの実際の名前と値 | 手元の監視を起動し、各 API を呼んでから Grafana で `http_server_requests_milliseconds_count` を引く（`project.md` の Corrections「式は起動して確かめる」）。招待・取り消し・登録の完了は監査ログに残り、登録の完了は利用者も残すため、送る前に依頼者に伝える |
| U1 の送信の観測の実際の名前とタグ（`mail.template` が `invitation`） | 同上。Mailpit を起動して招待を1回送る |
| 既存の警報の式が変わらず動くこと（`ms-origin` を含む） | 既存の警報の式をすべて流し直す |
| 6節の問い合わせの列の名前 | 複写を読み取りで開いて流す |

## 9. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| M-D1 | `nfr-design/observability-design.md` 1節 | 指標は `http.server.requests`（`uri`・`status`・`method` のラベル） | 手元の監視での実際の名前 `http_server_requests_milliseconds_*` で書いた | U2 の基盤の設計の M-D1 と同じ。承認済みの文書は書き換えない。observability-setup で起動して確かめる |
| M-D2 | `nfr-design/reliability-design.md` 4節 | 定期の削除の失敗は ERROR で出し、次の回に任せる | 既存の `ms-cleanup-fail` は文言で数えるため招待の削除の失敗に当たらず、`ms-error-logs` の閾値（5 分で 5 件）では1回の失敗で鳴らないことを記録した。警報は足さない | NFR6.9（新しい警報を足さない）の範囲で、見え方の限界を明記した。配備先が決まったときに見直す |
| M-D3 | 既存の警報 `ms-origin` | 送り元の不一致の多発を数える | 配備した環境に `MASTERSMITH_WEB_BASE_URL` を入れた後は、`127.0.0.1` から開いたログインでも数が増えることを記録した | Q1 A の影響（`infrastructure-specification.md` 9節の I-D1）。警報は変えない |
