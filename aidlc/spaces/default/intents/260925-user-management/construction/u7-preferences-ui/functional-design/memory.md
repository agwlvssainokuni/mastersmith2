<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T00:42:21Z — 質問は3問（ユーザーメニューから画面へ移る手段・画面の検証の範囲・読んだ値と当たっている値の食い違い）に絞った; 画面の構成・見せ方・保存の後の動き・文言・部品は refined-mockups・U2・U4 で決まっているため、設計の要点（16 件）と「決まっていること」に書いた。
- 2026-09-27T00:42:21Z — U2 の持ち越し R-01〜R-03 は画面の変更を生まないと読んだ; R-02 は U2 が Deferred で回した AC4.1.11・AC4.1.12・AC5.1.7〜AC5.1.9 を U7 の traceability.json で受ける形で扱う。
- 2026-09-27T01:02:05Z — traceability の Deferred と N/A の分け方を依頼の文どおりにした; ほかの単位（U2・U4）で確かめる AC は Deferred、画面に当たる要素が無い CR6.5・CR6.7・CR6.8 だけを N/A にした。U5 はサーバーだけで確かめる AC を N/A にしており、単位の間で書き方が違う。
- 2026-09-27T01:02:05Z — AC4.1.4・AC4.1.8・AC4.1.10・AC5.1.3・AC5.1.6 は U4・U2 でも OK だが、画面の側の確かめがあるため U7 でも OK にした; 対象の文に、サーバーや土台の側の持ち主を併記した。
- 2026-09-27T01:02:05Z — 保存の成功の Toast は、applyUserPreferences の後の描画で出す印（pendingSavedNotice）の形にした; make-you-chic-ui の Toast は呼んだ時点の文字列を持つため、新しい言語で出すには言語が切り替わった描画の後に文言を引く必要がある（AC4.1.12）。
- 2026-09-27T01:02:05Z — 今のパスワードには規則（長さ・バイト数）を当てず、空だけを確かめることにした; 今のパスワードは規則ができる前のものでもよく、照合はサーバー（U2 の BR4.2）が行うため。Q2 A の「サーバーと同じ決まり」は U2 の BR4.1（currentPassword は有ること）と読んだ。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T01:02:05Z — 言語・テーマ・文字の大きさの選択を design-system-mapping.md の RadioGroup ではなく共用の RadioFieldset（U6 が作る）で書いた; make-you-chic-ui の RadioGroup は選択肢の名前を文字列でしか受けず lang を付けられず（CR6.6）、fieldset・legend も描かないことをコードで確かめた。U6 の担当からの知らせに合わせ、functional-spec.md の 10節の (b) に差として記録した。
- 2026-09-27T01:02:05Z — 骨組み（AppFrame、持ち主は U4）の型・登録の検査・ShellLayout を U7 が変える設計にした; Q1 A の答えどおりだが unit-of-work.md の U7 の境界の外のため、functional-spec.md の 9節と 10節の (a) に明記し、承認の場で確かめる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T01:02:05Z — 保存の送信中に画面を離れた後の成功の応答でも applyUserPreferences を呼ぶことにした; 内部DB は保存されたため画面の値をそろえる側を選んだ。代わりに、離れた先の画面で見た目や言語が急に変わることがある（10節の (h)、承認の場で確かめる）。
- 2026-09-27T01:02:05Z — 開いた時点のそろえ（Q3 A）で言語やテーマが変わっても知らせないことにした; 利用者の操作ではなく内部DB の値に合わせるだけのためだが、利用者には理由の分からない切り替わりに見えうる（10節の (i)）。
- 2026-09-27T01:02:05Z — 送信の間は主な操作のボタンに加えて入力と「元に戻す」も変えられなくした; 送信中の選択が成功の応答で上書きされて失われるのを防ぐ代わりに、送信の間は選び直せない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T00:42:21Z — VALIDATION_FAILED の項目ごとの誤りの形がまだ決まっていない; U2 は項目の名前と理由を付けると決めたが、契約 C4 に形が無く、今の GlobalExceptionHandler も返していない。U7 はその形に依存するため、U2 のコード生成で決まる形を U7 のコード生成の前に確かめる。
- 2026-09-27T00:42:21Z — make-you-chic-ui の Button は loading のあいだ disabled になり、フォーカスが外れうる; AC4.1.12（保存の後もフォーカスは保存のボタン）と CR6.3 を両立させるため、送信の後にフォーカスを戻す要点にした。実際に外れるかはコード生成で確かめる。
- 2026-09-27T00:42:21Z — パスワードの規則の画面の関数を U6 と共通の置き場（frontend/src/shared/ の下）に置く案にした; U6 の機能設計はまだ無いため、U6 の設計と置き場を突き合わせる。
- 2026-09-27T01:02:05Z — 共用の確かめの関数と RadioFieldset の名前・引数・理由の値は U6 の成果物で決まる; U7 の文書は理由の名前を仮に書いたため、U7 のコード生成で U6 の形に合わせる（functional-spec.md の 10節の (d)）。
- 2026-09-27T01:02:05Z — 見せ方の最中にトークンの更新の応答が来ると U4 が見せ方を捨て、フォームと画面の見た目が食い違う; 次の選択・元に戻す・保存で解けるためこの単位では追わず、コード生成で実際に起きるかを確かめる（10節の (f)）。
