# コードの要約 — U3 u3-businessdata-screens（Bolt B5・B6。B7 は後で足す）

作業ブランチ `feature/261016-business-data-views-b5`（`develop` の `6815308` から）。コミットはまだしていない。詳しい記録は `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/code-generation/generation-notes.md`。

## B5（一覧・詳細・開けないとき）

### 1. 作ったもの・変えたもの・消したもの

- **業務データの画面**（`frontend/src/features/tables/`）: 受け渡しの型と写しの関数（L2）、読み取りの API 3本（L1。本文の上限 64 KB）、画面の外の記憶（L3。印 100 件・テーブル 100 個・世代・作業ロールの変化とログインの範囲の知らせで全部消す）、純粋な関数（L5・L6。書式・行のラベル・ページ・検索の条件・一覧の状態・文の選び方・定義の読み直しの判定・上限の定数）、フック（L7。定義・一覧・詳細）、画面（一覧 S1・詳細 S2・開けないとき S6）と部品（検索の領域・表・行の操作・理由の表示）、文言（日英）、登録（`/tables`・`/tables/row` を別々の lazy で）、CSS、テストとテストの補助（`testing/`）。
- **骨組み**（`frontend/src/app/`）: 4つの道を作る `toTablePath` と `isTablePath`（`tablePlaceholderPath.ts`）、サイドバーの今の項目の判定を4つの道に広げた（`buildNavSections.ts`）、ログインの範囲の知らせ `loginScope.ts` と、LoginStateGate がログイン⇄未ログインの切り替わりのたびに鳴らす口（L4）。
- **共通の部品**（`frontend/src/shared/`）: 横のスクロールの枠 `scroll-region/`、1ページの件数の選択 `page-size/`、NULL の表示 `null-value/`（L8）。
- **lint**: `frontend/eslint.config.js` に、`src/features/tables/**` と U3 の3つの共通の部品へ `no-console`（error）を足した（D10: A）。
- **E2E**: 補助 `frontend/e2e/support/businessDataFixtures.ts`・`businessDataApiRoute.ts`・`businessDataMeasure.ts` と単体のテスト、`secretValues.ts`（`SECRET_KINDS`・`businessValue`・目印・D5: A の値）、`frameApiRoute.ts`（`tableAccess` を外した）、`u7Measure.ts`（判定を `tableListShown` に）、160・170 の書き換え（本数に数えない）、185（23 件。本数に数えない）。
- **文書**: `README.md`（E2E の表・注記、業務データの画面の段落）、`frontend/src/features/README.md`。
- **消したもの**（A1）: `frontend/src/features/tables/TablePlaceholderPage.tsx`・`.css`・`.test.tsx`、`useTableAccess.ts`、`tableAccessApi.ts`・`.test.ts`。`e2e/support/navTreeFixtures.ts` の `tableAccessWire`。サーバーの `GET /api/me/table-access` は残した（G5）。
- 作ったパス・変えたパスの一覧は `source-manifest.json`（95 件）。

### 2. 主な決定

- 定義と一覧の状態は記憶を正とし、描くたびに記憶を読む。記憶に今の作業ロールの定義があれば定義の API を送らない。要求の中身は鍵の文字列にまとめ、操作1回で一覧の API は1本。
- 主キーは意味の無い印（16 バイトの乱数の16進）で記憶から引き、履歴の状態には `{ rowToken }` だけを置く。印が無ければ一覧へ置き換えて移る（D2。作業ロールの切り替えの後も、記憶が消えるため一覧へ移る。計画の D2: A）。
- Table の列の鍵・行の id はページの中の番号（`c<n>`・`actions`・`r<n>`）だけにし、カラム名・主キー・行の値を DOM の `id`・`data-testid` に入れない。
- 行のラベルの部分は、読み上げの名前と同じく空白をまとめて前後を切る。行のリンクの見える文字は、名前「詳細: <表示名> <行のラベル>」と行の操作の名前に必ず含まれる（性質ベースのテストで確かめた）。
- 画面に結べない 400 と 413 は、表を残して一覧の上に出す。403 は S6 に置き換え、業務のメニューと作業ロールを読み直し、業務データの API は送り直さない。

### 3. テストとカバレッジ

- U3 の B5 のテスト: 36 ファイル・251 件（画面のテスト 32 ファイル、E2E の補助の単体のテスト 4 ファイル）。性質ベースのテストは写しの関数・書式・行のラベル・道。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`: 通過、20分27秒。フロントエンド 1,821 件、行 95.24%・分岐 90.32%（`src/features/tables/` は 行 94.34%・分岐 90.03%）。バックエンド 3,970 件（失敗 0・SKIPPED 9）、行 97.64%・分岐 91.23%。
- E2E の全件（4.13）: 255 件すべて通過、13分26秒（基準の約 1.27 倍）。185 は 161.7 秒。
- 画面の時間の先行の測り（185、各 5 回の中央値）: listShown20 54.1 ms・listShown100 72.4 ms・detailFocused 46.6 ms・listShown100Back 176.1 ms。どれも目標（0.5 秒・1.0 秒）の内。

### 4. axe-core 4.14.0 の試し

- 作業フォルダの中だけで 4.14.0 にして E2E の全件を流し、255 件すべて通過・違反 0 件。U3 の行の操作のトリガー 3,160 件と行のリンク 2,440 件は、規則 `label-content-name-mismatch` ですべて passes。戻した後に `package.json`・`package-lock.json` の差は無い。

### 5. 計画との差

`generation-notes.md` の 2節（N-1〜N-15）。主なもの:
- 計画の表に無いファイル（純粋な関数・フックの共有の口・テストの補助・CSS）を足した（N-1）。
- 狭い幅で画面全体が横にはみ出したため、横のスクロールの枠と画面の枠に `position: relative` を足した（N-6）。
- 測りの部品が `focusin` でも判定を見直す形にした（N-7）。
- `ScrollRegion` の `tabIndex={0}` に1行の lint の除外を付けた（N-8。roletransfer と同じ形）。
- 170 の `/api/me/table-access` の形の比べを外した（N-9。業務データの形の比べは 180）。
- コミットの区切りの C3・C4 を変える案（generation-notes.md の 4.5）。

### 6. 依頼者に確かめたいこと

1. N-8: `ScrollRegion.tsx` の1行の lint の除外（`jsx-a11y/no-noninteractive-tabindex`。roletransfer と同じ形）を、計画 3節の「緩める変更は無い」の外として受け入れてよいか。
2. N-5: `writeConfigured` が false のとき、読み取りだけの作業ロールにも「いまは業務データを変えられません（書き込みの設定がありません）」を出す（機能設計 D13 の文のまま）でよいか。B6 で、書き込みの操作が許されるときだけに絞る形もとれる。
3. N-3・N-4: 検索の既定の値の入れ方（RANGE は下限、CHOICE は選択肢の1つ）と、検索の領域をすべての項目が折りたたみのときだけ閉じて始める読み方でよいか。
4. コミットの区切り: 計画の C3 では型の検査が通らないため、E2E の変更を C3 に入れ、C4 を `README.md` だけにする案でよいか。

## B6（作成・更新・削除）

作業ブランチ `feature/261016-business-data-views-b6`（`develop` の `1152202` から）。コミットはまだしていない。詳しい記録は `generation-notes.md` の 5〜8節。

### 1. 作ったもの・変えたもの

- **見本の PG の安全の確かめと TEMPORARY の取り上げ**（Step 22・23）: 上乗せ `docker/e2e/compose.targetdb-port.yaml`。開けた直後に誤ったパスワードが拒まれた（scram）。共有の見本の PG の PUBLIC の `TEMPORARY` を取り上げた（`f|f|t`）。E2E の後に上乗せ無しで作り直し、15432 は閉じた。
- **E2E の見本の PG の仕組み**（Step 24）: `build.gradle.kts` の `e2eTest` に前提の確かめ (a)〜(d)・前の残りの片付け・準備・値の受け渡し・`-Pe2e.files`・`finalizedBy` の片付け（`e2eTargetDbCleanup`）。`frontend/e2e/support/businessDataSchema.sql`（表1つ・架空の行 25 件）。`playwright.config.ts`（対象DB の値が無ければ止める、`SECRET_ENV_NAMES` に一時のロールの4つと接続先）。
- **E2E**: 040 の書き換え（`SCHEMA_MISMATCH`）、代表の流れ 180（本物の見本の PG。一覧 → 検索 → 作成 → 詳細 → 更新 → 削除、キーボード、見本と本物の形の比べ）、185 に作成の誤り・離れる確かめ・分からない知らせ・更新・削除の確かめの状態と測り `formShown100`。
- **業務データの画面**（`frontend/src/features/tables/`）: 書き込みの写しの確かめ・書き込みの API 3本・結果の分け方（`writeOutcome.ts`）・本文の写し（`formDiff.ts`）・画面の検証（`formValidation.ts`）・書き込みの文、フック `useRowWrite`・`useRowDeleteFlow`、作成・更新の画面 `BusinessDataFormPage`、削除の確かめ `DeleteConfirmModal`、一覧の「作成」と行の操作の「更新」「削除」、詳細の「更新」「削除」、N-5 の絞り（`RestrictionNotice`）、登録に `/tables/new`・`/tables/edit`。
- **共通の部品と骨組み**: 未保存の確かめを roleadmin から `src/shared/unsaved-guard/` へ移し、`markClean()`・版の数・副作用の無い判定を足した（roleadmin の振る舞いは同じ）。作業ロールの切り替えの前の確かめの口 `registerSwitchGuard`。
- **文書**: `README.md`（E2E の起動の手順、「E2E の見本の PostgreSQL」の節、040・180・185 の行、ファイルを選んで流す手順を `-Pe2e.files` に、TEMPORARY の取り上げの1文）。
- **バックエンドのテストの直し**（N-32）: `backend/src/test/java/cherry/mastersmith/businessdata/testsupport/RandomFieldMask.java`（乱数の欄を伏せる）と単体のテスト、`AbstractBusinessDataReadIT`・`BusinessDataSecretLeakIT`・`BusinessDataWriteSecretLeakIT` の確かめに当てた。本体は変えていない。
- 作ったパス・変えたパスの一覧は `source-manifest.json`（計 133 件）。

### 2. 主な決定

- 書き込みの結果は「保存した」「保存されていない」「分からない」に分け、「保存されていない」は既知の状態コードと code の組（15 組）のときだけ。ほかはすべて「分からない」に倒す。
- 保存の成功では `markClean()` → 切り替えの確かめを外す → 移動 → Toast の順にし、自分の保存の移動を未保存の確かめが止めない。
- 「分からない」の後は入力を残し、確かめる入口を出し、もう一度押すと「保存済みかもしれません」の確かめを経て送る（D4: A）。書き込みの 403 は S6 の 403 の表示に替え、入力を捨てる（D3: A）。作業ロールが変わったら一覧へ移る（D2: A）。

### 3. テストとカバレッジ

- 画面のテスト: `src/features/tables` 32 ファイル・295 件、`src/shared/unsaved-guard` 2 ファイル・17 件、`src/app/work-role` 3 ファイル・30 件、E2E の補助の単体のテスト 30 件。性質ベースのテストは書き込みの結果の分け方・本文の写し・画面の検証の長さと小数の比べ（失敗なし）。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`: 3回目で通過、21分15秒（1回目は CSS の lint、2回目は既存のバックエンドの結合テストの偶然の失敗。トレースID に `838`。generation-notes の 7.1）。依頼者の決定でその確かめを直した後（N-32）の最後の回は 20分43秒で通過。フロントエンド 1,963 件、行 95.10%・分岐 89.90%（`src/features/tables/` は 行 94.01%・分岐 88.17%）。バックエンド 3,975 件（失敗 0・SKIPPED 9）、行 97.64%・分岐 91.23%。
- E2E の全件（見本の PG あり）: 256 件すべて通過、13分45秒（基準の約 1.30 倍）。180 は 2.6 秒、185 は 182.4 秒。途中で失敗させた回でも片付けの後の残りは 0。
- 画面の時間（185、各 5 回の中央値）: listShown20 50.1 ms・listShown100 70.1 ms・detailFocused 45.2 ms・listShown100Back 179.0 ms・formShown100 32.8 ms。どれも目標の内。

### 4. axe-core 4.14.0 の試し

- 作業フォルダの中だけで 4.14.0 にして E2E の全件を流し、256 件すべて通過・違反 0 件。U3 の行の操作 3,160 件と行のリンク 2,440 件はすべて passes。B6 の状態で新しい区分は出なかった。戻した後に `package.json`・`package-lock.json` の差は無い。

### 5. 計画との差

`generation-notes.md` の 6節（N-16〜N-31）。主なもの: N-5 の近い判定（N-16、機能設計 D13 との差）、必須の真偽値の初めの値（N-17）、`WorkRoleSwitcher.tsx` を変えなかった（N-19）、計画の表に無いファイル（N-22）、180 の DSL に `validations` を付けた（N-27）、`formShown100` の判定の直し（N-29）、README のファイルを選んで流す手順（N-30）、乱数の欄に当たる既存のバックエンドの確かめの直し（N-32。テストだけ。二段の確かめは generation-notes の 8.1）、(c) の満たさない形を使い捨ての PG で確かめた（N-33）。

### 6. 依頼者に確かめたいこと

コミットの区切り（generation-notes の 7.5 の C1〜C5）。ほかの点（偶然の失敗の扱い・N-16・(c)・N-17・N-23・N-24）は依頼者の答えで決まった（generation-notes の 9節）。
