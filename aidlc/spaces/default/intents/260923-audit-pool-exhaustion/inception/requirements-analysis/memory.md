<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T05:12:13Z — Q3 で「C（試して記録＋環境変数、既定値10のまま）」、Q1 で「プールを大きくするだけ」と、両立しない回答があった。追加の質問 FQ1〜FQ3 で「既定値30を採用し、環境変数でも変えられるようにし、事前の試しも記録する」に確定した。Q2 の「プログラム修正は不要」は、範囲を設定の変更に限る意味と読んだ。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T05:12:13Z — 環境変数の名前 MASTERSMITH_DB_MAXIMUM_POOL_SIZE は、質問で決めずに既存の MASTERSMITH_DB_* にそろえて提案とし、要件の前提（assumption）に明記した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T05:12:13Z — 依頼者の判断で、2本使いの解消（接続を返してから記録する・監査専用のプール）ではなく、プールの上限の引き上げを選んだ。コードと既存の監査の決まりは変わらない。その代わりに、同時の数が上限（30）に達すると再び起きうるという危険が残り、要件（FR5）と README に既知の制約として記録する。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
