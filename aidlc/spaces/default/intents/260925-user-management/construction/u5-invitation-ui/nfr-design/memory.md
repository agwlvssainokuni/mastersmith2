<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T10:49:35Z — Table の足りない口と 400 の fieldErrors は質問にしなかった; NFR9.5 と機能設計の 6.2（項目ごとの誤りの形に頼らない）で作りが1つに決まるため。要点 7・9 に書き、まとめの確認で確かめる。
- 2026-09-27T10:49:35Z — 承認の場の Minor R-01（取り消しの 204）を画面の側にも当てた; 「成功の本文を JSON として読み、読めなければ通信の失敗」を一覧・招待・送り直しだけに当て、取り消しは本文を読まずに成功とする形を要点 7 に書いた。

- 2026-09-27T12:06:21Z — Q3 A（ログインの画面から開く）を画面の開き方だけに当て、測定の準備の招待の API にはログインの API のトークンを使うと読んだ; Q2 A が API で 21 件を置くとしており、準備は画面の操作ではないため。performance-design.md の上流との差に記録した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T12:06:21Z — 質問の案で「既存のリンタの決まりを緩めない」としかけた console の扱いを、テストの見張りで確かめる形に直した; frontend/.oxlintrc.json に console を止める決まりが無いと確かめたため。決まりを足すとほかの機能に及ぶので足さない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T12:06:21Z — 警告の状態は、同じコンテキストで差し替えの答えを替えてページを読み込み直して出す形にした; ログインを1組1回に抑えられる代わりに、Cookie のリフレッシュトークンの復元の道に頼る。
- 2026-09-27T12:06:21Z — 招待を使える設定が無いときは測定を test.skip にして理由を注記に残し Unverified で引き継ぐ形にした; 目標を緩めずに済む代わりに、infrastructure-design が E2E の WAR に設定を渡すまで画面の時間は測れない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T10:49:35Z — 検査で一覧の行と警告の状態をどう出すか（Q1）; E2E の WAR は1つを共有し招待を使える設定にするはずで、本物の警告の状態は出せないため、page.route の差し替え（U6 の Q2: B と同じ）を推奨にした。依頼者の答えを待つ。
- 2026-09-27T10:49:35Z — 測定の 21 件の用意と測る場（Q2）とログインの開き方（Q3）; リフレッシュトークンは使うたびに作り直されるため storageState を使い回せず、コンテキストごとのログインが要る。測定は本物の一覧を読むため、E2E の WAR の招待の設定（infrastructure-design）に頼り、無ければ Unverified とする案にした。
- 2026-09-27T12:06:21Z — Q1〜Q3 はすべて A、まとめは Looks correct で答えが出た; B5 の検査のファイルの番号・初期管理者の設定を変えない方針・共用の手伝いの形は、B5 のコード生成の計画で U6・U7 とそろえて決める（logical-components.md の5.1）。
