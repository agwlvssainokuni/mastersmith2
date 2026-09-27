<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T03:54:09Z — 応答の時間の差はそろえない方針を要点に置いた; リンクの確かめ・完了の時間の差から分かるのはトークンが有効かだけで、256 ビットのトークンを持つ人にしか意味が無いため。質問にはせず、要点 9 に根拠を書いて依頼者の確認に委ねた。
- 2026-09-27T03:54:09Z — 招待・送り直しの 5 秒は U1 の時間切れ 3 秒で収まるとして質問にしなかった; U1 の NFR6.1 で決まっており、遅れ続ける受け手の既知の限界（U1 の NFR6.3）を引き継ぐだけにした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T04:34:05Z — 要件 NFR6 の招待の 5 秒を、受け手が正常なときの目標とした（performance-requirements.md の P-D1）; U1 で全体の上限を作らないと決まっており、遅れ続ける受け手では数倍になりうるため。要件の文書は書き換えていない。
- 2026-09-27T04:34:05Z — 登録の完了の同じメールアドレスの利用者がいる拒否（BR7.4）を、1 秒の目標（NFR6.5）の対象から外した; 今の createUser は重なりを確かめる前にハッシュを計算しうるため、成功と同じ重さになる。同時の登録でしか起きないまれな場合。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T04:34:05Z — 回数の制限なし（Q2 A）と有効期限の上限なし（Q3 C）を、残る危険 R1・R2 として security-requirements.md に置いた; 仕組みを持たずに済む代わりに、未認証の要求で監査の行を増やせる点と、受信箱に残ったリンクが長く使える点が残る。
- 2026-09-27T04:34:05Z — 登録の完了は bcrypt の間も接続と行の排他を持つ（Q4 A、NFR5.2）; 契約 C2 と U2 の BR5.3 を変えずに済む代わりに、U2 のパスワードの変更と作りがそろわない。接続が足りるかは NFR5.3 の k6 で確かめる。
- 2026-09-27T04:34:05Z — 負荷の試験の受け手に Mailpit を使い、登録の完了のトークンの用意（Mailpit の API で取り出すか、既知のハッシュで行を入れるか）を手順書の段に残した; 今の perf/README.md には受け手を起動する手順が無い。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T03:54:09Z — 登録の完了の bcrypt をトランザクションの中で計算するかを Q4 にした; 今の createUser は @Transactional の中で encode を呼び、BR7.3 と契約 C2 のままだと接続と招待の行の排他を約 278 ms 持つ。B を選ぶと C2 と U2 の承認済みの BR5.3 を変える差になる。
- 2026-09-27T03:54:09Z — 有効期限の上限を Q3 にした; 承認済みの BR1.6 は下限だけで上限が無く、上限を足すと承認済みの決まりへの追加の差になる。
