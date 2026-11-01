# CI Pipeline の質問

CI の仕組み（GitHub Actions の `.github/workflows/ci.yml`、`./gradlew verify` の1コマンド）は前の Intent から実装済みで、この Intent では `ci.yml` を変えていない。この段の文書は、新しい設計ではなく、今あるものとこの Intent の差の記録として書く（`project.md` の学び）。

## 決まっていること（質問にしない）

- **CI の道具・きっかけ**: GitHub Actions。`develop` への push（と `workflow_dispatch`）で、ジョブ `verify`（`timeout-minutes: 60`）が `./gradlew verify` を流し、WAR を `mastersmith-<コミットのハッシュ>` の名前で 30 日保存する。`v*` のタグで動くジョブ `release` は、配備先が決まるまでタグを付けないため動かさない（`team.md` の Deployment）。
- **段の並び**: `verify` の 0〜9 の段（準備 → フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト（組み込みの H2 と3種類の対象DB のコンテナ）→ カバレッジの下限 → 安全の検査 → 成果物）。1つでも失敗したら全体を失敗とする（`team.md` の Deployment、`build.gradle.kts` の `verifyStages`）。
- **分岐の戦略と統合の関門**: 日々の統合先は `develop`、Bolt ごとの短命のブランチを squash（サブモジュールの固定先の更新を含むときは fast-forward）で戻す。プルリクエストは使わない。**統合の前の関門は手元の `./gradlew verify`**（CI と同じタスク）で、CI は統合の後の再確認とする（`team.md` の Way of Working、`project.md` の「CI も同じタスクを呼ぶ」）。
  - そのため、Build and Test の N/A の行 **BT-CI-MERGE**（各単位の Testing Contract の「classic は CI の実行を統合の前に求める」）は、この段で新しい仕組みを作らず、「CI と同じ `./gradlew verify` を統合の前に手元で流すことが統合の前の関門であり、CI は統合の後に同じタスクで再確認する」という今の形で受け持つ。`quality-gates.md` に、統合の前の関門（手元）と CI の関門の対応として記録する。org の既定（統合の前に CI）と形が違う点は、`team.md` の決まり（プルリクエストを使わない）による差として明記する。
- **関門の基準**: 重大度 High 以上で止める。SpotBugs の priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず止める。カバレッジは全体と新しいパッケージごとに行 80%・分岐 70%（`team.md`）。記録（`evidence/`）の JWT の形は1件でも止める（この Intent の U4、`team.md` の Testing Posture の伏せ字の行）。
- **成果物と版**: フロントエンドを同梱した実行可能 WAR。版はコミットのハッシュで識別する。配備先が決まるまで、レジストリと SBOM は置かない（`team.md`）。固定している版（Actions はコミットのハッシュ、JDK 25（Temurin）・Node.js 24、Gitleaks 8.30.1・OSV-Scanner 2.6.0 は版と SHA-256）は `ci.yml` のとおりで、この Intent で変えていない。
- **CI の外に置く検査**: E2E（`./gradlew e2eTest`）は `verify` と CI の外に置き、対象のブランチの統合の前・Intent の最後（Build and Test、この Intent は 277 件 passed）・リリースの前に手元で流す。k6 の負荷の試験は使い捨ての環境で手元で流す（Build and Test は試し走りまで、判定の回は Performance Validation）（`team.md`・`project.md`）。
- **この Intent の差（CI が流すもの）**: `ci.yml`・`.github/dependabot.yml`・`.gitleaks.toml`・`frontend/vitest.config.ts` は変えていない。`build.gradle.kts`（B5 の `13883f6c`）で `verify` に `perfToolFormatCheck`（1 の段）・`perfToolLint`（2 の段）・`perfEvidenceScan`（8 の段）を足し、ライセンスヘッダーの検査に `perf/` の `.mjs`・`.js` を加えた。CI は `./gradlew verify` を呼ぶだけのため、`ci.yml` を変えずにこれらが CI でも流れる（`13883f6c` の後のすべての run で成功）。ほかに、結合テストに参照の IT が加わり（結合 1,654 件）、画面のテストに U4 の 115 件が加わった。`frontend/package.json` は依存の入れ直し（`41b8bf51`）だけ。
- **CI の時間**: 前の main の取り込みの後の 34 の run の検査の job は 23分17秒〜37分45秒（最大 `c91d831d`）で、42 分の線（上限 60 分の7割）の内。テストの分け方を諮る必要は無い（`team.md` の Testing Posture）。
- **試行ごとの記録**: 再実行で通ったのは2つだけで、Build and Test の `test-results.md` 7節に記録済み。この段で gh で取り直して一致を確かめた。
  - `a75baec4`（run 41161783903）: 1回目 failure（`ReferenceSameResponseIT` の「the order of the checks: format 400 before names 403, names 403 before the EQUALS type 400」。目印 "abc" が乱数のトレースID に偶然に当たった）、2回目 success。`a3099f80` で直し、以後の CI は1回目で成功。同じテストの二度目の失敗は無い。
  - `cf887ad8`（run 41025421073）: 1回目 cancelled（job 0 件、テストは流れていない）、2回目 success。
- **まだ終わっていない CI**: `e1e028d1`（Build and Test の段の承認の記録、run 41264314230）は 2026-11-01T02:14 に始まり、この質問を書いた時点で実行中。記録だけのコミットでも新しい脆弱性の公表で落ちうるため（`project.md` の学び）、結果を試行ごとに確かめて `ci-config.md` に足す。開いている Dependabot のプルリクエストは 0 件。
  - **追記（結果、2026-11-01）**: run 41264314230 は1回目（`:frontendTest`）・2回目（02:20:49Z〜02:56:46Z、`:frontendCoverage`）とも failure。どちらも画面のテスト 238 files・2,312 件は passed で、make-you-chic-ui の Tooltip の 300 ms の時計から出た未処理の例外 `ReferenceError: window is not defined`（`BusinessDataListPage.write.test.tsx` の実行中）が1件。同じ誤りの二度目のため、`team.md` の決まりで直すまで進めないと判断した。30 分の再現の試み（依頼者の決定 9594abae、`flaky-repro-record.md`）では手元で再現せず、仕組みはソースで確かめた。依頼者の決定（e4538e84）B4+A で、`cac6fa54`（`frontend/vitest.setup.ts` で、テストの片付けの後に残った時計の予約を取り消す）と、上流の直し（依頼7 → `cab4207`）の固定先の更新 `cb23466b` を入れ、`develop` へ fast-forward で統合した。`cb23466b` の CI（run 41270200479）は1回目で success（検査の job 28分49秒）。詳しくは `ci-config.md` 6.3・6.4。

この段で新しく決める論点は無いため、質問を作らず、要点の確認だけを行う（`project.md` の学び）。

## Consolidated Summary Confirmation

**ci-config**
- 今ある CI（GitHub Actions、`develop` への push、`./gradlew verify` の 0〜9 の段、WAR の保存、`release` のジョブは動かさない）と、固定している版（Actions のハッシュ・JDK・Node・Gitleaks・OSV-Scanner）を記録する。
- この Intent の差（`ci.yml` は変えていない、`verify` に `perfToolFormatCheck`・`perfToolLint`・`perfEvidenceScan` が加わり CI でも流れる、ライセンスヘッダーの検査の対象に `perf/` が加わった）を書く。
- CI の時間（検査の job 23分17秒〜37分45秒、42 分の線の内）と、`e1e028d1` の run 41264314230 の結果を書く。

**quality-gates**
- 統合の前の関門（手元の `./gradlew verify`）と CI の関門の基準を、Build and Test が記録した検査の一覧（コマンド）と1つずつ対応づける。BT-CI-MERGE は、手元の verify が統合の前の関門で CI は統合の後の再確認という今の形で受け持ち、org の既定との差を書く。
- CI の外に置く E2E・k6 は、その旨と代わりの実行の場を書く。
- 前の main の取り込みの後のすべてのコミットの CI の試行ごとの結果（`a75baec4` の1回目の `ReferenceSameResponseIT` とその直し `a3099f80`、`cf887ad8` の1回目の cancelled、以外は1回目で成功）を書く。

**phase-check-construction**
- 5つの単位の build と test、各 `traceability.json`、Build and Test の網羅の確かめ（195 行: Met 176・Not Met 0・Unverified 18・N/A 1。Unverified は Performance Validation・Observability Setup などの持ち主の段へ引き継ぐ）を確かめ、Construction から Operation への境の判定を書く。

Does this all look correct before I generate the CI pipeline artifacts?

- Looks correct
- Request changes

[Answer]:
