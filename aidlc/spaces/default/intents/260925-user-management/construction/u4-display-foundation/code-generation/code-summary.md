# Code Summary — U4 表示の設定の土台（u4-display-foundation）

Bolt B4 の残りとして、U4 のコード生成を承認済みの計画（`code-generation-plan.md`）の Step 1〜24 のとおりに行った。作業のブランチは `feature/260925-user-management-b4`（`develop` の先頭 `e2d1005` から作成）。コミットはしていない（区切りの案は 9節）。パスはリポジトリのルートからの相対パス。

Step 19 では、050 の検査で green・orange の組の `color-contrast` の違反が出た。計画の9節の決定 4 の (g) に当たるため、一度生成を止めて依頼者に諮った。依頼者の4つの決定（6節）を反映してから、Step 19 から先を続けた。

## 1. 作ったもの・手を入れたもの

| 置き場 | 中身 |
|---|---|
| `frontend/src/app/display-settings/` | 型と許される値の一覧（`displaySettingsTypes.ts`、`LANGUAGE_NAMES`）、純粋な関数（`resolveDisplaySettings.ts`: `parseStoredDisplaySettings`・`resolveTheme`・`validateUserDisplaySettings`・`decideScreenSettings`・`parseAppearance`・`isDisplayLanguage`）、ブラウザの保存（`browserStorage.ts`）、見た目の設定の読み取り（`appearanceLoad.ts`: `startAppearanceLoad`・`resolvedAppearance`・`peekAppearance`）、OS の配色（`colorScheme.ts`）、置き場（`displaySettingsStore.ts`: `saveBrowserDisplaySettings`・`saveBrowserLanguage`・`resetDisplaySettings` ほか）、`DisplaySettingsProvider.tsx`（`useDisplaySettings`）、テストの支え `testing/fakeColorScheme.ts` |
| `frontend/src/app/login-handoff/loginHandoff.ts` | `handOffToLogin`・`takeLoginHandoff`・`clearLoginHandoff`・`resetLoginHandoff` |
| `frontend/src/app/` の既存 | `App.tsx`（並びを ThemeProvider → LoginStateGate → DisplaySettingsProvider → I18nProvider → FeatureRegistryProvider に。起動の誤りの画面も同じ並び）、`I18nProvider.tsx`（言語を必須の props で受ける）、`registry/types.ts`（`LoginState.preferences`）、`LoginStateGate.tsx`、`LoginLayout`（`topRight`）、`ShellLayout.tsx`（氏名）、文言 `display.theme.*`・`display.fontSize.*`、`testing/renderWithProviders.tsx` |
| `frontend/src/main.tsx` | Noto Serif JP の8つの CSS。描画の前に写しの鍵の書き直し・要求の言語の関数の登録・`startAppearanceLoad()` |
| `frontend/src/shared/api-client/apiClient.ts` | `TOKENLESS_API_PATHS`・`isTokenlessApiPath`（`AUTH_API_PATHS`・`isAuthApiPath` を置き換えて消した）、`registerLanguageResolver` と `Accept-Language` |
| `frontend/src/features/auth/` | `CurrentUser` の4項目、`toLoginState`（氏名と `preferences`）、`LoginLanguageSwitch`（と CSS）、`LoginPage`、`LoginForm`（受け渡しの案内とメールアドレス）、文言 `auth.login.registered`・`auth.language.label` |
| `frontend/e2e/050-display-accessibility.e2e.ts`・`frontend/e2e/support/` | 20 組の axe と横のはみ出し、最初の画面の時間の測定。手伝いは `appearanceFixture.ts`・`displayCombos.ts`・`axe.ts`（`KNOWN_VIOLATIONS` を含む）・`overflow.ts`・`pageProblems.ts` |
| `frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`frontend/tsconfig.json` | 報告に json を追加。仮の資格情報は `webServer.env` からプロセスの環境変数へ移し、json に含まれないことを確かめる報告の部品を追加（6節の決定 2）。tsconfig に報告の部品を追加 |
| `frontend/package.json`・`package-lock.json` | `@fontsource/noto-serif-jp` 5.3.0（dependencies）、`axe-core` 4.13.0（devDependencies） |
| `vendor/make-you-chic-ui` | 固定先の更新だけ（中身は変えていない） |
| `build.gradle.kts` | `frontendBuild` の入力に make-you-chic-ui の dist を追加（6節の決定 4） |
| `backend/src/test/java/cherry/mastersmith/invitation/web/InvitationSendFailureIT.java` | 確かめ方の直し（6節の決定 3） |
| `README.md`・`frontend/src/features/README.md` | 「画面の表示の設定（U4）」の節（既知の制約・契約との差を含む）、E2E の表の 050 の行と説明、U8 の節の既知の制約の一文、差し込み口の表、ライセンスの表、固定先 |

テストの件数の内訳（フロントエンドで足したもの）は次のとおり。

- 新しいファイル: `resolveDisplaySettings` 17 件（fast-check の性質 4 件を含む）、`browserStorage` 7 件、`loginHandoff` 5 件、`appearanceLoad` 6 件、`colorScheme` 4 件、`displaySettingsStore` 6 件、`DisplaySettingsProvider` 15 件、`LoginLanguageSwitch` 6 件
- 既存のファイルへの追加: `apiClient` 8 件、`LoginStateGate` 3 件、`loginStateProvider` 2 件、`LoginForm` 5 件、`LoginLayout` 1 件、`LoginPage` 1 件、`ShellLayout` 3 件、`registration` 1 件、`App` 1 件
- `I18nProvider` のテストは言語を props で渡す形に書き直した

## 2. 実測（Step 1 と Step 24）

`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` で測った。

| 項目 | Step 1（変更の前） | Step 24（変更の後） |
|---|---|---|
| 結果・時間 | 1回目 FAILED（5 分 19 秒、6節の決定 3 の不安定なテスト）、2回目 成功（5 分 41 秒） | 成功（5 分 44 秒）。B3 の後の 5 分 43 秒とほぼ同じ |
| バックエンドの単体 | 1234 件（失敗 0・飛ばし 0） | 1234 件（失敗 0・飛ばし 0） |
| バックエンドの結合 | 562 件（失敗 0・飛ばし 0。対象DB のテストは SKIPPED でない） | 562 件（失敗 0・飛ばし 0） |
| フロントエンド | 47 ファイル 316 件 | 55 ファイル 408 件 |
| フロントエンドのカバレッジ | 行 97.86%（961/982）・分岐 93.6%（585/625） | 行 98.04%（1252/1277）・分岐 94.27%（757/803） |
| バックエンドのカバレッジ（JaCoCo の全体） | 行 98.78%・分岐 94.38% | 行 98.78%・分岐 94.38% |
| Gitleaks | — | 漏れなし（202 コミット） |
| OSV-Scanner | — | 実行された（UP-TO-DATE でない）。3 つの lockfile を検査し、失敗の条件 0 件・警告 0 件 |
| `vendorUnchanged` | 通る | 通る |

U4 の新しいファイルのカバレッジ（行・分岐）は次のとおり。

- `DisplaySettingsProvider.tsx`: 100%・97.43%
- `displaySettingsStore.ts`: 96.92%・88.46%
- `fakeColorScheme.ts`（テストの支え）: 84.21%・75%
- `appearanceLoad`・`browserStorage`・`colorScheme`・`resolveDisplaySettings`・`displaySettingsTypes`・`loginHandoff`・`LoginLanguageSwitch`: いずれも 100%・100%

計測の除外は増やしていない。

## 3. 固定先の更新（Step 2）

- **前後**: `vendor/make-you-chic-ui` を `edb1f943c0e66293494fa974605f34fcd7e258d7` から `735ef04ce6eb618cb875f5c4b31c1645a1f84c28` へ移した。含まれる 8 コミットは d61f2cb Modal・329bcc1 RadioGroup/Radio・a405dd1 Table・c9a7e2d Dropdown・e76dbf1 Button・2f8e05d Table・4d3df7e Table・735ef04 CatalogPage。
- **依存**: make-you-chic-ui の `package-lock.json` と `packages/make-you-chic-ui/package.json` に差は無い。`react`・`react-dom` の peerDependencies は `^19.0.0` のまま。`frontend/package-lock.json` はこの更新では変わらない。
- **U4 が使う API**: `src/theme` と Alert に差は無い。Button は `disabled={disabled}`・`aria-disabled={loading || disabled || undefined}` と onClick の包みの変更だけ。
- **ビルドと既存のテスト**: `vendorUnchanged frontendTypecheck frontendBuild` が通った。既存の画面のテストは 47 ファイル 316 件すべて通り、DSL の画面の `toBeDisabled()` も通ったため、C2 の直しは要らなかった。DSL の画面の `RadioGroup`（legend なし）も今までどおり通った。
- **分かったビルドの抜け**: この確かめで、`frontendBuild` が固定先を変えても UP-TO-DATE になることが分かった（入力に make-you-chic-ui の dist が無い）。`--rerun` で確かめたうえで、6節の決定 4 で直した。

## 4. 依存と R-02（Step 3）

- **`@fontsource/noto-serif-jp` 5.3.0**: OFL-1.1、推移依存なし。integrity は `sha512-7dRFleN7F+uuJdcMW/fJmnl6KLZSn9dHCjAVTaWjTcT4hg5V4ECbi2qgIamI6rS/+yTt16KDLU+9aJi74r6ZxQ==`。
- **`axe-core` 4.13.0**: MPL-2.0、推移依存なし、devDependencies。integrity は `sha512-UzGt8zg7Ny8djbYMhxl2zuEevVa7r2gJjYY5Lwr1xM7+XU2nd6CkIWFTVcCIbAP63vSz71NaVyyuSk9lHKcy0A==`。`npm ls axe-core` で 4.13.0 の1つにそろい、vitest-axe（`^4.4.2`）とも重なった。`config/npm-build-tools.txt` には足していない。
- **R-02**: Noto Serif JP の8つの CSS は、どれも `@font-face` が1つで、`font-display: swap` を持ち、`unicode-range` を持たない。

## 5. 画面の確かめの結果（Step 15・19・20・23）

### 5.1 make-you-chic-ui への反映の時点（Step 15）

make-you-chic-ui の ThemeProvider は、`<html>` の属性を描画の後（useEffect）で置く。そこで `DisplaySettingsProvider` の layout effect での属性の直接の置き方を外して流すと、「前の利用者の見た目が出ない」テストが落ちた。子の部品の effect が、ログインの後の最初の確定で `data-theme=dark` を見たため。

これを受けて、部品 3.3 が許した選択どおり、`DisplaySettingsProvider` が `setTheme`・`setFontSize` と同じ時点で `<html>` の `data-theme`・`data-font-size` も置く形にした。`data-brand`・`data-font-family` は make-you-chic-ui に任せた。

### 5.2 050 の結果（Step 19・23）

- **組ごとの成否**: 20 組すべて成功（Step 19 の2回目・3回目、Step 23 の3回とも同じ）。
  - (a) light・dark × sm・md・lg と、(c) 同じ6組の 375px は、想定外の違反 0 件・既知の違反 0 件。
  - (b) の blue・purple × light・dark は、想定外の違反 0 件・既知の違反 0 件。
  - (b) の green・orange × light・dark は、想定外の違反 0 件で、既知の違反 2 件（`color-contrast` の `login-language-switch-ja` と `login-form-submit-button`）。
  - どの組も `incomplete` は 0 件で、はみ出しも無い（375px の組の scrollWidth は 375）。
  - 1組あたりの時間は 241〜581 ミリ秒。
- **Step 19 の1回目（決定の前）**: 17 件成功、4 件失敗（green・orange の組の `color-contrast`）。
- **最初の画面の時間（ミリ秒、5 回）**: 1回目 117・110・105・104・111、2回目 115・107・104・104・103、3回目 114・103・102・105・104、Step 23 105・106・105・105・107。どの回も 5 回すべて 2 秒以内。
- **CSP**: CSP の見出しが付いた WAR の画面で、axe-core を `page.evaluate` で評価できた。CSP の違反は、測定の5回のどれでも 0 件。
- **Noto Serif JP**: `sans` のとき、フォントのファイルへの要求は 0 件。
- **本物の応答の照合**: 本物の `GET /api/appearance` の応答は、見本と項目の名前も型も一致した。
- **E2E の全件（Step 23）**: `./gradlew e2eTest` は既存の 6 件と 050 の 21 件で 27 件すべて通った（31 秒）。020・030・040 のユーザーメニューの名前の確かめも、変えずに通った。
- **json の結果**: 決定 2 の後は、報告の部品が「仮の資格情報は含まれていない（3 項目）」と確かめた。`e2e-admin@example.com` を探しても 0 件。

### 5.3 成果物の確かめと大きさ（Step 20）

- `backend/src/main/resources/application.yaml` の CSP に差は無い。
- `dist/index.html` の `<script>` は `src` つきの1つだけで、`<style>` は無い。
- 入口の CSS に Noto Serif JP の `@font-face` が8つあり、どれも `font-display:swap` で、URL は `/assets/noto-serif-jp-*`。
- `Deque Systems` を含む `dist` のファイルは 0 件。

| 値 | 変更の前 | 変更の後 |
|---|---|---|
| 初回の JavaScript（gzip） | 111.5 KB | 114.1 KB（目安の 500 KB の警告なし） |
| フォント noto-sans-jp | 9,788,560 B | 9,788,560 B |
| フォント noto-serif-jp | 0 B | 13,245,592 B（16 ファイル） |
| 入口の CSS（圧縮前 / gzip） | 22,694 B / 4,202 B | 25,518 B / 4,572 B |
| `frontend/dist` の全体 | 10,232,841 B | 23,495,427 B |
| WAR | 88,310,656 B | 101,566,201 B |

## 6. 依頼者の決定（Step 19 で諮ったもの）と反映

| 決定 | 内容 | 反映 |
|---|---|---|
| 1. コントラスト不足を許容する | green（3.30:1）・orange（3.56:1）の primary の Button（brand-500 の背景に白い文字）が WCAG AA の 4.5:1 に届かないことを、既知の制約として受け入れる | README の「画面の表示の設定（U4）」の「既知の制約」に比の表・原因・blue・purple を選ぶことを書き、U8 の節から参照した。050 では `frontend/e2e/support/axe.ts` の `KNOWN_VIOLATIONS` に、green・orange の組の `color-contrast` で、primary の Button で、名前（`login-language-switch-ja`・`login-form-submit-button`）が一致するものだけを既知の違反とした。ほかの規則・要素・組の違反は失敗にし、既知の違反が一覧と一致しない（消えた・増えた）ときも失敗にする |
| 2. json の仮の資格情報（A） | 仮の署名鍵・初期管理者のメールアドレス・仮のパスワードを `webServer.env` に置かない | `playwright.config.ts` の読み込みのときに、プロセスの環境変数に置く（WAR の起動は Playwright が `process.env` を引き継ぐ）。`frontend/playwright-secret-check-reporter.ts` を json の報告の後に並べ、3つの値が json にあれば実行を失敗にする（値は表示しない）。含む・含まない・ファイルが無いの3通りで報告の部品の動きを確かめた。以前の `frontend/test-results/e2e-results.json` は消した |
| 3. InvitationSendFailureIT を B4 で直す | 1回目の verify で、`output.getOut()` の全体に `"553"` が無いことの確かめが、無関係のログの無作為の番号（`127.0.0.1:55366`）に当たって落ちた | B1 の `MailSecretLeakIT` と同じ形にした。ロガー（`cherry.mastersmith.invitation`・`cherry.mastersmith.mail`・`org.springframework.mail`・`jakarta.mail`・`org.eclipse.angus`）を絞り、記録があること（`isNotEmpty`）と SMTP の応答の形（`5xx` と空白・ハイフン）が無いことを確かめる。単独で `--rerun` を5回続けて流し、5回とも通った（3 件ずつ）。**計画に無い追加** |
| 4. frontendBuild の入力の抜けを B4 で直す | 固定先を変えても `frontendBuild` が UP-TO-DATE になる | `inputs.dir(vendorDir.dir("packages/make-you-chic-ui/dist"))` を足した。確かめは、2回続けて UP-TO-DATE → 固定先を `edb1f94` に一時的に戻すと vendorBuild・frontendBuild が作り直される → `735ef04` に戻しても作り直される → その後は UP-TO-DATE。最後の固定先は `735ef04`。**計画に無い追加** |

## 7. 上流・計画との差

- **NFR7.1・NFR7.3（WCAG AA の目標）との差**: 6節の決定 1 で、ブランドカラーが green・orange のときの primary の Button のコントラスト不足（AA に届かない）を、依頼者の判断で既知の制約として受け入れた。NFR の目標そのものは緩めていない。
  - 050 の既知の違反は、名前と対象を指定したものだけに限る。
  - 違反の対象には既存のログインのボタンも含む。原因は make-you-chic-ui の primary の色で、`vendor/` は変えていない。
- **要求の言語の関数を描画の前にも登録**: `main.tsx` が描画の前に `installLanguageResolver()` を呼ぶ。`DisplaySettingsProvider` が描かれる前の最初のトークンの更新と `/api/appearance` にも、ブラウザの保存の値とブラウザの言語設定で決めた言語を付けるため。W12 の「画面を開いたときに登録する」の範囲の作り。
- **答え済みの約束の見分け**: `appearanceLoad.ts` に `resolvedAppearance`・`peekAppearance` を足した。答え済みの約束を渡すと、最初の描画から子を描ける。既存の画面のテストを同期で描けるようにするため（NFR9.10）。
- **`useDisplaySettings` は Provider の外で使うと例外を投げる**（すぐに失敗させる）。
- **050 のコンテキスト**: 組ごとのテストは、Playwright がテストごとに作る新しいコンテキスト（`page`）に `setViewportSize`・`addInitScript`・`page.route` を当てる。測定のテストは `browser.newContext` で5回作る。
- **型に伴う既存のテストの見本の直し**: `CurrentUser` に契約 C3 の4項目を足した型に合わせて、既存の auth のテスト（`authApi`・`authSession`・`LoginForm`・`registration`）の見本に4項目を足した。
  - 期待の変更は計画の8節の3つ（`I18nProvider.test.tsx`・`App.test.tsx`・`loginStateProvider.test.ts` の氏名）だけ。
  - `App.test.tsx` には起動の誤りの画面の表示の設定のテストを1件足した。
- **静的検査で直した2件（Step 21）**: oxlint の指摘 `unicorn(no-useless-spread)`・`no-unused-vars` を、コードの側で直した（決まりは緩めていない）。
- **README のフォーマット**: ルートの `README.md` は、変更の前から Prettier の形ではない（`frontend/` の検査の対象外）。今回もそのまま。
- **契約 C9 の差**（計画の9節の決定 3）: `resolvedTheme`・`displayName`・`LANGUAGE_NAMES`・`saveBrowserLanguage` を足し、置き場を `frontend/src/app/display-settings/` にした。`contract-summary.md` は書き換えず、README の「契約との差」に書いた。
- **報告の部品の置き場**: Playwright の決まりで default のエクスポートが要るため、構文の禁止を当てない `frontend/` の直下に置いた（設定のファイルと同じ扱い）。tsconfig の `include` に1行足した。

## 8. Build and Test に引き継ぐこと

- 計画の「Build and Test に引き継ぐこと」のとおり。
- 050 の値（組の成否・既知の違反・時間）は `frontend/test-results/e2e-results.json` から写す。
- 既知の違反（green・orange）は、make-you-chic-ui が直ったら `KNOWN_VIOLATIONS` と README を見直す。
- 最初の画面の時間の運用の判定は `Unverified`（observability-setup・feedback-optimization）。

## 9. コミットの区切りの案（依頼者の承認を得てから行う）

| 区切り | 中身 | メッセージの件名の案 |
|---|---|---|
| C1 | `vendor/make-you-chic-ui` の固定先だけ | make-you-chic-ui の固定先を 735ef04 に更新（edb1f94 → 735ef04） |
| C2 | `build.gradle.kts`（frontendBuild の入力） | frontendBuild の入力に make-you-chic-ui の dist を足し、固定先の更新で作り直すようにする |
| C3 | `InvitationSendFailureIT.java` | InvitationSendFailureIT の SMTP の応答の確かめをロガーと応答の形に絞る（無関係のログの番号に当たる不安定さを直す） |
| C4 | `frontend/package.json`・`package-lock.json` | Noto Serif JP と axe-core を依存に足す |
| C5 | 本番のコード（`frontend/src` のテスト以外、文言） | B4 U4 表示の設定の土台（言語・テーマ・文字の大きさ・見た目の設定、要求の言語、ログインの画面の言語の切り替えと受け渡し、氏名） |
| C6 | テスト（`*.test.ts(x)`・`renderWithProviders.tsx`・`testing/fakeColorScheme.ts`） | B4 U4 のテスト |
| C7 | 050・`frontend/e2e/support/`・`playwright.config.ts`・`playwright-secret-check-reporter.ts`・`tsconfig.json` | B4 U4 の実際のブラウザの検査（050、既知の違反、json の仮の資格情報の確かめ） |
| C8 | `README.md`・`frontend/src/features/README.md` | B4 U4 の文書（表示の設定、既知のコントラストの制約、050、ライセンス） |
| C9 | 記録（`construction/u4-display-foundation/code-generation/` の下） | U4 のコード生成の記録 |

- 計画の C1〜C8 の区切りに、直しの専用の C2・C3 を足したため、全体で C1〜C9 になった。
- 計画の C2（固定先の更新に伴う既存のテストの直し）は不要だったため作らない。
- どのメッセージも末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。
