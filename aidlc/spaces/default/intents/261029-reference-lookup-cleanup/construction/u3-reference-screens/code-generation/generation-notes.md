# 生成の記録 — U3 u3-reference-screens（Bolt B4）

開発担当が生成の途中で残す記録（計画との差・裁量で決めたこと・測った値）。指揮役が `code-summary.md` にまとめる材料。

## Step 1: 準備

- ブランチ `feature/261029-reference-lookup-cleanup-b4` は `develop`（a75baec）と同じ先頭で、549d38b（B1）・b048ec1（B2）・f35b7b8（B3）を含むことを確かめた。作業フォルダの未コミットの変更は `aidlc/` の監査ログだけ。
- `frontend/test-results`（git の対象外）を消した。`frontend/playwright-report` は無かった。
- 3節の検索の対象は計画のとおりで、増えたものは無かった。

## Step 2: テストの実行の口の確かめと、変更の前の測り

### E2E の全体の時間（前。Q3: A）

- 1回目の背景の実行は、始めの Mailpit の確かめで 3 秒で止まった（`http://127.0.0.1:8025/api/v1/info` に届かない）。同じ時刻に前景の curl・背景の curl は 200 で届き、前景の Gradle の確かめも Mailpit を通った。原因は確かめていない（見立て: その時だけの Gradle の作業プロセスの接続の失敗）。コードは変えずに流し直した。
- 流し直し（`caffeinate -dims ./gradlew e2eTest`、11:29:11〜11:42:21）: **256 passed（Playwright 13.1 分、Gradle 全体 13分10秒）**。`pmset -g log` のこの間に Sleep・DarkWake は無かった（Chrome の表示のスリープを止める assertion の作成・解放だけ）。
- 1.5 倍の線: 約 19.7 分（Playwright の 13.1 分 × 1.5）。

### chunk の大きさ（前）

- `./gradlew e2eTest` の `frontendBuild` が UP-TO-DATE（今の develop のソースで作った `frontend/dist`）。リポジトリの外（作業用の一時の場所）に置いた node の数行（`performance-design.md` 4節の擬似コード、`gzipSync`）で測った。スクリプトはリポジトリに足していない。
- 起点（キーが `src/features/tables/` で始まり `isDynamicEntry` が真）は 3つ取れ、`registration.ts` の `lazy` で読む3画面（`BusinessDataListPage.tsx`・`BusinessDataDetailPage.tsx`・`BusinessDataFormPage.tsx`）に当たった。起点の条件は直していない（R-06 の記録は不要）。

| 値 | 前 |
|---|---|
| 入口の JavaScript（`check-bundle-size.mjs`） | 165.1 KB（169,037 B） |
| 起点 `BusinessDataListPage.tsx` | 14.7 KB（15,087 B） |
| 起点 `BusinessDataDetailPage.tsx` | 10.2 KB（10,473 B） |
| 起点 `BusinessDataFormPage.tsx` | 14.3 KB（14,596 B。うち画面のファイル 5,750 B） |
| 起点の合計（同じファイルは1回） | 23.5 KB（24,091 B） |
| `.css` の別枠の合計 | 1.8 KB（1,893 B） |

### 単体テストの口

- `unit-test-instructions.md` 3.1 のコマンド（既存の3ファイル）は、変更の前の単独の実行を省き、Step 4・10 で同じファイルを流して通ることで口を確かめた（計画との差。変更の前の E2E の全件が同じ3画面を通っているため）。

## Step 3・4: 受け渡しの層

- 型（`businessDataTypes.ts`）: 計画の型に加えて `REFERENCE_STATUSES`（写しで照らす既知の一覧）・`DEFINITION_PURPOSES`・`ReferenceCondition`（ピッカーの条件の送る形）を足した。`FORM_PARTS` に `lookup` を足した。
- 写し（`businessDataParse.ts`）の裁量:
  - 知らない `kind`（`FIXED` を含む）は、写しの結果を `{ kind: 'REFERENCE', status: 'NO_PERMISSION', options: [], picker: null }` にした（`ReferenceField.kind` の型を広げずに D3 の入力不可に倒すため）。`formPart: lookup` との組の崩れの判定は、届いた `kind` が文字どおり `REFERENCE` のときだけに当てる。
  - `parseReferenceRowPage` は、計画の条件に加えて「行は 20 件まで」「`hasNext` が真なら行はちょうど 20 件」を形の崩れにした（ページ送りの総ページが今のページより小さくならないため。D25 の式の前提）。`PICKER_PAGE_SIZE = 20` を写しの置き場に置いた。
  - 知らない `status` の参照の項目に `options`・`picker` が届いたときも、形が崩れていれば形の崩れにする（届いた値を黙って捨てない）。
- API（`businessDataApi.ts`）: `getDefinition` の本文は、`definitionId`・`purpose` が無ければ今と同じ形（キーを足さない）。`searchReferenceRows` は空の値の条件を本文から外す（画面の確かめと二重）。
- `useTableDefinition`（X-2）: `purpose` は鍵に入れ、記憶（`businessDataStore`）は読まず書かない。`reload` は最後に読めた定義の識別を `staleId` にし、`forgetDefinition` は呼ばない。
- テストの件数: 受け渡しの4ファイルで 112 件。既存の `lookup` の2件は題と読みだけ直した（入力は変えず、「参照が無い `lookup` は形の崩れ」として通る）。fast-check の失敗は無かった（種の記録なし）。

## Step 5・6: 純粋な関数

- `reference/referenceField.ts`: 計画の関数に加えて `isRequiredColumn`（`nullable` が偽か `required` が真）・`isBlockedRequired`（D13）・`lockedReferenceColumns`（X-5 に渡す集合）を置いた。必須の判定は計画の前提（`nullable` が偽）に `required` が真を足した広い側にした（U2 の 400 `REQUIRED` は `required` からも出るため、画面で先に止める）。
- `chooseFieldKind` の戻り値は `hidden`（作成で入力できない）・`locked`（更新で入力できない）・`plain`・`reference-readonly`（理由つき）・`reference-select`・`lookup` の6つ。
- `referenceErrorKey` と D13 の文の鍵の関数 `requiredBlockedMessageKey` は `problemMessage.ts` に置いた（`problemMessage.ts` → `reference/referenceField.ts` → `formDiff.ts` の向き。循環なし）。
- `pickerSearch.ts`: `validatePickerInputs` は「すべて空」「条件の誤り」「送る条件」の3つを返す。`pickerPaging` は表の件数のほかに、件数の変化の知らせの `from`・`to` と文の鍵も返す。BOOLEAN の「（指定しない）」の文言は `tables.picker.unspecified` を足した（5節の鍵に無かったため。真・偽は既存の `tables.value.yes`・`tables.value.no`）。
- `formDiff.ts`・`formValidation.ts`（X-5）: 最後の任意の引数 `lockedColumns`（既定は空の集合 `NO_LOCKED`）。
- テストの件数: 純粋な関数の6ファイルで 115 件。fast-check の失敗は無かった。

## Step 7・8: 文言・フック・部品

- 文言: 5節の鍵をすべて日英に足し、`tables.error.REFERENCE_NOT_FOUND` を差し替えた。5節に無い鍵として `tables.reference.loading`（今は使っていない。消してよい）・`tables.picker.unspecified` を足した。
- i18n の補間: アプリの設定（`app/i18n/i18n.ts`）は前から `escapeValue: false`（React の文字として描くため React がエスケープする）で、計画の「`escapeValue` を切らない」とは違う。設定は変えていない。値は React の文字として描き、`dangerouslySetInnerHTML` を使わないことを XSS の見本のテストで確かめた。
- `useReferenceLabels(items, key, load)`: 世代の鍵を作成・更新の画面のフォームの定義の識別にした（定義の読み直しで世代が進む）。状態の更新は答えの後だけで、効果の中で同期に `setState` しない（`loading` は「まだ答えの無い項目」から求める）。
- `useLookupSearch`: 400 の条件の誤りのときは、検索の前の状態に戻して条件の誤りを出す。「再度検索」は 409（`mismatch`）でも出す（D27 に 409 の「再度検索」の決めが無かったため、503 と同じにした）。
- `LookupField`: 「クリア」は値が空のとき押せなくした（消すものが無いため）。
- `ReferenceColumnField`（X-4）: 既存の `FieldInput` と読み取りの表示は作成・更新の画面に残し、描く関数（`renderPlain`・`renderLocked`）で受ける形にした（`FieldInput` を別のファイルへ移さずに済む）。
- テストの補助 `testing/renderReference.tsx`（文言と Modal の Provider で包む）を足した。
- テストの件数: 部品・フックの8ファイルと `messages.test.ts` で 118 件（vitest-axe 16 件を含む）。

## Step 9・10: 作成・更新の画面

- `BusinessDataFormPage.tsx`: 計画のとおり。LOOKUP で選んだ行のラベルは画面の状態 `picked` に持ち、読めたラベルより先に使う。定義の読み直しの後、入力不可の参照の項目に変わったカラムの入力は捨て、既存の「捨てた」知らせを出す（D35）。
- 既存の画面のテスト（作成・更新・一覧・一覧の書き込み・詳細・表・`registration`）62 件は、期待を変えずに通った（3節で見込んだ「定義の要求の数」の直しは要らなかった）。
- 足したテスト: `BusinessDataFormPage.test.tsx` に 11 件（用途・R-05・503・D13 の開いた直後と保存の後の vitest-axe・D14・409/400 の3件・503・LOOKUP の読みと選びと保存・ラベルの失敗・AC2.5.4・D35）。AC3.4.3 は `testing/sampleDefinition.ts` の `referenceDefinitionOverrides` を一覧・詳細のテストに1件ずつ流した。

## Step 11: 画面の層の手元の確かめ

- `npm run format:check`・`lint`（oxlint・ESLint）・`lint:css`・`typecheck`・`license:check` が通った。oxlint の `no-script-url` に XSS の見本の `javascript:` が当たったため、既存のテストと同じ組み立て（`['javascript', 'alert(1)'].join(':')`）にした。
- コードの検索: `reference/` に `dangerouslySetInnerHTML`・`localStorage`・`sessionStorage`・`indexedDB`・`console.` は無い。`useTableDefinition` は `purpose` つきの経路で記憶を呼ばない。
- `git diff --stat develop -- frontend/package.json frontend/package-lock.json` は空。
- `unit-test-instructions.md` 3.3 の単位のテスト（`referenceFixtures.test.ts` を除く 24 ファイル）: **374 件すべて通過**。計画の外の既存のテストが壊れていないことを見るため、画面のテストの全件も流した: **236 ファイル・2,262 件すべて通過**（63 秒）。

## Step 12: E2E の共有の DSL と前提の表

- `support/businessDataSchema.sql` に `e2e_customer`（3 行）・`e2e_product`（25 行。PROD-024 は 100 文字以上で `<` を含む、PROD-025 は2語の品名。ほかは目印を含む）・`e2e_order`（1 行）を足した。権限は既存の `GRANT … ON ALL TABLES IN SCHEMA` で足した表にも付く。
- `support/businessDataDsl.ts`: 180 の DSL を移し、参照の3つの表と注文のメニューを足した。180 は import と `TABLE` の定数を替えただけで、流れと確かめは変えていない。確認項目: (1) 足す表・メニューの名前は「E2ER …」で始め、180 の「E2EB 品目 …」を含まない、(2) 180 の確かめは行の件数だけで表・メニューの件数に頼っていない、(3) 180 のロールの権限は `e2e_item` だけ。
- E2E の補助を足した（計画 5.1 の 2 の範囲）: `support/referenceSetup.ts`（190・195 が共有する前提の作り方。共有の DSL の投入・適用、自作のロール `E2ER_<種類>_<印>`、招待から登録した自作の利用者、値のファイルへの記録）、`support/referenceFixtures.ts`・`referenceFixtures.test.ts`（195 の差し替えの見本。本物の応答と同じく null の `options`・`picker` を本文に置かない形の型）。

## Step 13: E2E 195 の状態 7（R-03）

- 準備（自作の利用者・ロール・共有の DSL）と状態 7 だけを書き、`-Pe2e.files=e2e/195-reference-picker-accessibility.e2e.ts` で流した（12:01:23〜12:02:12）。**20 組すべて通過・axe の違反 0 件（`color-contrast` なし）・`role="alert"` 0 件**。Playwright 35.6 秒、Gradle 全体 42 秒。Sleep・DarkWake なし。止めて諮ることは無かった。

## Step 14・15: E2E 190・195・180

- `-Pe2e.files=e2e/180-business-data-flow.e2e.ts,e2e/190-reference-picker-flow.e2e.ts,e2e/195-reference-picker-accessibility.e2e.ts`（12:04:55〜12:06:16）: **22 件すべて通過**（180 の1件・190 の1件・195 の 20 組）。Playwright 1.3 分、Gradle 全体 1分20秒。Sleep・DarkWake なし。報告の部品（資格情報・値のファイルの値の検索）も通った。
- ファイルごとの時間: 180 は 5.4 秒、190 は 2.7 秒、195 は1組 2.7〜3.3 秒（20 組で約 57 秒）。E2E の全件の見込みは変更の前の 13.1 分＋約 1 分で、1.5 倍の線（約 19.7 分）の内の見込み（全件は指揮役が流す）。
- 195 の axe: **12 の状態 × 20 組 = 240 回、違反 0 件**。375px の 6 組で、開いたダイアログの矩形が表示の中に収まることを 3 つの状態（開いた直後・結果・長い名前）で 18 回確かめた。全状態で画面全体の横のはみ出しが無いことを確かめた。
- 195 の X-3: 前提を作る `beforeAll` の中で、自作の利用者で本物の定義（用途 CREATE）・検索・ラベルの1件読みの応答を取り、差し替えの見本と `shapeCompare` で比べて違い 0 件（顧客・商品のカラムは別に比べ、参照の項目の中の形も見た）。
- R-04: 保存の要求（作成・更新）は差し替えの口ですべて答え、本物へ通さない形にした。各組で受けた保存の要求は状態 9 の1回だけだった（状態 8 は画面の検証で送らない）。
- 計画との差: 195 は状態を `test.step` で分け、組ごとに1件（20 件）。比べの確かめは `beforeAll` に置き、件数を増やさなかった。差し替えは `support/businessDataApiRoute.ts`（答えの無い要求を打ち切る）を使わず、195 の中で定義・検索・保存の3つの道だけを差し替え、ほかは本物へ通す口にした（本物の状態 1〜3・10・11 を作るため）。

## chunk の大きさ（後。Step 16 の材料）

- Step 2 と同じ手順で、この変更で作り直した `frontend/dist` を測った（起点の条件は直していない）。

| 値 | 前 | 後 |
|---|---|---|
| 入口の JavaScript | 165.1 KB | 166.4 KB（＋1.3 KB。足した文言 `messages.ts` は `registration.ts` が入口で静的に読むため） |
| 起点 `BusinessDataListPage.tsx` | 14.7 KB | 16.4 KB |
| 起点 `BusinessDataDetailPage.tsx` | 10.2 KB | 11.6 KB |
| 起点 `BusinessDataFormPage.tsx` | 14.3 KB | 19.9 KB（画面のファイル 5,750 B → 8,896 B） |
| 起点の合計 | 23.5 KB | 28.3 KB |
| `.css` の合計 | 1.8 KB | 2.1 KB |

- 一覧・詳細の起点の増え方は、共有の chunk（写し・API の `useTableDefinition` の chunk 6,450 B → 7,813 B と、`searchConditions` の分かれ）による。参照の部品は作成・更新の画面の chunk に入り、入口には入っていない（`registration.test.ts` の `lazy` の確かめも通過）。

## 計画との差: 下位のフォルダの名前を `references/` にした

- 生成の終わりの `git status` で、計画 2節の `frontend/src/features/tables/reference/` がリポジトリのルートの `.gitignore` の `reference/`（依頼者の参考資料の置き場の決まり。`project.md` の Mandated）に当たり、git の対象外になると分かった。そのままではコミットに入らない。
- 機能設計の `frontend-components.md` の前提（「下位のフォルダ名 `reference/` とファイル名は仮で、Code Generation で変えてよい」）の範囲で、フォルダを `frontend/src/features/tables/references/` に替えた。`.gitignore` は変えていない（参考資料の決まりを狭めないため）。中身・ファイル名は同じで、import の道（`problemMessage.ts`・`BusinessDataFormPage.tsx`）だけを直した。
- 替えた後に、型・リンタ・`features/tables` のテストの全件と見本のテスト（43 ファイル・508 件）を流し直して通った。E2E 190・195・180 は替える前の WAR で流した（中身は同じで道の名前だけの違い）。替えた後の E2E は指揮役の全件の実行で確かめる。
- 承認済みの `code-generation-plan.md` 2節・9節と `unit-test-instructions.md` 3節のコマンドの道（`src/features/tables/reference/…`）は書き換えていない。流すときは `references/` と読み替える。

## 指揮役が行うこと（この担当は行っていない）

- Step 16 の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と E2E の全件、Step 17 のコミット・統合、Step 18 の文書と追跡。

## B4 の確かめと統合 — 指揮役の記録

- 依頼者の決定: フォルダ名 `references/`（`.gitignore` の `reference/` に当たるため）、E2E の補助 `support/referenceSetup.ts` の追加、担当が決めた細かい点を受け入れた。
- コミット: 491e57a（C1）・3a286ed（C2）・a4ea6bd（C3）・f63f2d4（C4）・c0d65a7（C5）。各区切りで、後の区切りの変更を外して `npm run typecheck` が通ることを確かめた。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の環境変数・`caffeinate -dims`）: 成功、27分48秒。バックエンドの単体 2,804 件・結合 1,652 件、画面 2,291 件、失敗 0。
- E2E の全件（`references/` に替えた後）: 277 件すべて通過（14.4 分。変更の前の 13.1 分の 1.5 倍の線 19.7 分の内）。流した 12:11〜12:53 に Sleep・DarkWake は無かった。

## 直しの Bolt B7（承認の場の Request Changes の直し。計画の Step 19〜37）— 開発担当の記録

前の節は書き換えていない。ブランチ `fix/261029-reference-lookup-cleanup-review`（先頭 `aa6883c`。Step 19 の記録のコミットとブランチの作成、E2E の出力の削除は指揮役が済ませた）。コミット・push・`./gradlew verify` の全体・`e2eTest` は行っていない（指揮役が行う）。

### 依頼者の決定

- **Step 30: B**。`bizCreateLarge`・`bizSearchWide` の `memory.peak` も 95% 未満に緩める方向。目標の緩和のため、この Bolt では README を 95% にせず、要件としての決定（理由と前の線を並べる記録）を Build and Test の質問で取る。それまでは本文と「今の基準の一覧」を 90% 未満にそろえ（一覧に `< 0.9` の 2 行を足した）、前の 1343 行の「95% 未満に緩めた」の注記を「緩めるかは未決で、Build and Test で要件として決める。それまでは 90% 未満」に直した。経緯は新しい履歴の項目 H69（`805db12` が要件の裏付けなく足した注記で、FR1.2・H66 と食い違う）。H54 は書き換えていない。
- **Step 28: A**（台本に確かめの文を足し、README に 1 行足す）。

### Step 19: 影響の範囲（検索の結果）

- 画面: `isRequiredColumn`・`validateValue`・`validateForm`・`validateOnBlur`・`tables.form.unselected`・`tables.form.required` を使うのは `formValidation.ts`（と test）・`formDiff.property.test.ts`・`messages.ts`・`BusinessDataFormPage.tsx`・`references/referenceField.ts`・`references/ReferenceSelectField.tsx`・`references/ReferenceColumnField.tsx`。直した後も既存のテストは1件も落ちなかった（見本の参照の項目はどれも `nullable` が真か `required` が真で、新しい規則で結果が変わらないため）。
- E2E: 180・190・195 は欄を `getByRole` の `name` で探す（Playwright の既定は部分一致のため、名前に必須の印が加わっても外れない）。E2E の注文の表（`support/businessDataDsl.ts`・`businessDataSchema.sql`・`support/referenceFixtures.ts`）の参照のカラムは、195 の状態 7・8（`blocked-required`）を除いて `nullable` が真で `required` が偽のため、印も「（選ばない）」も変わらない。状態 7・8 は入力不可の表示（`ReadonlyReferenceField`）で、この直しの部品を通らない。**E2E は直していない**（Step 26）。
- バックエンド: `referenceStep`・`warnReference`・`BusinessDataUnexpectedException` を使うテストは `BusinessDataWriteReferenceTest`・`BusinessDataWriteServiceTest`・`ReferenceLookupTest`・`BusinessDataReadServiceTest`。`ReferenceLookupTest` は `check`・`countState` の写さない失敗で `BusinessDataUnexpectedException` を期待するため、その形を保った。

### Step 20・21: U2 R-01（⑩ の想定外の診断の WARN）

- 型の置き方（計画の「選んだ方を書く」）: `ReferenceStepOutcome`（domain）にも確かめの結果の型（`ReferenceCheckResult`・`ReferenceCountState`、domain）にも場合を足さず、`BusinessDataWriteService` の中に口の中の結果の `sealed interface StepAttempt`（`Completed`・`Unexpected`）を置いた。口の外の `switch` が場合を尽くす形を保ち、domain の型を `switch` している既存の場所（`ReferenceWritePolicy` など）とテストを変えずに済むため。どちらも計画の「置き場は `businessdata.service` の中」に合う。
- 手がかりの運び方: `BusinessDataUnexpectedException`（`final`）に、値を含まない手がかり（`sqlState`・`vendorCode`・`exceptionClass`）を持つ作り方 `withDiagnosis`（パッケージの中だけ）と読み出しを足した。メッセージも原因も持たないことは変えていない。`ReferenceLookup` は書き込みの確かめの口（`check`・`countState`）の写さない失敗だけを `writeFailureOf` で手がかりつきにし、読み取りの API の経路（`failureOf`、検索・ラベル）は変えていない。値の欠け（`valueOf`）は手がかりの無い今の例外のまま。
- 起きた読み取り（`stage`）: 口の中で `check` は `CHECK`、`countState` は `COUNT_STATE` で包み（`atStage`）、手がかりと一緒に `StepAttempt.Unexpected` に写す。口の外で `warnReference` を1回だけ呼び（`reason` は文字 `UNEXPECTED`。`ReferenceFailureReason` には足していない）、今と同じく `BusinessDataUnexpectedException`（手がかりなし）を投げる。書き込みの口は借りず、監査も出さない。
- **計画との差（範囲を少し広げた）**: 更新前の行の写さない失敗（`updateStep` の `RowDetailResult.Failed` で `INVALID_VALUE`・`UNEXPECTED`）も同じ ⑩ の想定外のため、`stage=PREVIOUS_ROW` と手がかりで WARN を1行出すようにした（計画が挙げたのは `check`・`countState` の写さない失敗と値の欠けだけ）。ほかに ⑩ の中で投げられる手がかりの無い想定外（来ないはずの形）も、`stage` が分からなければキーを出さずに WARN を1行出す。既存の `unexpectedPreviousRow` のテストに WARN の確かめを足した。
- テスト（`BusinessDataWriteReferenceTest` に 3 件、既存 1 件に確かめを足した）: `INVALID_VALUE` の `check`（`stage=CHECK`、手がかり一致）・`UNEXPECTED` の `countState`（`stage=COUNT_STATE`、手がかり一致、後の `check` を流さない）・値の欠け（`stage=CHECK`、`sqlState`・`vendorCode`・`exceptionClass` のキーなし）。どれも WARN がちょうど1行・キーの並びが `purpose`・`stage`・`reason`（・`sqlState`・`vendorCode`・`exceptionClass`）・`budgetExceeded`、`purpose=REFERENCE`・`reason=UNEXPECTED`、本文とキーに送った値・`orders`・`dept` が無い、例外は `BusinessDataUnexpectedException`（メッセージ・原因なし）、書き込みの口 0 回・監査の出来事 0 件。
- 流した結果: `./gradlew :backend:test --tests '*BusinessDataWriteReferenceTest' --tests '*BusinessDataWriteServiceTest' --tests '*ReferenceLookupTest'` → 22・16・24 件、失敗 0（T-F2 と BR2.7 を含む）。`:backend:integrationTest --tests '*ReferenceWriteSecretLeakIT'`（colima の環境変数・`caffeinate -dims`）→ 1 件、失敗 0・SKIPPED 0。あわせて `--tests '*ArchitectureTest*' --tests '*BusinessData*'` の単体 228 件、失敗 0。`:backend:spotlessCheck`・`:backend:spotbugsGate` 通過（触れた 3 クラスの警告は直す前と同じ件数で、増えていない）。

### Step 22〜25: U3 R-01・R-02（必須の判定と欄への結び付け）

- `formValidation.ts` に `isRequiredInput`（参照の項目は `isRequiredColumn`、参照でない項目は `required === true`）を置き、`validateValue`（空の値の `REQUIRED`）・`validateForm`（作成で値が無いときの `REQUIRED`）と `BusinessDataFormPage.tsx` の `validateOnBlur` が同じ関数を呼ぶ形にして条件を1か所にした。`isRequiredColumn` は `references/referenceField.ts` のまま（`referenceField.ts` は `formValidation.ts` を読まず、import の循環は無い）。参照でない項目の判定と描画（`FieldInput`）は変えていない。
- `ReferenceSelectField.tsx`: `required` を `isRequiredColumn(column)` にし、印・`aria-required`・「（選ばない）」の出し分けと 0 件の必須の文を同じ値にそろえた。
- `LookupField.tsx`: 必須の印と欄の `aria-required` を足した。**計画との差**: 必須の値を新しい props で受けず、`clearable` の否定から決めた（呼ぶ側 `ReferenceColumnField.tsx` は `clearable={!isRequiredColumn(column)}` を渡しており、必須とクリアの出し分けが同じ判定の裏表のため。呼ぶ側のファイルを触らずに済む）。props の説明文にこの関係を書いた。
- R-02（F-1 の裏付け）: `LookupField.test.tsx` の 6 件で、`getByLabelText` が欄（`tables-lookup-value-c4`）を返す（`label` の `for` と欄の `id` が一致）、誤りのとき欄だけに `aria-invalid="true"` と誤りの文を指す `aria-describedby`、値が空のとき説明文を指す `aria-describedby`（`aria-invalid` なし）、値が入ると `aria-describedby` なし、ボタンには両方とも付かないことを確かめた。どれも通ったため、**部品の結び付けは直していない**（コメントを事実に合わせて直しただけ。`useFieldProps` は使っていない）。
- テストの件数: `formValidation.test.ts` 7 件（fast-check の性質 1 件を含む。失敗は無く、種の記録は無い）・`ReferenceSelectField.test.tsx` 4 件・`LookupField.test.tsx` 6 件・`BusinessDataFormPage.test.tsx` 4 件、合わせて 21 件を足した。欄から離れたときの誤りは `waitFor` で待って確かめた。直した既存のテストは無い。

### Step 27: frontend の手元の確かめ

- `npm run format:check`・`npm run lint`・`npm run lint:css`・`npm run typecheck`・`npm run license:check` すべて通過。`git diff --stat develop -- frontend/package.json frontend/package-lock.json` は空。
- `npx vitest run src/features/tables`: 41 ファイル・517 件、失敗 0。画面のテストの全件 `npx vitest run`（`caffeinate -dims`）: 238 ファイル・2,312 件、失敗 0（B4 の後の 2,291 件 ＋ この Bolt の 21 件）、57.9 秒。

### Step 28・29: U2 R-03・U4 R-04

- `perf/biz/make-biz-seed.sh` の最後（行の数の出力の後）に、`biz_refcreate` が 0 行でなければ止まる文を足した（PG は `DO` の塊の `RAISE EXCEPTION`、MySQL・MariaDB は 0 行でなければ 2 行を返す副問い合わせを1つの値として比べ `Subquery returns more than 1 row` で止まる形）。目印は `biz_refcreate_must_be_empty`。確かめの結果は U5 の `perf-docs-check-record.md` 12.3節（見本の PostgreSQL で 0 行のとき終了コード 0、1 行のとき 3、`bizperf` を消して片付けた）。MySQL・MariaDB の流し込みは Build and Test の試し走りで確かめる。`shellcheck` はこの PC に無い。
- `perf/README.md`: U2 の節に「流す前に毎回 0 行にする」の行、U4 の `--scan` の説明に終わりのコード 2 の対処の行を足した。道具（`perf/redact-k6-evidence.mjs`）は変えていない。

### Step 30〜33: U5 R-01・R-02・R-05

- 分け方の表・直し・確かめの結果（CB5・CB6・CB7・CB8・CB9〜CB12・CB22・Q1〜Q7、すべて合格）は U5 の `perf-docs-check-record.md` 12節に書いた。
- **計画との差・担当の判断（依頼者に確かめたい）**:
  1. 取り出した行は 73 行（計画の見込み 47 行より多い。正規表現は同じで、コードブロックの書式の `%` などにも当たった）。
  2. 一覧に無かった今の線として、`bizSearchWide`・`bizCreateLarge`（Step 30 の B のとおり < 0.9）に加えて、`groupPoolLimit`・`rolePoolLimit` の合否の回の `acquire` の `MAX` の最大 < 20 ms と、`userAdminPoolLimit` の上限に届いたことの条件（時間切れの累計の増分 1 以上、または `acquire` の最大 1,000 ms 以上）の 3 行を k6 の外の表に足した。出典の設計（Intent 261004-role-menu の group・role の `nfr-design/scalability-design.md` 2節、Intent 261003-user-admin-followup の FR4）の線で、後の Intent で変えた記録が無く、台本（`scenarios.js`）は判定しない（人が読む）ため、`memory.peak` と同じく一覧の「k6 の外の線」に当たると判断した。
  3. 決めかねて残した部分: 前の 994 行の試しの効き目の目安「2回とも 0.9 未満か」。推奨は、履歴へ移して効き目を今の線（0.95）で読む形にすること（前の線を本文に残さないという R-02 の趣旨に合うため）。ただし、試しは前の Intent の一度きりの比べで、今流す予定は無いため、本文に残しても害は小さい。
  4. VM の余裕（前の 1214 行）は、計画の推奨どおり本文を今の値で計算し直し（2048 + 2688 + 512 + 256 = 5,504 MiB、余り約 406 MiB）、元の 2304m の計算を履歴 H73 へ移した。U5 の `code-summary.md` 8節の推奨（本文には今の値だけ）とは、本文に今の値の計算を残す点で違う。
- 外の置き場は U5 の手順書の `~/.mastersmith-b6` ではなく `~/.mastersmith-b7`（権限 700）にした。確かめの後に消した。

### Step 34: 追跡

- U2: NFR1.32・NFR2.24、U3: AC1.1.1・AC1.5.1・NFR4.42、U4: AC4.1.4、U5: AC4.2.2・AC4.2.4・NFR8.84・AC4.3.1・AC4.3.2 の note に B7 の注記を足した。status は変えていない。

### 記録の検査

- `gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1` をこのファイル・U5 の `perf-docs-check-record.md`・U2〜U5 の `code-summary.md`・`perf/biz/make-biz-seed.sh` に流し、どれも終了コード 0（指摘 0 件）。JWT の形の検索もどれも 0 件。

### 依頼者の決定の後の直し（依頼者のメッセージ eac44a47）

- 上の Step 30〜33 の「依頼者に確かめたい」の 2・3 に答えが出た: 接続プールの3行は **A**（一覧に置いたまま）、前の 994 行の目安「2回とも 0.9 未満か」は **A**（履歴 H74 へ移し、本文は今の線 0.95 で読む）。`perf/README.md` を直し（R5 の範囲）、CB5・CB6〜CB12・CB22・Q1〜Q7 を流し直してすべて合格（U5 の `perf-docs-check-record.md` 12.5節）。R4 の2行のパッチは引き続き `git apply --cached --check` が通る。
