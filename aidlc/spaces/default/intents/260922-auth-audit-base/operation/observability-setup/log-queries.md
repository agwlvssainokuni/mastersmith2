# ログと監査ログの見方（log-queries）

Intent `260922-auth-audit-base`（auth-audit-foundation）の、アプリのログと監査ログを調べる方法。入力は、U1〜U4 の `construction/<単位>/infrastructure-design/monitoring-design.md`（monitoring-design）と `infrastructure-specification.md`（infrastructure-specification）、`construction/<単位>/nfr-design/performance-design.md`（performance-design）・`security-design.md`（security-design）・`reliability-design.md`（reliability-design）である。決定は `observability-setup-questions.md`（Q1〜Q7）にある。

## 1. 監視のコンテナを使わないとき（`docker compose logs`）

アプリのログは標準出力に1行1件の JSON で出る（U1 の monitoring-design 4章）。`jq` で絞り込む。

| 見たいもの | 命令 |
|---|---|
| ERROR の一覧 | `docker compose logs --no-log-prefix --no-color app \| grep '^{' \| jq -c 'select(.level=="ERROR") \| {timestamp,logger,message}'` |
| 監査の書き込みの失敗（U4-NFR10.5） | `docker compose logs --no-log-prefix --no-color app \| grep -c '監査イベントの記録に失敗しました'` |
| 監査の書き込みの遅れ | 同上で `監査イベントの記録に時間がかかりました` |
| 1つの要求のログ | `... \| grep '^{' \| jq -c 'select(.traceId=="<トレースID>")'` |
| アカウントのロック | 同上で `アカウントをロックしました` |
| 応答をエラーに変えた要求の内訳 | `... \| grep '^{' \| jq -r 'select(.message=="要求をエラー応答に変換しました") \| .code' \| sort \| uniq -c` |

外部エクスポートを有効にしているときは、JVM の警告（`sun.misc.Unsafe`）の数行が JSON でないため、`grep '^{'` で除く。

## 2. 監視のコンテナを使うとき（Grafana の Explore、Loki）

Loki には、ロガーの名前（`scope_name`）・レベル（`severity_text`）・文言・トレースID（`trace_id`）が入る。キーと値の項目（`code` など）は入らない。

| 見たいもの | LogQL |
|---|---|
| WARN 以上 | `{service_name="mastersmith"} \| severity_text=~"WARN\|ERROR"` |
| 監査の書き込みの失敗の件数（1 時間） | `sum(count_over_time({service_name="mastersmith"} \|= "監査イベントの記録に失敗しました" [1h]))` |
| ロックの件数（1 時間） | `sum(count_over_time({service_name="mastersmith"} \|= "アカウントをロックしました" [1h]))` |
| 1つの要求 | `{service_name="mastersmith"} \| trace_id="<トレースID>"`（Tempo のトレースへも移れる） |

起動より前のログ（Spring の起動のログ）は送られない（実測: 送られたのは起動の後の 11 件）。起動の失敗は `docker compose logs` で見る。

## 3. 監査ログから数える（U2-NFR10.7・U3-NFR10.5）

監査ログを見る画面・API は無い。アプリを止めてボリュームを複写し、複写を読み取りで開く（README の「監査ログの確かめ方」、U4 の monitoring-design 4章）。複写はリポジトリの直下に作る（Git の無視の対象。colima の VM から見える場所であるため）。

```sql
SELECT CAST(occurred_at AT TIME ZONE 'Asia/Tokyo' AS DATE) AS day_jst,
       SUM(CASE WHEN event_type = 'LOGIN_FAILED' THEN 1 ELSE 0 END) AS login_failed,
       SUM(CASE WHEN event_type IN ('LOGIN_FAILED', 'LOGIN_SUCCEEDED') THEN 1 ELSE 0 END) AS login_attempts,
       CAST(100.0 * SUM(CASE WHEN event_type = 'LOGIN_FAILED' THEN 1 ELSE 0 END)
            / NULLIF(SUM(CASE WHEN event_type IN ('LOGIN_FAILED', 'LOGIN_SUCCEEDED') THEN 1 ELSE 0 END), 0) AS DECIMAL(5, 1)) AS failed_pct,
       SUM(CASE WHEN event_type = 'ACCESS_DENIED' THEN 1 ELSE 0 END) AS access_denied
FROM audit_events GROUP BY day_jst ORDER BY day_jst;
```

実行の例: `java -cp <h2-2.4.240.jar> org.h2.tools.Shell -url "jdbc:h2:file:<展開した場所>/mastersmith;ACCESS_MODE_DATA=r" -user sa -password "" -sql "<上の SQL>"`

| 目安 | 超えたとき |
|---|---|
| ログインの失敗の割合: 1 日 30% 以下（U2 の monitoring-design 1章） | 同じメールアドレス・接続元IP の失敗が続いていないかを、`entered_email`・`source_ip` で確かめる（値は画面に出さない場所で見る） |
| アクセス拒否: 1 日 数十件以内（U3 の NFR1.5） | 利用者と接続元IP を確かめる |

2026-09-23 の実行の結果は `slo-config.md` 4節。

## 4. 定期の確認（U4 の monitoring-design 5章を引き継ぐ）

| 確かめること | 担当 | 頻度 | 方法 |
|---|---|---|---|
| 書き込みの失敗と遅れ | 依頼者 | 週に1回、および配備のとき | 1節の命令、または監視のコンテナを起動して警報の一覧を見る |
| 記録の量（U4-NFR1.4・U1-NFR1.9） | 依頼者 | 月に1回、およびバックアップのとき | `docker system df -v` でボリューム `mastersmith_mastersmith-data` の大きさ（2026-09-23: 69.63kB、31 件）を見て、見積もり（1年 約 180MB、索引を含め 250MB 程度）の2倍を超えていないかを確かめる |
| ログインの失敗の割合・アクセス拒否の件数 | 依頼者 | 月に1回 | 3節 |

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/observability-setup-questions.md`（Q1〜Q7、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `compose.yaml`、`docker/monitoring/`、`README.md` の「手元の監視（Grafana）」
- 2026-09-23 12:43〜12:57 に手元で実行した `docker compose`・Grafana の API・Prometheus と Loki の問い合わせ・H2 の Shell の出力

## Assumptions & Open Questions

- 本段の確認のために送った未ログインの要求（`/api/admin/check` 20 件）は、監査ログに ACCESS_DENIED として残っている。消す仕組みは無い（監査ログは追記だけ）。
