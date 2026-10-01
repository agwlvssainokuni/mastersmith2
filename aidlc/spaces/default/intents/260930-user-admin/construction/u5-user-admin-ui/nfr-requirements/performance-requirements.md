# Performance Requirements — U5 利用者の管理の画面（u5-user-admin-ui）

U5 の性能の要件です。U5 は画面の単位（種類 ui）で、自分の API を持ちません。ここでは、画面を開いてから一覧が出るまでの時間とページ送りの時間、一覧の読み方、送信の待ちの見せ方と二重の送信の防ぎ方、初回に読み込む JavaScript の大きさを扱います。一覧と操作の API の応答時間は U3 の持ち物で、この文書では指すだけにします。答えは `nfr-requirements-questions.md` です（Q1: A、Q2: B、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は要件 `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md` の ID
- D・W・4節〜10節は、この単位の `construction/u5-user-admin-ui/functional-design/functional-spec.md`。「承認の場」は同じ文書の「承認の場の決定」の節
- 「部品 n節」は、同じフォルダの `frontend-components.md` の節
- Q1・Q2 と「要点 n」は、この段の `nfr-requirements-questions.md`（Q と「設計の要点（案）」の番号）
- U3 の NFRx.y は、この Intent の `construction/u3-user-admin-api/nfr-requirements/`。U4 の NFRx.y は `construction/u4-admin-forbidden-ui/nfr-requirements/`
- PM は `aidlc/spaces/default/memory/project.md`、TM は `aidlc/spaces/default/memory/team.md`

## 枝番の振り方と置き場

枝番はこの単位の中で .1 から振ります（ほかの単位と同じ番号でも別の要件です）。要件の NFR5 は応答時間の要件のため、画面の時間・一覧の読み方・待ちの見せ方・初回の大きさを、質問の文書の「設計の要点（案）」のとおり NFR5 の枝番にしました。当たる上流の ID が無い依存・静的検査・CSP・テストの要件は NFR9 の枝番に寄せます（PM の学び）。

| 成果物 | 置く要件 |
|---|---|
| この文書 | 性能（NFR5.1〜NFR5.6） |
| `security-requirements.md` | 画面の判定とサーバーの判定（NFR1.1・NFR1.2）、個人に関する値・失敗の文言・応答の項目・E2E の報告（NFR3.1〜NFR3.4）、静的検査と CSP（NFR9.1） |
| `tech-stack-decisions.md` | アクセシビリティ（NFR7.1〜NFR7.3）、多言語（NFR8.1〜NFR8.3）、依存・make-you-chic-ui・`.npmrc`・テスト・E2E（NFR9.2〜NFR9.9） |

## 前提

- 一覧は 1ページ 20 件で、サーバーの順のまま描きます。読むのは画面を開いたとき・ページ送り・検索・検索を消す・操作の応答を受けた後・「もう一度読み込む」のときだけです（D1・D3）。
- API の目標は U3 で決まっています。どれも Performance Validation の段の k6 で測ります。
  - 一覧（`GET /api/admin/users`、検索を含む）: 利用者 1,000 名の状態で同時 10 件の p95 1 秒以内（U3 の NFR5.1）
  - 氏名と言語の変更（`PUT /api/admin/users/{userId}/profile`）: 同時 10 件の p95 1 秒以内（U3 の NFR5.3）
  - 5つの操作: 同時 10 件で操作ごとに p95 1 秒以内（U3 の NFR5.4。排他の待ちの上限切れの 409 `USER_ADMIN_BUSY` は約 3 秒で返る、U3 の NFR5.6）
- 画面の時間は、手元の PC のブラウザ（Playwright の Chromium）で、`./gradlew :backend:bootWar` で作った WAR を相手に測ります（既存の `frontend/playwright.config.ts`、前の Intent の招待の画面と同じ場）。
- 画面の時間は統合の関門にしません。ブラウザと PC の状態に左右されるためです（前の Intent の画面の単位と同じ扱い、TM の「不安定なテストは統合しない」）。

## 1. 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR5.1 | ログインした管理者が手元の PC のブラウザで「利用者の管理」を開いてから、一覧の1ページ目の行が出るまで 2 秒以内。一覧の API は本物のまま（何も差し替えない）で、E2E の内部DB にその時点でいる利用者の数のまま測る | コード生成で、`frontend/e2e/120-user-admin-accessibility.e2e.ts` の中に測りのテストを1件置く。サイドバーの項目を選んでから（または `/admin/users` への移動の開始から）一覧の最初の行が見えるまでを 5 回測り、5 回すべてが 2 秒以内であることを目標にする。Build and Test で `./gradlew e2eTest` を流し、測った値（5 回分と最大）を記録する。時間はテストの成否にしない | Q2 B、要点 3、W1 |
| NFR5.2 | 一覧の1ページ目で「次へ」を押してから、2ページ目の行が出るまで 1.5 秒以内。測るのは画面の側の時間だけで、一覧の API の答えを 2 ページ分の見本（1ページ目 20 件、全体 21 件以上）に差し替える。API の時間は U3 の NFR5.1 が持つ | NFR5.1 と同じ測りのテストの中で、「次へ」を押してから2ページ目の最初の行が見えるまでを 5 回測る（測るたびに1ページ目へ戻す）。5 回すべてが 1.5 秒以内であることを目標にする。Build and Test で値を記録し、テストの成否にしない | Q2 B、要点 3、W2 |
| NFR5.3 | 一覧（検索を含む）・氏名と言語の変更・5つの操作の API の応答時間は、U3 の NFR5.1・NFR5.3〜NFR5.6 の目標をそのまま当て、U5 では目標を足さず、測り直さない。U5 は API と要求の形（C3）を変えない | Performance Validation の段の k6（U3 の持ち物）。U5 では、画面が送る要求のパス・`q` の付け方・本文が C3 のとおりであることを、コード生成で `api/userAdminApi.test.ts` によって確かめる | 要件 NFR5、要点 2、U3 の NFR5.1〜NFR5.6 |
| NFR5.4 | 一覧は1ページ 20 件までをサーバーの順のまま描き、画面で並べ替えない。決まった間隔の自動の読み直しをしない。読み直しが重なったら最後に始めた読み直しの答えだけを使い、画面を離れた後の答えを捨てる。読み直しで今のページが空になったときの最後のページの読み直しは、1回の読み込みにつき1回までとし、移った先も空なら読まずに空のページの表示にする | 画面部品のテスト（部品 8節の `UserAdminPage.test.tsx`）で、重なった読み直しで古い答えを捨てること、空の応答が 2 回続くと読み直しが 1 回で止まることを見る。自動の読み直しが無いことは、偽の時計を進めても一覧の API が呼ばれないことで見る（コード生成） | 要点 1、D1・D3・D10、承認の場（R-04・R-06） |
| NFR5.5 | 画面は要求に独自の時間切れを置かない（既存の ApiClient のまま）。送信から 5 秒を過ぎても応答が無いときは、確かめ・入力の表示の中に「時間がかかっています」を出し、応答を受けたら消す。時間で要求を打ち切らない。待ちの上限はサーバーの側（排他の待ちの上限 3000 ミリ秒、U3 の NFR5.6）に任せる。二重の送信は、(1) 表示のボタンを押せなくし「処理中」と示す、(2) 読み直しの間は行の「操作」を `Button` の `loading` にし、[検索]・[検索を消す] を `aria-disabled` にし、ページ送りの押下を `onPageChange` の側で捨てる、(3) `useUserAdmin` が読み直し・送信の最中を参照（`useRef`）で持ち、その間の呼び出しを捨てる、の3つで防ぐ | 画面部品のテスト（部品 8節の `UserAdminPage.test.tsx`・`UserSearchBox.test.tsx`・`UserTable.test.tsx`・`UserRowActions.test.tsx`・`ConfirmActionDialog.test.tsx`）で、偽の時計で 5 秒の表示を見ること、読み直しの間の押下で要求が増えないこと、実行・保存の二度押しで要求が 1 回だけであることを見る（コード生成）。実時刻と `sleep` に頼らない | 要点 4、D13・D14、`inception/refined-mockups/interaction-spec.md` 3節、承認の場（R-01） |
| NFR5.6 | 初回の読み込みの JavaScript（入口のファイルと、そこから静的に読み込まれるファイル）は、gzip で 500KB 以内を目安とする。超えたら警告を出すだけで、統合は止めない（既存の目安のまま）。画面は遅延読み込み（`lazy`）とし、U5 は新しい実行時の依存を足さない | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中）。make-you-chic-ui の固定先を上げる前と、U5 の変更の後の値を、コード生成で測って記録する | 要点 5、機能設計 2節（`registration.ts` の `lazy`）、前の Intent の画面の単位の目安 |

## 2. 測り方の注意

- NFR5.1 は本物の API を通すため、E2E の内部DB にいる利用者の数（初期管理者と、ほかの E2E が作った利用者）で値が変わります。利用者をこのために大量に作りません（Q2 B。C の 21 人を作る案は選ばなかった）。1,000 名の状態での一覧の時間は U3 の NFR5.1 が押さえます。
- NFR5.2 は見本の答えを返すため、測る値は画面の描画とページ送りの扱いの時間です。見本は `tech-stack-decisions.md` の NFR9.9 の形（画面の側の型を付けた1つの見本）で置きます。
- 目標を超えたときは、目標を緩めて「満たした」ことにはしません（PM の Testing Posture）。一覧の API の時間（Playwright の要求の記録で見る）か画面の描画かを確かめ、結果とともに依頼者に相談します。
- 長い計測は `caffeinate -i` を台本全体に付けて流し、PC のスリープで値が崩れるのを防ぎます（PM の Testing Posture）。
- テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばしません（TM の Testing Posture）。描画の後に反映される値は `waitFor` で待ちます。

## 3. 上流との差

承認済みの文書は書き換えません（PM の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 画面の時間の目標（一覧 2 秒・次のページ 1.5 秒、Q2: B） | 要件の NFR5 | 要件は API の応答時間（p95 1 秒）だけを決める | 追加（NFR5.1・NFR5.2）で、食い違いではない。前の Intent の招待の画面とそろえた。統合の関門にせず、測って記録する |
| 「次へ」の時間は見本の答えで測る | 前の Intent の招待の画面（本物の API で 21 件以上を置いて測った） | — | 利用者は招待と登録の完了でしか作れず、21 人を作ると E2E の時間が延び Mailpit に 21 通が残るため、画面の側の時間だけを測る形にした（Q2 B）。API の時間は U3 の NFR5.1 で押さえる |
| 画面の側の性能を NFR5 の枝番に置く | U4 の `performance-requirements.md` | U4 は画面の側の時点・回数・大きさを NFR9 の枝番に寄せた | U5 は質問の文書の「設計の要点（案）」のとおり NFR5 の枝番に置いた。単位ごとの振り方の違いで、要件の中身は食い違わない。traceability.json の NFR5 の target に書く |
