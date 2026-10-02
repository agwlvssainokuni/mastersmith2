# Generation Notes — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 のコード生成の経過・実測の値・計画との差を書く。パスはリポジトリのルートからの相対パスで、画面のファイルは `frontend/src/` の下を `src/...` と書く。計画は `code-generation-plan.md`、テストの手順は `unit-test-instructions.md`。

## 1回目の依頼（Step 1〜Step 8）

### Step 1: 作業の場の確かめと、変更の前の基準

- ブランチ: `git rev-parse --abbrev-ref HEAD` → `feature/260930-user-admin-b2`。
- 先頭: `git rev-parse HEAD` → `4163b67c7a9dbca05b062c1a2b5d7c1d24b496a6`（記録だけのコミット R1）。計画の Step 1 は「先頭が `e20c3b7`」と書くが、同じ Step の3つ目の項目の R1 を依頼者の承認を得てオーケストレーターが先に作ったため、先頭は R1 になっている（下の「計画との差」の N-1）。R1 の親は `e20c3b7`（U2 の C2）で、`git diff --stat e20c3b7 HEAD -- . ':!aidlc'` は空（アプリのソースは `e20c3b7` と同じ）。
- R1: `git show --stat HEAD` の9ファイルはすべて `aidlc/spaces/default/intents/260930-user-admin/` の下（`aidlc-state.md`・監査ログ・U2 の記録5つ・U4 の計画と単位のテストの手順）。`aidlc/` の外のファイルは入っていない。
- 作業フォルダ: `git status --short` は `aidlc/` の下の2つ（監査ログの追記と、この段の `code-generation-questions.md`）だけで、アプリのソースに未コミットの変更は無かった（ワークフローの記録は外して判断した）。
- U2 の単位ごとの squash の条件: U2 の `generation-notes.md` に「直しの後の verify（単位ごとの squash の条件の記録、U4 の計画 9節の Q-C の決定 A で書き足した）」の節があり、BUILD SUCCESSFUL（6 分 41 秒）、バックエンドの単体 1287・結合 587、失敗・エラー・飛ばした 0、そのときのソースは `e20c3b7` と同じ、と書かれていることを確かめた。
- `frontend/playwright-report/`・`frontend/test-results/`: 始めた時点では両方とも無かった（`ls` で確かめた）。
- 画面のテストの基準: U2 の後に画面のソースは変わっていない（上の `git diff` が空）ため、U2 の Step 13 の実測（91 ファイル・736 件、行 97.44%・分岐 92.67%）を基準とした。
- 初回の JavaScript の大きさ（`./gradlew frontendBundleSize`、U4 の変更の前。`frontendBuild` は UP-TO-DATE で、入力は `e20c3b7` と同じ）:

| 入口のファイル | gzip |
|---|---|
| `assets/index-qi16i6xd.js` | 98.7 KB |
| `assets/useTranslation-BhMSudxJ.js` | 16.6 KB |
| `assets/hooks-6Qx7iViM.js` | 7.8 KB |
| 合計 | 123.1 KB |

- Dependabot は計画のとおりここでは確かめていない。U2 の後に新しく開いた High 以上の知らせは、この作業の中では分かっていない。

### Step 2: テストの実行の準備

- `unit-test-instructions.md` 2.1 の1つ目のコマンド（既存の 12 ファイル）: 12 ファイル・147 件すべて成功（変更の前）。
- 2つ目のコマンド `npx playwright test --list e2e/060-invitation-accessibility.e2e.ts`: `Total: 21 tests in 1 file`。報告の部品の「仮の資格情報は含まれていません（3 項目を確かめた）」も出た。
- `--list` の実行で、報告の部品が `frontend/playwright-report/`・`frontend/test-results/` を作った（ファイル 2・ディレクトリ 2）。中を開かずに消し、誰にも共有していない（`gate-decisions.md` の U5 R-02 の決定の扱いに合わせた）。Step 14 の `--list` でも同じことが起きるため、そこでも消して記録する（N-2）。
- 実行のコマンドは `unit-test-instructions.md` の 2節のとおり（`frontend/` で `NODE_OPTIONS=--no-experimental-webstorage npx vitest run <ファイル・フォルダー>`、範囲を絞るときは `--coverage` を付けない）。

### Step 3: 判定と読み直しの口（ApiClient）— 実装

- 新しい `src/shared/api-client/adminForbidden.ts`: `ADMIN_API_PREFIX`（`/api/admin/`）・`ACCESS_DENIED`・`FORBIDDEN_STATUS`（403）と `isAdminForbidden(path: string, error: unknown): boolean`（SD 2.2 の形）。`src/app`・`features` を読まない。
- `src/shared/api-client/apiClient.ts`: `refreshSessionOnce()` を足し、中で今の `refreshOnce` を呼ぶだけにした。先頭の説明文に1行足した。`apiFetch`・`send`・`needsRefresh`・`TOKENLESS_API_PATHS`・`resetApiClient` は変えていない。

### Step 4: 判定と読み直しの口 — テスト

- `src/shared/api-client/adminForbidden.test.ts`（新しい、8件）: 例 6件（true の例、管理の外・似たパス・`/api/admin`、code の無い・違う 403、ほかの状態と文字の 403、通信の失敗、知らない値）と fast-check の性質2つ（パス・状態・code・`kind` の組で3つがそろうときだけ true、`fc.anything()` と任意のパスで例外なく真偽値）。先頭に種の再現の仕方を書いた。
- `src/shared/api-client/apiClient.test.ts`（足す、4件、`describe('apiClient refreshSessionOnce')`）: 登録された更新を呼び結果を返す、401 の更新と同時に呼んでも更新は1回、登録が無ければ false で `fetch` を呼ばない、`refreshSessionOnce` は要求を送らない。既存の 16 件は変えていない。
- 実行（2.2）: 3 ファイル・33 件すべて成功（`adminForbidden.test.ts` 8・`apiClient.test.ts` 20・`apiClient.download.test.ts` 5）。

### Step 5: 骨組みの状態と表示 — 実装

- 新しい `src/app/admin-forbidden/forbiddenHeading.ts`: `forbiddenHeadingKey(pathname, registrations)` と、共通の見出しの鍵の定数 `ADMIN_FORBIDDEN_HEADING_KEY`（N-3）。
- 新しい `src/app/admin-forbidden/AdminForbiddenProvider.tsx`: FC 3.3 の表と断片のとおり（`forbidden`・`seenPath` の `useState`、描画の中での捨て方、`currentPathRef`・`forbiddenRef` を `useLayoutEffect` で新しくする、`inFlightRef`、依存なしの `report`、状態用と関数用の2つの context）。`useAdminForbidden` は `useCallback`（依存は `report` と画面の URL）。Provider の外では2つの口とも例外（計画の D-5）。`await`・`setTimeout`・`setInterval`・`Date`・`console` は無い。
  - `useAdminForbidden` は、hooks の呼び出しの順を変えないため、`useContext`・`useLocation`・`useCallback` を呼んだ後で Provider の外かを判定して例外にする（react-hooks の決まり）。
  - 読み直しの約束は `refreshSessionOnce().finally(...)` で、結果を待たない。登録される `features/auth` の `refresh` は失敗を `false` で返し、約束を拒否しない（`authSession.ts` の `refresh` を読んで確かめた）。
- 新しい `src/app/admin-forbidden/AdminForbiddenView.tsx`・`AdminForbiddenView.css`: `section`（`aria-labelledby`、`data-testid="admin-forbidden-view"`）、`h1`（`tabIndex={-1}`、`data-testid="admin-forbidden-heading"`、見出しの id は `useId`）、`Alert variant="info"` の中に文言の段落と `Link`（`to={HOME_PATH}`、`data-testid="admin-forbidden-home-link"`）。作られたときだけ（`useEffect`、依存なし）見出しへフォーカス。CSS は `Page.css` の `.page`・`.page-heading` と同じ値の自前の組（`.admin-forbidden`・`.admin-forbidden-heading`・`.admin-forbidden-message`・`.admin-forbidden-home-link`）で、リンクの色は `.page-link` と同じ `--color-primary-subtle-text`（Alert の info の背景 `--color-primary-subtle` の上の文字用の色）。
- `src/app/i18n/messages/ja.ts`・`en.ts`: `adminForbidden.heading`（管理／Administration）・`adminForbidden.message`・`adminForbidden.homeLink` を足した（FS 7節の文言のとおり）。
- 型の検査・oxlint・ESLint・Stylelint を通した（Stylelint の `comment-empty-line-before` を1か所直した）。

### Step 6: 骨組みの状態と表示 — テスト

- `forbiddenHeading.test.ts`（新しい、5件）: 登録の項目の鍵、`visibleWhen: 'ADMIN'` の項目、URL の引数を持つ path、共通の鍵（登録に無い・途中までしか合わない URL）、性質1つ（登録に無い URL はいつも共通の鍵）。先頭に種の再現の仕方を書いた。
- `AdminForbiddenProvider.test.tsx`（新しい、12件）: S6 と読み直し1回、同じ URL の重なりで1回、`false` の失敗で何も変わらない、URL が変わると消える、Provider の外で例外、R-02 の3点（A → B → 戻るで A は中身・B への最初の描画でも S6 を描かない／外れた後に A の関数へ渡した 403 で B は S6 にならず更新は呼ぶ／関数の同一性とそれを依存にした `useEffect` が繰り返されない）、読み直しが終わる前の結び付かない 403 で1回（終わった後はまた呼ぶ）、保留の更新でも `report` の直後に S6（NFR9.1）、偽の時計を 10 分進めても更新は1回（NFR9.2）、`detail` の目印が画面に無く `console` の5つの関数が呼ばれない（NFR3.1）。
- `AdminForbiddenView.test.tsx`（新しい、7件）: ja の文言と見出し・リンク、en の文言、共通の見出し「管理」、Alert が `role="status"`（と `section`・`h1` の名前）、見出しへのフォーカス（`waitFor`）と Tab・Enter でホームへ、`console` が呼ばれない、vitest-axe の違反 0。
- `src/app/i18n/messages.test.ts`（足す、1件）: 3つの鍵が ja・en にあり空でない。
- Step 6 の時点では `renderWithProviders` が Provider を持たないため、2つの部品のテストは計画のとおりテストの中で `AdminForbiddenProvider` を置いて描き、Step 7 の後に `renderWithProviders` だけを使う形に直した。
- テストの部品 `Probe` が口の関数と画面の移動の関数を外へ渡す代入は、ESLint の `react-hooks/globals`（描画の中で外の変数に代入しない）に当たったため、`useLayoutEffect` の中で代入する形にした。描画ごとの値の記録（配列への追加）は描画の中のまま。
- 実行（2.3）: 4 ファイル・30 件すべて成功（Provider 12・View 7・見出し 5・文言 6）。
- 確かめの確かめ（作業の中だけの一時の変更。元に戻したことを `git diff` で確かめた）: Provider の捨て方を `useLayoutEffect` に替え、出すかの判定から「今のパスと同じとき」を外すと、R-02 の「A → B → A」のテストが落ちることを確かめた。

### Step 7: 振り分けと置き換え — 実装

- `src/app/routing/decideRoute.ts`: `{ kind: 'ADMIN_FORBIDDEN'; route: RouteRegistration }` を足し、`ADMIN` の画面でログインしていて管理者でないときに返す。ログインしていないとき・登録に無い URL は今のまま。
- `src/app/routing/AppRouter.tsx`: `ADMIN_FORBIDDEN` のとき `Routes` → `Route path={route.path}` → `ShellLayout` → `AdminForbiddenView` を描く（SCREEN と同じ形の木、R-05）。
- `src/app/layout/ShellLayout.tsx`: `useIsAdminForbiddenHere()` が true なら `children` の代わりに `AdminForbiddenView`。サイドバー・上の帯・ユーザーメニューは今のまま。
- `src/app/App.tsx`: `AdminForbiddenProvider` を `FeatureRegistryProvider` の内側・`AppRouter` の外側に置き、先頭の説明文の並びに足した。
- `src/app/testing/renderWithProviders.tsx`: 同じ位置（`FeatureRegistryProvider` の内側）に `AdminForbiddenProvider` を置き、`ui` と `LocationProbe` を包んだ。口（`RenderOptions`）は変えていない。
- Step 6 の2つのテストの仮の置き方を外した。
- この時点の `src/app src/shared/api-client` の実行で、書き換えの範囲の2件（`decideRoute.test.ts`・`AppRouter.test.tsx` の管理者でない利用者の確かめ）だけが落ちた（想定どおり、Step 8 で書き換える）。

### Step 8: 振り分けと置き換え — テスト

- `decideRoute.test.ts`: 書き換え1件・足す2件（下の書き換えの一覧）。足したもの: ログインしていない利用者の管理の画面はログインの画面へ（登録が無ければログイン用のレイアウト）、管理の下の登録に無い URL は管理者でも管理者でなくても `NOT_FOUND`。
- `AppRouter.test.tsx`: 書き換え1件・足す3件（`describe('admin forbidden (U4)')`）。管理者でない利用者の管理の画面では、開いたときに管理の API を呼ぶ画面の部品が作られず、偽物の `fetch` が呼ばれない（NFR1.3）。起動の時の更新で印が外れた状態（管理者でないログイン状態）から描くと、管理のメニューが無く S6 で見出しは項目の名前（AC2.2.2、R-06）。ForbiddenByApi（画面が `report` で S6）から ForbiddenByRoute（提供元が管理者でない状態を知らせる）へ移っても、見出しの要素と `app-shell` の要素が同じもの（`toBe`）で、フォーカスが見出しに残り、サイドバーの畳んだ状態（`collapsed`）が変わらない（R-05）。サイドバーの開閉は `fireEvent.click` で押し、フォーカスを動かさずに確かめた。
- `ShellLayout.test.tsx`: 足す5件（`describe('admin forbidden (U4)')`）。子の代わりに S6 でサイドバーと上の帯は残る、AC2.2.2 のきっかけごとに管理のメニューが消える（403 の後の読み直し＝偽物の更新が提供元を管理者でない状態にして知らせる・トークンの更新の応答・ログアウトの後のログインし直しの応答、`waitFor`）、S6 の後にサイドバーで管理の外の画面へ移ると S6 が消える（NFR1.4、AC2.2.3）。
- 実行（2.4、`src/app src/shared/api-client`）: 30 ファイル・241 件すべて成功。型の検査・oxlint・ESLint も通った。
- 確かめの確かめ（一時の変更、元に戻した）: `ADMIN_FORBIDDEN` を `Routes` の木の外の `ShellLayout` で描く形にすると、R-05 のテストが落ちることを確かめた。
- 参考: Step 10 の確かめの前に影響を見るため、画面のテストの全体（`npx vitest run`、カバレッジなし）も流した。95 ファイル・783 件すべて成功（U2 の後の 91 ファイル・736 件に、新しい4ファイルと足した 47 件）。`renderWithProviders` に Provider が入っても、ほかの機能のテストは変えずに通っている。正式な確かめは Step 10 と Step 17 で行う。

### 書き換えた既存のテスト（Step 8 の分、NFR9.11）

| ファイル | 元のテストの題 | 書き換えの中身 |
|---|---|---|
| `src/app/routing/decideRoute.test.ts` | `shows not found to logged-in non-admins opening an ADMIN screen and shows it to admins` | 管理者でない利用者は `{ kind: 'ADMIN_FORBIDDEN', route: adminRoute }`、管理者は SCREEN に替えた。中身が変わったため題を `shows the forbidden view to logged-in non-admins opening an ADMIN screen and shows it to admins` に替えた |
| `src/app/routing/AppRouter.test.tsx` | `does not show ADMIN screens to logged-in non-admins`（題はそのまま） | `not-found-page` を待つ確かめを、S6（`admin-forbidden-view`）がアプリシェルの中に出て、`admin-screen` と `not-found-page` が無い確かめに替えた |

Step 10 で書き換える3つの画面のテストと描画の補助は、2回目の依頼で追記する。

### 名指しのテストの件数（Step 1〜8 の時点）

| ファイル | 件数 | 新しい・足す |
|---|---|---|
| `src/shared/api-client/adminForbidden.test.ts` | 8 | 新しい |
| `src/shared/api-client/apiClient.test.ts` | 20 | 足す 4 |
| `src/app/admin-forbidden/forbiddenHeading.test.ts` | 5 | 新しい |
| `src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` | 12 | 新しい |
| `src/app/admin-forbidden/AdminForbiddenView.test.tsx` | 7 | 新しい |
| `src/app/i18n/messages.test.ts` | 6 | 足す 1 |
| `src/app/routing/decideRoute.test.ts` | 11 | 書き換え 1・足す 2 |
| `src/app/routing/AppRouter.test.tsx` | 11 | 書き換え 1・足す 3 |
| `src/app/layout/ShellLayout.test.tsx` | 18 | 足す 5 |

### 計画との差（Step 1〜8）

| ID | 計画の記述 | 実際 | 理由・扱い |
|---|---|---|---|
| N-1 | Step 1: 先頭が `e20c3b7` であることを記録する | 先頭は R1 `4163b67`（親が `e20c3b7`） | 同じ Step の R1 を先に作ったため。アプリのソースは `e20c3b7` と同じことを `git diff` で確かめ、差分が無いことの基準（計画 2.2 の `e20c3b7`）は変えない |
| N-2 | Step 2・14: `--list` はアプリを起動しないとだけ書く | `--list` でも報告の部品が `frontend/playwright-report/`・`frontend/test-results/` を作った | 中を開かずに消し、件数と共有していないことを記録した。Step 14 でも同じ扱いにする |
| N-3 | 4.1: `forbiddenHeading.ts` の export は `forbiddenHeadingKey` | 加えて、共通の見出しの鍵の定数 `ADMIN_FORBIDDEN_HEADING_KEY` を名前つきで export した | テストで鍵の文字を重ねて書かないため。振る舞いは同じ |
| N-4 | 4.1: `AdminForbiddenView.css` は `Page.css` の `.page`・`.page-heading` の形にそろえる | `Page.css` を読み込まず、同じ値の自前のクラス（`.admin-forbidden-*`）を部品と同じ場所の CSS に置いた | 部品と同じ場所に素の CSS を置く決まり（`team.md`）のため。見た目の値は同じ |
| N-5 | Step 6: テストの部品の作り | 外へ関数を渡す代入を `useLayoutEffect` の中に置いた | ESLint の `react-hooks/globals` に当たったため。確かめる中身は変わらない |

このほかに、承認済みの計画・設計と違う作りは無い。

### 依頼者に確かめたいこと（Step 1〜8 の時点）

- 無し（N-1〜N-5 は記録の扱いで、計画の決定を変えていない）。

## 2回目の依頼（Step 9〜）

この回は Step 9〜Step 15 を行った。Step 16（記録とコミットの提案）と Step 17（B2 の関門と統合）は行っていない。コミット・`git add` はしていない。`backend/` と `vendor/` は変えていない。前の回の変更（Step 1〜8）はそのまま残している。

### Step 9: 既存の3つの画面の置き換え — 実装

- 管理の入口: `src/features/admin/adminAreaStatus.ts` の状態の `NotFound` を `Forbidden` に替え、`statusFromError(error, report)` を「`report(error, ADMIN_CHECK_PATH)` が true なら `Forbidden`、false なら `Error`」にした。`FORBIDDEN_STATUS` を消した（計画の D-2）。渡す関数の型 `ReportAdminForbidden` を名前つきで export した（N-10）。`src/features/admin/AdminAreaPage.tsx` は `useAdminForbidden()` の関数を `statusFromError` に渡し、`Forbidden` のときは `null` を描く。`NotFoundPage` の import を消した。確かめの `useEffect` の依存に渡す関数を入れた（画面の URL が変わらない限り同じ関数のため、確かめは繰り返さない。N-11）。
- DSL の管理: `src/features/dsl/useDslAdmin.ts` の `checkForbidden` を「`reportForbidden(error, DSL_API_ROOT)` を返す」`useCallback` にし、定数 `FORBIDDEN`・状態 `forbidden`・戻り値の `forbidden` と、`failureStatus` の import を消した。4つの呼び出しの場所はそのまま。`src/features/dsl/DslAdminPage.tsx` の `state.forbidden` の分かれ道と `NotFoundPage` の import を消した。
- 招待の管理: `src/features/invitation/useInvitationAdmin.ts` に `isForbidden`（`reportForbidden(error, INVITATION_API_ROOT)`）を足し、一覧の読み込み・招待・送り直し・取り消しの4つの失敗の扱いで、`isUnauthorized` と並べて true なら何もせず終える。一覧の読み込みは `!isLatest()` を先に見るため、画面を離れた後・古い読み直しの答えは今までどおり捨て、骨組みへ渡さない（FS 5.2 の「外れた後の結果を捨てる今の仕組み」のまま。N-12）。招待の失敗は `mounted` の確かめの後、`isUnauthorized` より前に見る。先頭の説明文の「403 は一般の 4xx の文言で示す」を直した。`failureMessageKey` は変えていない。
- 3つとも `useAdminForbidden` は `src/app/admin-forbidden/AdminForbiddenProvider` から読む。
- `grep -rn 'NotFoundPage\|FORBIDDEN_STATUS\|state\.forbidden' src/features`（テストを除く）は 0 件。

### Step 10: 既存の3つの画面 — テスト

- `src/features/dsl/testing/renderDsl.tsx`・`src/features/invitation/testing/renderInvitation.tsx` に、3つ目の省略できる引数 `options`（`withShell`・`provider`・`registrations`）を足した。既定は今のまま（ShellLayout なし・文言だけの登録・提供元なし）。型 `RenderDslOptions`・`RenderInvitationOptions` を export した。
- S6 を確かめるテストだけを ShellLayout の中に、本物の登録（`dsl/registration.ts`・`invitation/registration.ts`）と管理者の `fakeProvider` で描いた。見出しは登録の項目の名前（「DSL」「利用者の招待」）。偽物の API を使う DSL・招待の画面は更新を登録していないため、読み直しは false で何もしない。管理の入口は偽物の `fetch` で ApiClient を通し、ApiClient に登録した偽物の更新が1回呼ばれたことを確かめた。
- 実行（2.5）: `src/features/admin src/features/dsl src/features/invitation` は 32 ファイル・265 件すべて成功。`src/features/auth src/features/preferences src/features/registration` は 28 ファイル・243 件すべて成功（変えていない）。
- 確かめの確かめ（作業の中だけの一時の変更。元に戻したことを `grep` と `git diff` で確かめた）: 招待の `isForbidden` を常に false にすると、足した3件が落ちることを確かめた。

### 書き換えた既存のテスト（Step 10 の分、NFR9.11）

| ファイル | 元のテストの題 | 書き換えの中身 |
|---|---|---|
| `src/features/admin/AdminAreaPage.test.tsx` | （描き方） | テストの中の `render` を、`<ShellLayout><AdminAreaPage /></ShellLayout>` を管理者の `fakeProvider` で描く形に直した（R-03）。`beforeEach` の偽物の更新を `vi.fn` にして回数を数えられるようにした |
| 同 | `hides the content and tells assistive technology while the check is running`（題はそのまま） | 提供元を渡すと骨組みはログイン状態を読んでから描くため、最初の `getByTestId` を `await findByTestId` にした（N-8） |
| 同 | `shows the not found screen when the check answers 403` | S6（`admin-forbidden-view`）・見出し「管理」・文言が出て、画面の中身・「ページが見つかりません」・誤りの文言が無く、更新が1回呼ばれることを確かめる形にした。題を `shows the forbidden view and reads the login state again when the check answers 403` に替えた |
| 同 | `shows a generic message when the server fails`（題はそのまま） | S6 が出ないことの確かめを1行足した |
| 同 | `calls the check again on every display and keeps no result in memory`（題はそのまま） | 2回目の 403 の確かめを `not-found-page` から S6 に替えた |
| `src/features/admin/adminAreaStatus.test.ts` | `maps 403 to the not found screen` | 本物の `isAdminForbidden` を通す `report` を渡し、`ACCESS_DENIED` の 403 が `Forbidden` で、`report` が `ADMIN_CHECK_PATH` で1回呼ばれることを確かめる形にした。題を `maps a 403 ACCESS_DENIED to the forbidden state and passes the check path` に替えた |
| 同 | `always maps 403 to the not found screen whatever the code is`（性質） | 「`ACCESS_DENIED` 以外の 403（code の無いものを含む）も `Error`」に替えた。題を `maps every 403 without ACCESS_DENIED to the generic error` に替えた（NFR9.9） |
| 同 | `maps a server failure …`・`maps a network failure …`・`maps every other status …`・`maps an unknown value …`（題はそのまま） | `report` を渡す形にした。`maps every other status …` は code に `ACCESS_DENIED` を付けても 403 以外は `Error` の形にし、`maps an unknown value …` の許す値を `Error`・`Forbidden` にした |
| `src/features/dsl/DslAdminPage.test.tsx` | `shows the not found screen when the server answers 403` | ShellLayout の中に描き、状態の読み込みの 403・`ACCESS_DENIED` で S6（見出し「DSL」）が出て、画面の中身・Alert・「ページが見つかりません」・確かめの表示が無いことを確かめる形にした。題を `shows the forbidden view when the server answers 403 ACCESS_DENIED` に替えた |
| `src/features/invitation/InvitationAdminPage.test.tsx` | `shows the failed list with the general message, cannot invite, and loads again`（題はそのまま） | 一覧の失敗を `apiError(403, 'ACCESS_DENIED')` から code の無い `apiError(403)` に替えた（一般の文言のまま） |
| 同 | `shows the general message inside the dialog for 403, unknown codes, 5xx and network failures`（題はそのまま） | 1つ目の失敗を code の無い `apiError(403)` に替えた |
| 同 | `does not reload after 403, 5xx or network failures and keeps focus on the resend button`（題はそのまま） | 1つ目の失敗を code の無い `apiError(403)` に替えた |
| `src/features/invitation/failureMessage.test.ts` | `uses the general client message for an unknown code or no code on a 4xx`（題はそのまま） | `response(403, 'ACCESS_DENIED')` を `response(403, 'ORIGIN_NOT_ALLOWED')` と `response(403)` に替えた（`ACCESS_DENIED` はもう届かない、FC 6.2） |

足したテスト（Step 10）:

| ファイル | 題 |
|---|---|
| `src/features/dsl/DslAdminPage.test.tsx` | `closes the open confirmation and shows the forbidden view when an operation answers 403`（適用の確かめの表示を開いた状態の 403 で表示が閉じて S6、状態とプレビューは読み直さない） |
| 同 | `keeps the usual failure handling for a 403 without ACCESS_DENIED`（code の無い 403 は今の誤りの扱い、S6 にならない） |
| `src/features/invitation/InvitationAdminPage.test.tsx` | `shows the forbidden view when the list answers 403 ACCESS_DENIED` |
| 同 | `closes the invite dialog and shows the forbidden view when inviting answers 403` |
| 同 | `shows the forbidden view without reloading when resending answers 403` |

### Step 11: 自分の氏名と言語の反映 — 実装

- `src/app/display-settings/displaySettingsStore.ts` に `OwnProfile` の型と `applyOwnProfileFor(binding, profile, current)` を足した。`savedUser` を `binding` に結び付けた「氏名・言語＝渡した値、テーマと文字の大きさ＝`current`」にする。言語が ja・en のときだけ、ブラウザの保存の言語を `writeStoredLanguage` で書き（読み直した今の値の言語だけを置き換える。置き場の `stored` もその返り値にする。N-7）、言語の見せ方だけを捨てる。ja・en 以外は言語を変えず（`current` の言語のまま）、保存も見せ方も触らない。今の口（`applyUserPreferencesFor` など）は変えていない。
- `current` の型を、計画の「テーマと文字の大きさ」から `DisplaySettings`（言語・テーマ・文字の大きさ）にした（N-6）。ja・en 以外のときに `savedUser` の言語を「今の言語のまま」にする値が要るため。渡すのは同じく見せ方を除いた当てている値。
- `src/app/display-settings/DisplaySettingsProvider.tsx`: `decideScreenSettings({ ...input, preview: null, prefersDark: false })` で当てている値を求め、ログイン状態とその値を ref に入れる。ref は依存なしの `useLayoutEffect` で描画の確定ごとに新しくし、描画の中では書かない。`applyOwnProfile` は依存なしの `useCallback` で、呼ばれた時点の ref を読み、ログインしていなければ何もしない（SD 4.2、S-1、R-01）。値（`useMemo`）に `applyOwnProfile` を足した（依存は同一の関数のため、値の作り直しの回数は増えない）。`applyUserPreferences`・`setPreview`・`setLanguage` は変えていない。
- 新しい `src/app/display-settings/useApplyOwnProfile.ts`: `useDisplaySettings().applyOwnProfile` を返す。渡す値の型 `ApplyOwnProfileInput`（`displayName: string`・`language: 'ja' | 'en'`、C4 の型）を export した（N-9）。

### Step 12: 自分の氏名と言語の反映 — テスト

- `displaySettingsStore.test.ts` に5件（`describe('displaySettingsStore applyOwnProfileFor (U4)')`）: 氏名と言語と当てている値のテーマ・文字の大きさが `savedUser` に入る、ブラウザの保存は言語だけ（ほかのタブで変わった文字の大きさの保存の値はそのまま）、言語の見せ方だけを捨てテーマと文字の大きさの見せ方は残す、ja・en 以外は言語・保存・見せ方を変えず氏名だけ、次のログイン状態で捨てられる。
- `DisplaySettingsProvider.test.tsx` に8件（`describe('DisplaySettingsProvider applyOwnProfile (U4)')`）: 氏名・文言・`<html lang>`・`Accept-Language`（偽物の `fetch`）が変わる、テーマと文字の大きさ（ほかのタブで変わった保存の値を含む）が変わらない、`setPreview` で試したテーマが保存されず見せ方を捨てると当てている値に戻る（R-01）、ログインしていないときは何もしない、印が変わらない、描画の時点に取り出した関数を新しいログイン状態の知らせと描画の確定の後に呼ぶと新しいログイン状態に結び付く（S-1、`waitFor`）、ログイン状態・テーマ・言語が変わっても関数が同じもの、ブラウザの保存のどの鍵にも氏名が無い（NFR3.1）。R-03 の隙間のテストは足していない（承認の場の決定）。
- 実行（2.6、`src/app/display-settings src/app/layout/ShellLayout.test.tsx`）: 7 ファイル・86 件すべて成功。
- 確かめの確かめ（一時の変更、元に戻した）: ref を新しくする `useLayoutEffect` を最初の1回だけにすると、S-1 と関数の同一性の2件が落ちることを確かめた。

### Step 13: 実際のブラウザの検査 130 と共有の手伝い・README — 実装

- `frontend/e2e/support/loginPreferences.ts`: 型 `LoginPreferences` に省略できる `displayName?: string` を足し、値が `undefined` の項目を重ねない形（LC 6.2）にした。冒頭の説明に 130 が使うことを書き足した。060 は変えていない。
- 新しい `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts`: `test.describe('130 admin forbidden view accessibility on the built WAR')` の中で `DISPLAY_COMBOS` の組ごとに1つ（20 件）。順は計画の Step 13 の表のとおり（見張り → `prepareCombo` → `routeLoginPreferences`（組の値と2語の氏名）→ `GET /api/admin/check` だけを 403 の見本に差し替え → ログインと組の確かめ → 読み直しの応答の約束を作ってからサイドバーの「管理」→ S6 → 読み直しの応答を待つ → axe・はみ出し・`role="status"`・見出しのフォーカス・Avatar の文字の数 2・`detail` の目印が無い・書き換え 1 以上 → 「ホームへ戻る」でホーム・S6 が消える（全 20 組）→ `networkidle` の後に、除いた表示の件数が 403 を返した回数以下・CSP の違反 0・除いた残りの問題 0・管理の API への GET 以外の要求 0）。見本と2語の氏名（`Probe Sample`）は 130 の中の定数。注記は `axe`（組の名前・違反の件数・規則の名前・incomplete の規則と件数・はみ出し・Avatar の文字の数・書き換えの回数・403 を返した回数・除いた表示の件数）と `admin-forbidden-problems`（最後の件数）の2つで、氏名・メールアドレス・パスワード・トークンを入れない。添付は付けない（N-13）。画面の時間は測らない。
- 実行は Step 17 の `./gradlew e2eTest` で行う（計画の D-3）。この回は型の検査・書式・リンタと `--list` だけを確かめた（Step 14）。
- `README.md`: E2E の表に 130 の行、「130 について」の節（100 の節の後）、DSL の管理画面の節の1文を S6 の説明に直した、「管理の画面の「権限が無い」の扱い（Intent 260930-user-admin の U4）」の節（「DSL の管理画面（U5）」の後・「外部エクスポートの確かめ方」の前。表示・読み直し・判定の置き場・サーバー側が正・自分の氏名と言語の反映の口）を足した。

### Step 14: 画面の静的検査と構造の確かめ

- 型の検査 `npm run typecheck`（`e2e` を含む）: 通った。
- Prettier（2.7 の範囲）: 通った（足したテスト4ファイルを `--write` で整えた後）。oxlint・ESLint（`src/shared/api-client src/app src/features/admin src/features/dsl src/features/invitation e2e`）: どちらも終了コード 0。Stylelint（`src/app/admin-forbidden/**/*.css`）: 0。ライセンスヘッダーの検査: 「すべてのファイルにヘッダーがあります」。除外は足していない。
- `npx playwright test --list e2e/060-invitation-accessibility.e2e.ts e2e/130-admin-forbidden-accessibility.e2e.ts`: `Total: 41 tests in 2 files`（060 が 21 件、130 が 20 件）。報告の部品の「仮の資格情報は含まれていません（3 項目を確かめた）」も出た。
- `--list` が作った `frontend/playwright-report/`・`frontend/test-results/`（ファイル 2・ディレクトリ 2）は、中を開かずに消した。誰にも共有していない（N-2 と同じ扱い）。
- 画面の側の境界（NFR9.12、LC 3.1）:
  - `grep -rn "from '.*features/" frontend/src/app frontend/src/shared`（テストを除く）は 0 件で、`e20c3b7` でも 0 件（U4 の分で増えていない）。
  - `src/shared/api-client/adminForbidden.ts` は import を持たない。`apiClient.ts` の import は `./apiError` だけ（変わらない）。
  - `src/app/admin-forbidden/`・`src/app/display-settings/` に `features/` の import は無い。
  - `features/admin`・`dsl`・`invitation` の本体は骨組みの口を `../../app/admin-forbidden/AdminForbiddenProvider` から読む。ほかの機能を読むのは既存の `registration.test.ts(x)` の6行だけで、どれも `e20c3b7` にあった（U4 で増えていない）。
- 差分が無いこと（`git diff --stat e20c3b7 -- <パス>` が空）: `backend`、`backend/src/main/resources/application.yaml`、`vendor/make-you-chic-ui`、`frontend/package.json`、`frontend/package-lock.json`、`frontend/vitest.config.ts`、`frontend/playwright.config.ts`、`frontend/e2e/support/pageProblems.ts`・`axe.ts`・`displayCombos.ts`・`adminLogin.ts`、`frontend/e2e/030-admin-access.e2e.ts`、`.github`、`docker`、`compose.yaml`、`gradle`、`build.gradle.kts`。

### Step 15: コードのレビューでの確かめ

| 項目 | 確かめた方法 | 結果 |
|---|---|---|
| 判定の1か所（NFR1.2、SD 2.2） | `frontend/src` を `403`・`ACCESS_DENIED`・`FORBIDDEN` で検索（テストと `testing/` を除く） | 状態コードと code を比べているのは `src/shared/api-client/adminForbidden.ts` の `isAdminForbidden` だけ。ほかは説明文、振り分けの `ADMIN_FORBIDDEN`、S6 の見出しの鍵の定数、DSL の無関係な `FORBIDDEN_TAG` だけ。招待の `failureMessageKey` の一般の 4xx の扱いは判定ではない |
| 待たない・重ねない（NFR9.1・NFR9.2、PD 2節・3節） | `AdminForbiddenProvider.tsx` を `await`・`setInterval`・`setTimeout`・`Date`・`console` で検索 | 説明文の「console」の1語だけ。`report` は `refreshSessionOnce().finally(...)` を `inFlightRef` に置くだけで待たない |
| 画面に出す値（NFR3.1、SD 3節） | ソースを読んだ | `AdminForbiddenView` は Props を持たず（`export function AdminForbiddenView()`）、失敗の値を受け取らない。U4 の部品と関数（`adminForbidden.ts`・`forbiddenHeading.ts`・`AdminForbiddenProvider.tsx`・`AdminForbiddenView.tsx`・`useApplyOwnProfile.ts`・`applyOwnProfileFor`）に `console` の呼び出しは無い。`applyOwnProfileFor` がブラウザに書くのは `writeStoredLanguage`（言語だけ）だけ |
| CSP と静的検査（NFR9.5） | `src/app/admin-forbidden` を `dangerouslySetInnerHTML`・`<script`・`style=` で検索、Step 14 | 0 件。外部の資源・埋め込みのスクリプトとスタイルは無い。`application.yaml` に差分は無い |
| 401 とログアウト（NFR1.4、D10） | `git diff e20c3b7 -- frontend/src/shared/api-client/apiClient.ts` | 差分は先頭の説明文の1行と `refreshSessionOnce`（説明文つきで `refreshOnce()` を返すだけ）の追加だけ |
| 130 の報告と資格情報（NFR3.2、SD 5節） | 130 のソースを読み、`requestAdminAccessToken`・`webServer`・`console.` を検索 | どれも使っていない。ログインは `loginAsAdmin`（プロセスの環境変数）。注記・`test.step` の題に氏名・メールアドレス・パスワード・トークンを入れていない。添付は無い。2語の氏名の定数は差し替えの引数にだけ使う。実行の後の json の報告の確かめは Step 17 |
| R4 の戻り方の読み方（SD 8節） | `AdminForbiddenProvider.tsx`・`AppRouter.tsx`・3つの画面を読んだ | S6 の間はコンテンツの領域の画面の部品が外れているため、管理の API を呼ばない。ForbiddenByRoute（`decideRoute` の `ADMIN_FORBIDDEN`）は `AppRouter` が S6 を描くだけで `report` も読み直しも呼ばない（読み直しの契機が無い）。実装のとおりで、実装は変えていない |

### 画面のテストの全体・カバレッジ・静的検査（Step 15 の終わり）

- `npx vitest run`（`frontend/` で `NODE_OPTIONS=--no-experimental-webstorage`、カバレッジなし）: 95 ファイル・801 件すべて成功（U2 の後の 91 ファイル・736 件から、新しいテストのファイル4つと 65 件の増え）。
- `npm run test:coverage`: 95 ファイル・801 件すべて成功、下限（行 80%・分岐 70%）を満たした。全体は 行 97.61%（2414/2473）・分岐 93.01%（1505/1618）（U2 の後は 行 97.44%（2322/2383）・分岐 92.67%（1443/1557））。この実測は手元の名指しでない実行で、正式な記録は Step 17 の `verify` の段で取る。

| ファイル | 行 | 分岐 |
|---|---|---|
| `src/shared/api-client/adminForbidden.ts`（新しい） | 100%（9/9） | 100%（11/11） |
| `src/shared/api-client/apiClient.ts` | 100%（55/55） | 100%（26/26） |
| `src/app/admin-forbidden/forbiddenHeading.ts`（新しい） | 100%（6/6） | 100%（4/4） |
| `src/app/admin-forbidden/AdminForbiddenProvider.tsx`（新しい） | 100%（42/42） | 95%（19/20） |
| `src/app/admin-forbidden/AdminForbiddenView.tsx`（新しい） | 100%（8/8） | 分岐なし |
| `src/app/display-settings/useApplyOwnProfile.ts`（新しい） | 100%（1/1） | 分岐なし |
| `src/app/display-settings/displaySettingsStore.ts` | 97.4%（75/77） | 91.93%（57/62） |
| `src/app/display-settings/DisplaySettingsProvider.tsx` | 100%（79/79） | 97.56%（40/41） |
| `src/app/routing/decideRoute.ts` | 100%（17/17） | 95.65%（22/23） |
| `src/app/routing/AppRouter.tsx` | 100%（14/14） | 100%（8/8） |
| `src/app/layout/ShellLayout.tsx` | 100%（21/21） | 100%（10/10） |
| `src/app/App.tsx` | 93.75%（15/16） | 83.33%（10/12） |
| `src/features/admin/adminAreaStatus.ts` | 100%（1/1） | 100%（2/2） |
| `src/features/admin/AdminAreaPage.tsx` | 94.73%（18/19） | 70%（7/10） |
| `src/features/dsl/useDslAdmin.ts` | 94.78%（218/230） | 77.77%（70/90） |
| `src/features/dsl/DslAdminPage.tsx` | 100%（6/6） | 100%（8/8） |
| `src/features/invitation/useInvitationAdmin.ts` | 93.92%（170/181） | 83.47%（96/115） |

- U4 で作ったファイルは行・分岐とも目安の 90% 以上（`unit-test-instructions.md` 4節）。
- `frontend/coverage/` はもとから無視の対象（`git status --ignored` で `!!`）。
- 2.9 のまとめの実行（`src/shared/api-client src/app src/features/admin src/features/dsl src/features/invitation`）: 62 ファイル・519 件すべて成功。
- 型の検査・oxlint・ESLint・Stylelint・ライセンスヘッダー・Prettier: Step 14 のとおりすべて通った（最後の変更の後に流し直した）。
- 初回の JavaScript の大きさ（`./gradlew frontendBundleSize`、U4 の変更の後。`frontendBuild` を作り直し、`frontend/dist` に U4 の部品が入っていることを確かめた）:

| 入口のファイル | U4 の前（Step 1） | U4 の後 |
|---|---|---|
| `assets/index-*.js` | 98.7 KB | 106.1 KB |
| `assets/useTranslation-*.js` | 16.6 KB | 16.6 KB |
| `assets/hooks-*.js` | 7.8 KB | （入口の別のファイルとしては出ない） |
| 合計（gzip） | 123.1 KB | 122.7 KB |

  - 合計は 0.4 KB 減った。前の `hooks-*.js` の分が `index-*.js` に入った形に見える。`App` が `AdminForbiddenProvider`（react-router の `useLocation` などを使う）を読むようになり、分け方が変わったためと見ているが、確かめていない（見立て）。目安 500KB の内（NFR9.4）。

### 名指しのテストの件数（Step 9〜12 の時点）

| ファイル | 件数 | 新しい・書き換え・足す |
|---|---|---|
| `src/features/admin/AdminAreaPage.test.tsx` | 9 | 描き方と4件の書き換え（件数は変わらない） |
| `src/features/admin/adminAreaStatus.test.ts` | 6 | 書き換え 6（件数は変わらない） |
| `src/features/dsl/DslAdminPage.test.tsx` | 27 | 書き換え 1・足す 2 |
| `src/features/invitation/InvitationAdminPage.test.tsx` | 32 | 書き換え 3か所・足す 3 |
| `src/features/invitation/failureMessage.test.ts` | 6 | 書き換え 1（件数は変わらない） |
| `src/app/display-settings/displaySettingsStore.test.ts` | 11 | 足す 5 |
| `src/app/display-settings/DisplaySettingsProvider.test.tsx` | 23 | 足す 8 |
| `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` | 20（`--list`） | 新しい（実行は Step 17） |

### 計画との差（Step 9〜15）

| ID | 計画の記述 | 実際 | 理由・扱い |
|---|---|---|---|
| N-6 | 4.1: `applyOwnProfileFor(binding, profile, current)` の `current` はテーマと文字の大きさ（FC 3.6 の断片も `{ theme, fontSize }`） | `current` を言語・テーマ・文字の大きさの3つ（`DisplaySettings`）にし、Provider は見せ方を除いた当てている値の3つを渡す | ja・en 以外の言語のとき「言語は変えず氏名だけ」（D14）を `savedUser` で表すのに、今の言語の値が要るため。テーマと文字の大きさの扱い（R-01）は計画のとおり。依頼者に確かめたい（下） |
| N-7 | 4.1: ブラウザの保存は言語だけ（`writeStoredLanguage`） | そのとおり。置き場の `stored` は `writeStoredLanguage` の返り値（読み直した保存の値に言語を重ねたもの）にした | ほかのタブで変わったテーマと文字の大きさの保存の値を、置き場が古い値で持ち続けないため。ログインの後の画面の値には効かない |
| N-8 | 4.2: `AdminAreaPage.test.tsx` は描き方と403の2件を書き換える | 加えて、確かめの途中の表示のテスト1件の最初の確かめを `getByTestId` から `await findByTestId` に替え、500 のテストに S6 が出ない確かめの1行を足した | 提供元を渡すと骨組みがログイン状態を読んでから描くため、同期の確かめでは描かれる前に見てしまう（描き方の書き換えに伴う直し）。確かめる中身は変わらない |
| N-9 | 4.1: `useApplyOwnProfile` は C4 の名前と型 | 渡す値の型を `ApplyOwnProfileInput` として名前つきで export し、置き場には `OwnProfile` の型を置いた | U5 がその型を使えるようにするため。振る舞いは C4 のとおり |
| N-10 | 4.1: `statusFromError(error, report)` | 渡す関数の型 `ReportAdminForbidden` を `adminAreaStatus.ts` から名前つきで export した | 型を1か所で書くため |
| N-11 | 4.1: `AdminAreaPage` は `useAdminForbidden()` の関数を `statusFromError` に渡す | 確かめの `useEffect` の依存に、渡す関数を入れた | react-hooks の決まり。関数は画面の URL が変わらない限り同じため、確かめは表示のたびに1回のまま（既存の `calls the check again on every display …` が通る） |
| N-12 | 4.1: 招待の4か所で `isUnauthorized` と並べて `report` が true なら何もせず終える | 一覧の読み込みは `!isLatest()` を先に見て、画面を離れた後・古い読み直しの答えは骨組みへ渡さない。招待の失敗は `mounted` の確かめの後、`isUnauthorized` より前に見る。DSL も同じく今の `isLatest()`・`mounted` の確かめが先 | 今の捨て方（FS 5.2）のまま、捨てる答えを骨組みへ渡さないため。画面を離れた後の 403 で読み直しが起きない点は W5 の Provider の側の確かめ（`AdminForbiddenProvider.test.tsx`）とは別の、画面の側の今の扱い |
| N-13 | Step 13: 結果を組ごとの注記に残す | 注記を `axe`（検査の時点）と `admin-forbidden-problems`（`networkidle` の後の件数）の2つにした。添付は付けない | 除いたコンソールの表示の件数と残りの問題の件数は、「ホームへ戻る」の後に決まるため。添付を付けないのは報告に残るものを減らすため |
| N-14 | Step 14: `--list` の報告の置き場 | `--list` を3回流し、最後に `frontend/playwright-report/`・`frontend/test-results/`（ファイル 2・ディレクトリ 2）を中を開かずに消した | N-2 と同じ扱い |
| N-15 | 4.3: README に U4 の短い節を足す | 節の名前を「管理の画面の「権限が無い」の扱い（Intent 260930-user-admin の U4）」にし、「DSL の管理画面（U5）」の後に置いた | README には前の Intent の U4（画面の表示の設定）の節があり、名前が重ならないようにするため |

このほかに、承認済みの計画・設計と違う作りは無い。

### 依頼者に確かめたいこと（Step 9〜15 の時点）

- N-6: `applyOwnProfileFor` の `current` に言語を含めた形（ja・en 以外の言語では、今当てている言語のまま氏名だけを当てる）でよいか。

## 依頼者の決定（Step 15 の後）

- **N-6**: 依頼者は、`applyOwnProfileFor` の `current` に言語を含め、ja・en 以外の言語では今当てている言語のまま氏名だけを当てる形を受け入れた。実装はこのまま変えない。

## 3回目の依頼（Step 16 の記録の部分）

- この回は Step 16 のうち記録の項目だけを行った（`code-summary.md` の仕上げ、`source-manifest.json`、`traceability.json`、計画の Step 16 の1つ目のチェック）。コミット・`git add`・統合・push はしていない。
- `source-manifest.json`: U4 で作った・変えたアプリのソース・テスト・E2E・README の 42 件（`git diff --name-only e20c3b7` と未追跡のファイルから `aidlc/` を除いたもの）。すべて実在することを確かめた。
- `traceability.json`: upstream_ids 39（AC2.2.1〜AC2.2.6・AC5.1.1〜AC5.1.8・NFR1.1〜NFR9.12。U4 の機能設計は決まりを D1〜D14 で書き、`BRx.y` の ID を持たないため BR は無い）。OK 31・Deferred 8・GAP 0。OK の target はすべて実在するファイル1つで、Python で読めることと実在を確かめた。130 を target にした NFR3.2・NFR7.3・NFR9.3・NFR9.10 と、`check-bundle-size.mjs`（NFR9.4）・`vitest.config.ts`（NFR9.8）は Step 17 で流して確かめる（`code-summary.md` 4節）。

## コード生成のレビュー（iteration 1、READY）の指摘と依頼者の決定（2026-10-02）

| ID | 重さ | 指摘 | 依頼者の決定 |
|---|---|---|---|
| R-01 | Major | B2 の関門（verify・osvScan・e2eTest）が未実施で、130 の 20 組は一度も流れていない。Step 17 で合否が決まる OK が6件ある | Step 17 で流し、結果で6件を確かめ直す（計画どおり） |
| R-02 | Major | 画面の判定（403 かつ `ACCESS_DENIED`）が本物のサーバーの応答と一致することを、U4 のテストは確かめていない | 確かめ済みとして記録する。サーバー側の既存の結合テスト（`AdminPathBoundaryIT`・`DslAccessControlIT`・`AccessDeniedEventsIT`）が、管理の API の 403 で `code` が `ACCESS_DENIED` であることを確かめている（オーケストレーターが読み取りで確かめた）。画面と本物のサーバーを通した確かめは、U5 の E2E 110 で自分で作った管理者でない利用者を使って行う（B5 の計画へ申し送る） |
| R-03 | Minor | 画面の管理者の印が古く false のとき、印を付けられた直後でも、読み直しが起きるまで S6 のまま | 受け入れる（設計で承認済みの R4。サーバー側の判定は常に最新で、安全の側の遅れ） |
| R-04 | Minor | 古い読み込みの 403 を捨てた後に、最新の要求の 403 で S6 が出る道のテストがあるか | B5 の計画で確かめる（U5 の画面も同じ道を使う） |
| R-05 | Minor | 130 が 20 組それぞれで本物の refresh を呼ぶことの、共有の初期管理者への影響。初回の JavaScript の入口の分け方が変わった原因 | Step 17 の E2E の結果で確かめる |

コミット: C3 0e69fc8・C4 18f5274・C5 eef989f・C6 5324fed（依頼者の承認を得てオーケストレーターが作った）。

## Step 17 の関門（B2 全体、2026-10-02）— E2E の 090 の1件が失敗し、止めた

この回は Step 17 のうち関門の確かめだけを行った。統合・コミット・`git add`・ブランチの削除・push はしていない。作業ブランチ `feature/260930-user-admin-b2`、先頭 `3879075`（R2）。始めた時点でアプリのソースに未コミットの変更は無く（`git status` は `aidlc/` の下の2つだけ）、`frontend/playwright-report/`・`frontend/test-results/` は無かった。

### 1. verify（通った）

- colima が動いていることを `colima status` で確かめ、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` → **BUILD SUCCESSFUL（6 分 16 秒）**。書式・リンタ・ライセンスヘッダー・型・ビルド・単体・結合・カバレッジ・`gitleaksScan`・`spotbugsGate`・`frontendBundleSize`・`bootWar`・`verifyDslSchemaInWar` の段がすべて通った。`osvScan` はこの中では UP-TO-DATE（下の 2 で `--rerun-tasks` で流した）。

| 種類 | 件数 | 失敗・エラー・飛ばした |
|---|---|---|
| バックエンドの単体（`backend/build/test-results/test`） | 1287 | 0・0・0 |
| バックエンドの結合（`backend/build/test-results/integrationTest`） | 587 | 0・0・0（対象DB のテストは飛ばされていない） |
| 画面（Vitest） | 95 ファイル・801 件 | 0 |

| 範囲 | 行 | 分岐 |
|---|---|---|
| バックエンドの全体（JaCoCo） | 98.8%（5652/5719） | 94.5%（2069/2190） |
| `common.paging` | 100.0%（13/13） | 100.0%（10/10） |
| `invitation.domain` | 98.3%（233/237） | 96.4%（107/111） |
| `invitation.service` | 100.0%（331/331） | 93.5%（87/93） |
| `invitation.web` | 97.3%（107/110） | 87.0%（20/23） |
| `invitation.repository` | 100.0%（2/2） | 分岐なし |
| 画面の全体（`frontend/coverage/coverage-summary.json`） | 97.61%（2414/2473） | 93.01%（1505/1618） |

- バックエンドの値は U2 の Step 13 と同じ。画面の全体は U2 の後（行 97.44%・分岐 92.67%）から上がった。下限（行 80%・分岐 70%）・除外・`packagesJudgedByTotal` は変えていない。
- U4 で作った・変えた画面のファイルの行・分岐は、Step 15 の終わりの表と同じ値だった（`adminForbidden.ts` 100%・100%、`AdminForbiddenProvider.tsx` 100%（42/42）・95%（19/20）、`AdminForbiddenView.tsx` 100%（8/8）、`forbiddenHeading.ts` 100%・100%、`useApplyOwnProfile.ts` 100%（1/1）、`apiClient.ts` 100%・100%、`displaySettingsStore.ts` 97.4%・91.93%、`DisplaySettingsProvider.tsx` 100%・97.56%、`decideRoute.ts` 100%・95.65%、`AppRouter.tsx` 100%・100%、`ShellLayout.tsx` 100%・100%、`App.tsx` 93.75%・83.33%、`adminAreaStatus.ts` 100%・100%、`AdminAreaPage.tsx` 94.73%・70%、`useDslAdmin.ts` 94.78%・77.77%、`DslAdminPage.tsx` 100%・100%、`useInvitationAdmin.ts` 93.92%・83.47%）。
- 初回の JavaScript（verify の段 `frontendBundleSize`）: `assets/index-*.js` 106.1 KB・`assets/useTranslation-*.js` 16.6 KB、合計 122.7 KB（gzip）。Step 1（U4 の前）の 123.1 KB から 0.4 KB 減り、Step 15 の終わりの値と同じ。目安 500KB の内（NFR9.4）。

### 2. osvScan（通った）

- `caffeinate -i ./gradlew osvScan --rerun-tasks` → BUILD SUCCESSFUL（約 4 秒）。走査したパッケージ: `backend/gradle.lockfile` 252・`frontend/package-lock.json` 382・`vendor/make-you-chic-ui/package-lock.json` 404。失敗の条件に当たるもの 0 件、警告 14 件（どれも `vendor/make-you-chic-ui/package-lock.json` の開発用の `brace-expansion@5.0.9` 3件・`undici@8.10.0` 11件）。

### 3. E2E（失敗: 090 の1件）

- Mailpit: `docker compose ps` で `mailpit` は running、`http://127.0.0.1:8025/api/v1/info` は 200。もとから動いていたため起動も停止もしていない（B1 の経過のとおり）。
- `caffeinate -i ./gradlew e2eTest` → **BUILD FAILED（4 分 16 秒）**。json の `stats`: 成功 129・失敗 1・飛ばした 0・不安定 0、時間 254.5 秒。

| ファイル | 件数 | 結果 |
|---|---|---|
| 010 | 2 | 成功 |
| 020 | 2 | 成功 |
| 030 | 1 | 成功 |
| 040 | 1 | 成功 |
| 050 | 21 | 成功 |
| 060 | 21 | 成功（20 組と実データの1件。`skip-reason` は無い） |
| 070 | 20 | 成功 |
| 080 | 21 | 成功 |
| 090 | 1 | **失敗** |
| 100 | 20 | 成功 |
| 130 | 20 | 成功 |

- 11 ファイル・130 件（期待どおりの数）。
- **090 の失敗の中身**: `090-invitation-registration-flow.e2e.ts` の step `new user logs in and cannot use the administration screens`（238 行目）。090 が自分で作った管理者でない利用者でログインし `page.goto('/admin/invitations')` した後に `getByTestId('not-found-page')` が見えることを期待しているが、見つからず 5 秒で時間切れ。招待・登録・ログインの前の step は通っている。
- 見立て: U4 の承認済みの決まり（D6・G1・G5: ログインしていて管理者でない利用者が `ADMIN` の画面を開くと `NOT_FOUND` ではなく S6（`ADMIN_FORBIDDEN`）を出す）どおりに画面が変わったため、090 の期待（「ページが見つかりません」）が古いまま残った。不安定なテストではなく、決まった失敗と見る（130 は 090 の後に流れるため、130 の影響ではない）。計画の Step 13・14・4.4 は 090 を変える対象に入れておらず、計画の段で `not-found-page` を期待する既存の E2E を洗い出していなかった（`frontend/e2e` で `not-found-page` を使うのは 090 の2か所と 100 の1か所。100 は登録に無い URL で、通っている）。直していない（依頼のとおり止めた）。
- 直し方の案（依頼者に諮る）: 090 の 236〜238 行目の期待を、`/admin/invitations`・`/admin` の両方で S6（`admin-forbidden-view`）が見えることに替える（U4 の C6 のテストの直しとして、計画にない E2E の変更）。直した後は Step 17 の verify からやり直す（計画の D-3・Step 17）。U2 のソースには触れないため、単位ごとの squash の条件は崩れない。

#### 130 の組ごとの結果（json の注記 `axe`・`admin-forbidden-problems` から）

- 20 組すべて成功。どの組も: axe の違反 0（規則の名前なし、incomplete なし）、はみ出し無し（幅 1280 の組は scrollWidth 1280、375px の組は 375）、Avatar の文字の数 2、書き換えの回数 2（ログインと読み直し）、403 を返した回数 1、除いたコンソールの表示 1、除いた残りの問題 0、CSP の違反 0、管理の API への GET 以外の要求 0。
- 注記は1組に2つ（`axe`・`admin-forbidden-problems`）で、計 40。氏名・メールアドレス・パスワード・トークンは入っていない（組の名前と数だけ）。
- 130 の全体の時間（20 組の実行時間の合計）: 24.4 秒（1組 1.17〜1.31 秒）。見込みの 1〜2 分より短い（R-02 の実行時間）。画面の時間は測っていない（NFR9.3）。

#### 報告と秘密

- 報告の部品（`playwright-secret-check-reporter.ts`）: 「E2E の json の結果に仮の資格情報は含まれていません（3 項目を確かめた）」が出た。
- 2語の氏名の値が json の報告に含まれる件数: `grep -o` の件数で 0 件（値は表示していない）。
- 読んだのは json の `stats` とテストごとの題・状態・注記と、Gradle の端末の出力（失敗の題・期待・行番号）だけ。html の報告・trace・error-context は開いていない。
- 記録の後に `frontend/playwright-report/`（ファイル 21・ディレクトリ 4）・`frontend/test-results/`（ファイル 4・ディレクトリ 105。失敗の1件の trace を含む）を中を開かずに消した。報告は誰にも共有していない。

### 4. レビューの R-05 の確かめ

- **130 と共有の初期管理者**: E2E は1つのワーカーで 010 → 100 → 130 の順に流れ、130 は最後に流れた。そのため、130 の後に流れた 010〜100 は無く、130 が 010〜100 の結果に影響した道は無い。130 の 20 組はそれぞれ本物の `POST /api/auth/session/refresh` を呼んだ（読み直しの応答 200 を待つ順6・8 が全組で通り、書き換えの回数 2）。130 は `GET /api/admin/check` を 403 に差し替えるだけで、管理の API への GET 以外の要求は全組で 0 件（初期管理者の状態を変える要求を送っていない）。各組のログイン（`loginAsAdmin`）が全組で通ったことから、20 回の読み直しの後も初期管理者はログインできる状態のまま（ロック・利用停止にならなかった）と読める。ただし 130 の後に初期管理者でログインする E2E が無いため、「130 の後の初期管理者の状態」を別の流れで確かめてはいない。順番を入れ替えた実行はしていない。
- **初回の JavaScript の入口の分け方**: verify の後の `frontend/dist/.vite/manifest.json` を読んだ。入口 `index.html` → `assets/index-*.js` が静的に読むのは `useTranslation-*.js` だけで、`hooks-*` という名前の塊は今の出力に無い（`dist/assets` にも無い）。動的に読む塊は 7。U4 の前（Step 1）にあった `hooks-*.js`（7.8 KB）の中身が入口の `index-*.js`（98.7 → 106.1 KB、+7.4 KB）に移り、合計は 0.4 KB 減ったことは数字から読めるが、どのモジュールが移ったか・なぜ分け方が変わったか（`App` が静的に読む `AdminForbiddenProvider`・`AdminForbiddenView` が、前は動的な画面の塊と共有されていたモジュール（react-router の口や make-you-chic-ui の `Alert` など）を入口から読むようになったため、という見立て）は確かめていない。U4 の前の版（`e20c3b7`）を作り直して塊の中身（sourcemap の sources）を比べれば確かめられるが、読み取りの範囲を超えるためしていない。

### 5. U2 のレビューの R-05（招待の既存のテスト）

- verify で、バックエンドの招待のテスト（`cherry.mastersmith.invitation.*`）は単体 127 件・結合 60 件がすべて通った（失敗・エラー・飛ばした 0）。画面の招待のテストも 801 件の中ですべて通った。
- B2 の分岐の元（`develop` との merge-base `f324c43`）からの招待のテストの差分は、U2 の移し（`InvitationPagingTest` を消して `common/paging/PagingTest` へ、`invitation/paging.test.ts` を `shared/paging/paging.test.ts` へ）・`InvitationRepositoryIT` のフォーマッタによる行のまとめ直し（U2 の依頼者の決定 (1)）と、U4 の計画どおりの書き換え（`InvitationAdminPage.test.tsx`・`failureMessage.test.ts`、Step 10）だけ。ほかの招待のテストのファイルは変更なしで通った。

### 6. traceability の OK の6件の確かめ

| ID | target | 結果 |
|---|---|---|
| NFR3.2（E2E の報告と資格情報） | 130 | 確かめた: 報告の部品が通り、2語の氏名の件数 0、注記に値が無い、報告は記録の後に消した |
| NFR7.3（実際のブラウザの検査） | 130 | 確かめた: 20 組すべて axe の違反 0・はみ出し無し・Avatar 2 文字・`role="status"`・フォーカス・「ホームへ戻る」 |
| NFR9.3（画面の時間は目標を置かず記録しない） | 130 | 確かめた: 130 は時間を測らず、注記に時間が無い（全体の実行時間 24.4 秒はテストの実行の時間で、画面の時間ではない） |
| NFR9.4（初回の JavaScript） | `check-bundle-size.mjs` | 確かめた: 122.7 KB（目安 500KB の内、警告なし） |
| NFR9.8（カバレッジ） | `vitest.config.ts` | 確かめた: 画面の全体 行 97.61%・分岐 93.01%、下限を満たす。バックエンドも下限を満たす |
| NFR9.10（E2E の本数と既存の E2E） | 130 | **満たしていない**: 130 は流れに数えず本数は増えていないが、既存の E2E のうち 090 が失敗した（上の 3） |

### 7. この関門の扱い

- verify と osvScan は通ったが、E2E の 090 が失敗したため、Step 17 の関門は通っていない。`team.md` の「不安定なテストと CI の失敗」の決まりでは、手元で決まって起きる失敗は原因を直すまで統合しない。依頼のとおり直さずに止めた。R3 の記録のコミットと統合は、090 を直して verify からやり直した後に行う。

## 090 の直しと関門のやり直し（依頼者の決定、2026-10-02）

依頼者の決定: E2E の 090 の期待を S6 に直す（U4 のテストの直し。計画との差として記録する）。コミット・`git add`・統合はしていない。

### 090 の直し（計画との差 N-16）

| ID | 計画の記述 | 実際 | 理由・扱い |
|---|---|---|---|
| N-16 | Step 13・14・4.4: 変える E2E は 130 と `support/loginPreferences.ts` だけ。既存の 010〜100 は変えずに流す（NFR9.10） | `frontend/e2e/090-invitation-registration-flow.e2e.ts` の step `new user logs in and cannot use the administration screens` で、管理者でない利用者が `/admin/invitations`・`/admin` を開いたときの期待を `not-found-page` から S6（`admin-forbidden-view` が見え、`admin-forbidden-heading` が見える）に替えた（130 と同じ部品の名前）。説明のコメント1行を足した | U4 の決まり（D6・G1・G5）で、管理者でない利用者の管理の画面は S6 になる。計画の段（Step 13・14・4.4）で `not-found-page` を期待する既存の E2E を洗い出しておらず、090 を直す対象に入れていなかった。Step 17 の1回目の `e2eTest` で 090 が決まって失敗して分かった。100 の `not-found-page`（登録に無い URL）とほかの E2E のファイルは変えていない |

- 確かめ: `npx prettier --check`（090）通過、`npm run typecheck`（`e2e` を含む）通過、`npx oxlint`・`npx eslint`（090）終了コード 0、ライセンスヘッダーの検査「すべてのファイルにヘッダーがあります」。
- `source-manifest.json` に `frontend/e2e/090-invitation-registration-flow.e2e.ts` を足した（43 件）。

### やり直した関門

- **verify**: colima の設定を渡し `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` → BUILD SUCCESSFUL（6 分 17 秒）。バックエンドの単体 1287・結合 587（失敗・エラー・飛ばした 0、招待は単体 127・結合 60）、画面 95 ファイル・801 件すべて成功。カバレッジはバックエンドの全体 行 98.8%（5652/5719）・分岐 94.5%（2069/2190）、`common.paging` 100%・100%、`invitation.domain` 98.3%・96.4%、`invitation.service` 100%・93.5%、`invitation.web` 97.3%・87.0%、`invitation.repository` 100%・分岐なし、画面の全体 行 97.61%（2414/2473）・分岐 93.01%（1505/1618）（1回目と同じ）。初回の JavaScript 122.7 KB（gzip、`index-*.js` 106.1 KB・`useTranslation-*.js` 16.6 KB）。`gitleaksScan`・`spotbugsGate` 通過。
- **osvScan**: 依存が変わらないため流し直していない。verify の中では UP-TO-DATE で、結果は1回目の `--rerun-tasks`（失敗の条件 0 件・警告 14 件）のまま。
- **E2E**: Mailpit は running・`/api/v1/info` 200（もとから動いていたため起動も停止もしていない）。始める前に報告のディレクトリが無いことを確かめた。`caffeinate -i ./gradlew e2eTest` → BUILD SUCCESSFUL（4 分 11 秒）。json の `stats`: 成功 130・失敗 0・飛ばした 0・不安定 0、時間 249.5 秒。

| ファイル | 件数 | 結果 |
|---|---|---|
| 010・020・030・040 | 2・2・1・1 | 成功 |
| 050 | 21 | 成功 |
| 060 | 21 | 成功（`skip-reason` 無し） |
| 070 | 20 | 成功 |
| 080 | 21 | 成功 |
| 090 | 1 | 成功（8.3 秒） |
| 100 | 20 | 成功 |
| 130 | 20 | 成功 |

- 130: 20 組すべて、axe の違反 0（規則なし、incomplete なし）、はみ出し無し（1280 の組 14・375px の組 6）、Avatar の文字の数 2、書き換えの回数 2、403 を返した回数 1、除いたコンソールの表示 1、除いた残りの問題 0、CSP の違反 0、管理の API への GET 以外の要求 0。注記 40（`axe` 20・`admin-forbidden-problems` 20）に値は無い。130 の全体の時間（20 組の合計）24.8 秒。
- 報告の部品: 「E2E の json の結果に仮の資格情報は含まれていません（3 項目を確かめた）」。2語の氏名の値の件数 0。読んだのは json の `stats`・題・状態・注記と Gradle の出力だけ。
- 記録の後に `frontend/playwright-report/`（ファイル 1・ディレクトリ 1）・`frontend/test-results/`（ファイル 2・ディレクトリ 105）を中を開かずに消した。誰にも共有していない。
- R-05（130 と共有の初期管理者）: 順は1回目と同じく 130 が最後で、090 は 130 の前に流れて通った。1回目の見立て（130 の後に流れる流れは無い）は変わらない。

### traceability の NFR9.10 の確かめ（やり直し）

- 確かめた: 130 は流れの本数に数えず、E2E の流れの本数は増えていない（`030-admin-access.e2e.ts` は変えていない）。既存の 010〜100 は 090 の期待を U4 の決まりに合わせて直した後、すべて通った（N-16）。OK の6件（NFR3.2・NFR7.3・NFR9.3・NFR9.4・NFR9.8・NFR9.10）はすべて確かめた。`traceability.json` の target は 130 のまま変えていない。

### 関門の扱い

- verify・osvScan（1回目の `--rerun-tasks` の結果）・E2E がすべて通り、Step 17 の関門の確かめは終わった。R3 の記録のコミットと統合は、依頼者の承認を得てオーケストレーターが行う。090 の直しはアプリのソース（E2E）の変更で、C3〜C6 の後の未コミットの変更として作業フォルダにある（U4 の squash の範囲に入る。U2 のソースには触れていない）。
