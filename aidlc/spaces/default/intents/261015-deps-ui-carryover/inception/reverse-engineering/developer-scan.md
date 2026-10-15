# 開発担当のコードの読み取り（Intent 261015-deps-ui-carryover）

- 読み取りの幅: Focused scan（前の Intent 261004-role-menu の振り返り `aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/feedback-loop.md` 2節の第1の束 U1・P1〜P7 と第2の束 K1〜K5 に絞る）
- 読んだ時点: `develop` の HEAD `425ab64c7ef66687dae34f7adc5266c924e00ada`（2026-10-15）。読み取り（ファイルの表示・grep・`git submodule status`・`gh pr list`）だけで確かめ、ビルド・テスト・git の状態を変える操作はしていない。
- 書き方: 「確かめた事実」はファイルと行で裏付けたもの。「見立て」は未検証の仮説で、要件定義・コード生成で確かめる。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**（控えの範囲の中だけ）:
  - gradle/libs.versions.toml
  - settings.gradle.kts
  - build.gradle.kts（verify の並び・osvScan・npm の呼び出しの部分）
  - backend/build.gradle.kts
  - backend/src/main/java/cherry/mastersmith/role/store/RoleStore.java
  - backend/src/main/java/cherry/mastersmith/role/service/RoleStoreTransactions.java
  - backend/src/main/java/cherry/mastersmith/dsl/parse/SafeYamlParser.java
  - backend/src/main/java/cherry/mastersmith/dsl/parse/YamlLimits.java
  - backend/src/main/java/cherry/mastersmith/dsl/domain/DslError.java
  - backend/src/main/java/cherry/mastersmith/navigation/domain/MenuFilter.java（Javadoc の部分）
  - frontend/package.json
  - frontend/src/app/work-role/WorkRoleSwitcher.css
  - frontend/src/shared/tree/SharedTreeView.tsx
  - frontend/src/shared/tree/SharedTreeNodeItem.tsx
  - frontend/src/features/groupadmin/useSlowNotice.ts・frontend/src/features/roleadmin/useSlowNotice.ts・frontend/src/features/roletransfer/useSlowNotice.ts（3つの差分）
  - Dockerfile
  - .github/dependabot.yml
  - .github/workflows/ci.yml
  - config/npm-build-tools.txt
  - perf/k6/scenarios.js（場面の一覧の説明と setupRoles・setupRoleMembers・ロールの読み取りの3場面）
- **Skimmed only**（grep・一覧・一部の行だけ）:
  - backend/gradle.lockfile（版の行だけ）、frontend/package-lock.json（Dependabot の対象の版だけ）
  - compose.yaml・docker/perf/compose.yaml（`image:`・`mem_limit` の行だけ）、docker/hikari-pool.sh（イメージの行だけ）
  - backend/src/main/java/cherry/mastersmith/role/（RoleStore・RoleStoreTransactions 以外は grep。RoleAdminService は流れのコメントだけ）
  - backend/src/main/java/cherry/mastersmith/group/（BUSY と排他の grep だけ）
  - backend/src/main/java/cherry/mastersmith/dsl/・dslmanage/（上限の定数と byte[] の受け渡しの grep だけ）
  - backend/src/main/java/cherry/mastersmith/navigation/（record の `toString` の有無だけ）
  - backend/src/main/resources/application.yaml（上限・プール・タイムアウトの行だけ）
  - frontend/src/app/navigation/buildNavSections.ts・frontend/src/app/layout/ShellLayout.tsx（区画と文言の行だけ）、Dropdown の `placement` の grep
  - perf/README.md（イメージの固定と BUSY の数え方の行だけ）
- **控えの範囲の外で見たもの**（深く読んだ扱いにしない）: gradle/wrapper/gradle-wrapper.properties、backend/src/test/（ファイル名・archunit.properties・ArchUnit の読み込みの grep）、frontend/vitest.config.ts（`thresholds` の行）、README.md（イメージの行の grep）、`git submodule status`、`gh pr list`。

### Packages Found

- backend — Spring Boot 4.1.1 の実行可能 WAR — Java 25 — 今回は role・group（行の排他と BUSY）、dsl・dslmanage（DSL の読み込み）、navigation（record の伏せ字）が対象
- frontend — Vite 8 ＋ React 19 の SPA — TypeScript 6 — 今回は app/work-role・shared/tree・app/navigation・features の groupadmin・roleadmin・roletransfer が対象
- vendor/make-you-chic-ui — npm の `file:` 依存（サブモジュール、固定先 `5bf1ffe`）— 直接の変更は禁止
- vendor/java-mustache-processor — Gradle の composite build（サブモジュール、固定先 `8d44c36`、タグ 0.1.0）— 今回の範囲では触れない
- perf — k6 の台本（perf/k6/scenarios.js、2,086 行）と補助の台本

### Build System

- **Type**: Gradle 9.8.0（Kotlin DSL。wrapper の配布の URL）＋ npm（frontend・vendor/make-you-chic-ui）
- **Config Files**: settings.gradle.kts、build.gradle.kts、backend/build.gradle.kts、gradle/libs.versions.toml、backend/gradle.lockfile（257 行）、frontend/package.json・frontend/package-lock.json
- **Build Dependencies**: `verify`（ルート）→ vendorInstall → vendorBuild → frontendInstall → 画面の検査とテスト → backend の Spotless・テスト・結合テスト・JaCoCo・SpotBugs の関門 → gitleaksScan・osvScan。bootWar は `:frontendBuild` の dist を `WEB-INF/classes/static` に同梱する。

### APIs Discovered

今回の範囲では API の足し引きは無い。BUSY を返す経路だけを記録する（確かめた事実）。

- `ROLE_BUSY`（409）— role/web の MeWorkRoleController・RoleAssignmentController・RoleAdminController・RoleTransferController が、結果の型の `Busy` を `switch` で `BusinessException` に変える（例: role/web/RoleAdminController.java 121・186 行）
- `GROUP_BUSY`（409）— group/web/GroupAdminController.java 123・215 行、role/web/RoleAssignmentController.java 156 行（グループの行の待ち）

### Frameworks & Libraries

今回の U1 に関わるものだけ。版は確かめた事実（lockfile・package-lock の解決済みの版）。

- Spring Boot — 4.1.1 — 土台（BOM で多くの版を管理）
- Tomcat — 11.0.26（libs.versions.toml で上書き）／Jackson — 3.1.7（Jackson の BOM で上書き）／opentelemetry-logback-appender — 2.28.1-alpha（固定）／networknt json-schema-validator — 3.0.6（固定）／snakeyaml — 2.7
- Spotless プラグイン — 8.10.3（Dependabot #47 で 8.10.4）、jqwik — 1.10.1（#37 で 1.10.2）、Gradle wrapper — 9.8.0（#46 で 9.8.1）
- H2 — 2.4.240（`-Dh2.compactThreads=1` を Dockerfile・テストの JVM でそろえている）
- npm（解決済み）: vite 8.3.1（#41 → 8.3.4。成果物を作る道具）、@vitejs/plugin-react 6.1.1（#49。成果物を作る道具）、vitest 5.0.2・@vitest/coverage-v8 5.0.2（#24・#27）、axe-core 4.13.0（#39）、eslint 10.11.0（#32）、oxlint 1.85.0（#45）、stylelint 17.15.0（#34）、@types/node 26.6.3（#42）、@typescript-eslint/parser 8.70.1（#48）、typescript 6.0.3（大きな版だけ ignore）
- イメージ: eclipse-temurin `25.0.4_7-jre-noble@sha256:b573af9e…`（#38 → `25.0.4.1_1-jre-noble`）、grafana/otel-lgtm `0.34.0@sha256:b966ea10…`（#36 → 0.36.0）、axllent/mailpit `v1.31.2@sha256:74d609a4…`（#35 → v1.31.5）、otel/opentelemetry-collector 0.162.0、postgres 18.6、mysql 26.7.0、mariadb 13.0.2、grafana/k6 2.3.0（perf/README.md だけ）
- Actions（コミットのハッシュで固定、コメントが版の名前）: checkout v7.0.1、setup-java v6.0.1、setup-node v7.0.0（#43 → 7.1.0）、setup-gradle v6.3.0（#25 → 6.4.0）、upload-artifact v7.0.1（#40 → 7.0.2）、download-artifact v8.0.1（#44 → 8.0.2）。Gitleaks 8.30.1・OSV-Scanner 2.6.0 は ci.yml の env で版と SHA-256 を固定（Dependabot の対象外）

### Test Coverage

- **Test Directories**: backend/src/test/java（対象と同じパッケージ構成。`*Test` は単体・`*IT` は結合）、frontend/src（対象と同じ場所の `*.test.ts(x)`）、frontend/e2e（Playwright。中は読んでいない）
- **Test Frameworks**: JUnit 5・jqwik・ArchUnit 1.5.1・Testcontainers・subethasmtp／Vitest・Testing Library・vitest-axe・fast-check・Playwright
- **Coverage Config**: present。
  - backend: JaCoCo 0.8.15。全体と、`packagesJudgedByTotal` 以外のパッケージごとに行 80%・分岐 70%（backend/build.gradle.kts）。一覧は 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）で、team.md の記述と一致。role・group・dsl・dslmanage・navigation は一覧に無く、パッケージごとの下限の対象。
  - frontend: frontend/vitest.config.ts の `thresholds`（lines 80・branches 70）。
- テストの JVM のヒープ（P7）: 共通 `maxHeapSize = "1g"`、単体テスト（`tasks.test`）だけ `"1536m"`（backend/build.gradle.kts、Intent 261004-role-menu の B7 の RoleTransferReaderTest が理由）。ArchUnit の `*ArchitectureTest` は 18 ファイルあり、そのうち 16 が `private static final JavaClasses CLASSES = new ClassFileImporter()…` でクラスごとに別々に読み込んで静的に持つ（grep の結果。中身は読んでいない）。

### Code Quality Indicators

- **Linting**: backend は Spotless（palantir-java-format 2.98.0・ライセンスヘッダー）と SpotBugs 4.10.4 ＋ FindSecBugs 1.14.0（`spotbugsGate`）。frontend は Prettier・oxlint・ESLint・Stylelint・tsc（frontend/package.json の scripts）。
- **CI/CD**: .github/workflows/ci.yml（`develop` への push と `v*` のタグで `./gradlew verify`、WAR を成果物に保存）。.github/dependabot.yml（gradle・npm・github-actions・docker・docker-compose の5項）。
- **Documentation**: 設定とコードのコメントは日本語で、決めた理由と見直す時期まで書かれている（libs.versions.toml・dependabot.yml）。

### Technical Debt Signals

各束の項目ごとに、確かめた事実と見立てを分ける。

**U1 依存の更新**

- 事実: 開いている Dependabot のプルリクエストは 20 件（`gh pr list`。#24・#25・#27・#32・#34〜#49）。内訳は npm 10・Gradle 3（#37 jqwik・#46 wrapper・#47 Spotless）・Actions 4・docker 1（#38）・docker-compose 2（#35・#36）で、振り返りの U1 の一覧と一致する。
- 事実: Dependabot が見ない固定先（.github/dependabot.yml のコメントの一覧）に、今回上げるイメージが次のとおりある。
  - eclipse-temurin の jre（#38）: perf/README.md（40・239・324・567・824 行）、README.md（352・362・735・1135 行）。同じ版の jdk（`25.0.4_7-jdk-noble@sha256:2feab631…`）が docker/hikari-pool.sh 48 行・perf/README.md 76 行・README.md 392 行にあり、jdk は Dependabot が見ないため、jre に合わせて手で上げる必要がある。
  - grafana/otel-lgtm（#36）: perf/README.md 436 行。
  - axllent/mailpit（#35）: docker/perf/compose.yaml 134 行（ダイジェスト付き）、README.md 552 行（タグだけ、ダイジェスト無し）。
- 事実: Actions はコミットのハッシュで固定しているため、更新はハッシュとコメントの版の両方を替える。
- 事実: `vite`・`@vitejs/plugin-react` は config/npm-build-tools.txt の成果物を作る道具に入っている（devDependencies でも High 以上で止まる）。
- 事実: TargetDbImages は backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java にある（場所だけ確かめた）。今回の 20 件に対象DB のイメージは無い。
- 見立て: grafana/k6 は perf/README.md にしか無く、Dependabot が見るファイルにも、dependabot.yml の「見ない固定先」の一覧の版の記述にも、更新の知らせの経路が無い（一覧に名前はあるが、知らせは来ない）。
- 見立て: eclipse-temurin の `25.0.4.1_1` は版の付け方が変わる（`25.0.4_7` → `25.0.4.1_1`）。dependabot.yml の ignore（`semver-major`）に当たらないことは知らせが来たことから分かるが、jdk の同じ版のタグがあるかは確かめていない。

**P1・P4 ロール・グループの書き込みと BUSY**

- 事実: ロールの書き込みは `RoleStoreTransactions.inFirst` の1つ目のトランザクション（`TransactionTemplate`）で、排他 → 判定 → 書き込みと flush を行う。入れ子の REQUIRES_NEW は使わず、1つ目の接続を返してから2つ目を借りる（RoleStoreTransactions.java のクラスの説明）。成功の出来事は1つ目の中で出し、確定の後に audit が記録する（RoleAdminService.java 72〜73 行のコメント）。
- 事実: 行の排他は JPA の `PESSIMISTIC_WRITE` と `jakarta.persistence.lock.timeout` のヒント 3000 ms（RoleStore.java 65・81・153〜164 行、group/store/GroupStore.java 48 行も 3000）。上限切れは `RoleStoreClassifier` が `Busy` に読み替える。project.md の学びどおり、一意の鍵・主キーの待ちにはヒントが効かず H2 の既定の約 2 秒で切れる（RoleStore.java の MERGE_SELECTION の説明）。
- 事実: 結合テストのファイル RoleBusyApiIT・RoleBusyLogTraceIT・RoleTransferBusyIT・GroupBusyLogIT が backend/src/test にある（ファイル名だけ。中身は読んでいない）。
- 見立て: P1 の「2本使いの待ちの最大」は、確定の後に audit のリスナーが2本目の接続を借りるまでの待ちと見られる（audit パッケージは控えの範囲の外で読んでいない）。23.55 ms の1回だけの外れの原因は、コードからは分からない（GC の停止・colima の VM の時計のずれ・プールの借りる順などの候補）。
- 見立て: k6 で BUSY が 0 件なのは、場面がどれも VU ごとに別のロール・グループを使い、同じ行で 3 秒待つ重なりが起きないため。L3・L4 の結び付きは、既存の RoleBusyLogTraceIT・GroupBusyLogIT がすでに確かめている可能性がある（要確認）。

**P2 DSL の読み込みとヒープ**

- 事実: 上限は DslFormat の `MAX_BYTES = 10 MiB`・`MAX_DEPTH = 50`・`MAX_COLLECTION_ALIASES = 100`・`MAX_EXPANDED_NODES = 1,000,000`、権限の YAML は RoleTransferLimits の `DEFAULT_MAX_BYTES = 5,242,880`・`MAX_DEPTH = 10`・`MAX_ALIASES = 0`・`MAX_EXPANDED_NODES = 500,000`。深さ・別名は LoaderOptions と LimitingParser の両方に、大きさは `setCodePointLimit` に渡す（SafeYamlParser.java 88〜98 行）。
- 事実: **1行の長さの上限は無い**（YamlLimits の項目は大きさ・深さ・別名・展開後の節の数の4つだけ。LimitingParser に行の扱いが無い）。DSL と権限の YAML は同じ SafeYamlParser を使う。
- 事実: 読み込みの中で本文が複数の形で同時に生きる。`byte[]`（要求の本文、DslAdminController.java 121 行）→ UTF-8 の `String`（SafeYamlParser.decode）→ SnakeYAML の `Node` の木 → 検証用の JSON の形と位置の対応表（YamlTreeConverter）。プレビューの行（DslPreviewRecord）も `byte[]` を持つ。
- 事実: Dockerfile の ENTRYPOINT は `-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError`、compose.yaml・docker/perf/compose.yaml の app は `mem_limit: ${MASTERSMITH_CONTAINER_MEMORY:-2g}`、JVM の追加の設定は `MASTERSMITH_JAVA_OPTIONS`。application.yaml の DSL の投入の上限は `max-submit-size: 10MB`、Tomcat の `max-swallow-size: 11MB`。
- 見立て: 見積もり（256〜384 MB）と実測（GC の後 573〜746 MB）の差は、上の4つの形が同時に生きることと、`String` が全角を含むと UTF-16 で本文の約2倍になることで説明できる可能性がある。試しの測定で確かめる。

**P3・P5 k6 の台本**

- 事実: `setupRoleMembers` は `myPermissionsTree`・`roleAssignmentsRead` で悪い側のロールを VU の利用者（perf-gr-<VU>）に割り当て、`roleAssign` は 204 以外を `fail` にする（perf/k6/scenarios.js 1546〜1550・1569〜1571 行）。`roleAdminRead` も同じ悪い側のロールを perf-gr-0001 に割り当てる（1498〜1504 行）。そのため、前の場面の割り当てが残ると 409 `ROLE_NO_CHANGE` で setup が止まる。
- 事実: `workRoleRead` の確かめは `r.json('roles').length === 2`（1694 行）で、ロールの数に頼る。前の場面で悪い側のロールが残ると 3 になり不一致になる。
- 事実: DSL の投入と権限の YAML の確かめを重ねる場面（P5）は台本に無い（場面の一覧と grep）。
- 見立て: P3 は、setup で 409 `ROLE_NO_CHANGE` を受け入れる・確かめを「自分のロールが含まれる」に変える形で、台本だけで直せる。

**K1〜K5 画面・コード**

- 事実（K1）: frontend/src/app/work-role/WorkRoleSwitcher.css 71 行で `body:has(.work-role-switcher [aria-expanded='true']) .mycui-dropdown-menu { max-width: … }` を 768px 未満で上書きしている。サブモジュールの固定先は `5bf1ffe` で、手元の origin の参照の先頭も `5bf1ffe`（`git fetch` はしていないため、上流の今の先頭は確かめていない）。トップバーの文言は ShellLayout.tsx の `navLabels: SidebarLabels`（124 行）だけを渡し、トップバーの文言の口は無い（依頼（3）の 2.3 と一致）。
- 事実（K2 R-05）: dsl/domain/DslError.java は `record` で `toString` を上書きしておらず、`messageArgs`（利用者が書いた値の先頭 100 文字まで、`excerpt`）が既定の `toString` にそのまま出る。
- 事実（K3）: navigation の record のうち `toString` を上書きしているのは NavigationMenu・NavNode・NavigationResponse・NavNodeResponse の4つで、DisplayLabel・MenuFilterInput・NavTableRef・TableAccessResult・DisplayLabelResponse・NavTableRefResponse・TableAccessResponse は上書きしていない。MenuFilter.java 40 行の Javadoc は「入れ子は DSL の深さの上限（5 段）の内に収まるため、再帰でたどってもスタックを使い尽くさない」と、MenuFilter の外の上限に頼る前提を書いている。
- 事実（K4 R-02）: shared/tree/SharedTreeNodeItem.tsx 61〜62 行の読み込みの失敗の表示は `<p>` で、`role`・`aria-live` が無い（読み上げに知らせない）。失敗の時は区画に `tabIndex={-1}` があり、再試行でそこへフォーカスを戻す。
- 事実（K5）: `useSlowNotice.ts` は groupadmin・roleadmin・roletransfer の **3つ**（振り返りの文言は groupadmin・roletransfer の2つ）にあり、違いはコメントの1行（ほかの2つの名前）だけ。テストも3つにある（`useSlowNotice.test.tsx`）。frontend/src/shared には paging・tree・validation などがあり、移し先の形はそろっている。
- 見立て（K2 R-04・K4 R-03・R-05〜R-07、K2 R-07・R-08）: 指摘の本文（role-menu の build-and-test-summary.md 5.4）を読まないと対象のファイルを特定できない。360px の画面と、管理者でない利用者のサイドバーの区画（buildNavSections.ts の ADMIN 区画は項目が0なら足さない、272 行）は、指摘の本文と突き合わせて要件で範囲を決める。

**その他**

- 事実: role・group・dsl・dslmanage・navigation は `packagesJudgedByTotal` に無いため、K2・K3 の直しでカバレッジの一覧から外す作業は生じない。
- 見立て: P7 は、16 の ArchUnit のテストが同じ本体のクラスを別々に読み込んで静的に持つことがヒープを押し上げている可能性がある（ArchUnit の `@AnalyzeClasses` のキャッシュか、共有の読み込みに寄せる直しの候補）。テストだけの変更のため、team.md の「手を入れる」には当たらない。

## Handoff Summary

- **Intent-relevant finding**:
  - U1: 20 件の内訳は振り返りと一致する。イメージを上げるときに手でそろえる固定先は、eclipse-temurin の jre（perf/README.md・README.md）と同じ版の jdk（docker/hikari-pool.sh 48 行・perf/README.md 76 行・README.md 392 行。Dependabot が見ない）、otel-lgtm（perf/README.md 436 行）、mailpit（docker/perf/compose.yaml 134 行・README.md 552 行はダイジェスト無し）。Actions はハッシュの固定。
  - P2: SafeYamlParser に1行の長さの上限が無い（YamlLimits は4項目）。本文は `byte[]`・`String`・`Node` の木・JSON の形が同時に生きる。
  - P3: perf/k6/scenarios.js の `roleAssign` は 204 以外で `fail`（1546〜1550 行）、`workRoleRead` はロールの数 2 に頼る（1694 行）。
  - K5: `useSlowNotice` の複製は3つ（groupadmin・roleadmin・roletransfer）。
  - K2 R-05: DslError（record）の `toString` が利用者の値の抜粋を出す。
- **Risks / follow-up**:
  - eclipse-temurin `25.0.4.1_1` の jdk のタグとダイジェストの有無は未確認。jre だけ上げると、運用の道具（docker/hikari-pool.sh）と手順書の jdk が古いまま残る。
  - grafana/k6 は Dependabot の知らせが来ない固定先で、今回の 20 件に入らない。
  - P1・P4 の原因と BUSY が出ない理由は見立てのみ。既存の RoleBusyLogTraceIT・GroupBusyLogIT の中身（L3・L4 の結び付きをすでに確かめているか）を要件定義の前に確かめるとよい。
  - K1 は make-you-chic-ui の公開待ち。上流の今の先頭は `git fetch` をしていないため未確認。
  - K2 R-04・R-07・R-08、K4 R-03・R-05〜R-07 は、指摘の本文が範囲の外のため対象のファイルを特定していない。
  - 範囲の外の audit パッケージ（2本目の接続を借りる AuditEventListener）と backend/src/test は深く読んでいない。
