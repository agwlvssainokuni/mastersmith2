# 検査の結果（260929-log-deps-cleanup）

## 1. ビルドとテストの実測

`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` を、colima の環境変数を渡して流した（数字は実測だけ）。

| 実行 | 対象 | 結果 | 単体 | 結合 | frontend | 時間 |
|---|---|---|---|---|---|---|
| コード生成の Step 3（基準） | 変更の前 `3c38081` | 成功 | 1234 | 565 | 732 | 6分37秒 |
| コード生成の Step 21 | 作業ブランチ（`e13808b` と同じ中身） | 成功 | 1243 | 566 | 732 | 6分19秒 |
| この段 1 | `develop` `e13808b`（Q1: A） | 成功 | 1243 | 566 | 732 | 6分20秒 |
| この段 2 | テストの直し・otel-collector・team.md の後（`f5fc7ed` の中身） | 成功 | 1243 | 566 | 732 | 6分48秒 |
| この段 3 | spotbugs の Gradle プラグイン 6.5.12 の後（`8489241` の中身） | 成功 | 1243 | 566 | 732 | 6分47秒 |

- 単体・結合テストの失敗・誤り・SKIPPED は、どの実行も 0。
- カバレッジ（この段 3、JaCoCo）: 全体 行 98.8%・分岐 94.4%、`user.domain` 行 99.5%・分岐 97.6%、`user.service` 行 100.0%・分岐 95.5%、`common.observability` 行 97.5%・分岐 94.4%。frontend（`@vitest/coverage-v8`）行 97.44%・分岐 92.67%。下限（行 80%・分岐 70%）を満たす。
- 秘密情報の検出（Gitleaks）・SpotBugs の関門・カバレッジの検証は、どの実行も成功。OSV-Scanner（`osvScan`）は、この段の3回とも入力（lockfile）が変わらず UP-TO-DATE で、実際に流したのはコード生成の Step 21（成功）と CI（下の 2節、成功）。
- E2E（`./gradlew e2eTest`）: コード生成の Step 22 で 110 件すべて成功（flaky 0）。この段の変更（テストの待ち方・compose.yaml・team.md・Gradle のプラグイン）は画面に触れないため流し直していない。

## 2. CI（GitHub Actions の `./gradlew verify`）

| run | コミット | 結果 | 失敗の中身 |
|---|---|---|---|
| 36593277868（1回目） | `e13808b` | 失敗 | frontendTest で `ShellLayout.test.tsx` の「shows the new name right after the preferences are saved」1件（731/732 成功） |
| 36593277868（再実行、T1: A） | `e13808b` | 失敗 | frontendTest は成功。frontendCoverage で `PreferencesPage.test.tsx` の「drops the preview when leaving the screen without saving」1件（731/732 成功） |
| 36613967625 | `f5fc7ed` | 成功 | ― |
| 36615809940 | `8489241` | 成功 | ― |

## 3. 不安定なテストの直し（T3: A、コミット `48a4c5c`）

- 原因: 2件とも、画面の操作の直後に、描画の後の効果（useEffect）で反映される表示の設定を同期で確かめていた。負荷の高い CI で効果がまだ流れていないと落ちる。ShellLayout の件は、表示の設定の保存先の購読が描画の後の効果で始まる前に保存していた（`DisplaySettingsProvider.test.tsx` の待ち方と同じ理由）。PreferencesPage の件は、見せ方の取りやめが画面が外れるときの効果の片付けで行われる。手元では再現しなかった（単独 12 回・全体 2 並列 4 回ですべて成功）ため、原因は見立てで、コードの読みで裏付けた。
- 直し: 2件と、同じ形の `RegistrationPage.test.tsx` の1件を、`waitFor` で反映を待って確かめる形にした。確かめる中身は変えていない。
- 確かめ: 書式・リンタ・型の検査、直した3ファイルの単独の実行 3 回（各 70/70）、frontend 全体 1 回（732/732）、verify 2 回、CI 2 回がすべて成功。直しの確かめ（負荷をかけた再現の試み）は、長くかかりすぎたため依頼者の指示で途中で止めた。
- 計画に無い変更。team.md の「不安定なテストは原因を直すまで統合しない」に沿って、統合の前に直した。

## 4. 計画に無い取り込み

- otel-collector を 0.162.0 に（`2ac7df1`、T2 の依頼者の答え）。Dependabot の #21 は 0.161.0-386（32 ビット向けのタグ）への更新で、版の読み違いのため取り込まない。`validate` と起動（Everything is ready）で確かめた。
- spotbugs の Gradle プラグインを 6.5.12 に（`8489241`、Dependabot #22、依頼者の指示）。`buildEnvironment` で 6.5.12 が使われていることと、verify・CI の成功を確かめた。
- team.md の `packagesJudgedByTotal` の記述（`f5fc7ed`、FR6.1、Q3: A）。

## 5. Dependabot のプルリクエスト

- #5（typescript 7.0.2）・#18（`@types/node`）・#20（jackson-bom 3.1.7）は Dependabot が閉じた。#19（spotless）・#21（otel-collector）は依頼者が閉じた。#22（spotbugs）は依頼者が閉じる。
- 見立て: Dependabot はイメージのタグの後ろの部分（`-386` など）を版と読み違えるため、0.162.0 にしても `0.162.0-386` の知らせが来うる。

## Loop-Back Log

なし（コード生成の段へ戻る必要のある失敗は無かった）。
