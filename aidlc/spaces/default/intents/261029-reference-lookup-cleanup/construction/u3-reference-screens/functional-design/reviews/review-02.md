## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-29T23:31:24Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/frontend-components.md > 3.2 ReferenceRowPage・3.3 getReferenceLabel（functional-spec.md D18） | ラベルの1件読みは `parseReferenceRowPage` で写し、`ReferenceRowPage.page` を `number` としている。U1 の機能設計 4.4 は、1件読みの応答の `page` を `null` と決めている。形が違えば「通信の失敗」にする決め（3.3）と合わせると、成功の応答でも写しが失敗し、更新の画面の LOOKUP のラベルが常に値だけになる。D18・AC2.3.1 と代表の E2E（F8 の 6）が通らなくなる | `ReferenceRowPage.page` を `number \| null` にするか、1件読みだけ別の写し（`page` を見ない）にする。1件読みの成功の応答（`page: null`・`cells` が空）を使う写しのテストを 7節に足す | Resolved (reviewer) |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md > D25・D26 | 2ページ目以降が 0 件のとき `totalCount` は `(page−1)×20` になり、`Table` の総ページが今のページ番号より小さくなる。`Table` はこの組を想定した作りか未確認で、ページ表示や「前へ」の可否が崩れうる | 0 件の後ろのページでは `Table` を出さず「前のページへ」だけを出すか、`totalCount` の下限を決める。`pickerPaging` の性質ベースのテストにこの組を足す | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md > D13・9節 | 作成の画面で、必須の入力不可の参照項目があると保存が常に止まり、その行は画面から作れない（M1: A の行き止まりを受け入れた決定）。開いた直後の誤りが `role="alert"` で読み上げられることと合わせ、NFR 設計に回した点が未決のまま | 承認の場の確かめ点 5 のとおり、NFR 設計で読み上げの扱いを必ず決める。行き止まりである旨を、画面の文言が管理者への依頼を促していることで足りるか確かめる | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md > D22・8節 X1 | ピッカーの検索の条件を文字の入力にする差（`valueType` が U1 の契約に無い）を、U3 だけで抱えている。`EQUALS` の値が型に合わないと 400 を受けて初めて分かり、数値の列で入力しにくい | 承認の場の確かめ点 1 で、U1 に `valueType` を足すかを先に決める。足さないなら、条件の入力の近くに形の例を出すか不要かを明記する | Resolved (reviewer) |

> R-01 Reviewer note: functional-spec.md D18 と frontend-components.md 3.2・3.3 で、ラベルの1件読みは検索の写しと別の `parseReferenceLabel` で受ける形になった。`page`・`hasNext`・`cells` は見ず、`rows` がちょうど1件で `value`・`label` の形が正しいことだけを確かめる。U1 の 4.4（rows 1件・cells 空・page null・hasNext 偽）と一致する。frontend-components.md 7節に `page: null` の成功の応答の写しのテストと `useReferenceLabels` のテストがある

> R-02 Reviewer note: D26 で、2ページ目以降が 0 件のときは Table を描かず、「このページには行がありません」と「前のページへ」だけを出す形に決まった。D25 の `totalCount` の式は行が1件以上のページだけに使う。`pickerPaging` の性質ベースのテスト（総ページが今のページより小さくならない・2ページ目以降の 0 件は empty と backTo が page−1）が 7節と D37 にある。式を確かめると、hasNext 真で page＋1、偽で page になり整合する

> R-03 Reviewer note: 9節の表と D13 で、開いた直後の `role="alert"` の扱いを NFR 設計で必ず決めると明記され、あわせて行き止まり（M1: A）は変えないと書かれた。10節 5・13節にも引き継ぎがある。承認の場の決定どおり

> R-04 Reviewer note: U1 の 2節・BR5.1 に `searchFields` と `listColumns` の `valueType` があることを確認した。D22 が `valueType` から既存の作成・更新の画面と同じ入力を選び、D23 が EQUALS の型の確かめ、D27 が U1 の 400 の写しを決めている。frontend-components.md 3.1・4.4・7節にも型とテストがあり、X1 は解消済みと書かれた

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りによる突き合わせ（traceability.json の target と functional-spec.md の番号） | D1〜D39・X2・X6・X9・F8 などの target はすべて存在する番号を指し、Deferred は U1・U2 に正しく振られている | 網羅は妥当。U1 の 4.4・2節との照合でも食い違いは見つからなかった |

### Summary

前回の4件はいずれも文書の中で解消された。ラベルの1件読みの別の写し、2ページ目以降の 0 件の扱い、`role="alert"` を NFR 設計で決める旨、`valueType` による入力の選択が揃い、U1 の契約とも整合する。実装を始めるのに推測が要る点は見当たらないため、READY とする。
