<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T13:50:00Z — team.md の★「一覧に無い icon を拒否するか既定にするか」は要件で決まっていると読んだ; FR9.6・AC5.1.7 が「既定のアイコンにする」と書いているため、質問にせず「決まっていること」に置いた。サーバーは list に置き換え、拒否しない（BR4.2）。
- 2026-10-05T13:50:00Z — AC5.1.14（DSL に無いテーブルを指す項目）は、絞る関数を単独で確かめるための入力と読んだ; dsl-v2 の BR1.6 で適用中のモデルの組は必ず DSL にあるため、本番の流れでは起きない。解決の口が NONE を返して落ちる形で、特別な分岐は持たない（BR2.6）。
- 2026-10-05T13:50:00Z — 置き場の問い合わせの「その権限を持たない 403」は、要るテーブルの READ だけを欠く利用者で確かめると読んだ; 2本ともログインだけの API で管理者の印の組は無いため、team.md の「要る権限だけを欠く利用者」をテーブルの単位に当てた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T13:50:00Z — 契約 C9 の形を Q1〜Q5 の答えで変えた（表示名を {ja, en}、id と emptyReason を足す、置き場の道を引数に、アイコンの一覧をファイルに）; 承認済みの contract-summary.md は書き換えず、functional-spec.md の8節に差の表を置いた。型と道の変更は U7 の機能設計で受け入れを確かめる。
- 2026-10-05T13:50:00Z — 置き場の問い合わせに 400 VALIDATION_FAILED（引数が無い・空・257 文字以上）を足した; 契約 C9 には 200 と 403 しか無いが、引数で受ける形（Q4 A）にしたため入力の検証が要る。長さの上限 256 はまとめの確認の要点 SP 6 の例の値をそのまま使った。
- 2026-10-05T14:20:00Z — 承認の場の Request Changes（推奨の案のとおり直す）で、置き場の名前の長さの上限 256 をやめた（R-01）; DSL の名前に長さの上限が無く、メニューに出るのに開けないテーブルができるため、無い・空だけを 400 にした。あわせて契約 C9 との差の表に 400 と BR4.3（一覧のファイルの誤りで起動を止める）を足した（R-02）。functional-spec.md の10節に記録した。
- 2026-10-05T13:50:00Z — 依存の先が components.md（dsl・role）より広い（access.domain・auth.domain）; ACCESS_DENIED の code と要求の文脈の主体を読むためだけで、useradmin と同じ使い方。まとめの確認（SP 2）で承認済み。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T13:50:00Z — 項目の id を絞る前の DSL の木での位置の道にした（Q2 A）; 作業ロールを切り替えても同じ項目の id が変わらず開閉の状態が保たれる代わりに、DSL を適用し直すと id が変わり、開閉が閉じに戻りうる。前置きは U7 が付ける。
- 2026-10-05T13:50:00Z — current() と写しの DSL の識別が食い違っても読み直さない（BR1.5）; 読み直しの手間を省ける代わりに、適用の直後の1回は古いメニューの形が返りうる。新しい DSL に無いテーブルは写しが NONE を返して落ちるため、権限の無い項目は出ない。
- 2026-10-05T13:50:00Z — 一覧のファイルと画面の一覧の一致のテストを、ファイルを作る U5 の B7 で frontend/src/app/registry/ の下に置くことにした（Q5 B）; service の単位が画面のテストを1件持つことになるが、ファイルと同じ Bolt で一致を関門に入れられる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T13:50:00Z — 画面の道 /tables/{スキーマ名}/{テーブル名} が要求の検査で 400 になりうる; 名前に /・%・..・; があると、直接開く・再読み込みで index.html の前に REQUEST_REJECTED になる。AC5.1.8・AC5.1.13 に関わり、U7 の機能設計で道の形を決める必要がある（functional-spec.md 9節）。
- 2026-10-05T13:50:00Z — 同じテーブルを指す項目が複数あるときの今の項目と、label が空のときの既定の表示は U7 が決める; dsl-v2 は同じ組の重なりを禁じておらず、表示名の空の文字列も許すため。
- 2026-10-05T13:50:00Z — 一覧のファイル（.txt）のライセンスヘッダーの検査の対象にするかはコード生成の計画で決める; # のコメント行でヘッダーを書き、読むときに飛ばす形にした。
