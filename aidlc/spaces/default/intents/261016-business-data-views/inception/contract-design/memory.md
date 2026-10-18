<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-18T06:30:00Z — 書く前に既存の口（EffectivePermissionResolver・PermissionSnapshot・targetdb.domain・UrlQueryStrippingObservationFilter・既存の一覧の API・Problem Details の code と fieldErrors）をコードで確かめた（前の Intent の学び）。権限の写し PermissionSnapshot が DSL と dslHash を持つため、それを判定と SQL の照合の両方に使う決まり（C7）にし、Domain Design のレビュー R-03 を契約で解いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-18T07:00:00Z — 承認の場で Request Changes を受けたとき、前の段の学びのとおり、直す前に段のやり直し（redo jump）を行ってから直し、確認のまとめを確かめ直してレビューに回した（承認の場が開けなくなる事態を避けた）。
- 2026-10-18T06:30:00Z — 業務の失敗の code の名前と状態コードの割り当て（409・503 など）は契約で仮に置き、確定を U1・U2 の機能設計に回した（Open questions）。1つの code に1つの状態コードを固定する決まり（team.md）を機能設計で守る。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-18T06:30:00Z — 行の値（主キー・検索の値）を API の道にも問い合わせにも載せず本文で運ぶ形にした（CQ1: A）。トレースの道の属性に値が残らない代わりに、詳細・削除が REST の GET・DELETE ではなく POST になり、画面の道にも主キーを載せない（CQ2: A、再読み込みでは一覧へ戻る）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-18T06:30:00Z — 書き込みの DataSource とトランザクションの管理者の作り方、業務データの時間の上限の置き場、SqlIdentifiers・DatabaseProduct の持ち主（Domain Design のレビュー R-01・R-02）は U1・U2 の機能設計・NFR 設計で決める。
