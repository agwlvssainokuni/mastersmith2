# 生成の記録（Generation Notes）— U4 ロール（role）

計画: `code-generation-plan.md`（同じディレクトリ、依頼者の Approve Plan で承認済み。13節の Q1〜Q9 はすべて A、D-11・D-12 も A）。生成の担当（開発担当）が手順ごとのコマンドと結果、計画との差を書き足す。パスはリポジトリのルートからの相対。

- 依頼の範囲（1回目の依頼）: B4 の Step 1〜12。Step 12 が通ったら止めて報告する。
- 長いコマンドは `caffeinate -i` で包み、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して流した。
- テストの件数は `backend/build/test-results/{test,integrationTest}/*.xml` を集計した実測の値（名指しの実行でも `:backend:cleanTest`・`:backend:cleanIntegrationTest` を付けた）。

## B4

### Step 1: 作業の場の用意

- ブランチ: `feature/261004-role-menu-b4`（指揮役が作成済み）。開始の時点の HEAD は `c581cfa712fb21412e7a00d2a5288cec910c1d7a`（「B4〜B6 role のコード生成の計画を承認」）。
- `git status --short`: アプリのソースに未コミットの変更なし。変更は `aidlc/spaces/default/intents/261004-role-menu/audit/sakura-local-4e42a93f87ce.md`（監査ログの追記。ワークフローの記録として外して判断）だけ。
- `frontend/playwright-report`・`frontend/test-results`: どちらも無い。

### Step 2: テストの実行の準備

- colima: `colima status` は running（macOS Virtualization.Framework、aarch64、docker、mountType sshfs）。`colima ssh -- ls <リポジトリ>/docker` でホームの共有が付いていることを確かめた（`check-container-limits.sh`・`hikari-pool.sh`・`jmx`・`monitoring`・`otel-collector`・`perf`・`targetdb`）。
- `unit-test-instructions.md` 2.1 のコマンド（`:backend:cleanTest :backend:cleanIntegrationTest` を付けた）: 単体 4 クラス・12 件、結合 2 クラス・14 件。失敗 0・飛ばし 0（exit 0）。
- 洗い出しの検索の流し直し（計画の 7.2・7.3 の一覧との増減）:
  - `onlyKnownFeaturesDependOnUser`・`dependsOnlyOnGroupServiceAndCommon`: `user/UserBoundaryArchitectureTest.java:83` と `role/RoleBoundaryArchitectureTest.java:54` の2件。計画どおり。
  - `containsOnlyKeys`（audit のテスト）: `audit/service/AuditEventListenerTest.java:777` の1件（起動時の出来事の項目。計画の 6節 A8 のとおり当たらない）。
  - `TODO(B5)`: `role/service/RoleGroupDeletionGuard.java:34` の1件（B5 で置き換える）。
  - `org.hibernate.orm.jdbc.error`: `application.yaml` の 350 行（コメント）と 359 行（`OFF`）。
  - `db/migration/`: V1〜V10 だけ（`V10__u3_group.sql` が最後）。

### Step 3: 変更の前の基準

- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`（23:21:35〜23:31:09、約 9 分 34 秒、exit 0）。コンパイルは開始の時点のソース（`c581cfa`）で行われた（Step 4 のファイルは、コンパイルが終わってテストを流している間に書いた。この実行の結果には入っていない）。
- 件数: 単体 201 クラス・1,737 件、結合 166 クラス・829 件。失敗 0・飛ばし 0（対象DB の3種類の結合テストも飛ばされていない）。
- カバレッジ（`backend/build/reports/jacoco/test/jacocoTestReport.xml`）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `audit.domain` | 99.7%（324/325） | 96.8%（91/94） |
| `audit.service` | 99.5%（206/207） | 84.6%（44/52） |
| `role.service` | 100.0%（8/8） | 100.0%（2/2） |
| 全体 | 98.5%（7,503/7,618） | 93.9%（2,795/2,978） |

- `backend/build.gradle.kts` の `packagesJudgedByTotal` は 7 パッケージのまま（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。B4 で手を入れるパッケージ（`role.*`・`audit.domain`・`audit.service`）は入っていない。
- 次の空き移行番号は V11（V1〜V10 だけがある）。計画の D-1 のとおり `V11__u4_role.sql` とする。

### Step 4: データの形 — 実装（移行とエンティティ）

- `backend/src/main/resources/db/migration/V11__u4_role.sql`: 計画の 4.5 のとおり `roles`（`uk_roles_name_key`）と `permission_settings`（`pk_permission_settings`・`fk_permission_settings_role`（削除の制限）・検査の制約4つ `ck_permission_settings_any`・`ck_permission_settings_level`・`ck_permission_settings_column_aux`・`ck_permission_settings_main`）を足した。`users.admin_flag` と既存の表・監査の表には触れていない。説明のコメントは日本語。
- `role.domain`: `Role`（JPQL のエンティティ名 `Role`、`rename`・`touch`（権限の保存などで更新の日時だけを書く）、toString は ID だけ）、`PermissionSetting`（`@IdClass`、主権限は `@Enumerated(STRING)`、全部が設定なしの値・カラムの補助権限では作らない・書き換えない、toString は ID と階層だけ）、`PermissionSettingId`、値の型 `PermissionTarget`（null の下位の名前を表では空の文字列で持つ）・`PermissionValues`・`MainPermission`・`PermissionLevel`。
- `Role` が `RoleName` を受けるため、`RoleName`・`RoleNameValidation`（計画の 7.2 では Step 6 の一覧）を Step 4 で作った。group の `GroupName` と同じ規則（D-30）。
- `application.yaml`: `org.hibernate.orm.jdbc.error: OFF` のコメントに role の理由（`uk_roles_name_key`・`pk_permission_settings`・`fk_permission_settings_role` の違反の文にロールの名前と ID・対象の名前が入る）を足した。値は変えていない（D-31）。
- 計画との差: `PermissionLevel`（階層の列挙）を足した（計画の 7.2 の一覧に無い小さな値の型。`entities.md` の「level（保存しない値）」）。`RoleName`・`RoleNameValidation` を作る手順を Step 6 から Step 4 に前倒しした（コンパイルのため。コミットの区切り C1 の中で変わらない）。

### Step 5: データの形 — テスト（止めた）

- 新しい: `role/RoleSchemaIT`（5 件。V11 が成功で当たり `users.admin_flag` が残る、制約の名前と列の長さ 128／256／8、検査の制約4つの拒否と受け付け、名前の鍵の一意・外部キー（削除の制限・無いロール）、エンティティの読み書きと JPQL の `Role`・`PermissionSetting`・素の `roles` の読み取り（6節 A4 は当たらなかった））、`role/RoleMigrationCompatibilityIT`（3 件。V1〜V10 だけを置いた前の版の Flyway が V11 まで当てた内部DB で止まらず実行 0 件、V11 の前の利用者の `admin_flag` と監査の行が変わらない、V11 の後に前の版の列だけで利用者と監査の行を足せる。前の版と今の版の移行はどちらも番号で絞って写した場所から読むため、B5 で V12 を足しても落ちない）。
- 書いた後の直し（自分のテストの誤り）: `INFORMATION_SCHEMA` に同じ名前の表 `ROLES`（H2 の役割の一覧）があるため、制約と列の長さの問い合わせを `TABLE_SCHEMA = 'PUBLIC'` で絞った。名前の鍵の重なりの確かめは、JPA では Hibernate の例外のまま上がるため、JDBC の挿入にした。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.role.RoleSchemaIT' --tests 'cherry.mastersmith.role.RoleMigrationCompatibilityIT' --tests 'cherry.mastersmith.audit.*'`
- 結果: 結合 18 クラス・84 件、失敗 2・飛ばし 0（exit 1）。`RoleSchemaIT`・`RoleMigrationCompatibilityIT` は通った。
- **止めた理由**: 既存の `audit/domain/AuditMigrationCompatibilityIT`（B3 で足した V10 の互換のテスト）の2件が落ちた。このテストは「今の版の移行の最後は V10」を前提に書かれている（`classpath:db/migration` をすべて当てたときの実行の数を 1、`targetSchemaVersion` を `"10"`、履歴の最大の版を 10 と決めている）。V11 を足すと、実行の数が 2、版が 11 になる。移行が壊れたのではなく、テストの前提（最新の版の番号）が古くなった。計画の影響の範囲（5節・6節）に無いテストのため、直さずに止めた。
  - 落ちた2件: `previousFlywayIgnoresTheFutureMigration`（期待 10、実際 11）、`existingRowsSurviveTheMigration`（期待 1、実際 2）。ほかの2件（V10 の後の前の版の追記、V10 の表）は通った。
- **依頼者の決定 B**（期待の値を書き換える）: `audit/domain/AuditMigrationCompatibilityIT` に定数 `CURRENT_LAST_VERSION = 11`（今の版の最後の移行）と `MIGRATIONS_AFTER_PREVIOUS`（= 11 − 9 = 2）を置き、2件の期待（履歴の最大の版・実行した移行の数・`targetSchemaVersion`）をこの定数で書いた。テストだけの変更で、本体は変えていない。
- 流し直し（同じコマンド）: 結合 18 クラス・84 件、失敗 0・飛ばし 0（exit 0）。
- 計画との差: 計画の影響の範囲に無い既存のテスト `AuditMigrationCompatibilityIT` を書き換えた（依頼者の決定 B）。**B5 で V12 を足すと同じテストがまた落ちるため、B5 で `CURRENT_LAST_VERSION` を 12 に書き換える必要がある**（`code-summary.md` の B5 への引き継ぎに書く事項）。

### Step 6: ドメイン — 実装（role.domain と audit の写し方）

- `role.domain`: `InheritedFrom`・`EffectivePermission`・`PermissionInheritance`（`resolve(level, schema, table, column)` で実効の値と継承の元を主権限・CREATE・DELETE で別に返す。`main`・`auxiliary` は式そのもの）・`PermissionTargetName`（1〜128 コードポイント、制御文字は `RoleName` と同じ範囲、前後の空白を取り除かない）・`PermissionNode`・`PermissionTreeBuilder`（`schemas`・`tables`・`columns`・`inCurrentDsl`。DSL の順の後に今の DSL に無い名前をコードポイントの順）・`RoleOperation`（B4 は `CREATE`・`RENAME`・`DELETE`・`CHANGE_PERMISSIONS`）・`RoleAuditEvent`・`RoleAuditFailure`・`RoleAuditDetail`（`Name`・`Rename`・`InUse`・`PermissionChanges`（`summarize(n)`）・`PermissionChangesSummary`、1件は `PermissionChange`）・`RoleRejection`・`RoleProblemTypes`（B4 の7つ）・`package-info`。
- `audit`: `AuditEventType` に4つ、`AuditFailureReason` に5つ（どれも 32 文字以内）。`AuditEventFactory.from(RoleAuditEvent)`・`roleEventTypeOf`・`roleFailureReasonOf`、`resultOf` の網羅の追従。`AuditDetailJson.ofRole(RoleAuditDetail)`（全件の JSON を作り、16,384 を超える権限の変更は要約。先頭は 20 件から上限に収まるまで減らす。D-11）。`AuditEventListener.onRoleAuditEvent` と組み立ての失敗の項目 `fields(RoleAuditEvent)`（対象のロール・グループ・detail は値があるときだけ。D-10）、`isDslOperation` の網羅の追従。
- 境界テスト: `RoleBoundaryArchitectureTest` を計画の 5節の B4 の規則（6つのテスト）に広げた。`UserBoundaryArchitectureTest` の「user に依存してよい機能」に `role` だけを足した（13節 Q1: A）。
- 計画との差:
  - `AuditDetailJson` の role の方法の名前を `of` ではなく `ofRole` にした。`of` を重ねると、既存の `AuditGroupEventFactoryTest` の `AuditDetailJson.of(null)` が型を決められずコンパイルできなくなるため（既存のテストを書き換えない）。
  - `RoleStore` を使う `role.service` のクラスの規則は「`RoleStore` を項目として持つ（注入される）のは `RoleStoreTransactions` だけ」として確かめる。業務処理は `inFirst` の引数の関数で `RoleStore` の方法を呼ぶため、型への依存そのものは業務処理にも出る（group と同じ形）。
  - B4 の業務の拒否の detail: ロールが無い拒否は detail なし、`DSL_NOT_APPLIED`・`PERMISSION_TARGET_NOT_IN_DSL`・保存と消すの `ROLE_NO_CHANGE` は `Name`（ロールの名前）。detail の JSON のキーは上の `ofRole` の説明のとおり（承認済みの文書にキーの名前が無いため、ここで決めた）。

### Step 7: ドメイン — テスト

- 新しい（`role/domain`）: `RoleNameTest`（16。`GroupNameTest` と同じ観点）、`RoleNameKeyPropertyTest`（6 性質。jqwik: 受け付けた名前は前後の空白なし・1〜64、規則どおりの受け付け、正規化のくり返し、大文字と小文字だけの違いは同じ鍵、名前 128・鍵 256 に収まる、全角と半角は別の鍵）、`PermissionTargetNameTest`（10）、`PermissionTargetTest`（4。計画の一覧に無い。対象の組の形・設定の行の値・主権限の名前の読み）、`PermissionInheritanceTest`（7。AC1.2.2 の例を含む）、`PermissionInheritancePropertyTest`（4 性質: 式に等しい・下位の明示が上書き・CREATE と DELETE が影響しない・カラムの補助権限はテーブルの値）、`PermissionTreeBuilderTest`（7）、`RoleAuditEventTest`（4）、`RoleAuditDetailTest`（2）、`RoleProblemTypesTest`（4）。
- 新しい（`audit`）: `audit/domain/AuditRoleEventFactoryTest`（5。種類と理由、32 文字以内、対象と detail、種類から結果を決められない）、`audit/domain/AuditRoleDetailJsonTest`（5。16,384 ちょうどは全件・1 超えは要約、要約は 20 件以下で長い名前（サロゲートペア 128 の3階層）なら減らして上限に収まる、エスケープ）、`audit/service/AuditRoleEventListenerTest`（5。確定の後、書き込みの失敗の ERROR に `targetRoleId`・detail、detail が上限を超えて組み立てに失敗した枝で `targetRoleId` が載る（B3 の引き継ぎ）、空の出来事）。
- 手伝い: `role/testsupport/RoleDslFixture`（版 2 の小さな DSL と名前に記号を含む DSL、`install`・`remove`）。
- `./gradlew :backend:spotlessApply` で書式をそろえた。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.role.domain.*' --tests 'cherry.mastersmith.audit.*'`
- 結果: 単体 24 クラス・210 件、失敗 0・飛ばし 0（exit 0）。単体テストの結果に `RoleNameKeyPropertyTest`（6）と `PermissionInheritancePropertyTest`（4）が出ている。
- 計画との差: `PermissionTargetTest` を足した（計画の一覧に無い小さな単体テスト）。

### Step 8: DB アクセス — 実装（role.repository・role.store）

- `role.repository`（`Repository` を継ぎ `@Query` の射影だけ）: `RoleRepository`（件数・ID の順の1ページ・1件・有無・名前の鍵）、`PermissionSettingRepository`（木の3つの階層の読み取りを各1回、保存の範囲の今の値、1つの対象の行）、射影 `RoleRowView`・`RoleView`・`PermissionLevelRow`・`PermissionSettingRow`。
  - 木の1段目とテーブルの階層は、子の名前ごとに「その子の階層の行の値（同じ表への左の外部結合）」と「その子の下の行の数」を1回の集計で読む。今の DSL に無いスキーマ・テーブルでも、下に設定があれば節になり（`hasChildren` も分かる）、消せるところまでたどれる。テーブルの階層の読み取りの名前が空の文字列の行は、継承に使うスキーマの行。カラムの階層はそのテーブルの行とスキーマの行とカラムの行を1回で読む。
- `role.store`（TraceAspect の対象の外）: `RoleStore`（`lockRole`（`PESSIMISTIC_WRITE`・ヒント 3,000 ms）・`insertRole`・`renameRole`・`deleteRole`（設定 → ロールの順）・`writePermissions`（後の値が設定なしなら行を消す、無ければ足す、あれば書き換え、ロールの `updatedAt` を書く）・`clearPermissions`（消した行の数を返す））、`RoleStoreOutcome`（toString は種類と排他の種類・相手だけ。D-5）、`Referent`、`RoleStoreClassifier`、`RoleStoreUnexpectedException`（文はクラスの名前・SQLState・role の既知の制約の名前だけ。原因を持たない。D-7）。上限切れは `RowLockFailures.warn`（`ROLE_ROW`・`ROLE_NAME_KEY`）。
- 計画との差:
  - 木の1段目の読み取りは、`performance-design.md` 1節の「スキーマなら `table_name = ''` の行」ではなく、そのロールの行を1回で集計して読む形にした。設定だけがある今の DSL に無いスキーマ（下の階層の行だけを持つもの）を1段目に出して消せるようにするため（BR4.10 の「今の DSL に無いスキーマ・テーブルの下も設定があれば返す」）。読み取りは1回のまま。
  - `AlreadyAssigned` の型は置いたが、割り当ての表が無いため区分（制約の名前）は B5 で足す。
  - 設定の主キーの違反は、ロールの行の排他で並ぶため API から届かない。届いたときは想定外（500）として、包み直した例外の文に `PK_PERMISSION_SETTINGS` を入れる（表の名前で見分ける）。

### Step 9: DB アクセス — テスト

- 新しい: `role/store/RoleStoreClassificationTest`（8。上限切れ、名前の鍵（制約の名前と文の両方）、ロールへの外部キー（23503・23506）、待った後の違反 #14、想定外（設定の主キー・検査の制約・ほかの SQLState・ほかの表）、包み直した例外が SQLState と既知の名前だけを持ち行の値（目印 `leakcheck_role_7f3a`・ID）を持たない、WARN が1件、toString が種類だけ）、`role/store/RoleStoreConstraintIT`（5。名前の鍵の待たない違反 #2、名前の鍵の上限切れ #3 と WARN `ROLE_NAME_KEY`（`UncommittedWrite`）、ロールの行の上限切れと WARN `ROLE_ROW`、消えたロールへの設定の書き込みが `Referenced(ROLE)`、通常の書き込み）、`role/repository/RoleRepositoryIT`（5。ID の順・20 件・1件・有無・名前の鍵、1段目は下の階層だけを持つ今の DSL に無いスキーマも行になる、テーブルの階層の空の名前の行はスキーマの行・大文字と小文字を区別、カラムの階層は1つのテーブルの分だけ、保存の範囲と1つの行）。
- 手伝い: `role/testsupport/RoleFixtures`（ロールと設定の行を本番の書き込みを通さずに作る）。group の `UncommittedWrite` を使い回した。
- 書いた後の直し（自分のテストの誤り）: `RoleRepositoryIT` の総称の読み取りの手伝いが AssertJ の `assertThat` の重ね定義と型推論でぶつかりコンパイルできなかったため、手伝いを外して repository を直接呼ぶ形にした。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.role.store.*' :backend:integrationTest --tests 'cherry.mastersmith.role.store.*' --tests 'cherry.mastersmith.role.repository.*'`
- 結果: 単体 1 クラス・8 件、結合 2 クラス・10 件。失敗 0・飛ばし 0（exit 0）。上限切れの2件は行 3 秒・鍵 約 2 秒を実際に待つ（合否は結果の型と WARN で決める）。

### Step 10: 業務処理 — 実装（role.service）

- `RoleStoreTransactions`（`inFirst`。`Done` 以外で必ず `setRollbackOnly()`。`RoleStore` を持つのはこのクラスだけ）、`RoleFirstStep`（`Done`・`Rejected`・`Store`。toString は種類だけ。D-4・D-5）、`RoleBarrier`・`NoOpRoleBarrier`（4つの点）、`RoleProblemTypeCatalog`。
- `RoleAdminService`: 作成・名前の変更・削除・権限の保存・消す・一覧・1件・木の3つ。変える操作は 入力 → 排他 → `afterLock` → 拒否の判定 → `afterCheck`（作成・名前の変更）→ 書き込み → `afterWrite` → 成功の出来事。拒否と違反の読み替えは1つ目を巻き戻して2つ目で失敗の出来事（計画の 4.3、13節 Q2: A）。`Busy` は出来事なし。保存は排他の後に DSL を読み、`DSL_NOT_APPLIED` → 値を設定する対象が今の DSL に無い（`PERMISSION_TARGET_NOT_IN_DSL`）→ 変わる対象が無い（`ROLE_NO_CHANGE`）の順で、変わった対象だけを書き、前後の値を detail に入れる。消す操作は今の DSL にある対象を入力の誤り（400、トランザクションの前）にし、行が無ければ `ROLE_NO_CHANGE`。木は読み取りだけの1つのトランザクションで、ロールの有無 → DSL の有無の順。
- 結果の型: `RoleCreateResult`・`RoleChangeResult`（`InvalidName`・`InvalidInput(field, reason)`・`Rejected`・`InUse`・`Busy`・`Done`）・`RoleListResult`・`RoleDetailResult`・`PermissionTreeResult`・`RoleDetail`。どれも toString は種類・ID・件数だけ。
- 入力の判定 `PermissionInputs`（保存の `scope` と `entries`、消すの `targets`。最初に当たった項目の名前と理由だけを返す）。
- 計画との差:
  - 保存の範囲の読み方を決めた（承認済みの文書に細かい決まりが無いため）: スキーマの範囲はそのスキーマの対象だけ、テーブルの範囲はそのテーブルとそのカラム（画面 S4 の1つの表の分）。`entries` が空は 400（`REQUIRED`）。
  - 入力の判定を `role.service` の純粋な部品 `PermissionInputs` に置いた（web はこれを呼んで 400 の項目に写す）。
  - B4 のロールの一覧の `userCount`・`groupCount` は 0（割り当ての表を足す B5 で数える。D-37 と同じ扱い）。

### Step 11: 業務処理 — テスト

- 新しい: `role/service/RoleAdminServiceTest`（14。入力の誤りでトランザクションなし、作成の順と出来事、重なり（事前の判定と違反の両方）、`Busy` で出来事なし、名前の変更の判定の順 BR3.6、大文字と小文字だけの変更と4つの点の順、行と鍵の上限切れ・違反、削除と `Referenced(ROLE)` の `ROLE_IN_USE`、DSL が無い・今の DSL に無い対象、差分だけの書き込みと detail・`ROLE_NO_CHANGE`、今の DSL に無い対象を設定なしへ戻すのは受け付ける・書き込みの上限切れ、消す操作、一覧、1件と木）、`PermissionInputsTest`（5。計画の一覧に無い）、`RoleProblemTypeCatalogTest`（3）、`RoleStoreTransactionsIT`（3。違反で例外なく巻き戻り2つ目の失敗の出来事が監査の行になる・`UnexpectedRollbackException` との比べ）、`RoleAdminQueryCountIT`（2。一覧はロール 1 件と 26 件で同じ2つの文、木の3つの階層はどれもロールの有無と設定の読み取りの2つの文で、設定 1 行と 120 行で同じ）。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.role.service.*' :backend:integrationTest --tests 'cherry.mastersmith.role.service.*'`
- 結果: 単体 4 クラス・24 件（既存の `RoleGroupDeletionGuardTest` を含む）、結合 2 クラス・5 件。失敗 0・飛ばし 0（exit 0）。
- 計画との差: `PermissionInputsTest` を足した。

### Step 12: API — 実装（role.web）

- `RoleAdminController`（`/api/admin/roles`: 一覧・作成・1件・名前の変更・削除）と `RolePermissionController`（`/api/admin/roles/{roleId}/permissions`: `schemas`・`tables?schema=`・`columns?schema=&table=`・PUT・`clear`）の 10 の口。どちらもクラスに `@ApiAccess(ADMIN)`。結果の型を `switch` で尽くして `BusinessException` に変える。`ROLE_IN_USE` の応答に `assignedUsers`・`assignedGroups`（B4 では起きない）。木の名前は `@RequestParam` で受け、欠け・空は 400、長さで拒否しない（D-14）。0 以下の ID は業務処理が無いロールとして 404（D-13）。
- DTO: `RoleNameRequest`・`PermissionSaveRequest`・`PermissionTargetRequest`（`scope` と消すの1件）・`PermissionEntryRequest`・`PermissionClearRequest`・`RoleResponse`・`RolePageResponse`・`RoleRowResponse`・`PermissionNodesResponse`・`PermissionNodeResponse`。`RoleFieldErrors`（`GroupFieldErrors` と同じ形）、`RoleRequestContextResolver`（`GroupRequestContextResolver` と同じ形、D-32）。
- 計画との差（承認済みの文書で決まっていなかった点。承認の場で確かめる）:
  - 木の節の `displayName` は、DSL の表示名（`ja`・`en`）を要求の Accept-Language（既存の `AcceptLanguageResolver`、既定は日本語）で選んだ文字列にした。今の DSL に無い節は物理名。契約 C7 と U6 の型は文字列で、画面の ApiClient は画面の言語を Accept-Language に付けて送るため。U5 navigation は `{ja, en}` の組で返す形に決めているため、形が単位の間で揃っていない。
  - 応答の `inheritedFrom` は主権限の継承の元（契約 C7 の1つの項目）。CREATE・DELETE の継承の元はドメインでは別に求めているが、応答には載せていない。
  - 計画の 7.2 の `PermissionEntryRequest`・`PermissionClearRequest` のほかに、`scope` と消す対象の1件の共通の型 `PermissionTargetRequest` を足した（`scope` の `columnName` は 400）。
- 確かめ（Step 14 の前の確認。Step 14 の記録ではない）: `./gradlew :backend:spotlessApply :backend:compileJava` を通した。構造の検査（`*BoundaryArchitectureTest`・`ArchitectureTest`・`ApiAccessArchitectureTest`・`ApiAccessRulesTest`・`PublicApiInventoryTest`）は単体 18 クラス・92 件、`ApiAccessConsistencyIT` は結合 1 クラス・5 件で、どれも失敗 0。1回目は自分の `RoleBoundaryArchitectureTest` の repository の規則が、射影の record の項目の方法（`updatedAt()`・`delete()`）まで書き込みの名前として拾って落ちたため、規則を Spring Data の口（interface）に絞った。

### Step 13: API — テスト（結合）

- 手伝い（`role/testsupport`）: `TestRoleBarrier`（group の `TestGroupBarrier` と同じ `hold`・`signal`、上限 20 秒、`@Primary`）、`RoleActors`（管理者・管理者の印を持たない利用者・停止中の管理者。本番の作成の口、`example.com`）、`RoleApi`（ID は文字列でも受け、木の名前は問い合わせの引数としてエンコードする）。
- 新しい（`role/web`、件数）: `RoleAdminApiIT`（6）、`RolePermissionApiIT`（7。3つの階層の形と値・言語ごとの表示名、記号を含む名前と長い名前で 400 にならない、引数の欠け・空は 400、DSL が無い、差分の保存と読み直し、入力の誤り、今の DSL に無い対象と消す）、`RoleAdminAuthorizationApiIT`（40 = 10 の口 × 未認証 401・印なし 403・管理者・停止中の管理者 401。拒否の後に状態が変わらない）、`RoleMassAssignmentApiIT`（3）、`RoleIdorApiIT`（2。存在しない・0・-1 の ID で 8 つの口が 404、変える操作の4つだけが要求の ID で FAILURE、状態が変わらない）、`RoleConcurrencyIT`（1。#7）、`RoleConflictAuditIT`（2。#2 作成と名前の変更）、`RoleBusyApiIT`（2。#3 名前の鍵、#5 ロールの行で保存と名前の変更）、`RoleBusyLogTraceIT`（1。`ROLE_ROW`・`ROLE_NAME_KEY` の WARN が traceId ごとに1件、決めた項目だけ）、`RoleAuditIT`（3）、`RoleAuditWriteFailureIT`（4）、`RoleMetricsIT`（1）、`RoleConnectionUsageIT`（3。書き込み 2 本・読み取り 1 本・拒否 2 本・`ROLE_BUSY` 1 本・違反の負けた側 2 本）、`RoleUniqueViolationSecretLeakIT`（2。INFO と TRACE）、`RoleSecretLeakIT`（1）。
- 書いた後の直し（自分のテストの誤り）: `RolePermissionApiIT` の総称の型の書き方でコンパイルが通らなかった所を直した。`RoleSecretLeakIT` の「結果の型は種類だけ」の確かめが、値を持たない `RoleCreateResult.Rejected`（理由の列挙だけ）まで拾ったため、`RoleStoreTransactions#inFirst` の EXIT の行が種類だけ（`Done`・`Rejected[NAME_DUPLICATE]`）であることと、`Done[value=` が無いことを確かめる形に直した。
- コマンド: `caffeinate -i ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.role.*' --tests 'cherry.mastersmith.audit.service.*'`（00:13:04〜00:14:18）
- 結果: 結合 34 クラス・159 件、失敗 0・飛ばし 0（exit 0）。
- 計画との差:
  - `RoleAuditIT`・`RoleAuditWriteFailureIT` は、group と違って `audit/service` ではなく `role/web` に置いた（role の API を通す結合テストのため）。
  - `RoleConcurrencyIT` の #7 は、後の側が `ROLE_BUSY` になった場合も負けの code として受け入れ、その場合は値と監査の確かめをしない（`reliability-design.md` 4.2 の注）。今回の実行では両方 204 だった。
  - `RoleSecretLeakIT` の B4 の分は、操作した管理者のメールアドレス・パスワード、結果の型の文字列、応答と監査の行のハッシュ値を確かめる。B4 の口は利用者の氏名・メールアドレスを扱わない（割り当ての一覧は B5）。

### Step 14: 境界と構造の検査

- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.*BoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.ApiAccessArchitectureTest' --tests 'cherry.mastersmith.ApiAccessRulesTest' --tests 'cherry.mastersmith.PublicApiInventoryTest' :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT'`
- 結果: 単体 18 クラス・92 件（`RoleBoundaryArchitectureTest` の6規則、`UserBoundaryArchitectureTest` を含む）、結合 1 クラス・5 件。失敗 0・飛ばし 0（exit 0）。B2 の静的な検査と実行時の検査で、10 の口の `ApiAccess(ADMIN)` の印と `/api/admin/**` が合っている。

### Step 15: 書式とバックエンドの全体のテスト

- コマンド: `./gradlew :backend:spotlessApply` の後に `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:spotlessCheck :backend:test :backend:integrationTest`（colima の環境変数を付けた。00:15:24〜00:25:39）
- 結果: 単体 218 クラス・1,851 件、結合 187 クラス・930 件。失敗 0・飛ばし 0（exit 0）。Step 3 の基準（単体 201・1,737、結合 166・829）から単体 +17 クラス・+114 件、結合 +21 クラス・+101 件。

### Step 16: op のタグの確かめ

- ホームの下の一時の場所（権限 700、終わった後に消した）に、要求を送らない最小の台本（`shared-iterations` 6 回、繰り返しごとに `exec.vu.metrics.tags.op` を `a`・`b` と交互に入れる。閾値に `iteration_duration{scenario:x,op:a}`・`{…,op:b}`）を置き、`docker run --rm grafana/k6:2.3.0 run --summary-export …` で流した（exit 0）。
- 結果: 要約に `iteration_duration{scenario:x,op:a}`・`iteration_duration{scenario:x,op:b}` の系列がそれぞれ値を持って出た（タグなしの `iteration_duration` と別）。op のタグは `iteration_duration` に付く。そのため、操作ごとに場面を分ける名前（`roleAdminOpsCreate` など）は使わない（2つ目のチェックは「付かなければ」の分岐のため、確かめの結果として閉じた）。

### Step 17: k6 の場面と手順

- `perf/k6/scenarios.js`: 先頭のコメント、`ROLE_API`・`ROLE_SCENARIOS`（`roleTreeRead`・`rolePermissionSave`・`roleAdminRead`・`roleAdminOps`）・`ROLE_OPS`・`ROLE_DURATION`（既定 3m）・`ROLE_WORST_NAME`・`ROLE_TREE_SCHEMA`・`ROLE_TREE_TABLE`、`scenariosFor`（`constant-vus`）、`thresholdsFor`（op ごとの `iteration_duration` の p95 < 1000、`checks` の率 1、`http_req_duration{scenario:…}` は `max>=0`）、`setupTimeout` 10m、`setupRoles`、4つの場面の関数を足した。既存の場面は変えていない。
- `perf/README.md`: 末尾に「ロールの管理の場面（Intent 261004-role-menu の U4）」の節（場面の表・形・データ・トークン・名前・流し方）を足した。
- `k6 inspect --include-system-env-vars`（`grafana/k6:2.3.0`、場面ごとに `-e SCENARIO=`）: 4つの場面とも `constant-vus`・10 VU・3m0s、閾値は上の形、`setupTimeout` 10m0s。
- 計画との差:
  - `roleAdminRead` は B4 の口だけ（一覧の1ページ目・最後のページ・1件）にした。グループのロールと利用者のロールの読み取り（`roleAdminReadGroupRoles`・`roleAdminReadUserRoles` に当たる op）は B5 で足す。
  - 悪い側のデータ（`perf-role-worst`・スキーマ `perf`・テーブル `perf_t001`）は B6 の準備の台本が入れる前提で、名前は環境変数で替えられる。準備の台本が決まった時点で既定の名前をそろえ直す必要がありうる（B6 への引き継ぎ）。
  - 操作する管理者 `perf-roleop01` を新しく使う（グループの `perf-graop01` と分けた）。手順 2'' の SQL の形で入れる。

### Step 18: 文書

- README に「ロールと権限の API（Intent 261004-role-menu の U4、Bolt B4）」の節（10 の口の表、名前・木の節・主権限・保存の範囲・本文・DSL が無いとき・同時の操作（行 3 秒、一意の鍵 約 2 秒、`ROLE_NAME_DUPLICATE` と `ROLE_BUSY` に分かれること）・監査・指標）を、グループの管理の API の節の後に足した。「スキーマの変更（Flyway）」に V11、「監査ログ（U4）」に ROLE_ の4種類と失敗の理由と `detail` のキー（要約の形を含む）を足し、V10 の対象の列の説明の「後の Bolt の U4 で使います」を「U4 の B4 から使います」に直した。設計の文書は書き換えていない。
- 書いた中身は、`RoleProblemTypes`（状態コード）・`PermissionInputs`（範囲・重なり・カラムの補助の権限）・`AuditDetailJson.ofRole`（キー）・`RoleAdminService`（DSL が無いときの拒否は木と保存だけ）で確かめた。

### Step 19: 取り残しと変えないものの確かめ

- `git diff --stat develop -- backend/build.gradle.kts build.gradle.kts gradle/libs.versions.toml backend/gradle.lockfile frontend .github compose.yaml docker Dockerfile vendor backend/src/main/java/cherry/mastersmith/common backend/src/main/java/cherry/mastersmith/access backend/src/main/java/cherry/mastersmith/auth backend/src/main/java/cherry/mastersmith/audit/repository`: 差なし（`packagesJudgedByTotal`・計測の除外・依存・lockfile も変わらない）。
- `git diff develop -- backend/src/main/resources/application.yaml`: `org.hibernate.orm.jdbc.error` の説明のコメント4行だけ。
- `class …Properties` は role の本体・テストに無い（既存の `InvitationTestProperties` は develop にあるもの）。`role/testsupport` の5つの手伝いの外の新しいテストのクラスは、どれも `Test` か `IT` で終わる。`V11` は `users.admin_flag` を変えない。

### Step 20: 記録

- 1ファイルずつ書いた: `traceability.json`（機能設計の AC 127・決まり BR 92・NFR 設計の NFR 44 の計 263 行。OK 100・Deferred 159・N/A 4。B4 で一部だけ受け持つ行は OK の target に「部分」と書き、B5・B6・U5〜U7・Performance Validation・Observability Setup の分は Deferred）→ `source-manifest.json`（`aidlc/` の外の作った・変えたファイル 120。テストと README・負荷の道具を含む）→ `code-summary.md`（「B4 ロールと権限の設定」の節。冒頭にコミットは依頼者の承認を得て計画 3節の区切りで行うこと、作ったもの・決めたこと・テストの量（Step 21・22 の後に書いた）・計画との差 B4-1〜B4-15・承認の場で確かめたいこと・後に回すこと・B5 への引き継ぎ・`curl` の手順）。
- 計画との差: AC1.2.18（応答時間）は計画 10.1 では B4 の手順に入るが、流して判定するのは Performance Validation のため Deferred にした（B4-15）。

### Step 21: 1コマンドの検査

- colima: `colima status` で動いていること（mountType sshfs）、`colima ssh -- ls "$HOME"` でホームの共有を確かめた。README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けた。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（BUILD SUCCESSFUL in 12m 21s、exit 0）。
- 件数: バックエンドの単体 218 クラス・1,851 件、結合 187 クラス・930 件、失敗 0・飛ばし 0（対象DB の3種類の SKIPPED 0 件、「コンテナの実行環境」の警告 0 件）。画面 113 ファイル・1,043 件 passed（`frontend/` に触れていない）。単体の結果に `RoleNameKeyPropertyTest`・`PermissionInheritancePropertyTest` がある。
- 時間: 12 分 21 秒。前の Bolt（B3）の verify の 10 分 46 秒から約 1 分 35 秒延びた（Step 3 は verify ではなく test・integrationTest・jacoco の 9 分 34 秒）。
- カバレッジ（Step 3 → B4）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `audit.domain` | 99.7% → 99.5%（411/413） | 96.8% → 94.8%（128/135） |
| `audit.service` | 99.5% → 99.6%（226/227） | 84.6% → 84.5%（49/58） |
| `role.domain` | 96.5%（390/404） | 88.0%（212/241） |
| `role.repository` | 100.0%（20/20） | 100.0%（2/2） |
| `role.store` | 97.2%（141/145） | 85.4%（41/48） |
| `role.service` | 100.0% → 96.3%（389/404） | 100.0% → 89.1%（155/174） |
| `role.web` | 90.9%（140/154） | 79.2%（38/48） |
| 全体 | 98.5% → 98.2%（8,682/8,845） | 93.9% → 92.8%（3,283/3,536） |

- どのパッケージも下限（行 80%・分岐 70%）を満たす。`packagesJudgedByTotal` は変えていない。

### Step 22: E2E

- `docker compose --profile mail up -d mailpit`（すでに Running。作り直していない）の後に、colima の環境変数を付けて `caffeinate -i ./gradlew e2eTest` を流した。
- 1回目（BUILD FAILED in 7m 8s、exit 1）: 177 件のうち 176 passed・1 failed。落ちたのは `e2e/135-dsl-admin-accessibility.e2e.ts` の `dsl admin green dark 360px` で、`loginAsAdmin` の最初の `getByTestId('login-layout')` が 5 秒で見えなかった（画面の写しは `status` の要素だけで、読み込みの途中に見えた。並行してリフレッシュの要求が 401 を返し、テストの終わりに `route.fetch: Test ended` が出た）。B4 は `frontend/` と DSL の管理の口に触れていない。報告の確かめの道具は「残してはならない値は含まれていません（値の種類 7・json の報告 1・添付 299・失敗の画面の写し 1・見つかった件数 0）」。
- 再現の試み（上限を「全体の1回の流し直し」と先に決めた）: 同じコマンドで全体を流し直し、177 passed（6.8m、BUILD SUCCESSFUL in 6m 50s、exit 0）。報告の確かめの道具は「含まれていません（失敗の画面の写し 0・見つかった件数 0）」。
- 扱い: 手元で再現しなかったため、`team.md` の Testing Posture の「不安定なテストと CI の失敗」の決まりの2つ目（再現しないもの）として、不安定と確かめられていない扱いで記録した。見立て（未検証）は、ログインの画面の描画がリフレッシュの要求（401）の応答を待つ間に 5 秒を過ぎた、PC の負荷による一時的な遅れ。同じテストが二度目に落ちたら、原因を直すまで進めない。統合に進めるかは指揮役・依頼者の判断に委ねる。
- 片付け: `rm -rf frontend/playwright-report frontend/test-results`（git の対象外）。

## B5

- 依頼の範囲: B5 の Step 24〜34（業務処理のテストまで）。Step 34 が通ったら止めて報告する。
- 長いコマンドは `caffeinate -i` で包み、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して流した。件数は `backend/build/test-results/{test,integrationTest}/*.xml` を集計した実測の値（名指しの実行でも `:backend:cleanTest`・`:backend:cleanIntegrationTest` を付けた）。

### Step 24: 作業の場の用意

- ブランチ: `feature/261004-role-menu-b5`（指揮役が作成済み）。開始の時点の HEAD は `3d4f344fef05311408efab936e46a32eed3d183f`（B4 の squash の統合）。
- `git status --short`: アプリのソースに未コミットの変更なし。変更は監査ログ（`aidlc/spaces/default/intents/261004-role-menu/audit/sakura-local-4e42a93f87ce.md`）の追記だけ（ワークフローの記録として外して判断）。
- `frontend/playwright-report`・`frontend/test-results`: どちらも無い。

### Step 25: テストの実行の準備

- colima: `colima status` は running（macOS Virtualization.Framework、aarch64、docker、mountType sshfs）。`colima ssh -- ls <リポジトリ>/docker` でホームの共有が付いていることを確かめた。
- `unit-test-instructions.md` 2.2 のコマンド: 単体 16 クラス・102 件、結合 3 クラス・39 件。失敗 0・飛ばし 0（exit 0）。
- 洗い出しの検索の流し直し:
  - `git grep -n 'TODO(B5)' -- backend`: `role/service/RoleGroupDeletionGuard.java:34` の1件（計画どおり）。
  - `git grep -n 'GroupDeletionGuard' -- backend/src`: 本体は `group/service/GroupDeletionGuard`・`GroupAdminService`・`role/service/RoleGroupDeletionGuard`、テストは `TestGroupDeletionGuard` を `@Import` する group・audit の結合テスト 13 クラスと `GroupAdminServiceTest`・`RoleGroupDeletionGuardTest`。計画の影響の範囲と増減なし。
  - `git grep -n -e 'FK_GROUP_MEMBERS_GROUP' -- backend/src/main`: `group/store/StoreFailureClassifier.java:75` の1件（Q4: A で足す場所）。
  - `git grep -n -e 'nonAdmin' -- …/GroupAdminAuthorizationApiIT.java`: 0 件。403 の主体は列挙 `Subject.MEMBER`（管理者の印を持たない利用者）で表されている（Step 36 で置き換える所）。

### Step 26: 変更の前の基準

- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`（01:22:55〜01:33:22、BUILD SUCCESSFUL in 10m 27s、exit 0）。コンパイルは開始の時点のソース（`3d4f344`）で行われた（Step 27 のファイルはテストを流している間に書いた。この実行の結果には入っていない）。
- 件数: 単体 218 クラス・1,851 件、結合 187 クラス・930 件。失敗 0・飛ばし 0（B4 の Step 21 と同じ）。
- カバレッジ（`backend/build/reports/jacoco/test/jacocoTestReport.xml`）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `role.domain` | 96.5%（390/404） | 88.0%（212/241） |
| `role.repository` | 100.0%（20/20） | 100.0%（2/2） |
| `role.store` | 97.2%（141/145） | 85.4%（41/48） |
| `role.service` | 96.3%（389/404） | 89.1%（155/174） |
| `role.web` | 90.9%（140/154） | 79.2%（38/48） |
| `audit.domain` | 99.5%（411/413） | 94.8%（128/135） |
| `audit.service` | 99.6%（226/227） | 84.5%（49/58） |
| `group.store` | 97.8%（91/93） | 94.6%（35/37） |
| 全体 | 98.2%（8,682/8,845） | 92.8%（3,283/3,536） |

- 次の空き移行番号は V12（V1〜V11 だけがある）。計画の D-1 のとおり `V12__u4_role_assignment.sql` とする。

### Step 27: データの形 — 実装

- `backend/src/main/resources/db/migration/V12__u4_role_assignment.sql`: 計画の 4.5 のとおり `user_role_assignments`（`pk_user_role_assignments`・`fk_user_role_assignments_role`・`fk_user_role_assignments_user`・`ix_user_role_assignments_user_id`）、`group_role_assignments`（`pk_group_role_assignments`・`fk_group_role_assignments_role`・`fk_group_role_assignments_group`・`ix_group_role_assignments_group_id`）、`work_role_selections`（`pk_work_role_selections`、`role_id` は外部キーなし、`fk_work_role_selections_user`）。外部キーはどれも削除の制限。`users.admin_flag` と既存の表・監査の表には触れていない。説明のコメントは日本語。
- `role.domain`: `UserRoleAssignment`・`UserRoleAssignmentId`・`GroupRoleAssignment`・`GroupRoleAssignmentId`・`WorkRoleSelection`（JPQL のエンティティ名は計画の D-2 のとおり。toString は ID だけ）。

### Step 28: データの形 — テスト（`MERGE` の確かめを含む）

- 足した: `role/RoleSchemaIT` に4件（V12 の適用と制約・索引の名前、割り当ての主キーと外部キー（ロール・利用者・グループ、ロールとグループの削除の制限）、作業ロールの保存の主キーと利用者への外部キー・ロールへの外部キーが無いこと、3つのエンティティの JPQL での読み書き）、`role/RoleMigrationCompatibilityIT` に3件（V1〜V10 の前の版と V1〜V11 の B4 の版の Flyway が V12 まで当てた内部DB で止まらない、V12 の前の利用者・ロール・監査の行が変わらず `admin_flag` が残る、V12 の後に前の版の列だけで書ける）、新しい `role/store/WorkRoleSelectionMergeIT`（3件）。
- B4 の引き継ぎ（依頼者の決定 B）: `audit/domain/AuditMigrationCompatibilityIT` の `CURRENT_LAST_VERSION` を 12 に書き換え、説明文を直した（`MIGRATIONS_AFTER_PREVIOUS` は差の式のため 3 に追従）。テストだけの変更。
- **`MERGE` の確かめの結果（NFR 設計の読み直しの R-15、計画の D-19）**: 別の接続が同じ利用者の保存の行を未確定のまま持つ間に `MERGE INTO work_role_selections (user_id, role_id, updated_at) KEY (user_id) VALUES (…)` を流した。
  - 未確定の挿入に対して: 待った後に SQLState `HYT00`・誤りの番号 50200（待ちの上限切れ。`RowLockFailures.isLockFailure` が真）。待ちは約 4,016 ms（探りの1回の実測。合否には使わない）。
  - 未確定の書き換えに対して: 同じく `HYT00`・50200。待ちは約 2,006 ms。
  - 先を確定させた後の `MERGE` は書き換えになり、誤りにならない（行は1つ、値は後の値）。
  - 23505・上限切れ以外の誤りは出なかったため、**store の保存の形は `MERGE INTO … KEY` のまま**とした（D-19 の「読んでから更新か挿入」には切り替えない）。区分は `Busy("WORK_ROLE_SELECTION_KEY")`。テストは H2 を上げたときの見張りとして、上限切れ（50200）であることを確かめる形に絞った（探りの出力は消した）。
- コマンド: `caffeinate -i ./gradlew :backend:spotlessApply :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.role.RoleSchemaIT' --tests 'cherry.mastersmith.role.RoleMigrationCompatibilityIT' --tests 'cherry.mastersmith.role.store.WorkRoleSelectionMergeIT' --tests 'cherry.mastersmith.audit.domain.AuditMigrationCompatibilityIT'`
- 結果: 結合 4 クラス・22 件、失敗 0・飛ばし 0（exit 0）。1回目は自分のテストの誤り（`SQLException` が `Iterable` のため AssertJ の `assertThat` があいまいでコンパイルできない）で止まり、`(Object)` に直した。
- 計画との差: `unit-test-instructions.md` 3.2 の Step 28 のコマンドに、B4 の引き継ぎで書き換えた `AuditMigrationCompatibilityIT` を名指しで足して流した。

### Step 29: ドメイン — 実装

- `role.domain`: `RoleOperation` に `ASSIGN`・`UNASSIGN`・`SWITCH_WORK_ROLE`、`RoleAuditFailure` に `ROLE_NOT_ASSIGNED`・`USER_NOT_FOUND`・`GROUP_NOT_FOUND`、`RoleRejection` に `NOT_ASSIGNED`・`USER_NOT_FOUND`・`GROUP_NOT_FOUND`、`RoleProblemTypes` に `ROLE_NOT_ASSIGNED`（409。`of` は `USER_NOT_FOUND` を user の、`GROUP_NOT_FOUND` を group の定義に写す。`all()` には足さない。BR13.2）、`RoleAuditDetail` に `Assignment(roleName, groupName)`・`WorkRoleSwitch(fromRoleId, fromRoleName, toRoleName, storedBeforeRoleId)`、`RoleAuditEvent` に対象の利用者・グループを持つ `succeeded`・`failed` を足した（B4 の口はそれを呼ぶ形）。新しい値の型 `WorkRoleRef`・`RoleSource`（`Direct`・`Group`）・`UserRoleView`、純粋な関数 `WorkRoleChooser`、写し `PermissionSnapshot`（作業ロールの ID と行の数だけの toString。D-5）。
- `audit`: `AuditEventType` に `ROLE_ASSIGNED`・`ROLE_UNASSIGNED`・`WORK_ROLE_SWITCHED`、`AuditFailureReason` に `ROLE_NOT_ASSIGNED`（どれも 32 文字以内）。`AuditEventFactory.roleEventTypeOf`・`roleFailureReasonOf`・`resultOf`、`AuditEventListener` の網羅の `switch` の追従、`AuditDetailJson.ofRole` に2つの形（キーは `roleName`・`groupName`、`fromRoleId`・`fromRoleName`・`toRoleName`・`storedBeforeRoleId`。無い値は null）。
- 計画との差:
  - 割り当ての外のロールへの切り替えの拒否（`ROLE_NOT_ASSIGNED`）は、ロールが無い拒否と同じく detail を持たない形にした（`RoleAuditEvent` の組み合わせの検査に足した）。存在しないロールでは名前が無く、割り当ての外のロールの名前を残す理由も無いため。`entities.md` の RoleAuditDetail の一覧に拒否の形が無かった。
  - `RoleAuditFailure` の `USER_NOT_FOUND`・`GROUP_NOT_FOUND` は監査の既存の値に写す（新しい監査の失敗の理由は `ROLE_NOT_ASSIGNED` だけ。計画の 4.6 のとおり）。

### Step 30: ドメイン — テスト

- 新しい（`role/domain`）: `WorkRoleChooserTest`（7）、`WorkRoleResolutionPropertyTest`（4 性質。jqwik: 結果は集合の中か無し・保存が集合にあればそれ、無ければ最小の ID・明示で選んだ後は作業ロール以外の足し外しで変わらない（AC4.1.21）・ID の順に依らない）、`PermissionSnapshotTest`（5。継承の関数と同じ値、DSL に無い名前は NONE、作業ロール・DSL が無ければ NONE、toString に名前が出ない、写しを持つ・引数の組の検査）。
- 足した: `RoleAuditDetailTest`（+2）、`RoleAuditEventTest`（+2）、`RoleProblemTypesTest`（`ROLE_NOT_ASSIGNED` と使い回しの code の写し・`all()` に重ねない）、`audit/domain/AuditRoleEventFactoryTest`（+3。既存の期待の書き換え: ROLE_ で始まる種類 4→6、失敗の理由の一覧に3つ、`resultOf` の種類に3つ）、`audit/domain/AuditRoleDetailJsonTest`（+1）、`audit/service/AuditRoleEventListenerTest`（+2）。
- コマンド: `caffeinate -i ./gradlew :backend:spotlessApply :backend:cleanTest :backend:test --tests 'cherry.mastersmith.role.domain.*' --tests 'cherry.mastersmith.audit.*'`
- 結果: 単体 27 クラス・236 件、失敗 0・飛ばし 0（exit 0）。単体テストの結果に `WorkRoleResolutionPropertyTest`（4）が出ている（B4 の2つの `*PropertyTest` もある）。

### Step 31: DB アクセス — 実装

- `role.repository`（`Repository` を継ぎ `@Query` の射影だけ）: `RoleAssignmentRepository`（利用者の直接のロールをロールの名前を結合して ID の順、グループの ID の集合でグループのロールを名前を結合して1回、ロールの利用者・グループの ID、組の有無、ロールの ID の集合で数（一覧）、1つのロールの数（削除）、グループの ID の集合で数（問う口））、`WorkRoleSelectionRepository`（保存のロールの ID）、`PermissionSettingRepository` に `findAllOfRole`（写し）・`findAncestors`（祖先の最大3行）。射影 `AssignedRoleRow`・`GroupAssignedRoleRow`・`AssignmentCount`。
- `role.store`: `RoleStore` に `assignToUser`・`assignToGroup`（persist と flush）、`unassignFromUser`・`unassignFromGroup`（消した行の有無）、`saveWorkRoleSelection`（`MERGE INTO … KEY`。Step 28 の結果）、`deleteRole` を「設定 → そのロールを指す作業ロールの保存 → ロール」に広げた。`RoleStoreClassifier` に割り当ての主キー（`AlreadyAssigned`）、作業ロールの保存の主キー（23505 も `Busy("WORK_ROLE_SELECTION_KEY")` と WARN）、外部キーの相手（ロール3つ・利用者2つ・グループ1つ）、既知の制約の名前を足した。`lockKind` に `ROLE_ASSIGNMENT_KEY`・`WORK_ROLE_SELECTION_KEY`。
- 計画との差: 割り当ての表の主キーの違反は、H2 の主キーの索引の名前が `PRIMARY_KEY_xx` のため、設定の表と同じく表の名前でも見分ける。

### Step 32: DB アクセス — テスト

- 足した: `role/store/RoleStoreClassificationTest`（+4。割り当ての主キー・作業ロールの保存の主キーと WARN・割り当ての表の外部キーの相手・想定外の包み直し）。新しい: `role/store/RoleStoreKeyGuardIT`（7。#6 の利用者とグループの待たない違反、利用者の上限切れと WARN `ROLE_ASSIGNMENT_KEY`、#9 の保存の上限切れと WARN `WORK_ROLE_SELECTION_KEY`、利用者・グループ・消えたロールへの外部キー、外しの有無と割り当てが残るロールの削除（外部キーの最後の守り）と保存の削除、保存の挿入と書き換え。どれも本番の `RoleStoreTransactions` を通し `UnexpectedRollbackException` が出ない）、`role/repository/RoleAssignmentRepositoryIT`（5）。
- 手伝い: `role/testsupport/RoleFixtures` に割り当て・保存の行の作成と数の読み取りを足した。利用者とグループは group の `GroupFixtures` を使い回した。
- コマンド: `caffeinate -i ./gradlew :backend:spotlessApply :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.role.store.*' :backend:integrationTest --tests 'cherry.mastersmith.role.store.*' --tests 'cherry.mastersmith.role.repository.*'`
- 結果: 単体 1 クラス・12 件、結合 5 クラス・25 件。失敗 0・飛ばし 0（exit 0）。上限切れの2件は H2 の上限まで実際に待つ（合否は結果の型と WARN で決める）。
- 計画との差: `RoleStoreKeyGuardIT` に #6 のほか、外部キー・外し・削除・保存の通常の経路の確かめを足した（store の新しい方法をすべて本物の H2 で通すため）。

### Step 33: 業務処理 — 実装

- `RoleAssignmentService`: 割り当て（利用者・グループ）・外し（利用者・グループ）・割り当ての一覧・グループのロール・利用者のロール。判定の順は BR6.9。グループは `lockForAssignment` をロールの行の後に呼ぶ（BR8.2）。拒否と違反の読み替えは1つ目を巻き戻して2つ目のトランザクションで失敗の出来事（計画の 4.3）。`lockForAssignment` の `Busy` は1つ目の結果に `Busy("GROUP_ROW")` として載せ、`GROUP_BUSY`（監査なし、WARN は group が出し済み）。割り当ての一覧は直接の割り当て・グループの割り当て・グループの要約・`memberUserIds`・`findSummariesByIds` を各1回（BR6.11）、利用者は `UserAdminSummary`（伏せ字の型）のまま持つ（BR12.1）。
- `WorkRoleService`: 読み取り（ロールは ID の順、`current` は解決の口と同じ決め方）と切り替え（排他なし。割り当ての外・存在しないロールは `ROLE_NOT_ASSIGNED` で失敗の出来事、保存が同じなら書かず監査なし、それ以外は `MERGE` で書き `WORK_ROLE_SWITCHED`。BR7.5〜BR7.8）。
- `EffectivePermissionResolver`（Javadoc に使い分けと理由）・`EffectivePermissionResolverImpl`（読み取りだけ。写しは決める読み取り4回＋設定の行1回、`resolve` は4回＋祖先の行1回。D-15・D-16）。決める読み取りは `UserRoleReader`（4回、所属のグループが無ければ3回）と結果の `UserRoles`（有効な作業ロールは `WorkRoleChooser`）に1か所でまとめた。
- `RoleAdminService`: 削除は排他の後に直接の割り当ての数を数え、1 以上なら `ROLE_IN_USE`（応答と detail に数）。外部キーの違反の後は2つ目のトランザクションで数え直す。一覧の `userCount`・`groupCount` をページのロールの ID の集合で数える（B4.7 の引き継ぎ）。
- `RoleGroupDeletionGuard` を本物に書き換えた（割り当ての表をグループの ID の集合で1回で数える）。`TODO(B5)` と仮の実装の説明を消した（`git grep -n 'TODO(B5)' -- backend` は 0 件）。
- group の `StoreFailureClassifier` に `FK_GROUP_ROLE_ASSIGNMENTS_GROUP` を足した（13節 Q4: A、D-18）。group の本体の差はこの1ファイル。
- 計画との差:
  - 割り当てと作業ロールの結果は、B4 の `RoleChangeResult` に場合を足さず、新しい結果の型 `RoleAssignmentResult`（`Done`・`Rejected`・`Busy`・`GroupBusy`）・`WorkRoleSwitchResult`（`Switched`・`Unchanged`・`NotAssigned`・`Busy`）にした。`RoleChangeResult` に足すと B4 のコントローラーの網羅の `switch` が業務処理の段でコンパイルできなくなるため。読み取りの結果の型 `RoleAssignmentsResult`・`GroupRolesResult`・`UserRolesResult`・`WorkRoleView` と、グループのロールの1件 `RoleRef`（契約 C7 の RoleRef）を足した。
  - 計画の 8.2 の一覧に無い部品 `UserRoleReader`・`UserRoles`（`role.service`）を足した。解決の口・作業ロールの API・利用者のロールの読み取りが同じ読み取りと同じ決め方を使うため（契約 C5 の「有効な作業ロールを決める唯一の持ち主」）。
  - 写しは、DSL が無いときと作業ロールが無いときに設定の行を読まない（読み取りは計画の 4＋1 回より少ない）。
  - 外しは `RoleBarrier.afterWrite` を呼ばない（計画の 4.4 の表の `afterWrite` の操作に外しが無いため）。
  - 削除の外部キーの違反の読み替え（API からは届かない最後の守り）は、B4 の「数 0」から、2つ目のトランザクションで数え直す形にした（group の形にそろえた）。

### Step 34: 業務処理 — テスト

- 新しい（単体）: `RoleAssignmentServiceTest`（11。判定の順 BR6.9、`AlreadyAssigned`（違反の経路）が2つ目のトランザクションで `ROLE_NO_CHANGE` の失敗の出来事になる（R-14）、`Referenced` の相手ごとの読み替え、`ROLE_BUSY`、グループの排他の順・`GROUP_BUSY`・`exists` 偽、外しの `ROLE_NO_CHANGE`、一覧の `memberUserIds` が1回・出どころの並び・伏せ字の toString、読み取りの拒否、グループと利用者のロール）、`WorkRoleServiceTest`（7）、`EffectivePermissionResolverImplTest`（6）。**書き換え**: `RoleGroupDeletionGuardTest`（4。数で `Allowed`・`Blocked`、渡した ID のすべてにキー、空は問い合わせなし）。足した: `RoleAdminServiceTest`（コンストラクターの追従と +3: 割り当てが残る削除の `ROLE_IN_USE` と数、グループだけの割り当てと違反の後の数え直し、一覧の数）。
- 新しい（結合）: `RoleGroupDeletionGuardIT`（3。本番の Bean が唯一の実装、本物の割り当ての表で `Blocked(1)`・`Blocked(2)`・`Allowed`、外した後は `Allowed`）、`EffectivePermissionConsistencyIT`（7。`reliability-design.md` 3節の (a)〜(h) の8つの組を7件で。期待はテストの中の表。DSL の中と外のすべての対象で `resolve` と写しが表に等しい）、`EffectivePermissionQueryCountIT`（4。決める読み取りは割り当て・グループ 1 と 100 で同じ4つの文、写しと `resolve` は設定の行 1 と 10,000 で同じ5つの文、所属のグループが無ければ3つ）、`RoleDeleteCleanupIT`（3。AC1.1.3・AC1.1.4・AC1.1.13・AC4.1.14）。書き換え: `RoleAdminQueryCountIT` の一覧の期待を2つの文から4つの文（`select user_role_assignments`・`select group_role_assignments` を足す）にし、割り当てのある 25 のロールでも同じ文の数であることと数の値を確かめる形にした（B4.7 の引き継ぎ）。
- group（13節 Q4: A）: `group/store/StoreFailureClassifierTest` に1件、`group/store/GroupStoreConstraintIT` に1件（ロールの割り当てが残るグループの削除の外部キーの違反が `Referenced` になり、グループと割り当てが残る）。
- コマンド: `caffeinate -i ./gradlew :backend:spotlessApply :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.role.service.*' --tests 'cherry.mastersmith.group.store.StoreFailureClassifierTest' :backend:integrationTest --tests 'cherry.mastersmith.role.service.*' --tests 'cherry.mastersmith.group.store.GroupStoreConstraintIT'`
- 結果: 単体 8 クラス・61 件、結合 7 クラス・29 件。失敗 0・飛ばし 0（exit 0）。
- 追加の確かめ（計画の Step 34 のコマンドの外。Step 36 の前に当たりを知るため）: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest --tests 'cherry.mastersmith.role.*' --tests 'cherry.mastersmith.group.*' --tests 'cherry.mastersmith.audit.*'`（BUILD FAILED in 3m 6s、exit 1）。
  - 単体の全体: 224 クラス・1,911 件、失敗 0・飛ばし 0（構造の検査 `RoleBoundaryArchitectureTest`・`ArchitectureTest` などを含む）。
  - 結合（role・group・audit）: 62 クラス・306 件、失敗 1。
  - **止めた理由**: 計画の影響の範囲の外の既存のテスト `group/service/GroupAdminListQueryCountIT` の `listStatements` が落ちた。期待は `select groups`・`select groups`・`select group_members` の3つの文で、実際は4つ目に `select group_role_assignments` がある。このテストは説明文に「B3 の仮の実装は DB を読まないため Hibernate の文は 3 つ」と書いており、問う口を本物（割り当ての表を数える）に置き換えると必ず落ちる前提だった（group の承認済みの設計の一覧の読み取りは4回で、本物の数え方と合う）。本体ではなくテストの期待が古くなったもの。計画の影響の範囲（5節・6節・8.3）に無いため、直さずに止めた（依頼者・指揮役の判断を待つ）。
- **指揮役から伝えられた依頼者の決定（A）**: `group/service/GroupAdminListQueryCountIT#listStatements` の期待に `select group_role_assignments` を足して4つの文にし、説明文の「B3 の仮の実装」の記述を直した（テストだけ。group の本体は変えない）。計画との差として、この記録と `code-summary.md` の B5 の差に載せる。

### Step 35: API — 実装

- `role.web`: `RoleAssignmentController`（`/api/admin/roles/{roleId}/assignments` の GET・POST、`…/users/{userId}`・`…/groups/{groupId}` の DELETE。`ADMIN`）、`RoleLookupController`（`/api/admin/groups/{groupId}/roles`・`/api/admin/users/{userId}/roles` の GET。`ADMIN`）、`MeWorkRoleController`（`/api/me/work-role` の GET・PUT。`AUTHENTICATED`）、`MyPermissionsController`（`/api/me/permissions/schemas|tables|columns` の GET。`AUTHENTICATED`）。合わせて 11 の口。DTO は `AssignmentRequest`・`WorkRoleRequest`・`RoleRefResponse`・`RoleSourceResponse`・`RoleAssignmentsResponse`（利用者の行は `toString` で氏名とメールアドレスを伏せる）・`UserRoleResponse`・`WorkRoleResponse`・`MyPermissionNodesResponse`（実効の値は B4 の `PermissionNodeResponse.Effective` を使い回す）。
- 計画との差:
  - 自分の権限の木（FS の 2.11）のため、計画の 8.2 の一覧に無い `role.domain.EffectiveNode` と `role.service.MyPermissionService`・`MyPermissionTree` を足し、`PermissionSnapshot` に節を並べる方法（`schemaNodes`・`tableNodes`・`columnNodes`。DSL が無ければ空）を足した。写しを1回だけ取り、同じ作業ロールで1階層を答えるため。
  - 割り当ての本文は `userId` と `groupId` のちょうど一方。両方が無いときは `userId` の `REQUIRED`、両方あるときは `groupId` の `INVALID_VALUE` の 400 にした（項目の選び方は計画に無い）。
  - 出どころの応答の形は `RoleSourceResponse(type, groupId, name)`（`type` は `USER`・`GROUP`）。
  - `RoleAssignmentResult.GroupBusy` は group の `GROUP_BUSY`（409）に変える。

### Step 36: API — テスト（結合）と group のテストの書き換え

- 新しい（`role/web`）: `RoleAssignmentApiIT`（6）、`WorkRoleSwitchApiIT`（7）、`MyPermissionsApiIT`（4）、`RoleMeAuthorizationApiIT`（1。`/api/me` の5つの口の 401・200）、`RoleGrantsNoAdminAccessIT`（2。`RequestMappingHandlerMapping` から `ADMIN` の口をすべて集め、全権限のロールを作業ロールに持ち印の無い利用者がどれも 403）、`RoleSelfOperationIT`（2）、`RoleChangeVisibilityIT`（3）、`WorkRoleConcurrencyIT`（2。#8・#9）、`GroupRoleAssignmentConcurrencyIT`（2。#10）、`GroupDeletionWithRoleIT`（2。group の読み直しの R-04）、`WorkRoleSwitchAuditIT`（2）。
- 書き換え・追加: `RoleAdminAuthorizationApiIT`（17 の口 × 4 の主体。403 の主体を `FULL_ROLE_WITHOUT_ADMIN_FLAG` に置き換え、状態の比べに割り当てと保存の表を足した。13節 Q3: A）、`group/web/GroupAdminAuthorizationApiIT`（403 の主体を同じ利用者に置き換え。13節 Q3: A）、`RoleMassAssignmentApiIT`（+3）、`RoleIdorApiIT`（+2）、`RoleConcurrencyIT`（+3。#1 の両方の順と #4）、`RoleBusyApiIT`（+1。#5）、`RoleAuditIT`（+2）、`RoleAuditWriteFailureIT`（+1）、`RoleSecretLeakIT`（+1。TRACE で割り当ての一覧の氏名とメールアドレスがログに出ない）、`RoleConnectionUsageIT`（+1）、`RoleMetricsIT`（B4 の1件に B5 の道を足した）、`RoleAdminQueryCountIT`（+1。割り当ての一覧）、`role/domain/PermissionSnapshotTest`（+2。節の並び）。
- 手伝い: `RoleActors` に `FULL_SCHEMAS`・`adminFlagMissingFullRole`・`suspend`・`member(displayName)`、`RoleApi` に B5 の口の呼び出しを足した。
- コマンド: `caffeinate -i ./gradlew :backend:spotlessApply :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.role.*' --tests 'cherry.mastersmith.group.*' --tests 'cherry.mastersmith.audit.service.*'`（2 分 55 秒）
- 結果: 結合 70 クラス・379 件、失敗 1。落ちたのは足したばかりの `RoleAdminQueryCountIT` の B5 の1件で、期待の誤り（`memberUserIds` の文は `select group_members` ではなく `select groups` で数えられる）。期待を `select roles`・`select user_role_assignments`・`select group_role_assignments`・`select groups`・`select groups`・`select users` に直して流し直し、3 件・失敗 0。
- R-04（group の読み直し）: 403 の主体の置き換え（`GroupAdminAuthorizationApiIT`）、`RoleGroupDeletionGuardIT`（Step 34）と `GroupDeletionWithRoleIT` の通過、`TODO(B5)` 0 件を確かめた。
- 計画との差:
  - `RoleIdorApiIT` の既存の失敗の行数の確かめを、テストの前の数からの差（`failuresBefore`）に変えた。B5 の足したテストが同じクラスの中で失敗の行を作るため。
  - `RoleMetricsIT` は新しいテストを足さず、既存の1件に B5 の道を足した。
  - `group/web/GroupAdminAuthorizationApiIT` が role の手伝い（`RoleActors`・`RoleFixtures`）に依存する（テストの中だけ。本体の依存の向きは変わらない）。
  - 認可の表の管理の口の行は、利用者とグループへの割り当てを別の行に分けて7行にした。
  - `RoleSecretLeakIT` は、応答の本文にはメールアドレスが入る（割り当ての一覧の項目）ことを確かめたうえで、ログに出ないことを確かめる形にした（意図どおり）。

### Step 37: 境界と構造の検査

- `RoleBoundaryArchitectureTest` に B5 の規則を1件足した: role が使う `group.service` は、role に出す口（`GroupMembershipQuery`・`GroupDeletionGuard`）とその型（`DeletionDecision`・`GroupRowLock`・`GroupSummary`）だけ。規則が空振りしない確かめ（role が `GroupMembershipQuery` を使う、`group.service` に口の外の `GroupAdminService` がある）を添えた。
- コマンド: `./gradlew :backend:spotlessApply :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.*BoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.ApiAccessArchitectureTest' --tests 'cherry.mastersmith.ApiAccessRulesTest' --tests 'cherry.mastersmith.PublicApiInventoryTest' :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT'`
- 結果: 単体 18 クラス・93 件、結合 1 クラス・5 件。失敗 0・飛ばし 0（exit 0）。B5 の 11 の口の印と道が合い、`MIN_ENDPOINTS`・`PublicApiInventory` の変更は要らなかった（公開の口は足していない）。

### Step 38: バックエンドの区切りの確かめ

- コマンド: `./gradlew :backend:spotlessApply` の後、colima の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:spotlessCheck :backend:test :backend:integrationTest`（BUILD SUCCESSFUL in 11m 38s、exit 0）。
- 結果: 単体 224 クラス・1,914 件、結合 205 クラス・1,061 件。失敗 0・飛ばし 0。

### Step 39: 負荷の台本

- `perf/k6/scenarios.js`: B5 の6つの場面 `workRoleSwitch`・`workRoleRead`・`myPermissionsTree`（op `schemas`・`tables`・`columns`）・`roleAssignmentsRead`・`roleAssignOps`（op `assignUser`・`unassignUser`・`assignGroup`・`unassignGroup`）・`rolePoolLimit` を足した。`roleAdminRead` に B4.7 の引き継ぎの op `groupRoles`・`userRoles` を足した（setup が悪い側のロールを `perf-gr-0001` と新しいグループに割り当てる）。`rolePoolLimit` は閾値なしで `rolePoolLimit_204`・`_409`・`_500`・`_other` を数える。VU の番号を 5 で割った余りが 1〜3 の VU が作業ロールの切り替え、4・0 の VU がグループへの割り当てと外し（VUS=5 で 3:2、VUS=20 で 12:8）。
- 利用者の側の操作は、グループの場面のメンバーの候補 `perf-gr-<VU>` を使い、setup が操作する管理者 `perf-roleop01` の API でロール・グループを作って割り当て、最後に利用者のトークンを VU ごとに取る。
- `perf/README.md` の role の節: B5 の6つの場面の表、利用者の側の場面の前提、流し方の一覧に5つの場面、接続プールの 3R'（合否の回: 上限 11・VUS=5・2 分、時間切れの累計の増分 0・`rolePoolLimit_500` 0 件・`acquire` の MAX の最大 20 ms 未満。記録の回: 上限 10・VUS=20・2 分、10 ms 以上に届いた証拠、届かなければ VUS=40 で1回だけ流し直す。`acquire` の `baseUnit` を確かめて秒にそろえてから 0.020 と比べる（R-06）。前後の `active` とアプリのログで接続の使い手を切り分ける）、監査の数え方に `WORK_ROLE_SWITCHED` を足した。
- コマンド: 場面ごとに `docker run --rm -v "$PWD/perf/k6:/k6" -e SCENARIO=<場面> grafana/k6:2.3.0 inspect --include-system-env-vars /k6/scenarios.js`（`workRoleSwitch`・`workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead`・`roleAssignOps`・`rolePoolLimit`・`roleAdminRead`）
- 結果: どの場面も `constant-vus`・10 VU・3m0s、`setupTimeout` 10m0s。閾値は `checks{scenario:…}` の率 1・`http_req_duration{scenario:…}` の `max>=0`・`iteration_duration` の p95 < 1000（`myPermissionsTree` は op 3つ、`roleAssignOps` は op 4つ、`roleAdminRead` は op 5つごと）。`rolePoolLimit` は閾値なし（`{}`）。要求は送っていない（使い捨ての環境では流さない。Performance Validation で流す）。
- 計画との差: 計画に無い `rolePoolLimit` の合否の回の写し方として、3G' の `gr-pool.sh` を `sed` で `rolePoolLimit`・`ROLE_DURATION=2m` に替えた `role-pool.sh` を作る手順にした（読み方を3G' とそろえるため）。

### Step 40: 文書

- `README.md` の「ロールと権限の API」を B4・B5 の節にし、「割り当てと作業ロール（B5）」の小節（11 の口の表、出どころ、作業ロールの決め方、実効の権限、管理の可否、反映、解決の口の使い分け（`resolve`・`snapshotFor`・`effectiveWorkRole`）、同時の操作（ロール → グループの順の排他、切り替えは排他しない、`GROUP_IN_USE`）、監査）を足した。一覧の `userCount`・`groupCount` と `ROLE_IN_USE` の行を B5 の動作に直した。「スキーマの変更（Flyway）」に V12、「監査ログ（U4）」に `ROLE_ASSIGNED`・`ROLE_UNASSIGNED`・`WORK_ROLE_SWITCHED` と理由・列・detail を書いた。
- 書く前に、応答の形・code・監査の列と detail をソースで確かめた（外しの相手がいないときは 404 ではなく `ROLE_NO_CHANGE`、`USER_NOT_FOUND`・`GROUP_NOT_FOUND` の拒否の detail は `roleName` を持つ、など）。

### Step 41: 取り残しと変えないものの確かめ

- `git diff --stat develop -- <2.3 の変えないもの>`（`backend/build.gradle.kts`・`build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend`・`.github`・`compose.yaml`・`docker`・`Dockerfile`・`vendor`・`common`・`access`・`auth`・`audit/repository` の本体）: 差なし（作業の場の未コミットの変更も無し）。`application.yaml` の develop との差なし。`packagesJudgedByTotal` と計測の除外は変わっていない（`backend/build.gradle.kts` の差なし）。
- `git grep -n -e 'class [A-Za-z]*Properties\b' -- backend/src`: 既存の `invitation/testsupport/InvitationTestProperties` だけ（B5 で足していない）。新しいテストのクラスはすべて `Test` か `IT` で終わる（未追跡の `backend/src/test` で外れるもの 0 件）。
- `git grep -n 'TODO(B5)' -- backend`: 0 件（未追跡を含めた `grep -rn` も 0 件）。`RoleGroupDeletionGuard` に仮の実装の説明は無い。
- group の本体の HEAD との差は `group/store/StoreFailureClassifier.java` の1ファイルだけ（13節 Q4: A）。新しい依存と lockfile の変更は無い。

### Step 42: 記録（B5 の分）

- `traceability.json`: B5 へ Deferred にしていた行のうち 80 行を OK にし、テストのファイルを target に書いた（画面・U5 の分は target に注記）。時間の目標（AC4.1.20・NFR2.1〜NFR2.5・NFR2.8・NFR2.10）は Performance Validation へ Deferred のまま、target に B5 の台本と、問い合わせの数・指標を B5 で確かめたことを書いた。合計は OK 176・Deferred 83・N/A 4。
- `source-manifest.json`: B5 で作った・変えたソース（本体・移行・テスト・手伝い・group のテスト・負荷の台本・文書）75 の道を足した（合わせて 195）。
- `code-summary.md`: B5 の節を足した（Step 44 の後に書いた）。

### Step 43: 1コマンドの検査

- colima: `colima status` は running（mountType sshfs）、`colima ssh -- ls "$HOME"` でホームの共有を確かめた。README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けた。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（02:28:45〜02:42:15、BUILD SUCCESSFUL in 13m 30s、exit 0）。
- 件数: バックエンドの単体 224 クラス・1,914 件（B4 の 1,851 件から +63）、結合 205 クラス・1,061 件（930 件から +131）。失敗 0・飛ばし 0（対象DB の3種類の SKIPPED 0 件、「コンテナの実行環境」の警告 0 件）。画面 113 ファイル・1,043 件 passed（`frontend/` に触れていない）。単体の結果に B4・B5 の3つの `*PropertyTest`（`RoleNameKeyPropertyTest`・`PermissionInheritancePropertyTest`・`WorkRoleResolutionPropertyTest`）がある。
- 時間: 13 分 30 秒（B4 の 12 分 21 秒から約 1 分 9 秒延びた）。
- カバレッジ（Step 26 → B5）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `role.domain` | 96.5% → 93.8%（532/567） | 88.0% → 85.0%（260/306） |
| `role.repository` | 100.0%（27/27） | 100.0%（2/2） |
| `role.store` | 97.2%（210/216） | 85.4% → 90.3%（65/72） |
| `role.service` | 96.3% → 96.5%（783/811） | 89.1% → 89.7%（278/310） |
| `role.web` | 90.9% → 95.6%（263/275） | 79.2% → 86.2%（75/87） |
| `audit.domain` | 99.5%（432/434） | 94.8% → 95.1%（136/143） |
| `audit.service` | 99.6%（226/227） | 84.5%（49/58） |
| `group.store` | 97.8%（91/93） | 94.6% → 94.9%（37/39） |
| 全体 | 98.2% → 98.0%（9,438/9,635） | 92.8% → 92.5%（3,525/3,810） |

- どのパッケージも下限（行 80%・分岐 70%）を満たす。`packagesJudgedByTotal` は変えていない。
- B5 の終わりの条件（計画 8.1）: 11 の口と認可の表（Step 36・37）、`RoleGroupDeletionGuard` の本物への置き換えと `TODO(B5)` 0 件（Step 33・41）、group の R-04（Step 36）、`MERGE` の確かめ（Step 28）、境界の規則（Step 37）、台本と文書（Step 39・40）、verify の通過（この Step）を確かめた。E2E は Step 44。

### Step 44: E2E

- `mailpit` はすでに Running（healthy）だったため作り直していない。再現の試みの上限は、流す前に「全体の1回の流し直し」と決めた。
- コマンド: colima の環境変数を付けて `caffeinate -i ./gradlew e2eTest`（02:42:31〜02:49:58、BUILD SUCCESSFUL in 7m 27s、exit 0）。
- 結果: 177 passed（7.4m）。失敗 0 のため流し直しはしなかった（B4 で1回落ちた `135-dsl-admin-accessibility.e2e.ts` の `dsl admin green dark 360px` も通った）。報告の確かめの道具は「残してはならない値は含まれていません（値の種類 7・json の報告 1・添付 299・失敗の画面の写し 0・見つかった件数 0）」。
- 片付け: `rm -rf frontend/playwright-report frontend/test-results`（git の対象外）。
