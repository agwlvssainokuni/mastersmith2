# Code Summary — U5 招待の管理の画面（u5-invitation-ui）

Bolt B5 の最初の単位として、管理者が招待中の人を一覧で確かめ、招待・送り直し・取り消しを行う画面を作った。承認済みの計画（`code-generation-plan.md`）の Step 1〜22 を順に実施した。Step 16 で一度止めて依頼者に諮り、決定 A（ログインと復元の応答の2項目の書き換え）を受けて再開した。

- 作業のブランチは `feature/260925-user-management-b5-u5` で、`develop` の `fd44e79` から作った。コミットはしていない（区切りの案は 9節）。
- 固定先は変えていない。`vendor/make-you-chic-ui` は `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`、`vendor/java-mustache-processor` は `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4` のまま。
- サーバー側（`backend/`・`application.yaml`・Flyway・`compose.yaml`・`Dockerfile`・`build.gradle.kts`・`.github/`）と、`frontend/package.json`・`frontend/package-lock.json` は変えていない。
- パスはリポジトリのルートからの相対パス。

## 1. 作ったもの・手を入れたもの

### 1.1 画面（`frontend/src/features/invitation/`）

| ファイル | 役割 |
|---|---|
| `api/types.ts` | 契約 C5 の型（`Invitation`・`InvitationPage`・`InvitationRequest`・`PendingProblem`・`SendResult`・`UnavailableReason`・`InvitationLanguage`）。値の一覧は `as const` の配列と union で持つ（`enum` を使わない）。トークン・URL の項目は持たない |
| `api/invitationApi.ts` | 4つの API（`listInvitations`・`createInvitation`・`resendInvitation`・`cancelInvitation`）と `readPendingProblem`・`readUnavailableReasons`、差し替え用の `invitationApi`・`InvitationApi` |
| `paging.ts` | `PAGE_SIZE` 20・`pageCount`・`pageRange`・`correctedPage`・`pagerButtonDisabledAfter` |
| `focusTarget.ts` | フォーカスの行き先の型と `resolveFocusTarget` |
| `unavailable.ts` | 使えない理由から警告の文の形を決める（`knownReasons`・`unavailableNotice`） |
| `inviteInput.ts` | メールアドレスが空（空白だけを含む）かの判定。値は正規化しない |
| `failureMessage.ts` | 失敗から文言の鍵を選ぶ（`knownCode`・`failureStatus`・`generalFailureKey`・`failureMessageKey`）。`detail`・`title` を読まない |
| `messages.ts`・`useInvitationText.ts` | ja・en の文言（機能設計 7節の鍵）と、文言を引く関数 |
| `useInvitationAdmin.ts` | 画面の状態と操作（部品 3節）。重なった読み直しは番号で、画面を離れた後の答えは印で捨てる。自動の読み直しは置かない |
| `InvitationAdminPage.tsx`・`.css` | 画面（見出し・「招待する」・警告・失敗の知らせ・一覧・2つの Modal・件数の範囲の読み上げ） |
| `InvitationList.tsx`・`.css` | 一覧の見出し（h2）・読み込み中・空・読めなかった・make-you-chic-ui の Table（8列・`labels` の7項目・`aria-label`）。目立たせた行は `:has()`、フォーカスは render で描く要素への参照で当てる |
| `InvitationUnavailableAlert.tsx`・`.css` | 招待を使えないときの警告と、使えない理由の文（`UnavailableReasonsText`） |
| `FailureNoticeText.tsx` | 失敗の知らせの文（一覧の上と Modal の中で共に使う） |
| `InviteDialog.tsx`・`.css` | S1-M1（CSS は取り消しの確かめでも使う） |
| `CancelConfirmDialog.tsx` | S1-M2（`role="alertdialog"`） |
| `registration.ts` | 画面 `/admin/invitations`（遅延読み込み、`SHELL`・`ADMIN`）、サイドバー `invitation`（order 220、`ADMIN`）、文言 |
| `testing/renderInvitation.tsx`・`testing/fixtures.ts` | テストの補助 |

テストのファイル（対象と同じ場所）の件数:

| ファイル | 件数 |
|---|---|
| `paging.test.ts` | 7（うち fast-check 1） |
| `focusTarget.test.ts` | 5 |
| `unavailable.test.ts` | 5 |
| `inviteInput.test.ts` | 5（うち fast-check 1） |
| `failureMessage.test.ts` | 6 |
| `api/invitationApi.test.ts` | 9 |
| `InvitationList.test.tsx` | 10 |
| `InvitationUnavailableAlert.test.tsx` | 5 |
| `InviteDialog.test.tsx` | 8 |
| `CancelConfirmDialog.test.tsx` | 6 |
| `InvitationAdminPage.test.tsx` | 29 |
| `registration.test.tsx` | 6 |

合わせて 12 ファイル 101 件。画面部品5つにはそれぞれ vitest-axe の検査を入れた。

### 1.2 日時の書式の移動

- 新しく `frontend/src/shared/format/formatDateTime.ts`（`formatDateTime` と型 `FormatLanguage`）と `formatDateTime.test.ts` を置いた。テストは DSL から、確かめの中身を変えずに移した（3件）。
- `frontend/src/features/dsl/format.ts` には `shortHash`・`formatBytes` だけを残した。`format.test.ts` からは `formatDateTime` の確かめを移した。
- `DslStatusPanel.tsx`・`DslConfirmDialog.tsx`・`DslHistoryTable.tsx` は読み込み先だけを変えた。移した後も DSL の4つのテストは期待を変えずに通った（移動の前後とも 30 件）。

### 1.3 実際のブラウザの検査（`frontend/e2e/`）

| ファイル | 役割 |
|---|---|
| `060-invitation-accessibility.e2e.ts` | 20 組の検査（一覧・招待の入力・取り消しの確かめ、(a)・(b) は警告も）と、画面の時間の測定（5回） |
| `support/adminLogin.ts` | `loginAsAdmin`・`openSidebarItem`（B5 の共用） |
| `support/invitationFixtures.ts` | 一覧の見本（画面の型を付ける）と `hasInvitationPageShape`（B5 の共用） |
| `support/invitationSeed.ts` | `requestAdminAccessToken`・`readInvitationList`・`seedInvitations`・`newRunTag`（B5 の共用） |
| `support/loginPreferences.ts` | `routeLoginPreferences`（B5 の共用。決定 A。5節） |
| `support/axe.ts`（手を入れた） | `splitKnownViolations` に任意の第3引数 `knownTestIds` を足し、`INVITATION_KNOWN_VIOLATIONS`（状態ごとの一覧）を足した。050 の一覧と判定は変えていない |

### 1.4 文書

`README.md` に次を書いた。

- 新しい節「招待の管理の画面（U5）」
- E2E の表の 060 の行と、「060 について」の節（決定 A の当て方、21 通、060 だけのときの Mailpit）
- E2E のメールの片付けの書き方（「手元でメールを見る」の2行にそろえる。計画の決定 5）
- 「画面の表示の設定（U4）」の既知の制約に、U5 の画面で当たるボタン

## 2. 上流・計画との差

承認済みの文書は書き換えず、差をここに記録する。

| 項目 | 承認済みの形 | 実際の作り | 理由 |
|---|---|---|---|
| ログインの後の画面への組の当て方（決定 A） | NFR 設計 `logical-components.md` 5.4「初めのスクリプトで置いた値がそのまま効く」。`security-design.md` 6節「差し替えるのは一覧の API だけ」 | ログイン（`POST /api/auth/login`）と復元（`POST /api/auth/session/refresh`）の本物の応答を受けてから、`user.theme`・`user.fontSize` の2項目だけを組の値に書き換えて返す（`support/loginPreferences.ts`）。サーバーの状態は変えない。ほかの項目とほかの API は書き換えない。060 は書き換えの回数（ログインで1、警告の読み込み直しで2）も確かめる | ログインの後は利用者の設定が当たり、ブラウザの保存の値は使われない（U4 の W5・D8）。1回目の実行で 15 組の組が当たらなかった。Step 16 で止めて諮り、依頼者が A を選んだ。差し替えの範囲は `security-design.md` 6節より広い |
| Badge の種類 | 機能設計は種類を決めていない（文字で示す、色は補助） | 送信の結果は SENT を `secondary`・FAILED を `danger`、状態は期限内を `secondary`・期限切れを `danger` にした。`primary`・`success` は使わない | `primary` はブランドカラーの背景で、green・orange では既知の違反（primary の Button に限る）の外の違反になる。`success`（green-600 に白の文字）は 3.3:1 で 4.5:1 に届かない |
| 狭い幅の横の領域 | 機能設計 W1 の5・承認の場の U5 R-01（ブラウザがフォーカスした要素を見える位置へ動かす見込み） | 行のボタンとメールアドレスのセルがフォーカスを受けたら `scrollIntoView({ block: 'nearest', inline: 'nearest' })` を呼ぶ（`InvitationList.tsx` の `revealFocused`）。矢印のキーの処理は作らない | 375px の組で、Tab でフォーカスした「取り消す」の右端が 422.6px に残った。Chrome は一部が見えている要素へのフォーカスでは動かさないため。U5 のコードで直した |
| 部品のモジュール | 部品 2節の一覧 | 一覧に無い `FailureNoticeText.tsx` と `InvitationUnavailableAlert.css` を足した。`InviteDialog.css` は取り消しの確かめでも使う | 失敗の知らせの文を、一覧の上と Modal の中で共に使うため |
| 登録のテストの名前 | 計画は `registration.test.ts` | `registration.test.tsx` | JSX（遅延読み込みの確かめ）を含むため。DSL と同じ |
| Table の列 | 部品 4節は「7列」 | 8列 | 計画の決定 3 |
| ページの補正 | `correctedPage` は「items が空で total が 1 以上なら最後のページ」 | すでに最後のページを読んで行が空だったときは補正しない | 同じページを読み続けないため。補正の結果は 1 以上ページの数以下のまま |
| 招待の入力の状態 | 部品 3.2 の7項目 | `errorSeq` を足した | 同じ誤りを重ねて出したときも、描いた後にメールアドレスへフォーカスを移すため |
| 「招待する」を押せる条件 | D3 | 一覧が読めなかった間は、前の一覧があっても押せない | D3「読めなかった間も押せない」のとおり |
| 一覧の 401 | 6.1「失敗（401 を除く）」 | 401 では読み込み中のままにし、読めなかった表示を出さない | ApiClient とログイン状態に任せるため |
| 件数の範囲の読み上げ | W2 の3（ページ送りの後） | 一覧が読めたたびに更新する（全件数が 0 なら空） | 操作の後の読み直しでも範囲が変わるため |
| 送信中の閉じない作り | D9 | Modal の `onClose` を部品の側で無視する（Esc・閉じるボタン）。「やめる」は `disabled` | 設計どおり |

- 契約との差: U5 が受ける側の差は C5 の `invitedBy` だけ。README の「招待と登録の完了（U3）」の「契約との差」に「氏名だけ、行が無ければ空」とあることを確かめ、画面は氏名だけを出し、空なら「（不明）」を出す（D7）。契約の文書は書き換えていない。C9 は `useDisplaySettings().language` と `LANGUAGE_NAMES` だけを使い、差は無い。
- 横の領域の実際の動き: Tab で行の「送り直す」「取り消す」とページ送りのボタンに届き、届いた要素は Table の包む要素の中で見える位置まで動く。矢印のキーでの手動の横の移動は無い（README の U5 の節に記載）。

## 3. Step 2 の前提の確かめ（読み取りだけ）

- 固定先 `735ef04` で、U5 が使う口がすべてあることを確かめた。
  - Table: `labels` の7項目、`pageStatus(page, totalPages, totalCount)`、`aria-label`、`page`・`pageSize`・`totalCount`・`onPageChange`・`getRowId`・列の `render`
  - Modal: `closeOnBackdropClick`・`role`・`closeLabel`・`initialFocusRef`
  - RadioGroup: `legend`・選択肢の `lang`
  - Button: `loading` で `aria-disabled`・`aria-busy`（ネイティブの `disabled` にはしない）
  - Alert・Badge・`useToast`
- AppShell: `@media` の切り替えが無く、中身の領域 `.mycui-app-shell-content` は `overflow-y: auto`。375px でもサイドバー（220px）は出たままで、「利用者の招待」を押せた。
- U3 の API の形: 契約 C5 と機能設計のとおりだった。
  - `InvitationResponse` 8項目、`InvitationPageResponse` 6項目
  - 409 `INVITATION_ALREADY_PENDING` の `invitationId`・`page`、503 `INVITATION_NOT_CONFIGURED` の `unavailableReasons`（並びは BASE_URL・SMTP の順）
  - 不正なページは 400 `VALIDATION_FAILED`、取り消しは 204
- NFR 設計の承認の場の U5 R-02: `InvitationAdminApiIT.notConfigured` は、ベース URL が無い・SMTP が無い・両方無いの3通りで、管理者のアクセストークンの取得と一覧の 200（`invitationEnabled: false` と理由）を確かめている。E2E の初期管理者は `MASTERSMITH_AUTH_INITIAL_ADMIN_*` で用意し、SMTP の設定に頼らない。E2E の WAR には SMTP とベース URL が渡る（`webServer.env`）ため、060 の飛ばす判定は念のための備え（060 のコメントに記載）。
- 機能設計の承認の場の G6: README の「契約との差」に C5 の `invitedBy` の差がある。
- 機能どうしの読み込み: 今の本番のコードに無く、U5 も作っていない（テストだけが登録を組み合わせる）。

## 4. 実測

### 4.1 Step 1（変更の前）と Step 21（変更の後）の verify

どちらも colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流した。

| 項目 | Step 1（前） | Step 21（後） |
|---|---|---|
| 結果・時間 | 成功・6分7秒 | 成功・6分2秒 |
| バックエンドの単体テスト | 151 クラス 1234 件（失敗 0・飛ばし 0） | 151 クラス 1234 件（失敗 0・飛ばし 0） |
| バックエンドの結合テスト | 113 クラス 562 件（失敗 0・飛ばし 0） | 113 クラス 562 件（失敗 0・飛ばし 0、対象DB のテストも SKIPPED 無し） |
| バックエンドのカバレッジ（全体） | 行 98.78%・分岐 94.38% | 行 98.78%・分岐 94.38% |
| フロントエンドのテスト | 55 ファイル 408 件 | 68 ファイル 509 件 |
| フロントエンドのカバレッジ（全体） | 文 97.96%・分岐 94.27%・関数 97.8%・行 98.04% | 文 97.51%・分岐 93.73%・関数 97.91%・行 97.6% |
| Gitleaks | 通過 | 通過 |
| OSV-Scanner | UP-TO-DATE | UP-TO-DATE（lockfile を変えていないため。Step 17 で差分 0 を確かめた） |
| vendorUnchanged | 通過 | 通過 |

### 4.2 U5 の新しいファイルのカバレッジ（Step 18）

計画の単位の範囲のコマンドで測った（17 ファイル 131 件）。合計は行 96.29%・分岐 92.35%。100% でないファイルだけを示す。ほかはすべて行 100%・分岐 100%。

| ファイル | 行 | 分岐 |
|---|---|---|
| `useInvitationAdmin.ts` | 93.75% | 82.4% |
| `InvitationList.tsx` | 93.47% | 96.36% |
| `InviteDialog.tsx` | 100% | 90% |
| `api/invitationApi.ts` | 98.18% | 97.95% |
| `shared/format/formatDateTime.ts` | 100% | 91.66% |

どのファイルも行 80%・分岐 70% 以上。計測の除外は増やしていない。

### 4.3 配信物の大きさ（Step 1 と Step 17）

| 項目 | 前 | 後 |
|---|---|---|
| 初回の JavaScript（gzip） | 114.1 KB（`index` 1つ） | 119.2 KB（`index` 102.7 KB と共有の塊 `useTranslation` 16.6 KB） |
| `dist/assets` の JavaScript | 4 ファイル・418,890 バイト | 7 ファイル・453,707 バイト |
| WAR | 101,566,196 バイト | 101,578,791 バイト |

- 初回の JavaScript は 5.1 KB 増えた。登録の文言が入口に入ることと、react-i18next が共有の塊に分かれたことによる（500 KB の目安の警告は無し）。
- U5 の画面の塊 `InvitationAdminPage-*.js` は動的な読み込みの塊（`manifest.json` の `isDynamicEntry`）で、18,599 バイト（gzip の -9 で 5,526 バイト）。日時の書式は 570 バイトの共有の塊に分かれた。
- ほかの確かめ: `application.yaml`・`package.json`・`package-lock.json` の差分は 0。`index.html` の `<script>` は `src` つきの1つだけで、`<style>` は無い。

## 5. 実際のブラウザの検査（060）の結果

### 5.1 060 だけの実行（Step 16）

- 決定 A の後の1回目と2回目は、どちらも 21 件が通った（1回目 58.4 秒・2回目 57.6 秒）。
- 組と状態の成否は2回とも同じで、74 状態のすべてで想定外の違反は 0 件だった（(a)・(b) は4状態、(c) は3状態）。
- `color-contrast` と `scrollable-region-focusable` はすべての状態で流れ、`scrollable-region-focusable` の違反は無かった。
- 横のはみ出しは無かった（`scrollWidth` は既定の幅で 1280、375px の組で 375）。
- 375px の6組で、Tab でフォーカスした1行目の「取り消す」が表示の幅の中に入った。
- 招待の管理の POST は、各組とも0件だった。
- CSP の違反と画面の問題は、各組と測定の各回で0件だった。
- 決定 A の前の1回目は 17 件が失敗し、4 件が通った。15 組は組が当たらず、green・orange の light の2組は既知の違反の一覧が空だったため、375px の1組は「取り消す」が幅の外に残ったため。後の2件は直し、確かめ直した。

### 5.2 既知の違反（`INVITATION_KNOWN_VIOLATIONS`）

green・orange の light と dark の4組で当たったのは、次の名前だけ。blue・purple の組と、ほかの状態では当たらなかった。

| 状態 | 名前（`color-contrast`、primary の Button） |
|---|---|
| 一覧（`list`） | `invitation-invite-button` |
| 招待の入力の Modal（`inviteDialog`） | `invitation-invite-submit` |
| 取り消しの確かめの Modal（`revokeDialog`） | なし（danger のボタンは 4.83:1 で通る。背景の「招待する」は数えられない） |
| 警告（`unavailable`） | なし（「招待する」が `disabled`） |

### 5.3 判定できなかった要素（incomplete、失敗にしない）

- 一覧と警告の状態の `color-contrast`: 既定の幅で 1〜41 件、375px で 85〜101 件（表が包む要素の外に隠れる部分など）。
- 2つの Modal の `bypass`: 1件。
- 取り消しの確かめの `color-contrast`: 既定の幅で1件。

### 5.4 画面の時間（ミリ秒、目標: 一覧 2,000・次のページ 1,500。関門にしない）

| 実行 | 一覧（5回） | 次のページ（5回） |
|---|---|---|
| Step 16 の1回目 | 110・118・95・120・116 | 80・71・64・65・70 |
| Step 16 の2回目 | 100・111・120・120・99 | 68・79・52・67・79 |
| Step 20（`e2eTest` の全件の中） | 108・108・116・106・120 | 77・82・66・70・68 |

- どの実行も目標以内は 5/5 だった。
- 測定の準備はどの実行も 21 件が 201 で、`sendResult` はすべて SENT だった（Mailpit に 21 通）。
- 本物の一覧の応答の項目の名前と型は、見本と一致した。

### 5.5 json の結果の確かめ（基盤の設計の N9）

- `frontend/test-results/e2e-results.json` を値の文字列で検索した。アクセストークン（`eyJ`）・測定の招待のメールアドレス（`u5-perf-`）・初期管理者のメールアドレスは、どの実行でも 0 件だった。報告の部品の仮の資格情報の確かめも通った。
- `test.step` の題は json の `steps` に入る。入っていたのは6つの題（`open the invitations screen`・`list with rows`・`tab to the revoke button of the first row`・`invite dialog`・`revoke confirmation dialog`・`warning when invitations are unavailable`）で、秘密は含まない。
- 失敗の実行のときの `test-results/` の `error-context.md`（ページの写し）には、ユーザーメニューの名前として初期管理者のメールアドレスが入りうる。管理外でコミット・共有しない。

### 5.6 `./gradlew e2eTest` の全件（Step 20）

1分22秒で 48 件が通った（010〜040 が 6、050 が 21、060 が 21）。030 のサイドバーの「管理」の確かめも変わらず通った。Mailpit は止めず、消していない。

## 6. 計画の9節の決定の反映

| 決定 | 反映 |
|---|---|
| 1 | ブランチを作った。統合は squash で、依頼者の承認の後に行う |
| 2 | 060 の番号と、共用の手伝い（`adminLogin.ts`・`invitationSeed.ts`・`invitationFixtures.ts`）を作った。決定 A で `loginPreferences.ts` も共用にした |
| 3 | 8列にした |
| 4 | 状態ごとの既知の違反の一覧を作った（5.2）。README に記載した |
| 5 | README の片付けの書き方を「手元でメールを見る」の2行にそろえた |
| 6 | Step 16 で止めて諮った（決定 A）。(a)〜(f) の場面は起きなかった |

## 7. Build and Test と後の単位に引き継ぐこと

- 計画の「Build and Test に引き継ぐこと」の表のとおり。カバレッジの実測、verify の時間（今回 6分2秒、B4 の後は 5分44秒）、060 の時間と組の結果、CSP、配信物の大きさ、招待の API の時間（k6）、配備と戻しの手順。
- **U6・U7 の検査（070・080）の組の当て方**: 決定 A にそろえる。ログインの後の画面では `frontend/e2e/support/loginPreferences.ts` の `routeLoginPreferences` で、ログインと復元の応答の `user.theme`・`user.fontSize` だけを書き換えて当てる。
  - U7 の NFR 設計 5.3 の `GET /api/me/preferences` の差し替えとの関係（どちらを使うか、併せて使うか）は、U7 の計画で決める。
  - ログインの前の画面（U6 の登録の完了など）は、U4 の切り替え方（初めのスクリプトで U4 の鍵を置く）のまま。
- 後の単位は、共用の手伝い `adminLogin.ts`・`invitationSeed.ts`・`invitationFixtures.ts`・`loginPreferences.ts` と、`axe.ts` の `splitKnownViolations` の第3引数（状態ごとの既知の違反の名前の一覧）を使う。
- 060 は招待を 21 件置き、ログインの監査の記録を残す。後のファイル（090 など）は、一覧の件数・順・監査の件数に頼らない。
- U6 の E2E-1 が U5 の画面を通ることは、U6 の計画で確かめる。U5 の `data-testid` は `invitation-` で始まる。

## 8. 依頼者に確かめたいこと

- 初回の JavaScript が 114.1 KB から 119.2 KB（gzip）に増えた（上限は置いていない。4.3）。
- Badge の種類の選び方（2節）。
- 375px の直し（`scrollIntoView`）。
- 計画に無いファイル2つ（2節）。
- `useInvitationAdmin.ts` の分岐のカバレッジが 82.4% で、U5 のファイルの中で最も低い（下限の 70% は満たす）。

## 9. コミットの区切りの案（依頼者の承認を得てから行う）

| 区切り | 件名 | 中身 |
|---|---|---|
| C1 | `B5 U5 日時の書式を共通の置き場へ移す（formatDateTime を shared/format へ、DSL は読み込み先だけ）` | `frontend/src/shared/format/`、`frontend/src/features/dsl/format.ts`・`format.test.ts`・3つの部品 |
| C2 | `B5 U5 招待の管理の画面（一覧・招待・送り直し・取り消し、文言 ja・en、機能の登録）` | `frontend/src/features/invitation/` のテストと `testing/` 以外 |
| C3 | `B5 U5 招待の管理の画面のテスト（純粋な関数の性質ベース・API・画面部品・画面・登録）` | `frontend/src/features/invitation/` の `*.test.ts(x)` と `testing/` |
| C4 | `B5 U5 招待の管理の画面の実際のブラウザの検査と画面の時間の測定（060、共用の手伝い、ログインの後の組の当て方）` | `frontend/e2e/060-invitation-accessibility.e2e.ts`、`frontend/e2e/support/` の4つと `axe.ts` |
| C5 | `B5 U5 README に招待の管理の画面と E2E の 060 を書く` | `README.md` |
| C6 | `B5 U5 コード生成の記録（計画の実施・Step 16 の決定・実測）` | 記録の `construction/u5-invitation-ui/code-generation/` の下 |

- どのコミットのメッセージの末尾にも `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。
- `develop` への squash の統合コミットのメッセージは次のとおり。

```
B5 U5 招待の管理の画面（一覧・招待・送り直し・取り消し、日時の書式の共通化、実際のブラウザの検査）

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
