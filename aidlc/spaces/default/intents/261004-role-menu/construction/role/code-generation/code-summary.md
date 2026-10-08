# コードの要約（Code Summary）— U4 ロール（role）

この単位は3つの Bolt（B4・B5・B6）で作る（計画 3節）。この文書は Bolt ごとの節を足していき、B6 の後に単位の全体のまとめを書く。

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
