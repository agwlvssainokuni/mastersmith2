# 戻しの手順書（rollback-runbook）

Intent 261110-deps-forks-review の配備を戻す手順です。`docs/deployment.md` の「戻し方」と、前の手順（`aidlc/spaces/default/intents/261108-ci-checks-release/operation/deployment-pipeline/rollback-runbook.md`）を正とし、差を中心に書きます。

この配備には内部DB の移行と `.env` の変更が無いため、戻しは**イメージだけ**です。戻しの練習は行いません（`project.md` の Deployment の 2026-11-07 の決まり。`cd-config.md` の 5節）。

## 1. 戻すきっかけ

- 起動が healthy にならない、Flyway の誤りや予定に無い移行が出る、ERROR が出続ける（適用中の DSL の JSON Schema の検証の誤りを含む）、ログに秘密・メールアドレスそのもの・業務データの値が出る、スモークテスト（`deployment-strategy.md` の S1〜S5）が通らない。
- 画面に文言の鍵がそのまま出る、英語に切り替わらない、業務データの検索の欄の日時のヒント・誤りの文言が出ない・崩れる、API の応答の形が変わって画面が動かない。
- 戻すかどうかは依頼者に諮って決める。
- アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻さない（`docker compose start app` で起動し直し、原因をログと終了コードで確かめる）。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-deps-forks-review`（イメージ ID `0d4b6e8866a9…`、Intent 261108-ci-checks-release で配備した `a9ddc077` の版）。
- 内部DB はそのまま使う。スキーマは前の版と同じ（版 14）で、戻しで変わらない。
- `.env`・`.env.targetdb`・見本の PG・Mailpit は戻さない（この配備で変えていない）。
- `main` は戻さない（戻しはイメージで行い、ソースの履歴は動かさない）。戻した版と `main` の先頭が食い違うことを Deployment Execution の記録に書く。

### 2.1 手順

```bash
docker image inspect mastersmith:pre-deps-forks-review --format '{{.Id}}'   # sha256:0d4b6e8866a9… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（docs/internal-db.md の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz、置き場は ~/.mastersmith-backup/、複写は権限 600）
MASTERSMITH_IMAGE_TAG=pre-deps-forks-review docker compose up -d --no-build --no-deps app
MASTERSMITH_IMAGE_TAG=pre-deps-forks-review docker compose ps app   # healthy を待つ
docker inspect mastersmith-app-1 --format '{{.Image}}'   # sha256:0d4b6e8866a9… であること。違えば --force-recreate を足して作り直す
unset MASTERSMITH_IMAGE_TAG; env | grep -c MASTERSMITH_IMAGE_TAG   # 0 であること
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-deps-forks-review` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- **コマンドの外でこの変数を `export` したままにしないでください。**
- 入れ替えの後は、コンテナのイメージ ID が戻し先の ID と一致することを `docker inspect` で確かめる（構成が同じだと作り直されず古いイメージのまま起動することがある。`project.md` の学び）。
- 戻し先のタグのイメージが無いときは、`docs/deployment.md` の「戻し方」のとおり、前の配備のソース（`a9ddc077`）を `git worktree add` で取り出して WAR とイメージを作り直す。
- 開いているタブを読み込み直す（前の版の画面のファイルを読むため）。
- **片付け（`cd-config.md` の順 13）では、戻し先 `mastersmith:pre-deps-forks-review` を消さない。**

### 2.2 戻したときに変わること

| 変わること | 内容 |
|---|---|
| サーバーの部品 | networknt 3.0.6・Jackson 3.1.7・itu 1.14.0 に戻る。API の応答の形と DSL の検証の結果は同じ（E2E と結合テストで確かめた範囲） |
| 画面のファイル | 前の版の画面の依存（i18next 26 系ほか）に戻る。画面の振る舞いと表示は同じ |
| 表示の設定（言語など） | ブラウザに保存した設定はそのまま使われる（形は変えていない） |
| 監査・業務データの行・内部DB | 戻しでは変わらない。戻す前に残った行はそのまま残る（監査は追記だけ） |
| 検査・CI・コンテナの版（Mailpit・lgtm・collector・対象DB） | 配備に入らないため、戻しで変わらない |

### 2.3 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy、`/actuator/health` が 200（bash の `/dev/tcp` で待つ）、コンテナのイメージ ID が `0d4b6e8866a9…`。ERROR 0 件。Flyway の `Migrating schema` 0 件で、検証の誤りが無い。救済の WARN 0 件 |
| R2 | 未認証の要求 | AI | `deployment-strategy.md` の S2 の (1)・(2) と同じ。配る画面のファイルの名前が前の版のもの |
| R3 | ★ログインと画面 | 依頼者 | 初期管理者でログインし、ホームと業務データの一覧が開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に伝える） |

### 2.4 新しい版へ上げ直すとき

- 直した WAR で `docker compose up -d --build --no-deps app` を行い（`MASTERSMITH_IMAGE_TAG` を付けない）、イメージ ID の一致を確かめる。新しく当たる移行は無い。

## 3. 配備の前の複写を戻すとき（第二の手）

- 内部DB が壊れた・読めないときだけ使う。戻すと、複写の後に増えた行（監査・利用者の操作）を失う。手順は `docs/internal-db.md` の「内部DBのバックアップと戻し方」のとおり。複写は `cd-config.md` の順 6 の `…-pre-deps-forks-review.tgz`。
- 戻す前に、今の内部DB を `…-before-restore.tgz` として複写する（調べるため）。戻すかどうかは依頼者が決める。

## 4. 片付けの後に戻すとき

- 片付け（`cd-config.md` の順 13・7節）で消すのは `mastersmith:pre-ci-checks-release`（Intent 261104 で配備した版）と、固定先から外れた外部のイメージだけで、1つ前の版（戻し先）は残る。2つ前の版へ戻すときは、`docs/deployment.md` の「戻し方」のとおり、その版のソースを `git worktree add` で取り出して WAR とイメージを作り直す。
- 消した外部のイメージ（Mailpit v1.33.0・lgtm 0.39.0・collector 0.164.0・postgres と mysql の前のダイジェスト）は、配備したアプリの戻しには使わない。前の版の `compose.yaml` で動かすときは、ダイジェストで取り直す。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261108-ci-checks-release/operation/deployment-pipeline/rollback-runbook.md`、同じ Intent の `operation/deployment-execution/deployment-log.md`（イメージ `0d4b6e8866a9`）
- 流れ: `cd-config.md`、確かめ: `deployment-strategy.md`、質問と答え: `deployment-pipeline-questions.md`
- リポジトリ: `docs/deployment.md`（「戻し方」）、`docs/internal-db.md`（「内部DBのバックアップと戻し方」）、`compose.yaml`（`MASTERSMITH_IMAGE_TAG`）、`git diff a9ddc077 HEAD -- frontend/src backend/gradle.lockfile`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment の戻し方）、`project.md`（Deployment の 2026-11-07 の決まりと学び）、`aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`

## Assumptions & Open Questions

- [assumption] 戻し先のイメージ `0d4b6e8866a9` は、261108-ci-checks-release の配備の後に作り直されていない（`docker images` の作成が 2 日前で、その配備の時刻と合う）。順 4 でタグを付けるときに ID を確かめる。
