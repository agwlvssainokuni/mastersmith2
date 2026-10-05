<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T00:30:00Z — 置き場の問い合わせにもメニューと同じ p95 1 秒を置いた; 要件 NFR2.2 が名指すのはメニューを返す API だけだが、同じ「メニューから画面へ移る」流れの API で、まとめの確認の「決まっていること」で承認されたため。
- 2026-10-06T00:30:00Z — 要求の数の見積もりを、利用者 50 名が 5 秒に1回移る毎秒 10 要求と読んだ; app-frame-ui の Q2 A（画面の移動のたびに読む）を受け、10 VU の閉じた繰り返しがこれを覆うことを NFR2.1 に書いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T00:30:00Z — 置き場の問い合わせは resolve を1回呼ぶ（Q1 A）; 承認済みの機能設計 BR5.2 の「snapshotFor を1回」との差を security-requirements.md と tech-stack-decisions.md の「上流との差」に書いた。拒否の理由ごとの時間をそろえないことは受け入れた制約にした。
- 2026-10-06T00:30:00Z — 読み取りと DSL の適用し直しの重なりの要件を、質問の案の「足す枝番（NFR3.3）」ではなく NFR1.10 で振った; 上流の NFR3 を N/A にしたまま枝番を NFR3 の行に載せると「成果物で定めた枝番がすべて当たる枝番の行に現れる」を満たせないため。中身は「権限の無い項目を出さない」で NFR1.1 の行に当たる。信頼性と観測の要件も同じ理由で NFR1.x・NFR6.x の続きで振り、各成果物の対応表に理由を書いた。
- 2026-10-06T02:00:00Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01 を直し、NFR1.12 と NavigationMenuAccessConsistencyIT を足した; Q1 A でメニュー（snapshotFor）と置き場（resolve）の道が2本になり、移れる項目が 403 になる食い違いを見つけるテストが無かったため。性質ベースにはせず4つの作業ロールと DSL の組の結合テストにし、2つの口の一致のテストを U4 role の B5 へ引き継いだ（security-requirements.md の末尾の節）。
- 2026-10-06T00:30:00Z — 性質ベースのテストの性質を機能設計 BR2.7 の6つから8つにした（id の一意と、権限が違っても同じ id）; 機能設計の再レビューの R-05 を受け、まとめの確認で承認された。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T00:30:00Z — 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる; 読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。
- 2026-10-06T00:30:00Z — k6 は操作ごとに場面を分けた（navMenu・tableAccessVisible・tableAccessDenied・navMenuBaseline）; op のタグに頼らず、iteration_duration の p95 で判定できる代わりに場面の数が増える。場面は時間をずらして順に流す。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T00:30:00Z — 解決の口の読み取りの途中で止める待ち合わせの口の置き場が決まっていない; NavigationConnectionUsageIT と NavigationDslSwapIT が要る。role の RoleBarrier で足りるか、navigation の側の替え物で包むかをコード生成の計画で決める（tech-stack-decisions.md の引き継ぎ）。
- 2026-10-06T00:30:00Z — app-frame-ui の機能設計の質問の案に、置き場の「257 文字以上は 400」という直す前の記述が残っていた; navigation の承認の場の直し R-01 で長さの上限はやめている。U7 の担当への伝達を指揮役に頼んだ。
