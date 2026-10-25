# 単体テストの手順（261025-deps-perf-carryover）

範囲は bugfix、Test Strategy は Minimal（要件ごとに1件以上、不具合の直しは再現のテストを同じコミットに入れる）。この手順は Intent 専用で、**すべてのコマンドはこの Intent のテストだけに絞っている**。プロジェクト全体の `npm test` や無指定の `./gradlew test` は、Build and Test と統合の前の `./gradlew verify` 以外では使わない。

テストの説明文（`@DisplayName`・`describe`・`it`）は英語、テストデータは日本語でよい。メールアドレスは予約のドメイン（`example.com`）だけ。

## 1. 前提とテストの枠組み

| 項目 | 内容 |
|---|---|
| バックエンドの単体テスト | JUnit 5 + AssertJ（`backend/src/test/java`、クラス名は `XxxTest`、`./gradlew :backend:test`） |
| バックエンドの結合テスト | Spring Boot Test + 組み込みの H2（内部DB）+ Testcontainers（対象DB の MySQL・MariaDB・PostgreSQL。`XxxIT`、`./gradlew :backend:integrationTest`） |
| 構造の検査 | ArchUnit（`*BoundaryArchitectureTest`・`ArchitectureTest`。単体テストの扱い） |
| フロントエンド | Vitest + Testing Library（jsdom）+ user-event + vitest-axe + fast-check（`frontend/src/**/*.test.ts(x)`） |
| E2E | Playwright（`./gradlew e2eTest`。`verify` と CI の外） |
| 前提の道具 | colima が動いていること（対象DB の結合テスト）、`osv-scanner`、`gitleaks`、Node（`frontend/.nvmrc` どおり）。`README.md` の「前提の道具」 |

対象DB の結合テストを colima の PC で流すときは、シェルに次を渡す（渡さないと SKIPPED になり、パッケージごとのカバレッジの下限で失敗する。`README.md` の「対象DB の結合テストとコンテナの実行環境」の値を使う）:

```
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

長いコマンド（`verify`・`e2eTest`・k6）は `caffeinate -dims` で包む。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる。入っていたら結果を使わずに流し直す。

## 2. 最初のテストの前に実行できるコマンド（テスト実行の準備の確かめ）

ここのコマンドは、実装を始める前（Step 2）に流して通ることを確かめる。

```
./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.TargetDbPropertiesTest'
npm --prefix frontend run test -- src/features/preferences/PasswordChangePage.test.tsx
```

期待: どちらも BUILD SUCCESSFUL／全件通過（直す前の状態）。

## 3. 層ごとのコマンド（この Intent の変更のテストだけ）

### B1・B2（依存・ビルド設定の層。FR2・FR1）

| 目的 | コマンド | 期待 |
|---|---|---|
| appender（外部エクスポートを有効にして起動） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.common.observability.OtlpLogExportIT' --tests 'cherry.mastersmith.common.observability.ExternalExportIT'` | 全件通過（Spring Boot 4.1.2 + appender 2.28.1-alpha） |
| DSL の JSON Schema の検証（networknt 3.0.6 + Jackson 3.1.7） | `./gradlew :backend:test --tests 'cherry.mastersmith.dsl.validate.*'` | 全件通過 |
| 依存の脆弱性（上書きを外した後） | `./gradlew osvScan --rerun` | High 以上が 0 件 |
| 更新後の画面のテスト（B2） | `npm --prefix frontend run test -- --run`（B2 は依存の更新が全体に及ぶため、この Intent で唯一、全件を流す）。通常は `./gradlew frontendTest` | 全件通過 |
| Playwright（B2、Q2: A のとき） | `./gradlew e2eTest` | 全件通過。報告の json に資格情報・接続先が入っていない（`grep -c` で 0） |

### B3: 設定の層（FR4.2・FR4.4）

```
./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.TargetDbPropertiesTest' --tests 'cherry.mastersmith.targetdb.config.TargetDbSettingsTest' --tests 'cherry.mastersmith.targetdb.config.TargetDataSourceConfigTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.config.TargetDbStartupIT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataConnectionUsageIT' --tests 'cherry.mastersmith.navigation.web.NavigationConnectionUsageIT' --tests 'cherry.mastersmith.group.web.GroupConnectionUsageIT' --tests 'cherry.mastersmith.role.web.RoleConnectionUsageIT'
```

期待: 読み取りのプールの既定が 10、書き込みが 5。1要求が使う対象DB と内部DB の接続の数が変わらない。

### B3: DB アクセス・業務処理の層（FR3.2・FR3.1・FR3.7）

| 対象 | コマンド | 期待 |
|---|---|---|
| C2 時間切れの後に接続を捨てる（単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.service.JdbcTargetReadAccessTest'` | 印のある接続が `evictConnection` で捨てられる |
| C2 再現（ServerSocket の方式。MySQL・MariaDB の件数の問い合わせ） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataTimeoutIT'` | 時間切れの1回の後の次の要求が 200 |
| C1 符号なしの範囲（単体・DSL の生成・検証） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.ColumnValueCodecTest' --tests 'cherry.mastersmith.businessdata.repository.RowDialectTest' --tests 'cherry.mastersmith.dslmanage.generate.*' --tests 'cherry.mastersmith.dsl.validate.*' --tests 'cherry.mastersmith.dslmanage.service.DslSummaryAndDiffTest'` | 符号なしの境界（最大ちょうどは受け付け、超えると拒否、−1 は拒否）。符号を持たない既存の DSL が変更なしで通る |
| C1 結合（3種類の対象DB） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.MysqlBusinessDataWriteIT' --tests 'cherry.mastersmith.businessdata.web.MariadbBusinessDataWriteIT' --tests 'cherry.mastersmith.businessdata.web.PostgresBusinessDataWriteIT'` | 符号なしの列への −1 が 400。符号つきの範囲の外が従来どおり 409。PostgreSQL は変わらない |
| JSON Schema の公開 | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dsl.DslSchemaPublicationIT'` | `description` を含む JSON Schema が公開される |
| R-08 真偽値の `tinyint` が 0/1 以外 | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.ColumnValueCodecTest' --tests 'cherry.mastersmith.businessdata.repository.RowSqlExecutorTest'` と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.MysqlBusinessDataReadIT' --tests 'cherry.mastersmith.businessdata.web.MariadbBusinessDataReadIT'` | `READ_CONVERSION`（409 `MISMATCH`） |
| R-07 長い名前を返さない | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.SearchRequestValidatorTest' --tests 'cherry.mastersmith.businessdata.web.BusinessDataReadControllerTest'` と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataSecretLeakIT'` | DSL に無い名前は `field` が空で `NOT_ALLOWED`。名前がログに出ない |

### B3: API の層（FR3.3・FR3.7）

| 対象 | コマンド | 期待 |
|---|---|---|
| C3 メニューの道が残り、table-access が消えた | `./gradlew :backend:test --tests 'cherry.mastersmith.navigation.*'` と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.navigation.web.*'` | 全件通過。削除した道は 404（または既定の扱い） |
| API の分類の網羅・構造 | `./gradlew :backend:test --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.navigation.NavigationBoundaryArchitectureTest' --tests 'cherry.mastersmith.businessdata.BusinessDataBoundaryArchitectureTest' --tests 'cherry.mastersmith.targetdb.TargetDbBoundaryArchitectureTest'` と、`ApiAccess` を使う分類のテスト（`grep -rl ApiAccess backend/src/test` で洗い出したクラス）を `--tests` で並べる | 全件通過。R-06 の新しい規則が、確かめ用のクラスで失敗する（規則が効いている） |
| R-02 DSL に無い名前の拒否 | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataAuthorizationApiIT' --tests 'cherry.mastersmith.businessdata.web.PostgresBusinessDataReadIT' --tests 'cherry.mastersmith.businessdata.web.MysqlBusinessDataReadIT' --tests 'cherry.mastersmith.businessdata.web.MariadbBusinessDataReadIT'` | 404・400 が返り、対象DB への文の数 0 |
| R-05 止める段 | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataTimeoutIT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataWriteTimeoutIT'` | 止めた段が診断に残り、期待の段で止まる |

### B3: 画面の層（FR3.5）

```
npm --prefix frontend run test -- src/features/preferences/PasswordChangePage.test.tsx src/features/preferences/PreferencesPage.test.tsx src/features/preferences/PasswordChangeForm.test.tsx src/features/preferences/PreferencesForm.test.tsx
```

期待: 全件通過。「画面を離れた直後の答えで Toast が出ない」「保存の成功の直後に知らせが出る」を `waitFor` で確かめる。各部品のアクセシビリティ検査（vitest-axe）が通る。

### B3: 統合の前（カバレッジと全体）

```
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
caffeinate -dims ./gradlew e2eTest
```

期待: BUILD SUCCESSFUL。カバレッジは行 80%・分岐 70% 以上（バックエンド・フロントエンドとも）。`src/main` に手が入ったパッケージ（plan の 3.4）の行・分岐の値を、`backend/build/reports/jacoco/` から読んで記録する。E2E は全件通過、報告の json に資格情報・接続先が入っていない。

### B4（運用の道具・台本。手動の確かめ）

`verify` に入らないもの。使い捨ての環境の手順は `perf/README.md`。結果は `generation-notes.md` に書く。

| 対象 | 確かめ方 |
|---|---|
| 台本の読み込み | `k6 inspect --include-system-env-vars /scripts/scenarios.js` を、`perf/versions.env` の `K6_IMAGE` で（場面の名前が `undefined` でないこと。新しい場面 `monitorLight`・`dslRoleOverlap` が出る。`tableAccess*` は出ない） |
| FR3.4 `compact` | 使い捨ての環境（`mastersmith-perf-app-1`）で、起動の直後に `./docker/hikari-pool.sh compact --container mastersmith-perf-app-1` を流し、取りこぼさず終了コード 0。再現できないときは見立てと試みの範囲を記録 |
| FR3.6 1か所 | `grep -rn 'grafana/k6:2.3.0@sha256' perf README.md` が1か所、`grep -rn 'h2-2.4.240.jar' perf README.md` が1か所 |
| FR3.9 数える道具 | 手元の複写（読み取り）に存在しない列の名前を渡し、誤りの文が出ずに件数だけが出る（個人に関する値を表示しない） |
| FR3.11 | 複数行の k6 の報告に対し、直した前の `grep` が何も出さず、直した後が `checks` の値を出す |

## 4. 期待のカバレッジの目標

- バックエンド・フロントエンドとも行 80% 以上・分岐 70% 以上（全体の合計）。バックエンドは新しいパッケージだけでなく、既存のパッケージもパッケージごとの下限の対象（`packagesJudgedByTotal` の7パッケージを除く）。
- `src/main` に手が入ったパッケージ（`targetdb.config`・`targetdb.domain`・`targetdb.service`・`dslmanage.generate`・`businessdata.repository`・`businessdata.domain`・`businessdata.web`・`navigation.*`）は、行 80%・分岐 70% 以上であること。
- 除外を増やさない。目標を下げない。

## 5. モック・スタブの方針

- 対象DB（MySQL・MariaDB・PostgreSQL）は H2 やモックで代用しない（`project.md` の Mandated）。結合テストは版を固定したイメージのコンテナで行う。
- 応答しない対象DB は、テストの中で開いた `ServerSocket`（受け付けて何も返さない）か `StallingRelay`（本物の対象DB へ流し、合図の後に止める中継）を使う。外の端末に頼らない。
- 時刻に依存する処理は注入した `Clock`。`sleep` と実時刻に頼らない。
- 画面の API は `fetch` を差し替えるのではなく、既存のテスト用の応答（`frontend/src/features/preferences/testing/`）を使い、本物と同じ形の応答にそろえる。描画の後に反映される値は `waitFor` で待つ。時間の上限は延ばさない。
- 外へメールを送らない。個人に関する値・秘密をテストの出力に出さない。

## 6. テストデータの管理

- 対象DB を使うテストは、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作り、終わったら消す（`team.md`）。符号なしの列（`int unsigned` など）は、MySQL・MariaDB の表だけで作る（PostgreSQL に符号なしの型は無い）。
- DSL のテスト用の見本は `backend/src/test/resources/cherry/mastersmith/dsl/valid-sample.yaml`・`dslmanage/generate/dept_mst.yaml`。符号なしの例は新しい見本に足し、既存の見本は変えない（符号を持たない既存の DSL が変更なしで通ることの確かめに使う）。
- 長い名前（R-07）は、64 KB 近い文字列を、テストの中で組み立てる（ファイルに置かない）。

## 7. Build and Test に引き継ぐこと（この段では流さない。持ち主は Build and Test）

| 項目 | 内容 |
|---|---|
| FR4.9 3種類の対象DB の負荷の試験 | 前回と同じ場面・同じ線（`aidlc/spaces/default/intents/261016-business-data-views/operation/performance-validation/nfr-validation-matrix.md`）。プール 10。Not Met 4 件（U1-NFR1.1・1.3・3.2・2.4）が Met になったか、場面ごとの値と件数で記録。目標を緩めない。上限に届く形の場面（12 VU の `bizSearchContains`）を含める。配備したアプリを約 4 時間止める |
| FR4.2 対象DB のメモリ | OOMKilled が false で `oom_kill` が 0（合格の条件）、`anon` の最大を並べる（線は 85%、諮る線）。(a) OOMKilled か `oom_kill` が 0 でなければ、既定を 5 に戻して依頼者に諮る |
| FR4.5 `memory.peak` | `dslMixed`、線は 90% 未満（前回 0.8731・0.8696）。既定の回と `RETENTION_TIME=0` の試しの回。取り込むかは測った結果を添えて諮る |
| FR4.7 画面の時間 | E2E 185 の 5 つの線（plan の 5.2）。5 回の中央値 |
| FR4.8 P5 | `dslRoleOverlap` を流して結果を件数で記録 |
| FR4.8 P6 | 置き場の p95 を測り直し、締める案を提案するだけ（目標は変えない） |
| FR4.8 P7 | `verify` の実測で単体テストの JVM のヒープ（1g／結合 1536m）が尽きていないことを確かめて記録。尽きたときだけ境界テストの読み込みを共有する直し |
| FR3.10 | リリースの前の確かめ（`osvScan --rerun`、`dist` に axe-core が無い）を、取り込みの前に実際に流す |
| `verify` と CI の時間 | 計測して記録。上限（`timeout-minutes: 60`）の 7 割（約 42 分）を超えたら依頼者に諮る |
