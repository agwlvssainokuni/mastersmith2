# 戻しの手順（rollback-runbook）

## 1. 戻し先と前提

- 戻し先は、配備の前にタグを付けたイメージ `mastersmith:pre-storage-memory`（`sha256:a9cfa9dc…`、前の Intent の版）。
- 内部DB のスキーマは変わらないため、データはそのまま使える（バックアップからの戻しは要らない）。
- `compose.yaml`・`.env`・`.env.targetdb` は今回変わらないため、戻すのはイメージだけ。
- 前の版は HikariCP の JMX を有効にしていないため、戻した後は詰め直しの道具が使えない（`status` が「プールの MBean がありません」で終わる）。詰め直しはアプリの起動し直し（止めるときに `DEFRAG_ALWAYS=TRUE` で詰め直す）で行う。最大ヒープも 75%（前の版の `Dockerfile` の既定）に戻る。

## 2. アプリを前の版に戻す

| 順 | 手順 | コマンド | 通る条件 |
|---|---|---|---|
| 1 | 戻し先のイメージがあることを確かめる | `docker image inspect mastersmith:pre-storage-memory --format '{{.Id}}'` | `sha256:a9cfa9dc…` |
| 2 | 戻し先を `local` のタグに付け直す | `docker tag mastersmith:pre-storage-memory mastersmith:local` | — |
| 3 | アプリだけを作り直す（イメージは作り直さない） | `docker compose up -d --no-build app` | エラーなく終わる |
| 4 | 健全になるまで待つ | `docker compose ps app` | `healthy` |
| 5 | スモークテスト | `deployment-strategy.md` 3節（詰め直しの後の項目を除く） | 通る |
| 6 | 記録 | 戻した時刻・理由・動いている版（`pre-storage-memory`）を Deployment Execution の記録に残す。`develop` のコミットは戻さない（直してから配備し直す） | — |

## 3. 詰め直しの道具が途中で終わったとき（配備した版のまま）

- `./docker/hikari-pool.sh resume` で再開する。`status` で「借りる待ち」が 0 に戻り、`healthy` を確かめる。
- だめなら `docker compose restart app`（止めるときに H2 が閉じて詰め直され、起動し直した後に使える）。
- それでも内部DB を開けないときは、README の「内部DBのバックアップと戻し方」で直前のバックアップを展開する（バックアップの後の記録は失われる。今回の配備の前にはバックアップを取らないため、最後にバックアップを取った時点に戻る）。

## 4. 戻しの確かめ

- 前の Intent と同じ形で、Deployment Execution の中で、前の版のイメージ（`mastersmith:pre-storage-memory`）を一時のボリュームで起動して健全になることを確かめる（配備したアプリと内部DB には触れない）。
- 配備した内部DB のデータでの起動は確かめない（スキーマが変わらないため）。未確認のまま残る前提として Deployment Execution の記録に書く（project.md の Corrections）。

## Sources

- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1・まとめの確認）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（D9 の統合の方法）
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/build-and-test/test-results.md`（配備と同じソースのイメージでの負荷の試験）
- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/`（前の Intent の配備の手順。正とする）
- `README.md`（コンテナでの起動と確認、内部DBのファイルの詰め直し）
