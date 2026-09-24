# Unit Test Instructions — U1 対象DB（u1-target-db）

U1 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の結合テスト）。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ | `backend/build.gradle.kts` の `tasks.test`（`*Test`）と `integrationTest`（`*IT`） |
| 性質ベースのテスト | jqwik 1.10.1（既存） | `backend/src/test/resources/junit-platform.properties`（`jqwik.database`）。失敗時の乱数の種はテストの出力に残る |
| 構造の検査 | ArchUnit 1.5.0（既存） | `backend/src/test/resources/archunit.properties` |
| 対象DB のコンテナ | Testcontainers（MySQL・MariaDB・PostgreSQL のモジュール、JUnit 5 の連携。版は Spring Boot の BOM、Step 2 で足す） | イメージの版とダイジェストは `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java` の1か所 |
| 内部DB | 組み込みの H2（既存。コンテナは使わない） | 既存の `TestDatabase` |
| カバレッジ | JaCoCo 0.8.15（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification` |

新しいテストの設定のファイルは足さない。Testcontainers の依存の追加（計画の Step 2）が終わると、下の結合テストのコマンドがコンテナを使えるようになる。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U1 のパッケージ `cherry.mastersmith.targetdb` だけに絞る。

単体テスト（`*Test`。値・設定の点検・読み取りの口・構造の検査）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'
```

結合テスト（`*IT`。起動と配線は組み込みの H2、読み取りは3種類の DB のコンテナ）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'
```

1種類の DB だけを試すとき（例: PostgreSQL）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*Postgres*'
```

- 最初のテスト（計画の Step 4）より前は、対象のテストが無いため Gradle が「一致するテストが無い」で失敗する。これは想定どおりで、Step 4 からコマンドが通る。
- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest` または `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（project.md の Testing Posture）。
- 結合テストはコンテナの実行環境（colima）が動いていることを前提にする。`colima status` で動いていることを確かめ、止まっていれば `colima start` で起動する。

## 3. コンテナの実行環境が無いとき

- 開発中（環境変数 `CI` が無い）: 対象DB のコンテナを使うテストだけが中断（飛ばした扱い）になり、「コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない」という WARN が出る。ほかのテストは動く。
- CI（GitHub Actions は `CI=true` を設定する）: 飛ばさずに失敗する。
- 飛ばした状態では統合しない。警告が出たら colima を起動して、同じコマンドをやり直す（team.md の Way of Working）。

## 4. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上（既存の `jacocoTestCoverageVerification`）。
- パッケージごと: `cherry.mastersmith.targetdb` の下の各パッケージ（`domain`・`config`・`repository`・`service`）で行 80%・分岐 70% 以上。既存のパッケージにも当てるかは計画の Step 13 の実測で決める。
- 計測から外すのは既存の除外（起動クラスと `*Properties`）だけ。U1 のために除外を足さない。`TargetDbProperties` は既存の除外 `**/*Properties.class` に当たる（設定値だけの record のため）。
- 単位だけのカバレッジは、単位のテストを実行した後に `./gradlew :backend:jacocoTestReport` の報告（`backend/build/reports/jacoco/test/html/cherry.mastersmith.targetdb*/`）で見る。下限の判定は全体の `./gradlew verify` の 7 の段で行う。

## 5. 差し替え（モック・スタブ）の方針

- 対象DB の読み取り（`MysqlSchemaQueries`・`PostgresSchemaQueries`）はモックにせず、3種類の DB の本物のコンテナで確かめる（project.md の Mandated。H2 などで代用しない）。
- 読み取りの口（`TargetSchemaReader` の実装）の単体テストでは、JDBC の `DataSource`・`Connection`・`PreparedStatement` を Mockito（Spring Boot の BOM に含まれる）で差し替え、打ち切りの例外（`SQLTimeoutException`、SQLState `57014`）・接続の失敗（SQLState `08*`・`28*`）・途中の失敗を作る。
- 照合の全体の上限は作らない（NFR 設計の承認の場の決定 B）。目的ごとの問い合わせ1回の待ち（生成 20 秒・照合 5 秒）が `setQueryTimeout` に渡ることを、差し替えた `PreparedStatement` で確かめる。`Thread.sleep` や実時刻に依存しない。
- 応答しない対象DB（接続を受け付けて何も返さない）は、テストの中で `ServerSocket` を開いて作る。外の端末に接続しない。
- ログの確かめは既存の `common/testsupport/LogEvents` を使う（WARN の件数、項目名だけであること、値が無いこと）。

## 6. テストのデータ

- 内部DB を使う起動の結合テストは、既存のとおりテストごとに一時ディレクトリの H2 を使う（`TestDatabase.url(dir)`）。
- 対象DB のコンテナは、テストのクラスごとに1つ起動し、名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作ってテストの表・ビュー・外部キー・コメントを入れ、クラスの終わりに消す。表を作る操作は巻き戻せないため、トランザクションの巻き戻しには頼らない（team.md の Testing Posture、NFR12.2）。
- 読み取りの権限だけのアカウントは、同じくクラスごとに作って消す。パスワードはテストの中で乱数から作り、コードやファイルに固定の値を書かない。
- 表・カラムの名前には、大文字・小文字の混在、引用符・空白・セミコロンを含むもの、日本語のコメントを入れる。テストの説明文（`@DisplayName`）は英語。
- 別のスキーマのテーブルと、そこへの外部キーを持つテーブルを用意し、写しに含まれないことを確かめる。
