# 環境の確かめの記録（validation-report）

依頼者の決定（Q2: A）により、見本の対象DB（`targetdb-postgres`）だけを起動し、設計の値を1つずつ確かめた（`aidlc/spaces/default/memory/project.md` の Deployment）。配備したアプリ（`mastersmith-app-1`）とその内部DB には触っていない。確かめの操作は見本の DB の中だけで、アプリの監査ログには残らない。

## 1. 結果

| # | 確かめたこと | 期待 | 実測 | 判定 |
|---|---|---|---|---|
| 1 | `.env` の9項目 | すべてある。`MASTERSMITH_TARGET_DB_PASSWORD` は読み取りのパスワードと同じ値 | 9項目ある。2つのパスワードは、1回目は一致せず（長さ 48 と 49）、依頼者が直した後に一致（値は出さずハッシュで比べた） | 合格 |
| 2 | 見本の DB の起動 | `docker compose --profile targetdb-postgres up -d targetdb-postgres` で起動し、アプリは作り直さない | ボリュームを作って起動。`mastersmith-app-1` は Up 32 時間のまま | 合格 |
| 3 | 初期化 | 見本のスキーマと読み取りのアカウントが作られ、ログに誤りが無い | `CREATE SCHEMA`・`CREATE TABLE`×4・`CREATE VIEW`・`CREATE ROLE`・`GRANT`×2、`init process complete`、`ready to accept connections`。ログの error・fatal は 0 件 | 合格 |
| 4 | メモリの上限 | 512MiB | `HostConfig.Memory=536870912`、使用 31MiB | 合格 |
| 5 | ネットワーク越しの認証 | アプリと同じくネットワーク越しの接続はパスワードで確かめる | `pg_hba.conf` の非ループバックは `scram-sha-256`。正しいパスワードで `mastersmith_reader` として接続でき、誤ったパスワードは `password authentication failed` で拒否 | 合格 |
| 6 | 読み取り | `sales` のテーブルとビューをすべて読める | テーブル4つ・ビュー1つ（`BASE TABLE 4`・`VIEW 1`）。記号入りの名前 `Product Master; "Quoted" 'Name'` を含め、全部の件数を読めた（0・1・2・1・1 件） | 合格 |
| 7 | 書き込みの拒否 | 表を作れない・行を入れられない | `create table sales.…` は `permission denied for schema sales`、`public` にも `permission denied for schema public`、行の追加は `permission denied for table` | 合格 |
| 8 | VM のメモリ | アプリ（2g）と見本の DB（512m）が収まる | VM の available 5,004MiB。アプリの使用 432MiB・見本の DB 31MiB | 合格 |

## 2. 確かめの途中で分かったこと

- 見本の DB のコンテナの中からループバック（127.0.0.1）でつなぐと、公式イメージの既定で認証しない（`trust`）。最初の確かめはこの経路で行ったため、誤ったパスワードでもつながり、認証の確かめになっていなかった。アプリと同じネットワーク越しの経路（コンテナの IP）でやり直し、6・7・5 を確かめ直した。見本の DB のポートは PC に開けていないため、ループバックの `trust` はコンテナの中からしか使えない。
- 見本のスキーマには、記号入りの名前のテーブルがわざと入っている（識別子の扱いを確かめるため）。確かめの台本は `format('%I')` で名前を組み立てて読んだ。

## 3. 次の段へ渡すこと

- 見本の対象DB は起動したまま次の段（Deployment Execution）に渡す。配備では `--profile targetdb-postgres` を付けてアプリと一緒に動かす（`README.md` の「コンテナでの起動と確認」）。
- 配備の前に、内部DB のボリュームの複写（`~/.mastersmith-backup/`）と、今のイメージへの戻し用のタグ `mastersmith:pre-dsl` を行う（`deployment-strategy.md`）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/environment-provisioning/environment-provisioning-questions.md`（確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/environment-provisioning/environment-inventory.md`
- `compose.yaml`（`targetdb-postgres`）、`docker/targetdb/postgres/01-sample-schema.sql`・`02-reader-account.sh`
- 実行したコマンド: `docker compose --profile targetdb-postgres up -d targetdb-postgres`、`docker compose logs targetdb-postgres`、`docker compose exec -T targetdb-postgres sh -c '… psql -h <コンテナの IP> -U mastersmith_reader -d business …'`（パスワードはコンテナの環境変数から渡し、表示していない）、`docker inspect`・`docker stats --no-stream`・`colima ssh -- free -m`

## Assumptions & Open Questions

None.
