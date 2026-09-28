# Unit Test Instructions — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

U7 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の確かめ）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パス。U7 は画面の単位のため、バックエンドのテストを足さない。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | Vitest（既存）、jsdom | `frontend/vitest.config.ts`（`src/**/*.test.{ts,tsx}`、`e2e/` は対象外、`css: false`）。Node 24 の localStorage と重ならないよう `NODE_OPTIONS=--no-experimental-webstorage` を付ける（既存の `npm run test` と同じ） |
| 画面部品の描画と操作 | Testing Library（`@testing-library/react`）・user-event・jest-dom（既存） | `frontend/vitest.setup.ts`（jest-dom と vitest-axe の照合の登録、テストごとの片付け。変えない） |
| 描画の支え | 新しい `frontend/src/features/preferences/testing/renderPreferences.tsx` | 既存の `renderWithProviders`（`frontend/src/app/testing/renderWithProviders.tsx`）に preferences と auth の機能の登録を渡し、make-you-chic-ui の `ToastProvider` で包み、`fakeProvider` のログイン中の状態で `route` の既定を `/me/preferences` にして描く（`/me/password` も渡せる）。U4 の口・ログイン状態・ApiClient は本物で動かす。画面の言語はブラウザの言語設定（`languages`）で ja・en を切り替える |
| 応答の見本と偽のサーバー | 新しい `frontend/src/features/preferences/testing/fixtures.ts` | `Preferences` の見本、`fieldErrors` と `detail` の目印つきの失敗の応答の見本、`fetch` の差し替えの偽のサーバー（要求のメソッド・パス・見出し・本文の記録、答えを後で決める約束） |
| アクセシビリティ（構造） | vitest-axe（既存） | 画面部品ごとに1件（`PreferencesPage`・`PreferencesLoadFailure`・`PreferencesForm`・`PasswordChangePage`・`PasswordChangeForm`、`ShellLayout` の `path` の項目を含むメニュー）。`expect(await axe(container)).toHaveNoViolations()` |
| 性質ベースのテスト | fast-check（既存） | 全体の設定は足さない。失敗時は fast-check が `seed`・`path` を出力に示す（既存の `frontend/src/features/dsl/submitInput.test.ts` と同じ扱い） |
| 実際のブラウザの検査と測り | Playwright（既存）＋ axe-core（既存、B4 で devDependencies に明示） | `frontend/playwright.config.ts`（変えない。`workers: 1`・Chromium だけ・`locale: 'ja-JP'`・reporter の `json` と仮の資格情報の確かめ）、新しい `frontend/e2e/080-preferences-accessibility.e2e.ts`、`frontend/e2e/support/`（U4〜U6 の手伝いと、U7 が足す `preferencesFixtures.ts`・`registeredUser.ts`、`axe.ts` の `PREFERENCES_KNOWN_VIOLATIONS`） |
| カバレッジ | `@vitest/coverage-v8`（既存） | `frontend/vitest.config.ts` の `thresholds`（行 80・分岐 70）。計測から外すのは `src/main.tsx`・型の宣言・テストのファイルだけ（変えない） |

新しい依存は足さない（NFR9.10）。Vitest・Playwright の設定のファイルは変えない。Mailpit の API は Node 24 の組み込みの `fetch` で呼ぶ（U6 の `support/mailpit.ts`）。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U7 の置き場（`frontend/src/features/preferences/`）と、U7 が手を入れる骨組みの3つのテストのファイルと、U7 の E2E のファイルだけに絞る。

単体テスト（U7 の範囲。プリファレンスとパスワードの変更の画面と、骨組みの変更）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/preferences src/app/registry/validateRegistrations.test.ts src/app/layout/ShellLayout.test.tsx src/app/navigation/navigationItems.test.ts)
```

最初のテストより前の確かめ（Step 4）: 上のコマンドで設定が読み込まれ、既存の骨組みの3つのテストが通って終了の状態が 0 であることを確かめる。U7 の機能のテストはまだ無いため、機能の置き場だけの実行は `--passWithNoTests` を付けて終了の状態 0 であることを確かめる。E2E の一覧（010〜070・090）が読めることも確かめる。

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/validateRegistrations.test.ts src/app/layout/ShellLayout.test.tsx src/app/navigation/navigationItems.test.ts)
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/preferences --passWithNoTests)
(cd frontend && npx playwright test --list)
```

層ごとの実行（Step 6・8・10・12・14。上の単体のコマンドの一部）:

| Step | 対象 | コマンド |
|---|---|---|
| Step 6 画面の純粋な関数 | `fieldErrors`・`errorMessages`・`formChecks` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/preferences/fieldErrors.test.ts src/features/preferences/errorMessages.test.ts src/features/preferences/formChecks.test.ts)` |
| Step 8 API | `preferencesApi` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/preferences/preferencesApi.test.ts)` |
| Step 10 骨組みの変更 | `validateRegistrations`・`ShellLayout`・`navigationItems` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/validateRegistrations.test.ts src/app/layout/ShellLayout.test.tsx src/app/navigation/navigationItems.test.ts)` |
| Step 12 画面部品 | `PreferencesLoadFailure`・`PreferencesForm`・`PasswordChangeForm` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/preferences/PreferencesLoadFailure.test.tsx src/features/preferences/PreferencesForm.test.tsx src/features/preferences/PasswordChangeForm.test.tsx)` |
| Step 14 画面・状態・登録 | `PreferencesPage`（`usePreferencesForm` を通す）・`PasswordChangePage`（`usePasswordChangeForm` を通す）・`registration` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/preferences/PreferencesPage.test.tsx src/features/preferences/PasswordChangePage.test.tsx src/features/preferences/registration.test.tsx)` |

実際のブラウザの検査と測りだけ（Step 16。`verify` と CI の外。WAR と Chromium と Mailpit が要る）:

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && caffeinate -i npx playwright test e2e/080-preferences-accessibility.e2e.ts)
```

- 事前に `(cd frontend && npx playwright install chromium)`。
- 080 だけのときも Mailpit を起動しておく（測りが招待のメールからリンクを取り出す。基盤の設計の N2）。`npx playwright test` で直接流すときは `./gradlew e2eTest` の Mailpit の前提の確かめが動かないため、起動を忘れないこと。起動していないと、測りは理由を注記に残して飛ばされる（念のための道。通常の `./gradlew e2eTest` では通らない）。
- 080 の測りは招待を1件置き、Mailpit に1通届き、新しい利用者と、招待・パスワードの変更の監査を一時の内部DB に残す。080 は Mailpit の API を読むだけで、書き込まず、メールを消さない。Mailpit は止めず消さない（README の「手元でメールを見る」の2行で開発者が片付ける）。
- 080 の検査は `PUT`・`POST /api/me/…` を送らない（送られていれば失敗）。
- 結果は `frontend/test-results/e2e-results.json`（json の報告）と `frontend/playwright-report/`（html の報告）。どちらも管理外で、コミット・共有しない。失敗のときのトレースには、初期管理者と作った利用者のパスワード・アクセストークン・招待のトークンを含む要求が載りうるため、共有しない。json に仮の資格情報が入らないことは既存の報告の部品（`frontend/playwright-secret-check-reporter.ts`）が確かめる。作った宛先（`u7-perf-`）・作ったパスワード（`e2e-u7-pw-`）・トークン（`#token=`・`token=`）・アクセストークン（`eyJ`）・初期管理者のメールアドレスが入らないことは Step 16 で文字列の検索で確かめる。

統合の前（Step 20・Step 21）は単位の範囲ではなく全体を流す（計画のとおり）:

```bash
docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- テストの件数を報告するときは、実測の数字だけを報告する。バックエンドのテストが UP-TO-DATE で飛ばされないよう、verify には `:backend:cleanTest :backend:cleanIntegrationTest` を付ける（`project.md` の Testing Posture）。
- colima の環境変数を渡さないと対象DB のテストが SKIPPED になる（`project.md` の Testing Posture）。U7 のテストはコンテナを使わない。

## 3. テストの一覧（Standard の量）

| 部品 | テスト（対象と同じ場所） | 件数の目安 | 主に確かめること |
|---|---|---|---|
| 項目ごとの誤りの読み取り | `src/features/preferences/fieldErrors.test.ts` | 6 件＋fast-check 2 件 | 知っている項目だけ、壊れた要素と知らない項目と2つ目を捨てる、`problem`・`fieldErrors` が無い・形が違うと空、`detail` を写さない。性質: どんな JSON の値でも例外を出さない、返す項目は `fields` の中だけで重ならない |
| 文言の鍵の選び方 | `src/features/preferences/errorMessages.test.ts` | 5 件 | 6つのサーバーの `reason` と知らない値の寄せ方、表のすべての組の鍵、表に無い組と `unknown` の一般の文言、すべての鍵が ja・en の両方にあって空でない |
| 画面の確かめ | `src/features/preferences/formChecks.test.ts` | 7 件 | 氏名の空白だけ・255・254 ちょうど・内側の改行と U+200B、今のパスワードの空と規則を当てないこと、新しいパスワードの 11・12 コードポイントと 72・73 バイトと絵文字 11・12 文字、確かめの空と不一致、最初の誤りの項目の順 |
| API | `src/features/preferences/preferencesApi.test.ts` | 8 件 | 3つの要求のメソッド・パス・本文、呼び出し側で見出しを付けない、成功の値と形の誤り、204 の空の本文、失敗の受け渡し（`problem` を含む）、401 の更新と送り直しが1回、400 `PASSWORD_CURRENT_MISMATCH` で更新の要求が送られない |
| 登録の検査（足す分） | `src/app/registry/validateRegistrations.test.ts` | ＋6 件 | `path` の項目・ホーム・既存の `action` の項目が通る、どちらも無い・両方ある・登録されていない `path`・絶対 URL・`//` の URL が問題になる、ほかの問題と一緒に集まる |
| `ShellLayout`（足す分） | `src/app/layout/ShellLayout.test.tsx` | ＋4 件 | `path` の項目が `<a href role="menuitem">`、マウスとキーボードで読み込み直しなしで移る、ログアウトはボタンのまま呼ばれる、vitest-axe |
| ユーザーメニューの並び（足す分） | `src/app/navigation/navigationItems.test.ts` | ＋1 件 | `path` の項目と `action` の項目が order の順 |
| `PreferencesLoadFailure` | `src/features/preferences/PreferencesLoadFailure.test.tsx` | 4 件 | `role="alert"` と文言、「もう一度読み込む」、en、vitest-axe |
| `PreferencesForm` | `src/features/preferences/PreferencesForm.test.tsx` | 8 件 | 3つの `fieldset` と `legend`（言語は案内を含む）、言語の選択肢の `lang`、テーマの並び、矢印キー、氏名の誤りの結び付け、選択の誤りが `legend` の中、「元に戻す」の押せる・押せない、送信中の表示と `readOnly`、画面の知らせと閉じるボタンの文言、vitest-axe |
| `PasswordChangeForm` | `src/features/preferences/PasswordChangeForm.test.tsx` | 6 件 | `type="password"` と `autocomplete`（CR6.9）、「12 文字以上」の案内、誤りの結び付け、送信中の表示と `readOnly`、`method`・`action` が無い、vitest-axe |
| `PreferencesPage`（`usePreferencesForm` を通す） | `src/features/preferences/PreferencesPage.test.tsx` | 22〜28 件 | 読み込み（AC4.1.1・AC4.1.10、GET は1回）、そろえ（D2）、見せ方（AC4.1.11）、保存の成功（AC4.1.4・AC4.1.8・AC4.1.12）、画面の確かめ（AC4.1.7）、サーバーの誤り（`fieldErrors`・`MALFORMED_REQUEST`・500・通信の失敗・形の誤り、`detail` が出ない）、送信中の二重の送信の防止（NFR6.5）、離れた後の答え、見せ方の最中のログイン状態の更新（機能設計 10節の (f)。確かめて記録するだけで、食い違いでも失敗にせず U4 は変えない。計画の9節の決定 4）、保存と `console` と URL への漏えい（NFR2.1）、vitest-axe |
| `PasswordChangePage`（`usePasswordChangeForm` を通す） | `src/features/preferences/PasswordChangePage.test.tsx` | 12〜16 件 | 開いても API を呼ばない、画面の確かめの境界（AC5.1.3）、成功（AC5.1.8）、今のパスワードの誤り（AC5.1.6・AC5.1.7、更新の要求が送られない）、`VALIDATION_FAILED`・500・通信の失敗と送信中、保存と URL と `console` への漏えい（NFR9.1）、送信中に外すと答えを捨てる、vitest-axe |
| 機能の登録と文言 | `src/features/preferences/registration.test.tsx` | 6 件 | 2つの画面の値（`SHELL`・`LOGGED_IN`・遅延読み込み）、ユーザーメニューの2つがログアウトより前で `path` を持つ、サイドバーの項目が無い、既存の登録と合わせた `validateRegistrations`、ja・en の鍵のそろい、en でも「日本語」、未ログインでログインの画面へ、ユーザーメニューから2つの画面へ移る（AC5.1.9） |
| 実際のブラウザの検査と測り | `frontend/e2e/080-preferences-accessibility.e2e.ts` | 21 件（20 組＋測り 1） | 組ごとにプリファレンス（最初・画面の確かめの誤り）とパスワードの変更（最初・画面の確かめの誤り）の axe の違反 0 件（既知の違反は `PREFERENCES_KNOWN_VIOLATIONS` のとおり）と横のはみ出し無し、`color-contrast`・`scrollable-region-focusable` が流れたこと、組が当たったこと、CSP の違反 0 件、PUT・POST が送られないこと。測りは開く（2画面×5回）・保存（5回）・パスワードの変更（5回）の時間と、本物の `GET /api/me/preferences` の応答と見本の形の照合 |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`describe`・`it`・`test`・`test.step`）は英語で書く。テストのデータは日本語でよい。新しいファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。

`team.md` の必須のテストと部品 7節のうち U7 が受け持つもの:

| 必須のテスト・確かめ | 確かめるテスト |
|---|---|
| パスワードの変更: 今のパスワードの確かめ（画面の側: 400 `PASSWORD_CURRENT_MISMATCH` を今のパスワードの項目に結び付け、ログインしたままでトークンの更新に乗らない。サーバーの照合は U2） | `PasswordChangePage.test.tsx`・`preferencesApi.test.ts` |
| パスワードの変更: パスワードの規則の境界（画面の側の確かめ。判定はサーバーが正で U2、共用の関数の境界の性質ベースは U6） | `formChecks.test.ts`・`PasswordChangePage.test.tsx`（AC5.1.3 の値） |
| パスワードの変更: 変更の後のリフレッシュトークンの扱い（決めた動作は「無効にしない」。画面の側: 成功の後もログインしたままで、ログインの画面へ移らず、トークンの更新の要求を送らない。サーバーの判定は U2） | `PasswordChangePage.test.tsx`（AC5.1.8） |
| 認証・認可に関わる変更の失敗の場合（`project.md` の Mandated） | `PasswordChangePage.test.tsx`（今のパスワードの誤り・入力の誤り・500・通信の失敗）、`validateRegistrations.test.ts`（外の URL の拒否）、`registration.test.tsx`（未ログインでログインの画面へ） |
| 秘密情報の漏えい: パスワード・トークン・メールアドレス（氏名の初期値）をブラウザの保存・URL・`console` に出さない | `PasswordChangePage.test.tsx`（NFR9.1）、`PreferencesPage.test.tsx`（NFR2.1）、080 の json の文字列の検索（Step 16） |
| 認可: 画面で隠すことをサーバー側の判定の代わりにしない（サーバー側の 401・200 は U2 の結合テスト） | `registration.test.tsx`（`LOGGED_IN` の画面の振り分け） |
| 性質ベースのテスト（fast-check）を純粋な関数に | `fieldErrors.test.ts`（NFR9.6） |
| 画面部品ごとのアクセシビリティ検査 | 5つの画面部品と `ShellLayout` の vitest-axe（NFR7.2）、080（NFR7.3・NFR7.4） |
| E2E は代表の流れを Intent ごとに1本 | U7 は流れの E2E を足さない（090 が1本）。080 は流れではない（NFR9.9） |
| 既存の画面のテストと E2E が通り続ける | `./gradlew verify`（Step 21）、`./gradlew e2eTest`（Step 20。ユーザーメニューの「ログアウト」を使う 020・030・040・090 を含む） |

## 4. カバレッジの目標

- フロントエンドの全体: 行 80% 以上・分岐 70% 以上（既存の `thresholds`。`./gradlew verify` の `frontendCoverage` で判定する）。フロントエンドの下限は全体の合計だけで、ディレクトリごとの下限は無いが、U7 の新しいファイルと骨組みの変えたファイルもそれぞれ行 80%・分岐 70% を目安にし、下回るファイルがあればテストを足す（Step 14・18）。
- 計測の除外を増やさない。`frontend/e2e/` の検査と手伝いは既存どおり Vitest の計測の対象外（`vitest.config.ts` の `include` が `src/**`）。`src/features/preferences/testing/` のテストの支えは今までどおり計測に入る。
- U6 の後の実測（81 ファイル 625 件、行 97.54%・分岐 93.06%）を基準に、Step 1・Step 21 で実測して比べる。
- U7 の範囲のカバレッジを見るとき（目安。下限の判定は verify で行う）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/preferences src/app/registry/validateRegistrations.test.ts src/app/layout/ShellLayout.test.tsx src/app/navigation/navigationItems.test.ts --coverage --coverage.include='src/features/preferences/**' --coverage.include='src/app/registry/**' --coverage.include='src/app/layout/ShellLayout.tsx')
```

  設定のファイルの下限（行 80・分岐 70）はこの範囲の値にもそのまま当たる（下限を外す指定はしない）。範囲の外のテストが通る分を含まないため値は目安で、全体の下限の判定は `./gradlew verify` の `frontendCoverage` で行う。報告は `frontend/coverage/index.html`。

## 5. 差し替え（モック・スタブ）の方針

- **API（画面とフックのテスト）**: `vi.stubGlobal('fetch', ...)` の偽のサーバー（`testing/fixtures.ts`）で `GET`・`PUT /api/me/preferences` と `POST /api/me/password` を受け、要求（メソッド・パス・見出し・本文）を記録して見本の応答を返す。`preferencesApi.ts`・ApiClient・U4 の口・ログイン状態は差し替えず本物で動かす（計画の9節の決定 2）。`resetApiClient` をテストごとに呼ぶ。答えを返さない約束で止め、読み込み中・送信中の表示・`aria-busy`・フォーカス・二重の送信の防止を見てから答える。重なった読み込みは、2つの約束を作って先の答えを後に返す。
- **口の呼ばれ方だけを見るテスト**: D2 のそろえで `applyUserPreferences` が呼ばれない場合などは、`useDisplaySettings` の口を差し替えて呼ばれ方を見る（機能設計 8節のとおり）。
- **ログイン状態**: `renderWithProviders` の `provider` に `fakeProvider`（ログイン中、氏名つき）を渡す。401 の更新の流れは、偽のサーバーで `POST /api/auth/session/refresh` を受けて記録し、今のパスワードの誤りでは送られないことを見る。ログインの画面へ移らないことは、auth の機能の登録も渡して `login-layout` が出ないことで見る。
- **make-you-chic-ui**: 差し替えない。本物の Button・Alert・FormField・TextInput・RadioGroup・Toast・Dropdown（AppShell の中）で描き、役割・名前・属性（`aria-disabled`・`aria-busy`・`aria-invalid`・`aria-describedby`・`lang`・`role="group"`・`role="menuitem"`）で確かめる。部品が中に持つ `data-testid` は探さない。
- **表示の設定**: `renderWithProviders` の既定のまま使う。ブラウザの保存の値は jsdom の localStorage に U4 の鍵（`mastersmith.display-settings`）で置き、テストの前後で `localStorage.clear()`・`sessionStorage.clear()` と `resetDisplayTestState()` で戻す。OS の配色は `matchMedia` の差し替え（`frontend/src/app/display-settings/testing/fakeColorScheme.ts`）。画面の値は `<html>` の `data-theme`・`data-font-size`・`lang` とユーザーメニューの名前で見る。
- **Toast**: `renderPreferences.tsx` が `ToastProvider` で包む。Toast の文言は `aria-live` の領域の中の文字で見る。
- **時刻**: U7 は時刻に頼る処理を持たない。`sleep` と実時刻に頼らず、`findBy*`・`waitFor` と約束の解決で待つ。
- **`console` の見張り**: `vi.spyOn(console, 'log' | 'info' | 'warn' | 'error' | 'debug')` で5つを見張り、どの流れでも呼ばれないことを見る（`vitest.setup.ts` の `vi.restoreAllMocks()` で戻る）。jsdom が axe の色の検査で出す「Not implemented: HTMLCanvasElement…」だけは除く（U6 と同じ）。呼ばれたときは引数にパスワード・氏名・メールアドレスが無いことも見て、失敗の説明に出す。
- **ブラウザの保存と URL**: 流れの後に localStorage・sessionStorage のすべての鍵の値を読んで、パスワード・氏名（メールアドレスの形の初期値）が無いことを見る。URL は `window.location` の `search`・`hash` を見る。
- **実際のブラウザの検査（080 の組のテスト）**: サーバーは本物の WAR。組ごとの新しいコンテキストで、`GET /api/me/preferences` だけを `page.route` で見本（`support/preferencesFixtures.ts` の `preferencesSample`）の 200 に差し替え、ブランドカラーは U4 の `prepareCombo` で `/api/appearance` を差し替える（そのページの中だけで効く）。ログインは本物（初期管理者、`loginAsAdmin`）。`PUT`・`POST` は差し替えず、送らない。
- **測り（080 の測りのテスト）**: 何も差し替えない（本物の WAR・Mailpit・一時の内部DB）。利用者は新しい `support/registeredUser.ts` で招待から作る（計画の9節の決定 3。既存の手伝いは変えない）。Mailpit の API は Node の `fetch` で `GET` だけを呼ぶ。

## 6. テストのデータ

- **氏名**: 日本語の架空の氏名（例「山田 花子」「検査 太郎」）、初期値の形のメールアドレス（`example.test`・`example.com` の下の固定の値）、境界の値（254・255 コードポイント）、空白だけ（半角・全角・タブ）、内側の改行・タブ・ゼロ幅の空白（U+200B）。
- **パスワード**: 画面部品のテストは境界の値（11・12 コードポイント、「あ」24 文字（72 バイト）と「あ」24 文字＋「a」（73 バイト）、絵文字 11・12 文字）と固定のテストの値。080 の測りは実行ごとに作る `e2e-u7-pw-<乱数の16進>`（リポジトリに置かない）を2つ作り、交互に使う。
- **`detail` の目印**: 応答の `detail` に見分けやすい文字（例 `server-detail-marker`）を入れ、画面に出ないことを見る。
- **`fieldErrors`**: U2 の形（`[{ field, reason }]`）の見本と、壊れた形（配列でない、要素が文字列、`field` が数、知らない項目、同じ項目の重なり）。
- **080 の宛先**: 実行ごとに重ならない `u7-perf-<印>@example.com`（印は時刻と乱数、U5 の `newRunTag`）。E2E の初期管理者（`e2e-admin@example.com`、パスワードは実行ごとに作る）は読むだけで、テストの題・`test.step` の題・注記・添付に入れない。
- **080 の差し替えの答えの氏名**: 固定のテストの値 `検査 太郎` だけ。

## 7. 性質ベースのテストの種

- fast-check の失敗時の乱数の種（`seed`）と道（`path`）は、テストの出力（失敗の詳細）に出る。再現するときは、その値を `fc.assert(property, { seed, path })` に一時的に書いて Step 6 のコマンドで実行し、直した後に外す（既存の `frontend/src/features/dsl/submitInput.test.ts` と同じ扱い。全体の設定は足さない）。
- 対象は純粋な関数 `readFieldErrors` だけ: どんな JSON の値（`fc.jsonValue()`・`fc.anything()`）を渡しても例外を出さない、返す項目の名前は画面ごとの項目名の一覧の中だけで重ならない（`team.md` の Testing Posture、NFR9.6）。共用の確かめの関数の性質ベースのテストは U6 が持つ。

## 8. 実際のブラウザの検査と測り（080）の読み方

- 080 の組は U4 の手伝い（`frontend/e2e/support/displayCombos.ts`）の 20 組と同じ: (a) テーマ2×文字の大きさ3（`blue`、既定の幅）の6組、(b) ブランドカラー4×テーマ2（`md`、既定の幅）の8組、(c) テーマ2×文字の大きさ3（`blue`、375px × 812px）の6組。組ごとに1つのテストで、初期管理者でログインし、ユーザーメニューの「プリファレンス」「パスワードの変更」で移る。
- 組のテーマと文字の大きさは、`GET /api/me/preferences` の答えの差し替えで、画面の D2 のそろえ（本物の `applyUserPreferences`）を通して当てる（計画の9節の決定 1）。当たったことを `<html>` の属性で確かめ、当たらなければ検査の結果を使わずに失敗させる。
- 080 の状態は、`preferences-ready`（4つの値の入ったフォーム）・`preferences-invalid`（氏名を空にして「保存する」、画面の確かめで止まる）・`password-ready`（空の3つの項目）・`password-invalid`（3つを空のまま「変更する」）。どちらの誤りの状態も要求を送らない。
- axe の合否は想定外の違反（`violations` のうち既知の違反の一覧の外）0 件。既知の違反は、green・orange の組の `color-contrast` で、primary の Button で、状態ごとの一覧（`PREFERENCES_KNOWN_VIOLATIONS`）に名前（`data-testid`）が書かれたものだけ。既知の違反が一覧と一致しない（消えた・増えた）ときも失敗にする。判定できなかった要素（`incomplete`）は失敗にせず、規則の名前と件数を注記に残す。
- 測りの時間（開く: ユーザーメニューの項目を押す直前から「保存する」「変更する」が見えるまで、保存: 「保存する」を押す直前から Toast「保存しました」が見えるまで、パスワードの変更: 「変更する」を押す直前から Toast「パスワードを変更しました」が見えるまで）は、場面ごとの5回のミリ秒と目標（2,000・1,500・2,500）以内の回数を注記と添付に残す。時間では失敗しない。値は Build and Test が json の報告から写す。本物の `GET /api/me/preferences` の応答と見本の形の照合、CSP の違反・画面の問題は失敗の条件。
- 測りは1回の実行で招待を1件置き、Mailpit に1通届き、新しい利用者と監査の記録を一時の内部DB に残す。後の 090 は、これに頼らない。
- 失敗したときのトレース（`trace: 'retain-on-failure'`）と html の報告には、仮の資格情報・作った利用者のパスワード・アクセストークン・招待のトークンを含む要求が含まれうるため、コミット・共有しない（`cicd-pipeline.md` 4節）。
