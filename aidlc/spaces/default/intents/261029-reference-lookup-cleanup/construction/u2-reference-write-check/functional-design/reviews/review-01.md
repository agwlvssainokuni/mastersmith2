## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-29T22:44:17Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md > 10 節 U1 の H1・H2 と BR4.3 | U2 の保存前の確かめは、U1 の `check` に 2 つの変更を求めて成り立つ。(1) 文字どおり一致の比べ（BR4.3）。(2) 必須の参照項目の件数判定（BR3.2 の TOO_MANY）。しかし U1 の設計は逆を決めている（BR9.4 は 1 行読んで 1 行以上なら FOUND、BR8.6 は確かめの読む行を 1 に固定、BR9.6 は件数を判定しない）。U2 は「決まるまでは安全側」とするが、MySQL・MariaDB で `ABC` と `abc` が両方ある参照先を `abc` で引くと、1 行だけ読んだ行が `ABC` だった場合に、実在する完全一致の値を拒否してしまう。Q2: A が約束する「画面で選んだ値はいつも通る」と食い違う。U1 の契約が未確定のまま U2 が頼っており、開発者は推測せずに実装できない | U1 の `check` の入出力（一致を型つきの値で比べる位置・読む行数・件数の状態を返す口）を、U1 の承認の場の変更として確定するか、U2 の設計に「U2 の `check` 呼び出しが使う口」として具体の形（読む行数・同じ値の行が重なる場合の読み方）を書く。暫定の「見つからなければ NOT_FOUND_OR_DENIED」で実在の値が拒否される場合があることを、仕様に明記する | New |
| R-02 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md > BR3.1・BR3.3〜BR3.5・BR2.3 | 必須の参照項目の「空」に空文字列 `""` を含める（BR3.1）一方、文字型では `""` は値として確かめに回る（BR2.3）。そのため、文字型の NOT NULL の参照で本文が `""` のとき、NO_PERMISSION・TOO_MANY では NOT_EDITABLE_NOT_NULL の 409 になる一方、AVAILABLE では BR3.5 が「既存の流れのまま確かめ・保存へ」と曖昧に書くのみで、`""` を確かめるのか空として 400 にするのかが決まらない。状態によって同じ `""` の扱いが割れる | 文字型の `""` の扱いを 1 つに決める（値として確かめる、または空として扱う）。BR3.1 の EMPTY の定義と BR3.5 の分岐をその決定に合わせ、`""` の 3 状態（NO_PERMISSION・TOO_MANY・AVAILABLE）の期待結果を表で示す | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md > 9 節・4.1 手順 5 | 作成のたびに REFERENCE の必須項目ごとに 201 行まで読む。同じ参照先を指す項目が複数あっても 1 回に束ねる決まりが無く、U1 の BR4.7（要求内で同じ参照先は 1 回）に相当する決まりが U2 に無い | NFR 設計で、同じ参照先の件数・確かめの読み取りを束ねるか、束ねない理由を決める | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md > 2 節・8 節 D4 | 409 の `NOT_EDITABLE_NOT_NULL` は field にカラム名を入れる。既存のコードは同じ reason を field `""` で返しており（`BusinessDataWriteService` の NOT NULL 違反の写し）、同じ reason で field の形が 2 通りになる。U3 が見分けることは 10 節に書かれているが、既存の画面・テストへの影響範囲は書かれていない | 既存の `NOT_EDITABLE_NOT_NULL` を使う画面・テストを洗い出し、field が空の場合とカラム名の場合の両方を扱うことを、コード生成の計画の影響範囲に入れる | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| (なし) | 実行しなかった | 段の定義に検証ツールの指定は無かったため。traceability.json の AC は、`upstream_ids` と `coverage` が一致していることを読み取りで確かめた。AC1.5.1・AC3.1.7 は Deferred で U3 に回されている |

### Summary

U2 の流れ（⑩ の位置、接続の借り方、監査、拒否の写し）は、既存の `WriteRejection`・`BusinessDataAuditReason`・`BusinessDataFieldErrorReason` に収まり、実装できる粒度である。主な懸念は、U1 の確定済みの `check` の決まりと食い違う 2 つの要求が未決のまま残っている点と、`""` の扱いの割れである。Major は 2 件で、READY の範囲内だが、承認の前に人間が判断する価値がある。
