# 単体テストの手順 — u1-internal-db-platform

この単位のテストを流す命令です。どれもこの単位のクラスだけに絞っている（プロジェクト全体の命令ではない）。Build and Test が単位ごとの命令を順に流す。

## テストの道具と設定

- 道具: JUnit 5（Jupiter）・AssertJ・ArchUnit・jqwik（必要なとき）・Spring Boot Test・Testcontainers（PostgreSQL。`TargetDbImages.postgres()`）。新しい道具は足さない。テストの設定（`backend/build.gradle.kts` の `tasks.test`・`integrationTest`・新しい `internalDbPostgresTest`）は、今の `-Dh2.compactThreads=1`・`user.timezone=Asia/Tokyo` などをそのまま使う。
- 置き場: `backend/src/test/java/cherry/mastersmith/internaldb/`（`platform/`・`migration/`・`postgres/`・`testsupport/`）。単体は `XxxTest`、起動を伴う結合は `XxxIT`。説明文（テスト名・`@DisplayName`）は英語、テストデータの日本語は可、メールアドレスは予約のドメイン（`example.com`）だけ。

## 命令（この単位だけ）

colima の PC では、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡してから流す。

1. 単体テスト（コンテナ不要）: `./gradlew :backend:test --tests 'cherry.mastersmith.internaldb.*'`
2. H2 と閉じたポートの結合テスト（コンテナ不要。タグ付きの PostgreSQL のクラスは除かれる）: `./gradlew :backend:integrationTest -PitTests='InternalDb*IT'`（当たるテストが 1 つも無いときは失敗になる。PostgreSQL のタグ付きのクラスだけに当たったときは、案内の文つきで失敗になる）
3. PostgreSQL の結合テスト（明示して流すときだけ。コンテナの実行環境が要る）: `./gradlew :backend:internalDbPostgresTest`
4. 構造の検査（既存の境界テストが新しいパッケージで落ちないことの確認を含む）: `./gradlew :backend:test --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.internaldb.InternalDbBoundaryArchitectureTest'`

2 と 3 は、コンテナで動かすテストを飛ばさない。コンテナの実行環境が無いとき、2 は `-PitTests` を付けているため失敗になる（CI と同じ扱い）。3 は手元では警告を出して飛ばす。飛ばした状態では統合しない（`team.md` の Way of Working）。

## 期待するカバレッジ

- この単位のパッケージ `cherry.mastersmith.internaldb.platform` は、**普段の `verify` に入るテスト（単体と H2・閉じたポートの結合テスト）だけで**、行 80%・分岐 70% を満たす（NFR6.1）。PostgreSQL の結合テストに頼らない。計測の除外を足さない（NFR6.2）。
- プロジェクト全体の下限（全体の合計と、すべてのパッケージごとの行 80%・分岐 70%）は、Build and Test が `:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で判定する。この単位のテストだけの実行では判定しない（他の単位のテストが覆うソースで下限を割ることがあるため）。下限の閾値は、この単位のために変えない。
- この単位の参考値を見るとき（判定ではなく読み取り）: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest -PitTests='InternalDb*IT' :backend:jacocoTestReport` の後に、`backend/build/reports/jacoco/test/jacocoTestReport.xml` から `cherry/mastersmith/internaldb/platform` の行・分岐の値を読む。このコマンドが下限の閾値で失敗するときも、選んだテストがすべて通っていれば失敗したテストとは扱わない。

## モック・代用の指針

- 種類の判断（`InternalDbUrlResolver`）・失敗の整形（`StartupFailureReporter`）は、モックを使わず値と例外を直接渡す。
- `InternalDbPoolCustomizer` は、接続を開かずに、`HikariDataSource` の設定の値（`connectionInitSql`）で確かめる（本物の Hikari を作って、最初の接続の前の setter が効くことも確かめる。接続先に届かないままでよい）。
- 起動の失敗のテストは、`SpringApplication` で起動して失敗を受け、ログは Logback のアペンダーで全ロガーの出力を集めて確かめる。DB やネットワークのモックは使わない（閉じたポートの本物の接続先を使う）。
- PostgreSQL に頼る振る舞いは、H2 で代用しない（`project.md` の Mandated）。実際の PostgreSQL（コンテナ）で確かめる。

## テストデータの扱い

- 空のデータベースが要る PostgreSQL のテストは、同じコンテナの中に実行ごとに一意の名前（`msidb_<乱数>`）で専用のデータベースを作り、終わりに `DROP DATABASE` で消す。途中で止まった前の実行の残りに頼らない。
- H2 のテストは、一時ファイルか名前の一意なメモリのデータベースを使い、実行順に依存しない。テストごとに自分が作ったデータだけを使い、自分で後始末をする。
- 秘密情報・実在の宛先をテストデータに置かない（コンテナの起動ごとの値かテスト専用の値だけ）。
- 時刻に依存する処理は無い。移行の時間・起動からヘルスチェックまでの時間は、ログに出す値としてだけ測り、合否には使わない。

## 流した結果の記録

結果（通った件数・失敗・飛ばしたもの）は `code-summary.md` に書く。流していないテストは、理由（コンテナの実行環境が無いなど）をつけて書き、流したものとして書かない。
