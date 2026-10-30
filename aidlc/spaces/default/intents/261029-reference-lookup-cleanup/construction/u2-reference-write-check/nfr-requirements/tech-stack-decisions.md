# 技術の選択 — U2 u2-reference-write-check

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR9、前提）
- `aidlc/spaces/default/memory/team.md`（Code Style・静的解析とセキュリティ検査）と `project.md` の Tech Stack

## ID の対応表

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR9 供給網 | 当たる（新しい依存を足さない） | NFR9.21 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR9.21 | この単位は新しい依存（Gradle の依存・npm のパッケージ・コンテナのイメージ）を足さない。要件はすべて採用済みの部品で満たす。足す必要が出たときは、採用の前にライセンスを確かめ、Apache License 2.0 と違えば理由を ADR に残し、lockfile と OSV-Scanner の対象に載ることを確かめる | コード生成の差分で `backend/gradle.lockfile`・`libs.versions.toml` に増えた依存が無いこと | Code Generation |

## 使う技術（すべて採用済み）

| 用途 | 選択 | 理由 |
|---|---|---|
| 保存前の確かめの実装 | Java・Spring Boot の既存の `businessdata` の機能（業務処理の層） | 既存の作成・更新の判定の順に ⑩ を足すだけで、層の境界（ArchUnit）を変えずに済む |
| 参照先の読み取り | U1 の口（`check`・`countState`）と既存の `RowSqlExecutor`・`RowDialect` | SQL を組み立てる部品を増やさず、SpotBugs の `SQL_` の除外と境界テストを緩めない（`team.md`） |
| 時間の上限 | U1 が足す `ReadPurpose` の `REFERENCE` と `TargetDbProperties.QueryTimeout`、注入できる `Clock` | 全体の上限の仕組みを U1 と共有し、時刻をテストで動かせる |
| 結合テスト | Testcontainers（MySQL・MariaDB・PostgreSQL の版を固定したイメージ）、JUnit 5 | `project.md` の Mandated（別の DB やモックで代用しない） |
| 性質ベースのテスト | jqwik | `team.md` の決まり |
| 負荷の試験 | k6（既存の `perf/k6/scenarios.js` に場面を足す） | 既存の判定の決まりと使い捨ての環境を使い回す |
