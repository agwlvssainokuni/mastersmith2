# 操作と状態の仕様 — reference-lookup-cleanup（確認の指摘を反映）

出典: `mockups.md`（M1〜M9）、承認済みのストーリー、`refined-mockups-questions.md`（Q1〜Q6）。部品ごとの書式は `.claude/knowledge/aidlc-design-agent/component-spec-template.md`。画面の組み立ては既存の `BusinessDataFormPage`（作成・更新）に参照の項目を足す形で、画面の構成・保存・離れるときの確かめ・誤りのまとめは変えない。

## 共通の決まり

- 参照の項目の種類は、定義の応答（サーバー）が決める。画面は、`formPart` と、選択肢（`choices`）、ピッカーの情報、「入力不可の理由」の印だけで部品を選ぶ。画面で権限を判定しない（サーバー側が正）。
- 入力不可の理由は 2 つで、文を分ける: 権限がない（M2）／候補が多い（M3）。必須の項目では、さらに「必須の参照項目に値を入れられません」（M7）を出す。
- 参照先の行の値（選択肢・ラベル・検索の値）を、`data-testid`・`id`・`document.title`・ログに入れない。ラベルは文字として出し、HTML として描かない。
- 保存の本文には、値が変わった項目だけを入れる（既存の動作）。入力不可の項目は、画面が本文に含めない。

## ReferenceSelectField（M1。REFERENCE の選択）

| Field | Value |
|---|---|
| Component | ReferenceSelectField |
| Description | 参照先の行を選択肢にした `select`／`radio`（`FormField` で包む） |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| loading | 選択肢の取得の間。入力できず「読み込み中」（`aria-busy`） | 画面を開いた直後 |
| default | 選択肢が出ている。保存済みの値は選ばれた状態 | 取得に成功 |
| empty | 選択肢が 0 件。「選べる候補がありません」。必須の項目は保存できない理由を添える | 参照先が 0 行 |
| error | 保存前の確かめの拒否（409）。項目の近くに誤り、フォーカスは誤りのまとめへ | AC3.1.2・AC3.1.3 |
| readonly-permission | M2 の見せ方 | 権限の印 |
| readonly-too-many | M3 の見せ方 | 候補が多いの印 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| column | ColumnDefinition | yes | — | 定義の 1 カラム（`formPart`・`choices`・理由の印・`required`） |
| value | string | yes | — | 今の値（空の文字は未選択） |
| onChange | (value: string) => void | yes | — | 選択の変更 |
| error | string | no | — | 項目の近くの誤り |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 1 列。選択肢の一覧は画面の幅に収める |
| tablet (768–1024px) | 既存の作成・更新の画面と同じ |
| desktop (>1024px) | 既存の作成・更新の画面と同じ |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `combobox`／`listbox`（ネイティブの `select`）または `radiogroup` |
| Keyboard interaction | 矢印で選ぶ、Enter で決める、Escape で閉じる（ネイティブの動作） |
| Label / aria-label | 項目名と必須の印（`FormField`）。同名のラベルは値を併記して見分ける |
| Contrast ratio | WCAG AA（4.5:1 / 3:1） |
| Screen reader | 取得中は `aria-busy`、誤りは `aria-describedby` で結ぶ |
| Focus management | 誤りのときは誤りのまとめへ移し、入力した値は消さない |

## ReadonlyReferenceField（M2・M3・M7。入力不可の項目）

| Field | Value |
|---|---|
| Component | ReadonlyReferenceField |
| Description | 参照先を読めない、または候補が多いため入力不可の項目。保存されている値そのものを読み取り専用の欄に出し、理由の説明文を下に置く |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| permission | 「参照先を読む権限がないため変更できません」 | 権限の印 |
| too-many | 「候補が多いため選べません。管理者に、検索（ピッカー）への切り替えを依頼してください」 | 候補が多いの印 |
| required-blocked | 必須（NOT NULL）の項目で、**作成の画面だけ**: 項目の誤り 1 つに「必須の参照項目に値を入れられません」と理由・次の手をまとめて書く（説明文は併記しない。`FormField` は `error` があると説明文を出さないため）。更新で保存済みの値があるときは出さず、permission／too-many の説明文だけを出す | 必須かつ上の 2 つ、かつ作成の画面 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| column | ColumnDefinition | yes | — | 定義の 1 カラム |
| value | string | yes | — | 保存されている値（ラベルではない）。作成の画面では空 |
| reason | 'permission' \| 'too-many' | yes | — | 理由の印 |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 欄と説明文を縦に並べる |
| tablet (768–1024px) | 既存の画面と同じ |
| desktop (>1024px) | 既存の画面と同じ |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | ネイティブの `input`（`readonly`＋`aria-readonly`） |
| Keyboard interaction | Tab でフォーカスが止まる（飛ばさない） |
| Label / aria-label | 項目名。説明文は `aria-describedby` で結ぶ |
| Contrast ratio | 説明文は 4.5:1。灰色だけで示さず、文字で出す |
| Screen reader | 値を読み上げで確かめられる（値を読めない `disabled` だけにしない） |
| Focus management | 説明文に到達できる |

## LookupField（M4。LOOKUP の項目）

| Field | Value |
|---|---|
| Component | LookupField |
| Description | 読み取り専用の欄にラベルと値を並べ、「選ぶ…」でダイアログを開く。NULL を許す項目では「クリア」も置く |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| empty | 欄は空。説明文「検索して選びます」 | 値なし |
| selected | 「ラベル (値)」。ラベルが引けないときは値だけ | 選んだ後／更新で保存済みの値がある |
| label-loading | 更新の画面で、ラベルを 1 件読む間（`aria-busy`） | 画面を開いた直後 |
| readonly-permission | M2 の見せ方（「選ぶ…」を出さない） | 権限の印 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| column | ColumnDefinition | yes | — | `formPart: lookup` と、ピッカーの情報（ダイアログに渡す） |
| value | string | yes | — | 今の値 |
| label | string \| null | no | null | 選んだ行のラベル |
| onChange | (value: string, label: string \| null) => void | yes | — | 選んだとき・クリアしたとき |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 欄とボタンを縦に並べる |
| tablet (768–1024px) | 欄の右にボタンを並べる |
| desktop (>1024px) | 同上 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 欄は `readonly` の `input`。ボタンは `button` |
| Keyboard interaction | Tab で欄→「選ぶ…」→「クリア」。Enter／Space で開く・クリア |
| Label / aria-label | 項目名。ボタンの名前は「{項目名}を選ぶ」「{項目名}をクリア」 |
| Contrast ratio | WCAG AA |
| Screen reader | 選んだ後は、選んだ行のラベルと値が読まれる |
| Focus management | ダイアログを閉じたら「選ぶ…」ボタンへ戻す（`finalFocusRef`）。クリアしたら欄へ |

## LookupPickerDialog（M5。ピッカーのダイアログ）

| Field | Value |
|---|---|
| Component | LookupPickerDialog |
| Description | 検索条件を入れて、結果の表から 1 行を選ぶダイアログ（`Modal size="lg"`） |
| Category | feedback |

### States

| State | Description | Trigger |
|---|---|---|
| initial | 条件の入力だけ。「条件を入れて『検索』を押してください」 | 開いた直後 |
| empty-condition | 条件が空のまま「検索」。要求を送らず「条件を入れてください」 | AC2.2.5 |
| searching | 「検索中」。「検索」を押せない | 検索の要求中 |
| results | 表と件数（`aria-live`）、ページ送り | 成功 |
| no-results | 「該当する行がありません」 | 0 件 |
| failed | 「検索できませんでした。もう一度検索してください」。条件は保つ | 503・時間切れ |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| open | boolean | yes | — | 開閉 |
| field | LookupInfo | yes | — | ピッカーの情報（参照元のカラムの指定。検索条件の項目・演算子、一覧のカラム・並び順） |
| onSelect | (value: string, label: string) => void | yes | — | 「選ぶ」を押したとき（ダイアログを閉じる） |
| onClose | () => void | yes | — | 閉じる／Escape |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 画面の幅に収める。条件は縦に並べ、表は Modal の中で横にスクロール（領域にフォーカスとラベル）。画面全体は横にスクロールしない |
| tablet (768–1024px) | `lg` の幅 |
| desktop (>1024px) | `lg` の幅 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`（名前はタイトル「{項目名}を選ぶ」） |
| Keyboard interaction | Tab で条件→検索→表→ページ送り→「閉じる」。Enter で検索。「選ぶ」は Enter／Space。Escape で閉じる。背景をクリックしても閉じない（`closeOnBackdropClick={false}`） |
| Label / aria-label | 検索条件の項目ごとにラベル（表示名と演算子）。表に `aria-label`（「{項目名}の検索結果」）。「選ぶ」ボタンの名前は「{行を見分けるラベル}を選ぶ」 |
| Contrast ratio | WCAG AA |
| Screen reader | 結果の件数の変化を `aria-live="polite"`、ページ送りは現在／総ページを読み上げる |
| Focus management | 開いたとき、最初の検索条件へ（`initialFocusRef`）。閉じたら「選ぶ…」ボタンへ戻す（`finalFocusRef`）。フォーカスはダイアログの中に閉じ込める（Modal の標準の動作） |

## 保存前の確かめの拒否（M6）

- 409 `BUSINESS_REFERENCE_NOT_FOUND`（その項目の誤り）は、既存の誤りのまとめ（`role="alert"`）と、その項目の近くの誤り（`aria-describedby` で結ぶ）に出す。フォーカスは誤りのまとめへ。入力した値は消さない。
- 存在しない場合と権限がない場合は、同じ文にする（区別できない）。

## 離れるときの確かめ・保存中（変えない）

- 変更のある間に画面を離れると確かめの Modal を出す（既存。はじめのフォーカスは「留まる」）。ピッカーのダイアログは Modal で背景を無効にするため、サイドバーなどの操作では離れられない。ブラウザの戻る操作などで離れようとしたときは、確かめの Modal が**ピッカーのダイアログの上に重なって開く**（make-you-chic-ui の Modal の重ね順の仕組み）。「留まる」を選ぶと確かめの Modal だけを閉じ、ピッカーは開いたまま、フォーカスは重なる前にあった要素（ダイアログの中）へ戻る。「離れる」を選ぶと、ピッカーも閉じて離れる。
- 保存中は保存・やめるを押せず「保存しています…」。ピッカーは開けない。

## 後の段で決める点

- 演算子が選べる定義のときの部品（`Select`）の使い方、検索条件の入力の型（文字・数・日付）。
- 「選ぶ」ボタンの読み上げの名前に使う「行を見分けるラベル」の作り方（`lookupList` の先頭のカラムか、ラベルのカラムか）。
- 選んだ値が参照先で消えていたときの更新の画面の見せ方（ラベルが引けないときは値だけ。AC2.3.2）の細部。
