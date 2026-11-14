## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-14T15:36:49Z
**Iteration:** 1
**Verdict Dictated:** no
**種別**: advisory（1回きり）

### 要約

部品の依存（components.md の depends_on と dependents）は対称で、循環はなく、ADR-001〜006 はいずれも Context・Decision・Consequences・Alternatives Rejected を持ち、2案以上を比べています。内部DB の接続情報を漏らさない決まりにも触れています。FR1〜FR5 は traceability.json で漏れなく対応づいており、Deferred の FR4.4・FR4.5 は文書だけの変更なので妥当です。Critical は無く、Major 相当も無いため READY としました。直しの提案は、「H2 と同じにする」とだけ書いて仕組みを決めていない点と、確定扱いの前提の確かめの置き場の2つです。どちらも機能設計で受けられます。

### Prior findings

なし（初回のレビュー）

### New findings

| ID | 重さ | 対象 | 内容 | 直し方の提案 | Status |
|---|---|---|---|---|---|
| R-01 | Medium | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md の UserPersistence・RolePersistence の behaviour と decisions.md の ADR-006「確かめていない前提」 | 一意の判定の大文字と小文字の扱い（FR3.2）と、主キー採番（IDENTITY）の値に頼る所が無いか（要件の5章の前提）が、「H2 と同じにする」と書かれているだけで、仕組みも確かめの置き場もない。要件の7章で未決とされた2点であり、ADR-006 の確かめていない前提の一覧にも入っていない。 | ADR-006 の確かめていない前提の一覧に2点を足し、確かめを機能設計の捨ての試しに割り当てる。H2 の列の照合かアプリ側のそろえ方（nameKey など）のどちらで効いているかを先に確かめる。 | New |
| R-02 | Medium | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md の InternalDbPlatform の behaviour（待ちの上限）と decisions.md の ADR-001・ADR-006 | FR3.3 は行の排他が 3 秒台・書き込みの待ちが 2 秒台と、2つの上限を求める。設計は接続ごとの `lock_timeout` を当てるとだけ書き、3 秒台の行の排他が Hibernate のヒントによる問い合わせごとの上書きに頼ることは書いていない（下調べの3節にはその測りがある）。接続の中継を挟まない前提（ADR-006）が崩れると、接続の設定も上書きの戻りも当てにならなくなる。 | InternalDbPlatform の behaviour に、接続の上限は 2 秒で、行の排他の 3 秒はヒントで上書きすることを書く。機能設計で、PostgreSQL の結合テストの待ちの測り（FR3.3 の確かめ）の対象に両方の経路を入れる。 | New |
| R-03 | Low | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/components.md の冒頭（変えない既存の部品）と traceability.json の FR3.1・FR3.7 | AuthPersistence・InvitationPersistence は「PostgreSQL でも通る見込み」と書くだけで、部品の YAML にも traceability の target にも出てこない。FR3.1（全機能）と FR3.7（時刻）の担当が、確かめる前の見込みのまま OK になっている。 | 2部品を YAML に「変えない・機能設計で確かめる」として載せるか、FR3.1・FR3.7 の target に加えて、見込みが外れたときの手当ての先を一言書く。 | New |
| R-04 | Low | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/traceability.json の FR5.4・FR3.4 | FR5.4（H2 の結合テストの全件を残す）の対応先が InternalDbMigration になっているが、内容は ADR-005 の既存テストを変えないことで、持ち主は InternalDbPostgresTestSuite 側の決定にある。FR3.4（失敗後のトランザクション）の対応先が PersistenceFailureClassification だけだが、確かめるのはトランザクションの振る舞いで、持つ部品が無い。 | FR5.4 の target を ADR-005・InternalDbPostgresTestSuite に直す。FR3.4 は、区分の部品に加えて、テストで確かめることを target に書き足す。 | New |
| R-05 | Low | aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/domain-design/decisions.md の ADR-002 | 両方の一式の版の番号が一致することを普段の verify で確かめる手立てが「機能設計で決める」のまま。一式がずれるおそれは、PostgreSQL の結合テストが普段流れないため、普段は気づけない。 | 機能設計で、単体テスト（版の番号と制約・索引の名前の突き合わせなど）を必ず入れると決める。 | New |

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| - | - | - | No findings | No action required | Resolved |
