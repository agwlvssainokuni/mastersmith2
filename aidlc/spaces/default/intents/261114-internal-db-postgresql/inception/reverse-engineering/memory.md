<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-14T12:07:01Z — 範囲を絞る調べ（Focused scan）の対象を、内部DB に関わる所（接続・移行・内部DB の repository・テストの土台・compose・CI・内部DB の文書・perf）とした。対象DB の businessdata・targetdb の repository は、内部DB と別扱いのまま変えない依頼のため、深く読む対象から外した。
- 2026-11-14T12:07:01Z — 違反と排他の失敗を受けるメソッドだけを読んだ4つのサービス（UserAccountService など）は、記録の上では流し読み（shallow）に置いた。深く読んだと記録すると、読んでいない部分まで確かめた扱いになるため。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-14T12:07:01Z — 協力者が無効で開発担当が1人で行う段を、スキャンと9文書のまとめの2回の依頼に分けた。長い生成を1回にせず手順ごとに分ける project.md の決まりに合わせた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-14T12:07:01Z — 全体の調べ直しではなく範囲を絞る調べを選んだ（依頼者の選択）。前回深く読んだ .github/・gradle/・settings.gradle.kts・settings-gradle.lockfile・frontend/package.json・config/・docs/verification.md・common/health/ が流し読みに下がる（範囲の比べで NARROWER）代わりに、内部DB の周りを深く読み直せた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-14T12:07:01Z — H2 から PostgreSQL へのデータの移し替えを範囲に入れるか、結合テストをどこまで PostgreSQL でも流すか（275 本が H2 を起動する）は、要件定義で確かめる。
