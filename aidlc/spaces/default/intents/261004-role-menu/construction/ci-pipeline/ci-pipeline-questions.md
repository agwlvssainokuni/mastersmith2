# CI Pipeline の質問 — 261004-role-menu

## 決まっていること（質問にしない）

- CI は既にある GitHub Actions（`.github/workflows/ci.yml`）で、この Intent では CI の定義を変えていない。この段の文書は、新しい設計ではなく既にあるものの記録として書く（`project.md` の Deployment の学び）。
- 統合先は `develop`、プルリクエストは使わない、CI は統合の後の再確認、push は依頼者が行う（`team.md` の Way of Working）。
- CI の検査は `./gradlew verify` と同じ（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体 → 結合（対象DB のコンテナ）→ カバレッジの下限 → セキュリティ）。E2E（Playwright）は CI の外に置き、統合の前と配備の前に手元で流す（`team.md` の Testing Posture）。
- 0415b94 の CI（run 38398376712）は成功。Build and Test の直し 005730a の CI（run 38432556919）は動いている最中で、この段で結果を記録する（Build and Test から引き継いだ TC-7b）。

## Q1. この Intent で CI の定義に変える所はあるか

この Intent では、新しい機能の API・画面・E2E（140・150・160・170）・k6 の場面・テストの JVM のヒープ（単体 1536m）を足したが、どれも `./gradlew verify` と手元の E2E の中で働き、CI の定義（`ci.yml`）を変える必要は無い見込み。

A. 変えない。今の CI を記録し、この Intent で増えた検査（テストの件数・カバレッジ・時間）を記録に足す
B. 変える（変える所を書く）
X. Other (please specify)

[Answer]: A

## Q2. 手元の `./gradlew verify` の中の OSV-Scanner が、lockfile が変わらないと前回の結果のまま飛ばされる件をどう扱うか

Build and Test で、lockfile が変わっていないため `osvScan` が UP-TO-DATE になり、手元の verify では新しく公表された脆弱性を確かめていなかった（`--rerun` で流し直して確かめた）。CI は毎回新しい環境で流すため、毎回確かめている。

A. 今のまま。CI では毎回確かめることと、手元では `--rerun` で流し直す手順を quality-gates.md と README に書く
B. `osvScan` を毎回流す形（Gradle の up-to-date の判定を外す）に直す。手元の verify が少し長くなる
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

答えのまとめ: Q1 A（CI の定義は変えず、今の CI を記録し、この Intent で増えた検査の件数・カバレッジ・時間を記録に足す。005730a の CI の結果もこの段で記録する）、Q2 A（ビルドの設定は変えず、OSV-Scanner は CI で毎回確かめることと、手元では `--rerun` で流し直す手順を quality-gates.md と README に書く）。あわせて、Construction から Operation への境目の確かめ（phase-check-construction.md）を書く。この内容で進めてよいか。

- Looks correct
- Request changes

[Answer]: Looks correct
