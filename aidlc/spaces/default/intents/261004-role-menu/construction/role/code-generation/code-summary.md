# コードの要約（Code Summary）— U4 ロール（role）

この単位は3つの Bolt（B4・B5・B6）で作る（計画 3節）。この文書は Bolt ごとの節と、最後に単位の全体のまとめを持つ。

## B4 ロールと権限の設定

コミットは依頼者の承認を得て計画 3節の区切りで行う（B4 は C1〜C3）。develop へは squash で1コミットになる。

計画: `code-generation-plan.md`（同じディレクトリ、13節の Q1〜Q9 はすべて A、D-11・D-12 も A）。Step ごとのコマンドと結果は `generation-notes.md`。方法は test-after（Testing Contract のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流した）。作業ブランチは `feature/261004-role-menu-b4`（`develop` の c581cfa から）。

### B4.1 作ったもの・変えたもの

アプリのソースの道の一覧は `source-manifest.json`（B4 の時点で 120）。

- **移行**: `V11__u4_role.sql`（`roles` と `permission_settings`。制約は `uk_roles_name_key`・`pk_permission_settings`・`fk_permission_settings_role`（削除の制限つき）・検査の4つ）。`users.admin_flag` は変えない。
- **`role.domain`**: `Role`・`PermissionSetting`（と ID の型）、名前（`RoleName`・`RoleNameValidation`・`PermissionTargetName`）、対象と値（`PermissionTarget`・`PermissionLevel`・`PermissionValues`・`MainPermission`・`EffectivePermission`・`InheritedFrom`）、純粋な関数（`PermissionInheritance`・`PermissionTreeBuilder`）と木の節 `PermissionNode`、監査（`RoleOperation`・`RoleAuditFailure`・`RoleAuditDetail`・`RoleAuditEvent`）、code（`RoleProblemTypes` の7つ）、`RoleRejection`。
- **`role.repository`**: 読み取りだけの `RoleRepository`・`PermissionSettingRepository` と投影の型（書き込みの方法を置かない）。
- **`role.store`**（TraceAspect の対象の外）: `RoleStore`（排他・書き込み）、`RoleStoreOutcome`、`RoleStoreClassifier`（SQLState と制約の名前で区分）、`RoleStoreUnexpectedException`。
- **`role.service`**: `RoleAdminService`（作成・名前の変更・削除・権限の保存・消す・一覧・1件・木の3階層）、`RoleStoreTransactions`（`inFirst`・2つ目のトランザクション）、`RoleFirstStep`、`RoleBarrier`（本番は `NoOpRoleBarrier`）、結果の型（`RoleCreateResult`・`RoleChangeResult`・`RoleListResult`・`RoleDetailResult`・`PermissionTreeResult`）、入力の判定 `PermissionInputs`、`RoleProblemTypeCatalog`。
- **`role.web`**: `RoleAdminController`（`/api/admin/roles` の5つの口）と `RolePermissionController`（`/api/admin/roles/{roleId}/permissions` の5つの口）。どちらも `@ApiAccess(ADMIN)`。DTO は `record`。
- **`audit`**: `AuditEventType` に `ROLE_CREATED`・`ROLE_RENAMED`・`ROLE_DELETED`・`ROLE_PERMISSION_CHANGED`、`AuditFailureReason` に5つ、`AuditEventFactory.from(RoleAuditEvent)`、`AuditDetailJson.ofRole`（16,384 文字を超えると要約）、`AuditEventListener.onRoleAuditEvent`。
- **設定**: `application.yaml` は `org.hibernate.orm.jdbc.error: OFF` の説明のコメント4行だけ。
- **テスト**: `role/` の下に単体 16 クラス（書き直した `RoleBoundaryArchitectureTest` を含む）・結合 21 クラス（件数は B4.3）、`audit` に単体3クラス、手伝い（`role/testsupport` の5つ）。既存のテストは `UserBoundaryArchitectureTest`（role を user に依存してよい機能に足す。Q1: A）と `AuditMigrationCompatibilityIT`（依頼者の決定 B）の2つを書き換えた。
- **負荷の道具と文書**: `perf/k6/scenarios.js` に4つの場面（`roleTreeRead`・`rolePermissionSave`・`roleAdminRead`・`roleAdminOps`）、`perf/README.md` に「ロールの管理の場面」の節、`README.md` に「ロールと権限の API」の節と V11・監査の種類。

### B4.2 実装で決めたこと

- 業務の拒否は1つ目のトランザクションを巻き戻し、書き込みの無い2つ目のトランザクションで失敗の出来事を出す（Q2: A、group の形にそろえた）。排他の待ちの上限はロールの行 3 秒、名前の鍵は H2 の既定の約 2 秒で、超えると `ROLE_BUSY`（監査に残さない）。
- 結果の型・`RoleStoreOutcome`・`RoleFirstStep` の `toString` は種類・ID・件数だけを出す（D-5）。`RoleSecretLeakIT` で TRACE のログを確かめた。
- 想定外の DB の例外は `RoleStoreUnexpectedException` に包み直し、文には SQLState と role の既知の制約の名前だけを入れる（D-7、原因の例外は付けない）。
- op のタグは `iteration_duration` に付くことを Step 16 で確かめたため、k6 の場面は操作ごとに分けず、1つの場面の中で op ごとに判定する。

### B4.3 テストの量とカバレッジ

**1コマンドの検査（Step 21）**: colima の共有を確かめ、README の環境変数を付けた `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の実測（BUILD SUCCESSFUL in 12m 21s。B3 の 10 分 46 秒から約 1 分 35 秒延びた）。

- バックエンドの単体 218 クラス・1,851 件（変更の前 1,737 件から +114）、結合 187 クラス・930 件（829 件から +101）。失敗 0・飛ばし 0。対象DB の3種類のテストの SKIPPED 0 件、「コンテナの実行環境」の警告 0 件。単体テストの結果に `RoleNameKeyPropertyTest`・`PermissionInheritancePropertyTest` がある。
- 画面 113 ファイル・1,043 件 passed（B4 は `frontend/` に触れていない）。

| パッケージ | 行 | 分岐 |
|---|---|---|
| `role.domain` | 96.5% | 88.0% |
| `role.repository` | 100.0% | 100.0% |
| `role.store` | 97.2% | 85.4% |
| `role.service` | 100.0% → 96.3% | 100.0% → 89.1% |
| `role.web` | 90.9% | 79.2% |
| `audit.domain` | 99.7% → 99.5% | 96.8% → 94.8% |
| `audit.service` | 99.5% → 99.6% | 84.6% → 84.5% |
| 全体 | 98.5% → 98.2% | 93.9% → 92.8% |

（矢印の左は Step 3 の変更の前の値。新しいパッケージは右の値だけ。`role.service` の変更の前は B3 の仮の実装 `RoleGroupDeletionGuard` だけ）。どのパッケージも下限（行 80%・分岐 70%）を満たす。

**E2E（Step 22）**: 全体（14 ファイル・177 件）を流した。1回目は 176 passed・1 failed（`135-dsl-admin-accessibility.e2e.ts` の `dsl admin green dark 360px` で、ログインの画面が 5 秒で見えなかった）。全体の流し直しで 177 passed（6.8m）。手元で再現しなかったため、`team.md` の不安定なテストの決まりの「再現しないもの」として、不安定と確かめられていない扱いで記録した（`generation-notes.md` の Step 22）。報告の確かめの道具は2回とも「残してはならない値は含まれていません（見つかった件数 0）」。報告のフォルダーは消した。

### B4.4 計画との差

| ID | 差 | 理由 |
|---|---|---|
| B4-1 | `AuditMigrationCompatibilityIT`（計画の影響の範囲の外）の期待の値を、`CURRENT_LAST_VERSION = 11` と `MIGRATIONS_AFTER_PREVIOUS` の定数で書き換えた | V11 を足すと履歴の最大の版と移行の数が変わり落ちた。依頼者の決定 B（テストだけ、本体は変えない） |
| B4-2 | 小さな型とテストを足した: `PermissionLevel`・`PermissionInputs`・`PermissionTargetRequest`・`PermissionTargetTest`・`PermissionInputsTest` | 計画の 7.2 の一覧に無いが、階層の値・入力の判定・`scope` と消す対象の共通の形が要った |
| B4-3 | `RoleName`・`RoleNameValidation` を Step 6 から Step 4 に前倒し | エンティティのコンパイルのため。コミットの区切り C1 の中で変わらない |
| B4-4 | `AuditDetailJson` の role の方法の名前を `of` ではなく `ofRole` にした | `of` を重ねると既存の `AuditDetailJson.of(null)` が型を決められずコンパイルできない |
| B4-5 | `RoleStore` の持ち主の規則は「項目として持つのは `RoleStoreTransactions` だけ」 | 業務処理は `inFirst` の引数の関数で `RoleStore` を呼ぶため、型への依存は業務処理にも出る（group と同じ） |
| B4-6 | 木の1段目は `table_name = ''` の行だけでなく、そのロールの行を1回で集計して読む | 下の階層の行だけを持つ今の DSL に無いスキーマを1段目に出して消せるようにするため（BR4.10）。読み取りは1回のまま |
| B4-7 | 保存の範囲: スキーマの `scope` はそのスキーマの行だけ、テーブルの `scope` はそのテーブルとそのカラムの行だけ。範囲の外・重なり・空の `entries` は 400 | 承認済みの文書に細かい決まりが無かった（画面 S4 の1つの表の分） |
| B4-8 | ロールの一覧の `userCount`・`groupCount` は B4 では 0 | 割り当ての表は B5 で足す（D-37 と同じ扱い） |
| B4-9 | `AlreadyAssigned` の型は置いたが、区分（制約の名前）は B5 で足す | 割り当ての表が無い |
| B4-10 | 木の節の `displayName` は Accept-Language で選んだ1つの文字列（今の DSL に無い節は物理名） | 契約 C7 と U6 の型は文字列。U5 navigation は `{ja, en}` の組で返すため、単位の間で形が揃っていない |
| B4-11 | 応答の `inheritedFrom` は主権限の継承の元だけ | 契約 C7 の項目は1つ。CREATE・DELETE の継承の元はドメインでは求めているが応答に載せていない |
| B4-12 | `RoleAuditIT`・`RoleAuditWriteFailureIT` を `role/web` に置いた（group は `audit/service`） | role の API を通す結合テストのため |
| B4-13 | `RoleConcurrencyIT` の #7 は後の側の `ROLE_BUSY` も負けとして受け入れる（その場合は値と監査を確かめない） | `reliability-design.md` 4.2 の注。今回の実行では両方 204 |
| B4-14 | k6 の `roleAdminRead` は B4 の口だけ。悪い側のデータの名前は環境変数で替えられる既定（`perf-role-worst`・`perf`・`perf_t001`）。操作する管理者は `perf-roleop01` | グループのロール・利用者のロールの読み取りは B5、準備の台本は B6 |
| B4-15 | AC1.2.18（応答時間）は B4 では台本だけで、`traceability.json` では Performance Validation へ Deferred | 計画 10.1 では B4 の手順に入っているが、流して判定するのは Performance Validation |

### B4.5 承認の場で確かめたいこと

承認済みの文書で決まっていなかった点を、実装で次のとおり決めた。承認の場で確かめたい。

**依頼者の答え（B4 の統合の前）**: 1 は A（文字列1つのまま。単位の間の差を記録に残す）、2〜4 は A（3つとも受け入れる）、5 は A（受け入れる。B5 で数える）、6 は A（team.md の「手元で再現しないもの」として記録して進める。同じテストが二度目に落ちたら、原因を直すまで進まない）。7 はコード生成の段の承認の場で確かめる。

1. **木の節の `displayName`**（B4-10）: Accept-Language で日本語か英語の1つの文字列を返す形でよいか。U5 navigation の `{ja, en}` の組の形にそろえるなら、U6 の画面の型と契約 C7 の差になる。
2. **木の1段目の読み取り**（B4-6）: 1段目もロールの行を1回で集計して読む形（`performance-design.md` 1節の「`table_name = ''` の行」との差）でよいか。
3. **`inheritedFrom`**（B4-11）: 主権限の継承の元だけを返す形でよいか。画面で CREATE・DELETE の継承の元も示すなら、項目を足す必要がある。
4. **保存の範囲**（B4-7）: スキーマの範囲・テーブルの範囲の受け付ける行と、空の `entries` を 400 にする読み方でよいか。
5. **一覧の数**（B4-8）: B4 の間（B5 の統合まで）、`userCount`・`groupCount` が常に 0 を返すことを受け入れるか。
6. **E2E の1件の失敗**（B4.3）: 1回目に `135-dsl-admin-accessibility.e2e.ts` の1件が落ち、流し直しで通った。再現しないものとして記録した扱いで統合に進めてよいか。
7. そのほかの差（B4-1〜B4-5・B4-9・B4-12〜B4-15）。

### B4.6 後に回すこと

- **H2 の例外の文への依存**（group のコード生成のレビュー R-05）: role の区分も H2 の例外の文の制約名・表名に頼り、H2 を上げると `UNEXPECTED` に落ちうる。見張りは `RoleStoreConstraintIT`（B4）と `RoleStoreKeyGuardIT`（B5）。`project.md` の Tech Stack の H2 の項への追記は、学びの段で依頼者に諮る。
- **想定外の DB の例外の手がかり**: 包み直した例外には SQLState と既知の制約の名前だけが入る（D-7）。調べの手がかりを増やすかは後で決める。

### B4.7 B5 への引き継ぎ

- **`AuditMigrationCompatibilityIT`**: B5 で V12 を足すと同じテストがまた落ちる。`CURRENT_LAST_VERSION` を 12 に書き換える（`MIGRATIONS_AFTER_PREVIOUS` は前の版との差の数として見直す）。
- **一覧の数**: `RoleListResult` の `userCount`・`groupCount` は 0 のまま。B5 で割り当ての表から数え、`RoleAdminQueryCountIT` の問い合わせの数を見直す。
- **`AlreadyAssigned` の区分**: 割り当ての表の制約の名前を `RoleStoreClassifier` の既知の一覧に足し、`RoleStoreClassificationTest` に行を足す。
- **`ROLE_IN_USE`**: `RoleChangeResult.InUse` と `RoleAuditDetail.InUse` は形だけ置いた（B4 では割り当てが無いため届かない）。B5 で削除の拒否と重なり（AC1.1.3・AC1.1.6・AC1.1.8）を足す。
- **認可の表**: `RoleAdminAuthorizationApiIT` の 403 の主体は「管理者の印を持たない利用者」。B5 で ADMIN の口の読み方が変わるなら表を足す。
- **k6**: `roleAdminRead` にグループのロールと利用者のロールの読み取りの op を足す。
- **`RoleGroupDeletionGuard`**: B3 の仮の実装（`TODO(B5)`）は B4 で変えていない。B5 で本物に置き換える。

### B4.8 見せるもの（`curl` の手順）

開発時の起動（`./gradlew :backend:bootRun`）か、ビルドした WAR で行う。DSL を適用しておく（DSL の管理の画面か API）。操作は監査に残るため、配備した環境では行わない。管理者のアクセストークンは画面でログインした後のブラウザーの開発者の道具から取るか、ログインの API の応答の `accessToken` を使う（値を記録に残さない）。`<schema>`・`<table>` は適用した DSL の名前に替える。

```bash
B=http://localhost:8080; H=(-H "Authorization: Bearer $T" -H "Origin: $B" -H 'Content-Type: application/json')
curl -s "${H[@]}" -X POST "$B/api/admin/roles" -d '{"name":"営業"}'                    # 201 と roleId
curl -s "${H[@]}" -X POST "$B/api/admin/roles" -d '{"name":"営業"}'                    # 409 ROLE_NAME_DUPLICATE
curl -s "${H[@]}" -X PUT "$B/api/admin/roles/<roleId>/permissions" \
  -d '{"scope":{"schemaName":"<schema>"},"entries":[{"schemaName":"<schema>","main":"READ"}]}'   # 204
curl -s "${H[@]}" -X PUT "$B/api/admin/roles/<roleId>/permissions" \
  -d '{"scope":{"schemaName":"<schema>","tableName":"<table>"},"entries":[{"schemaName":"<schema>","tableName":"<table>","main":"FULL","create":true}]}'   # 204
curl -s "${H[@]}" "$B/api/admin/roles/<roleId>/permissions/schemas"                    # 200、explicit.main READ
curl -s "${H[@]}" -H 'Accept-Language: en' "$B/api/admin/roles/<roleId>/permissions/tables?schema=<schema>"   # 200、<table> は FULL（明示）、ほかは READ（inheritedFrom SCHEMA）。<table> は inheritedFrom EXPLICIT
curl -s "${H[@]}" "$B/api/admin/roles/<roleId>/permissions/columns?schema=<schema>&table=<table>"   # 200、カラムは FULL（inheritedFrom TABLE）
curl -s "${H[@]}" -X POST "$B/api/admin/roles/<roleId>/permissions/clear" \
  -d '{"targets":[{"schemaName":"<schema>","tableName":"<table>"}]}'                   # 204
curl -s "${H[@]}" -X DELETE "$B/api/admin/roles/<roleId>"                              # 204（設定も消える）
unset T
```

## B5 割り当てと作業ロール

コミットは依頼者の承認を得て計画 3節の区切りで行う（B5 は C1〜C3）。develop へは squash で1コミットになる。

計画: `code-generation-plan.md`（同じディレクトリ、8節。13節の Q1〜Q9 と D-11・D-12 は A）。Step ごとのコマンドと結果は `generation-notes.md` の「B5」。方法は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流した）。作業ブランチは `feature/261004-role-menu-b5`（`develop` の 3d4f344 から）。

### B5.1 作ったもの・変えたもの

アプリのソースの道の一覧は `source-manifest.json`（B5 で 75 を足し、合わせて 195）。

- **移行**: `V12__u4_role_assignment.sql`（`user_role_assignments`・`group_role_assignments`・`work_role_selections`。主キー・外部キー（削除の制限）・利用者とグループの ID の索引。作業ロールの保存はロールへの外部キーを置かない）。`users.admin_flag` と監査の表は変えない。
- **`role.domain`**: 割り当てと保存のエンティティ（`UserRoleAssignment`・`GroupRoleAssignment`・`WorkRoleSelection` と ID の型）、`WorkRoleRef`・`RoleSource`・`UserRoleView`、純粋な関数 `WorkRoleChooser`、写し `PermissionSnapshot` と木の節 `EffectiveNode`。`RoleOperation`・`RoleAuditFailure`・`RoleRejection`・`RoleProblemTypes`（`ROLE_NOT_ASSIGNED`）・`RoleAuditDetail`（`Assignment`・`WorkRoleSwitch`）・`RoleAuditEvent` に足した。
- **`role.repository`**: `RoleAssignmentRepository`・`WorkRoleSelectionRepository` と射影の型、`PermissionSettingRepository` に写しと祖先の読み取り。
- **`role.store`**: 割り当て・外し・作業ロールの保存（`MERGE INTO … KEY`）、ロールの削除で保存も消す。`RoleStoreClassifier` に割り当てと保存の主キー・外部キーの相手。
- **`role.service`**: `RoleAssignmentService`・`WorkRoleService`・`MyPermissionService`、解決の口 `EffectivePermissionResolver`（`effectiveWorkRole`・`snapshotFor`・`resolve`）と実装、決める読み取り `UserRoleReader`・`UserRoles`、結果の型。`RoleGroupDeletionGuard` を本物に置き換えた（`TODO(B5)` 0 件）。`RoleAdminService` の削除の `ROLE_IN_USE` と一覧の数。
- **`role.web`**: `RoleAssignmentController`（4）・`RoleLookupController`（2）は `ADMIN`、`MeWorkRoleController`（2）・`MyPermissionsController`（3）は `AUTHENTICATED`。合わせて 11 の口。DTO は `record`（割り当ての一覧の利用者の行は `toString` で氏名とメールアドレスを伏せる）。
- **`audit`**: `ROLE_ASSIGNED`・`ROLE_UNASSIGNED`・`WORK_ROLE_SWITCHED`、失敗の理由 `ROLE_NOT_ASSIGNED`、写し方・受け取り・detail の JSON。
- **group**: 本体は `group/store/StoreFailureClassifier` の1か所（`FK_GROUP_ROLE_ASSIGNMENTS_GROUP`。13節 Q4: A）。テストは `GroupAdminAuthorizationApiIT`（403 の主体。Q3: A）・`StoreFailureClassifierTest`・`GroupStoreConstraintIT`（Q4: A）と、依頼者の決定 A の `GroupAdminListQueryCountIT`。
- **テスト**: `role/` の下に新しい単体 6 クラス・結合 18 クラス、既存の role・audit のテストに B5 の分を足した。手伝いは `RoleActors`・`RoleApi`・`RoleFixtures`。
- **負荷の道具と文書**: `perf/k6/scenarios.js` に6つの場面と `roleAdminRead` の op 2つ、`perf/README.md` の role の節に B5 の場面と接続プールの 3R'、`README.md` に「割り当てと作業ロール（B5）」・V12・監査の種類。

### B5.2 実装で決めたこと

- **`MERGE` の確かめ（R-15、D-19）**: 別の接続が同じ利用者の保存の行を未確定のまま持つ間の `MERGE INTO work_role_selections … KEY (user_id)` は、未確定の挿入にも書き換えにも待った後に `HYT00`（50200、待ちの上限切れ）になり、ほかの誤りは出なかった。保存の形は `MERGE` のまま（読んでから更新か挿入に切り替えない）とし、区分は `Busy("WORK_ROLE_SELECTION_KEY")`（`ROLE_BUSY`）。保存の主キーの 23505 も同じ区分にして WARN を出す。
- 業務の拒否と違反の読み替えは、1つ目のトランザクションを巻き戻して2つ目で失敗の出来事を出す（B4 と同じ）。グループへの割り当て・外しはロール → グループの順に排他し、グループの排他の上限切れは group の `GROUP_BUSY`（監査なし）。
- 作業ロールの切り替えは排他しない。保存が選んだロールと同じなら何も書かず監査にも残さない（204）。割り当ての外のロールと存在しないロールは同じ `ROLE_NOT_ASSIGNED`（detail なし）。
- 有効な作業ロールを決める読み取りは `UserRoleReader` の1か所（割り当て・所属グループ・グループの割り当て・保存の4回、所属が無ければ3回）。写しは DSL か作業ロールが無ければ設定を読まない。

### B5.3 テストの量とカバレッジ

**1コマンドの検査（Step 43）**: colima の共有を確かめ、README の環境変数を付けた `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の実測（BUILD SUCCESSFUL in 13m 30s。B4 の 12 分 21 秒から約 1 分 9 秒延びた）。

- バックエンドの単体 224 クラス・1,914 件（B4 の 1,851 件から +63）、結合 205 クラス・1,061 件（930 件から +131）。失敗 0・飛ばし 0。対象DB の3種類のテストの SKIPPED 0 件、「コンテナの実行環境」の警告 0 件。単体テストの結果に `RoleNameKeyPropertyTest`・`PermissionInheritancePropertyTest`・`WorkRoleResolutionPropertyTest` がある。
- 画面 113 ファイル・1,043 件 passed（B5 は `frontend/` に触れていない）。

| パッケージ | 行 | 分岐 |
|---|---|---|
| `role.domain` | 96.5% → 93.8% | 88.0% → 85.0% |
| `role.repository` | 100.0% | 100.0% |
| `role.store` | 97.2% | 85.4% → 90.3% |
| `role.service` | 96.3% → 96.5% | 89.1% → 89.7% |
| `role.web` | 90.9% → 95.6% | 79.2% → 86.2% |
| `audit.domain` | 99.5% | 94.8% → 95.1% |
| `audit.service` | 99.6% | 84.5% |
| `group.store` | 97.8% | 94.6% → 94.9% |
| 全体 | 98.2% → 98.0% | 92.8% → 92.5% |

（矢印の左は Step 26 の変更の前の値）。どのパッケージも下限（行 80%・分岐 70%）を満たす。

**E2E（Step 44）**: 全体を1回流し、177 passed（7.4m）。失敗 0（B4 で1回落ちた `135-dsl-admin-accessibility.e2e.ts` の1件も通った）。報告の確かめの道具は「残してはならない値は含まれていません（見つかった件数 0）」。報告のフォルダーは消した。

**group の読み直しの R-04**: (1) 403 の主体を「全スキーマを FULL・CREATE と DELETE を可にしたロールを作業ロールに持ち、管理者の印だけを欠く利用者」に置き換えた（`RoleAdminAuthorizationApiIT`・`GroupAdminAuthorizationApiIT`、既存の管理の口すべては `RoleGrantsNoAdminAccessIT`）。(2) `RoleGroupDeletionGuardIT`・`GroupDeletionWithRoleIT` が通る。(3) `git grep -n 'TODO(B5)' -- backend` は 0 件。

### B5.4 計画との差

| ID | 差 | 理由 |
|---|---|---|
| B5-1 | `group/service/GroupAdminListQueryCountIT`（計画の影響の範囲の外）の期待に `select group_role_assignments` を足して4つの文にし、説明文の「B3 の仮の実装」の記述を直した | 問う口を本物にすると必ず落ちる前提のテストだった。依頼者の決定 A（テストだけ、group の本体は変えない） |
| B5-2 | 結果の型を B4 の `RoleChangeResult` に足さず、`RoleAssignmentResult`・`WorkRoleSwitchResult` と読み取りの結果の型を新しく作った | `RoleChangeResult` に場合を足すと B4 のコントローラーの網羅の `switch` が壊れるため |
| B5-3 | 計画の 8.2 の一覧に無い `UserRoleReader`・`UserRoles`・`MyPermissionService`・`MyPermissionTree`・`EffectiveNode`、`PermissionSnapshot` の節を並べる方法を足した | 有効な作業ロールを決める読み取りを1か所にまとめ（契約 C5）、自分の権限の木を1つの写しで答えるため |
| B5-4 | 割り当ての本文の誤りは、両方が無いとき `userId` の `REQUIRED`、両方あるとき `groupId` の `INVALID_VALUE` | 項目の選び方が設計に無かった |
| B5-5 | 出どころの応答は `RoleSourceResponse(type, groupId, name)`（`type` は `USER`・`GROUP`） | 契約 C7 の RoleSource の具体の形 |
| B5-6 | 割り当ての外・存在しないロールへの切り替えの拒否（`ROLE_NOT_ASSIGNED`）は detail を持たない | 存在しないロールには名前が無く、割り当ての外のロールの名前を残す理由も無い（`entities.md` に拒否の形が無かった） |
| B5-7 | 写しは DSL が無いときと作業ロールが無いときに設定を読まない | 読み取りは計画の 4＋1 回以下になる |
| B5-8 | 外しは `RoleBarrier.afterWrite` を呼ばない。削除の外部キーの違反の後は2つ目のトランザクションで数え直す | 計画の 4.4 の表に外しの `afterWrite` が無い。group の形にそろえた |
| B5-9 | 割り当ての表の主キーの違反は、制約の名前に加えて表の名前でも見分ける | H2 の主キーの索引の名前が `PRIMARY_KEY_xx` になるため |
| B5-10 | `RoleIdorApiIT` の既存の失敗の行数の確かめを、テストの前の数からの差にした。`RoleMetricsIT` は既存の1件に B5 の道を足した | B5 のテストが同じクラスの中で失敗の行を作るため。指標の確かめを1か所にまとめるため |
| B5-11 | `GroupAdminAuthorizationApiIT` が role のテストの手伝い（`RoleActors`・`RoleFixtures`）に依存する | 13節 Q3 の主体を作るため（テストの中だけで、本体の依存の向きは変わらない） |
| B5-12 | `RoleStoreKeyGuardIT`・`AuditMigrationCompatibilityIT` を名指しで足して流した（Step 28・32） | B4 の引き継ぎ（`CURRENT_LAST_VERSION` 12）と、store の新しい方法をすべて本物の H2 で通すため |
| B5-13 | k6 の `rolePoolLimit` の合否の回は、3G' の `gr-pool.sh` を `sed` で替えた `role-pool.sh` で流す手順にした | 接続プールの値の読み方を group とそろえるため |
| B5-14 | AC4.1.20・NFR2.3 などの時間の目標は台本だけで、`traceability.json` では Performance Validation へ Deferred（問い合わせの数・指標は B5 で確かめた） | 流して判定するのは Performance Validation |

### B5.5 承認の場で確かめたいこと

**依頼者の答え（B5 の統合の前）**: 1 は A（この形でよい）、2 は A（detail を残さないままでよい）、3 は A（受け入れる。U6 の設計で扱う）、4 は A（テストを足さずに進める）。5 はコード生成の段の承認の場で確かめる。

承認済みの文書で決まっていなかった点を、実装で次のとおり決めた。承認の場で確かめたい。

1. **割り当ての本文の 400 の項目**（B5-4）: 両方が無いとき `userId`、両方あるとき `groupId` を `fieldErrors` に出す形でよいか（U6 の画面の表示に効く）。
2. **切り替えの拒否の detail**（B5-6）: `ROLE_NOT_ASSIGNED` の監査の行に detail（ロールの名前）を残さない形でよいか。
3. **外しの相手がいないとき**: 存在しない利用者・グループの外しは 404 ではなく `ROLE_NO_CHANGE`（BR6.4 のとおり）。割り当てでは相手ごとの 404 になる。この非対称を画面（U6）で扱えるか。
4. **`role.domain` のカバレッジ**: 行 96.5% → 93.8%、分岐 88.0% → 85.0% に下がった（下限は満たす）。足りない所は主に割り当ての ID の型（`UserRoleAssignmentId`・`GroupRoleAssignmentId`）の `equals`・`hashCode` と、エンティティの JPA 用の口（B4 の `PermissionSettingId` と同じ形）。
5. そのほかの差（B5-1〜B5-3・B5-5・B5-7〜B5-14）。

### B5.6 B6 への引き継ぎ

- **準備の台本**: B5 の k6 の場面は、B6 で足す準備の場面（悪い側のロール `perf-role-worst`・スキーマ `perf`・100 カラムのテーブル。計画の Step 61 ではグループとメンバー・割り当ても入れる）と、グループの場面の 2G のメンバーの候補 `perf-gr-0001`〜を前提にする。`roleAdminRead` と `myPermissionsTree`・`roleAssignmentsRead` の setup は悪い側のロールを利用者・グループに割り当てる（同じ使い捨ての環境で流し直すと `ROLE_NO_CHANGE` で setup が止まる）。準備の台本で環境を作り直すか、流し方を Performance Validation の手順で決める。
- **適用と割り当て**: B6 の適用（置き換えの削除を含む）でロールや設定の行を変えるときは、B5 で足した割り当ての表と作業ロールの保存との関係（割り当てが残るロールの扱い、ロールを消すときの保存の削除の順）を、B5 の削除（設定 → 保存 → ロール）とそろえて計画どおりに確かめる。`RoleTransferBusyIT` #12 の「適用中の同じロールへの割り当て」は B5 の割り当ての口を使う。
- **解決の口**: U5 navigation と B6 は `EffectivePermissionResolver` を使い、2つ以上の対象は `snapshotFor` を1回だけ呼ぶ（README の使い分け）。
- **移行**: B6 で移行を足すときは、`AuditMigrationCompatibilityIT` の `CURRENT_LAST_VERSION` と `RoleMigrationCompatibilityIT` の版を書き換える。
- **認可の表**: B6 の口を `RoleAdminAuthorizationApiIT` の表に足す（403 の主体は B5 の `FULL_ROLE_WITHOUT_ADMIN_FLAG`）。`RoleGrantsNoAdminAccessIT` は `ADMIN` の口を自動で集めるため、足す必要は無い。

### B5.7 見せるもの（`curl` の手順）

開発時の起動（`./gradlew :backend:bootRun`）か、ビルドした WAR で行う。DSL を適用し、B4.8 の手順でロール「営業」（READ）とロール「経理」（FULL）を作っておく。操作は監査に残るため、配備した環境では行わない。`$T` は管理者のアクセストークン、`$U` は割り当てる利用者のアクセストークン（どちらも値を記録に残さない）。

```bash
B=http://localhost:8080; H=(-H "Authorization: Bearer $T" -H "Origin: $B" -H 'Content-Type: application/json')
M=(-H "Authorization: Bearer $U" -H "Origin: $B" -H 'Content-Type: application/json')
curl -s "${H[@]}" -X POST "$B/api/admin/roles/<営業のroleId>/assignments" -d '{"userId":<userId>}'   # 204
curl -s "${H[@]}" -X POST "$B/api/admin/roles/<経理のroleId>/assignments" -d '{"userId":<userId>}'   # 204
curl -s "${H[@]}" "$B/api/admin/users/<userId>/roles"                     # 200、2つのロールと出どころ USER
curl -s "${M[@]}" "$B/api/me/work-role"                                    # 200、current は ID の小さい方
curl -s "${M[@]}" "$B/api/me/permissions/tables?schema=<schema>"           # 200、作業ロールの設定の値
curl -s "${M[@]}" -X PUT "$B/api/me/work-role" -d '{"roleId":<経理のroleId>}'   # 204
curl -s "${M[@]}" "$B/api/me/permissions/tables?schema=<schema>"           # 200、経理の設定の値に変わる
curl -s "${H[@]}" -X DELETE "$B/api/admin/roles/<営業のroleId>"            # 409 ROLE_IN_USE と数
unset T U
```

## B6 権限の受け渡し（書き出し・確かめ・適用）

コミットは依頼者の承認を得て計画 3節の区切りで行う（B6 は C1〜C3）。develop へは squash で1コミットになる。

- 計画: `code-generation-plan.md`（同じディレクトリの 9節）。13節の Q1〜Q9 と D-11・D-12 は A。
- Step ごとのコマンドと結果: `generation-notes.md` の「B6」。
- 方法: test-after。層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流した。
- 作業ブランチ: `feature/261004-role-menu-b6`（`develop` の b1c2760 から）。

### B6.1 作ったもの・変えたもの

アプリのソースの道の一覧は `source-manifest.json`（B6 で 56 を足し、合わせて 251）。移行は足していない（V1〜V12 のまま）。

- **`role.domain`**:
  - 既存の型に足したもの: `RoleOperation.APPLY_TRANSFER`、`RoleAuditFailure.ROLE_TRANSFER_STALE`、`RoleRejection.TRANSFER_STALE`、`RoleProblemTypes`（`ROLE_TRANSFER_INVALID` 400・`ROLE_TRANSFER_STALE` 409）、`RoleAuditDetail`（`Transfer`・`TransferSummary`・`TransferRole`）、`RoleAuditEvent.transferApplied`。
  - 新しい型: `TransferAction`、`TransferError`（理由 13・行・列・場所。入れた値は持たない）。
- **`role.transfer`**（新しい。TraceAspect の対象の外、DB と `dsl.parse` に依存しない）:
  - 読み込みと検証 `RoleTransferReader`、書き出し `RoleTransferWriter`、変わる点の一覧 `TransferPlanner`、指紋 `TransferFingerprint`、並べ方 `TransferOrder`。
  - 中身を伏せる受け渡しの型: `RoleTransferPayload`・`RoleTransferTree`・`RoleTransferDocument`・`TransferPlan`・`TransferRolePlan`・`TransferChange`・`TransferErrors`・`TransferRoleSettings`。
- **`role.repository`**: 書き出しと照らし合わせの読み取り（`findAllKeys`・`findAllOrderByRole`・`findAllOfRoles`）と、射影 `RoleKeyView`・`RoleSettingRow`。
- **`role.store`**: `RoleStore.applyTransfer`。新しいロールは JPA で作って flush し、設定の行は `JdbcTemplate` で消してから 1,000 件ずつのバッチで書く（文の上限は 10 秒）。あわせて `TransferWrite`・`TransferWritten`。
- **`role.service`**: `RoleTransferService`（`export`・`check`・`apply`。判定の順は NFR3.8）、入口 `RoleTransferSlot`（`Semaphore(1)`）、`RoleTransferLimits`（深さ 10・コレクションの別名 0・節 1,000,000・大きさは設定）、`RoleTransferBody`、結果の型。
- **`role.web`**: `RoleTransferController`（`ADMIN` の3つの口）、`RoleWebConfig`（上限の Bean と、確かめと適用の道の `RequestBodyLimitRoute`）、`RoleTransferLimitProperties`、`RoleTransferPaths`、DTO（`TransferCheckResponse`・`TransferResultResponse`・`TransferErrorItem`）。
- **`audit`**: `ROLE_TRANSFER_APPLIED`、`ROLE_TRANSFER_STALE`、写し方、受け取り、detail の JSON（16,384 文字を超えると要約に替える）。
- **`application.yaml`**: `org.springframework.jdbc.core.StatementCreatorUtils: OFF` と、その理由のコメント。
- **`role/package-info.java`**: 説明を今の作りに合わせて直した（コメントだけ。依頼者の決定）。
- **テスト**:
  - `role/transfer` に単体 5 クラス。`role/service`・`role/store`・`role/web` に結合 9 クラス。
  - 手伝い: `LargeRoleTransferYaml`（1ファイルで起動できる上限ちょうどの YAML の生成。テストつき）、`TransferYamlTrees`、`FailingSettingTrigger`、`RawTransferPost`。
  - 既存のテストに B6 の分を足した: `RoleAdminAuthorizationApiIT`、`RoleSecretLeakIT`、`RoleConnectionUsageIT`、`RoleBusyLogTraceIT`、`RoleBoundaryArchitectureTest`、`RoleApi`、domain と audit のテスト。
- **負荷の道具と文書**:
  - `perf/k6/scenarios.js`: `rolePrepare`・`roleExport`・`roleTransferLargeCheck`・`roleTransferLargeApply` を足した。
  - `perf/README.md`: B6 の場面と、流す順番を書いた。
  - `README.md`: 「権限の受け渡し（B6）」と監査の種類を書いた。

### B6.2 実装で決めたこと

- **判定の順（NFR3.8）**: 次の順で判定する。
  1. 本文の型と大きさ（web と既存のフィルター）
  2. 入口（取れなければ `ROLE_BUSY` と WARN 1回。監査には残さない）
  3. DSL の有無
  4. 本文を上限まで読む
  5. 上限つきの読み込み
  6. 中身の検証
  7. （適用だけ）置き換えるロールの行を ID の昇順に排他し、DSL を確かめ直し、一覧を作り直して指紋を照らす
  8. 書き込み
  9. 成功の出来事

  拒否したときは1つ目のトランザクションを巻き戻し、2つ目のトランザクションで失敗の出来事を出す。入口は `finally` で必ず放す。
- **排他の前に消えたロール**: 飛ばす。作り直した一覧の置き換え先に排他できなかったロールがあれば、`ROLE_TRANSFER_STALE` にする。
- **YAML の読み方**:
  - 版は整数の 1 だけを受け付ける。版が誤りなら、その1件だけを返す。
  - 入れ子の値は対応表だけを受け付ける。
  - 名前の誤りとロールの重なりがあった所の下は、確かめない。
  - カラムの `create`・`delete` は `COLUMN_AUX_NOT_ALLOWED` にする。
- **書き出しの二重引用符**: `security-design.md` 5節の一覧より広い側で囲む（読み込みで同じ名前に戻すため）。
- **指紋**: 項目を「文字数:値;」で区切って SHA-256 を取る。
- **書き出しの読み取り**: 書き出しは入口に入らない。

### B6.3 テストの量とカバレッジ

**1コマンドの検査（Step 65）**: colima の共有を確かめ、README の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流した実測の値（BUILD SUCCESSFUL in 13m 49s。B5 の 13 分 30 秒から約 19 秒延びた）。

- バックエンドは、単体 232 クラス・2,015 件（B5 の 1,914 件から +101）、結合 214 クラス・1,109 件（1,061 件から +48）。失敗 0・飛ばし 0。
- 対象DB のテストの SKIPPED は 0 件で、「コンテナの実行環境」の警告も 0 件だった。
- 単体テストの結果に、role の4つの `*PropertyTest` がある（B5 の3つに `RoleTransferRoundTripPropertyTest` が加わった）。
- 画面は 113 ファイル・1,043 件 passed（B6 は `frontend/` に触れていない）。Gitleaks は漏えいなし。OSV-Scanner は lockfile が変わっていないため UP-TO-DATE（前回の合格のまま）。

| パッケージ | 行 | 分岐 |
|---|---|---|
| `role.domain` | 93.8% → 94.2% | 85.0% → 85.4% |
| `role.repository` | 100.0% → 95.0% | 100.0% |
| `role.store` | 97.2% → 97.5% | 90.3% → 90.6% |
| `role.service` | 96.5% → 96.6% | 89.7% → 90.3% |
| `role.web` | 95.6% → 96.0% | 86.2% → 86.0% |
| `role.transfer`（新しい） | 97.8% | 91.6% |
| `audit.domain` | 99.5% → 99.6% | 95.1% → 94.7% |
| `audit.service` | 99.6% | 84.5% |
| 全体 | 98.0% → 97.9% | 92.5% → 92.4% |

（矢印の左は Step 48 の変更の前の値。）どのパッケージも下限（行 80%・分岐 70%）を満たす。新しいパッケージ `role.transfer` は、自動でパッケージごとの下限の対象になる。

**E2E（Step 66）**: 全体を1回流し、177 passed（6.8m）、失敗 0。報告の確かめの道具の結果は「残してはならない値は含まれていません（見つかった件数 0）」。報告のフォルダーは消した。

**節の上限**: 境界（1,000,000 と 1,000,001）は単体テストで確かめた。API での確かめは、上限の Bean の値の確かめだけである（B6-12）。結合テストの全体の実行の中で、API に 100 万の節を送ると OutOfMemoryError が起きた事実は、B6.5 の 1 と B6.6 のとおり Performance Validation に引き継ぐ。

### B6.4 計画との差

| ID | 差 | 理由 |
|---|---|---|
| B6-1 | `TransferAction` と `RoleAuditDetail.TransferRole` を足した（計画の 9.2 に無い） | 適用の detail に、ロールごとの要約の形が要るため。`role.transfer` と `role.domain` で同じ区分を使うため |
| B6-2 | 適用の detail の `fileSha256` は、本文を読む前の拒否（`DSL_NOT_APPLIED`）では null にした。`fingerprint` は、成功では一致した指紋、拒否では要求の指紋 | 本文を読んでいない時点では、SHA-256 が無いため |
| B6-3 | `TransferError.path` は、キーを点でつないだ形（例 `roles.営業.schemas.SALES`）にした。`entities.md` の例は斜線の形（`/roles/営業/...`） | 読み込みの部品の拒否（`SafeYamlResult.Rejected.path`）と DSL の誤りの一覧が点の形で、1つの応答の中の形をそろえるため |
| B6-4 | `errors` の1件を record `TransferErrorItem` にした。`RoleAuditDetail.Transfer`・`TransferSummary`・`TransferRole` と `RoleKeyView`・`RoleSettingRow` の `toString` は、数と ID だけを出す形にした（**TRACE の漏えいの直し**） | `RoleSecretLeakIT` で、共通の誤りの応答の変換（`common.error.web` の `ErrorResponseFactory`・`GlobalExceptionHandler`）が TRACE で追加の項目を文字列にし、ロールと対象の名前がログに出ると分かったため。`common` は変えていない |
| B6-5 | `role.repository` に読み取り3つと射影2つを足した。`RoleStore` のコンストラクターに `JdbcTemplate` を足した | 書き出しと照らし合わせに読み取りが要るため（後者は計画の 9.2 のとおり） |
| B6-6 | `RoleWebConfig`・`RoleTransferLimitProperties`・`RoleTransferPaths` を、Step 57 ではなく Step 55 で作った | 業務処理が上限の Bean を要り、Step 56 の結合テストで Spring を起動するため |
| B6-7 | 分割送信で本文を上限より多く読めたときと、読み込みの部品の `TOO_LARGE` は、どちらも 413 の結果にした | 大きさの誤りを1つの形にそろえるため |
| B6-8 | 上限ちょうどの YAML の生成の部品のテストを、ロール 20・1万カラム・名前 30 文字の形にした（計画の例はロール 26） | 26 では中身だけで 10 MiB を超えるため |
| B6-9 | `FailingSettingTrigger` を、H2 のソースのトリガー（`CREATE TRIGGER … AS $$…$$`）にした | H2 がテストのコンパイルの依存に無く、ビルドの設定を変えないため |
| B6-10 | `RoleTransferStaleIT` の「DSL の適用し直し」を、`ActiveDslModelHolder` の差し替えで起こした。適用・確かめ・重なりの結合テスト3つは `role/service` に置いた | 業務処理の層の結合テストのため（`unit-test-instructions.md` 6節との差） |
| B6-11 | 作るロールの名前の重なりは、API では鍵の待ちの上限切れの `ROLE_BUSY` で確かめた。`ROLE_NAME_DUPLICATE` に読み替える経路は `RoleTransferServiceTest` だけで確かめた | 作り直しと書き込みの間に待ち合わせの口が無く、API からは決まった形で起こせないため |
| B6-12 | **節の上限の確かめ（依頼者の決定 A と A'）**: 境界（1,000,000 は受け付け、1,000,001 は拒否）は、Spring を起動しない単体テスト `RoleTransferReaderTest#expandedNodeBoundary`（本物の `SafeYamlReader` と `RoleTransferReader`）で確かめた。API で 100 万の節を送る確かめは消し、`RoleTransferLimitsApiIT#expandedNodeLimit` で、上限の Bean が 1,000,000 を `SafeYamlLimits` に渡すことだけを確かめる。ビルドの設定は変えていない | 結合テストの全体の実行の中で、100 万の節を API に送ると `OutOfMemoryError: Java heap space` で 500 になったため（2回。テストの JVM のヒープ 1g を多くの Spring のアプリと分け合う中で起きた。名指しの実行では通った）。コレクションの別名を 0 にしているので、節を 100 万にするには本文に実際に 100 万の節が要り、小さい木では作れなかった |
| B6-13 | `role/package-info.java` の B3 の時点の説明を、今の作りに合わせて直した（コメントだけ） | 依頼者の決定（計画に無い変更） |
| B6-14 | 準備の YAML は、生成の部品ではなく k6 の台本の中で作る。準備の DSL は、`perf/make-large-dsl.mjs` ではなく `perf/dsl-timing.sh` が生成した 100 × 100 の DSL をそのまま使う。準備の場面ではグループ・メンバー・割り当てを作らない | 悪い側のロールには READ と FULL を交互に明示する形が要るため。`make-large-dsl.mjs` は名前の形を変えるため。B5 の各場面の setup がグループ・メンバー・割り当てを作るため（依頼者の決定 A で受け入れた） |
| B6-15 | k6 の `ROLE_TREE_SCHEMA`・`ROLE_TREE_TABLE` の既定を `perf`・`perf_t001` から `large`・`t_001` に替えた。`LargeRoleTransferYaml` の既定の名前も同じく替え、カラムの名前の頭を渡す引数を足した | 準備の DSL の名前に合わせるため（B4・B5 の場面にも効く） |

### B6.5 承認の場で確かめたいこと

**依頼者の答え（B6 の作業の中）**:
- Step 60 の OOM には A（境界を単体テストへ移す）、続けて A'（API の 100 万の節の確かめを消し、上限の Bean の確かめにする）。
- `package-info` の直しは A。
- Step 61 の計画との差 1〜3 は A（受け入れる）。

承認の場で確かめたいのは次の点です。

1. **テストの中の OutOfMemoryError と Performance Validation への引き継ぎ**（B6-12）: 結合テストの全体の実行の中で、100 万の節の YAML を API に送ると、テストの JVM のヒープ（1g）が尽きた（2回）。配備したアプリの最大ヒープも約 1g（コンテナの上限 2g の 50%）のため、**10 MiB の本文1つでヒープが尽きないか**と、100 万の節を API に送る確かめを、Performance Validation の `roleTransferLargeCheck`・`roleTransferLargeApply` で必ず確かめる。それまでは、確かめていない危険として残る。
2. **`TransferError.path` の点の形**（B6-3）: `entities.md` の例の斜線の形と違う。U6 の画面は点の形で読む前提になる。
3. **作るロールの名前の重なり**（B6-11）: `ROLE_NAME_DUPLICATE` への読み替えを、単体テストだけで確かめた形でよいか。
4. **受け入れた制約**（README の「権限の受け渡し（B6）」）でよいか。
   - 分割送信の本文は、既存のフィルターが上限まで先に読む（R-11）。
   - 本文をゆっくり送る管理者は、その間入口を持ち続ける（R-16）。
   - 書き出しと DSL の管理の操作は、入口に入らない。
   - 未認証の要求でも、最大 11MB の本文を読み捨てる（R-02）。
5. **k6 の既定の名前の変更**（B6-15）: B4・B5 の場面の前提も `large`・`t_001` になる。
6. そのほかの差（B6-1・B6-2・B6-4〜B6-10・B6-14）。

### B6.6 後に回すこと・引き継ぎ

- **Performance Validation**:
  - 準備の場面を1回流した後に、B4・B5 の場面、`roleExport`、`roleTransferLargeCheck`・`roleTransferLargeApply` の順に流す（最後の2つは、流し直すときに環境を作り直す）。
  - 上限ちょうどの YAML の確かめと適用は、配備と同じ形（コンテナの上限 2g・最大ヒープ 50%）の使い捨てのアプリで流す。ヒープの最大（`jvm.memory.used`）と OOMKilled の有無を記録する（上の 1。テストの中で OutOfMemoryError が起きた事実を前提に、必ず確かめる）。
  - AC3.1.17・NFR2.4・NFR2.5 などの時間の目標は、`traceability.json` で Performance Validation へ Deferred にした。
- **U6 role-admin-ui**: 確かめの一覧の画面と拒否の表示（AC3.1.9〜AC3.1.11 の画面の分）。`errors[].path` は点の形で読む。
- **group の R-05（H2 の例外の文への依存）**: B4.6 のとおり、学びの段で諮る。

### B6.7 見せるもの（`curl` の手順）

開発時の起動か、ビルドした WAR で行う。DSL を適用しておく。操作は監査に残るため、配備した環境では行わない。`$T` は管理者のアクセストークン（値を記録に残さない）。

```bash
B=http://localhost:8080; A=(-H "Authorization: Bearer $T" -H "Origin: $B")
curl -s "${A[@]}" -o roles.yaml -D - "$B/api/admin/role-transfer/export"        # 200、添付 role-permissions.yaml
curl -s "${A[@]}" -H 'Content-Type: application/yaml' --data-binary @roles.yaml \
  "$B/api/admin/role-transfer/check"                                           # 200、fingerprint と roles（変わる点なし）
FP=<確かめの fingerprint>
curl -s "${A[@]}" -H 'Content-Type: application/yaml' -H "X-Role-Transfer-Fingerprint: $FP" \
  --data-binary @roles.yaml "$B/api/admin/role-transfer/apply"                 # 409 ROLE_NO_CHANGE（往復で変わらない）
# roles.yaml の1つのロールの main を書き換え、check → apply すると 200 と created・replaced
unset T FP
```

## 単位の全体のまとめ（U4 role、B4〜B6）

- **作ったもの**:
  - ロールと権限の設定（B4）。
  - 割り当て・作業ロール・実効の権限の解決の口（B5、契約 C5）。
  - 権限の YAML の書き出し・確かめ・適用（B6）。
  - API は合わせて 24 の口（B4 10・B5 11・B6 3）。どの口も、401・403・200 の表で確かめた。
  - 移行は V11・V12。監査の種類は 8（`ROLE_*`・`WORK_ROLE_SWITCHED`）。
- **パッケージ**: `role.domain`・`role.repository`・`role.store`・`role.service`・`role.web`・`role.transfer`。どれもパッケージごとの下限（行 80%・分岐 70%）の対象（値は B4.3・B5.3・B6.3）。
- **依頼者がすでに答えた点**:
  - B4.5 の 1〜6: 1 は A（文字列1つのまま）。2〜4 は受け入れ。5 は受け入れ（B5 で数える）。6 は再現しないものとして記録して進める。
  - B5.5 の 1〜4: 1 は A。2 は detail を残さない。3 は U6 の設計で扱う。4 はテストを足さない。
  - B5-1: `GroupAdminListQueryCountIT` の期待を直す（決定 A）。
  - B6 の作業の中: Step 60 は A と A'、`package-info` の直しは A、Step 61 の差 1〜3 は A。
- **承認の場で確かめる点**:
  - B4.5 の 7: B4-1〜B4-5・B4-9・B4-12〜B4-15。
  - B5.5 の 5: B5-1〜B5-3・B5-5・B5-7〜B5-14。
  - B6.5 の 1〜6。とくに次の2点。
    - テストの中の OutOfMemoryError と、配備した形での大きい YAML のメモリを、Performance Validation で必ず確かめること。
    - `path` の点の形。
- **後に回すこと**:
  - **U5 navigation**: 業務のメニュー。`EffectivePermissionResolver` を使う。
  - **U6 role-admin-ui**: 画面と、確かめの一覧の表示。
  - **U7 app-frame-ui**: 作業ロールの表示。
  - **Performance Validation**: k6 の全場面と、大きい YAML のメモリ。
  - **Observability Setup**: NFR5.4。
  - **記録だけの項目**: H2 の例外の文への依存（group の R-05）と、想定外の DB の例外の手がかり（B4.6）。
- **traceability.json**: 単位の全体で 263 件。OK 212・Deferred 47・N/A 4。OK の target は既存の1ファイルを指す（括弧の中は補足）。Deferred は U3（済み）・U5・U6・U7・Performance Validation・Observability Setup。
