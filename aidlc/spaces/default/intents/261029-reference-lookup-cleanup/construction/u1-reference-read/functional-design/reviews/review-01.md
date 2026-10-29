## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-29T22:24:01Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md > BR7.3・BR11.2、functional-spec.md > 4.2・5 | 「値は切らない」としているが、既存の RowSqlBuilder.listSelection は主キー以外の列を LIST_TEXT_LIMIT（100）で SQL の側で切って選ぶ（backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlBuilder.java の 205〜218 行付近）。valueColumn は主キーではないことが多く、ReferenceReadSpec の項目（出すカラム・NULL の除外・件数・読む行・読み飛ばす数）には「この列は切らずに選ぶ」指定が無い。このままだと文字の valueColumn が 100 文字で切れ、選んだ値の保存や確かめ（BR9.4）と食い違う | ReferenceReadSpec に、値のカラムを切らずに選び、ラベルと一覧の列だけを切る指定を足し、RowSqlBuilder の選び方との対応を書く。値が 100 文字を超える参照先の境界のテストを BR7.3 の確かめに入れる | New |
| R-02 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md > BR8.4・BR8.1 | 「各問い合わせの上限は min(REFERENCE の上限, 残りの時間)」としているが、既存の接続の貸し出し（TargetReadSession）は借りたときに目的ごとの秒数を1つ決める作りで、RowSqlExecutor は文ごとに session.queryTimeoutSeconds() をそのまま使う。定義では接続を1回借りて REFERENCE を順に読む（BR8.2）ため、文ごとに残りの時間へ絞る手段が設計に無い。全体の上限は REFERENCE の数×1回の上限まで超えうる。秒の単位なので 1 秒未満の残りの丸めも決まっていない | 文ごとの上限を渡す口（search への引数か session の上限の差し替え）をどこに足すかを決めて書く。秒の丸め（切り上げ・残りが 1 秒未満なら読まず 503）も決める。NFR 設計に回すなら 10節の表に仕組みの置き場を明記する | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md > 4.2・5、rules.md > BR4.2・BR5.8・BR11.2 | 並び（ラベル→値→主キー、NULL は最後）を既存の view.orderKeys とは別に組む必要があるが、ReferenceReadSpec の項目に並びの指定が含まれていない。既存の search は並びを利用者の sortKey から作る | ReferenceReadSpec に並びの指定を含めるか、固定の並びを作る箇所を書く | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md > BR6.3・BR9.4・BR4.4 | 文字の valueColumn の一致は DB の照合順序（MySQL・MariaDB は大文字小文字を区別しないことが多い）で決まる一方、選択肢の重複除去はアプリの文字どおりの一致で決める。そのため ABC しか無い参照先で abc の確かめが FOUND になり、保存が外部キーの制約で後から失敗しうる | この動作を決めた側の動作として明示し、3種類の DB の結合テストに大文字小文字の違う値の確かめを入れる | New |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md > BR3.3 | 入力できない参照元のカラムを NO_PERMISSION で返すと、権限の不足と入力不可が同じ状態に混ざる。U3 に「入力不可の表示を優先」と注記して頼っている | 承認の場の確認事項 3 として扱い、U3 の設計で区別を失って権限の文言が出ないことを確かめる項目を引き継ぎに足す | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（この段に検証の道具の指定は無く、Read/Grep のみ） | — | 既存のコード（LIST_TEXT_LIMIT=100、MAX_PAGE、ReadPurpose の4値、RowSqlExecutor.search の形）を読んで確かめた。契約 C1・C2・C5・C6 の参照先は contract-summary.md の範囲で矛盾なし |

### Summary

判定の順・権限・漏えい・契約との差（D1〜D6）は整っていて、実装に進める水準である。ただし値の切り捨て（BR7.3 と既存の選び方）と、定義の全体の時間の上限を文ごとに効かせる仕組みの 2 点は、実装で迷うため承認の前に確かめるとよい。
