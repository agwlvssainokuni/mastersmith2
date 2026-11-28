# 拡張性の設計 — u3-postgres-behaviour-tests

NFR7.1・NFR7.2 を設計に落とします。この単位はテストだけで、規模の目標を足しません。

## 設計の決定

| ID | 決定 | 満たす NFR | 理由 |
|---|---|---|---|
| DD-19 | アプリのインスタンスは 1 つの前提のまま。同時の実行の確かめは、1 つのアプリの中の複数のスレッドと、テスト側の JDBC の接続（保持側）で行い、複数のアプリを同じデータベースへ向けない。 | NFR7.1 | 要件 NFR7 |
| DD-20 | `internalDbPostgresTest` は `maxParallelForks` を 1 のまま（並列を指定しない）、JUnit の並列実行を有効にしない。PostgreSQL のコンテナは JVM ごとに 1 つ（U1 の `InternalDbPostgresContainer`）。共有のアプリとクラスごとのアプリが同時に動くのは、共有のアプリが JVM の終了まで起動したまま、クラスごとのアプリが順に起動する形（同時に 2 つ以上のアプリが動くが、データベースは別）で、同じデータベースを共有しない。 | NFR7.2 | `project.md` の Testing Posture（長いテストを重ねない） |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/scalability-requirements.md`

## Assumptions & Open Questions

None.
