# Security Design — U5 招待の管理の画面（u5-invitation-ui）

U5 のセキュリティの設計です。U5 は画面の単位で、サーバー側の認可を持ちません。この文書では、招待のトークン・URL を扱わないこと、メールアドレスを画面の外に出さないこと、失敗の文言と応答の読み方、認可の画面の側の扱い、CSP、依存と make-you-chic-ui、実際のブラウザの検査の要求の差し替えの安全を決めます。答えは `nfr-design-questions.md` にあります（Q1: A、Q2: A、Q3: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号と、成果物ごとの置き場は `performance-design.md` の冒頭のとおりです。

## 前提

- トークンと利用者の情報はメモリだけに持ちます（既存の `frontend/src/features/auth/authSession.ts`）。アクセストークンの付与と 401 での更新は既存の ApiClient が受け持ち、この単位では変えません。
- 一覧・招待・送り直しの応答に、招待のトークンと招待の URL はありません（U3 の BR5.4、契約 C5）。
- サーバー側の認可（未認証 401・管理者でない 403・管理者の成功）は U3 のサーバー側のテストで確かめます。成功の状態コードは API ごとに契約 C5 のとおりで、一覧 200・招待 201・送り直し 200・取り消し 204 です（NFR 要件の承認の場の R-01）。
- GitHub のリポジトリは公開のため、テストのデータのメールアドレスは予約済みのドメイン（`example.test`・`example.com`）の下の値だけを使います。

## 1. 招待のトークン・URL（NFR1.1、D11）

### 1.1 作り

- 応答の型（`frontend/src/features/invitation/api/types.ts`）は、契約 C5 の `Invitation` の決まった項目（`invitationId`・`email`・`language`・`invitedBy`・`invitedAt`・`expiresAt`・`sendResult`・`expired`）と、`InvitationPage` の決まった項目だけを持ちます。
- 表示は型の項目だけから作ります。列の `render` は決まった項目を1つずつ読み、応答の行を丸ごと広げて渡す書き方（スプレッド）や、項目の名前を順にたどって描く書き方をしません。そのため、応答に `token`・`url` などの項目が加わっても表示に出ません。
- トークン・URL を画面の状態・文言の鍵・差し込みの値に持ちません。

### 1.2 確かめ

- `InvitationList` の画面部品のテストで、行に `token`・`url` の項目（目印の文字）を加えた応答を渡し、画面の文字と属性のどこにもその値が出ないことを見ます。

## 2. メールアドレスと氏名を画面の外に出さない（NFR2.1、D1・D11）

### 2.1 作り

| 出し先 | 作り |
|---|---|
| ブラウザの保存 | U5 のコードは localStorage・sessionStorage を読み書きしない。表示の設定は U4 の口（`useDisplaySettings`）から読むだけで、`setPreview`・`setLanguage` などを呼ばない |
| URL | 今のページは `useInvitationAdmin` の状態だけに持ち、パス・問い合わせ・`#` の後に載せない。ページ送りで `navigate`・`history` を呼ばない |
| コンソール | U5 のコードで `console` を呼ばない。失敗は画面の知らせだけで示す。今のリンタの設定（`frontend/.oxlintrc.json`）には `console` を止める決まりが無いため、テストの見張り（2.2）で確かめる。リンタの決まりは足さない（ほかの機能に及ぶため） |
| 要求の本文 | 招待の本文は `email`・`language` の2つだけ。送り直し・取り消しは本文を持たず、`invitationId` をパスに入れる（数として扱い `encodeURIComponent` を通す） |

### 2.2 確かめ

`InvitationAdminPage` の画面部品のテストで、一覧の表示・招待・送り直し・取り消しの流れ（成功・失敗の応答・通信の失敗を含む）の後に、次を見ます。

- localStorage と sessionStorage のすべての鍵の値に、テストのメールアドレス・氏名が無い
- ページ送りの後も `location` のパス・問い合わせ・`#` の後が変わらない
- `console` の5つの関数（`log`・`info`・`warn`・`error`・`debug`）を見張り、どの流れでも呼ばれない（呼ばれたときは、引数にメールアドレス・氏名が無いことも見て、失敗の説明に出す）

## 3. 失敗の文言と応答の読み方（NFR9.1、D4・6節）

### 3.1 文言の選び方

- `failureMessage.ts` が、失敗（ApiClient の `ApiError`）の `code` と状態コードの種類から文言の鍵を選びます。サーバーの `detail`・`title` は使いません。
- 画面が文言を持たない `code`・`code` の無い応答・通信の失敗は、4xx・5xx・通信の失敗ごとの一般の文言にします。
- 応答の値と差し込みの値（`{{ }}`）は React の文字として描き、HTML として解釈しません。HTML を直接埋め込む書き方は、既存のリンタの決まり（`react/no-danger`）で止めます。

### 3.2 400 の `fieldErrors`

- 400 `VALIDATION_FAILED` は、機能設計の 6.2 のとおり、メールアドレスの項目の下に決まった文言（`invitation.error.VALIDATION_FAILED`）を出し、項目にフォーカスします。
- U3 の NFR 設計が招待の 400 に載せる `fieldErrors: [{field, reason}]`（U2 の形）は読みません。言語は選択肢から選ぶため誤りにならず、誤りはメールアドレスの形式の1つに決まるためです。`fieldErrors` の追加は安全な追加で、U5 はその形に頼りません。
- ApiClient は Problem Details の本文を追加の項目ごと `ApiError.problem` に持つ既存のままで、変えません。

### 3.3 成功の本文の読み方と 204

| 関数 | 成功 | 本文の読み方 |
|---|---|---|
| `listInvitations` | 200 | JSON として読む。読めなければ通信の失敗として扱う（一般の文言） |
| `createInvitation` | 201 | 同上 |
| `resendInvitation` | 200 | 同上 |
| `cancelInvitation` | 204 | 本文を読まない。`Response.ok` だけで成功とする |

- 「成功の本文を JSON として読み、読めなければ通信の失敗」の扱いは、上の3つだけに当てます。取り消しの 204 の空の本文を通信の失敗と取り違えないためです（承認の場の R-01 を画面の側でも当てる）。
- 確かめは `invitationApi.ts` のテストで、204 の空の本文を成功として扱うこと、200・201 の読めない本文を通信の失敗として扱うことを見ます。

### 3.4 失敗の追加の項目

- `readPendingProblem` は、409 `INVITATION_ALREADY_PENDING` の `problem` の `invitationId`（整数）と `page`（1 以上の整数）の型を確かめてから返し、合わなければ使いません（案内を出さない側に倒す）。
- `readUnavailableReasons` は、503 `INVITATION_NOT_CONFIGURED` の `unavailableReasons` のうち、知っている値（`SMTP_NOT_CONFIGURED`・`BASE_URL_NOT_CONFIGURED`）だけを返します。
- 応答の知らない項目は無視します。`sendResult` の知らない値は「送信に失敗」の側に倒します。

### 3.5 確かめ

- `InvitationAdminPage`・`InviteDialog` の画面部品のテストで、`detail` に目印の文字を入れた応答（知っている `code`・知らない `code`・`code` なし）で、その文字が画面に出ないことを見ます。
- メールアドレスに `<`・`>`・`&` を含む値の行で、タグとして描かれず文字として出ることを見ます。

## 4. 認可の画面の側の扱い（NFR9.2、D14）

- 画面は骨組みの既存の `access: 'ADMIN'` と AccessControl のまま、サイドバーの項目は `visibleWhen: 'ADMIN'` です（機能の登録）。骨組みは変えません。
- 401 は既存の ApiClient の更新とログインの画面への移動に任せ、画面の文言を出しません。403 は一般の 4xx の文言で示します。
- 画面でメニューやボタンを隠すことは、サーバー側の管理者の判定の代わりにしません。サーバー側の 401・403・成功（200・201・204）は U3 のサーバー側のテストで確かめます。
- 確かめは、`InvitationAdminPage` の画面部品のテストで 403 の応答の表示を見ることと、`registration.ts` のテストで登録の値を見ることです。

## 5. CSP と埋め込み（NFR9.3、W7）

- 外部への通信・外部の資源を足しません。`backend/src/main/resources/application.yaml` の CSP は変えません。
- 埋め込みのスクリプト・スタイルを足しません。目立たせた行の見た目は機能の CSS（`:has()`）で付け、要素の `style` 属性で差し込みません（`logical-components.md` の6.2）。
- 確かめは次のとおりです。
  - コード生成で、`application.yaml` の CSP に差分が無いこと、ビルドした `index.html` に埋め込みのスクリプト・スタイルが無いことを確かめる。
  - 実際のブラウザの検査で、U5 の画面でコンソールに CSP の違反が出ないことを、U4 の手伝いの CSP の違反の集めで記録する（U4 の `logical-components.md` の5.1）。

## 6. 実際のブラウザの検査の要求の差し替えの安全（Q1: A）

- 差し替え（`page.route`）は、検査のそのコンテキストのページの中だけで効きます。サーバーの設定・内部DB・アプリのコード・CSP は変えません（U4 の `logical-components.md` の5.1、U6 の NFR 要件の Q2: B と同じ）。
- 差し替えるのは一覧の API（`GET /api/admin/invitations`）の答えだけです。行ありは 20 行・全件数 21 以上、警告は `invitationEnabled: false` と理由2つの見本です。Modal は開くだけで、招待・取り消しの要求は送りません。見本は `api/types.ts` の型で組み立て、型の食い違いを型検査で止めます。
- 見本のメールアドレスは `example.com` の下の固定の値、招待した管理者の氏名は架空の値です。実在の個人に関する値を使いません。
- 見本は本物の応答の形を通りません。本物の形は、U3 の契約 C5 の結合テスト・`performance-design.md` の4節の測定（本物の一覧を読む）・E2E-1 で通ります。
- 招待の用意（`performance-design.md` の4.2）のアクセストークンは、そのテストの変数だけに持ち、注記・添付に出しません。E2E の管理者のパスワードは既存の `frontend/playwright.config.ts` の作り方（実行ごとに作る）のままで、初期管理者の設定は変えません。失敗のときに残るトレースと報告は手元だけに置き、`.gitignore` の対象のままです。

## 7. 依存と make-you-chic-ui（NFR9.4・NFR9.5）

- U5 は新しい依存（実行時・開発時とも）を足しません。axe-core は U4 が B4 で devDependencies に明示で足したものを使います。コード生成で、U5 の変更による `frontend/package.json`・`frontend/package-lock.json` の差分が無いことを確かめて記録します。
- make-you-chic-ui の新しい Table・Modal・Button・RadioGroup は、B4 の固定先の更新（edb1f94 → 735ef04）の後の版を使います。B5 のコード生成の計画で、B4 の固定先の更新が済んでいることを前提として確かめます。
- `vendor/make-you-chic-ui` の中身は変えません（`project.md` の Forbidden）。足りない口は Table の外側で足します（`logical-components.md` の6.2）。

## 8. 脅威と扱い

| 脅威 | 扱い | 節 |
|---|---|---|
| 招待のトークン・URL が画面に出て、画面の共有や写しから第三者が登録を完了する | 型の決まった項目だけから描き、応答に項目が加わっても出さない | 1節 |
| 共用の PC で、招待のメールアドレスがブラウザの保存・URL に残る | 保存を使わず、今のページを画面の中だけに持つ | 2節 |
| メールアドレスがコンソールの出力から漏れる | `console` を呼ばず、テストで5つの関数を見張る | 2節 |
| サーバーの `detail` に内部の情報や利用者の値が混じり画面に出る | 文言は `code` から選び、`detail` を使わない | 3.1 |
| メールアドレスの記号による画面への差し込み（XSS） | React の文字として描き、HTML を直接埋め込まない | 3.1 |
| 取り消しの成功を失敗と取り違え、利用者が同じ操作を繰り返す | 204 は本文を読まずに成功とする | 3.3 |
| 画面でボタンを隠すだけで、管理者でない人が API を呼べる | サーバー側で判定し、U3 のテストで 401・403・成功を確かめる | 4節 |
| CSP の緩み・外部の資源の読み込み | CSP を変えず、埋め込みを足さない。検査でも緩めない | 5節 |
| 検査の差し替えや準備がサーバーの状態・秘密情報を漏らす | 差し替えはコンテキストの中だけ。トークンは注記に出さず、見本は架空の値 | 6節 |
| 依存の脆弱性・悪意のあるパッケージ | 新しい依存を足さない | 7節 |

## 9. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 認可の成功の状態コードを 200・201・204 と書く | NFR 要件の `security-requirements.md` の前提・NFR9.2 | 「401・403・200」 | 承認の場の R-01（Minor、Accepted risk）のとおり、取り消しの 204 を含む形で書いた。NFR 要件の文書は書き換えない |
| 204 の本文を読まない | 機能設計の `frontend-components.md` の5節 | 「成功の本文は JSON として読み、読めなければ通信の失敗として扱う」、表では取り消しは「204（値なし）」 | JSON として読む扱いを一覧・招待・送り直しに限り、取り消しは本文を読まないと明記した。5節の表の 204 と合わせた読み方で、食い違いではない |
| 招待の 400 の `fieldErrors` を読まない | U3 の NFR 設計の `security-design.md` の6節 | 招待の入力の誤りにも `fieldErrors` を載せる（U5 は形に頼らない） | U5 は読まない（機能設計の 6.2 のとおり）。食い違いではない |
| 一覧の答えを検査で差し替える（Q1: A） | NFR 要件の NFR7.3 | 行を置くための招待と、警告に要る招待を使えない状態は、検査の中で自分で用意する | 検査の中で `page.route` の見本で用意する形に決めた。前のテストの状態に頼らない点は NFR7.3 のとおり。本物の形の確かめは6節のとおりほかの場で行う |
