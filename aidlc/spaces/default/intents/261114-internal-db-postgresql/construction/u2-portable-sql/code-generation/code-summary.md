# コード生成の要約 — u2-portable-sql

H2 にしか通らなかった書き込みの文 3 つ（`savePreferences` の `FINAL TABLE`、作業ロールの保存の `MERGE ... KEY`、プレビューを置くの `MERGE ... KEY`）を、H2 と PostgreSQL の両方に通る書き方に直した。この単位で流したのは H2 までで、PostgreSQL に頼る確かめは U3 に渡す。コミットはまだ積んでいない（計画 Step 11 のコミットの区切りを依頼者に提案して承認を得る）。

## ファイルの一覧

アプリのソースの一覧は `source-manifest.json` と同じ。パスは `backend/src/main/java/cherry/mastersmith/`（本体）と `backend/src/test/java/cherry/mastersmith/`（テスト）からの相対。

### 本体

| 区分 | ファイル | 内容 |
|---|---|---|
| 新規 | `common/persistence/KeyedUpsert` | 表ごとの定数（`WORK_ROLE_SELECTIONS`・`DSL_PREVIEWS`）。更新・挿入の文、重なりの印、重なりでない印を持つ |
| 新規 | `common/persistence/KeyedUpsertExecutor` | 更新 → 挿入（セーブポイント）→ 重なりなら更新やり直しの共通の手順 |
| 新規 | `common/persistence/KeyedUpsertLostException` | 重なりの後の更新が 0 行のときの `PersistenceException`（文は固定、値なし） |
| 新規 | `common/persistence/UpsertOutcome` | `UPDATED`・`INSERTED`・`UPDATED_AFTER_CONFLICT` |
| 変更 | `common/persistence/RowLockFailures` | SQLState `55P03` を上限切れとして足した |
| 新規 | `user/repository/UserRepositoryCustom`・`UserRepositoryImpl` | `savePreferences` を JDBC の生成キーで 1 文のまま流す |
| 変更 | `user/repository/UserRepository` | `savePreferences` の `@Query` と宣言を外し、`UserRepositoryCustom` を継承 |
| 変更 | `role/store/RoleStore` | `MERGE_SELECTION` を外し、手順の呼び出しにした |
| 変更 | `dslmanage/repository/DslPreviewRepository` | `PLACE_SQL` を外し、手順の呼び出しにした |
| 変更 | `dslmanage/domain/DslPreviewRecord` | クラスの説明だけ（この単位で今回変えた分。u1 の `@JdbcTypeCode` の変更は u1 の分） |

### テスト

- 新規: `common/persistence/KeyedUpsertExecutorTest`（21 件）・`CommonPersistenceBoundaryArchitectureTest`（4 件）、`common/testsupport/StatementCounter`・`StatementCounterTest`（3 件）・`H2BlockedSessions`・`CommittableWrite`、`user/repository/SavePreferencesStatementCountIT`（5 件）、`role/store/WorkRoleSelectionStatementCountIT`（2 件）・`WorkRoleSelectionFailureClassificationTest`（4 件）、`dslmanage/repository/DslPreviewStatementCountIT`（1 件）・`DslPreviewPlaceConcurrencyIT`（2 件）
- 書き直し: `role/store/WorkRoleSelectionMergeIT`（3 件）
- 変更: `common/persistence/RowLockFailuresTest`（`55P03` の行を足し 15 件）、`businessdata/BusinessDataBoundaryArchitectureTest`（下の「計画との差」1）

件数は担当の報告の値（クラス内の `@Test` 系の数は `KeyedUpsertExecutorTest` が 11 個の注釈で、パラメーターを使うテストの展開後が 21 件）。

変えていないもの: `build.gradle.kts`・`backend/gradle.lockfile`・`gradle/`・`.github/`・`docker/`・`backend/src/main/resources` の差分は無いことを `git diff --stat` で確かめた（NFR8.1・NFR4.6・NFR4.8）。

## 影響の洗い出し（Step 1）

- `savePreferences`・`saveWorkRoleSelection`・`DslPreviewRepository.place`・`RowLockFailures` の呼び出し元と既存のテストは、メソッドの形を変えないため書き換えていない。結合テストの絞り込みには `UserRepositoryIT`・`PreferencesPartialUpdateIT`・`DisplayTimeZoneKeptByOtherUpdatesIT`・`PreferencesSecretLeakIT`・`DslManageRepositoryIT`・`DslConcurrencyIT`・`RoleStoreKeyGuardIT` などを含めた。
- 既存の境界テスト（`ArchitectureTest`・`UserBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`DslManageBoundaryArchitectureTest`）は `cherry.mastersmith.common..` への依存を許しており、緩めていない（R-06）。
- H2 の `MERGE` の文字列は、`RoleFixtures`・`UserAdminFixtures`・`RoleStoreClassificationTest` のテスト側に残っている（範囲外。U3 に渡す）。

## 実装の決め

- 手順は `common.persistence` の 1 部品に閉じ、`role`・`dslmanage`・`user` に依存しない。ログは出さない（NFR2.6）。
- 更新の文を先に流し、0 行ならセーブポイントを張って挿入する。挿入が SQLState `23505` かつ文に重なりの印を含み、重なりでない印（`DSL_PREVIEWS` では `UK_DSL_PREVIEWS_PREVIEW_ID`）を含まないときだけ、セーブポイントまで戻して更新をやり直す。それ以外の `SQLException` は、セーブポイントまで戻してからそのまま外へ投げる。
- `KeyedUpsertLostException` は `jakarta.persistence.PersistenceException` の下に置いた（R-02）。`RoleStore` の `catch (PersistenceException)` で受かり、`RoleStoreClassifier` の想定外の経路に入る。
- 値は JDBC の位置の引数で束縛する。`byte[]` は `setBytes` で複製しない。呼び出しの前に `entityManager.flush()` を呼ぶ。
- `savePreferences` は `UserRepositoryImpl` で、生成キーの列（`user_id`・`display_time_zone`、小文字）を求めて 1 文で流す。氏名などは束縛の直前に伏せ字の型から取り出す。
- 上限切れの分類: H2 の上限切れ（`HYT00`・50200）・`55P03`・PostgreSQL の形の `23505` の判定を、`WorkRoleSelectionFailureClassificationTest` と `KeyedUpsertExecutorTest` の表で確かめた。

## テストの結果

担当の報告と、指揮役が再実行した分。

- 単体テスト全体 3067 件のうち、境界テストの 1 件が落ちた（`BusinessDataBoundaryArchitectureTest` の「JDBC の文の所有者」の規則）。許可の 2 クラスを足した後、`BusinessDataBoundaryArchitectureTest`・`ArchitectureTest`・`common.persistence.*` を指揮役が再実行して全件通過し、`spotlessCheck` も通過した。
- H2 の結合テスト 50 クラス（`-PitTests` で絞り込み）は全件通過、失敗 0、飛ばし 0。

## 流していないテストとその理由

- 全件の `./gradlew verify`（バックエンドのカバレッジの下限とパッケージごとの下限の判定を含む）: Build and Test で流す（重ねて流さない決まり）。
- E2E: Build and Test と リリースの前に流す決まり。この単位は画面を変えない。
- PostgreSQL のテスト（`:backend:internalDbPostgresTest`）: この単位は足さない（U3）。したがって `55P03`・小文字の制約名の実際の文・PostgreSQL でのセーブポイントの確かめは未実施。
- 既存の監査の結合テスト（`AuditAuthenticationEventsIT` など）: 絞り込みに含めていない。NFR4.9 は Build and Test の全件の `verify` で確かめる。

## 実測した確かめ（担当の報告）

- Hibernate は `doReturningWork` の外へ出た `SQLException` を `PersistenceException` に変換する。`RoleStoreKeyGuardIT` で、外部キー違反が `Referenced(USER)`、上限切れが `Busy` になった（計画の前提のとおり。手順の外側で包む必要は無かった）。
- 文の数と接続: `savePreferences` は文 1・接続 1。作業ロールの保存とプレビューを置くは、行があれば文 1、最初の 1 回は文 2、接続は 1。
- 上限切れの待ち（実アプリの H2 の設定）: 作業ロール約 4035 ms、プレビュー約 4099 ms。2 本で約 8.1 秒。
- 上限切れの後の同じトランザクションの続行: セーブポイントまで戻り、別の保存が `Done` になり、行も読める。
- 同時の実行は後勝ち。
- 分類: H2 の上限切れ・`55P03`・PostgreSQL の形の `23505` は `Busy("WORK_ROLE_SELECTION_KEY")`。`KeyedUpsertLostException` は想定外として `RoleStoreUnexpectedException` になり、文に行の値は出ない。
- 10MB の往復は `DslManageRepositoryIT.tenMegabytesRoundTrip` が既にあり、新しい `place` 経由で通る（足していない）。

### カバレッジの参考値（判定ではない。担当の報告）

| パッケージ | 行 | 分岐 |
|---|---|---|
| `common.persistence` | 93/95 | 47/48 |
| `role.store` | 271/278 | 88/96 |
| `dslmanage.repository` | 72/72 | 4/4 |
| `user.repository` | 23/23（`UserRepositoryImpl` の整理後） | 5/6 |

`user.repository` のパッケージ全体は分岐 11/12 の見込み（整理前の実測は行 54/56・分岐 12/18）。閾値・除外は変えていない。最終の判定は Build and Test の全件の `verify`。

## 計画との差

1. 計画の抜け: Step 1 の境界テストの洗い出しに `businessdata` の「JDBC の文の所有者」の規則が入っていなかった。`BusinessDataBoundaryArchitectureTest` の `ALLOWED_STATEMENT_OWNERS` に `KeyedUpsertExecutor` と `UserRepositoryImpl` の 2 クラスを足した。依頼者が「2 クラスだけを足す」を承認済み。規則は緩めていない。
2. 補助クラス `H2BlockedSessions`・`CommittableWrite`、テスト `WorkRoleSelectionFailureClassificationTest` を足した。
3. `StatementCounterTest` は、`DriverManagerDataSource` を使う形にした。
4. プレビューの `placed_by_user_id` に外部キーは無いため、「外部キー違反が Referenced になる」確かめは作業ロール側だけにした。
5. `UserRepositoryImpl` は、カバレッジのため `try-with-resources` の途中の `return` をやめ、最後に 1 回返す形に整理した。
6. 計画 Step 8 (c)（上限切れの後の続行）は、`KeyedUpsertExecutorTest` ではなく `WorkRoleSelectionMergeIT` の 3 件目に置いた。

## 上流との差

1. 機能設計 BR2.4 は「SQLException を外へ出さない」と書くが、NFR 設計は「重なりについてだけ」にした。実装は NFR 設計に従った（重なりでない `SQLException` は外へ投げる）。
2. `KeyedUpsert` の列挙が「重なりでない印」を持つ（R-01 の答え）。BR2.3 の判定は「23505 かつ表の名前」だが、`dsl_previews` の `preview_id` の一意の制約の違反を重なりと取り違えないために、印の除外を足した。
3. 上限切れの確かめは 2 本（作業ロール 1 本・プレビュー 1 本。DD-5）。NFR5.2 は 1 本と書く。足す時間は約 8.1 秒で、目安の約 30 秒以内。

## Build and Test に渡すこと

- `:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で、パッケージごとのカバレッジの下限（特に `common.persistence`・`user.repository` の分岐）と、足したテストの時間（NFR5.2・NFR5.3）を実測して記録する。
- NFR4.9: 既存の監査の結合テストが通ること。
- NFR1.4: PostgreSQL の書き込みの所要時間は、この単位では測っていない。
- E2E は Intent の最後に 1 回流す。

## U3 に渡すこと

- `55P03` の実際の上限切れ、PostgreSQL のセーブポイント（重なりの後の続行・`25P02` にならないこと）、小文字の制約名（`pk_work_role_selections`・`dsl_previews_pkey`・`uk_dsl_previews_preview_id`）の実際の文と判定。
- 10MB の本文の往復（プレビューを置いて読む）。
- 検索の結果が H2 と PostgreSQL で同じになること。
- H2 の `MERGE` の文字列が `RoleFixtures`・`UserAdminFixtures`・`RoleStoreClassificationTest` に残る。U3 がフィクスチャを PostgreSQL のテストで共用するときは気づく必要がある。

## U4 に渡すこと

- 検索の Unicode の端の違い（`İ`・語末シグマ・`ǅ`）は既知の違いとして文書に書く（Q3: A）。
- Flyway の INFO 行が JDBC の URL のホスト・ポート・DB 名を出す件を README に書く。

## 依頼者に確かめたいこと

なし。`ALLOWED_STATEMENT_OWNERS` への 2 クラスの追加は承認済み。ほかに新しく確かめたい点は無い。コミットの区切り（計画 Step 11 の 4 つ）の提案は、記録のあとで別に尋ねる。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/code-generation/code-generation-plan.md`・`unit-test-instructions.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/functional-design/rules.md`・`nfr-requirements/`・`nfr-design/`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/code-generation/code-summary.md`・`traceability.json`
- 上の「ファイルの一覧」のソースと `git status`・`git diff --stat`（実際の変更ファイルの確認）
- 件数・実測値・カバレッジの参考値・H2 の結合テスト 50 クラスの結果は、実装の担当の報告による（指揮役が再実行したのは境界テストと `common.persistence.*`）

## Assumptions & Open Questions

None.
