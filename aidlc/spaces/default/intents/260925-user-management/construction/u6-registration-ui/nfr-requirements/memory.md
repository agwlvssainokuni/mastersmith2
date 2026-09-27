<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-09-27T05:03:20Z — トークンのリファラーの扱いは質問にせず要点にした; フラグメントはブラウザの決まりで Referer に載らず、既存の Referrer-Policy: same-origin と CSP の 'self' で外部の読み込みも無いため。新しい手当ては足さず、console の見張りを画面部品のテストに加える案にした。
- 2026-09-27T05:03:20Z — 二重の送信の防止・パスワードの画面の側の扱いは質問にしなかった; 機能設計の W8・D8・D9 で決まっているため、確かめ方（要求1回・本文の値が変わらない）だけを要点に書いた。
- 2026-09-27T05:03:20Z — E2E-1 の流れ全体の時間の目標は置かない案にした; 流れの成否を見るテストで、画面の時間は Q1 で別に測るため。リンクの取り出し方と SMTP の設定の渡し方は infrastructure-design の持ち主のまま。

- 2026-09-27T05:03:20Z — 画面の側のセキュリティの要件（公開の API・回数の制限・入力・二重の送信・CSP）を NFR9.1〜NFR9.5 に寄せた; 要件に当たる ID が無く、U4・U8 の前例と同じ扱いにするため。トークン・メールアドレス・列挙の防止は NFR1・NFR2・NFR3 の枝番にした。
- 2026-09-27T05:03:20Z — U4 の実際のブラウザの検査で、登録の完了の画面はフォームと「リンクが使えない」の2つの状態を検査するとした; 状態で見た目が大きく変わるため。確かめ中・読み込めない・ログイン中の案内は vitest-axe に任せた。

## Deviations
- 2026-09-27T05:03:20Z — U4 の NFR7.3 に幅 375px の6組と答えの差し替えを足したが、U4 の文書は書き換えなかった; 承認済みの文書を書き換えない決まりのため、tech-stack-decisions.md の上流との差に記録した。E2E-1 の中でリンクを 5 回開く計測の手順も W13 との差として記録した。
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

- 2026-09-27T05:03:20Z — 狭い幅の確かめ（Q3）は U4 が「B5 の計画で確かめる」とした点を前に出して問うた; 招待メールから開く登録の完了の画面はスマートフォンで開かれやすく、ラジオ3組と lg の組み合わせが最も崩れやすいため。推奨は U6 の画面だけに足す B とし、U5・U7 に及ぼす C は単位をまたぐため推奨にしなかった。

- 2026-09-27T05:03:20Z — 狭い幅の Q3 を取り下げ、決まっていることへ移した; U5 の質問で U5〜U7 に共通する依頼者の決定（幅 375px、テーマ2×文字の大きさ3の6組、B5 ですべての画面）が出たため。幅は推奨にしていた 360px から 375px にそろえた。

- 2026-09-27T05:03:20Z — フォームの状態の検査を要求の差し替えで出す（Q2 B）代わりに、本物の確かめの API の応答の形を通らない; 受け手の手段に頼らず検査を独立させるため。応答の形の食い違いは U3 の契約 C6 の結合テストと E2E-1 で見つける前提とし、tech-stack-decisions.md の 3.1 に記録した。

## Open questions
- 2026-09-27T05:03:20Z — 実際のブラウザのアクセシビリティの検査でフォームの状態を出す手段（Q2）; 本物のトークンには受け手の手段（infrastructure-design で未定）が要るため、要求の差し替え（page.route）で出す B を推奨にした。依頼者の答えを待つ。
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
