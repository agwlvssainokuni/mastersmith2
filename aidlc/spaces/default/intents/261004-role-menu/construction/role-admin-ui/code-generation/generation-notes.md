# 生成の記録（Generation Notes）— U6 role-admin-ui（Bolt B8）

計画 `code-generation-plan.md` の手順ごとに、流したコマンドと結果、計画との差を書く。作業ブランチは `feature/261004-role-menu-b8`（develop の 3977919 から）。

## Step 1: 作業の場の用意

- ブランチ: `feature/261004-role-menu-b8`（指揮役が作成済み、3977919 から）。`git status --short` は監査ログ（`aidlc/.../audit/sakura-local-4e42a93f87ce.md`）の未コミットの追記だけで、アプリのソースの変更は無い。
- `frontend/playwright-report`・`frontend/test-results` は無い。
- `vendor/make-you-chic-ui` の固定先は `e82b651`（計画どおり）。
- (1) E2E の番号: `frontend/e2e/` は 010・020・030・040・050・060・070・080・090・100・110・120・130・135。app-frame-ui の基盤の設計は 160・170 の案（`infrastructure-specification.md` の E2E の番号の行）。140・150 は空いている。
- (2) `role/domain/RoleName.java` の制御文字の判定は `Character.CONTROL`・`LINE_SEPARATOR`・`PARAGRAPH_SEPARATOR`（Cc と U+2028・U+2029）で、`GroupName` と同じ。前後の White_Space（U+2028・U+2029 を含む）を除いてから判定し、上限は 64 コードポイント。
- (3) 受信箱の件数に頼る E2E は無い。`e2e/support/mailpit.ts` は宛先の完全一致の検索（`to:"..."`）と1通の読み取りの GET だけで、件数を数える箇所は無い（`mailpit` を使うのは 090・`support/mailpit.ts`・`support/registeredUser.ts`）。

## Step 2: テストの実行の準備

- `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/useradmin src/shared/tree src/shared/api-client/apiClient.download.test.ts` → 16 ファイル・177 件 passed、失敗 0。
- `npm run typecheck` → 誤り 0。

## Step 3: 変更の前の基準

- `caffeinate -i ./gradlew frontendBundleSize`（frontendBuild は UP-TO-DATE、3977919 の時点の dist）:
  - `assets/index-BbV9KzeP.js`: 109.5 KB (gzip)
  - `assets/useTranslation-BfBSLuXB.js`: 16.8 KB (gzip)
  - 初回の読み込みの JavaScript の合計: **126.3 KB (gzip)**
- `frontend/dist/.vite/manifest.json` の入口（`isEntry`）から `imports` をたどった静的な集まり（読むスクリプトは scratchpad の一時のファイル、リポジトリに足さない）:
  - `index.html` → `assets/index-BbV9KzeP.js`
  - `_useTranslation-BfBSLuXB.js` → `assets/useTranslation-BfBSLuXB.js`
  - `isDynamicEntry` の項目は 8 個。
- `caffeinate -i npm run test:coverage`（`frontend/`）: **114 ファイル・1,046 件 passed**。全体のカバレッジ: 文 97.36%・**分岐 92.68%**・関数 98.02%・**行 97.52%**。
- SHA-256:
  - `frontend/package.json`: `cb5cbaed22d45b25f008210723991dea236b59b46b9c9147420c2ccd35b41071`
  - `frontend/package-lock.json`: `8a83a269d5df7c36adfb4816f4952807393733a6c99ba9c77df80fb2cc0b7ddb`
  - `backend/gradle.lockfile`: `d415205433ec3a81601fe066540fcd59d1bd8cefdf08fabf4aa834f27f5475d5`

## Step 4: 入口の差し替え — 実装

- `frontend/src/main.tsx` の `BrowserRouter` を `createBrowserRouter([{ path: '*', element: <App appearance={appearance} /> }])` と `RouterProvider` に替えた。前処理（`rewriteDesignSystemCopies(getPrefersDark())`・`installLanguageResolver()`・`startAppearanceLoad()`）と `StrictMode` はそのままで、ルーターは前処理の後に作る（機能設計の読み直し R-04 の手当て）。`react-router` は既存の依存（8.x）で、`RouterProvider` は `react-router` から読む。

## Step 5: 入口の差し替え — テストと確かめ

- `src/app/routing/DataRouterBlocker.test.tsx`（新、6 件）: `main.tsx` と同じ形の data router（`createMemoryRouter` の `path: '*'` に `App`）で、テスト用の登録の画面の `useBlocker` がサイドバーの押下とブラウザの戻る（`router.navigate(-1)`）で止まる・留まると道が変わらない・移るで移る・止めないときは移る、`App` の中の振り分け（ホーム・見つからない・管理者でない利用者の `ADMIN_FORBIDDEN`）が変わらないこと。
  - 最初の形は単独で3回中2回、「ブラウザの戻る」が止まらずに落ちた。画面の要素が DOM に出た時点では useBlocker の登録の効果がまだ走っていないことがあるため（見立て）。テストの画面に、useBlocker の後に宣言した効果で立てる印（`guard-ready`）を置き、それを待ってから移動を試す形に直し、単独で 8 回続けて通った。本体のコードは変えていない。
- 画面のテストの全体 `caffeinate -i npm run test`: **115 ファイル・1,052 件 passed**（Step 3 の 1,046 件 + `DataRouterBlocker.test.tsx` の 6 件。既存の件数は変わらない）。
- `caffeinate -i ./gradlew :backend:bootWar`（frontendBuild を含む）の後、Mailpit（`mastersmith-mailpit-1`、healthy）を起動した状態で `caffeinate -i npx playwright test`（010〜135 の全体）:
  - 1回目: **176 passed・1 failed**（7.7 分）。落ちたのは `060 … invitations (c) light sm blue 375px`。`loginAsAdmin` の `goto('/')` の後、5 秒以内にログインの画面が出なかった。失敗の記録では、`support/loginPreferences.ts` が差し替える `POST /api/auth/session/refresh` の `route.fetch()` がテストの終わり（5 秒）までサーバーから返っていなかった。
  - 再現の試み（時間の上限を「060 の単独 2 回と 010〜060 の通し 1 回」と先に決めた）: 060 の単独 21 passed・21 passed、010〜060 の通し 48 passed。再現しなかった。
  - 見立て（未検証）: 要求の送り出しは起きており、サーバーの refresh の応答が遅れた一時的な遅れ（PC の負荷）で、ルーターの差し替え（画面の中の振り分け）とは別の経路。`team.md` の「手元で再現しないもの」に当たり、原因は確かめていない。
  - 2回目の全体: **177 passed**（7.2 分、exit 0）。報告の部品: 残してはならない値は 0 件（値の種類 7・json の報告の添付 299）。
  - 報告のフォルダー（`frontend/test-results`・`frontend/playwright-report`）は消した。
- 判定: 差し替えの後も既存の画面のテストと E2E の全体が通り、`useBlocker` がサイドバーの移動とブラウザの戻るで止まる。機能設計 7.3 への切り替えは要らない。
- C1 のコミットの提案（`main.tsx`・`DataRouterBlocker.test.tsx`）は、生成の後に指揮役から依頼者に諮る（実行していない）。

## Step 6: 共有の部品 — 実装

- `src/shared/api-client/apiClient.ts`: `ApiDownload` に `headers: Headers` を足した（`blob`・`contentDisposition` は変えない）。
- `src/shared/validation/limits.ts`: `ADMIN_NAME_MAX_CODE_POINTS = 64` を足した。
- `src/shared/validation/validateAdminName.ts`（新）: `validateAdminName`（required・tooLong・invalidCharacter、拒むのは Cc と U+2028・U+2029、Cf は拒まない）と、残りの文字数の `remainingAdminNameLength`。
- `git mv` で `features/useradmin/searchInput.ts`・`searchInput.test.ts` を `shared/validation/searchText.ts`・`searchText.test.ts` へ移した。中身は変えず、import の道（`./codePoints`・`./searchText`）と、置き場が変わったことの説明の行だけを直した（「この機能だけが使うため limits.ts には置かない」の1行は事実と合わなくなるため消した）。`useUserAdmin.ts` の import を直した。

## Step 7: 共有の部品 — テスト

- `apiClient.download.test.ts` に2件（`headers` が返り既存の項目が変わらない、ヘッダーが無いとき null）。`validateAdminName.test.ts`（新、10 件、fast-check の性質1件）。移した `searchText.test.ts`。
- 計画との差: 最初に書いた `validateAdminName.ts` の正規表現に U+2028・U+2029 の文字そのものが入り（書き出しの道具がエスケープの書き方を文字に変えた）、変換の誤りで落ちた。正規表現を「バックスラッシュ u 2028」のエスケープの書き方に直した。テストのファイルの見えない文字（U+00A0・U+200B・U+FEFF・U+3000 など）もエスケープの書き方にそろえた。
- `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/api-client/apiClient.download.test.ts src/shared/validation src/features/useradmin src/features/dsl/api` → **21 ファイル・219 件 passed**。

## Step 8: 純粋な関数 — 実装

- roleadmin: `api/types.ts`（純粋な関数が使う型のため Step 10 より先に置いた）、`permissionDraft.ts`（`targetKey`・`scopeKey`・`applyChange`・`inScope`・`toEntries`・`retainDraft`・`draftExplicit`。カラムの対象は補助権限を null にそろえる）、`permissionLabels.ts`（`effectiveLabel`・`formatEffectiveLabel`。13節 Q4: A の形）、`removalImpact.ts`、`failureMessage.ts`（場面ごとの `ROLE_NO_CHANGE` の文言、`inUseCounts`）。
- groupadmin: `api/types.ts`、`removedRoles.ts`、`failureMessage.ts`（`inUseCounts` は `members`・`assignedRoles`）。
- roletransfer: `api/types.ts`、`changePaging.ts`、`reasonMessage.ts`（13 個）、`failureMessage.ts`（413 は code が無くても大きすぎるの文言、415 は汎用）、`safeFileName.ts`、`transferLimits.ts`。`reasonMessage.test.ts` が ja・en の文言の有無を確かめるため、`messages.ts` を誤りの文言と理由の 13 個で先に置いた（画面の文言は Step 18 で足す）。

## Step 9: 純粋な関数 — テスト

- 11 ファイル（roleadmin 4・groupadmin 2・roletransfer 5）、fast-check の性質は `permissionDraft`（4 件）・`removalImpact`（2 件）・`removedRoles`・`changePaging`・`reasonMessage`。
- 1回目に `permissionDraft` の性質「サーバーの値に戻すと写しから消える」が落ちた。見本の作り方が、同じ対象に別々のサーバーの値を当て、カラムの対象のサーバーの値に補助権限を入れていたため（実際の応答に無い形）。見本を「対象ごとに1つのサーバーの値（カラムは補助権限が null）」に直した。本体は変えていない。
- 直した後: **11 ファイル・53 件 passed**。

## Step 10: API の関数 — 実装

- `roleadmin/api/roleAdminApi.ts`（18 の関数）、`groupadmin/api/groupAdminApi.ts`（10 の関数）、`roletransfer/api/roleTransferApi.ts`（`exportRoles`・`checkTransfer`・`applyTransfer` と、誤りの一覧を読む `transferErrors`）、useradmin の `api/types.ts` に `RoleSourceType`・`UserRoleSource`・`UserRole`、`api/userAdminApi.ts` に `getUserRoles`。
- 2.3 の形に合わせた: 木の応答の `{items}`（A3）、保存の `scope` は `schemaName`・`tableName` だけ（A6）、出どころは応答の `type` を `kind` に写し `DIRECT`・`GROUP` 以外は通信の失敗（A9。`DIRECT` の `groupId`・`name` は null で来る）、利用者・グループのロールは配列（A12）、誤りの `line`・`column`・`path` は null を許す（A15）。
- 計画との差: `userAdminApi` の集まりに `getUserRoles` を足したため、`UserAdminPage.test.tsx` の差し替えの集まり（`fakeApi`）に `getUserRoles: vi.fn(() => Promise.resolve([]))` を1行足した（型の検査を通すため。Step 21 で「ロールを見る」の流れを足すときに使う）。

## Step 11: API の関数 — テスト

- `roleadmin/api/roleAdminApi.test.ts`（14 件）・`groupadmin/api/groupAdminApi.test.ts`（7 件）・`roletransfer/api/roleTransferApi.test.ts`（8 件）・`useradmin/api/userAdminApi.test.ts` に3件。
- `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/roleadmin/api src/features/groupadmin/api src/features/roletransfer/api src/features/useradmin/api` → **4 ファイル・40 件 passed**。`npm run typecheck`・`npm run lint`・`npm run license:check` は誤り 0（Prettier で整形した）。
- C2（共有の部品）・C3（純粋な関数と API の関数）のコミットの提案は、生成の後に指揮役から依頼者に諮る（実行していない）。

## Step 12: roleadmin の S3 — 実装（途中）

- 書いたもの: `messages.ts`（S3・S4・S5 の文言、ja・en 各 174 の鍵）、`useRoleAdminText.ts`、`latestLoad.ts`、`useSlowNotice.ts`。
- 1回目の依頼はここで止まった（長い出力が途中で切れた）。

## Step 12: roleadmin の S3 — 実装（続き、2回目の依頼）

- 書いたもの: `useRoleList.ts`（4.1 の状態、`latestLoad` の世代、`useSlowNotice` の `run`、`correctedPage`・`pagerButtonDisabledAfter`、W3.3〜W3.5 の拒否の扱い）・`RoleListPage.tsx`・`RoleNameDialog.tsx`（`validateAdminName`、残りの文字数は FormField の `helperText`、誤りは `error`）・`DeleteConfirmDialog.tsx`・`RoleRowActions.tsx`（「開く」は `react-router` の `Link`、Dropdown は `bottom-end`）・`paths.ts`・`RoleListPage.css`・`testing/fixtures.ts`。
- 計画との差（G に当たらない小さなもの）:
  - `paths.ts` を足した（`frontend-components.md` 2.1 に無いファイル）。登録と画面の中のリンク（S4・S5・DSL の管理）が同じ関数で道を組むため。部品のファイルから関数を書き出さないため分けた。
  - 画面の上の知らせは make-you-chic-ui の Alert の役割（成功は `role="status"`、拒否は `role="alert"`）で読み上げる。D30 の「`aria-live="polite"`」を包む要素に重ねると二重に読み上げるため付けない。
  - 名前の Modal の画面の検査の誤りは、入力を始めた後か送ろうとした後に出す（開いた直後の空の欄に「名前を入れてください」を出さない）。
- 既存のファイルの直し: `npm run lint` で、Step 5 で直した `src/app/routing/DataRouterBlocker.test.tsx` の印（`guard-ready`）が oxlint の `react(set-state-in-effect)` に当たっていた（効果の中の `setReady(true)`）。Step 11 の記録では lint の誤り 0 だったが、今回の実行で出た。印を状態にせず、効果の中で DOM の `data-ready` 属性を立てる形に直し、テストは `waitFor` でその属性を待つ形にした（テストの中身は同じ）。`npx vitest run src/app/routing/DataRouterBlocker.test.tsx` を 5 回続けて 6 件 passed。本体のコードは変えていない。
- `npm run typecheck`・`npm run lint`・`npm run lint:css`・`npm run license:check` は誤り 0（Prettier で整形した）。

## Step 13: roleadmin の S3 — テスト

- 書いたもの: `testing/renderRoleAdmin.tsx`（`renderRoleAdmin` は既存の `renderWithProviders`（MemoryRouter）に Toast・Modal の Provider を足す形、`renderRoleAdminDataRouter` は Step 14・15 の `useBlocker` の部品のための data router の描き方。骨組みの Provider は道ごとに作り直さないよう、親の道（`Outlet`）に置く）、`latestLoad.test.ts`（4 件）・`useSlowNotice.test.tsx`（5 件、偽の時計で 4.9 秒で出ず 5 秒で出て答えで消える・送信中の2回目を捨てる・同期の例外・画面を外した後）・`RoleNameDialog.test.tsx`（9 件、64・65 コードポイントとサロゲートペア・空白だけと全角の空白・前後を除いた長さ・Cc を拒み U+200B を受け付ける・サーバーの拒否の置き場・送信中・axe）・`DeleteConfirmDialog.test.tsx`（6 件）・`RoleRowActions.test.tsx`（5 件、`bottom-end` は jsdom の矩形を与えて Dropdown が求めた位置（右端を trigger の右端にそろえた left）で確かめた）・`RoleListPage.test.tsx`（15 件）。
- `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/roleadmin` → **11 ファイル・86 件 passed**（Step 9・11 の roleadmin の純粋な関数と API の 5 ファイルを含む）。`RoleListPage.test.tsx` は3回続けて 15 件 passed。`npm run typecheck`・`npm run lint`・`npm run license:check` は誤り 0。
- 途中の直し（本体）: 削除の成功の後の見出しへのフォーカスが、Modal の閉じた後のフォーカスの戻し（開いた行の「操作」）に上書きされることがあった。招待の画面（`useInvitationAdmin.ts`）と同じく、Modal を閉じた後の読み直しを `shared/modal/afterModalClosed` の `runAfterModalClosed` で閉じ終わった後に始める形にした（`useRoleList.ts`）。
- 途中の直し（テスト）: (1) 行の「操作」を表の読み込みの前に探していたため `findByRole` で待つ形にした。(2) 「答えの順を入れ替えた2つの読み込み」は、画面からは読み込みの最中の操作を捨てるため2つの読み込みを重ねられない。Testing Library の `configure({ reactStrictMode: true })` で根を StrictMode で包み、開いたときの効果を2回走らせて重ねた（部品の中に `<StrictMode>` を置く形は、最初の描画では効果が1回しか走らず使えなかった）。読み込みの世代の確かめを外す変異（`isLatest` を常に真）でこのテストが落ちることを確かめ、元に戻した。
- 計画の 8節の表との差: 偽の時計の待ちの案内（4.9 秒・5 秒）は `useSlowNotice.test.tsx` で確かめ、画面の側は Modal が `slow` を受けて案内を出すこと（`RoleNameDialog.test.tsx`・`DeleteConfirmDialog.test.tsx`）で確かめた。

## Step 14: roleadmin の S4・S5 と登録 — 実装

- 書いたもの: `registration.ts`（道3つ、S4・S5 は同じ `lazy` の `RoleDetailPage`、サイドバー「ロール」240・ADMIN、静的に読むのは `messages.ts`・`paths.ts` だけ）・`RoleDetailPage.tsx`・`useRole.ts`（`parseRoleId` は `^[1-9][0-9]*$` と安全な整数）・`PermissionPanel.tsx`・`useRolePermissions.ts`・`PermissionTable.tsx`・`PermissionPanel.css`・`useUnsavedGuard.ts`（`useBlocker` は道（pathname）が変わる移動だけを止める、`beforeunload` は写しがある間だけ）・`LeaveConfirmDialog.tsx`・`AssignmentPanel.tsx`（D19 の確かめの Modal を同じファイルに持つ）・`AssignmentPanel.css`・`useRoleAssignments.ts`・`CandidateDialog.tsx`、`testing/renderRoleAdmin.tsx` の data router の描き方（Step 13 で先に書いた）。
- 保存の後の読み直しは、スキーマの一覧を新しい配列にして共有の木に開いている節を読み直させる（計画 D-9）。選んでいるスキーマのテーブルの一覧は先に読み、木の `loadChildren` はそれ（prefetch）を使って同じ要求を二重に送らない。`PERMISSION_TARGET_NOT_IN_DSL` の後は、読み直した節（スキーマ・選んでいるスキーマのテーブル・選んでいるテーブルのカラム）のうち `inCurrentDsl` のものに当たる写しだけを残す（D14）。
- 計画との差（G に当たらない小さなもの）:
  - 開いたときの読み込み（`useRole`・`useRolePermissions`・`useRoleAssignments`・`CandidateDialog` のグループの1ページ目）は、効果の中で `queueMicrotask` で始め、後始末で取りやめる形にした。効果の中から状態を更新する関数を同期に呼ぶと oxlint の `react(set-state-in-effect)` に当たったため。振る舞いは同じ（開いた直後に1回読む）。`useRoleList`（S3）は当たらず、元の形のまま。
  - `LeaveConfirmDialog` の閉じた後のフォーカスは、開いたときのフォーカスがタブなら選んでいるタブへ戻す（矢印のキーで別のタブへ移ろうとして止めたとき、選ばれていないタブにフォーカスが残らないため）。それ以外は Modal の既定の戻し。
  - 今の DSL に無い印は make-you-chic-ui の Badge に `warning` が無いため `danger` の見た目にした（意味は文字「⚠ 今の DSL に無い」で示す）。
- `npm run typecheck`・`npm run lint`・`npm run lint:css` は誤り 0。`npx vitest run src/features/roleadmin src/app` → 38 ファイル・317 件 passed（登録が足されても既存の骨組みのテストは変わらない）。

## Step 15: roleadmin の S4・S5 と登録 — テスト

- 書いたもの: `RoleDetailPage.test.tsx`（13 件。D32 の道の ID 5 通り・ROLE_NOT_FOUND・読み込みの失敗、道からのタブ、5節 (e)、未保存のタブの切り替えと「← ロールの一覧」、axe）・`PermissionPanel.test.tsx`（16 件）・`PermissionTable.test.tsx`（7 件）・`useUnsavedGuard.test.tsx`（6 件）・`LeaveConfirmDialog.test.tsx`（4 件）・`AssignmentPanel.test.tsx`（10 件）・`CandidateDialog.test.tsx`（8 件）・`registration.test.tsx`（6 件、3つの道で管理者は SCREEN・管理者でない利用者は ADMIN_FORBIDDEN・未ログインは REDIRECT_TO_LOGIN、画面は lazy、サイドバー 240）・`RoleAdminForbidden.test.tsx`（12 件、画面と API の組の 403 の表 10 行・骨組みの中の権限なしの表示・ACCESS_DENIED の無い 403 は汎用）・`RoleAdminEnglish.test.tsx`（4 件、en の S3・S4・S5、console・ブラウザの保存・URL への漏えいなし）。`testing/fixtures.ts` に S4・S5 の見本を足した。
- **5節 (e) の確かめ**: `main.tsx` と同じ形の data router（`createMemoryRouter` の `path: '*'` に `App`、本物の roleadmin の登録、API は `vi.mock` で差し替え）で `/admin/roles/7` を開き、［割り当て］→［権限の設定］とタブを切り替えた。どちらの後も `getRole` は 1 回のままで、フォーカスは押したタブに残った。**作り直しは起きず**、計画の切り替え先（任意の区切りの1つの登録）は使わない。
- 途中で見つけた本体の誤り（直した）:
  - `PermissionPanel.tsx`: 共有の木に渡す `nodes` を描画のたびに新しい配列で作っていたため、木が開いている節を読み直し続け、`listTables` が数百回呼ばれた（共有の木の「nodes を替えると読み直す」の決まり）。`useMemo` でスキーマの一覧が変わったときだけ作り直す形にした。テストに「テーブルの一覧は1回だけ読む」「保存の後の読み直しでも二重に読まない」を入れた。
  - 見出しの段: 表の見出しを h3、カラムを h4 にしていたため axe の `heading-order` に当たった。S4 の表の見出しと S5 の利用者・グループの見出しを h2、カラムを h3 にした。
- 途中の直し（テスト）: `DataRouterBlocker.test.tsx`（Step 5）の「ブラウザの戻る」が、画面のテストの全体（カバレッジつき）の中で1回落ちた（手元で再現）。原因を `node_modules/react-router/dist/development/lib/hooks.js` の `useBlocker` で確かめた: 最初の描画の効果で鍵を状態に置き、**次の描画**の効果で `router.getBlocker` に登録する。Step 12 の印は最初の描画の効果で立っていたため、登録の前に戻るを試すことがあった。印を2回目の描画の効果で立てる形に直した（効果は宣言の順に走るため `useBlocker` の効果の後）。直した後、単独で 5 回続けて 6 件 passed、全体（カバレッジつき）も通った。本体のコードは変えていない（roleadmin の部品は、利用者が値を変えた後（何回も描画した後）に移動を試すため当たらない）。
- 計画との差: 403 の表の「S4 テーブルの一覧」の行は、共有の木が自分の「読み込めませんでした」を出す（木の子の読み込みの失敗）。骨組みの中では画面全体が権限なしの表示に置き換わるため、テストでその行だけ文言の無いことを確かめていない（コメントに書いた）。
- コマンドと結果:
  - `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/roleadmin` → **21 ファイル・172 件 passed**。
  - `npm run format:check`・`npm run typecheck`・`npm run lint`・`npm run lint:css`・`npm run license:check` → 誤り 0。
  - `caffeinate -i npm run test:coverage`（画面のテストの全体）: 1回目は上の `DataRouterBlocker.test.tsx` の1件で失敗（1,276 passed・1 failed）。直した後の2回目: **146 ファイル・1,277 件 passed**、全体のカバレッジ 文 95.28%・**分岐 90.1%**・関数 97.41%・**行 95.39%**（Step 3: 114 ファイル・1,046 件、行 97.52%・分岐 92.68%）。`features/roleadmin` の合計は 行 91.14%・分岐 83.73%（分岐の最も低いファイルは `useRolePermissions.ts` の 71.42%）。
- C4 のコミット（roleadmin の画面と登録・文言・テスト、`DataRouterBlocker.test.tsx` の印の直し2回を含む）の提案は、指揮役から依頼者に諮る（実行していない）。

## Step 16: groupadmin — 実装（3回目の依頼）

- 書いたもの: `messages.ts`（ja・en）・`useGroupAdminText.ts`・`latestLoad.ts`・`useSlowNotice.ts`（roleadmin と同じ形、機能どうしの import の禁止のため複製）・`paths.ts`・`useGroupList.ts`・`GroupListPage.tsx`・`GroupNameDialog.tsx`・`DeleteConfirmDialog.tsx`・`GroupRowActions.tsx`・`GroupListPage.css`（S6 の一覧は roleadmin の S3 の部品から名前を置き換えて作った）、`GroupDetailPage.tsx`・`useGroupDetail.ts`・`MemberTable.tsx`・`CandidateDialog.tsx`（利用者の検索だけ）・`GroupDetailPage.css`・`registration.ts`（道2つ、サイドバー「グループ」250・ADMIN）。
- D20: メンバーを外した後にその利用者のロールを読み、`removedRoles` で、このグループのロールのうち無くなったものを知らせに出す。読めなければ前の文だけ。D21: 割り当てたロールは `/admin/roles/{roleId}/assignments` への道の文字列のリンク（roleadmin を import しない）。詳細と割り当てたロールは並べて読み、ロールが読めなくてもメンバーの表は出す。
- 計画との差: S6 の一覧の `GROUP_IN_USE` の知らせにはリンクを付けない（D9 のリンクはロールの割り当てのタブのため、グループには当たらない）。詳細の「名前を変える」は詳細の画面のボタンから同じ `GroupNameDialog` を開く。
- `npm run typecheck`・`npm run lint`・`npm run lint:css` は誤り 0。

## Step 17: groupadmin — テスト

- 書いたもの: `testing/fixtures.ts`・`testing/renderGroupAdmin.tsx`（roleadmin の見本と描き方から名前を置き換えて作り、メンバー・詳細の見本を足した）、`GroupListPage.test.tsx`・`GroupNameDialog.test.tsx`・`DeleteConfirmDialog.test.tsx`・`GroupRowActions.test.tsx`（計画の一覧に無いが S3 と同じ形の部品のため置いた）・`GroupDetailPage.test.tsx`（13 件。D32 と同じ形の missing、D20 の3通り、D21 のリンク、ロールが読めないときもメンバーは出る、候補の済み・追加の3通りの拒否、名前の変更）・`MemberTable.test.tsx`・`CandidateDialog.test.tsx`・`registration.test.tsx`・`GroupAdminForbidden.test.tsx`（画面と API の組の 403 の表 7 行と骨組みの表示）・`GroupAdminEnglish.test.tsx`（漏えいの確かめを含む）。
- 途中で見つけて直した本体の誤り: 候補の Modal で「足す」の API が 403 を返すと、骨組みに渡したうえで Modal の中に汎用の「操作できませんでした」を出していた（D2 に反する。roleadmin の S5 も同じ）。足した結果に `handled`（骨組みが扱った、画面は文言を出さない）を足し、groupadmin・roleadmin の両方を直した。roleadmin の 403 の表に「S5 add user」の行を足した（13 件）。
- 見本の直し: roleadmin の `testing/fixtures.ts` の `ZERO_WIDTH_NAME` に U+200B の文字そのものが入っていた（書き出しの道具がエスケープを文字に変えた。Step 7 と同じ現象）。groupadmin の見本とともに `​` のエスケープの書き方にそろえた。
- `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/groupadmin` → **13 ファイル・89 件 passed**。`src/features/roleadmin` は 21 ファイル・173 件 passed。`npm run typecheck`・`npm run lint`・`npm run lint:css` は誤り 0。

## Step 18: roletransfer — 実装

- 書いたもの: `messages.ts` に画面の文言（ja・en）を足した、`useRoleTransferText.ts`・`latestLoad.ts`・`useSlowNotice.ts`（roleadmin と同じ形の複製）・`saveFile.ts`（DSL の画面と同じ形の複製）・`useRoleTransfer.ts`（4.5 の状態）・`RoleTransferPage.tsx`・`TransferFilePicker.tsx`・`TransferCheckTable.tsx`・`TransferErrorList.tsx`・`ApplyConfirmDialog.tsx`・`RoleTransferPage.css`・`registration.ts`（`/admin/role-transfer`、サイドバー「権限の受け渡し」260・ADMIN）。
- 確かめたときに読んだ本文の文字列は `useRoleTransfer` の参照にだけ持ち、適用でそのまま送る。ファイルの選び直し・適用の成功・画面を離れたときに捨てる（NFR 設計 Q1 A）。選び直したら世代を進め、確かめの最中の答えも捨てる。
- `TransferErrorList` と開いた行の変わる点の表の領域は `role="region"`・`tabIndex={0}`・読み上げの名前（ja・en）とし、oxlint の `jsx-a11y/no-noninteractive-tabindex` はその行だけを日本語の理由つきのコメントで外した（13節 Q7: A）。結果の表の外枠は中に開閉のボタンがあるため `tabIndex` を付けない。
- 計画との差（判断の要らない小さなもの）: 変わる点が無い（noChange）の判定は「ロールが無い、または作るロールが無く、どのロールの足す・変わる・消えるも 0」とした（D24 の「ロールが1つも無い・変わる点が無い」の読み）。適用の `ROLE_BUSY` は結果の表を残して、もう一度適用できるようにした（D4）。確かめの `ROLE_BUSY` は結果が無いため rejected（ファイルの選択は残る）。
- `npm run typecheck`・`npm run lint`・`npm run lint:css` は誤り 0。

## Step 19: roletransfer — テスト

- 書いたもの: `testing/fixtures.tsx`（見本・大きさだけを持つ `File`・差し替えた API・描き方）、`useRoleTransfer.test.tsx`（10,485,760 は送る・10,485,761 は送らない、確かめた本文と指紋がそのまま適用で送られて適用の後に捨てられる、確かめの最中の選び直しで答えを捨てる、noChange、STALE でファイルの選択が残る、適用の BUSY で結果が残る、INVALID と code の無い 413、403、書き出しの保存の名前・一時の URL をすぐ捨てる・上限超えの案内、画面を離れた後の答え）・`RoleTransferPage.test.tsx`・`TransferFilePicker.test.tsx`・`TransferCheckTable.test.tsx`（行の開閉の名前と `aria-controls`、150 件の 100 件ずつ、理由と ⚠、表の下の文）・`TransferErrorList.test.tsx`・`ApplyConfirmDialog.test.tsx`・`registration.test.tsx`・`RoleTransferForbidden.test.tsx`。
- 計画との差: 計画の `RoleTransferEnglish.test.tsx` は別のファイルにせず、`RoleTransferForbidden.test.tsx` の中に英語の表示と漏えい（console・ブラウザの保存）のテストとして置いた（同じ API の差し替えと描き方を使うため）。書き出しの応答のヘッダーが `true`・無い・`false`・ほかの値のときの判定は、Step 11 の `roleTransferApi.test.ts` が持ち、画面の側は API の結果の真偽で案内の有無を確かめた。
- `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/roletransfer` → **14 ファイル・72 件 passed**（Step 9・11 の純粋な関数と API のテストを含む）。`npm run format:check`・`typecheck`・`lint` は誤り 0。

## Step 20: useradmin の広げ — 実装

- 手を入れたもの: `rowActions.ts`（`ROW_ACTION_KINDS` の最後に `viewRoles`、どの行でも最後に出す。`ConfirmActionKind` を `Exclude<RowActionKind, 'editProfile' | 'viewRoles'>` に直した。計画 2.4・D-5）・`useUserAdmin.ts`（`viewRoles` で読み取りの Modal を開く `rolesUser` と `closeRoles`）・`UserAdminPage.tsx`（`UserRolesDialog` を描き、閉じたら既存の `dialogReturnRef` で行の「操作」へ戻す）・`messages.ts`（`useradmin.menu.viewRoles`・`useradmin.roles.*`、ja・en）。足したもの: `UserRolesDialog.tsx`・`UserRolesDialog.css`。`UserRowActions.tsx` は変えていない（項目は `rowActions` から作る）。
- `UserRolesDialog` は開いたときに1回読み、403 は骨組みに渡して文言を出さない。ロールの一覧へのリンクは道の文字列（roleadmin を import しない）。
- `npm run typecheck`・`npm run lint`・`npm run lint:css` は誤り 0。

## Step 21: useradmin の広げ — テスト

- 5節 (a) の既存のテストの直し（期待を緩めず、最後に `viewRoles` を足す形）: `rowActions.test.ts`（16 通りの表の見本の作り方・並びの期待2か所に `viewRoles` を足し、性質のテストに「最後は押せる viewRoles」を足した）・`UserRowActions.test.tsx`（項目の並びに「ロールを見る」を足した）。`ConfirmActionDialog.test.tsx` は型の直しだけで変わらず通った。
- 足したもの: `UserRolesDialog.test.tsx`（6 件。題に氏名、読み込み中、出どころの並び、空、USER_NOT_FOUND とほかの失敗、403 は骨組みへ渡して文言なし、ロールの一覧へのリンク、閉じる、記号の名前、axe）、`UserAdminPage.test.tsx` に「ロールを見る」の流れを1件（Modal を開き、閉じたら行の「操作」へフォーカスが戻り、一覧は読み直さず、表の列は7のまま）。
- `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/useradmin` → **14 ファイル・164 件 passed**。`format:check`・`typecheck`・`lint` は誤り 0。

## Step 22: 画面の全体の検査

- `npm run format:check`・`npm run lint`・`npm run lint:css`・`npm run license:check`・`npm run typecheck` → すべて exit 0。新しい3つの機能にも機能どうしの import の制限が当たり、誤り 0。
- `caffeinate -i npm run test:coverage` → **165 ファイル・1,408 件 passed**（exit 0）。全体のカバレッジ 文 94.7%・**分岐 89.46%**・関数 96.89%・**行 94.83%**（下限 行 80%・分岐 70%）。

| 時点 | ファイル | 件数 | 行 | 分岐 |
|---|---|---|---|---|
| Step 3（変更の前） | 114 | 1,046 | 97.52% | 92.68% |
| Step 15（roleadmin の後） | 146 | 1,277 | 95.39% | 90.1% |
| Step 22（この段の全体） | 165 | 1,408 | 94.83% | 89.46% |

- 機能ごと（行・分岐）: `features/roleadmin` 91.24%・84.02%（`api` 88%・84.84%）、`features/groupadmin` 91.41%・84.42%（`api` 85.26%・80.51%）、`features/roletransfer` 92.55%・88.77%、`features/useradmin` 96.17%・90.93%（`api` 96.29%・95.16%）、`shared/validation` 100%・97.72%。
- 下がった主な所（分岐が低いファイル）: `roleadmin/useRolePermissions.ts` 71.42%（読み直しと消すの失敗の枝）、`groupadmin/useGroupDetail.ts` 74.41%（401 の枝・詳細と割り当てたロールの読み込みの失敗の組み合わせ）、`roleadmin/useRoleAssignments.ts` 75.3%、`groupadmin/useSlowNotice.ts` 50%・`roletransfer/useSlowNotice.ts` 62.5%（roleadmin と同じ形の複製で、専用のテストは roleadmin だけに置いた。同期の例外の枝が通らない）、`roleadmin/api`・`groupadmin/api` の形の違いの枝。新しい機能の画面の部品は 90% 前後で、全体の値が下がったのは Step 3 より高い既存の部分に、新しい部品（分岐の多いフック）が加わったため。計測の除外は足していない。

## Step 23: E2E の部品 — 実装（4回目の依頼）

- `e2e/support/adminApiRoute.ts`: `MockReply` に `{ status: number; body: string }` を足した（前もって直列化した本文をそのまま `route.fulfill` に渡す）。`status` は必須、`contentType` は `json` の形と同じ規則（400 以上は `application/problem+json`）、失敗の数え方も同じ（NFR 設計の読み直し R-09）。`json` の形の振る舞いは変えていない。
- 足したもの: `e2e/support/roleAdminFixtures.ts`（画面の型を `import type` で付けた見本。出どころは応答の形の `type`、DIRECT の `groupId`・`name` は null）・`e2e/support/u6Measure.ts`（画面の中の `performance.now()` の聞き手と見張り、`click`・`change` の捕捉の段か `responseEnd` の最初の1回、終わりは条件がそろった後の次の `requestAnimationFrame`、5 回の中央値・最大・1回目）・`e2e/support/shapeCompare.ts`（項目の名前と型の違いの道だけを返す）。
- 計画との差: 測りで開いた行の「次へ」を選択子で捕まえるため、`TransferCheckTable.tsx` のページ送りの「次へ」に `data-testid="roletransfer-changes-next"` を足した（画面の振る舞いは変えない）。既存の `userAdminFixtures.ts` の `shapeDifferences`（利用者の管理の専用）は変えず、汎用の `shapeCompare.ts` を別に置いた。
- `npm run typecheck`・`npm run lint` は誤り 0。

## Step 24: 140 — 実装と実行

- 書いたもの: `e2e/140-role-admin-accessibility.e2e.ts`。(1) 表示の設定の 20 組ごとに1つのテストで、S3 の一覧・開いた Dropdown（はみ出し）・名前の誤りの Modal、S4 の保存の誤り（BUSY）・未保存の確かめ・今の DSL に無い印、S5 の候補の Modal（割り当て済み・利用停止）・外すときの確かめ（D19）、S6 の詳細とメンバーの外しの Alert（D20）、S7 の結果の行を開いた状態と誤りの一覧（フォーカス）、S9 の Modal（閉じたら行の「操作」へ戻る）の 12 の状態を axe（違反 0・REQUIRED_RULES）・画面全体の横のはみ出しなし・開いた Dropdown と Modal の矩形が表示の中で確かめる。(2) 既定の組の 360px・768px・1280px（768px 未満は Modal の左右の余白 16px 以下、木と表が縦に積まれ、木で選ぶと表の見出しへフォーカス、S7 の表の1列目が見える）。(3) 道の扱い（`/admin/roles/7`・`/admin/groups/3` の読み込み直し、S3 → S4 → S5 の戻る・進む、未保存のときの戻るで確かめが出る）。(4) 画面の時間の測り。書き出しは押せる形だけを見る（13節 Q2: A）。
- コマンド: `caffeinate -i ./gradlew :backend:bootWar` の後に `caffeinate -i npx playwright test e2e/140-role-admin-accessibility.e2e.ts` → **25 passed（1.7 分）**。差し替えの口の変更の確かめ（5節 (i)）: `caffeinate -i npx playwright test e2e/120-… e2e/130-… e2e/135-…` → **66 passed（3.6 分）**。報告の部品: 残してはならない値 0 件。報告のフォルダーは消した。
- 測り（既定の1組、画面の中の `performance.now()`、5 回の中央値。値は記録だけ）:

| 項目 | 始め | 中央値 | 最大 | 1回目 | 目標 | 判定 |
|---|---|---|---|---|---|---|
| NFR2.5 100 カラム | click | 31 ms | 33 ms | 31 ms | 1,000 ms | Met |
| NFR2.5 1,000 カラム（1回） | click | 61 ms | — | — | 記録だけ | Recorded |
| NFR2.12 1,000 カラムの Select（1回） | change | 33 ms | — | — | 記録だけ | Recorded |
| NFR2.6 保存（回ごとに NONE・READ・FULL・NONE・READ） | responseEnd | 22 ms | 25 ms | 22 ms | 1,000 ms | Met |
| NFR2.7 確かめ（25 ロール × 1万） | responseEnd | 232 ms | 238 ms | 232 ms | 3,000 ms | Met（ヒープ 約 148 MB） |
| NFR2.8 開いた行の次のページ | click | 15 ms | 16 ms | 14 ms | 500 ms | Met |

- `cicd-pipeline.md` 7節 (iv) の確かめ: 差し替えた応答でも `PerformanceObserver` の `resource` の項目（`initiatorType: fetch`）の `responseEnd` が得られた。操作から測る形への切り替えは起きなかった（台本には得られないときの切り替えを残した）。

## Step 25: 150 — 実装と実行

- 書いたもの: `e2e/150-role-permission-flow.e2e.ts`（F の流れ、この Intent の流れの E2E の1本）。1 初期管理者の API のトークンで `E2EF_` の版 2 の DSL（スキーマ1つ・テーブル2つ・カラム3つ・テーブルを指すメニュー1つ）を投入して適用、2 `createRegisteredUser` で利用者を作り値を `recordSecretValues` で書く、3 S3 でロールを作る（作ったロールの ID は一覧の API でたどり、S4 は道で開く）、4 S4 でテーブルの主権限を READ にして保存し「保存しました」と「主権限: READ（明示）」、5 S5 で利用者をメールアドレスで検索して割り当て、表に「直接」、6 利用者の API のトークンで `/api/me/work-role` の今のロールが作ったロール、`/api/me/navigation` にテーブルの項目、7 140 の見本のすべての形（ロールの一覧・1件・木の3段・利用者の一覧・割り当て・利用者のロール・グループの一覧・詳細・グループのロール・確かめの応答）を本物と項目の名前と型で比べる（自分の `E2EF_` のグループを作って利用者をメンバーにし、ロールをグループに割り当て、確かめは新しいロールを作る YAML を check にだけ送る）。後始末はしない。
- コマンド: `caffeinate -i npx playwright test e2e/150-role-permission-flow.e2e.ts`。1回目は DSL の投入の状態コードの期待を 200 にしていて失敗した（実際は 201。投入はプレビューを作る応答）。期待を 201 に直した2回目: **1 passed（12.1 秒）**。見本と本物の応答の違いは 0 件。報告の部品: 残してはならない値 0 件（値の種類 7）。報告のフォルダーは消した。
- 計画との差: 作ったロールを一覧の行から開かず、一覧の API で ID をたどって道（`/admin/roles/{id}`）で開いた（ロールが多いと作ったロールが後のページに出るため）。S3 の一覧の画面は 140 と画面のテストが覆う。

## Step 26: 文書

- `README.md`: E2E のファイルの表に 140・150 の行を足し、「140・150 について」の注意（本数の数え方・差し替え・測りの記録・150 が適用中の DSL を置き換えること）を足した。「ロール・グループ・権限の受け渡しの画面（Intent 261004-role-menu の U6、Bolt B8）」の節を足した（未保存の確かめと、上の帯のログアウトでは確かめが出ないことがある点、入れ替えの後は開いたタブを読み込み直す点を含む）。
- 見つけた食い違い（直していない）: README の E2E のファイルの表に `135-dsl-admin-accessibility.e2e.ts` の行が無い（前の Bolt の書き漏れ）。この Bolt の範囲の外のため足さず、承認の場で扱いを確かめる。

## Step 27: 取り残しと変えないものの確かめ、入口の量の判定

- `git diff --stat develop -- backend frontend/package.json frontend/package-lock.json frontend/vitest.config.ts frontend/vitest.setup.ts frontend/playwright.config.ts frontend/vite.config.ts frontend/eslint.config.js frontend/.oxlintrc.json frontend/tsconfig.json frontend/scripts vendor docker .github build.gradle.kts` → 差なし。`frontend/src/app/` の変更は `routing/DataRouterBlocker.test.tsx` を足しただけ（`main.tsx` は `src/` の直下）。
- SHA-256（Step 3 と同じ）: `frontend/package.json` cb5cbaed…1071、`frontend/package-lock.json` 8a83a269…7ddb、`backend/gradle.lockfile` d4152054…75d5。
- `TODO`: アプリのソースには無い（記録の文書の中の語だけ）。新しい `.ts`・`.tsx`・`.css` はすべてライセンスヘッダーを持つ。
- `caffeinate -i ./gradlew frontendBundleSize`（Step 24 の前に `:backend:bootWar` で作った dist）: `assets/index-DFIDIFLN.js` 135.1 KB（gzip）・`assets/useTranslation-Fp8M4AwN.js` 16.8 KB、初回の読み込みの JavaScript の合計 **151.9 KB（gzip）**（Step 3 は 126.3 KB、差 +25.6 KB）。
- 判定（計画 10節）: (i) `RoleListPage.tsx`・`RoleDetailPage.tsx`・`GroupListPage.tsx`・`GroupDetailPage.tsx`・`RoleTransferPage.tsx` はすべて manifest で `isDynamicEntry: true`（gzip で 3.9・9.7・3.5・3.7・5.6 KB）。(ii) 入口から静的な import をたどった集まりは `index-DFIDIFLN.js` と `useTranslation-Fp8M4AwN.js` の2つだけで、これらの塊を含まない。`UserRolesDialog` は manifest に独立の項目が無く、利用者の管理の画面の遅延読み込みの塊（`UserAdminPage-Cn_5_gMa.js`、7.3 KB）の中にある。→ **満たす**。(iii) 合計の差 +25.6 KB は記録だけ（見立て、未検証: 入口が静的に読む3つの機能の `messages.ts`（ja・en の文言）と、data router（`createBrowserRouter`・`RouterProvider`）の分）。

## Step 28: 記録

- `code-summary.md`（作ったもの・決めたこと・テストの量とカバレッジ・計画との差・承認の場で確かめたいこと・後に回すこと・受け入れた制約）、`source-manifest.json`（アプリのソースの道 153。develop との差と新しいファイルから作った）、`traceability.json`（AC 88・D1〜D32・NFR の集約の行（NFR1・NFR2・NFR4・NFR6）と枝番 38、計 162 行。OK 125・Deferred 37。OK の target はすべて実在する1つのファイル。機能設計で Deferred の画面の確かめを持つ AC1.1.12・AC3.1.4・AC3.1.7・AC3.1.13 は画面のテストを target にして OK（計画 2.2 の R-03）。NFR6.4 は `frontend/vitest.config.ts` の `thresholds`、NFR2.13 は登録の `lazy` の確かめ）。
- 記録の突き合わせで見つけて直した本体の誤り: S7 の待ちの案内に、確かめは最大 15 秒・適用は最大 30 秒の文を入れていなかった（NFR2.10・`performance-design.md` 2節）。文言 `roletransfer.slow.check`・`roletransfer.slow.apply`（ja・en）を足し、`RoleTransferPage.test.tsx` に偽の時計のテスト（4.9 秒で出ず 5 秒で出て 15 秒の文を含む）を足し、`ApplyConfirmDialog.test.tsx` で 30 秒の文を確かめた。`npx vitest run src/features/roletransfer` → 73 件 passed。

## Step 29: 統合の前の関門

- 始める前の確かめ: 動いたままの Gradle のテスト・Playwright・Vitest のプロセスは無い（Gradle のデーモン1つが待ちの状態だけ）。`frontend/test-results`・`frontend/playwright-report` は無い。colima は CPU 4・メモリ 6GiB で動いており、VM の中から `/Users/agawa` が見える（ホームの共有、`colima.yaml` の `mounts` は既定）。Mailpit（`mastersmith-mailpit-1`）は healthy で動いていたため起動のコマンドは流していない。
- 1回目の verify（15:27〜15:52、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`）→ **BUILD FAILED（25 分 18 秒）**。
  - 通った段: 書式・リンタ・ライセンス・ビルド、画面のテスト（`frontendTest`）**165 ファイル・1,409 件 passed**、バックエンドの単体テスト **240 クラス・2,082 件**（失敗・中断 0）、結合テスト **222 クラス・1,165 件**（失敗・中断 0、対象DB のテストも SKIPPED 無し）。
  - 落ちた段: `frontendCoverage`（15:40:31 開始）で `InviteDialog.test.tsx` と `LoginLanguageSwitch.test.tsx` の各1件が「Test timed out in 5000ms」（経過 約 662 秒）。162 ファイル中 2 failed、ほかに Errors 3。
  - 原因: `pmset -g log` で、PC が 15:40:52 に「Dark Wake Thermal Emergency」でスリープに入り 15:51:56 まで止まっていた（664 秒）。作業の間 PC は利用者のいない DarkWake の状態で、`caffeinate -i` はこのスリープを止められない。テストの時間がスリープの長さと一致するため、テストやコードの誤りではなく PC の休止による中断と判定した。その後も 15:52:41 に「Maintenance Sleep」へ入り、16:08 に DarkWake で戻るのを繰り返している。
  - 扱い: 落ちた2件は今回が初めて（B4 の 135・B8 Step 5 の 060 とは別）。コードは変えていない。PC が休止を繰り返す状態では verify（約 25 分）と E2E の全体（約 8 分）を通して流せないため、流し直さずに止めた。カバレッジ（画面・JaCoCo）とバックエンドのカバレッジの下限の段はこの回では得られていない。
- E2E の全体（010〜150）は流していない。
- 次にすること: 依頼者が PC を起こした状態（画面が点いた通常の状態、電源につないだまま）で、同じ verify を流し直し、続けて E2E の全体を流す。
- 2回目（6回目の依頼、依頼者が PC を起こした後）:
  - 流す前の確かめ: `pmset -g log` で 22:39:38 に「DarkWake to FullWake（HID Activity）」と「Display is turned on」、`pmset -g assertions` で `UserIsActive 1`、ふたは開いている（`AppleClamshellState = No`）、電源は AC。colima・Mailpit・配備したアプリは動いたまま。報告のフォルダーは無い。画面を消さないよう、今回は `caffeinate -di` で包んだ。
  - verify（2026-10-12 22:40:47〜22:56:20、README の環境変数を付けて `caffeinate -di ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`）→ **BUILD SUCCESSFUL（15 分 32 秒）**。
    - 画面のテスト（`frontendTest`）: 165 ファイル・1,409 件 passed。画面のカバレッジ（`frontendCoverage`）: 165 ファイル・1,409 件 passed、文 94.75%・**分岐 89.59%**・関数 96.96%・**行 94.88%**（下限 行 80%・分岐 70%）。前回落ちた `InviteDialog.test.tsx`・`LoginLanguageSwitch.test.tsx` も通った。
    - バックエンド: 単体テスト 240 クラス・2,082 件、結合テスト 222 クラス・1,165 件（失敗・中断・SKIPPED 0、対象DB のテストを含む）。JaCoCo の全体 **行 97.92%・分岐 92.44%**。`jacocoTestCoverageVerification`（全体とパッケージごとの下限）を通った。バックエンドのソースはこの Bolt で変えていない。
    - セキュリティ: Gitleaks は「no leaks found」。SpotBugs は止める基準（priority 1・`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）に当たる指摘なし（priority 2・3 の警告だけで、`spotbugsMain` は UP-TO-DATE）。`osvScan` は UP-TO-DATE（lockfile は Step 3 と同じ SHA-256 で入力が変わらないため、前に通った結果のまま）。`verifySecurity`・`verifyArtifact` を通った。
  - E2E の全体（2026-10-12 22:57:00〜23:06:08、Mailpit は動いたまま、`caffeinate -di ./gradlew e2eTest`、bootWar を含む）→ **203 passed（9.0 分）**、failed・flaky 0、**BUILD SUCCESSFUL（9 分 7 秒）**。件数は 010〜135 の 177 件 + 140 の 25 件 + 150 の 1 件。B4 の 135・B8 Step 5 の 060 で1回ずつ落ちたテストも今回は通った。
  - 報告の部品: 「E2E の報告に残してはならない値は含まれていません（値の種類 7・json の報告 1・json の報告の添付 300・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。json の報告の文字列の検索も報告の部品が行い、0 件。報告のフォルダー（`frontend/test-results`）は中を開かずに消した（`frontend/playwright-report` は作られていない）。
  - 流した後の確かめ: `pmset -g log` で 22:39:38 の FullWake の後、23:06 まで Sleep・DarkWake の行は無い（verify と E2E の間に休止なし）。
  - 判定: 統合の前の関門を満たす。コードは変えていない。
