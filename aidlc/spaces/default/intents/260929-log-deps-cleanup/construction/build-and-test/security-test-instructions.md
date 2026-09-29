# セキュリティの検査の手順（260929-log-deps-cleanup）

## 統合の前の検査（`./gradlew verify` の中）

| 検査 | 道具 | 止める基準 |
|---|---|---|
| 秘密情報の検出 | Gitleaks（`gitleaksScan`） | 見つかったら失敗 |
| Java の静的解析 | SpotBugs＋FindSecBugs（`spotbugsGate`。プラグイン 6.5.12） | priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず失敗 |
| 依存関係の脆弱性 | OSV-Scanner（`osvScan`） | 実行時の依存は High 以上、開発時は `config/npm-build-tools.txt` の道具と `MAL-` で失敗 |

## この Intent で関わるセキュリティのテスト

- メールアドレスの漏えい（FR1、`project.md` の Forbidden）: `InitialAdminInitializerTest`（単体）・`InitialAdminIT`（標準出力）・`OtlpLogExportIT`（外部エクスポート）。流し方は `integration-test-instructions.md`。
- 伏せ字の形（FR1.2）: `EmailAddressTest`（例のテストと jqwik の性質ベースのテスト）。

## 既知の例外（確かめの対象の外）

- 監査の記録の失敗の ERROR のキー `enteredEmail` は、メールアドレスそのものを載せる（要件のレビューの R-01 を受けた依頼者の決定で FR2 を行わない。前の Intent の U4 の決定のまま）。外部エクスポートでは `[REDACTED]` になる。
- `UserAccountService.existsByEmail(String)` の引数は、TraceAspect の TRACE のログ（既定では出ない）にそのまま出うる（コード生成の計画の 7節）。

## Dependabot と脆弱性の知らせ

- Jackson（`tools.jackson:jackson-bom`）はすべての版を Dependabot の ignore にした。Jackson の脆弱性には、`./gradlew verify` と CI の OSV-Scanner でだけ気づく（R-02・R-05）。
