# Code Generation Plan — U2 DSL の定義（u2-dsl-definition）

U2 のコード生成の計画を示す。作るものは、DSL の書式（同梱の JSON Schema と書式の版）、YAML の安全な読み込みと位置の対応表、構文と意味の検証、変更できないモデル、適用中のモデルの保持と提供口（パッケージ `cherry.mastersmith.dsl`）と、JSON Schema の公開の道である。Bolt は B1（`inception/delivery-planning/bolt-plan.md`）。

## 1. 入力にした設計

| 文書 | 使うところ |
|---|---|
| `construction/u2-dsl-definition/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`functional-design-questions.md`（DSL の形（案）） | 読み込みと検証の段、誤りの種類ごとの行・列（7.1）、決まり BR1.1〜BR5.4、モデルと結果の型、YAML の形 |
| `construction/u2-dsl-definition/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md` | NFR2.1〜2.4、NFR3.1〜3.6、NFR5.1〜5.2、NFR1.4・1.5、部品の選定（SnakeYAML 2.6・networknt 3.0.6）と試しの結果 |
| `construction/u2-dsl-definition/nfr-design/security-design.md`・`logical-components.md` | 読み込みの流れ、位置の対応表（ADR-008）、JSON Schema の検証、正規表現の確かめ、識別、JSON Schema の公開、部品と置き場 |
| `construction/u2-dsl-definition/infrastructure-design/cicd-pipeline.md` | 依存、`bootWar` での複写、公開の道、開発サーバーのプロキシ |
| `inception/contract-design/contract-summary.md` の C4・C8 | `DslReader`・`DslFormat`・`ActiveDslModelHolder`・`ActiveDslModelProvider` と結果の型 |
| `inception/user-stories/stories.md` | US2.2（AC2.2.1〜AC2.2.8）、US2.3（AC2.3.1〜AC2.3.8）の U2 の部分、US4.1 のモデルと提供口の部分 |

## 2. 前提と、この計画での読み方

- **承認の場で決まったこと**: Infrastructure Design の承認の場の決定 D「JSON Schema の静的なファイルを許す設定は足さず、ログインなしで取れることを結合テストで確かめる」に従い、`SecurityConfig` は変えない（今の決まりは `/api/**` の外をログインなしで通す）。NFR 設計 6節の「このパスだけを許す」は、この決定で読み替える。NFR Requirements の承認の場の「U2 の R-05 は直さない」（`traceability.json` の記述）は、この段では扱わない。
- **大きさの上限は 10MB**: 要件（NFR2）・機能設計（BR1.1）・契約 C4 は 5MB だが、U3 の NFR 要件で 10MB（10,485,760 バイト）に上げると決め、U2 の NFR 要件（NFR2.1）もそれに合わせた。承認済みの文書は書き換えず、10MB で実装する。
- **誤りの場所の書き方**: 位置の対応表のキーは networknt と同じ JSON Pointer の形（例 `/tables/dept_mst/columns/code`、NFR 設計 2節）で持つ。利用者に返す `DslError.path` は、契約 C4・`entities.md` の例のとおり点でつないだ形（例 `tables.dept_mst.columns.code`）に直して返す。並びの位置は `menus.0.items.1` のように数で書く。
- **文言の鍵の持ち主**: U2 は文言の鍵と埋める値だけを返し、ja・en の文言は持たない（U4 の NFR 設計のとおり、U4 が鍵から表示言語の文言にする）。U2 は使う鍵の一覧を1か所（`DslMessageKeys`）にまとめ、U4 が文言を漏れなく用意できるようにする。
- **JSON Schema の複写の場所**: 基盤の設計は `bootWar` で WAR の `WEB-INF/classes/static/dsl/` へ複写するとしている。結合テスト（WAR を作らずクラスパスで起動する）でも同じ道で確かめるため、複写は `processResources` の出力（`static/dsl/`）に対して行う。WAR には `WEB-INF/classes/static/dsl/` として入り、置き場は基盤の設計と同じになる。正本は `backend/src/main/resources/dsl/dsl-schema-v1.json` の1つのまま。
- **Jackson の YAML の部品**: networknt の推移依存の `jackson-dataformat-yaml`・`snakeyaml-engine` は依存から外す。外すと networknt の検証が動かないときだけ残し、ArchUnit で `dsl` から使わないことを確かめる（基盤の設計のとおり）。
- **U1 で入れた共通の関門**: パッケージごとのカバレッジの下限は、新しいパッケージ（`cherry.mastersmith.dsl` の下）にそのまま当たる。SpotBugs の関門もそのまま。

## 3. 作るもの（パッケージ `cherry.mastersmith.dsl`）

| 層 | 部品 | 役割 |
|---|---|---|
| `domain` | `DslModel`・`DslMenuItem`・`DisplayName`・`DslTable`・`DslForeignKey`・`DslColumn`・`DbType`・`SearchSetting`・`ListSetting`・`DetailSetting`・`Validation`・`OptionSource`（と選択肢・参照ピッカーの値）、列挙（`FormPart`・`SearchOperator`・`SortDirection`・`ListFormat`・`ValidationType`・`ValidationOrigin`・`OptionSourceKind`） | 検証を通った DSL の変更できない値の木（record、`List.copyOf`・順を保つ変更できない対応表）。接続先・権限を持たない（BR5.2） |
| `domain` | `DslReadResult`（sealed interface: `Valid`・`Invalid`）、`DslError`・`DslErrorKind`、`ActiveDsl`（sealed interface: `Present`・`Absent`）、`DslFormat`（`CURRENT_VERSION = 1`）、`DslMessageKeys` | 契約 C4・C8 の型と、誤りの文言の鍵の一覧 |
| `parse` | `SafeYamlParser` | SnakeYAML の `compose` で節の木だけを作る（Java の型を作らない）。`LoaderOptions`: 入れ子の深さ 50、コレクションを指す別名 100、重複キーを許さない、タグは `TagInspector` ですべて拒否、文字数の上限は 10MB 相当。部品の例外は種類ごとに `DslErrorKind` へ写し、部品の文言は使わない（NFR5.1） |
| `parse` | `YamlTreeConverter` | 節の木から検証用の JSON の形（Jackson 3 の `JsonNode`）と位置の対応表（JSON Pointer → 行・列。キーの位置）を作る。別名は展開し、展開後の節の数が 1,000,000 を超えたら ALIAS_LIMIT。別名の中の値は参照を書いたキーの位置（BR4.2） |
| `validate` | `dsl-schema-v1.json`（`backend/src/main/resources/dsl/`） | JSON Schema 2020-12。`functional-design-questions.md` の DSL の形と `entities.md` のとおり。どの対応表も知らない項目を許さない（`additionalProperties: false`） |
| `validate` | `DslSchemaValidator` | networknt 3.0.6 の `SchemaRegistry`（2020-12、`fetchRemoteResources(false)` を明示）。同梱の JSON Schema を起動時に1回読み、組み立てた検証器を使い回す。誤りをキーワード・場所・埋める値から文言の鍵にする。埋める値は場所と、利用者が書いた値の先頭 100 文字まで。知らない項目の値は埋めない（NFR5.2） |
| `validate` | `DslSemanticValidator`・`PatternChecker` | 意味の検証（BR3.1〜BR3.7、見つかったものをすべて返す）。`pattern` は 1,000 文字まで、組み立て（`Pattern.compile`）は上限つきの小さな実行器で行い 100 ミリ秒で待つのをやめて SEMANTIC。DSL の値に当てはめない。待ちの時間はテストで差し替えられる（NFR3.6） |
| `service` | `DslReader`（契約のインターフェース）と実装 | `read(byte[])`: 大きさ（10MB）→ 読み込み → 版（BR2.1）→ 構文 → 意味 の順で、前の段に誤りがあれば止める（BR2.3）。通れば `DslModel` を作り識別を付けて `Valid`。`hash(byte[])`: 本文のバイト列の SHA-256 を 16 進数の小文字 64 文字（BR5.1） |
| `service` | `ActiveDslModelHolder`（契約 C4）・`ActiveDslModelProvider`（契約 C8） | `AtomicReference` で1つを持ち、差し替えは参照の置き換え1回（BR5.3）。無ければ `Absent`（BR5.4） |

ほかの単位（U3・U4・後続の Intent）が使うのは `service` のインターフェースと `domain` の型だけとし、`parse`・`validate` は外から使わせない。

## 4. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。

### Step 1: 骨組み

- [x] `dsl` と `domain`・`parse`・`validate`・`service` の `package-info.java`（日本語の説明、ライセンスヘッダー）を作る
- [x] 対応するストーリー: US2.2・US2.3・US4.1（モデルと提供口）

### Step 2: 依存とテストの実行の準備

- [x] `gradle/libs.versions.toml` に SnakeYAML 2.6（直接の依存として版を明示）と networknt json-schema-validator 3.0.6 を足す。`backend/build.gradle.kts` で `implementation` に足し、networknt の推移依存の `jackson-dataformat-yaml`・`snakeyaml-engine` を外す（外すと検証が動かないときは残し、Step 11 の ArchUnit で守る。どちらにしたかを `code-summary.md` に記録）
- [x] `./gradlew :backend:resolveAndLockAll --write-locks` で lockfile を更新し、依存の木で Jackson（`tools.jackson.core`）が Spring Boot の版（3.1.5）のままであること、ほかの既存の部品の版が上がらないことを確かめる
- [x] 足した部品と推移依存（`com.ethlo.time:itu` など）のライセンスが Apache 2.0 であることを確かめ、`code-summary.md` に記録する
- [x] 単位のテストのコマンド（`unit-test-instructions.md`）が動くことを確かめる
- [x] 対応するストーリー: US2.3（NFR3.3・NFR3.5）

### Step 3: ドメインの値 — 実装

- [x] 3節の `domain` の部品。record の生成時に必須の値を確かめ、並び・対応表は変更できない写しにする（対応表は DSL の順を保つ）
- [x] `DslError` は `kind`・`line`・`column`（1 から、無ければ null）・`path`（点でつないだ形、無ければ null）・`messageKey`・`messageArgs` を持つ。`DslMessageKeys` に使う鍵をすべて定数で置き、一覧を返す
- [x] 対応するストーリー: US4.1（モデルと提供口）、契約 C4・C8、BR5.2

### Step 4: ドメインの値 — テスト（単体）

- [x] 値のテスト（変更できないこと、対応表の順が保たれること、必須の値の欠けの拒否、`DslReadResult`・`ActiveDsl` の場合分け、`DslMessageKeys` の鍵が重ならず `dsl.` で始まること）
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'` を実行し、通す

### Step 5: 読み込みと位置の対応表 — 実装

- [x] `SafeYamlParser`・`YamlTreeConverter`（3節）。スカラーは YAML の暗黙の型（文字列・整数・小数・真偽値・null）で JSON の値にする
- [x] 誤りの行・列: `functional-spec.md` 7.1 の表のとおり（DEPTH_LIMIT・ALIAS_LIMIT・FORBIDDEN_TAG は部品が示す位置、DUPLICATE_KEY は2回目のキーの位置と場所、YAML として読めないときは読めなかった位置）
- [x] 対応するストーリー: US2.3（AC2.3.2〜AC2.3.5）、US2.2（AC2.2.1・AC2.2.3 の位置）、BR1.2〜BR1.5・BR4.1・BR4.2、NFR2.2〜NFR2.4・NFR3.1・NFR3.2・NFR3.5・NFR5.1

### Step 6: 読み込みと位置の対応表 — テスト（単体）

- [x] 上限の境界: 深さ 50 は通り 51 は DEPTH_LIMIT、別名 100 は通り 101 は ALIAS_LIMIT、別名の入れ子で展開が爆発する DSL は展開後の節の上限で ALIAS_LIMIT になり 1 秒以内に終わる
- [x] タグ: 任意の型を作るタグ（テスト用の、作られたら記録するクラスを指すもの）と独自のタグが FORBIDDEN_TAG になり、記録は 0 件（AC2.3.4）
- [x] 重複キー: 2回目のキーの行・列つきの DUPLICATE_KEY で、後の値で上書きされない（AC2.2.3・AC2.3.5）。同じテーブル・同じカラムの二重の定義も同じ
- [x] 位置の対応表: 必須の欠け・知らない項目・型の違いのそれぞれで引く位置、別名の参照の位置（BR4.2）、並びの中の位置
- [x] YAML として読めない本文（字下げの誤りなど）が、読めなかった位置つきの SYNTAX になる。誤りの `messageArgs` に部品の例外の文言（例外のクラス名・部品の説明文）が入らない（NFR5.1）
- [x] jqwik の性質ベースのテスト: 任意のバイト列・任意の文字列を読ませても、想定外の例外が漏れず、結果（誤りの一覧か節の木）が返る
- [x] 単位の単体のコマンドを実行し、通す

### Step 7: 検証 — 実装

- [x] `dsl-schema-v1.json`（3節）。`version` は `const: 1` ではなく整数とし、版の検査（BR2.1）は構文の検証の前に `DslReader` の中で行う
- [x] `DslSchemaValidator`（3節）。networknt の誤りのキーワード（`required`・`additionalProperties`・`type`・`enum`・`minimum` など）ごとに文言の鍵を決め、見つかったものをすべて返す（BR2.2）。`$ref` を含む DSL は知らない項目として SYNTAX（BR1.6）
- [x] `DslSemanticValidator`・`PatternChecker`（3節、BR3.1〜BR3.7）。誤りの場所から同じ対応表で行・列を引く
- [x] 対応するストーリー: US2.2（AC2.2.1〜AC2.2.7）、US2.3（AC2.3.6〜AC2.3.8）、BR1.6・BR2.1〜BR2.3・BR3.1〜BR3.7・BR4.3、NFR3.3・NFR3.6・NFR5.1・NFR5.2

### Step 8: 検証 — テスト（単体）

- [x] 構文: 必須の欠けと型の違いの2件が、それぞれ行・列と場所つきで返る（AC2.2.1）。接続先の項目（例 `url`・`username`・`password`）を含む DSL が知らない項目の SYNTAX になり、その値が `messageArgs` に入らない（AC2.2.6、NFR5.2）。利用者の値は先頭 100 文字まで
- [x] 外部の `$ref`: テストの中で立てた要求を数えるだけの HTTP の受け口に、DSL の `$ref`（AC2.3.6）でも、外部を指す `$ref` を含むスキーマ（AC2.3.7）でも要求が0件で、後者は取りに行かずに失敗する
- [x] 意味: 無いテーブルを指すメニュー（AC2.2.2）、テーブルも子も無いメニューの項目、主キー・外部キー（自分のテーブルのカラムと参照先のカラム）・REFERENCE・LOOKUP・参照ピッカーの検索と一覧の参照の先が無い（AC2.2.4）、並び順の重なり、部品と選択肢の出どころの不一致、`min > max`・`minLength > maxLength`、正しくない `pattern`、1,000 文字は通り 1,001 文字は SEMANTIC、組み立ての待ちの打ち切り（待ちの時間を差し替えて確かめる）、複数の意味の誤りがすべて返ること
- [x] 版: 版が無い・2 のときは UNSUPPORTED_VERSION だけで、構文と意味の検証をしない（AC2.2.5、BR2.3）。本文が空のときも UNSUPPORTED_VERSION の1件
- [x] 段の順: 構文と意味の誤りを両方含む DSL は構文の誤りだけを返す（AC2.2.7）
- [x] 誤りの一覧に YAML・JSON Schema の部品の例外の文言が含まれない（AC2.2.8・AC2.3.8）
- [x] 単位の単体のコマンドを実行し、通す

### Step 9: 読み込みの口と適用中のモデル — 実装

- [x] `DslReader` と実装（3節）。10MB（10,485,760 バイト）を超えたら読まずに SIZE_LIMIT（行・列・場所なし）
- [x] 検証を通った JSON の形から `DslModel` を作る（Step 3 の値）
- [x] `ActiveDslModelHolder`・`ActiveDslModelProvider`（3節）。`replace(null)` は「無い」
- [x] 想定外の失敗（プログラムの誤り）だけを例外にする
- [x] 対応するストーリー: US2.3（AC2.3.1）、US4.1（モデルと提供口）、BR1.1・BR5.1〜BR5.4、NFR2.1

### Step 10: 読み込みの口と適用中のモデル — テスト

- [x] 単体: 見本の DSL（`functional-design-questions.md` の DSL の形の例を元にしたもの、メニューの N 階層・FIXED・REFERENCE・LOOKUP を含む）が `Valid` になり、モデルの中身が DSL と同じ。ちょうど 10MB は大きさでは止めず、1 バイト超えは SIZE_LIMIT（AC2.3.1、NFR2.1）。識別が同じバイト列で同じ・1 バイト違えば別で 64 文字の小文字 16 進数（BR5.1）。段の順（BR2.3）
- [x] 単体: 保持の差し替え（無い → M1 → M2 → 無い）、取り出した後の差し替えの影響を受けないこと、複数のスレッドで読みながら差し替えても前か後のモデルのどちらかだけを得ること（BR5.3・BR5.4）
- [x] 単位の単体のコマンドを実行し、通す

### Step 11: 構造の検査

- [x] `src/test/java/cherry/mastersmith/dsl/DslBoundaryArchitectureTest.java`
  - `dsl` は `targetdb`・`dslmanage`・`web` 層・`repository` 層に依存しない（ADR-001・ADR-009）
  - `dsl.parse`・`dsl.validate` は `dsl` の外から使われない
  - `dsl` は Jackson の YAML の読み込み（`tools.jackson.dataformat.yaml`・`com.fasterxml.jackson.dataformat.yaml`）と `snakeyaml-engine`（`org.snakeyaml.engine`）を使わない
  - `dsl` は SnakeYAML の型を作る仕組み（`org.yaml.snakeyaml.constructor` の下と `Yaml.load` 系）を使わない（NFR3.5）
- [x] 単位の単体のコマンドを実行し、通す

### Step 12: JSON Schema の公開の道

- [x] `backend/build.gradle.kts` の `processResources` で、正本 `dsl/dsl-schema-v1.json` を `static/dsl/dsl-schema-v1.json` へ複写する（2節）。U1 の変更と重ならない場所に足す
- [x] 既存の画面の配信（`config/WebConfig.java`。見つからない画面の URL に `index.html` を返す）が `/dsl/dsl-schema-v1.json` をファイルとして返すことを確かめ、返さなければ配信の決まりに `/dsl/` の下のファイルを足す。`SecurityConfig` は変えない（決定 D）
- [x] `frontend/vite.config.ts` の開発サーバーのプロキシに `/dsl` を足す
- [x] 結合テスト `DslSchemaPublicationIT`: ログインなしで `GET /dsl/dsl-schema-v1.json` が 200 で JSON を返し、中身がクラスパスの正本と同じ。`X-Content-Type-Options: nosniff` が付く
- [x] WAR の中の複写と正本が同じであることを、`./gradlew verify` の成果物の段（`verifyArtifact`）で確かめる検査を足す
- [x] 対応するストーリー: US2.2（DSL の作者が書式を知る道）、決定 D

### Step 13: 1コマンドの検査と文書

- [x] `README.md` に書く: JSON Schema の URL（`/dsl/dsl-schema-v1.json`）と、エディタ（YAML の言語サーバーの `# yaml-language-server: $schema=...` など）での使い方、DSL の読み込みの上限（10MB・深さ 50・別名 100・展開後の節 1,000,000・`pattern` 1,000 文字）、ライセンスの節に SnakeYAML・networknt
- [x] `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` をコンテナの実行環境が動いている状態で実行し、すべての段が通ることを確かめる

### Step 14: 記録

- [x] `code-summary.md`、`source-manifest.json`、`traceability.json` を作る（コード生成の段の手順 5）。`code-summary.md` には、承認済みの文書との差（大きさ 10MB、`path` の書き方、JSON Schema の複写の場所、決定 D）、使う文言の鍵の一覧の場所、U3・U4 への申し送りを載せる

## 5. ストーリーと手順の対応

| ストーリー | 受け入れ基準・要件 | 手順 |
|---|---|---|
| US2.2 | AC2.2.1〜AC2.2.8、BR2.1〜BR2.3・BR3.1〜BR3.7・BR4.1〜BR4.3、NFR5.1・NFR5.2 | Step 3〜8・12 |
| US2.3 | AC2.3.1〜AC2.3.8、BR1.1〜BR1.6、NFR2.1〜NFR2.4・NFR3.1〜NFR3.6 | Step 2・5〜11 |
| US4.1（モデルと提供口の部分） | BR5.1〜BR5.4、契約 C4・C8 | Step 3・4・9・10 |
| 共通の完了の条件（B1） | カバレッジ（全体とパッケージごと）、ライセンス、SpotBugs | Step 2・13 |

AC2.2.9（画面）は U5、AC2.2.10・AC2.3.9・AC2.3.10（監査・投入以外の API の上限）は U4 が受け持つ。NFR1.4・NFR1.5（時間）と 10MB の DSL のメモリ、正規表現の組み立ての時間の前提は Build and Test で測る（NFR 設計 4節）。

## 6. テストの量（Standard）

| 部品 | 単体 | 結合 |
|---|---|---|
| ドメインの値 | 5〜8 件 | — |
| 読み込みと位置の対応表（`SafeYamlParser`・`YamlTreeConverter`） | 10〜14 件（上限の境界・タグ・重複キー・位置・性質ベース） | — |
| 構文の検証（`DslSchemaValidator`） | 6〜8 件 | — |
| 意味の検証（`DslSemanticValidator`・`PatternChecker`） | 8〜12 件 | — |
| 読み込みの口と適用中のモデル | 6〜8 件 | — |
| 構造の検査 | 4 件 | — |
| JSON Schema の公開 | — | `DslSchemaPublicationIT`（2〜3 件） |

上限の境界・タグ・重複キー・`$ref`・拒否の文言は、team.md の「投入 DSL の必ず書くテスト」に当たるため、件数の目安を超えても省かない。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストをどこで実行するかは、Build and Test で `./gradlew verify` の時間と colima の VM のメモリを実測してから決める。決まるまでは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:2adf2e19e08e8d9f09279453393e762919beea4d6d9d5d13b89e72c1fffeed06",
  "contract_sha256": "sha256:e0a9abee7ec1245e67d5b55b468219a36caaa126e29794fd62a14795c277380c"
}
```


Testing Contract の `plan_profile.steps` との対応: 骨組みは Step 1、テストの実行の準備は Step 2、ドメインの値（データの形）は Step 3・4、読み込みと位置の対応表・検証（入力の変換と検証）は Step 5〜8、業務処理（読み込みの口と適用中のモデル）は Step 9・10、環境とビルドの設定（公開の道・検査）は Step 11〜13、記録は Step 14。U2 は library の単位で DB・API・画面の層を持たない（JSON Schema の公開は静的なファイルで、API を作らない）。
