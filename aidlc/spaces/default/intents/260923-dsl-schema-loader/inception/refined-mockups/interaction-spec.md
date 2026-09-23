# Interaction Spec — DSL の管理画面

部品ごとの仕様を、`.claude/knowledge/aidlc-design-agent/component-spec-template.md` の形で書く。部品の名前は仮で、実装の名前は機能設計・コード生成で決める。画面の配置は `mockups.md`、使う make-you-chic-ui の部品は `design-system-mapping.md`、アクセシビリティの点検は `accessibility-checklist.md`。

画面の幅の振る舞いは、どの部品も `mockups.md` の7節に従う（パソコンの幅を主に、狭い幅では縦に並べ、表は横に動かして見る）。下の各部品の「Responsive Behaviour」は、それと違う点だけを書く。

## 共通の操作の決まり

- **確かめる表示**: 読み込み・投入・履歴からの戻しで、プレビューがあるときは置き換えの確認を出す。適用と破棄は必ず確認を出す（M4: A）。確認は Modal で、はじめのフォーカスは「やめる」。Escape と「やめる」で閉じ、何も変えない。閉じるとフォーカスは開く前のボタンに戻る。背景をクリックしても閉じない（取り消しにくい操作のため）。
- **処理中**: 押したボタンを処理中の表示（印と文言）にし、読み込み・投入・履歴からの戻し・適用・破棄のボタンを使えなくする。タブの切り替えと表示の確認はできる。処理中と結果は、読み上げ用の領域（`aria-live="polite"`、失敗は `assertive`）で伝える（Q8: A）。
- **結果の知らせ**: 成功は Toast（数秒で消える。読み上げでも伝わる）。失敗は画面の中の Alert（消えない）。失敗の文言は、何が起きたか・どうすればよいかを書き、内部のエラーの文言・接続先の値を出さない。
- **サーバーの `code` から文言へ**: 画面は、応答の Problem Details の `code` で文言を選ぶ。`code` の一覧は契約の設計で決め、文言は ja・en を対で持つ。
- **見たプレビューを指定する**: プレビューの表示の応答に含まれる識別を、適用の要求に付ける（US4.2）。
- **状態の読み直し**: 読み込み・投入・戻し・適用・破棄の成功の後と、適用の拒否の後に、「今の状態」とプレビューを読み直す。

---

## DslStatusPanel（今の状態）

| Field | Value |
|---|---|
| Component | DslStatusPanel |
| Description | 適用中の DSL とプレビューの識別・出どころ・置いた人・日時を示し、スキーマの読み込みのボタンを持つ |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| default | 適用中とプレビューの両方がある | 画面の表示 |
| loading | 状態を読んでいる | 画面の表示、操作の後の読み直し |
| empty-applied | 適用中の DSL が無い（「まだ適用していません」） | 初めての導入 |
| empty-preview | プレビューが無い（「プレビューはありません」） | 適用・破棄の後 |
| busy | スキーマの読み込みを処理中（ボタンが処理中の表示） | 読み込みのボタン |
| error | 状態を読めなかった（Alert と「もう一度読み込む」） | 通信の失敗 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| applied | object \| null | yes | — | 適用中の DSL の識別・適用した人・日時 |
| preview | object \| null | yes | — | プレビューの識別・出どころ・置いた人・日時 |
| busy | boolean | yes | false | 処理中かどうか |
| onReadSchema | () => void | yes | — | スキーマの読み込み（置き換えの確認を含む） |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 適用中・プレビューの欄と、ボタンを縦に並べる |
| tablet (768–1024px) | 適用中・プレビューの欄を縦に並べる |
| desktop (>1024px) | 既定の配置 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 領域（`section`、見出し「今の状態」で名前を付ける） |
| Keyboard interaction | 読み込みのボタンは Tab で届き、Enter・Space で押せる |
| Label / aria-label | 識別の省略は、全体をツールチップと `aria-label`（または視覚的に隠した文字）で読めるようにする |
| Contrast ratio | WCAG AA（文字 4.5:1、部品 3:1） |
| Screen reader | 処理中「スキーマを読み込んでいます」、成功・失敗の結果を読み上げる |
| Focus management | 読み込みの成功後はプレビューのタブの見出しへフォーカスを移す |

---

## DslTabs（プレビュー・投入・履歴）

| Field | Value |
|---|---|
| Component | DslTabs |
| Description | 3つのタブの切り替え。make-you-chic-ui の Tabs を使う |
| Category | navigation |

### States

| State | Description | Trigger |
|---|---|---|
| default | 「プレビュー」が選ばれている | 画面の表示 |
| switched | 別のタブが選ばれている | タブの選択、操作の成功（プレビューへ切り替え） |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| selected | 'preview' \| 'submit' \| 'history' | yes | 'preview' | 選ばれているタブ |
| onSelect | (tab) => void | yes | — | タブの切り替え |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `tablist`・`tab`・`tabpanel`（make-you-chic-ui の Tabs に従う） |
| Keyboard interaction | 左右の矢印でタブを移り、Tab でパネルへ入る |
| Label / aria-label | 各タブの文字が名前 |
| Contrast ratio | WCAG AA |
| Screen reader | 選ばれているタブが伝わる |
| Focus management | 操作の成功でプレビューへ切り替えたときは、プレビューのパネルの見出しへフォーカスを移す |

---

## DslPreviewPanel（プレビュー）

| Field | Value |
|---|---|
| Component | DslPreviewPanel |
| Description | 検証を通ったこと・照合の警告・要約・違い・メニューの木を示し、ダウンロード・破棄・適用の操作を持つ |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| default | プレビューがあり、警告が無い | プレビューの表示 |
| warning | 対象DB との食い違い、または照合できなかった警告がある | 照合の結果 |
| no-applied | 適用中の DSL が無く、すべてを「増えた」で示す | 初めての導入 |
| empty | プレビューが無い（案内と、読み込み・投入のタブへの操作） | 適用・破棄の後、別の管理者の破棄 |
| loading | プレビューを読んでいる | 表示、読み直し |
| rejected | 適用が拒否された（Alert と「最新のプレビューを表示する」） | 適用の拒否 |
| busy | 適用・破棄を処理中 | 確認の後 |
| error | プレビューを読めなかった | 通信の失敗 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| preview | object \| null | yes | — | 要約・違い・警告・メニューの木・識別 |
| applied | object \| null | yes | — | 違いの計算の相手の有無 |
| busy | boolean | yes | false | 処理中 |
| onDownload | () => void | yes | — | プレビュー中の DSL のダウンロード |
| onDiscard | () => void | yes | — | 破棄（確認を含む） |
| onApply | (previewId) => void | yes | — | 適用（確認を含む、見たプレビューの識別を渡す） |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `tabpanel` の中に、見出し（要約・違い・警告・メニュー）で区切った節 |
| Keyboard interaction | 違いの表の行の開閉は、行の先頭のボタン（`aria-expanded`）を Enter・Space で操作する。メニューの木の開閉も同じ |
| Label / aria-label | 区分のバッジは文字（増えた・減った・変わった・変わらない）で示す |
| Contrast ratio | WCAG AA。検証の結果と警告は、色に加えて記号と文言で示す |
| Screen reader | 適用の成功「DSL を適用しました」、拒否の理由を読み上げる |
| Focus management | 拒否のときは Alert にフォーカスを移す。破棄・適用の成功の後は、空の状態の案内の見出しへ移す |

---

## DslDiffTable（適用中との違い）

| Field | Value |
|---|---|
| Component | DslDiffTable |
| Description | テーブルごとの区分（バッジ）とカラムの違いの数を示し、行を開くとカラムの違いを示す（Q4: A） |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| default | 違いのあるテーブルだけを表示 | 表示 |
| show-all | 変わらないテーブルも表示 | 「すべて表示」を入れる |
| expanded | ある行のカラムの違いを開いている | 行の開閉のボタン |
| empty | 違いが無い（「適用中の DSL と違いはありません」） | 同じ内容のプレビュー |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| diff | array | yes | — | テーブルごとの区分とカラムの違い |
| showAll | boolean | no | false | 変わらないテーブルも出すか |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 表は横に動かして見る |
| tablet (768–1024px) | 表は横に動かして見る |
| desktop (>1024px) | 既定の配置 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 表（make-you-chic-ui の Table）。開いた行の中のカラムの表は、入れ子の表として見出しを持つ |
| Keyboard interaction | 開閉のボタンと「すべて表示」（Switch）に Tab で届く |
| Label / aria-label | 開閉のボタンは「dept_mst のカラムの違いを開く」のように対象を含める |
| Contrast ratio | WCAG AA |
| Screen reader | 件数（増えた 1・減った 1・変わった 3）を表の前の文字で示す |
| Focus management | 開閉してもフォーカスはボタンに残す |

---

## DslSubmitForm（投入）

| Field | Value |
|---|---|
| Component | DslSubmitForm |
| Description | 「ファイルを選ぶ」と「貼り付ける」を切り替えて DSL を入力し、投入する（Q3: A） |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | 「ファイルを選ぶ」が選ばれ、何も入力されていない（投入は押せない、案内を表示） | 表示 |
| ready | 入力がある | ファイルの選択、貼り付け |
| too-large | 選んだファイルが 5MB を超える（送る前に表示） | ファイルの選択 |
| confirm-replace | 置き換えの確認を表示中 | プレビューがあるときの投入 |
| busy | 投入を処理中 | 投入 |
| error | 誤りの一覧を表示中（入力は残る） | 検証を通らない |
| disabled | ほかの処理中 | 読み込み・適用などの処理中 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| mode | 'file' \| 'paste' | yes | 'file' | 入力のしかた |
| hasPreview | boolean | yes | — | 置き換えの確認を出すか |
| busy | boolean | yes | false | 処理中 |
| onSubmit | (input) => void | yes | — | 投入（選んでいる方だけを送る） |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 入力のしかたは RadioGroup（`radiogroup`）。ファイルの選択は `input type="file"` を見える label と結ぶ。貼り付けは Textarea |
| Keyboard interaction | 入力のしかたは矢印で切り替える。ファイルの選択はボタンから Enter・Space で開く |
| Label / aria-label | 「DSL のファイル（YAML、5MB まで）」「DSL（YAML、5MB まで）」を見える label にする。案内・大きさの誤りは `aria-describedby` で結ぶ |
| Contrast ratio | WCAG AA |
| Screen reader | 処理中「DSL を確かめています」、受け付け「DSL をプレビューに置きました」、誤り「DSL に誤りが N件あります」 |
| Focus management | 誤りのときは件数の警告へフォーカスを移す。受け付けたときはプレビューのパネルの見出しへ移す |

---

## DslErrorList（誤りの一覧）

| Field | Value |
|---|---|
| Component | DslErrorList |
| Description | 件数の警告と、行・列・場所・内容の表。先頭の 100件を表示し、残りの件数を示す（Q5: A） |
| Category | feedback |

### States

| State | Description | Trigger |
|---|---|---|
| default | 100件以下の誤りを全部表示 | 検証を通らない |
| truncated | 100件を超え、先頭 100件と残りの件数を表示 | 誤りが 100件を超える |
| single-reason | 危険な形・版の非対応などで、理由の1件だけを表示 | 大きさ・深さ・別名・タグ・重複キー・版 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| total | number | yes | — | 誤りの総数 |
| items | array | yes | — | 行・列・場所・内容（先頭 100件） |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 表は横に動かして見る。場所の列は折り返す |
| tablet (768–1024px) | 表は横に動かして見る |
| desktop (>1024px) | 既定の配置 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 件数は Alert（`role="alert"`）、一覧は表（Table、列見出しつき） |
| Keyboard interaction | 表は読むだけ。件数の警告にフォーカスが移る（`tabindex="-1"`） |
| Label / aria-label | 表の名前は「DSL の誤りの一覧」 |
| Contrast ratio | WCAG AA。誤りは色に加えて記号と文言で示す |
| Screen reader | 件数と、表示を 100件に絞ったことを読み上げる |
| Focus management | 表示された時点で件数の警告にフォーカスを移す |

---

## DslHistoryTable（履歴）

| Field | Value |
|---|---|
| Component | DslHistoryTable |
| Description | 適用の履歴（新しい順、最大 20件）と、適用中の版の目印、ダウンロード・プレビューに戻す操作 |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| default | 履歴がある | 表示 |
| empty | 履歴が無い（「まだ一度も適用していません」） | 初めての導入 |
| loading | 履歴を読んでいる | 表示 |
| busy | プレビューに戻す処理中 | 戻す |
| error | 履歴を読めなかった、または戻した版が検証を通らない（誤りの一覧） | 通信の失敗、検証 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| entries | array | yes | — | 適用した日時・適用した人・識別・適用中かどうか |
| hasPreview | boolean | yes | — | 置き換えの確認を出すか |
| onRestore | (entryId) => void | yes | — | プレビューに戻す（確認を含む） |
| onDownloadApplied | () => void | yes | — | 適用中の DSL のダウンロード |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 表は横に動かして見る |
| tablet (768–1024px) | 表は横に動かして見る |
| desktop (>1024px) | 既定の配置 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 表（Table、列見出しつき） |
| Keyboard interaction | 各行の操作のボタンに Tab で届く |
| Label / aria-label | 「2026-09-20 16:40 の版をプレビューに戻す」のように、どの版かを含める |
| Contrast ratio | WCAG AA。適用中は「適用中」の文字（Badge）で示す |
| Screen reader | 戻したときの結果を読み上げる |
| Focus management | 戻した後はプレビューのパネルの見出しへ移す |

---

## DslConfirmDialog（確かめる表示）

| Field | Value |
|---|---|
| Component | DslConfirmDialog |
| Description | 置き換え・適用・破棄の確認。make-you-chic-ui の Modal を使う |
| Category | feedback |

### States

| State | Description | Trigger |
|---|---|---|
| replace | 今のプレビューの出どころ・置いた人・日時を示す | 読み込み・投入・戻し（プレビューがあるとき） |
| apply | 違いの件数と警告の件数を示す | 適用 |
| discard | 破棄したものは戻せないことを示す | 破棄 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| kind | 'replace' \| 'apply' \| 'discard' | yes | — | 確認の種類 |
| detail | object | yes | — | 示す情報 |
| onConfirm | () => void | yes | — | 確かめる |
| onCancel | () => void | yes | — | やめる |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`（`aria-modal="true"`、見出しで名前を付け、本文を `aria-describedby` で結ぶ） |
| Keyboard interaction | Tab は表示の中だけを巡る。Escape で閉じる（やめると同じ） |
| Label / aria-label | 確かめるボタンは操作の名前（置き換える・適用する・破棄する）にする。「OK」にしない |
| Contrast ratio | WCAG AA |
| Screen reader | 開いたときに見出しと本文が読まれる |
| Focus management | はじめは「やめる」。閉じると開く前のボタンへ戻す |
