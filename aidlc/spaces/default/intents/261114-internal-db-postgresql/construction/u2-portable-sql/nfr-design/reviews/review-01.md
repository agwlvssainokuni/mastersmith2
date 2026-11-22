## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-22T03:56:53Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/reliability-design.md > DD-1 手順 2.3（重なりの判定） | 重なりの判定は「SQLState 23505 かつ大文字にした文に表の名前」。H2 の文には表の名前が入る（RoleStoreClassifier も同じ前提）が、PostgreSQL の違反の文は制約の名前（例 pk_work_role_selections）しか持たず、表の名前が入るかは制約の名前の付け方に依る。作業ロールの保存の主キーは PK_WORK_ROLE_SELECTIONS で当たるが、dsl_previews の主キーの名前と、同じ表に別の一意の制約（preview_id など）が無いかは、設計に書かれておらず確かめた跡も無い。判定が外れると、PostgreSQL でプレビューの重なりが想定外の失敗になり後勝ちが崩れる（U3 まで気づけない） | dsl_previews の主キー・一意の制約の名前を移行のファイルで確かめて設計に書く。表の名前が入らない場合は、判定を制約の名前の一覧（KeyedUpsert の列挙が持つ）に替えるか、重なりを受ける鍵の列で絞る。判定の表の単体テストに、PostgreSQL の形の文（制約の名前だけ・Detail 付き）の行を足す | New |
| R-02 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/reliability-design.md > DD-3 最後の項目 | 重なりの後の更新が 0 行のときに投げる KeyedUpsertLostException の型が「コード生成の計画で合わせる」と先送りされている。この例外は common.persistence に置くため role.store の RoleStoreUnexpectedException に直接は依存できず、RoleStore.saveWorkRoleSelection は PersistenceException しか捕捉しない（RoleStore.java の catch）。型が PersistenceException の系統でないと、RoleStoreClassifier の想定外の経路を通らず素通りになる | KeyedUpsertLostException を jakarta.persistence.PersistenceException の下に置く、または各 repository・store が受けて今の想定外の例外に包む、のどちらかを設計で決めて書く | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/performance-design.md > DD-5 | 要件 NFR5.2 は「上限切れ（H2 約 4 秒）の確かめは 1 本にまとめる」だが、DD-5 は「作業ロール 1 本・プレビュー 1 本の 2 本まで」としている。reliability-design.md の「上流との差」の表にも無く、要件との差が隠れている（足す時間は約 4 秒で 30 秒の目安内） | 「上流との差」の表に NFR5.2 の差（2 本にする理由）を足すか、DD-5 を 1 本に直す | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/traceability.json > coverage | NFR 要件の observability-requirements.md が定める NFR2.6・NFR4.8・NFR4.9 が upstream_ids にも coverage にも無い（observability-design.md は受けている）。性能・構造の設計が引く NFR8.1・NFR9.2 も一覧に無い | NFR2.6・NFR4.8・NFR4.9（と、引いている NFR8.1・NFR9.2）の行を coverage に足し、target を observability-design.md などに向ける | New |
| R-05 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/performance-design.md > DD-4 StatementCounter | DataSource を java.lang.reflect.Proxy で包む設計だが、(1) 内部DB と対象DB など複数の DataSource から内部DB だけを選ぶ方法、(2) Hikari の指標を付ける後処理（DataSourceUnwrapper）が isWrapperFor・unwrap で元の HikariDataSource に届くこと、(3) 文の本数を数えるには prepareStatement が返す PreparedStatement も包む必要があること、が書かれていない。作れなくはないが、書かないと作る人が推測することになる | 選ぶ条件（Bean の名前か pool 名）、unwrap・isWrapperFor を元へ通すこと、PreparedStatement の包み方を DD-4 に 1〜2 行で足す | New |
| R-06 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/logical-components.md > 境界テスト | dslmanage.repository が common.persistence の新しい部品に依存する向きが、DslManageBoundaryArchitectureTest で許されているかを確かめた記録が無い（今の DslPreviewRepository は common.persistence を使っていない。role.store は RowLockFailures で既に使っている）。ArchitectureTest（web から repository へ、トランザクションの位置など）には common.persistence と衝突する規則は無いことを確かめた | 境界テストの許す依存先に common.persistence が入るかを確かめ、入らないなら緩めずに依頼者の承認を得る旨を書く（コード生成の計画の事項として） | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| コードとの突き合わせ（Read） | UserRepository.savePreferences は @Query のインターフェースのメソッド、RoleStore は PersistenceException だけを捕捉、RoleStoreClassifier は文を大文字にして照合、RowLockFailures は 50200・40001・HYT00・40001 と 3 つの型、ArchitectureTest の規則一覧 | 成果物の事実の記述と一致。持ち越し R-01（カスタムのフラグメント）・R-02（flush と例外の型）への答えは成り立つ（Spring Data の Impl の命名は両方有効、Hibernate 6 以降の HibernateException は PersistenceException の系統、機能設計の試し F3 で小文字の生成キーが H2・PostgreSQL の両方で通ることを確認済み） |
| 手順（DD-1）の追跡 | 更新 → 0 行ならセーブポイント → 挿入 → 23505 で巻き戻して更新のやり直し。重なり以外は巻き戻した後に外へ投げる | 重なりだけを手順の内側で受けるため、rollback-only を避ける（F7）と外へ投げる失敗の整合は取れている。外へ出た例外の型は想定として書かれ、最初のテストで確かめる手当て（失敗時は自分で包む）がある |
| traceability.json | 上流の NFR1.x・2.1〜2.5・4.1〜4.7・5.x・7.x は対応が正しい。NFR2.8 の N/A は U1 の番号の参照で妥当 | NFR2.6・NFR4.8・NFR4.9 が抜けている（Minor） |
| 審査済みの文書の扱い | NFR 要件・機能設計は書き換えず、reliability-design.md の「上流との差」の表に答えを書いている | 観点 6 は満たす。NFR4.3 の範囲の狭め（手順の内側だけ）も表で明示されている |

### Summary

持ち越しの R-01・R-02 への設計側の答え（カスタムのフラグメント・flush・重なりだけを内側で受ける手順）は成り立ち、ArchUnit との衝突や隠れた重い差は見つからなかった。残るのは、PostgreSQL での重なりの判定（制約の名前）の確かめ、例外の型の先送り、NFR5.2 との小さな差、対応表の抜けなどの Minor だけで、反証を試みても Critical・Major は見つからなかったため READY とする。
