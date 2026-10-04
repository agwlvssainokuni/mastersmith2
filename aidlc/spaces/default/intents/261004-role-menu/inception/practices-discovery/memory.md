<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T06:10:00Z — 再実行のため、リードの候補 P1〜P9 と支援役3名の追加（品質 Q-1〜Q-7、開発 D1〜D3、セキュリティ G1〜G5）のうち、チームの進め方に当たるものだけを 12 問にまとめた。役割の持ち方・権限の粒度・権限を DB とトークンのどちらで持つか・メニューの出どころ・深さの上限の値・監査の種類の名前は要件・設計の段に回した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T06:50:00Z — 反映の道具（practices-promote）が discovered-rules.md の Forbidden の「None.」を固い制約の1行（None. (affirmed 2026-10-04)）として project.md の Forbidden に足したため、依頼者の決定でその1行だけを消してコミットした。
- 2026-10-04T06:10:00Z — Q11（vendor の npm ci は今のまま）が team.md の「npm でパッケージを入れるときはスクリプトを動かさない」と食い違って読めるため追加の質問 F1 で確かめ、team.md の文言は変えず、vendor/make-you-chic-ui の vendorInstall（npm ci）は対象外で --ignore-scripts を付けないことを project.md の学びとして残す形（B）になった。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T06:10:00Z — P2（管理者フラグの文言）は、リードの「Build and Test で読み替える」案に開発担当とセキュリティ担当が「コード生成の Testing Contract に間に合わない」と異論を出し、依頼者はこの段で一般化する形（A）を選んだ。具体の形は要件で決まった後に足す。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-04T06:10:00Z — 手元の origin/HEAD は origin/main を指すが team.md は GitHub の既定のブランチを develop とする（GitHub の設定は未確認）。Dependabot の知らせのブランチが6つ残り、重大度 High 以上の取り込みが要るかを Construction の前に確かめる。
