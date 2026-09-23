<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T04:46:54Z — 最初のコード知識ベースのため全体を対象に調べたが、深く読んだのは F2 に関わる範囲（ログイン・ログアウト・監査・接続の設定・関係するテスト）だけなので、記録上の範囲は partial とした。画面側や共通部品は流し読みの扱い。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T04:46:54Z — 前の Intent では、監査を別スレッドに移すとトレースIDと「確定の後に記録」の決まりが変わるため、接続を2本使う形を受け入れていた。直し方を決めるときは、この決まりと既存の結合テスト（AuditAuthenticationEventsIT・AuditTraceIdIT・AuditRollbackIT・AuditWriteFailureIT）を守れるかで比べる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T04:38:06Z — 依頼者から「内部DBのプールサイズを増やす」案も試したいとの要望。直し方の候補の1つとして Requirements Analysis に引き継ぎ、現在のプールの設定（上限・待ちの上限・設定の置き場所）を調査結果で確かめる。
