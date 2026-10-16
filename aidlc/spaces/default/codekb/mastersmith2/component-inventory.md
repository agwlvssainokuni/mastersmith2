# 部品の一覧（mastersmith2）

## 読み方

- 見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回までの 47 個をそのまま使い、新しい ID は足していない（業務データの機能はまだコードに無いため）。
- 2026-10-16（記録のコミット `09b795d`）の Full rescan で作り直した。前回までの K-1〜K-48 の本文はここに残していない。
- 状態: healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ:
  - 「深い（一部）」は今回深く読んだファイルを持つ部品で、`analyzed.components` の 16 個。読んだのは今回の Intent に関わるファイルだけで、部品の全体ではない。読んだファイルは各部品の「今回読んだもの」に書いた。
  - 「流し読み」はディレクトリとファイルの名前・grep・節の読みだけで、責務の文はその範囲と前回までの記録による。
- この文書に本文を書いた所見は K-55（`frontend-feature-useradmin`）と K-56（`make-you-chic-ui`）。ほかの K の本文の場所は `business-overview.md` の一覧。
- パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省いて書く。依存の向きは `dependencies.md`。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（6 ファイル。あわせて `backend/src/main/resources/application.yaml`・`logback-spring.xml`）
- 責務: Spring Security の連鎖（`SecurityConfig.java`）、SPA の配信、観測、アプリの設定値。
- 今回の範囲: `application.yaml` の `mastersmith.target-db` の節だけ読んだ（読み取りの権限だけのアカウントを勧めるコメント。K-49）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: `ProblemType`（code・状態コード・日英の文言）、`BusinessException`、`@RestControllerAdvice` による Problem Details、`/error`、`GET /api/problems/{slug}`。
- `common.error.domain`・`common.error.service` は `packagesJudgedByTotal` に残る。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-persistence

- 場所: `common/persistence/`
- 責務: 行の排他の結果の型と排他の失敗の見分け（前回までの記録）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-security

- 場所: `common/security/`（`ApiAccess.java`・`ApiAccessLevel.java`・`ApiDefaultAccess.java`・`ErrorResponseWriter.java`・`SecurityExtensionValidator.java`・`SecurityRuleContributor.java`）
- 責務: API の分類の印 `@ApiAccess`（`PUBLIC`・`AUTHENTICATED`・`ADMIN`）、安全の決まりの差し込み口（order つき、機能の名前で 100 台ずつ）、`/api/**` の既定の口、差し込みの検査（order の重なりで起動を失敗させる）、エラー応答の書き出し。
- 今回読んだもの: 上の6ファイル。K-61 の持ち主の1つ（本文は `api-documentation.md`）。
- 状態: at-risk（業務データの API を足すときに当たる。K-61） ／ 読みの深さ: 深い（一部）

### common-web

- 場所: `common/web/`・`common/paging/`
- 責務: 本文の大きさの上限、Web の設定値、一覧のページ送りの共通の計算。
- `common.web` は `packagesJudgedByTotal` に残る。業務データの一覧のページ送りで使う見込み（未確認）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-health

- 場所: `common/health/`
- 責務: ヘルスチェック。`packagesJudgedByTotal` に残る。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-i18n

- 場所: `common/i18n/`
- 責務: 表示言語の解決（日英）。`common.i18n.domain` は `packagesJudgedByTotal` に残る。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-observability

- 場所: `common/observability/`
- 責務: `TraceAspect`（`web`・`service`・`domain`・`repository` の引数と戻り値を TRACE のときに文字列にして出す）ほか。業務データの値・主キーの値を受け渡す型は、個人に関する値を含みうるなら `toString` で伏せる必要がある（`team.md` の Code Style）。
- 状態: healthy ／ 読みの深さ: 流し読み

### auth

- 場所: `auth/{domain,repository,service,web}`（58 ファイル）
- 責務: ログインとアカウントロック、アクセストークンとリフレッシュトークン、ログアウト、要求ごとのアクセストークンの認証と主体（`AuthenticatedUser`、`admin()` を含む）の組み立て、初期管理者の救済の受け手。
- 今回の範囲: `admin_flag`・`adminFlag`・`.admin()` の grep の行だけ（`web/AccessTokenAuthenticationProvider.java` 67 行、`web/CurrentUserResponse.java`。K-53、本文は `code-quality-assessment.md`）。
- 依存: `common`・`user`
- 状態: at-risk（K-53） ／ 読みの深さ: 流し読み

### access

- 場所: `access/{domain,service,web}`（20 ファイル）
- 責務: `/api/admin/**` を管理者だけにする決まり、管理者の判定、`/api/**` の既定をログイン必須にする決まり、401・403 の応答と拒否の出来事、`GET /api/admin/check`。
- 今回読んだもの: `web/AdminAuthorizationManager.java`（主体の `admin()` だけを見る）・`web/AdminSecurityContributor.java`・`web/AdminApiDefaultAccess.java`、`domain/AdminPaths.java`・`domain/AccessDeniedReason.java`。K-53・K-61 に関わる。
- 依存: `auth`・`common`・`config`。境界テスト `AccessBoundaryArchitectureTest` がある。`access.service` は `packagesJudgedByTotal` に残る。
- 状態: at-risk（K-53） ／ 読みの深さ: 深い（一部）

### audit

- 場所: `audit/{domain,repository,service}`（15 ファイル、Web の層なし）
- 責務: 各機能の出来事を受け、確定の後に `audit_events` に追記する。監査を見る画面・API は無い。
- 今回読んだもの: `domain/AuditEventType.java`（34 個。業務データの操作の種類は無い）。`service/AuditEventListener.java`（import と受け取りの行）と `service/AuditEventRecorder.java`（`REQUIRES_NEW` の行）は一部だけ。監査の `detail` の列の上限は `backend/src/main/resources/db/migration/V10__u3_group.sql`。K-54 の持ち主（本文は `architecture.md`）、K-57 にも関わる。
- 依存: `access`・`auth`・`dslmanage`・`group`・`invitation`・`role`・`user`・`useradmin`（出来事の型）。`audit.repository` は `packagesJudgedByTotal` に残る。
- 状態: at-risk（K-54） ／ 読みの深さ: 深い（一部）

### user

- 場所: `user/{domain,repository,service,web}`（70 ファイル）
- 責務: 利用者（表 `users`。管理者の印 `admin_flag` と停止の列）、パスワード、氏名と表示の設定、自分の設定の API（`/api/me`）、初期管理者の作成と救済、最後の管理者の保護の行の排他。
- 今回の範囲: `admin_flag`・`adminFlag` の grep の行だけ（K-53）。
- 依存: `common`
- 状態: at-risk（K-53） ／ 読みの深さ: 流し読み

### invitation

- 場所: `invitation/{domain,lock,repository,service,web}`（65 ファイル）
- 責務: 招待・送り直し・取り消し・一覧（`/api/admin/invitations`）、リンクの確かめと登録の完了（`/api/registration`、ログインなし）。
- 状態: healthy ／ 読みの深さ: 流し読み

### useradmin

- 場所: `useradmin/{domain,service,web}`（27 ファイル）
- 責務: 利用者の管理の API（`/api/admin/users`。一覧・氏名と言語・管理者の印の付け外し・停止と再開・失敗回数の取り消し）。
- 今回の範囲: `service/UserAdminService.java` の `admin_flag` の grep の行だけ（354 行。K-53）。
- 依存: `access`・`auth`・`common`・`user`
- 状態: at-risk（K-53） ／ 読みの深さ: 流し読み

### mail

- 場所: `mail/{config,domain,service,template,transport}`（28 ファイル）
- 責務: SMTP の送信と Mustache のテンプレート（エンジンは `java-mustache-processor`）。
- 状態: healthy ／ 読みの深さ: 流し読み

### appearance

- 場所: `appearance/{config,service,web}`（14 ファイル）
- 責務: インスタンスの見た目の設定を `GET /api/appearance` で画面に渡す。
- 状態: healthy ／ 読みの深さ: 流し読み

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`（26 ファイル、web なし）
- 責務: 対象DB の読み取り専用の接続（`targetDataSource`、既定の候補にしない、上限 5）と、スキーマの読み取り（`TargetSchemaReader.readSchema(ReadPurpose)`）。識別子の引用の入り口 `SqlIdentifiers`（今は使わない）。
- 今回読んだもの: `config/TargetDataSourceConfig.java`・`config/TargetDbProperties.java`、`domain/ReadPurpose.java`・`domain/SqlIdentifiers.java`、`service/JdbcTargetSchemaReader.java`、テスト `backend/src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java`。K-49 の持ち主（本文は `architecture.md`）、K-51 に関わる。
- 依存: なし（ほかの機能を import しない）
- 状態: at-risk（K-49） ／ 読みの深さ: 深い（一部）

### dsl

- 場所: `dsl/{domain,parse,service,validate}`（64 ファイル。あわせて `backend/src/main/resources/dsl/`）
- 責務: DSL の型・安全な読み込み・検証・適用中のモデルの保持と提供口 `ActiveDslModelProvider`。
- 今回読んだもの: `domain/` の 21 ファイル（`DslModel`・`DslSchema`・`DslTable`・`DslColumn`・`DbType`・`FormPart`・`SearchSetting`・`SearchOperator`・`ListSetting`・`ListFormat`・`DetailSetting`・`OptionSource`・`OptionSourceKind`・`Validation`・`DslForeignKey`・`LookupListItem`・`LookupSearchItem`・`DslMenuItem`・`TableRef`・`DslFormat`・`ActiveDsl`）と `service/ActiveDslModelProvider.java`。`dsl-schema-v2.json` は `primaryKey`・`view` の行だけ。K-51 の持ち主（本文は `api-documentation.md`）。
- 依存: なし
- 状態: at-risk（K-51。一覧・詳細の画面が初めて使う） ／ 読みの深さ: 深い（一部）

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`（54 ファイル）
- 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴（`/api/admin/dsl`）。対象DB のスキーマを `targetdb` で読む。
- 状態: healthy ／ 読みの深さ: 流し読み

### role

- 場所: `role/{domain,repository,service,store,transfer,web}`（143 ファイル）
- 責務: ロールと権限の設定、利用者・グループへの割り当て、作業ロール（`/api/me/work-role`）、自分の権限（`/api/me/permissions`）、実効の権限の解決（`EffectivePermissionResolver`）、権限の YAML の書き出しと取り込み。
- 今回読んだもの: `domain/` の `MainPermission`・`PermissionLevel`・`PermissionValues`・`EffectivePermission`・`EffectiveNode`・`PermissionTarget`・`PermissionInheritance`・`PermissionSnapshot`、`service/EffectivePermissionResolver.java`・`EffectivePermissionResolverImpl.java`、移行 `V11__u4_role.sql`・`V12__u4_role_assignment.sql`、テスト `backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java`。`service/RoleAdminService.java` と `RoleBoundaryArchitectureTest` は grep の行だけ。K-50 の持ち主（本文は `architecture.md`）、K-57 の持ち主（本文は `code-quality-assessment.md`）。
- 依存: `common`・`dsl`・`group`・`user`。依存されてよいのは `audit` と `navigation` だけ。
- 状態: at-risk（K-50・K-57） ／ 読みの深さ: 深い（一部）

### group

- 場所: `group/{domain,repository,service,store,web}`（55 ファイル）
- 責務: グループの管理（`/api/admin/groups`）と行の排他。
- 状態: healthy ／ 読みの深さ: 流し読み（移行 `V10__u3_group.sql` は監査の `detail` の列のために読んだ）

### navigation

- 場所: `navigation/{domain,service,web}`（23 ファイル、repository なし）
- 責務: 適用中の DSL のメニューから、作業ロールの権限で枝を落とした表示の木を作り画面に渡す（`GET /api/me/navigation`）。テーブルの置き場の問い合わせ（`GET /api/me/table-access`）。
- 今回読んだもの: `domain/` の `MenuFilter`・`NavNode`・`NavTableRef`・`TableAccessResult`・`EmptyReason`、`service/NavigationService.java`、`web/NavigationController.java`・`TableAccessResponse.java`・`NavTableRefResponse.java`。`NavigationBoundaryArchitectureTest` は先頭の規則だけ。K-50・K-52・K-61 に関わる。
- 依存: `auth`・`common`・`dsl`・`role`。どの機能も `navigation` に依存しない。
- 状態: at-risk（K-50。置き場の API を広げるか新しい機能に分けるかの論点） ／ 読みの深さ: 深い（一部）

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/`（586 ファイル、リソースを含む）
- 責務: 機能ごとの `testsupport` と共通の `common/testsupport`、アプリ全体を起動する結合テスト、層と境界の検査（18 個）、API の分類の網羅（`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`・`PublicApiInventoryTest`）。
- 今回の範囲: 境界テストの一覧とファイルの有無だけ（境界テストの中身は `targetdb`・`role`・`navigation` の部品の項に書いた）。
- 状態: healthy ／ 読みの深さ: 流し読み

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/pages/`・`app/admin-forbidden/`・`app/login-handoff/`・`app/login-state/`・`app/testing/`・`shared/validation`・`shared/format`・`shared/modal`・`shared/tree`・`shared/slow-notice`
- 責務: 画面の起動と骨組み、共通の画面（ホーム・403 の画面）、管理の API の 403 を受けて 403 の画面に切り替える `AdminForbiddenProvider`、日時の書式、入力の確かめ、共有の木。
- 今回読んだもの: `app/admin-forbidden/AdminForbiddenProvider.test.tsx`。K-58 の持ち主（本文は `code-quality-assessment.md`）。
- 状態: at-risk（K-58） ／ 読みの深さ: 深い（一部）

### frontend-registry

- 場所: `app/registry/`・`app/navigation/`
- 責務: `features/<featureId>/registration.ts` を置くだけで機能を読み込む仕組み、差し込み口の型（`AccessLevel` は `PUBLIC`・`LOGGED_IN`・`ADMIN`、`LoginState.admin`）、サイドバーの区画の組み立て（`buildNavSections`）、テーブルの置き場の道（`toTablePlaceholderPath`）。
- 今回読んだもの: `app/registry/types.ts`、`app/navigation/tablePlaceholderPath.ts`。`app/navigation/buildNavSections.ts` は先頭の説明・型・`toTablePlaceholderPath` を呼ぶ行（158 行）だけ。K-52 の持ち主（本文は `architecture.md`）、K-53 にも関わる。
- 状態: at-risk（K-52） ／ 読みの深さ: 深い（一部）

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/routing/`・`app/work-role/`
- 責務: AppShell の配置（`ShellLayout.tsx`。トップバーの利用者のメニューの名前 `userMenu` を「`{{name}}のメニュー`」・`Menu for {{name}}` にする、137 行）、表示言語、振り分け、作業ロールの切り替え（`WorkRoleSwitcher.tsx`、トップバーの `Dropdown`）。
- 今回の範囲: `ShellLayout.tsx` の 137 行と `app/i18n/messages/*.ts` の `userMenu` の行だけ（K-56）。
- 状態: at-risk（K-56） ／ 読みの深さ: 流し読み

### frontend-app-display-settings

- 場所: `app/display-settings/`
- 責務: 利用者の表示の設定とインスタンスの見た目の設定を解決し、make-you-chic-ui の `ThemeProvider` に渡す。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`・`shared/paging`
- 責務: 同じオリジンの `/api/**` の呼び出し、トークンの付与、401 で1回だけ更新して送り直す、Problem Details の読み取り、管理の API の 403 の判定。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログインの画面とログインの状態の提供元（`CurrentUserResponse.admin` を `LoginState.admin` に写す）。
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
- 責務: 利用者の管理の画面。表の行ごとの「操作」のメニュー（`UserRowActions.tsx`）。
- 今回読んだもの: `UserRowActions.tsx`。`messages.ts` はトリガーの文言の行だけ。K-55 の持ち主（下）。
- K-55 行の「操作」のトリガーの名前（`useradmin`・`groupadmin`・`roleadmin`）と axe-core 4.14 の判定:
  - 確かめた事実:
    - 3つの行の操作（`frontend/src/features/useradmin/UserRowActions.tsx` 72〜80 行、`frontend/src/features/groupadmin/GroupRowActions.tsx` 73〜81 行、`frontend/src/features/roleadmin/RoleRowActions.tsx` 69〜77 行）は、make-you-chic-ui の `Dropdown` の `trigger` に `<Button variant="secondary" size="sm" loading={busy} aria-label={name}>` を渡し、見える文字は `t('<機能>.actions.button')`（「操作」・`Actions`）。`aria-label` は「`{{name}}（{{email}}）の操作`」（useradmin）・「`{{name}}の操作`」（groupadmin・roleadmin）、英語は `Actions for {{name}}…`、処理中は末尾に「（処理中）」・` (processing)`（各 `messages.ts`）。
    - `Dropdown`（`a9309c4`）は `cloneElement` でトリガーに `aria-haspopup`・`aria-expanded`・`data-testid="dropdown-trigger"` を足す（名前は変えない）。
    - 前の Intent の記録（`261015-deps-ui-carryover` の `generation-notes.md` の Step 10、`make-you-chic-ui-request-4.md` 2節）は、axe-core 4.14.0 で `label-content-name-mismatch` の違反が `dropdown-trigger`（行の操作など）に出たとし、直し方を「名前に見える文字を含める形（例: 『操作: 行の名前』、または見える文字のままにして行の名前を `aria-describedby` で添える）」としている。
    - 手元の axe-core 4.13.0 の `labelContentNameMismatchEvaluate`（`frontend/node_modules/axe-core/axe.js` 26543 行）は、名前と見える文字を小文字にし、句読点・絵文字などを除いてから「名前が見える文字を含むか」（`includes`）で判定する。この判定なら「山田（a@example.com）の操作」は「操作」を含み、`Actions for …` も `Actions` を含む。
  - 見立て（未検証）: 4.13.0 の判定のままなら今の行のトリガーは違反にならないはずで、4.14.0 で判定の仕方（含むかではなく、語の区切りや先頭での一致など）が変わったか、違反の要素が行のトリガーではなく別の `dropdown-trigger`（K-56 のトップバーなど）だった可能性がある。4.14 の `axe.js` は手元に無いため確かめていない。直す前に、4.14 の規則の判定と違反が出た要素の `target` を確かめる（見える文字で始まる名前にすれば、どちらの判定でも通る見込み）。
- 状態: at-risk（K-55） ／ 読みの深さ: 深い（一部）

### frontend-feature-roleadmin

- 場所: `features/roleadmin/`
- 責務: ロールの管理・権限の設定・割り当ての画面。表の行ごとの「操作」のメニュー（`RoleRowActions.tsx`）。
- 今回読んだもの: `RoleRowActions.tsx`。`messages.ts` はトリガーの文言の行だけ。K-55（本文は `frontend-feature-useradmin`）。
- 状態: at-risk（K-55） ／ 読みの深さ: 深い（一部）

### frontend-feature-groupadmin

- 場所: `features/groupadmin/`
- 責務: グループの管理の画面。表の行ごとの「操作」のメニュー（`GroupRowActions.tsx`）。
- 今回読んだもの: `GroupRowActions.tsx`。`messages.ts` はトリガーの文言の行だけ。K-55（本文は `frontend-feature-useradmin`）。
- 状態: at-risk（K-55） ／ 読みの深さ: 深い（一部）

### frontend-feature-roletransfer

- 場所: `features/roletransfer/`
- 責務: 権限の YAML の書き出しと取り込みの画面。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-mypermissions

- 場所: `features/mypermissions/`
- 責務: 自分の権限を木で見る画面（`/api/me/permissions`）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-tables

- 場所: `features/tables/`
- 責務: メニューのテーブルを指す項目が移る置き場の画面（今は「準備中」）。置き場の API の問い合わせ（`useTableAccess.ts`・`tableAccessApi.ts`）。
- 今回読んだもの: `registration.ts`・`TablePlaceholderPage.tsx`・`useTableAccess.ts`・`tableAccessApi.ts`。K-52 の持ち主（本文は `architecture.md`）。業務データの一覧・詳細の画面の置き場の第一の候補。
- 状態: at-risk（K-52） ／ 読みの深さ: 深い（一部）

### frontend-feature-registration

- 場所: `features/registration/`
- 責務: 招待を受けた人のリンクの確かめと登録の完了の画面（ログインなし）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-preferences

- 場所: `features/preferences/`
- 責務: 自分の設定とパスワードの変更の画面。make-you-chic-ui の `Alert`・`Button`・`FormField`・`RadioGroup`・`TextInput` を使い、`Dropdown` を使わない（import の行の grep。K-56）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-e2e

- 場所: `frontend/e2e/`（`*.e2e.ts` 18 本、010〜170、と `support/` 26 ファイル）
- 責務: Playwright の E2E（`./gradlew e2eTest`）。`verify` と CI の外。アクセシビリティの検査（050〜080・100・120・130・135・140・160）と流れ（020・030・040・090・110・150・170 など）。
- 今回読んだもの: `support/axe.ts`（タグ `wcag2a`・`wcag2aa`・`wcag21a`・`wcag21aa`、既知の違反の一覧は空）。080・170 は grep の行だけ。`support/preferencesFixtures.ts` の見本の氏名（27 行）は K-56 で確かめた。K-52・K-56・K-60 に関わる。
- 状態: at-risk（K-56・K-60） ／ 読みの深さ: 深い（一部）

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `a9309c4`。画面からは npm の `file:` の依存で使う）
- 責務: デザインシステム（AppShell・Topbar・Sidebar・Dropdown・Avatar・Button・Modal・Table・FormField ほか）。中身はこのリポジトリから変えない（`project.md` の Forbidden）。
- 今回読んだもの: `packages/make-you-chic-ui/src/components/Avatar/Avatar.tsx`。`Dropdown/Dropdown.tsx`（trigger の扱い、`cloneElement` で足す属性）と `AppShell/Topbar.tsx`（76〜97 行）は一部だけ。`a9309c4..b0a1e79` の差の統計（K-60、本文は `dependencies.md`）。
- K-56 表示の設定の画面（E2E 080）の違反は、どの画面にもあるトップバーの利用者のメニューのトリガー（Topbar と Avatar）の可能性がある:
  - 確かめた事実:
    - 表示の設定の画面（`frontend/src/features/preferences/`）は `Dropdown` を使わない（`frontend-feature-preferences`）。
    - アプリシェルの中の画面にはいつもトップバーがあり、`AppShell/Topbar.tsx`（76〜97 行）は利用者のメニューを `Dropdown` で描く。トリガーは `<button aria-label={labels.userMenu(user.name)}>` の中に `<Avatar name={user.name} />` を置く。アプリは `userMenu` を「`{{name}}のメニュー`」・`Menu for {{name}}` にしている（`frontend/src/app/layout/ShellLayout.tsx` 137 行、`app/i18n/messages/*.ts`）。
    - `Avatar/Avatar.tsx` は画像が無いとき、名前の最初の2語の頭文字を大文字にした文字（例: `Taro Yamada` → `TY`、`山田 太郎` → `山太`）を要素の中に文字として描く（32〜35 行、`role="img"`・`aria-label={name}` は 60 行。アーキテクトが確かめ直した）。
    - `a9309c4..b0a1e79` で変わったのは `Dropdown.tsx`・`Dropdown.css`・`Dropdown.test.tsx` の3つだけ。Topbar と Avatar は変わっていない。
    - 作業ロールの切り替え（`frontend/src/app/work-role/WorkRoleSwitcher.tsx`）もトップバーの `Dropdown` を使う（前の Intent の記録では「作業ロール: 〇〇」で違反にならない見込みとされ、まだ確かめていない）。
    - 080 の E2E の見本の氏名は2語の「検査 太郎」（`frontend/e2e/support/preferencesFixtures.ts` 27 行の `SAMPLE_DISPLAY_NAME`）。
  - 見立て（未検証）: 頭文字が2文字の名前では、トリガーの見える文字（頭文字「検太」）が名前（「検査 太郎のメニュー」）に含まれないため、`label-content-name-mismatch` に当たる。そのため 080 の違反はこのトリガーの可能性が高い。正しければ make-you-chic-ui の側（Topbar の名前の付け方か Avatar の頭文字の扱い）の直しで、`b0a1e79` では直らず、依頼（5）が要る。080 の E2E の違反の要素（`target`）を見て確かめる必要がある。
- 状態: at-risk（K-56・K-60） ／ 読みの深さ: 深い（一部）

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、固定先 `8d44c36`、0.1.0）。Gradle の composite build で使う。
- 責務: メールのテンプレートの Mustache のエンジン。今回の Intent では触れない見込み。
- 状態: healthy ／ 読みの深さ: 流し読み

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`backend/config/spotbugs-exclude.xml`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/eslint.config.js`・`config/`・`.github/workflows/ci.yml`・`.github/dependabot.yml`
- 責務: 1コマンドの検査 `./gradlew verify`、E2E の `./gradlew e2eTest`、WAR の組み立て、依存の取得元の固定と composite build、lockfile、カバレッジの下限（`packagesJudgedByTotal`）、SpotBugs の関門、Gitleaks・OSV-Scanner、Dependabot。
- 今回読んだもの: `gradle/libs.versions.toml`、`frontend/package.json`。`backend/build.gradle.kts`（`packagesJudgedByTotal` とカバレッジの検証の部分）・`frontend/eslint.config.js`（`no-restricted-imports` の2つの規則）・`.github/dependabot.yml`（npm の `ignore` とコメント）は一部だけ。`settings.gradle.kts`・`build.gradle.kts`・`ci.yml` は開いていない。K-60 に関わる（本文は `dependencies.md`）。
- 状態: at-risk（K-60） ／ 読みの深さ: 深い（一部）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`・`.env.targetdb.example`・`README.md`
- 責務: WAR をコピーするだけのイメージ、`app` のサービスと profile で起動する監視・メールの受け手・見本の対象DB。
- 今回の範囲: `Dockerfile` の ENTRYPOINT の行、`compose.yaml` の `mem_limit` の行、`.env.example`・`.env.targetdb.example` の変数の名前の行（値は表示していない。`.env` は開いていない）。K-59 に関わる。見本の対象DB に書けるアカウントは無い（K-49）。
- 状態: at-risk（K-49・K-59） ／ 読みの深さ: 流し読み

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験と使い捨ての環境、手元の監視の警報とダッシュボード、見本の対象DB（`docker/targetdb/`）、接続プールの運用の道具。
- 今回の範囲: `perf/README.md` の「(b) 大きな入力のヒープ」の節と `--storage` の行・150 行、`perf/k6/scenarios.js` の `dslMixed` の行、`docker/targetdb/postgres/01-sample-schema.sql`（`CREATE TABLE`・`PRIMARY KEY`・`CREATE VIEW` の行）と `02-reader-account.sh`（`GRANT` の行）、警報 `ms-forbidden` の式。K-59 の持ち主（本文は `code-quality-assessment.md`）、K-49・K-51・K-61 に関わる。
- 状態: at-risk（K-59） ／ 読みの深さ: 流し読み
