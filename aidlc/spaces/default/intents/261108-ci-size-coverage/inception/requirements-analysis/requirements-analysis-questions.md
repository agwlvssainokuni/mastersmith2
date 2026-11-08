# Requirements Analysis の質問（Intent 261108-ci-size-coverage）

## 決まっていること（質問にしない）

依頼文・これまでの会話の決定・Reverse Engineering の結果から、次は決まっている。

- CI（`.github/workflows/ci.yml` の `verify` の job）の実行の要約（`$GITHUB_STEP_SUMMARY`）に、プログラムの規模とカバレッジを載せる。並びは「規模 → カバレッジ」。
- 規模の数え方・分類の決まり・報告の表は、依頼者の手元の Python の台本のとおりとする（cloc で、git が管理するファイルだけを数える。`aidlc/`・`vendor/`・AI の道具の設定・lockfile を外す。製品・テスト・ビルド・検査・運用・文書に分け、サーバーは機能と層、画面は領域ごとにも出す）。
- 台本は Node に移して `scripts/program-size.mjs` に置く。移した後、手元で Python 版と同じ報告（表の値）が出ることを確かめる。
- CI の cloc は `apt-get install cloc` で入れる。報告に cloc の版を載せる。cloc（GPL-2.0）は CI で道具として動かすだけで WAR に含めないという採用の理由を記録に残す（`team.md` の Code Style の新しい依存のライセンスの決まり）。
- 前の回のプッシュのコミット（`github.event.before`）を `git worktree` で取り出して同じ台本で数え、大分類と中分類の表に差の列を出す。CI は `fetch-depth: 0` で全部の履歴を取っている。
- CI に合わせた直し: 台本と報告から依頼者の手元の置き場のパスを消す（`project.md` の Mandated）、台本の先頭に Apache License 2.0 のヘッダーを付ける、ブランチは `GITHUB_REF_NAME`、`main` の先頭は `origin/main` から取る、未コミットの変更の件数は外す。
- 要約の手順は `if: always()` を付け、`./gradlew verify` が落ちた回にも規模を載せる（今の CI の手順には `if:` が無く、落ちると後ろの手順が流れない。K-119）。
- カバレッジは、バックエンドの JaCoCo の XML（`jacocoTestReport`）と画面の `coverage-summary.json` から、全体の値と、`<details>` で畳んだパッケージごとの表を載せる。CI は全件の `verify` を1本で流すため、値は結合テストを含めた正式な値になる。
- 手元の使い方を `docs/verification.md` に書く。
- CI の検査の中身（`./gradlew verify` の段）と、カバレッジの下限は変えない。

## Q1: HTML のカバレッジの報告を成果物として保存するか

要約に載るのは値の表だけで、行ごとの色分けは HTML の報告でしか見られない。GitHub の画面では HTML をそのまま開けず、zip を落として手元で開く形になる。

推奨: A。保存の手間は手順1つで、CI の時間はほとんど増えない。保存の日数を WAR（30 日）より短くして、置き場の使用量を抑えられる。

A. JaCoCo と Vitest の両方の HTML の報告を、1つの成果物として 14 日保存する
B. 保存しない（要約の値の表だけ）
C. JaCoCo の HTML だけ保存する（14 日）
X. Other (please specify)

[Answer]: A

## Q2: 要約の手順が失敗したときの扱い

報告のファイルが無い・cloc が入らない・前の回のコミットが取れないなど、要約を作る手順そのものがうまくいかないことがある。

推奨: A。要約は検査ではなく報告なので、要約の不具合で CI の結果（検査の合否）を変えないため。

A. 要約の手順は CI を失敗にしない。作れなかった欄には理由を書き（例:「報告なし（./gradlew verify が途中で落ちた）」）、警告（`::warning::`）を出す
B. 要約の手順が失敗したら CI も失敗にする
X. Other (please specify)

[Answer]: A

## Q3: 前の回のコミットが使えないときの扱い

`github.event.before` は、手動の実行（`workflow_dispatch`）とタグ（`v*`）のプッシュでは前の回を指さない。新しいブランチの最初のプッシュでは 0 が並ぶ値になり、force-push の後は前のコミットが取れないことがある（K-123）。

推奨: A。どのきっかけでも1つの決まりで扱え、差の値が誤った基準と比べたものにならないため。

A. 前の回のコミットが使えないとき（0 が並ぶ値・取れない・手動の実行・タグ）は差の列を「—」にし、要約に理由を1行書く
B. 使えないときは `origin/develop` の1つ前のコミット（`HEAD~1`）と比べる
X. Other (please specify)

[Answer]: A

## Q4: `scripts/program-size.mjs` の検査とテスト

ルートの `scripts/` は、今はライセンスヘッダー・書式・リンタ・テストのどの検査の対象でもない（K-121）。`scripts/verify-graph-check.mjs` も同じで、前の Intent で検査に入れるかを後に持ち越している。express の Test Strategy（Minimal）では、要件ごとの単体テストが要る。

推奨: A。CI で毎回流れる台本になるため、分類の決まりと差の計算を単体テストで固め、ヘッダーの検査にも入れる。テストは Node の組み込みの `node --test` で、新しい依存を足さない。

A. ルートの `scripts/` の `.mjs` をライセンスヘッダーの検査の対象に足し、`scripts/program-size.mjs` の分類と差の計算の単体テストを `node --test` で書いて `./gradlew verify` の中で流す
B. A と同じだが、単体テストは `./gradlew verify` に入れず手元で流すだけにする
C. 検査とテストは足さない（この Intent の範囲の外とする）
X. Other (please specify)

[Answer]: C

## Q5: カバレッジのパッケージごとの表の範囲

推奨: A。バックエンドはパッケージごとの下限（行 80%・分岐 70%）があり、`packagesJudgedByTotal` の 7 パッケージの値を CI で見られる。画面は `team.md` で新しい機能のフォルダ（`src/features/<featureId>/`）の値を記録することになっているため、領域ごとの表があると拾える。

A. バックエンドはパッケージごと（下限との差と、`packagesJudgedByTotal` の印つき）、画面は領域ごと（`features/<機能>`・`app/<名前>`・`shared/<名前>`。`coverage-summary.json` のファイルごとの値を足し合わせる）の表を、どちらも `<details>` で畳んで載せる
B. バックエンドのパッケージごとの表だけ載せる（画面は全体の値だけ）
C. パッケージごとの表は載せない（全体の値だけ）
X. Other (please specify)

[Answer]: A

## Q6: テストの件数を載せるか

推奨: A。バックエンドの件数は今ある JUnit の XML から読める。画面の Vitest は件数を出す報告の形式が今は無いため、件数を載せるなら Vitest の設定に `json` の報告を足す必要があり、手間が増える。

A. バックエンド（単体テスト・結合テスト）の件数だけ載せる（通った・失敗・飛ばした）
B. バックエンドと画面（Vitest）の件数を載せる（Vitest に件数の報告を足す）
C. テストの件数は載せない
X. Other (please specify)

[Answer]: A

## F1: Q4 の C（テストを足さない）と決まりとの食い違い

Q4 の答え C は、次の決まりと食い違いうる。

- `team.md` の Testing Posture: 「テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない」
- `org.md` の express の Minimal: 要件ごとの単体テスト（部品ごとに通常の流れの1件以上）

推奨: B。台本の単体テストを置けば決まりを満たし、`./gradlew verify` と CI の時間は変わらない（Q4 で検査に入れないと決めた点はそのまま守れる）。

A. Q4 の C のままとする。テストの代わりに、手元で Python 版と同じ報告が出ることを確かめた結果と CI の1回の実行の要約で確かめ、決まりとの差（テストを置かない理由）を要件と Build and Test の記録に書く
B. 単体テスト（分類の決まりと差の計算。`node --test`）は置くが、`./gradlew verify` にも CI にも入れず、手元で流すだけにする。ヘッダーの検査の対象は広げない
C. Q4 を A に変える（ヘッダーの検査に足し、単体テストを `./gradlew verify` で流す）
X. Other (please specify)

[Answer]: X. Q4 の C のまま。本体の機能ではないのでルールは適用されない。
