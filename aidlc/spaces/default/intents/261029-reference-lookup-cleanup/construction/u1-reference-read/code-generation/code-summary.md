# コード生成のまとめ — U1 u1-reference-read

単位 U1（service・XL）を、計画 `code-generation-plan.md` のとおり2つの Bolt（B1・B2）に分けて作り、どちらも `develop` へ squash で統合した。材料は `generation-notes.md`（生成の担当と指揮役の記録）と、`git diff dbb3430 b048ec1`（`aidlc/` を除く）。

| Bolt | 統合のコミット | 作業ブランチの上のコミット | 中身 |
|---|---|---|---|
| B1 | 549d38b | 839013b（C1）・cb8c87d（C2）・18bc675（C3＋C4） | 2つの設定と `ReadPurpose.REFERENCE`、domain の判定と継ぎ目の型、行の SQL の部品への参照の読み取りの指定、定義の取得の `purpose` と `reference` |
| B2 | b048ec1 | 385b244（C1＋C2）・6e54229（C3）・fde2b2e（C4） | 検索とラベルの API、U2 向けの `check`・`countState`、認可・同じ応答・漏えい・トレース・接続の数・時間切れの結合テスト、perf の種・DSL・k6 の場面 |

## 1. Bolt ごとに作ったもの

### B1（U1 前半）

- **targetdb**: `ReadPurpose.REFERENCE`。`TargetDbProperties` に `QueryTimeout.reference`（既定 5s、`longest()` に入る）と `referenceReadTotal`（既定 10s）。`TargetDbSettings` で 1 秒未満を `Invalid`（項目名だけ WARN、起動は続く）、`Usable.queryTimeout` の `case REFERENCE` と `Usable.referenceReadTotal()`。`TargetReadAccess.referenceReadTotal()`（`withRead` の work の中でだけ呼ぶ。`Usable` でないときは `IllegalStateException`。NFR 設計 U1 R-05）。`application.yaml`・`.env.example`・`README.md` の環境変数の表。
- **businessdata.domain**: `DefinitionPurpose`・`ReferenceStatus`・`ReferenceTarget`・`ReferenceOption`・`PickerSearchField`・`PickerListColumn`・`PickerInfo`・`ReferenceField`・`ReferenceReadSpec`・`ReferencePolicy`（`accessOf`・`usableSearchFields`・`distinctByValue`・`fixedOrder`・`sameReferenceValue` に、計画の幅の中で `pickerOf`・`optionsSpec` を足した）・`ReferenceReadDeadline`・`ReferenceReadFailure`・`ReferenceFailureReason`。
- **repository**: `RowDialect` に既定のメソッド `lengthWithin`・`sortExpression`（両方の方言がこの既定を使う）。`RowSqlBuilder` に `referenceWhere`・`referencePage`。`RowSqlExecutor.search` に `Optional<ReferenceReadSpec>`（メソッドの数は同じ。`prepareStatement` は `search` の本体の中だけ）。`RowSearchResult.Rows`。
- **service**: `TableAccessResolution.Resolved` が権限の写しを持ち、`TableAccessResolver.referenceView` で同じ写しから参照先の判定の材料を作る。`ReferenceLookup.definitionReferences`（借りるのは1回、同じ参照先は1回、201 行目で `TOO_MANY`、失敗の後は読まない、診断の WARN を1行、`Clock` は既存の Bean）。`BusinessDataReadService.definition` が `purpose` を受ける。`DefinitionResult.Found` に用途と参照の項目。
- **web**: `DefinitionRequest` の任意の `purpose`（列挙に無い値は DSL を見る前に 400）、`ColumnDefinitionResponse.reference`（常に出す。`VIEW` と参照でないカラムは `null`）、`ReferenceFieldResponse`・`ReferenceOptionResponse`・`PickerInfoResponse`・`PickerFieldResponse`。D2: `CREATE`・`UPDATE` では LOOKUP のカラムの `formPart` を `lookup` で返す。`README.md` の定義の行。

### B2（U1 後半）

- **businessdata.domain**: `ReferenceValue`・`ReferenceCondition`・`ReferenceRowsQuery`（入れ子の `Mode { SEARCH, VALUE }`）・`ReferenceRow`・`ReferenceRows`・`ReferenceCheckResult`（`Found`・`NotFoundOrDenied`・`Failed(ReferenceReadFailure)`）・`ReferenceCountState`（`Available`・`TooMany`・`NoPermission`・`Failed(ReferenceReadFailure)`）・`ReferenceRowsRequestValidator`。`ReferenceReadSpec` に `picker`（21 行）・`label`（1 行）・`check`（201 行）の作り方、`ReferencePolicy` に `pickerSpec`・`labelSpec`・`checkSpec`。
- **repository**: 本体の変更なし（既存の `search` が条件の句の後に `IS NOT NULL` と長さの条件を足すため、値の一致と `CONTAINS` の既存の書き方がそのまま通る）。テストだけを足した。
- **service**: `ReferenceLookup.search`・`label`・`check`・`countState`、結果の型 `ReferenceRowsResult`（`Found`・`Denied`・`InvalidValue`・`Failed`）。検索・ラベルは文が1つのため全体の締め切りを使わず `REFERENCE` の文の上限だけで流す。`check`・`countState` は U2 が借りた接続を受け、WARN を出さない。
- **web**: `POST /api/tables/reference/rows`（既存の `BusinessDataReadController` に足した。クラスの `@ApiAccess(AUTHENTICATED)` に入る。計画の Q4: A）、`ReferenceRowsRequest`・`ReferenceConditionRequest`・`ReferenceRowsResponse`・`ReferenceRowResponse`、`BusinessDataPaths.REFERENCE_ROWS`、`BusinessDataWebConfig` の本文の上限 65,536 バイトの道、`README.md` の読み取りの API の表（3本 → 4本）。
- **perf**: `perf/biz/make-biz-seed.sh`（`biz_code200`・`biz_code201`・`biz_refsrc200`・`biz_refsrc201`・`biz_refpick`）、`perf/make-biz-dsl.mjs`（REFERENCE・LOOKUP の options）、`perf/k6/scenarios.js`（`refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains`、閾値の `REF_P95_LIMIT_MS`）、`perf/k6/thresholds-test.sh`、`perf/README.md` の「他テーブル参照の読み取りの場面」の節（プールの上限 5 の行、`hikaricp.connections.max` が 5 でなければ中止する条件、比べる相手、流す順、種の確かめ）。

### 変えていないもの（計画 3.3節・3.4節・1節）

- 境界テスト（ArchUnit）と `backend/config/spotbugs-exclude.xml` に差は無い。`spotbugsGate` に新しい `SQL_` の指摘は無い。`prepareStatement`・`createStatement` を呼ぶ場所は増えていない。
- 移行ファイル・DSL の JSON Schema・lockfile（`backend/gradle.lockfile`・`gradle/libs.versions.toml`・npm）に差は無い。新しい依存は無い。
- `packagesJudgedByTotal` の7パッケージに触れていない（一覧の変更・事前の実測は無し）。
- ダッシュボード（`mastersmith-overview.json`）と警報は変えていない（計画の Q2: A）。

## 2. 作った・変えたファイル

アプリのソース 99 ファイル（作成 52・変更 47・削除 0）。一覧は `source-manifest.json`。

| 置き場 | 作成 | 変更 |
|---|---|---|
| `backend/src/main/java/.../businessdata/domain/` | 21 | 0 |
| `backend/src/main/java/.../businessdata/repository/` | 0 | 4 |
| `backend/src/main/java/.../businessdata/service/` | 2 | 4 |
| `backend/src/main/java/.../businessdata/web/` | 8 | 6 |
| `backend/src/main/java/.../targetdb/`（config・domain・service） | 0 | 5 |
| `backend/src/main/resources/application.yaml` | 0 | 1 |
| `backend/src/test/java/`（businessdata・targetdb・common/testsupport） | 21 | 17 |
| `frontend/`（型・読み込みの部品・E2E の見本。4節の差） | 0 | 3 |
| `perf/` | 0 | 5 |
| `README.md`・`.env.example` | 0 | 2 |

## 3. テストと確かめの結果

すべて colima の環境変数（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）と `caffeinate -dims` を付けて流した。流した間に Sleep・DarkWake は無かった。

| 確かめ | B1 | B2 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 成功、23分28秒。単体 2,703 件・結合 1,439 件、失敗 0 | 成功、23分34秒。単体 2,748 件・結合 1,523 件、失敗 0 |
| SKIPPED | 10 件（DB の種類ごとの前からの `assumeTrue`。対象DB の SKIPPED は無い） | 同じ 10 件 |
| E2E（`./gradlew e2eTest` の全件） | 1回目 256 件中 1 件失敗（4節）→ 直した後 256 件すべて通過、13.1 分 | 256 件すべて通過、12.9 分 |
| CI（検査の job の時間） | run 41050809643（549d38b）: 1回目の試行で success、32分25秒（42 分の線の内）、再実行なし | run 41070279755（b048ec1）: 1回目の試行で success、34分49秒（42 分の線の内）、再実行なし。間の 9d1d2dd（記録だけ）の run 41063163854 も1回目で success（27分59秒） |

B1 の `verify` の後の画面の部品の流し直し（4節の直しの後）は成功、3分16秒（画面のテスト 1,977 件）。

### 3.1 この単位が足したテスト

| 層 | テスト | 件数 |
|---|---|---|
| domain | `ReferencePolicyTest`（16）・`ReferencePolicyPropertyTest`（jqwik 6）・`ReferenceReadDeadlineTest`（6）・`ReferenceReadDeadlinePropertyTest`（jqwik 1）・`ReferenceReadFailureTest`（10）・`ReferenceTypesTest`（7）・`ReferenceRowsRequestValidatorTest`（20）・`ReferenceRowsRequestValidatorPropertyTest`（jqwik 2） | 68 |
| repository | `RowDialectTest` に 2・`RowSqlExecutorTest` に 8 | 10 |
| service | `ReferenceLookupTest`（22）・`TableAccessResolverTest` に 1・`BusinessDataProblemTypeCatalogTest` に 1（T-C3） | 24 |
| web（単体） | `BusinessDataReadControllerTest` に 6 と `purpose` の分 | 6＋ |
| 結合 | `PostgresReferenceReadIT`・`MysqlReferenceReadIT`・`MariadbReferenceReadIT`（各 23）、`ReferenceConnectionUsageIT`（25）、`ReferenceTimeoutIT`（17）、`ReferenceAuthorizationApiIT`（24）、`ReferenceRequestLimitIT`（8）、`ReferenceSameResponseIT`（3）、`ReferenceSecretLeakIT`（1）、`ReferenceTraceExposureIT`（1） | 148 |

件数は `generation-notes.md` の各 Step の実行の記録から数えた（`verify` 全体の件数は上の表が実測）。jqwik の失敗は無く、記録する乱数の種は無い。

### 3.2 カバレッジ（B2 の `verify` の jacoco の報告、`backend/build/reports/jacoco/test/jacocoTestReport.xml`）

| パッケージ | 行 | 分岐 |
|---|---|---|
| `businessdata.domain` | 98.0% | 90.7% |
| `businessdata.repository` | 96.2% | 88.4% |
| `businessdata.service` | 95.6% | 88.6% |
| `businessdata.web` | 97.3% | 79.9% |
| `targetdb.config` | 100.0% | 99.1% |
| `targetdb.domain` | 100.0% | 97.2% |
| `targetdb.service` | 89.3% | 76.3% |
| 全体 | 97.6% | 91.2% |

どれも下限（行 80%・分岐 70%）を満たす。B1 の `verify` のパッケージごとの値は記録していない（`verify` のパッケージごとの関門は通った。計画 Step 13 の「値を記録する」との差）。

### 3.3 この段で流していないテスト・確かめ

- **k6 の5つの場面**（`refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains`）: 種の流し込みも場面の実行もしていない。確かめたのは `bash -n`・`node --check`・場面ごとの `k6 inspect --include-system-env-vars`（`perf/versions.env` の `K6_IMAGE`）・`bash perf/k6/thresholds-test.sh`（12 件 ok）だけ。試し走りは Build and Test、3種類の判定は Performance Validation（計画のとおり）。
- **種の行の数の確かめ**（200・201・100,000 と目印）: 種を流していないため Build and Test。
- **全体の上限で止める経路（`budgetExceeded`）の結合テスト**: 時間に合否を預けないため、単体テスト（T-T3）だけで確かめた。
- **B2 の CI**: run 41070279755（b048ec1）: 1回目の試行で success、34分49秒（42 分の線の内）、再実行なし。間の 9d1d2dd（記録だけ）の run 41063163854 も1回目で success（27分59秒）。

## 4. 計画と承認済みの設計との差（依頼者の決定つき）

| # | 差 | 依頼者の決定 |
|---|---|---|
| 1 | `targetdb.domain.TargetSchemaResultTest` の期待の並びの最後に `ReadPurpose.REFERENCE` を足した。計画 4節の影響の一覧（`ReadPurpose` の行）に無いテストで、生成を止めて確かめた | 受け入れ（決定 A、テストだけの1行の変更。確かめの意図は同じ） |
| 2 | 全体の上限で止めた失敗の作り方を `ReferenceReadFailure.ofBudgetExceeded()` にした。承認済みの設計（U1 の `nfr-design/reliability-design.md` 5.2・`logical-components.md` 5節、U2 の `reliability-design.md` 1.2・2節・`observability-design.md`）は項目 `budgetExceeded` と作り方 `budgetExceeded()` を同じ名前で決めており、Java では両立しない。項目の名前 `budgetExceeded` は変えていない。設計の文書は書き換えていない | 受け入れ（決定 A で確定）。**U2 の計画とコードは `ofBudgetExceeded()` を使う** |
| 3 | コミットの区切り: B1 は計画の C3 と C4 を 18bc675 にまとめた（`RowSearchResult.Rows` を足すと業務処理の `switch` が網羅でなくなりコンパイルが通らないため）。B2 は C1 と C2 を 385b244 にまとめた（同じファイルに混ざり、分けるとコンパイルが通らないため）。中身は計画どおり | 各コミットの承認の場で受け入れ |
| 4 | 計画 Step 12 の `ReferenceTimeoutIT` は「`SilentServer` で 503 `BUSINESS_TARGET_DB_TIMEOUT`」と書くが、接続を受けて何も返さない相手は接続を借りる段で止まるため、既存の決まり（reliability-design.md 2節、既存の `BusinessDataTimeoutIT`）どおり 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる。TIMEOUT の経路は、3種類の DB の眠る関数を含むビューを参照先にして確かめた（文の上限 5 秒で 503 TIMEOUT、全体の上限 2 秒の起動で約 2 秒の 503 TIMEOUT）。作りは変えていない | 計画の文の誤りとして記録（設計の決まりに合わせた） |
| 5 | 画面の型 `ColumnDefinition`（`frontend/src/features/tables/businessDataTypes.ts`）に任意の `reference?: unknown`、E2E の見本 `fixtureColumn`（`frontend/e2e/support/businessDataFixtures.ts`）に `reference: null`、読み込みの部品 `parseValidation`（`businessDataParse.ts`）で `reference` を届いたまま写す形を足した。E2E 180 の「見本と本物の応答の形」の確かめが落ちたため。計画 1節の「frontend のソースは変えない」との差で、本来は U3 の作業 | 受け入れ（決定 A）。中身の型と読み取りの確かめは U3 |
| 6 | `common/testsupport/ApiEndpointCounts.MIN_ENDPOINTS` を 72 から 73 に上げた（計画 4節の一覧に無いテストの変更。ファイルの説明の決まりに従った）。`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` は変えずに通った | 受け入れ |
| 7 | `ReferenceTraceExposureIT` は、既存の `BusinessDataTraceExposureIT` と同じく、テストだけのスパンの集め手で終わったスパンの属性を集める形にした。計画の文「外部へ送る設定を有効にして」との差 | 受け入れ |
| 8 | Step 17（repository の B2 の分）は本体を変えず、テストだけを足した | 受け入れ |
| 9 | perf の参照元を3つの表（`biz_refsrc200`・`biz_refsrc201`・`biz_refpick`）に分けた。1つの表に REFERENCE を2つ置くと、`refDefinition200` と `refDefinition201` が同じ要求になり見分けられないため。基盤の設計 4.2 の「参照元の表」1つとの差（基盤の設計は名前と列の形を Code Generation で決めるとしている） | 受け入れ |
| 10 | k6 の場面・種・DSL を B2 で書いた（Bolt の計画・単位の定義の「Build and Test で書く」との差） | 計画の Q1: A で承認済み |
| 11 | B1 の `verify` のパッケージごとのカバレッジの値を記録していない（3.2節） | —（この段の記録の漏れ。B2 の値で補った） |

計画の幅の中で決めたこと（差ではないもの）は `generation-notes.md` の各 Step に残してある（`ReferencePolicy.pickerOf`・`optionsSpec`、`ReferenceLookup` の中に閉じた結果の型、`ReferenceTables` を既存の `BusinessDataSampleSchema` に足す形、テストの部品の小さな組など）。

### 4.1 記録のコミットの誤り

- B1 の統合と CI の記録のコミット 9d1d2dd（`aidlc/` の下だけの記録のコミット）を、積む前に提案せずに積んだ。`project.md` の「段の成果物の初稿と段の承認のとき以外は提案して承認を得る」に当たらない時点のコミットで、決まりとの差。依頼者に伝えてある。

## 5. 後の段に引き継ぐこと（持ち主つき）

| 持ち主 | 引き継ぐこと |
|---|---|
| Build and Test | 種の流し込みと行の数・目印の確かめ、k6 の5つの場面の試し走り（読み取りのプールの上限 5 と中止の条件を含む）。比べる相手の結果をどれにするかを依頼者に確かめる（`perf/README.md` は Intent 261016 の Performance Validation を挙げている）。既存の一覧での MySQL のエラー 1038 の再現の確かめ（NFR 設計 R-04）。`verify`・CI の時間の記録（NFR5.3）。下の 6節の文書の食い違いの扱い |
| Performance Validation | 3種類の対象DB での NFR2.1・NFR2.3・AC3.5.4 の判定（測るまで Unverified） |
| Observability Setup | ダッシュボードのパネル 46・47 と区画の説明（`uri` を確かめてから書く。NFR2.10）、警報 `ms-biz-target-db` が新しい道と定義の道の 503 で firing になることの確かめ（NFR3.8） |
| U2（B3） | `ReferenceLookup.check`・`countState` と `ReferenceReadFailure.ofBudgetExceeded()` を使う（4節の2） |
| U3 | 画面の型 `reference` の中身の型と読み取り（4節の5で `unknown` のまま写すだけにした）、画面の側の受け入れ基準（AC1.1.7・AC1.1.8・AC1.1.10・AC1.3.3・AC1.3.6・AC1.3.7・AC2.3.3・AC3.4.5） |

## 6. 文書と実装の読み合わせ（計画 Step 24）

`README.md`・`.env.example`・`backend/src/main/resources/application.yaml`・`perf/README.md` を実装と読み合わせた（文書もコードも直していない）。

- `README.md`・`.env.example`・`application.yaml`: 既定（5s・10s）、1 秒以上の範囲と 1 秒未満の扱い、`purpose` の既定 `VIEW`、検索 API の道・本文の上限（65,536 バイト）・値の 1,000 文字・ページ 1〜1,000,000・同じ 403・型の 400 は実装（`TargetDbProperties`・`DefinitionRequest`・`BusinessDataWebConfig`・`ReferenceRowsRequestValidator`・`ReferenceReadSpec`）と合う。古い文言（「読み取りの全体の上限は無い」「4つの上限」）の残りは無い。
- **食い違い（1件）**: `perf/README.md` の新しい節の「流す順」の (3)「深いページのピッカー（PostgreSQL で 1,000 ページ目）を参考に1回」に当たる手段が無い。`perf/k6/scenarios.js` のピッカーの場面は `page: 1` に固定で、ページを変える環境変数も無く、節の台本 `ref-run.sh` にもその手順が無い。判定の対象の外の参考の測りのため、Build and Test で、場面に足すか、手順から外すかを依頼者に確かめる。

## 7. 依頼者に確かめたいこと

- 4節の差は、11 を除いて生成の途中で依頼者の決定を得てある。新しく決めてもらう論点は無い。
- 残るのは、5節の Build and Test への引き継ぎ（比べる相手の結果の選び方）と、6節の `perf/README.md` の食い違いの扱いの2つ。どちらも Build and Test の質問で扱う見込み。
- B2 の CI は 3節の表に書いた。

B2 の CI: run 41070279755（b048ec1）: 1回目の試行で success、34分49秒（42 分の線の内）、再実行なし。間の 9d1d2dd（記録だけ）の run 41063163854 も1回目で success（27分59秒）
