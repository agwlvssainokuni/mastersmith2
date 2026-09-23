# Logical Components — U2 DSL の定義（u2-dsl-definition）

U2 の論理的な部品と、障害の範囲を示す。

## 1. 部品

| 部品 | 役割 | 置き場（パッケージ） |
|---|---|---|
| DslReader | 読み込みの口（契約 C4 の `read`・`hash`）。段を順に呼び、結果の型で返す | `cherry.mastersmith.dsl.service` |
| SafeYamlParser | SnakeYAML の安全な読み込み（上限・タグ・重複キー）で節の木を作る | `cherry.mastersmith.dsl.parse` |
| YamlTreeConverter | 節の木から検証用の JSON の形と位置の対応表を作る。展開後の節の数を数える | `cherry.mastersmith.dsl.parse` |
| DslSchemaValidator | 同梱の JSON Schema（起動時に1回読む）で検証し、誤りを文言の鍵にする | `cherry.mastersmith.dsl.validate` |
| DslSemanticValidator | 意味の検証。正規表現の確かめは上限つきの実行器で行う | `cherry.mastersmith.dsl.validate` |
| DslModel ほか | 検証を通った DSL の変更できない値の木 | `cherry.mastersmith.dsl.domain` |
| ActiveDslModelHolder | 適用中のモデルの保持（`AtomicReference`）と提供口（契約 C8） | `cherry.mastersmith.dsl.service` |
| dsl-schema-v1.json | 同梱の JSON Schema の正本。ビルドで静的なファイルにも複写する | `backend/src/main/resources/dsl/` |

`dsl` は `targetdb` と `dslmanage` に依存しない（ADR-001）。後続の Intent は `dsl` の提供口だけを使う。

## 2. 障害の範囲

- U2 は外部に接続しない（DB・ネットワークに触れない）。失敗は入力の誤り（結果の型）か、アプリの中の想定外の失敗だけ。
- 1回の読み込みで使うメモリは本文の大きさに比例する（本文・節の木・検証用の形・対応表）。重い処理の同時の数は U4 が1つに絞る（U4 の NFR1.13）。10MB の DSL のメモリは Build and Test で測る。
- 正規表現の確かめの実行器は1つで、スレッドの数に上限がある。待ちをやめた組み立ては長さの上限で短く終わるため、実行器が埋まり続けない。

## 3. 共有するもの

- Jackson 3（`JsonNode`）は Spring Boot の既存の版を使う。networknt は 3.0.6 に固定する（3.0.7 は Jackson を引き上げるため）。
- SnakeYAML は Spring Boot の推移依存と同じ 2.6 を、直接の依存として明示する。
