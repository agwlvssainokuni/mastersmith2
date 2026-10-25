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
