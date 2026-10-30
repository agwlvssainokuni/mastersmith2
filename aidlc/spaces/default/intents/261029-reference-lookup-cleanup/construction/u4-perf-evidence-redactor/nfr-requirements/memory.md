<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-10-30T00:00:00Z — 伏せ字・入口・検査の要件を上流の NFR8（運用）に、仕組みのテストを NFR5 に、版と依存を NFR9 に、C7 の互換を NFR7 に、終わりのコードの固定を NFR6（code を変えない）に寄せた。NFR1〜NFR4 は業務データの API・DB・画面の要件のため N/A。
- 2026-10-30T00:00:00Z — 入力の上限 64 MiB を evidence の検査にも当て、上限を超えるファイルは黙って飛ばさず失敗にした（飛ばすと穴になるため）。機能設計に無い扱いで、security-requirements.md 4節に書いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-30T00:00:00Z — 機能設計の段の承認で依頼者が「直してほしい」とした点に従い、承認済みの functional-spec.md の 6節 3項と 10節、rules.md の BR6.2（文言と要約の行）に、標準出力・ログの JWT の形でない秘密の穴と台本の決まりを書き足した。
- 2026-10-30T01:00:00Z — NFR 要件の承認の場の Request Changes（依頼者の決定: 指摘の示した直しを採る）で、NFR5.62・NFR5.63 にテストの置き場と vitest.config.ts の include・coverage.include の扱いをコード生成の計画で明記することと Build and Test での passed の件数の記録を、NFR8.68 に NUL で飛ばしたファイルのパスの表示とテキストの拡張子（.json・.md・.txt・.log・.tsv）なら失敗にすることを足した。後者は機能設計の BR4.2 を狭める変更で、機能設計の文書は書き換えていない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-30T00:00:00Z — vitest の coverage の include は src/ だけで、perf/ の道具のファイルは今の形では計測されない。下限と除外は下げず、入れ方は ADR-006 の切り替え先とあわせてコード生成で決める（NFR5.63）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
