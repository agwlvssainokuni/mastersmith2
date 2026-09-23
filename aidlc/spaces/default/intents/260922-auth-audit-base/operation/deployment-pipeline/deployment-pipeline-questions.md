# Deployment Pipeline の質問（deployment-pipeline-questions）

Intent `260922-auth-audit-base`（auth-audit-foundation）の配備の流れを決めるための質問。

## 前提（確認済みのこと）

- 配備先はまだ決まっていない。当面の配備先は**開発者の PC 上のコンテナ**だけとし、本段はその範囲で配備の流れを書く。配備先が決まったら置き換える（依頼者の回答、team.md・project.md の Deployment）。
- 次のものは既にリポジトリにあり、承認済みの設計（`construction/u1-app-skeleton/infrastructure-design/cicd-pipeline.md` 5章、`infrastructure-specification.md`）に沿っている。本段ではこれを記録し、足りない点だけを決める。
  - `Dockerfile`（Temurin JRE 25、WAR をコピーするだけ、root 以外の利用者、`/app/data` の権限 700）
  - `compose.yaml`（`127.0.0.1:8080`、名前付きボリューム、`.env`、ヘルスチェック、停止の猶予 45 秒、再起動しない）
  - README の「コンテナでの起動と確認」「戻し方」「内部DBのバックアップと戻し方」
  - CI（`.github/workflows/ci.yml`）: `develop` へのプッシュで `./gradlew verify` と WAR の保存（`mastersmith-<コミットのハッシュ>`、30 日）。`v*` のタグで WAR を GitHub のリリースに添付
- 配備のやり方は、コンテナ1台を止めて新しい版で起動し直す形（入れ替え）になる。1台・開発者の PC のため、ブルー/グリーンやカナリアは使わない。機能の切り替えの仕組み（フィーチャーフラグ）も、切り替える対象が無いため持たない。

## Q1. 配備の手順の実行のしかた

今の手順は README に書かれた手作業（バックアップ → WAR を置く → `docker compose up -d --build` → healthy を待つ → ブラウザで確認）です。これをこのまま手で行うか、まとめて実行できるようにするかを決めます。

A. 今のまま README の手順を手で行う（本段では手順の記録と整理だけをする）
B. 手順をまとめたシェルのスクリプトを足す（例: `scripts/deploy-local.sh`。バックアップ → イメージ作成 → 起動 → ヘルスチェックの待ち → スモークテストの順に実行し、失敗したら止まる）
C. 同じ手順を Gradle のタスクとして足す（例: `./gradlew deployLocal`。検査の入口を Gradle にそろえている方針に合わせる）
X. Other (please specify)

[Answer]: A

## Q2. 配備する成果物（WAR）の出どころ

コンテナに入れる WAR をどこから取るかを決めます。成果物はコミットのハッシュで識別する決まりです（team.md の Deployment）。

A. 手元で `./gradlew verify` を通して作った WAR（普段の確認向け。手元の作業ツリーがきれいな状態であることが前提）
B. CI の成果物の WAR（`mastersmith-<コミットのハッシュ>`。`develop` へプッシュした後、検査を通ったもの）
C. リリースのタグ（`v*`）で GitHub のリリースに添付した WAR だけ
D. 普段の確認は A、リリースの確認は C と使い分ける
X. Other (please specify)

[Answer]: A

## Q3. 戻し方（直前の版に戻すとき）

承認済みの設計では、直前の版の WAR からイメージを作り直して起動します。一方、`compose.yaml` のイメージのタグは既定が `local`（`MASTERSMITH_IMAGE_TAG` で変えられる）で、設計の「イメージのタグにコミットのハッシュを付ける」は手順にまだ入っていません。

A. 設計どおり、直前の版の WAR からイメージを作り直して起動する（タグは `local` のままでよい）
B. 配備のたびにイメージにコミットのハッシュのタグを付けて残し、戻すときは直前のタグのイメージで起動し直す（作り直しが要らず速い）
C. B を基本とし、手元にイメージが無いときは A で作り直す
X. Other (please specify)

[Answer]: A

## Q4. 配備の確認（スモークテスト）のしかた

配備はスモークテストが通るまで完了とみなしません（team.md の Deployment）。承認済みの設計の確認項目は、ヘルスチェックが healthy、ログイン画面の表示、初期管理者でのログインとログアウト、「管理」から管理者向け領域が開けること、ログインとログアウトで監査イベントが2件記録されること、です。

A. 今のまま手で確かめる（ブラウザでの操作。監査イベントはボリュームを複写して読み取りで開く）
B. API を呼ぶスクリプトで自動にする（ヘルスチェック・ログイン・管理者向けの確認・ログアウトの応答）。画面の表示と監査イベントの件数は手で確かめる
C. E2E（Playwright。`./gradlew e2eTest` と同じテスト）を起動したコンテナに向けて実行する
X. Other (please specify)

[Answer]: A

## Q5. 戻すときの「直前の版の WAR」の入手のしかた（追加の質問）

Q2 で「手元で作った WAR」、Q3 で「直前の版の WAR から作り直す」を選びました。手元の WAR（`backend/build/libs/mastersmith.war`）はビルドのたびに上書きされるため、戻すときの直前の版の WAR をどこから得るかを決めます。

A. 直前の版のコミットを取り出して、手元で WAR を作り直す（例: `git worktree` で取り出して `./gradlew :backend:bootWar`）
B. CI の成果物（`mastersmith-<コミットのハッシュ>`、30 日保存）か、リリースに添付した WAR を取ってくる（今の README の記述どおり）
C. 配備のたびに、使った WAR を `mastersmith-<コミットのハッシュ>.war` の名前で手元に取っておき、戻すときはそれを使う
X. Other (please specify)

[Answer]: A

## Q6. 動いている版の見分け方（追加の質問）

成果物の版はコミットのハッシュで見分ける決まりです（team.md の Deployment）。手元の WAR を使い、イメージのタグは `local` のままにするため、いまコンテナで動いている版をどう見分けるかを決めます。

A. 配備の手順の中で、作業ツリーに未コミットの変更が無いことを確かめ、そのときのコミットのハッシュ（`git rev-parse --short HEAD`）を控える（控えるだけで、タグは `local` のまま）
B. A に加えて、`MASTERSMITH_IMAGE_TAG` にコミットのハッシュを渡してイメージのタグにも付ける（戻しには使わず、見分けるためだけに使う）
C. 見分けることは求めない（手元での確認だけのため）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答の要約:

- 配備先は未定のまま、開発者の PC 上のコンテナの範囲で配備の流れを書く。配備のやり方は、コンテナ1台を止めて新しい版で起動し直す入れ替えとする。ブルー/グリーン・カナリア・フィーチャーフラグは使わない。
- Q1: 配備は今のまま README の手順を手で行う。本段では手順の記録と整理だけをし、スクリプトや Gradle のタスクは足さない。
- Q2: コンテナに入れる WAR は、手元で `./gradlew verify` を通して作ったもの。
- Q3: 戻すときは、承認済みの設計どおり直前の版の WAR からイメージを作り直して起動する。イメージのタグは `local` のまま。
- Q4: 配備の確認（スモークテスト）は今のまま手で行う。healthy、ログイン画面の表示、初期管理者でのログインとログアウト、管理者向け領域が開けること、監査イベント2件の記録（ボリュームを複写して読み取りで開く）を確かめる。
- Q5: 直前の版の WAR は、直前の版のコミットを取り出して手元で作り直す（`git worktree` などで `./gradlew :backend:bootWar`）。
- Q6: 配備の手順の中で、作業ツリーに未コミットの変更が無いことを確かめ、コミットのハッシュ（`git rev-parse --short HEAD`）を控える。タグは `local` のまま。
- 反映の先: 本段の文書（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）に加えて、README の「コンテナでの起動と確認」と「戻し方」を上の決定に合わせて直す（戻しの WAR の入手を Q5、ハッシュの控えを Q6 に合わせる）。承認済みの設計の「イメージのタグにコミットのハッシュを付ける」は採らず、その差を文書に明記する。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
