# 拡張性の要件 — u2-portable-sql

上流の要件 NFR7（インスタンス）を、この単位の `NFR7.x` に分けます。アプリのインスタンスは 1 つの前提のまま変えません。この単位は、同時の書き込みの扱いと、接続の使い方の増えない約束だけを置きます。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR7.1 | アプリのインスタンスは 1 つの前提のまま。複数のインスタンスで内部DB を共有する設計・設定を足さない。同時の書き込み（同じ鍵への 2 つ）は、同じインスタンスの中の 2 つのトランザクションだけを想定し、行の排他と一意の制約で直列化する。 | 設計の読み取り（分散の部品・ロックの部品を足さない） | 要件 NFR7 |
| NFR7.2 | 書き込みの手順は、接続を新しく借りず、呼び出し元のトランザクションの接続を使う（セーブポイントも同じ接続）。内部DB の接続プール（上限 30）の使い方を増やさない。 | 結合テストで、書き込みの手順が接続を借りる回数が書き直しの前と同じであること（NFR1.2） | 要件 NFR7、`docs/operations.md` の既知の制約（接続を 2 本使う経路） |
| NFR7.3 | 同時の書き込みの失敗は、有限の時間で終わる（H2 は約 4 秒・PostgreSQL は約 2 秒の上限）。上限を足したり変えたりしない。 | 結合テスト（H2）で、保持が長いとき上限切れの失敗として出ること（機能設計 BR6.2） | 要件 NFR7、機能設計 BR2.5 |
| NFR7.4 | 表の形・データの量・列の型は変えない（移行を足さない）。 | 移行のファイルが変わらないこと（U1 の BR2.1 の確かめ） | 要件 NFR7 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR7）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`（BR2.5・BR2.7・BR6.2）
- `aidlc/spaces/default/memory/team.md`（Deployment）

## Assumptions & Open Questions

None.
