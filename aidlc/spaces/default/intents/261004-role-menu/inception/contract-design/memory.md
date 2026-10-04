<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T13:50:00Z — 誤りの形・応答の形・結果の型・ログの伏せ字の型・パスの形は前の Intent と team.md で決まっているとして質問にせず、import の結びつけ方・解決の口の形・作業ロールの渡し方の3問にした。書く前に ActiveDslModelProvider・DslModel・AuditEventType・SecurityRuleContributor をコードで確かめた（前の Intent の学び）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T13:50:00Z — 作業ロールは専用の GET /api/me/work-role で渡し（CQ3: B）、既存の「今の利用者」の応答（auth の CurrentUserResponse・ログインの応答）は変えない。auth が role を知らずに済む代わりに、画面はログインの後に1回多く読む。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T13:50:00Z — import は指紋で照合し確かめの結果を保存しない（CQ1: A）。保存の表と片付けが要らない代わりに、適用で同じ YAML をもう一度送る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-04T13:50:00Z — code の最終の名前（既存の USER_NOT_FOUND などとの重なり）、版 2 の JSON Schema の形、指紋の並べ方は機能設計で確かめる。
