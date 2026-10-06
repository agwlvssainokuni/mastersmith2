# 性能の設計 — U7 app-frame-ui

## 出典

- この単位の承認済みの NFR 要件 `construction/app-frame-ui/nfr-requirements/`:
  - `performance-requirements.md`: NFR2.2・NFR2.5〜NFR2.12。承認の場の直し R-02 で、判定は 5 回の中央値になった。
  - `security-requirements.md`: NFR1.1・NFR1.4・NFR1.6〜NFR1.10。
  - `tech-stack-decisions.md`: NFR4.1〜NFR4.5・NFR6.1〜NFR6.8。
- ui の単位は scalability・reliability・observability の要件を作らない（NFR 要件の段の `produces_kinds`）。そのため、段の定義が必須とするそれらの入力は無く、上の3つを入力にする。この段も scalability・reliability・observability の設計を作らない。
- 承認済みの機能設計 `construction/app-frame-ui/functional-design/functional-spec.md`（D4・D7・D10〜D21・W1〜W9）と `frontend-components.md`。
- `inception/contract-design/contract-summary.md`（C8・C9）と `inception/domain-design/components.md`（AppFrame・TablePlaceholderUi・MyPermissionsUi）。
- この段の答え: `nfr-design-questions.md` の Q1 A（時計は画面の中の `performance.now()` の1つ）・Q2 A（NFR 要件の読み直しの R-04・R-05・R-07〜R-09 をこの段の設計に入れる）。まとめの確認は Looks correct。
- 統合の点（読み取りだけ）:
  - `construction/role-admin-ui/nfr-design/`（U6 の検査 140・F の流れ 150）と、その読み直しの R-01・R-02（測り始めと時計）。
  - `construction/navigation/nfr-design/logical-components.md`（メニューと置き場の API）。

## 1. API の時間と要求の数（NFR2.2）

- API の時間は U4・U5 の目標（どれも p95 1 秒）をそのまま当て、この単位では測らない。
- 画面が送る要求の数を、次のとおり決めて画面のテストで数える。
  - 画面の移動1回で、作業ロールと業務のメニューの2本。
  - 作業ロールの切り替え1回で、`PUT` 1本と読み直しの2本。
  - 置き場の画面は、開いたとき・引数が変わったとき・作業ロールが変わったときに1本。
- 同じ描画の中で重なったきっかけ（例: 切り替えの成功と、それに伴う置き場の問い直し）は、マイクロタスクの終わりまで待って1回にまとめる（`useReloadTriggers`、`logical-components.md` 1節 L4）。
- `/logout` の道では読み直さない（`security-design.md` 5節）。

## 2. 読み直しの形（NFR2.9）

- `WorkRoleProvider`・`BusinessNavigationProvider`・`useTableAccess`・`useMyPermissions` は、どれも要求の世代（数）を持ち、送るたびに1つ進める。
- 答えが届いたとき、その要求の世代が今の世代でなければ捨てる。部品が外れた後の答えも捨てる。
- 読み直しの間は今の表示を残し、状態だけを `reloading` にする。業務の区画は消さない。
- 決まった間隔の自動の読み直し（ポーリング）は置かない。画面の側で要求に独自の時間切れも置かない（既存の ApiClient のまま）。
- 確かめ: 各フックのテストで、遅らせた古い応答が新しい表示を上書きしないことと、読み直しの間に業務の区画の項目が DOM に残ることを、`waitFor` で待って確かめる。

## 3. 切り替えの二重の送信（NFR2.11）

- `useWorkRole()` の `switchTo` は、送信の最中を ref（`switching`）で持ち、最中の呼び出しを何もせずに捨てる。
- `WorkRoleSwitcher` は、`status` が `switching` の間、Dropdown を描かず、同じ見た目の押せないボタン（`aria-disabled="true"`、押下を無視）を描く。
  - フォーカスは差し替えの前後で同じ位置に残す（差し替えの後のボタンへ移す）。
  - e82b651 の Dropdown はトリガーの `onClick` を上書きするため、トリガーに `aria-disabled` を付けるだけでは一覧が開く。
- 確かめ: `WorkRoleSwitcher.test.tsx` で、待ちの間にトリガーを押しても一覧が開かず、2本目の `PUT` が送られず、フォーカスがボタンにある。

## 4. 画面の時間の測り（NFR2.5〜NFR2.8・NFR2.12、Q1 A）

### 4.1 置き場と見本

- 測りのテストは U7 の検査のファイル（160、`logical-components.md` 5節）の中に1件置く。
  - 表示の設定の 20 組の繰り返しの外で、既定の1組（テーマ light・文字の大きさ md・ブランドカラー blue・既定の幅）だけで動かす。
  - E2E の本数に数えない。
- API は U7 の差し替えの口（`e2e/support/frameApiRoute.ts`、L13）で、遅れなく返す。
  - 見本の本文は、テストの準備で前もって `JSON.stringify` した文字列にし、口はその文字列を `route.fulfill` の `body` に渡すだけにする。測りの中に直列化の時間を入れない（U6 の読み直しの R-01）。
  - 口は状態を1つ持つ: `PUT /api/me/work-role` を受けたら 204 を返し、それより後の `GET /api/me/work-role`・`GET /api/me/navigation` には切り替えの後の見本（`current` が新しいロール、業務の木に切り替えの後にだけある印の項目）を返す。NFR2.5 の測り終わりの「新しい木」は、この印の項目で見分ける。
- 見本の木（NFR2.12）は `e2e/support/navTreeFixtures.ts` の生成の関数で作り、型は C9 の `NavigationResponse` で縛る。
  - 目安の木: 100 項目・深さ 1。
  - 悪い側の木: 1,000 項目・深さ 5 段・40 文字の名前を含む。末端までの1本の枝を持つ。

### 4.2 時計と聞き手（Q1 A、承認の場の直し R-03。U6 の読み直しの R-01・R-02 を避ける）

- 時計は画面の中の `performance.now()` の1つだけを使う。テストの側の時計と組にしない。
- 聞き手と見張りは、測りのテストの中で `page.evaluate` を使って入れる。アプリのコードには入れない。
- 手順（1回の測りごと）:
  1. 測る操作の前の準備を済ませる（NFR2.5 なら作業ロールの一覧を開く。NFR2.6 なら4段目までの祖先を開いた状態にする）。
  2. **測る操作の直前に**、聞き手と見張りを入れる（準備の押下を拾わないため）。
  3. 測る操作を1回行う。
  4. テストの側は、記録が揃うのを `expect.poll` で待ち、差を読んで添付に記録する。
- 測り始め: `document` に `click` の捕捉の段の聞き手を置く。押された要素が目当ての要素（`event.target.closest(startSelector)` が一致）のときだけ時刻を記録し、ほかの押下は記録しない。ボタンの動作は `click` で起きるため、押し下げと離しの間の往復は入らない。
- 測り終わり: `MutationObserver` で、テストが関数の本体の文字列ではなく**条件の名前**で選ぶ判定（下の表）が真になったのを見つけ、その後の次の `requestAnimationFrame` の呼び出しの時刻（次の描画の直前）を記録する。判定は見張りの中に用意した決まった関数だけで、アプリのコードには入れない。

説明用の断片（画面の中に入れる見張りの形だけ）:

```ts
await page.evaluate(({ startSelector, done }) => {
  const mark: { start?: number; end?: number } = {}
  ;(window as unknown as { __u7Mark: typeof mark }).__u7Mark = mark
  document.addEventListener('click', (event) => {
    const target = event.target as Element | null
    if (mark.start === undefined && target?.closest(startSelector)) mark.start = performance.now()
  }, { capture: true })
  const observer = new MutationObserver(() => {
    if (mark.start === undefined || !(window as unknown as { __u7Done: Record<string, () => boolean> }).__u7Done[done]()) return
    observer.disconnect()
    requestAnimationFrame(() => { mark.end = performance.now() })
  })
  observer.observe(document.body, { childList: true, subtree: true, characterData: true })
}, { startSelector, done })
```

- `__u7Done` は、この `page.evaluate` の前に別の `page.evaluate` で `window` に入れる決まった判定の表（名前と関数）で、次の3つを持つ。
  - `workRoleSwitched`: Toast の入れ物（`toast-container`）の文字に、見本の切り替えの後のロールの名前を含む切り替えの文があり、かつ業務の区画に印の項目（`sidebar-nav-biz:<印の id>`）がある。
  - `groupOpened`: 目当ての開閉のボタンの `aria-controls` の id の `ul` に、最初の項目がある。
  - `placeholderShown`: 置き場の画面の h1 の文字が、見本の表示名と一致する。

### 4.3 測る項目

| ID | 操作（測り始め） | 測り終わり（目当ての要素） | 見本 | 目標 |
|---|---|---|---|---|
| NFR2.5 | 開いた作業ロールの一覧で、別のロールの項目（`role="menuitem"`）を押す | `workRoleSwitched` が真になった時点（Toast の切り替えの文と、業務の区画の印の項目の両方が揃う） | 目安の木（判定）、悪い側の木（記録だけ） | 0.5 秒 |
| NFR2.6 | 悪い側の木の4段目のまとまりの開閉のボタン（`sidebar-toggle-<id>`）を押す | `groupOpened` が真になった時点 | 悪い側の木 | 0.2 秒 |
| NFR2.7 | 悪い側の木の5段目の末端の項目（`sidebar-nav-<id>`）を押す | `placeholderShown` が真になった時点 | 悪い側の木と置き場の見本 | 0.5 秒 |

- NFR2.5 の測り終わりは2つが揃った時点（`workRoleSwitched`）。Toast は既定 4 秒で消えるため、見張りは押す直前に入れておく（4.2 の手順の 2）。
- 5bf1ffe の Sidebar は、開いたまとまりにだけ子の `ul` を描く（`logical-components.md` 2節）。NFR2.6 の前提として、4段目までの祖先は開いた状態で始める。

### 4.4 判定と記録（NFR2.8、承認の場の決定）

- 各項目を 5 回測る。1回ごとに画面を読み込み直し、見張りを入れ直す。
- 判定は 5 回の中央値（小さい順の3番目）で行う。
  - 中央値が目標以内なら Met。最大だけが目標を超えたときも Met とし、最大を記録する。
  - 中央値が目標を超えたら Not Met。
  - 5 回のうち測れない回（記録が揃わない・テストの失敗）があれば、その項目は Unverified とし、理由を記録する。
- 添付には、項目ごとの 5 回の値・中央値・最大・1回目と、使った時計（画面の中の `performance.now()`）を書く。名前と時間だけを書き、氏名・メールアドレスを書かない。
- 値は記録だけで、テストの成否にしない。Not Met のときは、Build and Test が値と Not Met を記録し、承認の場で依頼者が扱いを決める。目標を緩めて満たしたことにはしない。

## 5. 初回の JavaScript（NFR2.10）

- 骨組みの新しい部品（`app/work-role`・`app/navigation`）は入口に入る。B9 の前と後で `frontend/scripts/check-bundle-size.mjs` の入口の値（gzip、目安 500KB、超えたら警告だけ）を測って記録する。
- `features/tables`・`features/mypermissions`・`LogoutPage` は `lazy` で登録し、入口に入れない。ビルドの結果（Vite の manifest）からそれぞれの chunk の gzip の大きさを読み、前と後の記録に並べる。
- 確かめ: 3つの画面の登録のテストで `lazy` であること。記録は code-summary に書く。

## 6. 受け入れた制約

- 画面の時間は手元の PC の1台で、既定の1組だけで測る。値は記録だけで統合を止めない。
- 画面の中の時計は、利用者の操作（`click`）を画面が受けた時点から、目当ての要素が揃った後の次の描画の直前（`requestAnimationFrame` の呼び出しの時点）までを測る。Playwright の待ちと往復は入らないが、ブラウザがイベントを受けるまでの OS の遅れも入らない。
- 画面の移動のたびの2本の要求は、U5 の見積もり（毎秒 10 要求ほど）と U4・U5 の k6 で押さえる。画面の側は数だけを確かめる。

## 7. 上流との差

| # | 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|---|
| (a) | `performance-requirements.md` の NFR2.8 | 測り始めは「操作（押す・選ぶ）を送った時点」 | 目当ての要素の `click` を画面が受けた時点（捕捉の段）を、画面の中の `performance.now()` で取る | 時計を画面の中の1つに固定するため（Q1 A）。テストの側から送った時点は画面の時計と組にできない |
| (b) | U6 の NFR 設計（`role-admin-ui/nfr-design/performance-design.md` の4節） | 時計はテストの側の `Date.now()` の差、または画面の中の `performance.now()` | U7 は画面の中の `performance.now()` だけを使う | 0.2 秒の目標を、Playwright の待ちと往復に左右されずに測るため（Q1 A）。U6 の設計は書き換えない |
| (c) | NFR 要件の読み直しの R-09（直していない Suggestion） | 20 組 × 約 10 の状態の axe の時間の見積もりが無い | 160 は組ごとに1つのテストにし、計画で1組あたりの時間を見積もる。全体が 10 分を超える見込みなら、状態の群で2つのファイルに分ける（`logical-components.md` 5節） | Q2 A |

## 8. 承認の場の決定と直し

- 決定: 依頼者は NFR 設計の承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」（直す範囲: 各単位の読み直しの Major と、単位の間でそろえる3点）。レビューの記録は `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-design/units/app-frame-ui/6f07949191bc1397/1.json`。
- **R-03（Minor。単位の間で測り方をそろえるため直す）**: 測り始めの聞き手が目当ての操作に結び付かず、前の押下を拾いえた。4.2 を次のとおり一意に書き直した。role-admin-ui もこの測り方にそろえる。
  - 聞き手は**測る操作の直前に**入れる（準備の押下を拾わない）。
  - 測り始めは `click` の捕捉の段で、押された要素が目当て（`closest(startSelector)`）のときだけ記録する（`once` と `??=` で最初の押下を拾う形をやめた）。押し下げと離しの往復は入らない。
  - 測り終わりは決まった判定の名前（`workRoleSwitched`・`groupOpened`・`placeholderShown`）で選び、NFR2.5 は Toast の文と業務の区画の印の項目の2つが揃った時点にした。
  - 差し替えの口（L13）に、`PUT` の後は切り替えの後の見本を返す状態を足した（4.1）。
  - 測り終わりは「次の描画の直前（`requestAnimationFrame` の呼び出しの時点）」と書き改めた（6節）。
- R-01・R-02 の直しは `security-design.md` の同じ名前の節に書いた。
