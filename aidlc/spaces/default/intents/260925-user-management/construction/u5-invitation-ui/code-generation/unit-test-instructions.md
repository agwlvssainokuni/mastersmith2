# Unit Test Instructions — U5 招待の管理の画面（u5-invitation-ui）

U5 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の確かめ）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パス。U5 は画面の単位のため、バックエンドのテストを足さない。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | Vitest（既存）、jsdom | `frontend/vitest.config.ts`（`src/**/*.test.{ts,tsx}`、`e2e/` は対象外、`css: false`）。Node 24 の localStorage と重ならないよう `NODE_OPTIONS=--no-experimental-webstorage` を付ける（既存の `npm run test` と同じ） |
| 画面部品の描画と操作 | Testing Library（`@testing-library/react`）・user-event・jest-dom（既存） | `frontend/vitest.setup.ts`（jest-dom と vitest-axe の照合の登録、テストごとの片付け。変えない） |
| 描画の支え | 新しい `frontend/src/features/invitation/testing/renderInvitation.tsx` | 既存の `renderWithProviders` の中に、make-you-chic-ui の `ToastProvider`・`ModalStackProvider` と、U5 の文言だけを持つ登録を置いて描く（DSL の `testing/renderDsl.tsx` と同じ形）。画面の言語は既定の ja、選択で en（ブラウザの言語設定として渡す） |
| 応答の見本 | 新しい `frontend/src/features/invitation/testing/fixtures.ts` | `api/types.ts` の型を付けた `Invitation`・`InvitationPage` の見本と組み立ての関数 |
| アクセシビリティ（構造） | vitest-axe（既存） | 画面部品ごとに1件（`InvitationList`・`InvitationUnavailableAlert`・`InviteDialog`・`CancelConfirmDialog`・`InvitationAdminPage`）。`expect(await axe(container)).toHaveNoViolations()` |
| 性質ベースのテスト | fast-check（既存） | 全体の設定は足さない。失敗時は fast-check が `seed`・`path` を出力に示す（既存の `frontend/src/features/dsl/submitInput.test.ts` と同じ扱い） |
| 偽の時計 | Vitest の `vi.useFakeTimers` | 自動の読み直しが無いこと（NFR6.3）の確かめだけで使い、テストの後に `vi.useRealTimers` で戻す |
| 実際のブラウザの検査と測定 | Playwright（既存）＋ axe-core（既存、B4 で devDependencies に明示） | `frontend/playwright.config.ts`（変えない。`workers: 1`・Chromium だけ・`locale: 'ja-JP'`・reporter の `json` と仮の資格情報の確かめ）、新しい `frontend/e2e/060-invitation-accessibility.e2e.ts`、`frontend/e2e/support/`（U4 の手伝いと、U5 が足す `adminLogin.ts`・`invitationFixtures.ts`・`invitationSeed.ts`） |
| カバレッジ | `@vitest/coverage-v8`（既存） | `frontend/vitest.config.ts` の `thresholds`（行 80・分岐 70）。計測から外すのは `src/main.tsx`・型の宣言・テストのファイルだけ（変えない） |

新しい依存は足さない（NFR9.4）。Vitest・Playwright の設定のファイルは変えない。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U5 の置き場と、U5 が手を入れる既存の置き場（日時の書式の移動で読み込み先を変える DSL の部品とそのテスト）だけに絞る。

単体テスト（U5 の範囲。招待の管理の画面・日時の書式・移動で手を入れる DSL の4つのテストのファイル）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation src/shared/format src/features/dsl/format.test.ts src/features/dsl/DslStatusPanel.test.tsx src/features/dsl/DslConfirmDialog.test.tsx src/features/dsl/DslHistoryTable.test.tsx)
```

最初のテストより前の確かめ（Step 4）: 上のコマンドを、U5 のテストがまだ無い状態で実行し、既存のテストが通ることを確かめる。`src/features/invitation`・`src/shared/format` に当たるファイルはまだ無いが、DSL の4つのテストのファイルに当たるため「当たるテストが無い」にはならない。あわせて `(cd frontend && npx playwright test --list)` で E2E の一覧（010〜050）が読めることを確かめる。

層ごとの実行（Step 6・8・10・12・14。上のコマンドの一部）:

| Step | 対象 | コマンド |
|---|---|---|
| Step 6 日時の書式の移動 | 移した `formatDateTime` と、読み込み先を変えた DSL の部品 | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/format src/features/dsl/format.test.ts src/features/dsl/DslStatusPanel.test.tsx src/features/dsl/DslConfirmDialog.test.tsx src/features/dsl/DslHistoryTable.test.tsx)` |
| Step 8 業務処理（純粋な関数） | `paging`・`focusTarget`・`unavailable`・`inviteInput`・`failureMessage` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation/paging.test.ts src/features/invitation/focusTarget.test.ts src/features/invitation/unavailable.test.ts src/features/invitation/inviteInput.test.ts src/features/invitation/failureMessage.test.ts)` |
| Step 10 API | `invitationApi` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation/api)` |
| Step 12 画面部品 | `InvitationList`・`InvitationUnavailableAlert`・`InviteDialog`・`CancelConfirmDialog` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation/InvitationList.test.tsx src/features/invitation/InvitationUnavailableAlert.test.tsx src/features/invitation/InviteDialog.test.tsx src/features/invitation/CancelConfirmDialog.test.tsx)` |
| Step 14 画面・状態・登録 | `InvitationAdminPage`（`useInvitationAdmin` を通す）・`registration` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation/InvitationAdminPage.test.tsx src/features/invitation/registration.test.ts)` |

実際のブラウザの検査と測定だけ（Step 16。`verify` と CI の外。WAR と Chromium と Mailpit が要る）:

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && caffeinate -i npx playwright test e2e/060-invitation-accessibility.e2e.ts)
```

- 事前に `(cd frontend && npx playwright install chromium)`。
- 060 だけのときも Mailpit を起動しておく。E2E の WAR はメールを Mailpit へ送る設定で起動し、060 の測定のテストは招待を 21 件置いて Mailpit に 21 通送る（基盤の設計の N2、`cicd-pipeline.md` 4.3）。060 は Mailpit の API に書き込まず、メールを消さない。Mailpit は止めず消さない（README の手順のとおり開発者が片付ける）。
- 結果は `frontend/test-results/e2e-results.json`（json の報告）と `frontend/playwright-report/`（html の報告）。どちらも管理外で、コミット・共有しない。json に仮の資格情報が入らないことは既存の報告の部品（`frontend/playwright-secret-check-reporter.ts`）が確かめる。アクセストークン（`eyJ` で始まる値）と測定の招待のメールアドレス（`u5-perf-`）が入らないことは Step 16 で文字列の検索で確かめる。

統合の前（Step 20・Step 21）は単位の範囲ではなく全体を流す（計画のとおり）:

```bash
docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- テストの件数を報告するときは、実測の数字だけを報告する。バックエンドのテストが UP-TO-DATE で飛ばされないよう、verify には `:backend:cleanTest :backend:cleanIntegrationTest` を付ける（`project.md` の Testing Posture）。
- colima の環境変数を渡さないと対象DB のテストが SKIPPED になる（`project.md` の Testing Posture）。U5 のテストはコンテナを使わない。

## 3. テストの一覧（Standard の量）

| 部品 | テスト（対象と同じ場所） | 件数の目安 | 主に確かめること |
|---|---|---|---|
| 日時の書式 | `src/shared/format/formatDateTime.test.ts`（`src/features/dsl/format.test.ts` から移す） | 既存の件数のまま | ja・en の書式、時差の固定、読めない値はそのまま。移した後も DSL の3つの部品のテストが期待を変えずに通る（NFR9.8） |
| ページの計算 | `src/features/invitation/paging.test.ts` | 6 件＋fast-check 1 件 | ページの数（0・20・21・43 件）、件数の範囲、ページの補正、押したボタンが押せなくなるかの判定。性質: `from ≦ to ≦ total`・1ページ 20 件以下・補正は 1 以上ページの数以下 |
| フォーカスの行き先 | `src/features/invitation/focusTarget.test.ts` | 5 件 | 送り直す・受け口・一覧の見出し・空の表示の文への代わり |
| 警告の文の形 | `src/features/invitation/unavailable.test.ts` | 5 件 | 理由1つずつ・両方・知らない値だけ・混ざり・空 |
| 空の判定 | `src/features/invitation/inviteInput.test.ts` | 4 件＋fast-check 1 件 | 空・空白だけ・前後に空白のある値。性質: 空白だけの文字列はすべて空 |
| 失敗の文言の鍵 | `src/features/invitation/failureMessage.test.ts` | 6 件 | 知っている `code` の鍵、知らない `code`・`code` なし（4xx・5xx）・通信の失敗の一般の鍵、`detail` に影響されない |
| API | `src/features/invitation/api/invitationApi.test.ts` | 8 件 | 4つの要求のメソッド・パス・本文、決まった項目だけの値（`token`・`url` を足しても入らない）、`sendResult` と理由の知らない値、200・201 の読めない本文は通信の失敗・204 の空の本文は成功、失敗の受け渡し、`readPendingProblem`・`readUnavailableReasons` の型の確かめ |
| `InvitationList` | `src/features/invitation/InvitationList.test.tsx` | 8 件（場面をまとめる） | 列と値、文字で示す状態、ボタンの名前、トークン・URL を出さない、記号を文字として出す、読み込み中・空・読めなかった、行の処理中、招待を使えないとき、期限切れの行、目立たせた行、フォーカスの行き先、表の名前と見出し、Tab で届く、ページ送りの文と端、en で日本語の既定の文言が出ない、読み直しの間も Table が残る、vitest-axe |
| `InvitationUnavailableAlert` | `src/features/invitation/InvitationUnavailableAlert.test.tsx` | 5 件 | 理由ごとの文・両方・知らない値だけ・使えるときは描かない、vitest-axe |
| `InviteDialog` | `src/features/invitation/InviteDialog.test.tsx` | 8 件 | 言語の初期値（en の管理者で en）、フォーカス、空の誤りと結び付き、送信中の `aria-disabled`・`aria-busy` とフォーカス、送信中に閉じない、背景のクリックで閉じない、閉じるボタンの名前（ja・en）、値が残る、招待中の案内、Modal の中の知らせ、vitest-axe |
| `CancelConfirmDialog` | `src/features/invitation/CancelConfirmDialog.test.tsx` | 6 件 | `alertdialog`、本文のメールアドレス、はじめのフォーカス「やめる」、Esc・やめるで閉じる、背景で閉じない、要求中の `loading` と押せない「やめる」、閉じるボタンの名前、vitest-axe |
| `InvitationAdminPage`（`useInvitationAdmin` を通す） | `src/features/invitation/InvitationAdminPage.test.tsx` | 16〜20 件 | 読み込み・ページ送り・読み上げ・最後のページの補正、重なった読み直しと外した後の答え、偽の時計で自動の読み直しが無い、招待・送り直し・取り消しの応答ごとの動き（機能設計 6.2〜6.4 の行を1つずつ）、失敗の知らせの置き換え、押せなさと警告、en の文言、`detail` の目印が出ない、403 の表示、ブラウザの保存・URL・`console` にメールアドレス・氏名が出ない、vitest-axe |
| 機能の登録と文言 | `src/features/invitation/registration.test.ts` | 5 件 | 画面とサイドバーの値、既存の登録と合わせた `validateRegistrations`、ja・en の鍵のそろい、値に `24` と HTML のタグの形が無い |
| 実際のブラウザの検査と測定 | `frontend/e2e/060-invitation-accessibility.e2e.ts` | 21 件（20 組＋測定） | 組ごとに一覧（行あり）・招待の入力の Modal・取り消しの確かめの Modal（(a)・(b) は警告も）の axe の違反 0 件（既知の違反は決定 4 の一覧のとおり）と横のはみ出し無し、`color-contrast`・`scrollable-region-focusable` が流れたこと、CSP の違反 0 件、招待の管理の `POST` が送られないこと、375px で Tab でフォーカスした「取り消す」が表示の幅の中。測定は5回の一覧と次のページの時間（失敗させない）と、本物の一覧の応答と見本の一致・CSP の違反 0 件（失敗させる） |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`describe`・`it`・`test`・`test.step`）は英語で書く。テストのデータは日本語でよい。新しいファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。

`team.md` の必須のテストと部品 7節のうち U5 が受け持つもの:

| 必須のテスト・確かめ | 確かめるテスト |
|---|---|
| 認可: 画面でボタン・メニューを隠すことをサーバー側の判定の代わりにしない（サーバー側の 401・403・成功は U3 の結合テスト） | `registration.test.ts`（`access: 'ADMIN'`・`visibleWhen: 'ADMIN'`）、`InvitationAdminPage.test.tsx`（403 の表示） |
| 秘密情報の漏えい: 招待のトークン・URL を画面に出さない、メールアドレス・氏名をブラウザの保存・URL・`console` に出さない | `InvitationList.test.tsx`・`invitationApi.test.ts`（NFR1.1）、`InvitationAdminPage.test.tsx`（NFR2.1） |
| 画面に差し込む値のエスケープ（React の文字として描く） | `InvitationList.test.tsx`・`InvitationAdminPage.test.tsx`（NFR9.1） |
| 性質ベースのテスト（fast-check）を純粋な関数に | `paging.test.ts`・`inviteInput.test.ts`（NFR9.7） |
| 画面部品ごとのアクセシビリティ検査 | 5つの画面部品の vitest-axe（NFR7.2）、060（NFR7.3・NFR7.4） |
| 既存の画面のテストと E2E が通り続ける | Step 6（DSL の4つのテストのファイル）、`./gradlew verify`（Step 21）、`./gradlew e2eTest`（Step 20） |

## 4. カバレッジの目標

- フロントエンドの全体: 行 80% 以上・分岐 70% 以上（既存の `thresholds`。`./gradlew verify` の `frontendCoverage` で判定する）。フロントエンドの下限は全体の合計だけで、ディレクトリごとの下限は無いが、U5 の新しいファイル（`shared/format/formatDateTime.ts` を含む）もそれぞれ行 80%・分岐 70% を目安にし、下回るファイルがあればテストを足す（Step 14・18）。
- 計測の除外を増やさない。`frontend/e2e/` の検査と手伝いは既存どおり Vitest の計測の対象外（`vitest.config.ts` の `include` が `src/**`）。`src/features/invitation/testing/` のテストの支えは今までどおり計測に入る。
- U4 の後の実測（55 ファイル 408 件、行 98.04%・分岐 94.27%）を基準に、Step 1・Step 21 で実測して比べる。
- U5 の範囲のカバレッジを見るとき（目安。下限の判定は verify で行う）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation src/shared/format src/features/dsl/format.test.ts src/features/dsl/DslStatusPanel.test.tsx src/features/dsl/DslConfirmDialog.test.tsx src/features/dsl/DslHistoryTable.test.tsx --coverage --coverage.include='src/features/invitation/**' --coverage.include='src/shared/format/**')
```

  設定のファイルの下限（行 80・分岐 70）はこの範囲の値にもそのまま当たる（下限を外す指定はしない）。範囲の外のテストが通る分を含まないため値は目安で、全体の下限の判定は `./gradlew verify` の `frontendCoverage` で行う。報告は `frontend/coverage/index.html`。

## 5. 差し替え（モック・スタブ）の方針

- **API（画面部品と画面のテスト）**: `InvitationAdminPage` の任意の props `api` に、`invitationApi` と同じ形の関数を渡して差し替える（DSL の画面のテストと同じ形）。答えを返さない約束で止め、送信中の表示・`aria-busy`・フォーカス・閉じないことを見てから答える。重なった読み直しは、2つの約束を作って先の答えを後に返す。失敗は ApiClient の `ApiError`（`kind`・`status`・`code`・`problem`）の形の値で返す。
- **API（`invitationApi.test.ts`）**: 既存の `apiClient.test.ts` と同じく `vi.stubGlobal('fetch', ...)` で差し替え、`resetApiClient` をテストごとに呼ぶ。ApiClient の中（トークン・401 の更新・`Accept-Language`）は既存と U4 のテストが確かめるため、U5 では見ない。
- **make-you-chic-ui**: 差し替えない。本物の Table・Modal・Button・RadioGroup・Alert・Badge・`useToast` の中で描き、役割・名前・属性（`aria-disabled`・`aria-busy`・`role="alertdialog"`・`lang`）で確かめる。Table の中の class・`data-testid` は探さない。
- **表示の設定**: `renderWithProviders` の既定（見た目の設定は答え済み・当てる値なし）のまま使い、画面の言語はブラウザの言語設定（既存の `stubBrowserLanguages` と同じ渡し方）で ja・en を切り替える。U5 は `setPreview`・`setLanguage` を呼ばない。
- **時差**: 部品と `formatDateTime` に時差（例 `Asia/Tokyo`・`UTC`）を渡して固定する（`frontend/src/features/dsl/format.test.ts` の前例）。
- **時刻**: U5 は画面の時計で期限を判定しない（応答の `expired` を出す）。`sleep` と実時刻に頼らず、`findBy*`・`waitFor` と約束の解決で待つ。偽の時計は自動の読み直しが無いことの確かめだけで使う。
- **`console` の見張り**: `vi.spyOn(console, 'log' | 'info' | 'warn' | 'error' | 'debug')` で5つを見張り、どの流れでも呼ばれないことを見る（`vitest.setup.ts` の `vi.restoreAllMocks()` で戻る）。呼ばれたときは引数にメールアドレス・氏名が無いことも見て、失敗の説明に出す。
- **ブラウザの保存と URL**: jsdom の localStorage・sessionStorage をそのまま使い、テストの前後で `localStorage.clear()`・`sessionStorage.clear()`。流れの後にすべての鍵の値を読んでテストのメールアドレス・氏名が無いことを見る。URL は `window.location` のパス・問い合わせ・`#` の後を比べる。
- **実際のブラウザの検査（060）**: サーバーは本物の WAR。検査の組では一覧の `GET`（問い合わせ `page=` の付いたもの）だけを `page.route` で見本の答えに差し替え（そのコンテキストのページの中だけで効く）、`POST` は差し替えない。ブランドカラーの組は U4 の手伝い（`prepareCombo`）で `/api/appearance` を差し替える。ログインは本物（ログインの画面のフォームから初期管理者で）。測定のテストは一覧と `/api/appearance` を差し替えず本物の応答を使い、招待は要求の口で招待の API から置く。

## 6. テストのデータ

- **メールアドレス**: `example.com`・`example.test` などの予約されたドメインだけを使う（例 `hanako@example.test`・`invitee-01@example.com`）。実在の宛先を書かない。漏えいの確かめでは見分けやすい値（例 `invitation-leak-check@example.test`）を使い、短い文字列の偶然の一致で確かめが崩れないようにする。記号の確かめには `<`・`>`・`&` を含む値（例 `a<b>&c@example.test`。サーバーでは受け付けられない形だが、画面の描き方の確かめのための見本）を使う。
- **氏名**: 日本語の架空の氏名（例「山田 花子」）と、漏えいの確かめ用の見分けやすい氏名（例「招待確認用の氏名」）。`invitedBy` が空の行は、今の API では起きない状態を確かめる防御のための行であるとコメントを書く（NFR 設計の承認の場の U5 R-01）。
- **日時**: ISO 8601 の UTC の固定の値（例 `2026-09-25T12:00:00Z`）。期限内・期限切れは応答の `expired` で決め、画面の時計と比べない。
- **トークン・URL の目印**: `token`・`url` の項目に見分けやすい値（例 `leak-check-token-value`・`http://leak-check.example.test/register#token=x`）を入れ、画面の文字と属性に出ないことを見る。Gitleaks に当たらない低いエントロピーの値にする。
- **`detail` の目印**: `detail` に見分けやすい文字（例 `server-detail-marker`）を入れ、画面に出ないことを見る。
- **一覧の件数**: ページの境目（0・1・20・21・40・41 件）と、`items` が空で `total` が 1 以上の場合。
- **実際のブラウザの検査（060）**: 見本の行は `example.com` の下の固定のメールアドレスと架空の氏名（`frontend/e2e/support/invitationFixtures.ts`）。測定の招待は実行ごとに重ならない `u5-perf-<印>-<番号>@example.com`（印は時刻と乱数、21 件、言語 `ja`）。E2E の初期管理者（`e2e-admin@example.com`、パスワードは実行ごとに作る）は読むだけで、テストの題・`test.step` の題・注記・添付に入れない。測定で得たアクセストークンはテストの変数だけに持つ。

## 7. 性質ベースのテストの種

- fast-check の失敗時の乱数の種（`seed`）と道（`path`）は、テストの出力（失敗の詳細）に出る。再現するときは、その値を `fc.assert(property, { seed, path })` に一時的に書いて Step 8 のコマンドで実行し、直した後に外す（既存の `frontend/src/features/dsl/submitInput.test.ts` と同じ扱い。全体の設定は足さない）。
- 対象は純粋な関数だけ: ページの計算（任意の全件数と有効なページで、件数の範囲の始まり ≦ 終わり ≦ 全件数、1ページの件数 ≦ 20、補正の結果が 1 以上ページの数以下）と、空の判定（空白だけの文字列はすべて空）（`team.md` の Testing Posture、NFR9.7）。

## 8. 実際のブラウザの検査の読み方（060）

- 組は U4 の手伝い（`frontend/e2e/support/displayCombos.ts`）の 20 組と同じ: (a) テーマ2×文字の大きさ3（`blue`、既定の幅）の6組、(b) ブランドカラー4×テーマ2（`md`、既定の幅）の8組、(c) テーマ2×文字の大きさ3（`blue`、375px × 812px）の6組。組ごとに1つのテストで、ログインの画面から管理者でログインし、サイドバーの「利用者の招待」から開く。
- 状態は、一覧（行あり）→ 招待の入力の Modal → 取り消しの確かめの Modal（どの組も）、警告（(a)・(b) だけ。差し替えの答えを替えてページを読み込み直す）。Modal は開くだけで、要求を送らない。
- axe の合否は想定外の違反（`violations` のうち既知の違反の一覧の外）0 件。既知の違反は、green・orange の組の `color-contrast` で、primary の Button で、状態ごとの一覧に名前（`data-testid`）が書かれたものだけ（計画の9節の決定 4）。既知の違反が一覧と一致しない（消えた・増えた）ときも失敗にする。判定できなかった要素（`incomplete`）は失敗にせず、規則の名前と件数を注記に残す。
- 画面の時間（一覧の1ページ目の 20 行目が見えるまで・次のページの最初の行が見えるまで）は、測定のテストの注記と添付（5回の値のミリ秒と目標以内の回数）に残る。時間では失敗しない。値は Build and Test が json の報告から写す。招待を使える設定が無いときは測定を `test.skip` にして理由を注記に残す（念のための備え。E2E の WAR には SMTP とベース URL が渡る）。
- 1回の実行でログインは約 25 回（20 組と測定の5回）、招待は 21 件、Mailpit に届くメールは 21 通。置いた招待とログインの監査の記録は、同じ WAR と一時の内部DB を使う後のファイルに残るため、後のファイルは件数・順・監査の件数に頼らない。
- 失敗したときのトレース（`trace: 'retain-on-failure'`）には実行ごとの仮の資格情報・アクセストークン・見本のメールアドレスが含まれうるため、コミット・共有しない（`cicd-pipeline.md` 5節）。
