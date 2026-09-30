# 技術の構成（mastersmith2）

版は 2026-09-30（コミット `31b980b`）の `gradle/libs.versions.toml`・`gradle/wrapper/gradle-wrapper.properties`・`frontend/package.json` から抜いた値と、開発担当が `backend/gradle.lockfile`・`frontend/package-lock.json` の検索で確かめた解決の値である。備考の「lockfile」は解決した版、「範囲」は `package.json` の指定。依存の向きと管理の決まりは `dependencies.md`。

## 言語と実行環境

| 分類 | 技術 | 版 | 備考 |
|---|---|---|---|
| バックエンドの言語 | Java（Temurin） | 25（`libs.versions.toml` の `java`） | 実行のイメージは `eclipse-temurin:25.0.4_7-jre-noble`（`Dockerfile`） |
| 画面の言語 | TypeScript | 6.0.3（範囲 `^6.0.3`） | |
| 画面の実行・ビルド | Node.js | 24 | |
| ビルド | Gradle（Kotlin DSL）＋ npm | Gradle 9.8.0（Wrapper） | 入口は `./gradlew verify` |

## バックエンド

| 分類 | 部品 | 版 | 備考 |
|---|---|---|---|
| 土台 | Spring Boot（webmvc・security・data-jpa・flyway・validation・aspectj・opentelemetry・oauth2-resource-server・mail・actuator） | 4.1.1 | Spring Framework 7.0.9・Spring Security 7.1.1（lockfile） |
| 認証 | Nimbus JOSE + JWT | 10.9.1（lockfile） | アクセストークン（HS256）の発行と検証 |
| JPA | Hibernate ORM | 7.4.5.Final（lockfile） | |
| 内部DB | H2 ／ Flyway | 2.4.240 ／ 12.4.0（lockfile） | `-Dh2.compactThreads=1` をそろえる（`project.md` の Tech Stack）。単一インスタンス前提 |
| 組み込みの Tomcat | tomcat-embed-core | 11.0.26 | 脆弱性のため Spring Boot の管理の版を上書き |
| JSON | Jackson（`tools.jackson`） | 3.1.6 | Jackson の BOM で上書き（`project.md` の Tech Stack） |
| ログ | logstash-logback-encoder ／ opentelemetry-logback-appender | 9.0 ／ 2.28.1-alpha | appender は固定（`project.md` の Tech Stack） |
| DSL | SnakeYAML ／ networknt json-schema-validator | 2.7 ／ 3.0.6 | |
| メール | cherry-mustache-core（`vendor/java-mustache-processor`） | 0.1.0 | composite build |
| 対象DB のドライバー | mysql-connector-j ／ mariadb-java-client ／ postgresql | Spring Boot の BOM | 今回は版を確かめていない |

## バックエンドのテストと検査

| 部品 | 版 | 備考 |
|---|---|---|
| JUnit Jupiter ／ jqwik ／ ArchUnit | 6.0.3（lockfile）／ 1.10.1 ／ 1.5.1 | ArchUnit が層と境界の検査（K-3） |
| Testcontainers | 2.0.5（lockfile） | 対象DB の結合テストだけ。内部DB のテストは組み込みの H2 |
| SubEthaSMTP | 7.2.2 | JVM の中のテスト用の SMTP の受け手 |
| JaCoCo | 0.8.15 | 全体とパッケージごとの下限（K-7） |
| Spotless（プラグイン）／ palantir-java-format | 8.10.3 ／ 2.98.0 | プラグインの版は toml の1行で決まり、lockfile に載らない |
| SpotBugs（プラグイン 6.5.12）／ FindSecBugs | 4.10.4 ／ 1.14.0 | 関門 `spotbugsGate` |

## 画面

| 分類 | 部品 | 版 | 備考 |
|---|---|---|---|
| 土台 | React ／ react-router ／ i18next・react-i18next | 19.3.0（lockfile、範囲 `^19.2.8`）／ 8.4.0（lockfile、範囲 `^8.3.0`）／ 26.4.2 | |
| デザインシステム | make-you-chic-ui（`vendor/make-you-chic-ui`、`file:` の依存） | 固定先 `077f5b4` | 部品の一覧は `component-inventory.md` |
| ビルド | Vite | 8.3.1 | |
| テスト | Vitest ＋ `@vitest/coverage-v8` ／ Testing Library（react・dom・user-event・jest-dom）／ jsdom ／ vitest-axe・axe-core ／ fast-check | Vitest 5.0.2・fast-check 範囲 `^4.10.2` | |
| E2E | `@playwright/test` | 1.63.0 | `verify` と CI の外 |
| 検査 | Prettier ／ oxlint ／ ESLint（react-hooks）／ Stylelint | 範囲 `^3.9.9` ／ `^1.78.0` ／ `^10.8.1` ／ `^17.14.1` | |

## 検査の外部の道具

| 分類 | 部品 | 備考 |
|---|---|---|
| 秘密情報の検出 | Gitleaks | pre-commit と `verify`。CI で版と SHA-256 を固定して入れる（前回までの記録。今回は `ci.yml` を検索だけ） |
| 依存の脆弱性 | OSV-Scanner | `verify` の `osvScan`。同上 |

今回の Intent で新しい依存は要らない見込み（利用者の管理は既存の Spring・JPA・make-you-chic-ui の部品で組める）。足すときはライセンスと推移依存を確かめる（`team.md` の Code Style、`project.md` の Corrections）。
