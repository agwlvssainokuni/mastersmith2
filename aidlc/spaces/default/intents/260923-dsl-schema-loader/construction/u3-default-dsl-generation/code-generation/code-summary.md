# Code Summary — U3 既定の DSL の生成（u3-default-dsl-generation）

承認済みの `code-generation-plan.md` の Step 1〜Step 11 を、test-after（層ごとに実装 → その層のテストを書いて実行 → 通ってから次の層）で行った結果を記録する。Bolt は B3。依頼者の回答は `code-generation-questions.md` の Q1: A（`COLUMN_TYPE` を U1 の写しに足す）。

## 1. 作った・変えたファイル

一覧の正本は `source-manifest.json`（28 件）。

### 1.1 U3（新規）

| 種類 | ファイル | 内容 |
|---|---|---|
| 本番 | `backend/src/main/java/cherry/mastersmith/dslmanage/package-info.java`・`dslmanage/generate/package-info.java` | パッケージの説明と、使ってよい U1・U2 の層 |
| 本番 | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/TypeCategory.java`・`TypeCategoryMapping.java` | 型の分類（8 つ）と、型の名前から分類・分類から初期値の表（BR2.2〜BR2.5） |
| 本番 | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java` | 写しから DSL の値の木を組み立てる（BR1.2〜BR1.5・BR2.1・BR3.1・BR3.2・BR4.1） |
| 本番 | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslYamlWriter.java` | 固定の `DumperOptions` で YAML に書き出し、先頭に固定のコメント1行を付ける。コメントの制御文字の除去（BR5.1、NFR4.7・NFR4.8） |
| 本番 | `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DefaultDslGenerator.java`・`DefaultDslResult.java`・`TargetSchemaDslGenerator.java` | 契約 C5 のインターフェースと結果の型、実装（BR1.1・BR5.2、NFR2.5・NFR1.7） |
| テスト | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/` の 10 ファイル | 単体 `TypeCategoryMappingTest`・`DslTreeBuilderTest`・`DslYamlWriterTest`・`DslGenerationPropertyTest`（jqwik）・`TargetSchemaDslGeneratorTest`、結合 `AbstractDefaultDslGeneratorIT` と `DefaultDslGeneratorMysqlIT`・`DefaultDslGeneratorMariadbIT`・`DefaultDslGeneratorPostgresIT`、補助 `GenerateTestSupport` |
| テスト | `backend/src/test/java/cherry/mastersmith/dslmanage/DslManageGenerateBoundaryArchitectureTest.java` | 構造の検査（3 件） |
| テスト資源 | `backend/src/test/resources/cherry/mastersmith/dslmanage/generate/dept_mst.yaml` | 生成の例（functional-spec.md 3節）から期待する本文 |
| 文書 | `README.md` | 「既定の DSL の生成の決まり」の節（表示名・並び・型の分類とフォーム部品の表・`tinyint(1)`・`bit(1)`・10MB を超えるときの運用） |

### 1.2 U1 の変更（Q1: A）

| ファイル | 変更 |
|---|---|
| `backend/src/main/java/cherry/mastersmith/targetdb/domain/TargetDbType.java` | 任意の項目 `columnType`（String）を足した。空・空白だけは null に揃える。今までの4つの引数の作り方も残した（`columnType` は null） |
| `backend/src/main/java/cherry/mastersmith/targetdb/repository/MysqlSchemaQueries.java` | カラムの問い合わせに `COLUMN_TYPE` を足した（テキストブロックの固定の文字列のまま） |
| `backend/src/main/java/cherry/mastersmith/targetdb/repository/PostgresSchemaQueries.java` | カラムの問い合わせの最後に `CAST(NULL AS varchar)` を足した（PostgreSQL は null） |
| `backend/src/main/java/cherry/mastersmith/targetdb/repository/SchemaRows.java` | 11 番目の項目を `columnType` として読む（計画の Step 2 に名前の無いファイル。5節） |
| `backend/src/test/java/cherry/mastersmith/targetdb/domain/TargetSchemaTest.java` | `columnType` の単体テストを1件足した |
| `backend/src/test/java/cherry/mastersmith/targetdb/repository/AbstractSchemaQueriesIT.java` | 3種類の DB で `columnType` を確かめる結合テストを1件足した（MySQL・MariaDB は `tinyint(1)`・`bit(1)` の精度 1・`varchar(40)`、PostgreSQL は null） |

U1 の決まり（接続先を持たない、読み取り専用、問い合わせは4回でテーブルの数に比例しない、SQL は固定の文字列、スキーマ名は `PreparedStatement` の値）は変えていない。U2 のコード（`cherry.mastersmith.dsl`）は変えていない。

## 2. 主な判断

| 判断 | 理由 |
|---|---|
| 書き出しは `Yaml` を使わず、SnakeYAML の `Representer`・`Serializer`・`Emitter` を直接組み合わせた | `Yaml.dump` と同じ組み合わせだが、型を作る仕組み（`constructor`）を持つ `Yaml` を U3 に入れないため。構造の検査で `org.yaml.snakeyaml.Yaml` と `constructor` を使わないことを守る |
| 改行の文字（LF・CR・U+0085・U+2028・U+2029）を含む文字列だけを二重引用符の形で書く | 性質ベースのテストで、CR を含む名前が LF に変わって読み直された（SnakeYAML が一重引用符の形を選び、YAML の読み込みが改行を LF にそろえるため）。二重引用符ではエスケープ（`\r` など）で書くため値が保たれる |
| `DumperOptions` は ブロックの形・字下げ 2（並びの記号も字下げ）・幅は無制限・行を分けない・LF・別名を作らない（`setDereferenceAliases`）・Unicode のまま・表せない文字はエスケープ（`NonPrintableStyle.ESCAPE`）・単純なキーの長さ 1024 に固定 | NFR 設計 2節。表せない文字の既定（`BINARY`）は `!!binary` のタグになり U2 に拒否されるため、エスケープにした |
| コメントの制御文字の「改行」は LF とし、CR は取り除く | NFR4.8 は「改行・タブを除く C0・C1」。CR は取り除くと CRLF のコメントが LF にそろう。U+007F も取り除く（NFR 設計 3節） |
| `tinyint(1)` は `columnType` が `tinyint(1)`（大文字・小文字を問わず、後ろに `unsigned` などの語が続くものを含む）かつ型の名前が `tinyint` のとき、`bit(1)` は型の名前 `bit` かつ精度 1 のとき BOOLEAN | Q1: A。PostgreSQL の `bit(1)` は精度ではなく長さで返るため対応外になる（BR2.2 は MySQL・MariaDB の `bit(1)` だけを挙げている） |
| `tinytext` は長さ 255 でも LONG_TEXT | BR2.2 の「長い文字列の型（text 系）」に当たると読んだ |
| 長さの無い `varchar`（PostgreSQL）は LONG_TEXT | 長さに上限が無いため「256 以上」と同じ扱い |
| 外部キーは、参照先のテーブルとカラムが写しにあるものだけを写す | 読める権限が無いなどで参照先が写しに無い外部キーを写すと、U2 の意味の検証を通らず、生成が想定外の失敗になるため |
| 1つのカラムが複数の1カラムの外部キーを持つときは、写しの順で最初のもの | 決まりが無いため、同じ写しから同じ本文になる順（BR5.1）にした |
| 主キーを兼ねる1カラムの外部キーは、`select` で検索は `CHOICE` | BR2.3 は外部キーを `select` にし、BR2.4 は `select` を `CHOICE` とするため、フォーム部品に合わせた（主キーの `EQUALS` より優先） |
| 既定の並べ替えは、主キーの最初のカラムを一覧に出すときだけ `ASC` | 一覧に出さない・並べ替えできないカラム（長い文字列・対応外）に既定の並べ替えを付けないため |
| 大きさの上限を超えたときは例外だけでログを出さない。U2 の検証を通らないときは WARN（誤りの種類と件数だけ）を出して例外 | 計画の Step 7 のとおり。例外のログは応答に変える境界で1回（team.md の Code Style） |
| 想定外の失敗は `IllegalStateException`（文言は固定で、本文・名前・接続先を含めない） | 契約 C5 に例外の型の定めが無く、U4 の共通の変換で 5xx になる |
| 内訳の時間は `System.nanoTime` で測り、DEBUG のログに `tables`・`bytes`・`readMillis`・`buildMillis`・`writeMillis`・`validateMillis` のキーと値で出す | 業務の判定に使わない測定のため、時計（`Clock`）は注入していない。テストは値を確かめない（unit-test-instructions.md 4節） |
| `DefaultDslResult.Generated` は本文の配列を写して持ち、`toString` に本文を出さない | 本文が外から変えられないようにし、ログに本文が出ないようにする（NFR4.9） |
| 結合テストの読み取りは、U1 の `SchemaQueries` を読み取りの権限だけのアカウントで呼ぶ小さな読み取りの口にした | アプリを起動せずに本物の3種類の DB を読み、生成 → U2 の検証までを通すため。U1 の読み取りの口（`JdbcTargetSchemaReader`）自体は U1 の結合テストが確かめている |

## 3. 3種類の DB の型の分類の表

型の名前は U1 の写しが返すもの（MySQL・MariaDB は `DATA_TYPE`、PostgreSQL は `udt_name`）。大文字・小文字を区別しない。

| 分類 | MySQL・MariaDB | PostgreSQL | フォーム部品 | 検索 | 一覧 | 詳細 | 書式 |
|---|---|---|---|---|---|---|---|
| SHORT_TEXT | `char`・`varchar`（長さ 255 以下） | `bpchar`・`varchar`（長さ 255 以下） | text | CONTAINS | 表示・並べ替え可 | 表示 | 無し |
| LONG_TEXT | `char`・`varchar`（256 以上）、`tinytext`・`text`・`mediumtext`・`longtext` | `varchar`（256 以上・長さ無し）、`text` | textarea | CONTAINS | 表示しない | 表示 | 無し |
| NUMBER | `tinyint`（`tinyint(1)` 以外）・`smallint`・`mediumint`・`int`・`bigint`・`decimal`・`numeric`・`float`・`double` | `int2`・`int4`・`int8`・`numeric`・`float4`・`float8` | number | RANGE | 表示・並べ替え可 | 表示 | NUMBER_GROUPED |
| BOOLEAN | `tinyint(1)`（`columnType` で判定）・`bit(1)`（精度 1）・`boolean`・`bool` | `bool` | checkbox | CHOICE | 表示・並べ替え可 | 表示 | BOOLEAN_YES_NO |
| DATE | `date` | `date` | date | RANGE | 表示・並べ替え可 | 表示 | DATE |
| DATETIME | `datetime`・`timestamp` | `timestamp`・`timestamptz` | datetime | RANGE | 表示・並べ替え可 | 表示 | DATETIME |
| TIME | `time` | `time`・`timetz` | text | EQUALS | 表示・並べ替え可 | 表示 | TIME |
| UNSUPPORTED | `bit`（精度 2 以上）・`json`・`enum`・`set`・`year` ほか | `bit`・`uuid`・`bytea`・`jsonb` ほか | text | 検索しない | 表示しない | 表示しない | 無し |

表の中の型は `TypeCategoryMappingTest`（41 件の型の組み合わせ）で確かめ、3種類の DB の実際の型の名前は結合テスト（`AbstractDefaultDslGeneratorIT` と U1 の `AbstractSchemaQueriesIT`）で確かめた。

MySQL 8.4 は `tinyint(1) unsigned` の表示の幅を落として `COLUMN_TYPE` を `tinyint unsigned` と返すことが、結合テストで分かった（MariaDB は `tinyint(1) unsigned` のまま）。そのため MySQL の符号なしの `tinyint(1)` は NUMBER になる。README に書いた。

## 4. テストの件数とカバレッジ（実測）

`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を README のとおり設定し、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を colima が動いている状態で実行した（2026-09-24、3 分 46 秒、BUILD SUCCESSFUL、全段が通過、SKIPPED 0）。

| 対象 | 単体テスト（`*Test`） | 結合テスト（`*IT`） |
|---|---|---|
| バックエンド全体 | 633 件（失敗 0・飛ばし 0） | 305 件（失敗 0・飛ばし 0） |
| うち U3（`cherry.mastersmith.dslmanage`） | 77 件 | 9 件（3種類の DB で各 3 件） |
| うち U1（`cherry.mastersmith.targetdb`） | 80 件（前は 79 件） | 50 件（前は 47 件） |
| 画面（Vitest） | 167 件 | — |

U3 の単体テストの内訳: `TypeCategoryMappingTest` 47（型の組み合わせ 41 の繰り返しを含む）、`DslTreeBuilderTest` 12、`DslYamlWriterTest` 3、`DslGenerationPropertyTest` 2（jqwik、200 回・300 回）、`TargetSchemaDslGeneratorTest` 10（理由ごとの繰り返し 2 を含む）、`DslManageGenerateBoundaryArchitectureTest` 3。

| カバレッジ（JaCoCo、単体と結合の合算） | 行 | 分岐 |
|---|---|---|
| 全体 | 97.5%（2938/3014） | 93.8%（1131/1206） |
| `cherry.mastersmith.dslmanage.generate` | 99.3%（303/305） | 99.3%（144/145） |
| `cherry.mastersmith.targetdb.config` | 99.2%（125/126） | 98.5%（66/67） |
| `cherry.mastersmith.targetdb.domain` | 100.0%（120/120） | 96.9%（62/64） |
| `cherry.mastersmith.targetdb.repository` | 96.0%（119/124） | 87.5%（28/32） |
| `cherry.mastersmith.targetdb.service` | 100.0%（29/29） | 100.0%（18/18） |

下限（行 80%・分岐 70%、全体と新しいパッケージごと）はすべて満たした。除外は足していない。SpotBugs の統合を止める指摘（priority 1・`SQL_`）は 0 件。U3 の警告は2件（`TargetSchemaDslGenerator` の `EI_EXPOSE_REP2`（注入した部品を持つことへの指摘）priority 2、`TypeCategoryMapping` の `IMPROPER_UNICODE`（大文字・小文字をそろえる比較への指摘。`Locale.ROOT` で比べている）priority 3）で、既存の決まりどおり警告のまま。

参考（性能の測定ではない）: 100 テーブル × 100 カラム（コメント無し、型は 3 種類を交互）の写しの組み立てと書き出しを単体テストの環境で1回試したところ、本文は 6,710,585 バイト、約 0.7 秒だった。

## 5. 計画からのずれ

| ずれ | 扱い |
|---|---|
| U1 の `SchemaRows.java` を変えた | 計画の Step 2 と依頼の範囲には `TargetDbType`・`MysqlSchemaQueries`・`PostgresSchemaQueries` だけが名指しされているが、問い合わせの行を写しの型に変えるのは `SchemaRows.columns` のため、11 番目の項目を読む1行を変えた。行の組み立て・問い合わせの数・決まりは変えていない |
| 計画の部品の表に無い `DefaultDslGenerator` の実装の名前を `TargetSchemaDslGenerator` にした | 既存の `JdbcTargetSchemaReader`・`DefaultDslReader` と同じく、インターフェースと実装を分けた |
| `DslYamlWriterTest` を足した（計画の Step 6 の件数の外） | 固定の書き出しの形・別名を作らないこと・改行の文字を含む名前を個別に確かめるため |
| 計画の「`Yaml.dump`」ではなく `Representer`・`Serializer`・`Emitter` で書き出した | 2節。書き出しの形と結果は同じ |
| 結合テストで、空のスキーマの読み取りに別の読み取りのアカウントを作った | U1 のテストの共通部品（`TargetDbTestDatabase`）を変えずに、空のスキーマだけを読める権限で読むため |
| 生成の例の期待する本文はブロックの形 | functional-spec.md 3節の例は流れの形（`{ ja: ..., en: ... }`）で書かれているが、NFR 設計 2節がブロックの形に固定しているため。項目の並びと値は例と同じ（`DslTreeBuilderTest` でバイト単位で比べる） |

## 6. 承認済みの文書との差

承認済みの文書は書き換えていない（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 項目 | 文書 | 承認済みの記述 | 実装 |
|---|---|---|---|
| `columnType` の追加（Q1: A） | `inception/contract-design/contract-summary.md` の C1、`construction/u1-target-db/functional-design/entities.md` | `dbType` は `typeName`・`length`・`precision`・`scale` | `TargetDbType` に任意の `columnType` を足した（MySQL・MariaDB の `COLUMN_TYPE`、PostgreSQL は null）。DSL の `dbType.name` は `DATA_TYPE` のまま |
| コメントの制御文字（NFR4.8） | `construction/u3-default-dsl-generation/functional-design/rules.md` の BR1.2 | 制御文字の扱いは無い | 改行（LF）・タブを除く C0・U+007F・C1 を取り除く。空・空白だけになったら物理名 |
| 読み取りの目的 | 契約 C1 | `readSchema()` に引数なし | `readSchema(ReadPurpose.GENERATE)`（U1 の NFR 設計の差のとおり） |
| 大きさの上限 | 要件 NFR2・契約 C4 ほか | 5MB | 10MB（`DslFormat.MAX_BYTES`。U3 の NFR 要件の決定） |
| 長さが 2147483647 を超える型（`longtext`） | BR2.1（型をそのまま入れる）・BR4.1（長さがあれば `maxLength`） | 長さをそのまま入れる | U2 の JSON Schema と `DbType` が 32 ビットの整数までのため、長さを null にし、`maxLength` を作らない |
| 参照先が写しに無い外部キー | BR3.2 | 前提として参照先は同じスキーマにある | 写しに無い参照先の外部キーは写さない（2節） |
| 100 × 100 の本文の大きさの目安 | `construction/u3-default-dsl-generation/nfr-requirements/tech-stack-decisions.md` の NFR2.5 | 約 5.3MB（流れの形の例での試算） | ブロックの形の実際の書き出しで約 6.7MB（コメント無し）。10MB の内だが、余裕は試算より小さい。README の目安を 6.7MB にした |
| 書き出しの形 | functional-spec.md 3節の例 | 流れの形 | ブロックの形（NFR 設計 2節） |

## 7. U4・Build and Test への申し送り

- **U4（呼び方）**: `DefaultDslGenerator.generate()`（実装の Bean は `TargetSchemaDslGenerator`）。`TargetUnconfigured`・`TargetUnavailable(reason)` を業務の例外（`TARGET_DB_*` の code）に変える。`Generated` の `yamlBytes()` は呼ぶたびに写しを返す。`dslHash` は U2 の `DslReader.hash` と同じ値。
- **U4（想定外の失敗）**: 大きさの上限を超えた・U2 の検証を通らないときは `IllegalStateException`（文言は固定で本文・名前・接続先を含まない）。共通の変換で 5xx になり、プレビューは変えないこと（NFR2.5）。U3 は大きさの超過ではログを出さない。
- **U4（時間）**: 生成は同時に1つに絞ること（U4 の NFR1.13）。全体の時間は U4 の指標（`operation=generate`）で見る。
- **U4（AC3.4.4 など）**: 生成した DSL に接続先の文字列が無いことを、U4 の結合テストでも確かめること（NFR4.9）。
- **Build and Test**: AC1.2.3・NFR1.6（100 × 100 で 30 秒以内、3種類の DB）と NFR1.7 の内訳を、`TargetSchemaDslGenerator` の DEBUG のログ（`readMillis`・`buildMillis`・`writeMillis`・`validateMillis`）で測ること。本文の大きさ（コメント無しで約 6.7MB）と、コメントがあるときに 10MB の内に収まるかも確かめること。
- **MySQL の `tinyint(1) unsigned`**: MySQL 8.4 では見分けられず NUMBER になる（3節）。

## 8. 承認の前の見直し（2026-09-24、依頼者の指示「B7 のテストを足す」）

承認の前の改めてのレビュー（U3 の指摘 R-01）で、BR2.4 の「主キーのカラムは EQUALS」が UNSUPPORTED の主キーにも及ぶように読めるのに、実装は検索できる分類の主キーだけを EQUALS にしており、その境目のテストが無いと分かった。依頼者は今の動作（UNSUPPORTED の主キーは `enabled: false` のまま）を受け入れ、境目のテストを足すと決めた。実装は変えていない。

- `backend/src/test/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilderTest.java` に `unsupportedPrimaryKey`（`json` の主キーは検索が無効・演算子なし・一覧に出さず並べ替えの初期値なし、ほかのカラムは分類どおり）を1件足した。
- 実行の結果（2026-09-24）: `DslTreeBuilderTest` は 13 件すべて成功（足した1件を含む）。全体の件数とカバレッジは Build and Test で測り直す。
