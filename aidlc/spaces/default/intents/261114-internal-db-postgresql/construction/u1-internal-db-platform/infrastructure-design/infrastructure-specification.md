# インフラの仕様 — u1-internal-db-platform

この単位は、実行環境（compose・イメージ・配備）を変えません。compose の PostgreSQL の選び方と配備の文書は U4 の持ち分です。この単位が持つインフラは、PostgreSQL の結合テストが使う使い捨てのコンテナと、それを流す CI の入口だけです。クラウドの基盤・IaC・検証環境は、配備先が決まるまで作りません（`project.md` の Deployment）。

## Deployment

| 項目 | 決めたこと | 理由 |
|---|---|---|
| 計算の形 | 変更なし。アプリは今のまま実行可能 WAR（コンテナの中で起動）。この単位でイメージ・Dockerfile を変えない | 実行時の変更が無い（接続のたびの文と、設定の読み方だけ） |
| 手元の PostgreSQL（テスト用） | Testcontainers が起動する使い捨てのコンテナ。イメージは `TargetDbImages.postgres()`（版 18.6 のタグとダイジェスト。内部DB 用に別の定数を持たない） | NFR3.2。対象DB と同じ版にそろえ、上げ漏れを防ぐ |
| ネットワーク | テストの JVM とコンテナの間の接続だけ（Testcontainers がポートを動的に割り当てる）。PC にポートを固定で開けない | 配備先が開発者の PC だけ。固定のポートは衝突の元 |
| 保存 | コンテナの中の使い捨て。テストの終了で消える。テストごとの専用データベースは、終わったら `DROP DATABASE` で消す | NFR4.9 |
| 環境 | 手元と CI だけ（検証環境・本番は無い）。手元はコンテナの実行環境（colima）が前提。無いとき警告して PostgreSQL のテストだけ飛ばす。CI では飛ばさない | NFR4.8、`team.md` の Way of Working |
| IaC | 無し | 配備先が決まるまで基盤を作らない |
| 資源の大きさ | PostgreSQL のコンテナは既定のまま（メモリ・CPU の上限を付けない）。テストの JVM ごとに 1 つ | BR6.5。上限を付けても確かめる項目が無い |
| 秘密情報 | コンテナの起動ごとの資格情報、またはテスト専用の値。CI は秘密情報を使わない | NFR2.9、ci.yml の方針 |

## Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| PostgreSQL 18.6（テスト用コンテナ） | データベース（内部DB の PostgreSQL の結合テスト） | `TargetDbImages.postgres()`。テストの JVM ごとに 1 コンテナ。専用のデータベースを `CREATE DATABASE` で作る | `max_connections` は既定（100）。内部DB の接続プール 30 本が収まる（NFR7.2） |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | 無し | — | 1 インスタンス・開発者の PC の前提で当たらない |
| compose の PostgreSQL（`profiles`・内部DB 用）と起動確認 | 配備の側 | U4 が足す | この単位では書かない。compose の `postgres:18.6` は対象DB 用と同じ版・ダイジェストにそろえる（U4） |

## Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| PostgreSQL のテスト用コンテナの起動の部品（`internaldb/testsupport` の基盤。`@InternalDbPostgresIT`・コンテナの保持・専用データベース） | u1-internal-db-platform | u2-portable-sql・u3-postgres-behaviour-tests | 基盤の注釈と部品を使うだけで、基盤の内部（コンテナの保持の仕組み）に頼らない。u2・u3 は専用のタスク `:backend:internalDbPostgresTest` に載るテストを足す |
| Gradle の専用のタスクとタグ（`internal-db-postgres`） | u1-internal-db-platform | u2・u3・u4 | タグを付けたクラスだけが専用のタスクに載る |
| CI の手動の入力 `internal_db_postgres` | u1-internal-db-platform | u4（配備の確かめで参照） | 真偽値。既定 false |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/logical-components.md`・`scalability-design.md`・`performance-design.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/tech-stack-decisions.md`（NFR3.2）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md`（components: InternalDbPlatform・InternalDbMigration。どちらも実行時のインフラの部品を持たず、1 つのアプリの中の部品）
- `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`（`postgres()`）
- `compose.yaml`（`targetdb-postgres`）・`.github/dependabot.yml`
- `aidlc/spaces/default/memory/team.md`（Way of Working・Deployment）・`project.md`（Deployment）

## Assumptions & Open Questions

- [assumption] CI のランナー（`ubuntu-latest`）はコンテナの実行環境を持つ（対象DB の結合テストが既に使っている）。
