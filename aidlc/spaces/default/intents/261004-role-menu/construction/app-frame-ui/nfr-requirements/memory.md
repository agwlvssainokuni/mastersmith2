<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T22:19:52Z — rules.md の代わりに機能設計の D1〜D30 を入力にした; ui の単位は機能設計で rules.md を作らないため（U6 と同じ）。無い rules.md の中身は作らず、各成果物の出典に書いた。
- 2026-10-05T22:19:52Z — 固定先の更新の依存の確かめは、上流で e82b651 から 5bf1ffe の間に json と LICENSE の変更が無いことを読み取りで確かめた結果を根拠にした; それでも B9 の更新のコミットで npm ci・OSV-Scanner・verify を確かめる要件（NFR6.6）にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T22:19:52Z — 機能設計の WorkRoleAnnouncer を置かず、切り替えの結果は Toast だけで伝える; Toast の入れ物が既に aria-live を持ち、同じ文が2回読まれるため（再レビューの R-05）。tech-stack-decisions.md の上流との差 (b) に記録した。
- 2026-10-05T22:19:52Z — /logout は操作の印のある移動のときだけログアウトする（Q3 A）; 機能設計 D27 の「描いたら logout() を呼ぶ」との差で、外のリンクによるログアウトの強制を防ぐ。差 (d) に記録した。
- 2026-10-05T22:40:00Z — 承認の場の決定「Major 11 件だけを直す」で R-01・R-02 を直した; /logout の印は履歴に残るルーターの state をやめてメモリ上の一度きりの値にし、戻る・再読み込みでログアウトが繰り返されないことを I の流れの最後の手順で確かめる（R-01）。画面の時間は5回の中央値で判定し、最大と1回目を並べて記録する（R-02）。
- 2026-10-05T22:19:52Z — 機能設計の質問のファイルの「257 文字以上は 400」は古い; navigation の承認の場の直しで長さの上限はやめた。質問のファイルは書き換えず、差 (a) に今の決まりを書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T22:19:52Z — 流れの E2E は始めに自分で DSL を適用し、後始末をしない（Q2 A）; 前のテストの状態に頼らない代わりに、後のファイルに適用中の DSL が残る。流れのファイルを 040 より後に置き、F の流れも同じ形にそろえるよう B9 の計画に引き継いだ。
- 2026-10-05T22:19:52Z — 画面の時間の目標（0.5 秒・0.2 秒・0.5 秒）は前例からの見立てで、記録だけにした（Q1 A）; 超えたら Build and Test が Not Met を記録し、依頼者が扱いを決める。目標は緩めない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T22:19:52Z — 自分の権限の応答の型（displayName の形、create・delete の型）は未確定; U4 の B5 の計画で確かめ、B9 の計画に書き留める（NFR6.8）。
- 2026-10-05T22:19:52Z — 畳んだ状態の 5bf1ffe の aria-labelledby が axe の違反になるかは未検証; 違反なら除外を足さずに B9 の時点で依頼者に諮る（NFR4.4）。
