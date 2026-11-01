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
