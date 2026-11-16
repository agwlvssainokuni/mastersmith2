## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-15T02:41:47Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md > C3 columns・must_hold・未決の点 | C3 の対象の列は dsl_previews.yaml_bytes と dsl_applied_revisions.yaml_bytes の2つだけだが、must_hold には「32 バイトのハッシュの往復」がある。32 バイトのハッシュの列は移行 V3（リフレッシュトークン）と V8（招待）の token_hash BINARY(32) で、C3 の対象に入っていない。また H2 の列の型は変えられないため、H2 の列のまま両方の validate が通る写し方が無かったときの扱い（H2 に足す移行にするか、写し方を種類で分けるか）が書かれておらず、後者は ADR-003 の方針と衝突しうる。 | token_hash の2列を C3 の対象に足すか、別の約束の対象外である理由を書く。単一の写し方が見つからなかったときに U1 が依頼者に諮る旨と、U2 の写し方の範囲が変わりうることを C3 の未決の点の行に足す。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md > 約束の一覧 C3 の Consumer 列 | C3 の使う側が U2 だけだが、本文の must_hold で 10MB とハッシュの往復を確かめるのは U3 で、U3 も C3 に頼る。C2 は U2・U3 と書かれ、書き方が揃っていない。 | C3 の Consumer 列に U3 を足す。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md > 未決の点の表 | 表には Contract・Question・Blocks の3列だけで、持ち主の列が無い。C4 の行の Blocks は「U1 の機能設計・U3 の機能設計」と並ぶが、U3 の機能設計が U1 の機能設計の後であることが書かれていない。C5 の行は U3 も結果を使うが Blocks に出ない。 | 表に Owner の列を足す（C2・C3・C4 は U1、C5 は U2）。C4 の行に、U1 の機能設計が先で U3 はその後に置くことを書く。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md > C4 gradle の integrationTest | 今の integrationTest に excludeTags を足す形だが、-PitTests で絞った形がタグ付きのクラスに当たったときの動きが書かれていない。今の integrationTest は当たるテストが無ければ失敗にするため、専用のタスクを使うように促す失敗の文言になるのか、黙って落ちるのかが決まらない。 | -PitTests がタグ付きのクラスに当たったときは、専用のタスクを使うよう促す失敗にするかを C4 に1行書く。 | New |
| R-05 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md > C6 upgrade_rule と本文の最後の段落、aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/memory.md > Interpretations | Q3: A で内部DB の結合テストも TargetDbImages を共有する読みは、team.md の「内部DB 用の固定先（テストの定数・compose）を足す」と文面が違う。実質は固定先が1か所減るだけで妥当だが、memory.md では Deviations でなく Interpretations に書かれている。また compose の内部DB 用のサービスと TargetDbImages が同じダイジェストであることの確かめ方（機械か手か）と、docs の文書に書かれるイメージの版も揃える対象に入ることが書かれていない（dependabot.yml のコメントの決まり）。 | 読みを Deviations に移すか両方に書き、依頼者の Q3: A の決定として残す。揃っているかの確かめ方（例: Build and Test での突き合わせ）と、docs/internal-db.md のイメージの記載を揃える対象に含めることを C6 に足す。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（Read だけで確かめる指示のため実行せず） | 未実行 | 依存の辺は unit-of-work-dependency.md と C1〜C7 を目で突き合わせた。U1 から U3・U4 は C1・C2・C4・C6、U2 から U3 は C5、U2 が U1 に頼る設計の順は C3 の order で覆われ、覆われない辺は無かった。 |

### Summary

単位の間の辺はすべて C1〜C7 で覆われ、C4 のタスク・タグ・注釈・CI の入力は FR5.2・FR5.3 と team.md の内部DB の PostgreSQL のテストの決まり、既存の ContainerRuntimeCheck の扱い（CI は失敗、手元は警告して飛ばす）に合っている。C3 を TBD のまま U1 に残す形も R-03 と設計の順に合う。ADR-005 が後に回した名前をこの段で決めた点は、memory.md の Deviations の扱いで妥当。指摘は Critical・Major が無く、C3 の対象の列と代替の扱い、使う側の列、未決の点の表、C6 の確かめ方の Minor 5件で、READY とする。
