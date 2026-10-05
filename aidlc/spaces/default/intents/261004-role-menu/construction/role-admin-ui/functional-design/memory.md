<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T13:47:26Z — ui の単位の traceability の target は BR ではなく functional-spec.md の D1〜D32 と流れの番号を指した; rules.md を作らない単位のため、BR を書くと sensor が rules.md に無い id として扱う。前の Intent の ui の単位（user-admin の U5）も functional-spec の節と D の番号を指していた。
- 2026-10-05T13:47:26Z — US5.3 の AC のうち、この単位が持つのは新しい管理の画面の登録だけと読んだ; AC5.3.1・AC5.3.2 を「部分」で OK にし、サイドバーの組み立て・見出し・出し分けの AC は U7 app-frame-ui へ Deferred にした。
- 2026-10-05T13:47:26Z — 詳細の画面は道でタブを決める形にした（/admin/roles/:roleId と .../assignments）; 拒否の文言・S6・S9 から割り当てのタブへ直接移れるようにするため。引数つきの道の登録は今のアプリで初めてになる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T13:47:26Z — 契約 C7 に無い GET /api/admin/roles/{roleId} を足す前提で設計した; Q1 A。role の機能設計は承認の場の Request Changes で直してもらい、それまでは「直した後の形」として functional-spec.md の 9節 (a) と 10節に記録した。
- 2026-10-05T13:47:26Z — S9 は mockups の「利用者の詳細」ではなく、行の操作の「ロールを見る」と読み取りの Modal にした; 利用者の管理に詳細の画面が無かったため（Q5 A）。表の列と既存の操作は変えない。
- 2026-10-05T13:47:26Z — AC2.2.9 は条件つきの確かめにした; 管理の API は他人の作業ロールを返さず、FR6.2 の線引きを保つため（Q4 A）。差は functional-spec.md 9節 (c)。
- 2026-10-05T14:30:00Z — 承認の場の Request Changes（推奨の案のとおり直す）で、R-01 と権限の木の道を直した; GET /api/admin/roles/{roleId} が role の設計に入り前提が確定になった。権限の木の API は名前を問い合わせの引数で受ける形に変わり、role の直しが読めなかったため例の形で書いて確定の形に合わせると functional-spec.md 13節に記録した。
- 2026-10-05T13:47:26Z — design-system-mapping.md の「Table に行の開閉が無い」は今の固定先と違った; e82b651 の Table は renderDetail を持つが、開閉のボタンの名前が全行で同じでページ送りが常に出るため、S7 は素の table のままにした（9節 (e)）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T13:47:26Z — AC1.2.15 を満たすためルーターを data router に替える（Q2 B）; 画面の外の移動も止められる代わりに、B8 で骨組みの入口 main.tsx に手が入り、U7 の作業と同じ場所になる。コード生成の最初に確かめ、だめなら画面の中の移動だけに狭める（7.3）。
- 2026-10-05T13:47:26Z — 誤りの一覧・ファイルの保存・候補の Modal・名前の Modal は機能ごとに持ち、dsl・ほかの機能から import しない; ESLint の制限と dsl に手を入れないためで、似た形の部品が機能ごとに重なる。名前の検査の純粋な関数だけは shared/validation に置く。
- 2026-10-05T13:47:26Z — 候補は1人ずつ足し Modal を開いたままにした（Q3 A）; API と同じ単位で失敗が1件ずつ分かる代わりに、多くの人を足すときは押す数が増える。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T13:47:26Z — 確かめの応答（TransferCheck）は全変更を1回で返すため、大きい YAML で応答と描画が重くなりうる; 画面は 100 件ずつ描くが応答の大きさは減らせない。U4 の NFR 要件（NFR2.4）の論点として引き継ぐ。
- 2026-10-05T13:47:26Z — 詳細の道（/admin/roles/:roleId など）でサイドバーの今の項目をどう示すかは U7 が決める; 前方一致で今の項目とするかを functional-spec.md 11節で引き継いだ。
- 2026-10-05T13:47:26Z — 骨組みのログアウトは道を変える前にトークンを捨てうるため、未保存の確かめが出ないことがある; U7 の設計でログアウトの順を決めるかを 11節で引き継いだ。
