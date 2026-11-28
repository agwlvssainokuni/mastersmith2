# インフラの仕様 — u3-postgres-behaviour-tests

この単位はテストだけで、実行環境（compose・イメージ・配備）を変えません。新しいサーバー・コンテナ・ネットワーク・保存先・秘密情報を足しません。PostgreSQL のテスト用コンテナ（`InternalDbPostgresContainer`）・専用の Gradle タスク・CI の入口は U1 が持ち、U3 はそれを使います。クラウドの基盤・IaC・検証環境は、配備先が決まるまで作りません（`project.md` の Deployment）。

## Deployment

| 項目 | 決めたこと | 理由 |
|---|---|---|
| 計算の形 | 変更なし。WAR に入るものが無い（`src/test/java` だけ） | テストだけの単位（unit-of-work.md） |
| 内部DB | 変更なし。テストの間だけ、U1 の `InternalDbPostgresContainer`（`postgres:18.6` のダイジェスト固定、JVM ごとに 1 つ）の中に、テストごとの専用データベース（`msidb_<乱数>`）を作って消す | NFR3.1・NFR7.2 |
| ネットワーク・保存 | 変更なし。コンテナのポートは Testcontainers がランダムに割り当て、テストの JVM からだけ使う。ボリュームを持たない（コンテナごと消える） | 新しい通信・保存先が無い |
| 環境 | 手元と CI（手動の実行）だけ。検証環境・本番は無い | `team.md` の Deployment |
| IaC | 無し | 配備先が決まるまで基盤を作らない |
| 資源の大きさ | 変更なし。待ちの確かめでは、保持側の接続（テストごとに数本）と、クラスごとのアプリの接続プールが、専用のデータベースへの接続を増やすが、コンテナの `max_connections` の既定（100）の内に収まる（共有のアプリ 1 つ + クラスごとのアプリ 1 つ + 保持側が数本。同時に動くアプリは最大 2 つ） | NFR7.2 |
| 秘密情報 | 新しい秘密情報・環境変数を足さない。接続の資格情報は実行ごとの乱数で、起動の引数にだけ持つ | NFR2.1・NFR2.3 |

## Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| PostgreSQL のコンテナ（Testcontainers） | テストの相手 | U1 の `InternalDbPostgresContainer` のまま（版・ダイジェストは `TargetDbImages` と同じ） | U3 は版を重ねて書かない（NFR3.1） |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | 無し | — | 1 インスタンス・開発者の PC の前提で当たらない |
| 内部DB の PostgreSQL（compose の profile） | 配備の側 | U4 が足す | この単位では書かない |

## Shared Infrastructure

この単位が持つ共有のインフラ資源はありません。U1 の共有（テストの土台・専用タスク・CI の入力）を使います。

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| PostgreSQL のコンテナ（JVM ごとに 1 つ）・専用のデータベースの作成と削除 | u1-internal-db-platform | u3-postgres-behaviour-tests | `InternalDbPostgresDatabase.create()` / `close()` の中だけ。U3 のテストの本体に管理者の資格情報を渡さない（NFR2.4） |
| `internalDbPostgresTest` タスク・タグ `internal-db-postgres` | u1-internal-db-platform | u3-postgres-behaviour-tests | U3 のクラスに `@InternalDbPostgresIT` を付けるだけで入る。タスクの設定は変えない |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/logical-components.md`・`scalability-design.md`・`security-design.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/memory/team.md`（Way of Working・Deployment）・`project.md`（Deployment）

## Assumptions & Open Questions

- [assumption] コンテナの `max_connections`（既定 100）の内に、共有のアプリ・クラスごとのアプリ・保持側の接続が収まる。アプリの接続プールの上限は今のまま（30）で、プールは使った分だけ張るため、Code Generation の最初の実行で `pg_stat_activity` の最大の数を 1 回記録して確かめる。
