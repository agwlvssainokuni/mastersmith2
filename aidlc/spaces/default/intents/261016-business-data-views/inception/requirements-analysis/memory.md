<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-18T01:00:00Z — 依頼文の J・K は参考資料の要件のドラフトの 9.3 の Intent J（検索＋一覧）・K（詳細・編集）と読み、2.3〜2.9・4章・5章・6章・7章を読んだ。権限の当て方（5.2〜5.5）・メニューの入口・持ち越しの直しの進め方は依頼文・前の Intent の要件・team.md で決まっているとして質問にせず冒頭に書き、質問は範囲・F2・検索・ページ・監査・同時の更新・ビュー・書式・検証・403/404・memory.peak の 11 問にした。classic の範囲で scope-definition が SKIP のため、依頼文の「F2 は範囲を決める段で決める」を要件定義の Q2 で尋ねた（答え A: 入れない）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-18T00:40:00Z — 承認の場の Request Changes の後に要件を直したが、advisory のレビューは1回までのため2回目の確認を頼めず、直した後の承認の場も「確認の記録が無い」として開けなかった。段のやり直し（redo jump）で確認のまとめを確かめ直し、直した要件をあらためてレビューに回した（READY）。advisory の段で Request Changes を受けたら、直す前に段のやり直しで進める。
- 2026-10-18T01:00:00Z — Q9（一意を保存の前に問い合わせる、B）と Q6（後の人の値で上書き、B）の組み合わせで、制約の無いカラムでは同時の重なりを防げないため追加の質問 F1 で確かめ、DB の制約の違反も同じ誤りに直して返し、重なりは既知の制約にする形（A）になった。答えの記録は、前の段の学びのとおり4問ずつの返事ごとに全問を1回で記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-18T01:00:00Z — 範囲を J・K から他テーブル参照の選択肢（REFERENCE）と参照ピッカー（LOOKUP）を除いた形にした（Q1: A）。作成・更新・削除まで入るため、対象DB の書き込みの接続・境界テストの変更・監査の仕組みが今回に入る代わりに、参照の子画面を後の Intent に回せる。そのカラムは作成・更新で文字の入力として扱う前提（A3）にした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-18T01:00:00Z — 主キーの値を道に置くか・トレースの道の伏せ方、拒否した操作を監査に残すか（A5）、pattern の時間の上限と読む行数の上限の値、080 の違反が Topbar のときの依頼（5）の中身は、設計の段で決める。
