# 部品の一覧（mastersmith2）

## 読み方

- 見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回までの 47 個に、業務データの機能 `businessdata` を足した 48 個（前回の知識ベースの時点ではコードに無かった）。
- 2026-10-25（記録のコミット `bc484c9`）の Full rescan・深さ Minimal で作り直した。前回までの K-1〜K-61 の本文はここに残していない。
- 状態: healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ: 「深い（一部）」は今回深く読んだファイルを持つ部品で、`analyzed.components` の 5 個。「流し読み」は名前・一覧・grep・前回の記録による（今回は確かめていない）。
- パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省く。依存の向きは `dependencies.md`。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java` ／ 責務: 起動クラス。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（6 ファイル）と `application.yaml`・`logback-spring.xml` ／ 責務: Spring Security の連鎖、SPA の配信、観測、アプリの設定値。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### common-error

- 場所: `common/error/{domain,service,web}` ／ 責務: `ProblemType`（code・状態コード）、`BusinessException`、`@RestControllerAdvice` の Problem Details。`common.error.domain`・`common.error.service` は `packagesJudgedByTotal` に残る（`team.md`）。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### common-persistence

- 場所: `common/persistence/` ／ 責務: 行の排他の結果の型と排他の失敗の見分け。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### common-security

- 場所: `common/security/` ／ 責務: API の分類の印 `@ApiAccess`、安全の決まりの差し込み口（order つき）、`/api/**` の既定の口、エラー応答の書き出し。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### common-web

- 場所: `common/web/`・`common/paging/` ／ 責務: 本文の大きさの上限、一覧のページ送りの共通の計算。`common.web` は `packagesJudgedByTotal` に残る。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### common-health

- 場所: `common/health/` ／ 責務: ヘルスチェック。`packagesJudgedByTotal` に残る。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### common-i18n

- 場所: `common/i18n/` ／ 責務: 表示言語の解決（日英）。`common.i18n.domain` は `packagesJudgedByTotal` に残る。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### common-observability

- 場所: `common/observability/` ／ 責務: `TraceAspect`（`web`・`service`・`domain`・`repository` の引数と戻り値を TRACE のときに文字列にして出す）ほか。行の値を持つ型は `toString` で伏せる必要がある。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### auth

- 場所: `auth/{domain,repository,service,web}`（58 ファイル） ／ 責務: ログイン・ロック・トークン・要求ごとの認証と主体の組み立て。 ／ 依存: `common`・`user` ／ 状態: healthy ／ 読みの深さ: 流し読み

### access

- 場所: `access/{domain,service,web}`（20 ファイル） ／ 責務: `/api/admin/**` を管理者だけにする決まり、401・403 の応答と拒否の出来事。 ／ 依存: `auth`・`common`・`config` ／ 状態: healthy ／ 読みの深さ: 流し読み

### audit

- 場所: `audit/{domain,repository,service}`（15 ファイル） ／ 責務: 各機能の出来事を受け、確定の後に `audit_events` に追記する。 ／ 依存: `access`・`auth`・`businessdata`・`dslmanage`・`group`・`invitation`・`role`・`user`・`useradmin`（出来事の型。`import` の検索で確かめた） ／ 状態: healthy ／ 読みの深さ: 流し読み

### user

- 場所: `user/{domain,repository,service,web}`（70 ファイル） ／ 責務: 利用者（`admin_flag`・停止の列）、パスワード、自分の設定、初期管理者の作成と救済。 ／ 依存: `common` ／ 状態: healthy ／ 読みの深さ: 流し読み

### invitation

- 場所: `invitation/{domain,lock,repository,service,web}`（65 ファイル） ／ 責務: 招待・送り直し・取り消し、リンクの確かめと登録の完了。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### useradmin

- 場所: `useradmin/{domain,service,web}`（27 ファイル） ／ 責務: 利用者の管理の API（一覧・変更・管理者の印・停止・失敗回数の取り消し）。 ／ 依存: `access`・`auth`・`common`・`user` ／ 状態: healthy ／ 読みの深さ: 流し読み

### mail

- 場所: `mail/{config,domain,service,template,transport}`（28 ファイル） ／ 責務: SMTP の送信と Mustache のテンプレート。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### appearance

- 場所: `appearance/{config,service,web}`（14 ファイル） ／ 責務: インスタンスの見た目の設定を画面に渡す。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`（43 ファイル、web なし）
- 責務: 対象DB への接続とスキーマの読み取り。読み取り専用のプール `targetDataSource`（既定の候補にしない、既定の上限 5、借りる待ち 3 秒）と、設定が揃うときだけ作る書き込みのプール（`TargetWriteDataSourceConfig`、`write.pool.maximumSize`）。貸し出しの口 `TargetReadAccess.withRead(ReadPurpose, …)`・`TargetWriteAccess`。目的 `ReadPurpose` は `GENERATE`・`COMPARE`・`LIST`・`DETAIL`、時間の上限は `TargetDbProperties.QueryTimeout`。K-63 の持ち主（本文は `architecture.md`）。
- 今回読んだもの: `config/TargetDataSourceConfig.java`・`config/TargetDbProperties.java`・`domain/ReadPurpose.java`。ほかはクラスの一覧と `TargetWriteDataSourceConfig` のプールの行だけ。
- 依存: なし（ほかの機能を import しない） ／ 状態: at-risk（K-63） ／ 読みの深さ: 深い（一部）

### dsl

- 場所: `dsl/{domain,parse,service,validate}`（64 ファイル）と `resources/dsl/` ／ 責務: DSL の型・安全な読み込み・検証・適用中のモデルの提供口 `ActiveDslModelProvider`。DSL の型（`dsl/domain`）に索引の情報は無い（`index` の語の grep で 0 件、K-65）。 ／ 依存: なし ／ 状態: healthy ／ 読みの深さ: 流し読み

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`（54 ファイル） ／ 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴。対象DB のスキーマを `targetdb` で読む。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### role

- 場所: `role/{domain,repository,service,store,transfer,web}`（143 ファイル） ／ 責務: ロールと権限の設定、割り当て、作業ロール、実効の権限の解決（`EffectivePermissionResolver`）、権限の YAML の入れ出し。 ／ 依存: `common`・`dsl`・`group`・`user`。依存されてよいのは `audit`・`navigation`・`businessdata`（`RoleBoundaryArchitectureTest`） ／ 状態: healthy ／ 読みの深さ: 流し読み

### group

- 場所: `group/{domain,repository,service,store,web}`（55 ファイル） ／ 責務: グループの管理。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### navigation

- 場所: `navigation/{domain,service,web}`（23 ファイル） ／ 責務: 適用中の DSL のメニューを作業ロールの権限で絞って渡す。テーブルの可否の問い合わせ。 ／ 依存: `auth`・`common`・`dsl`・`role` ／ 状態: healthy ／ 読みの深さ: 流し読み

### businessdata

- 場所: `businessdata/{domain,repository,service,web}`（113 ファイル）
- 責務: 業務データの定義・一覧・詳細・作成・更新・削除。`web` が 6 口（`/api/tables`）、`service` が権限の判定と対象DB の貸し出し、`repository` が SQL の組み立て（`RowSqlBuilder`）と実行（`RowSqlExecutor`）と DB の種類ごとの方言（`MysqlFamilyRowDialect`・`PostgresRowDialect`）と失敗の分類（`RowSqlFailureClassifier`）、`domain` が検証・列の出し分け・出来事 `BusinessDataChangedEvent`。K-62・K-64・K-65 の持ち主。
- 今回読んだもの: `repository/RowSqlBuilder.java`（`where`・`count`・`page`）・`repository/RowSqlExecutor.java`（`search`）・`service/BusinessDataReadService.java`（`search`）・`web/RowPageResponse.java`・`web/BusinessDataReadController.java`・`web/BusinessDataWriteController.java`・`web/BusinessDataPaths.java`・`domain/PageRequest.java`・`domain/ContainsPattern.java`。書き込み側と `domain` の大半は読んでいない。
- K-65: `RowDialect` の実装は `MysqlFamilyRowDialect`（MySQL と MariaDB の共通）と `PostgresRowDialect` の 2 つで、MySQL と MariaDB で別の最適化をするには切り方の見直しが要る（見立て）。`web`・`domain` に索引を案内する部品は見当たらない（クラス一覧と grep による）。
- 依存: `common`・`dsl`・`role`（`role.service`・`role.domain` だけ）・`targetdb`。依存されるのは `audit`（`businessdata.domain` だけ）。境界テストは `BusinessDataBoundaryArchitectureTest`（`RowSqlExecutor` だけが JDBC の接続から文を作る、読み取り側はトランザクションと `JdbcTemplate` を使わない、など）。
- 状態: at-risk（K-62・K-64・K-65） ／ 読みの深さ: 深い（一部）

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/` ／ 責務: 機能ごとの `testsupport`、アプリ全体を起動する結合テスト、層と境界の検査（19 ファイル）、API の分類の網羅。 ／ 状態: healthy ／ 読みの深さ: 流し読み

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/`（骨組み）・`shared/` の一部 ／ 責務: 画面の起動、共通の画面、管理の API の 403 の画面への切り替え。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-registry

- 場所: `app/registry/`・`app/navigation/` ／ 責務: `registration.ts` を置くだけで機能を読み込む仕組み、サイドバーの区画の組み立て、テーブルの道の組み立て。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/routing/`・`app/work-role/` ／ 責務: AppShell の配置、表示言語、振り分け、作業ロールの切り替え。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-display-settings

- 場所: `app/display-settings/` ／ 責務: 利用者の表示の設定とインスタンスの見た目を `ThemeProvider` に渡す。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`・`shared/paging` ／ 責務: 同じオリジンの `/api/**` の呼び出し、トークンの付与、Problem Details の読み取り。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-auth

- 場所: `features/auth/` ／ 責務: ログインの画面とログインの状態。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-admin

- 場所: `features/admin/` ／ 責務: 管理の入口の画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-dsl

- 場所: `features/dsl/` ／ 責務: DSL の管理画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-invitation

- 場所: `features/invitation/` ／ 責務: 管理者の招待の画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-useradmin

- 場所: `features/useradmin/` ／ 責務: 利用者の管理の画面（行ごとの「操作」のメニュー）。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-roleadmin

- 場所: `features/roleadmin/` ／ 責務: ロールの管理・権限の設定・割り当ての画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-groupadmin

- 場所: `features/groupadmin/` ／ 責務: グループの管理の画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-roletransfer

- 場所: `features/roletransfer/` ／ 責務: 権限の YAML の書き出しと取り込みの画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-mypermissions

- 場所: `features/mypermissions/` ／ 責務: 自分の権限を木で見る画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-tables

- 場所: `features/tables/`（業務データの画面）
- 責務: 一覧（`BusinessDataListPage`・`BusinessDataTable`・`SearchPanel`・`listPaging.ts`・`listState.ts`）、詳細（`BusinessDataDetailPage`）、作成と更新（`BusinessDataFormPage`）、削除の確かめ、API の呼び出し（`businessDataApi.ts`）、制限の通知（`RestrictionNotice`）。件数は `page.total`（数値）をそのまま表示とページ送りに使う（K-64、本文は `api-documentation.md`）。
- 今回読んだもの: `BusinessDataListPage.tsx`（207〜225 行の件数とページの範囲の外れ）・`BusinessDataTable.tsx`（213・251 行）。ファイルの一覧。
- 状態: at-risk（K-64） ／ 読みの深さ: 深い（一部）

### frontend-feature-registration

- 場所: `features/registration/` ／ 責務: 招待を受けた人の登録の完了の画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-preferences

- 場所: `features/preferences/` ／ 責務: 自分の設定とパスワードの変更の画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-e2e

- 場所: `frontend/e2e/`（`*.e2e.ts` 20 本、010〜185、と `support/`） ／ 責務: Playwright の E2E（`./gradlew e2eTest`）。`verify` と CI の外。業務データは 180（流れ）と 185（アクセシビリティ）。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `ecb07e9248af9f1203a0bd2d79673d35547a4416`）。画面からは npm の `file:` の依存。中身はこのリポジトリから変えない（`project.md` の Forbidden）。 ／ 責務: デザインシステム。今回は固定先のコミットの確認だけ。lockfile の `"hasInstallScript"` は `package-lock.json` に 1 件（3112 行付近、どのパッケージかは未確認）。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、固定先 `8d44c36`、0.1.0。Gradle の composite build） ／ 責務: メールのテンプレートの Mustache のエンジン。 ／ 状態: healthy ／ 読みの深さ: 流し読み

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vitest.config.ts`・`config/`・`.github/workflows/ci.yml`・`.github/dependabot.yml`
- 責務: 1コマンドの検査 `./gradlew verify`、WAR の組み立て、依存の取得元の固定と composite build、lockfile、カバレッジの下限、Gitleaks・OSV-Scanner、Dependabot。K-66 の持ち主（版の一覧は `technology-stack.md`、見送りは `dependencies.md`）。
- 今回読んだもの: `gradle/libs.versions.toml`・`backend/build.gradle.kts` の Jackson の BOM と `packagesJudgedByTotal` の部分・`backend/gradle.lockfile` の版・`.github/dependabot.yml`・`frontend/package.json`・`frontend/vitest.config.ts`。`ci.yml` は構成だけ。
- 状態: at-risk（K-66） ／ 読みの深さ: 深い（一部）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`・`README.md` ／ 責務: WAR をコピーするだけのイメージ、`app` と profile で起動する監視・メールの受け手・見本の対象DB。読み取りのプールの環境変数は `.env.example` 66〜67 行（値は表示していない。`.env` は開いていない）。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験と使い捨ての環境、手元の監視、見本の対象DB。biz の場面は `bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail`・`bizSearchWide`（`perf/k6/scenarios.js` 160〜165 行）。種は `perf/biz/make-biz-seed.sh`（`biz_item` は 100 カラム × 既定 100,000 行、`c_001` に索引なし）、DSL は `perf/make-biz-dsl.mjs`、環境は `docker/perf/compose.yaml`。K-67・K-68 の持ち主（本文は `code-quality-assessment.md`）。
- 今回読んだもの: `perf/k6/scenarios.js` の biz の場面の説明部、`perf/README.md` の biz の場面の表と grep の行、`docker/perf/compose.yaml` の資源の上限の行。
- 状態: at-risk（K-67・K-68） ／ 読みの深さ: 深い（一部）
