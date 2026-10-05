<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T15:35:19Z — 枝番を、この Intent の要件の NFR の番号の意味で寄せた（分類と検査は NFR1、共有の木は NFR4、ESLint・テスト・依存は NFR6）; project.md の学びの NFR6・NFR9 は前の Intent の並びのため読み替えた。制約 C4・C5 は NFR の番号が無いため NFR6 に寄せ、成果物の冒頭に書いた。
- 2026-10-05T15:35:19Z — NFR2・NFR3・NFR5 は U1 に当たらないとして N/A にした; U1 は API・データ・監査の出来事・指標を持たない。要件 NFR6.4 の access.service は、U1 が手を入れないため部分として扱った。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T15:35:19Z — PUBLIC の口だけテストの側に一覧を持つ（Q1 B）; ADR-008 はすべての口の一覧を退けたが、これは PUBLIC だけの追加の守りとして補足の扱いにした。分類の正は口の隣の印のまま。コード生成の計画に ADR-008 の補足として書く。
- 2026-10-05T22:28:57Z — 承認の場の決定「Major 11 件だけを直す」で R-01 を直し、PUBLIC の一覧を口の単位（9 つ）で比べる形に決めた; 各行は宣言された方法の集合と道の型の集合で比べる。暗黙の HEAD・OPTIONS は足さず、server.error.path は解決した値、パス変数は宣言の形のまま比べる。解決した道を読むため、比べる場所を構造の検査から実行時の検査（*IT）に移した。
- 2026-10-05T15:35:19Z — library の単位のため、性能・規模・信頼性・観測の文書は作らなかった; 段の定義の produces_kinds のとおり。実際のブラウザの axe は U6・U7 の画面の検査に委ねた（NFR4.5）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T15:35:19Z — 全体の archUnit の設定 failOnEmptyShould=false は変えず、U1 の規則だけ allowEmptyShould(false) にした; 全体を変えると既存の規則が空で落ちうる。代わりに、新しい規則を書く人が規則ごとに付け忘れうるため、対象の口が 34 以上あることも確かめる。
- 2026-10-05T15:35:19Z — 検査の時間に目標の数値を置かなかった; 起動の文脈を使い回せば増える時間は小さい見込みのため。Build and Test で実測して記録し、目立って増えたらそのとき見直す。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T15:35:19Z — WebInvocationPrivilegeEvaluator が Spring Security 7.1.1 で、方法つきの決まりと AuthenticatedUserToken の主体を判定できるか; NFR 設計の捨ての試しで確かめる。だめなら MockMvc の形に決め直し、依頼者に差として伝える。
- 2026-10-05T15:35:19Z — 要件 NFR6.4 の access.service を一覧から外す作業を、どの単位が持つか; U1 は手を入れない。手を入れる単位（U4 など）の NFR 要件かコード生成の計画で確かめる必要がある。
