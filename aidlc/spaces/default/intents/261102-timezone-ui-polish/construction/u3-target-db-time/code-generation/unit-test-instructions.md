# 単位のテストの手順 — u3-target-db-time

出典: コード生成の計画（`code-generation-plan.md`）、NFR Design（`nfr-design/security-design.md` の SD7・`logical-components.md` の LC7）、NFR Requirements（`tech-stack-decisions.md` の NFR2.1.x・`security-requirements.md` の NFR5.1.1）、`team.md` Testing Posture、`docs/target-db.md`（colima の環境変数）。書き方は前の単位（`construction/u1-time-zone-conversion/code-generation/unit-test-instructions.md`）にそろえる。

U3 は、対象DB の日時・時刻の読み書きと接続のタイムゾーン、既定の DSL の生成の直し（バックエンドの `src/main`、種類 library）。方法は test-after（層ごとに実装 → その層のテスト → 通ってから次へ）。テストの量は Standard（部品ごとに 5〜8 件）。

## テストの枠組みと設定

- 枠組み: JUnit 5・AssertJ・Mockito（単体）、Testcontainers（結合。MySQL・MariaDB・PostgreSQL の実物。版を固定したイメージ `TargetDbImages`）、jqwik（性質ベース。失敗時の乱数の種を出力に残す。`testLogging` が失敗の詳細をすべて出す）、ArchUnit。新しい依存は足さない。
- 置き場: `backend/src/test/java` に対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や対象DB のコンテナを使う結合テストは `XxxIT`（Gradle の `test` は `*Test`、`integrationTest` は `*IT` を流す）。テストの説明文（`@DisplayName`）は英語、テストデータは日本語でよい。
- 対象DB の結合テストは本番と同じ種類の DB を実物のコンテナで起動する（H2・モックで代用しない。`project.md` Mandated）。テスト（クラス）ごとに実行ごとに一意の名前のスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。書いた後の値は、アプリとは別の接続で読み直して確かめる。順序に依存させない。
- 時刻・タイムゾーン: 変換の部品は JVM の既定のタイムゾーンに結果を預けない。テストの JVM の `user.timezone` は `Asia/Tokyo`（`backend/build.gradle.kts`）。別の時差を確かめるテストは `TimeZone.setDefault` で明示に変え、`finally` で元に戻す。時計は注入し、`sleep` と実時刻に頼らない。
- 外へ出さない確かめ: 日時・時刻の値（読めない文字を含む）・接続情報・JDBC の例外のメッセージが、ログ・監査・トレースの属性・エラー応答に出ないことを、TRACE を有効にした `XxxSecretLeakIT` と、外へ送る設定を有効にした結合テストで確かめる（既存の形）。

## この単位だけを流すコマンド（リポジトリのルートで実行）

colima のコンテナの実行環境を使うため、シェルに次の2つを渡す（渡さないと対象DB のテストが SKIPPED になり、飛ばした状態では統合しない）。

```
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

単体テスト（コンテナ不要。この単位のクラスだけ）:

```
./gradlew :backend:test \
  --tests 'cherry.mastersmith.targetdb.config.TargetDataSourceConfigTest' \
  --tests 'cherry.mastersmith.targetdb.config.TargetWriteDataSourceConfigTest' \
  --tests 'cherry.mastersmith.businessdata.repository.*Test' \
  --tests 'cherry.mastersmith.businessdata.domain.*Test' \
  --tests 'cherry.mastersmith.dslmanage.generate.DslTreeBuilderTest' \
  --tests 'cherry.mastersmith.dslmanage.service.DslSummaryAndDiffTest'
```

1つのクラスだけを流すときは、`--tests 'cherry.mastersmith.businessdata.repository.TemporalCellCostTest'` のように完全なクラス名を書く。

結合テスト（対象DB のコンテナを起動。種類ごとに分けて流せる）:

```
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.businessdata.web.*BusinessDataTimeReadIT' \
  --tests 'cherry.mastersmith.businessdata.web.*BusinessDataTimeWriteIT' \
  --tests 'cherry.mastersmith.targetdb.config.*TargetDbTimeZoneIT' \
  --tests 'cherry.mastersmith.targetdb.config.TargetDbInitFailureIT' \
  --tests 'cherry.mastersmith.businessdata.web.BusinessDataConnectionUsageIT' \
  --tests 'cherry.mastersmith.businessdata.web.BusinessDataSecretLeakIT' \
  --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteSecretLeakIT' \
  --tests 'cherry.mastersmith.businessdata.web.BusinessDataTraceExposureIT' \
  --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteTraceExposureIT' \
  --tests 'cherry.mastersmith.businessdata.web.*BusinessDataReadIT' \
  --tests 'cherry.mastersmith.businessdata.web.*BusinessDataWriteIT' \
  --tests 'cherry.mastersmith.targetdb.repository.*SchemaQueriesIT' \
  --tests 'cherry.mastersmith.targetdb.service.*TargetSchemaReaderIT' \
  --tests 'cherry.mastersmith.dslmanage.generate.DefaultDslGenerator*IT'
```

PostgreSQL だけを流すときは `*Postgres*` に、MySQL・MariaDB だけなら `*Mysql*`・`*Mariadb*` に絞る。構造の検査（ArchUnit。**変更なしで通ること**を確かめる）:

```
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.BusinessDataBoundaryArchitectureTest' --tests 'cherry.mastersmith.targetdb.TargetDbBoundaryArchitectureTest' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest'
```

長い実行（結合テストの全部・`verify`）は `caffeinate -dims` で包み、流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。最初のテストを書く前（計画の Step 1）に、上の単体・結合のコマンドが変更の前の状態で実行できる（対象DB のテストが SKIPPED でない）ことを確かめる。

カバレッジの値（この単位のパッケージ）は、別のコマンドで読む（床の検証 `jacocoTestCoverageVerification` を含めない）:

```
./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport
```

読むのは `backend/build/reports/jacoco/test/jacocoTestReport.xml` の `targetdb.config`・`targetdb.repository`・`businessdata.repository`・`businessdata.domain`・`dslmanage.generate`。

## 期待する件数（Standard）

単体テスト:

| ファイル | 件数の目安 | 内容 |
|---|---|---|
| `TargetDataSourceConfigTest`・`TargetWriteDataSourceConfigTest`（直す） | 各 1〜2 増 | 種類 × 用途の初期化の文（6 通り）。MySQL・MariaDB の文が変わらない |
| `RowDialectTest`（直す） | 7〜8 | 型の名前の分類（桁つきの時差つきの型）、桁の取り出し（MySQL の `getPrecision` の導出・MariaDB と PostgreSQL の `getScale`・取れないとき null）、`comparisonValue`、番兵の見分け |
| `ColumnValueCodecTest`（直す） | 8〜10 | 桁 0・3・6 の整形、読めない日時・時刻が `Raw`、数・真偽値は `Unreadable` のまま、番兵、`timetz` の時差、明示のない桁は確かめない、`format`・`bind` |
| `CellValueTest`（新規） | 2〜3 | `toString` が値を伏せる |
| `TemporalCellsTest`（新規） | 6〜8 | 書式の使い回し・桁の収め方・ゼロの日時の近道・解析の失敗でも文字を残す・`user.timezone` 不変 |
| `TemporalCellCostTest`（新規） | 2 | 1 万のセルが合計 500 ミリ秒・平均 0.05 ミリ秒以内（通常の混在と、すべて読めない値の最悪。5 回の中央値） |
| `TemporalCellPropertyTest`（新規、jqwik） | 4 | 任意の文字列で例外が外へ出ない、往復、時差を持たない型の不変、桁の保持 |
| `RowSqlExecutorTest`（直す） | 4〜5 増 | 日時の読めない値で行が失敗しない、数の読めない値は行全体の失敗のまま、主キーが読めない行が出る、メタデータ `null`、メタデータの取得が結果ごとに 1 回 |
| `RowWriteSqlBuilderTest`・`RowSqlBuilder` のテスト（直す） | 各 1〜2 増 | 比較の値だけ UTC に直す |
| `ColumnWriteRulesFactoryTest`・`WriteRequestValidatorTest`・`WriteRequestValidatorPropertyTest`（直す） | 各 1〜2 増 | 桁を明示した列の境界（ちょうど・1 桁超）、明示のない列は確かめない |
| `DslTreeBuilderTest`（直す） | 6〜8 | 製品 × 桁 × 分類の表（既定と違う桁だけ書く・`DATE`・`interval` は書かない） |

単体テストは合わせて約 70 件（直す既存のテストの増減を含む）。

結合テスト（3 種類の対象DB の実物。同じ内容を MySQL・MariaDB・PostgreSQL の具象クラスで流す。PostgreSQL だけ・MySQL 系だけの項目は `assumeTrue` で分ける）:

| ファイル | 件数の目安（種類あたり） | 内容 |
|---|---|---|
| `AbstractBusinessDataTimeReadIT` ＋ 3 具象 | 6〜8 | 型 × 桁 0・3・6、日付の変わり目、読めない値（製品ごと）が読めないセルだけ DB の文字、番兵に近い正しい値、応答の項目が増えない |
| `AbstractBusinessDataTimeWriteIT` ＋ 3 具象 | 8〜10 | 作成・更新・検索・主キー・参照の値の往復（別の接続で読み直す）、桁の境界、DSL の桁 < DB の桁、時差の変換（分の端数を含む）、`timetz` の時差ごとの保存と非 UTC の主キー・検索、読めない形と SQL の文字、読めない主キーの行の操作の拒否、`timestamp` の範囲外（製品ごと）、既定の桁の列への端数 |
| `AbstractTargetDbTimeZoneIT` ＋ 3 具象 | 4〜5 | 読み取りと書き込みの両方のセッションが UTC、初期化は接続を作るとき 1 回、`user.timezone` を変えても不変 |
| `TargetDbInitFailureIT` | 3 | 初期化の失敗で借りる失敗（`UNAVAILABLE`）、応答・アプリのログ・Hikari の DEBUG のログに接続情報・例外のメッセージが出ない |
| `BusinessDataConnectionUsageIT`・`*SecretLeakIT`・`*TraceExposureIT`（直す） | 各 1〜2 増 | 日時の列を読んでも接続の数・問い合わせの数が同じ、値が出ない |
| `AbstractBusinessDataReadIT`・`AbstractSchemaQueriesIT`・`AbstractTargetSchemaReaderIT`・`AbstractDefaultDslGeneratorIT`・`DefaultDslGeneratorPostgresIT`（直す） | 期待の書き換えと 1〜3 増 | 読めない値・`timetz`・日時の列の `precision`・生成が既定と違う桁だけ書く |

結合テストは合わせて約 40 件（種類別の具象で数えると約 100 実行）。

## 期待するカバレッジ

- 全体の合計の下限（`team.md`）: 行 80%・分岐 70%（`backend/build.gradle.kts` の `jacocoTestCoverageVerification`）、パッケージごとにも同じ下限。**この下限は、プロジェクト全体のテストを流す Build and Test（と統合の前の `./gradlew verify`）が判定する**。この単位のコマンド（`:backend:test --tests …`・`:backend:integrationTest --tests …`）は床の検証を含まないため、床で終了コードが非 0 になることはない。床の検証を含むコマンド（`verify`・`jacocoTestCoverageVerification`）は、この単位のコマンドに混ぜず、単独で流す（その終了コードで後の手順を止めない）。下限の設定は変えない。
- 手を入れるパッケージ（`targetdb.config`・`targetdb.repository`・`businessdata.repository`・`businessdata.domain`・`dslmanage.generate`）はいずれも `packagesJudgedByTotal` の外で、パッケージごとの下限の対象。この単位のコードの目安は行・分岐とも 90% 以上（純粋な部品 `TemporalCells` と方言）。
- `packagesJudgedByTotal` の 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）の `src/main` には手を入れない。入れることになったら、テストを足して下限を満たし一覧から外す手順を計画に足す。

## 変換の費用の測り（NFR2.1.3・NFR2.1.4）

- 対象は `TemporalCells`（結果の 1 つのセルから値を作る純粋な部品。コンテナ・ネットワーク・モックの `ResultSet` を含めない）。固定の値 1 万個（時点・時差を持たない日時・日付を除く時刻・時差つきの時刻・端数 0・3・6 桁・読めないセルを混ぜる）と、**最悪の場合**（1 万個すべてが読めない値）の 2 通り。
- 暖機の後に `System.nanoTime()` の差を 5 回測り、中央値で、合計 500 ミリ秒以内・1 つあたりの平均 0.05 ミリ秒以内を判定する。暖機の前の 1 回目の値も出力に残す。失敗時は 5 回の値と環境（JDK の版・CPU）を出す。測った環境を `code-summary.md` に書く。
- 目標は緩めない。外れたときは原因を確かめて依頼者に諮る。落ちたときの扱いは `team.md` の「不安定なテストと CI の失敗」の決まりに従う（手元で再現した不安定は直すまで統合しない）。

## モック・スタブの方針

- 対象DB の結合テストは実物のコンテナを使い、H2 やモックで代用しない。
- 単体テストでは JDBC の `ResultSet`・`ResultSetMetaData`・`PreparedStatement`・`Connection` を Mockito で置き換えてよい（既存の `ColumnValueCodecTest`・`RowSqlExecutorTest` と同じ）。`getMetaData()` を設定しないモックは `null` を返し、桁なし（桁を固定しない）として扱われること自体も確かめる。
- 初期化の失敗は、本番のコードに口を足さず、本番の設定を作る部品が返す Hikari の設定のうち `connectionInitSql` だけを必ず失敗する文に差し替えたプールをテストの中で作って作る。
- `Clock`・`ZoneId` は注入する。`user.timezone` を変えるテストは `TimeZone.setDefault` を使い、必ず元に戻す。

## テストデータ

- 日時・時刻の値は固定の表（`@ParameterizedTest` の `@MethodSource`）で書く。日付の変わり目（UTC の 15:00 が日本時間の翌日 0:00）・月末・年末・うるう日（2024-02-29）・分の端数の時差（`+05:45`・`+09:30`・`-03:30`）・桁 0・3・6 を含める。読めない値は、MySQL・MariaDB のゼロの日時 `0000-00-00 00:00:00`・ゼロの日付・`838:59:59`・`-01:00:00`、PostgreSQL の `infinity`・`-infinity`・`24:00:00` と、番兵に近い正しい値（例 `2026-12-31 23:59:59.999999`）。
- 氏名・メールアドレスなどの個人に関する値は置かず、置く場合は架空の値と予約のドメイン（`example.com`）だけにする。対象DB の資格情報はテストの中に書かず、既存の `TargetDbFixture` が作るコンテナの値を使う。k6 の結果や `evidence/` に認証の値を置かない。
- 性質ベースの生成は、読めた日時（往復の性質）と任意の文字列（例外が外へ出ない性質）に分ける。失敗時は jqwik の種と反例を出し、手元で再現できるようにする。

## この単位に当たらないテスト

- E2E の流れ・画面のアクセシビリティの検査（axe）は、U3 が画面を持たないため足さない（業務データの日時の代表の流れ 1 本は u4-business-data-time が受け持つ）。ただし `backend/src/main` の業務データの機能と業務データを読む `targetdb` に触れるため、統合の前に既存の E2E（`./gradlew e2eTest`、見本の対象DB のコンテナが動いていること）を流して通す（`team.md`）。
- k6 の場面・perf の種は足さない。既存の業務データの読み取りの場面（`bizSearchFirst`・`bizDetail`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`）の p95 を U3 の統合の前後の版で並べる記録は Build and Test が行う（目標は `perf/README.md` と `perf/k6/scenarios.js` のまま変えない）。
- 初期化の失敗の見分け方の手順書は Incident Response が書く。
