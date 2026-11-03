# アプリのアイコンと favicon

アプリのアイコン・favicon の素材は、このリポジトリで自作したものだけを使います（外から取り込まない）。素材の置き場・作り方・守りの検査をこの文書にまとめます。

## 素材の一覧

`frontend/public/` の下に置いた次の 3 ファイルだけです。`public/` の中身はすべて `dist` のルートへ複写されて公開されるため、素材以外のファイル（README・下書き）は置きません（`src/appIconAssets.test.ts` が一覧にちょうど一致することを確かめます）。

| ファイル | 役割 |
|---|---|
| `favicon.svg` | SVG の favicon。固定の 1 色 `#2563eb`（利用者ごとのブランドカラー・テーマは favicon からは分からないため固定） |
| `favicon.ico` | SVG を読めないブラウザと、ブラウザが既定で読む `/favicon.ico` 用。16・32・48 px の PNG を詰めた ICO |
| `app-icon.svg` | `fill="currentColor"` の元の図。ログイン画面の部品（`frontend/src/shared/app-icon/AppIcon.tsx`）の図柄の元（`d` の一致を `AppIcon.test.tsx` が確かめます）。上のバーのロゴも同じ図を使います |

図柄は、鍛冶の金床とハンマーを直線だけで描いた塗りつぶしのシルエット（`viewBox="0 0 64 64"`、`path` は 2 つ、背景は透明）です。細い線は 16 px で潰れるため使いません。`apple-touch-icon.png` と 192/512 px の PNG は置きません（iOS のホーム画面への追加と Web アプリのマニフェストは要件にないため。要るときは `public/` の名前の一覧（`src/appIconAssets.test.ts`）とこの文書に足します）。

`index.html` の `<head>` に、次の 2 本の `<link>` があります（`.ico` が先、SVG が後）。ブラウザごとの優先の見え方は、配備の後のスモークテストで確かめます。

```html
<link rel="icon" href="/favicon.ico" sizes="32x32" />
<link rel="icon" href="/favicon.svg" type="image/svg+xml" />
```

## `favicon.ico` の作り方

ImageMagick は使いません（この PC にありません）。`rsvg-convert` で `favicon.svg` から PNG を作り、小さな Node のスクリプト（`frontend/scripts/make-ico.mjs`。新しい依存は足しません）で ICO の入れ物に詰めます。スクリプトはビルドには組み込まない、手で流す道具です。バイナリを手で編集しません。

| 項目 | 値 |
|---|---|
| 道具 | `rsvg-convert` 2.63.4（librsvg。cairo 1.18.6）、Node 26.13.0 |
| PNG を作る | `rsvg-convert -w 16 -h 16 frontend/public/favicon.svg -o <一時の場所>/ico16.png`（32・48 も同じ。背景は透明のまま） |
| ICO に詰める | `node frontend/scripts/make-ico.mjs frontend/public/favicon.ico <一時の場所>/ico16.png <一時の場所>/ico32.png <一時の場所>/ico48.png` |
| 出力 | `frontend/public/favicon.ico` 2085 バイト（16×16・32×32・48×48 の PNG を 3 枚。上限 32 KiB） |

PNG の一時の置き場はリポジトリの外にします（コミットしません）。`make-ico.mjs` は出力の前に形（先頭 4 バイト・画像の数・各エントリが収まること・各画像が PNG の署名を持つこと・大きさの上限）を確かめ、通らなければ書かずに終了コード 1 で終わります。

別の PC で作るときは、`rsvg-convert` の代わりに、SVG を指定の大きさの透明な PNG に描く道具（Inkscape の `--export-type=png`、ImageMagick の `magick -background none favicon.svg -resize 16x16` など）で同じ 3 つの PNG を作り、同じ `make-ico.mjs` で詰めます。

## SVG を変えたらバイナリを作り直す

`favicon.svg` の図柄を変えたときは、上の手順で `favicon.ico` を作り直します（`app-icon.svg` と `favicon.svg` の `d` は同じ値に保ちます）。バイナリの図柄が元の SVG と一致することは機械では確かめません。コード生成のレビューで目で確かめます。

## 守りの検査（`./gradlew verify` と CI）

| 検査 | 内容 | 段 |
|---|---|---|
| ライセンスヘッダー | `frontend/` の下のすべての `.svg` の先頭に、HTML と同じ `<!-- ... -->` の形の Apache License 2.0 ヘッダーがある（XML 宣言を先頭に置かない）。`.ico`・`.png` はヘッダーを置けないため対象の外 | 3 |
| SVG の中身（`npm run svg:check`） | 要素・属性・値の許可リスト。`script`・イベントの属性・`href`・`style`・外部の参照・コメント・処理命令などを含まない。4096 文字以内 | 3 |
| `dist` の素材（`npm run dist:check`） | `public/` のすべてのファイルが `dist` のルートに同じバイト列であり、`index.html` の favicon の `<link>` が `dist` の中のファイルを指す | 4 |
| WAR の素材（`verifyFaviconInWar`） | `mastersmith.war` の `WEB-INF/classes/static/` に `public/` のすべてのファイルが同じバイト列である | 9 |

素材が欠けると、`/favicon.ico` は 404 ではなく `index.html`（HTML）を 200 で返します（`StaticIconAssetsIT` がこの動きを記録しています）。サーバーは気づかないため、上の `dist` と WAR の検査が必要です。
