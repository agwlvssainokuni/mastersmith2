# 技術の構成（mastersmith2）

> 2026-10-29（記録のコミット `15a25aa`）の Intent `261029-reference-lookup-cleanup` の Focused scan で更新した。今回読み直したのは `frontend/package.json`（i18next 系）と `compose.yaml`・`docker/perf/compose.yaml` の `image:` 行、`perf/versions.env`、`backend/build.gradle.kts` の一部だけ。バックエンドの版の表・検査の道具の表は前回（2026-10-28、`gradle/libs.versions.toml`・`backend/gradle.lockfile` の再読）のまま。`Dockerfile`・`vendor/` は前々回（2026-10-25）の値のまま。所見 K-66 の本文はこの文書、K-78 の事実を「手元の運用」に足した。

## 言語と実行環境

| 項目 | 版・値 | 出どころ |
|---|---|---|
| Java | 25 | `libs.versions.toml` の `java` |
| 実行のイメージ | `eclipse-temurin:25.0.4.1_1-jre-noble`（ダイジェストで固定） | `Dockerfile` 17 行 |
| JVM の設定 | `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1` と `MASTERSMITH_JAVA_OPTIONS` | `Dockerfile` 52 行 |
| TypeScript | ^6.0.3 | `frontend/package.json` |

## バックエンド

| 部品 | 版 | 用途 |
|---|---|---|
| Spring Boot | **4.1.2** | アプリの土台（lockfile の `spring-boot-starter:4.1.2`） |
| Tomcat（組み込み） | 11.0.27（Spring Boot の管理の版。上書きは外れた） | Servlet の入れ物 |
| Jackson | 3.1.7（Spring Boot の管理の版。上書きは外れた） | JSON（lockfile の `jackson-core` は 3.1.7） |
| Hibernate | 7.4.13.Final | JPA |
| H2 | 2.4.240 | 内部DB（組み込み） |
| Flyway | 12.4.0 | 内部DB の移行（V1〜V13） |
| HikariCP | 7.0.2 | 接続のプール（内部DB と対象DB の読み取り・書き込み） |
| mysql-connector-j・mariadb-java-client・postgresql | 9.7.0・3.5.10・42.7.14 | 対象DB の JDBC ドライバー |
| SnakeYAML | 2.7（固定） | DSL の YAML |
| networknt json-schema-validator | 3.0.6（固定） | DSL の JSON Schema の検証 |
| cherry-mustache（java-mustache-processor） | 0.1.0 | メールのテンプレート |
| logstash-logback-encoder | 9.0 | 構造化ログ |
| opentelemetry-logback-appender-1.0 | 2.28.1-alpha（固定） | ログの外部エクスポート（既定で無効）。lockfile の `opentelemetry-sdk` は 1.62.0、`opentelemetry-instrumentation-api` は 2.28.1 |

## 画面

| 部品 | 版 | 用途 |
|---|---|---|
| React | ^19.2.8（`react-dom` も同じ） | 画面 |
| react-router | ^8.5.0 | 振り分け |
| i18next / react-i18next | ^26.5.5 / ^17.1.2（`package.json`。lockfile は 26.5.10 / 17.1.8。2026-10-29 に確かめた） | 日英の文言。Dependabot #81（i18next 26.5.10 → 26.5.11）・#82（react-i18next 17.1.8 → 17.1.9）は patch（K-79、`dependencies.md`） |
| Vite | ^8.3.5 | ビルドと開発のサーバー |
| make-you-chic-ui | サブモジュール `ecb07e9` | デザインシステム |

## テスト

| 部品 | 版 | 用途 |
|---|---|---|
| JUnit 5・Spring Boot Test・Testcontainers | Spring Boot の管理の版（Testcontainers 2.0.5） | Java の単体と結合（対象DB の3種類はコンテナ） |
| jqwik・ArchUnit | 1.10.2・1.5.1 | 性質ベースのテスト・層と境界の検査 |
| subethasmtp | 7.2.2 | テストの中の SMTP の受け手 |
| Vitest・`@vitest/coverage-v8` | ^5.0.5・^5.0.4 | 画面の単体テストとカバレッジ（thresholds） |
| Testing Library（react）・user-event・fast-check | ^16.3.3・^14.6.8・^4.10.2 | 画面のテスト |
| axe-core | ^4.14.0 | アクセシビリティの検査（B7 で 4.14.0 に上げ済み。Dependabot の見送りは外れている） |
| `@playwright/test` | ^1.64.1 | E2E（`./gradlew e2eTest`、`verify` と CI の外） |
| k6 | `grafana/k6:2.3.0`（`perf/versions.env`、ダイジェストで固定） | 負荷の試験（`perf/`） |

## 検査とビルドの道具

| 道具 | 版 | 用途 |
|---|---|---|
| Gradle（Kotlin DSL） | ラッパーの版は今回読んでいない | `./gradlew verify` の入口 |
| Spotless・palantir-java-format | 8.10.4・2.98.0 | Java の書式とライセンスヘッダー |
| SpotBugs（プラグイン・道具）・FindSecBugs | 6.5.13・4.10.4・1.14.0 | 静的解析 |
| JaCoCo | 0.8.15 | カバレッジ（全体とパッケージごと） |
| Prettier・oxlint・ESLint・Stylelint | ^3.9.10・^1.89.0・^10.13.0・^17.16.0 | 画面の書式とリンタ |
| Gitleaks・OSV-Scanner | `build.gradle.kts` から呼ぶ | 秘密情報の検出・依存の脆弱性検査 |
| GitHub Actions | `.github/workflows/ci.yml`（actions はコミットの SHA で固定） | `develop` への push で `./gradlew verify` |

## K-66 固定している部品の現状（Spring Boot 4.1.2 への引き上げの後。2026-10-28 に再確認）

前回（`261025-deps-perf-carryover`）の見直しで Spring Boot は 4.1.2 に上がった（`libs.versions.toml` の `spring-boot = "4.1.2"`、lockfile の `spring-boot-starter:4.1.2`）。`backend/build.gradle.kts` と `libs.versions.toml` に Tomcat・Jackson の BOM の上書きはもう無く（`grep -i tomcat|jackson` は ArchUnit 用の除外とコメントだけ）、lockfile の `tomcat-embed-core:11.0.27`・`jackson-core:3.1.7` は Spring Boot の管理の版（見立て: BOM の版が上書き時の版以上になったため外した。外した変更そのものは今回読んでいない）。

| 部品 | 今の固定 | 固定の理由（`libs.versions.toml` のコメントによる） | 外す時期 |
|---|---|---|---|
| opentelemetry-logback-appender | 2.28.1-alpha | Spring Boot の管理の OpenTelemetry が 4.1.2 でも 1.62.0 のまま。2.29 以降は api 1.63 以上を求める | 管理の OpenTelemetry が 1.63 以上になったとき（外部エクスポートを有効にして確かめる） |
| networknt json-schema-validator | 3.0.6 | 3.0.7 以降は Jackson を 3.2 系に引き上げる。4.1.2 でも管理の Jackson は 3.1.7（3.1 系） | 管理の Jackson が 3.2 系以上になったとき |

- ほかの Spring Boot が管理する部品の版は lockfile で確かめた: Hibernate 7.4.13.Final、HikariCP 7.0.2、Flyway 12.4.0、H2 2.4.240、OpenTelemetry SDK 1.62.0。H2 を上げるときは `-Dh2.compactThreads=1` の要否を見直す（`project.md` の Tech Stack）。

## 手元の運用

- コンテナの版の固定先（K-78、2026-10-29 に `image:` 行を読んだ）: `compose.yaml` は otel-lgtm `grafana/otel-lgtm:0.38.0@sha256:75cdf892…`（83 行）、Mailpit `axllent/mailpit:v1.32.0@sha256:0790692b…`（121 行）、otel-collector 0.163.2、postgres 18.6、mysql 26.10.0、mariadb 13.0.2。`docker/perf/compose.yaml` の postgres・mysql・mariadb・Mailpit は `compose.yaml` と同じ版とダイジェスト（lgtm と collector は無い）。ほかの固定先は `backend/.../TargetDbImages.java`、`perf/versions.env`（k6・JRE）、`docker/hikari-pool.sh`（jdk）、`README.md`（Mailpit）、`perf/README.md`（lgtm、433 行ほか）に分散し、一覧は `.github/dependabot.yml` のコメントにある。
- 動いているコンテナ（`docker ps -a` / `docker images` の一覧、開発担当の読み取り）: Mailpit は `axllent/mailpit:v1.32.0` で稼働中（`compose.yaml` と同じタグ）、lgtm のコンテナ `mastersmith-lgtm-1` は `grafana/otel-lgtm:0.38.0` で停止中、ローカルのイメージ一覧には `grafana/otel-lgtm:0.34.0` も残る。タグは `compose.yaml` と一致して見え、ずれは今は見えない。**ダイジェストの一致は `docker inspect` を使っていないため未検証。**

- 負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`）の対象DB は postgres 18.6・mysql 26.10.0・mariadb 13.0.2・Mailpit v1.32.0（いずれもダイジェストで固定）。
- 監視は `grafana/otel-lgtm` と OpenTelemetry Collector を compose の profile で起動し、警報とダッシュボードは `docker/monitoring/` のファイルで持つ。メールの受け手（Mailpit）と見本の対象DB も compose の profile（前回の記録）。

## Sources

- `gradle/libs.versions.toml`・`backend/gradle.lockfile`・`backend/build.gradle.kts`・`frontend/package.json`・`docker/perf/compose.yaml`・`perf/versions.env`・`.github/dependabot.yml`（今回）、`Dockerfile`（前回）。
