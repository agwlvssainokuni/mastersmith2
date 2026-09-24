# Code Summary — U2 DSL の定義（u2-dsl-definition）

承認済みの `code-generation-plan.md` の Step 1〜Step 14 を、test-after（層ごとに実装 → その層のテスト → 通ってから次の層）で行った。パッケージは `cherry.mastersmith.dsl`（`domain`・`parse`・`validate`・`service`）。Bolt は B1。

## 作った・変えたファイル

| 種類 | ファイル | 内容 |
|---|---|---|
| 新規（本番） | `backend/src/main/java/cherry/mastersmith/dsl/package-info.java` ほか各層の `package-info.java` | 層の説明と、外から使ってよい型 |
| 新規（本番） | `backend/src/main/java/cherry/mastersmith/dsl/domain/` の 27 ファイル | モデル（`DslModel`・`DslMenuItem`・`DisplayName`・`DslTable`・`DslForeignKey`・`DslColumn`・`DbType`・`SearchSetting`・`ListSetting`・`DetailSetting`・`Validation`・`OptionSource`・`OptionItem`・`LookupSearchItem`・`LookupListItem`）、列挙 7 つ、結果の型（`DslReadResult`・`ActiveDsl`）、`DslError`・`DslErrorKind`・`DslFormat`・`DslMessageKeys`、内部の補助 `ModelValues` |
| 新規（本番） | `backend/src/main/java/cherry/mastersmith/dsl/parse/` の 10 ファイル | `SafeYamlParser`（SnakeYAML の `Composer` だけで節の木）、`LimitingParser`（深さ・別名・タグを出来事ごとに位置つきで止める）、`YamlTreeConverter`（JSON の形・位置の対応表・重複キー・展開後の節の数）、`PositionMap`・`JsonPointers`・`YamlPosition`・`YamlDocument`・`YamlParseResult`・`YamlRejection` |
| 新規（本番） | `backend/src/main/java/cherry/mastersmith/dsl/validate/` の 5 ファイル | `DslSchemaValidator`（networknt 3.0.6、2020-12、`fetchRemoteResources(false)`）、`DslSemanticValidator`（BR3.1〜BR3.7）、`PatternChecker`（1,000 文字・100 ミリ秒）、`DslErrors` |
| 新規（本番） | `backend/src/main/java/cherry/mastersmith/dsl/service/` の 7 ファイル | `DslReader`・`DefaultDslReader`（段の順と SHA-256 の識別）、`DslModelMapper`、`ActiveDslModelHolder`・`ActiveDslModelProvider`・`ActiveDslModelStore`（`AtomicReference`） |
| 新規（本番） | `backend/src/main/resources/dsl/dsl-schema-v1.json` | 同梱の JSON Schema の正本（2020-12、どの対応表も `additionalProperties: false`） |
| 新規（テスト） | `backend/src/test/java/cherry/mastersmith/dsl/` の 15 ファイル、`backend/src/test/resources/cherry/mastersmith/dsl/valid-sample.yaml` | 単体テスト 89 件（13 クラス）、結合テスト `DslSchemaPublicationIT` 2 件、テストの補助（`DslSamples`・`CountingHttpServer`・`ValidationTestSupport`）、正しい見本の DSL |
| 変更 | `gradle/libs.versions.toml`、`backend/build.gradle.kts`、`backend/gradle.lockfile` | SnakeYAML 2.6・networknt 3.0.6 の追加（推移依存の YAML の部品を外す）、`processResources` での JSON Schema の複写、WAR の中の複写を確かめる `verifyDslSchemaInWar` |
| 変更 | `build.gradle.kts` | `verify` の 9 の段（`verifyArtifact`）に `:backend:verifyDslSchemaInWar` を足した |
| 変更 | `frontend/vite.config.ts` | 開発サーバーのプロキシに `/dsl` を足した |
| 変更 | `README.md` | 「DSL の書式（JSON Schema）と読み込みの上限」の節、開発サーバーの説明、ライセンスの表 |

一覧のすべては `source-manifest.json`。U1 のコード（`cherry.mastersmith.targetdb`）・`SecurityConfig`・`WebConfig`・`vendor/` は変えていない。

## 主な判断

- **深さ・別名・タグは自分で数える（`LimitingParser`）**: jar を調べたところ、SnakeYAML 2.6 の `LoaderOptions` の深さと別名の上限は位置を持たない `YAMLException` で止まり、`TagInspector` は `Composer` の中で「独自の global タグ」（`!!java...` など）にしか効かない（`!custom`・`!!str`・`!` は通る）。機能設計 7.1 の「部品が示す位置」を返し、NFR3.1 の「タグはすべて拒否」を満たすため、`Composer` に渡す `Parser` を包み、出来事ごとに確かめて位置つきで止める形にした。`LoaderOptions` にも同じ上限（深さ 50・別名 100・`TagInspector` は全拒否・文字数 10,485,760）を置き、二重に守る。
- **重複キーは変換の中で確かめる**: SnakeYAML の `compose` は重複キーを確かめない（型を作る段で確かめる）ため、`YamlTreeConverter` で 2 回目のキーの位置と場所つきで止める。
- **別名の参照の位置（BR4.2）**: 節の木には別名を書いた場所が残らないため、`LimitingParser` が別名の出来事の位置を文書の順に記録し、変換でアンカーを持つ節の 2 回目以降の出現に順に当てる。対応表の値の別名は参照を書いたキーの位置、並びの要素の別名は別名を書いた位置で示す。自分自身を含む別名は `ALIAS_LIMIT`（鍵 `dsl.limit.recursiveAlias`）で止める。
- **YAML の暗黙の型**: 型を作る仕組み（`org.yaml.snakeyaml.constructor`）を使わないため、スカラーの型（整数・小数・真偽値・null）は解決されたタグから自分で JSON の値にする。YAML 1.1 のため `yes`・`on` は真偽値になる（README に書いた）。60 進数・無限大・日付は文字列のまま。
- **Jackson の YAML の部品は依存から外した**: networknt の推移依存の `tools.jackson.dataformat:jackson-dataformat-yaml`（とその依存の `org.snakeyaml:snakeyaml-engine`）を `exclude` で外した。外しても networknt の JSON の検証は動く（全テストで確認）。あわせて `DslBoundaryArchitectureTest` で `dsl` から使わないことも守る。
- **JSON Schema の null の書き方**: `oneOf` は誤りが「どれにも合わない」の1件になり場所が分かりにくいため、null を許す項目は型の並び（`["integer","null"]`・`enum` に `null`）で書いた。条件つきの必須（`search.enabled` が true なら `operator`、`list.visible` が true なら `order`、`options.source` ごとの項目、`validations` の種類ごとの `value`）は `if`/`then` で書いた。
- **誤りの埋める値**: 対応表・並びの値は中身を埋めず `object`・`array` とする（知らない項目の値を型の誤りの経路で漏らさないため。NFR5.2）。文字の値は先頭 100 文字（コードポイントで数える）。
- **固定の選択肢の値の重なり**: `entities.md` の OptionSource.items の制約（value は重ならない）は JSON Schema で書けないため、意味の検証に入れた（鍵 `dsl.semantic.options.duplicateValue`。BR3.5 の近くに置いた）。
- **正規表現の確かめ**: スレッド 2 本・待ち行列 16 件の実行器で組み立て、100 ミリ秒で待つのをやめる。実行器が受け付けられないときも「正しくない」（SEMANTIC）にする。
- **版の検査**: 本文が空・根が対応表でない・`version: null` は `dsl.version.missing`（位置は `version` のキーがあればその位置）、それ以外で整数の 1 でなければ `dsl.version.unsupported`。どちらも種類は `UNSUPPORTED_VERSION`、場所は `version`。

## テストの件数とカバレッジ（実測）

`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を README のとおり設定し、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した（2026-09-24、3 分 59 秒、BUILD SUCCESSFUL、SKIPPED 0。0〜9 の段すべて通過）。

| 対象 | 単体テスト | 結合テスト |
|---|---|---|
| バックエンド全体 | 555 件（失敗 0・飛ばし 0） | 293 件（失敗 0・飛ばし 0。対象DB の 3 種類を含む） |
| うち U2（`cherry.mastersmith.dsl`） | 89 件 | 2 件 |

U2 の単体テストの内訳: `DslModelTest` 5、`DslResultTypesTest` 6、`SafeYamlParserTest` 20、`SafeYamlParserPropertyTest` 2（jqwik、300 回・500 回）、`YamlTreeConverterTest` 6、`DslSchemaValidatorTest` 8、`DslSemanticValidatorTest` 11、`PatternCheckerTest` 5、`DefaultDslReaderTest` 19、`ActiveDslModelStoreTest` 3、`DslBoundaryArchitectureTest` 4。

| カバレッジ（JaCoCo、単体と結合の合算） | 行 | 分岐 |
|---|---|---|
| 全体 | 97.3% | 93.0% |
| `cherry.mastersmith.dsl.domain` | 100.0% | 94.8% |
| `cherry.mastersmith.dsl.parse` | 98.7% | 92.1% |
| `cherry.mastersmith.dsl.validate` | 97.4% | 90.7% |
| `cherry.mastersmith.dsl.service` | 98.5% | 100.0% |

下限（行 80%・分岐 70%、全体と新しいパッケージごと）はすべて満たした。除外は増やしていない。安全の検査: SpotBugs の統合を止める指摘（priority 1・`SQL_`）0 件、OSV-Scanner 失敗 0 件・警告 0 件、Gitleaks 通過。U2 の SpotBugs の警告（priority 2）は 20 件で、`EI_EXPOSE_REP`（record が変更できない写しを返しているのに対する誤検知）、`CT_CONSTRUCTOR_THROW`、`UNSAFE_HASH_EQUALS`（識別の文字列の比較。秘密ではない）。既存の決まりどおり警告のまま。

team.md の「投入 DSL の必ず書くテスト」との対応: 大きさ（ちょうど 10MB は通り 1 バイト超えは `SIZE_LIMIT`）、深さ（50 と 51）、別名（100 と 101、展開の爆発が 1 秒以内に `ALIAS_LIMIT`）、タグ（6 種類。記録用のクラスが作られず 0 件）、重複キー（2 回目の位置、テーブル・カラムの二重の定義も）、`$ref`（DSL の `$ref` も外部を指すスキーマも、要求を数える受け口への要求 0 件）、部品の文言を含まないこと（各テストで確かめる）。Problem Details の形の応答は U4 の受け持ち。

## 依存とライセンス

| 部品 | 版 | ライセンス | 扱い |
|---|---|---|---|
| `org.yaml:snakeyaml` | 2.6（直接の依存にした。Spring Boot の推移依存と同じ版） | Apache 2.0 | POM で確認 |
| `com.networknt:json-schema-validator` | 3.0.6 | Apache 2.0 | POM で確認 |
| `com.ethlo.time:itu`（networknt の依存） | 1.14.0 | Apache 2.0 | POM で確認 |
| `tools.jackson.dataformat:jackson-dataformat-yaml`・`org.snakeyaml:snakeyaml-engine` | — | — | 依存から外した |

`backend/gradle.lockfile` の差は `com.ethlo.time:itu:1.14.0` と `com.networknt:json-schema-validator:3.0.6` の 2 行の追加だけで、既存の部品の版は変わっていない。依存の木で `tools.jackson.core:jackson-databind` は 3.1.5 のまま（networknt の求める 3.1.4 は 3.1.5 に解決）、`slf4j-api` も既存の 2.0.18 のまま。README の「ライセンス」に 3 つを載せた。

## 計画からのずれ

- 計画の 3節にない補助の型を足した: `parse` の `LimitingParser`・`PositionMap`・`JsonPointers`・`YamlPosition`・`YamlDocument`・`YamlParseResult`・`YamlRejection`、`validate` の `DslErrors`、`service` の `DslModelMapper`・`ActiveDslModelStore`、`domain` の `ModelValues`・`OptionItem`・`LookupSearchItem`・`LookupListItem`。役割は 3節の部品の中身を分けたもので、外から使う型（`service` のインターフェースと `domain`）は計画どおり。
- `Validation` の値は、`value` 1 つではなく、数（`number`、BigDecimal）と文字（`text`）の 2 つの項目に分けた（種類に合う方だけを持つ）。`SearchSetting` の `default` は Java の予約語のため `defaultValue` にした。
- Step 8 の「版」と「段の順」のテストは、版の検査が計画どおり `DslReader` の中にあるため、`DefaultDslReaderTest`（Step 10 と同じクラス）に書いた。印は Step 10 のテストを通してから付けた。
- `DslMessageKeys` の鍵は 32 個。計画にない鍵として、展開後の節の数（`dsl.limit.expandedNodes`）と自分自身を含む別名（`dsl.limit.recursiveAlias`）は、どちらも種類は `ALIAS_LIMIT` のまま鍵だけを分けた。
- 最初の `verify` の前に、Gradle の daemon が `node` を起動できずに `checkToolchain` が失敗したため、指示どおり `./gradlew --stop` してやり直した。

## 承認済みの文書との差

| 項目 | 承認済みの文書 | 実装 | 根拠 |
|---|---|---|---|
| 大きさの上限 | 要件 NFR2・機能設計 BR1.1・functional-spec.md・契約 C4・ストーリー AC2.3.1 は 5MB | 10MB（10,485,760 バイト） | U3 の NFR 要件の決定と U2 の NFR 要件の NFR2.1。文書は書き換えていない |
| 誤りの `path` の書き方 | NFR 設計 2節は位置の対応表を JSON Pointer（`/tables/dept_mst/...`）で持つ | 対応表のキーは JSON Pointer のまま、利用者に返す `DslError.path` は点でつないだ形（`tables.dept_mst.columns.code`、並びは `menus.0.items.1`）。文書の根の誤りは `path` を null にする | 契約 C4・`entities.md` の例 |
| JSON Schema の複写の場所 | 基盤の設計は `bootWar` で WAR の `WEB-INF/classes/static/dsl/` へ複写 | `processResources` の出力（`static/dsl/`）へ複写し、WAR には同じ `WEB-INF/classes/static/dsl/` として入る | 計画の 2節（結合テストでも同じ道で確かめるため） |
| 公開の道の許可 | NFR 設計 6節は「このパスだけをログインなしで許す」 | `SecurityConfig` は変えていない（今の決まりのままログインなしで取れることを `DslSchemaPublicationIT` で確かめた） | Infrastructure Design の承認の場の決定 D |
| 同じテーブル・カラムの二重の定義 | ストーリー AC2.2.3 は「意味の誤り」 | 物理名をキーにした対応表の重複キーとして `DUPLICATE_KEY`（読み込みの段）で止める | 機能設計 BR1.5（テーブル・カラムの二重の定義もこれで止まる） |

## 文言の鍵の一覧の場所

`backend/src/main/java/cherry/mastersmith/dsl/domain/DslMessageKeys.java`。鍵はすべて `dsl.` で始まり、各鍵の説明に埋める値の並びを書いた。`DslMessageKeys.all()` で一覧（変更できない）を返す。U2 は ja・en の文言を持たない。

## U3・U4 への申し送り

- **U3（既定の DSL の生成）**: 生成した YAML は `DslReader.read` で検証する（契約 C2）。YAML 1.1 の暗黙の型のため、文字として書く値（`yes`・`no`・`on`・`off`・`null`・数に見える値、選択肢の `value` など）は引用符で囲んで書き出すこと。タグ（`!!str` を含む）は拒否されるため書き出さないこと。書式の版は `DslFormat.CURRENT_VERSION`、上限は `DslFormat` の定数。
- **U3・U4（大きさ）**: 上限 10MB は `DslFormat.MAX_BYTES`。`read` は 10MB を超えると読まずに `SIZE_LIMIT` を返すが、要求の本文の上限（Filter）は U4 の受け持ち。
- **U4（誤りの応答）**: 誤りの文言は `DslMessageKeys` の鍵ごとに ja・en を用意すること。`DslError.messageArgs` は鍵の説明の順。件数は絞っていない（先頭 100 件に絞るのは U4）。`DslErrorKind` から応答の `code` と監査の `rejectionKind` を決めること。
- **U4（適用中のモデル）**: 差し替えは `ActiveDslModelHolder.replace`（null は「無い」）。起動の直後は「無い」から始まる。実装の Bean は `ActiveDslModelStore`（`ActiveDslModelProvider` も兼ねる）。
- **U4（NFR3.4）**: 画面を通さず API を直接呼んでも、本文をすべて `DslReader` で検証することを U4 の結合テストで確かめること。
- **後続の Intent（C8）**: `ActiveDslModelProvider.current()` を使う。モデルの `pattern` は正しい正規表現かを確かめただけで、当てはめるときは時間の上限つきにすること（Javadoc に書いた）。
- **Build and Test**: NFR1.4（想定の規模で 3 秒以内）・NFR1.5（10MB で 10 秒以内）、10MB の DSL のメモリ、1,000 文字の重い正規表現の組み立て時間（待ちをやめた後も短く終わる前提）を測ること。単体テストの 10MB ちょうどの読み込み（コメントの行で埋めた形）は数百ミリ秒で終わったが、これは性能の測定ではない。
