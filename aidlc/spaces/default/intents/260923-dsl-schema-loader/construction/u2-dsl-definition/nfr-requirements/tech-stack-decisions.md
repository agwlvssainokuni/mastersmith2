# Tech Stack Decisions — U2 DSL の定義（u2-dsl-definition）

U2 で足す技術と、その数値・選定の理由を示す。NFR1 の枝番は、単位の間で重ならないよう通しで振る（U1 は NFR1.1〜1.3、U2 は 1.4〜1.5、U3 は 1.6〜1.7、U4 は 1.8〜1.17、U5 は 1.18〜1.21）。既存の構成は `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`。答えは `nfr-requirements-questions.md`（Q1〜Q6、F1）。細かい版はコード生成で lockfile に固定する。

## 1. 選定

| 対象 | 選定 | ライセンス | 理由 |
|---|---|---|---|
| YAML の読み込み | SnakeYAML 2.6（`org.yaml:snakeyaml`。今は Spring Boot の推移依存。直接の依存にして版を明示する） | Apache 2.0 | 深さ・別名・重複キー・タグの制限の設定と、値ごとの行・列を持つ。試しで上限と拒否を確かめた |
| JSON Schema の検証 | networknt json-schema-validator 3.0.6（`com.networknt:json-schema-validator`） | Apache 2.0（推移依存の `com.ethlo.time:itu` も Apache 2.0） | Jackson 3 の `JsonNode` で検証し、誤りのパス（JSON Pointer の形）を返す。3.0.6 は Jackson 3.1 系で動き、Spring Boot の Jackson（3.1.5）を引き上げない |
| JSON Schema の版 | 2020-12 | — | Q4: A |
| 検証用の JSON の形と位置の対応表 | SnakeYAML の節の木から自分で作る（`dsl` の中の小さな変換） | — | Jackson の YAML の読み込みが別名を展開しないため。試しで行・列が正しく引けた |
| DSL の識別 | SHA-256（本文のバイト列）、16 進数の小文字 64 文字 | — | BR5.1。標準の Java（`MessageDigest`）で求める。ファイル名には先頭 12 文字を使う（U4 の BR6.2） |

### 1.1 使わないもの

- **networknt 3.0.7**: Jackson 3.2.1 を求め、アプリ全体の Jackson を Spring Boot の版から引き上げる。Spring Boot を上げるときに、networknt の版もあわせて見直す（project.md の Tech Stack の opentelemetry-logback-appender と同じ扱い）
- **Jackson の YAML の読み込み**（`jackson-dataformat-yaml`）: 別名を文字列にしてしまい、重複キーを黙って上書きする（試しで確かめた）。networknt の推移依存として入る場合は、依存から外すか、`dsl` から使わないことを ArchUnit で確かめる（コード生成で決める）
- **json-sKema**: networknt で条件を満たせたため試さない（MIT と Kotlin の標準ライブラリが増えるのを避けられる）

## 2. 数値

| ID | 項目 | 値 | 理由 |
|---|---|---|---|
| NFR1.4 | U2 の読み込み・検証・モデル作りの時間 | 想定の規模（100 テーブル × 100 カラム、既定の DSL の書き方で約 5.3MB）で 3 秒以内 | Q2: A。照合（U4）と保存に 10 秒の残りを使う。試しでは 92〜210 ミリ秒。Build and Test で測る |
| NFR1.5 | 10MB（上限）の DSL の読み込みと検証の時間 | 10 秒以内（NFR1 の上限と同じ）で、誤りの有無にかかわらず終わる | 上限の大きさでも処理が止まらないこと。Build and Test で測る |
| NFR2.1〜NFR2.4 | 本文 10MB・深さ 50・別名 100・展開後の節 1,000,000 | `security-requirements.md` の2節 | Q1: A と要点の確認。本文と展開後の節の上限は、U3 の NFR 要件（Q1: B）で 5MB・500,000 から上げた |

## 3. 位置の対応表（ADR-008）の試しの結果

Q6: A のとおり、使い捨ての作業ブランチで確かめ、試しのコードとブランチは捨てた（2026-09-23）。試しのテストは 12 件、すべて通った。

| 確かめたこと | 結果 |
|---|---|
| networknt の版と Jackson | 3.0.7 は Jackson を 3.2.1 に引き上げる。3.0.6 は 3.1.5 のまま |
| Jackson の YAML の読み込み | 別名 `*b` が文字列 `"b"` になり展開されない。重複キーは後の値で黙って上書き |
| SnakeYAML の深さ 50 | 50 は通り、51 は `Nesting Depth exceeded max 50` |
| SnakeYAML の別名 100 | 101 は `Number of aliases for non-scalar nodes exceeds the specified max=100` |
| 重複キー | 1回目と2回目の行・列つきで拒否 |
| タグ | `!!javax.script.ScriptEngineManager` などの型の指定も、独自の `!custom` も拒否 |
| 位置の対応表 | 必須の欠け（対象の対応表のキーの位置）、知らない項目（その項目のキーの位置）、型の違い（その値のキーの位置）を正しく引けた |
| 別名の参照の位置 | 別名で参照した値の中の誤りは、参照を書いたキーの位置（BR4.2 のとおり） |
| 展開の爆発 | 展開後の節の上限（試しでは 200,000）で、10 段の入れ子の別名を約 45 ミリ秒で打ち切り |
| 速さ | 100 テーブル × 100 カラム（約 0.68MB、節 50,503）で読み込み・変換・検証 92〜210 ミリ秒 |
| 外部の `$ref` | 試しの HTTP の受け手に要求が来なかった（既定で取りに行かない）。念のため取りに行かない設定を明示する |

判断: ADR-008 の条件は満たされた。切り替え先（パスだけを示す）は使わない。本物は B1 で作る。

## 4. JSON Schema の配り方（要件の OQ6）

- Q5 の答え「static コンテンツとして同梱」と F1: A により、検証に使う同梱の正本（`backend/src/main/resources/` の下）を、ビルドのときにアプリの静的なファイルへ複写し、ログインなしで取れる URL（例 `/dsl/dsl-schema-v1.json`）で公開する。正本は1つで、複写はビルドが行う（手で2か所を直さない）。
- DSL の管理画面にダウンロードのリンクを置き、README に URL とエディタ（YAML の言語サーバーなど）での使い方を書く。
- JSON Schema に秘密は含まれない。公開の範囲は、既存のセキュリティの決まり（`SecurityConfig`）で、このパスだけをログインなしで許す形にする（コード生成で決める）。
- **承認済みの設計との差**: U5 の機能設計（`construction/u5-dsl-admin-ui/functional-design/`）に、このリンクは無い。設計の文書は書き換えず、差を U5 の NFR 要件とコード生成で扱う（project.md の Way of Working）。
