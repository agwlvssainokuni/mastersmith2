# Tech Stack Decisions — U4 監査ログ（u4-audit-log）

U4 はアプリ全体の技術の選定（`aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/nfr-requirements/tech-stack-decisions.md`）に従う。本書は、U4 で使う仕組みだけを書く。

| 分類 | 選定 | 理由 | 出典 |
|---|---|---|---|
| 出来事の受け取り | Spring の出来事の受け取り（確定後に受け取る仕組み `@TransactionalEventListener(phase = AFTER_COMMIT)`、トランザクションの無い出来事も受け取る設定）と、別のトランザクションでの書き込み | U4 の決まり 1.3・1.4 | Functional Design Q1 |
| 保存 | Spring Data JPA（追記と読み取りだけを持つ DB アクセスの部品）、Flyway で表と日時の索引を作る | NFR3.2、NFR1.5 | U1 Q2・Q8 |
| 切り詰め | 文字（コードポイント）単位で、サロゲートペアを分断しない切り詰め | U4 の決まり 2.1 | — |
