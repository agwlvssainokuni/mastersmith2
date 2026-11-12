# 要件の網羅（Intent 261110-deps-forks-review）

入力: `aidlc engine sensor-traceability --stage build-and-test` の出力、`construction/code-generation/code-generation-plan.md`、`construction/code-generation/traceability.json`

## 判定

合格。道具の出力で「uncovered」と出た FR4.1・FR4.1a・FR4.2・FR4.3・FR4.4・FR4.5・FR5.1・FR5.3・FR6.1・FR6.2・FR6.3 は、どれも Code Generation で持ち主を Build and Test 以降の段とした（Deferred）要件。FR4.1〜FR4.4 はこの段で確かめて Met、FR4.5・FR5.1・FR5.3 はこの段で当たらないと判断した（N/A。理由は `build-and-test-summary.md` の Target Verification Matrix）。FR6.1〜FR6.3 は配備の段で行う。

## 要件ごと

| ID | 状態 | 持ち主の段 | 作った所・確かめ |
|---|---|---|---|
| FR1.1〜FR1.9 | 済み | code-generation | `Dockerfile`・`frontend/package.json`・`frontend/package-lock.json`・`gradle/libs.versions.toml`・`.github/workflows/ci.yml`・`compose.yaml`・`.gitmodules` ほか（`traceability.json`） |
| FR2.1〜FR2.5 | 済み | code-generation | `gradle/libs.versions.toml`・`Dockerfile`・`frontend/playwright.config.ts`・`generation-notes.md` |
| FR3.1〜FR3.3 | 済み | code-generation | `aidlc/spaces/default/memory/project.md`・`.github/dependabot.yml`・`generation-notes.md` |
| FR4.1・FR4.1a・FR4.2・FR4.3 | 済み（この段） | build-and-test | CI の実行 43059136421・43063129362・43067785167・43074723292・43078240581・43082808332（`test-results.md` 3.1節） |
| FR4.4 | 済み（この段） | build-and-test | `.github/workflows/ci.yml`・`docs/verification.md`（`c59185b9`） |
| FR4.5 | 当たらない | build-and-test | 既定の値を変えていない（参考に `a65fb5af` のプッシュの CI を記録） |
| FR5.1・FR5.3 | 当たらない | build-and-test | Spring Boot・H2・JRE が変わらず FR5.2 に当たる（`test-results.md` 5節） |
| FR5.2 | 済み | code-generation・build-and-test | `code-summary.md` 2節の版の表、省いた理由は `test-results.md` 5節 |
| FR6.1〜FR6.3 | 配備の段 | deployment-pipeline・deployment-execution | — |
| NFR1〜NFR5 | 済み | code-generation・build-and-test | `test-results.md` 1〜3・6節 |

## 網羅されていない ID

なし（FR6.1〜FR6.3 は持ち主の段で行う）。
