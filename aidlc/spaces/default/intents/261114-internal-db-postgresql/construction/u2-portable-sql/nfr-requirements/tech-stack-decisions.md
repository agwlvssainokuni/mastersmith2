# 技術選定と品質の決まり — u2-portable-sql

この単位で足す・選ぶ技術と、その理由を書きます。上流の要件 NFR3（対応の範囲）・NFR6（カバレッジ）・NFR8（依存）・NFR9（構造）を、ここで `NFRx.y` に分けます。

## 新しく足す依存

なし。次の既存の部品だけを使います。

| 部品 | 版 | 使う場所 |
|---|---|---|
| Hibernate ORM（`Session.doWork`・`doReturningWork`） | 7.4.13（今のまま） | 書き込みの手順（セーブポイント）と `savePreferences`（生成キー）で、JDBC の接続を取る |
| JDBC（`PreparedStatement`・`Savepoint`・`getGeneratedKeys`） | JDK 25 の標準 | 同上 |
| H2・PostgreSQL のドライバー | 今のまま（H2 2.4.240・pgjdbc 42.7.14） | 変えない |

## 決めたこと

| ID | 決まり | 理由と、検討した別の形 |
|---|---|---|
| NFR3.1 | 対応する DB は H2 と PostgreSQL の確かめた版（18 系）だけ（U1 と同じ）。書き込みの手順は、この 2 つに通る書き方（標準の JDBC・標準の SQL の `UPDATE`・`INSERT`）に限り、`MERGE ... KEY`・`ON CONFLICT`・`RETURNING`・`FINAL TABLE` のような片方にだけ通る書き方は使わない。 | ADR-003（種類で分岐しない）。別の形: 種類ごとに文を書き分ける案は、境界テストで守れず採らない |
| NFR6.1 | 新しい部品と書き換えた repository の分岐は、コンテナを使わない単体テストと H2 の結合テストだけで、パッケージごとの下限（行 80%・分岐 70%）を満たす。PostgreSQL にだけ通る分岐が無い（種類で分岐しない）ため、PostgreSQL の結合テストに頼らない。 | 要件 NFR6、`team.md` の Testing Posture |
| NFR6.2 | 下限を満たすための計測の除外を足さない。`common.persistence` を除外の一覧に入れない。 | `team.md` の Testing Posture |
| NFR6.3 | 検査の時間・カバレッジの報告は、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` の実測だけを使う。 | `project.md` の Testing Posture |
| NFR8.1 | 依存を足さない。依存の木・lockfile・OSV-Scanner の対象に差が出ないことを、コード生成で `backend/gradle.lockfile` に差分が無いことで確かめる。 | 要件 NFR8 |
| NFR9.1 | 手順は `common.persistence` の 1 つの部品に置く。機能（`role`・`dslmanage`）のパッケージに依存せず、`role`・`dslmanage` が部品を使う向きだけにする。業務処理の層は DB の種類で分岐しない。 | 要件 NFR9、機能設計 BR2.7・BR7.1 |
| NFR9.2 | 既存の ArchUnit の境界テスト（`ArchitectureTest`・`UserBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`DslManageBoundaryArchitectureTest`）を緩めず、消さない。`common.persistence` の新しい部品が機能のパッケージに依存しないことを確かめる検査を足す。 | `team.md` の Code Style、`project.md` の Code Style |
| NFR9.3 | 部品の呼び出し元（repository）の呼び出しの形（引数・戻り値・例外の種類）は、契約 C5 のとおり変えない。 | 契約 C5 |

## 実装の場所の選び方（機能設計の決定の確認）

- `savePreferences` は Spring Data の `@Query` のインターフェースのメソッドで、`Session.doReturningWork` をその中に書けない。カスタムのフラグメント（インターフェース + 実装クラス）に移すか、サービスの側から呼ぶ別の部品を置く形になる。どの形にするかと、層の境界テストへの影響は NFR 設計で決める（機能設計の審査の指摘）。
- `doWork` に切り替えると、`executeUpdate` が実行前に行っていた未反映の変更の送り出し（flush）が無くなる。送り出しの扱いと、挿入の重なり・上限切れ以外の失敗（外部キーの違反など）が `doWork` からどんな型の例外で出るかは、NFR 設計で決める（`RoleStore` は `PersistenceException` だけを受けて分類するため、型を保つ必要がある）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR3・NFR6・NFR8・NFR9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`（BR2.4・BR2.7・BR6.3・BR7.1）・`functional-spec.md`（F3・F7）・`reviews/review-01.md`（R-01・R-02）
- `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style）・`aidlc/spaces/default/memory/project.md`
- `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`

## Assumptions & Open Questions

- [assumption] Hibernate 7.4.13 の `Session.doWork`・`doReturningWork` は、呼び出し元のトランザクションの接続を渡し、`doWork` の前に未反映の変更を送り出さない（捨ての試しの観察）。コード生成の最初の単体・結合テストで確かめる。
