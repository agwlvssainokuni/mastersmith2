# 技術の構成（mastersmith2）

版の出どころ: バックエンドは `gradle/libs.versions.toml` と `backend/gradle.lockfile`（Spring Boot の BOM が決める版を含む）、画面は `frontend/package.json` の宣言（`^` の範囲。実際の版は `frontend/package-lock.json`）、ビルドは `gradle/wrapper/gradle-wrapper.properties`・`.github/workflows/ci.yml`・`Dockerfile`・`compose.yaml`。

## 言語と実行環境

| 対象 | 版 | 備考 |
|---|---|---|
| Java | 25（ツールチェーン） | コンテナは `eclipse-temurin:25.0.4_7-jre-noble` |
| TypeScript | ^6.0.3 | `strict` 系、`any` の禁止 |
| Node.js | 24 | `frontend/.npmrc` の `engine-strict`。`verify` の段 0 で確認する |
| Gradle | 9.7.1（Wrapper、Kotlin DSL） | 取得元は Maven Central だけ |

## バックエンド

| 部品 | 版 | 用途 |
|---|---|---|
| Spring Boot | 4.1.1 | 基盤。starter: webmvc・security・actuator・data-jpa・flyway・validation・aspectj・opentelemetry・oauth2-resource-server（tomcat-runtime は `providedRuntime`） |
| Spring Framework ／ Spring Security | 7.0.9 ／ 7.1.1 | Web・セキュリティ（BOM の管理） |
| Hibernate ORM | 7.4.5.Final | JPA（`ddl-auto: validate`） |
| Flyway | 12.4.0 | 内部DBのスキーマ変更（H2 用。MySQL・PostgreSQL 用のモジュールは無い） |
| H2 | 2.4.240 | 内部DB（組み込み・ファイル保存、`runtimeOnly`） |
| HikariCP | 7.0.2 | 接続プール `mastersmith-db`（上限 既定 30、借りる待ち 5 秒） |
| Jackson | 3.1.5（`tools.jackson`） | JSON。Spring Boot 4 の既定。`com.fasterxml.jackson.core:jackson-annotations` 2.21 だけが併存する |
| SnakeYAML | 2.6 | Spring Boot の推移依存（`application.yaml` の読み込み用）。アプリのコードからは使っていない |
| Nimbus JOSE + JWT | 10.9.1 | JWT（HS256）の発行と検証 |
| Tomcat（組み込み） | 11.0.26 | Spring Boot 管理の 11.0.24 から上書き（脆弱性の回避） |
| logstash-logback-encoder | 9.0 | 1行1件の JSON のログ |
| opentelemetry-logback-appender | 2.28.1-alpha（固定） | ログの OTLP の送信（有効時だけ）。Spring Boot の OpenTelemetry 1.62 に合わせて固定（`project.md` の Tech Stack） |

### バックエンドのテストと品質の道具

| 部品 | 版 | 用途 |
|---|---|---|
| JUnit Platform・spring-boot-starter-test・webmvc-test・security-test | BOM の管理 | 単体テストと結合テスト |
| jqwik | 1.10.1 | 性質ベースのテスト |
| ArchUnit | 1.5.0 | 層の境界の検査 |
| Spotless ＋ palantir-java-format | 8.10.2 ＋ 2.98.0 | フォーマットとライセンスヘッダー |
| SpotBugs（プラグイン）＋ FindSecBugs | 4.10.4（6.5.11）＋ 1.14.0 | 静的解析 |
| JaCoCo | 0.8.15 | カバレッジ |

## 画面

| 部品 | 版（宣言） | 用途 |
|---|---|---|
| React・react-dom | ^19.2.8 | 画面 |
| react-router | ^8.3.0 | URL の振り分け |
| i18next ／ react-i18next | ^26.4.2 ／ ^17.0.15 | 表示言語（ja・en） |
| @fontsource/noto-sans-jp | ^5.3.0 | フォント |
| make-you-chic-ui | `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui` | デザインシステム（サブモジュールの固定先 `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`） |
| Vite ＋ @vitejs/plugin-react | ^8.2.1 ＋ ^6.0.5 | ビルド |
| Vitest ＋ @vitest/coverage-v8 | ^4.1.11 | テストとカバレッジ |
| jsdom ／ Testing Library（react ^16.3.3・dom ^10.4.2・jest-dom ^7.0.1・user-event ^14.6.7）／ vitest-axe ^0.1.0 ／ fast-check ^4.10.2 | — | 画面部品のテスト、アクセシビリティ、性質ベース |
| @playwright/test | ^1.63.0 | E2E（`./gradlew e2eTest` だけ） |
| oxlint ／ ESLint ＋ eslint-plugin-react-hooks ／ Prettier ／ Stylelint ＋ stylelint-config-standard | ^1.78.0 ／ ^10.8.1 ＋ ^7.1.1 ／ ^3.9.6 ／ ^17.14.1 ＋ ^40.0.0 | リンタ・フォーマッタ |

## 安全の検査・CI・実行環境

| 道具 | 版 | 用途 |
|---|---|---|
| Gitleaks | 8.30.1（CI では SHA-256 で固定） | 秘密情報の検出（pre-commit と `verify`） |
| OSV-Scanner | 2.6.0（CI では SHA-256 で固定） | 依存関係の脆弱性の検査 |
| pre-commit | 4.x | コミット前の検査（Gitleaks・Spotless・Prettier） |
| Dependabot | — | gradle・npm・github-actions・docker の更新の通知（毎週） |
| GitHub Actions | Actions はコミットのハッシュで固定 | `develop` へのプッシュ・`v*` のタグ・手動で `./gradlew verify` |
| Docker（colima）・Compose v2 | — | 開発者の PC でのコンテナの起動 |
| otel/opentelemetry-collector ／ grafana/otel-lgtm | 0.161.0 ／ 0.33.1 | profile で起動する外部エクスポートの受け手と手元の監視 |
| k6 | — | 負荷の試験（`perf/`） |

## 今回の Intent に関わる「無いもの」

コードと lockfile で確かめた結果、次のものは依存関係に無い。

- MySQL・MariaDB・PostgreSQL の JDBC ドライバー
- Testcontainers
- JSON Schema の検証の部品（Java・画面とも）
- Jackson の YAML 形式（`jackson-dataformat-yaml`）。YAML を読む部品は、推移依存の SnakeYAML だけ
- 画面の YAML の解析の部品、コードエディター

足すときの制約（lockfile の更新、OSV の関門、Jackson 3 との組み合わせ）は `code-quality-assessment.md` の C-3 を参照。
