## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T07:57:05Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-specification.md > 2.3 誤った値の扱い | 新しい2つの設定（`reference`・`reference-read-total`）の 1 秒未満の値は `Invalid` になり、参照だけでなく対象DB 全体（一覧・詳細・書き込み・DSL の生成と照合）が使えなくなる。既存の時間の設定と同じ扱いで設計どおりだが、任意の調整用の設定の打ち間違いが影響する範囲が広い。復旧の手順は書かれているが、配備の確認（cicd-pipeline.md 3節の順 5）の「点検の WARN が無いこと」だけが検知の手段で、警報はない | 配備の手順書とスモークテストで、点検の WARN の項目名の確認に加え、既存の業務データの一覧が 200 で返ることを必ず確かめる、と明記する。警報を足さない判断は維持してよい | New |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/monitoring-design.md > 2節 ダッシュボード（パネル 46・47） | 既存パネル 40・42 の式は `uri=~"/api/tables/.*"` で、新しい 2 本の道（`/api/tables/definition`・`/api/tables/reference/rows`）をすでに合計している（式を実ファイルで確認）。追加の 46・47 は参照の量を分けて見せる点で有効だが、定義の道は `VIEW` を含み、ピッカーの検索とラベルの1件読みも同じ道のため、「参照だけの失敗」とは言い切れない。また `uri` ラベルの値は新しい道がまだ無いため未確認（6節の順 1 で確認予定） | パネルの題または説明に「定義の取得は `VIEW` を含む」を明記したまま、Observability Setup で `uri` の実際の値を確かめてから式を確定する。確認前は式を仮のものとして扱う | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-specification.md > 4.1 使い捨てのアプリ・上流との差 | 読み取りのプール 5 は一時の環境ファイルでだけ渡す設計で、`perf/README.md` と `docker/perf/compose.yaml` には 5 の指定が無い（`application.yaml` の既定は 10 であることを確認）。書き忘れると既定の 10 で流れ、プールが尽きる形の確かめ（NFR2.3）が成り立たないまま合格しうる。確かめは流す前の `hikaricp.connections.max` の読み取りに頼る | Code Generation で `perf/README.md` に 5 の行を手順として入れるとき、`hikaricp.connections.max` が 5 でなければ場面を流さない（中止する）条件を手順に明記する。比べる相手（5 で流した Intent 261016）の選び方は Build and Test で依頼者に確かめる、という既存の記述を維持する | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りでの突き合わせ（`application.yaml` の `query-timeout` 配下） | 既存は `generate`・`compare`・`list`・`detail` の 4 つ + `write`。コメントの「4つの上限」は設計の「5つの上限」への直しと整合。`TargetDbProperties.QueryTimeout` は 5 項目（`write` を `longest()` に含めない） | 足す `reference` の置き場所（`detail` と `write` の間）と `longest()` への追加は整合 |
| `.env.example` | 時間の設定の行と「読み取りの全体の上限は無い」の文言が実在する | 設計が書く編集箇所と一致 |
| ダッシュボードの実ファイル | 最終の区画は行 id 39 とパネル 40〜45（最大 id 45、最下段 y=98 h=8）。指標名は `http_server_requests_milliseconds_*`、ラベルは `service_name`・`method`・`uri`・`status` | 新しいパネル id 46・47（y=106）は衝突せず、式の名前も実在 |
| 警報 `ms-biz-target-db` | `uri=~"/api/tables/.*"`・`status="503"`・`for: 0s` | 新しい 2 本の道（`BusinessDataPaths.BASE` = `/api/tables`）を数える。新しい警報は不要という判断は妥当 |
| CI・配備 | `ci.yml` は変えない設計。E2E は `businessdata` の `src/main` に触れるため統合の前に流す（team.md の決まりどおり）。戻しはイメージのみで、移行も保存も無い | 問題なし |
| traceability の Deferred | アプリの中の作りの項目は Code Generation（`./gradlew verify` で確かめ）、測定の項目は Performance Validation。両段とも aidlc-state.md の実行予定にある | 持ち主は妥当 |

### Summary

U1 の基盤の設計は、既存の設定の形・ダッシュボードの実ファイル・警報の式と整合しており、新しい依存・接続・移行を足さない範囲に収まっていて、設計から実装できる。指摘は、設定の打ち間違いの影響範囲の確認、パネルの `uri` の確認、perf のプール 5 の確実な適用に関する軽微な点だけで、いずれも後続の段で手当てできる。
