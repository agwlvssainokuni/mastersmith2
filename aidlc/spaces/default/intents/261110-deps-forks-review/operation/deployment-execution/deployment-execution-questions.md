# Deployment Execution の質問（Intent 261110-deps-forks-review）

`operation/deployment-pipeline/cd-config.md` の流れの順 1〜4 のうち、読み取りの確かめ・WAR の作り直し・戻し先のタグを終えました。`main` への取り込み（順 1）の承認、順 5 の答え、アプリを起動する順 6〜8 の承認をいただくための質問です。

## 配備の前の確かめの結果（順 1〜4）

| 順 | 確かめ | 結果 |
|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更 0 件（`aidlc/` の下を除く）。台本 A2（`node scripts/ci-attempts.mjs`）で前の取り込み `a9ddc077` の後の 27 コミットを確かめ、最後の試行が通っていない実行 0 件（`b4c8d77e` の実行 43038873152 は1回目 cancelled・2回目 success）。`b8d61840` の CI（実行 43255567312）は試行 1 で success（検査の job 26分11秒）。先頭の `82825a67`（Deployment Pipeline の承認の記録だけ）の CI（実行 43265830641）は、この質問を書いた時点で走っている最中 |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks`（17:07:23〜17:08:06 UTC、43 秒、`caffeinate -dims`、`82825a67`）。SHA-256 `53abadc0d733a70754605fbc9da45a1579668f06736671be541f00df1188f3f4`。`WEB-INF/lib` は `json-schema-validator-3.0.9`・`jackson-core-3.1.8`・`jackson-databind-3.1.8`・`itu-1.17.0` だけで、前の版は無い。`static/` の画面のファイルは `frontend/dist` と同じ（WAR だけにあるのはバックエンドの資源 `dsl/dsl-schema-v2.json`）。`MASTERSMITH_IMAGE_TAG` 0 件 |
| 3 | 台本 A1 | Mailpit（v1.33.1）と見本の PG（18.6）を起動し、版とダイジェストが `compose.yaml` と一致（食い違い 0 件）。WAR の SHA-256 が順 2 と一致。`.env` の名前 18 個（値は読まない）、更新の時刻は 2026-11-02（前の配備より前で、変わっていない） |
| 4 | 戻し先のタグ | `mastersmith:pre-deps-forks-review` を付け、ID が `0d4b6e8866a9` で始まることを確かめた |

## Q1. [リリースの担当] `main` への取り込み（順 1、Deployment Pipeline の Q1: A）

先頭の `82825a67` の CI が通ったことを確かめてから、`git switch main && git merge --ff-only develop` で `main` を `develop` の先頭まで進め、`develop` に戻ります。`main` のプッシュは依頼者が行います。CI が通らなかったときは取り込まずに止め、改めて諮ります。

**推奨: A。** 理由: 取り込みの前の確かめ（全件の `verify`・E2E・前の取り込みの後の CI の試行ごと）は済んでおり、先頭の差は記録だけです。

A. 先頭の CI が通ったら取り込む
B. まだ取り込まない（理由を書く）
X. Other (please specify)

[Answer]: A

## Q2. [リリースの担当] 初期管理者について、前の配備の後に変えたこと

前の配備（2026-11-10、Intent 261108-ci-checks-release）の後に、初期管理者のパスワードを画面で変えた、利用を止めた、管理者の印を外した、のどれかを行いましたか。行っていると、起動のときの救済（初期管理者を元に戻す仕組み）が働き、監査に `INITIAL_ADMIN_RESCUED` が残る見込みになります。順 6 の複写の数え（台本 A3）で裏付けます。

A. どれも行っていない（救済は働かない見込み）
B. 行った（どれを行ったかを書く）
C. 覚えていない（順 6 の数えの結果で決める）
X. Other (please specify)

[Answer]: A

## Q3. [リリースの担当] アプリの起動（順 6〜8）の承認

`main` の取り込みの後、順 6 で内部DB を複写して台本 A3 で数え（`.env` を読む1回目。Deployment Pipeline の Q3: A で許しを得た）、順 7 でアプリのコンテナを作り直して起動し、順 8 で健全性を確かめます。アプリは今止まっているため、止まるものはありません。起動すると内部DB を使い始めます。

**推奨: A。** 理由: 配備の前の確かめがすべて通り、戻し先のタグも付けました。

A. 順 6〜8 を進める
B. まだ進めない（理由・待つことを書く）
X. Other (please specify)

[Answer]: A

## Sources

- 流れ: `operation/deployment-pipeline/cd-config.md`（3節・4節）、`deployment-strategy.md`、`rollback-runbook.md`
- 確かめの結果: `git status`、`node scripts/ci-attempts.mjs`、`gh run view 43255567312`・`43265830641`、`./gradlew :backend:bootWar --rerun-tasks`、`unzip -Z1`・`shasum -a 256`、`node scripts/ops-readonly-check.mjs --war backend/build/libs/mastersmith.war`、`docker tag`・`docker image inspect`
- 前の配備: `aidlc/spaces/default/intents/261108-ci-checks-release/operation/deployment-execution/deployment-log.md`
