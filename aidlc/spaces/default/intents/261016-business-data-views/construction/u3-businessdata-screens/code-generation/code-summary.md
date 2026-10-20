# コードの要約 — U3 u3-businessdata-screens（Bolt B5。B6・B7 は後で足す）

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
