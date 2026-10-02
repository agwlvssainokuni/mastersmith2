# Code Generation Plan — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 のコード生成の計画を示す。Bolt は B2 共通の土台（`inception/delivery-planning/bolt-plan.md`、この Intent の2つ目の Bolt）で、B2 は U2（u2-shared-paging）と U4 を同じ作業ブランチ `feature/260930-user-admin-b2` で作る。前半の U2 は生成とコミット（C1 d1c751b・C2 e20c3b7）を終えている。この計画は U4 の分と、B2 全体の統合の前の関門と統合（U2 の計画の Step 15 と同じ1回。この計画の Step 17）を扱う。

U4 は画面（ui）の単位で、管理の画面すべて（管理の入口・DSL の管理・招待の管理、のちに U5 の利用者の管理）が管理の API から「権限が無い」（`/api/admin/` の下・403・code `ACCESS_DENIED`）を受けたときの表示（S6）とログインの状態の読み直しを、画面の骨組み（AppFrame）の1か所にまとめる。あわせて、管理者が自分自身の氏名・言語を変えたときに画面へ当てる口（契約 C4 の `useApplyOwnProfile`、U5 が使う）を作る。`backend/` と `vendor/make-you-chic-ui` は変えない。新しい API・依存・スキーマ・設定は持たない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260930-user-admin/`（以下「記録」）とする。画面のファイルは `frontend/src/` の下を `src/...` と書くことがある。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u4-admin-forbidden-ui/functional-design/functional-spec.md`・`frontend-components.md`・`traceability.json` | 用語（1節）、置き場（2節）、決まり D1〜D14（3節）、状態の移り変わり（4節）、流れ W1〜W8（5節）、失敗の場合（6節）、文言（7節）、テストの方針（8節）、上流との差 G1〜G9（10節）、承認の場の決定（R-01〜R-06、D9・G1・G5・G3 の受け入れ）。部品の階層（FC 1節）、モジュール（FC 2節）、口 3.1〜3.7、props と state（FC 4節）、操作の流れ（FC 5節）、API との受け渡しと3つの画面の置き換え（FC 6.2）、テストで確かめる内容（FC 7節）、既存への影響（FC 8節） |
| `construction/u4-admin-forbidden-ui/nfr-requirements/security-requirements.md`・`performance-requirements.md`・`tech-stack-decisions.md` | NFR1.1〜NFR1.5・NFR3.1・NFR3.2・NFR5.1・NFR7.1〜NFR7.3・NFR8.1・NFR8.2・NFR9.1〜NFR9.12、残る危険 R1〜R4、承認の場の決定 R-01〜R-04 と申し送り（130 の2語の氏名の作り方、コンソールの除外の置き場、R4 の戻り方） |
| `construction/u4-admin-forbidden-ui/nfr-design/security-design.md`・`performance-design.md`・`logical-components.md` | 判定の形（SD 2.2）、画面の側の印（SD 2.3）、401 とログアウト（SD 2.4）、画面に出す値（SD 3節）、`applyOwnProfile` の ref の形と確かめ（SD 4.2・4.3）、130 の報告と資格情報（SD 5節）、CSP（SD 6節）、残る危険 R1〜R5（SD 8節）、上流との差 S-1〜S-7（SD 9節）、承認の場の決定（R-03・R-04・R5 の受け入れ、R-01・R-02 の申し送り）。置き換えの順序と描画の回数（PD 2節）、読み直しの回数（PD 3節）、配信物の大きさ（PD 5節）。部品の一覧（LC 1節）、依存の向き（LC 3.1）、130 の組み立て（LC 6.1〜6.4）、テストと関門（LC 7節） |
| `construction/u4-admin-forbidden-ui/infrastructure-design/cicd-pipeline.md`・`monitoring-design.md`・`infrastructure-specification.md` と `construction/infrastructure-design/gate-decisions.md` | verify の段ごとの確かめ（CP 2節）、依存と make-you-chic-ui（CP 3節）、130 の置き場・実行の時点・申し送り R-01・R-02 と README（CP 4節）、報告と秘密（CP 5節）、記録すること（CP 9節）、承認の場の決定（R-01〜R-03、スモークテストの申し送り）。監視は足さない（MD）。2回目の承認の場の U4 の申し送り（`monitoring-design.md` 4節の参照、Build and Test での報告の片付けの持ち主と順） |
| `inception/contract-design/contract-summary.md` の C4（と共通の決まり） | `isAdminForbidden`・`AdminForbiddenView`・`useAdminForbidden`・`useApplyOwnProfile` の名前と振る舞い。名前と置き場は U4 の機能設計で決めたとおり |
| `inception/delivery-planning/bolt-plan.md` の B2 と共通の完了の条件 | 完了の条件（403 の共通の扱い、3つの画面の置き換えとテストの直し、`useApplyOwnProfile`、画面部品ごとのアクセシビリティの検査、招待の画面は B2 の中で一度だけ直す）、E2E を統合の前に流すこと、単位ごとの squash にしてよいこと |
| `inception/units-generation/unit-of-work.md`（U4）・`unit-of-work-story-map.md`（US2.2 の主、US5.1 の従） | 単位の境界、作らないもの（U5 の画面）、確かめ方 |
| `inception/requirements-analysis/requirements.md` の FR2.2・FR2.3・FR6.3・FR8.1・FR8.2・NFR7・NFR8 と `inception/user-stories/stories.md` の US2.2（AC2.2.1〜AC2.2.6）・US5.1（AC5.1.3） | 要件と受け入れ基準 |
| 同じ Bolt の前の単位 `construction/u2-shared-paging/code-generation/code-generation-plan.md`（Step 15）・`code-summary.md`・`generation-notes.md` | B2 の関門と統合の手順（単位ごとの squash の条件、`osvScan --rerun-tasks`、E2E の報告を json から記録してから消す、Mailpit の扱い）、U2 の実測（画面の全体 行 97.44%・分岐 92.67%、画面のテスト 91 ファイル・736 件） |
| 前の Bolt の記録 `construction/u1-user-suspension/code-generation/code-summary.md` | B1 の経過（E2E 10 ファイル・110 件、225 秒。Mailpit がもとから動いていたときは起動も停止もしない。報告の片付けの記録の形） |
| 決まり `aidlc/spaces/default/memory/team.md`・`project.md` | 作業の場・統合・コミット、Testing Posture（画面のテスト・アクセシビリティ・`waitFor`・時間の上限・性質ベースのテスト・E2E の本数と初期管理者・テストのデータ）、Code Style（名前つきの export・`enum` を使わない・ライセンスヘッダー・素の CSS・`src/features` と `src/shared` の境界）、Change Control、Forbidden（make-you-chic-ui を変えない）・Mandated（利用者の状態の判定はサーバー側）、学び（承認の場の決定の洗い出し、Playwright の報告、axe は現実に近いデータで、`caffeinate -i`） |

既存のコード（読むだけで確かめた）: `frontend/src/shared/api-client/apiClient.ts`（内部の `refreshOnce` と `pendingRefresh`、`registerAuthHandlers`）・`apiError.ts`（`ApiError` は `kind`・`status`・`code`・`problem`）、`src/app/routing/decideRoute.ts`（`ADMIN` の画面で管理者でないと `NOT_FOUND`）・`AppRouter.tsx`、`src/app/layout/ShellLayout.tsx`、`src/app/App.tsx`、`src/app/testing/renderWithProviders.tsx`（`ToastProvider`・`ModalStackProvider` を持たない）、`src/app/display-settings/displaySettingsStore.ts`（`savedUser`・`applyUserPreferencesFor`・`writeStoredLanguage`）・`DisplaySettingsProvider.tsx`（`decideScreenSettings`）、`src/app/pages/NotFoundPage.tsx`・`Page.css`、`src/app/i18n/messages/ja.ts`・`en.ts`・`messages.test.ts`、`src/features/admin/AdminAreaPage.tsx`・`adminAreaStatus.ts`（`FORBIDDEN_STATUS`）・`adminApi.ts`（`ADMIN_CHECK_PATH`）、`src/features/dsl/useDslAdmin.ts`（`FORBIDDEN`・`forbidden`・`checkForbidden` の4か所の呼び出し）・`DslAdminPage.tsx`・`api/dslApi.ts`（`DSL_API_ROOT`）・`testing/renderDsl.tsx`、`src/features/invitation/useInvitationAdmin.ts`（`isUnauthorized` の4か所）・`api/invitationApi.ts`（`INVITATION_API_ROOT`）・`failureMessage.ts`・`testing/renderInvitation.tsx`、各画面のテスト（`AdminAreaPage.test.tsx` の 403 の2件、`adminAreaStatus.test.ts` の 403 の4件、`DslAdminPage.test.tsx` の `shows the not found screen when the server answers 403`、`InvitationAdminPage.test.tsx` の 403 の3か所、`failureMessage.test.ts` の 403 の1件）、振り分けのテスト（`decideRoute.test.ts` の `shows not found to logged-in non-admins …`、`AppRouter.test.tsx` の `does not show ADMIN screens to logged-in non-admins`）、`frontend/e2e/060-invitation-accessibility.e2e.ts`（組ごとの検査の形、`SIDEBAR_ITEM`、`login.rewritten`）・`support/loginPreferences.ts`（`routeLoginPreferences` を使うのは 060 だけ。080 は説明文に名前が出るだけ）・`support/pageProblems.ts`・`support/adminLogin.ts`・`support/axe.ts`・`support/displayCombos.ts`、`frontend/playwright.config.ts`・`playwright-secret-check-reporter.ts`・`vitest.config.ts`・`scripts/check-bundle-size.mjs`・`scripts/check-license-header.mjs`、`build.gradle.kts`（`verify` の段、`frontendBundleSize`、`e2eTest` の Mailpit の確かめ）、make-you-chic-ui の `Alert.tsx`（`variant` が info のとき `role="status"`、`data-testid="alert"`）・`Avatar.tsx`（`getInitials` は最初の2語から最大2文字、`mycui-avatar`）・`AppShell.tsx`、`README.md`（E2E の表、640 行目の「管理者でない利用者が URL を直接開くと『ページが見つかりません』」）。

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログと各文書の「承認の場の決定」の節から洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ `audit/sakura-local-4e42a93f87ce.md` の `GATE_REJECTED`（Request Changes の理由）・`GATE_APPROVED` と、U4 の各文書の終わりの「承認の場の決定」の節、`gate-decisions.md` を読んだ。U4 と B2 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes、R-01） | 自分の氏名と言語の反映で保存するテーマと文字の大きさは、見せ方を除いた当てている値（`decideScreenSettings` に `preview: null`）から取る | `applyOwnProfile` の `current` をその値から取り、試しのテーマが保存されないテストを足す | Step 11・12 |
| 機能設計（R-02） | 権限が無い URL はパスが変わった描画の中で捨てる。今の URL は ref で読む。状態と関数の context を分け `report` を同一に保つ。読み直しは終わるまで重ねない | FC 3.3 の Provider の仕組みのとおり作り、R-02 の3点のテストを足す | Step 5・6 |
| 機能設計（R-03） | `renderDsl`・`renderInvitation` と `AdminAreaPage.test` の描き方を書き換えの範囲に入れる | ShellLayout の中に描く選択とログイン状態の提供元を渡す口を足す（既定は今のまま） | Step 10 |
| 機能設計（R-04、受け入れ） | 読み直しの後もまだ管理者だったときは、その URL にいる間は S6 のまま（再試行の手段は足さない） | 足さない。130 は E2E のサーバーで初期管理者が管理者のままのため、この形（R1）を通る | Step 13 |
| 機能設計（R-05） | Alert（info）は `role="status"` であることをテストで確かめる。振り分けは同じ形の木を描き、ForbiddenByApi → ForbiddenByRoute で ShellLayout を作り直さない | `AppRouter` の `ADMIN_FORBIDDEN` を SCREEN と同じ形の木で描き、要素の同一・フォーカス・サイドバーの開閉のテストを足す | Step 7・8 |
| 機能設計（R-06） | AC2.2.2 のきっかけ（403 の後の読み直し・トークンの更新・画面の読み直し・ログインし直し）ごとにメニューが消えることを確かめる | `ShellLayout.test.tsx`・`AppRouter.test.tsx` に足す。`traceability.json` の target に FR・NFR の ID を添える | Step 8・16 |
| 機能設計（D9・G1・G5・G3、受け入れ） | 読み直しの失敗はログアウト。管理者でない人にも S6（振り分けのテスト2ファイルも書き換え）。ADR-005 と `unit-of-work.md` は書き換えない | そのとおり作る。設計の文書は書き換えない | Step 7〜10 |
| NFR 要件（Request Changes、R-01） | 130 は上の帯の氏名を架空の2語にした状態で 20 組すべてを検査し、Avatar の頭文字が2文字であることを確かめて記録する。作り方はコード生成の計画で決める | NFR 設計の S-5 のとおり、`loginPreferences.ts` の型に省略できる `displayName` を足す（8節の D-4） | Step 13・17 |
| NFR 要件（R-02・R-03・R-04） | 枝番の寄せ方の記録、残る危険 R4、130 が ForbiddenByRoute を確かめない理由とコンソールの除外は 130 の側だけ | ForbiddenByRoute は部品のテストで確かめる。`pageProblems.ts` は変えない | Step 8・13 |
| NFR 要件の申し送り（監査ログ） | 「コード生成の計画（… E2E の見本の置き場 …）」 | U4 の見本（`GET /api/admin/check` の 403 の本文）は 130 の中の定数に置く。共有の `support/` に見本のファイルを足さない（`/api/admin/check` を差し替えるのは 130 だけのため） | Step 13 |
| NFR 設計（Request Changes、R-03） | `applyOwnProfile` の描画の確定の前の隙間は R5 として受け入れ、テストは足さない | 足さない（SD 4.3 の「描画の確定の後に呼ぶ形」だけを確かめる） | Step 12 |
| NFR 設計（R-04） | 403 を受けるたびの読み直しで、一時的な不調の間にログアウトが増えることを受け入れる | 更新の失敗の扱いを変えない | Step 3・5 |
| NFR 設計（R5） | 既存の `applyUserPreferences`・`setPreview`・`setLanguage` の同じ形の危険は後の Intent | 変えない。「Build and Test に引き継ぐこと」に写す | — |
| NFR 設計（R-01、申し送り） | 130 で読み直しの応答を待つ約束（`waitForResponse`）を、管理の入口を開く操作より前に作る | Step 13 の 130 の順4（約束を作る）→ 順5（サイドバーの「管理」を押す）に明記した | Step 13 |
| NFR 設計（R-02、申し送り） | 「ホームへ戻る」の確かめを全組で行うか既定の組だけか、順10 の対象の組、20 組の実行時間 | 全 20 組で行う（9節の Q-A の決定 A）。順10 も全組。実行時間の見込みは 130 の全体で 1〜2 分（実測して記録） | Step 13・17 |
| 基盤の設計（1回目、R-01・R-02） | 監視の記述の言い方（403 の数え方）を直す | 監視は足さない。コードには関わらない | — |
| 基盤の設計（1回目、U5 R-02 の全単位の決定・R-03 の受け入れ） | B2 の E2E の後は json の報告から結果を記録してから `frontend/playwright-report/`・`frontend/test-results/` を消し、消したことと共有していないことを記録する。設定の先取りはしない | `playwright.config.ts` は変えない。記録する項目を Step 17 に決めた | Step 1・17 |
| 基盤の設計（1回目、スモークテストの申し送り） | S6 の確かめを入れるかは deployment-pipeline の段で決める | 「Build and Test に引き継ぐこと」に写す | — |
| 基盤の設計（2回目、U4 の申し送り） | `monitoring-design.md` 4節の参照（`cicd-pipeline.md` 4.4）は 5節と読み替える。Build and Test で E2E を流した後の報告の片付けの持ち主と、記録してから消す順 | 2.2 の読み方に書いた。持ち主と順は「Build and Test に引き継ぐこと」に書いた | — |
| 基盤の設計（CP 4.3、申し送り） | README の E2E の表に 130 の行と「130 について」の節を足す | 足す。あわせて README の「管理者でない利用者が URL を直接開くと『ページが見つかりません』」（DSL の管理の節）を S6 の説明に直し、403 の共通の扱いの節を足す（8節の D-6） | Step 13 |
| B2 の U2 の計画（Q-A A、3節） | 単位ごとの squash。U4 を始めた後に U2 のソースを直したときは Bolt 全体の1コミットに切り替える | 前提とする。U4 の生成は U2 のファイルに手を入れない | Step 1・17 |

### 2.2 この計画での読み方

- **層の順（test-after）**: Testing Contract の `ordering` のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む。U4 の層は「判定と読み直しの口（ApiClient）→ 骨組みの状態と表示（Provider・表示・見出し・文言）→ 振り分けと置き換え（decideRoute・AppRouter・ShellLayout・App・テストの補助）→ 既存の3つの画面 → 自分の氏名と言語の反映 → 実際のブラウザの検査 130 → 静的検査と構造」の順とする（依頼の順。共通の部品を先に作るため、各 Step の終わりで型の検査が通る）。
- **ApiClient は知らせない**: 「権限が無い」の判定は ApiClient の要求の流れに入れず、`isAdminForbidden` を別のファイルに置く。各画面が失敗を `useAdminForbidden` の関数に渡す（FS の G3、Q1 A）。ADR-005 と `unit-of-work.md` の「ApiClient が知らせる」は書き換えない（受け入れ済みの差）。
- **401 とログアウトは変えない**: `apiFetch` の 401 の更新と送り直し、`onUnauthenticated`、トークンを付けないパスの一覧は変えない。`refreshSessionOnce` は内部の `refreshOnce` を外へ出すだけ（FC 3.2、PD 3.1）。
- **画面の判定はサーバーの判定の代わりにしない**: S6 とメニューを隠すことは表示だけ。サーバーの 401・403・200 と監査（AC2.2.6）は U3 の持ち物で、U4 はバックエンドを変えない（NFR1.1、`project.md` の Mandated）。
- **既存の画面は 403 の扱いだけを変える**: 3つの画面のほかの振る舞い（文言・読み直し・フォーカス）は変えない（FS の W7）。招待の画面は U2 の import の切り替えに続けて、この Bolt の中で一度だけ直す。
- **既存のテストの書き換えの範囲**: FC 7節の「書き換え」の行と、振り分けのテスト2ファイル（G5）、描画の補助2つ（R-03）。ほかの既存のテストは変えずに通す。書き換えたテストの一覧を記録する（NFR9.11）。
- **130 の実行の置き場**: 130 は `./gradlew e2eTest` の中で、B2 の関門（Step 17）で1回だけ流す（依頼と U2 の計画の Step 15）。Step 13 では型・書式・リンタと `playwright test --list` で読めることを確かめる（8節の D-3）。
- **`monitoring-design.md` 4節の参照**: 「検査の結果」の行の `cicd-pipeline.md` 4.4 は、`cicd-pipeline.md` 5節（検査の結果のファイルと秘密）と読み替える（2回目の承認の場の申し送り）。130 の json の報告から写す項目は 5節の「Build and Test への写し」の行のとおり。
- **差分が無いことの基準のコミット**: 作業ブランチには U2 のバックエンドの変更があるため、U4 の変更による差分の確かめは `develop` ではなく U2 の最後のコミット `e20c3b7` と比べる（`git diff --stat e20c3b7 -- <パス>`）。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: 作業ブランチ `feature/260930-user-admin-b2` の上で続ける（U2 の C1 d1c751b・C2 e20c3b7 がコミット済み）。新しいブランチは作らない。worktree は使わない（`team.md` の Way of Working）。
- **U2 のファイルに手を入れない**: U4 の生成は U2 で作った・変えたファイル（`src/shared/paging/`・`common.paging`・招待のサーバーの2ファイル・`InvitationList.tsx`）に手を入れない。`useInvitationAdmin.ts` は U2 が import を変え、U4 が 403 の扱いを変える（同じファイルの別の行。U2 の最後のコミットの中身は変わらない）。U4 を始めた後に U2 の不具合が分かって U2 のソースを直すときは、単位ごとの squash の条件が崩れるため Bolt 全体の1コミットに切り替える（U2 の計画 3節）。
- **記録の扱い（9節の Q-B の決定 B）**: この段の記録（記録の `construction/u4-admin-forbidden-ui/code-generation/` の下・`aidlc-state.md`・監査ログ）と U2 の記録（`construction/u2-shared-paging/code-generation/` の下）は、作業ブランチの上で **記録だけのコミット**（`aidlc/` の下のファイルだけを含み、アプリのソースを含まない）にする。統合のときは、単位ごとの squash の範囲から `aidlc/` を外し、記録は `develop` の上で別の記録のコミットにする（手順は下の「統合の形」と Step 17）。記録だけのコミットは、依頼者の承認を得て次の時点で作る:

| 区切り | 中身 | 時点 |
|---|---|---|
| R1 | U2 のコード生成の記録（まとめ・生成の記録・変えたファイルの一覧・網羅の記録・計画のチェック）と、U4 の計画・単位のテストの手順・Plan Approval の記録（`aidlc-state.md`・監査ログを含む） | U4 の Plan Approval の後、Step 1 の前 |
| R2 | U4 の生成の記録（`code-summary.md`・`generation-notes.md`・`source-manifest.json`・`traceability.json`、計画のチェック） | Step 16（C3〜C6 の後） |
| R3 | B2 の関門の記録（Step 17 の実測・E2E の結果・報告を消したこと）と、その時点の `aidlc-state.md`・監査ログ | Step 17 の関門の後、統合の前 |

  - 記録だけのコミットに `aidlc/` の外のファイルが入っていないことを `git show --stat` で確かめる。アプリのソースのコミット（C3〜C6）には `aidlc/` の下を入れない。
  - 統合の後にも記録（統合のハッシュ・ブランチを消したこと）が増えるため、それは `develop` の上の記録のコミットに含めて提案する。
- **コミット**: 生成の担当はコミットしない。U4 の生成の後に、依頼者の承認を得て、作業ブランチの上で次の区切りでコミットする（`project.md` の Change Control）。メッセージは日本語で、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。

| 区切り | 中身 | 手順 |
|---|---|---|
| C3 | 判定と読み直しの口と骨組みの 403 の扱い（`adminForbidden.ts`・`apiClient.ts` とテスト、`src/app/admin-forbidden/`、文言、振り分け・`ShellLayout`・`App`・`renderWithProviders` とテスト） | Step 3〜8 |
| C4 | 既存の3つの画面の置き換え（管理の入口・DSL の管理・招待の管理とそのテスト、描画の補助2つ） | Step 9・10 |
| C5 | 自分の氏名と言語の反映の口（`displaySettingsStore.ts`・`DisplaySettingsProvider.tsx`・`useApplyOwnProfile.ts` とテスト） | Step 11・12 |
| C6 | 実際のブラウザの検査 130・共有の手伝い `loginPreferences.ts`・README | Step 13 |

- 件名の案は Step 16 で示す（例 C3「B2 U4: 管理の API の 403 を骨組みで扱う（isAdminForbidden・refreshSessionOnce・AdminForbiddenProvider・S6・振り分け）」）。途中のコミットで verify を流し直すことはしない。通ることを確かめるのは C3〜C6 をすべて含む作業ブランチ（Step 17）。
- **統合の前の関門（B2 全体で1回、Step 17）**: U2 の計画の Step 15 のとおり。colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し `caffeinate -i` で包んで `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を通し、`./gradlew osvScan --rerun-tasks` を通す。続けて E2E（`./gradlew e2eTest`、Mailpit を profile `mail` で起動、`caffeinate -i`）を流し、json から結果を記録してから `frontend/playwright-report/`・`frontend/test-results/` を消す。
- **統合の形（U2 の計画の Q-A A: 単位ごとの squash）**: 依頼者の承認を得て、`develop` の上で U2 の最後のコミット（e20c3b7）までを squash して1コミット（件名は U2 の `code-summary.md` 10節の案）、続けて作業ブランチの残り（U4 の C3〜C6）を squash して1コミットにする。U4 の件名の案は「B2 管理の画面の 403 の共通の扱い（U4）: 権限が無いときの表示 S6・ログインの状態の読み直し・自分の氏名と言語の反映の口」。サブモジュールの固定先は変えないため、fast-forward の例外には当たらない。単位ごとの squash の条件（U2 の最後のコミット e20c3b7 が単独で verify を通ること）は、U2 の記録に書き足した verify の結果で満たしている（9節の Q-C の決定）。単位ごとの squash の条件を満たさなくなったとき（U4 を始めた後に U2 のソースを直したとき）は、B2 全体を1コミットで squash する。
- **squash の範囲から `aidlc/` を外す手順（Q-B の決定 B）**: どれも依頼者の承認を得てから行う。
  1. 統合の前に、作業フォルダに未コミットのアプリのソースが無いこと、`aidlc/` の未コミットの変更は R3 に入れたことを `git status` で確かめる。
  2. `develop` に移り、`git merge --squash e20c3b7` で U2 を取り込む。C1・C2 は `aidlc/` を含まないが、`git diff --cached --stat -- aidlc/` が空であることを確かめてからコミットする（U2 の squash）。
  3. `git merge --squash feature/260930-user-admin-b2` で残りを取り込み、`git restore --staged --worktree --source=HEAD -- aidlc/` で `aidlc/` の下の変更（R1〜R3 の分）をこの squash から外す。`git diff --cached --stat -- aidlc/` が空で、`git diff --cached --stat` が U4 のアプリのソース・テスト・E2E・README だけであること（`source-manifest.json` と一致すること）を確かめてから、U4 の1コミットにする。
  4. `git checkout feature/260930-user-admin-b2 -- aidlc/spaces/default/intents/260930-user-admin` で作業ブランチの先頭の記録を `develop` の作業フォルダに戻し、統合の後の記録（統合のハッシュなど）を書き足して、`develop` の上の記録のコミット（件名の案「B2 のコード生成の記録（U2・U4 のまとめ・網羅の記録・変えたファイルの一覧・関門・統合 <U2 のハッシュ>・<U4 のハッシュ>）」）にする。`git diff --cached --stat` が `aidlc/` の下だけであることを確かめる。
  5. 作業ブランチの記録のコミットと `develop` の記録のコミットの記録の中身が同じであること（`git diff feature/260930-user-admin-b2 develop -- aidlc/` が統合の後に書き足した分だけであること）を確かめる。
- **統合の後**: 依頼者の承認を得て作業ブランチを消す。`origin` への `git push` は依頼者が行う。AI はプッシュしない。コミット・統合・ブランチの削除は、それぞれ依頼者の承認を得てから行う。

## 4. 作るもの・手を入れるもの・消すもの

### 4.1 本体（`frontend/src`）

| 置き場 | 部品 | 新しい・手を入れる | 中身 |
|---|---|---|---|
| `src/shared/api-client/adminForbidden.ts` | `isAdminForbidden`・`ADMIN_API_PREFIX`・`ACCESS_DENIED`・`FORBIDDEN_STATUS` | 新しい | `isAdminForbidden(path: string, error: unknown): boolean`。パスが `/api/admin/`（末尾の `/` まで含む）で始まり、`error` が `kind: 'response'`・`status: 403`・`code: 'ACCESS_DENIED'` の形のときだけ true。型を確かめてから比べ、知らない値で例外を出さない（D1、SD 2.2、FS の G4）。副作用なし。`src/app` を読まない |
| `src/shared/api-client/apiClient.ts` | `refreshSessionOnce` | 手を入れる | `export function refreshSessionOnce(): Promise<boolean>` を足し、中で今の `refreshOnce` を呼ぶ（登録が無ければ false、進行中の更新があれば同じ約束）。先頭の説明文に1行足す。`apiFetch`・`send`・`needsRefresh`・`TOKENLESS_API_PATHS`・`resetApiClient` は変えない（FC 3.2・6.1、PD 3.1） |
| `src/app/admin-forbidden/forbiddenHeading.ts` | `forbiddenHeadingKey` | 新しい | 全機能の `sidebarItems` から、`path` が今の URL に合う項目（`matchPath` の `end: true`、`visibleWhen` で絞らない）の `labelKey` を返す。無ければ `adminForbidden.heading`（D11、FC 3.5） |
| `src/app/admin-forbidden/AdminForbiddenProvider.tsx` | `AdminForbiddenProvider`・`useAdminForbidden`・`useIsAdminForbiddenHere` | 新しい | FC 3.3 の仕組み: 状態 `forbidden`（`useState`）と `seenPath`（描画の中で捨てる）、ref の `currentPathRef`・`forbiddenRef`（`useLayoutEffect` でパスごとに新しくする）・`inFlightRef`（読み直しの約束）、依存を持たない `report`、状態用と関数用の2つの context。`useAdminForbidden` は描画の時点の URL を持つ `(error: unknown, apiPath: string) => boolean` を `useCallback`（依存は `report` と画面の URL）で返す。`isAdminForbidden` が false なら何もせず false。Provider の外で `useAdminForbidden`・`useIsAdminForbiddenHere` を呼ぶと例外（8節の D-5）。`console` を呼ばない |
| `src/app/admin-forbidden/AdminForbiddenView.tsx`・`AdminForbiddenView.css` | `AdminForbiddenView` | 新しい | Props なし。`section`（`aria-labelledby`、`data-testid="admin-forbidden-view"`）、`h1`（`tabIndex={-1}`、`data-testid="admin-forbidden-heading"`、見出しは `forbiddenHeadingKey`）、make-you-chic-ui の `Alert`（`variant="info"`）の中に `adminForbidden.message` と react-router の `Link`（`to={HOME_PATH}`、`data-testid="admin-forbidden-home-link"`、文言 `adminForbidden.homeLink`）。部品が作られたときだけ（`useEffect`）見出しへフォーカスを移す（D12、FC 3.4）。CSS は部品と同じ場所の素の CSS（`Page.css` の `.page`・`.page-heading` の形にそろえ、リンクの色は既存の文字用の色のトークン。130 と vitest-axe のコントラストで確かめる） |
| `src/app/i18n/messages/ja.ts`・`en.ts` | 文言 | 手を入れる | `adminForbidden.message`（「この画面を使う権限がありません」／"You do not have permission to use this page."）・`adminForbidden.homeLink`（「ホームへ戻る」／"Back to home"）・`adminForbidden.heading`（「管理」／"Administration"）を足す（FS 7節） |
| `src/app/routing/decideRoute.ts` | `RouteDecision`・`decideRoute` | 手を入れる | `{ kind: 'ADMIN_FORBIDDEN'; route: RouteRegistration }` を足し、`ADMIN` の画面でログインしていて管理者でないときに返す（今の `NOT_FOUND` の代わり）。ログインしていないときと登録に無い URL は今のまま（D6、FC 4節） |
| `src/app/routing/AppRouter.tsx` | `AppRouter` | 手を入れる | `ADMIN_FORBIDDEN` のとき、SCREEN（SHELL）と同じ形の木（`Routes` → `Route path={route.path}` → `ShellLayout`）の中に `AdminForbiddenView` を描く（R-05、FC 1節・4節） |
| `src/app/layout/ShellLayout.tsx` | `ShellLayout` | 手を入れる | `useIsAdminForbiddenHere()` が true のとき、`children` の代わりに `AdminForbiddenView` を描く。サイドバー・上の帯・ユーザーメニューは今のまま（D3、FC 4節） |
| `src/app/App.tsx` | `App` | 手を入れる | `AdminForbiddenProvider` を `FeatureRegistryProvider` の内側・`AppRouter` の外側に置く。先頭の説明文の並びに足す |
| `src/app/display-settings/displaySettingsStore.ts` | `applyOwnProfileFor` | 手を入れる | `applyOwnProfileFor(binding, profile, current)` を足す。`savedUser` を `binding` に結び付けた「氏名＝渡した氏名・言語＝渡した言語（ja・en のときだけ）・テーマと文字の大きさ＝`current`」にする。ブラウザの保存は言語だけ（`writeStoredLanguage`）。言語の見せ方が残っていれば言語の分だけ捨て、テーマと文字の大きさの見せ方は残す。ja・en 以外は言語を変えず氏名だけ（D13・D14、FC 3.6、NFR8.2）。`applyUserPreferencesFor` などの今の口は変えない |
| `src/app/display-settings/DisplaySettingsProvider.tsx` | `DisplaySettingsValue.applyOwnProfile` | 手を入れる | `decideScreenSettings({ ...input, preview: null, prefersDark: false })` で当てている値を求め、ログイン状態とその値を ref（`useLayoutEffect`、依存なし）に入れる。`applyOwnProfile` は依存なしの `useCallback` で、呼ばれた時点の ref を読み、ログインしていなければ何もしない（SD 4.2、S-1、R-01）。値（`useMemo`）に足す。`applyUserPreferences`・`setPreview`・`setLanguage` は変えない（R5） |
| `src/app/display-settings/useApplyOwnProfile.ts` | `useApplyOwnProfile` | 新しい | `useDisplaySettings().applyOwnProfile` を返す口（C4 の名前と型 `(profile: { displayName: string; language: 'ja' \| 'en' }) => void`） |
| `src/features/admin/adminAreaStatus.ts` | `AdminAreaStatus`・`statusFromError` | 手を入れる | 状態の `NotFound` を `Forbidden` に替える。`statusFromError(error, report)` は `report(error, ADMIN_CHECK_PATH)` が true なら `Forbidden`、false なら `Error`（FC 6.2）。`FORBIDDEN_STATUS` を消す（判定は `isAdminForbidden` だけ、SD 2.2）。説明文を直す |
| `src/features/admin/AdminAreaPage.tsx` | `AdminAreaPage` | 手を入れる | `useAdminForbidden()` の関数を `statusFromError` に渡す。`Forbidden` のときは何も描かない（`null`。ShellLayout が S6 に置き換えている）。`NotFoundPage` の import をやめる。先頭の説明文を直す |
| `src/features/dsl/useDslAdmin.ts` | `checkForbidden`・状態 | 手を入れる | `checkForbidden` を「`report(error, DSL_API_ROOT)` を返す」形にし、定数 `FORBIDDEN` と状態 `forbidden`（と戻り値の `forbidden`）をなくす。4つの呼び出しの場所（読み込みの3つと `handleFailure`）は今のまま（FC 6.2） |
| `src/features/dsl/DslAdminPage.tsx` | `DslAdminPage` | 手を入れる | `state.forbidden` の「ページが見つかりません」の分かれ道と `NotFoundPage` の import をなくす。先頭の説明文を直す |
| `src/features/invitation/useInvitationAdmin.ts` | 4つの失敗の扱い | 手を入れる | 一覧の読み込み・招待・送り直し・取り消しの失敗の受け取り（今 `isUnauthorized` を確かめている4か所）で、`isUnauthorized(error)` と並べて `report(error, INVITATION_API_ROOT)` が true なら何もせず終える（FC 6.2）。先頭の説明文の「403 は一般の 4xx の文言で示す」を直す。`failureMessageKey` は変えない |

手を入れない: `backend/` のすべて、`vendor/make-you-chic-ui`、`frontend/package.json`・`frontend/package-lock.json`、`frontend/vitest.config.ts`・`playwright.config.ts`・`playwright-secret-check-reporter.ts`・`eslint.config.js`・`tsconfig.json`、`src/app/pages/NotFoundPage.tsx`（登録に無い URL のために残す）、`src/features/auth`、`src/shared/paging`、表示の設定の今の口（`applyUserPreferences`・`setPreview`・`setLanguage`）、`frontend/e2e/` の 010〜100 と `support/` の `pageProblems.ts`・`axe.ts`・`displayCombos.ts`・`adminLogin.ts`、`.github/`、`compose.yaml`・Dockerfile・`.env.example`、`docker/monitoring/`、`backend/src/main/resources/application.yaml`（CSP）。

### 4.2 テストとテストの補助

| 置き場 | 部品 | 新しい・書き換え | 中身 |
|---|---|---|---|
| `src/shared/api-client/adminForbidden.test.ts` | `isAdminForbidden` | 新しい | FC 7節の例と、fast-check の性質2つ（`unit-test-instructions.md` 3節） |
| `src/shared/api-client/apiClient.test.ts` | `refreshSessionOnce` | 足す | 4件。既存の 401 のテストは変えない |
| `src/app/admin-forbidden/forbiddenHeading.test.ts` | `forbiddenHeadingKey` | 新しい | 例と性質1つ |
| `src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` | Provider・口 | 新しい | FC 7節の項目、R-02 の3点、NFR9.1・NFR9.2・NFR3.1 |
| `src/app/admin-forbidden/AdminForbiddenView.test.tsx` | S6 | 新しい | ja・en、`role="status"`、フォーカス、リンク、vitest-axe 1件、`detail`・`console` |
| `src/app/i18n/messages.test.ts` | 文言 | 足す | 3つの鍵が ja・en にあること |
| `src/app/layout/ShellLayout.test.tsx` | 置き換えと AC2.2.2 | 足す | 5件 |
| `src/app/routing/decideRoute.test.ts` | 振り分け | 書き換え1件・足す | `shows not found to logged-in non-admins opening an ADMIN screen and shows it to admins` を `ADMIN_FORBIDDEN` に書き換える |
| `src/app/routing/AppRouter.test.tsx` | 振り分けの描画 | 書き換え1件・足す | `does not show ADMIN screens to logged-in non-admins` を S6 に書き換え、AC2.2.2（起動の時の更新）と R-05 のテストを足す |
| `src/app/testing/renderWithProviders.tsx` | テストの補助 | 手を入れる | `AdminForbiddenProvider` を `FeatureRegistryProvider` の内側に置く（`App` と同じ位置）。口は変えない |
| `src/app/display-settings/displaySettingsStore.test.ts`・`DisplaySettingsProvider.test.tsx` | 自分の氏名と言語 | 足す | FC 7節の項目と SD 4.3 の4項目 |
| `src/features/admin/AdminAreaPage.test.tsx` | 管理の入口 | 書き換え（描き方と2件） | テストの中の `render` を、`<ShellLayout><AdminAreaPage /></ShellLayout>` を管理者の提供元（`fakeProvider({ loggedIn: true, admin: true, … })`）で描く形に直す。403 の2件（`shows the not found screen when the check answers 403`・`calls the check again on every display …`）を S6 で確かめる形に書き換える |
| `src/features/admin/adminAreaStatus.test.ts` | `statusFromError` | 書き換え（4件） | `report` を渡す形にし、`ACCESS_DENIED` の 403 は `Forbidden`、ほかは `Error`。性質は「`ACCESS_DENIED` 以外の 403 も `Error`」 |
| `src/features/dsl/testing/renderDsl.tsx`・`src/features/invitation/testing/renderInvitation.tsx` | 描画の補助 | 手を入れる | 3つ目の引数に省略できる `options`（`withShell?: boolean`・`provider?: LoginStateProvider`・`registrations?`）を足す。既定は今のまま（ShellLayout なし、文言だけの登録）。`withShell` のときは `ShellLayout` の中に描き、見出しを確かめるときは本物の登録（`dsl/registration.ts`・`invitation/registration.ts`）を渡せる |
| `src/features/dsl/DslAdminPage.test.tsx` | DSL の管理 | 書き換え1件・足す2件 | 画面のテストのうち S6 を確かめるものだけ `withShell` で描く。`shows the not found screen when the server answers 403` を S6 に書き換え、確かめの表示を開いた状態の 403 と code の無い 403 を足す |
| `src/features/invitation/InvitationAdminPage.test.tsx` | 招待の管理 | 書き換え3か所・足す | `shows the failed list with the general message …`・`shows the general message inside the dialog for 403, …`・`does not reload after 403, 5xx or network failures …` の `apiError(403, 'ACCESS_DENIED')` を code の無い 403（一般の文言のまま）に替え、`ACCESS_DENIED` の 403 で S6 になる（入力の表示は閉じる）テストを一覧・入力の表示・送り直しで足す |
| `src/features/invitation/failureMessage.test.ts` | `failureMessageKey` | 書き換え1件 | 54 行目の `response(403, 'ACCESS_DENIED')` を code の無い・違う 403 に替える（`ACCESS_DENIED` はもう届かない、FC 6.2） |

### 4.3 E2E と文書

| 置き場 | 部品 | 新しい・手を入れる | 中身 |
|---|---|---|---|
| `frontend/e2e/support/loginPreferences.ts` | `LoginPreferences`・`routeLoginPreferences` | 手を入れる | 型に省略できる `displayName?: string` を足し、書き換えでは値が `undefined` の項目を重ねない（LC 6.2）。渡さない呼び出し（060）の動作は変えない。冒頭の説明に、130 が `displayName` を使うことを書き足す |
| `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` | 実際のブラウザの検査 130 | 新しい | 既存の 20 組ごとに1つのテスト。順は Step 13 の表（R-01 の順を含む） |
| `README.md` | 文書 | 手を入れる | E2E の表に 130 の行と「130 について」の節（流れに数えない、差し替えは `GET /api/admin/check` の1本だけ、時間は測らない、130 だけを流すときも Mailpit の起動が要る、2語の氏名は応答の書き換えで作りサーバーの状態を変えない）。DSL の管理の節の「管理者でない利用者が URL を直接開くと『ページが見つかりません』」を S6 の説明に直す。管理の画面の 403 の共通の扱い（U4）の短い節（S6 の表示、読み直し、401 は変わらない、判定はサーバー側）を足す（8節の D-6） |

### 4.4 差分が無いことを記録するもの

U2 の最後のコミット `e20c3b7` と比べて、次に U4 の差分が無いことを `git diff --stat e20c3b7 -- <パス>` で確かめて記録する（CP 9節、SD 9節の申し送り）: `backend`（`backend/src/main/resources/application.yaml` を含む）、`vendor/make-you-chic-ui`、`frontend/package.json`、`frontend/package-lock.json`、`frontend/vitest.config.ts`、`frontend/playwright.config.ts`、`frontend/e2e/support/pageProblems.ts`・`axe.ts`・`displayCombos.ts`・`adminLogin.ts`、`frontend/e2e/030-admin-access.e2e.ts`、`.github`、`docker`、`compose.yaml`、`gradle`、`build.gradle.kts`。

### 4.5 カバレッジと配信物の大きさの見込み

- 画面の全体の基準は U2 の後の実測 行 97.44%（2322/2383）・分岐 92.67%（1443/1557）、画面のテスト 91 ファイル・736 件（U2 の `code-summary.md` 3節）。U4 は新しいファイル6つ（`adminForbidden.ts`・`forbiddenHeading.ts`・`AdminForbiddenProvider.tsx`・`AdminForbiddenView.tsx`・`useApplyOwnProfile.ts` と CSS）と変える 13 ファイルに、テストのファイル4つを足し、既存のテストに足す。新しい部品は 3節のテストで行・分岐とも 90% 以上を見込むため、全体は下限（行 80%・分岐 70%）を大きく上回ったままの見込み。見込みは確かめの代わりにしない。Step 17 の `verify` で全体と U4 のファイルの値を実測して記録する。
- バックエンドは変えないため、`packagesJudgedByTotal`・パッケージごとの下限に U4 の作業は無い（NFR9.8）。
- 初回の JavaScript（gzip）は、Step 1 で U4 の前の値を `./gradlew frontendBundleSize` で測り、Step 17 の `verify` の段 9 の値と比べる。増えは数 KB までの見込み（PD 5節）。目安 500KB を超えても警告だけで、統合は止めない（NFR9.4）。

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。各 Step の実行のコマンドは `unit-test-instructions.md` の2節のとおりで、U4 に関わるテストのファイルかフォルダーを名指しして流す。

### Step 1: 作業の場の確かめと、変更の前の基準

- [x] 作業ブランチが `feature/260930-user-admin-b2` で、先頭が `e20c3b7`（U2 の C2）であることを `git rev-parse --abbrev-ref HEAD`・`git rev-parse HEAD` で記録する。アプリのソースに未コミットの変更が無いことを `git status` で確かめる（ワークフローの記録は外して判断する、`project.md` の学び）
- [x] U2 の単位ごとの squash の条件（U2 の最後のコミット `e20c3b7` の中身が単独で `./gradlew verify` を通ること）の確かめが、U2 の記録（`generation-notes.md` の「直しの後の verify」の節）に書き足されていることを確かめる。U2 の `code-summary.md` への書き足しは、9節の Q-C のとおり依頼者の決定に従う（9節の Q-C の決定 A。レビューの直しの後、C1・C2 のコミットの直前に `:backend:cleanTest :backend:cleanIntegrationTest verify` が BUILD SUCCESSFUL（6 分 41 秒、バックエンドの単体 1287・結合 587、失敗・エラー・飛ばした 0）。そのときの作業フォルダのアプリのソースは e20c3b7 と同じ）。書き足されていなければ、U4 の生成を始める前に止めて依頼者に諮る
- [x] 依頼者の承認を得て、記録だけのコミット R1（3節）を作業ブランチの上で作る。`git show --stat` で `aidlc/` の外のファイルが入っていないことを確かめ、ハッシュを記録する
- [x] `frontend/playwright-report/`・`frontend/test-results/` が手元に残っていないことを確かめる。残っていれば、中を開かずに消し、消したこと（ファイルとディレクトリの件数だけ）と共有していないことを記録する（`gate-decisions.md` の U5 R-02 の決定）
- [x] 変更の前の基準をとる: 画面のテストの件数とカバレッジは U2 の Step 13 の実測（91 ファイル・736 件、行 97.44%・分岐 92.67%）を基準とする（U2 の後に画面のソースは変わっていない。変わっていれば `(cd frontend && npm run test:coverage)` で測り直す）。初回の JavaScript の大きさを `./gradlew frontendBundleSize` で測り、入口のファイルごとの値と合計（gzip）を記録する（NFR9.4）
- [x] Dependabot の開いている知らせは B2 の始め（U2 の Step 1）で確かめ済みのため、ここでは確かめない。U2 の後に新しく開いた重大度 High 以上の知らせがあると分かったときは、依頼者に諮る（`team.md` の Way of Working）
- [x] 対応: B2 の共通の完了の条件、`gate-decisions.md`、NFR9.4、U2 の計画 3節

### Step 2: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` 2.1 のコマンドで、U4 が書き換える・足す先の既存の画面のテスト（Vitest・fast-check・vitest-axe）が作業ブランチの上で通ること、Playwright が E2E のファイルを読めること（`--list`）を確かめる
- [x] 新しいテスト（`adminForbidden.test.ts`・`src/app/admin-forbidden/*.test.*`・130）は、作るまで名指しすると「テストのファイルが無い」で失敗する。これは想定どおりで、作った Step からコマンドが通ることを `unit-test-instructions.md` の記述と合わせる
- [x] 対応: Testing Contract の `runner_step`、NFR9.11

### Step 3: 判定と読み直しの口（ApiClient）— 実装

- [x] `src/shared/api-client/adminForbidden.ts` を作る（4.1。SD 2.2 の形、定数 `ADMIN_API_PREFIX`・`ACCESS_DENIED`・`FORBIDDEN_STATUS`）。名前つきの export だけで、`enum` を使わない。`src/app`・`features` を読まない
- [x] `src/shared/api-client/apiClient.ts` に `refreshSessionOnce` を足す（今の `refreshOnce` を呼ぶだけ）。ほかは変えない
- [x] 新しいファイルの先頭に `/* ... */` の Apache License 2.0 のヘッダー（2026、agwlvssainokuni）を置き、日本語の説明文に D1・FC 3.1・3.2 の番号を書く
- [x] 対応: D1・D7・D10、C4（`isAdminForbidden`）、FS の G2・G4、NFR1.2・NFR1.4・NFR5.1・NFR9.2・NFR9.12

### Step 4: 判定と読み直しの口 — テスト

- [x] `src/shared/api-client/adminForbidden.test.ts` を作る: true になる1例（`/api/admin/check`・403・`ACCESS_DENIED`）と、false になる例（`/api/me/preferences` の 403、`/api/administrator` の 403、`/api/admin` の後に `/` が無いパス、code の無い 403、code `ORIGIN_NOT_ALLOWED` の 403、401 `AUTHENTICATION_REQUIRED`・404・409・500、通信の失敗、`null`・`undefined`・文字列・数・配列）。fast-check の性質2つ: (1) パス（`/api/admin/` の下・管理の外・似たパス）・状態（100〜599）・code（`ACCESS_DENIED`・ほかの文字・無し）・`kind` の組で、3つがそろうときだけ true (2) `fc.anything()` の値と任意の文字列のパスで例外を出さず真偽値を返す。先頭の説明文に、失敗のときの `seed` と `path` の再現の仕方を書く
- [x] `src/shared/api-client/apiClient.test.ts` に足す（4件）: 登録された更新を1回呼び結果を返す。401 の更新と同時に呼んでも更新は1回（同じ約束）。登録が無ければ false。`refreshSessionOnce` は `fetch` を呼ばない（新しい要求の経路を作らない、NFR5.1）。既存の 401 のテストは変えない
- [x] `unit-test-instructions.md` 2.2 のコマンドで流す
- [x] 対応: D1・D7・D10、NFR1.2・NFR1.4・NFR5.1・NFR9.2・NFR9.9、AC2.2.3

### Step 5: 骨組みの状態と表示（Provider・S6・見出し・文言）— 実装

- [x] `src/app/admin-forbidden/forbiddenHeading.ts` を作る（4.1、FC 3.5）
- [x] `src/app/admin-forbidden/AdminForbiddenProvider.tsx` を作る（4.1、FC 3.3 の表と説明用の断片のとおり）。要点: パスが変わったらその描画の中で `forbidden` を捨てる（`useEffect` で捨てない）。ShellLayout が読む値は「`forbidden` が今のパスと同じときだけ」と描画の中で求める。`report` は依存を持たない `useCallback`。画面の URL が今の URL と同じで `forbiddenRef` が既にその URL なら何もしない（重なった 403）、違えば覚えて読み直しを呼ぶ。画面の URL が今と違えば覚えずに読み直しを呼ぶ（W5）。読み直しは `inFlightRef` が空のときだけ `refreshSessionOnce()` を呼び、`finally` で空に戻す。結果は待たない（`await` しない、PD 2.1）。決まった間隔・時間切れ・時刻の判定を置かない
- [x] `src/app/admin-forbidden/AdminForbiddenView.tsx`・`AdminForbiddenView.css` を作る（4.1、FC 3.4）。`dangerouslySetInnerHTML` を使わず、URL を応答の値から組み立てない（NFR9.5）。失敗の値を受け取らない（NFR3.1）
- [x] `src/app/i18n/messages/ja.ts`・`en.ts` に `adminForbidden.*` の3つの鍵を足す（FS 7節）
- [x] この時点では振り分けと ShellLayout をまだ変えない（Step 7）。Provider と表示は単独で描いて確かめられる
- [x] 対応: D2〜D5・D7・D11・D12、FC 3.3〜3.5、NFR3.1・NFR7.1・NFR8.1・NFR9.1・NFR9.2・NFR9.5

### Step 6: 骨組みの状態と表示 — テスト

- [x] `src/app/admin-forbidden/forbiddenHeading.test.ts` を作る: 登録された項目の鍵（`admin.nav.label` など）を返す。`visibleWhen: 'ADMIN'` の項目も、管理者でない状態に関係なく探せる（関数はログイン状態を受けない）。URL の引数を持つ path（例 `/admin/users/:id`）に合う。無ければ `adminForbidden.heading`。性質（fast-check）: 登録に無い URL はいつも共通の鍵。種の再現の仕方を先頭に書く
- [x] `src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` を作る。`renderWithProviders`（Step 7 で Provider を足す前は、テストの中で `AdminForbiddenProvider` を置いて描く）と、`useAdminForbidden` の関数を取り出すテスト用の部品、ShellLayout の代わりに `useIsAdminForbiddenHere` を表示する部品、ブラウザの戻る（`useNavigate()(-1)`）を呼ぶ部品で確かめる。`unit-test-instructions.md` 3節の 12 件（R-02 の3点、NFR9.1 の保留の更新、NFR9.2 の回数、偽の時計、`console` の5つの関数、`detail` の目印）
- [x] `src/app/admin-forbidden/AdminForbiddenView.test.tsx` を作る: ja・en の文言、Alert が `role="status"`（`data-testid="alert"` の `role`）、見出しへのフォーカス（`waitFor`）、Tab で「ホームへ戻る」に届き Enter でホームへ移る（`LocationProbe` が `/`）、見出しが登録の項目の名前・無ければ「管理」、`console` が呼ばれない、vitest-axe の検査1件（違反 0、NFR7.2）
- [x] `src/app/i18n/messages.test.ts` に、3つの鍵が ja・en の両方にあることの確かめを1件足す（既存の鍵のそろいのテストも通す）
- [x] `unit-test-instructions.md` 2.3 のコマンドで流す
- [x] 対応: D2〜D5・D7・D11・D12、NFR3.1・NFR7.1・NFR7.2・NFR8.1・NFR9.1・NFR9.2・NFR9.9、AC2.2.1

### Step 7: 振り分けと置き換え（decideRoute・AppRouter・ShellLayout・App・テストの補助）— 実装

- [x] `src/app/routing/decideRoute.ts` に `ADMIN_FORBIDDEN` を足す（4.1）
- [x] `src/app/routing/AppRouter.tsx` に `ADMIN_FORBIDDEN` の描き分けを足す（SCREEN と同じ形の木、R-05）
- [x] `src/app/layout/ShellLayout.tsx` で、権限が無い URL のとき子の代わりに `AdminForbiddenView` を描く
- [x] `src/app/App.tsx` と `src/app/testing/renderWithProviders.tsx` に `AdminForbiddenProvider` を置く（`FeatureRegistryProvider` の内側・`AppRouter`／`ui` の外側）。Step 6 のテストの中の仮の置き方は、`renderWithProviders` を使う形に直す
- [x] 対応: D3・D6・D8、FS の G1・G5、FC 1節・4節、NFR1.3・NFR7.1・NFR9.1・NFR9.11

### Step 8: 振り分けと置き換え — テスト（振り分けのテスト2ファイルの書き換えを含む）

- [x] `src/app/routing/decideRoute.test.ts`: `shows not found to logged-in non-admins opening an ADMIN screen and shows it to admins` を、管理者でない利用者は `{ kind: 'ADMIN_FORBIDDEN', route: adminRoute }`、管理者は SCREEN に書き換える。ログインしていなければログインの画面へ、登録に無い URL は `NOT_FOUND` のままの確かめを足す
- [x] `src/app/routing/AppRouter.test.tsx`: `does not show ADMIN screens to logged-in non-admins` を、S6（`admin-forbidden-view`）が出て `admin-screen` が無く、`not-found-page` も無い形に書き換える。足す: 管理者でない利用者の管理の画面で偽物の `fetch` に `/api/admin/` の要求が届かない（NFR1.3）。起動の時の更新で印が外れた状態（管理者でないログイン状態）から描くと、管理のメニューが無く管理の画面が S6（AC2.2.2 の画面の読み直し、R-06）。ForbiddenByApi（管理者として描いた画面が `report` で S6）から ForbiddenByRoute（提供元が管理者でない状態を知らせる）へ移っても、S6 の見出しの要素が同じもの（`toBe` で同一）で、フォーカスが見出しに残り、サイドバーの開閉の状態が変わらない（R-05）
- [x] `src/app/layout/ShellLayout.test.tsx` に足す（5件）: 権限が無い URL のとき子の代わりに S6、サイドバーと上の帯は残る。AC2.2.2 のきっかけごとに、ログイン状態の提供元が管理者でない新しい状態を知らせると管理のメニューが消える（`waitFor`）: 403 の後の読み直し（偽物の更新）、トークンの更新の応答、ログインし直しの応答（R-06）。S6 の後もサイドバーで管理の外の画面へ移れ、S6 が消える（NFR1.4、AC2.2.3）
- [x] `unit-test-instructions.md` 2.4 のコマンドで、`src/app` のすべてのテストと `src/shared/api-client` を流す（`App.test.tsx` など、`App` と `renderWithProviders` の変更の影響を受けるテストが変えずに通ること）
- [x] 失敗したときは、書き換えの範囲（4.2）の外のテストを書き換えず、本体を直す。描画の後に反映される値の確かめが時間で揺れたときは、上限を延ばさずに原因を確かめる（`team.md` の Testing Posture）
- [x] 対応: D3・D5・D6・D8、R-05・R-06、NFR1.3・NFR1.4・NFR7.1・NFR9.1・NFR9.11、AC2.2.2・AC2.2.3・AC2.2.5

### Step 9: 既存の3つの画面の置き換え — 実装

- [x] 管理の入口: `src/features/admin/adminAreaStatus.ts`・`AdminAreaPage.tsx` を 4.1 のとおり直す（`statusFromError(error, report)`、`Forbidden` は何も描かない、`FORBIDDEN_STATUS` と `NotFoundPage` の import をなくす）
- [x] DSL の管理: `src/features/dsl/useDslAdmin.ts`・`DslAdminPage.tsx` を 4.1 のとおり直す（`checkForbidden` は `report(error, DSL_API_ROOT)` を返す、`FORBIDDEN`・`forbidden` と「ページが見つかりません」の分かれ道をなくす）。`checkForbidden` が true を返したときの今の扱い（Alert を出さず、読み直さない）はそのまま
- [x] 招待の管理: `src/features/invitation/useInvitationAdmin.ts` の4つの失敗の扱いに、`report(error, INVITATION_API_ROOT)` が true なら何もせず終える1行を、`isUnauthorized` の確かめと並べて足す（4.1）
- [x] 3つの画面とも、`useAdminForbidden` は `src/app/admin-forbidden/AdminForbiddenProvider` から読む（機能どうしは読み合わない、NFR9.12）。403 の扱いのほかの振る舞い（文言・読み直し・フォーカス）は変えない
- [x] `src/features` で `NotFoundPage`・`FORBIDDEN_STATUS`・`state.forbidden` を検索し、管理の入口と DSL の管理に参照が残っていないことを確かめる
- [x] 対応: D2・D3、W7、FC 6.2、FS の G9、NFR1.1、AC2.2.1・AC2.2.4

### Step 10: 既存の3つの画面 — テスト（書き換えと描画の補助）

- [x] `src/features/dsl/testing/renderDsl.tsx`・`src/features/invitation/testing/renderInvitation.tsx` に、省略できる `options`（`withShell`・`provider`・`registrations`）を足す（4.2。既定は今のまま）
- [x] `src/features/admin/AdminAreaPage.test.tsx`・`adminAreaStatus.test.ts`・`src/features/dsl/DslAdminPage.test.tsx`・`src/features/invitation/InvitationAdminPage.test.tsx`・`failureMessage.test.ts` を 4.2 の書き換えの範囲のとおり直す・足す。S6 の確かめは、S6（`admin-forbidden-view`）が出て、画面の一覧・操作・Alert・「ページが見つかりません」が無く、開いていた確かめ・入力の表示（Modal）が閉じていること（AC2.2.1・AC2.2.4）。管理の入口は ApiClient に登録した偽物の更新が1回呼ばれたこと（読み直し）も確かめる。偽物の API を使う DSL・招待の画面は更新を登録していないため、読み直しは false で何もしない（FS 8節）
- [x] `adminAreaStatus.test.ts` の性質ベースのテストを「`ACCESS_DENIED` 以外の 403 も `Error`」に直す（NFR9.9）
- [x] `unit-test-instructions.md` 2.5 の2つのコマンドで、`src/features/admin`・`dsl`・`invitation` と、`renderWithProviders` を使うほかの機能（`auth`・`preferences`・`registration`）を流し、書き換えの範囲の外のテストが変えずに通ることを確かめる（NFR9.11）
- [x] 書き換えたテストの一覧（ファイル・テストの題・中身）を `generation-notes.md` に書く
- [x] 対応: D2・D3、W7、R-03、FS の G9、NFR1.1・NFR9.9・NFR9.11、AC2.2.1・AC2.2.4

### Step 11: 自分の氏名と言語の反映（useApplyOwnProfile）— 実装

- [x] `src/app/display-settings/displaySettingsStore.ts` に `applyOwnProfileFor` を足す（4.1）
- [x] `src/app/display-settings/DisplaySettingsProvider.tsx` の値に `applyOwnProfile` を足す（SD 4.2 の ref の形。ref は `useLayoutEffect` で新しくし、描画の中で ref に書かない）
- [x] `src/app/display-settings/useApplyOwnProfile.ts` を作る（C4 の名前と型）
- [x] 今の `applyUserPreferences`・`setPreview`・`setLanguage` は変えない（R5、S-2）
- [x] 対応: D13・D14、W8、C4（`useApplyOwnProfile`）、R-01・S-1、NFR1.5・NFR3.1・NFR8.2、AC5.1.3（口の部分）、FR6.3

### Step 12: 自分の氏名と言語の反映 — テスト

- [x] `src/app/display-settings/displaySettingsStore.test.ts` に足す（`unit-test-instructions.md` 3節の5件）
- [x] `src/app/display-settings/DisplaySettingsProvider.test.tsx` に足す（8件）: 上の帯の氏名（ShellLayout で確かめるか、`useDisplaySettings().displayName`）と画面の言語・`<html lang>`・次の要求の `Accept-Language`（偽物の `fetch` で確かめる）が変わる。テーマと文字の大きさは、ほかのタブで変わった保存の値を含めて変わらない。テーマを `setPreview` で試しに当てた状態で呼ぶと、`savedUser` のテーマは見せ方の前の当てている値のままで、見せ方は残り、見せ方を捨てると当てている値に戻る（R-01）。ログインしていないときは何もしない。呼んだ後もログイン状態の `admin` が変わらない。描画の時点に取り出した関数を、ログイン状態の提供元が新しい状態を知らせて描画が確定した後に呼ぶと、新しいログイン状態に結び付いて当たる（`waitFor`、S-1）。ログイン状態・テーマ・言語が変わっても `useApplyOwnProfile` が返す関数が同じもの。反映の後に localStorage・sessionStorage のどの鍵の値にもテストの氏名が無い（NFR3.1）
- [x] R-03 の隙間（ログイン状態の知らせから描画の確定までの間に呼ばれる場合）のテストは足さない（NFR 設計の承認の場の決定）
- [x] `unit-test-instructions.md` 2.6 のコマンドで流す
- [x] 対応: D13・D14、R-01・S-1、NFR1.5・NFR3.1・NFR8.2、AC5.1.3（口の部分）

### Step 13: 実際のブラウザの検査 130 と共有の手伝い・README — 実装

- [x] `frontend/e2e/support/loginPreferences.ts` の型 `LoginPreferences` に省略できる `displayName?: string` を足し、書き換えでは値が `undefined` の項目を重ねない（LC 6.2 の形）。渡さない呼び出しは今と同じ動作（`displayName: undefined` を渡しても今の氏名を消さない）。冒頭の説明に 130 が `displayName` を使うことを書き足す。使うのは今 060 だけで、060 は変えない
- [x] `frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` を作る。`test.describe('130 admin forbidden view accessibility on the built WAR', …)` の中で `DISPLAY_COMBOS` の組ごとに1つのテスト。順は次のとおり（LC 6.1 の順を、NFR 設計の承認の場の R-01・R-02 の申し送りに合わせて並べ直した）:

| 順 | すること |
|---|---|
| 1 | `watchPage`（変えない）を張る。続けて 130 の中のコンソールの見張り（位置の URL のパスが `/api/admin/check` で、文が `Failed to load resource:` で始まる error を `excludedTexts` に集める、LC 6.3）と、`/api/admin/` への GET 以外の要求を数える見張り（`request.method()` と URL） |
| 2 | `prepareCombo` で組の幅・ブランドカラーを用意する |
| 3 | `routeLoginPreferences(page, { theme, fontSize, displayName: TWO_WORD_NAME })` で、ログインと復元の応答に組の値と2語の氏名を当てる（1つの差し替え）。`TWO_WORD_NAME` は 130 の中の架空の ASCII の2語の定数 |
| 4 | `GET /api/admin/check` だけを、403・`application/problem+json`・本文 `{ type, title, status: 403, detail: <目印の固定の文字>, code: 'ACCESS_DENIED' }` の見本に差し替え、返した回数を数える。GET 以外は `route.fallback()`。見本は 130 の中の定数（共有の `support/` に足さない） |
| 5 | `loginAsAdmin` でログインし、`expectComboApplied` で組が当たったことを確かめる |
| 6 | **読み直しの応答の約束を先に作る**（R-01）: `const reread = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/auth/session/refresh' && response.request().method() === 'POST' && response.status() === 200)` を、次の順7の操作より前に作る |
| 7 | サイドバーの「管理」（`openSidebarItem(page, '管理')`）から管理の入口を開く。S6（`admin-forbidden-view`）が見えるのを待つ |
| 8 | `await reread` で読み直しの応答を待ってから確かめる。E2E のサーバーでは初期管理者は管理者のままのため S6 のまま続く（残る危険 R1 の形）。読み直しの応答も順3 の差し替えを通るため、組の値と2語の氏名は保たれる |
| 9 | 組の確かめ（LC 6.4）: `runAxe`・`missingRequiredRules` が空・違反 0（既知の違反の一覧は持たない）、`measureHorizontalOverflow` ではみ出し無し、S6 の Alert（`admin-forbidden-view` の中の `data-testid="alert"`）が `role="status"`、見出し（`admin-forbidden-heading`）にフォーカス、上の帯の Avatar（`.mycui-avatar`）の文字の数が 2、画面の文字に `detail` の目印が無い、`login.rewritten` が 1 以上（ログインと読み直しで 2 の見込み。実測を注記に残す）。結果を組ごとの注記（`type: 'axe'`、組の名前・違反の件数・規則の名前・はみ出し・Avatar の文字の数・書き換えの回数・403 を返した回数・除いたコンソールの表示の件数）に残す。値（氏名・メールアドレス・トークン）を入れない |
| 10 | 「ホームへ戻る」（`admin-forbidden-home-link`）を押し、ホーム（`home-page`）が出て S6 が消えたことを確かめる（9節の Q-A の決定 A。全 20 組で行う） |
| 11 | `page.waitForLoadState('networkidle')` の後、問題の一覧を確かめる: `watch.problems` の写しから `excludedTexts` の各項目を1つずつ取り除いた残りが 0 件、除いた件数は 403 を返した回数以下、`watch.cspViolations` が 0 件、`/api/admin/` への GET 以外の要求が 0 件（全 20 組。S6 が消えた後のホームの画面の問題も含めて確かめる） |

- [x] 130 の先頭の説明文（日本語）に、組み立て・差し替えの範囲・ForbiddenByRoute を確かめない理由（部品のテストで確かめる）・時間を測らないこと・報告に値を入れないこと・コンソールの除外は 130 の中だけで `pageProblems.ts` を変えないことを書く。`test.step` の題は英語。画面の時間は測らず記録もしない（NFR9.3）
- [x] `README.md` を 4.3 のとおり直す（E2E の表の 130 の行、「130 について」の節、DSL の管理の節の1文、管理の画面の 403 の共通の扱い（U4）の節）
- [x] 対応: NFR7.3・NFR3.2・NFR9.5・NFR9.10、NFR 要件の承認の場の R-01・R-04、NFR 設計の承認の場の R-01・R-02、S-4〜S-7、CP 4節・5節

### Step 14: 画面の静的検査と構造の確かめ

- [x] `unit-test-instructions.md` 2.7 のコマンドで、型の検査（`npm run typecheck`、`e2e` を含む）、変えたフォルダーとファイルに絞った書式（Prettier）・リンタ（oxlint・ESLint、セキュリティ系の決まりを含む）・CSS のリンタ（Stylelint）・ライセンスヘッダーの検査を通す。除外を足さない（NFR9.5）
- [x] `npx playwright test --list` で 060 が 21 件、130 が 20 件と出ることを確かめる（130 の実行は Step 17）
- [x] 画面の側の境界（NFR9.12、LC 3.1）: U4 が足した・変えたファイルの import を一覧にし、`src/shared/api-client/` が `src/app/`・`features` を読まないこと、`src/app/admin-forbidden/`・`src/app/display-settings/` が `features` を読まないこと、`features/admin`・`dsl`・`invitation` が骨組みの口を `src/app/` から読み、ほかの機能を読まないことを、検索（`grep -rn "from '.*features/" frontend/src/app frontend/src/shared` が U4 の分で増えていないこと、`grep -rn "from '\.\./\.\./features\|from '\.\./\(admin\|dsl\|invitation\|auth\|preferences\|registration\)/" frontend/src/features/{admin,dsl,invitation}` が空のこと など）で確かめて記録する
- [x] 4.4 の差分が無いことを `git diff --stat e20c3b7 -- <パス>` で確かめて記録する（`backend`・`application.yaml`・`vendor/make-you-chic-ui`・`package.json`・`package-lock.json` ほか）
- [x] 対応: NFR1.1・NFR9.5・NFR9.6・NFR9.7・NFR9.12、CP 2節・9節

### Step 15: コードのレビューでの確かめ（申し送りと NFR の確かめ）

各項目の確かめた方法と結果を `generation-notes.md` に書き、`code-summary.md` に写す。

- [x] **判定の1か所（NFR1.2、SD 2.2）**: `src` で `403`・`ACCESS_DENIED`・`FORBIDDEN` を検索し、「権限が無い」を判定しているのが `isAdminForbidden` だけで、各画面・Provider・振り分けが状態コードと code を自分で見ていないことを確かめる（招待の `failureMessageKey` の一般の 4xx の扱いは判定ではないため対象外）
- [x] **待たない・重ねない（NFR9.1・NFR9.2、PD 2節・3節）**: `AdminForbiddenProvider.tsx` に `await`・`setInterval`・`setTimeout`・`Date` が無く、`report` の中で読み直しの約束を待っていないことを確かめる
- [x] **画面に出す値（NFR3.1、SD 3節）**: `AdminForbiddenView` が Props と失敗の値を持たず、U4 の部品と関数が `console` を呼ばず、`applyOwnProfileFor` がブラウザの保存に言語だけを書くことをソースで確かめる
- [x] **CSP と静的検査（NFR9.5）**: `dangerouslySetInnerHTML`・外部の資源・埋め込みのスクリプトとスタイルが無いこと、`application.yaml` に差分が無いこと（Step 14）を確かめる
- [x] **401 とログアウト（NFR1.4、D10）**: `apiClient.ts` の差分が `refreshSessionOnce` の追加と説明文だけであることを `git diff e20c3b7 -- frontend/src/shared/api-client/apiClient.ts` で確かめる
- [x] **130 の報告と資格情報（NFR3.2、SD 5節）**: 130 のソースで、アクセストークンを取り出していないこと（`requestAdminAccessToken` を使わない）、`webServer.env` を触らないこと、注記・添付・`test.step` の題・標準出力に氏名・メールアドレス・パスワード・トークンを入れていないことを確かめる。実行の後の json の報告の確かめは Step 17
- [x] **R4 の戻り方の読み方**: S6 にいる間は管理の API を呼ばず、ForbiddenByRoute には読み直しの契機が無いこと（SD 8節の R4）が実装のとおりであることを確かめ、記録する（実装は変えない）
- [x] 対応: NFR1.2・NFR1.4・NFR3.1・NFR3.2・NFR9.1・NFR9.2・NFR9.5、R4、SD 9節の申し送り

### Step 16: 記録とコミットの提案（U4）

- [x] `code-summary.md`（作ったもの・変えたもの、書き換えたテストの一覧、名指しのテストの件数、Step 14・15 の確かめの結果、計画・承認済みの文書との差、依頼者に確かめたいこと、承認の場で確かめること）、`source-manifest.json`（U4 で作った・変えたアプリのソースとテストと E2E と README のパスすべて。U2 のファイルと記録は入れない）、`traceability.json`（6節の対応。AC2.2.1〜AC2.2.5 と AC5.1.3（口の部分）を OK、AC2.2.6 と AC5.1.1・AC5.1.2・AC5.1.4〜AC5.1.8 を u3-user-admin-api・u5-user-admin-ui へ Deferred、D1〜D14 と NFR の ID の実装とテストのファイル。target に関わる FR・NFR の ID を添える、R-06）を作る（コード生成の段の手順）
- [ ] 3節の C3〜C6 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 承認を得てコミットした後、`git status` で U4 のアプリのソースに未コミットの変更が無いことを確かめ、U4 の最後のコミットのハッシュを `code-summary.md` に記録する。C3〜C6 のどれにも `aidlc/` の下のファイルが入っていないことを `git show --stat` で確かめる
- [ ] 依頼者の承認を得て、記録だけのコミット R2（3節。U4 の生成の記録）を作る。`aidlc/` の外のファイルが入っていないことを確かめる
- [ ] 対応: 段の記録、`project.md` の Change Control、`team.md` の Way of Working、9節の Q-B の決定

### Step 17: B2 の統合の前の関門と統合（U2 の計画の Step 15 と同じ1回）

この Step は U2 の計画の Step 15 を指し、B2 全体で1回だけ行う（二度は流さない）。U4 のコミット（C3〜C6）の後の作業ブランチで行う。

- [ ] colima が動いていることを確かめ、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を B2 全体で通す。対象DB のテストが SKIPPED になっていないことを確かめる。テストの件数（バックエンドの単体・結合、画面）とカバレッジ（バックエンドの全体と U2 の Step 13 と同じパッケージ、画面の全体と U4 で作った・変えたファイルの行・分岐。`frontend/coverage/coverage-summary.json` から）、初回の JavaScript の大きさ（Step 1 との比べ）を記録する。招待の既存のテストが変更なしで通ったこと（U2 のレビューの R-05）も記録する
- [ ] `caffeinate -i ./gradlew osvScan --rerun-tasks` を流し、通ること（走査したパッケージの数、失敗の条件に当たるもの 0 件、警告の件数）を記録する
- [ ] Mailpit が動いているかを確かめ（`docker compose ps`、`http://127.0.0.1:8025/api/v1/info` が 200）、動いていなければ `docker compose --profile mail up -d mailpit` で起動する。`.env` は開かない
- [ ] `caffeinate -i ./gradlew e2eTest` を実行する（010〜100 と 130。共有の手伝いに手が入ったため、既存の E2E が通ることの確かめを兼ねる、NFR9.10）
- [ ] `frontend/test-results/e2e-results.json` から次を `code-summary.md`（B2 の記録）に記録する。読むのは `stats` とテストごとの題・状態・注記だけで、ほかの中身は開かない:
  - 件数（`stats` の成功・失敗・飛ばした・不安定と時間）と、ファイルごとの件数と結果（期待 11 ファイル・130 件）
  - 060 の結果（20 組と実データの1件。飛ばされたときは注記 `skip-reason` と、画面のページ送りの確かめは Vitest だけが担ったこと、U2 の計画の Step 15）、090 の結果
  - 130 の組ごとの結果（違反の件数と規則の名前・はみ出しの有無・Avatar の文字の数・書き換えの回数・403 を返した回数・除いたコンソールの表示の件数）と、130 の全体の時間（R-02 の実行時間）
  - 報告の部品（`playwright-secret-check-reporter.ts`）が通ったこと。2語の氏名の値が json の報告に含まれないことを、値を表示しない形で件数を数えて（0 件）記録する
- [ ] 記録した後に `frontend/playwright-report/`・`frontend/test-results/` を消し、消したこと（ファイルとディレクトリの件数）と、報告を誰にも共有していないことを記録する（`gate-decisions.md` の U5 R-02 の決定）
- [ ] Mailpit は、この Step で起動したときは見終わったら止める。もとから動いていたときは止めない（B1 の経過）
- [ ] verify・`osvScan`・E2E のどれかが失敗したら、`team.md` の「不安定なテストと CI の失敗」の決まりで扱い、原因を直してから、この Step の verify からやり直す。U2 のソースを直したときは、単位ごとの squash の条件が崩れるため Bolt 全体の1コミットに切り替える（3節）
- [ ] 依頼者の承認を得て、関門の記録を記録だけのコミット R3（3節）にする。`aidlc/` の外のファイルが入っていないことを確かめる
- [ ] 依頼者の承認を得て、3節の形（単位ごとの squash、または条件を満たさないときは Bolt 全体の1コミット）で `develop` へ統合する。squash の範囲から `aidlc/` を外す手順（3節の 1〜5）に従い、U2・U4 の squash のコミットに `aidlc/` の下が入っていないことと、記録が `develop` の上の別の記録のコミットになったことを確かめて、ハッシュを記録する。統合の後、依頼者の承認を得て作業ブランチを消す（消す前に、記録のコミットの中身が `develop` に入ったことを 3節の 5 で確かめる）。プッシュは依頼者が行う
- [ ] 対応: B2 の共通の完了の条件、CP 2節・4節・5節・6節・9節、`gate-decisions.md`、U2 の計画の Step 15、`project.md` の Mandated（統合の前の確認）、NFR7.3・NFR9.4・NFR9.8・NFR9.10・NFR3.2

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US2.2 管理者でなくなったことを知る（主） | AC2.2.1（一覧と操作を出さない・権限が無い旨・外へ移る手段・確かめと入力の表示を閉じる）。FR2.3・FR8.2・NFR7・NFR8 | Step 5〜10・13 |
| US2.2 | AC2.2.2（ログインの状態の読み直しとメニュー、時間で判定しない）。FR2.2・FR2.3 | Step 3〜8 |
| US2.2 | AC2.2.3（管理の API の外は使え、ログアウトされない）。FR2.3・FR8.2 | Step 3・4・8 |
| US2.2 | AC2.2.4（入口・DSL・招待も同じ表示と読み直し）。FR2.3・FR8.2 | Step 9・10 |
| US2.2 | AC2.2.5（開き直したときも同じ表示、行を出さない）。FR8.1・FR8.2 | Step 7・8 |
| US2.2 | AC2.2.6（監査の「アクセスの拒否」） | Deferred（u3-user-admin-api。今のサーバーのまま、Step 14 で `backend/` の差分が無いことを記録） |
| US5.1 利用者の氏名・言語を直す（従） | AC5.1.3（自分の言語・氏名が直後から当たる）の口の部分。FR6.3・NFR8 | Step 11・12（呼ぶのは U5） |
| US5.1 | AC5.1.1・AC5.1.2・AC5.1.4〜AC5.1.8 | Deferred（u3-user-admin-api・u5-user-admin-ui、Step 16 の `traceability.json`） |
| 判定 | D1・D10 | Step 3・4・15 |
| 失敗を渡す口と置き換え | D2・D3 | Step 5・6・9・10 |
| URL との結び付きと捨て方 | D4・D5 | Step 5・6・8 |
| 管理者でない利用者 | D6 | Step 7・8 |
| 読み直し | D7・D8・D9 | Step 3〜8 |
| 見出しと S6 の表示 | D11・D12 | Step 5・6・13 |
| 自分の氏名と言語 | D13・D14 | Step 11・12 |
| 画面の判定とサーバー | NFR1.1・NFR1.3 | Step 7〜10・14 |
| 判定の条件 | NFR1.2 | Step 3・4・15 |
| 401 とログアウト | NFR1.4 | Step 3・4・8・15 |
| 印と自分の氏名・言語 | NFR1.5 | Step 11・12 |
| 画面に出す値 | NFR3.1 | Step 5・6・12・15 |
| E2E の報告と資格情報 | NFR3.2 | Step 13・15・17 |
| 読み直しの API の時間 | NFR5.1 | Step 4、Build and Test に引き継ぐこと |
| アクセシビリティ | NFR7.1・NFR7.2 | Step 5・6・8 |
| 実際のブラウザの検査 | NFR7.3 | Step 13・17 |
| 多言語 | NFR8.1・NFR8.2 | Step 5・6・11・12 |
| 画面の側の性能 | NFR9.1・NFR9.2（NFR9.3 は目標を置かない記録） | Step 5・6・15 |
| 配信物の大きさ | NFR9.4 | Step 1・17 |
| 静的検査と CSP | NFR9.5 | Step 13〜15 |
| 依存と make-you-chic-ui | NFR9.6・NFR9.7 | Step 14 |
| カバレッジ | NFR9.8 | Step 17 |
| 性質ベースのテスト | NFR9.9 | Step 4・6・10 |
| E2E の本数と既存の E2E | NFR9.10 | Step 13・17 |
| 画面のテストの決まりと書き換えの範囲 | NFR9.11 | Step 8・10 |
| 画面の側の境界 | NFR9.12 | Step 3・9・14 |
| 機能設計の上流との差 | G1・G5（振り分け）・G2（`refreshSessionOnce`）・G3（ApiClient は知らせない）・G4（`unknown` で受ける）・G6（en の文言）・G7（部品の名前）・G8（文言は原因を書かない）・G9（管理の入口の code） | Step 3・5・7〜10 |
| NFR 設計の上流との差 | S-1（ref の形）・S-2（今の口は直さない）・S-4（`detail` の目印）・S-5（`displayName`）・S-6（コンソールの除外）・S-7（060 だけが使う） | Step 11〜13 |
| 申し送り | NFR 設計の承認の場の R-01（約束の順）・R-02（「ホームへ戻る」の組と実行時間）、基盤の設計の README・報告の片付け・`monitoring-design.md` 4節の参照 | Step 13・17、2.2、Build and Test に引き継ぐこと |
| B2 の完了の条件（U4 の分） | 403 の共通の扱い、3つの画面の置き換えとテストの直し、`useApplyOwnProfile`、画面部品ごとのアクセシビリティの検査、招待の画面は一度だけ直す | Step 3〜17 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、主な境界のテストを置く（Testing Contract の `strategy_volume`）。件数の見込みと中身は `unit-test-instructions.md` 3節の表のとおり。

| 部品 | 単体（`*.test.ts(x)`） | 主な境界のテスト |
|---|---|---|
| 判定と読み直しの口 | `adminForbidden.test.ts` 8・`apiClient.test.ts` に4 | ApiClient に登録した偽物の更新を通す（401 の更新との重なり） |
| 骨組みの状態と表示 | `forbiddenHeading.test.ts` 5・`AdminForbiddenProvider.test.tsx` 12・`AdminForbiddenView.test.tsx` 7・`messages.test.ts` に1 | — |
| 振り分けと置き換え | `decideRoute.test.ts` 3（書き換え1）・`AppRouter.test.tsx` 4（書き換え1）・`ShellLayout.test.tsx` に5 | 骨組みの Provider の中で振り分け・ShellLayout・S6 を通して描く（ForbiddenByApi → ForbiddenByRoute） |
| 既存の3つの画面 | 書き換え（管理の入口2件・`adminAreaStatus` 4件・DSL 1件・招待3か所・`failureMessage` 1件）と足す（DSL 2・招待 3） | 画面を ShellLayout の中に描き、403 から S6 までを通す（管理の入口は ApiClient を通す） |
| 自分の氏名と言語 | `displaySettingsStore.test.ts` に5・`DisplaySettingsProvider.test.tsx` に8 | Provider・ShellLayout・`Accept-Language`（偽物の `fetch`）を通す |
| 実際のブラウザ | — | 130（20 組）。既存の 010〜100（110 件）を変えずに流す |

- Provider と表示の設定の Provider が 5〜8 件を超えるのは、機能設計の承認の場の R-01・R-02 の確かめと、NFR9.1・NFR9.2・NFR3.1・S-1 の確かめを合わせたため。
- Standard の「主な境界の結合テスト」は、画面の単位のため、骨組みの Provider の中に ShellLayout と振り分けを通して描く部品のテストと、実際のブラウザの検査 130 で満たす（バックエンドの結合テストは足さない。サーバー側の 401・403・200 と監査は U3 の持ち物）。
- 性質ベースのテストは fast-check の既定の 100 回で、`isAdminForbidden`（2）・`forbiddenHeadingKey`（1）・`adminAreaStatus`（書き換え）に当てる（NFR9.9）。失敗のときの種の再現の仕方は各テストの先頭に書く（`unit-test-instructions.md` 5節）。
- どのテストも成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。
- `team.md` の認証・認可・監査の必須テストのうち U4 に当たるのは「画面で管理メニューを隠すことはサーバー側の検査の代わりにしない」と「管理者の印の変更」の画面の側（古い印に頼らない表示）で、サーバー側の 401・403・200 と印の切り替えは U3 のサーバー側のテストが受け持つ（NFR1.1、残る危険 R4）。U4 は失敗の場合（403・code の無い 403・401・通信の失敗・読み直しの失敗）のテストを含める（`project.md` の Mandated）。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| ID | 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|---|
| D-1 | 層の並び（FC 2節の一覧、LC 1節） | 置き場ごとの一覧で、順は決めていない | 依頼の順（ApiClient → 骨組みの状態と表示 → 振り分けと置き換え → 3つの画面 → 自分の氏名と言語 → 130 → 静的検査）に、骨組みを「状態と表示」と「振り分けと置き換え」の2つの層に分けて並べた | test-after の層の順のまま、各 Step の終わりで型の検査と名指しのテストが通るようにするため |
| D-2 | `FORBIDDEN_STATUS` の置き場（FC 2節、FC 6.2） | 新しい `adminForbidden.ts` に `FORBIDDEN_STATUS` を置く。`adminAreaStatus.ts` の同名の定数の扱いは書いていない | `adminAreaStatus.ts` の `FORBIDDEN_STATUS` を消し、判定は `isAdminForbidden` だけにする | 判定を1か所にする決まり（D1、SD 2.2）。同名の定数が2つ残ると、画面で状態コードを見る形が残るため |
| D-3 | 130 の実行の時点（CP 4.2、LC 6.2 の「コード生成で 010〜100 と 130 が通ることを確かめて記録」） | コード生成（B2）で `./gradlew e2eTest` を流して記録する | Step 13 では型・書式・リンタと `--list` だけを確かめ、130 の実行は B2 の関門（Step 17）の `./gradlew e2eTest` の1回で行う。失敗したら直して Step 17 の verify からやり直す | 依頼（B2 の関門は U4 の計画の最後の Step から1回だけ）と U2 の計画の Step 15（E2E を B2 で1回に保つ）に合わせるため。130 は WAR に U4 のすべての変更が要り、層の途中で流しても同じ WAR をもう一度作ることになる。test-after の「実装の後にその層のテストを流す」は、統合の前の Step 17 で満たす |
| D-4 | 130 の2語の氏名の作り方（TS の NFR7.3、SD 9節の S-5） | 共有の手伝いに省略できる引数を足す案に決めた（NFR 設計） | そのとおり `loginPreferences.ts` の型に `displayName?` を足す。値が `undefined` の項目は重ねない | 承認済みの形のとおり。差ではなく、具体の書き方の決定 |
| D-5 | Provider の外での `useIsAdminForbiddenHere`（FS 6節、FC 3.3） | `useAdminForbidden` を Provider の外で呼ぶと例外とだけ書く | `useIsAdminForbiddenHere` も Provider の外で呼ぶと例外にする | 置き忘れにテストで気づくため（FS 6節と同じ考え方）。`App` と `renderWithProviders` が必ず置くため、利用者の画面には起きない。ShellLayout を Provider の外で描く既存のテストは無い（`renderWithProviders` と `App` だけ） |
| D-6 | README（CP 4.3） | E2E の表に 130 の行と「130 について」の節を足す | 加えて、DSL の管理の節の「管理者でない利用者が URL を直接開くと『ページが見つかりません』になります」を S6 の説明に直し、管理の画面の 403 の共通の扱い（U4）の短い節を足す | U4 の変更（D6・G1）で README の記述が事実と食い違うため（`project.md` の学び「手順を決定に合わせて直す」）。設計の文書は変えない |
| D-7 | 130 の見本の置き場（NFR 要件の申し送り「E2E の見本の置き場」） | 決めていない | `GET /api/admin/check` の 403 の見本は 130 の中の定数に置き、共有の `support/` に足さない | 使うのは 130 だけで、共有の手伝いを増やさない（CP 4.1 の「共有の手伝いは `loginPreferences.ts` だけに手が入る」） |
| D-8 | 130 の順（LC 6.1） | 順6「サイドバーの『管理』から開く」、順7「読み直しの応答を待ってから確かめる」 | 読み直しの応答の約束（`waitForResponse`）を作る手順を、管理の入口を開く操作の前に別の順として置いた（Step 13 の表の順6・7）。表の文書は書き換えない | NFR 設計の承認の場の R-01 の申し送り（後に作ると応答が先に終わり、時間切れの不安定なテストになりうるため） |
| D-9 | 130 の「ホームへ戻る」と問題の一覧の確かめの対象（LC 6.1 の順9・順10） | 既定の組だけで足りるが全組で行ってよい、コード生成の計画で決める | 全 20 組で「ホームへ戻る」を押し、S6 が消えた後の問題の一覧も全 20 組で確かめる（9節の Q-A の決定 A） | 1組あたりの増えはクリックと画面の移りの数秒で、20 組の 130 の全体は 1〜2 分の見込み。組ごとに同じ手順にしておくと、S6 から移った後の CSP の違反・問題の見落としが無くなる |
| D-10 | 差分が無いことの確かめの基準（CP 9節、SD 9節の申し送り） | 「差分が無いこと」を記録するとだけ書く | U2 の最後のコミット `e20c3b7` と比べる | 作業ブランチには U2 のバックエンドの変更があり、`develop` と比べると U2 の差分が出るため |

ほかに、上流（要件・機能設計・契約 C4・NFR 要件・NFR 設計・基盤の設計）と違う作りはない。

## 9. 依頼者の決定

計画の承認の前に諮った3つの論点について、依頼者が次のとおり決めた。計画の各 Step と 3節・8節はこの決定に合わせてある。

**Q-A 130 の「ホームへ戻る」と問題の一覧の確かめの対象の組**（NFR 設計の承認の場の R-02、8節の D-9）

- 依頼者の答え: **A（全 20 組）**
- 決定: 全 20 組で「ホームへ戻る」を押してホームへ移り S6 が消えたことを確かめ、その後の問題の一覧（CSP の違反・画面の問題・管理の API への GET 以外の要求）も全 20 組で確かめる（Step 13 の順10・順11）。130 の全体の時間は 1〜2 分の見込みで、Step 17 で実測して記録する
- 選ばなかった案: B（「ホームへ戻る」は既定の組だけで確かめ、ほかの組は S6 のまま問題の一覧を確かめる）
- 理由: 1組あたりの増えは数秒で、組ごとに同じ手順にすると S6 から移った後の CSP の違反・問題の見落としが無くなる

**Q-B この段と U2 の記録（`aidlc/` の下）のコミットの置き場**（3節）

- 依頼者の答え: **B（作業ブランチの上で記録だけのコミットを作り、統合のときに squash の範囲から外して、`develop` で記録のコミットにする）**
- 決定: 作業ブランチの上で記録だけのコミット R1（U2 の記録と U4 の計画・承認、Step 1）・R2（U4 の生成の記録、Step 16）・R3（関門の記録、Step 17）を作る。アプリのソースのコミット（C3〜C6）には `aidlc/` を入れない。統合では、U2・U4 の単位ごとの squash の範囲から `aidlc/` を外し（3節の手順 1〜5）、記録は `develop` の上の別の記録のコミットにする。どのコミットも依頼者の承認を得てから行う
- 選ばなかった案: A（作業ブランチではコミットせず、統合の後に `develop` の上で記録のコミットとして提案する）
- 理由: 記録が未コミットのまま長く残らず、作業ブランチの上でも記録の経過を追える。squash に記録が混ざらないことは、手順 3 の `git diff --cached --stat -- aidlc/` が空であることで確かめる

**Q-C U2 の単位ごとの squash の条件の確かめ**（Step 1）

- 依頼者の答え: **A（U2 の記録に書き足す）**
- 事実: U2 のレビューの直し（R-01・R-02）の後、C1・C2 のコミットの直前に、colima の設定と `caffeinate -i` を付けて `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流し、BUILD SUCCESSFUL（6 分 41 秒）、バックエンドの単体 1287・結合 587、失敗・エラー・飛ばした 0 だった。そのときの作業フォルダのアプリのソースは、コミットした e20c3b7 と同じ
- 決定: この結果を、U2 の最後のコミット e20c3b7 が単独で `verify` を通ることの記録として U2 の `generation-notes.md`（「直しの後の verify」の節）に書き足した。単位ごとの squash の条件は満たしている。U2 の `code-summary.md`（3節・8節）は、レビューが確定した成果物のため書き換えが止められており（review-freeze）、まだ書き足していない。書き足すかどうか（Request Changes と再レビューを経るか、R3 の記録のコミットや U4 の `code-summary.md` で触れるだけにするか）は依頼者が決める。Step 1 で `generation-notes.md` に書き足されていることを確かめる
- 選ばなかった案: B（B2 の関門の verify で代え、単位ごとの squash の条件を満たしたとみなす）

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

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は U4 では新しい骨組みが無いため Step 1（作業の場と基準）、テストの実行の準備は Step 2（最初のテストの Step 4 より前）。データの形と DB の振る舞い・DB アクセス・業務処理・API の層は U4 に無い（画面の単位で、保存するデータ・サーバーの変更を持たない。サーバーの 401・403・200 と監査は U3）。画面（Frontend behavior）の層は、判定と読み直しの口（Step 3・4）、骨組みの状態と表示（Step 5・6）、振り分けと置き換え（Step 7・8）、既存の3つの画面（Step 9・10）、自分の氏名と言語の反映（Step 11・12）、実際のブラウザの検査（Step 13 で作り、Step 17 で流す）に分けた。環境とビルドの設定は変えず、静的検査と構造と関門の確かめを Step 14・15・17、文書と記録を Step 13（README）・15・16 で行う。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジと件数の実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、画面の全体と U4 で作った・変えたファイルの行・分岐、バックエンドの全体をもう一度実測して記録する（NFR9.8） | Build and Test |
| CI | 依頼者のプッシュの後、CI（`./gradlew verify`）が通ることを確かめる。失敗したら `team.md` の「不安定なテストと CI の失敗」の決まりで扱う | Build and Test（CI の結果の確かめ） |
| E2E と 130 | Build and Test でも `./gradlew e2eTest`（010〜100 と 130）を流し、130 の組ごとの結果（違反の件数と規則の名前・はみ出し・Avatar の文字の数・除いたコンソールの表示の件数）と、報告の部品が通ったこと、2語の氏名の値が json の報告に含まれないこと（件数だけ）を記録する（NFR7.3・NFR3.2、`cicd-pipeline.md` 5節の「Build and Test への写し」。`monitoring-design.md` 4節の `cicd-pipeline.md` 4.4 は 5節と読み替える） | Build and Test |
| E2E の報告の片付け（2回目の承認の場の U4 の申し送り） | 持ち主は Build and Test の段（その段で E2E を流す担当）。順は、(1) `./gradlew e2eTest` を流す (2) `frontend/test-results/e2e-results.json` の `stats` とテストごとの題・状態・注記だけを読み、`test-results.md` に記録する (3) 2語の氏名の値が json に含まれないことを件数で記録する (4) `frontend/playwright-report/`・`frontend/test-results/` を消す (5) 消したこと（件数）と共有していないことを記録する。B5 で U5 が `playwright.config.ts` を直す（html を外し trace の既定を `off`）までは、この順で扱う | Build and Test |
| 読み直しの API の時間 | トークンの更新の API の既存の目標（同時 10 件で p95 1 秒）。U4 のために測り直さない（NFR5.1）。運用の中の判定は手元の監視を常に動かしていない間は `Unverified` | performance-validation（U1 の NFR5 の持ち物） |
| 初回の JavaScript の大きさ | Step 1 と Step 17 の値（gzip）を写す。目安を超えても警告だけ（NFR9.4） | Build and Test |
| スモークテストの S6 | 配備の後のスモークテストに S6 の確かめを入れるか（管理者でない利用者が要り、監査に残る）を決める（基盤の設計の申し送り） | deployment-pipeline |
| 既存の表示の設定の口の同じ形の危険（R5） | `applyUserPreferences`・`setPreview`・`setLanguage` を、呼ばれた時点の最新のログイン状態で動かす形に直すか（NFR 設計の申し送り） | 後の Intent |
| U5 が使う口 | U5 の利用者の管理の画面が、すべての失敗の扱いの入口で `useAdminForbidden` に `/api/admin/users` を渡すこと、自分の行の氏名・言語の保存の成功で `useApplyOwnProfile` を呼ぶこと、画面の見出しとサイドバーの項目の名前を「利用者の管理」でそろえること（FC 3.7） | B5（U5 のコード生成の計画） |
| 8KB を超える要求の HTML の 400（U2 の申し送り） | 画面の ApiClient が Tomcat の `text/html` の 400 を受けたときの扱い。U4 の `isAdminForbidden` は `kind: 'response'`・403・`ACCESS_DENIED` のときだけ true のため、HTML の 400 は各画面の今の扱いのまま | B5（U5） |
| サーバー側の 403 と監査（AC2.2.6） | 管理の API の 403 と監査の「アクセスの拒否」は今のサーバーの動作。サーバー側のテストは U3 | B3・B4（U3 のコード生成の計画） |
