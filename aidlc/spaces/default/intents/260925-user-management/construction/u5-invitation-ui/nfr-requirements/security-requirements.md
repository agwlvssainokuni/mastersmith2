# Security Requirements — U5 招待の管理の画面（u5-invitation-ui）

U5 のセキュリティの要件です。U5 は画面の単位で、サーバー側の認可を持ちません。ここでは、招待のトークン・URL を扱わないこと、メールアドレスを画面の外（ブラウザの保存・URL・コンソール）に出さないこと、失敗の文言、認可の画面の側の扱い、CSP を扱います。答えは `nfr-requirements-questions.md`（Consolidated Summary Confirmation: Looks correct）です。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`
- D・W・6節・7節は、この単位の `construction/u5-invitation-ui/functional-design/functional-spec.md`
- 「部品 7節」は、同じフォルダの `frontend-components.md` の 7節
- 「要点 n」は、この段の `nfr-requirements-questions.md` の「NFR の要点（案）」の番号
- U3 の BRx.y は `construction/u3-invitation/functional-design/rules.md`、U4 の NFRx.y は `construction/u4-display-foundation/nfr-requirements/`

枝番はこの単位の中で振ります。ほかの要件の置き場は `performance-requirements.md` の冒頭の表のとおりです。依存と make-you-chic-ui の扱いは `tech-stack-decisions.md` の NFR9.4・NFR9.5 に置きます。

要件に当たる ID が無い画面の側のセキュリティの要件（NFR9.1〜NFR9.3）は、NFR9 の枝番に寄せました。どれもテストで確かめ方を決める要件だからです。同じ Intent の U4・U8 の前例と同じ扱いです。

## 前提

- トークンと利用者の情報はメモリだけに持ちます（既存の `frontend/src/features/auth/authSession.ts`）。アクセストークンの付与と 401 での更新は既存の ApiClient が受け持ち、この単位では扱いません（機能設計の 2節）。
- 招待・一覧・送り直し・取り消しの API のサーバー側の認可（未認証・管理者でない・管理者の 401・403・200）は、U3 のサーバー側のテストで確かめます。画面の側の扱いは、その代わりにしません（要件 NFR4、`team.md` の Testing Posture）。
- 一覧・招待・送り直しの応答に、招待のトークンと招待の URL はありません（U3 の BR5.4、契約 C5）。
- GitHub のリポジトリは公開のため、テストのデータに実在の個人に関する値を使いません。メールアドレスは予約済みのドメイン（`example.test`・`example.com`）の下の値を使います（`team.md` の Way of Working）。

## 1. 招待のトークン・メールアドレス

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR1.1 | 画面は招待のトークンと招待の URL を扱わない。一覧・Modal・知らせ・Toast に表示せず、画面の状態にも持たない。応答にもしそれらの項目が加わっても、表示に使わない（画面が表示に使う項目は契約 C5 の `Invitation` の決まった項目だけ） | 画面部品のテスト（部品 7節の `InvitationList`）で、余計な項目（例: `token`・`url`）を加えた応答でも表示にその値が出ないことを見る | 要点 5、D11、U3 の BR5.4、`project.md` の Forbidden（招待のトークン） |
| NFR2.1 | 画面に出す・知らせる個人に関する値は、招待のメールアドレスと招待した管理者の氏名だけにする。応答の値（メールアドレス・氏名）を、ブラウザの保存（localStorage・sessionStorage）・URL（パス・問い合わせ・`#` の後）・ブラウザのコンソールに出さない。今のページは画面の中の状態だけに持ち、URL に載せない | 画面部品のテスト（部品 7節の `InvitationAdminPage`）で次を見る。一覧の表示・招待・送り直し・取り消しの流れの後に、localStorage と sessionStorage のどの鍵の値にもテストのメールアドレス・氏名が無いこと。ページ送りの後も URL（`location` のパス・問い合わせ・`#` の後）が変わらないこと。`console` の各関数（`log`・`info`・`warn`・`error`・`debug`）を見張り、どの流れ（成功・失敗の応答・通信の失敗を含む）でも呼ばれないこと（呼ばれたときは引数にメールアドレス・氏名が無いことも見る） | 要点 5、D1・D11、要件 NFR2、`project.md` の Forbidden（メールアドレス） |

## 2. 失敗の文言・認可・CSP

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.1 | 失敗の文言は、サーバーの `detail` を使わず、応答の `code` から画面の文言の鍵を選ぶ。画面が文言を持たない `code`・`code` の無い応答・通信の失敗は、状態コードの種類（4xx・5xx・通信の失敗）ごとの一般の文言にする。応答の値と文言の差し込みの値（`{{ }}`）は React の文字として描き、HTML として解釈しない。HTML を直接埋め込む書き方をしない | 画面部品のテスト（部品 7節の `InvitationAdminPage`・`InviteDialog`）で、`detail` に目印の文字を入れた応答（知っている `code`・知らない `code`・`code` なし）で、その文字が画面に出ないことを見る。メールアドレスに `<`・`>`・`&` を含む値で、タグとして描かれず文字として出ることを見る。HTML の直接の埋め込みは既存のリンタの決まり（`react/no-danger`）で止める | 要点 6、D4、7節、要件 NFR1・NFR2 |
| NFR9.2 | 画面でメニューやボタンを隠すことは、サーバー側の管理者の判定の代わりにしない。画面は骨組みの既存の `access: 'ADMIN'` と AccessControl のまま（変えない）。401 は既存の ApiClient の更新とログインの画面への移動に任せて画面の文言を出さず、403 は一般の 4xx の文言で示す | サーバー側の 401・403・200 は U3 のサーバー側のテスト。画面の側は、画面部品のテスト（部品 7節の `InvitationAdminPage`）で 403 の応答の表示を見る。機能の登録（`visibleWhen: 'ADMIN'`・`access: 'ADMIN'`）は登録のテストで見る | 要点 7、D14、6節、要件 NFR4、`team.md` |
| NFR9.3 | 外部への通信・外部の資源を足さない。CSP（`backend/src/main/resources/application.yaml`）は変えない。埋め込みのスクリプト・スタイルを足さない。目立たせた行の見た目は機能の CSS（`:has()`）で付け、要素の `style` 属性で差し込まない。アクセシビリティの検査（`tech-stack-decisions.md` の NFR7.3・NFR7.4）のためにアプリの CSP を緩めない | コード生成で、`application.yaml` の CSP に差分が無いこと、ビルドした `index.html` に埋め込みのスクリプト・スタイルが無いことを確かめる。Build and Test の実際のブラウザの検査で、U5 の画面でブラウザのコンソールに CSP の違反が出ないことを記録する | 要点 8、W7、U4 の NFR9.4 |

## 3. 脅威と扱い

| 脅威 | 扱い | 要件 |
|---|---|---|
| 招待のトークン・URL が画面に出て、画面の共有や画面の写しから第三者が登録を完了する | 画面はトークン・URL を扱わず、応答に項目が加わっても表示に使わない | NFR1.1 |
| 共用の PC で、招待のメールアドレスがブラウザに残る | ブラウザの保存と URL に載せない。今のページも画面の中だけに持つ | NFR2.1 |
| 招待のメールアドレスがコンソールの出力から漏れる（画面の写し・共有・拡張機能） | 応答の値をコンソールに出さない | NFR2.1 |
| サーバーの説明文（`detail`）に内部の情報や利用者の値が混じり、画面に出る | 文言は `code` から選び、`detail` を使わない | NFR9.1 |
| メールアドレスに入れた記号による画面への差し込み（XSS） | React の文字として描き、HTML を直接埋め込まない | NFR9.1 |
| 画面でボタンを隠すだけで、管理者でない人が API を呼べる | サーバー側で判定し、U3 のサーバー側のテストで 401・403・200 を確かめる | NFR9.2 |
| CSP の緩み・外部の資源の読み込み | CSP を変えず、埋め込みのスクリプト・スタイルを足さない | NFR9.3 |
| 依存の脆弱性・悪意のあるパッケージ | 新しい依存を足さず、make-you-chic-ui は固定先の更新で取り込み lockfile を検査の対象に入れる | NFR9.4・NFR9.5（`tech-stack-decisions.md`） |

## 4. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| NFR4（認可）を U5 では N/A とする | 要件の NFR4 | 認可はサーバー側のテストで 401・403・200 を確かめる | U5 はサーバー側の認可を持たない。画面の側の扱いは NFR9.2 に置き、サーバー側のテストの代わりにしない。U4 と同じ扱いで、食い違いではない |
| 画面の外に出さない確かめを、ブラウザの保存・URL に加えてコンソールまで広げる | 機能設計の D11 | 「応答の値をブラウザのコンソールや保存に出さない」 | D11 の決まりを確かめるテストの形を決めた（NFR2.1）。追加 |
| 応答に余計な項目が加わっても表示に使わない | 機能設計の D11、`frontend-components.md` の 7節 | 「トークン・URL を表示しない」 | 応答の変化に対しても守ることを確かめる形にした（NFR1.1）。追加 |
| 画面の側のセキュリティの要件を NFR9 の枝番に寄せる | 要件の NFR1〜NFR11 | 画面の側の失敗の文言・認可の扱い・CSP に当たる ID が無い | NFR9.1〜NFR9.3 に寄せた（U4・U8 の前例と同じ）。traceability.json の NFR9 の target に書く |
