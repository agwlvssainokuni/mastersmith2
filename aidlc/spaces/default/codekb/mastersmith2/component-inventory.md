# 部品の一覧（mastersmith2）

## 読み方

- 見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は 49 個（2026-11-06 に `frontend-shared-format` を足した。それまでは 48 個。2026-11-08 は増減なし）。
- 2026-11-08（記録のコミット `16f46f1`）の Intent `261107-verify-quick-local` の Focused scan で、`build-and-verify` の記述に今回の読み（K-111〜K-118）を足した。今回の `analyzed.components` はこの1つ。`backend-test-support` は結合テストの数と Testcontainers の前提（`ContainerRuntimeCheck`・`TargetDbTestDatabase`）を流し読みしただけのため、`analyzed.components` に入れていない（一行足した）。ほかの部品の記述は前回以前のまま。
- 2026-11-06（記録のコミット `a789a6f`）の Intent `261104-tz-options-perf` の Focused scan で、`frontend-feature-preferences` の記述を書き直し、`frontend-shared-format`（`frontend/src/shared/format/`。前の Intent の U1 で入った日時の変換を含む）を新しく足した。今回の `analyzed.components` はこの2つ。`frontend/vitest.config.ts` も深く読んだが、`build-and-verify` の部品全体は読んでいないため `analyzed.components` に入れていない（`build-and-verify` に一行足した）。`frontend-app-core` の `formatDateTime.ts` の記述は古い（K-110。訂正の注を足した）。ほかの部品の記述は前回以前のまま。
- 2026-11-03（記録のコミット `fa0b01e`）の Intent `261102-timezone-ui-polish` の Focused scan で、`targetdb`・`dsl`・`dslmanage`・`navigation`・`businessdata`・`frontend-app-core`・`frontend-registry`・`frontend-app-layout-i18n`・`frontend-feature-admin`・`frontend-feature-tables` の記述に今回の読み（K-94〜K-104）を足し、`make-you-chic-ui`・`java-mustache-processor` の固定先を直した（K-105）。今回の `analyzed.components` はこの 10 個。ID は 48 個のまま（増減なし）。前回まで深く読んだ `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`role` は、今回は確かめていない（記述は前回以前のまま、流し読みの扱い）。
- 2026-11-01（記録のコミット `6079f13`）の Intent `261101-maintenance-update` の Focused scan で、`build-and-verify`・`container-runtime`・`perf-and-monitoring`・`businessdata` の記述に今回の読み（K-81〜K-93）を足し、`frontend-e2e`・`make-you-chic-ui` の数と固定先を直した。今回の `analyzed.components` はこの4つ。ID は 48 個のまま。
- 2026-10-29（記録のコミット `15a25aa`）の Intent `261029-reference-lookup-cleanup` の Focused scan で、今回の範囲の部品（`businessdata`・`dsl`・`targetdb`・`role`・`frontend-feature-tables`・`perf-and-monitoring`・`container-runtime`・`build-and-verify`）の記述を更新した。ほかの部品の記述は前回（2026-10-28、`261027-perf-targets-review`）以前のまま。前回までの K-1〜K-61 の本文はここに残していない。
- 状態: healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ: 「深い（一部）」は 2026-11-03 に深く読んだファイルを持つ部品で、`analyzed.components` の 10 個（上の行）。それより前の記録で「深い（一部）」と書いた部品のうち今回の 10 個に無いものは、今回は確かめていない。以下は前回（2026-10-29）の書き方: `analyzed.components` の 8 個（`businessdata`・`dsl`・`targetdb`・`role`・`frontend-feature-tables`・`perf-and-monitoring`・`container-runtime`・`build-and-verify`）。「流し読み」は名前・一覧・grep・前回以前の記録による（今回は確かめていない）。前回は深く読んだ `build-and-verify` の `libs.versions.toml`・`gradle.lockfile`・`.github/dependabot.yml` の読み直しは、今回は `dependabot.yml` と `backend/build.gradle.kts`・`frontend/package.json` に絞った。2026-11-08 に深く読んだのは `build-and-verify` だけ（下の部品の行）。
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
- 責務: 対象DB への接続とスキーマの読み取り。読み取り専用のプール `targetDataSource`（既定の候補にしない、既定の上限 10、借りる待ち 3 秒）と、設定が揃うときだけ作る書き込みのプール（`TargetWriteDataSourceConfig`、`write.pool.maximumSize`）。貸し出しの口 `TargetReadAccess.withRead(ReadPurpose, …)`・`TargetWriteAccess`。目的 `ReadPurpose` は `GENERATE`・`COMPARE`・`LIST`・`DETAIL`、時間の上限は `TargetDbProperties.QueryTimeout`。K-63 の持ち主（本文は `architecture.md`）。
- 今回読んだもの: `config/TargetDataSourceConfig.java`・`config/TargetDbProperties.java`・`domain/ReadPurpose.java`（読み取りのプールの既定 10、書き込みのプールの既定 5 を `TargetDbProperties` で確かめた）、`service/JdbcTargetReadAccess.java` の接続の返し方（`evictConnection`）。ほかはクラスの一覧による。
- 今回（2026-10-29）の読み: `ReadPurpose`・`TargetDbProperties.QueryTimeout`・読み取りの口の署名だけ（署名レベル）。参照先の選択肢の読み取りに専用の `ReadPurpose` が要るかは未決（K-75）。`targetdb` に手を入れると `TargetDbBoundaryArchitectureTest` の許可に触れうる。
- 今回（2026-11-03）の読み（K-95・K-98）: `config/TargetDataSourceConfig.java` の全文。MySQL・MariaDB の読み取りのプールは接続を作るときに `SET time_zone = '+00:00'`（61・150〜152 行）、書き込みのプールは `SET time_zone = '+00:00', sql_mode = …STRICT_ALL_TABLES`（68 行。`TargetWriteDataSourceConfig.java` 113〜114 行は grep）。PostgreSQL には初期化の文を付けない（セッションの時差は JVM の既定に従う見込み、未検証）。`repository/MysqlSchemaQueries` は `DATETIME_PRECISION` を読まない（grep）。
- 依存: なし（ほかの機能を import しない） ／ 状態: at-risk（K-63・K-95） ／ 読みの深さ: 深い（一部）

### dsl

- 場所: `dsl/{domain,parse,service,validate}`（64 ファイル）と `resources/dsl/` ／ 責務: DSL の型・安全な読み込み・検証・適用中のモデルの提供口 `ActiveDslModelProvider`。DSL の型（`dsl/domain`）に索引の情報は無い（`index` の語の grep で 0 件、K-65）。
- 選択肢の型（K-74）: `OptionSource`（FIXED・REFERENCE・LOOKUP。種類ごとの必須をコンストラクターで検査）、`OptionSourceKind`、`FormPart`（`LOOKUP` を含む）、`LookupSearchItem`・`LookupListItem`。`DslModelMapper`（160〜180 行）が写し、`DslSemanticValidator`（230〜290・375〜380 行）が `select`・`radio` は FIXED か REFERENCE、`lookup` は LOOKUP のみ、参照先の table・column の実在（同じスキーマの中）を照合する。`resources/dsl/dsl-schema-v2.json`（271〜335 行が `options`）。
- 今回読んだもの: 上の選択肢の型・`DslModelMapper`・`DslSemanticValidator` の選択肢の節・`dsl-schema-v2.json` の `options` の節。
- 今回（2026-11-03）の読み（K-94・K-96・K-100・K-101）: `domain/DbType`（名前・長さ・精度・小数の桁・NULL の可否。日時の精度の欄は無く `precision` を使う）・`FormPart`（`date`・`datetime` はあるが時刻の部品は無い）・`ListFormat`（`DATE`・`DATETIME`・`TIME` ほか。頭に「書式の中身は画面の Intent で決める」）・`DslColumn`・`DslTable`（`view` の印）・`DslMenuItem`（`icon` は任意の文字）・`TableRef`・`DslModel`（`findTable`）・`ListSetting`・`DetailSetting`。型の名前と部品の組み合わせ（例 `timestamp` に `formPart: text`）を意味の検証で縛っていない（`validate/DslSemanticValidator`・`service/DslModelMapper` の grep で日時の語が 0 件）。
- 依存: なし ／ 状態: healthy ／ 読みの深さ: 深い（一部）

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`（54 ファイル） ／ 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴。対象DB のスキーマを `targetdb` で読む。
- 今回（2026-11-03）の読み（K-98・K-100）: `generate/TypeCategoryMapping.java`・`TypeCategory.java`。型の名前（MySQL・MariaDB は `DATA_TYPE`、PostgreSQL は `udt_name`）を分類し、日時（`datetime`・`timestamp`・`timestamptz`）は `formPart: datetime`・`list.format: DATETIME`・検索 RANGE、時刻（`time`・`timetz`）は `formPart: text`・`list.format: TIME`・検索 EQUALS、日付は `formPart: date`・`list.format: DATE`・検索 RANGE を初期値にする。日時の精度は DSL に写さない。`generate/DslTreeBuilder` はメニューに `icon` を書かない（86〜95 行、grep）。
- 状態: healthy ／ 読みの深さ: 深い（一部）

### role

- 場所: `role/{domain,repository,service,store,transfer,web}`（143 ファイル） ／ 責務: ロールと権限の設定、割り当て、作業ロール、実効の権限の解決（`EffectivePermissionResolver`）、権限の YAML の入れ出し。 ／ 依存: `common`・`dsl`・`group`・`user`。依存されてよいのは `audit`・`navigation`・`businessdata`（`RoleBoundaryArchitectureTest`）
- 今回読んだもの: `domain/PermissionSnapshot` の公開の口（`main(schemaName, tableName, columnName)` は 132 行、`create` ほか）。参照先のテーブル・カラムごとに実効の権限を引ける（K-75）。`role` に手を入れると `RoleBoundaryArchitectureTest` の許可の一覧に触れうる（緩める場合は計画に明記して依頼者の承認）。
- 状態: healthy ／ 読みの深さ: 深い（一部）

### group

- 場所: `group/{domain,repository,service,store,web}`（55 ファイル） ／ 責務: グループの管理。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### navigation

- 場所: `navigation/{domain,service,web}`（23 ファイル） ／ 責務: 適用中の DSL のメニューを作業ロールの権限で絞って渡す（`GET /api/me/navigation`）。テーブルの可否の問い合わせの口 `GET /api/me/table-access` は前の Intent で消えている（`NavigationController` の注記）。 ／ 依存: `auth`・`common`・`dsl`・`role`
- 今回（2026-11-03）の読み（K-100・K-101）: `domain/{NavNode,NavTableRef,NavIconPolicy,MenuFilter,NavigationMenu}`・`service/NavigationService`・`web/{NavNodeResponse,NavTableRefResponse,NavigationController}`。`MenuFilter` は純粋な関数で、子から先に絞り、テーブルの主権限が NONE でない項目だけを移れる項目にする。アイコンは `NavIconPolicy.iconOf`（許した名前と文字どおり照らし、外れは `list`）。応答にテーブルかビューかの印は無い。`service/AllowedNavIconList` は一覧だけ。
- 状態: healthy ／ 読みの深さ: 深い（一部）

### businessdata

- 場所: `businessdata/{domain,repository,service,web}`（113 ファイル）
- 責務: 業務データの定義・一覧・詳細・作成・更新・削除。`web` が 6 口（`/api/tables`）、`service` が権限の判定と対象DB の貸し出し、`repository` が SQL の組み立て（`RowSqlBuilder`）と実行（`RowSqlExecutor`）と DB の種類ごとの方言（`MysqlFamilyRowDialect`・`PostgresRowDialect`）と失敗の分類（`RowSqlFailureClassifier`）、`domain` が検証・列の出し分け・出来事 `BusinessDataChangedEvent`。K-62・K-64・K-65 の持ち主。
- 今回読んだもの（前回の読みを今回も再確認した範囲を含む）: `repository/RowSqlBuilder.java`（`where`・`count`・`page`）・`repository/RowSqlExecutor.java`（`search`）・`service/BusinessDataReadService.java`（`search`）・`web/RowPageResponse.java`・`web/BusinessDataReadController.java`・`web/BusinessDataWriteController.java`・`web/BusinessDataPaths.java`・`domain/PageRequest.java`・`domain/ContainsPattern.java`。書き込み側と `domain` の大半は読んでいない。
- K-65: `RowDialect` の実装は `MysqlFamilyRowDialect`（MySQL と MariaDB の共通）と `PostgresRowDialect` の 2 つで、MySQL と MariaDB で別の最適化をするには切り方の見直しが要る（見立て）。`web`・`domain` に索引を案内する部品は見当たらない（クラス一覧と grep による）。
- 依存: `common`・`dsl`・`role`（`role.service`・`role.domain` だけ）・`targetdb`。依存されるのは `audit`（`businessdata.domain` だけ）。境界テストは `BusinessDataBoundaryArchitectureTest`（`RowSqlExecutor` だけが JDBC の接続から文を作る、読み取り側はトランザクションと `JdbcTemplate` を使わない、など）。
- 今回（2026-10-29）の読み（K-74・K-75）: `domain/ColumnWriteRulesFactory`（48・106〜113 行）・`domain/ColumnWriteRules`（80 行）・`domain/SearchOperatorPolicy`・`domain/BusinessDataFieldErrorReason`・`domain/RowWriteFailureReason`・`domain/WriteRejection` の `REFERENCE_NOT_FOUND`、`domain/TableAccessPolicy`（64 行で `ColumnWriteRulesFactory.create` を呼ぶ）、`web/ColumnDefinitionResponse`・`web/OptionResponse`、`service/TableAccessResolver`（94〜170 行）、`service/BusinessDataWriteService`（245〜292 行の外部キー違反の写し）、`domain/PageRequest`・`domain/SearchRequestValidator`、境界テストの許可の一覧。`ColumnWriteRulesFactory` が `lookup` を `text` に置き換えているのは前の Intent の意図的な暫定。
- 今回の Intent への意味: 参照先の読み取りを `businessdata` の内側に置く限り、`BusinessDataBoundaryArchitectureTest` の許可の一覧（`targetdb.service/domain`・`role.service/domain`・`dsl.domain/service`・`common` など）は緩めずに済む見込み（未検証）。SpotBugs の `SQL_` 除外は `RowSqlExecutor` のメソッド単位（`search`・`detail`・`precheckUnique`・`insert`・`update` ほか）で個別に書かれており、新しいメソッドで SQL を組むと除外の追加（計画に明記して承認）か、既存の `search`・`detail` の再利用が要る（K-80）。
- 今回（2026-11-01）の読み（K-91・K-92）: `service/BusinessDataWriteService.java`（225〜255・320〜385・795〜935 行。書き込みと参照の段の WARN）、`repository/RowSqlBuilder.java`（`page`・`orderBy`）、`repository/RowDialect.java`（`sortExpression`）。ファイル数は 149（`find`）。SpotBugs の `SQL_` の除外は `RowSqlExecutor` の `search`・`detail`・`precheckUnique`・`insert`・`update`・`delete`・`inspectViolation`（`spotbugs-exclude.xml` 47〜62 行）。
- 今回（2026-11-03）の読み（K-94・K-97〜K-99）: `repository/ColumnValueCodec.java`（全文、604 行）・`RowDialect.java`・`MysqlFamilyRowDialect.java`・`PostgresRowDialect.java`・`domain/ValueType.java`・`ColumnKind.java`・`web/ColumnDefinitionResponse.java`。日時の型の対応・読み書き・文字の形は `architecture.md` の「日時の値の流れ」。`WriteRequestValidator`（295〜345 行、秒の端数の `TOO_MANY_DIGITS`）・`ColumnWriteRulesFactory`（40〜80 行）は流し読み。
- 状態: at-risk（K-62・K-64・K-65・K-74・K-75・K-91・K-92・K-94〜K-99） ／ 読みの深さ: 深い（一部）

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/` ／ 責務: 機能ごとの `testsupport`、アプリ全体を起動する結合テスト、層と境界の検査（19 ファイル）、API の分類の網羅。 ／ 状態: healthy ／ 読みの深さ: 流し読み
- 2026-11-08 の流し読み（K-113）: `*Test.java` 312・`*IT.java` 278。内部DB を H2 のファイルで持つ IT が 172（`common/testsupport/TestDatabase.register`）、対象DB のコンテナを使う IT が 39（`targetdb/testsupport/TargetDbTestDatabase` がクラスごとにコンテナを起動、`ContainerRuntimeCheck` が Docker の無いときに CI では失敗・手元では中断）。`junit-platform.properties` は jqwik の記録の置き場だけで、JUnit の並列は無い。`-PitForks` で並べるときの論点（同時のコンテナ・番号の取り合い）は `architecture.md` の K-116。

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/`（骨組み）・`shared/` の一部・`frontend/index.html` ／ 責務: 画面の起動、共通の画面（ホーム・見つからない・起動の失敗）、管理の API の 403 の画面への切り替え、共通の日時の書式（`shared/format/formatDateTime.ts`）。
- 今回（2026-11-03）の読み（K-96・K-102・K-103）: `app/pages/HomePage.tsx`（見出しと1文と、業務のメニューが空・失敗のときの案内だけ）、`shared/format/formatDateTime.ts`（UTC の日時を端末か渡した時差に直し略号を添える。DSL・招待・利用者の管理が使い、業務データは使わない）、`frontend/index.html`（`title` だけで favicon の `<link>` が無い）。
- 状態: at-risk（K-96・K-102・K-103。今回の Intent で手を入れる見込み） ／ 読みの深さ: 深い（一部）
- 訂正（K-110、2026-11-06）: `shared/format/formatDateTime.ts` は今は無い（前の Intent の U2 で消えた）。`shared/format/` は新しい部品 `frontend-shared-format` に分けて書く。上の 2026-11-03 の行は当時の記録として残す。

### frontend-registry

- 場所: `app/registry/`・`app/navigation/` ／ 責務: `registration.ts` を置くだけで機能を読み込む仕組み、サイドバーの区画の組み立て、テーブルの道の組み立て。
- 今回（2026-11-03）の読み（K-100）: `registry/allowedIcons.ts`（make-you-chic-ui の 18 種の写し、型で両方向を守る）・`registry/types.ts`（`SidebarItemRegistration.icon` は任意、区画は `ADMIN` だけ）・`navigation/buildNavSections.ts`（ホーム・業務・管理の3区画、アイコンは `resolveNavIcon` で外れを `list`）・`navigation/navigationItems.ts`（管理の項目を `order` の順に選ぶ）。
- 状態: at-risk（K-100。今回の Intent で手を入れる見込み） ／ 読みの深さ: 深い（一部）

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/routing/`・`app/work-role/` ／ 責務: AppShell の配置、表示言語、振り分け、作業ロールの切り替え。
- 今回（2026-11-03）の読み（K-102）: `layout/ShellLayout.tsx`（AppShell に区画・開閉・文言・`topbarEnd` の作業ロール・ユーザーメニューを渡す。`topbarStart` は使っていない。ロゴの口は AppShell に無い）、`i18n/messages/ja.ts`（`app.name`・`home.*`・`nav.*`・`adminForbidden.*` の文言）。
- 状態: healthy ／ 読みの深さ: 深い（一部）

### frontend-app-display-settings

- 場所: `app/display-settings/` ／ 責務: 利用者の表示の設定とインスタンスの見た目を `ThemeProvider` に渡す。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`・`shared/paging` ／ 責務: 同じオリジンの `/api/**` の呼び出し、トークンの付与、Problem Details の読み取り。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-shared-format

- 場所: `shared/format/`（`timeZone/` の本体 7 ファイル `index.ts`・`timeZoneGate.ts`・`formatInstant.ts`・`wallClock.ts`・`instantText.ts`・`parseLocalDateTime.ts`・`types.ts` と、`formatInstantToMinutes.ts`・`dateTimeInput.ts`） ／ 責務: 時差を明示に渡す日時の変換（時点 → 表示の文字、ローカル時刻 → 時点（夏時間の gap・overlap）、時点の文字の読み書きと比べ、名前の検査と `Intl.DateTimeFormat` のキャッシュ）と、その上の薄い関数。例外を投げず `kind` の union で返す純粋な関数だけの置き場（ESLint の `timeZoneConversionRules`）。前の Intent `261102-timezone-ui-polish` の U1 で入った。
- 依存: `Intl`（ECMA-402）だけ。使う側は `features/preferences`・`features/tables`・`features/useradmin`・`app/display-settings`（grep）。
- 今回（2026-11-06）の読み（K-106・K-107）: 整形器は名前ごとに 7 項目で作り、キャッシュの上限は 128（超えたら全体を捨てる）。`formatInstant` は1回の呼び出しで `getFormatter` 3 回・`formatToParts` 2 回（`wallAt` と `offsetSecondsAt` が同じ時点の壁時計を求め直す）。性能のテスト `conversionPerformance.test.ts` は整形器を温めた後の `formatInstant` 1 万回を測る（生成は測らない）。
- 状態: at-risk（K-106・K-107。今回の Intent で時差だけを求める関数を足す見込み） ／ 読みの深さ: 深い（本体はすべて。テストは `timeZoneGate.test.ts`・`conversionPerformance.test.ts` だけ深く、ほかは流し読み）

### frontend-feature-auth

- 場所: `features/auth/` ／ 責務: ログインの画面とログインの状態。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-admin

- 場所: `features/admin/`（本体 5 ファイル・CSS 1・テスト 5） ／ 責務: 管理の仮の画面（`/admin`、`access: 'ADMIN'`、サイドバーの管理の区画の先頭 `order: 200`・ラベル「管理」）。
- 今回（2026-11-03）の読み（K-104）: `registration.ts`・`AdminAreaPage.tsx`（表示のたびに `GET /api/admin/check`）・`adminApi.ts`・`adminAreaStatus.ts`・`AdminPlaceholder.tsx`（「今後の管理機能はこの画面に加わります。現在は表示できる機能がありません。」）。頼っているテスト・E2E は `code-structure.md` の K-104。
- 状態: at-risk（K-104。中身が空。今回の Intent で直すか廃止する見込み） ／ 読みの深さ: 深い（一部）

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

- 場所: `features/tables/`（業務データの画面。テストを含め約 5,300 行）
- 責務: 一覧（`BusinessDataListPage`・`BusinessDataTable`・`SearchPanel`・`listPaging.ts`・`listState.ts`）、詳細（`BusinessDataDetailPage`）、作成と更新（`BusinessDataFormPage`）、削除の確かめ（`DeleteConfirmModal`）、API の呼び出し（`businessDataApi.ts`）、状態（`businessDataStore.ts`・`useRowSearch`・`useRowDetail`・`useRowWrite`・`useTableDefinition`）、制限の通知（`RestrictionNotice`）、型（`businessDataTypes.ts`）。件数は `page.total`（数値）をそのまま表示とページ送りに使う（K-64、本文は `api-documentation.md`）。
- 今回の読み（K-76）: 構成の一覧、`BusinessDataFormPage.tsx` の部品の選び方（780〜870 行。`choices` と `formPart` だけで部品を決める）、`SearchPanel`（EQUALS・CONTAINS・RANGE・CHOICE の入力）、`businessDataTypes.ts` の頭。REFERENCE・LOOKUP のための部品（選択肢の読み込み・ピッカー）は無い。
- 流用の候補（未検証の仮説）: 参照ピッカー用に `shared/modal`（Modal）・`BusinessDataTable`（表）・`SearchPanel`（検索）の形が近い。共有の置き場は `shared/{paging,page-size,scroll-region,modal,tree,unsaved-guard,validation}`。`team.md` により、make-you-chic-ui に無い部品が要るときは設計の段で足りない点を一覧にして上流へ先に依頼する（固定先 `ecb07e9`。使える部品の一覧は今回読んでいない）。
- 今回（2026-11-03）の読み（K-96〜K-98）: `valueFormat.ts`（日時は `T` を空白に、時差つきは `Z` を `+00:00` にするだけで時差を直さない）・`formDiff.ts`（`toDateTimeInput` は秒までに切る、`toWireValue` は `DATETIME_TZ` に `+00:00` を付ける）・`searchConditions.ts`（型ごとの読み取りの式、時差つきの範囲の向きは画面で判定しない）・`SearchPanel.tsx` の `inputAttributes`・`BusinessDataFormPage.tsx` 880〜965 行（`datetime-local`・`date`、`DATETIME_TZ` に「UTC で入れる」）。`ListFormat` の日時の書式は使われていない。
- 状態: at-risk（K-64・K-76・K-96〜K-98） ／ 読みの深さ: 深い（一部）。テストの本体と `frontend/e2e/` は名前の検索だけ

### frontend-feature-registration

- 場所: `features/registration/` ／ 責務: 招待を受けた人の登録の完了の画面。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-preferences

- 場所: `features/preferences/` ／ 責務: 自分の設定（氏名・言語・テーマ・文字の大きさ・表示のタイムゾーン）とパスワードの変更の画面（`/me/preferences`・`/me/password`、ユーザーメニューから、遅延読み込み）。API は `GET`・`PUT /api/me/preferences`・`POST /api/me/password`。表示のタイムゾーンの選択（`TimeZoneSelect.tsx`）と選択肢の組み立て（`buildTimeZoneOptions.ts`）を持つ。
- 依存: `app/display-settings`（表示の設定と表示のタイムゾーンの提供元）・`app/i18n`・`shared/api-client`・`shared/validation`・`shared/format/timeZone`（`canInterpretTimeZone`・`formatInstant`）・make-you-chic-ui。
- 今回（2026-11-06）の読み（K-106・K-108・K-109）: 選択肢の組み立ては数百の名前の1件ごとに整形器の生成 1 回・`formatToParts` 2 回などを行い、性能のテスト `TimeZoneOptionsPerformance.test.ts`（NFR2.1.6、5 回の中央値 100 ms 以内）が CI の計測つきで2回落ちた。`buildTimeZoneOptions.test.ts` 111〜122 行は `formatInstant` の呼び出しを数える。
- 状態: degraded（K-106・K-108。CI の `frontendCoverage` で性能のテストが2回落ちた） ／ 読みの深さ: 深い（`buildTimeZoneOptions.ts`・`TimeZoneSelect.tsx`・性能のテストと組み立てのテストは全体。ほかの本体は全体か頭の説明と時差の関わる行）

### frontend-e2e

- 場所: `frontend/e2e/`（`*.e2e.ts` 22 本、010〜195、と `support/`。2026-11-01 に数えた） ／ 責務: Playwright の E2E（`./gradlew e2eTest`）。`verify` と CI の外。業務データは 180（流れ）と 185（アクセシビリティ）、参照ピッカーは 190（流れ）と 195（アクセシビリティ）。axe は `support/axe.ts` が `axe-core/axe.min.js` を読み、WCAG 2.0・2.1 の A・AA の規則で検査する。起動の引数に `-Dh2.compactThreads=1`（`playwright.config.ts` 137 行、K-85）。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `cae5fed185541b975dc56fb034dae3212d4c2247`。2026-11-03 の `git submodule status`。2026-11-01 の記録 `cab4207` から進んでいる。K-105）。画面からは npm の `file:` の依存。中身はこのリポジトリから変えない（`project.md` の Forbidden）。 ／ 責務: デザインシステム。今回は固定先のコミットの確認だけ。lockfile の `"hasInstallScript"` は `cab4207` の時点で `package-lock.json` に 1 件（3112 行付近、どのパッケージかは未確認）。2026-11-03 は `packages/make-you-chic-ui/src/components/Icon/registry.ts`（18 種）と `AppShell/{AppShell,Topbar,Sidebar}.tsx` の props だけを読み取りで見た（`SidebarNavItem.icon` は `IconName`、AppShell の差し込み口は `topbarStart`・`topbarEnd`、ロゴの口は無い。K-100・K-102）。 ／ 状態: healthy ／ 読みの深さ: 流し読み

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、固定先 `d61ba6d`、`0.1.0-6`。2026-11-03。2026-11-01 の記録 `8d44c36` から進んでいる。K-105。Gradle の composite build） ／ 責務: メールのテンプレートの Mustache のエンジン。 ／ 状態: healthy ／ 読みの深さ: 流し読み

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vitest.config.ts`・`frontend/scripts/`・`config/`・`.github/workflows/ci.yml`・`.github/dependabot.yml`（今回 2026-10-29 に深く読んだのは `backend/build.gradle.kts` の `packagesJudgedByTotal`・`frontend/package.json`・`.github/dependabot.yml` の全文・`.github/workflows/ci.yml` の段の名前。`gradle/libs.versions.toml`・`config/` は存在の確認だけ）
- 責務: 1コマンドの検査 `./gradlew verify`、WAR の組み立て、依存の取得元の固定と composite build、lockfile、カバレッジの下限、Gitleaks・OSV-Scanner、Dependabot。K-66・K-72 の持ち主（版の一覧は `technology-stack.md`、Dependabot の現状は `dependencies.md`）。K-111〜K-117 の持ち主（本文は `architecture.md` と `code-quality-assessment.md`）。
- 今回読んだもの: `backend/build.gradle.kts`（213〜221 行の `packagesJudgedByTotal` は現在 7 パッケージで `team.md` と一致。`businessdata`・`dsl`・`role`・`targetdb` は一覧に無くパッケージごとの下限が当たる）、`frontend/package.json`（i18next `^26.5.5`、react-i18next `^17.1.2`。lockfile は 26.5.10 と 17.1.8）、`.github/dependabot.yml`（K-79）、`ci.yml` の段の名前。`vitest.config.ts`・`gradle.lockfile` の版は今回読んでいない（前回の記録による）。
- 今回（2026-11-01）読んだもの（K-81・K-84・K-85・K-87・K-90）: `settings.gradle.kts`・`build.gradle.kts`（全文。`checkToolchain` の Node 24 の強制、verify の 10 段、perf の道具の Prettier と oxlint、E2E の見本の PG の準備と片付け）・`backend/build.gradle.kts`（全文）・`gradle/libs.versions.toml`・`gradle/wrapper/gradle-wrapper.properties`（9.8.2）・`backend/gradle.lockfile`・`frontend/package.json`・`package-lock.json`・`.npmrc`・`vite.config.ts`・`vitest.setup.ts`・`config/`・`.github/workflows/ci.yml`・`.github/dependabot.yml`（どちらも全文）。版と固定先の一覧は `technology-stack.md`。
- 状態: at-risk（K-81: Node 26 への引き上げで `engines`・`checkToolchain`・CI・README を同時にそろえる必要。K-90） ／ 読みの深さ: 深い（一部）
- 2026-11-06 に深く読んだのは `frontend/vitest.config.ts` だけ（K-108・K-110。部品全体は確かめていないため、今回の `analyzed.components` には入れていない）。`build.gradle.kts` の `frontendTest`・`frontendCoverage`（216〜220 行）と `ci.yml` の `verify` の job の頭は流し読み。K-81 の Node 26 はもう `engines`（`>=26 <27`）と `ci.yml`（`node-version: "26"`）に入っている（流し読み）。
- 今回（2026-11-08）読んだもの（K-111〜K-118）: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`frontend/vitest.config.ts`・`.github/workflows/ci.yml`・`config/` の2ファイル・`README.md`（どれも全文）、`frontend/scripts/` の `check-license-header.mjs`・`check-bundle-size.mjs`・`check-dist-assets.mjs`・`make-ico.mjs`（全文。`check-svg-safety.mjs`・`icon-binary.mjs` は頭だけ）。`verify` は 10 の段を `mustRunAfter` でつなぎ（K-111）、画面のテストは2回流れ（K-112）、結合テストは1つの JVM で並べる口も絞る口も無く（K-113）、カバレッジの判定は結合テストの実行記録に依り（K-114）、CI は `verify` を1本流す（K-115）。Node 26 は `engines`・`checkToolchain`（`v26.`）・CI・README でそろっている（K-81 は解消。K-118）。`gradle/libs.versions.toml`・`gradle.lockfile`・`dependabot.yml` は今回読んでいない。
- 状態（2026-11-08）: at-risk（K-112〜K-114・K-116。今回の Intent で `verifyQuick`・`-PitTests`・`-PitForks` を足し、画面のテストを1回にする見込み） ／ 読みの深さ: 深い（一部。今回の `analyzed.components`）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`・`README.md` ／ 責務: WAR をコピーするだけのイメージ、`app` と profile で起動する監視・メールの受け手・見本の対象DB。読み取りのプールの環境変数は `.env.example` 66〜67 行（値は表示していない。`.env` は開いていない）。
- 今回読んだもの（K-78）: `compose.yaml` の `image:` 行だけ（otel-lgtm は `grafana/otel-lgtm:0.38.0@sha256:75cdf892…`（83 行）、Mailpit は `axllent/mailpit:v1.32.0@sha256:0790692b…`（121 行）。ほか otel-collector 0.163.2・postgres 18.6・mysql 26.10.0・mariadb 13.0.2）と、`README.md` の Mailpit・E2E に関わる記述の検索。動いているコンテナは `docker ps` / `docker images` の一覧での事実で、ダイジェストの一致は未検証。
- 今回（2026-11-01）読んだもの（K-82・K-83・K-88・K-93）: `Dockerfile`（全文）、`compose.yaml` の `image:`・profile・`mem_limit` の行、`README.md` の見出しの一覧と「前提の道具」「取得と準備」、版の固定の行の grep。手元のイメージの一覧は見ていない（K-93）。
- 2026-11-08 の追記: `README.md` は 164 行に縮み（K-88 の経緯の溜まりは README からは消えた。K-118）、配備の手順の本文は `docs/deployment.md` ほかへ移った。`Dockerfile`・`compose.yaml` は今回読んでいない。
- 状態: at-risk（K-88: README の最新化。K-93） ／ 読みの深さ: 深い（一部）

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験と使い捨ての環境、手元の監視、見本の対象DB。biz の場面は `bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail`・`bizSearchWide`（`perf/k6/scenarios.js` 160〜165 行）。種は `perf/biz/make-biz-seed.sh`（`biz_item` は 100 カラム × 既定 100,000 行、`c_001` に索引なし）、DSL は `perf/make-biz-dsl.mjs`、環境は `docker/perf/compose.yaml`。K-67〜K-71・K-73 と K-68 の持ち主（本文は `code-quality-assessment.md`）。`perf/dsl-mixed-memory.sh`（`memory.peak` の内訳の記録）、`perf/versions.env`（k6・JRE の版）、`perf/lib/`、`perf/ui/` を含む。
- 今回（2026-10-29）読んだもの（K-77）: `perf/README.md` の見出しの構成と「性能・資源の目標の見直し」の節（1322 行以降が今の線）、`perf/k6/scenarios.js` の閾値と `setup()`（`handleSummary` は未使用）、`perf/versions.env`、`docker/perf/compose.yaml` の `image:` 行。k6 の呼び出し（`--summary-export`）は README に約 15 か所と `perf/dsl-mixed-memory.sh` 312 行。前回までに読んだ `thresholdsFor` などは今回は再読していない（前回の記録による）。
- 今回（2026-11-01）読んだもの（K-83・K-89・K-90）: `perf/README.md` の見出しの一覧と「## 履歴」の節（1563〜1753 行）、`perf/versions.env`、`perf/redact-k6-evidence.mjs` の先頭（口と定数）、`docker/perf/compose.yaml`・`docker/e2e/compose.targetdb-port.yaml`・`docker/hikari-pool.sh` の版の行。`perf/k6/scenarios.js`（2,848 行）と `docker/` の残りは一覧と grep だけ。
- 状態: at-risk（K-69〜K-71・K-73・K-77・K-89・K-90） ／ 読みの深さ: 深い（一部）
