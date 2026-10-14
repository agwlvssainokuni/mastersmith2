# ログと監査ログの見方（log-queries）

前の Intent の記録（`aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/log-queries.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。ここで扱うのは次のものです。

- `ROLE_BUSY`・`GROUP_BUSY` と業務の 409・404: 警報にしない（group・role の `monitoring-design.md` 2節）
- 置き場の 403
- ヒープが尽きて JVM が止まったとき

どれも警報ではなく、ここの問い合わせと手順で見ます（Q2: A・Q4: A・Q5: A）。

## 1. Loki に届く今回のログ（使い捨ての環境で確かめた）

使い捨ての環境から送らせたログで、項目の名前を確かめました（送り方は `dashboards.md` 2節）。成功と業務の拒否は監査に残し、アプリのログは境界の WARN だけです（group・role の `monitoring-design.md` 4節）。

| ロガー | 本文 | レベル | キー（Loki の属性） | 今回の件数 |
|---|---|---|---|---|
| `GlobalExceptionHandler` | 要求をエラー応答に変換しました | WARN | `code`・`status`・`exceptionType` | 79（L1） |
| `AdminAccessDeniedHandler` | 権限が足りないため要求を拒否しました | WARN | `code`（`ACCESS_DENIED`） | 26（L2） |
| `RowLockFailures`（group・role の排他の口） | 行の排他を取れませんでした | WARN | `lockKind`（`GROUP_ROW`・`GROUP_NAME_KEY`・`GROUP_MEMBER_KEY`・`ROLE_ROW`・`ROLE_NAME_KEY`・`ROLE_ASSIGNMENT_KEY`・`WORK_ROLE_SELECTION_KEY` など）・`exceptionClass` | 0（ソースで確かめた） |
| `RoleTransferSlot` | 権限の受け渡しの入口を取れませんでした | WARN | `lockKind`（`ROLE_TRANSFER_SLOT`）だけ | 0（ソースで確かめた） |
| `AuditEventListener` | 監査イベントの記録に時間がかかりました／失敗しました | WARN／ERROR | `auditEventType` ほか（前の Intent の記録のとおり） | 0 |

- 状態の項目は `severity_text`（`WARN` など）・`scope_name`（ロガー）・`trace_id`・`span_id` です。
- **漏えい**: 直近 1 時間の Loki のログで、次の文字の一致はどれも 0 件でした。
  - 利用者: `@example.test`・`obs-user`・`perf-admin`
  - トークンと秘密: `eyJ`（JWT の形）・`Bearer `・`password_hash`・`$2a$`（パスワードのハッシュ値の形）・`MVStoreException`
  - ロール・グループ・スキーマの名前: `obs-role`・`obs-g-`・`obs-r-`・`OBS`

  使い捨てのアプリの `docker logs` の `@example.test` の 2 件は、どちらも初期管理者の INFO のキー `maskedEmail`（伏せ字）でした（2回の起動の分。キー `email` は無い。値は表示していない）。
- 直近 1 時間のレベルごとの件数（L6）は WARN 105・INFO 10・ERROR 1 でした。ERROR の1件は、lgtm を網につなぐ前に使い捨てのアプリがログを送ろうとした `UnknownHostException: lgtm`（`io.opentelemetry.exporter.internal.http.HttpExporter`）で、確かめの手順の上の出来事です（`dashboards.md` 2節）。

## 2. Explore（Loki）で使う問い合わせ

どれも書いた後に流し、`success` を確かめました（2026-10-15 01:36〜01:38 JST、直近 1 時間）。

| ID | 見たいもの | LogQL | 確かめた値 |
|---|---|---|---|
| L1 | 拒否の code ごとの件数 | `sum by (code, status) (count_over_time({service_name="mastersmith"} \|= "要求をエラー応答に変換しました" [1h]))` | `GROUP_NAME_DUPLICATE`/409 13・`GROUP_NOT_FOUND`/404 13・`ROLE_NAME_DUPLICATE`/409 13・`ROLE_NOT_FOUND`/404 13・`ACCESS_DENIED`/403 13（置き場）・`VALIDATION_FAILED`/400 13（置き場の引数なし）・`ROLE_NO_CHANGE`/409 1 |
| L2 | 認可の入口の 403 | `sum(count_over_time({service_name="mastersmith"} \|= "権限が足りないため要求を拒否しました" [1h]))` | 26 |
| L3 | 行の排他を取れなかった WARN（種類ごと） | `sum by (lockKind, exceptionClass) (count_over_time({service_name="mastersmith"} \|= "行の排他を取れませんでした" [1h]))` | 結果なし（0 件） |
| L4 | 権限の受け渡しの入口を取れなかった WARN | `sum(count_over_time({service_name="mastersmith"} \|= "権限の受け渡しの入口を取れませんでした" [1h]))` | 結果なし（0 件） |
| L5 | 409 `ROLE_BUSY`・`GROUP_BUSY` の件数 | `sum by (code) (count_over_time({service_name="mastersmith"} \|= "要求をエラー応答に変換しました" \| code=~"ROLE_BUSY\|GROUP_BUSY" [1h]))` | 結果なし（0 件） |
| L6 | レベルごとの件数 | `sum by (severity_text) (count_over_time({service_name="mastersmith"} [1h]))` | ERROR 1・INFO 10・WARN 105 |
| L7 | この Intent の監査の書き込みの遅れ（1 日） | `sum(count_over_time({service_name="mastersmith"} \|= "監査イベントの記録に時間がかかりました" \| auditEventType=~"GROUP_.*\|ROLE_.*\|WORK_ROLE_SWITCHED" [1d]))` | 結果なし（0 件） |
| L8 | この Intent の監査の書き込みの失敗 | L7 の本文を「監査イベントの記録に失敗しました」、幅を `[1h]` にしたもの | 結果なし（0 件） |
| L9 | 置き場の 403（業務の層の `ACCESS_DENIED`） | `sum by (code, status) (count_over_time({service_name="mastersmith"} \|= "要求をエラー応答に変換しました" \| code="ACCESS_DENIED" [1h]))` | `ACCESS_DENIED`/403 13 |

- **BUSY の追い方**（Q2: A）: L5 で見つけた行の `trace_id` で L3 または L4 の本文を絞ると、同じ要求の排他の口の WARN（`lockKind`）が出ます。BUSY は監査に残りません。今回は重なりを作っていないため、L3〜L5 は式が流れることだけを確かめました。BUSY の行と2行の結び付きを実際に見るのは、performance-validation の書き込みの場面（`groupPoolLimit`・`rolePoolLimit` など）で lgtm を動かしたときです（`alarms.md` 2節）。
- `ROLE_TRANSFER_SLOT` の WARN（L4）は、`RowLockFailures` を使わない別の決まった文で、キーは `lockKind` だけです（`role/service/RoleTransferSlot.java`）。L3 には入らないため、別に数えます。

## 3. 403 の見分け方（Q4: A）

`ms-forbidden`（直近 1 時間の 403 が 20 件を超える）は、`uri` で絞らずに次の3つを数えます。今回の確かめでは、3つ合わせて 39 件で**鳴りました**（`alarms.md` 2節）。

| 403 の種類 | 指標の `uri` | アプリのログ | 監査 | 今回の件数 |
|---|---|---|---|---|
| 認可の入口の 403（管理者でない利用者からの管理の API、未分類の道など） | `UNKNOWN` | L2「権限が足りないため要求を拒否しました」 | `ACCESS_DENIED`/`NOT_ADMIN`（要求の道 `request_path` を持つ） | 26（指標 27.7・L2 26・監査 26） |
| 業務の層の確かめ直しの 403（前の Intent の利用者の管理など） | 道の型 | L1 の `ACCESS_DENIED`（`exceptionType` が業務の例外） | `NOT_ADMIN` など、その操作の種類の `FAILURE` | 0 |
| **置き場の 403**（`/api/me/table-access`、作業ロールで見られないテーブル） | `/api/me/table-access` | L9（L1 の `ACCESS_DENIED`/403） | **残らない**（navigation の `monitoring-design.md` 4節） | 13（指標 13.8・L9 13） |

- 鳴ったときの見方:
  1. `dashboards.md` の R2（403 の `uri` ごとの内訳）で、置き場の 403 が多いかを見る。
  2. 置き場の 403 は業務の結果（権限の無いテーブルを開いた・開いたままの画面で作業ロールを替えた）で、障害とは限らない。利用者は監査に残らないため、同じ時間の L9 の行の `trace_id` から Tempo で要求を追う。テーブルの名前は問い合わせの部分で、スパンにもログにも出ない（`tracing-config.md` 1節）。
  3. 入口の 403 は、L2 と監査の `ACCESS_DENIED` の `request_path` で、どの道を誰が呼んだかを見る（前の Intent と同じ）。
- 警報の注記（「監査ログで利用者と接続元IPを確かめる」）は、置き場の 403 には当たりません。式とあわせて、配備先が決まり利用者が増えたときに見直す申し送りです（`alarms.md` 2.2）。

## 4. ヒープが尽きて JVM が止まったときの見方（Q5: A）

`-XX:+ExitOnOutOfMemoryError` で JVM が止まると、compose の `restart: "no"` のため起き直しません（README の「コンテナの資源の上限」の近く）。手元の監視を動かしているときに頼れるのは `ms-app-absent` だけで、鳴るまでの時間は `alarms.md` 2.1 に測った値を書きました。

| 見るもの | コマンド・問い合わせ | 分かること |
|---|---|---|
| 止まった理由 | `docker inspect mastersmith-app-1 --format '{{.State.Status}} {{.State.OOMKilled}} {{.State.ExitCode}} {{.State.FinishedAt}}'` | JVM が自分で止まったときは `OOMKilled false`・終了コードは 0 以外（コンテナのメモリの上限で消されたときは `OOMKilled true`・137）。`docker compose stop` で止めたときは 143（今回の確かめで見た値） |
| 止まる直前の出力 | `docker compose logs --tail 200 app` | JVM が止まるときの「Terminating due to java.lang.OutOfMemoryError」の行は、logback を通らない標準エラーの出力で、JSON ではない。Loki には届かない見込みで、コンテナのログでだけ見る（今回は尽きることそのものを起こしていないため、行の文は JVM の決まりの文として書いた。`Unverified`） |
| 止まる前のヒープ | Explore（Prometheus）で `sum(jvm_memory_used_bytes{service_name="mastersmith",area="heap"}) / sum(jvm_memory_max_bytes{service_name="mastersmith",area="heap"})` を止まった時刻の前後で見る | 急に尽きたか、じわじわ増えたか。`ms-heap` は 85% が 5 分続いたときだけ鳴るため、急に尽きる場合は鳴らない |
| 止まった時刻 | Explore（Prometheus）で `timestamp(process_uptime_milliseconds{service_name="mastersmith"})` | 指標が最後に届いた時刻（今回の `docker compose stop` の確かめでは、止める直前の送信が最後で、止めた時刻と同じだった。JVM が自分で止まるときは、直前の周期の送信が最後になりうる） |
| 起動し直し | 原因を見てから `docker compose up -d --wait` | README の手順のとおり。尽きた操作（大きな権限の YAML の確かめ・適用、DSL の投入など）を同時に重ねていないかを、同じ時間のログの道で見る |

- 起動し直す前に、`docker compose logs app` を残しておきます（作り直すとコンテナのログは消える）。
- 配備したアプリの外部エクスポートは有効にしていないため、配備したアプリが止まっても、今は手元の監視の警報は鳴りません。気づくのは画面の表示です（`slo-config.md`・`anomaly-config.md`）。

## 5. 監査ログの確かめ（使い捨ての環境）

使い捨てのアプリを止めてから（Q5: A の手順を兼ねる）、内部DB のファイルをホームの下の一時の置き場（権限 700）へ複写しました。複写を読み取り（`jdbc:h2:file:...;ACCESS_MODE_DATA=r`）で開き、件数だけを数えています（値は出していない）。問い合わせ：

```sql
SELECT event_type, result, COALESCE(failure_reason,'-') AS reason, COUNT(*) AS n,
       COUNT(actor_user_id) AS actor, COUNT(target_user_id) AS target, COUNT(trace_id) AS trace
  FROM audit_events GROUP BY event_type, result, failure_reason ORDER BY event_type, result, reason;
```

結果と、送った要求から見込んだ件数の比べ（この Intent の 13 種類のうち、作れたもの）：

| 種類 | 結果 | 理由 | 件数 | 見込み（出し方） | 操作した人・対象・トレースID のある行 |
|---|---|---|---|---|---|
| GROUP_CREATED | SUCCESS | — | 16 | 16（13 回＋`obs-base`・`obs-dup`・確かめの `obs-probe`） | 16・0・16 |
| GROUP_CREATED | FAILURE | GROUP_NAME_DUPLICATE | 13 | 13（409） | 13・0・13 |
| GROUP_RENAMED | SUCCESS | — | 13 | 13 | 13・0・13 |
| GROUP_DELETED | SUCCESS | — | 13 | 13 | 13・0・13 |
| GROUP_MEMBER_ADDED | SUCCESS | — | 14 | 14（13 回＋用意の1件） | 14・14・14 |
| GROUP_MEMBER_REMOVED | SUCCESS | — | 13 | 13 | 13・13・13 |
| ROLE_CREATED | SUCCESS | — | 16 | 16（13 回＋用意の3件） | 16・0・16 |
| ROLE_CREATED | FAILURE | ROLE_NAME_DUPLICATE | 13 | 13（409） | 13・0・13 |
| ROLE_RENAMED | SUCCESS | — | 13 | 13 | 13・0・13 |
| ROLE_DELETED | SUCCESS | — | 13 | 13 | 13・0・13 |
| ROLE_PERMISSION_CHANGED | SUCCESS | — | 16 | 16（13 回＋用意の3件。権限の YAML の適用はこの種類に入らない） | 16・0・16 |
| ROLE_ASSIGNED | SUCCESS | — | 29 | 29（利用者 13＋グループ 13＋用意の3件） | 29・15・29（対象の利用者はグループへの割り当てでは空） |
| ROLE_UNASSIGNED | SUCCESS | — | 26 | 26（利用者 13＋グループ 13） | 26・13・26 |
| WORK_ROLE_SWITCHED | SUCCESS | — | 13 | 13（切り替えの 204。13 回とも今の作業ロールと違うロールへの切り替えだった） | 13・13・13 |
| ROLE_TRANSFER_APPLIED | SUCCESS | — | 1 | 1 | 1・0・1 |
| ROLE_TRANSFER_APPLIED | FAILURE | NO_CHANGE | 1 | 1（変える点の無い YAML の適用、409 `ROLE_NO_CHANGE`） | 1・0・1 |

- **すべて見込みと一致しました**。種類と理由の名前は `AuditEventType.java`・`AuditFailureReason.java` のとおりです。どの行もトレースID を持っていました。
- そのほかの種類: `ACCESS_DENIED`/`NOT_ADMIN` 26（入口の 403。L2 と一致）・`LOGIN_SUCCEEDED` 31・`DSL_SUBMITTED` 1・`DSL_APPLIED` 1・`INITIAL_ADMIN_CREATED` 1。表の行は全部で 283 行でした。
- 読み取り（GET）・404（存在しない ID の読み取り）・置き場の 403 と 400・権限の YAML の書き出しと確かめは、監査に残りませんでした。group・role の `monitoring-design.md` 4節と role の `observability-design.md` 1節のとおりです（どの種類にも当たる行が無い）。
- 作らなかった理由: `GROUP_IN_USE`・`ROLE_IN_USE`・`PERMISSION_TARGET_NOT_IN_DSL`・`DSL_NOT_APPLIED`・`ROLE_NOT_ASSIGNED`・`ROLE_TRANSFER_STALE`。存在しない ID への書き込み（`GROUP_NOT_FOUND`・`ROLE_NOT_FOUND` の行）も作っていません。今回の 404 は読み取りで送ったためです。どれも結合テスト（`RoleAuditIT`・`GroupAuditIT` など、Build and Test で Met）を正とします。
- 配備した環境の監査を見るときは、README の「監査ログの確かめ方」の手順（アプリを止めてボリュームを複写し、複写を読み取りで開く）で、上と同じ問い合わせを流します。この Intent の操作は `WHERE event_type LIKE 'GROUP_%' OR event_type LIKE 'ROLE_%' OR event_type = 'WORK_ROLE_SWITCHED'` で絞ります。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/observability-setup-questions.md`（Q2: A・Q4: A・Q5: A、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/log-queries.md`
- 基盤の設計: `construction/group/infrastructure-design/monitoring-design.md`（1・4・6節）、`construction/role/infrastructure-design/monitoring-design.md`（1・4・6節）、`construction/navigation/infrastructure-design/monitoring-design.md`（2・4・6節）と各単位の `infrastructure-specification.md`
- NFR の設計: `construction/group/nfr-design/observability-design.md`・`reliability-design.md`・`security-design.md`・`performance-design.md`、`construction/role/nfr-design/` と `construction/navigation/nfr-design/` の同じ文書
- `backend/src/main/java/cherry/mastersmith/common/persistence/RowLockFailures.java`・`role/service/RoleTransferSlot.java`・`common/error/web/GlobalExceptionHandler.java`・`access/web/AdminAccessDeniedHandler.java`・`audit/domain/AuditEventType.java`・`AuditFailureReason.java`、`Dockerfile`（`ExitOnOutOfMemoryError`）、`compose.yaml`（`restart: "no"`）、`README.md`（「監査ログの確かめ方」・コンテナの資源の上限）
- 実行したコマンドの出力:
  - `docker exec mastersmith-lgtm-1 curl -s localhost:3100/loki/api/v1/query?...`・`query_range?...`
  - `docker logs mastersmith-perf-app-1 | grep -c ...`
  - `docker inspect mastersmith-perf-app-1 --format ...`
  - `java -cp h2-2.4.240.jar org.h2.tools.Shell -url "jdbc:h2:file:...;ACCESS_MODE_DATA=r"`（使い捨ての環境の複写）

## Assumptions & Open Questions

- JVM が止まるときの1行の文と、それが Loki に届かないことは、尽きることを起こしていないため `Unverified` です（Q5: A で、尽きることを起こす確かめは performance-validation の `roleTransferLarge` と Build and Test の測りに任せた）。
- BUSY の行と排他の口の WARN の結び付きは、performance-validation で見ます（`Unverified`）。
