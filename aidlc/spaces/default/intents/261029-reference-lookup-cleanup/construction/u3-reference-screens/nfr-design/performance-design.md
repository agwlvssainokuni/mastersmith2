# 性能の設計 — U3 u3-reference-screens

U3（作成・更新の画面の参照の部品とピッカーのダイアログ）の性能の要件 NFR2.41〜NFR2.45 を、どの作りで満たすかを決める。U3 は種類 `ui` の単位で、参照先の API の応答時間（p95 1 秒・3 秒の線）は U1 の k6 の場面（`refDefinition200`・`refPickerEquals`・`refLabel`・`refPickerContains` など）が受け持つ。この文書は画面の側の要求の数と形・同時の数・描く量・初回の JavaScript・時間の記録に限る。文章で ID を指すときは、単位の接頭辞を付ける（例 `U3-NFR2.42`）。

捨ての試しは行わない。U3 の決めは画面の中の作り（同時の数の上限・描く部品の構造）で、DB や外の部品の振る舞いに頼る前提が無いため。200 件の `radio` と同時の数の上限は、Code Generation の画面の部品のテストで決めた値の境界を確かめる（Q2: A・Q3: A）。

## 冒頭の対応表

| 要件 | この文書の節 | 決め（質問） |
|---|---|---|
| NFR2.41 状態の移り変わり（時間の目標は置かない） | 1節 | Q4: A（「時間がかかっています」を出さない） |
| NFR2.42 要求の数と形 | 2節 | Q2: A（ラベルの1件読みは同時に 3 本まで） |
| NFR2.43 規模（200 件の選択肢・20 件の結果・長い値） | 3節 | Q3: A（200 件の `radio` は部品のテストと E2E 195 の axe） |
| NFR2.44 初回の JavaScript | 4節 | この段の決め（測りは一時の手順。スクリプトに足さない） |
| NFR2.45 時間の記録 | 5節 | 決まっていること（`project.md` の学び） |

## 1. 状態の移り変わり（NFR2.41、Q4: A）

- 画面の側の時間の目標は置かない（NFR 要件の Q1: A）。画面は「待つ間の表示」と「答えの後の表示」が決めたとおりに移ることだけを持つ。
- ピッカーの検索は、押してから答えまで「検索中」（`role="status"`、「検索」の `Button` の `loading`）だけを出す。**「時間がかかっています」の知らせは出さない**（Q4: A）。そのため、ピッカーの検索に `src/shared/slow-notice/useSlowNotice.ts`（5 秒の時計）を使わない。二重の押下の防止は、`useLookupSearch` の世代と `searching` の状態で行う（機能設計の D24）。
- 時間切れ（U1 の問い合わせの上限、約 11 秒）で 503 が返れば、既存の 503 の文（`tables.picker.failed`）に移る。画面は独自の時間切れの時計を持たない。
- 保存の 5 秒の知らせ（既存の `useRowWrite` の `useSlowNotice`）は変えない。
- 更新の画面の LOOKUP の項目は、ラベルの1件読みを待つ間（列に並んで待つ間を含む。2節）も `aria-busy="true"` で値だけを出す。答え（成功・失敗）が来たら外す。
- 確かめは画面の部品のテストで、遅らせた応答（手で解く Promise。時計に頼らない）を使い、待つ間と答えの後の表示を `waitFor` で確かめる。

## 2. 要求の数と形（NFR2.42、Q2: A）

### 2.1 ラベルの1件読みの同時の数

- `useReferenceLabels` は、値が空でない `AVAILABLE` の LOOKUP の項目を、定義のカラムの順に並べた列にし、**同時に送るのは 3 本まで**とする。残りは、送った要求のどれかが答え（成功・失敗のどちらでも）を返したら、列の先頭から1本ずつ送る。
- 上限は `features/tables/reference/` の画面の定数（例 `LABEL_READ_CONCURRENCY = 3`）にする。設定や定義の応答からは受けない。
- 理由: 使い捨ての環境の読み取りのプールの上限は 5（U1 の `performance-design.md`）。1人の画面が5本を使い切らず、ほかの利用者の分を残す。LOOKUP の項目は1つの表で多くても数個で、待ちは `refLabel` の時間（索引あり 0.5 ms 前後、U1 の試し）の数倍に収まる見込み。
- 失敗（403・503・通信の失敗・写しの失敗）は、その項目を値だけにし（D18）、枠を空けて次を送る。失敗で列を止めない。
- 画面を離れた・定義を読み直した（`reload`・`retry`）ときは、世代を進め、列に残った項目を送らずに捨て、送った要求の答えも捨てる。

```text
// 説明のための擬似コード（名前は Code Generation で決める）
queue = 値のある AVAILABLE の LOOKUP の項目（定義の順）
inFlight = 0; gen = 現在の世代
pump():
  while inFlight < LABEL_READ_CONCURRENCY かつ queue が空でない:
    item = queue.shift(); inFlight += 1
    getReferenceLabel(item.source, item.value)
      .then(label → gen が同じなら labels[item] = label)
      .catch(() → gen が同じなら labels[item] = null)
      .finally(() → gen が同じなら { inFlight -= 1; loading から item を外す; pump() })
```

- 確かめ（`useReferenceLabels.test.tsx`）: 手で解く Promise で、(1) 項目 3 つ（上限ちょうど）で 3 本が同時に出る、(2) 項目 4 つ（上限を超える）で最初は 3 本だけ出て、1本が答えると4本目が出る、(3) 答えの順が送った順と違っても各項目に正しいラベルが入る、(4) 失敗でも次が出る、(5) 離れた後は残りを送らず答えも捨てる。REFERENCE の項目と値が空の項目は列に入らない。

### 2.2 そのほかの要求

機能設計の決めのまま変えない。作りの置き場だけを書く。

| 要求 | 回数と形 | 置き場 |
|---|---|---|
| 定義の読み込み | 作成・更新の画面を開くたびに `purpose` つきで1回。機能の中の定義の記憶を読み書きしない（D1・D2） | `useTableDefinition` |
| ピッカーの検索 | 「検索」・ページ送り・「再度検索」・「前のページへ」の1回につき1本。空の条件だけ・1,001 文字以上・`EQUALS` で型として読めない値では送らない（D23）。世代で古い答え・閉じた後の答えを捨て、検索中のページ送りの押下を捨てる（D24・D29） | `useLookupSearch`・`pickerSearch.ts` |
| 自動の読み直し | しない（ポーリングなし）。画面は独自の時間切れを置かない | — |

## 3. 規模（NFR2.43、Q3: A）

- REFERENCE の選択肢は最大 200 件（U1 の上限）を、仮想化なしで、サーバーの並びのまま描く（D6）。画面は並べ直さない（U1 の F1: A。文字のラベル・値は先頭 100 字で並び、先頭 100 字が同じ行は主キーの順で届く）。
- `radio` の 200 件は、make-you-chic-ui の `RadioGroup` に `legend`（見えない項目名）を付けた既存の形（`BusinessDataFormPage.tsx` の 839〜848 行と同じ）で描く。`RadioGroup` は選択肢ごとにネイティブの `<input type="radio">` を同じ `name` で描く（`RadioGroup.tsx` の 62〜75 行）ため、矢印のキーで選択肢を移れ、Tab では群に1回だけ止まる。
- **描く時間の目標は置かない**（Q3: A）。確かめは次の2つ。
  - 画面の部品のテスト（`ReferenceSelectField.test.tsx`）: `formPart` が `radio` で 200 件の選択肢を描き、(1) `radio` が 200 個あり同じ `name`、(2) 各 `radio` の名前（ラベルの文）が全文（100 文字以上の見本を含む）で DOM にある、(3) Tab で群に入り、矢印のキーで 1 件目から 200 件目まで移れる（user-event 14.6 系は `radio` の矢印のキーの移動を持つ）、(4) vitest-axe の違反が 0 件。
  - E2E 195 に「REFERENCE の `radio` 200 件」の状態を足し、ブランドカラーとテーマのすべての組と 375px で axe を流す（`security-design.md` の 3.4）。200 件の定義は API の答えの差し替えで作り、見本を本物の応答と `shapeCompare` で比べる。
- ピッカーの結果は1ページ 20 件で、件数を数えない（D25）。
- 長いラベル・値は画面で切らず、CSS の折り返し（`overflow-wrap: anywhere`）で全文を DOM に残す（D25）。確かめは 100 文字以上の見本の全文が DOM にあること（部品のテスト）と、375px ではみ出さないこと（E2E 195）。
- 遅いと分かったとき（Build and Test の E2E の時間の記録や手元の操作で）の候補は、`radio` から `select` に替える DSL の案内（運用）とし、仮想化は入れない。候補は承認の場で諮る（この段では決めない）。

## 4. 初回の JavaScript（NFR2.44）

- 参照の部品はすべて `features/tables/reference/` に置き、業務データの画面の `lazy` の chunk に入れる。入口（`main`）から静的に import しない。`registration.ts` は変えない（`lazy` を保つ）。確かめは既存の `registration.test.ts` の `lazy` の確かめを保つこと。
- 既存の `frontend/scripts/check-bundle-size.mjs`（入口の gzip の合計、目安 500 KB で警告）は**変えない**。
- 業務データの画面の chunk の大きさは、スクリプトに足さず、Code Generation の**一時の手順**（リポジトリの外の写しで流す node の数行）で測る。理由: 値は変更の前と後の記録のためだけに要り、`./gradlew verify` の中身を変えずに済む。入口の測りと同じ `gzipSync` の数え方にそろえる。

```text
// 一時の手順の擬似コード（npm run build の後に、dist/.vite/manifest.json を読む）
entryFiles = 入口から静的な imports をたどったファイル（check-bundle-size.mjs と同じ集め方）
roots = キーが 'src/features/tables/' で始まり isDynamicEntry が真の項目
for root in roots:
  files = root から imports を静的にたどったファイル − entryFiles のうち '.js' のもの
  記録: root のキー、各ファイルの gzipSync の大きさ、その合計
記録: roots の全体の合計（同じファイルは1回だけ数える）
```

- 記録する値: 入口の値（`check-bundle-size.mjs` の出力）と、起点ごとの値と合計を、U3 の Bolt の前と後でそれぞれ。置き場はコード生成の成果物（`code-summary.md`）。

## 5. 時間の記録（NFR2.45）

- E2E 190・195 と E2E の全体の時間を、`caffeinate -dims` で包んで測る。流した後に `pmset -g log` で Sleep・DarkWake を確かめ、入っていたら結果を使わずに流し直す（`project.md` の学び）。
- 195 の状態は `security-design.md` の 3.4 の 12 個（Q1: A と Q3: A で、NFR 要件の 10 個から増えた）。組（ブランドカラー×テーマ、幅は既定と 375px）は既存の `frontend/e2e/support/displayCombos.ts` を使う。1件のテストで、1つの組について状態を順に作って axe を流す形にし、件数を組の数に抑える（前の Intent の 185 と同じ形）。
- E2E の全体が、変更の前の E2E の全体の時間の 1.5 倍を超えたら、ファイルごとの時間を記録し、195 の組と状態の分け方を依頼者に諮る。上限で落とさない。変更の前の値は Code Generation の最初の実行で測って写す。
- `./gradlew verify` と CI の時間を、U3 の Bolt の後と Build and Test で記録する（`team.md`）。

## 上流との差

承認済みの文書は書き換えず、差をここに書く（`aidlc/spaces/default/memory/project.md` の決まり）。

1. **ラベルの1件読みの「互いに待たない」（機能設計の D18）**: 同時に 3 本までとし、4本目からは前の答えを待つ（Q2: A）。D18 の「フォームの表示・入力・保存を止めない」は変わらない（待つのはラベルの読み込みだけ）。
2. **200 件の `radio` の確かめ（NFR 要件の NFR2.43「見た目と時間は NFR 設計で確かめる」）**: 時間は測らず、部品のテストの構造とキーボードの確かめと、E2E 195 の axe にした（Q3: A）。見た目は axe と 375px のはみ出しの確かめで見る。
3. **E2E 195 の状態の数**: NFR 要件の NFR4.44 の状態（入力不可の2つの理由を分けて数えて 10 個）に、`radio` 200 件を足し、作成の必須の入力不可を「開いた直後」と「保存を押した後」の2つに分けて、12 個に増えた（`security-design.md` の 3.4）。NFR2.45 の 1.5 倍の線で時間を見る。

## 後の段に回すこと

- **Code Generation**:
  - `LABEL_READ_CONCURRENCY` の名前と置き場を決め、2.1 の5つの確かめを書く。
  - 4節の一時の手順で、Bolt の前と後の値を記録する。
  - 5節の時間を記録する。変更の前の E2E の全体の時間を最初の実行で測る。
- **Build and Test**: E2E の全体・`verify`・CI の時間の記録（NFR2.45）、`features/tables` のカバレッジの値。
- **Performance Validation**: この単位からは引き継がない（参照先の API の時間は U1）。
- make-you-chic-ui: 性能について足りない口は無い（`RadioGroup` は 200 件をそのまま描け、矢印のキーはネイティブの振る舞い）。依頼は出さない。

## U1・U2 とそろえる点

- **U1**: 選択肢の上限 200（201 で `TOO_MANY`）、ピッカーの 20 件と `hasNext`、ラベルの1件読みの応答の形（`rows` 1件・`page: null`）、先頭 100 字の並び（F1: A）を前提にする。読み取りのプールの上限 5 は Q2 の理由に使う。
- **U2**: 保存の時間と 503 の分け方は既存のまま（`writeOutcome.ts` を変えない）。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/nfr-design-questions.md`（Q1〜Q4 すべて A）
- NFR 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-requirements/performance-requirements.md`（NFR2.41〜NFR2.45）、`tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`（D1・D2・D6・D18・D23〜D25・D29、9節）、`frontend-components.md`（4.5節・7節）
- U1 の NFR 設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/performance-design.md`（k6 の場面、読み取りのプール 5、F1: A）
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C3）
- コード（読み取りだけ）: `frontend/src/features/tables/BusinessDataFormPage.tsx`（839〜848 行の `RadioGroup`）、`frontend/src/shared/slow-notice/useSlowNotice.ts`、`frontend/scripts/check-bundle-size.mjs`、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/RadioGroup/RadioGroup.tsx`（固定先 `4c63d11`、62〜110 行）、`frontend/node_modules/@testing-library/user-event`（14.6.8、`keydown.js` の `walkRadio`）
- 書き方の手本: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/performance-design.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（学び: `caffeinate`・`pmset`）

## Assumptions & Open Questions

- [assumption] LOOKUP の項目は1つの表で多くても数個（Q2 の見立て）。項目が多い表でも上限 3 で待ちが伸びるだけで、表示・入力・保存は止まらない。
- [assumption] 配備した環境の読み取りのプールの上限も 5 以上である（使い捨ての環境の値から見立てた）。
- 測っていないため、NFR2.41〜NFR2.45 は Code Generation と Build and Test で確かめるまで `Unverified` とする。
