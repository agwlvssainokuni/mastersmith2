# Security Requirements — U4 表示の設定の土台（u4-display-foundation）

U4 のセキュリティの要件です。U4 は画面の単位で、サーバー側の認可を持ちません。ここでは、ブラウザに残す値、登録の完了からの受け渡し、トークンを付けない公開の API、要求のヘッダー、見た目の設定の応答の値、CSP を扱います。答えは `nfr-requirements-questions.md`（Consolidated Summary Confirmation: Looks correct）です。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`
- D・W・4.1・9節は、この単位の `construction/u4-display-foundation/functional-design/functional-spec.md`
- 「部品 7節」は、同じフォルダの `frontend-components.md` の 7節
- 「要点 n」は、この段の `nfr-requirements-questions.md` の「NFR の要点（案）」の番号

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。新しい依存とそのライセンスは `tech-stack-decisions.md` の NFR9.5〜NFR9.7 に置きます。

要件に当たる ID が無い画面の側のセキュリティの要件（NFR9.1〜NFR9.4）は、NFR9 の枝番に寄せました。どれもテストで確かめ方を決める要件だからです。同じ Intent の U8 が、当たる ID の無い要件を NFR9 に寄せた前例と同じ扱いです。

## 前提

- 目標は、前の Intent から続く決まりです。
  - トークンと利用者の情報はメモリだけに持ちます（`frontend/src/features/auth/authSession.ts`）。
  - 秘密情報をログ・エラー応答・ブラウザの保存に出しません（`project.md` の Forbidden）。
- サーバー側の認可（公開の範囲、未認証・管理者でない・管理者の 401・403・200）は、U3・U8 のサーバー側のテストで確かめます。画面の側の扱いは、その代わりにしません（要件 NFR4、`team.md` の Testing Posture）。
- GitHub のリポジトリは公開のため、テストのデータに実在の個人に関する値を使いません（`team.md` の Way of Working）。

## 1. 個人に関する値とブラウザの保存

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR2.1 | 登録の完了からログインの画面へは、メールアドレスだけを、画面の中のメモリだけで1回だけ渡す。URL（パス・問い合わせ・`#` の後）とブラウザの保存（localStorage・sessionStorage）には載せない。パスワードと招待のトークンは渡さない。受け取った値は、描画の確定の後に受け渡しの口から消す | 画面部品のテスト（部品 7節の `LoginForm`）。受け渡しの後の URL と localStorage・sessionStorage にメールアドレスが無いこと、2回目の表示と読み込み直しでは案内が出ないことを見る | 要点 6、D13・W9、AC3.2.17 |
| NFR2.2 | ブラウザの保存（localStorage）に置くのは次の2つだけにする。U4 の鍵 `mastersmith.display-settings` の3つの値（`language`・`theme`・`fontSize`）と、make-you-chic-ui の鍵（`design-system-*`）の見た目の値。どの鍵にも、トークン（アクセス・リフレッシュ）・メールアドレス・氏名・見せ方（未保存）の値を置かない。読み込む値は項目ごとに許される値だけを受け入れ、ほかは無いものとする。読み書きの例外は外へ出さない | 画面部品のテスト（部品 7節のブラウザの保存と `DisplaySettingsProvider`）で次を見る。ログインの成功・プリファレンスの保存・登録の完了の保存の後に、U4 の鍵の中身が3つの値だけであること。localStorage のどの鍵の値にも、テストのトークン・メールアドレス・氏名が含まれないこと。読み込む値の検証は性質ベースのテストでも見る（NFR9.9） | 要点 5、4.1・D6・D7、`project.md` の Forbidden（トークン） |

## 2. 公開の API・要求のヘッダー・応答の値

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.1 | 公開の API の3つのパス（`/api/appearance`・`/api/registration/verify`・`/api/registration/complete`）にはアクセストークンを付けない。401 を受けても、トークンの更新と送り直しをしない。パスの判定は、問い合わせの部分を除いた完全な一致で行い、似た別のパス（例: `/api/registration/other`）を公開として扱わない。ほかのパスのトークンの付け方と 401 の更新の流れは変えない | ApiClient の単体テスト（部品 7節の ApiClient）。アクセストークンを持つ状態で3つのパスを呼んで `Authorization` が付かないこと、401 でも更新の手段が呼ばれないこと、問い合わせの付いたパスも同じに判定されること、似た別のパスにはトークンが付くことを見る | 要点 7、D10、契約 C6・C7 |
| NFR9.2 | `Accept-Language` に付けるのは、画面の言語の許される値（`ja`・`en`）だけにする。利用者の入力をそのままヘッダーに入れない。呼び出し側が明示した `Accept-Language` は上書きしない。言語の関数が登録されていないときは付けない | ApiClient の単体テストで、許される値だけが付くこと・呼び出し側の指定が残ることを見る。画面の言語を決める関数が常に `ja`・`en` のどちらかを返すことは、性質ベースのテストで見る（NFR9.9） | 要点 8、D9・W12、ADR-005 |
| NFR9.3 | 見た目の設定の応答は、項目ごとに契約 C7 の許される値（ブランドカラーは `blue`・`green`・`purple`・`orange`、フォントファミリーは `sans`・`serif`）だけを make-you-chic-ui に渡す。ほかの値は当てない。応答の値を HTML や属性にそのまま差し込まない（make-you-chic-ui の `setBrand`・`setFontFamily` だけで当てる） | 画面部品のテスト（部品 7節の `DisplaySettingsProvider`）。許されない値・余計な項目・形の誤りの応答で `<html>` の `data-brand`・`data-font-family` が変わらないことを見る。HTML を直接埋め込む書き方は既存のリンタの決まり（`react/no-danger`）で止める | 要点 8、W3、契約 C7 |
| NFR9.4 | 外部のフォント・外部への通信を足さない。CSP（`default-src 'self'`・`script-src 'self'`・`style-src 'self'`・`font-src 'self'`・`connect-src 'self'` ほか、`backend/src/main/resources/application.yaml`）は変えない。フォントは自前で配信する。埋め込みのスクリプト・スタイルを足さない（`frontend/vite.config.ts` の `modulePreload.polyfill: false`・`assetsInlineLimit: 0` のまま）。アクセシビリティの検査（NFR7.3）のためにアプリの CSP を緩めない（検査の側の都合は、テストのブラウザのコンテキストだけで扱う） | コード生成で次を確かめる。`application.yaml` の CSP に差分が無いこと。ビルドした `index.html` に埋め込みのスクリプト・スタイルが無いこと。フォントの URL が同じオリジンの `/assets/` であること。Build and Test の E2E で、ブラウザのコンソールに CSP の違反が出ないことを記録する | 要点 9、9.1 |

## 3. 脅威と扱い

| 脅威 | 扱い | 要件 |
|---|---|---|
| 共用の PC で、前の利用者の情報がブラウザに残る | ブラウザに残すのは表示の設定だけで、トークン・メールアドレス・氏名を残さない。ログインの後は前の利用者の表示の設定を使わない（D2） | NFR2.2 |
| 招待のメールアドレスが URL に載り、履歴・リファラー・アクセスログに残る | 受け渡しはメモリだけで、URL に載せない | NFR2.1 |
| 期限切れのアクセストークンが公開の API で 401 を起こし、登録の完了が妨げられる | 公開の API にトークンを付けず、401 で更新しない | NFR9.1 |
| 似た名前のパスを公開と誤って判定し、トークンなしで要求する | 完全な一致で判定する | NFR9.1 |
| ヘッダーへの差し込み（`Accept-Language` に改行や任意の値が入る） | 許される値だけを付ける | NFR9.2 |
| 見た目の設定の応答の改ざん・想定外の値による表示の乗っ取り | 許される値だけを当て、HTML に差し込まない | NFR9.3 |
| 外部のフォントの配信元へのアクセスの情報の漏れ・CSP の緩み | 自前で配信し、CSP を変えない | NFR9.4 |
| 依存の脆弱性・悪意のあるパッケージ | 版を lockfile で固定し、依存関係の脆弱性検査の対象に入れる | NFR9.5・NFR9.6・NFR9.7（`tech-stack-decisions.md`） |

## 4. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| NFR4（認可）を U4 では N/A とする | 要件の NFR4 | 認可はサーバー側のテストで 401・403・200 を確かめる | U4 はサーバー側の認可を持たない。画面の側の扱い（トークンを付けない）は NFR9.1 に置き、サーバー側のテストの代わりにしない。食い違いではない |
| localStorage の中身の確かめを「どの鍵にも秘密・個人に関する値が無い」まで広げる | 機能設計の 4.1 | U4 の鍵の「持たないもの」を決めている | make-you-chic-ui の鍵も含めて確かめる形に広げた（NFR2.2）。追加 |
| 画面の側のセキュリティの要件を NFR9 の枝番に寄せる | 要件の NFR1〜NFR11 | 画面の側の公開の API・ヘッダー・CSP に当たる ID が無い | NFR9.1〜NFR9.4 に寄せた（同じ Intent の U8 の前例と同じ）。traceability.json の NFR9 の target に書く |
| 検査のために CSP を緩めない | 機能設計の 9.1 | CSP は変えない | アクセシビリティの検査（NFR7.3）でも、アプリの CSP を変えないことを明記した（NFR9.4）。追加 |
