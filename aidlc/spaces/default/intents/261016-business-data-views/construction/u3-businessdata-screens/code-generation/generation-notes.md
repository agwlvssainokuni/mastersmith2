# 生成の記録 — U3 u3-businessdata-screens（Bolt B5、Step 1〜18）

計画（`code-generation-plan.md`）の本文は書き換えず、チェックボックスの印だけを付けた。計画に無い変更と計画と違う判断は 2節に書く。パスはリポジトリのルートからの相対。

## 1. Step ごとの結果

### Step 1: 作業の準備と走らせ方の確かめ（B5）

- `aidlc/` の記録のコミットは指揮役が `6815308` で済ませた（依頼の文のとおり）。作業の開始の時点で未コミットだったのは監査の記録の shard だけで、触れていない。
- `frontend/test-results/`（git の対象外の生成物）が残っていたため消した。`frontend/playwright-report/` は無かった。
- `develop` の先頭は `6815308`（B4 の統合 `04b0589` の後の記録のコミット）。B4 の統合の後の CI（run 39339950936）は、作業の開始の時点で実行中だった（結果は 4節）。
- `develop` から `feature/261016-business-data-views-b5` を作って移った。
- 走らせ方の確かめ（`unit-test-instructions.md` の 3.1）: 書き換える既存のテスト8ファイル（置き場の画面・置き場の API・登録・道・今の項目・その性質・LoginStateGate・ShellLayout）は変更の前に 8 ファイル・107 件がすべて通った。
- 変更の前の値（P-6）: `npm run build` の後の `check-bundle-size.mjs` の入口の値は 159.3 KB（gzip。`index-*.js` 152.9 KB と `useTranslation-*.js` 6.4 KB）、置き場の画面の chunk（`TablePlaceholderPage-*.js`）は 1.34 KB（gzip）。
- 1.4 の検索を生成の直前の `develop` で流し直した。当たりは計画の表と同じで、増えた当たりは無かった（`tableAccessApi.ts` も `tablePlaceholderPath` を読んでいたが、消すファイルのため扱いは変わらない）。

### Step 2〜3: 受け渡しの型と写しの関数

- `frontend/src/features/tables/businessDataTypes.ts`・`businessDataParse.ts`。列挙は `as const` の配列から型を作り、写しの関数が同じ配列で照らす。`lookup` は `FORM_PARTS` に入れない。
- サーバーの JSON の null の出し方（項目を出さないか null か）に頼らないよう、null を許す項目（`listFormat`・`width`・`restrictionReason`・`defaultValue`・`workRoleId`・行の `key`）は、項目が無いときも null として受ける。
- 値の組（主キー・行の値）は `Object.fromEntries` で作り、読むときは `Object.hasOwn` で確かめる（DSL のカラムの名前が `__proto__` などでも物の形を変えない）。
- 詳細の応答の `values` には password と BINARY のカラムが入らない（U1 の `ColumnAccess`）ため、書式の関数は `masked`・BINARY を値の有無より先に判定する。
- テスト: 例のテスト 28 件・性質ベースのテスト 4 件（任意の JSON で例外を投げない、任意の欄を任意の JSON に替えても例外を投げない、BIGINT・DECIMAL の桁、任意のカラム名と値をそのまま写す）。fast-check の失敗は無かった（種の記録は不要だった）。

### Step 4〜5: API の呼び出し・記憶・ログインの範囲の知らせ

- `businessDataApi.ts`（読み取りの3本と、本文の上限の確かめ `postWithinLimit`）・`businessDataStore.ts`・`businessDataLimits.ts`・`frontend/src/app/login-state/loginScope.ts`、`LoginStateGate.tsx` の知らせ。
- 本文の上限を超えたときは送らずに `{ kind: 'response', status: 413, code: 'PAYLOAD_TOO_LARGE' }` を投げる（サーバーの 413 と同じ形）。
- 記憶の口は `readTableEntry`・`saveDefinition`・`saveListState`・`putRowKey`・`peekRowKey`・`forgetDefinition`・`clearBusinessDataStore`・`currentStoreGeneration`。作業ロールの変化の判定は「最後に書いたときの roleId」と比べ、読む・書く口の入口で行う。
- D9: A のとおり、別のタブで利用者が替わる場合は既知の制約のまま（`features/auth` の口は足していない）。
- LoginStateGate の知らせは、最初の答えでは鳴らさず、`loggedIn` が変わったときだけ鳴らす（同じログインのまま氏名や表示の設定が変わっても鳴らさない）。
- テスト: API 9 件（上限ちょうどと +1 バイトを ASCII・サロゲートの組・組になっていないサロゲート・制御の文字の4つで）、記憶 10 件、知らせ 3 件、LoginStateGate の足し 2 件（ファイルは 17 件）。

### Step 6〜7: 純粋な関数

- `valueFormat.ts`・`rowLabel.ts`・`listPaging.ts`・`problemMessage.ts`（読み取りの code と `needsDefinitionReload`）に加えて、`searchConditions.ts`（検索の入力から送る条件を作る・画面で確かめる）と `listState.ts`（一覧の既定の状態と、定義が変わったときの直し）を足した（2節の N-1）。
- 行のラベルの部分は、読み上げの名前の計算と同じく空白の並びを1つにまとめて前後を切り、空白だけの部分を「（空）」にする（2節の N-2）。
- テスト: 例のテスト 38 件（書式 5・行のラベル 6・ページ 5・文と読み直し 16・検索の条件 6）と性質ベースのテスト 3 件（桁を失わない、任意の文字列をそのまま返す、行のリンクの見える文字がリンクの名前と行の操作の名前に必ず含まれる、など）。

### Step 8〜9: 道・今の項目と共通の部品

- `tablePlaceholderPath.ts` に `TABLE_PATHS`・`TableScreen`・`toTablePath`・`isTablePath` を足し、`toTablePlaceholderPath` は `toTablePath('list', …)` を呼ぶ形にした（今の形は保つ）。`buildNavSections.ts` の今の項目の判定を `isTablePath` にした。
- `src/shared/scroll-region/`・`page-size/`・`null-value/`（CSS を含む）。
- テスト: 道・今の項目の書き換えと足しを流し、`ShellLayout.test.tsx` は書き換えずに通った。共通の部品は 5〜6 件ずつ（vitest-axe を1件ずつ）。

### Step 10: フック

- `useTableDefinition.ts`・`useRowSearch.ts`・`useRowDetail.ts` と、3つが共有する小さな口 `tableHookSupport.ts`（作業ロールの読み込みが済んだか、403 の後の読み直し、道の引数を安定させる `useTableRefFromSearch`）。
- 定義と一覧の状態は記憶を正とし、描くたびに記憶を読む（記憶に今の作業ロールの定義があれば要求を送らない）。要求の中身は鍵の文字列にまとめ、効果は鍵が変わったときだけ要求を送る（操作1回で一覧の API は1本）。
- 画面に結べない 400（`field` が空）と 413 は、表を消さずに一覧の上に出す（`notice`。security-design.md の 6節）。
- テスト: 定義 6 件・一覧 6 件・詳細 5 件・一覧の状態 4 件。

### Step 11〜12: 画面と部品

- 画面 `BusinessDataListPage.tsx`・`BusinessDataDetailPage.tsx`、部品 `SearchPanel.tsx`・`BusinessDataTable.tsx`・`RowActionsMenu.tsx`・`RestrictionNotice.tsx`・`BusinessDataProblemView.tsx`、文言 `messages.ts`、登録 `registration.ts`、`tablesText.ts`（書式の決まった文と表示名の選び方）、CSS（`tablesCommon.css` と部品ごと）。
- 置き場の6ファイル（`TablePlaceholderPage.tsx`・`.css`・`.test.tsx`、`useTableAccess.ts`、`tableAccessApi.ts`・`.test.ts`）を消した（A1）。`frontend/e2e/support/navTreeFixtures.ts` の型の import と `tableAccessWire` を消した。`frontend/src/features/README.md` を直した。
- D10: A のとおり、`frontend/eslint.config.js` に `no-console`（error）の決まりを足した（`src/features/tables/**` と U3 の `src/shared/` の3つの部品。テストのファイルを除く）。試しのファイルで当たることを確かめた。
- テスト: 画面と部品の 9 ファイル・54 件（一覧 11・詳細 8・表 9・検索 6・行の操作 5・理由 5・開けないとき 5・登録 3・文言 2）。必須の項目（U3-NFR7.9）は3節の表のとおり。

### Step 13: E2E の補助と測りの先行

- `frontend/e2e/support/businessDataFixtures.ts`・`businessDataApiRoute.ts`・`businessDataMeasure.ts`、`secretValues.ts` の `SECRET_KINDS` の1つの定数化と `businessValue`・目印 `BUSINESS_VALUE_MARK`、D5: A の U4 の測りの見本の値（`測定 01〜25`・`sokutei.01〜25@example.com`）。単体のテスト4ファイル・13 件。
- 測りの先行（D7: A）: `npx playwright test e2e/185-business-data-accessibility.e2e.ts -g measure`。1回目は `detailFocused` が測れなかった（フォーカスの移動は DOM の変化ではなく、判定が見直されなかった。2節の N-7）。直した後の2回目（2026-10-20 09:03）の値（ミリ秒、各 5 回）:

| 判定 | 目標 | 中央値 | 最大 | 1回目 | 判定 | 塗りまで（参考）中央値 | 塗りまで 最大 |
|---|---|---|---|---|---|---|---|
| listShown20（メニューから一覧を開く） | 500 | 54.1 | 58.6 | 58.6 | Met | 78.4 | 86.4 |
| listShown100（件数を 100 に） | 1,000 | 72.4 | 88.3 | 71.7 | Met | 179.7 | 192.8 |
| detailFocused（100 カラム・10,000 字の詳細） | 500 | 46.6 | 47.5 | 46.6 | Met | 62.5 | 63.2 |
| listShown100Back（詳細から 100 件の一覧へ戻る） | 1,000 | 176.1 | 177.5 | 176.9 | Met | 226.7 | 229.5 |

- どの判定も目標を下回り、「塗りまで」も目標を下回った。D7: A の「止めて諮る」には当たらない。流した後の `pmset -g log` に Sleep・DarkWake は無かった。

### Step 14: 160・170 の書き換えと 185

- 160: 末端のテーブルを業務データの一覧で開き、業務データの API を `businessDataApiRoute` で差し替えた（末端のテーブルは `leafDefinition`、ほかのテーブルの定義は 403）。差し替えた失敗の「Failed to load resource:」を除く道に、業務データの API の道を足した。状態の名前を `sidebar-5-levels-and-list`・`list-forbidden` に、測りの判定を `tableListShown` に替えた。
- 170: T1 の定義と一覧を見本で返し、T2 の定義に 403 を返す差し替えを、利用者のログインの前に張った。step 5 に一覧の件数の確かめを足し、step 7 の文を業務データの 403 の文にした。step 8 の `/api/me/table-access` の形の比べを外した（2節の N-9）。
- `frameApiRoute.ts` から `tableAccess` を外し、`u7Measure.ts` の判定 `placeholderShown` を `tableListShown`（引数 `listHeading`）に替えた。
- 185: 表示の組の 20 組を1組1件、200% の2件（640×360。既定の組と dark・lg）、測りの1件（計 23 件）。各件は一覧（100 カラム × 20 行）・検索の誤り・右端の行の操作のメニューと収まり・読み込み中・詳細（10,000 字）・空の一覧・403 を回って axe を流す（2節の N-10）。
- 1回目の 160・170・185 で、375px・360px・768px と 200% の件が画面全体の横のはみ出しで落ちた（17 件）。原因は、表のセルの中の読み上げだけの見えない文字（NULL・伏せた値）が絶対配置で、包む箱が横のスクロールの枠の外にあったこと（2節の N-6）。直した後は 160・170・185 の 50 件がすべて通った（4.2 分）。
- `README.md` の E2E の表（160・170 の説明、185 の行）と、160・170 の注記、「テーブルの置き場」の段落を業務データの画面に直した。

### Step 15〜17

4節に書く。

## 2. 計画に無い判断・計画との差

| # | 差 | 理由 |
|---|---|---|
| N-1 | 計画の 1.1 の表に無いファイルを足した: `searchConditions.ts`・`listState.ts`・`tableHookSupport.ts`・`tablesText.ts`・`tablesCommon.css`・`BusinessDataListPage.css`・`BusinessDataDetailPage.css`・`BusinessDataProblemView.css`・`SearchPanel.css`・`BusinessDataTable.css` と、テストの補助 `src/features/tables/testing/`（`businessDataSamples.ts`・`sampleDefinition.ts`・`valueTexts.ts`・`tableProviders.tsx`・`renderTablesScreen.tsx`） | 計画の部品（L5〜L7）の中身を、純粋な関数・フックの共有の口・テストの補助に分けた。部品の境界と import の向きは変えていない |
| N-2 | 行のラベルの各部分を、読み上げの名前と同じく空白の並びを1つにまとめて前後を切り、空白だけの部分を「（空）」にした（機能設計 D9 は「つないだ結果が空白だけなら（空）」） | 見える文字（先頭の部分）が、つないだラベルと `rowActionsName`（前後を切る）に必ず含まれるようにするため（D29 の性質）。セルの値の DOM の文字は切らない |
| N-3 | 検索の既定の値（`search.default`）は、RANGE では下限、CHOICE では選択肢の1つとして入れた | DSL の `search.default` は1つの文字のため（設計の文書に RANGE・CHOICE の扱いの記述が無い） |
| N-4 | 検索の領域は、すべての項目が折りたたみの欄にあるとき（`collapsed` がすべて真）だけ閉じて始める | AC1.2.7 の「collapsed のとき閉じて始める」を、項目ごとの `collapsed` から領域の開閉に読み替えた |
| N-5 | `writeConfigured` が false なら、許す操作によらず書き込みの設定が無い旨の Alert を出す | 機能設計 D13 の文のまま。読み取りだけの作業ロールでも出るため、要るなら B6 で「書き込みの操作が許されるときだけ」に絞るかを依頼者に確かめる（3節） |
| N-6 | `src/shared/scroll-region/ScrollRegion.css` と `tablesCommon.css` の `.tables-page` に `position: relative` を足した | E2E の狭い幅で、表のセルの中の読み上げだけの見えない文字（絶対配置）が枠のスクロールの外に置かれ、画面全体が横にはみ出した。包む箱を枠にして直した |
| N-7 | `businessDataMeasure.ts` の判定の見直しを、DOM の変化に加えて `focusin` でも行う | `detailFocused` はフォーカスの移動で真になるが、フォーカスの移動は DOM の変化ではなく、判定が見直されずに測れなかった。物差し（判定の後の次の rAF）は変えていない |
| N-8 | `ScrollRegion.tsx` の `tabIndex={0}` に、oxlint の `jsx-a11y/no-noninteractive-tabindex` の1行の除外を付けた | `role="region"` と名前つきの枠をキーボードで動かせるようにするため（D27、axe の `scrollable-region-focusable`）。`frontend/src/features/roletransfer/TransferCheckTable.tsx` と同じ形で、除外はこの1行だけ。規則の設定は変えていない。計画 3節の「緩める変更は無い」に当たるかを依頼者に確かめる（3節） |
| N-9 | 170 の step 8 から `/api/me/table-access` の形の比べを外した（計画の Step 14 は「業務データの一覧の画面（型の付いた見本で応答を差し替える）に書き換える」） | E2E の環境に対象DB が無く、業務データの本物の応答は 170 では得られない。業務データの見本と本物の応答の形の比べは 180（B6）の `shapeCompare` が受け持つ（NFR 設計 L10） |
| N-10 | 185 の「サイドバーを展開した状態」は、既定の幅でサイドバーが開いたままの一覧の状態で axe を流す形にした（サイドバーの開閉は押していない）。検索の誤りの状態は、見本の検索の項目が文字の型のため、1,001 字を入れた長さの誤り（TOO_LONG）で作った | 185 の見本のメニューは1項目だけで入れ子が無い。サイドバーの開閉と入れ子の確かめは 160 が受け持つ |
| N-11 | 一覧の状態（`ListState`）に、詳細から戻ったときにフォーカスを戻す行の番号と押した要素（`returnFocus`）を足した | interaction-spec.md の「詳細から戻ったときは、移る前に押したリンクかメニューのトリガーへフォーカスを戻す」のため。行の値を持たない |
| N-12 | 詳細の「一覧へ戻る」に `data-testid="tables-back-to-list"` を付けた | 185 の測り（listShown100Back）の測り始めの選択子のため |
| N-13 | `businessDataApiRoute.ts` に一覧の答えを止める口 `holdSearch()` を足した | 185 で読み込み中の状態（`aria-busy`）の axe を流すため |
| N-14 | `businessDataStore.ts` に `forgetDefinition` を足した | F6 の読み直しで、記憶の古い定義を使わずに今の定義の識別を添えて読み直すため |
| N-15 | 表（make-you-chic-ui の Table）の列の鍵は `c<n>`（行の操作は `actions`）、行の id は `r<n>` にした | Table は列の鍵と行の id を `data-testid`（`table-cell-r<n>-c<n>` など）に使うため、カラム名・主キーを DOM に入れない（D8・U4 の R-02） |

## 3. 依頼者に確かめたいこと

- N-5: `writeConfigured` が false のとき、読み取りだけの作業ロールにも書き込みの設定が無い旨を出す（D13 の文のまま）でよいか。
- N-8: `ScrollRegion` の1行の lint の除外（roletransfer と同じ形）を、計画 3節の「緩める変更は無い」の外として受け入れてよいか。
- コミットの区切り（7節の C3 と C4）: 4節の 4.5 のとおり、計画の C3 の時点では 160・170 の型の検査が通らないため、E2E の変更を C3 に入れ、C4 を `README.md` だけにする案にした。

## 4. 検査・E2E・4.14 の試し

### 4.1 Step 15: 1コマンドの検査（B5）

- `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）: BUILD SUCCESSFUL、**20分27秒**（2026-10-20 09:19:41〜09:40:09。B4 の後は 21分4秒。CI の上限の7割の約 42 分の内）。流した後の `pmset -g log` に Sleep・DarkWake は無い。
- バックエンド: 3,970 件、失敗 0、SKIPPED 9（どれも別の DB の種類だけに当たる結合テストの前提での飛ばし。対象DB のテストはコンテナで流れた）。JaCoCo の全体の合計は 行 97.64%・分岐 91.23%（バックエンドの本体のソースには触れていない）。
- フロントエンド: 217 ファイル・1,821 件がすべて通過。カバレッジの全体の合計は 行 95.24%・分岐 90.32%（B4 の後 行 95.37%・分岐 90.32%。下限 80%・70% を保つ）。`src/features/tables/` は 行 94.34%・分岐 90.03%、`src/app/login-state/` は 行 100%・分岐 93.33%。下限・対象・除外は変えていない。
- U3 のテスト（unit-test-instructions.md の 3.4 のうち B5 の分）: 36 ファイル・251 件。
- コードの検索（U3-NFR4.2・4.20）: `src/features/tables/` と `src/shared/scroll-region`・`page-size`・`null-value` の本体（テストのファイルを除く）で、`console`・`localStorage`・`sessionStorage`・`indexedDB`・`dangerouslySetInnerHTML`・`document.title` の使用は 0 件（説明文の中の語だけ）。
- 初回の JavaScript（P-6）: 入口の値は 159.3 KB → **162.0 KB**（gzip。`index-*.js` 152.9 → 155.6 KB、`useTranslation-*.js` 6.4 KB のまま）。増えた分の多くは、登録で静的に読む業務データの画面の文言（日英）と見られる。画面の chunk（gzip）: `BusinessDataListPage-*.js` 6.01 KB、`BusinessDataDetailPage-*.js` 1.47 KB、共有の `useTableDefinition-*.js` 5.16 KB（置き場の画面の chunk 1.34 KB は無くなった）。`check-bundle-size.mjs` は目安 500 KB の内で警告なし。

### 4.2 Step 16: E2E の全件（4.13）

- Mailpit が動いていることを確かめ、`caffeinate -dims ./gradlew e2eTest`: **255 件すべて通過**（json の報告の stats は expected 255・unexpected 0・flaky 0）。Playwright の表示で 13.4 分（duration 806 秒＝13分26秒）、Gradle で 13分28秒（2026-10-20 09:40:51〜09:54:18）。基準（232 件・約 10分35秒）から +23 件（185 の 23 件）、時間は約 1.27 倍で、1.5 倍（約 15分53秒）の内。U3-NFR7.8 の「諮る」には当たらない。
- ファイルごとの時間（秒）: 185 は 161.7、160 は 79.3、170 は 2.9（ほかは前と同じ程度。120 162.3・140 92.0・080 85.7 など）。
- 秘密の確かめ: 見つかった件数 0（値の種類 8。json の報告 1・添付 305）。流した後の `pmset -g log` に Sleep・DarkWake は無い。
- D1: A のとおり、見本の PG はまだ前提にしていない（15432 は開けていない、REVOKE はしていない）。

### 4.3 Step 17: axe-core 4.14 の試し（作業フォルダの中だけ。統合しない）

- 試しの前の `frontend/package.json`・`frontend/package-lock.json` の SHA-1: `dff50fde…`・`b16f0773…`。`npm view axe-core version` は 4.14.0（4 系の最新。U4 の B3 の試しと同じ版）。
- `ignore-scripts=true` のまま `npm install --save-dev axe-core@4.14.0`: `node_modules/axe-core` は 4.14.0・`license` MPL-2.0・`hasInstallScript` 無し。`hasInstallScript` の付いた項目は前と同じ `node_modules/fsevents` の1件。`npm ls axe-core` は 4.14.0 の1つ（vitest-axe の分は deduped）。差は2ファイル（5 行の追加・5 行の削除）。
- E2E は U4 の試しと違い、`expect.soft` に替えずにそのまま流した（違反が出れば失敗として数える）: `caffeinate -dims npx playwright test` で **255 件すべて通過**（12.5 分）、**どの規則の違反も 0 件**。秘密の確かめも 0 件。
- 規則 `label-content-name-mismatch` の要素の数（json の報告の注記 `axe-nodes` 1,237 件から、スクラッチの台本で集計。台本はリポジトリに足していない）:

| 区分・要素 | passes | incomplete | violations | ファイル |
|---|---|---|---|---|
| U3 の行の操作のトリガー（owner `tables-row-actions-*`、持ち主 U3） | 3,160 | 0 | 0 | 160（1,400）・185（1,760） |
| U3 の行のリンク（owner `tables-row-link-*`、持ち主 U3） | 2,440 | 0 | 0 | 160（1,400）・185（1,040） |
| C1 利用者の行の操作 | 882 | 0 | 0 | 120 |
| C3 ロールの行の操作 | 129 | 0 | 0 | 140 |
| C4 トップバーのトリガー（持ち主 make-you-chic-ui） | 334 | 638 | 0 | passes は 080・100・130・185（185 は2語の氏名で 154）、incomplete は 060・120・135・140・160（1語の氏名） |
| C5 作業ロールのトリガー | 972 | 0 | 0 | 060・080・100・120・130・135・140・160・185 |
| C6 作業ロールの項目 | 176 | 0 | 0 | 160 |
| 区分に当たらない要素（鍵の owner が無いもの） | 22 | 0 | 0 | 185 |

- U3 の行の操作のトリガーと行のリンクは、すべて passes に入った。B7 の passes の確かめの対象に足す候補として渡す（D6: A）。違反は U3 の部品にも make-you-chic-ui の部品にも無く、新しい依頼の文面は作っていない。
- 片付け: `git checkout -- frontend/package.json frontend/package-lock.json` の後、`./gradlew frontendInstall --rerun-tasks`（`npm ci`、`ignore-scripts` のまま）。`npm ls axe-core` は 4.13.0 の1つ。`git diff --exit-code frontend/package.json frontend/package-lock.json` は差なし（SHA-1 も試しの前と同じ）。`vendor/` の差も無い。型の検査と `src/features/tables` の 167 件を流し直して通った。

### 4.4 B4 の統合の後の CI

- run 39339950936（`04b0589`）: 1回目の試行で success（`./gradlew verify` が success、リリースの添付は skipped）。

### 4.5 コミットの区切り（Step 19 の提案の材料）

計画の 7節の区切りで、区切りごとに型の検査（`tsc`）と lint（`eslint`・`oxlint`）が通るかを、スクラッチに書き出した `HEAD` の写しに区切りの順に重ねて確かめた（コミットはしていない）。

| 区切り | 中身 | 型の検査と lint |
|---|---|---|
| C1 | `frontend/src/app/navigation/`（`tablePlaceholderPath.ts`・`buildNavSections.ts` とテスト）、`frontend/src/app/login-state/`（`loginScope.ts`・`LoginStateGate.tsx` とテスト） | 通る |
| C2 | `frontend/src/shared/scroll-region/`・`page-size/`・`null-value/` | 通る |
| 計画の C3（features だけ） | `frontend/src/features/tables/`（新しいファイル・置き場の6ファイルの削除）、`frontend/src/features/README.md`、`frontend/eslint.config.js`、`frontend/e2e/support/navTreeFixtures.ts` | **通らない**（160・170 がまだ `tableAccessWire` を読む） |
| 案の C3 | 上に、`frontend/e2e/` の変更のすべて（`support/` の新しい補助と単体のテスト、`secretValues.ts`・`frameApiRoute.ts`・`u7Measure.ts`、160・170 の書き換え、185）を足す | 通る |
| 案の C4 | `README.md` | 通る（最後の形は作業フォルダと同じ） |
