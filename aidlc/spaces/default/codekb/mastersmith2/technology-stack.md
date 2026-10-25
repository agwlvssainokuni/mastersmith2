# 技術の構成（mastersmith2）

> 2026-10-25（記録のコミット `bc484c9`）の Full rescan・深さ Minimal で作り直した。版は `gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`Dockerfile`（アーキテクトが読み直した）の値。所見 K-66（固定している部品の現状）の本文はこの文書に書いた。

## 言語と実行環境

| 項目 | 版・値 | 出どころ |
|---|---|---|
| Java | 25 | `libs.versions.toml` の `java` |
| 実行のイメージ | `eclipse-temurin:25.0.4.1_1-jre-noble`（ダイジェストで固定） | `Dockerfile` 17 行 |
| JVM の設定 | `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1` と `MASTERSMITH_JAVA_OPTIONS` | `Dockerfile` 52 行 |
| TypeScript | ^6.0.3 | `frontend/package.json` |

## バックエンド

| 部品 | 版 | 用途 |
|---|---|---|
| Spring Boot | **4.1.1** | アプリの土台（lockfile の `spring-boot-starter:4.1.1`） |
| Tomcat（組み込み） | 11.0.26（`tomcat` で上書き） | Servlet の入れ物 |
| Jackson | 3.1.7（`jackson-bom` を platform で上書き） | JSON（lockfile の `jackson-core`・`jackson-databind` は 3.1.7） |
| Hibernate | 7.4.5.Final | JPA |
| H2 | 2.4.240 | 内部DB（組み込み） |
| Flyway | 12.4.0 | 内部DB の移行（V1〜V13） |
| HikariCP | 7.0.2 | 接続のプール（内部DB と対象DB の読み取り・書き込み） |
| mysql-connector-j・mariadb-java-client・postgresql | 9.7.0・3.5.10・42.7.13 | 対象DB の JDBC ドライバー |
| SnakeYAML | 2.7（固定） | DSL の YAML |
| networknt json-schema-validator | 3.0.6（固定） | DSL の JSON Schema の検証 |
| cherry-mustache（java-mustache-processor） | 0.1.0 | メールのテンプレート |
| logstash-logback-encoder | 9.0 | 構造化ログ |
| opentelemetry-logback-appender-1.0 | 2.28.1-alpha（固定） | ログの外部エクスポート（既定で無効）。lockfile の `opentelemetry-sdk` は 1.62.0、`opentelemetry-instrumentation-api` は 2.28.1 |

## 画面

| 部品 | 版 | 用途 |
|---|---|---|
| React | ^19.2.8 | 画面 |
| react-router | ^8.3.0 | 振り分け |
| i18next / react-i18next | ^26.4.2 / ^17.0.15 | 日英の文言 |
| Vite | ^8.3.4 | ビルドと開発のサーバー |
| make-you-chic-ui | サブモジュール `ecb07e9` | デザインシステム |

## テスト

| 部品 | 版 | 用途 |
|---|---|---|
| JUnit 5・Spring Boot Test・Testcontainers | Spring Boot の管理の版（Testcontainers 2.0.5） | Java の単体と結合（対象DB の3種類はコンテナ） |
| jqwik・ArchUnit | 1.10.2・1.5.1 | 性質ベースのテスト・層と境界の検査 |
| subethasmtp | 7.2.2 | テストの中の SMTP の受け手 |
| Vitest・`@vitest/coverage-v8` | ^5.0.4 | 画面の単体テストとカバレッジ（thresholds） |
| Testing Library・user-event・fast-check | ^16.3.3・^14.6.7・^4.10.2 | 画面のテスト |
| axe-core | ^4.14.0 | アクセシビリティの検査（B7 で 4.14.0 に上げ済み。Dependabot の見送りは外れている） |
| `@playwright/test` | ^1.63.0 | E2E（`./gradlew e2eTest`、`verify` と CI の外） |
| k6 | 手元の道具 | 負荷の試験（`perf/`） |

## 検査とビルドの道具

| 道具 | 版 | 用途 |
|---|---|---|
| Gradle（Kotlin DSL） | ラッパーの版は今回読んでいない | `./gradlew verify` の入口 |
| Spotless・palantir-java-format | 8.10.4・2.98.0 | Java の書式とライセンスヘッダー |
| SpotBugs（プラグイン・道具）・FindSecBugs | 6.5.12・4.10.4・1.14.0 | 静的解析 |
| JaCoCo | 0.8.15 | カバレッジ（全体とパッケージごと） |
| Prettier・oxlint・ESLint・Stylelint | ^3.9.10・^1.87.0・^10.12.0・^17.16.0 | 画面の書式とリンタ |
| Gitleaks・OSV-Scanner | `build.gradle.kts` から呼ぶ | 秘密情報の検出・依存の脆弱性検査 |
| GitHub Actions | `.github/workflows/ci.yml`（actions はコミットの SHA で固定） | `develop` への push で `./gradlew verify` |

## K-66 固定している部品の現状（Spring Boot 4.1.1 → 4.1.2 の見直しの対象）

次の4つは Spring Boot の管理の版から外して固定・上書きしている。`libs.versions.toml` と `dependabot.yml` のコメントが、どれも「外す時期: Spring Boot を上げるとき」としている。

| 部品 | 今の固定 | 固定の理由（コメントによる） | 4.1.2 で見直す点（未検証） |
|---|---|---|---|
| Tomcat | 11.0.26 | Spring Boot 4.1.1 が管理する 11.0.24 に High 以上の脆弱性（GHSA-9xv2-5v5q-p794 ほか、11.0.25 で修正） | 4.1.2 の管理の版が 11.0.26 以上なら上書きは不要 |
| Jackson | 3.1.7（BOM の上書き） | 4.1.1 が管理する 3.1.5 に High の脆弱性（3.1.6 で修正）、3.1.6 にも High（3.1.7 で修正） | 4.1.2 の管理の版が 3.1.7 以上なら上書きと `dependabot.yml` の Jackson の `ignore` は不要 |
| opentelemetry-logback-appender | 2.28.1-alpha | 4.1.1 の OpenTelemetry 1.62 と、より新しい版が食い違い、外部エクスポートの有効時に失敗（2.29 以降は api 1.63 以上を求める） | 4.1.2 の OpenTelemetry が 1.63 以上なら appender も上げられる。外部エクスポートを有効にして確かめる必要がある（`./gradlew verify` では現れない） |
| networknt json-schema-validator | 3.0.6 | 3.0.7 以降は Jackson を 3.2 系に引き上げるため | 管理の Jackson が 3.2 系以上になるまで据え置き（4.1.2 の Jackson が 3.1 系ならそのまま） |

- 4.1.2 の BOM・release notes・OpenTelemetry の版はこのリポジトリにもキャッシュにも無く、読んでいない。上げた後に `./gradlew dependencies` と `backend/gradle.lockfile` の差で確かめる。
- ほかの Spring Boot が管理する部品（Hibernate・HikariCP・Flyway・JDBC ドライバー・H2 など）の版は、4.1.2 の BOM で動く見込み（未検証）。H2 を上げるときは `-Dh2.compactThreads=1` の要否を見直す（`project.md` の Tech Stack）。

## 手元の運用

- 監視は `grafana/otel-lgtm` と OpenTelemetry Collector を compose の profile で起動し、警報とダッシュボードは `docker/monitoring/` のファイルで持つ。メールの受け手（Mailpit）と見本の対象DB も compose の profile（前回の記録）。

## Sources

- `gradle/libs.versions.toml`・`backend/gradle.lockfile`・`backend/build.gradle.kts`（Jackson の BOM）・`frontend/package.json`・`Dockerfile`・`.github/dependabot.yml`。
