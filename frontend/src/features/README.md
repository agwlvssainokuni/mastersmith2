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
