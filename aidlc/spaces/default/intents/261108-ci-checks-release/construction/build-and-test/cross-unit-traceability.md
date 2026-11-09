# 要件の網羅（Intent 261108-ci-checks-release）

入力: `aidlc engine sensor-traceability --stage build-and-test` の出力、`construction/code-generation/code-generation-plan.md`、`construction/code-generation/traceability.json`

## 判定

合格。道具の出力で「uncovered」と出た FR3.2・FR4.2・FR5.1・FR5.5・FR6.1・FR6.2・FR6.3・NFR1・NFR2・NFR4 は、どれも Code Generation で持ち主を Build and Test 以降の段とした（Deferred）要件。FR3.2・FR4.2・FR5.1・FR5.5・FR6.1・NFR1・NFR2・NFR4 はこの段で確かめて Met とした（`build-and-test-summary.md` の Target Verification Matrix）。FR6.2（`main` への取り込み）と FR6.3（配備の段）は、この段の承認の後と配備の段で行う。

## 要件ごと

| ID | 状態 | 持ち主の段 | 作った所・確かめ |
|---|---|---|---|
| FR1.1〜FR1.4 | 済み | code-generation | `frontend/scripts/check-license-header.mjs`・`frontend/package.json`・`frontend/eslint.config.js`・`.pre-commit-config.yaml`・`docs/verification.md` |
| FR2.1〜FR2.4 | 済み | code-generation | `TimeBoundedDbHealthIndicatorTest.java`・`backend/build.gradle.kts`・`scripts/ci-summary.mjs`・`docs/verification.md`・`team.md`・`project.md` |
| FR3.1 | 済み | code-generation | `.github/workflows/ci.yml`（`ubuntu-latest` のまま） |
| FR3.2 | 済み（この段） | build-and-test | CI の実行 42535169021 の道具の版 |
| FR4.1・FR4.3 | 済み | code-generation | `.github/workflows/ci.yml`・`team.md`・`docs/verification.md` |
| FR4.2 | 済み（この段） | build-and-test | CI の実行 42535156503（N=2）・42535169021（N=3） |
| FR5.1 | 済み（この段で動いているコンテナを確かめた） | build-and-test | `scripts/ops-readonly-check.mjs` |
| FR5.2〜FR5.4 | 済み | code-generation | `scripts/ci-attempts.mjs`・`scripts/count-db-copy.mjs`・`docs/` |
| FR5.5 | 済み（この段） | build-and-test | A1・A2・A3 の合格の条件 |
| FR6.1 | 済み（この段） | build-and-test | 全件の `verify`・E2E・A2 |
| FR6.2 | この段の承認の後 | build-and-test・deployment-execution | 取り込みの直前の A2 と fast-forward |
| FR6.3 | 配備の段 | deployment-pipeline・deployment-execution | — |
| NFR1・NFR2・NFR4 | 済み（この段） | build-and-test | test-results.md |
| NFR3 | 済み | code-generation | 台本の出力の検索 |

## 網羅されていない ID

なし（FR6.2・FR6.3 は持ち主の段で行う）。
