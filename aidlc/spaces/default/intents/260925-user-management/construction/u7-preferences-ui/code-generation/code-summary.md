# Code Summary — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

この文書は、Bolt B5 の最後の単位として、承認済みの `code-generation-plan.md` の Step 1〜22 を実行した記録です。
- 作業のブランチは、`develop` の先頭 `08c9183` から作った `feature/260925-user-management-b5-u7` です（コミットはしていない。統合は squash）。
- パスはリポジトリのルートからの相対パスです。記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）です。
- 数値はすべてこの段で実測したものです。
- 生成の途中で3回止めて依頼者に諮り、3つとも決定を受けて続けました（7節）。

## 1. 作ったもの・手を入れたもの

### 1.1 骨組みの変更（`frontend/src/app/`、機能設計 9節）

| ファイル | 中身 |
|---|---|
| `registry/types.ts` | `UserMenuItemRegistration` を、`action` だけの形と `path` だけの形（もう一方は `?: never`）の union にした |
| `registry/validateRegistrations.ts` | ユーザーメニューの項目に2つの検査を足した。<br>(1) `action`（関数）と `path`（文字列）のどちらも無い・両方ある。型の外の値（関数でない `action`・文字列でない `path`）も含む。<br>(2) `path` が登録済みの画面の URL（ホームを含む）と完全に一致しない。<br>既存の検査と順は変えていない |
| `layout/ShellLayout.tsx` | `path` の項目は、Dropdown の `href` と `onClick`（`event.preventDefault()` の後に `navigate(path)`）にした。`action` の項目は今までどおり |
| テスト | 次の3つにテストを足した。<br>・`validateRegistrations.test.ts`: ＋5 件（絶対 URL・`javascript:`・`//`・末尾の `/` を含む）<br>・`ShellLayout.test.tsx`: ＋4 件（マウス・Enter・Space・ログアウトのボタン・メニューを開いた状態の vitest-axe）<br>・`navigationItems.test.ts`: ＋1 件（`navigationItems.ts` は変えていない） |

### 1.2 プリファレンスとパスワードの変更の画面（`frontend/src/features/preferences/`、新しい置き場）

| ファイル | 中身 |
|---|---|
| `registration.ts` | 機能の登録。<br>・`featureId: 'preferences'`<br>・画面は `/me/preferences`・`/me/password`（`SHELL`・`LOGGED_IN`、`lazy`）<br>・ユーザーメニューの `preferences-open`（80）・`preferences-password`（90）<br>・サイドバーの項目は無い |
| `messages.ts` | `preferencesMessages`（機能設計 7節の 40 の鍵、ja・en） |
| `preferencesApi.ts` | 次を置いた。<br>・関数: `getPreferences`・`savePreferences`・`changePassword`<br>・定数: `ME_PREFERENCES_PATH`・`ME_PASSWORD_PATH`<br>・型: `Preferences`・`PasswordChangeInput`・`PreferencesField`・`PasswordField`<br>・形の誤りの種類: `PreferencesShapeError`<br>形の確かめは U4 の `isDisplayLanguage`・`isThemeChoice`・`isFontSize` を使う |
| `fieldErrors.ts` | `readFieldErrors(problem, fields)` |
| `errorMessages.ts` | `FieldReason`・`toFieldReason`・`fieldMessageKey`、知らせの鍵の定数 |
| `formChecks.ts` | `checkPreferencesForm`・`checkPasswordChangeForm`・`PREFERENCES_FIELDS`・`PASSWORD_FIELDS` |
| `failure.ts` | `isApiResponseError`・`badRequestCode`（8節の差 #4） |
| `usePreferencesForm.ts`・`usePasswordChangeForm.ts` | 2つのフック（部品 3.1・3.2） |
| `PreferencesPage.tsx`・`PreferencesLoadFailure.tsx`・`PreferencesForm.tsx`・`PasswordChangePage.tsx`・`PasswordChangeForm.tsx` | 画面部品 |
| `PreferencesForm.css`・`PreferencesPage.css` | 部品と同じ場所の素の CSS。make-you-chic-ui のトークンだけを使い、内部のクラスは上書きしない |
| `testing/fixtures.ts`・`testing/renderPreferences.tsx` | テストの支え。<br>・偽のサーバー（メソッドつきの鍵）<br>・答えを後で決める `deferred`<br>・見本の値と、トークンの更新の応答<br>・`renderPreferencesApp`: 本物の AppRouter とアプリシェルで描く<br>・`renderPreferencesPart`: 部品だけを描く |
| テスト | 10 ファイル 97 件。<br>`fieldErrors` 8（性質ベース 2 を含む）・`errorMessages` 5・`formChecks` 9・`preferencesApi` 8・`PreferencesLoadFailure` 5・`PreferencesForm` 10・`PasswordChangeForm` 6・`PreferencesPage` 23・`PasswordChangePage` 17・`registration` 6 |

### 1.3 実際のブラウザの検査と測り（`frontend/e2e/`）

| ファイル | 中身 |
|---|---|
| `080-preferences-accessibility.e2e.ts`（新しい） | 20 組のテスト（組ごとに2画面 × 2状態）と、測りのテスト1件 |
| `support/preferencesFixtures.ts`（新しい） | `preferencesSample(theme, fontSize)`（氏名 `検査 太郎`）・`hasPreferencesShape`・`SAMPLE_DISPLAY_NAME` |
| `support/registeredUser.ts`（新しい） | `registrationPrerequisites`・`createRegisteredUser`・`newRunPassword`・`loginWithForm`・`openUserMenuItem` |
| `support/axe.ts`（足しただけ） | 次を足した。<br>・`PREFERENCES_KNOWN_VIOLATIONS` と `PreferencesAxeState`<br>・`AVATAR_KNOWN_COMBOS`・`AVATAR_KNOWN_VIOLATION`・`withAvatarKnownViolation`<br>・`FORM_FIELD_ERROR_KNOWN_STATES`・`formFieldErrorTargets`・`withFormFieldErrorKnownViolation`<br>既存の一覧・`runAxe`・`splitKnownViolations` は変えていない |

### 1.4 文書と既存のテスト

- `README.md`:
  - E2E の表に 080 の行と「080 について」を足した。
  - 060 の節の「後の単位（U6・U7）も同じ当て方」の文を、実際の当て方に直した（9節の決定 1）。
  - 新しい節「プリファレンスとパスワードの変更の画面（U7）」を足した（見せ方の最中のトークンの更新を含む）。
  - 「画面の表示の設定（U4）」の既知の制約に、U7 の行と、アバター・dark の誤りの文字の2つを足した。
  - 「後の単位が使う差し込み口」の画面の差し込み口の行に、ユーザーメニューの `path` を足した。
- `frontend/src/features/README.md`: 節「ユーザーメニューの項目で画面へ移る（`path`）」を足した。
- `frontend/src/features/auth/registration.test.ts`: 1行を型の絞り込みに直した（7節の決定 A、8節の差 #1）。
- 変えていないもの:
  - アプリの側: ApiClient・`frontend/src/shared/validation/`・`frontend/src/app/display-settings/`・`navigationItems.ts`・`renderWithProviders.tsx`・`features/auth` のテスト以外・ほかの機能
  - テストと道具の設定: `playwright.config.ts`・`playwright-secret-check-reporter.ts`・`vitest.config.ts`・`tsconfig.json`・`package.json`・`package-lock.json`
  - 既存の E2E: 010〜070・090 と既存の手伝い（`axe.ts` は足しただけ）
  - サーバーの側: `backend/`・`application.yaml`・`SecurityConfig`・`compose.yaml`・`build.gradle.kts`
  - サブモジュール: `vendor/`

## 2. Step 2 の前提の確かめ（読み取りだけ）と突き合わせ

- **固定先**: `vendor/make-you-chic-ui` が `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`、`vendor/java-mustache-processor` が `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4` です。計画のとおりでした。
- **make-you-chic-ui の口**: どれも想定どおりで、(a) には当たりませんでした。
  - Dropdown: `MenuItem.href` なら `<a href role="menuitem">`、Space でも `click()`。選んだ後に `close(true)` でフォーカスを戻す。
  - RadioGroup: `legend` なら `fieldset`・`legend`。`label` は ReactNode、選択肢に `lang`。
  - Button: `loading` で `aria-disabled`・`aria-busy`。`handleClick` で `preventDefault` し、`onClick` を呼ばない。
  - FormField: `error` の文字は `role="alert"`。`helperText` を結び付ける。
  - TextInput: `forwardRef`。
  - Alert: `dismissLabel`。
  - Toast: `aria-live="polite"`。
- **U4 の口**: `useDisplaySettings` の値と操作、`LANGUAGE_NAMES`・`THEME_CHOICES`・`FONT_SIZES`、文言の `display.theme.*`・`display.fontSize.*`（ja の `md` は「標準」）。`renderWithProviders` は `ToastProvider` を持ちません。
- **骨組み**: `ShellLayout` には U4 の B4 の氏名の変更が入っていました。ログアウトは `auth-logout`（`action` だけ、order 100）です。
- **U2 の API**: `MeController` と README の「契約との差」は、`fieldErrors: [{ field, reason }]`・`reason` の6つ（`FieldErrorReason`）・`PASSWORD_CURRENT_MISMATCH`・`MALFORMED_REQUEST` で一致しました（機能設計の承認の場の G6）。
- **U6 の共用の関数**: `validateDisplayName` → `required`・`tooLong`・`invalidCharacter`、`validateNewPassword` → `required`・`tooShort`・`tooLong`、`validatePasswordConfirmation` → `required`・`mismatch` でした。
  - `errorMessages.ts` を作った後の最終の突き合わせでも、`security-design.md` 3.3 の表と一致しました（NFR 設計の U7 R-01・NFR 要件の U7 R-02）。
  - 今のパスワードの空は U7 の確かめ（`formChecks.ts`）です。
- **飛ばす道の前提**: `playwright.config.ts` の `webServer.env` に `SPRING_MAIL_HOST=localhost`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_WEB_BASE_URL` が入り、`build.gradle.kts` の `e2eTest` が Mailpit の `/api/v1/info` を確かめます（U5 R-02・N10）。Step 16・20 の実行で、測りは飛ばされませんでした。
- **実行の準備（Step 4）**:
  - 骨組みの3つのテストのファイル: 22 件が通り、終了の状態 0。
  - `src/features/preferences` だけの `--passWithNoTests`: 終了の状態 0。
  - `npx playwright test --list`: 8 ファイル 69 件。

## 3. 実測

### 3.1 変更の前と後の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`

colima で、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して流しました。

| 項目 | 変更の前（Step 1） | 変更の後（Step 21） |
|---|---|---|
| 結果・時間 | 1回目は失敗（3.2）、2回目は成功で 6分6秒 | 成功で 6分 |
| バックエンドの単体・結合 | 1,234 件・562 件（失敗 0、飛ばした 0） | 1,234 件・562 件（失敗 0、飛ばした 0。対象DB のテストも実行された） |
| フロントエンド | 81 ファイル 625 件 | 91 ファイル 732 件（＋10 ファイル・＋107 件 ＝ U7 の 97 件＋骨組みの 10 件） |
| フロントエンドのカバレッジ | 行 97.54%・分岐 93.06% | 行 97.44%・分岐 92.67% |
| バックエンドのカバレッジ | 行 98.78%・分岐 94.38% | 行 98.78%・分岐 94.38%（変わらない） |
| Gitleaks・OSV-Scanner・`vendorUnchanged` | 通った | Gitleaks は no leaks found、`vendorUnchanged` は通った。OSV-Scanner は UP-TO-DATE（lockfile を変えていないため。3.4 で確かめた） |

### 3.2 既存の `DisplaySettingsProvider.test.tsx` が1回だけ落ちた件（依頼者の指示で記録）

- **いつ・何が**: Step 1 の変更の前の1回目の verify（何も変える前）で、`src/app/display-settings/DisplaySettingsProvider.test.tsx` の「applies saved preferences at once: text, lang, request language and name」の1件が落ちました（624/625 が通過）。
  - `applyUserPreferences` の直後に `Home` を待ったところ、`ホーム` でした。
- **その後の実行**: 単独では5回とも通りました。コードを変えずに流し直した verify も通りました。
  - この後の実行（Step 14 の範囲の実行、Step 18 の `frontendCoverage`、Step 21 の verify）でも再発していません。
- **原因**: 確かめていません。
- **扱い**: team.md の「不安定なテストは原因を直すまで統合しない」に当たりうるため、統合の前に依頼者に扱いを確かめたい点として 10節に挙げます。

### 3.3 U7 の新しいファイルと骨組みの変えたファイルのカバレッジ（Step 18 の `frontendCoverage`、行・分岐）

どれも行 80%・分岐 70% 以上でした。計測の除外は増やしていません。

| ファイル | 行 | 分岐 |
|---|---|---|
| `app/layout/ShellLayout.tsx`・`app/registry/types.ts`・`app/registry/validateRegistrations.ts` | 100 | 100 |
| `PasswordChangeForm.tsx`・`PasswordChangePage.tsx`・`PreferencesLoadFailure.tsx`・`PreferencesPage.tsx`・`failure.ts`・`fieldErrors.ts`・`formChecks.ts`・`messages.ts`・`preferencesApi.ts`・`registration.ts`・`testing/renderPreferences.tsx` | 100 | 100 |
| `PreferencesForm.tsx` | 95.23 | 82.14 |
| `errorMessages.ts` | 100 | 87.5 |
| `usePreferencesForm.ts` | 94.44 | 87.67 |
| `usePasswordChangeForm.ts` | 92.85 | 83.33 |
| `testing/fixtures.ts` | 97.67 | 85.71 |

### 3.4 ビルドの成果物（Step 17、NFR6.6・NFR9.10）

- **差分が無いもの**: `application.yaml`・`SecurityConfig.java`・`frontend/package.json`・`frontend/package-lock.json`・`vendor/` には差分がありません。`git submodule status` も上のとおりで、`./gradlew vendorUnchanged` は通りました。
- **`dist/index.html`**: `<script>` は `src` つきの1つだけで、`<style>` はありません。
- **外部の URL**: `frontend/src/features/preferences/` には、ライセンスの URL のほかにありません。
- **遅延読み込みの塊**: `dist/.vite/manifest.json` では、2つの画面が入口から静的にたどれない動的な読み込みの塊でした。

  | 塊 | 圧縮前 | gzip |
  |---|---|---|
  | `PreferencesPage-*.js` | 7,812 バイト | 2,599 バイト |
  | `PasswordChangePage-*.js` | 4,109 バイト | 1,568 バイト |
  | 2つが共有する `PreferencesPage-*.js`（フォームの CSS と部品） | 3,247 バイト | 1,332 バイト |

- **大きさの前後**:

  | 項目 | 変更の前 | 変更の後 |
  |---|---|---|
  | 初回の JavaScript（gzip） | 121.9 KB（`index` 83.7・`useTranslation` 16.6・`I18nProvider` 21.6） | 123.1 KB（`index` 98.7・`useTranslation` 16.6・`hooks` 7.8） |
  | `dist/assets/` の JavaScript | 10 ファイル・472,521 バイト | 14 ファイル・494,108 バイト |
  | WAR | 101,587,150 バイト | 101,595,012 バイト |

  - 初回の JavaScript は ＋1.2 KB で、500 KB の目安の警告はありません。
  - 登録（`registration.ts` と文言）は入口に入ります。Vite が共有の塊の分け方を変えました。

## 4. 実際のブラウザの検査と測り（080、Step 16・20）

- **実行の回数**: 依頼者の決定 B・A（7節）を入れた後に、080 だけを2回続けて流し、どちらも 21 件が通りました（1.6 分ずつ）。
  - 組・画面・状態ごとの想定外の違反の件数と、既知の違反の一覧は、2回で完全に同じでした。
- **80 の状態の結果**:
  - 想定外の違反 0 件、横のはみ出し 0 件、`incomplete` 0 件。
  - `/api/me/` への書き込み 0 件、CSP の違反と画面の問題 0 件。
  - `GET /api/me/preferences` の差し替えは、組ごとに1回使われました。
- **状態ごとの既知の違反**（組の数）:

  | 状態 | primary の Button | アバター | FormField の誤りの文字 |
  |---|---|---|---|
  | `preferences-ready` | `preferences-save-button`（green・orange の light と dark、4 組） | 19 組（purple light を除く） | — |
  | `preferences-invalid` | 同上 4 組 | 19 組 | dark の 10 組（1件） |
  | `password-ready` | `preferences-password-submit-button`（4 組） | 19 組 | — |
  | `password-invalid` | 同上 4 組 | 19 組 | dark の 10 組（3件） |

- **測り**: 何も差し替えず、招待から作った利用者で行いました。飛ばされず、本物の `GET /api/me/preferences` の応答と見本の形は3回とも一致しました（`realResponseShape: true`）。時間はどれも目標以内で、関門にはしていません。

  | 場面（目標） | 080 の1回目 | 080 の2回目 | e2eTest（Step 20） |
  |---|---|---|---|
  | 開く・プリファレンス（2,000） | 120・105・105・119・101 | 107・105・104・105・107 | 106・107・52・101・56 |
  | 開く・パスワードの変更（2,000） | 52・54・66・53・50 | 51・52・51・54・53 | 54・49・54・67・53 |
  | 保存（1,500） | 63・72・58・61・57 | 62・56・56・73・57 | 62・57・58・60・60 |
  | パスワードの変更（2,500） | 826・818・814・816・811 | 812・817・827・827・812 | 813・809・816・814・819 |

  値の単位はミリ秒です。どれも 5/5 回が目標以内でした。

- **json の結果（N9）**: 2回の 080 と e2eTest の `frontend/test-results/e2e-results.json` を値の文字列で検索しました。
  - `u7-perf-`・`e2e-u7-pw-`・`#token=`・`token=`・`eyJ`・`e2e-admin@example.com`・`計測 花子` は、どれも 0 件でした。
  - `playwright-secret-check-reporter.ts` の確かめも毎回通りました。
  - `test.step` の題は json の `steps` に入ります（6種類の英語の題、秘密を含まない）。
  - 結果のファイルはコミット・共有しません。
- **E2E の全件（Step 20）**: `caffeinate -i ./gradlew e2eTest` で 90 件がすべて通りました（3分16秒、Playwright は 3.2 分）。
  - 骨組みの変更の後も、ユーザーメニューの「ログアウト」を使う 020・030・040・090 は変えずに通りました（NFR9.4・NFR9.8）。
  - 時間の内訳（テストの時間の合計）は次のとおりです。

    | 部分 | 時間 |
    |---|---|
    | 流れ（010〜040・090） | 20.1 秒 |
    | 検査（050・060・070・080 の組のテスト） | 96.4 秒（050 5.4・060 40.9・070 18.4・080 31.7） |
    | 測り（050 3.9・060 8.7・080 56.2） | 68.8 秒 |

- **Mailpit**: 止めず消していません（README の「手元でメールを見る」の2行で開発者が片付ける）。

## 5. 見せ方の最中のログイン状態の更新（機能設計 10節の (f)、9節の決定 4）

- **確かめ方**: `PreferencesPage.test.tsx` の「records what the screen and the form show after a token refresh」で、テーマ「ダーク」を選んで見せている間にトークンの更新（`refresh()`、同じ利用者の新しいログイン状態）を起こしました。
- **結果（食い違いが起きる）**: U4 が見せ方を捨てるため、画面は当たっている値（ライト、`data-theme` なし）に戻り、フォームのテーマは選んだ「ダーク」のまま残ります。
- **解け方**: 次の選択（文字の大きさ「大」）で、画面はフォームの値（dark・lg）に戻りました。
- **扱い**: 決定 4（A）のとおり生成は止めず、U4 は変えていません。README の U7 の節に既知の動きとして書きました。

## 6. 依頼者の決定と計画との差

承認済みの計画と設計の文書は書き換えず、差をここに記録します（`project.md` の決まり）。計画の 8節に挙げた差（`formChecks.ts`・`PreferencesShapeError`・`fetch` の差し替え・画面部品ごとの vitest-axe・`renderPreferences.tsx`・共用の手伝い・080 の番号と当て方・Mailpit の片付け・見本と本物の照合・Toast の待ち方・件数）は、計画のとおり作りました。

| # | 対象 | 計画の形 | 実際の作り | 理由・依頼者の決定 |
|---|---|---|---|---|
| 1 | `features/auth/registration.test.ts` の1行 | 計画 2.2・9節の決定 5 の (f): ほかの機能の既存のテストは変えない | `registration.userMenuItems?.[0].action()` を、項目を変数に取り、`action` が無ければ例外を出してから呼ぶ形にした。確かめる期待（ログアウトされ、トークンが消える）は変えていない | 機能設計 9.1 の union の型で、この行が型検査（TS2722）に落ちた。テストに触れずに済む型の形は無かった。Step 9 で (f) として止めて諮り、依頼者が **A**（型の絞り込みの形にだけ直す）に決めた |
| 2 | トップバーのアバターのコントラスト | 計画 Step 16: 既知の違反は green・orange の primary の Button だけ | 080 に `AVATAR_KNOWN_COMBOS`（blue・green・orange の light と dark、purple の dark）と `withAvatarKnownViolation` を足した。名前は `color-contrast avatar` に限る。組の外のアバター・アバターの外の違反・当たるはずの組で当たらないことは失敗にする | make-you-chic-ui の Avatar の文字 `--color-primary` と背景 `--color-primary-subtle` が、例えば blue の light で 4.36:1 だった。頭文字が2文字（2語の氏名）のときに当たる（1文字は axe が判定できない扱い）。アプリのどの画面のトップバーでも起き、purple light では当たらない。Step 16 で (b) として止めて諮り、依頼者が **B**（既知の制約として受け入れる。見本の氏名 `検査 太郎` は変えない）に決めた。make-you-chic-ui が直ったら外す |
| 3 | dark の FormField の誤りの文字のコントラスト | 同上 | 080 に `formFieldErrorTargets`・`withFormFieldErrorKnownViolation` を足した。<br>・既知にするのは、dark の組で、状態が `preferences-invalid`・`password-invalid` のときの `.mycui-form-field-error-text` の要素の `color-contrast` だけ（誤りの文字は `data-testid` を持たず `id` が一定でないため、検査の直前に要素の選択子を集めて名前の代わりにする）。<br>・範囲の外・light の組・当たるはずの誤りの文字で当たらないことは失敗にする | dark の `--color-danger` `#dc2626` を背景 `#0b0f19` に描いて 3.96:1（11.7px）だった。ログイン・登録の完了（U6）・U7 のすべての画面の項目の誤りに当たる。U7 の `.preferences-choice-error` も同じ色。Step 16 で (b) として止めて諮り、依頼者が **A**（既知の制約として受け入れる）に決めた。050〜070 は変えない。make-you-chic-ui が直ったら外す |
| 4 | API の失敗の振り分けの置き場 | 部品 2節のモジュールの一覧に無い | `failure.ts`（`isApiResponseError`・`badRequestCode`）を足した | 2つのフックが同じ振り分けを使うため。純粋な関数 |
| 5 | フォーカスの行き先の要素の参照 | 部品 3.1・4節: フックが要素の参照を持ち、部品に渡す | 参照は部品の中に持ち、フックはフォーカスの要求（先と回の `FocusRequest`）だけを返す。部品が描画の確定の後に移す。「もう一度読み込む」は `retryFocusSeq` で受ける | 参照を props で渡すと、リンタの `react-hooks/refs` で止まるため。U6 の `focusTarget` と同じ考え方で、動きは設計のとおり |
| 6 | 画面とフックのテストのログイン状態 | unit-test-instructions.md 5節: `fakeProvider`（ログイン中、氏名つき） | `renderPreferencesApp` は auth の本物の提供元と認証の状態を使い、トークンの更新（`POST /api/auth/session/refresh`）の答えは偽のサーバーが返す | 次の3つを、本物の道で確かめるため。<br>・今のパスワードの誤りでトークンの更新が送られず、ログイン状態が変わらないこと（NFR9.3）<br>・見せ方の最中のログイン状態の更新（5節）<br>・未ログインでログインの画面へ移ること<br>9節の決定 2（ログイン状態は本物）の読み方で、差し替えは `fetch` だけ |
| 7 | U4 の口の呼ばれ方を見るテスト | 機能設計 8節: 口を差し替える | `PreferencesPage.test.tsx` で `useDisplaySettings` を `vi.mock` で包み、`applyUserPreferences` の呼び出しを記録してから本物へ渡す | そろえ（D2）で呼ばれないこと、保存の後と離れた後の 200 の値で呼ばれることを、本物の画面の値と一緒に確かめるため |
| 8 | `ShellLayout` のメニューを開いた状態の vitest-axe | 計画 Step 10: vitest-axe（`path` の項目を含むメニューを開いた状態） | WCAG 2.0・2.1 の A・AA のタグ（080 と同じ）で確かめた | make-you-chic-ui がメニューを body の直下（ランドマークの外）に描き、best-practice の `region` の規則に当たるため。WCAG の規則の違反は 0 件 |
| 9 | 氏名の内側の改行の画面のテスト | 計画 Step 14: 内側の改行 | 画面のテストはタブとゼロ幅の空白で確かめ、改行は `formChecks.test.ts` で確かめた | 1行の入力欄（`input`）は値の改行を取り除くため、画面から改行を入れられない |
| 10 | E2E の手伝い | 計画 Step 15 の `registeredUser.ts` の4つ | `openUserMenuItem(page, name)` を足した（開き口は `dropdown-trigger` で探し、氏名・メールアドレスを使わない） | 組のテストと測りで同じ操作を使い、題や失敗の知らせに氏名を載せないため |
| 11 | 080 の測りの時間の上限 | 計画に無い | `test.setTimeout(240_000)` を足した | 1回目は既定の 30 秒で時間切れになった。流し直すと約 58 秒で通った |
| 12 | 画面部品のテストの件数 | 計画 7節の目安 | `formChecks` 9（目安 7）・`fieldErrors` 8（6＋2）・`PreferencesForm` 10（8）・`PreferencesLoadFailure` 5（4）・`PreferencesPage` 23（22〜28）・`PasswordChangePage` 17（12〜16）・`validateRegistrations` ＋5（＋6） | 目安の前後で、減らした確かめは無い（`validateRegistrations` の6件目の「ホームの `path`」は1件目に含めた） |
| 13 | 文言の数 | 機能設計 7節の表 | 40 の鍵（表のとおり） | — |

## 7. 計画の9節の決定と、生成の途中の依頼者の決定の反映

| 決定 | 反映 |
|---|---|
| 1（080 の組の当て方） | `GET /api/me/preferences`（`preferencesSample`）と `/api/appearance`（`prepareCombo`）の差し替えだけで当て、`loginPreferences.ts` は使わない。README の 060 の節の文を直した |
| 2（`fetch` の差し替え、本物の ApiClient・U4・ログイン状態） | 6節の #6・#7 のとおり |
| 3（`registeredUser.ts`） | 1.3 のとおり。U5 の `invitationSeed.ts`、U6 の `mailpit.ts` と `registrationToken.ts` を使い、既存の手伝いは変えていない |
| 4（見せ方の最中の更新は記録だけ） | 5節のとおり |
| 5（止めて諮る場面） | 3回止めた。<br>・Step 9 の (f): auth のテストの1行（→ A）<br>・Step 16 の (b): アバター（→ B）<br>・Step 16 の (b): dark の誤りの文字（→ A）<br>時間はどれも目標以内だった |
| 6（作業の場と統合の単位） | `feature/260925-user-management-b5-u7` を作った。コミットは依頼者の承認の後（11節）。U7 の統合で B5 の3つの単位（U5・U6・U7）がそろう |

## 8. 既知の違反の一覧に足した名前

- `PREFERENCES_KNOWN_VIOLATIONS`:
  - `preferences-ready`・`preferences-invalid`: `['preferences-save-button']`
  - `password-ready`・`password-invalid`: `['preferences-password-submit-button']`
  - どちらも 080 の実測で確かめ、計画の見込みのとおりだった。
- `AVATAR_KNOWN_COMBOS`: `blue`・`green`・`orange` が `['light', 'dark']`、`purple` が `['dark']`（6節の #2）。
- FormField の誤りの文字: dark の組の `preferences-invalid`（1件）・`password-invalid`（3件）（6節の #3）。

## 9. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」の表のとおりです。この段の値は 3〜5節にあります。

- カバレッジと verify の時間の実測のし直し（3.1 の値と比べる）。
- 080 の測りの値（4節）。
  - E2E の WAR（PC の上、空の内部DB）と、配備したアプリ（コンテナ、使い続けた内部DB）の違いを明記する。
  - 運用の中の判定は `Unverified`（observability-setup・feedback-optimization）。
- 080 の 20 組 × 2 画面 × 2 状態の結果と既知の違反（8節）、CSP、json の確かめ、e2eTest の時間の内訳（4節）。
- 自分の設定の API の時間（k6）は performance-validation、無ければ Build and Test（`project.md` の学び）。
- 見せ方の最中のログイン状態の更新（5節）を、残る危険・既知の動きの記録に写す。
- 既知の制約の2つ（アバター・dark の誤りの文字）を、残る危険の記録に写す。make-you-chic-ui 側の直しの相談は依頼者が行う。
- B5 の完了の条件として、U5・U6・U7 の3つの画面がそろったことを記録する（画面部品ごとの vitest-axe、E2E-1 の 090、ja・en の文言）。

## 10. 依頼者に確かめたいこと

1. **不安定なテストの疑い**: 変更の前の verify で、既存の `DisplaySettingsProvider.test.tsx` の1件が1回だけ落ちました（3.2）。原因は確かめていません。team.md の「不安定なテストは原因を直すまで統合しない」に当たるかを、統合の前に確かめたいです。再現できなければ不安定と確かめられていない扱いとする前例があります（`project.md` の学び、260925-storage-memory-fixes の F3）。
2. **初回の JavaScript**: 121.9 KB から 123.1 KB に増え、Vite が共有の塊の分け方を変えました（3.4）。上限は置かない決まりで、警告はありません。このままでよいかを確かめたいです。
3. **既知の制約の2つ**（6節の #2・#3）: make-you-chic-ui のリポジトリ側で直すかどうかと、その時期を確かめたいです。直ったら `AVATAR_KNOWN_COMBOS` と FormField の誤りの文字の扱いを外し、README を見直します。

## 11. コミットの区切りの案（依頼者の承認を得てから行う）

生成の担当はコミットしていません。計画 3節の C1〜C6 の区切りで、次の案を示します（`develop` への統合は squash）。

| 区切り | 中身 | メッセージの案 |
|---|---|---|
| C1 | `frontend/src/app/registry/types.ts`・`validateRegistrations.ts`・`validateRegistrations.test.ts`、`frontend/src/app/layout/ShellLayout.tsx`・`ShellLayout.test.tsx`、`frontend/src/app/navigation/navigationItems.test.ts`、`frontend/src/features/auth/registration.test.ts`（型の絞り込みの1行） | B5 U7 ユーザーメニューの項目で画面へ移る骨組み（path の項目、登録の検査、読み込み直しなしの移り） |
| C2 | `frontend/src/features/preferences/` のテスト以外（`registration.ts`・`messages.ts`・`preferencesApi.ts`・`fieldErrors.ts`・`errorMessages.ts`・`formChecks.ts`・`failure.ts`・2つのフック・5つの部品・2つの CSS） | B5 U7 プリファレンスとパスワードの変更の画面（氏名と表示の設定の保存・選んだ時点の見せ方・開いた時点のそろえ・パスワードの変更） |
| C3 | `frontend/src/features/preferences/` の `*.test.ts(x)` と `testing/` | B5 U7 プリファレンスとパスワードの変更の画面のテスト（項目ごとの誤りの読み取り・画面の確かめ・API・画面部品のアクセシビリティ・画面の流れと漏えい） |
| C4 | `frontend/e2e/080-preferences-accessibility.e2e.ts`・`support/preferencesFixtures.ts`・`support/registeredUser.ts`・`support/axe.ts` | B5 U7 実際のブラウザの検査と画面の時間（080）、招待から利用者を作る手伝い、既知の制約の扱い |
| C5 | `README.md`・`frontend/src/features/README.md` | B5 U7 の文書（プリファレンスとパスワードの変更の画面、080、既知の制約、ユーザーメニューの path） |
| C6 | 記録の `construction/u7-preferences-ui/code-generation/` の下（計画・質問・手順書・この要約・`source-manifest.json`・`traceability.json`） | U7 のコード生成の記録を追加（計画の実施・止めた場面と依頼者の決定・実測・080 の結果） |

- 各コミットのメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付けます。
- `develop` への squash の統合コミットの件名の案: 「B5 U7 プリファレンスとパスワードの変更の画面（氏名と表示の設定の保存・選んだ時点の見せ方・パスワードの変更、ユーザーメニューから画面へ移る骨組み、実際のブラウザの検査と画面の時間）」
