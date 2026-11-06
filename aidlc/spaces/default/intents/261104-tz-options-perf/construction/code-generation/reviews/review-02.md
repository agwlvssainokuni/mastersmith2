## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-06T15:47:21Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | frontend/src/features/preferences/buildTimeZoneOptions.test.ts > creates no formatter and calls formatToParts zero times (temporal path, regression) と frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts | 100 ms の性能テストと、組み立て全体の回数のテストは、どちらも実行環境の Temporal の道だけを通る。Intl の道（Safari など Temporal が無い環境）は offsetLabel.test.ts で名前ごとの回数（生成・formatToParts が各 N 以下）を見るだけで、組み立て全体の時間を測るテストが無い。code-summary.md の捨ての試しでは、Intl の道は今の形 21.75 ms に対し 18.64 ms（0.857 倍）で、ほぼ速くなっていない。Safari では CI で落ちた 106 ms と同じ種類の遅さが残りうるが、これを捕まえるテストも記録された値も無い（実際の Safari での確かめも未実施と明記されている）。 | 承認の場で、Safari の Intl の道は速くならないことを依頼者が了解したと記録に明記する（依頼者の決定 B の範囲の確認）。あわせて、Temporal を無しにした状態で buildTimeZoneOptions 全体を呼ぶ回数のテストを 1 件足すか、足さない理由を残す。 | Unresolved |
| R-02 | Minor | frontend/src/features/preferences/buildTimeZoneOptions.ts > entriesFor・epochSecondsOf | now が Invalid Date のとき、epochSeconds が NaN になり、すべての名前（UTC も）の時差が求まらず除かれる。その空の結果が WeakMap に配列の参照ごと残る。性質テストが確かめる「UTC は常にある」が、この入力では成り立たない。code-summary.md の計画との差 4 に書かれているが、画面は new Date() しか渡さないという根拠だけで、テストは無い。 | Invalid Date のときの動き（先頭の「ブラウザに合わせる」だけになる）を決めた動作として 1 件テストに書くか、空の結果を保存しないようにする。 | Resolved (reviewer) |
| R-03 | Minor | frontend/src/shared/format/timeZone/offsetLabel.ts > offsetSecondsByTemporal・offsetLabel.test.ts > temporal path | 2 つの道の一致は Node 26 の 1 つの実行環境で、任意の名前と時点の性質テスト（300 回）で確かめている。Chrome・Firefox が Temporal と Intl で使う時刻帯のデータが食い違う場合の確かめは無い。また Temporal が秒未満の時差を返すと名前ごと選択肢から黙って除かれるが、実在の名前では起きない前提で、一覧の網羅の確かめは無い。 | 実際の時差が 2 つの道で全名前一致することを、全名前 × 数時点で確かめる一致のテストを足す（ZONES の全部で 1 時点ずつ以上）。足さない場合は、その理由を記録に残す。 | Unresolved |
| R-04 | Minor | aidlc/spaces/default/intents/261104-tz-options-perf/construction/code-generation/code-summary.md > 気になる点 | verify の中の conversionPerformance が中央値 101〜104 ms で、直す前の絞った実行の 74 ms より大きい。「変換の関数は変えておらず、負荷の違い」としているが、wallClock.ts の wallAt を wallWith に分けた変更が影響しないことの確かめ（同じ条件での前後の比べ）が無い。 | 承認の前か Build and Test で、同じ条件（verify の中と範囲を絞った実行）で、直す前後の値を並べて原因を確かめる。 | Unresolved |
| R-05 | Minor | frontend/src/features/preferences/buildTimeZoneOptions.ts > buildTimeZoneOptions の saved 分岐 | 保存済みの名前が一覧に無い場合の追加分は、entriesFor の結果とは別に毎回 offsetLabel を呼ぶ。1 件だけなので性能への影響は小さい。ただし、now が Invalid Date のときはこの追加分も出ない。 | 対応は不要。R-02 の Invalid Date の動きを決めた動作としてテストに書くときに、一緒に確かめること。 | New |

> R-01 Reviewer note: Intl の道は組み立て全体の時間も回数も測るテストが無く、Safari での遅さが残りうる点は変わらない。承認の場で依頼者が了解を記録するか、Temporal 無しの全体の回数のテストを 1 件足すこと。

> R-02 Reviewer note: buildTimeZoneOptions.ts の entriesFor が、秒が有限でないときは結果を WeakMap に保存しないようになった。コード上は直っている。Invalid Date の動きを固定するテストの有無は未確認（読み取りのみ）。

> R-03 Reviewer note: offsetLabel.ts は、秒未満の時差を返す名前を黙って除く。全名前で 2 つの道が一致する確かめは、コードを読んだ限り見当たらない。

> R-04 Reviewer note: 計画どおり Build and Test に回す項目。wallAt を wallWith に分けた前後を同じ条件で比べた値は、まだ無い。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| テスト・gradlew・npm | 流していない（依頼の指示） | code-summary.md の記録を根拠とした。コードは offsetLabel.ts と buildTimeZoneOptions.ts を読み取りで確かめた。 |

### Summary

今回の直し（Step 12）で R-02 は直っており、Temporal の道の設計は筋が通っている。Critical はなく、Major は R-01 の 1 件だけなので READY とする。承認の前に、依頼者が Safari の Intl の道は速くならないことを了解して記録するか、Temporal 無しで全体を呼ぶ回数のテストを足すかを決めること。
