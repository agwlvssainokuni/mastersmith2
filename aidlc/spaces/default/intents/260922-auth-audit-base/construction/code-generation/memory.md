<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T14:12:00Z — U1: TraceAspect の既定値を依頼者の指定どおりにし、設定の名前は計画の mastersmith.trace.*、環境変数は MASTERSMITH_TRACE_* とした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-22T14:12:00Z — U1: Step 11 を Step 10 のテストより先に実装した（SecurityFilterChain が無いと既定のセキュリティが全要求にログインを求めるため）。ErrorPathController・ForwardedHeaderConfig・UserDetailsServiceAutoConfiguration の除外など、計画に無い安全側の部品と設定を足した。
- 2026-09-22T14:12:00Z — U1: opentelemetry-logback-appender の最新版（2.31.1-alpha）が Spring Boot 4.1.1 の OpenTelemetry（1.62）と食い違い、外部エクスポートの有効時に失敗したため、依頼者の判断（A）で 2.28.1-alpha に固定した; Spring Boot を上げるときはこの部品の版も合わせて見直す。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-22T14:12:00Z — U1 の ErrorPathController は対応を決めていない状態コード（sendError による 401・403 など）を 500 にするため、U3 の 401・403 の処理は ErrorResponseWriter で書く必要がある（U3 の Code Generation で確かめる）。
