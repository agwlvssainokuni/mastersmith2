# セキュリティの要件 — u4-local-runtime-docs

上流の要件 NFR2（セキュリティ）を、この単位の `NFR2.x` に分けます。この単位は、手元の compose に内部DB 用の PostgreSQL を足し、文書を書くだけです（アプリのコードは変えない）。攻撃の面は、compose が開くポート・資格情報の置き場・文書に書く値の 3 つです。認証・認可・暗号化・入力の検証は変えません。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR2.1 | 内部DB 用の PostgreSQL の資格情報（利用者・パスワード・データベース名）は、`compose.yaml` にも文書にもリポジトリのどのファイルにも値を書かず、Git 管理外の `.env` から渡す。`.env.example` には項目だけを、値を空にして足す（コメントで説明する）。パスワードの既定値を置かない。 | `compose.yaml`・`.env.example`・README・`docs/` の差分を読み、資格情報の値が無いこと。Gitleaks が新しい指摘を出さない（`.gitleaks.toml` の除外を足さない） | 要件 NFR2、`project.md` の Forbidden（内部DB の接続情報は環境変数 `.env` 以外から受け取らない）・Mandated（秘密情報を設定ファイルに直接書かない） |
| NFR2.2 | 内部DB 用の PostgreSQL のポートを PC に開けない（`ports` を置かない）。アプリのコンテナからは compose の中のサービス名で接続する。見本の対象DB（`targetdb-postgres`）と同じ形。 | `compose.yaml` に内部DB 用のサービスの `ports` が無いこと。`docker compose config` の結果で確かめる | 要件 NFR2、`compose.yaml` の見本の対象DB の方針 |
| NFR2.3 | 内部DB 用の PostgreSQL は、見本の対象DB とは**別のサービス・別のボリューム・別の資格情報**にする（資格情報の環境変数の名前も別）。見本の対象DB の `.env.targetdb` の値を使い回さない。 | `compose.yaml` のサービス名・ボリューム名・環境変数の名前が重ならないこと | 要件 FR4.1 |
| NFR2.4 | 文書（README・`docs/internal-db.md`・`docs/operations.md`・`docs/configuration.md`）に書く接続先の例は、資格情報を含まない形（`jdbc:postgresql://<ホスト>:5432/<データベース名>`）と、環境変数の名前だけにする。ログに接続先の URL が出ること（起動時の Flyway の INFO は接続先の URL を含み、資格情報は含まない）を、既知の制約として文書に書く。バックアップと戻しの手順（`pg_dump`・`pg_restore`）の例も、パスワードを引数に書かず、環境変数またはコンテナの中の設定から渡す形にする。 | 文書の差分を読む。例のコマンドに資格情報の値・`PGPASSWORD=<値>` の直書きが無いこと | `project.md` の Forbidden（接続情報をログ・エラー応答に含めない。環境変数以外から受け取らない）、U3 のレビュー（Flyway の INFO の URL の持ち越し） |
| NFR2.5 | バックアップの複写（`pg_dump` の出力）は、リポジトリの外（ホームの下の `~/.mastersmith-backup/`、権限 700）に置き、コミットしない（個人に関する値・パスワードのハッシュを含むため）。文書の手順に、その置き場と権限を書く。 | 文書の手順を読む。`.gitignore` に新しい除外を足さない（置き場がリポジトリの外のため） | `team.md` の Deployment（内部DB の複写はリポジトリの外に置き、コミットしない）、`project.md` の Forbidden |
| NFR2.6 | 内部DB 用の PostgreSQL のコンテナの資源（メモリ）の上限を置き、colima の VM（CPU 4・メモリ 6GiB）の中で、アプリ・手元の監視・見本の対象DB と同時に動かせる大きさにする。 | `compose.yaml` に `mem_limit` があること | 要件 NFR7（インスタンス）、`compose.yaml` の他のサービスの方針 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR2・FR4.1）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`（U4 の節）
- `aidlc/spaces/default/memory/project.md`（Forbidden・Mandated）・`team.md`（Deployment）
- `compose.yaml`・`.env.example`・`docs/internal-db.md`（読み取り）

## Assumptions & Open Questions

- [assumption] 内部DB 用の PostgreSQL のメモリの上限は、見本の対象DB（512MB）と同じ 512m で足りる。内部DB の件数は少なく、負荷の試験の記録で確かめる（Deployment Execution）。
