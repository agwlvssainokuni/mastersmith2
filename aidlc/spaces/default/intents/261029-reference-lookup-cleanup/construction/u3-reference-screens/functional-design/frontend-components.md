# 画面の部品 — U3 u3-reference-screens

`functional-spec.md` の決め（D1〜D39）と流れ（F1〜F8）を、部品の構成・props・状態・API の受け渡し・文言・テストの計画に落とす。決めの理由と出典は `functional-spec.md` を正とし、ここでは繰り返さない。型の断片は説明のためのもので、名前は Code Generation で変えてよい（`any`・`enum`・`export default` は使わない。値は `unknown` か文字列リテラルの union で受ける）。

出典: 単位 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`、要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、部品 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`、画面イメージ `inception/refined-mockups/interaction-spec.md`、契約 `inception/contract-design/contract-summary.md`（C2・C3）、既存のコード `frontend/src/features/tables/`。

## 1. 部品の構成

```text
BusinessDataFormPage（既存。作成・更新の画面 S3・S4）
├─ useTableDefinition(ref, purpose)            … 定義を purpose つきで読む（D1・D2）
├─ useRowDetail（既存。更新の今の値）
├─ useReferenceLabels(definition, row)        … LOOKUP の保存済みの値のラベルを1件ずつ読む（D18。新規）
├─ 誤りのまとめ（既存。role="alert"）
└─ カラムごと: chooseFieldKind(column, mode, truncated)（D5。純粋な関数）
   ├─ 既存の読み取りの表示（span）
   ├─ FieldInput（既存。formPart に従う）
   ├─ ReferenceSelectField（新規。M1）                 … Select か RadioGroup を FormField で包む
   ├─ ReadonlyReferenceField（新規。M2・M3・M7）        … TextInput（readOnly）を FormField で包む
   └─ LookupField（新規。M4）                           … TextInput（readOnly）＋ Button「選ぶ…」「クリア」
      └─ LookupPickerDialog（新規。M5。開いている間だけ描く） … Modal
         ├─ 条件の form（TextInput × searchFields、Button「検索」）
         ├─ 状態の文（role="status"・role="alert"・aria-live="polite"）
         └─ ScrollRegion（既存の shared）
            └─ Table（make-you-chic-ui）… 1列目「選ぶ」の Button、続けて listColumns
```

## 2. 置き場

| 置き場 | 置くもの |
|---|---|
| `frontend/src/features/tables/reference/` | `ReferenceSelectField.tsx`・`ReadonlyReferenceField.tsx`・`LookupField.tsx`・`LookupPickerDialog.tsx`・`useLookupSearch.ts`・`useReferenceLabels.ts`、純粋な関数 `referenceField.ts`（`chooseFieldKind`・`optionText`・`referenceErrorKey`）・`pickerSearch.ts`（`searchInputFor`・条件の組み立て・確かめ・`pickerPaging`）、それぞれの `*.test.ts(x)`、CSS |
| `frontend/src/features/tables/`（既存のファイルに足す） | `businessDataApi.ts`（参照先の API の2つの呼び出しと `purpose`）、`businessDataParse.ts`（`reference` と参照先の行の写し）、`businessDataTypes.ts`（型）、`useTableDefinition.ts`（`purpose`）、`BusinessDataFormPage.tsx`（部品の選び方・誤りの写し）、`problemMessage.ts`（参照の誤りの文の鍵）、`messages.ts`（文言） |
| `frontend/src/shared/` | 変えない。`scroll-region`・`null-value`・`validation/codePoints.ts` を使う |
| `frontend/e2e/` | `190-reference-picker-flow.e2e.ts`（流れ）、`195-reference-picker-accessibility.e2e.ts`（axe。本数に数えない）、`support/` の見本と DSL（D39） |

機能どうしの import の決まり（`no-restricted-imports`）は変わらない（`features/tables` の中だけで閉じる）。下位のフォルダ `reference/` は `features/tables` の中の置き場で、新しい機能ではない（`registration.ts` は変えない）。

## 3. API の受け渡しの型

### 3.1 定義に足す型（`businessDataTypes.ts`）

```ts
export const REFERENCE_KINDS = ['REFERENCE', 'LOOKUP'] as const
export type ReferenceKind = (typeof REFERENCE_KINDS)[number]
export type ReferenceStatus = 'AVAILABLE' | 'NO_PERMISSION' | 'TOO_MANY'

export interface ReferenceOption {
  value: string
  /** 参照先の labelColumn の値（日英の表示名ではない）。NULL は null */
  label: string | null
}
export interface PickerSearchField {
  column: string
  displayName: { ja: string; en: string }
  operator: 'EQUALS' | 'CONTAINS'
  /** 既存の ColumnDefinition.valueType と同じ語彙（VALUE_TYPES）。入力の部品を選ぶ（D22） */
  valueType: ValueType
}
export interface PickerListColumn {
  column: string
  displayName: { ja: string; en: string }
  /** 同じ語彙。形の確かめに使い、セルは文字のまま出す（D25） */
  valueType: ValueType
}
```

```ts
export interface PickerInfo {
  searchFields: readonly PickerSearchField[]
  listColumns: readonly PickerListColumn[]
}
export interface ReferenceField {
  kind: ReferenceKind
  /** 知らない値は写しで NO_PERMISSION に倒す（D3） */
  status: ReferenceStatus
  options: readonly ReferenceOption[]
  picker: PickerInfo | null
}
// ColumnDefinition に足す: reference?: ReferenceField | null（null は参照でない・purpose なし）
// FORM_PARTS に 'lookup' を足す（D3）
export type DefinitionPurpose = 'CREATE' | 'UPDATE'
```

### 3.2 参照先の行（`businessDataTypes.ts`）

```ts
export interface ReferenceRow {
  value: string
  label: string | null
  /** listColumns の並びの値（ラベルの1件読みでは空） */
  cells: readonly (string | null)[]
}
/** 検索の応答（page は 1 以上の数。page が null の応答は検索の写しでは形の崩れ） */
export interface ReferenceRowPage {
  rows: readonly ReferenceRow[]
  page: number
  hasNext: boolean
}
/**
 * ラベルの1件読みの応答から取り出す値（D18）。
 * U1 は rows 1件（cells は空）・page: null・hasNext: false で返す。
 * parseReferenceLabel は page・hasNext・cells を見ず、rows がちょうど1件で
 * value・label の形が正しいことだけを確かめる
 */
export interface ReferenceLabel {
  value: string
  label: string | null
}
export interface ReferenceSource {
  schemaName: string
  tableName: string
  column: string
}
```

### 3.3 呼び出し（`businessDataApi.ts`）

| 関数 | 本文 | 応答の写し | 失敗 |
|---|---|---|---|
| `getDefinition(ref, definitionId?, purpose?)` | 既存の本文に、`purpose` があれば `purpose` を足す | 既存の `parseTableDefinition` に `reference` の写しを足す | 既存（`ApiError` を投げる） |
| `searchReferenceRows(source, conditions, page)` | `{ schema, table, column, conditions, page }`（`conditions` は値が空でないものだけ） | `parseReferenceRowPage`（形が違えば通信の失敗） | `ApiError` を投げる（D27 で分ける） |
| `getReferenceLabel(source, value)` | `{ schema, table, column, value }` | `parseReferenceLabel`（検索の写しと別。`page`・`hasNext`・`cells` を見ず、`rows` がちょうど1件で `value`・`label` の形が正しいこと。`page: null` の成功の応答を受ける）の `label` | `ApiError` を投げる。写しの失敗（0件・2件以上・形の崩れ）も通信の失敗と同じ扱い（D18 で値だけに倒す） |

- 道は `BUSINESS_DATA_PATHS` に `referenceRows: '/api/tables/reference/rows'` を足す。本文の上限は既存の `MAX_READ_BODY_BYTES` で、`postWithinLimit` を通す（1,000 文字×条件の数は上限に収まる）。
- 値（条件の値・ラベルを引く値）は本文だけに置き、`console` に出さない。

## 4. 部品ごとの props・状態

### 4.1 `ReferenceSelectField`（D6〜D9）

```ts
export interface ReferenceSelectFieldProps {
  column: ColumnDefinition // formPart が select か radio、reference.kind が REFERENCE で AVAILABLE
  index: number // テストの印（tables-form-field-c<index>）と radio の name に使う
  name: string // 項目名（日英の表示名）
  mode: 'create' | 'update'
  value: string
  /** 開いたときの値（更新で options に無ければ、値だけの選択肢を足す。D8） */
  initialValue: string
  error: string | undefined
  disabled: boolean
  onChange: (value: string) => void
  onBlur: () => void
}
```

- 状態は持たない（値は `BusinessDataFormPage` のフォームの状態）。
- 選択肢の文は `optionText(option)`（「ラベル (値)」、ラベルが `null` なら値）。並べ直さない。
- 0 件の説明文（`helperText`）は D9。必須は既存の `FieldInput` と同じく `aria-required` を自分で付ける。

### 4.2 `ReadonlyReferenceField`（D11〜D14）

```ts
export type ReadonlyReason = 'permission' | 'too-many'

export interface ReadonlyReferenceFieldProps {
  index: number
  name: string
  /** 保存されている値そのもの（作成では空） */
  value: string
  reason: ReadonlyReason
  /** 作成の画面で必須のとき true（D13 の固定の誤りを出す） */
  blockedRequired: boolean
  /** 保存の後の誤り（あれば固定の誤りより優先） */
  error: string | undefined
}
```

- `TextInput` に `readOnly`・`aria-readonly="true"` を付け、`disabled` にしない。`onChange` を持たない。
- `error ?? (blockedRequired ? 固定の誤り : undefined)` を `FormField` の `error` に、無ければ理由の説明文を `helperText` に渡す。

### 4.3 `LookupField`（D16〜D20）

```ts
export interface LookupFieldProps {
  index: number
  name: string
  picker: PickerInfo
  source: ReferenceSource
  value: string
  /** 選んだ行・読めたラベル。無ければ null（値だけを出す） */
  label: string | null
  labelLoading: boolean
  clearable: boolean // nullable が偽でなく、必須でない
  error: string | undefined
  disabled: boolean // 保存中
  onChange: (value: string, label: string | null) => void
}
```

- 状態: `open`（ダイアログを開いているか）。「選ぶ…」の `ref` を `finalFocusRef` として渡し、欄の `ref` をクリアの後のフォーカスに使う。
- 欄の文は `value === '' ? '' : optionText({ value, label })`。`labelLoading` のときは欄に `aria-busy="true"`。
- ボタンの名前は D17（見える文字で始める）。

### 4.4 `LookupPickerDialog`（D21〜D30）

```ts
export interface LookupPickerDialogProps {
  name: string // 見出しと表の名前に使う項目名
  picker: PickerInfo
  source: ReferenceSource
  finalFocusRef: React.RefObject<HTMLElement | null>
  onSelect: (row: ReferenceRow) => void
  onClose: () => void
}
```

状態（`useLookupSearch` が持つ。開くたびに作り直す）:

```ts
export type PickerState =
  | { kind: 'initial' }
  | { kind: 'empty-condition' }
  | { kind: 'searching'; page: number }
  | { kind: 'results'; page: number; rows: readonly ReferenceRow[]; hasNext: boolean }
  | { kind: 'no-results'; page: number } // page が 1 なら「該当する行がありません」、2 以上なら「このページには行がありません」＋「前のページへ」（D26）
  | { kind: 'failed'; message: 'retry' | 'mismatch' }
  | { kind: 'denied' }
// 条件の入力: Record<column, string>、条件ごとの誤り: Record<column, 'too-long' | 'invalid-value'>
```

- 検索の世代（`useRef<number>`）で古い答えを捨てる。`searching` の間のページ送りの押下は捨てる（D24）。
- 条件の入力の部品は `searchInputFor(field)`（純粋な関数）で `valueType` と演算子から選ぶ（D22。既存の `FieldInput` と同じ `type`・`inputMode`・`step`・説明文、`BOOLEAN` は「（指定しない）」・真・偽の `Select`、`CONTAINS` は常に文字）。送る値は既存の `searchConditions.ts` の書き方にそろえ、`EQUALS` は送る前に既存の `isReadableAs` で確かめる（D23）。
- 400 の `fieldErrors` のうち、`searchFields` にあるカラムの `INVALID_VALUE` だけを条件の誤りにする。ほかの 400 は `failed`（`retry`）にする。
- ページの表示は `pickerPaging(page, rowCount, hasNext)`（純粋な関数）で決める（D25・D26）。戻り値は次の2つ。
  - `{ kind: 'table'; totalCount; status }`: 行が1件以上のとき。`totalCount` は「(page−1)×20＋rowCount（`hasNext` なら＋1）」、`status` は件数を言わない文の鍵（続きあり・ここまで）
  - `{ kind: 'empty'; backTo: number | null }`: 行が0件のとき。`Table` を描かない。`backTo` は page が 2 以上なら page−1（「前のページへ」を出す）、1 なら `null`
- 開いた直後のフォーカスは最初の条件の入力の `ref`（`initialFocusRef`）。

### 4.5 `useReferenceLabels`（D18）

```ts
export interface ReferenceLabelState {
  /** カラムごとのラベル（読めなかった・まだのときは null） */
  labels: Readonly<Record<string, string | null>>
  /** 読んでいるカラム */
  loading: ReadonlySet<string>
}
```

- 更新の画面で、定義と今の値がそろったら、`AVAILABLE` の LOOKUP の項目で値が空でないものごとに1回 `getReferenceLabel` を呼ぶ。答えは画面の状態にだけ持ち、離れたら捨てる。失敗はすべて `null`（値だけ）。

## 5. 文言の鍵（`messages.ts`。日本語・英語）

前提の6つ（★）を必ず両方に置く（D36）。

| 鍵 | 日本語 | 英語 |
|---|---|---|
| ★`tables.reference.noPermission` | 参照先を読む権限がないため変更できません | You cannot change this because you do not have permission to read the referenced data |
| ★`tables.reference.tooMany` | 候補が多いため選べません。管理者に、検索（ピッカー）への切り替えを依頼してください | There are too many options to choose from. Ask an administrator to switch this field to the search picker |
| `tables.reference.requiredBlocked.permission` | 必須の参照項目に値を入れられません。参照先を読む権限がないためです。管理者に権限を依頼してください | This required reference cannot be filled because you do not have permission to read the referenced data. Ask an administrator for permission |
| `tables.reference.requiredBlocked.tooMany` | 必須の参照項目に値を入れられません。候補が多いためです。管理者に、検索（ピッカー）への切り替えを依頼してください | This required reference cannot be filled because there are too many options. Ask an administrator to switch this field to the search picker |
| `tables.reference.noOptions` | 選べる候補がありません | No options available |
| `tables.reference.noOptionsRequired` | この項目は必須のため、候補が入るまで保存できません | This field is required, so you cannot save until options are available |
| `tables.reference.lookupHint` | 検索して選びます | Search to choose a value |
| `tables.reference.open` | 選ぶ… | Select… |
| `tables.reference.openName` | 選ぶ…（{{field}}） | Select… ({{field}}) |
| ★`tables.reference.clear` | クリア | Clear |
| `tables.reference.clearName` | クリア（{{field}}） | Clear ({{field}}) |
| `tables.picker.title` | {{field}}を選ぶ | Select {{field}} |
| `tables.picker.close` | 閉じる | Close |
| `tables.picker.operator.EQUALS` | {{name}}（一致） | {{name}} (equals) |
| `tables.picker.operator.CONTAINS` | {{name}}（部分一致） | {{name}} (contains) |
| `tables.picker.search` | 検索 | Search |
| `tables.picker.initial` | 条件を入れて「検索」を押してください | Enter a condition and press Search |
| `tables.picker.emptyCondition` | 条件を入れてください | Enter at least one condition |
| `tables.picker.tooLong` | 1,000 文字以内で入れてください | Enter 1,000 characters or fewer |
| `tables.picker.invalidValue` | この条件には、この形の値を入れられません | This value is not valid for this condition |
| ★`tables.picker.searching` | 検索中 | Searching |
| ★`tables.picker.noResults` | 該当する行がありません | No matching rows |
| `tables.picker.pageEmpty` | このページには行がありません | There are no rows on this page |
| `tables.picker.previousPage` | 前へ | Previous |
| `tables.picker.nextPage` | 次へ | Next |
| `tables.picker.backPage` | 前のページへ | Go to the previous page |
| `tables.picker.pageStatusMore` | {{page}} ページ目（続きがあります） | Page {{page}} (more rows follow) |
| `tables.picker.pageStatusLast` | {{page}} ページ目（ここまで） | Page {{page}} (last page) |
| `tables.picker.rangeMore` | {{from}}〜{{to}} 件目を表示しています。続きがあります | Showing rows {{from}} to {{to}}. More rows follow |
| `tables.picker.rangeLast` | {{from}}〜{{to}} 件目を表示しています。これで最後です | Showing rows {{from}} to {{to}}. This is the last page |
| ★`tables.picker.failed` | 検索できませんでした。もう一度検索してください | Search failed. Please search again |
| `tables.picker.retry` | 再度検索 | Search again |
| `tables.picker.denied` | この項目の候補を検索できません。画面を開き直してください | You cannot search options for this field. Please reopen the screen |
| `tables.picker.results` | {{field}}の検索結果 | Search results for {{field}} |
| `tables.picker.selectColumn` | 選ぶ | Select |
| `tables.picker.selectRow` | {{label}}を選ぶ | Select {{label}} |
| `tables.error.REFERENCE_NOT_FOUND`（既存の文を差し替え） | 選べない値です。選び直してください | This value cannot be selected. Please choose again |

- `{{label}}`・`{{field}}` に入る値は i18next の補間でエスケープされる既定のまま使い（`escapeValue` を切らない）、文字として描く。行の値を文言の鍵に使わない。
- 「選ぶ」の名前の `{{label}}` は `row.label ?? row.value`。

## 6. 既存の部品の変更

| ファイル | 変更 |
|---|---|
| `useTableDefinition.ts` | 引数 `purpose?: DefinitionPurpose` を足す。`purpose` があるときは `businessDataStore` を読まず書かない（D2）。読み直し（`reload`）・やり直し（`retry`）は同じ `purpose` で読む |
| `BusinessDataFormPage.tsx` | `useTableDefinition(ref, mode === 'create' ? 'CREATE' : 'UPDATE')`。カラムごとに `chooseFieldKind` で部品を選ぶ（D5。`editableOnCreate`・`editableOnUpdate`・`truncatedColumns` を `reference.status` より先に見て、入力できないカラムは U1 が `NO_PERMISSION` を返していても既存の「入力できない」の表示にする）。入力不可の参照の項目を送る本文から外す（D15）。保存の前の検証で作成の必須の入力不可の項目を誤りに足す（D13）。誤りの文は `referenceErrorKey(column, reason, mode)` で選ぶ（D31・D32）。入力不可の参照の項目も誤りを結べる項目に含める。LOOKUP のラベルを `useReferenceLabels` と画面の状態に持つ |
| `businessDataParse.ts` | `reference`（`searchFields`・`listColumns` の `valueType` を含む）と、検索の写し `parseReferenceRowPage`・ラベルの写し `parseReferenceLabel` を足す。知らない `status`・`kind` は D3 のとおり倒し、語彙に無い `valueType` は形の崩れにする |
| `formDiff.ts`・`formValidation.ts` | 入力不可の参照の項目を入力できないカラムと同じに扱う口（引数で入力不可のカラムの集合を渡す）を足す。今の呼び出しの結果は変えない |
| `problemMessage.ts` | `referenceErrorKey` を足す（`REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`・`REQUIRED` と定義の状態から鍵を選ぶ）。`writeProblemKind` の分け方は変えない（D33） |
| `writeOutcome.ts` | 変えない（D33） |
| `messages.ts` | 5節の鍵を足し、`tables.error.REFERENCE_NOT_FOUND` の文を差し替える |
| 一覧・詳細の部品 | 変えない（D1・D34） |

## 7. テストの計画

すべて `*.test.ts(x)` を対象と同じ場所に置き、Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe で書く。テストの説明文は英語、データは架空の値（日本語可）。描画の後の効果で出る値は `waitFor` で待ち、時間の上限は延ばさない。各部品に vitest-axe の検査を1件入れる。

| 対象 | 確かめること（主なもの） |
|---|---|
| `referenceField.ts`（純粋） | 部品の選び方の表（D5）の全行。fast-check: `AVAILABLE` 以外では入力の部品を返さない、用途で入力できないカラム（`editableOnCreate`・`editableOnUpdate` が偽、更新で `truncatedColumns`）では `reference.status` が `NO_PERMISSION` を含むどの値でも既存の読み取りを返し、`ReadonlyReferenceField`（権限の文言）を返さない（R-05）、`referenceErrorKey` は入力できないカラムでは D13 の鍵を返さない、`optionText` は `<`・`"`・`'` を含んでも文字のまま返し、ラベルが `null` なら値を返す。`referenceErrorKey` の組み合わせ（入力不可の参照の項目の `REQUIRED`・`NOT_EDITABLE_NOT_NULL` は D13 の鍵、そのほかは既存の鍵） |
| `pickerSearch.ts`（純粋） | fast-check: 組み立てた条件に空の値が入らない、空だけなら「送らない」、1,000・1,001 文字（コードポイント、サロゲートの組を1つと数える）の境界、`EQUALS` で型として読めない値は「送らない」と条件の誤りになる。`searchInputFor` の表（D22 の全 `valueType` と `CONTAINS`）が既存の `FieldInput` の選び方と一致する。`pickerPaging` の性質（fast-check。page は 1〜1,000,000、rowCount は 0〜20、`hasNext` は真偽。ただし `hasNext` が真なら rowCount は 20）: (1) rowCount が 1 以上なら `kind` は `table` で、`computeTotalPages(totalCount, 20)`（`Table` と同じ式）が、`hasNext` なら page＋1、そうでなければ page に等しい（総ページが今のページより小さくならず、「次へ」の可否が `hasNext` と一致する）、(2) rowCount が 0 で page が 2 以上なら `kind` は `empty`・`backTo` は page−1（`Table` を描かず「前のページへ」だけ。R-02）、(3) rowCount が 0 で page が 1 なら `kind` は `empty`・`backTo` は `null` |
| `businessDataParse`（足した写し） | `reference` の正しい形・`null`・形の崩れ（通信の失敗）・知らない `status`（`NO_PERMISSION` に倒す）・`searchFields`・`listColumns` の `valueType` の欠けと語彙に無い値（形の崩れ）。検索の写し: 正しい形、`page` が `null` の応答は形の崩れ。**ラベルの写し `parseReferenceLabel`: U1 の 4.4 の成功の応答（`rows` 1件・`cells` が空・`page: null`・`hasNext: false`）から `label` を取り出せる（R-01）**、`label` が `null` の行、`page` が数でも受ける（見ない）、`rows` が 0 件・2 件以上・`value` が文字でないときは失敗 |
| `businessDataApi`（足した呼び出し） | 本文に `purpose`・条件・値が入り、道に載らない。空の条件を送らない |
| `ReferenceSelectField` | 「ラベル (値)」と値だけの表示、保存済みの値が無いときの値だけの選択肢（D8）、0 件の説明文と必須の文（D9）、XSS の fixture が文字で出る（AC1.1.10）、radio、vitest-axe |
| `ReadonlyReferenceField` | `readOnly`・`aria-readonly`、`disabled` でない（Tab で止まる）、値を読める、説明文の出し分け（権限・候補が多い）と `aria-describedby`、作成の必須の固定の誤り、更新では誤りを出さない、vitest-axe |
| `LookupField` | 空・「ラベル (値)」・値だけ・`aria-busy`、「クリア」は NULL を許す項目だけ、クリアで欄へフォーカス、ボタンの名前が見える文字で始まる（D17）、保存中は押せない、XSS（AC2.3.3）、vitest-axe |
| `LookupPickerDialog` | 開いたら最初の条件へフォーカス・閉じたら「選ぶ…」へ戻る、背景のクリックで閉じない、Escape で閉じ値は変わらない、空の条件で要求を送らない、1,001 文字で送らない、`EQUALS` の数の条件に数でない値で送らない、条件の入力が `valueType` に合う部品で出る（`INTEGER` は `number`、`DATE` は `date`、`BOOLEAN` は `Select`、`CONTAINS` は文字）、Enter で検索、検索中の二重押しの防止、古い答えを捨てる、結果・0 件・2ページ目以降の 0 件（`Table` とページ送りが無く、「前のページへ」で page−1 を同じ条件で読む）・失敗（503）・403・400（条件の誤り）・409 の表示、ページ送りの文（総ページを言わない）、「選ぶ」の名前（ラベルが `null` なら値）、1列目が「選ぶ」、`data-testid`・`id` に行の値が入らない、XSS（AC2.2.9）、状態の文の `role`、vitest-axe（初期・結果・0 件・失敗の4つの状態で各1件。部品ごとの1件の決まりに足す） |
| `useReferenceLabels` | 値のある LOOKUP の項目だけを読む、REFERENCE は読まない、`page: null` の 200 の応答でラベルが出る（R-01）、403・503・写しの失敗で `null`、離れた後の答えを捨てる |
| `BusinessDataFormPage`（足した振る舞い） | 作成は `CREATE`・更新は `UPDATE` で定義を読む、その用途で入力できない参照のカラム（`NO_PERMISSION` で届く）が既存の「入力できない」の表示になり権限の文言も D13 の誤りも出ない（R-05）、定義の 503 で「開けない・つながらない」を出しフォームを出さない（Q1: A）、入力不可の参照の項目を本文に入れない、作成の必須の入力不可の項目で保存を押すと送らずにまとめへフォーカス、409 `REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`・400 `REQUIRED` の文と結び付け、保存の 503 `UNAVAILABLE`・`TIMEOUT` の表示が今と同じ、画面の部品のテスト1件で「入力不可・値だけの表示・理由の表示」（AC2.5.4）。`reference` を含む定義の見本を、既存の一覧・詳細・作成・更新の画面のテストに流しても通る（AC3.4.3。見本は `features/tables/testing/sampleDefinition.ts` に足す） |

性質ベースのテストの乱数の種は、失敗したときに記録する（`team.md`）。

## 8. E2E の流れの概要

### 8.1 `190-reference-picker-flow.e2e.ts`（代表の流れ 1本。F8）

- 前提: 見本の対象DB（PostgreSQL）が動いていること（`./gradlew e2eTest` が届くかを確かめる既存の仕組み）。E2E の専用のスキーマに、参照先の顧客の表（数件）・商品の表（ページ送りの確かめに 21 件以上と、長い名前の行）と参照元の注文の表を置く（架空の値。D39）。
- 準備（API）: 自作の利用者・ロール（注文は FULL、顧客・商品は READ）・割り当て。180 と共有する E2E の DSL が適用されていなければ投入・適用する。初期管理者・見本の行・見本の DSL は変えない。
- 流れ: ログイン → 注文の表の作成の画面 → 顧客の選択肢を1つ選ぶ → 商品の「選ぶ…」をキーボードで開く → 条件を入れて Enter → キーボードで行の「選ぶ」 → 欄の「ラベル (値)」を確かめる → 保存（Enter）→ 作った行の更新の画面 → 商品の欄に同じラベル。
- 終わりに、報告の json を文字列で検索し、資格情報・接続先・トークンの値が無いことを確かめる。資格情報は `webServer.env` に置かない。`test.step` の題・注記・添付に行の値を入れない。

### 8.2 `195-reference-picker-accessibility.e2e.ts`（axe。本数に数えない）

- 状態: ピッカーを開いた状態・結果の一覧・0 件・検索の失敗・入力不可（権限不足・候補が多い）・作成の必須の入力不可の誤り・保存の 409 の誤り・長い名前（100 文字以上、`<` を含む）・2語以上の現実に近いデータ。
- 組: ブランドカラーとテーマのすべての組（既存の `support/displayCombos.ts`）。幅は既定と 375px。
- 375px では、開いたダイアログの矩形が画面の中に収まり、画面全体が横にスクロールしないことを、既存の `support/overflow.ts` で確かめる。
- 失敗・入力不可の状態は API の答えを差し替えて作り、差し替えの見本を本物の応答と `shapeCompare` で比べる（190 の流れで本物の応答を取る）。axe の規則・要素を外さない。

## Sources

- 単位: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`
- 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`
- 部品: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`
- 画面イメージ: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/refined-mockups/interaction-spec.md`・`mockups.md`・`design-system-mapping.md`
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C3）
- この単位の決め: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`
- 既存のコード: `frontend/src/features/tables/`、`frontend/src/shared/`、`frontend/e2e/`、make-you-chic-ui の固定先 `4c63d11` の部品

## Assumptions & Open Questions

- [assumption] 下位のフォルダ名 `reference/` とファイル名は仮で、Code Generation で変えてよい。
- [assumption] E2E のファイルの番号 190・195 は、既存の 180・185 の次として仮に置いた。
- 未決の点は `functional-spec.md` の 10節（承認の場）と 9節（NFR 設計）に集めた。
