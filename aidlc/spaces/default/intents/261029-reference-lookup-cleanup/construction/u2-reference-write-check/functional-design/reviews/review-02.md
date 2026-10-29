## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-29T23:29:41Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md > 10 節 U1 の H1・H2 と BR4.3 | U2 の保存前の確かめは、U1 の `check` に 2 つの変更を求めて成り立つ。(1) 文字どおり一致の比べ（BR4.3）。(2) 必須の参照項目の件数判定（BR3.2 の TOO_MANY）。しかし U1 の設計は逆を決めている（BR9.4 は 1 行読んで 1 行以上なら FOUND、BR8.6 は確かめの読む行を 1 に固定、BR9.6 は件数を判定しない）。U2 は「決まるまでは安全側」とするが、MySQL・MariaDB で `ABC` と `abc` が両方ある参照先を `abc` で引くと、1 行だけ読んだ行が `ABC` だった場合に、実在する完全一致の値を拒否してしまう。Q2: A が約束する「画面で選んだ値はいつも通る」と食い違う。U1 の契約が未確定のまま U2 が頼っており、開発者は推測せずに実装できない | U1 の `check` の入出力（一致を型つきの値で比べる位置・読む行数・件数の状態を返す口）を、U1 の承認の場の変更として確定するか、U2 の設計に「U2 の `check` 呼び出しが使う口」として具体の形（読む行数・同じ値の行が重なる場合の読み方）を書く。暫定の「見つからなければ NOT_FOUND_OR_DENIED」で実在の値が拒否される場合があることを、仕様に明記する | Resolved (reviewer) |
| R-02 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md > BR3.1・BR3.3〜BR3.5・BR2.3 | 必須の参照項目の「空」に空文字列 `""` を含める（BR3.1）一方、文字型では `""` は値として確かめに回る（BR2.3）。そのため、文字型の NOT NULL の参照で本文が `""` のとき、NO_PERMISSION・TOO_MANY では NOT_EDITABLE_NOT_NULL の 409 になる一方、AVAILABLE では BR3.5 が「既存の流れのまま確かめ・保存へ」と曖昧に書くのみで、`""` を確かめるのか空として 400 にするのかが決まらない。状態によって同じ `""` の扱いが割れる | 文字型の `""` の扱いを 1 つに決める（値として確かめる、または空として扱う）。BR3.1 の EMPTY の定義と BR3.5 の分岐をその決定に合わせ、`""` の 3 状態（NO_PERMISSION・TOO_MANY・AVAILABLE）の期待結果を表で示す | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md > 9 節・4.1 手順 5 | 作成のたびに REFERENCE の必須項目ごとに 201 行まで読む。同じ参照先を指す項目が複数あっても 1 回に束ねる決まりが無く、U1 の BR4.7（要求内で同じ参照先は 1 回）に相当する決まりが U2 に無い | NFR 設計で、同じ参照先の件数・確かめの読み取りを束ねるか、束ねない理由を決める | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md > 2 節・8 節 D4 | 409 の `NOT_EDITABLE_NOT_NULL` は field にカラム名を入れる。既存のコードは同じ reason を field `""` で返しており（`BusinessDataWriteService` の NOT NULL 違反の写し）、同じ reason で field の形が 2 通りになる。U3 が見分けることは 10 節に書かれているが、既存の画面・テストへの影響範囲は書かれていない | 既存の `NOT_EDITABLE_NOT_NULL` を使う画面・テストを洗い出し、field が空の場合とカラム名の場合の両方を扱うことを、コード生成の計画の影響範囲に入れる | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/traceability.json > coverage | functional-spec.md の 1 節と 9 節、unit-of-work-story-map.md は、US3.5 の保存前の確かめの接続の数と時間の上限（AC3.5.1・AC3.5.2・AC3.5.3）を U2 が部分で持つとしている。しかし upstream_ids と coverage にこの 3 件の行が無い | AC3.5.1〜AC3.5.3 を部分として行に足す（target は BR4.1・BR4.2・BR4.5・BR1.2）。AC3.5.4 の場面 (3) は Performance Validation への引き継ぎとして Deferred で書く | New |
| R-06 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md > 4.3 節「NULL を許すカラム」の表 | この表は `TOO_MANY` の列を持つが、U2 が `countState` を呼ぶのは必須の参照項目だけ（BR3.2・BR4.7）。NULL を許すカラムでは TOO_MANY の状態は決まらない。注記は「LOOKUP は TOO_MANY にならない」と D5 だけで、実装者がこの列を実装すべき状態と読む余地がある | NULL を許すカラムの表では TOO_MANY の列を NO_PERMISSION でない場合の列に統合するか、「件数は読まない。この列は参考」と明記する | New |

> R-01 Reviewer note: functional-spec.md 10.1 節と D7 が U1 の `check`（200 行まで読み、型つきの完全一致で FOUND）と `countState` の入出力を確定した。U1 の rules.md の BR9.4・BR9.7・BR7.6 と一致している。BR4.3 は U2 が結果をそのまま使う形で、暫定の扱いは残っていない

> R-02 Reviewer note: BR2.3 が `""` の扱いを「⑨ の結果に従う」の 1 つに決め、BR3.1・BR3.5 も同じ bodyState で揃っている。4.3 節に、必須の参照項目と NULL を許すカラムのそれぞれで 3 状態の期待結果の表がある

> R-03 Reviewer note: BR4.7 が同じ参照先の `countState` を要求 1 件の中で 1 回にまとめると決めた。値の違う `check` を束ねるかは 9 節で NFR 設計に回してあり、範囲として妥当

> R-04 Reviewer note: 10.3 節に `NOT_EDITABLE_NOT_NULL` の既存の使いどころの一覧がある。backend の `BusinessDataWriteService`・`BusinessDataWriteServiceTest`・`AbstractBusinessDataWriteIT` と、frontend の `problemMessage.ts`・`messages.ts`・各テストを grep で確かめ、一覧と一致した（漏れなし）

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（このパスはファイルの読み取りと grep のみ。ツールの指定なし） | — | U1 の rules.md・functional-spec.md の突き合わせと、既存コードの `NOT_EDITABLE_NOT_NULL` の grep で代えた |

### Summary

前回の 4 件はすべて解消した。U1 との継ぎ目（`check` の厳密な一致と `countState`）は両文書で一致し、`""` の扱いも表で追える。残るのは traceability の抜けと 4.3 節の表の紛らわしさの Minor 2 件だけで、実装者が推測なしに進められる。
