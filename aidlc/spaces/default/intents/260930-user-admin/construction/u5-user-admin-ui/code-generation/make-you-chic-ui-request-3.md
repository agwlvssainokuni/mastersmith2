# make-you-chic-ui への依頼（3回目）— Modal を閉じた後にフォーカスが元の要素へ戻らない

Intent 260930-user-admin の B5（U5 利用者の管理の画面）のコード生成で、make-you-chic-ui の固定先 `3d9521a` を使った E2E（実際のブラウザ）を流したところ、次の1点が見つかった（2026-10-03、generation-notes.md の N-19）。依頼者の決定で、make-you-chic-ui の側で直してもらい、直った版を固定先として取り込む。このリポジトリから `vendor/make-you-chic-ui` の中身は変えない（`project.md` の Forbidden）。

## 依頼（N-19）: Modal を閉じた後、フォーカスが開く前の要素ではなく body に移る

- 場所:
  - `packages/make-you-chic-ui/src/utils/useFocusTrap.ts`（閉じるときの後始末で `previouslyFocused?.focus()` を呼ぶ）
  - `packages/make-you-chic-ui/src/components/Modal/ModalStackContext.tsx`（いちばん上の Modal 以外の `<body>` の子に `inert` を付け外しする）
- 起きること: Modal を開いた要素（例: 表の行の「操作」のメニューの項目から開いた確かめの表示）から Modal を開き、「やめる」などで閉じると、フォーカスが開く前の要素に戻らず `body` に移る。`useFocusTrap` の後始末が `previouslyFocused.focus()` を呼ぶ時点では、まだ背景の要素に `inert` が付いたままのため、フォーカスを受けられないと見ている（このリポジトリの側の見立て。make-you-chic-ui の側で確かめてほしい）。
- 確かめた環境: Playwright（Chromium）。jsdom は `inert` を扱わないため、単体のテスト（Vitest＋jsdom）では起きない。
- 期待: Modal を閉じた後、`inert` が外れてから、開く前にフォーカスのあった要素（まだ文書の中にあり、フォーカスを受けられるとき）へフォーカスが戻ること（WCAG 2.4.3 フォーカス順序）。直し方（`inert` を外した後に戻す、戻す処理を後へ回す、など）は make-you-chic-ui の側で決めてよい。開いたときの初めのフォーカス、Tab の閉じ込め、Modal の重ね（いちばん上だけが操作できる）の振る舞いは変えない。
- 影響の見込み: Modal を使うこのリポジトリの既存の画面（招待の画面など）でも同じことが起きている見込み（確かめていない）。
- このリポジトリの側の確かめ: E2E 110 と 120 で、表示を閉じた後に行の「操作」へフォーカスが戻ること。今は、表示を閉じた後に `inert` が外れるのを待ってから次の操作をする形にしている。

## 取り込みの段取り

1. 依頼者が make-you-chic-ui の側で直し、公開の側（`origin/main`）に入れる。
2. このリポジトリの B5 で、固定先を直った版に上げる。C2（364e9d6、`077f5b4` → `3d9521a`）とは別の専用のコミット（C2′）にし、前後のハッシュ（`3d9521aa54b1d6277de473f9e935a496fb56ac1b` → 直った版）を書く。push の前に、取り込むコミットが公開の側にあることを確かめる。
3. 直るまでの間も、画面の生成の残り（静的検査・レビュー・記録）は進める。
