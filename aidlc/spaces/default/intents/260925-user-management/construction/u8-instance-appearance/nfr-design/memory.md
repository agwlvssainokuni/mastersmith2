<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T06:15:03Z — 質問は order の割り当て（Q1）と接続を借りないことの結合テストの作り（Q2）の2問にし、ほかは NFR 設計の要点（案）12 件として要約で確かめる形にした; 性能・拡張性・信頼性・観測性の設計は承認済みの NFR 要件（NFR4.1〜NFR9.8）と機能設計でほぼ決まっていると読んだ。候補の3つ目の判定の関数の置き場は、機能設計の1節が domain を作らないと決めているため service とし、質問にしなかった。
- 2026-09-27T06:15:03Z — 既存の SecurityRuleContributor の説明文の「U2 は 100 台、U3 は 200 台」は前の Intent の単位（auth 110・access 210）を指すと読んだ; AuthSecurityContributor・AdminSecurityContributor の説明文が同じ番号を使っているため。この Intent の u2・u3 と名前が重なることが NFR 要件の R-01 の原因で、Q1 A で機能の名前の割り当てに書き直す案にした。
- 2026-09-27T06:15:03Z — 既存のテストの決まりが order 100・150・200・250 を使っていることを確かめ、U8 の値の候補から外した; 同じ文脈に入ると SecurityExtensionValidator で起動が止まるため。本番は x10、テストは x00・x50 の慣習と読んだ。
- 2026-09-27T09:54:53Z — jqwik とカバレッジ（NFR9.7・NFR9.8）の設計は logical-components.md の5節に置いた; 5つの設計の分類のどれにも自然に当たらず、テストの置き場は部品の一覧と一緒に見る方が分かりやすいため。reliability-design.md からはその節を参照した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T09:54:53Z — Q1 A で order を機能の名前の割り当てに変え、u3-invitation の 310 を U8 の段で先に決めた; 承認済みの U3 の機能設計・NFR 要件は「コード生成で決める」としており、差を security-design.md の8節に記録した。U3 の NFR 設計・コード生成への引き継ぎが要る。
- 2026-09-27T09:54:53Z — SecurityRuleContributor の説明文の変更が「U8 の範囲の書き足し」から「割り当て全体の書き直し」に広がった; common.security は packagesJudgedByTotal にあるため、team.md の「手を入れる Bolt では一覧から外す」に当たるかを、書き直す Bolt の計画で依頼者に確かめると security-design.md の8節と logical-components.md の5節に書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T06:15:03Z — NFR5.2 の確かめは、接続を借りた回数の比べ（Q2 A）を推奨とし、プールの一時停止の再現（Q2 B）を選択肢に残した; 借りた回数が増えなければ一時停止で待たされることも無く、同じ文脈で安く確かめられる。一時停止の再現は心配の場面そのものだが、MBean を有効にした専用の文脈でテストの時間が増える。
- 2026-09-27T09:54:53Z — 借りた回数の比べは同じ文脈のほかのテストの要求が混ざると誤って失敗しうる; reliability-design.md の3.2節に、比べる前に待ち合わせてほかの要求が混ざらない形にすることを書き、具体はコード生成に残した。指標が取れないときの数える包みへの切り替えも同じくコード生成で記録する。
- 2026-09-27T06:15:03Z — Q1 A は U8 の段で u3-invitation の order（310）まで割り当てる案にした; 作る順（B3 → B4）どおりの並びで重なりを先に防げる代わりに、U3 の承認済みの「U3 のコード生成で決める」を U8 の決定で先に埋める形になるため、U3 の NFR 設計・コード生成への引き継ぎを明記した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T06:15:03Z — SecurityRuleContributor の説明文の書き足しは packagesJudgedByTotal にある common.security の変更になる; 承認済みの NFR9.7 はコードの中身を変えない扱いとしているが、team.md の「手を入れる Bolt では一覧から外す」を説明文だけの変更にも当てるかは明記されていない。書き足す Bolt（Q1 A なら先に作る U3 の B3 になりうる）の計画で扱いを確かめる。
