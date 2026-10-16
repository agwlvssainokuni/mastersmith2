# API（mastersmith2）

> 2026-10-16（記録のコミット `09b795d`）の Full rescan で作り直した。口の一覧は、アーキテクトが `@RestController` のクラスの `@ApiAccess` と各 Mapping と道の定数を検索で数えたもの（中身は読んでいない口が多い）。深く読んだのは `navigation/web/NavigationController.java` と `common/security/`。所見の本文はこの文書に K-51・K-61 を書いた。

## 外部の API（REST、Spring MVC、JSON）

エラー応答は RFC 9457 Problem Details に安定した `code` を足した形（`common/error`）。分類は口ごとの印 `@ApiAccess`（`PUBLIC`・`AUTHENTICATED`・`ADMIN`）。

| 道の根 | 分類 | 口の数 | 持ち主（controller） |
|---|---|---|---|
| `/api/auth`（ログイン・`/session/refresh`・`/session/logout`） | PUBLIC | 3 | `auth/web/AuthController.java` |
| `/api/registration`（リンクの確かめ・`/complete`） | PUBLIC | 2 | `invitation/web/RegistrationController.java` |
| `/api/appearance` | PUBLIC | 1 | `appearance/web/AppearanceController.java` |
| `/api/problems/{slug}` | PUBLIC | 1 | `common/error/web/ProblemTypeController.java` |
| `/api/me`（`/preferences` の GET・PUT、`/password`） | AUTHENTICATED | 3 | `user/web/MeController.java` |
| `/api/me/work-role`（GET・PUT） | AUTHENTICATED | 2 | `role/web/MeWorkRoleController.java` |
| `/api/me/permissions`（`/schemas`・`/tables`・`/columns`） | AUTHENTICATED | 3 | `role/web/MyPermissionsController.java` |
| `/api/me/navigation`・`/api/me/table-access` | AUTHENTICATED | 2 | `navigation/web/NavigationController.java` |
| `/api/admin/check` | ADMIN | 1 | `access/web/AdminCheckController.java` |
| `/api/admin/users`（一覧・`/{userId}/profile`・`grant-admin`・`revoke-admin`・`suspend`・`resume`・`reset-login-failures`） | ADMIN | 7 | `useradmin/web/UserAdminController.java` |
| `/api/admin/invitations`（招待・一覧・送り直し・取り消し） | ADMIN | 4 | `invitation/web/InvitationAdminController.java` |
| `/api/admin/dsl`（状態・プレビュー・生成・ダウンロード・適用・履歴と戻し） | ADMIN | 10 | `dslmanage/web/DslAdminController.java` |
| `/api/admin/groups`（一覧・作成・詳細・変更・削除・メンバーの追加と削除） | ADMIN | 7 | `group/web/GroupAdminController.java` |
| `/api/admin/roles`（一覧・作成・詳細・変更・削除） | ADMIN | 5 | `role/web/RoleAdminController.java` |
| `/api/admin/roles/{roleId}/permissions`（`/schemas`・`/tables`・`/columns`・設定・`/clear`） | ADMIN | 5 | `role/web/RolePermissionController.java` |
| `/api/admin/roles/{roleId}/assignments`（一覧・割り当て・利用者とグループの外し） | ADMIN | 4 | `role/web/RoleAssignmentController.java` |
| `/api/admin/groups/{groupId}/roles`・`/api/admin/users/{userId}/roles` | ADMIN | 2 | `role/web/RoleLookupController.java` |
| `/api/admin/role-transfer`（書き出し・取り込みの確かめと適用） | ADMIN | 3 | `role/web/RoleTransferController.java` |

- 合計 65 口（上の表の数え。`ErrorPathController` の `/error` は数えない）。業務データ（行の一覧・詳細・作成・更新・削除）の口は**無い**（K-49、本文は `architecture.md`）。
- ログインの応答（`TokenResponse`）は `CurrentUserResponse` を含み、画面へ `admin`（管理者の印）を渡す（K-53）。
- `/actuator/health` は公開（前の記録。今回は `config/SecurityConfig.java` を開いていない）。

### 今回深く読んだ口: `GET /api/me/navigation`・`GET /api/me/table-access`

- `navigation/web/NavigationController.java`（道の根 `/api/me`、55 行。`@ApiAccess(AUTHENTICATED)`、50 行）。
- `GET /api/me/navigation`: 作業ロールで枝を落とした業務のメニューの木。
- `GET /api/me/table-access?schema=&table=`: 200 は `schemaName`・`tableName`・`displayName`・`main`（`READ` か `FULL`）。見られなければ 403 `ACCESS_DENIED`（見られない理由 NONE・DSL に無い・DSL が無い を区別しない）、引数が欠け・空なら `fieldErrors` の無い 400 `VALIDATION_FAILED`。CREATE・DELETE・カラムの主権限・主キーの有無は返さない（K-50、本文は `architecture.md`）。

## 画面の道（SPA）

- `/tables?schema=&table=&item=`: テーブルの置き場（`features/tables/registration.ts`、`access: 'LOGGED_IN'`）。作るのは `toTablePlaceholderPath` の1か所（K-52、本文は `architecture.md`）。
- 管理の画面は `/admin/**`、自分の設定は `/preferences` など（流し読み）。

## 内部の口: DSL のモデル

適用中の DSL は `dsl/service/ActiveDslModelProvider.java` から変更できない値 `DslModel` として読む。後の Intent（業務データの一覧・詳細）が使う提供口である。

**K-51 DSL のモデルは一覧・詳細の画面に要る情報（主キー・ビュー・列の表示の設定・選択肢）を持つが、まだ誰も使っていない（`dsl`）**

- 確かめた事実:
  - `dsl/domain/DslTable.java`: `primaryKey`（主キーのカラムの物理名の一覧。無ければ空、36 行）、`view`（ビューなら true、35 行）、`foreignKeys`、`columns`（DSL の順を保つ対応表、1件以上）。
  - `dsl/domain/DslColumn.java`: `dbType`（名前・長さ・精度・小数の桁・NULL の可否）、`formPart`（11種）、`search`（`enabled`・`operator`・`defaultValue`・`collapsed`）、`list`（`visible`・`order`・`width`・`sortable`・`defaultSort`・`format`）、`detail.visible`、`validations`（`required`・`unique`・長さ・範囲・`pattern`）、`options`（`FIXED`・`REFERENCE`・`LOOKUP`）。`ListFormat` の Javadoc に「書式の中身は画面の Intent で決める」とある。
  - `dsl/domain/DslModel.java`: 書式の版 2、スキーマは検証でちょうど1つ（`schema()` は1つでないと例外）。テーブルは `findTable(TableRef(schema, name))` で引く（大文字と小文字を区別する）。
  - `DslForeignKey.referencedTable`・`OptionSource.table` はテーブルの物理名だけで、スキーマ名を持たない（スキーマが1つの前提）。
  - `ActiveDslModelProvider` の Javadoc: `pattern` は正しい正規表現かを確かめただけで値に当てはめたことは無く、当てはめるときは時間の上限つきにすること。
  - 識別子の引用の入り口 `targetdb/domain/SqlIdentifiers.java`（`quote`・`quoteKnown`。DB の種類の引用符で囲み、中の引用符を二重にする。NUL を拒否）は Javadoc で「今は使わない。利用者の入力から識別子を作らない」。
  - 見本の対象DB（`docker/targetdb/postgres/01-sample-schema.sql`）には、単一の主キーの表（`customers`・`orders`）、複合の主キーの表（`order_items` の `(order_id, line_no)`）、引用符と空白と `;` を含む名前の表、ビュー（`customer_orders`）がある。主キーの無い表は見本に無い（grep で確かめた）。
- 今回の Intent への意味: 主キーの有無による操作の可否の材料は `DslTable.primaryKey` の空かどうかと `view` でそろっている。主キーの無い表の確かめには、見本の DB かテストの表を足す必要がある。行の識別（詳細の道・更新・削除の対象）は複合の主キーと特殊な文字を含む値を扱う必要があり、画面の道の組み立て（エンコード）とサーバー側の識別子の引用（`SqlIdentifiers` を DSL の名前の一覧に照らして使う）が要る。`team.md` の「信頼できない入力」の決まりは DSL の YAML について書かれており、道に乗る主キーの値・検索の値の扱いは要件・設計で決める。
- 見立て（未検証）: `DslModel` は変更できない値のため、業務データの機能は `ActiveDslModelProvider` だけに依存すれば足りる見込み。`dsl` への逆向きの依存を止める規則があるかは今回読んでいない（`dependencies.md`）。

## 内部の口: 権限の解決

`role/service/EffectivePermissionResolver.java`（`effectiveWorkRole`・`snapshotFor`・`resolve`）。中身と依存してよい相手の制限は K-50（`architecture.md` の Interaction Diagrams 2）。

## API を足すときの決まり

**K-61 業務データの API を足すときに当たる決まり（API の分類・安全の決まりの差し込み・境界テスト）**

- 確かめた事実:
  - 新しい口には同じ変更で `@ApiAccess` を付ける。`ADMIN` は `/api/admin` の下だけ、`AUTHENTICATED` は `/api/` の下で管理の道の外（`common/security/ApiAccess.java` の Javadoc）。`ADMIN` の道は `/api/admin` と `/api/admin/**`（`access/domain/AdminPaths.java`）、`/api/**` の既定はログインだけ（`access/web/AdminApiDefaultAccess.java`）。検査はテストの `ApiAccessArchitectureTest`（印の書き忘れ・二重・道との食い違い）と `ApiAccessConsistencyIT`（印と本番の決まりの一致。ファイルの有無だけ確かめた）。
  - 安全の決まりの差し込み口 `common/security/SecurityRuleContributor.java` の order は機能の名前で 100 台ずつ（`auth` 100 台・`access` 200 台・`invitation` 300 台・`appearance` 400 台、本番は x10）。重なると起動を失敗させる（`SecurityExtensionValidator`）。
  - 置き場の API の 403 は `access` に依存せず、共通の問題の種類の一覧から code `ACCESS_DENIED` で引く（`NavigationController` 64・79 行）。
  - 手元の監視の警報 `ms-forbidden` は 403 の全体の数を数える（`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`status="403"` の `increase` の合計。式だけ読んだ）。前の Intent の持ち越し D2（置き場の 403 を分けるか）がある。
- 今回の Intent への意味: 業務データの API は利用者の作業ロールで判定するため `AUTHENTICATED` の分類になる見込みで、判定は API ごとにサーバー側で行う（`project.md` の Mandated）。`team.md` の「役割・権限の機能」の必須のテスト（API ごとの 401・403・200、要る権限だけを欠く利用者での 403、API の分類の網羅、割り当ての変更の直後の切り替え、IDOR・一括代入の防止）がすべて当たる。業務データの 403 が増えると `ms-forbidden` のしきい値（20）に届きやすくなる。

## Sources

- アーキテクトの検索（`@RestController`・`@ApiAccess`・各 Mapping・道の定数、2026-10-16）。
- 開発担当の調査の APIs Discovered・K-51・K-61。
