# コード生成の計画（Code Generation Plan）— U7 骨組みの画面（app-frame-ui）、Bolt B9

U7 app-frame-ui は ui の単位で、ログインした後の画面の骨組み（サイドバー・トップバー）を、作業ロールと権限で絞った N 階層の業務のメニューに替え、テーブルの置き場と自分の権限の画面を足す。この Intent の最後の Bolt（B9）で、B1〜B8 は `develop` へ統合済み（B2 65a76c5・B1 44677cc・B3 f9d1134・B4 3d4f344・B5 b1c2760・B6 fcc25b5・B7 3a30982・B8 ea55919）。

- make-you-chic-ui の固定先の更新: `vendor/make-you-chic-ui` を e82b651 から 5bf1ffe（入れ子のサイドバーの口 `navSections`）へ上げる。**B9 の本体より前に、専用の短命のブランチの専用のコミットで行い、`develop` へ fast-forward で統合する**（`team.md` の Way of Working の例外、`project.md` の Mandated）。
- 骨組み（`app/`）: 作業ロール（`app/work-role/`、S1 のトップバーの切り替え）、業務と管理のメニュー（`app/navigation/` の `buildNavSections`・業務のメニューの状態・開閉の保存・読み直しのきっかけ）、`ShellLayout` の `navSections` への差し替え、ホームの案内、登録の型と骨組みの口（`beforeNavigate`・`clearLogoutIntent`）。
- tables（新しい機能）: S2 テーブルの置き場 `/tables?schema=…&table=…&item=…`。
- mypermissions（新しい機能、Should）: S8 自分の権限 `/me/permissions`。
- auth（既存の機能を変える。U7 の持ち物の外、機能設計 7節 (e)）: ログアウトを道 `/logout` の移動にし、一度きりの印で守る。
- E2E: U7 の画面の検査（160、本数に数えない）、I の流れ（170、この Intent の2本までの1本）、F の流れ（150）への作業ロールの切り替えの追加（同じ1本の中の手順）、ユーザーメニューの開き口の共通の関数（`e2e/support/userMenu.ts`）と既存の E2E の書き換え。

バックエンド・移行・Gradle・Vitest と Playwright の設定・`vite.config.ts`・`main.tsx`・依存（`frontend/package.json`・`package-lock.json`）は変えない。方法は test-after（末尾の Testing Contract のとおり）。Test Strategy は Standard、範囲は classic。

この計画は Part 1（計画）だけで、コードはまだ書いていない。手順はすべて未実施（`[ ]`）である。末尾の Testing Contract（`contract_sha256` は `sha256:0800cfde…7349`。渡された contract の見出しと JSON の塊を連結で貼り、バイトの一致を照合した）が Part 2 の方法と順の正である。計画の段で見つかった判断の要る9つの点（Q1〜Q9）は依頼者が答え（すべて A）、14節に確定の形で書き、この計画と `unit-test-instructions.md` に反映した。

## 1. 入力にした設計

すべて `aidlc/spaces/default/intents/261004-role-menu/` の下にある。

| 種類 | 文書 |
|---|---|
| 機能設計 | `construction/app-frame-ui/functional-design/` の `functional-spec.md`（1節の置き場・API・make-you-chic-ui の口、2節の D1〜D30、3節の流れ W1.1〜W9.1、4節の状態の遷移、6節のテストの方針、7節の上流との差 (a)〜(k)、9節の作業の順、11節の承認の場の決定と直し）・`frontend-components.md`（部品・型・文言・アクセシビリティ）・`traceability.json`（53 の AC。OK 40・Deferred 13） |
| NFR 要件 | `construction/app-frame-ui/nfr-requirements/` の `security-requirements.md`（NFR1.1・NFR1.4・NFR1.6〜NFR1.10）・`performance-requirements.md`（NFR2.2・NFR2.5〜NFR2.12）・`tech-stack-decisions.md`（NFR4.1〜NFR4.5・NFR6.1〜NFR6.8、上流との差 (a)〜(e)、受け入れた制約） |
| NFR 設計 | `construction/app-frame-ui/nfr-design/` の `logical-components.md`（L1〜L15、3節の登録の型と骨組みの口、4節のテストの置き場、5節の 160、6節の 170 と 150 への追加、9節の B9 への引き継ぎ、10節）・`performance-design.md`（4節の画面の時間の測り、5節の初回の JavaScript、8節）・`security-design.md`（5節のログアウトの印、6節の E2E の報告、9節） |
| 基盤の設計 | `construction/app-frame-ui/infrastructure-design/` の `infrastructure-specification.md`（2節の固定先の更新、6節）・`cicd-pipeline.md`（2節の固定先の更新の手順、3節の verify の段、4節の E2E、5節の統合の前の手元の確かめ、6節の入口の量の判定、7節・8節）・`monitoring-design.md` |
| Inception | `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md`（U7）、`inception/requirements-analysis/requirements.md`（FR5・FR6・FR9・FR10、NFR1・NFR2.2・NFR4・NFR6、C1・C3）、`inception/user-stories/stories.md`（US4.1・US4.2・US5.1・US5.3）、`inception/refined-mockups/`（`mockups.md` の S1・S2・S8・10a 節、`interaction-spec.md`、`accessibility-checklist.md`、`design-system-mapping.md`、`make-you-chic-ui-request.md`）、`make-you-chic-ui-request-2.md`（追加の依頼の3点）、`inception/contract-design/contract-summary.md`（C2・C8・C9）、`inception/delivery-planning/bolt-plan.md`（B9） |
| 先の Bolt の記録（読み取りだけ） | `construction/navigation/code-generation/code-summary.md`（B7。メニューと置き場の API の応答の形、U7 への引き継ぎ）、`construction/role/code-generation/code-summary.md`（B4〜B6。作業ロールと自分の権限の API、`ROLE_BUSY`）、`construction/cross-cutting/code-generation/code-summary.md`（B2。登録の型の `section`・`icon`、`LoginStateProvider.logout`・`useLogout()`、共有の木、ESLint の制限、B9 の最初の make-you-chic-ui と `shared/tree` の突き合わせ）、`construction/role-admin-ui/code-generation/code-summary.md` と `code-generation-plan.md`（B8。data router への差し替え、`useSlowNotice` の複製、E2E 140・150、入口の量の判定の形、U7 への引き継ぎ、Step 29 の DarkWake の件） |
| コード（読み取りだけ） | `frontend/src/main.tsx`・`app/`（App・layout・navigation・registry・routing・login-state・admin-forbidden・pages・i18n・testing）・`features/auth/`・`features/*/registration.test.ts*`・`frontend/e2e/`、`backend/src/main/java/cherry/mastersmith/navigation/web/`・`role/web/`（`MeWorkRoleController`・`MyPermissionsController` と応答の record）・`role/domain/RoleProblemTypes.java`、上流の make-you-chic-ui の作業フォルダ（`../make-you-chic-ui`）の 5bf1ffe（`git show` と `git diff --stat` だけ） |
| 決まりの層 | `aidlc/spaces/default/memory/org.md`・`team.md`（Way of Working のサブモジュールと make-you-chic-ui の取り込み、Testing Posture の N 階層のメニュー・役割と権限の画面の出し分け・E2E・画面のテスト、Code Style のフロントエンド）・`project.md`（Mandated の固定先の更新、Forbidden の vendor、学び: `vendorInstall` に `--ignore-scripts` を付けない・実際のブラウザの axe・開いた部品のはみ出し・E2E の見本の型・`webServer.env`・`caffeinate`・承認の場の時点・`aidlc/` の記録の扱い）・`phases/construction.md` |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログと各文書の「承認の場の決定と直し」の節から洗い出した）

| 段 | 監査ログ | 決定の文 | この単位で効く中身 | 計画での扱い |
|---|---|---|---|---|
| 機能設計（2026-10-05） | GATE_REJECTED → GATE_APPROVED（Approve） | 「推奨の案のとおり直す」 | R-01: 作業ロールのトリガーを `data-testid="work-role-switcher"` の入れ物で包み、E2E の開き口を共通の関数に替える。R-02: `pruneExpanded` は木を読み終えたときだけ、祖先は `currentAncestorIds` の中身が変わったとき。R-03: ロールが1つ・無いときは開いた一覧の押せない項目と `description`。S8 の道は名前を問い合わせの引数で受ける形 | D6・D16・D28 のとおり作る（Step 14・10・25）。S8 の道は 3.1 の A5 で実装と一致を確かめた |
| NFR 要件（2026-10-05） | GATE_REJECTED → GATE_APPROVED（Approve） | 「Major 11 件だけを直す」 | R-01: ログアウトの印はメモリ上の一度きりの値。R-02: 画面の時間は5回の中央値で判定、最大と1回目を記録、測れない回は Unverified | Step 18・27（10.1） |
| NFR 設計（2026-10-06） | GATE_REJECTED → GATE_APPROVED（Approve） | 「推奨の案のとおり直す」 | R-01: `path` の項目に任意の `beforeNavigate`。R-02: `LoginStateProvider` に任意の `clearLogoutIntent`、`LoginStateGate` に `useClearLogoutIntent()`、`LogoutPage` は `authSession.logout` を直接呼ぶ。R-03: 測り始めは `click` の捕捉の段、測り終わりは決まった判定の名前 | Step 8・18・25・27 |
| 基盤の設計（2026-10-06） | GATE_APPROVED（Approve） | Approve（まとめの確認は Looks correct） | 固定先の更新の手順（`cicd-pipeline.md` 2節）、E2E の 160・170 と 150 への追加、統合の前の手元の確かめ（5節）、入口の量の判定（6節、Q1: A） | Step 4〜6・25〜29・31・33 |
| 基盤の設計（承認の場の決定 (4)） | — | 指揮役から伝達 | **入口の量の判定は、前の値を先に取る** | Step 3（固定先の更新の前の値）・Step 5（固定先の更新の後の値）・Step 31（後の値と判定）。11節 |
| 基盤の設計（承認の場の決定 (5)） | — | 指揮役から伝達 | **新しい画面は入口の静的な import に入れず、manifest の `isDynamicEntry` で確かめる**（読み直し R-01 の「鍵が manifest に在ること」を条件に含める） | 判定の鍵は `src/features/tables/TablePlaceholderPage.tsx`・`src/features/mypermissions/MyPermissionsPage.tsx`・`src/features/auth/LogoutPage.tsx`。11節 |

B8 の承認の場（コード生成）で U7 に効く決定は無い（B8 の `code-summary.md` 5節の確かめたいことは U6 の中のこと）。

### 2.2 読み直しで残った指摘の扱い（承認の場で直していないもの）

| 指摘 | 中身 | この単位での扱い |
|---|---|---|
| 機能設計 R-04（Minor） | Dropdown がトリガーの `onClick` を上書きし、PUT の待ちの間も選べる | NFR 要件の NFR2.11 で直った形（待ちの間は Dropdown を押せないボタンに差し替え、フックが送信中の呼び出しを捨てる）で作る（Step 14・15） |
| 機能設計 R-05（Minor） | Toast と別の `aria-live` の二重の読み上げ | NFR 要件の差 (b) のとおり Toast だけで伝える（`WorkRoleAnnouncer` を作らない） |
| 機能設計 R-06（Minor）・NFR 要件の受け入れた制約 | 管理者の印を外されても次のトークンの更新まで管理の区画が残る | 受け入れた制約のまま（サーバーは 403 で守る）。`code-summary.md` の受け入れた制約に書く |
| 機能設計 R-07（Minor） | `/logout` を開くだけでログアウトする | NFR 要件の NFR1.8（一度きりの印）で直った形で作る（Step 18） |
| 機能設計 R-08（Minor）・NFR 要件 R-08・NFR 設計の L10 | 自分の権限の応答の型が未確定 | **計画の段で実装を読んで確かめた**（3.1 の A5）。`displayName` は `{ja, en}` ではなく要求の言語で選んだ文字列1つ。S8 は B9 に含める（14節 Q3: A） |
| 機能設計 R-09（Suggestion） | 追加の依頼の範囲がまとめより広い | 変えない（`make-you-chic-ui-request-2.md` はそのまま）。上流に入っていなければ差として記録する（Step 1・4） |
| NFR 設計 R-04（Minor） | `aria-expanded`・`aria-current`・キーボードだけで末端まで・深さの上限ちょうど（5 段）の描画を確かめるテストの置き場が無い | **手当てする**。`ShellLayout.test.tsx` に「5 段の木をキーボードだけで末端まで開いて移る」「`aria-expanded`・名前（日英）」「`aria-current` は1つ」「深さちょうど 5 段が欠けずに描かれる」を置く（Step 17）。性質 4 は `buildNavSections.property.test.ts`（Step 11） |
| NFR 設計 R-05（Minor） | `clearExpanded()` が鍵を消すのか `[]` を書くのか、持ち主が不明 | **手当てする**。`clearExpanded()` は `sessionStorage` の鍵を消す（`removeItem`）。骨組みの開閉の状態を空にしても、保存の効果は空のときに鍵を書き戻さない（空の集合は `removeItem`）。持ち主は `BusinessNavigationProvider`（未ログインの知らせで捨てる）と `LogoutPage`（印があったとき）。12節 D-7 |
| NFR 設計 R-06（Minor） | `secretValues.ts` の値の形の表が 110 の形だけ | **手当ては要らない見込み**。170 も `support/registeredUser.ts` の `createRegisteredUser`（`u7-perf-<印>@example.com`・`e2e-u7-pw-<16進>`）で利用者を作るため、表の形に当たる。氏名は流れの中で決めて `recordSecretValues` で書き、読み戻しで揃わなければ失敗にする（NFR 設計 6節）。Step 1 で表と作り方が今も一致することを確かめ、違えば表に足す |
| NFR 設計 R-08（Suggestion） | `AppRouter` の印を消す効果は効く場面が少ない | 承認済みの設計（`security-design.md` 5.1）のとおり置く。消す口が残るだけで振る舞いは変わらない |
| NFR 設計 R-09（Minor） | 差し替えの口の「PUT の後の状態」を5回の測りの間に戻す時点が無い | **手当てする**。`frameApiRoute.ts` に `reset()` を置き、測りの1回ごとの読み込み直しの前に呼ぶ。押すロールは見本の名前で選ぶ（10.1） |
| NFR 設計 R-10（Minor） | U6 の時計の記述が古い | 文書の記述だけ。計画では U7 の形（画面の中の `performance.now()`、`click` の捕捉の段）で測る |
| 基盤の設計 R-01（Major、承認の場の決定 (5)） | manifest の判定が、画面が入口に取り込まれると空振りする | 判定を「3つの鍵が manifest に `isDynamicEntry: true` で在り、かつ入口から静的にたどった集まりに無い。鍵が無ければ不合格」にする（11節） |
| 基盤の設計 R-02（Minor） | 前の値の記録先と、固定先の更新の増えが混ざる | 3点（B8 の後・固定先の更新の後・B9 の後）で取り、`generation-notes.md` に書き、`code-summary.md` の決まった節（「入口の量」）にまとめて Build and Test へ渡す（11節） |
| 基盤の設計 R-03（Minor） | 古いタブの読み込みの失敗の説明が U7 の画面に当たらない | 配備の段への引き継ぎ（13節）に、2つの場合（前の版の遅延読み込みの画面を持つ古いタブ、イメージだけで戻したときの新しい版のタブ）に分けて書く |
| 基盤の設計 R-04（Minor） | 基盤の traceability の N/A に単体テストで確かめる要件が混ざる | コード生成の `traceability.json` では、NFR1.1・NFR1.4・NFR2.9・NFR2.11・NFR4.1〜NFR4.3 をテストを target にした OK にする（Step 32） |
| 基盤の設計 R-05（Minor） | `vendorInstall`・`osvScan` が UP-TO-DATE で飛ばしうる | 固定先の更新の確かめでは `--rerun-tasks` を付けて流す。本命の確かめは `vendorBuild`・型の検査・既存の `ShellLayout.test.tsx`・`AppRouter.test.tsx` の通過（旧形の `navItems` の経路は 5bf1ffe でも残る）と E2E の全体（Step 5） |
| 基盤の設計 R-06（Suggestion） | 入口の量の判定が U6 の決定に依る | U6 は B8 で同じ形（`isDynamicEntry`）で判定済み（B8 の `code-summary.md` 3節）。U7 の判定は 11節の2条件で閉じる |

### 2.3 この計画での読み方（承認済みの文書との差。文書は書き換えない）

- **固定先の更新の統合**: `cicd-pipeline.md` 7節・`infrastructure-specification.md` 2節は「squash か fast-forward かは B9 の計画で決める」。この計画は、固定先の更新を**専用の短命のブランチから先に fast-forward で統合**し、B9 の本体は別のブランチから **squash** で統合する形にする（14節 Q1: A で確定）。
- **自分の権限の応答の型**: `frontend-components.md` 2節の `MyPermissionNode.displayName: LocalizedLabel` ではなく、実装どおり文字列（要求の `Accept-Language` で選んだ表示名）として写す（3.1 の A5、12節 D-3）。
- **作業ロールの切り替えの `ROLE_BUSY`**: 機能設計 D8 は `ROLE_NOT_ASSIGNED`・`VALIDATION_FAILED`・通信の失敗だけを挙げるが、実装の `PUT /api/me/work-role` は `ROLE_BUSY`（409）も返す。D8 と同じ扱い（Toast の文と作業ロールの読み直し）にする（3.1 の A2、12節 D-4、14節 Q6: A で確定）。
- **自分の権限の画面（S8、Should）**: B9 に含める（14節 Q3: A）。表示の言語を替えたら最上位から読み直す（14節 Q4: A、12節 D-5）。
- **ログアウトの画面**: `frontend-components.md` 8節の「`useLogout()` を呼ぶ」ではなく、`security-design.md` 5.2 のとおり `features/auth` の中から `authSession.logout` を直接呼ぶ（NFR 設計の承認の場の R-02）。
- **結果の伝え方**: 機能設計 D7・D8 の「Toast と `aria-live` の区画の両方」ではなく、Toast だけ（NFR 要件の差 (b)）。
- **切り替えの待ち**: `frontend-components.md` 3.2 の「トリガーに `aria-disabled`」ではなく、Dropdown を押せないボタンに差し替える（NFR2.11）。
- **E2E の番号**: 160（U7 の画面の検査、本数に数えない）・170（I の流れ）。`frontend/e2e/` の今の番号は 010〜150 で、どちらも空いている。1組あたりの時間の見積もり（10.1）で 10 分を超えないため、160 は1つのファイルにする。

## 3. 突き合わせ（API・make-you-chic-ui の口・先の Bolt）

### 3.1 使う API の実装との突き合わせ（navigation B7・role B5 の web の層を読んだ）

| # | API | 設計（functional-spec 1.2・frontend-components 2節） | 実装（`backend/src/main/java/cherry/mastersmith/…`） | 一致・計画での扱い |
|---|---|---|---|---|
| A1 | GET `/api/me/work-role` | 200 `{roles: [{roleId, name}], current: {roleId, name} または null}` | `role/web/MeWorkRoleController.read()` → `WorkRoleResponse(List<RoleRefResponse> roles, RoleRefResponse current)`、`RoleRefResponse(long roleId, String name)` | 一致。`parseWorkRole` は `roleId` を数（整数）、`name` を文字列として写し、`current` は null を許す |
| A2 | PUT `/api/me/work-role` | 本文 `{roleId}` だけ。204。拒否は `VALIDATION_FAILED`（400）・`ROLE_NOT_ASSIGNED`（409） | `switchTo`: `roleId` が null なら `VALIDATION_FAILED`（`fieldErrors` の `roleId` REQUIRED）、`Switched`・`Unchanged` は 204、`NotAssigned` は `ROLE_NOT_ASSIGNED`（409）、`Busy` は **`ROLE_BUSY`（409）** | `ROLE_BUSY` が設計に無い。D8 と同じ扱いにする（2.3、12節 D-4、14節 Q6） |
| A3 | GET `/api/me/navigation` | 200 `{items: [NavNode], emptyReason}`。`NavNode` は `{id, label: {ja, en}, icon, table: {schemaName, tableName} または null, navigable, items}`。`emptyReason` は `NOT_CONFIGURED`・`NOTHING_VISIBLE`・null | `navigation/web/NavigationController.navigation()` → `NavigationResponse(List<NavNodeResponse> items, String emptyReason)`、`NavNodeResponse(String id, DisplayLabelResponse label, String icon, NavTableRefResponse table, boolean navigable, List<NavNodeResponse> items)`、`DisplayLabelResponse(String ja, String en)`、`EmptyReason` は `NOT_CONFIGURED`・`NOTHING_VISIBLE` | 一致。`parseNavigation` は知らない `emptyReason` を通信の失敗にする（D3） |
| A4 | GET `/api/me/table-access?schema=…&table=…` | 200 `{schemaName, tableName, displayName: {ja, en}, main: READ または FULL}`。403 `ACCESS_DENIED`・400 `VALIDATION_FAILED` | `tableAccess(@RequestParam schema, table)` → `TableAccessResponse(String schemaName, String tableName, DisplayLabelResponse displayName, String main)`。欠け・空は `fieldErrors` の無い 400、見られないときは 403 `ACCESS_DENIED`（DSL に無いテーブルも同じ 403） | 一致。道の引数は `URLSearchParams` で作り、`item` は送らない（D21）。この 403 は `AdminForbiddenProvider` に渡さない（`useAdminForbidden` は管理の画面だけが使う。S2 は自分の権限なしの表示を出す） |
| A5 | GET `/api/me/permissions/schemas`・`/tables?schema=…`・`/columns?schema=…&table=…` | 200 `{workRole: {roleId, name} または null, items: [MyPermissionNode]}`。`MyPermissionNode` は `{schemaName, tableName, columnName, displayName: {ja, en}（未確定）, effective: {main, create, delete}, inMenu, hasChildren}` | `role/web/MyPermissionsController`（`PATH = "/api/me/permissions"`、引数の名前は `schema`・`table`、欠けは `VALIDATION_FAILED`）→ `MyPermissionNodesResponse(RoleRefResponse workRole, List<Item> items)`、`Item(String schemaName, String tableName, String columnName, String displayName, Effective effective, boolean inMenu, boolean hasChildren)`、`Effective(MainPermission main, boolean create, boolean delete)`、`MainPermission` は `NONE`・`READ`・`FULL`。**`displayName` は要求の言語（`DisplayLanguage`）で選んだ文字列1つ** | 道と引数は一致（機能設計の差 (j) は解消済み）。`displayName` の形が設計と違う → 文字列で写す（12節 D-3）。言語を替えたら S8 を読み直す（12節 D-5、14節 Q4）。NFR6.8 の「型を確かめる」は満たした |
| A6 | 共通 | 応答は型の項目だけを写し、崩れた本文は通信の失敗。トークン・401 の更新・`Accept-Language` は ApiClient に任せる | 既存の `shared/api-client/apiClient.ts`（`apiRequest`）が `Accept-Language` を表示の言語で付ける | 一致。画面は利用者を指す値を送らない（D3・NFR1.4） |

`backend/` は変えない。応答の形の本物との一致は、170（navigation・table-access）と 150 への追加（work-role）で確かめる（10.2・10.3）。S8 の形は画面のテストの見本と A5 の record を突き合わせて確かめ、E2E では比べない（自分の権限の画面は 160 で差し替えの見本だけを使う。S8 を含める（14節 Q3: A）ため、170 の手順 8 で `GET /api/me/permissions/schemas` の本物の形も比べる。10.2 の手順 8）。

### 3.2 make-you-chic-ui の 5bf1ffe の口との突き合わせ（機能設計 1.3・NFR 設計 2節の記録を正とし、計画の段で読み取りで確かめ直した）

計画の段で、上流の作業フォルダ（`../make-you-chic-ui`、`git log` の先頭が 5bf1ffe）で `git show 5bf1ffe:packages/make-you-chic-ui/src/components/AppShell/{Sidebar,AppShell,Topbar}.tsx`・`git diff --stat e82b651 5bf1ffe` を読んだ。このリポジトリの `vendor/make-you-chic-ui` には 5bf1ffe がまだ取得されていない（取得は Step 4）。

| # | 口 | 記録（機能設計・NFR 設計） | 計画の段の確かめ | 扱い |
|---|---|---|---|---|
| M1 | `AppShell` の `navSections`・`navExpandedIds`・`onNavExpandedChange`・`navLabels`・`topbarEnd`・`user`・`userMenuItems` | 記録のとおり。`navSections` があれば `navItems` は使われない | 一致（AppShell.tsx の props 32〜49 行。`navItems` は旧形として残る） | `ShellLayout` は `navSections` だけを渡す |
| M2 | `SidebarNavSection` `{id, heading?, items}`・`SidebarNavItem` `{id, label, icon?, href?, onClick?, current?, children?}`・`SidebarLabels` `{navigationLabel, expandGroup, collapseGroup}` | 記録のとおり | 一致（Sidebar.tsx 33〜58 行。既定の `navigationLabel` は「メインナビゲーション」） | D17 の「メインメニュー」「Main menu」を `navLabels` で渡す |
| M3 | `data-testid` | `sidebar-nav-<id>`・`sidebar-toggle-<id>`。旧形の `navItems` は `sidebar-nav-<href>` | 一致（Sidebar.tsx 133・160・254 行）。トップバーの畳むボタンは `sidebar-toggle`（Topbar.tsx 55 行）で、`sidebar-toggle-<id>` と前方一致で重ならないよう、テストは完全一致で探す | 既存のテストの書き換え（6節） |
| M4 | 開閉のボタン・リンクの読み上げ | `href` の項目は `<a>` に `aria-label`・`title`、`current` で `aria-current="page"`。子の開閉は `aria-expanded`・`aria-controls` | 一致（130〜160 行） | 既存の E2E の `getByRole('link', { name, exact: true })`（`support/adminLogin.ts` の `openSidebarItem` ほか）は、管理の項目の `aria-label` が登録の文言のままなので変わらず当たる見込み。Step 26 で確かめる |
| M5 | `Topbar` の文言 | 畳むボタン「サイドバーを開く」「サイドバーを折り畳む」とユーザーメニューのトリガー「〇〇のメニュー」は日本語の固定 | 一致（Topbar.tsx 54・69 行） | 受け入れた制約（機能設計 7節 (i)）。追加の依頼が入った版なら、英語の名前を渡す（Step 1・4 で判断） |
| M6 | `Dropdown` | e82b651 から変わらない。トリガーに `data-testid="dropdown-trigger"`・`aria-haspopup`・`aria-expanded`・`onClick` を上書き。項目の `disabled`・`description`（`aria-describedby`）あり、区切り線は無い | 一致（2つの版の差に Dropdown は無い） | D6・NFR2.11・`userMenu.ts`（12節 D-9） |
| M7 | パッケージ | 2つの版の間で `package.json`・`package-lock.json`・`LICENSE`・`Icon` に変更は無い | 一致（差は `docs/integration-guide.md`・`AppShell.css`・`AppShell.test.tsx`・`AppShell.tsx`・`Sidebar.tsx`・`AppShell/index.ts`・`src/index.ts`・`theme/contrast.test.ts`・`theme/semantic.css` の9ファイル、625 行の追加・26 行の削除） | `frontend/package-lock.json` は変わらない見込み（`file:` の依存の版が変わらない）。Step 5 で SHA-256 を比べる |
| M8 | 見た目の変更 | — | `semantic.css` に今の項目の左の線の色 `--color-sidebar-active-border` が足された（4つのブランドカラー） | 固定先の更新だけで既存の画面の見た目が変わりうる。Step 5 の E2E の全体（axe の 20 組を含む）で確かめる |
| M9 | 追加の依頼（`make-you-chic-ui-request-2.md` の3点） | B9 までに上流に入れば、その版を固定先にする | 計画の段では、上流の作業フォルダの先頭が 5bf1ffe で、3点を含む版は無い（取得していないため、上流の origin に新しい版があるかは未確認） | Step 1 で上流の origin を取得して確かめる（14節 Q2: A）。3点を含む版があればそれを、無ければ 5bf1ffe を固定先にして進め、残る3点を差として `code-summary.md` に記録し承認の場で伝える |

Step 1 で、取得した版のソースを同じ表の M1〜M7 について確かめ直し、`generation-notes.md` に「記録と一致」か差を書く。差があれば止まって依頼者に尋ねる。

### 3.3 先の Bolt から引き継いだことの突き合わせ（今のコードで確かめた）

| # | 引き継ぎ | 今のコード（develop ea55919） | 扱い |
|---|---|---|---|
| P1 | B8 の data router（NFR 設計 9節の確かめ (ii)） | `src/main.tsx` は `createBrowserRouter([{ path: '*', element: <App/> }])`＋`RouterProvider`（前処理と `StrictMode` を保つ） | 入っている。D27 の未保存の確かめはサイドバーの移動・ログアウトの道の移動で出る。`main.tsx` は変えない |
| P2 | B2 の `useLogout`（確かめ (iii)） | `LoginStateProvider.logout?` と `LoginStateGate` の `LogoutContext`・`useLogout(): () => Promise<boolean>` がある | `clearLogoutIntent` と `useClearLogoutIntent()` を同じ形（別の文脈、無ければ何もしない関数）にそろえる（Step 8） |
| P3 | B2 の登録の型の `section`・`icon` | `SidebarItemRegistration` に `section: 'ADMIN'`（必須）・`icon?`。今の `ShellLayout` は `icon` を使わず `home`/`list` を当て、`navigationItems.buildSidebarEntries` は `section`・`icon` を落とす | `buildNavSections` が `section`・`icon` を使う（D12）。`buildSidebarEntries` の扱いは 14節 Q5 |
| P4 | B2 の「B9 の最初の make-you-chic-ui の実物と `shared/tree` の形の突き合わせ」 | `src/shared/tree/`（`SharedTreeView`・`SharedTreeNodeItem`・`types.ts`）は自前の部品で make-you-chic-ui の部品を使わない | Step 5 で、5bf1ffe の `Sidebar` の開閉の口（`Set<string>`・`aria-controls`）と `shared/tree` の開閉の口（`expandedIds`・`onToggle`）を読み比べ、置き換える必要が無いこと（Sidebar はナビゲーション、共有の木は選びの木で用途が違う）を記録する |
| P5 | B8 の引き継ぎ「150 の 6 の後に作業ロールの切り替えとサイドバーの変化」 | `e2e/150-role-permission-flow.e2e.ts` の手順 6 は API で作業ロールとメニューを確かめ、手順 7 で見本の形を比べる | 10.3 のとおり手順 6 の後に画面の手順を足す |
| P6 | B8 の引き継ぎ「管理の区画の今の項目は詳細の道との前方一致」 | 機能設計 D15 で決まり（道の区切りでの前方一致、最も長い道） | `buildNavSections` の今の項目の決め方（Step 10） |
| P7 | B7 の引き継ぎ「置き場の引数は必ずエンコード。アイコンが変わったら `allowed-icons.txt` と `allowedIcons.ts` の両方を直す」 | `allowedIcons.ts` は `IconName` との両向きの型の検査を持つ | 5bf1ffe でアイコンは変わらない（M7）。`allowedNavIcons.test.ts` の通過で確かめる（Step 5） |
| P8 | `secretValues.ts` の値の形（NFR 設計 R-06） | 形の表は `u7-perf-…@example.com`・`e2e-u7-pw-<24 桁の16進>`。`createRegisteredUser` が同じ形で作る | 170 も `createRegisteredUser` を使う（2.2） |

## 4. Bolt・ブランチ・統合・コミットの区切り

### 4.1 2つのブランチと統合の形（14節 Q1: A）

| 項目 | 固定先の更新（Step 4〜6） | B9 の本体（Step 7〜34） | 出典 |
|---|---|---|---|
| ブランチ | `feature/261004-role-menu-b9-mycui`。`develop` の先頭から作る（worktree は使わない） | `feature/261004-role-menu-b9`。固定先の更新を `develop` へ統合した後の `develop` の先頭から作る | `team.md` の Way of Working |
| 中身 | `vendor/make-you-chic-ui` の固定先（gitlink）だけ。ほかのファイルは含めない | 骨組み・tables・mypermissions・auth・E2E・文書 | `project.md` の Mandated（承認を得た専用のコミット） |
| コミット | 1つ（P1）。メッセージに更新の前後のハッシュ（e82b651c… → 取得した版の40桁）と `git diff --stat` の要約を書く | 4.2 の C1〜C8 | `project.md` の Mandated |
| 統合の前の関門 | `verify`（`--rerun-tasks` の確かめを含む、Step 5）と E2E の全体（010〜150）。画面の見た目が変わりうるため E2E も流す | `verify` と E2E の全体（010〜170）、手元の確かめ（Step 33） | `team.md`、`cicd-pipeline.md` 2節・5節 |
| 統合の形 | `develop` へ **fast-forward**（固定先の更新の専用のコミットを `develop` に残す。`team.md` の例外） | `develop` へ **squash**（1コミット）。件名は「B9 骨組みの画面: 作業ロールの切り替え・N 階層の業務と管理のメニュー・テーブルの置き場・自分の権限・ログアウトの道」 | `team.md`、`bolt-plan.md` |
| 後始末 | 統合の後にブランチを消す | 同じ | `team.md` |

- どちらの統合も、実行の前に依頼者の承認を得る。プッシュは依頼者が行う。
- **`aidlc/` の記録**: 計画の承認の後、Step 1 の前に、`develop` の上で `aidlc/` の未コミットの変更（監査ログ・この計画・質問の記録）を記録のコミットにまとめることを提案する（承認の後に実行）。固定先の更新のブランチは `develop` の先頭から作るため、記録のコミットは fast-forward の妨げにならない。B9 の本体の squash の前にも `aidlc/` の未コミットの変更をコミットしておく。`git restore` で `aidlc/` を戻さない（`project.md` の Change Control）。
- **コード生成の段の承認の場**: B9 の本体の最後のコードのコミット（C8）の後、記録だけのコミットを積む前に開く（`project.md` の Change Control）。
- **長いコマンド**: `verify`・`e2eTest`・画面のテストの全体は `caffeinate -di` で包み、流した後に `pmset -g log` でその時間帯にスリープ（DarkWake を含む）が無かったかを確かめる。スリープがあって時間切れで落ちたときは、結果を使わずに流し直す（B8 の Step 29 の件）。テストを流している間に同じ作業フォルダでビルド（`frontendBuild`・`bootWar`）を流さない。テストの出力は要約（件数・失敗の名前）だけを見る。
- **colima**: `verify` の前に、colima が動いていて、ホームの共有（`colima status` の mount に作業フォルダの親が入る）と README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を確かめる（`project.md` の学び）。

### 4.2 B9 の本体のコミットの区切り（機能・場所ごと。各コミットの時点で型の検査・リンタ・そのコミットまでの画面のテストが通る形）

前の Bolt では、同じ機能のフォルダーの中を手順ごとにファイルで振り分けると、途中でビルドが通らない区切りが出やすかった（B8 の `code-summary.md` の冒頭）。B9 は、機能・場所ごとに区切り、骨組みの差し替えと既存のテストの書き換えを同じコミットに入れる。実行の前に依頼者の承認を得る。

| コミット | 中身 | 手順 | 通る形にする理由 |
|---|---|---|---|
| C1 | 骨組みの口（足すだけの互換の変更）: `app/registry/types.ts` の `beforeNavigate?`・`clearLogoutIntent?`、`validateRegistrations.ts`、`app/login-state/LoginStateGate.tsx` の `useClearLogoutIntent()`、`app/routing/AppRouter.tsx` の印を消す効果、それぞれのテスト | Step 8〜9 | 任意の口だけで、既存の登録と画面は変わらない |
| C2 | 骨組みの新しい部品（まだ `ShellLayout` につながない）: `app/work-role/`（API・状態・`WorkRoleSwitcher`）、`app/navigation/` の `tablePlaceholderPath`・`navExpansion`・`buildNavSections`・`navigationApi`・`useReloadTriggers`・`BusinessNavigationProvider`、それぞれのテスト | Step 10〜15 | 新しいファイルだけ。どこからも使われなくても型の検査とテストは通る |
| C3 | 骨組みの差し替え: `App.tsx` の並び、`ShellLayout.tsx`（`navSections`・開閉・`navLabels`・`topbarEnd`・`beforeNavigate`）、`HomePage.tsx` の案内、`app/i18n/messages/ja.ts`・`en.ts`、`navigationItems.ts`、**既存のテストの書き換え**（6節の (a)〜(f)） | Step 16〜17 | 差し替えと、それで変わる既存のテストを同じコミットにする |
| C4 | ログアウトの道: `features/auth/logoutIntent.ts`・`LogoutPage.tsx`・`registration.ts`・`loginStateProvider.ts`、テストと既存のテストの直し（`features/auth/registration.test.ts`・`features/preferences/registration.test.tsx`） | Step 18〜19 | auth の中で閉じる（骨組みは C1 の口だけを読む） |
| C5 | tables（S2）: `features/tables/` のすべてとテスト | Step 20〜21 | 新しい機能のフォルダーだけ |
| C6 | 〔Should、14節 Q3: A で含める〕mypermissions（S8）: `features/mypermissions/` のすべてとテスト | Step 22〜23 | 新しい機能のフォルダーだけ。骨組みの入口（作業ロールの一覧・ホーム）は C3 で「道 `/me/permissions` が登録されているときだけ出す」形にしてあるため、C6 を外しても通る（12節 D-6） |
| C7 | E2E: `e2e/support/userMenu.ts`（新）・`registeredUser.ts`・`080-preferences-accessibility.e2e.ts` の開き口の書き換え、`frameApiRoute.ts`・`navTreeFixtures.ts`・`u7Measure.ts`（新）、`160-app-frame-accessibility.e2e.ts`・`170-menu-navigation-flow.e2e.ts`（新）、`150-role-permission-flow.e2e.ts` への追加 | Step 25〜29 | E2E は `verify` の外。`src/` の型は `import type` だけで読む |
| C8 | 文書: `README.md`（E2E の表の 160・170、150 の説明の追加、画面の節）、`frontend/src/features/README.md`（`beforeNavigate`・作業ロールと業務のメニューのフック・置き場の道） | Step 30 | 本体のコードを含まない |
| （記録） | `aidlc/` の下だけ（計画のチェック・`code-summary.md`・`source-manifest.json`・`traceability.json`・`generation-notes.md`） | Step 32・34 | 承認の場は C8 の後、記録のコミットの前に開く |

## 5. 作るもの・手を入れるもの

道は `frontend/` の下（`vendor/` と `README.md` を除く）。新しいファイルには Apache License 2.0 のヘッダー（`/* … */`、2026、agwlvssainokuni）を付ける。

### 5.1 固定先の更新（P1）

| 道 | 変更 |
|---|---|
| `vendor/make-you-chic-ui`（gitlink） | e82b651 → 5bf1ffe（または Step 1 で決めた版）。中身は変えない（`project.md` の Forbidden） |

### 5.2 骨組みの口（C1、L15）

| 道 | 変更 |
|---|---|
| `src/app/registry/types.ts` | `path` の項目に任意の `beforeNavigate?: () => void`（`action` の項目は `beforeNavigate?: never`）。`LoginStateProvider` に任意の `clearLogoutIntent?: () => void` |
| `src/app/registry/validateRegistrations.ts` | `beforeNavigate` は `path` の項目だけに許し、関数でなければ問題として起動を止める |
| `src/app/login-state/LoginStateGate.tsx` | 提供元の `clearLogoutIntent` を別の文脈で渡し、`useClearLogoutIntent()`（無ければ何もしない関数）を出す（`useLogout` と同じ形） |
| `src/app/routing/AppRouter.tsx` | 道が `/logout` 以外に変わったとき `useClearLogoutIntent()` の関数を呼ぶ効果 |

### 5.3 骨組みの新しい部品（C2）

| 道 | 中身（部品の ID） |
|---|---|
| `src/app/work-role/workRoleApi.ts` | L1。`getWorkRole()`・`switchWorkRole(roleId)`・`parseWorkRole` |
| `src/app/work-role/WorkRoleProvider.tsx` | L3。`WorkRoleProvider`・`useWorkRole()`（`state`・`revision`（切り替えの回数）・`switchTo`・`reload`・`switching`）。提供元が無いときの既定の値（`idle`、要求を送らない）を持つ |
| `src/app/work-role/WorkRoleSwitcher.tsx`・`.css` | L3。トップバーの表示（D5・D9）と Dropdown（D6、`placement="bottom-end"`、`work-role-switcher` の入れ物）、待ちの間の押せないボタン（NFR2.11）、Toast（D7・D8）、`focusWorkRoleSwitcher()`（D23） |
| `src/app/navigation/navigationApi.ts` | L2。`getNavigation()`・`parseNavigation` |
| `src/app/navigation/BusinessNavigationProvider.tsx` | L4。`BusinessNavigationProvider`・`useBusinessNavigation()`（`state`: loading・loaded・error、前に読めた木、`reload`）。未ログインで木と開閉の保存を捨てる（`clearExpanded()`）。既定の値あり |
| `src/app/navigation/useReloadTriggers.ts` | L4。D4 の (a)〜(d) のきっかけをまとめ、同じ描画の中の重なりを1回にする。`/logout` の道では読み直さない |
| `src/app/navigation/buildNavSections.ts` | L5。区画を作る純粋な関数（D10〜D15・D20）。ホームの区画・業務の区画・管理の区画、`biz:`・`adm:` の前置き、表示名の既定（D13）、アイコンの照らし合わせ（D14）、今の項目と祖先の id（D15・D20） |
| `src/app/navigation/tablePlaceholderPath.ts` | L6。`toTablePlaceholderPath`・`readTablePlaceholderParams`（`URLSearchParams`、先頭は常に `/tables?`） |
| `src/app/navigation/navExpansion.ts` | L7。`sessionStorage` の1つの鍵、`loadExpanded`・`saveExpanded`（空なら `removeItem`）・`clearExpanded`（`removeItem`）・`pruneExpanded`・`addAncestors`。読み書きの例外は無視 |

### 5.4 骨組みの差し替え（C3）

| 道 | 変更 |
|---|---|
| `src/app/App.tsx` | 並びを `… → AdminForbiddenProvider → WorkRoleProvider → BusinessNavigationProvider → AppRouter` にする（D2） |
| `src/app/layout/ShellLayout.tsx` | `navItems` をやめ `navSections`（`buildNavSections`）・`navExpandedIds`・`onNavExpandedChange`・`navLabels`・`topbarEnd={<WorkRoleSwitcher/>}` を渡す。開閉の状態を持ち、木を読み終えたときだけ `pruneExpanded`、`currentAncestorIds` の中身が変わったときに祖先を足す（D16）。ユーザーメニューの `path` の項目は既定の移動を止め、`beforeNavigate` があれば呼んでから移る |
| `src/app/pages/HomePage.tsx` | D19 の案内（`role="status"`、Alert、［もう一度］、S8 の道が登録されているときの［自分の権限を見る］） |
| `src/app/i18n/messages/ja.ts`・`en.ts` | `nav.*`（区画の見出し・領域の名前・開閉・読み込めなかった・名前なし）・`workRole.*`・`home.*` の鍵 |
| `src/app/navigation/navigationItems.ts` | ユーザーメニュー（`buildUserMenuItems`）は残す。サイドバーの平らな一覧（`buildSidebarEntries`・`HOME_ENTRY`）は消し、管理の項目を選ぶ純粋な関数 `selectAdminSidebarItems(registrations, loginState)` に置き換える。`buildNavSections` がこれを使う（14節 Q5: A、12節 D-16） |

### 5.5 ログアウトの道（C4、L11）

| 道 | 変更 |
|---|---|
| `src/features/auth/logoutIntent.ts`（新） | `markLogoutIntent`・`peekLogoutIntent`・`consumeLogoutIntent`（メモリ上の真偽1つ） |
| `src/features/auth/LogoutPage.tsx`（新） | `security-design.md` 5.2 の形（描画では `peek`、効果の中で ref で1回、印があれば `consume`・`clearExpanded`・`logout`、無ければホームへ `replace`）。「ログアウトしています」（`role="status"`） |
| `src/features/auth/registration.ts` | `auth-logout` を `{ path: '/logout', beforeNavigate: markLogoutIntent, order: 100 }` に、route `/logout`（LOGGED_IN・STANDALONE・`lazy`）を足す。文言 `auth.logout.inProgress`（日英） |
| `src/features/auth/loginStateProvider.ts` | `clearLogoutIntent: consumeLogoutIntent` を渡す |

`clearExpanded` は `app/navigation/navExpansion` の関数で、機能から `app/` を読むのは既存の形（ESLint の制限は機能どうしだけ）。

### 5.6 tables（C5、`src/features/tables/`、新、L9）

`registration.ts`（`/tables`、LOGGED_IN・SHELL、`lazy`、サイドバーの項目なし）・`messages.ts`・`tableAccessApi.ts`（`getTableAccess(schema, table)`・`parseTableAccess`）・`useTableAccess.ts`（世代で古い結果を捨てる、403 で `useBusinessNavigation().reload()`）・`TablePlaceholderPage.tsx`・`.css`・`latestLoad.ts`（必要なら。B8 の複製の形、機能どうしは import しない）・テスト。

### 5.7 〔Should〕mypermissions（C6、`src/features/mypermissions/`、新、L10）

`registration.ts`（`/me/permissions`、LOGGED_IN・SHELL、`lazy`、ユーザーメニュー `mypermissions-open` order 70）・`messages.ts`・`myPermissionsApi.ts`（3つの道・`parseMyPermissions`、`displayName` は文字列）・`useMyPermissions.ts`（作業ロールの `roleId`・`revision` と表示の言語が変わったら木を最上位から読み直す）・`MyPermissionsPage.tsx`・`MyPermissionsDetail.tsx`・`.css`・テスト。左の木は `shared/tree` の `SharedTreeView`（変えない）。

### 5.8 E2E（C7、`frontend/e2e/`）

| 道 | 中身 |
|---|---|
| `support/userMenu.ts`（新、L12） | `openUserMenu(page)`・`openUserMenuItem(page, name)`。`app-shell` の中の `header` の中で、`work-role-switcher` の子孫でない `dropdown-trigger` を1件に絞る（12節 D-9） |
| `support/registeredUser.ts` | `openUserMenuItem` を `userMenu.ts` へ移す（既存の呼び出し元の import は `registeredUser.ts` からの再エクスポートで保つか、import を直す。Step 25 で決めて記録） |
| `080-preferences-accessibility.e2e.ts` | 232 行の `getByTestId('app-shell').getByTestId('dropdown-trigger').click()` を `openUserMenu(page)` に替える |
| `support/frameApiRoute.ts`（新、L13） | `/api/me/work-role`・`/api/me/navigation`・`/api/me/table-access`・`/api/me/permissions/*` の差し替えの口。見本は前もって `JSON.stringify` した本文。`PUT` の後は切り替えの後の見本を返す状態と `reset()` |
| `support/navTreeFixtures.ts`（新、L13） | 見本の木の生成（目安 100 項目・深さ 1、悪い側 1,000 項目・深さ 5・40 文字の名前・末端までの1本の枝）。型は画面の側の `NavigationResponse` などで縛る |
| `support/u7Measure.ts`（新） | `performance-design.md` 4.2 の聞き手と見張りを入れる関数と、判定の表 `__u7Done`（`workRoleSwitched`・`groupOpened`・`placeholderShown`） |
| `160-app-frame-accessibility.e2e.ts`（新） | 10.1 |
| `170-menu-navigation-flow.e2e.ts`（新） | 10.2 |
| `150-role-permission-flow.e2e.ts` | 10.3 の手順を手順 6 の後に足す |

### 5.9 文書（C8）

`README.md`（E2E の表に 160・170 の行、150 の行に作業ロールの切り替えを足す、「160・170 について」の注意（Mailpit・単独で流すとき）、画面の節に作業ロール・業務のメニュー・置き場・自分の権限・ログアウトの道）、`frontend/src/features/README.md`（ユーザーメニューの `beforeNavigate`、骨組みのフック `useWorkRole`・`useBusinessNavigation`、置き場の道は `toTablePlaceholderPath` だけで作る）。README の E2E の表に 135 の行が無い件（B8 の確かめたいこと 4）は、同じ表を直すこの Bolt で足す（14節 Q8: A。`135-dsl-admin-accessibility.e2e.ts` の行を 130 と 140 の間に置く）。

### 5.10 変えないもの（Step 31 で差が無いことを確かめる）

`backend/`、`vendor/`（P1 の gitlink を除く）、`frontend/package.json`・`package-lock.json`・`.npmrc`、`vite.config.ts`・`vitest.config.ts`・`vitest.setup.ts`・`playwright.config.ts`・`eslint.config.js`・`.oxlintrc.json`・`tsconfig*.json`、`src/main.tsx`、`src/shared/`（`tree` を含む）、`src/app/testing/renderWithProviders.tsx`、`src/app/registry/allowedIcons.ts`、`backend/src/main/resources/navigation/allowed-icons.txt`、`docker/`・`.github/`、`frontend/scripts/check-bundle-size.mjs`、ほかの機能の本体（`features/` の auth 以外）。

## 6. 既存のテスト・E2E・lint・幅への影響の洗い出し

前の Bolt では、計画の影響の範囲の外の既存のテスト・E2E・lint・幅で止まった例が多かった（dsl-v2 の D-20・D-21、role の B4-1・B5-1、safety-carryover の G1・G2、B8 の Step 5 の 060）。計画の段で、変える骨組み・登録・ルート・サイドバー・ユーザーメニュー・ログアウトを使っている所を `git grep`（`sidebar-nav`・`dropdown-trigger`・`openUserMenuItem`・`openSidebarItem`・`buildSidebarEntries`・`HOME_ENTRY`・`auth-logout`・`'ログアウト'`・`<App`・`installFakeServer`・`stubGlobal('fetch'`・`/api/me`）で洗い出した。

### 6.1 画面のテスト（Vitest）

| # | 変えるもの | 当たる所（検索で見つけたもの） | 扱い |
|---|---|---|---|
| (a) | `ShellLayout` を `navSections` にする（`data-testid` が `sidebar-nav-<href>` から `sidebar-nav-<id>` に変わる） | `app/layout/ShellLayout.test.tsx`（`sidebar-nav` 11 か所）・`app/routing/AppRouter.test.tsx`（3 か所）・`app/routing/DataRouterBlocker.test.tsx`（`sidebar-nav-/admin/other` 2 か所） | `sidebar-nav-adm:<登録の id>` か、リンクの名前（`getByRole('link', { name })`）に書き換える。確かめていた中身（出し分け・移動・未保存の確かめ）は減らさない（NFR6.7） |
| (b) | サイドバーの平らな一覧 `buildSidebarEntries`・`HOME_ENTRY` | `app/navigation/navigationItems.test.ts`、9つの機能の登録のテスト（`features/{admin,dsl,invitation,useradmin,roleadmin,groupadmin,roletransfer,registration,preferences}/registration.test.ts*`。`buildSidebarEntries(...)` の結果と `toEqual([HOME_ENTRY, …])`・`toHaveLength(1)`） | 14節 Q5: A。管理の項目を選ぶ純粋な関数 `selectAdminSidebarItems(registrations, loginState)` に置き換え、9つのテストを同じ中身（管理者だけに出る・並び・未ログインで空）で書き換える。ホームは区画の関数（`buildNavSections`）のテストで確かめる。期待は緩めない |
| (c) | ログアウトの項目を `action` から `path: '/logout'` と `beforeNavigate` にする | `features/auth/registration.test.ts`（91〜103 行。`userMenuItems[0].action()` を呼んで `requestLogout` を確かめる）、`features/preferences/registration.test.tsx`（101 行の `{ id: 'auth-logout', path: undefined, order: 100 }`） | auth のテストは「`path` が `/logout`・`beforeNavigate` が印を立てる・`/logout` の route が `lazy`」に書き換え、ログアウトの要求の確かめは `LogoutPage.test.tsx` に移す。preferences のテストは `path: '/logout'` に直す（並びの確かめは変えない） |
| (d) | `App.tsx` に `WorkRoleProvider`・`BusinessNavigationProvider` を足す（ログイン中は `GET /api/me/work-role`・`/api/me/navigation` を送る） | `App` をそのまま描くテスト: `app/App.test.tsx`（未ログインだけ。要求は出ない見込み）、`app/routing/DataRouterBlocker.test.tsx`（ログイン中の管理者で `App` を描き、`fetch` を差し替えていない）。`renderWithProviders` で描くテスト（68 ファイル、`renderPreferencesApp` を含む）は新しい提供元を含まないため、フックの既定の値（idle）で要求を出さない | `DataRouterBlocker.test.tsx` は `fetch` を差し替え、2つの API に見本を返す（未保存の確かめの中身は変えない）。`App.test.tsx` は Step 17 で流して確かめる。`renderWithProviders` は変えない（5.10） |
| (e) | 要求の一覧を数えるテスト | `features/preferences/PasswordChangePage.test.tsx` 135 行（`server.requests` の道が `[REFRESH_PATH]`）、`features/registration/RegistrationPage.test.tsx`（`requestsTo(VERIFY)`）、`AppRouter.test.tsx` 195 行（`fetch` が呼ばれない） | どれも `renderWithProviders` で描くため、新しい2つの要求は出ない見込み。Step 17 の画面のテストの全体で確かめ、落ちたら止まらずに「その画面の API の道だけを数える」形に直す（期待を緩めない）。直したものは `code-summary.md` に書く |
| (f) | ホームの画面に案内を足す | `app/pages/HomePage.test.tsx`、`home-page` を待つテストと E2E | 案内は `useBusinessNavigation()` の既定の値（idle）では出さないため、既存の確かめは変わらない見込み。`HomePage.test.tsx` に D19 の5つの案内を足す |
| (g) | 文言の鍵を足す | `app/i18n/messages.test.ts`（日英の鍵の一致） | 日英を同時に足す |
| (h) | 新しい機能のフォルダーを2つ足す | `eslint.config.js` は `src/features/` のディレクトリから機能どうしの import の制限を作る。`eslintImportRules.test.ts` | 設定は変えない。新しい機能にも制限が当たることを Step 24 の `npm run lint` で確かめる |
| (i) | カバレッジ | 今は行 94.88%・分岐 89.59%（B8 の Step 29） | 下限（行 80%・分岐 70%）は今のまま、除外を足さない。`buildSidebarEntries` は消す（Q5: A）ため、本体の使わない関数は残らない |

### 6.2 E2E（Playwright、`verify` の外。Step 26・33 で流す）

| # | 変えるもの | 当たる所 | 扱い |
|---|---|---|---|
| (j) | トップバーに作業ロールの Dropdown が並び、`dropdown-trigger` が2つになる | `support/registeredUser.ts` の `openUserMenuItem`（`app-shell` の中の `dropdown-trigger`）を使う 080・100・110、`080-preferences-accessibility.e2e.ts` 232 行の直接の開き口 | `support/userMenu.ts` に替える（L12、12節 D-9）。110 の注記（利用者の管理の画面の上では行の「操作」と重なる）は、`header` に絞ることで当たらなくなるが、110 の使い方は変えない |
| (k) | ユーザーメニューのトリガーを名前で開くテスト | 020・030・040・090 は `getByRole('button', { name: <氏名かメールアドレス> })` で開き、`getByRole('menuitem', { name: 'ログアウト' })` を押す | 作業ロールのトリガーの名前は「作業ロール: …」で重ならない。ログアウトの項目は `<a>`（`path` の項目）になるが役割は `menuitem` のまま（Dropdown の項目）。押すと `/logout` を経てログインの画面へ移ることを Step 26 で確かめる。020 の「ログアウトの後の再読み込みでログインの画面のまま」も同じ |
| (l) | サイドバーのリンクの名前 | `support/adminLogin.ts` の `openSidebarItem`（`getByRole('link', { name, exact: true })`）を使う 060・090・100・110・120・130・135・140・150、030・040 の「管理」「DSL」のリンク、090・110 の「リンクが無い」 | 5bf1ffe でも管理の項目の `<a>` の `aria-label` は登録の文言のまま（M4）。業務のメニューに同じ名前の項目が出るのは、その利用者の作業ロールで見えるテーブルがある DSL を適用したときだけ（040・150 の DSL は `E2EF_` の名前）で、重ならない見込み。Step 26 で確かめる |
| (m) | 360px のはみ出しと axe（既存の 20 組の検査） | 050（ログインの画面、要求なし）を除く 060・080・100・120・130・135・140 の幅 375px・360px の組と axe | トップバーに作業ロールのトリガーが増え、ホームに案内が出る。初期管理者は作業ロールが無い（「作業ロール: なし ▼」、768px 未満は「なし ▼」）。既存の検査がはみ出し・コントラスト・`heading-order` で落ちうる。Step 26 と Step 33 の全体で確かめ、落ちたら止まって依頼者に尋ねる（期待と除外は変えない） |
| (n) | 画面の移動ごとの2つの要求（D4 の (d)） | 要求を数える E2E: 080 の `/api/me/` への書き込み（GET 以外）の数、130・135 の管理の API への書き込み、060 の招待の POST、070 の登録の API | どれも GET 以外か別の道を数えるため当たらない見込み。PUT `/api/me/work-role` は切り替えたときだけ |
| (o) | 固定先の更新だけでの見た目（`semantic.css` の新しい印の色・`AppShell.css`） | 既存の E2E のすべて | Step 5 で 010〜150 の全体を流す（`ShellLayout` は旧形の `navItems` のまま） |
| (p) | 150 が DSL を `E2EF_` に替え、170 が `E2EI_` に替える | 160 は API を差し替えるため影響しない。170 は自分で適用し直す | 170 を最後の番号にし、後に流れるファイルは無い。受信箱の件数に頼る E2E が無いことを Step 1 で確かめる |
| (q) | 初期管理者の作業ロール | 150 は初期管理者のロールを変えない。E2E は毎回一時の内部DB に初期管理者を作る | 初期管理者は作業ロールが無い前提で既存の E2E を読む。170・150 の追加でも初期管理者のロールは変えない（`team.md`） |

## 7. 手順

方法は test-after。層（純粋な関数と API の写し → 状態 → 画面の部品 → 機能の画面 → E2E）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ進む。コマンドの正確な形は `unit-test-instructions.md`。テストの説明文は英語、見本のメールアドレスは `example.com` だけ。描画の後に反映される値は `waitFor`・`findBy*` で待ち、テストの時間の上限は原因を確かめずに延ばさない。git の書き込み（ブランチ・サブモジュールの取得・コミット・統合）は、どれも実行の前に依頼者の承認を得る。

### 7.1 前の確かめと固定先の更新（Step 1〜6）

- [ ] **Step 1: 作業の場の用意と前の確かめ**（`develop` の上、読み取りと上流の取得）。`git status` で `aidlc/` の外に変更が無いこと、`frontend/playwright-report`・`frontend/test-results` が無いこと（あれば段の始めに消す、`project.md` の学び）、`vendor/make-you-chic-ui` の固定先が e82b651 であることを確かめる。依頼者の承認を得て `git -C vendor/make-you-chic-ui fetch origin` を行い（作業フォルダの中身は変えない）、origin の先頭が 5bf1ffe か、追加の依頼の3点を含む後の版があるかを確かめる。3点を含む版があればそれを、無ければ 5bf1ffe を固定先にする（14節 Q2: A。5bf1ffe のときは残る3点を差として記録する）。固定先にする版のソースを `git -C vendor/make-you-chic-ui show <版>:…` で読み、3.2 の M1〜M7 が記録と一致するかを `generation-notes.md` に書く（差があれば止まって尋ねる）。あわせて読み取りで、(1) `frontend/e2e/` の番号（010〜150）で 160・170 が空いていること、(2) 受信箱の件数に頼る E2E が無いこと（6.2 (p)）、(3) `e2e/support/secretValues.ts` の形の表が `createRegisteredUser` の作る値と一致すること（3.3 P8）、(4) 3.1 の A1〜A5 の record が計画の段から変わっていないことを確かめて書く。
- [ ] **Step 2: テストの実行の準備**（最初のテストより前）。名指しのコマンドが動くことを、既存のテスト（`src/app/layout`・`src/app/routing`・`src/app/navigation`・`src/app/login-state`・`src/app/registry`・`src/features/auth`）と `npm run typecheck` で確かめ、失敗 0 を記録する。
- [ ] **Step 3: 変更の前の基準**（基盤の設計の承認の場の決定 (4)、入口の量の点 1）。`develop`（ea55919 か、その後の記録のコミット）の上で `caffeinate -di ./gradlew frontendBundleSize` を流し、入口の合計（gzip）と、`frontend/dist/.vite/manifest.json` の `isEntry` の項目から `imports` をたどった集まりを記録する。`caffeinate -di npm run test:coverage`（`frontend/` で）で画面のテストのファイル数・件数・カバレッジ（行・分岐）を記録する。`frontend/package.json`・`frontend/package-lock.json`・`backend/gradle.lockfile`・`vendor/make-you-chic-ui/package-lock.json` の SHA-256 を記録する。終わったら `pmset -g log` を確かめる。
- [ ] **Step 4: 固定先の更新 — ブランチと専用の変更**（`cicd-pipeline.md` 2節の 1〜3）。`develop` の先頭から `feature/261004-role-menu-b9-mycui` を作る。`git -C vendor/make-you-chic-ui checkout <Step 1 で決めた版>` で固定先だけを動かし、`git diff --submodule=log -- vendor/make-you-chic-ui` と `git -C vendor/make-you-chic-ui diff --stat e82b651 <版>` を記録する。`package.json`・`package-lock.json`・`LICENSE`・`Icon` に差が無いこと（M7）を確かめる。`vendor/` の中のファイルは書き換えない。
- [ ] **Step 5: 固定先の更新 — 確かめ**（`cicd-pipeline.md` 2節の 4〜5、基盤の設計の読み直し R-05）。(1) `caffeinate -di ./gradlew vendorInstall vendorBuild vendorUnchanged osvScan --rerun-tasks`（`vendorInstall` は `npm ci`、`--ignore-scripts` を付けない。`project.md` の学び）。(2) `npm run typecheck` と画面のテストの全体（旧形の `navItems` の経路のまま、`ShellLayout.test.tsx`・`AppRouter.test.tsx`・`allowedNavIcons.test.ts` を含めて Step 3 と同じ件数で通る）。(3) `frontend/package-lock.json` の SHA-256 が Step 3 と同じ。(4) 3.3 の P4（`shared/tree` との読み比べ）を記録。(5) colima の共有を確かめて `caffeinate -di ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`。(6) 入口の量の点 2（`frontendBundleSize` と manifest の集まり）を記録。(7) Mailpit を起動し、`./gradlew :backend:bootWar` の後に `caffeinate -di ./gradlew e2eTest`（010〜150）。どれかが通らなければ、更新を止めて依頼者に尋ねる（`cicd-pipeline.md` 8節 (i)）。各コマンドの後に `pmset -g log` を確かめる。
- [ ] **Step 6: 固定先の更新 — 専用のコミットと統合**。依頼者の承認を得て、`vendor/make-you-chic-ui` の gitlink だけを含むコミット P1 を作る（メッセージは日本語。「make-you-chic-ui の固定先を上げる: 入れ子のサイドバー（navSections）」と、更新の前後の40桁のハッシュ、差のファイルの数）。依頼者の承認を得て `develop` へ fast-forward で統合し、ブランチを消す。前後のハッシュと確かめの結果を `generation-notes.md` に書く（後で `code-summary.md` にも書く）。

### 7.2 B9 の本体（Step 7〜34）

- [ ] **Step 7: B9 の作業ブランチ**。Step 6 の後の `develop` の先頭から `feature/261004-role-menu-b9` を作る（承認の後）。`vendor/make-you-chic-ui` が Step 6 の版であることを確かめる。
- [ ] **Step 8: 骨組みの口 — 実装**（L15、5.2）。`types.ts`・`validateRegistrations.ts`・`LoginStateGate.tsx`・`AppRouter.tsx`。
- [ ] **Step 9: 骨組みの口 — テスト**。`validateRegistrations.test.ts`（`path` の項目の関数は通る・`action` の項目に付けると止まる・関数でない値は止まる、既存の検査のテストが通る）、`LoginStateGate.test.tsx`（提供元の `clearLogoutIntent` が呼ばれる・無いときは何もしない）、`AppRouter.test.tsx`（`/logout` 以外の道の描画で消す口が呼ばれ、`/logout` では呼ばれない）。C1 のコミットの提案。
- [ ] **Step 10: 純粋な関数と API の写し — 実装**（L1・L2・L5〜L7、5.3）。`tablePlaceholderPath.ts`・`navExpansion.ts`・`buildNavSections.ts`（D10〜D15・D20、今の項目と祖先の id、管理の項目は `selectAdminSidebarItems` から。14節 Q5: A）・`workRoleApi.ts`・`navigationApi.ts`。
- [ ] **Step 11: 純粋な関数と API の写し — テスト**。`tablePlaceholderPath.test.ts`（`/`・`..`・`?`・`#`・`%`・空白・`&`・`=`・`javascript:`・`https://` を含む名前で、先頭が `/tables?`、読み戻すと同じ。fast-check の往復）、`navExpansion.test.ts`（壊れた値・保存の例外・空なら鍵を消す・`clearExpanded` で鍵が無い・捨てる・祖先を足す・木の読み込み前と失敗で捨てない・木が道より後に届いて祖先を足す・同じ道で閉じた枝を開き直さない）、`buildNavSections.test.ts`（D10〜D15・D20 の例）、`buildNavSections.property.test.ts`（機能設計 6.2 の性質 1〜7、失敗時の種を記録）、`workRoleApi.test.ts`・`navigationApi.test.ts`（送る道・本文が `{roleId}` だけ・余分な項目を写さない・欠けた・型の違う・知らない列挙の本文は通信の失敗）。
- [ ] **Step 12: 状態 — 実装**（L3・L4）。`WorkRoleProvider.tsx`・`useReloadTriggers.ts`・`BusinessNavigationProvider.tsx`。提供元が無いときの既定の値（idle、要求を送らない）、世代で古い答えを捨てる、読み直しの間は今の表示を残す、未ログインで捨てる（`clearExpanded()`）、送信の最中の `switchTo` を捨てる（NFR2.11）、`ROLE_NOT_ASSIGNED`・`ROLE_BUSY`・`VALIDATION_FAILED`・通信の失敗で読み直す（D8・12節 D-4）。
- [ ] **Step 13: 状態 — テスト**。`WorkRoleProvider.test.tsx`・`BusinessNavigationProvider.test.tsx`・`useReloadTriggers.test.tsx`（D4 の (a)〜(d) のきっかけごとの読み直し、同じ描画の中の重なりが1回、`/logout` で読み直さない、遅らせた古い応答を捨てる、読み直しの間に前の木が残る、未ログインで捨てる、切り替えの4つの結果（成功・同じロール・拒否・失敗）と `ROLE_BUSY`、送信の最中の2回目を捨てる、要求の数が NFR2.2 の見積もり（移動1回で2本、切り替え1回で PUT 1本と読み直し2本））。
- [ ] **Step 14: 作業ロールの部品 — 実装**（5.3 の `WorkRoleSwitcher`）。D5〜D9（5つの表示、入れ物 `work-role-switcher`、「（使用中）」、ロールが1つ・無いの押せない項目と `description`、S8 の道が登録されているときの最後の項目、待ちの間の押せないボタン、Toast、フォーカスをトリガーへ戻す、768px 未満の見える文字の縮み）。
- [ ] **Step 15: 作業ロールの部品 — テスト**。`WorkRoleSwitcher.test.tsx`（5つの表示、読み上げの名前「作業ロール: 営業」、今のロールを選ぶと要求を送らない、成功の Toast とフォーカス、拒否・失敗の文と読み直し、押せない項目が矢印のキーでたどれて `aria-describedby` で読める、入れ物の外にユーザーメニューのトリガーがあり区別できる、キーボードだけで切り替えられる、待ちの間は押せない、英語の表示、vitest-axe）。C2 のコミットの提案。
- [ ] **Step 16: 骨組みの差し替え — 実装**（5.4）。`App.tsx`・`ShellLayout.tsx`・`HomePage.tsx`・`messages/ja.ts`・`en.ts`・`navigationItems.ts`（`buildSidebarEntries`・`HOME_ENTRY` を消し `selectAdminSidebarItems` を置く。14節 Q5: A）。
- [ ] **Step 17: 骨組みの差し替え — テストと既存のテストの書き換え**。`ShellLayout.test.tsx` を書き換えて足す（区画の並びと見出し、空の見出しを出さない、管理者でなければ管理の区画が無い、開閉のボタンの `aria-expanded` と名前（日英）、`aria-current` は多くて1つ、**5 段の木をキーボードだけ（Tab・Enter・Space）で末端まで開いて移る**、**深さちょうど 5 段が欠けずに描かれる**（NFR 設計 R-04 の手当て）、`label` に `<script>`・`<img onerror>`・`<`・`>`・`&`・`"`・`'` を含めても文字で出る、知らないアイコンが `list`、ナビゲーションの名前が英語で「Main menu」、開閉の状態が画面の移動で保たれ、直接開いた道で祖先が開く、木が後から届いても祖先が開く、作業ロールの切り替えで業務の区画が替わる、ユーザーメニューの `path` の項目で `beforeNavigate` が移る前に1回呼ばれる、vitest-axe）。`HomePage.test.tsx` に D19 の5つの案内と［もう一度］。6.1 の (a)〜(e)・(g) の書き換え（`AppRouter.test.tsx`・`DataRouterBlocker.test.tsx`・`navigationItems.test.ts`・9つの登録のテスト・`App.test.tsx`）。画面のテストの全体を流す。C3 のコミットの提案。
- [ ] **Step 18: ログアウトの道 — 実装**（5.5）。
- [ ] **Step 19: ログアウトの道 — テスト**。`logoutIntent.test.ts`（3つの口、［留まる］の後・別の道の描画の後に印が残らない）、`LogoutPage.test.tsx`（StrictMode で包む。印あり: `logout()` が1回・印が消える・開閉の保存の鍵が無い・ホームへの移動が起きない。印なし: `logout()` を呼ばずホームへ `replace` が1回。描き直しても2回目は何もしない。vitest-axe）、`features/auth/registration.test.ts` の書き換え（6.1 (c)）、`features/preferences/registration.test.tsx` の直し。U6 の S4 の未保存の確かめがログアウトの道の移動で出ることを、`DataRouterBlocker.test.tsx` に1件足して確かめる（D27）。C4 のコミットの提案。
- [ ] **Step 20: tables（S2）— 実装**（5.6、D20〜D23）。
- [ ] **Step 21: tables（S2）— テスト**。`tableAccessApi.test.ts`（`URLSearchParams` の道・`item` を送らない・崩れた本文は失敗）、`TablePlaceholderPage.test.tsx`（準備中の表示名と言語の切り替え・空の表示名の既定、403・400・引数なしが同じ表示で表示名を出さない、403 で業務のメニューを読み直す、作業ロールの切り替えで問い直す、［作業ロールを切り替える］がロールが2つ以上のときだけでトリガーへフォーカスを移す、［ホームへ］、`aria-live` で伝えフォーカスを動かさない、vitest-axe）、`registration.test.ts`（`lazy`、LOGGED_IN・SHELL、未ログインはログインへ）。C5 のコミットの提案。
- [ ] **Step 22: 〔Should〕mypermissions（S8）— 実装**（5.7、D24〜D26。14節 Q3: A で B9 に含める）。表示の言語を替えたら最上位から読み直す（14節 Q4: A）。
- [ ] **Step 23: 〔Should〕mypermissions（S8）— テスト**。`myPermissionsApi.test.ts`（3つの道と引数の形、`displayName` は文字列、崩れた本文で失敗）、`MyPermissionsPage.test.tsx`（木の開閉と選び、値の文字（READ・FULL・NONE と意味、「可」「不可」）、`inMenu` の文字、作業ロールが無い・対象が無い、作業ロールの切り替えと言語の切り替えで読み直す、768px 未満の縦の積み（クラスの付き方だけ、見た目は 160）、vitest-axe）、`registration.test.ts`（`lazy`、ユーザーメニューの order 70 がプリファレンスの前）。C6 のコミットの提案。
- [ ] **Step 24: 画面の全体の検査**。`frontend/` で `npm run format:check`・`npm run lint`・`npm run lint:css`・`npm run license:check`・`npm run typecheck`・`caffeinate -di npm run test:coverage`。カバレッジが行 80%・分岐 70% 以上（除外を足さない）、件数とカバレッジを Step 3 と並べて記録する。新しいフォルダーごとの値（`app/work-role`・`app/navigation`・`features/tables`・`features/mypermissions`・`features/auth`）も記録する。
- [ ] **Step 25: E2E の部品 — 実装**（5.8）。`userMenu.ts`、`registeredUser.ts`・`080` の書き換え、`frameApiRoute.ts`（`reset()` を含む、NFR 設計 R-09 の手当て）、`navTreeFixtures.ts`、`u7Measure.ts`。
- [ ] **Step 26: 既存の E2E の書き換えの確かめ**。`./gradlew :backend:bootWar` の後に（この間はビルドを流さない）、`caffeinate -di npx playwright test` で 020・030・040・080・090・100・110・120・130 を流す（ユーザーメニュー・ログアウト・サイドバーのリンク・幅を使うもの。6.2 (j)〜(n)）。落ちたら止まって依頼者に尋ねる（期待と除外は変えない）。
- [ ] **Step 27: 160 — 実装と実行**（10.1）。`caffeinate -di npx playwright test e2e/160-app-frame-accessibility.e2e.ts`。1組あたりの時間を記録する。畳んだ状態の `aria-labelledby` が違反になったとき、360px ではみ出したときは、除外を足さずに止まって依頼者に尋ねる。測りの値は記録だけ（Not Met でも統合は止めない）。
- [ ] **Step 28: 170 — 実装と実行**（10.2）。`caffeinate -di npx playwright test e2e/170-menu-navigation-flow.e2e.ts`。報告の部品の結果と json の報告の文字列の検索で、パスワード・トークン・メールアドレス・氏名が無いことを確かめる。
- [ ] **Step 29: 150 への追加 — 実装と実行**（10.3）。`caffeinate -di npx playwright test e2e/150-role-permission-flow.e2e.ts`。報告の確かめは Step 28 と同じ。C7 のコミットの提案。
- [ ] **Step 30: 文書**（5.9。README の E2E の表に 160・170 と、書き漏れの 135 の行を足す。14節 Q8: A）。C8 のコミットの提案。
- [ ] **Step 31: 取り残しと変えないものの確かめ、入口の量の判定**。`git diff --stat develop` で 5.10 のファイルに差が無いこと、lockfile の SHA-256 が Step 3 と同じこと（NFR6.5）、`TODO` が無いこと、新しいファイルすべてにライセンスヘッダーがあること、`frontend/src` に `console.` で値を出す行を足していないこと（NFR1.6）。`caffeinate -di ./gradlew frontendBundleSize` で入口の量の点 3 を取り、11節の判定をする。
- [ ] **Step 32: 記録**（`aidlc/` の下）。`code-summary.md`（作ったもの・固定先の更新の前後のハッシュと確かめの結果・決めたこと・計画との差・入口の量の3点・画面の時間・承認の場で確かめたいこと・後に回すこと・受け入れた制約）、`source-manifest.json`（アプリのソースの道すべて。P1 の gitlink を含める）、`traceability.json`（8節の AC と D の番号、NFR の枝番。2.2 の基盤の設計 R-04 の手当てを含む）、`generation-notes.md`（Step ごとのコマンドと結果）。
- [ ] **Step 33: 統合の前の関門**。colima の共有を確かめ、README の環境変数を付けて `caffeinate -di ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`。続けて Mailpit を起動して `caffeinate -di ./gradlew e2eTest`（010〜170）。件数・カバレッジ・時間を記録し、報告の確かめの道具で残してはならない値が 0 件であることを確かめ、報告のフォルダーを消す。`pmset -g log` で休止が無かったことを確かめる。あわせて `cicd-pipeline.md` 5節の手元の確かめ（ログアウトとログイン、深い道の再読み込み、戻る・進む、`/logout` の直接の表示、未保存の確かめ）を、14節 Q7: A のとおり、使い捨ての内部DB で起動した WAR に対して作業の場の一時のファイル（scratchpad）の Playwright の台本で AI が行い、結果を `generation-notes.md` と `code-summary.md` に記録する（台本はリポジトリに足さない。依頼者の目視は任意）。落ちたら `team.md` の不安定なテストの決まりで扱う。
- [ ] **Step 34: コミットと承認の場と統合**。最後のコードのコミット（C8）の後にコード生成の段の承認の場を開き、その後に記録のコミット、`aidlc/` の未コミットの変更の確かめ、`develop` への squash の提案（どれも実行の前に依頼者の承認を得る）。統合の後に作業ブランチを消す。

## 8. ストーリー・要件と手順の対応

AC は機能設計の `traceability.json` で U7 が OK（部分を含む）にした 40 件、Deferred の 13 件は U4 role・U5 navigation が受け持つ（このまま Deferred）。

| ストーリー・AC | 機能設計の D・流れ | NFR | 手順（実装 → テスト） |
|---|---|---|---|
| US4.1 作業ロール: AC4.1.1・AC4.1.3・AC4.1.4・AC4.1.7〜AC4.1.13・AC4.1.15・AC4.1.17 | D4〜D9・W1.1・W1.2・W2.1〜W2.3 | NFR1.4・NFR2.2・NFR2.5・NFR2.9・NFR2.11・NFR4.1・NFR4.3 | Step 10〜15（API・状態・`WorkRoleSwitcher`）、Step 16〜17（トップバーへの配置）、Step 27（160 の開いた一覧・幅・NFR2.5 の測り）、Step 29（150 の切り替え） |
| US4.1 S2 の問い直し: AC4.1.10 | D21・W7.3 | NFR2.9 | Step 20〜21 |
| US4.2 自分の権限〔Should〕: AC4.2.1・AC4.2.2・AC4.2.4・AC4.2.6 | D24・D25・W8.1〜W8.3 | NFR1.4・NFR6.8 | Step 22〜23、Step 27（160 の S8） |
| US4.2 ホームの入口: AC4.2.7 | D19・D26・W4.1 | NFR4.1 | Step 16〜17 |
| US5.1 サイドバー: AC5.1.1・AC5.1.2・AC5.1.5〜AC5.1.10・AC5.1.12・AC5.1.13・AC5.1.15・AC5.1.17 | D10〜D18・D20・W3.1〜W3.3 | NFR1.7・NFR2.6・NFR2.9・NFR4.2・NFR4.3・NFR6.1・NFR6.2 | Step 10〜13（`buildNavSections`・開閉・読み直し・fast-check）、Step 16〜17（`ShellLayout`）、Step 27・28 |
| US5.1 置き場: AC5.1.3・AC5.1.4・AC5.1.11 | D20〜D23・W7.1〜W7.3 | NFR1.1・NFR1.6・NFR1.7・NFR2.7 | Step 10〜11（道）、Step 20〜21（S2）、Step 28（170 の手順 5〜7） |
| US5.1 実際のブラウザ: AC5.1.16 | 6.3 | NFR4.4・NFR4.5 | Step 27（160） |
| US5.3 管理の区画: AC5.3.1〜AC5.3.7 | D10・D12・D15・D17 | NFR1.1・NFR4.2 | Step 10〜11（管理の項目・今の項目の前方一致）、Step 16〜17 |
| ログアウトの道（機能設計 7節 (e)、AC1.2.15 の継承） | D27・W9.1 | NFR1.6・NFR1.8 | Step 8〜9（口）、Step 18〜19、Step 28（170 の手順 9） |
| 固定先の更新 | D28・1.3 | NFR6.6 | Step 1・4〜6 |
| 既存のテストの書き換え | D28・7節 (f) | NFR6.7 | Step 17・19・25・26 |
| 画面の時間 | — | NFR2.5〜NFR2.8・NFR2.12 | Step 25（見本と測りの部品）、Step 27（測り） |
| 初回の JavaScript | — | NFR2.10 | Step 3・5・31（11節） |
| 依存とカバレッジ | — | NFR6.4・NFR6.5 | Step 24・31・33 |
| E2E の報告 | — | NFR1.9 | Step 28・29・33 |
| 静的検査 | — | NFR1.10 | Step 24・33 |

Step 32 の `traceability.json` は、上の AC（Deferred を含む 53 件）と、NFR 要件の枝番（NFR1.1・NFR1.4・NFR1.6〜NFR1.10・NFR2.2・NFR2.5〜NFR2.12・NFR4.1〜NFR4.5・NFR6.1〜NFR6.8）を行に立て、target にテストのファイルと手順を書く。要件の網羅は、要件定義の FR・NFR → 機能設計の D と NFR 要件の枝番 → この `traceability.json` の2段でたどる（`project.md` の学び）。

## 9. テストの量（Standard）

部品ごとに 5〜8 件と要所の結合（Standard）。どの画面部品にも vitest-axe を1件（誤りの状態と、40 文字の名前・5 段の木の見本を含める）。

| 部品・ファイル | 件数の目安 | 主な中身 |
|---|---|---|
| `tablePlaceholderPath.test.ts` | 6〜8（fast-check 1 を含む） | 先頭 `/tables?`、記号を含む名前の往復、外部の URL・`javascript:` を作らない、`item` の読み戻し |
| `navExpansion.test.ts` | 8〜10 | 2.2 の NFR 設計 R-05、機能設計 R-02 の3つの場面 |
| `buildNavSections.test.ts` | 8〜10 | 区画の並び・見出し・空の区画・管理者でない・表示名の既定・アイコン・今の項目（ホーム・管理の前方一致・業務の `item`） |
| `buildNavSections.property.test.ts` | 7（性質 1〜7） | 機能設計 6.2。生成器は NFR6.2 のとおり、失敗時の種を記録 |
| `workRoleApi.test.ts`・`navigationApi.test.ts` | 各 5〜6 | 送る形・写し・崩れた本文 |
| `WorkRoleProvider.test.tsx` | 7〜8 | 読み直し・切り替えの結果・`ROLE_BUSY`・送信の最中・未ログイン・既定の値 |
| `BusinessNavigationProvider.test.tsx`・`useReloadTriggers.test.tsx` | 6〜8・5〜6 | D4 のきっかけ、古い答え、前の木を残す、要求の数（NFR2.2）、`/logout` |
| `WorkRoleSwitcher.test.tsx` | 8〜10 | 5つの表示と操作、キーボード、英語、axe |
| `ShellLayout.test.tsx`（書き換えと足し） | 既存＋8〜10 | 6.1 (a)、NFR 設計 R-04 の4つ、axe |
| `HomePage.test.tsx`（足し） | 既存＋5〜6 | D19 の5つ、［もう一度］、［自分の権限を見る］の有無 |
| `validateRegistrations.test.ts`・`LoginStateGate.test.tsx`・`AppRouter.test.tsx`（足し） | 各＋2〜3 | L15 の口 |
| `logoutIntent.test.ts`・`LogoutPage.test.tsx` | 4〜5・5〜6 | NFR1.8、StrictMode |
| `tableAccessApi.test.ts`・`TablePlaceholderPage.test.tsx`・`features/tables/registration.test.ts` | 5・8〜10・3 | D20〜D23 |
| 〔Should〕`myPermissionsApi.test.ts`・`MyPermissionsPage.test.tsx`・`features/mypermissions/registration.test.ts` | 5・8〜10・3 | D24〜D26 |

画面の側の必須のテスト（`team.md` の N 階層のメニュー・役割と権限の画面の出し分け）の置き場: 開閉と今の項目・キーボード・深さちょうど → `ShellLayout.test.tsx`、表示の木を作る関数の fast-check → `buildNavSections.property.test.ts`、信頼できない入力 → `ShellLayout.test.tsx`・`buildNavSections.property.test.ts`（性質 2・3）・`tablePlaceholderPath.test.ts`、make-you-chic-ui に頼る振る舞い → `ShellLayout.test.tsx`・`WorkRoleSwitcher.test.tsx` と 160 の axe、権限の無い画面へ直接移ったときの権限なしの表示 → `TablePlaceholderPage.test.tsx` と 170 の手順 7。サーバーの必須のテストは U3〜U5 で済んでいる。

## 10. 実際のブラウザの検査（160）と流れの E2E（170・150 への追加）

本数の読み方（`team.md`、`cicd-pipeline.md` 4節）: この Intent は最大2本。F の流れは 150 の1本、I の流れは 170 の1本。150 に足す作業ロールの切り替えは同じ1本の中の手順。160 の検査と、既存の E2E の開き口の書き換え（`registeredUser.ts`・080）は数えない。E2E は1つの WAR と内部DB を番号の順に1本ずつ流す（`workers: 1`）。初期管理者の状態とロールは変えない。

### 10.1 160 U7 の画面の検査（`160-app-frame-accessibility.e2e.ts`、本数に数えない）

- **時間の見積もり**（NFR 設計 5節・読み直し R-09）: 140 は 20 組 × 12 の状態で 25 件・1.7 分だった（B8 の Step 24）。160 は 20 組 × 約 11 の状態（下の (1)〜(11)）と幅の4件・測りの1件で、140 と同じ形の口と画面の数から、全体で約 2〜4 分（1組あたり 5〜10 秒）と見積もる。10 分を超えない見込みのため、160・161 に分けない。Step 27 の実測で 10 分を超えたら、状態の群（サイドバーと作業ロール／S2・S8・ホーム）で 161 に分ける。
- **組ごとに1つのテスト**（20 組、`support/displayCombos.ts`）。1つのテストの中で状態を順にたどる。API は `frameApiRoute.ts` の差し替えで返し、内部DB に書かない。見本の型は画面の側の型（`NavigationResponse`・`WorkRoleState`・`TableAccess`・`MyPermissionNodes`）で縛る。
- **状態**: (1) 5 段まで開き、40 文字の名前の項目を含むサイドバー（今の項目が末端）、(2) 開いた作業ロールの Dropdown（ロール3つ・40 文字のロール名）、(3) ロールが1つの開いた一覧（押せない項目と説明）、(4) ロールが無いの開いた一覧、(5) S2 の準備中、(6) S2 の権限なし、(7) 〔Should〕S8 の木を開いてカラムの表を出した状態、(8) ホームの案内「設定されていません」、(9) ホームの案内「表示できる業務のメニューがありません」、(10) ホームの案内「読み込めませんでした」と［もう一度］、(11) 畳んだサイドバー（5bf1ffe の `aria-labelledby` の件）。
- **合格**: axe の違反 0 件（`REQUIRED_RULES` が流れた）、画面全体の横のはみ出しが無い（`support/overflow.ts`）、開いた Dropdown と 5 段のサイドバーの矩形が表示の中に収まる（`project.md` の学び。開いた部品の矩形を測る）。
- **幅**（NFR4.5、既定の1組）: 360px・768px・1280px と、悪い側の1組（文字の大きさ lg・テーマ dark・360px。NFR 設計の差 (a)）。作業ロールの見える文字が 768px 未満で縮み読み上げの名前は変わらない、S8 が 768px 未満で縦に積まれる、サイドバー（幅 220px 固定）と画面全体にはみ出しが無い。はみ出しが出たら止まって依頼者に尋ねる（機能設計 7節 (c)）。
- **英語**（NFR4.3）: 英語の組で、`navLabels` の名前（「Main menu」・「Expand …」・「Collapse …」）が英語になる。トップバーの畳むボタンとユーザーメニューのトリガーの名前は日本語のまま（受け入れた制約）。
- **測り**（NFR2.5〜NFR2.8、`performance-design.md` 4節）: 組の繰り返しの外に1件、既定の1組（light・md・blue・既定の幅）だけで動かす。
  - 見本の本文は前もって `JSON.stringify` した文字列。目安の木（100 項目・深さ 1）と悪い側の木（1,000 項目・深さ 5・40 文字の名前・末端までの1本の枝）。
  - 1回ごとに、`frameApiRoute.reset()` で口の状態（PUT の後の見本）を戻してから画面を読み込み直し、測る操作の直前に `u7Measure.ts` の聞き手と見張りを入れる（NFR 設計 R-09 の手当て）。押す作業ロールは見本の名前で選ぶ。
  - NFR2.5（切り替え。目安の木で判定、悪い側の木は記録だけ）・NFR2.6（4段目のまとまりを開く）・NFR2.7（5段目の末端を押して置き場の見出し）を各5回。判定は5回の中央値（小さい順の3番目）、最大と1回目も記録。測れない回があれば Unverified と理由。値は添付に名前と時間だけで書き、テストの成否にしない。
- **報告**: 添付に値（氏名・メールアドレス）を書かない（差し替えの見本はロールとテーブルの名前だけ）。

### 10.2 170 I の流れ（`170-menu-navigation-flow.e2e.ts`、この Intent の2本までの1本）

時間の上限は既存の流れの E2E（150 の `test.setTimeout(240_000)`）と同じ形にする。名前は走らせるごとに一意（`E2EI_` と実行の印）。後始末はしない。

1. 初期管理者の資格情報（プロセスの環境変数）で API のトークンを取り、`E2EI_` の版 2 の DSL（スキーマ1つ、テーブル T1・T2、メニューは5段の入れ子で各段 40 文字までの名前、末端が T1、T2 を指す項目も置く）を投入して適用する。
2. ロール（`E2EI_ROLE_<印>`）を API で作り、T1 を READ、T2 を NONE にする。
3. `createRegisteredUser`（招待から登録まで）で利用者を作り、氏名を決めて、メールアドレス・パスワード・氏名を `recordSecretValues` で書き、読み戻して3つが揃わなければ流れの始めで失敗にする（NFR1.9、NFR 設計 6節）。そのロールを API で割り当てる。
4. その利用者で画面からログインし、サイドバーの「業務」の5段を開閉のボタン（`sidebar-toggle-biz:<id>`）で開いて末端の T1 を選ぶ。
5. 置き場の見出し（h1）に T1 の表示名が出て、末端の項目に `aria-current="page"` があり、祖先が開いている。
6. 再読み込みしても 5 と同じ（開閉の保存と祖先の展開、AC5.1.8）。
7. T2 の置き場の道（`/tables?schema=…&table=T2`）を直接開くと、権限なしの表示で T2 の表示名が出ない（AC5.1.4）。
8. 利用者のトークンで `GET /api/me/navigation`・`GET /api/me/table-access` の本物の応答を読み、`frameApiRoute.ts` の見本と項目の名前と型が一致する（`support/shapeCompare.ts`）。`GET /api/me/permissions/schemas` も比べる（S8 を含める。14節 Q3: A）。
9. ログアウトの確かめ（`security-design.md` 5.3）: (a) ログインしたまま `/logout` を直接開くとホームに戻り、ログインしたまま。(b) ユーザーメニュー（`userMenu.ts`）から「ログアウト」を選び、この時点から `POST /api/auth/session/logout` を数え始める。(c) ログインの画面が出るのを待つ。(d) ブラウザの戻るでログインの画面のまま、続けて再読み込みでも同じ。(e) (b) からの要求の数が1。(f) `sessionStorage` に開閉の鍵が無い。

### 10.3 150 F の流れへの追加（手順 6 の後、`logical-components.md` 6.2）

- 6a. 初期管理者のトークンで2つ目のロール（`E2EF_ROLE2_<印>`、T を設定しない）を API で作り、同じ利用者に割り当てる。
- 6b. 利用者で画面からログインし、トップバーが「作業ロール: <1つ目>」で、開いた一覧に2つのロールがある。サイドバーに T の項目がある。
- 6c. トップバーで2つ目に切り替え、Toast の入れ物（`toast-container`）に切り替えの文が出て、サイドバーから T の項目が消える。
- 6d. 1つ目に戻すと T の項目が出る。
- 6e. 利用者のトークンで `GET /api/me/work-role` の本物の応答と、`frameApiRoute.ts` の見本の項目の名前と型が一致する。
- 手順 7 以降（140 の見本の形の比べ）は変えない。2つ目のロールの作成は API で行い、画面の操作（S3・S5）は手順 3〜5 で確かめ済みのため繰り返さない（機能設計 6.5 の「管理者が S3・S5 で」との差。12節 D-10）。

## 11. 入口の量の判定（基盤の設計の承認の場の決定 (4)(5)）

| 点 | 時点 | すること | 判定・記録 |
|---|---|---|---|
| 1（Step 3） | B8 の統合の後、固定先の更新の前（`develop`） | `caffeinate -di ./gradlew frontendBundleSize` を流し、入口の合計（gzip）と、`frontend/dist/.vite/manifest.json` の `isEntry` の項目から `imports` をたどった集まり（ファイルの一覧）を記録する | 前の値（判定には使わない） |
| 2（Step 5） | 固定先の更新の後（`feature/261004-role-menu-b9-mycui`） | 同じ | make-you-chic-ui の更新の分の増えを分けて読むための値（基盤の設計の読み直し R-02） |
| — （Step 18・20・22） | 実装 | 3つの画面は登録で `lazy(() => import('./XxxPage').then(...))` にし、`registration.ts`（入口から静的に読まれる）は画面の部品を静的に import しない（`messages.ts` だけ）。骨組みの新しい部品（`app/work-role`・`app/navigation`）は入口に入る | 登録のテストで `screen` が `lazy` であることを確かめる |
| 3（Step 31） | B9 の後 | 同じコマンドで後の値を取り、manifest を読む | **判定**: (i) 鍵 `src/features/tables/TablePlaceholderPage.tsx`・`src/features/mypermissions/MyPermissionsPage.tsx`・`src/features/auth/LogoutPage.tsx` が manifest に**在り**、`isDynamicEntry: true` であること。鍵が無ければ不合格（画面が入口に取り込まれた疑い）。(ii) 入口から静的にたどった集まりに、これらの塊が無いこと。(iii) 3点の合計と差（固定先の更新の分・骨組みの分）、3つの塊の gzip の大きさは記録だけ（500KB の警告は今のまま）。(i)(ii) を満たさなければ、原因の静的な import を直す |
| 4（Build and Test） | — | 3点の記録を `code-summary.md` の「入口の量」の節にまとめ、Build and Test がそこから読む | 持ち主は Build and Test（`cicd-pipeline.md` 6節） |

- 判定は manifest を読むだけで、`check-bundle-size.mjs` は変えない。読む小さなスクリプトは作業の場の一時のファイル（scratchpad）に置き、リポジトリに足さない（B8 と同じ）。鍵の一覧はこの節と `code-summary.md` に残し、Build and Test が同じ手順を繰り返せるようにする。

## 12. この計画で決めたこと・承認済みの文書との差

承認済みの設計の文書は書き換えず、差をここと `code-summary.md` に書く（`project.md` の決まり）。

| ID | 決めたこと | 理由・出典 |
|---|---|---|
| D-1 | 固定先の更新を専用のブランチの専用のコミットにし、B9 の本体より前に `develop` へ fast-forward で統合する。B9 の本体は squash | 4.1、14節 Q1。`cicd-pipeline.md` 7節の「計画で決める」 |
| D-2 | 入口の量を3点で取り、判定は 11節の2条件（鍵の在ることを含む） | 基盤の設計の承認の場の決定 (4)(5)、読み直し R-01・R-02 |
| D-3 | 自分の権限の `displayName` は文字列1つ（要求の言語で選ばれた値）として写す。空のときはカラム・テーブル・スキーマの名前を出す | 3.1 の A5。`frontend-components.md` 2節の `LocalizedLabel` との差 |
| D-4 | 作業ロールの切り替えの `ROLE_BUSY`（409）を D8 と同じ扱い（Toast の文・フォーカスをトリガーへ・作業ロールの読み直し）にする | 3.1 の A2。14節 Q6 |
| D-5 | S8 は表示の言語が変わったら最上位から読み直す（`displayName` がサーバーで言語を選ぶため） | 3.1 の A5。14節 Q4 |
| D-6 | 骨組みの S8 への入口（作業ロールの一覧の最後・ホームの［自分の権限を見る］）は、登録に道 `/me/permissions` があるときだけ出す（骨組みは `features/` を import しないため、登録の道で知る）。ユーザーメニューの項目は mypermissions の登録が持つ | D26（「外すときは3つとも出さない」）を、機能の有無に結び付けて満たす |
| D-7 | `clearExpanded()` は `sessionStorage` の鍵を消す。保存の効果は空の集合のとき鍵を書き戻さない。持ち主は `BusinessNavigationProvider`（未ログインで捨てる）と `LogoutPage` | NFR 設計の読み直し R-05 の手当て |
| D-8 | フック `useWorkRole()`・`useBusinessNavigation()`・`useClearLogoutIntent()` は、提供元が無いときに既定の値（要求を送らない idle）を返す。`renderWithProviders` は変えない | 68 ファイルの既存の画面のテストへの影響を避ける（6.1 (d)(e)） |
| D-9 | `userMenu.ts` の開き口は、`app-shell` の中の `header` の中で、`work-role-switcher` の子孫でない `dropdown-trigger` を探す（機能設計の「`app-shell` の中で入れ物の外」を `header` に絞る） | 表の行の「操作」の Dropdown（`main` の中）とも重ならない形にする。110 の注記の件も当たらなくなる |
| D-10 | 150 に足す2つ目のロールは API で作って割り当てる（画面の S3・S5 は手順 3〜5 で確かめ済み） | 機能設計 6.5 の「管理者が S3・S5 で」との差。14節 Q9 |
| D-11 | 170 の利用者は `createRegisteredUser` で作る（`secretValues.ts` の形の表に当たる） | NFR 設計の読み直し R-06 の手当て |
| D-12 | `frameApiRoute.ts` に `reset()` を置き、測りの1回ごとに戻す | NFR 設計の読み直し R-09 の手当て |
| D-13 | `ShellLayout.test.tsx` に、キーボードだけで5段の末端まで・深さちょうど5段の描画・`aria-expanded`・`aria-current` のテストを置く | NFR 設計の読み直し R-04 の手当て |
| D-14 | コード生成の `traceability.json` では、基盤の traceability で N/A だった NFR1.1・NFR1.4・NFR2.9・NFR2.11・NFR4.1〜NFR4.3 を、テストを target にした OK にする | 基盤の設計の読み直し R-04 |
| D-15 | 固定先の更新の確かめは `--rerun-tasks` を付け、本命は `vendorBuild`・型の検査・既存の画面のテスト・E2E の全体とする | 基盤の設計の読み直し R-05 |
| D-16 | `buildSidebarEntries`・`HOME_ENTRY` を `selectAdminSidebarItems` に置き換え、9つの登録のテストを書き換える（14節 Q5: A） |
| D-17 | README の E2E の表に 135 の行を足す（B8 の書き漏れ） | 14節 Q8: A |
| D-18 | 統合の前の手元の確かめ（`cicd-pipeline.md` 5節）は、AI が scratchpad の Playwright の台本で行う（依頼者の目視は任意） | 14節 Q7: A。`cicd-pipeline.md` 5節の「手元のブラウザで」との差 | 本体で使わない関数を残さない。機能設計の「平らな一覧は `buildNavSections` に移す」 |

Part 2 の途中で計画に無い判断が要ったときは、止まって依頼者に尋ね（G1・G2 … の番号で）、答えを `generation-notes.md` と `code-summary.md` の計画との差に書く。

## 13. Build and Test・配備の段へ引き継ぐこと

- **Build and Test**: 入口の量の3点と判定（11節、`code-summary.md` の「入口の量」の節）、遅延読み込みの3つの塊の大きさ、画面の時間（160 の測り、5回の中央値・最大・1回目。Not Met のときは値で記録して承認の場で扱いを決める）、E2E の全体（010〜170）と `verify` の時間、画面のテストの件数とカバレッジ。固定先の更新の前後のハッシュ。
- **配備の段**: (1) 前の版に戻すと U7 の骨組み（作業ロール・業務のメニュー・置き場・自分の権限・ログアウトの道）と 5bf1ffe のサイドバーが無くなる（戻しはイメージだけ、古い固定先の make-you-chic-ui を同梱した版）。(2) 古いタブの読み込みの失敗は2つの場合に分けて扱う（基盤の設計の読み直し R-03）: 入れ替えの前に開いていたタブが前の版の遅延読み込みの画面（管理の画面など）を開こうとして外れる場合と、イメージだけで戻したときに新しい版のタブが新しい塊の名前を要求して外れる場合。どちらも、入れ替えの後・戻しの後に開いているタブを読み込み直す。(3) スモークテストで業務のメニューを確かめるには、DSL・ロール・割り当てを前提として用意する必要があり、それらは監査に残る。依頼者が画面を触ったときは、監査に残る操作をその場で聞き、監査の確かめの期待の件数を合わせる（`project.md` の学び）。(4) 開いた Dropdown・5段のサイドバーのはみ出しを画面で確かめる。
- **Observability Setup**: 画面の側の監視は置かない（`monitoring-design.md` 1節）。画面が呼ぶ API の時間は role・navigation の区画で見る。

## 14. 依頼者の答え（計画の承認の前に確かめたこと）

計画の段で見つかった9つの点を依頼者に尋ね、すべて A の答えを得た（指揮役から伝達）。答えはこの計画と `unit-test-instructions.md` に反映した。

| 点 | 答え | 確定した扱い | 反映した所 |
|---|---|---|---|
| Q1 統合の形 | **A** | 固定先の更新を専用のブランチ `feature/261004-role-menu-b9-mycui` の専用のコミット P1 にし、先に `develop` へ fast-forward。B9 の本体は `feature/261004-role-menu-b9` から squash | 2.3、4.1、12節 D-1、Step 4〜7・34 |
| Q2 固定先にする版 | **A** | Step 1 で上流の origin を取得し、追加の依頼の3点を含む版があればそれを、無ければ 5bf1ffe を固定先にして進め、残る3点を差として `code-summary.md` に記録し承認の場で伝える | 3.2 の M9、Step 1・4 |
| Q3 S8（Should）を B9 に含めるか | **A** | 含める。170 の手順 8 で自分の権限の本物の形も比べ、入口の量の判定の鍵に `MyPermissionsPage.tsx` を含める | 2.2、2.3、3.1、11節、Step 22・23・28、`unit-test-instructions.md` 3.6・4節 |
| Q4 S8 の言語の切り替え | **A** | 表示の言語を替えたら S8 を最上位から読み直す | 2.3、12節 D-5、Step 22・23 |
| Q5 サイドバーの平らな一覧の関数 | **A** | `buildSidebarEntries`・`HOME_ENTRY` を消し、管理の項目を選ぶ純粋な関数 `selectAdminSidebarItems(registrations, loginState)` に置き換える。9つの機能の登録のテストを同じ中身（管理者だけに出る・並び・未ログインで空）で書き換える。期待は緩めない | 5.4、6.1 (b)(i)、12節 D-16、Step 10・16・17、`unit-test-instructions.md` 3.4 |
| Q6 切り替えの `ROLE_BUSY` | **A** | D8 と同じ扱い（Toast の文・フォーカスをトリガーへ・作業ロールの読み直し）。新しい文言は足さない | 2.3、3.1 の A2、12節 D-4、Step 12・13 |
| Q7 統合の前の手元の確かめ | **A** | AI が使い捨ての内部DB で起動した WAR に対し、scratchpad の Playwright の台本で行い、結果を記録する。台本はリポジトリに足さない。依頼者の目視は任意 | 12節 D-18、Step 33 |
| Q8 README の 135 の行 | **A** | この Bolt の C8 で、160・170 と一緒に 135 の行を足す | 5.9、12節 D-17、Step 30 |
| Q9 150 に足す2つ目のロール | **A** | API で作って割り当てる（画面の S3・S5 は 150 の手順 3〜5 で確かめ済み）。機能設計 6.5 との差として `code-summary.md` に書く | 10.3、12節 D-10、Step 29 |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:1b1210c2e61bcacd9398989674a4d7c1a18abbfde6a25bf393014ab8e8f2f363",
  "contract_sha256": "sha256:0800cfde8b8915bab9d374a3c5479c6961cc23a534522745ba4170d1b16e7349"
}
```
