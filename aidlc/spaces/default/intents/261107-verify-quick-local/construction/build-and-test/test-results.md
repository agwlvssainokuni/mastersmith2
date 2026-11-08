# テストの結果（Intent 261107-verify-quick-local）

実行の場: 開発者の PC（macOS・colima、ブランチ `fix/261107-verify-quick-local`、先頭 `2eb72286`）。どの実行もシェルに `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -dims` で包んだ。`pmset -g log` で、最初の実行の開始（2026-11-08 12:38 JST）の前の 12:36 の Wake の後に Sleep・DarkWake が無いことを確かめた。実行の出力は `build/bt-261107/`（git の対象外）に置いた。

## 実行したコマンドと結果

| # | コマンド | 終了コード | 時間 | 結果 |
|---|---|---|---|---|
| 1 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 0 | 27分44秒 | BUILD SUCCESSFUL |
| 2 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=2` | 0 | 19分44秒 | BUILD SUCCESSFUL |
| 3 | `./gradlew :backend:cleanTest verifyQuick` | 0 | 3分7秒 | BUILD SUCCESSFUL |
| 4 | `node scripts/verify-graph-check.mjs` | 0 | — | 8 passed, 0 failed |
| 5 | `./gradlew e2eTest`（Mailpit と見本の PostgreSQL を手順どおりに起動。イメージの版とダイジェストは `compose.yaml` と一致） | 0 | 15分50秒 | 298 passed（Playwright、Chromium） |

Code Generation の段で流した確かめ（`verifyQuick -PitTests=AuditTraceIdIT`・`-PitTests=NoSuchClassIT`・コンテナに届かない状態の `-PitTests=DslTargetDbIT` など）は `aidlc/spaces/default/intents/261107-verify-quick-local/construction/code-generation/code-summary.md` にある。

## テストの件数

| 実行 | バックエンドの単体（`*Test`） | バックエンドの結合（`*IT`） | 画面（Vitest） |
|---|---|---|---|
| 1 直列の `verify` | 2,937（失敗 0・飛ばし 0） | 1,716（失敗 0・飛ばし 10） | 3,063 passed × 2 回（`frontendTest`・`frontendCoverage`） |
| 2 `verify -PitForks=2` | 2,937（失敗 0・飛ばし 0） | 1,716（失敗 0・飛ばし 10） | 3,063 passed × 2 回 |
| 3 `verifyQuick` | 2,937（作り直して流した） | 流さない | 3,063 passed × 1 回（`frontendCoverage` だけ） |

- 結合テストの飛ばし 10 件は、DB の種類が当たらない場合の想定どおりの飛ばし（例: `MysqlBusinessDataReadIT` の「PostgreSQL timetz …」）。コンテナの実行環境の無さによる飛ばしではない（その警告は出ていない）。
- 3 の出力に `:backend:integrationTest`・`frontendTest`・`jacocoTestCoverageVerification` のタスクは現れなかった。

## カバレッジ

| 実行 | バックエンドの行 | バックエンドの分岐 | 画面（行・分岐） | 判定 |
|---|---|---|---|---|
| 1 直列 | 97.68%（14904 / 15258） | 91.52%（6375 / 6966） | 95.49%・91% | 全体とパッケージごとの下限（行 80%・分岐 70%）を通過 |
| 2 並べた | 97.68%（14904 / 15258） | 91.52%（6375 / 6966） | 同じ | 同じ。直列と数が完全に一致（R-02） |
| 3 `verifyQuick` の参考値 | 87.3%（13323 / 15258） | 86.0%（5992 / 6966） | 画面は判定あり（通過） | バックエンドは判定しない（参考値だけ） |

報告の置き場: `backend/build/reports/jacoco/test/jacocoTestReport.xml`（1・2）、`backend/build/reports/jacoco/quick/jacocoQuickReport.xml`（3）。

## 時間の比べ（NFR1）

- `verifyQuick`（3分7秒）は `verify`（27分44秒）の約 11%。並べた `verify`（19分44秒）は直列の約 71%。
- 参考: `develop` の先頭 `16f46f12`（`verify` の中身は今回と同じ）の CI の検査の job（`gh run view 42336149063`、試行 1、success）は 30分28秒で、42 分の線の内。

## 失敗

無し。
