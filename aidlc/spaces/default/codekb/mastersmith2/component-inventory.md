# 部品の一覧（mastersmith2）

## 読み方

- 見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回の 39 個をそのまま使う（今回の追加・名前の変更は無い）。
- 状態: healthy（問題なし）・at-risk（今の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ:
  - 「深い（一部）」は今回の走査（コミット `d5aea52`、深さ Standard）で深く読んだファイルを持つ部品で、`analyzed.components` の 21 個。読んだのは今回の論点に関わるファイル（と範囲）だけで、部品の全体ではない。読んだファイルは各部品に書いた。
  - 「流し読み」はディレクトリとファイルの名前・検索だけで、責務の文はその範囲と前回までの記録による。
- K の番号は `business-overview.md` の所見の一覧を指す。この文書に本文を書いたのは K-36（`make-you-chic-ui`）だけで、ほかは持ち主の文書を参照する。
- パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省いて書く。依存の向きは `dependencies.md`。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて `backend/src/main/resources/application.yaml`・`logback-spring.xml`）
- 責務: Spring Security の連鎖（`config/SecurityConfig.java`）、SPA の配信、観測、転送ヘッダー、アプリの設定値。
- 今回読んだもの: `config/SecurityConfig.java`。公開の決まり（`/actuator/health`・`/api/problems/**`、120 行）→ 各機能の `SecurityRuleContributor` を order 順に当てる → `/api/**` の既定（`ApiDefaultAccess`、129〜135 行）→ 画面の配信は許可、の並び（K-33、本文は `architecture.md` の Interaction Diagrams 1）。
- 状態: at-risk（K-33 で決まりの並びに手が入りうる） ／ 読みの深さ: 深い（一部。`SecurityConfig.java` だけ）

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: `ProblemType`（code・状態コード・日英の文言）、`BusinessException`、`@RestControllerAdvice` による Problem Details、`/error`、`GET /api/problems/{slug}`。
- `common.error.domain`・`common.error.service` は `packagesJudgedByTotal` に残る（K-38）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-persistence

- 場所: `common/persistence/`
- 責務: 行の排他の結果の型 `RowLockAttempt`（`Acquired`・`Busy`）と、排他の失敗の見分け（前回までの記録）。`useradmin` の管理者の行の排他（K-34）が使う。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-security

- 場所: `common/security/`（`SecurityRuleContributor.java`・`ApiDefaultAccess.java`・`SecurityExtensionValidator.java`・`ErrorResponseWriter.java`）
- 責務: 安全の決まりの差し込み口の型 `SecurityRuleContributor`（order つき）と `/api/**` の既定の口 `ApiDefaultAccess`、差し込みの検査、エラー応答の書き出し。
- 今回読んだもの: ディレクトリのすべてのファイル。役割ごとの決まりを足すときの口（K-33）。
- 状態: at-risk（K-33） ／ 読みの深さ: 深い（一部。ディレクトリの全ファイル）

### common-web

- 場所: `common/web/`・`common/paging/`
- 責務: 本文の大きさの上限、Web の設定値、一覧のページ送りの共通の計算。
- `common.web` は `packagesJudgedByTotal` に残る（K-38）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-health

- 場所: `common/health/`
- 責務: ヘルスチェック。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-i18n

- 場所: `common/i18n/`
- 責務: 表示言語の解決（日英）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-observability

- 場所: `common/observability/`
- 責務: `TraceAspect`（`web`・`service`・`domain`・`repository` の層の引数と戻り値を TRACE のときに文字列にして出す）ほか（前回までの記録）。役割・権限の値を受け渡す型も、個人に関する値を持つなら `toString` で伏せる必要がある（`team.md` の Code Style）。
- 状態: healthy ／ 読みの深さ: 流し読み

### auth

- 場所: `auth/{domain,service,repository,web}`
- 責務: ログインとアカウントロック、アクセストークン（HS256 の JWT）とリフレッシュトークン、ログアウト、要求ごとのアクセストークンの認証と主体の組み立て、初期管理者の救済の受け手（`service/InitialAdminRescueListener.java`、ファイル名だけ）。
- 今回読んだもの: `domain/AuthenticatedUser.java`、`web/AuthenticatedUserToken.java`・`AccessTokenAuthenticationProvider.java`・`AuthSecurityContributor.java`（order 110）・`CurrentUserResponse.java`・`TokenResponse.java`、`service/AccessTokenService.java`（発行と検証の 83〜140 行）。権限の持ち方の持ち主（K-32、本文は `architecture.md` の Interaction Diagrams 1）と、画面に `admin` を渡す応答（K-35）。
- 依存: `user`（service の口と domain）・`common`
- 状態: at-risk（K-32・K-35） ／ 読みの深さ: 深い（一部。上の7ファイル）

### access

- 場所: `access/{domain,service,web}`
- 責務: `/api/admin/**` を管理者だけにする決まり（`web/AdminSecurityContributor.java`、order 210）、管理者の判定（`web/AdminAuthorizationManager.java`）、`/api/**` の既定をログイン必須にする決まり（`web/AdminApiDefaultAccess.java`）、401・403 の応答と拒否の出来事（`web/AdminAccessDeniedHandler.java` ほか）、`GET /api/admin/check`。
- 今回読んだもの: `domain/AdminPaths.java`・`AccessDeniedReason.java`・`AccessProblemTypes.java`、`web/AdminAuthorizationManager.java`・`AdminSecurityContributor.java`・`AdminApiDefaultAccess.java`・`AdminCheckController.java`・`AdminAccessDeniedHandler.java`。`service/` は流し読み。K-33 の持ち主（本文は `architecture.md` の Interaction Diagrams 1）。
- 依存: `auth.domain`・`auth.web`（`ClientInfoResolver`・`TokenAuthenticationEntryPoint`）・`config`（`SecurityHeaderProperties`）・`common.error`・`common.security`（今回の import の検索）
- 境界テストが無く、`access.service` は `packagesJudgedByTotal` に残る（K-38）。
- 状態: at-risk（K-33・K-38） ／ 読みの深さ: 深い（一部。`domain` の3ファイルと `web` の5ファイル）

### audit

- 場所: `audit/{domain,service,repository}`（Web の層なし）
- 責務: 各機能の出来事を受け、確定の後に `audit_events` に追記する。監査を見る画面・API は無い。
- 今回読んだもの: `domain/AuditEventType.java`（22 個。`USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`INITIAL_ADMIN_CREATED`・`INITIAL_ADMIN_RESCUED` を含む）、`service/AuditEventListener.java`（受け取りの一覧）。役割の割り当ての監査を足すときの持ち主（K-38、本文は `code-quality-assessment.md`）。
- `audit.repository` は `packagesJudgedByTotal` に残る。
- 状態: at-risk（K-38） ／ 読みの深さ: 深い（一部。上の2ファイル）

### user

- 場所: `user/{domain,service,repository,web}`
- 責務: 利用者（表 `users`、V2・V7・V9。管理者の印 `admin_flag` と停止の列を持つ）、パスワード、氏名と表示の設定、自分の設定の API（`/api/me/**`）、起動時の初期管理者の作成と救済。ほかの機能には `service/UserAccountService.java` の口だけを見せる。
- 今回読んだもの: `domain/User.java`（列の定義と作る口）、`service/UserSummary.java`（`userId`・`email`・`admin` ほか）、`repository/UserRepository.java`（問い合わせの一覧。`findActiveAdminIds` 182 行）。K-32・K-34 に関わる。
- 依存: `common`
- 状態: at-risk（K-32・K-34） ／ 読みの深さ: 深い（一部。上の3ファイル）

### invitation

- 場所: `invitation/{domain,lock,repository,service,web}`
- 責務: 招待・送り直し・取り消し・一覧（`/api/admin/invitations`）、リンクの確かめと登録の完了（`/api/registration/**`、ログインなし）。
- 今回読んだもの: `web/InvitationSecurityContributor.java`（決まりの部分。order 310 で登録の API を公開にする）。
- 状態: healthy ／ 読みの深さ: 深い（一部。`InvitationSecurityContributor.java` の決まりの部分だけ）

### useradmin

- 場所: `useradmin/{domain,service,web}`
- 責務: 利用者の管理の API（`/api/admin/users`）。業務処理は結果の型を返し、controller が業務エラーに変える。監査の出来事を知らせる。
- 今回読んだもの: `domain/AdminOperation.java`・`RejectionReason.java`、`service/UserAdminService.java`（250〜398 行。管理者の行の排他と判定の流れ）、`web/AdminUser.java`、テスト `backend/src/test/java/cherry/mastersmith/useradmin/UserAdminBoundaryArchitectureTest.java`（決まりの一覧）。K-34 の持ち主（本文は `architecture.md` の Interaction Diagrams 2）。
- 依存: `user`（domain・service）・`auth`（domain・service）・`access.domain`（`AccessProblemTypes` だけ）・`common`
- 状態: at-risk（K-34） ／ 読みの深さ: 深い（一部。上の4ファイルとテスト1つ）

### mail

- 場所: `mail/{config,domain,service,template,transport}`
- 責務: SMTP の送信と Mustache のテンプレート（エンジンは `java-mustache-processor`）。
- 状態: healthy ／ 読みの深さ: 流し読み

### appearance

- 場所: `appearance/{config,service,web}`
- 責務: インスタンスの見た目の設定を `GET /api/appearance` で画面に渡す。
- 今回読んだもの: `web/AppearanceSecurityContributor.java`（決まりの部分。order 410 で公開にする）。
- 状態: healthy ／ 読みの深さ: 深い（一部。`AppearanceSecurityContributor.java` の決まりの部分だけ）

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`
- 責務: 対象DB の接続とスキーマの読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み

### dsl

- 場所: `dsl/{domain,parse,validate,service}`（あわせて `backend/src/main/resources/dsl/dsl-schema-v1.json`）
- 責務: DSL の型・安全な読み込み・検証・適用中のモデル（メニューの木を含む）の保持と提供口 `ActiveDslModelProvider`。
- 今回読んだもの: `domain/DslMenuItem.java`・`DslModel.java`（メニューの項目）・`DslFormat.java`（上限の定数）、`service/ActiveDslModelProvider.java`、`validate/DslSemanticValidator.java`（メニューの検証の 78〜117 行）、`dsl-schema-v1.json`（`menus`・`menuItem`）。K-37 の持ち主（本文は `api-documentation.md`）。
- 状態: at-risk（K-37 で提供口の使い方・検証が増えうる） ／ 読みの深さ: 深い（一部。上の6ファイル）

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`
- 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴（`/api/admin/dsl`）。
- 今回読んだもの: `generate/DslTreeBuilder.java`（既定のメニューの生成の 60〜84 行）。テーブルごとに1段のメニューだけを作る（K-37）。
- 状態: healthy ／ 読みの深さ: 深い（一部。`DslTreeBuilder.java` の上の範囲だけ）

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/`（380 ファイル）
- 責務: 機能ごとの `testsupport` と共通の `common/testsupport`、アプリ全体を起動する結合テスト、層と境界の検査。
- 今回読んだもの: `ArchitectureTest.java`（全体の層の決まりの一覧）、`useradmin/UserAdminBoundaryArchitectureTest.java`・`auth/AuthBoundaryArchitectureTest.java`（決まりの一覧）。境界テストは 10 個あり、`access` には無い（K-38）。認可のテスト用の決まり `access/testsupport/PublicApiTestRules`・`AdminTestUsers` は名前だけ。
- 状態: at-risk（K-38） ／ 読みの深さ: 深い（一部。上の3ファイル）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/pages/`・`app/admin-forbidden/`・`app/login-handoff/`・`app/login-state/`・`shared/validation`・`shared/format`・`shared/modal`
- 責務: 画面の起動と骨組み、共通の画面（ホーム・403 の画面 S6）、管理の API の 403 を受けて S6 に切り替える `AdminForbiddenProvider`、日時の書式、入力の確かめ。
- 今回読んだもの: `app/admin-forbidden/AdminForbiddenProvider.tsx`、`app/pages/HomePage.tsx`。K-35 に関わる（本文は `architecture.md` の Interaction Diagrams 3）。前回の一覧の場所に `app/admin-forbidden/`・`shared/modal` が無かったため、ここに足した（ID は変えていない）。
- 状態: at-risk（K-35） ／ 読みの深さ: 深い（一部。上の2ファイル）

### frontend-registry

- 場所: `app/registry/`・`app/navigation/`
- 責務: `features/<featureId>/registration.ts` を置くだけで機能を読み込む仕組み、4つの差し込み口の型、サイドバー・ユーザーメニューの項目の組み立て（`buildSidebarEntries`・`buildUserMenuItems`）。
- 今回読んだもの: `app/registry/types.ts`・`registrationModules.ts`・`loadRegistrations.ts`・`validateRegistrations.ts`、`app/navigation/navigationItems.ts`。K-35 の中心（本文は `architecture.md` の Interaction Diagrams 3）。
- 状態: at-risk（K-35・K-36） ／ 読みの深さ: 深い（一部。上の5ファイル）

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/routing/`
- 責務: AppShell の配置（`ShellLayout.tsx`）、表示言語、振り分け（`decideRoute.ts`・`AppRouter.tsx`）。
- 今回読んだもの: `app/layout/ShellLayout.tsx`、`app/routing/decideRoute.ts`・`AppRouter.tsx`。アイコンの固定（46 行）と、管理者の画面の振り分け（63 行）（K-35・K-36）。
- 状態: at-risk（K-35・K-36） ／ 読みの深さ: 深い（一部。上の3ファイル）

### frontend-app-display-settings

- 場所: `app/display-settings/`
- 責務: 利用者の表示の設定とインスタンスの見た目の設定を解決し、make-you-chic-ui の `ThemeProvider` に渡す。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`・`shared/paging`
- 責務: 同じオリジンの `/api/**` の呼び出し、トークンの付与、401 で1回だけ更新して送り直す（`refreshSessionOnce`）、Problem Details の読み取り、管理の API の 403 の判定 `isAdminForbidden`。
- 今回読んだもの: `shared/api-client/adminForbidden.ts`。判定は「パスが `/api/admin/` で始まる・403・`ACCESS_DENIED`」に固定（K-35）。開発担当は深く読んだ部品に挙げていないが、深く読んだファイルを持つため `analyzed.components` に入れた。
- 状態: at-risk（K-35） ／ 読みの深さ: 深い（一部。`adminForbidden.ts` だけ）

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログインの画面とログインの状態の提供元。
- 今回読んだもの: `loginStateProvider.ts`（`CurrentUserResponse.admin` を `LoginState.admin` に写す。K-35）。
- 状態: at-risk（K-35） ／ 読みの深さ: 深い（一部。`loginStateProvider.ts` だけ）

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理の入口の画面 `/admin`。サイドバーの項目（order 200、`ADMIN`）。
- 今回読んだもの: `registration.ts`。
- 状態: healthy ／ 読みの深さ: 深い（一部。`registration.ts` だけ）

### frontend-feature-dsl

- 場所: `features/dsl/`
- 責務: DSL の管理画面 `/admin/dsl`（order 210）。プレビューのメニューの木を `DslMenuTree.tsx` で描く。
- 今回読んだもの: `DslMenuTree.tsx`（16〜60 行。入れ子のリストと `aria-expanded` の開閉のボタン、ARIA の tree の役割は使わない）、`api/types.ts`（`MenuNode`、`icon` は無い）。K-36・K-37 に関わる。
- 状態: healthy ／ 読みの深さ: 深い（一部。上の2ファイル）

### frontend-feature-invitation

- 場所: `features/invitation/`
- 責務: 管理者の招待の画面 `/admin/invitations`（order 220）。
- 状態: healthy ／ 読みの深さ: 流し読み（サイドバーの項目の値だけ検索で確かめた）

### frontend-feature-useradmin

- 場所: `features/useradmin/`
- 責務: 利用者の管理の画面（order 230）。
- 状態: healthy ／ 読みの深さ: 流し読み（サイドバーの項目の値だけ検索で確かめた）

### frontend-feature-registration

- 場所: `features/registration/`
- 責務: 招待を受けた人のリンクの確かめと登録の完了の画面（ログインなし）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-preferences

- 場所: `features/preferences/`
- 責務: 自分の設定とパスワードの変更の画面（ユーザーメニューの項目 order 80・90）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-e2e

- 場所: `frontend/e2e/`（`*.e2e.ts` 13 本、010〜130）
- 責務: Playwright の E2E（`./gradlew e2eTest`）。`verify` と CI の外。認可に関わるものは `030-admin-access`・`110-user-admin-flow`・`130-admin-forbidden-accessibility` など（名前だけ）。
- 状態: healthy ／ 読みの深さ: 流し読み

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `e82b651`。画面からは npm の `file:` の依存で使う）
- 責務: デザインシステム（AppShell・Button・Dropdown・Modal・Tabs・Table・TextInput・Toast ほか）。中身はこのリポジトリから変えない（`project.md` の Forbidden）。
- 今回読んだもの: `packages/make-you-chic-ui/src/components/AppShell/AppShell.tsx`・`Sidebar.tsx`、`Icon/registry.ts`。`Dropdown`・`Tabs` は props だけ流し読み。
- K-36 サイドバーは平らで、N 階層のメニューの部品は make-you-chic-ui に無い（確かめた事実）:
  - 骨組みの `buildSidebarEntries` は「ホーム」＋表示の条件を満たす登録の項目を `order` の順に並べた平らな一覧を作る（`frontend/src/app/navigation/navigationItems.ts`）。
  - `AppShell` の `navItems` の型 `AppShellNavItem`（定義は `Sidebar.tsx` 20〜25 行付近）は `label`・`icon?`（`IconName`）・`href`・`onClick?` だけで、子の項目・開閉・グループの見出しの口は無い。`Sidebar` は平らな `<ul>` を描き、`key={item.href}`（49 行）、`aria-label="メインナビゲーション"`（45 行、日本語の固定）で、今の項目を示す `aria-current` も無い。`Sidebar` は外へ出していない（`AppShell` の中だけ）。
  - アイコンは 18 種類（`menu`・`chevron-down`・`chevron-up`・`close`・`check`・`bell`・`user`・`search`・`edit`・`trash`・`download`・`settings`・`home`・`list`・`info`・`success`・`warning`・`danger`、`Icon/registry.ts`）。
  - 同じリポジトリの中の N 階層の木の前例は `frontend/src/features/dsl/DslMenuTree.tsx` だけで、機能の中の部品（`shared/` にも骨組みにも無い）。
- K-36 見立て（未検証）:
  - N 階層のサイドバーには、(a) make-you-chic-ui に入れ子のナビゲーション（子の項目・開閉・`aria-current`・言語に合わせた `aria-label`）を足してもらう、(b) `AppShell` のサイドバーを使わず骨組みが自前で描く、のどちらかが要る。(b) を選ぶときは、`project.md` の学び（make-you-chic-ui に無い機能を自前で作る設計にしたときは、承認の前に足りない点を一覧にし、取り込みを依頼者に諮る）に当たる。(a) の取り込みは、固定先の更新を承認を得た専用のコミットで行う（`project.md` の Mandated）。
  - 項目の `key` が `href` のため、同じ行き先を持つ項目が2つあると重なる。まとまり（子だけを持ち行き先の無い節）を平らな一覧に載せる形は、今の型では表せない。
  - DSL の `icon` を画面に出すなら、18 種類に無い名前の扱いを決める必要がある（K-37）。
- 状態: at-risk（K-36） ／ 読みの深さ: 深い（一部。上の3ファイル）

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、固定先 `8d44c36`）。Gradle の composite build で使う。
- 責務: メールのテンプレートの Mustache のエンジン。今回の Intent では触れない見込み。
- 状態: healthy ／ 読みの深さ: 流し読み

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`backend/config/spotbugs-exclude.xml`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vitest.config.ts`・`config/`・`.github/workflows/ci.yml`・`.github/dependabot.yml`
- 責務: 1コマンドの検査 `./gradlew verify`、E2E の `./gradlew e2eTest`、WAR の組み立て、依存の取得元の固定と composite build、lockfile、カバレッジの下限（JaCoCo の全体とパッケージごと、`packagesJudgedByTotal`）、SpotBugs の関門、Gitleaks・OSV-Scanner、Dependabot。
- 今回読んだもの: `backend/build.gradle.kts`（カバレッジの検証と `packagesJudgedByTotal`、225〜233 行）、`gradle/libs.versions.toml`。K-38 に関わる（本文は `code-quality-assessment.md`）。`build.gradle.kts`・`ci.yml`・`frontend/package.json`・`vitest.config.ts` は流し読み。
- 状態: at-risk（K-38） ／ 読みの深さ: 深い（一部。上の2ファイル）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`・`README.md`
- 責務: WAR をコピーするだけのイメージ、`app` のサービスと、profile で起動する監視・メールの受け手・見本の対象DB（前回までの記録。今回は読み直していない）。
- 状態: healthy ／ 読みの深さ: 流し読み

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験と使い捨ての環境、手元の監視の警報とダッシュボード、接続プールの運用の道具（前回までの記録。今回は読み直していない）。
- 状態: healthy ／ 読みの深さ: 流し読み
