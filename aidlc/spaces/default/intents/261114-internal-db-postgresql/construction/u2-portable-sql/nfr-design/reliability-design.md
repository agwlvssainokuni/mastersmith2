# 信頼性の設計 — u2-portable-sql

NFR Requirements の `NFR4.x` を、「あれば更新・無ければ挿入」の手順・失敗の分類・トランザクションの扱いの設計に落とします。サーキットブレーカー・再試行（重なりの後の更新のやり直し 1 回を除く）・フェイルオーバーは、内部DB が 1 つ（開発者の PC 上）の前提のため使いません。

## 上流との差（機能設計・NFR 要件の審査の持ち越しへの設計側の答え）

機能設計の審査の指摘（R-01・R-02）への答えを、承認の場まで機能設計を書き換えない決まりのため、この節に 1 か所で書きます。違いは `functional-spec.md` の W1・W2 と `rules.md` の BR1.1・BR2.4・BR2.5 に当たります。

| 指摘・持ち越し | この設計の答え | 反映先 |
|---|---|---|
| 機能設計 R-01: `savePreferences` は Spring Data の `@Query` のインターフェースのメソッドで、`Session.doReturningWork` をその中に書けない | `UserRepository` から `savePreferences` の `@Query` を外し、**カスタムのフラグメント**（`UserRepositoryCustom` のインターフェースと `UserRepositoryImpl`。同じ `user.repository` パッケージ）に移す。`UserRepository` は `UserRepositoryCustom` を継承し、メソッドの形（引数・戻り値）は変えない。`UserRepositoryImpl` は `EntityManager` を受けて、`flush` の後に `Session.doReturningWork` で生成キーの更新を行う。層の境界は変わらない（同じ repository の層の中）。結果の型は、今の `SavedPreferences`（インターフェース）を返す小さな実装（record の入れ子）で返す | DD-2、`logical-components.md` |
| 機能設計 R-02 (1): `executeUpdate` が実行前に行っていた未反映の変更の送り出し（flush）が `doWork` には無い | 手順と `savePreferences` は、JDBC に入る前に `entityManager.flush()` を呼ぶ（今の `executeUpdate` と同じ時点で未反映の変更を送る）。呼ばないと、同じトランザクションで先に変更したエンティティが送られないまま、更新が先に走る | DD-1、DD-2 |
| 機能設計 R-02 (2): 重なり・上限切れ以外の失敗が `doWork` からどの型の例外で出るか | **手順の中で受けるのは鍵の重なり（23505 かつ文に表の名前）だけ**。それ以外の `SQLException`（外部キーの違反・上限切れ・想定外）は、セーブポイントがあれば戻した後、**そのまま外へ投げる**。Hibernate は `doWork`・`doReturningWork` の外へ出た `SQLException` を、`executeUpdate` と同じ例外の変換（`JDBCException` の系統。`ConstraintViolationException`・`LockTimeoutException`・`PessimisticLockException` など）で `PersistenceException` に変換する想定で、`RoleStore` の `PersistenceException` の受け方と `RoleStoreClassifier` の分類は変わらない。想定は、コード生成の最初の単体・結合テストで確かめる（確かめられなければ、手順の外側で自分で `PersistenceException` に包む）。機能設計の BR2.4 の「SQLException を外へ出さない」は、**重なり**についてだけ当たり、ほかの失敗は外へ出す | DD-1、DD-3 |
| 機能設計 BR2.5・NFR4.3: 上限切れの後にトランザクションを使い続けられる | 手順の中で上限切れを受けたら、セーブポイントまで戻した後に外へ投げる（外へ出した後の継続は求めない。今の `executeUpdate` の失敗と同じく、呼び出し側の業務処理がトランザクションを終える）。NFR4.3 の「使い続けられる」は、手順の内側（重なりの後の更新のやり直し）だけが対象 | DD-3 |

## DD-1 書き込みの手順（`KeyedUpsertExecutor`。NFR4.1・NFR4.2・NFR4.3・NFR4.4）

`common.persistence` に、表・鍵の列・更新の文・挿入の文・束縛を受けて、更新 → 挿入 → 重なりなら更新のやり直しを行う 1 つの部品（目安の名前: `KeyedUpsertExecutor`）を置く。

1. `entityManager.flush()`（未反映の変更を送る）。
2. `Session.doReturningWork` に渡す仕事の中で次を行う（接続は呼び出し元のトランザクションのもの。新しい接続を借りない）:
   1. 更新の `PreparedStatement` を束縛して実行する。1 行以上なら `UPDATED`。
   2. 0 行なら、`Connection.setSavepoint()` を張り、挿入の `PreparedStatement` を実行する。通れば `releaseSavepoint` して `INSERTED`。
   3. 挿入が `SQLException` を投げたら、鍵の重なり（SQLState 23505 かつ大文字にした文に表の名前。機能設計 BR2.3）かを判定する:
      - 重なり: `rollback(savepoint)` して、同じ更新を再度実行する。1 行以上なら `UPDATED_AFTER_CONFLICT`。0 行なら想定外の失敗（`KeyedUpsertLostException` のような実行時の例外）として投げる（NFR4.4）。
      - 重なりでない: `rollback(savepoint)` した後、`SQLException` をそのまま投げる（上の表のとおり）。
3. 結果の区分（`UpsertOutcome`）を返す。repository は区分を読み捨てて、書いた行数 1 を返す（今の戻りと同じ）。

- 重なりの判定では `SQLException` の `getMessage()` を読む（表の名前を探すため）が、出力・ログには出さない（NFR2.1）。連なり（`getNextException`・cause）は 10 段までたどる。
- セーブポイントの解放・ロールバックの失敗は握りつぶさず、元の失敗に付けて（`addSuppressed`）投げる。
- 挿入は必ず JDBC で行う（`createNativeQuery(...).executeUpdate()` にしない。Hibernate が rollback-only の印を付けるため。機能設計 BR2.4）。

| 採らなかった形 | 理由 |
|---|---|
| 標準の `MERGE ... USING (VALUES ...)` | 同時の実行で後の側が 23505 で失敗し、H2 の「後勝ち」が変わる（機能設計 D-2）。時刻の `CAST` も要る |
| 挿入だけを新しいトランザクションで行う | 呼び出し元のトランザクションを分けると、確定の順序・監査の記録のタイミングが変わる |
| 楽観ロック・`SELECT ... FOR UPDATE` で先に行を取る | 行が無いときの排他が取れず、重なりを防げない。文も増える |

## DD-2 `savePreferences` の置き場と形（NFR4.5・NFR1.1）

- 上の表のとおり、カスタムのフラグメントに移す。`UserRepositoryImpl` は、`flush` の後に `Session.doReturningWork` で `UPDATE users SET ... WHERE user_id = ?` を、生成キー（列名 `user_id`・`display_time_zone`。小文字）で 1 文として流し、1 行なら生成キーから 2 列を取って返し、0 行なら空を返す。
- 氏名を伏せる型は、`UserRepositoryImpl` の中で束縛の直前に取り出す（機能設計 BR1.3。今の SpEL の取り出しと同じ値）。メソッドの引数・戻り値の形は変えない。例外は外へそのまま投げる（`Session` の変換）。
- 失敗の分類は今のまま: `UserPreferencesService` が受ける例外の型（`PersistenceException` の系統）は変わらない。

## DD-3 失敗の分類（NFR4.3・NFR4.5・NFR4.7）

- `RowLockFailures` に SQLState `55P03` を足す（H2 の判定は変えない）。Hibernate の変換を通る経路は型で当たり、手順の中で受けた生の例外は SQLState で当たる。
- `RoleStoreClassifier` は変えない（文を大文字にして `PK_WORK_ROLE_SELECTIONS` を照合する今の形が、PostgreSQL の小文字の制約名でも当たる）。プレビューを置く経路の失敗の分類は今のまま（今の経路が `RowLockFailures` を使う範囲）。
- 想定外の失敗（重なりの後の更新が 0 行）は、`RoleStoreUnexpectedException`・プレビューの想定外の失敗の今の経路に入る型で投げる（コード生成の計画で、その経路の型に合わせる）。

## テストの確実さ（NFR4.1〜NFR4.4）

- 単体テスト（コンテナ無し・H2 のメモリ）: 鍵の重なりの判定の表（SQLState・文の中の表の名前・大文字小文字）、手順の分岐（更新 1 行・更新 0 行 → 挿入が通る・重なり → 更新のやり直し・重なりの後の更新 0 行・重なり以外の失敗）。`KeyedUpsertExecutor` を本物の H2 の接続で動かして確かめる（JDBC の動作を模擬しない）。
- 結合テスト（H2）: 作業ロールの保存とプレビューを置くの同時の実行を、待ち合わせ（`CountDownLatch` など。実時刻の `sleep` に頼らない）で確実に作る。先の側が挿入を保持したままのとき、(a) 後の側が先のコミットの後に重なりを受け、やり直しで後の側の値が残る、(b) 上限切れ（H2 約 4 秒）のときは上限切れとして分類される失敗が出る、(c) 行が既にあるとき（行の排他）は直列になる。
- 「重なりの後に同じトランザクションの次の文が通る」ことは、手順の内側のテストで確かめる（重なりのとき、手順が戻る時点で確定まで通る）。
- 待ちの上限の秒数（H2 約 4 秒）は、定数として記録せず、テストの中で「上限切れが起きる長さの保持」として使う（H2 を上げたときに見直す）。

## 戻し・前方互換（NFR4.6）

- 表・列・移行を足さない。この単位の変更は文の形だけで、1 つ前の版のイメージに戻せば、前の版が同じ表で動く。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/reliability-requirements.md`（NFR4.1〜NFR4.9）・`tech-stack-decisions.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/functional-spec.md`（W1・W2、F5〜F9）・`rules.md`（BR1.1・BR2.1〜BR2.7）・`reviews/review-01.md`（R-01・R-02）
- `backend/src/main/java/cherry/mastersmith/common/persistence/RowLockFailures.java`・`role/store/RoleStore.java`・`RoleStoreClassifier.java`・`user/repository/UserRepository.java`
- `aidlc/spaces/default/memory/project.md`（Code Style・Corrections）

## Assumptions & Open Questions

- [assumption] Hibernate 7.4.13 は `Session.doWork`・`doReturningWork` の外へ出た `SQLException` を、`executeUpdate` と同じ型の `PersistenceException` に変換する。コード生成の最初の結合テスト（外部キーの違反・上限切れ）で確かめ、違えば手順の外側で包む。
- [assumption] `entityManager.flush()` は呼び出し元のトランザクションの中で呼べる（トランザクション無しの呼び出しは、今の repository と同じく業務処理の層が必ずトランザクションを持つ）。
