# Security Design — U5 利用者の管理の画面（u5-user-admin-ui）

U5 のセキュリティの設計です。承認済みの NFR 要件 `aidlc/spaces/default/intents/260930-user-admin/construction/u5-user-admin-ui/nfr-requirements/security-requirements.md`（NFR1.1・NFR1.2・NFR3.1〜NFR3.5・NFR9.1、承認の場の決定 R-01〜R-05 と申し送り）と `tech-stack-decisions.md`（NFR9.2〜NFR9.4・NFR9.9）を満たす作りを決めます。U5 は画面の単位（種類 ui）で、サーバー側の認可と監査を持ちません。守りの中心は、画面の判定をサーバーの判定の代わりにしないことと、個人に関する値と E2E の資格情報を画面と報告の外に出さないことです。性能の作りは `performance-design.md`、部品の一覧と障害の範囲、アクセシビリティ・多言語・テストの関門は `logical-components.md` にあります。

この段の質問 `nfr-design-questions.md` の設計の要点 6〜12 と答え（Q1 A・Q2 A・Q3 A）、まとめの確認（Looks correct）で決めました。プラットフォームの視点（配備先は開発者の PC 上のコンテナ）は 9節に重ねて書きました。Playwright の動きは、質問の文書と同じく `frontend/node_modules/playwright/` の 1.63.0 のソースを読んだ範囲で、この段では実行していません。

出典の略号: NS は `nfr-requirements/security-requirements.md`、NP は `nfr-requirements/performance-requirements.md`、TS は `nfr-requirements/tech-stack-decisions.md`、FS は `functional-design/functional-spec.md`（D・W はその決まりと流れ）、FC は `functional-design/frontend-components.md`、要点 n はこの段の `nfr-design-questions.md` の設計の要点、C3・C4 は `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md` の C3・C4、TM は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。画面のコードのパスは `frontend/src/features/useradmin/` の下を `useradmin/` と略します。

## 1. 信頼の境界と守りの配置

| 境界 | 入ってくるもの・出ていくもの | 守り | 置き場 |
|---|---|---|---|
| 画面 → 管理の API | 一覧の要求（`page`・`q`）、5つの操作、氏名と言語 | 画面は押せない形を示すだけ。判定はサーバーが要求ごとに行う | U3・U1（2節） |
| 管理の API → 画面 | 一覧の行、エラー応答 | C3 の項目だけを型で読む。文言は `code` から選び、`detail`・`title` を出さない | `useradmin/api/`・`useradmin/failureMessage.ts`（6節） |
| 画面 → ブラウザ | 画面の状態（ページ・検索の文字・行の値） | 画面の中の状態だけに持ち、URL・保存・履歴・コンソールに出さない | `useUserAdmin`（6節） |
| E2E → 報告のファイル | 操作の記録・失敗の手がかり・測った値 | 値を入れない書き方、html と trace を作らない、報告の部品で探す | `frontend/playwright.config.ts`・報告の部品・新しい手伝い（3節） |
| E2E の 120 → アプリ | `/api/admin/` の下の要求 | 書き換えの要求を本物へ通さず、打ち切って数える | 120 の差し替えの口（4節） |
| 依存 → ビルド | make-you-chic-ui の版、npm のパッケージ | 固定先のコミットで固め、インストールのスクリプトを動かさない | サブモジュール・`frontend/.npmrc`（8節） |

## 2. 画面の判定とサーバーの判定（NFR1.1・NFR1.2、要点 6）

### 2.1 行の項目と押せなさ（NFR1.1）

- 行の「操作」のメニューの項目と押せなさは、純粋な関数 `useradmin/rowActions.ts` が行の `admin`・`suspended`・`resettable`・`self` の4つだけから決めます。画面の外の状態（ログインした人の印の写しなど）や、ほかの項目を読みません。
- 自分の行の「管理者の印を外す」「利用を止める」は `aria-disabled="true"` と理由の文で押せない形にします。押しても要求・確かめの表示・入力の表示を出しません（`useUserAdmin` の `selectAction` も押せない項目を捨てる、二重の守り）。
- これは表示の守りです。サーバー側の拒否（自分自身の印を外す・止める操作の拒否、最後の管理者の保護、401・403・200）は U3・U1 に残し、サーバー側のテストで確かめます（PM の Mandated）。U5 では `backend/` に差分が無いことをコード生成で確かめて記録します。

### 2.2 403 と 401（NFR1.2）

失敗の扱いの入口は `useUserAdmin` の `handleFailure(error, path, context)` の1か所です（FC 3.4）。API を呼ぶ道はすべてここを通します。

```text
// handleFailure の順（説明用）
if (error.status === 401) { clearBusy(); closeConfirm(); closeEdit(); return }  // 文言は出さない
if (adminForbidden(error, path)) return      // U4 の useAdminForbidden。AppFrame が S6 に置き換える
switch (context) { case 'list': ...; case 'action': ...; case 'save': ... }   // 一般の失敗
```

- 403 は C4 の `useAdminForbidden` に失敗と要求のパスを渡します。真が返れば画面は何も出さず、AppFrame がコンテンツの領域ごと置き換えるため、画面の部品と開いていた Modal が外れます。
- 401 は既存の ApiClient とログインの状態に任せ、画面の文言を出しません。送信中の印を戻し、確かめの表示（S3）と入力の表示（S4）をどちらも閉じます。
- 画面の側の管理者の印を U5 から書き換えません。

## 3. E2E の報告に値を残さない（NFR3.4、要点 8〜10、Q1 A・Q2 A・Q3 A）

### 3.1 守る値と報告

| 守る値 | 出どころ | 報告の部品が探す形 |
|---|---|---|
| 署名鍵・初期管理者のメールアドレスとパスワード | `playwright.config.ts` がプロセスの環境変数に置く（今のまま） | 環境変数の値 |
| 利用者 U のメールアドレス・パスワード・氏名、実行ごとの印 `runTag` | 110 が `createRegisteredUser` で作る | 値のファイルの値（3.4）と、値の形（3.5） |

| 報告 | この設計の後 |
|---|---|
| json の報告（`test-results/e2e-results.json`） | 作る。Build and Test が写す唯一の報告 |
| html の報告（`playwright-report/`） | 作らない（Q1 A、3.2） |
| trace（`test-results/` の下の `trace.zip`） | 既定で作らない（Q2 A、3.2）。手元で調べるときだけ有効にする（3.7） |
| 失敗の画面の写し（`test-results/` の下の `error-context.md`） | 止めない。探す先に入れる（Q3 A、3.5・3.6） |

### 3.2 報告を作らない・残さない（Q1 A・Q2 A）

`frontend/playwright.config.ts` を次のとおり直します。

```text
// playwright.config.ts の変わる所（説明用）
const TRACE_MODES = ['off', 'on', 'retain-on-failure'] as const
const traceMode = process.env.E2E_TRACE ?? 'off'          // 決まった3つの外なら設定の読み込みで止める
reporter: [
  ['list'],
  ['json', { outputFile: jsonResultsFile }],              // html は外す（Q1 A）
  ['./playwright-secret-check-reporter.ts', { outputFile: jsonResultsFile, envNames: SECRET_ENV_NAMES, ... }],
],
use: { ..., trace: traceMode },                           // 既定は off（Q2 A）
```

- html の報告を作らない（Q1 A）。Playwright 1.63 の html の報告は、`fill` の手順の題（`Fill "<入れた値>"`）を含むすべての手順を書くため、既存の `loginAsAdmin`・`loginWithForm` が入れる資格情報が残ります。作らないことで、値を入れる手伝いを変えずに「報告に値を残さない」目的を満たします。
- trace の既定を `off` にする（Q2 A）。080 を含むすべての E2E（010〜100、U4 の 130、110・120）で trace を残しません。E2E のファイルは変えず、設定の1か所で効かせます。
- 110 と 120 は、明示のために `test.use({ trace: e2eTraceMode() })` を置きます。`e2eTraceMode()` は新しい手伝いの関数で、設定と同じ `E2E_TRACE` の読み方（無ければ `off`）を返します。既定の実行では `off` で、NS の NFR3.4 (c) の「110 と 120 は trace を残さない」を満たします。手元で調べるとき（3.7）だけ、ほかの E2E と同じ環境変数で有効になります（12節の差 3）。
- `E2E_TRACE` に決まった3つの外の値が入っていたら、設定の読み込みで誤りにして止めます（黙って `off` にしない）。
- 設定のファイルの冒頭の説明（「結果は list・html に加えて json」）と `README.md` の E2E の手順は、コード生成で直します。

### 3.3 値を入れない書き方（NS の NFR3.4 (a)・(b)）

- `test.step` の題・注記・添付・`expect` の説明文・失敗の知らせに、3.1 の値を入れません。資格情報はプロセスの環境変数で渡し、`webServer.env` に置きません（今のまま）。
- locator の名前と期待値に値を入れません。110 は検索の欄に `runTag` を入れて U の行を1行に絞り、その行の中で「操作」のボタンを、役割 `button` と名前の末尾「の操作」で探します。
- 読み上げの名前に氏名とメールアドレスが入っていることは、名前を読んで比べた真偽だけを `expect` に渡します。

```text
// 110 の確かめ方（説明用）
const name = await row.getByRole('button', { name: /の操作$/ }).getAttribute('aria-label') ?? ''
expect(name.includes(user.email) && name.includes(REGISTERED_DISPLAY_NAME), '操作の名前に対象の氏名と宛先がある').toBe(true)
```

- 検索の欄への `runTag` の入力（`fill`）は、html の報告と trace を作らないため報告の手順の題に残りません（json の報告は `test.step` の手順だけを書く）。

### 3.4 値のファイル（要点 8）

- 110 が U を作った直後に、新しい手伝い（例 `recordSecretValues`）が、U のメールアドレス・パスワード・氏名と `runTag` を `test-results/` の下の1つのファイルに書きます。ファイルの名前と手伝いの置き場はコード生成の計画で決めます（例 `test-results/e2e-secret-values.json`）。
- 中身は種類と値の組の JSON の配列です（例 `[{ "kind": "email", "value": "…" }]`）。workers が 1 のため、書くときは読み足して書き直します。
- 作るときに権限 600 で作り、書いた後にも 600 に直します（umask に左右されないため）。
- Playwright は実行の始めに `test-results/` を消すため、前の実行の値は残りません。報告の部品は探し終えたらこのファイルを消します。途中で止めた実行で残ったファイルは、次の実行の始めに消えます。
- 110 を飛ばした（前提が無い）実行では、ファイルは作られません。報告の部品は環境変数の値と値の形だけで探します。

### 3.5 報告の部品の探し方（要点 9）

`frontend/playwright-secret-check-reporter.ts` を広げます。`onEnd` で次を行います。

1. **探す値を集める**: (1) 環境変数の3つ（今のまま）、(2) 値のファイルの値、(3) 値の形。
2. **形を広げる**: (1)・(2) の値は、そのままの形に加えて、URL の形（`encodeURIComponent`）と JSON の `\u` の形（ASCII の外の文字を `\uXXXX` にした形）でも探します。trace の要求の記録や json の中で形が変わるためです。
3. **値の形で探す**: ファイルが書かれなかった場合や前の実行の残りに備えて、次の形でも探します。

| 種類 | 形 |
|---|---|
| U のメールアドレス | `u7-perf-` で始まり `@example.com` で終わる宛先（`@` が `%40` の URL の形を含む） |
| U のパスワード | `e2e-u7-pw-` と 24 文字の16進 |
| U の氏名 | 「計測 花子」（そのまま・URL の形・`\u` の形） |

4. **探す先**: json の報告と、`test-results/` の下のすべてのファイル（`trace.zip`・`error-context.md`・添付を含む。値のファイル自身は除く）。ファイルはバイト列として読み、値の UTF-8 のバイト列を探します。形は ASCII の正規表現で探します。
5. **zip を読む**: 拡張子が `.zip` のファイルは、中央の目録を読み、各ファイルを `node:zlib` の `inflateRawSync`（圧縮なしの項目はそのまま）で展開して読みます。新しい依存を足しません。
6. **前の html の報告**: `playwright-report/` が残っていれば、前の実行の報告として探します。`index.html` に埋め込まれた base64 の zip を取り出して 5 と同じく展開し、`data/` の下のファイルとあわせて値の形と今の値で探します。見つかれば失敗にし、消す手順（`frontend/playwright-report/` を消す）を示します。見つからなくても、もう作らない報告のため消すよう警告を出します。初期管理者のメールアドレスは実行ごとに変わらない値のため、前の実行の html の報告が残っていれば見つかって失敗になる見込みで、コード生成（B5）の最初の実行で一度消すことになります。
7. **開けないもの**: 読めない・展開できない（壊れた zip、知らない圧縮の方式、取り出せない base64）ファイルがあれば、黙って通さず失敗にします。
8. **出力**: 値は表示しません。値の種類（`signingKey`・`adminEmail`・`adminPassword`・`email`・`password`・`displayName`・`runTag`）と、ファイルの種類（json の報告・trace・失敗の画面の写し・そのほか・前の html の報告）ごとの件数だけを出します。
9. **片付け**: 値のファイルを最後に消します（見つかったかどうかと関係なく、`finally` で消す）。
10. **trace を有効にした実行**: `E2E_TRACE` が `off` でないときは、「trace を有効にした実行のため、合否に使わず、見た後に `test-results/` を消す」旨を最初に出します（3.7）。

### 3.6 失敗のときの値を伏せた手がかり（Q3 A、要点 10）

110・120 では trace を残さないため、新しい手伝い（例 `userAdminDiagnostics`）が、テストの間に値を含まない記録を集め、テストが失敗したときだけ注記と添付（名前 `user-admin-diagnostics`）に残します。

| 記録 | 中身 | 値を含まない理由 |
|---|---|---|
| 通った手順の題 | 手伝いを通した `test.step` の題 | 題は値を入れない決まり（3.3） |
| 要求 | `/api/admin/`・`/api/auth/` の下の要求のメソッドと道の型と状態コード | 道の中の利用者 ID は `{id}` に置き換え、問い合わせ（`q` など）と `#` の後を載せない |
| 画面の問題の件数 | 既存の `watchPage` の `problems`・`cspViolations` の件数 | 件数だけ（文は載せない） |
| 今の画面の道 | `page.url()` のパスだけ（例 `/admin/users`） | 問い合わせと `#` の後を載せない。決まった道の型（`/`・`/admin/users` など）に当たらなければ「その他」とする |
| 一覧の行の数 | 表の行の数 | 数だけ |

- 記録は、テストの終わり（`test.afterEach`）で `testInfo.status` が期待と違うときだけ添付します。成功したテストには残しません。
- 記録の中身も報告の部品の探す先に入るため、手伝いの誤りで値が混ざれば部品が見つけます。
- Playwright が失敗のときに書く `error-context.md`（誤りの文と画面の ARIA の写し）は止めません（Q3 A）。110 の失敗では写しに U の氏名・メールアドレスが入るため、報告の部品が見つけて失敗になります。このときは、写しを読んで原因を確かめた後に `frontend/test-results/` を消します（`README.md` に手順を書く、コード生成）。流れの失敗で実行はもともと失敗しているため、合否は変わりません。
- それでも原因が分からないときは、3.7 の手順で手元だけ trace を有効にして流し直します。

### 3.7 手元だけ trace を有効にする手順（Q2 A）

1. `E2E_TRACE=retain-on-failure ./gradlew e2eTest`（調べたいファイルだけを流してよい）。
2. `npx playwright show-trace` で `test-results/` の下の `trace.zip` を見る。
3. この実行は、報告の部品が trace の中の値を見つけて失敗になりうるため、合否に使わない。統合の前の確かめは、`E2E_TRACE` を付けない実行で行う。
4. 見た後に `frontend/test-results/` を消す。

手順は `README.md` の E2E の節に書きます（コード生成）。

### 3.8 確かめ方（コード生成 B5・Build and Test）

- わざと値を入れた報告で部品が失敗することを、種類ごとに確かめて記録します。(1) json の報告の注記に U の氏名、(2) `test-results/` の下の zip（圧縮あり）の中のファイルに U のパスワードの URL の形、(3) `error-context.md` に U のメールアドレス、(4) 値のファイルを書かずに値の形だけで見つかること、(5) 壊れた zip で失敗すること、(6) 残した `playwright-report/` で失敗すること。確かめに使った報告は消します。
- 値のファイルが探し終えた後に無いこと、作ったときの権限が 600 であることを確かめます。
- Build and Test で `./gradlew e2eTest` を流した後に、部品の結果（確かめた種類の数、見つかった件数 0）を記録します。

## 4. 120 の差し替えの口（NFR3.5、要点 11）

### 4.1 口の形

新しい手伝いのファイル1つに、`page.route` の口を1つだけ置き、`/api/admin/` で始まる道の要求をすべて受けます。

```text
// 差し替えの口の考え方（説明用）
await page.route(url => url.pathname.startsWith('/api/admin/'), async route => {
  const req = route.request(); const key = routeKey(req.method(), req.url())   // 道の ID は {id}
  if (mocks.has(key)) return route.fulfill(mocks.get(key))                    // (1) 見本で返す
  if (req.method() === 'GET' && mode.passThroughGet) return route.continue()  // (2) 本物へ通す GET
  if (req.method() === 'GET') return route.fulfill(notMocked())               // 見本の無い GET（検査のモード）
  blocked.push({ method: req.method(), path: templatePath(req.url()) })       // (3) 記録して
  return route.abort()                                                        //     打ち切る
})
```

| モード | 一覧の GET | 操作・保存（POST・PUT） | 見本の無い GET 以外 |
|---|---|---|---|
| 検査（20 組×11 状態） | 状態ごとの見本 | 状態ごとの見本（成功・409・400） | 打ち切って記録 |
| 測り・前半（NFR5.1） | 本物へ通す | 使わない | 打ち切って記録 |
| 測り・後半（NFR5.2） | 2 ページ分の見本 | 使わない | 打ち切って記録 |

- 検査のモードで見本の無い GET は、本物へ通さず、決まった失敗（通信の失敗の見本）で返します。状態ごとに読む GET を見本で決め、内部DB の状態で検査の結果が揺れないためです。GET は状態を変えないため、記録の対象（0 件を確かめる対象）にはしません。
- モードはテストの中で切り替えます（測りの前半から後半へ）。見本の表は 5節の1つの置き場から引きます。

### 4.2 記録と確かめ

- 記録は `{ method, path }` だけです。道の中の利用者 ID は `{id}` に置き換え、問い合わせの部分は記録しません。
- 各テストの終わりに、記録が 0 件であることを確かめます。失敗の知らせには、メソッドと道の型だけを出します。
- 口は `page.goto` より前に張ります（既存の `watchPage` と同じ順）。ログイン（`/api/auth/` の下）は口の外で、本物へ通ります。
- Playwright の `request` の口（APIRequestContext）の要求は `page.route` に乗りません。そのため 120 は `/api/admin/` の下の書き換えを `request` の口から送りません。読むだけの要求も 120 では送りません（見本と画面の状態だけで足りるため）。
- 既存の `support/pageProblems.ts` の `requests` は URL だけでメソッドが分からず、差し替えた要求も数えるため、この確かめに使いません。既存の `support/` のファイルは変えません。

### 4.3 確かめ方

コード生成で、わざと POST を送る小さな確かめ（画面の操作の見本を一時的に外す形）を一度流し、記録されて失敗になることを確かめて記録します。確かめの後は元に戻し、コミットに含めません。

## 5. 見本と本物の照らし合わせ（NFR9.9、要点 12）

- 120 の検査と NFR5.2 の測りで返す見本（一覧のページ、409、400）を、新しい手伝いのファイル1つにまとめ、画面の側の型（`useradmin/api/types.ts` の `AdminUserPage`・`AdminUser`）を付けます。E2E から画面の型を読むことは、型の検査（`tsc`）だけの依存で、実行時に画面のコードを読みません。
- 見本のメールアドレスは `example.com` の値を使い、110 が作る値の形（`u7-perf-` で始まる宛先、`e2e-u7-pw-` で始まるパスワード、氏名「計測 花子」）を使いません。値の形での探し方（3.5）と重ならないためです。見本の氏名は2語を含め、実在しそうな氏名を置きません（TM の Testing Posture）。
- 110 の中で、次を毎回確かめます。照らし合わせの失敗の知らせには項目の名前だけを出し、値を出しません。
  - 一覧: 本物の応答の項目の名前と型が、見本と一致する。
  - 409: U に失敗回数を戻した後でもう一度「失敗回数を戻す」の API を呼び、本物の 409（`USER_ADMIN_NO_CHANGE`、U の状態は変わらない）の項目の名前と型が見本と一致する。見本の `code` が C3 の5つの code のどれかである。
  - 400: 上限を超える検索の文字で一覧の API を呼び、本物の 400 の `code`（`VALIDATION_FAILED`）と項目の名前と型が見本と一致する。
- 110 の照らし合わせの要求は、110 が自分で作った U だけに向け、初期管理者の状態を変えません。

## 6. 画面の外に出さない・文言・応答の項目（NFR3.1〜NFR3.3、要点 7）

### 6.1 画面の外に出さない（NFR3.1）

- 今のページと今の検索の文字は `useUserAdmin` の状態だけに持ち、URL（パス・問い合わせ・`#` の後）・ブラウザの保存（localStorage・sessionStorage）・履歴（`history.pushState`）に載せません。画面を開く・再読み込みするたびに1ページ目・検索なしから始めます。
- 画面のコードはコンソールの関数を呼びません。失敗は画面の状態として扱い、ログにしません。
- 行の「操作」の読み上げの名前に氏名とメールアドレスを入れるのは画面の中の表示で、ログではありません。
- 確かめ: `UserAdminPage.test.tsx` で、一覧・検索・ページ送り・5つの操作・氏名と言語の保存の流れ（成功・業務の失敗・通信の失敗を含む）の後に、保存の領域のどの鍵の値にもテストの値が無いこと、`location` と `history.length` が変わらないこと、`console` の5つの関数が呼ばれないことを見ます。

### 6.2 文言（NFR3.2）

- 失敗の文言は `useradmin/failureMessage.ts` が応答の `code` と状態コードから文言の鍵を選びます。サーバーの `detail`・`title` を読みません。知らない code・code の無い応答・通信の失敗は一般の文言にします。
- 応答の値と文言に差し込む値は React の文字として描き、HTML として解釈しません。検索の 400 は入力欄の下に上限を知らせ、入れた値を文言に差し込みません。
- 確かめ: `failureMessage.test.ts` と、`detail`・`title` に目印の文字を入れた応答、氏名に `<`・`>`・`&`・`"`・`'` を含む見本の行を使う画面のテスト。

### 6.3 応答の項目（NFR3.3）

- `useradmin/api/userAdminApi.ts` は、応答を C3 の項目（行の11項目と `self`、ページの `page`・`size`・`total`）だけを取り出した新しい値に写して返します。応答のオブジェクトをそのまま画面へ渡しません。応答に `passwordHash`・`failedAttempts`・`refreshToken` などが加わっても、写した値に入りません。
- 必要な項目が無い・型が違う本文は、通信の失敗として扱います。
- 確かめ: `api/userAdminApi.test.ts`（余計な項目を加えた応答、形の違う本文）と `UserTable.test.tsx`（余計な項目の値が画面に出ない）。

## 7. 静的検査と CSP（NFR9.1）

- 既存の oxlint のセキュリティ系の決まり（`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url` など）・ESLint・型検査（`any` の禁止を含む）をそのまま通します。決まりを緩める・除外を足すことはしません。
- 外部への通信・外部の資源・埋め込みのスクリプトとスタイルを足しません。見た目は機能の CSS で付け、要素の `style` 属性で差し込みません。
- CSP（`backend/src/main/resources/application.yaml` の `content-security-policy`）を変えません。実際のブラウザの検査（120）のためにも緩めません。120 で CSP の違反がコンソールに出ないことを、既存の `watchPage` の `cspViolations` で記録します。

## 8. 依存と供給の守り（NFR9.2〜NFR9.4）

- **新しい依存を足さない（NFR9.2）**: axe-core・fast-check・Playwright は既存のものを使います。報告の部品の zip の読み込みは `node:zlib`・`node:fs` だけで書きます。`frontend/package.json` の差分は無く、lockfile の差分は make-you-chic-ui の版の分だけです。
- **make-you-chic-ui の固定先（NFR9.3）**: `077f5b4` から `3481488` 以降へ上げます。具体のコミットは B5 のコード生成の計画で決め、計画の承認の前に部品の口（FC 2.4・2.5）があることを確かめます。承認を得た専用のコミットで、更新前後のハッシュを記録します。`vendor/make-you-chic-ui` の中身は変えず、lockfile は OSV-Scanner の対象のままです。
- **インストールのスクリプトを動かさない（NFR9.4）**: `frontend/.npmrc` に `ignore-scripts=true` を足します（`engine-strict=true` は残す）。効くのは `frontend/` の中のインストールだけです。固定先の更新と `.npmrc` の変更は別のコミットにし、それぞれの後に `npm ci` と `./gradlew e2eTest` を通します（失敗の原因を切り分けるため）。

## 9. プラットフォームの視点（配備先は開発者の PC 上のコンテナ）

- U5 は WAR に同梱する画面のファイルを足すだけで、コンテナ・`.env`・compose・ネットワークの設定に変えるものはありません。新しい秘密の値も足しません。
- E2E の報告と値のファイルは、開発者の PC の `frontend/test-results/` の下にだけ置かれます。`frontend/test-results/`・`frontend/playwright-report/` は `.gitignore` の対象のままで、コミット・共有しません（NS の NFR3.4 (e)）。E2E は CI の外のため、CI の成果物に報告は載りません。
- 値のファイルの権限 600 は、同じ PC のほかの利用者から読まれないための守りで、PC の利用者が1人の今の運用では補いの守りです。

## 10. セキュリティのテストの対応

| NFR | 確かめ | 段 |
|---|---|---|
| NFR1.1 | `rowActions.test.ts`（16 通りと性質ベース）、`UserRowActions.test.tsx`、`backend/` の差分なし。サーバーの判定は U3・U1 のテスト | コード生成 |
| NFR1.2 | `UserAdminPage.test.tsx`（403 で `useAdminForbidden` が呼ばれ何も出ない、401 で S3・S4 が閉じる） | コード生成 |
| NFR3.1 | `UserAdminPage.test.tsx`（保存・URL・履歴・コンソール、6.1） | コード生成 |
| NFR3.2 | `failureMessage.test.ts`・`UserAdminPage.test.tsx`・`EditProfileDialog.test.tsx`（6.2） | コード生成 |
| NFR3.3 | `api/userAdminApi.test.ts`・`UserTable.test.tsx`（6.3） | コード生成 |
| NFR3.4 | わざと値を入れた報告の確かめ（3.8）、`./gradlew e2eTest` の後の部品の結果 | コード生成（B5）・Build and Test |
| NFR3.5 | 120 の各テストの終わりの 0 件の確かめ、わざと POST を送る確かめ（4.3） | コード生成（B5）・Build and Test |
| NFR9.1 | `./gradlew verify` のリンタと型検査、`application.yaml` の差分なし、120 の `cspViolations` | コード生成・Build and Test |
| NFR9.2〜NFR9.4 | `package.json` の差分なし、固定先の更新の前後のハッシュ、2つのコミットごとの `npm ci`・`./gradlew e2eTest`、`./gradlew verify`（OSV-Scanner） | コード生成（B5）・Build and Test |
| NFR9.9 | 見本の型の検査（`tsc`）、110 の中の一覧・409・400 の照らし合わせ | コード生成（B5）・Build and Test |

## 11. 残る危険

| 危険 | 扱い |
|---|---|
| 画像の中に描かれた値（画面の写真）は、報告の部品が文字として探せない | 今の E2E の設定は画面の写真と動画を取らない（既定の off のまま）。U5 でも足さない。足すときは、この危険を見直す |
| Playwright を上げると、手順の題の形・`error-context.md` の中身・html の報告の埋め込みの形が変わりうる | 部品は報告の形に頼らずすべてのファイルを探すため、形が変わっても探し漏れは起きにくい。前の html の報告の base64 を取り出せなくなったときは失敗になる（3.5 の 7）。Playwright を上げる変更では、3.8 の確かめを流し直す |
| 値の形を変えた手伝い（`createRegisteredUser` の宛先やパスワードの形）で、形での探し方が外れる | 値のファイルの値でも探すため、110 の値は見つかる。手伝いの形を変えるときは、部品の形の表（3.5 の 3）を合わせて直す |
| 失敗した実行の報告に値が残る間がある | 部品が失敗にして知らせ、原因を確かめた後に消す（3.6）。報告はコミットしない |
| `E2E_TRACE` を付けた実行の trace に値が残る | その実行を合否に使わず、見た後に消す（3.7）。部品が最初に知らせる |

## 12. 上流との差

承認済みの文書は書き換えません（PM の Way of Working）。

| # | 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|---|
| 1 | html の報告を作らない（Q1 A） | NS の NFR3.4（対象の報告と仕組みの案）、守るものの表、脅威の表 | 対象の報告は json・html・trace の3つで、報告の部品が html の報告のファイルも探す | html の報告を作らないことで、html に値を残さない目的を満たした（3.2）。html を探す代わりに、作らない設定にし、前の実行の `playwright-report/` が残っていれば探して失敗にし、消す手順を示す（3.5 の 6）。理由は、Playwright 1.63 の html の報告が `fill` の手順の題に入れた値を書き、既存の `loginAsAdmin`・`loginWithForm` の資格情報が今も残るため。承認済みの NFR3.4 のとおり探す先に加えると、010〜100 を含むふつうの成功の実行でも部品が失敗になる。代わりに、手元で html の報告を開いて調べる手段がなくなる（3.6・3.7 で補う） |
| 2 | すべての E2E の trace の既定を `off` にする（Q2 A） | NS の NFR3.4 (c)・申し送り（080 の扱い）、機能設計 FC 9節（`frontend/e2e/` の 010〜100 は変えない） | (c) は 110 と 120 だけを `test.use` で trace を残さない形にする。080 の trace の扱いは計画で決めて依頼者に確かめる | 設定の既定を `off` にし、080 を含むすべての E2E で trace を残さない（3.2）。E2E のファイル（010〜100・130）は変えず、`frontend/playwright.config.ts` の1か所の変更とした。申し送りの 080 の扱いは、この段の Q2 で依頼者が決めた。調べるときは手元だけ `E2E_TRACE` で有効にし、その実行は合否に使わない（3.7） |
| 3 | 110・120 の `test.use` の trace を、既定 `off` の関数で置く | NS の NFR3.4 (c) | 110 と 120 は `test.use` で trace を残さない（`trace: 'off'`） | 既定の実行では `off` で (c) を満たす。Q3 A の「それでも足りなければ Q2 の手順で手元だけ trace を有効にして流し直す」を 110・120 でも行えるよう、`'off'` を固定で書かず、設定と同じ `E2E_TRACE` の読み方の関数 `e2eTraceMode()` を置いた（3.2）。固定の `'off'` との違いは、手元で `E2E_TRACE` を付けた実行だけに現れる。承認の場で確かめる |
| 4 | 探す先を `test-results/` の下のすべてのファイルに広げ、失敗の画面の写しを止めない（Q3 A） | NS の NFR3.4（探す先は json・html・`trace.zip`）、NFR 要件の再レビューの Minor（R-06 `error-context.md`、R-07 trace を切った後の診断） | `error-context.md` と診断の扱いは書いていない | `error-context.md` を含む `test-results/` の下のすべてのファイルを探す（3.5 の 4）。110・120 が失敗したときだけ、値を含まない記録を注記と添付 `user-admin-diagnostics` に残す（3.6）。写しで部品が失敗したときは、中身を確かめてから消す。R-06・R-07 への答えで、追加 |
| 5 | 値の形の表に `@` の URL の形と氏名の3つの形を足す | NS の NFR3.4 の仕組みの案 | 作る値の形（`u7-perf-` の宛先、`e2e-u7-pw-` のパスワード、氏名「計測 花子」）でも探す | 形での探し方にも URL の形と `\u` の形を当てた（3.5 の 3）。要点 9 のとおりで、追加 |
| 6 | 検査のモードで見本の無い GET を本物へ通さない | NS の NFR3.5 | 本物へ通してよいのは `/api/admin/` の下では GET だけ | 検査のモードでは見本の無い GET も決まった失敗で返し、本物へ通さない（4.1）。NFR3.5 より狭い（守りを強める）形で、要件の「GET 以外は 0 件」の確かめは変えない。測りのモードの一覧の GET は本物へ通す |
| 7 | 設定のファイルの説明と `README.md` の手順を直す | 既存の `frontend/playwright.config.ts` の冒頭の説明・`README.md` | 「結果は list・html に加えて json」 | html を外し trace の既定を変えるため、説明と手順（3.6・3.7）をコード生成で直す。設計の文書の差ではなく、作る側の申し送り |
