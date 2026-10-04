# 部品の一覧（mastersmith2）

## 読み方

- 見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回の 38 個に、今回（Intent `261004-safety-carryover`）`common-persistence` を足して 39 個になった（名前の変更は無い）。
- 状態: healthy（問題なし）・at-risk（今の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ:
  - 「深い（一部）」は今回の走査（コミット `47ec27b`、深さ Minimal）で深く読んだファイルを持つ部品で、`analyzed.components` の 13 個。読んだのは今回の論点に関わるファイル（と範囲）だけで、部品の全体ではない。読んだファイルは各部品に書いた。
  - 「流し読み」はディレクトリとファイルの名前・検索だけで、責務の文はその範囲と前回までの記録による。
- K の番号は `business-overview.md` の所見の一覧を指す。この文書に本文を書いたのは K-29（`frontend-feature-useradmin`）だけで、ほかは持ち主の文書を参照する。
- パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省いて書く。依存の向きは `dependencies.md`。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて `backend/src/main/resources/application.yaml`・`logback-spring.xml`）
- 責務: Spring Security の連鎖（`config/SecurityConfig.java`。各機能の差し込み口 `SecurityRuleContributor` を order 順に当てる）、SPA の配信と観測の設定、アプリの設定値。
- 今回読んだもの: `application.yaml` の初期管理者・bcrypt・接続プール・内部DB の範囲（初期管理者の設定の鍵、bcrypt の cost の既定 12、`maximum-pool-size` の既定 30、`connection-timeout` 5000 ms）と、`logback-spring.xml` の MDC の項目（`traceId`・`spanId` だけを各行に出す。K-27）。`config/` の Java のクラスは読んでいない。
- 状態: healthy ／ 読みの深さ: 深い（一部。設定のファイル2つの上の範囲だけ）

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: `ProblemType`（code・状態コード・日英の文言）、`BusinessException`、`@RestControllerAdvice` による Problem Details（`web/GlobalExceptionHandler.java`）、Spring MVC の外の例外の受け口 `/error`（`web/ErrorPathController.java`）、`GET /api/problems/{slug}`。
- 今回読んだもの: `GlobalExceptionHandler.java` の例外の受け口とログ（4xx は WARN「要求をエラー応答に変換しました」、5xx は ERROR「想定外のエラーが起きました」）、`ErrorPathController.java`。L3 のログ（K-27）と、二重の ERROR の経路（K-28）の持ち主。本文は `architecture.md` の Interaction Diagrams 3・4。
- `common.error.domain`・`common.error.service` は `packagesJudgedByTotal` に残る（K-30）。`common.error.web` は一覧の外。
- 状態: at-risk（K-28） ／ 読みの深さ: 深い（一部。`web` の2ファイル）

### common-persistence

- 場所: `common/persistence/`（`RowLockAttempt.java`・`RowLockFailures.java`・`RowLockUnavailableException.java`・`package-info.java`。Intent `260930-user-admin` で足された）
- 責務: 行の排他の結果の型 `RowLockAttempt`（`Acquired`・`Busy` の sealed interface。取れなかったときの元の例外を持たない。連なりの文に行の値が入りうるため）と、排他の失敗（待ちの上限切れ・行き詰まり）の見分け `RowLockFailures.isLockFailure` とその WARN「行の排他を取れませんでした」（キー `lockKind`・`exceptionClass`）。K-27 の L4 の持ち主（本文は `architecture.md` の Interaction Diagrams 3）。
- 依存: アプリの中のほかのパッケージを import しない（JPA の例外と SLF4J だけ）。使う側は `dependencies.md`。
- 状態: healthy（今回の確かめの対象。`packagesJudgedByTotal` の外） ／ 読みの深さ: 深い（一部。`RowLockAttempt.java`・`RowLockFailures.java`）

### common-security

- 場所: `common/security/`
- 責務: 秘密の値の伏せ字（`RedactedText` など）、安全の決まりの差し込み口の型 `SecurityRuleContributor`。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-web

- 場所: `common/web/`・`common/paging/`（`Paging.java` の1ファイル。小さいため独立の ID にせずここに寄せた）
- 責務: 本文の大きさの上限、Web の設定値（`mastersmith.web.base-url` ほか）、一覧のページ送りの共通の計算（`common/paging`、`useradmin`・`invitation` が使う）。
- `common.web` は `packagesJudgedByTotal` に残る（K-30）。
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
- 責務: `TraceAspect`（`web`・`service`・`domain`・`repository` の層のメソッドの引数と戻り値を、ロガーが TRACE のときに文字列にして出す）、行の排他の失敗の文を伏せる `LockFailureSafeTraceInterceptor`、外部エクスポートの出口の伏せ字ほか（前回までの記録）。
- 今回は `common/observability/` のファイルを読んでいない（ログの MDC の項目は `config` の `logback-spring.xml` で読んだ）。K-25 の `InitialAdminProperties.toString()` の扱いは `TraceAspect` の対象に関わる（`architecture.md` の Interaction Diagrams 1）。
- 状態: healthy ／ 読みの深さ: 流し読み

### auth

- 場所: `auth/{domain,service,repository,web}`
- 責務: ログインとアカウントロック、アクセストークン（HS256 の JWT）とリフレッシュトークン、ログアウト、要求ごとのアクセストークンの認証（`web/AccessTokenAuthenticationProvider.java`。利用者を DB から読み直し、停止中を拒否する）、利用者の作成の知らせを受けてロックの状態の行を作る（`service/LoginAttemptStateInitializer.java`）、管理者によるロックの解除（`service/LockAdministrationService.java`、ファイル名だけ）。
- 今回読んだもの: `service/LoginService.java`（ログインの流れと停止の判定、K-26）、`service/LoginAttemptStateInitializer.java`（`UserCreatedEvent` の唯一の受け手、K-25）、`web/AccessTokenAuthenticationProvider.java`（フィルターの中の `findById`、K-28）、`service/TokenRefreshService.java`（停止の判定の範囲）。`domain`・`repository` は `LoginAttemptStateRepository` の排他の時間の定数（3000 ms）を検索で見ただけ。
- 依存: `user`（service の口と domain）・`common-error`・`common-security`・`common-observability`・`common-persistence`
- 状態: at-risk（K-26 は確かめだけ、K-28 は直す見込み） ／ 読みの深さ: 深い（一部。上の4ファイル）

### access

- 場所: `access/{domain,service,web}`
- 責務: `/api/admin/**` を管理者だけにする決まり、`/api/**` の既定をログイン必須にする決まり、管理者の判定、401・403 の応答と拒否の出来事、`GET /api/admin/check`（前回までの記録）。
- `access.service` は `packagesJudgedByTotal` に残る（K-30）。
- 状態: healthy ／ 読みの深さ: 流し読み

### audit

- 場所: `audit/{domain,service,repository}`（Web の層なし）
- 責務: 各機能の出来事を受け、確定の後に `audit_events` に追記する（`service/AuditEventListener.java` の `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` → `service/AuditEventRecorder.java` の `REQUIRES_NEW`）。書き込みの失敗は受け止めて ERROR を1回出し、呼び出し元に伝えない。監査を見る画面・API は無い。
- 今回読んだもの: `domain/AuditEvent.java`（構築子の `requireNonNull`、`source_ip` の受け渡し）、`domain/AuditEventType.java`（20 種類）、`domain/AuditEventFactory.java`（`from(...)` の一覧の範囲）、`service/AuditEventListener.java`、`service/AuditEventRecorder.java`、`backend/src/main/resources/db/migration/V4__u4_audit_event.sql`（`event_type VARCHAR(32)`・`source_ip VARCHAR(45) NOT NULL`）。初期管理者の作成・救済の種類が無いこと（K-25）の持ち主の一つ（本文は `architecture.md` の Interaction Diagrams 1）。
- 依存: 出来事の型のため `auth`・`access`・`user`・`invitation`・`dslmanage`・`useradmin` の domain（前回までの記録と、今回の import の検索）
- `audit.repository` は `packagesJudgedByTotal` に残る（K-30）。監査の種類を足すだけなら `audit.domain`・`audit.service`（一覧の外）に収まる見込み。
- 状態: at-risk（K-25） ／ 読みの深さ: 深い（一部。上の6ファイル）

### user

- 場所: `user/{domain,service,repository,web}`
- 責務: 利用者（表 `users`、V2・V7・V9）、パスワード、氏名と表示の設定、自分の設定の API（`/api/me/**`）、起動時の初期管理者の作成（`service/InitialAdminInitializer.java`）。ほかの機能には `service/UserAccountService.java` の口だけを見せる。
- 今回読んだもの: `service/InitialAdminInitializer.java`・`InitialAdminProperties.java`・`UserCreatedEvent.java`・`UserSummary.java`・`UserAccountConfig.java`・`PasswordProperties.java`、`service/UserAccountService.java`（`verifyPassword`・`createUser`・`existsByEmail`・`lockAdminRowsInIdOrder`・`lockUserRow` の範囲）、`repository/UserRowLockRepository.java`（`PESSIMISTIC_WRITE`、3000 ms）、`repository/UserRepository.java`（`findByEmail`・`existsByRedactedEmail` の範囲）。K-25（作成の判定と `toString`）・K-26（停止の値の読み取り）・K-27（行の排他）に関わる。
- 依存: `common-error`・`common-observability`・`common-persistence`
- `user.*` は `packagesJudgedByTotal` の外（すでにパッケージごとの下限の対象）。
- 状態: at-risk（K-25） ／ 読みの深さ: 深い（一部。上の9ファイル）

### invitation

- 場所: `invitation/{domain,lock,repository,service,web}`
- 責務: 招待・送り直し・取り消し・一覧（`/api/admin/invitations`）、リンクの確かめと登録の完了（`/api/registration/**`、ログインなし）、期限の切れた招待の定期の削除（前回までの記録）。
- 状態: healthy ／ 読みの深さ: 流し読み

### useradmin

- 場所: `useradmin/{domain,service,web}`
- 責務: 利用者の管理の API（`/api/admin/users` の一覧と、氏名・言語の変更・管理者の印の付け外し・利用停止と再開・ログインの失敗回数の取り消し）。業務処理は結果の型（`service/OperationResult.java` ほか）を返し、controller が業務エラーに変える。監査の出来事 `domain/UserAdminAuditEvent.java` を知らせる。
- 今回読んだもの: `service/UserAdminService.java`（`Busy` の作り方と巻き戻しの印の範囲）、`service/UserAdminBarrier.java`（本番は `NoOpUserAdminBarrier`）、`web/UserAdminController.java`（`Busy` から 409 `USER_ADMIN_BUSY` への変換の範囲）、テスト `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyApiIT.java`。K-27 の持ち主の一つ（本文は `architecture.md` の Interaction Diagrams 3）。
- 依存: `user`（domain・service）・`auth`（domain・service）・`access.domain`・`common`（`error`・`observability`・`paging`・`persistence`）。外から import するのは `audit` だけ（前回の import の検索）。
- 状態: at-risk（K-27 の確かめ。直しはテストだけの見込み） ／ 読みの深さ: 深い（一部。上の3ファイルとテスト1つ）

### mail

- 場所: `mail/{config,domain,service,template,transport}`
- 責務: SMTP の送信と Mustache のテンプレート（エンジンは `java-mustache-processor`）。
- 状態: healthy ／ 読みの深さ: 流し読み

### appearance

- 場所: `appearance/{config,service,web}`
- 責務: インスタンスの見た目の設定を `GET /api/appearance` で画面に渡す。
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
- 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴（`/api/admin/dsl`）。
- 状態: healthy ／ 読みの深さ: 流し読み

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/`（`*Test` 173 件・`*IT` 133 件、`*SecretLeakIT` 12 件。開発担当がファイルの名前で数えた）
- 責務: 機能ごとの `testsupport` と共通の `common/testsupport`（`JsonLogRecords`・`RowLockHolder`・`TestDatabase` ほか、名前だけ）、アプリ全体を起動する結合テスト、境界の検査、対象DB のイメージの固定（`targetdb/testsupport/TargetDbImages.java`）。
- 今回読んだもの: `targetdb/testsupport/TargetDbImages.java`（版の定数とダイジェストの定数。Testcontainers は `イメージ名@ダイジェスト` だけで起動し、版の定数は使っていない。K-31）、`user/service/InitialAdminIT.java`（テストの名前の範囲）。
- 状態: at-risk（K-31） ／ 読みの深さ: 深い（一部。上の2ファイル）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/pages/`・`app/login-handoff/`・`app/login-state/`・`shared/validation`・`shared/format`
- 責務: 画面の起動と骨組み、共通の画面、日時の書式、入力の確かめ。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-registry

- 場所: `app/registry/`・`app/navigation/`
- 責務: `features/<featureId>/registration.ts` を置くだけで機能を読み込む仕組みと、サイドバー・ユーザーメニューの項目。
- 状態: healthy ／ 読みの深さ: 流し読み

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
- 責務: 同じオリジンの `/api/**` の呼び出し、トークンの付与、401 で1回だけ更新して送り直す、Problem Details の読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログインの画面とログインの状態。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理の入口の画面 `/admin`。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-dsl

- 場所: `features/dsl/`
- 責務: DSL の管理画面 `/admin/dsl`。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-invitation

- 場所: `features/invitation/`
- 責務: 管理者の招待の画面 `/admin/invitations`。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-useradmin

- 場所: `features/useradmin/`
- 責務: 利用者の管理の画面（一覧・検索・行の「操作」・確かめの表示・氏名と言語の入力 `EditProfileDialog.tsx`）。
- 今回読んだもの: `EditProfileDialog.tsx`。
- K-29 言語の欄で Enter で送信すると、押せなくした選択肢からフォーカスが外れる（確かめた事実）:
  - `EditProfileDialog.tsx` は送信中（`state.status === 'submitting'`）に氏名の欄を `readOnly={submitting}`（フォーカスは残る）にし、言語の `RadioGroup` を `disabled={submitting}`（157〜158 行。前の Intent の FR3.1）にする。フォームは `onSubmit`（118 行）で送るため、言語の選択肢にフォーカスがある状態で Enter を押すと送信になり、その選択肢自身が押せなくなる。
  - make-you-chic-ui の `RadioGroup`（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/RadioGroup/RadioGroup.tsx`）の props は `name`・`options`・`value`・`defaultValue`・`onChange`・`disabled`・`className`・`style`・`legend` だけで、`readOnly` に当たる口は無い。`disabled` は各 `Radio` にそのまま渡る。
  - `vendor/make-you-chic-ui` はこのリポジトリから直接変えられない（`project.md` の Forbidden）。変えるなら上流で直し、固定先の更新を承認を得た専用のコミットで行う（`project.md` の Mandated、`team.md` の Way of Working）。
- K-29 見立て（未検証）:
  - ブラウザは押せなくなった要素にフォーカスを残さないため、フォーカスは `body` に落ちる。直し方の候補は、(a) 送信中も `disabled` にせず `onChange` で値の変更を受け付けない（見た目で押せないことが伝わらない）、(b) 送信を始めるときに保存のボタンなど押せるままの要素へフォーカスを移す、(c) make-you-chic-ui に読み取り専用の口を足してもらう（依頼者への相談が要る）。
  - 同じ形（送信中に `disabled` にする選択肢の欄）がほかの画面（プリファレンス・登録の完了など）にもあるかは、今回は確かめていない。
- 状態: at-risk（K-29） ／ 読みの深さ: 深い（一部。`EditProfileDialog.tsx` だけ）

### frontend-feature-registration

- 場所: `features/registration/`
- 責務: 招待を受けた人のリンクの確かめと登録の完了の画面（ログインなし）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-preferences

- 場所: `features/preferences/`
- 責務: 自分の設定とパスワードの変更の画面。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-e2e

- 場所: `frontend/e2e/`（`*.e2e.ts` 13 本。開発担当が名前で数えた）
- 責務: Playwright の E2E（`./gradlew e2eTest`）。`verify` と CI の外。
- 状態: healthy ／ 読みの深さ: 流し読み

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール。画面からは npm の `file:` の依存で使う）。今回は固定先のコミットを確かめていない。
- 責務: デザインシステム（Alert・AppShell・Button・Dropdown・Modal・RadioGroup・Table・TextInput・Toast ほか）。中身はこのリポジトリから変えない（`project.md` の Forbidden）。
- 今回読んだもの: `packages/make-you-chic-ui/src/components/RadioGroup/RadioGroup.tsx`（K-29、本文は `frontend-feature-useradmin`）。
- 状態: at-risk（K-29 の直しで上流の変更が要る場合） ／ 読みの深さ: 深い（一部。`RadioGroup.tsx` だけ）

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、`0.1.0`）。Gradle の composite build で使う。
- 責務: メールのテンプレートの Mustache のエンジン。今回の Intent では触れない見込み。
- 状態: healthy ／ 読みの深さ: 流し読み

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`backend/config/spotbugs-exclude.xml`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vitest.config.ts`・`config/`・`.github/workflows/ci.yml`・`.github/dependabot.yml`
- 責務: 1コマンドの検査 `./gradlew verify`、E2E の `./gradlew e2eTest`、WAR の組み立て、依存の取得元の固定と composite build、lockfile、カバレッジの下限（JaCoCo の全体とパッケージごと、`packagesJudgedByTotal`）、SpotBugs の関門、Gitleaks・OSV-Scanner、Dependabot。
- 今回読んだもの: `backend/build.gradle.kts`（`packagesJudgedByTotal` とカバレッジの検証の範囲、K-30。本文は `code-quality-assessment.md`）、`gradle/libs.versions.toml`（`[versions]` の範囲）、`.github/dependabot.yml`（K-31、本文は `dependencies.md`）。
- 状態: at-risk（K-30・K-31） ／ 読みの深さ: 深い（一部。上の3ファイル）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`・`README.md`（起動と設定の手順）
- 責務: WAR をコピーするだけのイメージ（`FROM eclipse-temurin:25.0.4_7-jre-noble`、ダイジェストなし）、`app` のサービスと、profile で起動する監視（`otel/opentelemetry-collector`・`grafana/otel-lgtm`、ダイジェストなし）・メールの受け手（Mailpit）・見本の対象DB（PostgreSQL・MySQL・MariaDB、版とダイジェストで固定）。
- 今回読んだもの: `Dockerfile`、`compose.yaml`（イメージの行）、`.env.example`（初期管理者の項目の名前だけ。値は空）、`README.md`（初期管理者の記述の範囲）。K-31 の持ち主の一つ（本文は `dependencies.md`）。
- 状態: at-risk（K-31） ／ 読みの深さ: 深い（一部。上の4ファイルの上の範囲）

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験（`perf/k6/scenarios.js`、場面を `SCENARIO` で選ぶ）と使い捨ての環境（`docker/perf/compose.yaml`）、手順（`perf/README.md`）、手元の監視の警報とダッシュボード（`docker/monitoring/`）、接続プールの運用の道具（`docker/hikari-pool.sh`・`docker/jmx`）。
- 今回読んだもの: `perf/k6/scenarios.js`（場面の一覧と利用者の管理の場面の定義、K-26・K-27）、`docker/perf/compose.yaml`（イメージの行、K-31）。
- 状態: at-risk（K-26 の切り分けの試験・K-31） ／ 読みの深さ: 深い（一部。上の2ファイル）
