## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-28
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-00 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/functional-spec.md > review findings table | The reviewer's findings table could not be read, so its rows are not listed here: the findings report could not be read: the New findings table header is, ID, Severity, Location, Finding, Required action, Status, ; it must be, Severity, Location, Finding, Required action, (an extra ID column is allowed). Write the whole review again with the required Prior findings and New findings tables | Address the reviewer's findings as written below this table. | Unresolved |

**The reviewer's findings, as written:**

> **Prior findings**
>
> | ID | Now | Severity | Note |
> | --- | --- | --- | --- |
> | - | - | - | None. |
>
> **New findings**
>
> | ID | Severity | Location | Finding | Required action | Status |
> |---|---|---|---|---|---|
> | R-01 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/functional-spec.md W7-3・rules.md BR7.1・functional-spec.md 2 の表（型の行） | BR7.1 は JVM の user.timezone を変えて時刻を確かめるが、user.timezone は JVM 全体の設定である。流れのテスト（JVM で共有する 1 つのアプリ）と同じ JVM で変えると、共有のアプリや他のクラスの時刻の扱いに響き、不安定なテストになる。team.md は 4 か所の user.timezone を同じ値にそろえる決まりで、テストの JVM もその 1 か所である。設計はクラスごとに起動し、起動前に設定するとだけ書き、元へ戻す方法と他のテストとの分け方を決めていない。 | 時刻のテストは別の JVM（別のテストの実行）で流すか、共有のアプリを閉じてから設定し、終了時に必ず元へ戻す形のどれかを設計に書く。team.md の 4 か所の決まりとの関係（テストの JVM の既定は変えない）も明記する。 | New |
> | R-02 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md BR3.3・functional-spec.md W4-5 | 行き詰まりで「検出された側が排他の失敗の区分で失敗し、もう一方は続けられる」と書くが、PostgreSQL がどちらを犠牲にするかは決まっていない（先に deadlock_timeout が切れた側）。保持側を JDBC の接続、待つ側をアプリの経路にすると、犠牲がテスト側の接続になりうり、アプリの分類を確かめられない。また、アプリのどの操作が行き詰まりを起こせるか、H2 のときの区分の出どころ（参照のテスト）が書かれていない。 | 犠牲がアプリ側になる作りにする（例: テスト側が先に待ちに入り、アプリの経路が後から待ちを閉じる順にする）か、どちらが犠牲でも合否が決まる書き方にする。起こせるアプリの操作と、H2 の期待の出どころ（無ければ使い捨てのコードで先に確かめる）を書く。 | New |
> | R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md BR5.2 | 見出しは「鍵の重なりの 3 つ」、論理は (a)(b) は UPDATED_AFTER_CONFLICT に収束し、(c) preview_id の一意は重なりと扱わず失敗になる、と書き、合否の形が混ざっている。U2 のレビューの持ち越しの 3 つと同じ指しかは、そのレビューを読んでいないため確認できていない。 | (a)(b) の収束と (c) の非収束を別の BR か別の項に分け、持ち越しの項目との対応を書く。 | New |
> | R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md BR3.1・BR3.2・BR3.4 | 合否が実時間の幅（2 秒以上 3 秒未満・3 秒以上 4 秒未満）で、負荷の高い CI の実行での余裕と、測り始めの基準（待ちの観測の後）の誤差の扱いが書かれていない。BR3.4 の「上限（3 秒）」と「応答の時間の揃え方は H2 と同じ」は、出どころの H2 のテストが無く、確かめ方が曖昧。BR3.2 の「プールの同じ接続で続けて」は、テストから接続を固定する方法が無い。 | 測りの始点と許す誤差の考え方を書く。BR3.4 の期待の出どころ（H2 の参照のテストか、事前の確かめ）を書く。接続を固定できない場合は、確かめ方（プールの大きさを 1 にする等）を設計に書く。 | New |
> | R-05 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md BR1.2・functional-spec.md 2 の表（流れの行）・3 の W1 | 流れ 11 クラスが 1 つのアプリを共有するが、DSL の適用・ロックの解除・利用者の停止などは、適用中の DSL や初期管理者など全体で 1 つの状態に触れうる。BR1.2 の「同じ行を書き換えない」は DSL の適用（1 つしかない状態）では守れない。メニューや DSL の流れが互いの結果に頼らない根拠が無い。 | DSL の適用・メニューの流れは専用のアプリ（クラスごと）にするか、適用済みの状態に頼らない作りを書く。初期管理者の状態を変えない決まり（team.md の E2E の決まりと同じ考え方）も添える。 | New |
> | R-06 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md BR9.2・BR9.5 | ArchUnit の決まりは org.h2 と「H2 向けの既存の部品」への依存を禁じるが、BR9.5 などが使う TestDatabase.randomSecret は、H2 の URL を作る TestDatabase のクラスにある。どの部品が禁止の一覧で、どれが許す部品（HttpTestClient・JsonLogRecords・MutableClock・TestDatabase.randomSecret）かが、構造のテストの書ける形になっていない。 | 禁止する部品と許す部品を、パッケージまたはクラス名で一覧にする。TestDatabase は許す側に入れるか、秘密の乱数だけ PostgreSQL 用の部品へ別に作るかを決める。 | New |
> | R-07 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/traceability.json（FR5.1・FR5.4 の行） | FR5.1 は FR3.2〜FR3.8 の確かめも含むが、対応先が BR1.1・BR9.1 だけで、BR2〜BR8 が FR5.1 に結びつかない。FR5.4 の対応は H2 のテスト自体の維持（BR9.3）と構造（BR9.2）で妥当。 | FR5.1 の対応先に BR2〜BR8 を足すか、FR3.x が FR5.1 を覆う旨を note に書く。 | New |
> | R-08 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/functional-design-questions.md Q1 の推奨文・rules.md BR1.1 の logic 末尾 | 質問の推奨文は「12 の機能」と書くが、rules.md・functional-spec.md は 11 の機能で、一致しない。BR1.1 の (11) の行末の文が「ヘルスチェックは U1 が持つ」と同じ文に混ざって読みにくい。 | 数を 11 にそろえ、(11) とヘルスチェックの除外の文を分ける。 | New |
> | R-09 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/functional-spec.md W6・W4・rules.md BR5.1・BR3.2 | 設計の段としては、StatementCounter・SHOW lock_timeout・pg_stat_activity の列名・クラス名の案など、実装に近い記述が多い。ただし合否の物差しとして使われており、害は小さい。 | 実装の手順は Code Generation の計画に回し、設計には観測する事実と合否だけを残す（任意）。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（Read だけで確認する指定） | 実行せず | クラス数は流れ 11・違反 2・待ち 4・失敗後 1・書き直し 3・型 3 の 24 で表と一致。BR の番号の参照（BR9.4 など）は rules.md の中で解決する。 |

### Summary

FR3.1〜FR3.8・FR5.1・FR5.4 は BR1〜BR9 で漏れなく覆われ、U2 からの持ち越し（FR3.6 の 10MB・3 つの重なり・55P03・SKIP LOCKED）も扱われ、src/main と H2 のテストを変えない境界とも矛盾しない。時刻のテストの JVM 全体の設定の分け方と、行き詰まりの犠牲の決まり方の 2 点が不安定なテストを生みうる Major で、承認の場で直すか、Code Generation の計画で決めるのがよい。Critical は無く、Major は 2 件のため READY とする。
