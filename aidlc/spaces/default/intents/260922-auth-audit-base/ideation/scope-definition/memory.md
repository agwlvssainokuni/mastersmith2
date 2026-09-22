<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T03:52:30Z — グリーンフィールドのため、本Intentが最初のアプリ骨格（ビルド・内部DB・起動可能なアプリ）も兼ねると解釈し、MVP範囲の質問（Q1）に含めた
- 2026-09-22T03:52:30Z — ユーザー登録（Intent G）が後続のため、ログイン検証用の初期管理者の作成方法がスコープ上の空白になると判断し、Q2として独立して質問した
- 2026-09-22T04:00:49Z — Q4とQ3の回答に、「監査ログ基盤」なのに共通の仕組みが未選択・「ログアウトあり／失効なし」という緊張関係を検出し、追加質問Q9・Q10で解消した

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-22T04:00:49Z — 監査記録の共通化を後続Intentに回した（Q9）。本Intentの範囲は小さくなるが、業務CRUDを扱う後続Intentで共通化の検討が必要になる
- 2026-09-22T04:00:49Z — ログアウトは画面側破棄＋リフレッシュトークンのサーバー側無効化とし、アクセストークン失効は含めない（Q10）。仕組みは簡素になるが、アクセストークンの有効期限を短くする必要がある

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
