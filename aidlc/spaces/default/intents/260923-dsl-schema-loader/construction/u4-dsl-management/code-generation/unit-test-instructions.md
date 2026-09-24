# Unit Test Instructions — U4 DSL の管理（u4-dsl-management）

U4 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の結合テスト）。U4 は既存の `common`・`config`・`audit` に手を入れるため、それらの既存のテストを保つコマンドもあわせて示す。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ、Mockito | `backend/build.gradle.kts` の `tasks.test`（`*Test`）と `integrationTest`（`*IT`） |
| HTTP の結合テスト | 既存の `common/testsupport/HttpTestClient`、ログインとトークンの補助 `auth/testsupport/`、管理者の利用者 `access/testsupport/AdminTestUsers` | 既存 |
| 監査の確かめ | 既存の `audit/testsupport/`（`AuditRows`・`FailingAuditEventRepositoryConfig` など） | 既存 |
| ログの確かめ | 既存の `common/testsupport/LogEvents`・`JsonLogRecords` | 既存 |
| 対象DB のコンテナ | Testcontainers（U1 で導入済み）と `targetdb/testsupport` の仕組み | U1 のまま |
| 構造の検査 | ArchUnit 1.5.0（既存） | 既存 |
| カバレッジ | JaCoCo 0.8.15（既存） | 既存 |

新しいテストの設定のファイルと依存は足さない。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。

U4 の単体テスト:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.*'
```

U4 の結合テスト（組み込みの H2 でアプリを起動する。照合と生成の一部は対象DB のコンテナを使う）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.*'
```

U4 が手を入れる既存の部分のテスト（本文の上限・エラー応答・監査・セキュリティの設定。決定 C の3件を含む）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.common.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.config.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.common.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.config.*'
```

- `dslmanage` のコマンドは U3 のテストも含む（同じパッケージの下）。U3 のテストが通り続けることも確かめる。
- 結合テストはコンテナの実行環境（colima）と、README の「対象DB」の節の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を前提にする。無いときは U1 と同じく対象DB のテストだけが警告つきで飛ぶ（CI では失敗）。飛ばした状態では統合しない。
- テストの件数を報告するときは、`:backend:cleanTest` または `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（project.md の Testing Posture）。

Build and Test からの戻し（Loop-back 1、計画の Step 18〜20）の確かめ:

```bash
# 拡張の登録の順の構造の検査（U1 で足したもの。違反 0）
./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.targetdb.testsupport.ExtensionOrderArchitectureTest'
# コンテナの実行環境に届かない状態（colima は止めず、接続先を存在しない場所に向ける）。期待: 失敗 0・対象DB のテストは警告つきで SKIPPED
DOCKER_HOST=unix:///nonexistent/docker.sock env -u CI ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.*'
# 内部DB の終了時の詰め直しの確かめ（組み込みの H2 の単体テスト）
./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.config.*'
```

## 3. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上。
- パッケージごと: `cherry.mastersmith.dslmanage` の下の新しいパッケージ（`web`・`service`・`domain`・`repository`）で行 80%・分岐 70% 以上。`dslmanage.generate`（U3）の下限も保つ。
- 既存のパッケージ（`common.web`・`common.error`・`config`・`audit` など）は全体の合計で判定される（U1 の実測の決定）。手を入れた部分にもテストを足し、全体の合計を下げない。
- 計測から外すのは既存の除外だけ。U4 のために除外を足さない（設定の型は既存の `*Properties` の除外に当たる名前にする）。

## 4. 差し替え（モック・スタブ）の方針

- 業務処理の単体テストでは、U1 の `TargetSchemaReader`・U3 の `DefaultDslGenerator`・U4 のリポジトリを差し替える。U2 の `DslReader` と `ActiveDslModelHolder` は本物を使う。
- 結合テストは、内部DB を組み込みの H2（既存の `TestDatabase`）で、対象DB を U1 の Testcontainers の仕組みで動かす。応答しない対象DB は、U1 と同じくテストの中で開いた待ち受け（接続を受け付けて何も返さない）で作る。
- 同時の適用と `DSL_BUSY` の重なりは、テスト用の差し込み口（待ち合わせの `CountDownLatch` を持つ設定。本番のコードは変えない）で確実に作る。`Thread.sleep` や実時刻に依存しない（project.md の Testing Posture の学び）。
- 適用の途中の失敗（AC4.2.4）と監査の書き込みの失敗（AC6.3.3）は、テスト用の設定で失敗するリポジトリを差し込む（既存の `FailingAuditEventRepositoryConfig` の形）。
- 時刻は既存の注入できる時計（`AuthClockConfig`）から取り、テストでは固定の時計を使う。

## 5. テストのデータ

- DSL の本文は、U2 の見本（`backend/src/test/resources/cherry/mastersmith/dsl/valid-sample.yaml`）と、テストの中で組み立てる小さな DSL を使う。10MB ちょうどと 1 バイト超えの本文、101 件以上の誤りを含む本文、101 件以上の未設定の表示名を含む本文はテストの中で作る（大きなファイルをリポジトリに置かない）。
- 利用者は既存の補助で管理者と管理者でない利用者を作る。パスワードとトークンは既存の補助の仕組みで作り、固定の秘密の値を書かない。
- 対象DB のスキーマは U1 と同じく、テストのクラスごとに名前の重ならないスキーマを作って消す。
- テストの説明文（`@DisplayName`）は英語。テストデータは日本語でよい。
