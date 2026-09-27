# NFR Design — Questions（U6 登録の完了の画面 / u6-registration-ui）

U6 は、招待のリンク（`/register#token=…`）を開いた人が、リンクを確かめてから氏名・パスワード・表示の設定を入れて登録を完了する画面の単位です。種類は ui のため、作る成果物は performance-design・security-design・logical-components・traceability の4つです（拡張性・信頼性・観測性は service の単位だけ）。

非機能の設計のほとんどは、承認済みの NFR 要件・機能設計・契約 C6・C9 と、U4・U3・U2 の NFR 設計（READY）で決まっています。そのため、まず NFR 設計の要点（案）を示します。上流から作りが1つに決まらない2点だけを質問にします。

1. アクセシビリティの検査の差し替えの答えと、本物の確かめの API の応答の形を、どう照合するか（NFR 要件の承認の場の R-02）
2. 登録の完了の 400 `VALIDATION_FAILED` に U3 が `fieldErrors` を載せることになったのを受けて、画面の出し方を機能設計の Q3 A から変えるか

読んだ上流:

- 承認済みの NFR 要件 `aidlc/spaces/default/intents/260925-user-management/construction/u6-registration-ui/nfr-requirements/`
  - `performance-requirements.md`: NFR6.1〜NFR6.3
  - `security-requirements.md`: NFR1.1〜NFR1.5、NFR2.1、NFR3.1、NFR9.1〜NFR9.5
  - `tech-stack-decisions.md`: NFR9.6・NFR9.7、NFR7.1〜NFR7.5（3.1 の記録を含む）、NFR8.1・NFR8.2、NFR9.8〜NFR9.11
  - `nfr-requirements-questions.md` の Q1: B・Q2: B
- NFR 要件の承認の場の決定（Minor 2 件は Accepted risk で、コード生成の計画で拾う）
  - R-01: 検査の回数（2 状態×20 組＝40 回）と、U4・U5・U7 の分を含めた `./gradlew e2eTest` 全体の時間の見積もりを添える。検査が長くなることは依頼者が受け入れた
  - R-02: 差し替えの答えの形（NFR7.5）と、本物の確かめの API の応答の形（InvitationView）が一致することを、コード生成かレビューの時点で1回照合する手順を、U3・U6 のコード生成の計画に書く
- 承認済みの機能設計 `construction/u6-registration-ui/functional-design/`
  - `functional-spec.md`: D1〜D12、W1〜W13、3節（状態）、5節（応答ごとの動き）、8節（秘密と個人情報）、10節（上流との差。フラグメントは React Router の置き換えの移動で消す）
  - `frontend-components.md`: 2節（置き場）、3節（C6 の呼び出しと応答の形の確かめ）、4節（`useRegistration`）、8節（テスト）
  - `functional-design-questions.md` の Q3 A（400 は項目に結び付けず、フォームの上の一般の知らせ）
- 依存する単位の NFR 設計（READY）
  - `construction/u4-display-foundation/nfr-design/`: 実際のブラウザの検査の作り（`logical-components.md` の5節。組ごとに新しいコンテキスト、テーマ・文字の大きさは U4 の鍵を初めのスクリプトで置く、ブランドカラーは `/api/appearance` の差し替え、`document.documentElement.scrollWidth` で横のはみ出し、B5 の画面はその検査の中で前提を自分で作る）、axe-core は Node の側で読み `page.evaluate` で評価（`security-design.md` の6節）、合否は WCAG 2.0・2.1 の A・AA のタグの規則（U4 の Q2 A）、最初の画面の時間の測り方（`performance-design.md`、テストの側の時計・5回・注記と添付）、トークンを付けないパスの一覧（U4 の Q3 A、`TOKENLESS_API_PATHS`・`isTokenlessApiPath` の例）
  - `construction/u3-invitation/nfr-design/security-design.md`: 6節（登録の完了の 400 に `fieldErrors: [{field, reason}]` を C6 の項目名で載せる。`token` は項目ごとの誤りにせず 404）、7節（拒否は理由によらず1つの経路で同じ応答）、上流との差の SD-D3（C6 への反映は U3 のコード生成の計画まで）
  - `construction/u2-user-preferences/nfr-design/security-design.md` の3節: `fieldErrors` の形と `reason` の値（REQUIRED・TOO_SHORT・TOO_LONG・INVALID_CHARACTER・INVALID_VALUE・MISMATCH）。知らない値は一般の文言で出す
- 契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md` の C6（InvitationView は `email`・`language` だけ、`language` は `ja`・`en`）・C9、画面 `inception/refined-mockups/interaction-spec.md`
- 決まり `aidlc/spaces/default/memory/team.md`・`project.md`（E2E の本数と置き場、Playwright の検査は流れの本数に数えない、公開のリポジトリ、トークン・メールアドレスの Forbidden）
- 既存のコード
  - `frontend/playwright.config.ts`: `workers: 1`・`fullyParallel: false`、`Desktop Chrome`、ロケール `ja-JP`、実行ごとの一時の内部DB、`trace: 'retain-on-failure'`
  - `frontend/tsconfig.json`: `include` に `src` と `e2e` の両方が入っており、`e2e` の中から `src` の型を読み込める
  - `frontend/e2e/010〜040`: 各テストは既定の新しいコンテキストで動く
  - `frontend/src/main.tsx`: `StrictMode` の中で描く

## NFR 設計の要点（案）

1. **部品の構成（logical-components）**
   - 画面の置き場は、機能設計の `frontend-components.md` の2節のとおりとする。
     - `frontend/src/features/registration/`（新しい。`registration.ts`・`registrationApi.ts`・`registrationToken.ts`・`failureKind.ts`・`formProblems.ts`・`useRegistration.ts`・5つの画面の部品）
     - `frontend/src/shared/validation/`（新しい。U7 と共用。`app/` と `features/` を読み込まない）
   - 依存の向きは `features/registration` → U4 の口（`app/display-settings`・`app/login-handoff`）・`app/i18n`・`app/login-state`・`shared/api-client`・`shared/validation`・make-you-chic-ui・`features/auth/authSession`（`logout` だけ）。ほかの機能から `features/registration` への依存は無い。
   - 実際のブラウザで動くものは `frontend/e2e/` に置き、画面の成果物（`dist`）に入らない。
     - E2E-1（招待から登録の完了までの流れ、NFR9.10）: 新しいファイル1本
     - アクセシビリティの検査（NFR7.3・NFR7.5）: E2E-1 と別のファイル。U4 の検査の手伝い（組の切り替え・axe の実行・はみ出しの判定）を使う。U4 の `050-` のファイルに足すか、画面の単位ごとのファイルにするかと番号は、B5 のコード生成の計画で U5・U7 とそろえて決める
     - 差し替えの答えの見本（Q1 で決める）: 検査と E2E-1 から読む手伝いのモジュール
   - 失敗の範囲は画面の中に閉じる。
     - 確かめ・完了の失敗: 5節の表のとおり `loadFailed`・`unavailable`・`ready` の知らせに振り分ける。画面は壊れない
     - 確かめの応答の形の誤り（`language` が `ja`・`en` でない、`email` が文字列でない）: `loadFailed` と同じ扱い（`frontend-components.md` の3節）
     - 見た目の設定の応答が返らない: U4 の W2 の5（全画面の最初の描画が止まる。受け入れ済み）
2. **性能（リンクを開いてからフォームが出るまで、NFR6.1）**
   - 測り方は U4 の `performance-design.md` と同じ形にする。E2E-1 の中で、登録を完了する前に、取り出した同じリンクを5回開く。
     1. `browser.newContext()` で、キャッシュが空の新しいコンテキストを作る
     2. `page.goto(リンク)` の直前から、メールアドレスの欄が見えるまでの時間を、テストの側の時計で測る
     3. コンテキストを閉じる
   - テストの側の時計は Playwright の待ちの間隔を含み、実際より長めに出る。長めに出る側で判定する。
   - 5回の値は、テストの注記（`test.info().annotations`）と添付（JSON）で残し、Build and Test の結果に写す。時間で失敗させない（統合の関門にしない）。
   - 同じ5回で、次のことも確かめる（失敗の条件にする。時間と違って不安定ではないため。U4 の NFR6.4・NFR9.4 と同じ扱い）。
     - フォームが出た後の `page.url()` に `#token=` が無いこと（NFR1.3）
     - ブラウザのコンソールに CSP の違反が出ないこと（NFR9.5）
   - 5回とも確かめ（`/verify`）だけで状態が変わらない（U3 の BR7.1）。完了は計測の後に1回だけ行う。
3. **性能（待ちの表示と画面の塊、NFR6.2・NFR6.3）**
   - 確かめ中は `role="status"`、送信中は Button の `loading`（`aria-busy`）。どちらの待ちにも画面の側で上限・再試行・中断を置かない（既存の ApiClient のまま）。
   - 画面は機能の登録の遅延読み込み（`registration.ts`）で別の塊にする。B5 のコード生成で、入口のファイルと別の塊に出ることと、その塊の大きさ（圧縮前と gzip）を記録する。初回の JavaScript の目安（gzip で 500KB、警告だけ）は既存の `check-bundle-size.mjs` のまま。
4. **性能（アクセシビリティの検査の回数と時間の見積もり、R-01）**
   - U6 の検査は 2 状態（`ready`・`unavailable`）×20 組（(a) 6組・(b) 8組・(c) 6組）＝ 40 回。組ごとに新しいコンテキストで画面を開き、axe を1回流す（U4 の作り）。
   - 見込みは、1組あたり 1〜3 秒（コンテキストの作成・画面の読み込み・見た目の設定とセッションの復元の待ち・axe の実行）で、U6 の分は約 1〜2 分。
   - U4 のログインの画面（20 組）、U5（3 画面の状態）・U7（2 画面）の分を合わせると、`./gradlew e2eTest` の検査の部分は、見込みで約 160 組・約 3〜8 分になる。どちらも実測していない見込みの値で、B5 のコード生成と Build and Test で実測して記録する。
   - 時間の上限は置かない。検査が長くなることは承認の場で受け入れ済み。組を減らす・1つのコンテキストを使い回す変更はしない（U4 の非干渉の作りを崩すため）。
5. **セキュリティ（招待のトークンの画面の側の扱い、NFR1.1〜NFR1.4）**
   - 取り出し: 最初の描画で `window.location.hash` を `registrationToken`（純粋な関数）に渡し、`token` の値だけを取る。無い・空なら API を呼ばずに `unavailable`。形は画面で確かめない（D1）。
   - 消去: 描画の確定の後の副作用で1回だけ、React Router の置き換えの移動（`replace`）で同じパス（問い合わせを含む）へ移り、フラグメントを消す（機能設計 10節）。開発時の `StrictMode` で副作用が2回走っても、2回目はフラグメントが無いため何もしない。
   - 保持: トークンはフックの状態（メモリ）だけに持つ。ブラウザの保存・URL・画面の文言・`console` に出さない。読み込み直すと無くなり `unavailable` になる。
   - 送信: 要求の本文（`{ token }`・CompleteRequest の `token`）にだけ入れる。`registrationApi` の呼び出しは ApiClient を通し、`Authorization`・`Accept-Language` を付けない（公開のパスの扱いは U4 の ApiClient の一覧、NFR9.1）。
   - U4 の最初の描画のゲート（見た目の設定とセッションの復元の答えを待つ）の間、フラグメントは消えずにアドレス欄に残る。U6 の部品が描かれた後に取り出して消すため、ゲートの待ちの間にほかの部品がアドレスを書き換えないことを前提にする（コード生成で、ゲートを通った後にトークンが取れることを画面部品のテストで確かめる）。
   - 確かめの作り（NFR1.1〜NFR1.3）は NFR 要件の表のとおり。
     - 画面部品のテスト: 描画の後の URL にフラグメントが無い、要求の本文にだけトークンがあり URL と見出しに無い、フラグメントが無い・空なら要求が無い、localStorage・sessionStorage のどの鍵にもトークンが無い、`console` の5つの関数（`log`・`info`・`warn`・`error`・`debug`）を見張り、開く・確かめの失敗・完了の失敗のどの流れでも呼ばれない
     - E2E-1: フォームが出た後の `page.url()` に `#token=` が無い（要点 2 の5回でも見る）
   - リファラー: 新しい手当ては足さない。フラグメントはリファラーに載らず、既存の `Referrer-Policy: same-origin` と CSP を変えない。画面に外部へのリンクと外部の読み込みを置かない（NFR1.4）。
   - 残る危険: ブラウザの閲覧の履歴（履歴の画面・同期された履歴）は、スクリプトが動く前の最初の移動のアドレス（フラグメントを含む）を記録しうる。置き換えの移動がその記録を消すかは、ブラウザに依存し確かめていない。トークンの1回だけ有効・有効期限・送り直しでの置き換え（U3、`project.md` の Mandated）で支える。
6. **セキュリティ（招待のメールアドレスと列挙の防止、NFR2.1・NFR3.1）**
   - メールアドレスは読み取り専用の欄と、U4 の受け渡し（`handOffToLogin`、メモリだけ・1回）だけに持つ。完了の 204 の後は `saveBrowserDisplaySettings` → `handOffToLogin(email)` → `/login` への置き換えの移動の順（D11）。パスワードとトークンは受け渡さない。
   - 確かめ・完了の 404 `REGISTRATION_LINK_INVALID` は理由によらず同じ表示にし、`detail` を読まない・出さない。振り分けは `failureKind`（状態コードと `code` だけを見る純粋な関数）の1か所で行う。U3 の7節で、サーバーの拒否の応答も理由によらず同じになる。
7. **セキュリティ（想定外の応答・パスワード・二重の送信・CSP、NFR9.2〜NFR9.5）**
   - 404 でも 400 でもない応答（例: 429・503）は、確かめでは `loadFailed`（もう一度読み込める）、完了では登録できない知らせ。U6 で回数の制限の手当ては足さない。
   - パスワードの2つの欄は `type="password"`・`autocomplete="new-password"`。送信の前に `shared/validation` でサーバーと同じ決まりを確かめるが、判定はサーバーが正。値は入力の欄と送信の本文だけに持つ。
   - 二重の送信は、Button の `loading` とフックの両方で防ぐ（送信の間の操作を無視し、欄を `readOnly`、ラジオの選択を受け付けない）。ログアウト中も同じ。
   - CSP は変えない。埋め込みのスクリプト・スタイルを足さず、応答の値を HTML として差し込まない（`react/no-danger`）。
8. **セキュリティと非干渉（アクセシビリティの検査のフォームの状態、NFR7.3・NFR7.5）**
   - 組ごとの新しいコンテキストの中で、`page.route` で `POST /api/registration/verify` だけを受けて 200 の見本の答え（`email` は `example.com` のアドレス、`language` は `ja`）を返す。ほかの要求（画面の配信・見た目の設定・セッションの復元）は本物の WAR へ送る。ブランドカラーの組は、U4 の作りのとおり同じコンテキストで `/api/appearance` の答えも差し替える。
   - 開くアドレスは `/register#token=<見本の値>`。見本のトークンは、どの招待にも当たらない、見て見本と分かる低いエントロピーの固定の値（例: `a11y-sample-token`）とし、秘密情報の検出（Gitleaks）に掛からない形にする（NFR1.5）。
   - 「リンクが使えない」の状態は、フラグメントの無い `/register` を差し替えなしで開く。
   - 検査はログインしない。サーバーの状態（内部DB・監査・招待）を変える要求を送らない（確かめは差し替えで WAR に届かず、`unavailable` は API を呼ばない）。
   - 差し替えの答えと本物の応答の形の照合は Q1 で決める。
9. **多言語（NFR8.1・NFR8.2）**
   - 文言の鍵（`registration.`）は ja・en でそろえ、画面部品のテストで両方にあり空でないことを見る。言語の選択肢は U4 の `LANGUAGE_NAMES`（訳さない）。
   - 招待の言語 en をブラウザの言語 ja で開いたときに、確かめの後の文言・`<html lang>`・画面の確かめの誤りが en になり、完了の要求の `Accept-Language` が en になることを画面部品のテストで見る。
10. **拡張性・信頼性・観測性（ui のため成果物は作らない）**
    - 状態はブラウザの1つのタブの中のフックだけにある。画面の側に独自の指標・ログの送り先を足さない。`console` に何も出さない（要点 5 の見張り）。
    - 3つの分類の要点は logical-components の失敗の範囲に書く。
11. **テストとカバレッジ・依存（NFR9.6〜NFR9.11）**
    - U6 は新しい依存を足さない。make-you-chic-ui の新しい版（RadioGroup の `legend`・`lang`、Button の `loading`）は B4 の固定先の更新（`edb1f94` → `735ef04`）の後に使う。
    - 画面部品ごとの vitest-axe、`shared/validation` の性質ベースのテスト（fast-check、失敗時の種を記録）、フロントエンドのカバレッジの下限（行 80%・分岐 70%）、除外を増やさない。
    - E2E-1 は流れの E2E の本数に数え（この Intent の1本）、アクセシビリティの検査は数えない。既存の 010〜040 は変えない。
    - 招待メールのリンクの取り出し方と、E2E の WAR への SMTP の設定の渡し方は infrastructure-design の持ち主のまま（この段では決めない）。E2E-1 は、その手段で取り出したリンクを使う前提で書く。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| リンクを開いてからフォームが出るまで 2 秒以内。E2E-1 の中で同じリンクを新しいコンテキストで5回開いて測り、記録する。統合の関門にしない | NFR6.1（Q1: B） |
| 待ちの表示（`role="status"`・`aria-busy`）、待ちに上限を置かない。画面は遅延読み込みの塊 | NFR6.2・NFR6.3 |
| トークンはフラグメントだけから取り、描画の確定の後に React Router の置き換えの移動で消し、メモリだけに持ち、本文にだけ入れる | D1〜D3、機能設計 10節、NFR1.1〜NFR1.3 |
| リファラーの手当ては足さず、`Referrer-Policy` と CSP に差分が無いことを確かめる | NFR1.4 |
| テストのトークンは使い捨てか見本の値。トレースと報告はコミットしない | NFR1.5 |
| メールアドレスは読み取り専用の欄と U4 の受け渡しだけ | NFR2.1、D11、U4 の NFR2.1 |
| 404 は理由によらず同じ表示で `detail` を出さない | NFR3.1、U3 の7節 |
| 公開の API のパスの扱いは U4 の ApiClient が持ち、U6 は ApiClient を変えない | NFR9.1、U4 の Q3 A |
| 想定外の応答は読み込み直せる失敗・登録できない失敗に振り分ける | NFR9.2 |
| パスワードの欄の属性、送信の前の画面の確かめ、判定はサーバーが正 | NFR9.3、D8・D9 |
| 二重の送信の防止は Button とフックの両方 | NFR9.4、W8 |
| CSP を変えず、HTML に差し込まない | NFR9.5 |
| アクセシビリティの検査は 2 状態×20 組。フォームの状態は確かめの API の答えの差し替え、「リンクが使えない」は本物のまま。流れの本数に数えない。検査が長くなることは受け入れ済み | NFR7.3・NFR7.5（Q2: B）、承認の場の決定 |
| 検査の作り（組ごとの新しいコンテキスト、組の切り替え、axe の読み込み方、合否の規則、はみ出しの判定） | U4 の NFR 設計（`logical-components.md` の5節、`security-design.md` の6節、Q2 A） |
| E2E-1 のリンクの取り出し方と SMTP の設定の渡し方は infrastructure-design の持ち主 | NFR9.10 |
| U6 は新しい依存を足さない。make-you-chic-ui の固定先の更新は B4 | NFR9.6・NFR9.7 |

次の2点は、候補の論点から質問にせず、要点に書いて要約で確かめます。

- 検査の回数と時間の見積もり（要点 4）: 回数は承認済みの組で決まっており、検査が長くなることも承認の場で受け入れ済みのため。見込みの値を書き、実測は B5 のコード生成と Build and Test で行う。
- トークンの画面の側の扱いの確かめの作り（要点 5）: 取り出し・消去・保持・送信のやり方は機能設計で、確かめ方は NFR 要件の表で決まっているため。E2E-1 の5回の計測でもアドレス欄を見ることだけを足す。

## Q1. アクセシビリティの検査の差し替えの答え（NFR7.5）と、本物の確かめの API の応答の形を、どう照合しますか？

理由:

- 検査のフォームの状態は、確かめの API（`POST /api/registration/verify`）の答えを `page.route` で差し替えて出します。そのため、本物の応答の形（C6 の InvitationView、`email`・`language`）を通りません（NFR 要件の 3.1 の記録）。
- NFR 要件の承認の場の R-02 は、差し替えの答えの形と本物の応答の形が一致することを、コード生成かレビューの時点で1回照合する手順を計画に書くことを求めています。
- 照合の作りには、1回だけ人が見るか、`./gradlew e2eTest` を流すたびに自動で見るかの違いがあります。
- E2E-1 は本物の確かめの API を呼ぶため、その応答を E2E-1 の中で受け取れます（`page.waitForResponse`）。E2E-1 とアクセシビリティの検査は、どちらも `./gradlew e2eTest` の中で動きます。
- `frontend/tsconfig.json` は `e2e` から `src` の型を読めるため、差し替えの答えの見本を画面の側の応答の型で書けます。

- A. 差し替えの答えの見本を `frontend/e2e/` の手伝いのモジュールに1つだけ置き、画面の側の確かめの応答の型（`registrationApi.ts` が返す型）で型を付ける。アクセシビリティの検査はこの見本を返す。E2E-1 は本物の確かめの応答を受け取り、項目の名前の集まりと各項目の型（文字列、`language` は `ja`・`en`）が見本と一致することを確かめる（流れの成否の一部にする）。形がずれると、`./gradlew e2eTest` のたびに E2E-1 が落ちて気づける。R-02 の「1回の照合」は、コード生成でこの確かめが通ることで閉じる（推奨）
- B. A の見本の型付けだけを行い、本物の応答との照合は B5 のコード生成の時点で1回、人が E2E-1 の実行の記録（応答の本文の項目の名前）と見本を突き合わせて `code-summary.md` に記録する。E2E-1 に確かめを足さない
- C. 見本の型付けと、画面部品のテストで見本を `registrationApi.ts` の応答の形の確かめに通すことだけにする。本物の応答の形は U3 の契約 C6 の結合テストに任せ、画面の側と本物の応答をつなぐ照合はしない
- X. Other (please specify)

[Answer]: A

## Q2. 登録の完了の 400 `VALIDATION_FAILED` の出し方を、機能設計の Q3 A のままにしますか？

理由:

- 承認済みの機能設計の Q3 A は、サーバーの 400 を項目に結び付けず、フォームの上に `role="alert"` の一般の知らせ（「入力を確かめてください。…」）を出し、フォーカスを知らせに移す形です。当時は契約 C6 に項目ごとの誤りの形が無かったためです。
- その後、U3 の NFR 設計（`security-design.md` の6節、SD-D3）で、登録の完了の 400 に U2 の形の `fieldErrors: [{field, reason}]`（C6 の項目名）が載ることになりました。
- 画面は送信の前にサーバーと同じ決まりを確かめるため、サーバーの 400 は画面の確かめをすり抜けたとき（空白の文字の種類の判定の差など）に限られる見込みです。言語・テーマ・文字の大きさはラジオの値のため、ふつうは誤りになりません。
- 項目に結び付けるには `fieldErrors` を読む関数が要ります。U7 の機能設計では、その関数 `fieldErrors.ts` を `frontend/src/features/preferences/` に置いています。機能どうしは依存しないため、U6 が使うには置き場を共用の場所へ移すか、U6 に別に持つ必要があります。
- どちらでもセキュリティの差はありません（`fieldErrors` は項目の名前と理由の値だけを持つ）。U3 が項目を足すのは安全な追加で、U6 が読まなくても壊れません。

- A. 機能設計の Q3 A のままにする。400 `VALIDATION_FAILED` は `fieldErrors` を読まず、フォームの上の一般の知らせを出し、入れた値を残す。承認済みの設計を変えず、U7 の設計にも触れない（推奨）
- B. `fieldErrors` を読んで項目に結び付ける。U7 の `fieldErrors.ts` を `frontend/src/shared/validation/`（U6 が作る共用の場所）へ移して U6・U7 で共用し、氏名・パスワード・確かめの項目の下に理由ごとの文言を出して最初の誤りの項目にフォーカスを移す。`fieldErrors` が無い・読めない・知らない項目だけのときは Q3 A の一般の知らせに戻す。承認済みの U6 の機能設計（Q3 A・5節）と U7 の機能設計（置き場）との差として記録する
- C. B と同じく項目に結び付けるが、読む関数は U6 の `features/registration/` に別に持ち、U7 の設計は変えない（同じ考えの関数が2か所になる）
- X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（Q1・Q2 の回答の後に埋めます）:

- NFR 設計の要点（案）は冒頭の 11 件のとおり。主な点は次のとおり。
  - 画面の置き場と依存の向きは機能設計の2節のまま。E2E-1 とアクセシビリティの検査は別のファイルにし、検査は U4 の手伝いを使う（ファイルの番号と分け方は B5 の計画で U5・U7 とそろえる）
  - リンクを開いてからフォームが出るまでの時間は、E2E-1 の中で新しいコンテキストで5回、テストの側の時計で測って注記と添付で残す。同じ5回でアドレス欄の `#token=` が無いことと CSP の違反が無いことを確かめる
  - 検査の回数は 40 回、見込みで U6 の分は約 1〜2 分、`e2eTest` の検査の部分は約 3〜8 分（見込みの値。実測して記録する）
  - トークンはフラグメントから取り、描画の確定の後に置き換えの移動で消し、メモリと本文だけに持つ。閲覧の履歴に最初のアドレスが残りうることは残る危険として書く
  - 検査の見本のトークンは低いエントロピーの固定の値にする
  - 拡張性・信頼性・観測性の成果物は作らない
- Q1 A: 差し替えの答えの見本を `frontend/e2e/` の手伝いのモジュールに1つだけ置いて画面の側の応答の型を付け、E2E-1 が本物の `/verify` の応答を受け取り、項目の名前と型が見本と一致することを毎回確かめる。コード生成でこの確かめが通れば NFR 要件の R-02 を閉じる
- Q2 A: 登録の完了の 400 VALIDATION_FAILED は機能設計の Q3 A のまま、`fieldErrors` を読まずにフォームの上に一般の知らせを出す。U7 の設計には触れない

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
