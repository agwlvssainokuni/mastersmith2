# Code Generation Plan — U1 対象DB（u1-target-db）

U1 のコード生成の計画を示す。作るものは、対象DB の接続の設定・接続・メタデータの読み取り（パッケージ `cherry.mastersmith.targetdb`）と、対象DB を実際に読む最初の単位としての Testcontainers の導入である。Bolt は B2（`inception/delivery-planning/bolt-plan.md`）。

## 1. 入力にした設計

| 文書 | 使うところ |
|---|---|
| `construction/u1-target-db/functional-design/functional-spec.md`・`rules.md`・`entities.md` | 起動時の点検、読み取りの手順、結果の型、決まり BR1.1〜BR1.9・BR2.1〜BR2.7 |
| `construction/u1-target-db/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md` | NFR4.1〜4.6、NFR6.1〜6.4、NFR7.1〜7.7、NFR1.1〜1.3、NFR12.1〜12.3、ドライバーの選定とライセンスの理由 |
| `construction/u1-target-db/nfr-design/security-design.md`・`logical-components.md` | 部品と置き場、接続の作り、問い合わせの作り、失敗の扱い、承認済みの文書との差（5節） |
| `construction/u1-target-db/infrastructure-design/cicd-pipeline.md` | 依存の追加、結合テストの段、コンテナの実行環境が無いときの扱い、手元の対象DB の profile、README |
| `inception/contract-design/contract-summary.md` の C1・C3 | `TargetSchemaReader` と結果の型（`readSchema` の引数は NFR 設計の差のとおり `ReadPurpose` を足す） |
| `inception/user-stories/stories.md` | US6.1（AC6.1.1〜AC6.1.5）、US1.1・US1.2 の読み取りの部分（AC1.1.1・AC1.1.7〜AC1.1.10・AC1.2.1） |

## 2. 前提と、この計画での読み方

- **3種類の DB をこの単位で読む**: Bolt の計画は「最初の1種類の DB でメタデータを読める（3種類は B3 で揃える）」としているが、NFR 設計（`logical-components.md`）は MySQL・MariaDB 用と PostgreSQL 用の読み手を U1 に置き、基盤の設計（`cicd-pipeline.md`、NFR12.1）は3種類の結合テストを `./gradlew verify` で毎回実行するとしている。決まり BR2.7（3種類の違いを吸収する）も U1 の決まりである。後から承認された設計に合わせ、U1 で3種類とも読めるようにする。型の共通の分類への対応と 30 秒の性能の確かめは、これまでどおり U3（B3）に残す。
- **承認済みの文書との差（NFR 設計 5節と、その承認の場の決定 B）**: 接続の待ちは 3 秒、`readSchema(ReadPurpose)` で実装する。照合の全体の上限 8 秒は、NFR 設計の承認の場で依頼者が「上限を作らず、応答しない対象DB では 10 秒の目標を超えること（最悪 23〜28 秒）を許す」と決めた（決定 B）ため、作らない。照合は問い合わせ1回ごとの待ち（5 秒）だけで打ち切る。承認済みの文書は書き換えない。
- **設定の型の置き場**: 既存の機能は設定の型を `service` に置いているが、NFR 設計（`logical-components.md`）のとおり `cherry.mastersmith.targetdb.config` に置く。team.md の「1つの機能だけに効く設定はその機能のパッケージの中に置く」は満たす。
- **種類とポートは文字列で受ける**: 設定の型でポートを数、種類を列挙で受けると、値が不正なときに Spring の結び付けで起動が止まる。BR1.3（不正でも起動を続け、項目の名前だけを WARN）を守るため、どちらも文字列で受けて点検で判定する。
- **この Intent で最初に作る Bolt としての共通の関門**: team.md の Testing Posture の「パッケージごとのカバレッジの下限」と Code Style の「SpotBugs の `SQL_` の指摘は priority によらず止める」は、今の `backend/build.gradle.kts` にまだ無い。Bolt の計画の共通の完了の条件に含まれるため、この単位で入れる（Step 12・Step 13）。
- **compose の `app` のサービス**: 基盤の設計は「`app` に対象DB の環境変数を足す」としている。`app` はすでに `.env` を `env_file` で読むため、変数は `.env.example` に足し、`app` の定義は変えない（読み込みの経路は同じで、値を compose.yaml に書かずに済む）。

## 3. 作るもの（パッケージ `cherry.mastersmith.targetdb`）

| 層 | 部品 | 役割 |
|---|---|---|
| `domain` | `DatabaseProduct`（MYSQL・MARIADB・POSTGRESQL）、`TargetSchema`・`TargetTable`・`TargetColumn`・`TargetDbType`・`TargetForeignKey`（record） | スキーマの写し。変更できない値（`List.copyOf`）。空・空白だけのコメントは無し（BR2.5） |
| `domain` | `TargetSchemaResult`（sealed interface: `Success`・`Unconfigured`・`Unavailable`）、`UnavailableReason`（TIMEOUT・CONNECTION_FAILED）、`ReadPurpose`（GENERATE・COMPARE） | 契約 C1・C3 の結果の型。接続先・資格情報・例外の文言を持たない |
| `domain` | `SqlIdentifiers` | 識別子を種類に合った引用符で囲む純粋な関数（NFR6.1。今の問い合わせでは使わないが、将来の問い合わせの唯一の入り口として置く） |
| `config` | `TargetDbProperties`（record、`@ConfigurationProperties("mastersmith.target-db")`） | 種類・ホスト・ポート・DB 名・スキーマ名・ユーザー名・パスワード（すべて文字列）と、待ち時間（接続 3 秒、問い合わせ 生成 20 秒・照合 5 秒。照合の全体の上限は作らない、決定 B）・プール（最大 5、使わない接続 60 秒）の既定値。`toString` でパスワードを伏せ字（BR1.4） |
| `config` | `TargetDbSettings`（点検の結果: 使わない・不正・使える）と点検の処理 | 起動時に1回だけ点検する。1つも無ければ WARN なし、欠け・不正なら項目名だけの WARN を1件（BR1.2・BR1.3） |
| `config` | `TargetDataSourceConfig` | 使えるときだけ `targetDataSource`（HikariCP、`@Bean(defaultCandidate = false)`、読み取り専用、最小 0、`initializationFailTimeout = -1`、接続の待ち 3 秒、最大 5、`idleTimeout` 60 秒）を作る。JDBC の URL は種類・ホスト・ポート・DB 名だけから組み立て、資格情報は `username`・`password` で渡す（NFR4.5） |
| `repository` | `SchemaQueries`（種類ごとの読み手の型）、`MysqlSchemaQueries`（MySQL・MariaDB）、`PostgresSchemaQueries` | 情報スキーマへ、テーブルとビュー・カラム・主キー・外部キーの4種類を1回ずつ問い合わせ、写しを組み立てる（BR2.1〜BR2.7）。スキーマ名は `PreparedStatement` の値、文は固定の文字列。別のスキーマへの外部キーは条件で除く。MariaDB で MySQL と違う点が見つかれば読み手を分ける |
| `service` | `TargetSchemaReader`（契約のインターフェース）と実装 | `readSchema(ReadPurpose)`。設定が無ければ UNCONFIGURED。接続を得て、目的ごとの問い合わせの待ち（`setQueryTimeout`）を当てる（照合の全体の上限は作らない、決定 B）。失敗は SQLState と例外の型で TIMEOUT・CONNECTION_FAILED に分け、原因の種類だけを WARN。途中まで読んだ写しは返さない。`@Transactional` は付けない（内部DB のトランザクションに乗せない） |

ほかの単位（U3・U4）が使うのは `service` の `TargetSchemaReader` と `domain` の型だけとし、`config`・`repository` は外から使わせない。

## 4. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。

### Step 1: 骨組みと本番の設定

- [x] `targetdb` と `domain`・`config`・`repository`・`service` の `package-info.java`（日本語の説明、ライセンスヘッダー）を作る
- [x] `backend/src/main/resources/application.yaml` に `mastersmith.target-db.*` を足す。接続の7項目は環境変数 `MASTERSMITH_TARGET_DB_*` の参照だけ（既定は空）、待ち時間とプールは既定値つきの環境変数の参照
- [x] 対応するストーリー: US6.1（AC6.1.4・AC6.1.5 の前提）、BR1.1

### Step 2: 依存とテストの実行の準備

- [x] `gradle/libs.versions.toml` に JDBC ドライバー3つ（`com.mysql:mysql-connector-j`・`org.mariadb.jdbc:mariadb-java-client`・`org.postgresql:postgresql`、版は Spring Boot の BOM）と Testcontainers（MySQL・MariaDB・PostgreSQL のモジュール、JUnit 5 の連携、版は BOM）を足す
- [x] `backend/build.gradle.kts` でドライバーを `runtimeOnly`、Testcontainers を `testImplementation` に足し、`./gradlew :backend:resolveAndLockAll --write-locks` で `backend/gradle.lockfile` を更新する。推移依存で既存の部品の版が上がらないことを依存の木で確かめる（project.md の Corrections）
- [x] 各ドライバーのライセンス（GPL v2 ＋ Universal FOSS Exception、LGPL 2.1、BSD 2-Clause）を jar の中の文書で確かめ、配布物（WAR）に各ドライバーのライセンスの文書が含まれることを確かめる。jar に含まれていなければ `backend/src/main/resources/META-INF/third-party-licenses/` に置く
- [x] 単位のテストの実行のコマンド（`unit-test-instructions.md`）が、まだテストの無い状態で動くことを確かめる（`--tests` の指定で対象が0件でも失敗しない設定を確かめ、必要なら `filter.isFailOnNoMatchingTests` を単位の実行でだけ外す方法を instructions に書く）
- [x] 対応するストーリー: US1.1（Testcontainers の導入）、US1.2、NFR12

### Step 3: ドメインの値 — 実装

- [x] `DatabaseProduct`・`TargetSchema`・`TargetTable`・`TargetColumn`・`TargetDbType`・`TargetForeignKey`・`TargetSchemaResult`（`Success`・`Unconfigured`・`Unavailable`）・`UnavailableReason`・`ReadPurpose`・`SqlIdentifiers`
- [x] record の生成時の確かめ（必須の値、テーブルの中のカラム名の重なり、主キー・外部キーのカラムがカラムにあること、外部キーの件数の一致）。空・空白だけのコメントは無しに揃える
- [x] 対応するストーリー: US1.1・US1.2 の読み取りの部分（BR2.3・BR2.5）、契約 C1・C3

### Step 4: ドメインの値 — テスト（単体）

- [x] `TargetSchemaTest` ほか値のテスト（変更できないこと、空のコメント、重なり・欠けの拒否）
- [x] `SqlIdentifiersTest`（例のテストと、jqwik の性質ベースのテスト: 任意の文字列を引用符で囲んだ結果が、引用符の外に文字を出さない。MySQL・MariaDB は `` ` ``、PostgreSQL は `"`、中の引用符は二重）（NFR6.1）
- [x] `TargetSchemaResultTest`（結果の `toString` に資格情報が入りようがないこと、`Unavailable` の理由が2種類だけ）
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'` を実行し、通す

### Step 5: 設定と接続 — 実装

- [x] `TargetDbProperties`（伏せ字の `toString`）、`TargetDbSettings` と点検、`TargetDataSourceConfig`
- [x] 点検: 7項目がすべて空なら「使わない」（ログなし）。一部が空、種類が MySQL・MariaDB・PostgreSQL のどれでもない（大文字・小文字は問わない）、ポートが 1〜65535 の数でないときは「不正」とし、`mastersmith.target-db.schema` のような項目名だけを並べた WARN を1件出す（値は出さない）
- [x] `targetDataSource` は「使える」ときだけ作る。プールの名前は `mastersmith-target-db`
- [x] 対応するストーリー: US6.1（AC6.1.1・AC6.1.4・AC6.1.5）、BR1.1〜BR1.5・BR1.7、NFR4.1・NFR4.2・NFR4.3・NFR4.5・NFR4.6・NFR7.1・NFR7.2・NFR7.4〜NFR7.7

### Step 6: 設定と接続 — テスト

- [x] 単体: `TargetDbPropertiesTest`（`toString` にパスワードの値が無く、有無だけ）、`TargetDbSettingsTest`（1つも無い・各項目の欠け・種類の対応外・ポートが数でない・範囲外・正しい。WARN が1件で項目名だけ）、接続の組み立てのテスト（URL に資格情報が無い、読み取り専用・最小 0・最大 5・待ち 3 秒・60 秒）
- [x] 結合（組み込みの H2 の内部DB。コンテナは使わない）: `TargetDbStartupIT`
  - 設定が無い: 起動し、ヘルスチェックが 200、ログインができる、`targetDataSource` が無い（AC6.1.1）
  - 届かない先を設定: 起動し、ヘルスチェックが 200 で応答は状態だけ、対象DB のプールが一度も接続していない（AC6.1.2、NFR7.2・NFR7.3）
  - 一部が欠けている・種類が対応外: 起動し、WARN が1件で項目名だけ、値（接続先・ユーザー名・パスワード）がログに無い（AC6.1.5）
  - 型で注入される `DataSource`、Flyway、JPA のトランザクション、ヘルスチェックが内部DB を使う（NFR7.4、ADR-006）
- [x] 結合: `TargetDbSecretLeakIT`（`cherry.mastersmith` のロガーを TRACE にしてメソッドの追跡を有効にし、設定の型の文字列化と読み取りの呼び出しでパスワードの値がログに出ない。AC6.1.4、NFR4.2）
- [x] 単位の単体・結合のコマンドを実行し、通す

### Step 7: 読み取りの問い合わせ — 実装

- [x] `SchemaQueries`・`MysqlSchemaQueries`・`PostgresSchemaQueries`
- [x] MySQL・MariaDB: `information_schema` の `TABLES`（`TABLE_TYPE` でビューを見分け、`TABLE_COMMENT`）、`COLUMNS`（`ORDINAL_POSITION` の順、`DATA_TYPE`・長さ・精度・スケール・`IS_NULLABLE`・`COLUMN_DEFAULT`・`COLUMN_COMMENT`）、`KEY_COLUMN_USAGE` と `TABLE_CONSTRAINTS`（主キー、同じスキーマを参照する外部キー）
- [x] PostgreSQL: `information_schema` と `pg_catalog`（コメントは `obj_description`・`col_description`、ビューは `relkind`、マテリアライズドビュー・シノニムは含めない）
- [x] 問い合わせは種類ごとに1回（合わせて4回）で、テーブルの数に比例しない（BR2.2、NFR1.3）。書き込み・DDL を発行しない（BR1.6）
- [x] 対応するストーリー: US1.1・US1.2 の読み取りの部分（AC1.1.1・AC1.1.9・AC1.2.1）、BR2.1〜BR2.7、NFR6.2

### Step 8: 読み取りの問い合わせ — テスト（3種類の DB のコンテナ）

- [x] テストの共通部品（`src/test/java/cherry/mastersmith/targetdb/testsupport/`）
  - `TargetDbImages`: `mysql:8.4.x`・`mariadb:11.8.x`・`postgres:18.x` の版とダイジェスト（`@sha256:...`）の定数（1か所。compose の見本の DB と同じ値）
  - コンテナの実行環境の確かめの JUnit 5 の拡張: Docker に届かないとき、CI（環境変数 `CI=true`）では失敗させ、それ以外では「コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない」という WARN を出してテストを中断（飛ばした扱い）にする（NFR12.3）
  - テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と、読み取りの権限だけのアカウントを作り、終わったら消す（NFR12.2）
- [x] `MysqlSchemaQueriesIT`・`MariadbSchemaQueriesIT`・`PostgresSchemaQueriesIT`（共通の確かめを1か所にまとめ、3種類で同じ内容を確かめる）
  - テーブル2つとビュー1つが読め、ビューの印が付き、別のスキーマのテーブルは含まれない（AC1.1.1、BR2.1）
  - カラムが定義の順で、型の名前・長さ・精度・スケール・NULL を許すか・既定値・コメントが読める。空のコメントは無し（BR2.5・BR2.7）
  - 大文字・小文字を含む名前がそのまま（BR2.3）
  - 主キーが主キーの中の順、同じスキーマへの外部キーが読め、別のスキーマへの外部キーは含まれない（BR2.6）
  - 引用符・空白・セミコロンを含むテーブル名・カラム名が読め、ほかのテーブルとデータが変わらない（AC1.1.9、NFR6.4）
  - テーブルが0件のスキーマは0件の写し
  - 読み取りの権限だけのアカウントで成功する（AC1.1.10）
- [x] 単位の結合のコマンドを実行し、通す

### Step 9: 読み取りの口 — 実装

- [x] `TargetSchemaReader` と実装（3節）。照合の全体の上限は、注入できる時計で残り時間を計る（sleep や実時刻に依存しない）
- [x] 失敗の分け方: 接続の待ちの打ち切り・問い合わせの打ち切り（`SQLTimeoutException`、SQLState `57014` など）・照合の全体の上限は TIMEOUT。認証・接続・問い合わせのそのほかの失敗は CONNECTION_FAILED。例外の文言は読まない。ログは WARN で原因の種類だけ（キーと値で渡す）
- [x] 対応するストーリー: US1.1・US1.2 の読み取りの部分（AC1.1.7・AC1.1.8 の U1 側）、BR1.6・BR1.8・BR1.9、NFR1.1・NFR1.2、NFR4.4

### Step 10: 読み取りの口 — テスト

- [x] 単体: `TargetSchemaReaderTest`（設定が無い → UNCONFIGURED、目的ごとの問い合わせの待ちが当たる、照合の全体の上限を超えたら残りを問い合わせず TIMEOUT、打ち切りの例外 → TIMEOUT、そのほかの例外 → CONNECTION_FAILED、途中で失敗したら写しを返さない、ログに例外の文言・接続先・ユーザー名が無い）
- [x] 結合（コンテナ）: `TargetSchemaReaderIT`（3種類の DB で `readSchema` が SUCCESS、接続が読み取り専用（`Connection.isReadOnly()`）、誤ったパスワード・止めた DB で CONNECTION_FAILED、応答しない先（接続を受け付けて何も返さない手元の待ち受け）で待ち 3 秒の TIMEOUT。結果とログに接続先・ユーザー名・パスワード・JDBC の例外の文言が無い）（NFR4.4・NFR4.6）
- [x] 単位の単体・結合のコマンドを実行し、通す

### Step 11: 構造の検査

- [x] `src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java`（既存の `AuditBoundaryArchitectureTest` と同じ形）
  - `targetdb.config`・`targetdb.repository` は `targetdb` の外から使われない（`web` 層を含む。NFR4.1）
  - `targetdb` は `dsl`・`dslmanage`・`web` 層に依存しない（U1 は DSL を知らない）
  - `targetdb` は更新系の API（`Statement.executeUpdate`・`executeLargeUpdate`・`addBatch`、Spring の `JdbcTemplate` の更新）を使わない（NFR4.6）
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'` を実行し、通す

### Step 12: 静的解析の関門（SpotBugs の `SQL_`）

- [x] `backend/build.gradle.kts` の `spotbugsGate` を、priority 1 に加えて、パターンの名前が `SQL_` で始まる指摘を priority によらず失敗にする形に直す（team.md の Code Style、NFR6.3）
- [x] U1 の固定の問い合わせで指摘が出ないことを `./gradlew :backend:spotbugsGate` で確かめる。誤検知が出たときだけ `backend/config/spotbugs-exclude.xml` に理由を書いて外す

### Step 13: パッケージごとのカバレッジの下限

- [x] 既存のパッケージのパッケージごとの行・分岐のカバレッジを JaCoCo の報告で実測する
- [x] すべての既存のパッケージが行 80%・分岐 70% を満たせば、`jacocoTestCoverageVerification` にパッケージ単位（`element = "PACKAGE"`）の規則をすべてのパッケージに当てる。満たさないものがあれば、パッケージ単位の規則は新しく作るパッケージ（`cherry.mastersmith.targetdb` とその下、以後の単位で作るパッケージ）だけに当てる（team.md の Testing Posture）。除外は増やさない
- [x] 実測の値と、どちらにしたかを `code-summary.md` に記録する

### Step 14: 手元で試す対象DB（compose）と設定の見本

- [x] `compose.yaml` に profile `targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb` のサービスとボリュームを足す（版は `TargetDbImages` と同じ、ホストにポートを開けない、`app` の定義は変えない）
- [x] `docker/targetdb/<種類>/` に、見本のスキーマ（テーブル・ビュー・外部キー・コメント・記号を含む名前）と読み取りだけのアカウントを作る初期化の SQL（パスワードは環境変数から）を置く
- [x] Build and Test で使う 100 テーブル × 100 カラムを作るスクリプトを `docker/targetdb/` に置く
- [x] `.env.example` に `MASTERSMITH_TARGET_DB_*` と見本の DB の管理者・読み取りのアカウントのパスワードの項目を、値を空で足す
- [x] `docker compose --profile <profile> config` で compose の定義が正しいことを確かめる

### Step 15: 1コマンドの検査と文書

- [x] ルートの `build.gradle.kts` の `verifyIntegrationTest` の説明を「組み込みの H2 と対象DB のコンテナ」に直す（段の中身は変えない）
- [x] `README.md` に書く: 前提の道具にコンテナの実行環境（colima）、Testcontainers が colima の Docker に届く設定（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` など）、届かないときの警告の意味（その状態では統合しない）、環境変数の表に `MASTERSMITH_TARGET_DB_*`、手元で試す対象DB の起動・停止・消し方とアプリへの設定、対象DB のアカウントを読み取りの権限だけにすることの推奨、ライセンスの節に3つのドライバー
- [x] `./gradlew verify` をコンテナの実行環境が動いている状態で実行し、すべての段が通ることを確かめる

### Step 16: 記録

- [x] `code-summary.md`、`source-manifest.json`、`traceability.json` を作る（コード生成の段の手順 5）

## 5. ストーリーと手順の対応

| ストーリー | 受け入れ基準・要件 | 手順 |
|---|---|---|
| US6.1 | AC6.1.1〜AC6.1.5、NFR4.1〜4.3・4.5、NFR7.1〜7.7 | Step 1・5・6・11 |
| US1.1（読み取りの部分） | AC1.1.1・AC1.1.7・AC1.1.8・AC1.1.9・AC1.1.10、NFR4.4・4.6、NFR6.1〜6.4 | Step 2・3・4・7〜12・14 |
| US1.2（読み取りの部分） | AC1.2.1（3種類の DB の読み取り）、NFR1.1〜1.3、NFR12.1〜12.3 | Step 2・7・8・14・15 |
| 共通の完了の条件（B2） | カバレッジ（全体とパッケージごと）、SpotBugs の `SQL_`、ライセンス | Step 2・12・13・15 |

## 6. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の結合テストを置く。

| 部品 | 単体 | 結合 |
|---|---|---|
| ドメインの値（`TargetSchema` ほか、`SqlIdentifiers`） | 6〜8 件（性質ベースを含む） | — |
| 設定と点検（`TargetDbProperties`・`TargetDbSettings`・接続の組み立て） | 6〜8 件 | `TargetDbStartupIT`（4〜5 件）、`TargetDbSecretLeakIT`（1〜2 件） |
| 読み取りの問い合わせ（MySQL・MariaDB 用、PostgreSQL 用） | — | 3種類 × 6〜7 件 |
| 読み取りの口（`TargetSchemaReader`） | 6〜8 件 | 3種類 × 3〜4 件 |
| 構造の検査 | 3 件 | — |

## 7. 見直し（依頼者の Request Changes の反映）

最初のコード生成の後の依頼者の指示「決定Bに合わせて直す。また、SchemaQueriesで発行するSQLはテキストブロックで書いた方が見通しが良いと思う。」と、レビューの指摘（R-01〜R-03）を反映する。4節の Step 9・Step 10 の「照合の全体の上限」の記述は、この節の Step 17 で置き換える。

### 承認済みの文書との差（追加）

| 決定 | 文書 | 承認済みの記述 | 実装での扱い |
|---|---|---|---|
| 照合の全体の上限を作らない（決定 B） | `construction/u1-target-db/nfr-design/security-design.md` の 2節・4節・5節 | 照合の全体に 8 秒の上限 | 作らない。照合は問い合わせ1回ごとの待ち 5 秒だけ。応答しない対象DB では最悪 接続 3 秒＋5 秒×4 回（約 23 秒）になりうる |
| カラムの長さの型（R-02） | `construction/u1-target-db/functional-design/entities.md` の `TargetDbType.length`（integer）、契約 C1（int） | 整数（32 ビット） | `Long`。MySQL・MariaDB の `longtext` の長さ 4294967295 が 32 ビットに収まらないため。U3・U4 は `Long` として扱う |
| ドライバー自身のログを止める（R-03） | `construction/u1-target-db/nfr-design/security-design.md` 3節（例外の文言・接続先・ユーザー名をログに入れない） | ドライバーのログの扱いの記述は無い | 3つのドライバーのロガーを OFF にする。MariaDB のドライバーが認証の失敗をユーザー名つきで WARN に出すことを結合テストで確かめた。MySQL・PostgreSQL のドライバーも接続先などを文言に含めうるため、同じ扱いにそろえる。障害の調べは読み取りの口の WARN（原因の種類と SQLState）で行う |

### Step 17: 決定 B と SQL の書き方の見直し

- [x] 照合の全体の上限を外す: `PurposeTimeLimit` の全体の期限（時計で残り時間を計る仕組み）、`TargetDbProperties` の照合の全体の待ちの項目と既定値、`application.yaml`・`.env.example`・`README.md` の対応する項目と説明を消し、照合は問い合わせ1回ごとの待ち（5 秒）だけにする。使われなくなった時計の注入・部品は残さない
- [x] テストを直す: `TargetSchemaReaderTest` ほかの「照合の全体の上限」のテストを消し、照合で問い合わせ1回ごとに 5 秒の待ちが当たること、全体では打ち切らないこと（4回の問い合わせがそれぞれ待ちの内なら成功すること）を確かめるテストにする
- [x] `MysqlSchemaQueries`・`PostgresSchemaQueries` の SQL を、文字列の連結ではなく Java のテキストブロック（`"""`）で書き直す。文は固定の文字列のまま（識別子を組み込まない、スキーマ名は `PreparedStatement` の値）。SpotBugs の `SQL_` の指摘が 0 件のままであることを確かめる
- [x] R-03 の理由（なぜ3つのドライバーのログを止めたか、障害の調べ方）を `application.yaml` の該当の設定のコメントと `README.md` に書く
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'` と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'` を実行して通し、最後に `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` ですべての段を通す
- [x] `code-summary.md`・`source-manifest.json`・`traceability.json` を、見直しの後の中身に合わせて直す（`code-summary.md` にこの節の3つの差を載せる）


## 8. Build and Test からの戻し（Loop-back 1）

Build and Test で、コンテナの実行環境に届かない状態で対象DB の結合テストを実行したところ、決めたとおりに警告を出して飛ばされる（SKIPPED）一方で、`AbstractTargetSchemaReaderIT` の3クラス（と U4 の `DslTargetDbIT`）がクラスの後片付けで `initializationError` になり、`integrationTest` のタスクが失敗した（NFR12.3 が Not Met。`construction/build-and-test/test-results.md` の Loop-Back Log）。依頼者の選択は「Retry with fix」（「Code Generation に戻って両方直す」）。

原因: `@ExtendWith({ContainerRuntimeCheck.class, OutputCaptureExtension.class})` の順のため、`ContainerRuntimeCheck.beforeAll` がテストを飛ばす例外を投げると `OutputCaptureExtension.beforeAll` が呼ばれず、逆の順で呼ばれる `afterAll` の `OutputCapture.pop` が空の待ち行列で `NoSuchElementException` を出す。

### Step 18: 拡張の登録の順の直しと再現の確かめ

- [x] `backend/src/test/java/cherry/mastersmith/targetdb/service/AbstractTargetSchemaReaderIT.java` の登録を `@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})` に入れ替え、なぜこの順でなければならないか（前処理は登録の順、後処理は逆の順に呼ばれ、ログの取り込みの開始より先に飛ばすと後片付けが失敗する）を日本語のコメントで書く。U4 の `DslTargetDbIT` は U4 の手順で直す
- [x] 再現の確かめ（不具合を直すときは再現するテストを同じ変更に含める決まり）: 新しい依存を足さずに、ArchUnit（既存）で「`ContainerRuntimeCheck` と `OutputCaptureExtension` の両方を `@ExtendWith` で登録するテストのクラスは、`OutputCaptureExtension` を先に登録する」ことを確かめる構造の検査を `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/` に足す（直す前の順では失敗し、直した後は通ることを確かめる）。テストの説明文は英語
- [x] コンテナの実行環境に届かない状態（Docker の接続先を存在しない場所に向け、`CI` を外す）で `./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'` を実行し、失敗 0・警告つきの SKIPPED になることを確かめて、出力の件数を `code-summary.md` に記録する。colima は止めない（配備したアプリも止まるため）
- [x] 届く状態に戻して `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'` と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'` を通す
- [x] `code-summary.md` に戻しの記録の節を足し、`source-manifest.json` と `traceability.json`（NFR12.3 の対象を足した確かめに向ける）を直す

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
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24)"
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
  "input_sha256": "sha256:246b734731604f4b9f4d77cecbeda1baf18157fdfe148f6c242e9cf2724ab8f9",
  "contract_sha256": "sha256:fad83f4781d495b9c1188364b74cbb3df645f2324fe0ecc57c74ab31a9426958"
}
```

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1、テストの実行の準備は Step 2、ドメインの値（データの形）は Step 3・4、設定と接続（データの持ち方）は Step 5・6、DB アクセス（読み取りの問い合わせ）は Step 7・8、業務処理（読み取りの口）は Step 9・10、環境とビルドの設定は Step 12〜15、記録は Step 16。U1 は library の単位で API と画面を持たないため、API・画面の層の手順は無い。
