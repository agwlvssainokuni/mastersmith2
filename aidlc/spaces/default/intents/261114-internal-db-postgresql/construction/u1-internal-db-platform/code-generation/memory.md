<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-22 Step 1 依存: `flyway-database-postgresql`（版なし、BOM 管理）を libs.versions.toml と backend/build.gradle.kts（runtimeOnly）に足し、`./gradlew resolveAndLockAll --write-locks` を実行。lockfile の差は 1 行だけ（`org.flywaydb:flyway-database-postgresql:12.4.0=productionRuntimeClasspath,runtimeClasspath,testRuntimeClasspath`）。`./gradlew :backend:dependencies --configuration runtimeClasspath` の前後の差は、この部品と flyway-core の並びの入れ替えだけで、既存の部品の版は上がっていない（flyway-core 12.4.0 のまま）。
- 2026-11-22 Step 1 ライセンス: flyway-database-postgresql の POM と jar にはライセンスの記載が無い（`<licenses>` なし、jar に LICENSE なし）。上流の Flyway Community は Apache License 2.0 で flyway-core と同じ系統の部品だが、POM では確認できていないため、code-summary.md では「POM 上は記載なし、上流の公開（Apache License 2.0）に基づく」と事実のまま書き、依頼者に確かめてもらう点にする。README のライセンス表に flyway-core の行は無い。
- 2026-11-22 Step 1 実行の入口: `./gradlew :backend:test --tests 'cherry.mastersmith.internaldb.*'` はタスクが解決し、テストがまだ無いため「No tests found」で失敗する（想定どおり）。
- 2026-11-22 Step 1 既存テストの影響の洗い出し（`spring.datasource`・`FlywayMigrationStrategy`・`HikariDataSource`・`DataSourceProperties` を含むファイル）: テスト側は DslTargetDbIT・DslStartupIT・BusinessDataTestApp・Target* の config/service のテスト群・AccessSecretLeakIT・H2DefragOnCloseTest・HikariJmxSettingsTest・SecurityExtensionIT・H2CompactionByPoolSuspensionIT・DataSourcePoolIT・AuthSuspensionSecretLeakIT・AuthSecretLeakIT・SigningKeyStartupIT・AuthSettingsIT・MailTestApplication・MailConfigurationIT・ConnectionAcquireCounter・AppearanceStartupIT・InitialAdmin*IT・InitialAdminSecretLeakIT・TestDatabase・TestHikariMbeansEnvironmentPostProcessor・DatabasePersistenceIT・ProblemTypeDuplicateStartupIT・FilterExceptionErrorLogIT・InvitationApps・InvitationSendConnectionIT。main 側は targetdb の TargetWriteDataSourceConfig・TargetDataSourceConfig・JdbcTargetReadAccess（対象DB の接続。`dataSource` の名前の Bean ではない）。これらが新しい部品の入った起動で変わらず通ることは Step 11 の全件で確かめる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-22 Step 4 分類の順: 計画の本文の並び（55P03 を移行の文の失敗より先）は、「対象の表を握られた場合は版の番号つき」という同じ計画の表の決めと食い違うため、表の決めを優先し、移行の文の失敗（版の番号つき）→ 55P03（版の番号なし）の順にした。
- 2026-11-22 Step 4 版の番号の取り出し: Flyway 12.4.0 の移行の文の失敗の例外の文は `Failed to execute script V3__broken.sql` で、計画の想定の `Migration V...` ではないため、ファイル名の `V<数字>__` から数字だけを取り出す形にした（数字以外は出さない）。
- 2026-11-22 Step 2 移行: PostgreSQL 用のファイルの先頭の注（`--` 行）に、書き換えの理由の行を足した（V3・V5・V8・V10〜V12。V5 は H2 の型名を書いていた行を言い換えた）。文（コメントを除く）の差は計画の列・制約だけであることを diff で確かめた。
- 2026-11-22 Step 5: 起動の失敗のテストで数える ERROR は、メッセージが `Internal DB startup failed` のものに限る（Spring Boot は起動の失敗の後に自分でも ERROR を出しうるため、全 ERROR の件数では数えない。実測はしていない）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
