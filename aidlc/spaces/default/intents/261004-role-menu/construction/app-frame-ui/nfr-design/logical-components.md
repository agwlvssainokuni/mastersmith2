# 論理の部品 — U7 app-frame-ui

## 出典

- この単位の承認済みの NFR 要件 `construction/app-frame-ui/nfr-requirements/` の `performance-requirements.md`（NFR2.2・NFR2.5〜NFR2.12）・`security-requirements.md`（NFR1.1・NFR1.4・NFR1.6〜NFR1.10）・`tech-stack-decisions.md`（NFR4.1〜NFR4.5・NFR6.1〜NFR6.8、受け入れた制約、上流との差）。
- ui の単位は scalability・reliability・observability の要件を作らない（NFR 要件の段の `produces_kinds`）。そのため、段の定義が必須とするそれらの入力は無く、上の3つを入力にする。
- 承認済みの機能設計 `functional-spec.md`（1.1・1.3・D1〜D30・6節）と `frontend-components.md`（1節〜12節）。
- `contract-summary.md`（C2・C8・C9）と `components.md`（AppFrame・MyPermissionsUi・TablePlaceholderUi・SharedTreeView・ApiClient）。
- この段の答え: Q1 A・Q2 A。まとめの確認は Looks correct。
- 統合の点（読み取りだけ）: `construction/role-admin-ui/nfr-design/logical-components.md`（140・150 の案、`E2EF_`、data router）、`construction/navigation/nfr-design/logical-components.md`（メニューと置き場の API）。
- make-you-chic-ui: 固定先 e82b651（`vendor/make-you-chic-ui`）と、上流のリポジトリの 5bf1ffe（`git show` の読み取りだけ）のソース。

## 1. 部品の一覧

| ID | 部品 | 置き場 | 受け持つ要件 |
|---|---|---|---|
| L1 | 作業ロールの API と応答の写し | `app/work-role/workRoleApi.ts` | NFR1.4・NFR2.2 |
| L2 | 業務のメニューの API と応答の写し | `app/navigation/navigationApi.ts` | NFR1.4・NFR2.2 |
| L3 | 作業ロールの状態（`WorkRoleProvider`・`useWorkRole`）と切り替えの部品（`WorkRoleSwitcher`） | `app/work-role/` | NFR2.2・NFR2.9・NFR2.11・NFR4.1・NFR4.2 |
| L4 | 読み直しのきっかけ（`useReloadTriggers`）と業務のメニューの状態（`BusinessNavigationProvider`） | `app/navigation/` | NFR2.2・NFR2.9 |
| L5 | 区画を作る純粋な関数（`buildNavSections`） | `app/navigation/buildNavSections.ts` | NFR1.1・NFR1.7・NFR4.2・NFR6.2 |
| L6 | 置き場の道（`tablePlaceholderPath`） | `app/navigation/tablePlaceholderPath.ts` | NFR1.6・NFR1.7・NFR6.2 |
| L7 | 開閉の状態の保存（`navExpansion`） | `app/navigation/navExpansion.ts` | NFR1.6 |
| L8 | 骨組みの配置（`ShellLayout`）とホームの案内（`HomePage`） | `app/layout/`・`app/pages/` | NFR4.1〜NFR4.3 |
| L9 | 置き場の画面（`TablePlaceholderPage`・`useTableAccess`・`tableAccessApi`） | `features/tables/` | NFR1.1・NFR1.4・NFR2.9 |
| L10 | 自分の権限の画面（`MyPermissionsPage`・`useMyPermissions`・`myPermissionsApi`）〔Should〕 | `features/mypermissions/` | NFR1.4・NFR2.9・NFR6.8 |
| L11 | ログアウトの印（`logoutIntent`）とログアウトの画面（`LogoutPage`） | `features/auth/` | NFR1.6・NFR1.8 |
| L15 | 登録の型と骨組みの口の変更（`beforeNavigate`・`clearLogoutIntent`・`useClearLogoutIntent`） | `app/registry/`・`app/layout/ShellLayout.tsx`・`app/login-state/LoginStateGate.tsx`・`app/routing/AppRouter.tsx` | NFR1.8・NFR1.10 |
| L12 | E2E の補助: ユーザーメニューの開き口（`userMenu.ts`） | `e2e/support/` | NFR6.7 |
| L13 | E2E の補助: U7 の API の差し替え（`frameApiRoute.ts`）と見本の木（`navTreeFixtures.ts`） | `e2e/support/` | NFR2.8・NFR2.12・NFR4.4 |
| L14 | E2E のファイル: U7 の検査（160、本数に数えない）と I の流れ（170） | `e2e/` | NFR1.8・NFR1.9・NFR2.5〜NFR2.8・NFR4.4・NFR4.5・NFR6.3 |

- 部品ごとにテストを対象と同じ場所に置く（4節）。フロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）は今の設定のまま、除外を足さずに満たす（NFR6.4）。

## 2. make-you-chic-ui の部品の口（ソースで確かめた）

### 2.1 5bf1ffe の `AppShell`・`Sidebar`・`Topbar`

- `AppShell` の口: `navSections?: SidebarNavSection[]`・`navExpandedIds?: Set<string>`・`onNavExpandedChange?: (ids: Set<string>) => void`・`navLabels?: Partial<SidebarLabels>`・`topbarEnd?`・`user?`・`userMenuItems?`。`navSections` があれば `navItems` は使われない。
- `SidebarNavSection` は `{ id; heading?; items }`。`items` が空の区画は描かれない。見出しは `h2` で、区画の `ul` を `aria-labelledby` で結ぶ（畳んだ状態では見出しを描かないが `aria-labelledby` は残る。受け入れた制約）。
- `SidebarNavItem` は `{ id; label; icon?; href?; onClick?; current?; children? }`。
  - `href` があれば `<a>`（`aria-label` と `title` に `label`、`current` なら `aria-current="page"`）、無ければ押せない `<span>`。
  - 子があれば開閉のボタン（`aria-expanded`・`aria-controls`、名前は `collapseGroup`・`expandGroup`）。開いたまとまりにだけ子の `ul`（id は `aria-controls` の値）を描く。
  - `data-testid` は `sidebar-nav-<id>`・`sidebar-toggle-<id>`。
- 畳んだ状態では1段目だけを描き、子を持つ1段目はボタン（押すとサイドバーを開いてその項目を開く）。
- `Topbar` の畳むボタンの名前「サイドバーを開く」「サイドバーを折り畳む」とユーザーメニューのトリガーの名前「〇〇のメニュー」は日本語の固定（追加の依頼 `make-you-chic-ui-request-2.md`）。
- 2つの版の間で `package.json`・`package-lock.json`・`LICENSE`・`Icon` に変更は無い。

### 2.2 e82b651 から変わらない部品

- `Dropdown`: 項目は `label`・`href?`・`onClick?`・`disabled?`・`description?`。トリガーには `data-testid="dropdown-trigger"`・`aria-haspopup`・`aria-expanded`・`onClick` を上書きして付ける。区切り線・まとまりは無い。
- `Toast`: 入れ物は `role="status"`・`aria-live="polite"`・`data-testid="toast-container"`。既定 4 秒で消える。
- `Tooltip`・`Alert`・`Card`・`Button`・`Badge`・`Table` は既存のまま使う。

## 3. 依存の向きと骨組みの並び

- 骨組みの並び: `… → FeatureRegistryProvider → AdminForbiddenProvider → WorkRoleProvider → BusinessNavigationProvider → AppRouter`（機能設計 D2）。`main.tsx` は B8 で data router（`createBrowserRouter`）に替わる前提で、U7 は `main.tsx` を変えない。
- 依存の向き:
  - `features/tables`・`features/mypermissions` は `app/work-role`・`app/navigation`・`shared/` を読む。互いに読まない。
  - 骨組み（`app/`）は `features/` を import しない。ログアウトの印を骨組みが消す口（`clearLogoutIntent`）は、下の「登録の型と骨組みの口の変更」の経路で渡す。
  - `shared/tree` は S8 だけが使う。
### 登録の型と骨組みの口の変更（承認の場の直し R-01・R-02）

今のコードで確かめたこと:
- `UserMenuItemRegistration`（`app/registry/types.ts`）は、`action` の項目と `path` の項目のどちらか一方で、`path` の項目に移る直前の処理を付ける口は無い。`validateRegistrations` は「`action` と `path` のどちらか一方だけ」と、`path` が登録済みの道と完全に一致することを確かめる。
- `LoginStateProvider` は `getLoginState` と任意の `subscribe` だけを持つ。`LoginStateGate` は提供元から得たログイン状態だけを文脈で渡し、ほかの口を読まない。
- 今のログアウトの項目（`features/auth/registration.ts`）は `action` で `authSession.logout()` を呼ぶ。`useLogout` は今のコードには無く、cross-cutting の機能設計（`frontend-components.md` 4節）が B2 で `LoginStateProvider` の任意の `logout` と `LoginStateGate` の `useLogout()` を足す設計である（計画の最初に、B2 で入った形を確かめる）。

U7（B9）で変えるもの（どれも足すだけの互換の変更）:

| 置き場 | 変更 | 確かめ |
|---|---|---|
| `app/registry/types.ts` | `path` の項目に任意の `beforeNavigate?: () => void` を足す（`action` の項目は `beforeNavigate?: never`）。`LoginStateProvider` に任意の `clearLogoutIntent?: () => void` を足す | 型の検査 |
| `app/registry/validateRegistrations.ts` | `beforeNavigate` は `path` の項目だけに許し、関数でなければ問題として起動を止める | `validateRegistrations.test.ts` に、`path` の項目の関数は通る・`action` の項目に付けると止まる・関数でない値は止まる、を足す。既存の検査のテストが通る |
| `app/layout/ShellLayout.tsx` | ユーザーメニューの `path` の項目を選んだとき、既定の移動を止め、`beforeNavigate` があれば呼んでから `navigate(path)` する（今の `onClick` に1行足す） | `ShellLayout.test.tsx` で、`beforeNavigate` が移る前に1回呼ばれる |
| `app/login-state/LoginStateGate.tsx` | 提供元の `clearLogoutIntent` を別の文脈で渡し、`useClearLogoutIntent()`（無ければ何もしない関数を返す）を出す。B2 で入る `useLogout` と同じ形にそろえる | `LoginStateGate.test.tsx` で、提供元の口が呼ばれる・無いときは何もしない |
| `app/routing/AppRouter.tsx` | 道が `/logout` 以外に変わったとき、`useClearLogoutIntent()` の関数を呼ぶ（`security-design.md` 5.1） | `AppRouter.test.tsx` |
| `features/auth/registration.ts`・`loginStateProvider.ts` | ログアウトの項目を `{ id: 'auth-logout', labelKey: 'auth.menu.logout', path: '/logout', beforeNavigate: markLogoutIntent, order: 100 }` にし、route `/logout`（LOGGED_IN・STANDALONE、`lazy`）を足す。提供元に `clearLogoutIntent: consumeLogoutIntent` を渡す | `features/auth` の登録のテストを直す（NFR6.7） |


## 4. テストの置き場（NFR6.1・NFR6.2・NFR6.4・NFR6.7）

- 部品ごとのテスト（Vitest＋Testing Library＋user-event＋vitest-axe、部品ごとに axe を1件、描画の後の値は `waitFor`、説明文は英語）:
  - L1〜L2・L9〜L10 の `*Api.test.ts`（送る形）。
  - L3 の `WorkRoleProvider.test.tsx`・`WorkRoleSwitcher.test.tsx`、L4 の `BusinessNavigationProvider.test.tsx`。
  - L7 の `navExpansion.test.ts`、L8 の `ShellLayout.test.tsx`・`HomePage.test.tsx`。
  - L9 の `TablePlaceholderPage.test.tsx`、L10 の `MyPermissionsPage.test.tsx`、L11 の `logoutIntent.test.ts`・`LogoutPage.test.tsx`（StrictMode で包む）。
- 性質ベースのテスト（fast-check、失敗時の種を記録）: L5 の `buildNavSections.property.test.ts`（機能設計 6.2 の性質 1〜7）と、L6 の `tablePlaceholderPath.test.ts`（往復）。
- 既存のテストの書き換え（NFR6.7）:
  - `app/layout/ShellLayout.test.tsx`・`app/routing/AppRouter.test.tsx`: `sidebar-nav-<href>` から `sidebar-nav-<id>` かリンクの名前へ。
  - `app/navigation/navigationItems.test.ts`: 平らな一覧から区画へ。
  - `features/auth` の登録のテスト: ログアウトの項目が action から `/logout` の path と印へ。
  - E2E の `support/registeredUser.ts`（`openUserMenuItem`）と `080-preferences-accessibility.e2e.ts` の開き口: L12 の `userMenu.ts` へ。入れ物 `work-role-switcher` の外の `dropdown-trigger` を探す（`app-shell` の中で、`[data-testid="work-role-switcher"]` の子孫でないもの）。
  - 書き換えた後も、確かめていた中身を減らさない。

## 5. 実際のブラウザの検査（160、NFR4.4・NFR4.5、Q2 A の R-05・R-09）

- ファイル: `frontend/e2e/160-app-frame-accessibility.e2e.ts`（名前はコード生成で決めてよい）。E2E の本数に数えない。
- 状態と見本:
  - 5 段まで開き、40 文字の名前の項目を含むサイドバー（今の項目が末端）。
  - 開いた作業ロールの Dropdown（ロール3つ・40 文字のロール名）と、ロールが1つ・無いの開いた一覧。
  - S2 の準備中と権限なし。S8 の木を開いてカラムの表を出した状態（cross-cutting の NFR4.5）。
  - ホームの案内（設定なし・表示なし・読み込みの失敗）。畳んだサイドバー。
  - API は L13 の差し替えの口で返す。見本に画面の側の型を付ける。
- 組: 表示の設定の 20 組すべて（`support/displayCombos.ts`）。
  - **組ごとに1つのテスト**にし、1つのテストの中で状態を順にたどる（R-09）。B9 の計画で1組あたりの時間を見積もり、ファイル全体が 10 分を超える見込みなら、状態の群（サイドバーと作業ロール／S2・S8・ホーム）で2つのファイル（160・161）に分ける。分けても本数には数えない。
- 合格: axe の違反 0 件、`REQUIRED_RULES` が流れた、画面全体の横のはみ出しが無い、開いた Dropdown と 5 段のサイドバーの矩形が表示の中に収まる。
- 幅（NFR4.5）: 360px・768px・1280px を既定の1組で確かめる。**加えて、悪い側の1組（文字の大きさ lg・テーマ dark・360px）を1つ足す**（R-05）。
  - 作業ロールの見える文字が 768px 未満で縮み、読み上げの名前は変わらない。
  - S8 が 768px 未満で縦に積まれる。
  - サイドバー（幅固定の 220px）と画面全体に横のはみ出しが無い。
- 畳んだ状態で 5bf1ffe の `aria-labelledby` が違反になったときは、除外を足さずに、その時点で依頼者に諮る。
- 測りのテスト（`performance-design.md` 4節）もこのファイルの中に置く。
- 文言の言語（NFR4.3）は、英語の表示の組で `navLabels` の名前が英語になることを、画面のテストと 160 の英語の組で確かめる。

## 6. 流れの E2E（170、NFR6.3）と F の流れへの追加

### 6.1 I の流れ（`frontend/e2e/170-menu-navigation-flow.e2e.ts`、名前はコード生成で決めてよい）

- 番号: U6 の検査 140・F の流れ 150 の後。DSL を自分で適用するほかのファイル（040・150）より後に流れる。計画の最初に、`frontend/e2e/` の番号と U6 の案を突き合わせて確定する。
- 前提は API で自分で作る（前のテストの状態に頼らない、初期管理者を変えない）。
  1. 管理者のトークンで、版 2 の DSL（スキーマ・テーブル・メニューの名前に接頭辞 `E2EI_`。5 段の項目・40 文字の名前・末端がテーブル T1・NONE にするテーブル T2）を投入して適用する。後始末はしない。
  2. ロール（名前は走らせるごとに一意）を作り、T1 を READ、T2 を NONE にする。
  3. 招待から登録まで済ませた利用者を作り、そのロールを割り当てる。作った値は `secretValues.ts` の口で書き、読み戻して揃わなければ失敗にする（`security-design.md` 6節、R-07）。
- 画面で確かめる:
  4. その利用者でログインし、5 段を開閉のボタンで開いて末端の T1 を選ぶ。
  5. 準備中の画面の見出しに T1 の表示名が出て、末端の項目に `aria-current="page"` があり、祖先が開いている。
  6. 再読み込みしても 5 と同じ（開閉の保存と祖先の展開）。
  7. T2 の置き場の道を直接開くと、権限なしの表示で T2 の表示名が出ない。
  8. `GET /api/me/navigation`・`GET /api/me/table-access` の本物の応答の項目の名前と型が、L13 の見本の型と一致する。
  9. ログアウトの確かめ（`security-design.md` 5.3 の 1〜5）。最後に `sessionStorage` の開閉の鍵が無いことを確かめる（R-04）。
- 時間の上限は、既存の流れの E2E（090・110）と同じ形で `test.setTimeout` で延ばし、値は既存に合わせる。

### 6.2 F の流れ（150、U6 のファイル）に B9 で足す部分

- 2つ目のロール（T を設定しない）を作って同じ利用者に割り当て、利用者でログインしてトップバーに2つのロールがあることを確かめる。
- トップバーで2つ目に切り替え、Toast の文と、サイドバーから T の項目が消えることを確かめる。1つ目に戻すと T の項目が出る。
- `GET /api/me/work-role` の本物の応答と見本の型の一致を確かめる。
- F の流れの DSL の前提は、U6 の NFR 設計のとおり始めに自分で適用する（`E2EF_`）。U7 の I の流れ（`E2EI_`）と名前が重ならない。

## 7. 依存と固定先の更新（NFR6.5・NFR6.6・NFR6.8、Q2 A の R-08）

- 新しい npm の依存は足さない（NFR6.5）。
- 固定先の更新（NFR6.6）は B9 の最初の専用のコミットで行い、前後のハッシュ・2つの版の `git diff --stat`・`vendorInstall`・OSV-Scanner・`./gradlew verify` の結果を記録する。追加の依頼が入っていればその版にする。
- 自分の権限の応答の型（NFR6.8）は、B9 の計画の前に U4 の B5 の計画・実装で確かめて計画に書き留める。**確かめられなかったときは、S8（Should）を B9 から外すかを、計画の承認で依頼者に諮る**（R-08）。L10 の `parseMyPermissions` は、崩れた本文を通信の失敗として扱う。

## 8. 上流との差

| # | 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|---|
| (a) | `tech-stack-decisions.md` の NFR4.5（読み直しの R-05） | 幅の確かめは既定の1組だけ | 悪い側の1組（lg・dark・360px）を足す（5節） | Q2 A |
| (b) | `tech-stack-decisions.md` の NFR4.4（読み直しの R-09） | 20 組 × 約 10 の状態の時間の見積もりが無い | 組ごとに1つのテスト。計画で見積もり、10 分を超える見込みなら2つのファイルに分ける（5節） | Q2 A |
| (c) | `tech-stack-decisions.md` の NFR6.8（読み直しの R-08） | 型が確かめられなかったときの扱いが無い | S8 を外すかを計画の承認で諮る（7節） | Q2 A |
| (d) | `tech-stack-decisions.md` の NFR6.3（読み直しの R-06） | I の流れは 040 より後の番号 | 160（検査）・170（I の流れ）。U6 の 140・150 の後で、DSL を適用するほかのファイルより後 | U6 の案と重ねないため |
| (e) | 機能設計 `frontend-components.md` 1節・8節 | 骨組みの並び・ログアウトの画面・E2E の補助の構成。ログアウトの項目は `/logout` の path と印 | ユーザーメニューの `path` の項目に任意の `beforeNavigate` を足し、`LoginStateProvider` に任意の `clearLogoutIntent`、`LoginStateGate` に `useClearLogoutIntent()` を足す（3節、L15）。E2E の補助 L12・L13 を足す | 今の型では `path` の項目に移る直前の口が無く、骨組みが印を消す経路も無いため（承認の場の直し R-01・R-02） |
| (f) | NFR 要件の読み直しの R-04・R-07 | — | `security-design.md` の 8節 (a)・(c) に記録 | Q2 A |

## 9. コード生成（B9）への引き継ぎ

- 作る順は機能設計 9節。最初に固定先の更新の専用のコミット（7節）。
- 計画の最初の手順で次を確かめる:
  - E2E の番号を U6 の案と突き合わせる（6.1）。
  - 自分の権限の応答の型（7節）。
  - B8 の data router の差し替えが入ったか（入っていなければ `BrowserRouter` のまま。D27 の未保存の確かめだけが出ない）。
  - 160 の1組あたりの時間の見積もり（5節）。
- 計画に書くこと:
  - L12 の開き口の関数と、書き換える既存の E2E（4節）。
  - L13 の見本と、前もって文字列にする本文（`performance-design.md` 4.1）。
  - 初回の JavaScript の前後の記録（`performance-design.md` 5節）。
- 計画の最初に、cross-cutting の B2 で `LoginStateProvider` の `logout` と `LoginStateGate` の `useLogout()` が入ったかを確かめ、3節の `useClearLogoutIntent()` を同じ形にそろえる。

## 10. 承認の場の決定と直し

- 決定: 「推奨の案のとおり直す」（NFR 設計の承認の場の Request Changes。直す範囲は各単位の読み直しの Major と、単位の間でそろえる3点）。レビューの記録は `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-design/units/app-frame-ui/6f07949191bc1397/1.json`。
- R-01・R-02 に合わせて、3節に「登録の型と骨組みの口の変更」（今のコードで確かめたことと、U7 が変える型・検査・`ShellLayout`・`LoginStateGate`・`AppRouter`・`features/auth`）を足し、部品 L15 と 8節 (e) を直した。直しの中身は `security-design.md` 9節、R-03 は `performance-design.md` 8節。
