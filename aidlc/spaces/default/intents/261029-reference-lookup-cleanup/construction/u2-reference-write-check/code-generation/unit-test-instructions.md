# 単体テストの手順 — U2 u2-reference-write-check

Test Strategy は Standard（部品ごとに 5〜8 件の要の振る舞い）。方法は test-after（`code-generation-plan.md` の Testing Contract が正）。どのコマンドもこの単位のテストのクラスかパッケージに絞ってある。プロジェクトのルートで流す。

## 1. 道具と設定

| 道具 | 使い方 |
|---|---|
| JUnit 5・AssertJ | 単体テスト（`*Test`、`:backend:test`）と結合テスト（`*IT`、`:backend:integrationTest`）。分け方は `backend/build.gradle.kts` の既存の filter のまま |
| jqwik | `ReferenceWritePolicyPropertyTest`・`WriteRequestValidatorPropertyTest`（足す1件）。失敗したときは乱数の種がテストの報告に出るので、記録して `@Property(seed = …)` で再現する |
| Spring Boot Test | 結合テスト。内部DB は組み込みの H2（コンテナにしない） |
| Testcontainers | 対象DB の MySQL・MariaDB・PostgreSQL（版を固定したイメージ。`targetdb/testsupport/TargetDbImages`）。H2 やモックで代えない |
| ArchUnit | 既存の境界テストを変えずに流す |

新しい依存・設定ファイルは足さない（既存の Gradle の設定で流せる。NFR9.21）。

## 2. 前提

- colima が動いていること。結合テストのコマンドには次の環境変数を付ける（付けないと対象DB のテストが SKIPPED になる）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- SKIPPED の対象DB のテストがあるときは、その結果で統合しない（`team.md`）。
- 長い実行は `caffeinate -dims` で包み、後で `pmset -g log` で Sleep・DarkWake が無かったことを確かめる。
- U1（B1 549d38b・B2 b048ec1）が `develop` に統合済みであること（`ReferenceLookup.check`・`countState`・`ReferenceReadDeadline`・`ReferenceReadFailure.ofBudgetExceeded()`・`TargetReadAccess.referenceReadTotal()` を使う）。

## 3. この単位のテストを流すコマンド

### 3.1 最初の手順の前に流せることを確かめる（計画の Step 2）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.BusinessDataWriteServiceTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.PostgresBusinessDataWriteIT'
```

### 3.2 単体テスト（層ごと）

```bash
# businessdata の domain（判定の純粋な関数・値を持つ型・必須の参照項目の空の誤りの保留）
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.ReferenceWrite*' --tests 'cherry.mastersmith.businessdata.domain.WriteRequestValidator*' --tests 'cherry.mastersmith.businessdata.domain.WriteValueTypesTest' --tests 'cherry.mastersmith.businessdata.domain.ColumnDefinitionWriteItemsConsistencyTest' --tests 'cherry.mastersmith.businessdata.domain.DeadlinePatternTimingTest'

# service（保存前の参照の段・ReferenceLookup に足した口・code の一覧が変わらないこと）
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.BusinessDataWriteServiceTest' --tests 'cherry.mastersmith.businessdata.service.BusinessDataWriteReferenceTest' --tests 'cherry.mastersmith.businessdata.service.ReferenceLookupTest' --tests 'cherry.mastersmith.businessdata.service.BusinessDataProblemTypeCatalogTest'

# web（拒否の写し）と、変えないことを確かめる境界テスト
./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteControllerTest' --tests 'cherry.mastersmith.businessdata.BusinessDataBoundaryArchitectureTest' --tests 'cherry.mastersmith.targetdb.TargetDbBoundaryArchitectureTest' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' --tests 'cherry.mastersmith.ApiAccessArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest'
```

### 3.3 結合テスト（3種類の対象DB を含む。2節の環境変数を付ける）

```bash
# U2 の新しい結合テスト（*ReferenceWriteIT の3種類・ReferenceWriteAuthorizationApiIT・ReferenceWriteConnectionUsageIT・
# ReferenceWriteTimeoutIT・ReferenceWriteSecretLeakIT・ReferenceWriteTraceExposureIT・ReferenceWriteAuditIT）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*ReferenceWrite*IT'

# 参照の無い作成・更新・削除と U1 の読み取りが今と変わらないこと（T-C1・NFR7.22）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*BusinessDataWrite*IT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataConnectionUsageIT' --tests 'cherry.mastersmith.businessdata.web.*ReferenceRead*IT' --tests 'cherry.mastersmith.businessdata.web.ReferenceConnectionUsageIT' --tests 'cherry.mastersmith.businessdata.web.ReferenceTimeoutIT'
```

### 3.4 perf の台本の確かめ（計画の Step 11。アプリ・対象DB には触れない）

```bash
bash -n perf/biz/make-biz-seed.sh
node --check perf/make-biz-dsl.mjs
node --check perf/k6/scenarios.js
bash perf/k6/thresholds-test.sh
SCENARIO=refCreateChecked k6 inspect --include-system-env-vars perf/k6/scenarios.js
```

`k6` が手元に無いときは、U1 と同じく `perf/versions.env` の `K6_IMAGE` で `docker run --rm -e SCENARIO=refCreateChecked -v "$PWD/perf/k6:/scripts" "$K6_IMAGE" inspect --include-system-env-vars /scripts/scenarios.js` を流す（`--include-system-env-vars` が無いと場面の名前が `undefined` になる。`project.md`）。

## 4. 期待するカバレッジ

- 行 80%・分岐 70% の下限は、全体の合計とパッケージごと（この単位が手を入れる `businessdata.domain`・`businessdata.service`）に当たる。どちらも `packagesJudgedByTotal` に入っていない。
- この下限はプロジェクト全体に当てる下限で、**Build and Test が全件のテストで判定する**（`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify`）。3節の絞ったコマンドはカバレッジの下限を判定しない。下限の設定や除外を、テストを通すために変えない。
- 統合の前の `verify`（計画の Step 12）は、次の1つのコマンドとして単独で流す（下限の判定で止まりうるため、後に手順を続けない）。

```bash
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- その後に `backend/build/reports/jacoco/test/jacocoTestReport.xml` から上の2パッケージの値を読み、`verify` の時間と並べて記録する（U1 の後は 23分34秒。42 分を超えたら依頼者に諮る。NFR5.23）。

## 5. 差し替え・待ち合わせの手引き

| 部品 | 使い方 |
|---|---|
| `businessdata/testsupport/FakeBusinessDataPorts` | 読み取り・書き込みの口と行の SQL の部品の差し替え。`BusinessDataWriteReferenceTest` と `BusinessDataWriteServiceTest` で `ReferenceLookup` をこの差し替えの口から作る（`detailResult`・`searchResult` を順に返す形で更新前の行・`countState`・`check` の答えを決める） |
| `businessdata/testsupport/SteppingClock` | ⑩ の全体の締め切り（T-T3）を時計で動かす。`sleep` と実時刻に頼らない。`narrow` そのもの（T-T2）は U1 のテストが持つ |
| `businessdata/testsupport/PausingTargetWriteAccess`・`PausingWriteAccessConfig` | T-G1・T-G2。`withWrite` の入口で一度きりの合図で止め、上限つきの `CountDownLatch`（20 秒）で待つ。上限切れはテストの失敗にし、`finally`・`@AfterEach` で必ず「進め」を出して止めた要求を放し、合図を外す |
| `businessdata/testsupport/CountingDataSourceConfig` | 内部DB・読み取り・書き込みの借りた回数・使用中・`peerInUseAtBorrow` を決定的に数える |
| `businessdata/testsupport/StatementLog` | MySQL・MariaDB で流れた文の数（同じ参照先の `countState` が1回、`NO_PERMISSION` の値で `check` が流れない、T-F2 で3つ目が流れない、T-G2 で `detail` だけ） |
| 眠る関数を含むビュー（`ReferenceTables`、U1 の `ReferenceTimeoutIT` と同じ作り） | T-T1 の 503 `BUSINESS_TARGET_DB_TIMEOUT`。`SilentServer`（`targetdb/testsupport`）は借りる段で止まるため 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる |
| ロガーで絞った記録 | `ReferenceWriteSecretLeakIT` で、`BusinessDataWriteService` のロガーの診断の WARN（ちょうど1行）と `GlobalExceptionHandler` のロガーの変換の WARN（1行）を分けて数える（既存の `BusinessDataWriteSecretLeakIT` と同じ形） |

モックは口の境い目（読み取り・書き込みの口・時計）だけに使い、SQL と3種類の対象DB の振る舞いは本物のコンテナで確かめる。差し替えの戻り値は本物と同じ型（`TargetReadOutcome`・`RowDetailResult`・`RowSearchResult`・`ReferenceCheckResult`・`ReferenceCountState`）で作る。

## 6. テストのデータ

- 参照先・参照元の表は、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB はデータベース）を実行ごとに一意の名前で作り、終わったら消す（`ReferenceTables` の書き込みの組、`BusinessDataSampleSchema`）。前の実行の残りに頼らない。アプリは読み取りのアカウントで読み、書き込みのアカウントで書く。書いた後の値はアプリとは別の接続（管理者）で読み直し、アプリのトランザクションの巻き戻しに頼らない（`team.md`）。
- 値は架空のものだけ。メールアドレスは `example.com` だけ。日本語の値はよい。漏えいの確かめには架空の目印の文字を使う。
- 長い値（10,000・10,001 字）を作る表は、1つのクラスで1回だけ作る（`verify` の時間を抑えるため）。
- テストの説明文（`@DisplayName`・メソッド名）は英語で書く。

## 7. 記録すること

- `verify` の時間（U1 の後の値と並べる）、テストの件数、2パッケージのカバレッジの値。
- E2E の全件の結果（統合の前。業務データの `src/main` に触れるため）。
- 流したが失敗したテストと、流せなかったテスト（理由つき）。
- jqwik が失敗したときの種。
