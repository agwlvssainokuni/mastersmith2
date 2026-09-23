# 要件の網羅の確かめ（cross-unit-traceability）

単位の分割の無い Intent のため、確かめる対象は段の単位の `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/code-generation/traceability.json` の1つだけである。User Stories の段は行っていないため、受け入れ条件（AC）は無い。

## 判定

**不合格（fail）**。要件の ID 24 件（親の FR1〜FR6 を含む）のうち、`OK` で網羅されていないものが 6 件（FR1.4、FR3.3、FR6、FR6.1、FR6.2、NFR2）ある。どれも、実装の漏れではない（下の「網羅されていない要素」を参照）。承認の関門で依頼者に示す。

## ID ごとの網羅

| ID | 状態 | 持ち主 | 対象のファイル（存在の確認） |
|---|---|---|---|
| FR1 | OK（子の FR1.1〜FR1.3 で網羅。FR1.4 は下を参照） | code-generation | — |
| FR1.1 | OK | code-generation | `backend/src/main/resources/application.yaml`（あり） |
| FR1.2 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/config/DataSourcePoolIT.java`（あり） |
| FR1.3 | OK | code-generation | `README.md`（あり） |
| FR1.4 | **N/A** | code-generation | 対象のファイル無し（プログラムを変えない要件）。`git diff --stat -- backend/src/main/java` が空であることを code-summary.md に記録 |
| FR2 / FR2.1 | OK | code-generation | `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/code-generation/code-summary.md`（あり） |
| FR3 | OK（子の FR3.1・3.2・3.4 で網羅。FR3.3 は下を参照） | code-generation | — |
| FR3.1 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/auth/web/ConcurrentLoginAuditIT.java`（あり） |
| FR3.2 | OK | code-generation | 同上（あり） |
| FR3.3 | **Deferred** | code-generation（Step 11） | traceability.json では未コミットのため Deferred。実際には、修正（`application.yaml`）と再現テスト（`ConcurrentLoginAuditIT.java`）が同じコミット `d948544` に入った（`git show --stat d948544` で確認） |
| FR3.4 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteBarrierConfig.java`（あり） |
| FR4 / FR4.1 | OK | code-generation | `backend/src/test/java/cherry/mastersmith/audit/service/AuditWriteFailureIT.java`（あり） |
| FR4.2 | OK | code-generation | code-summary.md（あり）。本段でも `verify` が通った |
| FR5 / FR5.1 | OK | code-generation | `README.md`（あり） |
| FR6 | **Deferred**（子の FR6.1・FR6.2 が Deferred） | deployment-execution | — |
| FR6.1 | **Deferred** | deployment-execution | 配備した後のスモークテスト |
| FR6.2 | **Deferred** | deployment-execution | 使い捨ての環境での k6 の同時 10 件のログイン |
| NFR1 | OK | code-generation | `ConcurrentLoginAuditIT.java`（あり） |
| NFR2 | **Deferred** | deployment-execution | 1g のコンテナでの起動と確かめ |
| NFR3 | OK | code-generation | `backend/build.gradle.kts`（あり） |
| NFR4 | OK | code-generation | `.env.example`（あり） |

## 網羅されていない要素

| ID | 理由 | 扱い |
|---|---|---|
| FR1.4 | 「プログラムを変えない」という要件で、対象のファイルが無い | 差分が空であることで満たしている（code-summary.md、`git diff`） |
| FR3.3 | Code Generation の成果物を書いた時点では未コミットだった | コミット `d948544` で満たした。traceability.json は承認済みの成果物のため書き換えていない |
| FR6.1・FR6.2（と親の FR6） | 配備した環境が要る | `deployment-execution` の段で行う（実行の計画に EXECUTE として入っている） |
| NFR2 | 配備した環境が要る | 同上 |

## Sources

- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/code-generation/traceability.json`
- `git show --stat d948544`
