# 技術の構成（mastersmith2）

版は 2026-10-04（コミット `47ec27b`）に、`gradle/libs.versions.toml` の `[versions]`（今回深く読んだ）と `frontend/package.json` の指定（流し読み）から抜いた値である。lockfile で解決した版は今回確かめていないため書かない。依存の向きと管理の決まりは `dependencies.md`。

## 言語と実行環境

| 分類 | 技術 | 版 | 備考 |
|---|---|---|---|
| バックエンドの言語 | Java（Temurin） | 25（`libs.versions.toml` の `java`） | 実行のイメージは `eclipse-temurin:25.0.4_7-jre-noble`（`Dockerfile`、ダイジェストなし、K-31） |
| 画面の言語 | TypeScript | 範囲 `^6.0.3` | |
| ビルド | Gradle（Kotlin DSL）＋ npm | — | 入口は `./gradlew verify`（Gradle の版は今回確かめていない） |

## バックエンド

| 分類 | 部品 | 版 | 備考 |
|---|---|---|---|
| 土台 | Spring Boot（Web・Security・Data JPA・Flyway・Actuator・Mail ほか） | 4.1.1 | |
| 組み込みの Tomcat | tomcat-embed-core | 11.0.26 | 脆弱性のため Spring Boot の管理の版を上書き。フィルターの中の例外のログは K-28 |
| JSON | Jackson | 3.1.7 | Jackson の BOM（platform）で上書き（`project.md` の Tech Stack） |
| 内部DB ／ 接続プール | H2 ／ HikariCP | Spring Boot の管理の版 | ファイルの H2（`DEFRAG_ALWAYS=TRUE`）、プールの上限の既定 30・借りる待ちの上限 5000 ms。行の排他の待ちの上限 3000 ms（K-27） |
| パスワードのハッシュ | Spring Security の `BCryptPasswordEncoder` | Spring Boot の管理の版 | cost の既定 12（`MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST`）。ログインの時間の大半と見る（K-26、見立て） |
| ログ | logstash-logback-encoder ／ opentelemetry-logback-appender | 9.0 ／ 2.28.1-alpha | 1行1件の JSON、MDC の `traceId`・`spanId`。appender は固定（`project.md` の Tech Stack） |
| DSL | SnakeYAML ／ networknt json-schema-validator | 2.7 ／ 3.0.6 | |
| メール | cherry-mustache（`vendor/java-mustache-processor`） | 0.1.0 | composite build |

## バックエンドのテストと検査

| 部品 | 版 | 備考 |
|---|---|---|
| jqwik ／ ArchUnit | 1.10.1 ／ 1.5.1 | 性質ベースのテストと、層・境界の検査 |
| Testcontainers | Spring Boot の管理の版 | 対象DB の結合テストだけ。イメージは `TargetDbImages` のダイジェストで固定（K-31） |
| SubEthaSMTP | 7.2.2 | JVM の中のテスト用の SMTP の受け手 |
| JaCoCo | 0.8.15 | 全体とパッケージごとの下限（K-30） |
| Spotless ／ palantir-java-format | 8.10.3 ／ 2.98.0 | |
| SpotBugs（プラグイン）／ SpotBugs ／ FindSecBugs | 6.5.12 ／ 4.10.4 ／ 1.14.0 | 関門 `spotbugsGate` |

## 画面

| 分類 | 部品 | 版（`package.json` の範囲） | 備考 |
|---|---|---|---|
| 土台 | React ／ react-router | `^19.2.8` ／ `^8.3.0` | |
| デザインシステム | make-you-chic-ui（`vendor/make-you-chic-ui`、`file:` の依存） | サブモジュールの固定先 | `RadioGroup` に読み取り専用の口が無い（K-29） |
| ビルド ／ テスト ／ E2E | Vite ／ Vitest ／ `@playwright/test` | `^8.3.1` ／ `^5.0.2` ／ `^1.63.0` | E2E は `verify` と CI の外 |

## 負荷の試験・監視・コンテナのイメージ

| 分類 | 部品 | 固定の仕方 |
|---|---|---|
| 負荷の試験 | k6（`grafana/k6:2.3.0`、`perf/README.md` の `docker run`） | 版だけ（ダイジェストなし） |
| 手元の監視 | `otel/opentelemetry-collector:0.162.0`・`grafana/otel-lgtm:0.34.0`（`compose.yaml`） | 版だけ |
| メールの受け手 | Mailpit `v1.31.2`（`compose.yaml`・`docker/perf/compose.yaml`） | 版とダイジェスト |
| 見本の対象DB | PostgreSQL `18.6`・MySQL `26.7.0`・MariaDB `13.0.2`（`compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages`） | 版とダイジェスト、3か所に手で書く（K-31） |

## 検査の外部の道具

| 分類 | 部品 | 備考 |
|---|---|---|
| 秘密情報の検出 | Gitleaks | pre-commit と `verify`（前回までの記録） |
| 依存の脆弱性 | OSV-Scanner | `verify` の `osvScan`。依存の脆弱性の関門はこれだけ（`team.md` の Way of Working） |

今回の Intent の論点は、新しい依存を足さずに扱える見込み（見立て）。足すときはライセンスと推移依存を確かめる（`team.md` の Code Style、`project.md` の Corrections）。
