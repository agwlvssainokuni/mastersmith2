# Code Summary — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 は、管理の画面（管理の入口・DSL の管理・招待の管理、後の U5 の利用者の管理）が管理の API から「権限が無い」（`/api/admin/` の下・403・code `ACCESS_DENIED`）を受けたときの表示（S6）とログインの状態の読み直しを、画面の骨組みの1か所にまとめた。あわせて、管理者が自分自身の氏名・言語を変えたときに画面へ当てる口（契約 C4 の `useApplyOwnProfile`）を作った。`backend/` と `vendor/make-you-chic-ui` は変えていない。新しい API・依存・スキーマ・設定は無い。

パスはリポジトリのルートからの相対パスで、画面のファイルは `frontend/src/` の下を `src/...` と書く。経過と実測の詳しい値は `generation-notes.md`、作った・変えたファイルの一覧は `source-manifest.json`（42 件）、要件との対応は `traceability.json`。Step 17（B2 の関門と統合）の結果とコミットのハッシュは、まだ空欄である。

## 1. 作ったもの・変えたもの

### 1.1 本体（`frontend/src`）

| ファイル | 新しい・変えた | 中身 |
|---|---|---|
| `src/shared/api-client/adminForbidden.ts` | 新しい | `isAdminForbidden(path, error)` と定数 `ADMIN_API_PREFIX`・`ACCESS_DENIED`・`FORBIDDEN_STATUS`（D1）。import を持たない |
| `src/shared/api-client/apiClient.ts` | 変えた | `refreshSessionOnce()` を足した（今の `refreshOnce` を呼ぶだけ、D7・D10） |
| `src/app/admin-forbidden/forbiddenHeading.ts` | 新しい | `forbiddenHeadingKey`・`ADMIN_FORBIDDEN_HEADING_KEY`（D11） |
| `src/app/admin-forbidden/AdminForbiddenProvider.tsx` | 新しい | `AdminForbiddenProvider`・`useAdminForbidden`・`useIsAdminForbiddenHere`（D2〜D8、R-02） |
| `src/app/admin-forbidden/AdminForbiddenView.tsx`・`AdminForbiddenView.css` | 新しい | S6（見出し・Alert（info、`role="status"`）・「ホームへ戻る」、D12） |
| `src/app/i18n/messages/ja.ts`・`en.ts` | 変えた | `adminForbidden.heading`・`message`・`homeLink` |
| `src/app/routing/decideRoute.ts`・`AppRouter.tsx` | 変えた | `ADMIN_FORBIDDEN`（管理者でない利用者の管理の画面、SCREEN と同じ形の木。D6、R-05） |
| `src/app/layout/ShellLayout.tsx` | 変えた | 権限が無い URL のとき子の代わりに S6（D3） |
| `src/app/App.tsx` | 変えた | `AdminForbiddenProvider` を `FeatureRegistryProvider` の内側・`AppRouter` の外側に置いた |
| `src/app/display-settings/displaySettingsStore.ts` | 変えた | `applyOwnProfileFor`・`OwnProfile`（D13・D14、R-01） |
| `src/app/display-settings/DisplaySettingsProvider.tsx` | 変えた | 値に `applyOwnProfile`（ref で最新のログイン状態と当てている値を読む、SD 4.2、S-1） |
| `src/app/display-settings/useApplyOwnProfile.ts` | 新しい | `useApplyOwnProfile`・`ApplyOwnProfileInput`（C4） |
| `src/features/admin/adminAreaStatus.ts`・`AdminAreaPage.tsx` | 変えた | `NotFound` → `Forbidden`、`statusFromError(error, report)`。`FORBIDDEN_STATUS` と `NotFoundPage` の import を消した |
| `src/features/dsl/useDslAdmin.ts`・`DslAdminPage.tsx` | 変えた | `checkForbidden` は `report(error, DSL_API_ROOT)` を返す。`FORBIDDEN`・`forbidden` と「ページが見つかりません」の分かれ道を消した |
| `src/features/invitation/useInvitationAdmin.ts` | 変えた | 4つの失敗の扱いで `report(error, INVITATION_API_ROOT)` が true なら何もせず終える |

### 1.2 テストとテストの補助

| ファイル | 新しい・書き換え・足す | 件数 |
|---|---|---|
| `src/shared/api-client/adminForbidden.test.ts` | 新しい（例 6・性質 2） | 8 |
| `src/shared/api-client/apiClient.test.ts` | 足す 4 | 20 |
| `src/app/admin-forbidden/forbiddenHeading.test.ts` | 新しい（例 4・性質 1） | 5 |
| `src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` | 新しい | 12 |
| `src/app/admin-forbidden/AdminForbiddenView.test.tsx` | 新しい（vitest-axe 1件を含む） | 7 |
| `src/app/i18n/messages.test.ts` | 足す 1 | 6 |
| `src/app/routing/decideRoute.test.ts` | 書き換え 1・足す 2 | 11 |
| `src/app/routing/AppRouter.test.tsx` | 書き換え 1・足す 3 | 11 |
| `src/app/layout/ShellLayout.test.tsx` | 足す 5 | 18 |
| `src/app/testing/renderWithProviders.tsx` | `AdminForbiddenProvider` を置いた（口は変えない） | — |
| `src/app/display-settings/displaySettingsStore.test.ts` | 足す 5 | 11 |
| `src/app/display-settings/DisplaySettingsProvider.test.tsx` | 足す 8 | 23 |
| `src/features/admin/AdminAreaPage.test.tsx` | 描き方と4件の書き換え | 9 |
| `src/features/admin/adminAreaStatus.test.ts` | 書き換え 6 | 6 |
| `src/features/dsl/testing/renderDsl.tsx`・`src/features/invitation/testing/renderInvitation.tsx` | 省略できる `options`（`withShell`・`provider`・`registrations`）を足した（既定は今のまま） | — |
| `src/features/dsl/DslAdminPage.test.tsx` | 書き換え 1・足す 2 | 27 |
| `src/features/invitation/InvitationAdminPage.test.tsx` | 書き換え 3か所・足す 3 | 32 |
| `src/features/invitation/failureMessage.test.ts` | 書き換え 1 | 6 |

書き換えた既存のテストの一覧（ファイル・元の題・中身）は `generation-notes.md` の「書き換えた既存のテスト（Step 8 の分）」「同（Step 10 の分）」のとおり（NFR9.11）。書き換えの範囲（計画 4.2）の外のテストは変えていない。

### 1.3 E2E と文書

| ファイル | 新しい・変えた | 中身 |
|---|---|---|
| `frontend/e2e/support/loginPreferences.ts` | 変えた | 省略できる `displayName`（値が `undefined` の項目は重ねない。060 の動作は変わらない） |
| `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` | 新しい | 20 組の実際のブラウザの検査（実行は Step 17） |
| `README.md` | 変えた | E2E の表の 130 の行、「130 について」、DSL の管理画面の節の1文（「ページが見つかりません」→ S6）、「管理の画面の「権限が無い」の扱い（Intent 260930-user-admin の U4）」の節 |

## 2. 主な決定

- **判定は1か所**: 「権限が無い」は `isAdminForbidden` だけで判定し（パスが `/api/admin/` で始まり・403・`ACCESS_DENIED`）、ApiClient の要求の流れには入れない（FS の G3）。各画面は失敗を `useAdminForbidden` の関数にその画面の API の根のパスを添えて渡す。
- **骨組みで置き換える**: 権限が無い URL を Provider が1つだけ持ち、パスが変わったらその描画の中で捨てる。ShellLayout がコンテンツの領域を S6 に置き換えるため、開いていた確かめ・入力の表示は画面の部品ごと閉じる。
- **読み直しは1回・待たない**: S6 になったとき `refreshSessionOnce` を1回呼び、終わるまで重ねない。結果は待たず、決まった間隔・時刻の判定を置かない。401 とログアウトの扱いは変えない。
- **管理者でない利用者にも S6**: 振り分けは `ADMIN_FORBIDDEN` を返し、SCREEN と同じ形の木の中に S6 を描く（ForbiddenByApi → ForbiddenByRoute で ShellLayout を作り直さない）。「ページが見つかりません」は登録に無い URL だけ。
- **自分の氏名と言語の反映**: `applyOwnProfile` は依存なしの同じ関数で、呼ばれた時点の最新のログイン状態と、見せ方を除いた当てている値（言語・テーマ・文字の大きさ）を ref から読む。ブラウザの保存は言語だけ。ja・en 以外の言語は今の言語のまま氏名だけを当てる（N-6、依頼者が受け入れた）。
- **130**: `GET /api/admin/check` の1本だけを 403 の見本に差し替え、組の値と架空の2語の氏名をログインと復元の応答の1つの差し替えで当てる。読み直しの応答の約束は管理の入口を開く前に作り、全 20 組で「ホームへ戻る」まで確かめる。

## 3. テストの件数とカバレッジ（生成の時点の実測）

| 確かめ | 結果 |
|---|---|
| 画面のテストの全体（`npx vitest run`） | 95 ファイル・801 件すべて成功（U2 の後は 91 ファイル・736 件） |
| U4 のまとめの実行（`unit-test-instructions.md` 2.9） | 62 ファイル・519 件すべて成功 |
| `renderWithProviders` を使うほかの機能（`auth`・`preferences`・`registration`） | 28 ファイル・243 件すべて成功（変えていない） |
| `npm run test:coverage` | 行 97.61%（2414/2473）・分岐 93.01%（1505/1618）。下限（行 80%・分岐 70%）を満たす（U2 の後は 行 97.44%・分岐 92.67%） |
| U4 で作ったファイル | `adminForbidden.ts`・`forbiddenHeading.ts`・`AdminForbiddenView.tsx`・`useApplyOwnProfile.ts` は行・分岐とも 100%、`AdminForbiddenProvider.tsx` は行 100%・分岐 95%（目安 90% 以上） |
| `playwright test --list` | 060 が 21 件、130 が 20 件 |
| 初回の JavaScript（gzip、`./gradlew frontendBundleSize`） | U4 の前 123.1 KB → 後 122.7 KB（目安 500KB の内、NFR9.4） |

ファイルごとのカバレッジの表は `generation-notes.md`。正式な値は Step 17 の `:backend:cleanTest :backend:cleanIntegrationTest verify` で記録する。

## 4. 確かめの時点（`traceability.json` の OK のうち、Step 17 で流して確かめるもの）

`traceability.json` は upstream_ids 39（AC2.2.1〜AC2.2.6・AC5.1.1〜AC5.1.8・NFR1.1〜NFR9.12。U4 の機能設計は決まりを D1〜D14 で書いており `BRx.y` を持たない）、OK 31・Deferred 8・GAP 0。OK の target はすべて実在するファイル1つ。次の OK は、この段の生成では型・書式・リンタと `--list` だけを確かめ、合否は Step 17 で流して決める。

| ID | target | 確かめの時点 |
|---|---|---|
| NFR3.2 | `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` | Step 17 の `./gradlew e2eTest` と、json の報告の確かめ（報告の部品・2語の氏名の件数 0） |
| NFR7.3 | 同上 | Step 17 の `./gradlew e2eTest`（20 組の axe 違反 0・はみ出し無し・Avatar 2 文字） |
| NFR9.3 | 同上 | Step 17（時間を測らないことはソースで確かめ済み） |
| NFR9.10 | 同上 | Step 17 の `./gradlew e2eTest`（010〜100 の 110 件が変えずに通ること） |
| NFR9.4 | `frontend/scripts/check-bundle-size.mjs` | Step 17 の `verify` の段 9（生成の時点の値は 3節） |
| NFR9.8 | `frontend/vitest.config.ts` | Step 17 の `verify` の画面のカバレッジの段（生成の時点の値は 3節） |

Deferred の持ち主: AC2.2.6・AC5.1.1・AC5.1.4・AC5.1.5・AC5.1.6・AC5.1.8 は u3-user-admin-api、AC5.1.7 は u5-user-admin-ui、AC5.1.2 は u5-user-admin-ui・u3-user-admin-api（機能設計の `traceability.json` と同じ）。要件 FR・NFR との対応は、機能設計・NFR 設計の `traceability.json` を経てたどる（FR2.2・FR2.3・FR8.1・FR8.2 → AC2.2.x、FR6.3 → AC5.1.3）。

## 5. 静的検査と構造（Step 14）とコードのレビューでの確かめ（Step 15）

- 型の検査（`e2e` を含む）・Prettier・oxlint・ESLint・Stylelint・ライセンスヘッダー: すべて通った。除外は足していない。
- 画面の側の境界（NFR9.12）: `src/app`・`src/shared` から `features` を読む import は 0 件（`e20c3b7` と同じ）。`features/admin`・`dsl`・`invitation` の本体はほかの機能を読まない。
- `e20c3b7` と比べて差分が無い: `backend`（`application.yaml` を含む）、`vendor/make-you-chic-ui`、`frontend/package.json`・`package-lock.json`・`vitest.config.ts`・`playwright.config.ts`、`frontend/e2e/support/pageProblems.ts`・`axe.ts`・`displayCombos.ts`・`adminLogin.ts`、`frontend/e2e/030-admin-access.e2e.ts`、`.github`、`docker`、`compose.yaml`、`gradle`、`build.gradle.kts`。
- Step 15 の7項目（判定の1か所 NFR1.2、待たない・重ねない NFR9.1・NFR9.2、画面に出す値 NFR3.1、CSP と静的検査 NFR9.5、401 とログアウト NFR1.4、130 の報告と資格情報 NFR3.2、R4 の戻り方）は、すべて計画のとおりだった（方法と結果は `generation-notes.md` の Step 15 の表）。

## 6. 計画・承認済みの文書との差

承認済みの計画と設計の文書は書き換えていない（`project.md` の決まり）。

| ID | 中身（要点） |
|---|---|
| N-1 | Step 1 の先頭は `e20c3b7` ではなく記録だけのコミット R1 `4163b67`（親が `e20c3b7`、アプリのソースは同じ） |
| N-2 | `--list` でも報告の部品が `frontend/playwright-report/`・`frontend/test-results/` を作るため、中を開かずに消して記録した |
| N-3 | `forbiddenHeading.ts` から共通の見出しの鍵の定数 `ADMIN_FORBIDDEN_HEADING_KEY` も export した |
| N-4 | `AdminForbiddenView.css` は `Page.css` を読まず、同じ値の自前のクラスを部品と同じ場所に置いた |
| N-5 | テストの部品の、外へ関数を渡す代入を `useLayoutEffect` の中に置いた（ESLint の `react-hooks/globals`） |
| N-6 | `applyOwnProfileFor` の `current` に言語を含めた（ja・en 以外の言語では今の言語のまま氏名だけ）。**依頼者が受け入れた** |
| N-7 | 置き場の `stored` は `writeStoredLanguage` の返り値（読み直した保存の値に言語を重ねたもの） |
| N-8 | `AdminAreaPage.test` の確かめの途中の表示のテストの最初の確かめを `await findByTestId` にし、500 のテストに S6 が出ない確かめを足した（描き方の書き換えに伴う直し） |
| N-9 | `useApplyOwnProfile` の渡す値の型 `ApplyOwnProfileInput` と、置き場の `OwnProfile` を export した |
| N-10 | `adminAreaStatus.ts` から渡す関数の型 `ReportAdminForbidden` を export した |
| N-11 | `AdminAreaPage` の確かめの `useEffect` の依存に渡す関数を入れた（同じ関数のため確かめは繰り返さない） |
| N-12 | 招待と DSL の画面は、離れた後・古い読み直しの答えを今までどおり先に捨て、骨組みへ渡さない |
| N-13 | 130 の注記を `axe` と `admin-forbidden-problems` の2つにし、添付は付けない |
| N-14 | Step 14 の `--list` の報告の置き場も N-2 と同じく消した（ファイル 2・ディレクトリ 2） |
| N-15 | README の U4 の節の名前を「管理の画面の「権限が無い」の扱い（Intent 260930-user-admin の U4）」にし、「DSL の管理画面（U5）」の後に置いた |

### 依頼者の決定

| 論点 | 決定 |
|---|---|
| Q-A（130 の「ホームへ戻る」と問題の一覧の確かめの対象の組） | A: 全 20 組で行う |
| Q-B（この段と U2 の記録のコミットの置き場） | B: 作業ブランチの上で記録だけのコミット（R1・R2・R3）を作り、統合では squash の範囲から `aidlc/` を外して `develop` の上の別の記録のコミットにする |
| Q-C（U2 の単位ごとの squash の条件の確かめ） | A: U2 の `generation-notes.md` に「直しの後の verify」（BUILD SUCCESSFUL、バックエンドの単体 1287・結合 587、失敗 0）を書き足した |
| N-6（`applyOwnProfileFor` の `current` に言語を含める） | 受け入れる（実装はこのまま） |

## 7. 承認の場で確かめること

- Step 17 の結果（`verify`・`osvScan --rerun-tasks`・E2E の 11 ファイル・130 件、130 の組ごとの結果と全体の時間、json の報告に2語の氏名が含まれないこと、報告を消したこと）。この記録の時点では未実施。
- 4節の、Step 17 で流して確かめる OK（NFR3.2・NFR7.3・NFR9.3・NFR9.4・NFR9.8・NFR9.10）が Step 17 で満たされたこと。
- 初回の JavaScript の入口のファイルの分け方が変わった（`hooks-*.js` の分が `index-*.js` に入った形）。合計は 0.4 KB 減った。原因は確かめていない（見立て）。

## 8. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」のとおり。

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジと件数 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、画面の全体と U4 のファイルの行・分岐、バックエンドの全体をもう一度実測して記録する（NFR9.8） | Build and Test |
| CI | 依頼者のプッシュの後に CI が通ることを確かめる。失敗は `team.md` の「不安定なテストと CI の失敗」の決まりで扱う | Build and Test |
| E2E と 130 | `./gradlew e2eTest`（010〜100 と 130）を流し、130 の組ごとの結果と、報告の部品が通ったこと、2語の氏名の値が json に含まれないこと（件数だけ）を記録する（NFR7.3・NFR3.2） | Build and Test |
| E2E の報告の片付け | 流す → json の `stats` と題・状態・注記だけを読んで記録 → 2語の氏名の件数を記録 → `frontend/playwright-report/`・`frontend/test-results/` を消す → 消したことと共有していないことを記録 | Build and Test |
| 読み直しの API の時間 | トークンの更新の API の既存の目標。U4 のために測り直さない（NFR5.1）。運用の中の判定は `Unverified` | performance-validation |
| 初回の JavaScript の大きさ | U4 の前と後の値（gzip）を写す。目安を超えても警告だけ（NFR9.4） | Build and Test |
| スモークテストの S6 | 配備の後のスモークテストに S6 の確かめを入れるか | deployment-pipeline |
| 既存の表示の設定の口の同じ形の危険（R5） | `applyUserPreferences`・`setPreview`・`setLanguage` を最新のログイン状態で動かす形に直すか | 後の Intent |
| U5 が使う口 | すべての失敗の扱いの入口で `useAdminForbidden` に `/api/admin/users` を渡す、自分の行の保存の成功で `useApplyOwnProfile` を呼ぶ、見出しとサイドバーの項目の名前を「利用者の管理」でそろえる | B5（U5） |
| 8KB を超える要求の HTML の 400 | `isAdminForbidden` は `kind: 'response'`・403・`ACCESS_DENIED` のときだけ true のため、HTML の 400 は各画面の今の扱いのまま | B5（U5） |
| サーバー側の 403 と監査（AC2.2.6） | 今のサーバーの動作。サーバー側のテストは U3 | B3・B4（U3） |

## 9. 提案するコミットの区切り（依頼者の承認を得てから行う。生成の担当はコミットしない）

アプリのソースのコミット（C3〜C6）には `aidlc/` の下を入れない。メッセージは日本語で、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。

**C3** 件名「B2 U4: 管理の API の 403 を骨組みで扱う（isAdminForbidden・refreshSessionOnce・AdminForbiddenProvider・S6・振り分け）」（22 ファイル）

- `frontend/src/shared/api-client/adminForbidden.ts`・`adminForbidden.test.ts`・`apiClient.ts`・`apiClient.test.ts`
- `frontend/src/app/admin-forbidden/AdminForbiddenProvider.tsx`・`AdminForbiddenProvider.test.tsx`・`AdminForbiddenView.tsx`・`AdminForbiddenView.css`・`AdminForbiddenView.test.tsx`・`forbiddenHeading.ts`・`forbiddenHeading.test.ts`
- `frontend/src/app/i18n/messages/ja.ts`・`messages/en.ts`・`messages.test.ts`
- `frontend/src/app/routing/decideRoute.ts`・`decideRoute.test.ts`・`AppRouter.tsx`・`AppRouter.test.tsx`
- `frontend/src/app/layout/ShellLayout.tsx`・`ShellLayout.test.tsx`
- `frontend/src/app/App.tsx`、`frontend/src/app/testing/renderWithProviders.tsx`

**C4** 件名「B2 U4: 管理の入口・DSL の管理・招待の管理の 403 を骨組みの S6 に置き換える」（12 ファイル）

- `frontend/src/features/admin/adminAreaStatus.ts`・`adminAreaStatus.test.ts`・`AdminAreaPage.tsx`・`AdminAreaPage.test.tsx`
- `frontend/src/features/dsl/useDslAdmin.ts`・`DslAdminPage.tsx`・`DslAdminPage.test.tsx`・`testing/renderDsl.tsx`
- `frontend/src/features/invitation/useInvitationAdmin.ts`・`InvitationAdminPage.test.tsx`・`failureMessage.test.ts`・`testing/renderInvitation.tsx`

**C5** 件名「B2 U4: 自分の氏名と言語を画面に当てる口（useApplyOwnProfile・applyOwnProfileFor）」（5 ファイル）

- `frontend/src/app/display-settings/displaySettingsStore.ts`・`displaySettingsStore.test.ts`・`DisplaySettingsProvider.tsx`・`DisplaySettingsProvider.test.tsx`・`useApplyOwnProfile.ts`

**C6** 件名「B2 U4: S6 の実際のブラウザの検査 130・loginPreferences の displayName・README」（3 ファイル）

- `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts`、`frontend/e2e/support/loginPreferences.ts`、`README.md`

C3〜C6 の 42 ファイルは `source-manifest.json` と一致する。

**R2**（記録だけのコミット、C3〜C6 の後）件名「B2 U4 のコード生成の記録（まとめ・生成の記録・変えたファイルの一覧・網羅の記録・計画のチェック）」

- `aidlc/spaces/default/intents/260930-user-admin/construction/u4-admin-forbidden-ui/code-generation/` の `code-summary.md`・`generation-notes.md`・`source-manifest.json`・`traceability.json`・`code-generation-plan.md`（チェック）・`code-generation-questions.md`
- その時点の `aidlc/spaces/default/intents/260930-user-admin/aidlc-state.md` と監査ログ `audit/sakura-local-4e42a93f87ce.md`
- `git show --stat` で `aidlc/` の外のファイルが入っていないことを確かめる。

### U4 の単位ごとの squash の件名・本文の案（Step 17 の統合で `develop` の上に作る）

```text
B2 管理の画面の 403 の共通の扱い（U4）: 権限が無いときの表示 S6・ログインの状態の読み直し・自分の氏名と言語の反映の口

- 管理の API の「権限が無い」（/api/admin/ の下・403・ACCESS_DENIED）の判定を isAdminForbidden の1か所にし、
  画面の骨組み（AdminForbiddenProvider・ShellLayout）でコンテンツの領域を S6 に置き換える
- S6 になったときにログインの状態を refreshSessionOnce で1回読み直す（待たない・重ねない。401 の扱いは変えない）
- 管理者でない利用者が管理の画面の URL を開いたときも S6 にする（振り分けの ADMIN_FORBIDDEN）
- 管理の入口・DSL の管理・招待の管理の 403 の扱いを骨組みの S6 に置き換え、テストを直した
- 自分の氏名と言語を画面に当てる口 useApplyOwnProfile（U5 が使う）
- 実際のブラウザの検査 130（20 組）と README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## 10. コミットと統合のハッシュ（Step 16・17 で記入）

| 区切り | ハッシュ |
|---|---|
| C3 |  |
| C4 |  |
| C5 |  |
| C6 |  |
| R2 |  |
| R3 |  |
| `develop` の U2 の squash |  |
| `develop` の U4 の squash |  |
| `develop` の記録のコミット |  |
