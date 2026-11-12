# Deployment Pipeline の質問（Intent 261110-deps-forks-review）

この Intent は bugfix です。依存・道具・イメージを最新にし、`-PitForks` の既定の値を確定しました。依存の更新で WAR とイメージの中身が変わるため、通常どおり配備します（要件 FR6.1）。

前の配備の記録（Intent `261108-ci-checks-release` の `cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`・`deployment-log.md`）と、既にある `Dockerfile`・`compose.yaml`・`docs/deployment.md`・`docs/internal-db.md`・`docs/operations.md` を正とします。質問は、今回の差で判断が分かれる点だけです（`project.md` の Deployment の学び）。CI Pipeline と Infrastructure Design の段はこの Intent の流れに無いため、上流は Build and Test の記録とリポジトリです。

## 調べて分かったこと（判断の前提）

- **前の配備（`a9ddc077`）の後に、配備に入る変更は次の4つです。**
  - `backend/gradle.lockfile`: networknt json-schema-validator 3.0.6 → 3.0.9、Jackson 3.1.7 → 3.1.8、itu 1.14.0 → 1.17.0（どれも実行時の依存）。
  - `frontend/package-lock.json`・`frontend/package.json`: 画面の依存（i18next 27・react-i18next 18 ほか）。
  - `frontend/src/features/tables/DateTimeField.tsx`・`SearchPanel.tsx`: i18next 27 の型に合わせ、文言の関数を包んだだけ（振る舞いは変えない）。業務データの検索の欄で、日時の入力のヒントと誤りの文言に使う。
  - `backend/src/main`・`Dockerfile`・`.env.example` は差 0。Spring Boot 4.1.2・H2 2.4.240・JRE のイメージ（`eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a5…`）は変わらない。
- 配備に入らない変更: `compose.yaml` の Mailpit・lgtm・collector・postgres・mysql の版とダイジェスト（Build and Test で作り直して当て済み）、`.github/`・`gradle/`・`docs/`・`perf/`・テスト・`aidlc/`。
- 内部DB の移行と `.env` の変更はありません（最後の移行は V14 のまま）。
- **アプリのコンテナ `mastersmith-app-1` は止まっています**（Exited (143)、2 日前。この Intent の Code Generation の前から止まっている）。イメージは前の配備の `mastersmith:local`（`0d4b6e8866a9`）。見本の PG・Mailpit・lgtm・collector も止まっています。
- 前の取り込み（`main` の `a9ddc077`）の後の `develop` の 25 コミットを台本 A2（`node scripts/ci-attempts.mjs`）で確かめました。最後の試行が通っていない実行は 0 件です。`b4c8d77e` の実行 43038873152 は1回目が cancelled（`e566faa4` のプッシュで待ちが入れ替わった）で、2回目が success です。先頭の `b8d61840`（Build and Test の承認の記録）の CI は、この質問を書いた時点で走っている最中です。
- 前の配備されていない Intent は無いため、引き継ぐ一覧はこの Intent の `construction/build-and-test/build-and-test-summary.md` の「次の配備へ引き継ぐこと」だけです。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。依頼者の承認のうえで手で配備する。1つのコンテナを置き換える形 | `team.md`・`project.md` の Deployment |
| WAR | `./gradlew :backend:bootWar --rerun-tasks` で作り直し、SHA-256 と、WAR の中の `WEB-INF/lib` の networknt 3.0.9・Jackson 3.1.8 を確かめる。Dockerfile は手元の WAR を写すだけのため | `project.md` の学び（WAR の確かめ） |
| 戻し先 | 前の配備のイメージ `0d4b6e8866a9` に、入れ替えの前にタグ `mastersmith:pre-deps-forks-review` を付ける | 前の手順 |
| 戻しの形 | イメージだけ。移行と `.env` の変更が無いため、戻しの練習は行わない | `project.md` の Deployment（2026-11-07 の決まり） |
| 内部DB の複写 | 入れ替えの前に1回（バックアップ）。アプリは止まっているため、止める操作は要らない。配備の後の複写と数えは、スモークテストで監査に残る操作を行ったときだけ | `project.md` の Deployment（2026-11-07 の決まり） |
| 配備の前の検査 | verify・E2E は Build and Test の結果を正とし、流し直さない（`c07dfbb7` の全件の `verify` 通過・E2E 298 件。その後の差は `ci.yml` のコメント・`docs/verification.md`・記録だけ）。CI は台本 A2 で確かめ、先頭の CI の結論は Deployment Execution の始めに確かめる | `construction/build-and-test/`、`team.md` の Way of Working |
| 配備の前の k6 | 流さない（Build and Test の B2: A。Spring Boot・H2・JRE が変わらず FR5.2） | `construction/build-and-test/test-results.md` 5節 |
| 古いイメージの片付け | 配備の完了の後に、固定先から外れたイメージと、新しい戻し先より古い `mastersmith` のタグを、使うコンテナが 0 件であることを確かめ、消す一覧の承認を得てから消す | 要件 FR6.2 |
| 秘密・個人に関する値 | `.env` の値、初期管理者のメールアドレス、業務データの行の値を表示しない。複写はリポジトリの外（`~/.mastersmith-backup/`、置き場 700・ファイル 600） | `project.md` の Forbidden と学び |

## Q1. [リリースの担当] `main` への取り込みの時機

要件 FR6.3 は、`main` への取り込みを、前の取り込みの後のすべてのコミットの CI を確かめてから、依頼者の承認を得て fast-forward で行うとしています。時機（配備の前か後か）は決まっていません。

**推奨: A。** 理由:

- 前の配備と同じ形で、配備した版が `main` の先頭になり、どの版をリリースしたかを `main` で追えます（`team.md` の Deployment）。
- 取り込みの前の確かめ（全件の `verify`・E2E・CI の試行ごと）は Build and Test と台本 A2 で済んでいます。残るのは先頭の `b8d61840` の CI の結論だけです。

A. 配備の前（Deployment Execution の始め）に、先頭の CI の結論を A2 で確かめてから、承認を得て `develop` を `main` へ fast-forward する。配備するのは `main` の先頭。プッシュは依頼者
B. 配備とスモークテストが通った後に、承認を得て `main` へ fast-forward する。配備するのは `develop` の先頭
X. Other (please specify)

[Answer]: A

## Q2. [リリースの担当] スモークテストの範囲

変わるのはサーバーの JSON の部品（Jackson のパッチ）と DSL の検証の部品（networknt）、画面の依存（i18next の大きな版を含む）です。どれも E2E 298 件で確かめ済みですが、配備した版の画面を実際のブラウザで読むのは配備の後が初めてです。

**推奨: A。** 理由:

- i18next 27 で文言の出し方が変わっていないかを、日本語と英語の両方で見られます。
- 包み直した日時のヒントと誤りの文言（業務データの検索の欄）を、実際の画面で1回見られます。
- 監査に残るのはログイン（`LOGIN_SUCCEEDED`）とログアウトだけで、保存・作成・DSL の投入はしません。ただし監査に残る操作をするため、配備の後の複写と数えが要ります（Q3）。

A. AI が未認証の確かめ（healthy・`/actuator/health`・画面の入口と favicon の 200・API の 401）とアプリのログを行い、依頼者が Chrome で初期管理者でログインし、ホーム・DSL の状態・業務データの一覧と検索の欄（日時の列のヒントと、日時の誤りの文言）・役割の一覧・利用者の管理を見て、表示の言語を英語に切り替えて同じ画面を見る（保存しない）。配備の後に内部DB を複写して監査の件数を数える
B. AI の未認証の確かめとアプリのログだけにする（ログインしない）。配備の後の複写と数えは行わない。画面は E2E で確かめ済みとし、差を `cd-config.md` に書く
X. Other (please specify)

[Answer]: A

## Q3. [リリースの担当] 内部DB の複写の数え（台本 A3 が `.env` を読むこと）

台本 A3（`scripts/count-db-copy.mjs`）は、初期管理者の状態を数えるために `.env` から初期管理者のメールアドレスを読みます（出力は「`.env` にあるか」の 0・1 だけで、値は出しません）。`docs/internal-db.md` で、AI が流す前に依頼者の許しを得ることにしています。

**推奨: A。** 理由:

- 配備の前の複写の数えが、救済の見込み（初期管理者の停止・管理の印）と監査の比べの基準になります。
- 2回分をここでまとめて許しておけば、配備の途中で止まらずに済みます（前の配備と同じ）。

A. 配備の前の複写と配備の後の複写（Q2 で A のとき）の2回、A3 を `--set basic,audit` で流すことを許す。ほかの回は流す前に改めて尋ねる
B. 配備の前の複写だけ A3 で数える。配備の後（Q2 で A のとき）は、流す前に改めて尋ねる
C. A3 を流さない（`.env` を読ませない）。複写は取るが数えない。救済の見込みは依頼者の答えだけで決める
X. Other (please specify)

[Answer]: A

## Sources

- `git diff --stat a9ddc077 HEAD`・`git diff a9ddc077 HEAD -- frontend/src backend/gradle.lockfile`、`git log a9ddc077..HEAD -- frontend/src`
- `docker ps -a`・`docker images`・`docker image inspect`・`docker system df`（読み取り）
- `node scripts/ci-attempts.mjs`（前の取り込みの後の CI）
- 前の手順: `aidlc/spaces/default/intents/261108-ci-checks-release/operation/deployment-pipeline/`、同じ Intent の `operation/deployment-execution/deployment-log.md`
- 引き継ぎの一覧: この Intent の `construction/build-and-test/build-and-test-summary.md`
- 要件: この Intent の `inception/requirements-analysis/requirements.md`（FR6）
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`docs/internal-db.md`（A3）・`docs/operations.md`（A1）・`docs/deployment.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）・`project.md`（Deployment）・`aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`
