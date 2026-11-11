# コード生成の作業の記録（Intent 261110-deps-forks-review）

計画 `code-generation-plan.md` の ① の作業の記録。版はすべて配布元（Maven Central・Gradle Plugin Portal・services.gradle.org・npm）で 2026-11-11 に確かめた。作業のブランチは `feature/261110-deps`（`develop` の `2ef7690d` から作成）。

## Step 1: 前の版と、最新の版の確かめ（FR2.1・FR5.2）

### 前の版（変える前の `develop`。`backend/gradle.lockfile`・`gradle/libs.versions.toml`・`Dockerfile`）

| 対象 | 前の版 | 出どころ |
|---|---|---|
| Spring Boot | 4.1.2 | `gradle/libs.versions.toml` の `spring-boot` |
| JRE のイメージ | `eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a5…` | `Dockerfile` の `FROM`（② で扱う） |
| H2 | 2.4.240 | lockfile（Spring Boot の BOM の管理） |
| OpenTelemetry（api） | 1.62.0（api-incubator 1.62.0-alpha） | lockfile（Spring Boot の BOM の管理） |
| Jackson（databind） | 3.1.7 | lockfile（Spring Boot の BOM の管理。上書きは無い） |
| opentelemetry-logback-appender | 2.28.1-alpha | `libs.versions.toml`（固定） |
| networknt json-schema-validator | 3.0.6 | `libs.versions.toml`（固定） |

### 配布元の最新の安定版（プレリリース・RC を除く）

| 対象 | 最新の安定版 | 備考 |
|---|---|---|
| Spring Boot（`spring-boot-dependencies`） | **4.1.2**（今と同じ） | Maven Central の最新は `4.2.0-RC2` だが RC のため対象外 |
| 4.1.2 の BOM が管理する OpenTelemetry | 1.62.0 | `opentelemetry.version` |
| 4.1.2 の BOM が管理する Jackson | 3.1.7 | `jackson-bom.version` |
| 4.1.2 の BOM が管理する H2 | 2.4.240 | `h2.version` |
| opentelemetry-logback-appender | 2.33.1-alpha | `opentelemetry-api` 1.67.0 を求める |
| networknt json-schema-validator | 3.0.9 | `version.jackson` は **3.1.8**（3.0.7・3.0.8 は 3.2.1、3.0.6 は 3.1.4） |

要点:

- Spring Boot の最新の安定版は今の 4.1.2 のままのため、Step 3 は変更なしになる。BOM の管理の版（OpenTelemetry 1.62.0・Jackson 3.1.7・H2 2.4.240）も変わらない。
- そのため Step 5 の固定を外す前提（OpenTelemetry 1.63 以上・Jackson 3.2 系以上・H2 が 2.4.240 から上がる）はどれも満たされない。
- 注目点: networknt の最新 3.0.9 は Jackson 3.1.8（3.1 系のパッチ）を求める。固定の理由だった「3.2 系に引き上げる」は 3.0.9 では当たらないが、BOM の 3.1.7 から 3.1.8 へ引き上げる（`platform` は強制しないため、Gradle の解決で 3.1.8 が勝つ）。計画の Step 5 (b) の条件（管理の Jackson が 3.2 系以上）は満たさないため、計画どおり上げない。扱いは依頼者に諮る（下の「依頼者に諮りたいこと」）。

## Step 2: 変える前の木での絞ったテスト（runner_step・NFR3）

変える前の木（`develop` の `2ef7690d` と同じ中身の `feature/261110-deps`）で、`unit-test-instructions.md` の 1 のコマンドを流した。始めに colima が止まっていたため `colima start` で起動した（team.md の Way of Working のとおり、コンテナの実行環境を起動してから流した。起動のときに docker の context `colima` が作られ、今の context になった）。

| コマンド | 結果 | 時間 |
|---|---|---|
| `DOCKER_HOST=… TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=… caffeinate -dims ./gradlew verifyQuick -PitTests='OtlpLogExportIT,ExternalExportIT,H2CompactionByPoolSuspensionIT'` | BUILD SUCCESSFUL。`OtlpLogExportIT` 4件（入れ子の2件を含む）・`ExternalExportIT` 1件・`H2CompactionByPoolSuspensionIT` 3件、すべて通過（飛ばし 0） | 224 秒（Gradle の表示 3分43秒） |
| `./gradlew :backend:test --tests 'cherry.mastersmith.config.H2CompactThreadsTest'` | BUILD SUCCESSFUL。1件通過 | 2 秒 |

流す前の `pmset -g assertions` は `PreventUserIdleSystemSleep 1`（caffeinate が効いた状態）。Step 2〜6 の検査（08:34〜08:56）の後に `pmset -g log` を見て、最後の Wake は 08:09 で、その後に Sleep・DarkWake は無かった。

## Step 3: Spring Boot（FR1.3）

- Maven Central の最新の安定版は 4.1.2（今と同じ。`4.2.0-RC2` は RC のため対象外）。`gradle/libs.versions.toml` は変えない。Jackson の上書きは今も無く、加えない。
- そのため、Build and Test の負荷の試験の分岐（FR5）の「Spring Boot」は「変わらない」。H2 も 2.4.240 のまま（JRE のイメージは ② の Step 11 で決まる）。

## Step 4: backend の Gradle の依存と wrapper（FR1.4・FR1.5・FR1.8）

版は Maven Central（ライブラリ・道具）と Gradle Plugin Portal（プラグイン）と services.gradle.org（Gradle）で、プレリリースを除いた最新の版を確かめた。

| 対象 | 前 | 後 | 備考 |
|---|---|---|---|
| Gradle Wrapper | 9.8.2 | **9.9.1** | `./gradlew wrapper --gradle-version 9.9.1` を2回流し、`gradle-wrapper.jar` も更新。jar の SHA-256 `621008af…cdaf8` は services.gradle.org の `gradle-9.9.1-wrapper.jar.sha256` と一致 |
| Spotless（プラグイン `com.diffplug.spotless`） | 8.10.4 | **8.11.0** | `libs.versions.toml` の `spotless`。Apache License 2.0 のまま |
| palantir-java-format | 2.103.0 | 2.103.0 | 最新と同じ |
| SpotBugs のプラグイン・本体 | 6.5.13・4.10.5 | 同じ | 最新と同じ |
| FindSecBugs | 1.14.0 | 1.14.0 | 最新と同じ |
| JaCoCo | 0.8.16 | 0.8.16 | 最新と同じ |
| logstash-logback-encoder | 9.0 | 9.0 | 最新と同じ |
| jqwik・ArchUnit | 1.10.2・1.5.1 | 同じ | 最新と同じ |
| snakeyaml | 2.7 | 2.7 | 最新と同じ |
| subethasmtp | 7.2.2 | 7.2.2 | 最新と同じ |
| Testcontainers・JDBC ドライバーなど | BOM の管理 | 同じ | Spring Boot 4.1.2 の BOM のまま（Testcontainers 2.0.5） |

- `./gradlew :backend:resolveAndLockAll --write-locks` を流し、`backend/gradle.lockfile`・`settings-gradle.lockfile` に差は出なかった（ライブラリの版が変わらず、Spotless はプラグインで lockfile の対象の外）。新しく増えた依存は無く、ライセンスの確かめが要るものは無い。
- 変えた後のテスト: Step 2 と同じ2つのコマンドを流し、どちらも BUILD SUCCESSFUL（`verifyQuick -PitTests=…` は 328 秒（5分27秒、Gradle 9.9.1 の初回の起動とキャッシュの作り直しを含む）。結合テストの 8 件と `H2CompactThreadsTest` 1 件はすべて通過・飛ばし 0）。Spotless 8.11.0 の `spotlessCheck` も通り、整形による差は出なかった。
- 古い値（`8.10.4`・`9.8.2`）が `vendor/`・`aidlc/`・生成物の外に残っていないことを `grep` で確かめた。

## Step 5: 連動する固定（FR2.2・FR2.3・FR2.4）

Step 1・3 の結果、どの固定も外す前提を満たさないため、計画どおりどれも外さない。固定の理由を確かめる既存のテストの期待（`H2CompactThreadsTest` の値など）は変えていない。

| 固定 | 外す前提 | 今の値 | 結果 |
|---|---|---|---|
| (a) opentelemetry-logback-appender 2.28.1-alpha | 管理の OpenTelemetry が 1.63 以上 | 1.62.0（Spring Boot 4.1.2） | 残す。appender の最新 2.33.1-alpha は api 1.67.0 を求める。外部エクスポートの手元の起動は不要（上げないため） |
| (b) networknt 3.0.6 | 管理の Jackson が 3.2 系以上 | 3.1.7（Spring Boot 4.1.2） | 残す。最新 3.0.9 は Jackson 3.1.8 を求める（下の「依頼者に諮りたいこと」1） |
| (c) `-Dh2.compactThreads=1`（5か所） | H2 が 2.4.240 から上がる | 2.4.240（Spring Boot 4.1.2） | 残す。5か所は変えない |

変えたもの:

- `gradle/libs.versions.toml`: appender と networknt のコメントに、2026-11-11 の見直しの結果（今の値と最新の版が求める版）を足した。版の値は変えない。
- `.github/dependabot.yml`: gradle の `ignore` の説明に、同じ見直しの結果を足した。`ignore` の行は残す（Step 7）。

外せなかった理由（`project.md` の学びの言い方。③ の Step 14 で `project.md` の Tech Stack に反映する文面の材料）:

- opentelemetry-logback-appender の 2.28.1-alpha の固定は残した。2026-11-11 の時点で Spring Boot の最新の安定版は 4.1.2 のまま（OpenTelemetry 1.62.0）で、appender の 2.29 以降は api 1.63 以上を求めるため。
- networknt json-schema-validator の 3.0.6 の固定は残した。Spring Boot 4.1.2 の管理の Jackson は 3.1.7 で、最新の 3.0.9 は Jackson 3.1.8 を求め、管理の版から引き上げるため（3.0.7・3.0.8 は 3.2.1 を求めた）。
- `-Dh2.compactThreads=1` は残した。Spring Boot 4.1.2 の管理の H2 が 2.4.240 のままのため。

## Step 6: frontend の npm の依存（FR1.4・FR1.4a・FR1.5・FR1.7・FR1.8）

`npm outdated` で一覧し、`npm install <名前>@<版>`（`frontend/.npmrc` の `ignore-scripts=true` のまま）で `package.json` と `package-lock.json` を更新した。小さな版をまとめて入れた後に `npm update` で推移依存も範囲の中で最新にし、大きな版は1組ずつ入れて型の検査・リンタ・画面のテストを流した。版は npm の registry で確かめた。

### 小さな版（まとめて）

| パッケージ | 前 | 後 |
|---|---|---|
| `@playwright/test` | 1.64.1 | 1.65.0（ブラウザも `npx playwright install chromium` で同じ版の Chromium（chromium-1254）を入れた） |
| `@testing-library/user-event` | 14.6.8 | 14.6.9 |
| `@typescript-eslint/parser` | 8.72.1 | 8.72.2 |
| `@vitejs/plugin-react` | 6.1.2 | 6.3.0 |
| `@vitest/coverage-v8`・`vitest` | 5.0.5 | 5.1.0 |
| `oxlint` | 1.90.0 | 1.93.0 |
| `vite` | 8.3.5 | 8.3.7 |

結果: 型の検査・リンタ・CSS のリンタ・画面のテスト（274 ファイル・3,063 件）すべて通過。

### 大きな版（1組ずつ）

| パッケージ | 前 | 後 | 結果 |
|---|---|---|---|
| `i18next`・`react-i18next`（peer で組） | 26.6.0（lock は 26.7.2）・17.1.10 | **27.1.1・18.0.1** | 型の検査で 2 ファイル 3 か所が崩れた（下）。直して全部通過 |
| `jsdom` | 30.1.3 | **31.0.0** | そのまま通過（peer の `@napi-rs/canvas` は入れない。前の版の `canvas` と同じく、無いことの案内が出るだけ） |
| `stylelint`・`stylelint-config-standard` | 17.16.0・40.0.0 | **18.0.0・41.0.0** | `lint:css` がそのまま通過 |
| `typescript` | 6.0.3 | 6.0.3（見送り） | 基準 3「上流の未対応」。`@typescript-eslint/parser` の最新 8.72.2 の peer が `typescript >=4.8.4 <6.1.0` のまま（registry の `peerDependencies` で確かめた）。6 系の最新は 6.0.3 で今と同じ |

i18next 27 の直し（基準 1 の範囲の中: アプリの本体 2 ファイル。画面の見た目・API・決まりは変えない）:

- 原因: i18next 27 の `TFunction` の引数の型が広くなり（`strictInterpolationChecks` の既定の変更など）、`(key: string, options?: Record<string, string>) => string` の形の引数へそのまま渡せなくなった。
- 直し: `frontend/src/features/tables/DateTimeField.tsx`（`DateTimeField` の部品）と `frontend/src/features/tables/SearchPanel.tsx`（`SearchPanel` の部品）で、`t` を「鍵と差し込みの値だけを渡す関数」に包んで `dateTimeHint`・`errorText` に渡す。`options` が無いときは `t(key)`、あるときは `t(key, options)` を呼ぶ（中身の振る舞いは前と同じ）。関数の型と既存のテスト（`DateTimeField.test.tsx` など）は変えていない。振る舞いを変えないため、新しいテストは足さない（既存の画面のテストで確かめた）。
- i18next 27 の既定の変更（`fallbackLng`・`returnEmptyString`・言語の符号の大小・`/` のエスケープ）と react-i18next 18 の変更（`Trans` の扱いなど）を変更履歴で確かめた。アプリの初期化（`frontend/src/app/i18n/i18n.ts`）は `fallbackLng: false`・`keySeparator: false`・`escapeValue: false` を明示し、言語は `ja`・`en`、空の文言は無く、`Trans`・`useSSR` は使っていないため、影響は無い。

### 全体の確かめ（最後の状態）

| 検査 | 結果 |
|---|---|
| `npm run typecheck`・`lint`・`lint:css`・`format:check`・`license:check` | すべて終了コード 0 |
| `npm test` | 274 ファイル・3,063 件すべて通過 |
| `npm run build`・`bundle:size`・`dist:check` | 通過。初回の読み込みの JavaScript の合計は 169.6 KB → 170.8 KB（gzip。i18next 27 の分） |

### そのほかの確かめ

- axe-core: 最新は 4.14.0 で今と同じため上げていない。lockfile の `node_modules/axe-core` は 4.14.0 の1つだけ。上げていないため、E2E の axe と vitest-axe の追加の流し（違反 0 件の件数の記録）は要らない。vitest-axe は画面のテストの中で通過。
- `hasInstallScript` の付いた項目: 前後とも `fsevents` 2.3.3 の1つだけで、増えていない。
- `npm audit`: Moderate の `minimatch`（10.2.6。GHSA の範囲 `>=7.0.0 <11.0.1`）の1件が、`eslint`・`@typescript-eslint/parser` の推移依存として残る。変える前の lockfile でも同じ版で、開発時だけの依存（devDependencies）かつ High 未満のため、決まりでは警告の扱い。`eslint` 10.14.0 は最新で、その依存の範囲では 11 系に上がらない。High 以上は無い（OSV-Scanner での最終の判定は Step 9 の全件の `verify`）。
- Node: 手元は 26.13.0 で、Node 26 系の最新（registry の `node@26`）と同じ。`engines` の `>=26 <27` は変えない。

## Step 7: `dependabot.yml` の `ignore` の見直し（FR1.4）

| `ignore` の行 | 判断 | 理由 |
|---|---|---|
| gradle: `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` | 残す | Step 5 (a)。説明に 2026-11-11 の見直しを足した |
| gradle: `com.networknt:json-schema-validator` | 残す | Step 5 (b)。説明に 2026-11-11 の見直しを足した |
| npm: `typescript` の大きな版 | 残す | Step 6（基準 3）。説明に 2026-11-11 の見直しを足した |
| docker: `eclipse-temurin` の大きな版 | 残す | JDK の大きな版はビルドと CI の 25 とそろえる（計画のとおり） |
| docker-compose: `otel/opentelemetry-collector` の `0.164.0-386` | ① では触らない | ② の Step 11 でタグに合わせる（計画のとおり） |

新しく `ignore` に足したものは無い（大きな版で見送ったのは typescript だけで、既にある行で足りる）。gradle の説明の「Spring Boot 4.1.1」の直しは ② の Step 12 で行う。

## Step 8: E2E の WAR の JVM の時差（FR2.5）

`team.md` の Deployment の「実行時のタイムゾーンの設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM の `user.timezone`・E2E の起動）を変えるときは、4か所を同じ値にそろえ」を、今の値で並べた（読み取りだけ。ファイルは変えていない）。

| 場所 | 今の値 | 出どころ |
|---|---|---|
| Dockerfile | `-Duser.timezone=Asia/Tokyo`（ENTRYPOINT） | `Dockerfile` 52 行目 |
| compose | `TZ: Asia/Tokyo`（app。perf も同じ） | `compose.yaml` 37 行目・`docker/perf/compose.yaml` 39 行目 |
| テストの JVM | `systemProperty("user.timezone", "Asia/Tokyo")` | `backend/build.gradle.kts` 118 行目 |
| E2E の起動（WAR の JVM） | **指定なし**。`webServer.command` は `java -Dh2.compactThreads=1 -jar <war> --server.port=… --spring.datasource.url=…` で、`webServer.env` にも `TZ` は無い。`./gradlew e2eTest`（`build.gradle.kts` の `npx playwright test`）も `TZ` を渡さない | `frontend/playwright.config.ts` 135〜152 行目 |
| （参考）E2E のブラウザ | `timezoneId: 'Asia/Tokyo'` | `frontend/playwright.config.ts` 131 行目。JVM ではなくブラウザの時差 |

判定: **合わない**。E2E の WAR の JVM は時差を明示に指定しておらず、PC の既定（この PC は `/etc/localtime` が `Asia/Tokyo`、`TZ` は未設定）に預けている。この PC では結果として Asia/Tokyo で動くが、「4か所を同じ値にそろえる」を明示の指定と読むなら満たさない。E2E は CI の外で手元だけで流すため、今の運用で値が食い違う場面は、PC の既定が Asia/Tokyo でない機械で流したときに限られる。

計画のとおり、ここで生成を止め、直し方を依頼者に諮る（下の「依頼者に諮りたいこと」2）。Step 8 のチェックは、決定を受けて直す（または直さないと決める）まで付けない。

## 依頼者に諮りたいこと

1. **networknt json-schema-validator の最新 3.0.9 の扱い（Step 5 (b)）**: 3.0.9 は Jackson 3.1.8 を求める（3.0.7・3.0.8 の 3.2.1 と違い、3.1 系のまま）。計画の条件（管理の Jackson が 3.2 系以上）は満たさないため、計画どおり 3.0.6 の固定を残した。ただ、固定の理由だった「3.2 系に引き上げる」は 3.0.9 では当たらなくなった。
   - 案 A（推奨）: 計画どおり 3.0.6 の固定を残す。理由: 3.0.9 を入れると、`platform` の BOM は強制しないため、アプリ全体の Jackson が Spring Boot の管理の 3.1.7 から 3.1.8 に上がり、「推移依存で既存の部品の版を引き上げない」（`project.md` の Corrections）に当たる。Spring Boot の次のパッチで管理の Jackson が 3.1.8 以上になれば、そのときに上げられる。
   - 案 B: 3.0.9 に上げ、Jackson 3.1.8（3.1 系のパッチ）への引き上げを受け入れる。全件の `verify`（DSL の読み込みのテスト）で確かめ、`dependabot.yml` の `ignore` と `project.md` の学びを書き換える。
2. **E2E の WAR の JVM の時差（Step 8）**: E2E の起動だけが時差を明示に指定していない。
   - 案 A（推奨）: `frontend/playwright.config.ts` の `webServer.command` に `-Duser.timezone=Asia/Tokyo` を足し（`-Dh2.compactThreads=1` の隣）、4か所を明示の同じ値にそろえる。理由: 1 ファイル 1 行で済み、PC の既定に結果を預けない（`checklist-timezone.md` の「実行する機械の既定のタイムゾーンに結果を預けない」と同じ向き）。E2E の結果は Step 9 の E2E の全件で確かめる。
   - 案 B: 直さない。決まりの「4か所」は「変えるときにそろえる」決まりで、E2E の起動は PC の既定（Asia/Tokyo）で同じ値になっていると読み、その読み方を記録する。
   - 案 C: `webServer.env` に `TZ: 'Asia/Tokyo'` を置く。`webServer.env` の値は json の報告に残るが、秘密ではないため差し支えは無い。ただし Dockerfile と同じ `-Duser.timezone` の形の方が、決まりの文と一致する。

## 依頼者の決定（2026-11-11、指揮役が記録）

- G1: B（networknt を 3.0.9 に上げる）。`gradle/libs.versions.toml` の版と説明、`.github/dependabot.yml` の説明を直し、`./gradlew :backend:resolveAndLockAll --write-locks` で lockfile を更新した。lockfile の差: `com.networknt:json-schema-validator` 3.0.6 → 3.0.9、`tools.jackson.core:jackson-core`・`jackson-databind`・`tools.jackson:jackson-bom` 3.1.7 → 3.1.8、推移の `com.ethlo.time:itu` 1.14.0 → 1.17.0（既存の依存の版の更新）。`jackson-annotations` 2.21 は変わらない。確かめ: `./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*' --tests 'cherry.mastersmith.dslmanage.*'` が 339 件通過（飛ばし・失敗 0）。版の固定と Dependabot の `ignore` は続ける（3.2 系を求める版を避けるため）。`project.md` の学びの書き換えは Step 14 で行う。
- G2: A（`frontend/playwright.config.ts` の `webServer.command` に `-Duser.timezone=Asia/Tokyo` を足した）。Step 8 を終えた。
- G3: 承認。コミットは `68978fed`（Gradle・Spotless・networknt・lockfile）・`ce703619`（画面の依存と i18next 27 の型）・`5f559c99`（Dependabot の説明）・`2dfbfd57`（E2E の時差）。networknt の直しは `libs.versions.toml` を共有するため1つ目のコミットに含めた。
- FR5 の分岐への影響: ① で Spring Boot・H2 は変わらないが、Jackson が 3.1.7 → 3.1.8 に変わった（JRE は ② で扱う）。

## Step 9: ① の全件の確かめ（NFR1〜NFR3・NFR5、指揮役が流した）

- 対象: ブランチ `feature/261110-deps` の `2dfbfd57`（記録のコミット `f82f048a` を含む先頭）。
- 全件の `verify`: `DOCKER_HOST=… TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=… caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3` が BUILD SUCCESSFUL（20:53:44〜21:09:07、15分23秒）。単体 2,939 件（飛ばし 0・失敗 0）、結合 1,716 件（飛ばし 10・失敗 0。飛ばしの 10 件は前と同じ件数）。OSV-Scanner を含むすべての段が通った。
- カバレッジ（`jacocoTestReport.xml`）: backend の全体は行 97.75%・分岐 91.52%。パッケージごとの最も低い値は `config` の分岐 76.7%・`targetdb.service` の分岐 77.1%（どれも下限の内）。frontend の全体は行 95.49%・分岐 91.03%。
- E2E の全件: Mailpit と見本の PG（上乗せでポートを開けて作り直し）を起動して `caffeinate -dims ./gradlew e2eTest` が 298 件通過（16.2 分、BUILD SUCCESSFUL in 16m 15s）。報告の秘密の値の確かめは見つかった件数 0。見本の PG の片付けは残り 0。流し終えた後に上乗せ無しで作り直してポートを閉じ、Mailpit と見本の PG を止めた（流す前と同じ止まった状態。配備したアプリは流す前から止まっている）。
- 眠り: `pmset -g log` で 20:53〜21:26 の間に Sleep・DarkWake は無い。

## 依頼者の決定 G5・G6 と Step 13: ② の全件の確かめ（指揮役が記録）

- G5: 承認。コミットは `8c16c259`（CI の道具）・`80f1025a`（コンテナのイメージ）・`b9e37bcc`（Dependabot の設定）。
- G6: A。手元の OSV-Scanner は Homebrew の 2.7.0 のまま。CI の 2.7.1 の結果は、統合の後の CI（Build and Test の比べ）で確かめる。
- 全件の `verify`（`b9e37bcc` の上、clean 付き、`-PitForks=3`）: BUILD SUCCESSFUL（22:01:36〜22:16:39、15分3秒）。単体 2,939 件（飛ばし 0・失敗 0）、結合 1,716 件（飛ばし 10・失敗 0）。
- E2E の全件: Mailpit（v1.33.1）と見本の PG（18.6 の新しいダイジェスト）を新しいイメージで作り直して流し、298 件通過（16.0 分）。報告の秘密の値の確かめは見つかった件数 0、見本の PG の片付けは残り 0。流し終えた後に上乗せ無しで作り直してポートを閉じ、Mailpit と見本の PG を止めた。
- 入れ替えたコンテナの `docker inspect`: `mastersmith-mailpit-1` は `axllent/mailpit:v1.33.1@sha256:aef2aacc…`（`RepoDigests` も同じ）、`mastersmith-targetdb-postgres-1` は `postgres:18.6@sha256:e3d24f69…`（`RepoDigests` も同じ）で、どちらも `compose.yaml` の記述と一致した。
- 眠り: `pmset -g log` で 21:50〜22:33 の間に Sleep・DarkWake は無い。
- otel-lgtm 0.40.0 と collector 0.165.0 は起動していない（collector は設定の `validate` だけ）。手元の監視を起動する次の場面で確かめる（Build and Test に引き継ぐ）。
- G7: 承認。② を `develop` へ squash で統合した（`ed0c8675`）。

## Step 14: `project.md` の Tech Stack の書き換え（FR3.1・FR3.3、依頼者の決定 G8: 承認）

ブランチ `docs/261110-rules`。`(learned …)` と `<!-- cid:… -->` は残した。

| 行 | 前 | 後 |
|---|---|---|
| opentelemetry-logback-appender | …固定する（Spring Boot 4.1.1 が持つ OpenTelemetry 1.62 と、より新しい版が食い違い、外部エクスポートの有効時に失敗するため）。Spring Boot を上げるときは、この部品の版も合わせて見直す。 | …固定する（Spring Boot 4.1.2 が持つ OpenTelemetry 1.62.0 と、より新しい版が食い違い、外部エクスポートの有効時に失敗するため。外す時期は、Spring Boot の管理の OpenTelemetry が 1.63 以上になったときで、外部エクスポートを有効にして確かめる）。Spring Boot を上げるときは、この部品の版も合わせて見直す。（2026-11-11 Intent 261110-deps-forks-review で書き換え） |
| Jackson | Jackson の上書きは 3.1.7 とする（user-admin の B1 の前に、High の脆弱性 4 件を解くため 3.1.6 から上げた。Spring Boot の管理の系列 3.1 のまま、Jackson の BOM を platform で読む形は同じ）。Spring Boot を上げるときは、この上書きの要否を見直す。 | Jackson は個別に上書きせず、Spring Boot の BOM の版を使う（BOM を platform で読む上書きは、Spring Boot を 4.1.2 に上げたコミット `98ef3e51` で外した）。ただし networknt json-schema-validator が推移依存で Jackson を引き上げることがあり、2026-11-11 に networknt を 3.0.9 に上げて Jackson は 3.1.8（3.1 系）になった（依頼者の決定）。3.2 系を求める networknt の版には上げない。Spring Boot を上げるときは、管理の Jackson の版と networknt が求める版を見直す。（2026-11-11 Intent 261110-deps-forks-review で書き換え） |

Step 5 の固定の結果（appender と `-Dh2.compactThreads=1` は残す、networknt は 3.0.9）は上の2行に反映した。`-Dh2.compactThreads=1` の行は H2 が 2.4.240 のままのため書き換えない。

## ② の作業のブランチ

`feature/261110-ci-images`（`develop` の `888a4cc1` から作成）。版は 2026-11-11 に配布元（GitHub の Releases とタグ・Docker Hub）で確かめた。

## Step 10: GitHub Actions・Gitleaks・OSV-Scanner（FR1.5）

版は各リポジトリの GitHub の Releases（`releases/latest` とリリースの一覧。プレリリースを除く）で、SHA はタグが指すコミット（`git/ref/tags/<タグ>`。注釈付きのタグは中のコミット）で確かめた。

| 対象 | 前 | 後 | 備考 |
|---|---|---|---|
| `actions/checkout` | v7.0.1（`3d3c42e5…`） | 変えず | 最新 |
| `actions/setup-java` | v6.1.0（`b843ebf9…`） | **v6.2.0（`77dd5b2ce225ea11469928ac317850245a74ae78`）** | 2026-11-04 公開。変更は Liberica・Corretto の版の解決、Undici の更新、Maven のミラーの設定など。使っている `distribution: temurin`・`java-version: "25"` の入力は変わらない |
| `actions/setup-node` | v7.1.0（`949feb24…`） | 変えず | 最新 |
| `gradle/actions/setup-gradle` | v6.4.1（`8da46b53…`） | 変えず | 最新（タグは注釈付きで、中のコミットが `8da46b53…` と一致） |
| `actions/upload-artifact` | v7.0.2（`cf430e03…`） | 変えず | 最新 |
| `actions/download-artifact` | v8.0.2（`9000827c…`） | 変えず | 最新 |
| Gitleaks | 8.30.1 | 変えず | リリースの一覧の最新が 8.30.1（2026-03-21） |
| OSV-Scanner | 2.7.0（`160c6253…218b`） | **2.7.1（`fb7dab19a8bd9bbe5c3189f09d4b7ee1820c9553a23bac202648a56efd2f6ee8`）** | 2026-11-09 公開。SHA-256 は配布元の `osv-scanner_SHA256SUMS` の `osv-scanner_linux_amd64` の値・GitHub の資産の digest・取得した実物の `shasum -a 256` の3つが一致 |

変えたファイル:

- `.github/workflows/ci.yml`: `actions/setup-java` の SHA と版のコメント、`OSV_SCANNER_VERSION`・`OSV_SCANNER_SHA256`。
- `README.md`: 前提の道具の表の OSV-Scanner を 2.7.1 に。
- `.pre-commit-config.yaml`: Gitleaks の `rev: v8.30.1` は最新のため変えない。

確かめたこと・残ること:

- OSV-Scanner 2.7.1 の変更点に、npm の lockfile の読み方の直し（`resolved` の無いパッケージも調べる。`file:`・`link:` の依存は飛ばす）がある。CI の結果が 2.7.0 と変わりうるため、② の全件の `verify`（Step 13）と統合の後の CI の OSV-Scanner の結果を見る。
- 手元の OSV-Scanner は Homebrew で入れた 2.7.0 のまま（2026-11-11 時点で Homebrew の stable が 2.7.0 で、`brew upgrade` では 2.7.1 にならない）。手元の `verify` の OSV-Scanner は 2.7.0、CI は 2.7.1 で流れる。
- 古い値（`v6.1.0`・`b843ebf9`・`OSV_SCANNER_VERSION: 2.7.0`）が `vendor/`・`aidlc/` の外に残っていないことを `git grep` で確かめた。

## Step 11: コンテナのイメージ（FR1.1・FR1.6）

### 始めの一覧（`git grep`。`vendor/`・`aidlc/`・`.claude/`・lockfile を除く）

| 場所 | 参照 |
|---|---|
| `Dockerfile` 17 行 | `eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a5…` |
| `compose.yaml` 73・83・121・151・172・192 行 | collector `0.164.0@sha256:28b6d41a…`・otel-lgtm `0.39.0@sha256:4c8056c9…`・mailpit `v1.33.0@sha256:9b3a463b…`・postgres `18.6@sha256:3a426452…`・mysql `26.10.0@sha256:7b16ec7c…`・mariadb `13.0.2@sha256:f4054da2…` |
| `docker/perf/compose.yaml` 69・96・122・150 行 | postgres・mysql・mariadb・mailpit（`compose.yaml` と同じ値） |
| `docker/hikari-pool.sh` 50 行 | `JDK_IMAGE=eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad…` |
| `perf/versions.env` 24・25 行 | `K6_IMAGE=grafana/k6:2.3.0@sha256:2dd4756e…`・`PERF_JRE_IMAGE`（jre と同じ値） |
| `perf/README.md` 131・488 行 | jdk（`hikari-pool.sh` と同じ値）・otel-lgtm（`compose.yaml` と同じ値） |
| `docs/internal-db.md` 12・22・155・161・195 行、`docs/dsl.md` 118 行、`docs/operations.md` 135 行 | jre・jdk（上と同じ値） |
| `docs/mail.md` 48 行 | mailpit（`compose.yaml` と同じ値） |
| `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java` | `MYSQL_VERSION`・`MYSQL_DIGEST`・`MARIADB_*`・`POSTGRES_*`（`compose.yaml` と同じ値） |
| `.github/dependabot.yml` | 説明のコメントの版（84・101〜103 行）と collector の `ignore` の `0.164.0-386`（132 行） |

計画の表に無い参照は無かった（`docs/target-db.md` 79 行は版の文字だけで、ダイジェストは持たない。版は変わらないため直さない）。

### 版とダイジェスト

最新の版は Docker Hub のタグの一覧（プレリリース・rc・nightly・edge を除く）と各リポジトリの GitHub の Releases で確かめ、ダイジェストは `docker buildx imagetools inspect <タグ>` の先頭の `Digest`（複数アーキテクチャの index。linux/amd64 と linux/arm64 を含むことを `Platform` で確かめた）を使った。

| イメージ | 前 | 後 | 備考 |
|---|---|---|---|
| JRE `eclipse-temurin:*-jre-noble` | `25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce` | **変わらない**（同じタグ・同じダイジェスト） | 25 系の最新は 25.0.5_8（`25.0.6` のタグは無い）。`25-jre-noble` も同じダイジェスト |
| JDK `eclipse-temurin:*-jdk-noble` | `25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a` | 変わらない | 同上 |
| postgres | `18.6@sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81` | `18.6@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42` | 版は同じで、同じタグの組み直し（2026-11-06）。19 は `19rc1` までで対象外 |
| mysql | `26.10.0@sha256:7b16ec7caa94ca7e92f4c5ec287de69d2e8738db056ceb629022951a6f6d910b` | `26.10.0@sha256:3819b2b23835c4a73c8b022fafc33c8095c0f81c929a8b7a84613d2fba6c52f5` | 版は同じで、同じタグの組み直し（2026-11-03）。26 系の最新は 26.10.0 |
| mariadb | `13.0.2@sha256:f4054da2…` | 変わらない | 13 系の最新は 13.0.2 |
| `axllent/mailpit` | `v1.33.0@sha256:9b3a463be15354c0f0eba91697d80e3e0d8a5623ef0e627cacc836bc17fd5231` | **`v1.33.1@sha256:aef2aacc11f075ec0fb6874275ee4a421e688e11a96ebb296134d1735c7761ce`** | 2026-11-07。直しだけ（宛先の読み方・読めないメールを黙って捨てずに拒むなど）。使っている `MP_SMTP_DISABLE_RDNS` と `--max` の既定は変わらない |
| `grafana/otel-lgtm` | `0.39.0@sha256:4c8056c9c05fcb3c8fd93800e85334bfd79b4d0271b47a54a121a64af41fe1fe` | **`0.40.0@sha256:72e05804790e6e0d8c0b70a6d5ff1f721ef7b203463374631e99aa3429bd0cf6`** | 2026-11-06。中の Prometheus 3.16.0・Pyroscope 2.4.3 など。設定の口の変更の記載は無い |
| `otel/opentelemetry-collector` | `0.164.0@sha256:28b6d41ac51bff6b41ba5e8fe503569ffb9d6a1ea90f7a78419e86f34e44f750` | **`0.165.0@sha256:34b24b740c3c88d3467740e8642a397c13898ef76d7295c5425164c482ed1b73`** | core の破壊的な変更は `pkg/confighttp` の古い項目の削除だけで、`docker/otel-collector/config.yaml` は使っていない。新しいイメージの一時のコンテナで `validate --config=…` が終了コード 0 |
| `grafana/k6` | `2.3.0@sha256:2dd4756e…` | 変わらない | GitHub の Releases の最新が v2.3.0、index のダイジェストも同じ |

collector の `-386` のタグ: `0.165.0-386` は単一のイメージ（manifest。index ではない）で、`architecture: 386`・`os: linux` を確かめた。`.github/dependabot.yml` の `ignore` を `0.165.0-386` に合わせ、説明のコメントに経過を足した。

変えたファイル: `compose.yaml`（collector・otel-lgtm・mailpit・postgres・mysql）、`docker/perf/compose.yaml`（postgres・mysql・mailpit）、`perf/README.md`（otel-lgtm）、`docs/mail.md`（mailpit）、`TargetDbImages.java`（`MYSQL_DIGEST`・`POSTGRES_DIGEST`）、`.github/dependabot.yml`（経過のコメントと collector の `ignore`）。JRE・JDK・mariadb・k6 の場所（`Dockerfile`・`docker/hikari-pool.sh`・`perf/versions.env`・`docs/internal-db.md`・`docs/dsl.md`・`docs/operations.md`）と README の SDKMAN の版（`25.0.5.0+8-tem`）は変えていない。

### 終わりの一覧

始めの一覧と同じ `git grep` で、各場所が上の「後」の値になったことを確かめた（`compose.yaml` と `docker/perf/compose.yaml` と `TargetDbImages.java` の対象DB、`compose.yaml` と `docker/perf/compose.yaml` と `docs/mail.md` の Mailpit、`compose.yaml` と `perf/README.md` の otel-lgtm がそれぞれ同じ値）。古いダイジェスト（`3a426452`・`7b16ec7c`・`9b3a463b`・`4c8056c9`・`28b6d41a`）と古いタグ（`v1.33.0`・`otel-lgtm:0.39`・`collector:0.164`）は、`.github/dependabot.yml` の前の Intent の経過を書いたコメント（102・131 行）のほかに残っていない。

### FR5 の分岐（Build and Test の負荷の試験）への影響

JRE のイメージのタグは `eclipse-temurin:25.0.5_8-jre-noble` のまま（ダイジェストも同じ）。② では JRE・Spring Boot・H2 のどれも変わらない（① で Jackson が 3.1.7 → 3.1.8 に変わったことは上の記録のとおり）。

## Step 12: `dependabot.yml` の Spring Boot の版の記述（FR3.2）

- gradle の `ignore` の説明の「Spring Boot 4.1.1 の OpenTelemetry 1.62」を「Spring Boot 4.1.2 の OpenTelemetry 1.62」に直した（Step 3 で Spring Boot は 4.1.2 のまま）。
- `vendor/`・`aidlc/` の外のほかの「4.1.1」は、`LoggingLevelResetTestExecutionListener.java` の「Spring Boot 4.1.1 まで」の経過の記述（今も正しい）と AC の番号だけで、直す対象は無い。

## Step 10〜12 の後のテスト（ダイジェストを変えた対象DB の結合テストを絞って）

- コマンド: `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew verifyQuick -PitTests='*Postgres*IT,*Mysql*IT,TargetDbSampleInitScriptIT' -PitForks=3`
- 対象: ダイジェストを変えた postgres・mysql の結合テストの全クラス（業務データの読み書き・深いページの並べ替え・参照の読み書き・DSL の生成・スキーマの問い合わせ・スキーマの読み込み）と、3種類の見本の初期化の台本を確かめる `TargetDbSampleInitScriptIT`。mariadb はイメージを変えていないため絞りの外。
- 結果: BUILD SUCCESSFUL（21:41〜21:49、7分44秒）。結合テストは 17 クラス・253 件、失敗 0・誤り 0、飛ばし 7。飛ばしの 7 件は、DB の種類で決まる前提（`timetz は PostgreSQL だけ`・`NUL を拒むのは PostgreSQL`・MySQL と MariaDB だけの型の扱いなど）で飛ぶもので、コンテナの実行環境が無いことによる飛ばしではない（`TestAbortedException` の理由で確かめた）。`verifyQuick` の残りの段（フォーマット・リンタ・ライセンス・ビルド・単体テスト・画面のテストと画面のカバレッジの下限・SpotBugs・OSV-Scanner（手元は 2.7.0）・Gitleaks）もすべて通った。
- 眠り: 流す前の `pmset -g assertions` は `PreventUserIdleSystemSleep 1`。流した後の `pmset -g log` で 21:41〜21:49 の間に Sleep・DarkWake は無い。
- collector 0.165.0 は、新しいイメージの一時のコンテナで `validate --config=/etc/otelcol/config.yaml` が終了コード 0。Mailpit・otel-lgtm と見本の PG の起動の確かめ（`docker inspect`）は Step 13 で指揮役が行う。
