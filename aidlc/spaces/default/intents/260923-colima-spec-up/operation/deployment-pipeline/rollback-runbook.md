# 戻し方の手順書（rollback-runbook）

今回の配備（コミット `e4b10af` 以降の版）で問題が出たときの戻し方。依頼者の決定（Q3: A）により、**まず `.env` を元に戻し**、それで直らなければ**直前の版 `3287050` に戻す**。戻すかどうかは依頼者が決める。AI-DLC の作業の中で AI が戻す場合は、依頼者の承認を得てから行う。

## 1. 戻すと決める基準

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md` 1節と同じ（healthy にならない、起動の失敗、スモークテストの失敗、配備の後の不具合）。今回の変更に固有のきっかけは次の2つ。

- コンテナの上限（メモリ 2g・CPU 4）に原因があると見られる不具合
- `ENTRYPOINT` の形（`sh -c "set -f; exec java ..."`）に原因があると見られる起動・停止の不具合

## 2. `.env` を元に戻す（第一の手）

`.env` の2行だけを配備の前の状態に戻す。イメージは作り直さない。

| 順 | 手順 | コマンドの例 |
|---|---|---|
| 1 | 配備の前に取った `.env` の複写を戻す（中身は表示しない） | `cp -p ~/.mastersmith-env-backup/env-<日時> .env` |
| 2 | アプリを作り直す（`.env` を読み直すため、`restart` ではなく `up -d`） | `docker compose up -d app` |
| 3 | 健全になるまで待つ | `docker compose ps` で `healthy` |
| 4 | 上限を確かめる | `docker inspect mastersmith-app-1 --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'`（1073741824 と、元の CPU の値） |
| 5 | スモークテストを行う | `deployment-strategy.md` 2節 |
| 6 | 記録する | 動いている版のハッシュは変わらず、設定だけが違うことを記録する |

- **この状態では F3（高い負荷でメモリの上限で止まる）と F4（ログインが 1 秒を超える）が元に戻る。** 原因を直したら、`deployment-strategy.md` の手順 6〜10 で `.env` を変え直す。
- 起動の形（`ENTRYPOINT`）はこの手では戻らない。起動・停止の不具合は次の節で戻す。

## 3. 直前の版に戻す（`.env` を戻しても直らないとき）

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md` 2節の手順で、直前の版 **`3287050`** に戻す。手順は、直前の版のコミットを `git worktree add` で取り出し、`./gradlew :backend:bootWar` で WAR を作り、`docker compose up -d --build` で起動するものである。

- 直前の版の `compose.yaml` は `MASTERSMITH_CONTAINER_MEMORY` を読まない（`mem_limit: 1g` の固定値）。`.env` に残した行は使われない。新しい版に戻すときのために、`.env` の行は残しておいてよい。
- 直前の版の `compose.yaml` は `MASTERSMITH_CONTAINER_CPUS` を読む。`.env` の値（4）はそのまま効き、VM の CPU は 4 なので起動できる。
- 前の Intent の手順は、取り出した場所で作った WAR を今のリポジトリに複写し、今のリポジトリの `Dockerfile`・`compose.yaml` でイメージを作る。これでは起動の形（`ENTRYPOINT`）は新しいままになる。起動の形も戻すときは、次のように取り出した場所で起動する。
  - 取り出した場所に、今のリポジトリの `.env` へのシンボリックリンクを作る（`ln -s <リポジトリ>/.env ../mastersmith-rollback/.env`。複写はしない。`.env` は Git 管理外のため取り出した場所には無い）。
  - 取り出した場所で `docker compose up -d --build` を行う。`compose.yaml` の `name: mastersmith` により、同じプロジェクト名・同じボリュームを使う。
  - 戻しが終わって取り出した場所を消す前に、シンボリックリンクを消す。
- スキーマは今回変わっていないため、戻すときのスキーマの扱いは要らない。

## 4. colima の VM を戻す場合（通常は行わない）

VM を元の大きさ（CPU 2・メモリ 2GiB）に戻すときは、次の順で行う。

1. `.env` の `MASTERSMITH_CONTAINER_CPUS` を 2 以下にし、`MASTERSMITH_CONTAINER_MEMORY` を 1g 以下にする（2節の複写を戻せばよい）。VM の CPU より大きい `cpus` は受け付けられず起動できない。
2. `colima stop` → `colima start --cpu 2 --memory 2`。
3. `docker compose up -d app` で起動し、healthy とスモークテストを確かめる。

VM を止めると動いているコンテナも止まる。行う前に内部DBのボリュームを複写する。

## 5. データも戻す・してはいけないこと

前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md` 4・5節のまま。ボリュームを消す操作（`docker compose down -v` など）はしない。

## Sources

- `deployment-pipeline-questions.md`（Q3: A、要約の前提）
- 前の Intent の `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/rollback-runbook.md`
- 前の Intent の Deployment Execution の記録（前回配備した版 `3287050`）

## Assumptions & Open Questions

- 3節の、直前の版を取り出した場所で起動する手順は、実際には動かしていない（戻しの練習をするかは Deployment Execution の段で決める）。
