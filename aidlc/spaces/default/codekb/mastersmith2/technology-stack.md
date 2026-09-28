# 技術の構成（mastersmith2）

版は lockfile（`backend/gradle.lockfile`・`frontend/package-lock.json`）と `gradle/libs.versions.toml`・`gradle/wrapper/gradle-wrapper.properties`・`Dockerfile`・`compose.yaml`・`.github/workflows/ci.yml` から `grep` で抜いた値である（2026-09-29、コミット `e68f54d`）。画面の版の一部は `package.json` の範囲（`^`）で、lockfile の実際の値は流し読みで確かめていない。更新の知らせ（Dependabot）と固定の決まりがぶつかる部品は K-7（`dependencies.md`）。

## 言語と実行環境

| 分類 | 技術 | 版 |
|---|---|---|
| バックエンドの言語 | Java（Temurin） | 25（`libs.versions.toml` の `java`、CI も JDK 25） |
| 画面の言語 | TypeScript | 6.0.3 |
| 画面の実行・ビルド | Node.js（CI） | 24 |
| ビルド | Gradle（Kotlin DSL）＋ npm | Gradle 9.7.1 |
| 実行のイメージ | `eclipse-temurin` | `25.0.4_7-jre-noble`（`Dockerfile`） |

## バックエンド

| 分類 | 部品 | 版 | 備考 |
|---|---|---|---|
| 土台 | Spring Boot（MVC・Security・OAuth2 Resource Server・Actuator・Data JPA・Flyway・Validation・AspectJ・OpenTelemetry） | 4.1.1 | |
| 観測 | Micrometer（core・observation・registry-otlp） | 1.17.1 | 指標の分布の設定は無い（K-6） |
| 観測 | OpenTelemetry API ／ opentelemetry-logback-appender | 1.62.0 ／ 2.28.1-alpha | appender は `project.md` の Tech Stack で固定 |
| 内部DB | H2 ／ HikariCP ／ Flyway | 2.4.240 ／ 7.0.2 ／ 12.4.0 | `-Dh2.compactThreads=1` を Dockerfile・テストの JVM・E2E でそろえる（`project.md` の Tech Stack） |
| 対象DB のドライバー | mysql-connector-j ／ mariadb-java-client ／ postgresql | 9.7.0 ／ 3.5.10 ／ 42.7.13 | |
| DSL | SnakeYAML ／ networknt json-schema-validator | 2.6 ／ 3.0.6 | networknt は Jackson を引き上げないため 3.0.6（`project.md` の Corrections） |
| メール | jakarta.mail-api ／ angus-mail ／ cherry-mustache-core（`vendor/java-mustache-processor`） | 2.1.5 ／ 2.0.5 ／ 0.1.0 | |
| ログ | logstash-logback-encoder | 9.0 | |

## バックエンドのテストと検査

| 部品 | 版 |
|---|---|
| JUnit Jupiter ／ Awaitility ／ jqwik ／ ArchUnit | 6.0.3 ／ 4.3.0 ／ 1.10.1 ／ 1.5.0 |
| Testcontainers | 2.0.5 |
| SubEthaSMTP（JVM の中のテスト用の SMTP の受け手） | 7.2.2 |
| JaCoCo | 0.8.15 |
| Spotless ／ palantir-java-format | 8.10.2 ／ 2.98.0 |
| SpotBugs ／ FindSecBugs | 4.10.4 ／ 1.14.0 |

## 画面

| 分類 | 部品 | 版 |
|---|---|---|
| 土台 | React ／ react-router ／ i18next | 19.3.0 ／ ^8.3.0 ／ ^26.4.2 |
| デザインシステム | make-you-chic-ui（`vendor/make-you-chic-ui`、`file:` の依存） | 固定先 `735ef04`（版の文字列は `0.0.0`、K-1） |
| ビルド | Vite | 8.3.0 |
| テスト | Vitest ／ `@vitest/coverage-v8` ／ jsdom ／ user-event ／ vitest-axe ／ fast-check | 4.1.11 ／ ^4.1.11 ／ 30.1.1 ／ 14.6.7 ／ — ／ — |
| E2E | `@playwright/test` ／ axe-core | 1.63.0 ／ 4.13.0 |
| 検査 | Prettier ／ oxlint ／ ESLint ／ Stylelint | 3.9.8 ／ — ／ — ／ — |

「—」は今回版を抜いていないもの。

## 検査の外部の道具とコンテナのイメージ

| 分類 | 部品 | 版 |
|---|---|---|
| 秘密情報の検出 | Gitleaks | 8.30.1（`ci.yml` で版と SHA-256 を固定） |
| 依存の脆弱性 | OSV-Scanner | 2.6.0（同上） |
| 手元の監視 | `grafana/otel-lgtm` ／ `otel/opentelemetry-collector` | 0.33.1 ／ 0.161.0 |
| メールの受け手 | `axllent/mailpit` | v1.31.2 |
| 見本の対象DB（テストは `TargetDbImages` の digest と同じ） | `mysql` ／ `mariadb` ／ `postgres` | 8.4.11 ／ 11.8.9 ／ 18.6 |

コンテナのイメージは `compose.yaml` でいずれも digest 付きで固定している。
