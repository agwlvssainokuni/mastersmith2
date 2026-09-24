# 技術の構成（mastersmith2）

版の出どころ: バックエンドは `gradle/libs.versions.toml` と `backend/gradle.lockfile`（主な部品の行だけを確かめた）、画面は `frontend/package.json` の宣言（実際の版は `frontend/package-lock.json`）、実行環境は `Dockerfile`・`compose.yaml`・`gradle/wrapper/gradle-wrapper.properties`・`.github/workflows/ci.yml`。

## 言語と実行環境

| 対象 | 版 | 備考 |
|---|---|---|
| Java | 25（ツールチェーン） | コンテナは `eclipse-temurin:25.0.4_7-jre-noble` |
| TypeScript | 6.0 系 | `strict` 系、`any` の禁止 |
| Node.js | 24 | `verify` の段 0 で確認 |
| Gradle | 9.7.1（Wrapper、Kotlin DSL） | 取得元は Maven Central だけ |

## バックエンド

| 部品 | 版 | 用途 |
|---|---|---|
| Spring Boot | 4.1.1 | 基盤（Spring Framework 7.0.9・Spring Security 7.1.1） |
| Tomcat（組み込み） | 11.0.26 | Boot の管理の版から脆弱性の回避のため引き上げ（`backend/build.gradle.kts` 46〜53 行） |
| Hibernate ORM | 7.4.5.Final | JPA（`ddl-auto: validate`）。起動時の案内のログは TD-4 |
| H2 | 2.4.240 | 内部DB（組み込み・ファイル、`DEFRAG_ALWAYS=TRUE`） |
| HikariCP | 7.0.2 | 内部DB の接続プール（上限 既定 30） |
| Flyway | 12.4.0 | 内部DB のスキーマ変更（V1〜V6） |
| logback-classic ／ logstash-logback-encoder | 1.5.38 ／ 9.0 | 1行1件の JSON のログ |
| OpenTelemetry API ／ opentelemetry-logback-appender-1.0 | 1.62.0 ／ 2.28.1-alpha（固定、`project.md` の Tech Stack） | ログの OTLP の送信（有効時だけ）。TD-1 |
| Micrometer Tracing | 1.7.1 | トレースID |
| MySQL Connector/J ／ MariaDB Connector/J ／ PostgreSQL JDBC | 9.7.0 ／ 3.5.10 ／ 42.7.13 | 対象DB の読み取り（ドライバー自身のログは OFF） |
| SnakeYAML ／ networknt json-schema-validator ／ Jackson | 2.6 ／ 3.0.6 ／ 3.1.5 | DSL の読み込みと検証、JSON |

### バックエンドのテストと品質の道具

| 部品 | 版 | 用途 |
|---|---|---|
| JUnit 5・Spring Boot Test | BOM の管理 | 単体と結合のテスト |
| jqwik | 1.10.1 | 性質ベースのテスト |
| ArchUnit | 1.5.0 | 層と機能の境界 |
| Testcontainers | 2.0.5 | 対象DB（MySQL・MariaDB・PostgreSQL）の結合テスト |
| Spotless ＋ palantir-java-format | ＋ 2.98.0 | フォーマットとライセンスヘッダー |
| SpotBugs ＋ FindSecBugs | 4.10.4 ＋ 1.14.0 | 静的解析 |
| JaCoCo | — | カバレッジ（全体とパッケージごと） |

## 画面

| 部品 | 版（宣言） | 用途 |
|---|---|---|
| React | 19.2 | 画面 |
| react-router | 8.3 | URL の振り分け |
| i18next ／ react-i18next | 26.4 ／ 17.0 | 表示言語（ja・en） |
| make-you-chic-ui | 0.0.0（`file:` 参照。サブモジュールの checkout `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`） | デザインシステム。TD-7 |
| Vite | 8.2 | ビルド（`resolve.dedupe`） |
| Vitest ＋ @vitest/coverage-v8 | 4.1 | テストとカバレッジ（行 80・分岐 70） |
| Testing Library・user-event・vitest-axe・fast-check | fast-check 4.10 | 部品のテスト・アクセシビリティ・性質ベース |
| Playwright | 1.63 | E2E（`verify` と CI の外） |
| Prettier・oxlint・ESLint・Stylelint | — | フォーマッタとリンタ |

## 実行環境・安全の検査・CI

| 道具 | 版 | 用途 |
|---|---|---|
| Docker（colima）・Compose | — | 開発者の PC でのコンテナ |
| otel/opentelemetry-collector ／ grafana/otel-lgtm | 0.161.0 ／ 0.33.1 | OTLP の受け手（確認用は debug の出力だけ）と手元の監視（Loki を含む） |
| 見本の対象DB | `postgres:18.6`・`mysql:8.4.11`・`mariadb:11.8.9`（ダイジェストで固定） | profile で起動 |
| k6 | — | 負荷の試験（`perf/k6/scenarios.js`） |
| Gitleaks ／ OSV-Scanner | CI では版と SHA-256 で固定 | 秘密情報の検出・依存関係の脆弱性 |
| GitHub Actions | Actions はハッシュで固定 | `develop` へのプッシュと `v*` のタグで `./gradlew verify` |
| Dependabot・pre-commit | — | 更新の通知（gradle・npm `/frontend`・github-actions・docker）、コミット前の検査 |
