# Performance Requirements — U5 招待の管理の画面（u5-invitation-ui）

U5 の性能の要件です。U5 は画面の単位のため、自分の API を持ちません。ここでは、招待の画面を開いてから一覧が出るまでの時間とページ送りの時間、一覧の読み方と描き方、招待・送り直しの待ちの見せ方、画面の側の時間切れ、初回に読み込む JavaScript の大きさを扱います。答えは `nfr-requirements-questions.md` です（Q1: B、Q2: B、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`
- D・W・6節〜9節は、この単位の `construction/u5-invitation-ui/functional-design/functional-spec.md`
- 「部品 n節」は、同じフォルダの `frontend-components.md` の節
- Q1・Q2 と「要点 n」は、この段の `nfr-requirements-questions.md`（Q と「NFR の要点（案）」の番号）
- U3 の NFRx.y は `construction/u3-invitation/nfr-requirements/`、U4 の NFRx.y は `construction/u4-display-foundation/nfr-requirements/`

枝番はこの単位の中で振ります（ほかの単位と同じ番号でも別の要件です）。この単位の成果物と置き場は次のとおりです。

| 成果物 | 置く要件 |
|---|---|
| この文書 | 性能（NFR6.1〜NFR6.6） |
| `security-requirements.md` | 招待のトークン・URL を扱わないこと（NFR1.1）、メールアドレスを画面の外に出さないこと（NFR2.1）、失敗の文言・認可の画面の側の扱い・CSP（NFR9.1〜NFR9.3） |
| `tech-stack-decisions.md` | 依存と make-you-chic-ui（NFR9.4・NFR9.5）、アクセシビリティ（NFR7.1〜NFR7.4）、多言語（NFR8.1・NFR8.2）、テスト（NFR9.6〜NFR9.9） |

## 前提

- 一覧は 20 件ごとで、サーバーの順のまま描きます。読むのは画面を開いたとき・ページを変えたとき・操作の後・「もう一度読み込む」のときだけです（D1・D2）。
- API の目標は U3 で決まっています。どれも Performance Validation の段の k6 で測ります。
  - 一覧（`GET /api/admin/invitations`）・取り消し: 同時 10 件で p95 1 秒（U3 の NFR6.3）
  - 招待・送り直し: SMTP の送信を含めて同時 10 件で p95 5 秒（U3 の NFR6.1）
  - 受け手が応答しないとき: U1 の時間切れ（3 秒）の後に `sendResult` FAILED で応答する。応答の手前で遅れ続ける受け手では、全体が時間切れの値の数倍になりうる（U3 の NFR6.2、U1 の NFR6.3 の既知の限界）
- 画面の時間は、手元の PC のブラウザ（Playwright の Chromium）で、`./gradlew :backend:bootWar` で作った WAR を相手に測ります（既存の `frontend/playwright.config.ts`、U4 の NFR6.1 と同じ場）。
- 招待を置くには、E2E が起動する WAR で招待を使える設定（手元の受け手への SMTP の設定とベース URL）が要ります（招待を使えないと 503、U3 の BR1.4）。今の `frontend/playwright.config.ts` の `webServer.env` にはこの設定がありません。E2E の WAR への設定の渡し方は infrastructure-design の持ち主です（U6 の NFR 要件と同じ前提）。

## 1. 目標

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR6.1 | ログインした管理者が手元の PC のブラウザで「利用者の招待」を開いてから、一覧の1ページ目（招待を 21 件以上置いた状態で 20 行）が出るまで 2 秒以内 | Build and Test で、実際のブラウザの E2E（`./gradlew e2eTest`）の中で測って記録する。サイドバーの項目を選んでから（または `/admin/invitations` への移動の開始から）一覧の 20 行目が見えるまでを 5 回測り、5 回すべてが 2 秒以内であることを目標にする。どの検査のファイルで測るか（Playwright＋axe-core の検査か E2E-1 か）はコード生成で決める。招待は測る前にその検査の中で自分で用意する（前のテストの状態に頼らない、U4 の NFR9.11）。時間は統合の関門にしない（テストの成否にしない）。測った値は Build and Test の結果に記録する | Q2 B、要点 1、W1 |
| NFR6.2 | 一覧の1ページ目で「次へ」を押してから、2ページ目の行が出るまで 1.5 秒以内 | NFR6.1 と同じ場で、「次へ」を押してから2ページ目の最初の行が見えるまでを 5 回測り、5 回すべてが 1.5 秒以内であることを目標にする。1ページ目へ戻してから測り直す。統合の関門にしない。測った値を記録する | Q2 B、要点 1、W2 |
| NFR6.3 | 一覧は1ページ 20 行までを描き、画面で並べ替えない。決まった間隔の自動の読み直しをしない。読み直しが重なったら最後に始めた読み直しの答えだけを使い、画面を離れた後の答えを捨てる。読み直しの間も Table とページ送りのボタンを描いたまま残す | 画面部品のテスト（部品 7節の `paging.ts`・`InvitationList`・`InvitationAdminPage`）で、重なった読み直しで古い答えを捨てること、読み直しの間も Table が残ることを見る。自動の読み直しが無いことは、偽の時計を進めても一覧の API が呼ばれないことで見る（コード生成） | 要点 1、D1・D2、4.1 |
| NFR6.4 | 招待・送り直し・取り消しの要求の間は、押したボタンを make-you-chic-ui の Button の `loading` にして「送信しています」「取り消しています」と文字で示し、フォーカスを押したボタンに保つ。招待の送信中と取り消しの要求中は Modal を閉じない。送り直しの処理中は、その行の「取り消す」だけを押せなくし、ほかの行は押せるまま | 画面部品のテスト（部品 7節の `InviteDialog`・`CancelConfirmDialog`・`InvitationList`）で、要求にまだ答えを返さない状態の表示・`aria-busy`・フォーカス・閉じないことを見る（コード生成） | 要点 2、D9、U3 の NFR6.1 |
| NFR6.5 | 画面は要求に独自の時間切れを置かない（既存の ApiClient のまま）。待ちの上限はサーバーの時間切れ（U1 の SMTP の時間切れ、U3 の NFR6.2）に任せる。受け手が応答の手前で遅れ続ける既知の限界（U1 の NFR6.3）の間は、招待の Modal が「送信しています」のまま閉じられない。この限界は画面の単位では変えず、画面の側でも引き継ぐ | 確かめの対象にしない（既知の限界の記録）。配備先が決まり U1 の NFR6.3 を見直すときに、画面の側の扱いも合わせて見直す | 要点 3、D9、U1 の NFR6.3、U3 の NFR6.2 |
| NFR6.6 | 初回の読み込みの JavaScript（入口のファイルと、そこから静的に読み込まれるファイル）は、gzip で 500KB 以内を目安とする。超えたら警告を出すだけで、統合は止めない（既存の目安のまま）。U5 は新しい実行時の依存を足さない | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中）。U5 の変更の前後の値をコード生成で測って記録する | 要点 4、U4 の NFR6.3 |

## 2. 測り方の注意

- NFR6.1・NFR6.2 はブラウザと PC の状態に左右されます。そのため統合の関門にはせず、測った値を記録します（U4 の NFR6.1、前の Intent の DSL の管理画面の NFR1.18 と同じ扱い、`team.md` の「不安定なテストは統合しない」）。
- 目標を超えたときは、目標を緩めて「満たした」ことにはしません（`project.md` の Testing Posture）。一覧の API の時間（要求の一覧で見る）か、画面の描画かを確かめ、結果とともに依頼者に相談します。
- 招待は、実行ごとに重ならないメールアドレス（予約済みのドメイン `example.com` の下）で用意し、E2E が起動したアプリの手元の受け手だけに送ります。実在の宛先・外部の SMTP へは送りません（`team.md` の Deployment、`project.md` の Forbidden）。
- 長い計測は `caffeinate -i` を付けて流し、PC のスリープで値が崩れるのを防ぎます（`project.md` の Testing Posture）。
- 同じ考え方（画面の時間の目標を置き、実際のブラウザで測って記録し、統合の関門にしない）を U6・U7 の画面にもそろえます（Q2 B の依頼者の決定）。

## 3. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 招待の画面の時間の目標（一覧 2 秒・次のページ 1.5 秒、Q2: B） | 要件の NFR6、U4 の NFR6.1 | 要件は API の応答時間だけを決め、U4 はログインの画面（最初の画面）にだけ画面の時間の目標を置いた | 追加（NFR6.1・NFR6.2）で、食い違いではない。統合の関門にせず、測って記録する |
| 画面の側に時間切れを置かない | 機能設計の D9 | 招待の送信中は Modal を閉じない | 受け手が遅れ続ける既知の限界（U1 の NFR6.3）の間は閉じられないことを明記した（NFR6.5）。追加の記録で、作りは変えない |
| 測る場の前提（E2E の WAR で招待を使える設定） | `frontend/playwright.config.ts` | 招待を使える設定を渡していない | 設定の渡し方は infrastructure-design の持ち主とした（前提）。U6 の E2E-1 と同じ前提 |
