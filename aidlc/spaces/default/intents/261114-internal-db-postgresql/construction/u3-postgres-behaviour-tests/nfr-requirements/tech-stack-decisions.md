# 技術選定と品質の決まり — u3-postgres-behaviour-tests

この単位で足す・選ぶ技術と、その理由を書きます。上流の要件 NFR3（対応の範囲）・NFR6（カバレッジ）・NFR8（依存）・NFR9（構造）を、ここで `NFRx.y` に分けます。

## 新しく足す依存

なし。次の既存の部品だけを使います（版は変えません）。

| 部品 | 版 | 使う場所 |
|---|---|---|
| Testcontainers（PostgreSQL） | 今のまま | U1 の `InternalDbPostgresContainer` を使い、新しいコンテナの種類は足さない |
| pgjdbc | 42.7.14（今のまま） | 保持側の JDBC の接続（`pg_stat_activity` の観測を含む） |
| JUnit 5・AssertJ・ArchUnit | 今のまま | テストと構造のテスト |

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR3.1 | 対応の範囲は PostgreSQL 18 系（イメージは対象DB と同じ `postgres:18.6` のダイジェスト）だけ。U3 のテストは U1 の `InternalDbPostgresContainer` の固定の版を使い、版の文字列をテストの中に重ねて書かない。 | `grep` で `postgres:` の記述が U3 のテストに無いこと | 要件 NFR3 |
| NFR6.1 | PostgreSQL のテストは JaCoCo の記録を取らず、バックエンドのカバレッジの下限（行 80%・分岐 70%）の判定に入らない。U3 は `src/main` を足さないため、カバレッジの計測の対象を増やさず、下限の計算も変えない。計測の除外を足さない。 | `internalDbPostgresTest` の JaCoCo が無効のまま。Build and Test の全件の `verify` でパッケージごとの値が変わらないこと | 要件 NFR6 |
| NFR6.2 | 普段の `verify` で流れるテストは、構造のテスト `PgTestBoundaryArchitectureTest`（`*Test`）だけ。PostgreSQL のコンテナを使わない。 | 同上 | 要件 NFR6、NFR5 |
| NFR8.1 | 依存を足さない（ライセンス・lockfile・OSV-Scanner の確かめは要らない）。足す必要が出たときは、採用の前にライセンスと依存の木を確かめ、U3 の範囲外として依頼者に諮る。 | `backend/gradle.lockfile` に差分が無いこと | 要件 NFR8 |
| NFR9.1 | PostgreSQL のテストのクラスとその部品は、`org.h2` と H2 向けの既存の部品に依存しない（BR9.2）。構造のテスト `PgTestBoundaryArchitectureTest` で、クラス単位に守る。許す依存の一覧は、HTTP の呼び出し・ログの読み取り・時計・秘密の乱数の部品のうち、H2 の URL を作る型を含まないものに限り、機能設計で曖昧だった `TestDatabase.randomSecret` は、H2 の URL を作るクラスにあるため許す側に入れず、`InternalDbPostgresDatabase` が持つ同等の部品を使う（NFR 設計で形を決める）。 | `PgTestBoundaryArchitectureTest` の合格 | 要件 NFR9、機能設計のレビュー（持ち越し） |
| NFR9.2 | 既存の ArchUnit の境界テストを緩めない。`src/main` を変えないため、機能の境界の追加も要らない。 | 既存の構造のテストの差分が無いこと | 要件 NFR9、`team.md` の Code Style |
| NFR9.3 | H2 の既存のテストと部品は変えない。PostgreSQL 用の部品は新しいファイルとして足し、両方が使う既存の部品は読むだけにする。 | git の差分で、既存のテストのファイルが変わっていないこと | 要件 FR5.4、BR9.3 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR3・NFR6・NFR8・NFR9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md`（BR9）
- `aidlc/spaces/default/memory/team.md`（Code Style・Testing Posture）

## Assumptions & Open Questions

- [assumption] `InternalDbPostgresDatabase` が持つ乱数の秘密の部品（`TestDatabase.randomSecret` と同等）で足りる。足りないときは、`TestDatabase` を読むだけで使うことの是非を NFR 設計で決める。
