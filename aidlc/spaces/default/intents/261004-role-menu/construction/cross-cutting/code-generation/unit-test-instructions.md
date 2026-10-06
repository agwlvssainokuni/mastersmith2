# 単体テストの手順（Unit Test Instructions）— U1 横断の準備（cross-cutting）

計画: `code-generation-plan.md`（同じディレクトリ）。Test Strategy は Standard（部品ごとに 5〜8 件と、要所の結合テスト）、方法は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流す）。

ここに書くコマンドは、どれもこの単位のテストだけを名指しで流す。素の `./gradlew test`・`./gradlew verify`・`npm test` はこの単位の確かめには使わない（統合の前の関門の `verify` は計画の Step 23 で別に流す）。

## 1. テストの道具と設定（既存のものをそのまま使う）

| 対象 | 道具 | 設定 | この単位で変えること |
|---|---|---|---|
| バックエンドの単体（`*Test`） | JUnit 5・AssertJ・ArchUnit 1.5.1 | `backend/build.gradle.kts` の `tasks.test`（`includeTestsMatching("*Test")`）、`backend/src/test/resources/archunit.properties`（`archRule.failOnEmptyShould = false`） | 変えない。この単位の規則だけ `allowEmptyShould(false)` |
| バックエンドの結合（`*IT`） | Spring Boot Test・spring-security-test・組み込みの H2（`TestDatabase`） | `integrationTest`（`includeTestsMatching("*IT")`） | 変えない。コンテナは使わない |
| 画面 | Vitest・Testing Library（jsdom）・user-event・vitest-axe・`@vitest/coverage-v8` | `frontend/vitest.config.ts`（`include: ['src/**/*.test.{ts,tsx}']`、`setupFiles: ['./vitest.setup.ts']`） | 変えない |
| ESLint の決まりのテスト | Vitest の node の実行環境（ファイルの先頭で `@vitest-environment node`）と ESLint の `ESLint` の部品 | 同上 | 変えない。node 環境で `vitest.setup.ts` が読めるかを計画の Step 2 で先に確かめ、だめなら jsdom のまま流す |

新しい依存は足さない（`backend/gradle.lockfile`・`frontend/package-lock.json` を変えない）。

## 2. テストを始める前に動くことを確かめるコマンド（計画の Step 2）

```bash
# バックエンド（既存のテストでコマンドが動くことを確かめる）
./gradlew :backend:test --tests cherry.mastersmith.ArchitectureTest
./gradlew :backend:integrationTest --tests cherry.mastersmith.access.web.ApiDefaultAccessIT

# 画面（既存のテストで動くことを確かめる）
cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/validateRegistrations.test.ts
```

## 3. この単位のテストを流すコマンド

### 3.1 バックエンド

```bash
# 単体（注釈・静的な検査・検査の検査・PUBLIC の一覧・境界テスト）
./gradlew :backend:test \
  --tests cherry.mastersmith.common.security.ApiAccessTest \
  --tests cherry.mastersmith.ApiAccessArchitectureTest \
  --tests cherry.mastersmith.ApiAccessRulesTest \
  --tests cherry.mastersmith.PublicApiInventoryTest \
  --tests cherry.mastersmith.access.AccessBoundaryArchitectureTest \
  --tests cherry.mastersmith.user.UserBoundaryArchitectureTest

# 結合（実行時の検査。組み込みの H2 の一時の内部DB だけを使い、コンテナは要らない）
./gradlew :backend:integrationTest --tests cherry.mastersmith.ApiAccessConsistencyIT
```

テストの結果が UP-TO-DATE で飛ばされたときは、`:backend:cleanTest`（単体）・`:backend:cleanIntegrationTest`（結合）を前に付けて流し直し、実際に流れた件数だけを記録する（`project.md` の学び）。

### 3.2 画面

```bash
cd frontend

# 登録の型と登録の検査（足した問題の境界）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/validateRegistrations.test.ts

# ログアウトの口と既存の違反の直し
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/login-state/LoginStateGate.test.tsx \
  src/features/auth/loginStateProvider.test.ts \
  src/features/registration/RegistrationPage.test.tsx

# 共有の木
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/tree/SharedTreeView.test.tsx

# ESLint の制限の決まり（名前は計画の案。生成で名前を変えたら code-summary.md に記録する）
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/eslintImportRules.test.ts
```

登録の型に必須の `section` を足すことで型を直す既存のテスト（振る舞いは変えない）は、次で通ることを確かめる。

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/navigation/navigationItems.test.ts \
  src/app/layout/ShellLayout.test.tsx \
  src/app/routing/AppRouter.test.tsx \
  src/app/admin-forbidden \
  src/features/admin/registration.test.ts \
  src/features/dsl/registration.test.tsx \
  src/features/invitation/registration.test.tsx \
  src/features/useradmin/registration.test.tsx
npm run typecheck
```

### 3.3 画面の制限の確かめ（テストではない、計画の Step 18・21）

```bash
cd frontend && npx eslint .       # 本体の違反が 0 件
```

## 4. テストの中身（Standard、部品ごと）

| 部品 | ファイル | 確かめること | 件数の目安 |
|---|---|---|---|
| 注釈 | `backend/src/test/java/cherry/mastersmith/common/security/ApiAccessTest.java` | 保持が `RUNTIME`、付けられる所が `TYPE`・`METHOD`、値が `PUBLIC`・`AUTHENTICATED`・`ADMIN` の3つちょうど | 3 |
| 静的な検査 | `backend/src/test/java/cherry/mastersmith/ApiAccessArchitectureTest.java` | 本番のクラス（`DO_NOT_INCLUDE_TESTS`）で、印は一方だけ・ADMIN と `AdminPaths.isAdminOnly` の両方向・AUTHENTICATED は `/api/` の下で管理者の道の外・`common.security` は機能に依存しない、口が 34 以上 | 5 |
| 検査の検査（規則） | `backend/src/test/java/cherry/mastersmith/ApiAccessRulesTest.java` | 違反の見本5つ（印なし・二重・`/api/other` の ADMIN・`/api/admin/x` の AUTHENTICATED・`/other` の AUTHENTICATED）がそれぞれ `AssertionError` で落ち、文に見本のクラス名と方法名が入る。`ValidSample` は3つの規則を通る。空の集まりは落ちる | 7 |
| 検査の検査（PUBLIC の一覧） | `backend/src/test/java/cherry/mastersmith/PublicApiInventoryTest.java` | 行を足す→増えた行、外す→減った行、変数名 `{slug}`→`{name}` で増減1行ずつ、`/error` の方法に `TRACE` を足す・`HEAD` を外すで増減1行ずつ、変えなければ差なし。`entryOf` は宣言した方法だけ（HEAD・OPTIONS を足さない）で変数名を残す | 8 |
| 実行時の検査 | `backend/src/test/java/cherry/mastersmith/ApiAccessConsistencyIT.java` | 34 以上の口×宣言された方法×3つの主体（未ログイン＝匿名のトークン、管理者でない利用者、管理者）の判定が期待の表どおり。口の集合が静的な検査とクラスと方法で一致し、本番のクラスだけ。印が PUBLIC の口が一覧の 9 行と一致 | 5 |
| 境界テスト | `backend/src/test/java/cherry/mastersmith/access/AccessBoundaryArchitectureTest.java`・`.../user/UserBoundaryArchitectureTest.java` | 今の依存のまま（依存してよい機能・依存される側） | 各 2〜4 |
| 登録の検査 | `frontend/src/app/registry/validateRegistrations.test.ts` に足す分 | 正しくない section、section ADMIN と visibleWhen LOGGED_IN、一覧の icon は通る、一覧に無い icon と空の文字は `RegistrationError`、icon なしは通る、4件の登録が通る | 6 |
| ログアウトの口 | `frontend/src/app/login-state/LoginStateGate.test.tsx`・`frontend/src/features/auth/loginStateProvider.test.ts`・`frontend/src/features/registration/RegistrationPage.test.tsx` に足す分 | `useLogout()` の関数が提供元の `logout` を呼んで真を返す、`logout` が無ければ何もせず偽を返す、`logout` の失敗を外へ出さず真を返す、提供元が無ければ偽を返す（計画の Q-B: B）、auth の提供元が `logout` を持ち `authSession` の `logout` を呼ぶ、既存の「ログアウトして続ける」が通る、提供元に `logout` が無いと「ログアウトして続ける」の後に画面が元に戻り押し直せる（`RegistrationPage.test.tsx`） | 7 |
| 共有の木 | `frontend/src/shared/tree/SharedTreeView.test.tsx` | 開閉と `aria-expanded`、選びと `aria-current`（読み込んだ節に無ければ無い）、最初に開いたときだけ読む、`nodes` の替えで読み直し・古い結果を捨てる、失敗の文と再試行（拒否の理由は出ない）、0 件の文、`<`・`>`・`&`・`"`・`'`・`<script>` が文字として出る、開閉のボタンの名前に表示名、Tab・Enter・Space だけで深い節まで、渡した文言だけ、vitest-axe 1件 | 8〜12 |
| ESLint の制限 | `frontend/src/eslintImportRules.test.ts` | 止める4（`features/registration` から `../auth/authSession`・`../../features/auth/authSession`、`shared/tree` から `../../app/registry/types`・`../../features/auth/authSession`）、許す4（`features/useradmin/testing` から `../api/types`、`features/auth` から `../../app/i18n/i18n`・`../../shared/api-client/apiClient`、テストのファイル `features/registration/x.test.ts` から `../auth/authSession`）、名前の重なりの検出（計画の Q-C: A。設定から切り出した関数に、重なる見本を渡すと誤り・重ならない見本と今の `src/features/` の一覧では通る） | 10 |

テストの説明文（`@DisplayName`・`describe`・`it`）は英語で書く。

## 5. カバレッジの目標

| 対象 | 目標 | 測り方 |
|---|---|---|
| バックエンドの全体 | 行 80%・分岐 70% 以上 | 統合の前の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（計画の Step 23）。レポートは `backend/build/reports/jacoco/` |
| `common.security` と8つの `web`（`auth.web`・`appearance.web`・`common.error.web`・`invitation.web`・`user.web`・`useradmin.web`・`dslmanage.web`・`access.web`） | パッケージごとに行 80%・分岐 70% 以上（どれも `packagesJudgedByTotal` に無い） | 同上。印は実行の中身を持たないため値は変わらない見込み。変更の前の基準（計画の Step 3、Q-D: A。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` の `jacocoTestReport.xml`）と変更の後（Step 23）の値を並べて記録し、下がったパッケージがあれば理由を書く |
| `packagesJudgedByTotal` | 7 パッケージのまま（増やさない・この単位では外すものが無い） | `backend/build.gradle.kts` に差が無いこと |
| 画面の全体 | 行 80%・分岐 70% 以上（`frontend/vitest.config.ts` の `thresholds`） | 同じ `verify` の段 7。レポートは `frontend/coverage/` |

計測の除外は増やさない。下限・閾値の設定は変えない。

## 6. モック・差し替えの指針

- **実行時の検査**: 本番の設定で起動し、本番の Spring Security の判定（`WebInvocationPrivilegeEvaluator`）をそのまま使う。判定・安全の決まりをモックにしない。テスト用の決まり（`PublicApiTestRules`）とテストだけの口（`mastersmith.test-fixture.*` の設定）は有効にしない。主体はトークンの値だけで作り、内部DB に利用者を作らない。
- **静的な検査と検査の検査**: モックを使わない。違反の見本は実際のクラス（`common/testsupport/apiaccess/`）で、`@ConditionalOnBooleanProperty("mastersmith.test-fixture.api-access-samples")` で Bean にならない。見本は `ClassFileImporter().importClasses(...)` で1つずつ読む。
- **PUBLIC の一覧の比べ**: `PublicApiInventory.diff` は純粋な関数で、Spring を起動せずに見本の集まりで確かめる。`entryOf` は Spring の `RequestMappingInfo.paths(...).methods(...)` で作った対応づけで確かめる。
- **画面**: 共有の木の `loadChildren` はテストの中で作る Promise（解決・拒否・保留を手で進める）。`onToggle`・`onSelect` は `vi.fn()`。ログイン状態の提供元はテストの中で作るオブジェクト（`logout` あり・なし・拒否）。`RegistrationPage.test.tsx` は今までどおり fetch の差し替えと本物の `authSession`。make-you-chic-ui はモックにしない。
- 描画の後（効果）に反映される値は `waitFor` で待つ。テストの時間の上限を原因を確かめずに延ばさない。時刻に依存する処理はこの単位に無い。

## 7. テストデータ

- 主体の見本: 利用者 ID は固定の数（例 1001・1002）、メールアドレスは `example.com` の見本（例 `member@example.com`・`admin@example.com`）。秘密の値・実在しそうな氏名は使わない。内部DB に入れない。
- 判定用の URI のパス変数は `1` に置き換える（例 `/api/problems/1`、`/api/admin/users/1/suspend`）。
- 違反の見本の道は `/api/other`・`/api/admin/x`・`/other`・`/api/sample` など、本番に無い道だけを使う。
- 共有の木の見本: 表示名は日本語でよい（例「販売DB」「受注」）。エスケープの確かめに `<script>alert(1)</script>`・`A&B "q" 'q' <b>` を使う。深い節の確かめは3段以上。
- ESLint の見本の文と道はテストのファイルの中に置き、`src/` に補助のファイルを作らない（計測の分母に入れないため）。
- 状態を持つデータ（内部DB の行）は作らないため、片付けは要らない（結合テストの一時の内部DB は `@TempDir` で消える）。
