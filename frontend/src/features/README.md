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
