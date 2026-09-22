<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-09-22T12:20:00Z — 当面の配備先が開発者の PC 上のコンテナだけのため、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らず、配備先が決まったときに置き換える前提で設計した; チームの進め方の Deployment に従うため。
<!-- aidlc-wave-memory:u1-app-skeleton:a77d8f37c251fe43baf200dba9a381d7f8ac951cfeda27ba9975ac61ab6d5e95 -->

- 2026-09-22T12:25:00Z — コンテナのタイムゾーン（Asia/Tokyo）は全単位に効く設定だが、削除の時刻を持つ U2 の段で決めた; 保存する時刻は UTC の時点とし、判定がタイムゾーンで変わらないようにした。
<!-- aidlc-wave-memory:u2-authentication:ee2a7541fb6e54060a7625f5c2330daff25c2f79c101c64f82fb5623d7e7607e -->

- 2026-09-22T12:32:00Z — U3 は独自の基盤を持たないため、質問を作らず、設計の要点を要約として依頼者に確認した; 監視の指標は U1 と重なるものを繰り返さず、U3 に固有のものだけを書いた（U2 の確認の指摘 R-02 を踏まえて）。
<!-- aidlc-wave-memory:u3-access-control:c0a1b47f83fb736f60d69dd1d1faa429fb818256e17c1450c9a5e98b7f843ae1 -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-09-22T12:25:00Z — 確定回答 Q2 により、U1 の基盤の設計のコンテナの CPU の上限（2）を U2 の要件のために 4 に上書きした; U1 の文書は確認済みで書き換えないため、U2 の infrastructure-specification.md 4章に優先を明記し、Code Generation の計画で compose に反映する。
<!-- aidlc-wave-memory:u2-authentication:992865570a8a1864d2d0512ff1158709986a8e8fb8ab4e9e9ee1cc5b65ec901d -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-09-22T12:20:00Z — 1コマンドの検査を Gradle の1つのタスクにし（Q4）、npm と外部の道具も Gradle から PC の実行ファイルを呼ぶ形にした; Node.js を Gradle で自動で入れる仕組みは使わず、版の確認で失敗させる方を選んだ（道具を増やさないため）。
<!-- aidlc-wave-memory:u1-app-skeleton:7444f4962eb78d62ce16cc8c60552166ade64815b8034bbaebecf1f785cd56f8 -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-09-22T12:20:00Z — ヘルスチェックの起動の猶予 40 秒・コンテナの資源（メモリ 1GB・CPU 2）は見積もりで、Performance Validation で見直す。
<!-- aidlc-wave-memory:u1-app-skeleton:52e2aea0387f742ddbd6c1d3e106e94906742958cb2c1f265038ae8563b173a2 -->

- 2026-09-22T12:32:00Z — 管理者でない利用者の画面の確認は、本Intentでは利用者を作る機能が無いため配備の確認に入れられない; E2E で管理者でない利用者をテスト用に作る方法を Code Generation で決める。
<!-- aidlc-wave-memory:u3-access-control:72076939fd611fb5d267337504d278eda8d2a0e91ad660b6a142fd0f276695ec -->
