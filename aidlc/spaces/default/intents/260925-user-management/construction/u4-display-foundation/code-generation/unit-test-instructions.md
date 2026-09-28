# Unit Test Instructions — U4 表示の設定の土台（u4-display-foundation）

U4 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の確かめ）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パス。U4 は画面の単位のため、バックエンドのテストを足さない。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | Vitest（既存）、jsdom | `frontend/vitest.config.ts`（`src/**/*.test.{ts,tsx}`、`e2e/` は対象外、`css: false`）。Node 24 の localStorage と重ならないよう `NODE_OPTIONS=--no-experimental-webstorage` を付ける（既存の `npm run test` と同じ） |
| 画面部品の描画と操作 | Testing Library（`@testing-library/react`）・user-event・jest-dom（既存） | `frontend/vitest.setup.ts`（jest-dom と vitest-axe の照合の登録、テストごとの片付け。変えない） |
| アクセシビリティ（構造） | vitest-axe（既存） | 画面部品ごとに1件（`LoginLanguageSwitch`・案内のある `LoginForm`・氏名を出す `ShellLayout`）。`expect(await axe(container)).toHaveNoViolations()` |
| 性質ベースのテスト | fast-check（既存） | 全体の設定は足さない。失敗時は fast-check が `seed`・`path` を出力に示す（既存の `frontend/src/features/dsl/submitInput.test.ts` と同じ扱い） |
| 骨組みの描画の支え | `frontend/src/app/testing/renderWithProviders.tsx`（Step 15 で広げる） | `ThemeProvider` → `LoginStateGate` → `DisplaySettingsProvider` → `I18nProvider` → `FeatureRegistryProvider` の並び。見た目の設定の約束は既定で答え済み（当てる値なし）、選択で差し替えられる。`stubBrowserLanguages`・`fakeProvider` は既存のまま |
| OS の配色の差し替え | 新しい `frontend/src/app/display-settings/testing/fakeColorScheme.ts` | `window.matchMedia` を、`prefers-color-scheme: dark` の今の値を持ち変化の知らせを送れる偽物に差し替える（jsdom には `matchMedia` が無い。全体の setup は変えない） |
| 実際のブラウザの検査 | Playwright（既存）＋ axe-core 4.13.0（Step 3 で devDependencies に明示で足す） | `frontend/playwright.config.ts`（`workers: 1`・Chromium だけ・`locale: 'ja-JP'`、Step 18 で reporter に `json` を足す）、`frontend/e2e/050-display-accessibility.e2e.ts`、`frontend/e2e/support/` |
| カバレッジ | `@vitest/coverage-v8`（既存） | `frontend/vitest.config.ts` の `thresholds`（行 80・分岐 70）。計測から外すのは `src/main.tsx`・型の宣言・テストのファイルだけ（変えない） |

新しいテストの依存は axe-core（実際のブラウザの検査だけで使う）だけ。Vitest・Playwright の設定のファイルは reporter の1行のほかに変えない。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U4 の置き場と、U4 が手を入れる既存の置き場だけに絞る。

単体テスト（U4 の範囲。表示の設定の土台・受け渡し・文言と I18nProvider・レイアウト・ログイン状態・`App`・ApiClient・AuthUi）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/display-settings src/app/login-handoff src/app/i18n src/app/layout src/app/login-state src/app/App.test.tsx src/shared/api-client src/features/auth)
```

層ごとの実行（Step 7・9・11・13・15・17。上のコマンドの一部）:

| Step | 対象 | コマンド |
|---|---|---|
| Step 7 業務処理（解き方の純粋な関数） | `resolveDisplaySettings` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/display-settings/resolveDisplaySettings.test.ts)` |
| Step 9 データアクセス | ブラウザの保存・受け渡し | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/display-settings/browserStorage.test.ts src/app/login-handoff)` |
| Step 11 API | ApiClient・見た目の設定の読み取り | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/shared/api-client src/app/display-settings/appearanceLoad.test.ts)` |
| Step 13 AuthUi のログイン状態の受け渡し | `loginStateProvider`・`LoginStateGate`・`authApi`・`authSession` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/auth/loginStateProvider.test.ts src/features/auth/authApi.test.ts src/features/auth/authSession.test.ts src/app/login-state)` |
| Step 15 表示の設定の土台 | `DisplaySettingsProvider`・置き場・OS の配色・`I18nProvider`・`App` | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/display-settings src/app/i18n src/app/App.test.tsx)` |
| Step 17 画面部品 | `LoginLanguageSwitch`・`LoginForm`・`LoginPage`・`LoginLayout`・`ShellLayout`・文言 | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/auth src/app/layout src/app/i18n/messages.test.ts)` |

固定先の更新の影響の確かめ（Step 2 に限る。make-you-chic-ui の Button の `aria-disabled` の変更が既存の DSL の画面のテストに及ぶかを見るため、既存の画面のテストを全件流す）:

```bash
./gradlew vendorUnchanged frontendTypecheck frontendBuild
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run)
```

DSL の画面だけを流し直すとき:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/dsl)
```

実際のブラウザの検査だけ（Step 19。`verify` と CI の外。WAR と Chromium と Mailpit が要る）:

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && caffeinate -i npx playwright test e2e/050-display-accessibility.e2e.ts)
```

- 事前に `(cd frontend && npx playwright install chromium)`。
- 050 はメールを使わないが、U1 の決定で E2E の WAR はメールを Mailpit へ送る設定で起動し、`./gradlew e2eTest` は Mailpit に届かなければ失敗するため、050 だけのときも Mailpit を起動しておく（基盤の設計の N2）。Mailpit は止めず消さない（README の手順のとおり開発者が片付ける）。
- 結果は `frontend/test-results/e2e-results.json`（json の報告）と `frontend/playwright-report/`（html の報告）。どちらも管理外で、コミット・共有しない。

最初のテストより前の確かめ（Step 5）: 上の単体テストのコマンドを変更の前の状態で実行し、既存のテストが通ることを確かめる。U4 の新しいテストがまだ無くても、`src/app`・`src/shared/api-client`・`src/features/auth` の既存のテストに当たる。`(cd frontend && npx playwright test --list)` で E2E の一覧が読めることも確かめる。

統合の前（Step 23・Step 24）は単位の範囲ではなく全体を流す（計画のとおり）:

```bash
docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- テストの件数を報告するときは、実測の数字だけを報告する。バックエンドのテストが UP-TO-DATE で飛ばされないよう、verify には `:backend:cleanTest :backend:cleanIntegrationTest` を付ける（`project.md` の Testing Posture）。
- colima の環境変数を渡さないと対象DB のテストが SKIPPED になる（`project.md` の Testing Posture）。U4 のテストはコンテナを使わない。

## 3. テストの一覧（Standard の量）

| 部品 | テスト（対象と同じ場所） | 件数の目安 | 主に確かめること |
|---|---|---|---|
| 解き方の純粋な関数 | `src/app/display-settings/resolveDisplaySettings.test.ts` | 8 件＋fast-check 4 件 | 保存の値の読み取りと検証（壊れた JSON・知らない値・知らない項目）、テーマの解き方、画面の値の決め方（ログインの前後・保存の後・見せ方の結び付けと軸ごとの置き換え）、見た目の設定の応答の項目ごとの検証、言語。性質: どんな文字列でも例外を出さない・受け入れた値は許される値だけ・3つの軸がいつも許される値・言語はいつも `ja`・`en` |
| ブラウザの保存 | `src/app/display-settings/browserStorage.test.ts` | 7 件 | 3つの保存と読み直し、言語だけの保存、写しの鍵の書き直し（U4 の鍵あり・無し・OS dark）、ブランドとフォントファミリーの鍵を書かない、`getItem`・`setItem` の例外で落ちない、書いた JSON の項目が3つの中だけ |
| 受け渡し | `src/app/login-handoff/loginHandoff.test.ts` | 5 件 | 渡した値を読める、読むだけでは消えない、消した後は無い、URL・localStorage・sessionStorage にメールアドレスが無い |
| ApiClient | `src/shared/api-client/apiClient.test.ts`（既存に足す） | 足す 8 件 | 公開の3つのパスに `Authorization` が付かない、401 でも更新しない、問い合わせつきも同じ、似た別のパスにはトークンが付く、認証の API の3つは今までどおり。`Accept-Language` が付く（認証・公開・ほか・`apiDownload`）、呼び出し側の指定を上書きしない、関数が無ければ付けない、許されない値を付けない、`resetApiClient` で消える |
| 見た目の設定の読み取り | `src/app/display-settings/appearanceLoad.test.ts` | 6 件 | 2回呼んでも `fetch` は1回、トークンが付かない、成功で2項目、許されない値の項目は当てない、500・通信の失敗・JSON でない本文で「当てる値なし」、例外で終わらない |
| OS の配色 | `src/app/display-settings/colorScheme.test.ts` | 4 件 | 今の値、変化の知らせ、購読の解除、`matchMedia` が無ければ light |
| 置き場と口の関数 | `src/app/display-settings/displaySettingsStore.test.ts` | 6 件 | `saveBrowserDisplaySettings`（3つの保存と見せ方を捨てる）、`saveBrowserLanguage`（言語だけ・言語の見せ方を捨てる）、見せ方の結び付け、`resetDisplaySettings` |
| `DisplaySettingsProvider` | `src/app/display-settings/DisplaySettingsProvider.test.tsx` | 8 件（場面をまとめる） | ゲート（答えの順を入れ替えた2通り）、並べ読み（2つの要求がともに送られる・StrictMode で1回）、見た目の設定の成功・失敗・許されない値、ログインの前（保存の値・ブラウザの言語設定と OS）、前の利用者の見た目が出ない、セッションの復元の最初の描画と氏名、`system` の追従、口（`setPreview`・`clearPreview`・`applyUserPreferences`・`setLanguage`・`saveBrowserDisplaySettings`）、ほかのタブの変化を打ち消さない、保存の中身に秘密が無い、localStorage の例外で描かれる |
| AuthUi の受け渡し | `src/features/auth/loginStateProvider.test.ts`・`src/app/login-state/LoginStateGate.test.tsx`・`src/features/auth/authApi.test.ts`・`authSession.test.ts`（既存に足す・直す） | 5〜6 件 | 氏名と3つの値、未ログインで落ちる、`preferences` の無い提供元もそのまま動く、応答の4項目 |
| `I18nProvider`・`App` | `src/app/i18n/I18nProvider.test.tsx`・`src/app/App.test.tsx`（既存を直す・足す） | 直しと足す 2〜3 件 | 言語を props で受けて文言が変わる、同じ描画で切り替わる、起動の誤りの画面も同じ並び |
| `LoginLanguageSwitch` | `src/features/auth/LoginLanguageSwitch.test.tsx` | 6 件 | 名前と `lang`、`aria-pressed`、en でも「日本語」、選ぶと文言・`<html lang>`・言語だけの保存、切り替えた後のログインの要求の `Accept-Language`、キーボードとフォーカス、vitest-axe |
| `LoginForm`・`LoginLayout`・`LoginPage` | 既存の `LoginForm.test.tsx`・`LoginLayout.test.tsx`・`LoginPage.test.tsx` に足す | 5〜6 件 | 受け渡しの案内とメールアドレスの初期値、2回目と受け渡しの無い表示では出ない、URL と保存にメールアドレスが無い、右上の置き場、案内のある状態の vitest-axe |
| `ShellLayout` | 既存の `ShellLayout.test.tsx` に足す | 3〜4 件 | 名前が氏名、保存の直後に新しい氏名、氏名が無いときの戻り、vitest-axe |
| 文言 | 既存の `src/app/i18n/messages.test.ts`・`src/features/auth/registration.test.ts` | 自動 | 足した鍵が ja・en の両方にあり空でない |
| 実際のブラウザの検査 | `frontend/e2e/050-display-accessibility.e2e.ts` | 21 件（20 組＋測定） | 組ごとの axe の違反 0 件（WCAG 2.0・2.1 の A・AA のタグ）と横のはみ出し無し、切り替えが効いたことの確かめ、`color-contrast`・`scrollable-region-focusable` が流れたこと。測定のテストは5回の時間（失敗させない）と、CSP の違反 0 件・`sans` で Noto Serif JP を読まない・本物の見た目の設定の応答と見本の一致（失敗させる） |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`describe`・`it`・`test`）は英語で書く。テストのデータは日本語でよい。新しいファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。

`team.md` の必須のテストと部品 7節のうち U4 が受け持つもの:

| 必須のテスト・確かめ | 確かめるテスト |
|---|---|
| 画面: ログイン画面のアクセシビリティ検査（既存の検査を残し、言語の切り替えと案内の状態を足す） | `LoginForm.test.tsx`・`LoginLanguageSwitch.test.tsx`（vitest-axe）、050（実際のブラウザ） |
| 秘密情報の漏えい: ブラウザの保存にトークン・メールアドレス・氏名が無い、受け渡しのメールアドレスが URL・保存に無い | `DisplaySettingsProvider.test.tsx`・`browserStorage.test.ts`・`loginHandoff.test.ts`・`LoginForm.test.tsx` |
| 画面の側で公開の API にトークンを付けない（サーバー側の 401・403・200 の確かめの代わりにしない。サーバー側は U3・U8 の結合テスト） | `apiClient.test.ts`・`appearanceLoad.test.ts` |
| 要求の言語（`Accept-Language`）に許される値だけ | `apiClient.test.ts`・`resolveDisplaySettings.test.ts`（性質ベース） |
| 見た目の設定の応答の許されない値・形の誤りを当てない | `resolveDisplaySettings.test.ts`・`appearanceLoad.test.ts`・`DisplaySettingsProvider.test.tsx` |
| 性質ベースのテスト（fast-check）を4つの純粋な関数に | `resolveDisplaySettings.test.ts` |
| 既存の画面のテストと E2E が通り続ける | 単体テストの全件（Step 2・Step 24）、`./gradlew e2eTest`（Step 23） |

## 4. カバレッジの目標

- フロントエンドの全体: 行 80% 以上・分岐 70% 以上（既存の `thresholds`。`./gradlew verify` の `frontendCoverage` で判定する）。フロントエンドの下限は全体の合計だけで、ディレクトリごとの下限は無いが、U4 の新しいファイルもそれぞれ行 80%・分岐 70% を目安にし、下回るファイルがあればテストを足す（Step 17・21）。
- 計測の除外を増やさない。`frontend/e2e/` の検査と手伝いは既存どおり Vitest の計測の対象外（`vitest.config.ts` の `include` が `src/**`）。`src/app/testing/` と `src/app/display-settings/testing/` のテストの支えは今までどおり計測に入る。
- B3 の後の実測（47 ファイル 316 件、行 97.86%・分岐 93.6%）を基準に、Step 1・Step 24 で実測して比べる。
- U4 の範囲のカバレッジを見るとき（目安。下限の判定は verify で行う）:

```bash
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/display-settings src/app/login-handoff src/app/i18n src/app/layout src/app/login-state src/app/App.test.tsx src/shared/api-client src/features/auth --coverage --coverage.include='src/app/display-settings/**' --coverage.include='src/app/login-handoff/**' --coverage.include='src/shared/api-client/**' --coverage.include='src/features/auth/**' --coverage.include='src/app/i18n/**' --coverage.include='src/app/layout/**' --coverage.include='src/app/login-state/**' --coverage.include='src/app/App.tsx')
```

  設定のファイルの下限（行 80・分岐 70）はこの範囲の値にもそのまま当たる（下限を外す指定はしない）。範囲の外のテストが通る分を含まないため値は目安で、全体の下限の判定は `./gradlew verify` の `frontendCoverage` で行う。報告は `frontend/coverage/index.html`。

## 5. 差し替え（モック・スタブ）の方針

- **fetch**: ApiClient・見た目の設定の読み取り・`App` の並べ読みの確かめでは、既存の `apiClient.test.ts` と同じく `vi.stubGlobal('fetch', ...)` で差し替える。並べ読みの確かめは、`/api/appearance` とトークンの更新の2つの `fetch` をどちらも答えない約束で止め、2つがともに呼ばれたことを見てから答える。`resetApiClient`・`resetAppearanceLoad` をテストごとに呼ぶ。
- **make-you-chic-ui**: 差し替えない。本物の `ThemeProvider` の中で描き、`<html>` の `data-theme`・`data-font-size`・`data-brand`・`data-font-family` と make-you-chic-ui の鍵（`design-system-*`）の値で確かめる。ほかのタブの変化は `window.dispatchEvent(new StorageEvent('storage', { key, newValue, storageArea: localStorage }))` で作る。
- **ログイン状態**: 既存の `fakeProvider` と、知らせを送れる偽の提供元（`subscribe` を持つ）をテストの中で作る。本物の `authSession` を使う確かめ（ログインの成功・更新の応答）は、fetch の差し替えで応答を返し、`resetAuthSession` をテストごとに呼ぶ。
- **OS の配色**: `fakeColorScheme` で `window.matchMedia` を差し替え、変化の知らせを送る。差し替えないテストは `matchMedia` が無い状態（light）として動く。
- **ブラウザの保存**: jsdom の localStorage をそのまま使い、テストの前後で `localStorage.clear()`・`sessionStorage.clear()`。例外の場合は `vi.spyOn(Storage.prototype, 'getItem' | 'setItem')` で投げる（`vitest.setup.ts` の `vi.restoreAllMocks()` で戻る）。
- **置き場の状態**: 表示の設定の置き場・受け渡し・見た目の設定の約束はモジュールの中の状態のため、テストごとに `resetDisplaySettings`・`resetLoginHandoff`・`resetAppearanceLoad` を呼ぶ（`renderWithProviders` の手伝いにまとめる）。
- **時刻**: U4 は時刻に依存しない。`sleep` と実時刻に頼らず、`findBy*`・`waitFor` と約束の解決で待つ。
- **実際のブラウザの検査**: サーバーは本物の WAR。ブランドカラーの組だけ `page.route('**/api/appearance', ...)` で見本の形の答えに差し替える（そのコンテキストのページの中だけで効く）。測定のテストは差し替えず本物の応答を使い、見本と項目の名前・型が一致することを確かめる。ログインしない。

## 6. テストのデータ

- **メールアドレス**: `example.com` などの予約されたドメインだけを使う（例 `hanako@example.com`・`invitee@example.com`）。実在の宛先を書かない。秘密の漏えいの確かめでは見分けやすい値（例 `display-leak-check@example.com`）を使い、短い文字列の偶然の一致で確かめが崩れないようにする。
- **氏名**: 日本語の氏名（例「山田 花子」）と、漏えいの確かめ用の見分けやすい氏名（例「表示確認用の氏名」）。
- **アクセストークン**: 形だけの仮の文字列（例 `test-access-token-for-display`）。Gitleaks に当たらない値にする。
- **表示の設定**: 3つの軸のすべての値（`ja`・`en`、`light`・`dark`・`system`、`sm`・`md`・`lg`）。壊れた値の例は、JSON でない文字列（`{`・`not-json`）、配列（`[]`）、数（`1`）、`null`、知らない値（`{"theme":"blue","fontSize":"xl","language":"fr"}`）、大文字（`"Dark"`）、余計な項目（`{"theme":"dark","token":"x"}`）。
- **見た目の設定の応答**: 4色（`blue`・`green`・`purple`・`orange`）と2種（`sans`・`serif`）、許されない値（`red`・`mono`・`BLUE`・` blue`）、欠けた項目、余計な項目、配列・数・`null`。
- **ブラウザの言語設定**: 既存の `stubBrowserLanguages`（`['ja-JP']`・`['en-US']`・`['fr-FR']`）。
- **実際のブラウザの検査**: E2E の初期管理者（`e2e-admin@example.com`、パスワードは実行ごとに作る）はログインに使わない。json の報告に初期管理者のメールアドレスと仮のパスワードの値が入らないことを確かめる（Step 19）。

## 7. 性質ベースのテストの種

- fast-check の失敗時の乱数の種（`seed`）と道（`path`）は、テストの出力（失敗の詳細）に出る。再現するときは、その値を `fc.assert(property, { seed, path })` に一時的に書いて Step 7 のコマンドで実行し、直した後に外す（既存の `frontend/src/features/dsl/submitInput.test.ts` と同じ扱い。全体の設定は足さない）。
- 対象は純粋な関数だけ: 保存の値の読み取りと検証（どんな文字列でも例外を出さない、受け入れた値は許される値だけ）、テーマの解き方（どの入力でも `light`・`dark`）、画面の値の決め方（どの入力でも3つの軸が許される値、見せ方の無い軸は当てている値のまま）、言語の決め方（どの入力でも `ja`・`en`）（`team.md` の Testing Posture、NFR9.9）。

## 8. 実際のブラウザの検査の読み方（050）

- 組は (a) テーマ2×文字の大きさ3（`blue`、既定の幅）の6組、(b) ブランドカラー4×テーマ2（`md`、既定の幅）の8組、(c) テーマ2×文字の大きさ3（`blue`、375px × 812px）の6組の合わせて 20 組で、組ごとに1つのテスト。対象はログインの画面（`/login`）の最初の状態（登録の完了の案内とログインの誤りの表示は画面部品のテストで見る）。
- axe の合否は違反（`violations`）0 件。判定できなかった要素（`incomplete`）は失敗にせず、規則の名前と件数を注記に残す。
- 最初の画面の時間は、測定のテストの注記と添付（5回の値のミリ秒と 2 秒以内の回数）に残る。時間では失敗しない。値は Build and Test が json の報告から写す。
- 失敗したときのトレース（`trace: 'retain-on-failure'`）には実行ごとの仮の資格情報が含まれうるため、コミット・共有しない（`cicd-pipeline.md` 5節）。
