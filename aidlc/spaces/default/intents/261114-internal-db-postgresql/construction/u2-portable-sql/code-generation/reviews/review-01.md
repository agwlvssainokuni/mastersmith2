## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-27T17:10:51Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/code-generation/traceability.json > FR3.6 | FR3.6 は「PostgreSQL でも H2 と同じに保存し読み出す」だが、OK の target の DslManageRepositoryIT.tenMegabytesRoundTrip は H2 でしか動かない。code-summary は PostgreSQL の確かめを U3 に渡すと書いており、実装の側は誠実だが、追跡の表は OK と読める。 | FR3.6 を「H2 のみ OK・PostgreSQL は U3 で確かめる」と target に書くか、Deferred（U3）にする。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/code-generation/traceability.json > NFR2.8 | target が application.yaml で、ログの出力元（org.postgresql と org.hibernate.orm.jdbc.error）が OFF のまま残ることは実在する（365・388 行）。ただしこの単位の差分には無く、実際に確かめるのは U1 の確かめと KeyedUpsertExecutorTest の文を出さない確認。追跡の読み手が差分を探すと見つからない。 | target の説明に「U1 の設定が残ることの確認」と書き、可能なら設定を読むテストを併記する。 | New |
| R-03 | Minor | backend/src/main/java/cherry/mastersmith/common/persistence/KeyedUpsertExecutor.java > isKeyConflict | 重なりの判定は H2 と PostgreSQL の例外の文の文字列（表の名前・制約の名前）に頼る。DSL_PREVIEWS の除外の印 UK_DSL_PREVIEWS_PREVIEW_ID は両方の文に当たる前提で、PostgreSQL の実際の文での確かめは未実施（U3 に渡し済み）。 | U3 で PostgreSQL の実際の文により、主キーの重なり・preview_id の重なり・作業ロールの重なりの 3 つを必ず確かめる。 | New |
| R-04 | Info | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/code-generation/code-summary.md > 上流との差 | BR2.4（SQLException を外へ出さない）と実装（重なりでない SQLException は外へ投げる）の差は、NFR 設計に従った形で明記されており、実装は SQLException を投げる前にセーブポイントへ戻している。ArchUnit の許可は 2 クラスだけで、規則は緩んでいない。 | 対応は不要。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| gradlew :backend:test common.persistence.* と common.testsupport.* | BUILD SUCCESSFUL | 手順書の命令 1 は通過。 |
| 境界テストの命令（2 本目） | 流していない | code-summary が指揮役の再実行で通過と記録。BusinessDataBoundaryArchitectureTest の差分は 2 クラスの追加だけで、規則は緩めていないことを差分で確認した。 |
| H2 の結合テスト | 流していない | 担当が 50 クラス全件通過と報告。DslPreviewPlaceConcurrencyIT などに sleep は無く、assert はあった。 |

### Summary

手順は更新、セーブポイント付きの挿入、重なりのときの更新やり直しで、flush を先に呼び、重なりでない SQLException は戻した後に外へ投げ、KeyedUpsertLostException は PersistenceException の下にあり、値・文はログにも例外にも出ない。設計とのずれは見つからなかった。追跡の表の FR3.6 と NFR2.8 の書き方、PostgreSQL の文での確かめが U3 に残る点だけを重みづけの材料として挙げる。
