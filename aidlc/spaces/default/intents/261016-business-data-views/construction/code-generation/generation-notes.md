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

## B1（U5 u5-carryover-tests）

単位の記録 `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/code-generation/generation-notes.md` を写したもの（見出しは1段下げた）。

計画（`aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/code-generation/code-generation-plan.md`）の Step 1〜16 を行った記録と、計画に無い判断・計画との差を書く。計画の文は書き換えていない（チェックボックスの印だけ）。コミットと push はしていない。Step 17（コミット）と Step 18（統合）は指揮役が依頼者の承認を得て行う。

### 1. Step ごとの結果

#### Step 1: 作業の準備と走らせ方の確かめ

- `aidlc/` の記録のコミット（A1）は指揮役が `05a9b37` で済ませた。その後も監査の記録 `aidlc/spaces/default/intents/261016-business-data-views/audit/sakura-local-4e42a93f87ce.md` にフックの追記が続いており、コミットしていない（統合の前に指揮役が積む。計画の Step 18）。
- git の対象外の生成物: `frontend/test-results/` を消した。`frontend/playwright-report/` は無かった。
- B2・B3 の統合の後の CI（4.5。読み取りだけ）:
  - B2 の `ea23bf3`（run 39196260878）: 1回目 failure・2回目 success。1回目に落ちたのは `PatternCheckerTest`（「valid and invalid regular expressions are told apart by compiling only」）の1件。`AdminForbiddenProvider.test.tsx` は 12 件通過。
  - `0427d45`（run 39172328548）: 1回目 failure・2回目 success。1回目に落ちたのは `PasswordChangePage.test.tsx` の「drops the answer when the screen is left while sending」の1件。`AdminForbiddenProvider.test.tsx` は 12 件通過。
  - どちらの試行でも `RoleConcurrencyIT` と `AdminForbiddenProvider.test.tsx` は落ちていない。B3（`e34c260`〜`1e78b51`）は push されておらず CI が無い（`origin/develop` は `1913503`）。
  - 対象の外の2件（別々のテスト）が1回目に落ちて再実行で通っている。team.md の「不安定なテストと CI の失敗」の決まりの扱いを依頼者に確かめたい（code-summary.md の 6節）。
- ブランチ: `develop`（`05a9b37`）から `feature/261016-business-data-views-b1` を作って移った。
- 走らせ方の確かめ（`unit-test-instructions.md` 3.1、変更の前）: `LargeRoleTransferYamlTest` 3件、`RoleConcurrencyIT` 4件、`AdminForbiddenProvider.test.tsx` 12件がすべて通過。

#### Step 2・3: テストの補助と単体テスト

- `TestRoleBarrier`: `Gate.arrivedThread()`（止まったスレッド。`arrived.countDown()` の直前に記録）、`Signal.passedThread()`（最初に通ったスレッドだけを `compareAndSet` で記録）を足した。止める・通すの振る舞いと既存の口は変えていない。
- `AuditWriteReorderBarrierConfig`（新）と入れ子の `AuditWriteReorderBarrier`: 口は `armFor(Thread held, Thread releaser, Duration waitLimit)`（A3）・`heldArrived()`・`releasedByTimeout()`・`disarm()`。状態は1つのロックで守り、準備の回の番号で前の回の待ちを必ず放す。止める相手と放す相手が同じスレッドのとき・上限が正でないときは `IllegalArgumentException`（文はスレッドの名前と上限の値だけ）。
- `RoleActors.Actor`: `toString` を `Actor[userId=<数>, email=***, token=***]` にした。
- 単体テスト（`--rerun`）: `AuditWriteReorderBarrierTest` 7件、`TestRoleBarrierTest` 3件、`RoleActorsTest` 2件がすべて通過。順は `CountDownLatch` とスレッドの状態（`TIMED_WAITING`）で作り、`sleep` は使っていない。上限の場合だけ 200 ミリ秒を渡した。

#### Step 4: 二段の1段目（直す前の確かめ方で落ちること）

- 入れ替えた順の場合を足し、確かめ方は直す前の形（ID の順で1行目・2行目を決め打ち）のまま、そのメソッドだけを流した（作業フォルダの中だけ。コミットしない）。
- 1回目は `armFor` が「止める相手と放す相手が同じスレッドです: http-nio-auto-1-exec-2」で止まった。原因と直し方は 2節の N-2（知らせを先の保存が止まった後に作る）。
- 直した後の結果（1回で起きた）:
  - 失敗の文: `Expecting actual: "{"roleName":"同時の保存-1-2289630e","changes":[{"target":{...},"before":{"main":"READ",...},"after":{"main":"FULL",...}}]}" to contain: ""before":{"main":null"`（`detail` の値だけで、トークン・メールアドレスは出ていない）
  - 待ち合わせ: `heldArrived=true`・`releasedByTimeout=false`。止めた相手は `http-nio-auto-1-exec-2`（先の保存）、放す相手は `http-nio-auto-1-exec-3`（後の保存）。
  - 監査の行: 「後: FULL」の行が ID 2、「後: READ」の行が ID 3（入れ替えが実際に起きた）。後の保存は 204。
- 止める相手は印（`AFTER_LOCK` の `Gate`）で特定できた。出来事の中身で特定する形への切り替えは要らなかった。

#### Step 5: 二段の2段目（直した確かめ方）

- #7 を自然の順（止める相手 = 後の保存、放す相手 = 先の保存）と入れ替えた順の2つのメソッドにした。どちらも同じ待ち合わせで確実に作る（D2: A）。
- 確かめ: 対象のロールの `ROLE_PERMISSION_CHANGED` がちょうど2行で、「前が空・後が READ」と「前が READ・後が FULL」がちょうど1行ずつ（ID の順は合否に使わない）、最後の主権限が FULL。場合ごとの前提として `heldArrived` 真・`releasedByTimeout` 偽・ID の順がその場合に作った順。
- 409: 入れ替えた順は待ち合わせの結果の確かめと「409 でないこと」を 409 の分岐より前に置いた（D1: A）。自然の順は今の分岐（`ROLE_BUSY` の code だけ）のまま。
- 結果を待つ順: 放す相手の結果を先に待つ（入れ替えた順は後の保存 → 先の保存、自然の順は先の保存 → 後の保存）。
- `@Import` に `AuditWriteReorderBarrierConfig` を足し、`@AfterEach` で `reorderBarrier.disarm()` を `barrier.reset()` の前に呼ぶ。
- 読み合わせ（R-05）: `admin.token()` は `api.save` の引数だけで、失敗の文・`.as(...)` に渡していない。`.as` に出すのは `detail` の一覧・真偽の説明・409 のときの code だけ。
- `RoleConcurrencyIT` の5件（#7 の2件、#1 の2件、#4 の1件）がすべて通過。

#### Step 6: 繰り返し

- `RoleConcurrencyIT` を `--rerun` で5回流し、5回とも5件が通過（1回 8.25〜8.36 秒、21:24:53〜21:25:45 JST）。
- 最後の回の出力に `ROLE_BUSY` は 0 件（409 の分岐に入っていない）。WARN の `GlobalExceptionHandler` 3件は #1・#4 の想定内の 4xx。
- 入れ替えは Step 4 で起こせたため、30 分の再現の試みは要らなかった。

#### Step 7: 画面のテストの試し（コミットしない）

- T1: `Probe` の `useLayoutEffect` で最初の1回だけ `effectRuns` を写し、対象のテストの写す値をそれに替えて流した。5回とも `AssertionError: expected 1 to be +0` で落ちた（`-t 'keeps the same function'` で対象の1件だけを流した。1 failed・11 skipped）。
- T2: T1 の `Probe` の変更を残し、写す前に `await waitFor(() => expect(effectRuns).toBe(1))` を置いた。5回とも通過（1 passed・11 skipped）。
- 試しの変更を `git checkout` で戻し、`git diff --exit-code` で差が無いことを確かめた。

#### Step 8: 画面のテストの直し

- 対象の1件だけ、`findByTestId` の後に `await waitFor(() => expect(effectRuns).toBe(1))` を置いてから `before`・`runsBefore` を写す形にした。403 の後の確かめは保ち、時間の上限は延ばしていない。Prettier の確かめは通過。
- ファイルの12件を5回流し、5回とも12件が通過（1回 0.93〜0.99 秒）。

#### Step 9・10: `PERF_DB_URL` の確かめ

- 関数 `check_perf_db_url` を `perf/lib/perf-db-url.sh` に書いた。止めたときは決まりの名前（前の部分・使えない文字・空の要素・許さない設定・重なり）だけを標準エラーに出す。
- `perf/dsl-timing.sh` の引数の確かめの直後・既存の環境の確かめの前の最上位で `source` して呼び、外れたら `exit 1`。冒頭の注記に受け付ける形を書き足した。144〜145 行（今は 147〜148 行）の書き込みは変えていない。
- 試しの台本 `perf/lib/perf-db-url-test.sh`: 19 件（悪い入力 15・正しい入力 4）がすべて期待どおり、値の漏れ（目印の語と `jdbc:` の件数）は 0 件。
- 親が止まることの確かめ: `PERF_DB_URL='jdbc:h2:tcp://example.invalid/x' ./perf/dsl-timing.sh postgres` は終了コード 1（「前の部分」）、`~/.mastersmith-perf-*` の前後の差は無し（前も後も 0 件）。改行を含む値でも終了コード 1（「使えない文字」）。Docker は使っていない。

#### Step 11・12: 記録のループと1回の測りの台本

- `perf/dsl-mixed-memory.sh` を書いた（流れは台本の冒頭と `perf/README.md` の (b) 節）。記録のループだけを流す `--loop-only <コンテナ> <秒> <TSV>` も持つ（2節の N-5）。
- 構文: `bash -n` で `perf/dsl-timing.sh`・`perf/lib/perf-db-url.sh`・`perf/lib/perf-db-url-test.sh`・`perf/dsl-mixed-memory.sh` が通った。
- 空回し（A2・D5: A）: `--loop-only mastersmith-app-1 5 build/perf-results/u5-loop-dryrun/memory.tsv`。見出しと5行（12:32:02Z〜12:32:06Z）が書かれ、`NA` は 0 件。ループの終わりの後に `docker top` に `sleep 1`・`bash -c` の処理は残っていなかった。
- `anon` の列の確かめ:
  - 計画の形（同じ秒に別の `docker exec` で `grep '^anon '` を読む）では、5秒とも約 0.2 MB 違った（例 695406592 と 695218176）。別々の `docker exec` の処理が同じ cgroup の `anon` を変えるためと見られ、値の大きさは `anon` で、ほかの列（`file` は 741376 など）とは桁が違う。
  - 列の取り違えを確実に見るため、同じ時点の `memory.stat` の写し1つから、ループと同じ awk の取り出しと `grep` の値を並べた。5つのキー（anon・file・inactive_file・active_file・kernel）すべてが一致した（`build/perf-results/u5-loop-dryrun/stat-snapshot-check.txt`）。
- アプリのイメージに `bash`・`awk`・`stat`・`date` があることも空回しで確かめた（Assumptions の3つ目）。
- 結果の置き場は `build/perf-results/u5-loop-dryrun/`（git の対象外。コミットしない）。

#### Step 13: `perf/README.md`

- DSL の節の `PERF_DB_URL` の行に、受け付ける形・止め方・試しの台本を書き足した。
- (b) 節の終わりに「`dslMixed` の `memory.peak` と `memory.stat` の記録（Intent 261016-business-data-views の U5）」を足した（合否・並べる値・列・1回の流れ・記録の置き場・後置きの欠け・設定の値の確かめ・環境の分け方・一時ファイルと秘密・手順 1〜6・秘密の検索・片付けと消えたことの確かめ）。
- 照らした名前: `k6d` のイメージ（`grafana/k6:2.3.0@sha256:9c2dee7f…`）・`ui.env`・`PERF_USER_PASSWORD`・`mastersmith-perf_perf-data`・`mastersmith-perf_default`・H2 の `Shell` の形・`valid-10mb.yaml`（`perf/dsl-timing.sh` 314 行）・`htpasswd`（この PC にある）。

#### Step 14: 1コマンドの検査

- `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の2つの変数を渡した）: BUILD SUCCESSFUL、17分38秒（12:34:39Z〜12:52:18Z）。
- 件数: バックエンドの単体 2,370 件（失敗 0）、結合 1,254 件（失敗 0・飛ばし 4）、画面 188 ファイル・1,633 件（失敗 0）。
- 飛ばした 4 件は `AbstractBusinessDataReadIT` の `assumeTrue`（DB の種類ごとの前提。PostgreSQL だけ・MySQL と MariaDB だけの確かめ）で、コンテナの実行環境の不足ではない（ログに「コンテナの実行環境」の警告は 0 件）。
- カバレッジ: バックエンドの全体 行 97.8%・分岐 91.8%、フロントエンドの全体 行 95.37%・分岐 90.39%（下限 80%・70%）。
- 眠り: 流す前に `pmset -g assertions` で `PreventUserIdleSystemSleep`・`PreventSystemSleep` が 1、AC 電源。流した後の `pmset -g log` の Sleep・DarkWake の最後は 2026-10-18 で、検査の窓には無かった。

#### Step 15: 変えていないことの確かめ

- 変えたファイル（`develop` との差と、追跡していない新しいファイル）は 1.1・1.2 の表のものと、計画のチェックボックス・監査の記録（`aidlc/`）だけ。
- `backend/src/main`・`frontend/src` のテストでないファイル・`application.yaml`・`Dockerfile`・`compose.yaml`・`docker/`・`.env.example`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/vitest.config.ts`・lockfile・`vendor/`・`.github/` の差は 0 件。
- 新しい7つのファイルの先頭に Apache License 2.0 のヘッダー（2026、agwlvssainokuni）があることを確かめた（Java は `/* ... */`、シェルは `#`）。

### 2. 計画に無い判断・計画との差

- **N-1 監査の記録**: フックが書き続ける監査の記録の追記はコミットしていない（指揮役が統合の前に積む）。
- **N-2 後の保存の知らせを作る時点**（Step 4・5）: 計画の流れは今の #7 と同じく、`BEFORE_LOCK` の知らせを先の保存を始める前に作る形だった。先の保存も `BEFORE_LOCK` を通るため、知らせの最初の通過は先の保存のスレッドになり、`second.passedThread()` が止める相手と同じになった（`armFor` が拒否）。知らせを `first.awaitArrival()` の後に作る形に替え、印で特定する形のまま進めた。あわせて、今までの #7 では `second.awaitPassed()` がすぐ戻り、後の保存が排他の直前の点（`BEFORE_LOCK`）を通ったことも確かめていなかったと分かった。今の形で確かめるのは、後の保存がその点を通ったことまでで、排他を待っていることまでは保証しない（このテストは監査の書き込みの順と中身の確かめで、排他の待ちが働いたことの確かめではない。コードのレビューの R-01 で書き方を直した）。
- **N-3 イメージの固定先を増やさない**（Step 11・13）: `.github/dependabot.yml` の「Dependabot が見ない固定先」の一覧に無いファイルに、イメージの版とダイジェストを書かないため、`perf/dsl-mixed-memory.sh` は k6 のイメージを `K6_IMAGE`（必須。`perf/README.md` の `k6d` と同じ値を README の手順で渡す）で受け、H2 の `Shell` は測るアプリのイメージ（`mastersmith:<タグ>`、同じ JRE）で `--entrypoint java` で動かす。計画の 4.4 の R-02 が挙げた形（README 38〜40 行の eclipse-temurin のイメージ）とは、イメージだけが違う。README の利用者を入れる関数 `perf_users` は既存の形（一覧にある `perf/README.md` の eclipse-temurin）のまま。
- **N-4 設定の値の読み方の先の確かめ**（計画に無い試し）: 一時の docker のボリューム2つだけで、(1) 接続先の URL の `RETENTION_TIME=0` で作った DB の `INFORMATION_SCHEMA.SETTINGS` が 0、URL に無いときは 45000（H2 の既定）になること、(2) ボリュームを `:ro` で付け `ACCESS_MODE_DATA=r` で開いて読めること、を `mastersmith:local` のイメージで確かめた。一時のボリュームは消した（残り 0 件）。配備したアプリのボリュームには触れていない。Assumptions の2つ目（URL で渡せる）の裏付けになるが、使い捨てのアプリで効いたことは Build and Test で読む。
- **N-5 `--loop-only`**: 空回しを台本と同じループのコードで行うため、記録のループだけを流す口を足した。
- **N-6 空回しの `anon` の確かめ方**: 1節の Step 11・12 のとおり、計画の形では約 0.2 MB の差が出たため、同じ写しからの比べを足して列の正しさを確かめた。
- **N-7 k6 の出力**: 試験用の利用者の名前を含むため記録に残さず、一時ディレクトリに置いてログインの行の数と重なりの数だけを `k6.txt` に書き、すぐ消す（NFR4.6）。
- **N-8 無効の回**: 始める前の確かめに外れた回は `invalid.txt` に書いて終了コード 3 で止まる（README の手順は `|| exit $?` で止まる）。
- **N-9 走らせ方**: `unit-test-instructions.md` 3.2 の「親が止まることの確かめ」のコマンドは、zsh では一致しない glob（`~/.mastersmith-perf-*`）が誤りになり、前の一覧のファイルが作られなかった。同じコマンドを bash の台本で流した。また、この担当の環境では `bash -c`・変数を埋めた `python3 -c` がフックで止められたため、scratchpad の固定の台本で流した。
- **N-10 試しの単独の流し方**: Step 7 の T1・T2 は `-t 'keeps the same function'` で対象の1件だけを流した（ファイルの残りの 11 件は skipped）。Step 8 の5回はファイルの12件すべてを流した。
- **N-11 traceability**: `OK` の target は道1つでなければならないため、計画の 7節の「OK（部分）」の書き分けは code-summary.md の 4節に書いた。FR16.1〜FR16.4 の行は置かず、BR3・BR4 を通して2段の連鎖でたどる（`project.md` の学び）。

### 3. Build and Test へ引き継ぐこと

- `dslMixed` の `memory.peak` の測り（既定の2回・試しの2回。`perf/README.md` の (b) 節の手順）と判定（BR3.4・BR3.5）。台本 `perf/dsl-mixed-memory.sh` の1回の測りの全体は、配備したアプリを止める必要があるため Code Generation では流していない。最初の回で記録のファイルが揃っているか（特に `settings.txt` の値、`k6.txt` の `post_missing_seconds`）を確かめてから続ける。
- 試しの2回の設定の値（`RETENTION_TIME` が 0）の確かめ（BR4.1）と、結果による諮り（BR4.2・BR4.3）。
- 秘密の検索の件数（NFR4.5・NFR4.6）と、片付けで一時ディレクトリとボリュームが消えたことの確かめ。
- 配備したアプリを止める時間を、U1・U2 の k6 とまとめられるかの並べ方。
- E2E の全件（Intent の最後）。
- B1 の統合の後の CI の試行ごとの結果と時間（今回の verify は 17分38秒）。

### 4. コードのレビュー（READY・Minor 4）の直し

レビューの記録は `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/code-generation/units/u5-carryover-tests/bb969d769be0cb2e/1.json`。依頼者は N-2〜N-4 と bash での実行を受け入れ、CI の1回目だけ落ちた2件（`PatternCheckerTest`・`PasswordChangePage.test.tsx`）を「手元で再現しないもの」として記録すると決めた。あわせて、統合の前に R-01〜R-03 を直し、R-04 は持ち越すと決めた。直しは4つのコミット（`1d55103`・`ba84196`・`31fdb0d`・`b0ee373`）の後の作業フォルダで行い、コミットはしていない。

- **R-01**: N-2 の「後の保存が排他の直前に来たことを保証する」は言い過ぎだった。`BEFORE_LOCK` の知らせが確かめるのは、後の保存がその点を通ったことまでで、排他を待っていることまでは保証しない。この記録の N-2、`code-summary.md` の 6節の 1、`RoleConcurrencyIT` の Javadoc をそう直した。
- **R-02**: `RoleConcurrencyIT` の #7 の自然の順の場合も、後の保存が 409 `ROLE_BUSY` で返ったら失敗にした。待ち合わせの結果（`heldArrived` 真・`releasedByTimeout` 偽）と「409 でないこと」を、入れ替えた順と同じ位置（結果を待った直後、ほかの確かめより前）に置き、409 の code だけを確かめて通す分岐は消した（D1: A を自然の順にも当てる。BR1.3 との差は依頼者の決定）。`--rerun` で5回流し、5回とも5件通過（1回 8.89〜9.08 秒、22:37:22〜22:38:20 JST）、`ROLE_BUSY` は 0 件。
- **R-03**: `perf/dsl-mixed-memory.sh` を直した。
  - 記録の TSV は完全な行だけを使う（改行で終わらない最後の行と、9 列・時刻と数か `NA` の形でない行を捨てる）。まとめに捨てた数 `dropped_rows` を出し、最後の完全な行の時刻が読めなければ `post_missing_seconds=NA` にして続ける。`memory.peak`・`memory.stat` はループを止めた直後に先に読む。
  - `trap` を EXIT・INT・TERM に置き、手元の `docker exec` と、コンテナの中の目印（台本の1回ごとに一意の引数）を持つ記録のループの処理だけを止める。確かめで、手元の `docker exec` を止めてもコンテナの中のループは残ると分かったため、コンテナの中の目印の処理も止める形にした（アプリの処理には触れない）。
  - 確かめのため `--tsv-check <TSV>`（完全な行の数・最後の完全な行の時刻・まとめ。Docker を使わない）を足した。`--loop-only` も同じ `start_loop`・`trap` を通る。
  - 一時ファイルの一覧と `KEEP` の決まりは変えていない。`perf/README.md` の (b) 節に、後置きの欠けの `NA`・切れた行・ループの後始末の3点を書き足した。
  - 確かめ: `bash -n` が通過。切れた行を含む TSV 5つの形（列の足りないまま切れた最後の行・9 列だが改行で終わらない最後の行・途中の切れた行・見出しだけ・完全）を一時の場所に作り、`--tsv-check` がどれも終了コード 0 で、切れた行を捨てた（完全な形では `dropped_rows=0`、まとめは直しの前と同じ値）。配備したアプリのコンテナに読むだけで上限 30 秒のループを起こし、3 秒後に TERM・INT を送ると、台本は 143・130 で終わり、コンテナの中（`docker top`）と手元（`pgrep`）にループは残らなかった。1回目の試しでは、TERM で手元は止まったがコンテナの中のループが残り、INT は試しの台本がジョブ制御なしの `&` で起こしたため届かなかった（bash は後ろの処理の SIGINT を無視したまま起こす。試しの台本に `set -m` を付けて流し直した）。普通に終わる5秒の空回しも5行・`NA` 0 件・残り無し。結果は `build/perf-results/u5-trap-check/` と `build/perf-results/u5-loop-dryrun/memory-after-r03.tsv`（git の対象外）。
- **R-04**: 持ち越し（手を付けていない）。
- **直しの後の verify**: `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` は BUILD SUCCESSFUL、17分44秒。単体 2,370 件・結合 1,254 件（既存の `assumeTrue` の飛ばし 4 件、コンテナの実行環境の警告 0 件）・画面 188 ファイル・1,633 件、失敗 0。カバレッジはバックエンド 行 97.8%・分岐 91.8%、フロントエンド 行 95.37%・分岐 90.32%。流す前の `pmset -g assertions` で `PreventUserIdleSystemSleep` が 1、流した後の `pmset -g log` に検査の窓の Sleep・DarkWake は無かった。
- `source-manifest.json` と `traceability.json` は、変えたパスが変わらないため直していない。

## CI の不安定なテスト2件の直し（B6 の統合の前）

`develop` の CI（run 39375104956、`1152202`）で、前に落ちたことのある2件が二度目に落ちた。team.md の「同じテストが二度目に落ちたら、原因を直すまで次へ進まない」に当たるため、依頼者の決定で B6 の統合の前に直した。作業のブランチは `fix/261016-business-data-views-ci-flaky`。直しはテストだけで、本番のコード（`src/main`）・`vendor/`・`.env` には触れていない。再現の試みの時間の上限は、それぞれ 1 時間とした（画面のテストは約 10 分、バックエンドは約 40 分で済んだ）。

### 1. `frontend/src/features/preferences/PasswordChangePage.test.tsx`「drops the answer when the screen is left while sending」

- **失敗**: 試行 1 で `expect(screen.getByTestId('toast-container')).toBeEmptyDOMElement()` が通らず、「パスワードを変更しました」の Toast が出ていた。前に `0427d45`（run 39172328548）の1回目でも同じテストが落ちている（「手元で再現しないもの」として記録済み）。
- **見立て**: `usePasswordChangeForm` は、部品が外れたかの印（`mounted`）を `useEffect` の後片付けで下ろす。プリファレンスの画面は遅延読み込み（`lazy`）で、画面の切り替えの commit は同期の更新ではないため、後片付けは commit の後のタスクで流れる。テストは新しい画面の DOM（`preferences-page`）が見えた時点で答えを返しており、後片付けより先に答えが届くと、印がまだ下りていないため Toast が出る。手元では後片付けのタスクが先に流れて通り、遅い CI では順が入れ替わって落ちる。テストの待ち合わせの抜け（「画面を離れた」を DOM だけで判定している）と見た。
- **再現（待ち合わせで確実に起こした）**: 使い捨てのテストのファイル（確かめの後に消した）で、新しい画面の DOM が置かれた直後（`MutationObserver` の知らせ、同じ commit の後のマイクロタスク）に答えを返す順を作った。5回とも、CI と同じ「パスワードを変更しました」の Toast が出て失敗した。
- **直した中身**: 新しい画面の DOM を待った後に、新しい画面の副作用が出す読み込みの要求（`GET /api/me/preferences`）が届くのを `waitFor` で待ってから答えを返す形にした。React は副作用を流すときに後片付けを先に流すため、要求が届いた時点で前の画面の印は下りている。時間の上限は変えていない。本番のコード（`usePasswordChangeForm`）は変えていない。
- **二段の確かめ**: 直す前の順（DOM の直後に答える）は上のとおり5回とも落ち、直した後の順（読み込みの要求を待ってから答える）は同じ使い捨てのファイルで通った。直した本物のファイルを単独で5回流し、5回とも 17 件通過。

### 2. `backend` の `H2CompactionByPoolSuspensionIT`「the H2 file grows while connections are open and shrinks after suspend, evict and resume via the pool MBean」

- **失敗**: 試行 2・3 で、一時停止と接続の破棄の後の待ちが `pool condition not met within 30000 ms (all connections closed after suspend and evict): total=1, active=0, idle=1, threadsAwaiting=0` になった（前の Intent で上限を 30 秒に延ばし、失敗のときの診断を足したテスト）。
- **見立て**: HikariCP 7.0.2 の補充（`PoolEntryCreator`、担い手は1本の `connection-adder` のスレッド）は、一時停止かを確かめた後に接続を作り、作り終えてから袋に足す。作っている途中で一時停止と接続の破棄をすると、破棄の後に新しい接続が1本だけ足され、その後の補充は一時停止のため止まる。足された1本は破棄の印を持たず空きのまま残るため、0 本にならない（`total=1, idle=1` と合う）。補充は、クラスの中で先に流れる2つのテスト（JUnit の並びは「借りた接続」→「一時停止の間の要求」→このテスト）の再開（`resumePool`）や起動の直後に始まり、最小の空きの数（既定で上限と同じ 30）まで1本ずつ作る。手元では補充がテストの投入と適用の間に終わり、遅い CI では一時停止の時点でまだ作っている途中だった。上限を延ばしても直らない、テストの待ち合わせの抜けと見た。
- **再現（待ち合わせで確実に起こした）**: 使い捨ての変更（確かめの後に戻した）で、`connection-init-sql` から呼ぶ H2 の関数（別のスキーマに置いた待ち合わせ）で `connection-adder` を接続を作る途中で止めた。補充を始めて止まったのを確かめてから、直す前の順（一時停止 → 破棄 → 0 本を待つ）で進め、止めていた作る途中の接続を進めると、CI と同じ `total=1, active=0, idle=1, threadsAwaiting=0` で 30 秒の待ちが切れた。あわせて、確実にしない試し（`connection-init-sql` の重い問い合わせで作る時間を延ばしただけ）でも、「借りた接続」のテストが同じ理由（`total=2, active=1, idle=1`）で落ちた。この試しでは後のテストが H2 の `CK_DSL_PREVIEWS_SOURCE` の制約の誤りでも落ちたが、試しの設定（すべての接続で重い問い合わせを流す）のときだけ出て、本物の設定と CI では出ていない（原因は確かめていない）。
- **直した中身**: 一時停止の前に、補充が済むのを待つ `awaitFillSettled()` を足し、一時停止の前に破棄の後の本数を数える2つのテスト（このテストと「借りた接続」のテスト）で、一時停止の直前に呼ぶ。条件は、全体が上限に届くか、空きが最小の空きの数に届くこと（どちらかになると、次の補充は作る前に止まり、作っている途中の接続が無い）。上限と最小の空きの数は、プールの設定の MBean（`HikariConfigMXBean`）から読む。待ちの上限は、ほかの待ちと同じ `ZERO_CONNECTIONS_WAIT`（30 秒）で、延ばしていない。運用の道具と同じ「一時停止 → 破棄 → 0 本を待つ → 再開」の順は変えていない。
- **二段の確かめ**: 上の待ち合わせのまま、直す前の順は CI と同じ失敗になり、直した後の順（作る途中の接続を 2 秒後に進め、`awaitFillSettled()` がそれを待ってから一時停止する）は `total=0` になって通った。使い捨ての変更を戻した後、直した本物のクラスを `--rerun` で単独で5回流し、5回とも 3 件通過（1回 11〜12 秒）。

### 3. 直しの後の verify

- `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）は BUILD SUCCESSFUL、21分35秒（18:39:46〜19:01:21 JST）。
- 件数: 単体 2,584 件、結合 1,386 件（飛ばしは既存の DB の種類ごとの `assumeTrue` の 9 件、コンテナの実行環境の警告 0 件）、画面 217 ファイル・1,821 件、失敗 0。
- カバレッジ: バックエンド 行 97.64%・分岐 91.23%、フロントエンド 行 95.22%・分岐 90.30%。
- 流す前の `pmset -g assertions` で `PreventUserIdleSystemSleep` が 1、流した後の `pmset -g log` の検査の窓には Assertions の行だけで、Sleep・DarkWake は無かった。
- 直しはテストだけのため、team.md の Way of Working のとおり E2E は流していない（Intent の最後の Build and Test で流す）。

### 4. 依頼者に確かめたいこと

- 運用の道具 `docker/hikari-pool.sh compact`（`docker/jmx/HikariPoolControl.java`）も、一時停止 → 破棄を1回だけ行うため、補充の途中（起動の直後・接続の寿命の 30 分ごとの入れ替えの直後など）に流すと、同じ理由で 0 本にならず、`--zero-wait`（10 秒）で詰め直さずに再開して 1 で終わりうる。害は無く（再開は済む）、流し直せばよいが、道具の側で「0 本になるまで破棄を繰り返す」か「補充が済むのを待ってから一時停止する」形にするかは、今回は触れていない（テストだけの直しの範囲の外）。
- `usePasswordChangeForm`・`usePreferencesForm` は、外れたかの印を `useEffect` の後片付けで下ろすため、本物のブラウザでも、画面の切り替えの commit と後片付けの間（ごく短い間）に答えが届くと Toast が出うる（出る文言は事実のとおり）。直すなら `useLayoutEffect` などに替える本番のコードの変更になるため、今回は触れていない。
