# make-you-chic-ui への依頼: Dropdown のメニューの項目を押せない形にし、理由の文を添える

依頼元: mastersmith2 の Intent 260930-user-admin（利用者の管理の画面）。対象の版: make-you-chic-ui の今の固定先 `077f5b4`（Button: primaryボタンのhover/active時の文字色コントラスト不足を修正）。

## 背景

mastersmith2 の利用者の管理の画面では、一覧の各行に「操作」のメニュー（`Dropdown`）を置き、その行で今できる操作を並べます。管理者は、自分自身の管理者の印を外す操作と、自分自身の利用を止める操作をできません。この2つは、メニューから消すのではなく、**押せない形で出し、押せない理由を読めるようにする**ことに決めました。理由を示さずに項目を消すと、管理者は「なぜこの行だけ操作が無いのか」が分からないためです。

今の `Dropdown` の `MenuItem` は `label`・`href`・`onClick` だけを持ち、押せない形と説明の文を表せません。そのため、次の3点を `Dropdown` に足していただきたいです。

## 依頼の内容

### R1. 項目を押せない形にする

- `MenuItem` に `disabled?: boolean` を足す。
- `disabled` の項目は、`aria-disabled="true"` を付け、押しても（クリック・Enter・Space）`onClick` を呼ばず、メニューも閉じない。
- 見た目で押せないことが分かる（文字の色を薄くするなど）。ただし、文字と背景のコントラストは light・dark の両方で 4.5:1 を保つ（WCAG 2.1 AA。押せない理由を文字で読むため、薄くしすぎない）。
- `href` の項目に `disabled` を付けたときも、移動しない（`href` を出さないか、`preventDefault` する）。

### R2. 項目に説明（理由）の文を添える

- `MenuItem` に `description?: string` を足す。
- 説明の文は、項目の名前の下に小さな文字で表示する。
- 説明の文に id を付け、項目に `aria-describedby` で結び、読み上げで理由が分かるようにする。
- 押せる項目にも説明を付けられる（押せない項目だけに限らない）。

### R3. 押せない項目へのキーボードのフォーカス

- ↑↓・Home・End で、押せない項目にもフォーカスが移る（WAI-ARIA Authoring Practices のメニューの推奨。読み上げで押せない理由を聞けるようにするため）。
- メニューを開いたときの最初のフォーカスは、今と同じく最初の項目とする（押せない項目でもよい）。

## 受け入れの目安

- `disabled` の項目をクリック・Enter・Space しても `onClick` が呼ばれず、メニューが開いたままである。
- `disabled` の項目に `aria-disabled="true"` が付き、`description` があれば `aria-describedby` で説明の文に結ばれている。
- ↓ で押せない項目にもフォーカスが移る。
- 押せない項目と説明の文の文字のコントラストが、light・dark の両方で 4.5:1 以上である。
- vitest-axe の検査で違反が無い（押せない項目と説明を含むメニューを開いた状態）。
- 今の使い方（`label`・`href`・`onClick` だけ）の動きは変わらない。

## 使い方の例（mastersmith2 の側）

```tsx
<Dropdown
  trigger={<Button variant="secondary" size="sm" aria-label="佐藤 一郎（admin@example.com）の操作">操作</Button>}
  items={[
    { label: '管理者の印を外す', disabled: true, description: '自分自身の印は外せません' },
    { label: '利用を止める', disabled: true, description: '自分自身は止められません' },
    { label: 'ロックを解除（失敗回数を戻す）', onClick: openResetDialog },
    { label: '氏名・言語を直す', onClick: openEditDialog },
  ]}
/>
```

## 参考（気づいた点。今回の依頼の範囲の外）

- trigger に付く `data-testid="dropdown-trigger"` と、項目の `data-testid` が、1つの画面に複数の `Dropdown` を置くと同じ値で並びます（表の行ごとにメニューを置く使い方）。テストでの見分けのため、呼ぶ側が `data-testid` を渡せると助かります。
- trigger に付ける `aria-haspopup` は今 `'true'` です。WAI-ARIA の値としては `'menu'` の方が意図がはっきりします。

## mastersmith2 の側の扱い

- make-you-chic-ui に取り込まれたら、mastersmith2 は固定先を更新して使います（固定先の更新は承認を得た専用のコミットで行い、fast-forward で統合する）。
- 取り込みが mastersmith2 のコード生成に間に合わないときの扱い（frontend の側で同じ形のメニューを作って後で置き換える、など）は、mastersmith2 の Delivery Planning とコード生成の計画で決めます。
