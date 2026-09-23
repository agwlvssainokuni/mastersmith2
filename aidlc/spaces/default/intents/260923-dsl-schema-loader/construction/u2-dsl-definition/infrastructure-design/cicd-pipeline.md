# CI/CD Pipeline — U2 DSL の定義（u2-dsl-definition）

U2 の依存・ビルド・JSON Schema の公開の道を示す。U2 は外部に接続しない library で、配備先の基盤は増やさない。要点の確認は `infrastructure-design-questions.md`。

## 1. 依存（ビルド）

| 足すもの | 版 | ライセンス | 扱い |
|---|---|---|---|
| SnakeYAML（`org.yaml:snakeyaml`） | 2.6（今は Spring Boot の推移依存。直接の依存にして明示） | Apache 2.0 | 読み込みと書き出し（U2・U3） |
| networknt json-schema-validator（`com.networknt:json-schema-validator`） | 3.0.6 | Apache 2.0 | JSON Schema の検証。3.0.7 は Jackson を 3.2 に引き上げるため使わない |
| （推移依存）`com.ethlo.time:itu` | networknt の求める版 | Apache 2.0 | そのまま |
| （推移依存）`jackson-dataformat-yaml`・`snakeyaml-engine` | — | Apache 2.0 | 使わないため依存から外す。外せなければ ArchUnit で `dsl` から使わないことを確かめる |

- `gradle/libs.versions.toml` に版を置き、`backend/gradle.lockfile` を更新する（既存の `resolveAndLockAll`）。
- 依存の木で、Jackson（`tools.jackson.core`）が Spring Boot の版（3.1.5）のままであることを確かめる（`project.md` の Corrections の学び）。Spring Boot を上げるときは networknt の版もあわせて見直す。

## 2. JSON Schema の公開（U2 の NFR 設計 6節）

| 項目 | 作り |
|---|---|
| 正本 | `backend/src/main/resources/dsl/dsl-schema-v1.json` の1つ（検証もこれを読む） |
| 同梱 | `bootWar` で WAR の静的なファイルの置き場 `WEB-INF/classes/static/dsl/` へ複写する（既存の画面の `dist` の同梱と同じ仕組みに足す）。画面の側で `/dsl/` の下にファイルを置かない（名前が重ならないように） |
| 公開の道 | 今の `SecurityConfig` は `/api/**` の外を `anyRequest().permitAll()` でログインなしで通すため、許す設定は足さない（NFR 設計の 6節の「このパスだけを許す」は、今の決まりのまま満たされると読み替える）。本文が無いので、本文の上限の Filter の影響は受けない |
| 開発サーバー | Vite のプロキシに `/dsl` を足し、バックエンドから取る（今は `/api`・`/actuator` だけ） |
| 確かめ | 結合テストで、ログインなしで取れること、WAR の中の複写と正本が同じ内容であること（ビルドのテスト） |

## 3. 検査の流れ

- 既存の `./gradlew verify`（手元と CI）のまま。U2 の単体テスト（上限の境界・タグ・重複キー・外部の `$ref`・位置の対応表・正規表現の打ち切り）は `test` の段、公開の道の確かめは `integrationTest` の段で動く。
- OSV-Scanner（既存）で足した依存の脆弱性を、Gitleaks（既存）で秘密情報を確かめる。
- 正規表現の打ち切りの前提の実測と、10MB の DSL のメモリ・時間の測定は Build and Test で行う（U2 の NFR 設計）。

## 4. 共有するファイルへの変更

`backend/build.gradle.kts`（`bootWar` の複写）と `frontend/vite.config.ts`（プロキシ）は、ほかの単位と共有するファイル。変更はこの文書の2節のとおりとし、ほかの単位の変更（U1 の依存、U4 の Filter の置き場）と重ならない場所に足す。
