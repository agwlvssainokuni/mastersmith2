<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-03T13:24:57Z [unit u5-menu-icons-home] — 応答の `icon` は DSL の指定と「指定なし・一覧に無い」がどちらも `list` で区別できない。`list` を「指定なし」として種類の既定を当てる読みにした（D13）。応答の `icon` の形を変えない（NFR4.2）ための既知の制約として差の一覧に記録。
- 2026-11-03T13:24:57Z [unit u5-menu-icons-home] — 契約 C5 の `tableKind` の `null` は「フォルダ」だけでなく「DSL にテーブルが見つからない（差し替えの直後）」も指す読みにした（D5）。画面はどちらも「種類の既定に落とす」で扱うため区別は要らない。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T00:00:00Z — Bolt の計画は U3 を先頭にしたが、エンジンの進み方は依存の図の順（根の単位は図に書いた順）で、U1 から始まった。U3 は U4 より前に来る順のため、「U4 の設計の前に対象DB を確かめる」狙いは保てる。計画の並びとの差として、ここに残す。
- 2026-11-03T00:00:00Z — 単位の対応表（unit-of-work-story-map.md）の「関わる単位」が中黒区切りで、網羅の道具に読まれなかった。依頼者の決定で `;` に直し、直しの記録を対応表に残した（U1 の網羅の表が 17 件から 29 件になった）。
- 2026-11-03T00:00:00Z — 質問のファイルの答えの書き込みを、ファイル用の道具ではなくシェルの小さなスクリプトで行った（規則は道具を使うこと。前の段に続く2回目）。次の単位からは編集の道具で行う。
- 2026-11-03T00:00:00Z — U1 のレビュー（READY）の指摘は、その場で直さず承認の場へ持ち越した（単位ごとの確認は1回きり。`project.md` の学び）。Major 2件（ADR-002 によるサーバー側の拒否の読み替えを差の一覧と追跡の target に明記する／端数 7〜9 桁の往復と BR1.13 の矛盾）と Minor 3件。
- 2026-11-03T13:24:57Z [unit u5-menu-icons-home] — 承認済みの画面イメージ（Q7 A・Q8 A）は上流の取り込み前を想定して「管理の区画は 3 項目だけ・業務の既定は取り込むまで今のまま」としていたが、上流の依頼 8 は取り込み済み（固定先 d112795、2aafe750）。許すアイコンの一覧も U1 の統合の時点で 24 個にそろっていた。U5 は 6 項目すべてに付け、3 種類を見分け、ロゴも入れる（Should の部分）。承認済みの文書は書き換えず、functional-spec.md の 6節に差を記録した。Bolt の計画の B6 の「固定先の更新の専用のコミット」も済んでいる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T13:24:57Z [unit u5-menu-icons-home] — 追跡の target を BR ではなく D 番号にした（ui の単位は rules.md を作らない。前の Intent の ui の単位と同じ）。代わりに、追跡の検査（advisory）は「target に BRx.y が無い・rules.md が無い」と failed になる。rules.md を足して D と二重に持つ案は、ずれの元になるため見送った。
- 2026-11-03T13:24:57Z [unit u5-menu-icons-home] — 管理のカードをサイドバーと同じ登録から作る（`selectAdminSidebarItems`）。ホームに専用の一覧を持つ案は、サイドバーとの食い違いの元になるため見送った。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
