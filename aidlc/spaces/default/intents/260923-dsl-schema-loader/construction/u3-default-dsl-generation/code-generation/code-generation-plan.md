# Code Generation Plan — U3 既定の DSL の生成（u3-default-dsl-generation）

U3 のコード生成の計画を示す。作るものは、対象DB のスキーマの写し（U1）から既定の DSL（U2 の書式の YAML の本文）を作り、U2 で検証して返す部品（パッケージ `cherry.mastersmith.dslmanage.generate`）である。あわせて、`tinyint(1)` を見分けるために U1 の写しの型に項目を1つ足す（`code-generation-questions.md` の Q1: A）。Bolt は B3（`inception/delivery-planning/bolt-plan.md`）。

## 1. 入力にした設計

| 文書 | 使うところ |
|---|---|
| `construction/u3-default-dsl-generation/functional-design/functional-spec.md`・`rules.md`・`entities.md` | 生成の手順、型の分類と初期値の表、生成の例、失敗のふるまい、決まり BR1.1〜BR5.3、結果の型 |
| `construction/u3-default-dsl-generation/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md` | NFR4.7〜4.9、NFR2.5、NFR1.6・1.7、NFR9.2、10MB への引き上げの決定と差の一覧 |
| `construction/u3-default-dsl-generation/nfr-design/security-design.md`・`logical-components.md` | 流れ、書き出しの形（`DumperOptions` の固定）、コメントの制御文字、接続情報を出さない、時間の内訳のログ、部品と置き場、承認済みの文書との差 |
| `construction/u3-default-dsl-generation/infrastructure-design/cicd-pipeline.md` | 単体・結合テストの中身（U1 の Testcontainers の3種類の DB を使う）、時間の測り方 |
| `inception/contract-design/contract-summary.md` の C1・C5 | `TargetSchemaReader` の写し、`DefaultDslGenerator` と `DefaultDslResult` |
| `inception/user-stories/stories.md` | US1.2（AC1.2.1・AC1.2.2）、US1.1 の生成の部分（AC1.1.1〜AC1.1.5・AC1.1.9 の生成の側） |
| `code-generation-questions.md` | Q1: A（`COLUMN_TYPE` を U1 の写しに足す） |

## 2. 前提と、この計画での読み方

- **承認の場で決まったこと**: NFR Requirements の承認の場「U3 の R-01 はコード生成で NFR4.8 に従う」により、コメントの制御文字を取り除く（承認済みの機能設計の BR1.2 には無い）。Infrastructure Design の承認の場「U3 の NFR4.9 はコード生成で拾う」により、生成した DSL・結果・ログに接続先・ユーザー名・パスワードが入らないことを単体テストで確かめる。
- **`tinyint(1)` の見分け方（Q1: A）**: U1 の写しの型 `TargetDbType` に、MySQL・MariaDB の情報スキーマの `COLUMN_TYPE`（例 `tinyint(1)`・`int unsigned`）を任意の項目 `columnType` として足す。PostgreSQL では null。U3 は `columnType` が `tinyint(1)`（大文字・小文字を問わない、`unsigned` などの付いたものを含む）なら BOOLEAN にする。`bit(1)` は精度 1 で BOOLEAN にする。DSL の `dbType.name` は今までどおり `DATA_TYPE` のまま（BR2.1。`columnType` は DSL に書かない）。
- **U1 のコードの変更**: U1 のコードとテストに手が入るが、U1 の承認済みの計画は書き換えない。変更は U3 の計画（Step 2）として行い、U3 の `source-manifest.json` に U1 のファイルも載せる。契約 C1 との差（項目の追加）を U3 の `code-summary.md` に記録する。U1 の決まり（接続先を持たない、読み取り専用、問い合わせの数がテーブルの数に比例しない、SQL はテキストブロックの固定の文字列）は変えない。
- **10MB**: 生成した DSL の上限は 10MB（10,485,760 バイト、U3 の NFR 要件の決定）。U2 の `DslFormat` の定数を使い、U3 で数を重ねて書かない。
- **置き場**: NFR 設計（`logical-components.md`）のとおり `cherry.mastersmith.dslmanage.generate` に置く。U3 は `targetdb`（U1）の `service` と `domain`、`dsl`（U2）の `service` と `domain` だけを使う。
- **U2 の書式への合わせ方**: 項目の並びは U2 の書式の例（`construction/u2-dsl-definition/functional-design/functional-design-questions.md` の DSL の形）の順に固定する。`null` の項目を書くか省くかは、U2 の JSON Schema（`backend/src/main/resources/dsl/dsl-schema-v1.json`）で許される形に合わせ、既定は例のとおり書く。文字の値の引用と、タグを書かないことは U2 の申し送りのとおり。
- **U1・U2 の共通の関門**: パッケージごとのカバレッジの下限は `cherry.mastersmith.dslmanage.generate` にそのまま当たる。

## 3. 作るもの

| 置き場 | 部品 | 役割 |
|---|---|---|
| `dslmanage.generate` | `DefaultDslGenerator`（契約 C5 のインターフェース）と実装 | U1 の `readSchema(GENERATE)` → 組み立て → 書き出し → 10MB の確かめ → U2 の `read` を順に呼び、`DefaultDslResult` で返す。どこかで失敗したら部分的な DSL を返さない。内訳の時間を DEBUG のログに出す（本文・写しの値・接続先を出さない） |
| `dslmanage.generate` | `DefaultDslResult`（sealed interface: `Generated`・`TargetUnconfigured`・`TargetUnavailable`） | 契約 C5 の結果の型 |
| `dslmanage.generate` | `TypeCategory`・`TypeCategoryMapping` | 3種類の DB の型の名前（と長さ・精度・`columnType`）から分類を決め、分類からフォーム部品・検索・一覧・詳細・書式の初期値を決める表（BR2.2〜BR2.5、functional-spec.md 2節） |
| `dslmanage.generate` | `DslTreeBuilder` | 写しから DSL の値の木（`LinkedHashMap`・`List`）を組み立てる（BR1.2〜BR1.5・BR2.1・BR3.1・BR3.2・BR4.1）。テーブルは物理名を大文字・小文字を区別せずに比べた順（同じなら元の名前の順） |
| `dslmanage.generate` | `DslYamlWriter` | 固定の `DumperOptions`（ブロック、字下げ 2、幅無制限、LF、別名を作らない、Unicode のまま）で書き出し、先頭に固定のコメント1行（`# generated from the target database schema by MasterSmith`）を付けて UTF-8 のバイト列にする（BR5.1・NFR4.7）。コメントの制御文字（改行・タブを除く C0・C1 と U+007F）を取り除き、空・空白だけならコメント無し（NFR4.8） |
| `targetdb.domain`（U1） | `TargetDbType` に `columnType`（String、任意）を足す | Q1: A |
| `targetdb.repository`（U1） | `MysqlSchemaQueries` の問い合わせに `COLUMN_TYPE` を足す | Q1: A。PostgreSQL の読み手は null を入れる |

## 4. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。

### Step 1: 骨組み

- [x] `dslmanage` と `dslmanage.generate` の `package-info.java`（日本語の説明、ライセンスヘッダー）を作る
- [x] 対応するストーリー: US1.1・US1.2（生成の部分）

### Step 2: U1 の写しに `columnType` を足す（Q1: A）— 実装とテスト

- [x] `TargetDbType` に任意の `columnType` を足す（空・空白だけは null に揃える）。`MysqlSchemaQueries` の問い合わせに `COLUMN_TYPE` を足して入れる。`PostgresSchemaQueries` は null を入れる。SQL はテキストブロックの固定の文字列のまま
- [x] U1 の既存のテスト（値のテスト・3種類の DB の結合テスト）を直し、MySQL・MariaDB で `tinyint(1)` のカラムの `columnType` が `tinyint(1)`、`bit(1)` の精度が 1、PostgreSQL で `columnType` が null であることを確かめるテストを足す
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'` と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'` を実行し、通す（U1 のカバレッジと SpotBugs の関門を保つ）
- [x] 対応するストーリー: US1.2（AC1.2.2 の真偽値）、BR2.2

### Step 3: 型の分類と初期値 — 実装

- [x] `TypeCategory`・`TypeCategoryMapping`（BR2.2〜BR2.5）。3種類の DB が U1 の写しで返す実際の型の名前（U1 の結合テストで読めた値）を表に並べる。文字列は長さ 255 以下で SHORT_TEXT、256 以上または長い文字列の型（`text`・`mediumtext`・`longtext`・`clob` など）で LONG_TEXT。整数・小数は NUMBER。`boolean`・`bool`・`tinyint(1)`・`bit(1)` は BOOLEAN。`date` は DATE。`timestamp`・`datetime`（時差つきを含む）は DATETIME。`time`（時差つきを含む）は TIME。それ以外・分からないものは UNSUPPORTED
- [x] 対応するストーリー: US1.2（AC1.2.2）

### Step 4: 型の分類と初期値 — テスト（単体）

- [x] `TypeCategoryMappingTest`: 3種類の DB の代表の型ごとの分類、長さ 255・256 の境界、`tinyint(1)` と `tinyint`・`tinyint(4)` の区別、`bit(1)` と `bit(8)`、大文字・小文字、分からない型が UNSUPPORTED。分類ごとのフォーム部品・検索・一覧・詳細・書式が functional-spec.md 2節の表のとおり
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.*'` を実行し、通す

### Step 5: 組み立てと書き出し — 実装

- [x] `DslTreeBuilder`（3節）。表示名（BR1.2、コメントは NFR4.8 で制御文字を除いたもの）、メニュー1階層とテーブルの並び（BR1.3・BR1.4）、ビュー（BR1.5）、主キー（BR3.1）、1カラムの外部キーの select と REFERENCE（`labelColumn` は参照先のカラム、BR3.2）、複数カラムの外部キーは写すだけ、バリデーション（NOT NULL で既定値が無ければ required、文字列で長さがあれば maxLength、origin は DB。BR4.1）、主キーのカラムの検索は EQUALS、既定の並べ替えは主キーの最初のカラムの ASC（BR2.4・BR2.5）
- [x] `DslYamlWriter`（3節）
- [x] 対応するストーリー: US1.1（AC1.1.1〜AC1.1.4 の生成の側）、BR1.2〜BR5.1・BR5.3、NFR4.7〜NFR4.9・NFR9.2

### Step 6: 組み立てと書き出し — テスト（単体）

- [x] 生成の例（functional-spec.md 3節の `dept_mst`）と同じ本文になる（先頭のコメント、項目の並び、値）
- [x] 表示名: コメント「氏名」を持つ `name` は ja が「氏名」・en が `name`、コメントの無い `code` は ja・en とも `code`（AC1.1.2）。制御文字が消え、改行・タブと長いコメントはそのまま残る（NFR4.8）
- [x] NOT NULL で長さ 50 の文字列のカラムに required と maxLength 50（origin DB）が付く（AC1.1.3）。既定値のある NOT NULL には required が付かない
- [x] `b_table`・`a_table` の順の写しから、メニューとテーブルが `a_table`・`b_table` の順になる。大文字・小文字の混じった名前の並び（AC1.1.4、BR1.3・BR1.4）
- [x] ビューは `view: true` で主キー・外部キーが空（AC1.1.1、BR1.5）。1カラムの外部キーは select と REFERENCE、複数カラムの外部キーは部品を変えない（BR3.2）
- [x] jqwik の性質ベースのテスト: 同じ写しを2回生成するとバイト列が同じ（BR5.1）。任意の文字列（`:`・`#`・`-`・改行・引用符・`&`・`*`・`!` を含む）のコメントと名前で生成した DSL が U2 の検証を通り、読み直したモデルの値が元（制御文字を除いたもの）と同じ（NFR4.7）。失敗時の乱数の種が出力に残る
- [x] 生成した本文に写しの外の値（接続先・ユーザー名・パスワードに当たる文字列）が入らない（NFR4.9、BR5.3）
- [x] 単位の単体のコマンドを実行し、通す

### Step 7: 生成の口 — 実装

- [x] `DefaultDslGenerator` と実装、`DefaultDslResult`（3節）。U1 が UNCONFIGURED なら `TargetUnconfigured`、UNAVAILABLE なら理由をそのまま `TargetUnavailable`（BR1.1）。書き出した本文が 10MB を超えたら想定外の失敗（例外。部分的な DSL を返さない、NFR2.5）。U2 の検証が INVALID なら想定外の失敗とし、WARN のログは誤りの種類と件数だけ（本文・写しの値・接続先を出さない、BR5.2）。VALID なら `Generated`（本文・識別）
- [x] 対応するストーリー: US1.1（AC1.1.5・AC1.1.7 の U3 の側）、BR1.1・BR5.2、NFR2.5・NFR1.6・NFR1.7（内訳のログ）

### Step 8: 生成の口 — テスト

- [x] 単体（U1 の読み取りの口を差し替える。U2 の `DslReader` は本物を使う）: 設定が無い・接続できない（理由ごと）が結果にそのまま出る。テーブルが0件の写しから、メニュー・テーブルが空の DSL ができて U2 の検証を通る。分からない型のカラムがあっても生成が続く。10MB を超える写しで例外になり部分的な DSL を返さない（NFR2.5）。U2 の検証を通らない本文を作る書き出しを差し込むと例外になり、ログに本文と値が出ない（BR5.2）。結果の識別が U2 の `hash` と同じ
- [x] 結合（コンテナ）: `DefaultDslGeneratorMysqlIT`・`DefaultDslGeneratorMariadbIT`・`DefaultDslGeneratorPostgresIT`（共通の確かめを1か所にまとめる。U1 の `targetdb/testsupport` のコンテナとスキーマの仕組みを使う）。3種類の DB に、文字列・数値・日付・日時・真偽値（MySQL・MariaDB は `tinyint(1)` と `bit(1)`、PostgreSQL は `boolean`）・長い文字列・ビュー・外部キー・コメント・記号を含む名前のテーブルを作り、読み取り → 生成 → U2 の検証を通り、型の分類のとおりのフォーム部品とバリデーションになる（AC1.2.1・AC1.2.2、AC1.1.1〜AC1.1.5・AC1.1.9 の生成の側）
- [x] 単位の単体・結合のコマンドを実行し、通す

### Step 9: 構造の検査

- [x] `src/test/java/cherry/mastersmith/dslmanage/DslManageGenerateBoundaryArchitectureTest.java`
  - `dslmanage.generate` は `targetdb` の `service`・`domain` と `dsl` の `service`・`domain` だけを使い、`targetdb.config`・`targetdb.repository`・`dsl.parse`・`dsl.validate` を使わない
  - `dslmanage.generate` は `web` 層・`repository` 層・内部DB（JPA・JDBC）を使わない（保存は U4）
  - `dslmanage.generate` は SnakeYAML の型を作る仕組み（`org.yaml.snakeyaml.constructor`）と Jackson の YAML の部品を使わない
- [x] 単位の単体のコマンドを実行し、通す

### Step 10: 1コマンドの検査と文書

- [x] `README.md` に書く: 既定の DSL の生成の決まりの要約（表示名・メニューの並び・型の分類とフォーム部品の表・`tinyint(1)`・`bit(1)` の扱い）、生成した DSL が 10MB を超えるほど大きなスキーマはスキーマを分けるなどの運用に任せること
- [x] `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` をコンテナの実行環境が動いている状態で実行し、すべての段が通ることを確かめる

### Step 11: 記録

- [x] `code-summary.md`、`source-manifest.json`（U1 のファイルの変更も載せる）、`traceability.json` を作る（コード生成の段の手順 5）。`code-summary.md` には、承認済みの文書との差（Q1: A の `columnType` と契約 C1、NFR4.8 の制御文字、`readSchema(GENERATE)`、10MB）、U4 への申し送りを載せる

## 5. ストーリーと手順の対応

| ストーリー | 受け入れ基準・要件 | 手順 |
|---|---|---|
| US1.1（生成の部分） | AC1.1.1〜AC1.1.5・AC1.1.9 の生成の側、BR1.1〜BR5.3、NFR4.7〜NFR4.9、NFR9.2 | Step 5〜8 |
| US1.2 | AC1.2.1・AC1.2.2（3種類の DB と型の分類）、NFR2.5 | Step 2〜4・8 |
| 共通の完了の条件（B3） | カバレッジ（全体とパッケージごと）、SpotBugs、U1 の関門を保つ | Step 2・9・10 |

AC1.2.3 と NFR1.6・NFR1.7（100 テーブル × 100 カラムで 30 秒以内と内訳）は Build and Test で3種類の DB それぞれ測る。AC1.1.6・AC1.1.7 の応答・AC1.1.11（プレビュー・エラー応答・監査）は U4 が受け持つ。

## 6. テストの量（Standard）

| 部品 | 単体 | 結合 |
|---|---|---|
| U1 の `columnType` | 2〜3 件 | 3種類の DB で各1〜2 件 |
| 型の分類と初期値 | 6〜8 件 | — |
| 組み立てと書き出し | 8〜10 件（性質ベース 2 件を含む） | — |
| 生成の口 | 6〜8 件 | 3種類の DB で各2〜3 件 |
| 構造の検査 | 3 件 | — |

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


Testing Contract の `plan_profile.steps` との対応: 骨組みは Step 1、DB アクセス（U1 の写しへの項目の追加）は Step 2、業務処理（型の分類・組み立て・書き出し・生成の口）は Step 3〜8、環境とビルドの設定（構造の検査・検査）は Step 9・10、記録は Step 11。U3 は library の単位で API と画面を持たない。テストの実行の準備（`plan_profile` の2つ目）は既存の Gradle の設定を使い、`unit-test-instructions.md` にコマンドを記す。
