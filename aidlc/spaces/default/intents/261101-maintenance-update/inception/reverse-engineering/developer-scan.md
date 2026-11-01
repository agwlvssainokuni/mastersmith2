# 開発担当のコードスキャン（Intent 261101-maintenance-update）

- 実施日: 2026-11-01
- 記録するコミット: `6079f13826853c7564ac284cad43f6d63311c09b`（`develop` の HEAD。スナップショットの head_commit と同じ）
- 走査の広さ・深さ: 範囲を絞ったスキャン（Focused scan）・Minimal。既存の知識ベースは STALE。
- 確かめ方: ファイルの読み取り（cat・sed・grep・ls・wc）と、読み取りの git（`rev-parse`・`submodule status`）だけ。ビルド・テスト・`gradlew`・docker の操作はしていない。`frontend/package-lock.json` の版は、lockfile を読むだけの短い node の式で取り出した（書き込みなし）。`.env`・`.env.targetdb`・`reference/`・`vendor/` の中は開いていない。

## Developer Code Scan Results

### Scan Coverage
- **Analyzed deeply**:
  - gradle/（`libs.versions.toml` の全文、`wrapper/gradle-wrapper.properties`）
  - build.gradle.kts（全文 812 行）
  - settings.gradle.kts（全文）
  - backend/build.gradle.kts（全文 414 行）
  - backend/gradle.lockfile（主な部品の解決済みの版を grep で）
  - backend/config/spotbugs-exclude.xml（除外の形）
  - frontend/package.json・frontend/package-lock.json（解決済みの版）・frontend/.npmrc・frontend/vitest.setup.ts・frontend/vite.config.ts
  - Dockerfile・compose.yaml（`image:`・profile・`mem_limit` の行）・.gitmodules
  - docker/perf/compose.yaml・docker/e2e/compose.targetdb-port.yaml・docker/hikari-pool.sh（版の固定の行）
  - .github/（`workflows/ci.yml` と `dependabot.yml` の全文）
  - config/（`npm-build-tools.txt`・`license-header.txt` の有無）
  - perf/README.md（見出しの構成と「履歴」の節）・perf/versions.env・perf/redact-k6-evidence.mjs（先頭の口と定数）
  - README.md（見出しの構成、「前提の道具」と「取得と準備」の全文、版の固定の行の grep）
  - backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java（225〜255・320〜385・795〜935 行）・repository/RowSqlBuilder.java（140〜180・330〜348 行）・repository/RowDialect.java（110〜160 行）
- **Skimmed only**:
  - frontend/e2e/（ファイルの一覧と `support/axe.ts` の規則の grep）
  - frontend/scripts/（`check-license-header.mjs` の対象の説明だけ）
  - frontend/playwright.config.ts・frontend/vitest.config.ts（スナップショットの範囲の外。版・`webServer` の起動の引数・カバレッジの `include` の grep だけ）
  - docker/ の残り（`monitoring/`・`otel-collector/`・`targetdb/`・`jmx/`・`check-container-limits.sh`。名前と版の固定の grep だけ）
  - perf/ の残り（`k6/scenarios.js` 2,848 行・`biz/`・`lib/`・`ui/`・`*.sh`・`*.mjs`。一覧と版の grep だけ）
  - backend/src/main/java/cherry/mastersmith/businessdata/ の残り（ファイルの一覧と WARN のログの grep）
  - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java（スナップショットの範囲の外。版とダイジェストの定数の行の grep だけ）
  - .pre-commit-config.yaml（スナップショットの範囲の外。`rev:` の行の grep だけ）
  - frontend/src/perfEvidence*.test.ts（範囲の外。ファイルの名前の grep だけ）
  - vendor/make-you-chic-ui/・vendor/java-mustache-processor/（`git submodule status` の固定先のハッシュだけ）
- **Left out**:
  - build/・backend/build/・frontend/dist/・frontend/coverage/・frontend/test-results/（ビルドとテストの出力）
  - frontend/node_modules/・vendor/ の中（依存）
  - .gradle/・.idea/（道具のキャッシュ）
  - .env・.env.targetdb・reference/（秘密と参考資料。開かない）
  - .claude/・aidlc/（AI-DLC 自身）

### Packages Found
- backend — Gradle サブプロジェクト — Java 25 — Spring Boot 4.1.2 の実行可能 WAR（画面の `dist` を同梱）
- frontend — npm のパッケージ `mastersmith-frontend` — TypeScript 6.0.3・React 19.3.1 — 画面（Vite 8.3.5 でビルド）
- vendor/make-you-chic-ui — Git サブモジュール（npm の `file:` の依存）— TypeScript — デザインシステム。固定先 `cab42074cf040e3c03127db8c92582cc329f934d`
- vendor/java-mustache-processor — Git サブモジュール（Gradle の composite build）— Java — メールのテンプレートのエンジン。固定先 `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（タグ 0.1.0）
- perf — 負荷の試験の台本と手順（k6・シェル・Node の `.mjs`）
- docker — コンテナの設定（使い捨ての環境・E2E の上乗せ・監視・見本の対象DB・JMX の道具）

### Build System
- **Type**: Gradle（Kotlin DSL）＋ npm。入口は `./gradlew verify`（10 段）と `./gradlew e2eTest`。
- **Config Files**: `settings.gradle.kts`（取得元を Maven Central だけに固定、`includeBuild("vendor/java-mustache-processor")`）、`build.gradle.kts`（verify の段・npm の呼び出し・Gitleaks・OSV-Scanner・perf の道具の Prettier と oxlint・E2E の見本の PG の準備と片付け）、`backend/build.gradle.kts`（依存・テストの分け方・JaCoCo・Spotless・SpotBugs の関門・bootWar）、`gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`（Gradle 9.8.2）、`backend/gradle.lockfile`・`settings-gradle.lockfile`、`frontend/package.json`・`package-lock.json`（lockfileVersion 3）・`.npmrc`（`engine-strict=true`・`ignore-scripts=true`）。
- **Build Dependencies**: `backend:bootWar` → `:frontendBuild` → `vendorBuild` → `vendorInstall`（make-you-chic-ui の `npm ci`）。`backend` → composite build の `cherry-mustache-core`。`verify` の段は 0 準備 → 1 フォーマット → 2 リンタ → 3 ライセンス → 4 ビルド → 5 単体 → 6 結合 → 7 カバレッジ → 8 安全 → 9 成果物。

### APIs Discovered
- REST（Spring MVC）— `businessdata/web`（`/api/tables`）— 今回は API の形は読んでいない（前回の記録 `api-documentation.md` のまま）。今回の Intent は API を変えない見込み。

### Frameworks & Libraries
バックエンド（`libs.versions.toml` と `backend/gradle.lockfile` の解決済みの版）:
- Spring Boot — 4.1.2 — 土台（`spring-boot-starter:4.1.2`、Spring Framework 7.0.10、Spring Security 7.1.2）
- Tomcat（組み込み）— 11.0.27 — Spring Boot の管理の版
- Jackson — 3.1.7（`tools.jackson.core:jackson-core`・`jackson-databind`）— Spring Boot の管理の版。**`libs.versions.toml` にも `backend/build.gradle.kts` にも Jackson の BOM の上書きは無い**（依頼の「Jackson の BOM の上書き 3.1.7」は既に外れている）
- Hibernate — 7.4.13.Final、HikariCP — 7.0.2、Flyway — 12.4.0、H2 — 2.4.240、Micrometer — 1.17.2、Micrometer Tracing — 1.7.2、Logback — 1.5.38、SLF4J — 2.0.20
- OpenTelemetry API・SDK — 1.62.0（Spring Boot の管理）
- opentelemetry-logback-appender-1.0 — 2.28.1-alpha（固定）
- networknt json-schema-validator — 3.0.6（固定）、SnakeYAML — 2.7（明示）
- nimbus-jose-jwt — 10.9.1、jakarta.mail-api — 2.1.5・angus-mail — 2.0.5
- JDBC: mysql-connector-j 9.7.0・mariadb-java-client 3.5.10・postgresql 42.7.14
- logstash-logback-encoder — 9.0、cherry-mustache — 0.1.0（composite build。lockfile に載らない）
- テスト: JUnit Jupiter 6.0.3・AssertJ 3.27.7・Mockito 5.23.0・Testcontainers 2.0.5・jqwik 1.10.2・ArchUnit 1.5.1・subethasmtp 7.2.2
- 道具: Spotless 8.10.4・palantir-java-format 2.98.0・SpotBugs プラグイン 6.5.13・SpotBugs 4.10.4・FindSecBugs 1.14.0・JaCoCo 0.8.15

画面（`package.json` の範囲／lockfile の解決済みの版）:
- react・react-dom — ^19.3.1／19.3.1、react-router — ^8.5.1／8.5.1、i18next — ^26.5.11／26.5.11、react-i18next — ^17.1.9／17.1.9
- vite — ^8.3.5／8.3.5（rolldown 1.2.16）、@vitejs/plugin-react ^6.1.2
- typescript — ^6.0.3／6.0.3、@typescript-eslint/parser ^8.72.1／8.72.1
- vitest・@vitest/coverage-v8 — ^5.0.5／5.0.5、jsdom ^30.1.3、Testing Library（react ^16.3.3・dom ^10.4.2・jest-dom ^7.0.1・user-event ^14.6.8）、fast-check ^4.10.2、vitest-axe ^0.1.0
- axe-core — ^4.14.0／4.14.0（lockfile の中は 1 つの版）
- @playwright/test・playwright・playwright-core — ^1.64.1／1.64.1（ブラウザは Chromium だけ）
- eslint ^10.13.0・eslint-plugin-react-hooks ^7.1.1・oxlint ^1.90.0・prettier ^3.9.10・stylelint ^17.16.0（stylelint-config-standard ^40.0.0）
- @types/node ^26.6.6、@fontsource/noto-sans-jp・noto-serif-jp ^5.3.1
- `braces` 3.0.3（frontend の lockfile にもある。A2 は make-you-chic-ui の lockfile の件）

コンテナと外部の道具:
- eclipse-temurin `25.0.4.1_1-jre-noble`（Dockerfile・`perf/versions.env`・README）と `25.0.4.1_1-jdk-noble`（`docker/hikari-pool.sh`・README・perf/README）
- postgres 18.6・mysql 26.10.0・mariadb 13.0.2・axllent/mailpit v1.32.0・grafana/otel-lgtm 0.38.0・otel/opentelemetry-collector 0.163.2・grafana/k6 2.3.0（いずれも index のダイジェストで固定）
- Gitleaks 8.30.1・OSV-Scanner 2.6.0（CI は版と SHA-256 で固定）
- GitHub Actions: checkout v7.0.1・setup-java v6.1.0・setup-node v7.1.0・gradle/actions/setup-gradle v6.4.0・upload-artifact v7.0.2・download-artifact v8.0.2（コミットの SHA で固定）

### Test Coverage
- **Test Directories**: `backend/src/test/java/cherry/mastersmith/`（`*Test` は `test`、`*IT` は `integrationTest`）、`frontend/src/**/*.test.ts(x)`、`frontend/e2e/support/**/*.test.ts`、`frontend/e2e/*.e2e.ts`（22 本、010〜195）
- **Test Frameworks**: JUnit 5（Jupiter 6.0.3）・Spring Boot Test・Testcontainers・jqwik・ArchUnit／Vitest・Testing Library・vitest-axe・fast-check／Playwright と axe-core
- **Coverage Config**: present。JaCoCo（全体とパッケージごとに行 80%・分岐 70%、`packagesJudgedByTotal` 7 パッケージ）、Vitest の `thresholds`（`lines: 80`・`branches: 70`、`coverage.include` は `src/**/*.{ts,tsx}` だけ）

### Code Quality Indicators
- **Linting**: Spotless（palantir-java-format・ライセンスヘッダー）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`、`spotbugsGate`）、Prettier（`frontend/.prettierrc.json`）・oxlint（`frontend/.oxlintrc.json`）・ESLint（`frontend/eslint.config.js`）・Stylelint。perf の伏せ字の道具には Prettier と oxlint だけが当たる（`perfToolFormatCheck`・`perfToolLint`）。
- **CI/CD**: `.github/workflows/ci.yml`（`develop` への push と `v*` のタグで `./gradlew verify`、`timeout-minutes: 60`、WAR を成果物に保存、タグではリリースを作る）。`.github/dependabot.yml`（gradle・npm・github-actions・docker・docker-compose、weekly）。
- **Documentation**: `README.md` 1,678 行、`perf/README.md` 1,753 行。どちらも Intent・単位・Bolt の経緯が本文と見出しに多く入っている（下の負債）。

### Technical Debt Signals
- **TD-1 Node の版が 6 か所に散らばる**: `frontend/package.json` の `engines`（`>=24 <25`、`engine-strict=true` で強制）、`build.gradle.kts` の `checkToolchain`（95 行の `startsWith("v24.")` で強制）と `toolGuides`（72・73 行の文言）と説明（90 行）、`.github/workflows/ci.yml`（57 行の名前・60 行の `node-version: "24"`）、`README.md`（12 行の表・20 行の説明）。`.nvmrc` などの版の定義の1か所は無い。Node 26 へ上げるには全部をそろえる必要がある。
- **TD-2 Node 24 に依る起動の引数**: `frontend/package.json` の `test`・`test:coverage` が `NODE_OPTIONS=--no-experimental-webstorage` を付ける（Node の Web Storage の実験の機能を切る旗）。Node 26 で同じ旗が受け付けられるか・要るかは未検証（Node が旗を知らないと起動に失敗しうる）。
- **TD-3 Java の版の固定先**: `libs.versions.toml` の `java = "25"`（toolchain）、`ci.yml` の `java-version: "25"`、Dockerfile の `eclipse-temurin:25.0.4.1_1-jre-noble`、`perf/versions.env` の `PERF_JRE_IMAGE`、`docker/hikari-pool.sh` の `JDK_IMAGE`、README の jre 4 か所と jdk 2〜3 か所、perf/README の jdk 1 か所、README の表の SDKMAN の `25.0.4-tem`。jdk のイメージは Dependabot の知らせが来ない（`dependabot.yml` 82 行のコメント）。
- **TD-4 対象DB などのコンテナの版の固定先が複数ある**: postgres・mysql・mariadb は `compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages.java`（`*_VERSION` と `*_DIGEST`）の3か所。Mailpit は `compose.yaml`・`docker/perf/compose.yaml`・README（733 行）。otel-lgtm は `compose.yaml`・perf/README（486 行）。k6 は `perf/versions.env` だけ（Dependabot が見ない）。一覧は `dependabot.yml` のコメント（71〜88 行）にあり、今の値どうしは食い違っていない（grep で確かめた）。
- **TD-5 Gitleaks の版が2か所**: `.github/workflows/ci.yml` の `GITLEAKS_VERSION: 8.30.1`（と SHA-256）と `.pre-commit-config.yaml` の `rev: v8.30.1`。README の表（13 行）も 8.30.1。OSV-Scanner 2.6.0 も `ci.yml` と README の2か所。手元の道具（brew）の版はこの固定と別に動く。
- **TD-6 README の固定先の記述が古い**: README 37 行は「make-you-chic-ui の固定先は `ecb07e9`」と経緯の連なりを書くが、`git submodule status` の実際は `cab4207`（前の Intent 261029-reference-lookup-cleanup の後に上げた）。README 5 行の「現在の内容はアプリの骨格（U1）です」も今の内容と合わない。
- **TD-7 README.md と perf/README.md に経緯が溜まっている**:
  - README.md 1,678 行。`##`・`###` の見出しのうち 25 個が Intent・単位（U1〜U8）・Bolt を名前に含む。経緯の文（Intent・単位・Bolt・履歴の参照）は本文に 164 か所。「契約との差」の節が3つ（1457・1487・1604 行）、「後の単位（U2・U3・U4）が使う差し込み口」（1608〜1634 行）は骨格の時点の説明。make-you-chic-ui の固定先の経緯の連なり（37 行、10 段）。単位ごとの節（「メール（U1）」「監査ログ（U4）」「招待の管理の画面（U5）」など）が機能の別ではなく作った順に並ぶ。
  - perf/README.md 1,753 行。末尾の「## 履歴」の節（1563〜1753 行、約 190 行。H1〜H65 前後の番号付きの経緯と見出しの対応表）は前の Intent で本文から移したもの。本文にも経緯の参照が 139 か所残る。手順の本文（60〜1470 行）は場面ごとの手順（`0・1`・`1''`・`3''''` などの継ぎ足しの番号）が多い。
- **TD-8 Jackson の上書きは既に無い**: 依頼の「Jackson の BOM の上書き 3.1.7 の見直し」は対象が無い（`grep` で上書き・`platform` の読みは見当たらない。lockfile の 3.1.7 は Spring Boot 4.1.2 の管理の版）。`dependabot.yml` の Jackson の `ignore` も無い。
- **TD-9 Spring Boot に連動する固定が2つ残る**: `opentelemetry-logback-appender` 2.28.1-alpha（OpenTelemetry 1.62.0 に合わせる。外す時期: 管理の OpenTelemetry が 1.63 以上）と networknt 3.0.6（外す時期: 管理の Jackson が 3.2 系以上）。理由と時期は `libs.versions.toml` と `dependabot.yml` のコメントに書いてある。Spring Boot を上げたときに満たすかは Maven Central の BOM で確かめる必要がある（未検証）。
- **TD-10 H2 の `-Dh2.compactThreads=1` が3か所**: Dockerfile の ENTRYPOINT、`backend/build.gradle.kts` のテストの JVM（117 行の `systemProperty`）、`frontend/playwright.config.ts` の `webServer` の起動の引数（137 行）。H2 を上げるときに要否を見直す（`project.md` の Tech Stack）。H2 は 2.4.240 のまま。
- **TD-11 perf の伏せ字の道具の検査の抜け（T1・T2）**: 道具 `perf/redact-k6-evidence.mjs`（907 行）のテストは `frontend/src/perfEvidence*.test.ts` の3つで Vitest に流れるが、`frontend/vitest.config.ts` の `coverage.include` は `src/**/*.{ts,tsx}` だけのため、道具のカバレッジは下限の判定に入らない（T1）。`build.gradle.kts` は道具に Prettier と oxlint だけを当て（`perfToolFormatCheck`・`perfToolLint`）、ESLint と `tsc` は当てない（T2）。
- **TD-12 書き込みの参照の段の `stage` の無い WARN（T3）**: `BusinessDataWriteService.warnReference`（908〜931 行）は `stage` が null のときキーを出さない。`StepAttempt.Unexpected`（364〜372 行）の `unexpected.stage()` が null の経路で「保存の前に対象DB の参照先を読めませんでした」の WARN が `stage` 無しで出る（値は出ない）。どの経路で null になるかは読んでいない（未検証）。
- **TD-13 一覧の並べ替えと MySQL の 1038（P1）**: 参照先の読み取りの並びは `RowDialect.sortExpression`（145〜151 行）で文字の型を先頭 `LIST_TEXT_LIMIT` 字の `SUBSTRING` にして並べ、MySQL の並べ替えの領域の不足（1038）を避けている。一方、業務データの一覧のページの文（`RowSqlBuilder.page`、150〜173 行）の `orderBy`（337〜347 行）はカラムをそのまま `orderTerm` に渡し、`LIMIT ? OFFSET ?` で読む。深い `OFFSET` や1ページの件数の大きい形で 1038 が起きるかは未検証（見立て: 主キーだけを先に並べる内側の問い合わせでも、並べ替えの列が長い文字なら並べ替えの領域を使う）。
- **TD-14 コンテナのイメージの片付け（依頼の「古いイメージの削除」）**: 手元にどのイメージが残っているかは、docker の操作をしない決まりのため今回は見ていない。前の Intent の記録（`feedback-loop.md` の片付け）では、名前の無いボリューム・固定先から外れた古い版のイメージ（約 2.8GB）・PC のディスク 88% が候補として残る。

## Handoff Summary
- **Intent-relevant finding**: 版の固定先が分散しており、上げるたびに複数のファイルをそろえる必要がある。Node は6か所（`frontend/package.json` 8 行の `engines`、`build.gradle.kts` 72・73・90・95 行、`ci.yml` 57・60 行、`README.md` 12・20 行）で、`build.gradle.kts` 95 行の `startsWith("v24.")` と `engines` の `<25` は Node 26 を拒む。対象DB のイメージは `compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages.java` の3か所、JRE・JDK は Dockerfile・`perf/versions.env`・`docker/hikari-pool.sh`・README・perf/README に分散（一覧は `dependabot.yml` 71〜88 行のコメント）。README.md（1,678 行）と perf/README.md（1,753 行、末尾の履歴 1563〜1753 行）は経緯の記述が多く、README 37 行の make-you-chic-ui の固定先 `ecb07e9` は実際の `cab4207` と食い違う。
- **Risks / follow-up**:
  - 依頼の「Jackson の BOM の上書き 3.1.7」は既に無い（`libs.versions.toml`・`backend/build.gradle.kts`・`dependabot.yml` に無く、lockfile の 3.1.7 は Spring Boot の管理の版）。要件定義で対象から外すか確かめる。
  - `NODE_OPTIONS=--no-experimental-webstorage`（`frontend/package.json` の `test` と `test:coverage`）が Node 26 で使えるかは未検証。
  - make-you-chic-ui の固定先は `cab4207`（前回の記録 `ecb07e9` から進んでいる）。依頼（5）・`braces` の取り込みは上流のソースで確かめる必要がある（`vendor/` の中は今回読んでいない）。
  - スナップショットの範囲の外で今回の Intent に関わる固定先がある: `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`（対象DB の版とダイジェスト）、`frontend/playwright.config.ts`（`-Dh2.compactThreads=1`）、`frontend/vitest.config.ts`（T1 の `coverage.include`）、`.pre-commit-config.yaml`（Gitleaks の `rev`）、`frontend/src/perfEvidence*.test.ts`（T1 のテスト）、`frontend/eslint.config.js`・`frontend/tsconfig.json`（T2）。どれも grep の流し読みだけで、深く読んでいない。深く読む必要があれば範囲を広げたスナップショットが要る。
  - 手元の docker のイメージとボリュームの今の状態（片付けの候補）は見ていない。後の段で読み取りの一覧で確かめる。
