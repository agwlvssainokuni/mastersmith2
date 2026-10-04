<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T03:23:16Z — 計画の担当が挙げた D1〜D6 を計画の承認の前に依頼者に尋ねて反映した。D5（統合の前に E2E を流さない）が team.md の「画面・認証に関わる変更は統合の前に E2E」と食い違いうるため D7 で確かめ、画面に触れず E2E の初期管理者では救済が働かないため当たらないと読む形（B）になった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T03:23:16Z — 生成を2回（Step 1〜9・9〜18）に分けて依頼し、途中で2回止まった。G1: 計画の影響の範囲に無い既存のテスト2件（SecretTypesTest・AuthSuspensionSecretLeakIT）が落ち、直す形（A）。G2: FilterExceptionErrorLogIT が verify 全体の中では Tomcat のエンジン名が番号付き（Tomcat-32）になり落ち、テストだけを実際の名前に合わせる形（A）。レビューで、G2 の後のテストは直しの有無を見分けず（設定の鍵の有無だけ）、本番のエンジン名で設定が効くことはどのテストも確かめていないと指摘された（R-01）。README の C1 と C5 は同じファイルのため C1 にまとめ、コミットは4つにした。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T03:23:16Z — Tomcat のロガー（dispatcherServlet）を OFF にする1行の直し（D3: A）を選んだ。設定だけで済む代わりに、応答を書き始めた後の例外や /error に回らない経路の例外のログも残らなくなる（レビュー R-02）。ログのロガーの名前に依る設定は、同じ JVM で複数の Tomcat を起動するテストでは名前が変わるため、テストでの確かめ方に注意が要る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
