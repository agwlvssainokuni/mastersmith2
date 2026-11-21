# 規模・同時性の要件 — u1-internal-db-platform

上流の要件 NFR7（インスタンス）と、それに関わる接続の数・データの大きさを `NFR7.x` に分けます。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR7.1 | PostgreSQL のときも、アプリのインスタンスは 1 つの前提のままとする。複数のインスタンスで動かすことは約束せず、Flyway の移行が複数のインスタンスから同時に走る場合の保証も持たない。 | 文書（README・設計）に前提を書く（U4）。この単位に複数インスタンスの確かめは置かない | 要件 NFR7、6 章 |
| NFR7.2 | 内部DB の接続の数の上限（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`、既定 30）は、PostgreSQL のときも変えない。PostgreSQL の同時の接続の上限（既定 100）の中で、内部DB の 30 本と対象DB の接続プール（読み取り 10・書き込み 5）が収まる。 | 設定の差分に接続プールの上限の変更が無いこと。compose の PostgreSQL の設定（`max_connections`）の確かめは U4 | 要件 NFR7、`application.yaml` の既存の設定 |
| NFR7.3 | 内部DB の PostgreSQL は、アプリ専用のデータベースと利用者を使い、ほかのアプリと共有しない。H2 から PostgreSQL へのデータの移し替えは行わず、空から始める。 | 文書に書く（U4）。結合テストは空のデータベースから始める（BR6.6） | 要件 5 章・6 章（Q2） |
| NFR7.4 | 大きなバイナリ（DSL の本文の 2 列）は、PostgreSQL でも H2 と同じ上限（10 MB）を扱える。列の型は BYTEA で、写し方は `@JdbcTypeCode(SqlTypes.LONG32VARBINARY)` にして、1 つの写し方で両方の DB を扱う。データの増え方の見積もりは H2 のときから変えない。 | `ddl-auto: validate` が両方の DB で通ること（BR4.4）。10 MB ちょうどの往復の確かめは U3 | BR4.1、BR4.3、BR4.4、`checklist-dsl.md` |
| NFR7.5 | H2 のファイルを詰め直す運用の道具（`docker/hikari-pool.sh` の compact・接続プールの一時停止の許可 `allow-pool-suspension`）は、H2 のときのものとして残し、PostgreSQL のときの運用の文書では「使わない」と書く。この単位は設定を変えない。 | U4 の文書の確かめ。`application.yaml` の差分に `allow-pool-suspension` の変更が無いこと | BR1.7、`application.yaml` の既存の設定 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR3・NFR7、5 章・6 章）[desc]
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`（BR1.7・BR4.1・BR4.3・BR4.4・BR6.6）
- `backend/src/main/resources/application.yaml`（内部DB の接続プール `mastersmith-db`、上限 30、`allow-pool-suspension: true`）
- `aidlc/spaces/default/knowledge/aidlc-shared/checklist-dsl.md`（DSL の本文の大きさと、バイナリ列の上限の突き合わせ）

## Assumptions & Open Questions

- [assumption] PostgreSQL の `max_connections` は既定の 100 のまま使う。実際の compose の設定で 30＋15 本（接続プール）と運用の接続が収まることは、U4 の compose の変更のときに確かめる（この単位の外）。
