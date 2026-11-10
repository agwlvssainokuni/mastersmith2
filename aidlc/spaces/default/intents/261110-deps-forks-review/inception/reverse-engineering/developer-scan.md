# 開発担当のコードスキャン（mastersmith2、Intent 261110-deps-forks-review）

- 走査の広さ・深さ: Focused scan・Minimal。既存の知識ベースの判定は STALE。
- 記録するコミット: `7f36d8aa823b2f7315fe21884582b5c90bf28c47`（`git rev-parse HEAD` で確かめた。スナップショットの `head_commit` と一致）。
- 確かめ方: ファイルの読み取り（Read・`cat`・`sed`・`grep`・`ls`・`wc`）と、`git rev-parse`・`git log`・`git diff --stat`・`git submodule status` の読み取りだけ。テスト・ビルド・`npm`・`./gradlew`・`docker`・`gh` は流していない。外のサイト（Maven Central・Docker Hub）は見ていない。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**:
  - `.github/`（`dependabot.yml`・`workflows/ci.yml` の全文）
  - `gradle/`（`libs.versions.toml`・`wrapper/gradle-wrapper.properties` の全文。`gradle-wrapper.jar` はバイナリのため開いていない）
  - `settings.gradle.kts`（全文）
  - `settings-gradle.lockfile`（全文）
  - `backend/build.gradle.kts`（全文、527 行）
  - `frontend/package.json`（全文）
  - `Dockerfile`（全文）
  - `compose.yaml`（全文）
  - `config/`（`npm-build-tools.txt` の全文、`license-header.txt` の頭）
  - `docs/verification.md`（1〜60 行と、`-PitForks` の節 79〜95 行。ほかは `grep` だけ）
  - `backend/src/main/java/cherry/mastersmith/common/health/`（2 ファイルの全文）
- **Skimmed only**:
  - `backend/gradle.lockfile`（主な部品の行を `grep`）
  - `frontend/package-lock.json`（主な部品の `version` の行を `grep`）
  - `build.gradle.kts`（1〜80 行と `itTests`・`verifyQuick` の行の `grep`）
  - `scripts/`（7 ファイルの頭の説明と `wc -l`、`ci-summary.mjs` の `parseGradleFloors`（174〜196 行））
  - `docker/`（`perf/compose.yaml`・`e2e/compose.targetdb-port.yaml`・`hikari-pool.sh`・`check-container-limits.sh` のイメージの行の `grep` と一覧）
  - `backend/config/`（`spotbugs-exclude.xml` の `<Match` の数だけ）
  - `frontend/scripts/check-license-header.mjs`（今回は開いていない。前回の記録による）
  - `backend/src/test/java/cherry/mastersmith/common/health/`（テストの名前と `@DisplayName` の一覧だけ）
  - 7 パッケージのうち `common/health` の本体以外の 13 ディレクトリ（`access/service`・`audit/repository`・`common/error/{domain,service}`・`common/i18n/domain`・`common/web` の本体とテスト）: `wc -l` と、前の記録のコミット `4fe8e79` からの `git diff --stat`（変化なし）だけ
  - スナップショットの範囲の外で `grep` だけしたもの: `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`（版とダイジェストの行）・`backend/src/test/java/cherry/mastersmith/config/H2CompactThreadsTest.java`・`frontend/playwright.config.ts`（132〜175 行）・`perf/versions.env`・`perf/README.md`・`.pre-commit-config.yaml`・`docs/internal-db.md`・`docs/dsl.md`・`docs/operations.md`・`docs/mail.md`（どれもイメージ・版・`compactThreads` の行だけ）
- **Left out**:
  - `vendor/`（サブモジュール。固定先のコミットは `git submodule status` だけで見た）
  - `node_modules/`・`build/`・`dist/`・`coverage/`・`test-results/`・`.gradle/`
  - `reference/`
  - AI-DLC 自身（`.claude/`・`.kiro/`・`.codex/`・`.cursor/`・`.opencode/`・`.aidlc/`・`aidlc/`（書き込み先の codekb と記録の置き場を除く））

### Packages Found

- `backend` — Gradle のサブプロジェクト — Java 25 — Spring Boot 4.1.2 のアプリ（実行可能 WAR）
- `frontend` — npm のパッケージ `mastersmith-frontend` — TypeScript 6.0.3・React 19.3.1 — 画面（`dist` を WAR に同梱）
- `vendor/java-mustache-processor` — Gradle の composite build（`settings.gradle.kts` の `includeBuild`）— Java — メールのテンプレートの Mustache（固定先 `d61ba6d`、`0.1.0-6-gd61ba6d`、版の名前 `cherry-mustache = "0.1.1"`）
- `vendor/make-you-chic-ui` — npm の `file:` の依存 — TypeScript — デザインシステム（固定先 `d112795`）

### Build System

- **Type**: Gradle 9.8.2（Wrapper、Kotlin DSL）＋ npm（`frontend/`）
- **Config Files**: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`
- **Build Dependencies**: `backend:bootWar` → `:frontendBuild`（`dist` を `WEB-INF/classes/static`）。`backend` → `vendor/java-mustache-processor`（composite build）。`frontend` → `vendor/make-you-chic-ui`（`file:`）。依存の取得元は Maven Central だけ（`RepositoriesMode.FAIL_ON_PROJECT_REPOS`）。Spring Boot の BOM を `platform(libs.spring.boot.bom)` で `implementation`・`providedRuntime`・`testImplementation`・`testRuntimeOnly` に読む（`backend/build.gradle.kts` 46〜50 行）。`settings-gradle.lockfile` は `empty=incomingCatalogForLibs0` の1行だけ。

### APIs Discovered

- 今回の範囲に新しい API は無い。`common/health` は Actuator の `/actuator/health` の DB の確かめを `TimeBoundedDbHealthIndicator`（時間の上限 `mastersmith.health.db-timeout`、既定 2s）で置き換えるだけ。

### Frameworks & Libraries

版の正本は `gradle/libs.versions.toml` と lockfile。下は今回の値（2026-11-11）。

- Spring Boot — 4.1.2（`libs.versions.toml` 21 行、lockfile 223 行）— 土台。Spring Framework 7.0.10・Spring Security 7.1.2・Tomcat 11.0.27・Hibernate 7.4.13.Final・Flyway 12.4.0・HikariCP 7.0.2・Micrometer 1.17.2・Micrometer Tracing 1.7.2・Logback 1.5.38・SLF4J 2.0.20（lockfile）
- Jackson — 3.1.7（`tools.jackson.core:jackson-core`・`jackson-databind`・`tools.jackson:jackson-bom`、lockfile 257〜259 行）— Spring Boot の BOM の管理の版。**Jackson の BOM を platform で読む上書きは今のビルドに無い**（`backend/build.gradle.kts`・`libs.versions.toml` の `grep` で `jackson` は networknt の `exclude` と説明のコメントだけ）。`git log -S jackson-bom` では、コミット `98ef3e51`「Spring Boot を 4.1.2 に上げ、Tomcat・Jackson の上書きを外す（B1）」で外れた。`com.fasterxml.jackson.core:jackson-annotations` 2.21 も推移で入る（lockfile 9 行）
- opentelemetry-logback-appender-1.0 — 2.28.1-alpha（固定。`libs.versions.toml` 33 行、lockfile 56 行）— ログの外部エクスポート。OpenTelemetry API・SDK は 1.62.0（lockfile 60・73 行）。固定の理由と外す時期は `libs.versions.toml` 28〜32 行のコメント（2.29 以降は api 1.63 以上を求める。外す時期は管理の OpenTelemetry が 1.63 以上）
- networknt json-schema-validator — 3.0.6（固定。`libs.versions.toml` 42 行、lockfile 28 行）— DSL の JSON Schema。3.0.7 以降は Jackson を 3.2 系へ引き上げる（コメント 38〜41 行）。`jackson-dataformat-yaml` と `snakeyaml-engine` を依存から外す（`backend/build.gradle.kts` 66〜69 行）
- H2 — 2.4.240（Spring Boot の管理。lockfile 24 行）— 内部DB
- SnakeYAML 2.7（明示）・logstash-logback-encoder 9.0・jqwik 1.10.2・ArchUnit 1.5.1・subethasmtp 7.2.2・Testcontainers 2.0.5・JUnit Jupiter 6.0.3・AssertJ 3.27.7・Mockito 5.23.0・JDBC（mysql-connector-j 9.7.0・mariadb-java-client 3.5.10・postgresql 42.7.14）・nimbus-jose-jwt 10.9.1・jakarta.mail-api 2.1.5・angus-mail 2.0.5
- ビルドのプラグインと道具 — Spotless 8.10.4・palantir-java-format 2.103.0・SpotBugs のプラグイン 6.5.13・SpotBugs 4.10.5・FindSecBugs 1.14.0・JaCoCo 0.8.16（`libs.versions.toml` 20〜27 行）
- 画面（`frontend/package.json` の範囲／lockfile の解決済み）— React・react-dom `^19.3.1`／19.3.1、react-router `^8.5.1`、i18next `^26.6.0`、react-i18next `^17.1.10`、Vite `^8.3.5`／8.3.5、TypeScript `^6.0.3`／6.0.3、Vitest・`@vitest/coverage-v8` `^5.0.5`／5.0.5、ESLint `^10.14.0`／10.14.0、oxlint `^1.90.0`／1.90.0、Prettier `^3.9.10`、Stylelint `^17.16.0`、`@playwright/test` `^1.64.1`／1.64.1、axe-core `^4.14.0`／4.14.0、`@typescript-eslint/parser` `^8.72.1`、jsdom `^30.1.3`、fast-check `^4.10.2`、`engines` は `node >=26 <27`
- CI の道具 — JDK 25（temurin）・Node 26・Gitleaks 8.30.1・OSV-Scanner 2.7.0（版と SHA-256）、Actions は checkout v7.0.1・setup-java v6.1.0・setup-node v7.1.0・setup-gradle v6.4.1・upload-artifact v7.0.2・download-artifact v8.0.2（コミットの SHA で固定）。`runs-on: ubuntu-latest`（版を固定しない）

### Test Coverage

- **Test Directories**: `backend/src/test/java`（`*Test` と `*IT`）、`frontend/src`（`*.test.ts(x)`）、`frontend/e2e/`
- **Test Frameworks**: JUnit 6・jqwik・ArchUnit・Testcontainers・subethasmtp／Vitest・Testing Library・vitest-axe・fast-check／Playwright
- **Coverage Config**: present。JaCoCo の判定 `jacocoTestCoverageVerification`（`backend/build.gradle.kts` 305〜338 行）は、全体の `rule` と `element = "PACKAGE"` の `rule` の2つで、どちらも行 0.80・分岐 0.70。**パッケージごとの規則に `excludes` は無く、すべてのパッケージに当たる**。前の一覧 `packagesJudgedByTotal` は 298〜304 行のコメントに経緯として名前が残るだけ（Intent 261108-ci-checks-release で消えた）。`scripts/ci-summary.mjs` の `parseGradleFloors`（174〜196 行）は今は `tasks.jacocoTestCoverageVerification` の塊から `element = "PACKAGE"` の前後の `minimum` を読む形で、一覧を読まない。`docs/verification.md` 22 行も今の形。計測から外すのは `MastersmithApplication*` と `*Properties`（213〜217 行）。

### Code Quality Indicators

- **Linting**: Spotless（palantir・ライセンスヘッダー）、SpotBugs＋FindSecBugs と `spotbugsGate`、Prettier・oxlint・ESLint・Stylelint（`frontend/`、`../scripts` も対象）
- **CI/CD**: `.github/workflows/ci.yml`（ジョブ `verify` が `./gradlew verify -PitForks="$IT_FORKS"`、ジョブ `release` は `v*` のタグのとき）、`.github/dependabot.yml`（gradle・npm・github-actions・docker・docker-compose）
- **Documentation**: `docs/verification.md` に段の表・使い分け・`-PitForks` の実測の表がある。コードのコメントは日本語で、版の固定に理由と外す時期を書いている

### Technical Debt Signals

- `.github/dependabot.yml` の gradle の `ignore` の説明の最初の段落は「Spring Boot 4.1.1 の OpenTelemetry 1.62」と書き、続く段落は 4.1.2 で見直した経緯を書く（古い版の名前が残る。動きには効かない）。
- `aidlc/spaces/default/memory/project.md` の Tech Stack の「Jackson の上書きは 3.1.7 とする（…Jackson の BOM を platform で読む形は同じ）」は、今のビルドに上書きが無いため古い（上の Frameworks の Jackson の行）。同じく「opentelemetry-logback-appender は … Spring Boot 4.1.1 が持つ OpenTelemetry 1.62」も版の名前が古い（固定そのものは残っている）。
- 前の知識ベースの `technology-stack.md` の表の版の古いもの: 実行のイメージ `eclipse-temurin:25.0.4.1_1`（今は `25.0.5_8`）、palantir-java-format 2.98.0（今は 2.103.0）、SpotBugs 4.10.4（今は 4.10.5）、JaCoCo 0.8.15（今は 0.8.16）、cherry-mustache 0.1.0（版の名前は 0.1.1）、Mailpit v1.32.0・otel-lgtm 0.38.0・otel-collector 0.163.2（今は v1.33.0・0.39.0・0.164.0）、make-you-chic-ui の固定先 `cae5fed`（今は `d112795`）。
- 前の知識ベースの `packagesJudgedByTotal` を今の仕組みとして書く箇所（`architecture.md`・`code-structure.md`・`code-quality-assessment.md`・`component-inventory.md`・`dependencies.md`・`business-overview.md`）。
- `backend/gradle.lockfile` に SLF4J が2つの版で載る（2.0.17 は SpotBugs の道具の構成 `spotbugsSlf4j` だけ、2.0.20 はアプリとテスト）。アプリには効かない。
- E2E の起動（`frontend/playwright.config.ts` の `webServer.command`）は `-Dh2.compactThreads=1` を渡すが、`-Duser.timezone` は渡さない（132〜175 行の `grep`。ブラウザは `timezoneId: 'Asia/Tokyo'`）。`team.md` の「時差の設定を4か所でそろえる」の4か所目（E2E の起動）が何を指すかは、この読みでは決まらない（未検証）。

## Handoff Summary

- **Intent-relevant finding**:
  - Jackson の BOM の上書きは今のビルドに無い（`backend/build.gradle.kts` 46〜96 行・`gradle/libs.versions.toml` の全文。コミット `98ef3e51` で外れた）。Jackson 3.1.7 は Spring Boot 4.1.2 の BOM の値。依頼の「Jackson の上書きの見直し」は、`project.md` の Tech Stack の行を直すことと、Spring Boot を上げたときに 3.2 系へ移るかを見ることに当たる。
  - 固定の2つ（opentelemetry-logback-appender 2.28.1-alpha、networknt 3.0.6）は、版の値（`libs.versions.toml` 33・42 行）と Dependabot の `ignore`（`.github/dependabot.yml` の gradle の項）の2か所にあり、外す時期はどちらも Spring Boot の管理の版（OpenTelemetry 1.63 以上、Jackson 3.2 系以上）に依る。
  - `-Dh2.compactThreads=1` は `Dockerfile` 52 行の `ENTRYPOINT`・`backend/build.gradle.kts` 117 行（テストの JVM）・`frontend/playwright.config.ts` 140 行（E2E の起動）の3か所。`H2CompactThreadsTest` がテストの JVM の値を確かめ、`docs/internal-db.md` 175 行が説明する。
  - コンテナのイメージは版のタグと index のダイジェストで固定し、すべての固定先で同じ値（`grep` で確かめた）: JRE `eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a5…`（`Dockerfile`・`perf/versions.env`・`docs/` の3文書）、JDK `25.0.5_8-jdk-noble@sha256:95e350ad…`（`docker/hikari-pool.sh`・`docs/internal-db.md`・`perf/README.md`）、postgres 18.6・mysql 26.10.0・mariadb 13.0.2（`compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages.java`）、Mailpit v1.33.0（`compose.yaml`・`docker/perf/compose.yaml`・`docs/mail.md`）、otel-lgtm 0.39.0（`compose.yaml`・`perf/README.md`）、otel-collector 0.164.0（`compose.yaml`）、k6 2.3.0（`perf/versions.env`）。
  - `-PitForks` は `backend/build.gradle.kts` 166〜198 行（1 以上の整数でなければ失敗、`maxParallelForks = itForks`、付けなければ1つの JVM）。CI は `IT_FORKS_DEFAULT: "3"`（`ci.yml` 43 行）と手動の入力 `it_forks`（1〜8 を `case` で確かめ、104 行で `./gradlew verify -PitForks="$IT_FORKS"`）。`docs/verification.md` 79〜95 行は手元の実測（N=1〜4、N=3 が最速の 9分43秒）と「CI の上で 2 と 3 を比べるまでは既定 3」を書く。CI の上の比べの結果は、今回読んだファイルには無い。
  - `packagesJudgedByTotal` はソースから消えている（`backend/build.gradle.kts` 298〜304 行のコメントと `scripts/ci-summary.mjs` 35 行のコメント・`docs/verification.md` 22 行に経緯として残るだけ）。知識ベースの古い記述は今回の候補で今の形に直した。
- **Risks / follow-up**:
  - `project.md` の Tech Stack の Jackson の上書きの行と OpenTelemetry の「Spring Boot 4.1.1」は古い（メモリーの書き換えは学びの手順による。この段では直していない）。
  - Spring Boot を上げるときは、Jackson（3.2 系か）と OpenTelemetry（1.63 以上か）の管理の版で2つの固定と Dependabot の `ignore` の要否が決まる。新しい Spring Boot の BOM の中身は Maven Central で確かめる必要がある（今回は外を見ていない）。
  - H2 を上げるときは、`-Dh2.compactThreads=1` の3か所と `H2CompactThreadsTest` を一緒に見直す。
  - `-PitForks` の CI の値の再評価は、CI の実行の時間（試行ごと）を `gh` で読む必要がある（今回は `gh` を使っていない）。並べると JaCoCo の記録は同じ `integrationTest.exec` に追記され、テストの JVM（1 つあたり 1g）と対象DB のコンテナが N 倍になりうる。見立て（未検証）: GitHub の標準の機械の資源（CPU・メモリ）によって 2 と 3 の差が手元と変わりうる。
