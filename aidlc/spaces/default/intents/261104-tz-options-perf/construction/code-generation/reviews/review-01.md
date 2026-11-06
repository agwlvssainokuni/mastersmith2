## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-06T15:38:36Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | frontend/src/features/preferences/buildTimeZoneOptions.test.ts > creates no formatter and calls formatToParts zero times (temporal path, regression) と frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts | 100 ms の性能テストと、組み立て全体の回数のテストは、どちらも実行環境の Temporal の道だけを通る。Intl の道（Safari など Temporal が無い環境）は offsetLabel.test.ts で名前ごとの回数（生成・formatToParts が各 N 以下）を見るだけで、組み立て全体の時間を測るテストが無い。code-summary.md の捨ての試しでは、Intl の道は今の形 21.75 ms に対し 18.64 ms（0.857 倍）で、ほぼ速くなっていない。Safari では CI で落ちた 106 ms と同じ種類の遅さが残りうるが、これを捕まえるテストも記録された値も無い（実際の Safari での確かめも未実施と明記されている）。 | 承認の場で、Safari の Intl の道は速くならないことを依頼者が了解したと記録に明記する（依頼者の決定 B の範囲の確認）。あわせて、Temporal を無しにした状態で buildTimeZoneOptions 全体を呼ぶ回数のテストを 1 件足すか、足さない理由を残す。 | New |
| R-02 | Minor | frontend/src/features/preferences/buildTimeZoneOptions.ts > entriesFor・epochSecondsOf | now が Invalid Date のとき、epochSeconds が NaN になり、すべての名前（UTC も）の時差が求まらず除かれる。その空の結果が WeakMap に配列の参照ごと残る。性質テストが確かめる「UTC は常にある」が、この入力では成り立たない。code-summary.md の計画との差 4 に書かれているが、画面は new Date() しか渡さないという根拠だけで、テストは無い。 | Invalid Date のときの動き（先頭の「ブラウザに合わせる」だけになる）を決めた動作として 1 件テストに書くか、空の結果を保存しないようにする。 | New |
| R-03 | Minor | frontend/src/shared/format/timeZone/offsetLabel.ts > offsetSecondsByTemporal・offsetLabel.test.ts > temporal path | 2 つの道の一致は Node 26 の 1 つの実行環境で、任意の名前と時点の性質テスト（300 回）で確かめている。Chrome・Firefox が Temporal と Intl で使う時刻帯のデータが食い違う場合の確かめは無い。また Temporal が秒未満の時差を返すと名前ごと選択肢から黙って除かれるが、実在の名前では起きない前提で、一覧の網羅の確かめは無い。 | 実際の時差が 2 つの道で全名前一致することを、全名前 × 数時点で確かめる一致のテストを足す（ZONES の全部で 1 時点ずつ以上）。足さない場合は、その理由を記録に残す。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261104-tz-options-perf/construction/code-generation/code-summary.md > 気になる点 | verify の中の conversionPerformance が中央値 101〜104 ms で、直す前の絞った実行の 74 ms より大きい。「変換の関数は変えておらず、負荷の違い」としているが、wallClock.ts の wallAt を wallWith に分けた変更が影響しないことの確かめ（同じ条件での前後の比べ）が無い。 | 承認の前か Build and Test で、同じ条件（verify の中と範囲を絞った実行）で、直す前後の値を並べて原因を確かめる。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（読み取りだけの確かめ） | code-summary.md の実測の記録を根拠にした | テスト 3057 件通過・カバレッジの下限を満たす記録を確認した。再実行はしていない |

### Summary

作りは要件どおりで、共有の整形器のキャッシュを汚さず、性能テストの測り方も変えていない。timeZoneGate・wallClock・formatInstant の直しは動きを変えない切り出しで、ライセンスヘッダーと ESLint の規則も保たれている。承認の前に考えるべき点は、Temporal の無い環境の Intl の道がほぼ速くならず、その時間を守るテストが無いことである。
