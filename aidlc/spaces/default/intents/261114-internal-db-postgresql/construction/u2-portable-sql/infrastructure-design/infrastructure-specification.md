# インフラの仕様 — u2-portable-sql

この単位は、実行環境（compose・イメージ・配備）を変えません。書き換えるのはアプリの書き込みの文だけで、新しいサーバー・コンテナ・ネットワーク・保存先・秘密情報を足しません。PostgreSQL のテスト用コンテナと CI の入口は U1 が持ちます（この単位は使いません）。クラウドの基盤・IaC・検証環境は、配備先が決まるまで作りません（`project.md` の Deployment）。

## Deployment

| 項目 | 決めたこと | 理由 |
|---|---|---|
| 計算の形 | 変更なし。アプリは今のまま実行可能 WAR（コンテナの中で起動）。イメージ・Dockerfile・compose を変えない | 実行時の変更が文の形だけ（`savePreferences` の取り出し方と、書き込みの手順） |
| 内部DB | 変更なし。H2 の既定のまま。この単位は PostgreSQL の起動・コンテナを持たない（U1 の専用タスクで確かめる範囲） | 単位の中身は H2 だけで確かめられる（unit-of-work.md） |
| ネットワーク・保存 | 変更なし | 新しい通信・保存先が無い |
| 環境 | 手元と CI だけ（検証環境・本番は無い） | `team.md` の Deployment |
| IaC | 無し | 配備先が決まるまで基盤を作らない |
| 資源の大きさ | 変更なし（接続プールの上限 30・ヒープの設定は今のまま）。書き込みの手順は接続を増やさない | NFR7.2 |
| 秘密情報 | 新しい秘密情報・環境変数を足さない | NFR2 |

## Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | 無し | — | 1 インスタンス・開発者の PC の前提で当たらない |
| 内部DB の PostgreSQL（compose の profile） | 配備の側 | U4 が足す | この単位では書かない |

## Shared Infrastructure

この単位が持つ共有のインフラ資源はありません（新しいコンテナ・タスク・入力を足さない）。U1 の共有（テストの土台・専用タスク・CI の入力）を、U3 が使います。

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| なし（この単位は共有のインフラを足さない） | — | — | — |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/logical-components.md`・`scalability-design.md`・`performance-design.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md`（UserPersistence・RolePersistence・DslManagePersistence。いずれも実行時のインフラの部品を持たず、1 つのアプリの中の部品）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/functional-spec.md`（1.1: この単位が変えるもの・変えないもの）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/memory/team.md`（Way of Working・Deployment）・`project.md`（Deployment）

## Assumptions & Open Questions

None.
