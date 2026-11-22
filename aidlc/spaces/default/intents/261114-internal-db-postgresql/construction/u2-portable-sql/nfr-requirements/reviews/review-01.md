## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-22T03:52:37Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/performance-requirements.md > NFR1.1・NFR1.2 の確かめ方 | 「文の数が 1（行が無い最初の 1 回は 2）」を数える手段が決まっていない。既存の `ConnectionAcquireCounter` は HikariCP の `hikaricp.connections.acquire` の件数（接続を借りた回数）しか数えず、流した文の本数は数えられない。NFR1.2 は「同じ形」と書くが、文の数については該当する既存の部品が無い。NFR1.1 は「数えるか、コードの読み取り」と逃げ道を置いている。 | NFR1.2 の「文の数」の確かめ方を、(a) 接続を借りる回数は `ConnectionAcquireCounter`、(b) 文の数は新しい数え方（データソースのプロキシ等）かコードの読み取り、のどちらかに分けて書く。数え方を足すなら Code Generation の計画に載せる。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/observability-requirements.md > NFR4.8・NFR4.9 の由来 | 上流の NFR4 は「PostgreSQL で上限なく待たない」「移行が接続ごとの待ちの上限で失敗しない」であり、「運用の継続」「監査の内容を変えない」という文言は無い。由来が上流の文と合わない。移行の部分が U2 の対象外であることも書かれていない。 | 由来を「機能設計 BR2.4・BR2.7・FR5.4」などの実際の根拠に直し、上流 NFR4 の移行の部分は U1 の持ち分で、この単位では扱わないと 1 行書く。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/reliability-requirements.md > NFR4.5 と tech-stack-decisions.md > 実装の場所の選び方 | NFR4.5 は「例外の種類を変えない」と書く。一方、tech-stack-decisions.md は `doWork` に切り替えると例外の型が変わりうると書く（`RoleStore` は `PersistenceException` だけを受ける）。両者は矛盾はしないが、NFR4.5 が持ち越し事項に条件付きであることが読み取れない。 | NFR4.5 に「例外の型の保持の方法は NFR 設計で決める（tech-stack-decisions.md 参照）」と一言足し、設計への持ち越しであることをつなげる。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/performance-requirements.md > NFR1.2 の確かめ方 | `ConnectionAcquireCounter` は `appearance/testsupport` にある。プロジェクトの決まりでは、テストの手伝いは各機能の `<機能>/testsupport` に置く。`role`・`dslmanage` のテストが `appearance` の部品を参照すると、テストの機能間の依存が増える。 | 参照のままでよいか、`common` 側のテスト用の置き場へ移すかを、コード生成の計画で決めて記録する。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 上流 NFR1〜NFR9 と traceability.json の突き合わせ（Read） | 全 9 件が OK で、target の ID は各文書の ID と一致 | 漏れ・矛盾は見つからない。NFR3・6・8・9 は tech-stack-decisions.md、NFR2 は security と observability に分かれて載る |
| `RowLockFailures.java` の突き合わせ | ERROR_CODES は 50200・40001、SQL_STATES は HYT00・40001、3 つの型。`55P03` は未追加 | NFR4.7 の「H2 の分類を変えずに足す」は成り立つ。`RowLockFailuresTest` は存在する |
| `application.yaml` の突き合わせ | `org.postgresql: OFF`（365 行）と `org.hibernate.orm.jdbc.error: OFF`（388 行）がある | NFR2.4 の前提どおり |
| `UserRepository.savePreferences` の突き合わせ | `@Query` のネイティブで `FINAL TABLE (UPDATE ...)` の 1 文、`TraceAspect` の対象は `..repository..` | NFR1.1・NFR2.3 の前提と一致。`@Query` のインターフェースから `doReturningWork` を呼べないという持ち越しの指摘は正しく、tech-stack-decisions.md が書いている |
| `RoleStore.saveWorkRoleSelection` の突き合わせ | `MERGE INTO ... KEY` の `executeUpdate` を 1 文で流し、`PersistenceException` だけを受けて分類する | NFR1.2・NFR4.5 の前提どおり。例外の型を保つ必要があるという持ち越しの記述も正しい |
| 既存のテストの存在 | `WorkRoleSelectionMergeIT`・`PreferencesSecretLeakIT`・`RowLockFailuresTest`・`ConnectionAcquireCounter` が存在 | 確かめ方に挙げた名前は実在する。ただし `ConnectionAcquireCounter` は接続を借りた回数だけを数える |

### Summary

上流の NFR1〜NFR9 の派生に漏れ・矛盾は無く、確かめ方が参照するコード・テストも実在し、機能設計の審査の持ち越し（`doReturningWork` と `doWork` の flush・例外の型）も tech-stack-decisions.md が正直に書いている。反証を試みて見つかったのは、文の数を数える手段の不明、NFR4.8・NFR4.9 の由来の不一致などの Minor だけで、重い欠陥は無いため READY。
