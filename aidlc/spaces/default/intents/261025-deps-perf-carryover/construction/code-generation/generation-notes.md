# 生成の記録（261025-deps-perf-carryover）

## B1: Spring Boot 4.1.2（Step 2〜7）

作業ブランチ: `chore/261025-deps-perf-carryover-spring-boot`。コミットはしていない（変更はワークツリーに残す）。

### Step 2 準備
- colima は動いている（`docker info` の Context: colima）。`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を README のとおり渡した。対象DB のテストは実行された（SKIPPED の9件は他の DB 種別向けの条件付きテストで、DB のコンテナの不在によるものではない）。
- `osv-scanner` は `/opt/homebrew/bin/osv-scanner` にある。
- 非対話のシェルでは `node` が PATH に無く `:checkToolchain` が失敗した。`PATH` に `$HOME/.volta/bin`（Node 24.21.0）を足して流した。

### Step 3 依存の解決（Spring Boot 4.1.2）
`runtimeClasspath` の解決で、Jackson は 3.1.7、OpenTelemetry API は 1.62.0、Tomcat は BOM が 11.0.27（上書きが残る間は 11.0.26）と確かめた。

### Step 4 上書きを外した差（`backend/gradle.lockfile`）
- 外した: `libs.versions.toml` の `tomcat`・`jackson`・`jackson-bom`、`backend/build.gradle.kts` の `resolutionStrategy` と `jacksonBom`。
- Tomcat 11.0.26 → 11.0.27（tomcat-embed-core・el・websocket）。Jackson は 3.1.7 のまま（`jackson-bom` が `providedRuntime` から外れただけ）。
- Spring Boot の部品 4.1.1 → 4.1.2、Spring Framework 7.0.9 → 7.0.10、Spring Security 7.1.1 → 7.1.2。
- そのほか: micrometer 1.17.1 → 1.17.2（tracing 1.7.1 → 1.7.2）、hibernate-core 7.4.5 → 7.4.13、hibernate-validator 9.1.3 → 9.1.5、hibernate-models 1.1.1 → 1.1.2、postgresql 42.7.13 → 42.7.14、byte-buddy 1.18.11 → 1.18.15、protobuf-java 4.35.1 → 4.36.2、slf4j 2.0.18/2.0.19 → 2.0.20（spotbugs の設定は 2.0.18 のまま）。
- networknt が Jackson 3.2 系を引き込むことは無かった（Jackson は 3.1.7 のまま）。

### Step 5 固定の見直し
- `opentelemetry-logback-appender` 2.28.1-alpha: OpenTelemetry が 1.62.0 のままのため固定を残す。外す時期は「Spring Boot の管理の OpenTelemetry が 1.63 以上になったとき」。
- networknt 3.0.6: Spring Boot の管理の Jackson が 3.1.7 のため固定を残す。外す時期は「Jackson が 3.2 系以上になったとき」。
- `.github/dependabot.yml`: Jackson の BOM の項（`ignore` とコメント）を外し、残す2項のコメントを更新した。

### Step 6 外部エクスポート
- `OtlpLogExportIT`（1件）・`ExternalExportIT`（`Enabled`・`Unreachable` を含む 4件）を単独で流して通った（失敗 0）。`OtlpLogExportIT` は `mastersmith.observability.export.enabled=true` でアプリ全体を起動するため、`OpenTelemetryAppender.install` の失敗が無いことの確かめを兼ねる。
- 使い捨ての別の起動（外部エクスポート有効の手動起動）は行っていない。理由: 上の結合テストが同じ起動を行い、Gradle の中で再現できるため。

### Step 7 verify と osvScan
- 計画の外の失敗と直し（下の「計画外の変更」）をはさんで、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`caffeinate -dims` で包む）を流した。
  - 1回目: 20分17秒。結合テストが 13 件落ちた（下記の原因。このあとの直しで解決）。
  - 2回目（直し後）: BUILD SUCCESSFUL、21分38秒。単体テスト 2589 件（失敗 0・SKIPPED 0）、結合テスト 1389 件（失敗 0・SKIPPED 9。他の DB 種別向けの条件付きテスト）。
- `./gradlew osvScan --rerun`: 失敗の条件に当たるもの 0 件、警告 15 件（vendor の開発用の依存の警告で、変更前から）。
- 2回目の間、`pmset -g log` に眠り・DarkWake は無かった（最後の Sleep/Wake は 21:45 JST で、流し始め 22:28 JST より前）。

### 計画外の変更（テストの補助）
- 症状: `AuditWriteFailureIT`・`AuditWriteTimingIT`・`GroupAuditWriteFailureIT`・`UserAdminAuditWriteFailureIT`・`PasswordChangedAuditIT` の 13 件が、ログの出来事の件数が 1 でなく 3（TRACE の ENTER・EXIT が余分）で落ちた。単独では通り、`*SecretLeakIT` と同じ JVM で流すと再現した（4.1.1 では同じ組が通る）。
- 原因: `*SecretLeakIT` などが `logging.level.cherry.mastersmith…=TRACE` を指定した文脈を起動すると、ロガーの水準が JVM に残る。Spring Boot 4.1.1 まではキャッシュ中の文脈の一時停止で `LoggingApplicationListener.Lifecycle.stop()`（ロギングの後片付け）が走って水準が戻っていたが、4.1.2 でこの Lifecycle の `isPauseable()` が偽になった（ソースの差で確認）ため、TRACE が次のテストへ漏れる。
- 直し: `backend/src/test/java/cherry/mastersmith/common/testsupport/LoggingLevelResetTestExecutionListener.java` を足し、`backend/src/test/resources/META-INF/spring.factories` で登録した。Spring の文脈を使うテストの前に、TRACE・DEBUG を明示したロガーのうち文脈の設定（`logging.level.*`）に無いものを継承へ戻し、設定にあるものを設定どおりにする。YAML の `OFF` が `false` として届くため OFF に読む（これを抜くと `org.hibernate.orm.jdbc.error` の OFF が外れ、`*UniqueViolationSecretLeakIT` 8 件が落ちた。その場で直した）。Spring Boot が既定で入れる WARN・OFF などの水準は触らない。
- 確かめ: 原因の再現（SecretLeak + Audit の組）で直す前に落ち、直した後に通ることを確かめた。品質の目標・閾値・テストは弱めていない。

## B2: Dependabot の更新と固定先のそろえ（Step 8〜13）

作業ブランチ: `chore/261025-deps-perf-carryover-deps`。コミットはしていない（変更はワークツリーに残す）。

### Step 8 知らせの状態
- 作業の途中で指揮役から更新の連絡があり、`gh pr list` の今の状態は 17 件（#54・#57・#58・#63・#66 は #69・#72・#71・#73・#75 に置き換わり、#70・#74 が新規）。この版で進めた。計画の Step 9 の collector 0.163.1 は 0.163.2 に変わった（差）。
- `vendor/make-you-chic-ui` の固定先は上げていない。

### Step 9 イメージ（版のタグ + 複数アーキテクチャの index のダイジェスト）
- `otel/opentelemetry-collector` 0.162.0 → 0.163.2 `sha256:63e5e8ec…`（amd64・arm64）: `compose.yaml`
- `axllent/mailpit` v1.31.5 → v1.31.6 `sha256:23fe39e8…`（386・amd64・arm64）: `compose.yaml`・`docker/perf/compose.yaml`・`README.md`
- `grafana/otel-lgtm` 0.36.0 → 0.37.0 `sha256:57b5ab1c…`（amd64・arm64）: `compose.yaml`・`perf/README.md`
- `mysql` 26.7.0 → 26.10.0 `sha256:f51c9c01…`（amd64・arm64/v8）: `compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages.java`（`MYSQL_VERSION`・`MYSQL_DIGEST`）
- 旧い版・ダイジェストを `grep` で探し、残りが無いことを確かめた。`.github/dependabot.yml` の固定先の一覧は実際と一致している（追記のコメントを足した）。`grafana/k6` は 2.3.0 が最新で変更なし。

### Step 10 Gradle・Actions
- `com.github.spotbugs` 6.5.12 → 6.5.13（`gradle/libs.versions.toml`）。
- `actions/setup-java` 6.0.1 → 6.1.0（SHA `b843ebf9…`、`.github/workflows/ci.yml`）。
- gradle-wrapper 9.8.1 → 9.8.2（#74。`./gradlew wrapper --gradle-version 9.8.2` で `gradle-wrapper.properties` の `distributionUrl`。`distributionSha256Sum` は元から無い。wrapper の jar に差は出なかった）。

### Step 11 npm
- `package.json` の下限を #59・#61・#62・#64・#67・#68・#69〜#73・#75 に合わせ、`npm install --ignore-scripts --package-lock-only` → `npm ci`（`frontend/.npmrc` の `ignore-scripts=true` のまま）。通った。
- lockfile は caret の範囲で最新に解決され、Dependabot の版より新しい: i18next 26.5.10（#73 は 26.5.5）、react-i18next 17.1.8（#71 は 17.1.2）。ほかは Dependabot の版どおり（oxlint 1.89.0、@typescript-eslint/parser 8.72.0 など）。
- lockfile の差: 追加 0、削除 1（`data-urls/node_modules/whatwg-url@16.0.1`）、`hasInstallScript` の付いた項目は更新前後とも `fsevents` の1件のみ（増えていない）。`npm audit --omit=dev` は 0 件。
- 見送った件は無い。

### Step 12・13 検査
- 依頼者の決定: B2 では `./gradlew e2eTest` を流さない（Build and Test と B3 の統合前に回す）。計画の Q2: A との差。Playwright は 1.64.1（`npx playwright --version`）。ブラウザの版の確認と `npx playwright install` は E2E を流す段に回す。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`: BUILD SUCCESSFUL、27分49秒（`caffeinate -dims`）。単体テスト 2589 件（失敗 0・SKIPPED 0）、結合テスト 1389 件（失敗 0・SKIPPED 9。他の DB 種別向けの条件付きで B1 と同数）。画面のテスト 226 ファイル・1975 件が通過。対象DB（mysql 26.10.0 を含む）の結合テストは実行された。
- `./gradlew osvScan --rerun`: 失敗の条件に当たるもの 0 件、警告 12 件。
- `pmset -g log` の最後の Sleep は 22:53 JST で、verify の開始より前。流している間の眠りは無い。
- 最初に旧い版（collector 0.163.1 など）で流し始めたが、連絡を受けて止め、更新後の状態で最初から流し直した。

## B3 前半（バックエンド）: Step 14〜28

作業ブランチ: `fix/261025-deps-perf-carryover-code`。コミットはしていない（変更はワークツリーに残す）。`./gradlew verify` 全体・E2E・カバレッジの実測（Step 32〜35）・画面（Step 29〜31）はやっていない。対象DB の結合テストは README のとおり `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して実行した（対象DB のテストは SKIPPED でなく実行された。SKIPPED は他の DB 向けの `assumeTrue` だけ）。

### Step 14〜15 設定の層（FR4.2・FR4.4）
- 読み取りのプールの既定を 5 → 10 にした（`TargetDbProperties.Pool`、`application.yaml`、`.env.example`、`README.md` の表、`perf/README.md` の借りる待ちの説明）。書き込みのプール（5）は変えていない。
- 計画の影響の範囲に無かった既存のテスト 2 件が既定の 5 に頼っていたため直した: `TargetDataSourceConfigTest`（`getMaximumPoolSize` の 5）・`AbstractTargetSchemaReaderIT.connectionIsReadOnly`（3種類の対象DB。最初の統合テストの流しで落ちて分かった）。
- `docker/monitoring` のダッシュボード・警報は上限をしきい値に使っていない（`hikaricp_connections_active/pending` の値だけ）。変更なし。
- 接続の数（FR4.4）: `BusinessDataConnectionUsageIT`・`NavigationConnectionUsageIT`・`GroupConnectionUsageIT`・`RoleConnectionUsageIT`・`TargetDbStartupIT` を流し、プール 10 のもとで 1 要求が使う接続の数は変わらず通った（上限 5 に頼る決定的な数え方は無かった）。

### Step 16〜17 C2: 時間切れの後に接続を捨てる（FR3.2）
- `TargetReadSession` に印（`AtomicBoolean`、4つ目の component。3引数の constructor も残した。`markBroken()`・`isBroken()`。`toString` は伏せたまま）。`RowSqlExecutor` の読み取り（`search`・`detail`）が、失敗の分類が `TIMEOUT` または `UNAVAILABLE`（断絶）のときに印を立てる。`JdbcTargetReadAccess.withRead` が返すとき、印があれば `evictConnection` で捨てる（例外の連なりで返す場合も）。ログは既存どおり例外のクラスの名前だけ。
- 直す前に失敗する再現: `BusinessDataTimeoutIT.nextRequestSucceedsAfterStallInsideCountQuery`（MySQL・MariaDB）。**直す前の確かめ**（`giveBack` に印を渡さない形に一時的に戻して実行）: MySQL は落ち（時間切れの次の1回が 503 `BUSINESS_TARGET_DB_UNAVAILABLE`、止めた位置 `chunk#2(87B)`）、MariaDB は直す前でも通った（MariaDB のドライバーは時間切れの接続をプールに戻しても次の要求に響かない）。直した後は両方通る。単体: `JdbcTargetReadAccessTest`（印のある接続は閉じる前に捨てる／印が無ければ捨てない／処理が例外でも捨てる）・`TargetReadSessionTest`・`RowSqlExecutorTest`（時間切れ・断絶は印が立ち、定義との食い違いは立たない）。直す前は `JdbcTargetReadAccessTest.brokenMarkEvicts` が落ちる形（印を見ないため）。

### Step 18〜20 C1: 符号なしの型の名前（FR3.1、計画の R-08 の案 A）
- `ColumnValueCodec`: 型の名前に `unsigned` を含む整数は下限 0・符号なしの上限（`tinyint` 255・`smallint` 65,535・`mediumint` 16,777,215・`int`/`integer` 4,294,967,295・`bigint` 2^64−1）。含まない名前は従来の「符号つきの下限と符号なしの上限の和」のまま。範囲の外は 400 `VALIDATION_FAILED`（`OUT_OF_RANGE`）。
- `RowDialect.baseName` の決定: `int(10) unsigned` のように括弧の後ろに `unsigned` がある手書きの表記は、**整数の型（tinyint/smallint/mediumint/int/integer/bigint）に限り** `int unsigned` として符号なしの印を残す（括弧の後ろのほかの語や整数でない型 `double(10,2) unsigned` は従来どおり落とす。`float unsigned` などの `equalsSupported` の判定を変えないため）。`timestamp(3) with time zone` は従来どおり `timestamp`（計画外の挙動変更をしない）。
- `DslTreeBuilder.dbType`: MySQL・MariaDB の `COLUMN_TYPE` に `unsigned` を含む整数型は名前を `int unsigned` のように書く（Q4: A）。`tinyint(1)`（真偽値）や整数でない型・PostgreSQL は変えない。`TypeCategoryMapping` は `TargetDbType` を見るため変わらない（既存のテストが符号なしの NUMBER を確かめている）。再生成の差の表では `dbType.name` の型の差として出る（`DslSummaryAndDiffTest` に足した）。
- `dsl-schema-v2.json` に `dbType` と `dbType.name` の `description` を足した（構造・版は変えない）。README の「DSL の書式」「既定の DSL の生成の決まり」に書いた。
- 直す前に失敗する再現（実際に直す前の状態で確かめた）: `ColumnValueCodecTest` の符号なしの境界（8件）・`RowDialectTest.baseName`・`DslTreeBuilderTest.unsignedIntegerNames` が落ちた。結合テスト `AbstractBusinessDataWriteIT.unsignedRanges`（新しい表 `w_sign`）は MySQL・MariaDB が `expected 400 but was 409` で落ち、PostgreSQL は直す前後とも通る（符号なしが無く変わらない）。符号を持たない既存の DSL は `valid-sample.yaml`・`dept_mst.yaml` の既存のテスト（`DslSchemaValidatorTest`・`DslTreeBuilderTest`）が変更なしで通る。符号つきの列（名前に unsigned なし）は従来どおり、範囲内の外の 2147483648 は 409 `BUSINESS_SAVE_REJECTED`、−2147483649 は 400（`unsignedRanges` で確かめた）。

### Step 21 C7 R-08: 真偽値の `tinyint` が 0 と 1 以外
- `RowDialect.readsBooleanAsInteger()`（MySQL 系 true／PostgreSQL false）を足し、`ColumnValueCodec.readCell` の BOOLEAN が MySQL 系では `getLong` で読んで 0→false・1→true・NULL→NULL・それ以外は `IllegalStateException`（値を入れない）→ `CellValue.Unreadable` → 既存の経路で `READ_CONVERSION`（409 `BUSINESS_TARGET_DB_MISMATCH`）。
- 直す前の確かめ: 結合テスト `AbstractBusinessDataReadIT.booleanTinyint`（新しい表 `flags`、`tinyint(1)` と `tinyint`＋switch、値 5）が MySQL・MariaDB とも `expected 409 but was 200`（true が返る）で落ち、直した後は通る。単体: `ColumnValueCodecTest.readBooleanAsInteger`・`RowSqlExecutorTest.booleanTinyintOtherThanZeroAndOne`。

### Step 22 C7 R-07: 要求の名前を fieldErrors に返さない
- `SearchRequestValidator`: 条件の列・並べ替えの列・主キーの名前が、このテーブルで読めるカラム（`TableAccessView.column`）に無いときは `field` を空の文字にする（`NOT_ALLOWED` / `KEY_MISMATCH`。NONE のカラムも DSL に無い名前と同じ空）。DSL にある名前（検索できない `pw`・重ねた `name` など）は従来どおり名前を返す。応答の形（`fieldErrors` の `field`・`reason`）は変えない。`validateKey` は更新・削除の鍵の確かめにも使われるため、書き込み側の鍵の誤りも同じになった（`WriteRequestValidatorTest` の1件の期待を直した）。
- 直す前に失敗する再現（実際に確かめた）: `SearchRequestValidatorTest`（3件）と `BusinessDataSecretLeakIT.unknownNamesLeaveNoTrace`（約 60,000 文字の名前・引用符を含む名前を TRACE を有効にして送り、応答とログに名前が無いこと。直す前は応答に長い名前がそのまま載って落ちた）。本文の上限 64 KB のため長い名前は1要求に1つ、鍵の名前は Jackson の名前の長さの上限（50,000 文字）に収まる 40,000 文字にした。`BusinessDataReadControllerTest` は、コントローラーが渡された空の `field` をそのまま返す（長い名前が応答に載らない）ことを確かめる。
- **書き込みの本文の `values` の名前**（`WriteRequestValidator` の `NOT_ALLOWED`）は、計画の範囲（検索の条件・並べ替え・主キー）の外のため変えていない。同じく要求の名前を `fieldErrors` に返す（`AbstractBusinessDataWriteIT.injection` がその名前の返りを確かめている）。依頼者に確かめたい点。

### Step 23〜24 C3: `GET /api/me/table-access` の削除（FR3.3）
- 削除: `NavigationController.tableAccess`（と、それだけが使った `SCHEMA`/`TABLE`/`ACCESS_DENIED_CODE`・`ProblemTypeRegistry` の引数。constructor は `NavigationService` だけになった）、`TableAccessResponse`、`TableAccessResult`、`NavigationService.tableAccess`、`NavigationBarrier.afterResolve`（`tableAccess` だけが使っていた待ち合わせの点。`NoOp` と、テストの `TestNavigationBarrier.Point.AFTER_RESOLVE` も外した）。アプリの中にこの道を呼ぶコードが無いことは grep で再確認した（`frontend/src` に無し。`frontend/e2e/support/frameApiRoute.ts` のコメントと `businessDataApiRoute.test.ts` の「この道は業務データの API でない」の1件だけが残る。画面の側は今回触っていない）。
- テスト: 削除した道に依存したテスト・ケースを外し（`NavigationApiIT` の置き場の3件、`NavigationUntrustedInputIT` の名前の23種の `tableAccess`、`NavigationDslSwapIT` の `AFTER_RESOLVE` の2件、`NavigationServiceTest`/`NavigationCallCountTest`/`NavNodeTest`/`NavigationRecordsToStringTest` の置き場の件）、`NavigationAuthorizationApiIT` はメニューの API だけの表（主体 5 つ）にした。**消えたことの確かめ**: `NavigationApiIT.tableAccessPathIsGone`（作業ロールを持つ利用者で 404、トークン無しで 401、名前が応答に出ない）。`NavigationQueryExposureIT` は削除した道に目印の名前を付けて送っても span・指標・ログに残らないことを確かめる形に直した。
- 計画外の1件: `ApiEndpointCounts.MIN_ENDPOINTS` を 73 → 72 に下げた（`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` が数える API の数が1本減ったため。ファイルの注記の「下げるのは口を外したときだけ」に当たる。検査を弱めたのではなく、数え方の下限を口の数に合わせた）。
- README: 業務のメニューの API の節から置き場の問い合わせの行・説明・受け入れた制約（`%` の並びの500 など、その道に限る話）を外し、削除したことを書いた。`perf/k6/scenarios.js`・`perf/README.md` の `tableAccess*` の場面は B4（Step 46）。

### Step 25 C7 R-02: DSL に無い名前の確かめ
- 新しい `BusinessDataUnknownNamesIT`（計画は `BusinessDataAuthorizationApiIT`・`AbstractBusinessDataReadIT` に足すとしていたが、3種類の対象DB で同じ表を回し、対象DB の接続を貸す部品を `CountingDataSourceConfig` で数えるため、新しいクラスにした。本番のコードは変えない）。DSL に無いスキーマ・テーブルの名前（引用符・`;`・`--`・バッククォート・改行・`../`・末尾の空白の10種）を3本の API に送って 404 `BUSINESS_TABLE_NOT_FOUND`、DSL に無い並べ替えの列・検索の列・主キーの列で 400 `VALIDATION_FAILED`、DSL が適用されていないアプリで 404 `BUSINESS_DSL_NOT_APPLIED`。**「文の数 0」は、対象DB の接続を貸す部品が借りられた回数 0（`CountingDataSource.borrows()`）で確かめた**（文を作る部品は接続を借りてからしか文を作れないため）。最後に正しい要求で借りた回数が 1 になること（数える部品が働いていること）も確かめた。6件通過。

### Step 26 C7 R-05: 止める段（`StallingRelay`）
- 暗号化された通信（MySQL・MariaDB の TLS）では応答の種類が中継から見えないため、中継は「大きなまとまりの何番目で止めるか」のままにして、**止めた後に一般ログ（`general_log` の表出力）で、止める前に対象DB に届いた文を読み、想定の段か確かめる**形を足した（新しい `StatementLog`・`StallStage`。ドライバーが時間切れの後に別の接続から流す `KILL QUERY` は段の手がかりでないため除く）。MySQL（Connector/J）の件数の問い合わせの前は `SET sql_select_limit=1` だけが届く、MariaDB は何も届かない（件数が最初の文）。`BusinessDataTimeoutIT` の既存の `stallAfterConnecting`（最初の文のまとまり＝何も届かない）と新しい `nextRequestSucceedsAfterStallInsideCountQuery`（MySQL は SET の次＝件数）で確かめる。
- 再現のテスト: `StallStageTest`（ドライバーの分け方が変わった疑似の届いた文で、届いた文の一覧・中継の診断・原因の見立てを持つ失敗になる）。実際の初回の流しで、`KILL QUERY` が届いた文に混ざって想定と違う失敗が出て、除く形に直した（原因の分かる失敗が本当に出ることの確認にもなった）。
- README の「対象DB の結合テストとコンテナの実行環境」に、ドライバーの版への依存と直し方を書いた。

### Step 27 C7 R-06: 境界テスト（緩めず厳しくした）
- `BusinessDataBoundaryArchitectureTest`: 文を作る口に `prepareCall` を、実行に `Statement.execute/executeQuery/executeUpdate/executeBatch/executeLargeUpdate/executeLargeBatch` を足した。既存の規則（businessdata の中で `RowSqlExecutor` だけ）を残したまま、新しい規則 `statementsOnlyInTheAllowedOwners`: アプリ全体（`businessdata` の外を含む）で、これらを呼んでよいクラスを名前の一覧に限る（`RowSqlExecutor`・`MysqlSchemaQueries`・`PostgresSchemaQueries`・`SchemaRows`・`TimeBoundedDbHealthIndicator`。実在も確かめる）。一覧は実際の呼び出し元を洗い出して決めた（最初は `SchemaRows` が漏れ、規則が実際に呼び出しを見つけることの確認になった）。
- 再現: `common/testsupport/StatementCallFixture`（確かめ用のクラス。`prepareCall` と `Statement.execute` を持つ）だけを読み込んで同じ規則を適用すると、`prepareCall` と `execute` を理由に失敗する（`ruleFindsCallsOutsideTheAllowedOwners`）。

### Step 28 API・境界・構造のテスト
- 実行して通った: `ArchitectureTest`・`ApiAccessRulesTest`・`ApiAccessArchitectureTest`・各機能の境界テスト・`ApiAccessConsistencyIT`・`RoleGrantsNoAdminAccessIT`（API の分類の網羅）、`businessdata.*`・`navigation.*`・`dsl.*`・`dslmanage.*`・`targetdb.*` の結合テストの全部（401 件、失敗 0、SKIPPED 10 は他の DB 向けの `assumeTrue`）。最初の流しで落ちた3件（`AbstractTargetSchemaReaderIT` の既定 5）は直して通した。
- 単体テストの全部（`:backend:cleanTest :backend:test`）: 2,611 件、失敗 0、SKIPPED 0（B2 の 2,589 件から +22）。これは `verify` ではなく、`verify`・E2E・カバレッジの実測は Step 32〜35 で行う。
- 新しい `src/main` の変更があるパッケージ（Step 34 の実測の対象）: `targetdb.config`・`targetdb.domain`・`targetdb.service`・`businessdata.domain`・`businessdata.repository`・`dslmanage.generate`・`navigation.web`・`navigation.service`・`navigation.domain`（削除のみ）。`packagesJudgedByTotal` の7パッケージ（`access.service`・`audit.repository`・`common.error.*`・`common.health`・`common.i18n.domain`・`common.web`）には手を入れていない。

### 依頼者に確かめたい点（B3 前半）
1. 書き込みの本文（`values`）の DSL に無い名前は、R-07 の範囲外として今も `fieldErrors` に返している（`AbstractBusinessDataWriteIT.injection` がその返りを確かめている）。同じように空の `field` にするか。
2. `ApiEndpointCounts.MIN_ENDPOINTS` を 73 → 72 に下げた（口を1本外したため）。
3. R-02 の「文の数 0」を、文の数ではなく対象DB の接続を借りた回数 0 で確かめた。
4. 「ドライバーの分け方が変わったときの失敗」を、中継の改造ではなく、止める前に届いた文の一般ログでの確かめにした（MySQL・MariaDB だけ。`general_log` を一時的に有効にする。PostgreSQL は合図の文字で止めるため対象外）。
5. `RowDialect.baseName` は整数の型に限って括弧の後ろの `unsigned` を残す。
6. README の対象DB の結合テストの節の MySQL・MariaDB の版（26.7・13.0）が、B2 の固定の更新（MySQL 26.10.0）と合っていない（本 Bolt の変更ではない。B2 の記録の直しの対象かもしれない）。

## B3 後半: Step 29〜35

### 依頼者の決定（B3 前半の確認事項1）: 書き込みの本文 `values` の DSL に無い名前
- 決定: 書き込みの本文 `values` に DSL に無い名前（読めないカラムを含む）があるとき、R-07 と同じく要求の名前をそのまま `fieldErrors` に返さず、空の `field` で `NOT_ALLOWED` にする（応答の形は変えない）。DSL にある名前（書けないカラム `ro` や主キー `id` の更新など）は従来どおり名前を返す。
- 実装: `WriteRequestValidator.validate` の `NOT_ALLOWED` の組み立てで、`view.column(name)` が空のとき `field` を空にする。
- 直す前に失敗する再現（直す前の状態で実際に流して確かめた）:
  - 単体 `WriteRequestValidatorTest.unknownNameIsNotEchoed`（約 60,000 文字の名前・引用符と改行を含む名前。作成と更新）と、既存の `notAllowed`（`unknown` の名前の期待を空に直した）が落ちた（`15 tests completed, 2 failed`）。
  - 結合 `BusinessDataWriteSecretLeakIT.unknownValueNamesLeaveNoTrace`（TRACE を有効にして、作成・更新の `values` に長い名前（40,000 文字）と引用符・改行を含む名前を送り、応答の本文とアプリのログに名前の目印が無いこと、応答が 4,000 文字未満であること）が落ちた。直す前は応答の `fieldErrors` に長い名前がそのまま載った。
  - 名前の長さは Jackson の名前の長さの上限（50,000 文字）に収まる 40,000 文字にした（依頼の約 60,000 文字は `values` の対応表の鍵には通らず、要求の段階で別の誤りになるため。読み取りの条件の列の名前は値なので 60,000 文字を使えた）。
- 直した後: 上の 2 つと、期待を空の `field` に直した `AbstractBusinessDataWriteIT.injection`（3種類の対象DB）・`BusinessDataWriteAuthorizationApiIT.idorAndMassAssignment`（`owner_id`）が通った。
- 計画の範囲外だった `WriteRequestValidator` の置き場（`businessdata.domain`）は R-07 と同じパッケージで、新しいパッケージの追加はない。

### Step 29〜31 C5: 部品が外れた印を `useLayoutEffect` に（FR3.5）
- 直す前に失敗するテスト（決定的、3回とも同じ失敗）: `PasswordChangePage.test.tsx` の「drops the answer that arrives the moment the next screen is placed in the DOM, before any effect cleanup」。`MutationObserver` で次の画面（`preferences-page`）の DOM が置かれた直後に保存の成功の答えを届ける。直す前は Toast（`パスワードを変更しました`）が出て `toast-container` が空でなくなり落ちた（3回）。
- `usePasswordChangeForm.ts`: `mounted` の印を `useLayoutEffect` に替えた。`usePreferencesForm.ts`: `mounted` の印を `useLayoutEffect` に、見せ方の取りやめ（`clearPreview`）は従来どおり `useEffect` の後片付けに分けた。
- `PreferencesPage.test.tsx` にも同じ順（DOM が置かれた直後）で答えを届けるテストを足した。ただし**直す前でも通る**（プリファレンスの Toast は状態の更新の後の効果で出すため、外れた部品への状態の更新は React が捨て、見える差が出ない）。このため PreferencesPage は「直す前に失敗する」確かめはできず、一貫性のための直しと、順の固定の確かめにとどまる（依頼者に確かめたい点）。
- 直した後: プリファレンスのフォルダの画面のテスト 9 ファイル・91 件が 3 回続けて通過（`waitFor` で待つ形は既存のまま、時間の上限は変えていない）。Prettier・tsc・oxlint・ESLint は通過。
- `frontend/e2e/support/frameApiRoute.ts` のコメントを、消えた道を指す書き方から「業務データの API は `businessDataApiRoute.ts` が受ける（置き場の問い合わせの道はバックエンドから消えた）」に直した。`businessDataApiRoute.test.ts` の `isBusinessDataApiPath('/api/me/table-access')` は、消えた道の名前をやめて別の業務データでない道 `/api/me/navigation` にした（期待は偽のまま）。テストのどれも table-access を前提にしていなかった。動作の変更なし。

### 決定の影響で直した既存のテスト
- `BusinessDataWriteServiceTest.validationFailed`（`values` の DSL に無い名前 `nothing` の期待を空の `field` に）。1回目の `verify` がこの1件で落ちて分かった（`2612 tests completed, 1 failed`、約 2分11秒で停止）。ほかに `WriteRequestValidatorTest.notAllowed`・`AbstractBusinessDataWriteIT.injection`・`BusinessDataWriteAuthorizationApiIT.idorAndMassAssignment` の期待を同じ形に直した。

### Step 32 `verify`
- `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`PATH` に `$HOME/.volta/bin`）。2回目: BUILD SUCCESSFUL、**22分27秒**（CI の上限 `timeout-minutes: 60` の 7 割 = 42 分の **約 53%**）。
- 単体テスト 2,612 件（失敗 0・SKIPPED 0）、結合テスト 1,375 件（失敗 0・SKIPPED 10。他の DB 種別向けの `assumeTrue` だけで、対象DB のテストは実行された）、画面のテスト 226 ファイル・1,977 件が通過。B2 の結合テスト 1,389 件から減ったのは、消した道 `GET /api/me/table-access` のテストを外したため。
- Gitleaks: 漏れ 0（501 コミット）。SpotBugs の関門・`verifyCoverage` 通過。眠り: 流している間の `Entering Sleep`・`DarkWake` は無し（最後の `Sleep` は 00:06 JST で開始前）。

### `osvScan --rerun`
- BUILD SUCCESSFUL（失敗の条件に当たるもの 0 件、警告は開発用の npm の依存の警告だけ）。`verify` の中の `osvScan` は UP-TO-DATE だったため、`--rerun` で流し直した。

### Step 33 E2E（`./gradlew e2eTest`）
- Playwright 1.64.1。必要なブラウザは chromium-1248 で手元に無く、`npx playwright install chromium`（README のとおり）で入れた（Chrome Headless Shell 156.0.8078.4、v1248）。
- 見本の PostgreSQL は README のとおり上乗せ（`docker/e2e/compose.targetdb-port.yaml`）で 127.0.0.1:15432 に開けて作り直し、流し終えた後に上乗せ無しで作り直してポートを閉じた（配備したアプリは healthy のまま）。Mailpit は起動済みだった。
- 結果: **256 件 passed**（失敗 0・flaky 0・skipped 0）、13.0 分（BUILD SUCCESSFUL 13分7秒）。眠りは無し。
- 報告の確かめ: 報告の部品の出力「値の種類 13・json の報告 1・添付 305・見つかった件数 0」。加えて、`.env` の署名鍵・初期管理者のパスワード・対象DB の読み取り／書き込みのユーザー名とパスワードの値を json の報告から文字列で探し、すべて 0 件（値は表示していない）。`jdbc:` を探すと1件当たるが、アプリの起動の引数の一時の内部DB のファイルの道（`jdbc:h2:file:/var/folders/…`、対象DB の接続先ではない）だけ。`15432`・一時のロール名の接頭辞は 0 件。`frontend/playwright-report/` は無い。
- 画面の時間の測り（FR4.7）は Build and Test の持ち主のため、この段では記録しない。

### Step 34 カバレッジの実測
- バックエンド全体（`jacocoTestReport.xml`、`verify` の 2 回目）: 行 97.7%・分岐 91.4%。
- `src/main` に手が入ったパッケージ（`git diff --name-only develop -- backend/src/main` から。行／分岐）: `targetdb.config` 100.0／99.1、`targetdb.domain` 100.0／97.2、`targetdb.service` 89.1／75.4、`dslmanage.generate` 99.4／100.0、`businessdata.repository` 96.4／89.0、`businessdata.domain` 97.7／90.2、`businessdata.web`（`src/main` の変更なし）97.6／77.9、`navigation.web` 92.0／100.0、`navigation.service` 100.0／100.0、`navigation.domain` 100.0／92.6。すべてパッケージごとの下限（行 80%・分岐 70%）以上。
- `packagesJudgedByTotal`（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）との重なりは**無し**。一覧は変えていない。除外は増やしていない。
- フロントエンド全体（`coverage-summary.json`）: 行 95.06%・分岐 89.79%。今回触ったフック `usePasswordChangeForm.ts` 行 92.85%・分岐 83.33%、`usePreferencesForm.ts` 行 94.49%・分岐 87.67%。

### Step 35 ライセンスヘッダー
- `verify` の中の `spotlessJavaCheck`・`spotlessCheck` と、画面側の検査が通った。新しいファイル（`StallStage.java`・`StallStageTest.java`・`StatementLog.java`・`BusinessDataUnknownNamesIT.java`・`StatementCallFixture.java`）は `/* … */` の 2026・agwlvssainokuni のヘッダー付き。

### Step 36 コミットの区切りの案（積んでいない。承認を得てから）
各区切りでコンパイルが通る順（依存の向き: 設定 → `targetdb` → `businessdata`・`dslmanage` → `navigation` → 画面）。
1. 読み取りのプールの既定 10（`TargetDbProperties`・`application.yaml`・`.env.example`・`README.md`・`perf/README.md` の説明、`TargetDbPropertiesTest`・`TargetDataSourceConfigTest`・`AbstractTargetSchemaReaderIT`）
2. C2: 時間切れの後に接続を捨てる（`targetdb` の `TargetReadSession`・`JdbcTargetReadAccess`、`businessdata.repository.RowSqlExecutor` の印、各テストと `BusinessDataTimeoutIT`・`StallStage`・`StatementLog`・`StallStageTest`）。R-05 の止める段の確かめと README の注意も同じ区切り
3. C1: 符号なしの型の名前（`ColumnValueCodec`・`RowDialect` と2つの方言、`DslTreeBuilder`、`dsl-schema-v2.json`、README、各テスト）。R-08 の真偽値の読み（`readCell`）は `ColumnValueCodec` を同じく触るためここに入れる
4. C7: R-07（`SearchRequestValidator`・`WriteRequestValidator` と各テスト・今回の決定）、R-02（`BusinessDataUnknownNamesIT`）、R-06（`BusinessDataBoundaryArchitectureTest`・`StatementCallFixture`）
5. C3: `GET /api/me/table-access` の削除（`navigation` の本体とテスト、`ApiEndpointCounts`、README）
6. C5: 画面（`usePasswordChangeForm.ts`・`usePreferencesForm.ts` と2つのテスト、`frameApiRoute.ts` のコメント、`businessDataApiRoute.test.ts`）
- `aidlc/` の記録（この `generation-notes.md`・計画のチェック・監査ログ）は、上のコードのコミットの後に記録だけのコミットとして別に積む（`project.md` の Change Control）。統合は squash で `develop` へ。

### 依頼者に確かめたい点（B3 後半）
1. `WriteRequestValidator` の決定の範囲: DSL に無い名前だけを空の `field` にし、DSL にある書けないカラム（`ro`・主キー `id` の更新）は名前を返す形にした（R-07 と同じ）。読めないカラム（主権限 NONE）は DSL に無い名前と同じ扱いで空になる。
2. 再現の名前の長さは、`values` の鍵が Jackson の名前の上限（50,000 文字）に収まる 40,000 文字になった（依頼は約 60,000 文字）。条件の列などの値は 60,000 文字で確かめ済み。
3. PreferencesPage は直す前に失敗するテストを作れなかった（見える差が出ないため）。PasswordChangePage だけが決定的に再現した。
4. `playwright install chromium`（v1248）でブラウザを入れた。
5. E2E の json に `jdbc:h2:file:/var/folders/…`（一時の内部DB の道）が起動の引数として残る（前からの形で、報告の部品の対象外）。対象DB の接続先ではない。

## B4: 運用の道具・台本・手順書（Step 37〜50）

作業ブランチ: `docs/261025-deps-perf-carryover-perf`（`develop` の B3 統合後から）。コミットはしていない（変更はワークツリーに残す）。使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト名 `mastersmith-perf`、イメージは配備済みの `mastersmith:local` をそのまま使い、作り直していない）でだけ流し、配備したアプリ・配備した見本の対象DB・Mailpit・`.env` は止めず、変えず、開かなかった（流している間も `mastersmith-app-1` は healthy のまま）。一時の環境ファイルはホームの下（権限 700）に作り、終わりに環境（ボリュームを含む）と一時のディレクトリを消した。

### Step 38 FR4.3 部分一致は索引が効かない
- `README.md` の「業務データの読み取りの API」に、運用の注意（`LIKE '%…%'` は索引を使えず、1回の検索で件数とページの2つの問い合わせにより表の全部を2回読む。DSL では行が多い表の文字のカラムの `search.enabled` を `false` にするか `search.operator` を `EQUALS` にする）を足した。`perf/README.md` の「借りる待ち」の下にも原因として足した。
- **計画との差**: 計画（Step 38）は「JSON Schema の `description` は Step 19 で足してある」としたが、Step 19 で足したのは `dbType`・`dbType.name` の説明だけで、検索の設定の説明は無かった。そのため `backend/src/main/resources/dsl/dsl-schema-v2.json` の `searchOperator` と `search` に `description` を足した（構造・版は変えない。JSON として読めることを確かめた。公開の複写の一致は `DslSchemaPublicationIT` が `verify` で確かめる）。B4 は文書と台本が中心だが、`src/main/resources` の1ファイルに触れている。

### Step 39 FR3.4（C4）`compact` が補充の途中を避ける
- `docker/jmx/HikariPoolControl.java`: `compact` の始め（一時停止の前）に `awaitFillSettled` を足した。プールの設定の MBean（`com.zaxxer.hikari:type=PoolConfig (<プール名>)`、`HikariConfigMXBean`）から `MaximumPoolSize` と `MinimumIdle` を読み、全体の接続の数が上限に届くか、待機の接続の数が最小の空きの数（上限を超えない）に届くまで待つ（前の Intent の結合テスト `H2CompactionByPoolSuspensionIT.awaitFillSettled` と同じ条件）。待ちの上限は新しい `--fill-wait`（既定 10 秒、1〜600）で、超えたら**一時停止せず**、詰め直さずに終わりの値 1（まだ止めていないため再開は要らない）。
- `docker/hikari-pool.sh`: 使い方と `--fill-wait` の受け取りを足した。`README.md` の「内部DBのファイルの詰め直し」に「補充の途中を避ける」を足した。
- `javac` で `HikariPoolControl.java` がコンパイルできることを確かめた。

### Step 40 FR3.4 の確かめ（使い捨ての環境）
- 試みの時間の上限は先に 30 分と決めた（実際は約 10 分で終えた）。使い捨てのアプリは `mastersmith:local`（上限 2g）、プールは既定の上限 30・最小の空きは未指定（= 30）。
- 新しい道具: `status` は「使用中 0・待機 30・合計 30」。`compact` は「補充が済んでいます」のあと、接続の破棄から 7 ms で 0 本、約 3.2 秒でファイルが落ち着き、終わりの値 0（始めに要求を1つ送って補充を起こした直後に流した3回と、続けた1回の計4回とも同じ）。
- **取りこぼしの再現は、できなかった**。前の道具の動き（補充の途中で一時停止 → 破棄 → 0 本を待つ）を、同じ JMX の呼び出しで、使い捨ての環境のアプリに直接かける確かめ用の小さな Java（リポジトリの外、`Race.java`）を作り、「接続を全部捨てる → 要求を1つ送って補充を起こす → 合計が閾値（2・3・10・20・28 本）に届いた直後に、一時停止 → 破棄 → 3 秒まで 0 本を待つ」を、前の手順で（最初の版 12 回 + 12 回 + 40 回 + 閾値 10・20・28 本の各 25 回）計 139 回繰り返した。**139 回とも 0 本になった（取りこぼし 0 回）**。新しい手順（補充が済むのを待ってから）も 25 回、取りこぼし 0 回。
- 見立て（未検証）: 使い捨ての環境では接続の作成（組み込みの H2）が1本あたり数マイクロ秒〜ミリ秒と速く、「破棄の後に補充の途中の接続が1本だけ足される」窓がほとんど開かない。前の Intent のテストで起きた取りこぼしは、PC の負荷が高く接続の作成が遅いときの窓と見られる。試みの範囲: 補充を起こす方法は「接続を全部捨てて要求を送る」の1つだけで、PC に負荷をかけた状態は作っていない（上限 30 分の内で、負荷をかけた再現は行わなかった）。
- 新しい道具の `--fill-wait` を超える経路（補充が済まない）は、実際には起こせず、確かめていない（`--fill-wait 0` は範囲外の値として使い方の誤り（終わりの値 2）になることだけ確かめた）。

### Step 41 FR3.6（C6）固定の値を1か所に
- 新しい `perf/versions.env`（`K6_IMAGE`・`PERF_JRE_IMAGE`。ヘッダー付き。`source` で読む）と `perf/h2-shell.sh`（H2 の jar の探し方と Shell の起動。`--read-only` で `:ro` と `ACCESS_MODE_DATA=r`。`H2_URL` で接続先を替えられる）を作った。`perf/README.md` の 14 か所の k6 のイメージは `"$K6_IMAGE"` に、7 か所の H2 の jar の取り方と Shell の起動は `./perf/h2-shell.sh …` に直した（手順 1 に `source perf/versions.env` を足した）。`perf/dsl-mixed-memory.sh` は `versions.env` を読み、設定の値の読み取りは `h2-shell.sh --read-only` に替えた（`bash -n` で構文を確かめた）。
- `grep -rn 'grafana/k6:2.3.0@sha256' .`（`vendor`・`node_modules`・`build`・`aidlc` を除く）は `perf/versions.env` の1か所だけ。`h2-2.4.240.jar` の取り方は `perf/h2-shell.sh` の1か所だけ。`.github/dependabot.yml` のコメント（Dependabot が見ない固定先の一覧）を更新した。
- `h2-shell.sh --read-only` を使い捨ての環境の内部DB で実際に動かした（`SELECT COUNT(*) FROM users` が出た）。JRE は Java のソースを直接実行できない（`jdk.compiler` が無い）ため、Step 43 の数える道具は JDK のイメージ（`docker/hikari-pool.sh` と同じ固定先）で動かす。
- **計画との差**: 計画は JDK のイメージも `versions.env` に集めるとしたが、`perf/h2-shell.sh` が使うのは JRE のイメージで、JDK のイメージは `docker/hikari-pool.sh`・`perf/README.md` の NMT の手順・README にあり、perf の手順の外（hikari-pool.sh は独立した運用の道具）のため、`versions.env` には `K6_IMAGE` と `PERF_JRE_IMAGE` だけを置いた。JDK のイメージの固定先は変えていない。

### Step 42 FR3.8（G2）監視の確かめ用の台本 `monitorLight`
- `perf/k6/scenarios.js` に `monitorLight` を足した: 1 VU・繰り返しごとに `sleep(1 + Math.random())`（1〜2 秒）、`GET /actuator/health`・`GET /api/appearance`・`GET /api/admin/check`（setup で管理者のトークンを取る）を順に1つずつ。`MONITOR_BIZ=1` のときは業務データの定義と一覧（試験用の利用者 `perf-biz-01`、先に `bizPrepare`）も回す。長さは `MONITOR_DURATION`（既定 5m）。閾値は `checks` の率 1 だけ。
- `k6 inspect --include-system-env-vars` で場面（`monitorLight`、1 VU・5m）と閾値が読めた。使い捨ての環境で 20 秒流し、最初は `adminCheck` が 204 でなく 200 を期待していて 5 件落ちた（台本の誤り）。204 に直して流し直し、**checks 13 件・失敗 0**、要求は毎秒 約 0.65 件。
- `README.md` の「手元の監視（Grafana）」と `perf/README.md`（新しい節「手元の監視の式の値を確かめる場面」）に流し方を書いた。lgtm が止まらないことと式の値の確かめは、Build and Test の持ち主（この段では lgtm を起動していない。VM のメモリの余裕のため）。

### Step 43 FR3.9（G4）複写を数える道具
- `README.md` の「内部DBのバックアップと戻し方」の後に「内部DBの複写を数える（読み取りだけ・個人に関する値を出さない）」を足し、リポジトリの外に置く小さな Java（`CountRows.java`）を載せた。表と列の名前は `INFORMATION_SCHEMA.COLUMNS` で先に確かめ、無ければ `列なし`・`表なし` と書くだけ（誤りの文は出さない）。開けない・読めないときは例外のクラスの名前と SQLState だけ。`group` は `event_type`・`result`・`failure_reason` に限る。値はバインド変数、名前は INFORMATION_SCHEMA で見つけた名前を引用して使う。
- README に載せた文面そのものをファイルに取り出して、使い捨ての環境の内部DB の複写（`tar` で取った複写をホームの下の新しいディレクトリへ）で確かめた: `count users` → `users 1`、`group audit_events event_type` → `INITIAL_ADMIN_CREATED 1`、`count audit_events event_type LOGIN_SUCCESS` → `audit_events.event_type 0`、**存在しない列 `count users no_such_column x` → `users.no_such_column 列なし`**（誤りの文なし）、存在しない表 → `no_table 表なし`、`group users email` → `users.email 表示しない列`、`users;DROP` → `表なし`、複写が空のディレクトリ → `開けない・読めない（JdbcSQLNonTransientException 90028）`。**個人に関する値は表示していない**（複写の中身は件数だけ）。
- 確かめの途中で2点を README に反映した: (1) JRE のイメージでは Java のソースを直接実行できない → JDK のイメージを使う。(2) 複写の展開で `chown` が macOS のホームの下の bind マウントで失敗する → `chown` を外し、複写を `:ro` で付けて root のまま読む。複写・道具・一時のディレクトリは確かめの後に消した。

### Step 44 FR3.10（G9）リリースの前の確かめ（実測）
- `README.md` に「リリースの前の確かめ」の節（`osvScan --rerun` と `dist`・WAR に axe-core が入らない確かめのコマンドと期待の出力）を足した。
- 実際に流した結果（2026-10-26 05:52 JST、このブランチの作業の途中の状態）: `./gradlew osvScan --rerun` は **失敗の条件に当たるもの 0 件、警告 12 件**、BUILD SUCCESSFUL（6 秒）。`grep -rIl 'axe-core\|axe\.min' frontend/dist` は**何も出さない**（終わりの値 1）。`unzip -l backend/build/libs/mastersmith.war | grep -i axe` も何も出さない。`frontend/dist` と WAR は B3 の `verify`（01:29・01:54）のもの。取り込みの前の実測は Build and Test でもう一度行う。
- 警告 12 件は開発用の npm の依存関係（`fast-uri`・`@vitest/mocker`・`zod` ほか）の公表。**`zod@4.6.5`（frontend）と `zod@4.4.3`（vendor/make-you-chic-ui）に CVSS 8.2 の公表（GHSA-mrhx-c7xc-m3w5）があるが、開発用の依存関係で、成果物を作る道具の一覧（`config/npm-build-tools.txt`）にも無いため、決まりどおり警告のまま**（実行時の依存関係なら止まる）。

### Step 45 FR3.11 `checks` の `grep`
- 使い捨ての環境で k6 の `health` を 10 秒流し、`--summary-export` の報告（145 行の複数行の JSON）に対して、直す前の `grep -h -o '"checks": {[^}]*}'` が**何も出さない**（終わりの値 1）ことを先に確かめた。k6 2.3.0 の報告の構造は `.metrics.checks` が `{fails, passes, value}`、`.root_group.checks` が check の名前ごとの `{passes, fails}`。
- `perf/README.md`（ロールの管理の節の `role-three.sh` の後）を `jq -c '.metrics.checks // .root_group.checks'` に直し、同じ報告で `{"fails":43189,"passes":54501,"value":0.5578…}` が出ることを確かめた。
- **気づき（計画の範囲外。直していない）**: この試し走りの `health` は 10 VU・待ち無しで毎秒 約 9,800 件になり、約 44% が非 2xx だった（`http_req_failed`）。直前に `compact` を流した後で、プールが 0 本からの補充の最中だったこと、上限なしの負荷で内部DB の接続待ちが出たことが見立てだが、確かめていない。FR3.11 の確かめ（報告から `checks` の値を読めること）には関係しない。
- **気づき（計画の範囲外。直していない）**: `perf/README.md` の `grep -h -o '"groupPoolLimit_[0-9a-z]*": {[^}]*}'`（619・686・715・826 行ほか）も、同じ複数行の報告に対しては何も出さない見込み（`--summary-export` の同じ形）。FR3.11 は `checks` の1行だけが対象のため、ここは直していない。前の Intent で出ていた値は、別の形（単一行）の報告だった可能性がある。確かめていない。

### Step 46 FR3.3 の台本側
- `perf/k6/scenarios.js` から `tableAccessVisible`・`tableAccessDenied`・`tableAccess`・`navTable`・`NAV_TABLE_HALF`・403 を期待の状態コードとする宣言・`NAV_SCENARIOS` の該当・export の該当を消した。`perf/README.md` の業務のメニューの場面の表・流す順・`k6r` の繰り返しから外し、外した旨を書いた。`k6 inspect` で `navMenu`・`monitorLight`・`dslRoleOverlap` が読め、`tableAccessVisible` は場面を持たない名前になった（実行すれば「知らない SCENARIO です」）。前の Intent の試し走りの結果の文（B7 の記録）は日付つきの過去の記録として残した。

### Step 47 FR4.6（B5・G1）
- `perf/README.md` の 2B: `mysql`・`mariadb` の道具に `--default-character-set=utf8mb4` を足した。種を入れた後の確かめ 2B'（目印の語の当たり・最長の文字数・行の数を、行の値を出さずに数えてから表の大きさを測る）を足した（PostgreSQL の例の SQL。**実際には流していない**。種の投入に数分かかるため）。
- 試験の値の表を、前の Intent の Performance Validation の記録（`load-test-plan.md`）の実際の値に直した: MySQL プール **1280M**・上限 **2688m**（表 1,017,806,848 B、75.8%）、MariaDB プール **896M**・上限 **1408m**（747,274,240 B、79.5%）、PostgreSQL 256MB・768m（212,566,016 B、79.2%）。古い値（MySQL 768M／2304m、MariaDB 512M／1g）は使わないと書いた。
- **計画との差**: 計画は「MariaDB・PostgreSQL は `docker/perf/compose.yaml` の既定」と書いたが、compose の既定（MariaDB 512m・PostgreSQL 512m）は表の大きさに足りず、前の Intent が実際に使った値は MariaDB 896M／1408m・PostgreSQL 256MB／768m だった。実際に使った値を採った。
- 流す順の見直しの案（読み取りを先に、書き込みはその後、書き込みの後に読み取りが要るときは対象DB を作り直す）を、書き込みの場面の「流す順」の下に書いた。

### Step 48 FR4.8 P5 `dslRoleOverlap`
- `perf/k6/scenarios.js` に足した。2 つの VU を並べて `DURATION` 流す: `dslHeavy`（既存の `dslMixed` の重い側。10MB の DSL の投入とプレビューの表示）と `roleChecks`（新しい `roleCheckLoop`。上限ちょうどの権限の YAML（`ROLE_YAML_FILE`）の確かめ。適用はしない。トークンは VU の中で 4 分ごとに取り直す）。判定は `checks` の率 1 と `iteration_duration{scenario:roleChecks}` の最大 15 秒未満（`roleTransferLargeCheck` と同じ線）。k6 の setup は前の場面の状態に頼らない。
- `k6 inspect --include-system-env-vars` で、場面（`dslHeavy`・`roleChecks`）と閾値が読めた（`DSL_FILE`・`ROLE_YAML_FILE` には確かめ用の仮のファイルを渡した）。**実際には流していない**（準備に 10MB の DSL と上限ちょうどの YAML が要り、流すのは Build and Test）。`perf/README.md` に新しい節を足した。

### Step 49 Build and Test の手順
- `unit-test-instructions.md` に「7.1 Build and Test の手順」を足した（FR4.9・FR4.2・FR4.5・FR4.7・P5〜P7・FR3.8・FR3.10・時間の計測）。**P6 は計画と食い違うため「要確認」と書いた**（下の「依頼者に確かめたい点」）。

### 補足の直し（計画の範囲外）
- `README.md` の対象DB の結合テストの節の版（MySQL 26.7・MariaDB 13.0・PostgreSQL 18）を、いまの固定（MySQL 26.10.0・MariaDB 13.0.2・PostgreSQL 18.6。正は `TargetDbImages.java`・`compose.yaml`）に直した。ほかに古い版の記述が README・`perf/README.md`・`.env.example`・`compose.yaml`・`docker/` に無いかを `grep`（`26.7`・`v1.31.5`・`0.162.0`・`0.36.0`）で確かめ、残りは無かった。`TargetDbImages.java` の Javadoc「26.7 の系列」だけが古かったため「26.10 の系列」に直した（コメントだけ）。

### Step 50 `verify`
- `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を README のとおり渡し、`PATH` に `$HOME/.volta/bin`）。開始 06:01:25 JST、**BUILD SUCCESSFUL、22分38秒**（CI の上限 `timeout-minutes: 60` の 7 割 = 42 分の約 54%）。
- 単体テスト 2,612 件（失敗 0・SKIPPED 0）、結合テスト 1,375 件（失敗 0・SKIPPED 10。他の DB 種別向けの `assumeTrue` だけで、対象DB のテストは実行された。B3 と同数）、画面のテスト 226 ファイル・1,977 件が通過。B3 と件数が同じ（このブランチは `src/main` のコードを変えていない）。`dsl-schema-v2.json` の説明の追加は `DslSchemaPublicationIT`・`DslSchemaValidatorTest` を含めて通過。フロントエンドのカバレッジは行 95.06%・分岐 89.77%（B3 と同じ）。ライセンスヘッダー・SpotBugs・Gitleaks・`osvScan`・`verifyCoverage` を含めて通過。
- 眠り: 流している間（06:01〜06:24）の `Sleep`・`DarkWake` は無し（最後の `DarkWake` は 05:52 JST で開始より前）。
- `HikariPoolControl.java` の変更は `src/main`・`src/test` の外（`docker/jmx/`）で、`verify` のどのテストも読まない。既存のテストへの影響は無い（`javac` でのコンパイルと、使い捨ての環境での動作の確かめが確認のすべて）。

### コミットの区切りの案（積んでいない。承認を得てから）
いずれの区切りでも、アプリのコンパイルに影響しない（文書・台本・運用の道具・JSON Schema の説明・Javadoc だけ）。`README.md`・`perf/README.md` は複数の区切りにまたがるため、`git add -p` で分ける。
1. `docker/hikari-pool.sh`・`docker/jmx/HikariPoolControl.java`・`README.md` の「内部DBのファイルの詰め直し」（FR3.4。補充の途中を避ける）
2. `perf/versions.env`・`perf/h2-shell.sh`・`perf/dsl-mixed-memory.sh`・`perf/README.md` の k6 のイメージと H2 の Shell の呼び出し・`.github/dependabot.yml` のコメント（FR3.6）
3. `perf/k6/scenarios.js`（`monitorLight`・`dslRoleOverlap`・`tableAccess*` の削除）・`perf/README.md` の該当の節と流す順・2B の直し（FR3.8・FR4.8 P5・FR3.3 の台本側・FR4.6・FR3.11）
4. `README.md` の「内部DBの複写を数える」「リリースの前の確かめ」「手元の監視」の注意・対象DB の版の記述（FR3.9・FR3.10・FR3.8）、`README.md` と `dsl-schema-v2.json` の部分一致の注意（FR4.3）、`TargetDbImages.java` の Javadoc
5. `aidlc/` の記録（この `generation-notes.md`・`code-generation-plan.md` のチェック・`unit-test-instructions.md`・監査ログ）— 記録だけのコミットとして別に積む
- 統合は squash で `develop` へ（B4 はサブモジュールの固定先の更新を含まない）。
