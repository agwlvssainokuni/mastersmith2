## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-22T03:48:49Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/functional-spec.md > 3. ワークフロー W1・1.1 変えるもの | 今の `UserRepository.savePreferences` は Spring Data の `@Query(nativeQuery = true)` を付けたインターフェースのメソッドで、`FINAL TABLE` の文はその注釈の中にある。W1 は `Session.doReturningWork` で JDBC を直接流す形にするが、インターフェースのメソッドの中には `EntityManager` も処理も書けない。メソッドの形を変えない（契約 C5）まま実現するには、カスタムのフラグメント（別のインターフェース＋実装クラス）に分けるか、呼び出しの経路を変える必要があり、`UserRepository` の構造・置き場・層の境界テストへの影響が成果物に書かれていない。 | W1 と 1.1 に、実現の形（例: `UserRepository` に足すカスタムのフラグメントと実装クラスの置き場と名前、`Preferences` の取り出しをその中で行うこと）を決めて書く。`UserBoundaryArchitectureTest`・層の境界テストが新しい実装クラスで通ることも BR7.1 に足す。 | New |
| R-02 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md > BR2.4・BR2.1 と RoleStore.saveWorkRoleSelection | (1) 今の `saveWorkRoleSelection` は `createNativeQuery(...).executeUpdate()` で書き、Hibernate が実行前に未反映の変更を先に DB へ送る。JDBC（`doWork`）に変えると、同じトランザクションで直前に作った利用者の行などが未反映のまま外部キーの検査に当たる恐れがある（メソッドの説明は「その場で DB へ送る」）。送り出しの扱いが決まりに無い。(2) BR2.4 は「SQLException を外へ出さない」とするが、鍵の重なり・上限切れ以外（利用者への外部キー違反 23503・23506 など）は外へ出す必要がある。`RoleStore` は `PersistenceException` だけを捕捉して `RoleStoreClassifier` に渡し `Referenced` に読み替えているため、`doWork` から出る例外の型（元の `SQLException` のまま包むのか、`PersistenceException` に変えるのか）が決まっていないと、外部キー違反の分類が壊れる。 | BR2.1 か BR2.4 に、JDBC で書く前に未反映の変更を送ること（`flush`）を決めて書く。重なり・上限切れ以外の失敗は、原因の連なりに元の `SQLException`（SQLState・文）を残した `PersistenceException` として出し、`RoleStore` の捕捉と `RoleStoreClassifier` の外部キー違反の読み替えが今と同じに働くことを BR6.3 の単体テスト（外部キー違反の行）に足す。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md > BR1.2 | 「束縛は名前つきのままでよい」と書くが、W1 は JDBC の `PreparedStatement` の `?` で流す形で、名前つきの束縛は使えない。 | 「位置の束縛（`?`）にする」に直し、`CASE WHEN ? = TRUE` の真偽値の束縛と `CAST(? AS VARCHAR(64))` の束縛の順番を W1 に書く。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md > BR2.5 | 更新（手順の 1 番目）自体の待ちの上限切れ（既に行があり先の側が保持している場合）の経路が、セーブポイントを張る前のため BR2.5 と W2 の図に出ていない（図は挿入の側の上限切れだけ）。PostgreSQL ではこの場合もトランザクションが中断される。 | 更新の上限切れも同じ「失敗として出す」ことを BR2.5 と W2 の図に足し、これも BR6.2 の確かめに入れる。 | New |
| R-05 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/entities.md > ENT-002 UpsertOutcome | 区分は呼び出し側へ返さず、テストのためだけに持つと書く。本体のコードに「テストのためだけの値」を持たせる理由が弱い。 | 手順が区分を返す設計（戻り値で部品の単体テストが確かめる）と明記するか、エンティティから外してテストの観点に移す。 | New |
| R-06 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md > BR6.2 | 「実時刻の sleep に頼らない」と書きつつ、保持の約 500 ms・約 4 秒超を前提にしている。上限切れの確かめは H2 の約 4 秒の観測値（未調査の挙動）に依るため、H2 の版を上げると揺れる。 | 保持の長さの決め方（一度測った上限の目安に余裕を足す・待ち合わせで保持を解くなど）を決めて書き、H2 の版を上げるときの見直しを Assumptions のとおり記録する。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| コードとの突き合わせ（Read） | `UserRepository.savePreferences` の `FINAL TABLE`・`RoleStore.MERGE_SELECTION`（`KEY (user_id)`）・`DslPreviewRepository.PLACE_SQL`（`KEY (preview_slot)`）・`RowLockFailures` の誤りの番号と SQLState の一覧（50200・40001・HYT00・40001）・`RoleStoreClassifier` の `SELECTION_KEY`（大文字にして照合）は、成果物の記述と一致 | コードの事実は正確。構造の影響（Spring Data のインターフェース・flush・例外の型）が設計に出ていない |
| traceability.json | FR3.5・FR3.6・FR3.8・FR5.4 が BR に対応。BR7.1 は reverse に N/A | 上流の ID の対応は網羅されている |
| 上流との対応 | U2 の責務・契約 C5・FR5.4 と整合。MERGE を更新 → 挿入にした差と、大きなバイナリを U1 が先に実装した差は「上流との差」に記載済み | 隠れた差は見つからない |

### Summary

捨ての試しの根拠（F1〜F11）と手順の考え方（更新 → セーブポイント → 挿入 → 重なりなら更新やり直し、JDBC で挿入して rollback-only を避ける）は成り立ち、BR2.x の間に矛盾はなく、H2 の結果を変えない制約も守られている。ただし、`savePreferences` が Spring Data のインターフェースである実現の形、JDBC への切り替えで失う送り出しと例外の型の扱いの 2 点（Major）は、コード生成の前に決めておく必要がある。Major は 2 件のため READY とする。
