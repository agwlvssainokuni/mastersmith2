# make-you-chic-ui への修正のお願い — Modal を閉じた後にフォーカスが元に戻らない

## 概要

`Modal` を閉じた直後、フォーカスが開く前の要素（またはアプリが指定した要素）に戻らず、`body` に落ちます。キーボードと支援技術の利用者は、閉じるたびにページの先頭から操作をやり直すことになります（WCAG 2.1 の 2.4.3 フォーカス順序）。

- 対象の版: `main` の `3d9521a`（Dropdown: 押せない項目のhover/focusコントラスト不足と読み上げ重複を修正）
- 起きる環境: 実際のブラウザ（Playwright の Chromium で確認）。jsdom は `inert` を扱わないため、単体のテスト（Vitest＋jsdom）では起きません。

## 再現の手順

1. `ModalStackProvider` の下で、ボタン（または `Dropdown` の項目）から `Modal` を開く。
2. 「キャンセル」などのボタン・Escape・閉じるボタンのどれかで `Modal` を閉じる。
3. `document.activeElement` を見ると `body` になっている。

アプリの側で、閉じた直後（同じ描画の確定の後の `useEffect` など）に開いた元のボタンへ `focus()` を呼んでも、同じく `body` のままです。

## 原因の見立て

閉じたときの処理の順が、次のようになっていると見ています。

1. `Modal` が `open=false` で描画を返さなくなり、`useFocusTrap` の後始末が `previouslyFocused?.focus()` を呼ぶ。
2. ところがこの時点では、`ModalStackContext` の `useEffect`（`entries` が変わったら `<body>` の子の `inert` を付け外しする）がまだ走っておらず、開いた元の要素を含む `<body>` の子に `inert` が付いたまま。`inert` の中の要素は `focus()` を受け付けないため、フォーカスは `body` に落ちる。
3. その後に `entries` が空になり、`inert` が外れる（ここでは誰もフォーカスを当て直さない）。

あわせて、`Dropdown` の項目から `Modal` を開いた場合は、`previouslyFocused` が閉じたメニューの項目（文書から外れた要素）になるため、`inert` が外れても戻す先がありません。

該当の箇所:

- `packages/make-you-chic-ui/src/utils/useFocusTrap.ts`（後始末の `previouslyFocused?.focus()`）
- `packages/make-you-chic-ui/src/components/Modal/ModalStackContext.tsx`（`entries` の `useEffect` での `inert` の付け外し）
- `packages/make-you-chic-ui/src/components/Modal/Modal.tsx`（`useFocusTrap` の呼び出しと `open` で描画を返さない形）

## 期待する動き

- `Modal` を閉じた後、`inert` が外れてから、フォーカスが戻る。
- 戻す先は、次の順で決まる。
  1. アプリが指定した要素（例: `finalFocusRef` のような props。`Dropdown` の項目から開いたときに、開き口のボタンへ戻したい）
  2. 開く前にフォーカスのあった要素（文書の中にあり、フォーカスを受けられるとき）
  3. どちらも無ければ、今と同じ（何もしない）
- アプリが閉じた後に自分で `focus()` を呼ぶ場合も、`inert` が外れた後なら効く（閉じた時点で背景の `inert` がすでに外れている、または外れたことを知る手段がある）。
- 開いたときの初めのフォーカス、Tab の閉じ込め、`Modal` の重ね（いちばん上だけが操作できる）の振る舞いは変えない。

直し方（`inert` を外すのを閉じる処理の中で先に行う、フォーカスを戻すのを `inert` を外した後に回す、`finalFocusRef` を足す、など）は make-you-chic-ui の側でお決めください。

## 確かめていただきたいこと

- 実際のブラウザで、ボタンから開いた `Modal` を「キャンセル」・Escape・閉じるボタンで閉じたとき、開いたボタンにフォーカスが戻る。
- `Dropdown` の項目から開いた `Modal` を閉じたとき、指定した要素（開き口のボタンなど）にフォーカスが戻る。
- `Modal` を2つ重ねて、上の `Modal` を閉じたとき、フォーカスが下の `Modal` の中に戻る（背景には戻らない）。
- jsdom では `inert` を扱わないため、`inert` の外れる順を確かめるテストは、属性の付け外しの順か、実際のブラウザのテストで確かめる必要があるかもしれません。

## 使う側の状況（参考）

利用者の管理の画面では、表の行の「操作」（`Dropdown`）から確かめの表示（`Modal`）を開きます。閉じた後は、その行の「操作」のボタンへフォーカスを戻す設計です。今は実際のブラウザでは `body` に落ちるため、E2E では `inert` が外れるのを待ってから次の操作をしています。直った版を取り込んだ後に、閉じた後のフォーカスの戻り先を E2E で確かめる形に替える予定です。

## お願いしたい返し

- 反映したコミットのハッシュと、`main` へ push したこと
- 直し方の要点（props を足した場合は、その名前と使い方）
