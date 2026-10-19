# 生成の記録 — Intent 261016-business-data-views のコード生成

このファイルは、Bolt ごとの生成の記録をまとめる置き場である（計画の Step 21）。単位ごとの詳しい記録は、各単位の `code-generation/generation-notes.md` にある。

## B3（U4 u4-a11y-carryover）

単位の記録 `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/code-generation/generation-notes.md` を写したもの（見出しは1段下げた）。


計画（`aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/code-generation/code-generation-plan.md`）の Step 1〜21 を行った記録と、計画に無い判断・計画との差を書く。計画の文は書き換えていない（チェックボックスの印だけ）。コミットと push はしていない（指揮役が依頼者の承認を得て積む）。Step 22（コミット）と Step 23（統合）は指揮役が依頼者の承認を得て行う。

### 1. Step ごとの結果

#### Step 1: 作業の準備と走らせ方の確かめ

- `aidlc/` の下の未コミットの変更: 監査の記録 `aidlc/spaces/default/intents/261016-business-data-views/audit/sakura-local-4e42a93f87ce.md` だけ（34 行の追記。SUBAGENT_COMPLETED・GUARD_DISABLED の行）。今の会話の中でフックが書き続けている記録で、止めても無くならないため、コミットせずに進めた（2節の N-1）。
- git の対象外の生成物: `frontend/test-results/`（`.gitignore` の 114 行）を消した。`frontend/playwright-report/` は無かった。
- ブランチ: `develop`（`1913503`）から `feature/261016-business-data-views-b3` を作って移った。
- 走らせ方の確かめ（`unit-test-instructions.md` の 3.1）: 4 ファイル・58 件が通った。
- 変更の前の `npx vitest list | wc -l`: 1508 行。

#### Step 2: 上流の確かめ（A1）

- `git -C vendor/make-you-chic-ui fetch origin` を流した。
- `b0a1e79` の完全な値: `b0a1e79ef4f78416d37f78e75dfc6da3fc4b19df`。`merge-base --is-ancestor b0a1e79 origin/main` が成り立った（上流の `main` から辿れる）。
- `origin/main` の先頭は `b0a1e79` そのもの。`git log --oneline a9309c4..origin/main` は1件（`b0a1e79 Dropdown: 説明付き項目でlabel-content-name-mismatch(WCAG 2.5.3)に 違反しないようDOM構造を変更`）。`b0a1e79` より新しい上流のコミットは無い（D2 の扱いは不要）。
- `git diff --stat a9309c4 b0a1e79`: 3 ファイル（`packages/make-you-chic-ui/src/components/Dropdown/Dropdown.css`・`Dropdown.test.tsx`・`Dropdown.tsx`、119 行の追加・42 行の削除）。`package.json`・`package-lock.json`・`packages/make-you-chic-ui/package.json`・`LICENSE` の差は無い。
- `hasInstallScript` の付いた項目の名前（`git show <rev>:package-lock.json` の読み取り）: `a9309c4`・`b0a1e79` ともに `node_modules/fsevents` の1件で同じ。増えた・変わった名前は無い。
- 変更の中身（読み取り）: Dropdown の項目の説明（`mycui-dropdown-item-description`）を、menuitem の制御（`button`・`a` の `role="menuitem"`、クラスは `mycui-dropdown-item-control` に変わった）の子孫から、包む `div.mycui-dropdown-item` の中の兄弟へ移した。`aria-describedby` の結び付き・`role="menuitem"`・portal の形・`data-testid`（`dropdown-trigger`・`dropdown-menu`・`dropdown-item-<番号>`）は保たれている。

#### Step 3: 固定先の更新

- `git -C vendor/make-you-chic-ui checkout b0a1e79` で固定先を移した。前後のコミットハッシュ: `a9309c4ee863b162236571a7d6300c62fd4b4f70` → `b0a1e79ef4f78416d37f78e75dfc6da3fc4b19df`。
- `git diff --stat` の差は `vendor/make-you-chic-ui` の固定先と、監査の記録（N-1）だけ。
- `./gradlew vendorInstall vendorBuild vendorUnchanged frontendInstall`: 成功（`vendorUnchanged` が通り、vendor の作業フォルダに差は無い。`frontendInstall` は UP-TO-DATE）。`vendor/make-you-chic-ui/packages/make-you-chic-ui/dist` は作り直された。
- `git diff --exit-code frontend/package-lock.json`: 差なし。`frontend/package-lock.json` の `hasInstallScript` の付いた項目の名前は、前後とも `node_modules/fsevents` の1件。
- コミットは積んでいない（計画の C1 は指揮役が依頼者の承認を得て積む）。

#### Step 4: 固定先の後の既存のテスト（D7・U4-NFR7.6）

- `unit-test-instructions.md` の 3.2 の「固定先の後」のコマンド（`WorkRoleSwitcher`・`UserRowActions`・`UserSearchBox`・`EditProfileDialog`・`CandidateDialog`・`PermissionPanel` のテスト）を書き換えずに流し、6 ファイル・62 件が通った。`toHaveAccessibleDescription` の確かめは、説明が menuitem の外の兄弟になっても `aria-describedby` で今までどおり通る。残りの4ファイルは Step 15 の verify で確かめた。

#### Step 5・6: 名前の関数 L1

- `frontend/src/shared/row-actions/rowActionsName.ts` を `frontend-components.md` 2節の形で書いた。
- `rowActionsName.test.ts`（6 件）と `rowActionsName.property.test.ts`（4 つの性質）を書き、10 件が通った。性質のテストで失敗は出ておらず、記録する seed は無い。

#### Step 7・8: 行の操作の部品

- 3つの機能の `messages.ts` を変えた（`useradmin.actions.row`・`*.actions.busySuffix` を足し、`*.actions.name`・`*.actions.busyName` を消した。英語の `busySuffix` は先頭の空白つきの ` (processing)`）。
- `UserRowActions.tsx`・`GroupRowActions.tsx`・`RoleRowActions.tsx` のトリガーの `aria-label` を `rowActionsName(...)` に差し替え、`busy` なら `busySuffix` を足した。Dropdown・Button・`placement="bottom-end"`・`data-testid`・見える文字・props・状態は変えていない。先頭の説明の文の名前の形を直した。
- 部品のテストを書き換え・足した（`UserRowActions.test.tsx` 11 件、`GroupRowActions.test.tsx` 9 件、`RoleRowActions.test.tsx` 9 件）。7つのページのテストの名前の照合を書き換え、3つのページ（`UserAdminPage`・`GroupListPage`・`RoleListPage`）に「名前を作っても初めの描画の一覧の API の呼び出しが1回のまま」のテストを1件ずつ足した（U4-NFR7.1）。
- `unit-test-instructions.md` の 3.2 の「行の操作」のコマンド: 10 ファイル・149 件が通った。

#### Step 9・10: トップバー L2

- `ja.ts`・`en.ts` の `topbar.userMenu` を `{{initials}}: {{name}}のメニュー`・`{{initials}}: Menu for {{name}}` にし、`topbar.userMenuWithoutInitials`（`{{name}}のメニュー`・`Menu for {{name}}`）を足した。
- `ShellLayout.tsx` で `getInitials` を `make-you-chic-ui` から名前付きで読み、頭文字が空文字なら `topbar.userMenuWithoutInitials` で名前を作る形にした。状態・効果・API の呼び出しは足していない。
- `ShellLayout.test.tsx` の 2 か所を「管: 管理者のメニュー」「管: Menu for 管理者」に書き換え、4 件を足した（2語の氏名の日英、頭文字が Avatar の丸の文字と同じ、空白だけの氏名で「: 」が出ない、ユーザーメニューを開くまで業務のメニューの読み込みが1回のまま）。テストの手伝いの `renderShell` に `displayName` の指定を足した。
- `unit-test-instructions.md` の 3.2 の「トップバー」のコマンド: 44 件が通った。あわせて文言の検査（`src/app/i18n` ほか 16 ファイル・114 件）も通った。

#### Step 11・12: E2E の axe の補助 L3〜L6

- `frontend/vitest.config.ts`（A3）: `include` に `e2e/support/**/*.test.ts` を足し、`exclude` を `e2e/**/*.e2e.ts` にした。カバレッジの設定は変えていない。
- `frontend/e2e/support/axeElementKey.ts`（L3）・`frontend/e2e/support/axeRuleChecks.ts`（L5・L6）を新しく作り、`frontend/e2e/support/axe.ts`（L4）を変えた（`AxeViolationNode` を `{ key; primaryButton }` にし、`AxeSummary` に `labelContentNameNodes` を足し、`runAxe` の1回の `page.evaluate` の中で鍵を作り、毎回 `axe-nodes` の注記を残す）。`REQUIRED_RULES`・`KNOWN_VIOLATIONS`（空）・`missingRequiredRules`・`WCAG_TAGS` は変えていない。
- `135-dsl-admin-accessibility.e2e.ts`・`160-app-frame-accessibility.e2e.ts` の失敗の文を `describeAxeNode`（`規則 + 鍵`）にした。
- `axeElementKey.test.ts`（8 件）・`axeRuleChecks.test.ts`（8 件）: 16 件が通った。
- `npx vitest list | wc -l`: 1552 行（Step 1 の 1508 ＋ この単位で足した 44 件。消したテストは無い）。`e2e/support` の行は 16、`.e2e.ts` の行は 0（Vitest は E2E を拾っていない）。
- 型の検査（`tsc --noEmit`）・oxlint・ESLint・Prettier・ライセンスヘッダーの検査が通った。

#### Step 13: 報告の秘密の確かめの値

- `frontend/e2e/support/secretValues.ts` の `SECRET_FIXED_VALUES` に、`userAdminFixtures.ts` の検査の一覧の6行の氏名とメールアドレス（`LONG_EMAIL` と同じ値を含む）と、130 の `Probe Sample` を足した（値はこのファイルにだけ置き、ここには書き写さない）。

#### Step 14: E2E の名前の照合の書き換えと検索の流し直し

- `110-user-admin-flow.e2e.ts` の5か所の `/の操作$/` を `/^操作: /` に、`140-role-admin-accessibility.e2e.ts` の2か所を `` `操作: ${LONG_ROLE_NAME}` `` に、`160-app-frame-accessibility.e2e.ts` の `/^Menu for /` を `/: Menu for /` にした。110 の「名前に氏名と宛先が入る」確かめは残した。
- 検索の流し直し（`frontend/src`・`frontend/e2e`）:

| 検索語 | 件数 | 残りの中身 |
|---|---|---|
| `の操作$` | 0 | — |
| `` の操作` `` | 0 | — |
| `の操作'` | 2 | `dsl.page.tabsLabel`「DSL の操作」とそのテスト（タブの並びの名前。D12 で変えないもの） |
| `Actions for` | 0 | — |
| `actions.name` | 0 | — |
| `actions.busyName` | 0 | — |
| `のメニュー'` | 6 | 新しい形（`topbar.userMenu`・`topbar.userMenuWithoutInitials`、ShellLayout のテストの新しい名前）と、`mypermissions.column.inMenu`「業務のメニュー」（別の文言） |
| `^Menu for` | 1 | `ShellLayout.test.tsx` の空の氏名のテスト（頭文字が無いときの新しい形） |
| `node.target` | 1 | `frontend/e2e/support/axe.ts` の `page.evaluate` の中（ブラウザの側で要素を引くためだけ。戻り値に入れない） |
| `node.testId` | 0 | — |

- `UserRowActions.tsx`・`GroupRowActions.tsx`・`RoleRowActions.tsx`・`ShellLayout.tsx`・`rowActionsName.ts` に、ログ・`console`・トレースの呼び出しは 0 件（U4-NFR4.2）。`frontend/src` から `e2e/` を読む import も 0 件。

#### Step 15: 1コマンドの検査

- colima は動いていた。README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、`caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流した。
- 結果: BUILD SUCCESSFUL。時間は 17分37秒（2026-10-19T17:22:35+09:00〜17:40:13+09:00）。流した後の `pmset -g log` に、この間の Sleep・DarkWake は無い。
- コンテナの実行環境が無いときの警告は出ていない。SKIPPED は4件で、どれも対象DB の種類ごとに当たらない場面を名前で飛ばす既存のテスト（`MariadbBusinessDataReadIT`・`MysqlBusinessDataReadIT` の PostgreSQL の timetz の場面、`PostgresBusinessDataReadIT` の MySQL・MariaDB の場面）。
- Vitest: 188 ファイル・1633 件が通った（`vitest list | wc -l` の 1552 行とは数え方が違う。比べの元は Step 1・12 の `vitest list` の行の数）。
- フロントエンドのカバレッジ（全体の合計）: 行 95.37%（6265 中 5975）・分岐 90.34%（4496 中 4062）。下限（行 80%・分岐 70%）を満たす。`rowActionsName.ts` は行・分岐とも 100%。カバレッジの報告（`frontend/coverage/coverage-summary.json`）の 252 ファイルに `e2e/` のファイルは 0。
- OSV-Scanner: `osvScan` は UP-TO-DATE（入力の lockfile に変化が無いため、前の結果のまま。lockfile は変えていない）。

#### Step 16: `dist` に axe-core と E2E の補助が入らないこと

| 検索語 | `frontend/node_modules/axe-core/axe.min.js`（陽性の対照） | `frontend/dist`（Step 15 のビルドの結果） |
|---|---|---|
| `label-content-name-mismatch` | 9 | 0 |
| `axe.version` | 9 | 0 |
| `Deque Systems` | 1 | 0 |
| `axeElementKey` | — | 0 |
| `labelContentNameProblems` | — | 0 |

- `frontend/src` から `e2e/` を読む import: 0 件。

#### Step 17: E2E の全件（4.13）

- Mailpit は動いていた（起動・停止はしていない）。`caffeinate -dims ./gradlew e2eTest` を流した。
- 結果: 232 件すべて通過（Playwright の表示で 10.7 分、Gradle で 10分43秒。2026-10-19T17:40:53+09:00〜17:51:37+09:00）。基準（前の Intent の 232 件・約 10分35秒）とほぼ同じで、1.5 倍（約 15分53秒）を超えていない。流した後の `pmset -g log` に Sleep・DarkWake は無い。
- 報告の秘密の確かめ（`frontend/playwright-secret-check-reporter.ts`）が通った（見つかった件数 0。json の報告 1・添付 304 を確かめた）。json の報告を文字列で検索し、足した見本の値（氏名・メールアドレス）が無いことも確かめた。
- 注記 `axe-nodes` は 1083 件残り、規則 `label-content-name-mismatch` の要素は 0 件、違反の要素は 0 件だった（4.13 ではこの規則が `experimental` で流れないという計画の前提のとおり）。

#### Step 18: 試しの準備（作業フォルダの中だけ。統合しない）

- 試しの前の作業フォルダの状態を記録した（`git status --short` と `git diff --stat`、監査の記録を除く。frontend と vendor の差の要約の値も取った）。
- 試しの版: `npm view axe-core version` は 4.14.0（2026-10-05 公開。4 系の最新）。4.14.0 の CHANGELOG（上流の `v4.14.0` の `CHANGELOG.md` を読み取りで読んだ）で、既定で流れるようになった規則は `label-content-name-mismatch` の1つ（「Enable rule by default」#5421）。同じ規則の直し（語ごとの比べ、CJK の語の分け方、全角の正規化、括弧の中を無視、値を持つ role は incomplete）も入っている。
- `frontend/.npmrc` の `ignore-scripts=true` のまま `npm install --save-dev axe-core@4.14.0` を流した。`frontend/package-lock.json` の `node_modules/axe-core` は 4.14.0・`license` は `MPL-2.0`・`hasInstallScript` 無し。`hasInstallScript` の付いた項目の名前は前と同じ `node_modules/fsevents` の1件。`npm ls axe-core` は 4.14.0 の1つ（vitest-axe の分は deduped）。差は `frontend/package.json`・`frontend/package-lock.json` の2ファイル（5 行の追加・5 行の削除）。
- 一時の差分（D4: A）: axe の違反の確かめ10か所を `expect` から `expect.soft` にした。対象は `050`・`060`・`070`・`080`・`100`・`120`・`130`・`135`・`140`・`160` の各 `*.e2e.ts` の1か所ずつ。規則・要素は外していない（違反は失敗として数える形のまま）。

#### Step 19: 試しの実行と記録（4.14.0）

- `caffeinate -dims ./gradlew e2eTest`: 232 件すべて通過（Playwright の表示で 10.6 分、Gradle で 10分51秒。2026-10-19T17:54:09+09:00〜18:05:00+09:00）。json の報告の `stats` は expected 232・unexpected 0・flaky 0。`expect.soft` の失敗も 0 で、**どの規則の違反も 0 件**だった。流した後の `pmset -g log` に Sleep・DarkWake は無い。報告の秘密の確かめも通った（見つかった件数 0）。
- 集計: json の報告（`frontend/test-results/e2e-results.json`）の注記 `axe-nodes` 1083 件から、一時の場所の台本で集めた（台本はリポジトリに足していない）。表には規則・鍵・件数だけを書く。

**区分 × 結果（規則 `label-content-name-mismatch` の要素の数）**

| 区分 | passes | incomplete | violations | 見つかったファイル（passes/incomplete/violations） |
|---|---|---|---|---|
| C1 利用者の行の操作のトリガー | 882 | 0 | 0 | 120（882/0/0） |
| C2 グループの行の操作のトリガー | 0 | 0 | 0 | 見つからない |
| C3 ロールの行の操作のトリガー | 129 | 0 | 0 | 140（129/0/0） |
| C4 トップバーのトリガー | 180 | 638 | 0 | 080（80/0/0）・100（80/0/0）・130（20/0/0）、060（0/35/0）・120（0/189/0）・135（0/48/0）・140（0/152/0）・160（0/214/0） |
| C5 作業ロールのトリガー | 818 | 0 | 0 | 060・080・100・120・130・135・140・160（すべて passes） |
| C6 作業ロールの項目 | 176 | 0 | 0 | 160（176/0/0）。`button[menuitem]` 112・`a[menuitem]` 64 |
| C7 トップバーの項目 | 0 | 0 | 0 | 見つからない |
| 区分に当たらない要素 | 9711 | 113 | 0 | — |

- 見つからなかった区分と理由:
  - C2: グループの一覧の行の操作のトリガーが表示された状態で axe を流す場面が今の E2E に無い（140 はロールの一覧で axe を流し、グループの画面は流れの確かめだけ）。B7 で対象にするなら、場面を足すかを B7 の計画で決める。
  - C7: ユーザーメニューを開いた状態で axe を流す場面が今の E2E に無い（計画の Assumptions のとおり）。
- C4（トップバーのトリガー）: passes は 080・100・130 の場面で、incomplete は 060・120・135・140・160 の場面。130 は2語の氏名 `Probe Sample`（頭文字 `PS`）で passes。incomplete の場面は1語の氏名の利用者（頭文字が1文字）で、機能設計の D10c の見込み（1語の氏名では規則が incomplete になり、合格の根拠に使わない）と同じ。080・100 は2語の氏名の場面と見られる（ファイルごとの氏名は記録に書き写さない）。違反は 0 件で、前の Intent の見立て（トップバーの頭文字）は、今の名前の形では違反にならないと確かめられた。
- C5（作業ロールのトリガー）: 見える文字が「作業ロール:」と名前の2つの部分、名前が「作業ロール: <名前>」の形で、すべて passes（トップバーの中 566・狭い幅で内容の領域に置いた形 252）。
- C6（説明を持つ作業ロールの項目を含む）: 160 の作業ロールのメニューを開いた場面ですべて passes。固定先 `b0a1e79`（説明を menuitem の外の兄弟にした形）で、前の Intent の見立て（作業ロールの項目）は違反にならないと確かめられた。
- 前の Intent で incomplete だった 060: 規則 `label-content-name-mismatch` の incomplete は C4（トップバーのトリガー）の 35 件だけ。
- 区分に当たらない incomplete（113 件。違反ではない）: `140` の表の見出しの中のボタン（鍵 `element=button owner=button marks=mycui-button path=button>span>th>tr>tbody`）46 件と、`140`・`160` の木の開閉のボタン（`owner=shared-tree-toggle`）67 件。
- vitest-axe（同じ 4.14.0）: 画面のテストの全件（`npm test`）の1回目は `src/eslintImportRules.test.ts` のファイルの読み込みで1件の失敗（そのファイルの 22 件が流れず、残り 187 ファイル・1611 件は通過）。単独で流すと 22 件が通り、全件の2回目は 188 ファイル・1633 件がすべて通った（2節の N-10）。jsdom ではこの規則を判定しないため、vitest-axe の結果は 4.13 と同じ見込み（件数として変わりは無かった）。
- 試しの記録の形（L6）で気づいたこと（B7 へ）: 鍵の owner の正規化は末尾の数字・`r<数字>` だけを `-*` にするため、`data-testid` に名前を持つもの（サイドバーの項目 `sidebar-nav-biz:<位置>`、DSL の違いの表の開閉 `dsl-diff-toggle-<スキーマ>/<テーブル>`）は名前のまま鍵に残る。見本の DSL のスキーマとテーブルの名前で、個人に関する値ではないが、U3 の業務データの行の値に当たる `data-testid` を作るときは、鍵に値が残らない形（末尾の ID の形）にそろえる必要がある。

#### Step 20: 試しの片付けと戻ったことの確かめ

- `git checkout --` で `frontend/package.json`・`frontend/package-lock.json` と、この単位で変えていない7つの E2E（050・060・070・080・100・120・130）を戻した。この単位で変えた 135・140・160 は、`expect.soft` の1か所ずつだけを `expect` に戻した（`*.e2e.ts` の `expect.soft` は 0 件）。
- `./gradlew frontendInstall --rerun-tasks`（`npm ci`、`ignore-scripts` のまま）で入れ直した。`npm ls axe-core` は 4.13.0 の1つ（vitest-axe の分は deduped）。
- `git diff --exit-code frontend/package.json frontend/package-lock.json` は差なし。`git status --short` と `git diff --stat`（監査の記録と計画のチェックボックスを除く）は Step 18 の記録と同じ。frontend と vendor の差の要約の値も Step 18 の前と一致した（U4-NFR5.4）。
- 違反は 0 件だったため、D6: A の「止めて諮る」には当たらず、Step 21 へ進んだ。B3 で直すものは無い。

### 2. 計画に無い判断・計画との差

- **N-1（Step 1）**: `aidlc/` の下の未コミットの変更は監査の記録の追記だけで、この会話の中でフックが書き続けているため、コミットの承認を待たずに進めた。コミットは指揮役が行う。
- **N-2（Step 11、marks の取り方）**: 計画 4.4 の「marks に入れる印のクラス」の一覧（`work-role-switcher-trigger`・`useradmin-row-actions`・`groupadmin-row-menu`・`roleadmin-row-menu`）のうち3つはトリガーを包む要素のクラスのため、marks を「自分のクラスのうち `mycui-` で始まるものと一覧に当たるもの」に「祖先のクラスのうち一覧に当たるもの」を足した集合（並べ替え済み）とした。祖先の `mycui-` のクラスは入れない。
- **N-3（Step 11、menuOwner の形）**: menuOwner は、開いているトリガーの `<owner か ->` の後に ` .<mark>` を並べた文字列（例 `work-role-switcher .work-role-switcher-trigger`）にした。開いたトリガーが2つ以上のときの印は `!multiple-open-triggers`（`axeRuleChecks.ts` の `MULTIPLE_OPEN_TRIGGERS` と `axeElementKey.ts` の中の定数で同じ値）。開いたトリガーが無いメニューの中の要素は null（区分に当たらず、L5 の「要素が見つからない」で拾う）。
- **N-4（Step 11、区分の当てはめ）**: C1〜C3 は、owner に加えて element が role の無い `button` で、marks に包む要素のクラス（`useradmin-row-actions`・`groupadmin-row-menu`・`roleadmin-row-menu`）があることを条件にした。グループとロールの行の「開く」のリンク（同じ owner を持つ `a`）を区分に入れないため。選択子が1つの要素を指さない結果の要素は、鍵 `UNRESOLVED_KEY`（element が `(unresolved)`）にした。
- **N-5（Step 11、既知の違反の照らし）**: `splitKnownViolations` は鍵の owner で照らす（計画 4.1 の R-04）。owner は自分か近い祖先の `data-testid` のため、前の形（要素自身の `data-testid`）より広く当たりうる。`KNOWN_VIOLATIONS` は空のままで、今は効かない。
- **N-6（Step 12、評価し直しの確かめ）**: `axeElementKey.toString()` を評価し直す確かめは、`eval`・`new Function` を使わず（oxlint の決まり）、Node の `node:vm` の `runInNewContext` で別の文脈に評価して行った。外の名前に頼っていれば ReferenceError で落ちる形。
- **N-7（Step 12、`axe.ts` の読み込み）**: `axeRuleChecks.test.ts` の `splitKnownViolations` の確かめは、`axe.ts`（`@playwright/test` と `node:fs` を読む）を Vitest から直接 import して行った。jsdom の実行環境でも読み込めた。
- **N-8（Step 8・10、件数）**: 部品のテストの件数は、計画の目安（`UserRowActions` 8・`GroupRowActions` 7・`RoleRowActions` 7・`ShellLayout` の足し 5）と少し違う（11・9・9・4）。既存のテストを残したまま、計画の確かめの項目をすべて入れた結果。ShellLayout は 2語の氏名の日英を1件にまとめた。
- **N-9（Step 13、値の範囲）**: 測りの見本の行（`measurementListPage` の `測定 NN`・`sokutei.NN@example.com`）は、行の操作の名前に入るが、番号つきで生成する値のため `SECRET_FIXED_VALUES` には足していない（値の形の表 `SECRET_VALUE_SHAPES` も変えていない）。
- **N-10（Step 19、vitest の1回目の失敗）**: 4.14.0 の下の `npm test` の1回目で `src/eslintImportRules.test.ts` がファイルの読み込みで落ちた（失敗の文は残っていない）。単独の実行と全件の2回目では通った。このファイルは ESLint の設定を読み込んで決まりを確かめるテストで、axe-core を使わず、この単位の変更にも触れない。team.md の「手元で再現しないもの」として、再現の試みは全件の流し直し1回と単独の1回までに限り、見立て（PC の負荷の下での ESLint の読み込みの時間切れ。未検証）を記録して進めた。同じテストが二度目に落ちたら原因を直す。Step 15 の verify（4.13）では通っている。

### 3. 依頼（5）の送付

- 依頼（5）（`aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-5.md`）は、依頼者の答えで「送ってある（日付は答えに無し）」（計画の 10節の D3: A）。上流の対応の確かめは B7（U3 の計画）で行う。

### 4. B7（U3 の計画の最後の Bolt）へ引き継ぐこと

- axe-core の採る版の確定（その時点の 4 系の最新。B3 の試しの版は 4.14.0。CHANGELOG・`license`・`hasInstallScript` の名前の比べ、`npm ls axe-core` で版が1つ）。
- `.github/dependabot.yml` の axe-core の `ignore` とコメントを外し、受け方のコメントを書く。Way of Working との差は「lockfile だけの更新の例外、根拠は Testing Posture の axe-core の決まり」と書く（基盤の設計のレビューの R-01）。
- `REQUIRED_RULES` に `label-content-name-mismatch` を足し、Step 19 の表で要素が見つかった区分（C1・C3・C4・C5・C6）を必須の対象として E2E に passes の確かめ（L5 の `labelContentNameProblems`）を足す。C4 は1語の氏名の場面で incomplete になるため、2語の氏名の場面（130 など）で確かめる。C2・C7 は場面が無く、対象に入れるなら場面を足す。対象の一覧は B7 の計画で確定して承認を得る。
- U3 の行の操作（`tables-row-actions-*`）と行のリンクの区分は、U3 の B5・B6 の試しで同じ形（L6）で記録して足す。U3 で新しく作る `data-testid` は、鍵に行の値が残らない形（末尾の ID）にそろえる（Step 19 の気づき）。
- 依頼（5）の上流の対応の確かめ（上流のソースで口の形）と、固定先の2回目の更新（間に合えば）。
- `dist` の確かめ（Step 16 と同じ手順）、E2E と vitest-axe の違反 0 件と件数、E2E の時間。
