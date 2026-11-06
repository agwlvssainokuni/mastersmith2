# Build and Test のまとめ — Intent 261104-tz-options-perf

## 全体の状態

- 範囲: 画面（フロントエンド）の純粋な関数とそのテストだけ（bugfix・Minimal・Test Strategy Minimal・単位なし）。コード生成のコミット: 23faf271・7e94498d・05a56351（ブランチ `fix/tz-options-perf`）。`develop` へ squash で統合した 985b7e6c（ブランチは消した）。
- 結果: すべての目標が Met。繰り戻しは無い。詳しい数値は `test-results.md`、要件の対応は `cross-unit-traceability.md`。
- 前提: colima・Node 26・JDK（`build-instructions.md`）。

## 生成したテストの種類

- Minimal のため、追加のテスト手順のファイル（結合・性能・セキュリティ）は作らない。単体テストはコード生成の段で作った（`offsetLabel.test.ts` 新規・`buildTimeZoneOptions.test.ts`）。
- 性能のテスト `TimeZoneOptionsPerformance.test.ts` は既存のもの（変えていない）。
- E2E は既存の全体を `team.md` の決まり（Intent の最後に全体を1回）で流す。この Intent で E2E は足していない。

## カバレッジの見込み

- フロントエンド: 全体の合計に行 80%・分岐 70%（`vitest.config.ts` の `thresholds`）。新しい `src/shared/format/timeZone/offsetLabel.ts` と変えた `src/features/preferences/` の値を記録する。
- バックエンド: この Intent では変えていない。全体と パッケージごとの下限は `verify` で確かめる。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| T-NFR1.1 | requirements.md NFR1.1 | 性能のテストの目標 100 ms と測り方を変えない | 性能のテスト（`TimeZoneOptionsPerformance.test.ts`）と `vitest.config.ts` は 2c01c33c と 985b7e6c で差が無い | `git diff --stat 2c01c33c 985b7e6c`・test-results.md 3節 | build-and-test | Met |
| T-NFR1.2 | requirements.md NFR1.2 | 手元・カバレッジつきの中央値が T0（31.5 ms）の半分の 15.75 ms 以下 | 手元・カバレッジつきの中央値 0.7 ms（T0 31.5 ms の約 0.02 倍） | code-summary.md（Step 8 の3回の実測） | build-and-test | Met |
| T-NFR1.3 | requirements.md NFR1.3 | push の後の CI の `frontendCoverage` で性能のテストが 100 ms 以下で通る | CI run 42254568136（985b7e6c、1回目の試行で success）の `frontendCoverage` で中央値 1.4 ms | test-results.md 3節 | build-and-test | Met |
| T-NFR2 | requirements.md NFR2.1・NFR2.2 | 回数の退行のテストが verify と CI で流れて通る（直す前の作りで落ちることはコード生成で記録済み） | `buildTimeZoneOptions.test.ts` 13 件（回数の退行のテストを含む）が手元の `verify` と CI で通過 | test-results.md 1節・3節 | build-and-test | Met |
| T-NFR3.1 | requirements.md NFR3.1 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が通る | BUILD SUCCESSFUL in 29m 4s（バックエンド 2936・1716 件、画面 3063 件、失敗 0） | `build/verify-bt.log`・test-results.md 1節 | build-and-test | Met |
| T-NFR3.2 | requirements.md NFR3.2 | ESLint の時差の規則を緩めない（`frontend/eslint.config.js`・`eslint-rules/` を変えない） | `frontend/eslint.config.js`・`frontend/eslint-rules/` は 2c01c33c と 985b7e6c で差が無い。リンタは `verify` と CI で通過 | `git diff --stat 2c01c33c 985b7e6c` | build-and-test | Met |
| T-COV-FE | team.md Testing Posture・Testing Contract | フロントエンドの全体の合計 行 80%・分岐 70% 以上 | 全体の合計 行 95.49%・分岐 90.97%（`timeZone/` 98.53%・96.56%、`offsetLabel.ts` 100%・96.55%、`preferences/` 96.62%・90.78%） | test-results.md 1節 | build-and-test | Met |
| T-COV-BE | team.md Testing Posture・Testing Contract | バックエンドの全体とパッケージごとの 行 80%・分岐 70% 以上 | 全体の合計 行 97.68%・分岐 91.52%、パッケージごとの検証（`jacocoTestCoverageVerification`）も通過 | `jacocoTestReport.xml`・test-results.md 1節 | build-and-test | Met |
| T-E2E | team.md Way of Working・Testing Posture | E2E 全体（`./gradlew e2eTest`）が通り、報告の json に秘密・個人の値が無い | 298 件通過・失敗 0・flaky 0（15.6 分）。報告の json に JWT・メールアドレス・パスワード・対象DB の資格情報は無い | `build/e2e-bt.log`・test-results.md 2節 | build-and-test | Met |
| T-R04 | code-generation レビュー R-04 | 変換の性能のテスト（`conversionPerformance`）の値を、同じ条件で直す前と後で並べ、直しで遅くなっていないことを確かめる | 手元の同じ条件の比べで、直す前 68.5〜69.8 ms と後 68.0〜68.4 ms（カバレッジなし）、前 81.5〜83.0 ms と後 80.9〜83.0 ms（カバレッジつき）。差はばらつきの内 | test-results.md 4節 | build-and-test | Met |
| T-TIME | team.md Testing Posture | `verify` と CI の検査の job の時間を記録し、42 分を超えない | 手元の `verify` 29分4秒、CI の検査の job 34分34秒（985b7e6c）・38分14秒（2c01c33c）。どれも 42 分の内 | test-results.md 1節・3節 | build-and-test | Met |
| T-CI-CONTRACT | Testing Contract（CI before merge） | — | — | — | — | N/A（the plan runs no CI Pipeline。NFR1.3 の CI の確かめは T-NFR1.3 で行う） |

## 準備の状態

- build-ready: Ready（手元の `verify` と CI の `verify` が通った）
- test-ready: Ready（単体・結合・画面のテストと E2E がすべて通り、カバレッジの下限を満たした）
- deployment-ready: Ready（985b7e6c が `develop` にあり、CI が1回目の試行で通った。配備の流れは Deployment Pipeline 以降の段で扱う）

## 既知の制約・残り

- Safari など Temporal が無いブラウザ（Intl の道）での実際の動きは、この PC の Playwright が Chromium だけのため試せない。Intl の道は Node の単体テスト（jsdom、Temporal を無しにして呼ぶ）で確かめた範囲だけ。Safari では選択肢の組み立てはほぼ速くならない（依頼者の決定 B の範囲、コード生成の承認の場で了解）。

## Sources

- `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`
- `inception/requirements-analysis/requirements.md`
- `construction/build-and-test/test-results.md`・`cross-unit-traceability.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`
