<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-15T00:00:00Z — User Stories を飛ばしたため、要件の FR の枝番（29件）で対応表と traceability.json を作った。テストだけの単位 U3 は当てはまる種類が無いため種類を付けなかった（段の指示どおり、設計の段は全部の組になる）。
- 2026-11-15T00:00:00Z — 依頼者が Q1・Q2 で案（種類・部品・FR を含む表）をそのまま選んだため、Step 4 の計画の承認を別に尋ねず、その答えを計画の承認として扱った（同じことを二度確かめない）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-15T00:00:00Z — 段の質問の例（独立した単位の扱い・配備の形）は、team.md（worktree を使わない・1つの WAR）で決まっているため質問にせず「決まっていること」に書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-15T00:00:00Z — U2 の大きなバイナリの写し方と U1 の移行の列の型はつなぎ目になるが、U2 は H2 だけで確かめられるため依存の辺を置かなかった。一致は U1 の ddl-auto: validate と U3 の 10MB の往復で拾い、Contract Design でつなぎ目として書く。

- 2026-11-15T00:00:00Z — レビューの R-03・R-04 で、大きなバイナリの列の型と写し方の組の持ち主を U1、AuthPersistence・InvitationPersistence の見込みが外れたときの直しの持ち主を U2 に決めた。U3 をテストだけに保ち、依存の図を変えずに済む形を選んだ（代わりに U1 の機能設計を U2 の機能設計より前に置く設計の順の制約が増える）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-15T00:00:00Z — （解決: レビューの R-04 で U2 が持つと決めた）AuthPersistence・InvitationPersistence の見込みが外れて本体の直しが要るとき、U2 と U3 のどちらで直すか。
