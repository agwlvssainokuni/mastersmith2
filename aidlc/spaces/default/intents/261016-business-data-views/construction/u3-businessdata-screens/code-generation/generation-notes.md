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

## 5. B6（Step 21〜）

### 5.1 B5 の承認の場で依頼者が決めたこと（B6 で扱う）

- D1〜D10: すべて A（計画の 10節のとおり）。
- N-8: 受け入れる（`ScrollRegion` の1行の lint の除外）。
- N-5: B6 で絞る。書き込みの設定が無い旨（`writeConfigured` が false）の知らせは、書き込みの操作（`CREATE`・`UPDATE`・`DELETE` のどれか）が `operations` に許される人にだけ出す。**機能設計 D13（`writeConfigured` が false なら出す）との差**として記録する（設計の文書は書き換えない）。直すのは B6 の画面の Step（Step 32 の前後）。
- N-3・N-4: B5 の読み方のまま。
- レビュー: B7 の後に U3 の全体を1回で行う。B6 の Step の「記録とレビュー」（Step 37）は記録だけにする。

### 5.2 Step 21: 作業の準備と走らせ方の確かめ（B6）

- `aidlc/` の下の未コミットの変更は、監査の記録の shard（`audit/sakura-local-4e42a93f87ce.md`）だけ。触れていない（コミットは指揮役が依頼者に諮る）。
- git の対象外の生成物 `frontend/test-results/` が残っていたため消した。`frontend/playwright-report/` は無かった。
- `develop` の先頭は `1152202`（B5 の統合）。B5 の統合の後の CI（run 39375104956、試行 1）は、作業の開始の時点（2026-10-20 14:30 ごろ）で実行中だった。結果は B6 の統合の前に試行ごとに確かめる。B4 の統合の後の CI（run 39339950936）は試行 1 で success。
- `develop` から `feature/261016-business-data-views-b6` を作って移った。
- 走らせ方の確かめ（`unit-test-instructions.md` の 3.1 の B6）: roleadmin の3ファイル・作業ロールの2ファイル・`BusinessNavigationProvider.test.tsx`・`src/features/tables` の **30 ファイル・221 件**がすべて通った。
- 変更の前の値（P-6）: `npm run build` の後の `check-bundle-size.mjs` の入口は **162.0 KB**（gzip。`index-*.js` 155.6 KB・`useTranslation-*.js` 6.4 KB。B5 の後と同じ）。画面の chunk の gzip（`gzip -c | wc -c` のバイト数）: `BusinessDataListPage-*.js` 6,192、`BusinessDataDetailPage-*.js` 1,545、`useTableDefinition-*.js` 5,305。B5 の記録（6.01 KB・1.47 KB・5.16 KB）は測り方（圧縮の水準・単位）が違うため、B6 の後もこの測り方（`gzip -c` の既定の水準のバイト数）で比べる。

### 5.3 Step 22: 上乗せの compose（途中で止めた）

- `docker/e2e/compose.targetdb-port.yaml` を書いた（ライセンスヘッダー、`targetdb-postgres` に `127.0.0.1:${E2E_TARGETDB_PORT:-15432}:5432` を足すだけ。開け方・閉じ方と、開けた直後の確かめの要ることを説明文に書いた）。`compose.yaml` は変えていない。
- 読み取りの確かめ（副作用なし）:
  - `docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres config --quiet` は通った。`config --format json` の `targetdb-postgres` の `ports` だけを取り出して、`127.0.0.1:15432→5432/tcp` になること、`app` の `ports` は変わらないこと（`127.0.0.1:8080`）を見た（環境変数の値は表示していない）。
  - `--dry-run up -d targetdb-postgres` は、`mastersmith-targetdb-postgres-1` の作り直し（Recreate）だけを示した。アプリと Mailpit は対象にならない（`app` に `depends_on` は無い）。流した後のコンテナの状態は変わっていない（`targetdb-postgres` は Up 12 days・`5432/tcp`）。
  - 見本の PG のイメージは `postgres:18.6@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722`（`compose.yaml` と同じ）。PC に `psql` は無いため、開けた直後の確かめは同じイメージ・digest の `docker run --rm` の一時の psql で行う。
  - `pg_hba.conf` は基盤の設計 2.2 の記録と同じ（`127.0.0.1/32`・`::1/128`・ソケットが `trust`、最後に `host all all all scram-sha-256`）。
  - PC の 15432 は空き（待ち受け無し）。
  - 見本の PG の今の状態（読み取り）: `has_database_privilege('public','business','TEMP')` は `t`（まだ取り上げていない）、`datacl` は既定（null）、`mastersmith_writer` は 0 件、`e2e_biz_%` のスキーマと `mastersmith_e2e_%` のロールは 0 件。
- 依頼者の承認（指揮役の伝達）を得て、作り直しの前で一度止めた（作り直しの前に報告する依頼のため）。承認の後に次を行った。
- 作り直し（2026-10-20 14:58:29 JST）: `docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres`。作り直したのは `mastersmith-targetdb-postgres-1` だけで、ボリュームは残った。`pg_isready` は約 1 秒で応答した。公開は `127.0.0.1:15432->5432/tcp`。
- PC から届くか: `nc -z -w 3 127.0.0.1 15432` は成功した。
- 誤ったパスワードの接続（4.4 の R-02）: 同じイメージ・digest の `docker run --rm --network host` の一時の psql で、乱数の誤ったパスワード（表示しない）を使い、`mastersmith_reader` として `127.0.0.1:15432` に入った。**終了コード 2、`FATAL: password authentication failed`** で拒まれた。scram に当たり、trust ではない。1回目の試しは、パスワードの変数を `PGPASSWORD` の名前で渡していなかった。このときは `fe_sendauth: no password supplied` で終わった。サーバーがパスワードを求めたため trust ではないと分かったが、確かめとしては使わずにやり直した。
- 配備したアプリ（4.4 の R-05）: 作り直しの後、`/actuator/health` は 200、コンテナは healthy。作り直しの後のアプリのログは 0 行（WARN・ERROR は 0 件）で、この間に対象DB への要求は無かった。**業務データの一覧の画面・API の戻りは未確認**（依頼者の決定で画面では確かめない。R-05）。

### 5.4 Step 23: 見本の PG の TEMPORARY の取り上げ（A5）

- **共有の見本の PG（配備したアプリも使う）を変える操作**。依頼者の承認を得て、2026-10-20T05:59:28Z に流した。
- SQL: `REVOKE TEMPORARY ON DATABASE business FROM PUBLIC;`。`docker/targetdb/postgres/03-writer-account.sh` の最後の文（`SELECT format('REVOKE TEMPORARY ON DATABASE %I FROM PUBLIC', current_database()) \gexec`）を DB `business` で展開した形と同じ。README の戻し方（`GRANT TEMPORARY ON DATABASE business TO PUBLIC`）と対になる。
- 流し方: `docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -v ON_ERROR_STOP=1`（コンテナの中のソケット。パスワードはプロセスに入れていない）。結果は `REVOKE`、終了コード 0。
- 確かめ: `has_database_privilege` の (PUBLIC・TEMP, mastersmith_reader・TEMP, mastersmith_reader・CONNECT) は **`f|f|t`**（期待どおり）。前の値は PUBLIC の TEMP が `t`、`datacl` は既定（null）。
- 後の段（U2 の Deployment Execution）でこの見本の PG に `03-writer-account.sh` を流しても、同じ取り上げを重ねるだけで結果は変わらない（何度流しても同じ結果になる文）。

### 5.5 Step 24: E2E のタスクと Playwright の設定

- `build.gradle.kts`: `e2eTest` の `doFirst` に、Mailpit の確かめの後で、見本の PG の前提の確かめ (a)〜(d)（`checkE2eTargetDb`）・前の残りの片付け（`cleanupE2eTargetDbLeftovers`）・準備（`prepareE2eTargetDb`）を足し、作った値を `Exec` の `environment` で渡す。`-Pe2e.files`（カンマ区切り。無ければ `e2e` の全件）。`finalizedBy` の片付けのタスク `e2eTargetDbCleanup`（この実行の名前だけで、接続を切る → `DROP OWNED BY` → `DROP ROLE` → `DROP SCHEMA … CASCADE`。消した件数と接頭辞の残りの件数をログに出し、残りがあれば失敗）。
  - 名前（スキーマ・2つのロール）は同じビルドの中の値（スクリプトの中の `e2eTargetDbRun`）で片付けへ渡し、パスワードは渡さない（R-04 (1)）。
  - psql は `docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -X -q -t -A -U target_admin -d business -v ON_ERROR_STOP=1 -v VERBOSITY=sqlstate`（準備は `-1`）。値は標準入力の `\set` で渡し、argv に出さない（R-03）。失敗のときは段の名前・終了コード・SQLSTATE の件数と一覧だけを出し、標準エラー出力は出さない（D8: A）。
  - 準備の後に、作った書き込みのロールに `TEMPORARY` が無いことを確かめる（`has_database_privilege`。cicd-pipeline.md 3.2 の (c) の後半）。
  - (d) は、見本の PG のコンテナのイメージ（`docker inspect` の `Config.Image`。digest つき）の一時の psql を `docker run --rm --network host` で流し、`target_admin` へ乱数の誤ったパスワード（環境変数 `PGPASSWORD`。表示しない）で入る。終了コードが 0 でなく `password authentication failed` が出れば拒まれたとみなす。
- `frontend/e2e/support/businessDataSchema.sql`: 表 `e2e_item`（`item_code` varchar(20) 主キー・`item_name` varchar(50) NOT NULL・`quantity` integer・`note` text）と架空の行 25 件（品目の名前に目印 `E2E-BIZ-MARK`、5 行ごとに備考を NULL）。
- `frontend/playwright.config.ts`: 対象DB の 10 の環境変数が無ければ「`./gradlew e2eTest` から流す」旨の誤りで止める（Q7: A）。`SECRET_ENV_NAMES` に一時の2つのロールのユーザー名とパスワードの4つと、接続先 `E2E_TARGETDB_ENDPOINT`（`<ホスト>:<番号>`。設定の読み込みで組み立てる）を足した（3.3 の「接続先を探すか」は探す側に決めた）。
- 確かめ（Step 24 の中）:
  - 満たした形: `./gradlew e2eTest -Pe2e.files=e2e/010-skeleton.e2e.ts` が通り、準備 98〜113 ms、片付けの後の接頭辞の残り 0（スキーマ 1・ロール 2 を消した）。
  - (a) を満たさない形: `DOCKER_HOST` を存在しないソケットにして流し、起動の手順を示して止まった（片付けは「作ったものは無い」）。
  - (b) を満たさない形: `E2E_TARGETDB_PORT=15433`（開いていない）で流し、上乗せの起動の手順を示して止まった。
  - (d) を満たさない形: 見本の PG と同じイメージの使い捨てのコンテナ（`POSTGRES_HOST_AUTH_METHOD=trust`、`127.0.0.1:15433`、名前 `mastersmith-e2e-trust-probe`。確かめの後に止めて消した）に `E2E_TARGETDB_PORT=15433` で向けて流し、「誤ったパスワードの接続が拒まれませんでした」で止まった。共有の見本の PG には触れていない。
  - **(c) を満たさない形は流していない**（共有の見本の PG の PUBLIC に `TEMPORARY` を戻す操作が要り、依頼の範囲の外のため）。(c) の満たした形（`temp=false`）は毎回通っている。依頼者に確かめる（7節）。

### 5.6 Step 25: 040 の書き換え

- 照合の期待を `dsl-warning-group-SCHEMA_MISMATCH`（「スキーマの名前の違い」）に替え、説明（21行）を「E2E の対象DB は見本の PG の実行のスキーマで、DSL のスキーマ `e2e` と違う」に直し、PG の読み取りが失敗すると `TARGET_UNAVAILABLE` になり落ちる旨を足した。`./gradlew e2eTest -Pe2e.files=e2e/040-dsl-admin.e2e.ts` で通った。

### 5.7 Step 26〜33: 書き込みの写し・API・結果の分け方・純粋な関数・共通の部品・画面

- 写しの関数: 詳細のカラムのうち入力にするもの（`editableOnCreate` か `editableOnUpdate`）に `formPart`・`required`・`nullable` が無ければ形の崩れ（`hasWriteMaterial`）。`lookup`・知らない `formPart` は B5 から形の崩れ。作成の応答の `key` は `parseCreatedKey`（無い・空・形が違えば null）。
- `businessDataApi.ts`: `createRow`（POST `/api/tables/rows`、4 MiB）・`updateRow`（PATCH 同じ道、4 MiB）・`deleteRow`（POST `/api/tables/rows/delete`、64 KiB）。例外を投げず `writeOutcome` の3つで返す。上限を超えたら送らずに「保存されていない」（413）。
- `writeOutcome.ts`: `NOT_SAVED_CODES`（code と状態コードの 15 組）に当たるときだけ「保存されていない」。ほかは「分からない」。成功は期待の状態（作成 201・更新と削除 200）かつ content-type が JSON（`application/json` か `+json`、`application/problem+json` を除く）のときだけ「保存した」で、本文が読めない・形が違うときも保存したとみなす。
- `formDiff.ts`・`formValidation.ts`・`problemMessage.ts` の書き込みの code（`writeProblemKind`・`writeProblemMessageKey`）、D3: A の `tables.problem.writeForbidden`（削除は `writeForbiddenDelete`）。
- `src/shared/unsaved-guard/`（roleadmin から `git mv` で移した）: `useUnsavedGuard(dirty, discard, { version })` に `markClean()` を足し、判定の関数（`shouldBlockNavigation`）を副作用なしにし、旗を下ろす2つの条件（`useLocation` の key の変化・版の数の変化）と `beforeunload` の付け直しを入れた。`LeaveConfirmDialog` は文言（`labels`）と data-testid の頭（`testIdPrefix`）を props で受ける。roleadmin の `PermissionPanel.tsx` は今の文言と `roleadmin-leave` の頭を渡し、振る舞いと data-testid は変わらない（E2E 140 の `roleadmin-leave-stay` もそのまま）。
- `app/work-role/WorkRoleProvider.tsx`: `registerSwitchGuard`（既定の値にも足した）。確かめが false なら送らずに `'ignored'` を返し、`runAfterModalClosed` で Modal のフォーカスの戻しが済んでからトリガーへフォーカスを戻す。確かめの最中の2回目の `switchTo` も `'ignored'`。
- 画面: `BusinessDataFormPage.tsx`（作成と更新。道で決める）、`DeleteConfirmModal.tsx`、`useRowWrite.ts`、`useRowDeleteFlow.ts`（削除の流れ。詳細と一覧が共有）、一覧の「作成」と行の操作の「更新」「削除」、詳細の「更新」「削除」、`registration.ts` に `/tables/new`・`/tables/edit`（同じ lazy）、`messages.ts` の足し（日英）。
- テスト（Step 27・29・31・33）: 3節の件数。fast-check の失敗は無かった（種の記録は不要だった）。

## 6. B6 の計画に無い判断・計画との差

| # | 差 | 理由 |
|---|---|---|
| N-16 | **N-5 の絞り方**: 書き込みの設定が無い旨は「ビューでなく、詳細のカラムに作成か更新で入力できるもの（主権限 FULL）がある」人にだけ出す（`RestrictionNotice` の `canWriteWhenConfigured`）。**機能設計 D13（`writeConfigured` が false なら出す）との差** | 依頼者の決定 N-5（書き込みの操作が許される人にだけ出す）。サーバーは書き込みの設定が無いと `operations` に CREATE・UPDATE・DELETE を入れず（`TableAccessPolicy` 154〜166行）、補助権限 CREATE・DELETE も定義の応答に出さないため、画面は近い判定しかできない。主権限 FULL を持たず補助権限 DELETE だけを持つ人には出ない（7節で確かめる） |
| N-17 | 作成のフォームで、必須（`required`）の真偽値（checkbox・switch）の入力は「外れた」（`'false'`）で始めて送る。必須でない真偽値は空（NULL）で始め、触らなければ送らない | 作成は空のままのカラムを送らない（D20）が、チェックの外れた状態を送る手段が無いと必須の真偽値を保存できない |
| N-18 | 日時（DATETIME・DATETIME_TZ）の入力は秒までにし、DB の値の秒の端数は入力に出さない | D20 の「日時は秒まで」。変えなければ送らないため、DB の端数は保たれる |
| N-19 | `WorkRoleSwitcher.tsx` は変えていない（計画 1.2 は変える見込み）。拒まれたときのフォーカスの戻しは Provider が行う | Switcher は `'ignored'` で何もしない今の形のままでよく、フォーカスの戻しは Modal の戻しより後に行う必要があるため Provider に置いた |
| N-20 | `LeaveConfirmDialog` の CSS の class を `roleadmin-dialog*` から `shared-unsaved-guard-*` に替え（見た目の値は同じ）、`frontend/eslint.config.js` の `no-console` の対象に `src/shared/unsaved-guard/**` を足した | `src/shared/` は機能の CSS を読めない。規則は足すだけ（D10: A の範囲を移した部品に広げた） |
| N-21 | `.tables-text-button` を `BusinessDataListPage.css` から `tablesCommon.css` へ移した | 詳細の画面と削除の Modal でも使うため |
| N-22 | 計画の 1.1 の表に無いファイル: `useRowDeleteFlow.ts`・`BusinessDataFormPage.css`・`testing/renderTablesDataRouter.tsx`（data router で描くテストの補助）と、テスト `BusinessDataListPage.write.test.tsx`・`useRowWrite.test.tsx`・`formDiff.property.test.ts`。`BusinessDataTable` に `onRowAction`、`BusinessDataProblemView` に `messageKey` の口を足した | 削除の流れを詳細と一覧で共有し、未保存の確かめ（`useBlocker`）のため data router でテストする |
| N-23 | 作成・更新の画面を、定義の `operations` に CREATE・UPDATE が無いまま開いたときは「この操作はできません。一覧へ戻ってください。」（`tables.form.notAllowed`）と一覧への入口を出す | 設計の文書に、道を直接開いたときの表示の記述が無い（判定はサーバーが正で、送れば 403 か 409 になる） |
| N-24 | `writeOutcome` で、401（トークンの更新の後も未ログイン）と 400 `MALFORMED_REQUEST` も「分からない」に倒した | 計画 4.3 の R-01 の表の外の組のため（表に無い組は「保存されていない」と言わない） |
| N-25 | 書き込みの材料の確かめ（`hasWriteMaterial`）は詳細のカラム（`detailColumns`）だけに当てた | フォームは詳細のカラムから作る（D19）。一覧のカラムの欠けで一覧を開けなくしない |
| N-26 | E2E の (d) の確かめは `target_admin` で入る（Step 22 の手の確かめは `mastersmith_reader`） | `target_admin` は見本の PG の初期化で必ずあり、trust に当たれば必ず接続が成り立つため見分けられる |
| N-27 | 180 の DSL は手で書き、必須と長さは `validations`（`required`・`maxLength`、origin DB）で付けた。DB の型の名前は `varchar`・`int4`・`text` | サーバーの必須は DSL の `validations` の `required` だけから決まる（`ColumnWriteRulesFactory`）。最初の試しで付けずに流し、必須の印が出ずに落ちた |
| N-28 | 180 のキーボードの操作は、メニュー・ページ送り・検索（Enter）・作成・保存・更新・削除の確かめ（Tab と Enter）で行い、並べ替えのキーボードの操作は入れていない | 並べ替えの見出しのボタンの操作は B5 の画面のテストが確かめている。180 の時間を短く保つ |
| N-29 | `businessDataMeasure.ts` の判定 `formShown100` を `tables-form-field-c` の数と `aria-busy` が偽で見る形に直した | B5 で書いた判定は `tables-form-c` の数を見ていて、フォームの data-testid（`tables-form-field-c<n>`）と合わなかった（B5 では使っていなかった） |
| N-30 | README の、ファイルを選んで E2E を流す手順（`(cd frontend && npx playwright test …)`）を、すべて `./gradlew e2eTest -Pe2e.files=…` に替えた | Q7: A で、直接の `npx playwright test` は設定の読み込みで止まるため |
| N-32 | **計画に無い変更（依頼者の決定）**: 既存のバックエンドの結合テストの「本文・ログに行の値が含まれない」の確かめを、乱数の欄を伏せてから確かめる形に直した。テスト用の部品 `backend/src/test/java/cherry/mastersmith/businessdata/testsupport/RandomFieldMask.java`（名前が traceId・spanId で終わる JSON の欄と `traceId=<16進>` の形の値、Problem Details の type の URL の番号を伏せる）とその単体のテスト `RandomFieldMaskTest.java`（5 件）を足し、`AbstractBusinessDataReadIT`（`838`・`0000-00-00`。MySQL・MariaDB・PostgreSQL の3つの子のクラスが使う）、`BusinessDataSecretLeakIT`・`BusinessDataWriteSecretLeakIT`（8 桁の数字の主キーの目印。応答の本文とログ）に当てた。本体は変えていない | 7.1 の 2回目の偶然の失敗（トレースID の `…c83841…`）。同じ形（16進に当たりうる数字だけの目印で、トレースID・番号を含む本文やログを確かめる）を検索で探し、当たりうる3つのクラスを直した。ほかの `doesNotContain` は、`zz-` などの16進に当たらない目印か、トレースID を含まない値（定義の JSON・スパンの属性・指標のタグ）を確かめる形で、直していない（8.1） |
| N-33 | E2E の前提の確かめ (c) の満たさない形を、使い捨ての PG（共有の見本の PG とは別の compose のプロジェクト。`TEMPORARY` を PUBLIC に残した既定のまま）で確かめた（8.2） | 依頼者の決定。共有の見本の PG は変えない |
| N-31 | `businessDataSchema.sql` と上乗せの compose はライセンスヘッダーを付けたが、ヘッダーの検査（`check-license-header.mjs`・Spotless）の対象の外のまま | 既存の `docker/` の SQL・YAML も検査の対象の外。検査の範囲を広げるのは計画の外 |

## 7. B6 の検査・E2E・4.14 の試し（Step 35〜37）

### 7.1 Step 35: 1コマンドの検査（B6）

- 1回目: `frontendLintCss` で落ちた（新しい CSS 2つで、ライセンスヘッダーの直後の説明のコメントの前に空行が無い。stylelint の `comment-empty-line-before`）。空行を足して直した。
- 2回目（2026-10-20 15:57:34〜16:17:02）: `:backend:integrationTest` の 1,386 件のうち **1 件が落ちた**: `MariadbBusinessDataReadIT` の「MySQL and MariaDB zero dates and out-of-range times are 409 MISMATCH logged as READ_CONVERSION」（U1 の B2 の `AbstractBusinessDataReadIT.unreadableStoredValues` 531 行）。応答の本文の連結に `838` が含まれないことを確かめる式が、**乱数のトレースID（`traceId`、32 文字の16進）の中の `838`** に当たった（落ちた応答の3つ目の `traceId` が `…c83841…`）。行の値（`838:59:59`）の漏れではない。バックエンドの本体にも U1 のテストにも、この Bolt では触れていない。1つの応答の `traceId` が `838` を含む見込みは約 0.7%（3つで約 2%）。手元の再現は乱数のため決まった形では起こせない。**直し方（テストで `traceId` を除いて比べる・`"838:` の形で探す、など）は依頼者に確かめる**（直していない）。
- 3回目（2026-10-20 16:17:41〜16:38:57、`caffeinate -dims`、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` は **BUILD SUCCESSFUL、21分15秒**（B5 の後は 20分27秒。CI の上限の7割の約 42 分の内）。流した後の `pmset -g log` に Sleep・DarkWake は無い。
  - バックエンド: 3,970 件、失敗 0、SKIPPED 9。JaCoCo の全体の合計は 行 97.64%・分岐 91.23%（B5 の後と同じ。本体のソースには触れていない）。
  - フロントエンド: 225 ファイル・1,963 件がすべて通過。カバレッジの全体の合計は **行 95.10%・分岐 89.84%**（B5 の後 行 95.24%・分岐 90.32%。下限 80%・70% を保つ）。`src/features/tables/` は 行 94.01%・分岐 88.17%、`src/shared/unsaved-guard/` は 行 100%・分岐 96.97%、`src/app/work-role/` は 行 98.90%・分岐 96.52%。下限・対象・除外は変えていない。
- 初回の JavaScript（P-6、`gzip -c | wc -c` のバイト数、5.2 と同じ測り方）: 入口は 162.0 KB → **163.9 KB**（`check-bundle-size.mjs`。`index-*.js` 155.6 → 157.5 KB）。画面の chunk: `BusinessDataListPage-*.js` 6,192 → 6,744、`BusinessDataDetailPage-*.js` 1,545 → 1,623、`useTableDefinition-*.js`（共有）5,305 → 6,362、新しい `BusinessDataFormPage-*.js` 5,760。目安 500 KB の内で警告なし。
- コードの検索（U3-NFR4.2・4.20）: `src/features/tables/` と `src/shared/unsaved-guard`・`scroll-region`・`page-size`・`null-value` の本体（テストのファイルを除く）で、`console`・`localStorage`・`sessionStorage`・`indexedDB`・`dangerouslySetInnerHTML`・`document.title` の使用は 0 件（説明文の中の語だけ）。

### 7.2 Step 36: E2E の全件と B6 で確かめること

- 前提: Mailpit と、上乗せで開けた見本の PG（`127.0.0.1:15432`）。`caffeinate -dims ./gradlew e2eTest`（2026-10-20 16:40:33〜16:54:22）: **256 件すべて通過**（json の報告の stats は expected 256・unexpected 0・flaky 0）。Playwright の表示で 13.8 分（duration 825 秒＝13分45秒）、Gradle で 13分49秒。基準（232 件・約 10分35秒）の約 1.30 倍で、1.5 倍（約 15分53秒）の内。U3-NFR7.8 の「諮る」には当たらない。
- ファイルごとの時間（秒）: 180 は 2.6、185 は 182.4（B5 の後 161.7）、040 は 6.3、150 は 8.9、170 は 3.0。
- 準備は 100 ms、片付けは 211 ms（スキーマ 1・ロール 2、接頭辞の残り 0）。前の残りは 0 件。
- 秘密の確かめ: 見つかった件数 0（値の種類 13。json の報告 1・添付 305）。流した後の `pmset -g log` に Sleep・DarkWake は無い。
- 040・150・170・180 はどれも通った。
- 途中で失敗させた回: わざと落とす一時の E2E（`frontend/e2e/999-tmp-fail.e2e.ts`。確かめの後に消した。コミットしない）を `-Pe2e.files` で流し、E2E は失敗、片付けは動いてスキーマ 1・ロール 2 を消し、接頭辞の残り 0。見本の PG で `e2e_biz_%` と `mastersmith_e2e_%` を数えて 0|0。
- (d) の誤ったパスワードの拒否は、毎回の前提の確かめで通っている（5.5 と 7.2 のすべての回）。
- E2E の後の見本の PG の `docker stats --no-stream`: 27.41 MiB / 512 MiB、CPU 0.02%。
- 流し終えた後（4.14 の試しの後）に、上乗せ無しで見本の PG を作り直してポートを閉じた（2026-10-20 17:10 ごろ。`5432/tcp` だけになり、`nc -z 127.0.0.1 15432` は失敗）。作り直しの後も `has_database_privilege('public','business','TEMP')` は `f`、配備したアプリの `/actuator/health` は 200。

### 7.3 Step 37: axe-core 4.14 の試し（B6、作業フォルダの中だけ。統合しない）

- 試しの前の `frontend/package.json`・`package-lock.json` の SHA-1: `dff50fde…`・`b16f0773…`。`npm view axe-core version` は 4.14.0。`ignore-scripts=true` のまま `npm install --save-dev axe-core@4.14.0`: `license` は MPL-2.0、`npm ls axe-core` は 4.14.0 の1つ（vitest-axe の分は deduped）。差は2ファイル（5 行の追加・5 行の削除）。
- `caffeinate -dims ./gradlew e2eTest`（見本の PG の前提あり）: **256 件すべて通過**（13.0 分）、**どの規則の違反も 0 件**。秘密の確かめも 0 件。
- 規則 `label-content-name-mismatch` の U3 の区分（json の注記 `axe-nodes` から集計。台本はリポジトリに足していない）: 行の操作のトリガー（owner `tables-row-actions-*`）passes 3,160（160 で 1,400・185 で 1,760）、行のリンク（owner `tables-row-link-*`）passes 2,440（160 で 1,400・185 で 1,040）、incomplete・violations はどちらも 0。B6 で足した状態（作成・更新のフォーム・削除の確かめ・離れる確かめ・分からない知らせ）には、この規則の対象の新しい区分は出なかった。B7 の passes の確かめの対象は B5 と同じ2つの区分のまま渡す（D6: A）。
- 片付け: `git checkout -- frontend/package.json frontend/package-lock.json` の後に `./gradlew frontendInstall --rerun-tasks`。`npm ls axe-core` は 4.13.0、`git diff --exit-code` は差なし（SHA-1 も試しの前と同じ）。`vendor/` の差も無い。型の検査と `src/features/tables` の 295 件を流し直して通った。

### 7.4 Step 38: 記録（B6）

- `code-summary.md` の B6 の節、この `generation-notes.md` の 5〜7節、`source-manifest.json`（B6 で作った・変えたパスを足した。計 133 件（N-32 の5件を含む）。移したファイルの元のパスは、B5 で消したファイルと同じく載せていない）、`traceability.json`（B6 に回していた AC2.x・AC3.x の部分・NFR1.10・NFR4.23〜4.25・NFR6.4・NFR7.11・NFR7.13 を OK にした。OK 120・Deferred 37・N/A 3）。
- コードのレビューは依頼していない（依頼者の決定: B7 の後に U3 の全体で1回）。
- `git mv` で移した4つのファイル（`useUnsavedGuard.ts`・`.test.tsx`・`LeaveConfirmDialog.tsx`・`.test.tsx`）は、移しの記録のために索引（index）に載っている。コミットはしていない。

### 7.5 コミットの区切り（Step 39 の提案の材料）

`HEAD`（`1152202`）の `frontend/` の写しをスクラッチに書き出し、区切りの順に重ねて型の検査（`tsc --noEmit`）・`eslint`・`oxlint` を流した（コミットはしていない）。どの区切りも通り、最後の写しは作業フォルダと同じ。

| 区切り | 中身 | 型の検査と lint |
|---|---|---|
| C1 | 未保存の確かめの移し替え（`src/shared/unsaved-guard/` へ `git mv` と口の足し・CSS）、roleadmin の import と文言の渡し方、作業ロールの確かめの口（`WorkRoleProvider.tsx` とテスト、`WorkRoleSwitcher.test.tsx`）、`eslint.config.js` | 通る |
| C2 | `frontend/src/features/tables/` の B6 のすべて（37 ファイル） | 通る |
| C3 | E2E の見本の PG の仕組みと E2E: `build.gradle.kts`、`docker/e2e/compose.targetdb-port.yaml`、`playwright.config.ts`、040・180・185、`e2e/support/` の見本・測り・SQL | 通る |
| C4 | `README.md` | 通る（最後の形は作業フォルダと同じ） |
| C5 | N-32 の直し: `backend/src/test/java/cherry/mastersmith/businessdata/testsupport/RandomFieldMask.java`・`RandomFieldMaskTest.java`、`businessdata/web/AbstractBusinessDataReadIT.java`・`BusinessDataSecretLeakIT.java`・`BusinessDataWriteSecretLeakIT.java` | バックエンドのテストだけ（コンパイルと Spotless を通した。フロントエンドの型の検査・lint に影響しない） |

## 8. 依頼者の決定の後の直しと確かめ（B6）

依頼者の答え: 838 の失敗は B6 で直す（テストだけ。二段の確かめ）、N-16 は近い判定でよい（D13 との差のまま）、(c) は使い捨ての PG で確かめる、N-17・N-23・N-24 はこの読み方でよい。

### 8.1 乱数の欄に当たる確かめの直し（N-32）と二段の確かめ

- 検索: `backend/src/test/java/cherry/mastersmith/businessdata` の `doesNotContain`（92 か所）を読み、トレースID・スパンID・Problem Details の type の URL（アプリの番号）を含みうる本文やログを、16進に当たりうる目印（数字だけ）で確かめているものを探した。当たったのは `AbstractBusinessDataReadIT` の `838`（MySQL・MariaDB）と、`BusinessDataSecretLeakIT`（`98765431`）・`BusinessDataWriteSecretLeakIT`（`97531864`）の主キーの目印の3つ。書き込みの IT（`AbstractBusinessDataWriteIT`）の確かめは鍵の有無だけ、`BusinessDataWriteAuditIT` の `"51"` は引用符つきのため当たらない、`BusinessDataTraceExposureIT` などはスパンの属性と指標のタグを確かめていてトレースID を含まない。
- 二段の確かめ（team.md）。確実に起こすため、テストの HTTP の部品（`BusinessDataTestApp.postRaw`）に一時に W3C の `traceparent` の見出しを足し、アプリのトレースID を決めた値にした（アプリはこの見出しを受け継ぐ）。確かめの後に `git checkout` で戻した（差なし）。
  - 1段目（直す前のテスト。`git show HEAD:` で戻した形）: トレースID `0000000000000000000000000838838a` で `MariadbBusinessDataReadIT`・`MysqlBusinessDataReadIT` の `unreadableStoredValues` が2件とも落ちた（応答の `traceId` に `838` が当たった）。トレースID `98765431975318640000000000000001` で `BusinessDataSecretLeakIT`・`BusinessDataWriteSecretLeakIT` が2件とも落ちた（失敗の文に決めたトレースID が出て、目印 `98765431`・`97531864` に当たった）。
  - 直しの途中の確かめ: 決めたトレースID のまま直した形で流すと、2つの漏えいの IT がまだ落ちた。TRACE のログに `traceId=<16進>`（`ProblemDetail` の properties・監査の記録の文字の形）と JSON の `auditTraceId` の欄があったため、伏せる形を「名前が traceId・spanId で終わる欄」と `…=<16進>` の形に広げた。
  - 2段目（直した後のテスト）: 同じ決めたトレースID で、`MariadbBusinessDataReadIT`・`MysqlBusinessDataReadIT`（それぞれ 16 件、SKIPPED 1）と2つの漏えいの IT がすべて通った。
  - 見出しを外した後: 3種類の DB の `MariadbBusinessDataReadIT`・`MysqlBusinessDataReadIT`・`PostgresBusinessDataReadIT`（16 件ずつ、SKIPPED 1・1・2）と2つの漏えいの IT がすべて通った。`RandomFieldMaskTest`（5 件）は伏せる前の本文が目印に当たること・伏せた後は当たらないこと・本当の漏れ（`838:59:59`・ログの message の値）と対象DB の接続先（`localhost:<番号>`）は伏せずに残ることを確かめる。

### 8.2 (c) の満たさない形（N-33）

- 共有の見本の PG とは別の compose のプロジェクト `mastersmith-e2eprobe`（`COMPOSE_PROJECT_NAME` と、スクラッチの compose のファイルを `COMPOSE_FILE` で渡した。同じイメージ・digest、tmpfs、`127.0.0.1:15433`、乱数の管理者のパスワード）で使い捨ての PG を起動した。PUBLIC の `TEMPORARY` は既定のまま `t`。
- `COMPOSE_PROJECT_NAME`・`COMPOSE_FILE`・`E2E_TARGETDB_PORT=15433` を付けて `./gradlew e2eTest -Pe2e.files=e2e/010-skeleton.e2e.ts` を流し、(a)・(b) を通って **(c) の「PUBLIC に TEMPORARY が残っています」で止まった**（片付けは「作ったものは無い」）。`e2eTest` の本体は変えていない（`docker compose` がこの2つの環境変数を読むため）。
- 確かめの後に `docker compose … down -v` で消し、スクラッチの compose のファイルも消した（`e2eprobe` のコンテナは 0）。共有の見本の PG は触れておらず、`has_database_privilege('public','business','TEMP')` は `f` のまま、15432 は閉じたまま。

### 8.3 直した後の1コマンドの検査

- `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-20 17:46:01〜18:06:45、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）: **BUILD SUCCESSFUL、20分43秒**。流した後の `pmset -g log` に Sleep・DarkWake は無い。
- バックエンド: 3,975 件（`RandomFieldMaskTest` の 5 件の足しを含む）、失敗 0、SKIPPED 9。JaCoCo の全体の合計は 行 97.64%・分岐 91.23%（本体は変えていない）。
- フロントエンド: 225 ファイル・1,963 件がすべて通過。行 95.10%・分岐 89.90%。
- 直しはバックエンドのテストだけで、フロントエンドと E2E の補助に触れていないため、E2E は流し直していない（7.2 の 256 件の通過のまま。依頼者の指示）。15432 は閉じたまま。

### 8.4 依頼者に確かめたいこと（B6）

1. コミットの区切り（7.5 の C1〜C4 に、N-32 の直しの C5 を足す形）でよいか。

## 9. 依頼者に確かめたこと（B6、決まった）

1. 既存のテストの偶然の失敗 → B6 で直す（8.1）。
2. N-16 → 近い判定でよい（機能設計 D13 との差として記録したまま）。
3. (c) の満たさない形 → 使い捨ての PG で確かめる（8.2）。
4. N-17・N-23・N-24 → この読み方でよい。
