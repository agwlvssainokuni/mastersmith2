# Code Generation Plan — U4 表示の設定の土台（u4-display-foundation）

U4 のコード生成の計画を示す。作るものは、画面の表示の設定（言語・テーマの選択・文字の大きさ・インスタンスの見た目）をすべての画面に当てる土台（新しい置き場 `frontend/src/app/display-settings/`・`frontend/src/app/login-handoff/`、契約 C9 の口）、ApiClient の広げ（トークンを付けない公開の API のパスの1つの一覧と判定、要求の言語 `Accept-Language`）、AuthUi の広げ（契約 C3 の受け、ログインの画面の言語の切り替え、登録の完了からの案内とメールアドレスの持ち越し）、ユーザーメニューの氏名、Noto Serif JP の配信、実際のブラウザのアクセシビリティの検査（`frontend/e2e/050-display-accessibility.e2e.ts`）と、make-you-chic-ui の固定先の更新（`edb1f94` → `735ef04`）である。Bolt は B4 の残り（`inception/delivery-planning/bolt-plan.md`）。B4 のもう1つの単位 U8（インスタンスの見た目の設定）は依頼者の判断で先に独立して作り、`develop` に統合済み。B1（U1）・B2（U2）・U8・B3（U3）は `develop` に統合済みで、先頭は `e2d1005`。U4 は ui の単位で、サーバーのコード・内部DB・Flyway を変えない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）とする。既存のコードの説明文にある「U1〜U4」「U2・U3」は前の Intent の単位番号のことがあり、この計画の U4 は u4-display-foundation を指す。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u4-display-foundation/functional-design/functional-spec.md`・`frontend-components.md`・`functional-design-questions.md`・`traceability.json` | 決まり D1〜D14、画面の流れ W1〜W12、状態の移り変わり（Booting・Guest・User と見せ方）、ブラウザの保存の鍵（4節）、失敗の場合（7節）、文言（8節）、Noto Serif JP・Noto Sans JP の採用の記録（9節）、承認の場の Request Changes の直し（12節の1〜5）、部品の階層と props・state（部品 1〜5節）、API との受け渡し（部品 6節）、テストで確かめる内容（部品 7節）、既存への影響（部品 8節）、答え Q1 A・Q2 A・Q3 A・Q4 A、網羅の OK 16 件・Deferred 36 件 |
| `construction/u4-display-foundation/nfr-requirements/` の全文書と `nfr-requirements-questions.md` | NFR2.1・NFR2.2、NFR6.1〜NFR6.5、NFR7.1〜NFR7.5、NFR8.1・NFR8.2、NFR9.1〜NFR9.11、答え Q1 B（最初の画面まで 2 秒、Build and Test で記録し関門にしない）・Q2 B（Playwright＋axe-core の検査、axe-core を devDependencies に明示）、axe-core の採用の記録（`tech-stack-decisions.md` 2.1） |
| `construction/u4-display-foundation/nfr-design/` の全文書と `nfr-design-questions.md` | 部品の一覧と関係（`logical-components.md` 1〜3節）、最初の描画のゲートと読み取りの約束を描画の外で1回だけ作ること（`performance-design.md` 2節）、時間の測り方（同 3節）、フォント（同 5節）、配信物の大きさ（同 6節）、ブラウザの保存・受け渡し・トークンを付けないパス・要求の言語・見た目の値・CSP・axe-core の読み込み方・依存（`security-design.md` 1〜7節）、050 の置き場・組・切り替え方・はみ出し・対象の画面（`logical-components.md` 5節）、答え Q1 A（ログインの画面を 375px の6組に入れる）・Q2 A（WCAG 2.0・2.1 の A・AA のタグ）・Q3 A（1つの一覧と1つの判定、名前を改める） |
| `construction/u4-display-foundation/infrastructure-design/` の全文書と `infrastructure-design-questions.md` | 実行の形・配信とキャッシュ・CSP を変えない（`infrastructure-specification.md`）、`./gradlew verify` の段と関門・依存とサブモジュール・050 の実行の前提（Mailpit）・reporter の json・結果のファイルの扱い・配備と戻し（`cicd-pipeline.md`）、SLI と `Unverified` の引き継ぎ（`monitoring-design.md`）、答え Q1 A（画面の時間は `e2eTest` の中だけで測る）・Q2 A（reporter に `json` を足す） |
| `inception/contract-design/contract-summary.md` の C3・C6・C7・C9 と共通の決まり | ログインと更新の応答の `user` の4項目、公開の API（`/api/registration/verify`・`/api/registration/complete`・`/api/appearance`）、見た目の設定の2項目、表示の設定の口の形、要求の言語（ADR-005） |
| `inception/domain-design/components.md`・`decisions.md` | AppFrame・ApiClient・AuthUi の持ち物、ADR-005（要求の言語）・ADR-006（見た目の設定を API で読む）・ADR-007（表示の設定を AppFrame に足す） |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U4 の責務と境界、US1.1・US3.2・US4.1 に関わる、CR1・CR2・CR6 の土台 |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md`・`inception/refined-mockups/`（`mockups.md` の S2・S3、`interaction-spec.md` の 6節・9節、`design-system-mapping.md`） | FR4.3・FR5.3〜FR5.5・FR7・FR8、NFR2・NFR6〜NFR9、AC3.2.17・AC3.2.18・AC4.1.2〜AC4.1.6・AC4.1.8・AC4.1.9・CR1.1〜CR1.5・CR2・CR6.6、ログインの画面の言語の切り替えの見た目と動き |
| `inception/delivery-planning/bolt-plan.md` の B4 | 完了の条件（C9 の部品のテスト、氏名、言語の切り替え、既存の画面のテストと E2E が通る）、見せ方、統合の形（B4 は固定先の更新を含むため fast-forward でよい、`team.md` の Way of Working） |
| B1・B2・B3・U8 の `code-summary.md`（`construction/u1-mail/`・`u2-user-preferences/`・`u3-invitation/`・`u8-instance-appearance/` の `code-generation/`） | U2 の `CurrentUserResponse` の4項目（初期管理者の氏名はメールアドレス・言語 ja・テーマ system）、U3 の公開の2つの道、U8 の `GET /api/appearance`（トークンなしで 200、使えないトークン付きは 401）と引き継ぎ「U4 との突き合わせ」、B3 の Step 30 のフロントエンドの実測（47 ファイル 316 件、行 97.86%・分岐 93.6%）、`frontend/playwright.config.ts` の今の形（ベース URL・`-Dh2.compactThreads=1`） |
| 既存のコード | `frontend/src/app/`（`App.tsx`・`i18n/`・`layout/`・`login-state/`・`registry/types.ts`・`testing/renderWithProviders.tsx`）、`frontend/src/shared/api-client/apiClient.ts`、`frontend/src/features/auth/`、`frontend/src/main.tsx`、`frontend/e2e/010〜040`、`frontend/playwright.config.ts`・`vitest.config.ts`・`vitest.setup.ts`・`vite.config.ts`・`package.json`・`tsconfig.json`・`scripts/check-bundle-size.mjs`・`scripts/check-license-header.mjs`、`build.gradle.kts`（`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`frontendBundleSize`・`osvScan`・`e2eTest`）、`vendor/make-you-chic-ui` の `edb1f94..735ef04` の差（8 コミット）と `docs/integration-guide.md`、`frontend/node_modules/@fontsource/noto-sans-jp`（5.3.0、CSS に `unicode-range` なし） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログから洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Functional Design・NFR Requirements・NFR Design・Infrastructure Design の `DECISION_RECORDED`・`GATE_APPROVED`・`GATE_REJECTED` と、レビューの記録（`.aidlc-reviews/*/units/u4-display-foundation/*/1.json`）の指摘を読んだ。U4 と B4 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes、2026-09-27、U4 R-01 Major） | 登録の完了の API（`/api/registration/verify`・`/api/registration/complete`）をトークンを付けない公開のパスに足す | ApiClient の1つの一覧に、認証の API の3つと公開の API の3つを並べる（D10、NFR9.1）。アクセストークンを持つ状態で3つの公開のパスに `Authorization` が付かず、401 でも更新しないことを単体テストで確かめる | Step 10・11 |
| 機能設計（Request Changes、U4 R-02 Minor） | 見た目の設定の応答が返らない（ハング）場合と Q2 A の影響範囲の明記 | 設計どおり待ちに上限・再試行・中断を置かない。README の U4 の節に影響範囲（1つの API の障害が全画面の最初の描画を止めうる）を書く | Step 14・22 |
| 機能設計（Request Changes） | Noto Sans JP の採用の理由の記録、make-you-chic-ui の新しい部品（RadioGroup の legend・lang、Table の labels、Modal の closeOnBackdropClick・alertdialog、Dropdown の href、Button の aria-disabled）への置き換え | 採用の記録は機能設計の 9.3 で済み。新しい部品の置き換えは U5〜U7 の画面（B5）の話で、B4 はその前提になる固定先の更新（`edb1f94` → `735ef04`）を行う。U4 の `LoginLanguageSwitch` は設計どおり Button の組（RadioGroup にしない、機能設計の 12節の5） | Step 2・16 |
| 機能設計の memory（B4 で確かめるとされた点） | Button の loading の変更（`disabled` から `aria-disabled` へ）が既存の画面のテスト（`disabled` を確かめるもの）に及ぶかを、固定先を更新する B4 で確かめる | Step 2 で既存の画面のテストを全件流して確かめる。DSL の画面の5つのボタンが `loading` を使い、テストに `toBeDisabled()` がある（どのボタンも `disabled={busy}` を併せ持つため通る見込みだが、確かめる）。落ちたときの扱いは9節の決定 2 | Step 2 |
| NFR 要件（承認の場、Minor 12 件の拾い上げ） | U4 の検査の置き場と既存の E2E への非干渉（R-01）を計画に書く | 050 は既存の 010〜040 の後の番号の新しいファイルで、組ごとに新しいブラウザのコンテキストを作り、ログインせず、`page.route` はそのページの中だけ。既存の 4 つのファイルは変えない（`logical-components.md` 5.1） | Step 18・19・23 |
| NFR 要件のレビュー（U4 R-02 Minor、Accepted risk） | ログインの画面の狭い幅（768px 未満）の確かめ方を B4 の計画に書き、B5 で自動の検査に含めるかを残す | NFR 設計の Q1 A で閉じた: B4 でログインの画面を幅 375px（高さ 812px）の6組（(c)）に入れ、横のはみ出しと axe の違反 0 件を確かめる。U5〜U7 の画面の 375px は B5 で足す（各画面の NFR 要件のとおり） | Step 18・19 |
| NFR 設計（承認の場、U4 R-01 Minor） | 見張りの順番は既存の E2E にならう: 5回の各回で「監視（`securitypolicyviolation` と要求の一覧の収集）を張る → `page.goto` → 測定 → 判定」 | 050 の測定のテストの各回をこの順に書き、既存の `010-skeleton.e2e.ts` の `collectProblems` と同じ見方の手伝いを `frontend/e2e/support/` に置く（010 は変えない） | Step 18・19 |
| NFR 設計（承認の場、U4 R-02 Minor） | B4 でフォントの CSS を確かめる: fontsource の CSS が `unicode-range` を持たない前提をコード生成で確かめ、崩れたときの方針を一言記録する | Step 3 で `@fontsource/noto-serif-jp` 5.3.0 の8つの CSS に `unicode-range` が無いことを確かめ、Step 19 で `sans` のときに Noto Serif JP のフォントのファイルへの要求が無いことを確かめる。**崩れたときの方針**: 確かめ（NFR6.4 の「`sans` で読まない」）は緩めない。生成をその手順で止め、設計のやり直しの候補（見た目の設定が `serif` のときだけ Noto Serif JP の CSS を読み込む形など）を示して依頼者に諮る（9節の決定 4 の (b)） | Step 3・19、9節 |
| NFR 設計（承認の場、U8 R-02） | B4 の最初に指標（`hikaricp.connections.acquire`）の可否を確かめる | U8 のコード生成で済み（U8 の `code-summary.md` 4節、指標で数える形）。U4 は画面の単位でサーバーのテストを持たないため、B4 の U4 には関わらない | — |
| NFR 設計（承認の場、上流との差 A5） | `common.security` の説明文とカバレッジの一覧の扱いは B3 か B4 の計画で確かめる | U8 で済み（`common.security`・`config`・`access.web` を `packagesJudgedByTotal` から外した）。U4 はバックエンドに手を入れない | — |
| U8 のコード生成（HEAD・末尾の `/` の扱い） | 実際の扱いを確かめて記録する | U8 で済み（U8 の `code-summary.md` 5節、HEAD・トークンなしは 401）。U4 は GET だけを呼ぶため関わらない | — |
| U8 の引き継ぎ（U8 の `code-summary.md` 8節「U4 との突き合わせ」） | 画面がトークンを付けずに呼び、C7 の2項目を使うこと | ApiClient の単体テストで `/api/appearance` に `Authorization` が付かないこと、見た目の設定の読み取りの単体テストで2項目だけを項目ごとに検証して使うことを確かめる。050 の測定のテストで本物の応答の項目の名前と型が差し替えの見本と一致することも確かめる（`project.md` の Corrections の「見本を1つにして本物の応答と照らす」） | Step 10・11・18・19 |
| 基盤の設計（承認の場、N2） | 050 だけでも Mailpit が要る（B4・B5） | 050 を流す手順（`unit-test-instructions.md`・README）に Mailpit の起動を書く。`./gradlew e2eTest` は Mailpit に届かなければ失敗する（U1 の仕組みのまま） | Step 19・22・23 |
| 基盤の設計（承認の場、N9） | json の結果に操作の題が入るか（B5） | B5 の持ち物。B4 では、json の報告に入るのが時間・規則の名前・件数・組の名前・テストの題だけで、秘密（トークン・パスワード・メールアドレス）が入らないことを確かめて記録する（`cicd-pipeline.md` 4.2） | Step 19、Build and Test に引き継ぐこと |
| 基盤の設計（Q1 A・Q2 A） | 画面の時間は `./gradlew e2eTest` の中だけで測る。reporter に `json` を足し、`frontend/test-results/` の下の1つのファイルに書く | `frontend/playwright.config.ts` の reporter に `['json', { outputFile: 'test-results/e2e-results.json' }]` を足す（既存の `list`・`html` は残す。`.gitignore` は変えない）。配備したアプリでは測らない | Step 18・19 |

コード生成の段の依頼で挙げられた「コード生成で拾うことの一覧」のうち、make-you-chic-ui の固定先の更新と Button の aria-disabled の DSL の画面のテストへの影響は Step 2、axe の `page.evaluate` と CSP は Step 19、Noto Serif JP は Step 3・14・19・20、hikaricp の指標と HEAD の扱いは U8 で済み（上の表）である。

### 2.2 この計画での読み方

- **設計の文書どおりに作るもの**: 部品の階層（部品 1節）、置き場ごとのモジュール（部品 2節）、口の形と決め方（部品 3節）、props と state（部品 4節）、AuthUi とログイン状態（部品 5節）、API との受け渡し（部品 6節）、最初の描画のゲート（`performance-design.md` 2節）、ブラウザの保存・受け渡し・トークンを付けないパス・要求の言語・見た目の値（`security-design.md` 1〜4節）、CSP を変えない（同 5節）、axe-core の読み込み方と合否の規則（同 6節）、依存（同 7節）、050 の置き場・組・切り替え方・はみ出し・対象（`logical-components.md` 5節）、時間の測り方（`performance-design.md` 3節）。
- **名前を決めるもの**（「コード生成で決める」とされたもの）:
  - トークンを付けないパスの一覧と判定は `TOKENLESS_API_PATHS`・`isTokenlessApiPath`（NFR 設計の仮の名前のまま。`AUTH_API_PATHS`・`isAuthApiPath` は `apiClient.ts` の中だけで使われているため、置き換えて消す）。
  - 要求の言語の関数の登録は `registerLanguageResolver`（部品 6.1 の名前）。テストの初期化は既存の `resetApiClient` で一緒に消す。
  - 置き場 `frontend/src/app/display-settings/` のモジュールは、型と定数 `displaySettingsTypes.ts`（`DisplayLanguage` は既存の `i18n/resolveLanguage.ts` の型を使う・`ThemeChoice`・`ResolvedTheme`・`FontSize`・`BrandColor`・`FontFamily`・`DisplaySettings`・`UserDisplaySettings`・`LANGUAGE_NAMES`、文字列リテラルの union と `as const` の一覧）、解き方の純粋な関数 `resolveDisplaySettings.ts`、ブラウザの保存 `browserStorage.ts`、見た目の設定の読み取り `appearanceLoad.ts`（`startAppearanceLoad`）、OS の配色の購読 `colorScheme.ts`、置き場（購読できる状態）`displaySettingsStore.ts`、`DisplaySettingsProvider.tsx`（`useDisplaySettings`）、口の関数の再公開 `index.ts` は作らない（`frontend-components.md` の置き場ごとのモジュールに合わせ、使う側は各モジュールを名前付きで読み込む）。受け渡しは `frontend/src/app/login-handoff/loginHandoff.ts`（`handOffToLogin`・`takeLoginHandoff`・`clearLoginHandoff`・テストの初期化 `resetLoginHandoff`）。
  - ログインの画面の言語の切り替えは `frontend/src/features/auth/LoginLanguageSwitch.tsx`（と同じ場所の CSS）。
- **表示の設定の置き場（購読できる状態）**: ブラウザの保存の値・見せ方（テーマ・文字の大きさ・言語と、置いたときのログイン状態の結び付け）・保存の後の利用者の設定（受けたときのログイン状態の結び付け）を、モジュールの中の1つの置き場に持つ（部品 3.2）。React からは `useSyncExternalStore` で購読し、テストの初期化 `resetDisplaySettings` を置く（既存の `resetAuthSession` と同じ形）。ログイン状態の結び付けは「`LoginStateGate` が新しいログイン状態の値を作るたびに変わる印」で表し、印が今のログイン状態と違う見せ方・保存の後の利用者の設定は捨てたものとして扱う（D2・D8）。
- **最初の描画の前の処理**: 画面の入口 `frontend/src/main.tsx` で、`createRoot` の前に写しの鍵の書き直し（`browserStorage` の関数、W1 の2・D5）と `startAppearanceLoad()`（描画の外で1回だけ、`performance-design.md` 2.1）を呼び、約束を `App` の props（任意、既定は `startAppearanceLoad()` の結果）で渡す。`App` はテストから答え済みの約束を渡せる。
- **make-you-chic-ui への反映の時点**: `DisplaySettingsProvider` は描画の確定の直後（`useLayoutEffect`）に、前に渡した値から変わったときだけ `setTheme`・`setFontSize` を呼び、`<html lang>` と要求の言語の関数が返す値を変える。ログインの後の最初の画面に前の利用者の見た目が一瞬出ることがテストで分かったときは、同じ時点で `<html>` の `data-theme`（`light` のときは消す、make-you-chic-ui の扱いに合わせる）・`data-font-size` も置く（部品 3.3 が許した選択）。どちらにしたかは Step 15 で確かめて `code-summary.md` に記録する。
- **`renderWithProviders` の広げ**: 既存の並びに make-you-chic-ui の `ThemeProvider` と `DisplaySettingsProvider` を足し、並びを `ThemeProvider` → `LoginStateGate` → `DisplaySettingsProvider` → `I18nProvider` → `FeatureRegistryProvider` にする。見た目の設定は既定で「読み終わった（当てる値なし）」の約束を渡し、選択で差し替えられるようにする。既存の画面のテストは `/api/appearance` の要求を用意せずに描ける（NFR9.10）。
- **既存の E2E は変えない**: 初期管理者の氏名はメールアドレス（`user/service/InitialAdminInitializer.java`）のため、既存の 020・030・040 の `getByRole('button', { name: adminEmail })` はユーザーメニューが氏名を出す形になっても変わらず通る見込み。Step 23 で確かめる。
- **サーバーの側は変えない**: `backend/` のコード・`application.yaml`（CSP を含む）・Flyway・`compose.yaml`・`Dockerfile`・`build.gradle.kts`・`.github/`・`config/npm-build-tools.txt`・`.gitignore` は変えない。`vendor/` の中身も変えない（固定先の更新だけ）。
- **既存の ArchUnit** はバックエンドの検査で、U4 は触れない。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: `develop` の先頭（`e2d1005`）から短命のブランチ `feature/260925-user-management-b4` を作る（`team.md` の Way of Working。9節の決定 1）。
- **統合の形**: B4 は make-you-chic-ui の固定先の更新を含むため、固定先の更新を専用のコミットとして残すよう、squash ではなく短命のブランチから `develop` へ **fast-forward** で統合する（`team.md` の Way of Working、NFR9.7）。統合の前に `./gradlew verify` を通し（Step 24）、画面・認証に関わる変更のため `./gradlew e2eTest` も通す（Step 23）。統合は依頼者の承認を得て行う。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、次の区切りでまとめてコミットする（`project.md` の Change Control の学び）。メッセージは日本語で、B4 の内容が分かる件名にする。fast-forward のため、どのコミットも `develop` にそのまま残る。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | make-you-chic-ui の固定先の更新（`vendor/make-you-chic-ui` の固定先だけ、`edb1f943c0e66293494fa974605f34fcd7e258d7` → `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`）。前後のハッシュと8つのコミットの題をメッセージに書く（`project.md` の Mandated） | Step 2 |
| C2 | 固定先の更新によって必要になった既存のテストの直し（9節の決定 2 に当たったときだけ。無ければこの区切りは作らない） | Step 2 |
| C3 | 依存の追加（`frontend/package.json`・`frontend/package-lock.json`: `@fontsource/noto-serif-jp`・`axe-core`） | Step 3 |
| C4 | 本番のコード（`frontend/src/app/display-settings/`・`login-handoff/`・`i18n/`・`layout/`・`login-state/`・`registry/types.ts`・`App.tsx`、`frontend/src/main.tsx`、`frontend/src/shared/api-client/apiClient.ts`、`frontend/src/features/auth/`）と文言 | Step 4・6・8・10・12・14・16 |
| C5 | テスト（`*.test.ts(x)`・テストの支え・`frontend/src/app/testing/renderWithProviders.tsx`。既存のテストの直しを含む） | Step 5・7・9・11・13・15・17 |
| C6 | 実際のブラウザの検査（`frontend/e2e/050-display-accessibility.e2e.ts`・`frontend/e2e/support/`・`frontend/playwright.config.ts` の reporter） | Step 18・19 |
| C7 | 文書（`README.md`・`frontend/src/features/README.md`） | Step 22 |
| C8 | この段の記録（記録の `construction/u4-display-foundation/code-generation/` の下） | Step 25 |

- C1 は固定先だけの専用のコミットにする。C1 だけの状態で画面のテストが落ちる場合があることは、依頼者が受け入れた（9節の決定 1）。
- `origin` への `git push` は依頼者が行う。AI はプッシュしない。

## 4. 作るもの・手を入れるもの

| 置き場 | 部品 | 新しい・手を入れる | 役割 |
|---|---|---|---|
| `app/display-settings/displaySettingsTypes.ts` | 型と許される値の一覧、`LANGUAGE_NAMES`（「日本語」「English」、訳さない） | 新しい | D1・D12、NFR8.2、C9 |
| `app/display-settings/resolveDisplaySettings.ts` | 純粋な関数: 保存の値の読み取りと検証（`parseStoredDisplaySettings`）、テーマの解き方（`resolveTheme`）、ログイン状態の表示の設定の検証（`validateUserDisplaySettings`）、当てている値と画面の値の決め方（`decideScreenSettings`）、見た目の設定の応答の検証（`parseAppearance`）、許される言語の判定（`isDisplayLanguage`） | 新しい | D1・D2・D4・D7・D8、W3・W5、NFR9.2・NFR9.3・NFR9.9 |
| `app/display-settings/browserStorage.ts` | U4 の鍵 `mastersmith.display-settings` の読み書き（3つをまとめて・言語だけ）、写しの鍵 `design-system-theme`・`design-system-font-size` の書き直し。例外を外へ出さない | 新しい | 4.1・4.2・D5〜D7、NFR2.2 |
| `app/display-settings/appearanceLoad.ts` | `startAppearanceLoad`（描画の外で1回だけ `GET /api/appearance`、例外で終わらない結果）、テストの初期化 | 新しい | W3・D11、NFR6.2・NFR9.3 |
| `app/display-settings/colorScheme.ts` | `prefers-color-scheme: dark` の今の値と変化の購読（`matchMedia` が無ければ light） | 新しい | D4・W11 |
| `app/display-settings/displaySettingsStore.ts` | 購読できる置き場、口の関数 `saveBrowserDisplaySettings`・`saveBrowserLanguage`、テストの初期化 `resetDisplaySettings` | 新しい | 部品 3.2・3.3、D6 |
| `app/display-settings/DisplaySettingsProvider.tsx` | ゲート（見た目の設定の答えまで子を描かない）、画面の値の決定、make-you-chic-ui・`<html lang>`・要求の言語への反映、ログイン状態が新しくなったときの保存、`useDisplaySettings`（`language`・`theme`・`fontSize`・`resolvedTheme`・`displayName`・`setPreview`・`clearPreview`・`applyUserPreferences`・`setLanguage`） | 新しい | 部品 3.1・3.3、W2〜W6・W10〜W12、C9 |
| `app/login-handoff/loginHandoff.ts` | 受け渡しの変数1つ（`{ email: string }`） | 新しい | D13・W9、NFR2.1 |
| `app/i18n/I18nProvider.tsx` | 言語を必須の props で受ける。ブラウザの言語設定を自分で読まず、`<html lang>` の副作用を `DisplaySettingsProvider` へ移す | 手を入れる | 部品 4節、D14 |
| `app/i18n/messages/ja.ts`・`en.ts` | `display.theme.*`・`display.fontSize.*` の6つ | 手を入れる | 8節、NFR8.1 |
| `app/registry/types.ts` | `LoginState` に任意の `preferences`（`language`・`theme`・`fontSize`）。`displayName` の説明を氏名に | 手を入れる | 部品 5節 |
| `app/login-state/LoginStateGate.tsx` | `normalizeLoginState` がログイン中なら `preferences` も通す | 手を入れる | 部品 4節 |
| `app/App.tsx` | 並びを `ThemeProvider`・`ToastProvider`・`ModalStackProvider` → `LoginStateGate` → `DisplaySettingsProvider` → `I18nProvider` → `FeatureRegistryProvider`・`AppRouter`（起動の誤りの画面も同じ並び）。見た目の設定の約束を props（任意）で受ける | 手を入れる | 部品 1節・4節、W1・W2 |
| `app/layout/LoginLayout.tsx`（と CSS） | 右上の置き場 `topRight`（任意の props） | 手を入れる | 部品 4節、`interaction-spec.md` 6節 |
| `app/layout/ShellLayout.tsx` | ユーザーメニューの名前を `useDisplaySettings().displayName`（無ければログイン状態の `displayName`） | 手を入れる | D8・W5・W10、AC4.1.8 |
| `main.tsx` | Noto Serif JP の8つの CSS（Noto Sans JP の後）、写しの鍵の書き直しと `startAppearanceLoad()` を `createRoot` の前に | 手を入れる | W1、9.1、NFR6.4 |
| `shared/api-client/apiClient.ts` | `TOKENLESS_API_PATHS`・`isTokenlessApiPath`、`registerLanguageResolver` と `Accept-Language` の付与 | 手を入れる | D9・D10・W12、NFR9.1・NFR9.2 |
| `features/auth/authApi.ts` | `CurrentUser` に `displayName`・`language`・`theme`・`fontSize` | 手を入れる | C3 |
| `features/auth/loginStateProvider.ts` | `toLoginState` が `displayName` に氏名、`preferences` に3つの値 | 手を入れる | 部品 5節、M7 |
| `features/auth/LoginLanguageSwitch.tsx`（と CSS） | 「日本語」「English」の Button の組（`lang`・`aria-pressed`、組の名前 `auth.language.label`）。選ぶと `setLanguage` と `saveBrowserLanguage` | 新しい | W7・D12、CR1.5・CR6.6、NFR7.4 |
| `features/auth/LoginPage.tsx` | `LoginLayout` の `topRight` に `LoginLanguageSwitch` | 手を入れる | W7 |
| `features/auth/LoginForm.tsx` | 最初の描画で受け渡しを1回読み、案内（`Alert` の成功の種類、`auth.login.registered`）とメールアドレスの初期値。描画の確定の後に受け渡しを消す | 手を入れる | W9、AC3.2.17 |
| `features/auth/registration.ts` | 文言 `auth.login.registered`・`auth.language.label`（ja・en） | 手を入れる | 8節、NFR8.1 |
| `app/testing/renderWithProviders.tsx` | `ThemeProvider` と `DisplaySettingsProvider`、見た目の設定の約束の差し替え | 手を入れる（テストの支え） | NFR9.10 |
| `frontend/e2e/050-display-accessibility.e2e.ts` | 20 組の axe と横のはみ出し、最初の画面の時間の測定（5回）、CSP の違反と Noto Serif JP の要求の確かめ、本物の見た目の設定の応答と見本の照合 | 新しい | NFR6.1・NFR6.4・NFR7.3・NFR7.5・NFR9.4・NFR9.11 |
| `frontend/e2e/support/`（`displayCombos.ts`・`axe.ts`・`overflow.ts`・`pageProblems.ts`・`appearanceFixture.ts`） | 組の一覧と切り替え、axe-core の読み込みと実行（`page.evaluate`）と規則の抜けの確かめ、はみ出しの判定、CSP の違反と要求の一覧の集め、見た目の設定の見本（画面の側の型を付ける） | 新しい | `logical-components.md` 5節、`security-design.md` 6節、`infrastructure-specification.md` 4節（B5 が使う共有の手伝い） |
| `frontend/playwright.config.ts` | reporter に `json`（`test-results/e2e-results.json`） | 手を入れる | 基盤の設計の Q2 A |
| `frontend/package.json`・`package-lock.json` | `@fontsource/noto-serif-jp`（`dependencies`）・`axe-core`（`devDependencies`） | 手を入れる | NFR9.5・NFR9.6 |
| `vendor/make-you-chic-ui` | 固定先 `edb1f94` → `735ef04`（中身は変えない） | 固定先の更新 | NFR9.7 |

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。U4 の層は「業務処理（解き方の純粋な関数）→ データアクセス（ブラウザの保存・受け渡し）→ API（ApiClient と見た目の設定の読み取り）→ AuthUi のログイン状態の受け渡し → 画面の振る舞い（表示の設定の土台 → 画面部品）→ 実際のブラウザの検査」の順とする。データアクセスが読み込む値の検証に純粋な関数を使うため、依存の順に業務処理を先に置く。U4 は内部DB を持たないため、データの形（DB）の層は無い。

### Step 1: 作業の場の用意と、変更の前の基準（依頼者が承認した git の操作。9節の決定 1）

- [x] `develop` の先頭のハッシュ（`e2d1005`）と `vendor/make-you-chic-ui` の今の固定先（`edb1f943c0e66293494fa974605f34fcd7e258d7`）を記録し、`develop` から短命のブランチ `feature/260925-user-management-b4` を作る（コミットはしない。統合は fast-forward）
- [x] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し（`caffeinate -i`）、テストの件数（バックエンドの単体・結合、フロントエンドのファイル数と件数、失敗・飛ばした）と、フロントエンドの全体のカバレッジ（行・分岐）を記録する（brownfield の Test Baseline、`project.md` の Testing Posture）
- [x] 同じ実行の成果物で、変更の前の値を記録する: `frontendBundleSize` の初回の JavaScript（gzip）、`frontend/dist/assets/` のフォントのファイルの合計（名前の `noto-sans-jp` ごと。`noto-serif-jp` は 0）、入口の CSS（`dist/.vite/manifest.json` の入口の `css`、圧縮前と gzip）、`frontend/dist/` の全体、`backend/build/libs/mastersmith.war` の大きさ（NFR6.3・NFR6.5、`performance-design.md` 6節。測る道具は足さず `du`・`stat`・Node の1回だけのコマンド）
- [x] 対応: B4 の共通の完了の条件、NFR6.3・NFR6.5（前の値）

### Step 2: make-you-chic-ui の固定先の更新と影響の確かめ（C1 の専用のコミットに分ける変更。9節の決定 1・2）

- [x] `git -C vendor/make-you-chic-ui checkout 735ef04ce6eb618cb875f5c4b31c1645a1f84c28` で固定先を移す（手元の履歴にあることは基盤の設計の段で確かめ済み。無ければ `git -C vendor/make-you-chic-ui fetch` を依頼者に確かめてから行う）。サブモジュールの中身は変えない（`project.md` の Forbidden）。前後のハッシュと8つのコミットの題（d61f2cb Modal・329bcc1 RadioGroup/Radio・a405dd1 Table・c9a7e2d Dropdown・e76dbf1 Button・2f8e05d Table・4d3df7e Table・735ef04 CatalogPage）を記録する
- [x] 更新した版の `vendor/make-you-chic-ui/package-lock.json` と `packages/make-you-chic-ui/package.json` が前後で変わらないこと（`git -C vendor/make-you-chic-ui diff --stat edb1f94 735ef04 -- package-lock.json packages/make-you-chic-ui/package.json` が空）と、`react`・`react-dom` の `peerDependencies`（`^19.0.0`）が同じことを記録する。変わらないため `frontend/package-lock.json` も変わらない見込みで、変わったときはその差を記録する
- [x] `./gradlew vendorUnchanged frontendTypecheck frontendBuild` を実行し、新しい固定先の `npm ci`・ビルド・サブモジュールの中身が変わっていないことの確かめ（`vendorUnchanged`）・型検査・画面のビルドが通ることを確かめる
- [x] U4 が使う make-you-chic-ui の API（`ThemeProvider`・`useTheme` の `setTheme`・`setFontSize`・`setBrand`・`setFontFamily`・`design-system-*` の鍵・`Alert`・`Button` が button の属性をそのまま通すこと）が前後で変わらないことを、差分（`src/theme/` に差が無い、`Alert` に差が無い、`Button` は `aria-disabled`・`onClick` の包みだけ）で確かめて記録する（機能設計の 12節の5、NFR9.7）
- [x] 既存の画面のテストを全件流す（`(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run)`、この Step に限り既存の全件。`unit-test-instructions.md` 2節）。特に DSL の画面の `loading` を使う5つのボタン（`dsl-preview-discard`・`dsl-preview-apply`・`dsl-read-schema-button`・`dsl-history-restore-*`・`dsl-submit-button`）の `toBeDisabled()` の確かめ（`DslPreviewPanel.test.tsx`・`DslAdminPage.test.tsx`・`DslStatusPanel.test.tsx`・`DslHistoryTable.test.tsx`・`DslSubmitForm.test.tsx`）が通るかを見る。どのボタンも `disabled={busy}` を併せ持つため通る見込み
  - **落ちたとき**: 9節の決定 2 のとおり扱う（Button の loading の変更だけが原因なら、確かめを「`aria-disabled="true"` で、押しても操作が呼ばれない」に置き換えて強さを保ち、C2 に入れる。画面のコードの変更が要る、またはほかの原因のときは、生成を止めて依頼者に諮る）
- [x] `RadioGroup` を `legend` なしで使う DSL の画面（`DslSubmitForm.tsx`）は、更新の後も今までどおり `role="radiogroup"` の形で描かれ、選択肢の名前が `<span>` に包まれるだけであること（`getByLabelText` などの既存の確かめが通ること）を確かめる。DSL の画面を新しい部品に置き換える作業はしない（範囲の外）
- [x] 対応: NFR9.7、機能設計の memory の「Button の loading の変更を B4 で確かめる」、`project.md` の Mandated（サブモジュールの固定先の更新）

### Step 3: 依存の追加（@fontsource/noto-serif-jp・axe-core）

- [x] `(cd frontend && npm install @fontsource/noto-serif-jp@5.3.0)` で実行時の依存に足す（`package.json` は既存の `@fontsource/noto-sans-jp` と同じ `^5.3.0` の書き方）。`(cd frontend && npm install --save-dev axe-core@4.13.0)` で開発時の依存に明示で足す（`^4.13.0`）。版は `frontend/package-lock.json` で固定する（`project.md` の Mandated）
- [x] 記録する: 足した版と lockfile の `resolved`・`integrity`、どちらも推移依存が無いこと、ライセンス（OFL-1.1・MPL-2.0。採用の記録は機能設計の 9.2 と NFR 要件の `tech-stack-decisions.md` 2.1）、`npm ls axe-core` で lockfile の axe-core が 4.13.0 の1つにそろい、vitest-axe が求める範囲（`^4.4.2`）の中であること（NFR9.5・NFR9.6）
- [x] **R-02 の確かめ**: `frontend/node_modules/@fontsource/noto-serif-jp/` の `japanese-400`〜`700`・`latin-400`〜`700` の8つの CSS が、太さごとに `@font-face` が1つで `font-display: swap` を持ち、`unicode-range` を持たないこと（今の Noto Sans JP と同じ形）を確かめて記録する（`performance-design.md` 5節）
  - **前提が崩れたとき**（`unicode-range` を持つ、`@font-face` が複数、`font-display: swap` でない）: 2.1 の方針のとおり、NFR6.4 の確かめは緩めず、生成をこの手順で止めて依頼者に諮る（9節の決定 4 の (b)）
- [x] `axe-core` は `config/npm-build-tools.txt` に足さない（成果物を作る道具ではない）。`frontend/src/` から読み込まない
- [x] 対応: NFR9.5・NFR9.6、NFR 設計の承認の場の U4 R-02、`team.md` の Code Style（新しい依存のライセンス）

### Step 4: 骨組み（型・定数・文言の鍵・置き場）

- [x] `frontend/src/app/display-settings/displaySettingsTypes.ts` を作る: `THEME_CHOICES`（`light`・`dark`・`system`）・`FONT_SIZES`（`sm`・`md`・`lg`）・`DISPLAY_LANGUAGES`（`ja`・`en`）・`BRAND_COLORS`・`FONT_FAMILIES` の `as const` の一覧と、そこから作る文字列リテラルの union の型（`enum` を使わない）、`DisplaySettings`（3つの軸、どれも任意の形と必須の形）、`UserDisplaySettings`（3つの軸と氏名）、`LANGUAGE_NAMES`（`{ ja: '日本語', en: 'English' }`、訳さない。D12）、既定の値（D1: テーマ `system`・文字の大きさ `md`、言語はブラウザの言語設定）
- [x] 骨組みの文言に `display.theme.system`（OS に合わせる / Match OS）・`display.theme.light`（ライト / Light）・`display.theme.dark`（ダーク / Dark）・`display.fontSize.sm`（小 / Small）・`display.fontSize.md`（標準 / Medium）・`display.fontSize.lg`（大 / Large）を `frontend/src/app/i18n/messages/ja.ts`・`en.ts` に足す（8節）
- [x] AuthUi の文言 `auth.login.registered`（登録が完了しました。設定したパスワードでログインしてください。 / Registration is complete. Log in with the password you set.）・`auth.language.label`（表示の言語 / Display language）を `frontend/src/features/auth/registration.ts` の `messages` に足す（8節）
- [x] 新しいファイルはすべて先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）と、日本語の説明のコメントを置く。エクスポートは名前付きだけ（`export default` を使わない）
- [x] 対応: D1・D12、NFR8.1・NFR8.2、`team.md` の Code Style

### Step 5: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` 2節の単体のコマンド（Vitest の対象を U4 の置き場と手を入れる既存の置き場に絞ったもの）が、この時点の状態で動き、既存のテストが通ることを確かめる。U4 の新しいテストはまだ無いが、`src/app`・`src/shared/api-client`・`src/features/auth` の既存のテストに当たるため「当たるテストが無い」にはならない
- [x] `fast-check`（既存）と `vitest-axe`（既存）が使えることを確かめる。jsdom に `matchMedia` が無いこと（`vitest.setup.ts` は差し替えない）を確かめ、OS の配色の差し替えをテストの支え（`frontend/src/app/display-settings/testing/fakeColorScheme.ts`。変化の知らせを送れる偽の `matchMedia`）として用意する方針を決める（全体の setup は変えない）
- [x] `(cd frontend && npx playwright test --list)` で既存の E2E の一覧が読めることを確かめる（050 はまだ無い）
- [x] 対応: Testing Contract の `runner_step`

### Step 6: 業務処理（解き方の純粋な関数）— 実装

- [x] `resolveDisplaySettings.ts` の純粋な関数（React・ブラウザの保存・`window` に触れない）:
  - `parseStoredDisplaySettings(raw: string | null)`: JSON として読めなければ全体を無いもの、読めたら項目ごとに許される値だけを受け入れ、知らない項目は捨てる（4.1・D7）
  - `resolveTheme(choice, prefersDark)`: `system` は OS の配色で `light`・`dark` に解き、`light`・`dark` は OS によらない（D4）
  - `validateUserDisplaySettings(prefs)`: ログイン状態の `preferences` を項目ごとに検証し、外れた項目は無いものにする（W5 の6）
  - `decideScreenSettings(input)`: 入力はログイン状態（ログイン中か、今の結び付けの印、`preferences`）・ブラウザの保存の値・保存の後の利用者の設定（印つき）・見せ方（印つき、軸ごと）・ブラウザの言語設定・OS の配色。部品 3.3 の順（(1) 当てている値、(2) 結び付いた見せ方の軸だけ置き換え、(3) テーマを解く）で `language`・`theme`・`fontSize`・`resolvedTheme` を返す。無い軸は D1 の既定（言語は既存の `resolveLanguage`）
  - `parseAppearance(body: unknown)`: 項目ごとに C7 の許される値だけを返し、余計な項目・配列・数・`null` の形の誤りは「当てる値なし」にする（W3、NFR9.3）
  - `isDisplayLanguage(value)`: `ja`・`en` の判定（NFR9.2）
- [x] 対応: D1・D2・D4・D7・D8、W3・W5・W11、NFR9.2・NFR9.3・NFR9.9

### Step 7: 業務処理 — テスト（単体・性質ベース）

- [x] `resolveDisplaySettings.test.ts`（fast-check、失敗時の `seed`・`path` はテストの出力に残る）:
  - 保存の値: 壊れた JSON・JSON でない・配列・数・`null` は全体が無い、知らない値の項目だけが無い、知らない項目は捨てる、有効な値はそのまま。性質: どんな文字列でも例外を出さず、受け入れた値は許される値だけ
  - テーマ: `system` は OS の配色どおり、`light`・`dark` は OS によらない。性質: どの入力でも `light`・`dark` のどちらか
  - 画面の値: ログインの前はブラウザの保存の値（無い軸は既定）、ログインの後は利用者の設定（前の利用者の保存の値を使わない）、保存の後の利用者の設定が今の印なら優先、見せ方は印が今と同じときだけ・軸ごとに置き換わる、印が違う見せ方は捨てたものになる。性質: どの入力でも3つの軸が許される値になる、見せ方の無い軸は当てている値のまま
  - 言語: 画面の言語を決める道の結果が、どの入力でも `ja`・`en` のどちらか（NFR9.2 の性質）
  - 見た目の設定の応答: 4色・2種はそのまま、大文字・前後の空白・知らない値・余計な項目・配列・数・`null`・欠けた項目は当てない（項目ごと）
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR9.9・NFR9.2・NFR9.3、D1・D2・D4・D7

### Step 8: データアクセス（ブラウザの保存・受け渡し）— 実装

- [x] `browserStorage.ts`: U4 の鍵 `mastersmith.display-settings` の読み取り（`parseStoredDisplaySettings`）、3つをまとめて書く（3つの軸だけからその都度 JSON を作る。ログイン状態・応答の本文をそのまま書かない）、言語だけを書く（読み直した今の値の `language` だけを置き換え、無い項目は無いまま）、写しの鍵の書き直し（`design-system-theme` は U4 の鍵のテーマの選択を D4 で解いた値、無ければ OS の配色で解いた値。`design-system-font-size` は U4 の鍵の文字の大きさ、無ければ `md`）。`design-system-brand`・`design-system-font-family` は書かない。すべての読み書きを `try`/`catch` で包み、外へ出さない（`security-design.md` 1.1）
- [x] `loginHandoff.ts`: モジュールの中の変数1つに `{ email: string }` だけを持つ。`handOffToLogin(email)`・`takeLoginHandoff()`（読むだけ）・`clearLoginHandoff()`・`resetLoginHandoff()`。URL・ブラウザの保存・`history.state`・`console` に載せない（`security-design.md` 2節）
- [x] 対応: 4.1・4.2・D5〜D7・D13、NFR2.1・NFR2.2

### Step 9: データアクセス — テスト（単体）

- [x] `browserStorage.test.ts`: 3つの保存と読み直し、言語だけの保存でほかの2つが変わらず無い項目は無いまま、写しの鍵の書き直し（U4 の鍵あり・無し・OS dark・壊れた値）、ブランドとフォントファミリーの鍵を書かないこと、`localStorage` の `getItem`・`setItem` が例外を投げても例外を出さないこと（`vi.spyOn(Storage.prototype, ...)`）、書いた JSON の項目の名前が `language`・`theme`・`fontSize` の中だけであること（NFR2.2）
- [x] `loginHandoff.test.ts`: 渡した値を読める、消した後は無い、読むだけでは消えない、localStorage・sessionStorage・`window.location.href` にメールアドレスが無い
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR2.1・NFR2.2、D5〜D7

### Step 10: API（ApiClient のトークンを付けないパスと要求の言語・見た目の設定の読み取り）— 実装

- [x] `apiClient.ts`: `AUTH_API_PATHS`・`isAuthApiPath` を `TOKENLESS_API_PATHS`・`isTokenlessApiPath` に改め、1つの一覧に認証の API の3つ（BR8.5）と公開の API の3つ（`/api/appearance`・`/api/registration/verify`・`/api/registration/complete`、C7・C6）をコメントで分けて並べる。判定は問い合わせの部分を除いた完全な一致（前方一致・正規表現・`startsWith` を使わず、大文字・小文字・末尾の `/` の揺れを直さない）。`apiFetch` は判定の結果だけで分岐し、トークンを付けないパスでは 401 でも更新と送り直しをしない（D10、`security-design.md` 3節）。ほかのパスの付け方と 401 の更新の流れ・同時の 401 をまとめる形・エラーの変換は変えない
- [x] `registerLanguageResolver(resolver: () => DisplayLanguage)` を足し、`send` で、呼び出し側が `Accept-Language` を明示していなければ、関数が返す値を一覧（`ja`・`en`）と比べてから付ける。関数が無ければ付けない。`resetApiClient` で関数も消す（D9、`security-design.md` 4節）。ファイルの先頭の説明のコメントを直す
- [x] `appearanceLoad.ts`: `startAppearanceLoad()` はモジュールの中で1回だけ約束を作り（2回目以降は同じ約束）、`apiFetch('/api/appearance')` の成功（200）の本文を `parseAppearance` に通した結果を返す。通信の失敗・200 以外・JSON でない・形の誤りは「当てる値なし」（例外で終わらない）。上限・再試行・中断は置かない（`performance-design.md` 2.1、W2 の4）。テストの初期化 `resetAppearanceLoad` を置く
- [x] 対応: D9・D10・D11・W3・W12、NFR6.2・NFR9.1〜NFR9.3、C6・C7、U8 の引き継ぎ（U4 との突き合わせ）

### Step 11: API — テスト（単体）

- [x] `apiClient.test.ts` に足す（既存の確かめは変えずに通す。名前を改めた後も認証の API の3つが今までどおりトークンなしで送られる）: アクセストークンを持つ状態で公開の3つのパスに `Authorization` が付かない、401 / `AUTHENTICATION_REQUIRED` でも更新の手段が呼ばれない、問い合わせの付いたパス（`/api/appearance?x=1`）も同じに判定される、似た別のパス（`/api/registration/other`・`/api/appearance/x`・`/api/appearances`・`/api/Appearance`・`/api/appearance/`）にはトークンが付く
- [x] 同じく `Accept-Language`: 関数の値が付く（認証の API・公開の API・ほかの API・`apiDownload` を含む）、呼び出し側の指定は上書きしない、関数が無ければ付けない、関数が許されない値（型を越えた値）を返しても付けない、`resetApiClient` で消える
- [x] `appearanceLoad.test.ts`: 2回呼んでも `fetch` は1回、トークンを持つ状態でも `Authorization` が付かない、成功で2項目、許されない値の項目は当てない、500・通信の失敗・JSON でない本文で「当てる値なし」、例外で終わらない
- [x] 単体のコマンドを実行して通す
- [x] 対応: NFR9.1・NFR9.2・NFR9.3・NFR6.2

### Step 12: AuthUi のログイン状態の受け渡し（C3）— 実装

- [x] `authApi.ts` の `CurrentUser` に `displayName`・`language`・`theme`・`fontSize` を足す（C3）
- [x] `registry/types.ts` の `LoginState` に任意の `preferences?: { language; theme; fontSize }` を足し、`displayName` の説明を「ユーザーメニューに表示する氏名」に直す（差し込み口の形の安全な追加）
- [x] `loginStateProvider.ts` の `toLoginState` を、ログイン中なら `displayName` に C3 の `displayName`（今のメールアドレスではない、M7）、`preferences` に3つの値を入れる形にする
- [x] `LoginStateGate.tsx` の `normalizeLoginState` を、ログイン中なら `preferences` も通す形にする（未ログインなら従来どおり落とす）
- [x] 対応: 部品 5節、C3、W5

### Step 13: AuthUi のログイン状態の受け渡し — テスト（単体）

- [x] `loginStateProvider.test.ts`: ログイン中は氏名と3つの値、未ログインは `preferences` なし。既存の確かめの `displayName` の期待をメールアドレスから氏名へ直す（承認済みの設計 M7 による直し、8節）
- [x] `LoginStateGate.test.tsx`: `preferences` がログイン中だけ通る、未ログインで落ちる、既存の提供元（`preferences` なし）もそのまま動く
- [x] `authApi.test.ts`・`authSession.test.ts`: 応答の4項目を受けて持つ（既存の確かめは変えない）
- [x] 単体のコマンドを実行して通す
- [x] 対応: C3、M7、W5 の6

### Step 14: 画面の振る舞い — 表示の設定の土台（置き場・DisplaySettingsProvider・I18nProvider・App・入口）— 実装

- [x] `colorScheme.ts`: `prefers-color-scheme: dark` の今の値と変化の購読。`matchMedia` が無いブラウザ（と jsdom）は `light`（W11 の5）
- [x] `displaySettingsStore.ts`: 購読できる置き場（ブラウザの保存の値・見せ方・保存の後の利用者の設定、それぞれの結び付けの印）。口の関数 `saveBrowserDisplaySettings({ language, theme, fontSize })`（3つを保存し見せ方を捨てる。ログインの前は当てている値がこの値になる。W8）と `saveBrowserLanguage(language)`（言語だけを保存し言語の見せ方を捨てる。W7）。`resetDisplaySettings()`
- [x] `DisplaySettingsProvider.tsx`（props は `appearance`（約束）と `children`）:
  - 見た目の設定の答えが出るまで子を描かない（D11）。答えが出たら、当てる値のある項目だけ make-you-chic-ui の `setBrand`・`setFontFamily` を呼ぶ（利用者の操作で変える手段は置かない、FR8.1）
  - 描画の中で `decideScreenSettings` で画面の値を決める（ログイン状態が変わった描画でそのまま文言が切り替わる。D3）
  - 描画の確定の直後（`useLayoutEffect`）に、このタブの画面の値が前に渡した値から変わったときだけ `setTheme`（解いた値）・`setFontSize` を渡し（ほかのタブの変化を打ち消さない、4.2・Q3 A）、`<html lang>` を画面の言語にし、`registerLanguageResolver` の関数が返す値を画面の言語にする（W12 の4）
  - ログイン状態が新しくなり表示の設定を持つときは、利用者の設定をブラウザに保存する（D6 の (a)）。見せ方と保存の後の利用者の設定の結び付けを新しい印にし、古いものを捨てる（D2・D8）
  - テーマの画面の値が `system` の間だけ OS の配色の変化を購読し、解き直す（W11）
  - `useDisplaySettings()` は `language`・`theme`・`fontSize`・`resolvedTheme`・`displayName`（ログインの後だけ。保存の後の利用者の設定か、ログイン状態の氏名）と、`setPreview(theme?, fontSize?)`（渡した軸だけ置き換え、保存しない）・`clearPreview()`・`applyUserPreferences(prefs)`（ログインの後だけ有効。当てている値と氏名を置き換え、見せ方を捨て、ブラウザに保存する。ログインの前は何もしない）・`setLanguage(lang)`（言語の見せ方、保存しない）を返す（部品 3.1、5節の移り変わりの注意）
- [x] `I18nProvider.tsx`: 言語を必須の props `language` で受け、`createI18n` を言語と文言で作る（言語が変わったら同じ描画で文言が変わる）。ブラウザの言語設定を読む処理と `<html lang>` の副作用を外す。`useMessages`・`useDisplayLanguage` は残す
- [x] `App.tsx`: 並びを部品 1節のとおりに変え（起動の誤りの画面も同じ並び）、`DisplaySettingsProvider` の子として `I18nProvider language={...}` を置く（言語は `useDisplaySettings().language`）。props に任意の `appearance`（約束、既定は `startAppearanceLoad()`）を足す
- [x] `main.tsx`: Noto Sans JP の8つの CSS の後に `@fontsource/noto-serif-jp` の `japanese-400`〜`700`・`latin-400`〜`700` の8つの CSS を読み込む（`<link rel="preload">` は足さない）。`createRoot` の前に写しの鍵の書き直しと `startAppearanceLoad()` を呼び、約束を `App` に渡す（埋め込みのスクリプトは使わない、NFR9.4）。説明のコメントを直す
- [x] 見た目の設定の応答が返らないと全画面が描けない影響（W2 の5）は設計どおり受け入れ、コードのコメントに理由を書く
- [x] 対応: 部品 1〜4節、W1〜W6・W10〜W12、D2〜D6・D8・D11・D14、NFR6.2・NFR6.4・NFR7.4、C9

### Step 15: 画面の振る舞い — 表示の設定の土台 — テスト（画面部品・既存のテストの直し）

- [x] `renderWithProviders.tsx` を 2.2 のとおり広げる（`ThemeProvider`・`DisplaySettingsProvider`、見た目の設定の約束の差し替え。既定は答え済み・当てる値なし）。テストの前後で `resetDisplaySettings`・`resetLoginHandoff`・`resetAppearanceLoad`・`resetApiClient`・localStorage の片付けを行う手伝いを置く
- [x] `DisplaySettingsProvider.test.tsx`（部品 7節の `DisplaySettingsProvider` の行を1つずつ）:
  - 見た目の設定と復元の答えが出るまで描かない（見た目の設定だけ・復元だけの答えの2通り、順を入れ替えて）、見た目の設定の失敗で前の値のまま描き読み直さない、成功で `<html>` の `data-brand`・`data-font-family` が変わる、許されない値・余計な項目・形の誤りで変わらない（NFR9.3）
  - 2つの要求（`/api/appearance` とトークンの更新）が、どちらにも答えない状態でともに送られている（`App` を描いて `fetch` を止める。NFR6.2）、StrictMode の中で描いても `/api/appearance` の要求が1回
  - ログインの前はブラウザの保存の値、無ければブラウザの言語設定と OS の配色（W4・W6、AC4.1.6）
  - 前の利用者の値（dark）がブラウザに残る状態で light の利用者がログインすると、ログインの後の最初の画面から light で、途中で dark が出ない（`<html>` の `data-theme` を描画ごとに記録して見る。W5、AC4.1.5）。前の値が一瞬出るときは 2.2 のとおり `DisplaySettingsProvider` が同じ時点で `<html>` の属性も置く形にし、どちらにしたかを記録する
  - ブラウザの保存が light・md・ja で、利用者の設定が dark・lg・en のセッションの復元で dark・lg・en になり、ユーザーメニューに氏名が出る（AC4.1.9）
  - ブラウザの保存を消してからログインしても利用者の設定で表示され、ログインでブラウザに保存される（AC4.1.2）
  - テーマ `system` で OS を dark にするとダーク、開いたまま light に変えると追従、見せ方の `system` でも追従、`light` の間は OS の変化で何もしない（W11、AC4.1.3）
  - `applyUserPreferences` で言語を en にすると文言と `<html lang>` が en、以降の要求の `Accept-Language` が en、氏名がすぐ出る（W10、AC4.1.4・AC4.1.8、CR1.3）
  - `setPreview` は保存しない、渡さない軸は前の見せ方のまま、`clearPreview` で戻る、ログイン状態が変わると見せ方が捨てられる、ログインの前の `applyUserPreferences` は何もしない（W10、D2・D6）
  - `saveBrowserDisplaySettings` の後、ログインの画面が保存した en・dark・lg で表示される（W8、AC3.2.18）。ログインの後に呼んでも当てている値は変わらない
  - ほかのタブの make-you-chic-ui の鍵の変化（`storage` の知らせ）で make-you-chic-ui の見た目が変わったまま打ち消されない（4.2、Q3 A）
  - 保存の後に U4 の鍵の項目の名前が3つの中だけで、localStorage・sessionStorage のすべての値にテストのトークン・メールアドレス・氏名が無い（ログインの成功・`applyUserPreferences`・`saveBrowserDisplaySettings`・言語の切り替えの後。NFR2.2、`security-design.md` 1.2）
  - `localStorage` が例外を投げる状態でも画面が描かれる（D7）
- [x] `displaySettingsStore.test.ts`・`colorScheme.test.ts`: 口の関数の保存と見せ方の捨て方、`matchMedia` が無いときの light、購読の解除
- [x] 既存のテストの直し（承認済みの設計に伴うもの。8節）: `I18nProvider.test.tsx`（言語を props で渡す形へ。ブラウザの言語設定から決める確かめは `DisplaySettingsProvider` と `resolveDisplaySettings` の確かめへ移す）、`App.test.tsx`（見た目の設定の約束を答え済みで渡す）。そのほかの既存の画面のテスト（`renderWithProviders` を使うもの、DSL・管理・認証）は期待を変えずに通す。8節に無い期待の変更が要るときは生成を止めて諮る（9節の決定 4 の (e)）
- [x] 単体のコマンドを実行して通す
- [x] 対応: 部品 7節、NFR2.2・NFR6.2・NFR7.4・NFR9.3・NFR9.10、AC3.2.18・AC4.1.2〜AC4.1.6・AC4.1.8・AC4.1.9、CR1.1〜CR1.3・CR2

### Step 16: 画面の振る舞い — 画面部品（LoginLayout・LoginLanguageSwitch・LoginPage・LoginForm・ShellLayout）— 実装

- [x] `LoginLayout.tsx`: 任意の props `topRight` を足し、右上に固定して置く（CSS は同じ場所の素の CSS。狭い幅で見出し・フォームと重ならない配置にする）。`LOGIN_LAYOUT` の振り分け（登録が無い場合の枠だけの表示）では渡さない
- [x] `LoginLanguageSwitch.tsx`: make-you-chic-ui の `Button` を2つ並べた組（`role="group"` と `aria-label` に `auth.language.label`）。「日本語」「English」は `LANGUAGE_NAMES` から出し、それぞれに `lang`（`ja`・`en`）と `aria-pressed`（今の言語が true）を付ける。選ぶと `useDisplaySettings().setLanguage` と `saveBrowserLanguage` を呼ぶ。フォーカスは選んだボタンのまま。`data-testid`（`login-language-switch`・`login-language-switch-ja`・`login-language-switch-en`）を付ける。`loading` は使わない
- [x] `LoginPage.tsx`: `LoginLayout` の `topRight` に `LoginLanguageSwitch` を渡す
- [x] `LoginForm.tsx`: 最初の描画で `takeLoginHandoff()` を1回だけ読み（`useState` の初期値）、値があればフォームの上に `Alert`（成功の種類、`auth.login.registered`、`data-testid="login-form-registered-alert"`）を出し、メールアドレスの欄の初期値にする。受け渡しの口から消すのは描画の確定の後（`useEffect` で `clearLoginHandoff`、StrictMode の二重の描画でも失われない。W9 の3）
- [x] `ShellLayout.tsx`: ユーザーメニューの名前を `useDisplaySettings().displayName`、無ければログイン状態の `displayName`
- [x] 対応: W5・W7・W9、D12・D13、AC3.2.17・AC4.1.8、CR1.5・CR6.6、NFR7.4

### Step 17: 画面の振る舞い — 画面部品 — テスト（vitest-axe を含む）

- [x] `LoginLanguageSwitch.test.tsx`: 「日本語」「English」と `lang` 属性、今の言語の `aria-pressed`、画面の言語が en でも「日本語」と出る（NFR8.2）、選ぶと文言と `<html lang>` が変わりブラウザに言語だけが保存される（テーマ・文字の大きさは保存済みのまま）、切り替えた後の誤ったパスワードのログインの要求が切り替えた言語の `Accept-Language` を持つ（CR1.5、W7 の3）、キーボード（Tab・Enter・Space）で切り替えられフォーカスが残る、vitest-axe の違反 0 件
- [x] `LoginForm.test.tsx` に足す: 受け渡しがあれば案内が出てメールアドレスの欄に値が入る、2回目の表示（部品を外して描き直す）と受け渡しの無い初めの表示では出ない、受け渡しの後の `window.location.href` と localStorage・sessionStorage にメールアドレスが無い（NFR2.1）、案内のある状態の vitest-axe の違反 0 件（既存のアクセシビリティの検査は残す）
- [x] `LoginLayout.test.tsx`・`LoginPage.test.tsx` に足す: 右上の置き場に渡したものが出る、渡さないときは出ない、ログインの画面に言語の切り替えがある
- [x] `ShellLayout.test.tsx` に足す: ユーザーメニューの名前がメールアドレスではなく氏名、`applyUserPreferences` の直後に新しい氏名、氏名の無いときはログイン状態の `displayName`、vitest-axe の違反 0 件（既存があれば氏名の状態で）
- [x] 文言の鍵の確かめ: 既存の `messages.test.ts`（骨組みの ja・en の鍵がそろう）と `registration.test.ts`（AuthUi）で、足した鍵が ja・en の両方にあり空でないことを確かめる（NFR8.1）
- [x] 単体のコマンドを実行して通す。続けて U4 の範囲のカバレッジを見て、行 80%・分岐 70% に届かない U4 の新しいファイルがあればテストを足す
- [x] 対応: NFR2.1・NFR7.2・NFR7.4・NFR8.1・NFR8.2、AC3.2.17・AC4.1.8、CR1.5・CR6.6

### Step 18: 実際のブラウザの検査（050 と e2e/support、reporter の json）— 実装

- [x] `frontend/e2e/support/appearanceFixture.ts`: 見た目の設定の見本を1つだけ置き、画面の側の型（`displaySettingsTypes.ts` の `BrandColor`・`FontFamily` から作る応答の型）を付ける（`project.md` の Corrections）
- [x] `frontend/e2e/support/displayCombos.ts`: 組 (a) テーマ `light`・`dark` × 文字の大きさ `sm`・`md`・`lg`（`blue`、既定の幅）、(b) `blue`・`green`・`purple`・`orange` × `light`・`dark`（`md`、既定の幅）、(c) (a) と同じ6組を 375px × 812px の 20 組の一覧と、組ごとに新しいコンテキストを作る関数（`addInitScript` で読み込みの前に U4 の鍵へ `{ theme, fontSize }` を置く、`page.route('**/api/appearance', ...)` で見本の形の `{ brandColor, fontFamily: 'sans' }` を 200 で返す、`viewport`）。検査の前に `<html>` の `data-theme`（`light` は属性なし）・`data-font-size`・`data-brand` が組の値であることを確かめ、効いていなければ結果を使わずに失敗させる（`logical-components.md` 5.3）
- [x] `frontend/e2e/support/axe.ts`: axe-core の本体を Node の側で `createRequire(import.meta.url).resolve('axe-core/axe.min.js')` と `readFileSync` で読み、`page.evaluate` で評価し、`axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } })` を同じく `page.evaluate` で呼んで結果を返す。`bypassCSP`・`addScriptTag`・`@axe-core/playwright` は使わない。結果の規則の名前の合わせ（`passes`・`violations`・`incomplete`・`inapplicable`）に `color-contrast`・`scrollable-region-focusable` が無ければ失敗させる（`security-design.md` 6.1〜6.4）。`window.axe` の型の宣言はこのファイルの中に置く
- [x] `frontend/e2e/support/overflow.ts`: `document.documentElement.scrollWidth <= window.innerWidth` の判定と、失敗の説明（組と横の大きさ）
- [x] `frontend/e2e/support/pageProblems.ts`: 既存の `010-skeleton.e2e.ts` の `collectProblems` と同じ見方（`pageerror`、コンソールの error、初めのスクリプトで `securitypolicyviolation` をコンソールへ写す、未ログインのトークンの更新の 401 の1件だけを除く）と、要求の一覧の収集（`page.on('request')`）。`page.goto` より前に張る（NFR 設計の承認の場の U4 R-01）
- [x] `frontend/e2e/050-display-accessibility.e2e.ts`:
  - 20 組のテスト（組ごとに1つのテスト。題は組の名前）: 新しいコンテキストでログインの画面（`/login`）の最初の状態を開き、切り替えが効いたことを確かめ、axe の違反 0 件（違反があれば規則の名前・対象・組を失敗の説明に出す）、横のはみ出し無し。組ごとの成否・違反の件数と規則の名前・`incomplete` の規則の名前と件数を注記（`test.info().annotations`）と添付（JSON）に残す
  - 最初の画面の時間の測定のテスト（1つ）: 5回くり返し、各回で「新しいコンテキスト（キャッシュが空）→ 監視（CSP の違反と要求の一覧）を張る → `page.goto('/', { waitUntil: 'commit' })` の直前から見出し（ログイン）が見えるまでをテストの側の時計で測る → 判定 → コンテキストを閉じる」の順。`/api/appearance` は差し替えず本物の応答を使う。時間では失敗させず、5回の値（ミリ秒）と 2 秒以内の回数を注記と添付に残す（NFR6.1）。5回すべてで、CSP の違反が 0 件、要求の一覧に `noto-serif-jp` を含むフォントのファイルが無い（見た目の設定が `sans` の既定の WAR）ことを失敗の条件にする（NFR9.4・NFR6.4）。1回目で本物の `/api/appearance` の応答の項目の名前と型が見本と一致することを確かめる（`project.md` の Corrections）
  - どのテストもログインしない（内部DB・監査の記録・ロックの回数を変えない）。既存の 010〜040 は変えない
- [x] `frontend/playwright.config.ts` の reporter に `['json', { outputFile: 'test-results/e2e-results.json' }]` を足し、説明のコメントを直す（既存の `list`・`html` は残す。`.gitignore` は変えない）
- [x] 対応: NFR6.1・NFR6.4・NFR7.1・NFR7.3・NFR7.5・NFR9.4・NFR9.6・NFR9.11、NFR 設計の Q1 A・Q2 A、基盤の設計の Q2 A、NFR 設計の承認の場の U4 R-01

### Step 19: 実際のブラウザの検査 — 実行と確かめ

- [x] `./gradlew :backend:bootWar` で WAR を作り、`docker compose --profile mail up -d mailpit` で Mailpit を起動して（基盤の設計の N2）、`(cd frontend && caffeinate -i npx playwright test e2e/050-display-accessibility.e2e.ts)` で 050 だけを流す（`unit-test-instructions.md` 2節）
- [x] **CSP の下の `page.evaluate`**: CSP の見出しが付いた WAR の画面で axe-core を `page.evaluate` で評価して `axe.run` が結果を返すこと、同じテストで CSP の違反の知らせが出ないことを確かめて記録する（`security-design.md` 6.1）
  - **評価できない、または違反の知らせが出るとき**: `bypassCSP`・`addScriptTag`・検査のための道などへ切り替える前に、生成をこの手順で止めて依頼者に諮る（9節の決定 4 の (a)）
- [x] 20 組の結果（成否・違反の件数・`incomplete`）と、最初の画面の時間（5回の値、2 秒以内の回数）、CSP の違反の件数、Noto Serif JP の要求の件数を記録する。2 秒を超えた回があれば、2つの API・JavaScript の読み込み・描画のどれが遅いかを要求の一覧と時刻で切り分けて記録し、承認の場で依頼者に相談する（目標を緩めない。時間は関門にしない。NFR 要件の2節）
- [x] axe の違反や 375px のはみ出しが出たときは、U4 のコード（`LoginLayout`・`LoginLanguageSwitch` の配置・CSS など）で直して流し直す。直すのに make-you-chic-ui の変更が要るときは、生成を止めて依頼者に諮る（9節の決定 4 の (g)。`vendor/` は変えられない）
- [x] **R-02**: `sans` のときに Noto Serif JP のフォントのファイルへの要求が無いことを、Step 3 の CSS の確かめと合わせて記録する。要求が出たときは 2.1 の方針のとおり止めて諮る（9節の決定 4 の (b)）
- [x] `frontend/test-results/e2e-results.json` を開き、入っているのが組の名前・テストの題・時間・規則の名前・件数・要素の選択子だけで、トークン・パスワード・メールアドレス（`e2e-admin@example.com` と仮のパスワードの値）が入らないことを、値の文字列の検索で確かめて記録する（`cicd-pipeline.md` 4.2・5節）。結果のファイルはコミット・共有しない
- [x] 050 を2回続けて流し、結果（組の成否）が同じであることを確かめる（不安定な検査を残さない、`team.md` の Testing Posture）
- [x] 終わったら Mailpit は止めず消さない（README の手順のとおり開発者が片付ける。Step 23 でも使う）
- [x] 対応: NFR6.1・NFR6.4・NFR7.3・NFR7.5・NFR9.4・NFR9.11、NFR 設計の承認の場の U4 R-01・R-02、基盤の設計の N2・N9

### Step 20: ビルドの成果物の確かめ（CSP・埋め込み・フォント・axe-core の混入・大きさの前後）

- [x] `backend/src/main/resources/application.yaml` の CSP に差分が無いこと（`git diff --stat` に出ない）を確かめる（NFR9.4）
- [x] ビルドした `frontend/dist/index.html` に埋め込みのスクリプト・スタイルが無いこと（`<script>` は `src` つきだけ、`<style>` なし）を確かめる（NFR9.4）
- [x] ビルドした入口の CSS に Noto Serif JP の `@font-face` が `font-display: swap` で入り、フォントのファイルの URL が同じオリジンの `/assets/` であること、フォントのファイルが `dist/assets/` のハッシュ付きの名前で出ることを確かめる（NFR6.4、`performance-design.md` 5節）
- [x] `dist/` の下に axe-core の本体に特有の文字列 `Deque Systems` を含むファイルが無いことを確かめる（NFR9.6、`security-design.md` 7節）
- [x] 変更の後の値を記録し、Step 1 と比べる: 初回の JavaScript（gzip、500KB の目安の警告の有無）、フォントのファイルの合計（`noto-sans-jp`・`noto-serif-jp` ごと）、入口の CSS（圧縮前・gzip）、`dist/` の全体、WAR の大きさ（NFR6.3・NFR6.5）
- [x] 対応: NFR6.3〜NFR6.5・NFR9.4・NFR9.6

### Step 21: 静的検査とカバレッジ

- [x] `./gradlew frontendFormatCheck frontendLint frontendLintCss frontendLicenseCheck frontendTypecheck` を通す（Prettier・oxlint・ESLint（`react-hooks`）・Stylelint・ライセンスヘッダー・`tsc`。`frontend/e2e/` の新しいファイルも対象）。リンタの決まり（`react/no-danger` など）を緩めない
- [x] `./gradlew frontendCoverage` でフロントエンドのカバレッジの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を満たすことを確かめ、全体の値と U4 の新しいファイルの値を記録する。計測の除外を増やさない（`frontend/e2e/` は既存どおり計測の対象外。NFR9.8、`team.md` の Testing Posture）。届かないときはテストを足し、それでも届かなければ生成を止めて諮る（9節の決定 4 の (c)）
- [x] 対応: NFR9.8、`team.md` の Code Style・Testing Posture

### Step 22: 文書（README）

- [x] `README.md` に新しい節「画面の表示の設定（U4）」を足す: 表示の設定の3つの軸と当て方（ログインの前はブラウザに最後に保存された値、ログインの後は利用者の設定、テーマ `system` は OS に追従）、ブラウザに残す値（`mastersmith.display-settings` の3つと make-you-chic-ui の写しの鍵だけ、トークン・メールアドレス・氏名を残さない）、見た目の設定の読み方（起動時に1回、答えまで描かない、失敗は前の値、応答が返らないと全画面が描けない影響を受け入れていること。機能設計の U4 R-02）、ログインの画面の言語の切り替え、要求の言語（`Accept-Language`）、トークンを付けないパスの一覧（6つと完全な一致）、契約との差（9節の決定 3）
- [x] 「ビルドした WAR での画面の確認（E2E）」の表に `050-display-accessibility.e2e.ts` の行（ログインの画面の 20 組のアクセシビリティの検査と最初の画面の時間の測定。時間は記録だけで失敗させないこと、流れの E2E に数えないこと、050 だけを流すときも Mailpit の起動が要ること、結果は `frontend/test-results/e2e-results.json`）を足す（`cicd-pipeline.md` 4.3）
- [x] 取得と準備の節のサブモジュールの説明と「ライセンス」の節に、make-you-chic-ui の固定先の更新（`735ef04`）と、Noto Serif JP（OFL-1.1、実行時）・axe-core（MPL-2.0、開発時の検査だけで成果物に入らない）を足す（今の書き方に合わせ、書く場所がなければ依存の説明に1行ずつ）
- [x] `frontend/src/features/README.md` に、機能の画面は表示の設定を `frontend/src/app/display-settings/` の口（`useDisplaySettings`・`saveBrowserDisplaySettings`・`LANGUAGE_NAMES`、契約 C9）だけで扱い、make-you-chic-ui の `useTheme` と localStorage を直接触らないことを短く書く（U5〜U7 の B5 が使う）
- [x] 対応: `cicd-pipeline.md` 4.3、機能設計の U4 R-02、C9

### Step 23: E2E の全件（統合の前。9節の決定 5）

- [x] Mailpit を起動したまま（止まっていれば `docker compose --profile mail up -d mailpit`）`caffeinate -i ./gradlew e2eTest` を流し、既存の 6 件（010 の2件・020 の2件・030 の1件・040 の1件）と 050 の 21 件がすべて通ることを確かめる。既存の 020・030・040 のユーザーメニューの名前の確かめ（初期管理者の氏名はメールアドレス）とログインの画面の見出しが変わらず通ることを確かめる
- [x] 050 の時間の値を Step 19 の値と並べて記録する。Mailpit は止めず消さない
- [x] 対応: B4 の完了の条件（既存の画面のテストと E2E が通る）、`team.md` の Testing Posture（画面・認証に関わる変更を統合する前に E2E）、NFR9.10

### Step 24: 1コマンドの検査（統合の前の関門）

- [x] colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通り、対象DB のテストが SKIPPED でないことを確かめる
- [x] テストの件数（バックエンド・フロントエンド）と、フロントエンドの全体のカバレッジと、バックエンドの全体のカバレッジ（変わらない見込み）を実測の数字で記録し、Step 1 と比べる（既存のテストが減っていない・失敗していない）
- [x] 秘密情報の検出（Gitleaks）と依存関係の脆弱性検査（OSV-Scanner、`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`・`backend/gradle.lockfile`）が通ることを確かめる。lockfile を変えたため OSV-Scanner が UP-TO-DATE で飛ばされていないことを確かめ、飛ばされたときは `./gradlew osvScan --rerun` で流し直す。`@fontsource/noto-serif-jp`（実行時）は High 以上で止まる対象、`axe-core`（開発時）は `MAL-` だけ止まる対象であることを記録する
- [x] `vendorUnchanged` が通る（サブモジュールの中身を変えていない）ことを確かめる
- [x] 対応: B4 の共通の完了の条件、`project.md` の Mandated（統合の前の確認）

### Step 25: 記録とコミットの提案

- [x] `code-summary.md`（作ったもの、上流と計画との差、Step 1・Step 24 の実測、Step 2 の固定先の前後のハッシュと影響の確かめの結果、Step 3 の依存の版と R-02 の確かめ、Step 15 の make-you-chic-ui への反映の時点の選択、Step 19・23 の 050 の結果と時間、Step 20 の成果物の確かめと大きさの前後、9節の決定の反映、契約との差）、`source-manifest.json`（作った・変えたアプリのソースのすべてのパス。`vendor/make-you-chic-ui` の固定先の更新を含む）、`traceability.json`（機能設計の OK 16 件と NFR の枝番から手順と部品へ。Deferred は機能設計の分け方にそろえる）を作る
- [ ] 3節の C1〜C8 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）。C1 のメッセージに固定先の前後のハッシュを書く
- [ ] 対応: 段の記録、`project.md` の Change Control・Mandated

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US3.2 招待のリンクから登録を完了する（関わる） | AC3.2.17（W9・D13、登録の完了の後の案内とメールアドレスの持ち越し） | Step 8・9・16・17 |
| US3.2 | AC3.2.18（W8・W4・D6 の (c)、保存した3つでログインの画面） | Step 14・15 |
| US3.2 | AC3.2.1（登録の完了の画面の言語と表示の設定）の口 `setLanguage`・`setPreview`・`clearPreview`・`saveBrowserDisplaySettings` の振る舞い | Step 14・15（口の振る舞い）。画面の確かめは Deferred（u6-registration-ui） |
| US3.2 | AC3.2.2〜AC3.2.16（サーバーと画面） | Deferred（u3-invitation・u6-registration-ui）。U4 は登録の完了の API をトークンを付けない公開の API として扱う（Step 10・11） |
| US4.1 自分の表示の設定と氏名を変える（関わる） | AC4.1.2（W5・W10・D1・D2・D6 の (a)） | Step 6・7・14・15 |
| US4.1 | AC4.1.3（W11・D4、テーマ system と追従） | Step 6・7・14・15 |
| US4.1 | AC4.1.4（W10・W12・D9・D14、保存の後の言語と要求の言語） | Step 10・11・14・15 |
| US4.1 | AC4.1.5（W5・D2・D3、前の利用者の見た目が出ない） | Step 14・15 |
| US4.1 | AC4.1.6（W6・W4・D1・D6、ログインの前の画面） | Step 6・7・14・15 |
| US4.1 | AC4.1.8（W10・W5・D8、ユーザーメニューの氏名） | Step 12〜17 |
| US4.1 | AC4.1.9（W2・W5・D1・D3、セッションの復元で最初の描画から利用者の設定） | Step 14・15 |
| US4.1 | AC4.1.11・AC4.1.12（見せ方と保存の後）の口の振る舞い | Step 14・15（口の振る舞い）。画面の確かめは Deferred（u7-preferences-ui） |
| US4.1 | AC4.1.1・AC4.1.7・AC4.1.10・AC4.1.13 | Deferred（u2-user-preferences・u7-preferences-ui） |
| US1.1 利用者を招待する（関わる） | AC1.1.1 の元になる管理者の言語（`useDisplaySettings().language`） | Step 14・15（口）。画面の確かめは Deferred（u5-invitation-ui）。AC1.1.2〜AC1.1.13 は Deferred（u3-invitation・u5-invitation-ui） |
| CR1 言語の適用 | CR1.1・CR1.2（W5・W12・D9、ADR-005）、CR1.3（D14）、CR1.4（8節の文言）、CR1.5（W7） | Step 4・10・11・14〜17 |
| CR2 インスタンスの見た目の設定（Should） | W3・W2・D10・D11（Q2 A） | Step 6・7・10・11・14・15・18・19 |
| CR6 画面の共通の決まり | CR6.6（D12、言語の名前と `lang`）。そのほかは Deferred（u5〜u7） | Step 4・16・17 |
| 個人に関する値とブラウザの保存 | NFR2.1・NFR2.2 | Step 8・9・15・17 |
| 性能 | NFR6.1（Step 18・19・23 で記録、Build and Test に引き継ぐ）・NFR6.2（Step 10・14・15）・NFR6.3（Step 1・20）・NFR6.4（Step 3・14・19・20）・NFR6.5（Step 1・20） | Step 1・3・10・14・15・18〜20・23 |
| アクセシビリティ | NFR7.1・NFR7.3・NFR7.5（Step 18・19・23）、NFR7.2・NFR7.4（Step 16・17） | Step 16〜19・23 |
| 多言語 | NFR8.1・NFR8.2 | Step 4・17 |
| 画面の側のセキュリティ | NFR9.1〜NFR9.3（Step 6・7・10・11・15）、NFR9.4（Step 14・18〜20） | Step 6・7・10・11・14・15・18〜20 |
| 依存と make-you-chic-ui | NFR9.5・NFR9.6（Step 3・20・24）、NFR9.7（Step 2・24） | Step 2・3・20・24 |
| テストとカバレッジ | NFR9.8（Step 21・24）・NFR9.9（Step 7）・NFR9.10（Step 2・15・23）・NFR9.11（Step 18） | Step 2・5・7・15・18・21・23・24 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の確かめ（ApiClient と fetch、ブラウザの保存と localStorage、make-you-chic-ui の ThemeProvider との結び目、実際のブラウザの検査）を置く。成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。画面部品ごとに vitest-axe の検査を1件入れる（`team.md` の Testing Posture）。

| 部品 | 単体（Vitest） | 実際のブラウザ（Playwright） |
|---|---|---|
| 解き方の純粋な関数（`resolveDisplaySettings.ts`） | `resolveDisplaySettings.test.ts` 8 件＋性質ベース 4 件（保存の値・テーマ・画面の値・言語） | — |
| ブラウザの保存（`browserStorage.ts`） | `browserStorage.test.ts` 7 件 | — |
| 受け渡し（`loginHandoff.ts`） | `loginHandoff.test.ts` 5 件 | — |
| ApiClient（`apiClient.ts`） | 既存の `apiClient.test.ts` に足す 8 件（トークンを付けないパス 4・`Accept-Language` 4）。既存の確かめはそのまま | — |
| 見た目の設定の読み取り（`appearanceLoad.ts`） | `appearanceLoad.test.ts` 6 件 | — |
| OS の配色・置き場（`colorScheme.ts`・`displaySettingsStore.ts`） | `colorScheme.test.ts` 4 件、`displaySettingsStore.test.ts` 6 件 | — |
| `DisplaySettingsProvider` | `DisplaySettingsProvider.test.tsx` 8 件（部品 7節の行をまとめた場面。ゲート・並べ読み・ログインの前後・前の利用者・system の追従・口・ほかのタブ・保存の中身） | — |
| AuthUi の受け渡し（`loginStateProvider.ts`・`LoginStateGate.tsx`・`authApi.ts`） | 既存のテストに足す・直す 5〜6 件 | — |
| `I18nProvider`・`App` | 既存のテストの直し（言語の props、答え済みの約束）と足す 2〜3 件 | — |
| `LoginLanguageSwitch` | `LoginLanguageSwitch.test.tsx` 6 件（vitest-axe を含む） | — |
| `LoginForm`・`LoginLayout`・`LoginPage` | 既存のテストに足す 5〜6 件（案内のある状態の vitest-axe を含む） | — |
| `ShellLayout` | 既存のテストに足す 3〜4 件（vitest-axe を含む） | — |
| 文言 | 既存の `messages.test.ts`・`registration.test.ts` で足した鍵を確かめる | — |
| 実際のブラウザの検査（`050-display-accessibility.e2e.ts`） | — | 20 組のテスト 20 件、最初の画面の時間の測定 1 件（CSP の違反・Noto Serif JP の要求・本物の応答と見本の照合を含む） |

この Intent の代表の流れの E2E（招待から登録の完了まで）は B5（U5・U6）で足す。050 は流れの E2E ではないため本数に数えない（NFR9.11）。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|
| 層の順番 | Testing Contract の `plan_profile.steps` は「データの形 → DB アクセス → 業務処理 → API → 画面の振る舞い」 | データの形（DB）の層は無し。業務処理（解き方の純粋な関数）をデータアクセス（ブラウザの保存・受け渡し）より先に置く | U4 は内部DB を持たない。ブラウザの保存の読み取りが検証に純粋な関数を使うため、依存の順に並べた（test-after の方法は変えない） |
| トークンを付けないパスの名前 | `TOKENLESS_API_PATHS`・`isTokenlessApiPath` は仮の名前（NFR 設計の Q3 A） | 仮の名前のまま採る。`AUTH_API_PATHS`・`isAuthApiPath` は消す（`apiClient.ts` の中だけで使われている） | NFR 設計の承認どおり。ふるまいは NFR9.1 のまま |
| モジュールの名前と置き場 | 「モジュールの名前は仮で、コード生成で決める」（`logical-components.md` 1節） | 2.2 の名前（`displaySettingsTypes.ts`・`resolveDisplaySettings.ts`・`browserStorage.ts`・`appearanceLoad.ts`・`colorScheme.ts`・`displaySettingsStore.ts`・`DisplaySettingsProvider.tsx`・`login-handoff/loginHandoff.ts`、`e2e/support/` の5つ）。まとめて再公開する `index.ts` は置かない | 既存の `app/` の書き方（部品ごとのファイルを名前付きで読み込む）に合わせた |
| 見た目の設定の約束の渡し方 | `App` が約束を受けて `DisplaySettingsProvider` に渡す（`performance-design.md` 2.1・7節） | `main.tsx` が `createRoot` の前に `startAppearanceLoad()` を呼び、`App` の任意の props で渡す（既定も `startAppearanceLoad()`） | 描画の外で1回だけ作るため（StrictMode で要求を1回にする）。テストから答え済みの約束を渡せる |
| make-you-chic-ui への反映の時点 | 「一瞬出ることがテストで分かったときは `<html>` の属性も置く。どちらにするかはコード生成で確かめて記録する」（部品 3.3） | Step 15 のテストで確かめ、必要なときだけ `<html>` の `data-theme`・`data-font-size` を同じ時点で置く。結果を `code-summary.md` に記録する | 承認済みの設計が許した選択の中で決める |
| 既存のテストの直し（承認済みの設計に伴うもの） | `I18nProvider` の言語を必須の props にする（部品 4節・8節）、ユーザーメニューの名前を氏名にする（M7、部品 5節）、`renderWithProviders` を広げる（`logical-components.md` 7節） | `I18nProvider.test.tsx`（言語を props で渡す形へ）・`App.test.tsx`（答え済みの約束）・`loginStateProvider.test.ts`（`displayName` の期待を氏名へ）を直す。確かめの強さは変えず、ブラウザの言語設定から決める確かめは `DisplaySettingsProvider`・`resolveDisplaySettings` のテストへ移す | 承認済みの設計の変更が既存のテストの前提を変えるため。これ以外の期待の変更は止めて諮る（9節の決定 4 の (e)） |
| `renderWithProviders` の並び | 部品 1節の並び（`LoginStateGate` → `DisplaySettingsProvider` → `I18nProvider`） | 加えて make-you-chic-ui の `ThemeProvider` を外側に置く | `DisplaySettingsProvider` が `setTheme` などを呼ぶため（`ThemeProvider` の外では make-you-chic-ui が何もしない形で警告を出す） |
| 050 のテストの分け方 | 組ごとに新しいコンテキスト（`logical-components.md` 5.1） | 組ごとに1つのテスト（20 件）と測定のテスト 1 件 | 組ごとの成否を json の報告で読みやすくするため |
| 本物の見た目の設定の応答と見本の照合 | 設計に記述なし | 050 の測定のテストの1回目で、本物の応答の項目の名前と型が見本と一致することを確かめる | `project.md` の Corrections（E2E で API の答えを差し替えるときは見本を1つにし、本物の応答と照らす）。B4 には流れの E2E が無いため、本物の応答を使う測定のテストで照らす |
| DSL の画面の部品の置き換え | 機能設計の承認の場の「新しい部品への置き換え」は U5〜U7 の画面 | DSL の画面（前の Intent）の `RadioGroup`・`Button` は置き換えない。固定先の更新の影響だけを確かめる（Step 2） | 範囲の外。承認の場の決定は U5〜U7 の設計への置き換え |
| 既存の E2E | 変えない（`logical-components.md` 5.1） | 変えない。初期管理者の氏名はメールアドレスのため、020・030・040 のユーザーメニューの名前の確かめはそのまま通る見込み（Step 23） | 設計どおり |
| 契約 C9 の差 | 契約 C9 の口は `useDisplaySettings`（7つ）と `saveBrowserDisplaySettings`、置き場は「`frontend/src/app/` の側で U4 が決める」 | 機能設計どおり `resolvedTheme`・`displayName`・`LANGUAGE_NAMES`・`saveBrowserLanguage`（U4 の中の口）を足し、置き場を `frontend/src/app/display-settings/` にする。`contract-summary.md` の扱いは9節の決定 3 | 項目の追加は契約の決まりで安全な変更（持ち主の単位だけで行える） |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者がすべて推奨のとおり（A）に決めた。計画の各 Step と8節はこの決定に合わせてある。

1. **作業の場と git の操作・コミットの分け方**: A。Step 1 で `develop` から短命のブランチ `feature/260925-user-management-b4` を作り、Step 2 で `git -C vendor/make-you-chic-ui checkout 735ef04` を行う。統合は `develop` へ fast-forward。コミットは生成の後に依頼者の承認を得て C1〜C8 でまとめて行い、C1 は固定先だけの専用のコミットにする（`project.md` の Mandated の「専用のコミット」を文字どおり守る）。C1 だけの状態では、Step 2 で既存のテストの直しが要った場合に画面のテストが落ちうることを受け入れる（関門の `./gradlew verify` は統合する先頭の状態で通す）。
2. **固定先の更新で既存の DSL の画面のテストが Button の `aria-disabled` の変更のために落ちたとき**（Step 2）: A。原因が `loading` の `disabled` から `aria-disabled` への変更だけなら、落ちた確かめを「`aria-disabled="true"` で、押しても操作が呼ばれない」確かめに置き換える（`aria-busy` の確かめは残し、強さを保つ）。直しは C2 に入れ、8節と `code-summary.md` に記録する。画面のコード（DSL の画面）の変更が要る、またはほかの原因で落ちるときは、生成を止めて諮る。
3. **契約 C9 の差（`resolvedTheme`・`displayName`・`LANGUAGE_NAMES`・`saveBrowserLanguage`、置き場 `frontend/src/app/display-settings/`）の反映**: A。実装は承認済みの機能設計のとおりに作り、`inception/contract-design/contract-summary.md` は書き換えない。差を `code-summary.md` と README の U4 の節の「契約との差」に記録する（B2・B3 の決定と同じ扱い。Step 22・25）。
4. **生成を止めて諮る場面**: A。次の7つのときは、生成をその手順で止め、結果と候補を示して依頼者に諮る。
     - (a) Step 19 で、axe-core を `page.evaluate` で評価できない、または CSP の違反の知らせが出る（`bypassCSP`・`addScriptTag`・検査のための道へ切り替える前に止める）
     - (b) Step 3・19 で、Noto Serif JP の CSS が `unicode-range` を持つ・`sans` のときに Noto Serif JP のフォントのファイルが読まれる（NFR6.4 の確かめは緩めず、設計のやり直しの候補を示す。NFR 設計の承認の場の U4 R-02 の方針）
     - (c) Step 21 でテストを足してもフロントエンドのカバレッジの下限（行 80%・分岐 70%）に届かない
     - (d) Step 2 で、U4 が使う make-you-chic-ui の API が変わっていた、または画面のコードの変更が要る
     - (e) 8節に挙げたもの以外に、既存の画面のテスト・既存の E2E（010〜040）の期待を変える必要が出る（緩めずに作れる形を先に探す）
     - (f) Step 15 で、ログインの後の最初の画面に前の利用者の見た目が一瞬出て、`<html>` の属性を同じ時点で置いても防げない
     - (g) Step 19 で、ログインの画面の axe の違反・375px のはみ出しを直すのに make-you-chic-ui の変更が要る（`vendor/` は変えられない）
     - なお、最初の画面の時間が 2 秒を超えたときは止めずに切り分けて記録し、承認の場で相談する（時間は関門にしない。NFR 要件の2節）
5. **統合の前の E2E**: A。画面・認証に関わる変更のため、Step 23 のとおり Mailpit を起動して `./gradlew e2eTest` を流し、既存の 6 件と 050 の 21 件がすべて通ってから統合する（`team.md` の Testing Posture）。050 の時間の値は記録だけ。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:591505487a7fb6a8983ed2b2b5ed40524251e26ce603ffd5e0b713cb57a870cd",
  "contract_sha256": "sha256:b0e0f1eed9e80e43e2a774e6087e20f8a66c419412203955de70d736ee76c67f"
}
```

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1〜4（固定先の更新 Step 2・依存 Step 3・型と文言 Step 4）、テストの実行の準備は Step 5（最初のテストの Step 7 より前）、データの形（DB）の層は U4 に無い（内部DB を持たない）、業務処理（解き方の純粋な関数）は Step 6・7、データアクセス（ブラウザの保存・受け渡し）は Step 8・9（検証に純粋な関数を使うため業務処理の後。8節）、API（ApiClient と見た目の設定の読み取り、C3 の受け）は Step 10〜13、画面の振る舞いは Step 14〜17（土台 → 画面部品）と実際のブラウザの検査 Step 18・19、環境とビルドの設定と確かめは Step 2・3・18（reporter）・20・21・23・24、文書と記録は Step 22・25。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、フロントエンドの全体と U4 の新しいファイル、バックエンドの全体の値をもう一度実測して記録する | Build and Test |
| verify の時間 | B4 の後の `./gradlew verify` の時間を測り、B3 の後の実測（5 分 43 秒）と比べる | Build and Test |
| 最初の画面の時間（NFR6.1） | `./gradlew e2eTest` の 050 の測定のテストの5回の値と 2 秒以内の回数を `frontend/test-results/e2e-results.json` から写して記録する（統合の関門にしない。2 秒を超えたら切り分けて依頼者に相談、目標を緩めない）。運用の中での判定は `Unverified`（基盤の設計の Q1 A） | Build and Test（記録）・observability-setup・feedback-optimization（運用の判定） |
| 実際のブラウザの検査（NFR7.3・NFR7.5） | 050 の 20 組の成否・違反の件数と規則の名前・`incomplete` を json の報告から写して記録する。json に秘密が入らないことをもう一度確かめる | Build and Test |
| フォントと CSP（NFR6.4・NFR9.4） | 050 の測定のテストで、`sans` のときに Noto Serif JP のフォントのファイルが読まれないこと、CSP の違反が 0 件であることを記録する | Build and Test |
| 配信物の大きさ（NFR6.3・NFR6.5） | コード生成の前後の値（`code-summary.md`）を Build and Test の結果に写す。上限は置かない | Build and Test |
| 見た目の設定の API の時間 | `GET /api/appearance` の同時 10 件で p95 300 ミリ秒（U8 の NFR6.1、U8 の引き継ぎのとおり k6 の場面 `appearance`） | performance-validation（無ければ Build and Test、`project.md` の学び） |
| 指標・警報・SLO | `uri="/api/appearance"` の p95 のパネルを足すか、既存の警報の流し直し、画面の時間の SLO は `Unverified` | observability-setup |
| 配備と戻し | イメージを作り直して `docker compose up -d`、ヘルスチェックと既存のスモークテスト（ログインの画面が出る、`<html lang>`、言語の切り替えがある）。戻しはイメージだけ。戻した後にブラウザに残る U4 の鍵と写しの鍵に害が無いことの具体の確かめ（`cicd-pipeline.md` 7節） | deployment-pipeline（手順）・deployment-execution（実行） |
| 契約の反映 | 9節の決定 3 のとおり、C9 の差が `code-summary.md` と README に記録されているかを確かめる | Build and Test |
| B5 への引き継ぎ | U5〜U7 の画面を 050 と同じ手伝い（`frontend/e2e/support/`）で (a)・(b)・(c) の組に足す（NFR7.5）。ログインの後の画面の検査は前提を自分で作る（NFR9.11）。検査のファイルの番号をそろえる（NFR 設計の承認の場の U6 R-02・U7 R-02）。json の結果に操作の題が入るかを確かめる（基盤の設計の N9）。U6 が `handOffToLogin` と `saveBrowserDisplaySettings` を使い、U5 が `useDisplaySettings().language`、U6・U7 が `LANGUAGE_NAMES` と `display.theme.*`・`display.fontSize.*` を使うことを確かめる。E2E の見本は1つにして本物の応答と照らす（`project.md` の Corrections） | B5（U5・U6・U7 のコード生成の計画） |
