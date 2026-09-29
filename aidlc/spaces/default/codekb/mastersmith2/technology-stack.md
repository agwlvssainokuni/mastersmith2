# 技術の構成（mastersmith2）

版は lockfile（`backend/gradle.lockfile`・`frontend/package-lock.json`）と `gradle/libs.versions.toml`・`gradle/wrapper/gradle-wrapper.properties`・`Dockerfile`・`compose.yaml`・`.github/workflows/ci.yml` から抜いた値である（2026-09-29、コミット `c1ed553`）。前回（`e68f54d`）からの変化は備考に書いた。更新の知らせ（Dependabot）と ignore の扱いは K-14（`dependencies.md`）。

## 言語と実行環境

| 分類 | 技術 | 版 | 備考 |
|---|---|---|---|
| バックエンドの言語 | Java（Temurin） | 25（`libs.versions.toml` の `java`、CI の `java-version` も 25） | |
| 画面の言語 | TypeScript | 6.0.3（`^6.0.3`） | 7.0.2 の知らせが開いている（K-14）。`config/npm-build-tools.txt` の対象 |
| 画面の実行・ビルド | Node.js | 24（CI の `node-version`、`package.json` の `engines.node` は `>=24 <25`） | 型の `@types/node` は 26 系（下） |
| ビルド | Gradle（Kotlin DSL）＋ npm | Gradle 9.8.0 | 前回 9.7.1 |
| 実行のイメージ | `eclipse-temurin` | `25.0.4_7-jre-noble`（`Dockerfile`） | 大きな版の更新は Dependabot で知らせない |

## バックエンド

| 分類 | 部品 | 版 | 備考 |
|---|---|---|---|
| 土台 | Spring Boot（MVC・Security・OAuth2 Resource Server・Actuator・Data JPA・Flyway・Validation・AspectJ・OpenTelemetry） | 4.1.1 | |
| 組み込みの Tomcat | tomcat-embed-core | 11.0.26 | `libs.versions.toml` で Spring Boot の管理の版を上書き（脆弱性のため） |
| JSON | Jackson（`tools.jackson`） | 3.1.6 | Jackson の BOM を platform で読み、Spring Boot の管理の 3.1.5 を上書き（`backend/build.gradle.kts` 61〜67 行）。3.1.7 の知らせが開いている（K-14） |
| 観測 | Micrometer（core・observation・registry-otlp） | 1.17.1 | `http.server.requests`・`mastersmith.mail.send` に `slo` の境界のバケット（K-15） |
| 観測 | OpenTelemetry API ／ opentelemetry-logback-appender | 1.62.0 ／ 2.28.1-alpha | appender は `project.md` の Tech Stack で固定、Dependabot で全版を知らせない |
| 内部DB | H2 ／ HikariCP ／ Flyway | 2.4.240 ／ 7.0.2 ／ 12.4.0 | `-Dh2.compactThreads=1` を Dockerfile・テストの JVM・E2E でそろえる（`project.md` の Tech Stack） |
| 対象DB のドライバー | mysql-connector-j ／ mariadb-java-client ／ postgresql | 9.7.0 ／ 3.5.10 ／ 42.7.13 | |
| DSL | SnakeYAML ／ networknt json-schema-validator | 2.7 ／ 3.0.6 | SnakeYAML は前回 2.6。networknt は Jackson を引き上げないため 3.0.6、Dependabot で全版を知らせない |
| メール | jakarta.mail-api ／ angus-mail ／ cherry-mustache-core（`vendor/java-mustache-processor`） | 2.1.5 ／ 2.0.5 ／ 0.1.0 | 前回の記録 |
| ログ | logstash-logback-encoder | 9.0 | |

## バックエンドのテストと検査

| 部品 | 版 | 備考 |
|---|---|---|
| JUnit Jupiter ／ Awaitility ／ jqwik ／ ArchUnit | 6.0.3 ／ 4.3.0 ／ 1.10.1 ／ 1.5.1 | ArchUnit は前回 1.5.0。Awaitility は前回の記録 |
| Testcontainers | 2.0.5 | |
| SubEthaSMTP（JVM の中のテスト用の SMTP の受け手） | 7.2.2 | |
| JaCoCo | 0.8.15 | |
| Spotless（Gradle のプラグイン）／ palantir-java-format | 8.10.2 ／ 2.98.0 | Spotless は `libs.versions.toml` 29 行の1行で決まり、lockfile に載らない。8.10.3 の知らせが開いている（K-14） |
| SpotBugs ／ FindSecBugs | 4.10.4 ／ 1.14.0 | |

## 画面

| 分類 | 部品 | 版 | 備考 |
|---|---|---|---|
| 土台 | React ／ react-router ／ i18next | 19.3.0 ／ 8.4.0 ／ 26.4.2 | |
| デザインシステム | make-you-chic-ui（`vendor/make-you-chic-ui`、`file:` の依存） | 固定先 `310e1ec`（版の文字列は `0.0.0`） | 前回 `735ef04`。次の更新の候補 `077f5b4`（K-12） |
| ビルド | Vite | 8.3.1 | 前回 8.3.0。`config/npm-build-tools.txt` の対象 |
| テスト | Vitest ／ `@vitest/coverage-v8` ／ jsdom | 5.0.2 ／ 5.0.2 ／ 30.1.1 | Vitest は前回 4.1.11 |
| E2E | `@playwright/test` ／ axe-core | 1.63.0 ／ 4.13.0 | |
| 型 | `@types/node` | 26.6.2（`^26.2.0`、`undici-types` 8.9.0 を連れる） | 実行の Node 24 と大きな版がずれる（K-14）。26.6.3 の知らせが開いている |
| 検査 | Prettier ／ oxlint ／ ESLint ／ `@typescript-eslint/parser` ／ Stylelint | 3.9.9 ／ 1.85.0 ／ 10.11.0 ／ 8.70.1（`^8.67.0`）／ 17.15.0 | `@typescript-eslint/*` の peer は `typescript >=4.8.4 <6.1.0`（K-14） |

## 検査の外部の道具とコンテナのイメージ

| 分類 | 部品 | 版 |
|---|---|---|
| 秘密情報の検出 | Gitleaks | 8.30.1（`ci.yml` で版と SHA-256 を固定） |
| 依存の脆弱性 | OSV-Scanner | 2.6.0（同上） |
| 手元の監視 | `grafana/otel-lgtm` ／ `otel/opentelemetry-collector` | 0.34.0 ／ 0.161.0 |
| メールの受け手 | `axllent/mailpit` | v1.31.2 |
| 見本の対象DB（テストは `TargetDbImages` の digest と同じ） | `mysql` ／ `mariadb` ／ `postgres` | 26.7.0 ／ 13.0.2 ／ 18.6 |

コンテナのイメージは `compose.yaml` で digest 付きで固定している（`otel-lgtm`・`opentelemetry-collector` は版の文字列だけ）。
