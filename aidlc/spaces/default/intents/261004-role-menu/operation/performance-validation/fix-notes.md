# 直しの記録: 英語の画面の DSL の誤り（1件）に全角のコロンが出る

Performance Validation の試し走り（`perf/ui/dsl-ui-lang.mjs --lang` の `versionError` の段）で見つかった画面の不具合の直し。依頼者の決定（`script-fixes.md` 3節の A: アプリを直して配備し直す）による。作業ブランチは `fix/261004-role-menu-pv`（基点 `f581916`）。コミット・統合・配備はまだ行っていない。

## 1. 原因

- `frontend/src/features/dsl/DslErrorList.tsx` の誤りが1件だけ（危険な形・版の非対応）のときの表示で、種類と理由の区切りを JSX に `{'：'}` と直書きしていた（Intent 260923-dsl-schema-loader の U5、`345a94e` から）。
- そのため、英語の画面でも `Format version：…` と全角のコロンが出ていた。種類（`dsl.errorKind.*`）と理由（応答の `message`）は言語に合わせて出ており、区切りだけが文言の仕組みの外にあった。
- team.md の Code Style「画面の文言は日本語と英語の両方を用意する」に反する。

## 2. 直し

| ファイル | 変更 |
|---|---|
| `frontend/src/features/dsl/messages.ts` | 文言の鍵 `dsl.errors.kindSeparator` を足した。日本語は `：`、英語は `: `（コロンと半角の空白）。既存の `dsl.errors.*` の鍵の並び（`dsl.errors.single` の次）に置いた |
| `frontend/src/features/dsl/DslErrorList.tsx` | `{'：'}` を `{t('dsl.errors.kindSeparator')}` に替えた。先頭の説明に、区切りを文言の鍵から引くことを1行足した |

- 種類と理由を別の `span` に置く今の作り（`dsl-error-kind` の見た目）は変えていない。区切りだけを鍵にしたのは、理由（応答の `message`）を文言の中に埋めずに React の文字として描く今の形（NFR3.9）を保つため。
- 日本語の表示は直す前と同じ（`書式の版：版 2 には対応していません` の形）。

## 3. テスト（直す前に失敗したこと）

`frontend/src/features/dsl/DslErrorList.test.tsx` に2件を足した。

| テスト | 確かめること | 直す前 | 直した後 |
|---|---|---|---|
| `separates the kind and the reason with an English colon on the English screen` | 英語の画面（`en-US`）で、版の非対応の誤り1件の表示が `Format version: Version 2 is not supported` になり、全角の `：` を含まないこと | 失敗（受け取った文字は `Format version：Version 2 is not supported`） | 通る |
| `keeps the full-width colon between the kind and the reason on the Japanese screen` | 日本語の画面で、表示の文字がちょうど `書式の版：版 2 には対応していません` であること（今の形のまま） | 通る | 通る |

- 直す前の実行: このファイルの 15 件のうち 1 件が失敗、14 件が通った（上の英語の1件だけが失敗）。
- 既存の `shows only the reason for a single dangerous-shape error`（`大きさ：大きすぎます`）と `DslAdminPage.test.tsx` の日本語の確かめ（`書式の版：…`）は、日本語の表示のため変えずに通る。

## 4. 確かめの結果

| 確かめ | 結果 |
|---|---|
| Prettier（直した3ファイル） | 通った |
| `npm run lint`（oxlint・ESLint） | 通った |
| `npm run typecheck` | 通った |
| `npm run license:check` | 通った |
| 直したファイルの単独の実行（3回） | 3回とも 15 件すべて通った |
| フロントの全体（`npm test`、1回） | 186 ファイル・1586 件すべて通った |
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -dims` で包んだ） | `BUILD SUCCESSFUL in 14m 15s`（2026-10-15 04:02:23〜04:16:39） |
| 　バックエンドの単体テスト | 2102 件（失敗 0・誤り 0・飛ばし 0） |
| 　バックエンドの結合テスト | 1165 件（失敗 0・誤り 0・飛ばし 0。対象DB のテストも飛ばしていない） |
| 　バックエンドのカバレッジ（JaCoCo、全体） | 行 97.93%（10525/10748）、分岐 92.47%（4013/4340） |
| 　フロントのテスト（verify の中） | 186 ファイル・1586 件すべて通った |
| 　フロントのカバレッジ（v8、全体） | 行 95.42%、分岐 90.34%、文 95.25%、関数 96.76% |
| `./gradlew e2eTest`（`E2E_PORT=18081`、配備したアプリの 8080 とぶつからない番号。`caffeinate -dims` で包んだ） | 230 件 passed（10.3 分、2026-10-15 04:17:15〜04:27:34）。`:backend:bootWar` は UP-TO-DATE で、verify の WAR をそのまま使った |
| 眠りの確かめ（`pmset -g log`） | verify と E2E の間（04:02〜04:28）に Sleep・DarkWake・Wake の記録は無かった |
| 配備したアプリ・mailpit・見本の対象DB | 止めていない（`mastersmith-app-1` は healthy のまま） |

- verify で作り直した WAR: `backend/build/libs/mastersmith.war`、SHA-256 `310298a7c26364ae9957c259f87044f88d082a0777a2020188a4cc651c2e0eb9`（102,383,245 バイト、2026-10-15 04:16）。E2E の後も同じ値。
- WAR の中の画面の JS に、`dsl.errors.kindSeparator` の日本語（`：`）と英語（`: `）の両方の値と、それを引く箇所があることを確かめた。
- この WAR は未コミットの作業フォルダ（`perf/` の3ファイルの未コミットの変更を含む。`perf/` は WAR に入らない）から作った。配備の前に、コミットの後の版で WAR を作り直すか、この SHA-256 と中身を確かめ直す。

## 5. 同じ種類の固定の検索の一覧

`frontend/src/` の `*.ts`・`*.tsx`（テストのファイルを除く）から、コメントを除いた行で、日本語の文字（ひらがな・カタカナ・漢字）と全角の記号（U+3000〜303F・U+FF00〜FFEF）を含むものを探した。文言の辞書（各機能の `messages.ts`・`app/i18n/messages/ja.ts`・`registration.ts` の `ja` の辞書）と、テストの手伝い（`testing/`）は外した。英語の辞書（各機能の `messages.ts` の英語の側・`app/i18n/messages/en.ts`・`registration.ts` の `en`）には、日本語の文字・全角の記号は無かった。

### 5.1 英語の画面にも出るもの（直していない。報告のみ）

| 場所 | 中身 | 出る画面 |
|---|---|---|
| `frontend/src/features/dsl/DslMenuTree.tsx:111` | メニューの項目のテーブル名を全角の括弧で囲む `（{schema}.{name}）` | DSL の管理の画面のプレビュー（メニューの木） |
| `frontend/src/features/dsl/DslSubmitForm.tsx:150` | 選んだファイルの名前と大きさを全角の括弧で囲む `` `${name}（${size}）` `` | DSL の管理の画面の投入（ファイルを選んだ後） |
| `frontend/src/features/dsl/DslDiffTable.tsx:84` | 変わった項目を中黒でつなぐ `join('・')` | DSL の管理の画面のプレビュー（違いの表のカラムの行） |

- 3件とも DSL の管理の画面にあり、英語の画面にも全角の記号が出る。依頼の範囲（DSL の誤りの表示）の外のため直していない。
- `perf/ui/dsl-ui-lang.mjs --lang` が画面の文字に全角の記号が無いことを条件にしているなら、これらが写る段（プレビュー・ファイルを選んだ後）でも `pass: false` になりうる。台本がどの範囲の文字を見るかを、測りの前に確かめる必要がある。

### 5.2 画面に出ない、または言語によらないもの（直す要なし）

| 場所 | 中身 | 判断 |
|---|---|---|
| `frontend/src/app/display-settings/displaySettingsTypes.ts:75` | 言語の選択肢の名前 `ja: '日本語'` | 言語の自分の名前（自称）で、どの言語の画面でも同じに出すのが慣例 |
| `frontend/src/main.tsx:53`、`app/display-settings/DisplaySettingsProvider.tsx:280`、`app/admin-forbidden/AdminForbiddenProvider.tsx:54`、`features/preferences/preferencesApi.ts:66` | `Error` の文言 | 作りの誤りや応答の形の違いを開発者に知らせる例外の文言で、画面には出さない |
| `frontend/src/app/registry/loadRegistrations.ts:41`、`app/registry/validateRegistrations.ts`（22 行）、`app/registry/types.ts:154` | 画面の登録の検査の文言 | 起動時の登録の誤りを開発者に知らせる文言で、利用者の画面には出さない |
| `frontend/src/features/auth/registration.ts`・`features/admin/registration.ts` | `ja` の文言の辞書 | 同じファイルに `en` の辞書がある（文言の仕組みの中） |

## 6. 追加の直し: 5.1 の3件（依頼者の決定 A）

依頼者は、5.1 の3件もこの直しと一緒に直す形（A）を選んだ。DslErrorList と同じ形で、再現のテストを先に書き、直す前に失敗することを確かめてから文言の鍵に移した。日本語は今の記号のまま、英語は既存の英語の文言の形（括弧は半角の ` (…)`、つなぎは `, `）にそろえた。

### 6.1 直し

| ファイル | 変更 |
|---|---|
| `frontend/src/features/dsl/messages.ts` | 鍵を3つ足した。`dsl.menu.tableName`（ja `（{{schema}}.{{name}}）`・en ` ({{schema}}.{{name}})`）、`dsl.submit.fileNameSize`（ja `{{name}}（{{size}}）`・en `{{name}} ({{size}})`）、`dsl.diff.changedItemsSeparator`（ja `・`・en `, `） |
| `frontend/src/features/dsl/DslMenuTree.tsx` | 全角の括弧の直書きを `t('dsl.menu.tableName', { schema, name })` に替えた（span の作りは同じ） |
| `frontend/src/features/dsl/DslSubmitForm.tsx` | テンプレート文字列を `t('dsl.submit.fileNameSize', { name, size })` に替えた |
| `frontend/src/features/dsl/DslDiffTable.tsx` | `join('・')` を `join(t('dsl.diff.changedItemsSeparator'))` に替えた |

- 埋める値（スキーマ名・テーブル名・ファイル名）は、i18next の `escapeValue: false` と React の文字として描く今の形のまま。HTML に見える名前を文字として出す既存のテスト（`DslMenuTree.test.tsx` の `<b>"s"</b>`）は変えずに通る。

### 6.2 テスト（直す前に失敗したこと）

| テスト | 直す前 | 直した後 |
|---|---|---|
| `DslMenuTree.test.tsx` の `shows English labels without translating the physical names`（期待を `Departments (sales.dept_mst)` に直し、全角の括弧を含まないことを足した。既存のテストが不具合の形 `Departments（sales.dept_mst）` を期待していた） | 失敗（受け取った文字は `Departments（sales.dept_mst）`） | 通る |
| `DslMenuTree.test.tsx` の `keeps the full-width parentheses around the schema and table names in Japanese`（新規） | 通る | 通る |
| `DslSubmitForm.test.tsx` の `shows the chosen file name and size with half-width parentheses in English`（新規） | 失敗（受け取った文字は `master-dsl-v3.yaml（1.5KB）`） | 通る |
| `DslSubmitForm.test.tsx` の `keeps the full-width parentheses around the file size in Japanese`（新規） | 通る | 通る |
| `DslDiffTable.test.tsx` の `joins the changed items with a comma in English`（新規） | 失敗（描かれた文字は `label.ja・dbType.length`） | 通る |

- フロント全体を流したとき、`DslPreviewPanel.test.tsx` の `shows English text` が落ちた。既存のテストが英語のメニューの木で不具合の形 `Departments（sales.dept_mst）` を期待していたためで、直しの結果そのものである。期待を `Departments (sales.dept_mst)` に直した。
- 日本語の既存の確かめ（`部署（sales.dept_mst）`・`master-dsl-v3.yaml（10.0MB）`・`label.ja・dbType.length`）は変えずに通る。

### 6.3 `perf/ui/dsl-ui-lang.mjs`（台本は変えていない）の判定の対象

- 判定は、英語の利用者で `/admin/dsl` を開き、貼り付けで5回投入し、各段で `dsl-warning-list`（`DslWarningList`）と `dsl-submit-errors`（`DslAdminPage` の中の `DslErrorList`）の `innerText` を見る。`JAPANESE = /[　-ヿ一-鿿＀-￯]/`（U+3000〜30FF・U+4E00〜9FFF・U+FF00〜FFEF）に当たれば `shownTextJapanese` になり、`pass: false` になる。あわせて、応答の `message` に日本語が無いことと、画面にその `message` が出ていることも見る。`dsl-alert` の文字は記録するが、判定には使わない。
- メニューの木（6.1 の `DslMenuTree`）・ファイルの名前（`DslSubmitForm`）・違いの表（`DslDiffTable`）は判定の対象の外だった。直したので、画面のどこに出ても英語の形になる。
- 判定の対象の2つの部品の、直した後の固定の文字:
  - `DslWarningList`: 種類の見出しは文言の鍵（英語の辞書に日本語・全角の記号は無い）。場所と内容のつなぎは ` … `（U+2026。正規表現の範囲の外）。場所と内容は応答の値。
  - `DslErrorList`: 区切りは `dsl.errors.kindSeparator`（英語は `: `）。見出し・件数は文言の鍵。値が無いときの `—`（U+2014。範囲の外）。種類・行・列・場所・内容は文言の鍵か応答の値。
- 台本が投入する DSL の表示名（`label`）は ja・en とも英数字で、日本語は入らない。
- このため、直した後の版では、判定に掛かる日本語・全角の記号はフロントの側に残っていない。残る条件は応答の `message` が英語であることで、これはサーバーの側（前の試し走りで `serverMessagesJapanese` が5つの段すべてで false）である。

### 6.4 確かめの結果（追加の直しの後）

| 確かめ | 結果 |
|---|---|
| Prettier・`npm run lint`・`npm run typecheck`・`npm run license:check` | 通った |
| 直した4ファイルのテスト（DslErrorList・DslMenuTree・DslSubmitForm・DslDiffTable）の単独の実行（3回） | 3回とも 51 件すべて通った |
| フロント全体（`npm test`） | 186 ファイル・1590 件すべて通った（`DslPreviewPanel.test.tsx` の期待を直した後） |
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-15 04:52:27〜04:53:35） | **失敗**（`:frontendTest`）。テストは 186 ファイル・1590 件すべて通ったが、Vitest が Unhandled Rejection を1件拾って全体を失敗にした（下の 6.5） |
| `./gradlew e2eTest` | verify の失敗で流していない |

- 04:53 に `backend/build/libs/mastersmith.war` が作り直されたが（SHA-256 の先頭 `4bf6e68ca1d996b4`）、verify が通っていないため確かめた版としては記録しない。4節の `310298a7…` は DslErrorList だけを直した時点の WAR で、6.1 の直しを含まない。

### 6.5 計画の外の失敗（依頼者の決定 B で 7節のとおり直した）

- **事実**: verify の中のフロントのテストで、`src/app/work-role/WorkRoleSwitcher.test.tsx:70` の `shows text without a button while loading and after a failed first load` で、`answer.reject(new Error('down'))` の拒否が Unhandled Rejection として拾われた（`PromiseRejectionHandledWarning: Promise rejection was handled asynchronously` も出た）。テストの件数としては全件通っている。今回の変更の経路（`features/dsl`）に触れていないテストである（`0415b94` の B9 で入った）。
- **再現の試み**: 単独で5回、フロント全体で1回（`caffeinate -dims`）流し、どれも再現しなかった。verify の前後の `pmset -g log` に Sleep・DarkWake は無い（04:52:12 の Wake は始める前）。
- **見立て（未検証）**: 拒否を作った直後は処理が付いておらず、描画の後の効果で後から処理が付くため、そのすき間で Node が「処理されていない拒否」と判定することがある（負荷による時間の差に左右される）。同じ形の失敗は前に ShellLayout・AppRouter で直している（`2161ce0`）。
- team.md の「不安定なテストと CI の失敗」では、手元で再現しないものは試みの範囲を記録したうえで、流し直して通れば進めてよい（不安定と確かめられていない扱い）。同じテストが二度目に落ちたら、原因を直すまで進まない。

## 7. 6.5 の直し: テストの非同期の危うさ（依頼者の決定 B）

依頼者は B を選んだ。6.5 の失敗を、`2161ce0` の ShellLayout と同じ形にテストだけで直し、同じ形の危うさを `frontend/src/` の画面のテスト全体で探した。本体のコードは変えていない。

### 7.1 WorkRoleSwitcher の直し

- `frontend/src/app/work-role/WorkRoleSwitcher.test.tsx` の `shows text without a button while loading and after a failed first load`。拒否の前に、偽の API の読み込みが呼ばれたことを `await waitFor(() => expect(calls.get).toBeGreaterThan(0))` で待つ形にした。理由はコメントに書いた。時間の上限は変えていない。
- 本体のコードの不具合ではないと判断した。`WorkRoleProvider` は、ログインしていて初めての読み込みの前（`phase: 'idle'`）でも状態を `loading` として見せる。そのため「読み込んでいます」が出た時点では、まだ `getWorkRole` が呼ばれていないことがある。一方、呼んだときは同じ処理の中で `.then(成功, 失敗)` を付けるので、呼んだ後の拒否は必ず受け取られる。危うさは、呼ばれる前に deferred を拒否するテストの側にある。
- 確かめ: 単独で5回流し、5回とも 10 件すべて通った（Unhandled なし）。直す前の失敗は手元で再現できていないため、この直しで当の失敗が消えたことは、流し直しの結果でしか裏付けられていない。

### 7.2 同じ形の検索と扱い

**1. 偽の要求・deferred を拒否する箇所**（テストの中の `.reject(`。`Promise.reject`・`mockRejectedValue` は呼ばれた時点で受け取る側が付くため外した）

| 場所 | 扱い | 理由 |
|---|---|---|
| `app/work-role/WorkRoleSwitcher.test.tsx:70` | 直した（7.1） | 呼ばれる前に見える状態（loading）を待って拒否していた |
| `app/layout/ShellLayout.test.tsx:705` | 直さない | `2161ce0` で直し済み（`nav.count` を待つ） |
| `app/navigation/BusinessNavigationProvider.test.tsx:140` | 直さない | 待っている `reloading` は `reload()` の中で状態を変えるのと同じ処理で `load().then(…)` を呼ぶため、`reloading` が見えた時点で受け取る側は必ず付いている |
| `features/useradmin/UserAdminPage.test.tsx:950・953` | 直さない | 拒否の前に `waitFor(() => expect(fake.list).toHaveBeenCalledTimes(3))` で呼ばれたことを待っている |
| `features/invitation/InvitationAdminPage.test.tsx:796・817` | 直さない | 拒否の前に `await user.click(...)` が終わっている。`createInvitation`・`cancelInvitation` は押したときの処理（`submitInvite`・`confirmCancel`）の中で同期に呼ばれ、同時に `.then` が付く（`useInvitationAdmin.ts` の 303・454 行） |
| `shared/tree/SharedTreeView.test.tsx:228・316・345` | 直さない | 拒否の前に `loadChildren` の呼ばれた回数を `waitFor` で待っている。さらに `take()` は呼ばれていなければ例外にする |

**2. 呼び出しを同期で確かめる箇所**（`await …find…` などの直後に、`waitFor` の外で `toHaveBeenCalled*` を確かめる。機械の検索で 33 か所）

- 直したものは無い。どれも、確かめる呼び出しが押したときの処理の中で同期に起きるか、待った画面の結果が呼び出しの答えに依るため、結果が見えた時点で呼び出しは終わっている。前者は `clear-intent-probe` の `onClick`、ログアウト・保存・削除のボタンなど。後者は一覧・木・ダイアログの中身など。
- `RoleDetailPage.test.tsx:180・185` と `SharedTreeView.test.tsx:189` は、呼ばれる回数が増えないことを確かめるもので、待つ形にすると確かめにならない。
- `2161ce0` の AppRouter の形（描画の後の効果の中で呼ぶもの、`clearLogoutIntent` など）は、ほかに見つからなかった。`LoginStateGate.test.tsx:260` は同じ `clearLogoutIntent` だが、ボタンの `onClick` から直接呼ぶ形で、効果ではない。

### 7.3 確かめの結果（7.1 の後。6節の直しも含む最終の状態）

| 確かめ | 結果 |
|---|---|
| Prettier（`frontend/src` 全体）・`npm run lint`・`npm run typecheck` | 通った |
| `WorkRoleSwitcher.test.tsx` の単独の実行（5回） | 5回とも 10 件すべて通った |
| フロント全体（`npm test`、1回） | 186 ファイル・1590 件すべて通った（Unhandled なし） |
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`、`caffeinate -dims`。2026-10-15 05:06:01〜05:20:10） | `BUILD SUCCESSFUL in 14m 9s`。Vitest の Errors（Unhandled）なし |
| 　バックエンド | 単体 2102 件・結合 1165 件（失敗・誤り・飛ばしはどれも 0）。カバレッジは行 97.93%（10525/10748）・分岐 92.47%（4013/4340） |
| 　フロント（verify の中） | 1590 件すべて通った。カバレッジは行 95.42%・分岐 90.34%・文 95.25%・関数 96.76% |
| `./gradlew e2eTest`（`E2E_PORT=18081`、`caffeinate -dims`。05:20〜05:30:22） | 230 件 passed（10.1 分）。`:backend:bootWar` は UP-TO-DATE |
| 眠りの確かめ（`pmset -g log`、05:06〜05:31） | Sleep・DarkWake・Wake は無い |
| 配備したアプリ・mailpit・見本の対象DB | 止めていない（`mastersmith-app-1` は healthy のまま） |

- 確かめた WAR: `backend/build/libs/mastersmith.war`、SHA-256 `4bf6e68ca1d996b47b419023590a8034c0656fe4cce26f07d0d809fa741febd3`（102,383,315 バイト、2026-10-15 04:53 に作成）。
  - 7.1 はテストだけの変更のため、verify の中で WAR は作り直されず、04:53 の WAR がそのまま使われた。verify と E2E の後も同じ値だった。
  - 画面の JS には、4つの鍵（`dsl.errors.kindSeparator`・`dsl.menu.tableName`・`dsl.submit.fileNameSize`・`dsl.diff.changedItemsSeparator`）の日本語と英語の両方の値があることを確かめた。
  - この WAR は未コミットの作業フォルダから作った（`perf/` の未コミットの変更は WAR に入らない）。配備の前に、コミットした後の版で作り直すか、この値を確かめ直す。
- 参考: 6.4 の verify が失敗した後、同じ流れの中で E2E がひとりでに流れ、04:53 の WAR で 230 件 passed だった（05:03:55 に終了）。verify が通る前の実行のため、確かめには数えない。

## Sources

- 依頼の文（不具合の事実・すること・守ること）。
- `aidlc/spaces/default/intents/261004-role-menu/operation/performance-validation/script-fixes.md` 3節（不具合の見つかった経緯と選択肢）。
- `frontend/src/features/dsl/DslErrorList.tsx`・`messages.ts`・`useDslText.ts`・`DslErrorList.test.tsx` のソース。`git log` の `345a94e`。
- team.md の Code Style（画面の文言は日本語と英語の両方）・Testing Posture、project.md の Mandated（不具合を再現するテストを同じコミットに）と Testing Posture（`cleanTest`・`cleanIntegrationTest` を付けた実測、`caffeinate -dims` と `pmset -g log`）。
- 実行の記録: verify と E2E の出力、`backend/build/test-results/`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`、`shasum -a 256`、`pmset -g log`。

## Assumptions & Open Questions

- [assumption] 英語の区切りは、英語の文の慣例に合わせて半角のコロンと半角の空白 `: ` とした。日本語は今の `：`（空白なし）のまま。
- 5.1 の3件は、依頼者の決定（A）で 6節のとおり直した。
- 6.3 で確かめた。台本の判定は 5.1 の箇所を写さず、直した後の版で判定に掛かるフロントの固定の文字は残っていない。
- 6.5 は依頼者の決定（B）で 7節のとおりテストだけで直し、verify と E2E が通った。直す前の失敗が手元で再現できなかったため、直しで当の失敗が消えたことは、その後の verify・CI が通ることでしか裏付けられない。
- 未決: コミットの区切り。案は次の3つ。C1 は DSL の管理の画面の11ファイル（DslErrorList と 6節の3件の直し・再現のテスト・`DslPreviewPanel.test.tsx` の期待の直し）で、件名は「DSL の管理の画面で英語の表示に全角の記号が出ないようにする（文言の鍵に移し、再現のテストを足す）」。C2 はテストの非同期の直し（`WorkRoleSwitcher.test.tsx`）。記録は別のコミットにする。
- 未決: `develop` への統合、イメージの作り直しと配備のやり直し。
- 作業の後、PC が 05:31 から眠りに入った（`pmset -g log` の Sleep・DarkWake は 05:31〜06:50）。7.3 の verify（05:06〜05:20）と E2E（05:20〜05:30:22）はその前に終わっており、流した間に Sleep・DarkWake は無い。再開の後（06:55）に書式・lint・型と `WorkRoleSwitcher.test.tsx` の単独の実行（3回、10 件すべて通った）を流し直し、作業フォルダと WAR の SHA-256 が 7.3 のときと同じであることを確かめた。
