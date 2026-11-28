# 単位のテストの手引き — u3-postgres-behaviour-tests

この単位のテストは、内部DB を PostgreSQL で動かす経路の振る舞いを確かめる結合テスト（`@InternalDbPostgresIT`、タグ `internal-db-postgres`）と、普段の `verify` に入る構造のテスト 1 クラスです。コンテナの実行環境（colima）が要ります。

## 前提（colima の PC）

```
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## この単位だけのコマンド（最初のテストの前に使えるもの）

- 骨組みの確かめ（U1 の 1 クラスで、土台が動くこと）:
  `DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew :backend:internalDbPostgresTest --tests 'cherry.mastersmith.internaldb.postgres.InternalDbPostgresBaselineIT'`
- 構造のテスト（コンテナを使わない）:
  `./gradlew :backend:test --tests 'cherry.mastersmith.internaldb.PgTestBoundaryArchitectureTest'`
- この単位のクラス 1 つ（例）:
  `DOCKER_HOST=... TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=... ./gradlew :backend:internalDbPostgresTest --tests 'cherry.mastersmith.internaldb.postgres.PgTimeRoundTripIT'`
- この単位のクラスの全件（待ちの確かめを含み、数分かかる）:
  `DOCKER_HOST=... TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=... caffeinate -dims ./gradlew :backend:internalDbPostgresTest --tests 'cherry.mastersmith.*.Pg*IT'`
- 承認済みの確かめのコマンド（Construction Verification Command）:
  `DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew verifyQuick :backend:internalDbPostgresTest`

長いテスト（全件）は `caffeinate -dims` で包み、並べて流さない。

## テストの枠組み

- JUnit 5・AssertJ・Spring Boot（`SpringApplicationBuilder` で実際にアプリを起動）・Testcontainers（`InternalDbPostgresContainer`）・pgjdbc・ArchUnit。新しい依存は足さない。
- クラスには `@InternalDbPostgresIT`（タグ・標準出力の取り込み・実行環境の確かめ）を付け、名前は `*IT`。説明（`@DisplayName`）は英語。

## 見込みの件数

- PostgreSQL の結合テスト 24 クラス（流れ 11・違反 2・待ち 4・失敗後 1・書き直し 3・型 3）＋ `PgSecretLeakIT` 1 クラスで、1 クラスあたり 3〜8 件（約 100 件の目安）。
- 構造のテスト 1 クラス（4 件の確かめ）。コンテナを使わない。

## 期待するカバレッジ

- この単位は `src/main` を変えず、JaCoCo の記録を取る `integrationTest` にも入らない。バックエンドの全体とパッケージごとの下限（行 80%・分岐 70%）は、普段の全件の `./gradlew verify`（Build and Test）が判定し、この単位の変更で値が動かないことを確かめる。この単位のコマンドで下限を判定しない。

## 模擬（mock）・データの扱い

- DB・アプリは模擬せず、実際の PostgreSQL（`postgres:18.6`、U1 の固定）と実際に起動したアプリを使う。
- データは各テストが API で作り、実行ごとの乱数で一意にする。メールアドレスは予約のドメイン（`example.com`）だけ。接続の情報・行の値・違反の文を出力に出さない。
- 待ち合わせは `pg_stat_activity` の観測（上限 10 秒）で行い、実時間の `sleep` を合否に使わない。
- H2 の既存のテストと部品は変えない。H2 の構文・部品に依存しない（構造のテストで確かめる）。
