# コードの質の評価（mastersmith2）

件数はファイルを数えた値・検索で数えた値で、テストを実行した値ではない（Gradle・npm・Docker は実行していない）。この文書に本文を書いた所見は K-38（前回）と K-42・K-43・K-45（2026-10-15）で、ほかは持ち主の文書を参照する（一覧は `business-overview.md`）。

> 2026-10-15（コミット `4f6c27d`）の更新: 末尾に「2026-10-15 の所見」（K-42・K-43・K-45）を足し、技術的負債の一覧に今回の行を足した。テストの件数（176・137・110・13 本）と「今回の Intent に関わる既存のテスト」の表は前回（コミット `d5aea52`、`261004-role-menu` の実装の前）の値のままで、今回は数え直していない。パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省いて書く。

## テスト

| 対象 | 置き場と数（2026-10-04 にファイルの名前で数えた値） | 道具 |
|---|---|---|
| バックエンドの単体 | `backend/src/test/java/`、`*Test` 176 件（タスク `test`。層と境界の検査を含む） | JUnit 5・jqwik・ArchUnit |
| バックエンドの結合 | 同上、`*IT` 137 件（タスク `integrationTest`）。内部DB は組み込みの H2。漏えいの確かめ `*SecretLeakIT` 12 件 | Spring Boot Test・spring-security-test・Testcontainers（対象DB だけ）・SubEthaSMTP |
| 画面 | `frontend/src/**/*.test.ts(x)` 110 件 | Vitest・Testing Library・user-event・vitest-axe・fast-check |
| E2E | `frontend/e2e/*.e2e.ts` 13 本（010〜130）。`verify` と CI の外 | Playwright |

`backend/src/test/java/` のファイルは 380 で、上の2つの合計（313）との差は `testsupport` などの補助である。

### 今回の Intent に関わる既存のテスト（ファイルの名前で確かめた）

| 論点 | テスト | 今回の変更で前提が変わりうる点（見立て） |
|---|---|---|
| 認可（K-32・K-33） | `backend/src/test/java/cherry/mastersmith/access/web/` の `AdminAccessIT`・`AdminPathBoundaryIT`・`ApiDefaultAccessIT`・`AdminAuthorizationManagerTest`・`AdminAccessDeniedHandlerTest`・`AdminAuthenticationEntryPointTest`・`AccessDeniedEventsIT`・`AccessProblemTypesIT`・`AccessRequestRejectedHandlerTest`・`AccessSecretLeakIT`、テスト用の決まり `access/testsupport/PublicApiTestRules`・`AdminTestUsers` | 「管理者なら 200・そうでなければ 403」の2値の前提。役割を入れると、役割ごとの 401・403・200 の組が増える |
| 認証の主体（K-32） | `auth/web/AccessTokenApiIT`・`AccessTokenAuthenticationProviderTest`・`SuspendedUserAuthenticationIT` など | 主体の形（`AuthenticatedUser`）を変えると広く波及する |
| 管理者の印の業務（K-34） | `useradmin/web/UserAdminOperationsApiIT`・`UserAdminMassAssignmentIT`・`UserAdminSecretLeakIT` など、境界テスト `UserAdminBoundaryArchitectureTest` | 最後の管理者の保護と印の付け外しの前提 |
| 画面の出し分け（K-35） | `frontend/src/app/navigation/navigationItems.test.ts`・`app/routing/decideRoute.test.ts`・`AppRouter.test.tsx`・`app/layout/ShellLayout.test.tsx`・`app/registry/validateRegistrations.test.ts` | `admin` の真偽値と平らなサイドバーの前提 |
| E2E | `030-admin-access`・`110-user-admin-flow`・`130-admin-forbidden-accessibility` など | 管理者・一般の利用者の2種類の前提。`team.md` の決まりで、この Intent で足せる代表の流れは1本まで |

## カバレッジ

- バックエンド: JaCoCo。単体と結合を合わせた全体で行 80%・分岐 70%、加えて `packagesJudgedByTotal` 以外のすべてのパッケージごとに同じ下限（`backend/build.gradle.kts` の `jacocoTestCoverageVerification`）。
- 画面: `frontend/vitest.config.ts` の `thresholds`（行 80%・分岐 70%。開発担当の流し読み）。
- 計測から外すのは起動クラス・設定値だけのクラス・自動生成・`vendor/` に限る（`team.md` の Testing Posture）。

## 検査と CI

- 形と静的検査: Spotless（palantir-java-format・ライセンスヘッダー）、SpotBugs ＋ FindSecBugs と関門 `spotbugsGate`（除外は `backend/config/spotbugs-exclude.xml`）、ArchUnit の層と境界の検査。
- 画面: Prettier・oxlint・ESLint（`frontend/eslint.config.js`）・Stylelint・型検査・ライセンスヘッダーの検査（`scripts/check-license-header.mjs`）。
- 秘密情報: Gitleaks（`.gitleaks.toml`）。依存: OSV-Scanner と Dependabot（`.github/dependabot.yml`）。
- CI: `.github/workflows/ci.yml`（checkout → setup-java・setup-node・setup-gradle → `./gradlew verify` → 成果物の保存。アクションはコミットのハッシュで固定。開発担当の流し読み）。E2E は CI の外（`team.md`）。

## 文書

- `README.md`: 起動・配備・設定の一覧・既知の制約（今回は読み直していない）。
- `frontend/src/features/README.md`: 機能の登録の決まり。
- コードのコメント: Javadoc・JSDoc は日本語で、設計の ID（BR・NFR・契約・ADR・Intent）を細かく引いている。後続の Intent への申し送りもコメントにある（`AdminAuthorizationManager` の「後続 Intent F」、`ActiveDslModelProvider` の「後続の Intent I・J・K」。今回読んだファイルで確かめた）。

### K-38 監査・境界テスト・カバレッジへの波及

確かめた事実:

- 監査の種類は `audit/domain/AuditEventType.java` の 22 個の列挙で、列は `audit_events.event_type VARCHAR(32) NOT NULL`（`backend/src/main/resources/db/migration/V4__u4_audit_event.sql` 26 行）。今いちばん長い名前は `INITIAL_ADMIN_RESCUED`（21 文字）。出来事は各機能が出し、`audit/service/AuditEventListener.java` が確定の後に受ける（`access` の拒否は `AdminAccessDeniedEvent` を 115 行で受ける）。`project.md` の Mandated は「利用者の権限・状態を変える管理の操作は、操作した人・対象の利用者・結果を監査に残す」。
- 機能ごとの境界テストは 10 個（`appearance`・`audit`・`auth`・`dsl`・`dslmanage` の2つ・`invitation`・`mail`・`targetdb`・`useradmin`）で、**`access` には無い**（`team.md` の「新しく作る機能は `<機能>BoundaryArchitectureTest` を置く」より前に作られた）。`access` は `auth.domain`・`auth.web`・`config` に依存し（`dependencies.md`）、`useradmin.web` は `access.domain.AccessProblemTypes` だけ使ってよい（`UserAdminBoundaryArchitectureTest` 113 行〜）。
- `backend/build.gradle.kts` の `packagesJudgedByTotal`（225〜233 行）は 7 個（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）で、`access.service` を含む。`access.domain`・`access.web`・`auth.*`・`user.*`・`useradmin.*`・`audit.domain`・`audit.service`・`dsl.*` は一覧の外（パッケージごとの下限の対象）。

見立て（未検証）:

- 役割の割り当ての変更は Mandated の監査の対象になり、新しい種類が増える（名前は 32 文字以内）。役割そのものの作成・変更・削除を監査に残すかは要件で決める。
- 役割の管理を新しい機能（パッケージ）にするなら、`team.md` の決まりで `<機能>BoundaryArchitectureTest` を置き、既存の境界テストは書き換えずに足す。`access` に手を入れるときに `access` の境界テストを新しく足すかは、計画の論点になる（足すのは既存の検査を緩めることにはならない）。
- `access.service` の本体のソースに手を入れる Bolt では、`team.md` の決まりでテストを足して下限（行 80%・分岐 70%）を満たし、一覧から外す作業が付く。`audit.repository` に手を入れる場合も同じ。どちらに手が入るかは設計で決まり、Delivery Planning で今の値を実測して見積もる（`project.md` の Way of Working・Testing Posture の学び）。
- 認可の変更は `team.md` の Testing Posture の必須のテスト（「認可: 401・403・200」「管理の API の認可」「要求の改ざん」「管理の操作の監査」「利用者の管理の漏えい」）に当たる。

## 2026-10-15 の所見

### K-42 k6 の台本のロールの場面が前の場面の状態に頼る

確かめた事実:

- `perf/k6/scenarios.js` の `setupRoleMembers` は `myPermissionsTree`・`roleAssignmentsRead` の setup で悪い側のロールを VU の利用者（`perf-gr-<VU>`）に割り当て、`roleAssign`（1547〜1550 行）は 204 以外を `fail` にする。`roleAdminRead` も同じ悪い側のロールを `perf-gr-0001` に割り当てる（開発担当の記録、1498〜1504 行）。そのため、前の場面の割り当てが残ると 409 `ROLE_NO_CHANGE` で setup が止まる。
- `workRoleRead` の確かめは `r.json('roles').length === 2`（1694 行）で、ロールの数に頼る。前の場面の割り当てが残ると数が変わり不一致になる。
- DSL の投入と権限の YAML の確かめを重ねる場面（P5）は台本に無い（場面の一覧と grep、開発担当の記録）。

見立て（未検証）:

- P3 は台本だけで直せる（setup で 409 `ROLE_NO_CHANGE` を割り当て済みとして受け入れる、確かめを「自分のロールが含まれる」に変える）。`project.md` の学び（setup は前の場面の状態に頼らず、自分で前提を作るか片付けてから始める）に当たる。
- P4 の BUSY を起こす場面と P5 の重なりの場面は、足すなら同じ行を複数の VU で取り合う形が要る（K-40）。P4 は前の Intent の `USER_ADMIN_BUSY` と同じく結合テストで確かめる形もある。

### K-43 単体テストの JVM のヒープと ArchUnit の読み込み

確かめた事実:

- `backend/build.gradle.kts` のテストの JVM のヒープは共通で `maxHeapSize = "1g"`（129 行。コメントに「構造の検査（ArchUnit）の各クラスがアプリのクラスの読み込みの結果を静的に持ち続ける」とある）、単体テスト（`tasks.test`）だけ `"1536m"`（158 行、Intent `261004-role-menu` の B7 の `RoleTransferReaderTest` が理由と開発担当の記録）。
- `backend/src/test/java/` の `*ArchitectureTest.java` は 18 ファイルで、そのうち 16 が `static final JavaClasses` をクラスごとに持つ（grep。中身は読んでいない）。ArchUnit の `@AnalyzeClasses`（読み込みのキャッシュ）を使うテストは 0 件。

見立て（未検証）:

- 16 のテストが同じ本体のクラスを別々に読み込んで静的に持つことが、ヒープを押し上げている可能性がある。`@AnalyzeClasses` のキャッシュか、共有の読み込みに寄せる直しが候補（振り返りの P7・navigation R-02）。テストだけの変更のため、`team.md` の「手を入れる」（`packagesJudgedByTotal`）には当たらない。境界テストを書き換える形になるときは、検査を緩めないこと（`team.md` の Code Style）を計画で確かめる。

### K-45 利用者の値を持つ record の `toString` の伏せ字の抜け

確かめた事実:

- `dsl/domain/DslError.java` は `record DslError(kind, line, column, path, messageKey, messageArgs)`（35〜36 行）で `toString` を上書きしていない。`messageArgs` は利用者が書いた値の抜粋（`DslFormat.MAX_ARGUMENT_LENGTH = 100` 文字まで）を持ち、既定の `toString` にそのまま出る。
- `navigation` の record 11 個のうち、`toString` を上書きしているのは `NavigationMenu`・`NavNode`・`NavigationResponse`・`NavNodeResponse` の4つで、`DisplayLabel`・`MenuFilterInput`・`NavTableRef`・`TableAccessResult`・`DisplayLabelResponse`・`NavTableRefResponse`・`TableAccessResponse` の7つは上書きしていない（grep）。
- `TraceAspect` は `web`・`service`・`domain`・`repository` の層の引数と戻り値を TRACE のときに文字列にして出す（前回までの記録、`team.md` の Code Style）。

見立て（未検証）:

- 振り返りの K2 R-05・K3 R-03 は、利用者の値（DSL の抜粋・表示名・テーブル名）が TRACE のログに出うる指摘である。直すときは先に `*SecretLeakIT` の形の再現のテストを書く（振り返りの K2、`project.md` の Mandated「不具合の再現のテストを同じコミットに含める」）。どの値を伏せるか（テーブル名は個人に関する値か）は要件定義で決める。
- `dsl`・`navigation` は `packagesJudgedByTotal` に無いため、直してもカバレッジの一覧から外す作業は生じない（パッケージごとの下限はそのまま当たる）。

## 技術的負債の一覧

2026-10-15 の行（K-39〜K-48）は今回（コミット `4f6c27d`）の走査で、それより上の行は前回（2026-10-04、コミット `d5aea52`、`261004-role-menu` の実装の前）の走査で確かめたもの。前回の行の多く（K-32〜K-36）は `261004-role-menu` で手当てされたが、今回は確かめ直していない。

| 項目 | 内容（場所） | 所見 | 重さ |
|---|---|---|---|
| 権限が真偽値1つ | `users.admin_flag`、`AuthenticatedUser` の `admin`、空の権限の一覧 | K-32（`architecture.md` の Interaction Diagrams 1） | 高（今回の Intent で作り替える） |
| URL の決まりが1つ・403 の理由が1つ | `AdminSecurityContributor`・`AdminAuthorizationManager`・`AccessDeniedReason.NOT_ADMIN` | K-33（同上） | 高（今回の Intent で決める） |
| 「管理者」の判定の散在 | サーバー側 3 か所（`AdminAuthorizationManager`・`UserAdminService`・`UserRepository.findActiveAdminIds`）、画面側 3 か所（`navigationItems.ts`・`decideRoute.ts`・`adminForbidden.ts`） | K-34・K-35（`architecture.md` の Interaction Diagrams 2・3） | 中（判定の入口を寄せないと、役割の追加で直す場所が増える） |
| 画面の 403 の判定が URL の接頭辞に固定 | `frontend/src/shared/api-client/adminForbidden.ts` | K-35 | 中 |
| サイドバーのアイコンが固定・項目の型にアイコンが無い | `frontend/src/app/layout/ShellLayout.tsx` 46 行、`SidebarItemRegistration` | K-35・K-36 | 低〜中 |
| make-you-chic-ui のサイドバーが平らで、`aria-label` が日本語の固定・`aria-current` が無い | `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/Sidebar.tsx` | K-36（`component-inventory.md` の `make-you-chic-ui`） | 中（N 階層の前提を満たさない。英語の表示とアクセシビリティにも関わる） |
| DSL のメニューの `icon` を照らしていない・メニュー専用の深さの上限が無い | `dsl/validate/DslSemanticValidator.java` 102〜117 行、`DslFormat.MAX_DEPTH` | K-37（`api-documentation.md`） | 低〜中 |
| `access` に境界テストが無い | `backend/src/test/java/cherry/mastersmith/access/` | K-38（この文書） | 中 |
| `access.service` が全体の合計で判定されている | `backend/build.gradle.kts` の `packagesJudgedByTotal` | K-38（この文書） | 低（手を入れるとカバレッジの作業が付く） |
| 依存の更新 20 件と手でそろえる固定先（2026-10-15） | `.github/dependabot.yml`、`Dockerfile`・`docker/hikari-pool.sh`・`perf/README.md`・`README.md`・`docker/perf/compose.yaml` | K-39（`dependencies.md`） | 中（土台のイメージを含む。jdk と k6 は知らせが来ない） |
| ロールの書き込みの待ちの外れと BUSY を起こす場面の欠け（2026-10-15） | `role/store/RoleStore.java`・`role/service/RoleStoreTransactions.java`・`perf/k6/scenarios.js` | K-40（`architecture.md` の Interaction Diagrams 5） | 中（目標を1回外した原因が分からない） |
| YAML の読み込みに1行の上限が無い・ヒープの見積もりの外れ（2026-10-15） | `dsl/parse/SafeYamlParser.java`・`YamlLimits.java` | K-41（`architecture.md` の Interaction Diagrams 6） | 中〜高（悪い形の権限の YAML でヒープが上限に届く、受け入れた制約） |
| k6 の台本の setup が前の場面に頼る（2026-10-15） | `perf/k6/scenarios.js` | K-42（この文書） | 低〜中（流し直しが要る） |
| 単体テストのヒープ 1536m と ArchUnit の読み込み（2026-10-15） | `backend/build.gradle.kts`、`*ArchitectureTest.java` 16 個 | K-43（この文書） | 低（次に尽きたら直す） |
| Dropdown の幅をアプリの CSS で上書き・トップバーの文言の口が無い（2026-10-15） | `frontend/src/app/work-role/WorkRoleSwitcher.css` | K-44（`component-inventory.md` の `make-you-chic-ui`） | 低（上流の公開待ち） |
| record の `toString` の伏せ字の抜け（2026-10-15） | `dsl/domain/DslError.java`、`navigation` の record 7 個 | K-45（この文書） | 中（利用者の値が TRACE のログに出うる） |
| `MenuFilter` の再帰の安全が DSL の上限に頼る（2026-10-15） | `navigation/domain/MenuFilter.java` | K-46（`component-inventory.md` の `navigation`） | 低 |
| 共有の木の読み込みの失敗が読み上げられない（2026-10-15） | `frontend/src/shared/tree/SharedTreeNodeItem.tsx` | K-47（`component-inventory.md` の `frontend-app-core`） | 中（アクセシビリティ） |
| `useSlowNotice` の3つの複製（2026-10-15） | `frontend/src/features/{groupadmin,roleadmin,roletransfer}/useSlowNotice.ts` | K-48（`code-structure.md`） | 低 |
