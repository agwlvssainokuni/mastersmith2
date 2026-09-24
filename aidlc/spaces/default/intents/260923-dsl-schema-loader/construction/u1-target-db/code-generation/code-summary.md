# Code Summary — U1 対象DB（u1-target-db）

承認済みの計画（`code-generation-plan.md`）の Step 1〜16 を、test-after（層ごとに実装 → その層のテストを書いて実行 → 通ってから次の層）で実行した結果と、依頼者の Request Changes を反映した Step 17（計画の 7節）の見直しの結果を記録する。Bolt は B2。この文書の件数・カバレッジ・一覧は見直しの後のもの。

## 1. 作った・変えたファイル

一覧の正本は `source-manifest.json`（69 件。見直しで `QueryTimeLimit`・`PurposeTimeLimit` を消した）。まとめは次のとおり。

| 区分 | ファイル | 内容 |
|---|---|---|
| ドメイン | `backend/src/main/java/cherry/mastersmith/targetdb/domain/`（12 ファイル） | `DatabaseProduct`・`TargetSchema`・`TargetTable`・`TargetColumn`・`TargetDbType`・`TargetForeignKey`・`TargetSchemaResult`（`Success`・`Unconfigured`・`Unavailable`）・`UnavailableReason`・`ReadPurpose`・`SqlIdentifiers`、確かめの共通の処理 `DomainValues` |
| 設定と接続 | `backend/src/main/java/cherry/mastersmith/targetdb/config/`（5 ファイル） | `TargetDbProperties`（伏せ字の `toString`）・`TargetDbSettings`（使わない・不正・使える の点検）・`TargetDbUsableCondition`・`TargetDataSourceConfig`（`targetDataSource`、既定の候補にしない） |
| DB アクセス | `backend/src/main/java/cherry/mastersmith/targetdb/repository/`（5 ファイル） | `SchemaQueries`・`MysqlSchemaQueries`（MySQL・MariaDB）・`PostgresSchemaQueries`（問い合わせの文はテキストブロックの定数）・`SchemaRows`（行の変換と組み立て） |
| 業務処理 | `backend/src/main/java/cherry/mastersmith/targetdb/service/`（3 ファイル） | `TargetSchemaReader`（契約 C1・C3）・`JdbcTargetSchemaReader`（目的ごとの問い合わせ1回の待ちを当てる。全体の上限は無い）。ほかに `targetdb/package-info.java`（合わせて本番のコード 26 ファイル） |
| テスト | `backend/src/test/java/cherry/mastersmith/targetdb/`（26 ファイル） | 単体 9 クラス、結合 8 クラス（抽象の基底 2 つを含む 10 ファイル）、共通部品 7 ファイル `testsupport/`（`TargetDbImages`・`ContainerRuntimeCheck`・`TargetDbTestDatabase`・`MysqlFamilyTestDatabase`・`PostgresTestDatabase`・`TargetDbFixture`・`SilentServer`） |
| 設定 | `backend/src/main/resources/application.yaml` | `mastersmith.target-db.*`（7 項目は `MASTERSMITH_TARGET_DB_*` の参照だけで既定は空、待ち時間とプールは既定値つき）。3つのドライバー自身のログを OFF（理由と障害の調べ方をコメントに書いた） |
| ライセンス | `backend/src/main/resources/META-INF/third-party-licenses/` | MariaDB Connector/J の LGPL 2.1 の文書と説明（jar に文書が無いため） |
| ビルド | `gradle/libs.versions.toml`・`backend/build.gradle.kts`・`backend/gradle.lockfile`・`build.gradle.kts` | ドライバー3つ（`runtimeOnly`）と Testcontainers（`testImplementation`）、テストのログに SKIPPED、パッケージごとのカバレッジの規則、`spotbugsGate` の `SQL_`、`verifyIntegrationTest` の説明 |
| 手元の対象DB | `compose.yaml`・`docker/targetdb/`・`.env.example` | profile `targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb`、見本のスキーマと読み取りだけのアカウントの初期化、100 × 100 の生成の台本、環境変数の見本 |
| 文書 | `README.md` | 前提の道具、1コマンドの検査の段、環境変数の表、「対象DB（利用者の業務データの DB）」の節（Testcontainers と colima、届かないときの警告、手元の対象DB、読み取りの権限の推奨）、ライセンスの節 |

## 2. 主な判断

| 判断 | 理由 |
|---|---|
| 接続のプールは引数なしで作り、設定を写す（`copyStateTo`） | 設定を渡す作り方の HikariCP 7 はプールをすぐに始める。引数なしなら初めて接続を借りるまでプールが始まらず、起動時に接続しないこと（NFR7.2）を「プールの管理の口が無い」ことで確かめられる |
| 失敗の分け方に `SocketTimeoutException`（原因の連鎖の中）を TIMEOUT として足した | HikariCP は接続の待ち（3 秒）から `DriverManager` のログインの待ち（3 秒）を決めるため、MySQL Connector/J が応答しない相手との接続の途中で同じ時刻に失敗し、プールの打ち切りと競って CONNECTION_FAILED になった。例外の型で見分け、文言は読まない |
| ドライバーの接続・通信の待ち（`connectTimeout`・`socketTimeout`・PostgreSQL の `loginTimeout`）を、プールの待ちより 2 秒長く・問い合わせの待ちより 5 秒長く置いた | プールの待ち（3 秒）が先に切れて、応答しない相手を TIMEOUT と見分けるため。ドライバーの待ちは止まった通信の後始末のため。MySQL・MariaDB は手元のファイルを送る機能（`allowLoadLocalInfile`・`allowLocalInfile`）を無効にした |
| ホストと DB の名前で、URL で意味を持つ記号（`? & / ; # @ = :` など）を「不正」にした | 資格情報や接続の設定を URL に差し込まれないため（NFR4.5）。BR1.3 の「不正」に含め、項目名だけを WARN で出す |
| 待ち時間とプールの値も点検し、接続の部品の受け付けない値（接続の待ち 250 ミリ秒未満、問い合わせの待ち 1 秒未満など）を「不正」にした | 不正な値でプールの作成が例外になり起動が止まるのを避け、BR1.3 と同じ扱い（項目名だけの WARN、起動は続ける）にするため |
| 3つのドライバー自身のログを OFF（`application.yaml`） | 結合テストで、MariaDB のドライバーが認証の失敗をユーザー名つきで WARN に出すことが分かった（NFR4.4 に反する）。失敗は読み取りの口が原因の種類と SQLState だけを WARN で出す |
| PostgreSQL の主キー・外部キーは `pg_constraint` から読む | `information_schema.table_constraints` は SELECT だけの権限のアカウントに制約を返さないため（AC1.1.10）。テーブル・カラムは権限で見える範囲が揃う `information_schema` を使い、コメントとビューの見分けは `pg_class` で得る |
| 主キー・外部キーは、カラムの一覧に出るカラムだけで組み立てる | MariaDB のシステムバージョニングの表は、カラムの一覧に出ない隠れたカラム `row_end` を主キーに含める（結合テストで見つけた）。主キーは読めるカラムだけにし、読めないカラムを含む外部キーは入れない。読める権限のカラムが1つも無いテーブルは写しに入れられないため除き、除いた数を WARN で出す |
| 型の長さは `Long` で持つ | MySQL・MariaDB の `longtext` の長さ 4294967295 が 32 ビットの整数に収まらないため |
| テストの DB のイメージは、Testcontainers にはダイジェストだけで渡す（`mysql@sha256:...`） | 版とダイジェストを両方書くと、Testcontainers は版をイメージの名前の一部と読む。版は定数に別に記録し、compose は `版@ダイジェスト` で指定する |
| テストのログの出し方に SKIPPED を足した（`backend/build.gradle.kts`） | コンテナの実行環境が無いときに、対象DB のテストが飛ばされたことを Gradle の出力に出し、黙って飛ばさないため（NFR12.3） |
| パッケージごとのカバレッジの規則は、既存の 22 パッケージを一覧で外す形にした | 既存のパッケージに単独で下限を下回るものがあった（4節）。一覧に無いパッケージ（`targetdb` と以後に作るもの）は自動で下限の対象になる |

## 3. テストの件数とカバレッジ（実測）

見直しの後に、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を colima（CPU 4・メモリ 6GiB）が動いている状態で実行した（2026-09-24、全段が成功、14 分 8 秒。見直しの前の実行は 9 分 23 秒で、差は同じ PC のほかの負荷による揺れと見ている。段の中身は変えていない）。

| 区分 | 全体 | うち U1 | 失敗・飛ばし |
|---|---|---|---|
| 単体テスト（`*Test`） | 466 件 | 79 件 | 0 件 |
| 結合テスト（`*IT`） | 291 件 | 47 件（3種類の DB のコンテナを使うもの 41 件） | 0 件 |
| 画面（Vitest） | 167 件 | — | 0 件 |

見直しの前（最初のコード生成）は単体 465 件（うち U1 78 件）・結合 291 件だった。単体の差 +1 は、`TargetSchemaReaderTest` で全体の上限のテスト3件（照合の全体の上限・生成に全体の上限が無い・残りの秒の計算）を消し、4件（照合は4回とも 5 秒・生成は4回とも 20 秒・設定した待ちが目的ごとに当たる・秒への変換）を足したため。

U1 の結合テストの時間は合わせて約 74 秒（読み取りの口の結合テストは DB ごとに約 17 秒、読み手の結合テストは 1〜6 秒）。着手の前の基準は単体 387 件・結合 244 件で、すべて通っていた。

| カバレッジ | 行 | 分岐 |
|---|---|---|
| 全体（バックエンド） | 96.6%（1783/1845） | 92.7%（587/633） |
| `cherry.mastersmith.targetdb.config` | 99.2%（125/126） | 98.5%（66/67） |
| `cherry.mastersmith.targetdb.domain` | 100.0%（117/117） | 96.7%（58/60） |
| `cherry.mastersmith.targetdb.repository` | 96.0%（119/124） | 87.5%（28/32） |
| `cherry.mastersmith.targetdb.service` | 100.0%（29/29） | 100.0%（18/18） |

計測の除外は既存のもの（起動クラスと `*Properties`）だけで、U1 のために足していない。`TargetDbProperties` は既存の除外に当たる。

## 4. パッケージごとのカバレッジの実測と Step 13 の判断

U1 の変更の前の既存のパッケージ（基準の実行の JaCoCo の報告）:

| パッケージ | 行 | 分岐 | 下限（行 80%・分岐 70%） |
|---|---|---|---|
| `access.domain` | 100.0% | 100.0% | 満たす |
| `access.service` | 100.0% | — | 満たす |
| `access.web` | 100.0% | 93.8% | 満たす |
| `audit.domain` | 99.0% | 92.6% | 満たす |
| `audit.repository` | —（インターフェースだけ） | — | — |
| `audit.service` | **77.2%** | 80.0% | **行が下回る** |
| `auth.domain` | 97.9% | 100.0% | 満たす |
| `auth.repository` | 93.9% | **50.0%** | **分岐が下回る** |
| `auth.service` | 97.9% | 95.8% | 満たす |
| `auth.web` | 100.0% | 95.0% | 満たす |
| `common.error.domain` | 100.0% | 94.4% | 満たす |
| `common.error.service` | 100.0% | 81.2% | 満たす |
| `common.error.web` | 96.8% | 87.6% | 満たす |
| `common.health` | **79.2%** | 100.0% | **行が下回る** |
| `common.i18n.domain` | 100.0% | 89.3% | 満たす |
| `common.observability` | 97.5% | 100.0% | 満たす |
| `common.security` | 100.0% | 100.0% | 満たす |
| `common.web` | 94.3% | 84.4% | 満たす |
| `config` | 92.4% | 73.1% | 満たす |
| `user.domain` | 100.0% | 100.0% | 満たす |
| `user.repository` | —（インターフェースだけ） | — | — |
| `user.service` | 100.0% | 100.0% | 満たす |

判断: 単独で下限を下回る既存のパッケージ（`audit.service`・`auth.repository`・`common.health`）があるため、team.md の Testing Posture に従い、パッケージごとの下限は新しく作るパッケージだけに当てた。`backend/build.gradle.kts` の `packagesJudgedByTotal` に既存の 22 パッケージを並べて外し、既存のパッケージは全体の合計で判定する。一覧は増やさない（新しいパッケージは自動で対象になる）。規則が `targetdb` の4つのパッケージだけを評価することは、一時的に行の下限を 99.9% にした実行で確かめ（`targetdb.repository` と `targetdb.config` だけが違反として出た）、元の値（80%・70%）に戻した。

## 5. 計画からのずれ

| ずれ | 扱い |
|---|---|
| `TargetDbSecretLeakIT` と `TargetDbStartupIT` の読み取りの呼び出しの確かめは、Step 6 では読み取りの口が無いため、Step 10 で足した | test-after の順を守ったため。Step 6 の時点では設定の型の文字列化と TRACE の起動を確かめ、Step 10 で `readSchema` の呼び出し（TRACE の ENTER・EXIT、設定が無いときの UNCONFIGURED）を足した |
| 読み取りの口の結合テストは、計画の `TargetSchemaReaderIT` を、共通の基底 `AbstractTargetSchemaReaderIT` と DB ごとの `MysqlTargetSchemaReaderIT`・`MariadbTargetSchemaReaderIT`・`PostgresTargetSchemaReaderIT` にした | unit-test-instructions.md の「コンテナはテストのクラスごとに1つ起動」と、DB ごとに絞って実行するコマンド（`*Postgres*`）に合わせるため。確かめる内容は計画のとおり |
| AC6.1.3（監査は内部DB、対象DB に Flyway の履歴の表もアプリの表も無い）を、読み取りの口の結合テスト（3種類の DB）で確かめた | 計画の表では US6.1 の手順に AC6.1.3 の置き場が明記されていなかった。対象DB のコンテナが要るため、コンテナを使う結合テストに置いた |
| 読み手の結合テストに、DB ごとの読み取りの範囲の確かめを足した（PostgreSQL のマテリアライズドビュー・シーケンス・パーティションの子の除外と、パーティションの親への外部キー、MariaDB のシーケンスの除外とシステムバージョニングの表） | BR2.1 の「含めないもの」を本物の DB で確かめるため。MariaDB の隠れた主キーのカラムはここで見つけ、組み立てを直した（2節） |
| 計画の「MariaDB で MySQL と違う点が見つかれば読み手を分ける」 | 問い合わせは分けずに済んだ。違いは、文字列の既定値を引用符で囲んで返すこと（`'new'`。MySQL は `new`）と、システムバージョニングの表の隠れたカラムで、前者は既定値を DB が返したまま入れる決まりどおり、後者は共通の組み立てで扱った |
| 計画の Step 2 の「`--tests` の対象が0件でも失敗しない設定」 | 最初のテストの前に単位のコマンドを実行し、「一致するテストが無い」で失敗することを確かめた。unit-test-instructions.md がこれを想定どおりと書いているため、設定も instructions も変えていない |
| `.env` は読まず、compose の定義の確かめは `docker compose --env-file .env.example --profile <profile> config` で行った | `.env` を開かないため |
| 見本の対象DB の初期化の SQL と台本、100 × 100 の生成の台本は、compose ではなく使い捨てのコンテナ（`docker run`）で3種類とも実際に動かして確かめた | 配備済みのアプリのコンテナ（`mastersmith-app-1`）に触れないため。3種類とも、見本の5つ（表4・ビュー1）と `large` の 100 表・10000 カラムを読み取りのアカウントで読め、書き込みは拒否された |

## 6. ライセンスの確かめの結果

| 部品 | 版 | ライセンス（確かめた場所） | 配布物（WAR）の文書 |
|---|---|---|---|
| MySQL Connector/J | 9.7.0 | GPL v2 ＋ Universal FOSS Exception 1.0（jar の中の `LICENSE`） | jar の中の `LICENSE`（`WEB-INF/lib/mysql-connector-j-9.7.0.jar`） |
| MariaDB Connector/J | 3.5.10 | LGPL-2.1-or-later（POM。jar の中に文書は無い） | `WEB-INF/classes/META-INF/third-party-licenses/mariadb-java-client-LGPL-2.1.txt`（gnu.org の原文。SHA-256 `20e50fe7aae3e56378ebf0417d9de904f55a0e61e4df315333e632a4d3555d95`） |
| PostgreSQL JDBC | 42.7.13 | BSD 2-Clause（jar の中の `META-INF/LICENSE`） | jar の中の文書 |
| Checker Framework qualifiers（PostgreSQL JDBC の依存） | 3.55.1 | MIT（jar の中の `META-INF/LICENSE.txt`） | jar の中の文書 |
| Testcontainers（テストだけ） | 2.0.5 | MIT | 配布物に含めない |

依存の木で、実行時の依存（WAR に入るもの）に増えたのはドライバー3つと `checker-qual` だけで、既存の部品の版は変わっていない。テストの依存では、Testcontainers が持ち込む `org.jetbrains:annotations` が 13.0 から 17.0.0 になった（注釈だけの部品で、実行時の依存は 13.0 のまま）。OSV-Scanner の判定は失敗 0 件・警告 0 件。

## 7. 固定したイメージのダイジェスト

`docker pull` の後に `docker image inspect` と `docker buildx imagetools inspect` で実際の値を取った（複数の CPU をまとめた一覧のダイジェスト）。正本は `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java` で、`compose.yaml` も同じ値を使う。

| DB | 版 | ダイジェスト |
|---|---|---|
| MySQL | 8.4.11 | `sha256:0744ee5ef89ce6ccfa13de3e579fe6b9e27f93dd70da9c06d2c908b1b193fb8d` |
| MariaDB | 11.8.9 | `sha256:79d59758afc91b89b120b0a8904d637f5a3b3e1c4900f29b740d6d46c72fef68` |
| PostgreSQL | 18.6 | `sha256:86c951e05bf56c93d95d397747fb8820ac76cc3bedb78f43abd83eedbe3666ae` |

## 8. 後の単位への申し送り

- U3 の型の分類（`functional-design/rules.md` の「tinyint(1)・bit(1) は BOOLEAN」「型の名前と長さで判定」）: U1 は MySQL・MariaDB の `DATA_TYPE`・`CHARACTER_MAXIMUM_LENGTH`・`NUMERIC_PRECISION` をそのまま返すため、`tinyint(1)` は型の名前 `tinyint`・精度 3、`bit(1)` は `bit`・精度 1 になり、長さからは見分けられない。U3 のコード生成で判定の仕方を決める必要がある（U1 の写しに `COLUMN_TYPE` を足すかどうかを含む）。
- 既定値の書き方は DB ごとに違う（例: 文字列 `new` の既定値は MySQL `new`、MariaDB `'new'`、PostgreSQL `'new'::character varying`）。U1 は DB が返したまま入れる（entities.md）。
- 手元でテストを実行するときは、README のとおり `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに設定しておく（Testcontainers は Docker の context を読まない）。

## 9. 見直し（計画の Step 17。依頼者の Request Changes の反映）

依頼者の指示「決定Bに合わせて直す。また、SchemaQueriesで発行するSQLはテキストブロックで書いた方が見通しが良いと思う。」と、レビューの指摘（R-01〜R-03）を反映した。

### 9.1 承認済みの文書との差（計画の 7節の3つ）

| 決定 | 文書 | 承認済みの記述 | 実装での扱い |
|---|---|---|---|
| 照合の全体の上限を作らない（決定 B） | `construction/u1-target-db/nfr-design/security-design.md` の 2節・4節・5節 | 照合の全体に 8 秒の上限 | 作らない。照合は問い合わせ1回ごとの待ち 5 秒だけ。応答しない対象DB では最悪 接続 3 秒＋5 秒×4 回（約 23 秒）になりうる。README の「対象DB」の節と `application.yaml` のコメントに書いた |
| カラムの長さの型（R-02） | `construction/u1-target-db/functional-design/entities.md` の `TargetDbType.length`（integer）、契約 C1（int） | 整数（32 ビット） | `Long`。MySQL・MariaDB の `longtext` の長さ 4294967295 が 32 ビットに収まらないため。U3・U4 は `Long` として扱う |
| ドライバー自身のログを止める（R-03） | `construction/u1-target-db/nfr-design/security-design.md` 3節 | ドライバーのログの扱いの記述は無い | 3つのドライバーのロガーを OFF。理由（MariaDB のドライバーが認証の失敗をユーザー名つきで WARN に出すことを結合テストで確かめた。MySQL・PostgreSQL も接続先などを文言に含めうるため同じ扱い）と障害の調べ方（読み取りの口の WARN の `reason`・`sqlState` で絞り、DB の付属のクライアントか対象DB の側のログで調べる。配備した環境でドライバーのログを有効にしない）を、`application.yaml` の `logging.level` のコメントと README の「対象DB」の節に書いた |

承認済みの設計の文書は書き換えていない。

### 9.2 変えたこと

| 対象 | 変更 |
|---|---|
| 照合の全体の上限（決定 B） | `PurposeTimeLimit`（時計で残りを計る仕組み）と、全体の上限の残りを見るための `QueryTimeLimit` を消した。`SchemaQueries.read` は問い合わせ1回の待ち（秒の数）を受け取り、4回とも同じ値を `setQueryTimeout` に当てる。`JdbcTargetSchemaReader` から時計（`Clock`）の注入を外した（時計の Bean は U2 が提供する既存のもので、U1 は使わなくなった）。`TargetDbProperties` の `compareOverallTimeout`（既定 8 秒）、`TargetDbSettings` の項目の名前 `compare-overall-timeout` とその点検、`Usable.overallTimeout` を消した |
| 設定と文書 | `application.yaml` の `compare-overall-timeout`、`.env.example` と README の環境変数の表の `MASTERSMITH_TARGET_DB_COMPARE_OVERALL_TIMEOUT` を消した。`ReadPurpose`・`UnavailableReason`・`TargetSchemaReader` の説明から全体の上限の記述を外した |
| テスト | `TargetSchemaReaderTest` の全体の上限のテストを消し、照合で4回の問い合わせそれぞれの前に 5 秒が当たり（後の回ほど減ることが無い）成功すること、生成で4回とも 20 秒が当たること、設定した待ち（生成 30 秒・照合 7 秒）が目的ごとに当たること、秒への変換（端数の切り捨て・int の上限）を確かめる形にした。時計を進める方法をやめ、差し替えた `PreparedStatement` に渡る値で確かめる。`TargetDbPropertiesTest`・`TargetDbSettingsTest`・`TargetDataSourceConfigTest` の全体の上限の確かめを外し、`AbstractSchemaQueriesIT` は待ちを数（20 秒）で渡す |
| SQL の書き方 | `MysqlSchemaQueries`・`PostgresSchemaQueries` の4つずつの問い合わせを、文字列の連結から Java のテキストブロック（`"""`）の定数に書き直した。条件・結合・並べ方は変えていない（1行に1つの条件の形に並べ直しただけ）。文は固定の文字列のままで識別子を組み込まず、スキーマ名は `PreparedStatement` の値 |

### 9.3 確かめた結果

- `./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.targetdb.*'`: 79 件成功（失敗・飛ばし 0）。
- `./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'`: 47 件成功（失敗・飛ばし 0。3種類の DB のコンテナを使用）。テキストブロックに直した問い合わせで、3種類の DB の読み取りの結果が変わらないことをここで確かめた。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`: 全段が成功（3節の件数とカバレッジ）。
- SpotBugs（`backend/build/reports/spotbugs/main.xml`）: パターンの名前が `SQL_` で始まる指摘は 0 件のまま。`spotbugsGate` も通った。除外の設定は足していない。
- カバレッジの下限（全体とパッケージごと）と除外は変えていない。数値の目標（接続 3 秒、問い合わせ 生成 20 秒・照合 5 秒、プール 最大 5・最小 0・60 秒）も変えていない。

