# Build and Test のまとめ（Intent 261108-ci-checks-release）

入力: `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`

## 1. 状態

- ビルド: 成功（手元の全件の `verify`、E2E 全体、CI の手動の実行2回）。
- テストの種類: Test Strategy は Minimal。足した自動テストは `common.health` の単体テスト2件。台本 A1〜A3 は手元で流して合格の条件を確かめた（単体テストは置かない。依頼者の読み方）。追加のテストの手順のファイル（結合・性能・セキュリティ）は作らない。
- カバレッジ: バックエンドは全体と、すべてのパッケージごとの下限を満たす（`packagesJudgedByTotal` を消した後）。画面は全体の下限を満たす。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1 | requirements.md NFR1 | CI の job を前（35分47秒）と並べ、42 分の線から離す。目安 30 分未満 | N=3 で 21分39秒（N=2 は 29分07秒） | test-results.md 3節 | build-and-test | Met |
| NFR2 | requirements.md NFR2 | 下限・合否を緩めない。全パッケージが下限を満たす。並べた CI の件数・失敗・カバレッジが並べない値と一致（食い違えば依頼者に諮る） | 全パッケージが下限を満たし `jacocoTestCoverageVerification` が通った。失敗 0。分岐は一致。行は CI の N=3 だけ 1 行の揺れ（auth.repository の 137 行。競り合いのタイミングによる）で、依頼者が受け入れた | test-results.md 1・3節 | build-and-test | Met |
| NFR3 | requirements.md NFR3 | 台本が秘密・個人に関する値・ホームのパスを出さない | A1・A2・A3 の出力の検索で出ない | test-results.md 4節、code-summary.md 2節 | code-generation | Met |
| NFR4 | requirements.md NFR4 | 既存のテストが緑のまま | 単体 2,939・結合 1,716（飛ばし 10）・画面 3,063 × 2・E2E 298、失敗 0 | test-results.md 1・2節 | build-and-test | Met |
| FR3.2 | requirements.md FR3.2 | Ubuntu 26 の上で CI の道具が動き、版を記録する | ubuntu-26.04 で JDK 25.0.5・Node 26.13.0・Gradle 9.8.2・cloc 2.06 などが動き、job が通った | test-results.md 3節 | build-and-test | Met |
| FR4.2 | requirements.md FR4.2 | CI の上で N=2 と N=3 を比べて N を決める | N=3（差 7分28秒） | test-results.md 3節 | build-and-test | Met |
| FR5.1・FR5.5（A1） | requirements.md FR5.5 | 5 つの項目が出て、食い違いを示し、値が出ない | 動いているコンテナで食い違い 0 件、値は出ない | test-results.md 4節 | build-and-test | Met |
| FR6.1 | requirements.md FR6.1 | 全件の `verify`・E2E 全体・前の取り込みの後の CI の試行ごとの確かめ | どれも通った（A2 で 23 コミット、再実行 0） | test-results.md 1・2・4節 | build-and-test | Met |
| FR6.2 | requirements.md FR6.2 | 依頼者の承認を得て fast-forward で取り込む。直前に A2 を流し直す | この段の承認の後に行う | — | build-and-test（承認の後）・deployment-execution | N/A |
| FR6.3 | requirements.md FR6.3 | 配備の段を通し、前の Intent の引き継ぎを手順に入れる | 配備の段で行う | — | deployment-pipeline・deployment-execution | N/A |
| TP-COV-BE | team.md Testing Posture | 行 80%・分岐 70%、すべてのパッケージごと | 行 97.75%・分岐 91.51%、下回るパッケージ 0 | test-results.md 1節 | build-and-test | Met |
| TP-COV-FE | team.md Testing Posture | 行 80%・分岐 70% | 行 95.49%・分岐 91.0% 前後（`verify` の判定が通った） | test-results.md 1・3節 | build-and-test | Met |
| TP-CI-TIME | team.md Testing Posture | 全件の `verify` と CI の時間を記録 | 手元 15分34秒（N=3）、CI 21分39秒（N=3） | test-results.md 1・3節 | build-and-test | Met |
| TC-STRATEGY | Testing Contract strategy_volume | 要件ごとの単体テスト | `common.health` の2件のほかは置かない（台本は本体の機能でないという依頼者の読み方）。合格の条件を手元で確かめた | requirements.md 5節、code-summary.md | code-generation | N/A |
| CI-BEFORE-MERGE | Testing Contract | CI での実行（CI Pipeline の段を流すとき） | この計画は CI Pipeline の段を流さない（統合の前に CI の手動の実行で確かめた） | aidlc-state.md | — | N/A |

## 2. 準備の状態

- ビルドの準備: できている。
- テストの準備: できている。
- 配備の準備: アプリの本体のソース（`src/main`）は変えておらず、WAR の中身は前の配備と同じ見込み。リリース（`main` への取り込み）と配備の段は、依頼者の決定（Q6: A）で通す。

## 3. 決まりとの差・残ること

- E2E の環境の直し（`compose.yaml` の Mailpit の `MP_SMTP_DISABLE_RDNS`）は計画に無かった。依頼者の決定（A）と承認で足した（`9a739212`）。
- NFR2 の 1 行の揺れ（auth.repository の 137 行）は、依頼者の決定で受け入れた。
- 知識ベースの古い記述（Code Generation のレビュー R-06）は、次の Intent の Reverse Engineering で直す。
- 統合は Bolt ごとの squash（計画の承認で選んだ）。作業ブランチのコミットを Bolt 1〜5 の5つにまとめ、レビューの直し（`e359f10f`）と Mailpit の直し（`9a739212`）は、当たる Bolt（4・5 と環境）に含めるか、別のコミットにするかを統合のときに依頼者に尋ねる。

## 次の配備へ引き継ぐこと

- この Intent の配備の段で、前の Intent（261107-verify-quick-local・261108-ci-size-coverage）の「次の配備へ引き継ぐこと」の一覧を読んで手順に入れる（FR6.3）。
- `main` へ取り込んだ後の最初の `develop` へのプッシュの CI で、`-PitForks=3` の既定の時間と結果を確かめる。
