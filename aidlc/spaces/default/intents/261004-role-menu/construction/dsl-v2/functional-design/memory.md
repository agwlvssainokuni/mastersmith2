<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T23:32:42Z — mockups.md の「木と表に1段足す」をメニューの木の括弧と違いの表の見出しの行と読んだ; メニューの木は menus を描きスキーマの節を持たないため、そのままでは実装できない。Q6: A でこの読み方を確かめた。
- 2026-10-04T23:32:42Z — 起動時の深すぎる枝は子ごと落とし、空になったまとまりはさかのぼって落とす; 既存の DslMenuItem の「テーブルか子を持つ」決まりを保つため。WARN は1件で識別の先頭12文字と落とした数と上限だけを出す。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T23:32:42Z — 契約 C3 と3点違う形にした; スキーマの表示名を ja・en の label に、安全な読み込みの上限に展開後の節の数を足し、位置を引く口を dsl.service の型にした。承認済みの契約は書き換えず functional-spec.md の 9節に差を書いた。
- 2026-10-04T23:32:42Z — ストーリーに無い場合を2つ足した; 版を上げる前のプレビューの読み直し（Q4: A）と今の状態の appliedUnreadable（Q5: A）。どちらも今のコードのままでは 500 や誤解を招くと分かったため。
- 2026-10-04T23:32:42Z — 前の Intent の BR5.3（生成した DSL にスキーマ名を書かない）を覆した; D1 と Q7: A のとおり3種類の DB ともスキーマ名を書く。スキーマ名が無いことを確かめる既存のテスト4つを書き換える。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T23:32:42Z — スキーマの数は意味の検証で1つに絞り、名前の違いは照合の警告にした（Q2: A）; dsl が対象DB の設定を知らずに済み結果が設定の有無に依らない代わりに、名前の違う DSL も適用できてしまう。後で複数を受け付けるときは決まりを緩めるだけで済む。
- 2026-10-04T23:32:42Z — 外部キーと選択肢の参照は同じスキーマの中の名前のままにした（Q1: A）; 書く量と既存の検証の変更が少ない代わりに、スキーマの間の参照は後で項目を足して広げる必要がある。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-04T23:32:42Z — dsl-schema-v1.json の公開を止めると、エディターで v1 の URL を参照している利用者の補完が切れる; 版 1 は受け付けないため置かない形にしたが、README の案内で足りるかを Code Generation で確かめる。
- 2026-10-04T23:32:42Z — 負荷の試験の道具（perf の DSL を作るスクリプト）の版 2 への書き換えが B1 の作業の量に入る; Code Generation の計画で見積もる。
