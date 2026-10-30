## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-29T23:47:29Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/scalability-requirements.md > NFR2.8 と「補足」 | `valueColumn` は切らずに読む（BR7.3）一方、読み取り側の値の長さの上限が要件として決まっていない。10,000 文字は書き込み側の上限で、アプリの外から入った長い値（種の `c_010` は 2,000,000 字）を `valueColumn` に指定した DSL では、選択肢 201 行の読み取りだけで数百 MB のヒープを使いうる（NFR2.8 の見積もりは 10,000 文字を前提）。扱い（読むまま・落とす・`NO_PERMISSION`）は「NFR 設計で決める」と先送りされ、security-requirements.md にも上限が無い | NFR 設計へ回すなら、先送りでなく「読み取り側の値の長さに上限を置き、超える行・カラムの扱いを決める」ことを NFR1.4 または NFR2.8 の要件として明記する。あわせて、上限超えの値の結合テストと、ヒープの試しの確かめ方を要件に足す | New |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/performance-requirements.md > NFR2.1・NFR2.3 と scalability-requirements.md > NFR3.3 | 部分一致 `bizSearchContains`（`biz_item` 100,000 行）は前の Intent で MySQL・MariaDB が Not Met になった（読み取りのプール 5 に 10 VU で借りる待ちが超過し 503）。今回の要件は 10 VU・3 分と、NFR3.3 の「プールの待ちの時間切れの累計 0」を判定に使うが、perf の読み取りのプールの上限（前は 5、`TargetDbProperties` の既定は 10）を要件に固定していない。上限の違いで同じ失敗が再現するか、見かけ上通るかが変わる | 場面を流す使い捨ての環境の読み取りのプールの上限を要件に明記する（または前の Intent との差を書く）。MySQL・MariaDB で線を超える見込みであることと、その場合は目標を緩めず依頼者に諮ることを、NFR2.3 に先に書く | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/security-requirements.md > NFR1.1 | 403 を確かめる「要る権限だけを欠く利用者」が4通り（参照元が FULL でない・参照先の表が読めない・`valueColumn` が NONE・`lookupList` が NONE）で、rules.md BR3.1 の欠ける条件のうち「ラベルに使うカラムが NONE」と「`valueColumn` がバイナリか password」「使える検索の条件が残らない」が含まれていない | 表のテストの利用者に、ラベルのカラムが NONE・`valueColumn` がバイナリか password・使える検索の条件が残らない場合を足す | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/reliability-requirements.md > NFR3.4・NFR3.5 | 時間の上限の枠が揃っていない: REFERENCE 5 秒・定義全体 10 秒だが、切り上げのため選択肢の読み取りだけで最悪約 11 秒、これに写しの読み取りと接続の待ちが前に乗る。要件 NFR2 の定義の取得 p95 1 秒との関係（時間切れの経路は p95 の対象外か）が書かれていない。幅の受け入れ自体は決定済み | 最悪値を「全体 10 秒」の目標と並べて、k6 の p95 判定の対象外（時間切れの経路）であることを一文で明記する | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 構造確認（`traceability.json` と各文書の ID の突き合わせ） | NFR1.1〜1.15・NFR2.1〜2.10・NFR3.1〜3.8・NFR5.1〜5.3・NFR6.1・NFR7.1〜7.3・NFR9.1 が各文書に存在し、`.1`〜`.20` の枝番の範囲に収まる。NFR4・NFR8 は N/A の理由つき | 参照は解決する |
| コードの照合（読み取りのみ） | `BusinessDataWebConfig` の `MAX_BODY_BYTES` = 65,536、`RequestBodyLimitRoute` の既存の使い方、`TargetDbProperties.QueryTimeout` と `longest()` が文書の記述どおりに存在 | 前提の記述は正しい |

### Summary

要件は上流の機能設計（BR1〜BR12）と決定（Q1〜Q4: A）に整合し、判定の順・時間の上限・接続・漏えいの確かめ方も実装できる粒度で揃っている。実装前に詰めたいのは、読み取り側の値の長さの上限が未決のまま NFR 設計へ先送りされている点（Major 1件）で、残りは確かめ方を補う程度。
