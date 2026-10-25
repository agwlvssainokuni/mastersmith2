# ログと監査ログの見方（log-queries）

前の Intent の記録（`aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/log-queries.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。ここで扱うのは次のものです。

- 対象DB に届かない・答えないときの WARN（業務データの 503）
- 業務データの 403・409
- 業務データの監査（`BUSINESS_ROW_*`）

ログの収集の設定は変えていません（U1・U2 の `monitoring-design.md` 4節）。値はすべて、使い捨ての環境の実測（`measurement-notes.md` 9節・10節・11節）と、この段の確かめの回（`dashboards.md` 2.1）の件数です。

## 1. Loki に届く今回のログ（使い捨ての環境で確かめた）

| ロガー | 本文 | レベル | キー（Loki の構造化メタデータ） | 実測の件数（前回の2回目） |
|---|---|---|---|---|
| `BusinessDataReadService` | 対象DB の業務データを読めませんでした | WARN | `purpose`・`reason`・`sqlState`・`vendorCode`・`exceptionClass` | 192（503 の件数と同じ） |
| `BusinessDataWriteService` | 対象DB の業務データを書き込めませんでした | WARN | 上に `stage` を加えたもの | 96（503 の件数と同じ） |
| `GlobalExceptionHandler` | 要求をエラー応答に変換しました | WARN | `code`・`status` ほか（前の Intent の記録のとおり） | 164,916（409・403・503 の変換の行） |
| `com.zaxxer.hikari.pool.PoolBase` | 「Pool is empty…」「Failed to validate…」（ライブラリの文） | WARN | — | 29 |

- 状態の項目は `detected_level`・`severity_text`・`scope_name`（ロガー）・`trace_id`・`span_id` です。
- この段の確かめの回（PG を止めて 24 回）でも、読み取りの WARN 48・書き込みの WARN 24 で、503 の件数（読み取り 48・書き込み 24）と同じでした。
- **漏えい**（値を出さずに件数で数えた。`measurement-notes.md` 11節と、この段の確かめの回）:

| 数えたもの | 前回の3つのアプリ | この段のアプリ（1,037 行） |
|---|---|---|
| 試験用の利用者のメールアドレス（`perf-biz-`・`perf-roleop`・`perf-admin@`） | 0 | 0 |
| 種と送った行の値の目印（`目印`・`いろはにほへと`・`幅n`・`書き込みn`・`通常n` など） | 0 | 0 |
| `Bearer`・`accessToken` | 0 | 0 |
| 対象DB の接続先（`jdbc:postgresql`） | 0 | 0 |
| `jdbc:`（すべて） | 0 | 6（内部DB の `jdbc:h2` だけ。2回の起動ごとに HikariCP・Flyway・Hibernate の INFO が1行ずつ。資格情報を含まない） |
| `@example.test` を含む行 | 6 | 2（どれも初期管理者の INFO のキー `maskedEmail` の伏せ字） |

## 2. Explore（Loki）で使う問い合わせ

`<窓>` は見たい幅（例: `[1h]`）です。L1〜L3・L5 は、前回の使い捨ての環境で流し、`success` と件数を確かめました（窓は 02:37:16〜03:15 UTC）。

| ID | 見たいもの | LogQL | 確かめた値 |
|---|---|---|---|
| L1 | 読み取りの失敗の理由ごとの件数 | `sum by (reason) (count_over_time({service_name="mastersmith"} \|= "対象DB の業務データを読めませんでした" <窓>))` | `UNAVAILABLE` 192 |
| L1b | 同じく目的ごと | L1 の `by (reason)` を `by (purpose)` にしたもの | `LIST` 96・`DETAIL` 96 |
| L2 | 書き込みの失敗の理由・段ごとの件数 | `sum by (reason, stage) (count_over_time({service_name="mastersmith"} \|= "対象DB の業務データを書き込めませんでした" <窓>))` | `UNAVAILABLE`・`BORROW` 96 |
| L3 | 例外のクラスと SQLState（読み取り・書き込み） | L1・L2 の `by` を `exceptionClass` や `sqlState` にしたもの | どちらも `java.sql.SQLTransientConnectionException`。`sqlState` は 08001 が読み取り 190・書き込み 94、08003 が各 2 |
| L4 | 業務データの 403・409 の code ごとの件数 | `sum by (code, status) (count_over_time({service_name="mastersmith"} \|= "要求をエラー応答に変換しました" \| code=~"ACCESS_DENIED\|BUSINESS_.*" <窓>))` | 形は前の Intent の L1・L9 と同じ。この Intent では流していない（`Unverified`） |
| L5 | レベルとロガーごとの件数 | `sum by (detected_level, scope_name) (count_over_time({service_name="mastersmith"} \| detected_level=~"warn\|error" <窓>))` | 1節の表の件数。ERROR は 0 |

- **503 の見分け方**（警報 `ms-biz-target-db` が鳴ったとき）:
  1. L1・L2 で `reason` を見る。`UNAVAILABLE` は借りる待ちの時間切れ（接続を作れない・借りられない）、`TIMEOUT` は問い合わせの時間切れ（対象DB が答えない）。
  2. L2 の `stage` で、書き込みのどの段で失敗したかを見る（今回は `BORROW` だけ）。
  3. `docker compose ps targetdb-postgres` で対象DB の状態を見る。
  4. 気になる行の `trace_id` から Tempo で要求を追う（`tracing-config.md` 2節）。
- 実測では、`docker stop` と `docker pause` のどちらでも `UNAVAILABLE` だけで、`TIMEOUT` は 0 件でした（`measurement-notes.md` 6節）。`TIMEOUT` の行は結合テスト（ServerSocket の方式）を正とします。
- JDBC の例外のメッセージと行の値は、WARN にもエラー応答にも出ません。出るのは SQLState・業者のエラーの番号・例外のクラスの名前だけです（`project.md` の Forbidden、1節の件数）。

## 3. 業務データの 403 の見分け方（Q4: A）

| 403 の種類 | 指標の `uri` | アプリのログ | 監査 | 実測の件数 |
|---|---|---|---|---|
| 業務データの権限の 403（権限の無いテーブル・カラム・操作） | 道の型（`/api/tables/…`） | `GlobalExceptionHandler` の `ACCESS_DENIED`/403 | **残らない** | 534（6本の API で 89 件ずつ） |

- 鳴ったときの見方は `alarms.md` 4節です。業務データの 403 は、ダッシュボードの区画の要求の数（`status="403"`）と L4 で数え、利用者は `trace_id` から追います。

## 4. 業務データの監査（使い捨ての環境と、配備した内部DB の複写）

使い捨てのアプリを止めてから、内部DB を読み取り（`ACCESS_MODE_DATA=r`）で開き、件数だけを数えました（値は出していない）。問い合わせ:

```sql
SELECT event_type, result, COUNT(*) FROM audit_events GROUP BY event_type, result ORDER BY event_type, result;
SELECT COUNT(*) FROM audit_events;
```

| 種類 | 結果 | 書き込みの場面の後（前回の1回目） | 最後（前回の2回目の後） |
|---|---|---|---|
| `BUSINESS_ROW_CREATED` | SUCCESS | 262,657 | 262,984 |
| `BUSINESS_ROW_CREATED` | FAILURE | 371,544（409 の件数と一致） | 535,779 |
| `BUSINESS_ROW_UPDATED` | SUCCESS | 396,480 | 396,751 |
| `BUSINESS_ROW_DELETED` | SUCCESS | 20 | 291 |

- 403 に当たる種類の行は 0 件でした（業務データの 403 は監査に残らない）。
- 2回目の FAILURE の増え（164,235）のうち、何件が 409・503 かは分けていません（`failure_reason` で数えていない）。
- 配備した内部DB の複写（Deployment Execution の配備の後の複写）では、`audit_events` 280 行のうち `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED` が SUCCESS 1 件ずつ（スモークテストの3件）でした。
- 配備した環境の監査を見るときは、README の「監査ログの確かめ方」の手順（アプリを止めてボリュームを複写し、複写を読み取りで開く）で、`WHERE event_type LIKE 'BUSINESS_ROW_%'` を足した上の問い合わせを流します。種類と結果に加えて `failure_reason` でも分けると、409 と 503 を見分けられます。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/observability-setup-questions.md`（Q2: A・Q4: A・Q6: A、やり直したまとめの確認）
- この段の `measurement-notes.md`（6節・8節〜11節）・`dashboards.md`（2.1）・`alarms.md`（4節）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/log-queries.md`
- 基盤の設計: `construction/u1-businessdata-read/infrastructure-design/monitoring-design.md`（4節）・`construction/u2-businessdata-write/infrastructure-design/monitoring-design.md`（4節）
- `backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataReadService.java`・`BusinessDataWriteService.java`、`common/error/web/GlobalExceptionHandler.java`、`README.md`（「監査ログの確かめ方」）
- 実行したコマンドの出力:
  - `curl -s http://127.0.0.1:13000/api/datasources/proxy/uid/loki/loki/api/v1/query?...`・`query_range?...`（キーの名前と件数だけ）
  - `docker compose -p mastersmith-perf -f docker/perf/compose.yaml logs --no-log-prefix app`（ホームの下の一時の置き場に保存し、件数だけを数えて消した）
  - `java -cp h2-2.4.240.jar org.h2.tools.Shell -url "jdbc:h2:file:/data/mastersmith;ACCESS_MODE_DATA=r"`

## Assumptions & Open Questions

- L4 はこの Intent では流していません（形は前の Intent で確かめたもの。`Unverified`）。
- `TIMEOUT` の WARN の行は、使い捨ての環境では起こせませんでした（`Unverified`。結合テストを正とする）。
