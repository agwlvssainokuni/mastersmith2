# Code Summary — U6 登録の完了の画面（u6-registration-ui）

Bolt B5 の2番目の単位として、承認済みの `code-generation-plan.md` の Step 1〜23 を実行した記録。作業のブランチは `develop` の先頭 `dc21a5a` から作った `feature/260925-user-management-b5-u6`（コミットはしていない。統合は squash）。パスはリポジトリのルートからの相対パス、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）。数値はすべてこの段で実測したもの。

## 1. 作ったもの・手を入れたもの

### 1.1 入力の確かめの関数（`frontend/src/shared/validation/`、新しい置き場、U7 と共用）

| ファイル | 中身 |
|---|---|
| `limits.ts` | `DISPLAY_NAME_MAX_CODE_POINTS`（254）・`PASSWORD_MIN_CODE_POINTS`（12）・`PASSWORD_MAX_UTF8_BYTES`（72） |
| `codePoints.ts` | `countCodePoints`（UTF-16 の単位を見て組になったサロゲートだけを1つと数える独自の数え方）・`utf8ByteLength`（TextEncoder と同じ。組になっていないサロゲートは3バイト）・`trimDisplayName`（前後の Unicode の White_Space だけを除く）・`isWhiteSpaceCodePoint`。White_Space の集合は `backend/src/main/java/cherry/mastersmith/user/domain/DisplayName.java` の `isWhiteSpace` と同じ 25 文字（U+0009〜U+000D・U+0020・U+0085・U+00A0・U+1680・U+2000〜U+200A・U+2028・U+2029・U+202F・U+205F・U+3000）で、生成の中で突き合わせた |
| `validateDisplayName.ts` | `validateDisplayName`・`DisplayNameProblem`（`required` → `tooLong` → `invalidCharacter` の順。Cc・Cf は `\p{Cc}`・`\p{Cf}`） |
| `validatePassword.ts` | `validateNewPassword`・`validatePasswordConfirmation`・`NewPasswordProblem`・`PasswordConfirmationProblem` |
| テスト | `codePoints.test.ts`・`validateDisplayName.test.ts`・`validatePassword.test.ts`（3 ファイル 25 件。fast-check の性質ベースを含む） |

### 1.2 登録の完了の画面（`frontend/src/features/registration/`、新しい置き場）

| ファイル | 中身 |
|---|---|
| `registration.ts` | 機能の登録（`featureId: 'registration'`、`REGISTRATION_PATH = '/register'`、遅延読み込み、`STANDALONE`・`PUBLIC`、`role` なし、サイドバー・ユーザーメニューの項目なし） |
| `messages.ts` | `registrationMessages`（機能設計 7節の 34 の鍵、ja・en） |
| `registrationApi.ts` | `verifyRegistration`・`completeRegistration`・`REGISTRATION_VERIFY_PATH`・`REGISTRATION_COMPLETE_PATH`・`VerifiedInvitation`・`CompleteRegistrationRequest`。確かめの成功の本文の形の確かめ（形が違えば通信の失敗の `ApiError`） |
| `registrationToken.ts` | `readRegistrationToken(hash)` |
| `failureKind.ts` | `verifyFailureKind`（`unavailable`・`loadFailed`）・`completeFailureKind`（`unavailable`・`validationFailed`・`submitFailed`）。状態コードと `code` だけを読む |
| `formProblems.ts` | `checkRegistrationForm`（項目ごとの文言の鍵と最初の誤りの項目）と、フォームの値の型 `RegistrationValues`・`RegistrationFailure`・`RegistrationFocusTarget` |
| `useRegistration.ts` | 状態・操作・副作用（部品 4節） |
| `RegistrationPage.tsx`・`RegistrationPage.css` | Card・アプリ名・h1、状態ごとの子。CSS は状態ごとの表示の共通の配置（`registration-section`・`registration-actions`・`registration-link` など）も持つ |
| `RegistrationStatus.tsx`・`RegistrationUnavailable.tsx`・`RegistrationLoggedInNotice.tsx` | 確かめ中・読み込めない・使えないリンク・ログイン中の案内 |
| `RegistrationForm.tsx`・`RegistrationForm.css` | フォーム（3つの RadioGroup は `legend` 付き、言語の選択肢に `lang`、送信中は Button の `loading` と欄の `readOnly`、描画の確定の後のフォーカスの移し） |
| `testing/fixtures.ts`・`testing/renderRegistration.tsx` | テストの補助（偽のサーバー `installFakeServer`、答えを後で決める `deferred`、見本の値、URL の確かめ用の `RegistrationLocationProbe`） |
| テスト | `registrationToken.test.ts`・`failureKind.test.ts`・`formProblems.test.ts`・`registrationApi.test.ts`・`RegistrationStatus.test.tsx`・`RegistrationUnavailable.test.tsx`・`RegistrationLoggedInNotice.test.tsx`・`RegistrationForm.test.tsx`・`RegistrationPage.test.tsx`・`registration.test.tsx`（10 ファイル 91 件） |

### 1.3 実際のブラウザの検査と E2E-1（`frontend/e2e/`）

| ファイル | 中身 |
|---|---|
| `070-registration-accessibility.e2e.ts`（新しい） | 20 組 × 2 状態（`ready`・`unavailable`）の axe と横のはみ出し。ログインしない。確かめの API の POST だけを見本に差し替え、完了は送らない |
| `090-invitation-registration-flow.e2e.ts`（新しい） | E2E-1（W13）。5回の測りと3つの確かめ、本物の確かめの応答と見本の形の照合 |
| `support/mailpit.ts`（新しい） | `MAILPIT_API_URL`（`http://127.0.0.1:8025`）と `findInvitationLink(address)`（GET だけ、Node の fetch） |
| `support/registrationFixtures.ts`（新しい） | `VERIFY_SAMPLE`（`VerifiedInvitation` の型付き）・`A11Y_SAMPLE_TOKEN = 'a11y-sample-token'`・`shapeOf` |
| `support/axe.ts`（手を入れた） | `REGISTRATION_KNOWN_VIOLATIONS`（`ready: ['registration-submit-button']`・`unavailable: []`）と `RegistrationAxeState` を足しただけ。既存の一覧と判定は変えていない |

### 1.4 文書とビルド

- `README.md`: 「ビルドした WAR での画面の確認（E2E）」の表に 070・090 の行、「070 について」「090 について」、新しい節「登録の完了の画面（U6）」、「画面の表示の設定（U4）」の既知の制約に U6 の行と 070 の扱い。
- `frontend/src/features/README.md`: 「入力の確かめの関数（`src/shared/validation/`）」の節。
- `build.gradle.kts`: `mailpitInfoUrl` の上に `frontend/e2e/support/mailpit.ts` の `MAILPIT_API_URL` を参照するコメント1行だけ（動きは変えていない）。
- 変えていないもの: 骨組み（`frontend/src/app/`）・ApiClient・`frontend/src/features/auth/`・`frontend/src/features/invitation/`・`playwright.config.ts`・`playwright-secret-check-reporter.ts`・`vitest.config.ts`・`tsconfig.json`・`package.json`・`package-lock.json`・既存の E2E（010〜060）と既存の手伝い・`backend/`・`application.yaml`・`SecurityConfig`・`compose.yaml`・`vendor/`。

## 2. 上流・計画との差

承認済みの文書は書き換えず、差をここに記録する（`project.md` の決まり）。

| # | 対象 | 承認済みの形 | 実際の作り | 理由 |
|---|---|---|---|---|
| 1 | ログアウトの後に確かめへ移る仕組み（W3 の4） | 部品 4.3 の副作用「ログイン状態が未ログインになったら `verifying` へ移す」 | 状態は `loggedIn`・`loggingOut` のまま、未ログインを受けた描画で画面の状態を `verifying` と決め（描画の中で導く）、確かめの答えで状態を移す | 副作用の中で同期に状態を変える形が、フロントエンドのリンタの決まり（`react-hooks/set-state-in-effect`）で止まるため。動きは設計のとおり（ログアウトの完了と更新の失敗の知らせのどちらでも確かめる） |
| 2 | 確かめの古い答えの捨て方 | 部品 4.1「番号と印」 | 確かめは副作用の後始末で無効にする形（次の確かめ・状態の移り変わり・部品が外れたときに無効）。完了は番号と部品の印 | 1 と同じ決まりのため、確かめの送信を副作用の中に置いた。StrictMode の二重の実行でも最後の答えだけを使う |
| 3 | 関数の名前（「コード生成で決める」とされたもの） | 部品 2節は役割だけ | `readRegistrationToken`・`verifyFailureKind`・`completeFailureKind`・`checkRegistrationForm`、`codePoints.ts` に `isWhiteSpaceCodePoint` を足した | 確かめ・完了で行き先の型が違うため振り分けを2つに分けた（判定の置き場は `failureKind.ts` の1か所のまま） |
| 4 | フォームの値の型の置き場 | 部品 4.1 の `values`（置き場は書いていない） | `formProblems.ts` に `RegistrationValues`・`RegistrationFailure`・`RegistrationFocusTarget` を置いた | フックと画面部品の両方が読む型で、部品のファイルに置くとフックが部品に依存するため |
| 5 | 「ログインの画面へ」のリンクの色 | 部品 5節「ボタンの見た目にしない」だけ | 文字の色（`--color-text`）に下線 | primary の色にすると green・orange の組でコントラストの既知の違反が増えるため |
| 6 | ラジオの選択肢の並び | 画面イメージ S2 は横並び | 自分のクラス（`registration-radio-group`）で横に並べて折り返す。make-you-chic-ui の内部のクラスは上書きしない | 画面イメージのとおり。375px でもはみ出しは無かった |
| 7 | `RegistrationPage.test.tsx` の件数 | 計画は 20〜26 件 | 34 件（`it.each` の行を1件と数える） | 画面の確かめの境界（7 行）・確かめの失敗（3 行）・完了の失敗（3 行）を行ごとに数えたため。目安を超える側で、減らしていない |
| 8 | 090 のサイドバーのリンクの名前 | 計画の Step 17「管理」「利用者の招待」「DSL の管理」 | 「管理」「利用者の招待」「DSL」 | 今の DSL の項目の文言（`dsl.nav.label`）は「DSL」のため |
| 9 | 090 のリンクを開く操作 | 計画「リンクは `page.goto` のまま開く」 | `page.goto` を小さな関数 `openLink` で包み、開けないときの失敗の知らせを種類だけの文に置き換えた | Playwright の `goto` の失敗の知らせは URL（トークン）を含み、json の結果に載りうるため（基盤の設計の N9）。HTML の報告とトレースに載ることは受け入れたまま |
| 10 | 090 の招待の確かめ | 計画「Modal が閉じ、失敗の知らせが無い」 | 加えて招待の POST の状態コードが 201 であることを見る。言語は `日本語` のラジオを明示で選ぶ | 送信の結果の確かめを強くするため（本文は読まない） |
| 11 | Mailpit からの取り出し | 基盤の設計 4.3「HTML の href と本文の文字の URL が同じ」 | HTML の href・HTML の本文の文字（タグを除く）・Mailpit の `Text` の3つを比べる。HTML の文字参照は戻してから比べる | 実物の Mailpit は `Text` も返したため（3.4節） |
| 12 | 070 の既知の違反の一覧 | 計画「Step 16 の最初の実行の結果で確かめてから書く」 | 見込み（`ready` が `registration-submit-button`、`unavailable` は無し）を先に書いて1回目を流し、一致したことで確かめた | 一覧と一致しないときは失敗する作りのため、1回目の結果がそのまま確かめになる |
| 13 | ページのテストの `console` の見張り | 計画「どの流れでも呼ばれない」 | 各テストの後に5つの `console` を確かめる。jsdom が axe の色の検査で出す「Not implemented: HTMLCanvasElement…」だけは除く | この画面のコードの出力ではないため |
| 14 | 基盤の設計の「1か所の定数」 | 2か所で値を持たない | `mailpit.ts` と `build.gradle.kts` の2か所（互いにコメントで参照） | 計画の9節の決定 3（承認済み） |
| 15 | Mailpit の片付けの書き方 | 基盤の設計 `docker compose rm -sf mailpit` | README は「手元でメールを見る」の2行のまま | 計画の8節（U5 の決定 5） |

- 機能どうしの依存: `features/registration` → `features/auth/authSession` の `logout` の1本（機能設計 10節）。テストでは `features/auth` の `LoginForm`・`loginStateProvider`・`registration` も読む（テストだけ）。

## 3. Step 2 の前提の確かめ（読み取りだけ）と実物の形

### 3.1 前提（すべて計画の想定どおりで、9節の決定 5 の (a) には当たらなかった）

- 固定先: `vendor/make-you-chic-ui` が `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`、`vendor/java-mustache-processor` が `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（Step 1 と Step 19 で同じ）。
- make-you-chic-ui: RadioGroup（`legend` を渡すと `fieldset`・`legend`、`options[].label` は ReactNode、`lang` は選択肢の文字の `span` に付く、`value`・`onChange` で外から渡せる。`data-testid` は `radio-group` 固定のため包む `div` に `registration-*` を付けた）、Button（`loading` で `aria-disabled`・`aria-busy`、押すと `preventDefault` して `onClick` を呼ばない、`type`・`data-testid` は上書きできる）、FormField（`label` は文字列、`error` は `role="alert"` の `span`、`helperText`・`required`、`aria-describedby` の結び付け）、TextInput（`forwardRef`）、Alert（`danger`・`warning` は `role="alert"`、`info`・`success` は `role="status"`）、Card。
- U4 の口: `handOffToLogin(email)`（モジュールの変数だけ）、`LoginForm` の `takeLoginHandoff`・`clearLoginHandoff`・`login-form-registered-alert`、`useDisplaySettings()` の `setPreview`・`clearPreview`・`setLanguage`、`saveBrowserDisplaySettings`、`LANGUAGE_NAMES`・`THEME_CHOICES`・`FONT_SIZES`、骨組みの文言 `app.name`・`display.theme.*`・`display.fontSize.*`、`TOKENLESS_API_PATHS` に `/api/registration/verify`・`/api/registration/complete`。
- U3 の API: `TokenRequest(token)`、`InvitationViewResponse(email, language)`、`CompleteRequest` の7項目、完了は 204、400 `VALIDATION_FAILED`（`fieldErrors` 付き）、404 `REGISTRATION_LINK_INVALID`。
- 振り分けと配信: `decideRoute` は PUBLIC を常に表示、見つからない URL はログイン中なら NOT_FOUND、ADMIN は管理者でなければ NOT_FOUND。`WebConfig` は画面の URL に `index.html` を返す。`NotFoundPage` は `not-found-page`。ユーザーメニューの名前は氏名。
- `logout` は API の失敗を捕まえて画面の側の破棄を必ず行い、例外を外へ出さない。`loginStateProvider` は `subscribe` で知らせ、`LoginStateGate` が未ログインを渡す。
- リンタ: ESLint は react-hooks の推奨・`export default` と `enum` の禁止・`no-implied-eval` だけ、oxlint に読み込みの制限の決まりは無い（機能どうしの読み込みを止める決まりは無い）。

### 3.2 実行の準備（Step 4）

- 単体のコマンドは `--passWithNoTests` で終了の状態 0、`loginHandoff.test.ts` は 5 件通過、`npx playwright test --list` は 6 ファイル 48 件、`parsePath('/register#token=abc')` の `hash` は `#token=abc`（MemoryRouter の場所にフラグメントが入る）。

### 3.3 突き合わせ

- `shared/validation` の関数の名前と誤りの種類は、機能設計の 6節と U7 の NFR 設計 `security-design.md` の 3.3 の「寄せる理由」（`required`・`tooShort`・`tooLong`・`invalidCharacter`・`mismatch`）と一致する（作った後に確かめた）。

### 3.4 Mailpit の API の実物の形（Step 17、基盤の設計の N7・C-D4）

値は出さず、項目の名前だけを確かめた。起動していた Mailpit は 090 の前はメッセージが 0 通だったため、`search`・`message` の形は 090 の1回目の後に確かめた。設計の想定と一致し、取り出しの部品を直す必要は無かった。

| API | 項目の名前 |
|---|---|
| `GET /api/v1/info` | `Database`・`DatabaseSize`・`LatestVersion`・`Messages`・`RuntimeStats`・`Tags`・`Unread`・`Version` |
| `GET /api/v1/search?query=to:"…"` | `count`・`messages`・`messages_count`・`messages_unread`・`start`・`tags`・`total`・`unread`。`messages[]` は `Attachments`・`Bcc`・`Cc`・`Created`・`From`・`ID`・`MessageID`・`Read`・`ReplyTo`・`Size`・`Snippet`・`Subject`・`Tags`・`To`・`Username`、`To[]` は `Address`・`Name` |
| `GET /api/v1/message/{ID}` | `Attachments`・`Bcc`・`Cc`・`Date`・`From`・`HTML`・`ID`・`Inline`・`ListUnsubscribe`・`MessageID`・`ReplyTo`・`ReturnPath`・`Size`・`Subject`・`Tags`・`Text`・`To`・`Username`（`HTML`・`Text` とも空でない）。無い ID は 404 |

## 4. 実測

### 4.1 `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）

| 項目 | Step 1（変更の前） | Step 23（変更の後） |
|---|---|---|
| 結果・時間 | 成功、6分43秒 | 成功、6分5秒 |
| バックエンドの単体テスト | 151 クラス 1,234 件（失敗 0・飛ばし 0） | 151 クラス 1,234 件（失敗 0・飛ばし 0） |
| バックエンドの結合テスト | 113 クラス 562 件（失敗 0・飛ばし 0） | 113 クラス 562 件（失敗 0・飛ばし 0。対象DB のテストも SKIPPED なし） |
| フロントエンドのテスト | 68 ファイル 509 件 | 81 ファイル 625 件（＋13 ファイル・＋116 件） |
| フロントエンドのカバレッジ（全体） | 文 97.51%・分岐 93.73%（1,048/1,118）・関数 97.91%・行 97.6%（1,631/1,671） | 文 97.39%（2,016/2,070）・分岐 93.06%（1,222/1,313）・関数 97.88%・行 97.54%（1,946/1,995） |
| バックエンドのカバレッジ（JaCoCo の合計） | 行 98.78%・分岐 94.38% | 行 98.78%・分岐 94.38%（変わらない） |
| Gitleaks | 212 コミット、漏れなし | 212 コミット、漏れなし。加えて新しい・変えたファイルごとに `gitleaks dir` で漏れなし |
| OSV-Scanner | UP-TO-DATE | 実行された。失敗の条件 0 件・警告 0 件 |
| `vendorUnchanged` | 成功 | 成功 |

- Step 23 の1回目は `frontendFormatCheck` で `frontend/src/features/README.md` の表の桁そろえ（Prettier）が止め、3 秒で失敗した。Prettier で直して流し直した結果を基準とした。
- Step 20 の1回目は `frontendLintCss` が `RegistrationForm.css` の `column-gap`・`row-gap`（`gap` の省略形を使う決まり）で止めた。`gap` にして通した。

### 4.2 U6 の新しいファイルのカバレッジ（Step 20 の `frontendCoverage`、行・分岐）

| ファイル | 行 | 分岐 |
|---|---|---|
| `features/registration/RegistrationForm.tsx` | 100% | 78.57% |
| `features/registration/RegistrationPage.tsx` | 86.66% | 85.71% |
| `features/registration/useRegistration.ts` | 95.45% | 83.05% |
| `features/registration/testing/fixtures.ts` | 94.59% | 70% |
| `shared/validation/codePoints.ts` | 100% | 95% |
| そのほかの U6 のファイル（`RegistrationLoggedInNotice.tsx`・`RegistrationStatus.tsx`・`RegistrationUnavailable.tsx`・`failureKind.ts`・`formProblems.ts`・`messages.ts`・`registration.ts`・`registrationApi.ts`・`registrationToken.ts`・`testing/renderRegistration.tsx`・`limits.ts`・`validateDisplayName.ts`・`validatePassword.ts`） | 100% | 100% |

すべて目安（行 80%・分岐 70%）に届いた。計測の除外は増やしていない。

### 4.3 配信物の大きさ（NFR6.3、`performance-design.md` 3節）

| 項目 | Step 1 | Step 19・23 |
|---|---|---|
| 初回の JavaScript（gzip） | 119.2 KB（`index` 102.7 KB＋`useTranslation` 16.6 KB） | 121.9 KB（`index` 83.7 KB＋`useTranslation` 16.6 KB＋`I18nProvider` 21.6 KB）。500 KB の目安の警告なし |
| `frontend/dist/assets/` の JavaScript | 7 ファイル 453,707 バイト | 10 ファイル 472,521 バイト |
| `backend/build/libs/mastersmith.war` | 101,578,791 バイト | 101,587,150 バイト |

- 登録の完了の画面の塊 `RegistrationPage-*.js` は動的な読み込みの塊（`isDynamicEntry`）で、入口から静的にたどれない。11,888 バイト（gzip 3,869 バイト）、CSS 1,456 バイト（gzip 472 バイト）。
- `shared/validation` の中身は登録の完了の画面の塊にだけ入り、入口に入らない。入口に入ったのは機能の登録と文言（ほかの機能と同じく登録は最初に読み込む）。
- Vite が、入口と遅延読み込みの塊が共有する部分を `I18nProvider-*.js` と `loginHandoff-*.js`（108 バイト）に分けたため、初回の JavaScript のファイルが 2 つから 3 つになり、合計が 2.7 KB 増えた。
- Step 19 の確かめ: `application.yaml`・`SecurityConfig.java`・`frontend/package.json`・`frontend/package-lock.json` に差分なし、`dist/index.html` は `src` 付きの `<script>` 1つだけで `<style>` なし、`features/registration` に外部の URL なし（ライセンスの URL を除く）。

## 5. 実際のブラウザの検査（070）の結果（Step 16・22）

- 1回目: 20 件通過、27.1 秒（壁の時計で 28 秒）。2回目: 20 件通過、26.5 秒。組・状態の成否は同じ。全件の中（Step 22）では 18.3 秒。
- 40 の状態（20 組 × `ready`・`unavailable`）すべてで、想定外の違反 0 件、`incomplete` なし、横のはみ出しなし（既定の幅は 1,280/1,280px、375px の組は 375/375px）、CSP の違反 0 件、確かめの要求は `ready` の1回だけ、完了の要求は 0 件。
- 既知の違反: (b) green light・green dark・orange light・orange dark の `ready` で `color-contrast registration-submit-button`（primary の Button）だけ。`unavailable` の状態は無し。`REGISTRATION_KNOWN_VIOLATIONS` と一致した。9節の決定 5 の (b)・(c) には当たらなかった。
- json の結果（`frontend/test-results/e2e-results.json`）に `a11y-sample-token`・`e2e-a11y-invitee`・`#token=`・`token=`・`eyJ` は 0 件。報告の部品の仮の資格情報の確かめも通った。
- 070・090 を単独で流したのは Step 20 の CSS の直し（`gap` の省略形。見た目は同じ）の前の WAR。直した後の WAR で Step 22 の全件を流し直し、すべて通った。

## 6. E2E-1（090）の結果と閲覧の履歴（A9）

### 6.1 090（Step 18・22）

| 回 | 結果 | リンクを開いてからフォームまで（ミリ秒、5回） | 目標（2,000）以内 |
|---|---|---|---|
| Step 18 の1回目 | 通過（テスト 9.9 秒、全体 17.6 秒） | 116・108・103・100・83 | 5 回 |
| Step 18 の2回目 | 通過（テスト 9.8 秒、全体 16.3 秒） | 90・104・105・101・101 | 5 回 |
| Step 22 の全件の中 | 通過（8.2 秒） | 97・96・94・96・105 | 5 回 |

- 3つの確かめ（アドレス欄に `#token=` が無い・CSP の違反 0 件・ブラウザの保存にトークンが無い）は、どの回の5回とも通った。2 秒を超えた回は無く、切り分けは不要だった。
- 本物の確かめの応答の項目の名前と型は見本と同じだった（`email: string`・`language: ja|en`。NFR 要件の承認の場の R-02 を閉じる）。
- Mailpit からの取り出し: 宛先が完全に一致するメールが1通、リンクは `baseURL` ＋ `/register#token=` で始まり、HTML の href・本文の文字・`Text` のリンクが同じだった。
- 完了の後: ログインの画面の案内とメールアドレスの持ち越し、URL にメールアドレスが無い、localStorage の表示の設定は `ja`・`system`・`md`、どの鍵にもトークン・パスワード・宛先が無い。新しい利用者でログインし、サイドバーに「管理」「利用者の招待」「DSL」のリンクが無く、`/admin/invitations`・`/admin` は見つからない表示、ユーザーメニュー（氏名）からログアウトできた。
- json の結果に `#token=`・`token=`・`e2e-invitee-`・`e2e-invitee-pw-`・`eyJ`・初期管理者のメールアドレスは 0 件。`test.step` の題（7 つ）は json の `steps` に入り、`page.goto` などの操作は入らなかった（U5 と同じ）。
- 090 は Mailpit の API を GET で読むだけで、メールを消していない。Mailpit は止めず消していない。

### 6.2 閲覧の履歴（A9、9節の決定 2）

- 手順: ホームの下の一時の場所（権限 700）に使い捨ての台本と Playwright の設定（リポジトリの `playwright.config.ts` を読み込み、テストの置き場と報告の出し先だけを一時の場所に替えたもの。WAR と一時の内部DB は同じ仕組みで起動）を置き、090 と同じ手順で招待を1件置いて Mailpit からリンクを取り出し（登録は完了しない）、`chromium.launchPersistentContext`（headless、使い捨てのプロファイル）でリンクを開いて閉じ、`node:sqlite` でプロファイルの `Default/History` を読み取り専用で開く形にした。値は出していない。終わった後に一時の場所ごと消した。
- 確かめは2回行った。1回目は計画の9節の決定 2 のとおり headless で流した。2回目は、その結果を受けた依頼者の決定で、画面を出す Chromium（`headless: false`）で同じ手順を1回だけ流した。どちらも一時の場所（ホームの下、権限 700）に使い捨ての台本とプロファイルを置き、終わった後に場所ごと消した。数えたのは件数だけで、値と URL そのものは表示していない。

| 回 | 起動の形 | 開いた後のアドレス欄の `#token=` | `History` のファイル | `urls` の行（全体） | 登録の画面（`/register`）の URL の行 | `#token=` を含む行 | `visits` の行 | 計画の区分 |
|---|---|---|---|---|---|---|---|---|
| 1 | headless | 無し | 作られない | — | — | — | — | そのブラウザ（起動の形）が履歴を持たない |
| 2 | 画面あり（`headless: false`） | 無し | 作られた | 2 | 2 | 1 | 2 | **残る** |

- 結論: 画面のある Chromium では、フラグメントを消す前のアドレス（`/register#token=…`）が閲覧の履歴に1行残る。フラグメントを消した後のアドレス（`/register`）も別の1行として残る。画面の側でアドレス欄から消しても（アドレス欄の確かめは通った）、ブラウザが開いた時点のアドレスを履歴に書くため、この画面の作り（`history.replaceState` と同じ置き換え）では防げない。機能設計 W2 の4（「フラグメントの無いアドレスだけが残る」）は、この起動の形の Chromium では成り立たなかった。機能設計は書き換えず、差として記録する（`project.md` の決まり）。
- 閲覧の履歴に残るトークンは、使い終えるか有効期限が過ぎると使えなくなる（使用済み・期限切れは 404。U3）。残るのは、同じ端末の閲覧の履歴を見られる人が、登録を終える前の使える間にリンクを開き直せる危険。
- 扱い（依頼者の決定）: **A**（残る危険として受け入れる。コードは変えない）を選んだ。承認の場で受け入れた危険として扱い、承認済みの NFR 設計の A9 の決定のまま記録する。README の「登録の完了の画面（U6）」の閲覧の履歴の文を、この結果と受け入れた危険に合わせて直した。
- 1回目の最初の試みは、一時の場所に `package.json`（`"type": "module"`）が無く、設定の読み込みで止まった（ブラウザを開く前）。足して流した1回を1回目の結果とした。2回目は初めから置いた。
- 2回の確かめで、E2E の一時の内部DB（実行ごとに捨てられる）に登録の終わっていない招待が1件ずつ、Mailpit に招待メールが1通ずつ増えた。Mailpit は止めず、消さず、API に書き込んでいない。配備したアプリと `.env` には触れていない。アプリのソースは変えていない。

### 6.3 E2E の全件（Step 22、`./gradlew e2eTest`）

- 結果: 69 件すべて通過（010〜040 の 6 件・050 の 21 件・060 の 21 件・070 の 20 件・090 の 1 件）、飛ばし 0・不安定 0。Gradle の全体 1分50秒、Playwright の全体 106.1 秒。
- 時間の内訳（テストの時間の合計）: 検査の部分（050 9.2 秒・060 50.8 秒・070 18.3 秒）78.3 秒、流れの部分（010 1.6 秒・020 2.8 秒・030 1.7 秒・040 6.1 秒・090 8.2 秒）20.4 秒。NFR 要件の承認の場の U6 R-01 の見込み（検査の部分で約 3〜8 分）より短かった。
- U5 の変更の後に E2E-1 と既存の E2E（010〜060）が通ることを確かめた（U5 の引き継ぎ、NFR9.11）。

## 7. 計画の9節の決定の反映

| 決定 | 反映 |
|---|---|
| 1（招待の後に管理者のコンテキストを閉じ、新しいコンテキストで開く） | 090 の最初の `test.step` の終わりで管理者のコンテキストを閉じ、測りと登録はそれぞれ新しいコンテキストで開く。ログインしたまま開いたときの案内（W3）は `RegistrationPage.test.tsx` で確かめた |
| 2（A9 は生成の中で1回だけ確かめる） | 6.2節のとおり。090 には常設していない |
| 3（Mailpit の API の場所の定数） | `frontend/e2e/support/mailpit.ts` の `MAILPIT_API_URL` と `build.gradle.kts` の `mailpitInfoUrl` を互いにコメントで参照（`build.gradle.kts` はコメント1行だけ） |
| 4（API で登録を完了する関数は U7 で足す） | U6 には置いていない |
| 5（止めて諮る場面） | (a)〜(f) のどれにも当たらなかった。時間も目標以内だった |
| 6（作業の場と統合の単位） | `feature/260925-user-management-b5-u6` を作った。コミットは依頼者の承認の後（9節） |

## 8. U7 への引き継ぎ

- `frontend/src/shared/validation/` の関数と誤りの種類（`index.ts` は置かない。各ファイルから名前付きで読む）:
  - `validateDisplayName(value): DisplayNameProblem | undefined`、`DisplayNameProblem = 'required' | 'tooLong' | 'invalidCharacter'`
  - `validateNewPassword(password): NewPasswordProblem | undefined`、`NewPasswordProblem = 'required' | 'tooShort' | 'tooLong'`
  - `validatePasswordConfirmation(password, confirmation): PasswordConfirmationProblem | undefined`、`PasswordConfirmationProblem = 'required' | 'mismatch'`
  - `countCodePoints`・`utf8ByteLength`・`trimDisplayName`・`isWhiteSpaceCodePoint`（`codePoints.ts`）
  - `DISPLAY_NAME_MAX_CODE_POINTS`（254）・`PASSWORD_MIN_CODE_POINTS`（12）・`PASSWORD_MAX_UTF8_BYTES`（72）（`limits.ts`）
  - U7 の `errorMessages.ts` の表との最終の突き合わせの記録は U7 の計画に残す（2.1 の U7 R-01）。
- 検査のファイルの番号は 080（U7）。070 と 090 の間で、090 は最後の番号のまま。
- 共用の手伝い: `support/mailpit.ts`（`findInvitationLink`）・`support/registrationFixtures.ts`（`VERIFY_SAMPLE`・`A11Y_SAMPLE_TOKEN`・`shapeOf`）と、U5 の `adminLogin.ts`・`invitationSeed.ts`・`invitationFixtures.ts`・`loginPreferences.ts`。ログインの後の組の当て方は U5 の `loginPreferences.ts`。
- API で登録を完了する関数（リンクからトークンを取り出して `POST /api/registration/complete` を呼ぶ）は U7 の計画で足す（決定 4）。
- Mailpit の片付けの書き方は README の「手元でメールを見る」の2行。

## 9. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」の表のとおり。この段の値は 4〜6節。

- カバレッジと verify の時間の実測のし直し（4.1節の値と比べる）。
- NFR6.1 の5回の値の写し（6.1節）。運用の中の判定は `Unverified`（observability-setup・feedback-optimization）。
- 070 の 20 組 × 2 状態の結果と既知の違反、090 の3つの確かめと形の照合と json の確かめ、e2eTest の時間の内訳（5・6節）。
- 登録の API の時間（同時 10 件で p95 1 秒、k6）は performance-validation、無ければ Build and Test（`project.md` の学び）。
- A9 は 6.2節の結果（headless は履歴を持たない。画面ありの Chromium では `#token=` を含む行が1行残る）と、依頼者の決定（A: 承認の場で受け入れた残る危険として扱う。コードは変えない）を、残る危険の記録に写す。

## 10. 依頼者に確かめたいこと

1. A9（閲覧の履歴）: 決定済み。画面ありの Chromium では、トークンを含むアドレスが閲覧の履歴に1行残った（6.2節、区分は「残る」）。依頼者は **A: 残る危険として受け入れる**（承認の場で受け入れた危険として扱う。コードは変えない）を選んだ。ほかの候補（B: 利用者への案内を足す、C: 設計を見直す）は選ばなかった。あわせて README の閲覧の履歴の文を、この結果と受け入れた危険に合わせて直した。
2. 初回の JavaScript が 119.2 KB から 121.9 KB に増え、Vite が共有の部分を `I18nProvider-*.js` に分けた（上限は置かない決まりで、500 KB の目安の警告は無い）。このままでよいか。
3. ログアウトの後に確かめへ移る仕組みを、設計の「副作用で `verifying` へ移す」から「描画の中で導く」に変えた（2節の #1・#2、リンタの決まりのため。動きは同じ）。
4. 090 のサイドバーの確かめを、計画の「DSL の管理」ではなく今の文言の「DSL」にした（2節の #8）。

## 11. コミットの区切りの案（依頼者の承認を得てから行う）

生成の担当はコミットしていない。計画の3節の C1〜C6 の区切りで、次の案を示す（`develop` への統合は squash）。

| 区切り | 中身 | メッセージの案 |
|---|---|---|
| C1 | `frontend/src/shared/validation/` の7ファイル | B5 U6 入力の確かめの関数（氏名・パスワード・確かめ、コードポイントと White_Space、U7 と共用） |
| C2 | `frontend/src/features/registration/` のテスト以外（`registration.ts`・`messages.ts`・`registrationApi.ts`・`registrationToken.ts`・`failureKind.ts`・`formProblems.ts`・`useRegistration.ts`・5つの部品の `.tsx`・2つの `.css`） | B5 U6 登録の完了の画面（リンクの確かめ・フラグメントの消去・ログイン中の案内・フォームと表示の設定・ログインの画面への受け渡し） |
| C3 | `frontend/src/features/registration/` の `*.test.ts(x)` と `testing/` | B5 U6 登録の完了の画面のテスト（トークン・失敗の振り分け・API・画面部品のアクセシビリティ・画面の流れと漏えい） |
| C4 | `frontend/e2e/070-registration-accessibility.e2e.ts`・`090-invitation-registration-flow.e2e.ts`・`support/mailpit.ts`・`support/registrationFixtures.ts`・`support/axe.ts`、`build.gradle.kts` のコメント1行 | B5 U6 実際のブラウザの検査（070）と代表の流れの E2E（090）、Mailpit からのリンクの取り出し |
| C5 | `README.md`・`frontend/src/features/README.md` | B5 U6 の文書（登録の完了の画面、070・090、既知の制約、入力の確かめの関数） |
| C6 | 記録の `construction/u6-registration-ui/code-generation/` の下（計画・質問・手順書・この要約・`source-manifest.json`・`traceability.json`） | U6 のコード生成の記録を追加（計画の実施・実測・070 と 090 の結果・A9 の確かめ） |

各コミットのメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。`develop` への squash の統合コミットの件名の案は「B5 U6 登録の完了の画面（リンクの確かめ・入力と表示の設定・ログインの画面への受け渡し、入力の確かめの共通化、実際のブラウザの検査と代表の流れの E2E）」。
