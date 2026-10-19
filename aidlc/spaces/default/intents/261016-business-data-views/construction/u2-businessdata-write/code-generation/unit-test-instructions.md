# 単体テストの手順 — U2 u2-businessdata-write（Bolt B4）

この文書は、U2 のテストの書き方と走らせ方を決める。Test Strategy は standard（部品ごとに 5〜8 件、要所の境目に結合テスト）、methodology は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）。手順の順は `code-generation-plan.md` の 5節の Step に従う。

## 1. 道具と設定

| 用途 | 道具 | 設定の置き場 |
|---|---|---|
| 単体テスト（名前が `Test` で終わるクラス） | JUnit 5・AssertJ・Mockito | `backend/build.gradle.kts` の `tasks.test`（既存。変えない） |
| 結合テスト（名前が `IT` で終わるクラス） | Spring Boot のテスト・Testcontainers（版を固定したイメージ）・組み込みの H2（内部DB） | `backend/build.gradle.kts` の `integrationTest`（既存。変えない） |
| 性質ベースのテスト | jqwik 1.10.2（既存の依存） | 失敗の詳細と乱数の種は、既存の `testLogging` の設定で出力に残る |
| 構造の検査 | ArchUnit（既存の依存） | — |
| 静的解析 | SpotBugs ＋ FindSecBugs（既存の関門。`SQL_` は priority によらず止める） | `backend/config/spotbugs-exclude.xml`（計画の A3 で除外を広げる） |
| 対象DB のイメージ | `targetdb/testsupport/TargetDbImages`（MySQL 26.7.0・MariaDB 13.0.2・PostgreSQL 18.6 のダイジェスト） | 変えない |
| 負荷の試験の台本の確かめ | k6（既存の `perf/` の使い方） | `perf/k6/scenarios.js` |

- 新しい依存は足さない（`tech-stack-decisions.md`）。テストの設定のファイル（`build.gradle.kts`）も変えない。
- テストの JVM は既存の設定のまま（`user.timezone=Asia/Tokyo`・`java.net.preferIPv4Stack=true`・`h2.compactThreads=1`）。JVM の既定のタイムゾーンを変える確かめ（NFR3.6）は、テストの中でアプリを起動するときの引数で渡す（JVM の設定は変えない）。
- テストの説明文（`@DisplayName`）は英語。コメントは日本語。テストのファイルの先頭にも Apache License 2.0 のヘッダー（`/* ... */`）を置く。
- 置き場は `backend/src/test/java/cherry/mastersmith/` の下で、対象と同じパッケージにする。テストの手伝いは `businessdata/testsupport`・`targetdb/testsupport`・`audit/testsupport` に置く。

## 2. 走らせる前の準備（毎回）

コンテナの実行環境（colima）が動いていることを確かめ、README の2つの変数をシェルに渡す（`project.md` の学び。渡さないと対象DB のテストが SKIPPED になる）。

```bash
colima status
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- コンテナの実行環境が無いと、対象DB のテストのクラスは WARN を出して飛ばされる。飛ばした状態の結果では統合しない。
- 数分を超える実行（結合テストの全体・verify・E2E・k6）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる。入っていたら結果を使わずに流し直す。
- 配備した環境（アプリ・見本の PG・`.env`・`.env.targetdb`）には触れない。テストの資格情報は、テストの中で作る乱数の値だけを使う。

## 3. この単位のテストの走らせ方

どのコマンドも、この単位が足した・変えたテストのクラスだけに絞っている（プロジェクトの全体のテストは、Step 29 の `verify` でだけ流す）。プロジェクトのルートで流す。

### 3.1 走らせ方の確かめ（Step 1。最初のテストの Step の前）

既存の `targetdb`・`businessdata`・`audit` の単体テストが通り、絞り込みの形が動くことを確かめる。

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.*' --tests 'cherry.mastersmith.businessdata.domain.*' --tests 'cherry.mastersmith.audit.domain.AuditEventTest'
```

### 3.2 層ごとのコマンド

| 層（Step） | コマンド |
|---|---|
| 設定の層（Step 3、単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.*' --tests 'cherry.mastersmith.targetdb.service.TargetSchemaReaderTest' --tests 'cherry.mastersmith.targetdb.service.JdbcTargetReadAccessTest'` |
| 設定の層（Step 3、結合。名前つきの管理者の IT を先に） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.config.TargetWriteTransactionManagerInjectionIT'` の後に `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.config.TargetDbStartupIT' --tests 'cherry.mastersmith.targetdb.config.TargetDbSecretLeakIT'` |
| 書き込みの口（Step 6） | `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.domain.TargetWriteSessionTest' --tests 'cherry.mastersmith.targetdb.domain.TargetWriteOutcomeTest' --tests 'cherry.mastersmith.targetdb.domain.WriteStepTest' --tests 'cherry.mastersmith.targetdb.service.JdbcTargetWriteAccessTest' --tests 'cherry.mastersmith.targetdb.TargetDbBoundaryArchitectureTest'` |
| domain の層（Step 8） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.*'` |
| repository の層（Step 10） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.*'` の後に `./gradlew :backend:spotbugsMain :backend:spotbugsGate` |
| 監査（Step 12、単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.audit.domain.AuditBusinessDataEventFactoryTest' --tests 'cherry.mastersmith.audit.domain.AuditEventTest' --tests 'cherry.mastersmith.audit.service.AuditBusinessDataEventListenerTest' --tests 'cherry.mastersmith.audit.service.AuditEventListenerTest' --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest' --tests 'cherry.mastersmith.businessdata.BusinessDataBoundaryArchitectureTest'` |
| 監査（Step 12、結合） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.audit.domain.AuditSchemaIT' --tests 'cherry.mastersmith.audit.domain.AuditMigrationCompatibilityIT' --tests 'cherry.mastersmith.audit.service.AuditSecretLeakIT' --tests 'cherry.mastersmith.audit.service.AuditWriteFailureIT'` |
| service の層（Step 14） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.*'` |
| web の層（Step 16、単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.web.*' --tests 'cherry.mastersmith.ApiAccessArchitectureTest'` |
| web の層（Step 16、結合） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT' --tests 'cherry.mastersmith.common.error.service.ProblemTypeDuplicateStartupIT'` |
| 3種類の対象DB の書き込み（Step 18） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*BusinessDataWriteIT'` |
| 認可・IDOR（Step 19） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteAuthorizationApiIT'` |
| 本文と値の長さ（Step 20） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteRequestLimitIT'` |
| 時間切れ・設定の欠け（Step 21） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteTimeoutIT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteNotConfiguredIT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataTimeoutIT'` |
| 監査（Step 22） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteAuditIT'` |
| 漏えい（Step 23） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteSecretLeakIT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteTraceExposureIT'` |
| 接続の数（Step 24） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataConnectionUsageIT'` |
| 構造の検査と静的解析（Step 25） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.BusinessDataBoundaryArchitectureTest' --tests 'cherry.mastersmith.targetdb.TargetDbBoundaryArchitectureTest' --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.ApiAccessArchitectureTest'` の後に `./gradlew :backend:spotbugsMain :backend:spotbugsGate` |
| 見本の初期化の台本（Step 26） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.TargetDbSampleInitScriptIT'` |
| 性質ベース（Step 8 の中） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.WriteRequestValidatorPropertyTest' --tests 'cherry.mastersmith.businessdata.domain.DeadlinePatternMatcherPropertyTest'` |

### 3.3 この単位のテストをまとめて流す（Build and Test でも使う）

```bash
# 単体テスト（U2 が足した・変えたテストのクラス）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.businessdata.*' \
  --tests 'cherry.mastersmith.targetdb.*' \
  --tests 'cherry.mastersmith.audit.domain.AuditBusinessDataEventFactoryTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventTest' \
  --tests 'cherry.mastersmith.audit.service.AuditBusinessDataEventListenerTest' \
  --tests 'cherry.mastersmith.audit.service.AuditEventListenerTest' \
  --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessArchitectureTest'

# 結合テスト（3種類の対象DB を含む。caffeinate -dims で包む）
caffeinate -dims ./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.businessdata.*' \
  --tests 'cherry.mastersmith.targetdb.*' \
  --tests 'cherry.mastersmith.audit.domain.AuditSchemaIT' \
  --tests 'cherry.mastersmith.audit.domain.AuditMigrationCompatibilityIT' \
  --tests 'cherry.mastersmith.audit.service.AuditSecretLeakIT' \
  --tests 'cherry.mastersmith.audit.service.AuditWriteFailureIT' \
  --tests 'cherry.mastersmith.ApiAccessConsistencyIT'
```

- `businessdata.*` と `targetdb.*` には U1 のテストも入る。U2 は U1 の型と読み取りの経路（D5）を変えるため、まとめて流して U1 の振る舞いが崩れていないことも確かめる。
- 結果は `backend/build/test-results/test/` と `backend/build/test-results/integrationTest/` の XML で確かめる。テストが UP-TO-DATE で飛ばされたときは、件数を報告する前に `:backend:cleanTest`・`:backend:cleanIntegrationTest` を付けて流し直す（`project.md` の学び）。
- 負荷の試験の台本の確かめ（Step 27）は単体テストではないが、同じく単位に絞って流す: `k6 inspect --include-system-env-vars -e SCENARIO=<場面の名前> perf/k6/scenarios.js`（場面ごとに1回。場面の名前は `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`・`bizCreateLarge`、あわせて書き込みの前提を足した `bizPrepare`）。

## 4. 部品ごとのテストと件数の目安

standard の目安（部品ごとに 5〜8 件）に、team.md の業務データの必須のテストを足す。件数は目安で、生成で変えたときは code-summary.md に実数を書く。

### 4.1 単体テスト

| テストのクラス | 確かめること | 件数の目安 |
|---|---|---|
| `targetdb/config/TargetDbPropertiesTest`（直す） | 書き込みの既定値・空の値・toString の伏せ字、`QueryTimeout` の5つの上限、`longest()` は読み取りの4つ | 既存＋3 |
| `targetdb/config/TargetDbSettingsTest`（直す） | 書き込みの両方・片方（足りない項目の名前）・無し、読み取りが使えないときは書き込みも使えない | 既存＋4 |
| `targetdb/config/TargetDataSourceConfigTest`（直す） | 読み取りのプールの `connectionInitSql`（MySQL・MariaDB だけ。D5）、読み取りの通信の待ちが書き込みの上限で変わらない、片方だけの WARN | 既存＋3 |
| `targetdb/config/TargetWriteDataSourceConfigTest`（新規） | 通信の待ち = 書き込みの上限 + 5 秒（上限を変えたとき）、`useAffectedRows=false`、LOAD DATA LOCAL の無効、読み取り専用でない、プールの名前と上限、`connectionInitSql` の定数、資格情報が URL に入らない | 6〜8 |
| `targetdb/domain/TargetWriteSessionTest`・`TargetWriteOutcomeTest`・`WriteStepTest`（新規） | 必須の値、toString に接続・値・メッセージが出ない | 合わせて 6〜8 |
| `targetdb/service/JdbcTargetWriteAccessTest`（新規） | 確定・巻き戻し・セーブポイントへ戻して確かめる・設定が無い・借りる失敗・確定の失敗（段が載る）・処理の非検査の例外・捨てる処理が管理者の後・接続が戻り値に出ない | 8 |
| `businessdata/domain/WriteValueTypesTest`（新規） | `WriteRequest`・`WriteValues`・`TypedValue`・`BusinessDataChangedEvent` の伏せ字の toString、変更できない | 5〜6 |
| `businessdata/domain/ColumnWriteRulesFactoryTest`（新規） | BR3.6 の表、`lookup` → `text`、長さの上限（DSL・DB・10,000 字の小さい方）、`nullable` はすべてのカラム、書けないカラムの既定 | 7〜8 |
| `businessdata/domain/WriteRequestValidatorTest`（新規） | 各理由、長さ・桁・範囲・秒の端数の境界、10,000 と 10,001 字、主キーの 1,000 と 1,001 字、サロゲートペア、DB 由来の必須、空の更新、時差の3つの形 | 8 を超えてよい（team.md の必須の境界のため） |
| `businessdata/domain/DeadlinePatternMatcherTest`（新規） | 値ごと・合計の締め切りの境界を注入した時計で、`((a+)+)+c`・`(.*a){12}` と `a` の 30 字＋`!`、`(a+)+$` は重くない式、全体一致、toString が `***` | 6〜8 |
| `businessdata/domain/DeadlinePatternTimingTest`（新規） | NFR1.6: 100 カラムの検証の全体が実時間で 400 ms 以内 | 1〜2 |
| `businessdata/domain/BusinessDataAuditReasonTest`（新規） | 監査に残す応答の code のすべてに写しがある・写しが 32 字以内・残さない code に写しが無い（BR11.6） | 3〜4 |
| `businessdata/domain/AuditDetailBuilderTest`（新規） | ちょうど 16,384 字・超え（`keyTruncated`）・複合の主キー・主キーの無い作成 | 4〜5 |
| `businessdata/domain/ColumnDefinitionWriteItemsConsistencyTest`（新規） | 定義の項目とサーバーの検証の境界の値が同じ（BR14.2） | 3〜4 |
| `businessdata/domain/TableAccessPolicyTest`（直す） | 検証の材料が入る（274行の直し） | 既存＋2 |
| `businessdata/repository/RowWriteSqlBuilderTest`（新規） | 引用・修飾・バインドの位置、事前の問い合わせと確かめの1文、自分の行を除く条件、照合を通らない名前では文を作らない | 7〜8 |
| `businessdata/repository/RowSqlExecutorTest`（直す） | 書き込みのメソッドの結果の型、非検査の例外、鍵の読み方、読み取りの `SET` を消した直し | 既存＋6 |
| `businessdata/repository/RowSqlFailureClassifierTest`（直す） | 書き込みの表のすべての行（パラメーターの表）、段ごと、競合（D1）・形の誤り（D2）・確定の段（D3）・違反の後の段（D4）、MariaDB の 1213 の後の 1305 | 既存＋表の行の数 |
| `businessdata/repository/RowDialectTest`・`ColumnValueCodecTest`（直す） | 書き込みの形と型つきのバインド、`needsUtcSession` の確かめを消す | 既存＋3 ずつ |
| `businessdata/service/BusinessDataWriteServiceTest`（新規） | 判定の順 ①〜⑩、⑥ の監査の範囲、口を1回だけ呼ぶ、出来事は口から戻った後、拒んだときは口を呼ばない、ログの1か所とキー、`UNKNOWN` | 8 を超えてよい（判定の順のため） |
| `businessdata/service/BusinessDataProblemTypeCatalogTest`・`TableAccessResolverTest`（直す） | 5つの code、検証の材料 | 既存＋2 ずつ |
| `businessdata/web/BusinessDataWriteControllerTest`（新規） | 3本の成功・各拒否の code と状態コード・`fieldErrors` の形・応答に値が無い・要求の toString | 7〜8 |
| `businessdata/web/BusinessDataReadControllerTest`（直す） | 定義の応答に検証の材料が出る（126行の直し） | 既存＋1 |
| `audit/domain/AuditBusinessDataEventFactoryTest`（新規） | 3つの種類・9つの理由・`UNKNOWN`・3列・`detail` | 5〜6 |
| `audit/service/AuditBusinessDataEventListenerTest`（新規） | 写しの網羅、すべての値の 32 字以内、失敗の ERROR に `detail`・主キー・cause が無い | 5〜6 |
| `businessdata/BusinessDataBoundaryArchitectureTest`（直す） | A1・A2 と足す決まり | 既存＋2 |

### 4.2 性質ベースのテスト（jqwik）

| テストのクラス | 性質 |
|---|---|
| `businessdata/domain/WriteRequestValidatorPropertyTest` | 長さ・桁・範囲の内側は受け付け、外側は拒む。書けないカラムは必ず拒む（NFR7.6） |
| `businessdata/domain/DeadlinePatternMatcherPropertyTest` | 任意の値と式で、合計の締め切りを超えて照合が続かない（注入した時計で決める） |

- 失敗したときの乱数の種は、既存の `testLogging` の設定で出力に残る。再現は `@Property(seed = …)` で行い、種を generation-notes.md に記録する。

### 4.3 結合テスト

| テストのクラス | 確かめること |
|---|---|
| `targetdb/config/TargetWriteTransactionManagerInjectionIT`（新規。最初に通す） | 型で引ける管理者と DataSource が内部DB の1つだけ、既存の `@Transactional` が内部DB の管理者で確定、書き込みの2つの Bean は名前でだけ引ける、設定が無いと作られない |
| `targetdb/config/TargetDbStartupIT`・`TargetDbSecretLeakIT`（直す） | 書き込みの両方・片方・無しで起動を続ける、WARN は項目の名前だけ、資格情報が出ない |
| `targetdb/TargetDbSampleInitScriptIT`（新規、3種類） | 実際の台本での初めての起動（DML は通り DDL・一時の表は拒まれ、読み取りのアカウントでは書けない）、01・02 だけで起動して 03 を `exec` で2回、パスワードの当て直し |
| `businessdata/web/AbstractBusinessDataWriteIT` と `Postgres`・`Mysql`・`MariadbBusinessDataWriteIT`（新規） | 計画の Step 18 の一覧（作成・更新・削除、件数、一意・外部キー・NOT NULL・CHECK などの code とカラム、競合と形の誤り、境界の値、時差、秒の端数、注入の文字、生成された鍵、アカウントの使い分け）。書いた後の値は別の接続で読み直す |
| `businessdata/web/BusinessDataWriteAuthorizationApiIT`（新規） | 401・403・200 の表、作業ロールと割り当ての切り替え、停止中の利用者、判定の順、IDOR・一括代入で状態が変わらず文が出ない |
| `businessdata/web/BusinessDataWriteRequestLimitIT`（新規） | 4 MiB・64 KB の境界、4 バイトの文字の最大の本文、10,000・1,000 字の境界、応答に値が無い |
| `businessdata/web/BusinessDataWriteTimeoutIT`（新規） | 答えない ServerSocket・接続できない番号・中継で書き込みの文と確定と違反の後の段で止める、25 秒以内、DB ごとの実測、次の要求が通る |
| `businessdata/web/BusinessDataTimeoutIT`（直す） | D5 で MySQL の通すまとまりの数を直した後も 25 秒以内に 503 TIMEOUT |
| `businessdata/web/BusinessDataWriteNotConfiguredIT`（新規） | 409 `BUSINESS_WRITE_NOT_CONFIGURED`、権限の無い人には 403、読み取りは動く、`writeConfigured: false` |
| `businessdata/web/BusinessDataWriteAuditIT`（新規） | 成功と失敗の監査の行、残さない拒否、⑥ の範囲、`detail` の切り詰め、`UNKNOWN`、監査の書き込みの失敗の ERROR |
| `businessdata/web/BusinessDataWriteSecretLeakIT`・`BusinessDataWriteTraceExposureIT`（新規） | TRACE のログ・誤りの応答・トレースの属性に値・主キー・接続先・例外のメッセージが無い |
| `businessdata/web/BusinessDataConnectionUsageIT`（直す） | 作成・更新・削除の3行、書き込みの DataSource の列 |
| `audit/domain/AuditSchemaIT`・`AuditMigrationCompatibilityIT`（直す）、`audit/service/AuditSecretLeakIT`（直す） | V13 の3列、前の版との互換、列の一覧 |

- 対象DB へ書き込むテストは、アプリのトランザクションの巻き戻しに頼らない。テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。名前には実行ごとに一意の値を入れ、前の実行の残りに頼らない（team.md）。
- 足す IT は、`targetdb/testsupport/ExtensionOrderArchitectureTest` の決まりの順に従う。

## 5. カバレッジの目標

- バックエンドの全体と、パッケージごとに 行 80%・分岐 70% 以上（team.md）。対象は `businessdata.*`・`targetdb.*`・`audit.domain`・`audit.service`。`businessdata.web` は U1 の後の分岐が 74.3% で下限に近いため、書き込みの controller の `switch` の場合をすべて単体テストで通す。
- `packagesJudgedByTotal` の7パッケージ（`audit.repository`・`common.web` を含む）の本体のソースには手を入れない。手を入れたら、テストを足して下限を満たし、一覧から外す。
- 計測の除外は増やさない。
- 実測は `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（Step 29）で行い、`backend/build/reports/jacoco/test/jacocoTestReport.xml` から実測の数字だけを記録する。

## 6. モックと偽物の使い方

- 対象DB の振る舞い（SQL・制約の違反・件数・時差・権限）は、モックで代用せず、版を固定したイメージのコンテナの実際の MySQL・MariaDB・PostgreSQL で確かめる（`project.md` の Mandated）。
- 単体テストでは、業務処理の層の口（権限の解決の口・書き込みの口・出来事の発行）を、`businessdata/testsupport` の偽物（U1 の `FakeBusinessDataPorts` に書き込みの口を足す）で置き換える。書き込みの口の単体テスト（`JdbcTargetWriteAccessTest`）は、組み込みの H2 のメモリの DB を書き込みの DataSource に見立てて、確定・巻き戻し・セーブポイントを実際に動かす（H2 は対象DB の代わりではなく、口の確定・巻き戻しの手順の確かめだけに使う）。
- 応答しない対象DB は、テストの中で開いた ServerSocket（`targetdb/testsupport/SilentServer`）と、つないだ後に止まる中継（`businessdata/testsupport/StallingRelay`）で作る。本物の DB の遅延ではない（`project.md` の学び）。
- 時刻は注入した時計で動かし、`sleep` と実時刻に頼らない（pattern の締め切り）。NFR1.6 の実時間の確かめだけは実時間で測る。
- 監査の書き込みの失敗は、既存の `audit/testsupport/FailingAuditEventRepositoryConfig` で起こす。
- 接続の数は、`businessdata/testsupport/CountingDataSource` で `dataSource`・`targetDataSource`・`targetWriteDataSource` を包んで数える。
- 競合（D1）は、テストの管理者の接続で行を排他して書き込みのアカウントの待ちの上限を短くした形と、トリガーで番号を出す形で起こす。

## 7. テストのデータ

- 値はすべて架空の値にする。メールアドレスは `example.com` などの予約のドメインだけ。実在しそうな氏名・宛先を置かない。テストデータは日本語でよい。
- 対象DB の資格情報（管理者・読み取り・書き込み）は、テストの中で作る乱数の値だけにし、ログ・報告に出さない。
- 書き込みの表は、テストのクラスごとのスキーマに `businessdata/testsupport/BusinessDataWriteTables` が作る（自動の採番の主キー（PG は `serial` と `identity`）・複合の主キー・一意の制約・外部キー（`ON DELETE CASCADE` を含む）・NOT NULL・CHECK・更新できないビュー・主キーの無い表・境界の値の型・競合のトリガー）。
- 境界の値: `BIGINT` の上限と下限、`DECIMAL(38,10)` の桁の多い値、NULL、秒の端数（DB の型の精度ちょうどと1桁超え）、10,000 字と 10,001 字（サロゲートペアを含む）、主キーの 1,000 字と 1,001 字、4 MiB ちょうどと1 バイト超えの本文、`detail` の 16,384 字ちょうどと超え。
- 注入の文字: `'`・`"`・`;`・`--`・バッククォート・改行・`<script>`（値として保存され、識別子としては照合で拒まれる）。
- 負荷の試験の種（Step 27）は `perf/biz/make-biz-seed.sh` の決めた式から作り、乱数を使わない（team.md）。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/code-generation/code-generation-plan.md`（5節の Step・10節の D1〜D8）
- この単位の設計: `construction/u2-businessdata-write/nfr-requirements/`（NFR1.6・NFR2.6・NFR3.5・NFR3.6・NFR4.1〜NFR4.16・NFR7.4〜NFR7.6・NFR8.4）、`nfr-design/`（reliability-design.md・security-design.md・performance-design.md の試しの結果）、`infrastructure-design/cicd-pipeline.md`（2節の段 5・6、7節）
- U1 の手順: `construction/u1-businessdata-read/code-generation/unit-test-instructions.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（学び・Mandated）

## Assumptions & Open Questions

- [assumption] テストのクラスの名前は計画の仮の名前で、生成で変えたときは code-summary.md に実際の名前とコマンドを書く。
- [assumption] D1〜D8 は A の形で書いた。A 以外になったら、4.1・4.3 の該当の行を直す。
- [assumption] `JdbcTargetWriteAccessTest` で組み込みの H2 を書き込みの DataSource に見立てるのは、口の確定・巻き戻し・セーブポイントの手順だけを確かめるためで、対象DB の振る舞いの確かめには使わない（それは 4.3 の3種類の結合テスト）。
