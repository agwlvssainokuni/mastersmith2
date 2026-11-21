<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-18T15:40:00Z — 捨ての試し（Delivery Planning の Q3: A）を、この段の始めに U1 と U2 の分を1回で行った。U2 の H2 側の確かめ（`FINAL TABLE`・`MERGE ... KEY` の書き直しと、同時の標準の `MERGE`）は、U2 の機能設計の前に回した（U1 の機能設計に要るのは PostgreSQL 側と、バイナリの写し方の両方での確かめだったため）。
- 2026-11-18T15:40:00Z — 機能設計の「技術に依らない・コード無し」の制約は、この単位が内部DB の設定そのものを扱うため守りきれない（`@JdbcTypeCode`・Flyway・接続プールの名前が決まりの中身になる）。設計の判断として必要な名前だけを書き、実装の細部（クラス・SQL の全文）は書かなかった。
- 2026-11-18T15:40:00Z — FR3.10 の「接続情報を出さない」を、依頼者の決定（Q1: A）で、起動時だけ保証する形に読んだ。動作中の失敗のログの例外の文にホスト・ポート・利用者名が出うることは、README の既知の制約に回す。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-18T15:40:00Z — `unit-of-work.md` の U1 の注意「既存の `config` の部品に手を入れる」は、新しい機能 `internaldb`（`internaldb.platform`）に置く決定（D-1）により当たらなくなった。`config` の境界テストは足さず、`InternalDbBoundaryArchitectureTest` を足す。
- 2026-11-18T15:40:00Z — 契約 C4 の「`ContainerRuntimeCheck` と同じ扱い」は、警告の文が「対象DB のテスト」と書かれているため、内部DB 用の別クラス `InternalDbPostgresRuntimeCheck` にした（判定の考え方は同じ）。
- 2026-11-18T15:40:00Z — 要件 FR3.10 の「動作中」は今のまま（質問 Q1: A）。要件 FR1.1 の周辺に、接続先が空のとき起動を止める決まりを足した（質問 Q2: A）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-18T15:40:00Z — Spring Boot との組み立ては、`EnvironmentPostProcessor` で設定を足す案より、起動の中の3つの入口（Flyway の設定の差し替え・接続プールの `BeanPostProcessor`・Flyway の移行の入口）を選んだ。PostgreSQL の結合テストが接続先をコンテナの起動の後に動的に渡すため、前者は値を読み逃す。代わりに `BeanPostProcessor` の前提（プールが最初の接続の前に始まらない）を単体テストで確かめる必要が増える。
- 2026-11-18T15:40:00Z — バイナリの写し方は、`@Column(length=10485760)` だけで通る案より、意図が読める `@JdbcTypeCode(LONG32VARBINARY)` を選んだ（どちらも両方の DB で通った）。V8 の一意は、条件つきの一意の索引より、STORED の生成列＋同じ名前の制約を選んだ（表の形を H2 と同じに保てる）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-18T15:40:00Z — 外の接続が移行の表を握っているときの `lock_timeout` 2 秒の影響と、移行の接続だけ待ちの上限を外すか（NFR 設計）。
- 2026-11-18T15:40:00Z — `HikariDataSource` の `setConnectionInitSql` が、最初の接続を借りる前なら効くという前提（コード生成の最初の単体テスト T8 で確かめる）。
- 2026-11-18T15:40:00Z — U2 の機能設計の前に確かめること: `FINAL TABLE`・`MERGE ... KEY` の書き直しの H2 側の結果、同時の標準の `MERGE` の H2 での振る舞い。
