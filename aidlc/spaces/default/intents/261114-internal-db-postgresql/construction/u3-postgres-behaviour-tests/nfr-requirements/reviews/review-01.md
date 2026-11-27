## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-00 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/security-requirements.md > review findings table | The reviewer's findings table could not be read, so its rows are not listed here: the findings report could not be read: Prior findings row 1 has Now "-"; write Fixed or Still applies. Write the whole review again with the required Prior findings and New findings tables | Address the reviewer's findings as written below this table. | Unresolved |

**The reviewer's findings, as written:**

> **Prior findings**
>
> | ID | Now | Severity | Note |
> | --- | --- | --- | --- |
> | - | - | - | None. |
>
> **New findings**
>
> | ID | Severity | Location | Finding | Required action |
> |---|---|---|---|---|
> | - | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/performance-requirements.md > NFR1.2 と Assumptions | 待ちの時間は「待ち側が pg_stat_activity で待ちに入ったことを観測した時点」から測り、書き込みは 2 秒以上 3 秒未満、行の排他は 3 秒以上 4 秒未満で合否を決めるとしている。lock_timeout は待ちが始まった時点から数えるため、観測は必ずその後になる。測った時間は 2 秒（3 秒）より観測の遅れの分だけ短くなり、下限で落ちうる。仮定の「1 秒の幅があるので揺れない」は上限側にしか当てはまらない。team.md が禁じる不安定なテストを、要件の段で作っている。 | 下限は観測の遅れを引いた値にするか、測りの始点を文の発行の直前にして、観測は待ちに入ったことの確認だけに使う。許す誤差の考え方を数値で書く。仮定の文も直す。 |
> | - | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/security-requirements.md > NFR2.3 と tech-stack-decisions.md > NFR9.1 | NFR2.3 は秘密の乱数に TestDatabase.randomSecret() を使うと書く。NFR9.1 は同じ部品を H2 の URL を作るクラスにあるため許す側に入れず、InternalDbPostgresDatabase の同等の部品を使うと書く。両方に従うと構造のテストが落ちる。functional-design の BR9.3・BR9.5 は TestDatabase.randomSecret を読むだけで使ってよいとしており、3 つが食い違う。機能設計レビューの R-06 の持ち越しが解けていない。 | どちらかにそろえる。NFR2.3 を PostgreSQL 側の部品に直すか、許す部品の一覧（クラス名）を NFR9.1 に書いて TestDatabase.randomSecret を入れるかを決め、BR9.3・BR9.5 との差を明記する。許す依存の一覧は構造のテストが書ける形（クラス名）で固定する。 |
> | - | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/reliability-requirements.md > NFR4.4 と Assumptions（R-01 の持ち越し） | 時刻のテスト（JVM の user.timezone を変える）は、別の JVM または別のクラスとあいまいに書かれ、仮定は「足りないときは NFR 設計で決める」とだけある。足りるかどうかの判定の基準が無い。確かめ方も「構造のテストまたはコードの読み取り」で、合否が定まらない。team.md の 4 か所の user.timezone をそろえる決まりとの関係にも触れていない。 | 要件として、時刻のテストが共有のアプリを閉じてから設定する、または別の JVM で流すことを選び、戻し忘れを検出する確かめ（@AfterAll の後に既定のゾーンが元の値）を書く。team.md の 4 か所の値は変えないことを添える。 |
> | - | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/reliability-requirements.md > Assumptions（R-02 の持ち越し）と NFR4.1 | 行き詰まりの犠牲側の決まり方は、NFR 設計の前の捨ての試しで確かめるとだけあり、要件になっていない。犠牲がテスト側の接続になる場合の合否（どちらが犠牲でも決まる書き方か、アプリ側に固定するか）が書かれていない。 | NFR4.1 に、行き詰まりの確かめの合否を「犠牲がどちらでも」か「アプリ側に固定」かで 1 行で決め、捨ての試しの結果で変えるときの扱いを書く。 |
> | - | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/observability-requirements.md > NFR5.4・NFR2.6 | NFR5.4 は性能、NFR2.6 は安全に当たるが、観測の文書に置かれ、両方の本来の文書の表に載っていない。ID が文書をまたいで飛ぶため、探しにくい。NFR2.6 の確かめ方は「失敗させて出力を読む確かめを 1 回」で、繰り返せず、合否の基準も無い。 | 本来の文書へ移す、または対応表を冒頭に足す。NFR2.6 は、失敗の出力に値の文字列が無いことを自動の確かめに変える（NFR2.2 と統合してもよい）。 |
> | - | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/performance-requirements.md > NFR5.1 | 構造のテストが足す時間を「1 秒未満」としているが、測り方と根拠が無い。ArchUnit はテストのクラスを読み込むため、1 秒を超えうる。また PostgreSQL のテストは CI で流れないため、「CI の job が基準の 1.1 倍以内」は、このテストの有無でほとんど動かない確かめになっている。 | 1 秒の値は目安として Build and Test で測って記録する形にする。CI の時間の確かめは、構造のテストを足した後の値を前提に書く。 |
> | - | Info | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/traceability.json | NFR1〜NFR9 の 9 つがすべて対応行を持ち、対応先の NFRx.y は各文書に実在し、重複・欠番は無い。NFR4（移行が lock_timeout を設定した状態で通ること）は U1 側の確かめで、U3 は共有のアプリの起動で暗黙に確かめるだけだが、その旨は書かれていない。 | 任意: NFR4 の移行の確かめが U1 の持ち分であることを 1 行書く。 |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（Read だけで確認する指定） | 実行せず | traceability.json の ID を 6 つの文書の表と突き合わせ、すべて実在・重複なしを確認した。基準 21分32秒の 1.1 倍は約 23分41秒で、要件の値と整合する。 |

### Summary

上流の NFR1〜NFR9 は漏れなく扱われ、src/main・H2 のテスト・依存を変えない境界とも矛盾しない。Major は 2 件（待ちの時間の下限が観測の遅れで落ちうる点と、秘密の乱数の部品をめぐる NFR2.3 と NFR9.1 の食い違い）で、どちらも承認の場か NFR 設計で直せる。Critical は無く Major は 2 件のため READY とする。
