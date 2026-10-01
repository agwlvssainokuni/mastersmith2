# NFR Design の質問 — u5-user-admin-ui

単位 U5（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 ui）の NFR 設計のための質問です。ui の単位のため、成果物は `performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json` の4つです。確かめた資料は、この単位の承認済みの NFR 要件 `construction/u5-user-admin-ui/nfr-requirements/`（`performance-requirements.md` の NFR5.1〜NFR5.6、`security-requirements.md` の NFR1.1・NFR1.2・NFR3.1〜NFR3.5・NFR9.1 と「承認の場の決定」の節と申し送り、`tech-stack-decisions.md` の NFR7.1〜NFR7.3・NFR8.1〜NFR8.3・NFR9.2〜NFR9.9）、NFR 要件の再レビューの新しい Minor（R-06 失敗のときの `error-context.md`、R-07 trace を切った後の診断）、機能設計 `construction/u5-user-admin-ui/functional-design/`（`functional-spec.md` の 3節・9節・承認の場の決定、`frontend-components.md` の 2.4・2.5・8節・9節）、契約 `inception/contract-design/contract-summary.md` の C3・C4、今のコード（`frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`frontend/e2e/support/registeredUser.ts`・`adminLogin.ts`・`invitationSeed.ts`・`pageProblems.ts`・`frontend/e2e/060-invitation-accessibility.e2e.ts`・`080-preferences-accessibility.e2e.ts`、Playwright 1.63.0 の `frontend/node_modules/playwright/lib/` と `playwright-core/lib/coreBundle.js`）です。実行はしておらず、Playwright の動きはソースを読んで確かめた範囲です。配備先は開発者の PC 上のコンテナで、U5 には基盤の論点がありません。

## 設計の要点（案）

承認済みの NFR 要件と今のコードから導ける、この単位の作りの見通しです。質問の答えで決まる点は（Qn）と書きます。

### 性能（performance-design.md）

1. **一覧の読み方（NFR5.4）**: 読むのは開いたとき・ページ送り・検索・検索を消す・操作の後・「もう一度読み込む」だけ。`useUserAdmin` が読み直しごとに番号を振り、最後に始めた読み直しの答えだけを使い、画面を離れた後の答えは捨てる。空のページの読み直しは1回の読み込みにつき1回まで。並べ替えと決まった間隔の読み直しはしない。
2. **待ちの見せ方と二重の送信（NFR5.5）**: 独自の時間切れは置かず、送信から 5 秒で「時間がかかっています」を出す（タイマーは応答と画面を離れたときに解く）。二重の送信は、表示のボタン・`Button` の `loading` と `aria-disabled`・`onPageChange` の側で捨てる、`useUserAdmin` の参照（`useRef`）での守り、の3つで防ぐ（機能設計の承認の場の R-01）。
3. **初回の大きさ（NFR5.6）**: 画面は `registration.ts` の `lazy` で読み、新しい実行時の依存を足さない。`check-bundle-size.mjs` の値を固定先を上げる前と U5 の後で測って記録する。
4. **画面の時間の測り方（NFR5.1・NFR5.2）**: 120 の中に測りのテストを1件置き、20 組の繰り返しの外で既定の1組（light・md・blue、既定の幅）だけで動かす。前例の 060 と同じく、テストの側で `Date.now()` を取る。一覧は「ホームの画面でサイドバーの『利用者の管理』を選ぶ直前」から「一覧の最初の行が見えた時点」まで、ホームへサイドバーで戻って 5 回（再読み込みしない）。この間、一覧の GET は本物へ通す。次のページは、同じテストの後半で差し替えの口を見本の 2 ページ分に切り替え、「次へ」を押してから2ページ目の最初の行が見えるまでを「前へ」で戻して 5 回。どちらも 1 回目（遅れて読み込む部品の取得を含む）と 2〜5 回目を分け、`{ first, rest, max, withinTarget, targetMs }` の形の数だけを注記と添付（`user-admin-screen-ms`）に残す。時間はテストの成否にしない。差し替えの口は本物へ通す GET も一度受けてから通すため、その分の小さな上乗せが値に含まれることを記録に書く。
5. **API の時間（NFR5.3）**: U3 の目標を当て、U5 では測り直さない。画面が送るパス・`q`・本文が C3 のとおりであることは `api/userAdminApi.test.ts` で確かめる。

### セキュリティ（security-design.md）

6. **画面の判定とサーバーの判定（NFR1.1・NFR1.2）**: 行の項目と押せなさは `admin`・`suspended`・`resettable`・`self` の4つだけから純粋な関数 `rowActions.ts` で決め、サーバーの判定の代わりにしない。403 は C4 の `useAdminForbidden` に渡し、401 は ApiClient に任せて S3・S4 を閉じる。
7. **画面の外に出さない（NFR3.1〜NFR3.3）**: ページと検索の文字は画面の中の状態だけに持ち、URL・保存・履歴・コンソールに出さない。文言は `code` と状態コードから選び、`detail`・`title` は出さない。応答は C3 の項目だけを型で読み、知らない項目を捨てる。HTML を直接埋め込まない（NFR9.1、CSP は変えない）。
8. **値のファイル（NFR3.4）**: 110 が作った値（U のメールアドレス・パスワード・氏名、`runTag`）を、新しい手伝いが `test-results/` の下の1つのファイルに JSON の配列で書く（作るときに権限 600、書いた後にも 600 に直す）。Playwright は実行の始めに `test-results/` を消すため、前の実行の値は残らない。報告の部品は探し終えたらこのファイルを消す。途中で止めた実行で残ったファイルは、次の実行の始めに消える。
9. **報告の部品の探し方（NFR3.4）**: 探す値は、環境変数の3つ（今のまま）と値のファイルの値。あわせて値の形（`u7-perf-` で始まり `@example.com` で終わる宛先、`e2e-u7-pw-` と 24 文字の16進のパスワード、氏名「計測 花子」）でも探す。値はそのままの形に加えて、URL の形（`encodeURIComponent`）と JSON の `\u` の形でも探す（trace の要求の記録や json の中で形が変わるため）。探す先は json の報告と、`test-results/` の下のすべてのファイル（`trace.zip`・`error-context.md` を含む。R-06）。zip は中央の目録を読み、各ファイルを `node:zlib` の `inflateRawSync` で展開して読む（新しい依存を足さない）。html の報告の扱いは（Q1）。開けない・展開できないファイルがあれば失敗にする。値は表示せず、どの種類の値がどの種類のファイルに何件あったかだけを出す。
10. **trace と診断（NFR3.4 (c)・R-07）**: 110 と 120 は `test.use({ trace: 'off' })` にする。ほかの E2E の trace の扱いは（Q2）。失敗のときの値を伏せた手がかりの作りは（Q3）。
11. **120 の差し替えの口（NFR3.5）**: 新しい手伝いのファイル1つに、`page.route` の口を1つだけ置き、`/api/admin/` で始まる道の要求をすべて受ける。(1) 見本で返す道とメソッドの組なら `route.fulfill`、(2) GET でかつ本物へ通すモードなら `route.continue`、(3) それ以外の GET 以外（POST・PUT・PATCH・DELETE）は `route.abort` して `{ method, path }` を記録する（道の中の利用者 ID は `{id}` に置き換え、問い合わせの部分は記録しない）。モードは検査（すべて見本）と測り（一覧の GET は本物、後半は見本）で切り替える。各テストの終わりに、記録が 0 件であることを、メソッドと道の型だけを失敗の知らせに出して確かめる。口は `page.goto` より前に張る（`watchPage` と同じ順）。`request` の口（APIRequestContext）の要求は `page.route` に乗らないため、120 は `/api/admin/` の下の書き換えを `request` の口から送らない。わざと POST を送って記録され失敗になることを、コード生成で一度確かめる。
12. **見本（NFR9.9）**: 一覧・409・400 の見本を1つの置き場にまとめて `api/types.ts` の型を付け、110 の中で本物の応答と項目の名前・型を照らし合わせる。見本の宛先は `example.com` で、`u7-perf-` の形を使わない（9 の形での探し方と重ならないため）。

### 論理部品（logical-components.md）

13. **画面の部品**: `frontend/src/features/useradmin/`（`UserAdminPage`・`UserSearchBox`・`UserTable`・`UserRowActions`・`ConfirmActionDialog`・`EditProfileDialog`・`useUserAdmin`・`api/userAdminApi.ts`・`api/types.ts`・`rowActions.ts`・`searchInput.ts`・`failureMessage.ts`・`lockedUntil.ts`・`registration.ts`）と、使う共通のもの（`src/shared/paging/`・`src/shared/format/`・`src/shared/validation/`・`src/shared/api-client/fieldErrors.ts`、U4 の `useAdminForbidden`・`useApplyOwnProfile`）。機能設計の `frontend-components.md` のとおりで、新しい部品は足さない。
14. **E2E の部品**: 足すのは 110・120 と、新しい手伝い（値のファイル・差し替えの口・見本・値を伏せた診断）。直すのは `frontend/playwright-secret-check-reporter.ts` と、答えによって `frontend/playwright.config.ts`（Q1・Q2・Q3）。手伝いの置き場（`support/` の下の新しいファイルか）とファイルの名前はコード生成の計画で決める。既存の `support/` のファイルと 010〜100 は変えない（Q2 の答えによる差を除く）。

### テストと関門（traceability.json）

15. **テストと関門**: カバレッジの下限（行 80%・分岐 70%）と除外を増やさないこと、性質ベースのテスト（`rowActions.ts`・`searchInput.ts`）、`waitFor` と偽の時計、文言の鍵の ja・en の一致は NFR 要件のとおりで、作りの選択肢は無い。traceability.json は NFR 要件の ID を、上の設計の節へ結ぶ。新しい枝番は足さない。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 110 と 120 は trace を残さない。locator の名前と期待値・`test.step` の題・注記・添付に値を入れず、名前の比べは真偽だけを `expect` に渡す | `security-requirements.md` の NFR3.4 (a)〜(c) |
| 値のファイル（権限 600）と、値の形での探し方と、zip を `node:zlib` で読むこと。開けない報告は失敗にし、値は表示しない | NFR3.4 の「仕組み」、承認の場（R-01） |
| 120 は `/api/admin/` の下の GET 以外を本物へ通さず打ち切って記録し、0 件を確かめる。手伝いは新しいファイルに置き、既存の `support/` は変えない | NFR3.5、NFR9.9、承認の場（R-02） |
| 画面の時間の測り始めはサイドバーの項目を選んだ時点の1つ。1回目を分けて記録し、目標から外さない。既定の1組だけで測り、成否にしない | NFR5.1・NFR5.2、承認の場（R-03） |
| 実際のブラウザの検査は 20 組×11 状態で、`REQUIRED_RULES` が走ったことを確かめる | NFR7.3、承認の場（R-04） |
| make-you-chic-ui の固定先の具体のコミット、固定先の更新と `.npmrc` の変更を別のコミットにする順は、B5 のコード生成の計画で決める | NFR9.3・NFR9.4、承認の場（R-05） |
| 新しい npm の依存を足さない。CSP を変えない | NFR9.2・NFR9.1 |
| 報告（`frontend/test-results/`・`frontend/playwright-report/`）はコミット・共有しない（`.gitignore` の対象のまま） | NFR3.4 (e) |
| 配備先は開発者の PC 上のコンテナで、U5 に基盤の設計の論点は無い | `project.md` の Deployment の学び |

---

## Q1. html の報告をどう扱いますか？

理由: Playwright 1.63 は、`fill` の操作の手順の題を `Fill "<入れた値>"` の形で作ります（`playwright-core/lib/coreBundle.js` の `Frame.fill` の題）。json の報告は `test.step` の手順だけを書くため入りませんが、html の報告はすべての手順を書くため入ります（`playwright/lib/runner/index.js` の html の組み立て）。そのため、今でも既存の `support/adminLogin.ts` の `loginAsAdmin` が入れる初期管理者のメールアドレスとパスワード、`support/registeredUser.ts` の `loginWithForm` が入れる U のメールアドレスとパスワードが、html の報告（`playwright-report/index.html` の中に base64 の zip で埋め込まれる）に残っています。110 の検索の欄に入れる `runTag` も同じです。承認済みの NFR3.4 のとおり html を探す先に加えると、010〜100 を含むふつうの成功の実行でも報告の部品が失敗になります。

A. html の報告を作らない（`frontend/playwright.config.ts` の reporter から `html` を外し、`list`・`json`・報告の部品の3つにする）（推奨）。Build and Test が写すのは json だけで、html を読む手順は無いため。値を入れる手伝いを変えずに「報告に値を残さない」目的を満たせる。前の実行の `playwright-report/` が残っていれば、部品が値の形で探し、見つかったら失敗にして消す手順を示す。承認済みの NFR3.4 の「html を探す」との差は、html を作らないことで目的を満たした差として記録する。代わりに、手元で html の報告を開いて調べる手段がなくなる（診断は Q3 による）
B. html の報告を残して探す先に加え、値が手順の題に出ない入れ方に変える。110・120 の新しい手伝いに加えて、既存の `support/adminLogin.ts`・`registeredUser.ts` の入れ方も変える（`fill` を使わず、ページの中で値を入れて入力の出来事を起こす）。機能設計の「`support/` は変えない」との差になり、010〜100 のログインにも効く。React の入力欄に値を入れる仕組みが実際の入力と違う動きになるおそれがある
C. html の報告を残し、探す先を json と `test-results/` の下に限る（html は探さない）。作りは最も少ないが、値は html に残り続け、承認済みの NFR3.4（html を探す）と違う作りになる
X. Other (please specify)

[Answer]: A

## Q2. 080 を含む既存の E2E の trace をどうしますか？

理由: 設定の既定は `trace: 'retain-on-failure'` で、失敗した E2E の `trace.zip` には、操作の記録（`fill` の値）と要求の本文が入ります。080 は 110 と同じ手伝い `createRegisteredUser`・`loginWithForm` を使うため、失敗した 080 の trace には U の宛先・パスワードが入り、値の形での探し方で見つかります。ほかの E2E も、`loginAsAdmin` を使うものは失敗したときの trace に初期管理者の値が入ります（今の部品は json だけを探すため、見つけていません）。探す先に `test-results/` の下を加えると、どの E2E が失敗しても部品も失敗になります。NFR 要件の申し送りで、080 の扱いを依頼者に確かめることになっています。

A. 設定の既定を `trace: 'off'` にし、すべての E2E で trace を残さない（推奨）。設定の1か所で、どの E2E の報告にも操作の値が残らない形にそろい、080 のファイルも変えずに済むため。110・120 の `test.use` は明示のために置く。調べるときは、手元だけで環境変数（例 `E2E_TRACE=retain-on-failure`）を付けて流し直して trace を見る。その実行では部品が失敗になりうるため、合否に使わず、見た後に報告を消す（README に手順を書く）
B. 080 だけ、110・120 と同じく `test.use({ trace: 'off' })` を足す（機能設計の「010〜100 は変えない」との差として記録する）。ほかの E2E の trace は残し、失敗のときに部品が初期管理者の値を見つけたら、既存の失敗として記録し、値を表示せずに報告を消す
C. 080 も変えない。080 が失敗したときに部品も失敗するのは既存の失敗として記録し、値を表示せずに報告を消す（流れの失敗で実行はもともと失敗している）。作りは変わらないが、失敗のたびに値の入った trace が手元に残る間がある
X. Other (please specify)

[Answer]: A

## Q3. 110・120 が失敗したときの、値を伏せた診断をどう作りますか？

理由: trace を残さないと、失敗した流れの手がかりが減ります（NFR 要件の再レビューの R-07）。一方、Playwright は trace の設定と関係なく、失敗したときに `test-results/` の下へ `error-context.md`（誤りの文と、画面の ARIA の写し）を書き、報告の添付にします（`playwright/lib/index.js`、R-06）。110 の失敗では、画面の写しに U の行の氏名・メールアドレスが入るため、部品が見つけて失敗になります。

A. 画面の写しは止めない。110・120 の新しい手伝いが、テストの間に値を含まない記録を集め、失敗したときだけ注記と添付（`user-admin-diagnostics`）に残す（推奨）。記録は、通った手順の題、`/api/admin/`・`/api/auth/` の要求のメソッドと道の型（利用者 ID は `{id}`、問い合わせは載せない）と状態コード、`watchPage` の問題の件数、今の画面の道（`/admin/users` など）、一覧の行の数。値を伏せたまま、どこで何が返ったかが分かるため。写しに値が出て部品が失敗したときは、写しを読んで原因を確かめた後に報告を消す（README に手順を書く）。それでも足りなければ、Q2 の手順で手元だけ trace を有効にして流し直す
B. A の記録に加え、画面の写しを止める（設定で `PLAYWRIGHT_NO_COPY_PROMPT` を立てる）。`error-context.md` が誤りの文だけになり、失敗のたびに部品も失敗することが減る。代わりに、すべての E2E で画面の写しの手がかりを失い、この環境変数は Playwright の内部の扱いで、版を上げると変わりうる
C. 値を伏せた記録は作らず、失敗したら手元だけ trace を有効にして流し直す手順だけを書く。作りは最も少ないが、一度きりの失敗（流し直すと通るもの）の手がかりが残らない
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U5 の NFR 設計の計画:

- 設計の要点（案）のとおりに作る。成果物は ui の4つ（performance・security・logical-components・traceability）。
- Q1 A: E2E の html の報告を作らない（reporter から `html` を外す）。Build and Test が写すのは json だけ。承認済みの NFR3.4（html を探す）との差として記録する。
- Q2 A: E2E の設定の既定を `trace: 'off'` にする（080 を含むすべて）。調べるときは手元だけ環境変数で一時的に trace を有効にし、その実行は合否に使わない。
- Q3 A: 110・120 が失敗したときだけ、値を含まない記録（通った手順の題、要求のメソッドと道の型と状態コード、画面の問題の件数、今の画面の道、行の数）を注記と添付に残す。画面の写し（`error-context.md`）は止めず、値を探す先に入れる。写しで確かめが失敗したら、中身を確かめてから消す。
- 値のファイル（`test-results/` の下、権限 600、探した後に消す）と、探す値の形（そのまま・URL の形・JSON の `\u` の形）、120 の差し替えの口と打ち切りの記録、画面の時間の測り方は、設計の要点のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
