<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T00:00:00Z — Bolt の計画は U3 を先頭にしたが、エンジンの進み方は依存の図の順（根の単位は図に書いた順）で、U1 から始まった。U3 は U4 より前に来る順のため、「U4 の設計の前に対象DB を確かめる」狙いは保てる。計画の並びとの差として、ここに残す。
- 2026-11-03T00:00:00Z — 単位の対応表（unit-of-work-story-map.md）の「関わる単位」が中黒区切りで、網羅の道具に読まれなかった。依頼者の決定で `;` に直し、直しの記録を対応表に残した（U1 の網羅の表が 17 件から 29 件になった）。
- 2026-11-03T00:00:00Z — 質問のファイルの答えの書き込みを、ファイル用の道具ではなくシェルの小さなスクリプトで行った（規則は道具を使うこと。前の段に続く2回目）。次の単位からは編集の道具で行う。
- 2026-11-03T00:00:00Z — U1 のレビュー（READY）の指摘は、その場で直さず承認の場へ持ち越した（単位ごとの確認は1回きり。`project.md` の学び）。Major 2件（ADR-002 によるサーバー側の拒否の読み替えを差の一覧と追跡の target に明記する／端数 7〜9 桁の往復と BR1.13 の矛盾）と Minor 3件。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
