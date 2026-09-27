# Security Design — U4 表示の設定の土台（u4-display-foundation）

U4 のセキュリティの設計です。U4 は画面の単位で、サーバー側の認可を持ちません。この文書では、次の作りを決めます。

- ブラウザに残す値
- 登録の完了からの受け渡し
- トークンを付けない公開の API のパスの判定
- 要求のヘッダーと、見た目の設定の応答の値の当て方
- CSP を緩めないことと、検査の道具（axe-core）の読み込み方
- 依存の扱い

答えは `nfr-design-questions.md` にあります（Q1: A、Q2: A、Q3: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号:

- NFR: この単位の NFR 要件 `construction/u4-display-foundation/nfr-requirements/` の枝番
- D・W・4.1・9節: この単位の機能設計 `construction/u4-display-foundation/functional-design/functional-spec.md`
- 部品 n節: 同じフォルダの `frontend-components.md`
- 要点 n: この段の `nfr-design-questions.md` の「NFR 設計の要点（案）」の番号

成果物ごとの置き場は、`performance-design.md` の冒頭の表のとおりです。

## 前提

- サーバー側の認可（公開の範囲、未認証・管理者でない・管理者の 401・403・200）は、U3・U8 のサーバー側のテストで確かめます。画面の側の作りは、その代わりにしません（要件 NFR4、`team.md` の Testing Posture）。
- トークンと利用者の情報は、既存の `frontend/src/features/auth/authSession.ts` のとおりメモリだけに持ちます。この単位では変えません。
- GitHub のリポジトリは公開のため、テストのデータに実在の個人に関する値を使いません。メールアドレスは `example.com` を使います。

## 1. ブラウザの保存（NFR2.2、4.1・D5〜D7）

### 1.1 作り

| 項目 | 作り |
|---|---|
| 読み書きの場所 | `frontend/src/app/display-settings/` の中の1つのモジュール（仮の名前 `browserStorage.ts`）だけが、U4 の鍵 `mastersmith.display-settings` と、make-you-chic-ui の写しの鍵（`design-system-theme`・`design-system-font-size`）を読み書きする。ほかのモジュールと機能（`features/*`）は localStorage を直接触らない |
| 書く値 | 3つの軸（`language`・`theme`・`fontSize`）だけを持つ形から、その都度 JSON を作る。ログイン状態・応答の本文・利用者の設定のオブジェクトをそのまま書かない（余計な項目が混ざらない） |
| 読む値 | JSON として読めなければ全体を無いものとする。読めたら、項目ごとに許される値の一覧と比べ、合う項目だけを受け入れる（D7）。知らない項目は捨てる |
| 例外 | 読み書きの呼び出しを `try`/`catch` で包み、外へ出さない。読めなければ無いものとし、書けなければ保存せずに続ける（D7） |
| make-you-chic-ui の鍵 | 写しの2つの鍵の書き直しは同じモジュールで行う（D5）。`design-system-brand`・`design-system-font-family` は U4 から書かない（make-you-chic-ui の `setBrand`・`setFontFamily` が書く） |

```ts
// 説明用の断片（名前は仮）。保存の形は3つの軸だけから作る。
function toStoredJson(s: { language?: Language; theme?: ThemeChoice; fontSize?: FontSize }): string {
  return JSON.stringify({ language: s.language, theme: s.theme, fontSize: s.fontSize })
}
```

### 1.2 確かめ

画面部品のテスト（部品 7節のブラウザの保存と `DisplaySettingsProvider`）で確かめます。

1. 次の後に、U4 の鍵の値を JSON として読み、項目の名前が `language`・`theme`・`fontSize` の中だけであることを見ます。
   - ログインの成功
   - プリファレンスの保存（`applyUserPreferences`）
   - 登録の完了の保存（`saveBrowserDisplaySettings`）
   - ログインの画面の言語の切り替え
2. 同じ時点で、localStorage と sessionStorage のすべての鍵の値に、テストで使ったトークン・メールアドレス・氏名の文字列が含まれないことを見ます。
3. 読み取りと検証の関数は、性質ベースのテスト（fast-check）で見ます。
   - どんな文字列を入れても、例外を出さない
   - 受け入れた値は、許される値だけになる
   - 失敗時の種を記録する（`logical-components.md` の 7節）
4. `localStorage` のアクセスが例外を投げる状態を差し替えで作り、画面が描かれることを見ます。

## 2. 登録の完了からの受け渡し（NFR2.1、D13・W9）

| 項目 | 作り |
|---|---|
| 置き場 | `frontend/src/app/login-handoff/` のモジュールの中の変数1つだけ。React の状態・URL・ブラウザの保存・`history.state` に載せない |
| 持つ値 | メールアドレスの文字列だけ。型を `{ email: string }` に限り、パスワード・招待のトークンを受ける口を作らない |
| 受け取り | `LoginForm` の最初の描画で1回だけ読む。消すのは描画の確定の後（W9 の3。StrictMode の二重の描画でも失われない） |
| 出さない所 | URL（パス・問い合わせ・`#` の後）、localStorage・sessionStorage、`console`、エラーの表示 |

確かめ（部品 7節の `LoginForm`）:

- 受け渡しの後に、`window.location.href` と、localStorage・sessionStorage のすべての値にメールアドレスが無いこと
- 2回目の表示と読み込み直しでは、案内が出ないこと

## 3. トークンを付けない公開の API のパス（NFR9.1、D10、Q3: A）

### 3.1 一覧と判定

- 今の `AUTH_API_PATHS`・`isAuthApiPath` を、1つの一覧と1つの判定にまとめ、名前を改めます（Q3: A）。
  - 仮の名前は `TOKENLESS_API_PATHS`・`isTokenlessApiPath` です。名前はコード生成で決めます。
  - 一覧は、認証の API の3つと公開の API の3つを、コメントで分けて並べます。
- 今の名前は `frontend/src/shared/api-client/apiClient.ts` の中だけで使われています。ほかのファイルとテストは参照していないため、改めても影響はこのファイルに閉じます。
- トークンを付けないかを決める場所は、この一覧と判定の関数の1か所だけにします。`apiFetch` は判定の結果だけで分岐します。点検するときは、この1か所を見れば足ります。

```ts
// 説明用の断片（名前は仮）。トークンを付けず、401 で更新も送り直しもしないパス。
export const TOKENLESS_API_PATHS = [
  // 認証の API（BR8.5）
  '/api/auth/login', '/api/auth/session/refresh', '/api/auth/session/logout',
  // 公開の API（契約 C7・C6）
  '/api/appearance', '/api/registration/verify', '/api/registration/complete',
] as const
export function isTokenlessApiPath(path: string): boolean {
  const withoutQuery = path.split('?')[0]
  return TOKENLESS_API_PATHS.some((p) => p === withoutQuery)
}
```

### 3.2 判定の決まり

| 決まり | 理由 |
|---|---|
| 問い合わせの部分（`?` の後）を除いた完全な一致 | 似た別のパス（`/api/registration/other`・`/api/appearance/x`・`/api/appearances`）をトークンなしで送らない |
| 前方一致・正規表現・`startsWith` を使わない | 一覧に無いパスが公開として扱われる誤りを防ぐ |
| 大文字・小文字・末尾の `/` の揺れを直さない | 呼び出し側は一覧と同じ文字列で呼ぶ。揺れたパスはトークンを付ける側（今までの扱い）に倒れる。サーバーは公開のパスでもトークンを検証するため、付けても害は 401 だけ |
| トークンを付けないパスでは、401 でも更新と送り直しをしない | D10。期限切れのトークンで登録の完了が妨げられない |
| ほかのパスの付け方と、401 の更新の流れは変えない | 既存の認証の決まりのまま |

### 3.3 確かめ

ApiClient の単体テスト（部品 7節の ApiClient）で確かめます。

- アクセストークンを持つ状態で公開の3つのパスを呼び、`Authorization` が付かないこと
- 401 / `AUTHENTICATION_REQUIRED` でも、更新の手段が呼ばれないこと
- 問い合わせの付いたパスも、同じに判定されること
- 似た別のパス（`/api/registration/other`・`/api/appearance/x`）にはトークンが付くこと
- 既存の認証の API の3つが、今までどおりトークンなしで送られること（名前を改めた後も既存のテストが通る）

## 4. 要求の言語と見た目の値（NFR9.2・NFR9.3、D9・D14・W3・W12）

| 対象 | 作り |
|---|---|
| `Accept-Language` | ApiClient は、登録された関数（AppFrame が登録する）が返す値だけを付ける。関数の戻り値の型は `'ja'` か `'en'` の2つの値だけに限り、付ける前にも一覧と比べる（型を越えた値が来ても付けない）。呼び出し側が `Accept-Language` を明示していれば上書きしない。関数が登録されていなければ付けない |
| 言語を決める関数 | 画面の言語は、D1 の順（見せ方・利用者の設定・ブラウザの保存の値・ブラウザの言語設定）で決まり、どれも許される値だけを通す。利用者の入力の文字列がヘッダーに届く道は無い |
| 見た目の設定の応答 | 応答の本文を読んで、`brandColor` は `blue`・`green`・`purple`・`orange`、`fontFamily` は `sans`・`serif` と、項目ごとに比べる。合う項目だけを `setBrand`・`setFontFamily` に渡す。余計な項目は読まない |
| HTML への差し込み | 応答の値を HTML・属性・スタイルに直接差し込まない。`<html>` の `data-brand`・`data-font-family` は make-you-chic-ui が変える。`react/no-danger` などの既存のリンタの決まりのまま |
| Problem Details | `ApiError.problem` は、応答の本文を追加の項目ごと持つ（既存の `apiError.ts`）。U2 の `fieldErrors`（`construction/u2-user-preferences/nfr-design/security-design.md` の3節）のために ApiClient は変えない。読むのは U7 の `fieldErrors.ts` |

確かめ:

- **ApiClient の単体テスト:** 許される値だけが付くこと、呼び出し側の指定が残ることを見ます。
- **性質ベースのテスト:** 言語を決める関数が、どんな入力でも `ja`・`en` のどちらかを返すことを見ます。
- **画面部品のテスト（`DisplaySettingsProvider`）:** 次の応答で、`<html>` の `data-brand`・`data-font-family` が変わらないことを見ます。
  - 許されない値
  - 余計な項目
  - 形の誤り（配列・数・`null`）

## 5. CSP と埋め込み（NFR9.4、9.1）

| 決まり | 作り |
|---|---|
| アプリの CSP を変えない | `backend/src/main/resources/application.yaml` の CSP（`default-src 'self'`・`script-src 'self'`・`style-src 'self'`・`font-src 'self'`・`connect-src 'self'` ほか）に差分を作らない |
| 外部への通信・外部のフォントを足さない | フォントは `@fontsource` から自前で配信する。見た目の設定の読み取りは同じオリジンの `/api/appearance` |
| 埋め込みのスクリプト・スタイルを足さない | `frontend/vite.config.ts` の `modulePreload.polyfill: false`・`assetsInlineLimit: 0` のまま。写しの鍵の書き直し（W1 の2）は、`index.html` の埋め込みのスクリプトではなく、入口のモジュールで行う |
| 検査のために CSP を緩めない | 実際のブラウザの検査（6節）の都合は、検査のブラウザのコンテキストだけで扱う |

確かめ:

- **コード生成:** `application.yaml` の CSP に差分が無いことを見ます。ビルドした `index.html` に、埋め込みのスクリプト・スタイルが無いことを見ます。
- **Build and Test:** 実際のブラウザで、コンソールに CSP の違反が出ないことを見ます（`performance-design.md` の 3.3）。

## 6. axe-core を検査のブラウザの中だけで読み込む（NFR9.4・NFR9.6）

### 6.1 読み込み方

- axe-core の本体（`axe-core/axe.min.js`）は、明示の devDependencies（4.13.0）から Node の側で読みます。例: `createRequire(import.meta.url).resolve` と `readFileSync`。
- 読んだ本体を、検査のページで `page.evaluate` を使って評価します。`@axe-core/playwright` と同じ読み込み方です。
  - この評価はブラウザの操作の口（DevTools の手順）から行うため、ページの CSP（`script-src 'self'`）に止められない見込みです。
  - コード生成で、実際に CSP の見出しが付いた WAR の画面で評価でき、同時に CSP の違反の知らせが出ないことを確かめます。
- 評価の後、`axe.run` を同じく `page.evaluate` で呼び、結果（違反・判定できなかった要素）を Node の側へ返します。

```ts
// 説明用の断片（名前は仮）。検査のページの中だけで axe-core を動かす。
const axeSource = readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8')
async function runAxe(page: Page): Promise<AxeResults> {
  await page.evaluate(axeSource)
  return page.evaluate(() =>
    window.axe.run(document, { runOnly: { type: 'tag', values: WCAG_TAGS } }),
  )
}
```

### 6.2 使わない方法

| 方法 | 使わない理由 |
|---|---|
| アプリの CSP・`index.html`・画面のコードに axe-core を入れる | 画面の成果物（`dist`・WAR）に検査の道具が入り、NFR9.4・NFR9.6 に反する |
| コンテキストで CSP を外す（`bypassCSP: true`） | アプリの実際の条件とずれる。同じファイルで CSP の違反を見る確かめができなくなる |
| `page.addScriptTag({ content })`・`addScriptTag({ path })` | 埋め込みのスクリプトになり、`script-src 'self'` で止まる |
| 同じオリジンの道（例: `/__axe.js`）への差し替え（`page.route`）で配信し、`addScriptTag({ url })` で読む | CSP には通るが、検査のためだけの道が増える。`page.evaluate` で足りる |

### 6.3 合否に使う規則（Q2: A）

- `axe.run` の `runOnly` に、WCAG 2.0・2.1 の A と AA のタグだけを渡します。
  - タグは `wcag2a`・`wcag2aa`・`wcag21a`・`wcag21aa` です。
  - ベストプラクティス（`best-practice`）と WCAG 2.2 のタグは流しません。
- 手元の axe-core 4.13.0 で、主な規則のタグを確かめました（`frontend/node_modules/axe-core` の `getRules()`）。
  - `color-contrast` は `wcag2aa` を持ちます。
  - `scrollable-region-focusable` は `wcag2a` を持ちます。
  - どちらもタグだけで入るため、規則の名前で足す必要はありません（Q2: A の「無ければ名前で足す」は当たらない）。
  - 版を上げてタグが変わったときは、この2つが結果の規則の一覧にあることを検査の中で確かめ、無ければ失敗させます（6.4）。
- 合否は、違反（`violations`）が 0 件であることで決めます。
- 判定できなかった要素（`incomplete`）は失敗にしません。規則の名前と件数を、テストの注記に記録します。例: 背景の重なりで色を決められないコントラスト。
- 違反があったときは、規則の名前・対象の要素・組をテストの失敗の説明に出します。

### 6.4 規則が抜けないことの確かめ

検査の中で、`axe.run` の結果の規則の一覧（`passes`・`violations`・`incomplete`・`inapplicable` の規則の名前の合わせ）に、`color-contrast` と `scrollable-region-focusable` が含まれることを確かめます。

- 含まれなければ失敗させます。
- 版を上げてタグが変わり、黙って流れなくなることを防ぐためです。

## 7. 依存（NFR9.5〜NFR9.7）

| 依存 | 種類 | 扱い |
|---|---|---|
| `@fontsource/noto-serif-jp` 5.3.0 | `dependencies`（実行時） | OFL-1.1（採用の記録は機能設計の 9.2）。lockfile（`frontend/package-lock.json`）で固定し、CI では `npm ci`。依存関係の脆弱性検査で重大度 High 以上なら統合を止める。推移依存が無いことをコード生成で記録する |
| `axe-core` 4.13.0 | `devDependencies`（明示で足す） | MPL-2.0（採用の記録は NFR 要件の `tech-stack-decisions.md` の 2.1）。改変しない。画面の成果物に入れない。lockfile で固定する。vitest-axe が求める範囲（`^4.4.2`）の中で、lockfile の axe-core が1つの版にそろうことを記録する。脆弱性は警告の扱いで、悪意のあるパッケージ（OSV の `MAL-`）だけ止める。成果物を作る道具ではないため、`config/npm-build-tools.txt` に足さない |
| make-you-chic-ui（`vendor/make-you-chic-ui`） | サブモジュール、`file:` の依存 | 固定先の更新（`edb1f943c0e66293494fa974605f34fcd7e258d7` → `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`）は、B4 で承認を得た専用のコミットで行い、前後のハッシュを記録する。中身は変えない。更新した版の lockfile も依存関係の脆弱性検査の対象に入る。統合は短命のブランチから fast-forward でよい（`team.md` の Way of Working） |

確かめ:

- **axe-core が画面の成果物に入らないこと:** ビルドの後の `dist/` の中に、axe-core の本体に特有の文字列（本体の先頭の著作権の表示 `Deque Systems`）を含むファイルが無いことを、コード生成で1回確かめて記録します。短い `axe` の文字列はほかの部品にも現れうるため、判定に使いません。
  - `frontend/src/` から axe-core を読み込まないことで作ります。
  - vitest-axe は `*.test.tsx` だけから読み込まれるため、ビルドに入りません。

## 8. 脅威と扱い

| 脅威 | 扱い | 設計 |
|---|---|---|
| 共用の PC で、前の利用者の情報がブラウザに残る | ブラウザに残すのは3つの表示の設定だけ。書く形を3つの軸から作る | 1節 |
| 招待のメールアドレスが URL に載り、履歴・リファラー・アクセスログに残る | 受け渡しはモジュールの変数だけで、URL・`history.state` に載せない | 2節 |
| 期限切れのアクセストークンが公開の API で 401 を起こし、登録の完了が妨げられる | 公開の API にトークンを付けず、401 で更新しない | 3節 |
| 似た名前のパスを公開と誤って判定し、トークンなしで要求する | 1か所の一覧と、完全な一致の判定 | 3節 |
| トークンを付けない判定が複数の場所に散り、点検で見落とす | 一覧と判定を1つにまとめる（Q3: A） | 3.1 |
| ヘッダーへの差し込み（`Accept-Language` に改行や任意の値が入る） | 許される値だけを付け、付ける前にも比べる | 4節 |
| 見た目の設定の応答の改ざん・想定外の値による表示の乗っ取り | 項目ごとに許される値だけを当て、HTML に差し込まない | 4節 |
| 外部のフォントの配信元へのアクセスの情報の漏れ・CSP の緩み | 自前で配信し、CSP を変えない | 5節 |
| 検査の道具が成果物に混ざる・検査のために CSP を緩める | axe-core は検査のページで `page.evaluate` だけ。成果物に入らないことを確かめる | 6節・7節 |
| 依存の脆弱性・悪意のあるパッケージ | 版を lockfile で固定し、依存関係の脆弱性検査の対象に入れる | 7節 |

## 9. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| トークンを付けないパスを1つの一覧と1つの判定にまとめ、名前を改める（Q3: A） | 機能設計の `frontend-components.md` の 6.1 | 「名前を改めるか、公開の API の一覧を別に置いて判定でまとめるかはコード生成で決める」 | この段で前倒しで決めた（依頼者の決定）。ふるまいは NFR9.1 のまま変わらない。名前の具体（仮の名前 `TOKENLESS_API_PATHS`・`isTokenlessApiPath`）はコード生成で決める |
| axe-core を `page.evaluate` で読み込む | NFR 要件の NFR7.3・NFR9.4 | 「axe-core の読み込みに必要な扱いは、この検査のブラウザのコンテキストだけで行う」 | 読み込み方を `page.evaluate` に決め、`bypassCSP` と `addScriptTag` は使わないとした（6節）。要件の範囲の中の作りで、食い違いではない |
| axe の合否を WCAG 2.0・2.1 の A・AA のタグだけで決める（Q2: A） | NFR 要件の NFR7.3 | 「コントラストを含む違反 0 件」（規則の範囲は決めていない） | 規則の範囲をこの段で決めた。`incomplete` は失敗にせず記録する。追加 |
| `scrollable-region-focusable` を名前で足さない | この段の Q2: A | 「タグに入っていなければ名前で足す」 | axe-core 4.13.0 で `wcag2a` のタグを持つと確かめたため、名前で足さない。代わりに、規則が抜けないことを検査の中で確かめる（6.4）。答えの条件どおりで、食い違いではない |
| axe-core が成果物に入らないことを、`dist` の中の文字列で確かめる | NFR 要件の NFR9.6 | 「axe-core が画面の成果物（`dist`）に入らないこと」（確かめ方は決めていない） | 確かめ方を決めた（7節）。追加 |
