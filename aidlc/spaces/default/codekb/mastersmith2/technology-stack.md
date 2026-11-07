# 技術の構成（mastersmith2）

> 2026-11-08（記録のコミット `16f46f1`）の Intent `261107-verify-quick-local` の Focused scan で、今回読んだファイル（`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`.github/workflows/ci.yml`・`README.md`）にある版だけを直した: Node.js は 26（K-81 は解消）、OSV-Scanner は 2.7.0、`gradle/actions/setup-gradle` は v6.4.1。訂正の一覧は「K-118 前の記録の訂正」の節。`gradle/libs.versions.toml`・lockfile・`Dockerfile`・`compose.yaml` は読んでいないため、ほかの表の値は前回のまま。
>
> 前回の記録: 2026-11-06（記録のコミット `a789a6f`）の Intent `261104-tz-options-perf` の Focused scan で、テストの表の Vitest・`@vitest/coverage-v8` の版を lockfile で確かめ（どちらも 5.0.5、変わらず）、「画面の日時と Intl」の節を足した。ほかの表は前回のまま（今回は読み直していない）。
>
> 前回の記録: 2026-11-03（記録のコミット `fa0b01e`）の Intent `261102-timezone-ui-polish` の Focused scan で、時差の設定の置き場（K-95）と make-you-chic-ui の固定先（K-105）だけを足した。版の表は前回（2026-11-01）のまま（今回は読み直していない）。
>
> 前回の記録: 2026-11-01（記録のコミット `6079f13`）の Intent `261101-maintenance-update`（bugfix・Minimal）の Focused scan で更新した。今回は版の定義と固定先（`gradle/`・`libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`package-lock.json`・`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`・`perf/versions.env`・`.github/`・`README.md`）を読み直し、表の版を今の値にそろえ、所見 K-81・K-82・K-84・K-85・K-93 を足した（本文はこの文書）。版がどこに何か所書かれているかの一覧は、この文書の「版の固定先（K-81〜K-85）」の節だけに書く（ほかの文書はここを参照する）。前回（2026-10-29、`261029-reference-lookup-cleanup`）からの差は、画面の部品の版（React 19.3.1・react-router 8.5.1・i18next 26.5.11・react-i18next 17.1.9・oxlint 1.90.0）と make-you-chic-ui の固定先（`cab4207`）。

## 言語と実行環境

| 項目 | 版・値 | 出どころ |
|---|---|---|
| Java | 25 | `libs.versions.toml` の `java`（Gradle の toolchain）、`ci.yml` の `java-version: "25"` |
| 実行のイメージ | `eclipse-temurin:25.0.4.1_1-jre-noble`（index のダイジェストで固定） | `Dockerfile` 17 行 |
| JVM の設定 | `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1` と `MASTERSMITH_JAVA_OPTIONS` | `Dockerfile` 52 行 |
| Node.js | 26（`engines` は `>=26 <27`、`.npmrc` の `engine-strict=true`）。2026-11-08 に確かめた | `frontend/package.json` 7〜9 行、`build.gradle.kts` の `checkToolchain`（95 行の `startsWith("v26.")`）、`ci.yml` 60 行（`node-version: "26"`）、README 14 行 |
| Gradle | 9.8.2（Wrapper） | `gradle/wrapper/gradle-wrapper.properties` |
| TypeScript | 6.0.3（`^6.0.3`） | `frontend/package.json`・lockfile |

## バックエンド

版は `backend/gradle.lockfile` の解決済みの値（2026-11-01 に読んだ）。

| 部品 | 版 | 用途 |
|---|---|---|
| Spring Boot | 4.1.2 | アプリの土台（Spring Framework 7.0.10・Spring Security 7.1.2） |
| Tomcat（組み込み） | 11.0.27（Spring Boot の管理の版） | Servlet の入れ物 |
| Jackson | 3.1.7（Spring Boot の管理の版。上書きは無い。K-85） | JSON |
| Hibernate | 7.4.13.Final | JPA |
| H2 | 2.4.240 | 内部DB（組み込み） |
| Flyway | 12.4.0 | 内部DB の移行 |
| HikariCP | 7.0.2 | 接続のプール（内部DB と対象DB の読み取り・書き込み） |
| mysql-connector-j・mariadb-java-client・postgresql | 9.7.0・3.5.10・42.7.14 | 対象DB の JDBC ドライバー |
| SnakeYAML | 2.7（`libs.versions.toml` で明示） | DSL の YAML |
| networknt json-schema-validator | 3.0.6（固定） | DSL の JSON Schema の検証 |
| cherry-mustache（java-mustache-processor） | 0.1.0（composite build。lockfile に載らない） | メールのテンプレート |
| logstash-logback-encoder・Logback・SLF4J | 9.0・1.5.38・2.0.20 | 構造化ログ |
| opentelemetry-logback-appender-1.0 | 2.28.1-alpha（固定） | ログの外部エクスポート（既定で無効）。OpenTelemetry API・SDK は 1.62.0 |
| Micrometer・Micrometer Tracing | 1.17.2・1.7.2 | 指標とトレース |
| nimbus-jose-jwt | 10.9.1 | JWT |
| jakarta.mail-api・angus-mail | 2.1.5・2.0.5 | メール |

## 画面

版は `frontend/package.json` の範囲と `package-lock.json` の解決済みの値（2026-11-01）。

| 部品 | 範囲／解決済み | 用途 |
|---|---|---|
| React・react-dom | ^19.3.1／19.3.1 | 画面 |
| react-router | ^8.5.1／8.5.1 | 振り分け |
| i18next・react-i18next | ^26.5.11／26.5.11・^17.1.9／17.1.9 | 日英の文言（前の Intent の Dependabot #81・#82 は取り込み済み）。2026-11-08 の `package.json` は `^26.6.0`・`^17.1.10`（lockfile は読んでいない） |
| Vite・@vitejs/plugin-react | ^8.3.5／8.3.5（rolldown 1.2.16）・^6.1.2 | ビルドと開発のサーバー |
| make-you-chic-ui | サブモジュール `cae5fed`（npm の `file:`。2026-11-03。K-105） | デザインシステム（K-86・K-105、`dependencies.md`） |
| @fontsource/noto-sans-jp・noto-serif-jp | ^5.3.1 | 書体 |

## テスト

| 部品 | 版 | 用途 |
|---|---|---|
| JUnit Jupiter・AssertJ・Mockito | 6.0.3・3.27.7・5.23.0（Spring Boot の管理） | Java の単体と結合 |
| Testcontainers | 2.0.5 | 対象DB の3種類をコンテナで |
| jqwik・ArchUnit | 1.10.2・1.5.1 | 性質ベースのテスト・層と境界の検査 |
| subethasmtp | 7.2.2 | テストの中の SMTP の受け手 |
| Vitest・`@vitest/coverage-v8`・jsdom | 5.0.5・5.0.5・30.1.3 | 画面の単体テストとカバレッジ（thresholds） |
| Testing Library（react・dom・jest-dom・user-event）・fast-check・vitest-axe | 16.3.3・10.4.2・7.0.1・14.6.8・4.10.2・0.1.0 | 画面のテスト |
| axe-core | 4.14.0（lockfile の中は 1 つの版） | アクセシビリティの検査（E2E は `e2e/support/axe.ts` で `axe.min.js` を直接読む） |
| `@playwright/test`・playwright | 1.64.1（ブラウザは Chromium だけ） | E2E（`./gradlew e2eTest`、`verify` と CI の外） |
| k6 | `grafana/k6:2.3.0`（`perf/versions.env`、ダイジェストで固定） | 負荷の試験（`perf/`） |

## 検査とビルドの道具

| 道具 | 版 | 用途 |
|---|---|---|
| Gradle（Kotlin DSL） | 9.8.2（Wrapper） | `./gradlew verify` の入口。`gradle.properties` は無く、並列の設定（`org.gradle.parallel`）も結合テストの `maxParallelForks` も無い（2026-11-08） |
| Spotless・palantir-java-format | 8.10.4・2.98.0 | Java の書式とライセンスヘッダー |
| SpotBugs（プラグイン・道具）・FindSecBugs | 6.5.13・4.10.4・1.14.0 | 静的解析 |
| JaCoCo | 0.8.15 | カバレッジ（全体とパッケージごと） |
| Prettier・oxlint・ESLint・Stylelint | 3.9.10・1.90.0・10.13.0・17.16.0 | 画面の書式とリンタ（`eslint-plugin-react-hooks` 7.1.1・`@typescript-eslint/parser` 8.72.1・`stylelint-config-standard` 40.0.0）。2026-11-08 の `package.json` の範囲は ESLint `^10.14.0`（lockfile は読んでいない） |
| Gitleaks・OSV-Scanner | 8.30.1・2.7.0（CI は版と SHA-256 で固定。2026-11-08） | 秘密情報の検出・依存の脆弱性検査（`build.gradle.kts` から呼ぶ） |
| GitHub Actions | checkout v7.0.1・setup-java v6.1.0・setup-node v7.1.0・gradle/actions/setup-gradle v6.4.1・upload-artifact v7.0.2・download-artifact v8.0.2（いずれもコミットの SHA で固定。2026-11-08） | `develop` への push と `v*` のタグで `./gradlew verify` |
| pre-commit | 手元の道具（README は 4.x）。Gitleaks のフックは `rev: v8.30.1` | コミットの前の検査 |

## 版の固定先（K-81〜K-85、2026-11-01）

上げるときに同じ値をそろえる必要のある場所の一覧。行の番号は記録のコミット `6079f13` のもの（Node と OSV-Scanner の行は 2026-11-08 の `16f46f1` で直した）。

| 対象 | 今の版 | 書かれている場所 | Dependabot |
|---|---|---|---|
| Node.js（K-81） | 26（2026-11-08） | `frontend/package.json` 7〜9 行（`engines` `>=26 <27`）・`build.gradle.kts` 72・73 行（入れ方の文言）・90 行（説明）・95 行（`startsWith("v26.")` で拒む）・`.github/workflows/ci.yml` 57 行（名前）・60 行（`node-version: "26"`）・`README.md` 14 行（表） | 知らせない（版の定義の1か所が無い。`.nvmrc` なども無い） |
| Node に依る起動の旗（K-81） | `NODE_OPTIONS=--no-experimental-webstorage` | `frontend/package.json` の `test`・`test:coverage`（22〜23 行。Node 26 でも残っている） | — |
| Java（K-82） | 25 | `gradle/libs.versions.toml` の `java`・`ci.yml` の `java-version` と名前（51 行）・README 3・13 行（表に SDKMAN の `25.0.5.0+8-tem`。2026-11-08） | docker の `eclipse-temurin` の大きな版だけ見送り |
| JRE のイメージ（K-82） | `eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23…` | `Dockerfile` 17 行・`perf/versions.env` 25 行（`PERF_JRE_IMAGE`）・README 407・417・525・994・1391 行ほか（2026-11-01 の README。今の README は 164 行で、版の行は `docs/` に移った見込み。未確認） | `Dockerfile` だけを見る |
| JDK のイメージ（K-82） | `eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:589ff4cc…` | `docker/hikari-pool.sh` 50 行（`JDK_IMAGE`）・README 531・565 行（2026-11-01）・`perf/README.md` 130 行 | 見ない（jre の知らせで手でそろえる） |
| 対象DB のイメージ（K-83） | postgres 18.6・mysql 26.10.0・mariadb 13.0.2 | `compose.yaml` 147・168・188 行・`docker/perf/compose.yaml` 69・96・122 行・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`（`*_VERSION`・`*_DIGEST`） | `compose.yaml` だけを見る |
| Mailpit（K-83） | `axllent/mailpit:v1.32.0@sha256:0790692b…` | `compose.yaml` 121 行・`docker/perf/compose.yaml` 150 行・README 733 行（2026-11-01） | `compose.yaml` だけを見る |
| otel-lgtm（K-83） | `grafana/otel-lgtm:0.38.0@sha256:75cdf892…` | `compose.yaml` 83 行・`perf/README.md` 486 行 | `compose.yaml` だけを見る |
| otel-collector（K-83） | `otel/opentelemetry-collector:0.163.2@sha256:63e5e8ec…` | `compose.yaml` 73 行 | 見る（`-386` のタグは `ignore`） |
| k6（K-83） | `grafana/k6:2.3.0@sha256:9c2dee7f…` | `perf/versions.env` 24 行だけ | 見ない |
| Gitleaks（K-84） | 8.30.1 | `ci.yml` の `GITLEAKS_VERSION`（33 行）と `GITLEAKS_SHA256`・`.pre-commit-config.yaml` の `rev: v8.30.1`・README 15 行（2026-11-08） | 見ない |
| OSV-Scanner（K-84） | 2.7.0（2026-11-08） | `ci.yml` の `OSV_SCANNER_VERSION`（35 行）と `OSV_SCANNER_SHA256`（36 行）・README 16 行 | 見ない |
| GitHub Actions（K-84） | 上の表 | `ci.yml`（コミットの SHA と版のコメント） | 見る |
| Gradle Wrapper（K-84） | 9.8.2 | `gradle/wrapper/gradle-wrapper.properties`（と `gradle-wrapper.jar`） | gradle の項が見るかは未確認 |
| Playwright のブラウザ（K-84） | `@playwright/test` 1.64.1 と同じ版 | `frontend/package.json`・README 19 行（`npx playwright install chromium`。2026-11-08） | npm の項が見る |
| H2 の `-Dh2.compactThreads=1`（K-85） | — | `Dockerfile` 52 行・`backend/build.gradle.kts` 117 行・`frontend/playwright.config.ts` 137 行 | — |

- 固定先の一覧の正本は `.github/dependabot.yml` のコメント（71〜88 行）。今の値どうしは食い違っていない（2026-11-01 に grep で確かめた）。
- `compose.yaml`・`docker/perf/compose.yaml` のアプリのイメージは `mastersmith:${MASTERSMITH_IMAGE_TAG:-local}`（手元で作る）。

## K-85 Spring Boot と連動する固定（2026-11-01 に再確認）

前々回（`261025-deps-perf-carryover`）の見直しで Spring Boot は 4.1.2 に上がり、Tomcat・Jackson の上書きは外れた。今回 `libs.versions.toml`・`backend/build.gradle.kts`・`dependabot.yml` を読み直し、**Jackson の BOM の上書きはどこにも無い**ことを確かめた（lockfile の `jackson-core`・`jackson-databind` 3.1.7 は Spring Boot 4.1.2 の管理の版）。依頼にある「Jackson の BOM の上書き 3.1.7 の見直し」は、対象が既に無い。

| 部品 | 今の固定 | 固定の理由（`libs.versions.toml` のコメントによる） | 外す時期 |
|---|---|---|---|
| opentelemetry-logback-appender | 2.28.1-alpha | Spring Boot の管理の OpenTelemetry が 4.1.2 でも 1.62.0 のまま。2.29 以降は api 1.63 以上を求める | 管理の OpenTelemetry が 1.63 以上になったとき（外部エクスポートを有効にして確かめる） |
| networknt json-schema-validator | 3.0.6 | 3.0.7 以降は Jackson を 3.2 系に引き上げる。4.1.2 でも管理の Jackson は 3.1.7（3.1 系） | 管理の Jackson が 3.2 系以上になったとき |
| H2 の `-Dh2.compactThreads=1` | 3 か所（上の表） | H2 2.4.240 の複数スレッドの詰め直しの競合を避ける（`project.md` の Tech Stack） | H2 を上げるとき要否を見直す |

- 見立て（未検証）: Spring Boot を上げて管理の OpenTelemetry・Jackson の版が変われば、上の2つの固定は外せる。新しい Spring Boot の BOM の版は Maven Central で確かめる必要がある（今回は外を見ていない）。

## K-118 前の記録の訂正（2026-11-08）（`build-and-verify`・`container-runtime`）

- 確かめた事実（今回読んだファイルによる）:
  - Node.js は 26 にそろっている: `frontend/package.json` の `engines` は `>=26 <27`、`build.gradle.kts` の `checkToolchain` は `v26.` で始まらないと失敗（94〜97 行）、入れ方の文言は `node@26`（72〜73 行）、`ci.yml` は `node-version: "26"`、README 14 行の表も 26。K-81 の「Node 26 を拒む」は解消した。
  - OSV-Scanner は 2.7.0（`ci.yml` 35〜36 行、README 16 行）。前の記録の 2.6.0 は古い。
  - `gradle/actions/setup-gradle` は v6.4.1（`ci.yml` 67 行）。前の記録の v6.4.0 は古い。
  - README は 164 行（前の記録の 1,678 行）。前提の道具・取得と準備・開発時の起動・コンテナでの起動と確認・文書の目次・ライセンスだけを持ち、手順の本文は `docs/` の主題ごとの文書へ移った（目次 102〜119 行）。K-88 の行の番号（README の 407・525 行など）は今の README には当たらない。
  - `frontend/scripts/` は 6 ファイル（前の記録は2つ）。`config/` は2ファイル（`license-header.txt`・`npm-build-tools.txt`）。
  - perf の伏せ字の道具の ESLint（`perfToolEslint`）と型の検査（`perfToolTypecheck`）が `verify` の 2・4 の段に入っている（K-90 の T2 は直っている）。
- 未検証: `docs/` へ移った版の行（JRE・JDK・Mailpit など）の今の場所は読んでいない。版の固定先の表の README の行の番号は 2026-11-01 のまま。

## 手元の運用

- コンテナの版の固定先は上の「版の固定先」の表（K-78 の後継の K-83）。前回（2026-10-29）の時点では、動いている Mailpit・止まっている lgtm のタグは `compose.yaml` と一致して見えた（ダイジェストの一致は未検証）。
- **K-93 古いコンテナのイメージの片付け**: 依頼の「古いコンテナのイメージの削除」の対象は、今回は docker の操作をしない決まりのため一覧していない。前の Intent の記録（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/feedback-optimization/feedback-loop.md` の「片付け」）では、名前の無いボリューム（回収できる 12.56GB の多く）・固定先から外れた古い版のイメージ（約 2.8GB）・PC のディスク 88% が残る候補。戻し先のタグ（`pre-…`）を消すかは依頼者の判断。後の段で `docker images`・`docker volume ls` の読み取りの一覧で確かめる。
- 負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`）の対象DB は postgres 18.6・mysql 26.10.0・mariadb 13.0.2・Mailpit v1.32.0（いずれもダイジェストで固定）。上限はアプリ CPU 4・メモリ 2g、対象DB は環境変数で替えられる（既定 512m・768m・512m）。
- 監視は `grafana/otel-lgtm` と OpenTelemetry Collector を compose の profile で起動し、警報とダッシュボードは `docker/monitoring/` のファイルで持つ。メールの受け手（Mailpit）と見本の対象DB も compose の profile。E2E の見本の PostgreSQL は上乗せ `docker/e2e/compose.targetdb-port.yaml` で `127.0.0.1:15432` に開ける。
- 手元の検査の前提（2026-11-08、README 23・24 行）: colima の VM は CPU 4・メモリ 6GiB を勧める。`./gradlew verify` は対象DB の結合テストのためにコンテナの実行環境が動いていることを前提にする。colima の PC では `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す（`project.md` の Testing Posture。README の本文は `docs/target-db.md` に移った見込み。未確認）。

## 時差の設定の置き場（K-95、2026-11-03）

本文は `architecture.md` の「日時の値の流れ」。置き場だけをここに置く（すべて grep で確かめた）。

| 置き場 | 値 | 効く先 |
|---|---|---|
| `Dockerfile` 52 行の `ENTRYPOINT` | `-Duser.timezone=Asia/Tokyo` | 配備した JVM（PgJDBC のセッションの時差もこれに従う見込み、未検証） |
| `compose.yaml` 37 行 | `TZ: Asia/Tokyo` | 配備したコンテナ（Dockerfile とそろえる注記あり） |
| `backend/build.gradle.kts` 118 行 | `systemProperty("user.timezone", "Asia/Tokyo")` | テストの JVM |
| `targetdb/config/TargetDataSourceConfig.java` 61・68 行 | `SET time_zone = '+00:00'`（読み取り・書き込み） | MySQL・MariaDB の対象DB のセッション |
| `frontend/src/shared/format/formatDateTime.ts` | 端末の時差（引数で渡せる） | DSL・招待・利用者の管理の画面の日時 |

- アプリが保存する時刻（内部DB）は時差に依存しない時点（UTC）として扱う決まり（`project.md` の Corrections）。業務データの時差つきの値も UTC にそろえて受け渡す。画面で地方時に直す仕組みは業務データには無い（K-96）。

## 画面の日時と Intl（2026-11-06）

| 項目 | 値 | 出どころ |
|---|---|---|
| 時差の名前の一覧 | `Intl.supportedValuesOf('timeZone')`（Node・ブラウザ同梱の ICU が答える。取り出せなければ空で UTC だけ） | `features/preferences/buildTimeZoneOptions.ts` 35〜41 行 |
| 整形器 | `Intl.DateTimeFormat`、ロケール `en-US-u-ca-gregory-nu-latn`、`hourCycle: 'h23'`、`era`・`year`・`month`・`day`・`hour`・`minute`・`second`（名前ごとに1つ、キャッシュの上限 128） | `shared/format/timeZone/timeZoneGate.ts` 24・27・63〜73 行 |
| 時差の求め方 | `formatToParts` の数字を UTC とみなした秒と元の時点の差（`timeZoneName: 'longOffset'` は使わない。前の Intent の NFR Design の Q1: A） | `shared/format/timeZone/wallClock.ts` の頭の説明と 119〜148 行 |
| テストの実行環境 | Node `>=26 <27`（`engines`）、CI は Node 26・`ubuntu-latest`。`test`・`test:coverage` は `NODE_OPTIONS=--no-experimental-webstorage vitest run`（後者に `--coverage`） | `frontend/package.json` 7〜8・22〜23 行、`.github/workflows/ci.yml` |
| カバレッジ | `@vitest/coverage-v8` 5.0.5（V8 の計測）、`thresholds` 行 80・分岐 70 | `frontend/vitest.config.ts` 66〜77 行、`package-lock.json` 1993 行 |

- 名前の数は ICU の版で変わる（今回は数えていない）。CI の失敗の記録の CPU は AMD EPYC 7763（依頼の記述）。

## Sources

- 2026-11-08: `build.gradle.kts`・`backend/build.gradle.kts`・`settings.gradle.kts`・`frontend/package.json`・`.github/workflows/ci.yml`・`README.md`（どれも全文）、開発担当の調査 `aidlc/spaces/default/intents/261107-verify-quick-local/inception/reverse-engineering/developer-scan.md`。
- 2026-11-06: `frontend/package.json`・`frontend/package-lock.json`（vitest・coverage-v8 の行）・`frontend/vitest.config.ts`・`frontend/src/shared/format/timeZone/{timeZoneGate,wallClock}.ts`・`.github/workflows/ci.yml`（`verify` の job の頭）、開発担当の調査 `aidlc/spaces/default/intents/261104-tz-options-perf/inception/reverse-engineering/developer-scan.md`。
- 2026-11-03: `Dockerfile`・`compose.yaml`・`backend/build.gradle.kts` の時差の grep、`TargetDataSourceConfig.java`、開発担当の調査 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/reverse-engineering/developer-scan.md`。
- `gradle/libs.versions.toml`・`gradle/wrapper/gradle-wrapper.properties`・`backend/gradle.lockfile`・`backend/build.gradle.kts`・`build.gradle.kts`・`frontend/package.json`・`frontend/package-lock.json`・`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`・`docker/hikari-pool.sh`・`perf/versions.env`・`.github/workflows/ci.yml`・`.github/dependabot.yml`・`README.md`（2026-11-01、開発担当の調査 `aidlc/spaces/default/intents/261101-maintenance-update/inception/reverse-engineering/developer-scan.md`）。`TargetDbImages.java`・`.pre-commit-config.yaml`・`frontend/playwright.config.ts` は版の行の grep だけ。
