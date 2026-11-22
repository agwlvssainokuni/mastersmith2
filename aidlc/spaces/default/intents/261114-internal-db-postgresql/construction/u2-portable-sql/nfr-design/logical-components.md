# 論理の部品と、NFR の決まりを当てる場所 — u2-portable-sql

この単位が持つ部品は、`common.persistence`（書き込みの手順）・`user.repository`（`savePreferences` のカスタムのフラグメント）と、既存の `role.store`・`dslmanage.repository` の書き換えだけです。インフラの部品（サーバー・キュー・キャッシュ）は足しません。

## 部品の一覧と受け持つ NFR

「DD-n」は設計の決定の番号で、`reliability-design.md`（DD-1〜DD-3）と `performance-design.md`（DD-4・DD-5）に書く。

| 部品（目安の名前） | 置き場 | 受け持つ NFR | 設計の決定 |
|---|---|---|---|
| `KeyedUpsertExecutor`（手順） | `common.persistence`（`TraceAspect` の対象の層の外） | NFR4.1〜NFR4.4・NFR7.2・NFR2.1・NFR2.2 | 更新 → 挿入（セーブポイント）→ 重なりなら更新やり直し（DD-1） |
| `KeyedUpsert`（列挙）・`UpsertOutcome`（列挙）・`KeyedUpsertLostException`（想定外） | `common.persistence` | NFR2.2・NFR4.4 | 表・鍵の列・文は定数から取る |
| `RowLockFailures`（既存への 1 行） | `common.persistence` | NFR4.3・NFR4.7 | `55P03` を足す（DD-3） |
| `UserRepositoryCustom` と `UserRepositoryImpl`（新しいフラグメント） | `user.repository` | NFR1.1・NFR4.5・NFR2.3 | `savePreferences` を JDBC の生成キーで 1 文（DD-2）。`UserRepository` から `@Query` を外し、`UserRepositoryCustom` を継承する |
| `RoleStore`（既存の書き換え） | `role.store` | NFR4.1・NFR4.5 | `MERGE ... KEY` を `KeyedUpsertExecutor` の呼び出しに替える。`RoleStoreClassifier` は変えない |
| `DslPreviewRepository`（既存の書き換え） | `dslmanage.repository` | NFR4.1・NFR4.5 | `MERGE ... KEY` を `KeyedUpsertExecutor` の呼び出しに替える |
| `StatementCounter`（テスト用） | `src/test/java/cherry/mastersmith/common/testsupport` | NFR1.1・NFR1.2・NFR7.2 | 文の本数・接続を借りる回数を数える（DD-4） |

## 失敗の範囲（failure domain）と影響の範囲

| 失敗 | 影響の範囲 | 守り |
|---|---|---|
| 同じ鍵への同時の挿入の重なり | その書き込みだけ（後の側は更新をやり直して成功） | 手順の中で収束（NFR4.1） |
| 待ちの上限切れ | その書き込みだけ（失敗として出る） | 今までと同じ分類（NFR4.3） |
| 重なりの後の更新が 0 行 | その書き込みだけ（想定外の失敗） | 結果を作らず投げる（NFR4.4） |
| `savePreferences` の失敗 | 本人の保存だけ | 今までと同じ（NFR4.5） |

## 共有する資源

- 内部DB の接続プール `mastersmith-db`（上限 30）は、今のまま共有する（呼び出し元のトランザクションの接続を使い、増やさない）。

## 境界テスト

- 既存の ArchUnit の境界テスト（`ArchitectureTest`・`UserBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`DslManageBoundaryArchitectureTest`）は緩めず、書き換えない。`user.repository` に増える `UserRepositoryImpl`・`UserRepositoryCustom` は、今の repository の層の中なので、既存の層の検査に当たる（層の依存の向きは変わらない）。
- `common.persistence` の新しい部品が機能のパッケージ（`role`・`dslmanage`・`user` など）に依存しないことを、新しい検査で確かめる（`common/persistence` の下のテストに足す。NFR9.2）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/functional-spec.md`（1 節の部品）・`rules.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/tech-stack-decisions.md`・`reliability-requirements.md`
- `aidlc/spaces/default/memory/project.md`（Code Style: `TraceAspect`）・`team.md`（Code Style: 境界テスト）

## Assumptions & Open Questions

- [assumption] 部品の Java の名前は目安で、コード生成の計画で確定する。役割と置き場は決まり。
- [assumption] `UserRepositoryCustom`・`UserRepositoryImpl` の Spring Data のカスタムのフラグメントの規則（`UserRepository` の名前 + `Impl`、または継承するインターフェースの名前 + `Impl`）に合わせた名前にする。コード生成の最初のテストで、フラグメントが組み込まれること（`UserRepositoryIT` の既存のテストが通ること）を確かめる。
