<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T10:15:24Z — 差し込み口の order は U8 の NFR 設計の割り当て 310 を採ると要点に書いた; U3 の承認済みの機能設計と NFR 要件は「コード生成で決める」のままだが、U8 の security-design.md 2.3 が機能の名前で 100 台ずつ割り当てたため。質問にはしなかった。
- 2026-09-27T10:15:24Z — 承認の場の Minor（BR7.4 の経路は Unverified、R1 に警報が無いことを書き足す）を要点 5・15 に置いた; U2 の createUser が bcrypt の前に登録済みを確かめるため、この経路はふつう bcrypt を計算しない。性能の場面には入れず、正しさだけを結合テストで確かめる。

- 2026-09-27T10:37:17Z — 登録の完了の拒否で U3 が自分の setRollbackOnly を付ける作りを reliability-design.md 3節に書いた; createUser が EmailAlreadyUsed で付ける印は参加したトランザクション全体の印で、呼び出し元が自分の印なしに確定すると UnexpectedRollbackException になるため。U2 の設計の「呼び出し元は必ず巻き戻す」を具体にした。
- 2026-09-27T10:37:17Z — TraceAspect が invitation の Bean の引数と戻り値を TRACE で文字列にするため、トークンと URL を伏せ字の値の型で受け渡す作りにした; NFR1.5 の漏えいを TRACE のロガーでも確かめるよう InvitationSecretLeakIT に含めた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T10:37:17Z — order 310（SD-D1）、R1 の警報なしの書き足し（SD-D2）、BR7.4 の Unverified（PD-D1）、C5・C6 への fieldErrors の追加（SD-D3）を各成果物の上流との差に記録した; 承認済みの U3 の文書と契約は書き換えず、契約への反映は U3 のコード生成の計画までに行う。
- 2026-09-27T10:37:17Z — 定期の削除の既定の案（3 時 45 分・1000 件）と invitation の中の @EnableScheduling を書いた（RD-D2・RD-D3）; 最終の値はコード生成で決める承認のままとし、auth の設定に黙って頼らないため。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

- 2026-09-27T10:15:24Z — ADR-010 の「H2 の索引で招待中を1件に限れるか」を使い捨ての試しで確かめた; スクラッチの Java（メモリの中の H2 2.4.240）で、生成列と一意の制約で PENDING の2件目が 23505 で拒まれ、同時の追記は待った後に拒まれて1件だけ残った。Flyway と Hibernate の validate との組み合わせは未確認で、B3 の最初の結合テストで確かめる案（Q2 A）にした。

- 2026-09-27T10:37:17Z — 送信の失敗のログは U3 の INFO 1件と U1 の WARN 1件に分けた（Q5 A）; 1つの失敗に WARN が2件並ばず、ログの設定と U1 を変えずに済む代わりに、invitationId と失敗の WARN は同じトレースIDでつないで読む必要がある。
- 2026-09-27T10:37:17Z — 公開の道のアクセストークンの扱いを変えない（Q3 A）ことを残る危険 R3 として security-design.md に足した; auth と common.security に手を入れずに済む代わりに、画面の側がトークンを付けると登録の完了が 401 になりうる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T10:15:24Z — 公開の2つの道でも既存の AuthSecurityContributor がアクセストークンを読むため、壊れたトークン付きなら permitAll の前に 401 になると分かり Q3 にした; 画面の側は U4 の ApiClient が付けない前提で、サーバーでも読まないようにすると common.security と auth.web に手が入る。
- 2026-09-27T10:15:24Z — invitation から auth への依存を Q1 にした; 構造の検査は禁じず dslmanage の前例があるが、承認済みの components.md の Invitation の depends_on は UserAccount と Mail だけで、U2 は機能の中で取る形を選んでいる。
- 2026-09-27T10:15:24Z — 送信の間に接続を持たないことの確かめ方を Q4 にした; 承認どおりの「一覧が応答する」テストだけでは接続が 30 本あるため持ち続けを見落としうる。
- 2026-09-27T10:15:24Z — 送信の失敗のログを Q5 にした; U1 は失敗ごとに WARN を1件出すが invitationId を持たず、logback の MDC は traceId・spanId だけを出すため、重ねない形に選択肢がある。
