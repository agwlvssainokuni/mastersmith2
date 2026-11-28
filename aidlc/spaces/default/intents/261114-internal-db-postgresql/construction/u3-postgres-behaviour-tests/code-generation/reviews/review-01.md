## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-28
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | backend/src/main の KeyedUpsertExecutor と RowLockFailures の SQLState の一覧（U2 の成果物）。この単位の記録は aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/code-generation/code-summary.md の「依頼者に確かめたいこと」1 | KeyedUpsertExecutor の中で行き詰まり（40P01）の犠牲になると、作業ロールの保存が 409 ROLE_BUSY ではなく想定外（500）になる。一覧に 40P01 が無いため。U3 は合否に入れず観測の行だけにしており、記録の書き方は正直だが、本体の差が残っている | 承認の場で、U2 に戻して一覧へ 40P01 を足す小さな直しにするか、既知の差として持ち越すかを依頼者が決める。持ち越すなら Build and Test か文書に差として残す。この単位のテストは変えなくてよい | New |
| R-02 | Minor | backend/src/test/java/cherry/mastersmith/internaldb/postgres/PgSecretLeakIT.java 起動の出力の検査 | 起動の出力に資格情報を含まない接続先 URL が出る（Flyway の INFO）。NFR 要件 NFR2.1・NFR 設計 DD-12 の「接続先の文字列が無い」とは読み方が違い、合否は利用者・パスワード・初期管理者の資格情報だけで、URL は観測。code-summary.md に上流との差として書いてあり、project.md の Forbidden（資格情報を含む URL）には当たらない | 承認の場でこの読み方を依頼者が確認する。受け入れなら上流の NFR 文言を直すか差の記録を維持する | New |
| R-03 | Minor | backend/src/test/java/cherry/mastersmith/internaldb/PgTestBoundaryArchitectureTest.java | 構造のテスト 6 件のうち、「注釈付きなのに名前が IT で終わらない」の検出と org.h2 の型への依存の検出は、わざと違反を入れて落ちることを確かめられていない（code-summary.md に明記済み）。他の規則は否定の確認済み | 承認の場で受け入れる。可能なら後の機会に、一時的な違反を置くテスト用の小さなクラスで確かめる | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/code-generation/traceability.json NFR5.1 | NFR5.1 は Deferred（Build and Test で CI の検査の job の時間を確かめる）。測っていない目標を OK にしておらず、扱いは正しい | 対応不要。Build and Test で CI の時間と全件の verify の時間を確かめる | New |
| R-05 | Info | PgKeyConflictIT (c)・PgFailureTransactionIT・PgDeadlockIT・PgTimeRoundTripIT | (c) は専用のデータベースの中で ck_dsl_previews_slot を一時的に外し、finally で行を消して制約を戻す。BR4.1 は同じトランザクションの 2 文（版の挿入 → プレビューの削除）に作り方を変えた。どちらも上流との差として記録済み。PgDeadlockIT は「ちょうど一方が 40P01 で終わる」と 10 秒以内を合否にし、犠牲の側は問わない。PgTimeRoundTripIT は JVM の時差 3 つ × セッションの時差 3 つ × 夏時間の境とマイクロ秒を確かめ、時計を進めて sleep を使わず、時差は AfterAll で戻して確かめる | 対応不要 | New |
| R-06 | Info | code-summary.md の件数 | 計画は IT 25 クラス（24 + 出力の検査 1）と構造のテスト 1。実際は部品の回帰の目印の PgSharedAppSmokeIT を足した 26 クラス・63 件と構造のテスト 6 件で、書かれた内訳と数が合う | 対応不要 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| ./gradlew :backend:test --tests 'cherry.mastersmith.internaldb.PgTestBoundaryArchitectureTest' | BUILD SUCCESSFUL in 3s | 構造のテストは通る |
| ./gradlew :backend:internalDbPostgresTest --tests '...postgres.PgTimeRoundTripIT'（colima の環境変数つき） | BUILD SUCCESSFUL in 16s | 時刻の 4 件が実際の PostgreSQL で通る |
| git status | 新規ファイルのみ（backend/src/test の下の 34 個と記録）。src/main・build.gradle.kts・ci.yml・lockfile・H2 の既存のテストに差分なし | 境界は守られている |
| 読み取りの検索 | 新規ファイルの Thread.sleep は ConflictProbe の 50 ms 間隔の観測だけ（合否に使わない）。メールアドレスは example.com 系のみ。新規の Java ファイルにライセンスヘッダーの欠けなし | 問題なし |
| 全件と verifyQuick | この確かめでは流していない。担当の結果が code-summary.md にある（76 件・BUILD SUCCESSFUL 5m 10s） | 記録を信頼し、再実行はしていない |

### Summary

計画の Step 1〜11 は実施され、BR1〜BR9 は対応するテストクラスに実在する。待ちの幅は呼び出しの開始から測り、sleep に頼らず、値や接続情報を出力に出さない確かめもある。差と作り方の変更は正直に書かれている。残る大きな論点は、この単位の外の本体にある 40P01 の分類の差で、承認の場で依頼者が決める。
