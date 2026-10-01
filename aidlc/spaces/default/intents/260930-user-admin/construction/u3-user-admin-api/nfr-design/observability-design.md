# Observability Design — U3 利用者の管理の API（u3-user-admin-api）

U3 の観測の設計です。承認済みの `construction/u3-user-admin-api/nfr-requirements/observability-requirements.md`（NFR5.9〜NFR5.11・NFR3.4・NFR9.3）を満たす作りを決めます。U3 は新しい指標・警報・ダッシュボードを足さず、既存の HTTP の指標・構造化ログ・トレースID・監査ログの上で見えるようにします。手元の監視（grafana/otel-lgtm）は compose の profile で見たいときだけ起動し、外部エクスポートは既定で無効のままです。

出典の略号は `reliability-design.md` と同じ。

## 1. 指標（NFR5.9）

- 7つの API の応答時間と状態コードは、既存の `http.server.requests`（`uri`・`method`・`status` のラベル、100・250・500・1000・2000・5000 ms のバケット）で取れる。新しい指標は足さない。
- `uri` のラベルは道の型（`/api/admin/users`・`/api/admin/users/{userId}/grant-admin` など）になり、利用者 ID と検索の文字を含まない。Spring MVC の道の型の引数（`{userId}`）で受けるため、既存の作りのまま。
- 確かめ（observability-setup）: 手元の監視を起動し、7つの API に要求を指標の送信の周期（1 分）を複数またいでくり返し送り、指標とラベルの名前・`uri` の値・`le` のバケットを実際に確かめる。

## 2. 警報（NFR5.10）

新しい警報は足さず、既存の警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）が続けて起きる異常・数の多い異常のときにだけ U3 の失敗を拾います。

| U3 の失敗 | 拾う既存の警報（しきい値） | 1件・数件のときに見るもの |
|---|---|---|
| 確かめ直しの 403・認可の入口の 403 | `ms-forbidden`（すべての 403、直近 1 時間に 20 件を超えたら） | 監査の NOT_ADMIN と既存のアクセスの拒否 |
| 想定外の誤り（500） | `ms-5xx-ratio`（5xx が 1% を超える状態が 5 分）・`ms-error-logs`（5 分に 5 件を超えたら） | アプリのログの ERROR とトレースID |
| 監査の書き込みの失敗・遅れ | `ms-audit-fail`（直近 1 時間に 1 件でも）・`ms-audit-slow`（1 日に 2 件を超えたら）。Loki を使うため手元の監視を起動しているときだけ | アプリのログの ERROR・WARN |
| 接続プールの待ち | `ms-pool-pending`（待ちが 0 を超える状態が 1 分）。借りる待ちは 5 秒で時間切れになるため短い枯渇では鳴らない | 2本目の時間切れによる監査の記録の失敗の ERROR |
| 409（BUSY・最後の管理者など） | 警報にしない | アプリのログの WARN（BUSY）・監査（ほかの 409） |

- 拾うと書いた警報が実際に数えることは、observability-setup で状態コードとログのレベルに加えて、しきい値を超えたときに鳴ることまで確かめる。
- `ms-pool-pending` を時間切れの累計（`hikaricp_connections_timeout_total`）の増加を見る式に見直すことは、配備先が決まるまでの申し送りのまま。

## 3. アプリのログ（NFR3.4、BR3.5・BR7.4）

| 場面 | レベル | キーと値 | 出す場所 |
|---|---|---|---|
| 409 USER_ADMIN_BUSY | WARN | code（既存の変換の境界の形） | `GlobalExceptionHandler`（既存。スタックトレースなし） |
| 排他を取れなかった（上限切れ・行き詰まり） | WARN | 排他の種類（例 `ADMIN_ROWS`・`USER_ROW`・`LOGIN_ATTEMPT_ROW`・招待の行）と例外のクラスの名前（`exceptionClass`） | 問い合わせを実行する `repository` のメソッド（`reliability-design.md` 5.2、既存の経路は `security-design.md` 7.2）。例外そのもの（cause とスタックトレース）は渡さない |
| 既存のログイン・招待・送り直し・取り消し・登録の完了の上限切れ | 上の WARN と ERROR（スタックトレース付き） | ERROR は値を含まない例外（例 `RowLockUnavailableException`）のもの。元の例外の連なりは出ない | repository の WARN と `GlobalExceptionHandler`（既存。応答は今までどおり 500） |
| ほかの 4xx（404・409・403・400） | WARN 以下 | code | `GlobalExceptionHandler`（既存） |
| 想定外の誤り（500） | ERROR（スタックトレース付き） | 既存の形 | `GlobalExceptionHandler`（既存） |
| 監査の書き込みの失敗 | ERROR | 既存の形（メールアドレス・氏名を含めない） | 既存の `AuditEventRecorder` の周り |
| 成功・業務の拒否 | 新しいログを出さない | ― | 監査ログで見る（監査をアプリのログで代用しない） |

- BUSY のときは、排他の口の WARN（例外のクラスの名前）と、変換の境界の WARN（code）の2行が出る。既存の経路の上限切れも、repository の WARN と変換の境界の ERROR の2行になる。1つの失敗にログが2行出る点は、`team.md` の Code Style の「例外のログは変換する境界で1回だけ」と形の上で食い違うため、決まりとの差として `security-design.md` 12節の SD-5 に書き、承認の場で受け入れた。2行は同じトレースID で結び付く。
- 排他の口の WARN を例外のクラスの名前だけにするのは、例外の連なりに排他されていた行の値が入るため（`security-design.md` 7節）。上限切れ（試しのコードで `jakarta.persistence.LockTimeoutException` と確かめた）と行き詰まり（`PessimisticLockException` の系統の見込み）は、クラスの名前で見分けられる。行き詰まりの型は試していないため、受ける範囲は型と誤りの番号の両方で見分ける判定による（`security-design.md` 7.1）。
- どのログにも検索の文字・メールアドレス・氏名・トークン・ハッシュ値・失敗回数を出さない。業務のログは利用者 ID と区分だけ（BR7.4）。
- 確かめ: 既存のログの形のテストと、`UserAdminSecretLeakIT`（上限切れの場合を含む）と既存の経路の漏えいのテスト（`security-design.md` 7.2）を、TRACE を有効にした場合と既定のログのレベル（INFO）の場合の両方で流して確かめる。

## 4. 監査ログ（NFR9.3、BR6.1〜BR6.4、C6）

| 項目 | 値 |
|---|---|
| 種類（`event_type`） | `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET`（既存の `AuditEventType` に足す） |
| 結果（`result`） | 既存の `SUCCESS`・`FAILURE` |
| 理由（`failure_reason`） | `USER_NOT_FOUND`・`NOT_ADMIN`（既存）、`SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`（足す） |
| 操作した人・対象 | `actor_user_id` は認証の主体、`target_user_id` は要求の利用者 ID（対象がいないときもそのまま） |
| 送り手 | 既存の `RequestOrigin`（接続元 IP・User-Agent・トレースID） |
| 残さないもの | 一覧・氏名と言語の変更・BUSY・入力の誤り・認可の入口の 401 と 403（403 は既存のアクセスの拒否だけ）。メールアドレス（`entered_email` は空）・氏名・検索の文字・失敗回数・ハッシュ値 |

- 出来事（例 `UserAdminAuditEvent`、`useradmin.domain`）を確定の後に既存の `AuditEventListener` が受け、`AuditEventFactory` が写す（ADR-006、FS の 7節の監査の3ファイル）。
- 値の長さは 32 文字以内で、列と CHECK 制約は変えない（`reliability-design.md` 9節）。
- 確かめ: 5つの操作のそれぞれで、成功と各理由の失敗の監査の行の項目を結合テストで確かめる。残さない場合に行が増えないことも確かめる。

## 5. トレース

- 既存の Micrometer Tracing のまま。トレースID はアプリのログと監査の行に入る。新しいスパンと属性は足さない。
- URL の問い合わせの部分（q）は、既存の仕組みがトレースの属性とアクセスの拒否の監査のパスから除く（BR7.4）。

## 6. SLI・SLO（NFR5.11）

- SLO は決めない。手元の監視を常に動かしていないため、前の Intent と同じく、配備の直後・監視の確かめ・負荷の試験の時点の値を基準の値として記録し、配備先が決まったときに SLO と測り方を決め直す。目標を緩めて満たしたことにはしない。
- SLI の候補（配備先が決まったときの材料）: `/api/admin/users` の一覧と5つの操作の応答時間の p95（`http.server.requests`）、5xx の割合、監査の書き込みの失敗の件数。
- 基準の値の記録の持ち主は performance-validation と observability-setup。SLO の判定は Unverified として持ち主の段を書く。
