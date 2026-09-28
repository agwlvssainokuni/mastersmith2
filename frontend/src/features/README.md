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
