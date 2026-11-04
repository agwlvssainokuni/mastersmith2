# コード生成のまとめ — u4-business-data-time

作業ブランチ `feature/261102-timezone-ui-polish-b4`。コミットは C1 `c2b40303`（サーバー）・C2 `9749465c`（画面の関数）・C3 `a5483114`（部品と画面）・C4 `d0537e04`（保存の押下の変換を1回に）・C5 `028985bc`（E2E）。

## 作った・変えたファイル

`source-manifest.json` のとおり（84 ファイル）。要点:

- サーバー（Step 2〜4）: 参照の項目に `labelValueType` を足した（`ReferenceField`・`ReferencePolicy.labelValueTypeOf`・`ReferenceLookup`・`ReferenceFieldResponse`）。`AVAILABLE` のときだけ型を持ち、JSON は `NON_NULL`、`toString` は型の名前だけ。DSL から決めるため問い合わせは増えない。
- 画面の関数（Step 5〜11）: `shared/format/dateTimeInput.ts`（入力の初めの値と送る形）、`features/tables/resolveDateTimeInput.ts`（`ok`・`invalid`・`gap`・`overlap-pending`、範囲の始まりは先・終わりは後）、`valueFormat`・`rowLabel` のタイムゾーンの状態、`formDiff`（`FormTime`・`resolveFormDateTimes`）・`formValidation`・`searchConditions`（`resolveSearch`）・参照（`formatReferenceText`・`pickerSearch`・`useLookupSearch`）。
- 部品と画面（Step 12〜13）: `useTablesTimeZone`・`TimeZoneNotice`・`DateTimeField`（時刻の欄と、別の `FormField` ＋見えない legend の `RadioGroup` の2択）、一覧・詳細・フォーム・検索・ピッカーの配線、日英の文言（`tables.form.utc` を消した）。
- E2E（Step 14）: `200-business-data-time-flow.e2e.ts`、`support/businessDataTimeDsl.ts`（版の印 `E2ET_`）、`businessDataSchema.sql` に `e2e_event`、185 に時刻の状態 12。

新しい依存・API の口・表・列・code は足していない（応答に項目を1つ足しただけ）。カバレッジの下限・除外・axe の規則・ArchUnit の境界テスト・SpotBugs の除外は変えていない。

## テストの数と実測（Step 15）

`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 付き、`caffeinate -dims` で `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を1回: **BUILD SUCCESSFUL、27 分 49 秒**（09:01:30〜09:29:20。`pmset -g log` に Sleep・DarkWake なし）。

| 対象 | 件数 |
|---|---|
| サーバーの単体 | 2936 件、失敗 0、飛ばし 0 |
| サーバーの結合 | 1716 件、失敗 0、飛ばし 10（対象DB の種類ごとに当たらない場面の既存の飛ばし） |
| 画面（Vitest） | 273 ファイル・2962 件、すべて通過 |

画面のテストのファイル数は、Step 12〜13 の担当の途中の流しで 263 と出ていたが、verify では見込みどおり 273（`src` の 266 ＋ E2E の手伝いの 7）だった。

カバレッジ（行 / 分岐）:

| 範囲 | 行 | 分岐 |
|---|---|---|
| サーバー全体 | 97.7% | 91.5% |
| `businessdata.domain` | 98.2% | 91.6% |
| `businessdata.service` | 95.9% | 89.1% |
| `businessdata.web` | 97.3% | 80.1% |
| 画面全体 | 95.49% | 90.92% |
| `src/features/tables/` | 95.63% | 91.42% |

いずれも下限（行 80%・分岐 70%）以上。

### 書き換えたテストの前と後の件数（画面）

前の基準は変更の前の `npm --prefix frontend run test`（268 ファイル・2782 件）。

| ファイル | 前 | 後 |
|---|---|---|
| `valueFormat.test.ts` | 5 | 18 |
| `valueFormat.property.test.ts` | 2 | 4 |
| `rowLabel.test.ts` | 6 | 7 |
| `rowLabel.property.test.ts` | 1 | 1 |
| `formDiff.test.ts` | 10 | 21 |
| `formDiff.property.test.ts` | 5 | 8 |
| `formValidation.test.ts` | 14 | 16 |
| `searchConditions.test.ts` | 6 | 20 |
| `references/pickerSearch.test.ts` | 31 | 34 |
| `businessDataParse.test.ts` | 80 | 86 |
| `references/referenceField.test.ts` | 22 | 31 |
| `BusinessDataDetailPage.test.tsx` | 9 | 13 |
| `BusinessDataFormPage.test.tsx` | 40 | 49 |
| `BusinessDataListPage.test.tsx` | 12 | 20 |
| `SearchPanel.test.tsx` | 6 | 13 |
| `useRowSearch.test.tsx` | 6 | 8 |
| `listState.test.ts` | 4 | 5 |
| `messages.test.ts` | 2 | 3 |
| `references/LookupPickerDialog.test.tsx` | 21 | 24 |

新規: `dateTimeInput.test.ts` 24・`resolveDateTimeInput.test.ts` 26・`DateTimeField.test.tsx` 14・`TimeZoneNotice.test.tsx` 5・`useTablesTimeZone.test.tsx` 6。`formDiff.test.ts` と `BusinessDataFormPage.test.tsx` の後の件数は、C4 の変換の回数のテスト（2件・1件）を含む。

サーバー: 範囲の命令で単体 165 → 176、結合 108 → 112（Step 1〜4 の時点）。

## 計画との差・決めたこと

1. 呼ぶ側の直しを Step 2 に前倒し（引数を変えるとコンパイルが通らないため）。層の順は守った。
2. 接続の数の確かめ（`ReferenceConnectionUsageIT` に1件）は、今ある参照先に合わせて `STRING` のラベルで確かめた。時刻の型のラベルは単体テストで確かめた。
3. Step 5〜11 の間は仮の UTC（`provisionalTimeZone.ts`）を渡して型を通し、Step 13 で本当の表示のタイムゾーンに置き換えてファイルを消した。
4. 誤りの理由を4つ足した（`DATE_TIME_FORMAT`・`DATE_TIME_GAP`・`DATE_TIME_OVERLAP`・`TIME_ZONE_UNAVAILABLE`）。読み込みの失敗の短い文は U2 の既存の鍵 `displayTime.error` を使った。
5. Step 9 の `formDiff` を Step 13 で直し、選択肢・参照の日時のカラムは変換しない（D13・D33）。依頼者が受け入れた（C3 の前）。
6. プリファレンスへのリンクの道を tables の側の定数に置き、`features/preferences` の定数と一致することをテストで確かめる（機能どうしの import の制限のため）。依頼者が受け入れた。
7. 保存の誤りは D22 のとおり誤りのまとめへフォーカスを移し、まとめの2択の誤りを押すと最初の選択肢へ移る。検索とピッカーは直接最初の選択肢へ移る。依頼者が受け入れた。
8. 保存の押下の変換: 初めは欄ごとに2回（実は離れた欄の確かめを含めて3回）だった。依頼者の決定（設計どおりに直す）で、確かめの結果を `diffForm` に渡して使い回し、離れた欄だけを確かめる形にして欄ごとに1回にした（C4。性能の設計 2.2 に合う）。
9. 検索の作った条件は一覧の保存の形に足さず、入力の文字と表示のタイムゾーンを鍵に `useMemo` で覚える（保存の形を変えないため）。ページ送り・並べ替え・件数の変更で変換が増えないことを回数で確かめた。
10. 古い形（`T` 区切り、前は UTC として読んだ値）の保存済みの日時の条件は、依頼者の決定で欄に残して誤りを出し、その条件は送らない（テストは一覧と検索の画面に1件ずつ）。
11. 一致の検索の案内の文は担当が作った（「秒の端数を持つ値は、端数まで入れてください（例: .123）」と英語の文）。依頼者が受け入れた。
12. 2択の組み方（別の `FormField` ＋見えない legend）は vitest-axe で違反 0 で、legend なしの形への切り替えは要らなかった（インフラ設計の H5）。
13. E2E の 195 の見本（`referenceFixtures.ts`）を、本物の応答に合わせて `AVAILABLE` のときだけ `labelValueType` を持つ形に直した。`writableDefinition` はカラムの `formPart` を保つ形にした（今の表は結果が変わらない）。185 の時刻の状態は、既存の組のテストを変えないため別のテストにした（20 組 × 1）。
14. 作業の中の手順の誤り（どれも元に戻した）: Step 12〜13 の担当が `git rm --cached` を1回（すぐ `git add` で戻した）、`sed -i` を1回（結果は正しいことを確かめた）、vitest の json の出力ファイルを1回作って消した。

## 未実行（Build and Test へ引き継ぐ）

- `./gradlew e2eTest`: 200（専用の DSL がサーバーの検証を通るか、実際のブラウザで通るか）と 185 の時刻の状態（違反 0）、`e2e_event` を足した後の 180・185・190・195。185 が 20 本増えるため E2E 全体の時間も記録する（インフラ設計の H2）。
- k6 の業務データの場面の p95（NFR2.1.2、H1）、CI の時間（H4）。

## 承認の場の決定（U4 の確かめの場、2026-11-04）

依頼者が Request Changes を選び、設計の段から持ち越したレビューの指摘を文書だけで直した（コードとテストは変えない。計画の 4 節）。コード生成のレビューの R-01（E2E の未実行）は Build and Test へ、R-02（D11/D12）は機能設計の文書の直しで解き、R-03（画面の `new Date()`）は今の形で受け入れる。

## 依頼者に確かめたいこと

- 直しの後の再レビューで出た Minor（機能設計 R-07: 文言の鍵の表と検索の欄の読み込み中の書き漏れ、NFR 要件 R-05: NFR2.1 の Deferred の行・R-06: NFR2.1.4 の読み替え、NFR 設計 R-05: 検索の既定の値の時期の書きぶり・R-06: 差1の書きぶり）をどう扱うか。どれも文書の書き漏れで、コードに影響しない。
