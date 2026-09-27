<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T06:17:10Z — 質問を4問（本人の特定と送り手の情報の取り方・項目ごとの誤りの形・同時のパスワードの変更・V7 の後方互換）に絞った; NFR 要件で作りが1つに決まらない点だけを選んだ。bcrypt をトランザクションの外に置く流れ、2本目の接続、カバレッジの戻し、利用者の作成で bcrypt の前に登録済みを確かめることは、NFR5.1・NFR5.2・team.md の決まりとコードから作りが決まるため、設計の要点に書いた。
- 2026-09-27T06:17:10Z — 利用者の作成で bcrypt の前に登録済みを確かめる作りは質問にしなかった; 読み取り1回が増えるだけで、U3 の登録の完了は verify と完了の前に existsByEmail で拒否済みのため時間の差で新しく分かることが無い。一意の制約に当たったときの巻き戻しの印は、U3 が EmailAlreadyUsed で必ず巻き戻す（U3 の BR7.4）ため食い違わない。

- 2026-09-27T09:55:23Z — 401 の AUTHENTICATION_REQUIRED は auth.domain.AuthProblemTypes にあり user から参照できないと分かり、user.web が ProblemTypeRegistry.findByCode で起動時に引く作りにした; 同じ code を user の一覧に重ねると起動時の重複の検査で止まるため。本人の行が消えた場合は業務処理が結果の型で返し、user.web が 401 に変える（security-design.md 2節）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T09:55:23Z — 要約の要点 14（PasswordChangedEvent は user.service）と違い、user.domain に置いた; audit.domain.AuditEventFactory は既存の出来事をどれも各機能の domain から読むため。依頼者が Looks correct とした要約との差として security-design.md の S-D3 に記録した。
- 2026-09-27T09:55:23Z — 契約 C4 に無い fieldErrors の形を決め（Q2 A）、パスワードの書き込みを条件つきの更新にした（Q3 A）; 契約と機能設計の文書は書き換えず、security-design.md の S-D1・S-D2、reliability-design.md の R-D1・R-D2、performance-design.md の P-D1 に差を記録した。C4 への反映は後の段。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T09:55:23Z — createUser は EmailAlreadyUsed のとき自分で巻き戻しの印を付ける作りにした; 一意の制約に当たった後に正常に戻ると、自分で始めたトランザクション（初期管理者の作成）で確定のときに UnexpectedRollbackException になるため。呼び出し元は EmailAlreadyUsed で必ず巻き戻す前提になり、Javadoc に書く。
- 2026-09-27T09:55:23Z — Hibernate の validate が余分な列を許すことは自動のテストで確かめず、戻しの練習（deployment-execution）に回した; 1つ前の版のエンティティをテストで持てないため。見込みが外れたと分かるのは配備の段になる。

## Open questions
- 2026-09-27T06:17:10Z — 新しい user.web は auth の主体の型と送り手の情報を使えない（既存の ArchUnit「user does not depend on auth」）; 機能設計の entities.md は「ClientInfo と同じ取り方」とだけ書いている。Q1 で、user の中で取る・common へ移す・構造の検査を緩めるのどれにするかを尋ねた。
- 2026-09-27T06:17:10Z — 今の User は全列を持つエンティティで、変更の検出の更新は全列を書く; BR3.4（相手の列を古い値で上書きしない）を守るため、書き換えは更新の問い合わせで行うと要点に書いた。Flyway 12 が知らない新しい移行を無視する既定と、Hibernate の validate が余分な列を許すことは、まだ見込みのまま（Q4）。
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
