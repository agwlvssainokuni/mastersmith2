# make-you-chic-ui への依頼（4）: Dropdown の項目の名前と、見える文字の一致（WCAG 2.5.3）

依頼元: mastersmith2（Intent 261015-deps-ui-carryover「第1の束と第2の束」の持ち越し）
対象の版: make-you-chic-ui `a9309c4`（mastersmith2 の今の固定先。2026-10-16 の時点で上流の origin の先頭も `a9309c4`）
対象の部品: `Dropdown`（`packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx`）

## 1. 背景

mastersmith2 は、依存の更新で axe-core を 4.13.0 から 4.14.0 に上げようとしました。4.14.0 では、規則 `label-content-name-mismatch`（WCAG 2.5.3 Label in Name: 見えている文字が、読み上げの名前に含まれること）から `experimental` の印が外れ、既定で流れるようになります。そのため mastersmith2 の実際のブラウザの axe（Playwright の E2E）で、次の2種類の違反が出ました（E2E の 5 ファイルで計 100 件）。

- (a) Dropdown のトリガー（`data-testid="dropdown-trigger"`）。利用者・ロール・グループの一覧の行の「操作」のボタンなど。
- (b) Dropdown の項目（`button[aria-labelledby="…-item-0-label"]`）。mastersmith2 のトップバーの「作業ロール」の切り替えの一覧。

調べた結果、(a) は mastersmith2 の側の名前の付け方が原因のため、mastersmith2 で直します（2節）。(b) は Dropdown の項目の作りによるため、make-you-chic-ui の側への依頼です（3節）。mastersmith2 は axe-core 4.14.0 を見送っており（`.github/dependabot.yml` の `ignore`）、両方を直してから上げます。

mastersmith2 は `vendor/make-you-chic-ui` を直接変えない決まりです。

## 2. mastersmith2 の側で直すもの（参考。依頼ではない）

- 行の「操作」のトリガーは、mastersmith2 が `<Button aria-label={行の名前}>操作</Button>` のように、見える文字（「操作」）を含まない `aria-label` を渡している。名前に見える文字を含める形（例: 「操作: 行の名前」、または見える文字のままにして行の名前は `aria-describedby` で添える）に直す。
- 作業ロールの切り替えのトリガーは、`aria-label` が「作業ロール: 〇〇」で、見える文字（「作業ロール:」と名前）を含むため、違反にならない見込み。次の Intent の E2E で確かめる。

## 3. 依頼する機能: 説明のある項目の名前を、見える文字と一致させる

### 3.1 今の作り（`a9309c4` の時点。行番号はそのコミットのファイル）

- `Dropdown.tsx` 171〜184 行: 項目の中身は、`item.label` の `span`（`id={labelId}`）と、`item.description` があるときの `span`（`id={descriptionId}`）の2つ。
- `Dropdown.tsx` 186〜228 行: 項目の要素（`a` か `button`、`role="menuitem"`）に `aria-labelledby={labelId}`・`aria-describedby={descriptionId}` を付けている。
- 起きること: 説明のある項目では、見えている文字は「名前＋説明」だが、読み上げの名前は `aria-labelledby` により「名前」だけになる。axe-core 4.14 の `label-content-name-mismatch` は、見えている文字（要素の中の文字の全体）が名前に含まれないとして違反にする。
- mastersmith2 で当たる場面: 作業ロールの切り替えで、ロールが1つだけのとき（説明「割り当てられたロールは〇〇だけのため、切り替える先はありません」）と、ロールが無いとき（説明「割り当てられたロールがありません。管理者に依頼してください。」）の、押せない項目。

### 3.2 してほしいこと

- 説明のある項目でも、axe-core 4.14 以降の `label-content-name-mismatch` で違反にならないようにしてほしい。読み上げでは、名前は項目の名前、説明は説明として伝わる今の振る舞いは保ってほしい。
- 案（make-you-chic-ui の方針に合わせてよい）:
  - 案 A: 説明の `span` を、名前の計算に入らない形で項目の外側の構造に置く（例: 項目の要素の中には名前だけを置き、説明は `aria-describedby` で結ぶ兄弟の要素として描く）。見た目は今と同じにする。
  - 案 B: 項目の `aria-labelledby` をやめ、中身の文字から名前を計算させたうえで、説明を `aria-describedby` でも結ぶ（名前に説明も含まれることになるため、読み上げが重複しないかを確かめる）。
  - 案 C: 説明を見える文字の外（ツールチップなど）に移すかを選べる口を設ける。
- 説明の無い項目は、今の振る舞い（`aria-labelledby` で名前を結ぶ）のままで構わない。

### 3.3 確かめ方（完了の目安）

- make-you-chic-ui のテストで、説明のある項目と無い項目の両方について、axe-core 4.14 以降で `label-content-name-mismatch` の違反が出ないこと。
- 説明のある項目の読み上げの名前が項目の名前で、説明が説明（`aria-describedby`）として結ばれていること。
- 押せない項目（`aria-disabled`）に説明が付く形でも、上の2つが成り立つこと（mastersmith2 の作業ロールの切り替えで使う）。

## 4. 補足

- 2節の mastersmith2 の側の直しと、この依頼の取り込み（固定先の更新の専用のコミット）は、mastersmith2 の次の Intent で行う予定です。取り込みの後に axe-core を 4.14 以降に上げ、mastersmith2 の E2E で違反が無いことを確かめます。
- 上の (a)(b) の分け方は、mastersmith2 の `frontend/src` の Dropdown の使い方と、`a9309c4` の `Dropdown.tsx` を読んで判断しました。axe-core 4.14 で違反が出た E2E のうち、表示の設定の画面のもの（「選択の誤りの状態」）は、Dropdown ではなく別の部品の可能性があり、mastersmith2 の側でまだ確かめていません。make-you-chic-ui の部品に当たると分かったときは追って知らせます。

## 5. 対応の報告（2026-10-16 受領）

- make-you-chic-ui から、3節の依頼への対応が完了したと報告を受けた。上流の `main` に `b0a1e79`「Dropdown: 説明付き項目でlabel-content-name-mismatch(WCAG 2.5.3)に 違反しないようDOM構造を変更」が push 済み（`a9309c4..b0a1e79`）。
- 中身（報告より）: 説明を menuitem の子孫ではなく兄弟の要素として描き、`aria-labelledby`（名前）と `aria-describedby`（説明）の振る舞いは保った。実際のブラウザで axe-core 4.14.0 を読み込み、ブランドカラー4種 × light・dark の8組で違反が無いこと、直す前の構造では違反が出ることを確かめた。公開の口と既存の使い方への影響は無い。
- mastersmith2 の側で確かめたこと（読み取りだけ）: `b0a1e79` で変わったのは `Dropdown.tsx`・`Dropdown.css`・`Dropdown.test.tsx` の3つで、package の版の定義と lockfile は変わらない。アプリと E2E は Dropdown の内部のクラス（`mycui-dropdown-*`）を参照していない。固定先は `a9309c4` のまま。
- 次の Intent で、固定先の更新（専用のコミット）、2節の行の「操作」のトリガーの直し、axe-core 4.14 以降の取り込みと E2E での確かめを行う。
