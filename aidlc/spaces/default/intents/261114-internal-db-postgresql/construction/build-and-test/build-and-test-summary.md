# Build and Test の要約 — Intent 261114-internal-db-postgresql

内部DB を組み込みの H2 に加えて PostgreSQL でも動かせるようにした Intent（4 単位: u1-internal-db-platform・u2-portable-sql・u3-postgres-behaviour-tests・u4-local-runtime-docs）の、ビルドとテストの結果です。実行日は 2026-11-28。数字は `test-results.md` の実測だけを使っています。

## 全体の状態

- ビルド: 成功（全件の `./gradlew verify` が `BUILD SUCCESSFUL in 29m 48s`。`it_forks` 指定なし）。
- 単体テスト: 326 クラス・3,079 件、失敗 0・エラー 0・スキップ 0。
- 結合テスト（内部DB は H2）: 285 クラス・1,732 件、失敗 0・エラー 0・スキップ 10（対象DB の種類ごとの条件付きのテスト）。
- 内部DB の PostgreSQL の結合テスト（明示して流した）: 31 クラス・76 件、すべて通過。
- E2E（Playwright、実ブラウザ Chromium で実行）: 298 件、すべて通過（`BUILD SUCCESSFUL in 16m 41s`）。
- カバレッジ: バックエンド全体 行 97.7%・分岐 91.5%。72 パッケージのすべてが下限（行 80%・分岐 70%）を満たす。
- 戦略は Standard。`performance-test-instructions.md`・`security-test-instructions.md` は作らない（性能は PostgreSQL のときの合否に使わない決定、セキュリティは各単位の結合テストと Gitleaks・OSV-Scanner・SpotBugs が持つ）。

## テストの種類の一覧

| 種類 | 内容 | 流し方 |
|---|---|---|
| 単体テスト | 接続先の種類の判定・起動の失敗の報告・接続プールの設定・書き込みの手順・分類・構造（ArchUnit） | `./gradlew verify`（`:backend:test`） |
| 結合テスト（H2） | 既存の全件と、この Intent で足した前の版の H2 ファイルでの起動・起動の失敗・TRACE の漏れ・書き込みの手順 | `./gradlew verify`（`:backend:integrationTest`） |
| 結合テスト（PostgreSQL） | 11 機能の代表の流れ・違反の区分・待ちの上限と行き詰まり・失敗後のトランザクション・書き直した SQL・10MB と 32 バイト・時刻・検索 | `./gradlew :backend:internalDbPostgresTest`（明示したときだけ） |
| 画面のテスト | 変更なし（`frontend/src` の差分なし）。`verify` の中で判定 | `./gradlew verify` |
| E2E | 変更なし（本数を足していない）。内部DB は H2 | `./gradlew e2eTest` |

詳しくは `integration-test-instructions.md`。

## カバレッジの期待

バックエンドは全体とパッケージごとに行 80%・分岐 70%（実測は上のとおり、すべて満たす）。この Intent で触れたパッケージの実測は `test-results.md`。

## Target Verification Matrix

Verdict の `Met` は、この段で実際に流した検査・読み取りで確かめたものです。`Unverified` は後の段・push 後・依頼者の確認でしか測れないもので、持ち主の段と期待する証拠の場所を書きました。`N/A` は、この計画で当たらないものです。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| REQ-FR1.1 | requirements.md FR1.1・u1 nfr-requirements/reliability-requirements.md NFR4.6 | 前の版の H2 の内部DB のファイルのまま起動し、移行の検証が通る | 通過 | `InternalDbH2PreviousVersionIT` が結合テスト 1,732 件の中で通過 | build-and-test | Met |
| REQ-FR1.2/1.3 | requirements.md FR1.2・FR1.3・u1 NFR4.4 | PostgreSQL の URL で動く種類の判定。対応しない種類・空・閉じたポートで有限の時間に起動が止まり、ログに URL・資格情報が出ない | 通過 | `InternalDbUrlResolverTest`（単体）・`InternalDbStartupFailureIT`（結合、コンテナ無し）が全件の `verify` で通過。PostgreSQL 側は 76 件の中の `InternalDbPostgresStartupFailureIT` | build-and-test | Met |
| REQ-FR2.1 | requirements.md FR2.1・FR2.5・u1 NFR4.2 | 空の PostgreSQL に V1〜V14 が `lock_timeout` 2 秒の接続のまま通り、`ddl-auto: validate` が通る。初期管理者が 1 回だけ作られる | 通過 | `InternalDbPostgresMigrationIT`（PostgreSQL の結合テスト 31 クラス・76 件の中。承認の確かめで通過） | build-and-test | Met |
| REQ-FR2.2/2.3 | requirements.md FR2.2・FR2.3・u1 NFR4.7 | H2 の移行が書き換わっておらず、H2 と PostgreSQL が同じ版の番号で組になる | 通過。起点 `65b2ba65` からの差分に `db/migration/` の変更なし | `H2MigrationFilesUnchangedTest`・`MigrationSetsConsistencyTest`（単体）が通過。`:backend:verifyPostgresMigrationsInWar` が `verify` の中で通過（正の確認） | build-and-test | Met |
| REQ-FR3.1 | requirements.md FR3.1・FR5.1 | 内部DB を使う 11 機能の代表の流れが、PostgreSQL でも H2 と同じ状態コード・`code`・項目で通る | 11 の流れのクラスが通過 | PostgreSQL の結合テスト 76 件（`PgAuthFlowIT` ほか 11 クラス） | build-and-test | Met |
| REQ-FR3.2 | requirements.md FR3.2・FR5.1 | 違反の区分（一意・親なし・参照のある削除）と一意の大文字小文字の扱いが H2 と同じ。`ON DELETE RESTRICT` の差（23001）を無くす | 通過 | `PgViolationClassificationIT`・`PgCaseSensitivityIT`（76 件の中） | build-and-test | Met |
| REQ-FR3.3 | requirements.md FR3.3・u3 NFR1.2・NFR4.1 | 書き込みの待ち 2 秒以上 3 秒未満、行の排他の待ち 3 秒以上 4 秒未満で切れ、上限なく待つ経路が無い（書き込み・行の排他・行き詰まり・SKIP LOCKED の 4 経路） | 書き込み 2,019 / 2,016 ms、行の排他 3,041〜3,047 ms、続く書き込み 2,049 ms、SKIP LOCKED 3,040 ms・21 ms、行き詰まり 1,060 ms。すべて幅の内 | `PgWriteWaitIT`・`PgRowLockWaitIT`・`PgDeadlockIT`・`PgSkipLockedLoginIT`（`INTERNAL_DB_PG_WAIT` の標準出力） | build-and-test | Met |
| REQ-FR3.4/3.5 | requirements.md FR3.4・FR3.5・u2 NFR4.1〜4.5 | SQL の失敗後に書きかけが残らず、書き直した SQL（`MERGE`・`FINAL TABLE` 等）が PostgreSQL でも H2 と同じ結果。H2 の結果は変わらない | 通過 | H2 側は結合テスト全件（`WorkRoleSelectionMergeIT` を含む）と `KeyedUpsertExecutorTest`、PostgreSQL 側は `PgFailureTransactionIT`・`PgPortableSqlIT`・`PgKeyConflictIT`・`PgLockFailureClassificationIT` | build-and-test | Met |
| REQ-FR3.6/3.7/3.8 | requirements.md FR3.6・FR3.7・FR3.8 | 10MB の DSL・32 バイトのハッシュが往復で変わらない。時刻が時差の設定に左右されない。検索の `%`・`_`・`\` が一致の条件にならない | 通過。ただし検索の Unicode の端 3 つ（`İ`・語末のシグマ・`ǅ`）は H2 と差が残り、文書に書いた（既知の制約） | `PgBinaryRoundTripIT`・`PgTimeRoundTripIT`・`PgUserSearchIT`（76 件の中）、`README.md` の既知の制約 | build-and-test | Met |
| REQ-FR3.9/3.10 | requirements.md FR3.9・FR3.10 | ヘルスチェックが届けば UP・届かなければ DOWN。接続失敗のログに原因が出て、URL・資格情報が出ない | 通過 | `InternalDbPostgresHealthIT`・`InternalDbStartupFailureIT`・`StartupFailureReporterTest` | build-and-test | Met |
| REQ-FR4.1/4.3 | requirements.md FR4.1・FR4.3・u4 NFR2.2・NFR2.3 | compose の内部DB 用の PostgreSQL が別のサービス・別のボリューム・別の資格情報で、既定では起動せず、既定の内部DB は H2 | 通過（`ports` なし・profile で選ぶ。profile なしの `docker compose config` の差は `depends_on`（`required: false`）の 4 行だけ） | u4 `code-summary.md` の Step 8 の確かめ。`git diff` で `compose.yaml` の差分を確認 | build-and-test | Met |
| REQ-FR4.2 | requirements.md FR4.2・NFR3 | 内部DB 用イメージが対象DB の PostgreSQL と同じ版とダイジェストで、固定先の一覧に足してある | 一致。`.github/dependabot.yml` はコメント 3 行の追加だけ | u4 `code-summary.md`（`compose.yaml` の 2 サービス・`TargetDbImages`・`docker/perf/compose.yaml` が一致）。`git diff --stat` で確認 | build-and-test | Met |
| REQ-FR4.4/4.5 | requirements.md FR4.4・FR4.5 | PostgreSQL の内部DB のバックアップと戻し、H2 だけの運用の明記、使い方の文書 | 文書あり（`docs/internal-db.md`・`README.md`）。手順は使い捨ての環境で `pg_dump`・`pg_restore` の往復を確かめ済み。アプリを PostgreSQL に接続した状態での練習は未実施 | u4 `code-summary.md` の Step 7・文書ごとのチェックリスト | build-and-test | Met |
| REQ-FR5.2/5.3 | requirements.md FR5.2・FR5.3・u1 NFR4.8・NFR5.2 | PostgreSQL の結合テストが通常の `verify`・`verifyQuick`・CI に入らず、明示したときだけ流れる。コンテナ無しのときは手元で警告して飛ばし、CI では失敗する | 全件の `verify` の結合テスト 1,732 件にタグ `internal-db-postgres` のクラスは入っていない（31 クラス・76 件は別に明示して通過）。`InternalDbPostgresRuntimeCheck` の単体テストが通過 | `test-results.md`、`InternalDbPostgresRuntimeCheck` の単体テスト | build-and-test | Met |
| REQ-FR5.4 | requirements.md FR5.4・u1 NFR1.1 | H2 の結合テストの全件が通り、件数が減らない | 285 クラス・1,732 件が通過、失敗 0・エラー 0（U1 の記録時点の 281 クラス・1,722 件より増え、減ってはいない） | `test-results.md`。Intent 開始前の件数は記録を持たないため、Intent 内の比較にとどめる | build-and-test | Met |
| REQ-NFR1 | requirements.md NFR1・u1 NFR1.1・NFR1.3 | H2 のときの性能・資源の目標を変えない。k6・perf の台本・環境を変えない | 変更なし。起点からの差分に `perf/`・`docker/perf/`・`frontend/src` が無い。H2 の結合テスト全件が通過 | `git diff --stat 65b2ba65..HEAD`（読み取り） | build-and-test | Met |
| REQ-NFR1-PG | requirements.md NFR1・6 章「PostgreSQL のときの性能の目標の合否の判定」 | PostgreSQL のときの性能の合否を判定しない（基準の値を記録するだけ） | 合否に使わない。基準: `startupToHealthMs=1560`・`migrationMillis=224`、共有アプリの起動 6,455 ms。応答時間・メモリは測っていない | `test-results.md`（`INTERNAL_DB_PG_BASELINE`）。U2 の書き込みの所要時間（U2 NFR1.4）も測っていない | build-and-test | N/A |
| U1-NFR1.2 | u1 performance-requirements.md NFR1.2 | PostgreSQL の基準として、移行の所要時間と起動からヘルス UP までを測って記録する（合否に使わない） | 記録した: `migrationMillis=224`・`startupToHealthMs=1560` ms | PostgreSQL の結合テストの標準出力（`INTERNAL_DB_PG_BASELINE`）。U1 の実測は 3 回で 4,078・7,870・5,983 ms と 226・637・375 ms（記録は `u1 code-summary.md`） | build-and-test | Met |
| U1-NFR1.4 | u1 NFR1.4 | `SET lock_timeout` が接続を作るときに 1 回だけで、借りる経路に処理を足さない | 通過 | 接続プールの初期化の文の単体テスト（`internaldb.platform`、全件の `verify` で通過） | build-and-test | Met |
| U2-NFR1 | u2 performance-requirements.md NFR1.1〜1.3 | H2 のとき `savePreferences` の文の数 1 本・あれば更新の文 1 本・接続を借りる回数・10MB の本文を複製しない | 通過 | 結合テストの全件（`common.persistence`・`dslmanage.repository`・`user.repository` の結合テストを含む）。コードの読み取りは U2 の記録 | build-and-test | Met |
| U1-NFR5.1 | u1 NFR5.1・requirements.md NFR5 | CI の検査の job の時間が、基準（中央値 21分32秒、`it_forks` 3）の 1.1 倍（約 23分42秒）以内で、約 42 分の線の内 | 未測定。手元の全件の `verify` は 29m 48s（`it_forks` 指定なしで、CI の基準とは別物） | 期待する証拠: `develop` を依頼者が push した後の CI の実行の `gh run view --json attempt,jobs` の検査の job の時間と、再実行の有無（`project.md` の Corrections の「CI の結果の記録」のとおり） | ci-pipeline | Unverified |
| U1-NFR5.2 | u1 NFR5.2・u3 NFR5.1 | PostgreSQL の結合テストが、通常の `verify`・`verifyQuick`・CI の検査の時間に加わらない | 加わらない（全件の `verify` の結合テストにタグ付きのクラスが入っていない。u3 の構造のテスト 6 件は単体で 1 秒未満の見込み） | `test-results.md` | build-and-test | Met |
| U1-NFR5.4 | u1 NFR5.4・u3 NFR5.2 | 全件の `verify` の時間と `:backend:internalDbPostgresTest` の時間を測って記録する | 全件の `verify` 29m 48s。`internalDbPostgresTest` は約 1 分 32 秒（U3 の承認の確かめの実行）、31 クラス・76 件 | `test-results.md` | build-and-test | Met |
| U1-NFR5.3 | u1 NFR5.3・u2 NFR5.1〜5.3 | この Intent が通常の `verify` に足したテストの時間の増分が、基準の 1.1 倍の中に収まる | 手元の全件は 29m 48s で、Intent 開始前の同じ条件（`it_forks` 指定なし）の値を持たず、増分は分けられない | 期待する証拠: CI の検査の job の時間（U1-NFR5.1 と同じ実行）。基準の中央値 21分32秒と並べる | ci-pipeline | Unverified |
| U3-NFR5.3 | u3 NFR5.3 | 共有のアプリを JVM ごとに 1 回起動し、起動の時間を U1 の基準と並べて記録する | 共有アプリの起動 6,455 ms（`INTERNAL_DB_PG_SHARED_APP`）。U1 の基準の 1,560 ms は別の対象（ヘルスまで）で、並べて記録した（合否には使わない） | `test-results.md` | build-and-test | Met |
| TC-COVERAGE-FLOOR | team.md Testing Posture・requirements.md NFR6 | バックエンド全体とすべてのパッケージで行 80%・分岐 70% 以上。除外を増やさない | 全体 行 97.7%・分岐 91.5%。72 パッケージに下限を下回るものなし（最小の分岐は `config` の 76.7%）。除外の追加なし | 全件の `verify` の JaCoCo の判定（`BUILD SUCCESSFUL`）。`test-results.md` | build-and-test | Met |
| U1-NFR2 | u1 security-requirements.md NFR2.1〜2.6・2.8・requirements.md NFR2 | 内部DB の資格情報（利用者・パスワード・資格情報つき URL）と、違反の文に入る値を、ログ・監査ログ・エラー応答に含めない。例外に cause を持たせない。TRACE でも出ない | 通過 | `InternalDbSecretLeakIT`・`InternalDbStartupFailureIT`・`StartupFailureReporterTest`・`PgSecretLeakIT`・Gitleaks（`no leaks found`）。`InternalDbBoundaryArchitectureTest` で部品が層の外にある | build-and-test | Met |
| REQ-NFR2-HOST | requirements.md NFR2・`project.md` の Forbidden（内部DB の接続先） | 起動のログに内部DB の接続先（ホスト・ポート・データベース名）を含めない | 成功する起動でも、2 か所の INFO に資格情報を含まない接続先が出る: Flyway の INFO（`org.flywaydb.core.FlywayExecutor`）の `jdbc:postgresql://<host>:<port>/<db>` と、Hibernate の INFO（`org.hibernate.orm.connections.pooling`、HHH10001005）。H2 のときも同じロガーでファイルのパスが出る。利用者名・パスワードは出ない | u1 `code-summary.md`「依頼者に確かめたいこと」1、`README.md`・`docs/internal-db.md`。依頼者の決定で既知の制約として受け入れた（2026-11-28）。README・docs/internal-db.md に書いてある。project.md の Forbidden の対象（資格情報を含む URL）には当たらない | build-and-test | Not Met |
| U1-NFR2.7 | u1 NFR2.7 | 動作中の接続の失敗は今のまま（ドライバーのログは OFF）にし、ログにホスト・ポート・利用者名が出うることを README の既知の制約に書く | 変更なし。README に書いてある | `README.md`・`docs/internal-db.md` | build-and-test | Met |
| U2-U3-NFR2 | u2 security-requirements.md NFR2.1〜2.6・u3 NFR2.1〜2.5 | 書き込みの手順とテストが、値・例外の文をログ・出力に出さない。資格情報は実行ごとの乱数。SQL に値を連結しない | 通過。Gitleaks・SpotBugs（`SQL_` 系を含む）に新しい指摘なし | `PreferencesSecretLeakIT`・`PgSecretLeakIT`・`KeyedUpsertExecutorTest`、`verify` の Gitleaks・SpotBugs | build-and-test | Met |
| U4-NFR2.1〜2.5 | u4 security-requirements.md NFR2.1〜2.5 | compose・文書に資格情報の値が無い。内部DB 用のポートを開けない。別サービス・別ボリューム・別資格情報。文書の接続先の例に資格情報が無い。複写はリポジトリの外 | 通過（追加行に `PGPASSWORD`・パスワードの直書きは 0 件。Gitleaks は `no leaks found`） | u4 `code-summary.md` の Step 8。`verify` の Gitleaks | build-and-test | Met |
| U4-NFR2.6-LIMIT | u4 NFR2.6 | `compose.yaml` にメモリの上限（`mem_limit`）があり、colima の VM の中で動かせる | `mem_limit: 512m` あり | `compose.yaml`（`git diff` で確認） | build-and-test | Met |
| U4-NFR2.6-ACTUAL | u4 NFR2.6・u4 code-summary.md「後の単位・段へ渡すこと」 | 内部DB 用のコンテナの実メモリ（`docker stats`）が 512m の内に収まる | 未測定 | 期待する証拠: Deployment Execution の記録（使い捨ての PostgreSQL の内部DB を相手にアプリを起動し、`docker stats` を 1 回記録） | deployment-execution | Unverified |
| U1-NFR4 | u1 reliability-requirements.md NFR4.1〜4.6・4.8〜4.10 | `lock_timeout` 2 秒の設定、移行が握られても有限の時間で `MIGRATION_FAILED`、起動の失敗が有限の時間、種類の INFO ログ、PostgreSQL のテストの飛ばし方 | 通過。握られた移行は 2,040〜2,047 ms で `MIGRATION_FAILED`（`sqlState=55P03`）。起動の失敗はプロセスが異常終了 | PostgreSQL の結合テスト（`InternalDbPostgresMigrationIT`・`InternalDbPostgresMigrationFailureIT`・`InternalDbPostgresHealthIT`）、`InternalDbStartupFailureIT`、`InternalDbPostgresRuntimeCheck` の単体テスト。握られた移行の実測値は u1 `code-summary.md` | build-and-test | Met |
| U1-NFR4.11 | u1 observability-requirements.md NFR4.11 | PostgreSQL のときも `hikaricp_connections_*`（プール `mastersmith-db`）が同じ名前で出る。`docker/monitoring/` に差分が無い | 通過。`docker/monitoring/` の差分なし | PostgreSQL の結合テストの `hikaricp.connections{pool=mastersmith-db}` の確認、`git diff --stat` | build-and-test | Met |
| U2-NFR4 | u2 reliability-requirements.md NFR4.1〜4.7 | 同時の書き込みは後勝ち、重なりの後にトランザクションを続けられる、上限切れは収束させず失敗、更新 0 行は想定外、`55P03` を分類に足しても H2 の分類が変わらない、移行を足さない | 通過（H2 側）。PostgreSQL 側は U3 が確かめ、通過 | H2 の結合テスト全件、`RowLockFailuresTest`、PostgreSQL の `PgKeyConflictIT`・`PgLockFailureClassificationIT` | build-and-test | Met |
| U3-NFR4 | u3 reliability-requirements.md NFR4.2〜4.6 | 待ち合わせが観測に基づき（実時間の `sleep` に頼らない）、保持側を必ず戻し、クラスごとの専用のアプリで待ちの設定を分け、クラスを単独で流しても結果が同じで、飛ばし方が U1 のまま | 通過 | `PgTestBoundaryArchitectureTest`・`ConflictProbe` の単体テストが `verify` で通過、PostgreSQL の結合テスト 76 件。単独の実行は U3 の承認の確かめの記録 | build-and-test | Met |
| NFR7-1INSTANCE | requirements.md NFR7・u1 NFR7.1〜7.5・u2 NFR7.1〜7.4・u3 NFR7.1〜7.2 | アプリのインスタンスは 1 つの前提。接続プールの上限・H2 の運用の道具を変えない。大きなバイナリが 10MB まで扱える | 変更なし。文書に前提を書いた。10MB の往復が通過 | `docs/internal-db.md`・`README.md`、`PgBinaryRoundTripIT`、`application.yaml` の差分確認は U1 の記録 | build-and-test | Met |
| REQ-NFR8-DEP | requirements.md NFR8・u1 NFR8 | 足す依存を lockfile で固定し、OSV-Scanner の対象に入れる。推移依存で既存の部品の版を上げない | `flyway-database-postgresql` 12.4.0 だけが増え、既存の版は上がっていない。`verify` の OSV-Scanner が通過 | u1 `code-summary.md`「NFR8 の記録」（`:backend:dependencies` の前後の比較）、`verify` の OSV-Scanner | build-and-test | Met |
| REQ-NFR8-LICENSE | requirements.md NFR8・u1 `code-summary.md`「依頼者に確かめたいこと」2 | `flyway-database-postgresql` のライセンス（Apache License 2.0 と想定）を採用の前に確かめる | 確かめた。`flyway-database-postgresql` 12.4.0 の POM の親 `flyway-parent` 12.4.0 の `<licenses>` が「Apache License, Version 2.0」（Gradle のキャッシュの POM を読み取りで確認。この部品は同じ親を持つ）。`flyway-core` も同じ親 | Gradle のキャッシュの `org.flywaydb/flyway-parent/12.4.0` の POM の `<licenses>`（2026-11-28 に読み取り）。採用の理由と記録は `docs/` ではなくこの記録と U1 の設計の記録 | build-and-test | Met |
| REQ-NFR9 | requirements.md NFR9・team.md Code Style | 既存の ArchUnit の境界テストを緩めず、DB の種類ごとの差が repository と移行の置き場に閉じる | 通過。`ArchitectureTest`・各機能の境界テスト・`InternalDbBoundaryArchitectureTest`・`PgTestBoundaryArchitectureTest` が `verify` で通過 | `test-results.md`（単体テスト 3,079 件が通過） | build-and-test | Met |
| TC-STATIC-CHECKS | team.md Way of Working・project.md Mandated | フォーマット・リンタ・ライセンスヘッダー・型検査・Gitleaks・OSV-Scanner（High 以上）・SpotBugs が通る | 通過。除外・例外の追加なし | 全件の `verify`（`BUILD SUCCESSFUL in 29m 48s`） | build-and-test | Met |
| TC-NO-SILENT-SKIP | team.md Way of Working（コンテナの実行環境）・FR5.3 | コンテナで動かすテストを黙って飛ばさない。飛ばした状態で統合しない | 結合テストのスキップ 10 件は種類違いの条件付きだけ。コンテナの警告は無し | `test-results.md`（colima の 2 変数を渡して流した） | build-and-test | Met |
| TC-E2E | team.md Testing Posture・Way of Working | E2E の全件が通る（実ブラウザ） | 298 件通過、失敗 0（実ブラウザ Chromium、Playwright）。初回は環境の前提（見本の PostgreSQL のポート）の不足で失敗し、整えた後の 1 回で通った | `./gradlew e2eTest` が `BUILD SUCCESSFUL in 16m 41s`、1 worker | build-and-test | Met |
| TC-FLAKY | team.md Testing Posture（不安定なテストと CI の失敗） | 手元で再現した不安定なテストがない。再実行で通したものを黙って進めない | この段の実行（全件の `verify`・E2E）で再実行して通したものは無い。CI は未実行 | `test-results.md`。CI の再実行の有無は U1-NFR5.1 の実行で確かめる | build-and-test（CI 分は ci-pipeline） | Met |
| TC-CI-MANUAL | requirements.md FR5.2・u1 NFR5.2・`ci.yml` の入力 `internal_db_postgres` | CI の手動の実行で `internal_db_postgres=true` を指定したとき、PostgreSQL の結合テストが流れて通る | 未実行。`ci.yml` に入力と `if: inputs.internal_db_postgres == true` の段がある（読み取り） | 期待する証拠: 依頼者が `develop` を push した後の手動の実行（`gh workflow run ci.yml --ref develop -f internal_db_postgres=true`）の結果。時間の記録も兼ねる | ci-pipeline | Unverified |
| TC-DEPLOY-PG | requirements.md FR4・team.md Deployment・u4 `code-summary.md` | アプリを PostgreSQL の内部DB に接続して、起動確認・スモークテストが通り、バックアップと戻しを 1 回練習して移行の履歴まで同じに戻る | 未実施。手順は使い捨ての `postgres` コンテナで `pg_dump`・`pg_restore` の往復を確かめたが、アプリは接続していない | 期待する証拠: Deployment Execution の記録（使い捨ての PostgreSQL の内部DB を相手に、ヘルスチェック・スモークテスト・バックアップと戻しの練習） | deployment-execution | Unverified |
| TC-WAR-NEG | u1 `code-summary.md`「流していないもの」 | `verifyPostgresMigrationsInWar` が、PostgreSQL 用の移行のファイルを欠けさせたときに失敗する | 正の確認（通る場合）だけ。欠けさせた失敗の確認は流していない | 期待する証拠: ファイルを欠けさせた使い捨ての作業での実行の記録。小さな追加の確かめで、依頼者の判断で持ち越してよい | build-and-test の承認の場（依頼者の判断） | Unverified |
| U2-NFR1.4 | u2 performance-requirements.md NFR1.4 | PostgreSQL のときの書き込みの所要時間は U2 では測らず、測っていないことをこの記録に書く（基準の値は U1 の起動の時間だけが持つ） | 測っていない。この記録に書いた（`REQ-NFR1-PG` の行と `test-results.md`） | `build-and-test-summary.md`・`test-results.md` | build-and-test | Met |
| U2-NFR4.9 | u2 reliability-requirements.md NFR4.9 | 書き込みの手順の変更が、作業ロールの保存の監査・DSL 管理の監査の内容・タイミング・件数・種類を変えない | 通過 | 既存の監査の結合テスト（`AuditAuthenticationEventsIT`・DSL 管理の監査のテストなど）が結合テスト 1,732 件の中で通過 | build-and-test | Met |

Verdict の集計（行の数）: `Met` 42・`Not Met` 1・`Unverified` 6・`N/A` 1（合計 50 行）。

## 判定（失敗の判定）

Build and Test の失敗の判定の定義（いずれかの目標が `Not Met` または `Unverified`）に当たります。流したコマンドはすべて終了コード 0 で、コードの失敗は 1 件もありません。`Not Met` は上の `REQ-NFR2-HOST`（Flyway の INFO に接続先が出る点）の 1 件、`Unverified` は後の段・push 後・承認の場の決定に属する 6 件（`U1-NFR5.1`・`U1-NFR5.3`・`TC-CI-MANUAL` は ci-pipeline、`U4-NFR2.6-ACTUAL`・`TC-DEPLOY-PG` は deployment-execution、`TC-WAR-NEG` は承認の場で持ち越しを受け入れる）です。`Not Met` の `REQ-NFR2-HOST` は、依頼者の決定（2026-11-28）で既知の制約として受け入れました（Verdict は事実のまま `Not Met`）。ふつうの失敗への対応（in-stage の直し・loop-back）の対象となる実装の不具合は、この段では見つかっていません。

## 準備の状態

| 評価 | 状態 |
|---|---|
| build-ready | 準備できている（全件の `verify` が成功） |
| test-ready | 準備できている。H2 の結合テスト全件・PostgreSQL の結合テスト 76 件・E2E 298 件が通過 |
| deployment-ready | まだ。CI の時間と手動の実行、アプリを PostgreSQL に接続した確かめが残る |

## 既知の制約と残っている項目

1. **起動のログに接続先が出る（受け入れ済み）**: Flyway の INFO（`org.flywaydb.core.FlywayExecutor`）と Hibernate の INFO（`org.hibernate.orm.connections.pooling`、HHH10001005）の 2 か所に、資格情報を含まない接続先（ホスト・ポート・データベース名。H2 ではファイルのパス）が出る。依頼者の決定（2026-11-28）で既知の制約として受け入れた（直さない）。README・`docs/internal-db.md` に書いてある（`REQ-NFR2-HOST`）。
2. **検索の Unicode の端の 3 つの差**（`İ`・語末のシグマ・`ǅ`）: H2 と PostgreSQL で結果が分かれる。文書に書いた。
3. **アプリの利用者が PostgreSQL の所有者の 1 アカウント**（スーパーユーザー）: 手元の単一インスタンスの前提。`docs/internal-db.md` に書いた。
4. **構造のテストで起こせなかった 2 点**: 名前が `…IT` で終わらないクラスの検出、`org.h2` の型への依存の検出。
5. **u3 の `PgSharedAppSmokeIT`** は計画外の追加（部品の動作確認。回帰の目印）。
6. **u1 の審査の Minor R-01〜R-08**（各単位の `code-generation/reviews/review-01.md` と `code-summary.md`）。
7. **u4 の審査の Minor 2 件**: 指揮役が直した（`docs/verification.md` の件数の固定、`docs/internal-db.md` のバックアップ手順の注記）。
8. **cross-unit-traceability の判定は fail**（`cross-unit-traceability.md`）。u3 の `traceability.json` は直され、NFR3・NFR6 は満たされた。残る NFR1・NFR2・NFR4・NFR5 は、対象が「測っていない・受け入れ・CI の時間」で、ファイルを持たない枝番が `not_built` になっているため。持ち主と Matrix の行は同じ記録に書いた。
9. **`.env.example`**: 内部DB 用の 3 項目はコメントのまま。アプリのコンテナは `.env` を丸ごと読むため、3 変数がアプリのコンテナの環境にも入る（u4 が依頼者に確かめたい点）。

## 次の段へ引き継ぐこと（次の配備へ引き継ぐこと）

- CI Pipeline: `develop` の push 後の CI の検査の job の時間（基準 21分32秒の 1.1 倍・約 42 分の線）と再実行の有無、手動の実行 `internal_db_postgres=true`（`U1-NFR5.1`・`U1-NFR5.3`・`TC-CI-MANUAL`）。`docs/` に書いた CI の入力名は `internal_db_postgres`（U1 の `cicd-pipeline.md` の `internal_db_postgresql` は誤記）。
- Deployment Execution: 使い捨ての PostgreSQL の内部DB を相手にした起動確認・スモークテスト・バックアップと戻しの練習（Flyway の履歴まで同じに戻ることをアプリを起動して確かめる）と、内部DB 用コンテナの実メモリ（`U4-NFR2.6-ACTUAL`・`TC-DEPLOY-PG`）。

## 承認の場の決定（依頼者、2026-11-28）

1. `REQ-NFR2-HOST`（起動のログに接続先が出る）: 既知の制約として受け入れる（直さない）。出るのは Flyway の INFO と Hibernate の INFO（HHH10001005）の 2 か所。Verdict は事実のまま `Not Met`。
2. u3 の `code-generation/traceability.json`: 対象欄を実在するファイル 1 つに直し、NFR2.6・NFR3.1・NFR5.4・NFR6.1・NFR6.2・NFR8.1・NFR9.1〜9.3 の行を足した（指揮役が実施）。
3. `TC-WAR-NEG`: 持ち越す（`Unverified` のまま。持ち主は build-and-test の承認の場で受け入れる）。
4. `REQ-NFR8-LICENSE`: `flyway-parent` 12.4.0 の POM が Apache License 2.0 のため `Met`（指揮役が更新）。u4 の審査 Minor 2 件は指揮役が直した。

## 依頼者に確かめたい点

1. `REQ-FR5.4` の「件数が減らない」は、Intent 開始前の件数の記録が無いため、Intent 内の比較（U1 の時点の 1,722 件から今 1,732 件）で `Met` にした。この読み方でよいか。
2. `Not Met`（1 件、受け入れ済み）と `Unverified`（6 件）が残る状態で承認の場へ進んでよいか。`Unverified` の持ち主は ci-pipeline（3 件）・deployment-execution（2 件）・承認の場の持ち越し（1 件）で、push 後・配備の段で埋まる。
