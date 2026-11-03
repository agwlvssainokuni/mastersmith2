## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03T16:22:32Z
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

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| sensors（required-sections・upstream-coverage・linter・type-check・traceability） | 実行せず。コマンドを使わない指示のため、手で確かめた | traceability.json の upstream_ids 31件（AC2.1.1〜7・AC2.2.1〜4・AC2.3.1〜6・AC2.4.1〜5・AC2.5.1〜5・AC5.2.1〜4）と coverage 31行が一致する。US2.1〜US2.5・US5.2 のストーリーとも一致する。target が D 番号になる点は、ui の単位として既知の扱い |
| 読み取りの確認 | `formatInstant`・`parseLocalDateTime`（gap・overlap・端数1〜6桁・最大長26）は D1・D17・D20・D21 の前提と一致。`ReferenceFieldResponse` に `labelValueType` は未実装で、足すだけの変更になる | D30 の変更は、既存の `ReferenceField` の生成口（`noPermission`・`tooMany`・`choices`）に型を通す直しで済む。問い合わせは増えない。`toString` に値が出ない決まりも保てる |

### Summary

上流（ADR-002・契約 C1・C2・C4・画面イメージ）との整合は取れており、6節の「上流との差」の書き漏れは見つからなかった。往復・夏時間の存在しない時刻と重なる時刻・範囲の両端の決まりも実装できる形で、矛盾は見つからなかった。`labelValueType` の追加も、漏えい・問い合わせの数・前の版との互換を守る。承認の前に決めたいのは、表示のタイムゾーンが error のときのフォームの扱い（D11 と D12 の食い違い）の1点だけ。残りは軽微。
