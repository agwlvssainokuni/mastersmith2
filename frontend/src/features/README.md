# 機能の登録の置き場所

後の単位（U2・U3 など）は、このディレクトリの下に機能ごとのディレクトリを作り、
`<featureId>/registration.ts` に `FeatureRegistration`（`src/app/registry/types.ts`）を
名前付きのエクスポート `registration` として置く。画面の骨組み（U1）が起動時に自動で読み込む。
U1 のファイルは書き換えない。

```ts
import type { FeatureRegistration } from '../../app/registry/types'

export const registration: FeatureRegistration = {
  featureId: 'example',
  routes: [],
}
```

U1 の段階では登録は無い（このファイルはディレクトリを保つための説明）。

## 表示の設定の使い方（Intent 260925-user-management の U4、契約 C9）

機能の画面は、表示の設定（言語・テーマ・文字の大きさ）を `src/app/display-settings/` の口だけで扱う。
make-you-chic-ui の `useTheme` と localStorage を直接触らない。

- `useDisplaySettings()`（`DisplaySettingsProvider.tsx`）: 画面の値（`language`・`theme`・`fontSize`・`resolvedTheme`・`displayName`）と、
  見せ方の `setPreview`・`clearPreview`・`setLanguage`、保存の後の `applyUserPreferences`
- `saveBrowserDisplaySettings`（`displaySettingsStore.ts`）: 登録の完了のときに3つをブラウザに保存する
- `LANGUAGE_NAMES`（`displaySettingsTypes.ts`）: 言語の選択肢の名前（訳さない）と `lang` 属性に使う
- 文言の鍵 `display.theme.*`・`display.fontSize.*`: テーマ・文字の大きさの選択肢
- 登録の完了からログインの画面へのメールアドレスの受け渡しは `src/app/login-handoff/` の `handOffToLogin`

## 入力の確かめの関数（`src/shared/validation/`、Intent 260925-user-management の U6・U7）

氏名とパスワードの画面の側の確かめは、サーバーと同じ決まり（U2 の BR1.1〜BR1.4・BR4.1、U3 の BR7.2）の純粋な関数を
`src/shared/validation/` から名前付きで読む（`index.ts` は置かない）。関数は誤りの種類（文字列リテラルの union）だけを返し、
文言は機能ごとに持つ（例: `registration.password.tooShort`）。判定はサーバーが正で、画面の確かめはその代わりにしない。
React・`window`・ブラウザの保存・`app/`・`features/` には触れない。U6（登録の完了）と U7（パスワードの変更・プリファレンス）が使う。

| ファイル                 | 関数・定数                                                                                               | 返す誤りの種類                                                                                                                                             |
| ------------------------ | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `validateDisplayName.ts` | `validateDisplayName(value)`                                                                             | `DisplayNameProblem` = `'required'`・`'tooLong'`・`'invalidCharacter'`（前後の White_Space を除いた後に、空 → 254 コードポイント超 → Cc・Cf を含む、の順） |
| `validatePassword.ts`    | `validateNewPassword(password)`                                                                          | `NewPasswordProblem` = `'required'`・`'tooShort'`（12 コードポイント未満）・`'tooLong'`（UTF-8 で 72 バイト超）                                            |
| `validatePassword.ts`    | `validatePasswordConfirmation(password, confirmation)`                                                   | `PasswordConfirmationProblem` = `'required'`・`'mismatch'`（文字の並びの完全な一致。正規化・前後の空白の除去をしない）                                     |
| `codePoints.ts`          | `countCodePoints`・`utf8ByteLength`・`trimDisplayName`（と `isWhiteSpaceCodePoint`）                     | —（コードポイントの数、TextEncoder と同じバイト数、前後の Unicode の White_Space だけを除く。JavaScript の `trim` は使わない）                             |
| `limits.ts`              | `DISPLAY_NAME_MAX_CODE_POINTS`（254）・`PASSWORD_MIN_CODE_POINTS`（12）・`PASSWORD_MAX_UTF8_BYTES`（72） | —                                                                                                                                                          |

誤りが無いときは `undefined` を返す。サーバーの 400 の `fieldErrors` の `reason`（`REQUIRED`・`TOO_SHORT`・`TOO_LONG`・
`INVALID_CHARACTER`・`MISMATCH`）は、同じ名前の種類（`required` など）に寄せてから文言の鍵にすると、画面とサーバーで同じ文言になる。

## ユーザーメニューの項目で画面へ移る（`path`、Intent 260925-user-management の U7）

ユーザーメニューの項目（`userMenuItems`）は、選んだときに操作を行う `action`（例: ログアウト）か、登録済みの画面へ移る
`path`（例: プリファレンス）の、どちらか一方だけを持つ（`UserMenuItemRegistration` は2つの形の union）。

```ts
userMenuItems: [
  {
    id: 'preferences-open',
    labelKey: 'preferences.menu.preferences',
    path: '/me/preferences',
    order: 80,
  },
]
```

- 登録の検査（`validateRegistrations`）は、`action` と `path` のどちらも無い・両方ある項目と、`path` が登録済みの画面の URL
  （ホームを含む）と完全に一致しない項目を問題にして、起動を止める。外の URL（`https:`・`javascript:`・`//` で始まる値など）は
  必ずここで止まる。
- `ShellLayout` は `path` の項目を、make-you-chic-ui の Dropdown の `href`（`<a>` で描かれ、リンクとして読み上げられる）と、
  既定の移動を止めて移る `onClick` にする。サイドバーと同じく読み込み直しなしで移り、マウスでも Enter・Space でも同じ。
  `action` の項目は今までどおり `<button>` で描かれる。
- 項目は `order` の順に並ぶ（ログアウトは 100。プリファレンス 80・パスワードの変更 90）。

## サイドバーの項目の区画とアイコン（Intent 261004-role-menu の U1、契約 C2）

サイドバーの項目の登録（`SidebarItemRegistration`）は、区画 `section` を必ず持つ。今の区画は管理のメニューの `'ADMIN'` だけで、
`section: 'ADMIN'` の項目は `visibleWhen: 'ADMIN'` にする（登録の検査が起動を止める）。アイコン `icon` は任意で、
make-you-chic-ui の `IconName` のうち `src/app/registry/allowedIcons.ts` の `ALLOWED_ICONS` にある名前だけを使える
（無ければ骨組みが既定のアイコンを当てる）。区画と `visibleWhen` は見せ方だけを決め、管理の API はサーバー側の判定で守る。
見出しの文言・区画の並び・区画ごとの組み立ては骨組みが持つ。

```ts
sidebarItems: [
  { id: 'example', labelKey: 'example.nav.label', path: '/admin/example', order: 300, visibleWhen: 'ADMIN', section: 'ADMIN', icon: 'list' },
],
```

## ログアウト（`useLogout`、Intent 261004-role-menu の U1）

機能の画面がログアウトするときは、ログインの機能（`auth`）を直接読まず、骨組みの `useLogout()`
（`src/app/login-state/LoginStateGate.tsx`）を使う。返す関数は `Promise<boolean>` で、ログイン状態の提供元の `logout` を
呼べたら `true`、提供元またはその `logout` が無ければ何もせずに `false` を返す（`false` のときは画面を元に戻し、押し直せるようにする）。
`logout` の失敗は外へ出さない（画面の側のトークンの破棄は `auth` が必ず行う）。未ログインになったことはログイン状態の知らせで伝わる。

## 機能どうしの import の制限（Intent 261004-role-menu の U1）

機能のファイルは、ほかの機能のファイルを直接 import しない。複数の機能で共有するものは `src/shared/` へ移し、ログアウトなどの
骨組みの口は `src/app/` から使う。`src/shared/` は `src/app/` と `src/features/` を読まない。どちらも `frontend/eslint.config.js` の
`no-restricted-imports` で止める（`./gradlew verify` の ESLint で落ちる）。

- テストのファイル（`*.test.ts`・`*.test.tsx`）は対象外で、画面を組むためにほかの機能の登録や `authSession` を読んでよい。
  `testing/` の部品などテストのファイルでないものは対象で、ほかの機能の部品が要るときは呼ぶ側のテストのファイルから引数で受け取る
  （例: `preferences/testing/renderPreferences.tsx` の `auth`）。
- 機能の中の下位のディレクトリ（`api/`・`testing/` など）に、機能と同じ名前を付けない（設定の読み込みが失敗する）。
- 決まりの確かめは `src/eslintImportRules.test.ts`。

## 共有の木（`src/shared/tree/`、Intent 261004-role-menu の U1、契約 C2）

木の形の一覧（権限の設定の対象・メニューの定義など）は `SharedTreeView` を使う。開閉（`expandedIds`・`onToggle`）と
選び（`selectedId`・`onSelect`）は呼ぶ側が持ち、子は節を開いたときに `loadChildren` で読む（閉じて開き直しても読み直さない。
`nodes` を新しい配列に替えると読み直す）。文言は `labels`（開く・閉じる・読み込み中・失敗・再試行・子が無い）で表示言語に
合わせて渡す。表示名と印は文字として描かれ、HTML として描かれない。入れ子の `ul` と `button` で表し、tree の役割と矢印のキーは
使わない。深さの上限は木に置かず、呼ぶ側のデータで決まる。
