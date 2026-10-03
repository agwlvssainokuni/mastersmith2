# ログと監査ログの見方（log-queries）

前の Intent の記録（`aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/log-queries.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。409 `USER_ADMIN_BUSY` と最後の管理者の保護の拒否は、警報ではなく、ここの問い合わせで見ます（決まっていること、U3 の `monitoring-design.md` 4節・7節）。

## 1. Loki に届く今回のログ（使い捨ての環境で確かめた）

使い捨ての環境から送らせたログで、項目の名前を確かめました（送り方は `dashboards.md` 2節）。成功と業務の拒否は監査に残し、アプリのログは境界の WARN だけです。

| ロガー | 本文 | レベル | キー（Loki の属性） |
|---|---|---|---|
| `GlobalExceptionHandler` | 要求をエラー応答に変換しました | WARN | `code`・`status`・`exceptionType` |
| `AdminAccessDeniedHandler` | 権限が足りないため要求を拒否しました | WARN | `code`（`ACCESS_DENIED`） |
| `RowLockFailures`（排他の口） | 行の排他を取れませんでした | WARN | `lockKind`・`exceptionClass`（ソースで確かめた。今回は 0 件） |
| `AuditEventListener` | 監査イベントの記録に時間がかかりました | WARN | `auditEventType`・`elapsedMs`（ソースで確かめた。今回は 0 件） |
| `AuditEventListener` | 監査イベントの記録に失敗しました | ERROR | `auditEventType`・`result`・`failureReason`・`actorUserId`・`targetUserId`・`exceptionType` ほか（ソースで確かめた。今回は 0 件） |

- 状態の項目は `severity_text`（`WARN` など）・`scope_name`（ロガー）・`trace_id`・`span_id` です。
- **漏えい**: 直近 30 分の Loki のログで、`@example.test`・`perf-ua`・`perf-q`・`perf-n@`・`eyJ`（JWT の形）・`Bearer `・`MVStoreException`・`password_hash`・`$2a$`（パスワードのハッシュ値の形）の一致は、どれも 0 件でした。使い捨てのアプリの docker logs の `@example.test` の 2 件は、どちらも初期管理者の INFO のキー `maskedEmail`（伏せ字）で、キー `email` はありませんでした（値は表示していない）。

## 2. Explore（Loki）で使う問い合わせ

どれも書いた後に流し、`success` を確かめました（2026-10-03 22:29:38、直近 1 時間）。

| ID | 見たいもの | LogQL | 確かめた値 |
|---|---|---|---|
| L1 | 拒否の code ごとの件数 | `sum by (code, status) (count_over_time({service_name="mastersmith"} \|= "要求をエラー応答に変換しました" [1h]))` | `USER_ADMIN_NO_CHANGE`/409 115・`USER_ADMIN_LAST_ADMIN`/409 69・`USER_ADMIN_SELF_OPERATION`/409 46・`USER_ADMIN_TARGET_SUSPENDED`/409 23・`USER_NOT_FOUND`/404 46・`VALIDATION_FAILED`/400 46・`ACCESS_DENIED`/403 33・`AUTHENTICATION_FAILED`/401 23 |
| L2 | 認可の入口の 403 | `sum(count_over_time({service_name="mastersmith"} \|= "権限が足りないため要求を拒否しました" [1h]))` | 219 |
| L3 | 409 `USER_ADMIN_BUSY` の件数 | `sum(count_over_time({service_name="mastersmith"} \|= "要求をエラー応答に変換しました" \| code="USER_ADMIN_BUSY" [1h]))` | 結果なし（0 件） |
| L4 | 行の排他を取れなかった WARN（種類ごと） | `sum by (lockKind, exceptionClass) (count_over_time({service_name="mastersmith"} \|= "行の排他を取れませんでした" [1h]))` | 結果なし（0 件） |
| L5 | U3 の監査の書き込みの遅れ（1 日） | `sum(count_over_time({service_name="mastersmith"} \|= "監査イベントの記録に時間がかかりました" \| auditEventType=~"USER_ADMIN_GRANTED\|USER_ADMIN_REVOKED\|USER_SUSPENDED\|USER_RESUMED\|LOGIN_FAILURES_RESET" [1d]))` | 結果なし（0 件） |
| L6 | U3 の監査の書き込みの失敗 | L5 の本文を「監査イベントの記録に失敗しました」、幅を `[1h]` にしたもの | 結果なし（0 件） |

- **BUSY の追い方**: L3 で見つけた行の `trace_id` で `{service_name="mastersmith"} |= "行の排他を取れませんでした"` を絞ると、同じ要求の排他の口の WARN（`lockKind` 例 `ADMIN_ROWS`・`USER_ROW`・`LOGIN_ATTEMPT_ROW`）が出ます（U3 の `security-design.md` 12節の SD-5）。BUSY は監査に残りません。今回は BUSY が起きなかったため、2行の結び付きは実際には確かめていません（`Unverified`、performance-validation の (B) の場面で見る）。
- **入口の 403 の見方**: 入口で拒否した 403 は、指標では `uri="UNKNOWN"` に入り道で分けられません（`dashboards.md` 2節）。件数は L2 で、道は監査の `ACCESS_DENIED` の行の `request_path` で見ます。L1 の `ACCESS_DENIED`/403（33）は業務の層の確かめ直しの 403 で、監査の `NOT_ADMIN`（33）と一致しました。L2（219）は監査の `ACCESS_DENIED`（219）と一致しました。
- L1 の `AUTHENTICATION_FAILED`/401 は、失敗回数を戻す操作の前にわざと失敗させたログインです（監査の `LOGIN_FAILED` 23 件）。

## 3. 監査ログの確かめ（使い捨ての環境）

使い捨てのアプリを止めてから（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml stop app`）、内部DB のファイルをホームの下の一時の置き場（権限 700）へ複写し、複写を読み取り（`jdbc:h2:file:...;ACCESS_MODE_DATA=r`）で開いて、件数だけを数えました（値は出していない）。問い合わせ：

```sql
SELECT event_type, result, COALESCE(failure_reason,'-') AS reason, COUNT(*) AS n,
       COUNT(actor_user_id) AS actor, COUNT(target_user_id) AS target, COUNT(trace_id) AS trace
  FROM audit_events
 WHERE event_type IN ('USER_ADMIN_GRANTED','USER_ADMIN_REVOKED','USER_SUSPENDED','USER_RESUMED','LOGIN_FAILURES_RESET')
 GROUP BY event_type, result, failure_reason ORDER BY event_type, result, reason;
```

結果と、送った要求の状態コードから見込んだ件数の比べ：

| 種類 | 結果 | 理由 | 件数 | 見込み | `actor_user_id`・`target_user_id`・`trace_id` のある行 |
|---|---|---|---|---|---|
| USER_ADMIN_GRANTED | SUCCESS | — | 197 | 197 | 197 |
| USER_ADMIN_GRANTED | FAILURE | NOT_ADMIN | 33 | 33 | 33 |
| USER_ADMIN_GRANTED | FAILURE | NO_CHANGE | 23 | 23 | 23 |
| USER_ADMIN_GRANTED | FAILURE | TARGET_SUSPENDED | 23 | 23 | 23 |
| USER_ADMIN_GRANTED | FAILURE | USER_NOT_FOUND | 23 | 23 | 23 |
| USER_ADMIN_REVOKED | SUCCESS | — | 197 | 197 | 197 |
| USER_ADMIN_REVOKED | FAILURE | LAST_ACTIVE_ADMIN | 69 | 69 | 69 |
| USER_ADMIN_REVOKED | FAILURE | NO_CHANGE | 23 | 23 | 23 |
| USER_ADMIN_REVOKED | FAILURE | SELF_OPERATION | 23 | 23 | 23 |
| USER_SUSPENDED | SUCCESS | — | 23 | 23 | 23 |
| USER_SUSPENDED | FAILURE | NO_CHANGE | 23 | 23 | 23 |
| USER_SUSPENDED | FAILURE | SELF_OPERATION | 23 | 23 | 23 |
| USER_RESUMED | SUCCESS | — | 23 | 23 | 23 |
| USER_RESUMED | FAILURE | NO_CHANGE | 23 | 23 | 23 |
| LOGIN_FAILURES_RESET | SUCCESS | — | 23 | 23 | 23 |
| LOGIN_FAILURES_RESET | FAILURE | NO_CHANGE | 23 | 23 | 23 |

- **すべて見込みと一致しました**。5つの種類と6つの理由の名前（`SELF_OPERATION`・`TARGET_SUSPENDED`・`NO_CHANGE`・`LAST_ACTIVE_ADMIN`・`USER_NOT_FOUND`・`NOT_ADMIN`）は、`AuditEventType.java`・`AuditFailureReason.java` と U3 の `monitoring-design.md` 5節のとおりでした。どの行も操作した人・対象・トレースID を持っていました。
- 見込みの出し方: 成功は 204 の件数（印を付ける 197 = 通常 23＋競合の後に戻した 138＋`NOT_ADMIN` の競合で先に通った側 36。外す 197 = 通常 23＋競合の勝った側 69＋`NOT_ADMIN` の競合の外す側 69＋後片付け 36）、失敗は 409・404・業務の層の 403 の件数。`LAST_ACTIVE_ADMIN` と `NOT_ADMIN` は同時の競合で作った（`dashboards.md` 2節）。
- そのほかの種類: `ACCESS_DENIED` FAILURE 219（入口の 403、L2 と一致）・`LOGIN_FAILED` FAILURE 23・`LOGIN_SUCCEEDED` SUCCESS 75。表の行は全部で 1,089 行。
- 一覧（GET）・氏名と言語の変更・入力の誤り（400）・BUSY は監査に残らないこと（U3 の `monitoring-design.md` 5節）とも合っています（どの種類にも当たる行が無い）。
- 配備した環境の監査を見るときは、README の「監査ログの確かめ方」の手順（アプリを止めてボリュームを複写し、複写を読み取りで開く）で、上と同じ問い合わせを流します。最後の管理者の保護で拒否された操作は `WHERE failure_reason = 'LAST_ACTIVE_ADMIN'`、権限を失った管理者の操作は `failure_reason = 'NOT_ADMIN'` で絞ります。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/observability-setup-questions.md`（決まっていること、Q1: A、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/log-queries.md`
- U3 の `construction/u3-user-admin-api/infrastructure-design/monitoring-design.md`（4・5・7節）・`infrastructure-specification.md`、`construction/u3-user-admin-api/nfr-design/security-design.md`（12節）・`reliability-design.md`・`performance-design.md`、U4 の `construction/u4-admin-forbidden-ui/infrastructure-design/monitoring-design.md`
- `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`・`audit/domain/AuditEventType.java`・`AuditFailureReason.java`・`common/persistence/RowLockFailures.java`・`useradmin/service/UserAdminService.java`、`backend/src/main/resources/db/migration/V4__u4_audit_event.sql`
- `README.md`（「監査ログの確かめ方」）、`perf/README.md`
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:3100/loki/api/v1/query?...`・`query_range?...`、`java -cp h2-2.4.240.jar org.h2.tools.Shell -url "jdbc:h2:file:...;ACCESS_MODE_DATA=r"`（使い捨ての環境の複写）、`docker logs mastersmith-perf-app-1 | grep -c ...`

## Assumptions & Open Questions

None.
