<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T12:30:57Z — 再実行のため、リードの候補 P1〜P11 と支援役3名の追加（受け手の条件・E2E の実行の時点・SpotBugs の関門・Dependabot の受け方・記録の更新）のうち、チームの進め方に当たるものだけを 11 問にまとめた。利用者の状態・招待の期限・パスワード変更後のトークン・送信とトランザクションなどの機能の中身は要件・設計の段に回した。セキュリティ担当の確認で GitHub の既定のブランチは develop と分かり、Dependabot は向き先ではなく受け方だけを問うた。
- 2026-09-25T12:54:09Z — Q1（サブモジュール＋composite build）の答えが team.md・project.md の「lockfile で版を固定」「脆弱性検査の対象に含める」「取得元は Maven Central だけ」と食い違いうるため、追加の質問 F1（置き場と直接の変更の禁止）・F2（推移依存も lockfile と検査の対象にすることを条件にし最初の Bolt で確かめる）で確かめた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T12:54:09Z — まとめの確認の前に出す決定の要約（review-brief summary）は道具が動かず（main が無いという誤り）出せなかったため、答えのまとめだけを示して確認した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T12:54:09Z — 「今回は招待から登録の完了までの E2E を1本足す」は今回の Intent だけの決定のため team.md に入れず evidence.md の要件・設計に回す論点に置いた。team.md は「Intent ごとに代表の流れを1本まで足す」の決まりだけにした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
