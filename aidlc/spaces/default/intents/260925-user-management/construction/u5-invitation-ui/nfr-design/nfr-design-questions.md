# NFR Design — Questions（U5 招待の管理の画面 / u5-invitation-ui）

U5 は、管理者が招待中の人を一覧で確かめ、招待・送り直し・取り消しを行う画面の単位です（S1・S1-M1・S1-M2）。種類は ui のため、作る成果物は performance-design・security-design・logical-components・traceability の4つです（拡張性・信頼性・観測性は service の単位だけ）。

画面の単位の作りの多くは、U4 の NFR 設計（最初の描画のゲート、`050-` の検査の置き場と既存の E2E への非干渉、組の切り替え方、axe-core の読み込みと合否の規則、トークンを付けないパスの一覧）と、承認済みの U5 の NFR 要件・機能設計で決まっています。そのため、まず NFR 設計の要点（案）を示します。上流から作りが1つに決まらない3点だけを質問にします。3点はどれも、B5 で U5 の画面を実際のブラウザで開くための作りです（U4 の B4 の検査はログインしない画面だけを扱ったため、ログインの後の画面を開く作りはまだ決まっていません）。

1. 実際のブラウザのアクセシビリティの検査で、一覧の行と警告の状態をどう出すか
2. 画面の時間の測定（NFR6.1・NFR6.2）で、21 件以上の招待をどう用意し、どのファイルで測るか
3. ログインの後の画面を、検査のコンテキストごとにどう開くか

読んだ上流:

- 承認済みの NFR 要件 `aidlc/spaces/default/intents/260925-user-management/construction/u5-invitation-ui/nfr-requirements/`
  - NFR1.1・NFR2.1、NFR6.1〜NFR6.6、NFR7.1〜NFR7.4、NFR8.1・NFR8.2、NFR9.1〜NFR9.9
  - `nfr-requirements-questions.md` の Q1: B・Q2: B と要点 13 件
- NFR 要件の承認の場の決定（2026-09-27 Approve、Minor はコード生成で拾う）
  - U5 の R-01: 認可の確かめ方の「401・403・200」は、取り消しの成功が 204 のため「401・403・成功（200・201・204、API ごとの状態コードは契約 C5 のとおり）」と読む
- 承認済みの機能設計 `construction/u5-invitation-ui/functional-design/`
  - `functional-spec.md`: D1〜D14、W1〜W10、6.2〜6.4 の応答ごとの動き（400 `VALIDATION_FAILED` は項目ごとの誤りの形に頼らず、決まった文言を出す）、7節の文言
  - `frontend-components.md`: 1節の部品の階層、3節の `useInvitationAdmin`、4節の props（Table の `aria-label`・`labels`、目立たせた行の `:has()`）、5節の API との受け渡し（取り消しは 204 で値なし）、7節のテスト
- 依存する単位の NFR 設計（READY）
  - `construction/u4-display-foundation/nfr-design/`: `logical-components.md` の5節（`frontend/e2e/050-display-accessibility.e2e.ts` の見込み、手伝いは `frontend/e2e/support/`、組ごとに新しいコンテキスト、組の切り替えは `addInitScript` と `/api/appearance` の `page.route`、はみ出しの判定、5.5 で B5 に U5 の画面を足す、ログインの後の画面は前提を検査の中で自分で作る）、`security-design.md` の6節（axe-core は Node の側で読み `page.evaluate` で評価、合否は WCAG 2.0・2.1 の A・AA のタグ、`scrollable-region-focusable` は `wcag2a` に入る）、`performance-design.md` の3節（測定の手順と記録の仕方）
  - `construction/u3-invitation/nfr-design/`: `security-design.md` の6節（招待の 400 にも `fieldErrors` を載せるが、U5 は形に頼らない）、`performance-design.md`（招待・送り直しは確定 → 接続なしの送信 → 結果の記録）
  - `construction/u2-user-preferences/nfr-design/security-design.md` の3節（`fieldErrors: [{field, reason}]` の形）
- 契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`（C5 の一覧・招待・送り直し・取り消し、取り消しの成功は 204。C9 の `useDisplaySettings`）、画面 `inception/refined-mockups/interaction-spec.md`（狭い幅（768px 未満）では表を横に動かして見る、Modal は画面の幅いっぱい）
- U6 の NFR 要件の Q2: B（フォームの状態の検査は `page.route` の差し替えで出す）
- 決まり `aidlc/spaces/default/memory/team.md`・`project.md`（E2E の本数と置き場、Playwright の検査は流れの本数に数えない、実在の宛先へ送らない、公開のリポジトリ）
- 既存のコード
  - `frontend/playwright.config.ts`: 1つの WAR と実行ごとの一時の内部DB を全ファイルで共有、`workers: 1`、初期管理者 `e2e-admin@example.com` と実行ごとに作るパスワード。`webServer.env` に招待を使える設定（SMTP・ベース URL）が無い
  - `frontend/e2e/030-admin-access.e2e.ts`・`040-dsl-admin.e2e.ts`: ログインの画面のフォームから管理者でログインしてから管理画面を開く
  - `frontend/src/features/auth/authSession.ts`: アクセストークンはメモリだけ。画面を開くとリフレッシュトークン（HttpOnly の Cookie、`backend/src/main/java/cherry/mastersmith/auth/web/RefreshCookies.java`）で1回だけ復元する
  - `backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java`: リフレッシュトークンは使うたびに作り直す（前の値は無効になる）。そのため、1回のログインの Cookie を複数のコンテキストで使い回せない
  - `frontend/src/shared/api-client/apiClient.ts`: `apiRequest` は `Response` を返し、本文を読むかは呼び出し側が決める
  - `vendor/make-you-chic-ui`（固定先の更新の後の 735ef04 を `git show origin/main:` で読み取り）: Table は `mycui-table-wrapper`（`overflow-x: auto`）で包み、包む要素に `tabIndex`・名前は無い。`aria-label` は `table` 要素に付く。行の class と `caption` の口は無い

## NFR 設計の要点（案）

1. **部品の構成（logical-components）**
   - 置き場は機能設計の2節のとおり `frontend/src/features/invitation/` とし、`formatDateTime` を `frontend/src/shared/format/` へ移す。骨組み・AdminArea・ApiClient は変えない。
   - 失敗の範囲は画面の中に閉じる。一覧が読めないときは一覧の中の表示と「もう一度読み込む」、操作の失敗は一覧の上か Modal の中の知らせ（機能設計の W10・6節）。ほかの機能の画面には及ばない。
   - 状態は画面の中だけに持ち、画面を離れた後の答えは捨てる（D2）。アプリ全体の置き場を作らない。
2. **性能（一覧の読み方と描き方、NFR6.3）**
   - 読み直しごとに番号を増やし、最後の番号の答えだけを使う。画面を離れた後の答えも捨てる（`useDslAdmin` の `statusSeq`・`mounted` と同じ形）。
   - 読み直しの間は Table とページ送りのボタンを描いたまま `data` を空にする（機能設計の 4.1）。決まった間隔の読み直しはしない。
   - 部品のテストで、偽の時計を進めても一覧の API が呼ばれないことを見る。
3. **性能（待ちの見せ方と時間切れ、NFR6.4・NFR6.5）**
   - 要求の間は押したボタンを Button の `loading` にし、フォーカスを保つ。招待の送信中と取り消しの要求中は Modal を閉じない。
   - 画面の側に時間切れ・中断（AbortController）・再試行を置かない。受け手が応答の手前で遅れ続ける既知の限界（U1 の NFR6.3）の間は「送信しています」のまま閉じられないことを、logical-components の失敗の範囲に書く。
4. **性能（画面の時間の測定、NFR6.1・NFR6.2）**
   - 測り方は U4 の `performance-design.md` の3節にそろえる。テストの側の時計で測り、5回の値をテストの注記と添付（JSON）で残し、Build and Test の結果に写す。時間で失敗させない（統合の関門にしない）。
   - 一覧（2 秒）は、移動の開始から一覧の 20 行目が見えるまで。次のページ（1.5 秒）は、「次へ」を押してから2ページ目の最初の行が見えるまで。1ページ目へ戻してから測り直す。
   - 測る場と招待の用意は Q2 で決める。長い計測は `caffeinate -i` を付けて流す（`project.md`）。
5. **性能（初回の JavaScript、NFR6.6・NFR9.4）**
   - 画面は機能の登録の `lazy` で遅延読み込みの塊に入り、入口のファイルを増やさない。新しい依存を足さない。
   - 既存の `check-bundle-size.mjs` で変更の前後を測り、U5 の画面の塊が入口と別のファイルに出ることと、その塊の大きさ（圧縮前と gzip）をコード生成で記録する。
6. **セキュリティ（トークン・URL・メールアドレス、NFR1.1・NFR2.1）**
   - 応答の型（`api/types.ts`）は契約 C5 の `Invitation` の決まった項目だけを持つ。表示は型の項目だけから作り、知らない項目は読まない。
   - 今のページは画面の中の状態だけに持ち、URL・ブラウザの保存に載せない。画面のコードで `console` を呼ばない。
   - 確かめは NFR 要件の表のとおり画面部品のテストで行う（余計な `token`・`url` を加えた応答、localStorage・sessionStorage のすべての鍵、`location`、`console` の5つの関数の見張り）。
7. **セキュリティ（失敗の文言と応答の読み方、NFR9.1）**
   - 文言は `failureMessage.ts` で `code` と状態コードの種類から鍵を選び、`detail` を使わない。差し込みの値は React の文字として描く。
   - 400 `VALIDATION_FAILED` は、機能設計の 6.2 のとおりメールアドレスの項目の下に決まった文言を出し、`fieldErrors` を読まない（U3 の NFR 設計が載せる `fieldErrors` は安全な追加で、U5 はその形に頼らない。ApiClient も変えない）。
   - 取り消し（`cancelInvitation`）は 204 で本文を読まない。「成功の本文を JSON として読み、読めなければ通信の失敗」の扱いは、一覧・招待・送り直しの3つだけに当てる（承認の場の R-01 の 204 を画面の側でも取り違えない）。`invitationApi.ts` のテストで、204 の空の本文を成功として扱うことを見る。
   - `readPendingProblem`・`readUnavailableReasons` は `problem` の型を確かめてから読み、合わなければ使わない。
8. **セキュリティ（認可の画面の側の扱いと CSP、NFR9.2・NFR9.3）**
   - 画面は骨組みの `access: 'ADMIN'`・`visibleWhen: 'ADMIN'` のまま。401 は ApiClient の更新とログインの画面への移動に任せ、403 は一般の 4xx の文言で示す。
   - サーバー側の 401・403・成功（一覧 200・招待 201・送り直し 200・取り消し 204、契約 C5 のとおり）は U3 のサーバー側のテストで確かめ、画面の側の扱いで代えない。
   - 目立たせた行の見た目は機能の CSS（`:has()`）で付け、`style` 属性で差し込まない。CSP・`index.html` は変えない。
9. **アクセシビリティ（make-you-chic-ui の Table の足りない口、NFR7.1・NFR9.5）**
   - `vendor/make-you-chic-ui` は変えず、Table の外側で足す（NFR9.5 のとおり）。
     - 表の名前（`caption` の代わり）: 見える一覧の見出し `h2`「招待中の人」と、Table の `aria-label`（`table` 要素に付く）
     - 行ごとの class の代わり: 列の `render` で描くメールアドレスのセルの受け口に `data-invitation-highlighted` の印を置き、機能の CSS の `:has()` で行に枠の線と背景を付ける
     - 横に動く領域の名前: 付けない。Table の包む要素（`overflow-x: auto`）に名前と `tabIndex` は無いが、各行に押せるボタン（「取り消す」は招待を使えないときも押せる）があるため、キーボードで領域の中に届き、axe の `scrollable-region-focusable` は通る見込み。規則が流れたことは U4 の `security-design.md` の 6.4 のとおり検査の中で確かめる
   - Table の中の class・`data-testid` を探して見た目やフォーカスを当てることはしない（機能設計の 3.3）。
10. **実際のブラウザの検査の置き場と組（NFR7.3・NFR7.4・NFR9.9）**
    - U4 の手伝い（`frontend/e2e/support/`: 組の切り替え・axe-core の読み込みと実行・はみ出しの判定・CSP の違反の集め）を使い回し、U5 の検査は `050-` の後の番号の新しいファイルに置く（例: `frontend/e2e/05x-invitation-accessibility.e2e.ts`。番号はコード生成で決める）。既存の 010〜050 のファイルは変えない。
    - 組と対象は NFR 要件のとおり。
      - 既定の幅: (a) テーマ2×文字の大きさ3の6組、(b) ブランドカラー4×テーマ2の8組。対象は一覧（行あり）・招待を使えないときの警告・招待の入力の Modal・取り消しの確かめの Modal
      - 幅 375px: (c) テーマ2×文字の大きさ3の6組。対象は一覧（行あり）・2つの Modal（警告は入れない）
    - 1つの組の中で、一覧 → 招待の入力の Modal を開いて検査 → 閉じる → 取り消しの確かめの Modal を開いて検査 → 閉じる、の順に同じコンテキストで状態を出す。Modal は開くだけで、招待・取り消しの要求は送らない。
    - 一覧の行・警告の状態の出し方は Q1、ログインの後の画面の開き方は Q3 で決める。
    - 流れの E2E ではないため、`team.md` の「代表の流れを1本まで」に数えない。U5 の変更の後に E2E-1（U6）と既存の E2E が通ることを B5 で確かめる。
11. **拡張性・信頼性・観測性（ui のため成果物は作らない）**
    - 状態はブラウザの1つのタブの中だけにある。画面の側に独自の指標・ログの送り先を足さない。要点は logical-components の失敗の範囲に書く。
12. **テストとカバレッジ（NFR7.2・NFR8.1・NFR8.2・NFR9.6〜NFR9.8）**
    - 画面部品ごとの vitest-axe の検査、fast-check の性質ベースのテスト（`paging.ts`・`inviteInput.ts`、失敗時の種を記録）、ja・en の文言の鍵のそろい、en の画面で Table と Modal の日本語の既定の文言が出ないことを見る。
    - フロントエンドのカバレッジの下限（行 80%・分岐 70%）を守り、除外を増やさない。`frontend/e2e/` の下の検査は Vitest の計測の対象外のまま。
    - `formatDateTime` を移した後も DSL の画面のテストがそのまま通ることを確かめる。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 一覧は 20 件ごと・サーバーの順・自動の読み直しなし・重なった読み直しは最後の答えだけ・読み直しの間も Table を残す | 機能設計 D1・D2・4.1、NFR6.3 |
| 画面は独自の時間切れを置かない。受け手が遅れ続ける既知の限界の間は Modal を閉じられない | NFR6.5、機能設計 D9 |
| 画面の時間の目標（一覧 2 秒・次のページ 1.5 秒）は、実際のブラウザで5回ずつ測って記録し、統合の関門にしない | NFR6.1・NFR6.2（Q2: B） |
| 画面はトークン・URL を扱わず、メールアドレス・氏名を保存・URL・コンソールに出さない | NFR1.1・NFR2.1、機能設計 D11 |
| 失敗の文言は `code` から選び、`detail` を使わない。400 は項目ごとの誤りの形に頼らない | NFR9.1、機能設計 6.2、U3 の NFR 設計の `security-design.md` 6節 |
| 取り消しの成功は 204（値なし）。サーバー側の認可は 401・403・成功（200・201・204）を U3 で確かめる | 契約 C5、NFR 要件の承認の場の R-01 |
| 実際のブラウザの検査は Playwright＋axe-core（U4 の手伝い・読み込み方・合否の規則のまま）。組は (a)・(b)・(c)。流れの本数に数えない | NFR7.3・NFR7.4・NFR9.9、U4 の NFR 設計 |
| 検査の要求の差し替え（`page.route`）は、検査のブラウザのコンテキストの中だけで行い、アプリの CSP とコードは変えない | U4 の NFR 設計 5.1、U6 の NFR 要件の Q2: B |
| Table の足りない口（`caption`・行の class・横に動く領域の名前）は Table の外側で足し、`vendor/make-you-chic-ui` を変えない | NFR9.5、`project.md` の Forbidden |
| E2E の WAR へ招待を使える設定（SMTP・ベース URL）を渡す方法は infrastructure-design の持ち主 | NFR 要件の performance-requirements.md の前提、U6 の NFR 要件 |

次の2点は、候補の論点から質問にせず、要点に書いて要約で確かめます。

- Table の足りない口の回避（要点 9）: NFR9.5 で「Table の外側（見える見出し・`aria-label`・機能の CSS）で足す」と決まっており、作りが1つに決まるため。
- 招待の 400 の `fieldErrors` の出し方（要点 7）: 承認済みの機能設計の 6.2 が「項目ごとの誤りの形に頼らない」と決めており、U3 の NFR 設計も「U5 は形に頼らない」としているため。

## Q1. 実際のブラウザのアクセシビリティの検査で、一覧の行と「招待を使えない」警告の状態を、どう出しますか？

理由:

- NFR7.3 は、行を置くための招待と、警告の表示に要る「招待を使えない状態」を、検査の中で自分で用意するとしています（前のテストの状態に頼らない）。
- 本物のサーバーで行を置くには、E2E の WAR が招待を使える設定（SMTP・ベース URL）を持ち、管理者が招待を送る必要があります。この設定の渡し方は infrastructure-design の持ち主で、まだ決まっていません。
- 警告の状態は「招待を使えない」設定の WAR でしか本物では出ません。E2E の WAR は全ファイルで1つを共有し、U6 の E2E-1 のために招待を使える設定にするはずなので、同じ実行の中で本物の警告の状態は出せません。
- 検査の目的は描画と色・構造の確かめで、サーバーの動きの確かめではありません。U6 も同じ理由で、フォームの状態を `page.route` の差し替えで出すと決めました（U6 の NFR 要件の Q2: B）。
- 差し替えは応答の形が本物とずれるおそれがあります。本物の形は、U3 の契約 C5 の結合テストと、Q2 の測定（本物の一覧を読む）と E2E-1 で通ります。

- A. 一覧の API（`GET /api/admin/invitations`）の答えを、そのコンテキストの中だけで `page.route` で差し替えて出す。行ありは 20 行・全件数 21 以上（送信の結果 SENT・FAILED、期限内・期限切れ、招待した管理者の空を含む見本）、警告は `invitationEnabled: false` と理由2つ。見本は `api/types.ts` の型で組み立て、型の食い違いを型検査で止める。メールアドレスは予約済みのドメイン（`example.com`）の固定の見本。Modal は開くだけで要求を送らないため、ほかの API は差し替えない（推奨）
- B. 行ありの状態は本物のサーバーで招待を置いて出し（infrastructure-design の設定に頼る）、警告の状態だけ `page.route` の差し替えで出す
- C. 警告の状態は実際のブラウザの検査から外し、画面部品のテスト（vitest-axe）だけで見る。行ありの状態は B と同じく本物で出す
- X. Other (please specify)

[Answer]: A

## Q2. 画面の時間の測定（NFR6.1・NFR6.2）で、21 件以上の招待をどう用意し、どのファイルで測りますか？

理由:

- NFR6.1 は、招待を 21 件以上置いた状態で「開いてから 20 行目が見えるまで 2 秒以内」とし、どのファイルで測るか（Playwright＋axe-core の検査か E2E-1 か）をコード生成で決めるとしていました。画面の待ちは主に一覧の API の時間のため、測定は本物のサーバーの一覧を読む必要があります（差し替えでは API の時間が入らない）。
- 本物の招待を置くには、E2E の WAR が招待を使える設定を持つことが前提です（infrastructure-design。U6 の E2E-1 も同じ前提）。設定があれば、受け手に届かなくても招待は置かれ、`sendResult` が FAILED になるだけです（201）。
- 画面から 21 回招待すると、Modal の操作が 21 回になり測定の前の準備が長くなります。API で置けば、メールの送信と監査は本物と同じ道を通りつつ準備は短くなります。
- 置いた招待は、同じ WAR と内部DB を使う後のファイル（U6 の E2E-1 など）にも残ります。後のファイルは一覧の件数に頼らない作りにする必要があります（NFR9.9 と U4 の NFR9.11）。

- A. U5 の検査のファイル（要点 10）の中に測定のテストを1件置き、準備としてそのテストの中で管理者のアクセストークンを API のログインで得て、招待の API（`POST /api/admin/invitations`）で実行ごとに重ならない 21 件（`example.com` の下）を置く。その後、新しいコンテキストで5回、移動の開始から 20 行目が見えるまでと、「次へ」から2ページ目の最初の行までを測る。E2E-1 は変えない。E2E の WAR に招待を使える設定が無いときは測定を `Unverified` とし、持ち主の段（Build and Test）を明記して引き継ぐ（推奨）
- B. A と同じファイルで測るが、21 件は画面の招待の Modal から置く（API を直接呼ばない）
- C. E2E-1（U6）の中で測る。E2E-1 の招待の前に 20 件を置いてから、流れの途中で一覧を5回開いて測る
- X. Other (please specify)

[Answer]: A

## Q3. ログインの後の画面（招待の管理）を、検査と測定のコンテキストごとに、どう開きますか？

理由:

- アクセストークンはメモリだけにあり、リフレッシュトークンは HttpOnly の Cookie で、使うたびに作り直されます。そのため、1回のログインの状態（`storageState`）を複数のコンテキストで使い回すと、2つ目以降の復元が失敗します。組ごとに新しいコンテキストを使う（U4 の NFR 設計 5.1）ため、コンテキストごとにログインが要ります。
- 組は (a) 6・(b) 8・(c) 6 の 20 組と、測定の5回で、1回の実行でログインは約 25 回になります。ログインの成功は監査に残りますが、実行ごとの一時の内部DB の中だけです。ロックの回数は失敗だけを数えるため、成功のログインは影響しません。
- 組の切り替え（`addInitScript`・`/api/appearance` の差し替え）は画面の読み込みの前に置くため、どちらの開き方とも両立します。

- A. コンテキストごとに、ログインの画面のフォームから管理者でログインし（既存の 030・040 と同じ道）、サイドバーの「利用者の招待」を押して開く。測定（Q2）は、ログインの後にサイドバーの項目を押してから測る（NFR6.1 の「サイドバーの項目を選んでから」）。手伝いにログインの関数を1つ置き、既存の 030・040 は変えない（推奨）
- B. コンテキストごとに、そのコンテキストの要求の口（`context.request`）でログインの API を呼んでリフレッシュトークンの Cookie を得て、`/admin/invitations` を直接開く（画面は復元の道でログイン状態になる）。ログインの画面を通らないため速いが、Cookie の名前と復元の道に頼る
- C. A と B を分ける。アクセシビリティの検査は B（速さ）、測定は A（利用者の操作に近い道）
- X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（Q1〜Q3 の回答の後に埋めます）:

- NFR 設計の要点（案）は冒頭の 12 件のとおり。主な点は次のとおり。
  - 一覧は最後の読み直しの答えだけを使い、読み直しの間も Table を残す。画面の側に時間切れを置かない
  - 画面の時間は U4 の測り方にそろえ、5回の値を注記と添付で残し、統合の関門にしない
  - 取り消しの 204 は本文を読まずに成功とし、400 は `fieldErrors` を読まない
  - Table の足りない口は、見える見出し・`aria-label`・`:has()` の機能の CSS で足し、make-you-chic-ui を変えない
  - U5 の実際のブラウザの検査は、U4 の手伝いを使う `050-` の後の新しいファイルに置き、1つの組の中で一覧と2つの Modal を順に出す（要求は送らない）
  - 拡張性・信頼性・観測性の成果物は作らない
- Q1 A: 実際のブラウザの検査で、一覧の行と「招待を使えない」警告の状態は `GET /api/admin/invitations` の答えを `page.route` で差し替えて出す。見本は `api/types.ts` の型で組み立てる。Modal は開くだけで要求は送らない（U6 の Q2 B と同じ考え方）
- Q2 A: U5 の検査のファイルに測定のテストを1件置き、API のログインで得たトークンで招待の API から 21 件を置いて、本物の一覧で5回ずつ測る。E2E の WAR に招待の設定が無ければ `Unverified` とし、持ち主の段（Build and Test）を明記して引き継ぐ。置いた招待は共有の内部DB に残るため、後のファイルは一覧の件数に頼らない
- Q3 A: ログインの後の画面は、コンテキストごとにログインの画面のフォームからログインし、サイドバーの項目を押して開く（既存の 030・040 と同じ道）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
