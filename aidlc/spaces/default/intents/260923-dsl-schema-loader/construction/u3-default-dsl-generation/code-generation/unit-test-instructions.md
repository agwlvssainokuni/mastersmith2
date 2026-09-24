# Unit Test Instructions — U3 既定の DSL の生成（u3-default-dsl-generation）

U3 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の結合テスト）。U3 の計画の Step 2 で U1 のコードに手が入るため、U1 のテストのコマンドもあわせて示す。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ、Mockito | `backend/build.gradle.kts` の `tasks.test`（`*Test`）と `integrationTest`（`*IT`） |
| 性質ベースのテスト | jqwik 1.10.1（既存） | `backend/src/test/resources/junit-platform.properties`。失敗時の乱数の種はテストの出力に残る |
| 構造の検査 | ArchUnit 1.5.0（既存） | `backend/src/test/resources/archunit.properties` |
| 対象DB のコンテナ | Testcontainers（U1 で導入済み）。イメージは `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`、コンテナの実行環境が無いときの扱いとスキーマの作り方も同じ `testsupport` の仕組みを使う | U1 のまま |
| カバレッジ | JaCoCo 0.8.15（既存） | `backend/build.gradle.kts` |

新しいテストの設定のファイルと依存は足さない。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。

U3 の単体テスト（型の分類・組み立て・書き出し・生成の口・構造の検査）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.*'
```

U3 の結合テスト（3種類の DB のコンテナで、読み取り → 生成 → U2 の検証）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.*'
```

計画の Step 2 で変える U1 のテスト（`columnType`）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'
```

- `dslmanage` のコマンドは、最初のテスト（計画の Step 4）より前は対象のテストが無いため Gradle が「一致するテストが無い」で失敗する。これは想定どおり。結合テストのコマンドは Step 8 から通る。
- 結合テストはコンテナの実行環境（colima）が動いていることと、README の「対象DB」の節の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` の設定を前提にする。コンテナの実行環境が無いときは、U1 と同じく警告を出して対象DB のテストだけを飛ばす（CI では失敗）。飛ばした状態では統合しない。
- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest` または `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（project.md の Testing Posture）。

## 3. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上。
- パッケージごと: `cherry.mastersmith.dslmanage.generate` で行 80%・分岐 70% 以上（U1 で入れた、新しいパッケージに当てる決まり）。U1 の `targetdb` の各パッケージも下限を保つ。
- 計測から外すのは既存の除外だけ。U3 のために除外を足さない。
- 単位だけのカバレッジは `./gradlew :backend:jacocoTestReport` の報告（`backend/build/reports/jacoco/test/html/cherry.mastersmith.dslmanage.generate/`）で見る。下限の判定は全体の `./gradlew verify` で行う。

## 4. 差し替え（モック・スタブ）の方針

- 生成の口の単体テストでは、U1 の `TargetSchemaReader` だけを差し替え（Mockito、または写しを返す小さな実装）、UNCONFIGURED・UNAVAILABLE・写しを返す。U2 の `DslReader` は本物（`DefaultDslReader`）を使い、生成した本文が実際に検証を通ることを確かめる。
- U2 の検証を通らない場合（BR5.2）の確かめは、書き出しの部品を差し替えて通らない本文を渡す形で行う。
- 3種類の DB の読み取りはモックにせず、結合テストで本物のコンテナを使う（project.md の Mandated）。
- 時刻・乱数に依存しない（生成は日時を入れない）。内訳の時間のログは値を確かめず、ログに本文・写しの値・接続先が出ないことだけを確かめる（既存の `common/testsupport/LogEvents`）。

## 5. テストのデータ

- 単体テストの写し（`TargetSchema`）はテストの中で組み立てる。生成の例（functional-spec.md 3節の `dept_mst`）の写しと、その期待する本文をテストの資源に置く（`src/test/resources/cherry/mastersmith/dslmanage/generate/`）。
- 10MB を超える写しは、テストの中で多くのテーブル・カラムを組み立てて作る（大きなファイルをリポジトリに置かない）。
- 結合テストのスキーマは、U1 の結合テストと同じく、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す（team.md の Testing Posture）。
- 名前とコメントには、日本語、大文字・小文字の混在、YAML の記号（`:`・`#`・`-`・`&`・`*`・`!`・引用符）・改行・制御文字を含むものを入れる。テストの説明文（`@DisplayName`）は英語。
