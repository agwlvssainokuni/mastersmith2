# コード生成の計画 — U3 u3-businessdata-screens（Bolt B5・B6・B7）

U3 は種類 `ui` の単位で、業務データの画面（一覧・詳細・作成・更新・削除の確かめ・開けないときの表示）を作り、`features/tables` の今の置き場（準備中の画面）を置き換える。サーバー（`businessdata` の API 6本）は U1（B2）・U2（B4）で `develop` に統合済みで、この単位はバックエンドの本体のソースに触れない（触れるのはルートの `build.gradle.kts` の E2E のタスクだけ）。

この計画は3つの Bolt を受け持つ。

| Bolt | 中身 | 作業ブランチ | 統合の形 |
|---|---|---|---|
| B5 | 一覧・詳細・開けないときの表示（S1・S2・S6）、`src/shared/` の部品（横のスクロールの枠・件数の選択・NULL の表示）、読み取りの API 3本と記憶、道の登録と今の置き場の置き換え、E2E 160・170 の書き換えと 185（一覧・詳細）、4.14 の試し | `feature/261016-business-data-views-b5` | squash |
| B6 | 作成・更新・削除の確かめ（S3〜S5）、未保存の確かめの移し替えと `markClean()`、作業ロールの切り替えの前の確かめの口、書き込みの API 3本、E2E の見本の PG の仕組み（上乗せの compose・準備と片付け・`-Pe2e.files`）、040 の書き換え、代表の流れ 180、185 の残り、4.14 の試し | `feature/261016-business-data-views-b6` | squash |
| B7 | U4 の後半: axe-core 4.14 以降への引き上げ、`.github/dependabot.yml` の `ignore` の外し、`REQUIRED_RULES` の足しと passes の確かめ、依頼（5）の取り込み（上流が間に合ったとき） | `feature/261016-business-data-views-b7` | 固定先を上げるときは fast-forward、上げないときは squash |

この計画は Part 1（計画）である。依頼者が計画を承認するまで、コードを書かず、ブランチも作らない。

- コード生成の進め方（`aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md` の Deviations）: 単位ごとに U1 → U4 → U5 → U2 → U3 の順で作る。U3 は最後の単位で、B5・B6 と、依頼者の決定（A）で U4 から移した B7 を受け持つ。U4 の B7 にあたる ID（U4 の AC4.1.3・AC4.1.5・AC4.1.6・U4-NFR4.8・U4-NFR5.1〜5.3）は、U4 の `traceability.json` で U3 への Deferred になっている。
- 計画を書いた時点の `develop` の先頭は `04b0589`（B4 の統合）。U1（B2）・U4（B3）・U5（B1）・U2（B4）は統合済み。`vendor/make-you-chic-ui` の固定先は `b0a1e79`（B3 で上げた）。手元の `origin/main` の先頭も `b0a1e79` で、依頼（5）の上流の対応はまだ見えていない（最後の fetch の後の値。B7 で依頼者の承認を得て fetch し直す）。
- 計画を書いた時点で、`aidlc/` の下に未コミットの変更がある（`aidlc-state.md` と監査ログの shard）。B5 の Step 1 で依頼者にコミットの承認を得てから進む。
- 承認の場の決定（各段の `memory.md`）とレビューの指摘は、設計の文書を書き換えず、この計画で直す（4節。`project.md` の学び）。

## 1. 影響の範囲

パスはリポジトリのルートからの相対で書く。名前は機能設計・NFR 設計の仮の名前に従い、生成で変えるときは `generation-notes.md` に差を書く。

### 1.1 新しく作るもの

**B5**

| 置き場 | もの | 部品（NFR 設計 L） |
|---|---|---|
| `frontend/src/features/tables/` | `businessDataTypes.ts`（受け渡しの型。値は `string \| null`）、`businessDataParse.ts`（`unknown` から型へ写す関数。読み取りの3つの応答と Problem Details） | L2 |
| 〃 | `businessDataApi.ts`（読み取りの3本。本文を1回だけ直列化し、64 KB を超えたら送らない） | L1 |
| 〃 | `businessDataStore.ts`（印の表 100 件・テーブルごとの表 100 個・世代・全部を消す口） | L3 |
| 〃 | `valueFormat.ts`・`rowLabel.ts`・`listPaging.ts`（業務データ用のページの計算）・`businessDataLimits.ts`（上限の定数。U1・U2 の定数の名前を説明文に書く）・`problemMessage.ts`（読み取りの code の文と `needsDefinitionReload`） | L5・L6 |
| 〃 | フック `useTableDefinition.ts`・`useRowSearch.ts`・`useRowDetail.ts` | L7 |
| 〃 | 画面 `BusinessDataListPage.tsx`・`BusinessDataDetailPage.tsx`、部品 `SearchPanel.tsx`・`BusinessDataTable.tsx`・`RowActionsMenu.tsx`（「詳細」だけ）・`RestrictionNotice.tsx`・`BusinessDataProblemView.tsx`、各部品の CSS | L7 |
| 〃 | 上のすべてのテスト（`*.test.ts(x)`・`*.property.test.ts`） | — |
| `frontend/src/app/login-state/` | ログインの範囲の知らせ（仮 `loginScope.ts`。ログイン⇄未ログインの切り替わりを購読できる口）とそのテスト | L4 |
| `frontend/src/shared/` | `scroll-region/ScrollRegion.tsx`・`page-size/PageSizeSelect.tsx`・`null-value/NullValue.tsx` とそれぞれの CSS・テスト | L8 |
| `frontend/e2e/support/` | `businessDataFixtures.ts`（型を付けた1つの見本。型は `src` から `import type`）・`businessDataApiRoute.ts`（POST・PATCH の `/api/tables/*` の差し替え。記録はメソッドと道だけ）・`businessDataMeasure.ts`（click と change で測り始め、判定の値と「塗りまで」を残す）と、それぞれの単体のテスト（`e2e/support/**/*.test.ts`） | L10 |
| `frontend/e2e/` | `185-business-data-accessibility.e2e.ts`（一覧・詳細の状態。B6 で作成・更新・削除・離れる確かめを足す） | L10 |

**B6**

| 置き場 | もの | 部品 |
|---|---|---|
| `frontend/src/shared/unsaved-guard/` | `useUnsavedGuard.ts`・`LeaveConfirmDialog.tsx`（roleadmin から移す。`markClean()`・入力の版の数・副作用の無い判定の関数を足す。文言は props で受ける）、CSS、テスト | L8 |
| `frontend/src/features/tables/` | `businessDataApi.ts` の書き込みの3本（4 MiB）と書き込みの結果の分け方（仮 `writeOutcome.ts`）、`formDiff.ts`・`formValidation.ts`、`problemMessage.ts` の書き込みの code、フック `useRowWrite.ts`、画面 `BusinessDataFormPage.tsx`、部品 `DeleteConfirmModal.tsx`、`RowActionsMenu.tsx` の「更新」「削除」、各テスト | L1・L5・L6・L7 |
| `docker/e2e/compose.targetdb-port.yaml` | `targetdb-postgres` に `127.0.0.1:${E2E_TARGETDB_PORT:-15432}:5432` を足すだけの上乗せ（ライセンスヘッダー付き） | 基盤の設計 1.1 |
| `frontend/e2e/support/businessDataSchema.sql` | 表1つと架空の行 21 件以上（スキーマ名は psql の `:"schema"`。ライセンスヘッダー付き） | 基盤の設計 1.1 |
| `frontend/e2e/` | `180-business-data-flow.e2e.ts`（代表の流れ1本） | L10 |

**B7**

| 置き場 | もの |
|---|---|
| （新しいファイルは作らない見込み） | passes の確かめは既存の E2E（110・130・140・160 など）と 185 の中に足す（D6）。依頼（5）が取り込めたときは、`src/shared/scroll-region`・`page-size` を make-you-chic-ui の口に置き換え、要らなくなった部品とテストを消す |

### 1.2 変える既存のファイル

| ファイル | Bolt | 変更 |
|---|---|---|
| `frontend/src/app/navigation/tablePlaceholderPath.ts` | B5 | 4つの道（`/tables`・`/tables/row`・`/tables/new`・`/tables/edit`）を作る1か所の関数と `isTablePath(pathname)` を足す。今の `toTablePlaceholderPath`・`readTablePlaceholderParams`・`TableRef` の形は保つ（D1） |
| `frontend/src/app/navigation/buildNavSections.ts` | B5 | 今の項目の判定（283行の `pathname === TABLE_PLACEHOLDER_PATH`）を `isTablePath` にする（AC1.1.6） |
| `frontend/src/app/navigation/navigationApi.ts` | B5 | `TableRef` の型の import だけ（変えない見込み。型の名前を変えるときだけ直す） |
| `frontend/src/app/login-state/LoginStateGate.tsx` | B5 | 切り替わりのたびに L4 の知らせを鳴らす |
| `frontend/src/features/tables/registration.ts`・`messages.ts` | B5（B6 で足す） | 3つの画面を別々の `lazy` で4つの道に登録（どれも「ログインだけ」）。文言の鍵は `frontend-components.md` の 5節（`tables.preparing` を消し、`tables.forbidden` を変える） |
| `frontend/src/features/README.md` | B5 | 置き場の記述を業務データの画面に直す |
| `frontend/.oxlintrc.json` か `frontend/eslint.config.js` | B5 | D10 が A のとき、`src/features/tables/**` と U3 の `src/shared/` の部品に `no-console`（error）を当てる（テストのファイルを除く）。規則を足すだけで緩めない |
| `frontend/e2e/support/navTreeFixtures.ts` | B5 | `tableAccessApi` の型の import（24行）と `tableAccessWire`（193行）を消す（消すファイルを読んでいるため、型の検査が通らなくなる） |
| `frontend/e2e/support/frameApiRoute.ts` | B5 | `FRAME_API_PATHS.tableAccess`（38・47行）の差し替えを外す（画面が呼ばなくなる） |
| `frontend/e2e/support/u7Measure.ts` | B5 | 判定 `placeholderShown`（59行の `tables-heading`）を、一覧の画面の見出しが出た判定に置き換える（一覧の見出しの `data-testid` は `tables-heading` を引き継ぐ。4.8 の P-7） |
| `frontend/e2e/support/secretValues.ts` | B5（種類）・B6（値のファイル） | `SecretKind` の型と実行時の一覧（36行・111行）を1つの定数から作る形にし、`businessValue` を足す（NFR 設計のレビュー R-07）。185 の見本の目印の値と、U4 の測りの見本の生成の値（D5）を `SECRET_FIXED_VALUES` に足す |
| `frontend/e2e/160-app-frame-accessibility.e2e.ts` | B5 | 置き場の画面（`tables-heading`・`/api/me/table-access`・`tableAccessWire`、101〜120行・295行・333行・467行・508行）を、業務データの一覧の画面と `businessDataApiRoute` の差し替えに書き換える（本数に数えない） |
| `frontend/e2e/170-menu-navigation-flow.e2e.ts` | B5 | 259〜302行の置き場の画面と `/api/me/table-access` の形の比べを、業務データの一覧の画面（型の付いた見本で応答を差し替える）に書き換える（本数に数えない。NFR 要件 Q2: A） |
| `README.md` | B5・B6 | E2E の表（160・170 の説明、180・185 の行、040 の行、170 が最後の番号という説明（210行））、「テーブルの置き場」の段落（1029行）。B6 で E2E の節（見本の PG の上乗せの起動・`.env.targetdb` が要ること・実行のスキーマと一時のロール・`-Pe2e.files`・片付けの件数の見方・E2E の後に上乗せ無しで作り直して閉じる手順・前の残りを手で消す手順）と、TEMPORARY の取り上げの手順（U2 の手順と1つにまとめる） |
| `frontend/src/features/roleadmin/PermissionPanel.tsx`・`testing/renderRoleAdmin.tsx` | B6 | `useUnsavedGuard`・`LeaveConfirmDialog` の import を `src/shared/unsaved-guard/` に変え、文言を props で渡す（振る舞いは変えない） |
| `frontend/src/app/work-role/WorkRoleProvider.tsx`・`WorkRoleSwitcher.tsx` | B6 | `registerSwitchGuard` の口を足す（拒まれたら `'ignored'` を返し、トリガーへフォーカスを戻す。D25a） |
| `build.gradle.kts`（ルート） | B6 | `e2eTest` の前提の確かめ（Mailpit・見本の PG の (a)〜(d)）・前の残りの片付け・準備（実行のスキーマ・表・行・2つの一時のロール・付与）・値の受け渡し（`Exec` の environment）・`-Pe2e.files`・`finalizedBy` の片付けのタスク（基盤の設計 `cicd-pipeline.md` 3.2。4.4 の直しを含む） |
| `frontend/playwright.config.ts` | B6 | `SECRET_ENV_NAMES` に一時の2つのロールの4つの名前を足す。対象DB の環境変数が無いときは設定の読み込みで「`./gradlew e2eTest` から流す」旨の誤りにして止める（Q7: A） |
| `frontend/e2e/040-dsl-admin.e2e.ts` | B6 | 照合の期待を `dsl-warning-group-SCHEMA_MISMATCH` に替え、21行の説明を直す（基盤の設計 Q2: A。本数に数えない）。PG の読み取りが失敗したときは `TARGET_UNAVAILABLE` になり 040 が落ちる旨を説明に1行足す（基盤の設計のレビュー R-07） |
| `frontend/package.json`・`frontend/package-lock.json` | B7 | axe-core を採る版に上げる（B5・B6 の 4.14 の試しは作業フォルダだけで戻す） |
| `.github/dependabot.yml` | B7 | axe-core の `ignore`（74〜75行）と65〜70行のコメントを外し、受け方のコメントを書く（U4 の基盤の設計 6節の形） |
| `frontend/e2e/support/axe.ts` | B7 | `REQUIRED_RULES`（46行）に `label-content-name-mismatch` を足す。`WCAG_TAGS`・`KNOWN_VIOLATIONS`（空）・20 の表示の組は変えない |
| `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` ほか | B7 | 区分ごとの passes の確かめ（U4 の L5 `labelContentNameProblems`）を足す。C4 は 130 の2語の氏名の場面に限る（U4 のコードのレビュー R-01）。足す場面は D6 |
| `vendor/make-you-chic-ui`（固定先） | B7 | 依頼（5）の上流の対応がソースで確かめられたときだけ、承認を得た専用のコミットで上げる |

### 1.3 消すもの（B5）

`frontend/src/features/tables/TablePlaceholderPage.tsx`・`TablePlaceholderPage.css`・`TablePlaceholderPage.test.tsx`、`useTableAccess.ts`、`tableAccessApi.ts`・`tableAccessApi.test.ts`（D28）。サーバーの `GET /api/me/table-access`（`navigation` の `NavigationController`）とそのテストは消さず、後の Intent に回す（G5）。

### 1.4 影響を受ける既存のテストと E2E（検索の結果）

計画を書く前に、変える型・道・口の名前で `frontend/src`・`frontend/e2e`・`frontend/playwright.config.ts` を検索した（`project.md` の学び）。機能設計の `frontend-components.md` の 7節の一覧に、太字のものを足した。

| 検索した名前 | 当たったファイル | 扱い |
|---|---|---|
| `tablePlaceholderPath`・`TABLE_PLACEHOLDER_PATH`・`toTablePlaceholderPath`・`readTablePlaceholderParams` | `app/navigation/tablePlaceholderPath.test.ts`・`buildNavSections.ts`・`buildNavSections.test.ts`・`buildNavSections.property.test.ts`・**`navigationApi.ts`（型の import だけ）**・`features/README.md`・`features/tables/` の置き場の3ファイル | 書き換える（4つの道と今の項目）。`navigationApi.ts` は形を保てば変えない |
| `'/tables`（道の文字） | **`app/layout/ShellLayout.test.tsx`（520・632〜690・856〜890行）**・`features/tables/registration.test.ts`・`TablePlaceholderPage.test.tsx` | `ShellLayout.test.tsx` は道の文字を使うだけで置き場の画面を描かないため変えない見込み。B5 の Step 8 の後に流して確かめる |
| `useUnsavedGuard`・`LeaveConfirmDialog` | `features/roleadmin/useUnsavedGuard.ts`・`.test.tsx`・`LeaveConfirmDialog.tsx`・`.test.tsx`・`PermissionPanel.tsx`・`PermissionPanel.test.tsx`・`testing/renderRoleAdmin.tsx` | B6 で移して書き換える。**`LeaveConfirmDialog.tsx` は `useRoleAdminText` と `RoleListPage.css` に頼っており、`src/shared/` は `app/`・`features/` を読めない（`eslint.config.js` の `sharedImportRule`）ため、文言は props で受け、CSS は部品と同じ場所に移す** |
| `WorkRoleContextValue`・`useWorkRole`・`switchTo` | `app/work-role/WorkRoleProvider.tsx`・`.test.tsx`・`WorkRoleSwitcher.tsx`・`.test.tsx`、**`app/navigation/BusinessNavigationProvider.tsx`・`.test.tsx`・`app/pages/HomePage.tsx`・`features/mypermissions/useMyPermissions.ts`** | 口を足すだけで今の項目は変えない。**既定の値（83〜85行）にも `registerSwitchGuard` を足す**。`BusinessNavigationProvider.test.tsx` などは、B6 の Step 31 の後に流して変わらないことを確かめる |
| `LoginStateGate` | `app/App.tsx`・`LoginStateGate.test.tsx`・`ShellLayout.tsx`・`.test.tsx`・`DisplaySettingsProvider.tsx`・`.test.tsx`・`useReloadTriggers.ts`・`BusinessNavigationProvider.tsx`・`testing/renderWithProviders.tsx`・`routing/AppRouter.tsx`・`WorkRoleProvider.tsx`・`features/registration/useRegistration.ts`・`roleadmin/testing/renderRoleAdmin.tsx` | 知らせの口を足すだけ。`LoginStateGate.test.tsx` に知らせのテストを足し、ほかは B5 の verify で変わらないことを確かめる |
| `table-access`・`tableAccessApi`・`tableAccessWire` | `features/tables/tableAccessApi.ts`・`.test.ts`・`TablePlaceholderPage.test.tsx`、**`e2e/support/navTreeFixtures.ts`（24行の型の import・193行）**・`e2e/support/frameApiRoute.ts`（38・47行）・`e2e/170-menu-navigation-flow.e2e.ts`（33・296〜302行）・**`e2e/160-app-frame-accessibility.e2e.ts`（64・114〜116行）** | 消す・書き換える。`tsconfig.json` の `include` に `e2e` があるため、`navTreeFixtures.ts` は置き場のファイルを消すのと同じコミットで直す |
| `tables-heading`・`tables-page`・`placeholderShown` | **`e2e/160-app-frame-accessibility.e2e.ts`（295・333・467・508行）**・**`e2e/support/u7Measure.ts`（59行）**・`e2e/170-menu-navigation-flow.e2e.ts`（273・274行） | 一覧の画面の見出しと枠に同じ `data-testid` を引き継ぎ、書き換えを小さくする（4.8 の P-7）。160 の測り（`placeholderShown`）は一覧の見出しの判定に置き換える |
| `/api/tables`（業務データの API） | 画面からの呼び出しは無い（U1・U2 のサーバーと README だけ） | 足すだけ |
| `SECRET_ENV_NAMES`・`SecretKind`・`SECRET_FIXED_VALUES` | `playwright.config.ts`（58行）・`e2e/support/secretValues.ts`（36・111行）・`playwright-secret-check-reporter.ts` | B5・B6 で足す（4.3 の R-07） |
| `REQUIRED_RULES`・`labelContentNameProblems`・`LABEL_CONTENT_NAME_RULE` | `e2e/support/axe.ts`（46・130行）・`axeRuleChecks.ts`・`.test.ts` | B7 で足す |
| `dsl-warning-group-`・「接続先が設定されていません」 | `e2e/040-dsl-admin.e2e.ts`（21・141〜147行） | B6 で書き換える |
| E2E の順と適用中の DSL | `e2e/150`（`E2EF_`）・`e2e/170`（`E2EI_`。README は「170 は最後の番号」）・新しい 180（実行のスキーマの DSL を適用する）・185（差し替え） | 180 より後に流れるファイル（185）は、適用中の DSL に頼らない（基盤の設計のレビュー R-07）。B7 で足す確かめも、180 が適用した DSL に頼らない |

### 1.5 実際の API の形との突き合わせ（U1・U2 のソースで確かめた点）

機能設計の `frontend-components.md` の 3節の型は仮の形で、U1・U2 のコード生成で足された項目がある。`backend/src/main/java/cherry/mastersmith/businessdata/web/` の record を読み取りで確かめ、写しの関数は次の形で受ける。

| 応答・要求 | 実際の形（ソース） | 写しの扱い |
|---|---|---|
| 定義 `TableDefinitionResponse` | `definitionId`（`dslHash`・`workRoleId`（数か null））・`definitionChanged`・`schema`・`table`・`displayName`（`ja`・`en` の組）・`view`・`hasPrimaryKey`・`primaryKeyColumns`（DETAIL が許されるときだけ。ほかは空）・`operations`（`LIST`・`DETAIL`・`CREATE`・`UPDATE`・`DELETE`）・`restrictionReason`（`NO_PRIMARY_KEY`・`PRIMARY_KEY_HIDDEN`・`VIEW`・null）・`writeConfigured`・`rowLabelColumns`・`listColumns`・`detailColumns`・`searchFields`・`sortableColumns`・`defaultSort` | 機能設計に無い `schema`・`table`・`hasPrimaryKey`・`primaryKeyColumns`・`defaultSort` も読む（U1 の G17・G20）。`schema`・`table` が要求と違えば形の崩れにする |
| カラム `ColumnDefinitionResponse` | 読み取りの項目に、検証の材料（`formPart`・`required`・`nullable`・`minLength`・`maxLength`・`precision`・`scale`・`min`・`max`・`choices`・`hasPattern`）を省かずに足す。`required`・`nullable` は必ず真偽値、型に当たらない項目と入力できないカラムの `formPart` は `null`（`JsonInclude.ALWAYS`）。`formPart` に `lookup` は来ない（`text`） | 入力にするカラム（`editableOn*` が真）で `formPart` が `null`・欠け、`required`・`nullable` が欠け、知らない列挙の値・`lookup` が来たら形の崩れ（U3-NFR4.24）。入力にしないカラムの `formPart: null` は正しい形 |
| 検索の項目 `SearchFieldResponse` | `column`・`displayName`・`operator`（`EQUALS`・`CONTAINS`・`RANGE`・`CHOICE`）・`valueType`・`defaultValue`・`collapsed`・`options`（`value`・`label` の組） | 真偽値の選択肢の表示名は値のまま来るため、画面が `listFormat` に合わせて文言を当てる（U1 の G17） |
| 一覧 `RowPageResponse` | `definitionId`・`definitionChanged`・`items`（`key`（null あり）・`values`・`truncatedColumns`）・`page`（long）・`size`・`total`（long） | `page`・`total` は数で受け、Number の安全な範囲を超えたら形の崩れ（page の上限は 1,000,000、U1 の `PageRequest.MAX_PAGE`） |
| 詳細 `RowDetailResponse` | `definitionId`・`definitionChanged`・`key`・`values`・`truncatedColumns` | — |
| 作成 `RowCreatedResponse`（201） | `definitionId`・`key`（作った行の主キー。無ければ項目ごと出ない） | `key` が無いときは一覧へ戻る（D24、U2 の G8・R-03） |
| 更新・削除 `RowWrittenResponse`（200） | `definitionId` だけ | 4.3 の R-03 の形で「保存した」とみなす |
| 要求 | 定義 `{schema, table, definitionId?}`、一覧 `{schema, table, definitionId, conditions[{column, value?, from?, to?, values?}], sort?{column, direction}, page, size}`、詳細・削除 `{schema, table, definitionId, key}`、作成 `{schema, table, definitionId, values}`、更新 `{schema, table, definitionId, key, values}` | 書き込みは `definitionId` を必ず送る（無いと 409 `BUSINESS_DEFINITION_CHANGED`。U2 の G12） |
| 上限の定数 | 本文: 読み取り・削除 `BusinessDataWebConfig.MAX_BODY_BYTES = 65_536`、作成・更新 `MAX_WRITE_BODY_BYTES = 4_194_304`。値の長さ: 検索 `SearchRequestValidator.MAX_VALUE_LENGTH = 1_000`、書き込み `ColumnWriteRulesFactory.MAX_VALUE_LENGTH = 10_000`（DSL の `maxLength` が短ければそれ）。数え方はどちらも `String.codePointCount` | NFR 設計のレビュー R-13・NFR 要件のレビュー R-04 の確かめは、計画を書く時点で読み取りで済んだ（4.3）。画面の `frontend/src/shared/validation/codePoints.ts` の `countCodePoints` で同じ数え方になる |
| 誤りの code | `BusinessDataProblemTypes` の 13 個（`BUSINESS_DSL_NOT_APPLIED`・`TABLE_NOT_FOUND`・`ROW_NOT_FOUND`・`ROW_OPERATION_NOT_ALLOWED`・`ROW_NOT_UNIQUE`・`TARGET_DB_MISMATCH`・`TARGET_DB_UNAVAILABLE`・`TARGET_DB_TIMEOUT`・`DEFINITION_CHANGED`・`WRITE_NOT_CONFIGURED`・`DUPLICATE_VALUE`・`REFERENCE_NOT_FOUND`・`SAVE_REJECTED`）と共通の `ACCESS_DENIED`・`VALIDATION_FAILED`・`PAYLOAD_TOO_LARGE`（`CommonProblemTypes`）。`fieldErrors.reason` は `BusinessDataFieldErrorReason` の 16 個（`NOT_ALLOWED`〜`NOT_EDITABLE_NOT_NULL`） | 文の表は 4.6 |

### 1.6 カバレッジとパッケージ

- バックエンドの本体のソース（`backend/src/main`）に触れないため、`packagesJudgedByTotal` の7パッケージにも、パッケージごとの下限にも当たらない。
- フロントエンドの下限（`frontend/vitest.config.ts` の `thresholds`。全体の合計で行 80%・分岐 70%）とカバレッジの対象・除外は変えない（U3-NFR7.12）。B4 の後の値は行 95.37%・分岐 90.32%。`src/features/tables/` の値は Build and Test で記録する。
- 置き場の画面のテストを消すため、消した後も下限を割らないことを B5 の verify で確かめる。

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB へ書き込むテスト（業務データの作成・更新・削除を範囲に入れる場合）は、アプリの対象DB への接続とテストの接続が別のため、アプリのトランザクションの巻き戻しに頼らない。3種類の対象DB のすべてで、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。書いた後の値は、アプリとは別の接続で読み直して確かめる。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E をどのブランチで統合の前に流すかは Way of Working のとおりとし、リリースの前にも手元で実行する。\n- 業務データを表示する E2E は、見本の対象DB（PostgreSQL の1種類）がコンテナで動いていることを前提にする。`./gradlew e2eTest` は始める前に対象DB に届くかを確かめ、届かなければ起動の手順を示して失敗する（Mailpit と同じ扱い。起動・停止はしない）。流れが使う行は、その流れで自分で作るか、流れの専用のスキーマに置き、見本の行は架空の値だけにする。対象DB の資格情報は Playwright の `webServer.env` に置かずプロセスの環境変数で渡し、報告の json を文字列で検索して資格情報・接続先の値が入っていないことを確かめる。Intent 261016-business-data-views で足す E2E は業務データの代表の流れ1本までとし、持ち越しの直し（make-you-chic-ui の固定先の更新・axe-core の取り込み）と既存の E2E の書き換えは本数に数えない。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- 業務データの一覧・詳細（と、範囲に入れるなら作成・更新・削除）の機能では、次のテストを必ず書く（役割・権限の機能と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - 権限ごとの操作の可否: テーブルの主権限と補助権限の組ごとに、業務データの API の未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書き、403 を確かめる利用者は要る権限だけを欠く利用者にする。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わること。\n  - 主キーとビュー: 主キーの無いテーブルとビューでは、行を1件に決められない操作（更新・削除など）を、画面で隠すだけでなくサーバー側で拒否する。主キーが複数のカラムのテーブルも確かめる。★主キーの無いテーブルで詳細を見せるか\n  - カラムの出し分け: カラムの主権限が NONE（読めない）のカラムは、画面で隠すだけでなく API の応答に含めないことを確かめる。DSL の表示の設定（`list.visible`・`detail.visible`）で隠すだけのカラムの扱いは設計の段で決め、決めた側の動作を明示したテストにする。\n  - IDOR と一括代入: スキーマ名・テーブル名・主キーの値・並べ替えの列・検索の列を差し替えた要求（適用中の DSL に無い名前、権限の無いテーブル、存在しない行）は拒否され、状態が変わらないこと。作成・更新の本文に許していないカラムを足しても反映されないこと。\n  - 403 と 404 の使い分け: 権限の無いテーブル・DSL に無いテーブル・存在しない行の応答の状態コードと code をそろえるか。★使い分け（決めた側の動作を明示したテストにする）\n  - SQL の組み立て: 識別子は適用中の DSL にある名前と照らし、値はバインド変数で渡すこと。識別子・検索の値・並べ替えに `'`・`\"`・`;`・`--`・バッククォート・改行を含めても、SQL として解釈されないこと（Code Style の SQL の組み立ての決まりを確かめる）。\n  - 3種類の対象DB の境界の値: 一覧・詳細（と書き込み）を、MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。型の違い・識別子の大文字と小文字・NULL の並びと、境界の値（最大の整数・桁の多い小数・NULL）を含める。\n  - 行の数の上限と時間切れ: 一覧のページの大きさの上限の境界（上限ちょうどは受け付け、超えると拒否）。JDBC の取り出しの上限（`setMaxRows` か問い合わせの上限）を置き、上限を超える行を内部で読まないこと。応答しない対象DB での時間切れの応答（既存の ServerSocket の方式）。\n  - 部分一致の検索: 部分一致の検索を設けるなら、検索の値の `%`・`_`・エスケープの文字が一致の条件として働かない（エスケープして渡す）こと。\n  - 画面に出す値: 業務データの値とカラムの表示名を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、一覧・詳細で文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - 道に乗る主キーの値: 画面の道に乗る主キーの値（複合の主キー、`/`・`%`・`?`・`#`・空白・引用符を含む値）をエンコードし、サーバー側でデコードの後に照らすこと。\n  - 接続の数: 業務データの API が1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT`（`NavigationConnectionUsageIT` など）と同じ形の結合テストで決定的に確かめる。\n  - 性質ベースのテスト: テーブルの主キーの有無・ビューか・主権限・補助権限から許す操作の集合を求める関数と、識別子の照合の部品は純粋な関数にし、jqwik・fast-check で性質（権限を足しても許す操作は減らない、主キーの無いテーブルに行を1件に決める操作が出ない、DSL に無い名前は拒否される、引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない など）を確かめる。\n  - 漏えい: 業務データの行の値（主キーの値を含む）と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性（外部へ送る設定を有効にした結合テスト）・エラー応答に含まれないこと。JDBC の例外のメッセージが応答にもログにも出ないこと。\n  - 監査: ★業務データの操作（読み取りを含めるか）を監査に残すか、残す中身、対象DB の確定と監査の行の一方だけが残ったときの扱い。決まった側の動作を明示したテストにする。\n  - 画面: 一覧・詳細の画面ごとのアクセシビリティ検査、権限の無いテーブルへ直接移ったときの 403 の表示、操作の可否に合わせてボタンが押せない（または出ない）こと。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06) \n- 長いテスト（verify・E2E・k6）は caffeinate -dims で包み、流す前に pmset -g assertions で眠らない状態か、流した後に pmset -g log で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。DarkWake の Thermal Emergency のスリープは caffeinate -i では止められず、role-menu の B8 で画面のテストが約 660 秒止まって時間切れになった。 (learned 2026-10-13) \n- コード生成の計画の段で、変える型・表・移行の番号・API・画面の骨組みを使っている既存のテストと E2E を検索で洗い出し、影響の範囲に入れる。role-menu では移行の最後の版を前提にしたテスト（AuditMigrationCompatibilityIT）、SQL の文の数を数えるテスト（GroupAdminListQueryCountIT）、狭い幅のはみ出し（E2E の 375px）で計画の外の失敗が起き、生成が止まった。 (learned 2026-10-13) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- k6 の場面の setup は、前の場面が残した状態（ロールの割り当てなど）に頼らず、自分で前提を作るか片付けてから始める形で書く（role-menu の Performance Validation で、workRoleRead・myPermissionsTree・roleAssignmentsRead が前の場面の割り当てで 409 ROLE_NO_CHANGE や確かめの不一致になり、割り当てを外して流し直した）。 (learned 2026-10-15) \n- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) \n- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) \n- 依頼者の決定で、E2E をブランチごとの統合の前ではなく Intent の最後（Build and Test）に1回だけ流すことにした。① と ② は統合の前に流して通し、③・④ は ./gradlew verify だけで統合した。team.md の「画面・認証に関わる変更は統合の前に E2E」との差。 (learned 2026-10-15) \n- CI の 776738e の1回目が AdminForbiddenProvider.test.tsx の1件（描画の後の効果の回数を、効果が済む前に数える形）で落ち、2回目の試行で通った。手元で10回流して再現しなかったため、team.md の「手元で再現しないものは CI の再実行で通れば進めてよい」の扱いとし、見立てを記録した（同じテストが二度落ちたら直す）。 (learned 2026-10-15) \n- dslMixed の memory.peak が 94.4%・取り直し 93.1% で線（90% 未満）を超えた。peak の時の内訳は anon 68.3%・回収できるページキャッシュ 23.7% で、依頼者の決定「持ち越す」で設定を変えず Not Met のまま記録した。物差しを anon に替えると Met になるが、目標の測り方を後から緩めることになるため選ばなかった。 (learned 2026-10-15) \n- 1回目の使い捨てのアプリのログの WARN 43 行を、内訳を読む前に環境を消したため取り直せなかった（project.md の「確かめの結果を見てから片付ける」の漏れ）。memory.peak の取り直しの回では、消す前に WARN を数える指示を最初から書いた。 (learned 2026-10-15) \n- business-data-views の Delivery Planning の実測（約 13 分）で、全体の合計で判定している7パッケージのうち下限に足りないのは common.health（行 79.2%）だけで、audit.repository は数える行が無かった（全体の合計 行 98.0%・分岐 92.6%）。 (learned 2026-10-18) \n- U1・U2・U3 で捨ての試しを先に行い、前提が崩れた点（U1 は MySQL・MariaDB の重さ、応答しない MySQL の 50 秒、bit と空間の型。U2 は perf の MySQL の OOMKilled、PG の TEMP の権限、更新できないビュー、JDK 25 で重くならない式。U3 は測り終わりの時点、T3 が代わりの案にならない）を追加の質問（F）で依頼者に確かめてから文書に反映した。 (learned 2026-10-18) \n- U2 の Q7: A で perf の MySQL 1792m を短い捨ての試しで確かめ、プールを埋めた状態で上限に張りつき（anon 90%）、プール以外の量も見立ての約 614 MiB でなく約 840 MiB と分かったため、追加の質問 F1 で 2304m にした。U1 の文書の値は承認の場でそろえる。 (learned 2026-10-18)"
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
  "input_sha256": "sha256:51e889664b3e247205a3fc601835ef28134fa4e098f9c6eb3f4c667fcf787668",
  "contract_sha256": "sha256:7b5ef641649418f9e844285e996fba87207000bd5c245758d4323958b43707d3"
}
```

Contract の層の読み替え: この単位は画面だけの単位で、サーバーを作らない。`Data model / database behavior` は受け渡しの型と写しの関数（L2）、`Repository / data access` は API の呼び出し（L1）と画面の外の記憶（L3）とログインの範囲の知らせ（L4）、`Business logic` は純粋な関数（L5・L6）、`Frontend behavior` は共通の部品・骨組みの変更・フック・画面（L7〜L9）に読み替える。`API / endpoint` はこの単位に当たらない（サーバーは U1・U2）。`Environment/build configuration` は E2E の補助と E2E（L10）・`build.gradle.kts` の E2E のタスク・上乗せの compose・lint の設定にあたる。methodology（test-after）と ordering（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）はそのまま守り、Bolt ごとに同じ順を繰り返す。B6 では、見本の PG の安全の確かめと E2E のタスク（Environment/build configuration）を画面の層より前に置く（Step 22〜25。理由は Step 24）。

（上の `## Testing Contract` の節は、この計画の 2節にあたる。JSON の塊は `aidlc engine testing-posture render` の出力（`tc-u3.md`）の `## Testing Contract` の塊を、一字も変えずに写したもの。）

## 3. 依頼者の承認を得ること

計画の承認（Approve Plan）で、次の承認もあわせて得る。ただし、その時点で中身が決まるもの（固定先・axe-core の版・REVOKE の実行）は、その Step で中身を見せてもう一度承認を得る。生成の途中で、ここに書いた範囲を超える変更が要るとわかったら、生成を止めて依頼者に諮る（計画を書き換えず、`generation-notes.md` に記録する。`project.md` の学び）。

| # | Bolt | 変更 | 中身 | 承認の時点 |
|---|---|---|---|---|
| A1 | B5 | 今の置き場の画面と画面からの `GET /api/me/table-access` の呼び出しを消す | 1.3 のファイルを消す。サーバーの API とそのテストは残す（G5。消すのは後の Intent） | 計画の承認 |
| A2 | B5・B6 | 既存の E2E の書き換え（本数に数えない） | B5: 160・170（置き場の画面 → 業務データの一覧、応答の差し替え）。B6: 040（`SCHEMA_MISMATCH` の期待） | 計画の承認 |
| A3 | B6 | 既存の機能 roleadmin の本体のソースに手を入れる | `useUnsavedGuard`・`LeaveConfirmDialog` を `src/shared/unsaved-guard/` へ移し、import を変え、文言を props で渡す。roleadmin は `markClean()` と版の数を使わず、振る舞いは変えない（移したテストで確かめる） | 計画の承認 |
| A4 | B6 | 見本の PG を E2E の間だけ `127.0.0.1:15432` に開ける上乗せの compose | 開けたらすぐに、誤ったパスワードの接続が拒まれる（scram に当たる）ことを確かめる。`trust` に当たったら、すぐに上乗せ無しで作り直して閉じ、生成を止めて Q1 を諮り直す（基盤の設計のレビュー R-02） | 計画の承認。開ける操作の前に一言伝える |
| A5 | B6 | 見本の PG への手の `REVOKE TEMPORARY ON DATABASE business FROM PUBLIC` | 配備したアプリも使う共有の見本の PG を変える操作。SQL は U2 の README と `docker/targetdb/postgres/03-writer-account.sh` と同じであることを読み取りで確かめて見せる（基盤の設計のレビュー R-01） | その Step で SQL を見せて承認を得てから流す |
| A6 | B6 | `build.gradle.kts` の `e2eTest` が見本の PG の中に実行のスキーマと一時のロールを作って消す | 触るのは接頭辞 `e2e\_biz\_%` のスキーマと `mastersmith\_e2e\_%` のロールだけ。管理者のパスワードはプロセスに入れない（Q3: A） | 計画の承認 |
| A7 | B5・B6 | axe-core 4.14 の試し（作業フォルダだけ） | `frontend/package.json`・`frontend/package-lock.json` を一時に変えて E2E の全件を流し、`git diff` で戻したことを確かめる。統合しない | 計画の承認 |
| A8 | B7 | `vendor/make-you-chic-ui` で `git fetch`、依頼（5）の上流の対応のソースでの確かめ、固定先の2回目の更新 | 固定先を上げるときは、前後のハッシュ・上流の `main` から辿れること・vendor の lockfile の差（`hasInstallScript` の増減）を見せ、承認を得た専用のコミットにする。`hasInstallScript` が増えていたら取り込む前に諮る | fetch の前と、固定先を上げる前の2回 |
| A9 | B7 | axe-core の版・`package.json` の範囲・lockfile、`.github/dependabot.yml` の `ignore` の外し | 採る版（その時点の 4 系の最新）、CHANGELOG で既定で流れるようになった規則、`license`（MPL-2.0 のままか）、`hasInstallScript`、`npm ls axe-core` が1つを見せる | その Step で見せて承認を得てから入れる |
| A10 | B7 | passes の確かめの対象の区分の一覧（U4 の Q-2、NFR 設計 L5 の手順 2） | D6 の答えの形で、B5・B6 の試しの結果を足して確定した一覧を見せる | その Step で見せる |
| A11 | B5 | lint の規則の足し（D10 が A のとき） | `no-console` を当てる。規則を緩めない | 計画の承認 |

- 緩める変更は無い。ArchUnit の境界テスト・`eslint.config.js` の `no-restricted-imports`・`sharedImportRule`・SpotBugs の除外・カバレッジの下限と除外・axe の規則と要素（`disableRules`・`exclude`・`KNOWN_VIOLATIONS`）は、どれも変えない（`project.md` の Forbidden）。
- 新しい npm の依存は足さない（NFR 要件の `tech-stack-decisions.md`）。axe-core は既にある devDependency の版の更新（A9）。足すことになったら、採用の前にライセンスを確かめて諮る。

## 4. 反映した決定（承認の場の決定・レビューの指摘・先の単位のコード生成の結果）

承認済みの設計の文書は書き換えず、この計画で直す（`project.md` の学び）。

### 4.1 機能設計の承認の場（`construction/functional-design/memory.md`）

| 決定 | この計画での扱い | Step |
|---|---|---|
| U2 R-07・U3 R-09: `nullable` は U2 が足す検証の材料、`formPart` から `lookup` を外す、U3 は `formPart`・`nullable`・`required` が欠けたら形の崩れ | 写しの関数で、入力にするカラムに3つのどれかが欠けたら形の崩れ。`lookup` は知らない値として形の崩れ。6.2 の `text`・`lookup` の行は `text` だけと読む。U2 の実装（1.5）と合う | 26・27 |
| R-01（保存の後の移動）・R-02（行の名前）・R-05（前提が崩れたとき）・R-08 | 機能設計の 12.1 のとおり直った形で作る（D9・D24・D25・D4） | 6・7・30〜33 |
| R-03（B5 の E2E の行の出どころ） | NFR 要件 Q2: A のとおり、170 と 185 は型を付けた1つの見本で応答を差し替え、180 だけが本物の見本の PG で流す | 13・14・34 |
| R-04（作業ロールの切り替えの後の画面） | D2 で決める | 32・33 |
| R-06（保存の 403 の文） | D3 で決める | 28・32 |
| R-07（主キーの記憶の寿命） | NFR 設計で `peekRowKey`（読んでも消さない、作った順に最大 100 件）に決まった形で作る | 4・5 |
| R-10・R-11・R-12 | `lookup`、行のリンクの見える文字が空のときの「（空）」と最初のカラムが列に無いときの扱い（U3-NFR5.9）、「1回だけ飛ばす」の旗の3つの下ろす条件（U3-NFR7.11）で作る | 6・7・30・31 |

### 4.2 NFR 要件の承認の場（`construction/nfr-requirements/memory.md`）

| 決定 | この計画での扱い |
|---|---|
| U3 R-01: 見本 DB の管理の資格情報をプロセスの環境変数で渡し `SECRET_ENV_NAMES` に足す | 基盤の設計の Q3: A で、管理者は `docker compose exec` でコンテナの中のローカルのソケットから入り、管理者のパスワードをプロセスに入れない形になった（基盤の設計の上流との差 1）。`SECRET_ENV_NAMES` には一時の2つのロールの値だけを足す（Step 24） |
| ID の重なり | 単位の中の ID として扱い、単位の接頭辞を付けて指す（例 `U3-NFR4.22`・`U4-NFR5.1`）。`traceability.json` も同じ |
| NFR 要件のレビュー R-02（100 × 100 の描画を早く試す） | NFR 設計の試し（T1 は中央値 46 ms）に加えて、B5 の Step 13 で、本物の画面で 185 の測りの1件を先に作って流す。目標を超えたら生成を止めて、承認の場の候補（重ねたときに作る Tooltip・仮想化）を諮る（F2: A） |
| R-03（作業ロールの切り替えでも消す） | NFR 設計 Q2: A のとおり roleId の変化で全部消す（Step 4） |
| R-04（サーバーの数え方） | 1.5 のとおり、U1・U2 とも `codePointCount` で、画面の `countCodePoints` と同じ（計画を書く時点で確かめた） |
| R-06（違反の要素の持ち主） | 4.14 の試しの記録に持ち主の列（`mycui-` の印があれば make-you-chic-ui）を足し、make-you-chic-ui の部品なら依頼（5）と B7 に渡す（Step 17・37） |

### 4.3 NFR 設計の承認の場（Major はレビューの推奨の方向でこの計画で直す）

承認の場の決定（`construction/nfr-design/memory.md` の 2026-10-19T00:30:00Z）: 各単位の Major は文書を直さず、レビューの推奨の方向でコード生成の計画で直す。U3 の Minor もあわせて扱う。

| 指摘 | 直し方 | Step |
|---|---|---|
| **R-01（Major）書き込みの 5xx の分け方** | 書き込みの結果は「分かる」と「分からない」に分ける。「保存されていない」と言うのは、既知の code が読めたときだけ（503 `BUSINESS_TARGET_DB_UNAVAILABLE`・409 のすべての業務の code・400 `VALIDATION_FAILED`・413 `PAYLOAD_TOO_LARGE`・403 `ACCESS_DENIED`・404 `BUSINESS_ROW_NOT_FOUND`・`BUSINESS_TABLE_NOT_FOUND`・`BUSINESS_DSL_NOT_APPLIED`）。応答が届かない・500・502・504・ほかの 5xx・503 で code が無いか知らない code・本文が Problem Details でない 5xx は、すべて「分からない」。この倒し方を `writeOutcome` の表と単体テストに入れる | 26〜29 |
| **R-02（Major）二重の作成の手当て** | 「分からない」の後の画面の状態を D4 で決める（入力を残す・もう一度押すときの確かめ・確かめる入口）。作成の二重のおそれを、D4 の選択肢で比べた | 32・33 |
| R-03 2xx の範囲 | 「保存した」とみなすのは、期待する状態（作成 201、更新・削除 200）で、本文が JSON として読めて（`content-type` が `application/json` か `application/problem+json` でない JSON）、形が違うか、本文が空のとき。`content-type` が JSON でない 2xx（HTML のログインのやり直しの画面など）は「分からない」 | 26・27 |
| R-04 消去と遅れた答え・roleId の null | (1) 記憶は世代を持ち、全部を消すたびに世代を進める。消す前に送った要求の答えは、世代が違えば記憶に書き戻さない。(2) roleId の変化は、記憶に最後に書いたときの roleId と違うときすべて（null から数、数から null を含む）を「変化」とし、全部消す。一覧・詳細の画面は、作業ロールの読み込みが済んでから最初の要求を送るため、初めの読み込みで状態が消えることは無い | 4・5・10 |
| R-05 別のタブで利用者が替わる場合 | D9 で決める | 4 |
| R-06 入力の履歴の自動補完 | 業務データの検索・作成・更新の入力に `autocomplete="off"` を付ける（password は `new-password`）。画面のテストで属性を確かめる | 11・12・32・33 |
| R-07 `SecretKind` の型と実行時の一覧 | 型と一覧を1つの定数（`as const` の配列）から作り、`businessValue` を足す。値のファイルに `businessValue` を書いて読み戻す単体テストを `e2e/support/secretValues.test.ts` に足す | 13 |
| R-08 旗の副作用と beforeunload | 移動を止めるかの判定の関数は副作用を持たない（旗は読むだけ）。旗を下ろすのは、止めなかった移動が成り立った後（道か問い合わせが変わったことを `useLocation` の変化で見る）と、`markClean()` の後に版の数が変わったとき。`markClean()` で外した `beforeunload` は、その後に入力が変わったら付け直す。実物の data router（`createMemoryRouter`）で、判定が何回呼ばれても1回の移動で旗が尽きないこと、`markClean()` の後の入力の変更で `beforeunload` が戻ることをテストに足す | 30・31 |
| R-09 185 の場面 | 185 のログインの利用者は 130 と同じ `routeLoginPreferences` の差し替えで2語の氏名にする（初期管理者の氏名は変えない）。状態の一覧に、サイドバーを展開した状態・403 の表示・「保存されたか分かりません」の知らせ・空の一覧・読み込み中を足す。測りは 20 組＋200% の2件の外の、別の1件（23 件目）にする | 14・34 |
| R-10 185 の時間の見積もり | 見積もり: 1件に axe を約 8 回（一覧は 100 カラム × 20 行）。B3 の 4.14 の試しの記録と同じ形の画面の axe 1回を約 1.5〜3 秒と見て、1件 約 20〜40 秒、22 件で約 7〜15 分、測りの1件（5 回 × 4 判定）で約 1 分。E2E の全体（基準 232 件・約 10分35秒）の 1.5 倍（約 15分53秒）を超える見込みが高い。B5 の Step 16 で 185 の時間を実測し、超えたら（B5 は一覧・詳細だけで、B6 で増える分の見込みも添えて）組の分け方を依頼者に諮る。上限で落とさない | 16・36 |
| R-11 「塗りまで」と 100 件のまま戻る場面 | 「塗りまで」が目標を超えても Not Met にせず、承認の場で報告するだけにする。測りに「詳細から戻って 100 件のまま一覧を描く」場面（判定 `listShown100Back`、目標 1.0 秒）を1つ足す | 13 |
| R-12 180 の DSL と以降のファイル | 180 は実行のスキーマの DSL を適用し、後始末はしない。180 より後のファイル（185）は差し替えで、適用中の DSL に頼らない。資格情報（一時の2つのロール）は報告の json の文字列の検索の対象に入れる | 24・34 |
| R-13 定数と数え方の確かめ | 計画を書く時点で読み取りで確かめた（1.5）。生成で定数を書くときは、説明文に U1・U2 の定数の名前を書く | 6 |

### 4.4 基盤の設計の承認の場（Major はレビューの推奨の方向でこの計画で直す）

承認の場の決定（`construction/infrastructure-design/memory.md` の 2026-10-19T06:00:00Z）: U3 R-01（TEMPORARY の取り上げの時期）・R-02（15432 を開ける安全性の確かめを、最初に開ける Bolt へ前倒し）は、文書を直さずレビューの推奨の方向で直す。

| 指摘 | 直し方 | Step |
|---|---|---|
| **R-01（Major）TEMPORARY の取り上げの時期** | 確かめ (c) を有効にする Bolt と、手での REVOKE を同じ Bolt にそろえる。D1 が A なら、上乗せ・準備と片付けのタスク・(c)・040 の書き換え・手の REVOKE をすべて B6 に入れ、REVOKE は準備のタスクを入れる前の Step で行う。B5 の統合の前の E2E は今の前提（対象DB の接続先が無い）のまま流す。手順には「共有の見本の PG（配備したアプリも使う）を変える操作」と書き、SQL が U2 の README と同じことを確かめる | 22・23 |
| **R-02（Major）15432 を開ける安全性** | (1) 上乗せを入れる Bolt（D1 が A なら B6）の最初の Step で、開けたらすぐに誤ったパスワードの接続が拒まれることを確かめる。ホストに psql が無ければ、見本の PG と同じイメージ（同じ digest）の `docker run --rm` の一時の psql で、`127.0.0.1:15432` 宛てに誤ったパスワードで入り、拒まれることだけを見る（値は表示しない）。(2) `e2eTest` の前提の確かめに (d) として同じ確かめを足す（拒まれなければ止める）。(3) `trust` に当たったら、すぐに上乗せ無しで作り直して閉じ、生成を止めて Q1 を諮り直す。(4) README に、E2E の後に上乗せ無しで作り直して閉じる手順を書く | 22・24・36 |
| R-03 psql の呼び方 | `-v ON_ERROR_STOP=1` を付け、準備は1つのトランザクション（`-1`）にする（CREATE ROLE・GRANT は巻き戻せる）。値は標準入力の `\set` で渡し、argv に出さない。失敗時の標準エラー出力は、平文の一時のパスワードを含みうるため、扱いを D8 で決める | 24 |
| R-04 準備と片付けの受け渡し | (1) スキーマ名・ロール名は Gradle の同じビルドの中の値（`extra` か `BuildService`）で片付けのタスクへ渡し、パスワードは片付けに渡さない（片付けは名前だけで行える）。Gradle が途中で止められて片付けが動かなかったときは、次の実行の「前の残りを消す」が接頭辞で拾う。(2) 前の残りを消す段が失敗したときに手で消す手順を README に書く。(3) `docker compose exec` と `pg_isready` の呼び出しに `--profile targetdb-postgres` を付け、`DOCKER_HOST`（colima）を引き継ぐ前提を書く。(4) 接続の切断（`pg_terminate_backend`）の対象は、接頭辞に当たるロールの接続だけにする | 24 |
| R-05 作り直しの間の配備したアプリ | 「短い間の読み取りの失敗。戻りは自動の見込み」と README に書き、B6 で配備したアプリを動かしたまま上乗せで作り直す回を1回確かめる（配備したアプリのヘルスと、作り直しの後に業務データの一覧の API が戻ること。確かめないときは未確認として記録する） | 22・36 |
| R-06 traceability の基準 | この段の `traceability.json` では、OK の target にコードの部品とテストを書き、テストの中身だけのものは OK（テストのファイルと件名）とする。U3-NFR4.3（N/A）は、E2E の一時のロールを読み取りと書き込みで分ける形を target にした OK（部分）にする | 18・38・50 |
| R-07 040 の揺れと 180 の後のファイル | 040 の説明に「PG の読み取りが失敗したときは `TARGET_UNAVAILABLE` になり 040 が落ちる」旨を1行足す。180 より後に置くファイルは、適用中の DSL に頼らない（1.4） | 25・34・46 |

### 4.5 U4（B3）のコード生成の結果と、B7 へ引き継いだこと

| 項目 | この計画での扱い | Step |
|---|---|---|
| U4 のコードのレビュー R-01（C4 の確かめは2語の氏名の場面に限る） | C4（トップバーのトリガー）の passes の確かめは 130 の2語の氏名の場面だけで行い、1語の氏名の場面の incomplete は記録だけにして合否に使わない（U4 の D10c） | 46 |
| R-02（行の値を `data-testid` に入れない） | U3 が新しく作る `data-testid` は固定の名前か、末尾がページの中の番号 `r<n>` のもの（`tables-row-actions-r<n>`・`tables-row-link-r<n>`）だけにする。主キー・行の値・スキーマ名・テーブル名・カラム名を `data-testid` と DOM の `id` に入れない（U4 の Q-3 の答え。U3-NFR4.2・NFR4.21）。画面のテストで形を確かめ、コードの検索でも確かめる | 11・12・32・33 |
| R-03（測りの見本の生成値を json の探索に入れるか） | D5 で決める | 13 |
| Q-2（C2・C7 は場面が無い） | D6 で決める | 46 |
| Q-1・Q-4・Q-5（N-10・N-2〜N-5・記録のコミット） | U4 の承認の場で扱う。この計画は N-2〜N-5 の鍵の形をそのまま使う（`frontend/e2e/support/axeElementKey.ts`） | — |
| B7 へ引き継いだこと（U4 の `generation-notes.md` の 4節） | axe-core の版の確定、`dependabot.yml` の直し（受け方のコメントを書く。Way of Working との差は「lockfile だけの更新の例外、根拠は Testing Posture の axe-core の決まり」と書く）、`REQUIRED_RULES` の足しと区分ごとの passes の確かめ、U3 の行の操作・行のリンクの区分、依頼（5）の上流の確かめと固定先の2回目の更新、`dist` の確かめ、E2E と vitest-axe の違反 0 件と件数、E2E の時間 | 41〜52 |

### 4.6 U2（B4）のコード生成の結果と、画面の文の選び方

U2 の D1〜D4・G5・G6・G8・G12・G13・R-03 で、画面が受ける code と状態コードが承認済みの機能設計（6.1）・NFR 設計（5節・6節）から変わった。画面は code と reason だけで文を選ぶため、文の表を次の形にする（日英で対にする。U3-NFR6.3）。

| 受ける応答 | U2 の実装での中身 | 画面の文と行き先 |
|---|---|---|
| 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（書き込み） | 借りられない・事前の問い合わせの段・**書き込みの文の段の時間切れと断絶（D3）**・**競合による巻き戻し（デッドロックなど、D1）**・**違反の後の段の時間切れと断絶（D4）**。どれも保存されていない | 「保存されていません。時間をおいてやり直してください。」（削除は「削除されていません」）。入力は残す |
| 503 `BUSINESS_TARGET_DB_TIMEOUT`（書き込み） | **確定の段の失敗だけ**（D3。監査は `UNKNOWN`） | 「保存されたか分かりません」と確かめる入口（4.3 の R-01・D4） |
| 409 `BUSINESS_SAVE_REJECTED` で `fieldErrors` なし | DB が拒んだ値（CHECK・日時の範囲・文字集合・NUL）・**形の誤り（D2）**・**確定の段の類 23（D3）**・**違反の後の確かめの失敗（G5: A。誤りを付けない）**・**MySQL・MariaDB の符号つきの整数の範囲の外（G13）** | 「保存できませんでした（保存はされていません）。入力を確かめてやり直し、続くときは管理者に確かめてください。」をフォームの上に出す。削除の 409 は「ほかのデータから使われているため削除できません。」 |
| 409 `BUSINESS_SAVE_REJECTED` で `fieldErrors` あり | `NOT_EDITABLE_NOT_NULL`（`field` は空の文字。入力できないカラムの名前は出さない、G6）・更新の `NOT_NULL`（送った NULL のカラムがちょうど1つならそのカラム、ほかは `field` が空、G6） | `field` が空の誤りはフォームの上に、カラムの名前の誤りはその入力に結ぶ |
| 409 `BUSINESS_DUPLICATE_VALUE`・`BUSINESS_REFERENCE_NOT_FOUND` | 一意の違反・参照先に無い値（事前の問い合わせと違反の後の確かめのどちらでも同じ code。カラムが決まれば `fieldErrors`。複合の外部キーは先頭のカラム、G7） | 機能設計の 6.1 のとおり |
| 409 `BUSINESS_DEFINITION_CHANGED` | 定義が古い。**`definitionId` が無いときも（G12）** | F6 |
| 201（作成） | `key` は、DETAIL が許され、主キーを作れたときだけ。**送らなかった主キーのカラムが2つ以上の MySQL・MariaDB（G8）と、生成された鍵を文字で読めないとき（R-03）は `key` が無い** | `key` があれば詳細へ、無ければ一覧へ（D24） |
| 400 `VALIDATION_FAILED` | `fieldErrors.reason` は `BusinessDataFieldErrorReason` の 16 個。`PATTERN_TIMEOUT` を含む | reason ごとの文（`tables.error.<reason>`）。画面に結べる `field` が無いとき（`field` が空、U1 の page の上限など）は一覧・フォームの上に出す |
| 413 `PAYLOAD_TOO_LARGE` | 本文の上限 | 「入力が大きすぎるため送れませんでした。値を短くしてください。」（画面で先に止めたときも同じ） |

- **`needsDefinitionReload` の直し**: 機能設計の D4 は「`fieldErrors` の `field` が今の定義の検索の項目・カラムに無ければ F6」とする。U2 は `field` が空の文字の誤り（G6）を返すため、`field` が空の文字の誤りは「今の定義に無い」の判定から外す（空の `field` で F6 に入らない）。単体テストに入れる（Step 7・29）。
- 監査の `UNKNOWN` は画面に出さない（U2 の計画 9節）。
- 書き込みの 409 で `operations` を外れた操作（`BUSINESS_ROW_OPERATION_NOT_ALLOWED`・`BUSINESS_WRITE_NOT_CONFIGURED`）は、機能設計の 6.1 のとおり。

### 4.7 U1（B2）のコード生成の結果

| 項目 | この計画での扱い |
|---|---|
| 定義の応答の足し（G17〜G20: `schema`・`table`・`displayName`・選択肢の表示名、`hasPrimaryKey`・`primaryKeyColumns`） | 1.5 のとおり写す。真偽値の選択肢の表示名は値のまま来るため、画面が `listFormat`（`BOOLEAN_YES_NO`・`BOOLEAN_ON_OFF`）の文言を当てる |
| R-04（G7、主キーが NONE のテーブルでも主キーで並べる） | 画面には響かない（主キーの値は返らない） |
| G15（MySQL・MariaDB の範囲の外の日時は 409 `BUSINESS_TARGET_DB_MISMATCH`） | 6.1 の MISMATCH の文で出す |
| G15（U2 の記録。読み取りの時間切れの後の次の1回の 503） | 画面は 503 の文を出してやり直しの入口を出すだけ。自動では送り直さない（U3-NFR1.9） |

### 4.8 計画で決めたこと（設計の文書が計画に回したもの）

| # | 論点 | 決め | 根拠 |
|---|---|---|---|
| P-1 | L4 の口の名前と形（NFR 設計の未決） | `frontend/src/app/login-state/loginScope.ts`（仮）に `subscribeLoginScope(listener): () => void` と、LoginStateGate が呼ぶ `notifyLoginScopeChanged()` を置く。機能（tables）は骨組みの口を購読するだけ（機能から骨組みへの import は許される） | NFR 設計 L4 |
| P-2 | `takeRowKey` の名前 | `peekRowKey`（読んでも消さない）。機能設計の `takeRowKey` との差を記録する | NFR 設計の上流との差 2 |
| P-3 | 測りの判定の名前と見本の生成の関数 | 判定は `listShown100`・`listShown20`・`detailFocused`・`formShown100` と、R-11 の `listShown100Back`。見本は `businessDataFixtures.ts` の `wideDefinition(columns)`・`widePage(rows, columns)`・`longDetail()` など（値は架空の固定の文字と番号だけ） | NFR 設計 4節 |
| P-4 | `src/features/tables/` の `no-console` | D10 | NFR 設計の後の段 |
| P-5 | 主キーのカラムを更新で変えるとき（機能設計 11節・R-07） | 起きない。U1・U2 の `TableAccessPolicy`（`backend/src/main/java/cherry/mastersmith/businessdata/domain/TableAccessPolicy.java` 136〜137行）は、主キーのカラムの `editableOnUpdate` を常に偽にする（`editableOnCreate && !key`）ため、更新で主キーは変わらない。更新の成功の後は今の印で詳細へ戻る（D24）。画面は定義の `editableOnUpdate` に従うだけで、主キーの作り直しの仕組みは作らない。主キーのカラムが `editableOnUpdate: true` で来たら、形の崩れにはせず読み取りで出す（サーバーの判定を正とし、画面で主キーを送らない） | 機能設計 11節、R-07、計画を書く時点で読み取りで確かめた |
| P-6 | 業務データの画面の chunk の大きさの記録 | B5・B6 の前と後で、`check-bundle-size.mjs` の入口の値（gzip）と、3つの画面の chunk の gzip を記録する。`check-bundle-size.mjs` は超えても警告だけ（基盤の設計 `cicd-pipeline.md` 2節の「上限を超えたら止める」はスクリプトの今の振る舞いと違うため、差を記録する） | U3-NFR1.10 |
| P-7 | 既存の `data-testid` の引き継ぎ | 一覧の画面の枠に `tables-page`、見出し（h1。テーブルの表示名）に `tables-heading` を引き継ぎ、160・170・`u7Measure` の書き換えを小さくする。403 の表示の枠は今の置き場の画面と同じ見た目と `data-testid` の形にそろえる | 1.4 |
| P-8 | E2E の補助の単体のテスト | `businessDataFixtures`（見本が写しの関数を通ること）・`businessDataApiRoute`（道とメソッドの照らし）・`businessDataMeasure` の純粋な部分・`secretValues` の読み戻しの単体のテストを `e2e/support/*.test.ts` に置く（U4 の Q2: A で vitest の対象） | 基盤の設計 `cicd-pipeline.md` 2節 |
| P-9 | 一覧の画面が最初の要求を送る時点 | 作業ロールの読み込みが済んでから（4.3 の R-04） | NFR 設計 3節 |
| P-10 | 定義の記憶と作業ロール | テーブルの鍵（スキーマ名とテーブル名）ごとに `{ definition, listState }` を持ち、`definitionId.workRoleId` が今の作業ロールと違う定義は使わない | NFR 設計 3節 |

## 5. Step（Bolt ごとに 準備 → 実装 → その層のテスト → E2E → 1コマンドの検査 → 記録 → コミット → 統合）

どの Step も、コードのコメント・JSDoc は日本語、テストの説明文（`describe`・`it`）は英語、ファイルの先頭に Apache License 2.0 のヘッダー（TypeScript・CSS・JavaScript は `/* ... */`、YAML・SQL・シェルは言語のコメントの形。2026、agwlvssainokuni）を置く。エクスポートは名前付きだけ、`enum` を使わず文字列リテラルの union、`any` を使わず `unknown` で受ける（team.md の Code Style）。テストの値は架空の値だけにし、メールアドレスは `example.com` などの予約のドメインだけにする。描画の後に反映される値は `waitFor` で待って確かめ、テストの時間の上限は原因を確かめずに延ばさない。部品ごとに vitest-axe の検査を1件入れる。各 Step のテストのコマンドは `unit-test-instructions.md` の 3節にある。

長いテスト（verify・E2E）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる。入っていたら結果を使わずに流し直す（`project.md` の学び）。

### B5（一覧・詳細）

#### 準備

- [x] **Step 1: 作業の準備と走らせ方の確かめ（B5）**
  - `aidlc/` の下の未コミットの変更（計画を書いた時点で `aidlc-state.md` と監査ログの shard）を確かめ、依頼者にコミットの承認を得てから進む（`project.md` の Change Control）。
  - git の対象外の生成物（`frontend/playwright-report`・`frontend/test-results`）が残っていれば消す（`project.md` の学び）。
  - `develop` の先頭が B4 の統合（`04b0589`）か、その後の記録のコミットであることと、B4 の統合の後の CI の結果を試行ごと（`gh run view --json attempt,jobs`）に確かめる。CI が失敗していたら、team.md の「不安定なテストと CI の失敗」の決まりで扱ってから進む。
  - `develop` から `feature/261016-business-data-views-b5` を作って移る。
  - 走らせ方の確かめ（brownfield。最初のテストの Step の前）: `unit-test-instructions.md` の 3.1 のコマンドで、書き換える既存のテスト（置き場の画面・道・今の項目・LoginStateGate・ShellLayout）が変更の前に通ることを確かめ、件数を記録する。
  - 変更の前の値の記録: `npm run build` の後の `check-bundle-size.mjs` の入口の値（gzip）と、置き場の画面の chunk の gzip（P-6）。
  - 1.4 の検索を、生成の直前の `develop` でもう一度流し、増えた当たりがあれば `generation-notes.md` に足す。

#### 型と写しの関数（Contract の Data model）

- [x] **Step 2: 受け渡しの型と写しの関数を書く**
  - `businessDataTypes.ts`: 1.5 の実際の形の型（`TableDefinition`・`ColumnDefinition`（検証の材料は任意の項目。B6 で入力にするカラムの欠けを確かめる）・`SearchField`・`RowPage`・`RowItem`・`RowDetail`・`DefinitionId`・`LocalizedText`・`CellValue = string | null`・列挙の union）。
  - `businessDataParse.ts`: `parseTableDefinition`・`parseRowPage`・`parseRowDetail`・`parseBusinessProblem` を `unknown` から写す。例外を投げず、形が違えば `undefined`。列挙はすべて既知の一覧と照らす（`lookup` を含めない）。値は文字か `null` で、数に直さない。`schema`・`table` が要求と違う定義の応答は形の崩れ。
- [x] **Step 3: 型と写しの関数のテストを書いて流す**
  - 例のテスト（正しい形・各項目の欠け・知らない列挙・`lookup`・数の値の型の誤り・`page`・`total` の範囲・`key` の null・`definitionChanged` が 400 にも付くこと）と、性質ベースのテスト（fast-check。任意の JSON で例外を投げずに `undefined` か正しい型を返す。BIGINT・DECIMAL の桁を失わない。失敗のときの乱数の種を記録する）。

#### API の呼び出し・記憶・ログインの範囲の知らせ（Contract の Repository / data access）

- [x] **Step 4: 読み取りの API・記憶・知らせを書く**
  - `businessDataApi.ts`: `getDefinition`・`searchRows`・`getRow`。本文は `JSON.stringify` を1回だけ行い、`utf8ByteLength` が 65,536 を超えたら送らずに 413 と同じ扱いにする（U3-NFR4.23）。今の `apiRequest` を通し、失敗は今の ApiError のまま投げる。自動では送り直さない。
  - `businessDataStore.ts`: `putRowKey`（印は `crypto.getRandomValues` の 16 バイトの16進。主キーから作らない）・`peekRowKey`（読んでも消さない。作った順に最大 100 件、101 件目で最も古いものを捨てる）・`tableEntry`（テーブルごとの `{ definition, listState }`。使った順に最大 100 個）・`clearBusinessDataStore`・世代（全部を消すたびに進め、古い世代の書き戻しを捨てる。4.3 の R-04）・roleId の変化での全部の消去（null と数の間の変化を含む）。ブラウザの保存に写さない。`console` を呼ばない。
  - `frontend/src/app/login-state/loginScope.ts`（P-1）と、`LoginStateGate.tsx` の切り替わりのたびの知らせ。記憶は読み込まれた時に購読する。
  - D9 が B のときは、`features/auth` の更新の成功で利用者が変わったときにも知らせる口を足す（A のときは既知の制約として記録するだけ）。
- [x] **Step 5: API・記憶・知らせのテストを書いて流す**
  - `businessDataApi.test.ts`（本文の上限ちょうどは送る・+1 バイトは送らない、サロゲートの組と組になっていないサロゲートを含む、要求の本文の形、失敗の投げ方）、`businessDataStore.test.ts`（101 件目で最古が捨てられる、読んだ後も残る、101 テーブル目、3つの消す時（ログアウト・トークンの更新の失敗で未ログイン・新しいログイン）と roleId の変化で消える、消した後の遅れた書き戻しが捨てられる）、`loginScope.test.ts` と `LoginStateGate.test.tsx` の足し。

#### 純粋な関数（Contract の Business logic）

- [x] **Step 6: 書式・行のラベル・ページ・文と読み直しの判定・上限の定数を書く**
  - `valueFormat.ts`（D15。日英で同じ形、時差つきは `+00:00`、`masked` は `••••••`、BINARY は「（バイナリ）」、NULL は「（値なし）」、空の文字列は空のまま、真偽値は `listFormat` の文言）、`rowLabel.ts`（`rowLabelParts`・`rowLabelText`・`rowLinkText`。見える文字が空白だけなら「（空）」にし、行のラベルにも含める。最初のカラムが一覧の列に無ければリンクを出さない判定を返す。U3-NFR5.9）、`listPaging.ts`（20・50・100、表示の範囲、ページを超えたとき）、`businessDataLimits.ts`（65,536・4,194,304・1,000・10,000。説明文に U1・U2 の定数の名前）、`problemMessage.ts` の読み取りの code と `needsDefinitionReload`（`field` が空の文字の誤りは判定から外す。4.6）。
- [x] **Step 7: 純粋な関数のテストを書いて流す**
  - 例のテストと性質ベースのテスト（D29・U3-NFR7.10）: BIGINT・DECIMAL の桁を失わない、`<`・`"`・`'`・`javascript:` を含んでも文字のまま返る、任意の値（NULL・空の文字列・`masked`・BINARY・真偽値・数、先頭のカラムが空の文字列を含む）で行のリンクの見える文字が「詳細: <表示名> <行のラベル>」と `rowActionsName(見える文字, 行のラベル)` に必ず含まれる。`needsDefinitionReload` は `definitionChanged` のとき・今の定義に無い `field` のときに真、今の定義の項目だけ・`field` が空の文字だけなら偽。失敗のときの乱数の種を記録する。

#### 骨組みの変更と共通の部品（Contract の Frontend behavior の骨組みと shared）

- [x] **Step 8: 道・今の項目と、共通の部品を書く**
  - `tablePlaceholderPath.ts` に4つの道を作る1か所の関数と `isTablePath`、`buildNavSections.ts` の今の項目の判定（D1・L9）。
  - `src/shared/scroll-region/ScrollRegion.tsx`（`role="region"`・`aria-label`・`tabIndex={0}`、横にだけスクロール）、`src/shared/page-size/PageSizeSelect.tsx`（make-you-chic-ui の Select。20・50・100）、`src/shared/null-value/NullValue.tsx`（淡い文字の「（値なし）」、読み上げは「値なし」、色だけで表さない）。`src/shared/` は `app/`・`features/` を読まない（文言は props）。
- [x] **Step 9: 骨組みと共通の部品のテストを書いて流す**
  - `tablePlaceholderPath.test.ts`・`buildNavSections.test.ts`・`buildNavSections.property.test.ts` の書き換え（4つの道、詳細・作成・更新でもテーブルの項目が今の項目、道の値は `URLSearchParams` でエンコードされ `/tables` で始まる）。`ShellLayout.test.tsx` を書き換えずに流して通ることを確かめる。3つの共通の部品のテスト（各 5〜8 件、vitest-axe を1件ずつ）。

#### フック・画面（Contract の Frontend behavior）

- [x] **Step 10: フックを書き、続けてそのテストを書いて流す**
  - `useTableDefinition`（記憶にあれば読まない、D4 のきっかけで読み直す、作業ロールの読み込みの後に始める P-9）・`useRowSearch`（操作1回で一覧の API は1本、世代で古い答えと画面を離れた後の答えを捨てる、400 で `needsDefinitionReload` なら定義を読み直す、403 の後に送り直さない U3-NFR8.7）・`useRowDetail`（印が記憶に無ければ `missing`）。
  - テスト（各 5〜8 件）: 操作ごとの要求の数、遅らせた古い応答が新しい表示を上書きしない、消去の後の書き戻しが捨てられる、403 の後に要求が増えない、決まった間隔の読み直しが無い。
- [x] **Step 11: 一覧・詳細・開けないときの画面と部品を書く**
  - `BusinessDataListPage`（D3・D7〜D14、`aria-busy`、件数と表示の範囲の `aria-live`、空の一覧の出し分け、ページを超えたとき）、`SearchPanel`（開閉の `aria-expanded`・`aria-controls`、範囲と選択肢は `fieldset`・`legend`、誤りは `aria-invalid`・`aria-describedby`、入力に `autocomplete="off"`。R-06）、`BusinessDataTable`（`React.memo`、列の定義は `useMemo` で1回、Table の `getRowId` はページの中の番号、セルは素の `span` を Tooltip で包み CSS だけで省略、`truncatedColumns` の印、行のリンクの `data-testid` は `tables-row-link-r<n>`、`ScrollRegion` で包む）、`RowActionsMenu`（Dropdown を `placement="bottom-end"`、名前は U4 の `rowActionsName`、包みの span の `data-testid` は `tables-row-actions-r<n>`。B5 は「詳細」だけ）、`RestrictionNotice`、`BusinessDataDetailPage`（`dl`、`white-space: pre-wrap; overflow-wrap: anywhere`、開いたら h1 へフォーカス、`document.title` に行のラベルを入れない）、`BusinessDataProblemView`（6.1 と 4.6 の文、F8 の作業ロールの切り替えの入口）、`messages.ts`（日英）、`registration.ts`（3つの画面を別々の `lazy`、4つの道。B5 では `/tables/new`・`/tables/edit` は登録しない（作成・更新の画面は B6）、各部品の CSS。
  - 一覧の画面の枠と見出しに `tables-page`・`tables-heading` を引き継ぐ（P-7）。
  - 1.3 のファイルを消し、`e2e/support/navTreeFixtures.ts` の型の import と `tableAccessWire` を消す（型の検査を通すため同じ Step で行う）。`features/README.md` を直す。
  - D10 が A のとき、lint の設定に `no-console` を足す。
- [x] **Step 12: 画面と部品のテストを書いて流す**
  - 部品ごとに 5〜8 件と vitest-axe 1件。必須の項目（team.md の「業務データの一覧・詳細」の画面の分、U3-NFR7.9）: 権限の無いテーブルへ直接移ったときの 403 の表示、`operations` に無い操作のボタンが出ない、値と表示名の `<script>`・`<img onerror>`・`"`・`'`・`javascript:` が文字として出てリンクの先にならない、`/`・`%`・`?`・`#`・空白・引用符を含む複合の主キーが道・履歴の状態に現れず記憶を通して詳細へ往復できる、`data-testid` と `id` に行の値が無い（4.5 の R-02）、読み直しの間の `aria-busy`、切った値の全体が DOM の文字と Tooltip にある（U3-NFR2.11）、ブラウザの戻る操作で一覧の状態が保たれ再読み込みで既定に戻る、`registration.test.ts` の `lazy` と道（U3-NFR1.10）。

#### E2E の補助と E2E（Contract の Environment/build configuration）

- [x] **Step 13: E2E の補助と、測りの先行**
  - `businessDataFixtures.ts`（型を付けた1つの見本。100 カラム × 100 行・NULL の行・長い表示名・10,000 字の値。値は架空の固定の文字と番号だけ）、`businessDataApiRoute.ts`、`businessDataMeasure.ts`（判定は P-3 の5つ。判定の値と「塗りまで」を並べ、記録は名前と時間だけ）、`secretValues.ts` の `SecretKind` の1つの定数化と `businessValue`（R-07）、185 の見本の目印の値と、D5 が A なら U4 の測りの見本の生成の値（`測定 NN`・`sokutei.NN@example.com`）を `SECRET_FIXED_VALUES` に足す。それぞれの単体のテスト（`e2e/support/*.test.ts`、P-8）を書いて流す。
  - 測りの先行（NFR 要件のレビュー R-02）: 185 の測りの1件（既定の組・既定の幅・各 5 回・中央値）だけを先に作り、`./gradlew :backend:bootWar` と Mailpit の起動の後に `npx playwright test e2e/185-business-data-accessibility.e2e.ts -g measure` で流す。中央値が目標（`listShown100`・`listShown100Back` 1.0 秒、`listShown20`・`detailFocused` 0.5 秒）を超えたら、生成を止めて依頼者に諮る（候補は「重ねたときに作る Tooltip」と「仮想化」。F2: A）。「塗りまで」が超えたときは、止めずに記録して承認の場で報告する（R-11）。
- [x] **Step 14: 160・170 の書き換えと 185（一覧・詳細）**
  - 160: 置き場の画面を業務データの一覧の画面に、`/api/me/table-access` の差し替えを `businessDataApiRoute` の差し替えに、測りの `placeholderShown` を一覧の見出しの判定に置き換える。170: 259〜302行を、型の付いた見本で応答を差し替えた業務データの一覧の画面と、権限の無いテーブルの道の 403 の表示に書き換える（表示名と名前が出ないことは今のまま確かめる）。`frameApiRoute.ts`・`u7Measure.ts` を直す。
  - 185: `displayCombos.ts` の 20 組を1組1件にし、各件は1回のログイン（`routeLoginPreferences` で2語の氏名）で、一覧（100 カラム × 20 行、NULL の行・長い表示名）・開いた検索の項目と誤りの状態・開いた行の操作のメニューと収まり（`overflow.ts`）・サイドバーを展開した状態・空の一覧・読み込み中・403 の表示・詳細（10,000 字）を回って状態ごとに axe を流す。200% の2件（640×360 の CSS ピクセル。既定の組と dark・lg）。測りの1件は別の件。規則・要素は外さない。記録は選択子を出さず、U4 の鍵の形。作成・更新・削除・離れる確かめの状態は B6 の Step 34 で足す。
  - `README.md` の E2E の表（160・170 の説明、185 の行）。

#### 検査・記録・統合

- [x] **Step 15: 1コマンドの検査とカバレッジ・時間の記録（B5）**
  - `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す。対象DB のテストが SKIPPED でないこと）。
  - 記録: verify の時間（B4 の後は 21 分 4 秒。CI の上限の7割の約 42 分を超えたら依頼者に諮る）、フロントエンドのテストの件数とカバレッジ（全体の合計。下限を割らないこと）、`npm run build` の後の入口と3つの画面の chunk の gzip（P-6。B5 では作成・更新の画面はまだ無い）。
  - `src/features/tables/` と U3 の `src/shared/` の部品で、`console`・`localStorage`・`sessionStorage`・`indexedDB`・`dangerouslySetInnerHTML`・`document.title` を検索して 0 件であることを記録する（U3-NFR4.2・4.20）。
- [x] **Step 16: E2E の全件（4.13）**
  - B5 は `frontend/src` に触れるため、統合の前に E2E の全件を流す（team.md の Way of Working）。D1 が A なら、見本の PG はまだ前提にしない。Mailpit を起動して `./gradlew e2eTest` を流し、件数（基準 232 件から増える見込み: 185 の 23 件）・時間・ファイルごとの時間（`test-results/e2e-results.json` の duration）・秘密の確かめの件数 0 を記録する。
  - E2E の全体が基準（232 件・約 10分35秒）の 1.5 倍（約 15分53秒）を超えたら、185 の時間と B6 で増える分の見込みを添えて、組の分け方を依頼者に諮る（上限で落とさない。U3-NFR7.8）。
- [x] **Step 17: axe-core 4.14 の試し（B5、統合しない）**
  - 作業フォルダの中だけで axe-core を 4 系の最新（4.14 以上）にして E2E の全件を流す（A7）。違反を「ファイル × 表示の組 × 規則 × 鍵 × 区分 × 持ち主（`mycui-` の印があれば make-you-chic-ui、無ければ U3）」で記録し、行の操作のトリガーと行のリンクが `label-content-name-mismatch` の passes・incomplete・violations のどれに入ったかを件数で記録する（U3-NFR5.8）。
  - 違反が U3 の部品なら、B5 の中で直してから統合する（規則・要素を外さない）。make-you-chic-ui の部品なら、新しい依頼の文面を `make-you-chic-ui-request-<n>.md` に置いて依頼者に諮る（FR14.5）。
  - `package.json`・`package-lock.json` を戻し、`git diff` に差が無いことを確かめる。
- [ ] **Step 18: 記録とコードのレビュー（B5）**
  - `code-summary.md` の B5 の節（作ったもの・計画との差・依頼者に確かめたいこと）、`generation-notes.md`（計画に無い変更は計画を書き換えずにここへ）、`source-manifest.json`（B5 の作った・変えた・消したパスを足す）、`traceability.json`（B5 の分を足す。4.4 の R-06 の基準）。
  - 指揮役がコードのレビューを依頼する。レビュー役には、git・gradlew・npm・docker を使わず Read だけで確かめるよう最初から伝える（`project.md` の学び）。指摘の扱いを依頼者に諮る。
- [ ] **Step 19: コミットの提案と積み（B5、依頼者の承認）**
  - 7節の B5 の区切りで提案し、承認を得てから積む。区切りごとに `npm run typecheck`（と `npm run lint`）が通ることを、区切りの順に重ねて確かめる（`project.md` の学び）。コミットの提案は、確かめたい点の問いとは別に、積む前に必ず尋ねる。
- [x] **Step 20: 統合（B5、依頼者の承認の後）**
  - 統合の前に `aidlc/` の未コミットの変更（監査ログを含む）をコミットしておく（`project.md` の学び）。
  - Step 15 の verify と Step 16 の E2E が通った状態で、`develop` へ squash で1コミットとして戻す。件名は「業務データの一覧と詳細の画面を足す（Intent 261016-business-data-views、B5）」の形。統合を終えたブランチは消す。
  - push は依頼者が行う。B6 に入る前に、B5 の統合の後の CI の結果を試行ごとに確かめる。

### B6（作成・更新・削除）

#### 準備と見本の PG

- [x] **Step 21: 作業の準備と走らせ方の確かめ（B6）**
  - Step 1 と同じ確かめ（`aidlc/` の未コミットの変更、生成物、B5 の統合の後の CI）。`develop` から `feature/261016-business-data-views-b6` を作る。
  - 走らせ方の確かめ: `unit-test-instructions.md` の 3.1 の B6 のコマンドで、移す roleadmin のテスト・作業ロールのテスト・B5 の業務データのテストが通ることを確かめ、件数を記録する。
  - 変更の前の入口と chunk の gzip を記録する（P-6）。
- [x] **Step 22: 上乗せの compose と、開けた直後の確かめ（D1 が A のとき。A4）**
  - `docker/e2e/compose.targetdb-port.yaml` を書く。開ける前に依頼者に一言伝える。
  - 上乗せを付けて見本の PG を作り直し、すぐに誤ったパスワードで `127.0.0.1:15432` に入れず拒まれることを確かめる（4.4 の R-02。見本の PG と同じイメージ・digest の `docker run --rm` の一時の psql か、ホストの psql。値は表示しない）。拒まれなければ（`trust` に当たれば）、すぐに上乗せ無しで作り直して閉じ、生成を止めて Q1 を諮り直す。
  - 配備したアプリを動かしたまま作り直し、作り直しの後に配備したアプリのヘルスと業務データの一覧の API が戻ることを確かめる（4.4 の R-05。確かめられなければ未確認として記録する）。
- [x] **Step 23: 見本の PG の TEMPORARY の取り上げ（手で1回。A5）**
  - SQL が U2 の README と `docker/targetdb/postgres/03-writer-account.sh` の取り上げと同じであることを読み取りで確かめ、依頼者に見せて承認を得てから、`docker compose exec -T targetdb-postgres psql -U target_admin -d business -v ON_ERROR_STOP=1` で流す。共有の見本の PG（配備したアプリも使う）を変える操作であることを記録に書く。
  - `has_database_privilege('public','business','TEMP')` が偽になったことを確かめる（値を表示しない形で）。
- [x] **Step 24: E2E のタスクと Playwright の設定（Contract の Environment/build configuration を先に行う）**
  - 順の理由: 見本の PG を前提にする仕組みは、安全の確かめ（Step 22・23）の直後に入れ、以降の Step の E2E がこの前提で流れるようにする（4.4 の R-01・R-02）。画面の層の前に置くのは Contract の baseline の順と違うが、methodology と各層の「実装 → テスト」の順は変えない。
  - `build.gradle.kts` の `e2eTest`: 前提の確かめ（Mailpit、見本の PG の (a) `docker compose --profile targetdb-postgres exec -T targetdb-postgres pg_isready`・(b) `127.0.0.1:${E2E_TARGETDB_PORT:-15432}` に TCP で届くか・(c) PUBLIC と作る書き込みのロールに TEMPORARY が無いこと・(d) 誤ったパスワードで拒まれること）、前の残りの片付け（接頭辞の LIKE、`pg_terminate_backend` は接頭辞のロールの接続だけ）、準備（実行の印、`e2e_biz_<UTC の年月日時分秒>_<16進 8 文字>`、`businessDataSchema.sql`、2つのロール（`VALID UNTIL` 2 時間、LOGIN、乱数のパスワード）、付与。`-v ON_ERROR_STOP=1`・`-1`、値は標準入力の `\set`）、値の受け渡し（`Exec` の environment。`webServer.env` に置かない）、`-Pe2e.files`、`finalizedBy` の片付けのタスク（接続を切る → `DROP OWNED BY` → `DROP ROLE` → `DROP SCHEMA … CASCADE`、消した件数だけをログに出す）。名前は Gradle の同じビルドの中で渡し、パスワードは片付けに渡さない（R-04）。psql の誤りの出力の扱いは D8。
  - `frontend/e2e/support/businessDataSchema.sql`（表1つ・架空の行 21 件以上）。
  - `playwright.config.ts`: `SECRET_ENV_NAMES` に4つの名前を足す。対象DB の環境変数が無いときは設定の読み込みで止める（Q7: A）。接続先の値（`127.0.0.1:15432`）を探す対象に入れるかは、D8 と同じ記録に書く（入れる見込み）。
  - 確かめ（この Step の中で流す）: 前提の (a)〜(d) をそれぞれ満たさない形で `./gradlew e2eTest -Pe2e.files=e2e/010-skeleton.e2e.ts` を流し、起動の手順を示して止まること、満たした形で通り片付けの後に `e2e_biz_*` と `mastersmith_e2e_*` が 0 件になること。
- [x] **Step 25: 040 の書き換え**
  - 照合の期待を `dsl-warning-group-SCHEMA_MISMATCH` に替え、21行の説明を直し、PG の読み取りが失敗したときの揺れの1行を足す（4.4 の R-07）。`./gradlew e2eTest -Pe2e.files=e2e/040-dsl-admin.e2e.ts` で通ることを確かめる。

#### 書き込みの型・API・結果の分け方（Contract の Data model と Repository / data access）

- [x] **Step 26: 書き込みの写し・API・結果の分け方を書く**
  - 写しの関数に、入力にするカラムの `formPart`・`nullable`・`required` の欠けと `lookup`・知らない値の形の崩れを足す（4.1）。`parseWriteResult`（作成の `key` の有無）。
  - `businessDataApi.ts` に `createRow`・`updateRow`・`deleteRow`（4,194,304・65,536 バイトの上限。`definitionId` を必ず送る）。
  - `writeOutcome.ts`: 書き込みの結果を「保存した」「保存されていない（code と文）」「分からない」に分ける純粋な関数（4.3 の R-01・R-03 の表。今の `apiRequest` の `networkError()` は、書き込みの呼び出しだけ「分からない」に分ける）。
- [x] **Step 27: 書き込みの写し・API・結果の分け方のテストを書いて流す**
  - 例のテスト（各 code・状態コード・`content-type`・本文なしの 2xx・HTML の 200・502・504・code の無い 503・知らない code）と性質ベースのテスト（任意の応答で、既知の code が読めないかぎり「保存されていない」にならない）。

#### 純粋な関数（Contract の Business logic）

- [x] **Step 28: 変更の比べ・画面の検証・書き込みの文を書く**
  - `formDiff.ts`（D20。初めの値と今の値の比べ、作成では空のカラムを送らない、更新で空にした文字の型は `""`・ほかは `null`、`masked` は入れたときだけ、真偽値の NULL は変えなければ送らない、`DATETIME_TZ` は送るときに `+00:00`、日時は秒まで）、`formValidation.ts`（D21。必須・長さ（コードポイント）・桁・範囲・型・選択肢、10,000 字と DSL の短い方。`pattern` は画面で当てない）、`problemMessage.ts` の書き込みの code（4.6 の表、`field` が空の誤りはフォームの上、413、D3 が A なら `tables.problem.writeForbidden`）。
- [x] **Step 29: 純粋な関数のテストを書いて流す**
  - 例のテストと性質ベースのテスト: 変えないカラムは本文に入らない、BIGINT・DECIMAL の桁を失わない、上限ちょうどは通り +1 字は誤り（サロゲートの組を含む）、`field` が空の誤りで `needsDefinitionReload` が偽。失敗のときの乱数の種を記録する。

#### 共通の部品と骨組みの口（Contract の Frontend behavior の shared と骨組み）

- [x] **Step 30: 未保存の確かめの移し替えと、作業ロールの確かめの口を書く**
  - `src/shared/unsaved-guard/useUnsavedGuard.ts`・`LeaveConfirmDialog.tsx`（roleadmin から移す。文言は props、CSS は同じ場所）。足すもの: `dirtyRef`（判定の関数は ref を読む）、`markClean()`（「変更なし」にし、`beforeunload` を外し、次の移動1回だけ確かめを飛ばす）、任意の引数の入力の版の数、旗を下ろす3つの条件、副作用の無い判定の関数、`markClean()` の後の入力の変更で `beforeunload` を付け直す（4.3 の R-08、U3-NFR7.11）。roleadmin の `PermissionPanel.tsx`・`testing/renderRoleAdmin.tsx` の import と文言の渡し方を変える（A3）。
  - `app/work-role/WorkRoleProvider.tsx`・`WorkRoleSwitcher.tsx` に `registerSwitchGuard`（登録は1つ、後の登録が前を置き換える、外す関数を返す、`switchTo` の前に呼ぶ、拒まれたら `'ignored'` でトリガーへフォーカスを戻す、既定の値にも足す）。
- [x] **Step 31: 共通の部品と骨組みのテストを書いて流す**
  - 移した `useUnsavedGuard.test.tsx`・`LeaveConfirmDialog.test.tsx`（roleadmin の今の振る舞いのまま通る）と足すテスト: `markClean()` の後の道の移動が止まらない、その後に変更を作ると次の移動が止まる、呼ばなければ今どおり止まる、`markClean()` で `beforeunload` が外れ入力の変更で戻る、道の変わらない移動の後に変更を作ると次の移動が止まる、実物の data router で判定が何回呼ばれても旗が1回の移動で尽きない。`PermissionPanel.test.tsx`・`WorkRoleProvider.test.tsx`・`WorkRoleSwitcher.test.tsx`（登録の有無・留まる・離れる）・`BusinessNavigationProvider.test.tsx` を流す。

#### フック・画面（Contract の Frontend behavior）

- [x] **Step 32: 書き込みのフックと、作成・更新・削除の画面を書く**
  - `useRowWrite`（`create`・`update`・`remove`・`saving`・`slow`。`useSlowNotice` の 5 秒）、`BusinessDataFormPage`（D19〜D25a。`formPart` ごとの部品、BIGINT・DECIMAL は文字の入力、`aria-required` を自分で付ける、誤りのまとめ（`role="alert"`）へフォーカス、入力は消さない、入力に `autocomplete="off"`（password は `new-password`）、保存の成功で `markClean()` → 移動 → Toast、作業ロールの確かめの登録と外し、「分からない」の後の画面は D4、作業ロールを切り替えた後は D2、書き込みの 403 は D3、F6 で入力できなくなったカラムの変更を捨てて促す）、`DeleteConfirmModal`（`role="alertdialog"`・`closeOnBackdropClick={false}`・はじめのフォーカスは「やめる」、見出しは行のラベル、失敗は Modal の中）、`RowActionsMenu` と詳細の画面に「更新」「削除」（`operations` に従う）、`registration.ts` に `/tables/new`・`/tables/edit`（同じ `lazy` のフォームの画面）、`messages.ts` の足し（日英）。
- [x] **Step 33: 書き込みのフックと画面のテストを書いて流す**
  - 部品ごとに 5〜8 件と vitest-axe 1件。必須の項目: 作成と更新の保存の成功から詳細への移動で未保存の確かめの Modal が出ない（D25）、`key` の無い作成で一覧へ戻る、4.6 の code ごとの文と行き先（`field` が空の誤り・`NOT_EDITABLE_NOT_NULL`・413・409 の文）、「分からない」の後の画面（D4）、`operations` に無いボタンが出ない、値の `<script>` などが入力・読み取りの欄で文字として出る、`aria-required`・`aria-invalid`・`aria-describedby`、上限 +1 字・+1 バイトで送らない、作業ロールの切り替えの前の確かめ（留まる・離れる）と切り替えの後の画面（D2）、書き込みの 403（D3）、F6 の後の入力。

#### E2E・検査・記録・統合

- [x] **Step 34: 180 と 185 の残り**
  - 180: 本物の見本の PG の実行のスキーマ（`E2E_BIZ_SCHEMA`）で、DSL を組んで API で適用し、流れの中で利用者とロールを作り（初期管理者の状態は変えない）、一覧 → 作成 → 詳細 → 更新 → 削除をキーボードの操作を含めて流す（U3-NFR5.10）。保存の後に未保存の確かめが出ないこと、ページ送り（21 件以上）を確かめる。見本の形（`businessDataFixtures`）を本物の応答と `shapeCompare` で比べる。180 が作る値は目印を付けて値のファイルに書く（`businessValue`）。
  - 185: 作成の誤りのまとめ・更新・削除の確かめの Modal・離れる確かめの Modal・「保存されたか分かりません」の知らせを各件の状態に足す。測りの件に `formShown100` を足す。
  - `README.md` の E2E の節（3.1 の起動の手順、`.env.targetdb` が要ること、実行のスキーマと一時のロール、`-Pe2e.files`、片付けの件数、E2E の後に上乗せ無しで作り直して閉じる、前の残りを手で消す）と、TEMPORARY の取り上げの手順。
- [x] **Step 35: 1コマンドの検査とカバレッジ・時間の記録（B6）**
  - Step 15 と同じ。入口と3つの画面の chunk の gzip を記録する（P-6）。`no-console` の検索も同じ。
- [x] **Step 36: E2E の全件と B6 で確かめること**
  - Mailpit と、上乗せで起動した見本の PG の前提で `./gradlew e2eTest` を流す。記録: 件数・時間・ファイルごとの時間（180・185 を分けて）・準備と片付けの所要・秘密の確かめの件数 0。
  - 確かめ: 040・150・170・180 が通る、片付けの後に `e2e_biz_*` と `mastersmith_e2e_*` が 0 件（E2E を途中で失敗させた回（`-Pe2e.files` で1件だけわざと落とす）も含める）、(d) の誤ったパスワードの拒否、見本の PG の `docker stats --no-stream` の値。
  - E2E の全体が基準の 1.5 倍を超えたら、185 の組み立てを依頼者に諮る。
  - 流し終えたら、上乗せ無しで見本の PG を作り直してポートを閉じる（README の手順。4.4 の R-02）。
- [x] **Step 37: axe-core 4.14 の試し（B6、統合しない）**
  - Step 17 と同じ。B6 の新しい画面（作成・更新・削除・離れる確かめ）の区分の結果を足して記録する。
- [x] **Step 38: 記録とコードのレビュー（B6）**
  - Step 18 と同じ（B6 の節）。
- [ ] **Step 39: コミットの提案と積み（B6、依頼者の承認）**
  - 7節の B6 の区切りで、Step 19 と同じ形。
- [ ] **Step 40: 統合（B6、依頼者の承認の後）**
  - Step 20 と同じ形で squash。件名は「業務データの作成・更新・削除の画面を足す（Intent 261016-business-data-views、B6）」の形。B7 に入る前に CI を試行ごとに確かめる。

### B7（U4 の後半: axe-core の引き上げ）

- [x] **Step 41: 作業の準備と走らせ方の確かめ（B7）**
  - Step 1 と同じ確かめ。`develop` から `feature/261016-business-data-views-b7` を作る。
  - 走らせ方の確かめ: `unit-test-instructions.md` の 3.1 の B7 のコマンド（`e2e/support/axeRuleChecks.test.ts`・`axeElementKey.test.ts`、置き換える共通の部品のテスト）を流し、件数を記録する。
- [x] **Step 42: 依頼（5）の上流の対応の確かめ（A8）**
  - 依頼者の承認を得て `vendor/make-you-chic-ui` で `git fetch` し、上流の `main` の新しいコミットで、表の横のスクロールの領域と1ページの件数の選択の口の形を、読み取りだけの `git show` で確かめる（報告ではなくソースで。team.md）。
  - 間に合っていなければ Step 43・44 を行わず、`src/shared/` の手当てのまま残し、置き換えを次の Intent に回したことを記録する（U4 の D11・AC4.1.5）。axe-core の引き上げは止めない。
- [ ] **Step 43: 固定先の2回目の更新（間に合ったとき。A8）**
  - 新しいコミットが上流の `main` から辿れること、`vendor/make-you-chic-ui/package-lock.json` の前後の差（増えた・変わったパッケージ、`hasInstallScript` の増減）を確かめ、前後のハッシュと同じ記録に書く。`hasInstallScript` が増えていたら取り込む前に諮る。依頼者の承認を得て、固定先の更新だけの専用のコミットにする。
  - `./gradlew vendorInstall vendorBuild frontendInstall` の後に、Dropdown・Table・Modal を使う既存の部品のテストを書き換えずに流して通ることを確かめる。
- [ ] **Step 44: 手当ての置き換え（間に合ったとき）**
  - `BusinessDataTable` の横のスクロールの枠と件数の選択を make-you-chic-ui の口に置き換え、要らなくなった `src/shared/scroll-region`・`page-size` とそのテストを消す（ほかに使う所が無いことを検索で確かめる）。置き換えた部品のテストを書いて流す（`aria-label`・フォーカス・件数を変えたら1ページ目、vitest-axe）。
- [x] **Step 45: axe-core の版の確定と取り込み（A9）**
  - その時点の 4 系の最新（4.14 以上）を採る。CHANGELOG で既定で流れるようになった規則、`license`（MPL-2.0 のままか）、`hasInstallScript` を確かめて依頼者に見せ、承認を得てから `frontend/.npmrc` の `ignore-scripts=true` のまま入れる。`package.json` の範囲は `^<採った版>`。`npm ls axe-core` で版が1つ（vitest-axe の分を含む）。`vendor/make-you-chic-ui/package-lock.json` の axe-core は版を記録するだけ。
  - `.github/dependabot.yml` の axe-core の `ignore` と 65〜70行のコメントを外し、受け方のコメント（U4 の基盤の設計 6節の形。Way of Working との差は「lockfile だけの更新の例外、根拠は Testing Posture の axe-core の決まり」）を書く。
- [x] **Step 46: `REQUIRED_RULES` と passes の確かめを E2E に足す（A10）**
  - `REQUIRED_RULES` に `label-content-name-mismatch` を足す。区分ごとの passes の確かめ（U4 の L5 `labelContentNameProblems`。passes に1件以上、incomplete と violations に 0 件、対象が 0 件なら失敗）を、D6 の答えの対象と場面で既存の E2E と 185 に足す。C4 は 130 の2語の氏名の場面だけ（4.5 の R-01）。U3 の行の操作（`tables-row-actions-*`）と行のリンク（`tables-row-link-*`）は、B5・B6 の試しで要素が見つかったものを対象にする。足す確かめは適用中の DSL に頼らない（1.4）。
  - 既存の axe の検査の結果をもう一度読むだけにし、新しく画面を開き直さない（U4-NFR の性能の決まり）。場面を足すときだけ、その場面を開く。
- [x] **Step 47: 単体のテストと vitest-axe**
  - 変えた `e2e/support/` のテストと、新しい axe-core の下の画面の部品のテスト（vitest-axe）を流し、件数と違反 0 件を記録する。
- [x] **Step 48: 1コマンドの検査（B7）**
  - Step 15 と同じ。OSV-Scanner の axe-core の結果（devDependency の High は警告、`MAL-` は止める）を記録する。
- [x] **Step 49: E2E の全件と件数の記録（B7）**
  - Mailpit と上乗せで起動した見本の PG の前提で `./gradlew e2eTest` を流す。記録: E2E の全件の通過、違反 0 件、区分ごとの passes の件数（incomplete・violations は 0）、vitest-axe の件数、`npm ls axe-core` の結果、E2E の時間（U4-NFR7.2。B3・B5・B6 の試しの時間と並べる）。
  - `npm run build` の後に `frontend/dist` を `axe-core`・`axe.run` などで検索して 0 件（U4-NFR4.8）。
  - 流し終えたら、上乗せ無しで見本の PG を作り直してポートを閉じる。
- [x] **Step 50: 記録とコードのレビュー（B7。単位の最後）**
  - `code-summary.md` の B7 の節と単位の全体のまとめ、`traceability.json`（U3 の機能設計・NFR 要件の ID と、U4 から Deferred で受けた AC4.1.3・AC4.1.5・AC4.1.6・U4-NFR4.8・U4-NFR5.1〜5.3。画面の時間の目標 U3-NFR1.7 と U4-NFR7.2・7.3 は Build and Test への Deferred）、`source-manifest.json`（B5〜B7 のすべてのパス）、`generation-notes.md`。
  - 指揮役がコードのレビューを依頼する（Step 18 と同じ伝え方）。
- [ ] **Step 51: コミットの提案と積み（B7、依頼者の承認）**
  - 7節の B7 の区切りで、Step 19 と同じ形。コード生成の段の承認の場は、この最後のコードのコミットの後、`aidlc/` の下だけの記録のコミットを積む前に開く（`project.md` の学び）。
- [ ] **Step 52: 統合（B7、依頼者の承認の後）**
  - 固定先を上げたときは、短命のブランチから `develop` へ fast-forward（固定先の更新の専用のコミットを残す。team.md の例外）。上げなかったときは squash で1コミット。どちらも統合の前に Step 48・49 を通す。件名は「axe-core を上げ、行の操作とトップバーの名前を確かめる（Intent 261016-business-data-views、B7）」の形。

## 6. ストーリーから Step への対応

| ストーリー（U3 の受け持ち。画面の側） | 主な受け入れ基準 | Bolt | Step |
|---|---|---|---|
| US1.1 メニューからテーブルの一覧を開く | AC1.1.1〜AC1.1.9 | B5 | 2〜5・8〜12・14 |
| US1.2 条件で行を絞り込む | AC1.2.1〜AC1.2.3・AC1.2.5〜AC1.2.13 | B5 | 2・3・6・7・10〜12・14 |
| US1.3 ページを送り、並べ替える | AC1.3.1〜AC1.3.3・AC1.3.12〜AC1.3.15 | B5 | 6〜12・13・14 |
| US1.4 行の詳細を見る | AC1.4.1〜AC1.4.7 | B5 | 4〜7・10〜12・14 |
| US1.5 値を読みやすい形で見る | AC1.5.1〜AC1.5.8 | B5 | 6・7・11・12 |
| US2.1 行を作成する | AC2.1.1〜AC2.1.6・AC2.1.8〜AC2.1.10 | B6 | 26〜29・32〜34 |
| US2.2 行を更新する | AC2.2.1・AC2.2.2・AC2.2.4〜AC2.2.13 | B6 | 26〜33・34 |
| US2.3 行を削除する | AC2.3.1〜AC2.3.5 | B6 | 26・27・32〜34 |
| US2.4 入力の誤りをその場で直す | AC2.4.1・AC2.4.2・AC2.4.5〜AC2.4.8 | B6 | 28・29・32・33 |
| US3.1（画面の側） | AC3.1.1・AC3.1.8・AC3.1.9 | B5・B6 | 11・12・32・33 |
| US3.2（画面の側） | AC3.2.1〜AC3.2.3・AC3.2.5・AC3.2.6 | B5・B6 | 11・12・32・33 |
| US3.3（画面の側） | AC3.3.4・AC3.3.5・AC3.3.7〜AC3.3.9 | B5・B6 | 6・7・11・12・28・32・33 |
| US3.6（画面の側） | AC3.6.5 | B5 | 6・7 |
| US4.1（U3 の画面の分） | AC4.1.1・AC4.1.6 | B5・B6 | 11・12・14・17・34・37 |
| US4.1（U4 から受けた B7 の分） | AC4.1.3・AC4.1.5（U4）、U4-NFR4.8・U4-NFR5.1〜5.3 | B7 | 42〜49 |
| 横断（NFR 要件・NFR 設計・基盤の設計の作り） | U3-NFR1.7〜1.10・2.10・2.11・3.7・4.2・4.20〜4.26・5.6〜5.10・6.3・6.4・7.8〜7.13・8.7 | B5〜B7 | 4・5・13〜17・22〜25・34〜37・49 |

U3 で受け持たない受け入れ基準（機能設計の `traceability.json` の Deferred 37 件。サーバーの判定は U1・U2、U4 の B3 の分は U4）は、そのまま Deferred で残す。U4 の traceability が U3 へ Deferred にした ID は、B7 の Step 50 で OK にする（測りの目標 U4-NFR7.2・7.3 は Build and Test への Deferred のまま）。

## 7. コミットの区切り

場所・機能ごとに区切り、どのコミットの時点でも型の検査（`npm run typecheck`。`tsconfig.json` は `src` と `e2e` を含む）と lint が通る順にする（`project.md` の学び）。import の向き（`features/tables` → `app/`・`shared/`、`e2e/support` → `src` の `import type`）から、使われる側を先に積む。区切りの中身が生成の結果で変わるときは、提案のときに理由を添えて示す。メッセージは日本語で、末尾に共著者の行を付ける。

### B5

| # | 中身 | 型の検査が通る理由 |
|---|---|---|
| C1 | 骨組み: `app/navigation/`（4つの道・`isTablePath`・今の項目）と `app/login-state/`（知らせ）、そのテストの書き換えと足し | 今の置き場の画面が使う口（`toTablePlaceholderPath`・`readTablePlaceholderParams`・`TABLE_PLACEHOLDER_PATH`）の形を保つため、置き場の画面はこの時点でも動く |
| C2 | `src/shared/` の3つの部品とそのテスト | どこからも使われない新しいファイルだけ |
| C3 | `features/tables/` の新しい機能（型・写し・API・記憶・純粋な関数・フック・画面・文言・登録・CSS）とそのテスト、置き場の6ファイルの削除、`e2e/support/navTreeFixtures.ts` の型の import の削除、`features/README.md`、lint の設定（D10 が A のとき） | 置き場のファイルを読む `navTreeFixtures.ts` を同じコミットで直す |
| C4 | E2E: `e2e/support/` の補助（見本・差し替え・測り・`secretValues.ts`）とその単体のテスト、`frameApiRoute.ts`・`u7Measure.ts`、160・170 の書き換え、185、`README.md` の E2E の表と「テーブルの置き場」の段落 | C3 の型を `import type` で読む |

### B6

| # | 中身 | 型の検査が通る理由 |
|---|---|---|
| C1 | `src/shared/unsaved-guard/` への移し替えと口の足し、roleadmin の import と文言の渡し方、移したテストと足したテスト | 移す側と使う側を同じコミットにする |
| C2 | `app/work-role/` の `registerSwitchGuard` とそのテスト | 口を足すだけで、使う側は C3 |
| C3 | `features/tables/` の書き込みの側（写し・API・結果の分け方・純粋な関数・フック・フォームと削除の画面・行の操作の足し・文言・登録）とそのテスト | C1・C2 の口を使う |
| C4 | E2E の環境と E2E: `docker/e2e/compose.targetdb-port.yaml`、`build.gradle.kts` の `e2eTest`、`businessDataSchema.sql`、`playwright.config.ts`、`secretValues.ts` の値のファイル、040 の書き換え、180、185 の足し、`README.md` の E2E の節と TEMPORARY の手順 | 画面のコードに依存しない（E2E の補助は `import type` だけ） |

### B7

| # | 中身 | 型の検査が通る理由 |
|---|---|---|
| C1 | （固定先を上げるとき）`vendor/make-you-chic-ui` の固定先の更新だけの専用のコミット | 中身は変えない。前後のハッシュを本文に書く |
| C2 | （固定先を上げるとき）`src/shared/` の手当ての置き換えと削除、そのテスト | C1 の口を使う |
| C3 | axe-core の版（`frontend/package.json`・`frontend/package-lock.json`）と `.github/dependabot.yml` | 版の範囲と lockfile だけ |
| C4 | `e2e/support/axe.ts` の `REQUIRED_RULES` と、E2E の passes の確かめと足した場面 | C3 の版で規則が流れる |

## 8. 統合

| 項目 | B5 | B6 | B7 |
|---|---|---|---|
| ブランチ | `feature/261016-business-data-views-b5` | `feature/261016-business-data-views-b6` | `feature/261016-business-data-views-b7` |
| 作る元 | B4 の統合の後の `develop` | B5 の統合の後の `develop` | B6 の統合の後の `develop` |
| 統合の形 | squash で1コミット | squash で1コミット | 固定先を上げたら fast-forward（team.md の例外。C1〜C4 がそのまま `develop` に並ぶ）、上げなければ squash で1コミット |
| 統合の前の関門 | `:backend:cleanTest :backend:cleanIntegrationTest verify` と E2E の全件（`frontend/src` に触れるため。team.md） | 同じ（E2E は見本の PG を上乗せで起動した前提） | 同じ。加えて E2E と vitest-axe の違反 0 件と件数、`npm ls axe-core` が1つ、`dist` に axe-core が無いこと |
| 統合の後 | ブランチを消す。push は依頼者。次の Bolt の前に CI を試行ごとに確かめ、失敗は team.md の決まりで扱う | 同じ | 同じ。CI の結果は Build and Test で試行ごとに記録する |

- worktree は使わない（team.md）。どの Bolt でも、統合の前に `aidlc/` の未コミットの変更をコミットしておく。
- 単位ごとの squash の選び方（複数の単位を持つ Bolt の例外）は当たらない（どの Bolt も U3 の計画の1つの Bolt）。

## 9. 後の段への引き継ぎ

- **Build and Test**
  - E2E の全件（180・185 を含む）の通過と時間、ファイルごとの時間（180・185 を分けて）、準備と片付けの所要、基準（232 件・約 10分35秒）との比べ（U3-NFR7.8・U4-NFR7.2）。見本の PG を上乗せで起動する手順を全件の手順に入れる。
  - 画面の時間（U3-NFR1.7・1.8）: 185 の測りの5つの判定の中央値・最大・1回目と、参考の「塗りまで」を並べ、判定を書く（「塗りまで」は報告だけ）。
  - axe の件数（E2E と vitest-axe の違反 0 件、区分ごとの passes）と `npm ls axe-core`。
  - `./gradlew verify` と CI の時間（B5・B6・B7 の後の値と並べる。U3-NFR7.8・U4-NFR7.3）。CI は試行ごとに確かめ、再実行で通ったものは1回目の失敗とそのテストも書く（`project.md` の学び）。
  - `src/features/tables/` のカバレッジの値（team.md）。
  - 見本の PG の `docker stats` の値、片付けの後の0件、(d) の確かめ。
- **Performance Validation**: U3 は k6 の場面を足さない。API の応答時間は U1・U2 の場面が受け持つ（U3-NFR1 の API の分）。
- **Observability Setup**: 業務データの API の 403 を既存の警報（`ms-forbidden`）が拾うかの実測（U3-NFR8.7。画面は 403 の後に送り直さない）。
- **Deployment Pipeline・Deployment Execution**
  - 見本の PG の TEMPORARY は、B6 の Step 23 で手で取り上げ済み（U2 の Deployment Execution の予定の手順のうち、この PG の分は済んだことになる。済んだ日時と確かめの結果を記録に残し、配備の段で重ねて流しても同じ結果になることを書く）。
  - 上乗せの compose は配備には使わない。配備した見本の PG は上乗せ無しの形（ポートを開けない）で動いていることを、配備の前に確かめる。
  - スモークテストで業務データの作成・更新・削除を行うと、見本の PG の行と監査に残ることを依頼者に先に伝える（`project.md` の学び）。
  - 戻しはイメージだけ（U3 は内部DB の移行を足さない）。
- **後の Intent**: サーバーの `GET /api/me/table-access` とそのテストを消すこと（G5）。依頼（5）が B7 までに間に合わなかったときの置き換え（U4 の D11）。

## 10. 依頼者の決定（D1〜D10: A）

依頼者は D1〜D10 のすべてに推奨の A で答えた（`code-generation-questions.md`）。本文の「D<n> が A のとき」「D<n> が A なら」は、決まった形として読む（B のときの書き分けは使わない）。以下は尋ねたときの選択肢の記録。

計画の承認の前に、判断が分かれる 10 点を尋ねる。どれも A を推奨とし、本文は A の形で書いた。A 以外になったら、該当の Step・3節・4節・7節を直してから計画の承認に進む。

### D1. 見本の PG を前提にする E2E の仕組みを入れる Bolt（基盤の設計のレビュー R-01・R-02）

上乗せの compose（`127.0.0.1:15432`）・準備と片付けのタスク・TEMPORARY の確かめ (c)・040 の書き換えは、見本の PG の PUBLIC の TEMPORARY を手で取り上げてからでないと (c) で必ず止まります。どの Bolt に入れるかで、取り上げの時期と、ポートを開けて安全を確かめる時期が決まります。

A. すべて B6 に入れる。B6 の最初に上乗せで開けて誤ったパスワードが拒まれることを確かめ（拒まれなければすぐ閉じて諮る）、次に手で REVOKE し、その後で準備のタスク（(a)〜(d)）・040 の書き換え・180 を入れる。B5 の統合の前の E2E は今の前提（対象DB の接続先が無い）のまま流す。170・185 は差し替えのため見本の PG が要らない（推奨）

B. すべて B5 に入れる。REVOKE と安全の確かめを B5 の最初に移し、B5 の統合の前の E2E から見本の PG を前提にする（180 は B6）

C. 上乗せと 040 の書き換えだけを B5 に入れ、(c)・一時のロールの作成・準備のタスクは B6 から有効にする（B5 の間はポートを開けても使わない）

X. Other (please specify)

### D2. 作業ロールを切り替えた後の画面（機能設計のレビュー R-04）

NFR 設計の Q2: A で、作業ロールの roleId が変わると記憶（主キーの印・一覧の状態・定義）を全部消します。詳細・更新の画面は印が無くなるため、そのままでは続けられません。

A. 切り替えが済んだら、詳細・作成・更新の画面は同じテーブルの一覧へ置き換えて移る。一覧は既定の状態から、新しい作業ロールの定義を読み直す。一覧の画面にいるときは、既定の状態で読み直す。roleId が変わらない `revision` の変化（拒否・同じロール）では読み直さない。メニューから入り直したときは、記憶にある一覧の状態を使う（D3 のまま）（推奨）

B. 作成・更新の画面はその場に留まり、定義を読み直して、今も入力できるカラムの入力だけを残す（F6 と同じ扱い）。詳細の画面は一覧へ移る

X. Other (please specify)

### D3. 書き込みの 403 の文（機能設計のレビュー R-06・U3-NFR6.4）

保存や削除の 403 に、読み取りと同じ「このテーブルを見る権限がありません」を出すと、見ることはできる人には事実と違う文になりえます。

A. 鍵 `tables.problem.writeForbidden` を足し、「権限が変わったため、保存できませんでした。作業ロールを確かめてください。」（削除は「削除できませんでした」。英語も用意）を出す。行き先は F8 と同じ（メニューから選び直す・作業ロールを切り替える）。入力は捨てる（RQ5: A）。AC2.2.13 の確かめに入れる（推奨）

B. 読み取りと同じ 403 の文のまま

X. Other (please specify)

### D4. 「保存されたか分かりません」の後の画面（NFR 設計のレビュー R-02）

送った後に応答が届かない・確定の段の時間切れ（503 `BUSINESS_TARGET_DB_TIMEOUT`）・code の読めない 5xx では、保存されたかが分かりません。そのまま保存を押し直すと、作成では同じ行が2つできることがあります（更新は同じ値の書き直し、削除は 404 になるだけ）。

A. 入力は残す。作成・更新・削除とも、もう一度押すと「保存済みかもしれません」の確かめの Modal（はじめのフォーカスは「やめる」）を出してから送る。作成の Modal の文には「同じ行が2つできることがあります」と書く。あわせて確かめる入口（作成は一覧で同じ行を探す、更新はその行を開き直す、削除は一覧を読み直す）を並べる（推奨）

B. 作成だけは保存を押せなくし、確かめる入口だけを出す（入力は未保存の確かめで守る。入力を変えると押せるように戻す）。更新・削除は A と同じ

C. どれも押せるまま、確かめの Modal は出さない（入口だけを並べる）

X. Other (please specify)

### D5. 測りの見本の生成の値を、報告の json の探索に入れるか（U4 のコードのレビュー R-03・N-9）

U4 の測り（`measurementListPage`）の見本の行は `測定 NN`・`sokutei.NN@example.com` を番号つきで作り、行の操作の名前に入ります。架空の値で個人に関する値ではありませんが、今は報告の探索の対象に入っていません。U3 の 185 の見本の目印の値は対象に入れます。

A. 入れる。見本を作る関数から同じ値の一覧を作り、`SECRET_FIXED_VALUES` に足す（U3 の 185 の目印と同じ扱いにそろえる）（推奨）

B. 入れない。架空の値で漏れても害がないことを理由に書いて残す

X. Other (please specify)

### D6. B7 の passes の確かめの対象と場面（U4 の Q-2・コードのレビュー R-01）

B3 の 4.14 の試しでは、C2（グループの行の操作のトリガー）と C7（トップバーの項目）は axe を流す場面が今の E2E に無く、要素が見つかりませんでした。対象が 0 件の区分を必須にすると、確かめは必ず落ちます。

A. C1・C3・C4・C5・C6 と、B5・B6 の試しで要素が見つかった U3 の行の操作・行のリンクを必須にする。C4 は 130 の2語の氏名の場面だけで確かめる。C2・C7 は、既存の axe の E2E に場面を足して対象に入れる（C2 は行のあるグループの一覧を開いた場面、C7 はトップバーの利用者のメニューを開いた場面。足すファイルは生成の前に検索で決め、本数に数えない書き換えにする）（推奨）

B. 同じく必須にするが、C2・C7 は場面を足さず、理由（場面が無い）を記録して対象から外す

X. Other (please specify)

### D7. U3 の測りで目標を超えたときに、B5 の中で手当てを選ぶか

NFR 設計の試し（T1）は 100 × 100 で中央値 46 ms でしたが、本物の画面（書式・行のラベル・行の操作・NULL の印）では確かめていません。B5 の Step 13 で先に測ります。

A. 目標を超えたら生成を止めて依頼者に諮り、「重ねたときに作る Tooltip」か「仮想化」かを決めてから B5 を続ける（F2: A のとおり。目標は緩めない）（推奨）

B. 超えても B5 は今の形のまま統合し、手当ては Build and Test の判定の後に決める

X. Other (please specify)

### D8. 一時のロールのパスワードが psql の誤りの出力に出うること（基盤の設計のレビュー R-03）

`CREATE ROLE … PASSWORD` が失敗すると、psql の誤りの表示と、見本の PG のサーバーのログ（`docker logs`）に、失敗した文（平文の一時のパスワードを含む）が出ることがあります。ロールは 2 時間で失効する使い捨てです。

A. `-v ON_ERROR_STOP=1` を付け、psql の標準エラー出力を Gradle のログに流さず、失敗の件数・段の名前・SQLState だけを出す。サーバーの `docker logs` に残りうることは、2 時間で失効する使い捨ての値として既知の制約に書く（推奨）

B. A に加えて、Gradle の中で SCRAM-SHA-256 のハッシュを作って `PASSWORD` に渡し、平文を SQL に載せない（JDK の標準の暗号で作り、新しい依存は足さない。作り方の確かめが増える）

X. Other (please specify)

### D9. 別のタブで利用者が替わる場合の記憶（NFR 設計のレビュー R-05）

同じタブで別の利用者がログインするときは、必ず未ログインを通るため記憶は消えます。別のタブで別の利用者がログインし、このタブのトークンの更新がその利用者に替わる場合は、ログイン状態が切り替わらず、前の利用者の検索の条件と主キーの印が残ります（サーバーは要求のたびに判定します）。

A. 既知の制約のまま残す。理由は費用（`LoginState` に利用者を見分ける値を足すと、骨組みの型と、今の購読のすべてに響く）と書く（推奨）

B. `features/auth` の更新の成功で、利用者（メールアドレス）が前と変わったときにも「ログインの範囲が変わった」を知らせ、記憶を消す

X. Other (please specify)

### D10. `src/features/tables/` に `no-console` を当てるか（NFR 設計の後の段に回したこと）

主キー・行の値・検索の値を `console` に出さないことを、今は検索だけで確かめる予定です。

A. `src/features/tables/**` と U3 の `src/shared/` の部品（テストのファイルを除く）に、lint の `no-console`（error）を当てる。規則を足すだけで、ほかの場所は変えない（推奨）

B. 当てず、verify の後の検索（Step 15・35）で 0 件を確かめるだけにする

X. Other (please specify)

## Sources

- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`（Step 1〜2）
- Testing Contract: `aidlc engine testing-posture render` の出力（2節。test-after・standard・classic・brownfield）
- この単位の設計（どれも `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/` の下）: `functional-design/`（`functional-spec.md`・`frontend-components.md`・`functional-design-questions.md`）、`nfr-requirements/`（`performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`）、`nfr-design/`（`performance-design.md`・`security-design.md`・`logical-components.md`・`nfr-design-questions.md`・`nfr-design-followup-questions.md`）、`infrastructure-design/`（`infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`・`infrastructure-design-questions.md`）
- 承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/{functional-design,nfr-requirements,nfr-design,infrastructure-design,code-generation}/memory.md`
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/{functional-design,nfr-requirements,nfr-design,infrastructure-design}/units/u3-businessdata-screens/*/1.json`
- U4: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/` の `functional-design/functional-spec.md`（D9〜D12・F5・F6）、`nfr-requirements/tech-stack-decisions.md`、`nfr-design/logical-components.md`、`infrastructure-design/cicd-pipeline.md`（4〜6節・9節）、`code-generation/code-summary.md`（Q-1〜Q-5）・`generation-notes.md`（N-1〜N-10・4節）・`traceability.json`
- U2: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/code-generation/code-summary.md`・`generation-notes.md`（G1〜G17・R-01〜R-03）・`code-generation-plan.md`（D1〜D4・9節）
- U1: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/code-generation/code-summary.md`・`generation-notes.md`（G7・G15・G17〜G20）
- Inception: `aidlc/spaces/default/intents/261016-business-data-views/inception/` の `requirements-analysis/requirements.md`（FR13・FR14）、`user-stories/stories.md`（US1.1〜US4.1）、`refined-mockups/`、`units-generation/unit-of-work.md`、`contract-design/contract-summary.md`、`delivery-planning/bolt-plan.md`（B5〜B7）、`aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-5.md`
- コード（読み取りだけ）
  - `backend/src/main/java/cherry/mastersmith/businessdata/web/`（`TableDefinitionResponse`・`ColumnDefinitionResponse`・`SearchFieldResponse`・`RowPageResponse`・`RowDetailResponse`・`RowCreatedResponse`・`RowWrittenResponse`・要求の record・`BusinessDataWebConfig`）、`businessdata/domain/`（`BusinessDataProblemTypes`・`BusinessDataFieldErrorReason`・`SearchMode`・`RestrictionReason`・`SearchRequestValidator`・`WriteRequestValidator`・`ColumnWriteRulesFactory`・`TableAccessPolicy`・`PageRequest`）
  - `frontend/src/app/navigation/`・`app/work-role/`・`app/login-state/LoginStateGate.tsx`・`features/tables/`・`features/roleadmin/useUnsavedGuard.ts`・`LeaveConfirmDialog.tsx`・`shared/api-client/`・`shared/validation/codePoints.ts`
  - `frontend/e2e/`（040・130・150・160・170）・`frontend/e2e/support/`（`navTreeFixtures.ts`・`frameApiRoute.ts`・`u7Measure.ts`・`secretValues.ts`・`axe.ts`）、`frontend/playwright.config.ts`・`vitest.config.ts`・`tsconfig.json`・`eslint.config.js`・`package.json`・`scripts/check-bundle-size.mjs`
  - `build.gradle.kts`（418〜455行）、`.github/dependabot.yml`（63〜75行）、`README.md`
  - git（読み取りだけ）: `develop` の先頭 `04b0589`、`vendor/make-you-chic-ui` の `HEAD`・`origin/main` とも `b0a1e79`
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment・Code Style）、`aidlc/spaces/default/memory/project.md`（学び・Forbidden・Mandated）

## Assumptions & Open Questions

- [assumption] D1〜D10 は A の形で本文を書いた。A 以外になったら、計画の承認の前に該当の Step を直す。
- [assumption] 依頼（5）の上流の対応は、計画を書いた時点の手元の `origin/main`（`b0a1e79`）には見えていない。B7 の Step 42 で fetch して確かめるまで、置き換え（Step 43・44）を行うかは決まらない。
- [assumption] 185 の時間の見積もり（4.3 の R-10）は、B3 の記録からの粗い見積もりで、E2E の全体が基準の 1.5 倍を超える見込みが高い。B5 の Step 16 で実測してから諮る。
- 未確認: 公開の番号から来る接続が `scram-sha-256` に当たること（B6 の Step 22 で最初に確かめる）、`DROP ROLE` が接続の残りで止まるか（片付けの先頭の `pg_terminate_backend` と0件の物差しで気づく）、作り直しの間の配備したアプリの戻り（Step 22）。
- 未確認: 本物の画面での 100 × 100 の描画の時間（B5 の Step 13 で先に測る。D7）。
