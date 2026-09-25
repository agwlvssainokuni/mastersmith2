# 技術の構成（mastersmith2）

## 版の出どころ

バックエンドは `gradle/libs.versions.toml` と `backend/gradle.lockfile`（今回は H2 などの主な行だけを確かめた）、画面は `frontend/package.json` の宣言（流し読み）、実行環境は `Dockerfile`。今回確かめていない版は、開発担当のスキャンの値をそのまま載せた。

## 言語と実行環境

| 対象 | 版 | 備考 |
|---|---|---|
| Java | 25 | コンテナは `eclipse-temurin:25.0.4_7-jre-noble`。`-XX:MaxRAMPercentage=75.0`、追加の指定は `MASTERSMITH_JAVA_OPTIONS` |
| TypeScript | 6.0 系 | `strict` 系 |
| Gradle | Wrapper、Kotlin DSL | lockfile で固定 |

## バックエンド

| 部品 | 版 | 用途（今回の4件との関わり） |
|---|---|---|
| Spring Boot | 4.1.1 | Web MVC・Security（JWT）・Data JPA・Flyway・Actuator・OpenTelemetry |
| Tomcat（組み込み） | 11.0.26 | Boot の管理の版から引き上げ |
| H2 | 2.4.240（`backend/gradle.lockfile` 22 行） | 内部DB（組み込み・ファイル、MVStore）。TD-1 |
| HikariCP | Boot の管理 | 内部DB の接続プール（上限 既定 30、`minimum-idle` の指定なし）。TD-3 |
| Flyway | Boot の管理 | 内部DB のスキーマ変更（V1〜V6、前進のみ） |
| SnakeYAML | 2.6 | DSL の安全な読み込み（`Composer` で節の木）。TD-2 |
| networknt json-schema-validator | 3.0.6 | DSL の JSON Schema（2020-12）の検証 |
| Jackson | 3 系（`tools.jackson`） | JSON と、YAML の節の木から作る木。TD-2 |
| logstash-logback-encoder | 9.0 | 1行1件の JSON のログ |
| opentelemetry-logback-appender | 2.28.1-alpha（固定、`project.md` の Tech Stack） | ログの外部エクスポート（既定で無効） |
| MySQL・MariaDB・PostgreSQL の JDBC ドライバー | Boot の管理 | 対象DB の読み取り |

### テストと品質の道具

| 部品 | 版 | 用途 |
|---|---|---|
| JUnit 5・Spring Boot Test・AssertJ | BOM の管理 | 単体と結合のテスト |
| jqwik | 1.10.1 | 性質ベースのテスト |
| ArchUnit | 1.5.0 | 層と機能の境界 |
| Testcontainers | BOM の管理 | 対象DB の結合テスト |
| Spotless ＋ palantir-java-format | 2.98.0 | フォーマットとライセンスヘッダー |
| SpotBugs ＋ FindSecBugs | 4.10.4 ＋ 1.14.0 | 静的解析 |
| JaCoCo | 0.8.15 | カバレッジ（全体とパッケージごと） |

## 画面

| 部品 | 版（宣言） | 用途 |
|---|---|---|
| React ／ react-router ／ i18next | 19.2 ／ 8.3 ／ 26 | 画面・振り分け・表示言語 |
| Vite | 8.2 | ビルド |
| Vitest・Testing Library・user-event・vitest-axe・fast-check | Vitest 4.1・fast-check 4.10 | テスト・アクセシビリティ・性質ベース |
| Playwright | 1.63 | E2E |
| Prettier・oxlint・ESLint・Stylelint | 3.9・1.78・10.8・17.14 | フォーマッタとリンタ |
| make-you-chic-ui | サブモジュール（固定先 `edb1f943c0e66293494fa974605f34fcd7e258d7`） | デザインシステム |

## 実行環境・検査・CI

| 道具 | 用途 |
|---|---|
| Docker（colima）・Compose | 開発者の PC でのコンテナ |
| k6 | 負荷の試験（`perf/k6/scenarios.js`） |
| Gitleaks ／ OSV-Scanner | 秘密情報の検出・依存関係の脆弱性（`verify` から呼ぶ） |
| GitHub Actions・Dependabot・pre-commit | CI、更新の通知、コミット前の検査 |
