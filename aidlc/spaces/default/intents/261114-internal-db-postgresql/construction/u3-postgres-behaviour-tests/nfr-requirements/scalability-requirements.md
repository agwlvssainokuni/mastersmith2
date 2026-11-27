# 拡張性の要件 — u3-postgres-behaviour-tests

上流の要件 NFR7（インスタンス）を、この単位の `NFR7.x` に分けます。この単位はテストだけで、負荷や規模の目標を足しません。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR7.1 | アプリのインスタンスは 1 つの前提のまま。PostgreSQL のテストは、複数のアプリのインスタンスを同じデータベースへ向けて動かさない（同時の実行の確かめは、1 つのアプリの中の複数のスレッド・接続と、テスト側の JDBC の接続で行う）。 | コードの読み取り | 要件 NFR7 |
| NFR7.2 | 共有のアプリと、クラスごとのアプリを同時に動かさない（テストの並列実行は使わない。`maxParallelForks` は 1 のまま）。PostgreSQL のコンテナは JVM ごとに 1 つ（U1 の `InternalDbPostgresContainer`）。 | `internalDbPostgresTest` の設定に並列の指定が無いこと | 要件 NFR7、`project.md` の Testing Posture（長いテストを重ねない） |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR7）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/functional-spec.md`

## Assumptions & Open Questions

None.
