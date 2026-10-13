# 生成の記録（Generation Notes）— U7 骨組みの画面（app-frame-ui）、Bolt B9

計画 `code-generation-plan.md` の手順ごとの記録。コマンドの結果は要約（件数・失敗の名前・値）だけを書く。

## Step 1: 作業の場の用意と前の確かめ（2026-10-13）

- 場所: ブランチ `feature/261004-role-menu-b9-mycui`（`develop` の 32e61e9 から。指揮役が作成。`develop` と同じコミット）。
- `git status`: `aidlc/` の外に変更なし（監査ログの追記だけ）。`frontend/playwright-report`・`frontend/test-results` は無い。
- `vendor/make-you-chic-ui` の固定先: e82b651c53ac6e048066fcc049e49386c0eb282e（記録と一致）。
- 上流の取得（依頼者の承認済み）: `git -C vendor/make-you-chic-ui fetch origin` → `origin/main` が e82b651..5bf1ffe に進んだ。e82b651 の後のコミットは 5bf1ffe（「AppShell/Sidebar: N階層のサイドバーメニュー(navSections)を追加」）の1つだけで、ほかのブランチは無い。
- **固定先にする版: 5bf1ffe**（14節 Q2: A）。`make-you-chic-ui-request-2.md` の3点（3.1 畳んだ状態の区画の `aria-labelledby`、3.2 畳んだ状態での今の項目の印、3.3 トップバーの文言を外から渡す口）を含む版は上流に無い。残る3点は差として `code-summary.md` に記録し、承認の場で伝える。
- 5bf1ffe のソース（`git show 5bf1ffe:…`、読み取りだけ）で 3.2 の M1〜M7 を確かめ直した。**すべて記録と一致**。
  - M1: `AppShellProps` に `navItems?`（旧形、`navSections` があれば使わない）・`navSections?`・`navExpandedIds?: Set<string>`・`onNavExpandedChange?`・`navLabels?: Partial<SidebarLabels>`・`user?`・`userMenuItems?`・`topbarStart?`・`topbarEnd?`。
  - M2: `SidebarNavItem {id, label, icon?, href?, onClick?, current?, children?}`・`SidebarNavSection {id, heading?, items}`・`SidebarLabels {navigationLabel, expandGroup, collapseGroup}`、既定の `navigationLabel` は「メインナビゲーション」。
  - M3: `Sidebar.tsx` 133・142・188・210 行の `sidebar-nav-${item.id}`、160 行の `sidebar-toggle-${item.id}`、旧形 254 行の `sidebar-nav-${item.href}`。`Topbar.tsx` 55 行の `sidebar-toggle`。
  - M4: `href` の項目は `aria-label`・`title`・`aria-current={current ? 'page' : undefined}`、開閉は `aria-expanded`・`aria-controls`。
  - M5: `Topbar.tsx` 54 行「サイドバーを開く／サイドバーを折り畳む」、69 行「`${user.name}のメニュー`」は日本語の固定のまま（受け入れた制約、機能設計 7節 (i)）。
  - M6: `Dropdown` は e82b651 から差なし（`dropdown-trigger`・`aria-haspopup`・項目の `disabled`・`description`）。
  - M7: `git diff --stat e82b651 5bf1ffe` は 9 ファイル、625 行の追加・26 行の削除（計画の記録と同じ）。`package.json`（ルートと `packages/make-you-chic-ui`）・`package-lock.json`・`LICENSE`・`components/Icon/` に差は無い。
- 読み取りの確かめ:
  - (1) `frontend/e2e/` の番号は 010〜150（135 を含む）で、160・170 は空いている。
  - (2) 受信箱の件数に頼る E2E は無い（`support/mailpit.ts` は宛先の完全一致の検索 `to:"…"` で探す）。
  - (3) `support/secretValues.ts` の形（`u7-perf-…@example.com`・`e2e-u7-pw-[0-9a-f]{24}`）は `support/registeredUser.ts` の作り方（`u7-perf-${runTag}@example.com`・`randomBytes(12)` の16進 24 文字）と一致。
  - (4) `backend/`・`frontend/` に ea55919 の後の変更は無く、A1〜A5 の record（`WorkRoleResponse`・`RoleRefResponse`・`NavigationResponse`・`NavNodeResponse`・`TableAccessResponse`・`MyPermissionNodesResponse.Item`）は計画の段のまま。

## Step 2: テストの実行の準備（2026-10-13 00:01）

- `npx vitest run src/app/layout src/app/routing src/app/navigation src/app/login-state src/app/registry src/features/auth`: **19 ファイル・156 件すべて通過（失敗 0）**。
- `npm run typecheck`: 失敗 0。

## Step 3: 変更の前の基準（入口の量の点 1、2026-10-13 00:01〜00:03）

`develop` と同じコミット 32e61e9（ea55919 の後の記録のコミット）の上で取った。

- `./gradlew frontendBundleSize`: 入口の合計 **152.0 KB（gzip）**（`assets/index-CYY2BuP2.js` 135.2 KB・`assets/useTranslation-Fp8M4AwN.js` 16.8 KB）。
- manifest（`frontend/dist/.vite/manifest.json`、scratchpad の一時のスクリプトで読んだ）: 入口から静的にたどった集まりは 2 つ（`index.html` → `assets/index-CYY2BuP2.js`、`_useTranslation-Fp8M4AwN.js`）。項目は 63、`isDynamicEntry` は 13。判定の鍵3つ（`TablePlaceholderPage.tsx`・`MyPermissionsPage.tsx`・`LogoutPage.tsx`）はまだ無い。
- `npm run test:coverage`: **165 ファイル・1,409 件すべて通過**。カバレッジは行 **94.88%**・分岐 **89.54%**（文 94.75%・関数 96.96%）。
- SHA-256:
  - `frontend/package.json`: `cb5cbaed22d45b25f008210723991dea236b59b46b9c9147420c2ccd35b41071`
  - `frontend/package-lock.json`: `8a83a269d5df7c36adfb4816f4952807393733a6c99ba9c77df80fb2cc0b7ddb`
  - `backend/gradle.lockfile`: `d415205433ec3a81601fe066540fcd59d1bd8cefdf08fabf4aa834f27f5475d5`
  - `vendor/make-you-chic-ui/package-lock.json`（e82b651）: `e0cf6516c27901cce37746fdbea6b1fd1225a0864229ff45a3557ce5e17d4fca`
- `pmset -g log`: 00:00〜00:03 にスリープ・DarkWake なし（電源はバッテリー、`caffeinate -di` で包んだ）。

## Step 4: 固定先の更新 — ブランチと専用の変更（2026-10-13 00:03）

- ブランチ `feature/261004-role-menu-b9-mycui` は指揮役が `develop`（32e61e9）から作成済み。
- `git -C vendor/make-you-chic-ui checkout 5bf1ffe` で固定先だけを動かした（サブモジュールの中では fetch と checkout だけ。中のファイルは書き換えていない。`git -C vendor/make-you-chic-ui status --short` は空）。
- `git diff --submodule=log -- vendor/make-you-chic-ui`: `Submodule vendor/make-you-chic-ui e82b651..5bf1ffe:` `> AppShell/Sidebar: N階層のサイドバーメニュー(navSections)を追加`。
- 前後の40桁: e82b651c53ac6e048066fcc049e49386c0eb282e → 5bf1ffe3cb7ee5c49963c3d7e8d4e8d0f032aae6。
- `git -C vendor/make-you-chic-ui diff --stat e82b651 5bf1ffe`: `docs/integration-guide.md`（+44）・`AppShell/AppShell.css`（83）・`AppShell/AppShell.test.tsx`（+182）・`AppShell/AppShell.tsx`（27）・`AppShell/Sidebar.tsx`（234）・`AppShell/index.ts`（9）・`src/index.ts`（9）・`theme/contrast.test.ts`（+39）・`theme/semantic.css`（+24）の 9 ファイル、625 行の追加・26 行の削除。`package.json`・`package-lock.json`・`LICENSE`・`Icon` に差なし（M7）。
- 親のリポジトリの差は gitlink（`vendor/make-you-chic-ui`）だけ（ほかは `aidlc/` の記録）。

## Step 5: 固定先の更新 — 確かめ（2026-10-13）

- (1) `./gradlew vendorInstall vendorBuild vendorUnchanged osvScan --rerun-tasks`（00:03:30〜00:03:50）: BUILD SUCCESSFUL。`vendorInstall` は `npm ci`（`--ignore-scripts` なし）。`vendorUnchanged` 通過（ビルドの後もサブモジュールの作業フォルダに差なし）。OSV-Scanner は「失敗の条件に当たるもの 0 件、警告 19 件」（すべて npm の開発用。`frontend/package-lock.json` の braces・source-map-js と、`vendor/make-you-chic-ui/package-lock.json` の brace-expansion・braces・postcss-selector-parser・source-map-js・undici。vendor の lockfile は 5bf1ffe で変わらないため、固定先の更新で増えた指摘ではない）。`vendorBuild` の後の `dist`（`frontend/node_modules/make-you-chic-ui` は vendor へのシンボリックリンク）に `navSections` の型が入ったことを確かめた。
- (2) 00:04〜00:05: `npm run typecheck` 失敗 0。Step 2 のコマンドは **19 ファイル・156 件すべて通過**（Step 2 と同じ）。`npm run test:coverage` は **165 ファイル・1,409 件すべて通過**、行 94.88%・分岐 89.54%（Step 3 と同じ）。`ShellLayout.test.tsx`・`AppRouter.test.tsx`・`allowedNavIcons.test.ts` を単独でも流し 3 ファイル・32 件通過（旧形の `navItems` の経路のまま）。
- (3) SHA-256 は Step 3 とすべて同じ（`frontend/package-lock.json` `8a83a269…7ddb`、`frontend/package.json` `cb5cbaed…1071`、`backend/gradle.lockfile` `d4152054…75d5`、`vendor/make-you-chic-ui/package-lock.json` `e0cf6516…4fca`）。lockfile は変わっていない。
- (4) 3.3 の P4（`shared/tree` との読み比べ）: 5bf1ffe の `Sidebar` は開閉を `expandedIds?: Set<string>`・`onExpandedChange?: (ids: Set<string>) => void`（`useControllableState`、制御・非制御の両方）で受け、ナビゲーションのリンク（`<a>`・`aria-current="page"`）と開閉のボタン（`aria-expanded`・`aria-controls`）を描く。`shared/tree` の `SharedTreeView` は `expandedIds: ReadonlySet<string>`・`onToggle(id, expanded)` で、子の遅延読み込み（`nodesToLoad`）と選び（`aria-current="true"` の選んだ項目）を持つ選びの木。用途（移動のメニューと、選んで中身を見る木）と開閉の口の形が違い、遅延読み込みは Sidebar に無いため、`shared/tree` を Sidebar に置き換える必要は無い（変えない）。
- (5) colima は動いている（VM 4 CPU・6GiB、`mounts: []` の既定でホームを共有。作業フォルダはホームの下）。README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（00:05:46〜00:20:17、14 分 31 秒）: **BUILD SUCCESSFUL**。バックエンドの単体 2,082 件・結合 1,165 件（失敗 0・SKIPPED 0、対象DB のテストも実行）、画面のテスト 165 ファイル・1,409 件、画面のカバレッジ 行 94.88%・分岐 89.64%、Gitleaks は no leaks found、`vendorUnchanged` 通過。`osvScan` は (1) の直後で UP-TO-DATE（(1) で `--rerun-tasks` で流した結果が正）。
- (6) 入口の量の点 2（`verify` の中の `frontendBundleSize` と manifest）: 入口の合計 **152.6 KB（gzip）**（`assets/index-oymgDLFg.js` 135.2 KB・`assets/useTranslation-CXXD2N7d.js` 17.3 KB）。点 1 からの増え +0.6 KB は make-you-chic-ui の更新の分（入れ子のサイドバーの部品が共有の塊 `useTranslation` に入る）。入口から静的にたどった集まりは 2 つのまま、項目 63・`isDynamicEntry` 13 は変わらない。鍵3つはまだ無い。
- (7) `./gradlew e2eTest`（`bootWar` を含む、00:21:04〜00:30:08）: **010〜150 の 203 件すべて通過**（失敗・flaky なし。B4 の 135、B8 の 060 も今回は通過）。Mailpit は配備の compose のものを使った（止めていない）。報告の確かめの部品: 「E2E の報告に残してはならない値は含まれていません（値の種類 7・json の報告 1・添付 300・見つかった件数 0）」。終わった後に `frontend/test-results` を消した（`playwright-report` は作られていない）。
- `pmset -g log`: 00:03〜00:31 にスリープ・DarkWake なし。
- 結論: 固定先の更新（5bf1ffe）だけで、既存の画面のテスト・`verify`・E2E の全体はすべて通り、lockfile は変わらない。`cicd-pipeline.md` 8節 (i) の止める条件には当たらない。

## Step 6: 固定先の更新 — 専用のコミットと統合（指揮役が依頼者の承認を得て行う）

- P1 に入れるもの: `vendor/make-you-chic-ui` の gitlink（e82b651c53ac6e048066fcc049e49386c0eb282e → 5bf1ffe3cb7ee5c49963c3d7e8d4e8d0f032aae6）だけ。`aidlc/` の記録（監査ログ・この記録・計画のチェック）は P1 に入れない。
- 開発担当はコミットと統合をしていない（指揮役に引き継ぐ）。

## Step 7: B9 の作業ブランチ（2026-10-13）

- ブランチ `feature/261004-role-menu-b9` は指揮役が `develop` の先頭 f6bd7df（固定先の更新 e911e59 と記録 f6bd7df の後）から作成済み。`git rev-parse HEAD develop` はどちらも f6bd7dfe…。
- `git submodule status vendor/make-you-chic-ui`: 5bf1ffe3cb7ee5c49963c3d7e8d4e8d0f032aae6（Step 6 の版と一致）。

## Step 8: 骨組みの口 — 実装（2026-10-13）

- `app/registry/types.ts`: `path` の項目に任意の `beforeNavigate?: () => void`、`action` の項目に `beforeNavigate?: never`。`LoginStateProvider` に任意の `clearLogoutIntent?: () => void`。
- `app/registry/validateRegistrations.ts`: `beforeNavigate` が関数でなければ「関数にしてください」、`action` の項目（または `path` の無い項目）に付けると「path の項目だけに付けられます」で起動を止める。
- `app/login-state/LoginStateGate.tsx`: 別の文脈 `ClearLogoutIntentContext`（既定は何もしない関数）で提供元の `clearLogoutIntent` を渡し、`useClearLogoutIntent()` を出す（`useLogout` と同じ形）。
- `app/routing/AppRouter.tsx`: `pathname` が `/logout` 以外に変わったとき（初めの描画を含む）に `useClearLogoutIntent()` の関数を呼ぶ効果。

## Step 9: 骨組みの口 — テスト（2026-10-13）

- 足したテスト: `validateRegistrations.test.ts` +3（`path` の項目の関数は通る／`action` の項目に付けると止まる／関数でない値 4 種は止まる）、`LoginStateGate.test.tsx` +2（提供元の口が1回呼ばれる／口が無い・提供元が無いときは何もしない）、`AppRouter.test.tsx` +2（`/logout` 以外の道の描画で呼ばれる／`/logout` では呼ばれず、別の道へ移ると1回呼ばれる）。
- `npx vitest run src/app/registry/validateRegistrations.test.ts src/app/login-state/LoginStateGate.test.tsx src/app/routing/AppRouter.test.tsx`: **3 ファイル・50 件すべて通過**。`npm run typecheck`: 失敗 0。
- 計画との差（確かめたいこと G1 の候補として記録）: `security-design.md` 5.1 は「S4 の未保存の確かめで［留まる］を選んだとき、U6 の `useBlocker` の `reset()` の直後に `useClearLogoutIntent()` を呼ぶ」とするが、計画 5.10 は roleadmin（`useUnsavedGuard`）を変えないものとしており、計画 5.2 の口は `AppRouter` の道の変化だけ。今回は計画どおり `AppRouter` の効果だけを入れた（［留まる］の後は、次に道が変わったときに消える）。Step 19 の `logoutIntent.test.ts` の「［留まる］の後に印が残らない」の扱いは Step 18 の前に決める（報告に選択肢を書いた）。

## Step 10: 純粋な関数と API の写し — 実装（2026-10-13）

- `app/navigation/tablePlaceholderPath.ts`（L6）: `toTablePlaceholderPath(ref, itemId?)`（`URLSearchParams`、先頭は常に `/tables?`、`item` が無い・空なら付けない）・`readTablePlaceholderParams(search)`（`schema`・`table` のどちらかが無い・空なら `ref: null`、値は読んだまま）。
- `app/navigation/navExpansion.ts`（L7）: 鍵 `mastersmith.nav.expanded`、`loadExpanded`（壊れた値・例外は空）・`saveExpanded`（空なら `removeItem`）・`clearExpanded`（`removeItem`）・`pruneExpanded(ids, businessIds)`（今の木に無い `biz:` だけを捨てる、変わらなければ同じ集合）・`addAncestors(ids, ancestorIds)`（足すものが無ければ同じ集合。同じ道で閉じた枝を開き直さないため）。計画 5.3 の名前のとおり（機能設計の `withAncestors` は `addAncestors`）。
- `app/navigation/buildNavSections.ts`（L5）: 出力は `{ sections, currentAncestorIds, businessIds }`（計画の形に、照らし合わせ用の `businessIds` を足した）。区画は ホーム（見出しなし、id `home`）→ 業務（`business`）→ 管理（`admin`）。業務は `loaded`・`reloading` で応答があるときに写し、初めての失敗（`load-error`）は押せない項目 `biz-load-error` を1つ。表示名の既定は D13（空白だけの値も空として扱う）。アイコンは `isAllowedIcon` で照らし、外れは `list`。今の項目は `/` でホーム、`/tables` で D20、ほかは管理の項目の道の区切りでの前方一致の最も長い道。
- `app/navigation/navigationItems.ts`: `selectAdminSidebarItems(registrations, loginState)` を足した（ログイン中の管理者だけに section ADMIN の項目を `order` の順）。`buildSidebarEntries`・`HOME_ENTRY` は Step 16 で消す（今の `ShellLayout` がまだ使うため、この Step では残した）。
- `app/work-role/workRoleApi.ts`（L1）: `getWorkRole()`・`switchWorkRole(roleId)`（本文は `{roleId}` だけ）・`parseWorkRole`。
- `app/navigation/navigationApi.ts`（L2）: `getNavigation()`・`parseNavigation`（知らない `emptyReason`・崩れた項目は通信の失敗。`icon` の null は空の名前として写し、画面で `list` に替える）。
- JSON の確かめの小さな補助（`isObject` など）は、骨組みが機能を読まないため、各ファイルに置いた（features/roleadmin の補助と同じ形の複製）。

## Step 11: 純粋な関数と API の写し — テスト（2026-10-13）

- `tablePlaceholderPath.test.ts` 6 件（記号を含む名前 13 種の往復、fast-check の往復、欠け・空）、`navExpansion.test.ts` 10 件、`buildNavSections.test.ts` 12 件、`buildNavSections.property.test.ts` 7 件（性質 1〜7。木は深さ 5 まで・40 文字までの名前・`<script>`・`javascript:` を含む名前・許していないアイコン・同じ組を指す項目の重なり）、`navigationApi.test.ts` 5 件、`workRoleApi.test.ts` 5 件。
- `unit-test-instructions.md` 3.2 のコマンド: **6 ファイル・45 件すべて通過**。性質ベースのテストの失敗は無く、記録する種は無い。`npm run typecheck`・`npx oxlint src/app`・`npx eslint src/app`: 失敗 0。
- `navExpansion.test.ts` の「木の読み込み前・失敗で捨てない」「木が道より後に届いて祖先を足す」「同じ道で閉じた枝を開き直さない」のうち、関数の側で確かめられるのは「足すものが無ければ同じ集合」まで。いつ呼ぶか（loaded のときだけ捨てる、祖先の並びの中身が変わったときだけ足す）は `ShellLayout` の効果の決まりのため、`ShellLayout.test.tsx`（Step 17）で確かめる。

## Step 12: 状態 — 実装（2026-10-13）

- `app/navigation/useReloadTriggers.ts`（L4）: `useReloadTriggers(onTrigger, extra?)`。ログイン状態の値・`pathname`・`search`・`extra` を1つの効果の依存にし（同じ描画の重なりは1回）、未ログインと `/logout` では呼ばない。呼ぶ関数は React 19.2 の `useEffectEvent` で最新のものを呼ぶ。
- `app/work-role/WorkRoleProvider.tsx`（L3）: `WorkRoleProvider`（`api` を差し替えられる）・`useWorkRole()`（`status`・`roles`・`current`・`revision`・`switching`・`switchTo`・`reload`・`focusWorkRoleSwitcher`・`registerSwitcherFocus`）。世代で古い答えを捨て、読み直しの間と失敗では前の値を残す（初めての失敗だけ `load-error`）。`switchTo` は今のロールなら `'unchanged'`（要求なし）、送信の最中は `'ignored'`、`ROLE_NOT_ASSIGNED`・`ROLE_BUSY`・`VALIDATION_FAILED` は `'rejected'`、ほかは `'failed'`。どの結果でも作業ロールを読み直し、成功と拒否は `revision` を進める（業務のメニュー・S2・S8 の読み直しのきっかけ）。提供元が無いときは既定の値（idle、`switchTo` は `'ignored'`）。
- `app/navigation/BusinessNavigationProvider.tsx`（L4）: `BusinessNavigationProvider`（`load` を差し替えられる）・`useBusinessNavigation()`（`status`・`response`・`reload`）。きっかけは `useReloadTriggers(reload, revision)`（切り替えの回数も同じ効果にまとめる）。未ログインで木を捨て、効果で世代を進めて `clearExpanded()`（D-7）。
- 計画との差: (1) 切り替えの結果に `'ignored'`（送信の最中の2回目）を足した（機能設計の4つの結果の外。NFR2.11 の「捨てる」を呼ぶ側が見分けられるように）。(2) リンタ（`react-hooks/set-state-in-effect`）に合わせ、未ログインでの値の破棄は効果ではなく描画の中で前の値と比べて直す形にした（外の値の世代と保存の鍵だけを効果で直す）。
- `app/testing/frameTestSupport.tsx`（テストの補助、新）: 知らせを出せる偽の提供元・手で解決する約束・道を変えるボタン・偽の API。

## Step 13: 状態 — テスト（2026-10-13）

- `WorkRoleProvider.test.tsx` 9 件（提供元なしの既定、読み込み中→ready、ログインの知らせ・道の変化で読み直し `/logout` では読まない・読み直しの間は値を残す、古い答えを捨てる、初めての失敗だけ load-error、今のロールは要求なし・切り替えは PUT 1・読み直し・revision、拒否3種と失敗、送信の最中の2回目を捨てる、未ログインで捨てる）、`BusinessNavigationProvider.test.tsx` 7 件（提供元なし、道の移動1回で要求2本、切り替え1回で PUT 1本と読み直し2本、読み直しの間と失敗で前の木を残す、初めての失敗と［もう一度］、古い答えを捨てる、未ログインで木と保存の鍵を捨てる）、`useReloadTriggers.test.tsx` 3 件。
- `unit-test-instructions.md` 3.3 のうち `src/app/work-role`・`BusinessNavigationProvider.test.tsx`・`useReloadTriggers.test.tsx`: **5 ファイル・24 件すべて通過**（`workRoleApi.test.ts` を含む）。typecheck・oxlint・eslint: 失敗 0。
- 重なりのテストの直し: 初めに「`extra` の変更と道の移動を同じ押下で起こすと1回」と書いたが、`MemoryRouter` の移動は別の描画になり3回になった（実装どおりの振る舞い）。設計の「同じ描画の中の重なり」に合わせ、道と問い合わせの同時の変化・同じ押下の中の2回の `extra` の変更がそれぞれ1回、の確かめに直した。

## Step 14: 作業ロールの部品 — 実装（2026-10-13）

- `app/work-role/WorkRoleSwitcher.tsx`・`.css`: D5 の5つの表示（idle は何も描かない、読み込み中・失敗は文字、ロールが2つ以上・1つ・無いは `Dropdown` のトリガー、`placement="bottom-end"`）。入れ物 `data-testid="work-role-switcher"`。トリガーは `aria-label` で「作業ロール: 営業」、見える文字は「作業ロール:」「営業」「▼」の3つの span（768px 未満で「作業ロール:」を消す、D9）。項目は D6（「（使用中）」、1つ・無いは押せない項目と `description`、`/me/permissions` が登録されていれば最後に「自分の権限を見る」= `useHasMyPermissions()`、D-6）。送信の間は押せないボタンに差し替え（NFR2.11）、結果は Toast だけ（成功 success・拒否と失敗 warning）、終わったらトリガーへフォーカスを戻す。`useWorkRole().registerSwitcherFocus` にトリガーへのフォーカスの関数を置く（D23 の `focusWorkRoleSwitcher`）。
- 文言の鍵（Step 16 の分を含めて先に足した）: `app/i18n/messages/ja.ts`・`en.ts` に `nav.*`（7）・`workRole.*`（13）・`home.*`（6）。`messages.test.ts` は通過（日英の鍵の一致）。差し込みのある文言は `react-i18next` の `useTranslation` の `t(key, values)` で引く（既存の `useRoleAdminText` と同じ。`useMessages` は差し込みを持たないため）。
- 計画との差: Dropdown はトリガーの `ref` を上書きするため、トリガーへのフォーカスは入れ物の中の `button` を探して当てる。

## Step 15: 作業ロールの部品 — テスト（2026-10-13）

- `WorkRoleSwitcher.test.tsx` 10 件: 読み込み中・失敗は文字だけ、トリガーの名前「作業ロール: 営業」と入れ物の中の `dropdown-trigger`・「（使用中）」の並び、今のロールで要求なし、キーボード（Enter・↓・Enter）で切り替え・成功の Toast・トリガーへのフォーカス、`ROLE_BUSY` の拒否の Toast と読み直し、送信の間は押せない、ロールが1つ・無いの押せない項目と `aria-describedby` の説明・矢印のキーでたどれる、`/me/permissions` が登録されているときだけの「自分の権限を見る」、英語、40 文字のロール名で開いた一覧の axe。
- axe: 開いた一覧は make-you-chic-ui が body の直下に描く（ランドマークの外）ため、既存の `UserRowActions.test.tsx`・`ShellLayout.test.tsx` と同じく、`document.body` は WCAG 2.0・2.1 の A・AA の規則（コントラストは jsdom で測れないため外す）で、入れ物は既定の規則で確かめた。除外の設定は足していない。
- `unit-test-instructions.md` 3.3 のコマンド（`src/app/work-role`・`BusinessNavigationProvider.test.tsx`・`useReloadTriggers.test.tsx`）: **5 ファイル・34 件すべて通過**。typecheck・oxlint・eslint・prettier: 失敗 0。
- C2 のコミットは指揮役が全部の Step の後に提案する（今回は提案しない）。

## Step 16: 骨組みの差し替え — 実装（2026-10-13）

- `app/App.tsx`: 並びを `… → AdminForbiddenProvider → WorkRoleProvider → BusinessNavigationProvider → AppRouter` にした（D2）。
- `app/layout/ShellLayout.tsx`: `navItems` をやめ、`navSections`（`buildNavSections`）・`navExpandedIds`・`onNavExpandedChange`・`navLabels`（「メインメニュー」と開閉の名前、日英。差し込みは `useTranslation`）・`topbarEnd={<WorkRoleSwitcher />}` を渡す。開閉の状態はこの部品が持ち（初めは `loadExpanded()`）、`loaded` の応答が変わったときだけ `pruneExpanded`、今の項目の祖先の並び（JSON の文字列で中身を比べる）が変わったときだけ `addAncestors`。どちらも描画の中で前の値と比べて直す形（効果で状態を変えない、リンタの決まり）。保存（`saveExpanded`）は効果。ユーザーメニューの `path` の項目は既定の移動を止め、`beforeNavigate` があれば呼んでから移る（L15）。
- `app/pages/HomePage.tsx`: D19 の案内（`Alert` の info＝`role="status"`）。読み込み中・idle は出さない。`NOT_CONFIGURED`、`NOTHING_VISIBLE` は作業ロールの有無で `home.nothingVisible`／`home.noRole`、失敗は `home.loadError` と［もう一度］（業務のメニューと作業ロールを読み直す）。`/me/permissions` が登録されているときだけ［自分の権限を見る］（D-6）。文の選び方は純粋な関数 `homeNoticeKey`。
- `app/navigation/navigationItems.ts`: `buildSidebarEntries`・`SidebarEntry`・`HOME_ENTRY` を消した（14節 Q5: A）。残るのは `selectAdminSidebarItems`・`buildUserMenuItems`。
- 文言は Step 14 で足した `nav.*`・`workRole.*`・`home.*`（`home.myPermissions` を含む）。
- この時点で typecheck の失敗は、消した関数を使う既存のテスト（`navigationItems.test.ts` と9つの登録のテスト）だけ。Step 17 で書き換える。

## Step 17: 骨組みの差し替え — テストと既存のテストの書き換え（2026-10-13、途中の記録）

- `navigationItems.test.ts`: `buildSidebarEntries` の3件を `selectAdminSidebarItems` の3件（管理者に order の順・管理者でなければ空・登録なしで空）に書き換え、未ログインの件も直した。
- 9つの登録のテスト（admin・dsl・invitation・useradmin・roleadmin・groupadmin・roletransfer・registration・preferences）: `selectAdminSidebarItems` で同じ中身（管理者だけに出る・並び・未ログインで空）を確かめる形に書き換えた。ホームの項目が並びから外れ（ホームは区画の関数の受け持ち）、管理者でない利用者は `[]`。admin と dsl は返る値が登録の項目そのものになったため `toMatchObject` にした。**10 ファイル・56 件通過**。
- `ShellLayout.test.tsx`: 既存の 18 件を `sidebar-nav-adm:<id>`・`sidebar-nav-home` に書き換えた。見本の「帳票」の項目は section ADMIN で `visibleWhen: 'LOGGED_IN'`（登録の検査に通らない形）だったため `ADMIN` に直し、管理者でない利用者が帳票へ移る確かめはホームの項目・管理者での移動に置き換えた（確かめる中身: 出し分け・サイドバーでの移動・英語・管理者の印を外した後に管理の区画が消える、は保った）。足した 13 件（U7）: 区画の並びと見出し・管理者でなければ管理の区画なし、読み込み中・空で業務の見出しなし、開閉のボタンの `aria-expanded` と名前（日英）・「Main menu」・「Business」、**キーボードだけ（Tab・Enter・Space）で5段を開いて末端へ移る**、**深さちょうど5段が欠けずに描かれ `aria-current` は1つ**、信頼できない名前を文字で出す・知らないアイコンは `list`、移動しても開閉が保たれ保存は id だけ、木が後から届いても祖先が開く、同じ道で閉じた枝は開き直さない（別の道から戻ると開く）、読み込み中・失敗で保存を捨てず loaded で今の木に無い id を捨てる、作業ロールの切り替えで業務の区画が替わる、`beforeNavigate` が移る前に1回、5段・40 文字の名前の axe。**31 件通過**。
- `HomePage.test.tsx`: D19 の案内の 7 件を足した（読み込み中は出さない・設定なし・作業ロールなし・表示なし・失敗と［もう一度］で業務のメニューと作業ロールを読み直す・［自分の権限を見る］は道が登録されているときだけ・失敗の案内の axe）。既存の 4 件はそのまま通る（提供元が無いときは案内を出さない）。
- `AppRouter.test.tsx`: `sidebar-nav-/admin` を `sidebar-nav-adm:admin` に（3 か所）。`DataRouterBlocker.test.tsx`: `sidebar-nav-/admin/other` を `sidebar-nav-adm:probe-other` に（2 か所）、`fetch` を差し替えて `GET /api/me/work-role`・`GET /api/me/navigation` に見本を返す（未保存の確かめの中身は変えない）。`App.test.tsx` は変えずに通過（未ログインで要求なし）。
- `unit-test-instructions.md` 3.4 のコマンド: **24 ファイル・190 件すべて通過**。
- 影響の確かめ（計画 6.1 (d)(e)）: `caffeinate -di npm run test:coverage`（07:57:19〜07:58:12）: **175 ファイル・1,510 件すべて通過**（Step 3 は 165 ファイル・1,409 件）。カバレッジは **行 95.19%・分岐 90.07%**（文 95.08%・関数 96.98%。Step 3 は行 94.88%・分岐 89.54%）。新しいフォルダー: `app/navigation` 行 99.12%・分岐 97.76%、`app/work-role` 行 98.75%・分岐 95.32%、`app/pages` 行 95.45%・分岐 94.44%。`PasswordChangePage`・`RegistrationPage`・`AppRouter` の要求を数えるテストは書き換えずに通った（`renderWithProviders` は新しい提供元を含まないため）。
- 静的検査: `npm run typecheck`・`oxlint .`・`eslint .`・`prettier --check .`・`lint:css`・`license:check` すべて失敗 0。
- `pmset -g log`: 07:57〜07:59 にスリープ・DarkWake なし（`caffeinate` の取得と解放だけ）。
- `frontend/package.json`・`package-lock.json`・`backend/`・`vendor/` に変更なし（lockfile の SHA-256 は Step 3 と同じ `8a83a269…`・`cb5cbaed…`）。
- 計画との差: (1) 管理の区画は管理者だけ（D10・D12）のため、`visibleWhen: 'LOGGED_IN'` の見本で管理者でない利用者がサイドバーの「帳票」へ移っていた既存のテストは、見本を登録の検査に合う形（ADMIN）に直し、管理者での移動・ホームの項目で同じ中身を確かめる形にした。(2) axe の `document.body` の検査は既存の形（WCAG A・AA の規則）を使い、除外は足していない。
- C3 のコミットは指揮役が全部の Step の後に提案する。

## 依頼者の決定 G1（2026-10-13）

- G1: **A（今のまま）**。S4 の未保存の確かめで［留まる］を選んだ後のログアウトの印は、次に道が変わったとき（`AppRouter` の効果）に消える形のままにする。roleadmin（`useUnsavedGuard`）は変えない。`security-design.md` 5.1 の「［留まる］の直後に `useClearLogoutIntent()` を呼ぶ」との差として、この記録と `code-summary.md` に書く。`logoutIntent.test.ts` は「次の道の変化で消える」を確かめる形にする。

## Step 18: ログアウトの道 — 実装（2026-10-13）

- `features/auth/logoutIntent.ts`（新）: `markLogoutIntent`・`peekLogoutIntent`・`consumeLogoutIntent`（モジュールの中の真偽1つ）。
- `features/auth/LogoutPage.tsx`（新）: `security-design.md` 5.2 の形（描画では `useState(peekLogoutIntent)`、効果の中で ref により1回、印があれば `consumeLogoutIntent`・`clearExpanded`・`authSession.logout`、無ければ `navigate('/', { replace: true })`）。文言「ログアウトしています」は `role="status"` の `p`（STANDALONE の配置がすでに `main` を持つため、`main` を重ねない）。
- `features/auth/registration.ts`: `auth-logout` を `{ path: '/logout', beforeNavigate: markLogoutIntent, order: 100 }` に、route `/logout`（LOGGED_IN・STANDALONE、`lazy`）を足した。文言 `auth.logout.inProgress`（日英）。`LOGOUT_PATH` を出す。
- `features/auth/loginStateProvider.ts`: `clearLogoutIntent`（`consumeLogoutIntent` を呼ぶ）を渡す。
- G1: A のとおり、［留まる］の後の印は次の道の変化で消える（roleadmin は変えない）。

## Step 19: ログアウトの道 — テスト（2026-10-13）

- `features/auth/logoutIntent.test.tsx` 4 件: 3つの口、骨組みの口（`loginStateProvider.clearLogoutIntent`）で消える、`/logout` では消えない、同じ道に留まる間は残り次の道の変化で消える（G1: A。［留まる］の後と同じ場面）。`AppRouter` を描くため拡張子は `.tsx`（計画の名前 `logoutIntent.test.ts` との差）。
- `features/auth/LogoutPage.test.tsx` 4 件（StrictMode で包む。`authSession.logout` を差し替え）: 印ありで `logout()` が1回・印が消える・開閉の保存の鍵が無い・ホームへ移らない、印なしで `logout()` を呼ばずホームへ `replace`（保存は消さない）、描き直しても2回目は何もしない、axe。
- `features/auth/registration.test.ts` の書き換え: route が2つ（`/logout` は LOGGED_IN・STANDALONE・`lazy`）、ログアウトの項目が `path: '/logout'` で `beforeNavigate` が印を立てる。ログアウトの要求の確かめ（要求1回・トークンの破棄）は、ログアウトの画面が使う提供元の `logout` の経路で残した。
- `features/preferences/registration.test.tsx`: `{ id: 'auth-logout', path: '/logout', order: 100 }` に直した（並びの確かめは同じ）。
- `app/routing/DataRouterBlocker.test.tsx` に1件足した（D27）: 未保存の変更がある画面でユーザーメニューの「ログアウト」を選ぶと、印が立ち、移動が止まって確かめが出る。［留まる］で同じ道に残り、もう一度選んで［移る］で `/logout` の画面へ移る。
- `unit-test-instructions.md` 3.5 のコマンド: **12 ファイル・78 件すべて通過**。typecheck・oxlint・eslint: 失敗 0（`registration.test.ts` の `no-unsafe-optional-chaining` を1か所直した）。

## Step 20: tables（S2）— 実装（2026-10-13）

- `features/tables/`（新）: `messages.ts`（`tables.*` の 9 鍵、日英）・`registration.ts`（`/tables`、LOGGED_IN・SHELL、`lazy`、サイドバーの項目なし。静的に読むのは文言と道の定数だけ）・`tableAccessApi.ts`（`getTableAccess(ref)`・`parseTableAccess`・`tableAccessPath`。引数は `URLSearchParams` の `schema`・`table` だけで `item` を送らない）・`useTableAccess.ts`・`TablePlaceholderPage.tsx`・`.css`。
- `useTableAccess(ref)`: 引数・作業ロールの `roleId`・`revision`・［もう一度］の回数を鍵にして問い合わせ、世代で古い答えを捨てる。問い合わせの最中は前の表示を残し、`pending` は「答えの鍵が今の鍵と違う」で導く（効果の中で状態を同期で変えないリンタの決まりのため）。200 は available、403・400 は forbidden、ほかは load-error。403 で業務のメニューと作業ロールを読み直す（D4 の (c)・D22）。引数が無い・空なら要求を送らず forbidden。
- `TablePlaceholderPage`: available は h1 に表示名（D13、`resolveNavLabel` を使う）と準備中の文。forbidden は h1「この画面を表示する権限がありません」と「今の作業ロール: 〇〇」（無ければ「なし」）、ロールが2つ以上なら［作業ロールを切り替える］（`focusWorkRoleSwitcher`）、いつも［ホームへ］。表示名とテーブルの名前は出さない。load-error は文と［もう一度］。状態の文は見えない `aria-live="polite"` の区画で伝え、フォーカスは動かさない。
- 計画との差: `latestLoad.ts` は作らず、世代の数を hook の中に持った（B8 の複製の形を置くほどの量ではないため）。

## Step 21: tables（S2）— テスト（2026-10-13）

- `tableAccessApi.test.ts` 5 件（`schema`・`table` だけを送り知らない項目を写さない、記号のエンコード、403・400 の ApiError、FULL を受け崩れた本文と知らない `main` は通信の失敗、JSON でない本文）、`registration.test.ts` 3 件（`lazy`・LOGGED_IN・SHELL・サイドバーの項目なし、検査と日英の鍵、未ログインはログインへ）、`TablePlaceholderPage.test.tsx` 9 件（表示名と準備中・`item` を送らない、英語・他方の言語・テーブル名の既定、403・400・引数なしが同じ表示で名前を出さない（引数なしは要求なし）、403 で業務のメニューと作業ロールを読み直す、作業ロールの切り替えで問い直す、［作業ロールを切り替える］はロールが2つ以上のときだけでトリガーへフォーカス・［ホームへ］、`aria-live` で伝えフォーカスを動かさない、失敗と［もう一度］、available と forbidden の axe）。
- 直したこと: 初めの描画で問い合わせが2回出た（作業ロールの読み込みの前と後）。`useTableAccess` は作業ロールの初めての読み込みが終わるまで問い合わせを待つ形にし、あわせて `WorkRoleProvider` はログインした直後（読み込みを始める前の描画）も `loading` を返す形にした（それまでは一瞬 `idle` を返していた）。骨組みのテストは直さずに通る。
- `unit-test-instructions.md` 3.6 の tables のコマンド: **3 ファイル・17 件すべて通過**。骨組みのテスト（`src/app/work-role`・`layout`・`pages`・`navigation`）も流し直して通過。oxlint・eslint: 失敗 0。

## Step 22: 〔Should〕mypermissions（S8）— 実装（2026-10-13）

- `features/mypermissions/`（新）: `messages.ts`（`mypermissions.*`、日英。共有の木の文言を含む）・`registration.ts`（`/me/permissions`、LOGGED_IN・SHELL、`lazy`、ユーザーメニュー `mypermissions-open` order 70）・`myPermissionsApi.ts`（`listMySchemas`・`listMyTables(schema)`・`listMyColumns(schema, table)`・`parseMyPermissions`・`myPermissionsPath`。名前は問い合わせの引数、`displayName` は文字列1つ（D-3、null は空として写す）、知らない `main` は通信の失敗）・`useMyPermissions.ts`・`MyPermissionsDetail.tsx`・`MyPermissionsPage.tsx`・`.css`。
- `useMyPermissions`: 作業ロールの `roleId`・`revision`・表示の言語・［もう一度］の回数を鍵にして最上位を読み直し、選び・開閉・カラムの答えは同じ鍵のときだけ使う（鍵が変われば捨てた扱い。D-5、14節 Q4: A）。作業ロールの初めての読み込みの間は待つ（S2 と同じ）。テーブルの段は共有の木の `loadChildren`、テーブルを選んだらカラムの段を読む（世代で古い答えを捨てる）。選んだ節の値は選ぶ操作の中で引いて持つ（描画の中で ref を読まないリンタの決まり）。
- `MyPermissionsPage`: 見出し・「今の作業ロール: 〇〇」・左の `SharedTreeView`（`shared/tree` は変えない）・右の `MyPermissionsDetail`（主権限は「READ（見られる・直せない）」などの文字、CREATE・DELETE は「可」「不可」、テーブルには「業務のメニュー: 出る・出ない」、カラムは主権限だけ）。作業ロールなし・対象なし・失敗と［もう一度］。768px 未満は CSS で縦に積み、`matchMedia('(width < 768px)')` が合うときは選んだ後に表の見出しへフォーカスを移す。
- D-6: 道 `/me/permissions` が登録されたため、作業ロールの一覧の最後とホームの案内に「自分の権限を見る」が出る（骨組みは登録の道で知る）。

## Step 23: 〔Should〕mypermissions（S8）— テスト（2026-10-13）

- `myPermissionsApi.test.ts` 5 件（3つの道と引数のエンコード、`displayName` は文字列で知らない項目を写さない、`workRole` と `displayName` の null、崩れた本文（`{ja,en}` の表示名・知らない `main` など）は通信の失敗、400 の ApiError）、`registration.test.ts` 3 件（`lazy`・LOGGED_IN・SHELL、ユーザーメニューの order 70 がプリファレンスの前でログアウトが最後、検査と日英の鍵）、`MyPermissionsPage.test.tsx` 9 件（見出し・今の作業ロール・最上位の木、スキーマを開いてテーブルを選ぶと値の文字（「FULL（見られる・直せる）」「可」「不可」「出る」）とカラムの表、スキーマの値（業務のメニューの列なし）、作業ロールなし・対象なし、失敗と［もう一度］、作業ロールの切り替えで最上位から読み直して選びを捨てる、表示の言語の切り替えで読み直す、768px 未満の縦の積み（クラスの付き方）と表の見出しへのフォーカス、木を開いてカラムの表を出した状態の axe）。
- `unit-test-instructions.md` 3.6 の mypermissions のコマンド: **3 ファイル・17 件すべて通過**。typecheck・oxlint・eslint: 失敗 0。
- 書いている途中の直し: テストの見本に機能の文言（登録）を渡していなかった、共有の木の開閉の名前は「販売を開く」（間に空白なし）だった。どちらもテストの側の誤り。

## Step 24: 画面の全体の検査（2026-10-13）

- `frontend/` で `npm run format:check`・`lint`・`lint:css`・`license:check`・`typecheck`: **すべて失敗 0**（09:00〜09:16。この間に PC がスリープした（09:00:07〜09:16:01）が、静的検査は時間に依らないため結果を使う）。
- 1回目の `caffeinate -di npm run test:coverage`（09:16〜）は、PC が DarkWake から再びスリープに入り（09:17:02〜09:32:22）止まったため、結果を使わずに止めて流し直した。
- 2回目 `caffeinate -dims npm run test:coverage`（09:32:48〜09:33:40）: **183 ファイル・1,554 件すべて通過**。カバレッジは **行 95.26%・分岐 90.10%**（文 95.11%・関数 96.74%）で、下限（行 80%・分岐 70%）を満たす（除外は足していない）。`pmset -g log`: 実行の間（09:32:48〜09:33:40）にスリープ・DarkWake の出入りなし（終わった直後の 09:33:40 にスリープに入った）。
- Step 3 との比べ: 165 ファイル・1,409 件、行 94.88%・分岐 89.54% → 183 ファイル・1,554 件、行 95.26%・分岐 90.10%。
- 新しい・変えたフォルダーの値（行・分岐）: `app/work-role` 99.38%・95.32%、`app/navigation` 99.12%・97.76%、`features/tables` 97.67%・94.64%、`features/mypermissions` 95.59%・88.73%、`features/auth` 99.19%・100%。目安（行 90%・分岐 80%）をすべて満たす。
- `frontend/package.json`・`package-lock.json` の SHA-256 は Step 3 と同じ（`cb5cbaed…`・`8a83a269…`）。`vendor/`・`backend/` に変更なし。

## Step 25: E2E の部品 — 実装（2026-10-13）

- `e2e/support/userMenu.ts`（新、L12）: `userMenuTrigger(page)`・`openUserMenu(page)`・`openUserMenuItem(page, name)`。`app-shell` の中の `header` で、`work-role-switcher` の子孫でない `dropdown-trigger` を XPath（`not(ancestor::*[@data-testid="work-role-switcher"])`）で探し、1件に絞れることを確かめてから押す（12節 D-9）。
- `e2e/support/registeredUser.ts`: `openUserMenuItem` の本体を `userMenu.ts` へ移し、既存の呼び出し元（080・100・110）の import を保つため `export { openUserMenuItem } from './userMenu'` で再び出す形にした（計画 5.8 の「Step 25 で決めて記録」）。
- `e2e/080-preferences-accessibility.e2e.ts`: 232 行の `getByTestId('app-shell').getByTestId('dropdown-trigger').click()` を `openUserMenu(page)` に替えた。
- `e2e/support/frameApiRoute.ts`（新、L13）: `/api/me/work-role`・`/api/me/navigation`・`/api/me/table-access`・`/api/me/permissions/` の下を受ける口。見本は前もって直列化した本文（`jsonReply`）。`PUT /api/me/work-role` に 204 を返すと、その後の作業ロールと業務のメニューの GET は切り替えの後の見本を返し、`reset()` で前に戻す（NFR 設計 R-09 の手当て、12節 D-12）。見本の無い GET は打ち切り、GET 以外は道を記録して打ち切る（`expectFrameRouteClean`）。
- `e2e/support/navTreeFixtures.ts`（新、L13）: 型は画面の側の `NavigationResponse`・`WorkRoleState`・`TableAccess`・`MyPermissionNodes`。検査の木（5段の枝・40 文字の名前・記号を含む名前・許していないアイコン `table`）、目安の木（100 項目・深さ 1）、悪い側の木（1,000 項目・深さ 5・40 文字の名前・末端までの1本の枝）、切り替えの後の印の項目、空の理由2つ、ロール3つ（40 文字のロール名を含む）、置き場の見本と 403、自分の権限の3段の見本。項目の id はサーバーと同じ位置の道（`0`・`0.0` …）。
- `e2e/support/u7Measure.ts`（新）: `installU7Done`（判定の表 `__u7Done`: `workRoleSwitched`・`groupOpened`・`placeholderShown`）・`armU7Measure`（`click` の捕捉の段、最初の1回）・`readU7Measure`。判定と記録は `u6Measure.ts` の `summarizeRuns` を使う。
- `npx prettier --write`（6 ファイル）・`npm run typecheck`・`npx oxlint e2e`・`npx eslint e2e`: 失敗 0。

## Step 26: 既存の E2E の書き換えの確かめ（2026-10-13、途中で止めた）

- `pmset -g assertions` で UserIsActive 1 を確かめた。colima は動いている、Mailpit の API は 200。
- `caffeinate -dims ./gradlew :backend:bootWar`（17:54:26〜17:54:51）: 成功。入口の塊は gzip 156.47 kB（記録だけ、判定は Step 31）。
- `caffeinate -dims npx playwright test` で 020・030・040・080・090・100・110・120・130（17:54:58〜17:59:48、4.8 分）: **89 件のうち 71 件通過・18 件失敗**。`pmset -g log`: 17:54〜18:00 にスリープ・DarkWake の出入りなし。
- 失敗はすべて幅 375px の組（(c) の6組）で、080 の6件・120 の6件・130 の6件。どれも画面全体の横のはみ出し（`scrollWidth` 407〜417px > `innerWidth` 375px。文字の大きさ sm 407・md 412・lg 417）。ユーザーメニューの開き口・ログアウト・サイドバーのリンク（020・030・040・090・100・110 と各ファイルの 375px 以外の組）はすべて通った。
- 原因の確かめ（scratchpad の Playwright の台本、リポジトリに足していない。初期管理者・375px・md）: 幅 220px 固定のサイドバーの右のトップバー（`header.mycui-topbar`）の最小の幅が 192px で、残りの 155px に収まらない。トップバーの中身は 左右の余白 16px×2・畳むボタン 28px・間 8px×3・作業ロールのトリガー 64px（768px 未満の「なし ▼」）・ユーザーメニューのトリガー 44px。作業ロールのトリガーを足す前は約 120px で収まっていた。`main` も同じ列のため 192px に広がる。
- 計画 6.2 (m) のとおり、期待と除外は変えずに止めて依頼者に尋ねる（G2）。
- 記録の誤り: この実行は `--reporter=line` を付けたため、設定の報告の部品（`playwright-secret-check-reporter.ts`）と json の報告が外れ、報告に残してはならない値の確かめが行われなかった。`frontend/test-results/`（56 件、失敗の文脈のファイルなど）は共有せずにすぐ消した。以後の実行では報告の部品を外さない（`--reporter` を付けない）。

## 依頼者の決定 G2（2026-10-13）と直し

- G2: **A**。768px 未満では作業ロールの切り替えをトップバーから外し、内容の領域の先頭（`ShellLayout` の `main` の先頭）に置く。768px 以上は今のまま。機能設計 D9（トップバーに置き、768px 未満は見える文字を縮める）との差として記録する。既存の E2E の期待と除外は変えない。
- 直したもの:
  - `src/app/layout/useNarrowLayout.ts`（新）: `matchMedia('(width < 768px)')` を `useSyncExternalStore` で購読する。`matchMedia` が無いとき（jsdom）は広い幅として扱う。境は `WorkRoleSwitcher.css`・`MyPermissionsPage` と同じ。
  - `src/app/layout/ShellLayout.tsx`: 狭い幅では `topbarEnd` を渡さず、子の前に `shell-work-role-bar`（`data-testid`）の中に `WorkRoleSwitcher` を描く。どちらの幅でも、切り替えの部品は1つだけ描く（フォーカスの口 `registerSwitcherFocus` も1つ）。
  - `src/app/layout/ShellLayout.css`（新）: 帯は右寄せ、下に余白。帯の中では見える文字の「作業ロール:」を戻す（幅に余裕があるため。読み上げの名前は aria-label のまま）。
  - `src/app/layout/ShellLayout.test.tsx` に4件を足した: 広い幅ではトップバー、768px 未満では内容の領域の最初の子に1つだけあり、ユーザーメニューはトップバーに残って切り替えの一覧が開ける、幅の変化に従う、狭い幅の axe。
- `npx vitest run src/app/layout src/app/work-role`: **6 ファイル・68 件すべて通過**。`format:check`・`lint`・`lint:css`・`license:check`・`typecheck`: 失敗 0。

## Step 26: 既存の E2E の書き換えの確かめ — 流し直し（2026-10-13）

- `bootWar` を作り直した後、`caffeinate -dims npx playwright test`（`--reporter` を付けない、19:43:51〜19:49:15）で 020・030・040・080・090・100・110・120・130: **89 件すべて通過**（5.4 分、失敗・flaky なし）。375px の組のはみ出しは無くなった。
- 報告の部品: 「E2E の報告に残してはならない値は含まれていません（値の種類 7・json の報告 1・添付 163・見つかった件数 0）」。終わった後に `frontend/test-results` を消した。
- `pmset -g log`: 19:43〜19:50 にスリープ・DarkWake なし。

## Step 27: 160 — 実装と実行（2026-10-13、途中で止めた）

- `e2e/160-app-frame-accessibility.e2e.ts`（新）: 20 組ごとに1つのテスト（5段を開いたサイドバーと置き場、作業ロールの一覧（3つ）、置き場の権限なし、S8 のカラムの表、ホームの案内3つ、ロールが1つ・無いの一覧、畳んだサイドバー）、幅の4件（360・768・1280・lg dark 360。768px 未満は作業ロールの切り替えが内容の領域の先頭（G2: A）、768px 以上はトップバー）、英語の1件、画面の時間の1件。API は `frameApiRoute.ts` の口だけで返す。差し替えた失敗の「Failed to load resource:」は骨組みの API の道のものだけを引く（140 と同じ形）。
- `frameApiRoute.ts` に、失敗で返した件数 `failedReplies` を足した（除いた表示の上限の確かめに使う。140 の口と同じ形）。
- 計画との差:
  - サイドバーの矩形の確かめは横（左右の端）だけにした。5段・1,000 項目の木のサイドバーは縦に伸びて画面ごと縦に送るため、縦まで見ると必ず外れる（1回目の実行で、20 組すべてが `bottom` で外れた）。サイドバーと末端の項目の左右がサイドバーの中・表示の中にあることを確かめる。開いた一覧の確かめは今までどおり（縦も見る）。
  - axe の違反の知らせに、規則と要素の選択子（各規則 3 件まで）を足した（値は含まない）。
  - ホームの案内の文のコントラストの不足を直した（20 組の (a) light sm blue で `color-contrast`: `p[data-testid="home-notice"]`）。案内の文に薄い色の `page-description` を使っていたため、info の Alert の背景の上で足りなかった。`HomePage.tsx` は `page-notice`（色は Alert のまま）を使い、`Page.css` に `.page-notice` を足した。画面のテスト（jsdom はコントラストを測れない）は `src/app/pages` 2 ファイル・15 件通過。
- 実行（`--reporter` なし。WAR は直しの後に作り直した）:
  - 1回目（19:51〜19:52）: 26 件のうち 24 件失敗（サイドバーの縦）。
  - 2回目（一部 `-g`）: ホームの案内の `color-contrast` と、360px の開いた作業ロールの一覧のはみ出し。
  - 3回目（19:55:46〜19:57:02、1.2 分）: **26 件のうち 18 件通過・8 件失敗**。失敗はすべて、768px 未満（(c) の 375px の6組と、幅の 360px・lg dark 360px）で、開いた作業ロールの一覧が左に 16px はみ出す（`left -16px・right 375px / innerWidth 375px`）。ほかの状態（(a)(b) の 14 組のすべての状態、768・1280px、英語、畳んだサイドバー）は通った。畳んだサイドバーで `aria-labelledby` の違反は出なかった。375px・360px の組は一覧の確かめで止まったため、その後の状態（権限なし・S8・ホーム・畳んだ状態）はまだ見ていない。
  - 報告の部品は「失敗の画面の写し」に初期管理者のメールアドレスを見つけた（失敗したテストの写しにログインの画面が写る。報告は共有せず、`test-results` はその都度消した）。通るときは写しが作られない。
- 原因: 768px 未満では作業ロールのトリガーが内容の領域の右端（右から 16px）にあり、`placement="bottom-end"` は一覧の右端をトリガーの右端にそろえる。40 文字のロール名を含む一覧は、幅の上限が無いため画面の幅いっぱい（375px）に広がり、左に 16px はみ出す。make-you-chic-ui の `computeFloatingPosition` は、反対の向きでもはみ出すときに位置を詰めない。
- 確かめ（scratchpad の台本。アプリのコードは変えずに、CSSOM で `@media (width < 768px) { .mycui-dropdown-menu { max-width: calc(100vw - 2 * var(--space-4)) } }` を足した）: 360px で左 6px・右 344px、375px で左 6px・右 359px に収まる。767px は変わらない（左 197px・右 751px）。
- 画面の時間（3回目の実行の既定の1組、記録だけ。判定は5回の中央値）: NFR2.5 切り替え（目安の木）中央値 32.6 ms・最大 34.5・1回目 32.2（Met、目標 500）。NFR2.5（悪い側の木、1回）38.8 ms（記録）。NFR2.6 4段目を開く 中央値 14.5 ms・最大 14.8・1回目 13.1（Met、目標 200）。NFR2.7 末端から置き場 中央値 89.8 ms・最大 95.9・1回目 95.9（Met、目標 500）。
- `pmset -g log`: 19:50〜19:58 にスリープ・DarkWake なし（UserIsActive は 0 だったが、`caffeinate` と画面の点灯でスリープは止められていた）。
- 計画 10.1 の「360px ではみ出したら止まって依頼者に尋ねる」に当たるため止めた（G3）。

## 依頼者の決定 G3（2026-10-13）と直し

- G3: **A**。768px 未満で、開いた作業ロールの一覧の最大の幅を「画面の幅 − 余白」に抑える。効く範囲はできるだけ作業ロールの一覧に絞る。make-you-chic-ui の中身は変えない。
- 一覧は make-you-chic-ui の Dropdown が `createPortal` で body の直下に描くため（`Dropdown.tsx` 143 行）、入れ物（`.work-role-switcher`）の子孫の選択子では絞れない。そこで `src/app/work-role/WorkRoleSwitcher.css` に、`@media (width < 768px)` の中の `body:has(.work-role-switcher [aria-expanded='true']) .mycui-dropdown-menu { max-width: calc(100vw - 2 * var(--space-4)); }` を足した。作業ロールのトリガーが開いている間だけ効く（ほかの Dropdown の開いている間は効かない。Dropdown は外を押すと閉じるため、2つが同時に開くことは通常ない）。`:has()` を扱えないブラウザでは効かない（今の Chromium・Safari・Firefox は扱える）。
- make-you-chic-ui のクラス `.mycui-dropdown-menu` をアプリの CSS で上書きしている。上流に「一覧を画面に収める口（幅の上限か、位置を表示の中へ詰める）」を後で依頼したい（code-summary.md の後に回すことに書く）。
- 画面のテスト（`src/app/work-role`・`src/app/layout`・`src/app/pages`）: **8 ファイル・83 件すべて通過**（jsdom は幅と位置を測れないため、確かめは 160）。`format:check`・`lint`・`lint:css`・`license:check`・`typecheck`: 失敗 0。WAR を作り直した（`dist` の CSS に決まりが入ったことを確かめた）。

## Step 27: 160 — 流し直し（2026-10-13）

- `caffeinate -dims npx playwright test e2e/160-app-frame-accessibility.e2e.ts`（20:40:55〜20:42:19、1.4 分、`--reporter` なし）: **26 件すべて通過**。1組あたり約 2〜3 秒（(c) light sm blue 375px が 3.0 秒、幅 360px が 2.0 秒）で、10 分を大きく下回るため 161 に分けない。
- 375px の6組・360px・lg dark 360px で、開いた作業ロールの一覧（3つ・1つ）は左 6px・右 359px（375px）、左 6px・右 344px（360px）で表示の中に収まった。その後の状態（権限なし・S8・ホームの案内3つ・ロールが無い・畳んだサイドバー）もすべて axe の違反 0 件・はみ出しなしで通った。畳んだサイドバーで `aria-labelledby` の違反は出なかった。
- 報告の部品: 「残してはならない値は含まれていません（値の種類 6・json の報告 1・添付 1・見つかった件数 0）」。`test-results` は消した。
- 画面の時間（既定の1組、記録だけ。5回の中央値・最大・1回目）: NFR2.5 切り替え（目安の木）32.7・33.2・32.7 ms（Met、目標 500）。NFR2.5（悪い側の木、1回）37.9 ms（記録）。NFR2.6 4段目を開く 15.0・15.7・14.8 ms（Met、目標 200）。NFR2.7 末端から置き場 94.5・105.7・97.5 ms（Met、目標 500）。測れない回は無い。
- `pmset -g log`: 20:40〜20:43 にスリープ・DarkWake なし（UserIsActive 1）。

## Step 28: 170 — 実装と実行（2026-10-13）

- `e2e/170-menu-navigation-flow.e2e.ts`（新）: 計画 10.2 の手順 1〜9。DSL は `E2EI_<印>` のスキーマ・テーブル T1・T2（表示名 40 文字）、メニューは各段 40 文字の名前の5段の入れ子（末端が T1）と T2 を指す1段目の項目。ロール `E2EI_ROLE_<印>` は API で作り、T1 を READ・T2 を NONE にする。利用者は `createRegisteredUser`。メールアドレス・パスワード・氏名（`REGISTERED_DISPLAY_NAME` の決まった値）を `recordSecretValues` で書き、読み戻して3つが揃わなければ失敗にする（R-07）。割り当ては API。画面では開閉のボタン（`sidebar-toggle-biz:<id>`）で5段を開いて末端を選び、見出し・`aria-current`・祖先の展開・T2 の項目が出ないことを、選んだ後と再読み込みの後に確かめる。T2 の道を直接開くと権限なしの見出しで、T2 の表示名とテーブル名が出ない。本物の `GET /api/me/navigation`・`/api/me/table-access`・`/api/me/permissions/schemas` の形を、160 の見本と `shapeCompare` で比べる。ログアウトは `security-design.md` 5.3 の (a)〜(f)（`/logout` の直接の表示でホームへ戻りログインしたまま、ユーザーメニューの「ログアウト」から数えて要求1回、戻る・再読み込みでもログインの画面、開閉の保存の鍵が無い）。
- 計画との差: 権限の保存は、テーブルごとに1回ずつ（範囲 `scope` をテーブルにする）。サーバーはスキーマの範囲ではスキーマの対象だけを受け、テーブルの対象を入れると 400 になる（`PermissionInputs.inScope`）。1回目の実行でこの 400 で止まり、テストの側を直した。氏名は計画の「流れの中で決める」ではなく、`createRegisteredUser` が使う決まった値（`secretValues.ts` の決まった値の表に載る）を書いた。
- `caffeinate -dims npx playwright test e2e/170-menu-navigation-flow.e2e.ts`（`--reporter` なし）: 1回目は権限の保存の 400 で失敗（テストの誤り）。直した後 **1 件通過**（20:45:06〜20:45:19、12.7 秒）。もう一度流して通過（11.6 秒）。見本と本物の形の違いは無い（`shape-differences` は空）。
- 報告: 部品は「残してはならない値は含まれていません（値の種類 7・見つかった件数 0）」。json の報告の文字列の検索（メールアドレスの形・パスワードの形・氏名・`accessToken`）で当たったのは、設定の送信元 `e2e-noreply@example.com`（秘密ではない）だけ。`test-results` は消した。
- `pmset -g log`: 20:44〜20:46 にスリープ・DarkWake なし。

## Step 29: 150 への追加 — 実装と実行（2026-10-13）

- `e2e/150-role-permission-flow.e2e.ts`: 手順 6 の後に 6a〜6e を足した（本数は1本のまま）。6a: 2つ目のロール `E2EF_ROLE2_<印>`（T を設定しない）を API で作り、同じ利用者に API で割り当てる（12節 D-10）。6b: 初期管理者を画面のユーザーメニューからログアウトし、利用者で画面からログインして、トップバーが「作業ロール: <1つ目>」、開いた一覧に「<1つ目>（使用中）」と2つ目、サイドバーに T の項目（`sidebar-nav-biz:0`）がある。6c: 2つ目へ切り替えると `toast-container` に切り替えの文が出て、T の項目が消える。6d: 1つ目に戻すと T の項目が出る。6e: 利用者のトークンで `GET /api/me/work-role` の本物の応答と、160 の見本（`workRoleWire`）の形を比べる。手順 7 以降は変えていない。
- 計画との差: 利用者の画面は、初期管理者の画面（手順 3〜5）をユーザーメニューからログアウトした同じページで開いた（新しいブラウザのコンテキストを作らない）。ログアウトの道（U7）も通る。
- `caffeinate -dims npx playwright test e2e/150-role-permission-flow.e2e.ts`（`--reporter` なし、20:46:49〜20:47:08）: **1 件通過**（18.2 秒）。形の違いは無い。
- 報告: 部品は「残してはならない値は含まれていません（見つかった件数 0）」。json の報告の文字列の検索で当たったのは設定の送信元 `e2e-noreply@example.com` だけ。`test-results` は消した。
- `pmset -g log`: 20:46〜20:48 にスリープ・DarkWake なし（UserIsActive は 0 だったが、`caffeinate` でスリープは止められていた）。

## Step 30: 文書（2026-10-13）

- `README.md`: E2E の表に `135-dsl-admin-accessibility.e2e.ts`（130 と 140 の間、B8 の書き漏れ。14節 Q8: A）・`160-app-frame-accessibility.e2e.ts`・`170-menu-navigation-flow.e2e.ts` の行を足し、150 の行に作業ロールの切り替えを足した。「140・150 について」の B9 の書き方を直し、「160・170 について」（本数の数え方・差し替えの口・後始末しない・単独で流すときの Mailpit・`userMenu.ts`）を足した。画面の節に「骨組みの画面（作業ロール・業務のメニュー・テーブルの置き場・自分の権限）」を足した（768px 未満の作業ロールの置き場（G2: A）を含む）。B8 の画面の節の「上の帯の『ログアウト』では確かめが出ないことがあり」を、ログアウトの道で確かめが出る形に直した。
- `frontend/src/features/README.md`: ユーザーメニューの `beforeNavigate` と並びの order（自分の権限 70）、骨組みのフック `useWorkRole`・`useBusinessNavigation`（提供元の外では idle）、置き場の道は `toTablePlaceholderPath` だけで作ること、ログアウトの道と `useLogout` の関係を足した。
- `npm run format:check`: 失敗 0（`e2e/150-role-permission-flow.e2e.ts` の書式を `prettier --write` で直した。中身は変えていない）。ルートの `README.md` は Prettier の検査の対象の外（変更の前の版も Prettier の書式ではない）。

## Step 31: 取り残しと変えないものの確かめ、入口の量の判定（2026-10-13）

- `git diff --stat develop` で、計画 5.10 の変えないもの（`backend/`・`vendor/`・`frontend/package.json`・`package-lock.json`・`.npmrc`・`vite.config.ts`・`vitest.config.ts`・`vitest.setup.ts`・`playwright.config.ts`・`eslint.config.js`・`.oxlintrc.json`・`tsconfig*.json`・`src/main.tsx`・`src/shared/`・`src/app/testing/renderWithProviders.tsx`・`src/app/registry/allowedIcons.ts`・`docker/`・`.github/`・`frontend/scripts/check-bundle-size.mjs`）に差は無い。`src/features/` の auth・tables・mypermissions の外の変更は、9つの登録のテスト（Step 17）と `src/features/README.md` だけ（本体は変えていない）。テストの補助 `src/app/testing/frameTestSupport.tsx` は新しいファイル（Step 12）。
- lockfile の SHA-256 は Step 3 とすべて同じ（`frontend/package.json` `cb5cbaed…1071`・`frontend/package-lock.json` `8a83a269…7ddb`・`backend/gradle.lockfile` `d4152054…75d5`・`vendor/make-you-chic-ui/package-lock.json` `e0cf6516…4fca`）。NFR6.5 を満たす。
- 変更・新しいファイル（frontend の 86 件）に `TODO`・`FIXME` は無い。新しい `.ts`・`.tsx`・`.css` はすべてライセンスヘッダーを持つ。`frontend/src` に `console.` を足した行は無い（NFR1.6）。
- 入口の量の点 3（`caffeinate -dims ./gradlew frontendBundleSize`。G3 の直しの後の WAR と同じ `dist`）: 入口の合計 **158.3 KB（gzip）**（`assets/index-SAOmD7Kx.js` 151.8 KB・`assets/useTranslation-C0Eg_Lf_.js` 6.4 KB）。点 1 152.0 KB → 点 2 152.6 KB（固定先の更新の分 +0.6 KB）→ 点 3 158.3 KB（骨組みの分 +5.7 KB）。500 KB の警告の目安の内。
- manifest（scratchpad の一時のスクリプト `manifest-check.mjs`、リポジトリに足していない）: 項目 68・`isDynamicEntry` 16。入口から静的にたどった集まりは 2 つ（`index`・`useTranslation`）のまま。
  - (i) 鍵3つは manifest に在り、どれも `isDynamicEntry: true`: `src/features/tables/TablePlaceholderPage.tsx`（`TablePlaceholderPage-D13daGsb.js`、gzip 1.3 KB）・`src/features/mypermissions/MyPermissionsPage.tsx`（`MyPermissionsPage-mdeBj2l5.js`、2.5 KB）・`src/features/auth/LogoutPage.tsx`（`LogoutPage-CJwwzbX_.js`、0.3 KB）。
  - (ii) 3つとも、入口から静的にたどった集まりに無い。
  - **判定: 合格**（計画 11節の2条件を満たす）。

## Step 32: 記録（2026-10-13）

- `code-summary.md`（新）: 作ったもの、決めたこと、テストの量、計画との差（G1〜G3・本体の直し・小さな差・追加の依頼の3点）、固定先の更新の前後のハッシュと確かめ、承認の場で確かめたいこと、入口の量の3点と判定、後に回すこと（make-you-chic-ui への一覧の幅の口の依頼を含む）、受け入れた制約。8節（統合の前の関門）は Step 33 の後に書く。冒頭のコミットの記述は「依頼者の承認を得て計画の区切りで行う」とした。
- `source-manifest.json`（新）: アプリのソースの道 88 件（`develop` との差と新しいファイル、`aidlc/` を除く。P1 の gitlink `vendor/make-you-chic-ui` を含む）。
- `traceability.json`（新）: 112 行（OK 99・Deferred 13）。AC 53 件（OK 40 は1つの実在するテストのファイル、Deferred 13 は機能設計の受け持ちの単位のまま）、D1〜D30、NFR の枝番 29（NFR1.1・NFR1.4・NFR1.6〜NFR1.10・NFR2.2・NFR2.5〜NFR2.12・NFR4.1〜NFR4.5・NFR6.1〜NFR6.8）。基盤の traceability で N/A だった NFR1.1・NFR1.4・NFR2.9・NFR2.11・NFR4.1〜NFR4.3 はテストを target にした OK（D-14）。OK の target がすべて実在するファイルであることを、生成のスクリプト（scratchpad）で確かめた。固定先の更新（D28・NFR6.6）の target は、gitlink ではなく 5bf1ffe の `Sidebar.tsx`。
- JSON の2つはスクリプトで書いた後、記録の道具で `stage` の値を直して書き込みを残した。

## Step 33: 統合の前の関門（2026-10-13）

- `verify`（README の環境変数つき、`:backend:cleanTest :backend:cleanIntegrationTest` つき、20:53:58〜21:08:31、14 分 32 秒）: BUILD SUCCESSFUL。バックエンドの単体 2,082 件・結合 1,165 件（失敗・SKIPPED 0）。画面のテストは 183 ファイル・1,558 件、行 95.27%・分岐 90.19%。Gitleaks は no leaks found。入口は 158.3 KB。`osvScan` は UP-TO-DATE（Step 5 の `--rerun-tasks` の結果「失敗の条件 0 件」から lockfile は変わっていない）。
- `./gradlew e2eTest`（21:08:55〜21:19:24）: 010〜170 の 230 件すべて通過（flaky なし）。報告の部品は「見つかった件数 0」（添付 301）。報告のフォルダーは消した。
- 手元の確かめ（Q7: A、scratchpad の `pw/tests/manualcheck.scratch.ts`。リポジトリに足していない）: 6項目すべて通過（21:19:42〜21:19:57）。中身は code-summary.md 8節。
- `pmset -g log`: 20:53〜21:20 にスリープ・DarkWake なし。
- 統合（Step 34）の前に止める。
