<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-10-30T00:00:00Z — 保存前の確かめつきの作成の k6 の場面は、必須の参照項目（NOT NULL、参照先ちょうど 200 行）を1つ持つ専用の参照元の表への作成とし、countState と check の両方を毎回通すいちばん重い道を測る形と読んだ。更新の道は読む量が少ないため場面を置かず、結合テストで接続と時間の上限を確かめる。
- 2026-10-30T00:00:00Z — 全体の上限 10 秒の締め切りは、U1 と同じく ⑩ の最初の読み取りの前に決め、接続を借りる待ちは含めない（最悪約 11 秒＋前の待ち）と読んだ。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-30T00:00:00Z — 機能設計の traceability.json の AC3.5.1〜AC3.5.4 の行の追加は、まずシェルの JSON の書き換えで行い、その後に Edit で触り直した（依頼の「Write・Edit だけ」との差。中身は同じ）。センサーは通過。
- 2026-10-30T00:30:00Z — 承認の場の Request Changes（依頼者の決定: 各指摘の提案どおり）で R-01〜R-05 を直した。新しい ID は足さず、既存の NFR1.22・NFR1.29・NFR2.22・NFR1.32 と「測らない範囲」「後の段に回すこと」を直した。ログの出し場所は、対象DB の例外を結果の型に変える境界の1か所に決めた（NFR1.32 の持ち主に NFR 設計を足した）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-30T00:00:00Z — 確かめは参照ごとに順に行い前の結果を持ち続けない前提で、1要求の確かめのメモリを約 4 MB と見積もった。値の違う check を束ねる形を NFR 設計で選ぶと、1文で読む行が増えて見積もりが変わる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-30T00:00:00Z — 外部キーの無い参照先では確かめと保存の間のすき間を守るものが無い。既知の制約として受け入れる形で書いたため、承認の場で確かめたい。
