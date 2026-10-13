# 結合テストの手順（integration-test-instructions）

Intent `261004-role-menu` の結合テストの手順です。Test Strategy は Standard です。そのため、単位の境目と単位の間のやり取りを結合テストで確かめます。対象は次の2つです。

- **バックエンドの結合テスト**（`*IT`、`./gradlew verify` の 6 の段）。組み込みの H2 の内部DB と、対象DB の3種類のコンテナを使います。
- **画面からの一連の操作**（E2E、`./gradlew e2eTest`）。実際のブラウザの検査（axe）と画面の時間の測りを含みます。

単体テストの手順は、各単位の `unit-test-instructions.md`（`aidlc/spaces/default/intents/261004-role-menu/construction/<単位>/code-generation/`）にあります。この文書では繰り返しません。テストのクラスの道は `backend/src/test/java/cherry/mastersmith/`・`frontend/src/`・`frontend/e2e/` を省いて書きます。

## 1. 準備

- `build-instructions.md` の 3.1（colima と `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）と 3.2（Mailpit）を済ませておきます。
- テストのデータは、テストごとに用意して巻き戻します。実行の順には依存させません（`team.md` の Testing Posture）。
  - 内部DB は組み込みの H2 です。この Intent で足した結合テストはすべて内部DB を使います。対象DB のテストは、DSL の生成とスキーマの読み取り（U2 で版 2 のスキーマ名を足した）で3種類とも毎回流れます。
  - 時刻は注入した時計で動かし、`sleep` と実時刻に頼りません。
  - 同時の重なりは、スレッドの数ではなく待ち合わせの手伝いで確実に作ります（group の `testsupport`、role の `TestRoleBarrier`（`RoleBarrier` の差し替え）。`team.md` の「待ち合わせで確実に作る」）。違反のテストは先の側を確定させてから書く形、上限切れは H2 の上限まで放さない形です（`project.md` の学び）。一意の鍵・主キーの待ちの上限は H2 の既定の約 2 秒です（NFR 設計で承認済みの 3 秒から変えた）。
- E2E は1つの WAR と内部DB と初期管理者を共有するため、ファイル名の番号の順に1本ずつ流します（`workers: 1`）。150・170 は前のテストが作った状態に頼らず、DSL・ロール・利用者を自分で作ります。初期管理者の状態とロールは変えません（`team.md` の Testing Posture）。

## 2. 流し方

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

# バックエンドの結合テスト（件数とカバレッジを実測するときは clean を付ける。流した後に pmset -g log を見る）
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

# 結合テストだけ（開発中の途中の実行）
./gradlew :backend:integrationTest

# 境目ごと（例。開発中の途中の実行だけに使い、件数とカバレッジの報告には使わない）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.*' --tests 'cherry.mastersmith.group.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.navigation.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*' --tests 'cherry.mastersmith.dslmanage.generate.*'

# E2E（Mailpit を起動して。もとから動いていれば止めない）
docker compose --profile mail up -d mailpit
caffeinate -dims ./gradlew e2eTest
```

- テストを流している間は、同じ作業フォルダでビルドを動かしません（`build-instructions.md` の 6節）。
- 1つのファイルだけの E2E は `./gradlew :backend:bootWar` の後に `(cd frontend && npx playwright test e2e/<ファイル>)`。統合の前とリリースの前の判定は、全体（`./gradlew e2eTest`）の結果だけを使います。

## 3. 単位の境目の結合テスト

この Intent の単位の依存は U2（DSL の版 2）→ U4（ロール・権限）→ U5（業務のメニュー）→ U7（骨組みの画面）が中心で、U3（グループ）と U4 は削除の問う口で互いに結ばれます。U1（API の分類の印）はすべての API に効きます。

| 境目 | 主なテストクラス | 確かめること |
|---|---|---|
| U1 API の分類 ↔ すべての API（契約 C1） | `ApiAccessArchitectureTest`・`ApiAccessRulesTest`・`PublicApiInventoryTest`・`ApiAccessConsistencyIT`・`access/web/ApiDefaultAccessIT` | すべての口が分類の印を1つだけ持つ。静的な検査と実行時の検査の口の集合が一致し空振りしない（口の数の下限）。印と安全の決まりの判定が3つの主体（未ログイン・管理者でない利用者・管理者）で一致する。PUBLIC の口が一覧と一致する。B3〜B7 で足した口（グループ・ロール・割り当て・作業ロール・受け渡し・業務のメニュー・自分の権限）もこの検査に入る |
| U3 group ↔ U4 role（削除してよいかを問う口。契約 C4、DF1: A） | `role/service/RoleGroupDeletionGuardIT`・`RoleGroupDeletionGuardTest`、`role/web/GroupDeletionWithRoleIT`・`GroupRoleAssignmentConcurrencyIT`、`group/web/GroupConcurrencyIT` | ロールの割り当てが残るグループは削除できず（409 `GROUP_IN_USE`）、グループ・メンバー・割り当てが変わらない。グループの削除とロールの割り当ての追加が同時に重なっても、消えたグループに割り当てが残らない。B3 の仮の実装（数 0・`Allowed`）が B5 で本物に置き換わり、仮の実装が残っていない |
| U3 group ↔ U4 role（所属の読み取りの口。契約 C4） | `group/service/GroupMembershipQueryIT`・`GroupMembershipQueryCountIT`、`role/service/EffectivePermissionQueryCountIT`・`EffectivePermissionConsistencyIT` | 利用者の所属するグループを1回の問い合わせで返し、ロールの解決が利用者への直接の割り当てとグループの割り当てを合わせる（同じロールは1つ）。要求ごとの問い合わせの数がメンバー・グループの数に比例しない |
| U4 role の解決の口 ↔ U5 navigation（契約 C5） | `navigation/web/NavigationMenuAccessConsistencyIT`・`NavigationApiIT`・`NavigationDslSwapIT`・`NavigationFailureIT`・`NavigationConnectionUsageIT`、`navigation/service/NavigationCallCountTest` | メニューの絞り込みと置き場の判定が、解決の口の実効の主権限と一致する（テーブルが NONE なら出さない、子の無いまとまりを残さない）。navigation は `ActiveDslModelProvider.current()` と `snapshotFor(userId)` を1回ずつだけ呼ぶ。DSL の差し替えと重なっても古いモデルと新しいモデルが混ざらない。1要求で同時に持つ接続は1本まで |
| U4 role の割り当て・作業ロール ↔ 次の要求（FR4.4・FR5.2） | `role/web/RoleChangeVisibilityIT`・`WorkRoleSwitchApiIT`・`WorkRoleConcurrencyIT`・`RoleMeAuthorizationApiIT`・`MyPermissionsApiIT` | 割り当て・外し・作業ロールの切り替えの直後の次の要求で、作業ロールの選択肢と実効の権限がサーバー側で変わる（トークンに入れない）。割り当て・外し・ロールの削除・切り替えが同時に重なっても、割り当ての無いロールが作業ロールに残らない |
| U2 DSL の版 2 ↔ U4 権限の対象（契約 C3、FR2.3・FR2.4・FR7.5） | `role/web/RolePermissionApiIT`・`RoleTransferApiIT`・`RoleTransferStaleIT`、`role/domain/PermissionTreeBuilderTest`、`dslmanage/service/DslStartupIT`・`dslmanage/web/DslAdminApiIT` | 権限の木の対象（スキーマ・テーブル・カラム）と表示名は適用済みの版 2 の DSL から出る。DSL を適用し直して消えた対象の設定は名前で残り「今の DSL に無い」と出る。DSL が無いときは権限の設定と import を拒否する。確かめの後に DSL を適用し直すと、適用は拒否される（指紋の不一致） |
| U2 DSL の版 2 ↔ 既存の DSL の管理と起動 | `dslmanage/service/DslStartupIT`・`dslmanage/web/DslAdminApiIT`・`DslConcurrencyIT`、`dsl/DslSchemaPublicationIT` | 版 1 の DSL は受け付けない。適用中の版 1 の DSL がある内部DB で起動すると DSL が無い扱いで起動する。WAR の中の JSON Schema は v2 の2か所だけ |
| U3・U4 ↔ 監査（契約 C10、移行 V10） | `audit/service/GroupAuditIT`・`GroupAuditWriteFailureIT`、`role/web/RoleAuditIT`・`RoleTransferAuditIT`・`WorkRoleSwitchAuditIT`・`RoleConflictAuditIT`・`RoleAuditWriteFailureIT`、`audit/domain/AuditMigrationCompatibilityIT`、`role/RoleSchemaIT` | 操作した人・対象（ロール・グループ・利用者）・変えた中身・結果が行に残る。監査の書き込みが失敗しても操作の結果は変わらない。V10〜V12 は表と列を足すだけで、前の版の監査の読み書きの形が保たれる |
| U4 role ↔ 既存の利用者の管理（U6 の利用者のロールの表示） | `role/web/RoleAssignmentApiIT`、画面の `features/useradmin` のテスト | 利用者のロールの読み取りの応答に、パスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値が無い |
| U7 骨組みの画面 ↔ 作業ロール・業務のメニューの API（契約 C8・C9） | 画面の `app/work-role/WorkRoleProvider.test.tsx`・`WorkRoleSwitcher.test.tsx`・`app/navigation/BusinessNavigationProvider.test.tsx`・`useReloadTriggers.test.tsx`・`app/layout/ShellLayout.test.tsx`、E2E の 150（6a〜6e）・170 | 作業ロールを切り替えると業務のメニューが読み直されて変わる。この段の直し（Q3 A）で、切り替えの後の読み直しが失敗したら前のロールの木を残さず失敗の表示を出す。修飾キーつきのクリックは既定の動きに任せる（Q3 D） |
| U6 画面 ↔ U3・U4 の API（契約 C6・C7） | 画面の `features/roleadmin/api/roleAdminApi.test.ts`・`features/groupadmin` のテスト・`features/roletransfer/useRoleTransfer.test.tsx`、E2E の 150 の手順 7（見本の形の比べ） | 画面が送る要求は決めた項目だけ。本物の応答と E2E の見本の項目の名前と型が一致する（`support/shapeCompare.ts`） |

- この段で手が入る境目: 権限の YAML の上限（U4 の `RoleTransferLimits` と道ごとの本文の上限、画面の `transferLimits.ts`。節 500,000・5 MiB）、見えない文字だけの名前（U3・U4 の名前の型と画面の `validateAdminName.ts`）。上の表の `RoleTransferApiIT`・`RoleTransferLimitsApiIT`・`RoleTransferExportLimitIT`・`GroupAdminApiIT`・`RoleAdminApiIT` で既存の確かめが通ることを見ます。
- B9 の関門の件数（clean 付きの実測）: 単体 2,082 件・結合 1,165 件（Intent の始めの B2 の前は単体 1,577 件・結合 712 件）。この段の実測は `test-results.md` に書きます。

## 4. 対象DB の3種類

| テストクラス | 種類 | 確かめること |
|---|---|---|
| `targetdb/*/MysqlSchemaQueriesIT`・`MariadbSchemaQueriesIT`・`PostgresSchemaQueriesIT`（`AbstractSchemaQueriesIT`） | 3種類 | 読み取り専用のアカウントでスキーマ・テーブル・カラム・キーを読める |
| `targetdb/*/MysqlTargetSchemaReaderIT`・`MariadbTargetSchemaReaderIT`・`PostgresTargetSchemaReaderIT` | 3種類 | 対象DB の読み取りの口の結果 |
| `dslmanage/generate/DefaultDslGeneratorMysqlIT`・`DefaultDslGeneratorMariadbIT`・`DefaultDslGeneratorPostgresIT`（`AbstractDefaultDslGeneratorIT`） | 3種類 | 生成した既定の DSL が版 2 で、スキーマ名を書き、接続の項目を書かない（U2 の D-24） |
| `dslmanage/web/DslTargetDbIT` | PostgreSQL | 照合と `SCHEMA_MISMATCH`（スキーマ名が違えば1件、DSL のスキーマ名だけ） |
| `targetdb/TargetDbStartupIT`・`TargetDbSecretLeakIT` | — | 接続の設定が無い・不正でも起動を続ける。接続情報がログ・応答に出ない |

- イメージは版とダイジェストを固定（`targetdb/testsupport/TargetDbImages.java`）。H2 などの別の DB やモックで代用しません（`project.md` の Mandated）。
- 表を作るテストは、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消します（`team.md` の Testing Posture）。
- コンテナの実行環境が無いと SKIPPED と警告になります。**その状態では統合しません。** 期待は SKIPPED 0 件・警告 0 件です。

## 5. 画面からの一連の操作（E2E）と実際のブラウザの検査

### 5.1 ファイルと中身

| ファイル | 中身 | 本数 | この Intent での扱い |
|---|---|---|---|
| `010-skeleton` | 画面の骨格（ログイン画面の枠と CSP） | 数えない | 既存 |
| `020-auth` | 初期管理者のログインとログアウト、誤ったパスワード | 数えない | 既存 |
| `030-admin-access` | ログイン → 管理画面に入れるか → ログアウト | 数えない | 既存 |
| `040-dsl-admin` | DSL の管理の流れ。版 1 を貼り付けると版の誤りの文が出て、版 2 を貼り付けで投入・プレビュー・適用する | 数えない（既存の書き換え） | U2 で版 2 に合わせた |
| `050-display-accessibility` | ログインの画面の表示の設定の 20 組の axe と最初の画面の時間 | 数えない | 既存 |
| `060-invitation-accessibility` | 招待の管理の画面の 20 組の axe と時間 | 数えない | 既存（B8 で1回だけ落ち、再現しなかった） |
| `070-registration-accessibility` | 登録の完了の画面の 20 組の axe | 数えない | 既存 |
| `080-preferences-accessibility` | プリファレンスとパスワードの変更の画面の 20 組の axe と時間 | 数えない | 既存 |
| `090-invitation-registration-flow` | 招待から登録の完了の流れ | 前の Intent の1本 | 既存 |
| `100-app-text-contrast` | アプリ独自の CSS の文字のコントラスト（20 組） | 数えない | 既存 |
| `110-user-admin-flow` | 利用者の管理の流れ | 前の Intent の1本 | 既存 |
| `120-user-admin-accessibility` | 利用者の管理の画面の 20 組 × 12 回の axe と時間 | 数えない | 既存 |
| `130-admin-forbidden-accessibility` | 管理の画面の「権限が無い」の表示の 20 組の axe | 数えない | 既存 |
| `135-dsl-admin-accessibility` | DSL の管理の画面の版 2 の状態の 24 組（ブランドカラー × テーマ × 幅 360・768・1280）の axe とはみ出し | 数えない | U2 で足した |
| `140-role-admin-accessibility` | ロール・グループ・受け渡しの画面（S3〜S7）と利用者のロールの Modal（S9）の 20 組・12 の状態の axe、開いた Dropdown・Modal の収まり、幅3つ、道の扱い、画面の時間の測り（`u6-screen-ms`、記録だけ）。管理の API はすべて差し替え、書き換えをサーバーへ届けない | 数えない | U6 で足した |
| `150-role-permission-flow` | **F の流れ**: `E2EF_` の版 2 の DSL を適用 → 招待から利用者を作る → 画面でロールを作り、テーブルを READ にして保存し、利用者を割り当てる → 利用者の作業ロールと業務のメニューを API で確かめる → 2つ目のロールを割り当て、利用者で画面から作業ロールを切り替えるとサイドバーのテーブルの項目が消え、戻すと出る（6a〜6e、U7 で足した）→ 140・160 の見本と本物の応答の形を比べる | **この Intent の1本目** | U6 で足し、U7 で手順を足した |
| `160-app-frame-accessibility` | 骨組みの画面（作業ロールの切り替え・5段のサイドバー・置き場（準備中・権限なし）・自分の権限・ホームの案内3つ・畳んだサイドバー）の 20 組の axe、開いた一覧と5段のサイドバーの収まり、幅（360・768・1280 と悪い側の lg・dark・360px）、英語、画面の時間の測り（`u7-screen-ms`、記録だけ）。骨組みの API はすべて差し替え、内部DB に書かない | 数えない | U7 で足した |
| `170-menu-navigation-flow` | **I の流れ**: `E2EI_` の版 2 の DSL（5段のメニュー、末端がテーブル T1）を適用 → ロールを作り T1 を READ・T2 を NONE → 招待から利用者を作って割り当てる → 利用者で画面からログインし、5段を開いて T1 の置き場を開く（再読み込みでも同じ）→ T2 の置き場の道を直接開くと権限なし → 見本と本物の応答の形の比べ → `/logout` の直接の表示とユーザーメニューからのログアウト | **この Intent の2本目** | U7 で足した |

### 5.2 本数の決まり

- 流れの E2E は機能の Intent ごとに1本まで。この Intent は F（ロールベースの権限）と I（N 階層のメニュー）を束ねたため最大2本で、150（F）と 170（I）の2本です（`team.md` の Testing Posture）。
- 実際のブラウザの検査と画面の時間の測り（135・140・160）は操作の流れではないため数えません（`project.md` の学び）。既存の E2E を新しい形に合わせて書き換えたもの（040、`support/registeredUser.ts`・080 の開き口）も数えません。
- 150・170 は走らせるごとに一意の名前（`E2EF_`・`E2EI_` と実行の印）の DSL・ロール・利用者を作り、後始末をしません。適用中の DSL は 170 の `E2EI_` のものに置き換わります（170 は最後の番号で、後に流れるファイルはありません。app-frame-ui の読み直し R-06）。役割を変える操作は、その流れで作った利用者とロールだけを対象にします。

### 5.3 報告の扱い（秘密の確かめ）

1. `caffeinate -dims ./gradlew e2eTest` を流す（Mailpit を起動して。流した後に `pmset -g log` を見る）。
2. 報告の部品（`frontend/playwright-secret-check-reporter.ts`）の1行を記録する。期待は「残してはならない値は含まれていません（見つかった件数 0）」。部品は、実行ごとに作る仮の署名鍵・初期管理者のメールアドレス・仮のパスワードと、流れが作る利用者の値（値のファイル `support/secretValues.ts`）を、json の報告（添付と標準出力の base64 は復号して）・`frontend/test-results/` の下のすべてのファイルから探します。
3. `frontend/test-results/e2e-results.json` の `stats`（expected・unexpected・flaky・skipped）と、テストごとの題・状態・時間・注記を記録する。画面の時間は添付 `u6-screen-ms`・`u7-screen-ms` の名前と時間だけを読む（`performance-test-instructions.md` の 3節）。値を含みうるほかの添付は開かない。
4. `frontend/test-results/`（`frontend/playwright-report/` があればそれも）を中を開かずに消す。
5. 消したこと（ファイルとディレクトリの数）と、共有していないことを記録する。

- E2E の仮の資格情報は `webServer.env` ではなく Playwright のプロセスの環境変数で WAR に渡します（`project.md` の学び）。
- 失敗したときの画面の写し（`error-context.md`）に利用者の値が入ると、部品が見つけて失敗の知らせを出します。もともと失敗した実行のため合否は変わりません。原因を確かめた後に消します。
- トレースを有効にした実行（`E2E_TRACE=retain-on-failure`）は合否に使いません。

## 6. 期待する結果

- `./gradlew verify` が `BUILD SUCCESSFUL` になること。対象DB のテストの飛ばしが 0 件であること。
- 結合テストの失敗が 0 件であること。この段で2回落ちた画面のテスト（`ShellLayout.test.tsx` の作業ロールの置き場の幅のテスト）は、原因を直してから通ること（`team.md` の「同じテストが二度目に落ちたら原因を直すまで次へ進まない」）。
- E2E のすべてのファイル（010〜170）が通ること。B9 の関門は 230 件で、この段の画面の直しの後に流し直す。飛ばし・想定外・不安定が 0 であること。報告の部品の見つかった件数が 0 であること。
- カバレッジが全体と各パッケージで行 80%・分岐 70% 以上であること（`build-instructions.md` の 4節）。

実測は `test-results.md` に写し、目標ごとの判定は `build-and-test-summary.md` の Target Verification Matrix に書きます。

## Sources

- 各単位の `unit-test-instructions.md`・`code-generation-plan.md`（「Build and Test に引き継ぐこと」、U6 の 9節、U7 の 10節）・`code-summary.md`（`aidlc/spaces/default/intents/261004-role-menu/construction/<単位>/code-generation/`）
- `aidlc/spaces/default/intents/261004-role-menu/inception/contract-design/contract-summary.md`（C1〜C10 の向きと名前）
- `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/fix-notes.md`（3節〜8節）
- `backend/src/test/java/cherry/mastersmith/` の `*IT.java`・`*Test.java`（読み取り）、`frontend/e2e/*.e2e.ts` の先頭のコメント、`README.md` の E2E の表と「結果と報告」
- `aidlc/spaces/default/memory/team.md`（Testing Posture）・`aidlc/spaces/default/memory/project.md`（Testing Posture・Mandated・学び）

## Assumptions & Open Questions

None.
