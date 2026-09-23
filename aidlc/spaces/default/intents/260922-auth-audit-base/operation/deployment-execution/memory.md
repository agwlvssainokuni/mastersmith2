<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T03:30:00Z — 配備の手順1の「未コミットの変更が無い」は、段の途中で常に変わるワークフローの記録（aidlc/）を除き、WAR とイメージに入るものについて判断した（確認済みの要約で依頼者の合意を得た）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T03:30:00Z — rollback-runbook.md 6節で予定していた戻しの練習を、依頼者の決定（Q3）で行わなかった。直前の版が新しいスキーマの DB で起動できることは未確認のまま deployment-log.md に残した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T03:30:00Z — スモークテストのログインは、パスワードを AI が知らないよう依頼者がブラウザで行い、AI は監査イベントとログで裏付けた。監査イベントは個人に関する値を表示せず、項目に値があるかだけを読んだ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T03:30:00Z — リポジトリの直下のバックアップ（mastersmith-data-*.tgz）は PC 上で -rw-r--r-- になる。README の複写のコマンドで権限を所有者だけにするかは、依頼者の判断に残した。
