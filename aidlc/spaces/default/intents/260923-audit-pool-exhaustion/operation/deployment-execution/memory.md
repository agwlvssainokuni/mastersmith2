<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T06:16:46Z — 配備の前の「未コミットの変更が無い」確認は、アプリのソースを対象とし、監査ログとこの段の記録のディレクトリ（ワークフローの記録）は外して判断した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T06:16:46Z — 使い捨ての環境で指標の API（/actuator/metrics）が公開されているか確かめるため要求を1件送り、404 の WARN が1件出た。上限 30 の確認は、WAR の中の application.yaml とコンテナの環境変数で代えた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T06:16:46Z — colima の VM が 2GiB のため、使い捨ての環境（1g）と配備したアプリ（1g）を同時に動かせず、負荷の確かめのあいだは前の版のアプリを止めた。確かめが通らなければ docker compose start で前の版をそのまま起動し直せるよう、up ではなく stop にした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
