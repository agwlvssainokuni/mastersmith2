# 生成の記録 — U2 dsl-v2（Bolt B1）

計画 `code-generation-plan.md` の各 Step で流したコマンドと結果を書く。道はプロジェクトのルートからの相対で書く。colima を使うコマンドは、README のとおり次をシェルに渡して流した（以下「colima の環境変数」）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Step 1: 作業の場の用意

- ブランチ `feature/261004-role-menu-b1` は指揮役が `develop`（`65a76c57e8b4696c03d6215bf5fcf501ffc518db`、B2 を統合済み）から作成済み。計画の承認までの記録は依頼者の承認を得てコミット済み（`3b7e219`）。生成の開始の時点の HEAD は `3b7e21909e82f474061cb7d5e01e85d92386553a`。
- `git status --short`: アプリのソースに未コミットの変更なし（`aidlc/spaces/default/intents/261004-role-menu/audit/sakura-local-4e42a93f87ce.md` の監査ログの追記だけ。ワークフローの記録のため外して判断した）。
- `frontend/playwright-report`・`frontend/test-results`: どちらも無い。

## Step 2: テストの実行の準備と洗い出しの確かめ

| コマンド | 結果 |
|---|---|
| `./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'` | BUILD SUCCESSFUL。11 クラス・89 件、失敗 0・飛ばし 0 |
| colima の環境変数を付けて `./gradlew :backend:integrationTest --tests cherry.mastersmith.dsl.DslSchemaPublicationIT` | BUILD SUCCESSFUL。2 件、失敗 0・飛ばし 0 |
| `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/dsl` | 16 ファイル・138 件 passed |

- `unit-test-instructions.md` の 2節のコマンドと同じで、違いは無い。
- 洗い出しの検索（`git grep -l -F` で `tables:`・`menus:`・`version: 1`・`DslModel(`・`DslMenuItem(`・`.tables()`・`.table()`・`testing/fixtures` を `backend/src/test`・`frontend/src`・`frontend/e2e`・`perf` に流し、`targetdb` の下を除いた）の当たりは、計画の 4.4・4.5・4.6・4.7 の一覧にすべて載っていた。増減は無い（FD R-03）。
  - `testing/fixtures` の当たりのうち `features/invitation`・`features/preferences`・`features/registration`・`features/useradmin` の各テストは、それぞれの機能の見本（`features/<機能>/testing/fixtures.ts`）で DSL ではないため対象外。
  - 計画の一覧にあって検索に当たらないもの（`SafeYamlParserPropertyTest`・`ValidationTestSupport`・`DslSchemaPublicationIT` など）は、上限の引数化・v2 の道の書き換えで直すもので、版 1 の見本の文字を持たないため当たらない。

## Step 3: 変更の前の基準

- colima の環境変数を付けて `./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`（23:14:18〜23:22:46、BUILD SUCCESSFUL in 8m 28s）。`compileJava`・`compileTestJava` は UP-TO-DATE（変更の前のクラスで測った）。
- テストの件数（`backend/build/test-results/` の集計）: 単体 1,577 件・結合 712 件、どちらも失敗 0・飛ばし 0（対象DB の3種類のテストも SKIPPED なし）。
- `backend/build/reports/jacoco/test/jacocoTestReport.xml` のパッケージごとの値（全体の合計は行 98.9%・分岐 94.8%）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `dsl.domain` | 236/236 = 100.0% | 110/116 = 94.8% |
| `dsl.parse` | 220/223 = 98.7% | 117/127 = 92.1% |
| `dsl.service` | 130/132 = 98.5% | 52/52 = 100.0% |
| `dsl.validate` | 263/270 = 97.4% | 117/129 = 90.7% |
| `dslmanage.domain` | 130/130 = 100.0% | 24/26 = 92.3% |
| `dslmanage.generate` | 303/305 = 99.3% | 145/145 = 100.0% |
| `dslmanage.service` | 506/506 = 100.0% | 130/132 = 98.5% |
| `dslmanage.web` | 131/131 = 100.0% | 26/30 = 86.7% |
| `dslmanage.repository`（値の記録だけ） | 74/74 = 100.0% | 4/4 = 100.0% |

- `git grep -n 'dsl-schema-v1' -- ':!aidlc' ':!vendor'` の当たり（13 行・6 ファイル）: `README.md`（609・611・617・710 行の4か所）、`backend/build.gradle.kts`（383・384・386・404 行）、`DslFormat.java`（53・56 行）、`DslSchemaPublicationIT.java`（83 行）、`frontend/src/features/dsl/DslSubmitForm.test.tsx`（176 行）、`frontend/src/features/dsl/submitInput.ts`（26 行）。計画の「6か所と README の言及」は、README を除く5ファイルと README で6ファイルに当たる。

## Step 4: 読み込みの部品の上限の引数化と安全な読み込みの口 — 実装

- `dsl.parse` に上限の型 `YamlLimits`（大きさ・深さ・コレクションを指す別名の数・展開後の節の数。範囲を確かめる）を足し、`SafeYamlParser.parse(byte[], YamlLimits)`・`LimitingParser`・`YamlTreeConverter.convert(..., maxExpandedNodes)` が上限を引数で受ける形にした。`dsl.parse` の中から `DslFormat` の上限の定数を読む所は無くなった。深さと別名は `LoaderOptions` と `LimitingParser` に、大きさは `setCodePointLimit` に同じ値を渡す（T1'）。
- `DefaultDslReader` は `DslFormat` の値で作った上限（`DSL_LIMITS`）を渡す。段の順・誤りの種類と文言は変えていない。
- `dsl.service` に `SafeYamlReader`（口）・`DefaultSafeYamlReader`（`@Service`）・`SafeYamlLimits`・`SafeYamlResult`（`Parsed`・`Rejected`）・`SafeYamlRejectionKind`（6区分）・`YamlPositions`（`PositionMap` を包み、行・列は `YamlPositions.Location` で返す）を足した。名前は計画の案のとおり（9節 D-3）。
  - 写し方: `SIZE_LIMIT`→`TOO_LARGE`、`DEPTH_LIMIT`→`TOO_DEEP`、`ALIAS_LIMIT`（別名の数・展開後の節の数・自分を含む別名）→`TOO_MANY_ALIASES`、`FORBIDDEN_TAG`→`TAG_NOT_ALLOWED`、`DUPLICATE_KEY`→`DUPLICATE_KEY`、`SYNTAX`（UTF-8・YAML として読めない・キーが単独の値でない）→`SYNTAX`。文言の鍵と埋める値は捨てる。
  - `toString`: `Parsed` は JSON の木の節の数だけ（`Parsed[nodes=N]`。キーは数えない）、`Rejected` は区分と行・列だけ（場所 `path` は利用者の書いたキーを含みうるため出さない）、`YamlPositions` は場所の数だけ。
- 計画に無い小さな変更（コンパイルのため）: `parse` の引数が増えたため、Step 5 の3つのテストに加えて `dsl/validate/ValidationTestSupport.java` の呼び出し1行も上限を渡す形に直した（計画 4.4 の一覧に載るファイルで、Step 9 の書き換えより先に1行だけ直した）。上限の値はテストの手伝い `dsl/testsupport/DslSamples.DSL_YAML_LIMITS`（`DslFormat` の値）に置いた。
- `./gradlew :backend:spotlessApply :backend:compileJava :backend:spotlessCheck`: 通過。

## Step 5: 読み込みの部品と安全な読み込みの口 — テスト

- `dsl/service/SafeYamlReaderTest.java`（16 メソッド・18 件）: 大きさのちょうど（64 バイト）と1つ超え、読まずに `TOO_LARGE`（UTF-8 でない本文でも）、深さ N=3・N+1=4（`TOO_DEEP`、4 行 7 列）、別名 N=1・N+1=2（`TOO_MANY_ALIASES`、4 行 5 列）、別名 0（対応表を指す別名で拒否・文字の値を指す別名は通る）、展開後の節 5 のちょうどと6、別名の爆発（9 段 × 9 個）を DSL の上限と小さな上限（節 1,000）でそれぞれ 5 秒の内に `TOO_MANY_ALIASES`（テストの中で `System.nanoTime` で測った）、自分を含む別名、タグ3種、重複キー（場所つき）、`SYNTAX`（YAML・UTF-8）、部品の例外の文が無い、上限の範囲外の例外、位置を引く口、`toString` に目印の文字列が無い。
- 既存の `SafeYamlParserTest`・`YamlTreeConverterTest`・`SafeYamlParserPropertyTest` は上限を渡す形にだけ直した（見本は版 1 のまま）。
- 1回目: `toStringHasNoContent` が失敗（期待 `Parsed[nodes=4]`、実際 3）。テストの期待値の誤り（JSON の木ではキーは節にならない）で、期待値を 3 に直し、`Parsed.toString` の説明文に「キーは数えない」を足した。
- 2回目: `./gradlew :backend:spotlessCheck :backend:test --tests 'cherry.mastersmith.dsl.*'` → BUILD SUCCESSFUL。12 クラス・107 件（既存 89 件＋足した 18 件）、失敗 0・飛ばし 0。版 1 のままの既存の `DefaultDslReaderTest`・`SafeYamlParserTest`・`DslBoundaryArchitectureTest` が通り、引数化で DSL の読み込みの結果が変わらないこと（BR6.6）と、`SafeYamlReader` の公開の型に `dsl.parse` の型が出ないこと（`parseAndValidateStayInside`）を確かめた。

## Step 6: 版 2 の書式・モデル・深さの関数 — 実装

- `dsl.domain`: `DslFormat`（`CURRENT_VERSION = 2`、`MAX_MENU_DEPTH = 5`、`SCHEMA_RESOURCE`・`SCHEMA_PUBLIC_PATH` を v2。読み込みの上限の値は変えない）、`TableRef`・`DslSchema` を足す、`DslMenuItem.table` を `TableRef` に、`DslModel(dslHash, formatVersion, schemas, menus)`（スキーマ名の重なりを拒む、`findTable(TableRef)`・`schema()` を足す。版 1 の平らな `tables` は持たない）、`MenuDepth`（`overDepth`・`depth`・`prune`・`Pruned`）、`DslStartupReadResult`（`Valid(model, prunedMenuItems)`・`Invalid(errors)`。置き場は `DslReadResult` と同じ `dsl.domain`）、`DslMessageKeys` に `SEMANTIC_MENU_DEPTH`（`dsl.semantic.menu.depth`、埋める値は上限の段の数）・`SEMANTIC_SCHEMA_COUNT`（`dsl.semantic.schema.count`、埋める値は書かれたスキーマの数）。鍵の名前は計画の案（`dsl.semantic.menuDepth`・`dsl.semantic.schemaCount`）から、既存の鍵の並べ方（`dsl.semantic.menu.*`）に合わせて変えた（9節 D-3 の範囲）。
- `MenuDepth.overDepth` は木の型を受ける形（`overDepth(roots, children, limit)`）にし、意味の検証（JSON の木）とモデル（`DslMenuItem` の木）の両方で同じ数え方を使う。道は最上位からの位置の並び。
- メニューの組の無い先の誤り（`SEMANTIC_MENU_UNKNOWN_TABLE`）に埋める値は `スキーマ名.テーブル名` にした（鍵は既存のまま）。
- `git mv backend/src/main/resources/dsl/dsl-schema-v1.json backend/src/main/resources/dsl/dsl-schema-v2.json` の後、中身を版 2 にした: title「MasterSmith DSL（書式の版 2）」、根の `required` を `version`・`menus`・`schemas`、`version` に `const: 2`、`schemas`（スキーマ名の対応表、`propertyNames` で1文字以上）と `$defs/schema`（`label`・`tables`、ほかの項目を許さない）を足し、メニューの `table` を `{schema, name}`（`type: ["object", "null"]`、ほかの項目を許さない）にした。外部の `$ref` は無い。メニューの組は `anyOf` で書くと誤りの写し方が粗くなる（キーワード `anyOf` の1件になる）ため、既存の書き方（`type` に null を含め、対応表のときだけ効くキーワード）にそろえた。
- `backend/build.gradle.kts`: `dslSchemaSource`・説明のコメント（383・384 行）・`verifyDslSchemaInWar` の2つの道（404 行）を v2 にし、節の見出しのコメントに Intent の注記を足した（5 行の差）。`packagesJudgedByTotal`・計測の除外・タスク・テストの JVM は変えていない。

## Step 7: 版 2 の検証と読み方 — 実装

- `DslSemanticValidator`: `validate(document, checkMenuDepth)` を足し（`validate(document)` は true）、スキーマの数（BR1.3）→ メニューの組と空のまとまり（BR1.6）→ メニューの深さ（BR2.1、上限を超えた最初の段の項目ごと、`MenuDepth.overDepth`）→ スキーマごとのテーブル（外部キーと選択肢の参照は同じスキーマの `tables` の中で照らす BR1.5、既存の決まり）の順に誤りを並べる。場所は `schemas.<スキーマ>.tables.<テーブル>…`。
  - 計画の段の順は「BR1.3 → BR1.5 → BR1.6 → BR2.1 → 既存のテーブル・カラムの決まり」だが、BR1.5 はテーブルごとの既存の確かめの中で行うため、誤りの並びは「メニュー → 深さ → テーブル（参照・既存）」とした（誤りはすべて返すため、件数と中身は同じで、並びだけが違う）。承認の場で伝える。
- `DslSchemaValidator`: 本体は変えていない（読む資源は `DslFormat.SCHEMA_RESOURCE` で v2 になる）。
- `DslModelMapper`: `schemas` → `DslSchema` → `tables`、メニューの `table` の組から `TableRef` を作る。
- `DslReader` に `readAtStartup(byte[])` を足し、`DefaultDslReader` は共通の段の読み込み（大きさ → 読み込み → 版 → 構文 → 意味）の後、`readAtStartup` だけが深さを外して読み、モデルを作った後に `MenuDepth.prune` で深すぎる枝を落とし、落とした数を添える。版の確かめは整数の 2 だけを受け付ける（版 1 を読み替えない）。
- `./gradlew :backend:spotlessApply :backend:compileJava`: dsl は通り、dslmanage の 7 か所（`DslDiffCalculator`・`DslReconciler`・`DslSummaryCalculator`）が計画どおり型の違いで落ちた（Step 8 で追従）。

## Step 8: 次の層の型の追従（9節 D-1）

- 本体: `DslDiffCalculator`・`DslReconciler`・`DslSummaryCalculator` を `model.schema().tables()`・`item.table().name()` の置き換えだけにし、`TODO(B1 Step 12)` を付けた。`DslTreeBuilder` はコンパイルは通る（モデルの型を使わない）が、まだ版 1 の木を作るため `TODO(B1 Step 10)` を付けた。`DslResponses` は応答の型が `PreviewView` をそのまま使うため直す所が無く、`TODO(B1 Step 14)` は付けていない。
- dslmanage のテスト（型だけ）: `model.tables()` → `model.schema().tables()`（`AbstractDefaultDslGeneratorIT`・`DslGenerationPropertyTest`・`DslYamlWriterTest`・`DslTreeBuilderTest`・`TargetSchemaDslGeneratorTest`・`DslStartupIT`・`DslAdminApiIT`・`DslSummaryAndDiffTest`）、`extracting(DslMenuItem::table)` → `extracting(item -> item.table().name())`、`TargetSchemaDslGeneratorTest` の `CountingReader` に `readAtStartup` を足した。振る舞いの確かめは変えていない（それぞれの層の Step で書き換える）。
- テストのソースは dsl と dslmanage を一緒にコンパイルするため、`compileTestJava` を通すには dsl のテストの書き換え（Step 9 の中身）も要った。Step 9 の書き換えを先に済ませてから、この Step のコマンドを流した。
- `./gradlew :backend:compileJava :backend:compileTestJava :backend:spotlessCheck`: BUILD SUCCESSFUL。dslmanage のテストは流していない（2.3）。
- この時点の `git grep -n 'TODO(B1' -- backend`: 5 件（`DslTreeBuilder` 1・`DslDiffCalculator` 1・`DslReconciler` 1・`DslSummaryCalculator` 2）。

## Step 9: 版 2 の書式・モデル・検証 — テスト（dsl の層）

- 見本 `backend/src/test/resources/cherry/mastersmith/dsl/valid-sample.yaml` を版 2（`schemas.sales`、表示名「販売」・`Sales`、メニューの組）にした。
- 書き換え: `DslModelTest`（版 2 の形・`findTable`・`schema()`・`TableRef`・`DslSchema`）、`DslResultTypesTest`（版 2 のモデル、`DslStartupReadResult`、定数の期待値を版 2・深さ 5・v2 の道に）、`ActiveDslModelStoreTest`、`DefaultDslReaderTest`（版 1・`3`・`"2"` などは `UNSUPPORTED_VERSION` 1件、版が無い、スキーマ 0・1・2、スキーマ名とテーブル名の重複キー、組の無いスキーマ・テーブル、深さ 5 は受け付け・6 は位置と上限つきで拒否、`readAtStartup` が深さだけ外して落とした数を返す・版 1 とスキーマの数の誤りは Invalid、外部の `$ref` を取りに行かない（`CountingHttpServer`））、`DslSchemaValidatorTest`（字下げと場所の道を版 2 に、版 1 の平らな `tables` は構文の誤り、スキーマの形、メニューの組の形）、`DslSemanticValidatorTest`（場所の道を版 2 に、スキーマの数、同じスキーマの中の参照、組の無いスキーマ、深さの境界と位置、子孫に重ねないこと、深さを外す引数）、`SafeYamlParserTest`・`YamlTreeConverterTest`（見本を版 2 の形に、スキーマ名の重複キーを足した）、`DslSchemaPublicationIT`（`static/dsl/dsl-schema-v2.json`）。
- 足した: `MenuDepthTest`（例 6 件）、`MenuDepthPropertyTest`（jqwik の性質 5 件、各 300 回。深さ 8 段までの任意の木: 落とした木は深さ 5 以下・残った項目の親子と並びは元と同じ・テーブルも子も持たないまとまりが無い・落とした数＋残った数＝元の数・上限内の木は変わらない）。
- 1回目: 4 件が失敗。
  - `MenuDepthPropertyTest`（2 件）と `MenuDepthTest.sixthLevelIsPrunedWithChildren`: 本体 `MenuDepth.prune` の誤り。子の数が元と同じなら元の項目をそのまま残していたため、深い子孫を落として作り直した子があっても元の（深すぎる）項目が残った。子が元の項目とすべて同じもの（同一性）のときだけ元の項目を残す形に直した。性質ベースのテストが見つけた誤り。
  - `DslResultTypesTest.formatLimits`: 定数の期待値が版 1 のままだった（テストの書き換え漏れ）。期待値を版 2・深さ 5・v2 の道に直した。
- 2回目: `./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'` → BUILD SUCCESSFUL。14 クラス・140 件、失敗 0・飛ばし 0（`SafeYamlReaderTest` 18・`DefaultDslReaderTest` 27・`DslSemanticValidatorTest` 17・`DslSchemaValidatorTest` 11・`SafeYamlParserTest` 21・`DslModelTest` 8・`DslResultTypesTest` 7・`MenuDepthTest` 6・`MenuDepthPropertyTest` 5 ほか）。
- colima の環境変数を付けて `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dsl.*'` → BUILD SUCCESSFUL。`DslSchemaPublicationIT` 2 件、失敗 0。

## Step 10: 既定の DSL の生成 — 実装

- `DslTreeBuilder.build`: `version: 2`、`schemas` に写しのスキーマ名（`TargetSchema.schemaName()`、DB が返した大文字・小文字のまま）を1つ置き、表示名は ja・en ともスキーマ名（既存の `label(name, null)` で作る）、その下のテーブルは今の規則、メニューは平らに `table: {schema, name}`。根の並びは `version`・`menus`・`schemas`。クラスの説明文の「スキーマ名は入れない（BR5.3）」を版 2 の決まり（BR4.1〜BR4.3）に直した。`TODO(B1 Step 10)` を消した（検索 0 件）。
- `TargetSchemaDslGenerator`: 写しを木の部品へ渡し、生成した本文を通常の読み方で確かめる作りのままで、直す所は無かった（計画の「変える（必要なら）」）。接続先・ポート・ユーザー名・パスワード・JDBC の URL・PostgreSQL の database の項目は、写し（`TargetSchema`）に無いため渡らない。

## Step 11: 既定の DSL の生成 — テスト

- 期待の本文 `backend/src/test/resources/cherry/mastersmith/dslmanage/generate/dept_mst.yaml` を版 2（スキーマ名 `sales`、テストの写しの名前）にした。
- `DslTreeBuilderTest`: 期待の本文と空のスキーマの本文を版 2 に、「スキーマ名を書かない」の確かめを「スキーマ名を書き、接続の項目は書かない」に書き換え、版 2 の形（スキーマ1つ・表示名・平らなメニューの組）とスキーマ名の大文字・小文字を保つことのテストを足した（＋2）。
- `TargetSchemaDslGeneratorTest`: 生成した本文にスキーマ名と組を書くことを確かめる形に書き換え、検証を通らない本文の見本を版 2 の形（組の無いテーブルで意味の誤り1件）に、結果の値の見本の本文を `version: 2` にした。
- `DslGenerationPropertyTest`: 任意の名前で、スキーマ名と、メニューの組がすべてのテーブルを `(スキーマ名, テーブル名)` で指すことを足した。
- `DslYamlWriterTest`: YAML の記号を含むスキーマ名がキーと組に書かれ、読み直して同じになるテストを足した（＋1）。
- `AbstractDefaultDslGeneratorIT`（MySQL・MariaDB・PostgreSQL）: 版 2・スキーマ名（設定のスキーマ名と大文字・小文字を区別せずに一致）・表示名・メニューの組を確かめ、「スキーマ名を書かない」を「書く」に書き換えた。ユーザー名・パスワード・ポート・`jdbc:` を書かないことは残し、PostgreSQL では database の項目（キーと値）を書かないことを足した。想定の規模の大きさの記録は Build and Test。
- `./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.generate.*'` と、colima の環境変数を付けて `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.generate.*'`（1回のコマンドで両方）→ BUILD SUCCESSFUL。単体 5 クラス・78 件、結合 3 クラス（MySQL・MariaDB・PostgreSQL）・9 件、どちらも失敗 0・飛ばし 0（結果のファイルの時刻で、結合テストが流れたことを確かめた）。

## Step 12: 業務処理 — 実装

- `dslmanage.domain`: `PreviewView`（`Summary` の先頭に `schemaCount`、`MenuNode.table` を `TableRef`、`Diff(appliedExists, schemas)`、`SchemaDiff(name, label, change, tables)` を足す、`WarningKind.SCHEMA_MISMATCH`）、`DslStatus(applied, preview, appliedUnreadable)`。
- `DslDiffCalculator`: スキーマを名前で突き合わせ（増えた・減ったはその下のテーブルも同じ区分、両方にあれば表示名の違いで CHANGED・UNCHANGED）、テーブルは同じスキーマの中で今の規則。
- `DslSummaryCalculator`: スキーマの数、表示名の未設定はスキーマ → メニュー → テーブル → カラム → 固定の選択肢の順、場所は `schemas.<スキーマ>.tables.…`、メニューの木の節は組。
- `DslReconciler`: DSL のスキーマ名と写しのスキーマ名が同じ（大文字・小文字を区別）ときだけテーブルを比べ、場所は `schemas.<スキーマ>.tables.<テーブル>(.columns.<カラム>)`。違えば `SCHEMA_MISMATCH` 1件（場所 `schemas.<DSL のスキーマ名>`、文に DSL のスキーマ名だけ。文言は `frontend-components.md` 4節のとおり）。
- `DslErrorMessages`: 版の誤りの文言を BR7.1 の案内（版 2・スキーマの階層・既定の DSL の生成）に、書かれた版が 1 のときは BR7.1 の文言（`VERSION_ONE`）にする。深さ（`dsl.semantic.menu.depth`）・スキーマの数（`dsl.semantic.schema.count`）の文言（ja・en）を足した。
- `UnreadableAppliedRevision`（新規、`dslmanage.service` のパッケージの中だけの `@Component`。`remember`・`clear` は `DslStartupLoader` だけ、`isUnreadable` を `DslLifecycle` が使う。NFR 設計 R-06）。
- `DslStartupLoader`: `readAtStartup` で読む。深すぎる枝を落としたら WARN 1件（キー `dsl.hash`・`dsl.prunedMenuItems`・`dsl.menuDepthLimit` の3つ）。深さ以外の誤り（版 1 を含む）は既存の ERROR・Absent・`remember`。読めたとき・適用中が無いときは `clear`。コンストラクターは置き場の型に合わせてパッケージの中だけにした（Spring のコンストラクター注入のまま）。
- `DslLifecycle`:
  - `showPreview`: キャッシュに無いとき通常の読み方で読み直し、通らなければ 422 `DSL_INVALID`（誤りの一覧つき）。プレビューの行は残す。今までの 500（`IllegalStateException`）の経路を無くした。
  - `apply`: 計画の D-4 の順（キャッシュ → 今のプレビューの識別が同じときだけ読み直し、通らなければ確定の前に 422 → 確定 → 事前のモデルで差し替え。確かめていないのに確定できたときだけ履歴の本文を読む）。確かめの段の 422 も指標は `apply`・`rejected`。
  - `status`: `appliedUnreadable` を足した（`UnreadableAppliedRevision.isUnreadable(適用中の版の ID)`）。コンストラクターは置き場の型に合わせてパッケージの中だけにした。
- `./gradlew :backend:spotlessApply :backend:compileJava :backend:spotlessCheck`: 通過。`git grep -n 'TODO(B1' -- backend`: 0 件。

## Step 13: 業務処理 — テスト

- テストの手伝い `dslmanage/testsupport/DslYaml.java` を版 2 にした（スキーマ名の既定 `public`、`schema(name, ja, en)`・`deepMenu(depth, table)`・版 1 の本文の定数 `VERSION_ONE_YAML` を足す）。`DslYamlHashes.java` は本文のバイト列から識別を求めるだけで、書式に依らないため直す所は無かった。
- `DslSummaryAndDiffTest`（場所の道・組を版 2 に、スキーマの区分 ADDED・REMOVED・CHANGED・UNCHANGED、`schemaCount`、スキーマの表示名の未設定を足した。＋4）、`DslReconcilerTest`（場所の道を版 2 に、`SCHEMA_MISMATCH` 2件。設定のスキーマ名が文に無いこと・大文字と小文字の区別）、`DslStartupLoaderTest`（版 1 の ERROR と `remember`、読めたときと適用中が無いときの `clear`、深すぎる枝の WARN 1件のキーと値と目印が出ないこと）、`DslLifecycleTest`（読めないプレビューの表示は 422 で行は残り ERROR を出さない、適用は確定の前に 422 で何も写さない、識別が違えば 409 が先、キャッシュに無い正しいプレビューの適用で履歴の本文を読まない、`appliedUnreadable` の真と偽、版 1 の履歴の復元は 422）、`DslManageDomainTest`（`Summary`・`MenuNode`・`SchemaDiff`・`SCHEMA_MISMATCH`）、`UnreadableAppliedRevisionTest`（新規 4 件）。
- `./gradlew :backend:spotlessApply :backend:test --tests 'cherry.mastersmith.dslmanage.*'` → BUILD SUCCESSFUL（1回目で通過）。14 クラス・155 件、失敗 0・飛ばし 0（`DslLifecycleTest` 32・`DslSummaryAndDiffTest` 13・`DslReconcilerTest` 9・`DslStartupLoaderTest` 4・`UnreadableAppliedRevisionTest` 4・`DslManageDomainTest` 6 ほか。生成の層の 78 件を含む）。

## Step 14: API と設定 — 実装

- `dslmanage/web/DslResponses.java`: `DslStatusResponse` に `appliedUnreadable` を足した（今の状態と適用の応答に出る）。プレビューの応答は `PreviewView` の要約・違い・警告をそのまま写す作りのため、スキーマの数・スキーマの階層の違い・メニューの組（`{schema, name}`）・`SCHEMA_MISMATCH` は型の変更でそのまま応答に出る（説明文だけ足した）。道・方法・状態コード・`@ApiAccess(ADMIN)`・`@HeavyDslOperation` の印は変えていない（`DslAdminController.java` は差なし）。
- `backend/src/main/resources/application.yaml`: `server:` の下に `tomcat.max-swallow-size: 11MB` と説明のコメント（日本語）を足した（2.1 の決定 (1)、9節 D-10）。
- `./gradlew :backend:spotlessApply :backend:compileJava :backend:spotlessCheck`: BUILD SUCCESSFUL。

## Step 15: API と結合 — テスト

- `DslAdminApiIT`: 版 2 の応答（`schemaCount`・`diff.schemas`・メニューの組・`appliedUnreadable`）、字下げの直し、版 1 の履歴の復元は 422（英語の文に version 1 の案内）。足した: 版 1・深さ 6・スキーマ 0・スキーマ 2 の投入と復元が 422 で部品の例外の文が無く、内部DB（適用中・プレビュー・履歴）を読み直して要求の前と同じ（パラメーターの表で4件）、深さ 5 は 201・6 は場所と英語の文つきの 422、内部DB に版 1 の本文を直接置いたプレビューの表示と適用は 422・別の識別の適用は 409（409 が先）で内部DB は変わらず、破棄はできる、版 1 の適用中と深すぎるプレビューのダウンロードは本文をそのまま返す（＋7）。
- `DslStartupIT`: 版 1 の適用中で起動すると Absent・ERROR 1件・ヘルスチェック 200・`appliedUnreadable` 真（書き換え）。深さ 6 の版 2 の適用中で起動すると Present（深すぎる枝が無い）・WARN 1件（`dsl.hash`・`dsl.prunedMenuItems` 6・`dsl.menuDepthLimit` 5）・目印の表示名とテーブル名がログに無い・`appliedUnreadable` 偽（足した）。
- `DslTargetDbIT`（PostgreSQL）: 生成した版 2 の DSL に設定のスキーマ名を書き、PostgreSQL の database の項目を書かない、照合の警告の場所を `schemas.<スキーマ>.tables.…` に（見本のスキーマ名を設定のスキーマ名に合わせた）、名前の違うスキーマの投入で `SCHEMA_MISMATCH` 1件・文に設定のスキーマ名が無い（足した）。計画の「3種類の DB でスキーマ名を書き接続の項目を書かない」は、このテストのクラスが今までどおり PostgreSQL の1種類のため、3種類は Step 11 の `AbstractDefaultDslGeneratorIT`（MySQL・MariaDB・PostgreSQL）で確かめている。
- `DslManageRepositoryIT`: 置く本文の見本を `version: 2` に（振る舞いの確かめは変えていない）。`DslConcurrencyIT`・`DslAccessControlIT`・`DslAuditWriteFailureIT` は見本を `DslYaml` で作るため、`DslYaml` の版 2 化だけで通り、ファイルは変えていない。
- `dsl/service/SafeYamlReaderSecretLeakIT.java`（新規 2 件）: TRACE を有効にして、目印の文字列を含む YAML の読み込み（読めた・重複キーの拒否）で、追跡のログ（`SafeYamlReader#read`、`Parsed[nodes=…]`・`Rejected[kind=DUPLICATE_KEY…`）に目印が出ない。
- `max-swallow-size` の結合テストは足していない（10節 Q3: A。確かめは Step 24 の短い試走）。
- colima の環境変数を付けて `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dsl.*' --tests 'cherry.mastersmith.dslmanage.*'`:
  - 1回目: 9 件が失敗（`DslStartupIT` 1・`DslAdminApiIT` 5・`DslTargetDbIT` 3）。どれも版 1 の見本・場所の道・`diff.tables` を前提にした確かめで、この Step で書き換える範囲だった（計画の影響の範囲の中）。
  - 2回目（書き換えの後）: BUILD SUCCESSFUL。12 クラス・92 件、失敗 0・飛ばし 0（`DslAdminApiIT` 26・`DslAccessControlIT` 31・`DslManageRepositoryIT` 10・`DslTargetDbIT` 5・`DslStartupIT` 3・`DslConcurrencyIT` 3・`SafeYamlReaderSecretLeakIT` 2・`DslSchemaPublicationIT` 2・`DslAuditWriteFailureIT` 1・生成の3種類 9）。

## Step 16: バックエンドの区切りの確かめ

- `git grep -n 'TODO(B1' -- . ':!aidlc'`: 0 件。
- `./gradlew :backend:test :backend:spotlessCheck :backend:spotbugsGate` → BUILD SUCCESSFUL in 35s。単体の全体 186 クラス・1,646 件（変更の前の基準 1,577 件から +69）、失敗 0・飛ばし 0。`ArchitectureTest` 5・`ApiAccessArchitectureTest` 5・`DslBoundaryArchitectureTest` 4・`DslManageBoundaryArchitectureTest` 4・`DslManageGenerateBoundaryArchitectureTest` 3 が流れて通った（結果のファイルの時刻で確かめた）。SpotBugs の関門（`spotbugsMain`・`spotbugsGate`）も通過。
- `git diff develop --stat -- 'backend/src/test/java/**/*BoundaryArchitectureTest.java' backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`: 差なし。
- `ApiAccessConsistencyIT` は計画どおり Step 26 の `verify` に任せた。

## Step 17: 画面 — 実装（features/dsl）

- `api/types.ts`: `DslStatus.appliedUnreadable`、`TableRef`、`MenuNode.table` を組か null、`PreviewSummary.schemaCount`、`SchemaDiff` と `DslDiff.schemas`、`WarningKind` に `SCHEMA_MISMATCH`。
- `diffCounts.ts`: `allTables`・`countSchemaChanges` を足し、テーブルとカラムの件数はすべてのスキーマの合計に（計画の案の名前 `countSchemaChanges` のまま）。
- `DslStatusPanel.tsx`: `appliedUnreadable` のとき `Alert variant="warning"`（題と本文、閉じるボタンなし、`data-testid="dsl-status-unreadable"`）。
- `useDslAdmin.ts`: `previewInvalid`（プレビューの読み込みと適用の `DSL_INVALID` で入れ、`preview` より優先。投入・生成・復元の成功・破棄・適用の成功・プレビューの読み込みの成功で消す。D-6）。適用の確かめにスキーマの件数を渡す。食い違いの件数は今までどおり `NOT_COMPARED_KINDS` を除く。
- `DslPreviewPanel.tsx`: `invalidReport` を受け、読めないプレビュー（`data-testid="dsl-preview-invalid"`、`Alert variant="danger"` の題と本文、`DslErrorList`、ダウンロードと破棄だけ）を出す。要約に「スキーマ {count}」。
- `DslWarningList.tsx`: `SCHEMA_MISMATCH` を `NOT_COMPARED_KINDS` と見出しの順（照合できなかったものの後）に足した（10節 Q1: A）。
- `DslConfirmDialog.tsx`: `DslConfirm` の `apply` に `schemas` を足し、テーブルの件数の前にスキーマの件数の行（`data-testid="dsl-confirm-schema-counts"`）を出す（Q5: A）。
- `DslMenuTree.tsx`: 「（スキーマ名.テーブル名）」。`DslDiffTable.tsx`・`DslDiffTable.css`: スキーマごとの `tbody`（`data-testid="dsl-diff-schema-{schema}"`）と見出しの行（`th scope="rowgroup" colSpan=3`、`data-testid="dsl-diff-schema-heading-{schema}"`、スキーマ名・表示名・区分の `Badge`）、開閉と行の `data-testid` の鍵を `{schema}/{table}`、「すべて表示」が切られているときは変わったスキーマと変わったテーブルのあるスキーマだけ。`DslAdminPage.tsx` は `invalidReport` を渡すだけ。
- `messages.ts`: `frontend-components.md` 4節の7つの鍵（ja・en）、`dsl.diff.tableLabel` の直し、`dsl.confirm.schemaCounts`。`submitInput.ts`: `/dsl/dsl-schema-v2.json`。
- 計画との差: `dsl.confirm.schemaCounts` の ja は、計画の案の「スキーマ 増 {{added}}・減 {{removed}}・変 {{changed}}」ではなく、計画の「文は既存の `dsl.confirm.tableCounts` にそろえる」に従って「スキーマ 増えた {{added}}・減った {{removed}}・変わった {{changed}}」（en「Schemas added {{added}} · removed {{removed}} · changed {{changed}}」）にした。案の文と「そろえる」が食い違ったため、そろえる方を選んだ。
- 計画との差: 計画 4.6 の `DslStatusPanel.test.tsx` の「注意があってもダウンロードできる」は、適用中のダウンロードが今の状態の区画ではなく履歴のタブ（`DslHistoryTable`）にあり、この単位では変えないため、注意があっても「スキーマを読み込む」が押せることと適用中の行が残ることを確かめる形にした。
- `cd frontend && npm run typecheck && npm run lint:css`: 本体の型は通り、テストの見本の型の誤りは Step 18 で書き換えてから通した（テストも同じ型検査の対象のため）。

## Step 18: 画面 — テスト

- 書き換え: `testing/fixtures.ts`（版 2 の見本、`sampleSchemas` を足す、`appliedUnreadable`、場所の道）、`DslMenuTree.test.tsx`、`DslDiffTable.test.tsx`、`DslPreviewPanel.test.tsx`、`DslStatusPanel.test.tsx`、`DslConfirmDialog.test.tsx`、`DslWarningList.test.tsx`、`DslAdminPage.test.tsx`、`submitInput.test.ts`・`DslSubmitForm.test.tsx`（v2 の道）・`api/dslApi.test.ts`・`api/saveFile.test.ts`・`shared/api-client/apiClient.download.test.ts`（`version: 2`）。`DslErrorList.test.tsx`・`DslHistoryTable.test.tsx` は見本の書き換えだけで通った（ファイルは変えていない）。
- 足した: 注意の有無と英語と axe（StatusPanel）、読めないプレビューの表示と操作と axe・スキーマの数・`SCHEMA_MISMATCH` だけ／ほかと並ぶときの数え方（PreviewPanel）、「スキーマ名.テーブル名」と HTML に似た文字（MenuTree）、見出しの行と区分・同じ名前のテーブルの開閉が混ざらない・「すべて表示」・合計の件数・英語の axe（DiffTable）、スキーマの件数の行（0 件・英語）（ConfirmDialog）、`SCHEMA_MISMATCH` の種類（WarningList）、プレビューの読み込みと適用の `DSL_INVALID`・破棄で戻る・投入の成功で消える・適用の確かめのスキーマの件数と `SCHEMA_MISMATCH` を数えない（AdminPage、描画の後は `waitFor`）、`diffCounts.test.ts`（新規 4 件）。`role="alert"` は読み上げを前提にせず、見える文字と `role` の有無だけを確かめた（FD R-06）。
- `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/dsl src/shared/api-client/apiClient.download.test.ts`:
  - 1回目: 3 件が失敗（`data-testid` の鍵の書き換え漏れ1・括弧の中の期待値の書き換え漏れ1・`role="alert"` が誤りの一覧と2つ並ぶのに `getByRole('alert')` で1つを求めた1）。どれもテストの書き換えの誤りで、直した。
  - 2回目: 18 ファイル・168 件 passed（変更の前の 16 ファイル・138 件から +30）。
- あわせて `npm run lint` が、読めないプレビューの部品で `props` の中の ref を描画の中で参照した形を `react(refs)` で止めた。既存の部品と同じく分割代入に直して通した（Step 22 で全体を流し直す）。

## Step 19: E2E — 書き換えと DSL の画面の検査（途中で止めた）

- `frontend/e2e/040-dsl-admin.e2e.ts`: 投入する DSL を版 2（スキーマ `e2e`・メニューの組）にし、版 1 の投入で版の誤りの文（「書式の版 1 は使えません。書式の版 2（スキーマの階層あり）で書いてください。」）が誤りの一覧に出ることを1か所足した（D-11）。あわせて、書式のリンクが `/dsl/dsl-schema-v2.json`、要約の「スキーマ 1」、違いの表の見出しの行と `e2e/e2e_dept` の行、メニューの「部署（e2e.e2e_dept）」、適用の確かめのスキーマの件数の行を確かめる。版 1 の投入の 422 に Chrome が出す読み込みの失敗の表示は、要求の道（`/api/admin/dsl/preview`。問い合わせの `?source=PASTE` を外したパス）で判定して除く。
- `frontend/e2e/135-dsl-admin-accessibility.e2e.ts`（新規）: ブランドカラー（4）× テーマ（2）× 幅（360・768・1280）の 24 組、文字の大きさ md。`GET /api/admin/dsl/status`・`GET /api/admin/dsl/preview` だけを、画面の側の型（`api/types.ts`）を付けた1つの見本に差し替える。状態は (1) F2（今の状態の注意）と F3（読めないプレビュー、誤り2件）、(2) F2 と F5（60 文字のスキーマ名・長い表示名の見出しの行、「すべて表示」と開いた行）で、それぞれ axe（違反 0・必須の規則が流れた）と横のはみ出しを確かめる。GET 以外の管理の API の要求が 0 件であること、CSP の違反と画面の問題が無いことも確かめる。注記には組・状態・規則・違反の要素（`data-testid` か target）・はみ出しの値・件数だけを残す。
- `./gradlew :backend:bootWar` → BUILD SUCCESSFUL。`cd frontend && npx playwright test e2e/040-dsl-admin.e2e.ts e2e/135-dsl-admin-accessibility.e2e.ts`（Mailpit は起動済みだった。E2E のアプリはポート 18081・一時の内部DB で動き、配備したアプリには触れない）:
  - 1回目: 25 件が失敗。
    - 040（1件）: 版 1 の投入の 422 の表示を除く判定が、問い合わせつきの URL の末尾で比べていたため当たらなかった（テストの誤り）。要求の道で判定するように直し、040 だけを流し直して 1 passed。
    - 135 の 768・1280（16 件）: 状態 (1) は違反 0・はみ出し無しで通り、状態 (2) の「すべて表示」の `getByRole('switch').click()` が、見た目の部品（`.mycui-switch-thumb`）に押下を取られて時間切れになった（テストの誤り）。ラベルの文字を押す形に直した。直した後、blue light 768px は (1)・(2) とも違反 0・はみ出し無しで通った。
    - 135 の 360（8 件、すべての組）: 状態 (1) で axe の `scrollable-region-focusable` が1件（`.mycui-table-wrapper`）と、文書の横幅 362px（表示の幅 360px）のはみ出しで失敗した。**ここで止めた**（下の「判断が要る点」）。
  - 失敗の画面の写し（`error-context.md`）に初期管理者のメールアドレスが入っていると、報告の確かめの道具（`playwright-secret-check-reporter.ts`）が知らせた（値は表示されていない）。`frontend/test-results`・`frontend/playwright-report` は消した。
- 360px の失敗の元の切り分け（一時の調べのファイル `frontend/e2e/zz-probe-360.e2e.ts` を作って流し、終わった後に消した。差し替えは調べの中だけ）:
  - `scrollable-region-focusable`: 既存の誤りの一覧（`DslErrorList`）の表の包み（`.mycui-table-wrapper`）。場所の長い誤り（`menus.0.items.0.items.0.items.0.items.0.items.0`）で表が横に動くのに、包みにキーボードで入れない。場所の短い誤り1件だけなら出ない。投入のタブの誤りの一覧（既存の画面）でも、場所の長い誤りなら同じく出る作り。
  - 2px のはみ出し: 既存の今の状態の区画（`DslStatusPanel`）の「適用中」の行（`dl`・`dd`・`.dsl-status-values`・`.dsl-hash` と、隠した全文の識別）。適用中の DSL があれば、注意（`appliedUnreadable`）の有無にかかわらず出る。E2E の初めの状態（適用中が無い）では出ない。
  - どちらも今回の変更ではなく、既存の部品の 360px での作りによる。既存の DSL の画面は、今まで実際のブラウザで 360px を確かめていなかった（既存の検査の E2E の狭い幅は 375px）。
- 判断が要る点（依頼者に諮る）: 計画の 135 の目標（幅 360 を含むすべての組で違反 0・はみ出し無し）を緩めずに通すには、計画の 4.5 に無い既存の部品の直し（`DslErrorList` の表の包みにキーボードで入れる形（`tabIndex=0` と読み上げの名前）、`DslStatusPanel` の適用中の行の折り返し）が要る。目標・幅は変えていない。
- この Step のチェックボックスは付けていない（135 が通っていないため）。

## Step 19 の続き: 依頼者の決定 A（既存の2部品を直す）

- 依頼者の決定（指揮役から伝達）: A。この Bolt で既存の `DslErrorList` と `DslStatusPanel` を直し、計画との差として `code-summary.md` に書く。目標（360px を含む全組で違反 0・はみ出し無し）と幅は変えない。
- `DslErrorList.tsx`・`DslErrorList.css`・`messages.ts`: 誤りの一覧の表の包み（`.mycui-table-wrapper`。make-you-chic-ui の Table ではなく frontend の側の素の包みで、vendor/ は変えない）に `role="region"`・`tabIndex={0}`・読み上げの名前 `dsl.errors.tableRegionLabel`（ja「誤りの一覧の表（横に動かして読めます）」、en「Table of errors (scroll sideways to read)」）・フォーカスの見た目・`data-testid="dsl-error-list-table-region"` を足した。
- `DslStatusPanel.css`: 適用中の行の値の列を `minmax(0, 1fr)` と `min-width: 0`・`overflow-wrap: anywhere` で折り返す形にし、識別（`.dsl-hash`。今まで折り返さない）を今の状態の区画の中だけで折り返す形にし、読み上げ用の隠した全文の識別を識別の中に置いた（`position: relative`）。調べで、骨組みのサイドバー（220px）は 360px・375px の表示でも出たままで、本文の列は 360px で約 108px と分かった（375px で約 123px）。はみ出しの元は、折り返さない識別（約 109px）と、その後ろに置かれる隠した全文の識別（`position: absolute`）だった。
- `DslErrorList.test.tsx`: 包みに名前があり Tab でフォーカスできる、英語の名前、ひとつの理由だけのときは包みが無い、場所の長い誤りで axe の違反 0 の4件を足した（＋4）。`DslStatusPanel` の折り返しは jsdom では幅を測れないため 135 で確かめる。
- 確かめ:
  - vitest（`src/features/dsl` と `apiClient.download.test.ts`）: 18 ファイル・172 件 passed。
  - `./gradlew :backend:bootWar` の後に `npx playwright test e2e/135-dsl-admin-accessibility.e2e.ts -g '360px'`: 8 passed。どの組も状態 (1)・(2) とも違反 0・横幅 360px（はみ出し無し）。報告の置き場は消した。
  - 一時の調べのファイル `frontend/e2e/zz-probe-360.e2e.ts` は、調べの後に毎回消した。
- **止めた点**: `npm run lint` の oxlint の `jsx_a11y/no-noninteractive-tabindex`（`.oxlintrc.json` で error）が、包みの `tabIndex={0}` を1件止めた（`DslErrorList.tsx` 115 行）。表がはみ出したときだけ `tabIndex` を付ける形（`ResizeObserver` で測る）も試したが、同じ規則（式で渡しても止める）と `react(set-state-in-effect)` に当たったため、決まった単純な形（`tabIndex={0}`）に戻した。lint の設定を変えると検査を緩めることになるため、依頼者に諮る。format:check・typecheck・license:check・lint:css は通る。

## Step 19 の続き: lint の除外（依頼者の決定 A）と E2E の通過

- 依頼者の決定（指揮役から伝達）: `DslErrorList.tsx` の `tabIndex={0}` の1行だけを、oxlint の行の除外のコメントで外す。`.oxlintrc.json` と ESLint の設定は変えない。
- `frontend/src/features/dsl/DslErrorList.tsx` 115 行に `// oxlint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- 横に動く表の領域をキーボードで読めるようにするため（axe の scrollable-region-focusable）。role="region" と名前（aria-label）つきの領域で、除外はこの1か所だけ。` を足した。`frontend/src` と `frontend/e2e` の oxlint の除外は、この1か所と既存の `src/types/vitest-axe-matchers.d.ts` だけ。
- `cd frontend && npm run format:check && npm run lint && npm run typecheck && npm run license:check && npm run lint:css`: 終了コード 0。
- vitest（`src/features/dsl` と `apiClient.download.test.ts`）: 18 ファイル・172 件 passed。
- `./gradlew :backend:bootWar` の後に `cd frontend && npx playwright test e2e/040-dsl-admin.e2e.ts e2e/135-dsl-admin-accessibility.e2e.ts`: 25 passed（040 が1件、135 が 24 組）、失敗 0・飛ばし 0。135 の 48 回の検査（24 組 × 2 つの状態）すべてで axe の違反 0・横のはみ出し無し。報告の置き場は消した。

## Step 20: 負荷の道具の書き換え

- `perf/make-large-dsl.mjs`: 版 2 の DSL（スキーマの `tables:` が字下げ 4 にちょうど1つ）を前提にし、テーブルの見出し（字下げ 6）で区切って写す。誤りの差し込みも字下げを版 2 に合わせた（`formPart` は 12、`primaryKey` の要素は 10）。版 2 でない DSL・`tables:` が1つでない DSL は止める。
- `perf/make-pattern-dsl.mjs`: 版 2（スキーマ `perf`、メニューの `table: {schema, name}`）で書く。
- `perf/ui/dsl-ui-lang.mjs`: 版 2 で書き、NFR 設計 R-04（計画の D-8）の判定を足した。照合の警告（無いテーブル）・名前の違うスキーマ（`SCHEMA_MISMATCH`）・書式の誤り・書式の版 1（`UNSUPPORTED_VERSION`）・メニューの深さ 6（`SEMANTIC`）の5つで、応答の message と画面の文字に日本語の文字が無いことを見る。
- `perf/ui/dsl-ui-timing.mjs`: 違いの表の行の識別が「スキーマ/テーブル」になったため、結果の `diffExpand` に `schema` と `table` を分けて書く。渡す 10MB の DSL は版 2（make-large-dsl.mjs が作る）。
- `perf/dsl-timing.sh`:
  - 使い捨てのアプリの `app.env` にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を足し、`heap` の関数で `/actuator/metrics/jvm.memory.used?tag=area:heap` の VALUE を `heap.tsv` に記録する（10MB の投入の後・表示の後・`--busy` の後）。
  - `--busy`（`BUSY_ATTEMPTS`、既定 5）: 組ごとにログインを1回して、10MB の版 2 の DSL の投入を2つ並べて送る。要求ごとに別の応答のファイル、`-w '%{http_code}'` と curl の終了コードを両方記録する。判定は `pass`（201 が1つと 503 `DSL_BUSY` が1つ）、`not-overlapped`（2つとも 201。やり直す）、`fail-candidate`（それ以外。やり直さずに記録）。5 回とも重ならなければ `not-overlapped-5-times（依頼者に諮る）`。結果は `busy.tsv`（組ごとに1行）と `<種類>/busy-state.txt`（判定・回数・`memory.peak`・OOMKilled・終了の状態・健全性）。
  - `perf/dsl-timing-report.mjs` と `perf/k6/scenarios.js` は変えていない（scenarios.js は渡す DSL が版 2 になるだけ。計画 4.7 のとおり）。
- 確かめ:
  - `node --check`（perf の .mjs と scenarios.js のすべて）: 誤り無し。`bash -n perf/dsl-timing.sh`: 誤り無し。
  - `grep -c version perf/k6/scenarios.js`: 0。
  - make-large-dsl.mjs をテストの見本（valid-sample.yaml を生成と同じブロックの形に直したもの。最後のテーブルが主キーの無いビューだと末尾の誤りを入れられず止まるため、ビューを除いたもの）で流した。出力は 10,485,760 バイト、写したテーブル 2・formPart の置き換え 7・末尾の主キーの置き換え 1。写したテーブルはスキーマの `tables` の下に入った（Python の YAML で読んで確かめた）。最後のテーブルがビューのときに止まるのは版 1 の時と同じ作り（生成した大きなスキーマは全テーブルが主キーを持つ）。
  - dsl-ui-lang.mjs の5つの DSL を取り出して YAML で読み、版・スキーマ・メニューの深さ（1・1・1・版 1・6）を確かめた。
  - 一時のファイルは scratchpad の下に置いた（リポジトリの外）。

## Step 21: 文書

- `README.md`:
  - 「DSL の書式（JSON Schema）と読み込みの上限」: 正本を `dsl-schema-v2.json` に、URL を `/dsl/dsl-schema-v2.json` に替えた。v1 の JSON Schema が無くなったこと（その URL では JSON Schema は取れず画面の HTML が返る。`WebConfig` の SPA の戻しのため。404 ではない）と、エディタの補完の設定を v2 に替えることを書いた。版 2 の骨組みの例（`yaml-language-server` の行つき）、スキーマはちょうど1つ（名前の違いは `SCHEMA_MISMATCH` で照合できなかった扱い）、メニューの深さ 5 段の数え方を足した。表の「書式の版」を「2 だけ」に、「意味」にスキーマの数とメニューの深さを足し、誤りの場所の例を `schemas.sales.tables...` にした。
  - 「既定の DSL の生成の決まり」: スキーマ名を書かないの記述を外し、`schemas` に対象DB の設定のスキーマ名を1つ置くこと、メニューの `table: {schema, name}` を書いた。
  - 「DSL の管理の API（U4）」: `status` の `appliedUnreadable`、`GET /preview` の 422 `DSL_INVALID`（読めないプレビュー）、起動時の読めない版（`appliedUnreadable`、深さだけ緩めて枝を落とす WARN）、書式の版を上げたときの扱い（版 1 の適用中は無い扱い、版 1 のプレビューの表示は 422、破棄して既定の DSL を生成し直して適用、履歴の版 1 は戻すと 422）を足した。
  - 「DSL の管理画面（U5）」: 今の状態の注意（適用中の DSL は使われていません）、プレビューのタブ（スキーマの数・スキーマごとの違い・入れ子のメニュー・読めないプレビューは「ダウンロード」「破棄する」だけ）、投入のタブの JSON Schema のリンクを v2 にした。
- `perf/README.md`: DSL の節の冒頭に版 2 であることを書き、10MB の DSL の作り方（スキーマの `tables` の下に写す）、ヒープの使用の記録（`heap.tsv`。U2 dsl-v2 の見積もり 256〜384 MB と並べて読む記録で、判定は OOMKilled・`memory.peak`）、結果のファイル（`heap.tsv`・`busy.tsv`）、追加の確かめの表に `--busy`（判定・やり直し・記録・`memory.peak` の比べ先は1件の投入）を足し、`--lang` の中身を版 2 の5つの確かめに直した。
- Javadoc: `SafeYamlReader`・`MenuDepth`・`UnreadableAppliedRevision` は、Phase 1（Step 4〜15）で使い方と守りの決まりを日本語で書いてあることを確かめた（このステップでの変更は無し）。

## Step 22: 画面の区切りの確かめ

- `cd frontend && npm run format:check && npm run lint && npm run typecheck && npm run license:check`: 終了コード 0（Step 19 の後に画面のソースは変えていない）。

## Step 23: 取り残しと変えないものの確かめ

- `git grep -n 'dsl-schema-v1' -- ':!aidlc' ':!vendor'`: 1 件。README の「版 1 の `dsl-schema-v1.json` は無くなりました」の案内の行（Step 21 で意図して書いた、移行の案内）だけ。追跡外のファイルを含めた `grep -r`（node_modules・build・dist・aidlc・vendor を除く）も同じ1件。
- `grep -rn 'version: 1' backend/src frontend/src frontend/e2e perf`: 9 件。すべて版 1 を拒否する・読めないことを確かめる見本（`DslYaml.VERSION_ONE_YAML`、`DslLifecycleTest` の読めないプレビュー2件、`DslStartupLoaderTest`・`DslStartupIT` の読めない適用中、`DefaultDslReaderTest` の起動時も版 1 を拒否、`040-dsl-admin.e2e.ts` の版の誤り、`dsl-ui-lang.mjs` の版の誤り）。DSL の見本としては残っていない。
- 4.8 のファイル: `git diff --stat develop -- <4.8 の一覧>` は差が無い。`backend/build.gradle.kts` の差は JSON Schema の名指し（v1→v2 の3か所とコメント）だけ。Flyway の移行の置き場に追跡外の新しいファイルは無い。サブモジュールの固定先も develop と同じ。

## Step 24（途中で止めた）: 短い試走

- 先に確かめたこと: colima の VM は CPU 4・メモリ 6GiB（`free -m` で available 4,854 MB）。配備したアプリ（上限 2g）・mailpit（256m）・見本の対象DB（512m）が動いている。使い捨ての環境（アプリ 2g と PostgreSQL 512m）を足しても上限の合計は約 5.3GiB で VM に収まるため、配備したアプリは止めない判断（perf/README の「配備したアプリは止めない」のとおり）。
- `./gradlew :backend:bootWar` の後に `docker build -t mastersmith:perf-dsl .`（配備のタグ local は上書きしない）。
- `caffeinate -i env REPEAT=1 OUT_DIR=build/perf-results/dsl-v2-trial MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --busy --lang postgres` を2回流した（2回目は原因を見るため `KEEP=1`）。どちらも、対象DB の起動の直後のスキーマ large を作る段で `ERROR: role "mastersmith_reader" does not exist` で止まった。アプリの起動・投入・`--busy` までは届いていない。
- 原因（確かめた事実）: colima の VM の中でホームの共有（mountType `sshfs`）が外れている。VM の `mount` に `/Users/agawa` の fuse の共有が無く、VM から見えるのは Docker が作った空のディレクトリだけだった（`/Users/agawa/Documents/project/git/mastersmith2/docker/targetdb/postgres` が root の持ち物で 01:01 に作られた空のディレクトリ）。そのため `docker/perf/compose.yaml` の `../targetdb/postgres:/docker-entrypoint-initdb.d:ro` の結び付けが空になり、PostgreSQL のログに `ignoring /docker-entrypoint-initdb.d/*` が出て、読み取りのアカウント mastersmith_reader が作られなかった。今回の変更（perf の道具・DSL）とは関係が無い。
- 片付け: 1回目は台本の片付け（`down -v`）で消えた。2回目（KEEP=1）は、compose の `down` が環境ファイルの変数が無く動かなかったため、`docker rm -f mastersmith-perf-targetdb-postgres-1`・`docker volume rm mastersmith-perf_perf-targetdb-postgres`・`docker network rm mastersmith-perf_default` で消し、一時ディレクトリ（ホームの下）と結果の置き場 `build/perf-results/dsl-v2-trial` も消した。`mastersmith-perf` のコンテナ・ボリューム・ネットワークは 0。配備したアプリ・mailpit・見本の対象DB には触れていない（どれも動いたまま）。
- 直すには colima の再起動（`colima restart` など）で共有を付け直す必要があり、そのあいだ配備したアプリも止まる。依頼者の判断が要るため、ここで止めた。VM の中に Docker が作った空のディレクトリは、共有を付け直すと共有の下に隠れる（害は無い）。

## Step 24 の続き: colima の再起動（依頼者の決定 B）

- 再起動の前のコンテナ（名前・イメージ・状態）: `mastersmith-app-1`（mastersmith:local、Up 3 days (healthy)）、`mastersmith-mailpit-1`（axllent/mailpit:v1.31.2、Up 7 days (healthy)）、`mastersmith-targetdb-postgres-1`（postgres:18.6、Up 8 days）、`mastersmith-lgtm-1`（grafana/otel-lgtm:0.34.0、Exited (0) 4 days ago。前から止まっていたため起こさない）。
- `colima stop` → `colima start`（引数なし）。後も CPU 4・メモリ 6GiB・ディスク 100GiB。
- VM の `mount` に `:/Users/agawa on /Users/agawa type fuse.sshfs` が付き、`docker/targetdb/postgres` の `01-sample-schema.sql`・`02-reader-account.sh` が VM から見えることを確かめた。
- `docker start mastersmith-targetdb-postgres-1 mastersmith-mailpit-1 mastersmith-app-1`（作り直さず、止まったコンテナを起こした。ボリュームは触れていない）。app の止まり方は exit 143（SIGTERM で正常に終了）。`/actuator/health` の 200 を約 10 秒で確かめ、3 つとも Up（app と mailpit は healthy）。アプリに送った要求は健全性の確かめだけ。

## Step 24: 短い試走（再起動の後）

- 配備したアプリは止めていない（VM の available は再起動の前に 4,854 MB、使い捨ての環境の上限の合計を足しても約 5.3GiB で収まる）。
- `caffeinate -i env REPEAT=1 OUT_DIR=build/perf-results/dsl-v2-trial MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --busy --lang postgres`（イメージ mastersmith:perf-dsl は Step 24 の前半で作ったもの。今の作業フォルダの bootWar から作った）。
- 版 2 の流れ: 生成 201・表示 200・プレビューのダウンロード 200・適用 200・生成した DSL の投入 201・10MB の正しい版 2 の投入 201・表示 200・ダウンロード 200・適用 200・適用中のダウンロード 200・誤りを含む 10MB 2 つは 422・履歴 200・戻し 201（2 回）・戻した後の表示 200。すべて期待どおり。照合の警告の場所は `schemas.large.tables.x_<番号>_<名前>`（写したテーブル 44）。
- `--busy`: 1 組目で `pass`（a=503・curl 0・`DSL_BUSY`、b=201・curl 0）。やり直しなし。後の `memory.peak` 1,569,624,064 バイト、OOMKilled=false、running・healthy。
- ヒープの使用（`heap.tsv`、記録のみ。判定は Build and Test）: 10MB の投入の後 735,051,776 バイト、表示の後 752,877,568 バイト、`--busy` の後 835,715,072 バイト。使用量はその時点の値で、GC の前のごみを含む。計画の比べ先（256〜384 MB）より大きいため、Build and Test で GC の記録（`gc.log`）の GC の後の値と合わせて読む必要がある（確かめたい点として報告する）。
- 終わりの `memory.peak` 1,603,629,064 バイト（上限 2g）、OOMKilled=false、アプリのログの ERROR 0 件。
- `--lang`: 失敗（`compareWarning: locator.click: Timeout 30000ms exceeded`）。失敗のときの画面の文字は日本語（「投入」のタブ）で、英語の「Submit」のタブが見つからなかった。表示の言語は、ログインの後に利用者の表示の設定（サーバーに保存した言語。仮の管理者は日本語）がブラウザーの言語より優先される（`frontend/src/app/display-settings/displaySettingsStore.ts`。Intent 260925-user-management の表示の設定）。`dsl-ui-lang.mjs` の最後の変更は 2026-09-24 で、それより後の表示の設定の追加で前から動かなくなっていたと見立てる（版 2 の変更とは関係しない）。直すには台本でその利用者の表示の言語を英語にする手順が要り、計画に無い変更のため直していない。Step 24 の求め（版 2 の流れと 503 が届くこと）には含まれない。
- 片付け: 台本の `down -v` で使い捨てのコンテナ・ボリュームは 0、一時ディレクトリも無し。結果は `build/perf-results/dsl-v2-trial/`（git の対象外、秘密の値は含まない）に残した。配備したアプリ・mailpit・見本の対象DB は動いたまま。

## Step 25: 記録（コード生成の段の成果物）

- `source-manifest.json`: `git diff --name-status develop`（`aidlc/` を除く）と追跡外の新しいファイルから、作った・変えた・消したアプリのソースの道 111 件（`backend/src/main/resources/dsl/dsl-schema-v1.json` の削除と `dsl-schema-v2.json` を含む）。消した v1 を除き、すべて今あるファイル。
- `traceability.json`: 129 件（OK 76・Deferred 53）。
  - AC: 機能設計の 68 件。この単位が受け持つ 19 件は確かめるテスト（または E2E）のファイルを target にした OK。ほかの単位が主の 48 件は機能設計の Deferred の先のまま。AC6.1.8 は計画 2.2 の FD R-08 のとおり「U2 の配備の段への引き継ぎ（ADR-005 の影響、deployment-pipeline・deployment-execution）」の Deferred。
  - BR1.1〜BR8.2 の 36 件はすべて OK（BR1.5 は計画 2.2 の FD R-08 のとおり、同じスキーマの中だけで参照を照らすテスト `DslSemanticValidatorTest` を target に OK）。
  - NFR はこの単位の 25 件。確かめるテストを持つ 21 件は OK（Infrastructure Design R-06 のとおり target はテストのファイル。NFR6.4 は下限を持つ `backend/build.gradle.kts`）。時間・大きさ・英語の文言を Build and Test で測る NFR2.5〜NFR2.8 の 4 件は Deferred（target は build-and-test と `perf/dsl-timing.sh`）。
  - OK の target はすべて今あるワークスペースの1つのファイルであることを確かめた。
- `code-summary.md`: 作ったもの、実装で決めたこと、テストの量（Step 26・27 の実測は後で書き足す）、計画との差 D-17〜D-26、確かめたいこと、既知の制約（NFR 設計 R-07）、後の Intent に回すこと（FD R-07）、引き継ぎ。

## Step 26: 1コマンドの検査

- colima の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（01:13:35〜01:23:16、BUILD SUCCESSFUL in 9m 41s。B2 の 13 分 56 秒より短い）。1回目で通過（直しは無し）。
- テストの件数（`backend/build/test-results/` の集計）: 単体 186 クラス・1,646 件（基準 1,577 件から +69）、結合 144 クラス・723 件（基準 712 件から +11）、どちらも失敗 0・飛ばし 0。対象DB（MySQL・MariaDB・PostgreSQL）の結合テストも流れた（飛ばし 0、ログに「コンテナの実行環境」の警告 0 件）。画面は 113 ファイル・1,043 件 passed。
- バックエンドのカバレッジ（`jacocoTestReport.xml`、Step 3 の基準 → 今回）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `dsl.domain` | 100.0% → 321/321 = 100.0% | 94.8% → 148/154 = 96.1% |
| `dsl.parse` | 98.7% → 229/233 = 98.3% | 92.1% → 121/135 = 89.6% |
| `dsl.service` | 98.5% → 229/237 = 96.6% | 100.0% → 97/105 = 92.4% |
| `dsl.validate` | 97.4% → 293/300 = 97.7% | 90.7% → 129/141 = 91.5% |
| `dslmanage.domain` | 100.0% → 137/137 = 100.0% | 92.3% → 24/26 = 92.3% |
| `dslmanage.generate` | 99.3% → 312/314 = 99.4% | 100.0% → 145/145 = 100.0% |
| `dslmanage.service` | 100.0% → 585/598 = 97.8% | 98.5% → 175/180 = 97.2% |
| `dslmanage.web` | 100.0% → 132/132 = 100.0% | 86.7% → 26/30 = 86.7% |
| `dslmanage.repository`（記録だけ） | 100.0% → 74/74 = 100.0% | 100.0% → 4/4 = 100.0% |
| 全体 | 98.9% → 6,739/6,829 = 98.7% | 94.8% → 2,525/2,670 = 94.6% |

- 下がったパッケージの理由（どれも下限 行 80%・分岐 70% の上）: `dsl.parse` は上限の型 `YamlLimits` の範囲外の確かめ（行 2・分岐 4）と既存の部品の分岐。`dsl.service` は新しい `SafeYamlResult.Rejected`（行 3・分岐 4）・`DefaultSafeYamlReader`（行 2・分岐 2）・`YamlPositions.Location`（行 1・分岐 2）の、テストで通らない防御の道。`dslmanage.service` は `DslLifecycle`（行 13・分岐 2）の適用の確かめの事前のモデルが無い場合の道（確かめていないのに確定できたときだけ履歴の本文を読む、計画 D-4）などと `DslErrorMessages` の分岐 2。
- 画面の全体: 行 97.52%・分岐 92.68%（文 97.36%・関数 98.02%）。
- WAR の中の JSON Schema: `:backend:verifyDslSchemaInWar` が通り、WAR に `WEB-INF/classes/dsl/dsl-schema-v2.json` と `WEB-INF/classes/static/dsl/dsl-schema-v2.json` があり v1 は無い（`unzip -l`）。
- 4.8 のファイル: develop との差は無いまま。下限・除外・テストの JVM のヒープは変えていない。

## Step 27: E2E（統合の前に手元で）

- Mailpit（`mastersmith-mailpit-1`）は Step 24 の起動し直しの後から動いていた（healthy）ため、`docker compose --profile mail up -d mailpit` は流さずにそのまま使った。
- `caffeinate -i ./gradlew e2eTest`（02:44:54〜02:51:48、BUILD SUCCESSFUL in 6m 53s）: 14 ファイル・177 件 passed、失敗 0・飛ばし 0・不安定 0（B2 の 13 ファイル・153 件に、135 の 24 件を足した数）。ファイルごと: 010 2・020 2・030 1・040 1・050 21・060 21・070 20・080 21・090 1・100 20・110 1・120 22・130 20・135 24。
- 報告の確かめ: 報告の確かめの道具の出力は「E2E の報告に残してはならない値は含まれていません（値の種類 7・json の報告 1・添付 299・見つかった件数 0）」。json の報告の文字列の検索でも、トークンの形 0 件・パスワードの項目 0 件、メールアドレスの形は `example.com` の1件だけ（E2E の見本の宛先。値は表示していない）。
- `frontend/playwright-report`・`frontend/test-results` は消した。
