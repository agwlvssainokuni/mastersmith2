<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T03:30:00Z — 依頼者は Full rescan を選んだが、深さ Minimal のため開発担当が深く読んだのは4件に関わる約30ファイルだけだった。記録上の範囲は kind: partial とし、./ を analyzed.paths に入れない（前回・前々回と同じ扱い）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T03:50:00Z — アーキテクトへの依頼で、記録するコミットにスナップショットの source の識別（git:cb55a97…、コミットではない値）を渡してしまい、timestamp の表にそのまま書かれた。公開の前に develop の HEAD（4970f3b）に直した。記録するコミットは git rev-parse HEAD で取り、スナップショットの値と混ぜない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T03:30:00Z — 開発担当は原因の見立て（H2 が空いた場所を再利用しない理由、メモリの内訳、AccessTokenApiIT の原因）をすべて未検証の仮説として書いた。確かめた事実と仮説を分けることで、要件定義で測り方と切り分けを決める材料にする。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-25T03:30:00Z — 保存の形を変える直しは、スキーマの前進のみ・1つ前の版が動く後方互換・前の版のイメージへの戻しと衝突しうる（前の版は yaml_bytes の列を読む）。要件定義で扱う。
