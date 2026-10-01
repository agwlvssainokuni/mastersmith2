# Observability Requirements — U3 利用者の管理の API（u3-user-admin-api）

U3 の観測の要件です。U3 は新しい指標・ログ・警報・ダッシュボードを足さず、既存の仕組み（HTTP の指標、構造化ログ、トレースID、監査ログ）の上で見えるようにします（この段の Q4 A、依頼者の Looks correct）。手元の監視は grafana/otel-lgtm を compose の profile で見たいときだけ起動し、外部エクスポートは既定で無効です（`aidlc/spaces/default/memory/project.md` の Deployment、`team.md` の Deployment）。観測の指標は応答時間の NFR5、ログの中身は NFR3、監査は NFR9 の枝番に寄せました（`performance-requirements.md` の冒頭）。出典の略号は `performance-requirements.md` と同じ。

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR5.9 | 7つの API の応答時間と状態コードは、既存の HTTP の指標（Micrometer の `http.server.requests`、`uri`・`method`・`status` のラベル）で取れる。`uri` のラベルは道の型（`/api/admin/users`・`/api/admin/users/{userId}/...`）になり、利用者 ID と検索の文字を含まない。この指標は 100・250・500・1000・2000・5000 ms のバケットを持つため、1 秒の目標の p95 を判定できる | 手元の監視を起動し、7つの API に要求を送って、指標とラベルの名前・`uri` の値・`le` のバケットを実際に確かめる。要求は指標の送信の周期（1 分）を複数またいでくり返し送る（observability-setup） | NFR5、Q4 A、要点 17、`backend/src/main/resources/application.yaml` の `slo`、PM の Corrections（式は起動して確かめる・p95 のバケット・送信の周期の学び） |
| NFR5.10 | 新しい指標・警報・ダッシュボードは足さない。既存の警報は、続けて起きる異常・数の多い異常のときにだけ U3 の失敗を拾う。1件・数件の失敗は警報では拾えず、監査の記録とアプリのログで見る。対応は次のとおり（しきい値は `docker/monitoring/provisioning/alerting/mastersmith.yaml` の値）: 確かめ直しの 403 と認可の入口の 403 は「管理画面への拒否の増加」（`ms-forbidden`、すべての 403 を数え、直近 1 時間に 20 件を超えたら鳴る）。想定外の誤りは「5xx の割合の増加」（`ms-5xx-ratio`、全体の要求に対する 5xx が 1% を超える状態が 5 分続いたら）と「ERROR のログの増加」（`ms-error-logs`、5 分に 5 件を超えたら）。監査の書き込みの失敗は `ms-audit-fail`（直近 1 時間に 1 件でもあれば）、遅れは `ms-audit-slow`（1 日に 2 件を超えたら）で、どちらも Loki の文言の一致のため手元の監視を起動しているときだけ見える。接続の待ちは「コネクションプールの待ち」（`ms-pool-pending`、待ちの数が 0 を超える状態が 1 分続いたら）で、借りる待ちが 5 秒で時間切れになるため短い枯渇では鳴らない。409（BUSY・最後の管理者など）は警報にしない | 既存の警報の決まりが変わらないことをレビューで確かめる（code-generation）。拾うと書いた警報が実際に数えることを、状態コードとログのレベルに加えて、しきい値を超えたときに鳴ることまで確かめる（observability-setup）。`ms-pool-pending` を接続の枯渇の気づきとして頼るため、時間切れの累計（`hikaricp_connections_timeout_total`）の増加を見る式への見直しを、配備先が決まるまでの申し送りとする | NFR5、Q4 A、要点 17、PM の Corrections（既存の警報で拾うと書く前に確かめる学び）、レビューの R-03 |
| NFR5.11 | SLO は決めない。手元の監視を常に動かしていないため、前の Intent と同じく、配備の直後・監視の確かめ・負荷の試験の時点の値を基準の値として記録し、配備先が決まったときに SLO と測り方を決め直す。目標を緩めて満たしたことにはしない | performance-validation と observability-setup で基準の値を記録する。SLO の判定は Unverified として持ち主の段を書く | 要点 18、PM の Deployment・Testing Posture |
| NFR3.4 | U3 のアプリのログは既存の決まりに従う: キーと値の構造化ログ、トレースID を含む、想定内の誤り（4xx）は WARN 以下でスタックトレースなし、想定外（5xx）は ERROR でスタックトレース付き、例外のログは変換する境界で1回だけ。409 USER_ADMIN_BUSY は既存のエラー応答の仕組みが WARN で code を出す。ログに検索の文字・メールアドレス・氏名・トークン・ハッシュ値を出さない（NFR3.1） | 既存のログの形のテストと、`UserAdminSecretLeakIT`（TRACE を有効にする）で確かめる（code-generation） | NFR3、BR3.5、TM の Code Style、PM の Forbidden |
| NFR9.3 | 5つの操作は、業務の判定に届いた要求ごとに監査の行を1件、必須の項目（eventType は USER_ADMIN_GRANTED・USER_ADMIN_REVOKED・USER_SUSPENDED・USER_RESUMED・LOGIN_FAILURES_RESET のどれか、result、FAILURE のとき failureReason は USER_NOT_FOUND・SELF_OPERATION・TARGET_SUSPENDED・NO_CHANGE・LAST_ACTIVE_ADMIN・NOT_ADMIN のどれか、actorUserId に操作した人、targetUserId に要求の利用者 ID、sourceIp・userAgent・traceId、occurredAt）つきで残す。対象がいないときも要求の利用者 ID を残す。一覧・氏名と言語の変更・BUSY・入力の誤り・認可の入口の 401 と 403 では管理の操作の監査を残さない（認可の入口の 403 は既存のアクセスの拒否だけ）。監査ログをアプリのログで代用しない | 5つの操作のそれぞれで、成功と各理由の失敗の監査の行の項目を結合テストで確かめる。残さない場合に行が増えないことも確かめる（code-generation） | NFR9、BR6.1・BR6.2・BR6.4、CS の C6、PM の Mandated、TM の Testing Posture（管理の操作の監査）、FS の 3節 |

## 何を見れば分かるか

| 知りたいこと | 見るもの |
|---|---|
| 一覧・操作が遅い、誤りが多い | `http.server.requests` の `/api/admin/users` の応答時間と状態コード（NFR5.9） |
| 誰が誰の印を付けた・外した、利用を止めた・解いた、失敗回数を戻した | 監査ログの5つの種類を actorUserId・targetUserId で絞る（NFR9.3）。内部DB にあるため、アプリを止めて複写し読み取りで開く既存の手順で見る |
| 最後の管理者の保護で拒否された操作 | 監査ログの failureReason `LAST_ACTIVE_ADMIN` |
| 排他の待ちが上限に届いた | アプリのログの WARN の code `USER_ADMIN_BUSY`（監査には残らない） |
| 権限を失った管理者の操作・管理者でない利用者の呼び出し | 1件ずつは監査の `NOT_ADMIN` と既存のアクセスの拒否。数が多いとき（直近 1 時間に 403 が 20 件を超えたとき）だけ既存の警報 `ms-forbidden` が鳴る |
| 監査の書き込みが失敗した・遅い | アプリのログの ERROR・WARN。手元の監視（Loki）を起動しているときは既存の警報 `ms-audit-fail`（1件でも）・`ms-audit-slow`（1 日に 2 件を超えたら） |
| 想定外の誤り（500） | 1件ずつはアプリのログの ERROR とトレースID。続けて起きたときだけ `ms-5xx-ratio`（1% 超が 5 分）・`ms-error-logs`（5 分に 5 件超）が鳴る |
| 接続プールが尽きかけている | 待ちが 1 分続いたときだけ既存の警報 `ms-pool-pending` が鳴る。短い枯渇は、2本目の時間切れによる監査の記録の失敗の ERROR で見る（`ms-pool-pending` の式の見直しは申し送り、NFR5.10） |

## 選ばなかったもの

| 候補 | 選ばなかった理由 |
|---|---|
| 5つの操作をアプリのログに INFO で出し、印を付けた操作と最後の管理者の拒否に Loki の警報を足す（Q4 の B） | 監査ログと同じ事実を二重に持つ。管理者は少数で操作はまれ、権限の変更は監査ログで後から追える。警報の通知の先は配備先が決まるまで無い。配備先が決まったときに見直す |
| 409 USER_ADMIN_BUSY の WARN の数に警報を足す（Q4 の C） | 目標の負荷で BUSY が出ないことを負荷の試験で確かめる（NFR5.4・NFR5.6）。配備先が決まったときに見直す |
