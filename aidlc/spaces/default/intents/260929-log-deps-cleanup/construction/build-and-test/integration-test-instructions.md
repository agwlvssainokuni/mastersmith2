# 結合テストの手順（260929-log-deps-cleanup）

Test Strategy は Minimal のため、新しい結合テストの手順は作らない。この Intent で足した・直した結合テストと E2E の流し方だけを書く。

## この Intent で関わる結合テスト

| テスト | 確かめること | 流し方 |
|---|---|---|
| `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java` | 初期管理者の作成の標準出力のログに、メールアドレスそのものが無く、キー `maskedEmail` の伏せ字があること（FR1.1、NFR1） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.service.InitialAdminIT'` |
| `backend/src/test/java/cherry/mastersmith/common/observability/OtlpLogExportIT.java` | 外部エクスポートでキー `email` が送られない（0 件）こと、`maskedEmail` が伏せ字のまま送られること（G1: A） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.common.observability.OtlpLogExportIT'` |

どちらも colima の前提と `build-instructions.md` の環境変数が要る。

## E2E（Playwright、verify と CI の外）

- 全体: `./gradlew e2eTest`（Mailpit を compose の profile で起動してから。終わったら止めて消す）。
- 100 だけ: `unit-test-instructions.md`（コード生成の段）の 2.4 の手順。
- 画面に関わる変更（make-you-chic-ui の固定先の更新）を統合する前に手元で流す（`team.md` の Testing Posture）。この Intent ではコード生成の段で流した（110 件すべて成功）。
