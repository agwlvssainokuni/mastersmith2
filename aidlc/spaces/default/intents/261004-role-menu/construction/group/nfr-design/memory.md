<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T23:23:24Z — 同じ名前の同時の作成・変更で重なった側の 409 は、先の側が約 2 秒以内に確定すれば GROUP_NAME_DUPLICATE、そうでなければ GROUP_BUSY と読んだ; 捨ての試しで両方が起きると確かめたため（T1・T2）。NFR 要件の読み直しの R-07 として、この段の承認で確定する。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T23:23:24Z — 一意の鍵・主キーの待ちの上限を承認済みの「3 秒」ではなく H2 の既定の約 2 秒のままにした; 捨ての試しで書き込みの待ちにはヒントが効かず約 2,000 ms で切れ、SET LOCK_TIMEOUT はプールの接続に残ってほかの機能に響くため。差は reliability-design.md 1.3 と logical-components.md 4節に記録した。
- 2026-10-05T23:23:24Z — Q1: A で、承認済みの NFR2.7 に上限ちょうどの 5 VU の回を足し、20 VU の回を上限に届いた証拠つきの記録の回に変えた; 差を scalability-design.md 2.3 に記録し、U4 にも同じ形を勧めた。
- 2026-10-06T13:57:14Z — 承認の場の Request Changes（推奨の案のとおり直す）で、接続の合否を U4 と同じ上限 11・書き込み 5 VU（時間切れ 0・500 が 0 件・acquire 最大 20 ms 未満、pending は参考、20 VU は記録だけ）にし、同時の重なりのテストを時間に頼らない形（待たない違反・先の側を放さない上限切れ・4つの点の待ち合わせの口）にし、巻き戻しの印を GroupStoreTransactions に集めた; API 経由では同じメンバーの追加の主キーと削除の外部キーの違反は行の排他のため届かないと確かめ、store を直接呼ぶテストにした。鍵の待ち約 2 秒と 409 の code の分かれは受け入れた振る舞いとして記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T23:23:24Z — 違反・上限切れを起こしうる書き込みと排他を TraceAspect の対象の外の group.store にまとめた（Q3: A）; 例外の文（行の値が入ることを試しで確かめた）が TRACE に出る経路を構造の検査で閉じられる代わりに、Spring Data の書き込みの方法を使わず EntityManager を直接使う部品が1つ増える。
- 2026-10-05T23:23:24Z — 捨ての試しは Spring Boot の最小のアプリで約 10 分で7項目を確かめ、場所を消した; 素の JDBC では分からない Hibernate の例外の型と、setRollbackOnly を付け忘れると UnexpectedRollbackException になることが分かった（T5・T7）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T23:23:24Z — 上限ちょうどの 5 VU の回で pending が 0 にとどまるかは、HikariCP が接続を返す時機（確定の後の監査の2本目を返した直後に次の要求が借りる）に頼る; Performance Validation の結果で、ほかの接続の使い手が無いのに pending が出たときは、見積もりの誤りか時機のゆらぎかを hikaricp の値とログで切り分ける。
- 2026-10-05T23:23:24Z — Hibernate のロガー org.hibernate.orm.jdbc.error が違反の文（値つき）を WARN に出すことを試しで確かめた; 本番は OFF の設定だが、group の漏えいのテストでこの設定が消されたときに落ちる形にした。
