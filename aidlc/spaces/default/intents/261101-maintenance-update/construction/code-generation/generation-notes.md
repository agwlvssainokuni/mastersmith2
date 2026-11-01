# 生成の記録（Intent 261101-maintenance-update）

## ① サブモジュールの固定先の更新（ブランチ `fix/261101-submodules`）

作業ブランチは `develop`（`3675b2f7`）から作った（同じ作業フォルダの中。worktree は使っていない）。確かめは 2026-11-01 22:40〜23:05 ごろ（JST）。

### Step 1: 上流の最新と lockfile の差

上流は `git -C vendor/<名> fetch origin` で読んだ。新しいコミットは、どちらも上流の `origin/main` の先頭そのもので、`git merge-base --is-ancestor <前の固定先> origin/main` も通った（前の固定先から一本道で進んだ先。宙に浮いたコミットではない）。

| サブモジュール | 前の固定先 | 新しい固定先（上流の `main` の先頭） | 間のコミット |
|---|---|---|---|
| `vendor/make-you-chic-ui` | `cab42074cf040e3c03127db8c92582cc329f934d` | `cae5fed185541b975dc56fb034dae3212d4c2247`（2026-11-01T22:00:28+09:00） | 1件（`cae5fed` deps: package.json のバージョン指定を最新化し、クリーン再インストール） |
| `vendor/java-mustache-processor` | `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（タグ `0.1.0`） | `d61ba6dac8b4a1617ba72ee469ee0189683e42b4`（タグ `0.1.1`、2026-11-01T22:16:21+09:00） | 6件（依存の更新: Jackson 2.22.4・Shadow 9.6.1・SLF4J 2.0.20、テストの道具: JUnit 5.14.4・jqwik 1.10.2・SnakeYAML 2.7、OWASP dependency-check のプラグイン 13.0.0・Gradle 9.8.2、IDEA の設定、文書、版を 0.1.1 に） |

make-you-chic-ui の変わったファイルは `package.json`（`overrides` に `minimatch: ^11.0.3` を足し、`eslint` を `^10.14.0` に）と `package-lock.json` だけ。部品のソース（`packages/`）は変わっていない。

`vendor/make-you-chic-ui/package-lock.json` の `packages` の前後の差（`git show <commit>:package-lock.json` を読み取りで比べた）:

- 増えた: 1件 — `@isaacs/nonrecursive-glob-match@1.0.3`
- 消えた: 0件
- 版が変わった: 9件 — `@babel/generator` 7.29.8→7.29.12、`@babel/parser` 7.29.11→7.29.12、`@babel/traverse` 7.29.10→7.29.12、`@csstools/css-color-parser` 4.2.6→4.2.7、`@csstools/css-syntax-patches-for-csstree` 1.1.15→1.1.16、`electron-to-chromium` 1.5.467→1.5.472、`eslint` 10.13.0→10.14.0、`minimatch` 10.2.6→11.0.3、`node-releases` 2.0.61→2.0.63
- `hasInstallScript` の付いた項目: 前後とも `fsevents@2.3.3` の1件だけ。**増えていない**ため、依頼者に諮る条件には当たらない。

### Step 2: A2・A3 の確かめ

- **A2（`braces`）**: 新しい固定先 `cae5fed` の lockfile でも `braces@3.0.3` のまま。npm の配布元の最新も 3.0.3（2024-05-21）で、勧告 GHSA-vfj7-8cjw-p6xm（`<=3.0.3`）には直った版が無い。上流で直っていないため、取り込むものは無い（**残る**。開発時だけの依存で、OSV-Scanner では警告の扱い）。
- **A3（依頼（5）の 2・3）**: 上流のソース `git show cae5fed:packages/make-you-chic-ui/src/components/Table/Table.tsx` を読んだ。`TableProps` に1ページの件数を選ぶ口（`onPageSizeChange`・選べる件数の一覧）は無く、枠 `.mycui-table-wrapper` に `role="region"`・`tabIndex`・ラベルの口も無い。`cab4207` から `cae5fed` の間で `packages/` は変わっていない。**公開されていない**。

### Step 3: 固定先の更新（コミット前）

- 作業フォルダの `vendor/make-you-chic-ui` を `cae5fed` に、`vendor/java-mustache-processor` を `d61ba6d` に `checkout --detach` し、親の側で `git add vendor/make-you-chic-ui vendor/java-mustache-processor` を行った。vendor の中のファイルは変えていない（checkout の後の `git status` はどちらも差なし）。
- コミットは指揮役が依頼者の承認を得て積む（固定先の更新だけの専用のコミット）。
- java-mustache-processor の推移依存: `cherry-mustache-core` の `slf4j-api` が 2.0.16 → 2.0.20 に上がった。backend の `runtimeClasspath` では、Spring Boot の管理で既に `slf4j-api:2.0.20` に解決されており（`./gradlew :backend:dependencies --configuration runtimeClasspath` で `cherry-mustache-core:0.1.0 -> project ':java-mustache-processor:cherry-mustache-core'` の下が `slf4j-api:2.0.20`）、`./gradlew :backend:dependencies --write-locks` を流しても `backend/gradle.lockfile`・`settings-gradle.lockfile` は変わらなかった。`slf4j-api:2.0.20` は lockfile の 163 行にあり、OSV-Scanner の対象に載っている。lockfile は変えていない。
- 気づいた点（直していない）: `gradle/libs.versions.toml` の `cherry-mustache = "0.1.0"` とそのコメント（「タグ 0.1.0、コミット 8d44c36」）は新しい固定先（`0.1.1`・`d61ba6d`）と食い違う。composite build が版を問わず置き換えるため動きには影響しない。固定先だけのコミットに混ぜないため、② の Step 9（依存の更新）で直すか、指揮役の判断に任せる。

### Step 4: A3 の置き換え

A3 が上流に公開されていないため、frontend の自前の `ScrollRegion`・`PageSizeSelect` の置き換えは**見送った**。frontend のファイルは変えていない。依頼（5）の 2・3 は上流の対応待ちのまま残る。

### Step 5: verify と E2E

#### 計画との差: Node 24 を一時的に使って流した

手元の既定の Node は依頼者が 26（v26.13.0、Volta で管理）に上げ済みで、今の設定（`frontend/package.json` の `engines` `>=24 <25`・`engine-strict`、`build.gradle.kts` の `checkToolchain` の `startsWith("v24.")`）では Node の版の確かめで止まる。依頼者の決定（A）で、① の Step 5 だけを `volta run --node 24 -- <コマンド>` で流した。手元の既定の Node・Volta の既定の設定・`package.json` は変えていない。

- 使った Node: **v24.22.2**（npm 11.19.1）。Volta の手元の置き場に 2026-10-31 から入っていた版で、今回の取り寄せは無かった。Node を 26 にそろえるのは ② の Step 7。
- Gradle の中から呼ぶ node が 24 になっていたことは、`checkToolchain`（`node --version` が `v24.` で始まることを確かめる。`doLast` で毎回走る）が verify と E2E の両方の中で失敗しなかった（どちらも `BUILD SUCCESSFUL`）ことで確かめた。

#### 眠りの確かめ

- 流す前の `pmset -g assertions`: `PreventUserIdleSystemSleep 1`（`caffeinate -dims` は各コマンドに付けた）。
- 流した後の `pmset -g log`（2026-11-01 23:00〜23:47）: 種類は `Assertions` だけで、`Sleep`・`DarkWake`・`Wake` は 0 件。結果は使える。

#### verify

コマンド: `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims volta run --node 24 -- ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`

- 2026-11-01 23:04:40〜23:32:20、**BUILD SUCCESSFUL in 27m 39s**。
- backend 単体: 304 クラス・2,807 件（失敗 0・SKIPPED 0）。
- backend 結合: 266 クラス・1,654 件（失敗 0・SKIPPED 10）。SKIPPED の 10 件は、業務データの読み書きの結合テスト（`Mariadb`・`Mysql`・`Postgres` の `BusinessDataReadIT`・`BusinessDataWriteIT`）の中の、DB の種類に限った場合（`timetz` は PostgreSQL だけ、`bit` と空間の型は MySQL・MariaDB だけ など）の前提による飛ばしで、コンテナが無いための飛ばしではない（3種類の対象DB のテストは流れた）。
- カバレッジ（backend、JaCoCo）: 全体 行 97.62%・分岐 91.32%。71 パッケージのうち下限（行 80%・分岐 70%）に届かないのは `common.health`（行 79.2%）だけで、`packagesJudgedByTotal` の一覧のパッケージ（全体の合計で判定）。
- カバレッジ（frontend、`coverage-summary.json`）: 行 95.21%・分岐 90.39%・関数 96.28%・文 95.07%。frontend のテストの件数は、verify の出力の末尾に残らなかったため記録できていない。
- OSV-Scanner（`build/reports/osv-scanner/osv.json`）: 統合を止める指摘は無し（verify が通った）。指摘の付いたパッケージは、`backend/gradle.lockfile` の `io.opentelemetry:opentelemetry-api` 1.62.0（GHSA-ccrj-gw8r-854m）と `org.mariadb.jdbc:mariadb-java-client` 3.5.10（4件）、`frontend/package-lock.json` の `minimatch` 10.2.6（GHSA-g3hv-75wg-m249）。どれも今回変えていない lockfile の分で、② の依存の更新で扱う。`vendor/make-you-chic-ui/package-lock.json` の指摘は 0 件（前の Intent までの警告 17〜18 件は出なかった）。
- 新しい固定先で組んだ make-you-chic-ui のビルドと `vendorUnchanged`（ビルドの後もサブモジュールの作業フォルダに差なし）も verify の中で通った。

#### E2E

前提: Mailpit（`mastersmith-mailpit-1`、v1.32.0）と見本の PG（`mastersmith-targetdb-postgres-1`、postgres 18.6、`127.0.0.1:15432` を公開済み）は既に動いていたため、起動・作り直しはしていない。配備したアプリ（`mastersmith-app-1`）には触れていない。

コマンド: `caffeinate -dims volta run --node 24 -- ./gradlew e2eTest`

- 2026-11-01 23:33:15〜23:47:07、**BUILD SUCCESSFUL in 13m 51s**、Playwright **277 passed（13.8m）**、失敗 0。
- 報告の確かめ: 残してはならない値の見つかった件数 0（値の種類 13・json の報告 1・添付 305）。
- 見本の PG の片付け: スキーマ 1・ロール 2 を消した（接頭辞の残り 0）。

Step 5 のうち、`develop` への fast-forward の統合は指揮役が依頼者の承認を得て行う。

## ② 実行環境と依存の更新（ブランチ `feature/261101-runtime-deps`）

作業ブランチは `develop`（`6cbf5cfb`）から作った（同じ作業フォルダの中。worktree は使っていない）。作業は 2026-11-01 23:50〜2026-11-02 01:53（JST）。② から手元の既定の Node（v26.13.0、npm 11.20.0）で流した（`volta run` は使っていない）。版は次の公式の配布元で確かめた: Maven Central と Gradle Plugin Portal の `maven-metadata.xml`、Gradle の `services.gradle.org/versions/current`、Adoptium の API、SDKMAN の API、Node の `nodejs.org/dist/index.json`、npm の配布元（`npm view`・`npm outdated`）。生成の始めに `gh pr list --author app/dependabot --state open` で開いている Dependabot の PR を確かめ、0 件だった。

### 版の前後

| 対象 | 前 | 後 | 場所・備考 |
|---|---|---|---|
| Java（toolchain・CI） | 25 | 25（変えず） | 大きな版だけの指定。Temurin 25 の最新のパッチは 25.0.5+8（Adoptium）で、手元の JDK も 25.0.5。CI の `actions/setup-java` は `java-version: "25"` の形を保った |
| SDKMAN の識別子（README の表） | `25.0.4-tem` | `25.0.5.0+8-tem` | SDKMAN の API の今の識別子（既定の候補） |
| Gradle の wrapper | 9.8.2 | 9.8.2（変えず） | 最新が 9.8.2。`./gradlew wrapper --gradle-version 9.8.2` を2回流し、jar を含め差が無いことを確かめた |
| Node | 24 | 26（v26.13.0 が最新） | `frontend/package.json` の `engines`（`>=26 <27`）・`package-lock.json` の `engines`・`build.gradle.kts`（入れ方の文言2つ・説明・`startsWith("v26.")`・失敗の文言）・`ci.yml`（手順の名前「Node.js 26 を入れる」・`node-version: "26"`）・README の表（版と入れ方の例）。README の文の書き直しは ⑤ |
| Spring Boot | 4.1.2 | 4.1.2（変えず） | 最新の安定版が 4.1.2（Maven Central の一覧の後ろは 4.2.0-M1・M2・RC1 のプレリリースだけ） |
| palantir-java-format | 2.98.0 | 2.103.0 | Spotless の設定から使う（lockfile の外） |
| SpotBugs（道具） | 4.10.4 | 4.10.5 | |
| FindSecBugs | 1.14.0 | 1.14.0（変えず） | 最新 |
| SpotBugs の Gradle のプラグイン | 6.5.13 | 6.5.13（変えず） | 最新（Gradle Plugin Portal） |
| Spotless のプラグイン | 8.10.4 | 8.10.4（変えず） | 最新 |
| JaCoCo | 0.8.15 | 0.8.16 | |
| cherry-mustache（`libs.versions.toml`） | 0.1.0（コメント: タグ 0.1.0・コミット 8d44c36） | 0.1.1（コメント: タグ 0.1.1・コミット d61ba6d） | 依頼者の決定。composite build が置き換えるため動きは変わらない |
| logstash-logback-encoder・jqwik・ArchUnit・SnakeYAML・subethasmtp | 9.0・1.10.2・1.5.1・2.7・7.2.2 | 変えず | どれも最新 |
| eslint | 10.13.0 | 10.14.0 | 小さな版 |
| i18next | 26.5.11 | 26.6.0 | 小さな版（実行時の依存） |
| react-i18next | 17.1.9 | 17.1.10 | 小さな版（実行時の依存） |
| typescript | 6.0.3 | 6.0.3（**見送り**） | 7.0.2 が最新。下の「見送ったもの」 |
| そのほかの frontend の直接の依存 | — | 変えず | `npm outdated` で typescript 以外に遅れは無かった（`@playwright/test` 1.64.1・axe-core 4.14.0 も最新） |
| frontend の推移依存 | — | `npm update` で範囲の中の最新に | `electron-to-chromium` 1.5.467→1.5.472、`node-releases` 2.0.61→2.0.63、`parse5` 8.0.1→8.1.0 など。`aria-query` は `@testing-library/dom` が 5.3.0 に固定しているため 5.3.0 のまま |

`backend/gradle.lockfile` の変化（`./gradlew :backend:resolveAndLockAll --write-locks`）は、SpotBugs と JaCoCo の道具の構成（`spotbugs`・`jacocoAgent`・`jacocoAnt`）だけ: `spotbugs`・`spotbugs-annotations` 4.10.5、`commons-io` 2.22.0、`bcel` 6.13.0、`commons-lang3` 3.21.0（`spotbugs` 用。テスト用は 3.20.0 のまま）、`asm` 9.11（SpotBugs 用）・9.11.1（JaCoCo 用）、JaCoCo 0.8.16、`slf4j-api` 2.0.20・`jspecify` 1.0.1 に `spotbugs` の構成が加わった。アプリの実行時の構成（`runtimeClasspath`・`productionRuntimeClasspath`）は変わっていない。`settings-gradle.lockfile` は変わらなかった。

**新しく増えた依存のライセンス**: 新しい部品は増えていない（`jspecify`（Apache License 2.0）は既にアプリの構成にあり、`spotbugs` の構成に加わっただけ。ほかは既存の部品の版の更新）。frontend にも新しいパッケージは増えていない。

### Step 8: Spring Boot 4.1.2 の BOM が管理する版

Maven Central の `spring-boot-dependencies-4.1.2.pom` で確かめた: OpenTelemetry **1.62.0**、Jackson（`jackson-bom.version`）**3.1.7**、H2 **2.4.240**、MariaDB の JDBC 3.5.10、MySQL の JDBC 9.7.0、PostgreSQL の JDBC 42.7.14、Testcontainers 2.0.5、Flyway 12.4.0、Hibernate 7.4.13.Final。

### Step 10: 連動する固定

1つずつ外す条件を確かめた。どれも条件に当たらず、外せなかった（外す試しの verify は流していない。計画の Step 10 の条件のとおり）。

| 固定 | 外す条件 | 結果 | 理由 |
|---|---|---|---|
| (a) `opentelemetry-logback-appender` 2.28.1-alpha | 管理の OpenTelemetry が 1.63 以上 | **残す** | 管理は 1.62.0 のまま。最新の 2.33.1-alpha の pom は `opentelemetry-api` 1.67.0 を求める（2.29.0-alpha で 1.63.0） |
| (b) networknt 3.0.6 | 管理の Jackson が 3.2 系以上 | **残す** | 管理は 3.1.7 のまま。最新の 3.0.8 の pom は Jackson 3.2.1（`version.jackson`）を求める |
| (c) H2 の `-Dh2.compactThreads=1` | H2 が 2.4.240 から上がる | **残す**（3か所とも変えず） | 管理は 2.4.240 のまま（Maven Central の最新は 2.5.252 だが、Spring Boot の管理の版を上書きしない） |

`libs.versions.toml` の (a)・(b) のコメントと `dependabot.yml` の `ignore` の説明に、2026-11-01 に見直した結果と根拠を書き足した（理由は変わらないため、`ignore` の行は残した）。

### 見送ったもの（大きな版）

- **typescript 7.0.2**: 判定基準 3「上流の未対応」。`npm install --dry-run typescript@7.0.2` が `ERESOLVE`（`@typescript-eslint/parser` 8.72.1 の peer `typescript@">=4.8.4 <6.1.0"`）になった。`@typescript-eslint/parser`・`typescript-estree` の最新（8.72.1）の peer は `>=4.8.4 <6.1.0` のまま（npm の配布元で確かめた）。`dependabot.yml` の `ignore`（大きな版だけ）は残し、試した日と当たった基準を説明に書き足した。外す時期は変えない（`@typescript-eslint/*` が TypeScript 7 を受け付けたとき）。見送っても OSV の指摘は残らない（typescript 6.0.3 に指摘なし）。
- `eclipse-temurin` の大きな版の `ignore` は残す（計画のとおり。イメージは ③）。

### Step 7: Node 26 の起動の旗

Node v26.13.0 で `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/perfEvidenceRedactor.test.ts` が通った（34 件）。旗の効き目も確かめた: 旗を付けると `typeof globalThis.localStorage` が `undefined`（Node の実験の Web Storage が無効）。付けないと Node 26 は `localStorage` を出しうる（`--localstorage-file` が無いという警告が出る）ため、旗はそのまま使う（設定は変えていない）。

### Step 12: axe-core

axe-core は 4.14.0 のまま（npm の配布元の最新）で上がっていない。lockfile では `axe-core@4.14.0` の1つにそろっている（`vitest-axe` の分も deduped）。新しい規則の違反の確かめは不要だが、E2E の全件（axe を含む）と vitest-axe を含む画面のテストの全件は下の Step 14 で通った。

### 計画との差: palantir-java-format 2.103.0 の整形の変化（`spotlessApply`）

palantir-java-format を 2.103.0 に上げたところ、1回目の verify の `:backend:spotlessJavaCheck` が 27 ファイルの書式の違反で止まった。2.103.0 は `switch` 式を代入の右辺に置くときの折り返しと字下げを変えた（`Type x =` の後で改行せず、同じ行で `switch (…) {` を始め、`case` の字下げが浅くなる）。`./gradlew :backend:spotlessApply` で整形を当てた（書式だけで、空白を無視した差は 27 ファイル・38 行の追加と 80 行の削除。すべて折り返しと字下げ）。振る舞いの変化は無い。

- 触れた `src/main` は 16 ファイル（`businessdata` の domain 6・service 2・web 1、`dsl.service` 1、`dslmanage.service` 1、`group.web` 1、`role.transfer` 2、`role.web` 1、`targetdb.config` 1）、`src/test` は 11 ファイル。
- `packagesJudgedByTotal` の 7 パッケージには当たらない（team.md の「手を入れる」の決まりの作業は生じない）。触れた機能（businessdata・dsl・dslmanage・group・role・targetdb）には、どれも `<機能>BoundaryArchitectureTest` が既にある。
- `businessdata` の `src/main` に触れるため、team.md の決まりでも統合の前に E2E が要る（計画の Step 14 で流す予定のとおり）。

### 計画との差: verify を4回流した

1. 1回目（2026-11-02 00:00:42〜00:00:44）: `:checkToolchain` が「`node` を起動できない」で止まった。Gradle の daemon が ① の `volta run --node 24` のときに起動し、その時の一時の PATH（Volta の node 24 の置き場）を持ち続けていたためと見立てた。`./gradlew --stop` で daemon を止めると `checkToolchain` が通った。
2. 2回目（00:01:17〜00:01:40）: 上の書式の違反（`:backend:spotlessJavaCheck`）で止まった。
3. 3回目（00:09:04〜01:09:17）: 整形を当てた後に流した。`BUILD SUCCESSFUL in 1h 12s` だったが、`pmset -g log` で PC が 00:02:07〜00:08:58 と 00:09:04〜00:42:42 に眠っていた（Idle Sleep・Maintenance Sleep、DarkWake 2回。バッテリー駆動（25%）で、`caffeinate -s` は電源の接続中にしか効かない）。project.md の決まりで**結果を使わず**流し直した。
4. 4回目（01:09:46〜01:36:19、電源に接続）: 下の結果。これを基準とする。

### Step 14: verify と E2E

#### 眠りの確かめ

- 流す前の `pmset -g assertions`: `PreventUserIdleSystemSleep 1`、電源は AC（`caffeinate -dims` を各コマンドに付けた）。
- 流した後の `pmset -g log`（2026-11-02 01:09〜01:53）: `Sleep`・`DarkWake`・`Wake` は 0 件（種類は `Assertions` だけ）。4回目の verify と E2E の結果は使える。

#### verify

コマンド: `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`

- 2026-11-02 01:09:46〜01:36:19、**BUILD SUCCESSFUL in 26m 32s**（① は 27m 39s）。
- backend 単体: 304 クラス・2,807 件（失敗 0・SKIPPED 0）。
- backend 結合: 266 クラス・1,654 件（失敗 0・SKIPPED 10）。SKIPPED の 10 件は ① と同じ、DB の種類に限った場合の前提による飛ばし（3種類の対象DB のテストは流れた）。
- frontend: 238 ファイル・2,312 件（失敗 0）。verify の出力に件数が残らないため、verify の後に `caffeinate -dims npm run test` を別に流して数えた（83.96 秒）。
- カバレッジ（backend、JaCoCo）: 全体 行 97.62%・分岐 91.32%。71 パッケージのうち下限（行 80%・分岐 70%）に届かないのは `common.health`（行 79.25%・分岐 100%）だけで、`packagesJudgedByTotal` の一覧のパッケージ（全体の合計で判定）。① と同じ。
- カバレッジ（frontend）: 行 95.21%・分岐 90.34%・関数 96.28%・文 95.07%（verify の出力の Coverage summary）。
- SpotBugs（4.10.5）: 統合を止める指摘なし（`spotbugsGate` が通った）。
- 初回の読み込みの JavaScript: 166.4 KB（gzip）。

#### OSV-Scanner の指摘の変化

統合を止める指摘は無い（verify が通った）。① で指摘の付いた3つは、**どれも消えていない**:

| パッケージ | 版 | 指摘（重大度） | 消えなかった理由 |
|---|---|---|---|
| `io.opentelemetry:opentelemetry-api` | 1.62.0 | GHSA-ccrj-gw8r-854m（Moderate、直った版 1.67.0） | Spring Boot 4.1.2（最新の安定版）の管理の版のまま。上げると (a) の固定の理由（appender と OpenTelemetry の食い違い）にも触れる |
| `org.mariadb.jdbc:mariadb-java-client` | 3.5.10 | 4件（Moderate 3・Low 1、直った版 3.5.11） | Spring Boot 4.1.2 の管理の版のまま。管理の版を上書きする形は計画に無いため、上書きしていない |
| `minimatch`（frontend、開発時だけ） | 10.2.6 | GHSA-g3hv-75wg-m249（Moderate、直った版 11.0.1） | `eslint` 10.14.0 を入れても、`@typescript-eslint/typescript-estree` 8.72.1（最新）が `minimatch@^10.2.2` を求めるため 10.2.6 のまま。11 系への `overrides` は宣言の範囲を外れるため入れていない |

#### E2E

前提: Mailpit（`mastersmith-mailpit-1`）と見本の PG（`mastersmith-targetdb-postgres-1`）は動いていたため、起動・作り直しはしていない。配備したアプリ（`mastersmith-app-1`）には触れていない。

コマンド: `caffeinate -dims ./gradlew e2eTest`

- 2026-11-02 01:38:20〜01:52:23、**BUILD SUCCESSFUL in 14m 3s**、Playwright **277 passed（14.0m）**、失敗 0（axe の検査を含む）。
- 報告の確かめ: 残してはならない値の見つかった件数 0（値の種類 13・json の報告 1・添付 305）。
- 見本の PG の片付け: スキーマ 1・ロール 2 を消した（接頭辞の残り 0）。

Step 14 のうち、`develop` への squash の統合は指揮役が依頼者の承認を得て行う（Step 14 の印は付けていない）。

### ② の依頼者の決定（2026-11-02）

- コミットは C1（backend の道具の版と palantir 2.103.0 の整形、27 ファイルは折り返しだけ。指揮役が空白を無視した差分で確かめた）・C2（Node 26 と frontend の依存）・C3（`dependabot.yml`）・C4（記録）の4つに分け、`develop` へ squash で統合する。
- OSV の指摘のうち統合を止めない重大度の3つ（`mariadb-java-client` 3.5.10・`opentelemetry-api` 1.62.0 は Spring Boot の管理の版、frontend の `minimatch` 10.2.6 は開発時だけ・typescript-eslint の範囲に縛られる）は、上書き・`overrides` をせず、持ち越しとして記録する（依頼者の決定 A。指揮役の推奨は mariadb のパッチの上書き B だった）。Spring Boot が管理の版を上げたとき、typescript-eslint が範囲を広げたときに取り込む。

## ③ CI・コンテナのイメージ・道具（ブランチ `feature/261101-ci-images`）

作業ブランチは `develop`（`064be35a`）から作った（同じ作業フォルダの中。worktree は使っていない）。作業は 2026-11-02 02:00〜02:55（JST）。始める前に `./gradlew --stop` で daemon を止めた。

### Step 15: Actions と検査の道具

版は GitHub の Releases（`gh release list`、プレリリースを除く）とタグの一覧（`git ls-remote --tags`）で確かめた。SHA は `git ls-remote` でタグの先のコミット（注釈付きのタグは `^{}` の先）を読み、今の SHA も今の版のタグと一致することを確かめた。

| 対象 | 前 | 後 | 備考 |
|---|---|---|---|
| actions/checkout | v7.0.1（`3d3c42e`） | 変えず | 最新 |
| actions/setup-java | v6.1.0（`b843ebf`） | 変えず | 最新 |
| actions/setup-node | v7.1.0（`949feb2`） | 変えず | 最新 |
| gradle/actions/setup-gradle | v6.4.0（`3f5f9ad`） | **v6.4.1**（`8da46b539cd8fe6ab27ca75128919a18d757604c`） | 2026-10-28 公開。注釈付きのタグ（タグの物は `7296c77`、先のコミットを使った） |
| actions/upload-artifact | v7.0.2（`cf430e0`） | 変えず | 最新 |
| actions/download-artifact | v8.0.2（`9000827`） | 変えず | 最新 |
| Gitleaks | 8.30.1 | 変えず | 最新（2026-03-21）。`ci.yml` の SHA-256 は配布元の `checksums.txt` と一致。`.pre-commit-config.yaml` の `rev: v8.30.1`・README も同じ |
| OSV-Scanner | 2.6.0 | **2.7.0** | 2026-10-12 公開。`ci.yml` の SHA-256 を配布元の `osv-scanner_SHA256SUMS` の `osv-scanner_linux_amd64` の値（`160c6253…218b`）に。README の表も 2.7.0。手元（brew）は既に 2.7.0 で、下の verify は 2.7.0 で流れた |

### Step 16: コンテナのイメージ

版は Docker Hub のタグの一覧で確かめ、ダイジェストは `docker buildx imagetools inspect <タグ>` の先頭の index のもの（linux/arm64 と linux/amd64 を含むことを確かめた）。

| イメージ | 前 | 後 | 備考 |
|---|---|---|---|
| eclipse-temurin（jre） | 25.0.4.1_1-jre-noble@`d9a39a23…` | **25.0.5_8-jre-noble**@`a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce` | Java 25 の最新のパッチ（② の Adoptium の 25.0.5+8 と同じ）。`Dockerfile`・`perf/versions.env` の `PERF_JRE_IMAGE`・README（5 か所） |
| eclipse-temurin（jdk） | 25.0.4.1_1-jdk-noble@`589ff4cc…` | **25.0.5_8-jdk-noble**@`95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a` | `docker/hikari-pool.sh`・README（2 か所）・perf/README |
| postgres | 18.6@`5a5a84b1…` | 18.6@`3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81` | 版は最新のまま（19 は `19rc1` だけ）。同じタグの組み直し（2026-10-28。`PG_VERSION` は同じ） |
| mysql | 26.10.0@`f51c9c01…` | 26.10.0@`7b16ec7caa94ca7e92f4c5ec287de69d2e8738db056ceb629022951a6f6d910b` | 版は最新のまま。同じタグの組み直し（2026-10-27） |
| mariadb | 13.0.2@`d4fdec05…` | 13.0.2@`f4054da26e3a9dd6c8b5cc97363e544408b15c0fb369d7359c25dc04826eb6e1` | 版は最新のまま（13.1 は `13.1.1-rc` だけ）。同じタグの組み直し（2026-10-26） |
| axllent/mailpit | v1.32.0@`0790692b…` | **v1.33.0**@`9b3a463be15354c0f0eba91697d80e3e0d8a5623ef0e627cacc836bc17fd5231` | `compose.yaml`・`docker/perf/compose.yaml`・README |
| grafana/otel-lgtm | 0.38.0@`75cdf892…` | **0.39.0**@`4c8056c9c05fcb3c8fd93800e85334bfd79b4d0271b47a54a121a64af41fe1fe` | `compose.yaml`・perf/README |
| otel/opentelemetry-collector | 0.163.2@`63e5e8ec…` | **0.164.0**@`28b6d41ac51bff6b41ba5e8fe503569ffb9d6a1ea90f7a78419e86f34e44f750` | `compose.yaml`。`validate` で `docker/otel-collector/config.yaml` が通ることを確かめた |
| grafana/k6 | 2.3.0@`9c2dee7f…` | 2.3.0@`2dd4756ec6e14e62778916e1eb850108e4b499304c02df56760fa0bdb0fba0b8` | 版は最新のまま。同じタグの組み直し（2026-10-24）。`perf/versions.env` |

- **対象DB の大きな版の更新は無い**（3種類とも版は同じ）。見本の対象DB のデータのボリュームの扱いは要らない。版が同じで中身が組み直されたものは、「最新」に合わせて新しい index のダイジェストに揃えた（`TargetDbImages.java` を含む）。前と後のダイジェストの `PG_VERSION`・`MYSQL_VERSION`・`MARIADB_VERSION` は同じで、変わったのは作られた日（土台の組み直し）だけ。
- 3種類の対象DB の新しいイメージの `Entrypoint`・`Cmd`（`docker-entrypoint.sh`・`postgres`／`mysqld`／`mariadbd`）は前と同じで、`compose.yaml` の entrypoint の包み方と合う。
- collector の `-386`: 新しい `0.164.0-386` も linux/386 だけ（`imagetools inspect` で確かめた）。`dependabot.yml` の `ignore` の `versions` を `["0.164.0-386"]` に合わせ、説明を今のタグに直した。
- `dependabot.yml` の固定先の一覧のコメント（Dockerfile の jre の版、jdk を揃えた Intent）を直し、今回の上げ方を1段落足した。
- `docker compose config -q`（`compose.yaml`）と `docker/perf/compose.yaml`（`MASTERSMITH_PERF_ENV_FILE=/dev/null` を付けて）が通った。
- 新しい JRE のイメージの確かめ: 別のタグ `mastersmith:ci-images-check` でイメージを作り（`app` の `mastersmith:local` は作り直していない）、`MASTERSMITH_IMAGE_TAG=ci-images-check ./docker/check-container-limits.sh` が「すべて期待どおり」、`java -version` が `Temurin-25.0.5+8`。確かめた後にタグを消した。

#### grep の確かめ

始める前と終わった後に、`Dockerfile`・`compose.yaml`・`docker/`・`perf/`・`README.md`・`.github/`・`TargetDbImages.java` のイメージの参照（`FROM`・`image:`・`*_IMAGE=`・`@sha256:`・`_DIGEST`）を一覧にした。計画の表に無い参照は無かった（`mastersmith:${MASTERSMITH_IMAGE_TAG:-local}` は自前のイメージ）。終わった後に古い版・古いダイジェスト（`25.0.4.1_1`・前の各ダイジェスト・`v1.32.0`・`0.38.0`・`0.163.2`・OSV の前の SHA-256・setup-gradle の前の SHA）を `aidlc/`・`vendor/`・`node_modules`・`build` の外で探し、残りは次の3つだけ（どれも経緯の記録の文で、残した）:

- `.github/dependabot.yml` 87 行・88 行（前の Intent で揃えた版の経緯）
- `perf/README.md` 865 行（前の Intent の測り直しの記録「新しい土台のイメージ（`25.0.4.1_1-jre-noble`）で流す（履歴 H28・H29）」。過去の試験の条件の記録のため書き換えない。README の書き直しは ⑤ の Step 18〜21）

### Step 17: verify と E2E

#### 眠りの確かめ

- 流す前の `pmset -g assertions`: `PreventUserIdleSystemSleep 1`、電源は AC（電池 100%）。`caffeinate -dims` を各コマンドに付けた。
- 流した後の `pmset -g log`（2026-11-02 02:10〜02:53）: 種類は `Assertions` だけで、`Sleep`・`DarkWake`・`Wake` は 0 件。結果は使える。

#### verify

コマンド: `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`

- 2026-11-02 02:12:07〜02:38:03、**BUILD SUCCESSFUL in 25m 55s**（② は 26m 32s）。1回目で通った。
- backend 単体: 304 クラス・2,807 件（失敗 0・SKIPPED 0）。
- backend 結合: 266 クラス・1,654 件（失敗 0・SKIPPED 10）。SKIPPED の 10 件は ①② と同じ、DB の種類に限った場合の前提による飛ばし。対象DB の名前の付いた結合テスト（`*Mysql*IT`・`*Mariadb*IT`・`*Postgres*IT`）は 356 件が新しいダイジェストのイメージで流れた。
- frontend: 2,312 件（失敗 0、verify の出力の `Tests 2312 passed`）。
- カバレッジ（backend、JaCoCo）: 全体 行 97.62%・分岐 91.32%（② と同じ）。
- カバレッジ（frontend）: 行 95.21%・分岐 90.36%。
- OSV-Scanner 2.7.0・SpotBugs・Gitleaks を含め、統合を止める指摘は無し（verify が通った）。

#### 作り直したコンテナと inspect

サービス名を指定し `--no-deps --force-recreate` で作り直した。`app`（`mastersmith-app-1`、`mastersmith:local`）は止めず作り直さず、作り直しの後も running・healthy のまま。

| コンテナ | コマンド | inspect の結果（`.Config.Image`・`.Image`） |
|---|---|---|
| `mastersmith-mailpit-1` | `docker compose --profile mail up -d --no-deps --force-recreate mailpit` | `axllent/mailpit:v1.33.0@sha256:9b3a463b…`・`sha256:9b3a463b…`、healthy |
| `mastersmith-targetdb-postgres-1` | `docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d --no-deps --force-recreate targetdb-postgres` | `postgres:18.6@sha256:3a426452…`・`sha256:3a426452…`、`127.0.0.1:15432` を公開。ボリュームは残した（同じ大きな版） |
| `mastersmith-lgtm-1` | `docker compose --profile monitoring create --force-recreate lgtm`（起動しない。前から止まっていた） | `grafana/otel-lgtm:0.39.0@sha256:4c8056c9…`・`sha256:4c8056c9…`、状態 created（止まったまま） |

どれも `compose.yaml` の版とダイジェストに一致した。見本の PG は E2E の上乗せのまま（`127.0.0.1:15432` を開けている）で、README の手順の最後の「上乗せ無しで作り直してポートを閉じる」は行っていない（指揮役の判断に任せる）。

#### E2E

コマンド: `caffeinate -dims ./gradlew e2eTest`

- 2026-11-02 02:39:11〜02:53:14、**BUILD SUCCESSFUL in 14m 3s**、Playwright **277 passed（14.0m）**、失敗 0（axe の検査を含む）。
- 報告の確かめ: 残してはならない値の見つかった件数 0（値の種類 13・json の報告 1・添付 305）。
- 見本の PG の片付け: スキーマ 1・ロール 2 を消した（接頭辞の残り 0）。

Step 17 のうち、`develop` への squash の統合は指揮役が依頼者の承認を得て行う（Step 17 の印は付けていない）。

### 取り込んだ Dependabot の PR

③ の版は、開いている PR と同じか新しい: #85（gradle/actions/setup-gradle 6.4.0→6.4.1。③ も v6.4.1）、#84（grafana/otel-lgtm 0.38.0→0.38.1。③ は新しい 0.39.0）、#83（otel/opentelemetry-collector 0.163.2→0.164.0。③ も 0.164.0）。PR は統合の後に指揮役が閉じる。

### 計画との差

- 版の変わらない postgres・mysql・mariadb・k6 も、同じタグの組み直しの新しいダイジェストに揃えた（計画は「最新にする」で、版の更新の有無で分けていない）。
- verify の出力を一時に `build/ci-images-verify.log`（git の対象外）へ受けて件数を読んだ（決まりの「出力はファイルに送らない」との差）。読んだ後に消した。

### ③ の依頼者の決定（2026-11-02）

- コミットは C1（`ci.yml`）・C2（コンテナのイメージ、README の OSV の行を含む）・C3（`dependabot.yml`）・C4（記録）に分け、`develop` へ squash で統合する。
- Dependabot の PR #83・#84・#85 は、依頼者が push した後に、取り込んだコミットを書いたコメントを付けて閉じる。
- E2E の上乗せで作り直した見本の PG の `127.0.0.1:15432` は、Build and Test の E2E が終わるまで開けておき、その後に上乗せ無しで作り直して閉じる。
