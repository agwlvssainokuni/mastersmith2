# 生成の記録 — U1 u1-reference-read

## B1 の生成（Step 1〜6 の依頼、2026-10-30 再開）

### 前の担当の途中の作業の見直し

- 前の担当が止まった時点の未コミットの変更（targetdb の `ReadPurpose`・`TargetDbProperties`・`TargetDbSettings`・`TargetReadAccess`・`JdbcTargetReadAccess`、`application.yaml`・`.env.example`・`README.md`、targetdb のテスト8ファイル、`FakeBusinessDataPorts`）を、計画の Step 3・4 と reliability-design.md 1.1・1.2・5節に照らして読んだ。
- Step 3 の中身はそろっていた（`QueryTimeout.reference` 既定 5s と `longest()` への算入、`referenceReadTotal` 既定 10s、1 秒未満の点検と項目名だけの WARN、`Usable.queryTimeout` の `case REFERENCE`・`Usable.referenceReadTotal()`、`withRead` が `REFERENCE` を受ける、`referenceReadTotal()` の説明文と `Usable` でないときの `IllegalStateException`〈U1 R-05〉、yaml・`.env.example`・README の表）。直したところは無い。古い文言（「読み取りの全体の上限は無い」「4つの上限」）の残りも無い。
- Step 4 のテスト（`TargetDbPropertiesTest`・`TargetDbSettingsTest`・`JdbcTargetReadAccessTest` の追加分と、4節の構成子の直し）も書かれていた。

### Step 1 の確かめ

- ブランチ `feature/261029-reference-lookup-cleanup-b1` の上。`frontend/playwright-report`・`frontend/test-results` は無い。
- Dependabot の #81・#82 の取り込み（41b8bf5「frontend の依存を入れ直して最新化する」）が `develop` にある。

### Step 2 の確かめ

- `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'` は流せる（下の1件を除き通過）。
- 環境変数（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）を付けた `:backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.PostgresBusinessDataReadIT'` は 18 件・失敗 0（SKIPPED 3 は MySQL・MariaDB だけの確かめの `assumeTrue` で、前からの形）。
- 同じく `--tests 'cherry.mastersmith.targetdb.config.*'` の結合テストは 12 件（`TargetDbSecretLeakIT` 1・`TargetDbStartupIT` 7・`TargetWriteTransactionManagerInjectionIT` 4）すべて通過。
- 3.4節の突き合わせ: 今の変更は `targetdb.config`・`targetdb.domain`・`targetdb.service` だけで、`packagesJudgedByTotal` の7パッケージに触れていない。

### 止まった点（Step 4）

- `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'` で 160 件中 1 件が失敗した: `targetdb.domain.TargetSchemaResultTest` の `productFromSetting`（「the database product is read from the setting regardless of case and unknown values are empty」）。末尾の `assertThat(ReadPurpose.values()).containsExactly(GENERATE, COMPARE, LIST, DETAIL)` が、足した `REFERENCE` で食い違う。
- このテストは計画の 4節の「影響を受ける既存のテスト」の一覧に無い（`ReadPurpose` の行には `TargetDbSettingsTest`・`JdbcTargetReadAccessTest` だけがある）。依頼の決まり（計画の外の既存のテストが落ちたら止める）に従い、直さずに止めた。
- 直し方の案: 期待の並びの最後に `ReadPurpose.REFERENCE` を足す（テストだけの1行の変更。確かめの意図〈目的の一覧を固定する〉は変わらない）。

### 依頼者の決定（計画の 4節の影響の一覧との差）

- **依頼者の決定 A**: `TargetSchemaResultTest` の期待の並びの最後に `ReadPurpose.REFERENCE` を足した（テストだけの変更、確かめの意図は同じ）。計画の 4節の「`ReadPurpose` に値を足す」の行に無いテストで、計画との差として記録する。
- 直した後の `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'`: 160 件・失敗 0・飛ばし 0。Step 4 にチェックを付けた（`targetdb.config` の結合テスト 12 件は Step 2 で通過済み）。

### Step 5（businessdata.domain の実装、B1 の分）

- 足したクラス（`backend/src/main/java/cherry/mastersmith/businessdata/domain/`）: `DefinitionPurpose`・`ReferenceStatus`・`ReferenceTarget`・`ReferenceOption`・`PickerSearchField`・`PickerListColumn`・`PickerInfo`・`ReferenceField`・`ReferenceReadSpec`・`ReferencePolicy`・`ReferenceReadDeadline`・`ReferenceReadFailure`・`ReferenceFailureReason`（計画 3.1節の B1 の型）。
- 計画の幅の中で決めたこと:
  - `ReferenceTarget.kind` と `ReferenceField.kind` は DSL の `OptionSourceKind` を使い、`FIXED` を拒む（新しい列挙を足さない）。`ReferenceTarget.from(schema, OptionSource)` に BR2.5（ラベルのカラムの選び方）と `lookupList` の `order` の安定の並べ替えを置いた。
  - `PickerSearchField.operator` は既存の `SearchMode`（`EQUALS`・`CONTAINS` だけを受ける）。`CONTAINS` は既存の `SearchOperatorPolicy` と同じく `STRING` だけ。
  - `ReferencePolicy` は計画の5つ（`accessOf`・`usableSearchFields`・`distinctByValue`・`fixedOrder`・`sameReferenceValue`）に、`pickerOf`（BR5.1・BR3.5 の守り）と `optionsSpec`（BR4.2 の指定の組み立て）を足した。`accessOf` は参照先の判定の材料を `Optional<TableAccessView>` で受ける（DSL に無い・NONE は空）。
  - `fixedOrder` はラベルのカラムがバイナリか password のとき並びに使わない（値を読まないカラムで並べない）。主キーは既存の `orderKeys` と同じく `view.primaryKey()` から足す。
  - `ReferenceReadSpec` に `maxValueLength`（`ColumnWriteRulesFactory.MAX_VALUE_LENGTH` = 10,000）を持たせ（performance-design.md 1.1 の「値の長さの条件」）、`options(...)` の作り方（201 行・NULL を除く・件数なし）を置いた。B2 の作り方（検索・ラベル・check）は B2 で足す。

### 止まった点（Step 5）: 設計の名前どおりに作れない

- reliability-design.md 5.2（U1）と U2 の reliability-design.md 1.2・2節・observability-design.md は、`record ReferenceReadFailure(..., boolean budgetExceeded)` の項目と、引数の無い作り方 `static ReferenceReadFailure budgetExceeded()` の両方を同じ名前で決めている。Java では、引数の無い同じ名前のメソッドを、項目の読み出し（`boolean budgetExceeded()`）と作り方の2つで持てない（record でも通常のクラスでもコンパイルの誤り）。
- ビルドを通すため、作り方を仮に `ofBudgetExceeded()` にした（`of(RowQueryFailure)` と並ぶ名前。項目の名前 `budgetExceeded` は WARN のキーと U2 の文書が使うため変えていない）。説明文に「仮の名前・依頼者の決定待ち」と書いた。
- U2 の計画・コードはこの作り方の名前を使うため、名前を決めてもらってから Step 6 のテストを書く。Step 5・6 にはチェックを付けていない。

### 依頼者の決定 A（承認済みの設計との差）

- 全体の上限で止めた失敗の作り方は `ReferenceReadFailure.ofBudgetExceeded()` で確定した（仮ではない）。record の項目の名前は `budgetExceeded` のまま。
- 承認済みの設計（U1 の `nfr-design/reliability-design.md` 5.2・`logical-components.md` 5節、U2 の `nfr-design/reliability-design.md` 1.2・2節・`observability-design.md`）は `budgetExceeded()` と書くが、承認済みの文書は書き換えず、この差をここに記録する。**U2 の計画とコードは `ofBudgetExceeded()` を使う。**
- 説明文の「仮の名前・依頼者の決定待ち」の文言は消した。

### Step 5 の仕上げ

- `ReferencePolicy.optionsSpec` の説明文を直した: ラベルのカラムが値のカラムと同じときも、表示の列にラベルとして入れる（値は切らない選び方、ラベルは一覧の上限で切る選び方で別に選ぶ。BR7.3 のとおり）。初めの説明文は「選ばない」と誤っており、テストで分かった。

### Step 6（businessdata.domain のテスト、B1 の分）

- 足したテスト（`backend/src/test/java/cherry/mastersmith/businessdata/domain/`）:
  - `ReferencePolicyTest` 14 件（各欠ける条件で `NO_PERMISSION`、password・バイナリの値のカラム、password・バイナリのラベルは AVAILABLE で選ばず並べない、LOOKUP の一覧の列の NONE・使える条件が残らない、BR3.2 の条件の選び方、BR2.5 のラベルのカラム、ピッカーの情報と NONE の列の守り、固定の並び〈主キーの無い表を含む〉、選択肢の指定、同じ値を落とす、`sameReferenceValue`）
  - `ReferencePolicyPropertyTest`（jqwik）6 件（権限を足しても AVAILABLE が NO_PERMISSION にならない、NONE の列が条件と一覧に出ない、出力に重なりが無く最初の出現の順を保つ、反射的・対称的、大文字と小文字・末尾の空白の違いは一致しない、小数の末尾の 0 は一致する）
  - `ReferenceReadDeadlineTest` 6 件（T-T2: 残り 0.5 秒・0・過ぎた → 空、1.0 秒 → 1、2.3 秒 → 3、10 秒 → session の上限 5、同じ接続と壊れた印の共有、文ごとに時計を読み直す）と `ReferenceReadDeadlinePropertyTest`（jqwik）1 件（絞った上限は 1 以上・session の上限以下・残りの切り上げに等しい）
  - `ReferenceReadFailureTest` 10 件（T-S1: 理由の写し4つ、`INVALID_VALUE`・`UNEXPECTED` は `IllegalArgumentException`、すべての理由の網羅、`ofBudgetExceeded()` の形、食い違う組の拒否、`toString` にメッセージが無い）
  - `ReferenceTypesTest` 7 件（用途、`ReferenceTarget.from`、値を持つ型の `toString` の伏せ字〈BR10.1〉、`ReferenceField` の組の決まり、選択肢 200 は受け 201 は拒む、ピッカーの条件の演算子、`ReferenceReadSpec` の範囲）
- jqwik の失敗の種: 既存の決まりのとおり、失敗の報告（`exceptionFormat = FULL`）に種が出て、`@Property(seed = "...")` で再現する（`junit-platform.properties` の `jqwik.database = build/jqwik-database`）。今回の実行では失敗が無く、記録する種は無い。
- 途中の失敗2件（直した）: 性質ベースのテストで空の集合を `doesNotContainAnyElementsOf` に渡した誤り（テストの書き方）と、上の `optionsSpec` の説明文とテストの期待の誤り。
- `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.*'`: 164 件（うち新しい 44 件）・失敗 0・飛ばし 0、約 5 秒。
- `./gradlew :backend:test --tests '*ArchitectureTest'`: 19 クラス 94 件・失敗 0（新しい domain の型は既存の境界の決まりのまま通る）。境界テストと `backend/config/spotbugs-exclude.xml` に差は無い。
- Step 5・6 にチェックを付けた。

## B1 の生成（Step 7〜12 の依頼。C1 839013b・C2 cb8c87d の後）

### Step 7・8（repository）

- `RowDialect` に `lengthWithin`・`sortExpression` を **既定のメソッド** として足した（performance-design.md 1.2 の「既定の実装は3種類で同じ式」のとおり。両方の方言がこの既定を使い、方言のクラスの中身は変えていない）。
- `RowSqlBuilder` に `referenceWhere`（`IS NOT NULL`・文字の型の `CHAR_LENGTH(…) <= 10000`）と `referencePage`（値のカラムを主キーの側に `SUBSTRING(…,1,10001)`、表示の列を 101 で選び、固定の並びは `sortExpression` を `orderTerm` に渡す。主キーで結ぶ形・結ばない形、`LIMIT ? OFFSET ?`）を足した。判定の材料に無い表示の列・値を読まない列は選ばない（BR3.5・BR7.4）。値のカラムが読めなければ文を作らずに想定外。
- `RowSqlExecutor.search` に `Optional<ReferenceReadSpec>` を足した（メソッドの数は同じ。`prepareStatement` は `search` の本体の中だけ）。指定があるときは件数の文を作らず、`setMaxRows`・`LIMIT` に読む行を当て、`RowSearchResult.Rows` を返す。指定が無いときの文は今と同じ（既存の文字どおりの確かめが通る）。指定のときは `page` を使わないため null を渡す形にした。
- `RowSearchResult` に `Rows` を足したため、`BusinessDataReadService.search` の `switch` に `Rows`（来ないため想定外の 500）を足した。
- `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.*'`: 181 件・失敗 0（`RowDialectTest` 12・`RowSqlExecutorTest` 18 など。新しく足した確かめは方言 2 件・実行の部品 6 件）。
- `./gradlew :backend:spotbugsGate`: 通過（新しい `SQL_` の指摘は無い。除外の設定は変えていない）。

### Step 9・10（service）

- `TableAccessResolution.Resolved` に写し（`PermissionSnapshot`）を持たせ（`toString` は判定の材料だけ）、`TableAccessResolver` に `referenceView(snapshot, schema, table)`（同じ写しで参照先の判定の材料を作る。DSL が無い・DSL に無い・NONE は空）を足した。判定の材料を作る中身は `viewOf` に切り出し、`resolve` の振る舞いは変えていない。
- `ReferenceLookup.definitionReferences(resolved, purpose)`: 結果の型は `ReferenceLookup` の中の `DefinitionReferences`（`Read`・`Failed(reason)`）にした（計画 3.1節の `ReferenceLookup` のクラスの中に閉じた型。新しい外のクラスは足していない）。借りた接続の中の結果も中の型にし、例外を口の外へ投げない。`INVALID_VALUE`・`UNEXPECTED` は `BusinessDataUnexpectedException`。
  - 診断の WARN は `ReferenceLookup` のロガーで1行。キーは `purpose`・`reason`・（あるときだけ）`sqlState`・`vendorCode`・`exceptionClass`、と `budgetExceeded`。
  - 時計は既存の `Clock` の Bean（`AuthClockConfig`）を注入した。`BusinessDataReadService` は `ReferenceLookup` を受け、`Clock` は `ReferenceLookup` だけが持つ（計画 4節の「`ReferenceLookup` と `Clock` を渡す」の読み方）。
  - `DefinitionResult.Found` に `purpose` と参照の項目の対応表を足した（`VIEW` 用の2引数の作り方を残した）。`BusinessDataReadService.definition` は `purpose` を受け、失敗を既存の code（`TIMEOUT`・`UNAVAILABLE`・`MISMATCH`）に写す。
- 偽物 `FakeBusinessDataPorts.Executor` は、受けた指定と session を残し、参照先の読み取りの答えを順に返す（尽きたら空の行）形にした。
- 足したテスト: `ReferenceLookupTest` 11 件（VIEW で読まない、借りるのは1回・同じ参照先は1回、入力できないカラム・読めない参照先は読まない、200／201 行と同じ値、失敗の後は読まず WARN はちょうど1行、T-T3、上限の絞り、借りられない・設定が無い、MISMATCH と想定外、`toString`、読み取りの業務処理の code の写し）、`TableAccessResolverTest` に1件（写しを1回だけ取り参照先の判定の材料に使い回す）。
- `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.*'`: 63 件・失敗 0（`BusinessDataProblemTypeCatalogTest` の T-C3 の1件を含む）。

### Step 11・12（web と定義の結合テスト）

- `DefinitionRequest` に任意の `purpose`（文字で受け、`VIEW`・`CREATE`・`UPDATE` と文字どおり一致しなければ DSL を見る前に 400 `VALIDATION_FAILED`。3引数の作り方を残した）。
- 応答の型 `ReferenceFieldResponse`・`ReferenceOptionResponse`・`PickerInfoResponse`・`PickerFieldResponse`（検索の条件と一覧の列で同じ型。一覧の列は `operator` を載せない）。`ColumnDefinitionResponse.reference` は常に出す（VIEW と参照でないカラムは `null`）。`options` は REFERENCE の AVAILABLE だけ、`picker` は LOOKUP の AVAILABLE だけに載せる。
- D2: 作成・更新の定義では、LOOKUP の参照のカラムの `formPart` を `lookup` で返す（DSL の検証で `lookup` の部品と LOOKUP の出どころは対になるため、参照の項目の種類から決めた）。VIEW では今のまま `text`。
- `README.md` の「業務データの読み取りの API」の定義の行に、`purpose` と借りる接続の説明を足した。
- 計画 3.1節の `ReferenceTables`（testsupport）は、既存の `BusinessDataSampleSchema`（クラスごとの一意の名前のスキーマとアカウントを作って消す）に表を足す形にした。
- **計画の文との差（記録）**: 計画 Step 12 の `ReferenceTimeoutIT` は「`SilentServer` で 503 `BUSINESS_TARGET_DB_TIMEOUT`」と書くが、`SilentServer`（接続を受けて何も返さない）は接続を借りる段で止まるため、既存の決まり（reliability-design.md 2節の「接続の失敗・借りられない → UNAVAILABLE」、既存の `BusinessDataTimeoutIT` も同じ）どおり 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる。時間切れ（TIMEOUT）の経路は、3種類の DB の眠る関数を含むビューを参照先にして確かめた（文の上限 5 秒で 503 TIMEOUT、全体の上限を 2 秒にした起動で約 2 秒の 503 TIMEOUT）。設計の決まりに合わせたもので、作りは変えていない。
- 結合テストの結果（環境変数と `caffeinate -dims` を付けた）:
  - `PostgresReferenceReadIT` 12・`MysqlReferenceReadIT` 12・`MariadbReferenceReadIT` 12（T-B1〜T-B3・T-B8〜T-B10、長さの境界〈10,000・10,001 字の半角・日本語・補助面、100・101 字〉、NULL のラベルが最後、BIGINT の最大・桁の多い小数、同じ値、引用符を含む名前、複合の主キー・主キーの無い表・ビュー・自己参照、NO_PERMISSION の 200、LOOKUP のピッカー、T-C1、400）。すべて失敗 0。
  - `ReferenceConnectionUsageIT` 18（PostgreSQL・MySQL。REFERENCE 0・1・3・5 個と同じ参照先 2 項目で借りる回数と文の数〈MySQL の一般ログ〉、LOOKUP だけ・VIEW・用途なしで借りない、T-C2〈一覧 2 文・詳細 1 文〉、応答しない対象DB を待つ間に内部DB を持たない）。失敗 0。
  - `ReferenceTimeoutIT` 10（3種類。文の上限の 503 TIMEOUT、全体の上限 2 秒の絞り、対象DB に無いラベルのカラムで 409 MISMATCH、応答しない対象DB で 503 UNAVAILABLE、応答とログに例外の文・接続先・資格情報が無い）。失敗 0。1回目は確かめの書き方の誤り（取り込んだ出力の全体に Flyway の「does not exist yet」が入る）で 6 件落ち、テストの側を直した。
  - 計画の Step 12 のコマンド（`*Reference*IT` と `*BusinessDataRead*IT`）: 8 クラス・120 件（既存の `*BusinessDataReadIT` 54 件は変えずに通過。SKIPPED 5 は前からの DB の種類ごとの `assumeTrue`）。約 1 分 34 秒。
- 境界テスト（ArchUnit）・`backend/config/spotbugs-exclude.xml`・移行ファイル・DSL の JSON Schema・lockfile に差は無い。`packagesJudgedByTotal` の7パッケージに触れていない。
- 回帰の確かめ（verify ではない）: `:backend:test --tests 'cherry.mastersmith.businessdata.*' --tests 'cherry.mastersmith.targetdb.*' --tests '*ArchitectureTest'` と `:backend:integrationTest --tests 'cherry.mastersmith.businessdata.*'` を一度に流し、単体 76 クラス 714 件・失敗 0、結合 25 クラス 282 件・失敗 0（SKIPPED 10 は前からの DB の種類ごとの `assumeTrue`）。約 7 分。書き込みの結合テスト（解決の口を共有する U2 の経路）も通った。
- Step 7〜12 にチェックを付けた。コミットはしていない（依頼の決まり）。

## Step 13（B1 の確かめ）— 指揮役の記録

- コミットの区切りの差: 計画の C3（repository）だけでは、`RowSearchResult.Rows` を足した後の業務処理の `switch` が網羅でなくなりコンパイルが通らないため、C3 と C4 を1つのコミット（18bc675）にまとめた。中身は計画どおり。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の環境変数・`caffeinate -dims`）: 成功、23分28秒。単体 2,703 件・結合 1,439 件、失敗 0。SKIPPED 10 件は DB の種類ごとの前からの `assumeTrue`（対象DB の SKIPPED は無い）。流した間に Sleep・DarkWake は無かった。
- E2E の1回目: 256 件中 1 件失敗（180 の手順 6「見本と本物の応答の形」）。定義の応答のカラムに足した `reference`（参照でないカラムは null）が画面の E2E の見本に無かったため。依頼者の決定 A で、画面の型 `ColumnDefinition` に任意の `reference?: unknown` を、E2E の見本 `fixtureColumn` に `reference: null` を足した（計画との差。本来は U3 の作業）。
- その後の `verify`（画面の部品だけが流れ直る）で `businessDataFixtures.test.ts` の2件が失敗。画面の読み込みの部品 `parseValidation` が `reference` を落とすため。読み込みの部品で `reference` を届いたまま写す形にした（画面はまだ使わない。中身の確かめは U3 で足す）。直した後の `verify` は成功（3分16秒、画面のテスト 1,977 件）。
- E2E の全件をもう一度: 256 件すべて通過（13.1 分）。流した間に Sleep・DarkWake は無かった。
