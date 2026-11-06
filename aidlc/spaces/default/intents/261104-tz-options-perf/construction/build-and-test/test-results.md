# テストの結果 — Intent 261104-tz-options-perf

この段の確かめは1回で済み、繰り戻しは無い。手元の検査（`verify`）と E2E は統合の前のブランチ `fix/tz-options-perf`（コード生成のコミット 23faf271・7e94498d・05a56351 の上）で流した。CI は `develop` へ squash で統合した 985b7e6c を、依頼者が push した後に確かめた。

## 1. 手元の1コマンドの検査（2026-11-07）

`caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した。ログは `build/verify-bt.log`）

| 項目 | 結果 |
|---|---|
| 結果 | BUILD SUCCESSFUL in 29m 4s（00:50〜01:19） |
| 眠り | 流す前の `pmset -g assertions` で眠らない状態を確かめ、流した後の `pmset -g log` に Sleep・DarkWake は無かった |
| バックエンドの単体テスト | 2936 件、失敗 0 |
| バックエンドの結合テスト | 1716 件、失敗 0、飛ばし 10（別の種類の対象DB だけに当てるテストを、ほかの種類のクラスで条件により飛ばすもの。前の Intent と同じ。コンテナの実行環境が無いための飛ばしではない） |
| 画面のテスト | 274 ファイル・3063 件、すべて通過（`offsetLabel.test.ts` 98 件・`buildTimeZoneOptions.test.ts` 13 件を含む） |
| 秘密情報の検出（Gitleaks） | 649 コミット、検出 0 |
| SpotBugs | 止める指摘 0（priority 2 の警告だけ。この Intent で変えていないバックエンドのもの） |
| OSV-Scanner | 手元は入力が変わらず UP-TO-DATE。CI で新しく流した結果を下の 3 節に書いた |

### カバレッジ（行 / 分岐、下限 80% / 70%）

| 範囲 | 行 | 分岐 |
|---|---|---|
| 画面の全体の合計 | 95.49% | 90.97% |
| `src/shared/format/timeZone/` | 98.53% | 96.56% |
| `src/shared/format/timeZone/offsetLabel.ts`（新規） | 100% | 96.55% |
| `src/features/preferences/` | 96.62% | 90.78% |
| バックエンドの全体の合計（`jacocoTestReport.xml`） | 97.68% | 91.52% |
| バックエンドのパッケージごと | `jacocoTestCoverageVerification` が通過（この Intent ではバックエンドを変えていない） | 同左 |

## 2. E2E（2026-11-07）

`caffeinate -dims ./gradlew e2eTest`（Mailpit と見本の対象DB の PostgreSQL を起動して流した。ログは `build/e2e-bt.log`）

| 項目 | 結果 |
|---|---|
| 結果 | 298 件通過、失敗 0、不安定（flaky）0、15.6 分 |
| 本数 | 前の Intent の終わりと同じ 298（この Intent では E2E を足していない・変えていない） |
| 報告の json の検索 | `frontend/test-results/e2e-results.json` に JWT の形・メールアドレス・パスワードの値は無かった。`jdbc:` の1件は内部DB（H2）の一時のファイルの道で、対象DB の資格情報ではない |
| 後片付け | 流し終えた後、対象DB の公開の番号を閉じた |

## 3. push の後の CI

`gh run view <run> --json attempt,jobs` で試行ごとに確かめた。前の取り込みの後のコミットは 2c01c33c（AI-DLC の更新、記録だけ）と 985b7e6c（この Intent）の2つで、どちらも1回目の試行で通った。

| コミット | run | 試行 | 結果 | 検査の job の時間 |
|---|---|---|---|---|
| 2c01c33c | 42243328820 | 1 | success | 15:28:17〜16:06:31Z（38分14秒） |
| 985b7e6c | 42254568136 | 1 | success | 16:40:28〜17:15:02Z（34分34秒） |

- OSV-Scanner は両方の run で同じ警告 6 件（mariadb-java-client 3.5.10 の 4 件・opentelemetry-api 1.62.0 の 1 件・開発用の minimatch 10.2.6 の 1 件、どれも CVSS 7 未満）。直す前の run 42057847502 と同じで、この Intent で増えたものは無い。止める基準（High 以上）には当たらない。
- Gitleaks は検出 0。

### 選択肢の組み立ての性能（`TimeZoneOptionsPerformance.test.ts`、目標 100 ms 以下）

CI の `verify` の中では、カバレッジなしの画面のテスト（先）と `frontendCoverage`（後）の2回流れる。NFR1.3 の判定は `frontendCoverage` の値で行う。

| run（コミット） | CPU | カバレッジなし 中央値 | `frontendCoverage` 中央値 |
|---|---|---|---|
| 41823570241（f2f2eb9b、直す前・失敗した run） | AMD EPYC 7763 | 98.9 ms | **106.4 ms（失敗）** |
| 41930640359（89b83fca、直す前） | Intel Xeon 8370C | 62.8 ms | 73.8 ms |
| 42057847502（a789a6f0、直す前） | AMD EPYC 9V74 | 77.2 ms | 68.8 ms |
| 42243328820（2c01c33c、直す前のコード） | Intel Xeon 8573C | 78.7 ms | 63.5 ms |
| **42254568136（985b7e6c、直した後）** | AMD EPYC 9V74 | **1.3 ms** | **1.4 ms**（runs 4.7・1.5・1.2・1.4・1.1） |

## 4. 変換の性能の前後の比べ（コード生成のレビュー R-04）

`wallClock.ts` の `wallAt` から `wallWith` を分けた変更と、`timeZoneGate.ts` の `createUncachedFormatter` を分けた変更が、変換（`formatInstant`）を遅くしていないかを、同じ条件で比べた。

### 手元の同じ条件の比べ（2026-11-07、Apple M2）

直す前（2c01c33c）を作業ツリーとは別の一時の worktree（scratchpad の下。`node_modules` は作業ツリーのものを参照）に取り出し、`conversionPerformance.test.ts` だけを、直す前と後で交互に3回ずつ流した。終わった後に worktree は消した。

| 条件 | 直す前 中央値（3回） | 直した後 中央値（3回） |
|---|---|---|
| カバレッジなし | 69.2・69.8・68.5 ms | 68.3・68.0・68.4 ms |
| カバレッジつき（v8） | 82.5・83.0・81.5 ms | 83.0・80.9・82.6 ms |

差はばらつきの幅（約 2 ms）の中にあり、直しで遅くなっていない。コード生成の段で見た「`verify` の中で 101〜104 ms」は、ほかのテストと並んで流れる負荷によるもので、直す前後の差ではない。

### CI の値（参考、目標は合計 500 ms・1 回 0.05 ms 以下）

| run（コミット） | CPU | カバレッジなし 中央値 | カバレッジつき 中央値 |
|---|---|---|---|
| 41823570241（f2f2eb9b、直す前） | AMD EPYC 7763 | 251.1 ms | 348.3 ms |
| 41930640359（89b83fca、直す前） | Intel Xeon 8370C | 230.4 ms | 284.1 ms |
| 42057847502（a789a6f0、直す前） | AMD EPYC 9V74 | 155.6 ms | 154.4 ms |
| 42243328820（2c01c33c、直す前のコード） | Intel Xeon 8573C | 192.2 ms | 215.7 ms |
| 42254568136（985b7e6c、直した後） | AMD EPYC 9V74 | 109.7 ms | 205.4 ms |

CI の値は、runner の CPU と並んで流れるテストの負荷で run ごとに 150〜350 ms と大きく揺れるため、前後の判定には使わず、手元の同じ条件の比べで判定した。どの run も目標の 500 ms の内にある。

## Loop-Back Log

なし（繰り戻しは無かった）。

## Sources

- `build/verify-bt.log`・`build/e2e-bt.log`（手元のログ。git の対象外）
- GitHub Actions の run 41823570241・41930640359・42057847502・42243328820・42254568136（`gh run view --json attempt,jobs` と `--log`）
- `backend/build/reports/jacoco/test/jacocoTestReport.xml`
- `construction/code-generation/code-summary.md`（手元の選択肢の組み立ての中央値、T0 31.5 ms → 0.7 ms）
