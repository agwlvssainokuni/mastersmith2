# コード生成のまとめ — U3 u3-reference-screens

単位 U3（ui・L）を、計画 `code-generation-plan.md` のとおり Bolt B4 の1つで作り、`develop` へ squash で統合した（711ddd0。前の `develop` の先頭は a75baec）。材料は `generation-notes.md`（生成の担当と指揮役の記録）と `git diff a75baec 711ddd0`（`aidlc/` を除く）。計画の承認は「plan approval off」の記録（`code-generation-questions.md`）で、計画 6節の Q1〜Q4 はすべて推奨の A で作った。

| Bolt | 統合のコミット | 作業ブランチの上のコミット | 中身 |
|---|---|---|---|
| B4 | 711ddd0 | 491e57a（C1）・3a286ed（C2）・a4ea6bd（C3）・f63f2d4（C4）・c0d65a7（C5） | 作成・更新の画面の他テーブル参照（REFERENCE の選択・LOOKUP の欄とピッカー・入力できない参照の読み取りの表示）、定義の `purpose`、ラベルの1件読み、保存の誤りの文言、E2E 190・195 と共有の DSL |

B4 の CI（711ddd0）: run 41166457994、1回目の試行で success、検査の job 27分49秒、再実行なし。間の a75baec（記録だけ）の run 41161783903 は1回目が ReferenceSameResponseIT の1件で failure（目印 "abc" が乱数のトレースID に偶然に当たった）、2回目で success（36分32秒）。原因を確かめて a3099f8 で直した

各区切りで、後の区切りの変更を外して `npm run typecheck` が通ることを確かめた（`project.md` のコミットの区切りの学び）。

## 1. 作ったもの

- **受け渡しの層**（`businessDataTypes.ts`・`businessDataParse.ts`・`businessDataApi.ts`・`useTableDefinition.ts`）: カラムの `reference` を本物の型 `ReferenceField`（`kind`・`status`・`options`・`picker`）で写す。U1 の B1 が仮に置いた `reference?: unknown` とそのまま写す形を置き換えた。`FORM_PARTS` に `lookup` を足した。参照先の検索 `searchReferenceRows` とラベルの1件読み `getReferenceLabel`（固定の道 `BUSINESS_DATA_PATHS.referenceRows`、値は本文で運ぶ）、ラベルは別の写し `parseReferenceLabel`。定義は作成で `CREATE`、更新で `UPDATE` を付けて毎回読み、記憶（`businessDataStore`）を読み書きしない（一覧・詳細は今のまま `purpose` を付けない）。
- **純粋な関数**（`references/referenceField.ts`・`references/pickerSearch.ts`・`problemMessage.ts`・`formDiff.ts`・`formValidation.ts`）: 部品の選び分け `chooseFieldKind`（`hidden`・`locked`・`plain`・`reference-readonly`・`reference-select`・`lookup`）、`optionText`（「ラベル (値)」、ラベルが null なら値）、必須の判定と開いた直後に保存できない参照の判定（`isRequiredColumn`・`isBlockedRequired`、D13）、入力できない参照の集合 `lockedReferenceColumns`（差分と検証に渡す。X-5）、ピッカーの条件の確かめ `validatePickerInputs` とページの表示 `pickerPaging`、保存の誤りの鍵 `referenceErrorKey`・`requiredBlockedMessageKey`。
- **フックと部品**（`references/`）: `useReferenceLabels`（同時 3 本まで、世代ごとの閉じた状態と `AbortController`、閉じた世代は `pump` を呼ばない。R-05）、`useLookupSearch`、`ReferenceSelectField`（select・radio、200 件の radio は1つのグループ）、`ReadonlyReferenceField`（readOnly・`aria-readonly`・説明文、disabled にしない）、`LookupField`（「選ぶ…」・「クリア」・隣のラベル）、`LookupPickerDialog`（条件・検索・20 件のページ・行ごとの「選ぶ」・件数の `aria-live`）、`ReferenceColumnField`（カラムごとの選び分けの薄い部品。X-4）、`reference.css`。
- **作成・更新の画面**（`BusinessDataFormPage.tsx`）: 部品を組み込み、開いた直後の必須の参照の理由は `helperText`（`role` 無し）、保存を押した後だけ `error`（`role="alert"`）。409 `BUSINESS_REFERENCE_NOT_FOUND`（`REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`）・400 `REQUIRED` を項目の誤りに出す。定義の読み直しで入力不可に変わったカラムの入力は捨て、既存の「捨てた」知らせを出す（D35）。
- **文言**（`messages.ts`）: 日英の鍵を足し、`tables.error.REFERENCE_NOT_FOUND` の文を差し替えた（D31）。
- **E2E**: 190（参照ピッカーの代表の流れ1本）、195（12 の状態 × 20 組の axe）、共有の DSL `support/businessDataDsl.ts`（180 の DSL を移し、参照の表3つとメニューを足した）、前提の作り方 `support/referenceSetup.ts`、差し替えの見本 `support/referenceFixtures.ts` とその型の確かめ、`support/businessDataSchema.sql` に表3つ（`e2e_customer` 3 行・`e2e_product` 25 行・`e2e_order` 1 行）。
- **基盤の設計 R-05 の扱い**: 180・190・195 はそれぞれ自分の共有の DSL を流れの前に投入・適用する。E2E は `workers: 1` で順に流れ、**最後に流れたファイルの DSL が適用中のまま残ってよい**（次の実行のどのファイルも前の DSL に頼らない）。初期管理者・見本の DSL・見本の行には触れない。

### 変えていないもの（計画 1節）

backend・DSL の書式と JSON Schema・移行・`compose.yaml`・CI のワークフロー・`frontend/package.json`・`package-lock.json`（`git diff` が空）・`frontend/scripts/check-bundle-size.mjs`・`registration.ts`・`src/shared/`・一覧と詳細の画面と部品・`writeOutcome.ts`・make-you-chic-ui（固定先 `4c63d11`）・`.gitignore`・README。make-you-chic-ui への新しい依頼は出していない。

## 2. 作った・変えたファイル

`source-manifest.json` に 49 のパスを並べた（`git diff --name-status a75baec 711ddd0 -- . ':!aidlc'`）。

| 種類 | ファイル |
|---|---|
| 新しい部品・関数（`frontend/src/features/tables/references/`） | `referenceField.ts`・`pickerSearch.ts`・`useReferenceLabels.ts`・`useLookupSearch.ts`・`ReferenceSelectField.tsx`・`ReadonlyReferenceField.tsx`・`LookupField.tsx`・`LookupPickerDialog.tsx`・`ReferenceColumnField.tsx`・`reference.css` |
| 新しいテスト（同じフォルダ） | `referenceField.test.ts`・`pickerSearch.test.ts`・`useReferenceLabels.test.tsx`・`ReferenceSelectField.test.tsx`・`ReadonlyReferenceField.test.tsx`・`LookupField.test.tsx`・`LookupPickerDialog.test.tsx`・`ReferenceColumnField.test.tsx` |
| 変えた本体（`frontend/src/features/tables/`） | `businessDataTypes.ts`・`businessDataParse.ts`・`businessDataApi.ts`・`useTableDefinition.ts`・`BusinessDataFormPage.tsx`・`formDiff.ts`・`formValidation.ts`・`problemMessage.ts`・`messages.ts` |
| 変えたテスト | `businessDataParse.test.ts`・`businessDataParse.property.test.ts`・`businessDataApi.test.ts`・`useTableDefinition.test.tsx`・`formDiff.test.ts`・`formDiff.property.test.ts`・`formValidation.test.ts`・`problemMessage.test.ts`・`BusinessDataFormPage.test.tsx`・`BusinessDataListPage.test.tsx`・`BusinessDataDetailPage.test.tsx` |
| テストの手伝い（`testing/`） | 新しい `referenceSamples.ts`・`renderReference.tsx`、変えた `sampleDefinition.ts`（`referenceDefinitionOverrides`） |
| E2E（`frontend/e2e/`） | 新しい `190-reference-picker-flow.e2e.ts`・`195-reference-picker-accessibility.e2e.ts`・`support/businessDataDsl.ts`・`support/referenceSetup.ts`・`support/referenceFixtures.ts`・`support/referenceFixtures.test.ts`、変えた `180-business-data-flow.e2e.ts`（import と `TABLE` の定数だけ）・`support/businessDataSchema.sql` |

## 3. テストと確かめの結果

### 3.1 全体（指揮役が作業ブランチの上で流した）

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の環境変数・`caffeinate -dims`）: **成功、27分48秒**（U2 の後の 26分48秒から約 1 分の伸び。CI の線の約 42 分の内）。**バックエンドの単体 2,804 件・結合 1,652 件、画面 2,291 件、失敗 0**。frontend のカバレッジの `thresholds`（全体の合計）も通った。
- `./gradlew e2eTest`（フォルダを `references/` に替えた後）: **277 件すべて通過、14.4 分**。変更の前（同じ日の Step 2 の実測。256 件・13.1 分）の 1.5 倍の線 **19.7 分の内**。流した 12:11〜12:53 に Sleep・DarkWake は無かった。
- 画面の層の手元の確かめ（Step 11）: `format:check`・`lint`（oxlint・ESLint）・`lint:css`・`typecheck`・`license:check` が通過。`references/` に `dangerouslySetInnerHTML`・`localStorage`・`sessionStorage`・`indexedDB`・`console.` は無い。

### 3.2 この単位のテスト（生成の担当の実測）

| まとまり | 件数 |
|---|---|
| `unit-test-instructions.md` 3.3 の単位のテスト（24 ファイル。`referenceFixtures.test.ts` を除く。既存のファイルにある前からの件を含む） | **374 件すべて通過** |
| 内訳: 受け渡しの4ファイル（Step 4） | 112 件 |
| 内訳: 純粋な関数の6ファイル（Step 6） | 115 件 |
| 内訳: 部品・フックの8ファイルと `messages.test.ts`（Step 8。vitest-axe 16 件を含む） | 118 件 |
| `BusinessDataFormPage.test.tsx` に足した件（Step 10） | 11 件（ほかに AC3.4.3 の一覧・詳細に1件ずつ） |
| 既存の画面のテスト（作成・更新・一覧・一覧の書き込み・詳細・表・`registration`） | 62 件、期待を変えずに通過 |
| fast-check の性質ベースのテスト | 失敗なし（種の記録なし） |

計画の Summary の見積もり（約 150 件）より多いのは、計画の数が足す件の見積もりで、374 件はファイルの全件を数えたため（`[assumption]` のとおり実数は実測）。

### 3.3 E2E 190・195 と axe

- **状態 7 を先に流した（R-03、Step 13）**: 20 組すべて通過、**axe の違反 0 件（`color-contrast` なし）**、`role="alert"` 0 件。止めて依頼者に諮ることは無かった。
- **180・190・195（Step 14・15）**: 22 件すべて通過（180 の1件・190 の1件・195 の 20 組）。ファイルごとの時間は 180 が 5.4 秒、190 が 2.7 秒、195 が1組 2.7〜3.3 秒（20 組で約 57 秒）。
- **195 の axe**: **12 の状態 × 20 組 = 240 回、違反 0 件**。375px の6組で、開いたダイアログが表示の中に収まることを3つの状態（開いた直後・結果・長い名前）で 18 回確かめ、全状態で画面全体の横のはみ出しが無い。
- **X-3**: 195 の `beforeAll` で自作の利用者で取った本物の定義（`CREATE`）・検索・ラベルの応答と差し替えの見本を `shapeCompare` で比べ、違い 0 件。
- **R-04**: 保存の要求は差し替えの口ですべて答え、本物へ通さない。各組で受けた保存の要求は状態 9 の1回だけ（状態 8 は画面の検証で送らない）。
- 報告の部品（資格情報・値のファイルの値の検索）も通った。

### 3.4 この段で流していない・確かめていないもの

- B4 の CI（711ddd0）の結果と検査の job の時間は確認中（Build and Test で試行ごとに確かめる）。
- `features/tables` のカバレッジの値は Build and Test で記録する（`team.md`。全体の合計の `thresholds` は `verify` で通過）。

## 4. chunk の大きさ（前と後。NFR2.44、基盤の設計 4節の「記録」）

`frontend/dist` をリポジトリの外の node の数行（`performance-design.md` 4節の擬似コード、`gzipSync`）で測った。起点の条件（キーが `src/features/tables/` で始まり `isDynamicEntry` が真）は直していない（R-06 の記録は不要）。起点は `lazy` で読む3画面に当たった。

| 値 | 前 | 後 |
|---|---|---|
| 入口の JavaScript（`check-bundle-size.mjs`） | 165.1 KB（169,037 B） | 166.4 KB（＋1.3 KB。足した文言 `messages.ts` を `registration.ts` が入口で静的に読むため） |
| 起点 `BusinessDataListPage.tsx` | 14.7 KB | 16.4 KB |
| 起点 `BusinessDataDetailPage.tsx` | 10.2 KB | 11.6 KB |
| 起点 `BusinessDataFormPage.tsx` | 14.3 KB（画面のファイル 5,750 B） | 19.9 KB（同 8,896 B） |
| 起点の合計（同じファイルは1回） | 23.5 KB（24,091 B） | 28.3 KB |
| `.css` の別枠の合計 | 1.8 KB | 2.1 KB |

一覧・詳細の起点の増え方は共有の chunk（写し・API・`useTableDefinition` の chunk 6,450 B → 7,813 B と `searchConditions` の分かれ）による。参照の部品は作成・更新の画面の chunk に入り、入口には入っていない（`registration.test.ts` の `lazy` の確かめも通過）。

## 5. 計画と承認済みの設計との差（依頼者の決定つき）

承認済みの文書（計画・`unit-test-instructions.md`・設計）は書き換えていない（`project.md`）。

### 5.1 依頼者が受け入れた差

| 差 | 内容と理由 | 依頼者の決定 |
|---|---|---|
| 下位のフォルダ名 `references/` | 計画 2節の `frontend/src/features/tables/reference/` は、ルートの `.gitignore` の `reference/`（依頼者の参考資料の置き場の決まり。`project.md` の Mandated）に当たり git の対象外になる。`frontend-components.md` の「下位のフォルダ名は仮で変えてよい」の範囲で `references/` に替え、`.gitignore` は変えていない（参考資料の決まりを狭めないため）。替えた後に型・リンタ・`features/tables` の全件と見本のテスト（43 ファイル・508 件）、E2E の全件を流し直して通った。計画 2節・9節と `unit-test-instructions.md` 3節のコマンドの道は `references/` と読み替える | 受け入れ |
| E2E の補助 `support/referenceSetup.ts` の追加 | 190・195 が共有する前提（共有の DSL の投入・適用、自作のロール `E2ER_<種類>_<印>`、招待から登録した自作の利用者、値のファイルへの記録）を1か所にした。計画 2節の `support/` の一覧に無いファイル | 受け入れ |
| 担当が決めた細かい点（下の 5.2） | 計画・設計に決めが無い、または計画の前提より安全側にした点 | 受け入れ |

### 5.2 担当が決めた細かい点

- **必須の判定を `!nullable || required` にした**: 計画の `[assumption]`（`nullable` が偽だけ）より広い側。U2 の 400 `REQUIRED` は `required` からも出るため、画面で先に止める。
- **409 でも「再度検索」を出す**: D27 に 409 `BUSINESS_TARGET_DB_MISMATCH` の「再度検索」の決めが無かったため、503 と同じにした。400 の条件の誤りのときは検索の前の状態に戻して誤りを出す。
- **値が空のとき「クリア」を押せなくした**: 消すものが無いため。
- **ピッカーの検索の応答の形の崩れ**: 行が 20 件を超える、または `hasNext` が真なのに行がちょうど 20 件でないものを形の崩れにした（総ページが今のページより小さくならないため。D25 の式の前提）。`PICKER_PAGE_SIZE = 20` は写しの置き場に置いた。
- **知らない `kind`（`FIXED` を含む）**: 写しの結果を `{ kind: 'REFERENCE', status: 'NO_PERMISSION', options: [], picker: null }` にして D3 の入力不可に倒す（`ReferenceField.kind` の型を広げない）。`formPart: lookup` との組の崩れは、届いた `kind` が文字どおり `REFERENCE` のときだけに当てる。知らない `status` に形の崩れた `options`・`picker` が届いたときも形の崩れにする。
- **文言の鍵**: 5節に無い `tables.picker.unspecified`（BOOLEAN の「（指定しない）」）を足した。**`tables.reference.loading` は足したが今は使っていない**（消してよい。7節）。
- **i18n の `escapeValue`**: 計画は「`escapeValue` を切らない」と書いたが、アプリの設定（`app/i18n/i18n.ts`）は前から `escapeValue: false`（React の文字として描き React がエスケープする）。設定は変えず、値は React の文字として描き `dangerouslySetInnerHTML` を使わないことを XSS の見本のテストで確かめた。
- **`useReferenceLabels` の世代の鍵**: 作成・更新の画面のフォームの定義の識別にした（定義の読み直しで世代が進む）。状態の更新は答えの後だけで、効果の中で同期に `setState` しない。
- **`ReferenceColumnField`（X-4）**: 既存の `FieldInput` と読み取りの表示を画面に残し、描く関数（`renderPlain`・`renderLocked`）で受ける形にした（`FieldInput` を別のファイルへ移さずに済む）。
- **195 の組み立て**: 状態を `test.step` で分け組ごとに1件（20 件）、比べの確かめは `beforeAll` に置き件数を増やさなかった。差し替えは `support/businessDataApiRoute.ts` を使わず、定義・検索・保存の3つの道だけを差し替え、ほかは本物へ通す（本物の状態 1〜3・10・11 を作るため）。
- **E2E の XSS の見本の `javascript:`**: oxlint の `no-script-url` に当たったため、既存のテストと同じ組み立て（`['javascript', 'alert(1)'].join(':')`）にした。
- **手順の省略**: `unit-test-instructions.md` 3.1 の変更の前の単独の実行は省き、Step 4・10 で同じファイルが通ることで口を確かめた（変更の前の E2E の全件が同じ3画面を通っているため）。
- **見込んだ直しが要らなかった**: 計画 3節の「定義の要求の数」を数える既存のテストの期待の直しは要らなかった（62 件が期待を変えずに通過）。`businessDataParse.test.ts` の `lookup` の2件は題と読みだけ直した（入力は変えず、「参照が無い `lookup` は形の崩れ」として通る）。

### 5.3 計画の承認で決めた設計との差（計画 5.2 のとおり作った）

| ID | 内容 |
|---|---|
| X-1 | 本物の応答の `options`・`picker`・`listColumns` の `operator` の欠け（`NON_NULL`）を空の並び・`null` として読み、`REFERENCE`・`AVAILABLE` の `options` の欠けと `LOOKUP`・`AVAILABLE` の `picker` の欠けだけを形の崩れにした |
| X-2 | `useTableDefinition(ref, load = getDefinition, purpose?)` と `LoadDefinition` の任意の3番目の引数（Q1: A） |
| X-3 | 195 の中で取った本物の応答と差し替えの見本を比べた（Q4: A） |
| X-4 | `references/ReferenceColumnField.tsx` を足した（Q2: A） |
| X-5 | `diffForm`・`hasFormChanges`・`validateForm` に任意の最後の引数 `lockedColumns`（既定は空の集合 `NO_LOCKED`） |
| X-6 | E2E の変更の前の値は Step 2 で同じ日に測った（Q3: A。256 件・13.1 分。1回目は始めの Mailpit の確かめで止まり、コードを変えずに流し直した。原因は確かめていない） |

## 6. 後の段に引き継ぐこと（持ち主つき）

| 持ち主 | 引き継ぐこと |
|---|---|
| Build and Test | B4 の CI（711ddd0）の試行ごとの結果と検査の job の時間（NFR2.45）、`features/tables` のカバレッジの値、Intent の最後の E2E の全件 |
| U1 u1-reference-read（統合済み） | `traceability.json` の Deferred: AC1.1.5・AC1.1.9・AC1.2.1・AC1.2.5、AC1.3.5（U2 と共同） |
| U2 u2-reference-write-check（統合済み） | `traceability.json` の Deferred: AC1.5.2 |

`traceability.json` は全 77 行（OK 69・Deferred 7・N/A 1）で、センサー（traceability）が `passed`。OK の `target` はどれも1つの実在するファイル。AC2.3.5 は初稿の ID のため機能設計と同じく N/A。

## 7. 依頼者に確かめたいこと

- **使っていない文言の鍵 `tables.reference.loading`**（日英）を残すか。推奨: 後の直しの Bolt で消す（今は害が無く、消すためだけに画面の本体を変えて E2E を流し直すのは手間に見合わないため）。
- 承認済みの計画と `unit-test-instructions.md` のコマンドの道（`src/features/tables/reference/…`）は書き換えず、`references/` と読み替える扱いでよいか（`project.md` の「承認済みの文書は書き換えない」に合わせた）。
- B4 の CI（711ddd0）は確認中で、失敗したときは `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。
