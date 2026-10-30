# 論理の部品 — U3 u3-reference-screens

この文書は、U3 の NFR の設計（`performance-design.md`・`security-design.md`）がどの部品に当たるかをまとめる。部品は L1〜L9 の 9 個とする。名前と置き場は機能設計の `frontend-components.md` の 2節の仮の名前で、コード生成で変えるときは差を記録する。文章で ID を指すときは、単位の接頭辞を付ける（例 `U3-NFR2.42`）。

U3 は画面だけの単位で、対象DB・内部DB の接続を持たない。失敗の及ぶ範囲は、1つの画面（作成・更新）の中の1つの項目か、1つのダイアログに閉じるように作る。

## 冒頭の対応表

| 部品 | 置き場 | 受け持つ NFR 要件 | 成果物（`dist`）に入るか |
|---|---|---|---|
| L1 API の呼び出し | `frontend/src/features/tables/businessDataApi.ts`（足す） | U3-NFR1.43・2.42 | 入る（業務データの画面の chunk） |
| L2 写しの関数 | `frontend/src/features/tables/businessDataParse.ts`（足す） | U3-NFR1.45・7.41 | 入る |
| L3 純粋な関数 | `features/tables/reference/referenceField.ts`・`pickerSearch.ts`、`problemMessage.ts` の `referenceErrorKey`、`formDiff.ts`・`formValidation.ts` の口 | U3-NFR1.41・1.44・1.46・2.42・5.42・6.42 | 入る |
| L4 ラベルの読み込みの列 | `features/tables/reference/useReferenceLabels.ts`（同時の数の定数を含む） | U3-NFR2.41・2.42 | 入る |
| L5 ピッカーの検索の状態 | `features/tables/reference/useLookupSearch.ts` | U3-NFR2.41・2.42・4.42 | 入る |
| L6 参照の部品 | `features/tables/reference/ReferenceSelectField.tsx`・`ReadonlyReferenceField.tsx`・`LookupField.tsx`・`LookupPickerDialog.tsx`、CSS | U3-NFR1.41・1.42・2.43・4.41〜4.45 | 入る |
| L7 作成・更新の画面 | `features/tables/BusinessDataFormPage.tsx`・`useTableDefinition.ts`（足す） | U3-NFR1.42・1.44・4.45・7.41 | 入る |
| L8 文言 | `features/tables/messages.ts`（足す） | U3-NFR6.41・6.42 | 入る |
| L9 E2E とその補助 | `frontend/e2e/190-reference-picker-flow.e2e.ts`・`195-reference-picker-accessibility.e2e.ts`、`frontend/e2e/support/` の見本と DSL・`secretValues.ts` の利用 | U3-NFR1.47・2.43・2.45・4.44・5.43 | 入らない |

外の部品（変えずに使う）: make-you-chic-ui（固定先 `4c63d11`）の `Modal`・`Table`・`FormField`・`TextInput`・`Select`・`RadioGroup`・`Button`・`Alert`、`frontend/src/shared/` の `scroll-region`・`null-value`・`validation/codePoints.ts`。make-you-chic-ui への新しい依頼は無い（`security-design.md` の 2.2・7節）。

## 1. 部品ごとの役割と境界

- **L1 API の呼び出し**
  - 参照先の検索とラベルの1件読みの2つの呼び出しと、定義の `purpose` を足す。値はすべて本文に置き、道は固定（U3-NFR1.43）。
  - 本文は既存の `postWithinLimit` を通す。自動では送り直さない。失敗は既存の `ApiError` で投げる。
- **L2 写しの関数**
  - 応答を `unknown` で受けて形を確かめる。知らない `status`・`kind` は入力不可に倒し、`valueType` の崩れは通信の失敗にする。ラベルの1件読みは検索と別の写しで受ける（D18）。
- **L3 純粋な関数**
  - 部品の選び方（`chooseFieldKind`）、表示の文（`optionText`）、条件の組み立てと確かめ、ページの表示（`pickerPaging`）、誤りの鍵（`referenceErrorKey`）。副作用を持たず、性質ベースのテストの対象にする。
  - 画面は権限を判定しない。定義の応答の印だけから選ぶ（U3-NFR1.44）。
- **L4 ラベルの読み込みの列**（Q2: A）
  - 値のある `AVAILABLE` の LOOKUP の項目を定義の順に並べ、同時に 3 本まで送る。失敗でも枠を空けて次を送る。
  - 世代で、画面を離れた後・定義を読み直した後の答えを捨て、列の残りを送らない。
  - 状態（`labels`・`loading`）は React の状態だけに持ち、ブラウザの保存と機能の記憶を使わない。
- **L5 ピッカーの検索の状態**（Q4: A）
  - 開くたびに初期の状態から作る。検索は1回の操作に1本、世代で古い答えを捨て、検索中のページ送りの押下を捨てる。
  - 「時間がかかっています」の時計を持たない（`useSlowNotice` を使わない）。
- **L6 参照の部品**（Q1: A・Q3: A）
  - 値を文字で描き、印・`id` に位置の番号だけを使う。
  - `ReadonlyReferenceField` は、作成の必須の入力不可の項目で、開いた直後は D13 の文を `FormField` の `helperText` に、保存を押した後は `error` に渡す（`security-design.md` の 2.1）。
  - `ReferenceSelectField` は `radio` 200 件を `RadioGroup`（見えない `legend`）でそのまま描く。
- **L7 作成・更新の画面**
  - 用途つきで定義を読み、部品を選び、入力不可の参照の項目を本文から外す。保存の前の検証で作成の必須の入力不可の項目を誤り（`error`）にし、誤りのまとめへフォーカスを移す。
  - 一覧・詳細の画面は変えない（U3-NFR7.41）。
- **L8 文言**
  - 日本語と英語の鍵を同じ集合で持つ。この段で鍵を足さない（`security-design.md` の 5節）。
- **L9 E2E とその補助**
  - 190: 自分で作った利用者・ロール・専用のスキーマの行だけを使い、初期管理者と見本の行を変えない。参照先のラベルを値のファイル（`businessValue`）に書き、報告の部品に検索させる。
  - 195: 12 の状態をブランドカラーとテーマのすべての組と 375px で流す（`security-design.md` の 3.4）。差し替えの見本は本物の応答と形で比べる。

## 2. 失敗の及ぶ範囲

| 失敗 | 及ぶ範囲 | 画面の振る舞い |
|---|---|---|
| 定義の読み込みの失敗（503 など） | 作成・更新の画面全体 | 既存のとおり「開けない・つながらない」を出し、フォームを出さない |
| ラベルの1件読みの失敗（403・503・写しの失敗） | その1項目のラベルだけ | 値だけを出し、誤りの文を出さない。列の次の項目は送る（L4）。表示・入力・保存は止めない |
| ラベルの読み込みの待ち（同時の数の上限） | その項目のラベルの表示の遅れだけ | `aria-busy` で値だけを出して待つ |
| ピッカーの検索の失敗（503・403・400） | そのダイアログだけ | 決めた文を出し、フォームの値は変えない。閉じれば元の画面に戻る |
| 保存の拒否（400・409・503） | その保存の1回 | 既存の誤りのまとめと項目の誤り。入力は消えない |
| 必須の入力不可の参照の項目（行き止まり、M1: A） | 作成の画面の保存 | 開いた直後は説明文、保存を押すと誤り。要求は送らない（Q1: A） |
| 応答の形の崩れ・知らない値 | その応答を使う部品 | 通信の失敗か入力不可に倒す（安全側） |

## 3. 共有するもの

| 共有するもの | 使う部品 | 守り |
|---|---|---|
| 対象DB の読み取りのプール（サーバー側、使い捨ての環境で上限 5） | L4・L5 の要求 | L4 は1画面で同時に 3 本まで（Q2: A）。L5 は1回の操作に1本 |
| make-you-chic-ui の部品（固定先 `4c63d11`） | L6・L7 | 口をソースで確かめた（`security-design.md` の 2.2、`performance-design.md` の 3節）。部品の内側のクラスを frontend の CSS から指さない |
| E2E の共有の DSL と見本の対象DB | L9 と既存の 180 | 180 と共有する DSL に参照の表を足し、180 の全件が通ることを確かめる。初期管理者・見本の行を変えない |
| 業務データの画面の chunk | L1〜L8 | 入口に入れない（`lazy` のまま）。大きさは一時の手順で前と後を記録する（`performance-design.md` の 4節） |

## 上流との差

承認済みの文書は書き換えず、差をここに書く（`aidlc/spaces/default/memory/project.md` の決まり）。

1. **L4 の同時の数の上限（機能設計の D18「項目ごとの要求は互いに待たない」）**: 同時に 3 本までにした（Q2: A。`performance-design.md` の上流との差 1）。
2. **L6 の `ReadonlyReferenceField` の誤りの出し方（`frontend-components.md` 4.2、D13）**: 開いた直後は `helperText`、保存を押した後は `error`（Q1: A。`security-design.md` の上流との差 1）。
3. **L9 の 195 の状態の数**: 12 個にした（`security-design.md` の 3.4）。

## 後の段に回すこと

- **Code Generation**: L1〜L9 の名前と置き場の確定、L4 の定数名、L9 の前提の行（顧客 3 行・商品 25 行・注文）、一時の手順による chunk の大きさの記録。
- **承認の場**: 開いた直後の D13 の文の見た目（`security-design.md` の 2.3）。受け入れないときだけ make-you-chic-ui への依頼を出す。
- make-you-chic-ui: 今の段で足りない口は無く、依頼は出さない。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/nfr-design-questions.md`（Q1〜Q4 すべて A）
- この段の設計: `performance-design.md`・`security-design.md`（同じフォルダ）
- NFR 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-requirements/performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`・`frontend-components.md`（1節・2節・4節）
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C3）
- U1 の NFR 設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/performance-design.md`（読み取りのプール 5）
- 書き方の手本: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/logical-components.md`

## Assumptions & Open Questions

- [assumption] 部品の名前と置き場は機能設計の仮の名前のまま。コード生成で変えるときは差を記録する。
- 未決の点は、承認の場で確かめる開いた直後の文の見た目だけ（`security-design.md` の 2.3）。
