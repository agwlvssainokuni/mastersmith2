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

## ④ 小さな直し（ブランチ `fix/261101-small-fixes`、2026-11-02）

`develop`（a29f6dbf）から `fix/261101-small-fixes` を作った。始める前に `./gradlew --stop`。

### Step 18（P1、FR9.1）: 1038 が起きた → 承認の場の決定 Q-C により生成を止めた

テストを書く前に、捨ての MySQL のコンテナ（`TargetDbImages.MYSQL_DIGEST` と同じイメージ、名前 `p1-probe-mysql`、確かめた後に消した）で、`RowSqlBuilder.page` が作る文と同じ形（内側で主キーだけを `ORDER BY c IS NULL ASC, c ASC[, id ASC] LIMIT ? OFFSET ?` で並べ、外側でつないで同じ並びで並べる）を流した。値は架空の文字（`LPAD` の番号＋同じ文字の繰り返し）だけ。

- MySQL 26.10.0、`sort_buffer_size` 262,144（既定）、`max_sort_length` 1,024、照合順序 `utf8mb4_0900_ai_ci`（NO PAD のため、並べ替えのキーは `max_sort_length` で切られず値の全体の重みになる）。
- **1038（`ERROR 1038 (HY001) Out of sort memory`）が起きた。** 起きるかどうかは `OFFSET`・1ページの件数・行の数に依らず、並べ替えのカラムの値1つの大きさで決まる（下の表。内側の主キーだけの問い合わせでも、外側だけでも起きる）。

| 値（並べ替えのカラム） | 型 | 行の数 | 件数・OFFSET | 結果 |
|---|---|---|---|---|
| ひらがな 10,000 字（29,990 B・重み 20,000 B） | LONGTEXT | 2,000 | 100・0／1,900／1,999 | 起きない |
| `a` 5,000〜16,000 字（重み 10,000〜32,000 B） | LONGTEXT | 300 | 100・200、20・0 | 起きない |
| `a` 17,000 字以上（重み 34,000 B〜） | LONGTEXT | 300 | 100・200 | **起きる** |
| 漢字 8,000 字（23,990 B・重み 31,990 B） | VARCHAR(10000) | 300 | 20・0 | **起きる** |
| 漢字 10,000 字（29,990 B・重み 39,990 B） | LONGTEXT | 300・2,000 | 20・100 の各 OFFSET（0〜末尾） | **すべて起きる** |
| 補助面の漢字 10,000 字（39,985 B） | LONGTEXT・VARCHAR(10000) | 2,000 | 100・1,900 | **起きる** |
| 漢字 16,000 字（47,990 B） | VARCHAR(16000) | 300 | 100・200 | **起きる** |

- 意味: アプリが書ける文字数の上限（`ColumnWriteRulesFactory.MAX_VALUE_LENGTH` = 10,000 字）の内でも、漢字などの文字（重み 4 B/字）がおよそ 8,000 字を超える値を持つカラムで一覧を並べ替えると、1ページ目から 1038 になる（境の正確な値は、値のバイト数と重みの両方に依るため決め切っていない）。アプリの外で入れた長い値（ラテン文字でおよそ 17,000 字以上）でも起きる。
- アプリの応答（見立て、アプリを通しては流していない）: `RowSqlFailureClassifier.classify` は HY001・1038 を時間切れ・接続・不一致・値の型のどれにも当てないため `UNEXPECTED` に分ける（読み取りの段の失敗のため）。
- 直し方の確かめ（同じ捨てのコンテナで）: 並べ替えの式を `SUBSTRING(c, 1, 100)` にすると、上の起きる組（漢字 8,000〜8,300 字、件数 100・OFFSET 200、600 KB の値）ですべて起きなかった。

#### 直し方の案（依頼者に諮る）

1. **案 A（推奨）**: 一覧の並び（`RowSqlBuilder.orderBy`）も、参照先の並びと同じ `RowDialect.sortExpression`（文字の型と OTHER を先頭 `LIST_TEXT_LIMIT` = 100 字の `SUBSTRING` にする）に寄せる。内側と外側の両方に当てる。主キーのあるテーブルは `orderKeys` が主キーを足すため、先頭 100 字が同じ行も主キーの順で決まり、ページの重なり・抜けは起きない。代わりに、先頭 100 字が同じ値どうしは 101 字目以降で並ばない（主キーの順になる）。3種類の DB で同じ式にでき、PostgreSQL・MariaDB の並びも同じ形になる（MariaDB での 1038 は確かめていない）。
2. **案 B**: MySQL・MariaDB だけ、文字の型の並びを `SUBSTRING` で切る（PostgreSQL は今のまま全体で並べる）。DB の種類ごとの差は `MysqlFamilyRowDialect` に閉じる。並びの意味が DB で変わる。
3. **案 C**: 対象DB の接続に `SET SESSION sort_buffer_size` を足す。値の大きさに上限が無いため、どの値で足りるかを決められず、接続ごとのメモリが増える（読み取りだけのアカウントで変えられるかも未確認）。勧めない。
4. **案 D**: 文字の型（長い VARCHAR・TEXT）を並べ替えの対象から外す（DSL の検証で `sortable` を拒む）。既存の DSL を壊しうる。勧めない。

どの案でも、追加する MySQL の結合テスト（仮の名前 `MysqlDeepPageSortIT`）で、漢字 10,000 字の値のカラムで並べ替えた一覧（件数 100・深い OFFSET）が、直す前は失敗し、直した後に 200 で返ることを記録する（計画の Step 18）。

### 止めた範囲（止めた時点）

- 止めた時点では Step 18〜22 のどれも終えておらず、コードの変更は無かった。捨ての MySQL のコンテナは消した。`app`・見本の PG・Mailpit は触っていない。

### 依頼者の決定（2026-11-02）と再開

- P1 は **案 A**（一覧の `RowSqlBuilder.orderBy` を、参照先の並びと同じ `RowDialect.sortExpression`（文字型と OTHER を先頭 100 字で切る）に揃え、内側と外側の両方に当てる。3種類の DB で同じ形）。T1〜T3 も同じブランチで再開してよい（決定 A）。

### Step 18（P1）の再現と直し

- 再現の結合テスト: `backend/src/test/java/cherry/mastersmith/businessdata/web/AbstractDeepPageSortIT.java` と、DB の種類ごとの子のクラス `MysqlDeepPageSortIT`・`MariadbDeepPageSortIT`・`PostgresDeepPageSortIT`（既存の `*BusinessDataReadIT` と同じ形）。テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB はデータベース）と読み取りだけのアカウントを作り、終わったら消す。
- 確かめた範囲: 1,000 行。`body`（MySQL・MariaDB は `LONGTEXT`、PG は `TEXT`）は先頭5字の番号＋漢字で 10,000 字（アプリが書ける上限 `ColumnWriteRulesFactory.MAX_VALUE_LENGTH`。約 30 KB）。`same`（`VARCHAR(10000)`）は先頭 100 字が全行で同じで、101 字目以降が違う 10,000 字。1ページの件数の上限 100 で、1ページ目と最後のページ（10 ページ目、OFFSET 900）を昇順・降順で読む。値は架空の文字だけ。MySQL 26.10.0（`sort_buffer_size` 262,144・`utf8mb4_0900_ai_ci`）、MariaDB 13.0.2、PostgreSQL 18.6（どれも `TargetDbImages` の固定のイメージ）。
- **直す前**（`:backend:integrationTest --tests '*DeepPageSortIT'`）: 9 件中 5 件が失敗。
  - MySQL: 3 件すべて 500 `INTERNAL_ERROR`。テストの記録のログに `sqlState` HY001・`vendorCode` 1038 が 3 件（`BusinessDataReadService.failure` の `UNEXPECTED`）。**1038 による失敗と確かめた。**
  - MariaDB: 長い値の昇順・降順の 2 件は通った（**1038 は起きない**）。先頭 100 字が同じ値のテスト 1 件は、全体の値の順に並んで失敗（案 A の決めた動作の確かめのため、直す前は落ちるのが正しい）。
  - PostgreSQL: MariaDB と同じ（長い値の 2 件は通り、先頭 100 字が同じ値の 1 件が失敗）。
- 直し: `RowSqlBuilder.orderBy` を、参照先の並びと共通の `sortedOrderBy`（`view.column` のあるカラムは `dialect.sortExpression`、無いカラムはカラムのまま）にした。`referenceOrderBy` も同じ関数を呼ぶ（振る舞いは前と同じ）。主キーは `TableAccessView.orderKeys` が足すため、先頭 100 字が同じ値どうしは主キーの昇順で決まり、ページの重なり・抜けは起きない。
- 単体テスト: `RowSqlExecutorTest` に「文字のカラムで並べたページは内側と外側の両方で先頭 100 字の式で並べる」（MySQL の SQL の文字列）を 1 件足した。既存の SQL の文字列のテスト（`price`（DECIMAL）・`code`（主キー）の並び）は変わらず通る。
- **直した後**: `*DeepPageSortIT` 9 件すべて通過。既存の `*BusinessDataReadIT`（3種類×18 件）・`*ReferenceReadIT`（3種類×23 件）もすべて通過（SKIPPED は前からの DB の種類の前提のもの）。
- 並びの意味が変わる点に当たる既存のテストを洗い出した（`businessdata` のテストと E2E の並べ替え・長い値の検索）。先頭 100 字が同じ値を並べ替えて順を確かめる既存のテストは無く、期待を直したテストは無い。

### Step 19（T1）

- `frontend/vitest.config.ts` の `coverage.include` に `../perf/redact-k6-evidence.mjs`（定数 `PERF_EVIDENCE_REDACTOR`）を足し、frontend/ の外のファイルを計測するため `coverage.allowExternal: true` を足した。`thresholds`（行 80%・分岐 70%）と除外は変えていない。テストの置き場は `frontend/src/perfEvidenceRedactor.test.ts`・`perfEvidenceRedactorCli.test.ts`（unit のプロジェクトの `src/**/*.test.{ts,tsx}` に入っている）。
- 道具のファイルが報告に載ることを確かめた（`perf/redact-k6-evidence.mjs` の行）。値は 2 つのテストだけ流したとき 行 73.49%・分岐 67.13%（CLI のテストの子のプロセスは計測されないため）、verify の全体のテストでは 行 94.23%・分岐 90.65%。

### Step 20（T2）

- ESLint: `frontend/eslint.config.js` に `perfToolRule`（`files: ['perf/redact-k6-evidence.mjs']`、Node の環境の大域の名前、`no-undef`・`no-unused-vars`・`no-eval`・`no-implied-eval`・`no-new-func`・export default の禁止）を足した。ESLint 10 は設定のファイルをリントするファイルの場所から探すため、リポジトリの根から `--config frontend/eslint.config.js` で呼ぶ（`basePath: '..'` は根の親を指して当たらなかったため使わない）。違反は `TextDecoder` の宣言の無い名前の 1 件で、Node の大域の名前として宣言して直した。規則が当たることは、違反を入れた標準入力で 5 つの規則が出ることで確かめた。
- 型の検査（Q-B の案 A）: 道具に `// @ts-check` と JSDoc の型（`Env`・`Io`・`Out`・`PathNode`・`Position`・`ScanCounts`・`Slots`・`TreeWork`、表示の部品の型）を足し、`frontend/tsconfig.perf-tool.json`（`allowJs`・`checkJs`・`strict`・`noUnusedLocals` など、`types: ["node"]`、対象は道具のファイルだけ）で `tsc --noEmit` を当てる。始めは 147 件の型の誤り。振る舞いを変えない形で直した（`while (work.length > 0) { work.pop() }` を `for (let item = work.pop(); item !== undefined; …)` に、`redactTree` の一番上の入れ物を配列 `[undefined]` から `{}` に（`holder[0]` を返すのは同じ）、`stdout`・`stderr` を値を返さない形に、`errorCode` で `'code' in error` を確かめる形に）。呼び出し方は変えていない。伏せ字のテスト（79 件）と `eslintImportRules.test.ts` は通る。
- 呼ばれるタスク: 新しい Gradle のタスク `perfToolEslint`（`verifyLint` の段）と `perfToolTypecheck`（`verifyBuild` の段）。どちらも `./gradlew verify` と CI（同じ `verify`）から流れる。

### Step 21（T3）

- 段の分からない想定外の経路を作るため、テストの偽物 `FakeBusinessDataPorts.Executor` に `onDetail`（詳細の読み取りのたびに呼ぶ処理）を足した。
- `BusinessDataWriteReferenceTest` に「段の分からない想定外でも、WARN に stage のキーを固定の名前で出す」を足した。**直す前は失敗**（キーの一覧に `stage` が無い）。
- 直し: `BusinessDataWriteService.warnReference` は `stage` を必ず出し、null のときは固定の名前 `UNKNOWN`（定数 `UNKNOWN_STAGE`）にする。`ReferenceStepOutcome.Stage` には足さない。値（行の値・参照先の値）は出さない。テストは `SECRET`・テーブルの名前がメッセージにもキーにも無いことも確かめる。**直した後は通過**（同じクラスの 24 件）。
- WARN のキーを確かめている既存のテスト（`grep '"stage"'`）: `BusinessDataWriteReferenceTest`・`BusinessDataWriteServiceTest`・`ReferenceWriteSecretLeakIT`・`BusinessDataWriteSecretLeakIT`。stage の無い WARN を期待するものは無く、直しは要らなかった（verify ですべて通過）。

### Step 22 の確かめ

#### 眠りの確かめ

- 流す前: 電源は AC（電池 100%）、`pmset -g assertions` は `PreventUserIdleSystemSleep 1`。各コマンドを `caffeinate -dims` で包んだ。
- verify の間（06:26〜06:54）の `pmset -g log`: 種類は `Assertions` だけで、`Sleep`・`DarkWake`・`Wake` は 0 件。

#### verify

- 1 回目（06:26:13）は `checkToolchain` が `node` を起動できず 1 秒で失敗した（前から残っていた Gradle の作業プロセスの PATH の食い違いと見立て。`./gradlew --stop` の後に通った。コードは変えていない）。
- コマンド: `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`
- 2026-11-02 06:26:44〜06:53:04、**BUILD SUCCESSFUL in 26m 19s**（③ は 25m 55s）。
- backend 単体: 304 クラス・2,809 件（失敗 0・SKIPPED 0。③ より 2 件多い: `RowSqlExecutorTest` と `BusinessDataWriteReferenceTest` の 1 件ずつ）。
- backend 結合: 269 クラス・1,663 件（失敗 0・SKIPPED 10。③ より 3 クラス・9 件多い: `*DeepPageSortIT`。SKIPPED は ③ と同じ DB の種類の前提のもの）。
- frontend: 238 ファイル・2,312 件（失敗 0）。
- カバレッジ（backend、JaCoCo）: 全体 行 97.63%・分岐 91.35%。手を入れたパッケージ: `businessdata.repository` 行 96.31%・分岐 88.49%、`businessdata.service` 行 95.84%・分岐 89.10%（どちらも下限を満たす。`packagesJudgedByTotal` の一覧には無い）。
- カバレッジ（frontend、道具のファイルを含む）: 全体 行 95.16%・分岐 90.37%（③ は 行 95.21%・分岐 90.36%）。道具のファイルは 行 94.23%・分岐 90.65%。
- OSV-Scanner・SpotBugs・Gitleaks・evidence の検査を含め、統合を止める指摘は無し。

#### E2E

- コマンド: `caffeinate -dims ./gradlew e2eTest`（見本の PG は E2E の上乗せのまま動かしたものを使い、止めていない）。
- 2026-11-02 06:53:28〜07:07:44、**BUILD SUCCESSFUL in 14m 15s**、Playwright **277 passed（14.2m）**、失敗 0（axe の検査を含む）。件数は ③ と同じ（E2E は足していない）。
- 報告の確かめ: 残してはならない値の見つかった件数 0（値の種類 13・json の報告 1・添付 305）。流れが作ったスキーマとロールは e2eTest の片付けが消す。
- 眠りの確かめ: E2E の間（06:53〜07:20）の `pmset -g log` は `Assertions` だけで、`Sleep`・`DarkWake`・`Wake` は 0 件。

Step 22 のうち、`develop` への squash の統合は指揮役が依頼者の承認を得て行う（Step 22 の印は付けていない）。

### 提案するコミットの区切り（④）

最初のコミットの時点でコンパイルが通る形にする。

- C1 業務データの一覧の並びを先頭 100 字に揃える（P1、不具合の直しと再現のテストを同じコミットに）: `RowSqlBuilder.java`・`RowSqlExecutorTest.java`・`AbstractDeepPageSortIT.java`・`MysqlDeepPageSortIT.java`・`MariadbDeepPageSortIT.java`・`PostgresDeepPageSortIT.java`
- C2 参照の段の WARN に stage を必ず出す（T3、直しとテストを同じコミットに）: `BusinessDataWriteService.java`・`BusinessDataWriteReferenceTest.java`・`FakeBusinessDataPorts.java`
- C3 perf の伏せ字の道具をカバレッジ・ESLint・型の検査の対象に入れる（T1・T2）: `perf/redact-k6-evidence.mjs`・`frontend/vitest.config.ts`・`frontend/eslint.config.js`・`frontend/tsconfig.perf-tool.json`・`build.gradle.kts`
- C4 記録: `code-generation-plan.md`（Step 18〜21 の印）・`generation-notes.md`・監査の記録

### 計画との差（④）

- P1 は計画の「起きなければ閉じる」でなく、起きたため依頼者の決定 案 A で直した（計画どおりの分岐）。再現のテストは MySQL だけでなく 3種類の DB の子のクラスにした（既存の形と team.md の 3種類の決まりに合わせた）。
- T1 で `coverage.allowExternal: true` を足した（frontend/ の外のファイルを計測するのに要る。計測の対象は include の一覧だけのまま）。
- T2 の ESLint は frontend の `npm run lint`（`eslint .`）ではなく、新しい Gradle のタスク `perfToolEslint` から根で呼ぶ形にした（既存の `perfToolLint`・`perfToolFormatCheck` と同じ形）。
- T2 の実験で一度 `sed -i.bak` で `frontend/eslint.config.js` を変えてすぐ元に戻した（決まりの「シェルで書かない」との差。最終の内容はファイルの道具で書いた）。
- verify の 1 回目の `checkToolchain` の失敗（上）。

## ⑤ README の書き直し（ブランチ `docs/261101-readme`、2026-11-02）

`develop`（`5950d786`）から `docs/261101-readme` を作った（同じ作業フォルダの中）。始める前の作業フォルダの未コミットの変更は、`.idea/misc.xml`（この作業の前から）と監査の記録・`.testing-brief.txt`（指揮役のもの）だけで、どれも触っていない。

### Step 23（FR8.4）: README の節を参照している箇所

`aidlc/spaces/default/memory/`・`.github/`・`docker/`・`perf/`・`frontend/`・`backend/` と根の設定のファイルを `grep "README"` で洗い出した（`node_modules`・`build`・`vendor`・`aidlc/spaces/default/intents/` の外）。節の名前を「」で参照しているもの:

| 参照の側 | 参照している節 | 書き直しの後 |
|---|---|---|
| `.env.example`（20・25 行） | README「コンテナの資源の上限」 | そのまま当たる |
| `.env.example`（38 行） | README「監査ログ（U4）」の既知の制約 | 切れた → 「監査ログ」に直した |
| `.env.example`（158 行） | README「メール（U1）」 | 切れた → 「メール」に直した |
| `.env.targetdb.example`（29 行） | README「対象DB」の節 | そのまま当たる |
| `backend/src/main/resources/application.yaml`（138 行） | README「メール（U1）」 | 切れた → 「メール」に直した |
| `backend/src/main/resources/application.yaml`（198 行） | README「監査ログ（U4）」の既知の制約 | 切れた → 「監査ログ」に直した |
| `backend/src/main/resources/application.yaml`（397 行） | README「例外のログ」 | そのまま当たる |
| `backend/src/main/resources/db/migration/V7・V8・V10・V11・V12`（コメント） | README「戻し方」 | そのまま当たる（移行のファイルは Flyway の照合の値が変わるため触らない） |
| `build.gradle.kts`（69・321・575・616 行） | README「前提の道具」「依存関係の脆弱性の判定」「E2E の見本の PostgreSQL」 | そのまま当たる（「E2E の見本の PostgreSQL」は見出しの単位の名前を外して文字どおり一致） |
| `build.gradle.kts`（642 行） | 「README の U2 の手順」 | 切れた → 「既にある見本の DB に書き込みのアカウントを足す」の手順に直した |
| `build.gradle.kts`（831 行） | README「メール（U1）」 | 切れた → 「メール」に直した |
| `build.gradle.kts`（686・788 行） | README の「手で消す手順」 | そのまま当たる |
| `compose.yaml`（58・80・104 行） | README「コンテナの資源の上限」「手元の監視」 | そのまま当たる |
| `docker/hikari-pool.sh`（42・164 行） | README「内部DBのファイルの詰め直し」 | そのまま当たる |
| `docker/targetdb/*/03-writer-account.sh`・`backend/src/test/.../TargetDbSampleInitScriptIT.java` | 「README」（節の名前なし） | そのまま |
| `settings.gradle.kts`（36 行）・`gradle/libs.versions.toml`（67・74 行） | README「取得と準備」「ライセンス」 | そのまま当たる |
| `docker/perf/compose.yaml`・`perf/*.sh`・`perf/biz/make-biz-seed.sh`・`perf/lib/perf-db-url.sh`・`perf/k6/*` | perf/README「DSL の時間を測る」「業務データの場面」「今の基準の一覧」「k6 の外の線」「利用者の設定と招待の場面」「利用者の管理の場面」「接続プールの上限を下げて流す」「グループの管理の場面」「ロールの管理の場面」「他テーブル参照の保存前の確かめの場面」「(b) 節」「受け付ける形」「準備の台本」 | そのまま当たる（perf/README の見出しの名前は変えていない） |
| `frontend/e2e/*.e2e.ts`・`frontend/e2e/support/traceMode.ts`・`frontend/src/shared/unsaved-guard/useUnsavedGuard.ts` | 「README を見直す」「README の既知の制約」など（節の名前なし） | そのまま |
| `frontend/src/app/registry/types.ts` | `features/README.md` の節 | 別の文書（対象外） |
| `aidlc/spaces/default/memory/team.md`・`project.md` | 「README の DOCKER_HOST」「README の手順」「perf/README.md」など（節の名前・番号の参照なし） | そのまま。**決まりの文書の直しは要らない** |

### Step 24（FR8.2）: 書き直しの前の一覧

書き直しの前の版は `5950d786` で、同じ一覧を git から取り直せる。取り出し方と、前の版の件数・指紋は付録 A に置いた。

### Step 25（FR8.1・FR8.3）: README.md

- 1,678 行 → 1,660 行。冒頭の説明（「アプリの骨格（U1）」）を今の機能の一覧に書き直した。
- make-you-chic-ui の固定先の記述（`ecb07e9` と移り変わりの列）を、今の固定先（make-you-chic-ui `cae5fed`・java-mustache-processor `d61ba6d`（0.1.1））だけにした。
- `./gradlew verify` の説明の「Node.js の版が 24 でない」を 26 に直した（② で値を上げたときの残り）。
- 見出しと本文から、Intent の名前・単位（`（U1）`〜`（U8）`、`U2 が提供` など）・Bolt（B3〜B9）の名前を外した。単位の名前で機能を指していた所（差し込み口の表の「使う単位」の列、アクセス制御の表の「受け持つ単位」の列など）は機能の名前（認証・アクセス制御・監査ログ・招待 など）に置き換えた。
- 経緯だけの記述を消した: 「以前は〜」「今までどおり」「〜で足しました」「後の単位の受け持ちです」、既知の違反が解消した経緯（E2E 050〜100 の注記、ブランドカラーのコントラストの節の「解消した既知の制約」）、3つの「契約との差」の節（招待と登録の完了は、今も効く形だけを「形の補足」として残した）、V7・V8 の移行の確かめの経緯、配備の段で決めるという記述。
- 節の並びは大きく入れ替えていない（計画との差 1）。

### Step 26（FR8.1）: perf/README.md

- 1,753 行 → 1,558 行。末尾の「## 履歴」（見出しの対応の表と H1〜H76）を消し、本文からその節を指す「（履歴 Hnn）」の参照をすべて外した（冒頭の「過去の結果は履歴の章を見る」は、`aidlc/` の記録と git の履歴を指す文に直した）。
- 単位の名前（`U2-NFR6.1` などの目標の ID の頭、`U1`〜`U5` の場面の呼び方）・Bolt の名前（B4〜B7）・Intent の名前を外し、場面の名前（ロールの管理の場面・参照の読み取りの場面 など）に置き換えた。`(a)`〜`(c)` の見出しの要件の ID も外した（`perf/dsl-mixed-memory.sh` が参照する「(b) 節」の見出しの頭 `(b)` は残した）。
- 線の経緯の文（「〜に緩めた」「〜に改めた」「Build and Test の決定」）は、今の線の値だけを書く形にした。**合否の線と物差しの値は変えていない**（「今の基準の一覧」の表は、注記の経緯だけを外し、値と物差しは計画の「Build and Test に引き継ぐこと」の表と同じ）。
- 例のタグ `mastersmith:<Intent の名前>` は `<試験の名前>` にした（Intent ごとに作る決まりの文も「試験ごと」に）。

### Step 27（FR8.2・FR8.4）: 前後の比べと参照

- コマンドの行（コードブロックの中の、コメントだけの行と空行を除いた行）: README.md は 236 行 → 236 行で**差なし**。perf/README.md は 679 行 → 679 行で、中身の変わった行は次の5か所だけ（どれも意図したもの）:
  1. `docker build -t mastersmith:<Intent の名前> .   # 例: mastersmith:user-admin-followup` → `<試験の名前>`・例 `perf-run`（Intent の名前の削除）
  2. `MASTERSMITH_IMAGE_TAG=<Intent の名前>` → `<試験の名前>`（同じ）
  3. `zsh "$D/role-run.sh" "$D"   # B4・B5 の場面（上の手順）` → コメントを「ロールの管理の場面」に（Bolt の名前の削除）
  4. `docker exec … memory.peak …   # …を参照）（履歴 H31）` → 末尾の「（履歴 H31）」を消した（履歴の参照の削除）
  5. `zsh "$D/role-run.sh" "$D"   # ロールの管理の節の B4・B5 の場面（流すときだけ）` → 「ロールの管理の節の場面」（Bolt の名前の削除）
  - ほかに、コードブロックの中のコメントだけの行「# 前例は履歴 H50 にある。」を前の行のコメントに寄せて消した（コメントだけの行のため、コマンドの行には数えない）。
- 大文字の名前の一覧（`[A-Z][A-Z0-9]*_[A-Z0-9_]*[A-Z0-9]` の重なりを除いた一覧）: README.md は 240 → 240、perf/README.md は 117 → 117 で、**どちらも一致**（付録 B）。
- Step 23 の切れた参照6か所を参照の側で直した（上の表）。`team.md`・`project.md` は直していない（直す必要が無い）。
- 書き直しの後に、Intent・Bolt・単位の名前と古い版（`25.0.4`・`ecb07e9`・`v1.32`・`0.38.0`・`0.163`）を両方の README で探し、0 件を確かめた（例外: 「機能の Intent ごとに代表の流れを1本まで」は team.md の決まりの引用、`V7__u2_…` などは移行のファイルの名前）。

### 書き直しの途中の誤りと戻し

- 同じファイルへの `replace_all` を並べて送ったため、一部の置き換えが後の書き込みで上書きされて戻った（`（U1）` などが 53 か所のうち 35 か所残った）。1つずつ順に流し直して 0 件にした。ほかの直し（置き換えの対象が1か所のもの）は、並べて送った後に目印の文字列を数え、すべて残っていることを確かめた。
- 行末の断片を空に置き換えると、その後の改行も一緒に消える動きがあり、README.md の見出しの後の空行が 13 か所、冒頭の段落の後の空行が1か所、perf/README.md のコードブロックの1行（`pmset -g log …`）が前の行とつながった。どれも戻し、見出しの直後の空行の抜けを前の版と比べて 0 件（コードブロックの中のコメントを除く）であること、コマンドの行の比べで上の5か所のほかに差が無いことを確かめた。

### 計画との差（⑤）

1. 節の並びは、計画の目安（前提 → 起動と配備 → 運用 → 開発）に大きく入れ替えず、今の並びのまま中身を書き直した。README は 1,600 行を超え、並べ替えると差が読めなくなり、コマンドの行の前後の比べと節の参照の確かめが難しくなるため。
2. Step 24 の一覧は、全文を貼らず、取り出し方・前の版のコミット・件数・SHA-256（コマンドの行）と、名前の一覧（付録 B）で残した。
3. 参照の直しで `backend/src/main/resources/application.yaml` のコメント2行を変えた（文書だけのブランチの例外。コードと設定の値は変えていない）。
4. perf/README.md の目標の ID のうち、単位の名前を頭に持たないもの（`NFR2.7`・`NFR5.1` など、設計の記録の要件の ID）は残した。単位の名前（`U2-` など）を頭に持つ ID と、単位の名前を頭に持つ確かめの ID（`U4-STORAGE` など → `STORAGE`）は外した。
5. **依頼者の決定 B（2026-11-02）**: 書き直しの後も README.md が 1,660 行・約 305 KB と重いままだったため、README.md を入口（概要・前提・最初の起動・文書の目次・ライセンス）にし、詳しい節を `docs/` の主題ごとのファイルに移した。計画の「節の並びを大きく変えない」との差。中身（コマンド・前提・設定の値・環境変数の名前）は変えず、移した先で切れる節の参照だけを直した（説明の文を短く書き直す案 C は選ばれていない）。詳細は下の「決定 B: README を入口にして docs/ へ分ける」。

### Step 28（NFR3）: verify

- 始める前に `./gradlew --stop`。電源は AC（電池 100%）、`pmset -g assertions` は `PreventUserIdleSystemSleep 1`。
- コマンド: `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew verify`（文書だけのため clean なし）。
- 2026-11-02 07:45:52〜08:12:29、**BUILD SUCCESSFUL in 26m 31s**（1回目で通過）。`application.yaml` のコメントを変えたため backend のテストも流れた（30 タスク実行・13 UP-TO-DATE）。初回の読み込みの JavaScript は 166.4 KB（gzip）で変わらず。
- 間の `pmset -g log`（07:45〜08:13）: 種類は `Assertions` だけで、`Sleep`・`DarkWake`・`Wake` は 0 件。結果は使える。
- Markdown は Prettier・ライセンスヘッダーの検査の対象に入っていない（`build.gradle.kts`・`frontend/package.json`・`.pre-commit-config.yaml` に `.md` の対象なし）。変えた `build.gradle.kts`・`application.yaml`・`.env.example` は verify の各段を通った。
- E2E は流していない（文書と設定のコメントだけのブランチ。team.md の決まりで Build and Test で1回流す）。
- `develop` への squash の統合は指揮役が依頼者の承認を得て行う（Step 28 の印は付けていない）。

### 提案するコミットの区切り（⑤）

- C1 README と perf/README の書き直し: `README.md`・`perf/README.md`
- C2 README の節の参照を直す: `build.gradle.kts`・`.env.example`・`backend/src/main/resources/application.yaml`
- C3 記録: `code-generation-plan.md`（Step 23〜27・29 の印）・`generation-notes.md`・`code-summary.md`・`traceability.json`・監査の記録
- `.idea/misc.xml`（この作業の前からの変更）と `.testing-brief.txt` はどれにも入れない。
- 決定 B の後の区切りは、下の「決定 B」の節の「提案するコミットの区切り（決定 B の後）」に置き換える。

### 決定 B: README を入口にして docs/ へ分ける（2026-11-02）

#### 分け方

README.md の節の並び（`5950d786` からの書き直しの後のもの）を主題でまとめ、節の見出しの名前は変えずに移した（見出しの段は、`docs/deployment.md` と `docs/internal-db.md` に移した「コンテナでの起動と確認」の下の `###`・`####` だけを1段ずつ上げた）。各ファイルの先頭に H1 と、何の文書かの1行を置いた。`docs/README.md` は置かず、目次は README.md の「文書の目次」にした。

| ファイル | 行 | 移した節 |
|---|---|---|
| `README.md` | 161 | 概要・前提の道具・取得と準備・開発時の起動・コンテナでの起動と確認（最初の起動。その下の小節は docs へ）・文書の目次（新しく足した）・ライセンス |
| `docs/verification.md` | 252 | 1コマンドの検査・依存関係の脆弱性の判定・リリースの前の確かめ・ビルドした WAR での画面の確認（E2E）・E2E の見本の PostgreSQL・結果と報告 |
| `docs/deployment.md` | 116 | 初期管理者の救済・戻し方・コンテナの資源の上限（既知の制約・設定の効き方の確かめ）・プロキシを置く配備・スキーマの変更（Flyway） |
| `docs/internal-db.md` | 171 | 内部DBのバックアップと戻し方・内部DBの複写を数える・内部DBのファイルの詰め直し |
| `docs/configuration.md` | 84 | 環境変数 |
| `docs/mail.md` | 49 | メール（暗号化の方式・運用で設定しない値・Mailpit） |
| `docs/target-db.md` | 151 | 対象DB（読み取りの API・書き込みの API・結合テスト・手元で試す対象DB・書き込みのアカウントを足す） |
| `docs/dsl.md` | 142 | DSL の書式・既定の DSL の生成・DSL の管理の API（監査・指標とログ）・DSL の管理画面 |
| `docs/users.md` | 160 | 利用者の管理の API・プリファレンスとパスワードの変更・招待と登録の完了・招待の管理の画面・登録の完了の画面・プリファレンスの画面 |
| `docs/roles-and-permissions.md` | 177 | 「権限が無い」の扱い・グループ・ロールと権限（割り当てと作業ロール・権限の受け渡し）・業務のメニュー・受け渡しの画面・骨組みの画面・API のアクセス制御 |
| `docs/operations.md` | 158 | 外部エクスポートの確かめ方・手元の監視・警報と対応の手順・例外のログ・監査ログ |
| `docs/appearance.md` | 64 | インスタンスの見た目の設定・画面の表示の設定・ブランドカラーのコントラスト |
| `docs/extension-points.md` | 30 | 機能が使う差し込み口 |

- README.md: 1,660 行・304,503 バイト → 161 行・21,853 バイト。README.md と docs/ を合わせて 1,715 行・311,592 バイト（増えた分は各ファイルの H1・説明の1行・目次と、参照に足したファイルの名前）。
- 移すたびに、元の行の範囲と移した先を `diff` で比べ、差が参照の直し（「」の前に `docs/…md` の・`README.md` の を足す、「上の・下の・次の節」を移った先の名前にする、「この README」→「この文書」）だけであることを確かめた（空行の差を除く）。

#### 確かめ 1: コマンドの行と大文字の名前

付録 A・B と同じ取り出し方で、`README.md` と `docs/*.md` を合わせた一覧を `5950d786` の README.md と比べた。

- コマンドの行（コードブロックの中の、空行とコメントだけの行を除いた行）: 236 行 → 236 行。違うのは1行だけで、`docs/deployment.md` の戻し方の `MASTERSMITH_IMAGE_TAG=pre-dsl docker compose ps app` の行末のコメント「上のスモークテストを行う」→「README.md のスモークテストを行う」（スモークテストが README.md に残り、戻し方が docs に移ったための参照の直し。意図したもの）。前の担当の書き直しの差（README.md は 0 件）と合わせて、ほかの差は無い。コメントだけの行では、`README.md` の「2回目以降は…」の2行と `docs/deployment.md` の「今の内部DBのデータを複写する（次の節…）」の1行の参照を、移った先の名前に直した（比べの対象の外）。
- 大文字の名前（`[A-Z][A-Z0-9]*_[A-Z0-9_]*[A-Z0-9]` の重なりを除いた一覧）: 前後とも 247 個で**一致**（付録 B の環境変数の名前を含む）。
- 移さずに重なりをまとめたものは無い（README の中に同じ手順の繰り返しは見当たらず、移すだけにした）。
- `docs/internal-db.md` の戻すときのコマンドの行末のコメント「戻し方の節の MASTERSMITH_IMAGE_TAG を付ける」は、コマンドの行を変えないため直さなかった（「戻し方」は `docs/deployment.md` にあり、README.md の目次からたどれる）。

#### 確かめ 2: 参照の洗い出しのやり直し

`git grep README`（`aidlc/spaces/default/intents/`・`vendor/`・`docs/`・`README.md`・`aidlc/spaces/default/codekb/`・`.claude/` の外）で洗い出し、節が docs に移ったものを移った先に直した。

| 参照の側 | 直した先 |
|---|---|
| `.env.example`（20・25・38・158 行） | `docs/deployment.md`「コンテナの資源の上限」・`docs/operations.md`「監査ログ」・`docs/mail.md`「メール」 |
| `.env.targetdb.example`（29 行） | `docs/target-db.md`「対象DB」 |
| `.gitignore`（42 行） | `docs/internal-db.md` の手順 |
| `compose.yaml`（58・80・104 行） | `docs/deployment.md`「コンテナの資源の上限」・`docs/operations.md`「手元の監視」 |
| `docker/hikari-pool.sh`（42・164 行） | `docs/internal-db.md`「内部DBのファイルの詰め直し」 |
| `docker/targetdb/*/03-writer-account.sh`（17 行、3つ） | `docs/target-db.md` |
| `backend/src/main/resources/application.yaml`（16・138・189・198・206・397 行。コメントだけ） | `docs/configuration.md`・`docs/mail.md`・`docs/operations.md`・`docs/internal-db.md` |
| `backend/src/test/java/cherry/mastersmith/targetdb/TargetDbSampleInitScriptIT.java`（53 行。コメントだけ） | `docs/target-db.md` |
| `build.gradle.kts`（321・575・616・642・686・788・831 行。失敗の案内の文） | `docs/verification.md`・`docs/target-db.md`・`docs/mail.md` |
| `.github/dependabot.yml`（84・112 行。手で揃える固定先の一覧） | `docs/` の文書（`docs/internal-db.md`・`docs/dsl.md`・`docs/operations.md` の eclipse-temurin、`docs/mail.md` の Mailpit）。89 行は過去の作業の記述のため変えていない |
| `frontend/e2e/050・060・070・080・100`（失敗の文言とコメント）・`frontend/e2e/support/traceMode.ts` | `docs/verification.md`・`docs/appearance.md`（テストのファイルだけ。Prettier の確かめを通過） |
| `perf/README.md`（115・831・1229 行） | `docs/deployment.md`「コンテナの資源の上限」・`docs/operations.md`「手元の監視（Grafana）」・`docs/target-db.md`「業務データの読み取りの API」 |
| `perf/k6/thresholds-test.sh`（31 行） | `docs/target-db.md` の DOCKER_HOST |

直さなかったもの:

- `backend/src/main/resources/db/migration/V7・V8・V10・V11・V12` の「README の「戻し方」」: Flyway の照合の値が変わるため触らない。README.md の「文書の目次」の `docs/deployment.md` の行に「戻し方」を節の名前として置き、README からたどれるようにした。
- `frontend/src/shared/unsaved-guard/useUnsavedGuard.ts`（33 行）の「README に書く」: frontend の本体（`frontend/src`）のため、触ると統合の前に E2E が要る（team.md の Way of Working）。中身は `docs/roles-and-permissions.md` の「未保存の確かめ」にある。指揮役に扱いを諮る。
- `build.gradle.kts`（69 行「前提の道具」）・`settings.gradle.kts`（36 行「取得と準備」）・`gradle/libs.versions.toml`（67・74 行「ライセンス」）: README.md に残した節のため、そのまま当たる。
- `perf/dsl-timing.sh`（121 行「README の手順で作ってください」）: イメージの作り方は README.md の「コンテナでの起動と確認」と perf/README.md の手順 0 の両方にあり、どちらを指しても当たるためそのまま。
- `frontend/src/app/registry/types.ts`: `features/README.md` の参照（別の文書）。
- **`aidlc/spaces/default/memory/team.md`（229 行）**: 「見本の対象DB に足す書き込みのアカウントとその権限は、README と見本の初期化に書く」。手順は `docs/target-db.md` に移ったため、文言が今の置き場と食い違う。決まりの文書のため**直さずに指揮役へ報告した**。
- `aidlc/spaces/default/memory/project.md`（61 行ほか）: 「README の DOCKER_HOST と…」などは過去の学びの記録（出来事の記述）で、決まりの置き場を指すものではないため、直す必要は無いと読んだ（判断は指揮役に委ねる）。

#### 確かめ 3: 検査の対象

- `docs/*.md` は Prettier（`frontend/` の中だけ）・ライセンスヘッダーの検査（Java・Kotlin DSL・Mustache・画面のファイル）・リンタのどれの対象にも当たらない。README.md と同じ扱い（ライセンスヘッダーなし）にした。
- 直した `frontend/e2e/*.ts` は `npx prettier --check` を通過。

#### 確かめ 5: verify

- 始める前に `./gradlew --stop`。電源は AC（電池 100%）。
- コマンド: `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock caffeinate -dims ./gradlew verify`（clean なし）。
- 2026-11-02 08:56〜09:23、**BUILD SUCCESSFUL in 26m 41s**（1回目で通過。43 タスク、33 実行・10 UP-TO-DATE）。初回の読み込みの JavaScript は 166.4 KB（gzip）で変わらず。
- 間の `pmset -g log`（08:56〜09:40）の `Sleep`・`DarkWake`・`Wake` は 0 件。結果は使える。
- E2E は流していない（frontend の本体と認証・権限・業務データの `src/main` に触れていない。テストのファイルと文書・設定のコメントだけ）。

#### 提案するコミットの区切り（決定 B の後）

- C1 README を入口にして docs/ へ分け、perf/README を書き直す: `README.md`・`docs/`（12 ファイル）・`perf/README.md`
- C2 README の節の参照を移った先に直す: `.env.example`・`.env.targetdb.example`・`.gitignore`・`compose.yaml`・`.github/dependabot.yml`・`build.gradle.kts`・`backend/src/main/resources/application.yaml`・`backend/src/test/java/cherry/mastersmith/targetdb/TargetDbSampleInitScriptIT.java`・`docker/hikari-pool.sh`・`docker/targetdb/*/03-writer-account.sh`・`perf/k6/thresholds-test.sh`・`frontend/e2e/`（050・060・070・080・100・`support/traceMode.ts`）
- C3 記録: `code-generation-plan.md`・`generation-notes.md`・`code-summary.md`・`traceability.json`・監査の記録
- `.idea/misc.xml`（この作業の前からの変更）・`.testing-brief.txt`・`aidlc/.aidlc-subagent-inflight` はどれにも入れない。

#### 確かめ 4: リンク

- README.md と docs の中に Markdown のリンク（`[…](…)`）は無い（前から無い）。文書の間の参照は「`docs/…md` の「節の名前」」の形。
- 一時の台本（リポジトリに残していない）で、README.md・docs/*.md・perf/README.md の「」の中の名前のうち、どれかのファイルの見出しと一致するものを拾い、前にファイルの名前があればそのファイルに、無ければ同じファイルにその見出しがあるかを照らした。切れた参照は 0 件（拾った 8 件は、サイドバーの項目の名前「DSL」、ダッシュボードの名前「MasterSmith」、「`docs/…md` の「A」の「B」」の続きの「B」で、どれも参照先は正しい）。

## 付録 A: 書き直しの前のコマンドの行（Step 24）

前の版は `5950d786`。コマンドの行は、コードブロック（行頭の空白の後に ```` ``` ```` で始まる行で囲む）の中の、空行とコメントだけの行（行頭の空白の後に `#`）を除いた行とし、次の形で取り出した（出力はファイルに書かず、比べはその場で `diff` に渡した）。

```bash
git show 5950d786:README.md | awk '/^[[:space:]]*```/{inb=!inb;next} inb && $0 !~ /^[[:space:]]*#/ && $0 !~ /^[[:space:]]*$/'
```

| ファイル | コマンドの行の数 | SHA-256（行を改行でつないだもの） | 大文字の名前の数 | SHA-256（名前の一覧） |
|---|---|---|---|---|
| README.md | 236 | `677c98569827f32b706183709ee1846a5b580cf883c3259057d529086047bc83` | 240 | `d4b2798fc5aa5fb92dd0c7e91f1a156c49c6b89b45726555022ade9c0c0dc8f8` |
| perf/README.md | 679 | `7b88a5ef70b670580ef66437d3e769005756c96604d24365e51d532dcab323fd` | 117 | `f78d8c2d033c11cce3939cd64ec90e274450b6f2ff02c79a5fa9674baf6cbe7f` |

## 付録 B: 書き直しの前の環境変数の名前（Step 24）

大文字の名前の一覧のうち、環境変数として使う名前（接頭辞 `MASTERSMITH_`・`SPRING_`・`E2E_`・`PERF_`・`K6_`・`DOCKER_`・`TESTCONTAINERS_`・`MANAGEMENT_`・`LOGGING_`・`POSTGRES_`・`MYSQL_`・`MARIADB_`・`JAVA_`・`MONITOR_`・`BIZ_`・`ROLE_`（台本の変数）・`DSL_`（台本の変数）・`STORAGE_`・`BUSY_`）。監査の種類・code（`ROLE_ASSIGNED`・`DSL_BUSY` など）も同じ形のため一緒に並ぶ。書き直しの後も同じ一覧（差なし）。

- README.md: `DOCKER_HOST` `E2E_PORT` `E2E_TARGETDB_PORT` `E2E_TRACE` `JAVA_TOOL_OPTIONS` `LOGGING_LEVEL_CHERRY_MASTERSMITH_COMMON_ERROR` `LOGGING_LEVEL_JAKARTA_MAIL` `LOGGING_LEVEL_ORG_ECLIPSE_ANGUS` `MANAGEMENT_HEALTH_MAIL_ENABLED` `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` `MARIADB_ROOT_PASSWORD` `MASTERSMITH_APPEARANCE_BRAND_COLOR` `MASTERSMITH_APPEARANCE_FONT_FAMILY` `MASTERSMITH_AUTH_ACCESS_TOKEN_TTL` `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` `MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` `MASTERSMITH_AUTH_LOCK_DURATION` `MASTERSMITH_AUTH_LOCK_THRESHOLD` `MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST` `MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_CRON` `MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_RETENTION` `MASTERSMITH_AUTH_REFRESH_TOKEN_TTL` `MASTERSMITH_AUTH_SIGNING_KEY` `MASTERSMITH_CONTAINER_CPUS` `MASTERSMITH_CONTAINER_MEMORY` `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` `MASTERSMITH_DB_PASSWORD` `MASTERSMITH_DB_URL` `MASTERSMITH_DB_USERNAME` `MASTERSMITH_DSL_HISTORY_LIMIT` `MASTERSMITH_DSL_MAX_SUBMIT_SIZE` `MASTERSMITH_HEALTH_DB_TIMEOUT` `MASTERSMITH_IMAGE_TAG` `MASTERSMITH_INVITATION_CLEANUP_CRON` `MASTERSMITH_INVITATION_RETENTION` `MASTERSMITH_INVITATION_VALIDITY` `MASTERSMITH_JAVA_OPTIONS` `MASTERSMITH_MAIL_FROM` `MASTERSMITH_MAIL_FROM_NAME` `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED` `MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT` `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` `MASTERSMITH_TARGET_DB_CONNECT_TIMEOUT` `MASTERSMITH_TARGET_DB_DATABASE` `MASTERSMITH_TARGET_DB_HOST` `MASTERSMITH_TARGET_DB_PASSWORD` `MASTERSMITH_TARGET_DB_POOL_IDLE_TIMEOUT` `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` `MASTERSMITH_TARGET_DB_PORT` `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_COMPARE` `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_DETAIL` `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_GENERATE` `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST` `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE` `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_WRITE` `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` `MASTERSMITH_TARGET_DB_SCHEMA` `MASTERSMITH_TARGET_DB_TYPE` `MASTERSMITH_TARGET_DB_USERNAME` `MASTERSMITH_TARGET_DB_WRITE_PASSWORD` `MASTERSMITH_TARGET_DB_WRITE_POOL_MAXIMUM_SIZE` `MASTERSMITH_TARGET_DB_WRITE_USERNAME` `MASTERSMITH_TRACE_ENTER_MESSAGE` `MASTERSMITH_TRACE_EXCEPTION_MESSAGE` `MASTERSMITH_TRACE_EXIT_MESSAGE` `MASTERSMITH_TRACE_HIDE_PROXY_CLASS_NAMES` `MASTERSMITH_TRACE_LOG_EXCEPTION_STACK_TRACE` `MASTERSMITH_TRACE_USE_DYNAMIC_LOGGER` `MASTERSMITH_TRACING_SAMPLING_PROBABILITY` `MASTERSMITH_WEB_BASE_URL` `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE` `MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS` `MONITOR_BIZ` `MONITOR_DURATION` `MYSQL_PWD` `MYSQL_ROOT_PASSWORD` `POSTGRES_PASSWORD` `SPRING_MAIL_HOST` `SPRING_MAIL_PASSWORD` `SPRING_MAIL_PORT` `SPRING_MAIL_PROPERTIES_MAIL_DEBUG` `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT` `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED` `SPRING_MAIL_PROTOCOL` `SPRING_MAIL_SSL_ENABLED` `SPRING_MAIL_TEST_CONNECTION` `SPRING_MAIL_USERNAME` `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`
- perf/README.md: `BIZ_DURATION` `BIZ_LARGE_ITERATIONS` `BIZ_P95_LIMIT_MS` `BUSY_ATTEMPTS` `DSL_FILE` `DSL_HISTORY_MIN` `K6_IMAGE` `LOGGING_LEVEL_CHERRY_MASTERSMITH_AUDIT_SERVICE` `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` `MARIADB_ROOT_PASSWORD` `MASTERSMITH_AUTH_ACCESS_TOKEN_TTL` `MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` `MASTERSMITH_AUTH_SIGNING_KEY` `MASTERSMITH_CONTAINER_CPUS` `MASTERSMITH_CONTAINER_MEMORY` `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` `MASTERSMITH_DB_URL` `MASTERSMITH_IMAGE_TAG` `MASTERSMITH_JAVA_OPTIONS` `MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE` `MASTERSMITH_PERF_ENV_FILE` `MASTERSMITH_PERF_TARGETDB_ENV_FILE` `MASTERSMITH_PERF_TARGETDB_MARIADB_BUFFER_POOL` `MASTERSMITH_PERF_TARGETDB_MARIADB_MEMORY` `MASTERSMITH_PERF_TARGETDB_MYSQL_BUFFER_POOL` `MASTERSMITH_PERF_TARGETDB_MYSQL_MEMORY` `MASTERSMITH_PERF_TARGETDB_POSTGRES_MEMORY` `MASTERSMITH_PERF_TARGETDB_POSTGRES_SHARED_BUFFERS` `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` `MASTERSMITH_TARGET_DB_DATABASE` `MASTERSMITH_TARGET_DB_PASSWORD` `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE` `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` `MASTERSMITH_TARGET_DB_TYPE` `MASTERSMITH_TARGET_DB_WRITE_PASSWORD` `MASTERSMITH_TARGET_DB_WRITE_USERNAME` `MONITOR_BIZ` `MONITOR_DURATION` `MYSQL_PWD` `MYSQL_ROOT_PASSWORD` `PERF_ADMIN_EMAIL` `PERF_ADMIN_PASSWORD` `PERF_DB_URL` `PERF_JRE_IMAGE` `PERF_PW_USER_COUNT` `PERF_UA_COUNT` `PERF_USER_COUNT` `PERF_USER_PASSWORD` `POSTGRES_PASSWORD` `ROLE_DURATION` `ROLE_LARGE_COUNT` `ROLE_LARGE_PREFIX` `ROLE_PREPARE_TOTAL` `ROLE_TREE_SCHEMA` `ROLE_TREE_TABLE` `ROLE_WORST_NAME` `ROLE_YAML_FILE` `SPRING_MAIL_HOST` `STORAGE_ROUNDS`

### ⑤ の依頼者の決定（2026-11-02）

- `aidlc/spaces/default/memory/team.md` 229 行の「見本の対象DB に足す書き込みのアカウントとその権限は、README と見本の初期化に書く」は変えない。「README」は README.md と、その目次からたどる `docs/` の一式を指すと読む（手順は `docs/target-db.md`）。
- `frontend/src/shared/unsaved-guard/useUnsavedGuard.ts` 33 行のコメントの「README に書く」は、同じ読み方で今も当たるため直さない（直すと frontend の本体の変更として統合の前の E2E が要る）。
- コミットは C1（README.md・docs/・perf/README.md）・C2（参照の直し）・C3（記録）に分け、`develop` へ squash で統合する。E2E は Build and Test で流す（E2E のファイルの変更はメッセージの文だけ）。
