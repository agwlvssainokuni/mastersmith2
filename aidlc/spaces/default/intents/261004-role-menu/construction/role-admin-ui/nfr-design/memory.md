<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T13:18:24Z — 段の定義が必須とする scalability・reliability・observability の要件は ui の単位に無いため、performance・security・tech-stack の3つを入力にした; NFR 要件の段の produces_kinds で ui はそれらを作らない。各成果物の出典に書いた。
- 2026-10-06T13:18:24Z — S7 の ROLE_BUSY は RoleTransferSlot と行の排他を区別しない文言にした; 同じ code で画面からは見分けられず、どちらも少し待ってやり直せば済むため。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T13:18:24Z — 適用で送る本文を、確かめのときに読んだ文字列にした（Q1 A）; 機能設計 W7.4 の「同じファイル」を読んだ本文と決めた。ファイルを書き換えたら選び直す案内を足した。
- 2026-10-06T14:00:00Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01・R-02 を直し、画面の時間の測りを app-frame-ui の Q1 A の形にそろえた; 時計は画面の中の performance.now の1つ、測り始めは click・change の捕捉の段か要求の responseEnd、見本は前もって直列化し adminApiRoute.ts に body を受ける形を足す、判定は 5 回の中央値。
- 2026-10-06T13:18:24Z — make-you-chic-ui の Tabs は矢印のキーでその場で選ぶため、未保存で止めたときに「留まる」の後のフォーカスを今のタブへ戻す決まりを足した; 機能設計 7.2 に無い細部で、ソースの focusAndSelect で確かめた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T13:18:24Z — 最新の読み込みと待ちの案内の小さな部品は、shared に置かず3つの機能に同じ形で持つ; 機能どうしの import の禁止と shared の変更を2つに抑えるためで、同じ形のコードが3か所に並ぶ。
- 2026-10-06T13:18:24Z — 古い答えは世代の数で捨て、要求は取り消さない; ApiClient に取り消しの口が無く書き換えはサーバーで確定しうるため。不要な応答の受け取りは残る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T13:18:24Z — E2E の番号（U6 の検査 140・F の流れ 150）は app-frame-ui と合わせてコード生成の計画で確定する; 読み直しの R-08。
- 2026-10-06T13:18:24Z — data router への差し替えが既存の画面と E2E を変えないかは、コード生成の最初の確かめで決まる; 成り立たなければ機能設計 7.3 に切り替える。
