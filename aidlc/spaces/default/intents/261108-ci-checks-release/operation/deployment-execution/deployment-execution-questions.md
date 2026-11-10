# Deployment Execution の質問（Intent 261108-ci-checks-release）

`operation/deployment-pipeline/cd-config.md` の流れの順 1〜4（読み取りの確かめと戻し先のタグ）を終えました。順 5 の答えと、アプリを起動する順 6〜8 の承認をいただくための質問です。

## 配備の前の確かめの結果（順 1〜4）

| 順 | 確かめ | 結果 |
|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更 0 件（`aidlc/` の下を除く）。`a9ddc077` と `develop` の先頭の間で `aidlc/` の外の差 0。`main` の取り込みの後の最初の `develop` のプッシュの CI（`24ed24e6`、実行 42704002194）は1回目で成功（検査の job 26 分 19 秒、台本 A2） |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks`（14:44:20〜14:45:05 UTC、45 秒）。SHA-256 `3805cffbadebddc59cfe4a53abff0d422d3a8a717558d6f38f51fdc3256bc9f3`。`static/` の画面のファイルは `frontend/dist` と同じ（WAR だけにあるのはバックエンドの資源 `dsl/dsl-schema-v2.json`）。画面の JavaScript に `dsl.status.at` が 0 件（前の版のイメージの WAR は SHA-256 が 261104 の記録と同じ `e2065c43…` で、1 件）。移行のファイルは 14 件（前と同じ）。`MASTERSMITH_IMAGE_TAG` 0 件 |
| 3 | 台本 A1 | 見本の PG・Mailpit の版とダイジェストが `compose.yaml` と一致（食い違い 0 件）。アプリのコンテナは無い。WAR の SHA-256 が順 2 と一致。`.env` の更新の時刻は 2026-11-02（前の配備より前で、変わっていない） |
| 4 | 戻し先のタグ | `mastersmith:pre-ci-checks-release` を付け、ID が `4ee5fb45593e` で始まることを確かめた |

## Q1. [リリースの担当] 初期管理者について、前の配備の後に変えたこと

前の配備（2026-11-07、Intent 261104）の後に、初期管理者のパスワードを画面で変えた、利用を止めた、管理者の印を外した、のどれかを行いましたか。行っていると、起動のときの救済（初期管理者を元に戻す仕組み）が働き、監査に `INITIAL_ADMIN_RESCUED` が残る見込みになります。順 6 の複写の数え（台本 A3）で裏付けます。

A. どれも行っていない（救済は働かない見込み）
B. 行った（どれを行ったかを書く）
C. 覚えていない（順 6 の数えの結果で決める）
X. Other (please specify)

[Answer]: A

## Q2. [リリースの担当] アプリの起動（順 6〜8）の承認

順 6 で内部DB を複写して台本 A3 で数え（`.env` を読む1回目。前の配備の設計の Q2: A で許しを得た）、順 7 でアプリのコンテナを新しく作って起動し、順 8 で健全性を確かめます。アプリは今動いていないため、止まるものはありません。起動すると内部DB を使い始めます。

**推奨: A。** 理由: 配備の前の確かめがすべて通り、戻し先のタグも付けました。

A. 順 6〜8 を進める
B. まだ進めない（理由・待つことを書く）
X. Other (please specify)

[Answer]: A

## Q3. [リリースの担当] スモークテストの S3（ログインと、コードを消した画面）の結果

順 6〜8 と S1・S2 は通りました（アプリは 14:54:32 UTC に healthy）。`deployment-strategy.md` の S3 を Chrome でお願いします。ログインすると監査に `LOGIN_SUCCEEDED` が1件残ります（ログアウトすると `LOGGED_OUT` も1件）。見るだけで、保存・作成・削除・生成はしません。

1. 開いているタブがあれば読み込み直し、初期管理者でログインする。
2. (a) DSL の状態の画面、(b) 業務データの一覧（例: `customers`）でページの件数を 20・50・100 に切り替えてページ送り、(c) 役割の一覧と1つの役割の権限の木と割り当て、(d) グループの一覧、(e) 役割の引き継ぎの画面、(f) 利用者の管理の一覧、(g) プリファレンスの表示のタイムゾーンの選択肢（選ぶだけで保存しない）を開く。
3. どの画面も開けて崩れず、文言の鍵がそのまま（`dsl.status.…` のような形で）出ていないこと。

A. すべて通った（ログインの回数・ログアウトの有無・予定に無い操作があれば書く）
B. 通らない画面があった（どの画面で、どう見えたかを書く）
X. Other (please specify)

[Answer]: A（依頼者の答え「A, login 1, logout 1」。ログイン 1 回・ログアウト 1 回、予定に無い操作なし）

## Q4. [リリースの担当] 古いイメージの片付け（順 14）

配備は完了しました（S1〜S5 すべて通過）。Deployment Pipeline の Q3: A のとおり、消す一覧の承認をお願いします。

| 名前 | ID | 理由 | 使うコンテナ |
|---|---|---|---|
| `mastersmith:pre-tz-options-perf` | `ffd2b8380460` | 261101 で配備した版。新しい戻し先 `mastersmith:pre-ci-checks-release`（`4ee5fb45593e`）より古い | 0 件 |

- 片付けの前の `docker system df` のイメージ: 13 個・7.767GB（回収できる 6.389GB）。
- 残すもの: `mastersmith:local`（`0d4b6e8866a9`、今回の配備）と戻し先 `mastersmith:pre-ci-checks-release`。ほかのイメージには触れない。

**推奨: A。** 理由: 戻し先は1つ前の版で足り、さらに前は `main` の履歴から作り直せます（`rollback-runbook.md` の 4節）。

A. この1つを `docker rmi` で消す
B. 消さない
X. Other (please specify)

[Answer]: A

## Sources

- 流れ: `operation/deployment-pipeline/cd-config.md`（3節・4節）、`deployment-strategy.md`、`rollback-runbook.md`
- 確かめの結果: `git status`・`git diff --stat`、`node scripts/ci-attempts.mjs --from a9ddc077`、`gh run view 42704002194`、`./gradlew :backend:bootWar --rerun-tasks`、`unzip -Z1`・`shasum -a 256`、`node scripts/ops-readonly-check.mjs`、`docker tag`・`docker image inspect`
- 前の配備: `aidlc/spaces/default/intents/261104-tz-options-perf/operation/deployment-execution/deployment-log.md`
