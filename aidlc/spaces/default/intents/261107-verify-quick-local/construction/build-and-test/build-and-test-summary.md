# Build and Test のまとめ（Intent 261107-verify-quick-local）

## 状態

ビルドとテストはすべて通った。手順は `build-instructions.md`、結果は `test-results.md`、要件の網羅は `cross-unit-traceability.md`。

## 生成したテストの手順

Test Strategy は Minimal のため、追加のテストの手順のファイルは作らない。単体の確かめは Code Generation の `unit-test-instructions.md`（確かめの台本と実行の確かめ）。E2E は既存の Playwright の全体（`./gradlew e2eTest`）を流した（今回の Intent で E2E は足していない。画面を変えていないため）。

## カバレッジの期待

アプリのソースを変えていないため、カバレッジの値は変わらない見込みで、下限（バックエンドは全体とパッケージごとに行 80%・分岐 70%、画面は全体に同じ値）を全件の `verify` で判定する。単位は無い。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| T-FR1 | requirements.md FR1.1〜FR1.4 | `verifyQuick` の一覧と順が対照表どおり | 一覧と順が一致、`frontendTest`・`integrationTest`・`jacocoTestCoverageVerification` が流れない | `test-results.md` の 3・4 | build-and-test | Met |
| T-FR2 | requirements.md FR2.1〜FR2.4・コードのレビューの R-01 | `-PitTests` の絞り込み・当たらなければ失敗・空は失敗・`verify` とは併用不可・コンテナ無しは失敗 | すべてその動き | 台本の項目 4〜6、`code-summary.md` の実行の確かめ（`AuditTraceIdIT` だけが流れる・`NoSuchClassIT` で失敗・コンテナ無しの `DslTargetDbIT` で失敗） | build-and-test | Met |
| T-FR3 | requirements.md FR3.1〜FR3.3 | `verifyQuick` の参考値は単体テストの記録だけ、判定しない | 参考値 行 87.3%・分岐 86.0% を表示、失敗にしない。`verify` の報告は単体＋結合のまま | `test-results.md` の 3、`code-summary.md`（sessioninfo が単体テストの1件） | build-and-test | Met |
| T-FR4 | requirements.md FR4.1〜FR4.3 | `verify` のタスクと順が変わらない、`-PitForks` で並べて通る、`ci.yml` を変えない | 台本の項目 1・8 が通る、並べた `verify` が通る、`ci.yml` の差は無い | `test-results.md` の 2・4、`git diff develop -- .github/workflows/ci.yml` | build-and-test | Met |
| T-FR5 | requirements.md FR5.1〜FR5.5 | 決まりの書き換えと前後の文の記録 | `project.md` 4 行・`team.md` 11 行を書き換え・追加、前後の文と印を記録 | `code-summary.md` の「決まりの書き換え（前と後）」、コミット `428b7424`・`5ec08caa` | build-and-test | Met |
| T-FR6 | requirements.md FR6.1・FR6.2 | 手順の文書 | `docs/verification.md` の使い分けの小節・判定の手順、`README.md` の6か所 | コミット `6ee613c7`・`a843c9bb` | build-and-test | Met |
| T-NFR1 | requirements.md NFR1 | `verifyQuick` の時間 < `verify` の時間（同じ PC・同じ条件） | 3分7秒 < 27分44秒 | `test-results.md` の時間の比べ | build-and-test | Met |
| T-NFR2 | requirements.md NFR2 | 全件の `verify` の合格の条件を変えずに通る | 通った（下限・セキュリティの検査を含む） | `test-results.md` の 1 | build-and-test | Met |
| T-NFR3 | requirements.md NFR3 | 直列の `verify` と `-PitForks` の `verify` が通る | どちらも通った | `test-results.md` の 1・2 | build-and-test | Met |
| T-R02 | 要件のレビューの R-02（計画で採用） | 並べた `verify` の全体の行・分岐のカバレッジが直列と一致 | 行 14904/15258・分岐 6375/6966 で完全に一致 | `test-results.md` のカバレッジ | build-and-test | Met |
| T-COV-BE | Testing Contract・team.md Testing Posture | バックエンドの全体とパッケージごとに行 80%・分岐 70% 以上 | 全体 行 97.68%・分岐 91.52%、`jacocoTestCoverageVerification` が通過 | `test-results.md` の 1 | build-and-test | Met |
| T-COV-FE | Testing Contract・team.md Testing Posture | 画面の全体に行 80%・分岐 70% 以上 | 行 95.49%・分岐 91% | `test-results.md` の 1 | build-and-test | Met |
| T-TIME | team.md Testing Posture | `verify` と CI の時間を記録、CI の上限の7割（約 42 分）以内 | 手元の `verify` 27分44秒、CI の検査の job 30分28秒（`develop` の先頭 `16f46f12`、`verify` の中身は同じ） | `test-results.md` の時間の比べ | build-and-test | Met |
| T-E2E | team.md Way of Working・Testing Posture | Intent の最後に E2E の全体を1回流し、すべて通る | 298 passed（実際のブラウザ Chromium） | `test-results.md` の 5 | build-and-test | Met |
| T-CI | Testing Contract の CI の義務 | — | — | — | — | N/A（the plan runs no CI Pipeline。CI は統合と依頼者の push の後に、変えていない `ci.yml` で `verify` を流す） |

## 準備の判定

- ビルド: 準備できた（どの入口でも WAR を作れる）。
- テスト: 準備できた（全件・並べた全件・`verifyQuick`・E2E がすべて通った）。
- 配備: WAR とイメージの中身は変わらない（アプリのソースを変えていない）。配備の段の扱いは次の Deployment Pipeline で決める（`project.md` の Deployment の「配備に響かない Intent」の決まり）。

## 次の配備へ引き継ぐこと

依頼者の決定（2026-11-08）で、この Intent は配備の段（Deployment Pipeline・Deployment Execution）を飛ばした（`project.md` の Deployment の「配備に響かない Intent」の決まり。変えたのは検査の道具・テストの道具・決まり・文書だけで、WAR とイメージの中身は変わらない）。次に配備する Intent の Deployment Pipeline は、この一覧を読んで手順に入れる。

- 配備の段で確かめる予定だった項目: 無し（戻しの練習・ブラウザの確かめ・スモークテストに当たる変更が無い。E2E の全体はこの段で実際のブラウザで流して通った）。
- 次の配備の前の全件の `verify`: 次の Intent では、配備に響く変更の Bolt でも、全件の場面（`team.md` の Way of Working）に当たらなければ統合の前は `verifyQuick` になる。リリースの前（`develop` から `main` への取り込みの前）は全件の `verify` を流す。
- CI: 統合と依頼者の push の後に、変えていない `ci.yml` で全件の `verify` が流れる。main への取り込みの前に、この Intent の後のすべてのコミットの CI の結果を試行ごとに確かめる（`project.md` の Deployment）。

## 残っていること

- `develop` への統合（squash）: この段の承認の後に行う（全件の `verify` が通ったため。統合の前に `aidlc/` の未コミットの変更をコミットする）。
- CI: 統合と依頼者の push の後の再確認として動く。結果は main への取り込みの前に試行ごとに確かめる（`project.md` の Deployment）。
- コードのレビューで受け入れた R-07（`docs/verification.md` の判定のコマンドが 7 パッケージを直書き）は後の Intent に回す。確かめの台本を検査に載せるかも後の Intent で決める（R-03 の直し）。
- `-PitForks` の N は 2 で通った（直列の約 71% の時間）。`docs/verification.md` の「JVM のヒープと対象DB のコンテナが N 倍になりうる」の注意は、N=2 では問題が出なかった。3 以上は試していない。
