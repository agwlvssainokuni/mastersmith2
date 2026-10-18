# 性能の設計 — U3 u3-businessdata-screens

U3 は種類 `ui` の単位で、業務データの画面を受け持つ。この文書は、画面の側の時間・要求の数・規模・初回の JavaScript・時間の記録を扱う。API の応答時間（p95 1 秒）は U1・U2 の k6 と Performance Validation が受け持つ。

ID は単位の接頭辞を付けて指す（例 `U3-NFR1.7`。NFR 要件の承認の場の決定）。

## 捨ての試しの結果（要点）

試しは Q1: A のとおり、リポジトリの外の試しの置き場で、2026-10-19 00:00〜00:03 に行った。

**試しの条件**
- 部品は `frontend/node_modules` のものだけを使った。
  - React 19
  - make-you-chic-ui の dist: 今の固定先 **`a9309c4`** のビルド。U4 が B3 で上げる `b0a1e79` ではない
  - vite・@vitejs/plugin-react（本番のビルドを作るため）
  - Playwright の Chromium（headless・Desktop Chrome・1280×720）
- 試しの画面: make-you-chic-ui の `Table` で、100 カラム（幅 160px）× 100 行を描いた。セルの値は架空の 100 字で、CSS の1行の省略で出した。
- 測り方: `U3-NFR1.8` のとおり。各 5 回測り、中央値で判定した。
- 「塗りまで」: 参考として、判定に使う時点の次の rAF（描いた最初の画面の配置と塗りを含む時点）までの時間も並べた。
- 眠り: `caffeinate -dims` で包み、流した後の `pmset -g log` に Sleep・DarkWake は無かった。リポジトリ・配備したアプリ・コンテナには触れていない。

**試しの結果**（中央値、カッコの中は最大。単位は ms）

| 試し | 場面 | NFR1.8 の値 | 塗りまで（参考） | 目標 |
|---|---|---|---|---|
| T1 セルごとの Tooltip（承認済みの形） | 件数 20→100（100×100） | 46（55） | 234（272） | 1,000 |
| T1 | 表を開く（20 件） | 42（70） | 44（73） | 500 |
| T2 Tooltip なし | 件数 20→100 | 29（37） | 205（228） | — |
| T3 はみ出したセルだけ Tooltip | 件数 20→100 | 452（532） | 464（555） | 1,000 |
| T4 詳細（100 カラム、10,000 字を含む） | 開いて見出しにフォーカス | 15（16） | 32（33） | 500 |
| T4 更新（100 カラムの入力） | 開く | 15（16） | 32（32） | 500 |

**設計への意味**
- T1（承認済みの形）のままにする。中央値は線の 0.8 秒を大きく下回り、塗りまででも目標の4分の1以下だった。表の仮想化は入れない。
- T3 は代わりの案にならない（F2: A）。100 字の値は幅 160px にほぼ必ず収まらず、10,000 セルのすべてがはみ出しになった。そのうえで1万セルの幅を測り直すため、T1 より約 10 倍遅い。
- NFR1.8 の測り終わりは、新しい DOM の配置と塗りの前にあたる。100×100 の表では、目にするまでの時間の約5分の1しか測らない。物差しは変えずに、「塗りまで」を参考として並べて記録する（F1: A）。
- 試しの画面には、骨組み・書式の関数・行のラベル・行の操作の Dropdown・NULL の印を入れていない。B5 の E2E 185 で確かめ直す。

## 冒頭の対応表

| U3 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR1.7 画面の側の時間の目標 | 1節 | 試しで T1 を確かめた。目標と物差しはそのまま |
| NFR1.8 測りの決まり | 4節 | 物差しはそのまま。「塗りまで」を参考に並べる（F1: A） |
| NFR1.9 要求の数と形 | 2節 | そのまま |
| NFR1.10 初回の JavaScript | 5節 | 3つの画面を別々の `lazy` にする |
| NFR2.10 規模 | 1節 | 仮想化なしで描く。代わりの案の順から T3 を外した（F2: A） |
| NFR2.11 長い値 | 1節 | DOM の文字を切らない |
| NFR3.7 DB の種類で分岐しない | 3節 | 部分（画面の側だけ） |
| NFR7.8 時間の記録 | 6節 | E2E 185 の組み立て（Q4: A）を前提にした見積もりを足した |

## 1. 描画の作り（NFR1.7・NFR2.10・NFR2.11）

**一覧の表**
- 列の定義は、定義ごとに `useMemo` で1回だけ作る。
- 行のラベル（`rowLabelParts`）は、ページの答えを受けたときにまとめて1回だけ求める。
- `BusinessDataTable` は `React.memo` にし、渡す関数を変わらないものにする。検索の入力の途中の値は `SearchPanel` の中だけに持ち、表を描き直さない。
- セルは、素の `span` に書式の後の文字を入れて Tooltip で包む（T1）。
  - 省略は CSS（`overflow: hidden; text-overflow: ellipsis; white-space: nowrap`）だけで行い、DOM の文字は切らない。
  - 読み上げは DOM の文字で値の全体を読むため、セルに `aria-label` を付けない。
- 仮想化は入れない。B5 の測りで目標を超えたら、承認の場で次の2つから選ぶよう諮る（F2: A）。どちらも承認済みの機能設計（D17）との差になる。
  - 重ねたときに作る Tooltip（表に1つの Tooltip を置き、重ねたセルの値を出す）
  - 表の仮想化

**詳細とフォーム**
- 詳細の値は `dl` で出し、`white-space: pre-wrap; overflow-wrap: anywhere` にする。10,000 字の値も、そのまま文字で描く。
- フォームの入力の部品は、カラムごとに値で memo にする。1字打つたびに 100 個を描き直さない。
- 変更の有無は、初めの値と今の値の O(カラムの数) の比べで求める（D20）。

## 2. 要求の数と形（NFR1.9・U3-NFR8.7）

- 定義は、テーブルの記憶にあれば読まない。読み直すのは次のときだけにする。
  - 読み取りの応答（成功・400）で `definitionChanged: true` を受けた
  - 409 `BUSINESS_DEFINITION_CHANGED` を受けた
  - `needsDefinitionReload` が真になった
  - 作業ロールの roleId が変わり、記憶を消した（Q2: A。`security-design.md` の 3節）
- 一覧の操作（検索・ページ・並べ替え・件数）1回で送る要求は、一覧の API の1本だけにする。
- 要求に世代を付けて、古い答えと、画面を離れた後の答えを捨てる。読み取りの要求は中断しない（サーバーの処理は止まらず、作りが増えるだけのため）。
- 画面独自の時間切れ・決まった間隔の読み直し・自動の再送は置かない。
  - 403 の後も業務データの API を送り直さない。メニューと作業ロールの読み直し（F8）は別の API で、今の形のまま。
  - 書き込みの結果が分からないときも自動では送り直さない（Q3: A）。
- 保存中は、5 秒を超えたら「時間がかかっています」を出す（`frontend/src/shared/slow-notice/useSlowNotice.ts`）。

## 3. DB の種類と値の形（NFR3.7、部分）

- 画面は DB の種類で分岐しない。値は文字か `null` のまま扱い、数に直さない。
- U1 の F3: A で、MySQL・MariaDB の長さ 2 以上の `bit` と空間の型は、定義の `valueType: BINARY` で届く。画面は「（バイナリ）」で出すだけで、新しい分岐は作らない。
- 日時の入力は秒まで（`step=1`）とし、秒より細かい値は送らない（U2 の Q6: A）。DB の値に秒の端数があっても、入力を変えなければ送らない（D20 の比べ）。
- 3種類の DB の振る舞いの確かめは、U1・U2 の結合テストが受け持つ（部分）。

## 4. 測りの作り（NFR1.8、F1: A）

- 測りの部品を `frontend/e2e/support/businessDataMeasure.ts` に新しく置く。
  - 今の `frontend/e2e/support/u7Measure.ts` は click だけで測り始める（84行）。1ページの件数の選択はネイティブの `<select>` の change で起きるため、測り始めのきっかけを click と change の2つにする。
  - 5 回の集計は、`frontend/e2e/support/u6Measure.ts` の `summarizeRuns` を使い回す。
- 判定の名前（仮）と、真になる条件:

| 判定の名前 | 真になる条件 | 目標 |
|---|---|---|
| `listShown100` | 行が 100 あり、最後のセルが描かれた | 1.0 秒 |
| `listShown20` | 既定の 20 件が描かれた | 0.5 秒 |
| `detailFocused` | フォーカスが h1 の見出しにあり、100 カラムが描かれた | 0.5 秒 |
| `formShown100` | 100 カラムの入力と読み取りの欄がそろった | 0.5 秒 |

- 判定の値は、判定が真になった後の次の rAF までの時間（NFR1.8 のまま）。
- あわせて「塗りまで」（その次の rAF まで）の中央値・最大・1回目を同じ記録に並べ、Build and Test に引き継ぐ。「塗りまで」は判定に使わない。
- 記録に残すのは、判定の名前と時間だけにし、行の値を残さない。

測りの終わりの作り（説明用の断片）:

```ts
// 判定が真になった後の次の rAF が判定の値。その次の rAF は「塗りまで」（参考）
requestAnimationFrame(() => {
  mark.end = performance.now()
  requestAnimationFrame(() => {
    mark.endWithPaint = performance.now()
  })
})
```

## 5. 初回の JavaScript（NFR1.10）

- 一覧・詳細・フォームの3つの画面を、それぞれ別の `lazy` で登録する。入口の chunk には入れない。
- B5・B6 の前と後で、次の2つを記録する。
  - `frontend/scripts/check-bundle-size.mjs` の入口の値（gzip）
  - 画面ごとの chunk の gzip の大きさ

## 6. 時間の記録（NFR7.8）

- 180・185 と E2E の全体を、B5・B6・Build and Test で `caffeinate -dims` を付けて測る。流した後の `pmset -g log` に Sleep・DarkWake があれば、結果を使わずに流し直す。
- 185 は1組を1件にした 20 件と、200% の確かめの2件にする（Q4: A。`security-design.md` の 7節）。
  - 1件で axe を約 8 回流す。
  - 一覧の axe は 100 カラム × 20 行で流す。axe を1万セルに当てると時間が延びるため、100 × 100 の行は測りの件だけで使う。
- E2E の全体が基準（前の Intent の 232 件・約 10分35秒）の 1.5 倍（約 15分53秒）を超えたら、ファイルごとの時間を記録して、185 の組み立てを依頼者に諮る。上限で落とさない。
- `./gradlew verify` と CI の時間を、B5・B6 の後と Build and Test で記録する。

## 上流との差

承認済みの文書は書き換えず、差をここに書く（`aidlc/spaces/default/memory/project.md` の決まり）。

1. **代わりの案の順から T3 を外した（F2: A）**: 試しで T3 は T1 より遅かった。目標を超えたときの候補は「重ねたときに作る Tooltip」と「仮想化」の2つにし、承認の場で諮る。
2. **「塗りまで」を参考として並べる（F1: A）**: `U3-NFR1.8` の物差し（判定の後の次の rAF）は変えない。その次の rAF までの時間を、判定に使わない値として同じ記録に並べる。
3. **書き込みの結果が分からないときの表示（Q3: A）**: 2xx で本文の形が崩れていれば、保存できたものとして扱う。機能設計の D5（形の崩れは通信の失敗）との差で、中身は `security-design.md` の 5節。性能には、自動の再送をしないことで響く（2節）。
4. **作業ロールの切り替えで記憶を消す（Q2: A）**: 機能設計の D4 の定義の読み直しのきっかけに、作業ロールの roleId の変化が加わる（2節）。中身は `security-design.md` の 3節。
5. **U4 の R-01・R-03 を前提にした**: 185 の作りは、U4 のレビューの R-01（menuOwner の選択子を Dropdown のトリガーに絞る）と R-03（owner の末尾は数字か `r<n>` だけ）が直った形を前提にする（`security-design.md` の 7節）。U4 の承認の場で別の形になったら、U3 をそろえ直す。

## 後の段に回すこと

- **Infrastructure Design**: 次の4つ（`security-design.md` の「後の段に回すこと」と同じ）。
  - 180 の対象DB と、040 の前提（`frontend/e2e/040-dsl-admin.e2e.ts` 21行の「E2E の環境には対象DB の接続先が無い」）
  - 専用のスキーマ
  - 読み取り・書き込みのアカウントへの付与
  - 管理者の資格情報の渡し方
- **コード生成の計画**:
  - 測りの判定の名前と、見本の生成の関数を確定する。
  - R-04（作業ロールの確かめの後の画面）と R-06（保存の 403 の文）。
- **B5**: E2E 185 の中の測りで、本物の画面（骨組み・書式・行の操作・NULL の印、make-you-chic-ui の `b0a1e79`）で確かめ直す。目標を超えたら、上の2つの候補を承認の場で諮る。

## U1・U2・U4 とそろえる点

- **U1**
  - API の応答時間の判定（k6 の `biz` の場面）は U1 のものを使い、U3 は測り直さない。
  - 長い値の字数（一覧 100 字・詳細 10,000 字）は U1 の NFR2.5 に合わせる。
  - ページの上限 1,000,000 を画面のページ送りに当て、それを超える番号へは移らない。
- **U2**
  - 書き込みの API の時間（p95 1 秒）を前提にする。
  - pattern は画面で当てない。`PATTERN_TIMEOUT` の文を画面が用意する。
- **U4**
  - E2E の全体の時間は U4 の NFR7.2 と同じ回で測り、180・185 の分を分けて書く。目安の 1.5 倍も同じ。
  - 表示の組の 20 組（`frontend/e2e/support/displayCombos.ts`）は U4 と同じものを使う。

## Sources

- この段の答え
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/nfr-design-questions.md`（Q1〜Q4: A、まとめの確認: Looks correct）
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/nfr-design-followup-questions.md`（F1・F2: A、まとめの確認: Looks correct）
- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-requirements/performance-requirements.md`（NFR1.7〜NFR1.10・NFR2.10・NFR2.11・NFR3.7・NFR7.8）・`tech-stack-decisions.md`
- NFR 要件のレビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u3-businessdata-screens/00d44a7a2452e6b7/1.json`（R-02）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/functional-design/functional-spec.md`（D4・D5・D14・D17・D20・D23）・`frontend-components.md`（4節）
- U1・U2・U4 の NFR 設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-design/performance-design.md`・`aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-design/performance-design.md`・`aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-design/performance-design.md`
- コード
  - `frontend/e2e/support/u7Measure.ts`（84行の click だけの測り始め）・`frontend/e2e/support/u6Measure.ts`・`frontend/e2e/support/displayCombos.ts`
  - `frontend/scripts/check-bundle-size.mjs`
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Table/Table.tsx`・`Tooltip/Tooltip.tsx`
- 捨ての試し: リポジトリの外の試しの置き場の画面・台本・結果（T1〜T4）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture の物差しを変える決まり）、`aidlc/spaces/default/memory/project.md`（学び）

## Assumptions & Open Questions

- 前提: 試しの値は、PC の1台・headless の Chromium・make-you-chic-ui の `a9309c4` でのもの。本物の画面の上乗せ（書式・行のラベル・Dropdown・骨組み）を含めても目標に収まると見込むが、B5 の 185 で確かめるまでは未検証。
- 前提: 一覧の値は、ほとんどがはみ出す長さ（U1 の上限の 100 字）で届く見込み。短い値が多い表では Tooltip の数は同じでも、配置の時間は短くなる。
