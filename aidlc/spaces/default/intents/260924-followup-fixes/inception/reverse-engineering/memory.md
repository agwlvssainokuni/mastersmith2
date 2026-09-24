<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
- 2026-09-24T22:49:27Z — 依頼者は Full rescan を選んだが、深さ Minimal のため開発担当が深く読んだのは7件に関わる約30ファイルとビルドの設定だけだった。記録上の範囲は kind: partial とし、./ を analyzed.paths に入れない（前回と同じ扱い）。
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
- 2026-09-24T22:56:55Z — 比べた結果は NARROWER。前回の深い範囲（common-*・access・frontend-app-core など12部品）は今回流し読みで、記録上の範囲から外れた。代わりに今回の7件に関わる30ファイル（config・auth・audit・container-runtime・perf-and-monitoring・build-and-verify・frontend-feature-dsl）を深く確かめた。
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
- 2026-09-24T22:49:27Z — 1件目（Loki へキーと値を送る）で、監査の書き込み失敗の ERROR（メールアドレス・IP・User-Agent）と初期管理者のメールアドレスが外へ送られるようになる。送ってよいかは要件の段で決める。
- 2026-09-24T22:49:27Z — 4件目の Hibernate の案内のロガー名と、7件目のサブモジュールの固定先（gitlink）と checkout 5258c8b の一致は、読むだけでは確かめられていない。
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
