<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T04:30:00Z — 要件の「同時 10 件を繰り返し」を、考える時間なしの閉じた負荷（仮想の利用者 10）と読んだ。最も厳しい読み方で、F3（メモリの上限で止まる）はこの重さでだけ起きた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T04:30:00Z — 1回目で refresh の負荷でアプリが止まり adminCheck・forbidden を測れなかったため、使い捨ての環境を起動し直して 2 回目を行った。ログインの最初の同時 10 件の遅れと監査の失敗（F2）も、2 回目で再現させてから記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T04:30:00Z — 測れない目標は、応答時間の全体を上限として判定した（Q4）。全体が目標より桁違いに小さかったため判定に迷いはなかったが、全体が目標に近い場合は差を測る仕組み（計器）が要る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T04:30:00Z — F1（ヘルスチェックの誤った DOWN）・F2（同時ログインでの接続の待ち合いと監査の欠落）・F3（メモリの上限）・F4（CPU 2 でのログインの遅さ）の直し方と時期は依頼者の判断待ち。F2 は監査ログの欠落を伴い、最も重い。
