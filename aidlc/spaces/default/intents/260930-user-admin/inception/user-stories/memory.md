<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-30T18:30:00Z — mob の3人の意見のうち、専門の知識で決まるもの（事実の食い違いの直し・境界値・同時の重なりの合否・監査の書き込みの失敗は既存どおり・重なって負けた側の理由・元の管理者を P2 に含める・自分自身の失敗回数の取り消しを許す）はリードが決め、利用者の体験や要件の差に当たる9点（M1〜M9）だけを依頼者に尋ねた。M の答えで答えの中身が変わったため、まとめの確認をやり直した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-30T18:30:00Z — M1 A（停止を解いた後の止める前のアクセストークン）・M5 B（403 の表示を管理の画面すべてに広げる）・M7 B（拒否の理由に「対象の利用者が停止中」を足す）は承認済みの要件と違うため、要件は書き換えず、ストーリーの「要件との差」に記録する。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-30T18:30:00Z — M8 B で、止める操作とトークンの更新が重なったときに残るリフレッシュトークンの隙を塞がずに記録することにした。作りは簡単になるが、停止を解いた後に重なりで作られたトークンが使える可能性が残る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
