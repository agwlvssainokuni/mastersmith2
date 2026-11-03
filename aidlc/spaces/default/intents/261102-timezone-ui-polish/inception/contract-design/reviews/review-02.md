## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03T02:31:34Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C3 PreferencesRequest・displayTimeZone | C3 は「本文に項目が無い = 変えない」「null = 消す」を区別する決まり（Q1: A）だが、今の PreferencesRequest は String 4つだけの record で、Jackson は項目が無いときと null のときをどちらも null にする。区別する仕組み（項目の有無を持つ型・JsonNode で受ける など）が契約のどこにも書かれておらず、素直に足すと「古い画面の PUT が保存済みの値を黙って消す」実装になる。 | C3 に、項目の有無を区別して受ける仕組みを書く（例: 有無を持つ型・別の受け方）。あわせて「項目が無い PUT で保存済みの値が変わらない」「null で消える」ことを確かめるテストを U2 の必須として契約に挙げる。 | Resolved (reviewer) |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C4 time_with_zone_only | 「timetz は変換しない」「要求は DB の値と時差のまま」とあるが、今の ColumnValueCodec は timetz も読み書きとも UTC に直している（withOffsetSameInstant(UTC)、時差が無い値は UTC とみなす）。契約と今の動きが食い違う。 | 「UTC の時差にそろえる（時刻の値は時点として同じ）」と実際の動きに合わせて直すか、変える点なら U3 が直す点に入れる。 | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C4 time_point の response | 「端数は DB の値の桁のまま」は、今の実装（appendFraction の 0〜9 桁で末尾のゼロが落ちる）とは違う。「U3 が直す点」に端数の桁は入っているが、今は満たしていない点だと表に書かれていない。 | 「今は末尾のゼロが落ちる。U3 で桁を固定する」と現状との差を明記する。 | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C3 400 の項目ごとの誤り | 400 の fieldErrors の field 名（displayTimeZone）と reason の値（今の FieldError の reason の列挙に足すか）が未定。 | 既存の reason の列挙のどれを使うか、足すなら名前を契約に書く。 | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C5 tableKind | 今の NavNode の領域モデルにはテーブルかビューかの情報の持ち主が書かれておらず、tableKind をどこで決めるか（DSL か対象DB のメタデータか）が無い。ビューの判定は DSL に無い可能性がある。 | tableKind の出どころ（DSL の項目か既存の判定の口か）を契約か未解決の表に1行足し、持ち主の段を付ける。 | Resolved (reviewer) |

> R-01 Reviewer note: C3 に 3 状態（項目が無い・明示の null・名前）を持つ型と専用の読み方の仕組み、U2 が必ず持つテスト（無い・null・名前・不正値の 400）が明記された。型の名前・置き場は U2 の機能設計に回しており、実装できる粒度。

> R-02 Reviewer note: C4 の time_with_zone_only に、今の ColumnValueCodec が timetz を UTC にそろえていること、U3 が直すこと、前の版の画面が壊れないことが書かれた。

> R-03 Reviewer note: C4 の time_point に、今は appendFraction(0, 9) で末尾のゼロが落ちること、U3 が DB の定義の桁に固定することが差として明記された。コードの該当行（150 行）と合う。

> R-04 Reviewer note: C3 に fieldErrors の field が displayTimeZone、reason は既存の INVALID_VALUE（FieldErrorReason に存在）で、REQUIRED は使わないと明記された。

> R-05 Reviewer note: C5 に tableKind の出どころが DslTable.view（コードに存在）で、NavigationService が足し、新しい問い合わせを足さないと明記された。細部の置き場は U5 の機能設計に回している。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| コードの読み取り照合（FieldErrorReason・DslTable・ColumnValueCodec） | 契約の記述と一致 | INVALID_VALUE と DslTable.view が存在し、ColumnValueCodec の端数の動きも契約の記述どおり。 |

### Summary

前回の指摘 R-01〜R-05 はすべて契約に反映され、今のコードとも矛盾しない。新しい指摘は無く、残る未解決の点は後続の機能設計に回す細部だけで、開発者が推測なしで進められる。
