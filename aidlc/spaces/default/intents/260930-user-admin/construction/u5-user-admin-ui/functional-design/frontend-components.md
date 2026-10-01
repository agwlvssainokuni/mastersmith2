# Frontend Components — U5 利用者の管理の画面（u5-user-admin-ui）

画面の流れ（W1〜W12）・状態の移り変わり・決まり（D1〜D20）・文言の正本は `functional-spec.md`。この文書は、部品の階層、置き場ごとのモジュール、`useUserAdmin` の状態と操作、部品ごとの props と state、入力の確かめ、API との受け渡し（契約 C3）、使う口（C4・C5）、機能の登録、テストで確かめる内容を書く。部品の名前は `interaction-spec.md` の名前（UserAdminPage・UserSearchBox・UserTable・UserRowActions・ConfirmActionDialog・EditProfileDialog）を正式の名前にする（設計の要点 2）。

## 1. 部品の階層

```
AppShell（骨組み、既存。U4 の AppFrame が 403 のときコンテンツを AdminForbiddenView に置き換える）
└─ UserAdminPage（features/useradmin、遅延読み込み）
   ├─ h1「利用者の管理」
   ├─ UserSearchBox（role="search"。FormField・TextInput・Button×2）〔Should〕
   ├─ 業務の失敗の知らせ（Alert warning、1つだけ。[×] で閉じる）
   ├─ 読み込みの失敗（Alert error と「もう一度読み込む」）… load-error のとき
   ├─ h2「利用者の一覧」（tabIndex=-1、フォーカスの行き先）
   ├─ 状態の読み上げ（role="status"。読み込み中・件数の範囲）
   ├─ UserTable（make-you-chic-ui の Table、aria-label「利用者の一覧」）
   │  └─ 行ごと: Badge（管理者・状態・ロック中・あなた）と UserRowActions
   │     └─ UserRowActions（Button＋Dropdown）
   ├─ ConfirmActionDialog（Modal、5種類）… 確かめの対象があるとき
   └─ EditProfileDialog（Modal、FormField・TextInput・RadioGroup）… 入力の対象があるとき
（Toast は make-you-chic-ui の useToast で出す）
```

文字の代替: 骨組みの AppShell の中に UserAdminPage を置く。UserAdminPage は見出し、検索（UserSearchBox）、業務の失敗の知らせ、読み込みの失敗の表示、一覧の見出し、状態の読み上げ、一覧の表（UserTable）を縦に並べ、表の各行に印（Badge）と行の操作（UserRowActions）を置く。確かめの表示（ConfirmActionDialog）と氏名・言語の入力（EditProfileDialog）は、対象があるときだけ Modal で重ねる。403 のときは AppFrame がコンテンツの領域ごと U4 の AdminForbiddenView に置き換えるため、UserAdminPage は外れる。

## 2. 置き場ごとのモジュール

### 2.1 `frontend/src/features/useradmin/`（新しい）

| ファイル | 中身 | テスト |
|---|---|---|
| `registration.ts` | 機能の登録（6節） | `registration.test.tsx` |
| `messages.ts` | 文言 ja・en（`functional-spec.md` 7節、鍵は `useradmin.`） | 鍵の対がそろうことを `registration.test.tsx` か `messages.test.ts` で |
| `useUserAdminText.ts` | 文言を引く関数（差し込みつき）。招待の `useInvitationText` と同じ形 | 画面のテストで |
| `UserAdminPage.tsx`・`.css` | 画面。`useUserAdmin` を呼び、部品を並べる | `UserAdminPage.test.tsx` |
| `useUserAdmin.ts` | 画面の状態と操作（3節） | `UserAdminPage.test.tsx` で |
| `UserSearchBox.tsx`・`.css` | 検索 | `UserSearchBox.test.tsx` |
| `UserTable.tsx`・`.css` | 一覧の表（Table の列の描き方） | `UserTable.test.tsx` |
| `UserRowActions.tsx` | 行の「操作」とメニュー | `UserRowActions.test.tsx` |
| `ConfirmActionDialog.tsx` | 確かめの表示（5種類） | `ConfirmActionDialog.test.tsx` |
| `EditProfileDialog.tsx`・`.css` | 氏名・言語の入力 | `EditProfileDialog.test.tsx` |
| `rowActions.ts` | 行の値から項目と押せなさを決める純粋な関数（W4、D5・D6） | `rowActions.test.ts`（性質ベース） |
| `searchInput.ts` | 検索の文字の確かめ（D15） | `searchInput.test.ts`（性質ベース） |
| `profileInput.ts` | 氏名の確かめ（`validateDisplayName`）とサーバーの `reason` を寄せる表、項目と理由から文言の鍵 | `profileInput.test.ts` |
| `lockedUntil.ts` | 解除の予定の時刻の書式（D18、今日なら時刻だけ） | `lockedUntil.test.ts`（時間帯と今の時刻を引数で固定） |
| `failureMessage.ts` | 失敗から文言の鍵と code を選ぶ（D8、7.5） | `failureMessage.test.ts` |
| `focusTarget.ts` | フォーカスの行き先の型と、読み直しの後の行き先を決める純粋な関数（W12） | `focusTarget.test.ts` |
| `api/types.ts` | 応答・要求の型（5節） | — |
| `api/userAdminApi.ts` | API の関数（5節） | `api/userAdminApi.test.ts` |
| `testing/fixtures.ts`・`testing/renderUserAdmin.tsx` | テストの見本の行（2語の氏名・長いメールアドレス・ロック中・停止中・自分の行）と描画の手伝い | — |

- CSS は部品と同じ場所に素の CSS で置く（`team.md`）。狭い画面向けの形は作らず、表の外側を横にずらせるようにするだけ（RQ7 C）。
- すべてのソースファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。エクスポートは名前付きだけ、`enum` は使わず文字列の union にする（`team.md`）。

### 2.2 `frontend/src/shared/`（移す・使う）

| ファイル | 扱い |
|---|---|
| `shared/api-client/fieldErrors.ts`（移す）・`fieldErrors.test.ts`（移す） | `features/preferences/fieldErrors.ts` とそのテストを移す（Q3 A）。口 `readFieldErrors`・`ReadFieldError` は変えない。プリファレンスの読み込み先を直す |
| `shared/paging/paging.ts`（U2 が移したもの） | `PAGE_SIZE`・`pageCount`・`pageRange`・`correctedPage`・`pagerButtonDisabledAfter`・`PagerDirection` を使う（C5） |
| `shared/format/formatDateTime.ts` | 登録した日時と、解除の予定の時刻が今日でないとき（D18） |
| `shared/validation/validateDisplayName.ts`・`codePoints.ts`・`limits.ts` | 氏名の確かめ、検索の文字の前後の空白の除き方（`trimDisplayName`）とコードポイントの数え方（`countCodePoints`） |
| `shared/api-client/apiClient.ts`・`apiError.ts` | 要求と失敗の型（`ApiError`） |

### 2.3 U4 の口（C4、`src/app/` と `src/shared/api-client/`）

| 口 | 使い方 |
|---|---|
| `useAdminForbidden()` | 画面の始めに1回呼び、返った関数に一覧・操作・保存の失敗と要求のパスを渡す。真ならその失敗について何もしない（D11） |
| `useApplyOwnProfile()` | 自分の行の保存の成功の後に、送った `{ displayName, language }` を渡す（D17） |
| `useDisplaySettings()`（既存） | 画面の言語 `language`（日時の書式と文言の言語） |

- 名前は C4 のまま（U4 の機能設計で変えないと決めた）。U4 の機能設計の成果物で名前が変わったら、コード生成の計画で合わせる。

### 2.4 そのほかのリポジトリの作業

| 作業 | 内容 |
|---|---|
| make-you-chic-ui の固定先 | `077f5b4` から `3481488` 以降へ。承認を得た専用のコミットで、更新前後のハッシュを記録する。更新したときに `MenuItem` の `disabled`・`description` の実際の名前と型、押せない項目のキーボードの動き、`aria-haspopup` の値を確かめ、違えば UserRowActions の作りをコード生成の計画で合わせる |
| `frontend/.npmrc` | `ignore-scripts=true` を足す。`vendor/make-you-chic-ui` のインストールとビルドが通ることを確かめる |
| E2E | `frontend/e2e/110-user-admin-flow.e2e.ts` を足す（`functional-spec.md` 9節）。`e2e/support/` は変えない |

## 3. `useUserAdmin`

`useUserAdmin(api, t, language)` は画面の状態と操作をまとめるフック。画面の状態はこの画面の中だけで持ち、アプリ全体の置き場・ブラウザの保存・URL は使わない（D2）。

### 3.1 持つ状態

| 状態 | 型（意味） | 初めの値 |
|---|---|---|
| `page` | 今のページ（1 から） | 1 |
| `searchText` | 今の検索の文字（前後の空白を除いた後の値。空なら検索なし） | '' |
| `searchInput` | 検索の入力欄の値（入れたまま） | '' |
| `searchError` | 検索の入力欄の下の誤りの文言の鍵、または無し | 無し |
| `list` | 最後に読めた一覧の応答（`AdminUserPage`）、または無し | 無し |
| `loadState` | `'loading'`・`'reloading'`・`'loaded'`・`'failed'` のどれか | `'loading'` |
| `failure` | 業務の失敗の知らせ（文言の鍵と差し込む氏名）、または無し | 無し |
| `confirm` | 確かめの表示の状態（3.2）、または無し | 無し |
| `edit` | 氏名・言語の入力の状態（3.3）、または無し | 無し |
| `busyUserId` | 要求を送っている行の利用者 ID、または無し | 無し |
| `slow` | 送信から 5 秒を過ぎたか（D14） | 偽 |
| `focusTarget` | 次の描画で移すフォーカスの行き先（W12）、または無し | 無し |
| `liveMessage` | `role="status"` で読み上げる文（読み込み中・件数の範囲） | '' |

- 画面の状態（4.1 の表の loading・reloading・populated・empty-search・empty-page・load-error）は、`loadState`・`list`・`searchText` から導く（別に持たない）。loading は `list` が無い間の読み込み、reloading は `list` がある間の読み込み。
- 読み直しの番号（`useRef`）を持ち、最後に始めた読み直しの答えだけを使う。画面を離れた後の答えは捨てる（D3）。
- 5 秒の時計は送信の開始で始め、応答で止める。画面を離れたら止める。時刻は `setTimeout` で、テストでは偽の時計で進める。

### 3.2 確かめの表示の状態（`ConfirmState`）

| 項目 | 意味 |
|---|---|
| `action` | `'grantAdmin'`・`'revokeAdmin'`・`'suspend'`・`'resume'`・`'resetFailures'` のどれか |
| `user` | 対象の行（`userId`・`displayName`・`email` を使う） |
| `submitting` | 送信中 |

### 3.3 氏名・言語の入力の状態（`EditState`）

| 項目 | 意味 |
|---|---|
| `user` | 対象の行 |
| `displayName` | 入れた値（初めは行の `displayName`） |
| `language` | `'ja'`・`'en'` のどちらか（初めは行の `language`） |
| `fieldErrors` | 項目（`displayName`・`language`）ごとの誤りの文言の鍵 |
| `errorSeq` | 誤りを出した回数（同じ誤りでも描いた後に最初の誤りの欄へフォーカスを移すため） |
| `status` | `'open'`・`'submitting'`・`'notFound'`・`'failed'` のどれか（invalid は `fieldErrors` があることで表す） |
| `dialogMessage` | not-found・failed の文言の鍵、または無し |

### 3.4 返す値と操作

| 名前 | 中身 |
|---|---|
| `view` | 画面の状態（4.1 の6つ）・行・全体の件数・今のページ・今の検索の文字・失敗の知らせ・読み上げの文 |
| `goToPage(page, direction)` | W2 |
| `changeSearchInput(value)`・`submitSearch()`・`clearSearch()` | W3 |
| `retry()` | load-error の「もう一度読み込む」 |
| `selectAction(user, action)` | W4 の項目を選んだとき。確かめの操作なら `confirm` を、`editProfile` なら `edit` を作る。業務の失敗の知らせを消す |
| `confirmAction()`・`closeConfirm()` | W5。送信中は閉じない |
| `changeEditName(value)`・`changeEditLanguage(value)`・`saveEdit()`・`closeEdit()` | W7。送信中は閉じない |
| `dismissFailure()` | 業務の失敗の知らせの [×] |
| `confirm`・`edit`・`busyUserId`・`slow`・`focusTarget`・`consumeFocus()` | 部品が使う状態と、フォーカスを移した後に行き先を消す口 |

- 失敗の扱いの入口は1か所（`handleFailure(error, path, context)`）に集め、順に「401 なら印を戻して終わり」→「`adminForbidden(error, path)` が真なら終わり」→「context（一覧・操作・保存）ごとの扱い」を行う（D11・D12）。403 の扱いを漏らさないため、API を呼ぶ道はすべてここを通す。
- 自分の行の保存の成功では、`applyOwnProfile` を Toast と読み直しより先に呼ぶ（W8）。

## 4. 部品ごとの props と state

| 部品 | props | 自分で持つ state |
|---|---|---|
| UserAdminPage | なし（登録から開く。API の関数の集まりは既定の実装を使い、テストでは描画の手伝いで差し替える） | なし（`useUserAdmin` が持つ） |
| UserSearchBox | `value: string`・`error`（誤りの文言、または無し）・`disabled: boolean`・`onChange(value)`・`onSearch()`・`onClear()` | なし。`role="search"` の `form` で Enter を受ける。説明の文を `aria-describedby`、誤りを `aria-invalid` |
| UserTable | `rows: AdminUser[]`・`total: number`・`page: number`・`onPageChange(page)`・`busy: boolean`・`busyUserId`（数、または無し）・`labels`（Table の文言）・`language`・`renderActions(row)` | なし。列の描き方（D18・D19）だけを持つ。`getRowId` は `userId` の文字列 |
| UserRowActions | `user: AdminUser`・`busy: boolean`・`onSelect(action)`・`t` | なし（開閉は `Dropdown` が持つ） |
| ConfirmActionDialog | `state: ConfirmState`・`slow: boolean`・`onConfirm()`・`onCancel()`・`t` | 「やめる」の ref（`initialFocusRef`） |
| EditProfileDialog | `state: EditState`・`slow: boolean`・`onChangeName(v)`・`onChangeLanguage(v)`・`onSave()`・`onCancel()`・`t` | 氏名の入力の ref（はじめのフォーカスと誤りのときのフォーカス） |

- `Modal` は `closeOnBackdropClick` を偽にする。送信中は `onClose` で閉じない（Escape・[×] を受けても何もしない）。ConfirmActionDialog は危険の操作（revokeAdmin・suspend）の実行のボタンを make-you-chic-ui の危険の見た目にする。
- UserRowActions は `Dropdown` の `trigger` に `Button`（secondary・小）を渡し、`aria-label` に「〔氏名〕（〔メールアドレス〕）の操作」（busy なら末尾に「（処理中）」）を付ける。`items` は `rowActions(user)` の結果から作り、押せない項目は `disabled: true` と `description`（理由の文）、押せる項目は `onClick`（busy の間は何もしない）にする。
- UserTable の列「氏名」は、自分の行に `Badge`「あなた」を添える。「管理者」「ロック」の無い印は「—」を `aria-hidden` にし、見えない文字「なし」を添える。

### 4.1 行の操作の項目の型（説明のための断片）

```ts
export type RowActionKind =
  | 'grantAdmin'
  | 'revokeAdmin'
  | 'suspend'
  | 'resume'
  | 'resetFailures'
  | 'editProfile'

export type DisabledReason = 'selfRevoke' | 'selfSuspend'

export interface RowAction {
  kind: RowActionKind
  disabledReason?: DisabledReason
}
```

`rowActions` は行の `admin`・`suspended`・`resettable`・`self` だけを受け、`functional-spec.md` W4 の表の順で `RowAction` の一覧を返す（D5）。

## 5. API との受け渡し（契約 C3）

| 関数 | 要求 | 成功 | 失敗 |
|---|---|---|---|
| `listUsers(page, searchText)` | `GET /api/admin/users?page=<n>`、`searchText` が空でなければ `&q=<encodeURIComponent した値>` | 200 の本文を `AdminUserPage` として読む | `ApiError` をそのまま投げる |
| `grantAdmin(userId)` | `POST /api/admin/users/{userId}/grant-admin`（本文なし） | 204（本文を読まない） | 同上 |
| `revokeAdmin(userId)` | `POST …/{userId}/revoke-admin` | 204 | 同上 |
| `suspendUser(userId)` | `POST …/{userId}/suspend` | 204 | 同上 |
| `resumeUser(userId)` | `POST …/{userId}/resume` | 204 | 同上 |
| `resetLoginFailures(userId)` | `POST …/{userId}/reset-login-failures` | 204 | 同上 |
| `updateProfile(userId, { displayName, language })` | `PUT …/{userId}/profile`、`Content-Type: application/json`、本文は2つの項目だけ | 204 | 同上 |

- 根のパスは定数 `USER_ADMIN_API_ROOT = '/api/admin/users'`。操作のパスは `userId` を `encodeURIComponent` して組み立てる関数で作り、`useAdminForbidden` に渡すパスにも同じ関数を使う。
- すべて既存の ApiClient（`apiRequest`）を通す。アクセストークン・401 での更新・`Accept-Language` は ApiClient に任せる。
- 一覧の本文は、決まった項目だけの値に変える（知らない項目は捨てる）。`userId` が整数、`email`・`displayName`・`registeredAt` が文字列、`language` が `ja`・`en`、`admin`・`suspended`・`locked`・`resettable`・`self` が真偽、`lockedUntil` は無いか文字列、`page`・`size`・`total` が整数でなければ、読めない本文として通信の失敗（`networkError()`）を投げる（招待の `invitationApi.ts` と同じ考え方）。
- 応答・要求の値を console・ブラウザの保存に出さない（D20）。

### 5.1 応答の型（`api/types.ts`）

| 型 | 項目 |
|---|---|
| `AdminUser` | `userId: number`・`email: string`・`displayName: string`・`language: UserLanguage`・`admin: boolean`・`suspended: boolean`・`locked: boolean`・`lockedUntil?: string`・`resettable: boolean`・`registeredAt: string`・`self: boolean` |
| `AdminUserPage` | `items: AdminUser[]`・`page: number`・`size: number`・`total: number` |
| `ProfileRequest` | `displayName: string`・`language: UserLanguage` |
| `UserLanguage` | `'ja'`・`'en'` の union（定数の配列 `USER_LANGUAGES` から作る） |

### 5.2 失敗から文言（`failureMessage.ts`）

| 入力 | 結果 |
|---|---|
| `kind: 'response'` で code が `USER_NOT_FOUND`・`USER_ADMIN_SELF_OPERATION`・`USER_ADMIN_TARGET_SUSPENDED`・`USER_ADMIN_NO_CHANGE`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_BUSY` | その code の文言の鍵（7.5） |
| `kind: 'response'` で code が `VALIDATION_FAILED` | 一覧は検索の誤りの鍵、保存は項目ごとの誤りへ（W3・W7） |
| そのほかの応答・知らない code・code の無い応答・`kind: 'network'` | 一般の文言の鍵（一覧は読み込みの失敗、操作は 7.5 の一般、保存は 7.6 の一般） |

- 画面が扱う code の一覧は定数の配列で持ち、型をそこから作る（招待の `INVITATION_ERROR_CODES` と同じ形）。

## 6. 機能の登録（`registration.ts`）

| 項目 | 値 |
|---|---|
| `featureId` | `'useradmin'` |
| 画面 | `path: '/admin/users'`（定数 `USER_ADMIN_PATH`）・`screen`: 遅延読み込みの UserAdminPage・`layout: 'SHELL'`・`access: 'ADMIN'` |
| サイドバー | `id: 'useradmin'`・`labelKey: 'useradmin.nav.label'`（ja「利用者の管理」、en「Users」）・`path: USER_ADMIN_PATH`・`order: 230`・`visibleWhen: 'ADMIN'` |
| 文言 | `userAdminMessages`（`messages.ts`） |

- 骨組み（`src/app/`）のファイルは書き換えない。機能の一覧への足し方は、既存の機能（招待など）の登録が骨組みに読み込まれている形に従う（足す場所はコード生成で確かめる）。
- サイドバーで隠すのは表示の切り替えにすぎず、判定はサーバー側で行う（`project.md` の Mandated、FR8.2）。
- U4 の S6 の見出しは、この登録のサイドバーの項目の名前（「利用者の管理」）を使う（U4 の Q5 A）。

## 7. 入力の確かめ

| 入力 | 画面の確かめ | 誤りの出し方 | サーバーとの関係 |
|---|---|---|---|
| 検索の文字 | `searchInput.ts`: `trimDisplayName` で前後の White_Space を除き、`countCodePoints` が 254 を超えれば `tooLong`。除いた後が空なら「検索なし」 | 入力欄の下（`FormField` の誤り）、`aria-invalid`。一覧は変えない | サーバーも同じ数え方（U3 の Q3 A）。サーバーの 400 は「検索の文字が正しくありません。」（AC1.1.5） |
| 氏名 | `validateDisplayName`（required・tooLong・invalidCharacter の順） | 入力欄のすぐ下、`aria-invalid`・`aria-describedby`。入力は消さない。保存のときに最初の誤りの欄へフォーカス | サーバーの 400 の `fieldErrors` の `displayName` も同じ場所。`reason` を寄せて同じ文言にする |
| 言語 | `RadioGroup` で `ja`・`en` のどちらかしか選べない | サーバーの 400 の `language` は言語のまとまりの下 | サーバーの判定を正とする |

- 検索の上限の定数 `USER_SEARCH_MAX_CODE_POINTS = 254` は `searchInput.ts` に置く（この機能だけが使うため `shared/validation/limits.ts` には置かない）。U3 の値と同じであることを説明文に書く。
- 画面の確かめはサーバーの判定の代わりにしない（`team.md`、FR6.2）。

## 8. テストで確かめる内容

| テスト | 確かめること |
|---|---|
| `rowActions.test.ts` | W4 の表の全組（`admin`・`suspended`・`resettable`・`self` の 16 通り）と、`functional-spec.md` W4 の 6 の性質（fast-check、乱数の種を記録） |
| `searchInput.test.ts` | 254 ちょうど・255、前後の半角・全角の空白、空白だけ、サロゲートペアの数え方。性質: 前後に空白を足しても結果が同じ、結果の値の長さは 254 以下 |
| `lockedUntil.test.ts` | 今日・明日・時間帯の違い（時間帯と今の時刻を引数で固定）、ja・en |
| `failureMessage.test.ts` | 6つの code・知らない code・code 無し・通信の失敗の鍵 |
| `profileInput.test.ts` | 理由の寄せ方と、項目と理由から文言の鍵 |
| `focusTarget.test.ts` | 読み直しの後に同じ `userId` の行があればその行、無ければ一覧の見出し |
| `api/userAdminApi.test.ts` | パスと `q` の付け方（空なら付けない、特殊文字の符号化）、本文の2つの項目、知らない項目を捨てる、形の違う本文は通信の失敗 |
| `UserAdminPage.test.tsx` | 4.1 の6つの状態、最後の読み直しの答えだけを使う、ページ送りで検索の文字を保つ、検索で1ページ目、`correctedPage` で移る、5つの操作の成功（Toast・読み直し・フォーカス）、7.5 の各 code の知らせと読み直し、403 で `useAdminForbidden` が呼ばれ何も出さない、401 で何も出さない、5 秒の「時間がかかっています」（偽の時計）、自分の行の保存で `useApplyOwnProfile` が送った値で呼ばれる・ほかの行では呼ばれない、vitest-axe（表示・読み込み中・読み込みの失敗・0 件・失敗の知らせ） |
| `UserSearchBox.test.tsx` | Enter と [検索]、上限の誤り、検索を消す、読み直しの間は押せない、vitest-axe（既定・誤り） |
| `UserTable.test.tsx` | 列と印の文字、「—」の読み上げ「なし」、自分の行の「あなた」、長いメールアドレス・2語の氏名、日時の書式、ページの状態の文言（ja・en）、vitest-axe |
| `UserRowActions.test.tsx` | 読み上げの名前、押せない項目の `aria-disabled` と理由の `aria-describedby`、押せない項目を押しても `onSelect` が呼ばれない、busy の名前、キーボードで開いて移る・Escape で戻る、vitest-axe（自分の行のメニューを開いた状態） |
| `ConfirmActionDialog.test.tsx` | 5種類の見出し・効き目・ボタン、はじめのフォーカスが「やめる」、背景のクリックで閉じない、「やめる」で `onConfirm` が呼ばれない、送信中は閉じない・「処理中」、vitest-axe（5種類） |
| `EditProfileDialog.test.tsx` | 今の値で開く、はじめのフォーカスが氏名、画面の確かめの誤りと入力が消えないこと、サーバーの 400 の項目の誤り、404 の表示と「保存」を押せない、一般の失敗で入力が残る、vitest-axe（今の値・誤り） |
| `registration.test.tsx` | 画面とサイドバーの登録の値、文言の ja・en の鍵の対 |
| プリファレンスの既存のテスト | `fieldErrors` を移した後も通る（ふるまいを変えない） |
| `e2e/110-user-admin-flow.e2e.ts` | `functional-spec.md` 9節の流れ |

- 描画の後に反映される値は `waitFor` で待つ。テストの見本の氏名は2語（例「山田 太郎」）を含め、メールアドレスは `example.com` だけ（`team.md`）。

## 9. 既存への影響

| 既存 | 影響 |
|---|---|
| `features/preferences/fieldErrors.ts`・`fieldErrors.test.ts` | `src/shared/api-client/` へ移す。プリファレンスの読み込み先（`usePreferencesForm.ts` など `readFieldErrors` を読むファイル）を直す。ふるまいは変えない（Q3 A） |
| `features/invitation/` | 変えない（ページ送りの切り替えは U2、403 の扱いは U4） |
| `src/app/` | 変えない（U4 が変える） |
| `vendor/make-you-chic-ui` | 固定先だけを上げる。上げた版で既存の画面（招待の一覧・DSL・ShellLayout のユーザーメニューなど）のテストと E2E が通ることを確かめる |
| `frontend/.npmrc` | `ignore-scripts=true` を足す |
| `frontend/e2e/` | 110 を足す。010〜100 と `support/` は変えない |
