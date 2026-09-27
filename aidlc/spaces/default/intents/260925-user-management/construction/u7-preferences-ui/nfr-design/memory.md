<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T10:49:58Z — 質問は3問（測りの置き場と利用者・ログインの後の検査の利用者と組の当て方・検査する状態）にし、ほかは要点 11 件として要約で確かめる形にした; 検査の組・合否・axe-core の読み込み方は U4 の NFR 設計で、fieldErrors の形は U2 の NFR 設計で決まっていると読んだ。fieldErrors と U6 の共用の関数の関係、骨組みの変更の既存の画面への影響は、作りが1つに決まるため要点 6・8 にした。
- 2026-09-27T10:49:58Z — U4 の組の切り替え（U4 の鍵を初めのスクリプトで置く）はログインの後の画面では効かないと読み、Q2 にした; ログインの応答の利用者の設定が当たり（U4 の W5・D8）、プリファレンスの画面は GET の値でそろえる（D2）ため。推奨は GET /api/me/preferences の差し替えで D2 の本物の道を通す案。
- 2026-09-27T10:49:58Z — 機能設計の 10節の (c)（fieldErrors の形は U2 のコード生成で決まる）は、U2 の NFR 設計の3節で形が決まったため、この段で読み取りと理由の対応（REQUIRED → required など、INVALID_VALUE は選択の一般の文言）を設計する案にした; U6 の共用の関数の理由の union（U6 の機能設計の6節）と同じ union に寄せ、1つの表で文言にする。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T12:13:53Z — Q1〜Q3 はすべて A、まとめは Looks correct で答えが出た; U4 の組の切り替え方をログインの後の画面では使わず GET /api/me/preferences の差し替えにしたことと、機能設計の 10節の (c) の fieldErrors の形をこの段で当てたことを、logical-components.md と security-design.md の上流との差に記録した。承認済みの文書は書き換えていない。
- 2026-09-27T12:13:53Z — 測りの登録の完了を画面ではなく API（POST /api/registration/complete）で行う作りにした; 画面の流れは U6 の E2E-1 が確かめるため、測りの準備を短くする。招待を使える設定か受け手の手段が無いときは測りを飛ばして Unverified とし、U5 の performance-design.md の 4.2 と同じ扱いにそろえた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T12:13:53Z — 2つの画面を同じコンテキストで続けて検査し、誤りの状態も同じコンテキストで続ける作りにした; 組ごとのログインは 40 回、検査は 80 回になる。検査の中で PUT・POST が送られていないことをコンテキストの要求の記録で確かめ、サーバーの状態を変えないことを保つ。
- 2026-09-27T12:13:53Z — 送信中の印を useState ではなく useRef で持つ作りにした; 状態の更新は次の描画まで見えず、描画の前の2回目のクリック・Enter を止められないため。表示は状態の saving・sending で描く。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T10:49:58Z — Q1 の推奨 A（測りのテストの中で招待から利用者を作る）は、受け手からリンクを取り出す手段と E2E の WAR への SMTP の設定の渡し方に頼る; どちらも infrastructure-design の持ち主で未定（U5・U6 と同じ前提）。B5 の検査のファイルの番号と、U5〜U7 の検査・測りで初期管理者の設定とパスワードを変えないことは、B5 のコード生成の計画で U5・U6 とそろえる必要がある。
