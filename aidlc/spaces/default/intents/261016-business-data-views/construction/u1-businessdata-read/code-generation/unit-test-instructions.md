# 単体テストの手順 — U1 u1-businessdata-read（Bolt B2）

この文書は、U1 のテストの書き方と走らせ方を決める。Test Strategy は standard（部品ごとに 5〜8 件、要所の境目に結合テスト）、methodology は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）。手順の順は `code-generation-plan.md` の 5節の Step に従う。

## 1. 道具と設定

| 用途 | 道具 | 設定の置き場 |
|---|---|---|
| 単体テスト（名前が `Test` で終わるクラス） | JUnit 5・AssertJ・Mockito | `backend/build.gradle.kts` の `tasks.test`（既存。変えない） |
| 結合テスト（名前が `IT` で終わるクラス） | Spring Boot のテスト・Testcontainers（版を固定したイメージ）・組み込みの H2（内部DB） | `backend/build.gradle.kts` の `integrationTest`（既存。変えない） |
| 性質ベースのテスト | jqwik 1.10.2（既存の依存） | 失敗の詳細と乱数の種は、既存の `testLogging` の設定で出力に残る |
| 構造の検査 | ArchUnit（既存の依存） | — |
| 対象DB のイメージ | `targetdb/testsupport/TargetDbImages`（MySQL 26.7.0・MariaDB 13.0.2・PostgreSQL 18.6 のダイジェスト） | 変えない |

- 新しい依存は足さない（`tech-stack-decisions.md`）。テストの設定のファイル（`build.gradle.kts`）も変えない。
- テストの JVM は既存の設定のまま（`user.timezone=Asia/Tokyo`・`java.net.preferIPv4Stack=true`・`h2.compactThreads=1`）。
- テストの説明文（`@DisplayName`）は英語。コメントは日本語。テストのファイルの先頭にも Apache License 2.0 のヘッダー（`/* ... */`）を置く。
- 置き場は `backend/src/test/java/cherry/mastersmith/` の下で、対象と同じパッケージにする。テストの手伝いは `businessdata/testsupport` に置く。

## 2. 走らせる前の準備（毎回）

コンテナの実行環境（colima）が動いていることを確かめ、README の2つの変数をシェルに渡す（project.md の学び。渡さないと対象DB のテストが SKIPPED になる）。

```bash
colima status
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- コンテナの実行環境が無いと、対象DB のテストのクラスは WARN（「コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない」）を出して飛ばされる。飛ばした状態の結果では統合しない。
- 数分を超える実行（結合テストの全体・verify・E2E）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる。入っていたら結果を使わずに流し直す。

## 3. この単位のテストの走らせ方

どのコマンドも、この単位のテストのクラスだけに絞っている（プロジェクトの全体のテストは、Step 24 の `verify` でだけ流す）。プロジェクトのルートで流す。

### 3.1 走らせ方の確かめ（Step 1。最初のテストの Step の前）

既存の `targetdb` の単体テストが通り、絞り込みの形が動くことを確かめる。

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.domain.*' --tests 'cherry.mastersmith.targetdb.config.*'
```

### 3.2 層ごとのコマンド

| 層（Step） | コマンド |
|---|---|
| domain の層（Step 5） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.*' --tests 'cherry.mastersmith.targetdb.domain.*' --tests 'cherry.mastersmith.role.domain.PermissionSnapshotTest'` |
| 設定（Step 7、単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.*' --tests 'cherry.mastersmith.targetdb.service.TargetSchemaReaderTest'` |
| 設定（Step 7、結合） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'` |
| repository の層（Step 9） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.*'` |
| service の層（Step 12） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.*' --tests 'cherry.mastersmith.targetdb.service.JdbcTargetReadAccessTest'` |
| web の層（Step 14、単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.web.*' --tests 'cherry.mastersmith.ApiAccessArchitectureTest'` |
| web の層（Step 14、結合） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataAuthorizationApiIT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataRequestLimitIT' --tests 'cherry.mastersmith.ApiAccessConsistencyIT' --tests 'cherry.mastersmith.common.error.service.ProblemTypeDuplicateStartupIT'` |
| 3種類の対象DB の結合テスト（Step 15） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*BusinessDataReadIT'` |
| 境界テスト（Step 16） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.BusinessDataBoundaryArchitectureTest' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' --tests 'cherry.mastersmith.targetdb.TargetDbBoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.dsl.DslBoundaryArchitectureTest'` |
| 静的解析（Step 16） | `./gradlew :backend:spotbugsMain :backend:spotbugsGate` |
| 漏えいの IT（Step 17） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataSecretLeakIT' --tests 'cherry.mastersmith.businessdata.web.BusinessDataTraceExposureIT'` |
| 接続の数の IT（Step 18） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataConnectionUsageIT'` |
| 時間切れの IT（Step 19） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.BusinessDataTimeoutIT'` |
| 性質ベースのテスト（Step 20） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.*PropertyTest'` |

### 3.3 この単位のテストをまとめて流す（Build and Test でも使う）

```bash
# 単体テスト（U1 が足した・変えたテストのクラス）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.businessdata.*' \
  --tests 'cherry.mastersmith.targetdb.*' \
  --tests 'cherry.mastersmith.role.domain.PermissionSnapshotTest' \
  --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessArchitectureTest'

# 結合テスト（3種類の対象DB を含む。caffeinate -dims で包む）
caffeinate -dims ./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.businessdata.*' \
  --tests 'cherry.mastersmith.targetdb.*' \
  --tests 'cherry.mastersmith.ApiAccessConsistencyIT'
```

- 結果は `backend/build/test-results/test/` と `backend/build/test-results/integrationTest/` の XML で確かめる。テストが UP-TO-DATE で飛ばされたときは、件数を報告する前に `:backend:cleanTest`・`:backend:cleanIntegrationTest` を付けて流し直す（project.md の学び）。
- 負荷の試験の台本の確かめ（Step 22）は単体テストではないが、同じく単位に絞って流す: `k6 inspect --include-system-env-vars -e SCENARIO=<場面の名前> perf/k6/scenarios.js`（場面ごとに1回。場面の名前は `bizPrepare`・`bizDefinition`・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail`・`bizSearchWide`）。

## 4. 部品ごとのテストと件数の目安

standard の目安（部品ごとに 5〜8 件）に、team.md の業務データの必須のテストを足す。件数は目安で、生成で変えたときは code-summary.md に実数を書く。

### 4.1 単体テスト

| テストのクラス | 対象 | 件数の目安 | 主に確かめること |
|---|---|---|---|
| `targetdb/domain/TargetReadSessionTest` | `TargetReadSession` | 5 | toString が DB の種類と秒だけ、必須の値 |
| `targetdb/domain/TargetReadOutcomeTest` | `TargetReadOutcome` | 5 | `Done` の toString が値の型の名前だけ、`Unavailable` がメッセージを持たない |
| `targetdb/domain/TargetSchemaResultTest`（直す） | `ReadPurpose` | 既存＋0 | 4つの目的 |
| `targetdb/config/TargetDbPropertiesTest`・`TargetDbSettingsTest`・`TargetDataSourceConfigTest`（直す） | 設定の型・点検・通信の待ち | 既存＋各 2〜3 | `list`・`detail` の既定 5 秒・1 秒未満は不正（項目の名前だけ）・4つの上限の最大 + 5 秒 |
| `targetdb/service/JdbcTargetReadAccessTest` | 読み取りの口 | 8 | 設定が無い・借りる失敗・通信の待ちを付けて戻す・戻す処理の失敗で捨てる・処理の非検査の例外・閉じる・`GENERATE` を拒む・`product()` |
| `role/domain/PermissionSnapshotTest`（足す） | `dsl()` | 既存＋2 | DSL のある写し・無い写し |
| `businessdata/domain/TableAccessPolicyTest` | 出し分けと許す操作 | 8 | NONE を出さない・list と detail・FR9.1 の表・主キーの無いテーブル・主キーが NONE・ビュー・行を見分ける値・理由・BR7.9 と BINARY の主キー |
| `businessdata/domain/SearchOperatorPolicyTest` | 演算子と型の表 | 7 | IN の読み替え・REFERENCE と LOOKUP・BR4.9 の表の各行・表に無い組を外す |
| `businessdata/domain/SearchRequestValidatorTest` | 本文の確かめ | 8 | 使えないカラム（NONE と DSL に無いで同じ答え）・型として読めない・範囲の向き・CHOICE の重複と選択肢・主キーのそろえ方・page（1・1,000,000・1,000,001）・size・1,000 字の境界 |
| `businessdata/domain/ContainsPatternTest` | 部分一致のエスケープ | 5 | `%`・`_`・`!`・`\`・空の文字 |
| 値の型のテスト（`RowKeyTest`・`RowValuesTest`・`BoundValueTest`・`SearchConditionTest`・`DefinitionIdTest`） | toString と不変 | 各 5 | 値が toString に出ない、一覧を変えられない |
| `businessdata/repository/ColumnValueCodecTest` | 型の対応・読む・書く | 8 | dbType の分け方（`tinyint(1)`・`bit(1)`・`bit(8)`・空間の型・表に無い型）・桁を失わない・時差の読み（オフセット無し・`Z`・`+09:00`）・JVM の既定のタイムゾーンを変えても同じ（終わりに戻す）・コードポイントの切り詰め・非検査の例外を結果の型にする |
| `businessdata/repository/RowDialectTest` | 方言 | 8 | NULL の並び・`LIKE ? ESCAPE '!'`・選ぶ列の式・主キーを先に読む形（複合の主キー）・`timetz`・OTHER の渡し方・BR7.9 の一覧・始めの文 |
| `businessdata/repository/RowSqlFailureClassifierTest` | 失敗の分け方 | 8（パラメーターの表） | 段階 × SQLState（3種類の DB の値）・`SESSION_INIT` は UNAVAILABLE・`READ` の型の変換の失敗は 400 にせず、理由 `READ_CONVERSION` で 409 `BUSINESS_TARGET_DB_MISMATCH` に写す（D1: A） |
| `businessdata/repository/RowSqlExecutorTest` | SQL の部品 | 8 | 組み立てた SQL の形・バインドの型と順・`setQueryTimeout`・`setMaxRows`・件数以上で2つ目の文を作らない・一覧に無い識別子で文を作らない・例外の経路・時間切れの後に続けない |
| `businessdata/service/TableAccessResolverTest` | 写しと判定 | 6 | 写しは1回・DSL が無い・DSL に無い名前（引用符・`;`・改行を含む）・NONE・DB の種類がわからないとき |
| `businessdata/service/BusinessDataReadServiceTest` | 判定の順と流れ | 8（パラメーターの表を含む） | 判定の順の組・写しの後に口を呼ぶ順・`definitionChanged`・503 の2つの code・409・ログのキーと値 |
| `businessdata/service/BusinessDataProblemTypeCatalogTest` | code の一覧 | 5 | code と状態コードの組・共通の code と重ならない |
| `businessdata/web/BusinessDataReadControllerTest` | 結果の型から応答へ | 8 | 状態コード・code・`fieldErrors` の形・`definitionChanged`（400 だけ）・403 を registry から引く |
| `businessdata/web` の要求と応答の record のテスト | toString | 5 | 値が出ない |
| `businessdata/BusinessDataBoundaryArchitectureTest` | 境界 | 5 | 依存してよい相手・repository は targetdb.service に依存しない・`prepareStatement`・`createStatement` は `RowSqlExecutor` だけ（見つかることも確かめる）・トランザクションの規則・依存される側 |
| `role/RoleBoundaryArchitectureTest`（直す。承認が要る） | 依存してよい相手 | 既存＋1 | `businessdata` は `role.service`・`role.domain` だけ |

### 4.2 性質ベースのテスト（jqwik）

| テストのクラス | 性質 |
|---|---|
| `TableAccessPolicyPropertyTest` | 権限を足しても出るカラムと許す操作が減らない・NONE が出ない・FULL でないカラムが入力にならない・主キーの無い／主キーが NONE のテーブルに DETAIL・UPDATE・DELETE が出ない・ビューに CREATE・UPDATE・DELETE が出ない |
| `SearchOperatorPolicyPropertyTest` | 表に無い組が検索の項目に出ない・password と BINARY がどの演算子にも出ない |
| `IdentifierMatchingPropertyTest` | 一覧に無い名前は拒まれる・任意の文字列（引用符・`;`・改行を含む）でも引用の結果は1つの識別子としてしか読めない |
| `ContainsPatternPropertyTest` | エスケープの後に、`!` の前置の無い `%`・`_` が残らない |

乱数の種は失敗の出力に残る。再現するときは `@Property(seed = "<種>")` を一時的に付けて流し、直した後に外す。

### 4.3 結合テスト

| テストのクラス | 対象DB | 主に確かめること |
|---|---|---|
| `MysqlBusinessDataReadIT`・`MariadbBusinessDataReadIT`・`PostgresBusinessDataReadIT`（`AbstractBusinessDataReadIT` を継ぐ） | 3種類 | `code-generation-plan.md` の Step 15 の一覧（振る舞い・型・識別子・NULL の並び・エスケープ・境界の値・ページ・詳細の 0/1/2 行・時差・OTHER・BR7.9・BINARY と同じ扱いの型・切り詰め・MISMATCH・読む段の型の誤り（MySQL・MariaDB のゼロ日付と範囲の外の値で 409 `BUSINESS_TARGET_DB_MISMATCH`。D1: A）・読み取りだけのアカウント） |
| `BusinessDataAuthorizationApiIT` | PostgreSQL | 3本 × 401・403（要る権限だけを欠く人）・200 の表、停止中の利用者、切り替えた直後・割り当てを変えた直後 |
| `BusinessDataRequestLimitIT` | PostgreSQL | 64KB の境界・413 が 400 より先・1,000 字の境界・page の上限 |
| `BusinessDataSecretLeakIT` | PostgreSQL | TRACE のログ・誤りの応答に値・接続先・メッセージが無い、非検査の例外の経路、`Done` の戻り値、WARN のキー |
| `BusinessDataTraceExposureIT` | PostgreSQL | 外部へ送る設定を有効にし、span の属性に値・名前が無い |
| `BusinessDataConnectionUsageIT` | PostgreSQL（MySQL の行を1つ） | 定義 内部DB 1・対象DB 0、一覧・詳細 内部DB 1・対象DB 1、対象DB を借りている間の内部DB 0 |
| `BusinessDataTimeoutIT` | ServerSocket・遅い問い合わせ・中継（D3: A のとおり MySQL・MariaDB・PostgreSQL の3種類すべて） | 503 `UNAVAILABLE`・503 `TIMEOUT`・25 秒以内（試しの値は MySQL 約 20 秒・PG 約 15 秒・MariaDB 約 10 秒。中継の3つで verify が約 45 秒延びる）・止めを解いた後の次の要求の成功 |
| 既存（直して流す・変えずに流す） | — | `AbstractTargetSchemaReaderIT` の3つ・`TargetDbStartupIT`・`TargetDbSecretLeakIT`・`ApiAccessConsistencyIT`・`ProblemTypeDuplicateStartupIT` |

## 5. カバレッジの目標

- 行 80%・分岐 70% 以上を、全体の合計と、パッケージごと（`businessdata.domain`・`businessdata.repository`・`businessdata.service`・`businessdata.web`・`targetdb.config`・`targetdb.domain`・`targetdb.service`・`role.domain` を含む）に当てる（team.md）。下限の設定と計測の除外は変えない。
- `packagesJudgedByTotal` の 7 パッケージには手を入れない（計画の 1.4節）。
- 測るのは Step 24 の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` だけで、層ごとのコマンドではカバレッジを判定しない。値は実測の数字だけを code-summary.md に書く。
- 下回ったら、除外を増やさずにテストを足す。

## 6. モックと偽物の使い方

- **JDBC**: repository の層の単体テストは、Mockito の `Connection`・`PreparedStatement`・`ResultSet` で、組み立てた SQL の文字・バインドの呼び出し・上限の呼び出しを確かめる。値の受け渡しの正しさ（桁・時差・型）は、モックでなく3種類の本物の DB の結合テストで確かめる（project.md の Mandated。H2 やモックで対象DB を代用しない）。
- **読み取りの口と解決の口**: service の層の単体テストは、`TargetReadAccess`・`EffectivePermissionResolver`・`RowSqlExecutor` の偽物（手で書いた小さな実装）を使い、呼んだ順と回数を記録して確かめる。
- **時計**: U1 は時刻に依存する判定を持たない。時差の確かめは固定の値で行い、`sleep` と実時刻に頼らない。時間切れの IT の時間の確かめは、上限（3 秒・5 秒・25 秒）に対する上限の側だけを見る（「これより長くかからない」）。
- **応答しない対象DB**: 既存の `targetdb/testsupport/SilentServer`（受け付けて何も返さない）。つないだ後に止まる形は、`businessdata/testsupport` に置く中継（`StallingRelay`。手元の番号で受けて本物の DB へ流し、合図で流れを止める）で作る。外の端末には接続しない。
- **接続の数**: 内部DB と対象DB の両方の DataSource を包んで、借りている数の最大と時点を数える（既存の `navigation/testsupport/ConnectionCountingDataSource` と同じ形の包みを `businessdata/testsupport` に置く）。
- **ログ**: 既存の `common/testsupport/JsonLogRecords`・`LogEvents` と `OutputCaptureExtension` で JSON のログを読み、キーと値の無いことを確かめる。

## 7. テストのデータ

- 対象DB の結合テストは、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作り、クラスの終わりに消す（既存の `TargetDbTestDatabase`。名前には実行ごとに一意の値を入れる）。表を作る操作は巻き戻せないため、トランザクションの巻き戻しに頼らない。読み取りだけのアカウントで読む。
- 表は 100 カラム・数行の小さな表を中心にする（100,000 行の種は負荷の試験の環境だけで使い、verify に入れない）。
- サーバーのタイムゾーンは夏時間のある地域（America/New_York）にして、時差の重なりを確かめる。
- 値はすべて架空の値。氏名は実在しそうなものを置かず、メールアドレスは予約のドメイン（`example.com`）だけにする。テストデータに日本語を使ってよい。
- 内部DB（利用者・ロール・権限・DSL）は組み込みの H2 で、テストごとに前提を自分で作る（作業ロールの切り替えと割り当ての変更は、そのテストで作った利用者とロールだけを対象にする）。
- 資格情報は実行のたびの乱数（既存の `TargetDbTestDatabase` の形）で、コードに固定の値を書かない。
