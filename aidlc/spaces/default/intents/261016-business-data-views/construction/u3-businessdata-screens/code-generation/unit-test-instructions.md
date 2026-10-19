# 単体テストの手順 — U3 u3-businessdata-screens（Bolt B5・B6・B7）

この単位が足す・変える画面のテスト、E2E の補助の単体のテスト、E2E のファイルの走らせ方を書く。Testing Contract は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）、Test Strategy は standard（部品ごとに 5〜8 件、要所の結合の確かめ）。計画は `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/code-generation/code-generation-plan.md`（Step の番号はこの計画のもの）。

この文書のコマンドは、どれもこの単位のファイルだけを指定して流す。全体の検査（`./gradlew verify`）と E2E の全件（`./gradlew e2eTest`）は、計画の Step 15・16・35・36・48・49 で統合の前の関門として流すもので、この文書の対象ではない（Build and Test は 3.4 のまとめのコマンドを使う）。

## 1. 道具と設定

| 道具 | 版（`frontend/package.json`） | 使い方 |
|---|---|---|
| Vitest | `^5.0.4` | 画面のテストと E2E の補助の単体のテスト。設定は `frontend/vitest.config.ts`（jsdom・`vitest.setup.ts`。対象は `src/**/*.test.{ts,tsx}` と `e2e/support/**/*.test.ts`） |
| Testing Library・user-event | `@testing-library/react`・`@testing-library/user-event` | 部品を描き、利用者の操作をまねる。名前は `getByRole(..., { name })` で確かめる |
| vitest-axe | `^0.1.0` | 部品ごとにアクセシビリティの検査を1件（jsdom では `label-content-name-mismatch` を判定しない。B7 で axe-core を上げても jsdom の結果は変わらない見込み） |
| fast-check | `^4.10.2` | 性質ベースのテスト（書式・行のラベル・変更の比べ・本文の写し・写しの関数・書き込みの結果の分け方）。失敗のときの乱数の種（`seed`・`path`）を記録する |
| `@vitest/coverage-v8` | — | カバレッジ。下限は全体の合計で行 80%・分岐 70%（変えない） |
| Playwright | `@playwright/test ^1.63.0` | E2E（160・170 の書き換え、180・185、040 の書き換え、B7 の passes の確かめ） |
| react-router の data router | 今の版 | 未保存の確かめのテストは `createMemoryRouter` の実物で流す（計画 4.3 の R-08） |

設定の変更は無い（`vitest.config.ts`・カバレッジの対象と除外・下限は変えない）。D10 が A のとき、lint の設定（`.oxlintrc.json` か `eslint.config.js`）に `no-console` を足すだけ。

## 2. 走らせる前の準備

- 作業はリポジトリのルート（`mastersmith2/`）から始める。Node.js は 24 系（`frontend/package.json` の `engines`）。
- make-you-chic-ui は `file:` の依存で、ビルドした結果を読む。初めて流すときと、B7 で固定先を上げた後は、次を1回流す。

```bash
./gradlew vendorInstall vendorBuild frontendInstall
```

- `frontend/.npmrc` の `ignore-scripts=true` のまま入れる。画面のテストは API を差し替えるため、`.env` も見本の PG も使わない。
- テストの値は架空の値だけにする（メールアドレスは `example.com`）。テストの説明文（`describe`・`it`）は英語。主キー・行の値の見本は、`/`・`%`・`?`・`#`・空白・引用符・`<script>` を含む架空の文字にする。
- 描画の後に反映される値は `waitFor` で待って確かめる。テストの時間の上限は、原因を確かめずに延ばさない。

## 3. この単位のテストの走らせ方

どのコマンドも `frontend/` で流す。`NODE_OPTIONS=--no-experimental-webstorage` は `package.json` の `test` と同じ指定。ファイルの名前は計画の仮の名前で、生成で変えたときは `generation-notes.md` に書き、このコマンドも同じ名前に読み替える。

### 3.1 走らせ方の確かめ（各 Bolt の最初の Step。最初のテストの Step の前）

**B5（Step 1）**: 書き換える既存のテストが、変更の前に通ることを確かめ、件数を記録する（brownfield）。

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/TablePlaceholderPage.test.tsx \
  src/features/tables/tableAccessApi.test.ts \
  src/features/tables/registration.test.ts \
  src/app/navigation/tablePlaceholderPath.test.ts \
  src/app/navigation/buildNavSections.test.ts \
  src/app/navigation/buildNavSections.property.test.ts \
  src/app/login-state/LoginStateGate.test.tsx \
  src/app/layout/ShellLayout.test.tsx
```

**B6（Step 21）**: 移す roleadmin のテスト・作業ロールのテスト・B5 で足した業務データのテストが通ることを確かめる。

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/roleadmin/useUnsavedGuard.test.tsx \
  src/features/roleadmin/LeaveConfirmDialog.test.tsx \
  src/features/roleadmin/PermissionPanel.test.tsx \
  src/app/work-role/WorkRoleProvider.test.tsx \
  src/app/work-role/WorkRoleSwitcher.test.tsx \
  src/app/navigation/BusinessNavigationProvider.test.tsx \
  src/features/tables
```

**B7（Step 41）**: axe の補助の単体のテストと、置き換えるかもしれない共通の部品のテスト。

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  e2e/support/axeRuleChecks.test.ts \
  e2e/support/axeElementKey.test.ts \
  src/shared/scroll-region \
  src/shared/page-size \
  src/features/tables/BusinessDataTable.test.tsx
```

### 3.2 層ごとのコマンド（B5）

**型と写しの関数（Step 3）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/businessDataParse.test.ts \
  src/features/tables/businessDataParse.property.test.ts
```

**API・記憶・知らせ（Step 5）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/businessDataApi.test.ts \
  src/features/tables/businessDataStore.test.ts \
  src/app/login-state/loginScope.test.ts \
  src/app/login-state/LoginStateGate.test.tsx
```

**純粋な関数（Step 7）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/valueFormat.test.ts \
  src/features/tables/valueFormat.property.test.ts \
  src/features/tables/rowLabel.test.ts \
  src/features/tables/rowLabel.property.test.ts \
  src/features/tables/listPaging.test.ts \
  src/features/tables/problemMessage.test.ts
```

**骨組みと共通の部品（Step 9）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/app/navigation/tablePlaceholderPath.test.ts \
  src/app/navigation/buildNavSections.test.ts \
  src/app/navigation/buildNavSections.property.test.ts \
  src/app/layout/ShellLayout.test.tsx \
  src/shared/scroll-region/ScrollRegion.test.tsx \
  src/shared/page-size/PageSizeSelect.test.tsx \
  src/shared/null-value/NullValue.test.tsx
```

**フック（Step 10）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/useTableDefinition.test.tsx \
  src/features/tables/useRowSearch.test.tsx \
  src/features/tables/useRowDetail.test.tsx
```

**画面と部品（Step 12）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/BusinessDataListPage.test.tsx \
  src/features/tables/SearchPanel.test.tsx \
  src/features/tables/BusinessDataTable.test.tsx \
  src/features/tables/RowActionsMenu.test.tsx \
  src/features/tables/RestrictionNotice.test.tsx \
  src/features/tables/BusinessDataDetailPage.test.tsx \
  src/features/tables/BusinessDataProblemView.test.tsx \
  src/features/tables/registration.test.ts \
  src/features/tables/messages.test.ts
```

**E2E の補助の単体のテスト（Step 13）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  e2e/support/businessDataFixtures.test.ts \
  e2e/support/businessDataApiRoute.test.ts \
  e2e/support/businessDataMeasure.test.ts \
  e2e/support/secretValues.test.ts
```

**E2E のファイル（Step 13・14。B5 の間は `-Pe2e.files` がまだ無い）**

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
cd frontend
npx playwright test e2e/185-business-data-accessibility.e2e.ts -g measure   # Step 13 の測りの先行
npx playwright test e2e/160-app-frame-accessibility.e2e.ts e2e/170-menu-navigation-flow.e2e.ts e2e/185-business-data-accessibility.e2e.ts
```

### 3.3 層ごとのコマンド（B6・B7）

**書き込みの写し・API・結果の分け方（Step 27）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/businessDataParse.test.ts \
  src/features/tables/businessDataApi.test.ts \
  src/features/tables/writeOutcome.test.ts \
  src/features/tables/writeOutcome.property.test.ts
```

**純粋な関数（Step 29）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/formDiff.test.ts \
  src/features/tables/formDiff.property.test.ts \
  src/features/tables/formValidation.test.ts \
  src/features/tables/formValidation.property.test.ts \
  src/features/tables/problemMessage.test.ts
```

**共通の部品と骨組みの口（Step 31）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/shared/unsaved-guard/useUnsavedGuard.test.tsx \
  src/shared/unsaved-guard/LeaveConfirmDialog.test.tsx \
  src/features/roleadmin/PermissionPanel.test.tsx \
  src/app/work-role/WorkRoleProvider.test.tsx \
  src/app/work-role/WorkRoleSwitcher.test.tsx \
  src/app/navigation/BusinessNavigationProvider.test.tsx
```

**書き込みのフックと画面（Step 33）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables/useRowWrite.test.tsx \
  src/features/tables/BusinessDataFormPage.test.tsx \
  src/features/tables/DeleteConfirmModal.test.tsx \
  src/features/tables/RowActionsMenu.test.tsx \
  src/features/tables/BusinessDataDetailPage.test.tsx \
  src/features/tables/registration.test.ts \
  src/features/tables/messages.test.ts
```

**E2E のファイル（Step 24・25・34。B6 で `-Pe2e.files` と見本の PG の前提が入った後）**

B6 の Step 24 の後は、`npx playwright test` を直接流すと、対象DB の環境変数が無いため設定の読み込みで止まる（Q7: A）。ファイルを選ぶときは `-Pe2e.files` を使う。見本の PG は上乗せで起動しておき、流し終えたら上乗せ無しで作り直してポートを閉じる。

```bash
docker compose --profile mail up -d mailpit
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml \
  --profile targetdb-postgres up -d targetdb-postgres
./gradlew e2eTest -Pe2e.files=e2e/040-dsl-admin.e2e.ts
./gradlew e2eTest -Pe2e.files=e2e/180-business-data-flow.e2e.ts
./gradlew e2eTest -Pe2e.files=e2e/185-business-data-accessibility.e2e.ts
# 終わったら閉じる（上乗せ無しで作り直す）
docker compose --profile targetdb-postgres up -d targetdb-postgres
```

**B7（Step 44・46・47）**

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  e2e/support/axeRuleChecks.test.ts \
  e2e/support/axeElementKey.test.ts \
  src/features/tables/BusinessDataTable.test.tsx \
  src/features/tables/BusinessDataListPage.test.tsx
cd ..
./gradlew e2eTest -Pe2e.files=e2e/130-admin-forbidden-accessibility.e2e.ts
```

passes の確かめを足した既存のファイル（D6 の答えで決まる 110・140・160 など）も、同じ形で `-Pe2e.files` に並べて流す（`-Pe2e.files` はカンマで区切って複数を受ける形にする。生成で決めた形をここに書き足す）。

### 3.4 この単位のテストをまとめて流す（Build and Test でも使う）

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npx vitest run \
  src/features/tables \
  src/shared/scroll-region src/shared/page-size src/shared/null-value src/shared/unsaved-guard \
  src/app/navigation/tablePlaceholderPath.test.ts \
  src/app/navigation/buildNavSections.test.ts \
  src/app/navigation/buildNavSections.property.test.ts \
  src/app/login-state \
  src/app/work-role \
  src/features/roleadmin/PermissionPanel.test.tsx \
  e2e/support/businessDataFixtures.test.ts \
  e2e/support/businessDataApiRoute.test.ts \
  e2e/support/businessDataMeasure.test.ts \
  e2e/support/secretValues.test.ts
cd ..
./gradlew e2eTest -Pe2e.files=e2e/040-dsl-admin.e2e.ts,e2e/160-app-frame-accessibility.e2e.ts,e2e/170-menu-navigation-flow.e2e.ts,e2e/180-business-data-flow.e2e.ts,e2e/185-business-data-accessibility.e2e.ts
```

B7 で `src/shared/scroll-region`・`page-size` を消したときは、そのパスを外す。

## 4. 部品ごとのテストと件数の目安

### 4.1 B5

| テストのファイル | 件数の目安 | 確かめること |
|---|---|---|
| `businessDataParse.test.ts`・`.property.test.ts` | 8＋性質 3 | 正しい形、項目の欠け、知らない列挙・`lookup`、`schema`・`table` の食い違い、`page`・`total` の範囲、`key` の null、400 の `definitionChanged`、任意の JSON で例外を投げない、桁を失わない |
| `businessDataApi.test.ts`（読み取り） | 6 | 本文の形、65,536 バイトちょうどは送る・+1 は送らない（サロゲートの組・組になっていないサロゲート）、失敗は ApiError のまま、自動で送り直さない |
| `businessDataStore.test.ts` | 8 | 101 件目で最古の印が捨てられる、読んだ後も残る、101 テーブル目、3つの消す時と roleId の変化（null と数の間を含む）で消える、消した後の遅れた書き戻しが捨てられる、ブラウザの保存を使わない |
| `loginScope.test.ts`・`LoginStateGate.test.tsx`（足し） | 3＋2 | 切り替わりのたびに鳴る、購読を外せる、ログイン⇄未ログインの両方で鳴る |
| `valueFormat.test.ts`・`.property.test.ts` | 8＋性質 2 | D15 の各書式（日英）、時差の `+00:00`、`masked`・BINARY・NULL・空の文字列、文字のまま返る |
| `rowLabel.test.ts`・`.property.test.ts` | 6＋性質 2 | 書式の後の文字、空白だけなら「（空）」、最初のカラムが列に無いときリンクを出さない、見える文字がリンクの名前と行の操作の名前に含まれる（先頭のカラムが空の文字列の入力を含む） |
| `listPaging.test.ts` | 5 | 20・50・100、表示の範囲、ページを超えたとき、件数を変えたら1ページ目 |
| `problemMessage.test.ts`（読み取り） | 8 | code ごとの鍵、知らない code、`needsDefinitionReload`（`definitionChanged`・今の定義に無い `field`・今の定義の項目だけ・`field` が空の文字） |
| `tablePlaceholderPath.test.ts`・`buildNavSections*.test.ts`（書き換え） | 今の件数＋4 | 4つの道、`isTablePath`、詳細・作成・更新でもテーブルの項目が今の項目、エンコード |
| `ScrollRegion`・`PageSizeSelect`・`NullValue` の各テスト | 5〜6 ずつ | 役割・名前・フォーカス、選んだ件数の通知、淡い文字と読み上げ、vitest-axe 1件ずつ |
| `useTableDefinition`・`useRowSearch`・`useRowDetail` の各テスト | 5〜8 ずつ | 要求の数、世代、消去の後の書き戻し、400 の読み直し、403 の後に送り直さない、印が無ければ `missing` |
| `BusinessDataListPage.test.tsx` | 8 | 状態の出し分け、`aria-live`・`aria-busy`、403 の表示、ブラウザの戻る操作で状態を保つ、再読み込みで既定、vitest-axe |
| `SearchPanel.test.tsx` | 6 | 開閉の `aria-expanded`、`fieldset`・`legend`、誤りの結び付け、`autocomplete="off"`、範囲の向き、vitest-axe |
| `BusinessDataTable.test.tsx` | 8 | 列の順と幅、`getRowId` がページの中の番号、`data-testid` と `id` に行の値が無い、値が文字として出る、切った印と全体の文字、並べ替えのボタン、`ScrollRegion` の名前、vitest-axe |
| `RowActionsMenu.test.tsx` | 5 | 名前が `rowActionsName` の形、`placement="bottom-end"`、`data-testid` が `tables-row-actions-r<n>`、`operations` に無い項目が出ない、vitest-axe |
| `RestrictionNotice.test.tsx`・`BusinessDataProblemView.test.tsx` | 5 ずつ | 理由ごとの文、書き込みの設定が無い旨、6.1 の行き先、作業ロールの切り替えの入口、vitest-axe |
| `BusinessDataDetailPage.test.tsx` | 7 | `dl`、h1 へのフォーカス、切った値の文、主キーが道・履歴の状態に無く記憶で往復できる、`document.title` に値が無い、404・409 の文、vitest-axe |
| `registration.test.ts`・`messages.test.ts` | 3＋2 | 3つの `lazy`、道と「ログインだけ」、日英の鍵がそろう |
| `e2e/support` の4つ | 3〜5 ずつ | 見本が写しの関数を通る、道とメソッドの照らし、測りの集計、`businessValue` の読み戻し |

### 4.2 B6

| テストのファイル | 件数の目安 | 確かめること |
|---|---|---|
| `businessDataParse.test.ts`（足し） | 4 | 入力にするカラムの `formPart`・`nullable`・`required` の欠け、入力にしないカラムの `formPart: null` は正しい形 |
| `businessDataApi.test.ts`（書き込み） | 5 | 4,194,304 バイトちょうど・+1、`definitionId` を必ず送る、作成の `key` の有無 |
| `writeOutcome.test.ts`・`.property.test.ts` | 8＋性質 1 | 計画 4.3 の R-01・R-03 の表の各行、HTML の 200、本文なしの 2xx、code の無い 503、知らない code、届かない、既知の code が読めないかぎり「保存されていない」にならない |
| `formDiff.test.ts`・`.property.test.ts` | 8＋性質 2 | D20 の各場合、`DATETIME_TZ` の `+00:00`、秒まで、変えないカラムは入らない、桁を失わない |
| `formValidation.test.ts`・`.property.test.ts` | 8＋性質 1 | 必須・長さ（コードポイント、サロゲートの組）・桁・範囲・型・選択肢、上限ちょうどと +1 字、`pattern` は当てない |
| `problemMessage.test.ts`（書き込み） | 6 | 計画 4.6 の表、`field` が空の誤り、413、書き込みの 403（D3） |
| `useUnsavedGuard.test.tsx`（移したものと足し） | 今の件数＋6 | 計画 5節 Step 31 の項目（`markClean()` の3つ、`beforeunload` の外しと戻し、道の変わらない移動、判定の回数で旗が尽きない） |
| `LeaveConfirmDialog.test.tsx`（移したもの） | 今の件数 | 文言を props で受けても今どおり、はじめのフォーカスは「留まる」、vitest-axe |
| `WorkRoleProvider.test.tsx`・`WorkRoleSwitcher.test.tsx`（足し） | 4＋3 | 確かめの登録の有無、留まると切り替えない（`'ignored'`、トリガーへフォーカス）、離れると切り替える、後の登録が前を置き換える、外す関数 |
| `useRowWrite.test.tsx` | 5 | 保存中の印、5 秒の「時間がかかっています」、結果の分け方を通す、自動で送り直さない |
| `BusinessDataFormPage.test.tsx` | 8＋必須 | 部品の出し分け、`aria-required`・誤りのまとめへのフォーカス、入力を消さない、`autocomplete`、**保存の成功から詳細への移動で確かめの Modal が出ない（作成と更新）**、`key` の無い作成で一覧へ、「分からない」の後（D4）、作業ロールの切り替え（D2）、F6、vitest-axe |
| `DeleteConfirmModal.test.tsx` | 5 | `alertdialog`、背景で閉じない、はじめのフォーカスは「やめる」、見出しの行のラベル、失敗の文が Modal の中、vitest-axe |

### 4.3 B7

| テストのファイル | 件数の目安 | 確かめること |
|---|---|---|
| `e2e/support/axeRuleChecks.test.ts`（足し） | 2〜4 | U3 の行の操作・行のリンクの区分の当てはめ、対象が 0 件なら失敗 |
| 置き換えた部品のテスト（間に合ったとき） | 5〜6 | make-you-chic-ui の口の名前・フォーカス・件数の通知、vitest-axe |

E2E の本数: 業務データの代表の流れは 180 の1本だけ。160・170・040 の書き換えと 185・測り・B7 の passes の確かめは本数に数えない（team.md、`project.md` の学び）。

## 5. カバレッジの目標

- フロントエンドの全体の合計で行 80% 以上・分岐 70% 以上（`vitest.config.ts` の `thresholds`。変えない）。B4 の後の値は行 95.37%・分岐 90.32%。
- 除外は増やさない（team.md）。置き場の画面のテストを消した後も、新しい機能のテストで下限を保つ。
- `src/features/tables/` の値は Build and Test で記録する（team.md）。目安として、新しい機能のフォルダでも行 80%・分岐 70% を下回らないようにする（下回ったら、足りない場合のテストを足す。除外では手当てしない）。
- 件数とカバレッジの報告は、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の実測だけを書く（`project.md` の学び）。

## 6. モックと差し替えの使い方

- **画面のテスト**: API は `apiRequest` の手前で差し替える（今の機能のテストと同じ形。例 `frontend/src/features/roleadmin/testing/renderRoleAdmin.tsx`）。見本の応答は、写しの関数を通る形（1.5 の実際の形）で作り、`unknown` のまま渡す。
- **時刻と遅れ**: 5 秒の「時間がかかっています」と世代の確かめは、Vitest の偽の時計（`vi.useFakeTimers`）か、解決を遅らせた Promise で作る。実時間の `sleep` に頼らない。
- **作業ロールとログインの状態**: 骨組みの Provider は今のテストの補助（`frontend/src/app/testing/renderWithProviders.tsx`）で包み、作業ロールの roleId の変化とログイン⇄未ログインの切り替わりを外から起こす。
- **router**: 未保存の確かめと詳細への移動は、`createMemoryRouter` の実物で流す（判定の関数の呼ばれ方を実物で確かめる。計画 4.3 の R-08）。
- **E2E の差し替え**: 170・185 は `businessDataApiRoute.ts` で POST・PATCH の `/api/tables/*` を差し替え、`businessDataFixtures.ts` の型を付けた1つの見本を返す。見本の形は 180 の中で本物の応答と `shapeCompare` で毎回比べる（`project.md` の学び）。180 は差し替えず、本物の見本の PG で流す。
- **乱数**: 印の乱数（`crypto.getRandomValues`）はテストで差し替えず、形（16 バイトの16進）と一意だけを確かめる。

## 7. テストのデータ

- 業務データの見本の値は架空の値だけにする（例 商品コード `E2E-0001`・商品名「見本の品」）。実在しそうな氏名・宛先を置かない。メールアドレスは `example.com` だけ。
- 信頼できない入力の見本: `<script>alert(1)</script>`・`<img src=x onerror=alert(1)>`・`"`・`'`・`javascript:alert(1)`・改行・`/`・`%`・`?`・`#`・空白・バッククォート。
- 境界の値: BIGINT の上限と下限、`DECIMAL(38,10)` の桁、NULL、空の文字列、10,000 字の値、100 カラム × 100 行、65,536 バイト・4,194,304 バイトちょうどと +1、サロゲートの組（例 `𠮷`）と組になっていないサロゲート。
- E2E 180 の行・利用者・ロールは、その流れで自分で作る（初期管理者の状態は変えない）。行は準備のタスクが実行のスキーマに置く 21 件以上と、流れの中で作る行。実行ごとに一意の印を付け、目印を付けて値のファイル（`businessValue`）に書く。
- E2E の対象DB の資格情報は一時の2つのロールだけで、プロセスの環境変数で渡し（`webServer.env` に置かない）、`SECRET_ENV_NAMES` で報告の json と `test-results/` を検索する。管理者のパスワードはプロセスに入れない。
- 主キー・行の値・検索の値は、テストの説明文・`test.step` の題・注記・添付・標準出力・失敗の文に入れない。

## Sources

- 計画: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/code-generation/code-generation-plan.md`（2節の Testing Contract、5節の Step、10節の D1〜D10）
- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`（Step 2 の unit-test-instructions）
- この単位の設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/` の `functional-design/frontend-components.md`（7節）、`nfr-requirements/security-requirements.md`（NFR7.9〜7.13）、`nfr-design/security-design.md`（9節）・`performance-design.md`（4節）、`infrastructure-design/cicd-pipeline.md`（2節・3節）
- U4: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/code-generation/unit-test-instructions.md`（コマンドの形）
- コード（読み取りだけ）: `frontend/package.json`・`vitest.config.ts`・`playwright.config.ts`・`tsconfig.json`、`frontend/e2e/support/`、`build.gradle.kts`（`e2eTest`）、`README.md`（E2E の節）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（学び）

## Assumptions & Open Questions

- [assumption] ファイルの名前は計画の仮の名前で、生成で変えたら `generation-notes.md` に書き、この文書のコマンドも同じ名前に読み替える。
- [assumption] `-Pe2e.files` は B6 の Step 24 で足す。カンマで区切って複数のファイルを受ける形の見込みで、生成で決めた形を 3.3・3.4 に合わせる。
- [assumption] B5 の間（`-Pe2e.files` と対象DB の前提が入る前）は、README の手順どおり `npx playwright test <ファイル>` を直接流せる（D1 が A のとき）。
- D6・D10 の答えで、3.3 の B7 のファイルと lint の設定が変わる。
