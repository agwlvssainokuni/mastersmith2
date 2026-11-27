# Code Generation Plan Approval

AI-DLC writes this file when it asks you to approve the plan. To answer here
instead of in chat, write your answer after `[Answer]:` and tell me in chat when you are done.

## Plan Approval

Approve the code plan for u2-portable-sql?

- Builds: H2 にしか通らない書き込みの文 3 つ（`savePreferences` の `FINAL TABLE`、作業ロールの保存とプレビューを置くの `MERGE ... KEY`）を、H2 と PostgreSQL の両方に通る書き方に直す。「あれば更新・無ければ挿入」の共通の手順（`common.persistence`）と、`RowLockFailures` への `55...
- Touches: `backend/src/main/java/cherry/mastersmith/common/persistence/`（新しい部品 4 つ + `RowLockFailures` への 1 行）、`user/repository/`（`UserRepository` から `savePreferences` を外し、カスタムのフラグメント 2 つを足す）、`role/...
- Tests: 単体テスト約 25 件（コンテナ無し。判定の表を含む。手順は本物の H2 の接続で動かす）、H2 の結合テスト約 10 件（同時の実行・文の数・既存の書き直し）、構造の検査 1〜2 件。PostgreSQL のテストは足さない（U3）
- 変えない: repository・store のメソッドの形（引数・戻り値）、`copyFromPreview`・Auth・招待の SQL、利用者の検索、移行、`application.yaml`、`ci.yml`・`build.gradle.kts`・依存・lockfile
- Already changed before you approved (2 files): backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslAppliedRevisionRecord.java, backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslPreviewRecord.java.

Full plan: aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/code-generation/code-generation-plan.md
Test instructions: aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/code-generation/unit-test-instructions.md

[Approval Fingerprint]: sha256:v3:0a9cc9ab1896a9e61bfcbd9a316626483fc6d10314b659da4b6b1a074e8d6a4e
[Planned Source]: 1825dde6b9dbebf030c789a9a471f4c5f5174535177d18d2b87596cf860c2eef

- A. Approve Plan
- B. Request Changes
- C. I'll edit the files

[Answer]: A. Approve Plan
