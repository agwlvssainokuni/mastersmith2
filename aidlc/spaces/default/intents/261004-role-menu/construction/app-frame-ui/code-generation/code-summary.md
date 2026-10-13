# コードの要約（Code Summary）— U7 骨組みの画面（app-frame-ui）、Bolt B9

計画 `code-generation-plan.md`（14節の Q1〜Q9 はすべて A）に沿って、Step 1〜33 を行った。依頼者の決定は G1: A・G2: A・G3: A（4節）。

- 固定先の更新: 専用のブランチ `feature/261004-role-menu-b9-mycui` の専用のコミット e911e59（P1）で行い、`develop` へ fast-forward で統合済み（記録のコミット f6bd7df を含む）。
- B9 の本体: 作業ブランチは `feature/261004-role-menu-b9`（`develop` の f6bd7df から）。
- コミット: 依頼者の承認を得て、計画の区切り（4.2 の C1〜C8）ではなく場所ごとの3つにした（e1ef744 骨組み・作業ロール・業務のメニュー・ログアウトの道と既存の機能の登録の書き換え、f8f0bfa テーブルの準備中の画面と自分の権限の画面、f106293 E2E と README）。骨組みの差し替えが既存の 10 の機能の登録とテストに及び、手順ごとに分けると途中でビルドが通らない区切りが出るため。e1ef744 の時点で型の検査が通ることを確かめた。`develop` へは squash で1コミットになる。

## 1. 作ったもの・変えたもの

| 区分 | 道（`frontend/` の下。ほかはリポジトリのルートから） | 中身 |
|---|---|---|
| 固定先の更新（P1） | `vendor/make-you-chic-ui`（gitlink） | e82b651c53ac6e048066fcc049e49386c0eb282e → 5bf1ffe3cb7ee5c49963c3d7e8d4e8d0f032aae6（入れ子のサイドバー `navSections`）。中身は変えていない |
| 骨組みの口（C1） | `src/app/registry/types.ts`・`validateRegistrations.ts`、`src/app/login-state/LoginStateGate.tsx`、`src/app/routing/AppRouter.tsx` | `path` の項目の任意の `beforeNavigate`、提供元の任意の `clearLogoutIntent` と `useClearLogoutIntent()`、`/logout` 以外の道で印を消す効果（L15） |
| 骨組みの新しい部品（C2） | `src/app/work-role/`（新）、`src/app/navigation/` の `tablePlaceholderPath`・`navExpansion`・`buildNavSections`・`navigationApi`・`useReloadTriggers`・`BusinessNavigationProvider`（新）、`src/app/testing/frameTestSupport.tsx`（新、テストの補助） | 作業ロールの API・状態・トップバーの切り替え（L1・L3）、業務のメニューの API・状態・読み直しのきっかけ（L2・L4）、区画を作る純粋な関数（L5）、置き場の道（L6）、開閉の保存（L7） |
| 骨組みの差し替え（C3） | `src/app/App.tsx`・`layout/ShellLayout.tsx`・`layout/ShellLayout.css`（新）・`layout/useNarrowLayout.ts`（新）・`pages/HomePage.tsx`・`pages/Page.css`・`i18n/messages/ja.ts`・`en.ts`・`navigation/navigationItems.ts`、既存のテストの書き換え（6.1 の (a)〜(e)・(g)） | 提供元の並び（D2）、`navSections` のサイドバーと開閉の状態（D10〜D17）、作業ロールの置き場（768px 以上はトップバー、未満は内容の領域の先頭。G2: A）、ホームの案内（D19）、`selectAdminSidebarItems`（14節 Q5: A） |
| ログアウトの道（C4） | `src/features/auth/logoutIntent.ts`・`LogoutPage.tsx`（新）・`registration.ts`・`loginStateProvider.ts` | ログアウトを道 `/logout` の移動にし、メモリ上の一度きりの印で守る（D27・NFR1.8） |
| tables（C5、新） | `src/features/tables/` | S2 テーブルの置き場（準備中・権限なし・失敗、D20〜D23） |
| mypermissions（C6、新、Should） | `src/features/mypermissions/` | S8 自分の権限（D24〜D26。`displayName` は文字列、言語を替えたら読み直す） |
| E2E（C7） | `e2e/support/userMenu.ts`・`frameApiRoute.ts`・`navTreeFixtures.ts`・`u7Measure.ts`（新）、`e2e/support/registeredUser.ts`・`e2e/080-preferences-accessibility.e2e.ts`（開き口）、`e2e/160-app-frame-accessibility.e2e.ts`・`e2e/170-menu-navigation-flow.e2e.ts`（新）、`e2e/150-role-permission-flow.e2e.ts`（6a〜6e） | 160 は 20 組 × 10 の状態・幅4件・英語・画面の時間（本数に数えない）。170 は I の流れ（この Intent の2本までの1本）。150 は作業ロールの切り替え（同じ1本の中） |
| 文書（C8） | `README.md`（ルート）、`src/features/README.md` | E2E の表の 135・160・170 と 150 の追記、「160・170 について」、骨組みの画面の節、`beforeNavigate`・骨組みのフック・置き場の道 |

`backend/`・移行・Gradle・Vitest と Playwright の設定・`vite.config.ts`・`main.tsx`・`src/shared/`・`renderWithProviders.tsx`・`package.json`・`package-lock.json` は変えていない（Step 31。lockfile の SHA-256 は Step 3 と同じ）。

## 2. 実装で決めたこと

- フック `useWorkRole()`・`useBusinessNavigation()`・`useClearLogoutIntent()` は、提供元が無いときに要求を送らない既定の値（idle）を返す（D-8）。既存の 68 の画面のテストは書き換えずに通った。
- 作業ロールの切り替えの結果に `'ignored'`（送信の最中の2回目）を足した。`ROLE_BUSY` は D8 と同じ扱い（D-4）。
- 置き場（S2）と自分の権限（S8）は、作業ロールの初めての読み込みが終わるまで問い合わせを待つ（初めの描画で2回問い合わせていたのを直した）。
- 骨組みは機能を import しないため、S8 の入口（作業ロールの一覧の最後・ホームの案内）は登録に道 `/me/permissions` があるときだけ出す（D-6）。
- 768px 未満の判定は `useNarrowLayout`（`matchMedia('(width < 768px)')` の購読）で行い、作業ロールの切り替えの部品はどちらの幅でも1つだけ描く。

## 3. テストの量とカバレッジ

- 画面のテスト（Step 33 の `verify`、実測）: 183 ファイル・1,558 件すべて通過。行 95.27%・分岐 90.19%（Step 3 は 165 ファイル・1,409 件、行 94.88%・分岐 89.54%）。バックエンドは変えていない（単体 2,082 件・結合 1,165 件、Step 5 と同じ）。
- 新しい・変えたフォルダー（Step 24、行・分岐）: `app/work-role` 99.38%・95.32%、`app/navigation` 99.12%・97.76%、`features/tables` 97.67%・94.64%、`features/mypermissions` 95.59%・88.73%、`features/auth` 99.19%・100%。目安（行 90%・分岐 80%）をすべて満たす。除外は足していない。
- G2・G3 の後に足した画面のテスト: `ShellLayout.test.tsx` の4件（広い幅の置き場・狭い幅の置き場・幅の変化・狭い幅の axe）。
- E2E: 160（26 件）・170（1 件）・150（1 件、6a〜6e を足した）。
- 性質ベースのテスト（`buildNavSections.property.test.ts` の性質 1〜7、`tablePlaceholderPath.test.ts` の往復）: 失敗は無く、記録する種は無い。

## 4. 計画との差（詳しくは `generation-notes.md` の各 Step）

### 4.1 依頼者の決定

- **G1: A**（［留まる］の後のログアウトの印）: S4 の未保存の確かめで［留まる］を選んだ後の印は、次に道が変わったとき（`AppRouter` の効果）に消える形のまま。`security-design.md` 5.1 の「［留まる］の直後に `useClearLogoutIntent()` を呼ぶ」との差。roleadmin（`useUnsavedGuard`）は変えない。`logoutIntent.test.tsx` は「次の道の変化で消える」を確かめる。
- **G2: A**（768px 未満のトップバーのはみ出し）: 幅 220px 固定のサイドバーの横のトップバーに、作業ロールとユーザーメニューの2つのトリガーが収まらなかった（375px で 32〜42px のはみ出し。080・120・130 の 375px の組が落ちた）。768px 未満では作業ロールの切り替えをトップバーから外し、内容の領域の先頭（`ShellLayout` の `main` の先頭、`shell-work-role-bar`）に置く。768px 以上は今のまま。機能設計 D9（トップバーに置き、768px 未満は見える文字を縮める）との差。帯の中では見える文字に「作業ロール:」を戻した（幅に余裕があるため）。
- **G3: A**（768px 未満の開いた作業ロールの一覧のはみ出し）: 内容の領域の右端のトリガーから `bottom-end` で開く一覧が、40 文字のロール名で画面の幅いっぱいに広がり、左へ 16px はみ出した（160 の 375px・360px）。`WorkRoleSwitcher.css` で、768px 未満のときだけ一覧の最大の幅を「画面の幅 − 左右の余白」に抑えた。一覧は make-you-chic-ui の Dropdown が body の直下に描く（`createPortal`）ため子孫の選択子では絞れず、`body:has(.work-role-switcher [aria-expanded='true']) .mycui-dropdown-menu` で「作業ロールのトリガーが開いている間だけ」に絞った。**make-you-chic-ui のクラス（`.mycui-dropdown-menu`）をアプリの CSS で上書きしている**（6節）。

### 4.2 テストで見つけて直した本体の誤り

- ホームの案内の文のコントラストの不足（160 の `color-contrast`）: 案内の文に薄い色の `page-description` を使っていた。`page-notice`（Alert の文字の色のまま）に替えた。

### 4.3 そのほかの差（判断の要らない小さなもの）

- `openUserMenuItem` は `userMenu.ts` へ移し、`registeredUser.ts` から再び出す（既存の呼び出し元の import を保つ）。
- `latestLoad.ts`（tables）は作らず、世代の数を hook の中に持った。
- `logoutIntent.test.ts` は `AppRouter` を描くため `logoutIntent.test.tsx` にした。
- 160 のサイドバーの矩形の確かめは横だけにした（5段・1,000 項目の木のサイドバーは縦に伸びて画面ごと縦に送るため）。開いた一覧は縦も見る。
- 170 の権限の保存はテーブルごとに1回ずつ（サーバーはスキーマの範囲ではスキーマの対象だけを受ける）。170 の氏名は `createRegisteredUser` の決まった値（`secretValues.ts` の決まった値の表に載る）。
- 150 の利用者の画面は、初期管理者をユーザーメニューからログアウトした同じページで開いた（ログアウトの道も通る）。2つ目のロールは API で作って割り当てた（D-10）。
- 160 の差し替えの口に `failedReplies` を足した（除いた表示の上限の確かめ）。
- Step 26 の1回目は `--reporter=line` を付けたため、報告の秘密の確かめが働かなかった。結果のファイルは共有せずに消し、流し直しは報告の部品つきで行った。

### 4.4 make-you-chic-ui の追加の依頼の3点（`make-you-chic-ui-request-2.md`）

- 上流の origin の先頭は 5bf1ffe で、3点（3.1 畳んだ状態の区画の `aria-labelledby`、3.2 畳んだ状態での今の項目の印、3.3 トップバーの文言を外から渡す口）を含む版は無い。固定先は 5bf1ffe にした（14節 Q2: A）。
- 3.1 は、160 の畳んだサイドバーの axe で違反として出なかった（20 組・幅の組とも）。3.2・3.3 は残る（トップバーの畳むボタンとユーザーメニューのトリガーの名前は日本語の固定のまま。受け入れた制約）。

## 5. 固定先の更新（P1）の確かめ

- 前後のハッシュ: e82b651c53ac6e048066fcc049e49386c0eb282e → 5bf1ffe3cb7ee5c49963c3d7e8d4e8d0f032aae6。差は9ファイル・625 行の追加・26 行の削除。`package.json`・`package-lock.json`・`LICENSE`・`Icon` に差は無い。
- 確かめ（Step 5）:
  - `vendorInstall vendorBuild vendorUnchanged osvScan --rerun-tasks` は成功（OSV-Scanner の失敗の条件 0 件）。
  - typecheck・画面のテストの全体（165 ファイル・1,409 件）・lockfile の SHA-256 は変わらない。
  - `verify` は成功（バックエンドの単体 2,082 件・結合 1,165 件）。
  - E2E 010〜150 の 203 件すべて通過。

## 6. 承認の場で確かめたいこと

1. G2: A による機能設計 D9 との差（768px 未満は作業ロールを内容の領域の先頭に置き、帯の中では「作業ロール:」を見せる）。
2. G3: A で、make-you-chic-ui のクラス `.mycui-dropdown-menu` をアプリの CSS（`:has()` で作業ロールの一覧が開いている間だけ）で上書きしたこと。`:has()` を扱えないブラウザでは効かない（今の Chromium・Safari・Firefox は扱える）。
3. G1: A による `security-design.md` 5.1 との差（［留まる］の後の印は次の道の変化で消える）。
4. ホームの案内の文のコントラストの直し（4.2）。
5. 追加の依頼の3点は上流に無く、5bf1ffe を固定先にしたこと（4.4）。
6. 入口の量の判定（7節）と画面の時間（8節）。
7. 統合の前の手元の確かめ（8節）。

## 7. 入口の量（Build and Test へ渡す）

| 点 | 時点 | 入口の合計（gzip） | 中身 |
|---|---|---|---|
| 1（Step 3） | 固定先の更新の前（32e61e9） | 152.0 KB | `index` 135.2 KB・`useTranslation` 16.8 KB。項目 63・`isDynamicEntry` 13 |
| 2（Step 5） | 固定先の更新の後 | 152.6 KB（+0.6） | `index` 135.2 KB・`useTranslation` 17.3 KB。項目 63・`isDynamicEntry` 13 |
| 3（Step 31） | B9 の後（G3 の直しを含む） | 158.3 KB（+5.7） | `index` 151.8 KB・`useTranslation` 6.4 KB。項目 68・`isDynamicEntry` 16 |

- 判定の鍵: `src/features/tables/TablePlaceholderPage.tsx`（gzip 1.3 KB）・`src/features/mypermissions/MyPermissionsPage.tsx`（2.5 KB）・`src/features/auth/LogoutPage.tsx`（0.3 KB）。
- (i) 3つとも manifest に在り `isDynamicEntry: true`、(ii) 入口から静的にたどった集まり（`index`・`useTranslation` の2つ）に無い。**合格**。
- 判定の手順: `./gradlew frontendBundleSize` の後に `frontend/dist/.vite/manifest.json` を読み、`isEntry` の項目から `imports` をたどる。

## 8. 統合の前の関門（Step 33）

- colima は動いている（VM 4 CPU・6GiB、ホームを共有）。README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けて、`caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流した（20:53:58〜21:08:31、14 分 32 秒）。**BUILD SUCCESSFUL**。
  - バックエンド: 単体 2,082 件・結合 1,165 件（失敗・エラー・SKIPPED 0）。
  - 画面のテスト: 183 ファイル・1,558 件すべて通過。カバレッジは行 95.27%・分岐 90.19%（下限 80%・70%、除外は足していない）。
  - Gitleaks: no leaks found。初回の読み込みの JavaScript は 158.3 KB（gzip）。
  - `osvScan` は UP-TO-DATE（lockfile が Step 5 の `--rerun-tasks` の実行から変わっていないため、その結果「失敗の条件 0 件」が正）。
- E2E の全体（Mailpit を起動したまま `caffeinate -dims ./gradlew e2eTest`、21:08:55〜21:19:24、10.4 分）: **010〜170 の 230 件すべて通過**（失敗・flaky なし。B4 の 135、B8 の 060 も通過）。報告の部品は「残してはならない値は含まれていません（値の種類 7・json の報告 1・添付 301・見つかった件数 0）」。報告のフォルダーは消した。
- 手元の確かめ（14節 Q7: A、`cicd-pipeline.md` 5節）: scratchpad の Playwright の台本で行った（リポジトリに足していない）。WAR は使い捨ての内部DB（設定の一時ディレクトリ）で起動し、DSL・ロール・利用者はその中で作った。21:19:42〜21:19:57 に6項目すべて通過。
  1. ユーザーメニューの「ログアウト」→ `/logout` → ログインの画面 → 再ログイン。
  2. 深い道の再読み込み（置き場 `/tables?…`・`/me/permissions`）。置き場では、今の項目の `aria-current` も保たれた。
  3. 戻る・進む（ホーム・置き場・自分の権限の間）。
  4. `/logout` の直接の表示（ログイン中はホームへ戻りログインしたまま、未ログインはログインの画面）。
  5. 管理の項目（`/admin/roles` の再読み込み、ロールとグループの間の戻る・進む）。
  6. 未保存の確かめ（D27）が、サイドバーの移動とユーザーメニューのログアウトで出る。［留まる］で同じ道に残り、［移る］でホームへ移る。
- `pmset -g log`: 20:53〜21:20 にスリープ・DarkWake なし。
- 画面の時間（160 の Step 27 の流し直し。既定の1組・記録だけ。5回の中央値・最大・1回目）:
  - NFR2.5 切り替え（目安の木）: 32.7・33.2・32.7 ms（Met、目標 500）。悪い側の木（1回）は 37.9 ms（記録）。
  - NFR2.6 4段目を開く: 15.0・15.7・14.8 ms（Met、目標 200）。
  - NFR2.7 末端から置き場: 94.5・105.7・97.5 ms（Met、目標 500）。

## 9. 後に回すこと・引き継ぎ

- make-you-chic-ui への追加の依頼: (a) Dropdown の一覧を画面に収める口（幅の上限か、位置を表示の中へ詰める。G3 で上書きした分を置き換えるため）、(b) `make-you-chic-ui-request-2.md` の 3.2・3.3（3.1 は今の検査では違反が出ないが、依頼は残す）。取り込まれたら `WorkRoleSwitcher.css` の上書きを外す。
- Build and Test: 入口の量の3点（7節）、画面の時間、E2E の全体（010〜170）と `verify` の時間、画面のテストの件数とカバレッジ。
- 配備の段（計画 13節）:
  - 前の版に戻すと、U7 の骨組みと 5bf1ffe のサイドバーが無くなる。
  - 古いタブの読み込みの失敗は2つの場合に分ける。開いているタブは読み込み直す。
  - スモークテストで業務のメニューを見るには、DSL・ロール・割り当てを前提として用意する（監査に残る）。
  - 開いた Dropdown・5段のサイドバー・768px 未満の作業ロールの置き場を画面で確かめる。

## 10. 受け入れた制約

- 管理者の印を外されても、次のトークンの更新まで管理の区画が残る（サーバーは 403 で守る。機能設計 R-06）。
- トップバーの畳むボタンとユーザーメニューのトリガーの名前は日本語の固定（機能設計 7節 (i)）。
- 結果の伝え方は Toast だけ（NFR 要件の差 (b)）。
- 画面の時間は手元の PC の1台・既定の1組で測り、値は記録だけ。
