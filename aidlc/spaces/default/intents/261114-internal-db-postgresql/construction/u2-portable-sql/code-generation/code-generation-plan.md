# コード生成の計画 — u2-portable-sql

## Summary

- Builds: H2 にしか通らない書き込みの文 3 つ（`savePreferences` の `FINAL TABLE`、作業ロールの保存とプレビューを置くの `MERGE ... KEY`）を、H2 と PostgreSQL の両方に通る書き方に直す。「あれば更新・無ければ挿入」の共通の手順（`common.persistence`）と、`RowLockFailures` への `55P03`
- Touches: `backend/src/main/java/cherry/mastersmith/common/persistence/`（新しい部品 4 つ + `RowLockFailures` への 1 行）、`user/repository/`（`UserRepository` から `savePreferences` を外し、カスタムのフラグメント 2 つを足す）、`role/store/RoleStore.java`、`dslmanage/repository/DslPreviewRepository.java`、`backend/src/test/java/cherry/mastersmith/`（`common/persistence`・`common/testsupport`・`role/store`・`dslmanage/repository`・`user/repository`）
- Tests: 単体テスト約 25 件（コンテナ無し。判定の表を含む。手順は本物の H2 の接続で動かす）、H2 の結合テスト約 10 件（同時の実行・文の数・既存の書き直し）、構造の検査 1〜2 件。PostgreSQL のテストは足さない（U3）
- 変えない: repository・store のメソッドの形（引数・戻り値）、`copyFromPreview`・Auth・招待の SQL、利用者の検索、移行、`application.yaml`、`ci.yml`・`build.gradle.kts`・依存・lockfile

## 計画の前提（設計の段の決定と、持ち越した審査の指摘の受け方）

機能設計・NFR 要件・NFR 設計・インフラ設計の文書は承認の場の前の初稿で、承認の場の決定はまだ無い（各段の審査はすべて READY で、指摘は持ち越し）。この計画は、設計の文書を書き換えずに、次のとおり各段の決定と指摘を受ける。承認の場で設計の文書を直すときは、段のやり直しを先に行い、この計画との差を確かめ直す。

### 依頼者が決めたこと（機能設計の質問。2026-11-22）

| 質問 | 決定 | 反映先 |
|---|---|---|
| Q1 | `savePreferences` は JDBC の生成キーで 1 文のまま | Step 6 |
| Q2 | 作業ロールの保存・プレビューを置くは、更新 → 挿入（セーブポイント）→ 重なりなら更新やり直し | Step 3・5 |
| Q3 | 検索の Unicode の端の違い（`İ`・語末シグマ・`ǅ`）は既知の違いとして受け入れ、U4 の文書に書く | コードは変えない。`code-summary.md` の「後の単位に渡すこと」 |

### この計画が決める点（設計の審査の Minor の答え。依頼者の承認で確定する）

| 指摘 | この計画の決め | 反映先 |
|---|---|---|
| NFR 設計 R-01（`dsl_previews` の PostgreSQL の制約の名前が未確認） | 移行（`V5`）で確かめた: 主キー `preview_slot` は名前なし（H2 は自動の名前、PostgreSQL は `dsl_previews_pkey`）、一意の制約は `uk_dsl_previews_preview_id`（`preview_id`）。後者の名前にも表の名前が入るため、判定が誤って当たらないよう、`KeyedUpsert` の列挙が「重なりでない印」（`DSL_PREVIEWS` の表では `UK_DSL_PREVIEWS_PREVIEW_ID`。大文字）を持ち、文にそれを含むときは重なりとみなさない。`work_role_selections` は主キーだけ（`pk_work_role_selections`）。判定の表の単体テストに PostgreSQL の形の文（制約の名前だけ・Detail 付き・小文字）の行を足す | Step 3・4 |
| NFR 設計 R-02（`KeyedUpsertLostException` の型が未決） | `jakarta.persistence.PersistenceException` の下に置く。`RoleStore` の `catch (PersistenceException)` で受けて、今の `RoleStoreClassifier` の想定外の経路に入る（`common.persistence` は `RowLockFailures` で既に `jakarta.persistence` を使うため、依存は増えない） | Step 3・5 |
| NFR 設計 R-03（NFR5.2 は 1 本、DD-5 は 2 本） | 上限切れの確かめは、作業ロール 1 本・プレビュー 1 本の 2 本（DD-5 のとおり）。足す時間は約 4 秒 ×2 で、全件の `verify` の増加の目安（約 30 秒）の内。NFR5.2 との差を `code-summary.md` の「上流との差」に書く | Step 8、`code-summary.md` |
| NFR 設計 R-04（traceability の抜け） | 設計側の文書の直し。この計画は NFR2.6・NFR4.8・NFR4.9・NFR8.1・NFR9.2 を Step 4・7・9 の確かめに入れ、Step 11 の traceability.json に載せる | Step 4・7・9・11 |
| NFR 設計 R-05（`StatementCounter` の細部） | 選ぶ条件は Bean の名前 `dataSource`（`BeanPostProcessor`）。`Proxy` は `unwrap`・`isWrapperFor` を元の `DataSource` へ通す（Hikari の指標の後処理が元に届く）。`prepareStatement` の戻り値も `Proxy` で包んで `execute`・`executeUpdate`・`executeQuery`・`executeBatch` を数える。接続を借りる回数は `getConnection` で数える。内部DB の `DataSource` だけに当てる（`targetDataSource` などは包まない） | Step 7 |
| NFR 設計 R-06（`dslmanage` → `common.persistence` の向き） | 確かめた: `DslManageBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`UserBoundaryArchitectureTest` は、`cherry.mastersmith.common..` への依存を許している（緩める必要は無い）。Step 9 でテストを流して確かめる | Step 1・9 |
| 機能設計 R-01（`@Query` のインターフェースに `doReturningWork` を書けない） | NFR 設計の答えを採る: `UserRepositoryCustom` と `UserRepositoryImpl`（同じ `user.repository`）に移す。`SavedPreferences` のインターフェースは `UserRepository` の入れ子のまま（呼び出し側の参照を変えない） | Step 6 |
| 機能設計 R-02（`doWork` は flush を持たず、例外の型が変わりうる） | NFR 設計の答えを採る: 手順と `savePreferences` は JDBC の前に `entityManager.flush()` を呼ぶ。手順の中で受けるのは鍵の重なりだけで、ほかの `SQLException` はセーブポイントを戻した後にそのまま外へ投げる。Hibernate が外へ出た `SQLException` を `executeUpdate` と同じ型の `PersistenceException` に変換することは、Step 4 の最初の確かめ（外部キーの違反・上限切れ）で確かめる。違えば手順の外側で自分で包む | Step 3・4 |
| 機能設計の Minor（BR1.2 の名前つきの束縛・update の上限切れの道・`UpsertOutcome` がテスト用・BR6.2 の sleep） | 束縛は JDBC の位置の引数（`?`）にする（名前つきは使わない）。更新の上限切れは重なりではないので外へ投げる（Step 4 に確かめを入れる）。`UpsertOutcome` は手順の戻り値として持ち、repository は読み捨てる（単体テストで区分を確かめる）。同時の実行は待ち合わせ（`CountDownLatch`・H2 の `INFORMATION_SCHEMA.SESSIONS` の待ち状態を上限つきで見る）で作り、実時刻の `sleep` を合否に使わない。保持の時間は H2 の上限切れ（約 4 秒）を確かめる 2 本でだけ要る | Step 8 |
| NFR 要件・インフラ設計の Minor（`internal_db_postgresql` の入力名の誤記・`NFR4.8`/`NFR4.9` の言い回し・`NFR4.5` と例外の型・`ConnectionAcquireCounter` の置き場など） | 設計側の文書の直し（承認の場で、段のやり直しの後に直す）。コードの手順は変わらない。入力名の誤記は `cicd-pipeline.md` の 1 か所（正しい名前は `internal_db_postgres`）で、承認の場で直す | 承認の場 |

### 上流との差（`code-summary.md` の「上流との差」に 1 か所で書く）

1. 機能設計 BR2.4 は「SQLException を外へ出さない」と書くが、設計（NFR 設計の `reliability-design.md` 上流との差の表）は「重なりについてだけ」にした。この計画は NFR 設計に従う。
2. `KeyedUpsert` の列挙が「重なりでない印」を持つ（R-01 の答え）。機能設計 BR2.3 の判定は「23505 かつ表の名前」だが、`dsl_previews` の `preview_id` の一意の制約の違反を重なりと取り違えないために、印の除外を足す。
3. 上限切れの確かめは 2 本（NFR5.2 は 1 本）。

## 要件との対応

| 要件 | 計画の手順 |
|---|---|
| FR3.5 | Step 2〜6（書き直しと手順）、Step 4・7・8（H2 での確かめ。PostgreSQL での確かめは U3） |
| FR3.6 | Step 8（H2 の 10MB の往復の確かめ。写し方は U1 が直した形） |
| FR3.8 | コードは変えない。`code-summary.md` に既知の違いを U4 へ渡すものとして書く |
| FR5.4 | Step 7・8・10（H2 の結合テストの全件の結果を変えない） |
| NFR1.x | Step 7（文の数・接続を借りる回数）、Step 3（本文を複製しない） |
| NFR2.x | Step 3・4・6・8（値・例外の文を出さない。氏名の伏せ字の型を束縛の直前まで保つ） |
| NFR4.x | Step 3・4・8（収束・上限切れ・想定外の失敗・分類） |
| NFR5.x | Step 8・10（足すテストの時間。通常の検査にコンテナを足さない）、Build and Test（時間の記録） |
| NFR7.x | Step 7（接続を増やさない） |
| NFR8.1 | Step 1・11（依存・lockfile・`ci.yml` に差分が無いこと） |
| NFR9.2 | Step 9（境界テスト） |

## 実施の手順（Testing Contract: test-after。層ごとに実装を書き、その層のテストを書いて流す）

層のうち、API・エンドポイントの層と画面（frontend）の層は、この単位に当たらない（画面入出力も画面も変えない）。当たる層は、DB アクセスの層（repository・store・共通の手順）、業務処理に当たる部品の呼び出しの形、構造の検査。

- [x] **Step 1: 実行の確かめと、影響の洗い出し（骨組み）**
  - `unit-test-instructions.md` の命令（単体・H2 の結合・構造の検査）が実際に使えることを、通るテストが 1 件以上当たる形で確かめる（今の `RowLockFailuresTest`・`WorkRoleSelectionMergeIT`・`ArchitectureTest` に当たる）。
  - 変える口を使っている既存のテストと本体を検索で洗い出して `code-summary.md` に書く: `savePreferences`（`UserRepositoryIT`・`PreferencesPartialUpdateIT`・`DisplayTimeZoneKeptByOtherUpdatesIT`・`PreferencesSecretLeakIT`・`UserPreferencesServiceTest`）、`saveWorkRoleSelection`（`WorkRoleSelectionMergeIT` と `role` の IT）、`DslPreviewRepository.place`（`DslManageRepositoryIT`・`DslConcurrencyIT`・`DslLifecycleTest`・`DslStartupLoaderTest`）、`RowLockFailures`（`businessdata` の別の分類は触れない）。`RoleStore`・`DslPreviewRepository`・`UserRepository` を手で組み立てる・部分的な起動クラスで Bean 名を並べるテスト（`new RoleStore(`・`new DslPreviewRepository(` など）の有無を調べ、あれば手順の部品を自分で作る形（下の Step 5）で通ることを確かめる。
  - 同じ値を持つファイルの洗い出し: 表の名前（`WORK_ROLE_SELECTIONS`・`DSL_PREVIEWS`）は `RoleStoreClassifier` と同じ判定の文字列を、新しい `KeyedUpsert` の列挙にまとめて持つ。
  - 既存の境界テスト（`ArchitectureTest`・3 つの機能の境界テスト）が `cherry.mastersmith.common..` への依存を許していることを読んで確かめ、結果を書く（R-06。緩めない）。
  - 依存・lockfile・`ci.yml` に差分が無いことを `git diff --stat` で確かめる（NFR8.1）。

- [x] **Step 2: 上限切れの分類に `55P03` を足す（DB アクセスの層・共通の部品。実装とテスト）**
  - `common/persistence/RowLockFailures.java`: `SQL_STATES` に PostgreSQL の SQLState `55P03`（`lock_not_available`）を足す（BR2.6）。H2 の判定（型・50200・40001・`HYT00`）は変えない。クラスの説明に PostgreSQL を足す。
  - `RowLockFailuresTest`: `55P03` の `SQLException`（直接と連なりの奥）が上限切れとして分類される行、`55P03` と似た別の SQLState（`55000` など）は当たらない行を足す。既存の行は書き換えない。

- [x] **Step 3: 「あれば更新・無ければ挿入」の共通の手順（DB アクセスの層。実装）**
  - `common/persistence/` に次を作る（名前は目安から確定。すべて Apache License 2.0 のヘッダー付き。`TraceAspect` の対象の層の外）。
    - `KeyedUpsert`（列挙。表ごとの定数: `WORK_ROLE_SELECTIONS`・`DSL_PREVIEWS`。持つもの: 更新の文・挿入の文・重なりの印（大文字の表の名前）・重なりでない印（`DSL_PREVIEWS` は `UK_DSL_PREVIEWS_PREVIEW_ID`）。文は定数で、利用者の入力を連結しない。NFR2.2）。文: `UPDATE work_role_selections SET role_id = ?, updated_at = ? WHERE user_id = ?` と `INSERT INTO work_role_selections (user_id, role_id, updated_at) VALUES (?, ?, ?)`。`UPDATE dsl_previews SET preview_id = ?, yaml_bytes = ?, dsl_hash = ?, source = ?, placed_by_user_id = ?, placed_at = ? WHERE preview_slot = 1` と `INSERT INTO dsl_previews (preview_slot, preview_id, yaml_bytes, dsl_hash, source, placed_by_user_id, placed_at) VALUES (1, ?, ?, ?, ?, ?, ?)`（スロットの値は `DslPreviewRecord.SLOT` と同じ 1。時刻・UUID・数値・バイト列に `CAST` を付けない）。
    - `UpsertOutcome`（列挙。`UPDATED`・`INSERTED`・`UPDATED_AFTER_CONFLICT`）。
    - `KeyedUpsertLostException`（`PersistenceException` の下の実行時の例外。重なりの後の更新が 0 行のとき。文は表の名前だけの固定の説明で、値・例外の文を入れない。cause なし。R-02。NFR2.1）。
    - `KeyedUpsertExecutor`（`EntityManager` を受ける、`final`、Bean にしない）。公開の口 `UpsertOutcome upsert(KeyedUpsert upsert, Object[] updateArguments, Object[] insertArguments)`: `entityManager.flush()` の後に `Session.doReturningWork` で、同じトランザクションの接続の上で `execute(Connection, ...)` を呼ぶ。`execute`（パッケージ内で見える。単体テストが本物の H2 の接続で直接呼ぶ）: (1) 更新を流して 1 行以上なら `UPDATED`、(2) 0 行ならセーブポイントを張って挿入を流し、通れば解放して `INSERTED`、(3) `SQLException` が鍵の重なり（SQLState `23505` かつ文を大文字にして重なりの印を含み、重なりでない印を含まない。連なり 10 段まで）なら、セーブポイントまで戻して同じ更新をやり直し、1 行以上で `UPDATED_AFTER_CONFLICT`、0 行なら `KeyedUpsertLostException`、(4) 重なりでない `SQLException` はセーブポイントまで戻した後、そのまま外へ投げる。セーブポイントの解放・戻しの失敗は元の失敗に `addSuppressed` で付ける。判定は静的な純粋な関数 `isKeyConflict(KeyedUpsert, SQLException)`（文を読むが保持・出力しない。BR2.3・NFR2.1）。値の束縛は `PreparedStatement` の位置の引数で、`byte[]` は `setBytes`（複製しない。NFR1.3）、`null` は `setObject(i, null)` でなく型を持たない `setNull` を避けるため、使う値に `null` が無いことを前提にする（作業ロールの 3 値・プレビューの 6 値はすべて非 null）。挿入は必ず JDBC で行い、`createNativeQuery(...).executeUpdate()` にしない（BR2.4）。
  - 手順の部品は機能のパッケージ（`role`・`dslmanage`・`user`）に依存しない（NFR9.2）。ログは出さない（NFR2.6）。

- [x] **Step 4: 共通の手順のテスト（DB アクセスの層のテスト。コンテナ無し・本物の H2 の接続）**
  - `common/persistence/KeyedUpsertExecutorTest`: H2 のメモリのデータベースを使い、必要な表を自分で作って、`execute` を本物の接続で動かす（JDBC の動作を模擬しない）。
    - 区分: 行が既にある → `UPDATED`（文は更新 1 本）、行が無い → `INSERTED`、更新 0 行 → 挿入が重なり（別の接続が未確定の同じ鍵の挿入を持つ状態を単体で作る、または挿入の直前に別の接続が確定する差し込みの口を使う） → `UPDATED_AFTER_CONFLICT`、重なりの後の更新が 0 行 → `KeyedUpsertLostException`（挿入した行を別の接続が消す形で作る）、重なりでない失敗（外部キー・NOT NULL の違反）→ セーブポイントまで戻った後にそのまま外へ出る（元の `SQLException` で、トランザクションは使い続けられる）。
    - 更新の上限切れ（別の接続が行を未確定のまま持つ）は重なりではなく外へ出ること（1 本。待ちの上限の秒数は定数に書かず、上限切れを起こす時間をテストの中で測る）。
    - `isKeyConflict` の表（パラメーターを使うテスト）: H2 の文（大文字・表の名前入り）、PostgreSQL の形（小文字の制約の名前だけ・`Detail: Key (user_id)=(…) already exists.` 付き・`dsl_previews_pkey`）、`uk_dsl_previews_preview_id`（重なりでない）、SQLState が `23505` でない、表の名前が無い、連なりの奥、null の文。**値（キー・本文の断片・メールアドレス）を入れた例外を渡しても、返すのが真偽だけで出力・例外の文に出ないこと**。
    - `KeyedUpsertLostException` の文に値が入らない・`PersistenceException` であること。
  - 最初の確かめ（R-02 の想定）: Step 5 で `RoleStore` を書き換えた後に、外部キーの違反（存在しない利用者）と上限切れの失敗が、今までと同じ型の `PersistenceException` で `RoleStoreClassifier` に届くことを `RoleStore` の結合テストで確かめる。違えば手順の外側で包んで直す。

- [x] **Step 5: 作業ロールの保存とプレビューを置くの書き換え（DB アクセスの層。実装）**
  - `role/store/RoleStore.java`: `MERGE_SELECTION` を外し、`saveWorkRoleSelection` を `new KeyedUpsertExecutor(entityManager)`（コンストラクターで作って持つ）の呼び出しに替える。引数: 更新 = `roleId`・`OffsetDateTime(updatedAt, UTC)`・`userId`、挿入 = `userId`・`roleId`・`updatedAt`。結果の区分は読み捨てて `Done(null)`。`catch (PersistenceException e)` と `RoleStoreClassifier.toOutcome` は今のまま（重なりの後の更新が 0 行は `KeyedUpsertLostException` が `PersistenceException` なので想定外の経路に入る）。クラスの説明・コメントの `MERGE` の言及を直す。`RoleStoreClassifier` は変えない（文を大文字にして `PK_WORK_ROLE_SELECTIONS` を照合する今の形が、PostgreSQL の小文字の制約名でも当たる）。
  - `dslmanage/repository/DslPreviewRepository.java`: `PLACE_SQL` を外し、`place` を同じ手順の呼び出しに替える（引数の `byte[]` を複製せず束縛。`placedAt` は `OffsetDateTime(UTC)`）。戻りは 1 のまま。クラスの説明の「1 文の MERGE」を直す。
  - 既存の `RoleStore`・`DslPreviewRepository` を手で組み立てるテストがあれば、コンストラクターの形を変えないため書き換えは要らない（Step 1 で確かめる）。

- [x] **Step 6: `savePreferences` のカスタムのフラグメント（DB アクセスの層。実装）**
  - `user/repository/UserRepositoryCustom`（インターフェース。メソッド `Optional<UserRepository.SavedPreferences> savePreferences(long userId, Preferences preferences, boolean change, String zone)`。今の `UserRepository` の宣言と同じ形）と `UserRepositoryImpl`（`EntityManager` を受ける）を作る。`UserRepository` は `UserRepositoryCustom` を継承し、`savePreferences` の `@Query` と宣言を外す（入れ子の `SavedPreferences` のインターフェースは `UserRepository` に残す）。Javadoc の H2 の `FINAL TABLE` の言及を直す。
  - `UserRepositoryImpl.savePreferences`: `entityManager.flush()` の後に `Session.doReturningWork` の中で `UPDATE users SET display_name = ?, language = ?, theme = ?, font_size = ?, display_time_zone = CASE WHEN ? = TRUE THEN CAST(? AS VARCHAR(64)) ELSE display_time_zone END WHERE user_id = ?` を、生成キーの列名 `user_id`・`display_time_zone`（小文字）を求めて 1 文で流す。1 行なら生成キーから 2 列を取って返し、0 行なら空。氏名・言語・テーマ・文字の大きさは、束縛の直前に伏せ字の型から取り出す（取り出した値を変数・ログ・例外に残さない。BR1.3・NFR2.3）。例外は受けずに外へ出し、Hibernate の変換に任せる。`SavedPreferences` を返す小さな実装（record の入れ子。`getUserId`・`getDisplayTimeZone`）を `UserRepositoryImpl` の中に持つ。持続化の文脈は書き換えない（今と同じ）。
  - Spring Data が `UserRepositoryImpl` を組み込むことは、Step 7 の `UserRepositoryIT` の既存のテストが通ることで確かめる。

- [x] **Step 7: 文の数の確かめと、既存の書き直しのテスト（DB アクセスの層のテスト。H2 の結合テスト）**
  - `common/testsupport/StatementCounter`（テストの部品。`@TestConfiguration` の `BeanPostProcessor`。Bean の名前 `dataSource` だけを `java.lang.reflect.Proxy` で包み、`unwrap`・`isWrapperFor` を元へ通し、`prepareStatement` の戻り値も包んで実行の回数を数える。`getConnection` の回数も数える。数えを 0 に戻す口を持つ。依存を足さない）と `StatementCounterTest`（数え方・`unwrap`・元の `DataSource` を包み直さないことの 3 件程度）。`ConnectionAcquireCounter`（`appearance/testsupport`）は参照も書き換えもしない。
  - `user/repository/UserRepositoryIT`（既存）の全件が書き直した後も通ること。足すもの（別のクラス `SavePreferencesStatementCountIT` でもよい）: `savePreferences` が 1 回の呼び出しで文 1 本・接続を借りる回数が変わらないこと（NFR1.1）、本人がいなければ空、`change` が偽なら表示のタイムゾーンは今の値のまま、`change` が真で `zone` が null なら列が空になって返る、同じトランザクションで先に変更したエンティティが `flush` で送られてから更新が走ること（R-02 (1)）。
  - `role/store/` の結合テスト（`WorkRoleSelectionStatementCountIT` など）: 行が既にあるとき作業ロールの保存が文 1 本・接続を借りる回数 1、行が無い最初の 1 回は文 2 本（NFR1.2）。`dslmanage/repository/` の同じ確かめ（プレビューを置く）。外部キーの違反（存在しない利用者）が今までと同じ区分（`Referenced`）になること（R-02 (2) の確かめ）。
  - `PreferencesSecretLeakIT`（既存）が通ること（TRACE を有効にして保存しても氏名・タイムゾーンが出ない。NFR2.3）。

- [x] **Step 8: 同時の実行の結合テスト（DB アクセスの層のテスト。H2）**
  - `common/testsupport/` に待ち合わせの部品（`CountDownLatch` と、H2 の `INFORMATION_SCHEMA.SESSIONS` で別の接続が待っている状態を上限つきで確かめる口。実時刻の `sleep` を合否に使わない。`group/testsupport/UncommittedWrite` の既存の形に倣う。既存の部品は書き換えない）を置く。
  - `role/store/WorkRoleSelectionMergeIT` を新しい手順の期待に書き直す（H2 の `MERGE ... KEY` の見張りという目的は、新しい手順では使わないため、クラスの説明を書き直す）。確かめるもの（待ち合わせで確実に作る）: (a) 先の側が行の無い鍵へ挿入を未確定のまま持ち、後の側が書くと、先の確定の後に後の側が 23505 を受けて更新をやり直し、最終の行は後の側の値になる（後勝ち。保持は約 500ms）、(b) 先の側が上限（H2 約 4 秒）を超えて持つと、後の側は上限切れ（`RowLockFailures` で上限切れ）として `Busy("WORK_ROLE_SELECTION_KEY")` になる（上限切れの確かめ 1 本）、(c) (b) の後に、セーブポイントまで戻った接続で次の文が通る（手順の内側の確かめ。`KeyedUpsertExecutorTest` に置いてもよい）、(d) 行が既にあるとき（行の排他）は直列になり、後の側の値が残る。
  - `dslmanage/repository/` に `DslPreviewPlaceConcurrencyIT`（新しい。今は無い）: プレビューを置く 2 つのトランザクションが同じ鍵（スロット 1）へほぼ同時に書いたとき、後に確定した方が残る（(a) と同じ形）、上限切れの確かめ 1 本。
  - 10MB ちょうどの本文の往復（プレビューを置いて読み、バイト列が一致する。BR4.1・FR3.6）が H2 の既存のテストに無ければ足す（既存の `DslManageRepositoryIT` を先に調べる）。`ddl-auto: validate` を含む H2 の結合テストの全件が通ること。
  - 足すテストの時間の合計は、全件の `verify` の増加が約 30 秒以内の目安（NFR5.2。差は `code-summary.md`）。

- [x] **Step 9: 構造の検査（業務処理の層・構造のテスト。NFR9.2）**
  - `backend/src/test/java/cherry/mastersmith/common/persistence/CommonPersistenceBoundaryArchitectureTest`（新しい）: `common.persistence` が `role`・`dslmanage`・`user` などの機能のパッケージに依存しないこと、`TraceAspect` の対象の層（web・service・domain・repository）に置かないこと。`user.repository` の `UserRepositoryImpl`・`UserRepositoryCustom` が既存の層の検査に当たること。既存の `ArchitectureTest`・3 つの機能の境界テストは書き換えず、そのまま通ること（緩めない・消さない。落ちたときは直さず、依頼者に諮る）。

- [x] **Step 10: 実行して通す**
  - Step 2〜9 のテストを `unit-test-instructions.md` の命令で流し、すべて通ることを確かめる。通らないものは直してから先へ進む。結合テストは、影響の洗い出し（Step 1）で挙げたクラスと、足したクラスに `-PitTests` で絞って流す（コンテナの実行環境は colima の値を渡す）。全件の結合テストとバックエンドのカバレッジの下限は、Build and Test の全件の `./gradlew verify` で確かめる（重ねて流さない）。
  - `common.persistence`・`user.repository`・`role.store`・`dslmanage.repository` のカバレッジの参考値（行・分岐）を `unit-test-instructions.md` の手順で読んで `code-summary.md` に書く（新しい分岐は単体テストと H2 の結合テストだけで全部通す。BR6.3）。

- [x] **Step 11: 記録と traceability（文書・追跡）**
  - `unit-test-instructions.md` の命令どおりにすべて流し、結果を `code-summary.md` に書く（ファイルの一覧・実装の決め・テストの結果・流していないテストとその理由・計画との差・**上流との差（上の 3 点）**・実測した確かめ（Hibernate が外へ出た `SQLException` を変換するか）・Build and Test に渡すこと・U3 に渡すこと（PostgreSQL での確かめ・`55P03`・10MB の往復・検索）・U4 に渡すこと（検索の Unicode の端の違い）・NFR1.4 の「PostgreSQL の書き込みの所要時間は測っていない」）。
  - `aidlc engine sensor-traceability --stage code-generation --unit u2-portable-sql --output-path <この段の traceability.json の絶対パス> --write-upstream` で `traceability.json` を作り、各行の `status` と `target`（実在するコード・テストのファイル）を埋める。
  - `source-manifest.json` に、この単位が作った・変えたすべてのパスを書く。
  - コミットの区切り（提案して承認を得る。各コミットの時点でコンパイルが通る）: (1) `common.persistence` の手順と `RowLockFailures` とそのテスト、(2) `RoleStore`・`DslPreviewRepository` の書き換えとテスト（`StatementCounter`・待ち合わせの部品・結合テストを含む）、(3) `UserRepository` のカスタムのフラグメントとそのテスト、(4) 構造の検査。

## Assumptions & Open Questions

- [assumption] Hibernate 7.4.13 は `Session.doWork`・`doReturningWork` の外へ出た `SQLException` を、`executeUpdate` と同じ型の `PersistenceException` に変換する（NFR 設計の Assumptions）。Step 7 の確かめ（外部キーの違反・上限切れ）で確かめ、違えば手順の外側で包む。
- [assumption] `entityManager.flush()` は、呼び出し元の業務処理の層が持つトランザクションの中で呼べる（今の `executeUpdate` と同じ前提）。
- [assumption] H2 の未確定の挿入への待ちは約 4 秒（H2 2.4.240 の観測。機能設計の捨ての試し F5）。H2 を上げるときは、上限切れの 2 本の保持の長さを見直す。
- [assumption] `StatementCounter` の `Proxy` は、内部DB の `DataSource`（Bean の名前 `dataSource`）が Hikari であることに頼らず、`unwrap`・`isWrapperFor` を通す形で作る。Hikari の指標の後処理が元に届かない場合は、数えるテストの中でだけ包みを外す（本体の `DataSource` の Bean は変えない）。
- [assumption] 時刻の束縛は、手順の引数に `OffsetDateTime`（UTC）を渡し、`setObject` で H2・PostgreSQL の両方に通ると見込む（`RoleStore` の今の束縛と同じ型。機能設計の捨ての試し F4 の `CAST` は標準の `MERGE` の話で、直接の代入には要らない）。通らない場合は、型を足して原因を `code-summary.md` に書く。
- Open: なし。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`functional-design-questions.md`（Q1: A・Q2: A・Q3: A）・`reviews/review-01.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-requirements/performance-requirements.md`・`security-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`・`reviews/review-01.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/reliability-design.md`（DD-1〜DD-3・上流との差）・`performance-design.md`（DD-4・DD-5）・`security-design.md`・`scalability-design.md`・`observability-design.md`・`logical-components.md`・`reviews/review-01.md`（R-01〜R-06）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`・`reviews/review-01.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/code-summary.md`（`@Lob` の 2 列を直した形・`internaldb` の境界テスト）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/contract-design/contract-summary.md`（C3・C5）
- `aidlc/spaces/default/knowledge/aidlc-shared/checklist-auth-audit.md`（秘密情報の漏えい）・`checklist-dsl.md`（DSL の本文）・`checklist-timezone.md`（時刻の束縛）
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`
- コードで確かめたこと: `backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java`、`role/store/RoleStore.java`・`RoleStoreClassifier.java`、`dslmanage/repository/DslPreviewRepository.java`、`common/persistence/RowLockFailures.java`、`backend/src/main/resources/db/migration-postgresql/V5__*.sql`（`dsl_previews` の制約の名前）、`backend/src/test/java/cherry/mastersmith/role/store/WorkRoleSelectionMergeIT.java`、`dslmanage/DslManageBoundaryArchitectureTest.java`・`role/RoleBoundaryArchitectureTest.java`・`user/UserBoundaryArchitectureTest.java`

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Methodology evidence**: 今の `team.md` で確定した決まりを引き継いだ。各 Bolt で層ごとに実装を書いてから同じ Bolt の中でその層のテストを書いて流す形で、これまでの Intent もこの形で進めてきた。今回の Intent（内部DB に PostgreSQL を足す）で進め方を変える理由は見つかっていない。\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- 統合の前の `./gradlew verifyQuick` は、バックエンドのカバレッジの下限を判定しない（単体テストの記録だけから参考値を `backend/build/reports/jacoco/quick/` に出して表示し、下回っても失敗にしない）。バックエンドの下限は、全件の `./gradlew verify` を流す場面（Way of Working）と CI で判定する。画面のカバレッジの下限は `verifyQuick` でも判定する。（2026-11-08 Intent 261107-verify-quick-local で追加）\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる（外すパッケージは無い）。全体の合計で判定していた既存のパッケージの一覧（`backend/build.gradle.kts` の `packagesJudgedByTotal`。最後は 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`））は、Intent 261108-ci-checks-release で `common.health` にテストを足して下限を満たし、一覧とそれを使った除外の仕組みを消した。パッケージを外す一覧を再び作らず、下限を満たさないパッケージはテストを足して満たす。（2026-11-08 Intent 261107-verify-quick-local で書き換え）（2026-11-09 Intent 261108-ci-checks-release で書き換え）\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを全件の `./gradlew verify` の中と CI で毎回実行する。統合の前の `./gradlew verifyQuick` では、`-PitTests` で絞ったときに当たるものだけを流す。コンテナの実行環境が無いときの扱いは Way of Working のとおり。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- テストを足した Bolt の後は、統合の前に流した1コマンドの検査（`./gradlew verifyQuick`。全件の場面に当たる Bolt では `./gradlew verify`）の時間を、Build and Test では全件の `./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB を使うテストは組み込みの H2 を基本とし、コンテナではなく本番と同じ組み込みの H2 で行う。H2 での結合テストの全件は今のまま残す。内部DB を PostgreSQL で動かす経路に固有の振る舞いを確かめる結合テストは、H2 の全件とは別のバリエーションとして作り、版を固定したイメージのコンテナで起動した実際の PostgreSQL で行う（固い制約は `project.md` の Mandated）。このテストは明示の指定（Gradle の指定。名前は設計で決める）を付けたときだけ流し、通常の `./gradlew verify`・`./gradlew verifyQuick`・CI（`develop` へのプッシュ）では流さない。流す場面は決めず、必要と判断したときに明示して流す（CI で流すときも明示の指定を付けた実行に限る。形は設計で決める）。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストと、内部DB の PostgreSQL の結合テストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) （2026-11-14 Intent 261114-internal-db-postgresql で書き換え。H2 を基本とし、PostgreSQL に固有の結合テストを明示したときだけ流す形にした）\n\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実行し直し、実測の数字だけを報告する。`./gradlew verifyQuick` の JaCoCo の値は単体テストだけの参考値で、カバレッジの報告には使わない。 (learned 2026-09-23)  （2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。すべての組で行うのは、その Intent で変えた画面だけとし、変えていない画面は E2E の既存の検査に任せる（2026-11-07 依頼者の決定で緩めた）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify`、または `-PitTests` を付けた `./gradlew verifyQuick` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、`verify` ではパッケージごとのカバレッジの下限で失敗する。`-PitTests` を付けた `verifyQuick` では、バックエンドのカバレッジの下限を判定しないため、飛ばさずに失敗する（CI と同じ扱い）。 (merged 2026-11-04)（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:552cb5658f2cb47e1e82a7327ac8ad923769dea2d1ef59be8f82d75eefe758af",
  "contract_sha256": "sha256:a556f2389fe8ebabfea24044e818005bb3c9a4fafaa7645490018229aa364acb"
}
```
