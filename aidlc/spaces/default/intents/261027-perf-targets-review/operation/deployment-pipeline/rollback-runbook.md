# 戻しの手順書（rollback-runbook）

Intent 261027-perf-targets-review の配備を戻す手順です。README の「戻し方」と、前の手順（`aidlc/spaces/default/intents/261025-deps-perf-carryover/operation/deployment-pipeline/rollback-runbook.md`）を正とし、差を中心に書きます。今回はスキーマも `.env` も変わらず、**アプリの中身も変わらない**ため、戻しは**イメージだけ**で、戻しの練習と配備の前の内部DB の複写は行いません（`cd-config.md` の 4節・5節）。

## 1. 戻すきっかけ

- 起動が healthy にならない、ERROR が出続ける、ログに秘密・メールアドレスそのもの・業務データの値が出る、スモークテスト（`deployment-strategy.md` の S1〜S6）が通らない。
- 戻すかどうかは依頼者に諮って決める。
- アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻さない（4節）。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-perf-targets-review`（イメージ ID `3075065a2653…`、WAR の SHA-256 は `ff50498b…`）。今動いている版と同じ中身で、直前の版へ戻すのと同じ形で手順を使える。
- 内部DB はそのまま使い、スキーマは戻さない（V13 のまま）。
- `.env`・`.env.targetdb`・見本の PG は変えていないため、戻すことに伴う作業は無い。

### 2.1 手順

```bash
docker image inspect mastersmith:pre-perf-targets-review --format '{{.Id}}'   # sha256:3075065a2653… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（README の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz、置き場は ~/.mastersmith-backup/、複写は権限 600）
MASTERSMITH_IMAGE_TAG=pre-perf-targets-review docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-perf-targets-review docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-perf-targets-review` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- **コマンドの外でこの変数を `export` したままにしないでください。** Build and Test の測り直しで、試験用のタグを `export` したまま `docker compose up -d app` を行い、配備したアプリが意図しないタグで作り直された。戻した後や試験の後は `unset MASTERSMITH_IMAGE_TAG` で消し、`env | grep -c MASTERSMITH_IMAGE_TAG` が 0 であることを確かめる。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、`main`（`94538ef`）を `git worktree add` で取り出して WAR とイメージを作り直す。
- 開いているタブを読み込み直す。

### 2.2 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy、`/actuator/health` が 200。ERROR 0 件。Flyway は `Migrating schema` 0 件で、WARN は前の版から出ているもののほかに無い |
| R2 | ★ログインと画面 | 依頼者 | 初期管理者でログインし、ホームと業務データの一覧が開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に伝える） |

### 2.3 新しい版へ上げ直すとき

- 移行は当たらない。直した WAR で `docker compose --profile targetdb-postgres up -d --build app` を行う（`MASTERSMITH_IMAGE_TAG` を付けない）。

## 3. 配備の前の複写を戻すとき（第二の手）

今回は配備の前の複写を取らないため、この手は**使えない**。H2 の版も移行も変わらないため、内部DB が壊れる見込みは低い。入れ替えの起動で内部DB が壊れたと分かったときは、戻す前に依頼者に諮る。手元にある複写は、前の Intent の `~/.mastersmith-backup/` のもの（`…-after-deps-perf-carryover.tgz` など）と、配備の後に取る `…-after-perf-targets-review.tgz` のみで、戻すとそれ以降のデータと監査の行を失う。

## 4. アプリが止まったとき（`ExitOnOutOfMemoryError`）

- 前の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md` 7節）のとおり。ヒープが尽きると JVM が止まり（終了コード 3・OOMKilled false）、`restart: "no"` のため起き直さない。`docker compose --profile targetdb-postgres start app` で約 10 秒で戻る。
- 対象DB（見本の PG）が止まったとき（`OOMKilled` true など）は、アプリは動き続け、業務データの API が 503 を返す。`docker compose --profile targetdb-postgres start targetdb-postgres` で起動し直す。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261025-deps-perf-carryover/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md`
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- リポジトリ: README（「戻し方」「内部DBのバックアップと戻し方」）、`compose.yaml`（`MASTERSMITH_IMAGE_TAG`・`restart: "no"`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 戻し先のイメージ `3075065a2653` は、今の内部DB（V13）でそのまま起動する。今の本物のデータで動いているイメージそのもので、測り直しのあとの `docker compose up -d app` でも起動を確かめている。戻しの練習は行わない。
