<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T08:10:00Z — メモリの 93% はアプリが持ち続けるデータではなく最大ヒープの割合 75% によると測定から読み、依頼者の決定（D1: T1・D2: M1）で Dockerfile の MaxRAMPercentage を 50 にするだけにした。目標の判定は Build and Test の dslMixed で行う。
- 2026-09-25T05:20:00Z — 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T08:10:00Z — 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。
- 2026-09-25T05:40:00Z — 依頼者の決定（Q2: A、Q3）で、詰め直しの入口を承認済みの要件の管理者向け HTTP API（FR1.3）から JMX だけに変え、HikariCP の標準の機能で行うためアプリのログと監査ログ（FR1.6・FR1.7）は出さないことにした。要件の文書は書き換えず、差を計画とこの段の成果物に明記する（project.md の決まり）。
- 2026-09-25T05:20:00Z — 開発担当への依頼で Testing Contract の本文を差し込み忘れ（{CONTRACT} のまま）、すぐ後に追って本文のファイルの場所を伝えた。計画に貼られた本文が render の出力と一致するかを承認の前に確かめる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T08:10:00Z — AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
