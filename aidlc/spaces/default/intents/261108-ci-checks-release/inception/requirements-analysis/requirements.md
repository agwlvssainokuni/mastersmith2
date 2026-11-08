# 要件定義（Intent 261108-ci-checks-release）

## 1. 目的

検査と CI を整え、たまった変更をリリースする。

- ルートの `scripts/` の台本にも、ほかのソースと同じ検査（ライセンスヘッダー・書式・リンタ）を当て、CI の報告の台本や新しい自動化の台本の質を保つ。
- 手で書き写したパッケージの一覧（R-07）と、使い道の無くなった `packagesJudgedByTotal` の仕組みを無くし、カバレッジの下限を全パッケージに同じ形で当てる。
- CI の時間（job 35〜39 分）を 42 分の線から離す。
- 毎回手で行う確かめ（A1〜A3）を台本にする。
- 最後に `develop` から `main` へ取り込み、配備の段で前の Intent からの引き継ぎを片付ける。

- 種類: 検査・CI・手順の改善と、リリース（アプリの本体のソース `src/main` は変えない見込み）
- 深さ: Minimal

## 2. 機能の要件

### FR1: ルートの `scripts/` の検査（Q1: A）

- **FR1.1** `scripts/` の `.mjs` を、ライセンスヘッダーの検査の対象に足す（今は `frontend/scripts/check-license-header.mjs` が見ていない）。
- **FR1.2** 同じく、書式の検査（Prettier）とリンタ（ESLint または oxlint。どちらに当てるかはコード生成の計画で決める）の対象に足す。道具と設定は `frontend/` に今ある Prettier・ESLint（oxlint）とその設定を使い回し、新しい npm の依存は足さない（ルートに `package.json` を作らない）。今ある台本で出る指摘は直す。（レビュー R-04 を受けて追記）
- **FR1.3** `scripts/verify-graph-check.mjs` は `./gradlew verify` の中では流さない（手元で必要なときに流す今の扱いのまま）。
- **FR1.4** `docs/verification.md` の「`scripts/` は検査の外」とした記録（前の Intent の依頼者の決定）を、今回の決定に書き換える。

### FR2: R-07 と `packagesJudgedByTotal`（Q4: A）

- **FR2.1** `common.health` にテストを足し、パッケージごとの下限（行 80%・分岐 70%）を満たす（今は行 42/53、79.25%）。`src/main` は変えない。
- **FR2.2** `packagesJudgedByTotal` の 7 パッケージをすべて外し、一覧と、一覧を使った除外の仕組みを `backend/build.gradle.kts` から消す。パッケージごとの下限は、すべてのパッケージに当てる。
- **FR2.3** 一覧を読んでいるもの（`scripts/ci-summary.mjs` の下限の読み取りと印、`docs/verification.md` の確かめのコマンド（R-07）と説明、README）を、一覧が無い形に合わせて直す。R-07 のコマンドは、一覧が無くなるため不要になれば消す。
- **FR2.4** `team.md`（Way of Working の全件の `verify` を流す場面、Testing Posture の `packagesJudgedByTotal` の行）と `project.md` の該当の行を、一覧が無くなった形に書き換え、書き換えの印を付ける。

### FR3: CI の実行環境（Q2: C）

- **FR3.1** `runs-on: ubuntu-latest` のままとする。
- **FR3.2** Ubuntu 26 の上で、CI の道具（JDK・Node・Gradle・Gitleaks・OSV-Scanner・cloc・Testcontainers のコンテナ）が動き、版がどれかを Build and Test の CI の実行で確かめて記録する。合格は、CI の job が通り、各道具の版が記録されていること。動かない道具が出たときは、版の固定（`runs-on` のラベル・道具の版）に切り替えるかを依頼者に諮る。（レビュー R-05 を受けて追記）

### FR4: CI の時間（Q3: A）

- **FR4.1** CI の `./gradlew verify` に `-PitForks=<N>` を付けて、結合テストを並べて流す。
- **FR4.2** N は Build and Test で、CI の上で 2 と 3 を流して時間と結果（件数・失敗・カバレッジ）を比べて決める。（レビュー R-01・R-02 を受けて追記）
  - 流し方: 依頼者が作業ブランチを `origin` にプッシュした後、`gh workflow run` で作業ブランチの CI を手動で2回（N=2・N=3）動かす。N を変える方法（CI の入力の値など）はコード生成の計画で決める。依頼者のプッシュを待つ。
  - 選び方: NFR2（件数・失敗・カバレッジが並べない値と一致）を満たす N のうち、job が最も短い N を選ぶ。2 と 3 の job の差が1分未満なら、資源の余裕を取って小さい方を選ぶ。
  - うまくいかないとき: 2 も 3 も NFR2 を満たさない（失敗・不安定・カバレッジの食い違い）ときは、N を付けない（1つの JVM の）まま依頼者に諮る。
- **FR4.3** `team.md` の「`-PitForks` は CI を変えない」の行を、CI でも使う形に書き換える。`docs/verification.md` も合わせる。

### FR5: 自動化 A1〜A3（Q5: A）

3つとも `scripts/` に置き、FR1 の検査を当てる。ホームのパス・秘密は台本に書かず、置き場は引数と環境変数で受け取る。値（秘密・個人に関する値・行の値）は出さず、名前・件数・ハッシュだけを出す。

- **FR5.1** A1: 振り返りの段の読み取りの確かめを1つの台本にする（動いているコンテナの版とダイジェストと `compose.yaml` の比べ、環境変数の名前の数え、WAR の SHA-256、`.env` の更新の時刻、`docker system df`）。
- **FR5.2** A2: `main` への取り込みの前に、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめる台本（`gh run view --json attempt,jobs`。再実行で通ったものは1回目の失敗とそのテストも出す。CI の実行が無いコミットも出す）。
- **FR5.3** A3: 内部DB の複写を数える道具を1つにまとめる（今はホームの下の2か所の `count-db.sh`・`count-db2.sh`・`count-db3.sh`）。数え方は今の道具を読んで引き継ぐ。ホームの下の古い道具は、依頼者の確認の後に片付ける。
- **FR5.4** 使い方を `docs/` か README に書く。
- **FR5.5** 合格の条件（レビュー R-03 を受けて追記）:
  - A1: 今の手元の環境で流し、FR5.1 の5つの項目がすべて出て、コンテナの版とダイジェストが `compose.yaml` と食い違うものを「食い違い」として示すこと。値（環境変数の値・秘密）が出ないことを出力の検索で確かめる。
  - A2: 前の取り込み（`b2990415`）の後のコミットについて、コミットごとに CI の実行の有無と試行ごとの結論が出て、再実行で通ったものは1回目の失敗が示されること。手で `gh run view` で確かめた結果と一致すること。
  - A3: 今の道具（`count-db.sh`・`count-db2.sh`・`count-db3.sh`）と同じ内部DB の複写に流し、数える項目ごとに今の道具と件数が一致すること。行の値は出さない。

### FR6: リリースと配備（Q6: A）

- **FR6.1** Build and Test で、全件の `./gradlew verify`・E2E 全体を流し、FR5.2 の台本で前の取り込み（`main` の今の先頭 `b2990415`）の後のすべてのコミットの CI を試行ごとに確かめる。
- **FR6.2** 依頼者の承認を得て、`develop` から `main` へ fast-forward で取り込む。プッシュは依頼者が行う。取り込みの直前に FR5.2 の台本を流し直し、取り込む `develop` の先頭のコミットまで（Build and Test の後に積んだ記録のコミットを含む）の CI を確かめる。先頭の CI がまだ終わっていなければ、終わるのを待つ。（レビュー R-06 を受けて追記）
- **FR6.3** 配備の段（Deployment Pipeline・Deployment Execution）を通す。前の Intent（261107-verify-quick-local・261108-ci-size-coverage）の「次の配備へ引き継ぐこと」の一覧を読んで手順に入れ、どのコミットを配備したかを記録する。

## 3. 非機能の要件

- **NFR1（CI の時間）** CI の job の時間を、変える前（35分47秒、実行 42489430268）と並べて記録し、42 分の線（`team.md`）から離れたことを確かめる。目安は 30 分未満とし、届かなければ依頼者に諮る。
- **NFR2（検査とカバレッジを緩めない）** カバレッジの下限（全体と、すべてのパッケージごとの行 80%・分岐 70%）・CI の合否の決まりを緩めない。`packagesJudgedByTotal` を消した後も、全パッケージが下限を満たす。並べて流した CI の結合テストの件数・失敗・カバレッジが、並べない手元の値と一致する（食い違ったら依頼者に諮る）。
- **NFR3（公開してよい中身）** 新しい台本は秘密情報・個人に関する値・ホームのパスを出さず、リポジトリに書かない。
- **NFR4（既存のテスト）** 既存のテストが緑のまま。並べて流して落ちるテストが出たら、`team.md` の「不安定なテストと CI の失敗」の決まりに従う。

## 4. 制約

- `src/main` を変えない見込み。変えることになったら、`team.md` の決まり（全件の `verify`、境界テスト）を当て、配備の段に響くことを依頼者に伝える。
- 新しい依存を足さない（Node の組み込み・git・gh・docker の CLI だけで台本を作る）。

## 5. 前提

- `[assumption]` `audit.repository` は interface だけで、JaCoCo が数えないため、一覧から外しても下限の判定で落ちない（K-134。Build and Test の全件の `verify` で確かめる）。
- `[assumption]` CI の実行環境は 4 vCPU の見込みで、`-PitForks` を 2〜3 にしても対象DB のコンテナと合わせて資源が足りる（Build and Test の CI の実行で確かめる）。
- `[assumption]` 新しい台本（A1〜A3、検査を足した `scripts/` の台本）に単体テストは置かない。前の Intent（261108-ci-size-coverage）の依頼者の読み方（「本体の機能ではない台本には、テストのない機能は完成とみなさないの決まりは当たらない」）を引き継ぐ。確かめは手元で流した結果で行う。`common.health` に足すテストは本体のテストとして置く。

## 6. 範囲の外

- `ubuntu-latest` の版の固定と、cloc の版の固定（Q2: C）。
- CI の job を分けること（Q3 で A を選んだ）。
- `scripts/verify-graph-check.mjs` を `verify` で流すこと（Q1: A）。
- 新しい機能（N3〜N5・N7）。

## 7. 未解決の点

None.

## Sources

- Initial description: 依頼文（`aidlc engine workspace project-description` の description）`[desc]`
- Workflow-selected scope: bugfix `[scope]`
- 質問の答え Q1〜Q6（`inception/requirements-analysis/requirements-analysis-questions.md`）`[Q1]`〜`[Q6]`
- Reverse Engineering の知識ベース（`aidlc/spaces/default/codekb/mastersmith2/`、K-127〜K-137）
- 7 パッケージの今の値: 2026-11-08 の全件の `verify` の `backend/build/reports/jacoco/test/jacocoTestReport.xml`
- CI の時間の内訳: CI の実行 42489430268 のログ
- A1〜A3: Intent 261029-reference-lookup-cleanup の `operation/feedback-optimization/feedback-loop.md` 4節
- `team.md`（Way of Working・Testing Posture・Deployment）、`project.md`（Deployment・Testing Posture）

## Assumptions & Open Questions

- 前提は 5 節のとおり。未解決の点は無い。
