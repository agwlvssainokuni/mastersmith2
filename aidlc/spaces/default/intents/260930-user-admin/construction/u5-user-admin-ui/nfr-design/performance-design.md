# Performance Design — U5 利用者の管理の画面（u5-user-admin-ui）

U5 の性能の設計です。承認済みの NFR 要件 `aidlc/spaces/default/intents/260930-user-admin/construction/u5-user-admin-ui/nfr-requirements/performance-requirements.md`（NFR5.1〜NFR5.6）を満たす作りを決めます。U5 は画面の単位（種類 ui）で、自分の API を持ちません。API の応答時間は U3 の持ち物で、この文書では指すだけにします。セキュリティの作りは `security-design.md`、部品の一覧と障害の範囲は `logical-components.md` にあります。

この段の質問 `nfr-design-questions.md` の設計の要点 1〜5 と答え（Q1 A・Q2 A・Q3 A）、まとめの確認（Looks correct）で決めました。性能に関わる質問はありませんでした（要点 1〜5 は承認済みの NFR 要件と機能設計から導いた形です）。プラットフォームの視点（配備先は開発者の PC 上のコンテナ）は 6節に重ねて書きました。

出典の略号: NP は `nfr-requirements/performance-requirements.md`、NS は `nfr-requirements/security-requirements.md`、TS は `nfr-requirements/tech-stack-decisions.md`、FS は `functional-design/functional-spec.md`（D・W はその決まりと流れ）、FC は `functional-design/frontend-components.md`、要点 n はこの段の `nfr-design-questions.md` の設計の要点、C3 は `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md` の C3、TM は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。画面のコードのパスは `frontend/src/features/useradmin/` の下を `useradmin/` と略します。

## 1. 性能の予算

| 対象 | 目標 | 持ち主 | 関門か |
|---|---|---|---|
| サイドバーの「利用者の管理」を選んでから、一覧の最初の行が見えるまで | 5 回すべて 2 秒以内（1回目を外さない） | U5（NFR5.1） | 関門にしない。測って記録する |
| 「次へ」を押してから、2ページ目の最初の行が見えるまで | 5 回すべて 1.5 秒以内（1回目を外さない） | U5（NFR5.2） | 関門にしない。測って記録する |
| 一覧・氏名と言語の変更・5つの操作の API | p95 1 秒以内（同時 10 件。排他の待ちの上限切れは約 3 秒で 409） | U3（NFR5.3 で指すだけ） | Performance Validation の k6 |
| 初回の JavaScript（gzip） | 500KB 以内を目安 | U5（NFR5.6） | 警告だけ（既存のまま） |

画面の時間は、手元の PC の Chromium（Playwright）で、`./gradlew :backend:bootWar` で作った WAR を相手に測ります。ブラウザと PC の状態に左右されるため、統合の関門にしません（NP の前提、TM の「不安定なテストは統合しない」）。目標を超えたときは目標を緩めず、一覧の API の時間か画面の描画かを確かめて依頼者に相談します（PM の Testing Posture）。

## 2. 一覧の読み方（NFR5.4、要点 1）

### 2.1 読むときを限る

一覧を読むのは次のときだけです（FS の D1・D3）。

| きっかけ | 読むもの |
|---|---|
| 画面を開いたとき | 1ページ目・検索なし |
| ページ送り | 選んだページ・今の検索の文字 |
| [検索]・[検索を消す] | 1ページ目・入れた文字（または検索なし） |
| 操作・保存の応答を受けた後 | 今のページ・今の検索の文字 |
| 「もう一度読み込む」 | 今のページ・今の検索の文字 |

- 決まった間隔の自動の読み直し（ポーリング）はしません。画面がタブの裏にある間も API を呼びません。
- 一覧はサーバーの順のまま、1ページ 20 件（`shared/paging` の `PAGE_SIZE`）までを描きます。画面で並べ替え・絞り込みをしません。
- 一覧の応答を写し（キャッシュ）として画面の外に持ちません。画面を開くたびに読み直します（NS の NFR3.1 とも合う）。

### 2.2 重なった読み直しと離れた後の答え

`useUserAdmin` は読み直しごとに番号を振り、最後に始めた読み直しの答えだけを使います。画面を離れた（部品が外れた）後に届いた答えは捨てます。

```text
// useUserAdmin の load の考え方（説明用）
const seq = ++loadSeq.current            // 読み直しの番号（useRef）
const result = await api.list(target)
if (!mounted.current || seq !== loadSeq.current) return   // 古い答え・離れた後の答えは捨てる
applyResult(result)                      // 200 のときだけ page・searchText を置き換える
```

- 要求そのものは取り消しません（既存の ApiClient に取り消しの口が無く、一覧の GET は状態を変えないため）。答えを捨てるだけにします。
- 古い答えを捨てても、`loadingRef` は最後の読み直しが終わったときにだけ戻します。

### 2.3 空のページの読み直しを1回に限る

読み直しで今のページが空になった（ほかの管理者の操作で件数が減った）ときは、`shared/paging` の `correctedPage` で最後のページを求めて1回だけ読み直します。`load` の中に「補正した」印を持ち、補正した先も空なら読まずに空のページの表示（empty-page）にします（FS の D10、承認の場の R-06）。招待の画面の前例は補正を繰り返しうるため、U5 では回数を1回に固定します。

### 2.4 確かめ方

`UserAdminPage.test.tsx`（FC 8節）で次を見ます。

- 重なった2つの読み直しで、先に始めた側の答えが後から届いても画面に出ない。
- 部品を外した後に届いた答えで、状態の更新が起きない（React の警告が出ない）。
- 空の応答が2回続くと、一覧の API の呼び出しが合わせて2回（最初と補正の1回）で止まる。
- 偽の時計を十分に進めても、一覧の API が呼ばれない（自動の読み直しが無い）。

## 3. 待ちの見せ方と二重の送信（NFR5.5、要点 2）

### 3.1 時間切れを置かない

- 画面は要求に独自の時間切れを置きません（既存の ApiClient のまま）。待ちの上限はサーバーの側（排他の待ちの上限 3000 ミリ秒で 409 `USER_ADMIN_BUSY`、U3 の NFR5.6）に任せます。
- 送信（確かめの表示の実行・入力の表示の保存）から 5 秒を過ぎても応答が無いときは、表示の中に「時間がかかっています」を出します。タイマーは送信の開始で `setTimeout` で始め、応答を受けたときと画面を離れたときに解きます。時間で要求を打ち切りません。

### 3.2 二重の送信を3つの層で防ぐ（機能設計の承認の場の R-01）

| 層 | 守り | 置き場 |
|---|---|---|
| (1) 表示のボタン | 確かめ・入力の表示の実行・保存のボタンを押せなくし「処理中」と示す。送信中は表示を閉じない | `ConfirmActionDialog`・`EditProfileDialog` |
| (2) 部品の形 | 読み直しの間は行の「操作」を `Button` の `loading` にして押しても開かない形にする（フォーカスは保つ）。[検索]・[検索を消す] は `aria-disabled` で押しても何もしない。ページ送りは `Table` に押せなくする口が無いため、`onPageChange` の側で読み直し中の押下を捨てる | `UserRowActions`・`UserSearchBox`・`UserTable` |
| (3) フックの参照 | `useUserAdmin` が `loadingRef`（読み直しの最中）と `submittingRef`（送信の最中）を `useRef` で持ち、その間の `goToPage`・`submitSearch`・`clearSearch`・`selectAction`・`confirmAction`・`saveEdit` の呼び出しを捨てる | `useUserAdmin` |

状態（`loadState`・`submitting`）は描画のために、参照は同じ描画の中で起きた二度押しを捨てるために使います。(1)・(2) は見た目と読み上げの守り、(3) は描画が追いつく前の押下の守りで、どれか1つに頼りません。

### 3.3 確かめ方

偽の時計（Vitest の `useFakeTimers`）で次を見ます。実時刻と `sleep` に頼りません（TM の Testing Posture）。

- 送信から 4999 ミリ秒では「時間がかかっています」が出ず、5000 ミリ秒で出て、応答を受けると消える。
- 読み直しの間にページ送り・[検索]・行の「操作」を押しても、一覧の API の呼び出しが増えない。
- 実行・保存のボタンを続けて2回押しても、要求は1回だけ。
- 描画の後に反映される値（読み直しの後の行・Toast・フォーカス）は `waitFor` で待つ。テストの時間の上限は原因を確かめずに延ばさない。

## 4. 初回の大きさ（NFR5.6、要点 3）

- 画面は `useradmin/registration.ts` の `lazy` で遅れて読み込み、初回の入口のファイルに含めません。
- U5 は新しい実行時の依存を足しません（TS の NFR9.2）。make-you-chic-ui は固定先を上げるだけで、使う部品（`Table`・`Dropdown`・`Modal`・`Button`・`Badge`・`Alert`・`Toast`）は既存の画面がすでに使っています。
- 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中、超えたら警告だけ）で、コード生成（B5）の中の2つの時点の値を記録します。(1) make-you-chic-ui の固定先を上げる前、(2) U5 の変更の後。固定先の更新で増えた分と U5 で増えた分を分けて見られるようにします。

## 5. 画面の時間の測り方（NFR5.1・NFR5.2、要点 4）

### 5.1 置き場と動かす組

- 測りのテストを `frontend/e2e/120-user-admin-accessibility.e2e.ts` の中に1件置きます。20 組の検査の繰り返しの外に置き、既定の1組（テーマ light・文字の大きさ md・ブランドカラー blue、既定の幅）だけで動かします。表示の設定の組で時間を比べません。
- 時刻はテストの側で `Date.now()` を取ります（前例の 060 と同じ）。ブラウザの中の性能の API は使いません。
- 時間はテストの成否にしません。測った値だけを記録します。

### 5.2 一覧を開くまで（NFR5.1）

1. 管理者でログインし、ホームの画面が出るのを待つ。
2. 差し替えの口（`security-design.md` 4節）を「測りのモード」で張る。一覧の GET は本物へ通す（`route.continue`）。
3. 次を 5 回くり返す。サイドバーの「利用者の管理」を選ぶ直前に時刻を取り、一覧の最初の行が見えた時点で時刻を取る。測るたびにサイドバーでホームへ戻る（画面の再読み込みはしない）。
4. 1回目（遅れて読み込む部品の取得と、ブラウザの初めての実行を含む）と、2〜5回目を分けて記録する。

一覧の GET は本物のため、値は E2E の内部DB にその時点でいる利用者の数（初期管理者と、ほかの E2E が作った利用者）で変わります。このために利用者を作りません（NP の 2節）。1,000 名の状態での API の時間は U3 の NFR5.1 が押さえます。

### 5.3 次のページまで（NFR5.2）

1. 同じテストの後半で、差し替えの口を「見本のページのモード」に切り替える。一覧の GET を、型を付けた見本（`security-design.md` 5節）の 2 ページ分（1ページ目 20 件、全体 21 件以上）で返す。
2. 一覧を読み直して1ページ目を出す。
3. 次を 5 回くり返す。「次へ」を押す直前に時刻を取り、2ページ目の最初の行が見えた時点で時刻を取る。測るたびに「前へ」で1ページ目へ戻す。
4. 1回目と2〜5回目を分けて記録する。

見本で返すため、測る値は画面の描画とページ送りの扱いの時間です。

### 5.4 記録の形

測った値は、数だけを注記と添付（名前 `user-admin-screen-ms`）に残します。値を含まないことは報告の部品で確かめます（`security-design.md` 3節）。

```text
// 添付 user-admin-screen-ms の中身（説明用、ミリ秒）
{ "list":     { "first": 1234, "rest": [410, 395, 402, 388], "max": 1234, "withinTarget": true, "targetMs": 2000 },
  "nextPage": { "first": 520,  "rest": [180, 175, 182, 177], "max": 520,  "withinTarget": true, "targetMs": 1500 } }
```

- `withinTarget` は 5 回すべてが目標以内かどうかです。1回目だけが超えたときも偽にし、相談の対象にします（NP の 2節）。
- Build and Test は `./gradlew e2eTest` の後に json の報告（`test-results/e2e-results.json`）からこの添付を写します。

### 5.5 値に含まれる上乗せ

差し替えの口は、本物へ通す一覧の GET も一度受けてから `route.continue` で通します。その分の小さな上乗せ（Playwright とブラウザの間の1往復）が NFR5.1 の値に含まれます。上乗せを差し引かず、含んだ値として記録し、そのことを Build and Test の記録に書きます。上乗せは目標を厳しくする向きのため、目標を緩めることにはなりません。

### 5.6 測りと検査を分ける理由

測りのテストを検査の 20 組と分けたのは、(1) 表示の設定の組ごとの時間の違いは目標の対象でないこと、(2) 20 組すべてで 5 回ずつ測ると E2E の時間が延びること、のためです。

## 6. API の時間とプラットフォームの視点（NFR5.3、要点 5）

- 一覧（検索を含む）・氏名と言語の変更・5つの操作の API の応答時間は、U3 の NFR5.1・NFR5.3〜NFR5.6 の目標をそのまま当て、U5 では測り直しません（Performance Validation の段の k6 は U3 の持ち物）。
- U5 は API と要求の形（C3）を変えません。画面が送る要求のパス・`q` の付け方（前後の空白を除き、空なら付けない）・本文が C3 のとおりであることを、`useradmin/api/userAdminApi.test.ts` で確かめます。
- 配備先は開発者の PC 上のコンテナで、U5 は WAR の中の静的なファイル（`dist`）を足すだけです。サーバーの資源（接続プール・メモリの上限・JVM の設定）に変えるものはありません。画面の配信は既存の WAR のまま、CDN や圧縮の設定を足しません。

## 7. 性能のテストの対応

| NFR | 確かめ | 段 |
|---|---|---|
| NFR5.1 | 120 の測りのテスト（5.2）、`user-admin-screen-ms` の `list` | コード生成で書いて流す。Build and Test で記録する |
| NFR5.2 | 120 の測りのテスト（5.3）、`user-admin-screen-ms` の `nextPage` | 同上 |
| NFR5.3 | `useradmin/api/userAdminApi.test.ts`（要求の形）。API の時間は U3 の k6 | コード生成。Performance Validation（U3） |
| NFR5.4 | `UserAdminPage.test.tsx`（2.4） | コード生成 |
| NFR5.5 | `UserAdminPage.test.tsx`・`UserSearchBox.test.tsx`・`UserTable.test.tsx`・`UserRowActions.test.tsx`・`ConfirmActionDialog.test.tsx`（3.3） | コード生成 |
| NFR5.6 | `check-bundle-size.mjs` の2つの時点の値 | コード生成（B5）で記録する |

## 8. 上流との差

承認済みの文書は書き換えません（PM の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| NFR5.1 の値に差し替えの口の上乗せが含まれる | NP の NFR5.1 | 一覧の API は本物のまま（見本で返さない）で測る | 一覧の GET は本物へ通すが、差し替えの口を一度通る（NS の NFR3.5 の守りを測りのテストにも当てるため）。上乗せを含んだ値として記録する（5.5）。要件の中身は変えない。追加の記録 |
| 測った値の記録の形を決めた | NP の NFR5.1・NFR5.2 | 1回目と2〜5回目を分けて記録する | `{ first, rest, max, withinTarget, targetMs }` の形で添付 `user-admin-screen-ms` に残す（5.4）。決めるべき点の決定で、食い違いではない |
| 初回の大きさを2つの時点で測る | NP の NFR5.6 | 固定先を上げる前と、U5 の変更の後の値を記録する | 同じ（4節）。食い違いではない |
