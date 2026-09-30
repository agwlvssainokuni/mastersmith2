# 部品の一覧（mastersmith2）

## 読み方

見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回と同じ 36 個で、今回は足しも名前の変更もしていない。

- 状態: healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ: 「深い」は今回（コミット `31b980b`）深く読んだ部品で、`analyzed.components` に入れた 11 個。「深い（一部）」は、部品の責務のうち今回の Intent の型として要る部分だけを深く読んだもの（`invitation`・`config`・`frontend-feature-invitation`）。「流し読み」はファイル名・見出し・検索だけで、責務の文はその範囲と前回までの記録による。
- K の番号は `business-overview.md` の所見の一覧（今回 K-1 から振り直した）を指す。この文書に本文を書いたのは K-2（`auth`）・K-6（`audit`）・K-8（`user`）で、ほかは持ち主の文書を参照する。
- パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省いて書く。依存の向きは `dependencies.md`。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス（`@EnableScheduling` を含む）。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて `application.yaml`・`logback-spring.xml`）
- 責務: Spring Security の連鎖（`config/SecurityConfig.java`）。セッションなし・CSRF の仕組みなしで、各機能の差し込み口 `SecurityRuleContributor` を order 順に当てる。SPA の配信と観測の設定。
- 依存: `common-security`・`common-web`・`common-observability`
- 状態: healthy ／ 読みの深さ: 深い（一部。`SecurityConfig.java` だけ。`application.yaml` と観測の設定は一覧だけ）

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: `ProblemType`（code・状態コード・日英の文言）、`BusinessException`、`@RestControllerAdvice` による Problem Details、`GET /api/problems/{slug}`。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-security

- 場所: `common/security/`
- 責務: 秘密の値の伏せ字（`RedactedText` など）、安全の決まりの差し込み口の型 `SecurityRuleContributor`。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-web

- 場所: `common/web/`
- 責務: 本文の大きさの上限、Web の設定値（`mastersmith.web.base-url` ほか）。
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
- 責務: `TraceAspect`（`web`・`service`・`domain`・`repository` の層のメソッドの引数と戻り値を、ロガーが TRACE のときに文字列にして出す。`record` 自身・設定のクラス・起動クラスは対象外。`TraceAspect.java` 47〜57 行、アーキテクトが確かめた）、外部エクスポートの出口の伏せ字（`SanitizingLogRecordExporter`）ほか。一覧の型への影響は K-8（`user`）。
- 状態: healthy ／ 読みの深さ: 流し読み（`TraceAspect` の対象の指定だけ）

### auth

- 場所: `auth/{domain,service,repository,web}`（約 3,020 行）
- 責務: ログインとアカウントロック、アクセストークン（HS256 の JWT、クレームは `sub`・`iat`・`exp` だけ）とリフレッシュトークン（表 `refresh_tokens`、使うたびに無効にして作り直す）、ログアウト、期限切れのリフレッシュトークンの定期の削除。要求ごとのアクセストークンの認証（`web/AccessTokenAuthenticationProvider.java`）で DB から利用者を読み直す。設定は `service/AuthProperties.java`（アクセストークン 5 分・リフレッシュトークン 24 時間・ロックのしきい値 5・ロックの時間 30 分が既定）。
- 依存: `user`（service の口と domain）・`common-error`・`common-security`・`common-observability`
- 利用停止を効かせる3つの入口はこの部品にある（K-1、`architecture.md` の Interaction Diagrams 1）。
- K-2 ロックの状態は `auth` の表にあり、時刻で決まる（確かめた事実）:
  - 表 `login_attempt_states`（V3）: `subject_id`（利用者 ID、または -1〜-8 のダミー）・`consecutive_failures`・`locked_until`。`users` への外部キーは無く、ダミーの行（V3 で作成。存在しない利用者でのログインの時間をそろえるため）と同じ表にある。
  - ロック中の判定は `now` が `locked_until` より前か（`domain/LockPolicy.java` 52 行）。解除時刻を過ぎた後は、次の試みで失敗回数を 0 から数え直す（58 行）。したがって解除時刻を過ぎた行も、次のログインまで `locked_until` と失敗回数が残る。表の値だけで「ロック中」とすると誤り、注入した時計（`Clock`）と比べる必要がある。
  - 書き込みは明示の `update(subjectId, failures, lockedUntil)`（`repository/LoginAttemptStateRepository.java` 120 行〜）と、行の排他つきの読み取り `lockForUpdate`（65 行〜、待ちの上限 3 秒）。行が無い場合の作成は `createIfAbsent`（102 行〜）。
  - 利用者ごとの行は、`UserCreatedEvent` を受けた `service/LoginAttemptStateInitializer.java`（`Propagation.MANDATORY`、同じトランザクション）が作る。ログインの判定で行が無ければ `Decision.ROW_MISSING` になり、行を作ってやり直す（`service/LoginService.java` 157〜198 行と `createRow`）。行の無い利用者がありうる。
  - ロックしたときは INFO のログ（キー `userId`）を出す（`LoginService.java`）。既存の解除は時間の経過だけで、管理者による解除の操作と監査の種類は無い。
- K-2（見立て、未検証）:
  - ロックの解除は既存の口で「`lockForUpdate` で排他つきで読み、`update(id, 0, null)`」と書ける見込み。ログインの判定と同じ行の排他で順番がそろう。
  - 一覧・解除は「行が無い＝ロックしていない」と扱う必要がある。
  - 解除の口を足すと `auth.repository`（と理由を足すなら `auth.domain`）に手が入り、K-7 のカバレッジの作業が付く。
- 状態: at-risk（K-1・K-2） ／ 読みの深さ: 深い（`domain` は10ファイル、`repository` のすべて、`service` の6ファイル、`web` の4ファイル）

### access

- 場所: `access/{domain,service,web}`（約 1,040 行、DB を読まない）
- 責務: `/api/admin` と `/api/admin/**` を管理者だけにする決まり（`domain/AdminPaths.java`・`web/AdminSecurityContributor.java` order 210）、`/api/**` の既定をログイン必須にする決まり（`web/AdminApiDefaultAccess.java`）、管理者の判定（`web/AdminAuthorizationManager.java`、K-4）、401・403 の応答と拒否の出来事 `AdminAccessDeniedEvent`、`GET /api/admin/check`。拒否の理由 `domain/AccessDeniedReason.java` は `TokenFailureReason` を網羅の `switch` で写す（K-1）。
- 依存: `auth`（domain・web）・`common-error`・`common-security`・`config`
- 状態: at-risk（停止の区分を足すと `AccessDeniedReason` に波及しうる。K-1・K-7） ／ 読みの深さ: 深い（`domain` の2ファイルと `web` の5ファイル。`service` はファイル名だけ）

### audit

- 場所: `audit/{domain,service,repository}`（約 1,590 行、Web の層なし）
- 責務: 各機能の出来事を受け、確定の後に `audit_events` に追記する（流れは `architecture.md` の Interaction Diagrams 3）。監査を見る画面・API は無い（`AuditBoundaryArchitectureTest` 125 行で Web の層を持たない決まり）。
- 依存: 出来事の型のため `auth`・`access`・`user`・`invitation`・`dslmanage` の domain
- K-6 監査の列は足りているが、管理の操作の種類と出来事の型は無い（確かめた事実）:
  - `audit_events` は `actor_user_id`（V6）と `target_user_id`・`target_invitation_id`（V7）を持ち、操作した人と対象の利用者を記録できる（パスワードの変更・招待の写し取りが使う、`domain/AuditEventFactory.java`）。
  - `domain/AuditEventType.java` の種類は `LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED`・DSL の5つ・`PASSWORD_CHANGED`・`INVITATION_ISSUED`・`INVITATION_RESENT`・`INVITATION_CANCELLED`・`REGISTRATION_COMPLETED`・`REGISTRATION_FAILED`。管理者の印の変更・利用停止・停止の解除・ロックの解除の種類は無い。
  - `domain/AuditFailureReason.java` は `USER_NOT_FOUND`・`PASSWORD_MISMATCH`・`ACCOUNT_LOCKED`・`NOT_ADMIN`・`TOKEN_*` の3つ・`CURRENT_PASSWORD_MISMATCH`・招待の4つ・`EMAIL_ALREADY_REGISTERED`。結果は `AuditResult`（`SUCCESS`・`FAILURE`）。
  - 列 `event_type` と `failure_reason` は `VARCHAR(32)`（V4）で、名前の長さに上限がある。
  - 記録は `service/AuditEventListener.java`（`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`、98 行）→ `service/AuditEventRecorder.java`（`REQUIRES_NEW`、51 行）。要求1件で接続を2本使う。
- K-6（見立て、未検証）: 管理の操作を足すと、出来事の型（持ち主の機能の `domain`）、`AuditEventType`（と理由を足すなら `AuditFailureReason`）、`AuditEventListener`・`AuditEvent`・`AuditEventFactory` の3ファイル、`AuditBoundaryArchitectureTest` の許す依存を直すことになる。列を足す移行は要らない見込み。`audit.repository` に手を入れると K-7 の作業が付く。
- 状態: at-risk（K-6） ／ 読みの深さ: 深い（`domain` の4ファイル（`AuditEventFactory` は 1〜260 行）、`repository` のすべて、`service` の3ファイル（`AuditEventListener` は 1〜200 行））

### user

- 場所: `user/{domain,service,repository,web}`（約 3,370 行）
- 責務: 利用者（表 `users`、V2・V7）、パスワード、氏名と表示の設定、自分の設定の API（`web/MeController.java`、`/api/me/**`）、起動時の初期管理者の作成（`service/InitialAdminInitializer.java`。INFO のキーは `maskedEmail` の伏せ字）。ほかの機能には `service/UserAccountService.java` の口だけを見せる（`auth` は `user.repository` を使えない、K-3）。
- 依存: `common-error`・`common-observability`
- 利用停止の状態の列が無いこと（K-1）と、管理者の印を変える口が無いこと（K-4）は `architecture.md`。一覧の問い合わせが無いことは `api-documentation.md`（K-5）。
- K-8 一覧の型と応答は、TRACE のログとメールアドレスの決まりに当たる（確かめた事実）:
  - `TraceAspect`（`common-observability`）は `service`・`repository` などの層の引数と戻り値を TRACE で文字列にして出す。そのため既存の型は `toString` でメールアドレスと氏名を伏せる（`service/UserSummary.java` 38 行・`service/NewUser.java`）か、`RedactedText` で受け渡す（`repository/UserRepository.java` の `existsByRedactedEmail`、`UserAccountService.findDisplayName`）。
  - `project.md` の Forbidden: メールアドレスをアプリのログとエラー応答に含めない（先頭の1文字＋`***`＋`@`＋ドメインの伏せ字 `EmailAddress.mask` は可、`project.md` の Corrections）。管理者に見せる一覧の応答にメールアドレスを載せる前例は、招待の一覧にある（`invitation/web/InvitationPageResponse.java` の `email`）。
  - 漏えいの確かめは `*SecretLeakIT`（`AuthSecretLeakIT`・`AccessSecretLeakIT`・`AuditSecretLeakIT`・`InvitationSecretLeakIT`・`MeSecretLeakIT` ほか）と `common/testsupport/JsonLogRecords` の形が使える（ファイル名だけ確かめた）。
- K-8（見立て、未検証）: 利用者の一覧の要約・ページの型も、`toString` で伏せるか `record` の外の伏せる型で持つ必要がある。`record` は `TraceAspect` の対象外だが、引数・戻り値として渡ると `toString` が出るため、`record` の `toString` を上書きする形（`UserSummary` と同じ）が前例になる。
- 状態: at-risk（K-1・K-4・K-5・K-8） ／ 読みの深さ: 深い（`domain` の2ファイル、`repository` のすべて、`service` の8ファイル。`web` はファイル名だけ）

### invitation

- 場所: `invitation/{domain,repository,service,web}`（約 4,340 行）
- 責務: 招待・送り直し・取り消し・一覧（`/api/admin/invitations`）、リンクの確かめと登録の完了（`/api/registration/**`、ログインなし）、期限の切れた招待の定期の削除。表は V8（`invitations`、`users` への外部キー2つ）。招待先のメールアドレスの利用者がいれば 409 `INVITATION_EMAIL_REGISTERED`（状態を見ない、K-1）。
- 管理の一覧と操作の見本（K-5、`api-documentation.md`）。`auth`・`audit` に依存できない境界（`InvitationBoundaryArchitectureTest` 50 行）のため、要求の文脈の読み取りを自前で持つ（`web/InvitationRequestContextResolver.java`）。
- 依存: `user`（domain と service の口）・`mail`・`common-error`・`common-security`・`common-web`・`common-observability`
- 状態: healthy ／ 読みの深さ: 深い（一部。`web` の4ファイル、`domain/InvitationPaging.java`、`service/InvitationBarrier.java`、`service/InvitationService.java` の一覧の部分だけ）

### mail

- 場所: `mail/{config,domain,service,template,transport}`（約 1,880 行）
- 責務: SMTP の送信と Mustache のテンプレート（エンジンは `java-mustache-processor`）。アプリの中のほかのパッケージを import しない。
- 状態: healthy ／ 読みの深さ: 流し読み

### appearance

- 場所: `appearance/{config,service,web}`（約 580 行）
- 責務: インスタンスの見た目の設定を `GET /api/appearance` で画面に渡す（差し込み口 order 410）。
- 依存: `common-security`
- 状態: healthy ／ 読みの深さ: 流し読み

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`
- 責務: 対象DB の接続とスキーマの読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み

### dsl

- 場所: `dsl/{domain,parse,validate,service}`
- 責務: DSL の型・安全な読み込み・検証・適用中のモデル。
- 状態: healthy ／ 読みの深さ: 流し読み

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`
- 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴（`/api/admin/dsl`）。要求の文脈の読み取り `web/DslRequestContextResolver.java` を持つ（ファイル名だけ）。
- 依存: `dsl`・`targetdb`・`user`（service の口）・`auth`（domain・web）・`common-error`・`common-i18n`・`common-web`
- 状態: healthy ／ 読みの深さ: 流し読み

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/`（318 ファイル）
- 責務: 機能ごとの `testsupport`（`auth/testsupport/MutableClock`・`AuthApi`・`AuthTestTokens`、`access/testsupport/AdminTestUsers`・`PublicApiTestRules`、`audit/testsupport/AuditRows`、`user/testsupport/TestUserAccounts`・`MeApi` ほか）と共通の `common/testsupport`（`LogEvents`・`JsonLogRecords`・`HttpTestClient`・`TestDatabase` ほか）、アプリ全体を起動する結合テスト。境界の検査（`ArchitectureTest`・`*BoundaryArchitectureTest`）の中身は `build-and-verify` と K-3。
- 状態: healthy ／ 読みの深さ: 流し読み（ファイル名の一覧だけ）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/pages/`・`app/login-handoff/`・`app/login-state/`・`shared/validation`・`shared/format`
- 責務: 画面の起動と骨組み、共通の画面（NotFoundPage など）、日時の書式、氏名とパスワードの確かめ。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-registry

- 場所: `app/registry/`・`app/navigation/`
- 責務: `features/<featureId>/registration.ts` を置くだけで機能を読み込む仕組みと、サイドバー・ユーザーメニューの項目。本文は K-9（`code-structure.md`）。
- 状態: healthy ／ 読みの深さ: 深い（`app/registry/types.ts`・`app/navigation/navigationItems.ts`・`features/README.md`）

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/routing/`
- 責務: AppShell の配置、表示言語、振り分け。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-display-settings

- 場所: `app/display-settings/`
- 責務: 利用者の表示の設定とインスタンスの見た目の設定を解決し、make-you-chic-ui の `ThemeProvider` に渡す。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`
- 責務: 同じオリジンの `/api/**` の呼び出し、トークンの付与、401 `AUTHENTICATION_REQUIRED` で1回だけ更新して送り直す、Problem Details の読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み（401・403 の検索だけ）

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログインの画面とログインの状態。`authSession.ts` がログインと更新の応答から `LoginState`（`admin` を含む）を作ってモジュールの変数に持つ（K-4）。
- 状態: at-risk（管理者の印の変更の後に古い値が残る。K-4） ／ 読みの深さ: 深い（`authSession.ts` だけ）

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理の入口の画面 `/admin`（`registration.ts` の order 200）。確認用 API の 403 を「ページが見つかりません」の表示にする（`adminAreaStatus.ts` 23〜27 行）。
- 状態: healthy ／ 読みの深さ: 深い（`registration.ts`。`adminAreaStatus.ts` は 403 の検索だけ）

### frontend-feature-dsl

- 場所: `features/dsl/`
- 責務: DSL の管理画面 `/admin/dsl`（order 210）。`useDslAdmin.ts` は 492 行。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-invitation

- 場所: `features/invitation/`
- 責務: 管理者の招待の画面 `/admin/invitations`（order 220）。make-you-chic-ui の `Table`・`Badge`・`Button`・`Modal`・`Toast`・`Alert` を使う、利用者の管理の画面の見本（K-9）。状態の管理は `useInvitationAdmin.ts`（472 行）。
- 状態: healthy ／ 読みの深さ: 深い（一部。`registration.ts` と `api/invitationApi.ts` の 1〜80 行。`InvitationList.tsx` は冒頭と `import` だけ）

### frontend-feature-registration

- 場所: `features/registration/`
- 責務: 招待を受けた人のリンクの確かめと登録の完了の画面（ログインなし）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-preferences

- 場所: `features/preferences/`
- 責務: 自分の設定とパスワードの変更の画面（ユーザーメニューの order 80・90）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-e2e

- 場所: `frontend/e2e/`（`*.e2e.ts` 10 本（010〜100）と `support/` の補助 13 ファイル）
- 責務: Playwright の E2E（`./gradlew e2eTest`）。`030-admin-access` が管理の入口、`060-invitation-accessibility` が招待の管理の画面の axe、`090-invitation-registration-flow` が代表の流れ、`100-app-text-contrast` が文字のコントラスト（既知の違反の一覧は空）。`verify` と CI の外。補助 `support/adminLogin.ts`・`invitationSeed.ts`・`registeredUser.ts` が管理者のログインと利用者の用意に使える。
- 状態: healthy ／ 読みの深さ: 流し読み（一覧と一部の冒頭だけ）

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `077f5b48ce84cd020ecec2d925836a085f9d9e11`、`git submodule status` で確かめた）。画面からは `file:` の依存で使う。
- 責務: デザインシステム。部品は Alert・AppShell・Avatar・Badge・Button・Card・Checkbox・Dropdown・FormField・Icon・Modal・RadioGroup・Select・Switch・Table・Tabs・Textarea・TextInput・Toast・Tooltip。中身はこのリポジトリから変えない（`project.md` の Forbidden）。
- 状態: healthy ／ 読みの深さ: 流し読み（固定先と部品のディレクトリの一覧だけ）

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、固定先 `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`、`0.1.0`）。Gradle の composite build で `cherry-mustache-core` として使う。
- 責務: メールのテンプレートの Mustache のエンジン。今回の Intent では触れない見込み。
- 状態: healthy ／ 読みの深さ: 流し読み（固定先だけ）

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`backend/config/spotbugs-exclude.xml`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vitest.config.ts`・`config/npm-build-tools.txt`・`.github/workflows/ci.yml`・`.github/dependabot.yml`、境界の検査 `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java` と `*BoundaryArchitectureTest`
- 責務: 1コマンドの検査 `./gradlew verify`（`architecture.md` の Interaction Diagrams 4）、E2E の `./gradlew e2eTest`、WAR の組み立て（画面の `dist` を同梱）、依存の取得元を Maven Central だけにする `RepositoriesMode.FAIL_ON_PROJECT_REPOS` と composite build（`settings.gradle.kts`）、lockfile による版の固定、テストの分け方（`test` と `integrationTest`）、カバレッジの下限（JaCoCo の全体とパッケージごと、`packagesJudgedByTotal`、K-7）、SpotBugs の関門 `spotbugsGate`（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`、除外は理由つきの1件）、Gitleaks・OSV-Scanner、Dependabot（gradle・npm・github-actions・docker）。
- 状態: at-risk（K-7） ／ 読みの深さ: 深い（`backend/build.gradle.kts`・`spotbugs-exclude.xml`・`libs.versions.toml`・`settings.gradle.kts`・`dependabot.yml`・`frontend/package.json`・`vitest.config.ts` の下限の行と、境界の検査の決まりの名前と対象。ルートの `build.gradle.kts` と `ci.yml` は検索だけ）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`
- 責務: WAR をコピーするだけのイメージ（`eclipse-temurin:25.0.4_7-jre-noble`）、`app` のサービスと、profile で起動する監視・メールの受け手・見本の対象DB。
- 状態: healthy ／ 読みの深さ: 流し読み（イメージの行だけ）

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験と使い捨ての環境、手元の監視（警報とダッシュボード）。
- 状態: healthy ／ 読みの深さ: 流し読み（今回は読んでいない。名前だけ）
