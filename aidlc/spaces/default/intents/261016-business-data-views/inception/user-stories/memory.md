<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-18T02:30:00Z — 要件の承認の場で受け入れた指摘 R-09（更新の画面のカラム）と R-10（接続の失敗の監査）を、この段の質問 Q7・Q6 で決めた。mob の3人が挙げた判断の候補（約 26）のうち、専門の知識で決まるもの（状態コードのそろえ方・判定の順・NULL の並び・ページの範囲・ストーリーの分け方・既知の制約）はリードが決め、利用者の体験や要件との差に当たる8点（M1〜M8）だけを依頼者に尋ねた。M の答えで中身が変わったため、まとめの確認をやり直した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-18T02:30:00Z — 統合をプロダクトマネージャーの担当に任せたところ、traceability.json の書き込みが記録されず、レビューの依頼が拒まれた。指揮役の Edit で触り直して依頼を通した。担当に成果物を書かせた後は、レビューの依頼の前に指揮役の Edit で各成果物を触り直す。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-18T02:30:00Z — 主キーとビューの決まりによる拒否を 403 ではなく 409 と専用の code にした。要件の「403 は監査に残さない」と「業務の理由の拒否は残す」を両立させるためで、権限の不足と操作できないテーブルを応答で見分けられる代わりに、要件の FR9 の文言との差を「要件との差」に記録した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-18T02:30:00Z — M2（空の入力）は作成を名指ししておらず、作成で空にした FULL のカラムは FR6.4 のとおり DB の既定値に任せると読んだ（X9）。password の応答に値を含めない読み（X10）とあわせて承認の場で確かめる。秒未満の日時の書式は後の段で決める。
