# トレース（tracing-config）

前の Intent の記録（`aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/tracing-config.md` と、それが正とする前々回までの記録）を正とします。**トレースの設定は変えていません**。
- Micrometer Tracing、`MASTERSMITH_TRACING_SAMPLING_PROBABILITY` の既定 1.0、W3C の伝え方
- この Intent で新しいスパン・属性は無い（group・role・navigation の `monitoring-design.md` 4節）

## 1. 今回の確かめ（使い捨ての環境、Q1: A）

- Tempo に、区画のすべての道のトレースが届きました。直近 40 分の検索（上限 1,000 件）で、根のスパンの名前は道の型（`{groupId}`・`{roleId}`・`{userId}`）です。主な件数は次のとおりです。
  - `http get /api/me/table-access` 39・`http post /api/admin/roles` 29・`http post /api/admin/groups` 29・`http post /api/admin/roles/{roleId}/assignments` 29
  - `http get /api/me/navigation` 14・`http put /api/me/work-role` 13
  - `http post /api/admin/role-transfer/check` 2・`/apply` 2・`http get /api/admin/role-transfer/export` 1
- 入口で拒否した 403 のトレースは、根のスパンの名前が `http get`（道の型なし）で 26 件ありました。指標の `uri="UNKNOWN"` と同じ扱いです（`dashboards.md` 2節）。
- 次の道ごとに、トレースを最大 5 件ずつ取りました（合わせて約 180KB）。
  - 置き場、権限の YAML の確かめ・適用、権限の保存
  - グループの作成、作業ロールの切り替え、業務のメニュー、入口の 403

  スパンの属性のキーは、`uri`・`method`・`status`・`outcome`・`exception`・`http.url` と Spring Security の決まりのキー（`spring.security.*`）だけでした。`spring.security.authentication.authorities` は 34 件とも `[]` でした。
- **漏えい**: 上のトレースで、次の文字の一致はどれも 0 件でした。

  `@example.test`・`obs-`・`OBS`（スキーマ名）・`eyJ`（JWT の形）・`Bearer `・`schema=`・`table=`・`password`・`fingerprint`・`X-Role`

  `T2`（テーブル名）の一致 2 件は、どちらもスパンの ID（base64）の中の文字でした。
  - **置き場の `http.url` は5件とも `/api/me/table-access` だけ**で、問い合わせの部分（スキーマ名・テーブル名）を含みません（navigation の `monitoring-design.md` 4節、`NavigationQueryExposureIT` の決まりどおり）。
  - 権限の YAML の確かめ・適用の `http.url` は道だけで、本文（ロールの名前）と指紋のヘッダーを含みません。
- 権限の保存の `http.url` は `/api/admin/roles/12/permissions` のように**ロールの ID（数）を含みます**。前の Intent の利用者の管理の道と同じ扱いです。ID は内部の番号で、名前・個人に関する値は含みません。スパンの名前は道の型です。

## 2. 要求1件の追い方

1. Grafana の Explore（Loki）で、`log-queries.md` 2節の L1・L5・L9 から気になる行（例: `code="ROLE_BUSY"`、置き場の `ACCESS_DENIED`）を開く。
2. 行の `trace_id` から Tempo のトレースを開き、時間と状態を見る。BUSY のときは、同じ `trace_id` で排他の口の WARN（L3・L4）も絞れる。
3. 監査の行の `trace_id` は、同じ要求のログの `trace_id` と一致します。今回の監査の行は、この Intent の種類もすべて `trace_id` を持っていました（`log-queries.md` 5節）。置き場の 403 は監査に残らないため、ログとトレースだけで追います。

## 3. 外部エクスポート（決まっていること）

- 配備したアプリの外部エクスポートは**有効にしていません**。配備した `.env` は開いておらず、アプリも作り直していません。
- この段では、使い捨てのアプリにだけ、ホームの下の一時の環境ファイル（権限 600）で `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318` を渡しました。lgtm は使い捨ての環境の網に別名 `lgtm` でつなぎました（`docker network connect --alias lgtm mastersmith-perf_default mastersmith-lgtm-1`。終わったら外した。`alarms.md` 6節）。
- 使い捨てのアプリは、lgtm を網につなぐ前に起動していました。そのため、ログの送信の失敗の ERROR が 1 件（`io.opentelemetry.exporter.internal.http.HttpExporter`、`UnknownHostException: lgtm`）出ました。指標の送信は、網につないだ後に届いています。前の Intent でも同じ手順で同じ種類の失敗（WARN）が出ています。次からは、網につないでからアプリを起動し直す順にします。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/observability-setup-questions.md`（決まっていること、Q1: A）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/tracing-config.md`
- 基盤の設計: `construction/group/infrastructure-design/monitoring-design.md`（4節）・`infrastructure-specification.md`、`construction/role/infrastructure-design/monitoring-design.md`（4節）・`infrastructure-specification.md`、`construction/navigation/infrastructure-design/monitoring-design.md`（4節）・`infrastructure-specification.md`
- NFR の設計: `construction/navigation/nfr-design/security-design.md`・`observability-design.md`・`performance-design.md`・`reliability-design.md`、`construction/group/nfr-design/` と `construction/role/nfr-design/` の同じ文書
- `backend/src/main/resources/application.yaml`（`management.tracing`・`mastersmith.observability.export`）、`docker/perf/compose.yaml`
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:3200/api/search?...`・`localhost:3200/api/traces/<id>`（名前と属性のキーと一致の件数だけを見た）

## Assumptions & Open Questions

None.
