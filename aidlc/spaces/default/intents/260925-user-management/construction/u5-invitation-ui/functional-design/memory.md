<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T00:43:17Z — AC2.1.2 を「期限内の行にも期限内と文字で出す」と読んだ; 画面イメージは期限切れの印だけだったが、AC2.1.2 は1秒前の招待が期限内であることも文字で示すことを求めるため。設計の要点 4 と上流との差（要点 19 の c）に書いた。
- 2026-09-27T00:43:17Z — 招待の 400 VALIDATION_FAILED はメールアドレスの項目の誤りとして扱うことにした; 今の GlobalExceptionHandler の VALIDATION_FAILED は項目ごとの誤りを返さず、契約 C5 も形を決めていない。Modal の項目のうち言語は選択肢から選ぶため誤りにならず、項目の形に頼らずに済む。
- 2026-09-27T00:43:17Z — U3 の R-01・R-02 と U1 の R-01 は直した後の形を前提にした; 依頼者の決定で承認の場でまとめて直すため。U3 の今の文書（BR5.3 の氏名が無ければメールアドレス）とは違うことを「決まっていること」と要点 19 の b に書いた。
- 2026-09-27T01:05:00Z — Q1 A の「関数を shared へ移す」は formatDateTime だけを移すと読んだ; shortHash・formatBytes は DSL だけが使うため features/dsl/format.ts に残す。shared は app に依存しない今の形を保つため、移した関数の言語の引数は 'ja'・'en' の型を自分で持つ（9節の d）。
- 2026-09-27T01:05:00Z — traceability の対象は US1.1・US2.1・US2.2 の全 AC と CR1.1・CR1.4・CR6・CR6.1〜CR6.9 とした; サーバーだけで確かめる AC（AC1.1.11〜13・AC2.1.3・AC2.2.1・2.2.5・2.2.6・2.2.13）は U4 の書き方に合わせて Deferred ではなく N/A（U3 が OK で持つ）にした。U3 から Deferred で受けた7件はすべて OK。
- 2026-09-27T01:05:00Z — 一覧の 403 は、DSL の画面の「表示できない」ではなく読めなかった表示と一般の 4xx の文言にした; まとめの確認の要点 8・11 の形のままにし、骨組みの access ADMIN で 403 は使っている間に権限を失った場合だけのため（9節の g）。
- 2026-09-27T01:05:00Z — 送り直しの一般の失敗（404・503 以外）と取り消しの一般の失敗は読み直さないことにした; 要点 12・13 は一般の失敗の読み直しを書いていないため、状態が変わっていない前提でフォーカスを元のボタンに残す。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T01:05:00Z — 取り消しの確かめの役割を interaction-spec.md の alertdialog ではなく make-you-chic-ui の Modal の dialog にした; Modal は role を dialog に固定し、サブモジュールは変更できないため。DSL の確かめと同じ作りで、CR6.7 の動きは満たす。まとめの確認の要約には無かった差で、9節の e に記録し承認の場で伝える必要がある。
- 2026-09-27T01:05:00Z — 「一覧でこの招待を見る」を mockups.md のリンクではなく見た目がリンクの button にした; URL へ移らない画面の中の操作のため（9節の f）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T00:43:17Z — 質問を3問（日時の書式と置き場・今のページの持ち方・開いたままの間の期限切れ）に絞り、ほかは設計の要点に置いた; 応答ごとの画面の動き・警告の文言・フォーカスの置き場は、契約 C5・画面イメージ・CR6・DSL の画面の前例から導けるため。要点が 19 件と多くなり、依頼者は要点の表を読んで確かめる必要がある。
- 2026-09-27T01:05:00Z — 背景のクリックで閉じない仕組み（DSL の DslConfirmDialog の押下の見張り）と一般の失敗の文言を features/invitation に複写した; 機能どうしの読み込みを作らないため。同じ仕組みが2か所になり、3つ目の機能で要るなら shared へ移すことを考える。
- 2026-09-27T01:05:00Z — 「取り消す」の英語を Cancel ではなく Revoke にした; DSL と同じく「やめる」を Cancel とするため、確かめの Modal で2つの Cancel が並ぶのを避けた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
