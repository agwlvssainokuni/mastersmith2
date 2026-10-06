# 生成の記録（Generation Notes）— U1 横断の準備（cross-cutting）

計画 `code-generation-plan.md` の各 Step で流したコマンドと結果を記録する。バックエンドのコマンドは、どれも README の colima の環境変数（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）をシェルに渡して流した。

## Step 1: 作業の場の用意

- `develop` の先頭: `c97f760609b563b1615a548019f9b318214486fd`（作業ブランチ `feature/261004-role-menu-b2` の先頭も同じ。ブランチは指揮役が依頼者の承認を得て作成済み）。
- `git status --short`: アプリのソースに未コミットの変更は無い。未コミットはワークフローの記録だけ（`aidlc/.../aidlc-state.md`・監査ログの変更、`construction/code-generation/`・`construction/cross-cutting/code-generation/` の未追跡）。
- 2つ目の項目（`aidlc/` の未コミットの変更の記録のコミット）は生成の担当ではコミットしない約束のため行っていない。指揮役に引き継ぐ（報告に書いた）。
- `frontend/playwright-report`・`frontend/test-results`: どちらも無い。

## Step 2: テストの実行の準備

| コマンド | 結果 |
|---|---|
| `./gradlew :backend:test --tests cherry.mastersmith.ArchitectureTest` | 成功。5 件（失敗 0・飛ばし 0） |
| `./gradlew :backend:integrationTest --tests cherry.mastersmith.access.web.ApiDefaultAccessIT` | 成功。15 件（失敗 0・飛ばし 0） |
| `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/validateRegistrations.test.ts` | 成功。13 件 |

- ESLint の決まりのテストの実行環境（Infrastructure Design R-01）: 一時の確かめのファイル `frontend/src/zzTmpEslintEnvProbe.test.ts`（先頭で `@vitest-environment node`、`ESLint` の部品で今の `eslint.config.js` を読み `lintText` を1回呼び、`window` が無いことを確かめるだけ）を流した。
  - 1回目は失敗した（初回の読み込みで ESLint の部品と構文解析の部品の読み込みに時間がかかり、テストの時間の上限 5 秒に届いたと見られる。テストの所要は約 5 秒、全体 8 秒）。
  - 2〜5回目（4回）はすべて成功（約 1 秒）。`vitest.setup.ts` の読み込みと `cleanup()`・`vi.restoreAllMocks()` は node 環境で害を出さなかった。
  - 結論: node 環境を使う。初回の読み込みの遅さに備え、Step 19 のテストでは `ESLint` の部品を1回だけ作り（`beforeAll`）、各テストの中で作り直さない形にする。時間の上限は延ばさない（`team.md`）。
  - 確かめのファイルは消した（`git status` で残っていないことを確かめた）。
- 確かめたコマンドは `unit-test-instructions.md` 2節のコマンドと同じ（違いなし）。

## Step 3: 変更の前の基準（Q-D: A）

### 口を持つクラスの検索（Infrastructure Design R-04）

`grep -rlE "^@(RestController|Controller)\b" backend/src/main/java` の結果は 10 クラス:

- `access/web/AdminCheckController.java`
- `appearance/web/AppearanceController.java`
- `auth/web/AuthController.java`
- `common/error/web/ErrorPathController.java`
- `common/error/web/ProblemTypeController.java`
- `dslmanage/web/DslAdminController.java`
- `invitation/web/InvitationAdminController.java`
- `invitation/web/RegistrationController.java`
- `user/web/MeController.java`
- `useradmin/web/UserAdminController.java`

`common/health/`・`common/web/` には口を持つクラスが無い（`GlobalExceptionHandler` は `@RestControllerAdvice` で口ではない）。

### カバレッジの基準

コマンド: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`（2026-10-07 05:20:21〜05:28:39 JST、約 8 分 18 秒、成功）。テストの結果のファイルの合計は 2,254 件（失敗 0・飛ばし 0）。

`backend/build/reports/jacoco/test/jacocoTestReport.xml` から読んだ値（変更の前）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `common.security` | 14/14 = 100.0% | 6/6 = 100.0% |
| `auth.web` | 123/123 = 100.0% | 21/22 = 95.5% |
| `appearance.web` | 13/13 = 100.0% | 分岐なし |
| `common.error.web` | 205/211 = 97.2% | 101/113 = 89.4% |
| `invitation.web` | 107/110 = 97.3% | 20/23 = 87.0% |
| `user.web` | 55/57 = 96.5% | 11/13 = 84.6% |
| `useradmin.web` | 83/84 = 98.8% | 15/16 = 93.8% |
| `dslmanage.web` | 131/131 = 100.0% | 26/30 = 86.7% |
| `access.web` | 73/73 = 100.0% | 15/16 = 93.8% |
| 全体 | 6416/6486 = 98.9% | 2381/2511 = 94.8% |

## Step 4: 注釈（ドメイン）— 実装

- `backend/src/main/java/cherry/mastersmith/common/security/ApiAccessLevel.java`（`PUBLIC`・`AUTHENTICATED`・`ADMIN`）と `ApiAccess.java`（`RUNTIME`、`TYPE`・`METHOD`、値 `ApiAccessLevel value()`）を足した。機能のパッケージは import していない。
- `ApiAccess` の Javadoc に、新しい口へ印を付ける決まりと検査（`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`）の場所を、この時点で書いた（Step 20 の項目のうち Javadoc の分。README の分は Step 20 で行う）。

## Step 5: 注釈（ドメイン）— テスト

- `common/security/ApiAccessTest.java`（3 件: 保持が `RUNTIME`、付けられる所が `TYPE`・`METHOD`、値が3つちょうど）。
- `./gradlew :backend:test --tests cherry.mastersmith.common.security.ApiAccessTest`: 成功。3 件（失敗 0）。

## Step 6: 既存の 34 の口への印（API）— 実装

- 10 のコントローラーのクラスに `@ApiAccess(ApiAccessLevel.X)` を `@RestController` の次の行に付け、import を2行足した（口の道・方法・中身は変えていない）。PUBLIC: `AuthController`・`AppearanceController`・`ProblemTypeController`・`ErrorPathController`・`RegistrationController`。AUTHENTICATED: `MeController`。ADMIN: `UserAdminController`・`DslAdminController`・`AdminCheckController`・`InvitationAdminController`。
- `./gradlew :backend:compileJava :backend:spotlessCheck`: 成功。

## Step 7: 静的な検査と検査の検査

- `common/testsupport/ApiAccessRules.java`: 口（`@RestController`・`@Controller` のクラスの、`@RequestMapping` か `@RequestMapping` を付けた注釈を持つ方法）を対象に、規則4つ（`markedExactlyOnce`・`adminMatchesAdminPaths`・`authenticatedPathsInsideApi`・`commonSecurityIndependentOfFeatures`）を作る。どれも `allowEmptyShould(false)`。印の規則は口をすべて対象に取り、条件の中で印ごとに場合分けする（R-07）。`common.security` の規則は「`cherry.mastersmith` の中で依存してよいのは `cherry.mastersmith.common` の下だけ」とした。口の鍵（クラス名#方法名(引数の型)）の集合も出す（実行時の検査と比べるため）。
- 見本6つ（`common/testsupport/apiaccess/`）。どれも `@ConditionalOnBooleanProperty("mastersmith.test-fixture.api-access-samples")`。
  - 計画との差（小さな追加）: `ValidSample` に、計画の3つの口（`${server.error.path:/error}` の PUBLIC・`/api/admin` の ADMIN・`/api/sample` の AUTHENTICATED）に加えて、`/api/administrator` の AUTHENTICATED の口を足した。要件 NFR1.2 の「確かめ方」に挙がった境界（`/api/administrator` は管理者の道でない）を見本で確かめるため。
  - `AdminPathNotAdminSample` は `/api/admin/x` の AUTHENTICATED と `/api/admin/y` の PUBLIC（security-design 4.2.1 の「もう1つの口に PUBLIC」）。
- `ApiAccessRulesTest.java`（7 件）と `ApiAccessArchitectureTest.java`（5 件）。
- `./gradlew :backend:test --tests cherry.mastersmith.ApiAccessRulesTest --tests cherry.mastersmith.ApiAccessArchitectureTest`: 成功。7 件・5 件（失敗 0）。
- 道の読み取りの確かめ（一時の確かめのテスト。流した後に消した）: 本番の 34 の口の道と印を並べ、機能設計 3節の表と一致した（例 `ErrorPathController.error PUBLIC [/error]`、`ProblemTypeController.describe PUBLIC [/api/problems/{slug}]`、`DslAdminController.restore ADMIN [/api/admin/dsl/history/{revisionId}/restore]`）。見本の `${server.error.path:/error}` は `/error` と読めた。
- 確かめ（B2 の「見せるもの」）: `AdminCheckController` の印を一時に外して `ApiAccessArchitectureTest` を流すと、5 件のうち「every production endpoint carries exactly one ApiAccess mark」が `cherry.mastersmith.access.web.AdminCheckController.check [/api/admin/check] has no ApiAccess mark` で落ちた。写しから元に戻し、`git diff` で印が戻ったことを確かめた。

## Step 8: PUBLIC の一覧

- `common/testsupport/PublicApiInventory.java`: `PublicApiEntry(Set<String> methods, Set<String> patterns)`（R-08）、`EXPECTED`（9 行、理由のコメントにクラス名と方法名）、`entryOf(RequestMappingInfo)`、`diff(expected, actual)`（`Diff(added, removed)`）。
- `PublicApiInventoryTest.java`: 9 件（計画の目安 8 件に「一覧が 9 行・方法と道の組で 14」の確かめを1件足した）。
- `./gradlew :backend:test --tests cherry.mastersmith.PublicApiInventoryTest`: 成功。9 件（失敗 0）。

## Step 9: 実行時の検査

- `common/testsupport/ApiAccessSubjects.java`: 主体 `ANONYMOUS`（匿名のトークン）・`NON_ADMIN_USER`（利用者 ID 1001、`member@example.com`）・`ADMIN_USER`（1002、`admin@example.com`）と期待の表。内部DB に利用者を作らない。
- `ApiAccessConsistencyIT.java`: 5 件（判定の部品が Bean で得られる・本番のクラスだけ・静的な検査と同じ集合で 34 以上・3つの主体の判定・PUBLIC の一覧）。起動は `ApiDefaultAccessIT` と同じ形。
- `./gradlew :backend:integrationTest --tests cherry.mastersmith.ApiAccessConsistencyIT`: 成功。5 件（失敗 0、テストのクラスの所要 約 9 秒）。
- 捨ての試しの再確かめ（R-05）: 判定の部品は型で注入できた（MockMvc への切り替えは不要）。テストのログの値: 口 34、判定の組 117（方法と道の組 39 × 主体 3）、食い違い 0。PUBLIC の口 9、方法と道の組で 14。
- 確かめ（計画に無い、追加の確かめ。元に戻した）: `MeController` の印を一時に PUBLIC に変えて流すと、「3つの主体の判定」（`/api/me/preferences` の GET・PUT と `/api/me/password` の POST が ANONYMOUS で止まる、の3件）と「PUBLIC の一覧」（増えた行 3）の2件が落ちた。写しから元に戻し、`git diff` で AUTHENTICATED に戻ったことを確かめ、戻した後に流し直して 5 件が通った（06:50、約 11 秒）。
  - この負の確かめの1回は Gradle 全体で 15 分 18 秒かかった（テストのクラスの所要は約 10 秒）。原因は確かめていない（同じ時間帯の PC の状態によると見られる。前後の実行は約 1 分以内）。

## Step 10: 境界テスト

- import の数え直し（`backend/src/main/java`）:
  - `access` が使う: `auth.domain`・`auth.web`・`common.error`・`common.security`・`config`。`access` を使う: `audit`（`AuditEventListener`・`AuditEventFactory`）・`useradmin`（`UserAdminController`）。
  - `user` が使う: `common.error`・`common.observability`・`common.persistence`・`common.security`（`MeController` の印）。`user` を使う: `audit`・`auth`・`dslmanage`・`invitation`・`useradmin`。
- `access/AccessBoundaryArchitectureTest.java`（3 件）・`user/UserBoundaryArchitectureTest.java`（3 件）。既存の境界テストと同じ形（本番のクラスを読み、禁じる依存の規則と、規則が依存を見分けていることの確かめ）。
- `./gradlew :backend:test --tests cherry.mastersmith.access.AccessBoundaryArchitectureTest --tests cherry.mastersmith.user.UserBoundaryArchitectureTest`: 成功。3 件・3 件（失敗 0）。

## Step 11: バックエンドの区切りの確かめ

- `./gradlew :backend:cleanTest :backend:test :backend:spotlessCheck :backend:spotbugsGate`: 成功（06:49:31〜06:50:04）。単体の全体 1,577 件（失敗 0・飛ばし 0。既存の `ArchitectureTest` と既存の境界テストを含む）。既存の `*BoundaryArchitectureTest` は検索で 10 ファイル（`DslManageGenerateBoundaryArchitectureTest` を含む）で、計画の「境界テスト 11 個」と数が1つ違う（計画の数え方は確かめていない。全件が通ったことに変わりはない）。
- SpotBugs は `reactor.core.scheduler.Schedulers` が解析に無いという案内を出したが、関門は通った（今回の変更は reactor を使わない。前からの案内かは確かめていない）。

## Step 1 の2つ目（後から）

- 指揮役が依頼者の承認を得て `aidlc/` の記録をコミットした（c85c11b）。チェックボックスを [x] にした。

## Step 12: 画面の登録の型と登録の検査 — 実装

- `app/registry/types.ts`: `SidebarSection = 'ADMIN'`、`SidebarItemRegistration` に必須の `section` と任意の `icon?: IconName`、`LoginStateProvider` に任意の `logout?: () => Promise<void>`。
- `app/registry/allowedIcons.ts`（新規）: `ALLOWED_ICONS`（18 個、`as const satisfies readonly IconName[]`）と逆向きの網羅の型の確かめ `ALL_ICONS_COVERED`、`isAllowedIcon`。
- `app/registry/validateRegistrations.ts`: 区画の値・区画 ADMIN と visibleWhen ADMIN・アイコンの名前の3つの問題（frontend-components 3.2 の文）。型の外から来た値も扱う。
- 4件の登録（`features/{admin,dsl,invitation,useradmin}/registration.ts`）に `section: 'ADMIN'`（icon は足さない）。
- 型だけの直し: `SidebarItemRegistration` を組み立てる既存のテストの見本6ファイル（`navigationItems.test.ts`・`AdminForbiddenView.test.tsx`・`forbiddenHeading.test.ts`・`ShellLayout.test.tsx`・`AppRouter.test.tsx`・`validateRegistrations.test.ts`）に `section: 'ADMIN'` を足した。区画の値は今 ADMIN だけのため、visibleWhen が LOGGED_IN の見本にも ADMIN を入れた（登録の検査を通す見本は、もともと問題の文を部分一致で確かめていて、確かめは変わらない）。
- `npm run typecheck`: 成功。

## Step 13: 画面の登録の型と登録の検査 — テスト

- `validateRegistrations.test.ts` に6件（区画の正しくない値と無い値・区画 ADMIN と visibleWhen LOGGED_IN・一覧の 18 個は通る・一覧に無い名前と空の文字・アイコンなしは通る・今の4件の登録は通る）。
- 計画のコマンド（`src/app/registry src/app/navigation …` の 9 つの対象）: 成功。17 ファイル・141 件。

## Step 14: ログアウトの口と既存の違反の直し — 実装

- `app/login-state/LoginStateGate.tsx`: ログイン状態とは別の文脈で提供元の `logout` を渡し、`useLogout()` を出した。返す関数は `Promise<boolean>`（呼べたら true、提供元またはその `logout` が無ければ false、失敗は外へ出さず true）（Q-B: B、D-7）。
- `features/auth/loginStateProvider.ts`: `logout`（`authSession` の `logout`）を渡す。
- `features/registration/useRegistration.ts`: `../auth/authSession` の import を消し、`useLogout()` を使う。false なら `loggingOut` を解いて段階を `loggedIn` に戻す。
- `npm run typecheck`: 成功。

## Step 15: ログアウトの口と既存の違反の直し — テスト

- `LoginStateGate.test.tsx` に4件（呼べたら true・`logout` が無ければ false・失敗を外へ出さず true・提供元が無ければ false）。`loginStateProvider.test.ts` に1件（`authSession` の `logout` を呼び、API が失敗しても未ログインになる）。`RegistrationPage.test.tsx` に1件（提供元に `logout` が無いと案内に戻り、押し直せる。ログアウトと確かめの要求は送らない）。
- 計画のコマンド（`src/app/login-state src/features/auth src/features/registration`）: 成功。19 ファイル・161 件（既存の「ログアウトして続ける」を含む）。

## Step 16: 共有の木 — 実装

- `shared/tree/{types.ts, SharedTreeNodeItem.tsx, SharedTreeView.tsx, SharedTreeView.css}`（新規）。`app/`・`features/` を読まない。表示名・印は文字の差し込みだけ。子の読み込みの状態を、どの `nodes` に対するものかと組で持ち、`nodes` が替わったら捨てる。届いた結果は、呼んだときの `nodes` が今の `nodes` と同じで部品が付いているときだけ使う。`data-testid` は開閉（`shared-tree-toggle`）・選ぶ（`shared-tree-select`）・再試行（`shared-tree-retry`）・木（`shared-tree`）。再試行の後は子の区画（`tabIndex=-1`）にフォーカスを移して、その場所に残す。
- `npm run typecheck && npm run lint:css`: 成功（stylelint の指摘3件、コメントの前の空行・詳細度の順を直した後）。

## Step 17: 共有の木 — テスト

- `shared/tree/SharedTreeView.test.tsx`: 10 件（開閉と aria-expanded・選びと aria-current（読み込んだ節に無い selectedId では付かない）・最初に開いたときだけ読む・nodes の替えで読み直し古い結果を捨てる・失敗の文と再試行（拒否の理由は出ない、ほかの節は使える）・0 件の文・エスケープ・Tab と Enter と Space だけで3段下まで開いて選ぶ・渡した文言だけ・axe（開いた状態・読み込み中・失敗））。
- `npx vitest run src/shared/tree`: 成功。10 件。

## Step 18: ESLint の制限 — 実装（途中で止めた）

- 道の別名: `tsconfig.json`・`vite.config.ts` に `paths`・`baseUrl`・`alias` は無い。
- `frontend/eslint.config.js`: `src/features/` の一覧から機能ごとの `no-restricted-imports`（テストのファイルは対象外）と、`src/shared/**` の決まり。名前の重なりの検出の関数 `findFeatureNameOverlaps` を名前付きで出し、重なりがあれば設定の読み込みで失敗させる（今は重なり無し。下位のディレクトリは `api`・`testing` だけ）。
- 計画との差（実装の中で決めたこと）: 兄弟の機能を指す相対の道の型を、機能の中のファイルの深さごとに作る形にした（深さ 0 は `../B`、深さ 1 は `../../B`、それより深いものは深さ 2 の決まりにまとめる。`**/features/B` はどの深さでも止める）。はじめに深さによらず `../B`・`../../B` の両方を止めたところ、`features/{preferences,useradmin}/testing/` から自分の機能の `../registration`（ファイル）を指す道を、同じ名前の兄弟の機能 `registration` への道と取り違えて止めたため（誤検出2件）。
- `npx eslint .`: **違反が 3 件残った**。どれも既存のテストの補助 `frontend/src/features/preferences/testing/renderPreferences.tsx`（テストのファイル `*.test.tsx` ではない）が `auth` を読むもの（30〜32 行: `../../auth/authSession`・`../../auth/loginStateProvider`・`../../auth/registration`）。BR2.3 は「テストでないファイル（testing/ の部品など）が他の機能を読めば BR2.1 で誤り」、BR2.4 は違反を `useRegistration.ts` の1件だけとしており、計画にこの3件の扱いが無いため、依頼者の判断を待って止めた。
- ほかの検査: `npx oxlint .` 成功、`npx prettier --check .` 成功、`npm run license:check` 成功。

## Step 18（続き）: 依頼者の決定の後

- 依頼者の決定 1（A）: `features/preferences/testing/renderPreferences.tsx` から auth の import を外し、auth の登録・提供元・認証の状態の準備を `PreferencesTestAuth`（`auth` の引数）で受ける形にした。呼ぶ側のテストのファイル3つ（`PasswordChangePage.test.tsx`・`PreferencesPage.test.tsx`・`registration.test.tsx`）が auth から読んで渡す。`npx vitest run src/features/preferences`: 9 ファイル・89 件が通過。
- 依頼者の決定 2: 道の型を深さごとに作る形を受け入れた。
- `npx eslint .`: 違反 0 件（終了 0）。

## Step 19: ESLint の制限 — テスト

- `frontend/src/eslintImportRules.test.ts`（node の実行環境、`ESLint` の部品を `beforeAll` で1回作る）: 12 件（node で動く1、止める4、許す4、同じ名前のファイルを兄弟の機能と取り違えない1、名前の重なりの検出2）。設定の関数は `eslint.config.js` を道の文字から動的に読み込んで使う（JS の設定ファイルに型の宣言が無いため）。
- `npx vitest run src/eslintImportRules.test.ts`: 2回流して 12 件が通過（約 0.9 秒）。

## Step 20: 文書

- `frontend/src/features/README.md` に4つの節（サイドバーの項目の区画とアイコン・`useLogout`・機能どうしの import の制限・共有の木）を足した。`ApiAccess` の Javadoc は Step 4 で書いた。

## Step 21: 画面の区切りの確かめ

- `npm run format:check && npm run lint && npm run typecheck && npm run license:check`: 成功。

## Step 22: 記録

- `code-summary.md`・`source-manifest.json`（59 件）・`traceability.json`（110 行: OK 69・Deferred 36・N/A 5。OK の target はどれも実在するファイルであることを書き出しの中で確かめた）。

## Step 23: 1コマンドの検査

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の環境変数つき）: 成功。07:54:13〜08:08:10、Gradle の表示で 13 分 56 秒。
- テストの件数（実測）: バックエンドの単体 1,577 件・結合 712 件（どちらも失敗 0・飛ばし 0。対象DB のテストも SKIPPED 無し）。画面 112 ファイル・1,004 件。
- `ApiAccessConsistencyIT` のテストのクラスの所要: 8.6 秒。
- バックエンドのカバレッジ（Step 3 の基準 → 今回）:

| パッケージ | 行（前 → 後） | 分岐（前 → 後） |
|---|---|---|
| `common.security` | 14/14 100.0% → 18/18 100.0% | 6/6 100.0% → 6/6 100.0% |
| `auth.web` | 100.0% → 100.0% | 95.5% → 95.5% |
| `appearance.web` | 100.0% → 100.0% | 分岐なし |
| `common.error.web` | 97.2% → 97.2% | 89.4% → 89.4% |
| `invitation.web` | 97.3% → 97.3% | 87.0% → 87.0% |
| `user.web` | 96.5% → 96.5% | 84.6% → 84.6% |
| `useradmin.web` | 98.8% → 98.8% | 93.8% → 93.8% |
| `dslmanage.web` | 100.0% → 100.0% | 86.7% → 86.7% |
| `access.web` | 100.0% → 100.0% | 93.8% → 93.8% |
| 全体 | 98.9% → 98.9%（6420/6490） | 94.8% → 94.8%（2381/2511） |

  下がったパッケージは無い（`common.security` は `ApiAccessLevel` の4行が足され、検査が読むことで覆われた）。
- 画面のカバレッジ（全体）: 行 97.49%（3000/3077）・分岐 92.67%（1947/2101）・文 97.33%。足した分: `shared/tree/SharedTreeView.tsx` 行 98.07%・分岐 86.66%、`SharedTreeNodeItem.tsx` 行 100%・分岐 91.66%、`app/registry/allowedIcons.ts`・`validateRegistrations.ts` 100%・100%、`app/login-state/LoginStateGate.tsx` 行 100%・分岐 85%（`shared/tree/types.ts` は型だけで計測の対象の行が無い）。
- 変えないもの（計画 4.5）: `git diff --stat develop -- .github/workflows/ci.yml build.gradle.kts backend/build.gradle.kts frontend/vitest.config.ts frontend/vitest.setup.ts gradle/libs.versions.toml backend/gradle.lockfile frontend/package.json frontend/package-lock.json backend/src/main/resources compose.yaml Dockerfile .env.example docker/monitoring vendor backend/src/main/java/cherry/mastersmith/access/service` の差は空（`access.service` を含む）。

## Step 24: E2E

- `docker compose --profile mail up -d mailpit`（起動済みだった）の後に `./gradlew e2eTest`（08:08:51〜08:15:29、Gradle の表示で 6 分 37 秒）: 成功。13 ファイル・153 件が通過（失敗 0・飛ばし 0・不安定 0、`020-auth.e2e.ts`・`090-invitation-registration-flow.e2e.ts` を含む）。新しい E2E は足していない。配備したアプリ（8080）とは別の番号（18081）で起動するため、配備したアプリは止めていない。
- json の報告（`test-results/e2e-results.json`）の確かめ: トークンの形の文字は 0 件。メールアドレスの形の文字は1件で、`config.webServer.env.MASTERSMITH_MAIL_FROM`（送り元の設定の見本、`example.com`、利用者のメールアドレスでも資格情報でもない、前からの設定）だった。秘密の値の確かめの報告の部品（`playwright-secret-check-reporter.ts`）も通った。
- 確かめた後に `frontend/playwright-report`（今回は作られていない）・`frontend/test-results` を消した。

## 読み直しの R-01・R-04 の直し（C1〜C5 のコミットの後、依頼者の決定）

- R-01: `frontend/eslint.config.js` の機能どうしの制限を、深さちょうどのファイルにだけ当たる `files`（`**` なし）で深さ 0〜6 ごとに作り、深さが上限 6 を超えるファイルがあれば設定の読み込みで失敗させる形にした（`MAX_FEATURE_DEPTH`・`findTooDeepFeatureFiles`・`filesAtDepth` を名前付きで出す）。`frontend/src/eslintImportRules.test.ts` に 10 件を足した（22 件）。
- R-04: `code-summary.md` の冒頭を C1〜C5 のコミット済みに直し、4節に D-17・D-18 を足した。
- 確かめ: `npm run format:check`・`npm run lint`（`npx eslint .` の違反 0 件）・`npm run typecheck`・`npm run license:check` が成功。`eslintImportRules.test.ts` 22 件、画面の全体 112 ファイル・1,014 件が通過。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の環境変数つき）: 成功（08:32:01〜08:41:35、Gradle の表示で 9 分 34 秒）。バックエンドの単体 1,577 件・結合 712 件（失敗 0・飛ばし 0）、画面 1,014 件。カバレッジはバックエンドの全体 行 98.9%・分岐 94.8%、`common.security` と8つの `web` は前回の verify と同じ値、画面の全体 行 97.49%・分岐 92.67%。
- E2E は画面のコードを変えていないため流し直していない（依頼のとおり）。`source-manifest.json`・`traceability.json` は道が増えていないため変えていない。
