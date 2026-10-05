<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T23:32:11Z — 既存の 34 の口の分類を今の安全の決まりから読み、PUBLIC 9・AUTHENTICATED 3・ADMIN 22 とした; /error の2つは Q2 A で PUBLIC に数えた。10 のコントローラーはどれも中の口が同じ分類のため、印はクラスの単位で付ける形にした。
- 2026-10-04T23:32:11Z — team.md の「既存の機能に手を入れたら境界テストを足す」を機能のパッケージに当てると読んだ; access と user に境界テストを足し、common は共通部品として全体の ArchitectureTest に任せる。まとめの確認でこの読みを含めて承認を得た。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T23:32:11Z — 単位の一覧と C1 の「フィルターが受けるログイン」はコードと違った; ログインは AuthController の口で受けており、フィルターだけが受ける入口は無い。ログインの口には PUBLIC の印を付け、functional-spec.md 9節に差として書いた。
- 2026-10-04T23:32:11Z — 契約 C2 の共有の木の props に labels を足し、onToggle を (id, 次の状態) の形にした; どちらも足すだけの互換の変更で、まとめの確認で承認を得た。LoginStateProvider への logout の追加も C2 に無い変更（Q4 A）として差に書いた。
- 2026-10-05T14:38:57Z — 承認の場の決定「推奨の案のとおり直す」で、実行時の検査の主体に管理者を足した（R-01）; AuthenticatedUserToken に admin() が真の AuthenticatedUser を入れた主体で、ADMIN の口は通ることを確かめる。あわせて、実行時の検査の口の集合を本番のクラスだけに絞り、静的な検査と一致させた（R-02）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T23:32:11Z — 実行時の検査を、要求を送らない WebInvocationPrivilegeEvaluator の判定にした（Q1 B）; 監査の行などの副作用が無く、PUBLIC の広げすぎも落とせる。代わりに、今の版で部品が使えるかは確かめておらず、使えなければ MockMvc に切り替える（BR1.5）。
- 2026-10-04T23:32:11Z — icon の実行時の照合の一覧を app/registry に 18 個で持つことにした; make-you-chic-ui の入口は型 IconName を出すが、iconRegistry の値は出していない。固定先を上げてアイコンが増えたら一覧を直す手間が残る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-04T23:32:11Z — MockMvc に切り替えたときの PUBLIC の判定で、口の中の業務の 401（リフレッシュの Cookie が無いときなど）と安全の決まりの 401 をどう見分けるか; コード生成の最初の試しで確かめる。判定の部品が使えれば起きない。
- 2026-10-04T23:32:11Z — navSections に組み立てるときの「ホーム」の置き場と、業務と管理のメニューの id の重なり; U7 の設計で決める。S1 の画面イメージにはホームが無いが、今のコードは先頭に必ず置いている。
- 2026-10-04T23:32:11Z — 分類は3つの値だけ; 後の Intent（J・K）で業務データの API を権限で守るとき、値を足すか、AUTHENTICATED にして中で判定するかを決める必要がある。
