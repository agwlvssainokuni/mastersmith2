# トレース（tracing-config）

前の Intent の記録（`aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/tracing-config.md` と、それが正とする `260922-auth-audit-base` の記録）を正とします。**トレースの設定は変えていません**（Micrometer Tracing、`MASTERSMITH_TRACING_SAMPLING_PROBABILITY` の既定 1.0、W3C の伝え方。新しいスパン・属性は無い。U3 の `monitoring-design.md` 4節）。

## 1. 今回の確かめ（使い捨ての環境、Q1: A）

- Tempo に、利用者の管理の7つの API のトレースが届きました。直近 30 分の検索（上限 500 件）で、根のスパンの名前ごとに次の件数です: `http get /api/admin/users` 168・`http post /api/admin/users/{userId}/grant-admin` 119・`.../revoke-admin` 123・`.../suspend` 27・`.../resume` 18・`.../reset-login-failures` 18・`http put /api/admin/users/{userId}/profile` 27（上限に届いたため、件数は届いた全部ではない）。
- スパンの名前は道の型（`{userId}`）で、利用者 ID を含みません。印を付ける要求の1件のトレースは5つのスパン（`http post ...`・`security filterchain before`・`secured request`・`authorize request`・`security filterchain after`）で、属性のキーは `uri`・`method`・`status`・`outcome`・`exception`・`http.url` と Spring Security の決まりのキーだけでした。
- 入口で拒否した 403 のトレースは、根のスパンの名前が `http get`（道の型なし）で 219 件ありました。指標の `uri="UNKNOWN"` と同じ扱いです（`dashboards.md` 2節）。業務の層の 403 は `http post /api/admin/users/{userId}/grant-admin` の名前で 33 件でした。
- **漏えい**: 一覧のトレース 40 件（約 208KB、検索の文字を送ったものを含む）と印を付けるトレース 1 件で、`@example.test`・`perf-`・`eyJ`（JWT の形）・`Bearer `・`password`・`?q=`・`q=perf`・`displayName` の一致は、どれも 0 件でした。一覧のトレースの属性 `http.url` は 40 件とも `/api/admin/users` だけで、URL の問い合わせの部分（検索の文字 `q`）を含みません（U3 の BR7.4 のとおり）。

## 2. 要求1件の追い方

1. Grafana の Explore（Loki）で、`log-queries.md` 2節の L1・L3 から気になる行（例: `code="USER_ADMIN_BUSY"`）を開く。
2. 行の `trace_id` から Tempo のトレースを開き、時間と状態を見る。同じ `trace_id` で排他の口の WARN（L4）も絞れる。
3. 監査の行の `trace_id` は、同じ要求のログの `trace_id` と一致します（今回の監査の5つの種類の行は、すべて `trace_id` を持っていた。`log-queries.md` 3節）。

## 3. 外部エクスポート（Q4: A）

- 配備したアプリの外部エクスポートは**有効にしていません**。配備した `.env` は開いておらず、アプリも作り直していません。
- この段では、使い捨てのアプリにだけ、ホームの下の一時の環境ファイル（権限 600）で `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318` を渡しました。lgtm は使い捨ての環境の網に別名 `lgtm` でつなぎました（`docker network connect --alias lgtm mastersmith-perf_default mastersmith-lgtm-1`。終わったら外した。`alarms.md` 5節）。
- 使い捨てのアプリの最初の起動は lgtm を網につなぐ前だったため、指標の送信の失敗の WARN（`OtlpMeterRegistry`、22:19:47）が 1 件出ました。つないだ後の起動では出ていません。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/observability-setup-questions.md`（決まっていること、Q1: A・Q4: A）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/tracing-config.md`
- U3 の `construction/u3-user-admin-api/infrastructure-design/monitoring-design.md`（4節）・`infrastructure-specification.md`、`construction/u3-user-admin-api/nfr-design/security-design.md`・`observability-design.md`・`performance-design.md`・`reliability-design.md`
- `backend/src/main/resources/application.yaml`（`management.tracing`・`mastersmith.observability.export`）、`docker/perf/compose.yaml`
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:3200/api/search?...`・`localhost:3200/api/traces/<id>`（名前と属性のキーと一致の件数だけを見た）

## Assumptions & Open Questions

None.
