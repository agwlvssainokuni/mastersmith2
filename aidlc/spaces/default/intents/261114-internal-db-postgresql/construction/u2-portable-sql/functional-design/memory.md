<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-11-22T02:20:00Z — U1 のコード生成の質問 Q1: A で `@Lob` の 2 列を U1 が直したため、U2 の責務のうち FR3.6 の写し方は済み。U2 は H2 での確かめ（BR4.1）だけを持つ形に縮めた。
- 2026-11-22T02:20:00Z — 捨ての試しは調査担当に頼み、2 回に分けた（1 回目: 候補の文と同時の実行、2 回目: セーブポイントでの収束）。2 回目は、標準の MERGE が H2 の後勝ちを変えると分かった後で、更新 → 挿入の収束を同じトランザクションの中でできるかを確かめるために足した。

## Deviations
- 2026-11-22T02:20:00Z — `unit-of-work.md` の U2 は MERGE を標準の MERGE ... USING に直すとしていたが、同時の実行で H2 の結果が変わるため、更新 → 挿入（セーブポイント）の手順にした（質問 Q2: A）。`functional-spec.md` の「上流との差」に書いた。

## Tradeoffs
- 2026-11-22T02:20:00Z — savePreferences は JDBC の生成キー（1 文のまま）を選び、更新 → SELECT の 2 文（単純）を採らなかった。前の Intent の NFR2.1.2（文の数を変えない）を守る代わりに、`Session.doReturningWork` と小文字の列名への依存が増える。
- 2026-11-22T02:20:00Z — 更新 → 挿入（セーブポイント）は複雑な代わりに、H2 の後勝ちを両方の DB で守れる。標準の MERGE は単純だが、後の側が 23505 で失敗し H2 の結果が変わる。

## Open questions
- 2026-11-22T02:20:00Z — H2 の未確定の挿入への待ちが約 4 秒になる理由は観測だけで調べていない。H2 を上げるときに `WorkRoleSelectionMergeIT` の保持時間の期待を見直す。
