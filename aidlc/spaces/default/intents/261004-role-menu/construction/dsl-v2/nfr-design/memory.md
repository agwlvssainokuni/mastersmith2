<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T23:03:38Z — library の単位のため性能・画面・テストの設計を logical-components.md、セキュリティ・ログ・信頼性を security-design.md に割り当てた; 段の定義の produces_kinds で performance・reliability・observability の文書を作らないため。割り当ての表を security-design.md の 2節に置いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T13:57:17Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01 を直した; 捨ての試し T4 で 10 MiB の読み込みと検証のヒープを測り（1件 256〜384 MB、2件同時は 512 MB で OutOfMemoryError・1 GB で通る）、既存の DslHeavyOperationGate で同時の投入は1つに絞られることと、適用の前の読み直しと RoleTransferSlot との重なりを受け入れた制約として security-design.md 4.9 に書いた。Build and Test で本番と同じヒープで測る引き継ぎを logical-components.md 4節に書いた。
- 2026-10-05T23:03:38Z — 機能設計 BR3.6 の appliedUnreadable の判定を、起動時に読めなかった版の ID との比べに変えた（Q1: A）; 識別を比べる形は適用の確定から提供口の差し替えまでの間に誤って真になるため。承認済みの機能設計は書き換えず security-design.md の 5節に差を書いた。
- 2026-10-05T23:03:38Z — 捨ての試しの T3 は本文を約 8.2 MB で比べた（10 MiB に届かない作り）; 版 1 と版 2 の比べには足りると判断し、10 MiB の実測は Build and Test に任せた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T23:03:38Z — 深さと別名の上限を LoaderOptions と LimitingParser の両方に同じ値で渡す形にした; 試しの T1' で LoaderOptions の方が小さいと部品の例外（位置なし）が先に出て SYNTAX に写るため。値を2か所に渡す手間の代わりに、区分と位置を必ず保てる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T23:03:38Z — 別名の爆発のテストの時間の上限 5 秒は、手元の実測 0.2 秒に余裕を持たせた値; 負荷の高い CI で落ちないかは Build and Test と CI で確かめる。
