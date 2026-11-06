# 要件の対応 — Intent 261104-tz-options-perf

`aidlc engine sensor-traceability --stage build-and-test`（2026-11-07、`develop` の 985b7e6c の上）の出力から書いた。この Intent は単位を持たない（bugfix・Minimal）ため、単位をまたぐ対応は無く、要件と Code Generation の `traceability.json` の対応だけを見る。

## 判定

**道具の判定は fail、記録で満たした**。要件 14 件のうち 13 件が Code Generation の作ったファイルに対応している。対応の無い1件（NFR1.3）は、Code Generation で Build and Test へ Deferred にしたもので、この段の CI の確かめで満たした。

| 区分 | 数 | 対応あり | 対応なし |
|---|---|---|---|
| 機能要件（FR） | 7 | 7 | 0 |
| 非機能要件（NFR） | 7 | 6 | 1（NFR1.3） |

## 対応の無い ID

| ID | 中身 | 持ち主の決まり | この段の手当て | 判定 |
|---|---|---|---|---|
| NFR1.3 | push の後の CI の `frontendCoverage` で、選択肢の組み立ての性能のテストが 100 ms 以下で通る | Code Generation の `traceability.json` で Deferred（Build and Test: push の後の CI の結果を記録する） | 985b7e6c の CI run 42254568136（1回目の試行で success）の `frontendCoverage` で中央値 1.4 ms（`test-results.md` 3 節） | 記録で満たした |

## 対応の一覧

| ID | 対応するファイル（Code Generation） | Build and Test での確かめ |
|---|---|---|
| FR1.1・FR2.2・FR3.2 | `frontend/src/shared/format/timeZone/offsetLabel.test.ts` | `verify` と CI で 98 件通過 |
| FR1.2・FR1.3・FR3.1・NFR2.1・NFR2.2 | `frontend/src/features/preferences/buildTimeZoneOptions.test.ts` | `verify` と CI で 13 件通過 |
| FR2.1・NFR3.2 | `frontend/src/shared/format/timeZone/offsetLabel.ts` | 行 100%・分岐 96.55%。ESLint の設定と規則は変えていない（2c01c33c と 985b7e6c の差が無い） |
| NFR1.1・NFR1.2 | `frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts` | テストは変えていない。手元の中央値 0.7 ms（T0 31.5 ms の半分以下） |
| NFR1.3 | （Deferred） | CI の `frontendCoverage` で 1.4 ms |
| NFR3.1 | `build.gradle.kts`（変えていない） | `verify` が BUILD SUCCESSFUL |

## Sources

- `aidlc engine sensor-traceability --stage build-and-test` の出力
- `construction/code-generation/traceability.json`
- `construction/build-and-test/test-results.md`
- `inception/requirements-analysis/requirements.md`
