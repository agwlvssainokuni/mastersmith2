# デザインシステムの対応 — reference-lookup-cleanup

出典: `mockups.md`・`interaction-spec.md`。部品の口は、報告ではなく固定先（`vendor/make-you-chic-ui` の `ecb07e9`）のソース（`packages/make-you-chic-ui/src/components/`）を読んで確かめた（`team.md` の Way of Working）。

## 画面の要素と部品

| 画面の要素 | 部品 | 確かめた口 |
|---|---|---|
| 参照の選択（REFERENCE の `select`） | `Select`＋`FormField` | `FormField` が ID・説明・誤りの結び付けを入力に渡す。既存の作成・更新の画面が `Select` を使っている |
| 参照の選択（REFERENCE の `radio`） | `RadioGroup` | 既存の画面が使っている |
| 入力不可の項目（M2・M3） | `TextInput`（読み取り専用）＋`FormField`（`helperText` に説明文） | `TextInput` は `<input>` の属性（`readOnly`・`aria-readonly`）を受ける |
| LOOKUP の項目（M4） | `TextInput`（読み取り専用）＋`Button`（「選ぶ…」「クリア」） | 同上。`Button` は既存 |
| ピッカーのダイアログ（M5） | `Modal`（`size="lg"`・`closeOnBackdropClick={false}`・`initialFocusRef`・`finalFocusRef`） | `ModalProps` に口がある。重ねて開く仕組み（`ModalStackContext`）あり |
| 検索条件（M5） | `TextInput`・`Select`＋`FormField`＋`Button`（「検索」） | 既存 |
| 結果の表（M5） | `Table`（`columns` の `render` に「選ぶ」の `Button` を置く、ページ送り、`labels`、`aria-label`） | `TableColumn.render`・`TableProps.page/pageSize/onPageChange/labels/aria-label` がある |
| 初期・0 件・失敗・権限の案内 | `Alert` と説明文（`FormField` の `helperText`） | 既存 |
| 保存の成功の通知 | `Toast` | 既存（変えない） |

## make-you-chic-ui に無いもの（上流へ取り込みを諮る一覧）

| 足りない点 | 依頼 | 取り込みまでの手当て |
|---|---|---|
| 検索つきのピッカー（検索条件＋結果の表＋1 行を選ぶ） | 出さない | `Modal`＋`TextInput`＋`Select`＋`Table`（`render` の「選ぶ」ボタンの列）の組み合わせで作れる。専用の部品は要らない（frontend の側で、`features/tables` の中に置く） |
| 入力不可の説明つきの表示 | 出さない | `TextInput` の読み取り専用＋`FormField` の説明文で足りる |
| 選択済みのラベルの欄 | 出さない | 読み取り専用の `TextInput` で足りる |
| 表の行の単一選択（ラジオ型の行の選択） | 出さない | `Table` の複数選択（`selectedRowIds`）は使わず、「選ぶ」ボタンの列で単一選択にする |
| 表の横のスクロールの領域のフォーカスとラベル | 出さない | 固定先の `ecb07e9` の `Table` の枠には、フォーカス（`tabIndex`）もラベルも無いことをソースで確かめた。既存の `frontend/src/shared/scroll-region` で枠を包む（前の Intent と同じ手当て） |

- **結論**: この Intent では、make-you-chic-ui への新しい依頼（依頼（7））は出さない見込み。理由は、必要な部品がすべて既存の部品の組み合わせで作れ、`Modal`・`Table` の口が足りているため。設計の段で、組み合わせの実装中に足りない口が見つかったら、`team.md` の決まりどおり、先に上流への依頼を一覧にして依頼者に諮る。
- 依頼を出さない代わりの手当てとして、ピッカーのダイアログの部品は `features/tables` に置き、複数の機能で使うことになったら `src/shared/` へ移す。

## デザイントークン

新しいトークンは足さない。入力不可の説明文は、既存の淡い文字の色（`--color-text-muted` 相当）を使い、背景との対比 4.5:1 を保つ。灰色だけで示さず、文字で出す。
