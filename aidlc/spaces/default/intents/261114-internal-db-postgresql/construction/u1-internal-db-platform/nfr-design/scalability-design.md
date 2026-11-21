# 規模・同時性の設計 — u1-internal-db-platform

水平のスケール・負荷分散・データの分割・自動のスケールは、この Intent で扱いません（アプリは 1 インスタンス。NFR7.1）。ここでは、接続の数・データの大きさ・H2 固有の運用の扱いを決めます。

## 設計の決定

| ID | 決定 | 対応する要件 |
|---|---|---|
| SD-1 | アプリは 1 インスタンスの前提のままにする。複数のインスタンスの同時の移行の保証（Flyway の排他に頼ること）を約束せず、設計・テストに置かない。README と設計の文書に前提を書く（U4） | NFR7.1 |
| SD-2 | 内部DB の接続プール（`mastersmith-db`、既定 30、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`）は変えない。PostgreSQL の同時の接続の上限（既定 100）の中に、内部DB の 30 本と対象DB の接続プール（読み取り 10・書き込み 5）が収まる。compose の PostgreSQL の設定の確かめは U4 | NFR7.2 |
| SD-3 | PostgreSQL の内部DB はアプリ専用のデータベースと利用者を使う。H2 からのデータの移し替えは行わず、空から始める。PostgreSQL のテストは、同じコンテナの中に専用のデータベースを作って使う（BR6.6） | NFR7.3 |
| SD-4 | 大きなバイナリは 1 つの写し方（`@JdbcTypeCode(SqlTypes.LONG32VARBINARY)`・列は BYTEA）で両方の DB を扱う。上限 10 MB は変えない。データの増え方の見積もりは H2 のときから変えない。往復の確かめ（10 MB ちょうど）は U3 | NFR7.4 |
| SD-5 | H2 のファイルを詰め直す道具（`docker/hikari-pool.sh` の compact）と、接続プールの一時停止の許可（`allow-pool-suspension: true`）は、この単位で設定を変えない。PostgreSQL のときの運用の文書では「使わない」と書く（U4）。許可の設定は PostgreSQL のときも残るが、使わなければ害は無い | NFR7.5 |

## 容量の見積もり

| 項目 | 見積もり |
|---|---|
| 内部DB の接続の数 | 上限 30（既定）。PostgreSQL の既定の上限 100 の 3 割 |
| 対象DB の接続の数 | 読み取り 10・書き込み 5（今のまま。内部DB の PostgreSQL とは別の接続先） |
| 大きな列 | 1 行 10 MB まで（DSL の本文）。Hibernate は列を 1 行ぶんまとめて読むため、同時の読み出しの数と 10 MB の積がメモリに乗る。上限と同時の数は今のまま（H2 と同じ。メモリの目標は NFR1.1 のとおり変えない） |

## 採らなかった形

| 形 | 理由 |
|---|---|
| PostgreSQL のときだけ接続プールの上限を変える | H2 のときの設定を 1 つも変えない決まり（BR1.7）と、設定の種類が増える手間。上限は環境変数で運用者が変えられる |
| 複数のインスタンスを見越して移行の排他を設計する | 要件の範囲の外（6 章）。確かめられない保証は置かない |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/scalability-requirements.md`（NFR7.1〜NFR7.5）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（5 章・6 章）
- `backend/src/main/resources/application.yaml`（接続プールの設定）
- `aidlc/spaces/default/knowledge/aidlc-shared/checklist-dsl.md`（DSL の本文の大きさ）

## Assumptions & Open Questions

- [assumption] PostgreSQL の既定の `max_connections`（100）のまま使う。compose の設定の確かめは U4（NFR7.2）。
