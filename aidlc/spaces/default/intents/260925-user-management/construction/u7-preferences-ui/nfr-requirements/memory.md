<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T05:02:19Z — 質問を作らず要点 13 件の要約の確認にした; API の時間は U2 の NFR6.1〜NFR6.4、アクセシビリティの確かめ方は U4 の NFR7.3・NFR7.5、送信中の表示と二重送信は機能設計の D10、パスワードの値の扱いは D13 と project.md の Forbidden で決まっている。project.md の「新しく決める論点が無いときは質問を作らない」に従った。
- 2026-09-27T05:02:19Z — U7 の画面には画面の時間の数値の目標を置かないと読んだ; U4 の Q1 B（2 秒）はログインの画面の目標で、U7 の画面は開くたびの GET 1回と送信だけのため、U2 の API の目標で押さえる。要約の確認で依頼者が確かめられる形にした。

- 2026-09-27T05:15:01Z — 画面の側のパスワードの値・応答の値・骨組みの変更の要件を NFR9 の枝番（NFR9.1〜NFR9.4）に寄せた; 要件に当たる ID が無く、どれもテストで確かめ方を決める要件のため。U4・U8 と同じ扱いで、氏名（初期値はメールアドレス）の扱いだけは NFR2.1 に置いた。
- 2026-09-27T05:15:01Z — 画面の時間の測り（NFR6.1〜NFR6.3）を、アクセシビリティの検査と同じく流れの E2E の本数に数えない読み方にした; U4 の NFR9.11 の前例に合わせた。測りの置き場（e2eTest の中か Build and Test の手順か）は B5 のコード生成の計画に残した。

## Deviations
- 2026-09-27T05:15:01Z — U4 の NFR7.3（既定の幅だけ）に幅 375px の6組を足す形を、U4 の文書を書き換えずに U7 の NFR7.4 と上流との差に記録した; U5 の段の共通の決定による。B5 のコード生成の計画で U4 の検査に足す。
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
- 2026-09-27T05:02:19Z — 狭い幅（768px 未満）を実際のブラウザの検査に足すかは、この段では決めなかった; U4 の NFR 要件の 6節で B5 のコード生成の計画で依頼者に確かめるとしたため。U5〜U7 の画面にまとめて当てる論点で、U7 だけで決めると単位ごとに扱いが割れる。
- 2026-09-27T05:10:00Z — U5 の段の依頼者の決定で、狭い幅と画面の時間の扱いが決まり、質問のファイルを直した; 狭い幅は幅 375px の6組を検査に足して B5 で U7 にも当てる。画面の時間は目標を置いて Build and Test で5回ずつ測るため、「目標を置かない」とした読みをやめ、U7 の場面と値を Q1 として尋ねる形にした。
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
