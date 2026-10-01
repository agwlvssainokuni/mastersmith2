# Security Design — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 のセキュリティの設計です。承認済みの NFR 要件 `aidlc/spaces/default/intents/260930-user-admin/construction/u4-admin-forbidden-ui/nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.5・NFR3.1・NFR3.2・NFR9.5、残る危険 R1〜R4、承認の場の決定 R-01〜R-04 と申し送り）を満たす作りを決めます。U4 は画面の単位（種類 ui）で、サーバー側の認可と監査を持たず、サーバーのコードも変えません。性能の作りは `performance-design.md`、部品の一覧・境界・アクセシビリティと多言語・テストと関門・実際のブラウザの検査 130 の組み立ては `logical-components.md` にあります。

この段の質問 `nfr-design-questions.md` の設計の要点 1〜9 と答え（Q1 A・Q2 B・Q3 A）、まとめの確認（Looks correct）で決めました。プラットフォームの視点（配備先は開発者の PC 上のコンテナ）は `performance-design.md` 7節と `logical-components.md` 8節に重ねて書きました。

出典の略号: SR は `nfr-requirements/security-requirements.md`、PR は `nfr-requirements/performance-requirements.md`、TS は `nfr-requirements/tech-stack-decisions.md`、FS は `functional-design/functional-spec.md`（D・W・G は同じ文書の決まり・流れ・差）、FC は `functional-design/frontend-components.md`、要点 n はこの段の `nfr-design-questions.md` の設計の要点、CS は `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`、TM は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。コードのパスは `frontend/src/` の下を書きます。

## 1. 信頼の境界と守りの配置

U4 の守りの考え方は「画面の判定はサーバーの判定の代わりにしない」です。画面は、サーバーが返した 403 と、サーバーが返したログイン状態の印を、表示を決める材料としてだけ使います。

| 境界 | 入ってくるもの | 守り | 置き場 |
|---|---|---|---|
| 管理の API の応答（`/api/admin/` の下） | 403・`ACCESS_DENIED` の Problem Details | 3つの条件がそろうときだけ「権限が無い」とし、`detail` を画面に出さない | `shared/api-client/adminForbidden.ts`（2.2）、`app/admin-forbidden/AdminForbiddenView.tsx`（3節） |
| トークンの応答（ログイン・更新） | アクセストークン・管理者の印・氏名・設定 | 印はここからだけ入る。画面から印を書き換える口を作らない | 既存の `features/auth`・`LoginStateGate`（2.3） |
| 利用者の管理の画面（U5）からの呼び出し | 自分の氏名と言語 | 氏名と言語だけを当て、印・状態・テーマ・文字の大きさを変えない。ブラウザの保存は言語だけ | `app/display-settings/`（4節） |
| URL（直に打つ・ブックマーク・戻る） | 管理の画面のパス | 画面の側の印が管理者でなければ部品を作らず、管理の API を呼ばない | `app/routing/decideRoute.ts`（2.3） |
| 実際のブラウザの検査 130 | 初期管理者の資格情報・差し替えた応答 | 資格情報はプロセスの環境変数、報告に入れない、差し替えは1本の GET だけ | `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts`（5節） |

管理の API の 401・403・200 の判定と、監査の「アクセスの拒否」は、今のサーバーの動作のままです。サーバー側のテストは U3 が受け持ちます（SR の前提）。

## 2. 画面の判定とサーバーの判定（NFR1.1〜NFR1.5）

### 2.1 表示だけにする（NFR1.1）

- S6 を出すこと・管理のメニューを隠すことは表示だけです。管理の API を呼ぶかどうかの最終の判定はサーバーにあり、画面が S6 を出していなくても、サーバーは要求ごとに内部DB の印で 403 を返します。
- U4 は `backend/` のどのファイルも変えません。コード生成で、U4 の変更による `backend/` の差分が無いことを確かめて記録します（`logical-components.md` 7節）。
- 403 を受けた画面は、ShellLayout がコンテンツの領域を `AdminForbiddenView` に置き換えることで部品ごと外れます（`performance-design.md` 2.1）。部品が外れるため、一覧の行・操作のボタン・開いていた確かめと入力の Modal も一緒に消え、続けて操作できません。操作の手段を1つずつ無効にする形は取りません（漏れが出るため）。
- 確かめ: `DslAdminPage.test.tsx`・`InvitationAdminPage.test.tsx` で、確かめ・入力の Modal を開いた状態で 403 を受けると Modal が閉じて S6 になることを確かめます（FC の 7節）。

### 2.2 「権限が無い」の判定（NFR1.2）

判定は純粋な関数 `isAdminForbidden` の1か所だけで行います。各画面・Provider・振り分けは、この関数の結果だけを使い、自分で状態コードや code を見ません。

```text
// shared/api-client/adminForbidden.ts（説明用）
export function isAdminForbidden(path: string, error: unknown): boolean {
  if (typeof path !== 'string' || !path.startsWith(ADMIN_API_PREFIX)) return false
  if (typeof error !== 'object' || error === null) return false
  const e = error as { kind?: unknown; status?: unknown; code?: unknown }
  return e.kind === 'response' && e.status === FORBIDDEN_STATUS && e.code === ACCESS_DENIED
}
```

| 入力 | 結果 |
|---|---|
| パスが `/api/admin/` で始まる・状態 403・code `ACCESS_DENIED` | true（これだけ） |
| 接頭辞だけ似たパス（`/api/administrator` など、`/api/admin` の後に `/` が無い） | false（接頭辞は末尾の `/` まで含めて比べる） |
| 管理の API の外の 403（`/api/me/…` など） | false |
| code の無い・違う 403、401・404・409・500 | false |
| 通信の失敗（`kind` が `response` でない）、`null`・`undefined`・文字列・数 | false で、例外を出さない |

- 引数は `unknown` で受け、中で型を確かめます（FS の G4）。型の決めつけ（`as ApiError`）を呼び出し元に置きません。
- パスは呼び出し元の定数（各画面の API の根のパス）で、利用者の入力ではないため、正規化や復号はしません。
- 確かめ: `adminForbidden.test.ts` の例のテストと、fast-check の性質ベースのテスト（パス・状態・code・`kind` の組を作り、3つがそろうときだけ true、どの値でも例外が出ない）。失敗時の乱数の種を記録して再現できるようにします（TM の Testing Posture）。

### 2.3 管理者でない利用者と画面の側の印（NFR1.3・NFR1.5）

- 振り分け（`decideRoute`）は、`access: 'ADMIN'` の画面で、ログインしていて画面の側の印が管理者でないときに `ADMIN_FORBIDDEN` を返します。振り分けは登録された画面の部品を作らず、`AdminForbiddenView` だけを描くため、管理の API の要求は出ません。ログインしていないときは今までどおりログインの画面へ移ります（FS の D6、W2）。
- 管理の画面の URL があることは分かりますが、画面の一覧は配る JavaScript からも読めるため、新しく漏れる情報は無いとして受け入れ済みです（残る危険 R3）。
- 画面の側の印（`LoginState.admin`）は、トークンの応答（ログイン・更新）からだけ入ります。U4 は印を書き換える口を作りません。`applyOwnProfile` の口も印を引数に取らず、ログイン状態のオブジェクトを書き換えません（4節）。
- 画面の側の印はサーバーの判定に使われません。印が誤っていても、誤る向きは「表示が出ない」（S6）か「サーバーが 403 を返す」のどちらかで、権限を広げる向きの誤りは起きません。
- 確かめ: `decideRoute.test.ts`・`AppRouter.test.tsx` で、管理者でない利用者の管理の画面が S6 になり、偽物の `fetch` に管理の API が届かないことを確かめます。`displaySettingsStore.test.ts`・`DisplaySettingsProvider.test.tsx` で、`applyOwnProfile` の後もログイン状態の印が変わらないことを確かめます。

### 2.4 401・ログアウト・読み直しの失敗（NFR1.4）

| 場合 | 扱い | 置き場 |
|---|---|---|
| 401（`AUTHENTICATION_REQUIRED`） | 今の ApiClient の更新と送り直し、`onUnauthenticated` のまま変えない | `shared/api-client/apiClient.ts`（`refreshSessionOnce` を足すだけ） |
| 403 の後の管理の API の外の画面 | 使え、ログアウトされない。権限が無い URL は1つだけで、URL が変わったら捨てる | Provider（FS の D5） |
| 読み直しの失敗（リフレッシュトークンが使えない・通信の失敗） | 今のトークンの更新の失敗と同じく、`features/auth` の更新がログインの状態を消し、ログインの画面へ移る。U4 は扱いを足さない | 既存の `features/auth`（FS の D9） |
| 管理者の印を外された | リフレッシュトークンは無効にならないため、印の変更だけではログアウトにならない（決めた側の動作） | サーバー（U3） |

- 確かめ: `apiClient.test.ts` の 401 の既存のテストがそのまま通ること、`ShellLayout.test.tsx` で S6 の後もサイドバーと管理の外の画面が使えることを確かめます。

## 3. 画面に出す値とブラウザの保存（NFR3.1）

| 出すもの・書くもの | 決まり |
|---|---|
| S6 の見出し | 今の URL に登録されたサイドバーの項目の文言の鍵、無ければ共通の見出し（`forbiddenHeadingKey`、FS の D11） |
| S6 の本文 | 決まった文言「この画面を使う権限がありません」と、ホームへのアプリの中のリンク「ホームへ戻る」だけ |
| 出さないもの | 原因（管理者ではなくなった）、応答の `detail`・`title`・`instance`、利用者の値、状態コードの数 |
| コンソール | U4 の部品と関数（`isAdminForbidden`・Provider・`AdminForbiddenView`・`applyOwnProfile`）は `console` のどの関数も呼ばない。失敗の値をログに出さない |
| ブラウザの保存 | `applyOwnProfile` が書くのは言語だけ（今の `writeStoredLanguage`）。氏名・印・トークンは書かない（4節） |

- `AdminForbiddenView` は Props を持たず、失敗の値を受け取りません。受け取らないため、`detail` を出す経路がそもそもありません。
- 確かめ: `AdminForbiddenView.test.tsx`・`AdminForbiddenProvider.test.tsx` で、`detail` に目印の文字を入れた 403 でもその文字が画面に出ないこと、`console` の各関数（`log`・`info`・`warn`・`error`・`debug`）が呼ばれないことを確かめます。`DisplaySettingsProvider.test.tsx` で、反映の後に localStorage・sessionStorage のどの鍵の値にもテストの氏名が無いことを確かめます（SR の NFR3.1）。

## 4. 自分の氏名と言語の反映（NFR1.5・NFR3.1・NFR8.2、Q3 A）

### 4.1 口の決まり

`useApplyOwnProfile` が返す関数 `applyOwnProfile({ displayName, language })` は、U5 が自分の行の氏名・言語の保存に成功したときだけ呼びます（FC の 3.7）。

| 決まり | 中身 |
|---|---|
| 当てる範囲 | 上の帯の氏名と画面の言語（文言・`<html lang>`・次からの要求の `Accept-Language`）だけ |
| 変えないもの | 管理者の印・利用者の状態・テーマ・文字の大きさ。テーマと文字の大きさは「見せ方を除く当てている値」のまま保存の後の設定（`savedUser`）に写す（FS の D13、R-01） |
| ログインしていないとき | 何もしない |
| ja・en 以外の言語 | 言語を変えず、氏名だけを当てる（NFR8.2） |
| ブラウザの保存 | 言語だけを書き換える。氏名は保存しない（NFR3.1） |

### 4.2 最新のログイン状態で動かす（機能設計のレビューの R-07、Q3 A）

`applyOwnProfile` は、描いた時点のログイン状態を閉じ込めません。保存の応答を待つ間にトークンの更新が入ると、閉じ込めた古いログイン状態に結び付けて当て、新しいログイン状態ではすぐ捨てられてしまうためです。呼ばれた時点で、最新のログイン状態と、そのログイン状態で求めた当てている値を ref から読みます。

```text
// DisplaySettingsProvider の中（説明用）
const applied = decideScreenSettings({ ...input, preview: null, prefersDark: false })
const latestRef = useRef({ loginState, current: { theme: applied.theme, fontSize: applied.fontSize } })
useLayoutEffect(() => {
  latestRef.current = { loginState, current: { theme: applied.theme, fontSize: applied.fontSize } }
}) // 描画のたびに新しくする。描画の中では ref に書かない
const applyOwnProfile = useCallback((profile: OwnProfile) => {
  const { loginState: now, current } = latestRef.current
  if (!now.loggedIn) return
  applyOwnProfileFor(now, profile, current)
}, []) // 依存なし。Provider が生きている間は同じ関数
```

- ref は `useLayoutEffect`（依存なし、描画の確定ごと）で新しくします。描画の中で ref に書く形は取りません（React の描画は純粋に保つ決まり。途中で捨てられた描画の値が残らないようにするため）。
- 保存の応答は、トークンの更新による描画が確定した後の非同期の続き（約束の解決）で届くため、その時点の ref は新しいログイン状態を指します。ログイン状態の知らせから描画の確定までの間に呼ばれることは、実際の操作の順序では起きません。仮に起きても、古いログイン状態に結び付いた値は捨てられ、新しいログイン状態の（サーバーが返した）値が出るだけで、印や権限には影響しません（FS の D14 と同じ考え方）。
- `applyOwnProfile` は依存を持たない `useCallback` のため、Provider の値（`useMemo`）の依存を増やしません。U5 の画面がこれを依存に入れても描き直しや読み込みの繰り返しを起こしません。
- 直す範囲は新しい `applyOwnProfile` だけです。今の `applyUserPreferences`・`setPreview`・`setLanguage` は変えません（FC の 3.6 の「変えない」のまま、Q3 A）。同じ形の危険は残る危険 R5 として記録し、後の Intent へ申し送ります（8節）。

### 4.3 確かめ

`DisplaySettingsProvider.test.tsx` に次を足します（FC の 7節の項目に加えて）。

| 確かめること | 合否の見方 |
|---|---|
| 保存の途中でトークンの更新が入る | 描画の時点に取り出した `applyOwnProfile` を、ログイン状態の提供元が新しい状態を知らせた後に呼ぶと、上の帯の氏名と画面の言語が渡した値になる（新しいログイン状態に結び付く）。描画の後に反映される値は `waitFor` で待つ |
| 関数の同一性 | ログイン状態・テーマ・言語が変わって Provider が描き直されても、`useApplyOwnProfile` が返す関数が同じもの |
| 印を変えない | 呼んだ後もログイン状態の `admin` が変わらない（2.3） |
| ログアウトの後 | `loggedIn` が false の状態で呼ぶと何も変わらない（`LoginState` は利用者の ID を持たないため、ログアウトの後は何もしない、で足りる） |

## 5. 実際のブラウザの検査 130 の報告と資格情報（NFR3.2）

検査 130 の組み立て（2語の氏名の作り方 Q1 A、コンソールの表示の除き方 Q2 B）は `logical-components.md` 6節にあります。この節は、検査が秘密と状態を扱う決まりだけを書きます。

| 決まり | 作り |
|---|---|
| 資格情報 | 初期管理者のメールアドレスとパスワードは、既存の `support/adminLogin.ts` と同じくプロセスの環境変数から読む。`playwright.config` の `webServer.env` に置かない（PM の Testing Posture の学び） |
| 報告に入れないもの | パスワード・アクセストークン・メールアドレス・2語の氏名の値を、`test.step` の題・注記・添付・標準出力に入れない。注記に残すのは組の名前・違反の件数・はみ出しの有無・Avatar の文字の数・除いたコンソールの表示の件数だけ |
| 差し替える応答 | 管理の API では `GET /api/admin/check` の1本だけを 403・`ACCESS_DENIED` の見本に差し替える。ほかに差し替えるのは、組の値と2語の氏名を当てるログインと復元の応答（既存の `routeLoginPreferences`、本物の応答の `user` の項目だけを書き換える） |
| 状態を変える要求 | 管理の API への GET 以外の要求（POST など）を送らない。130 の中の要求の見張り（060 と同じ形、`request.method()` と URL を見る）で0件を確かめる |
| サーバーの状態 | 初期管理者の印・氏名・設定・状態を変えない。2語の氏名は本物の応答をページの中で書き換えるだけで作る（SR の承認の場の R-01） |
| 見本の 403 の `detail` | 目印の文字（個人に関する値でも秘密でもない固定の文字）を入れ、S6 の画面の文字にその目印が出ないことを確かめる（3節の実際のブラウザでの裏付け） |

- 確かめ: json の報告の検索は、既存の報告の部品 `frontend/playwright-secret-check-reporter.ts`（`playwright.config.ts` の `SECRET_ENV_NAMES` の署名鍵・初期管理者のメールアドレス・仮のパスワードが json の結果に含まれれば実行を失敗にする、値は表示しない）がそのまま受け持ちます。130 はアクセストークンをテストのコードで取り出さない（060 の `requestAdminAccessToken` のような手伝いを使わない）ため、トークンが報告に入る経路を作りません。コード生成と Build and Test で、`./gradlew e2eTest` の後にこの部品が通ったことと、2語の氏名の値が json の報告に含まれないこと（値は表示しない形で数える）を記録します。

## 6. 静的検査と CSP（NFR9.5）

- HTML を直接埋め込みません（`dangerouslySetInnerHTML` を使わない）。文言は i18next の鍵から React の文字として描きます。既存の oxlint のセキュリティ系の決まり（`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url` など）・ESLint・型検査をそのまま通します。
- 「ホームへ戻る」は react-router の `Link` で、ホームのパスはアプリの中の定数です。応答の値・今の URL・問い合わせの文字から URL を組み立てません。
- 外部への通信・外部の資源（フォント・画像・スクリプト）・埋め込みのスクリプトとスタイルを足しません。スタイルは部品と同じ場所の素の CSS ファイルです（TM の Code Style）。
- CSP（`backend/src/main/resources/application.yaml` の `content-security-policy`）を変えません。130 のためにアプリの CSP を緩めません。コード生成で `application.yaml` に差分が無いことを確かめます。
- 確かめ: `./gradlew verify` のリンタと型検査。Build and Test の 130 で、S6 の画面で CSP の違反が0件であること（共有の `watchPage` の `cspViolations`）を記録します。

## 7. 脅威と扱い

| 脅威 | 扱い | 節 |
|---|---|---|
| 画面でメニューを隠すだけで、管理者でない人が管理の API を呼べる | サーバーが要求ごとに判定する（U3 のサーバー側のテスト）。U4 はサーバーを変えない | 2.1 |
| 印を外された管理者の画面に、一覧・操作・開いていた Modal が残る | 403 を受けた描画で部品ごと外す | 2.1 |
| 判定の条件が広すぎて、使える画面を失う | 3つの条件がそろうときだけ true、性質ベースのテストで網羅 | 2.2 |
| 管理者でない利用者が管理の画面の URL を開き、行を見る | 部品を作らず API を呼ばない。サーバーも 403 | 2.3 |
| 自分の氏名と言語の反映の口から、画面の側の印が書き換わる | 口は印を引数に取らず、ログイン状態を書き換えない | 2.3・4.1 |
| 403 の読み直しで 401 の扱いが壊れる・ログアウトされる | 401 とログアウトを変えない。読み直しの失敗は今の更新の失敗と同じ | 2.4 |
| S6 に原因・`detail`・内部の情報が出る | `AdminForbiddenView` は失敗の値を受け取らない | 3節 |
| 共用の PC で、自分の氏名がブラウザの保存に残る | 言語だけを保存する | 3節・4.1 |
| 古いログイン状態に結び付けて氏名と言語を当て、反映が消える | `applyOwnProfile` は呼ばれた時点の最新のログイン状態を読む | 4.2 |
| E2E の報告に資格情報・氏名が残る | 環境変数で渡し、報告を文字列で検索する | 5節 |
| 文言への差し込み・外部の資源・CSP の緩み | HTML を直接埋め込まず、外部の資源を足さず、CSP を変えない | 6節 |
| 依存の脆弱性・悪意のあるパッケージ | 新しい依存を足さない（`logical-components.md` 7節） | — |

## 8. 残る危険

| # | 危険 | 扱い |
|---|---|---|
| R1 | 403 の後の読み直しで、まだ管理者だった（403 の後に印が付け直された）ときは、その URL にいる間は S6 のままで、同じ URL を選び直しても戻れない | 受け入れ済み（機能設計の承認の場の R-04）。ほかの画面へ移るか読み込み直すと戻る。安全側の誤り |
| R2 | 403 を受けた直後の通信の失敗で、読み直しが失敗してログインの画面へ移る | 受け入れ済み（機能設計の承認の場の D9）。今の更新の失敗と同じ扱いで、安全側の誤り |
| R3 | 管理者でない利用者にも、管理の画面の URL があることは分かる | 受け入れ済み（機能設計の承認の場の G1・G5）。画面の一覧は配る JavaScript からも読める |
| R4 | 画面の側の管理者の印が古い（管理者でない）まま、サーバー側では管理者に付け直された利用者が管理の画面を開くと、サーバーに問わずに S6 が出る（FS の D6）。読み直しは 403 を受けたときだけ始まり、ForbiddenByRoute には読み直しの契機が無い | 実際の動きで受け入れ済み（NFR 要件の承認、SR の承認の場の R-03）。ForbiddenByRoute でも読み直しを呼ぶ形にはしない。戻り方（NFR 要件の再レビューの R-05）: S6 にいるあいだは管理の API を呼ばず、更新は API が 401 を返したときにしか走らないため、アクセストークンの期限が切れても自動では戻らない。確実に戻る手段は再読み込みと再ログインで、期限切れの更新で戻るのは、期限が過ぎた後にほかの画面が API を呼んだときに限る。安全側の誤り（使える画面が一時的に見えない）で、権限を広げる方向の誤りではない。サーバー側の印の切り替えは U3 のサーバー側のテストで確かめる |
| R5 | 今の `applyUserPreferences`（プリファレンスの画面の保存の後）・`setPreview`・`setLanguage` は、描いた時点のログイン状態を閉じ込めている。保存の応答を待つ間にトークンの更新が入ると、古いログイン状態に結び付けて当て、すぐ捨てられる（画面には新しいログイン状態の、サーバーが返した値が出る） | この段の Q3 A で受け入れて記録する。U4 では `applyOwnProfile` だけを直し、既存の口は機能設計の「変えない」のまま。表示の値が一時的に古く見えるだけで、印・権限・保存の値（サーバーが正）には影響しない。後の Intent への申し送り（9節） |

## 9. 上流との差

承認済みの文書は書き換えません（PM の Way of Working）。

| # | 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|---|
| S-1 | `applyOwnProfile` を ref で最新のログイン状態と当てている値を読む形にする（Q3 A、機能設計の再レビューの R-07） | FC の 3.6 の説明の断片 | 描画の時点の `loginState` と `applied` を閉じ込めた関数として書いている | 4.2 の形に変えた。口の名前・引数・当てる範囲（D13・D14）は変えない。`DisplaySettingsProvider.test.tsx` に 4.3 の確かめを足す（FC の 7節に無い項目の追加） |
| S-2 | 既存の `applyUserPreferences` などの同じ形の危険は直さない（Q3 A） | FC の 3.6（今の口は変えない） | 変えない | そのまま変えない。危険を R5 として記録し、後の Intent へ申し送る。食い違いではない |
| S-3 | R4 の戻り方の一文を足す（NFR 要件の再レビューの R-05） | SR の 5節の R4 | 依頼者の決定の文は「ほかの画面へ移るか再読み込みで戻る」。SR は戻り方を申し送りで確かめるとしていた | NFR 要件の承認で「実際の動きで受け入れ」と決まったため、8節の R4 に実際の戻り方を書いた |
| S-4 | 130 で見本の 403 の `detail` に目印を入れ、画面に出ないことを確かめる | SR の NFR3.1（確かめは部品のテスト） | 実際のブラウザでの確かめは書いていない | 5節で足した（確かめの追加で、食い違いではない） |
| S-5 | 共有の手伝いの型 `LoginPreferences` に省略できる `displayName` を足す（Q1 A） | TS の NFR7.3（共有の手伝いの既定の動作は変えず、130 の側で書き換えるか、省略できる引数を足す） | 2つの案を並べ、コード生成の計画で決めるとしていた | 省略できる引数を足す案に決めた。共有のファイルに手が入るが既定の動作は変えない（`logical-components.md` 6.2） |
| S-6 | コンソールの表示は 130 の中の見張りで件数を引いて除く（Q2 B） | TS の NFR7.3（除外は 130 の側だけ、共有の `watchPage` の既定は変えない） | 同じ | 文のとおり。共有の `pageProblems.ts` は変えない（`logical-components.md` 6.3） |
| S-7 | 共有の手伝い `routeLoginPreferences` を今使う既存の E2E は 060 だけ | この段の質問の文書の Q1 A（「050〜090 は渡さないため影響が無い」）、TS の NFR7.3 の申し送り（050〜100 など、`loginPreferences.ts` を使うもの） | 050〜090 または 050〜100 が使うと読める | `frontend/e2e/` の検索で、使うのは 060 だけと確かめた。影響の確かめは今までどおり 010〜100 のすべてを流して行うため、作業は変わらない（`logical-components.md` 6.2） |

申し送り:

| 先 | 中身 |
|---|---|
| コード生成の計画（U4、Bolt B2） | 4.2 の ref の形と 4.3 の確かめ、5節の報告の確かめ、`backend/`・`application.yaml`・`vendor/make-you-chic-ui`・`frontend/package.json`・`frontend/package-lock.json` に差分が無いことの記録 |
| 後の Intent | R5（既存の `applyUserPreferences`・`setPreview`・`setLanguage` を、呼ばれた時点の最新のログイン状態で動かす形に直すか） |
