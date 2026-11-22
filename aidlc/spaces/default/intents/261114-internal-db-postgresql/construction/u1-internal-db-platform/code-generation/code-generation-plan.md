# コード生成の計画 — u1-internal-db-platform

## Summary

- Builds: 内部DB の種類の判断・PostgreSQL 用の移行 14 本・接続ごとの `lock_timeout`・起動時の失敗の記録・PostgreSQL の結合テストの土台（専用の Gradle タスクと CI の手動の入力）
- Touches: `backend/src/main/java/cherry/mastersmith/internaldb/platform/`（新しい機能）、`backend/src/main/resources/db/migration-postgresql/`、`backend/build.gradle.kts`・`build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`、`.github/workflows/ci.yml`、`backend/src/test/java/cherry/mastersmith/internaldb/`
- Tests: 単体テスト約 45 件（パラメーターを使う表を含む。コンテナ不要）、H2 の結合テスト 3 クラス、PostgreSQL の結合テスト 5 クラス（約 14 件。明示して流したときだけ）
- Touches（追加）: `DslPreviewRecord`・`DslAppliedRevisionRecord` の 2 列の写し方（Q1: A。U2 の先取り）

## 計画の前提（設計の段の決定と、持ち越した審査の指摘の受け方）

この計画は、機能設計・NFR 要件・NFR 設計・インフラ設計の文書（承認の場の前の初稿。承認の場の決定はまだ無い）と、各段の審査（READY）の指摘を、設計の文書を書き換えずに次のとおり受ける。承認の場で設計の文書を直すときは、段のやり直しを先に行い、この計画との差を確かめ直す。

### この計画が決める 2 点（NFR 設計の審査の Major。依頼者の承認で確定する）

| 指摘 | この計画の決め | 反映先の手順 |
|---|---|---|
| NFR 設計 R-01: 外の接続が `flyway_schema_history` を握ると、移行の文の失敗ではなく履歴の表の `lock_timeout`（SQLState `55P03`）になり、`UNKNOWN`・版の番号なしになる（DD-5 の約束と合わない） | 握られた 2 つの場合を分ける。**対象の表を握られた**: 移行の文の失敗として `MIGRATION_FAILED`・版の番号つき。**履歴の表を握られた**: SQLState `55P03` を連なりに持ち、移行の文の失敗が無いものを `MIGRATION_FAILED`・版の番号なし、とする（`StartupFailureReporter` の分類に `55P03` の判定を足す。接続の失敗（08・28・3D）の判定の後、移行の文の失敗の判定の前） | Step 4・5、Step 14（PostgreSQL の結合テストで 2 つの場合を各 1 本） |
| NFR 設計 R-02: 条件の 1 つが外れると「何もしない」で、`lock_timeout` が入らないまま黙って起動できる | 当てる対象は Bean の名前 `dataSource` だけで決める。**種類が PostgreSQL で、`dataSource` が `HikariDataSource` でなければ、`InternalDbStartupException`（`UNKNOWN`）で起動を止める**。プール名 `mastersmith-db` は対象の絞り込みに使わない（運用者が変えられる設定で、`dataSource` の名前だけで内部DB のプールは一意に決まる）。種類が H2 のとき、または `dataSource` 以外の Bean（`targetDataSource`・`targetWriteDataSource`）には何もしない。プールが既に始まっていたときも `UNKNOWN` で止める（DD-1 のとおり） | Step 4・5 |

この 2 点は NFR 設計の DD-1・DD-5・security-design の failureKind の決め方と違う（上流との差）。違いは `code-summary.md` の「上流との差」に書く。設計の文書は承認の場まで書き換えない。

### 持ち越した指摘の受け方（一覧）

| 指摘 | 受け方（この計画の手順） |
|---|---|
| 機能設計 R-01（BPP の対象） | DD-1 とこの計画の上の決めで受ける。対象DB のプールに何も入らないことを Step 5 の単体テストで確かめる |
| 機能設計 R-02（Hikari のログ） | 実測済み（出ない）。Step 5 の起動の失敗のテストと Step 14 のパスワード違いのテストが、全ロガーの出力に URL・資格情報が無いことを確かめる。Hikari のロガーを絞る設定は足さない |
| 機能設計 R-03（判断は 1 回・1 か所） | `InternalDbUrlResolver`（純粋関数）を、BPP と `InternalDbSettings` が同じ値で呼ぶ（DD-2） |
| 機能設計 R-04（誰が報告するか） | EMPTY・UNSUPPORTED は BPP が報告して投げ、Spring が連ねる例外の文にも接続先が入らないことを Step 5 の T2 で確かめる（DD-3） |
| 機能設計 R-05（コメントを除いてから探す） | Step 3 の BR2.8 (e) の単体テストは、SQL のコメント（`--` 行）を取り除いてから `ON DELETE`・`BINARY(`・`BINARY LARGE OBJECT` を探す。移行の先頭の注に理由を書いてよい |
| 機能設計 R-06（BR5.2 と BR5.3 の読み方） | 例外の文を読むのは、版の番号の数字を取り出す 1 か所（`StartupFailureReporter` の版の取り出し）だけ。出力には数字以外の文を入れない。単体テストで確かめる（Step 5） |
| 機能設計 R-07（WAR の中の PostgreSQL 用の移行） | Step 10 で、WAR の中に `WEB-INF/classes/db/migration-postgresql/` の 14 本が H2 用と同じ名前で入っていることを確かめる検査（`verifyPostgresMigrationsInWar`）を足す |
| NFR 設計 R-03（NFR4.11・4.12 が traceability に無い） | 設計側の文書の直し。この計画は NFR4.11・4.12 を Step 5・14 の確かめに入れ、Step 15 の traceability.json に載せる |
| NFR 設計 R-04（上流との差の節が無い） | この計画の「この計画が決める 2 点」と `code-summary.md` の「上流との差」に 1 か所で書く |
| NFR 設計 R-05（Hikari の実測の根拠・パスワード違い） | Step 5（閉じたポート。コンテナ不要）と Step 14（パスワード違い。コンテナ）の両方で全ロガーの出力を確かめる。実測の再現手順（版・条件・見えた行）は `code-summary.md` に残す |
| NFR 設計 R-06（最初の接続が Flyway になる前提） | 閉じたポートの起動のテスト（Step 5 の T3）が、ERROR がちょうど `Internal DB startup failed` の 1 件で、Spring の既定の失敗の文（ホスト・ポート）が出ないことまで確かめる |
| NFR 設計 R-07（NFR8 の記録の持ち主） | コード生成が持つ: 依存の追加の確かめ（ライセンス・lockfile・依存の木の前後・OSV-Scanner・WAR への同梱）を `code-summary.md` に書く。`META-INF/third-party-licenses` の一覧の決まりの確認も `code-summary.md` に書く（Apache License 2.0 で独立の文書は要らない見込み。決まりを読み直して書く）。Build and Test が全件の `verify` で再確認する |
| NFR 要件の審査の Major（Hikari の主張の言い回し） | 文書の言い回しの直し。コードの手順は変わらない（承認の場で設計の文書を直す） |
| インフラ設計の審査の Minor（CI の手順の置き場・`docs/verification.md` の持ち主・警報の言い回し・NFR4.2/4.12 の根拠・ダッシュボードに H2 の項目が無いこと） | CI の手順は「WAR の保存の後・3 つの報告の手順の前」に置く（Step 12）。`docs/verification.md` の書き足しは U4（文書の単位）に渡し、U1 は測った値と手動の実行の手順を `code-summary.md` に残す。ほかは設計の文書の直し |

## 要件との対応

| 要件 | 計画の手順 |
|---|---|
| FR1.1・FR1.2・FR1.3・FR1.4 | Step 4・5（種類の判断・起動を止める・環境変数だけ。T1・T2・T6・T7・T10） |
| FR2.1・FR2.3 | Step 2・3（移行の一式・突き合わせ）、Step 14（空の PostgreSQL への移行） |
| FR2.2 | Step 3（H2 の移行の SHA-256 の固定）、Step 5（T6 前の版の H2 のファイル） |
| FR2.4 | Step 4・5（移行の失敗の記録・版の番号）、Step 14 |
| FR2.5 | Step 14（初期管理者を PostgreSQL で 1 回だけ作る） |
| FR3.2 | Step 2・3（制約の名前・NO ACTION の外部キー） |
| FR3.3 | Step 4・5（接続ごとの `lock_timeout`）、Step 14（`SHOW lock_timeout`・握られたときの 2 秒台の失敗） |
| FR3.6 | Step 2・3（バイナリ 4 列の `BYTEA`）、Step 14（列の型が `bytea`・起動時の validate） |
| FR3.7 | Step 2（時刻の列は `TIMESTAMP WITH TIME ZONE` のまま。書き換えなし） |
| FR3.9 | Step 14（ヘルスチェックの UP・DOWN・UP） |
| FR3.10 | Step 4・5（ENT-005 だけを出す・cause なし・`TraceAspect` の外）、Step 14（パスワード違い） |
| FR5.1・FR5.2・FR5.3 | Step 9・10・11・12・13・14（土台・Gradle・CI・コンテナの確認） |
| FR5.5 | Step 3・5・11（普段の `verify` に入る単体テストと H2 の結合テスト） |
| NFR1.2・NFR1.3 | Step 4（移行の時間の INFO）、Step 14（`InternalDbPostgresBaselineIT`）。値の記録は Build and Test |
| NFR2.x | Step 4・5・14（ログの形・実測の網・TRACE）、Step 8（`application.yaml` の読み合わせ） |
| NFR4.x | Step 4・5・14 |
| NFR5.x | Step 9・10（通常の検査に入れない仕組み）、Build and Test（時間の記録） |
| NFR6.x | Step 5（単体テストと H2 の結合テストだけで `internaldb.platform` の下限を満たす） |
| NFR8.x | Step 1・10（依存・lockfile・依存の木・WAR）、`code-summary.md` |
| NFR9.x | Step 6（境界テスト） |

## 実施の手順（Testing Contract: test-after。層ごとに実装を書き、その層のテストを書いて流す）

層のうち、API・エンドポイントの層、repository の層、画面（frontend）の層は、この単位に当たらない（画面入出力も DB アクセスも持たない。機能設計 3 節）。当たる層は、データモデル・DB の振る舞い（移行）、業務処理に当たる部品（`internaldb.platform`）、環境・ビルドの設定、結合テストの土台。

- [x] **Step 1: 依存の追加と、実行の確かめ（骨組み）**
  - `gradle/libs.versions.toml` に `flyway-database-postgresql = { module = "org.flywaydb:flyway-database-postgresql" }`（版なし。Spring Boot 4.1.2 の BOM が管理）を足し、`backend/build.gradle.kts` に `runtimeOnly(libs.flyway.database.postgresql)` を足す。`./gradlew resolveAndLockAll --write-locks` で `backend/gradle.lockfile` を更新する（NFR8.2、BR2.12）。
  - 足す前と後で `./gradlew :backend:dependencies --configuration runtimeClasspath` を比べ、既存の部品の版が上がっていないこと（`flyway-core` と同じ 12.4.0 だけが増える）を確かめて、`code-summary.md` に書く（NFR8.3）。ライセンス（Apache License 2.0）は POM で確認済みの見込みを再確認して書く。OSV-Scanner は、依存が lockfile 経由で対象に入ることを、Build and Test の全件の `verify` で確かめる（NFR8.4）。
  - 既存のテストの実行の入口が使えることを確かめ、unit-test-instructions.md の命令を実際に流して通ることを記録する（下の 3 つ。まだテストが無いので 0 件で、タスクが解決することだけを確かめる）。
  - 既存のテストのうち、内部DB の設定・`FlywayMigrationStrategy`・`HikariDataSource`・`spring.datasource` に触れるもの（`DataSourcePoolIT`・`HikariJmxSettingsTest`・`H2DefragOnCloseTest`・`H2CompactionByPoolSuspensionIT`・`TestHikariMbeansEnvironmentPostProcessor`・`TestDatabase`・`ConnectionAcquireCounter`・各機能のテスト用の起動クラス `BusinessDataTestApp`・`MailTestApplication`・`InvitationApps` など）が、新しい部品の入った起動で変わらずに通ることを、Step 11 の全件の結合テストで確かめる（影響の洗い出しは Step 1 で行い、結果を `code-summary.md` に書く）。
  - 同じイメージ・同じ値を持つファイルの洗い出し: PostgreSQL のイメージは `TargetDbImages.postgres()` だけを使う（新しい定数を作らない）。ほかに写す場所は無い（compose は U4）。

- [x] **Step 2: PostgreSQL 用の移行の一式（データモデル・DB の振る舞い。実装）**
  - `backend/src/main/resources/db/migration-postgresql/` に `V1__u1_baseline.sql` 〜 `V14__u2_display_time_zone.sql` の 14 本を、H2 用（`backend/src/main/resources/db/migration/`）と同じファイル名で作る。内容は H2 用をそのまま写し、次の書き換えだけを当てる（BR2.5・BR2.6。名前は H2 と同じ）。
    - 列 `refresh_tokens.token_hash`（V3）・`invitations.token_hash`（V8）: `BINARY(32)` → `BYTEA`（BR4.2。要件 FR3.6）。
    - 列 `dsl_previews.yaml_bytes`・`dsl_applied_revisions.yaml_bytes`（V5）: `BINARY LARGE OBJECT` → `BYTEA`（BR4.1。FR3.6）。
    - 列 `invitations.pending_email`（V8。生成列）: `GENERATED ALWAYS AS (CASE WHEN state = 'PENDING' THEN email END) STORED` とし、同じ名前の一意の制約 `uk_invitations_pending_email` を付ける（BR2.6 (3)、D-5。FR2.1）。
    - V10〜V12 の外部キー 7 つ: `ON DELETE RESTRICT` を外す（制約の名前は H2 と同じ。BR2.5。FR3.2）。理由の注（先頭のコメント）を V10・V11・V12 に書く。
    - ほか（V1・V2・V4・V6・V7・V9・V13・V14 と、V3・V5・V8・V10〜V12 の上の列・制約以外）は H2 用と同じ文。時刻の列は `TIMESTAMP WITH TIME ZONE` のまま（BR4.5。FR3.7）。
  - 各ファイルの先頭に `--` 形式の Apache License 2.0 ヘッダーを置く（H2 用と同じ）。H2 用のファイルには手を入れない（BR2.1）。
  - 書き換えは H2 用の写しから作り、作った後に H2 用と文を比べて、書き換えが上の列・制約だけであることを `diff` で確かめる。

- [x] **Step 3: 移行の突き合わせの単体テスト（BR2.8。データモデル・DB の振る舞いのテスト）**
  - `backend/src/test/java/cherry/mastersmith/internaldb/migration/` に、次を置く（クラスの名前は目安。(a)〜(f) を分けてよい）。
    - `H2MigrationFilesUnchangedTest`: (a) `classpath:db/migration` の V1〜V14 のファイルごとの SHA-256 が固定の一覧（テストの中の定数。計算した値を書く）と同じ。(b) `classpath:db/migration` を再帰して探すと、H2 のファイル 14 本だけが見つかる（`db/migration-postgresql` が混ざらない。D-3）。
    - `MigrationSetsConsistencyTest`: (c) 2 つの置き場のファイル名（版と説明）の集まりが同じで、版が 1 から欠けなく続く。(d) 同じ版の 2 つのファイルの `objectNames`（表・名前付きの制約・索引の名前の集まり。名前の無い主キーは比べない）が同じ。(e) PostgreSQL の置き場に `ON DELETE`・`BINARY(`・`BINARY LARGE OBJECT` が無い（**コメントを取り除いてから探す**）。(f) 突き合わせの部品自身を確かめる小さな例（わざと片方に足した名前・欠けた版・変えたファイル）で失敗する。
    - `MigrationObjectNames`（`src/test/java/.../internaldb/testsupport`）: SQL から `objectNames` を取り出す部品（テストの補助）。
  - 件数の目安: 突き合わせ 6〜8 件、SHA-256 1 件（14 ファイル分）、置き場の再帰 1 件。

- [x] **Step 4: 起動の入口の部品（業務処理に当たる部品。実装）**
  - `backend/src/main/java/cherry/mastersmith/internaldb/platform/` に、次を作る（名前は決まり。置き場は `TraceAspect` の対象の層の外。BR5.6）。すべて Apache License 2.0 のヘッダー付き。
    - `InternalDbKind`（列挙。`H2`・`POSTGRESQL`。種類ごとの URL の先頭・移行の置き場・接続のたびの文を持つ。H2 の接続のたびの文は無し。ENT-001）。
    - `InternalDbUrlResolver`（純粋関数）と `InternalDbUrlResolution`（sealed interface。`Supported(kind)`・`Empty`・`Unsupported`）。`jdbc:h2:`・`jdbc:postgresql:` の前方一致（大文字と小文字を区別・前後の空白は取らない）。例外を投げない（ENT-002。BR1.1・BR1.3・BR1.4）。
    - `InternalDbSettings`（値。`Environment` から `spring.datasource.url` を 1 回読み、結果を持つ。`spring.datasource.url` の値が環境に **無い**ときは、種類の判断も接続プールへの設定もしない — 下の Assumptions）。
    - `StartupFailureKind`（列挙。`EMPTY_URL`・`UNSUPPORTED_KIND`・`CONNECTION_FAILED`・`MIGRATION_FAILED`・`MIGRATION_VALIDATION_FAILED`・`UNKNOWN`）、`StartupFailureReport`（record。ENT-005。`failureKind`・`exceptionTypes`・`sqlState`・`migrationVersion`・`settingName`・`supportedKinds`）、`StartupFailureReporter`（分類と ERROR 1 件の出力）。分類の順: SQLState の先頭 08・28・3D → `CONNECTION_FAILED`、**SQLState `55P03`（この計画の決め）→ `MIGRATION_FAILED`（版の番号なし）**、Flyway の移行の文の失敗 → `MIGRATION_FAILED`（版の番号つき）、Flyway の検証の例外 → `MIGRATION_VALIDATION_FAILED`、それ以外 → `UNKNOWN`。連なりを辿る深さの上限は 10。版の番号は、移行の失敗の例外の文の移行のファイル名（`V<数字>__`）か検証の例外の `migration version <数字>` から数字だけを取り出す（文を読むのはこの 1 か所だけ。BR5.2・BR5.3）。出すのはクラス名・SQLState・版の番号・設定の名前・対応する種類だけ。
    - `InternalDbStartupException`（文は `failureKind` と固定の説明だけ。cause なし。BR5.1）。
    - `InternalDbPoolCustomizer`（静的な `BeanPostProcessor`。`postProcessAfterInitialization`。**Bean の名前が `dataSource` のときだけ**当てる。種類が PostgreSQL: `HikariDataSource` なら `setConnectionInitSql("SET lock_timeout = '2s'")`、そうでなければ（またはプールが既に始まっていれば）`InternalDbStartupException`（`UNKNOWN`）。種類が H2: 何もしない。`EMPTY`・`UNSUPPORTED`: ERROR 1 件の後 `InternalDbStartupException`。種類が確定したときに INFO 1 行 `Internal DB kind resolved`（`internalDbKind`）を出す。NFR4.1・NFR4.10）。
    - `InternalDbFlywayCustomizer`（`FlywayConfigurationCustomizer`。種類に応じた置き場 `classpath:db/migration` か `classpath:db/migration-postgresql` を渡す。BR2.2）。
    - `InternalDbMigrationStrategy`（`FlywayMigrationStrategy`。`flyway.migrate()` を呼び、失敗を `StartupFailureReporter` で記録して `InternalDbStartupException` を投げ直す。成功時に INFO 1 行 `Internal DB migration finished`（`migrationMillis`・`migrationCount`。`System.nanoTime` の差）を出す。BR2.9・NFR1.2・DD-6）。
    - `InternalDbPlatformConfiguration`（`@Configuration`。静的な `@Bean` の BPP と、`InternalDbSettings`・Flyway の設定の差し替え・移行の入口の Bean）。
  - ログはすべて SLF4J のキー・値 API（文字列の連結にしない）。メッセージは固定の英語の短文（`Internal DB startup failed` など）。接続先・利用者名・パスワード・ホスト・ポート・URL・例外の文をどのキーにも入れない（NFR2.2）。
  - **Q1: A（依頼者の決定）**: 大きなバイナリの 2 列のエンティティの写し方を、この単位で直す。`DslPreviewRecord.yamlBytes`・`DslAppliedRevisionRecord.yamlBytes` の `@Lob` を外し、`@JdbcTypeCode(SqlTypes.LONG32VARBINARY)` を付ける（BR4.1。H2 の列は `BINARY LARGE OBJECT` のまま、PostgreSQL の列は `BYTEA`。捨ての試し E2 で両方の validate と 10MB の往復が通ると確かめ済み）。この変更は U2 の仕事の先取りで、U2 の計画はその分が済んだ前提で作る。直した後、H2 の既存の結合テスト（DSL の投入・適用・本文の往復）の全件が通ることを Step 11 で確かめる。
  - 上のエンティティ 2 つを除く既存の `src/main` の機能（`config`・`targetdb`・業務の機能）と `application.yaml` の値には手を入れない（BR1.x・BR3.5。機能設計 1.1）。

- [x] **Step 5: 起動の入口の部品のテスト（業務処理に当たる部品のテスト。コンテナ不要）**
  - 単体テスト（`backend/src/test/java/cherry/mastersmith/internaldb/platform/`）:
    - T1 `InternalDbUrlResolverTest`: 種類の判断の表（H2・PostgreSQL・MySQL・MariaDB・空・空白だけ・大文字の `jdbc:POSTGRESQL:`・前に空白・接頭辞だけ・`jdbc:` だけ）。パラメーターを使うテスト。
    - T4 `StartupFailureReporterTest`: 失敗の整形。URL・利用者名・パスワード・ホストの文字列を文に入れた例外を渡しても出力に出ないこと、`failureKind` の分類の表（**`55P03` のみ・移行の文の失敗・検証の失敗・接続の失敗・それ以外**）、本物の Flyway の例外（H2 の内存の DB で壊した移行・変えた移行を流して作る）から版の番号が取れること、深さの上限 10、版の番号は数字だけが出ること。
    - T7 `InternalDbKindTest`: 種類が H2 のときの設定（置き場が `classpath:db/migration`、接続のたびの文が無い）と、PostgreSQL の設定。
    - T8 `InternalDbPoolCustomizerTest`: 接続を開かずに、設定の値だけで確かめる。(1) PostgreSQL の `dataSource` の Hikari にだけ文が入る、(2) 名前が `dataSource` でない Bean（`targetDataSource` など）と H2 のプールには何も入らない、(3) 利用者の `connection-init-sql` が置き換わる、(4) 最初の接続の前の setter が効く、(5) **`dataSource` が Hikari でない・プールが始まっていたときは黙って通らない（`UNKNOWN` で止まる）**、(6) `spring.datasource.url` が環境に無いときは何もしない、(7) INFO の `internalDbKind` が出る。
  - 起動を含むテスト（Spring を使うが DB の種類は H2 か、接続できない先）:
    - T6 `InternalDbH2PreviousVersionIT`: 前の版が作るのと同じ H2 のファイル（H2 の置き場だけで作り、利用者を 1 人入れたもの）に、接続先を変えずに起動し、移行の検証が通り、その利用者でログインできる（BR1.7）。
    - T2・T3 `InternalDbStartupFailureIT`: (a) 対応しない接続先（MySQL の形）と空の接続先で起動が止まり、ERROR がちょうど 1 件 `Internal DB startup failed`（`failureKind`・`settingName`・`supportedKinds`）、URL・利用者・パスワードが出ない。連なる例外（Spring が包む Bean 作成の失敗を含む）の文にも出ない。(b) **閉じたポートの PostgreSQL の接続先**（コンテナ不要）で起動が止まり、ERROR がちょうど 1 件で `failureKind=CONNECTION_FAILED`・SQLState `08001`・例外の型の名前が出て、ホスト・ポート・利用者名・パスワードが **アプリの全ロガー**（Hikari・Spring を含む）の出力に出ない（NFR2.6・NFR4.4・R-02・R-06 の網）。
    - T11 `InternalDbSecretLeakIT`: `TraceAspect` の TRACE を有効にして、URL・利用者・パスワードを入れた対応しない接続先で起動を試み、ログに値が出ない（NFR4.12。`team.md` の `XxxSecretLeakIT` の形）。
  - 件数の目安: 単体約 40 件（パラメーター化を含む）、結合 3 クラス（H2 の起動 4〜5 件）。

- [x] **Step 6: 構造の境界テスト（NFR9.2。業務処理の層のテスト）**
  - `backend/src/test/java/cherry/mastersmith/internaldb/InternalDbBoundaryArchitectureTest.java`（T10）: `internaldb.platform` が依存してよい先を JDK・Spring・Flyway・Hikari・SLF4J・`internaldb` の中だけに固定し、業務の機能（`auth`・`user` など）と `targetdb` に依存せず、依存もされない（Spring の自動の組み立てを除く）。既存の `ArchitectureTest` と他の機能の境界テストは書き換えない（緩めない・消さない）。新しいパッケージで既存の `ArchitectureTest` が落ちたときは、直さずに依頼者に諮る（既存の境界テストを緩めるには、この計画の明記と承認が要る）。
  - `application.yaml` の値の確かめ（T10 の一部。`InternalDbConfigurationFilesTest`）: `logging.level.org.postgresql: OFF` と `org.hibernate.orm.jdbc.error: OFF` が残ること（BR3.5・NFR2.8）、`spring.datasource` の環境変数の名前（`MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD`・`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`）が変わらないこと（BR1.6）。

- [x] **Step 7: 実行して通す（ここまでの層）**
  - Step 3・5・6 のテストを unit-test-instructions.md のコマンドで流し、すべて通ることを確かめる。通らないものは直してから先へ進む。H2 の結合テストの既存の全件は Step 11 で流す。

- [x] **Step 8: 設定の読み合わせ（環境・ビルドの設定の前の確かめ）**
  - `backend/src/main/resources/application.yaml` に差分が無いこと、`.env.example`・`Dockerfile`・`compose.yaml` に差分が無いこと（この単位は触れない。U4）を `git diff --stat` で確かめる。

- [x] **Step 9: 結合テストの土台（テストの補助。実装）**
  - `backend/src/test/java/cherry/mastersmith/internaldb/testsupport/` に次を置く（D-7・BR6.5〜BR6.7）。H2 に頼る書き方（`INFORMATION_SCHEMA.SESSIONS`・H2 のトリガー・`DATEADD`・`RANDOM_UUID()`）は使わない。説明文は英語、メールアドレスは `example.com` だけ。
    - `InternalDbPostgresIT`（注釈。JUnit のタグ `internal-db-postgres`、`@ExtendWith(InternalDbPostgresRuntimeCheck.class)`）。
    - `InternalDbPostgresRuntimeCheck`（`BeforeAllCallback`。Docker が使えれば進む。使えず、環境変数 `CI` が `true`、またはシステムプロパティ `mastersmith.requireContainerRuntime` が `true` なら失敗。それ以外は警告（アプリのログと標準エラー。内部DB の PostgreSQL のテストを飛ばしたこと・この状態では統合しないことを書く）を出して `TestAbortedException`）。判定の部分は純粋な関数にする（BR6.4）。
    - `InternalDbPostgresContainer`（`TargetDbImages.postgres()` のコンテナを JVM ごとに 1 つ起動して使い回し、JVM の終わりで止める。BR6.5）。
    - `InternalDbPostgresDatabase`（実行ごとに一意の名前 `msidb_<乱数>` の専用データベースを `CREATE DATABASE`、終わりに `DROP DATABASE`。接続先・利用者・パスワードを返す。BR6.6）。
  - 境界テスト `InternalDbBoundaryArchitectureTest` に、拡張の順序（コンテナ・文脈を作る拡張が、確認の拡張の後）を確かめる検査を足す（BR6.5。`targetdb` の `ExtensionOrderArchitectureTest` と同じ決まり）。
  - T9 `InternalDbPostgresRuntimeCheckTest`: 判定の表（届く・届かず CI・届かずプロパティ・届かず手元）。

- [x] **Step 10: Gradle の専用のタスクと検査（環境・ビルドの設定）**
  - `backend/build.gradle.kts`: (1) タスク `internalDbPostgresTest`（`Test` 型。`integrationTest` と同じクラスパス・JVM の設定。JUnit のタグ `internal-db-postgres` だけを流す）。`verify`・`verifyQuick`・`check` の依存に入れない。JaCoCo の記録は取らない（`jacoco` の拡張を無効にする）。(2) `integrationTest` は `excludeTags("internal-db-postgres")`（設定の 1 か所。BR6.2）。(3) `integrationTest` の `-PitTests` が付いていて、実行の終わりの集計（`afterSuite` の `testCount`。飛ばした分も数える）が 0 件のとき、「タグ `internal-db-postgres` のクラスは `:backend:internalDbPostgresTest` で流してください」の文で失敗にする（BR6.3・D-8）。`internalDbPostgresTest` も 0 件なら失敗にする。(4) タスク `verifyPostgresMigrationsInWar`（`verifyDslSchemaInWar` と同じ作り）: 実行可能 WAR の `WEB-INF/classes/db/migration-postgresql/` に、H2 用の `WEB-INF/classes/db/migration/` と同じ名前のファイルが同数ある。
  - ルートの `build.gradle.kts`: `verifyDslSchemaInWar` と同じ段（`verifyArtifact`）に `verifyPostgresMigrationsInWar` を足す。段の説明の文も直す。
  - 通常の `verify` に足す時間は、足す単体テストと H2 の結合テストと `verifyPostgresMigrationsInWar` だけ（NFR5.3）。

- [x] **Step 11: 既存の結合テストの全件と、H2 側の確かめ**
  - 新しい部品の入った状態で、既存の結合テストの全件（`:backend:integrationTest`）が通ることを確かめる。落ちるものがあれば、原因が新しい部品か既存のテストの前提かを切り分け、新しい部品の側で直す（既存のテストを書き換えない）。コンテナの実行環境（colima）が要る対象DB のテストは、`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して飛ばさずに流す。

- [x] **Step 12: CI の手動の入力（環境・ビルドの設定）**
  - `.github/workflows/ci.yml`: `on.workflow_dispatch.inputs.internal_db_postgres`（`type: boolean`・`default: false`・説明は日本語）を足す。新しい手順を `verify` の job の中の **WAR の保存の後・3 つの報告の手順の前**に足す: `if: inputs.internal_db_postgres == true`、`run: ./gradlew :backend:internalDbPostgresTest`。入力の値をシェルの文に埋め込まない。Actions の固定のハッシュは今のまま。新しい Action は足さない。秘密情報は使わない。
  - この変更は 1 コマンドの検査では流れない。式 `inputs.internal_db_postgres == true` が boolean の入力で成り立つことは、Build and Test で手動の実行を 1 回流して確かめる（記録は `code-summary.md` の「Build and Test に渡すこと」）。

- [x] **Step 13: PostgreSQL の結合テスト 1: 移行・初期管理者・指標（実装してから書く・流す）**
  - `backend/src/test/java/cherry/mastersmith/internaldb/postgres/InternalDbPostgresMigrationIT`（`@InternalDbPostgresIT`。専用の空のデータベースに本番と同じ設定でアプリを起動）: (a) `flyway_schema_history` に V1〜V14 が成功で記録される、(b) H2 の置き場に書かれた制約と索引の名前がすべて PostgreSQL のカタログにある、(c) 起動時の `ddl-auto: validate` が通ってアプリが起動する（**ここで落ちるのは `@Lob` の 2 列。U2 が直す**。U1 の時点でこの確かめが通らないときの扱いは下の Open questions）、(d) バイナリの 4 列の型が `bytea`、(e) `SHOW lock_timeout` が 2 秒、(f) 初期管理者が 1 人だけ作られ、同じデータベースで文脈を作り直しても重ねて作られない、ログにパスワードが出ない（BR2.11）、(g) `MeterRegistry` に `hikaricp.connections` のメーターが `pool=mastersmith-db` のタグで 1 つ以上ある（NFR4.11）。

- [x] **Step 14: PostgreSQL の結合テスト 2: 失敗・握られた移行・ヘルスチェック・基準（実装してから書く・流す）**
  - `InternalDbPostgresMigrationFailureIT`: (a) 専用のデータベースに前もって同じ名前の表を作っておくと、起動が止まり、ERROR に `MIGRATION_FAILED`・失敗した版の番号が出て、接続先・利用者・パスワードが出ない（BR2.10）。(b) **対象の表を握られた**: 先に V1〜V13 まで移行したデータベースで、外の接続が V14 の変える表を排他で握ると、起動が 2 秒台で `MIGRATION_FAILED`・版の番号 14 つきで止まり、握りを解くと次の起動が通る。(c) **履歴の表を握られた**: 外の接続が `flyway_schema_history` を排他で握ると、起動が 2 秒台で `MIGRATION_FAILED`・版の番号なしで止まる（この計画の決め）。
  - `InternalDbPostgresStartupFailureIT`: パスワード違い（`28P01`）で起動が止まり、`CONNECTION_FAILED`・ERROR がちょうど 1 件で、アプリの全ロガーの出力に URL・利用者名・パスワード・ホスト・ポートが出ない（NFR2.6。R-05）。
  - `InternalDbPostgresHealthIT`: `/actuator/health` が UP、コンテナを一時停止して届かないとき DOWN、再開して UP に戻る（BR3.6）。
  - `InternalDbPostgresBaselineIT`: アプリの起動の前後の時刻と、`/actuator/health` が UP になった時刻の差を取り、標準出力に `INTERNAL_DB_PG_BASELINE startupToHealthMs=<値>` を 1 行出す。合否には使わない（DD-6。NFR1.2）。
  - ここまでを `./gradlew :backend:internalDbPostgresTest`（コンテナの実行環境を渡して）で流し、通ることを確かめる。通らないテストは、原因が新しい部品か土台かを切り分けて直す。

- [x] **Step 15: 記録と traceability（文書・追跡）**
  - `unit-test-instructions.md` の命令どおりにすべて流し、結果を `code-summary.md` に書く（ファイルの一覧・実装の決め・テストの結果・流していないテストとその理由・計画との差・**上流との差（この計画が決める 2 点を含む）**・NFR8 の記録・実測の再現手順（Hikari のログ。版・条件・見えた行）・Build and Test に渡すこと）。
  - `aidlc engine sensor-traceability --stage code-generation --output-path <この段の traceability.json> --write-upstream` で `traceability.json` を作り、各行の `status` と `target`（実在するコード・テストのファイル）を埋める。
  - `source-manifest.json` に、この単位が作った・変えたすべてのパス（`backend/src/main/java/cherry/mastersmith/internaldb/`・`backend/src/main/resources/db/migration-postgresql/`・テスト・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`backend/build.gradle.kts`・`build.gradle.kts`・`.github/workflows/ci.yml`）を書く。
  - コミットの区切り（提案して承認を得る。各コミットの時点でコンパイルが通る）: (1) 依存・lockfile、(2) PostgreSQL 用の移行と突き合わせのテスト、(3) `internaldb.platform` の部品とそのテスト、(4) テストの土台・Gradle のタスク・`verifyPostgresMigrationsInWar`・PostgreSQL の結合テスト、(5) `ci.yml`。

## Assumptions & Open Questions

- [assumption] `spring.datasource.url` が環境に無い（`application.yaml` の既定が効かない）文脈、例えば `spring.datasource` を使わずに部分的な起動クラスだけで作る一部のテストでは、種類の判断も接続プールへの設定もしない（「設定されているが空」だけがエラーという BR1.3 と、既存のテストへの影響を避けるため）。Step 5 の T8 (6) で確かめる。
- [assumption] 起動時の失敗の ERROR のメッセージは固定の英語の短文（`Internal DB startup failed`・`Internal DB kind resolved`・`Internal DB migration finished`）。文言は目安で、キーの名前と、値に接続先を入れないことが決まり（observability-design.md の Assumptions）。
- [Q1] 決定済み（依頼者の答え: A）。U1 の中で `@Lob` の 2 列を `@JdbcTypeCode(SqlTypes.LONG32VARBINARY)` に直す（Step 4）。これで Step 13 (c) の起動時の validate は U1 の時点で通る前提で書く。通らないときは、原因を切り分けて依頼者に諮る（validate を切って通さない）。
- [assumption] 外の接続の握りを作る確かめ（Step 14 (b)(c)）の、握らせる表と握る文（`LOCK TABLE … IN ACCESS EXCLUSIVE MODE` を開いたトランザクションの中で）は、PostgreSQL 18.6 のコンテナで実際に 2 秒台で切れることを書きながら確かめる。切れない形になったら、握る文を変え、結果の要点を `code-summary.md` に書く。
- [assumption] `internalDbPostgresTest` の 0 件の失敗は、`testCount`（飛ばした分を含む）で判定する。手元で Docker が無く全件が飛ばされたときは、警告を出して通る（失敗にしない。BR6.4）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`reviews/review-01.md`（R-01〜R-07）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/tech-stack-decisions.md`・`performance-requirements.md`・`security-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/reliability-design.md`・`performance-design.md`・`security-design.md`・`observability-design.md`・`logical-components.md`・`reviews/review-01.md`（R-01〜R-07）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C1〜C6）
- `aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（初期管理者・秘密情報の漏えい）・`checklist-dsl.md`（DSL の本文の列）・`checklist-timezone.md`（時刻の列）
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`
- コードで確かめたこと: `backend/build.gradle.kts`（`integrationTest`・`-PitTests`・`verifyDslSchemaInWar`）、`build.gradle.kts`（`verifyArtifact` の段）、`backend/src/main/resources/application.yaml`（`spring.datasource`・`spring.flyway`・`logging.level`）、`.github/workflows/ci.yml`、`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/`（`ContainerRuntimeCheck`・`PostgresTestDatabase`・`TargetDbImages`）、`backend/src/main/resources/db/migration/`（V1〜V14）

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
      "text": "- **Methodology**: test-after\n- **Methodology evidence**: 今の `team.md` で確定した決まりを引き継いだ。各 Bolt で層ごとに実装を書いてから同じ Bolt の中でその層のテストを書いて流す形で、これまでの Intent もこの形で進めてきた。今回の Intent（内部DB に PostgreSQL を足す）で進め方を変える理由は見つかっていない。\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- 統合の前の `./gradlew verifyQuick` は、バックエンドのカバレッジの下限を判定しない（単体テストの記録だけから参考値を `backend/build/reports/jacoco/quick/` に出して表示し、下回っても失敗にしない）。バックエンドの下限は、全件の `./gradlew verify` を流す場面（Way of Working）と CI で判定する。画面のカバレッジの下限は `verifyQuick` でも判定する。（2026-11-08 Intent 261107-verify-quick-local で追加）\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる（外すパッケージは無い）。全体の合計で判定していた既存のパッケージの一覧（`backend/build.gradle.kts` の `packagesJudgedByTotal`。最後は 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`））は、Intent 261108-ci-checks-release で `common.health` にテストを足して下限を満たし、一覧とそれを使った除外の仕組みを消した。パッケージを外す一覧を再び作らず、下限を満たさないパッケージはテストを足して満たす。（2026-11-08 Intent 261107-verify-quick-local で書き換え）（2026-11-09 Intent 261108-ci-checks-release で書き換え）\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを全件の `./gradlew verify` の中と CI で毎回実行する。統合の前の `./gradlew verifyQuick` では、`-PitTests` で絞ったときに当たるものだけを流す。コンテナの実行環境が無いときの扱いは Way of Working のとおり。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- テストを足した Bolt の後は、統合の前に流した1コマンドの検査（`./gradlew verifyQuick`。全件の場面に当たる Bolt では `./gradlew verify`）の時間を、Build and Test では全件の `./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB を使うテストは組み込みの H2 を基本とし、コンテナではなく本番と同じ組み込みの H2 で行う。H2 での結合テストの全件は今のまま残す。内部DB を PostgreSQL で動かす経路に固有の振る舞いを確かめる結合テストは、H2 の全件とは別のバリエーションとして作り、版を固定したイメージのコンテナで起動した実際の PostgreSQL で行う（固い制約は `project.md` の Mandated）。このテストは明示の指定（Gradle の指定。名前は設計で決める）を付けたときだけ流し、通常の `./gradlew verify`・`./gradlew verifyQuick`・CI（`develop` へのプッシュ）では流さない。流す場面は決めず、必要と判断したときに明示して流す（CI で流すときも明示の指定を付けた実行に限る。形は設計で決める）。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストと、内部DB の PostgreSQL の結合テストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) （2026-11-14 Intent 261114-internal-db-postgresql で書き換え。H2 を基本とし、PostgreSQL に固有の結合テストを明示したときだけ流す形にした）\n\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実行し直し、実測の数字だけを報告する。`./gradlew verifyQuick` の JaCoCo の値は単体テストだけの参考値で、カバレッジの報告には使わない。 (learned 2026-09-23)  （2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。すべての組で行うのは、その Intent で変えた画面だけとし、変えていない画面は E2E の既存の検査に任せる（2026-11-07 依頼者の決定で緩めた）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify`、または `-PitTests` を付けた `./gradlew verifyQuick` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、`verify` ではパッケージごとのカバレッジの下限で失敗する。`-PitTests` を付けた `verifyQuick` では、バックエンドのカバレッジの下限を判定しないため、飛ばさずに失敗する（CI と同じ扱い）。 (merged 2026-11-04)（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)"
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
  "input_sha256": "sha256:552cb5658f2cb47e1e82a7327ac8ad923769dea2d1ef59be8f82d75eefe758af",
  "contract_sha256": "sha256:a556f2389fe8ebabfea24044e818005bb3c9a4fafaa7645490018229aa364acb"
}
```
