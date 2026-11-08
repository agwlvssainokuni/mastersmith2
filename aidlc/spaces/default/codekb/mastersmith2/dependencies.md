# 依存関係（mastersmith2）

> 2026-11-09（記録のコミット `4fe8e79`）の Intent `261108-ci-checks-release` の Focused scan で、「CI の手順の間の依存（2026-11-09）」と「台本と一覧の読み手の依存（2026-11-09）」を足した。機能の間の依存の表とサブモジュール・Dependabot の節は読み直していない（前回以前の記録による）。
>
> 前回の記録: 2026-11-08 の2回目（記録のコミット `9fd4f7f`）の Intent `261108-ci-size-coverage` の Focused scan で、「Gradle のタスクの間の依存」に `verifyQuick`・`jacocoQuickReport`・`-PitTests` のときの `integrationTest` を足し、CI の手順の間の依存を足した。機能の間の依存の表とサブモジュール・Dependabot の節は読み直していない（前回以前の記録による）。
>
> 前回の記録: 2026-11-08（記録のコミット `16f46f1`）の Intent `261107-verify-quick-local` の Focused scan で、「ビルドの依存」に Gradle のタスクの間の依存（`dependsOn` と順の決まり）を足した。機能の間の依存の表とサブモジュール・Dependabot の節は読み直していない（前回以前の記録による）。
>
> 前回の記録: 2026-11-06（記録のコミット `a789a6f`）の Intent `261104-tz-options-perf` の Focused scan で、「画面の依存」に日時の変換（`shared/format`）を読む側の一覧を足した。ほかの節は前回以前の記録による（今回は読み直していない）。
>
> 前回の記録: 2026-11-03（記録のコミット `fa0b01e`）の Intent `261102-timezone-ui-polish` の Focused scan で、`.gitmodules` と `git submodule status` を読み直し、サブモジュールの固定先を今の値に直した（K-105）。あわせて、今回の範囲の機能（`businessdata`・`navigation`）の依存の注記を足した。機能の間の依存の表そのものは前回までのまま（今回は `import` の検索をしていない）。
>
> 前回の記録: 2026-11-01（記録のコミット `6079f13`）の Intent `261101-maintenance-update` の Focused scan で更新した。今回は `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`.gitmodules`・`git submodule status`・`.github/dependabot.yml` の全文を読み、サブモジュールの固定先（K-86）・Dependabot の `ignore` の現状（K-87）・コンテナの版の固定先（K-83）を更新した。機能の間の依存の表は前々回（2026-10-25）の `import` の検索のまま（今回は再確認していない）。
>
> 前回の記録: 2026-10-29（記録のコミット `15a25aa`）の Intent `261029-reference-lookup-cleanup` の Focused scan で更新した。機能の間の依存の表は前回（2026-10-25）の `import` の検索による。今回は `businessdata` の境界テストの許可の一覧と `dependabot.yml` の全文を読み、Dependabot の i18next 系2件（K-79）とコンテナの版の固定先（K-78）を足した。K-66・K-72 の記述は前回（2026-10-28）のまま、`dependabot.yml` で再確認した範囲だけ更新した。

## 機能の間の依存（バックエンド）

| 機能 | 依存する機能（import の先） | 境界テスト |
|---|---|---|
| `auth` | `common`・`user` | `AuthBoundaryArchitectureTest` |
| `access` | `auth`・`common`・`config` | `AccessBoundaryArchitectureTest` |
| `audit` | `access`・`auth`・`businessdata`・`dslmanage`・`group`・`invitation`・`role`・`user`・`useradmin`（各機能の出来事の型） | `AuditBoundaryArchitectureTest` |
| `user` | `common` | `UserBoundaryArchitectureTest` |
| `useradmin` | `access`・`auth`・`common`・`user` | `UserAdminBoundaryArchitectureTest` |
| `invitation` | `common`・`mail`・`user` | `InvitationBoundaryArchitectureTest` |
| `mail` | なし | `MailBoundaryArchitectureTest` |
| `appearance` | `common` | `AppearanceBoundaryArchitectureTest` |
| `targetdb` | なし | `TargetDbBoundaryArchitectureTest`・`ExtensionOrderArchitectureTest` |
| `dsl` | なし | `DslBoundaryArchitectureTest` |
| `dslmanage` | `auth`・`common`・`dsl`・`targetdb`・`user` | `DslManageBoundaryArchitectureTest`・`DslManageGenerateBoundaryArchitectureTest` |
| `role` | `common`・`dsl`・`group`・`user` | `RoleBoundaryArchitectureTest` |
| `group` | `common`・`user` | `GroupBoundaryArchitectureTest` |
| `navigation` | `auth`・`common`・`dsl`・`role` | `NavigationBoundaryArchitectureTest` |
| `businessdata` | `common`・`dsl`・`role`・`targetdb`（`import` の検索。`role` は `role.service`・`role.domain` だけ） | `BusinessDataBoundaryArchitectureTest` |
| `config` | `common` | 全体の `ArchitectureTest` |

- 依存される側の制限: `role` に依存してよいのは `audit`（`role.domain`）・`navigation`・`businessdata`（`role.service`・`role.domain`）だけ（`RoleBoundaryArchitectureTest` 129 行の `@DisplayName`）。`businessdata` に依存してよいのは `audit` の `businessdata.domain`（出来事と理由）だけ（`BusinessDataBoundaryArchitectureTest` 174 行）。`targetdb` の外から `targetdb.config`・`targetdb.repository` に依存しない（前回の記録）。
- `BusinessDataBoundaryArchitectureTest` の `allowedDependencies`（2026-10-29 に読んだ）は `businessdata` の依存先を `targetdb.service/domain`・`role.service/domain`・`dsl.domain/service`・`common` などに限る。参照先の読み取りを `businessdata` の内側に置く限り緩める必要は無い見込み（未検証、K-80）。`targetdb` に手を入れると `TargetDbBoundaryArchitectureTest`、`role` に手を入れると `RoleBoundaryArchitectureTest` の許可の一覧に触れうる（緩める場合は計画に明記して依頼者の承認）。
- 逆向きの知らせは Spring の出来事（監査は `audit` が各機能の `domain` の出来事の型を import して受ける）。

- 今回の範囲（2026-11-03）: `businessdata` の日時の型の対応は `businessdata.repository`（`ColumnValueCodec`・`RowDialect`）の中に閉じ、`targetdb.domain.DatabaseProduct` と `dsl.domain`（`DslColumn`・`FormPart`）だけを読む。接続の時差は `targetdb.config` の初期化の文。`navigation` は `dsl.domain`（`DslMenuItem`・`TableRef`）と `role` の `PermissionSnapshot` を読み、アイコンの照合（`NavIconPolicy`）は自分の中に持つ。ビューの印を応答に足すなら `dsl.domain.DslModel.findTable` を読む形で、新しい依存は要らない見込み（未検証）。

## 画面の依存

- 機能（`frontend/src/features/<featureId>/`、12 機能）どうしの import は ESLint の `no-restricted-imports` で止める。`src/shared/` から `src/app/`・`src/features/` を読むことも止める（前回の記録）。
- 画面は make-you-chic-ui を npm の `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui` で使う。
- 日時の変換（2026-11-06、import の grep）: `features/preferences`（`buildTimeZoneOptions.ts`・`TimeZoneSelect.tsx`）・`features/tables`（`valueFormat.ts`・`formDiff.ts`・`DateTimeField.tsx`・`searchConditions.ts`・`resolveDateTimeInput.ts`・`BusinessDataFormPage.tsx`・`references/useLookupSearch.ts`）・`features/useradmin/lockedUntilInZone.ts`・`app/display-settings`（`browserTimeZone.ts`・`decideDisplayTimeZone.ts`・`DisplayTime.tsx`）が `shared/format/`（`timeZone` の公開の入口 `index.ts`、または `formatInstantToMinutes.ts`・`dateTimeInput.ts`）を読む。`shared/format/timeZone` は `Intl` だけに依り、画面の部品・通信・他の機能を読まない（ESLint の `timeZoneConversionRules`）。テストは入口を通さず `timeZoneGate.ts` を直接読む（`clearFormatterCache`）。

## ビルドの依存

- `backend` の WAR は `frontend` のビルド結果（`dist`）を同梱する。`frontend` は `vendor/make-you-chic-ui` に、`backend` は `vendor/java-mustache-processor`（Gradle の composite build）に依存する。
- 依存の取得元は Maven Central だけに固定（`team.md`）。lockfile は `backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`。
- 依存の脆弱性の関門は `./gradlew verify` の中の OSV-Scanner（`vendor/make-you-chic-ui` の lockfile を含む）。更新の知らせは Dependabot。

### Gradle のタスクの間の依存（2026-11-08、`build.gradle.kts`・`backend/build.gradle.kts`）

`dependsOn`（流すもの）と、`mustRunAfter`・`shouldRunAfter`（順だけ）を分けて書く。`verify` の段の順は `architecture.md` の K-111。

| タスク | `dependsOn` | 順だけの決まり |
|---|---|---|
| `verify` | 段のタスク 10 個 | — |
| 段のタスク（`verifyPrepare` 〜 `verifyArtifact`） | その段のタスクの一覧 | 前の段のタスクの後（`mustRunAfter`）。段の中の各タスクにも、前の段のすべてのタスクの後（496〜507 行） |
| `checkToolchain` | — | — |
| `vendorInstall` → `vendorBuild` → `vendorUnchanged` | 前のものに順に依る（`vendorInstall` は `checkToolchain`） | — |
| `mustacheVendorUnchanged` | `checkToolchain` | — |
| `frontendInstall` | `checkToolchain` | `vendorBuild` の後 |
| npm のタスク（`frontendFormatCheck`・`frontendLint`・`frontendLintCss`・`frontendLicenseCheck`・`frontendSvgCheck`・`frontendTest` ほか） | `frontendInstall`（`frontendTypecheck`・`frontendBuild`・`frontendTest`・`frontendCoverage` は `vendorBuild` も） | — |
| `frontendDistCheck`・`frontendBundleSize` | `frontendBuild` | — |
| perf の道具の検査（`perfToolFormatCheck`・`perfToolLint`・`perfToolEslint`・`perfToolTypecheck`） | `frontendInstall` | — |
| `perfEvidenceScan` | `checkToolchain` | — |
| `gitleaksScan`・`osvScan` | — | — |
| `:backend:test` | （Gradle の標準: テストのクラスのコンパイル） | — |
| `:backend:integrationTest` | （Gradle の標準: テストのクラスのコンパイル） | `test` の後（`shouldRunAfter`） |
| `:backend:check` | `integrationTest`（標準の `test` も） | — |
| `:backend:jacocoTestReport` | （テストには依らない） | `test`・`integrationTest` の後 |
| `:backend:jacocoTestCoverageVerification` | （テストには依らない） | `test`・`integrationTest`・`jacocoTestReport` の後 |
| `:backend:spotbugsGate` | `spotbugsMain`（`spotbugsTest` は無効） | — |
| `:backend:bootWar` | `:frontendBuild` | — |
| `:backend:verifyDslSchemaInWar`・`:backend:verifyFaviconInWar` | `bootWar` | — |
| `e2eTest` | `:backend:bootWar`（後に必ず `e2eTargetDbCleanup`） | — |

- ルートは `evaluationDependsOn(":backend")`（52 行）で `backend` を先に評価し、`backend` のタスクを段の表に入れる。
- カバレッジの報告と判定はテストに `dependsOn` しないため、単独で呼ぶと前の回の実行記録（`build/jacoco/*.exec`）を読む（K-114）。

### 2026-11-08 の2回目に足したもの（記録のコミット `9fd4f7f`。上の表の行の番号は 1回目のもの）

| タスク | `dependsOn` | 順だけの決まり |
|---|---|---|
| `verifyQuick` | `verifyStages` の各タスクから、`frontendTest`・`jacocoTestCoverageVerification`・（`-PitTests` が無ければ）`integrationTest` を除き、`jacocoTestReport` を `:backend:jacocoQuickReport` に替えたもの（`build.gradle.kts` 523〜555 行。段のタスクには依らない） | 段の表で各タスクに付けた `mustRunAfter` がそのまま効く |
| `:backend:jacocoQuickReport` | （テストには依らない） | `test` の後（`backend/build.gradle.kts` 253 行）。0〜6 の段のタスクの後で、8〜9 の段のタスクはこれの後（`build.gradle.kts` 543〜546 行） |
| `:backend:integrationTest`（`-PitTests` のとき） | 標準どおり | `test` の後（`shouldRunAfter`）。`verifyQuick` に入ると 6 の段の位置 |

- `verify` と `-PitTests` を一緒に指定すると、タスクのグラフができた時点で失敗にする（`build.gradle.kts` 559〜566 行）。

### CI の手順の間の依存（2026-11-08 の2回目、`.github/workflows/ci.yml`）

- ジョブ `verify` の手順は上から順に流れ、`if:` が無いため、前の手順が失敗すると後ろは流れない（GitHub Actions の既定。本文は `architecture.md` の K-119）。`./gradlew verify`（6 の手順）は 2〜5 の手順が入れた JDK・Node・Gradle・Gitleaks・OSV-Scanner に依る。WAR の保存（7 の手順）は 6 の手順が作る `backend/build/libs/mastersmith.war` に依る。
- ジョブ `release` は `needs: verify` で、タグのときだけ流れ、ジョブ `verify` が保存した WAR に依る。
- 規模とカバレッジの要約の手順を足すと、カバレッジの報告（6 の手順が作る）と、規模の比べに使う履歴（1 の手順の `fetch-depth: 0`）に依る形になる見込み（未検証。`architecture.md` の K-123・K-124）。

### CI の手順の間の依存（2026-11-09、`.github/workflows/ci.yml` 143 行。K-127）

- 上の節の「`if:` が無い」は 2026-11-08 の2回目の記録。今は 8〜10 の手順が `if: always()`・`continue-on-error: true` を持ち、6 の手順が落ちても流れる（7 は流れない）。手順の並びは `architecture.md` の K-127。
- 8（cloc）は機械のイメージの apt に依る（K-128）。9（`node scripts/ci-summary.mjs`）は、3 の手順の Node、8 の cloc、1 の手順の全履歴（`--base` の `github.event.before` を `git worktree` で取り出す）、6 の手順が作る `backend/build/test-results/{test,integrationTest}/`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`・`frontend/coverage/coverage-summary.json`、リポジトリの `backend/build.gradle.kts`・`frontend/vitest.config.ts` に依る。10 は 6 の手順の HTML の報告に依る（無ければ何もしない）。
- 9 の手順の中の依存: `scripts/ci-summary.mjs` は `scripts/program-size.mjs` の `collectMeta`・`countBase`・`countSize`・`fixed`・`int`・`renderReport` を `import` する（38 行）。

### 台本と一覧の読み手の依存（2026-11-09。K-131・K-135）

`backend/build.gradle.kts` の `packagesJudgedByTotal`（313〜321 行）を読む・写すもの:

| 読み手 | 読み方 | 一覧を変えたとき |
|---|---|---|
| `jacocoTestCoverageVerification`（同じファイル 340〜345 行） | `excludes = packagesJudgedByTotal` | 外したパッケージにパッケージごとの下限が当たる |
| `scripts/ci-summary.mjs` の `parseGradleFloors`（162〜182 行） | 正規表現 `val\s+packagesJudgedByTotal\s*=\s*listOf\(([\s\S]*?)\)` | 書き方を `listOf(` 以外にすると読めず、要約の下限の列が「—」になる（CI は落ちない） |
| `docs/verification.md` 78 行の確かめのコマンド（R-07） | 手で写した正規表現 | 手で合わせる（81 行） |
| `team.md` の Testing Posture | 手で写した名前の一覧 | 決まりの文の書き換え（`team.md` の書き方に従う） |

## サブモジュールと見送りの依存

| 依存 | 今の固定 | メモ |
|---|---|---|
| `vendor/make-you-chic-ui` | `cae5fed185541b975dc56fb034dae3212d4c2247`（2026-11-03 の `git submodule status`。コミット `28dd694` で `cab4207` から上げた。K-105） | `vendor/make-you-chic-ui/package-lock.json` の `"hasInstallScript": true` は `cab4207` の時点で 1 件（3112 行付近。どのパッケージかは未確認。`cae5fed` では数え直していない）。アイコンは `Icon` の 18 種だけ（K-100、`code-structure.md`）。固定先を上げるときは `team.md` の手順で増減を確かめる |
| `vendor/java-mustache-processor` | `d61ba6dac8b4a1617ba72ee469ee0189683e42b4`（`0.1.0-6-gd61ba6d`。2026-11-03 の `git submodule status`。コミット `28dd694` で `8d44c36` から上げた。K-105） | Gradle の composite build（`settings.gradle.kts` の `includeBuild`）。版は `libs.versions.toml` 46 行の `cherry-mustache = "0.1.1"`（2026-11-03 に grep）と固定先の両方。上げるときは両方と、推移依存が lockfile と OSV-Scanner の対象に載ることを確かめ直す（`team.md`） |
| `axe-core`（npm、dev） | `^4.14.0` | 以前の `>= 4.14.0` の見送りは外れている（`dependabot.yml` 65 行のコメント） |

**K-105 サブモジュールの固定先の訂正（2026-11-03）（`make-you-chic-ui`・`java-mustache-processor`）**

- 確かめた事実: `.gitmodules` の取得元は K-86 と同じ。`git submodule status` は make-you-chic-ui が `cae5fed`（`remotes/origin/HEAD`）、java-mustache-processor が `d61ba6d`（`0.1.0-6-gd61ba6d`）。どちらも Intent `261101-maintenance-update` のコミット `28dd694`（「サブモジュールの固定先を上げる（make-you-chic-ui cab4207→cae5fed、java-mustache-processor 8d44c36→d61ba6d（0.1.1））」、`git log` の読み取り）で上がった。知識ベースの K-86 の値（`cab4207`・`8d44c36`）はその前の値。
- 今回の Intent への意味: make-you-chic-ui の `Icon` の名前は `cae5fed` でも 18 種（`registry.ts` を読み取りで確かめた）。テーブル・ビューのアイコンや、ロゴ・アプリ名の差し込み口が要るなら、`team.md` の手順で上流へ依頼し、固定先の更新の専用のコミットで取り込む（K-100・K-102）。
- 未検証: `cae5fed` の lockfile の `hasInstallScript` の数、README の固定先の記述が `cae5fed` に直っているか（README は今回の範囲の外）。
- 2026-11-08 の追記: README 40 行は make-you-chic-ui を `d112795`、java-mustache-processor を `d61ba6d`（タグ `0.1.1`）と書く。make-you-chic-ui の固定先が `cae5fed` からさらに上がった見込みだが、`git submodule status` は今回見ていない（未確認）。

**K-86 サブモジュールの固定先（2026-11-01）（`make-you-chic-ui`・`java-mustache-processor`）**

- 確かめた事実: `.gitmodules` の取得元は `https://github.com/agwlvssainokuni/make-you-chic-ui` と `https://github.com/agwlvssainokuni/java-mustache-processor`。`git submodule status` は make-you-chic-ui が `cab4207`（`remotes/origin/HEAD`）、java-mustache-processor が `8d44c36`（`0.1.0`）。
- README の食い違い: `README.md` 37 行は「make-you-chic-ui の固定先は `ecb07e9`」と経緯の連なり（`edb1f94` → … → `ecb07e9`、10 段）を書くが、実際の固定先は `cab4207`。前の Intent の後に上げた記録が README に入っていない。
- 未検証: `vendor/` の中は今回読んでいない。A2（make-you-chic-ui の lockfile の `braces` 3.0.3。前の Intent の時点で上流 `cab4207` も 3.0.3）と A3（依頼（5）: 1ページの件数の選択と横のスクロールの領域。自前の `ScrollRegion`・`PageSizeSelect` を使い続けている）は、上流のソースを読み取りの `git fetch`・`git show` で確かめてから固定先を上げる（`team.md`）。`hasInstallScript` の増減も同じ時に確かめる。frontend の lockfile にも `braces` 3.0.3 がある（`fsevents` だけが `hasInstallScript`）。

**K-87 Dependabot の `ignore` の現状（2026-11-01 に全文を再読。K-66・K-72 の後継）**

| エコシステム | `ignore` | 理由（コメント） | 外す時期（コメント） |
|---|---|---|---|
| gradle | `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`（すべて） | 管理の OpenTelemetry 1.62.0 と食い違う | 管理の OpenTelemetry が 1.63 以上 |
| gradle | `com.networknt:json-schema-validator`（すべて） | 3.0.7 以降は Jackson を 3.2 系に引き上げる | 管理の Jackson が 3.2 系以上 |
| npm | `typescript` の大きな版 | `@typescript-eslint/*` の peer が `<6.1.0` | `@typescript-eslint/*` が TypeScript 7 を受け付けたとき |
| docker | `eclipse-temurin` の大きな版 | JDK をビルド・CI とそろえる | JDK をそろえて上げるとき |
| docker-compose | `otel/opentelemetry-collector` の `0.163.2-386` | linux/386 だけのイメージ | タグの付け方が変わったとき。collector を上げると新しい `-386` の提案が来うる |

- gradle の項は `exclude-paths: vendor/**`。github-actions の項に `ignore` は無い。Jackson の `ignore` は無い（上書きも無い。`technology-stack.md` の K-85）。
- `axe-core` は `ignore` に入れず、更新は版と lockfile だけでも E2E の全件と vitest-axe を流す例外の手順（`dependabot.yml` 60〜65 行のコメント）。

**K-66・K-72 Dependabot の現状（`.github/dependabot.yml`、2026-10-28 に再読。2026-11-01 は上の K-87 を正とする）**

- 確かめた事実:
  - エコシステムは gradle・npm（`/frontend`）・github-actions・docker・docker-compose の5つ（いずれも weekly、`open-pull-requests-limit: 10`）。`groups` は使っていない。
  - gradle: `exclude-paths: vendor/**`（composite build のサブモジュールの依存を更新できないため）。`ignore` は `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` と `com.networknt:json-schema-validator` の2件（すべての更新）。前回の記録にあった `tools.jackson:jackson-bom` の `ignore` は無くなっている（Jackson の上書きが外れたため）。外す時期はコメントに書いてある（`technology-stack.md` の K-66 の表）。
  - npm: `typescript` の大きな版だけを見送る（`@typescript-eslint/*` が TypeScript 7 を受け付けるまで）。`axe-core` は `ignore` に入れず、更新時は版と lockfile だけでも E2E の全件と vitest-axe を流し違反 0 件を件数で記録する例外の手順がある。
  - docker: `eclipse-temurin` の大きな版だけを見送る（JDK をビルド・CI とそろえて上げるとき）。
  - Dependabot が見ない固定先（手でそろえる）: `docker/perf/compose.yaml`、`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`、`perf/versions.env`（k6・JRE）、`perf/README.md`、`README.md`、`docker/hikari-pool.sh`。
  - `team.md` の受け方: Dependabot の PR は GitHub の画面でマージせず、手元で版と lockfile をまとめて更新し `./gradlew verify` を通してから `develop` に統合し、PR は閉じる。重大度 High 以上は次の Bolt の前に取り込む。見送りを足すときは理由と外す時期を同じファイルのコメントに書く。
- 再確認（2026-10-29）: `dependabot.yml` の npm の `ignore` は `typescript` の大きな版だけで、i18next 系の patch 2件を妨げない。

**K-79 Dependabot の i18next 系2件（#81、#82）（`build-and-verify`・`frontend-app-layout-i18n`）**

- 2026-11-01 の追記: `frontend/package.json` は i18next `^26.5.11`・react-i18next `^17.1.9`、lockfile も 26.5.11・17.1.9 で、2件は取り込み済み。

- 確かめた事実（開発担当の調査）:
  - i18next は `package.json` が `^26.5.5`、lockfile が 26.5.10。#81 は 26.5.10 → 26.5.11。react-i18next は `package.json` が `^17.1.2`、lockfile が 17.1.8。#82 は 17.1.8 → 17.1.9。どちらも patch。
  - 画面での使い方は狭い: `useTranslation` を 21 ファイルが使い、`I18nextProvider` は `app/i18n/I18nProvider.tsx` の1か所、`i18next` 本体の import は `app/i18n/i18n.ts` の1か所だけ。
  - `team.md` の決まりで、PR は GitHub の画面でマージせず、手元で `package.json`・lockfile を更新して `./gradlew verify` を通してから `develop` に統合し、PR は閉じる。
- 未検証: 2件のリリースノートは読んでいない。影響は小さい見込みだが、画面の文言の切り替え全体に効くため `verify`（画面のテスト）で確かめる。

**K-78 コンテナの版の固定先（`container-runtime`・`perf-and-monitoring`）** — 2026-11-01 の一覧は `technology-stack.md` の「版の固定先」（K-83）を正とする。

- 事実は `technology-stack.md` の「手元の運用」に書いた（固定先の一覧と、動いているコンテナのタグが `compose.yaml` と一致して見えること）。ダイジェストの一致は未検証。固定先が複数のファイルに分散するため、上げるときは `dependabot.yml` のコメントの一覧どおりにそろえる（`team.md`）。
- 未確認: 今開いている Dependabot の PR の一覧と重大度（`gh` を使っていないため。後の段で確かめる）。

## Sources

- 2026-11-09: `.github/workflows/ci.yml`・`scripts/ci-summary.mjs`・`scripts/program-size.mjs`（どれも全文）・`backend/build.gradle.kts`（300〜357 行）・`docs/verification.md`（73〜81 行）、開発担当の調査 `aidlc/spaces/default/intents/261108-ci-checks-release/inception/reverse-engineering/developer-scan.md`。
- 2026-11-08 の2回目: `build.gradle.kts`・`backend/build.gradle.kts`・`.github/workflows/ci.yml`（どれも全文）、開発担当の調査 `aidlc/spaces/default/intents/261108-ci-size-coverage/inception/reverse-engineering/developer-scan.md`。
- 2026-11-08: `build.gradle.kts`・`backend/build.gradle.kts`・`settings.gradle.kts`（タスクの依存）、`README.md` 40 行、開発担当の調査 `aidlc/spaces/default/intents/261107-verify-quick-local/inception/reverse-engineering/developer-scan.md`。
- 2026-11-06: `frontend/src` の `shared/format` の import の grep、`frontend/eslint.config.js` の `timeZoneConversionRules`、開発担当の調査 `aidlc/spaces/default/intents/261104-tz-options-perf/inception/reverse-engineering/developer-scan.md`。
- 2026-11-03: `.gitmodules`・`git submodule status`・`git log`（サブモジュールの2行）・`gradle/libs.versions.toml` の grep、開発担当の調査 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/reverse-engineering/developer-scan.md`。
- 2026-11-01: `.gitmodules`・`git submodule status`・`settings.gradle.kts`・`.github/dependabot.yml`（全文）、開発担当の調査 `aidlc/spaces/default/intents/261101-maintenance-update/inception/reverse-engineering/developer-scan.md`。
- `import` の検索（前回: `businessdata`・`audit`）、境界テストの `@DisplayName`、`git submodule status`（前回）、`.github/dependabot.yml`（今回）。
