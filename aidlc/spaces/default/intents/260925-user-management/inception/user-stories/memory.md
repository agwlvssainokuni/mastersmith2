<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T13:22:13Z — 要件の確認で受け入れた指摘（R-02 期限切れの招待がある場合の再招待と古い招待の扱い、R-03 判定条件の Given/When/Then）は、この段の受け入れ基準で具体にする方針を計画に書いた。質問はペルソナ・分け方・横断の要件・Should・細かさの5問にした。
- 2026-09-25T13:34:02Z — mob の3人が挙げた判断の候補（約 25）のうち、専門の知識で決まるもの（期限の時刻ちょうどは無効、今のパスワードの誤りは 401 にしない、完了の時点の同じメールアドレスはほかの拒否と同じ応答（NFR3）、リンクを開いただけの失敗は監査に残さない（FR9.1 は登録の失敗））はリードが決め、利用者の体験や要件の追加に当たる9点（M1〜M9）だけを依頼者に尋ねた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T13:49:48Z — mob の3人の意見は合わせて約 75KB あり、統合（stories.md・personas.md・traceability.json）を決定事項つきでプロダクトマネージャーの担当に任せた。リードは判断の振り分けと依頼者への確認を受け持った。
- 2026-09-25T13:49:48Z — 実施の判断の文書を質問の確認の前にシェルで書いたため記録された書き込みが無く、レビューの依頼が拒まれた。確認の後に編集し直して依頼を通した。成果物は確認の後に書く（または書き直す）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T13:49:48Z — 依頼者の判断の後に確認をやり直した（M1〜M9 を足したまとめ）。mob の判断は答えの中身を変えるため、ストーリーに反映する前にもう一度 Looks correct を得た。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-25T13:49:48Z — 維持された異論: 登録の完了の画面でフォームの初期値（system・md）と画面の表示（ブラウザの値）が食い違いうる点は refined-mockups で扱う。
