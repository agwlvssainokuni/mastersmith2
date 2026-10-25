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
