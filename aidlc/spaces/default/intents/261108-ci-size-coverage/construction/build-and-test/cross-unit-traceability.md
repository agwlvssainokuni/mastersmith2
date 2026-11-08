# 要件の網羅（Intent 261108-ci-size-coverage）

入力: `aidlc engine sensor-traceability --stage build-and-test` の出力、`construction/code-generation/code-generation-plan.md`、`construction/code-generation/traceability.json`

## 判定

合格。道具の出力では NFR1・NFR4 が「uncovered」と出るが、どちらも Code Generation で CI の実行で測るため Deferred（持ち主は Build and Test）とした要件で、この段で CI の実行で確かめて Met とした（`build-and-test-summary.md` の Target Verification Matrix）。

## 要件ごと

| ID | 状態 | 持ち主の段 | 作った所 |
|---|---|---|---|
| FR1.1〜FR1.9 | 済み | code-generation | `scripts/program-size.mjs` |
| FR2.1〜FR2.5 | 済み | code-generation | `scripts/ci-summary.mjs` |
| FR3.1 | 済み | code-generation | `scripts/ci-summary.mjs` |
| FR4.1〜FR4.4・FR4.6 | 済み | code-generation | `.github/workflows/ci.yml` |
| FR4.5 | 済み | code-generation | `scripts/ci-summary.mjs` |
| FR5.1 | 済み | code-generation | `docs/verification.md` |
| NFR1 | 済み（この段で測った） | build-and-test | `.github/workflows/ci.yml`（CI の実行 42485490431） |
| NFR2 | 済み | code-generation | `.github/workflows/ci.yml` |
| NFR3 | 済み | code-generation | `scripts/ci-summary.mjs` |
| NFR4 | 済み（この段で確かめた） | build-and-test | `scripts/program-size.mjs`（CI の実行 42485490431 と手元の比べ） |
| NFR5 | 済み | code-generation | `scripts/program-size.mjs` |

## 網羅されていない ID

なし（NFR1・NFR4 は上のとおりこの段で確かめた）。FR4.4 の `develop` へのプッシュの道（差の列が出る道）は、統合の後の CI で確かめる（`build-and-test-summary.md` の「次の配備へ引き継ぐこと」）。
