# Build and Test のまとめ — Intent 261104-tz-options-perf

## 全体の状態

- 範囲: 画面（フロントエンド）の純粋な関数とそのテストだけ（bugfix・Minimal・Test Strategy Minimal・単位なし）。コード生成のコミット: 23faf271・7e94498d・05a56351（ブランチ `fix/tz-options-perf`）。
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
| T-NFR1.1 | requirements.md NFR1.1 | 性能のテストの目標 100 ms と測り方を変えない | Pending | Pending | build-and-test | Pending |
| T-NFR1.2 | requirements.md NFR1.2 | 手元・カバレッジつきの中央値が T0（31.5 ms）の半分の 15.75 ms 以下 | Pending | Pending | build-and-test | Pending |
| T-NFR1.3 | requirements.md NFR1.3 | push の後の CI の `frontendCoverage` で性能のテストが 100 ms 以下で通る | Pending | Pending | build-and-test | Pending |
| T-NFR2 | requirements.md NFR2.1・NFR2.2 | 回数の退行のテストが verify と CI で流れて通る（直す前の作りで落ちることはコード生成で記録済み） | Pending | Pending | build-and-test | Pending |
| T-NFR3.1 | requirements.md NFR3.1 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が通る | Pending | Pending | build-and-test | Pending |
| T-NFR3.2 | requirements.md NFR3.2 | ESLint の時差の規則を緩めない（`frontend/eslint.config.js`・`eslint-rules/` を変えない） | Pending | Pending | build-and-test | Pending |
| T-COV-FE | team.md Testing Posture・Testing Contract | フロントエンドの全体の合計 行 80%・分岐 70% 以上 | Pending | Pending | build-and-test | Pending |
| T-COV-BE | team.md Testing Posture・Testing Contract | バックエンドの全体とパッケージごとの 行 80%・分岐 70% 以上 | Pending | Pending | build-and-test | Pending |
| T-E2E | team.md Way of Working・Testing Posture | E2E 全体（`./gradlew e2eTest`）が通り、報告の json に秘密・個人の値が無い | Pending | Pending | build-and-test | Pending |
| T-R04 | code-generation レビュー R-04 | 変換の性能のテスト（`conversionPerformance`）の値を、同じ条件で直す前と後で並べ、直しで遅くなっていないことを確かめる | Pending | Pending | build-and-test | Pending |
| T-TIME | team.md Testing Posture | `verify` と CI の検査の job の時間を記録し、42 分を超えない | Pending | Pending | build-and-test | Pending |
| T-CI-CONTRACT | Testing Contract（CI before merge） | — | — | — | — | N/A（the plan runs no CI Pipeline。NFR1.3 の CI の確かめは T-NFR1.3 で行う） |

## 準備の状態

- build-ready: Pending
- test-ready: Pending
- deployment-ready: Pending

## 既知の制約・残り

- Safari など Temporal が無いブラウザ（Intl の道）での実際の動きは、この PC の Playwright が Chromium だけのため試せない。Intl の道は Node の単体テスト（jsdom、Temporal を無しにして呼ぶ）で確かめた範囲だけ。Safari では選択肢の組み立てはほぼ速くならない（依頼者の決定 B の範囲、コード生成の承認の場で了解）。

## Sources

- `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`
- `inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`
