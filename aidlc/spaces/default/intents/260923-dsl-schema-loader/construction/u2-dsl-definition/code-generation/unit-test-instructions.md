# Unit Test Instructions — U2 DSL の定義（u2-dsl-definition）

U2 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の結合テスト）に、team.md の「投入 DSL の必ず書くテスト」を加える。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ | `backend/build.gradle.kts` の `tasks.test`（`*Test`）と `integrationTest`（`*IT`） |
| 性質ベースのテスト | jqwik 1.10.1（既存） | `backend/src/test/resources/junit-platform.properties`。失敗時の乱数の種はテストの出力に残る |
| 構造の検査 | ArchUnit 1.5.0（既存） | `backend/src/test/resources/archunit.properties` |
| 外部の `$ref` の確かめ | JDK の `com.sun.net.httpserver.HttpServer`（テストの中で `127.0.0.1` の空いたポートに立て、要求の数を数える） | テストのコードの中だけ。新しい依存は足さない |
| カバレッジ | JaCoCo 0.8.15（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification` |

U2 は DB とコンテナを使わない。新しいテストの設定のファイルは足さない。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U2 のパッケージ `cherry.mastersmith.dsl` だけに絞る。

単体テスト（`*Test`。値・読み込み・検証・読み込みの口・構造の検査）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'
```

結合テスト（`*IT`。JSON Schema の公開の道。組み込みの H2 でアプリを起動する）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dsl.*'
```

- 最初のテスト（計画の Step 4）より前は、対象のテストが無いため Gradle が「一致するテストが無い」で失敗する。これは想定どおりで、Step 4 からコマンドが通る。結合テストのコマンドは Step 12 から通る。
- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest` または `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（project.md の Testing Posture）。

## 3. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上（既存の `jacocoTestCoverageVerification`）。
- パッケージごと: `cherry.mastersmith.dsl` の下の各パッケージ（`domain`・`parse`・`validate`・`service`）で行 80%・分岐 70% 以上（U1 で入れた、新しいパッケージに当てる決まり）。
- 計測から外すのは既存の除外（起動クラスと `*Properties`）だけ。U2 のために除外を足さない。
- 単位だけのカバレッジは、単位のテストを実行した後に `./gradlew :backend:jacocoTestReport` の報告（`backend/build/reports/jacoco/test/html/cherry.mastersmith.dsl*/`）で見る。下限の判定は全体の `./gradlew verify` の 7 の段で行う。

## 4. 差し替え（モック・スタブ）の方針

- YAML の読み込み（SnakeYAML）と JSON Schema の検証（networknt）はモックにせず、本物の部品で確かめる（上限・タグ・重複キー・位置は部品のふるまいそのものを確かめるため）。
- 正規表現の組み立ての待ちの打ち切り（100 ミリ秒）は、`PatternChecker` の待ちの時間と組み立ての処理を差し替えられるようにし、終わらない組み立てを差し込んで打ち切りの道を確かめる。`Thread.sleep` や実時刻の長い待ちに依存しない。
- 外部の `$ref` は、テストの中で立てた要求を数えるだけの HTTP の受け口で確かめる。外の端末に接続しない。
- タグで型が作られないことは、テスト用の「作られたら記録するクラス」をタグで指し、記録が 0 件であることで確かめる。
- 適用中のモデルの差し替えが一度に切り替わることは、複数のスレッド（`ExecutorService`）で読みながら差し替え、読んだモデルが前か後のどちらかと同じであることで確かめる。待ち合わせは `CountDownLatch` などで行い、実時刻に依存しない。

## 5. テストのデータ

- DSL の見本は `src/test/resources/cherry/mastersmith/dsl/` に YAML のファイルとして置く（正しい見本1つと、誤りの種類ごとの見本）。上限の境界（10MB ちょうどと 1 バイト超え、深さ 50 と 51、別名 100 と 101、`pattern` 1,000 文字と 1,001 文字）と別名の展開の爆発は、テストの中で組み立てる（大きなファイルをリポジトリに置かない）。
- 正しい見本は、メニューの N 階層、FIXED・REFERENCE・LOOKUP の選択肢、外部キー、ビュー、日本語の表示名を含む。
- 接続先の項目（`url`・`username`・`password`）を含む見本の値は、明らかに見本と分かる文字列にし、本物の接続先・資格情報を書かない。
- テストの説明文（`@DisplayName`）は英語。テストデータは日本語でよい。
