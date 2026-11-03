## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03T02:16:37Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C3 PreferencesRequest・displayTimeZone | C3 は「本文に項目が無い = 変えない」「null = 消す」を区別する決まり（Q1: A）だが、今の PreferencesRequest は String 4つだけの record で、Jackson は項目が無いときと null のときをどちらも null にする。区別する仕組み（項目の有無を持つ型・JsonNode で受ける など）が契約のどこにも書かれておらず、素直に足すと「古い画面の PUT が保存済みの値を黙って消す」実装になる。 | C3 に、項目の有無を区別して受ける仕組みを書く（例: 有無を持つ型・別の受け方）。あわせて「項目が無い PUT で保存済みの値が変わらない」「null で消える」ことを確かめるテストを U2 の必須として契約に挙げる。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C4 time_with_zone_only | 「timetz は変換しない」「要求は DB の値と時差のまま」とあるが、今の ColumnValueCodec は timetz も読み書きとも UTC に直している（withOffsetSameInstant(UTC)、時差が無い値は UTC とみなす）。契約と今の動きが食い違う。 | 「UTC の時差にそろえる（時刻の値は時点として同じ）」と実際の動きに合わせて直すか、変える点なら U3 が直す点に入れる。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C4 time_point の response | 「端数は DB の値の桁のまま」は、今の実装（appendFraction の 0〜9 桁で末尾のゼロが落ちる）とは違う。「U3 が直す点」に端数の桁は入っているが、今は満たしていない点だと表に書かれていない。 | 「今は末尾のゼロが落ちる。U3 で桁を固定する」と現状との差を明記する。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C3 400 の項目ごとの誤り | 400 の fieldErrors の field 名（displayTimeZone）と reason の値（今の FieldError の reason の列挙に足すか）が未定。 | 既存の reason の列挙のどれを使うか、足すなら名前を契約に書く。 | New |
| R-05 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md > C5 tableKind | 今の NavNode の領域モデルにはテーブルかビューかの情報の持ち主が書かれておらず、tableKind をどこで決めるか（DSL か対象DB のメタデータか）が無い。ビューの判定は DSL に無い可能性がある。 | tableKind の出どころ（DSL の項目か既存の判定の口か）を契約か未解決の表に1行足し、持ち主の段を付ける。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（この段に検証の道具の指定は無い。Read と検索だけで確認） | N/A | 辺5本と外の口（C1〜C7）は契約として揃い、持ち主・失敗の扱い・足すだけの版の決まりがある。C5 は NavNodeResponse の今の形と矛盾せず、見られない項目は応答に出ない。C4 の時差の無い値は UTC とみなす点は今の parseDateTimeTz と一致する。 |

### Summary

C1〜C7 の構成と足すだけの版の決まりは妥当。最大の懸念は C3 で、項目が無いときと null の区別を実現する受け方が書かれておらず、今の record のままでは Q1: A が守れない。そのほか C4 の timetz・端数の記述が今の実装と差があるため、承認の場で直すかを判断してほしい。
