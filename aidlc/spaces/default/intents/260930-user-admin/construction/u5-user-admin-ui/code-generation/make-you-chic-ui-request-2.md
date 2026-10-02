# make-you-chic-ui への依頼（2回目）— Dropdown の押せない項目の見た目と読み上げ

Intent 260930-user-admin の B5（U5 利用者の管理の画面）のコード生成で、make-you-chic-ui の固定先を `3481488`（Dropdown: 項目に disabled/description を追加）に上げて確かめたところ、次の2点が見つかった（2026-10-03）。依頼者の決定で、make-you-chic-ui の側で直してもらい、直った版を固定先として取り込む。このリポジトリから `vendor/make-you-chic-ui` の中身は変えない（`project.md` の Forbidden）。

## 依頼1（N-4）: light テーマで、押せない項目のホバー・フォーカスの文字のコントラストが足りない

- 場所: `packages/make-you-chic-ui/src/components/Dropdown/Dropdown.css`
  - `.mycui-dropdown-item:hover`・`.mycui-dropdown-item:focus-visible` の背景は `var(--color-surface-hover)`
  - `.mycui-dropdown-item[aria-disabled='true']` と `.mycui-dropdown-item-description` の文字は `var(--color-text-muted)`
- 起きること: 押せない項目（と理由の文）にマウスを載せたとき・キーボードで移ったとき、light テーマで文字と背景のコントラストが **4.39:1** になり、WCAG 2.1 AA の 4.5:1 を下回る（WCAG の式で計算。通常の背景の上では light 4.83:1・dark 9.96:1、ホバー・フォーカスの背景の上では dark 8.87:1 で足りている）。
- 期待: light・dark のどちらでも、押せない項目の文字と理由の文が、ホバー・フォーカスの背景の上で 4.5:1 以上になること。直し方（押せない項目にはホバーの背景を付けない、文字の色を変える、など）は make-you-chic-ui の側で決めてよい。押せない項目を矢印キーのフォーカスの対象に残す振る舞い（R3）は変えない。
- 確かめ: このリポジトリの E2E 120（全 20 組、状態 (11) 押せない項目のホバー・フォーカス）の axe の color-contrast。

## 依頼2（N-3）: 理由の文が項目の名前にも入り、2回読まれる

- 場所: `packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`（`mycui-dropdown-item-description` の `<span>` が項目の要素の中にある）
- 起きること: 理由の文の要素が項目の中にあるため、読み上げの名前（accessible name）が「項目名＋理由の文」になり、`aria-describedby` の説明と合わせて理由が2回読まれる。
- 期待: 項目の名前は項目名だけで、理由の文は `aria-describedby` の説明としてだけ読まれること（例: 項目に `aria-labelledby` で項目名の要素だけを結ぶ、など。直し方は make-you-chic-ui の側で決めてよい）。見た目（項目名の下に理由を出す）は変えない。
- このリポジトリの側の影響: 名前が項目名だけになれば、U5 のテストと E2E で項目を名前の完全一致で探せる。

## 取り込みの段取り

1. 依頼者が make-you-chic-ui の側で直し、公開の側（`origin/main`）に入れる。
2. このリポジトリの B5 で、固定先を直った版に上げ、専用のコミット C2 に前後のハッシュ（`077f5b48ce84cd020ecec2d925836a085f9d9e11` → 直った版）を書く。push の前に、取り込むコミットが公開の側にあることを確かめる。
3. 直るまでの間は、作業フォルダの固定先を `3481488` のままにして画面の生成を進める（部品の口は変わらない見込み）。C2 は作らない。
