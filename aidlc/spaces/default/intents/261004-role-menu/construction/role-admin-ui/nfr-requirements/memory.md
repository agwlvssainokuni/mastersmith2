<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T22:03:31Z — 段の定義が必須とする rules は ui の単位に無いため、機能設計の D1〜D32 を入力にした; 機能設計の段の produces_kinds で ui は rules.md を作らない。各成果物の出典に書き、無い rules.md の中身は作らなかった。
- 2026-10-05T22:03:31Z — アクセシビリティと多言語の要件（NFR4.x）は tech-stack-decisions.md に置いた; ui の単位の作る成果物は4つで、前の Intent の ui の単位（user-admin の U5）も同じ置き場だったため。
- 2026-10-05T22:03:31Z — 依存・共有の部品・ルーター・脆弱性の関門（NFR6.5〜NFR6.8）は上流 NFR6.4 の行に寄せた; 上流に依存の枝番が無く、統合の関門として最も近いため。対応表と traceability の両方に同じ寄せ方を書いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T22:03:31Z — 共有の ApiDownload に応答のヘッダーを足す（Q2 A）; role の NFR2.9 の書き出しのヘッダーを画面が読むため。shared の部品に B8 で手が入るが、項目を足すだけの互換の変更で、既存の DSL のダウンロードは変えない。
- 2026-10-06T00:00:00Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01 を直し、F の流れの E2E は始めに自分で版 2 の DSL を適用する形にした; app-frame-ui の NFR 要件の Q2 の決定にそろえ、後始末は無し・040 より後の番号・前のテストの状態に頼らない。B8 の前半と B9 の後半は同じファイルで続ける。
- 2026-10-05T22:03:31Z — 機能設計の再レビューの R-03・R-04・R-09 は承認済みの機能設計を書き換えずコード生成の計画に回した; R-04 の今の main.tsx の受け渡しを保つことは NFR6.7 の要件に含めた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T22:03:31Z — 画面の時間は目標を書いたうえで記録だけにし、成否にしない（Q1 A）; 前の Intent の U5 と同じで、手元の1台の測りで統合を揺らさない代わりに、目標を外れても統合は止まらない。値は Build and Test で目標と並べて記録する。
- 2026-10-05T22:03:31Z — 1テーブルのカラムは分割せずに全件を描く; 目安 100 カラムでは十分で作りが単純になる代わりに、悪い側の 1,000 カラムは時間を記録するだけで守りを置かない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T22:03:31Z — 上限ちょうどの確かめの応答（約 25 万の変わる点）の受け取りの時間とメモリは画面で減らせない; NFR2.7 で値とヒープの大きさを記録し、応答の大きさそのものは U4 の持ち物として残る。
- 2026-10-05T22:03:31Z — data router への差し替え（NFR6.7）が既存の画面と E2E を変えないかは、コード生成の最初の小さな確かめで決まる; 成り立たなければ機能設計 7.3 の形に切り替えて差を記録する。
