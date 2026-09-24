# CI Pipeline — Questions

前提（読み取りだけで調べた結果）:
- CI は前の Intent（260922-auth-audit-base）で作った `.github/workflows/ci.yml`（GitHub Actions）。`develop` へのプッシュ（と `v*` のタグ）で `./gradlew verify` を実行し、WAR を成果物として保存する。この Intent では CI の定義を変えていない。
- この Intent で `verify` の中身は増えた（対象DB の3種類の結合テストをコンテナで毎回、テストの JVM のヒープ 1g、DSL の JSON Schema を WAR に入れた確かめ、拡張の登録の順の構造の検査、H2 の詰め直しのテスト）。GitHub の `ubuntu-latest` には Docker があり、CI（`CI=true`）では Docker に届かなければ対象DB のテストを飛ばさずに失敗させる。
- 手元の最新の `./gradlew verify` は成功（6分17秒、コミット 8961cb2）。`origin/develop` は 39f9aeb で、手元の `develop` は 3 コミット先にある（プッシュは依頼者が行う）。この PC の `gh` はログインしていないため、AI は GitHub の Actions の結果を読めない。
- E2E（`./gradlew e2eTest`）は、前の Intent の決定で CI と `verify` の外に置いている。この Intent で 010〜040 の4本（040 は DSL の管理画面）になった。
- 依存の脆弱性の検査（`osvScan`）は、lockfile が変わらないと手元の `verify` では UP-TO-DATE で飛ばされる（Build and Test で2回そうなった）。CI は毎回まっさらな環境で走らせるため、必ず走る。

## Q1. E2E を CI に入れるか

A. 今のまま CI の外に置く（統合の前とリリースの前に、手元で `./gradlew e2eTest` を実行する）
B. CI に E2E の仕事（job）を足す（Playwright の Chromium を入れ、`verify` の後に `./gradlew e2eTest` を実行する）
X. Other (please specify)

[Answer]: A

## Q2. この Intent の変更で CI が通ることの確かめ方（Build and Test から引き継いだ G-CI）

A. 依頼者が 8961cb2 以降（6f212e8 まで）をプッシュし、GitHub の Actions の結果を確かめて AI に伝える
B. 依頼者が `gh auth login` をして、AI が `gh run list`・`gh run view` で結果を読んで記録する（プッシュは依頼者）
C. この段では確かめず、Unverified のまま Deployment Pipeline に引き継ぐ
X. Other (please specify)

[Answer]: B

## Q3. 手元の `verify` で依存の脆弱性の検査が UP-TO-DATE で飛ばされる件

A. `osvScan` を毎回走らせるようにする（Gradle のタスクの設定を変える。数秒の検査で、新しく公開された脆弱性も統合の前に見つけられる）
B. 今のまま（lockfile が変わらなければ飛ばす。CI では毎回走る）
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進める。

1. CI の定義（`.github/workflows/ci.yml`）は変えない。E2E は今のまま CI の外に置き、統合の前とリリースの前に手元で `./gradlew e2eTest` を実行する（Q1: A）。README の説明は Build and Test で 010〜040 に合わせて直し済み。
2. CI が通ることは、依頼者が `gh auth login` をしたうえで、依頼者がプッシュした後に AI が `gh run list`・`gh run view` で Actions の結果（`./gradlew verify` の成否・時間・対象DB の結合テストの件数）を読んで記録する（Q2: B）。AI はプッシュしない。
3. 手元の `verify` の `osvScan` は今のまま（lockfile が変わらなければ UP-TO-DATE で飛ばす。CI では毎回走る）（Q3: B）。この扱いを quality-gates.md に明記する。
4. 成果物は、project.md の決まりどおり、新しい設計ではなく既にあるものの記録として書く（ci-config.md: きっかけ・段の並び・関門の基準・成果物・固定している版と、この Intent で `verify` に増えた中身。quality-gates.md: Build and Test が記録した検査の一覧との対応、CI の外に置く検査と代わりの実行の場）。
5. Construction から Operation への境目の確かめ（`verification/phase-check-construction.md`）を書く。Build and Test で受け入れた失敗（U4-STORAGE-RUN）と Unverified の13件は、持ち主の段とともに記録する。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

