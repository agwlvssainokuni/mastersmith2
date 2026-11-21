# 技術選定と品質の決まり — u1-internal-db-platform

この単位で足す・選ぶ技術と、その理由を書きます。上流の要件 NFR3（対応の範囲）・NFR6（カバレッジ）・NFR8（依存）・NFR9（構造）を、ここで `NFRx.y` に分けます。

## 新しく足す依存

| 部品 | 版 | ライセンス | 足す理由 | 版の決め方 |
|---|---|---|---|---|
| `org.flywaydb:flyway-database-postgresql` | 12.4.0（`flyway-core` と同じ。Spring Boot 4.1.2 の BOM が管理） | Apache License 2.0（Maven Central の親 POM の `licenses`） | Flyway 12 は PostgreSQL の対応を別の部品に切り出しており、無いと PostgreSQL への移行が動かない（下調べ 7 節の試し） | 版を書かず BOM に任せる（BR2.12）。lockfile で固定する |

既にある部品を使い回すもの（足さない）:

| 部品 | 版 | 使う場所 |
|---|---|---|
| PostgreSQL の JDBC ドライバー（`org.postgresql:postgresql`） | 42.7.14（lockfile。BSD 2-Clause） | 対象DB で既に使っているものを、内部DB にも使う |
| Testcontainers の PostgreSQL（`org.testcontainers:testcontainers-postgresql`） | 2.0.5（lockfile） | 対象DB のテストで使っているものを、PostgreSQL の結合テストに使う |
| PostgreSQL のコンテナのイメージ | 18.6（`TargetDbImages.postgres()` のタグとダイジェスト） | 内部DB の PostgreSQL の結合テストと、後の compose。上げるときは対象DB と一緒に上げる（`team.md` の Deployment） |
| HikariCP・Hibernate ORM 7.4 系・Spring Boot 4.1.2・H2 2.4.240 | 今のまま | 変えない |

## 決めたこと

| ID | 決まり | 理由と、検討した別の形 |
|---|---|---|
| NFR3.1 | 内部DB として対応するのは、組み込みの H2（今の版）と PostgreSQL の確かめた版（18 系）だけ。MySQL・MariaDB など対応しない種類は、起動を止める（BR1.4）。 | 確かめていない DB を黙って動かさない。別の形: 種類を問わず JDBC に任せる案は、移行・バイナリ・排他が確かめられず採らない |
| NFR3.2 | PostgreSQL のコンテナのイメージは、版のタグと複数アーキテクチャの index のダイジェストで固定し、`TargetDbImages.postgres()` を使う。内部DB 用に別の定数を持たない。 | 内部DB と対象DB を同じ版にそろえ、上げるときに食い違わない（`team.md` の Deployment）。別の形: 内部DB 用の定数を別に持つ案は、上げ漏れの危険がある |
| NFR3.3 | 確かめた版（18 系）以外の PostgreSQL への対応は約束しない。 | 要件 NFR3、6 章（Q5） |
| NFR8.1 | 足す依存は、採用の前にライセンスを確かめた。`flyway-database-postgresql` は Apache License 2.0 で、このプロジェクトと同じ。README のライセンスの一覧に足す必要は無い（Apache License 2.0 で、jar 同梱の独立のライセンス文書が要る種類ではないため。PostgreSQL の JDBC ドライバーは既に一覧にある）。 | `team.md` の静的解析とセキュリティ検査（新しい依存のライセンス）。`META-INF/third-party-licenses` に置くのは、jar にライセンスの文書が含まれない MariaDB Connector/J のような場合だけ（README のライセンスの節） |
| NFR8.2 | 版は BOM に任せ、`backend/gradle.lockfile` で固定する。CI では lockfile どおりに入れる。 | `project.md` の Mandated（依存は lockfile で固定） |
| NFR8.3 | 足した後で、依存の木（`./gradlew :backend:dependencies`）の前後を比べ、既存の部品の版が引き上がっていないことを確かめて、コード生成の記録に書く。推移依存は `flyway-core` だけ（Maven Central の POM）のため、引き上げは起きない見込み。 | `project.md` の Corrections（推移依存で既存の部品の版を引き上げないか） |
| NFR8.4 | 足した依存は、`backend/gradle.lockfile` を通して OSV-Scanner の対象に入る。重大度 High 以上で統合を止める基準のまま。 | `team.md` の静的解析とセキュリティ検査、`./gradlew verify` の OSV-Scanner |
| NFR9.1 | DB の種類ごとの差は、repository の層（と移行の置き場）に閉じる。業務処理の層は DB の種類で分岐しない。種類を見るのは `InternalDbPlatform` の 1 か所（BR1.5）。 | 要件 NFR9、BR7.2。別の形: 種類の分岐を各所に散らす案は、境界テストで守れず採らない |
| NFR9.2 | 既存の ArchUnit の境界テストは緩めず、消さない。新しい機能 `internaldb` に `InternalDbBoundaryArchitectureTest` を足す。 | `team.md` の Code Style、`project.md` の Code Style |
| NFR9.3 | PostgreSQL の結合テストの目印は、JUnit のタグ `internal-db-postgres` と、注釈 `@InternalDbPostgresIT`。専用の Gradle のタスクは `:backend:internalDbPostgresTest`。 | 機能設計 D-7・BR6.1 |
| NFR6.1 | PostgreSQL のときだけ通るコード（種類の判断・接続の設定・失敗の記録）は `cherry.mastersmith.internaldb.platform` に集め、通常の `./gradlew verify`（コンテナ無し）の単体テストと H2 の結合テストだけで、行 80%・分岐 70% の下限を満たす。 | 要件 NFR6、BR7.1、BR7.3 |
| NFR6.2 | 下限を満たすための計測の除外を足さない。`internaldb.platform` を除外の一覧に入れず、パッケージごとの下限を当てる。 | `team.md` の Testing Posture（除外を増やして下限を下げることはしない） |
| NFR6.3 | 検査の時間・カバレッジの報告は、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` の実測だけを使う（`verifyQuick` の参考値は使わない）。 | `project.md` の Testing Posture |

## 実装の場所の選び方（機能設計の決定の確認）

- Spring Boot との組み立ては、起動の中の 3 つの入口（Flyway の設定の差し替え・接続プールの初期化・Flyway の移行の入口。D-2）にする。`EnvironmentPostProcessor` で設定を足す案は、PostgreSQL の結合テストがコンテナの起動の後に接続先を渡すため値を読み逃し、採らなかった。
- 接続ごとの `lock_timeout` の渡し方（接続プールの初期化の文か、接続先の URL の指定か）の細部と、移行の接続だけ上限を外すかは、NFR Design で決める（NFR4.3）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR3・NFR6・NFR8・NFR9、4 章）[desc]
- `aidlc/spaces/default/knowledge/aidlc-shared/study-internal-db-postgresql.md`（7 節: Flyway の PostgreSQL の部品。8 節以降の試しの結果）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md`（D-1〜D-10）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`（BR1.4・BR1.5・BR2.12・BR6.1・BR7.1〜BR7.3）
- `backend/gradle.lockfile`（`flyway-core` 12.4.0・`postgresql` 42.7.14・`testcontainers-postgresql` 2.0.5）
- Maven Central の `flyway-database-postgresql` 12.4.0 の POM（依存は `flyway-core` のみ）と、親の `flyway-parent` 12.4.0 の POM（Apache License, Version 2.0）
- `README.md`（ライセンスの節）、`aidlc/spaces/default/memory/team.md`（Deployment・Testing Posture・Code Style）、`aidlc/spaces/default/memory/project.md`（Mandated・Corrections）
- `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`

## Assumptions & Open Questions

- [assumption] `flyway-database-postgresql` の依存が `flyway-core` だけであることは、Maven Central の POM で確認した。コード生成で足した後の依存の木の前後の比較（NFR8.3）で、実際の差を確かめ直す。
