<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T13:36:46Z — ui の単位のため scalability・reliability・observability の要件と設計は無く、performance・security・tech-stack の3つの要件を入力にした; 段の定義が必須とする入力が無いことを各成果物の出典に書いた（U6 と同じ）。
- 2026-10-06T13:36:46Z — 測り終わりの要素は 5bf1ffe の Sidebar のソースで決めた; 開いたまとまりにだけ子の ul（id は aria-controls と同じ）が描かれ、Toast は既定 4 秒で消えるため、見張りは押す前に入れる形にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T13:36:46Z — 承認の場で直さなかった NFR 要件の Minor（R-04・R-05・R-07・R-08・R-09）を、この段の設計に入れた（Q2 A）; 要件の文書は書き換えず、各設計の文書の上流との差に記録した。
- 2026-10-06T13:36:46Z — ログアウトの印を骨組みから消すため、登録の型の LoginStateProvider に任意の clearLogoutIntent を足す; 骨組みから features/auth を import しない向きを保つため、今の logout と同じ形にした（logical-components.md 3節）。
- 2026-10-06T14:00:00Z — 承認の場の決定「推奨の案のとおり直す」で R-01〜R-03 を直した; 今のコードでは path の項目に移る直前の口も useLogout も無いと確かめ、path の項目に任意の beforeNavigate、LoginStateGate に useClearLogoutIntent を足す設計にし、LogoutPage は authSession.logout を直接呼ぶ。測り始めは操作の直前に入れる click の捕捉の聞き手で、目当ての要素のときだけ記録する（role-admin-ui もそろえる）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T13:36:46Z — 画面の時間の時計を画面の中の performance.now() の1つにした（Q1 A）; 0.2 秒の目標を Playwright の待ちと往復に左右されずに測れる代わりに、U6 の測り方（テストの側の Date.now()）とはそろわない。
- 2026-10-06T13:36:46Z — E2E の番号を 160（検査）・170（I の流れ）にした; U6 の 140・150 と重ならず、DSL を自分で適用するほかのファイルより後に流れる。計画の最初に U6 の案と突き合わせて確定する。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T13:36:46Z — 160 の 20 組 × 状態の実行時間は未見積もり; B9 の計画で1組あたりを見積もり、10 分を超える見込みなら 160・161 に分ける。
- 2026-10-06T13:36:46Z — 自分の権限の応答の型は U4 の B5 で確かめる前提; 確かめられなければ S8 を外すかを計画の承認で諮る。
