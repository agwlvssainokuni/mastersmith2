# 画面の部品 — u4-business-data-time

出典: `functional-spec.md`（D1〜D44・W1〜W5）、契約 C1・C2・C4（`inception/contract-design/contract-summary.md`）、画面イメージ S2〜S4（`inception/refined-mockups/mockups.md`・`interaction-spec.md`・`design-system-mapping.md`・`accessibility-checklist.md`）、U1・U2 の画面の部品（`construction/u2-display-time-zone/functional-design/frontend-components.md`）、今の画面のコード（`frontend/src/features/tables/`。読み取りだけ）。部品の名前・置き場は論理の案で、コード生成で今の並びに合わせて確定する。

## 1. 部品の一覧

| 部品 | 置き場 | 役割 | 新規・直し |
|---|---|---|---|
| `formatInstantForInput` | `frontend/src/shared/format/` | U1 の `formatInstant` の結果から時差を除き、入力の初めの値（`YYYY-MM-DD HH:mm:ss[.端数]`）を返す。読めなければ元の文字（D16） | 新規（U2 の `formatInstantToMinutes` と同じ並び） |
| `formatLocalDateTimeForInput`・`toLocalDateTimeWire` | 同上 | 時差を持たない日時の初めの値（`T` を空白に）と、送る形（空白を `T` に）。書式と範囲の確かめは U1 と同じ規則（D14） | 新規 |
| `formatCell` | `frontend/src/features/tables/valueFormat.ts` | 表示のタイムゾーン（`ready` の名前か、`error` を表す値）を引数で受け、`DATETIME_TZ` を D1・D2・D5 で書式にする。`error` のときは短い文（種類 `unavailable`） | 直し |
| `TimeZoneNotice` | `frontend/src/features/tables/` | 案内の1行（3つの文とプリファレンスへのリンク。D9） | 新規 |
| `useTablesTimeZone` | 同上 | 業務データの画面が使う表示のタイムゾーンの状態の読み口。`useDisplayTimeZone` を包み、定義に時点を表す型のカラムがあるときだけ待つかを決める（D10・D11） | 新規 |
| `DateTimeField` | 同上 | 時点を表す型・時差を持たない日時の入力欄（`FormField`＋`TextInput`、案内、誤り、2択の `RadioGroup`）。作成・更新と検索で使い回す | 新規（今の `FieldInput` の `datetime-local` の分岐を置き換える） |
| `resolveDateTimeInput` | 同上（純粋な関数） | 文字・タイムゾーン・型・用途（作成・更新・範囲の始まり・範囲の終わり・一致）・2択の選択から、`ok`（送る値）・`invalid`・`gap`・`overlap-pending`・`unavailable` を返す（D17〜D22・D26・D27） | 新規 |
| `initialInputs`・`diffForm`・`hasFormChanges` | `formDiff.ts` | 時点を表す型・時差を持たない日時の初めの値と送る値を、上の関数で作る。2択の選択をフォームの状態として持つ。比べ方（初めの文字と今の文字）は今のまま（D35） | 直し |
| `validateForm` | `formValidation.ts` | 時点を表す型・時差を持たない日時の確かめを `resolveDateTimeInput` に任せる | 直し |
| `buildConditions`・`validateSearchInputs`・`defaultSearchInputs` | `searchConditions.ts` | 時点を表す型の条件を時点に直して送る、範囲の向き（`compareInstants`）、既定の値の読み（D23〜D28） | 直し |
| `SearchPanel` | 同上 | 時点を表す型・時差を持たない日時の条件を `DateTimeField`（案内つき）にする | 直し |
| `pickerSearch`・`LookupPickerDialog` | `features/tables/references/` | ピッカーの一致の条件と結果の列（D32・D34） | 直し |
| `optionText`・`ReferenceSelectField`・`ReadonlyReferenceField`・`LookupField` | 同上 | ラベルを `labelValueType`、値を参照元のカラムの型で書式にする（D32）。送る値は元の文字（D33） | 直し |
| 定義の応答の写し（`businessDataParse.ts`・`businessDataTypes.ts`） | `features/tables/` | `reference.labelValueType` を読む。無い・知らない値は `null`（D31） | 直し |
| `BusinessDataListPage`・`BusinessDataDetailPage`・`BusinessDataFormPage` | 同上 | `useTablesTimeZone` で待ち、`TimeZoneNotice`・`DisplayTimeZoneErrorBar` を置き、`formatCell` にタイムゾーンを渡す。フォームはタイムゾーンを開いたときに固定する（D12） | 直し |

## 2. 案内の1行（TimeZoneNotice。S2）

```text
受注（orders）
表示のタイムゾーン: Asia/Tokyo（プリファレンスで変更）
+--------+--------------------------------+-------------+
| 受注ID | 受注の時刻                     | 納期        |
| 1001   | 2026-11-02 00:00:00 +09:00     | 2026-11-05  |
| 1002   | 2026-11-02 09:30:15.123 +09:00 | 2026-11-06  |
+--------+--------------------------------+-------------+
```

- 置き場: 一覧・詳細の見出し（h1）の下の段落。見た目は今の画面の説明文（`page-description` 相当）に合わせる。
- 出す条件: 定義（一覧の列・詳細の項目）に `DATETIME_TZ` のカラムが1つ以上あり、表示のタイムゾーンが `ready`（D9）。
- 文: `source` ごとの3つ（5 節の文言）。「プリファレンスで変更」は react-router の `Link`（プリファレンスの画面の道は登録の定数を使う）。
- 作成・更新の画面には出さない（欄ごとの案内が伝える。D15）。

## 3. 時刻の欄（DateTimeField。S3・S4）

```text
受注の時刻 *
[ 2026-11-02 09:30:15                        ]
 Asia/Tokyo (+09:00) で入力。例: 2026-11-02 09:30:15（秒の端数は .123 のように付けられます）

受注の時刻 *
[ 2026-11-01 01:30:00                        ]
 America/New_York では、この時刻は2回あります。どちらですか。
   ( ) 先（-04:00）   ( ) 後（-05:00）
```

- props（論理）: `kind`（`instant`＝時点を表す型、`local`＝時差を持たない日時）、`purpose`（`form`・`range-from`・`range-to`・`equals`）、`timeZone`（`instant` のとき。フォームは固定の値、検索は今の値）、`value`・`onChange`、`overlapChoice`・`onOverlapChoice`、`error`、`disabled`、`label`・`required`、`now`（案内の今の時差。注入できる時計）。
- 入力: `TextInput`（`type="text"`、`inputMode` は指定しない、`autoComplete="off"`）。`FormField` の `helperText` に案内、`error` に誤りの文。
- 2択: `purpose` が `form`・`equals` で、結果が `overlap-pending`・選択済みのときだけ、欄の下に `RadioGroup`（legend は 5 節の文、選択肢は「先（{時差}）」「後（{時差}）」）。`range-from`・`range-to` では出さない（D27）。文字を書き換えると選択を解く（`onOverlapChoice(null)`）。
- 更新の画面で初めの値の文字のままの欄は、確かめも2択も出さない（D22）。
- `disabled`: 表示のタイムゾーンが `error` の、検索の画面とピッカーの `instant` の欄（D11。作成・更新は `error` の間フォームを開かないため使わない）。欄の下に短い文（U2 の `displayTime.error` と同じ文）。

## 4. 状態の一覧（画面ごと）

| 画面 | 表示のタイムゾーン `loading` | `ready` | `error` |
|---|---|---|---|
| 一覧（時点の列あり） | 今の読み込み中の表示（案内の1行・表を出さない） | 案内の1行と表 | 上に「再読み込み」の帯、案内の1行なし、時点のセルは短い文 |
| 一覧（時点の列なし） | 待たない | 表（案内の1行なし） | 表（帯なし） |
| 詳細 | 一覧と同じ | 案内の1行と項目 | 一覧と同じ |
| 作成・更新（時点の欄あり） | 今の読み込み中の表示 | フォーム（`ready` になった時点のタイムゾーンを固定し、初めの値を作る） | フォームを開かず、読み込み中の表示の位置に「再読み込み」の帯だけ。再読み込みで `ready` になったら、その時点のタイムゾーンを固定してフォームを開く（D11・D12） |
| 検索の画面 | 時点の条件の欄を使えない状態（検索の押下は時点の条件が空なら通す）。時点の既定の値は空 | 欄と案内。`ready` になった時点で既定の値を1回だけ入れる（入れた文字は上書きしない。D28） | 時点の条件の欄を使えない状態と短い文 |
| ピッカー | 検索の画面と同じ | 同じ | 同じ |

## 5. 文言の鍵（日本語・英語）

`{zone}` はタイムゾーンの名前、`{offset}` は `±HH:mm`。どちらも文字として差し込む。

| 鍵の名前（論理） | 日本語 | 英語 |
|---|---|---|
| 案内（設定あり） | 表示のタイムゾーン: {zone}（{link}） | Display time zone: {zone} ({link}) |
| 案内（ブラウザ） | 表示のタイムゾーン: ブラウザのタイムゾーン（{zone}）（{link}） | Display time zone: browser time zone ({zone}) ({link}) |
| 案内（解釈できない） | 保存したタイムゾーンを使えないため、ブラウザのタイムゾーン（{zone}）で表示しています（{link}） | The saved time zone is not available, so times are shown in the browser time zone ({zone}) ({link}) |
| リンク | プリファレンスで変更 | Change in Preferences |
| 入力の案内（時点） | {zone} ({offset}) で入力。例: 2026-11-02 09:30:15（秒の端数は .123 のように付けられます） | Enter in {zone} ({offset}). Example: 2026-11-02 09:30:15 (you can add fractional seconds such as .123) |
| 入力の案内（時差なし） | 例: 2026-11-02 09:30:15（秒の端数は .123 のように付けられます） | Example: 2026-11-02 09:30:15 (you can add fractional seconds such as .123) |
| 検索の案内（時点の範囲） | {zone} ({offset}) で入力。始まりも終わりも含みます。端数を含めるときは 23:59:59.999999 のように入れます | Enter in {zone} ({offset}). Both ends are included. To include fractional seconds, enter a value such as 23:59:59.999999 |
| 検索の案内（時差なしの範囲） | 始まりも終わりも含みます。端数を含めるときは 23:59:59.999999 のように入れます | Both ends are included. To include fractional seconds, enter a value such as 23:59:59.999999 |
| 誤り（読めない） | 日時を YYYY-MM-DD HH:mm:ss の形で入れてください | Enter the date and time as YYYY-MM-DD HH:mm:ss |
| 誤り（存在しない） | {zone} では、この時刻はありません（夏時間の切り替えで飛ばされる時刻です） | This time does not exist in {zone} (it is skipped by a daylight saving time change) |
| 2択の legend | {zone} では、この時刻は2回あります。どちらですか | This time occurs twice in {zone}. Which one? |
| 2択（先） | 先（{offset}） | Earlier ({offset}) |
| 2択（後） | 後（{offset}） | Later ({offset}) |
| 誤り（2択が未選択） | どちらの時刻かを選んでください | Choose which time you mean |

- 今の「UTC で入れる」（`tables.form.utc`）は使わなくなる。消し方はコード生成の計画で決める。
- 範囲の向きの誤り・項目ごとのサーバーの誤りの文は今の文言を使う。

## 6. 型（画面の側）

- `ReferenceField` に `labelValueType: ValueType | null` を足す（応答に無い・知らない値は `null`）。
- フォームの状態に、欄ごとの2択の選択（`Record<column, 'earlier' | 'later' | null>`）を足す。検索の入力（`SearchInput`）に、一致の条件の2択の選択を足す。
- `formatCell` の引数に、表示のタイムゾーンの状態（`{ status: 'ready', timeZone } | { status: 'error' }`）を足す。`loading` では呼ばない（画面が待つ。D10）。

## 7. テスト（画面）

- 純粋な関数（`resolveDateTimeInput`・`formatInstantForInput`・`formatCell`・`diffForm`・`buildConditions`）: D41 の具体の値と、D43 の性質ベースのテスト（fast-check）。タイムゾーンは引数で明示に渡す。
- 部品（`TimeZoneNotice`・`DateTimeField`・`SearchPanel`・フォーム・一覧・詳細・ピッカー）: 3つの状態（`loading`・`ready`・`error`）、案内の3つの文、誤りの文と `aria-invalid`、2択の出る・出ない（作成・更新で変えた欄・変えていない欄・範囲・一致）、保存の押下のフォーカスの行き先、キーボードでの2択の選択。U2 の配布の提供元にテスト用の固定のタイムゾーンを渡す。`waitFor` で待つ。
- `vitest-axe`: 部品ごとに1件（誤りの状態と2択を出した状態を含める）。
- 実際のブラウザの axe と E2E は D44・W5。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位・契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`・`inception/contract-design/contract-summary.md`（C1・C2・C4）
- 画面イメージ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/refined-mockups/mockups.md`（S2〜S4）・`interaction-spec.md`・`design-system-mapping.md`・`accessibility-checklist.md`
- 決まりと流れ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-design-questions.md`
- 先の単位の画面の部品: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/frontend-components.md`

## Assumptions & Open Questions

- [assumption] 部品の名前・置き場・props の形は論理の案で、コード生成で今の `features/tables` の並びに合わせて確定する。
- [assumption] 検索の画面で表示のタイムゾーンが `loading` のとき、時点の条件が空なら検索を通す（時点の条件を送らないため変換が要らない）。
