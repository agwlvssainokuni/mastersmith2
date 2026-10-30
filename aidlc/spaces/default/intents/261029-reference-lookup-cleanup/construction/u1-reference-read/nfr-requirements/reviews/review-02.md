## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T00:44:16Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/scalability-requirements.md > NFR2.8 と「補足」 | `valueColumn` は切らずに読む（BR7.3）一方、読み取り側の値の長さの上限が要件として決まっていない。10,000 文字は書き込み側の上限で、アプリの外から入った長い値（種の `c_010` は 2,000,000 字）を `valueColumn` に指定した DSL では、選択肢 201 行の読み取りだけで数百 MB のヒープを使いうる（NFR2.8 の見積もりは 10,000 文字を前提）。扱い（読むまま・落とす・`NO_PERMISSION`）は「NFR 設計で決める」と先送りされ、security-requirements.md にも上限が無い | NFR 設計へ回すなら、先送りでなく「読み取り側の値の長さに上限を置き、超える行・カラムの扱いを決める」ことを NFR1.4 または NFR2.8 の要件として明記する。あわせて、上限超えの値の結合テストと、ヒープの試しの確かめ方を要件に足す | Resolved (reviewer) |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/performance-requirements.md > NFR2.1・NFR2.3 と scalability-requirements.md > NFR3.3 | 部分一致 `bizSearchContains`（`biz_item` 100,000 行）は前の Intent で MySQL・MariaDB が Not Met になった（読み取りのプール 5 に 10 VU で借りる待ちが超過し 503）。今回の要件は 10 VU・3 分と、NFR3.3 の「プールの待ちの時間切れの累計 0」を判定に使うが、perf の読み取りのプールの上限（前は 5、`TargetDbProperties` の既定は 10）を要件に固定していない。上限の違いで同じ失敗が再現するか、見かけ上通るかが変わる | 場面を流す使い捨ての環境の読み取りのプールの上限を要件に明記する（または前の Intent との差を書く）。MySQL・MariaDB で線を超える見込みであることと、その場合は目標を緩めず依頼者に諮ることを、NFR2.3 に先に書く | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/security-requirements.md > NFR1.1 | 403 を確かめる「要る権限だけを欠く利用者」が4通り（参照元が FULL でない・参照先の表が読めない・`valueColumn` が NONE・`lookupList` が NONE）で、rules.md BR3.1 の欠ける条件のうち「ラベルに使うカラムが NONE」と「`valueColumn` がバイナリか password」「使える検索の条件が残らない」が含まれていない | 表のテストの利用者に、ラベルのカラムが NONE・`valueColumn` がバイナリか password・使える検索の条件が残らない場合を足す | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/reliability-requirements.md > NFR3.4・NFR3.5 | 時間の上限の枠が揃っていない: REFERENCE 5 秒・定義全体 10 秒だが、切り上げのため選択肢の読み取りだけで最悪約 11 秒、これに写しの読み取りと接続の待ちが前に乗る。要件 NFR2 の定義の取得 p95 1 秒との関係（時間切れの経路は p95 の対象外か）が書かれていない。幅の受け入れ自体は決定済み | 最悪値を「全体 10 秒」の目標と並べて、k6 の p95 判定の対象外（時間切れの経路）であることを一文で明記する | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/scalability-requirements.md > NFR2.8 | 読む行を 201 に絞ったうえで 10,000 文字超の行を外す。外した行を数えない扱いは NFR 設計に回されている。201 行のうち 1 行を外すと、実際は 201 行あるのに 200 件として返り、TOO_MANY が出ない可能性がある。ピッカーの次のページの有無でも同じ問題が起きる。要件側にこの見えない食い違いが書かれていない。 | NFR 設計で数え方を決める際、読む上限を外した行の分だけ延ばすか、外した行も TOO_MANY とページ有無の判定に数えるかを決め、境界の結合テスト（200、201 の各行数に長い値の行が混ざる場合）を NFR2.6 に足す。 | New |

> R-01 Reviewer note: scalability-requirements.md の NFR2.8 に、valueColumn を 10,001 文字に切って読み、10,000 文字を超える行を選択肢・ピッカーから外し check では NOT_FOUND_OR_DENIED とすることが要件として明記された。最悪量の見積もり（201 行 × 10,001 文字、1 要求で約 10〜15 MB、10 VU で約 100〜150 MB）、3 種類の DB の結合テスト、NFR 設計でのヒープの捨ての試しも書かれている。NFR1.4 と NFR1.9 からも参照されている。

> R-02 Reviewer note: scalability-requirements.md の NFR3.3 と performance-requirements.md の NFR2.3 で、perf の読み取りのプールの上限を 5 に固定した。MySQL・MariaDB の CONTAINS が線を超えうること、超えたときは目標を緩めず Not Met として諮ることも書かれている。

> R-03 Reviewer note: security-requirements.md の NFR1.1 の 403 の利用者に、ラベルのカラムが NONE・使える検索の条件が残らない・valueColumn がバイナリか password、の各場合が加わった。

> R-04 Reviewer note: reliability-requirements.md の NFR3.5 に、時間切れの経路の最悪値（選択肢の読み取りで約 11 秒、これに写しの読み取りと最大 3 秒の借りる待ちが乗る）を並べ、k6 の p95 の判定の対象外と明記した。performance-requirements.md の補足とも一致している。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（読み取りだけの指示） | 該当なし | 参照 ID（NFR1.15・NFR2.10・NFR3.8）は observability-requirements.md に存在することを確認した。traceability.json の各 target に欠落は見つからなかった。 |

### Summary

前回の指摘 R-01〜R-04 はすべて解消され、新たな Critical と Major は見当たらない。残る懸念は、外した行の数え方が TOO_MANY とページ有無の判定に与える影響で、NFR 設計で確定する前提のまま通してよい。
