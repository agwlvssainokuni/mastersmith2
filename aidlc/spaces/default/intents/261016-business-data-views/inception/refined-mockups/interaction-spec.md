# 操作の仕様 — business-data-views

出典: `mockups.md`（S1〜S8）、ストーリー `aidlc/spaces/default/intents/261016-business-data-views/inception/user-stories/stories.md`、この段の答え `refined-mockups-questions.md`（RQ1〜RQ8）。部品の形は `.claude/knowledge/aidlc-design-agent/component-spec-template.md` に従う。部品の名前は仮で、置き場と名前は機能設計で決める（team.md の Code Style: 画面は `features/tables` を置き換える1つの機能、共通の部品は `src/shared/`）。

## 共通の決まり

- 操作の結果（作成・更新・削除の成功）は Toast で知らせ、`aria-live="polite"` で読み上げにも伝える。誤りのまとめと拒否は Alert（`role="alert"`）。
- 取り消しにくい操作（削除）と、保存せずに離れる確かめは Modal。背景のクリックで閉じず、はじめのフォーカスは取り消す側（「やめる」「留まる」）。閉じたら開いた元へフォーカスを戻す。
- 処理中は保存・削除のボタンを押せなくし、「保存しています…」と出す。5 秒を超えたら「時間がかかっています」を出す（既存の時間がかかる旨の表示と同じ形。AC2.1.10）。
- 画面の文言は日本語と英語。表示名は DSL の表示名を画面の言語で出し、値と表示名は文字として出す（HTML として描かない）。
- 一覧の状態（検索の条件・ページ・1ページの件数・並び）は画面の中（そのテーブルの一覧の状態）に持ち、詳細・作成・更新・削除から戻る・ブラウザの戻る操作では保つ。再読み込みでは既定に戻る。道に検索の値を載せない（SM5）。
- 画面が出すボタン・リンク（作成・詳細・更新・削除）とカラムの入力の可否は、サーバーが返す操作の可否で決める（AC3.1.9）。

## BusinessDataListPage（S1 業務データの一覧）

| Field | Value |
|---|---|
| Component | BusinessDataListPage |
| Description | テーブルの行を検索し、ページに分けて並べ替えて出す |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| loading | 行を読んでいる。表の領域に読み込み中の表示 | 開く・検索・ページ・並べ替え・件数の変更 |
| populated | 行がある | 読み込みの成功 |
| empty-filtered | 条件に合う行が 0 件。「条件に合う行がありません」と条件をクリア | 検索の結果が 0 件 |
| empty-table | テーブルに行が無い。「行がありません」と作成の入口（CREATE 可のとき） | 条件なしで 0 件 |
| page-overflow | 今のページに行が無い（ほかの人の削除で減った）。前のページへの入口 | 空のページが返る（AC1.3.14） |
| restricted | 主キーの無いテーブル・主キーが NONE・ビュー。理由の Alert を一覧の上に出し、行の操作と詳細へのリンクを出さない（ビューの詳細は条件つき） | 操作の可否（AC3.2.5） |
| no-write | 書き込みの設定が無い。作成・更新・削除の入口を出さず、旨を Alert で出す | 操作の可否（AC3.3.9） |
| search-error | 検索の項目の入力の誤り。項目の下に文字で出し、直前の一覧は残す | 400（AC1.2.5） |
| forbidden / not-found / unavailable | S6 の表示に置き換える | 403・404・対象DB とつながらない |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| schema | string | yes | — | 道から読むスキーマ名（エンコード済み） |
| table | string | yes | — | 道から読むテーブル名 |
| menuItemId | string | no | — | 今の項目（`aria-current`）にするメニューの項目 |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 検索の項目を縦に積む。表は横のスクロールの領域。作成のボタンは見出しの下 |
| tablet (768–1024px) | 検索の項目を2列 |
| desktop (>1024px) | 検索の項目を横に並べる |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 表は `table`（Table）。横のスクロールの領域は `role="region"`＋`aria-label`（「商品の一覧の表」）＋`tabIndex=0` |
| Keyboard interaction | 見出しの並べ替えのボタン・ページ送り・件数の選択・行のリンク・行の「操作」のメニューに Tab で移れる。メニューは矢印で選び Enter で決め Escape で閉じる |
| Label / aria-label | 行の「操作」のトリガーは「操作: <行を見分ける値>」（英語 `Actions: …`）。詳細へのリンクは「詳細: <行を見分ける値>」 |
| Contrast ratio | WCAG AA。「（値なし）」の薄い色も 4.5:1 を保つ |
| Screen reader | 全体の件数と表示の範囲を `aria-live="polite"` で伝える（検索・ページの変化のたび）。並べ替えの向きは `aria-sort`。NULL は「値なし」と読む。省略したセルは読み上げの名前に全体の値を持つ |
| Focus management | 検索の後はフォーカスを動かさず、件数の読み上げで知らせる。詳細から戻ったときは、移る前に押したリンクかメニューのトリガーへフォーカスを戻す |

## SearchPanel（S1 の検索の領域）

| Field | Value |
|---|---|
| Component | SearchPanel |
| Description | DSL の検索の設定から項目を作り、条件で絞る |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| collapsed | 閉じている。見出しに条件の数 | `search.collapsed`（Should）・閉じる操作 |
| expanded | 開いている | 開く操作・既定 |
| error | 項目の入力の誤り | 型として読めない・下限が上限より大きい |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| fields | 検索の項目の一覧（カラム・表示名・演算子・既定の値・選択肢） | yes | — | サーバーが返す、見られるカラムの検索の設定 |
| values | 今の条件 | yes | 既定の値 | 一覧の状態として持つ |
| onSearch | (values) => void | yes | — | 検索（1ページ目へ戻す） |
| onClear | () => void | yes | — | 条件をクリア |

演算子ごとの入力: `EQUALS` は1つの入力、`CONTAINS` は1つの入力（`%`・`_` は文字）、`RANGE` は下限と上限の2つの入力（片方だけでもよい）、`CHOICE` は固定の選択肢のチェックボックス（複数選べる。真偽値で選択肢が無いときは「はい」「いいえ」。`REFERENCE`・`LOOKUP` は1つの入力の完全一致）。入力の型は RQ7 と同じ（日付は `date`、数は `number`、桁の多い数は文字）。

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 開閉のボタンに `aria-expanded`・`aria-controls`。項目は `fieldset` と `legend`（範囲・選択肢） |
| Keyboard interaction | Enter で検索。「条件をクリア」はボタン |
| Label / aria-label | 項目ごとに表示名と演算子の種類（「商品名（部分一致）」） |
| Screen reader | 誤りは `aria-invalid` と `aria-describedby` で項目に結ぶ |
| Focus management | 誤りのときは最初の誤りの項目へフォーカス |

## BusinessDataDetailPage（S2 行の詳細）

| Field | Value |
|---|---|
| Component | BusinessDataDetailPage |
| Description | 1行の `detail.visible` かつ READ 以上のカラムを出す |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| loading | 読み込み中 | 開く |
| populated | 値を出す | 成功 |
| not-found | 行が見つからない（S6） | 404 |
| forbidden / restricted | 403・409（主キー・ビューの決まり）は S6 か一覧の理由の表示 | 403・409 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 見出し `h1` と説明の一覧 `dl` |
| Keyboard interaction | 「一覧へ戻る」「更新」「削除」に Tab で移れる |
| Label / aria-label | 見出しは「<テーブルの表示名>: <行を見分ける値>」 |
| Screen reader | NULL は「値なし」、`password` は「伏せた値」 |
| Focus management | 開いたら見出しへフォーカス。更新・削除から戻ったら見出しへ |

## BusinessDataFormPage（S3 作成・S4 更新）

| Field | Value |
|---|---|
| Component | BusinessDataFormPage |
| Description | `detail.visible` のカラムのうち FULL を入力、READ を読み取りで出し、保存する |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| loading | 更新の画面で今の値を読んでいる | 開く（更新） |
| editing | 入力できる | 読み込みの後・作成の画面 |
| dirty | 変更がある（離れるときに確かめる） | 入力を変えた |
| saving | 保存中。ボタンを押せない | 保存 |
| invalid | 誤りがある。まとめと各入力の下に出す | 画面の検証・400・同じ値・参照先に無い値 |
| failed | DB が拒んだ保存・行が無い・行を1つに決められない。業務の言葉の Alert | 4xx の業務の誤り |
| timed-out | 保存されたか分からない。確かめる入口 | 時間切れ |
| forbidden | 403 の表示。入力は捨てる | 403（作業ロールの切り替えの後など。RQ5: A） |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| mode | `'create' \| 'update'` | yes | — | 作成か更新か |
| fields | カラムの一覧（表示名・フォーム部品・型・必須・長さ・範囲・選択肢・入力の可否） | yes | — | サーバーが返す |
| initialValues | 今の値 | update で yes | — | 詳細と同じ API で読む |

保存の本文: 作成は入れた FULL のカラム（空のまま送らないカラムは対象DB の既定値）、更新は変えたカラムだけ（SM1）。空にしたカラムは、文字の型は空の文字列、ほかの型は NULL（SM2）。`password` は入れたときだけ送る（SM3）。時差つきの型は UTC として送る（SM7）。

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 1列、ボタンは下に並べる |
| tablet / desktop | 1列、入力の幅は最大 40rem |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | FormField（ラベル・誤り・案内の結び付け）。誤りのまとめは `role="alert"` |
| Keyboard interaction | 入力の順は画面の上から下。保存は「保存」のボタン（Enter で送るのは1行の入力だけ） |
| Label / aria-label | ラベルは入力の上。必須は印「*必須」と `aria-required`。長さの上限と形の案内は `aria-describedby` |
| Screen reader | 誤りはその入力に `aria-invalid` と結んだ文。保存の成功は Toast（`aria-live`） |
| Focus management | 誤りのときはまとめへ、まとめのリンクから各入力へ。保存の後は移った先の見出しへ |

### 離れるときの確かめ（SM6）

変更があるまま「やめる」・メニュー・戻る操作・作業ロールの切り替えで離れようとしたら Modal を出す: 見出し「保存していない変更があります」、文「この画面を離れると、入力した内容は失われます。」、ボタン「留まる」（はじめのフォーカス）・「離れる」。英語 `You have unsaved changes` / `If you leave this page, your changes will be lost.` / `Stay` / `Leave`。

## DeleteConfirmModal（S5）

`role="alertdialog"`。見出し「<テーブルの表示名> <行を見分ける値> を削除しますか」、文「この操作は元に戻せません。」、ボタン「やめる」（はじめのフォーカス）・「削除する」。背景のクリックで閉じない。Escape は「やめる」と同じ。英語 `Delete <name> <id>?` / `This cannot be undone.` / `Cancel` / `Delete`。

## RowActionsMenu（行の「操作」）

Dropdown（`placement="bottom-end"`）。トリガーの名前「操作: <行を見分ける値>」。中身は行えるものだけ（「詳細」「更新」「削除」）。「削除」は DeleteConfirmModal を開く。既存の管理の一覧（利用者・グループ・ロール）のトリガーも同じ名前の形に直す（AC4.1.1）。

## 文言（日本語・英語）

| 場面 | 日本語 | 英語 |
|---|---|---|
| NULL | （値なし） | (none) |
| 0 件（条件あり） | 条件に合う行がありません。 | No rows match your search. |
| 0 件（行なし） | このテーブルには行がありません。 | This table has no rows. |
| 主キーの無いテーブル・主キーが NONE | このテーブルは行を1件に決められないため、詳細・更新・削除はできません。 | Rows in this table cannot be identified one by one, so you cannot view, edit, or delete them. |
| ビュー | このビューは読み取りだけです。 | This view is read-only. |
| 書き込みの設定が無い | いまは業務データを変えられません（書き込みの設定がありません）。管理者に確かめてください。 | Business data cannot be changed right now (no write connection is configured). Contact your administrator. |
| 403 | このテーブルを見る権限がありません。作業ロールを確かめてください。 | You do not have permission to view this table. Check your work role. |
| 404 テーブル | テーブルが見つかりません。 | Table not found. |
| 404 行 | 行が見つかりません（ほかの人が削除した可能性があります）。 | Row not found (it may have been deleted by someone else). |
| DSL が無い | いまは業務データを使える状態ではありません。管理者に確かめてください。 | Business data is not available right now. Contact your administrator. |
| つながらない | 業務データのデータベースとつながりません。時間をおいてやり直してください。 | Cannot connect to the business database. Please try again later. |
| 時間切れ（保存） | 保存されたか分かりません。 | We could not confirm whether the change was saved. |
| 同じ値 | 同じ値がすでにあります。 | This value already exists. |
| 参照先に無い値 | 参照先に無い値です。 | This value does not exist in the referenced data. |
| 入力できない項目の NOT NULL | このテーブルの作成には、入力できない項目の値が要ります。管理者に確かめてください。 | Creating a row in this table requires values for fields you cannot enter. Contact your administrator. |
| 外部キーで削除できない | ほかのデータから使われているため削除できません。 | This row cannot be deleted because other data uses it. |
| 行を1つに決められない | 行を1件に決められないため、この操作はできません。 | This operation cannot be performed because the row cannot be identified uniquely. |
| DSL と対象DB の食い違い | テーブルの定義とデータベースが食い違っています。管理者に確かめてください。 | The table definition does not match the database. Contact your administrator. |
| 作成・更新・削除の成功 | 作成しました。／更新しました。／削除しました。 | Created. / Updated. / Deleted. |
