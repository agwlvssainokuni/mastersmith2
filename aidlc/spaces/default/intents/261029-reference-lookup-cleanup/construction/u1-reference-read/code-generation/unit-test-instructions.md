# 単体テストの手順 — U1 u1-reference-read

Test Strategy は Standard（部品ごとに 5〜8 件の要の振る舞い）。方法は test-after（`code-generation-plan.md` 2節の Testing Contract が正）。どのコマンドもこの単位のテストのクラスかパッケージに絞ってある。プロジェクトのルートで流す。

## 1. 道具と設定

| 道具 | 使い方 |
|---|---|
| JUnit 5・AssertJ | 単体テスト（`*Test`、`:backend:test`）と結合テスト（`*IT`、`:backend:integrationTest`）。分け方は `backend/build.gradle.kts` の既存の filter のまま |
| jqwik | `ReferencePolicyPropertyTest`・`ReferenceReadDeadlinePropertyTest`・`ReferenceRowsRequestValidatorPropertyTest`。失敗したときは乱数の種がテストの報告に出るので、記録して `@Property(seed = …)` で再現する |
| Spring Boot Test | 結合テスト。内部DB は組み込みの H2（コンテナにしない） |
| Testcontainers | 対象DB の MySQL・MariaDB・PostgreSQL（版を固定したイメージ。`targetdb/testsupport/TargetDbImages`）。H2 やモックで代えない |
| ArchUnit | 既存の境界テストを変えずに流す |

新しい依存・設定ファイルは足さない（既存の Gradle の設定で流せる）。

## 2. 前提

- colima が動いていること。結合テストのコマンドには次の環境変数を付ける（付けないと対象DB のテストが SKIPPED になる）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- SKIPPED の対象DB のテストがあるときは、その結果で統合しない（`team.md`）。
- 長い実行は `caffeinate -dims` で包み、後で `pmset -g log` で Sleep・DarkWake が無かったことを確かめる。

## 3. この単位のテストを流すコマンド

### 3.1 最初の手順の前に流せることを確かめる（計画の Step 2）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.TargetDbSettingsTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.PostgresBusinessDataReadIT'
```

### 3.2 単体テスト（層ごと）

```bash
# targetdb（設定・読み取りの口）
./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.TargetDbPropertiesTest' --tests 'cherry.mastersmith.targetdb.config.TargetDbSettingsTest' --tests 'cherry.mastersmith.targetdb.service.JdbcTargetReadAccessTest'

# businessdata の domain（純粋な関数・継ぎ目の型・要求の形）
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.Reference*'

# repository（方言の口・文の形・既定の文が変わらないこと）
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.RowDialectTest' --tests 'cherry.mastersmith.businessdata.repository.RowSqlExecutorTest'

# service（ReferenceLookup・写しの受け渡し・定義の取得）
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.ReferenceLookupTest' --tests 'cherry.mastersmith.businessdata.service.TableAccessResolverTest' --tests 'cherry.mastersmith.businessdata.service.BusinessDataReadServiceTest' --tests 'cherry.mastersmith.businessdata.service.BusinessDataProblemTypeCatalogTest'

# web（要求の読み取り・結果の写し）と、変えないことを確かめる既存のテスト
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.web.BusinessDataReadControllerTest' --tests 'cherry.mastersmith.businessdata.domain.ColumnDefinitionWriteItemsConsistencyTest' --tests 'cherry.mastersmith.businessdata.BusinessDataBoundaryArchitectureTest' --tests 'cherry.mastersmith.targetdb.TargetDbBoundaryArchitectureTest' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' --tests 'cherry.mastersmith.ApiAccessArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest'
```

### 3.3 結合テスト（3種類の対象DB を含む。2節の環境変数を付ける）

```bash
# U1 の新しい結合テスト（*ReferenceReadIT の3種類・ReferenceConnectionUsageIT・ReferenceTimeoutIT・ReferenceAuthorizationApiIT・
# ReferenceSameResponseIT・ReferenceRequestLimitIT・ReferenceSecretLeakIT・ReferenceTraceExposureIT）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*Reference*IT'

# 定義の応答を足しても今の読み取り（一覧・詳細・定義）が変わらないこと（T-C1・T-C2）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*BusinessDataRead*IT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataConnectionUsageIT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataTimeoutIT'

# 設定の誤りで起動が止まらないこと
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.config.TargetDbStartupIT'
```

### 3.4 perf の台本の確かめ（B2 の Step 22。アプリ・対象DB には触れない）

```bash
bash -n perf/biz/make-biz-seed.sh
node --check perf/make-biz-dsl.mjs
bash perf/k6/thresholds-test.sh
for s in refDefinition200 refDefinition201 refPickerEquals refLabel refPickerContains; do
  SCENARIO=$s k6 inspect --include-system-env-vars perf/k6/scenarios.js
done
```

## 4. 期待するカバレッジ

- 行 80%・分岐 70% の下限は、全体の合計とパッケージごと（この単位が手を入れる `businessdata.domain`・`businessdata.service`・`businessdata.repository`・`businessdata.web`・`targetdb.config`・`targetdb.domain`・`targetdb.service`）に当たる。どれも `packagesJudgedByTotal` に入っていない。
- この下限はプロジェクト全体に当てる下限で、Build and Test が全件のテストで判定する（`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify`）。上の絞ったコマンドはカバレッジの下限を判定しない。下限の設定や除外を、テストを通すために変えない。
- 各 Bolt の統合の前の `verify` の後に、`backend/build/reports/jacoco/` の報告から上の7パッケージの値を読んで記録する。

## 5. 差し替え・待ち合わせの手引き

| 部品 | 使い方 |
|---|---|
| `businessdata/testsupport/FakeBusinessDataPorts` | 読み取りの口の差し替え。`referenceReadTotal()` を足し、`Usable` でない形では `IllegalStateException` を投げる本物と同じ振る舞いにする |
| `businessdata/testsupport/SteppingClock` | 締め切り（T-T2・T-T3）を時計で動かす。`sleep` と実時刻に頼らない |
| `targetdb/testsupport/SilentServer`・`businessdata/testsupport/StallingRelay` | 応答しない対象DB（時間切れ・待つ間の内部DB の接続の数） |
| `businessdata/testsupport/CountingDataSource`・`StatementLog` | 借りた回数と流れた文の数を決定的に数える |
| ロガーで絞った記録 | `ReferenceSecretLeakIT` で `ReferenceLookup` と `GlobalExceptionHandler` の WARN の行の数を数える（既存の `BusinessDataWriteSecretLeakIT` と同じ形） |

モックは口の境い目（読み取りの口・時計）だけに使い、SQL と3種類の対象DB の振る舞いは本物のコンテナで確かめる。差し替えの戻り値は本物と同じ型（`TargetReadOutcome`・`RowSearchResult` など）で作る。

## 6. テストのデータ

- 参照先・参照元の表は、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB はデータベース）を実行ごとに一意の名前で作り、終わったら消す（`businessdata/testsupport/ReferenceTables`）。前の実行の残りに頼らない。
- 値は架空のものだけ。メールアドレスは `example.com` だけ。日本語の値はよい。漏えいの確かめには架空の目印の文字を使う。
- 長い値（10,000・10,001・2,000,000 字、約 10,000 字の 200 行を含む 2,000 行）を作る表は、1つのクラスで1回だけ作る（`verify` の時間を抑えるため）。
- テストの説明文（`@DisplayName`・メソッド名）は英語で書く。

## 7. 記録すること

- 各 Bolt の `verify` の時間、テストの件数、7パッケージのカバレッジの値。
- 流したが失敗したテストと、流せなかったテスト（理由つき）。
- jqwik が失敗したときの種。
