<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-29T13:12:00Z — ログアウトの監査の確かめで、依頼者の「確認しました」の報告の直後に複写を取ったが、実際のログアウトは複写の約1分後（22:06:07）だった。依頼者の操作を裏付ける複写は、依頼者に操作の時刻か完了を具体的に確かめてから取る。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-29T13:12:00Z — スモークテストの S5 は、ブランドカラーが画面ではなく設定ファイルで決める項目だったため行えなかった。手順書を書く前に、画面で変えられる項目か設定で変える項目かをソースと README で確かめる。S6 は破棄の代わりに適用され、依頼者の決定でそのまま使った。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-29T13:12:00Z — 前の段の記録を承認なしでコミットしてしまい、依頼者の了承を得た。段の承認と記録のコミットは別の承認として、必ずコミットの前に提案する。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-29T13:12:00Z — 初期管理者の作成の INFO のログにメールアドレスが出る（project.md の Forbidden に反する既存の動き）。次の Intent の候補。

