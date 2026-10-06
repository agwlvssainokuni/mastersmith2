<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T23:41:00Z — 捨ての試しで YAML のキーは型によらず書いたとおりの文字列になると分かった; 今の変換の規則がキーの元の文字列を使うため。読み込みで型のキーを誤りにする手当ては要らず、書き出しの二重引用符だけで往復を保つと読んだ。
- 2026-10-05T23:41:00Z — 別名 0 はコレクションを指す別名 0 と読んだ; 試しでスカラーの別名は受け付けられ節も増えず、コレクションの別名は SnakeYAML の上限で拒否された。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T13:54:49Z — 承認の場の Request Changes（推奨の案のとおり直す）で、鍵・主キーの待ちの上限を約 2 秒の決まった値として書き、group は role の合否の形と重なりのテストの形にそろえたと直した; あわせて R-02 の表を見直し、#9 の MERGE の待ちを B5 の始めに確かめること、#10 の逆の順を合図なしで放すことを足した。RoleStoreTransactions と ArchitectureTest の整合は確かめて変更なし。
- 2026-10-06T11:33:51Z — 読み直し1回目（NOT-READY）の指摘 R-01〜R-10 を、依頼者の決定（Q5: A と、指摘をすべて直す）で直した; 巻き戻しの印は service の部品 RoleStoreTransactions の status.setRollbackOnly に集め、待ち合わせの口を4つにして API で届く重なりの表と (b)・(g) の期待を確定した。RoleTransferSlot の根拠はヒープ約 1 GiB に直し、本文を読む前に取って finally で放す形にした。各成果物の末尾に直しの節を足した。
- 2026-10-05T23:41:00Z — 確かめと適用を同時に1つずつしか通さない RoleTransferSlot を足した; 試しで1回の確かめにヒープ 512 MB の半分ほどを使うと分かったため。承認済みの要件に無い決まりで、reliability-design.md 6節と security-design.md 7節に記録した。
- 2026-10-05T23:41:00Z — 巻き戻しの印を業務処理ではなく role/store の中で付ける形にした; group の NFR 設計の読み直しの R-05 の手当てで、group の今の設計とは違う。
- 2026-10-05T23:41:00Z — rolePoolLimit の合否の回を上限 11・5 VU・acquire の最大 20 ms 未満と時間切れ 0 にした（Q1: A）; 承認済みの NFR2.7 と group の今の形との差を scalability-design.md 2.3・2.4 に記録した。
- 2026-10-05T23:41:00Z — 試しは Spring Boot と Hibernate を使わず HikariCP と JDBC で行った; 3つの範囲は JDBC で確かめられ、2 時間の上限の中で終えるため。本番の上乗せは目標の 10 分の 1 未満の値から見通した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T23:41:00Z — import の書き込みを role/store の中の JDBC のバッチ（1,000 件）にした; 試しで 26 万件の適用が約 3.7 秒・1文の最大 57 ms で、エンティティを作らずに済む代わりに、JPA と JDBC が同じトランザクションの接続を使う前提が要る。
- 2026-10-05T23:41:00Z — 写しと resolve の2つの口はそのままにし、一致を EffectivePermissionConsistencyIT の期待の表で守る（Q2: A）; 予算と承認済みの形を変えない代わりに、2本の実装の一致をテストで保ち続ける必要がある。
- 2026-10-05T23:41:00Z — 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）; 時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T23:41:00Z — 試しは上限の 96%（10,115,916 バイト）で測り、上限ちょうどは比例で見通した; Performance Validation の roleTransferLarge で上限ちょうどの1回を測る。
- 2026-10-05T23:41:00Z — acquire の最大 20 ms 未満は測定の無い目安; 合否の回の結果が 5〜20 ms のときは合格のまま記録し、承認の場で目安を見直す。
- 2026-10-05T23:41:00Z — 一意の鍵・主キーの待ちの上限（約 2 秒か 3 秒か）は group の承認の場の決着にそろえる; 決着が違えば reliability-design.md 2.1 と RoleBusyApiIT の期待を合わせる。
