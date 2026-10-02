# Code Summary — U5 利用者の管理の画面（u5-user-admin-ui）

U5（B5、この Intent の5つ目で最後の Bolt）では、管理者が利用者の一覧で状態（管理者の印・利用停止・ロック）を確かめ、利用者を探し、行ごとに5つの操作（印を付ける・外す、止める・停止を解く、ロックを解除＝失敗回数を戻す）と氏名・言語の変更を行う画面（`frontend/src/features/useradmin/`）を作った。あわせて次を行った。

- `frontend/.npmrc` に `ignore-scripts=true` を足した（C1 `17af97d`）。
- make-you-chic-ui の固定先を `077f5b4` から `3d9521a` に上げた（C2 `364e9d6`。計画の `3481488` からの差は 6節）。
- `features/preferences/fieldErrors.ts` を `src/shared/api-client/` へ移した。
- E2E の代表の流れ 110 と、実際のブラウザの検査・画面の時間の測り 120 を足した。
- E2E の設定（`frontend/playwright.config.ts`）と報告の部品（`frontend/playwright-secret-check-reporter.ts`）を直した。
- `README.md` を直した。

`backend/` は変えていない。`vendor/make-you-chic-ui` の中身も変えていない。新しい API・依存・スキーマ・設定は無い。

パスはリポジトリのルートからの相対パスで、`frontend/src/features/useradmin/` の下を `useradmin/...` と書く。経過と実測の詳しい値は `generation-notes.md`（回1〜回5 とその続き、N-1〜N-25）にある。作った・移した・変えたファイルの一覧は `source-manifest.json`（56 件）、要件との対応は `traceability.json` にある。Step 22（B5 の関門）・Step 23（統合）の結果と、C3〜C6・R2 以降のコミットのハッシュは、この記録の後に `generation-notes.md` に書く。make-you-chic-ui の N-19 の直しを取り込む C2′ の後の差も、`generation-notes.md` に書く。

## 1. 作ったもの・移したもの・変えたもの

### 1.1 リポジトリの作業（コミット済み）

| 置き場 | 中身 | コミット |
|---|---|---|
| `frontend/.npmrc` | `ignore-scripts=true` と説明のコメントを足した。`engine-strict=true` は残した | C1 `17af97d` |
| `vendor/make-you-chic-ui` | 固定先を `077f5b48ce84cd020ecec2d925836a085f9d9e11` から `3d9521aa54b1d6277de473f9e935a496fb56ac1b` に上げた。`3481488`（Dropdown の押せない項目と理由の文）と `3d9521a`（押せない項目のホバー・フォーカスのコントラストと読み上げの重複の直し）を取り込む。中身は変えていない | C2 `364e9d6` |

### 1.2 本体（`frontend/src`）

| 置き場 | 新しい・移す・変える | 中身 |
|---|---|---|
| `src/shared/api-client/fieldErrors.ts` | 移す | `features/preferences/` から移した。口とふるまいは変えず、説明文に移した理由を1行足した |
| `src/features/preferences/usePreferencesForm.ts`・`usePasswordChangeForm.ts` | 変える | 読み込み先の1行ずつ |
| `useradmin/api/types.ts`・`api/userAdminApi.ts` | 新しい | C3 の型と API の受け渡しを置いた。一覧の本文は C3 の項目だけを写す。`lockedUntil` は `null` と「無い」をどちらも「持たない」に写す（D-4） |
| `useradmin/rowActions.ts`・`searchInput.ts`・`profileInput.ts`・`lockedUntil.ts`・`failureMessage.ts`・`focusTarget.ts` | 新しい | 純粋な関数を置いた（D5・D6・D8・D15・D16・D18、W4・W12） |
| `useradmin/messages.ts`・`useUserAdminText.ts` | 新しい | ja・en の文言を置いた。言語の引数で文言を引ける（D17、D-5） |
| `useradmin/UserSearchBox.tsx`・`UserTable.tsx`・`UserRowActions.tsx`・`ConfirmActionDialog.tsx`・`EditProfileDialog.tsx` と CSS 4（`UserSearchBox.css`・`UserTable.css`・`EditProfileDialog.css`・`UserAdminPage.css`） | 新しい | 画面の部品（FC 4節）。`UserTable.css` の `.useradmin-no-mark { position: relative; }` は N-18 |
| `useradmin/useUserAdmin.ts`・`UserAdminPage.tsx`・`registration.ts` | 新しい | 画面の組み立て（FC 3節・6節）。`/admin/users`・`lazy`・`SHELL`・`ADMIN`、サイドバーは `order: 230` |

### 1.3 テストとテストの補助

| 置き場 | 件数 |
|---|---|
| `src/shared/api-client/fieldErrors.test.ts`（移したもの） | 8（移す前と同じ件数。N-5 でプリファレンスの読み込みを外した） |
| `useradmin/rowActions.test.ts`・`searchInput.test.ts`・`profileInput.test.ts`・`lockedUntil.test.ts`・`failureMessage.test.ts`・`focusTarget.test.ts` | 6・8・6・6・8・5（fast-check は rowActions に4件・searchInput に2件で、既定の 100 回。失敗の種は出ていない） |
| `useradmin/api/userAdminApi.test.ts` | 10 |
| `useradmin/registration.test.tsx` | 5 |
| `useradmin/UserSearchBox.test.tsx`・`UserTable.test.tsx`・`UserRowActions.test.tsx`・`ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx` | 7・9・8・8・8（部品ごとに vitest-axe を1件以上） |
| `useradmin/UserAdminPage.test.tsx` | 58（U4 のレビュー R-04、PD 3.3 の (a)〜(d)、D-5〜D-7、N-25 を含む） |
| `useradmin/testing/fixtures.ts`・`testing/renderUserAdmin.tsx` | テストの補助 |

### 1.4 E2E・設定・文書

| 置き場 | 新しい・変える | 中身 |
|---|---|---|
| `frontend/e2e/110-user-admin-flow.e2e.ts` | 新しい（1 件） | E2E-M9 の代表の流れ（計画 Step 17 の順1〜10）。順9 は Q-B A（D-14） |
| `frontend/e2e/120-user-admin-accessibility.e2e.ts` | 新しい（21 件） | 20 組×12 回の検査（状態 (11) をホバーとフォーカスに分けた）と、既定の1組の画面の時間の測り1件 |
| `frontend/e2e/support/traceMode.ts`・`secretValues.ts`・`userAdminRun.ts`・`userAdminFixtures.ts`・`adminApiRoute.ts`・`userAdminDiagnostics.ts` | 新しい | E2E の土台（D-8） |
| `frontend/e2e/support/preferencesFixtures.ts` | 変える（1行） | `fieldErrors` を移したことに伴う型の読み込みの1行（N-6、依頼者の決定 A） |
| `frontend/playwright.config.ts` | 変える | 報告から html を外した。trace の既定を `off`（`E2E_TRACE`）にした。報告の部品に渡す設定を広げた。冒頭の説明を直した |
| `frontend/playwright-secret-check-reporter.ts` | 変える | SD 3.5 の 1〜10 のとおり広げた。`node:fs`・`node:path`・`node:zlib` と `e2e/support/secretValues.ts` だけを読む |
| `README.md` | 変える | E2E の表に 110・120 を足した。「110 について」「120 について」「結果と報告」を書いた。090 の節の1行を直した |

## 2. 主な決定

- **失敗の入口は1か所**: API を呼ぶ道は3つ（一覧・操作・保存）。どれも `catch` から `handledCommonly(error, path)` を通り、401 → `useAdminForbidden` → 場面ごとの順に扱う。一覧の場面は `handleListFailure` の中で扱う（N-24）。古い読み直しの答えは失敗を含めて捨て、`useAdminForbidden` にも渡さない（D-6）。
- **二重の送信は3つの層で防ぐ**: 表示のボタン、部品の形（`Button` の `loading`、ページ送りの押下を捨てる、`aria-disabled`）、`useUserAdmin` の参照（`loadingRef`・`submittingRef`、`finally` で戻す）の3つ。
- **今のページと検索の文字は 200 のときだけ置き換える**: 機能設計 R-04 のとおり。空のページの補正は1回の読み込みにつき1回まで（R-06）。
- **自分の行の保存の順**: `applyOwnProfile` → Toast（送った言語で引く）→ 読み直し（R-02、D-5）。
- **E2E の報告に値を残さない**: html の報告を作らない。trace は既定で `off`。110 の値は値のファイルに書く。報告の部品は、json（添付と標準出力の base64 は復号する）・`test-results/` の下のすべてのファイル（zip は展開する）・前の `playwright-report/` を値と値の形で探し、見つけたら失敗にする。
- **120 の差し替えの口**: page ごとに1つ置き、同じオリジンで正規化したパスが `/api/admin` ちょうどか `/api/admin/` の下の要求を受ける。GET 以外で見本の無いものは打ち切って記録し、口が受けた件数は 1 以上でなければならない。
- **画面の時間は記録のみ**: 120 の測りは成否にしない。本番での判定は `Unverified`（基盤の設計の R-01）。

## 3. テストの件数とカバレッジ（生成の時点の実測）

| 確かめ | 結果 |
|---|---|
| `useradmin` の画面のテスト | 14 ファイル・152 件すべて成功 |
| U5 のまとめの実行（`unit-test-instructions.md` 2.12） | 24 ファイル・249 件すべて成功 |
| 画面のテストの全体（`npm run test:coverage`） | 109 ファイル・953 件すべて成功（B2 の基準 95 ファイル・801 件） |
| 画面の全体のカバレッジ | 行 97.43%（2882/2958）・分岐 92.85%（1870/2014）。下限（行 80%・分岐 70%）を満たす。B2 の基準は 行 97.61%・分岐 93.01% |
| `useradmin` の下の合計（`testing/` を含む） | 行 96.49%（468/485）・分岐 92.17%（365/396）。本体だけでは 行 96.38%（453/470）・分岐 91.84%（349/380） |
| 目安（行・分岐とも 90%）を下回るファイル | 分岐だけが下回る4つ: `useUserAdmin.ts` 85.06%・`UserRowActions.tsx` 83.33%・`lockedUntil.ts` 87.5%・`profileInput.ts` 75.0%。理由は `generation-notes.md` 回3（部品の側の守りで画面からは届かない参照の守り、`??` の右辺）。下限は満たす |
| `fieldErrors.ts`（移したもの） | 行・分岐とも 100% |
| `npx playwright test --list` | 110 が 1 件、120 が 21 件、全体が 13 ファイル・152 件 |
| 初回の JavaScript（gzip） | (1) 固定先を上げる前 122.7 KB → (2) `364e9d6`（固定先を上げた後、U5 の画面の前）122.8 KB。参考に、U5 の画面を含む作業フォルダは 125.5 KB。確定の (3) は Step 22 で測る |

バックエンドは変えていないため、バックエンドの件数とカバレッジは Step 22 の `:backend:cleanTest :backend:cleanIntegrationTest verify` で記録する（C1 の後の実測は、単体 1506・結合 689、行 98.90%・分岐 94.83%）。

## 4. 確かめた時点（`traceability.json`）

`traceability.json` の内訳は次のとおり。

- upstream_ids は 116。内訳は、機能設計の AC 67（AC1.1.1〜AC5.1.8、AC3.2.9・AC3.2.10 を含む）、NFR 設計の NFR 28（NFR1.1〜NFR9.9）、機能設計の決まり D1〜D20、E2E-M9。
- 判定は OK 63・Deferred 53・GAP 0。OK の target はすべて実在するファイル1つ（`vendor/make-you-chic-ui` は gitlink）。
- Deferred の内訳:
  - u3-user-admin-api: サーバーの判定・監査・401／403／200 の AC。
  - u4-admin-forbidden-ui: AC2.2.1〜AC2.2.5。AC2.2.6 は u4・u3。
  - u1-user-suspension: AC3.2.9・AC3.2.10。U5 は画面の流れを 110 の順7 で確かめた。
  - performance-validation・observability-setup・feedback-optimization: NFR5.1・NFR5.2（記録のみ・本番での判定は Unverified）。

OK のうち、次は生成の段（Step 16・18）で流して確かめた。合否の確定は Step 22 で行う。

| ID | target | 確かめた時点と結果 |
|---|---|---|
| E2E-M9・NFR9.8 | `frontend/e2e/110-user-admin-flow.e2e.ts` | Step 18 で成功。`skip-reason` は無し。全体の確定は Step 22 の `./gradlew e2eTest` |
| NFR7.3・NFR9.1 | `frontend/e2e/120-user-admin-accessibility.e2e.ts` | Step 18 で 20 組×12 回の検査を流し、axe の違反 0・はみ出し 0・CSP の違反 0・残りの問題 0 |
| NFR3.5 | `frontend/e2e/support/adminApiRoute.ts` | Step 18 で、各組とも受けた件数 9・打ち切り 0。わざと失敗させる3つの形（SD 4.3）も期待どおりに失敗した |
| NFR3.4・NFR9.2 | `frontend/playwright-secret-check-reporter.ts` | Step 16 で、わざと値を入れた確かめ (1)〜(8) と追加の3つがすべて期待どおり。Step 18 で見つかった件数 0 |
| NFR9.9 | `frontend/e2e/support/userAdminFixtures.ts` | Step 18 で、110 の一覧・409・400 の本物の応答と見本の項目の名前と型が一致した |
| NFR9.3 | `vendor/make-you-chic-ui` | Step 4 で `3481488`、回3 の続きで `3d9521a` のソースを確かめた。Step 4 の E2E 130 件が成功。`vendorUnchanged` は Step 22 で確かめる |
| NFR9.4 | `frontend/.npmrc` | Step 3 で `npm ci`・verify・E2E が成功 |
| NFR9.5 | `frontend/vitest.config.ts` | 3節の値。確定は Step 22 の verify |
| NFR5.6 | `frontend/scripts/check-bundle-size.mjs` | (1)・(2) は 3節。(3) は Step 22 |

FC 2.5 の表（固定先を上げた直後の Dropdown の形）の結果は `generation-notes.md` 回2。押せない項目の押下・`aria-disabled`・理由の文・押せない項目へのフォーカス・開き口は期待どおりだった。`3481488` で見つかった N-3（理由の文が名前にも入る）と N-4（light のホバー・フォーカスで 4.39:1）は、依頼者が make-you-chic-ui の側で直し、`3d9521a` で直った（light 4.83:1・dark 9.96:1、背景は変わらない）。

## 5. 静的検査と構造（Step 19）とコードのレビューでの確かめ（Step 20）

- 型の検査（`e2e` を含む）・Prettier・oxlint・ESLint・Stylelint・ライセンスヘッダーは、すべて通った。除外は足していない。全体の `format:check`・`oxlint .`・`eslint .` も終了コード 0 だった。
- `E2E_TRACE=bogus` では設定の読み込みで止まり（終了コード 1）、無指定では `off` になる。`--list` で html の報告は作られない。
- 画面の側の境界:
  - `features/useradmin` はほかの機能を読み込まない。
  - `src/shared`・`src/app` から `features/useradmin` への読み込みは無い。
  - E2E が画面のコードを読むのは `import type` の1か所だけ。
- `21a2fdd` と比べて差分が無いことを確かめた（計画 4.5）。例外は、`frontend/e2e/support/preferencesFixtures.ts` の1行（N-6）と、`vendor/make-you-chic-ui` の gitlink だけ。
- Step 20 の7項目（失敗の入口の1か所、画面の外に出さない、文言と項目、参照の戻り、自動の読み直しが無い、報告と資格情報、8KB を超える要求）は、すべて計画のとおりだった。方法と結果は `generation-notes.md` の回5 にある。

## 6. 計画・承認済みの文書との差

承認済みの計画と設計の文書は書き換えていない（`project.md` の決まり）。

| ID | 差 | 扱い |
|---|---|---|
| 固定先 | 計画の `3481488` ではなく `3d9521a` に上げた（N-3・N-4 を直した版） | 依頼者の決定（回2 の後・回3 の続き） |
| D-14 | 110 の順9 だけ、ログインの応答の `user.admin` を書き換える（FS 9節の「何も差し替えない」との差） | 依頼者の決定 Q-B A（計画の承認の前） |
| N-1 | Step 2 の `--list` が html・json の報告を作った。記録してから消した | 受け入れ（回2 の前） |
| N-2 | Gradle の `frontendInstall`・`vendorInstall` の入力に `.npmrc` が無い（UP-TO-DATE になる）。`build.gradle.kts` は変えていない | 記録だけ（依頼者の決定） |
| N-5 | `fieldErrors.test.ts` はプリファレンスの `formChecks` を読まず、項目の一覧をテストの中に持つ形にした | 直す（依頼者の決定） |
| N-6 | `e2e/support/preferencesFixtures.ts` の型の読み込みの1行を直した（計画 4.5 との差） | 案 A（依頼者の決定） |
| N-7 | 移すのに `git mv` ではなく `mv` を使った（`git add` しない決まりのため） | コミットのときに移しとして記録される見込み |
| N-8 | 検索の説明の文と誤りの文を `UserSearchBox` が自分で描き、`aria-describedby` に誤り → 説明の順で結ぶ | 受け入れ（依頼者の決定） |
| N-9 | 言語の誤りは `RadioGroup` のすぐ下に `role="alert"` で出す（まとまりに結ぶ口が無い） | 受け入れ（依頼者の決定） |
| N-10 | `useUserAdmin({ api, t, reportForbidden, applyOwnProfile })` の形にした（FC 3節の断片 `useUserAdmin(api, t, language)` との差）。画面の言語は画面が使う | 承認の場で確かめる（8節） |
| N-11 | 部品の props の小さな違い。`UserTable` の labels は中で作り、`label`・`now` を受ける。`UserRowActions` は `containerRef` を受ける。確かめの表示は `role="alertdialog"` | 承認の場で確かめる（8節） |
| N-12・N-13・N-14 | 読み直しが重なる道を保存の 500 の2回で作った。開いたメニューの vitest-axe は WCAG の A・AA の規則で行った（`region` の規則を外す。ShellLayout と同じ）。`useAdminForbidden` に渡すパスを `vi.mock` で包んで記録した | テストの作り方の記録。承認の場で確かめる（8節） |
| N-15 | Step 4 の確かめ（`npm ci`・画面のテスト・E2E）を、U5 の画面の変更を含む作業フォルダで流した。初回の大きさ (2) だけは `364e9d6` から作り直して測った | 受け入れ（依頼者の決定） |
| N-16 | 報告の部品が `secretValues.ts` を拡張子 `.ts` つきで読む（使い捨ての台本を Node の型の取り除きで流すため）。氏名「計測 花子」の値を `secretValues.ts` にも重ねて置いた | 承認の場で確かめる（8節） |
| N-17 | `E2E_TRACE` の誤りの知らせに、指定された値を出す（秘密ではないため） | 承認の場で確かめる（8節） |
| N-18 | 375px で画面全体が横にはみ出していたため、`UserTable.css` に1つの規則（`.useradmin-no-mark { position: relative; }`）を足した | 受け入れ（依頼者の決定） |
| N-19 | 実際のブラウザで、確かめ・入力の表示を「やめる」で閉じた後のフォーカスが行の「操作」に戻らず body になる（FS W5 の 2 との差）。make-you-chic-ui の `useFocusTrap` が、`ModalStack` が `inert` を外す前に戻そうとするため。E2E は `inert` が外れるのを待つ形にした | make-you-chic-ui の側で直す（依頼者の決定、依頼の文書は `make-you-chic-ui-request-3.md`）。直った版へは C2 とは別の専用のコミット C2′ で上げる。**未決**: 7節・8節 |
| N-20 | axe は `aria-disabled` の項目の文字をコントラストの検査から外すため、120 の状態 (11) は押せない項目の文字のコントラストを判定しない（計画 D-3 の「120 の状態 (11) で確かめる」との差）。代わりに、ホバーしても背景が透明のままであることをブラウザで確かめ、計算した比が当たることを確かめた | 受け入れて記録だけ（依頼者の決定） |
| N-21 | axe の incomplete（color-contrast・bypass）の件数を注記に残した（違反ではない） | 承認の場で確かめる（8節） |
| N-22 | 120 の状態 (9) の後は、「もう一度読み込む」が前の検索の文字のまま読む（R-04 のとおり）ため、「検索を消す」で一覧へ戻す手順にした | 承認の場で確かめる（8節） |
| N-23 | 110 で、確かめの表示の題を見出しではなく `alertdialog` の名前で探した | 承認の場で確かめる（8節） |
| N-24 | 失敗の入口の名前が、計画の `handleFailure` ではなく `handledCommonly`・`handleListFailure`。順と「すべての道が通る」形は同じ。説明文の古い名前は直した | 受け入れ（依頼者の決定） |
| N-25 | 保存の code の無い 400 を確かめる画面のテストを1件足した | 依頼者の決定 |
| README | 計画 4.4 の項目に加えて、090 の節の「HTML の報告とトレースにリンクが載る」の1行を、今の設定（html を作らない、トレースは手元で有効にしたときだけ）に合わせて直した | 承認の場で確かめる（8節） |

### 依頼者の決定（計画の承認の前と生成の途中）

| 論点 | 決定 |
|---|---|
| Q-A（`.npmrc` と固定先の順） | A: `.npmrc`（C1）を先にする |
| Q-B（画面と本物のサーバーを通した 403 の確かめ） | A: 110 の順9 で U のログインの応答の `user.admin` を書き換える |
| Q-C（8KB を超える要求の HTML の 400） | A: 残る危険として受け入れて記録する。code の無い 400 は一般の失敗として扱う（一覧・操作・保存の画面のテストで確かめた。保存は N-25 で足した） |
| Q-D（`playwright.config.ts` の直しの時点） | A: Step 15 で直す。Step 3・4 の E2E は今の設定のまま流し、記録してから報告を消す |
| N-3・N-4 | make-you-chic-ui の側で直す（`3d9521a` で直った） |
| N-5・N-6・N-8・N-9・N-15・N-18・N-20・N-24・N-25 | 6節の表のとおり |
| N-19 | make-you-chic-ui の側で直し、直った版へ C2′ で上げる。直るまでは今の E2E の形のままでよい |

## 7. 依頼者に確かめたいこと

### 決定済み（記録のため）

- N-5・N-6・N-8・N-9・N-15・N-18・N-20・N-24・N-25 の扱い（6節の表）。
- N-19 の直し方（make-you-chic-ui の側で直し、C2′ で上げる。直るまでは今の E2E の形）。

### 未決

- **N-19**: make-you-chic-ui の直し待ち。直った版へ C2′ で上げ、閉じた後のフォーカスを E2E で確かめる形に替えるかは、その時に決める（C2′ の後の確かめと差は `generation-notes.md` に記録する）。

## 8. 承認の場で確かめること

### 決定済み（承認の場で結果を見るもの）

- Step 22 の関門の結果:
  - `verify`（バックエンドの件数とカバレッジ、画面の全体と U5 のファイルの行・分岐、初回の大きさ (3)）。
  - `osvScan --rerun-tasks`。
  - E2E の 13 ファイル・152 件（110 に `skip-reason` が無いこと、120 の組と状態ごとの結果、`user-admin-screen-ms`、報告の部品の見つかった件数 0、`playwright-report/` を作らないこと、報告を消したこと）。
  - この記録の時点では未実施。
- 4節の、Step 22 で確定する OK（E2E-M9・NFR7.3・NFR9.1・NFR3.4・NFR3.5・NFR9.3・NFR9.5・NFR5.6・NFR9.8）。
- 画面の時間（生成の時点の Step 18 の実測）は、記録のみで本番での判定は `Unverified`。
  - list: 1回目 137、2〜5回目 104・101・101・102 ミリ秒（目標 2000）。本物の一覧の API を差し替えの口を通して読む時間で、口の上乗せを含む。
  - nextPage: 1回目 84、2〜5回目 79・79・77・78 ミリ秒（目標 1500）。見本の応答での描画の時間で、API の時間を含まない。
  - Step 22 の値で置き換える。

### 未決（承認の場で受け入れを確かめるもの）

- **N-19**: make-you-chic-ui の直し待ち。直った版へ C2′ で上げ、閉じた後のフォーカスを E2E で確かめる形に替えるかは、その時に決める。直るまでの間、FS W5 の 2（閉じた後のフォーカスを行の「操作」へ戻す）は、実際のブラウザでは満たしていない（画面のテストは jsdom のため通る）。
- 依頼者の決定をまだ得ていない計画・設計との差: N-10・N-11（作りの形の差）、N-12〜N-14・N-21〜N-23（テストの作り方の記録）、N-16・N-17（E2E の土台の小さな決定）、README の 090 の節の1行。
- `UserRowActions.tsx`・`lockedUntil.ts`・`profileInput.ts`・`useUserAdmin.ts` の分岐が、記録のための目安の 90% を下回る（下限は満たす）。

## 9. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」のとおり。

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジと件数 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、画面の全体と U5 のファイルの行・分岐、バックエンドの全体を実測して記録する（NFR9.5） | Build and Test |
| CI | 依頼者の push の後に CI（サブモジュールの取得を含む）が通ることを確かめる。失敗は `team.md` の「不安定なテストと CI の失敗」で扱う。サブモジュールの取得の失敗は CP 5節の 6 | Build and Test |
| E2E | `./gradlew e2eTest`（13 ファイル・152 件）を流し、110 の `skip-reason` が無いこと、120 の組と状態ごとの結果、報告の部品の結果（見つかった件数 0）を記録する。json の報告は記録の後に消す | Build and Test |
| 画面の時間 | `user-admin-screen-ms` を json の添付（base64 を復号）から写す。`nextPage` は API の時間を含まないこと、`list` は口の上乗せを含むことを書く。記録のみで、本番での判定は `Unverified` | Build and Test（記録）・performance-validation・observability-setup・feedback-optimization（判定） |
| API の時間 | 一覧・氏名と言語・5つの操作の p95 は U3 の目標のまま。U5 は測り直さない（NFR5.3） | performance-validation（U3） |
| 初回の JavaScript の大きさ | (1) 122.7 KB・(2) 122.8 KB と、Step 22 の (3) を写す。目安を超えても警告だけ（NFR5.6） | Build and Test |
| スモークテスト | 配備の後のスモークテストの U5 の分は、一覧が開けること。操作を入れるかは決め、行うときは先に依頼者に伝える（監査に残り、利用者の状態を変える） | deployment-pipeline |
| 閉じた後のフォーカス（N-19） | make-you-chic-ui の直しを C2′ で取り込んだ後に、E2E で確かめる形に替えるかを決める | B5 の後の手順・依頼者 |
| 送信中に通信が止まったときの抜け出し | 画面の中に抜け出しの手は無く、再読み込みで抜ける（PD 3.4 の残る危険） | 後の Intent |
| 8KB を超える要求 | Q-C の決定のとおり記録する | Build and Test（記録） |

## 10. コミットの区切り

コミットの区切り（C3〜C6・R2・R3・R4）は計画 3.3 のとおりで、依頼者の承認を得てから行う（生成の担当はコミットしない）。N-19 の直しを取り込む C2′ は、C2 と同じ形の専用のコミットにする（更新の前後の完全なハッシュを本文に書く）。コミットと統合のハッシュ、C2′ の後の差は `generation-notes.md` に記録する。
