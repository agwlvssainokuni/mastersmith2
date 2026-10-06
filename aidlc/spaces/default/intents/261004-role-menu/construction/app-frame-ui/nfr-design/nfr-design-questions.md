# NFR 設計の質問 — U7 app-frame-ui

対象の単位: U7 app-frame-ui（kind: ui、作るのは B9）。作る成果物は `performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json` の4つです（段の定義の `produces_kinds`。scalability・reliability・observability の設計は ui の単位に作らない）。段の定義が必須とする scalability・reliability・observability の要件は ui の単位には無いため（NFR 要件の段の `produces_kinds`）、承認済みの `performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md` を入力にします。

読んだもの:

- この単位の承認済みの NFR 要件 `construction/app-frame-ui/nfr-requirements/`（3つの文書と `traceability.json`）。
  - 承認の場の決定: Major 11 件だけを直す。画面の時間は 5 回の中央値で判定し、最大だけが超えたときは Met として最大を記録する。
  - NFR 要件の読み直しの記録 `.aidlc-reviews/nfr-requirements/units/app-frame-ui/0921d0fe911cb972/1.json`（直していない指摘 R-04〜R-11）。
- 承認済みの機能設計 `construction/app-frame-ui/functional-design/` の `functional-spec.md`（D1〜D30、W1.1〜W9.1、6節、11節）と `frontend-components.md`。
- `inception/contract-design/contract-summary.md`（C2・C8・C9）と `inception/domain-design/components.md`（AppFrame・MyPermissionsUi・TablePlaceholderUi）。
- 統合の点として読み取りだけで読んだもの:
  - `construction/role-admin-ui/nfr-design/performance-design.md`・`logical-components.md`（U6 の検査 140・F の流れ 150 の案、`E2EF_` の接頭辞、`main.tsx` の data router への差し替え、画面の時間の測り方）。
  - その読み直しの記録 `.aidlc-reviews/nfr-design/units/role-admin-ui/6f07949191bc1397/1.json`（R-01: 測り始めを `route.fulfill` の直前にする設計は、今の `adminApiRoute.ts` では取れない。R-02: 時計はテスト側の1つに固定する）。
  - `construction/navigation/nfr-design/logical-components.md`（メニュー・置き場の API と引数の扱い）。
- コードと部品（読み取りだけ）:
  - `frontend/src/main.tsx`（StrictMode）。
  - `frontend/src/features/registration/useRegistration.ts`（StrictMode の二重の描画の扱いの前例: 状態の初期値の関数で読み、確定の後に消す。ref で一度に守る）。
  - `frontend/e2e/support/adminApiRoute.ts`（見本の関数の後に `JSON.stringify` してから `route.fulfill`）。
  - `vendor/make-you-chic-ui` の Toast（入れ物が `role="status"`・`aria-live="polite"`・`data-testid="toast-container"`、既定 4 秒で消える）。
  - 上流の make-you-chic-ui の 5bf1ffe（`git show` の読み取りだけ）の `Sidebar.tsx`。開いたまとまりにだけ子の `ul`（id は `aria-controls` と同じ）を描く。開閉のボタンは `sidebar-toggle-<id>`、項目は `sidebar-nav-<id>`。

質問は 2 問です。

---

## 決まっていること

### 入力と ID

- 設計の ID は、承認済みの要件の ID（NFR1.1・NFR1.4・NFR1.6〜NFR1.10・NFR2.2・NFR2.5〜NFR2.12・NFR4.1〜NFR4.5・NFR6.1〜NFR6.8）を、そのまま設計の解の行に付ける。
- `traceability.json` は要件の `traceability.json` の上流の枝番と同じ並びで、各行の target に、この段の文書の実在する節を書く（U6 の読み直しの R-03 を避ける）。
- 論理の部品は `logical-components.md` に L1〜 の番号で置く。
  - 骨組み: `app/work-role`・`app/navigation`（`buildNavSections`・`tablePlaceholderPath`・`navExpansion`・`useReloadTriggers`）・`ShellLayout`・`HomePage`。
  - 機能: `features/tables`・`features/mypermissions`・`features/auth` の `LogoutPage` とログアウトの印。
  - E2E: `e2e/support` の新しい補助（`userMenu.ts`・見本の木の生成・U7 の API の差し替え）と、2つの E2E のファイル。

### E2E の番号（U6 の 140・150 と重ねない。NFR 要件の読み直しの R-06）

- 既存は 010〜130。U6 が U6 の検査に 140、F の流れに 150 を使う案（role-admin-ui の NFR 設計の L13）。
- U7 は、U7 の検査（本数に数えない）に **160**、I の流れに **170** を使う。
  - I の流れは、DSL を自分で適用するほかのファイル（040・150）より後に流れる。
  - U7 の検査（160）は API を見本で返すため、DSL の状態に頼らない。
  - 計画の最初の手順で、`frontend/e2e/` の番号と U6 の案を突き合わせて確定する（U6 の読み直しの R-08）。
- I の流れの見本の DSL のスキーマ・テーブル・メニューの名前には接頭辞 `E2EI_` を付け、F の流れの `E2EF_` と重ねない。

### 画面の時間の測り方（U6 の読み直しの R-01・R-02 を避ける）

- 測り始めを `route.fulfill` の直前に置かない。U7 の目標（NFR2.5〜NFR2.7）はどれも利用者の操作から始まるため、測り始めは操作の時点にする（時計の置き場は Q1）。
- 時計は1つに固定する。測り始めと測り終わりで別の原点の時計を組にしない（R-02）。
- API の見本は遅れなく返し、本文はテストの準備で前もって文字列にしておく。測りの中に直列化の時間を入れない。
- 判定は承認の場の決定どおり、5 回の中央値（小さい順の3番目）。最大と1回目も記録する。中央値が目標を超えたら Not Met。最大だけが超えたときは Met とし、最大を記録する。測れない回があれば Unverified。値は記録だけで、統合を止めない（NFR2.8）。

### make-you-chic-ui の部品に頼る動き（5bf1ffe のソースで確かめた口）

- 子の表示: 開閉のボタンを押すと `aria-expanded` が変わり、開いたまとまりの子の `ul`（id は `aria-controls` の値）が描かれる。NFR2.6 の測り終わりは、この `ul` の最初の項目が見えた時点にする。
- 切り替えの結果: Toast の入れ物（`toast-container`、`role="status"`）に文が出た時点を、NFR2.5 の測り終わりの1つにする。文は既定 4 秒で消えるため、測りと確かめは出た直後に行う。
- 畳んだ状態の `aria-labelledby` の件と、トップバーの日本語の固定の名前は、承認済みの要件の受け入れた制約のまま（追加の依頼 `make-you-chic-ui-request-2.md` が入ったら見直す）。

### `/logout` の印と StrictMode（NFR 要件の読み直しの R-10・R-11）

- 印は `features/auth` の中のメモリ上の値（NFR1.8）。
- 読み方と消し方は `useRegistration.ts` の前例に合わせる。
  - 描画の中では、印を消さずに読むだけ（状態の初期値の関数で、純粋に読む）。
  - 消すのと `logout()` の呼び出しは、描画の確定の後の効果の中で行い、ref で1回に守る。StrictMode の作り直しでは ref が残るため、2回目の効果は何もしない。
  - 印が無いと決まったときのホームへの `replace` も、同じ ref の決まりで1回だけ行う。
- 印は、S4 の確かめで［留まる］を選んだとき（`reset()`）と、`/logout` 以外の画面の道へ移ったときにも消す。
- `LogoutPage.test.tsx` は StrictMode で包み、`logout()` が1回、移動がログアウトの後の1回だけであることを確かめる。
- I の流れの確かめ（NFR1.8）では、ログアウトの要求の数を、メニューのログアウトを選んだ直後から流れの最後までで数える。ログインの画面が出るのを待ってから、戻る・再読み込みを行う。

### そのほか（承認済みの要件のまま）

- 読み上げは Toast だけ（NFR4.1）。切り替えの待ちの間は押せないボタンに差し替える（NFR2.11）。
- 古い答えは要求の世代で捨てる（NFR2.9）。描画の後に反映される値は、画面のテストで `waitFor` で待つ（`team.md`）。
- 実際のブラウザの axe の範囲・20 組・3つの幅・開いた部品が画面に収まる確かめは NFR4.4・NFR4.5。E2E の報告の値は既存の `playwright-secret-check-reporter.ts`（NFR1.9）。

---

## Q1 画面の時間（NFR2.5〜NFR2.7）を測る時計の置き場

背景: U6 は、テストの側の時計（Node の `Date.now()` の差、前の Intent の 120 と同じ形）で測る設計にしました。U7 の目標は 0.5 秒・0.2 秒・0.5 秒と短く、とくに NFR2.6（開閉のボタンを押してから子が出るまで 0.2 秒）は、テストの側で測ると次の時間が値に入ります。
- Playwright の `click` の前の待ち（見えているか・動かないかの確かめ）。
- ブラウザとテストの間の往復（数十ミリ秒ほどになりうる）。

どちらの形でも、時計は1つ（R-02）で、測り始めは `route.fulfill` ではなく操作の時点です（R-01）。

A. 時計を画面の中の `performance.now()` の1つにする（推奨: 0.2 秒の目標を Playwright の待ちと往復に左右されずに測れる。測り始めと測り終わりが同じ原点の時計で、R-02 を満たす）。
  - 測り始め: 画面の中に捕捉の段の聞き手（`pointerdown`・`keydown`）を置き、押した時刻を記録する。
  - 測り終わり: 画面の中の `MutationObserver` で、目当ての要素（子の `ul`・Toast の文・次の画面の見出し）が現れた後の次の描画の時刻（`requestAnimationFrame`）を記録する。
  - 差をテストの側で読んで記録する。聞き手と見張りは測りのテストの中だけで `page.evaluate` で入れ、アプリのコードには入れない。
B. 時計をテストの側の `Date.now()` の1つにする（U6 と前例 120 と同じ）。
  - 操作の直前から、目当ての要素が見えたこと（`expect(...).toBeVisible()`）の直後までの差を測る。Playwright の待ちと往復を含むことを記録に書く。
  - 0.2 秒の目標は、この上乗せの分だけ Not Met になりやすい。
C. A と B の両方で測り、判定は A、B は並べて記録する（測りの手間とファイルの長さが増える）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 NFR 要件の読み直しで直していない Minor・Suggestion（R-04〜R-09）を、この段の設計に入れるか

背景: NFR 要件の承認の場の決定は「Major 11 件だけを直す」で、次の指摘は要件の文書に残っています（R-06・R-10・R-11 は上の「決まっていること」で設計に入れました）。どれも目標を緩めるものではなく、設計の細部で手当てできます。
- R-04: ログアウトの後も、同じタブの `sessionStorage` に開閉の状態が残ることが要件に無い（機能設計 D16 は「ログアウトで捨てる」）。
- R-05: 幅の確かめが既定の1組だけで、文字の大きさ lg の悪い側の組が無い。
- R-07: I の流れの値を `secretValues.ts` に書き忘れても、報告の部品が通ってしまう。
- R-08: 自分の権限の応答の型が B9 の計画の前に確かめられなかったときの扱いが無い。
- R-09: 20 組 × 約 10 の状態の axe の実行時間の見積もりが無い。

A. 5件ともこの段の設計に入れ、要件の文書は書き換えずに設計の文書の「上流との差」に書く（推奨: どれも小さく、コード生成で迷う点を先に消せる。要件の目標は変えない）。
  - R-04: ログアウト（D27 の印のある移動）と未ログインの知らせで `clearExpanded()` を呼ぶ。`navExpansion.test.ts` と I の流れで、ログアウトの後に保存が空であることを確かめる。
  - R-05: 幅の確かめに、文字の大きさ lg・テーマ dark・360px の悪い側の組を1つ足す。
  - R-07: I の流れは、作った利用者の値を `secretValues.ts` に書いたことを流れの始めで確かめ、書けていなければ失敗にする。
  - R-08: 計画の前に型が確かめられなければ、S8（Should）を B9 から外すかを計画の承認で依頼者に諮る。
  - R-09: 160 のファイルは表示の設定の組ごとに1つのテストにし、1つのテストの中で状態を順にたどる。B9 の計画で1組あたりの時間を見積もり、ファイル全体が 10 分を超える見込みなら、状態の群でファイルを2つに分ける（本数には数えない）。
B. R-04 と R-07（セキュリティに関わるもの）だけを入れ、R-05・R-08・R-09 はコード生成の計画に引き継ぐ
C. どれも入れず、コード生成の計画に引き継ぐ（承認の場の決定の範囲を、この段でも守る）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（app-frame-ui の NFR 設計）:

- Q1 A: 画面の時間は、画面の中の `performance.now()` の1つの時計で、押した時刻と目当ての要素が出た時刻の両方を取って測る。見本の本文は前もって文字列にしておき、直列化の時間を測りに入れない。判定は 5 回の中央値（最大と1回目も記録）。
- Q2 A: NFR 要件の読み直しで直していない R-04（ログアウトで開閉の保存を消す）・R-05（lg・dark・360px の悪い側の組を足す）・R-07（I の流れの値を `secretValues.ts` に書けていなければ失敗）・R-08（計画の前に型が確かめられなければ S8 を外すかを依頼者に諮る）・R-09（160 は組ごとに1つのテスト、全体が 10 分を超える見込みなら2つのファイルに分ける）を設計に入れ、要件の文書は書き換えずに上流との差に書く。
- 決まっていること（E2E の番号は U7 の検査 160・I の流れ 170 の案で、計画の最初の手順で `frontend/e2e/` と U6 の案と突き合わせて確定、I の流れの見本の接頭辞 `E2EI_`、5bf1ffe の Sidebar・Toast の口、`/logout` の印は描画の中では読むだけで消すのと `logout()` は確定の後に ref で1回に守る、ログアウトの要求はメニューのログアウトの直後から数え戻る・再読み込みはログインの画面を待ってから）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
