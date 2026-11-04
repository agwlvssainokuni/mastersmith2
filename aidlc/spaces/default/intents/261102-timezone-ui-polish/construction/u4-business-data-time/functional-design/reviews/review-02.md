## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-04
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md > D11 と D12 と W2 の手順1 | D12 は作成・更新のフォームが「開いたときの表示のタイムゾーン」を固定するとし、D10・W2 は ready になるまで待つとする。一方 D11 は error のときもフォームを出し、時点の欄だけを使えない状態にして、再読み込みが成功したら読み込み直さずに時刻が出るとする。error で開いたフォームには固定するタイムゾーンが無く、更新の初めの値（D16）も作れない。再読み込みで ready になった時点で何を固定し、いつ初めの値を作るかが書かれていない。D35 の差分は初めの値の文字との比較なので、ここが決まらないと「変えていない欄を送らない」が実装できない | error から ready に変わったときの扱いを決めて書く。例: 時点の欄だけ ready になった時点のタイムゾーンで固定して初めの値を作る。または、フォーム全体を ready まで待ち error では欄を出さない。D11・D12・W2・frontend-components.md の4節を同じ決まりにそろえる | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md > D23・D28 と frontend-components.md 4節（検索の画面の loading） | 今の `defaultSearchInputs` は表示のタイムゾーンを受けない。D28 は既定の値を表示のタイムゾーンの壁時計の文字にして欄の初めの値にするとするが、loading で欄を使えない間に既定の値をいつ入れるかが書かれていない。マウント時に1回だけ作る作りだと、loading 中の空の値が残るか、ブラウザのタイムゾーンで入る恐れがある | ready になった時点で時点の条件の既定の値を1回だけ入れ、利用者が入れた文字は上書きしない、という決まりを D28 に足す | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md > D23・D27 の演算子の範囲 | 今の検索には EQUALS・CONTAINS・RANGE・CHOICE がある（`SearchPanel.tsx`）。D23 は「範囲・一致」だけを扱い、時点を表す型や時差を持たない日時のカラムに CONTAINS や CHOICE が付いたときの欄の種類が書かれていない | DSL でそれらの演算子が時刻の型に許されるかをコードで確かめて書く。許されるなら今のままの文字の入力とするか、変換の対象にするかを決める | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md > D24・D27（一致の検索） | 範囲の終わりには、端数が無ければその秒ちょうどまでという案内がある（D24）。一致の検索には、端数のある行は秒だけの入力では一致しないという案内が無い。`timestamp(6)` の列で秒だけを入れると 0 件になり、利用者に原因が伝わらない | 一致の検索の案内に、端数まで入れる旨を足す。または、一致の検索を範囲として扱わないことを明記する | New |
| R-05 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/traceability.json > AC2.1.2・AC2.3.6・AC2.5.2 | 3件とも本体はサーバー側（U3）の結合テストで保つのに、status は OK で、画面の側の target 文に一部を書いているだけ。AC2.5.5 だけは「部分」と書いており、書き方がそろっていない | 画面の側だけを受け持つ旨を「部分」と target に明記する。主の単位への Deferred の形にそろえてもよい | New |
| R-06 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md > D1 と D2 | D1 は書式を `±HH:mm` と書く。しかし U1 の `formatInstant` は、時差が秒を含む歴史的なタイムゾーンでは `±HH:mm:ss` を返す（`formatOffset`）。D2 は U1 の決まりをそのまま使うとしており、D1 の書式の表記と食い違う | D1 に、時差が秒を含む場合は `±HH:mm:ss` になることを一言足す。または、D41 のテストにその値を1件入れる | New |
| R-07 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/frontend-components.md > 5 節 文言の鍵・3 節 disabled・functional-spec.md D11 | (1) D27 が一致の検索の案内に「秒の端数を持つ値は、端数まで入れてください」を添えるとしたが、5 節の文言の表に一致の検索の案内の鍵（時点・時差なしの2つ）が無い。実装の DateTimeField は instantEquals・localEquals の案内を持つ。(2) 検索の画面とピッカーの時点の欄を使えない状態にするのは、D11 では error のときだけだが、4 節の表と実装（isDateTimeUsable）は loading でも使えない状態にする。D10 は検索の loading に触れていない | 5 節に一致の検索の案内の2文（日本語・英語）を足す。D10 か D11 に、検索の画面とピッカーは loading でも時点の欄を使えない状態にする（時点の条件が空なら検索は通す）と一言足す | New |

> R-01 Not re-checked this round

> R-02 Not re-checked this round

> R-03 Not re-checked this round

> R-04 Not re-checked this round

> R-05 Not re-checked this round

> R-06 Not re-checked this round

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りの確認（SearchOperatorPolicy.allowed） | CONTAINS は STRING だけ、CHOICE は固定の選択肢か真偽値のときだけ、それ以外は EQUALS に読み替え | D23 の演算子の記述と一致する（前回の R-03 は解けた） |
| 読み取りの確認（BusinessDataFormPage.tsx） | 時点の欄を持つフォームは time.timeZone が出るまで開かず、error のときは帯だけを出し、開いたときのタイムゾーンを固定する。定義の変更後も固定を引き継ぐ | D11・D12・W2・4 節の決まりと一致する（前回の R-01 は解けた） |
| 読み取りの確認（searchConditions.ts） | 既定の値は ready の1回だけ入れ、タイムゾーンが無ければ空。前の版の T 区切りの値は読めない値の誤りになり送らない。範囲の始まりは先・終わりは後、一致は2択、範囲の向きは compareInstants | D25・D27・D28 と一致する（前回の R-02・R-04 は解けた） |
| traceability.json | upstream_ids 31 件と coverage 31 行が一致。AC2.1.2・AC2.3.6・AC2.5.2・AC2.5.5 の target に「部分」と本体を持つ単位が書かれている | 前回の R-05 は解けた。D1 に `±HH:mm:ss` の記述があり、前回の R-06 も解けた |

### Summary

前回の R-01 から R-06 はすべて解けており、D11・D12・W2・W3・frontend-components.md の 4 節は1つの決まりにそろっている。D10・D15・D16・D22・D35 との矛盾と、6 節の上流との差の漏れは見つからなかった。残るのは文言の表と loading 時の検索の扱いの書き漏れ（Minor 1 件）だけで、実装との食い違いで設計が変わる点は無い。
