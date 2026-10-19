# 単体テストの手順 — U4 u4-a11y-carryover（Bolt B3）

この単位（B3）が足す・変える画面のテストと E2E の補助のテストの走らせ方を書く。Testing Contract は test-after、Test Strategy は standard（部品ごとに 5〜8 件、要所の結合の確かめ）。計画は `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/code-generation/code-generation-plan.md`。

この文書のコマンドは、どれもこの単位のファイルだけを指定して流す。全体の検査（`./gradlew verify`）と E2E の全件（`./gradlew e2eTest`）は、計画の Step 15・17 で統合の前の関門として流すもので、この文書の対象ではない。

## 1. 道具と設定

| 道具 | 版（`frontend/package.json`） | 使い方 |
|---|---|---|
| Vitest | `^5.0.4` | 画面のテストと E2E の補助の単体のテストを流す。設定は `frontend/vitest.config.ts`（jsdom・`vitest.setup.ts`） |
| Testing Library・user-event | `@testing-library/react ^16.3.3`・`@testing-library/user-event ^14.6.7` | 部品を描き、利用者の操作をまねる。名前は `getByRole(..., { name })` で確かめる（U4-NFR5.5） |
| vitest-axe | `^0.1.0` | 画面の部品ごとのアクセシビリティの検査（既存のまま。jsdom では `label-content-name-mismatch` を判定しない） |
| fast-check | `^4.10.2` | `rowActionsName` の性質ベースのテスト（U4-NFR7.4） |
| `@vitest/coverage-v8` | `^5.0.4` | カバレッジ。下限は全体の合計で行 80%・分岐 70%（変えない） |
| Playwright | `@playwright/test ^1.63.0` | E2E（この単位では書き換えだけ。3.3） |

設定の変更（計画の Step 11、A3）: `frontend/vitest.config.ts` の `include` を `['src/**/*.test.{ts,tsx}', 'e2e/support/**/*.test.ts']`、`exclude` を `['e2e/**/*.e2e.ts', 'node_modules/**', 'dist/**']` にする。カバレッジの `include`（`src/**/*.{ts,tsx}`）・`exclude`・`thresholds` は変えない。`e2e/support/` のテストは、この変更の後にだけ走る（Step 12 より前に Step 11 で変える）。

## 2. 走らせる前の準備

- 作業はリポジトリのルート（`mastersmith2/`）から始める。Node.js は 24 系（`frontend/package.json` の `engines`）。
- make-you-chic-ui は `file:` の依存で、ビルドした結果を読む。固定先を変えた後（Step 3）と、初めて流すときは、次を1回流す。

```bash
./gradlew vendorInstall vendorBuild frontendInstall
```

- `frontend/.npmrc` の `ignore-scripts=true` のまま入れる。`.env` は使わない（画面のテストは API を差し替える）。
- テストの値は架空の値だけにする（メールアドレスは `example.com`）。テストの説明文（`describe`・`it`）は英語。

## 3. この単位のテストの走らせ方

どのコマンドも `frontend/` で流す。`NODE_OPTIONS=--no-experimental-webstorage` は `package.json` の `test` と同じ指定（Node.js 24 の Web Storage の実験の機能を止め、jsdom の `localStorage` を使う）。

### 3.1 走らせ方の確かめ（計画の Step 1。最初のテストの Step の前）

既存の4つのテストのファイルが、変更の前に通ることを確かめ、件数を記録する（brownfield）。

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin/UserRowActions.test.tsx \
  src/features/groupadmin/GroupRowActions.test.tsx \
  src/features/roleadmin/RoleRowActions.test.tsx \
  src/app/layout/ShellLayout.test.tsx
```

### 3.2 層ごとのコマンド

**固定先の後（Step 4。書き換えずに通ること。D7・U4-NFR7.6）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/work-role/WorkRoleSwitcher.test.tsx \
  src/features/useradmin/UserRowActions.test.tsx \
  src/features/useradmin/UserSearchBox.test.tsx \
  src/features/useradmin/EditProfileDialog.test.tsx \
  src/features/groupadmin/CandidateDialog.test.tsx \
  src/features/roleadmin/PermissionPanel.test.tsx
```

`RegistrationForm`・`PasswordChangeForm`・`PasswordChangePage`・`PreferencesForm` のテスト（Dropdown の項目を対象にしない `toHaveAccessibleDescription`）は、計画の Step 15 の verify で一緒に確かめる。

**L1 名前の関数（Step 6）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/shared/row-actions/rowActionsName.test.ts \
  src/shared/row-actions/rowActionsName.property.test.ts
```

**行の操作の部品とページ（Step 8）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/useradmin/UserRowActions.test.tsx \
  src/features/useradmin/UserAdminPage.test.tsx \
  src/features/groupadmin/GroupRowActions.test.tsx \
  src/features/groupadmin/GroupListPage.test.tsx \
  src/features/groupadmin/GroupAdminEnglish.test.tsx \
  src/features/groupadmin/GroupAdminForbidden.test.tsx \
  src/features/roleadmin/RoleRowActions.test.tsx \
  src/features/roleadmin/RoleListPage.test.tsx \
  src/features/roleadmin/RoleAdminEnglish.test.tsx \
  src/features/roleadmin/RoleAdminForbidden.test.tsx
```

**トップバー（Step 10）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/layout/ShellLayout.test.tsx
```

**E2E の補助（Step 12。Step 11 の設定の変更の後）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  e2e/support/axeElementKey.test.ts \
  e2e/support/axeRuleChecks.test.ts
```

### 3.3 この単位のテストをまとめて流す（Build and Test でも使う）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/shared/row-actions/rowActionsName.test.ts \
  src/shared/row-actions/rowActionsName.property.test.ts \
  src/features/useradmin/UserRowActions.test.tsx \
  src/features/useradmin/UserAdminPage.test.tsx \
  src/features/groupadmin/GroupRowActions.test.tsx \
  src/features/groupadmin/GroupListPage.test.tsx \
  src/features/groupadmin/GroupAdminEnglish.test.tsx \
  src/features/groupadmin/GroupAdminForbidden.test.tsx \
  src/features/roleadmin/RoleRowActions.test.tsx \
  src/features/roleadmin/RoleListPage.test.tsx \
  src/features/roleadmin/RoleAdminEnglish.test.tsx \
  src/features/roleadmin/RoleAdminForbidden.test.tsx \
  src/app/layout/ShellLayout.test.tsx \
  src/app/work-role/WorkRoleSwitcher.test.tsx \
  e2e/support/axeElementKey.test.ts \
  e2e/support/axeRuleChecks.test.ts
```

4.14 の試し（計画の Step 18〜20）の一時の差分（`expect.soft`。計画の 10節の D4: A）は作業フォルダの中だけで当て、この文書のコマンドで流す状態には残さない。

この単位が書き換える E2E のファイルだけを流すときは、WAR を作ってから Playwright にファイルを指定する（Mailpit を起動しておく。README の E2E の節）。統合の前の関門は E2E の全件（計画の Step 17）で、このコマンドはその代わりにしない。

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && caffeinate -dims npx playwright test \
  e2e/110-user-admin-flow.e2e.ts \
  e2e/130-admin-forbidden-accessibility.e2e.ts \
  e2e/135-dsl-admin-accessibility.e2e.ts \
  e2e/140-role-admin-accessibility.e2e.ts \
  e2e/160-app-frame-accessibility.e2e.ts)
```

## 4. 部品ごとのテストと件数の目安

### 4.1 画面のテスト

| 部品・ファイル | 件数の目安 | 確かめること |
|---|---|---|
| `rowActionsName.test.ts`（新） | 6 | 日本語「操作: 佐藤 一郎（sato@example.com）」、英語「Actions: 営業」、空の値は見える文字だけ、空白だけの値も見える文字だけ、前後の空白の切り落とし、`<`・`"`・`'`・改行を含む値が文字のまま |
| `rowActionsName.property.test.ts`（新） | 4 つの性質 | 4.2 |
| `UserRowActions.test.tsx`（書き換え・足し） | 8 | 「操作: <氏名>（<宛先>）」で始まる、処理中の名前、英語の名前と ` (processing)` の先頭の空白、`data-testid`・見える文字・`placement` が変わらない、押せない項目の説明（`toHaveAccessibleDescription`、書き換えない）、`<` を含む氏名、vitest-axe |
| `GroupRowActions.test.tsx`（書き換え・足し） | 7 | 「操作: <名前>」、処理中、英語、長い名前、「〇〇を開く」のリンクの名前が変わらない（D12）、`data-testid`、vitest-axe |
| `RoleRowActions.test.tsx`（書き換え・足し） | 7 | グループと同じ |
| ページのテスト（`UserAdminPage`・`GroupListPage`・`GroupAdminEnglish`・`GroupAdminForbidden`・`RoleListPage`・`RoleAdminEnglish`・`RoleAdminForbidden`） | 書き換え＋各ページ1件 | 名前の照合の書き換え。3つのページで、名前を作っても初めの描画の一覧の API の呼び出しが1回のままであること（U4-NFR7.1） |
| `ShellLayout.test.tsx`（書き換え・足し） | 5 | 1語の氏名「管: 管理者のメニュー」と英語「管: Menu for 管理者」、2語の氏名「検太: 検査 太郎のメニュー」と英語「KT: Menu for Kensa Taro」、頭文字が Avatar の丸の文字と同じ、空白だけの氏名で「: 」で始まらない（U4-NFR6.2）、業務のメニューの API の呼び出しの回数が変わらない（U4-NFR7.1） |
| 固定先の後の既存のテスト（`WorkRoleSwitcher.test.tsx` ほか） | 書き換えない | 項目の説明が `toHaveAccessibleDescription` で今までどおり確かめられる（D7・U4-NFR7.6） |

描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず `waitFor` で待つ。テストの時間の上限（Vitest の既定 5 秒）は、原因を確かめずに延ばさない（team.md）。

### 4.2 性質ベースのテスト（fast-check）

| 性質 | 生成器 |
|---|---|
| 結果が `visibleLabel` で始まる | 空でない `visibleLabel`（`": "` を含まない）、任意の `rowLabel` |
| 空でない `rowLabel` なら、結果が `rowLabel.trim()` を含む | 空白だけでない `rowLabel` |
| 空白だけの `rowLabel` なら、結果が `visibleLabel` と等しい | 空白の文字（空白・タブ・改行）だけの文字列 |
| 区切り `": "` がちょうど1つ | `": "` を含まない `visibleLabel` と `rowLabel` |

fast-check は、失敗したときに seed と path を出力に示す。再現はその値を `fc.assert` の第2引数に一時的に書いて行い、seed を記録に残す（team.md。既存の `frontend/src/app/navigation/buildNavSections.property.test.ts` と同じ形）。

### 4.3 E2E の補助の単体のテスト

| ファイル | 件数の目安 | 確かめること |
|---|---|---|
| `axeElementKey.test.ts`（新、jsdom） | 8 | 行の操作のトリガー3種類・作業ロールのトリガー・トップバーのトリガー・portal の中の作業ロールの項目の鍵が区別できる。サイドバーの枝（`aria-expanded` だけを持つボタン）を開き作業ロールのメニューも開いた場面で、項目の menuOwner が作業ロールのトリガーになり誤りにならない（NFR 設計のレビューの R-01）。`aria-haspopup` と `aria-expanded="true"` を持つトリガーが2つ開いていると誤りの印になる。`work-role-switcher`・`sidebar-toggle` は残り、`useradmin-row-actions-12`・`tables-row-actions-r3` は `-*` になる（R-03）。`data-testid` の無い要素に `path` が付く（R-04）。`aria-label` や文字の氏名・メールアドレスが鍵に出ない（U4-NFR4.5）。`axeElementKey.toString()` を評価し直した関数も同じ鍵を返す（`page.evaluate` で動く形） |
| `axeRuleChecks.test.ts`（新） | 8 | 区分の要素が passes にだけあれば問題 0 件。incomplete に1件で問題（incomplete を合格に数えない）。violations に1件で問題。区分の要素が 0 件なら「確かめの作りの誤り」の問題。C1〜C7 の当てはめ。違反の文と、いつも残す注記 `axe-nodes` の中身（`axeNodesAnnotation`。計画の 10節の D5: A）に、選択子と値が出ず、規則・鍵・件数だけが入る。L6 の記録の行の形。`splitKnownViolations` は空の `KNOWN_VIOLATIONS` で、すべての違反を失敗に分ける |

DOM の見本は、jsdom の `document` に要素を組んで作る（実際の make-you-chic-ui の DOM の形をまねる。Dropdown のトリガーは `data-testid="dropdown-trigger"`・`aria-haspopup`・`aria-expanded`、メニューは `document.body` の直下の `data-testid="dropdown-menu"`、トップバーのトリガーは `button.mycui-topbar-user-trigger`）。

## 5. カバレッジの目標

- 下限は全体の合計で行 80%・分岐 70%（`frontend/vitest.config.ts` の `thresholds`）。この単位で下げない・除外を増やさない（U4-NFR7.7）。判定は計画の Step 15 の `./gradlew verify` で行う。
- 新しいファイル `frontend/src/shared/row-actions/rowActionsName.ts` は、行・分岐とも 100% を目安にする。
- `e2e/support/` のファイルはカバレッジの対象（`src/**`）に入らない。カバレッジの報告に `e2e/` のファイルが出ないことを記録する（基盤の設計のレビューの R-03）。
- この単位のファイルだけのカバレッジを見たいときは、次で記録だけに使う（下限の判定は全体で行うため、`thresholds` の結果は合否に使わない）。

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run --coverage \
  --coverage.include='src/shared/row-actions/**' \
  src/shared/row-actions/rowActionsName.test.ts \
  src/shared/row-actions/rowActionsName.property.test.ts
```

## 6. モックと偽物の使い方

- API は既存の機能ごとの手伝い（`frontend/src/features/*/testing/`）と、ページのテストの差し替えの口を使う。呼び出しの回数は既存の形（`api.listGroups.mock.calls`・`toHaveBeenCalledTimes`）で確かめる。
- 文言は既存の i18n の仕組み（日英の `messages.ts`・`frontend/src/app/i18n/messages/`）をそのまま使い、文言を差し替えない。英語は既存の英語のテストの形（`languages: ['en-US']` など）で切り替える。
- make-you-chic-ui の部品（Dropdown・Button・AppShell・Avatar）と `getInitials` はモックにしない（部品の振る舞いをこのリポジトリのテストで確かめるため。team.md）。
- ログの関数をモックにして「呼ばれない」ことを確かめる形は補助にとどめ、ログ・`console`・トレースの呼び出しが無いことはコードの検索で確かめる（計画の Step 14、U4-NFR4.2）。
- E2E の補助の単体のテストでは、ブラウザも axe-core も使わない。鍵の関数は jsdom の要素で、確かめの部品は手で組んだ要約（`AxeSummary` の形）で確かめる。

## 7. テストのデータ

- 氏名は架空の値（「佐藤 一郎」「検査 太郎」「Kensa Taro」など）、メールアドレスは `example.com` だけ。実在しそうな宛先を置かない（team.md）。
- 名前の文字のまま返ることの確かめには、`<script>`・`<b>`・`&`・`"`・`'`・改行を含む値を使う。
- 頭文字の確かめには、1語の氏名（頭文字が1文字）と2語の氏名（頭文字が2文字）と空白だけの氏名を使う。
- E2E の見本の値（`frontend/e2e/support/userAdminFixtures.ts` の行と 130 の `Probe Sample`）は、報告の秘密の確かめの値に足す（計画の Step 13）。この値を注記・添付・標準出力・テストの題に出さない。

## Sources

- 計画: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/code-generation/code-generation-plan.md`
- 設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/frontend-components.md`（2節・5節・7節）、`nfr-requirements/security-requirements.md`（U4-NFR5.5・6.1・6.2・7.1・7.4〜7.7）、`nfr-design/security-design.md`（1節・2節）、`infrastructure-design/cicd-pipeline.md`（3節）
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u4-a11y-carryover/df13e747868fc8fa/1.json`（R-01・R-03・R-04）、`aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/infrastructure-design/units/u4-a11y-carryover/4bcccff4724084fb/1.json`（R-03・R-04）
- コード: `frontend/package.json`（`scripts.test`・依存の版）、`frontend/vitest.config.ts`、`frontend/tsconfig.json`、`build.gradle.kts`（`vendorInstall`・`vendorBuild`・`frontendInstall`・`frontendTest`・`e2eTest`）、`frontend/src/app/navigation/buildNavSections.property.test.ts`、`README.md`（E2E の節）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（学び）

## Assumptions & Open Questions

- [assumption] `e2e/support/*.test.ts` は jsdom の実行環境と `vitest.setup.ts` のまま流せる（鍵の関数は DOM だけを読み、確かめの部品は Node の部品を読まない）。流せなければ、そのファイルの先頭で実行環境を指定する（基盤の設計の Assumptions）。
- [assumption] `npx vitest run <ファイル>` は、`vitest.config.ts` の `include` に当たるファイルだけを流す。Step 11 の前は `e2e/support/` のテストを指定しても流れない。
