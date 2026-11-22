<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-11-22T04:30:00Z — 質問は作らなかった。この単位はインフラ（compose・イメージ・配備・CI）を変えず、決めることが NFR Design までに決まっているため（`project.md` の「新しく決める論点が無いときは質問を作らない」）。3 つの文書は「変更が無い」ことと、既存の口が変わらない確かめだけを書いた。

## Deviations
- 2026-11-22T04:30:00Z — `infrastructure-specification.md` の Shared Infrastructure は、この単位が共有の資源を足さないため「なし」と書いた（U1 の共有を U3 が使う）。

## Tradeoffs
- 2026-11-22T04:30:00Z — `docs/verification.md` の書き足しは U1 のコードの変更を含めて U4 に任せた（U2 は文書を持たない）。

## Open questions
None.
