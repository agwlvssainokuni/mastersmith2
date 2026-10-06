# セキュリティの設計 — U7 app-frame-ui

## 出典

- この単位の承認済みの NFR 要件 `construction/app-frame-ui/nfr-requirements/` の `security-requirements.md`（NFR1.1・NFR1.4・NFR1.6〜NFR1.10、承認の場の直し R-01）・`performance-requirements.md`・`tech-stack-decisions.md`。
- ui の単位は scalability・reliability・observability の要件を作らない（NFR 要件の段の `produces_kinds`）。そのため、段の定義が必須とするそれらの入力は無く、上の3つを入力にする。
- NFR 要件の読み直しの記録 `.aidlc-reviews/nfr-requirements/units/app-frame-ui/0921d0fe911cb972/1.json`。直していない R-04・R-07・R-10・R-11 を、この段で設計に入れた（Q2 A と「決まっていること」）。
- 承認済みの機能設計 `functional-spec.md`（D1・D3・D11・D14・D16・D21・D27）と `frontend-components.md`（3節・4節・8節）。
- `contract-summary.md`（C8・C9、共通の決まり）と `components.md`（AppFrame）。
- コードの前例: `frontend/src/features/registration/useRegistration.ts`（StrictMode の二重の描画の扱い）、`frontend/src/main.tsx`（StrictMode）、`frontend/playwright-secret-check-reporter.ts`、`frontend/e2e/support/secretValues.ts`。
- この段の答え: Q1 A・Q2 A。まとめの確認は Looks correct。

## 1. 判定はサーバー（NFR1.1）

| 画面・部品 | 画面がすること | 判定の持ち主 |
|---|---|---|
| 管理の区画（サイドバー） | ログイン状態の `admin` で出し分ける（見せ方だけ） | 管理の API（既存の管理者の印の決まり） |
| 業務の区画（サイドバー） | C9 の応答の木をそのまま写す。画面で権限を足さない・項目を増やさない | U5（作業ロールで絞る） |
| 置き場（S2） | 開くたびに `GET /api/me/table-access` を送り、200 のときだけ表示名を出す。403・400・引数なしは同じ権限なしの表示 | U5（解決の口で判定） |
| 自分の権限（S8） | 応答をそのまま描く | U4 |

- 置き場の権限なしの表示は、表示名・テーブルの名前を DOM に入れない。道の引数のテーブルの名前も画面に写さない（テーブルの有無を見分けられない。AC5.1.4）。
- 確かめ: `buildNavSections.test.ts`（`admin` が偽なら管理の区画が無い）、`TablePlaceholderPage.test.tsx`（3つの拒否で同じ DOM、メニューに無い組でも問い合わせを送る）。サーバーの 401・403・200 の表は U3〜U5。

## 2. 送る項目（NFR1.4）

- API の関数は、各部品の `*Api.ts` の1か所でだけ要求を作る（`logical-components.md` 1節 L1・L2・L9・L10）。
  - `PUT /api/me/work-role` の本文は `{ roleId }` だけ。型で他の項目を持てなくする。
  - 置き場の問い合わせは `URLSearchParams` に `schema`・`table` だけを入れる。画面の道の `item` は送らない。
  - 自分の権限は role の確定の形（`/api/me/permissions/schemas`・`tables?schema=…`・`columns?schema=…&table=…`）で、引数は名前だけ。
  - 業務のメニュー・作業ロールの読み取りは引数を付けない。
- どの要求にも利用者を指す値を入れない。主体はサーバーが要求の文脈から読む。
- 確かめ: 各 `*Api.test.ts` で、要求の差し替えから道・引数・本文を読み、上の形だけであることを確かめる。

## 3. 画面の外へ出さない値（NFR1.6、Q2 A の R-04）

- コンソール: 応答・要求の値を `console` に出さない。失敗は種類（`kind`・`status`・`code`）だけで扱う。
- ブラウザの保存: `sessionStorage` の1つの鍵（`mastersmith.nav.expanded`）に、開閉の状態の項目の `id`（`home`・`biz:<位置の道>`・`adm:<登録の id>`）の配列だけを置く。名前・氏名・トークンは置かない。
- **ログアウトで開閉の保存を消す**（R-04）: 次の2つのときに `clearExpanded()` を呼び、骨組みの開閉の状態も空にする。
  - ログアウトの画面で `logout()` を呼ぶとき（5節）。
  - ログイン状態の知らせで未ログインになったとき（トークンの更新の失敗などを含む）。
- 道: 置き場の道の引数は DSL の定義のスキーマ名・テーブル名と、位置の道（`item`）だけ。接続情報は入らない。
- 確かめ:
  - `navExpansion.test.ts`: 保存した値が `id` の配列だけ。壊れた値・保存の例外の扱い。
  - `WorkRoleProvider.test.tsx`・`BusinessNavigationProvider.test.tsx`: 未ログインの知らせで開閉の保存が空になる。
  - 画面のテストで `console.error`・`console.log` を見張り、値が出ない。
  - I の流れの E2E（170）: ログアウトの後に `sessionStorage` の鍵が無い（`logical-components.md` 6節）。

## 4. 信頼できない入力（NFR1.7）

- 名前（`label`・`displayName`・ロールの名前）は React の文字として描く。make-you-chic-ui の Sidebar も `label` を文字として描き、`aria-label` と `title` に入れるだけ（5bf1ffe のソースで確かめた。`logical-components.md` 2節）。
- アイコンは `app/registry` の許した名前の一覧で照らし、一覧に無い・空・大文字違いは `list`（`buildNavSections` の中の1か所）。
- 置き場の道は `tablePlaceholderPath.toTablePlaceholderPath` の1か所だけで作る。`URLSearchParams` で値をエンコードし、先頭は常に `/tables?`。ほかの場所で道を文字列の連結で作らない。
- 確かめ: `<script>`・`<img onerror>`・`&`・`"`・`'` を含む名前の画面のテスト。`buildNavSections.property.test.ts` の性質 2・3。`tablePlaceholderPath.test.ts` の往復（`/`・`..`・`?`・`#`・`%`・空白・`&`・`=`・`javascript:`・`https://` を含む名前）。

## 5. ログアウトの印（NFR1.8、読み直しの R-10・R-11）

### 5.1 印の持ち方

- 印は `features/auth/logoutIntent.ts` のメモリ上の1つの値（真偽）。履歴の `state`・ブラウザの保存・URL には置かない。
- 口は3つ: `markLogoutIntent()`（立てる）・`peekLogoutIntent()`（消さずに読む）・`consumeLogoutIntent()`（読んで消す）。
- ユーザーメニューの「ログアウト」は `path: '/logout'` の項目にし、承認の場の直し R-01 で登録の型に足す任意の口 `beforeNavigate`（移る直前に呼ぶ関数）に `markLogoutIntent` を渡す。骨組み（`ShellLayout`）は、`path` の項目を選んだとき、既定の移動を止めてから `beforeNavigate` があれば呼び、続けてルーターで移る。型と検査の変更は `logical-components.md` 3節。
- 印を消すとき:
  - ログアウトの画面が印を使ったとき（5.2）。
  - S4 の未保存の確かめで［留まる］を選んだとき（U6 の `useBlocker` の `reset()` の直後に、骨組みの口 `useClearLogoutIntent()` が返す関数を呼ぶ。口の経路は `logical-components.md` 3節）。
  - `/logout` 以外の画面の道が描かれたとき（`AppRouter` の効果。修飾キーつきのクリックで別のタブに開いた・移動が中止された、などで印が残った場合の消し方）。

### 5.2 ログアウトの画面と StrictMode（`useRegistration.ts` の前例に合わせる）

- 描画の中では印を消さずに読むだけにする（状態の初期値の関数で `peekLogoutIntent()`）。StrictMode の二度の描画でも、どちらも同じ値を読む。
- 描画の確定の後の効果の中で、ref（`handled`）で1回に守って次を行う。StrictMode の作り直しでは ref が残るため、2回目の効果は何もしない。
  - 印があった: `consumeLogoutIntent()`・`clearExpanded()`・`logout()` を1回。終わった後の移動は振り分けに任せる（未ログインの LOGGED_IN の画面はログインの画面へ）。
  - 印が無かった: ログアウトせずに、ホームへ `replace` で1回だけ移る。

説明用の断片（形だけ。`logout` は同じ機能の `./authSession`、`clearExpanded` は `app/navigation/navExpansion`、印の口は `./logoutIntent` から読む）:

```tsx
export function LogoutPage() {
  const [intended] = useState(peekLogoutIntent)
  const handled = useRef(false)
  const navigate = useNavigate()
  useEffect(() => {
    if (handled.current) return
    handled.current = true
    if (!intended) { void navigate('/', { replace: true }); return }
    consumeLogoutIntent()
    clearExpanded()
    void logout()
  }, [intended, navigate])
  return <p role="status">{/* ログアウトしています */}</p>
}
```

- `/logout` の道では、作業ロールと業務のメニューを読み直さない（`useReloadTriggers` が道を見て除く）。

### 5.3 確かめ

- `LogoutPage.test.tsx` は StrictMode で包んで描く。
  - 印あり: `logout()` が1回、印が消え、ホームへの移動が起きない。
  - 印なし: `logout()` が呼ばれず、ホームへの `replace` が1回。
  - 同じ部品を描き直しても、2回目は何もしない。
- `logoutIntent.test.ts`: 3つの口と、［留まる］の後・別の道の描画の後に印が残らない。
- I の流れの E2E（170）の最後の手順（流れの本数は増やさない。`logical-components.md` 6節）:
  1. ログインしたまま `/logout` を直接開くと、ホームに戻り、ログインしたまま。
  2. メニューからログアウトを選ぶ。この時点から、ログアウトの要求（`POST /api/auth/session/logout`）を Playwright の要求の見張りで数え始める。
  3. ログインの画面が出るのを待つ。
  4. ブラウザの戻るを行い、ログインの画面のままであることを確かめる。続けて再読み込みを行い、同じことを確かめる。
  5. 2 から最後までの要求の数が1であることを確かめる。

## 6. E2E の報告（NFR1.9、Q2 A の R-07）

- 仮の資格情報はプロセスの環境変数で渡し、`webServer.env` に置かない（既存の `playwright.config.ts` の形）。
- 報告の値の確かめは、既存の `playwright-secret-check-reporter.ts` を使い回す（すべての結果を探すため、160・170 にもそのまま効く）。
- **書き忘れを失敗にする**（R-07）: I の流れは、作った利用者のメールアドレス・パスワード・氏名を `secretValues.ts` の口で書いた後、同じ口で読み戻して3つが揃っていることを確かめ、揃わなければ流れの始めで失敗にする。
- 測りのテスト（`performance-design.md` 4節）の添付には名前と時間だけを書く。

## 7. 静的検査（NFR1.10）

- oxlint の `react/no-danger`・`no-script-url`、ESLint の `no-implied-eval`・`export default` と `enum` の禁止・機能どうしの import の制限（U1）を、新しい部品にもそのまま当てる。
- `features/tables`・`features/mypermissions`・`features/auth` は互いに import しない。骨組みの口（`app/work-role`・`app/navigation`）と `shared/` だけを読む。
- `features/auth` のログアウトの印を消す口（`clearLogoutIntent`）は、登録の型の `LoginStateProvider` の任意の口として渡し、骨組みは `LoginStateGate` の文脈から読む（`logical-components.md` 3節。`LoginStateGate` の変更を含む）。骨組みから `features/auth` を直接 import しない。`LogoutPage` は `features/auth` の中の部品なので、同じ機能の `authSession.logout` を直接呼ぶ。
- 確かめ: `./gradlew verify` の中のリンタの段。

## 8. 上流との差

| # | 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|---|
| (a) | `security-requirements.md` の NFR1.6（読み直しの R-04） | ログアウトの後の開閉の保存の扱いが要件に無い | ログアウトの画面と未ログインの知らせで `clearExpanded()` を呼ぶ（3節） | 機能設計 D16 の「ログアウトで捨てる」を要件の確かめにつなぐ（Q2 A） |
| (b) | `security-requirements.md` の NFR1.8（読み直しの R-10・R-11） | 印を読む場所と1回にする手段、印が残った場合の消し方、E2E の数える範囲が無い | 描画の中は読むだけ、効果の中で ref で1回、別の道の描画でも消す。数えるのはメニューのログアウトの直後から（5節） | StrictMode の二重の描画で振る舞いを一意にするため（`useRegistration.ts` の前例） |
| (c) | `security-requirements.md` の NFR1.9（読み直しの R-07） | I の流れの値を書き忘れても報告の部品が通る | 書いた後に読み戻して揃わなければ失敗にする（6節） | Q2 A |
| (d) | 機能設計 `frontend-components.md` 8節 | ログアウトの画面は `useRef` の印で1回だけ `useLogout()` を呼ぶ | 印は `features/auth` のモジュールの値、ref は1回に守るためだけに使う。`LogoutPage` は同じ機能の `authSession.logout` を直接呼ぶ。印が無いときはホームへ `replace` | NFR 要件の承認の場の直し R-01（履歴に残る `state` をやめた）と、この段の承認の場の直し R-02（`useLogout` は今のコードに無い） |
| (e) | 機能設計 `frontend-components.md` 8節・今の `UserMenuItemRegistration` | ログアウトの項目を path に替えて印を付ける（型の口は書かれていない） | `path` の項目に任意の `beforeNavigate` を足し、骨組みが移る直前に呼ぶ。ログアウトの項目は `beforeNavigate: markLogoutIntent`（5.1、`logical-components.md` 3節） | 今の型は `action` と `path` が排他で、`path` の項目に移る直前の口が無い（承認の場の直し R-01） |

## 9. 承認の場の決定と直し

- 決定: 依頼者は NFR 設計の承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」（直す範囲: 各単位の読み直しの Major と、単位の間でそろえる3点）。レビューの記録は `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-design/units/app-frame-ui/6f07949191bc1397/1.json`。
- **R-01（Major）**: ログアウトの印を立てる経路が、今の `UserMenuItemRegistration`（`action` と `path` が排他で、`path` の項目に移る直前の口が無い）では成り立たなかった。`path` の項目に任意の `beforeNavigate` を足し、骨組みが移る直前に呼ぶ形にした（5.1・8節 (e)、`logical-components.md` 3節・L15・8節 (e)）。`validateRegistrations` の検査と既存の登録のテストへの影響も書いた。
- **R-02（Major）**: 前例とした `useLogout` と「今の logout と同じ形」は今のコードに無かった（`useLogout` は cross-cutting の B2 で入る設計）。`LogoutPage` は `authSession.logout` を直接呼ぶ形に直し、骨組みが `clearLogoutIntent` を読む経路（`LoginStateProvider` の任意の口・`LoginStateGate` の `useClearLogoutIntent()`・`AppRouter` での呼び出し）を設計に載せた（5.1・5.2・7節、`logical-components.md` 3節・L15）。
- R-03（測り方）の直しは `performance-design.md` の同じ名前の節に書いた。ほかの指摘（R-04〜R-08）は直さない。
