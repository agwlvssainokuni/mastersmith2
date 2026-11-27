# 単体テストの手順 — u2-portable-sql

この単位のテストを流す命令です。どれもこの単位のクラスだけに絞っている（プロジェクト全体の命令ではない）。Build and Test が単位ごとの命令を順に流す。

## テストの道具と設定

- 道具: JUnit 5（Jupiter）・AssertJ・ArchUnit・Spring Boot Test・H2（組み込み。本物の接続）。新しい道具・依存は足さない（`build.gradle.kts`・lockfile に差分を出さない）。テストの設定（`backend/build.gradle.kts` の `tasks.test`・`integrationTest`）は、今の `-Dh2.compactThreads=1`・`user.timezone=Asia/Tokyo` などをそのまま使う。
- 置き場: `backend/src/test/java/cherry/mastersmith/` の `common/persistence/`（手順・`RowLockFailures`・構造の検査）、`common/testsupport/`（`StatementCounter`・待ち合わせの部品）、`role/store/`・`dslmanage/repository/`・`user/repository/`（結合テスト）。単体は `XxxTest`、Spring や DB を起動する結合は `XxxIT`。説明文（テスト名・`@DisplayName`）は英語、テストデータの日本語は可、メールアドレスは予約のドメイン（`example.com`）だけ。
- この単位は PostgreSQL のテストを足さない（U3 が U1 の専用のタスク `:backend:internalDbPostgresTest` に足す）。コンテナを使うのは、既存の対象DB のテストが全件の結合テストに含まれるときだけ。

## 命令（この単位だけ）

colima の PC では、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡してから流す。

1. 単体テスト（コンテナ不要。手順・分類・`StatementCounter`）: `./gradlew :backend:test --tests 'cherry.mastersmith.common.persistence.*' --tests 'cherry.mastersmith.common.testsupport.*'`
2. 構造の検査（既存の境界テストが緩んでいないことの確認を含む）: `./gradlew :backend:test --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.user.UserBoundaryArchitectureTest' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' --tests 'cherry.mastersmith.dslmanage.DslManageBoundaryArchitectureTest' --tests 'cherry.mastersmith.common.persistence.CommonPersistenceBoundaryArchitectureTest'`
3. H2 の結合テスト（書き直した文・文の数・同時の実行。当たるテストが 1 つも無いときは失敗になる）: `./gradlew :backend:integrationTest -PitTests='UserRepositoryIT,PreferencesPartialUpdateIT,DisplayTimeZoneKeptByOtherUpdatesIT,PreferencesSecretLeakIT,SavePreferencesStatementCountIT,WorkRoleSelectionMergeIT,WorkRoleSelectionStatementCountIT,DslManageRepositoryIT,DslConcurrencyIT,DslPreviewPlaceConcurrencyIT,Role*IT'`（クラスの名前は、Step 1 の洗い出しの結果と足したクラスに合わせて Step 10 で確定する）
4. 既存の単体テストで `savePreferences`・`DslPreviewRepository`・`RoleStore` を使うもの: `./gradlew :backend:test --tests 'cherry.mastersmith.user.service.UserPreferencesServiceTest' --tests 'cherry.mastersmith.dslmanage.service.DslLifecycleTest' --tests 'cherry.mastersmith.dslmanage.service.DslStartupLoaderTest'`

3 は、コンテナで動かすテスト（`-PitTests` が対象DB のテストに当たるとき）を飛ばさない。コンテナの実行環境が無いとき、3 は `-PitTests` を付けているため、対象DB のテストに当たる場合に失敗になる（CI と同じ扱い）。この単位のクラスはコンテナを使わない。

## 期待するカバレッジ

- 新しい部品 `common.persistence`（手順・列挙・例外）と、書き換えた `user.repository`・`role.store`・`dslmanage.repository` の新しい分岐は、**普段の `verify` に入るテスト（単体と H2 の結合テスト）だけで**全部通す（BR6.3）。手順の分岐: 更新 1 行・更新 0 行 → 挿入が通る・挿入が重なり → 更新のやり直し・重なりの後の更新が 0 行・重なりでない失敗・更新の上限切れ・`55P03`。計測の除外を足さない。
- プロジェクト全体の下限（全体の合計と、すべてのパッケージごとの行 80%・分岐 70%）は、Build and Test が `:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で判定する。この単位のテストだけの実行では判定しない。閾値は、この単位のために変えない。
- この単位の参考値を見るとき（判定ではなく読み取り）: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest -PitTests='<上の 3 のクラス>' :backend:jacocoTestReport` の後に、`backend/build/reports/jacoco/test/jacocoTestReport.xml` から `cherry/mastersmith/common/persistence`・`user/repository`・`role/store`・`dslmanage/repository` の行・分岐の値を読む。選んだテストがすべて通っていれば、閾値の失敗は失敗したテストとは扱わない。

## モック・代用の指針

- 手順（`KeyedUpsertExecutor`）は、JDBC の動作を模擬せず、本物の H2 の接続で動かす（セーブポイント・23505・上限切れが本物の振る舞いで確かめられるため）。重なりの判定（`isKeyConflict`）は、値と例外を直接渡す。
- 同時の実行は、待ち合わせ（`CountDownLatch`・H2 の `INFORMATION_SCHEMA.SESSIONS` の待ち状態を上限つきで確かめる）で確実に作る。実時刻の `sleep` を合否に使わない。上限切れの確かめだけ、H2 の上限（約 4 秒）を超える保持が要る（作業ロール 1 本・プレビュー 1 本）。待ちの上限の秒数は、テストの中で定数に書かず、上限切れが起きる長さとして測る。
- `StatementCounter` は、内部DB の `DataSource`（Bean の名前 `dataSource`）だけを包み、本体の Bean は変えない。`ConnectionAcquireCounter` は参照しない。
- PostgreSQL に頼る確かめ（`55P03`・小文字の制約の名前の実際の文・10MB の往復・検索の結果の同じさ）は、H2 で代用せず U3 に渡す（`project.md` の Mandated）。この単位は、PostgreSQL の形の文・SQLState を「値」として渡して分類の表だけを確かめる。

## テストデータの扱い

- H2 のテストは、一時ファイルか名前の一意なメモリのデータベースを使い、実行順に依存しない。テストごとに自分が作ったデータだけを使い、自分で後始末をする（既存の `TestDatabase` の形）。
- 秘密情報・実在の宛先をテストデータに置かない。氏名・タイムゾーンのテストデータは日本語・予約のドメインのメールアドレスだけ。
- 時刻に依存する処理は無い（作業ロールの保存・プレビューを置くは、呼び出し側が渡す時刻を書くだけ）。

## 流した結果の記録

結果（通った件数・失敗・飛ばしたもの）は `code-summary.md` に書く。流していないテストは、理由をつけて書き、流したものとして書かない。
