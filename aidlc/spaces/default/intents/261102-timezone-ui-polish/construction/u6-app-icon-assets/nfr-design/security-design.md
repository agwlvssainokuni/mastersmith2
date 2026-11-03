# セキュリティの設計 — u6-app-icon-assets

出典: NFR Requirements（`construction/u6-app-icon-assets/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`・`reviews/review-01.md`）、要件（`inception/requirements-analysis/requirements.md` の FR9・NFR1.1・NFR3.1・NFR3.2・NFR5.3）、単位の定義（`inception/units-generation/unit-of-work.md` の U6）、設計の決定（`inception/domain-design/decisions.md` の ADR-008）、既存のコード（読み取りだけ: `frontend/scripts/check-license-header.mjs`・`frontend/index.html`・`frontend/vite.config.ts`・`backend/.../config/WebConfig.java`・`SecurityConfig.java`・`common/web/CacheControlFilter.java`・`backend/src/test/resources/static/`・`build.gradle.kts` の `verifyLicense`・`verifyBuild`・`verifyArtifact`・`verifyDslSchemaInWar`）、`team.md`・`project.md`（自作の SVG の決まり・Forbidden）。

U6 は素材の単位（種類 packaging）。新しい API・表・列・依存・サーバーの道は足さない。段の定義の `produces_kinds` に従い、この単位で作る文書は **この `security-design.md` と `traceability.json` の2つ**にする（`performance-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md` は、新しい経路・状態・負荷が無いため作らない。`logical-components.md` は種類 service・ui・library の文書で、packaging には当たらないため作らず、部品の見方は末尾の「論理の部品の見方」に置く）。素材の大きさの上限（NFR2.1.1）は SD7 の確かめに入れる。

設計の決定の番号は `SD<n>`（この文書の中で定義する番号）。実装のファイル名・関数名は Code Generation で決める（ここの名前は論理の名前。`check-svg-safety.mjs` など NFR Requirements で決めた名前は、そのまま使う）。

## 番号の付け方

NFR1.1.x・NFR3.x・NFR5.3.1・NFR2.1.1 は NFR Requirements の `security-requirements.md` の番号。NFR Requirements の `traceability.json` は上流の NFR2.1（日時の変換の問い合わせの数）に素材の大きさの上限 NFR2.1.1 を結びつけていたが、同じ番号を別の意味に使っている（レビューの R-03）。承認済みの文書は書き換えず、この段では「素材の大きさの上限 = U6 の追加の要件 NFR2.1.1」と読み、上流の NFR2.1 は N/A とする（「上流との差」の 3）。

## SD1. SVG の中身の検査（NFR1.1.1・NFR1.1.5 の細目。レビューの R-01 を解く）

### SD1.1 方針

- **許可リスト、疑わしければ拒否（fail-closed）**。許す要素・属性・値の文法に載るものだけを通し、読み取りの途中で想定外の形に出会ったら、その場で拒否して走査を止める。禁止リスト（`script` を探す）は使わない。
- **字句の走査は1回の前から後ろへの走査（状態機械）**で行い、全体に正規表現をかけない（入れ子の量指定子を持たない。下の「走査の時間」）。XML のパーサーは足さない（新しい依存を作らない。NFR Requirements の技術の選択）。
- **名前は大文字小文字を区別して**許可リストと照らす（SVG・XML は区別する。折りたたまない）。`<SCRIPT>`・`ONLOAD` は「許可リストに無い名前」として落ちる。
- 検査の関数は**最初の違反で止まり**、違反の種別（コード）と位置（先頭からの文字数）を返す。文書の中身は返さない（長い文字を出力へ流さない）。

### SD1.2 検査の前に確かめること（走査の入口）

| ID | 確かめ | 拒否の種別 |
|---|---|---|
| SD1.2.1 | 文字列であること。長さが 4096 文字以内（NFR2.1.1 の「1ファイル 4 KiB 以内」と同じ上限を、走査の前に使う。ASCII だけに絞るため文字数 = バイト数） | `too-large` |
| SD1.2.2 | **ASCII の印字可能な文字と、タブ・改行（LF・CR）だけ**。BOM・NUL・その他の制御文字・全角の文字・双方向の制御文字（U+202E など）は拒否する。図柄は `path` の数字と英字で足り、日本語の `title` は付けない（飾りで名前を持たない。NFR3.2.1） | `non-ascii-or-control` |

### SD1.3 走査の手順（`<` を起点にした状態機械）

| 手順 | 決まり | 拒否の種別 |
|---|---|---|
| 先頭（単独のファイルの形） | 先頭の文字がちょうど `<!--` で始まり、最初の `-->` で終わるコメント1つ（ライセンスヘッダー）だけを許す。コメントの本文に `--`・`<` を含めない。XML 宣言（`<?xml ...?>`）が先頭にあれば、ここで落ちる。ヘッダーの中身（ひな形との一致）はこの検査では見ない（SD2 の検査が見る） | `header-missing`・`comment-body` |
| コメント（先頭以外） | 許さない。先頭のヘッダーより後の `<!--` はすべて拒否する（コメントの中に隠す形を作らせない。コメントの中身を「無視する」根拠は置かない） | `comment-or-decl` |
| 処理命令 | `<?` で始まるものはすべて拒否（`<?xml-stylesheet href=...?>` は外部の読み込みになりうる） | `processing-instruction` |
| CDATA | `<![CDATA[` を拒否 | `cdata` |
| `DOCTYPE`・`ENTITY` ほか `<!` | `<!DOCTYPE`・`<!ENTITY`（大文字小文字を問わず）と、そのほかの `<!` をすべて拒否 | `doctype`・`entity`・`comment-or-decl` |
| 終了タグ `</name>` | 名前の直後に空白があってもよいが、`>` が続かなければ拒否。開いている要素の最後と名前が一致しなければ拒否 | `bad-close`・`nesting` |
| 開始タグ `<name ...` | 名前は、空白・`/`・`>` の手前までをそのまま取り出し、**要素の許可リスト**（SD1.4）に完全一致するものだけを通す。`svg:script` のような接頭辞付きの名前は、`:` を含むため許可リストに載らず落ちる。`<script\n>`・`<script\t>` のように名前の直後が空白でも、名前だけで判定するので通らない | `unknown-element` |
| 属性 | 1つずつ「空白 → 名前 → `=` → 引用符で囲んだ値」を読む。名前は**属性の許可リスト**（SD1.5）に完全一致するものだけ。同じ名前の2回目は拒否。値は**開いた引用符と同じ引用符の次の1文字まで**を取り出す（値の中の `>`・`/`・もう一方の引用符で切れない）。引用符が無い値・閉じない値・属性と属性の間に空白が無いものは拒否 | `unknown-attribute`・`duplicate-attribute`・`unquoted`・`unterminated-value`・`attr-spacing`・`no-equals` |
| 値 | 取り出した値は、その属性の**値の文法**（SD1.5）に全体が一致するときだけ通す。文法はどれも `&`・`<`・`>`・`:`（`xmlns` を除く）・`/`（`xmlns` を除く）・引用符を許さないので、文字参照（`&#x6A;avascript:`）・`javascript:`・`data:`・`http:` は文法の外として落ちる | `bad-value:<属性名>` |
| 終わり方 | `/>` か `>` だけ。`/` の次が `>` でなければ拒否 | `bad-slash`・`unterminated-tag` |
| 本文の文字 | 要素の間は空白だけ。`title`・`desc` の中だけ `[A-Za-z0-9 .,-]` の文字を許す（`&`・`<`・`>` を含めない）。要素の外の `>` は拒否 | `text`・`stray-gt` |
| 全体の形 | 根は `svg` が1つだけ。根の外に要素を足せない。深さは 8 まで。閉じていない要素が残れば拒否 | `root-not-svg`・`after-root`・`too-deep`・`unclosed`・`no-root` |

### SD1.4 要素の許可リスト

`svg`・`g`・`path`・`circle`・`ellipse`・`rect`・`line`・`polyline`・`polygon`・`title`・`desc`・`defs`・`clipPath`（NFR Requirements の技術の選択の一覧と同じ）。`script`・`style`・`foreignObject`・`iframe`・`object`・`embed`・`image`・`use`・`a`・`animate`・`set` など、リストに無いものはすべて拒否になる。

### SD1.5 属性の許可リストと値の文法

| 属性 | 値の文法（全体一致） |
|---|---|
| `xmlns`（根の `svg` だけ） | ちょうど `http://www.w3.org/2000/svg` |
| `viewBox`・`width`・`height`・`cx`・`cy`・`r`・`rx`・`ry`・`x`・`y`・`x1`・`y1`・`x2`・`y2`・`stroke-width`・`opacity`・`d`・`points` | `[0-9A-Za-z\s,.+-]*`（数・単位・パスの命令の英字・区切りだけ） |
| `transform` | `[A-Za-z0-9\s,.+()-]*` |
| `fill`・`stroke` | `none`・`currentColor`・`#` + 16進 3〜8 桁・`url(#<id>)` のどれか |
| `clip-path` | `url(#<id>)` |
| `id` | `[A-Za-z][A-Za-z0-9_-]*` |
| `stroke-linecap`・`stroke-linejoin`・`fill-rule`・`clip-rule` | それぞれ決まった語の一覧（`butt・round・square`、`miter・round・bevel`、`nonzero・evenodd`） |
| `role` | `img`・`presentation`・`none` |
| `aria-hidden`・`focusable` | `true`・`false` |

- **`href`・`xlink:href`・`xmlns:xlink`・`style`・`class` は属性の許可リストに載せない**。許す要素のどれも `href` を必要としないため、NFR1.1.1 が許していた「`#` で始まる `href`」も許さない（より厳しくする差。「上流との差」の 1）。`defs`・`clipPath` の参照は `clip-path="url(#id)"` と `fill`・`stroke` の `url(#id)` だけで足りる。
- `on` で始まる属性（`onload` など）は、許可リストに無いので落ちる（大文字の `ONLOAD` も）。個別の「`on` で始まる」判定を足す必要は無いが、テストの種には入れる（SD7.1）。
- 許可リストを広げるときは、検査・単体テスト・この表を一緒に変え、依頼者に諮る（NFR Requirements の前提どおり）。

### SD1.6 変形への耐性（NFR Requirements のレビューの R-01 への答え）

次の変形は、すべて「落ちる」ことを悪い例としてテストに入れる（SD7.1）。表の「落ちる理由」は、走査のどの決まりで落ちるか。

| 変形 | 例 | 落ちる理由 |
|---|---|---|
| 大文字小文字 | `<SCRIPT>`・`<Script>`・`ONLOAD="..."` | 名前は完全一致。許可リストに無い |
| 名前空間の接頭辞 | `<svg:script>`・`<x:path>`・`xlink:href="..."`・`xmlns:xlink` | 接頭辞付きの名前は許可リストに無い |
| 開始タグの名前の直後の空白・改行・タブ | `<script\n>`・`<script\t>` | 名前だけで判定 |
| コメント | `<!-- <script>x</script> -->`（先頭以外） | 先頭のヘッダー以外のコメントは拒否 |
| CDATA | `<![CDATA[<script>x</script>]]>` | `cdata` |
| 処理命令 | `<?xml-stylesheet href="http://e/a.css"?>`・先頭の `<?xml version="1.0"?>` | `processing-instruction`・`header-missing` |
| `DOCTYPE`・`ENTITY` | `<!DOCTYPE svg [<!ENTITY x SYSTEM "http://e">]>` | `doctype`・`entity` |
| 属性値の中の `>` | `<path id="a>b"/>`・`<path id="x>"/><script>1</script>` | 値は引用符で読み切るが、`id` の文法に `>` が無く落ちる。切り出しがずれる余地が無い |
| 文字参照 | `fill="&#x6A;avascript:x"`・`&amp;` | 値の文法に `&` が無い |
| `data:`・`javascript:`・外部の URL | `fill="data:image/svg+xml,..."`・`fill="url(http://e/a)"` | 値の文法の外 |
| `style` 属性・`style` 要素・`@import` | `style="fill:url(http://e)"`・`<style>@import ...</style>` | 許可リストに無い |
| 引用符の欠け・属性の続け書き・二重の属性 | `d=M0`・`d="M0"id="a"`・`d="M0" d="M1"` | `unquoted`・`attr-spacing`・`duplicate-attribute` |
| 制御文字・BOM・双方向の制御文字 | `\u0000`・`﻿`・`‮` | SD1.2.2 |
| 根を足す・要素を根の外へ置く | `</svg><svg .../>` | `after-root` |
| 大きすぎる入力 | 4096 文字を超える | `too-large` |

### SD1.7 走査の時間（ReDoS・停止の防止）

走査は `indexOf` と1文字ずつの前進だけで、入れ子の量指定子を持つ正規表現を使わない。値の文法の正規表現は、`[...]*` の1つの文字集合・先頭と末尾の固定だけ（入れ子なし）。入力は 4096 文字で打ち切るので、最悪でも定数の時間。捨ての試し（リポジトリの外）では、`'<'` の 4000 回・`'<svg '` の 800 回・`'a="'` の 1300 回の入力3つの検査が合計 1 ミリ秒未満だった。

### SD1.8 2つの形（単独のファイルと、画面の部品の描画結果）

関数は `checkSvgContent(content, { standalone })` の形にする（名前は NFR Requirements のとおり）。

- `standalone: true`（既定）: `.svg` のファイル。SD1.3 の先頭のヘッダーのコメントと、根の `xmlns` を必須にする。
- `standalone: false`: React の部品を描いた結果（`outerHTML`）の確かめ（SD5.2）。ヘッダーのコメントと `xmlns` を要求せず、そのほかの決まりは同じ。ここでも `class` は許可リストに無いので、部品の `svg` には `className` を付けない（大きさは `width`・`height`、色は `currentColor` で周りから継ぐ）。

### SD1.9 検査の対象の範囲（レビューの R-04 を解く）

中身の検査（`check-svg-safety.mjs`）の対象は、ヘッダーの検査（`check-license-header.mjs`）と**同じ範囲**にそろえる: `frontend/` の下のすべての `.svg`（`.` で始まるディレクトリと `node_modules`・`dist`・`coverage`・`playwright-report`・`test-results` を除く）。つまり `public/`・`src/` に加えて `e2e/` なども対象に入る。`vendor/` は `frontend/` の外のため対象外（このリポジトリが直接変更しない）。

- 範囲がずれないよう、ファイルを集める関数（`collectFiles`）と除外の一覧（`SKIP_DIRS`）は `check-license-header.mjs` から export して、`check-svg-safety.mjs` が import する（2か所に写さない）。
- 悪い例の `.svg` をファイルとして `frontend/` の下に置かない。悪い例はテストの中の文字列にする（置くと検査が実際に失敗する）。
- 範囲の同じことを、`e2e/` の下の仮の `.svg` で両方の検査が見つけることを使うテストで確かめる（SD7.2）。

## SD2. ライセンスヘッダーの検査（NFR1.1.2）

- 既存の `check-license-header.mjs` の `HTML_EXTENSIONS` に `.svg` を足す。`checkContent` は `<!--` 形のひな形（`config/license-header.txt` から作る）で先頭一致を見るため、XML 宣言が先頭にある・`/* */` の形・ヘッダー無しは、いずれも既存の判定のまま失敗する。
- ヘッダーの形の検査（SD2）と、中身の検査（SD1）は別のスクリプトで、どちらも通る必要がある。SD1.3 の「先頭にコメント1つ」は、SD2 が見るヘッダーのコメントと同じものを指す。
- `.svg` の改行は `.gitattributes` の `* text=auto eol=lf` で LF にそろう。SD2 の判定は CRLF を LF にしてから比べる既存の処理のまま。

## SD3. バイナリと素材の置き場（NFR1.1.3・NFR2.1.1）

- **置き場**: `frontend/public/` に置く。名前の候補は `favicon.svg`（固定のブランドの色の favicon）・`favicon.ico`・`apple-touch-icon.png`（180 px）・`app-icon.svg`（`currentColor` の元の図。ログイン画面の図柄の元）。最終の名前と点数はコード生成の計画で決める。`public/` の中身はすべて `dist` のルートへ複写され公開されるため、**素材以外のファイル（README・作業用の下書き）を `public/` に置かない**。素材の手順の記録は `public/` の外（リポジトリの README か `docs/` の1ファイル）に書く。
- **`public/` の中身の検査**: `public/` の下は、決めた名前の一覧（拡張子は `svg`・`ico`・`png` だけ）に一致する。一覧に無いファイルがあれば失敗する（SD7.3）。
- **作り方（この PC の道具を確かめた）**: SVG から PNG を作る `rsvg-convert`（2.63.4）はこの PC に入っている。ImageMagick（`magick`・`convert`）は**入っていない**ため、NFR Requirements の「ICO は ImageMagick で」の前提は成り立たない。そこで **ICO は、`rsvg-convert` で作った 16・32・48 px の PNG を ICO の入れ物（ヘッダー + ディレクトリ + PNG のそのままの並び）に詰める小さな Node のスクリプトで作る**（新しい依存を足さない）。このスクリプトは手で流す道具でビルドには組み込まない。スクリプトにも Apache License 2.0 のヘッダーを置き、ICO を詰める部分は純粋な関数にして単体テストで確かめる。道具の名前・版・コマンド・出力の大きさは SD3 の記録の場所に書く。別の PC で作るときの代わりの道具は、手順の記録に書く。
- **バイナリの確かめ**: PNG は先頭 8 バイトの署名と IHDR の幅・高さ（180×180 など決めた大きさ）、ICO は先頭 4 バイト（予約 0・種類 1）と画像の数（1〜8）、各エントリの位置と大きさがファイルの中に収まること、各画像が PNG の署名を持つこと。大きさの上限は NFR2.1.1（`.ico` 32 KiB・`.png` 16 KiB・SVG 4 KiB）。バイナリを手で編集しない（NFR1.1.3）。`.gitattributes` の `*.png binary`・`*.ico binary` は既にある。
- **元の図との一致**: バイナリの図柄が元の SVG と一致することは機械では確かめない（NFR Requirements のとおり）。手順の記録（SVG の変更を伴うときは、バイナリを作り直す）で追い、コード生成のレビューで確かめる。

## SD4. `/favicon.ico` を、本物の `dist`・WAR を通る形で確かめる（NFR1.1.4。レビューの R-02 を解く）

### SD4.1 読み取りで分かったこと（未検証の前提のうち、コードで確かめられた部分）

| 項目 | 分かったこと（出どころ） | 残る未検証 |
|---|---|---|
| ログインなしで読めるか | `SecurityConfig` の最後が `anyRequest().permitAll()`。`/api/**` 以外の静的なファイルはログインを求めない | 実際の要求（SD4.2 の L1・L4） |
| 画像として返るか | `WebConfig` の `/**` の資源の取り扱いが `classpath:/static/` の拡張子から種類を決める。`application.yaml` の `spring.web.resources.add-mappings: false` で標準の配信は切ってあり、この `WebConfig` の取り扱いが配る | `.ico` の `Content-Type` の実際の値（`image/x-icon` か `image/vnd.microsoft.icon`。どちらも `image/` で始まり NFR1.1.4 を満たす）、`.svg` が `image/svg+xml` になること（SD4.2 の L1・L4） |
| 種類の推測 | `SecurityConfig` が `X-Content-Type-Options: nosniff` を全応答に付ける（`contentTypeOptions`）。`SecurityHeadersIT` が代表の応答で確かめている | 素材の応答への付与（L1・L4） |
| CSP との関係 | CSP は `default-src 'self'; script-src 'self'; ... img-src 'self' data:`。同じ生成元の favicon・アイコンの読み込みは通る。SVG を直接開いても、インラインのスクリプトは `script-src 'self'` で止まる（SD1 の検査が主で、これは二重の守り。これに頼らない） | なし |
| 見つからないとき | `WebConfig` の解決の仕組みは、見つからない `/api/`・`/actuator/` 以外の道に **`index.html` を 200 で返す**。つまり `favicon.ico` が `dist` に無いと、`/favicon.ico` は HTML（`text/html`）を 200 で返し、サーバーは気づかない（K-102 のとおり） | 素材が `dist`・WAR に入ることを、サーバーの外の検査で確かめる必要がある（L2・L3） |
| キャッシュ | `CacheControlFilter` は `/assets/` 以外の応答（`favicon.*` を含む）に `no-cache` を付ける。ブラウザは使う前に再確認する。NFR Requirements が置いた「ブラウザの favicon のキャッシュは強い」という見立てのうち、サーバーの指定が原因になる部分は無い（ブラウザ側の favicon の保持は残る） | 配備の後の見え方（スモークテスト） |
| 結合テストが見る静的なファイル | バックエンドのテストのクラスパスの `static/` は、`backend/src/test/resources/static/` の**テスト用の見本**（`index.html` と `assets/`）で、`frontend/dist` ではない | 本物の素材は、結合テストでは見られない（NFR Requirements の NFR1.1.4 の結合テストだけでは、K-102 の確かめにならない） |

### SD4.2 確かめの4つの層

| 層 | 確かめ | 何が見えるか | 置き場・実行 |
|---|---|---|---|
| L1 | **仕組み**の結合テスト: テスト用の見本の `static/` に、小さな `favicon.ico` と `favicon.svg` の見本を足し、未認証で要求する。状態 200・`/favicon.ico` の `Content-Type` が `image/` で始まる・SVG は `image/svg+xml`・本文が `<!doctype html` で始まらない・`nosniff`・`Cache-Control: no-cache`。あわせて、**素材が無い道は `index.html` に落ちる**ことを1件で記録する（L2・L3 が要る理由を、動くテストで残す） | ログインなしで配られる・種類が正しい・推測させない、という配信の仕組み（本物の素材ではない） | `backend/src/test/java` の新しい結合テスト（`XxxIT`。既存の `SecurityHeadersIT` の近く。名前はコード生成で決める）。`./gradlew verify` の結合テスト |
| L2 | **`dist` に入ったこと**の検査: ビルドの後に、`dist` のルートに決めた素材のすべてがあり、`favicon.svg` が SD1 の検査（`standalone`）を通り、`favicon.ico`・`.png` が SD3 の形・大きさを満たし、`dist/index.html` の favicon の `<link>` の `href` がすべて `dist` の中のファイルを指し、外部の URL（`http:`・`https:`・`//`）を指さないこと。新しい npm スクリプトとして足し、Gradle の `frontendBuild` の後に動かす | Vite が `public/` を `dist` に複写したこと（`publicDir` の設定の変更・名前の誤りを拾う） | `frontend/scripts/` の検査 + Gradle のタスク（`verifyBuild` の段に追加。SD6）。`./gradlew verify` |
| L3 | **WAR に入ったこと**の検査: 組み上がった `mastersmith.war` の `WEB-INF/classes/static/` に、決めた素材のすべてがあり、`frontend/public/` の同名のファイルとバイト列が同じこと。既存の `verifyDslSchemaInWar`（正本と WAR の中の一致）と同じ形で足す | WAR の組み立て（`bootWar` の `from(frontend/dist)`）で落ちていないこと | `build.gradle.kts` の Gradle のタスク（`verifyArtifact` の段に追加。SD6）。`./gradlew verify` |
| L4 | **動いている WAR** への要求: 既存の E2E は組み上がった WAR を本物の設定で起動する。ログイン画面を見る既存の検査（`frontend/e2e/050-display-accessibility.e2e.ts` の「login screen」と「first screen ... CSP」の検査、または `020-auth.e2e.ts`。どれに足すかはコード生成の計画で決める）に、Playwright の要求の口で `/favicon.ico`・`/favicon.svg` を未認証で読み、状態 200・`image/` で始まる種類（SVG は `image/svg+xml`）・本文が HTML でない・`nosniff` を確かめる書き足しをする。新しい流れは足さず、E2E の本数に数えない（NFR5.3.1） | 本物の素材 + 本物のサーバーの設定 + 本物のセキュリティの決まり。K-102 の最終の確かめ | `frontend/e2e/`。`./gradlew e2eTest`（統合の前に流す。`frontend/index.html`・`frontend/public/` は「frontend の本体」に当たるため。`team.md`） |

層の役割は重ならないように分ける: L1 は配信の仕組み、L2・L3 は「素材が欠けると HTML が黙って返る」ことの検出、L4 は組み合わせの最終の確かめ。L1 の結合テストのクラスパスに `frontend/dist` を入れる案は採らない（`integrationTest` が画面のビルドに依存して遅くなり、テスト用の見本の `index.html` と本物の `dist` が衝突するため）。

### SD4.3 配備の後のスモークテストの項目（持ち主: Deployment Pipeline で手順に書き、Deployment Execution で実行）

次をスモークテストの手順に入れる（レビューの R-02）。

- 手順: 配備した画面の `/favicon.ico` と `/favicon.svg` を開き、画像として表示されること（HTML の画面が出ないこと）、ブラウザのタブに favicon が出ること。ブラウザは favicon を強く保持するため、図柄を変えたときは、保持のない（初めて開く）窓でも見る。
- 図柄が更新されて見えないときの手当て（ファイル名の変更など）は、見つかったときに決める。

### SD4.4 成り立たなかったときの戻り先

| 場合 | 見つかる層 | 戻り先 | 持ち主 |
|---|---|---|---|
| 未認証で 401・403 になる | L1・L4 | `SecurityConfig` の許可の設計に戻す（サーバーの変更が増える）。ADR-008 の「サーバーの変更なし」が崩れるため、依頼者に諮る | コード生成の計画で依頼者に諮る（L1 を最初に流す） |
| `Content-Type` が `image/` にならない | L1・L4 | 資源の取り扱いの種類の対応（拡張子 → 種類）を足す設定を設計に戻して決める | 同上 |
| 素材が欠けても検出されずに `index.html` が返る | L2・L3 が失敗しない場合 | 検査の穴として L2・L3 を直す。それでも「拡張子のある道の見つからない要求は 404」にしたい場合は、`WebConfig` の `SpaFallbackResourceResolver` に条件を足す設計に戻す（サーバーの変更） | Build and Test の失敗として扱い、依頼者に諮る |
| 配備の後だけ見えない（ブラウザの保持） | スモークテスト | SD4.3 の手当て | Deployment Execution |

## SD5. ログイン画面のアイコン（NFR1.1.6・NFR3.1.1・NFR3.2.1）

### SD5.1 部品の決まり

- React の部品として、JSX の `<svg>` と固定の `path` を書く。置き場は、ログイン画面（`frontend/src/features/auth/`）と上のバーのロゴ（U5 が使う口）の両方が使えるよう、`frontend/src/shared/` の部品にする（機能どうしは import し合わない決まり）。名前はコード生成で決める。
- `<svg>` の属性は許可リストの内側だけ: `viewBox`・`width`・`height`・`fill="currentColor"`（または `stroke="currentColor"`）・`aria-hidden="true"`・`focusable="false"`。`className`・`style`・`title`・`aria-label` は付けない。`dangerouslySetInnerHTML` を使わない（`react/no-danger` が止める）。SVG の文字列を読み込んで差し込まない。図柄はソースに書いた固定の値だけで、props から `d` を受けない（props は大きさだけ。利用者の入力・サーバーの応答は図柄に入らない）。
- 色は周りの文字の色（`currentColor`）から継ぐ。アプリ名の文字はブランドカラーとテーマのすべての組で本文の色の対比（4.5:1）を満たしており、アイコンはその文字の色と同じなので、飾りとして読まれなくても対比の面で別の失敗を作らない。アイコンは飾りのため、非テキストのコントラスト（WCAG 2.1 の 1.4.11）の対象ではない。ここで足す余計な独自の色は作らない。
- ログイン画面は新しい HTTP 要求を足さない（アイコンはバンドルの部品として描く。NFR2.1.1）。

### SD5.2 確かめ

- 描いた `<svg>` の `outerHTML` を `checkSvgContent(…, { standalone: false })` に通して、許可リストの内側であることを確かめる（部品が許可リストの外の属性・要素を足すと落ちる）。
- 部品の `path` の `d` を、`public/app-icon.svg` の `d`（SD1 の検査を通ったファイルから、属性の値を取り出す）と比べ、一致することを確かめる（NFR1.1.5 の（d））。
- 読み上げの確かめ: アクセシビリティツリーにアイコンが出ない（`getByRole('img')` で見つからない）こと、アプリ名が読めること。vitest-axe を1件。

## SD6. 検査の組み込み（`./gradlew verify` と CI）

`./gradlew verify` の段の並びは既存のままで、次を足す（CI は同じ `verify` を呼ぶので同じ検査が動く）。

| 追加 | 段 | 内容 |
|---|---|---|
| `frontendSvgCheck`（npm スクリプト `svg:check` → `check-svg-safety.mjs`） | `verifyLicense`（3 ライセンスヘッダー）に並べる | `frontend/` の下のすべての `.svg`（SD1.9）を SD1 の検査にかける。`license:check` と同じ `npmScript` の形 |
| `frontendDistCheck`（npm スクリプト `dist:check` → `dist` の素材の検査。SD4.2 の L2） | `verifyBuild`（4 ビルド）の `frontendBuild` の後 | `dependsOn(frontendBuild)` |
| `verifyFaviconInWar`（SD4.2 の L3） | `verifyArtifact`（9 成果物）の `verifyDslSchemaInWar` の隣 | `dependsOn(bootWar)`。WAR の入口と `frontend/public/` の同名ファイルのバイト列を比べる |

- 追加の依存・Gradle のプラグインは無い。`dist:check`・`svg:check` は Node の標準の機能だけ。
- Vite の `build.manifest` は `.vite/manifest.json` を出すので、`dist:check` は「`dist` に素材の一覧にある名前がある」ことと「`<link>` の `href` が `dist` の中を指す」ことを見るだけで、`dist` の他のファイルの一覧（ハッシュ付きの `assets/`）には触れない。
- `frontendBuild` の入力に `frontend/public/` を足す（今の `inputs` は `src`・`index.html` などで `public/` を含まないため、素材だけを変えると `UP-TO-DATE` で `dist` が作り直されない。Intent 260925-user-management で `vendor` の成果物を入力に足した抜けと同じ形）。コード生成の計画で `build.gradle.kts` の `frontendBuild` の `inputs` に `public/` を足す。

## SD7. 確かめの設計

| ID | 確かめ | 対応する要件 | 方法 |
|---|---|---|---|
| SD7.1 | 検査の関数の境界: 良い例（許可リストの要素・属性だけ。実際の `public/` の SVG と同じ形）は通り、許可リストの外を **1つ足すと落ちる**。悪い例は SD1.6 の表の全変形（`<script>`・`<SCRIPT>`・`<svg:script>`・`onload`・`ONLOAD`・`href="https://..."`・`url(http://...)`・`@import`・`foreignObject`・`DOCTYPE`・`ENTITY`・処理命令・CDATA・先頭以外のコメント・`data:`・`javascript:`・文字参照・属性値の中の `>`・引用符無し・二重の属性・BOM・NUL・双方向の制御文字・根の外の要素・大きさ超過）と、`style` 属性・`<style>`・`<a>`・`<use>`・`<image>`・`xlink:href`。表を使うテストで、違反の種別のコードも確かめる | NFR1.1.1・NFR1.1.5 | Vitest（表を使う単体テスト）。テストの説明文は英語 |
| SD7.1a | **性質ベースのテスト**（`fast-check`）: （1）任意の文字列で例外が出ず、短い時間で結果が返る（失敗時の乱数の種を記録）、（2）良い例の任意の位置に許可リスト外の要素を1つ入れると必ず落ちる、（3）許可された名前の大文字小文字を1文字変えると落ちる、（4）良い例に任意の前置き・後置きの文字（空白以外）を足すと落ちる、（5）受け入れた入力の `d` などの値には `<`・`>`・`&`・`:` が含まれない | NFR1.1.5・SD1.1・SD1.7 | Vitest + `fast-check` |
| SD7.1b | **別のパーサーとの突き合わせ（テストだけ）**: 検査が「受け入れた」例を、jsdom の `DOMParser`（`image/svg+xml`、厳密な XML）で読み、現れる要素の名前と属性の名前がすべて許可リストの内側で、接頭辞付きの名前・処理命令・外部の参照が無いことを確かめる。切り出しと本物のパーサーの食い違いを拾う。jsdom は既存の開発用の依存で、新しい依存は足さない | SD1.1・R-01 | Vitest（jsdom） |
| SD7.2 | 検査の対象の範囲: `e2e/` の下の仮の `.svg`（一時のディレクトリに作る）を、ヘッダーの検査と中身の検査の両方が見つける（片方だけが見ない状態を作らない）。ヘッダー: `.svg` の合格、ヘッダー無し、`/* */` の形、先頭が XML 宣言、が `checkContent` で落ちる | NFR1.1.2・SD1.9 | Vitest（`check-license-header.mjs`・`check-svg-safety.mjs` の単体テスト） |
| SD7.3 | 素材のファイル: `public/` の中身が決めた名前の一覧に一致する（余計なファイルが無い）、SVG はすべて SD1 の検査（`standalone`）を通る、PNG の署名と IHDR の大きさ、ICO のヘッダーとエントリの収まりと各画像の PNG の署名、大きさの上限（SVG 4 KiB・`.ico` 32 KiB・`.png` 16 KiB）。`index.html` に SVG の favicon と `.ico` か `.png` の `<link>` があり、`href` が外部を指さない | NFR1.1.3・NFR1.1.5・NFR2.1.1 | Vitest（実際のファイルを読む） |
| SD7.4 | ICO を詰める純粋な関数（SD3）: 出力が SD7.3 の形の検査を通る、PNG が1〜3 枚のときのディレクトリの位置と大きさ | NFR1.1.3 | Vitest |
| SD7.5 | `dist` の検査（L2）・WAR の検査（L3）が、素材が欠けた状態で**実際に失敗する**こと。`dist:check` は関数として取り出し、欠けた例・外部の `href`・HTML だった `favicon.ico` で単体テストを行う。`verifyFaviconInWar` は、コード生成で一度素材を一時的に外して失敗することを手で確かめ、結果を記録する（Gradle のタスクの単体テストは作らない） | NFR1.1.4・R-02 | Vitest + コード生成の確かめの記録 |
| SD7.6 | `/favicon.ico`・SVG の favicon の結合テスト（L1）と、動いている WAR への要求（L4） | NFR1.1.4 | SD4.2 のとおり |
| SD7.7 | ログイン画面のアイコン: SD5.2 のとおり（`outerHTML` の許可リスト、`d` の一致、飾り、vitest-axe 1件）。`react/no-danger` は oxlint が止める（`./gradlew verify` のリンタ） | NFR1.1.6・NFR3.2.1 | Vitest・oxlint |
| SD7.8 | 実際のブラウザの axe（ブランドカラーとテーマのすべての組）で違反 0 件。誤りの状態（ロック・認証失敗）と長い名前は、ログイン画面の既存の検査の範囲で行う。`runAxe` の規則を外す・`exclude` する・`KNOWN_VIOLATIONS` に足す形で通さない（`project.md` Forbidden）。違反 0 件は件数で Build and Test に記録 | NFR3.1.1 | 既存の E2E の書き換え（本数に数えない） |
| SD7.9 | 新しい依存が増えていない | NFR1.1.5 の前提 | コード生成の差分で `package.json`・lockfile・`build.gradle.kts` の依存に変更が無いことを確かめる |

## 脅威の見立ての更新（U6 の範囲）

| 脅威 | 対応する設計 |
|---|---|
| 自作の SVG に実行できるもの（`script`・イベントの属性・`foreignObject`）を混ぜる（大文字・接頭辞・コメント・CDATA・属性値の中の `>` で隠す形を含む） | SD1（許可リスト・fail-closed・変形の表）、SD7.1・SD7.1a・SD7.1b |
| SVG が外の URL を読みに行く（`href`・`url()`・`@import`・`image`・`use`・外部の実体・処理命令の `xml-stylesheet`） | SD1.3〜SD1.5（`href` を許さない、`url(#...)` だけ、`<?`・`<!` を拒否） |
| 検査の対象から漏れた `.svg`（`e2e/` などに置く）が、片方の検査にだけ見つかる | SD1.9（同じ範囲、同じ集める関数）、SD7.2 |
| 検査を通らない形のファイル（ヘッダー無し、先頭が XML 宣言） | SD2、SD1.3 の先頭の決まり |
| 出どころの追えないバイナリ・`public/` に混ざる余計なファイルが公開される | SD3（名前の一覧、作り方の記録、形の検査）、SD7.3 |
| `/favicon.ico` に HTML が 200 で返る（素材の欠け、`dist` の複写の漏れ） | SD4（L2・L3 で欠けを検出、L1・L4 で配信を確認）、SD4.3 |
| ログインなしで読める素材に、後から入った内容が混ざる | 上の各行。素材は公開のもので秘密を含まない（図柄だけ）。`public/` に秘密・個人の情報を置かない（Forbidden と Gitleaks） |
| アイコンが読み上げの邪魔になる | SD5.1（`aria-hidden`・名前を付けない）、SD7.7 |

## 論理の部品の見方（packaging のため `logical-components.md` は作らない）

| 部品 | 置き場 | 責務 | 失敗が及ぶ範囲 |
|---|---|---|---|
| AppIconAssets（素材） | `frontend/public/` → `dist` → WAR の `static/` | 自作の SVG・`.ico`・`.png` を静的なファイルとして配る | 素材が欠けると favicon が出ない（HTML が返る）。アプリの他の機能には影響しない |
| SvgSafetyCheck | `frontend/scripts/check-svg-safety.mjs` | SD1 の検査（ビルドの前の `./gradlew verify`） | 失敗は統合を止める。実行時には存在しない |
| LicenseHeaderCheck（拡張） | `frontend/scripts/check-license-header.mjs` | `.svg` のヘッダーの検査（SD2） | 同上 |
| DistAssetCheck・WarAssetCheck | `frontend/scripts/`・`build.gradle.kts` | L2・L3（SD4.2） | 同上 |
| LoginFrame のアイコン | `frontend/src/shared/`（部品）・`features/auth/`（置く場所） | ログイン画面のアプリ名の上の飾りのアイコン（SD5） | 描画の失敗は画面全体の失敗に及ぶが、固定の部品で状態を持たず、画面のテストで守る |

素材の配信は既存の `WebConfig`・`SecurityConfig`・`CacheControlFilter` を変えずに使う。共有する資源は無い（新しい接続・キャッシュ・状態を持たない）。

## 上流との差（承認済みの要件・設計は書き換えず、ここに記録する）

1. **`href` を許さない（NFR1.1.1 より厳しい）**: NFR1.1.1 は「`#` で始まる `href`・`xlink:href`」を許していたが、許す要素のどれも使わないため許可リストから外した（SD1.5）。`xlink:` の接頭辞・`xmlns:xlink` も許さない。
2. **検査の置き場（NFR Requirements の補足の具体化）**: ヘッダーの検査は `check-license-header.mjs` に `.svg` を足し、中身の検査は `check-svg-safety.mjs` で行う（NFR Requirements のとおり）。`standalone` の形（SD1.8）と、範囲を `frontend/` 全体にそろえること（SD1.9）が追加で、NFR1.1.1 の「`frontend/public/` と `frontend/src/` の下」より広い（R-04）。
3. **NFR2.1 の読み（R-03）**: 素材の大きさの上限 NFR2.1.1 は U6 の追加の要件で、上流の NFR2.1（日時の変換の問い合わせの数）とは別の意味。この段の `traceability.json` では上流の NFR2.1 を N/A にし、NFR2.1.1 は SD3・SD5.1・SD7.3 の設計の対象として下に書く。同じく NFR1.1.3（バイナリの出どころ）・NFR1.1.4（`/favicon.ico`）は上流の NFR1.1 の文言（SVG の安全とヘッダー）の外で、FR9.5・US4.1 の受け入れ基準に当たる。この段では NFR1.1 の細目として設計するが、出どころは FR9.5・US4.1 と読む。
4. **ICO の作り方（NFR Requirements の前提の差）**: ImageMagick を使う前提は、この PC に無いため成り立たない。`rsvg-convert` + 小さな Node のスクリプトで ICO を詰める（SD3）。
5. **`/favicon.ico` の結合テスト（NFR1.1.4 の確かめ方の差）**: 「Spring を起動して未認証で要求する」だけでは本物の素材を通らない（結合テストのクラスパスの `static/` はテスト用の見本）。4つの層（SD4.2）に分け、配備の後のスモークテストの項目（SD4.3）を足す（R-02）。
6. **キャッシュの見立て**: NFR Requirements は「ブラウザの favicon のキャッシュは強い」と置いたが、サーバー側は `no-cache` を付けるため、サーバーの指定が原因になる部分は無い。ブラウザの保持が残る点だけをスモークテストで確かめる（SD4.1・SD4.3）。
7. **`frontendBuild` の入力**: `build.gradle.kts` の `frontendBuild` の入力に `frontend/public/` を足す必要がある（SD6）。NFR Requirements には書かれていない作業で、コード生成の計画に入れる。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- セキュリティの要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/security-requirements.md`
- 技術の選択: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/tech-stack-decisions.md`
- NFR Requirements のレビュー: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/reviews/review-01.md`（R-01〜R-04）
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`（FR9・NFR1.1・NFR3.1・NFR3.2・NFR5.3）
- 単位の定義: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`（U6）
- 設計の決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/decisions.md`（ADR-008）
- 機能設計（functional-spec）と決まり（rules）: U6 は素材と検査だけの単位で、機能設計の段を持たないため、この単位の functional-spec・rules は無い。代わりに要件・単位の定義・ADR-008・NFR Requirements を上流とする
- 契約: `contract-summary.md` はこの単位の境界（素材の配信）に新しい契約を足さないため、参照しない（任意の入力）
- 既存のコードの読み取り: `frontend/scripts/check-license-header.mjs`・`frontend/index.html`・`frontend/vite.config.ts`・`frontend/e2e/050-display-accessibility.e2e.ts`・`backend/src/main/java/cherry/mastersmith/config/WebConfig.java`・`SecurityConfig.java`・`common/web/CacheControlFilter.java`・`backend/src/main/resources/application.yaml`・`backend/src/test/resources/static/`・`build.gradle.kts`・`backend/build.gradle.kts`（`verifyDslSchemaInWar`）
- 捨ての試し（リポジトリの外。設計の前提の確かめ）: SD1 の走査を小さな Node の試しで書き、SD1.6 の悪い例 40 件ほど（変形を含む）がすべて拒否され、良い例とインラインの形が通ること、入力3つの最悪の走査が合計 1 ミリ秒未満であることを確かめた。この PC の `rsvg-convert` は 2.63.4、ImageMagick は無い
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `.ico` の `Content-Type`（`image/x-icon` か `image/vnd.microsoft.icon`）は、Spring の資源の取り扱いの対応に任せ、`image/` で始まれば合格とする。実際の値は L1 の結合テストで確かめる。持ち主: Code Generation（L1 を最初に流す）。
- [assumption] 「ログインなしで読める」「`nosniff` が付く」は、コードの読み取りでは成り立つ（`anyRequest().permitAll()`・`contentTypeOptions`）。実行での確かめは L1・L4。成り立たなければ SD4.4 の戻り先に従い、サーバーの変更になるため依頼者に諮る。持ち主: Code Generation（L1）→ Build and Test（L4）。
- [assumption] `check-svg-safety.mjs` は、検査の対象を `check-license-header.mjs` の `collectFiles`・`SKIP_DIRS` の export で共有できる。export を足す変更は小さいが、既存の検査の公開の形が変わるため、コード生成の計画に書く。持ち主: Code Generation。
- [assumption] favicon の SVG は固定の1色で、ダークテーマの見え方の切り替えは図柄の確定（画面イメージの案）で決める。`style`・`prefers-color-scheme` を使う形は許可リストを広げるため、要るときは依頼者に諮る。持ち主: 図柄の確定の段（Code Generation の計画）。
- [assumption] 素材の最終の名前・数（`apple-touch-icon.png` を置くか、192・512 px の PNG を採るか）は、Code Generation の計画で決める。SD3・SD7.3 の一覧と大きさの上限は、決めた一覧に合わせて書く。
- Unverified: Firefox・Safari での favicon の見え方（SVG の favicon の対応・`.ico` へのフォールバック）。Chromium 系のほかは、この PC で確かめられない。持ち主の段: Deployment Execution のスモークテスト（SD4.3。依頼者が実際の画面で見る）。
- Unverified: ブラウザの favicon の保持（図柄を変えたあとの更新）。持ち主の段: Deployment Execution のスモークテスト。
- 質問にした点は無い（判断が分かれる論点は、レビューの指摘で答えの形が決まっており、上の差と前提で扱える）。
