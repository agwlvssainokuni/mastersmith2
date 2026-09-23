# 技術の構成（mastersmith2）

版は `gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json` による（コミット `6afbf97` 時点）。

## バックエンド

| 技術 | 版 | 用途 |
|---|---|---|
| Java | 25（toolchain） | 言語 |
| Spring Boot | 4.1.1 | webmvc・security・actuator・data-jpa・flyway・validation・aspectj・opentelemetry・oauth2-resource-server |
| Spring Framework（spring-tx・spring-orm） | 7.0.9 | トランザクション、JPA の統合（`JpaTransactionManager`、`HibernateJpaVendorAdapter`） |
| Spring Data JPA | 4.1.1 | リポジトリ |
| Hibernate ORM | 7.4.5.Final | JPA 実装。接続の扱いは spring-orm が設定する `DELAYED_ACQUISITION_AND_HOLD`（確定しても EntityManager を閉じるまで接続を持つ） |
| HikariCP | 7.0.2 | コネクションプール `mastersmith-db`（設定は `architecture.md` の「接続のプールの現在の設定」） |
| H2 | 2.4.240 | 内部DB（組み込み・ファイル保存） |
| Flyway | 12.4.0 | スキーマの変更（V1〜V4） |
| Tomcat（組み込み） | 11.0.26（強制。`backend/build.gradle.kts` 45〜52 行） | サーブレットコンテナ |
| logstash-logback-encoder | 9.0 | JSON の構造化ログ |
| opentelemetry-logback-appender | 2.28.1-alpha（固定。理由は `aidlc/spaces/default/memory/project.md` の Tech Stack） | ログの外部エクスポート（既定は無効） |

## フロントエンド（流し読み）

React 19.2、react-router 8.3、i18next 26・react-i18next 17、Vite 8.2、TypeScript 6.0。デザインシステムは `vendor/make-you-chic-ui`（サブモジュール、`file:` 参照）。

## テストと品質の道具

| 道具 | 版 | 用途 |
|---|---|---|
| JUnit 5（Boot の BOM）・Spring Boot Test・AssertJ | — | 単体・結合テスト |
| jqwik | 1.10.1 | 性質ベースのテスト（Java） |
| ArchUnit | 1.5.0 | 層と機能の境界の検査 |
| JaCoCo | 0.8.15 | カバレッジ（行 80%・分岐 70%） |
| Spotless ＋ palantir-java-format | 8.10.2 ＋ 2.98.0 | Java の書式とライセンスヘッダー |
| SpotBugs ＋ FindSecBugs | 4.10.4 ＋ 1.14.0 | Java の静的解析（High で失敗） |
| Vitest・@vitest/coverage-v8・Testing Library・vitest-axe・fast-check | 4.1 ほか・4.10 | 画面のテスト |
| Playwright | 1.63 | E2E（`verify` と CI の外） |
| oxlint・ESLint・Prettier・Stylelint | 1.78・10.8・3.9・17.14 | 画面の検査 |
| Gitleaks・OSV-Scanner | — | 秘密情報の検出、依存の脆弱性検査 |
| k6 | — | 負荷の試験（`perf/`） |

## 実行と観測

- 配備: 開発者の PC 上のコンテナ（`Dockerfile`・`compose.yaml`、アプリのメモリ上限 1GB）。
- 観測: Micrometer ＋ OpenTelemetry（外部エクスポートは既定で無効）。手元の監視は grafana/otel-lgtm を compose の profile で起動。`hikaricp_connections_pending` の警報が `docker/monitoring/provisioning/alerting/mastersmith.yaml` 268 行にある。
