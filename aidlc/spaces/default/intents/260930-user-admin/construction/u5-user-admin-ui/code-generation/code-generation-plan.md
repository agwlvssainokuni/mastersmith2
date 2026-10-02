# Code Generation Plan — U5 利用者の管理の画面（u5-user-admin-ui）

U5 のコード生成の計画を示す。Bolt は B5 利用者の管理の画面（`inception/delivery-planning/bolt-plan.md`、この Intent の5つ目で最後の Bolt）で、単位は U5 だけ。作業ブランチは `develop` から作る `feature/260930-user-admin-b5`。

U5 は画面（ui）の単位で、管理者が利用者の一覧で状態（管理者の印・利用停止・ロック）を確かめ、利用者を探し、行ごとに5つの操作（印を付ける・外す、止める・停止を解く、ロックを解除＝失敗回数を戻す）と氏名・言語の変更を行う画面（`frontend/src/features/useradmin/`）を作る。あわせて、make-you-chic-ui の固定先を `077f5b4` から `3481488` へ上げ、`frontend/.npmrc` に `ignore-scripts=true` を足し、`features/preferences/fieldErrors.ts` を `src/shared/api-client/` へ移し、E2E の代表の流れ 110・実際のブラウザの検査と画面の時間の測り 120 を足し、E2E の設定（`frontend/playwright.config.ts`）と報告の部品（`frontend/playwright-secret-check-reporter.ts`）を直す。`backend/` は変えない。新しい API・依存・スキーマ・設定は持たない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260930-user-admin/`（以下「記録」）とする。画面のファイルは `frontend/src/` の下を `src/...`、`frontend/src/features/useradmin/` の下を `useradmin/...` と書くことがある。設計の文書の略号は次のとおり: FS（`construction/u5-user-admin-ui/functional-design/functional-spec.md`）、FC（同 `frontend-components.md`）、NS・NP・TS（`nfr-requirements/` の `security-requirements.md`・`performance-requirements.md`・`tech-stack-decisions.md`）、SD・PD・LC（`nfr-design/` の `security-design.md`・`performance-design.md`・`logical-components.md`）、IS・CP・MD（`infrastructure-design/` の `infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`）。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| FS・FC・`functional-design/traceability.json` | 用語（FS 1節）、置き場と登録（FS 2節）、決まり D1〜D20（FS 3節）、状態の移り変わり（FS 4節）、流れ W1〜W12（FS 5節）、応答ごとの動き（FS 6節）、文言（FS 7節）、テストの方針（FS 8節）、E2E の代表の流れ E2E-M9（FS 9節）、上流との差 (a)〜(k)（FS 10節）、承認の場の決定 R-01〜R-08。部品の階層（FC 1節）、モジュールの一覧（FC 2.1）、共通のもの（FC 2.2）、U4 の口（FC 2.3）、固定先を上げた直後に確かめる表（FC 2.5）、`useUserAdmin`（FC 3節）、props と state（FC 4節）、API との受け渡し（FC 5節）、登録（FC 6節）、入力の確かめ（FC 7節）、テストで確かめる内容（FC 8節）、既存への影響（FC 9節） |
| NS・NP・TS・`nfr-requirements/traceability.json` | NFR1.1・NFR1.2・NFR3.1〜NFR3.5・NFR5.1〜NFR5.6・NFR7.1〜NFR7.3・NFR8.1〜NFR8.3・NFR9.1〜NFR9.9、承認の場の決定 R-01〜R-05 と申し送り（報告の部品・120 の差し替えの口・固定先と `.npmrc`） |
| SD・PD・LC・`nfr-design/traceability.json` | 境界と守り（SD 1節）、判定（SD 2節）、報告に値を残さない（SD 3節。設定・値のファイル・探し方・json の base64 の復号・zip・前の html の報告・診断・trace を手元だけ有効にする手順・わざと値を入れた確かめ）、120 の差し替えの口（SD 4節）、見本と本物の照らし合わせ（SD 5節）、画面の外に出さない・文言・応答の項目（SD 6節）、CSP（SD 7節）、依存（SD 8節）、残る危険（SD 11節）、上流との差 1〜8（SD 12節）、承認の場の決定（R-01〜R-06・受け入れた4点・申し送り）。読み方（PD 2節）、待ちの見せ方と二重の送信（PD 3節）、初回の大きさ（PD 4節）、画面の時間の測り方（PD 5節）。部品の一覧（LC 1節〜3節）、障害の範囲（LC 5節）、アクセシビリティ・多言語・テストの関門（LC 6節〜8節） |
| IS・CP・MD と `construction/infrastructure-design/gate-decisions.md` | 1コマンドの検査の段（CP 2節）、依存・固定先・`.npmrc`（CP 3節）、E2E の置き場・設定の直し・B5 までの報告の扱い・B5 の中で流す時点・わざと値を入れた確かめと戻し忘れの確かめ・README（CP 4節）、統合と配備（CP 5節）、記録すること（CP 8節）、承認の場の決定（R-01〜R-04）と2回目の承認の場の U5 の申し送り（確かめの前の「コミットしておく」は stash か差分だけの形にするか、コミットの前に依頼者の承認を得る） |
| `inception/contract-design/contract-summary.md` の C3・C4・C5 | 一覧・5つの操作・氏名と言語の API の形、403 の共通の扱いと自分の氏名と言語の反映の口、画面のページ送りの口 |
| `inception/delivery-planning/bolt-plan.md` の B5 と共通の完了の条件 | 完了の条件（固定先・`.npmrc`・S1〜S6・自分の氏名と言語・画面部品ごとのアクセシビリティの検査・E2E の代表の流れ）、統合の前の verify と E2E、B5 は fast-forward でよい |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U5 の境界（US1.1・US2.1・US3.1・US4.1・US5.1 の画面の側、US2.2 は共通の扱いを使う）、ストーリーの順（US1.1 → US2.1 → US3.1 → US4.1 → US5.1 → E2E） |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md`・`inception/refined-mockups/` | FR1・FR2・FR3・FR5・FR6・FR8.1・NFR3・NFR7・NFR8・NFR9、受け入れ基準（AC1.1.8〜AC1.1.12・AC2.1.8・AC2.1.9・AC2.1.13・AC3.1.7・AC3.1.8・AC3.1.11・AC4.1.9・AC4.1.10・AC5.1.2・AC5.1.3・AC5.1.7 と E2E の代表の流れ M9 A、AC3.2.9・AC3.2.10 の画面の流れ）、画面 S1〜S6・`interaction-spec.md`・`design-system-mapping.md`・`accessibility-checklist.md` |
| 前の単位の記録 | U4: `construction/u4-admin-forbidden-ui/code-generation/code-generation-plan.md`・`unit-test-instructions.md`（形の手本）、`generation-notes.md` のレビューの R-02・R-04 の申し送りと N-16（既存の E2E の期待を洗い出していなかった）、`code-summary.md`（U5 が使う口、`ApplyOwnProfileInput`）。U2: `construction/u2-shared-paging/code-generation/code-summary.md` 9節（`UiPaging` と `correctedPage`、文言の鍵は U5 が持つ、8KB を超える要求の HTML の 400）。U3: `construction/u3-user-admin-api/code-generation/code-summary.md`（一覧・氏名と言語・5つの操作の実際の応答、BUSY・確かめ直しの 403、B4 の関門の実測） |
| 決まり `aidlc/spaces/default/memory/team.md`・`project.md` | 作業の場・統合（サブモジュールの更新を含むため fast-forward でよい）・コミット・push は依頼者、Testing Posture（画面のテスト・アクセシビリティ・`waitFor`・時間の上限・性質ベースのテスト・E2E の本数と初期管理者・テストのデータ）、Code Style（名前つきの export・`enum` を使わない・ライセンスヘッダー・素の CSS・`src/features` と `src/shared` の境界・`ignore-scripts=true`）、Change Control（コミットは提案して承認を得る）、Forbidden（make-you-chic-ui の中身を変えない・メールアドレスをログに出さない・API の応答の秘密）・Mandated（固定先の更新は専用のコミットで前後のハッシュ・利用者の状態の判定はサーバー側・lockfile）、学び（承認の場の決定の洗い出し、Playwright の webServer.env の値が json に残る、axe は誤りの状態と現実に近いデータで全組、`caffeinate -i` は台本全体、長い依頼は分ける） |

既存のコード（読むだけで確かめた）: `frontend/package.json`（スクリプトと依存。`make-you-chic-ui` は `file:` の依存）・`.npmrc`（今は `engine-strict=true` だけ）・`vitest.config.ts`（`thresholds` 行 80・分岐 70、除外は入口と型の宣言とテスト）・`playwright.config.ts`（reporter に list・html・json・報告の部品、`trace: 'retain-on-failure'`、資格情報はプロセスの環境変数、`workers: 1`）・`playwright-secret-check-reporter.ts`（json の報告から環境変数3つの値を文字列で探すだけ）、`src/app/registry/registrationModules.ts`（`features/*/registration.ts` を `import.meta.glob` で読む。登録のために骨組みを書き換えない）・`src/features/invitation/`（`registration.ts`・`messages.ts`・`useInvitationText.ts`・`failureMessage.ts`・`focusTarget.ts`・`api/invitationApi.ts`・`testing/`）・`src/features/preferences/fieldErrors.ts`（読み込むのは `usePreferencesForm.ts`・`usePasswordChangeForm.ts`・`fieldErrors.test.ts` の3つ）・`src/shared/paging/paging.ts`（`PAGE_SIZE`・`pageCount`・`pageRange`・`correctedPage`・`pagerButtonDisabledAfter`・`PagerDirection`）・`src/shared/validation/`（`validateDisplayName`・`trimDisplayName`・`countCodePoints`・`DISPLAY_NAME_MAX_CODE_POINTS`）・`src/shared/format/formatDateTime.ts`（`formatDateTime(iso, language, timeZone?)`）・`src/shared/api-client/apiError.ts`（`toApiError` は本文が JSON で `code` が読めなければ `code` を持たない `kind: 'response'`）・`src/app/admin-forbidden/AdminForbiddenProvider.tsx`（`useAdminForbidden(): (error, apiPath) => boolean`）・`src/app/display-settings/useApplyOwnProfile.ts`（`ApplyOwnProfileInput`）・`src/app/testing/renderWithProviders.tsx`、`frontend/e2e/`（010〜100・130 と `support/`。`registeredUser.ts` の `createRegisteredUser`・`REGISTERED_DISPLAY_NAME`（「計測 花子」）・`newRunPassword`（`e2e-u7-pw-` と 24 文字の16進）・`loginWithForm`・`openUserMenuItem`（`app-shell` の中の `dropdown-trigger` を押す）、`invitationSeed.ts` の `newRunTag`（14 文字ほど。110 は使わない）・`requestAdminAccessToken`、`loginPreferences.ts`（ログインと復元の応答の `user` の項目を書き換える形）、`pageProblems.ts`・`axe.ts`・`displayCombos.ts`・`overflow.ts`・`adminLogin.ts`）、`backend/src/main/java/cherry/mastersmith/useradmin/web/`（`AdminUser`・`AdminUserPage`・`ProfileRequest`・`UserAdminController`。`lockedUntil` は Jackson の既定で、無いときは `null` の値として本文に出る。`NON_NULL` の設定は無い）・`useradmin/domain/UserAdminProblemTypes.java`（6つの code）、`build.gradle.kts`（`e2eTest` は `:backend:bootWar` に依存し、始める前に Mailpit に届くかを確かめる）、`.gitignore`（`frontend/test-results/`・`frontend/playwright-report/`）、`README.md`（「ビルドした WAR での画面の確認（E2E）」の表と 130 の節）、`vendor/make-you-chic-ui`（固定先 `077f5b4`。`Table` の `labels`・`onPageChange`・`getRowId`、`Modal` の `initialFocusRef`・`closeLabel`・`closeOnBackdropClick`、`Button` の `loading`、`Toast` は既定 4000 ミリ秒で消える）。

固定先の候補の確かめ（計画の承認の前、TS の NFR9.3 の求め）: このリポジトリの `vendor/make-you-chic-ui` の作業フォルダには `3481488` が取り込まれていない（取り込みは `git fetch` で、書き込みのため行っていない）。リポジトリの外の make-you-chic-ui の作業フォルダ（`../make-you-chic-ui`）を読み取りだけで確かめた。結果は 8節の D-3。

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログと各文書の「承認の場の決定」の節から洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ `audit/sakura-local-4e42a93f87ce.md` の各段の `GATE_REJECTED`（Request Changes の理由）・`GATE_APPROVED` と、U5 の各文書の終わりの「承認の場の決定」の節、`gate-decisions.md` を読んだ。U5 と B5 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes、R-01 Major） | 行の「操作」は `Button` の `loading` で押しても開かない形（フォーカスは残す）。[検索]・[検索を消す] は `aria-disabled`。ページ送りは `onPageChange` の側で捨てる。二重の送信は表示のボタン・部品の形・`useUserAdmin` の参照の3つで防ぐ。`pagerButtonDisabledAfter` はフォーカスの移し先を決める道具 | そのとおり作り、3つの層ごとのテストを置く | Step 11〜14 |
| 機能設計（R-02） | 自分の行の保存の順は `applyOwnProfile` → Toast → 読み直し。Toast は送った言語を明示して引く（i18next の `lng`）。en に直した直後の Toast が英語 | `useUserAdminText` に言語の引数を置き、テストで `waitFor` で確かめる | Step 7・8・13・14 |
| 機能設計（R-03） | 押せない項目の形は固定先を上げた直後に FC 2.5 の表で確かめる。Modal の `aria-labelledby`・`aria-describedby` は付け直さず、本文の先頭に対象と効き目の文 | 2.5 の表は計画の前にソースで確かめ（8節の D-3）、Step 4 で固定先を上げた後にテストで確かめ直す | Step 4・11・12 |
| 機能設計（R-04） | 今のページと検索の文字は応答 200 のときだけ置き換える。400 のときは送る前の値のまま | `load(target)` の形にし、3ページ目で検索が 400 の後の [次へ] が 4ページ目を読むテストを置く | Step 13・14 |
| 機能設計（R-05） | 401 では S3・S4 をどちらも閉じる | `handleFailure` の最初の分かれ道にする | Step 13・14 |
| 機能設計（R-06） | empty-page は競合のときだけ。D10 の読み直しは1回の読み込みにつき1回まで | 「補正した」印を `load` の中に持ち、空の応答が2回続くテストを置く | Step 13・14 |
| 機能設計（R-07・受け入れ） | `traceability.json` に E2E-M9 と AC3.2.9・AC3.2.10 を足した。U2 の置き場は `src/shared/paging/paging.ts` | コード生成の `traceability.json` で E2E-M9 を 110 に結ぶ | Step 21 |
| 機能設計（R-08、申し送りは Build and Test） | 統合の前の E2E で、代表の流れが飛ばされていないこと。U のプリファレンスを開くと 401 になること。解除の予定の時刻の確かめは文言の形に固定しない | B5 の関門でも飛ばした注記が無いことを記録する（Build and Test の前の確かめを兼ねる）。110 の確かめは時刻の形に固定しない | Step 17・22 |
| 機能設計（差 (a)・(b)・(d)・E2E を飛ばす扱い、受け入れ） | 検索の上限は送る前に数える、日時は `formatDateTime` の書式、`aria-haspopup` は `'true'` のまま、前提が無ければ理由の種類だけ注記して飛ばす | そのとおり作る | Step 7・11・17 |
| NFR 要件（Request Changes、R-01 Major） | 報告に値を残さない確かめを 110 の U の値と `runTag` に広げ、json・html・trace を対象に、110・120 は trace を残さず、locator の名前と期待値に値を入れない | NFR 設計の形（html を作らない・trace の既定 `off`・値のファイル・探す先を広げる）で作る | Step 15〜17 |
| NFR 要件（R-02 Major） | 120 は見本で返さなかった `/api/admin/` の下の GET 以外の要求を本物へ通さず打ち切り、0 件を確かめる | 差し替えの口の手伝いで作り、各テストの終わりに確かめる | Step 15・17・18 |
| NFR 要件（R-03〜R-05） | 測り始めはホームでサイドバーの項目を選んだ時点、1回目を分けて記録。120 の状態を 11 に。409・400 の見本を 110 で本物と照らし合わせる。固定先の具体のコミットを計画で決める。`.npmrc` は `frontend/` のインストールに効く範囲で、固定先の更新と別のコミット | そのとおり作る。固定先は `3481488`（8節の D-3）。2つのコミットの順は 9節の Q-A | Step 3・4・15・17 |
| NFR 要件（申し送り、監査ログ「U5 の固定先」「E2E の見本の置き場」） | 固定先と見本の置き場は計画で決める | 見本は `frontend/e2e/support/userAdminFixtures.ts` に置く（8節の D-8） | Step 15 |
| NFR 設計（Request Changes、R-01 Major） | 報告の部品は json の報告を JSON として読み、`attachments[].body` と `stdout`・`stderr` の `buffer` の base64 を復号して探す。読めない・復号できなければ失敗 | そのとおり作り、わざと値を入れた確かめの (7) を行う | Step 15・16 |
| NFR 設計（R-02〜R-06） | 口が受けた件数 1 以上の確かめ、同じオリジンの正規化したパスでの判定、page ごとに張る。参照（`loadingRef`・`submittingRef`）は `finally` で戻し、戻ることをテストで確かめる。口は `page.goto` とログインより前に張る。`nextPage` は API の時間を含まないと記録する。`runTag` の乱数の部分は 16 文字以上、24 文字以上のときだけ単独で探す | そのとおり作る | Step 13〜18 |
| NFR 設計（受け入れた4点） | 前の html の報告が残っていれば B5 の最初の実行で一度失敗しうる。110・120 の trace は `e2eTraceMode()`。120 の検査のモードでは見本の無い GET も本物へ通さない。NFR5.1 の値に差し替えの口の上乗せを含める | そのとおり作る。前の html の報告は、B5 の中の設定の直しの前の E2E の後に必ず消すため（下の基盤の設計 R-02）、残っていない見込み。残っていれば結果を記録する | Step 3・4・16・22 |
| 基盤の設計（1回目、U5 R-01 Major） | 画面の時間は記録のみで成否にせず、本番での判定は `Unverified`（持ち主は performance-validation・observability-setup・feedback-optimization） | `user-admin-screen-ms` を記録するだけにし、コード生成の `traceability.json` の NFR5.1・NFR5.2 は Deferred にする | Step 17・21・22 |
| 基盤の設計（1回目、U5 R-02 Major、全単位） | B5 の中で `playwright.config.ts` を直す前の E2E の実行の後は、json の報告から結果を記録してから `frontend/playwright-report/`・`frontend/test-results/` を消し、消したことと共有していないことを記録する | Step 3・4 の E2E の後に行う | Step 3・4 |
| 基盤の設計（1回目、U5 R-03 Minor） | わざと値を入れた確かめと画面の守りの確かめは元へ戻し、コミットの前に `git diff`・`git status` で残っていないことを確かめて記録する。`.npmrc` のコミットを早めに置くかは計画で決める | 確かめはリポジトリの外の使い捨ての台本と、追跡しない一時の E2E のファイルで行う（8節の D-9）。順は 9節の Q-A | Step 3・16・18 |
| 基盤の設計（1回目、U5 R-04 Minor） | push の前に、取り込む make-you-chic-ui のコミットが公開の側にあることを `git branch -r --contains <コミット>` か GitHub の画面で確かめて記録する。CI の取得が失敗したら make-you-chic-ui 側を先に push してから CI を再実行する | Step 23 で行う。計画の時点では外の作業フォルダの追跡の分岐 `origin/main` が `3481488` を含んでいた（8節の D-3） | Step 23 |
| 基盤の設計（2回目、U5 の申し送り） | 確かめの前の「コミットしておく」は、stash か差分だけの形にするか、コミットの前に依頼者の承認を得ると書く | 「差分だけの形」にする。わざと値を入れた確かめはリポジトリの外で行い、差し替えの口の確かめは追跡しない一時の E2E のファイルを足して消す（どちらも作業フォルダのコミット済みのファイルを書き換えない）。コミットは毎回依頼者の承認を得る | Step 16・18 |
| 基盤の設計（スモークテストの申し送り） | 一覧が開けることを U5 の分とし、操作を入れるかは deployment-pipeline で決める | 「Build and Test に引き継ぐこと」に写す | — |
| U4 のコード生成のレビュー（R-02） | 画面と本物のサーバーを通した 403 の確かめを、U5 の E2E 110 で自分で作った管理者でない利用者を使って行う | 110 に1つの step を足す。作り方は 9節の Q-B | Step 17 |
| U4 のコード生成のレビュー（R-04） | 古い読み込みの 403 を捨てた後に、最新の要求の 403 で S6 が出る道のテスト | `UserAdminPage.test.tsx` に、本物の `AdminForbiddenProvider` と ShellLayout の中に描くテストを置く（8節の D-6） | Step 14 |
| U4 の申し送り | `playwright.config.ts` の直し（html を外す・trace の既定 `off`・`e2eTraceMode()`）と報告の部品の直し（json の添付の base64 の復号・`runTag`）は B5 | Step 15 で行う | Step 15 |
| U2 の申し送り | `UiPaging` を使い、操作の後の読み直しで `correctedPage`。文言の鍵は U5 が持つ。8KB を超える要求の HTML の 400 の扱いを B5 で決める | 前の2つはそのとおり。HTML の 400 は 9節の Q-C | Step 13・14 |
| U3 の申し送り | 画面の型を本物の応答と一致させ、E2E の見本と本物の一致を流れの E2E で確かめる | `api/types.ts` を C3 と U3 の実装に合わせ、110 で照らし合わせる | Step 9・17 |

### 2.2 この計画での読み方

- **層の順（test-after）**: Testing Contract の `ordering` のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む。U5 の層は「リポジトリの作業（`.npmrc`・固定先）→ 共通への移し（`fieldErrors`）→ 純粋な関数と文言 → API の受け渡し → 画面の部品 → 画面の組み立て（フックと画面と登録）→ E2E の土台（設定・報告の部品・手伝い）→ E2E 110・120 → 静的検査と構造」の順とする（依頼の順。下の層を先に作るため、各 Step の終わりで型の検査が通る）。データの形と DB・DB アクセス・業務処理の層は U5 に無い（画面の単位）。
- **画面の判定はサーバーの判定の代わりにしない**: 行の項目の押せなさ・メニューの出し分け・S6 は表示だけ。サーバーの 401・403・204 と自分自身の操作の拒否・最後の管理者の保護・監査は U3・U1 の持ち物で、U5 は `backend/` を変えない（NFR1.1、`project.md` の Mandated）。
- **骨組みは変えない**: `src/app/` のファイルは書き換えない。403 の表示と自分の氏名と言語の反映は U4 の口（`useAdminForbidden`・`useApplyOwnProfile`）を呼ぶだけ。機能の登録は `registration.ts` を置くだけで、`registrationModules.ts` が読み込む（FS 2節）。
- **機能どうしは読み込まない**: `features/useradmin` は `features/invitation`・`features/preferences` を読まない。共通のものは `src/shared/` から読む（`team.md` の Code Style）。`fieldErrors.ts` は移すだけでふるまいを変えない（FS 10節の (f)）。
- **既存の E2E と設定**: 010〜100・130 と `support/` の既存のファイルは変えない。`playwright.config.ts` と報告の部品だけを直す（SD 12節の 2・7）。U4 の N-16（新しい表示で既存の E2E の期待が古くなった）の反省から、Step 1 と Step 19 で、U5 の変更（サイドバーの項目の追加・固定先の更新・報告の設定）に当たる既存のテストと E2E を検索で洗い出す。
- **差分が無いことの基準のコミット**: `develop` の今の先頭 `21a2fdd`（作業ブランチの分かれ目）と比べる（`git diff --stat 21a2fdd -- <パス>`）。記録だけのコミット（R1 など）は `aidlc/` の下だけのため、アプリのパスの比べに影響しない。
- **画面の時間**: 120 の測りは記録だけで成否にしない。本番での判定は `Unverified`（基盤の設計の R-01）。目標（2 秒・1.5 秒）は緩めない。

## 3. 作業の場・依頼の分け方・コミットの区切り・統合

### 3.1 作業の場

- `develop`（先頭 `21a2fdd`、`aidlc/` の下に未コミットの記録 `aidlc-state.md`・監査ログがある）から、依頼者の承認を得て短命のブランチ `feature/260930-user-admin-b5` を作る（`git switch -c`。未コミットの記録はそのまま作業ブランチへ持ち越す）。worktree は使わない（`team.md` の Way of Working）。
- AI は `origin` へ push しない。ブランチの作成・コミット・統合・ブランチの削除は、どれも依頼者の承認を得てから行う。生成の担当はコミットしない（オーケストレーターが依頼者の承認を得て行う、`project.md` の Change Control）。

### 3.2 依頼の分け方

`.npmrc` と固定先の更新は「それぞれのコミットの後に」確かめを流す決まり（TS の NFR9.3・NFR9.4、CP 4.4）で、コミットは依頼者の承認を要するため、生成の依頼を次の5回に分ける（`project.md` の学び「長い依頼は分ける」）。各回の終わりで、生成の担当は止まり、オーケストレーターが依頼者に結果と次のコミットを諮る。

| 回 | 生成の担当が行う Step | 回の後にオーケストレーターが行うこと |
|---|---|---|
| 1 | Step 1・2 と Step 3 の変更（`.npmrc` の1行） | 依頼者の承認を得て C1 をコミットする |
| 2 | Step 3 の確かめ（`npm ci`・verify・E2E、報告を記録してから消す）と Step 4 の固定先の更新（サブモジュールを `3481488` にする）と FC 2.5 の確かめ | 「依頼者に戻す差」があれば諮る。無ければ承認を得て C2 をコミットする |
| 3 | Step 4 の確かめ（`npm ci`・既存の画面のテスト・E2E、報告を記録してから消す・初回の大きさ）と Step 5〜14 | 途中で見つかった判断を諮る |
| 4 | Step 15〜20 と Step 21 の記録 | 承認を得て C3〜C6・R2 をコミットする |
| 5 | Step 22 の関門 | 承認を得て R3 をコミットし、fast-forward で統合する（Step 23） |

- 回1 に `.npmrc`、回2 に固定先を置く順は、9節の Q-A の決定（A: `.npmrc` を先）による。
- 次の回の担当には、前の回の決定と `generation-notes.md` の場所を伝える。

### 3.3 コミットの区切り

メッセージは日本語で、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。件名の案は各 Step（Step 21 でまとめて示す）。アプリのソースのコミットには `aidlc/` の下を入れず、記録だけのコミットには `aidlc/` の外を入れない（`git show --stat` で確かめる）。

| 区切り | 中身 | 時点 |
|---|---|---|
| R1 | U5 の計画・単位のテストの手順・Plan Approval の記録と、その時点の `aidlc-state.md`・監査ログ（B4 の後の未コミットの記録を含む） | Plan Approval の後、ブランチを作った直後（Step 1） |
| C1 | `frontend/.npmrc` に `ignore-scripts=true`（件名の案「B5 frontend の npm のインストールでパッケージのスクリプトを動かさない（ignore-scripts=true）」） | Step 3 の変更の後 |
| C2 | `vendor/make-you-chic-ui` の固定先を `077f5b4` から `3481488` へ（専用のコミット。本文に更新前後の完全なハッシュ `077f5b48ce84cd020ecec2d925836a085f9d9e11` → `34814887433c61a4eb51d3572362d6eb41edae30` と、取り込む変更（Dropdown の押せない項目と理由の文）を書く。件名の案「make-you-chic-ui の固定先を 3481488 に上げる（Dropdown の押せない項目と理由の文）」） | Step 4 の変更と FC 2.5 の確かめの後 |
| C3 | 共通への移し・純粋な関数と文言・API の受け渡し（`src/shared/api-client/fieldErrors.ts` とテスト、プリファレンスの読み込み先、`useradmin/` の純粋な関数・`messages.ts`・`useUserAdminText.ts`・`api/` とテスト） | Step 5〜10 |
| C4 | 画面の部品・組み立て・登録（`useradmin/` の部品と CSS・`useUserAdmin.ts`・`UserAdminPage.tsx`・`registration.ts`・`testing/` とテスト） | Step 11〜14 |
| C5 | E2E の土台（`playwright.config.ts`・`playwright-secret-check-reporter.ts`・`frontend/e2e/support/` の新しい手伝い） | Step 15・16 |
| C6 | E2E 110・120 と README | Step 17・18 |
| R2 | U5 の生成の記録（`code-summary.md`・`generation-notes.md`・`source-manifest.json`・`traceability.json`、計画のチェック、`aidlc-state.md`・監査ログ） | Step 21（C3〜C6 の後） |
| R3 | 関門の記録（Step 22 の実測・E2E の結果・報告を消したこと）と、その時点の `aidlc-state.md`・監査ログ | Step 22 の後、統合の前 |
| R4 | 統合の後の記録（統合のハッシュ・ブランチを消したこと・公開の側の確かめ） | Step 23 の統合の後、`develop` の上で |

- C1・C2 は確かめを流す前にコミットする（変更ごとに切り分けるため）。C3〜C6 は生成の後にまとめて提案し、途中のコミットで verify を流し直さない。通ることを確かめるのは C1〜C6 をすべて含む作業ブランチ（Step 22）。
- C3〜C6 の分け方は、コミットの後のどの時点でも型の検査が通る順にしてある（C3 が C4 の、C5 が C6 の前提）。

### 3.4 統合（fast-forward）

- サブモジュールの固定先の更新を含むため、`team.md` の Way of Working と `bolt-plan.md` の B5 のとおり、squash ではなく作業ブランチから `develop` へ **fast-forward** で統合する（`git switch develop` → `git merge --ff-only feature/260930-user-admin-b5`）。C1〜C6 と記録だけのコミット R1〜R3 は、区切りのまま `develop` に入る（記録のコミットを squash から外す手順は要らない）。
- 統合の前に `develop` が `21a2fdd` から動いていないことを確かめる。動いていれば fast-forward にならないため、作業ブランチを付け直すか（rebase）を依頼者に諮る（rebase したら Step 22 の関門をやり直す）。
- 統合の後、依頼者の承認を得て作業ブランチを消す。push は依頼者が行う。push の前に、`3481488` が make-you-chic-ui の公開の側にあることを確かめて記録する（Step 23）。

## 4. 作るもの・手を入れるもの・消すもの

### 4.1 リポジトリの作業

| 置き場 | 新しい・手を入れる | 中身 |
|---|---|---|
| `frontend/.npmrc` | 手を入れる | `ignore-scripts=true` を足す。`engine-strict=true` は残す（TS の NFR9.4）。効くのは `frontend/` の中のインストールだけ |
| `vendor/make-you-chic-ui` | 固定先を上げる | `077f5b4` → `3481488`（`git -C vendor/make-you-chic-ui fetch` の後に `checkout 3481488`）。中身は変えない（`project.md` の Forbidden）。`frontend/package-lock.json` は make-you-chic-ui の版（`package.json` の `version` が両方とも `0.0.0`）が変わらないため、差が出ない見込み。差が出たら中身を記録する |

### 4.2 本体（`frontend/src`）

| 置き場 | 部品 | 新しい・手を入れる | 中身 |
|---|---|---|---|
| `src/shared/api-client/fieldErrors.ts` | `readFieldErrors`・`ReadFieldError` | 移す（`git mv`） | `features/preferences/fieldErrors.ts` から移す。中身と口は変えない。先頭の説明文に、移した理由（2つの機能が使う、FS 10節の (f)）を1行足す |
| `src/features/preferences/usePreferencesForm.ts`・`usePasswordChangeForm.ts` | 読み込み先 | 手を入れる | `./fieldErrors` を `../../shared/api-client/fieldErrors` に替える。ほかは変えない |
| `useradmin/api/types.ts` | `AdminUser`・`AdminUserPage`・`ProfileRequest`・`UserLanguage`・`USER_LANGUAGES` | 新しい | FC 5.1 のとおり。`lockedUntil?: string`（8節の D-4） |
| `useradmin/api/userAdminApi.ts` | `USER_ADMIN_API_ROOT`・`userAdminOperationPath`・`UserAdminApi` と既定の実装（`listUsers`・`grantAdmin`・`revokeAdmin`・`suspendUser`・`resumeUser`・`resetLoginFailures`・`updateProfile`） | 新しい | FC 5節のとおり。既存の ApiClient（`apiRequest`）を通す。一覧の本文は C3 の項目だけを写した新しい値にし、形が違えば `networkError()` を投げる（SD 6.3）。`lockedUntil` は無いか `null` なら持たない、文字列ならその値、ほかの型は形の違い（8節の D-4）。`userId` は `encodeURIComponent` してパスに入れる。本文は `{ displayName, language }` だけ。`console` を呼ばない |
| `useradmin/rowActions.ts` | `RowActionKind`・`DisabledReason`・`RowAction`・`rowActions` | 新しい | 行の `admin`・`suspended`・`resettable`・`self` だけを受け、FS W4 の表の順で項目と押せなさを返す（D5・D6、FC 4.1） |
| `useradmin/searchInput.ts` | `USER_SEARCH_MAX_CODE_POINTS`（254）・`checkSearchInput` | 新しい | `trimDisplayName` で前後の White_Space を除き、`countCodePoints` が 254 を超えれば `tooLong`、空なら「検索なし」、それ以外は除いた後の値（D15、FC 7節）。説明文に U3 の値と同じことを書く |
| `useradmin/profileInput.ts` | 氏名の確かめと理由の寄せ方・文言の鍵 | 新しい | `validateDisplayName` を呼び、サーバーの `reason`（`REQUIRED`・`TOO_LONG`・`INVALID_CHARACTER`・`INVALID_VALUE` など）を画面の理由に寄せ、項目と理由から文言の鍵を返す（D16、FS 7.6）。表は useradmin の中に持つ（プリファレンスの `errorMessages.ts` を読まない） |
| `useradmin/lockedUntil.ts` | `formatLockedUntil` | 新しい | 解除の予定の時刻を、ブラウザの時間帯で今日なら時刻と時差の略号だけ、今日でなければ `formatDateTime` の書式で日付つきにする（D18）。今の時刻と時間帯は引数で受ける（テストで固定する） |
| `useradmin/failureMessage.ts` | `USER_ADMIN_ERROR_CODES`・`failureMessageKey` | 新しい | 失敗と場面（一覧・操作・保存）から文言の鍵と code を選ぶ（D8、FC 5.2）。`detail`・`title` を読まない |
| `useradmin/focusTarget.ts` | フォーカスの行き先の型と `focusAfterReload` | 新しい | 読み直しの後、同じ `userId` の行があればその行の「操作」、無ければ一覧の見出し（W12） |
| `useradmin/messages.ts` | `userAdminMessages` | 新しい | FS 7節の文言を ja・en の対で置く（鍵は `useradmin.`）。`Table` の `labels`・`Modal` の `closeLabel`・読み上げの名前の文言を含む（NFR8.1・NFR8.2） |
| `useradmin/useUserAdminText.ts` | `UserAdminText`・`useUserAdminText` | 新しい | 招待の `useInvitationText` の形に、3つ目の引数 `language` を足したもの（FC 3.4 の断片）。`language` があれば `t(key, { ...values, lng: language })` |
| `useradmin/UserSearchBox.tsx`・`.css` | `UserSearchBox` | 新しい | `role="search"` の `form`、`FormField`・`TextInput`・`Button`×2。busy の間は `aria-disabled="true"`（`disabled` 属性は付けない）で押しても Enter でも何もしない（FC 4節） |
| `useradmin/UserTable.tsx`・`.css` | `UserTable` | 新しい | make-you-chic-ui の `Table`（`aria-label`、`getRowId` は `userId` の文字列、`labels` は画面の言語）。列の描き方（D18・D19、`Badge`、「—」は `aria-hidden` で見えない文字「なし」）。busy の間の `onPageChange` を捨てる。表の外側を横にずらせるだけ（RQ7 C） |
| `useradmin/UserRowActions.tsx` | `UserRowActions` | 新しい | `Dropdown` の `trigger` に `Button`（secondary・小、`aria-label` は「〔氏名〕（〔メールアドレス〕）の操作」、busy なら末尾に「（処理中）」、busy の間は `loading`）。`items` は `rowActions` から作り、押せない項目は `disabled: true` と `description`（FC 4節） |
| `useradmin/ConfirmActionDialog.tsx` | `ConfirmActionDialog` | 新しい | `Modal`（`closeOnBackdropClick={false}`、`initialFocusRef` は「やめる」、`closeLabel` は画面の言語）。本文の先頭に対象と効き目の文、5種類の見出し・ボタン（revokeAdmin・suspend は危険の見た目）。送信中は閉じず「処理中」、5 秒で「時間がかかっています」（FS 7.3、D7・D13・D14） |
| `useradmin/EditProfileDialog.tsx`・`.css` | `EditProfileDialog` | 新しい | `Modal`（背景のクリックで閉じない、はじめのフォーカスは氏名）。`FormField`・`TextInput`（`autocomplete="off"`）・`RadioGroup`（日本語・English）。誤りは入力欄のすぐ下（`aria-invalid`・`aria-describedby`）、not-found・failed の文言、送信中は閉じない（FS 4.3・7.6） |
| `useradmin/useUserAdmin.ts` | `useUserAdmin` | 新しい | FC 3節の状態と操作。`load(target, options)`（200 のときだけ `page`・`searchText` を置き換える、読み直しの番号、補正は1回まで）、`loadingRef`・`submittingRef`（`finally` で必ず戻す、PD 3.2）、5 秒の時計（`setTimeout`、応答と画面を離れたときに解く）、失敗の入口 `handleFailure(error, path, context)`（401 → 403 → 場面ごとの順、SD 2.2）、自分の行の保存の順（`applyOwnProfile` → Toast（送った言語）→ 読み直し）。状態は画面の中だけに持ち、URL・保存・履歴・コンソールに出さない |
| `useradmin/UserAdminPage.tsx`・`.css` | `UserAdminPage` | 新しい | `h1`「利用者の管理」、検索、業務の失敗の知らせ（`Alert` warning、[×]）、読み込みの失敗（`Alert` error と「もう一度読み込む」）、`h2`「利用者の一覧」（`tabIndex=-1`）、状態の読み上げ（`role="status"`）、表、確かめと入力の表示。`useAdminForbidden()` と `useApplyOwnProfile()` と `useDisplaySettings()` の言語を `useUserAdmin` に渡す。API の関数の集まりは既定の実装を使い、テストでは描画の手伝いで差し替える |
| `useradmin/registration.ts` | `USER_ADMIN_PATH`・`registration` | 新しい | FC 6節（`featureId: 'useradmin'`、`/admin/users`、`lazy`、`layout: 'SHELL'`、`access: 'ADMIN'`、サイドバー `order: 230`・`visibleWhen: 'ADMIN'`、文言） |

手を入れない: `backend/` のすべて（`backend/src/main/resources/application.yaml` の CSP を含む）、`src/app/` のすべて、`src/features/invitation`・`admin`・`dsl`・`auth`・`registration`、`src/shared/paging`・`format`・`validation`・`api-client/apiClient.ts`・`apiError.ts`・`adminForbidden.ts`、`frontend/package.json`、`frontend/vitest.config.ts`・`vite.config.ts`・`eslint.config.js`・`tsconfig.json`、`.github/`、`compose.yaml`・Dockerfile・`.env.example`、`docker/`、`build.gradle.kts`。

### 4.3 テストとテストの補助（`frontend/src`）

| 置き場 | 新しい・移す・手を入れる | 中身 |
|---|---|---|
| `src/shared/api-client/fieldErrors.test.ts` | 移す（`git mv`） | 中身は読み込み先の1行だけ直す。件数は変えない |
| `useradmin/rowActions.test.ts`・`searchInput.test.ts`・`profileInput.test.ts`・`lockedUntil.test.ts`・`failureMessage.test.ts`・`focusTarget.test.ts` | 新しい | 純粋な関数の例と性質（`unit-test-instructions.md` 3節） |
| `useradmin/api/userAdminApi.test.ts` | 新しい | 要求の形・本文の写し・形の違い・`lockedUntil` の `null` |
| `useradmin/registration.test.tsx` | 新しい | 登録の値と、文言の鍵が ja・en で一致すること |
| `useradmin/UserSearchBox.test.tsx`・`UserTable.test.tsx`・`UserRowActions.test.tsx`・`ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx`・`UserAdminPage.test.tsx` | 新しい | 部品ごとの確かめと vitest-axe（FC 8節） |
| `useradmin/testing/fixtures.ts` | 新しい | 見本の行（2語の氏名・長いメールアドレス・ロック中・停止中・管理者・自分の行・記号を含む氏名、メールアドレスは `example.com` だけ）とページ |
| `useradmin/testing/renderUserAdmin.tsx` | 新しい | `renderWithProviders` を使い、偽物の API・ログイン状態の提供元・言語・表示の設定（テーマと文字の大きさ）・ShellLayout の中に描く選択を渡す手伝い。`ToastProvider`・`ModalStackProvider` は招待の `renderInvitation` と同じ形で置く |

### 4.4 E2E と設定と文書

| 置き場 | 新しい・手を入れる | 中身 |
|---|---|---|
| `frontend/playwright.config.ts` | 手を入れる | reporter から html を外す。`use.trace` を `traceModeFromEnv()`（既定 `off`、`E2E_TRACE` の `off`・`on`・`retain-on-failure` の外は読み込みで誤り）にする。報告の部品の設定に、値のファイル・`test-results/`・前の html の報告の場所と、trace を有効にしたかを渡す。冒頭の説明（「結果は list・html に加えて json」）を直す（SD 3.2） |
| `frontend/playwright-secret-check-reporter.ts` | 手を入れる | SD 3.5 の 1〜10（探す値を集める・形を広げる・値の形で探す・`test-results/` の下のすべてのファイル・json の報告の base64 の復号・zip の展開（`node:zlib` の `inflateRawSync`）・前の html の報告・開けないものは失敗・種類と件数だけの出力・値のファイルを `finally` で消す・trace を有効にした実行の知らせ）。新しい依存を足さない。値のファイルの `runTag` は 24 文字以上のときだけ単独で探す |
| `frontend/e2e/support/traceMode.ts` | 新しい | `TRACE_MODES`・`traceModeFromEnv()`（設定が使う）と `e2eTraceMode()`（110・120 の `test.use` が使う）。設定のファイルと同じ読み方を1か所に置く（`playwright.config.ts` を読まない） |
| `frontend/e2e/support/secretValues.ts` | 新しい | 値のファイルの置き場 `SECRET_VALUES_FILE`（`test-results/e2e-secret-values.json`）・`recordSecretValues(entries)`（種類と値の組の配列を読み足して書き直し、作るときと書いた後に権限 600）・値の形の表（宛先・パスワード・氏名）。報告の部品もこの表と置き場を読む |
| `frontend/e2e/support/userAdminRun.ts` | 新しい | 110 の `runTag`（時刻の16進と `randomBytes(8)` の16進、SD 3.4）を作る `newUserAdminRunTag()` |
| `frontend/e2e/support/userAdminFixtures.ts` | 新しい | 一覧のページ（2語の氏名・長いメールアドレス・ロック中・停止中・管理者・自分の行・21 件以上の2ページ分）・409・400 の見本。画面の型（`useradmin/api/types.ts` の `AdminUserPage`・`AdminUser`）を付ける。形の照らし合わせの関数（項目の名前と型、`lockedUntil` は文字列か `null`、値は出さない）を置く（SD 5節） |
| `frontend/e2e/support/adminApiRoute.ts` | 新しい | 差し替えの口（SD 4.1・4.2）。page ごとの1つの `page.route`、同じオリジンで正規化したパスが `/api/admin` ちょうどか `/api/admin/` の下を受ける、モード（検査・測り前半・測り後半）、見本で返す・本物へ通す GET・見本の無い GET の決まった失敗・GET 以外の打ち切りと記録、受けた件数、各テストの終わりの確かめ `expectNoBlockedWrites`（受けた件数 1 以上 → 打ち切りの記録 0 件。失敗の知らせはメソッドと道の型だけ） |
| `frontend/e2e/support/userAdminDiagnostics.ts` | 新しい | 値を伏せた手がかり（SD 3.6）。通った手順の題・`/api/admin/`・`/api/auth/` の要求のメソッドと道の型と状態コード・`watchPage` の問題の件数・今の画面の道の型・行の数を集め、テストが失敗したときだけ注記と添付 `user-admin-diagnostics` に残す |
| `frontend/e2e/110-user-admin-flow.e2e.ts` | 新しい | 代表の流れ（FS 9節、Step 17 の表） |
| `frontend/e2e/120-user-admin-accessibility.e2e.ts` | 新しい | 20 組×11 状態の検査（組ごとに1つのテスト）と、既定の1組だけの画面の時間の測り1件（Step 17 の表） |
| `README.md` | 手を入れる | CP 4.6 のとおり（E2E の表に 110・120 の行、「110 について」「120 について」の節、結果は json だけで html を作らないこと、手元だけ trace を有効にする手順、`error-context.md` で報告の部品が失敗したときの手順、前の `frontend/playwright-report/` を消す手順） |

### 4.5 差分が無いことを記録するもの

`21a2fdd` と比べて、次に差分が無いことを `git diff --stat 21a2fdd -- <パス>` で確かめて記録する（CP 8節）: `backend`（`application.yaml` を含む）、`frontend/package.json`、`frontend/vitest.config.ts`・`vite.config.ts`・`eslint.config.js`・`tsconfig.json`、`frontend/src/app`、`frontend/src/features/{invitation,admin,dsl,auth,registration}`、`frontend/src/shared/{paging,format,validation}`、`frontend/src/shared/api-client/{apiClient.ts,apiError.ts,adminForbidden.ts}`、`frontend/e2e/0*.e2e.ts`・`130-admin-forbidden-accessibility.e2e.ts`、`frontend/e2e/support` の既存のファイル（`adminLogin.ts`・`appearanceFixture.ts`・`axe.ts`・`displayCombos.ts`・`invitationFixtures.ts`・`invitationSeed.ts`・`loginPreferences.ts`・`mailpit.ts`・`overflow.ts`・`pageProblems.ts`・`preferencesFixtures.ts`・`registeredUser.ts`・`registrationFixtures.ts`）、`.github`、`docker`、`compose.yaml`、`gradle`、`build.gradle.kts`。`frontend/package-lock.json` は差が無いか、make-you-chic-ui の版の分だけであることを記録する。`vendor/make-you-chic-ui` は、ルートの差分が固定先（gitlink）の変更だけで、サブモジュールの作業フォルダの `git status --porcelain` が空で、先頭が `34814887433c61a4eb51d3572362d6eb41edae30` であることを記録する（verify の `vendorUnchanged` も確かめる）。

### 4.6 カバレッジと配信物の大きさの見込み

- 画面の全体の基準は B2 の関門の実測（行 97.61%（2414/2473）・分岐 93.01%（1505/1618）、画面のテスト 95 ファイル・801 件、U4 の `generation-notes.md`）。B3・B4 は画面を変えていない（`git diff --stat 1de9ef6 21a2fdd -- frontend` は空）。U5 は新しいソースのファイル 20 ほど（純粋な関数 6・API 2・文言 2・部品 5・フック 1・画面 1・登録 1・テストの補助 2）と、テストのファイル 14 を足す（画面のテストは 109 ファイルほどになる見込み）。新しいファイルは行・分岐とも 90% 以上を見込み、全体は下限（行 80%・分岐 70%）を大きく上回ったままの見込み。見込みは確かめの代わりにしない。Step 22 の `verify` で全体と U5 のファイルの値を実測して記録する。
- バックエンドは変えないため、`packagesJudgedByTotal` とパッケージごとの下限に U5 の作業は無い（CP 2節の段7）。
- 初回の JavaScript（gzip）は、(1) 固定先を上げる前（Step 1、基準は B2 の 122.7 KB）、(2) 固定先を上げた後（Step 4）、(3) U5 の変更の後（Step 22）の値を記録する（PD 4節の2つの時点に、切り分けのため (2) を足す）。U5 の画面は `lazy` のため入口はほぼ増えない見込み。目安 500KB を超えても警告だけ。

## 5. 手順

### Step 1: 作業の場と変更の前の基準

- [x] 依頼者の承認を得て、`develop`（先頭 `21a2fdd`）から `feature/260930-user-admin-b5` を作る。`git rev-parse --abbrev-ref HEAD`・`git rev-parse HEAD` と、アプリのソースに未コミットの変更が無いこと（`git status` が `aidlc/` の下だけ）を記録する
- [x] 依頼者の承認を得て、記録だけのコミット R1（3.3）を作る。`git show --stat` で `aidlc/` の外のファイルが入っていないことを確かめ、ハッシュを記録する
- [x] B4 の統合の後の CI の結果を確かめる（依頼者の push の後に `gh run list --branch develop --limit 3` で読むだけ）。失敗していれば `team.md` の「不安定なテストと CI の失敗」で扱い、次へ進む前に依頼者に諮る。まだ push されていなければ、そのことを記録して進めてよいかを諮る
- [x] Dependabot の開いている知らせを `gh pr list --state open` で確かめる。重大度 High 以上があれば、次の Bolt（B5）に入る前に取り込むかを依頼者に諮る（`team.md` の Way of Working）
- [x] `frontend/playwright-report/`・`frontend/test-results/` が手元に無いことを確かめる。あれば中を開かずに消し、消したこと（ファイルとディレクトリの件数）と共有していないことを記録する
- [x] 基準をとる: 画面のテストの件数とカバレッジは B2 の関門の実測を基準とする（4.6）。`git diff --stat 1de9ef6 21a2fdd -- frontend` が空であることを記録する。初回の JavaScript の大きさを `./gradlew frontendBundleSize` で測り、(1) の値として記録する
- [x] 影響の洗い出し（U4 の N-16 の反省）: 次を検索し、U5 の変更（サイドバーの項目「利用者の管理」の追加・固定先の更新・報告の設定の変更）で期待が変わる既存のテストと E2E を一覧にして記録する。`frontend/src` のテストで登録・サイドバーの項目の数や並びを数えるもの（`registrationModules`・`loadRegistrations`・`sidebarItems`・`order:`）、`frontend/e2e` でサイドバーのリンクの数・名前の一覧を確かめるもの（`getByRole('link'`・`toHaveCount`）、`app-shell` の中の `dropdown-trigger` を押すもの（`openUserMenuItem` と 080 の 232 行目。利用者の管理の画面の上で使うと行の「操作」と重なる）、`Dropdown` の項目の押下で閉じることに頼るもの、`html` の報告・`trace` に頼るもの。期待を変える必要があるものが見つかったときは、計画との差として記録し、直すかを依頼者に諮る（既存の E2E は変えない決まりのため、勝手に直さない）
- [x] 対応: B5 の共通の完了の条件、`gate-decisions.md` の U5 R-02、NFR5.6、`team.md` の Way of Working

### Step 2: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` 2.1 のコマンドで、U5 が移す・影響を受ける既存の画面のテスト（プリファレンス・ShellLayout のユーザーメニュー・招待の画面）が作業ブランチの上で通ること、Vitest・fast-check・vitest-axe が動くこと、Playwright が E2E のファイルを読めること（`--list`）を確かめる。件数を記録する
- [x] まだ作っていないテストを名指しすると「テストのファイルが無い」で失敗するのは想定どおりで、作った Step からコマンドが通ることを `unit-test-instructions.md` に書いてある
- [x] 対応: Testing Contract の `runner_step`、NFR9.7

### Step 3: `frontend/.npmrc` の `ignore-scripts=true`（9節の Q-A の決定 A で固定先より先）

- [x] `frontend/.npmrc` に `ignore-scripts=true` を足す（`engine-strict=true` は残す、説明のコメントを1行）。ほかのファイルは変えない（回1 はここで止まり、オーケストレーターが承認を得て C1 をコミットする）
- [x] C1 のコミットの後に、`(cd frontend && npm ci)`・colima の設定と `caffeinate -i` を付けた `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`・Mailpit を確かめて `caffeinate -i ./gradlew e2eTest`（010〜100・130）を流す。`npm ci` でスクリプトを動かさない警告・誤りが出ないこと、`vendorInstall`・`vendorBuild`（`vendor/make-you-chic-ui` の中のインストールには `.npmrc` が効かない）・`frontendInstall`・画面のビルド・Playwright（ブラウザは `npx playwright install chromium` で入れ済み、インストール時のスクリプトに頼らない）が通ることを記録する
- [x] E2E の後、`frontend/test-results/e2e-results.json` の `stats`（成功・失敗・飛ばした・不安定・時間）とファイルごとの件数と結果だけを読んで記録し、`frontend/playwright-report/`・`frontend/test-results/` を中を開かずに消す。消したこと（ファイルとディレクトリの件数）と共有していないことを記録する（`gate-decisions.md` の U5 R-02）
- [x] 失敗したら、原因が `ignore-scripts=true` か（スクリプトを要する依存があるか）を確かめ、直さずに止めて依頼者に諮る（`.npmrc` を外すか、依存の扱いを変えるか）
- [x] 対応: NFR9.4、CP 3節・4.3・4.4、`team.md` の Code Style（npm のパッケージのスクリプトを動かさない）

### Step 4: make-you-chic-ui の固定先の更新と FC 2.5 の確かめ

- [x] `git -C vendor/make-you-chic-ui fetch` の後に `git -C vendor/make-you-chic-ui checkout 34814887433c61a4eb51d3572362d6eb41edae30` で固定先を上げる。ルートの `git diff` が gitlink の1行だけであること、サブモジュールの中身を変えていないこと（`git -C vendor/make-you-chic-ui status --porcelain` が空）を確かめる。`077f5b4..3481488` の差分のファイルが `docs/integration-guide.md` と `Dropdown` の4ファイルだけであることを記録する
- [x] UserRowActions を書く前に、FC 2.5 の表を部品のソースとテスト（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`・`Dropdown.test.tsx`・`Dropdown.css`・`Button.tsx`）で確かめ、行ごとの結果を記録する（計画の前の確かめは 8節の D-3）。「依頼者に戻す差」が出たら、直さずに止めて依頼者に報告する（回2 はここで止まり、オーケストレーターが承認を得て C2 をコミットする）
- [x] C2 のコミットの後に、`(cd frontend && npm ci)` と、`Dropdown` を使う既存の画面のテスト（`unit-test-instructions.md` 2.2）を流し、続けて Mailpit を確かめて `caffeinate -i ./gradlew e2eTest`（010〜100・130）を流す。`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` の差の有無を記録する
- [x] E2E の後は Step 3 と同じく、json の `stats` とファイルごとの結果だけを記録してから `frontend/playwright-report/`・`frontend/test-results/` を消し、消したことと共有していないことを記録する
- [x] `./gradlew frontendBundleSize` で初回の JavaScript の大きさ (2) を測って記録する
- [x] 対応: NFR9.3、FC 2.4・2.5、CP 3節・4.4、`project.md` の Forbidden・Mandated（専用のコミットと前後のハッシュ）

### Step 5: 共通への移し（`fieldErrors`）— 実装

- [x] `git mv frontend/src/features/preferences/fieldErrors.ts frontend/src/shared/api-client/fieldErrors.ts` で移す。口（`readFieldErrors`・`ReadFieldError`）とふるまいは変えず、先頭の説明文に移した理由を1行足す
- [x] `usePreferencesForm.ts`・`usePasswordChangeForm.ts` の読み込み先を直す。`grep -rn "fieldErrors'" frontend/src` で古い読み込みが残っていないことを確かめる
- [x] 対応: FS 2節・10節の (f)、FC 2.2・9節、NFR9.7

### Step 6: 共通への移し — テスト

- [x] `git mv` で `fieldErrors.test.ts` を `src/shared/api-client/` へ移し、読み込みの1行だけを直す（件数を変えない）
- [x] `unit-test-instructions.md` 2.3 のコマンドで、移したテストとプリファレンスのすべてのテストが変更なしで通ることを確かめ、件数を記録する
- [x] 対応: NFR9.7

### Step 7: 純粋な関数と文言 — 実装

- [x] `useradmin/rowActions.ts`・`searchInput.ts`・`profileInput.ts`・`lockedUntil.ts`・`failureMessage.ts`・`focusTarget.ts` を 4.2 のとおり作る。名前つきの export だけ、`enum` を使わず文字列の union（code の一覧は定数の配列から型を作る）
- [x] `useradmin/messages.ts`（FS 7節の全文を ja・en の対で、鍵は `useradmin.`）と `useradmin/useUserAdminText.ts` を作る。en の見出しと効き目の文は ja と同じ中身にする
- [x] 新しいファイルの先頭に `/* ... */` の Apache License 2.0 のヘッダー（2026、agwlvssainokuni）を置き、日本語の説明文に D・W・FC の番号を書く
- [x] 対応: D5・D6・D8・D15・D16・D18、W4・W11・W12、FC 2.1・4.1・5.2・7節、NFR1.1・NFR3.2・NFR8.1・NFR8.3

### Step 8: 純粋な関数と文言 — テスト

- [x] `rowActions.test.ts`（16 通りの全組と W4 の 6 の性質、fast-check）・`searchInput.test.ts`（境界と性質）・`profileInput.test.ts`・`lockedUntil.test.ts`（時間帯と今の時刻を引数で固定、ja・en）・`failureMessage.test.ts`・`focusTarget.test.ts` を作る（`unit-test-instructions.md` 3節）
- [x] 性質ベースのテストの先頭に、失敗の種の再現の仕方を書く（`unit-test-instructions.md` 5節）
- [x] `unit-test-instructions.md` 2.4 のコマンドで流す
- [x] 対応: D5・D6・D8・D15・D16・D18、NFR1.1・NFR3.2・NFR8.3・NFR9.6

### Step 9: API の受け渡し — 実装

- [x] `useradmin/api/types.ts` と `useradmin/api/userAdminApi.ts` を 4.2 のとおり作る。型を C3 と U3 の実装（`AdminUser` の 11 項目と `self`、`AdminUserPage` の `items`・`page`・`size`・`total`、`ProfileRequest` の2項目）に合わせる
- [x] `useAdminForbidden` に渡すパスも、要求と同じパスを作る関数から作る（`USER_ADMIN_API_ROOT` と `userAdminOperationPath`）
- [x] 対応: C3、FC 5節、D8・D20、NFR3.1・NFR3.3・NFR5.3

### Step 10: API の受け渡し — テスト

- [x] `useradmin/api/userAdminApi.test.ts` を作る（偽物の `fetch` で ApiClient を通す。`unit-test-instructions.md` 3節）。パスと `q` の付け方（空なら付けない、特殊文字の符号化）、5つの操作の道と本文なし、氏名と言語の本文は2項目だけ、余計な項目（`passwordHash`・`failedAttempts`・`refreshToken`）を捨てる、`lockedUntil` が `null`・無い・文字列、形の違う本文は通信の失敗、失敗の応答は `ApiError` のまま
- [x] `unit-test-instructions.md` 2.5 のコマンドで流す
- [x] 対応: NFR3.3・NFR5.3、AC5.1.6（画面の側）

### Step 11: 画面の部品 — 実装

- [x] `UserSearchBox`・`UserTable`・`UserRowActions`・`ConfirmActionDialog`・`EditProfileDialog` と CSS を 4.2 と FC 4節のとおり作る。見た目は部品と同じ場所の素の CSS で付け、要素の `style` 属性で差し込まない。HTML を直接埋め込まない（NFR9.1）
- [x] `UserRowActions` は Step 4 で確かめた `Dropdown` の形（押せない項目は `onClick` を呼ばずメニューは開いたまま、`aria-disabled`、`description` を `aria-describedby`）に合わせる。busy の間に `onClick` が届いても `onSelect` を呼ばない
- [x] `useradmin/testing/fixtures.ts`・`renderUserAdmin.tsx` を作る
- [x] 対応: D6・D7・D13・D14・D16・D18・D19、W2〜W5・W7・W12、FC 4節、NFR1.1・NFR5.5・NFR7.1・NFR8.2・NFR9.1、AC1.1.10〜AC1.1.12・AC2.1.8・AC2.1.9・AC3.1.7・AC3.1.8・AC4.1.9・AC5.1.7

### Step 12: 画面の部品 — テスト

- [x] `UserSearchBox.test.tsx`・`UserTable.test.tsx`・`UserRowActions.test.tsx`・`ConfirmActionDialog.test.tsx`・`EditProfileDialog.test.tsx` を作る（FC 8節、`unit-test-instructions.md` 3節）。部品ごとに vitest-axe を1件以上（誤りの状態・2語の氏名・長いメールアドレスを含む見本）
- [x] 描画の後に反映される値は `waitFor` で待つ。テストの時間の上限は原因を確かめずに延ばさない
- [x] `unit-test-instructions.md` 2.6 のコマンドで流す
- [x] 対応: D6・D7・D13・D14・D19、NFR1.1・NFR3.2・NFR3.3・NFR5.5・NFR7.1・NFR7.2・NFR8.2・NFR9.7

### Step 13: 画面の組み立て（フック・画面・登録）— 実装

- [x] `useradmin/useUserAdmin.ts`・`UserAdminPage.tsx`・`.css`・`registration.ts` を 4.2 と FC 3節・6節のとおり作る
- [x] 失敗の扱いは `handleFailure` の1か所に集める（401 → `useAdminForbidden` → 場面ごと）。API を呼ぶ道はすべてここを通す。古い読み直しの答え（失敗を含む）は捨て、`useAdminForbidden` にも渡さない（8節の D-6）
- [x] 操作の後の読み直しの空のページは `correctedPage` で最後のページへ、1回の読み込みにつき1回まで（D10）
- [x] 対応: D1〜D4・D9〜D14・D17・D20、W1〜W12、FC 3節・6節、C4・C5、NFR1.2・NFR3.1・NFR5.4・NFR5.5・NFR5.6・NFR8.3、AC1.1.8〜AC1.1.10・AC2.1.13・AC3.1.11・AC4.1.10・AC5.1.2・AC5.1.3

### Step 14: 画面の組み立て — テスト

- [x] `UserAdminPage.test.tsx` と `registration.test.tsx` を作る（FC 8節、`unit-test-instructions.md` 3節）。U4 のレビューの R-04（古い読み込みの 403 を捨てた後に最新の要求の 403 で S6 が出る）と、PD 3.3 の参照の戻り（(a)〜(d)）を含める
- [x] `unit-test-instructions.md` 2.7 のコマンドで、`useradmin` のすべてと、登録が増えたことで影響を受けうる骨組みのテスト（`src/app`）と既存の機能のテストを流す
- [x] 失敗したときは、既存のテストを書き換えず本体を直す。既存のテストの期待を変える必要があると分かったときは止めて依頼者に諮る
- [x] 対応: D1〜D4・D9〜D14・D17・D20、R-01〜R-06、NFR1.2・NFR3.1・NFR3.2・NFR5.4・NFR5.5・NFR7.2・NFR8.1・NFR8.3、AC1.1.8〜AC1.1.12・AC2.1.13・AC3.1.11・AC4.1.10・AC5.1.2・AC5.1.3・AC5.1.7、U4 の R-04

### Step 15: E2E の土台（設定・報告の部品・手伝い）— 実装

- [x] `frontend/e2e/support/traceMode.ts`・`secretValues.ts`・`userAdminRun.ts`・`userAdminFixtures.ts`・`adminApiRoute.ts`・`userAdminDiagnostics.ts` を 4.4 のとおり作る。既存の `support/` のファイルは変えない
- [x] `frontend/playwright.config.ts` を直す（4.4）。資格情報は今のままプロセスの環境変数で渡し、`webServer.env` に置かない
- [x] `frontend/playwright-secret-check-reporter.ts` を SD 3.5 のとおり広げる（`node:fs`・`node:zlib`・`node:path` だけ。値は表示しない）。探す先・値のファイル・前の html の報告の場所は設定から受け、使い捨ての台本からも呼べる形にする（8節の D-9）
- [x] 対応: NFR3.4・NFR3.5・NFR9.2・NFR9.9、SD 3節・4節・5節、U4 の申し送り

### Step 16: E2E の土台 — 確かめ（わざと値を入れた確かめ）

- [x] SD 3.8 の (1)〜(8) を、リポジトリの外（ホームの下、権限 700 の一時の場所）に置く使い捨ての台本で、報告の部品を作った報告のファイルに向けて呼んで確かめる（8節の D-9。作業フォルダのファイルを書き換えない）。(1) json の報告の注記に U の氏名、(2) `test-results/` の下の zip（圧縮あり）の中のファイルに U のパスワードの URL の形、(3) `error-context.md` に U のメールアドレス、(4) 値のファイルを書かずに値の形だけで見つかる、(5) 壊れた zip で失敗、(6) 残した `playwright-report/`（`index.html` の base64 の zip に値）で失敗、(7) json の報告の添付の `body`（base64）に U のメールアドレス、(8) 値のファイルの `runTag` が 24 文字より短いときに単独では探さず警告。あわせて、値の無い報告で通ること、値のファイルが探し終えた後に無いこと、`recordSecretValues` が作ったファイルの権限が 600 であることを確かめる。台本の値は架空のもの（`example.com` の宛先、`e2e-u7-pw-` の形のパスワード、氏名「計測 花子」）で、終わったら一時の場所ごと消す
- [x] 確かめた種類ごとの結果（失敗・警告・通過）を、値を出さずに記録する
- [x] `E2E_TRACE=bogus` で設定の読み込みが誤りになり、無指定で `off` になることを `npx playwright test --list` で確かめる（`--list` はアプリを起動しない）
- [x] 確かめの後に `git status` と `git diff` で、作業フォルダに確かめの変更・一時のファイルが残っていないことを確かめて記録する（基盤の設計の R-03）
- [x] 対応: NFR3.4、SD 3.8、CP 4.5、`gate-decisions.md` の U5 R-03 と2回目の申し送り

### Step 17: E2E 110・120 と README — 実装

- [x] `frontend/e2e/110-user-admin-flow.e2e.ts` を作る。`test.use({ trace: e2eTraceMode() })`。順は次のとおり（FS 9節に、NFR9.9 の照らし合わせと U4 のレビューの R-02 を足した）:

| 順 | すること |
|---|---|
| 1 | `runTag = newUserAdminRunTag()`。`requestAdminAccessToken` と `registrationPrerequisites` で前提を確かめ、無ければ理由の種類だけを注記（`skip-reason`）して飛ばす（080 と同じ）。`createRegisteredUser(request, runTag)` で U を作り、すぐに `recordSecretValues` で U の宛先・パスワード・氏名（`REGISTERED_DISPLAY_NAME`）と `runTag` を値のファイルに書く。`u7-perf-<runTag>@example.com` が 254 コードポイント以内であることを確かめる |
| 2 | 管理者のコンテキストで `watchPage` → `loginAsAdmin` → `openSidebarItem(page, '利用者の管理')`。検索の欄に `runTag` を入れて [検索]。行がちょうど1行で、状態が [有効]、ロックが「—」。「操作」は行の中で役割 `button` と名前の末尾「の操作」で探し、読み上げの名前に U の氏名と宛先が入っていることは比べた真偽だけを `expect` に渡す（SD 3.3） |
| 3 | 一覧の本物の応答を `page.waitForResponse` で受けて、見本と項目の名前と型が一致することを確かめる（NFR9.9。失敗の知らせは項目の名前だけ） |
| 4 | Playwright の要求の口で U の宛先と誤ったパスワードで `POST /api/auth/login` を 5 回（既定のしきい値。`webServer` がしきい値を渡していないことを説明文に書く）呼び、すべて 401・`AUTHENTICATION_FAILED`。[検索] を押し直し、U の行に [ロック中] と解除の予定の時刻が出る（時刻の文言の形に固定しない、機能設計の R-08） |
| 5 | U の行の「操作」→「ロックを解除（失敗回数を戻す）」→ S3 でフォーカスが「やめる」→「ロックを解除」。成功の Toast、[ロック中] が消え、メニューに「ロックを解除（失敗回数を戻す）」が無い。新しいコンテキスト（U のブラウザ）で `loginWithForm` してホームが出る |
| 6 | 管理者のアクセストークン（`requestAdminAccessToken`）で U の失敗回数をもう一度戻す API を呼び、本物の 409（`USER_ADMIN_NO_CHANGE`）を受けて、見本と項目の名前と型が一致し、見本の `code` が C3 の5つの code のどれかであることを確かめる。上限を超える検索の文字で一覧の API を呼び、本物の 400（`VALIDATION_FAILED`）と見本が一致することを確かめる（NFR9.9。U の状態は変わらない） |
| 7 | 管理者が U の行の「操作」→「利用を止める」→ S3 →「利用を止める」。Toast、[利用停止]、メニューの「停止を解く」。U のブラウザで `openUserMenuItem` からプリファレンスを開くと 401 → 更新も 401 → ログインの画面（`login-layout`）。U の宛先と正しいパスワードで `login-form-error-text` がパスワードの誤りと同じ文言（AC3.2.9・AC3.2.10） |
| 8 | 管理者が「停止を解く」→ S3 →「停止を解く」。Toast と [有効]。U のブラウザで `loginWithForm` してホームが出る |
| 9 | U4 のレビューの R-02（画面と本物のサーバーを通した 403）: 9節の Q-B の決定の形で、管理者でない U の画面から本物の管理の API の 403 `ACCESS_DENIED` を受け、S6（`admin-forbidden-view`）が出て、管理のメニュー（「利用者の管理」のリンク）が消えることを確かめる。初期管理者・サーバーの U の状態は変えない |
| 10 | 後片付け: コンテキストを閉じる。U は消さない。初期管理者の印・停止・ロックは触らない。管理者の画面の `watchPage` の CSP の違反が 0 件 |

- [x] 110 の `test.afterEach` で `userAdminDiagnostics` の記録を、失敗のときだけ添付する。`test.step` の題は英語で、値を入れない
- [x] `frontend/e2e/120-user-admin-accessibility.e2e.ts` を作る。`test.use({ trace: e2eTraceMode() })`。`DISPLAY_COMBOS` の組ごとに1つのテスト（20 件）と、測りのテスト1件:

| 順 | すること（組ごとのテスト） |
|---|---|
| 1 | `watchPage` と 120 の中のコンソールの見張り（位置の URL が `/api/admin/` の下で、差し替えた失敗の応答に Chrome が出す `Failed to load resource:` の error だけを数えて引く。130 と同じ扱い。`pageProblems.ts` は変えない）を張り、`adminApiRoute` を検査のモードで張る（どちらも `page.goto` とログインより前） |
| 2 | `prepareCombo`、`routeLoginPreferences(page, { theme, fontSize })`、`loginAsAdmin`、`expectComboApplied` |
| 3 | 11 状態を順に作り、状態ごとに `runAxe`・`missingRequiredRules` が空・違反 0（既知の違反の一覧は空）・`measureHorizontalOverflow` ではみ出し無しを確かめる: (1) 一覧（2語の氏名・長いメールアドレス・ロック中・利用停止・管理者・「あなた」の行）、(2) 自分の行のメニューを開いた状態、(3) 確かめの表示（「利用を止める」）、(4) 氏名・言語の入力の誤りの状態（画面の確かめの誤り）、(5) 業務の失敗の知らせ（409 の見本）、(6) 検索の上限の誤り、(7) 氏名・言語の入力の通常の状態、(8) 成功の Toast（消える 4 秒の前に検査する）、(9) 一覧の読み込みの失敗、(10) 空の一覧（検索に当たらない見本）、(11) 自分の行のメニューの押せない項目にホバーした状態とキーボードでフォーカスを置いた状態 |
| 4 | 終わりに `expectNoBlockedWrites`（口が受けた件数 1 以上 → GET 以外の打ち切りの記録 0 件）、引いた後の問題 0 件、CSP の違反 0 件。組ごとの注記（`type: 'axe'`、組と状態の名前・違反の件数と規則の名前・はみ出し・除いたコンソールの表示の件数）に値を入れない |

| 順 | すること（測りのテスト、既定の1組 light・md・blue・既定の幅） |
|---|---|
| 1 | 口を測りのモード（一覧の GET は本物へ通す）で張り、ログインしてホームが出るのを待つ |
| 2 | サイドバーの「利用者の管理」を選ぶ直前と一覧の最初の行が見えた時点の `Date.now()` の差を 5 回（測るたびにサイドバーでホームへ戻る） |
| 3 | 口を測り後半のモード（2ページ分の見本）に切り替え、「次へ」を押す直前と2ページ目の最初の行が見えた時点の差を 5 回（測るたびに「前へ」で戻す） |
| 4 | 添付 `user-admin-screen-ms`（PD 5.4 の形、`apiTimeIncluded`・`note` は決まった文）と注記に数だけを残す。時間はテストの成否にしない。`expectNoBlockedWrites` を確かめる |

- [x] 110・120 の先頭の説明文（日本語）に、流れの本数に数えるのは 110 だけ・初期管理者を変えない・差し替えの範囲・報告に値を入れない・trace の扱い・既存の `support/` を変えないことを書く
- [x] `README.md` を 4.4 のとおり直す
- [x] 対応: E2E-M9、NFR3.4・NFR3.5・NFR5.1・NFR5.2・NFR7.3・NFR9.1・NFR9.8・NFR9.9、AC3.2.9・AC3.2.10（画面の流れ）、U4 のレビューの R-02、機能設計の R-08、CP 4.1・4.6

### Step 18: E2E 110・120 の実行と差し替えの口の確かめ

- [x] `./gradlew :backend:bootWar` と Mailpit を確かめた後に、`caffeinate -i` で包んで `(cd frontend && npx playwright test e2e/110-user-admin-flow.e2e.ts e2e/120-user-admin-accessibility.e2e.ts)` を流し、通るまで直す（README の 050〜130 と同じ形。合否の確定は Step 22 の `./gradlew e2eTest` の全体）。110 で飛ばした注記が出ていないこと、報告の部品が通ったことを確かめる。結果を記録してから `frontend/test-results/` を消す（`playwright-report/` は作られないことを確かめる）
- [x] SD 4.3 の3つの確かめ（わざと POST を送る形・口を張らない形・二重のスラッシュの道）を、追跡しない一時の E2E のファイル（例 `frontend/e2e/990-route-guard-check.e2e.ts`）に書き、`expectNoBlockedWrites` が失敗すること（前2つは「受けた件数」か「打ち切りの記録」で、二重のスラッシュは記録で）を確かめる形で流す。結果を記録した後にそのファイルを消す。既存のファイルは書き換えない
- [x] 確かめの後に `git status` と `git diff` で、一時のファイルと確かめの変更が残っていないことを確かめて記録する（基盤の設計の R-03）
- [x] 失敗したら `team.md` の「不安定なテストと CI の失敗」で扱う。時間の上限を原因を確かめずに延ばさない
- [x] 対応: NFR3.4・NFR3.5・NFR7.3・NFR9.8・NFR9.9、SD 4.3、CP 4.5

### Step 19: 画面の静的検査と構造の確かめ

- [x] `unit-test-instructions.md` 2.8 のコマンドで、型の検査（`npm run typecheck`、`e2e` と見本の型を含む）、変えたフォルダーとファイルに絞った書式（Prettier）・リンタ（oxlint・ESLint、セキュリティ系の決まりを含む）・CSS のリンタ（Stylelint）・ライセンスヘッダーの検査を通す。除外を足さない（NFR9.1）
- [x] `npx playwright test --list` で 110 が 1 件、120 が 21 件、全体が 13 ファイル・152 件と出ることを確かめる
- [x] 画面の側の境界: `features/useradmin` が `features/` のほかの機能を読まないこと（`grep -rn "features/\(invitation\|preferences\|admin\|dsl\|auth\|registration\)" frontend/src/features/useradmin` が空）、`src/shared` と `src/app` に `features/useradmin` への読み込みが無いことを確かめて記録する。E2E が画面のコードを読むのは型（`import type`）だけであることを確かめる
- [x] 4.5 の差分が無いことを確かめて記録する
- [x] Step 1 の影響の洗い出しを、実装の後にもう一度行い、結果を記録する
- [x] 対応: NFR1.1・NFR9.1・NFR9.2・NFR9.3、CP 2節・8節

### Step 20: コードのレビューでの確かめ（申し送りと NFR の確かめ）

各項目の確かめた方法と結果を `generation-notes.md` に書き、`code-summary.md` に写す。

- [x] **失敗の入口の1か所（NFR1.2、SD 2.2）**: `useradmin` で API を呼ぶ道がすべて `handleFailure` を通り、`403`・`ACCESS_DENIED` を自分で見ていないことを検索で確かめる
- [x] **画面の外に出さない（NFR3.1、SD 6.1）**: `useradmin` に `console`・`localStorage`・`sessionStorage`・`history.`・`location.` の使用が無いことを検索で確かめる
- [x] **文言と項目（NFR3.2・NFR3.3）**: `detail`・`title` を読んでいないこと、`dangerouslySetInnerHTML` と要素の `style` 属性が無いこと、一覧の本文を写して返していることを確かめる
- [x] **参照の戻り（PD 3.2）**: `loadingRef`・`submittingRef` を立てた関数がすべて `finally` で戻していることを確かめる
- [x] **自動の読み直しが無い（NFR5.4）**: `setInterval` が無く、`setTimeout` は 5 秒の表示だけであることを確かめる
- [x] **報告と資格情報（NFR3.4、SD 3.3）**: 110・120 と新しい手伝いの `test.step` の題・注記・添付・`expect` の説明文・locator の名前に値が入っていないこと、`webServer.env` を触っていないこと、120 が `request` の口で `/api/admin/` の下へ書き換えを送っていないことを確かめる
- [x] **8KB を超える要求（U2 の申し送り、9節の Q-C）**: 決定の形が実装とテストのとおりであることを確かめる
- [x] 対応: NFR1.2・NFR3.1〜NFR3.4・NFR5.4・NFR5.5・NFR9.1、SD 12節

### Step 21: 記録とコミットの提案（U5）

- [x] `code-summary.md`（作ったもの・移したもの・変えたもの、名指しのテストの件数、Step 3・4・16・18〜20 の確かめの結果、FC 2.5 の表の結果、初回の大きさの (1)・(2)、計画・承認済みの文書との差、依頼者に確かめたいこと、承認の場で確かめること）、`source-manifest.json`（U5 で作った・移した・変えたアプリのソース・テスト・E2E・設定・README のパスすべて。`frontend/.npmrc` とサブモジュールの gitlink を含め、記録は入れない）、`traceability.json`（6節の対応。U5 が OK とする AC と D1〜D20・NFR の ID に実装とテストのファイル、U3・U4・U1 へ Deferred の AC はそのまま、NFR5.1・NFR5.2 は Deferred で「記録のみ・本番での判定は Unverified（持ち主の段）」、E2E-M9 は 110）を作る
- [ ] 承認の場の前に、`code-summary.md` の「依頼者に確かめたいこと」「承認の場で確かめること」の節を洗い出して並べる（`project.md` の学び）
- [ ] 3.3 の C3〜C6 の区切りで、コミットの内容とメッセージの案を依頼者に示す（生成の担当はコミットしない）
- [ ] 承認を得てコミットした後、`git status` でアプリのソースに未コミットの変更が無いこと、C3〜C6 に `aidlc/` の下が入っていないことを `git show --stat` で確かめ、ハッシュを記録する
- [ ] 依頼者の承認を得て、記録だけのコミット R2 を作る
- [ ] 対応: 段の記録、`project.md` の Change Control、`team.md` の Way of Working

### Step 22: 統合の前の関門（B5）

- [ ] colima が動いていることを確かめ、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を通す。対象DB のテストが SKIPPED になっていないことを確かめる。テストの件数（バックエンドの単体・結合、画面のファイルと件数）、カバレッジ（バックエンドの全体、画面の全体と U5 で作った・変えたファイルの行・分岐。`frontend/coverage/coverage-summary.json` から）、初回の JavaScript の大きさ (3) を記録する
- [ ] `caffeinate -i ./gradlew osvScan --rerun-tasks` を流し、走査したパッケージの数（`vendor/make-you-chic-ui/package-lock.json` を含む）、失敗の条件に当たるもの 0 件、警告の件数を記録する
- [ ] Mailpit が動いているかを確かめ（`docker compose ps`、`http://127.0.0.1:8025/api/v1/info` が 200）、動いていなければ `docker compose --profile mail up -d mailpit` で起動する。`.env` は開かない。`frontend/playwright-report/` が残っていないことを確かめる（残っていれば、報告の部品が見つけるかを記録してから消す、NFR 設計の受け入れた1点目）
- [ ] `caffeinate -i ./gradlew e2eTest` を実行する（010〜130 と 110・120。期待は 13 ファイル・152 件）
- [ ] `frontend/test-results/e2e-results.json` から次を `code-summary.md`（B5 の記録）に記録する。読むのは `stats` とテストごとの題・状態・注記と、添付 `user-admin-screen-ms`（base64 を復号した数だけ）で、ほかの中身は開かない:
  - 件数（`stats`）と、ファイルごとの件数と結果
  - 110 の結果と、飛ばした注記（`skip-reason`）が無いこと
  - 120 の組と状態ごとの結果（違反の件数と規則の名前・はみ出し・除いたコンソールの表示の件数・CSP の違反・打ち切りの記録 0 件）と、120 の全体の時間
  - `user-admin-screen-ms` の値（`list` と `nextPage`、1回目と 2〜5 回目と最大、`withinTarget`。`nextPage` は見本の応答での描画の時間で API の時間を含まないこと、`list` は差し替えの口の上乗せを含むこと。記録のみで成否にせず、本番での判定は `Unverified`）
  - 報告の部品の結果（探した値の種類の数、探したファイルの種類ごとの件数、見つかった件数 0）と、値のファイルが残っていないこと
- [ ] 記録した後に `frontend/test-results/` を中を開かずに消し（`playwright-report/` は作られないことを確かめる）、消したこと（件数）と共有していないことを記録する
- [ ] Mailpit は、この Step で起動したときは見終わったら止める。もとから動いていたときは止めない
- [ ] verify・`osvScan`・E2E のどれかが失敗したら、`team.md` の「不安定なテストと CI の失敗」で扱い、原因を直してから、この Step の verify からやり直す
- [ ] 依頼者の承認を得て、関門の記録を記録だけのコミット R3 にする
- [ ] 対応: B5 の共通の完了の条件、CP 2節・4.4・8節、`project.md` の Mandated（統合の前の確認）、NFR3.4・NFR5.1・NFR5.2・NFR5.6・NFR7.3・NFR9.3・NFR9.5・NFR9.8

### Step 23: 統合（fast-forward）

- [ ] `develop` が `21a2fdd` から動いていないことを確かめる（動いていれば 3.4 のとおり依頼者に諮る）
- [ ] 依頼者の承認を得て、`git switch develop` → `git merge --ff-only feature/260930-user-admin-b5` で統合する。`develop` の先頭が作業ブランチの先頭と同じハッシュになったこと、C1・C2・C3〜C6・R1〜R3 が区切りのまま入ったことを `git log --oneline 21a2fdd..develop` で確かめて記録する
- [ ] push の前の確かめ（基盤の設計の R-04）: リポジトリの外の make-you-chic-ui の作業フォルダで `git fetch` の後に `git branch -r --contains 34814887433c61a4eb51d3572362d6eb41edae30` を実行して公開の側の分岐（`origin/main`）が出ること、または GitHub の画面でそのコミットを開けることを確かめ、方法と結果を記録する。無ければ push の前に依頼者に知らせる
- [ ] 依頼者の承認を得て作業ブランチを消す。統合の後の記録（統合のハッシュ・ブランチを消したこと・公開の側の確かめ）を、依頼者の承認を得て `develop` の上の記録のコミット R4 にする
- [ ] push は依頼者が行う。push の後の CI の結果は Build and Test で記録する。CI のサブモジュールの取得が失敗したときは、make-you-chic-ui 側のコミットを先に公開の側へ push してから CI を再実行する（CP 5節の 6）
- [ ] 対応: `team.md` の Way of Working（fast-forward・push は依頼者）、`bolt-plan.md` の B5、CP 5節

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US1.1 利用者の一覧を見て、利用者を探す（画面） | AC1.1.8（読み込み中の伝え方）・AC1.1.9（読み込みの失敗ともう一度読み込む）。FR1・NFR7 | Step 11〜14 |
| US1.1 | AC1.1.10（0 件・検索を消す・検索で1ページ目・ページ送りで検索の文字を保つ）・AC1.1.11（検索の上限を送る前に知らせる）。FR1 | Step 7・8・11〜14・17 |
| US1.1 | AC1.1.12（部品ごとの vitest-axe、2語の氏名と誤りの状態、テーマと文字の大きさ、en） | Step 12・14・17・18 |
| US1.1 | AC1.1.1〜AC1.1.7・AC1.1.13 | Deferred（u3-user-admin-api。画面は応答のまま出す: Step 11・13） |
| US2.1 管理者の印を付ける・外す（画面） | AC2.1.8（確かめの表示）・AC2.1.9（自分の行の押せない項目と理由）・AC2.1.13（理由ごとの文言と読み直し）。FR2 | Step 7・8・11〜14 |
| US2.1 | AC2.1.1〜AC2.1.7・AC2.1.10〜AC2.1.12・AC2.1.14 | Deferred（u3-user-admin-api） |
| US2.2 管理者でなくなったことを知る（共通の扱いを使う） | AC2.2.1〜AC2.2.6 は U4。U5 は D11・W9 で `useAdminForbidden` を使う | Step 13・14（U4 の R-04）・17（U4 の R-02） |
| US3.1 利用を止める・停止を解く（画面） | AC3.1.7（確かめの表示）・AC3.1.8（自分の行の押せない項目と理由）・AC3.1.11（表示の切り替えと拒否の文言）。FR3 | Step 7・8・11〜14・17 |
| US3.1 | AC3.1.1〜AC3.1.6・AC3.1.9・AC3.1.10・AC3.1.12 | Deferred（u3-user-admin-api） |
| US3.2 停止中の利用者（画面の流れ） | AC3.2.9・AC3.2.10（主は U1） | Step 17（110 の順7） |
| US4.1 ロックを解く（画面） | AC4.1.9（確かめの表示）・AC4.1.10（成功・拒否の後の読み直し）。FR5 | Step 7・8・11〜14・17 |
| US4.1 | AC4.1.1〜AC4.1.8・AC4.1.11・AC4.1.12 | Deferred（u3-user-admin-api） |
| US5.1 利用者の氏名・言語を直す（画面） | AC5.1.2（ほかの利用者の言語は自分に当てない）・AC5.1.3（自分の氏名と言語が直後から当たる）・AC5.1.7（今の値で開く・やめる・誤りを入力欄の下に）。FR6・NFR8 | Step 5〜14 |
| US5.1 | AC5.1.1・AC5.1.4〜AC5.1.6・AC5.1.8 | Deferred（u3-user-admin-api。画面の側の確かめと2項目だけを送ることは Step 9〜14） |
| E2E の代表の流れ（M9 A、E2E-M9） | 検索 → ロック → 失敗回数を戻す → 止める → 入れない → 停止を解く → 入れる | Step 17・18・22 |
| 一覧の並びとページ送り | D1・D10 | Step 13・14 |
| 画面の中だけの状態 | D2・D3・D20 | Step 13・14・20 |
| ロックの表示と日時 | D4・D18 | Step 7・8・11・12 |
| 行の項目と押せなさ | D5・D6 | Step 7・8・11・12 |
| 確かめの表示 | D7 | Step 11・12 |
| 失敗の文言と知らせ | D8・D9 | Step 7・8・13・14 |
| 403 と 401 | D11・D12 | Step 13・14 |
| 二重の送信と待ちの表示 | D13・D14 | Step 11〜14 |
| 検索と氏名の確かめ | D15・D16 | Step 5〜8・11・12 |
| 自分の氏名と言語 | D17 | Step 13・14 |
| 印と「—」 | D19 | Step 11・12 |
| 画面の判定とサーバー | NFR1.1 | Step 7・8・11・12・19 |
| 403・401 の扱い | NFR1.2 | Step 13・14・20 |
| 画面の外に出さない | NFR3.1 | Step 13・14・20 |
| 失敗の文言 | NFR3.2 | Step 7・8・12・14・20 |
| 応答の項目 | NFR3.3 | Step 9・10・12 |
| E2E の報告に値を残さない | NFR3.4 | Step 15〜18・22 |
| 120 の書き換えの打ち切り | NFR3.5 | Step 15・17・18・22 |
| 画面の時間（記録のみ、本番は Unverified） | NFR5.1・NFR5.2 | Step 17・22、Build and Test に引き継ぐこと |
| API の時間と要求の形 | NFR5.3 | Step 10、Build and Test に引き継ぐこと |
| 読み方 | NFR5.4 | Step 13・14・20 |
| 待ちの見せ方と二重の送信 | NFR5.5 | Step 11〜14・20 |
| 初回の大きさ | NFR5.6 | Step 1・4・22 |
| アクセシビリティ | NFR7.1・NFR7.2 | Step 11〜14 |
| 実際のブラウザの検査 | NFR7.3 | Step 17・18・22 |
| 多言語 | NFR8.1〜NFR8.3 | Step 7・8・11〜14 |
| 静的検査と CSP | NFR9.1 | Step 11・19・20・22 |
| 依存を足さない | NFR9.2 | Step 15・19 |
| make-you-chic-ui の固定先 | NFR9.3 | Step 4・19・22・23 |
| `ignore-scripts=true` | NFR9.4 | Step 3 |
| カバレッジ | NFR9.5 | Step 22 |
| 性質ベースのテスト | NFR9.6 | Step 8 |
| テストの書き方と `fieldErrors` の移し | NFR9.7 | Step 5・6・12・14 |
| 流れの E2E の本数と既存の E2E | NFR9.8 | Step 3・4・17・18・22 |
| 見本と本物の照らし合わせ | NFR9.9 | Step 15・17・18 |
| 機能設計の上流との差 | (a)〜(k) | Step 7・11〜14 |
| B5 の完了の条件 | 固定先・`.npmrc`・S1〜S6・押せない項目と理由・自分の氏名と言語・画面部品ごとのアクセシビリティの検査・E2E の代表の流れ | Step 3〜18・22 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、主な境界のテストを置く（Testing Contract の `strategy_volume`）。件数の見込みと中身は `unit-test-instructions.md` 3節の表のとおり。

| 部品 | 単体（`*.test.ts(x)`） | 主な境界のテスト |
|---|---|---|
| 共通への移し | `fieldErrors.test.ts`（移すだけ、件数は変えない） | プリファレンスの既存のテストを変えずに通す |
| 純粋な関数 | `rowActions.test.ts` 6（全組 16 通りを1件の表で・性質 4・並び 1）・`searchInput.test.ts` 8・`profileInput.test.ts` 6・`lockedUntil.test.ts` 6・`failureMessage.test.ts` 8・`focusTarget.test.ts` 5 | — |
| API の受け渡し | `api/userAdminApi.test.ts` 10 | 偽物の `fetch` で ApiClient を通す（`Accept-Language`・401 の更新は ApiClient のまま） |
| 登録と文言 | `registration.test.tsx` 5 | 骨組みの登録の検査（`validateRegistrations`）を通す |
| 画面の部品 | `UserSearchBox.test.tsx` 7・`UserTable.test.tsx` 8・`UserRowActions.test.tsx` 8・`ConfirmActionDialog.test.tsx` 8・`EditProfileDialog.test.tsx` 8 | 固定先を上げた本物の `Dropdown`・`Table`・`Modal` を通して描く |
| 画面の組み立て | `UserAdminPage.test.tsx` 40 ほど（FC 8節の項目を分けた件数） | 骨組みの Provider（`AdminForbiddenProvider`・`DisplaySettingsProvider`）と ShellLayout の中に描き、403 から S6、自分の言語の反映と `Accept-Language` までを通す |
| 実際のブラウザ | — | 110（1 件）・120（21 件）。既存の 010〜100・130（130 件）を変えずに流す |

- `UserAdminPage.test.tsx` が 5〜8 件を大きく超えるのは、フックと画面の決まり（D1〜D4・D9〜D14・D17・D20）と、機能設計の承認の場の R-01〜R-06、NFR の確かめ（NFR1.2・NFR3.1・NFR5.4・NFR5.5・NFR8.3）、U4 の R-04 を1つの画面で確かめるため。
- Standard の「主な境界の結合テスト」は、画面の単位のため、骨組みの Provider の中に描く画面のテストと、実際のブラウザの E2E（110・120）で満たす。バックエンドの結合テストは足さない（サーバーの 401・403・204 と監査は U3 の持ち物）。
- 性質ベースのテストは fast-check の既定の 100 回で、`rowActions`（4）・`searchInput`（2）に当てる（NFR9.6）。失敗のときの種の再現の仕方は各テストの先頭に書く（`unit-test-instructions.md` 5節）。
- どのテストも成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。
- `team.md` の認証・認可・監査の必須テストのうち U5 に当たるのは、画面の側の「管理者の印の変更」「利用停止」「ロックの解除」「最後の管理者の保護」の表示と文言、「画面で管理メニューを隠すことはサーバー側の検査の代わりにしない」、「利用者の管理の漏えい」の画面の側（コンソール・保存・URL）で、サーバー側の 401・403・200・最後の管理者の保護・監査・漏えいの結合テストは U3・U1 が受け持つ（NFR1.1）。U5 は失敗の場合（403・code の無い 403・401・404・6つの 409・400・通信の失敗・形の違う本文）のテストを含める（`project.md` の Mandated）。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| ID | 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|---|
| D-1 | 層の並び（FC 2.1 の一覧、LC 1節） | 置き場ごとの一覧で、順は決めていない | 依頼の順（`.npmrc`・固定先 → 共通への移し → 純粋な関数と文言 → API → 部品 → 組み立て → E2E の土台 → E2E → 静的検査）に並べた | test-after の層の順のまま、各 Step の終わりで型の検査と名指しのテストが通るようにするため |
| D-2 | `.npmrc` と固定先の順（CP 3節「計画で決める」） | 決めていない | `.npmrc`（C1）を先、固定先（C2）を後にする（9節の Q-A の決定 A） | 影響の読みにくい `ignore-scripts=true` を最も早く確かめ、固定先の更新の確かめを `ignore-scripts=true` の後の形（本番と同じ入れ方）で流すため |
| D-3 | 固定先の具体のコミット（TS の NFR9.3、CP 3節） | `3481488` 以降のどれか。計画の承認の前に部品の口を確かめる | `3481488`（`34814887433c61a4eb51d3572362d6eb41edae30`）。リポジトリの外の make-you-chic-ui の作業フォルダで読み取りだけで確かめた: (1) その作業フォルダの `main` の先頭で、追跡の分岐 `origin/main` も含む（最後に取り込んだ時点）。(2) `077f5b4..3481488` の差分は `docs/integration-guide.md`・`Dropdown.tsx`・`Dropdown.css`・`Dropdown.test.tsx` の4ファイル。(3) FC 2.5 の表: 押せない項目はクリック・Enter・Space で `onClick` を呼ばず、メニューも閉じない（期待どおりの R1）、`aria-disabled="true"` が付き、`href` の項目では `href` を出さない。`description` は項目名の下に出て `aria-describedby` で結ぶ。矢印・Home・End で押せない項目にも移り、開いたときは最初の項目へ。文字は既存の `--color-text-muted`（コミットの説明は light・dark とも 4.5:1）。開き口は今と同じく `cloneElement` で `onClick` を上書きし、`aria-haspopup` は `'true'`。`Button` の `loading` は `aria-disabled`・`aria-busy` で `onClick` を呼ばない。「依頼者に戻す差」は無い見込み。押せない項目のホバーの背景（`--color-surface-hover`）の上の文字のコントラストは 120 の状態 (11) で確かめる | FC 2.5 の表を計画の前に確かめておき、B5 の中で崩れていないことを Step 4 でテストとともに確かめ直す。より新しいコミットは取り込まない（必要な口がそろい、差分が小さいため） |
| D-4 | `lockedUntil` の受け取り（FC 5節・5.1） | 型は `lockedUntil?: string`、「`lockedUntil` は無いか文字列」 | 本物の応答は、ロックしていないとき `"lockedUntil": null` を返す（`AdminUser` の `Instant` が `null`、Jackson の `NON_NULL` の設定が無い）。`userAdminApi.ts` は `null` と無いを同じく「持たない」に写し、型は `lockedUntil?: string` のまま。E2E の形の照らし合わせも `lockedUntil` を「文字列か `null`」とする | C3 の意味（ロックしていなければ時刻が無い）と同じで、`null` を形の違いとすると一覧がいつも通信の失敗になるため。設計の型は変えない |
| D-5 | `useUserAdminText` の言語の引数（FC 3.4「`lng` で引けることはコード生成で確かめる」） | 確かめる | Step 14 で、ja の画面で `language: 'en'` を渡したときに英語の文言が返ることを、機能の文言が ja・en の両方とも i18next に読み込まれている形で確かめる。読み込まれていなければ止めて諮る | 承認済みの形のとおりで、確かめの置き場を決めた |
| D-6 | 古い読み直しの 403（U4 のレビューの R-04、D3） | 古い答えは捨てる（D3）。古い 403 の扱いは書いていない | 古い読み直しの答えは、失敗（403 を含む）も含めて捨て、`useAdminForbidden` に渡さない。最新の要求の 403 だけを渡す。テストは、古い読み直しの 403 が後から届いても S6 にならず、最新の読み直しの 403 で S6 が出ることを、本物の `AdminForbiddenProvider` と ShellLayout の中で確かめる | D3 の「最後に始めた読み直しの答えだけを使う」を失敗にも当てる形。権限が無ければ最新の要求も 403 になるため、表示が遅れることは無い |
| D-7 | 部品ごとの vitest-axe の表示の設定の組（FS 8節、TS の NFR7.2「既存の表示の設定のテストの手伝いで流す」） | light・dark と sm・md・lg の組を既存の手伝いで流す | `renderUserAdmin` にログイン状態の提供元の利用者の設定（テーマと文字の大きさ）を渡す口を置き、`UserAdminPage.test.tsx` で6つの組（light・dark × sm・md・lg）を表の1件で vitest-axe にかける。ほかの部品のテストは既定（light・md）の1組 | jsdom は文字のコントラストを計算しないため、色の組の確かめは 120 の実際のブラウザの 20 組で行う。部品のテストは組を変えても構造の違反が出ないことを確かめる |
| D-8 | E2E の手伝いの置き場と名前（LC 3節「計画で決める」、NS の申し送り「見本の置き場」） | 決めていない | `frontend/e2e/support/` の下に新しいファイル6つ（`traceMode.ts`・`secretValues.ts`・`userAdminRun.ts`・`userAdminFixtures.ts`・`adminApiRoute.ts`・`userAdminDiagnostics.ts`）を置く。値のファイルは `test-results/e2e-secret-values.json`。設定のファイルは `support/traceMode.ts` だけを読む（`playwright.config.ts` を読む `adminLogin.ts` と循環しない） | 既存の `support/` の形にそろえ、既存のファイルを変えずに済むため |
| D-9 | わざと値を入れた確かめの形（SD 3.8・4.3、CP 4.5、基盤の設計の R-03 と2回目の申し送り） | 確かめの前にコミットしておくか差分だけの形にし、元へ戻し、`git diff`・`git status` で確かめる | 報告の部品の確かめ（(1)〜(8)）は、リポジトリの外の権限 700 の一時の場所に置く使い捨ての台本から報告の部品を呼び、作った報告のファイルを探させる（作業フォルダのファイルを書き換えない）。差し替えの口の3つの確かめは、追跡しない一時の E2E のファイルを足して流し、消す。どちらの後も `git status`・`git diff` で残っていないことを確かめる | 2回目の申し送りの「差分だけの形」に当たり、コミット済みのファイルを書き換えないため戻し忘れが起きない。報告の部品は探す先を設定で受けるため、使い捨ての台本から呼べる |
| D-10 | 初回の大きさの時点（PD 4節） | 固定先を上げる前と U5 の後の2つ | 固定先を上げた後（Step 4）を足して3つ | 固定先の更新と U5 の増えを分けて見るため。目安と扱いは変えない |
| D-11 | 110 の `runTag`（SD 3.4） | 時刻の16進と `randomBytes(8)` の16進。`createRegisteredUser` は変えない | 新しい手伝い `support/userAdminRun.ts` の `newUserAdminRunTag()` で作る。既存の `invitationSeed.ts` の `newRunTag`（乱数 6 文字）は使わない | 承認済みの形のとおり。既存の手伝いを変えないため |
| D-12 | fast-forward の統合の途中のコミット（`team.md` の Way of Working） | 統合の前に1コマンドの検査を通す | 関門（Step 22）は作業ブランチの先頭で1回。C1・C2 の後には Step 3・4 の確かめを流すが、C3〜C6 の途中のコミットは単独で verify を流さない。fast-forward のため、途中のコミットも `develop` に入る | 前の Intent（followup-fixes）の fast-forward と同じ扱い。途中のコミットは型の検査が通る順にしてある（3.3） |
| D-13 | 記録の置き場（依頼） | — | 記録は作業ブランチの上で記録だけのコミット（R1〜R3）にし、fast-forward でそのまま `develop` に入る。統合の後の記録は R4 | fast-forward のため、squash から外す手順は要らない |
| D-14 | 110 の差し替え（FS 9節「何も差し替えない（本物の WAR・Mailpit・一時の内部DB）」） | 110 は何も差し替えない | 110 の順9（U4 のレビューの R-02 の確かめ）だけ、管理者でない U の新しいページのログインの応答（`POST /api/auth/login`）の `user.admin` を真に書き換える差し替えを1つ置く（復元・更新の応答は書き換えない）。順1〜8 は今までどおり何も差し替えない | 9節の Q-B の決定 A。サーバーの状態を変えずに、画面の判定と本物の 403 の一致を実際のブラウザで確かめるため。管理者の印の付け外しを流れに入れない（M9 A）形を保つ |

ほかに、上流（要件・機能設計・契約 C3〜C5・NFR 要件・NFR 設計・基盤の設計）と違う作りはない。9節の Q-C の決定 A は残る危険を受け入れる記録で、作りの差は無い。

## 9. 依頼者の決定

計画の承認の前に諮った4つの論点について、依頼者が次のとおり決めた（4問とも A）。計画の各 Step と 3節・8節はこの決定に合わせてある。

**Q-A `.npmrc` と固定先のコミットの順**（CP 3節、8節の D-2）

- 依頼者の答え: **A**
- 決定: `.npmrc`（C1）を先、固定先（C2）を後にする。C1 の後に `npm ci`・verify・E2E、C2 の後に `npm ci`・既存の画面のテスト・E2E を流す（Step 3・4、3.2 の依頼の分け方）
- 選ばなかった案: B（固定先（C1）を先、`.npmrc`（C2）を後）
- 理由: `ignore-scripts=true` の影響（スクリプトを要する依存）は読みにくく、最も早く確かめたい。固定先の更新の確かめは、本番と同じ入れ方（スクリプトを動かさない）で流せる。どちらも E2E を2回流す点は同じ

**Q-B 画面と本物のサーバーを通した 403 の確かめ（U4 のレビューの R-02）の作り方**（Step 17 の 110 の順9）

- 依頼者の答え: **A**
- 決定: 110 の中で、管理者でない U の新しいページにだけ、ログインの応答（`POST /api/auth/login`）の `user.admin` を真に書き換える差し替えを置いてログインする（復元・更新の応答は書き換えない）。画面は U を管理者と見てサイドバーに「利用者の管理」を出し、それを開くと本物の一覧の API が 403 `ACCESS_DENIED` を返し、U4 の S6 が出て、ログインの状態の読み直し（本物の更新）で管理のメニューが消える。サーバーの状態は変えない（E2E の一時の内部DB に「アクセスの拒否」の監査が1件残るだけ）。既存の `loginPreferences.ts` と同じ書き換えの考え方を 110 の中だけで使う。承認済みの機能設計 9節の「何も差し替えない」との差は 8節の D-14 に記録した
- 選ばなかった案: B（110 の中で U に管理者の印を付け、U が画面を開いた後に印を外して操作させる。本物の流れに近いが、機能設計の 9節「管理者の印の付け外しはこの流れに入れない（M9 A）」と食い違う）、C（E2E では行わず、U4 の記録（サーバー側の `AdminPathBoundaryIT` などが 403 の `code` を確かめている）と U5 の画面のテストで足りるとして記録する）
- 理由: 自分で作った管理者でない利用者を使い、承認済みの流れの範囲（印を変えない）のまま、画面の判定（`isAdminForbidden`）と本物の応答の一致を実際のブラウザで確かめられる

**Q-C 8KB を超える要求の HTML の 400（U2 の申し送り）**（Step 13・14・20）

- 依頼者の答え: **A**
- 決定: 残る危険として受け入れて記録する。画面が送る一覧の要求は、検索の文字を 254 コードポイントで止めるため（D15）、符号化しても数 KB で 8KB に届かず、ページの番号は画面が作る数だけ。万一 Tomcat の HTML の 400 が届いても、ApiClient は `code` の無い 400 として渡し、画面は一覧なら読み込みの失敗、操作なら一般の文言、保存なら failed にする。このことを `code` の無い 400 のテスト（一覧・操作・保存）で確かめる
- 選ばなかった案: B（画面で URL の長さを数え、8KB を超えるなら送らずに上限を知らせる仕組みを足す。承認済みの設計に無い作り）
- 理由: 画面の入力の上限で届かないことが決まっており、届いたときも一般の失敗として安全に扱えるため

**Q-D `playwright.config.ts` の直しの時点**（CP 4.4「直しをどの順に置くかはコード生成の計画で決める」）

- 依頼者の答え: **A**
- 決定: Step 3・4 の E2E（固定先と `.npmrc` の確かめ）は今の設定のまま流し、`gate-decisions.md` の U5 R-02 の手順（json から記録してから報告を消す）で扱う。設定の直しは E2E の土台（Step 15）で行う
- 選ばなかった案: B（設定の直しを Step 3 より前に置き、Step 3・4 の E2E から html と trace を作らない形にする）
- 理由: Step 3・4 の確かめは「その1つの変更の影響」を見るためのもので、報告の設定の変更を混ぜると切り分けが崩れる。報告は決まった手順で消すため、値が残る間は短い

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-09-29 の時点で 12 パッケージ。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。利用者の状態を変える操作（利用停止・管理者の印の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理者の印の変更: 印を付けた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い印に頼らない）。印を外す前に出したトークンの扱いと、自分の印を外す操作の扱いは、決めた側の動作を明示したテストにする。★印を外す前に出したトークンの扱い、自分の印を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者の印を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの印を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・管理者フラグなし（403）・管理者（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理者の印や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（印の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01)"
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
  "input_sha256": "sha256:1d19ce9bfd0f0e6eba516940c5111b116a2d58af04709ea78cc47cbad0196f0a",
  "contract_sha256": "sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a"
}
```

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は U5 では新しい骨組みが無いため Step 1（作業の場と基準）と Step 3・4（`.npmrc` と固定先）、テストの実行の準備は Step 2（最初のテストの Step 6 より前）。データの形と DB の振る舞い・DB アクセス・業務処理・API（サーバー）の層は U5 に無い（画面の単位で、保存するデータ・サーバーの変更を持たない。サーバーの 401・403・204 と監査は U3）。画面（Frontend behavior）の層は、共通への移し（Step 5・6）、純粋な関数と文言（Step 7・8）、API の受け渡し（Step 9・10）、画面の部品（Step 11・12）、画面の組み立て（Step 13・14）、E2E の土台（Step 15・16）、E2E（Step 17・18）に分けた。環境とビルドの設定（E2E の設定と報告の部品）は Step 15、静的検査と構造と関門は Step 19・20・22、文書と記録は Step 17（README）・21・23 で行う。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジと件数の実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、画面の全体と U5 で作った・変えたファイルの行・分岐、バックエンドの全体をもう一度実測して記録する（NFR9.5） | Build and Test |
| CI | 依頼者の push の後、CI（`./gradlew verify`、サブモジュールの取得を含む）が通ることを確かめる。失敗したら `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。サブモジュールの取得の失敗は CP 5節の 6 | Build and Test |
| E2E | `./gradlew e2eTest`（010〜130 と 110・120）を流し、110 で飛ばした注記が無いこと（機能設計の R-08）、120 の組と状態ごとの結果、報告の部品の結果（見つかった件数 0）を記録する。json の報告は記録の後に消す（設定の直しの後は html を作らない） | Build and Test |
| 画面の時間 | `user-admin-screen-ms` を json の添付から写す（base64 を復号）。`nextPage` は見本の応答での描画の時間で API の時間を含まないこと、`list` は差し替えの口の上乗せを含むことを書く。記録のみで成否にせず、本番での判定は `Unverified` | Build and Test（記録）・performance-validation・observability-setup・feedback-optimization（判定） |
| API の時間 | 一覧・氏名と言語・5つの操作の p95（U3 の NFR5.1・NFR5.3〜NFR5.6）。U5 は測り直さない（NFR5.3） | performance-validation（U3 の持ち物） |
| 初回の JavaScript の大きさ | Step 1・4・22 の値（gzip）を写す。目安を超えても警告だけ（NFR5.6） | Build and Test |
| スモークテスト | 配備の後のスモークテストの U5 の分は、管理者でログインしてサイドバーの「利用者の管理」から一覧が開けること。5つの操作と氏名・言語の変更は監査に残り利用者の状態を変えるため、入れるかを決め、行うときは先に依頼者に伝える（CP 5節の 8） | deployment-pipeline |
| 送信中に通信が止まったときの抜け出し | 画面の中に抜け出しの手は無く、再読み込みで抜ける（PD 3.4 の残る危険）。運用で問題が出たら改めて決める | 後の Intent |
| 8KB を超える要求 | 9節の Q-C の決定のとおり記録する | Build and Test（記録） |
