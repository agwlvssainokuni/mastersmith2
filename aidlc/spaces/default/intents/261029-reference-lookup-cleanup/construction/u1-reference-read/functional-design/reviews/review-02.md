## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-29T23:28:08Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md > BR7.3・BR11.2、functional-spec.md > 4.2・5 | 「値は切らない」としているが、既存の RowSqlBuilder.listSelection は主キー以外の列を LIST_TEXT_LIMIT（100）で SQL の側で切って選ぶ（backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlBuilder.java の 205〜218 行付近）。valueColumn は主キーではないことが多く、ReferenceReadSpec の項目（出すカラム・NULL の除外・件数・読む行・読み飛ばす数）には「この列は切らずに選ぶ」指定が無い。このままだと文字の valueColumn が 100 文字で切れ、選んだ値の保存や確かめ（BR9.4）と食い違う | ReferenceReadSpec に、値のカラムを切らずに選び、ラベルと一覧の列だけを切る指定を足し、RowSqlBuilder の選び方との対応を書く。値が 100 文字を超える参照先の境界のテストを BR7.3 の確かめに入れる | Resolved (reviewer) |
| R-02 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md > BR8.4・BR8.1 | 「各問い合わせの上限は min(REFERENCE の上限, 残りの時間)」としているが、既存の接続の貸し出し（TargetReadSession）は借りたときに目的ごとの秒数を1つ決める作りで、RowSqlExecutor は文ごとに session.queryTimeoutSeconds() をそのまま使う。定義では接続を1回借りて REFERENCE を順に読む（BR8.2）ため、文ごとに残りの時間へ絞る手段が設計に無い。全体の上限は REFERENCE の数×1回の上限まで超えうる。秒の単位なので 1 秒未満の残りの丸めも決まっていない | 文ごとの上限を渡す口（search への引数か session の上限の差し替え）をどこに足すかを決めて書く。秒の丸め（切り上げ・残りが 1 秒未満なら読まず 503）も決める。NFR 設計に回すなら 10節の表に仕組みの置き場を明記する | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md > 4.2・5、rules.md > BR4.2・BR5.8・BR11.2 | 並び（ラベル→値→主キー、NULL は最後）を既存の view.orderKeys とは別に組む必要があるが、ReferenceReadSpec の項目に並びの指定が含まれていない。既存の search は並びを利用者の sortKey から作る | ReferenceReadSpec に並びの指定を含めるか、固定の並びを作る箇所を書く | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md > BR6.3・BR9.4・BR4.4 | 文字の valueColumn の一致は DB の照合順序（MySQL・MariaDB は大文字小文字を区別しないことが多い）で決まる一方、選択肢の重複除去はアプリの文字どおりの一致で決める。そのため ABC しか無い参照先で abc の確かめが FOUND になり、保存が外部キーの制約で後から失敗しうる | この動作を決めた側の動作として明示し、3種類の DB の結合テストに大文字小文字の違う値の確かめを入れる | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md > BR3.3 | 入力できない参照元のカラムを NO_PERMISSION で返すと、権限の不足と入力不可が同じ状態に混ざる。U3 に「入力不可の表示を優先」と注記して頼っている | 承認の場の確認事項 3 として扱い、U3 の設計で区別を失って権限の文言が出ないことを確かめる項目を引き継ぎに足す | Resolved (reviewer) |

> R-01 Reviewer note: rules.md の BR7.3 と BR11.4 が、ReferenceReadSpec の valueColumn を keyExpression・上限なしで切らずに選び、ラベルと一覧の列だけを selectExpression・LIST_TEXT_LIMIT で選ぶと明記した。valueColumn がラベルを兼ねるときの扱いと、100 文字超（101 文字・100 文字ちょうど）の 3 種類の DB の境界テストも書かれている。

> R-02 Reviewer note: BR8.4 に、文ごとの上限を渡す置き場（ReferenceLookup が TargetReadSession を作り直す）と、残り秒の切り上げ、1 秒未満なら流さず 503 TIMEOUT にすることが書かれた。既存の TargetReadSession は 4 引数の正式なコンストラクター（product・connection・queryTimeoutSeconds・brokenMark）を持ち、同じ brokenMark を共有できることをソースで確かめた。全体の上限の値は NFR 設計に回してあり、決定どおり。

> R-03 Reviewer note: BR11.4 と entities.md の ReferenceReadSpec.order が、ラベル → 値 → 主キーの固定の並びを持つ。ReferenceLookup が純粋な関数で作り、利用者の sortKey と view.orderKeys からは作らないことも明記された。BR4.2・BR5.8 も BR11.4 を参照している。

> R-04 Reviewer note: BR7.6 の型つきの厳密な一致（sameReferenceValue）で check（BR9.4）と重複除去（BR4.4）をそろえた。ラベルの 1 件読みは DB の比べ方に従うことが BR6.3 に明示され、3 種類の DB で大文字小文字・末尾の空白・小数のテストを置く。

> R-05 Reviewer note: NO_PERMISSION を残したまま、BR3.3・functional-spec.md の 11 節・12 節に、U3 が editableOnCreate・editableOnUpdate を先に確かめて権限の文言を出さないという引き継ぎが入った。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| ソースの確認（TargetReadSession） | 4 引数の public record と brokenMark の共有を確認 | BR8.4 の「絞った session を作り直す」案は既存の型を変えずに実現できる |
| traceability.json の参照 | BR7.6・BR8.4・BR9.7・BR11.4 が載っている | 追加した決まりは対応表から漏れていない |

### Summary

5 件の既存の指摘はいずれも成果物に反映されている。今回の変更（check の厳密な一致・countState・valueType・固定の並び）は rules.md・entities.md・functional-spec.md の間で整合しており、実装に必要な判断は残っていない。新しい指摘は無い。
