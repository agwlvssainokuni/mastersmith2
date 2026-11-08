# Requirements Analysis の質問（Intent 261108-ci-checks-release）

## 決まっていること（質問にしない）

- 1つの Intent で、(1) 台本の検査と R-07、(2) Ubuntu 26、(3) CI の時間、(4) `packagesJudgedByTotal`、(5) 自動化 A1〜A3、(6) リリース の順に進める。リリースは最後（依頼者の決定）。
- R-07: `docs/verification.md` の `packagesJudgedByTotal` の確かめのコマンドに書き込まれたパッケージの一覧をやめ、`backend/build.gradle.kts` から読む形にする（`scripts/ci-summary.mjs` の読み方と同じ考え方。K-131）。下の Q4 で一覧そのものを消すなら、コマンドも合わせて直す。
- `packagesJudgedByTotal` の今の値（2026-11-08 の全件の `verify` の JaCoCo の報告、`develop` と同じソース）:

  | パッケージ | 行 | 分岐 | パッケージごとの下限（行 80%・分岐 70%） |
  |---|---|---|---|
  | `access.service` | 11/11（100%） | 分岐なし | 満たす |
  | `audit.repository` | 数える行なし（interface だけ） | — | 数える対象が無い（K-134。`verify` で判定が通るかは Build and Test で確かめる） |
  | `common.error.domain` | 41/41（100%） | 17/18（94.44%） | 満たす |
  | `common.error.service` | 28/28（100%） | 13/16（81.25%） | 満たす |
  | `common.health` | 42/53（79.25%） | 2/2（100%） | **行が 0.75 ポイント足りない**（あと1行で届く） |
  | `common.i18n.domain` | 38/38（100%） | 25/28（89.29%） | 満たす |
  | `common.web` | 110/114（96.49%） | 46/54（85.19%） | 満たす |

  7 つのうち 6 つは今のままで下限を満たす。`common.health` はテストを足せば届く見込みで、`src/main` は変えない見込み（変えるなら `team.md` の決まりで全件の `verify` と境界テストが当たる）。
- CI の時間の内訳（`develop` の CI の実行 42489430268、job 35分47秒、`./gradlew verify` 34分44秒。ログの Gradle のタスクの切り替わりの時刻から見積もった値）: `:backend:integrationTest` 約 25.5 分（全体の約 7 割）、`frontendCoverage` 約 3.2 分、`frontendTest` 約 2.5 分、ほかは1分未満。CI は結合テストを `-PitForks` なしの1つの JVM で流している（K-129）。手元では N=1 が約 23 分、N=3 が 9分43秒だった。
- CI の実行環境: `runs-on: ubuntu-latest`（2か所）。前の CI では apt の取得元が Ubuntu 26.04（resolute）で、cloc は 2.06 だった。JDK は `"25"`、Node は `"26"` の大きな版だけを指定している（K-128）。
- A1〜A3 の中身（Intent 261029 の振り返りの 4節）:
  - A1: 振り返りの段の読み取りの確かめ（コンテナの版とダイジェストと `compose.yaml` の比べ、環境変数の名前の数え、WAR の SHA-256、`.env` の時刻、`docker system df`）を1つの台本にする。値は出さない（名前と件数とハッシュだけ）。
  - A2: `main` への取り込みの前の CI の試行ごとの確かめ（`gh run view --json attempt,jobs`、前の取り込みの後のすべてのコミット）を台本にする。
  - A3: 内部DB の複写を数える道具（今はホームの下の2か所に `count-db.sh`・`count-db2.sh`・`count-db3.sh` がある）を1つにまとめる。
- リリース: `team.md` の決まりで、全件の `./gradlew verify`・E2E 全体・前の取り込みの後のすべてのコミットの CI の試行ごとの確かめを済ませ、依頼者の承認を得て `develop` から `main` へ fast-forward で取り込む。`main` へのプッシュでは CI は動かない（K-136）。プッシュは依頼者が行う。
- `team.md`・`project.md` の決まりで今回の変更と食い違う行（`scripts/` を検査の外とした記録、`-PitForks` は CI を変えない、`packagesJudgedByTotal` の行など）は、決まった内容に合わせて書き換え、書き換えの印（日付と Intent）を付ける。

## Q1: ルートの `scripts/` の検査

前の Intent（261108-ci-size-coverage）では、依頼者の決定で `scripts/` をどの検査の対象にもしなかった（`docs/verification.md` に記録）。今回はそれを変える。`scripts/` の台本は今 4 本（`verify-graph-check.mjs`・`program-size.mjs`・`ci-summary.mjs` と比べの元の一覧）。`verify-graph-check.mjs` は中で `./gradlew --dry-run` を流す（K-130）。

推奨: A。ヘッダー・書式・リンタは軽く、`verify` の時間はほとんど増えない。`verify-graph-check.mjs` を `verify` の中で流すと、Gradle の中から Gradle を呼ぶ形になり、時間も延びるため、手元で必要なときに流す今の扱いのままにする。

A. `scripts/` の `.mjs` を、ライセンスヘッダーの検査・書式（Prettier）・リンタ（ESLint または oxlint）の対象に足す。`verify-graph-check.mjs` は `verify` の中では流さない（手元で流す今の扱いのまま）
B. A に加えて、`verify-graph-check.mjs` を `verify` の段に入れる
C. ライセンスヘッダーの検査だけに足す（書式とリンタは当てない）
X. Other (please specify)

[Answer]: A

## Q2: CI の実行環境の版

推奨: A。`ubuntu-latest` は GitHub が移すたびに道具の版が知らせなく変わる（今回の cloc 2.06 のように）。版のラベルで固定すれば、変わるのは自分で上げたときだけになる。JDK・Node は大きな版の中の最新を取る今の形のままとする（直しの版は自動で取り込みたいため）。

A. `runs-on` を `ubuntu-26.04` に固定する（2か所）。JDK・Node・cloc の指定は今のまま
B. A に加えて、cloc も GitHub のリリースの版と SHA-256 で固定する（apt をやめる）
C. `ubuntu-latest` のままにする（道具の版と動きを Build and Test で確かめるだけ）
X. Other (please specify)

[Answer]: C

## Q3: CI の時間の縮め方

結合テストが CI の時間の約 7 割を占める。

推奨: A。job を分けずに済み、カバレッジの判定と要約の台本を組み直さなくてよい。CI の実行環境（4 vCPU の見込み）で並べる数は、Build and Test で 2 と 3 を CI で測って決める。`team.md` の「`-PitForks` は CI を変えない」の行は書き換える。

A. CI の `./gradlew verify` に `-PitForks=<N>` を付ける（N は Build and Test で CI で測って決める）
B. job を分ける（検査・単体テスト・結合テストなど）。カバレッジの判定と要約を最後の job でまとめる
C. 変えない（時間を測って記録するだけ）
X. Other (please specify)

[Answer]: A

## Q4: `packagesJudgedByTotal` の扱い

決まっていることの表のとおり、6 つは今のままで下限を満たし、`common.health` は行が 0.75 ポイント足りない。`team.md` の決まりで、一覧は一度外したら戻さず、増やさない。7 つを全部外すと一覧は空になり、Kotlin の書き方が変わって `scripts/ci-summary.mjs` が読めなくなる（K-135）。

推奨: A。一覧は増やさない決まりのため、空になった後に残しても使い道が無い。仕組みごと消せば、読む側（要約の台本・手順書のコマンド・`team.md` の行）も単純になる。

A. `common.health` にテストを足して下限に届かせ、7 つすべてを外して、一覧と除外の仕組みを消す（要約の台本・手順書・`team.md` の行も合わせて直す）
B. 7 つすべてを外すが、空の一覧と仕組みは残す（要約の台本を空の一覧も読める形に直す）
C. 今のままで満たす 6 つだけを外し、`common.health` は残す
X. Other (please specify)

[Answer]: A

## Q5: 自動化 A1〜A3 の置き場

A1・A3 は手元の環境（コンテナ・内部DB の複写）を読む台本で、今はホームの下に散らばっている（A3）。

推奨: A。リポジトリに置けば版の管理と Q1 の検査が当たり、PC を替えても同じ手順で使える。ホームのパスや秘密は台本に書かず、置き場は引数で受け取る。

A. 3つとも `scripts/` に置く（ホームのパス・秘密は書かず、引数と環境変数で受け取る。値は出さず名前・件数・ハッシュだけを出す）。ホームの下の古い道具は、依頼者の確認の後に片付ける
B. A2 だけ `scripts/` に置き、A1・A3 はホームの下の1か所（`~/.mastersmith-tools/`）にまとめる
C. A2 だけ作り、A1・A3 は見送る
X. Other (please specify)

[Answer]: A

## Q6: リリースと配備の段

`team.md` の Deployment では、どのコミットをリリースしたかを `main` の先頭と Deployment Execution の記録で追う。前の Intent（261107・261108-ci-size-coverage）は配備の段を飛ばし、「次の配備へ引き継ぐこと」の一覧を残している。この Intent で `src/main` を変えない見込みなら、WAR の中身は前の配備から変わらない。

推奨: A。`main` に取り込むときの版を配備の記録に残し、たまっている「次の配備へ引き継ぐこと」の一覧もここで片付けられる。

A. `main` へ取り込んだ後、配備の段（Deployment Pipeline・Deployment Execution）を通し、前の Intent の「次の配備へ引き継ぐこと」の一覧を読んで手順に入れる
B. `main` への取り込みだけを行い、配備の段は飛ばす（引き継ぐことの一覧は次の配備へ持ち越す）
X. Other (please specify)

[Answer]: A
