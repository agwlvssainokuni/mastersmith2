<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-11-22T13:40:00Z — 質問は作らなかった。この単位が持つインフラは、テスト用の使い捨ての PostgreSQL コンテナと CI の入口（手動の入力 `internal_db_postgres`）だけで、決めることが NFR Design までに決まっていたため（`project.md` の「新しく決める論点が無いときは質問を作らない」）。compose・配備の文書は U4 の持ち分とし、この単位では書かなかった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-22T13:40:00Z — 成果物の「Shared Infrastructure」を、この単位のテスト基盤（コンテナの部品・Gradle のタグとタスク・CI の入力）を u2・u3・u4 が使う形で書いた（単位の持ち主と使う側を明記するため）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-22T13:40:00Z — PostgreSQL の手順は `verify` の job の中の 1 手順にした（別の job にしない）。準備（WAR・Gradle のキャッシュ・枠 60 分）を使い回せる代わりに、手動の実行のときは検査の時間が延びる（通常の push の検査の時間には効かない）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-22T13:40:00Z — 手動の入力 `internal_db_postgres` の式（`inputs.internal_db_postgres == true`）が boolean の入力で成り立つことは、ci.yml を書くコード生成で、手動の実行 1 回で確かめる。