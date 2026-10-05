# 画面の部品 — U2 dsl-v2（S10 DSL の管理、`frontend/src/features/dsl`）

出典: 画面 `mockups.md` の 10節（S10）、`interaction-spec.md`・`accessibility-checklist.md`、ストーリー AC5.2.2・AC6.1.2・AC6.1.7・AC6.1.9、この段の答え Q4〜Q6（すべて A）、決まり `rules.md` の BR3・BR5・BR7、既存の画面のコード（`frontend/src/features/dsl/**`）。単位の種別は library だが、画面の小さな変更を持つためこの文書を作る（Units Generation の承認の場の直し R-04）。

## 1. 範囲

既存の DSL の管理の画面の形（タブ・今の状態・プレビュー・投入・履歴）は変えない。変えるのは次の6点だけで、新しい画面・道・API は足さない。

| # | 変えるもの | 部品 | 決まり |
|---|---|---|---|
| F1 | 版の誤りと深さの誤りの文言 | `DslErrorList`（表示は変えない。文言はサーバーが返す） | BR7.1・BR2.1 |
| F2 | 今の書式で読めない適用中の DSL の注意 | `DslStatusPanel` | BR7.2・BR3.6 |
| F3 | 読めないプレビューの誤りの一覧と案内 | `DslPreviewPanel`・`useDslAdmin` | BR7.3・BR3.3・BR3.4 |
| F4 | メニューの木の「スキーマ名.テーブル名」 | `DslMenuTree` | BR7.4 |
| F5 | 違いの表のスキーマの見出しの行 | `DslDiffTable`・`diffCounts.ts` | BR7.5・BR5.2 |
| F6 | 要約のスキーマの数、書式の道（v2） | `DslPreviewPanel`・`submitInput.ts` | BR7.5・BR1.7 |

使う make-you-chic-ui の部品は、今の画面がすでに使う `Alert`（`variant` に `warning`・`danger`、`action`、`role` は warning と danger で `alert`）・`Button`・`Card`・`Tabs`・`Tooltip`・`Modal`・`Badge`・`Icon`・`Switch` だけで、口は `vendor/make-you-chic-ui` のソースで確かめた範囲（`Alert` の `variant`・`action`・`onDismiss`・`dismissLabel`）に限る。make-you-chic-ui への新しい依頼は無い。

## 2. API の受け渡し（型の変更。`api/types.ts`）

サーバーの応答の知らない項目は無視する今の決まりのまま、次を変える・足す。

| 型 | 変更 |
|---|---|
| `DslStatus` | `appliedUnreadable: boolean` を足す |
| `MenuNode.table` | 文字列か null を、組 `{ schema: string; name: string }` か null にする |
| `PreviewSummary` | `schemaCount: number` を足す |
| `DslDiff` | `tables: TableDiff[]` を `schemas: SchemaDiff[]` にする。`SchemaDiff` は `name`・`label`（DisplayLabel）・`change`（`TableChange` と同じ4つ）・`tables: TableDiff[]` |
| `WarningKind` | `'SCHEMA_MISMATCH'` を足す |

`TableDiff`・`ColumnDiff`・`DslErrorReport` の形は変えない。誤りと警告の `path` は `schemas.<スキーマ>.tables.<テーブル>…` の形で届き、画面はそのまま出す。

## 3. 部品ごとの変更

### F1 誤りの一覧（`DslErrorList`）

- 部品は変えない。版 1 のときの文（ja「書式の版 1 は使えません。書式の版 2（スキーマの階層あり）で書いてください。既定の DSL を生成し直すと版 2 で得られます。」、en「Format version 1 is no longer supported. Write the DSL in format version 2 (with the schema level). You can get a version 2 DSL by loading the schema again.」）と、深さの文（ja「メニューの深さが上限（5 段）を超えています。」、en「The menu depth exceeds the limit (5 levels).」）はサーバーの文言で届き、既存の列（行・列・場所・種類・内容）に出る。
- 投入のタブ（AC6.1.2）、履歴の復元（AC6.1.7）、プレビューの読み直し（F3）の3か所で同じ部品を使う。

### F2 今の状態（`DslStatusPanel`）

- props は変えない（`status` の中の `appliedUnreadable` を読む）。
- `status.appliedUnreadable` が真のとき、適用中の欄の上に `Alert variant="warning"` を出す。題「適用中の DSL は使われていません」、本文「適用中の DSL は今の書式（版 2）で読めないため、権限とメニューでは DSL が無い扱いです。スキーマを読み込み、プレビューを確かめて適用し直してください。」。閉じるボタンは出さない（状態が直るまで出し続ける）。
- 案内の操作は既存の「スキーマを読み込む」ボタンをそのまま使い、別のボタンは足さない。適用中の欄（識別・出どころ・適用した人・日時・ダウンロード）は今のまま出し、ダウンロードはできる（BR3.5）。
- `role="alert"` のため、画面を開いたときに一度読み上げられる。

### F3 読めないプレビュー（`DslPreviewPanel`・`useDslAdmin`）

- 状態: `useDslAdmin` のプレビューの読み込みに、応答が `DSL_INVALID` の場合を足し、`previewInvalid: DslErrorReport` を持つ（ほかの失敗は今のまま `failed`）。適用の応答が `DSL_INVALID` のときも同じ状態にする。
- props: `DslPreviewPanelProps` に `invalidReport: DslErrorReport | null` を足す。
- 表示: `invalidReport` があるとき、プレビューの区画に `Alert variant="danger"`（題「このプレビューは今の書式で読めません」、本文「破棄してから、版 2 の DSL を投入するか、スキーマを読み込んでください。」）と `DslErrorList` を出し、操作は「ダウンロード」と「破棄する」（既存の確かめの `DslConfirmDialog` を通す）だけにする。「適用する」は出さない。
- 破棄が終わると、今の「プレビューはありません」の表示に戻る。

### F4 メニューの木（`DslMenuTree`）

- テーブルを指す節は、括弧の中を `（{schema}.{name}）` にする。名前は利用者が書いた値のまま文字として出す（HTML として描かない、今のまま）。
- 開閉のボタン・`aria-expanded`・読み上げの名前（`dsl.menu.expand`・`dsl.menu.collapse`）は変えない。

### F5 違いの表（`DslDiffTable`・`diffCounts.ts`）

- 表の列（テーブル・区分・カラムの違い）は変えない。各スキーマの前に見出しの行を1行置く: 1つのセル（`th scope="rowgroup"`、全列にまたがる）に「スキーマ {name}（{表示名}）」と区分の `Badge`（増えた・減った・変わった（表示名）・変わらない）。行は `tbody` をスキーマごとに分けて置く。
- 開閉はテーブルの行だけ。開閉の状態と `data-testid` の鍵は `{schema}/{table}` にする（同じ名前のテーブルが別のスキーマにあっても混ざらない）。
- 「すべて表示」が切られているときは、変わったテーブルのあるスキーマと、変わったスキーマ（増えた・減った・表示名の変化）の見出しだけを出す。
- 件数（`dsl.diff.counts`）は、すべてのスキーマのテーブルの数の合計で数える（今の文言のまま）。
- 表の読み上げの名前（`dsl.diff.tableLabel`）は ja「スキーマ・テーブルごとの違い」、en「Differences by schema and table」にする。

### F6 要約と書式の道

- 要約に「スキーマ {count}」を、テーブルの数の前に1行足す。
- 投入のタブの書式のリンク（`DSL_SCHEMA_PATH`）を `/dsl/dsl-schema-v2.json` にする。

## 4. 文言（`messages.ts` に足す鍵。ja・en の対）

| 鍵 | ja | en |
|---|---|---|
| `dsl.status.unreadableTitle` | 適用中の DSL は使われていません | The applied DSL is not in use |
| `dsl.status.unreadableBody` | 適用中の DSL は今の書式（版 2）で読めないため、権限とメニューでは DSL が無い扱いです。スキーマを読み込み、プレビューを確かめて適用し直してください。 | The applied DSL cannot be read in the current format (version 2), so permissions and menus treat it as absent. Load the schema, check the preview and apply it again. |
| `dsl.preview.invalidTitle` | このプレビューは今の書式で読めません | This preview cannot be read in the current format |
| `dsl.preview.invalidBody` | 破棄してから、版 2 の DSL を投入するか、スキーマを読み込んでください。 | Discard it, then submit a version 2 DSL or load the schema. |
| `dsl.preview.schemaCount` | スキーマ {{count}} | Schemas: {{count}} |
| `dsl.diff.schemaHeading` | スキーマ {{name}}（{{label}}） | Schema {{name}} ({{label}}) |
| `dsl.warningKind.SCHEMA_MISMATCH` | スキーマの名前の違い | Schema name mismatch |

`dsl.diff.tableLabel` は F5 のとおり直す。照合の警告の本文（ja「DSL のスキーマ「{0}」は対象DB の設定のスキーマと違うため、テーブルを照合できませんでした。」、en「The tables could not be compared because the DSL schema "{0}" differs from the schema configured for the target database.」）はサーバーが返す。

## 5. アクセシビリティ（WCAG 2.1 AA、NFR4）

- 状態は色だけにしない: 違いの区分は `Badge` の文字、読めない状態は `Alert` の題と本文で示す。
- 見出しの行は `th scope="rowgroup"` で、スキーマの下の行が読み上げでまとまりとして分かる。
- 注意（F2）と誤り（F3）は `Alert` の `role="alert"` で読み上げに伝わる。誤りの一覧は今のとおりフォーカスを移す。
- 破棄の確かめは既存の `DslConfirmDialog`（背景のクリックで閉じず、はじめのフォーカスは「やめる」）。
- 画面のテストは部品ごとに vitest-axe を1件（既存のテストに、注意あり・読めないプレビュー・見出しの行の状態を足す）。

## 6. 画面のテスト（足す・書き換える）

- `DslStatusPanel.test.tsx`: `appliedUnreadable` の真偽で注意の有無、ダウンロードが残ること、axe。
- `DslPreviewPanel.test.tsx`・`DslAdminPage.test.tsx`: プレビューの読み込みと適用が `DSL_INVALID` のときの誤りの一覧と操作（適用が出ない・破棄できる）。表示は `waitFor` で待って確かめる（`team.md`）。
- `DslMenuTree.test.tsx`: 「スキーマ名.テーブル名」と、名前に `<`・`>`・`&`・`"`・`'` を含めても文字として出ること。
- `DslDiffTable.test.tsx`: 見出しの行・区分・2つのスキーマに同じ名前のテーブルがあっても開閉が混ざらない・「すべて表示」の切り替え・axe。
- 版 1 の見本（`testing/fixtures.ts` ほか、`functional-spec.md` の 8節）を版 2 へ書き換える。
