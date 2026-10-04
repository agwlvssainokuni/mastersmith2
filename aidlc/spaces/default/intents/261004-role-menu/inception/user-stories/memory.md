<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T10:40:00Z — mob の3人の意見のうち、専門の知識で決まるもの（`/api/me` の直し、招待中の人は割り当てられない差、テーブルと子の両方を持つメニューの項目の出し分け、合否が一意でない基準の分け方、必須のテストの抜け）はリードが決め、利用者の体験や要件の差に当たる8点（M1〜M8）だけを依頼者に尋ねた。M の答えで答えの中身が変わったため、まとめの確認をやり直した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T10:40:00Z — 開発担当の確かめで今の DSL にスキーマの階層が無いと分かり（要件 FR2.1 の前提と食い違い）、依頼者は DSL にスキーマを足す形（M1: B）を選んだ。追加の質問 F2・F3 で、形は複数・中身は1つ、書式の版 2 だけを受け付け版 1 は読み替えない形になった。要件に無い DSL の変更のため、新しいストーリー US6.1 と「要件との差」に書き、前の版のアプリへ戻すと DSL が無い状態で起動することを配備の段の論点として残した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T10:40:00Z — mob の3人の意見は合わせて約 64KB あり、統合（stories.md・personas.md・traceability.json）を決定事項つきでプロダクトマネージャーの担当に任せた。統合後に担当の書き込みが記録されないおそれがあるため、各成果物を指揮役の Edit で触り直してから確かめを頼んだ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-04T10:40:00Z — スキーマ名を DSL に書くことが Forbidden の「接続情報」に当たらないという読み（D1）と、同じ値での保存・同じ名前への変更を M4 の拒否に含めた読み（D6）を承認の場で確かめる。
