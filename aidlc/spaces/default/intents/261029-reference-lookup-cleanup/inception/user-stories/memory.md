<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-29T15:10:00Z — 判断が分かれた3点（M1 必須の参照項目の行き止まり、M2 JWT の形以外の認証の値、M3 lgtm の状態）だけを依頼者に尋ね、専門の知識で決まる点はリードが決めて取り込んだ。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-29T15:10:00Z — 決定の要約の道具（review-brief summary）が今回も動かず、答えのまとめだけで確認した。統合はプロダクト担当に任せ、成果物は指揮役の Edit で触り直してからレビューに回した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-29T15:10:00Z — 必須の参照項目で読めないときの作成は、行き止まりを許容して 409 で拒否した（M1: A）。文字入力に切り替える案は、権限を欠く人が参照先の存在を試せるため採らなかった。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-29T15:10:00Z — レビューの Major 1件（AC1.1.5 の書きかけの文）と Minor 7件は承認の場へ持ち越した（advisory）。
