# Unit Test Instructions — U6 登録の完了の画面（u6-registration-ui）

U6 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の確かめ）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パス。U6 は画面の単位のため、バックエンドのテストを足さない。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | Vitest（既存）、jsdom | `frontend/vitest.config.ts`（`src/**/*.test.{ts,tsx}`、`e2e/` は対象外、`css: false`）。Node 24 の localStorage と重ならないよう `NODE_OPTIONS=--no-experimental-webstorage` を付ける（既存の `npm run test` と同じ） |
| 画面部品の描画と操作 | Testing Library（`@testing-library/react`）・user-event・jest-dom（既存） | `frontend/vitest.setup.ts`（jest-dom と vitest-axe の照合の登録、テストごとの片付け。変えない） |
| 描画の支え | 新しい `frontend/src/features/registration/testing/renderRegistration.tsx` | 既存の `renderWithProviders`（`frontend/src/app/testing/renderWithProviders.tsx`）に登録の完了の機能の登録と骨組みの文言を渡し、`route` の既定を `/register#token=<見本のトークン>` にして描く。U4 の口（表示の設定・受け渡し）とログイン状態は本物で動かす。画面の言語はブラウザの言語設定（`languages`）で ja・en を切り替える |
| 応答の見本 | 新しい `frontend/src/features/registration/testing/fixtures.ts` | 確かめの 200 の見本、失敗の応答の見本（`code` と `detail` の目印つき）、`fetch` の差し替えの組み立て |
| アクセシビリティ（構造） | vitest-axe（既存） | 画面部品ごとに1件（`RegistrationPage`・`RegistrationStatus`・`RegistrationUnavailable`・`RegistrationLoggedInNotice`・`RegistrationForm`）。`expect(await axe(container)).toHaveNoViolations()` |
| 性質ベースのテスト | fast-check（既存） | 全体の設定は足さない。失敗時は fast-check が `seed`・`path` を出力に示す（既存の `frontend/src/features/dsl/submitInput.test.ts` と同じ扱い） |
| 実際のブラウザの検査と E2E-1 | Playwright（既存）＋ axe-core（既存、B4 で devDependencies に明示） | `frontend/playwright.config.ts`（変えない。`workers: 1`・Chromium だけ・`locale: 'ja-JP'`・reporter の `json` と仮の資格情報の確かめ）、新しい `frontend/e2e/070-registration-accessibility.e2e.ts`・`frontend/e2e/090-invitation-registration-flow.e2e.ts`、`frontend/e2e/support/`（U4・U5 の手伝いと、U6 が足す `mailpit.ts`・`registrationFixtures.ts`、`axe.ts` の `REGISTRATION_KNOWN_VIOLATIONS`） |
| カバレッジ | `@vitest/coverage-v8`（既存） | `frontend/vitest.config.ts` の `thresholds`（行 80・分岐 70）。計測から外すのは `src/main.tsx`・型の宣言・テストのファイルだけ（変えない） |

新しい依存は足さない（NFR9.6）。Vitest・Playwright の設定のファイルは変えない。Mailpit の API は Node 24 の組み込みの `fetch` で呼ぶ。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U6 の置き場（`frontend/src/features/registration/`・`frontend/src/shared/validation/`）と U6 の E2E のファイルだけに絞る。

単体テスト（U6 の範囲。登録の完了の画面と共用の確かめの関数）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/registration src/shared/validation)
```

最初のテストより前の確かめ（Step 4）: U6 のテストはまだ無いため、上のコマンドに `--passWithNoTests` を付けて実行し、設定が読み込まれて終了の状態が 0 であることを確かめる。あわせて、環境が実際にテストを流せることを、U6 が使う既存の口のテストで確かめ、E2E の一覧（010〜060）が読めることを確かめる。

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/registration src/shared/validation --passWithNoTests)
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/login-handoff/loginHandoff.test.ts)
(cd frontend && npx playwright test --list)
```

層ごとの実行（Step 6・8・10・12・14。上の単体のコマンドの一部）:

| Step | 対象 | コマンド |
|---|---|---|
| Step 6 共用の確かめの関数 | `codePoints`・`validateDisplayName`・`validatePassword` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/validation)` |
| Step 8 画面の純粋な関数 | `registrationToken`・`failureKind`・`formProblems` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/registration/registrationToken.test.ts src/features/registration/failureKind.test.ts src/features/registration/formProblems.test.ts)` |
| Step 10 API | `registrationApi` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/registration/registrationApi.test.ts)` |
| Step 12 画面部品 | `RegistrationStatus`・`RegistrationUnavailable`・`RegistrationLoggedInNotice`・`RegistrationForm` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/registration/RegistrationStatus.test.tsx src/features/registration/RegistrationUnavailable.test.tsx src/features/registration/RegistrationLoggedInNotice.test.tsx src/features/registration/RegistrationForm.test.tsx)` |
| Step 14 画面・状態・登録 | `RegistrationPage`（`useRegistration` を通す）・`registration` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/registration/RegistrationPage.test.tsx src/features/registration/registration.test.tsx)` |

実際のブラウザの検査だけ（Step 16。`verify` と CI の外。WAR と Chromium と Mailpit が要る）:

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && caffeinate -i npx playwright test e2e/070-registration-accessibility.e2e.ts)
```

E2E-1 だけ（Step 18。同じく WAR と Chromium と Mailpit が要る）:

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && caffeinate -i npx playwright test e2e/090-invitation-registration-flow.e2e.ts)
```

- 事前に `(cd frontend && npx playwright install chromium)`。
- 070 だけのときも Mailpit を起動しておく（070 はメールを送らないが、E2E の WAR はメールを Mailpit へ送る設定で起動する。基盤の設計の N2）。`npx playwright test` で直接流すときは `./gradlew e2eTest` の Mailpit の前提の確かめが動かないため、起動を忘れないこと。
- 090 は招待を1件置き、Mailpit に1通届く。090 は Mailpit の API を読むだけで、書き込まず、メールを消さない。Mailpit は止めず消さない（README の「手元でメールを見る」の2行で開発者が片付ける）。
- 結果は `frontend/test-results/e2e-results.json`（json の報告）と `frontend/playwright-report/`（html の報告）。どちらも管理外で、コミット・共有しない。html の報告と失敗のときのトレースには、090 で開いた使い捨てのトークンを含むリンクが載る（基盤の設計の Q2 A で受け入れ済み）。json に仮の資格情報が入らないことは既存の報告の部品（`frontend/playwright-secret-check-reporter.ts`）が確かめる。トークン（`#token=`・`token=`）・宛先（`e2e-invitee-`）・パスワード（`e2e-invitee-pw-`）・アクセストークン（`eyJ`）が入らないことは Step 16・18 で文字列の検索で確かめる。

統合の前（Step 22・Step 23）は単位の範囲ではなく全体を流す（計画のとおり）:

```bash
docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- テストの件数を報告するときは、実測の数字だけを報告する。バックエンドのテストが UP-TO-DATE で飛ばされないよう、verify には `:backend:cleanTest :backend:cleanIntegrationTest` を付ける（`project.md` の Testing Posture）。
- colima の環境変数を渡さないと対象DB のテストが SKIPPED になる（`project.md` の Testing Posture）。U6 のテストはコンテナを使わない。

## 3. テストの一覧（Standard の量）

| 部品 | テスト（対象と同じ場所） | 件数の目安 | 主に確かめること |
|---|---|---|---|
| コードポイントと前後の除去 | `src/shared/validation/codePoints.test.ts` | 5 件＋fast-check 3 件 | 絵文字・結合文字・組になっていないサロゲートの数とバイト数、除く文字（U+3000・U+00A0・U+0085・U+2028 は除き、U+FEFF・U+200B は除かない）。性質: `Array.from` の長さ・`TextEncoder` の長さと同じ、2回かけても同じ・前後に White_Space が残らない・内側を変えない |
| 氏名の確かめ | `src/shared/validation/validateDisplayName.test.ts` | 6 件＋fast-check 1 件 | 空・空白だけ → `required`、253・254 → 誤りなし、255 → `tooLong`、内側の改行・タブ・U+200B・U+FEFF → `invalidCharacter`、除いた後の長さ、判定の順 |
| パスワードの確かめ | `src/shared/validation/validatePassword.test.ts` | 7 件＋fast-check 1 件 | 空・11・12 コードポイント、72・73 バイト（「あ」24 文字・＋「a」）、絵文字 11・12 文字（AC3.2.4）、確かめの空・1文字違い・前後の空白・正規化の違い・一致 |
| トークンの取り出し | `src/features/registration/registrationToken.test.ts` | 5 件＋fast-check 1 件 | `#token=abc`、`#` なし、ほかの鍵と並ぶ、符号化の戻し、無い・空・壊れた `%`。性質: `encodeURIComponent` して戻る |
| 失敗の振り分け | `src/features/registration/failureKind.test.ts` | 6 件 | 機能設計 5節の表の全行（確かめ・完了ごと、404 と 400 の `code` の違い・無い、401・403・429・503・500、通信の失敗）、`detail` に影響されない |
| フォームの誤り | `src/features/registration/formProblems.test.ts` | 5 件 | 誤りなし・1項目・複数の項目で最初の項目（氏名 → パスワード → 確かめ）、文言の鍵 |
| API | `src/features/registration/registrationApi.test.ts` | 7 件 | 2つの要求のメソッド・パス・本文、トークンが本文だけ、呼び出し側で見出しを付けない、公開のパスとしてトークンが付かない、確かめの成功の値と形の誤り、204 の空の本文、失敗の受け渡し |
| `RegistrationStatus` | `src/features/registration/RegistrationStatus.test.tsx` | 5 件 | 確かめ中の `role="status"`、読み込めないの `role="alert"`、「もう一度読み込む」、ja・en、vitest-axe |
| `RegistrationUnavailable` | `src/features/registration/RegistrationUnavailable.test.tsx` | 5 件 | 警告の `role="alert"` と文言、リンクの先 `/login`、トークン・メールアドレスを含まない、en、vitest-axe |
| `RegistrationLoggedInNotice` | `src/features/registration/RegistrationLoggedInNotice.test.tsx` | 5 件 | 情報の Alert、2つのボタン、ログアウト中の `aria-disabled`・`aria-busy`・押しても呼ばない・フォーカスが残る、vitest-axe |
| `RegistrationForm` | `src/features/registration/RegistrationForm.test.tsx` | 8 件 | メールアドレスの欄の属性（AC3.2.16）、パスワードの欄の `autocomplete="new-password"`、「12 文字以上」の案内、誤りの結び付け、3つの `fieldset` と `legend`、言語の選択肢の `lang`、矢印キー、送信中の表示と `readOnly`、失敗の知らせ、フォーカスの移り、vitest-axe |
| `RegistrationPage`（`useRegistration` を通す） | `src/features/registration/RegistrationPage.test.tsx` | 20〜26 件 | 開く（AC3.2.1）・トークン（フラグメントの消去・本文だけ・無い/空・読み込み直し）・確かめの 404 と 500 と通信の失敗・ログイン中の案内とログアウト・選んだ時点の反映と言語の切り替え・画面の確かめの境界・送信中の二重の送信の防止・完了の 204（保存・受け渡し・ログインの画面・URL・自動でログインしない・Toast なし）・400・404・500・通信の失敗・離れるときの `clearPreview`・保存と `console` と画面の文字への漏えい・vitest-axe |
| 機能の登録と文言 | `src/features/registration/registration.test.tsx` | 5 件 | 画面の値（`/register`・`STANDALONE`・`PUBLIC`）、サイドバー・ユーザーメニューの項目が無い、既存の登録と合わせた `validateRegistrations`、ja・en の鍵のそろい、en でも「日本語」 |
| 実際のブラウザの検査 | `frontend/e2e/070-registration-accessibility.e2e.ts` | 20 件（20 組×2 状態） | 組ごとに `ready`（確かめの API の差し替え）と `unavailable`（フラグメントの無い `/register`）の axe の違反 0 件（既知の違反は `REGISTRATION_KNOWN_VIOLATIONS` のとおり）と横のはみ出し無し、`color-contrast`・`scrollable-region-focusable` が流れたこと、CSP の違反 0 件、完了の要求が送られないこと |
| E2E-1 | `frontend/e2e/090-invitation-registration-flow.e2e.ts` | 1 件 | 管理者でログイン → U5 の画面で招待 → Mailpit からリンク → 5回の測り（アドレス欄・CSP・保存の確かめは失敗の条件、時間は記録だけ）→ 新しいコンテキストで開き応答の形の照合 → 登録の完了 → ログインの画面の案内とメールアドレスの持ち越し → 新しい利用者でログイン → 管理のリンクが無く `/admin/invitations`・`/admin` が見つからない → ログアウト |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`describe`・`it`・`test`・`test.step`）は英語で書く。テストのデータは日本語でよい。新しいファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。

`team.md` の必須のテストと部品 8節のうち U6 が受け持つもの:

| 必須のテスト・確かめ | 確かめるテスト |
|---|---|
| 招待と登録の完了: 使えないリンク（期限切れ・使用済み・取り消し・改ざん・存在しない）の拒否で、応答から利用者の存在を推測できない（画面の側: 理由によらず同じ表示で `detail` を出さない。サーバー側は U3） | `failureKind.test.ts`・`RegistrationPage.test.tsx`（`detail` だけを変えた 404）、`RegistrationUnavailable.test.tsx` |
| パスワードの規則の境界（画面の側の確かめ。判定はサーバーが正で U3） | `validatePassword.test.ts`・`RegistrationPage.test.tsx`（AC3.2.4 の値） |
| 秘密情報の漏えい: 招待のトークン・パスワード・メールアドレスをブラウザの保存・URL・`console`・画面の文字に出さない | `RegistrationPage.test.tsx`（NFR1.2・NFR2.1・NFR9.3）、090（実際のブラウザのアドレス欄と保存） |
| 認可: 画面で管理メニューを隠すことをサーバー側の判定の代わりにしない（サーバー側の 403 は U3・既存の結合テスト） | 090（新しい利用者に管理のリンクが無く、管理者だけの画面の URL が見つからない表示になること） |
| 性質ベースのテスト（fast-check）を純粋な関数に | `codePoints.test.ts`・`validateDisplayName.test.ts`・`validatePassword.test.ts`・`registrationToken.test.ts`（NFR9.9） |
| 画面部品ごとのアクセシビリティ検査 | 5つの画面部品の vitest-axe（NFR7.2）、070（NFR7.3・NFR7.5） |
| E2E は代表の流れを Intent ごとに1本、前のテストの状態に頼らない | 090（管理者のログインと招待を自分で行い、実行ごとに違う宛先。NFR9.10） |
| 既存の画面のテストと E2E が通り続ける | `./gradlew verify`（Step 23）、`./gradlew e2eTest`（Step 22） |

## 4. カバレッジの目標

- フロントエンドの全体: 行 80% 以上・分岐 70% 以上（既存の `thresholds`。`./gradlew verify` の `frontendCoverage` で判定する）。フロントエンドの下限は全体の合計だけで、ディレクトリごとの下限は無いが、U6 の新しいファイル（`shared/validation/` を含む）もそれぞれ行 80%・分岐 70% を目安にし、下回るファイルがあればテストを足す（Step 14・20）。
- 計測の除外を増やさない。`frontend/e2e/` の検査と手伝いは既存どおり Vitest の計測の対象外（`vitest.config.ts` の `include` が `src/**`）。`src/features/registration/testing/` のテストの支えは今までどおり計測に入る。
- U5 の後の実測（68 ファイル 509 件、行 97.6%・分岐 93.73%）を基準に、Step 1・Step 23 で実測して比べる。
- U6 の範囲のカバレッジを見るとき（目安。下限の判定は verify で行う）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/registration src/shared/validation --coverage --coverage.include='src/features/registration/**' --coverage.include='src/shared/validation/**')
```

  設定のファイルの下限（行 80・分岐 70）はこの範囲の値にもそのまま当たる（下限を外す指定はしない）。範囲の外のテストが通る分を含まないため値は目安で、全体の下限の判定は `./gradlew verify` の `frontendCoverage` で行う。報告は `frontend/coverage/index.html`。

## 5. 差し替え（モック・スタブ）の方針

- **API（画面部品と画面のテスト）**: `vi.stubGlobal('fetch', ...)` で確かめ・完了の要求を受け、要求（メソッド・パス・見出し・本文）を記録して見本の応答を返す。ApiClient・U4 の口・ログイン状態は差し替えず本物で動かす（部品 8節）。`resetApiClient` をテストごとに呼ぶ。答えを返さない約束で止め、確かめ中・送信中の表示・`aria-busy`・フォーカス・二重の送信の防止を見てから答える。重なった確かめは、2つの約束を作って先の答えを後に返す。
- **ログイン中の流れ**: `renderWithProviders` の `provider`（`fakeProvider` など）でログイン中の状態にし、ログアウトの要求（`POST /api/auth/session/logout`）を `fetch` の差し替えで受ける。ログアウトの後に未ログインを知らせ、確かめの要求が送られることを見る。
- **make-you-chic-ui**: 差し替えない。本物の Card・FormField・TextInput・Button・Alert・RadioGroup の中で描き、役割・名前・属性（`aria-disabled`・`aria-busy`・`aria-invalid`・`aria-describedby`・`lang`・`role="group"`）で確かめる。部品が中に持つ `data-testid` は探さない。
- **表示の設定**: `renderWithProviders` の既定（見た目の設定は答え済み）のまま使う。ブラウザの保存の値は jsdom の localStorage に U4 の鍵（`mastersmith.display-settings`）で置き、テストの前後で `localStorage.clear()`・`sessionStorage.clear()` と `resetDisplayTestState()` で戻す。OS の配色は `matchMedia` の差し替え（`frontend/src/app/display-settings/testing/fakeColorScheme.ts`）。
- **受け渡し**: `frontend/src/app/login-handoff/loginHandoff.ts` の `takeLoginHandoff` で受け渡された値を読み、テストの後に `resetLoginHandoff()` で戻す。ログインの画面への移り先は、同じ `renderWithProviders` の中で auth の機能の登録も渡して、`login-form-registered-alert` とメールアドレスの欄の値で確かめる。
- **時刻**: U6 は時刻に頼る処理を持たない。`sleep` と実時刻に頼らず、`findBy*`・`waitFor` と約束の解決で待つ。
- **`console` の見張り**: `vi.spyOn(console, 'log' | 'info' | 'warn' | 'error' | 'debug')` で5つを見張り、どの流れでも呼ばれないことを見る（`vitest.setup.ts` の `vi.restoreAllMocks()` で戻る）。呼ばれたときは引数にトークン・パスワード・メールアドレスが無いことも見て、失敗の説明に出す。
- **ブラウザの保存と URL**: 流れの後に localStorage・sessionStorage のすべての鍵の値を読んで、トークン・パスワード・メールアドレスが無いことを見る。URL は React Router の場所（テストの補助が画面に出す場所の値）と `window.location` のパス・問い合わせ・`#` の後を比べる。
- **実際のブラウザの検査（070）**: サーバーは本物の WAR。組ごとの新しいコンテキストで、`POST /api/registration/verify` だけを `page.route` で見本（`support/registrationFixtures.ts` の `VERIFY_SAMPLE`）の 200 に差し替える（そのページの中だけで効く）。ブランドカラーの組は U4 の手伝い（`prepareCombo`）で `/api/appearance` を差し替える。ログインしない。`unavailable` は差し替えずにフラグメントの無い `/register` を開く。
- **E2E-1（090）**: 何も差し替えない（本物の WAR・Mailpit・一時の内部DB）。Mailpit の API は Node の `fetch` で `GET` だけを呼ぶ（`support/mailpit.ts`）。

## 6. テストのデータ

- **メールアドレス**: `example.com`・`example.test` などの予約されたドメインだけを使う（例 `hanako@example.test`）。実在の宛先を書かない。漏えいの確かめでは見分けやすい値（例 `registration-leak-check@example.test`）を使う。
- **トークン**: 画面部品のテストは見分けやすい低いエントロピーの値（例 `registration-test-token-value`）、070 はどの招待にも当たらない固定の見本の値 `a11y-sample-token`（NFR1.5）。どちらも Gitleaks に当たらない値にする。090 のトークンは実行ごとの一時の内部DB の中の使い捨ての値で、テストの変数だけに持つ。
- **パスワード**: 画面部品のテストは境界の値（11・12 コードポイント、「あ」24 文字（72 バイト）と「あ」24 文字＋「a」（73 バイト）、絵文字 11・12 文字）と固定のテストの値。090 は実行ごとに作る `e2e-invitee-pw-<乱数の16進>`（リポジトリに置かない）。
- **氏名**: 日本語の架空の氏名（例「山田 花子」「招待 花子」）、境界の値（253・254・255 コードポイント）、空白だけ（半角・全角・タブ）、内側の改行・タブ・ゼロ幅の空白。
- **`detail` の目印**: 応答の `detail` に見分けやすい文字（例 `server-detail-marker`）を入れ、画面に出ないことを見る。
- **090 の宛先**: 実行ごとに重ならない `e2e-invitee-<実行時刻>-<乱数の16進>@example.com`。E2E の初期管理者（`e2e-admin@example.com`、パスワードは実行ごとに作る）は読むだけで、テストの題・`test.step` の題・注記・添付に入れない。

## 7. 性質ベースのテストの種

- fast-check の失敗時の乱数の種（`seed`）と道（`path`）は、テストの出力（失敗の詳細）に出る。再現するときは、その値を `fc.assert(property, { seed, path })` に一時的に書いて Step 6・8 のコマンドで実行し、直した後に外す（既存の `frontend/src/features/dsl/submitInput.test.ts` と同じ扱い。全体の設定は足さない）。
- 対象は純粋な関数だけ: `countCodePoints`（`Array.from` の長さと同じ）、`utf8ByteLength`（`TextEncoder` の長さと同じ）、`trimDisplayName`（2回かけても同じ、前後に White_Space が残らない、内側を変えない）、`validateDisplayName`（1〜254 コードポイントの使える文字だけの文字列は誤りなし）、`validateNewPassword`（境界の前後で結果が変わる）、`registrationToken`（`encodeURIComponent` して戻る）（`team.md` の Testing Posture、NFR9.9）。

## 8. 実際のブラウザの検査（070）と E2E-1（090）の読み方

- 070 の組は U4 の手伝い（`frontend/e2e/support/displayCombos.ts`）の 20 組と同じ: (a) テーマ2×文字の大きさ3（`blue`、既定の幅）の6組、(b) ブランドカラー4×テーマ2（`md`、既定の幅）の8組、(c) テーマ2×文字の大きさ3（`blue`、375px × 812px）の6組。組ごとに1つのテストで、ログインしない。テーマと文字の大きさは U4 の鍵を初めのスクリプトで置く（ログインの前の画面のため、U5 の `loginPreferences.ts` は使わない）。
- 070 の状態は、`ready`（確かめの API の差し替えで出すフォーム）と `unavailable`（フラグメントの無い `/register`）。完了の要求は送らない。
- axe の合否は想定外の違反（`violations` のうち既知の違反の一覧の外）0 件。既知の違反は、green・orange の組の `color-contrast` で、primary の Button で、状態ごとの一覧（`REGISTRATION_KNOWN_VIOLATIONS`）に名前（`data-testid`）が書かれたものだけ。既知の違反が一覧と一致しない（消えた・増えた）ときも失敗にする。判定できなかった要素（`incomplete`）は失敗にせず、規則の名前と件数を注記に残す。
- 090 の時間（リンクを開いてからメールアドレスの欄が見えるまで）は、5回の値のミリ秒と目標（2,000）以内の回数を注記と添付に残す。時間では失敗しない。値は Build and Test が json の報告から写す。同じ5回のアドレス欄の `#token=`・CSP の違反・ブラウザの保存のトークンの確かめは失敗の条件。
- 090 は1回の実行で招待を1件置き、Mailpit に1通届き、新しい利用者と監査の記録を一時の内部DB に残す。後のファイルは、090 の利用者に頼らない（090 は最後の番号）。
- 失敗したときのトレース（`trace: 'retain-on-failure'`）と html の報告には、090 のリンク（使い捨てのトークン）・宛先・実行ごとの仮の資格情報が含まれうるため、コミット・共有しない（`cicd-pipeline.md` 4.5）。
