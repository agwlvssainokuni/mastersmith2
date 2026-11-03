<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-03T00:00:00Z — U1（library）の NFR Design は、produces のうち security-design・logical-components・traceability だけを作り、性能の設計は logical-components の LC7 に置いた（種類 library は performance-design 等を作らない）。
- 2026-11-03T00:00:00Z — 捨ての試しを文書より先に流し（Node 26 と Chromium 156）、前提が崩れた3点（時差の求め方・年の範囲外・他ブラウザ）を質問 Q1〜Q3 にした（project.md の学びどおり）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T13:50:00Z [unit u5-menu-icons-home] — 機能設計 D5（DSL にテーブルが見つからないとき種類は `null`）は、メニューの木と種類の操作が同じモデルから作られるため、サービスの経路では起こらない防御の決まりと分かった。NFR 要件のレビュー R-01 の「`null` に届く状況を作れるテスト」は、`MenuFilterTest`（`null` を返す操作）と `NavigationDslSwapIT`（木と種類がずれない）に置いた。機能設計は書き換えず、logical-components.md の6節に記録した。
- 2026-11-03T13:50:00Z [unit u5-menu-icons-home] — NFR 要件の数字（追加分 22・18 バイト）は実際の 20・19・17 バイトと違うため、performance-design.md の 1.3 を正とした。承認済みの NFR 要件は書き換えず、差を記録した。
- 2026-11-03T00:00:00Z — 承認済みの tech-stack-decisions.md の「時差は longOffset」との差（時差は壁時計の差から求める。Q1: A）と、表示の年が範囲外のときの unreadable（BR1.5 の拡張。Q2: A）、Firefox・Safari の未確認（Q3: A）を、logical-components.md の「承認済みの記録との差」D1〜D3 に記録した（承認済みの文書は書き換えていない）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T00:00:00Z — 時差を longOffset の文字でなく壁時計の差で求めると、parse の1回の入力で壁時計の計算が最大4回になる（LC7.3）。代わりに求める道が1つになり、古い Safari でも動く。一覧の1万の値の側は1回の呼び出しで済み、目標に約8倍の余裕がある。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-03T13:50:00Z [unit u5-menu-icons-home] — 追跡の道具が、NFR 要件の文書が前の Intent の番号として引いた NFR2.3・NFR2.5 を上流の ID に数えた（N/A にして理由を書いた）。次の単位以降、前の Intent の番号を引くときは「前の Intent の」と書いても道具には区別されないため、文書の中では引かないほうがよい。
- 2026-11-03T00:00:00Z — Firefox・Safari での Intl の値は未確認（Unverified）。持ち主の段は Deployment Execution のスモークテスト。
