# Security Design — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

U7 のセキュリティの設計です。U7 は画面の単位で、サーバー側の認可・入力の検証・今のパスワードの照合・監査を持ちません（どれも U2）。この文書では、画面の側で守ることの作りを決めます。

答えは `nfr-design-questions.md` にあります（Q1: A、Q2: A、Q3: A、Consolidated Summary Confirmation: Looks correct）。出典の略号と、成果物ごとの置き場の表は `performance-design.md` の冒頭のとおりです。

この文書の節は次のとおりです。

| 節 | 置く設計 |
|---|---|
| 1節 | 個人に関する値（NFR2.1） |
| 2節 | パスワードの値（NFR9.1） |
| 3節 | 応答の値の扱いと、項目ごとの誤りの読み取り・理由の対応（NFR9.2。多言語の NFR8.2 の文言の表の作りを含む） |
| 4節 | 今のパスワードの誤りとログイン状態、401（NFR9.3） |
| 5節 | 骨組みの変更（NFR9.4） |
| 6節 | 実際のブラウザの検査と測りの安全（Q1: A・Q2: A） |
| 7節 | 依存（NFR9.10） |
| 8節 | 上流との差 |

前提:

- サーバー側の認可（`/api/me/` はログインした利用者だけ。未認証 401、ログインした利用者の 200・204）と入力の検証・今のパスワードの照合・監査は、U2 のサーバー側のテストで確かめます（U2 の NFR 設計の `security-design.md` の2節）。画面の側の確かめと画面を隠すことは、その代わりにしません（要件 NFR4、`team.md` の Testing Posture）。
- アクセストークンと利用者の情報は、既存のとおりメモリだけに持ちます（`frontend/src/features/auth/authSession.ts`）。U7 はトークンに触れません。
- GitHub のリポジトリは公開のため、テストのデータに実在の個人に関する値やパスワードを使いません。

## 1. 個人に関する値（NFR2.1）

| 項目 | 作り |
|---|---|
| 氏名の持ち方 | 画面に出す氏名（初期値はメールアドレス、AC4.1.10）は、`usePreferencesForm` の状態（今の設定とフォームの値）だけに持つ |
| ブラウザの保存 | U7 は localStorage・sessionStorage・Cookie・IndexedDB に触れない。ブラウザの保存は U4 の `applyUserPreferences` だけが行い、書くのは U4 の鍵の3つの表示の設定（言語・テーマ・文字の大きさ）だけ（U4 の NFR 設計の `security-design.md` の1節）。氏名は `applyUserPreferences` に渡すが、U4 はブラウザに保存しない |
| URL | 画面の URL は `/me/preferences`・`/me/password` の固定のパスだけで、問い合わせ・フラグメントに値を載せない |
| `console` | U7 のコードは `console` を呼ばない。失敗の応答の値も出さない |

- 確かめ（画面部品のテスト）: 読み込みの成功・保存の成功・保存の失敗の後に、localStorage と sessionStorage のすべての鍵の値に、テストの氏名（メールアドレスの形の初期値を含む）が無いこと、`console` の5つの関数（`log`・`info`・`warn`・`error`・`debug`）が呼ばれないことを見る。

## 2. パスワードの値（NFR9.1）

| 項目 | 作り |
|---|---|
| 持ち方 | 3つの値（今・新しい・確かめ）は `usePasswordChangeForm` の状態（メモリ）だけに持つ。ブラウザの保存・Cookie・URL に置かない |
| 項目 | 3つとも `type="password"`。`autocomplete` は今のパスワードが `current-password`、新しい2つが `new-password`（CR6.9） |
| 送り方 | フォームに `method`・`action` を置かず、送信の出来事で既定の動きを止めて ApiClient で `POST /api/me/password` を送る。ブラウザが値を URL の問い合わせに載せて送る道を作らない |
| 成功の後 | 204 で3つを空の文字列に戻す（W10 の1） |
| 失敗の後 | 値は消さない（D9、AC5.1.7）。値はメモリの状態のまま |
| 離れたとき | 部品が外れると状態は捨てられる。送信中の答えは捨てる（W10 の5） |
| 画面の確かめ | 共用の関数（U6）は値を受けて理由を返すだけで、値を保存・出力しない |

- 確かめ（画面部品のテスト）: 送信の前・成功の後・失敗の後に、localStorage と sessionStorage のどの鍵にもパスワードの値が無いこと、`location` の `search`・`hash` に値が無いこと、成功で3つの項目が空になることを見る。

## 3. 応答の値の扱いと項目ごとの誤り（NFR9.2・NFR8.2、要点 6）

### 3.1 応答の値の扱い

- 失敗の文言は、サーバーの `detail` を使わず、`code` と項目ごとの誤りの `field`・`reason` から、この機能の文言の鍵（`preferences.` で始まる）を選ぶ（D13）。
- 応答の値は React の文字としてだけ描き、HTML として差し込まない。既存のリンタの `react/no-danger` のまま。
- 成功の応答（`Preferences`）は、`preferencesApi.ts` で C4 の形（`displayName` が文字列、`language`・`theme`・`fontSize` が決めた値）を確かめてから使う。形が外れていれば失敗として扱う（6.1・6.2 の「200・形の誤り」）。決めた値の外の文字列を U4 の口に渡さない。

### 3.2 項目ごとの誤りの読み取り（`fieldErrors.ts`）

U2 の NFR 設計の `security-design.md` の3節で、400 `VALIDATION_FAILED` の追加の項目の形が `fieldErrors: [{ field, reason }]` に決まりました。`fieldErrors.ts` は、この形だけを知る1か所にします。ApiClient は変えません（`ApiError.problem` が Problem Details の本文を追加の項目ごと持つ、U4 の NFR 設計）。

| 入力 | 扱い |
|---|---|
| `problem` が無い・オブジェクトでない、`fieldErrors` が無い・配列でない | 空の一覧を返す |
| 要素がオブジェクトでない、`field` か `reason` が文字列でない | その要素を捨てる |
| `field` が画面ごとの項目名の union に無い | 捨てる（プリファレンスは `displayName`・`language`・`theme`・`fontSize`、パスワードの変更は `currentPassword`・`newPassword`・`newPasswordConfirmation`） |
| 同じ `field` が重なる | 最初の1つだけを使う |
| `reason` | 文字列のまま返し、ここでは解釈しない（3.3 で対応づける） |

- 例外を出さない。性質ベースのテスト（fast-check）で、どんな JSON の値を渡しても例外を出さず、知っている項目の名前だけを返すことを確かめる（`logical-components.md` の7節）。
- 読み取りの結果に、応答のほかの値（`detail`・`traceId` など）を写さない。

### 3.3 理由の対応と文言の表（`errorMessages.ts`）

画面の確かめ（U6 の共用の関数、U6 の機能設計の6節）と、サーバーの項目ごとの誤りを、同じ理由の union に寄せてから、1つの表で文言の鍵にします。画面とサーバーの決まりが同じ理由なら、同じ文言で出ます。

| サーバーの `reason`（U2） | 寄せる理由 | 共用の関数の出どころ |
|---|---|---|
| `REQUIRED` | `required` | `validateDisplayName`・`validateNewPassword`・`validatePasswordConfirmation`。今のパスワードの空は U7 の確かめ |
| `TOO_SHORT` | `tooShort` | `validateNewPassword` |
| `TOO_LONG` | `tooLong` | `validateDisplayName`・`validateNewPassword` |
| `INVALID_CHARACTER` | `invalidCharacter` | `validateDisplayName` |
| `MISMATCH` | `mismatch` | `validatePasswordConfirmation` |
| `INVALID_VALUE` | `invalidValue`（U7 だけの理由。画面では起きない） | — |
| 知らない値 | `unknown` | — |

| 項目 | 理由 → 文言の鍵 |
|---|---|
| `displayName` | `required` → `preferences.displayName.required`、`tooLong` → `preferences.displayName.tooLong`、`invalidCharacter` → `preferences.displayName.invalidCharacter` |
| `language`・`theme`・`fontSize` | `invalidValue` と、ほかのどの理由も → `preferences.choice.invalid` |
| `currentPassword` | `required` → `preferences.password.currentRequired` |
| `newPassword` | `required` → `preferences.password.newRequired`、`tooShort` → `preferences.password.tooShort`、`tooLong` → `preferences.password.tooLong` |
| `newPasswordConfirmation` | `required` → `preferences.password.confirmRequired`、`mismatch` → `preferences.password.mismatch` |
| 表に無い組と `unknown` | 文字の項目は `preferences.field.invalid`、選択のまとまりは `preferences.choice.invalid` |

- 今のパスワードの誤り（400 `PASSWORD_CURRENT_MISMATCH`）は `code` で振り分け、`preferences.password.currentMismatch` を今のパスワードの項目に結び付ける（4節）。
- 対応づけられる誤りが1つも無いときは、画面の知らせ `preferences.form.invalid`（「入力を確かめてください。」）を出す（W12 の4）。
- フックは誤りを文言の鍵で持ち、描画のたびに今の言語で引く（W13 の2）。表のすべての鍵が ja・en の両方にあることは `errorMessages.ts` のテストで確かめる（NFR8.2、`logical-components.md` の6節）。
- 共用の関数の名前と理由の union は U6 の機能設計の6節を前提にします。B5 のコード生成の計画の承認の前に、U6 の関数の形とこの表が合うことを突き合わせます（R-02、`logical-components.md` の7節）。

## 4. 今のパスワードの誤りとログイン状態、401（NFR9.3）

| 応答 | 作り |
|---|---|
| 400 `PASSWORD_CURRENT_MISMATCH` | 400 のため ApiClient の 401 の更新と送り直しの流れに乗らない。画面はログイン状態を変えず、ログインの画面へ移らず、今のパスワードの項目に誤りを結び付けてフォーカスを移す（W10 の2）。U2 の条件つきの更新（照合の後にほかの変更が確定した、U2 の `reliability-design.md` の2節）でも同じ code が返り、画面は同じ文言で出す |
| 回数の制限 | 画面の側でも置かない（U2 の決定のまま） |
| 401 | アクセストークンの期限切れと、U2 の「本人の行が無い」（U2 の `security-design.md` の2節）の2つで返る。どちらも既存の ApiClient が更新と送り直しを1回だけ行い、更新もできなければ未ログインになってログインの画面へ移る。画面の側で 401 を別に扱わない |
| 画面の確かめ | サーバーの検証の代わりにしない。サーバーが返す 400 `VALIDATION_FAILED` を受ける作り（3.2・3.3）を残す（D8） |

- 確かめ（画面部品のテスト、部品 7節の「今のパスワードの誤り」）: トークンの更新が呼ばれず、ログイン状態が変わらず、ログインの画面へ移らないことを見る。サーバー側の照合と 401・204 は U2 のサーバー側のテスト。

## 5. 骨組みの変更（NFR9.4、要点 8）

| 項目 | 作り |
|---|---|
| 型 | `UserMenuItemRegistration` を、`action` だけを持つ形と `path` だけを持つ形の union にする（機能設計の 9.1） |
| 登録の検査 | `validateRegistrations` で、(1) `action` と `path` のどちらも無い・両方ある、(2) `path` が登録済みの画面の URL（ホームを含む）と完全に一致しない、を誤りにし、既存と同じくすべての問題を集めて起動を止める（9.2） |
| 外の URL | `path` は登録済みの画面の URL の完全な一致だけを許すため、絶対 URL（`http:`・`https:`・`javascript:` など）と `//` で始まる URL は必ず (2) で止まる。外へ移る道はできない |
| 移り方 | `ShellLayout` は `path` の項目を Dropdown の `href` と `onClick`（`event.preventDefault()` の後に `navigate(path)`）にする。サイドバーの項目と同じ作り。make-you-chic-ui の新しい版（`735ef04`）の Dropdown は、`href` の項目を `<a href role="menuitem">` で描き、Enter と Space のどちらでも `onClick` を呼ぶため、キーボードでも読み込み直しなしで移る |
| 既存の項目 | `action` の項目（ログアウト）は今までどおり `<button role="menuitem">` で、既存の E2E の `getByRole('menuitem', { name: 'ログアウト' })` はそのまま通る |

- 確かめ: 既存の `validateRegistrations.test.ts`・`ShellLayout.test.tsx`・`navigationItems.test.ts` に、`path` の項目・誤りの2つ（絶対 URL と `//` の URL を含む）・ログアウトが変わらず動くこと・`path` の項目を選ぶと読み込み直しなしで移ること（マウスとキーボード）を足す。統合の前に既存の E2E を手元で流す（NFR9.8）。U7 の実際のブラウザの検査は、パスワードの変更の画面へユーザーメニューから移る（`logical-components.md` の 5.3）。

## 6. 実際のブラウザの検査と測りの安全（Q1: A・Q2: A）

| 項目 | 作り |
|---|---|
| 要求の差し替え | `GET /api/me/preferences` と `/api/appearance` の差し替え（`page.route`）は、検査のブラウザのコンテキストの中だけで効く。アプリのコード・CSP・サーバーの設定は変えない（U4 の NFR 設計と同じ）。差し替えの答えの氏名は固定のテストの値（例: `検査 太郎`）で、実在の個人に関する値を使わない |
| 差し替えない道 | ログイン・ユーザーメニューの移り・画面の確かめ・`applyUserPreferences` は本物の道を通す。検査の中で PUT・POST を送らない（Q3: A の誤りの状態は画面の確かめで出し、要求を送らない） |
| 初期管理者 | 検査は初期管理者（`frontend/playwright.config.ts` の `adminEmail` と実行ごとに作るパスワード）でログインするが、設定もパスワードも変えない。ログインの成功の監査は E2E の使い捨ての内部DB に残るだけ |
| 測りの利用者 | 測りのテストが招待から作る（`performance-design.md` の 4.2）。パスワードは実行ごとに作る値で、リポジトリに置かない |
| 秘密の値 | 初期管理者のパスワード・作った利用者のパスワード・アクセストークン・招待のトークン・招待の URL は、そのテストの変数だけに持ち、注記・添付・`console`・テストの名前に出さない。失敗のときに残るトレースと報告は手元だけに置き、`.gitignore` の対象のまま |
| メール | 招待のメールは E2E が起動した WAR に渡した手元の受け手だけに送る。実在の宛先・外部の SMTP へ送らない。メールアドレスは `example.com` の下（`team.md` の Deployment、`project.md` の Forbidden） |
| CSP | 検査のファイルでは U4 の手伝いで CSP の違反を集め、0 件を確かめる。axe-core は Node の側で読み `page.evaluate` で評価する（`bypassCSP` を使わない、U4 の NFR 設計） |

## 7. 依存（NFR9.10）

- U7 は新しい依存（実行時・開発時とも）を足さない。axe-core は U4 が B4 で明示で足したものを使う。
- make-you-chic-ui の新しい版（RadioGroup の `legend`・選択肢の `lang`、Dropdown の `MenuItem` の `href`、Button の `loading`）は、B4 の固定先の更新（`edb1f94` → `735ef04`）の後に使う。B5 のコード生成の計画で、固定先が `735ef04` であることを前提として確かめる。make-you-chic-ui は変更しない（`project.md` の Forbidden）。
- コード生成で、`frontend/package.json` と lockfile に U7 のための差分が無いことを確かめる。

## 8. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 項目ごとの誤りの形をこの段で当てて読み取りを設計した | 機能設計の 10節の (c)・W12 の6 | 形は U2 のコード生成で決まり、U7 のコード生成はその形を確かめてから読み取りを書く | U2 の NFR 設計の3節で形が決まったため、この段で読み取り（3.2）と理由の対応（3.3）を決めた。U2 のコード生成で形が変わったときは、`fieldErrors.ts` と 3.3 の表だけを直す。契約 C4 への反映は U2 の持ち物（U2 の S-D1） |
| サーバーの理由を共用の関数の理由の union に寄せる | 機能設計の 10節の (d) | 理由の名前（空・長すぎ・使えない文字・短すぎ・一致しない）は仮の名前 | U6 の機能設計の6節の union（`required`・`tooShort`・`tooLong`・`invalidCharacter`・`mismatch`）に寄せ、U7 だけの `invalidValue`・`unknown` を足した（3.3）。突き合わせは B5 のコード生成の計画の承認の前（R-02） |
| 今のパスワードの誤りの文言を、条件つきの更新の場合にも使う | 機能設計の W10 の2 | 今のパスワードが正しくないときの文言 | U2 の条件つきの更新（U2 の R-D1）でも同じ code が返るため、同じ文言で出す（4節）。画面の作りは変わらない |
| 外の URL へ移る道を作らないことを、絶対 URL と `//` の URL の登録の検査のテストで確かめる | NFR 要件の NFR9.4 | 登録されていない `path` は起動を止める | 確かめの具体（5節）を足した。食い違いではない |
| 検査は初期管理者でログインし、設定を変えない | NFR 要件の NFR7.3・NFR9.9 | 前提を自分で作る | ログインの後の検査の前提（Q2: A）。初期管理者は設定ファイルが作る利用者で、前のテストの状態に頼らない。設定を変えない方針は B5 のコード生成の計画で U5・U6 とそろえる（`logical-components.md` の 5.1） |
