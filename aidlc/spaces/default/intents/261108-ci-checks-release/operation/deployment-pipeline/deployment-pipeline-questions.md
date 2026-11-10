# Deployment Pipeline の質問（Intent 261108-ci-checks-release）

この Intent は bugfix です。検査・CI・確かめの台本を整え、`develop` を `main` へ取り込みました（`main` は `a9ddc077`、依頼者がプッシュ済み）。この質問は、その版の配備の流れを決めるためのものです（要件 FR6.3、Q6: A）。

前の配備の記録（Intent `261104-tz-options-perf` の `cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）と、既にある `Dockerfile`・`compose.yaml`・`docs/deployment.md`・`docs/internal-db.md`・`docs/operations.md` を正とします。質問は、今回の差で判断が分かれる点だけです（`project.md` の Deployment の学び）。CI Pipeline と Infrastructure Design の段はこの Intent の流れに無いため、上流は Build and Test の記録とリポジトリです。

## 調べて分かったこと（判断の前提）

- **前の配備の後に、画面のファイルが1つのコミットで変わっています。** 前の配備（261104、`d9e35417`）の後の `04b797d8`「使われていない画面の関数・型・文言の鍵を消す」（どの Intent にも属さないコミット）が、`frontend/src` の 16 ファイルから使われていない関数・型・文言の鍵（`dsl.status.at`）を消しました。画面の振る舞いは変えない直しですが、WAR の中の画面のファイルは前の配備と変わります。
- ほかの変更は配備に入りません。`backend/src/main`・`Dockerfile`・`.env.example`・`backend/gradle.lockfile` は前の配備から差 0 です。`backend/build.gradle.kts` の差は検査とカバレッジの判定のタスクだけです。`compose.yaml` の差は Mailpit の `MP_SMTP_DISABLE_RDNS` だけで、動いている Mailpit には既に当たっています（`docker inspect` で 1 件）。
- 内部DB の移行と `.env` の変更はありません。
- **アプリのコンテナ `mastersmith-app-1` が今ありません**（`docker ps -a` に無い）。いつ消えたかは記録から分かりませんでした。前の配備のイメージ `mastersmith:local`（`4ee5fb45593e`、261104 で配備した版）と内部DB のボリューム `mastersmith_mastersmith-data` は残っています。見本の PG（`postgres:18.6`）と Mailpit（`v1.33.0`）は動いています。
- 前の Intent の「次の配備へ引き継ぐこと」を読みました。
  - 261107-verify-quick-local: 配備の段で確かめる項目は無い。リリースの前の全件の `verify` と、前の取り込みの後のすべてのコミットの CI の試行の確かめは、この Intent の Build and Test とリリースの前に済ませた。
  - 261108-ci-size-coverage: 要約の差の列は、依頼者が `develop` のプッシュの CI で確かめた。CI の job の時間は、この Intent で `-PitForks=3` にして 26分27秒（実行 42647513146）。
  - この Intent: `main` へ取り込んだ後の最初の `develop` のプッシュの CI（`24ed24e6`、実行 42704002194）は、この質問を書いた時点で走っている最中。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。依頼者の承認のうえで手で配備する。1つのコンテナを置き換える形 | `team.md`・`project.md` の Deployment |
| 配備する版 | `main` の先頭 `a9ddc077`（配備に入るアプリのコードは `04b797d8` までと同じ）。WAR を作り直し（`./gradlew :backend:bootWar --rerun-tasks`）、SHA-256 と、文言の鍵 `dsl.status.at` が画面のファイルに無いことを確かめる。Dockerfile は手元の WAR を写すだけのため | `project.md` の学び（WAR の確かめ） |
| 戻し先 | 前の配備のイメージ `4ee5fb45593e` に、入れ替えの前に戻し先のタグ `mastersmith:pre-ci-checks-release` を付ける | 前の手順 |
| 戻しの形 | イメージだけ。移行と `.env` の変更が無いため、戻しの練習は行わない | `project.md` の Deployment（2026-11-07 の決まり） |
| 内部DB の複写 | 入れ替えの前に1回（バックアップ）。アプリは今止まっている（コンテナが無い）ため、止める操作は要らない。配備の後の複写と数えは、スモークテストで監査に残る操作を行ったときだけ | `project.md` の Deployment（2026-11-07 の決まり） |
| 配備の前の検査 | verify・E2E・CI は Build and Test とリリースの前の確かめの結果を正とし、流し直さない（verify 通過・E2E 298 件 passed・前の取り込みの後の CI は台本 A2 で確かめ済み）。最初の `develop` のプッシュの CI は Deployment Execution で結論を確かめる | `construction/build-and-test/`、FR6.2 |
| 配備の前の k6 | 流さない。サーバーの処理と内部DB は前の配備と同じで、差は画面の使われていないコードの削除だけ | `project.md` の学び（試験済みのときは省く） |
| 手元の環境の確かめ | 台本 A1（`scripts/ops-readonly-check.mjs`）を配備の前と後に流し、コンテナの版とダイジェストを `compose.yaml` と比べ、WAR の SHA-256 を出す（`.env` は項目の名前だけ） | この Intent の FR5.1、`docs/operations.md` |
| 秘密・個人に関する値 | `.env` の値、初期管理者のメールアドレス、業務データの行の値を表示しない。複写はリポジトリの外（`~/.mastersmith-backup/`、置き場 700・ファイル 600） | `project.md` の Forbidden と学び |

## Q1. [リリースの担当] スモークテストの範囲

画面のファイルの差は、使われていないコードの削除だけです（E2E 298 件で確かめ済み）。ただし、配備した版の画面のファイルを実際のブラウザで読むのは、配備の後が初めてです。

**推奨: A。** 理由:

- 配備した WAR の画面がログインの後に動くことを、実際のブラウザで1回確かめられます。
- 消したコードがあった画面（DSL の状態・業務データの一覧のページ送り・役割・グループ・役割の引き継ぎ・利用者の管理・表示のタイムゾーン）を、見るだけで回れます。保存はしません。
- 監査に残るのはログイン（`LOGIN_SUCCEEDED`）だけです。ただし、監査に残る操作をするため、配備の後の複写と数えが要ります（Q2）。

A. AI が未認証の確かめ（healthy・`/actuator/health`・画面の入口と favicon の 200・API の 401）とアプリのログを行い、依頼者が Chrome で初期管理者でログインして、上の画面を開いて見る（保存しない）。配備の後に内部DB を複写して監査の件数を数える
B. AI の未認証の確かめとアプリのログだけにする（ログインしない）。監査に残る操作が無いため、配備の後の複写と数えは行わない。画面は E2E で確かめ済みとし、差を `cd-config.md` に書く
X. Other (please specify)

[Answer]: A

## Q2. [リリースの担当] 内部DB の複写の数え（台本 A3 が `.env` を読むこと）

台本 A3（`scripts/count-db-copy.mjs`）は、初期管理者の状態を数えるために `.env` から初期管理者のメールアドレスを読みます（出力は「`.env` にあるか」の 0・1 だけで、値は出しません）。`docs/internal-db.md` で、AI が流す前に依頼者の許しを得ることにしています。

**推奨: A。** 理由:

- 配備の前の複写の数えが、救済の見込み（初期管理者の停止・管理の印）と監査の比べの基準になります。
- 2回分をここでまとめて許しておけば、配備の途中で止まらずに済みます。

A. 配備の前の複写と配備の後の複写（Q1 で A のとき）の2回、A3 を `--set basic,audit` で流すことを許す。ほかの回は流す前に改めて尋ねる
B. 配備の前の複写だけ A3 で数える。配備の後（Q1 で A のとき）は、流す前に改めて尋ねる
C. A3 を流さない（`.env` を読ませない）。複写は取るが数えない。救済の見込みは依頼者の答えだけで決める
X. Other (please specify)

[Answer]: A

## Q3. [リリースの担当] 古いイメージの片付け

新しい戻し先 `mastersmith:pre-ci-checks-release`（`4ee5fb45593e`）ができると、`mastersmith:pre-tz-options-perf`（`ffd2b8380460`、261101 で配備した版、655 MB）は2つ前の版になります。`docker system df` ではイメージの 84%（6.4 GB）が取り戻せる状態です。

**推奨: A。** 理由:

- 戻し先は1つ前の版で足ります。さらに前へ戻すときは、`main` の履歴から WAR とイメージを作り直せます（`docs/deployment.md` の「戻し方」）。
- 消すのは配備の完了の後で、使うコンテナが 0 件であることを確かめ、消す一覧の承認を得てからです。

A. `mastersmith:pre-tz-options-perf` を片付けの候補にし、Deployment Execution の終わりに一覧を示して承認を得てから消す。ほかのイメージ（タグの無い対象DB・監視・k6 など）には触れない
B. 片付けない（今回は残す）
X. Other (please specify)

[Answer]: A

## Sources

- `git log`・`git diff --stat d9e35417 a9ddc077`（前の配備から今の `main` まで）、`git show 04b797d8`
- `docker ps -a`・`docker images`・`docker inspect`・`docker system df`・`docker volume inspect`（読み取り）
- `node scripts/ci-attempts.mjs --from a9ddc077`（最初の `develop` のプッシュの CI）
- 前の手順: `aidlc/spaces/default/intents/261104-tz-options-perf/operation/deployment-pipeline/`、同じ Intent の `operation/deployment-execution/deployment-log.md`
- 引き継ぎの一覧: `aidlc/spaces/default/intents/261107-verify-quick-local/construction/build-and-test/build-and-test-summary.md`、`aidlc/spaces/default/intents/261108-ci-size-coverage/construction/build-and-test/build-and-test-summary.md`、この Intent の `construction/build-and-test/build-and-test-summary.md`
- 要件: この Intent の `inception/requirements-analysis/requirements.md`（FR6.3）
- `docs/internal-db.md`（A3）・`docs/operations.md`（A1）・`docs/deployment.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）・`project.md`（Deployment）・`aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`
