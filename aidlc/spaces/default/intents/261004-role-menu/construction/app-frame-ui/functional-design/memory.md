<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T14:18:04Z — 5bf1ffe の部品の口は報告ではなくソースで確かめた; 上流のリポジトリで git show を読み取りだけで使い、Sidebar・AppShell・Topbar・CSS を読んだ。報告の記述は data として扱い、口の形（navSections・Set<string> の開閉・current・labels）と data-testid の変化をソースの行で裏付けた。
- 2026-10-05T14:18:04Z — 業務のメニューの今の項目は道の引数 item で押した項目にする（Q3 A）; DSL は同じテーブルを複数の項目が指すことを禁じず、aria-current は1つに絞るのが望ましい。item が当たらないとき（ブックマーク・DSL の適用し直し）は行きがけの順で最初の項目にする。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T14:18:04Z — 画面の段の道 /tables/{スキーマ}/{テーブル} を /tables?schema=&table=&item= に替えた; 道の中の %2F などが Spring Security の要求の検査で 400 になり、直接開く・再読み込みで画面を返せないため。functional-spec.md 7節 (b) に差として記録した。
- 2026-10-05T14:18:04Z — mockups 1.1a のサイドバーの文と NavTree の error の［もう一度］をサイドバーに置かない; 5bf1ffe の navSections は項目しか描けないため、案内と［もう一度］はホームの画面に置いた（Q1 A）。7節 (a)・(d) に記録した。
- 2026-10-05T14:18:04Z — features/auth（ログアウトの道 /logout）に手を入れる; U7 の持ち物の外だが、U6 の S4 の保存していない変更の確かめをログアウトにも当てるため（Q6 B）。7節 (e) に記録した。
- 2026-10-05T14:18:04Z — 10a 節の 768px 未満の重ねたサイドバーは部品に無い; e82b651・5bf1ffe とも AppShell の CSS に @media が無く、畳むか開くかの2つだけのため、360px のはみ出しを axe の検査で確かめる形にした（7節 (c)）。
- 2026-10-05T14:40:22Z — 承認の場の決定「推奨の案のとおり直す」で R-01〜R-03 と S8 の道を直した; 作業ロールの Dropdown を work-role-switcher の入れ物で包み既存の E2E の開き口の探し方を B9 で書き換える（R-01）、開閉の照らし合わせを木を読み終えたときだけにし祖先は木が届いたときにも足す（R-02）、ロールが1つの説明を押せない項目の description にした（R-03）。S8 の道は問い合わせの引数の形の例で書き、role の確定の形に合わせる（functional-spec.md 11節）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T14:18:04Z — 画面の道が変わるたびに作業ロールと業務のメニューを読み直す（Q2 A）; 管理者の変更が次の画面の移動で表示に出る代わりに、移るたびに要求が2本増える。どちらも 1 秒の目標の軽い API で、利用者は 50 名程度のため受け入れた。
- 2026-10-05T14:18:04Z — make-you-chic-ui の残り3点は追加で依頼し、待たずに 5bf1ffe で進める（Q4 A）; 畳んだ状態の aria-labelledby は axe の違反になりうるが未検証で、違反なら B9 の時点で依頼者に諮る。依頼文は make-you-chic-ui-request-2.md。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T14:18:04Z — S8 の木の道（U4 の /api/me/permissions/schemas/{schemaName}/...）は要求の検査に当たる名前で 400 になりうる; navigation は引数の形にしたが契約 C8 は変えていないため、S8 では読み込めない表示になる（7節 (j)）。U4 の道を直すかは承認の場で確かめる。
- 2026-10-05T14:18:04Z — MyPermissionNode の displayName の形と create・delete の型は契約 C8 で決まっていない; 画面は {ja, en} と真偽値を仮に置き、U4 の確定の形に合わせてコード生成で直す（frontend-components.md 2節）。
