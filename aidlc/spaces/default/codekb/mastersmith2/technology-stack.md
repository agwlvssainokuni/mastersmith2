# 技術の構成（mastersmith2）

バックエンドとフロントエンドの版は前回の記録（`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`、コミット `6afbf97` 時点）による。実行環境とコンテナの版は今回（コミット `aeeaf73`）確かめた。

## バックエンド

| 技術 | 版 | 用途 |
|---|---|---|
| Java | 25（toolchain） | 言語 |
| Spring Boot | 4.1.1 | webmvc・security・actuator・data-jpa・flyway・validation・aspectj・opentelemetry・oauth2-resource-server |
| Spring Framework（spring-tx・spring-orm） | 7.0.9 | トランザクション、JPA の統合 |
| Spring Data JPA | 4.1.1 | リポジトリ |
| Hibernate ORM | 7.4.5.Final | JPA 実装。接続の扱いは `DELAYED_ACQUISITION_AND_HOLD` |
| HikariCP | 7.0.2 | コネクションプール `mastersmith-db`（上限 既定 30） |
| H2 | 2.4.240 | 内部DB（組み込み・ファイル保存） |
| Flyway | 12.4.0 | スキーマの変更（V1〜V4） |
| Tomcat（組み込み） | 11.0.26（強制） | サーブレットコンテナ。スレッドの上限は既定（200） |
| Spring Security `BCryptPasswordEncoder` | Boot の BOM | パスワードの照合。cost 既定 12（`MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST`、4〜31） |
| logstash-logback-encoder | 9.0 | JSON の構造化ログ |
| opentelemetry-logback-appender | 2.28.1-alpha（固定。理由は `aidlc/spaces/default/memory/project.md` の Tech Stack） | ログの外部エクスポート（既定は無効） |

## フロントエンド（流し読み）

React 19.2、react-router 8.3、i18next 26・react-i18next 17、Vite 8.2、TypeScript 6.0。デザインシステムは `vendor/make-you-chic-ui`（サブモジュール）。

## テストと品質の道具

| 道具 | 版 | 用途 |
|---|---|---|
| JUnit 5（Boot の BOM）・Spring Boot Test・AssertJ | — | 単体・結合テスト |
| jqwik | 1.10.1 | 性質ベースのテスト（Java） |
| ArchUnit | 1.5.0 | 層と機能の境界の検査 |
| JaCoCo | 0.8.15 | カバレッジ（行 80%・分岐 70%） |
| Spotless ＋ palantir-java-format | 8.10.2 ＋ 2.98.0 | Java の書式とライセンスヘッダー |
| SpotBugs ＋ FindSecBugs | 4.10.4 ＋ 1.14.0 | Java の静的解析（High で失敗） |
| Vitest・@vitest/coverage-v8・Testing Library・vitest-axe・fast-check | 4.1 ほか | 画面のテスト |
| Playwright | 1.63 | E2E（`verify` と CI の外） |
| oxlint・ESLint・Prettier・Stylelint | 1.78・10.8・3.9・17.14 | 画面の検査 |
| Gitleaks・OSV-Scanner | — | 秘密情報の検出、依存の脆弱性検査 |
| grafana/k6 | 2.3.0（`perf/README.md` でタグ固定） | 負荷の試験（`perf/k6/scenarios.js`） |

## 実行環境とコンテナ（今回確かめた範囲）

| 技術 | 版・設定 | 用途 | 場所 |
|---|---|---|---|
| Eclipse Temurin JRE | `25.0.4_7-jre-noble`（タグ固定、Ubuntu） | アプリのベースイメージ。負荷の試験の利用者の投入にも同じイメージを使う | `Dockerfile` 17 行、`perf/README.md` 27 行 |
| JVM の起動の引数 | `-XX:MaxRAMPercentage=75.0`、`-Duser.timezone=Asia/Tokyo` だけ | ヒープの上限をコンテナのメモリから決める | `Dockerfile` 34 行 |
| Docker Compose | v2（`name:`、`profiles`、`env_file` の `required`、`start_interval` を使う） | 配備と使い捨ての環境 | `compose.yaml`、`docker/perf/compose.yaml` |
| colima | この PC は CPU 2・メモリ 2GiB・aarch64（`colima list`） | Docker の VM（macOS） | README 16・21・232 行（リポジトリの外の設定） |
| grafana/otel-lgtm | 0.33.1（`mem_limit: 900m`） | 手元の監視（profile `monitoring`） | `compose.yaml` 79〜103 行 |
| otel/opentelemetry-collector | 0.161.0 | 外部エクスポートの確認（profile `observability`） | `compose.yaml` 69〜74 行 |
| Micrometer ＋ OpenTelemetry | Boot の BOM | 指標・トレース・ログの OTLP 送信（既定は無効、指標は 60 秒ごと） | `application.yaml` 168〜205 行 |

コンテナのメモリ・CPU の指標を集める道具（cAdvisor など）は入っていない。
