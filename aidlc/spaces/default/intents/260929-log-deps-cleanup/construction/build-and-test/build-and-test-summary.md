# Build and Test のまとめ（260929-log-deps-cleanup）

入力: `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/code-generation/code-generation-plan.md`（Testing Contract を含む）、`unit-test-instructions.md`、`code-summary.md`。

## ビルドの状態

- `develop` の `8489241`（origin にも push 済み）で、`./gradlew verify` と CI が成功した。手順は `build-instructions.md`、実測は `test-results.md`。
- 前提: JDK 25、Node 24、colima と環境変数（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）、サブモジュールの固定先。

## テストの種類

| 種類 | 手順の置き場 | この段で流したか |
|---|---|---|
| 単体テスト（JUnit・Vitest） | `code-generation/unit-test-instructions.md` | verify の中で3回 |
| 結合テスト（Spring・Testcontainers） | `integration-test-instructions.md` | verify の中で3回 |
| E2E（Playwright） | `integration-test-instructions.md` | コード生成の段で 110 件（この段の変更は画面に触れない） |
| 性能 | `performance-test-instructions.md` | 対象の変更なし（警報の読み込みはコード生成の段で確かめた） |
| セキュリティ（Gitleaks・SpotBugs・OSV-Scanner、漏えいのテスト） | `security-test-instructions.md` | verify の中で3回（OSV-Scanner はコード生成の段と CI で実行） |

## 期待するカバレッジ

単位の分割は無い。backend は全体とパッケージごとに行 80%・分岐 70%、frontend は行 80%・分岐 70%（team.md の Testing Posture）。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| TC-COV-BE | code-generation-plan.md の Testing Contract（team 層） | 行 80% 以上・分岐 70% 以上（全体） | 行 98.8%・分岐 94.4% | `test-results.md` 1節（この段 3） | build-and-test | Met |
| TC-COV-BE-PKG | 同上 | 各パッケージで行 80%・分岐 70%（`user.domain`・`user.service` を含む） | `user.domain` 99.5%・97.6%、`user.service` 100.0%・95.5%、`jacocoCoverageVerification` 成功 | `test-results.md` 1節 | build-and-test | Met |
| TC-COV-FE | 同上 | 行 80%・分岐 70% | 行 97.44%・分岐 92.67% | `test-results.md` 1節 | build-and-test | Met |
| TC-SUITE | Testing Contract の scope_floor | 既存のテストが通る（SKIPPED 0） | 単体 1243・結合 566・frontend 732、失敗 0・SKIPPED 0 | `test-results.md` 1節 | build-and-test | Met |
| TC-REGRESSION | Testing Contract の scope_floor（bugfix） | 不具合を再現するテストがある | `InitialAdminInitializerTest`・`InitialAdminIT` が直しを外すと失敗 | `code-generation/code-summary.md`（Step 8） | code-generation | Met |
| NFR1 | requirements.md NFR1 | 初期管理者の作成の経路でメールアドレスそのものがログに出ない | 単体・結合・外部エクスポートのテストが成功 | `test-results.md` 1節 | build-and-test | Met |
| NFR2 | requirements.md NFR2 | verify が対象DB のテストを飛ばさずに通る | 3回とも成功・SKIPPED 0 | `test-results.md` 1節 | build-and-test | Met |
| NFR3 | requirements.md NFR3 | 統合の前に E2E が通る | 110 件成功 | `code-generation/code-summary.md`（Step 22） | code-generation | Met |
| NFR4 | requirements.md NFR4 | 警報の式としきい値が読み込まれる | しきい値 500、評価の誤り 0、式が成功 | `code-generation/code-summary.md`（Step 19） | code-generation | Met |
| CI | team.md の Way of Working | 統合の後の CI が成功 | run 36615809940（`8489241`）成功 | `test-results.md` 2節 | build-and-test | Met |

## 準備の状態

- ビルド: できている。テスト: できている。配備: できている（配備は Deployment Pipeline・Deployment Execution の段で行う）。

## 残っていること・既知の制限

- 警報 `ms-check-p95` がしきい値そのもので鳴ることは確かめていない（要件の目標ではない。レビューの R-04。`performance-test-instructions.md`）。
- 監査の記録の失敗の ERROR のメールアドレスは既知の例外（FR2 を行わない決定）。この段の学びで、読み方を project.md に記録する（Q4: A）。
- CI で落ちたテストの直しは、負荷をかけた再現の確かめを途中で止めた（`test-results.md` 3節）。
- Dependabot は `0.162.0-386` のようなタグを版と読み違えうる（`test-results.md` 5節）。
