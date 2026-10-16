# 技術の構成（mastersmith2）

> 2026-10-16（記録のコミット `09b795d`）の Full rescan で作り直した。版は `gradle/libs.versions.toml`（アーキテクトが読み直した）と `frontend/package.json`・`frontend/package-lock.json` の値。所見の本文はこの文書には無い。

## 言語と実行環境

| 項目 | 版・値 | 出どころ |
|---|---|---|
| Java | 25 | `gradle/libs.versions.toml` の `java` |
| 実行のイメージ | eclipse-temurin の JRE（版とダイジェストで固定） | `Dockerfile`（ENTRYPOINT の行だけ読んだ） |
| JVM の設定 | `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError`、`-Dh2.compactThreads=1` | `Dockerfile` の ENTRYPOINT |
| コンテナのメモリの上限 | `mem_limit: ${MASTERSMITH_CONTAINER_MEMORY:-2g}` | `compose.yaml` |
| TypeScript | ^6.0.3 | `frontend/package.json` |
| Node のパッケージの入れ方 | `npm ci`、`frontend/.npmrc` で `ignore-scripts=true` | `team.md` の記録（今回は `.npmrc` を開いていない） |

## バックエンド

| 部品 | 版 | 用途 |
|---|---|---|
| Spring Boot | 4.1.1 | アプリの土台（Web・Security・Data JPA・Actuator など） |
| Tomcat（組み込み） | 11.0.26（上書き） | Servlet の入れ物 |
| Jackson | 3.1.7（BOM で上書き） | JSON |
| H2 | Spring Boot の管理の版（前の記録では 2.4.240） | 内部DB（組み込み） |
| Flyway | Spring Boot の管理の版 | 内部DB の移行（V1〜V12） |
| HikariCP | Spring Boot の管理の版 | 接続のプール（内部DB と対象DB の `mastersmith-target-db`、対象DB は既定の上限 5） |
| JDBC ドライバー（MySQL・MariaDB・PostgreSQL） | Spring Boot の管理の版 | 対象DB の読み取り |
| SnakeYAML | 2.7 | DSL の YAML の読み込み |
| networknt json-schema-validator | 3.0.6 | DSL の JSON Schema の検証（3.0.7 は Jackson を 3.2 系に上げるため据え置き） |
| cherry-mustache（java-mustache-processor） | 0.1.0 | メールのテンプレート |
| logstash-logback-encoder | 9.0 | 構造化ログ（JSON） |
| opentelemetry-logback-appender | 2.28.1-alpha（固定） | ログの外部エクスポート（既定で無効） |

## 画面

| 部品 | 版 | 用途 |
|---|---|---|
| React | ^19.2.8 | 画面 |
| react-router | ^8.3.0 | 振り分け |
| i18next / react-i18next | ^26.4.2 / ^17.0.15 | 日英の文言 |
| Vite | ^8.3.4 | ビルドと開発のサーバー |
| make-you-chic-ui | サブモジュール `a9309c4` | デザインシステム（AppShell・Dropdown・Avatar・Button など） |

## テスト

| 部品 | 版 | 用途 |
|---|---|---|
| JUnit 5・AssertJ・Spring Boot Test | Spring Boot の管理の版 | Java の単体と結合 |
| Testcontainers | Spring Boot の管理の版 | 対象DB の MySQL・MariaDB・PostgreSQL の結合テスト |
| jqwik | 1.10.2 | 性質ベースのテスト（Java） |
| ArchUnit | 1.5.1 | 層と境界の検査（18 個） |
| subethasmtp | 7.2.2 | テストの中の SMTP の受け手 |
| Vitest | ^5.0.4 | 画面の単体テスト（Testing Library・user-event・vitest-axe・fast-check と） |
| @playwright/test | ^1.63.0 | E2E（`./gradlew e2eTest`、`verify` と CI の外） |
| axe-core | ^4.13.0（lockfile 4.13.0） | 実際のブラウザと画面の部品のアクセシビリティの検査。`>= 4.14.0` は見送り（K-60、本文は `dependencies.md`） |
| k6 | 手元の道具 | 負荷の試験（`perf/`） |

## 検査とビルドの道具

| 道具 | 版 | 用途 |
|---|---|---|
| Gradle（Kotlin DSL） | ラッパーの版（今回は読んでいない） | 1コマンドの検査 `./gradlew verify` の入口 |
| Spotless・palantir-java-format | 8.10.4・2.98.0 | Java の書式とライセンスヘッダー |
| SpotBugs（Gradle のプラグイン・道具）・FindSecBugs | 6.5.12・4.10.4・1.14.0 | Java の静的解析 |
| JaCoCo | 0.8.15 | カバレッジ（全体とパッケージごとの下限） |
| Prettier・oxlint・ESLint・Stylelint | `frontend/package.json` の版 | 画面の書式とリンタ |
| Gitleaks・OSV-Scanner | `build.gradle.kts` から呼ぶ（今回は開いていない） | 秘密情報の検出・依存の脆弱性検査 |
| GitHub Actions | `.github/workflows/ci.yml` | `develop` への push で `./gradlew verify` |

## 手元の運用

- 監視は `grafana/otel-lgtm` と OpenTelemetry Collector を compose の profile で起動し、警報とダッシュボードは `docker/monitoring/` のファイルで持つ（前の記録。今回は警報 `ms-forbidden` の式だけ読んだ）。
- メールの受け手（Mailpit）と見本の対象DB も compose の profile（前の記録）。

## Sources

- `gradle/libs.versions.toml`・`frontend/package.json`・`frontend/package-lock.json`（axe-core の行）、`Dockerfile`・`compose.yaml` の該当の行。
