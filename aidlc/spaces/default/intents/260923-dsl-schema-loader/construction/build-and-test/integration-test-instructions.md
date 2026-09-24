# 結合テストの手順（integration-test-instructions）

Intent `260923-dsl-schema-loader` の5つの単位（U1 対象DB・U2 DSL の定義・U3 既定の DSL の生成・U4 DSL の管理・U5 DSL の管理画面）をまたぐ境界を、どのテストがどう確かめるかと、その実行のしかた。単体テストは各単位の `code-generation/` の記録が受け持つ（この段では作り直さない）。

Test Strategy は **Standard**（`aidlc/spaces/default/intents/260923-dsl-schema-loader/aidlc-state.md`）。単体と結合を中心に、E2E は代表の流れに絞る（team.md の Testing Posture）。

## 1. 枠組みと設定

| 区分 | 道具 | 置き場と名前 | 起動するもの |
|---|---|---|---|
| バックエンドの結合テスト | JUnit 5・Spring Boot Test・MockMvc・Testcontainers | `backend/src/test/java/`、名前は `XxxIT`（`:backend:integrationTest`） | Spring のアプリ、組み込みの H2（内部DB）、対象DB のコンテナ（MySQL 8.4.11・MariaDB 11.8.9・PostgreSQL 18.6） |
| 構造の検査 | ArchUnit | `backend/src/test/java/` の `*ArchitectureTest`（単体テストの側） | なし |
| 画面の部品と API の受け渡し | Vitest・Testing Library（jsdom）・user-event・vitest-axe | `frontend/src/` の対象と同じ場所の `*.test.ts(x)` | なし（`fetch` を差し替える） |
| E2E | Playwright（Chromium） | `frontend/e2e/*.e2e.ts`（`frontend/playwright.config.ts`） | ビルドした WAR（一時ディレクトリの内部DB、番号 18081） |

- 対象DB のコンテナのイメージは、版とダイジェストで固定する（`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`）。H2 やモックで代用しない（project.md の Mandated）。
- 対象DB を使うテストのクラスは、`ContainerRuntimeCheck`（`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java`）で、Docker に届くかを先に確かめる。届かないとき、開発中は警告を出して中断（SKIPPED）、CI では失敗にする（NFR12.3。ただし今は4クラスが中断ではなく失敗になる。`test-results.md` 3.1）。
- 表を作るテストは、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作って終わったら消す（`TargetDbTestDatabase`・`MysqlFamilyTestDatabase`・`PostgresTestDatabase`、NFR12.2）。
- テストの JVM のヒープは 1g（`backend/build.gradle.kts`）。

## 2. 実行のしかた

### 2.1 結合テスト

```bash
# 前提: colima が動いていて、DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE を設定済み（build-instructions.md 3.1）

# 結合テストだけを全部（対象DB 3種類を含む）
./gradlew :backend:cleanIntegrationTest :backend:integrationTest

# 統合の前の関門（結合テストを含む全検査。件数とカバレッジを報告するときはこの形）
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

### 2.2 絞り込み

`--tests` は直前のタスクにだけ効く。

```bash
# U1 対象DB（3種類）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'
# 対象DB を1種類だけ
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*Postgres*'
# U3 既定の DSL の生成（3種類の実際の DB から生成して検証）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.generate.*'
# U4 DSL の管理の API とアクセス制御の一括の確かめ
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.*'
# U2 DSL の定義（JSON Schema の公開）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dsl.*'
# 画面（U5）のテストだけ
(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/dsl src/shared/api-client)
```

### 2.3 E2E

```bash
(cd frontend && npx playwright install chromium)   # 初回と Playwright の更新のとき
./gradlew e2eTest
```

`e2eTest` は WAR をビルドし、一時ディレクトリの内部DB で起動して、`frontend/e2e/` のすべての確認を CSP 違反やスクリプトのエラーなしに通ることを確かめる。1つの WAR と内部DB を共有するため、`frontend/playwright.config.ts` を `workers: 1`・`fullyParallel: false` にし、**ファイル名の番号の順に1本ずつ**実行する（Q5: B）。番号は 10 刻みで、間に後から差し込める。

| ファイル | 確かめる流れ | 件数 |
|---|---|---|
| `frontend/e2e/010-skeleton.e2e.ts` | 画面の骨格（ログイン画面の枠と CSP の応答ヘッダー、画面の URL を直接開いたとき） | 2 |
| `frontend/e2e/020-auth.e2e.ts` | 初期管理者のログイン・読み直しでの維持・ログアウト、誤ったパスワードの表示 | 2 |
| `frontend/e2e/030-admin-access.e2e.ts` | 代表の流れ「ログイン → 管理画面に入れるか → ログアウト」 | 1 |
| `frontend/e2e/040-dsl-admin.e2e.ts` | DSL の管理「ログイン → DSL の管理 → 貼り付けで投入 → プレビュー → 確かめてから適用 → 今の状態が適用中 → ログアウト」 | 1 |

- 040 は対象DB を設定しないため、照合は「接続先が設定されていません」の警告になる（照合の中身は結合テストの `DslTargetDbIT` が受け持つ）。
- 040 は、プレビューが無いときの読み込み（`GET /api/admin/dsl/preview`）の 404 `DSL_PREVIEW_NOT_FOUND` を、想定どおりの表示として集計から外している（010〜030 の 401 と同じ扱い）。
- 前の Intent の `u1-skeleton`・`u2-auth`・`u3-admin-access` の名前は、この段で `010`〜`030` に改めた（中身は変えていない）。
- E2E は CI の外に置く（project.md の Deployment）。代わりの実行の場は、統合の前とリリースの前の手での実行である。

## 3. 単位をまたぐ境界と、それを受け持つテスト

契約は `aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/contract-design/contract-summary.md` の C1〜C8。

### 3.1 U1 → U3 → U4（対象DB を読み、既定の DSL を生成し、管理の API から使う）

| 境界（契約） | 確かめること | 受け持つテスト |
|---|---|---|
| C1 U1 → U3（`TargetSchemaReader`） | 3種類の実際の DB のスキーマを読み、生成した DSL が U2 の検証を通る。同じ写しから同じバイト列。接続先・スキーマ名が DSL に入らない。テーブルの無いスキーマは空の DSL | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/DefaultDslGeneratorMysqlIT.java`・`DefaultDslGeneratorMariadbIT.java`・`DefaultDslGeneratorPostgresIT.java`（親 `AbstractDefaultDslGeneratorIT.java`） |
| C1 の中身（U1 単独） | 4回の問い合わせで読む、読み取り専用、引用符・空白・セミコロンを含む名前、読み取りの権限だけのアカウント、失敗の結果とログに JDBC の文言・接続先・ユーザー名を含めない | `backend/src/test/java/cherry/mastersmith/targetdb/repository/MysqlSchemaQueriesIT.java`・`MariadbSchemaQueriesIT.java`・`PostgresSchemaQueriesIT.java`、`backend/src/test/java/cherry/mastersmith/targetdb/service/MysqlTargetSchemaReaderIT.java`・`MariadbTargetSchemaReaderIT.java`・`PostgresTargetSchemaReaderIT.java` |
| C3 U1 → U4（照合） | 照合の警告（テーブル・カラムの有無）、応答しない対象DB（テストの中の `SilentServer`）で打ち切って `TARGET_UNAVAILABLE` の警告つきで表示を続ける、対象DB が止まっていてもほかの操作は使える、応答・ログ・監査に接続情報が無い | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java` |
| C5 U3 → U4（`DefaultDslGenerator`） | 生成の API（`POST /api/admin/dsl/preview/generate`）が生成した DSL をプレビューに置く。設定が無い・接続できないときの応答 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`、`backend/src/test/java/cherry/mastersmith/dslmanage/service/DslLifecycleTest.java` |
| U1 の起動の境界 | 対象DB の設定が無い・不正でも起動する、起動時に接続しない、ヘルスチェックと内部DB の利用が対象DB に引きずられない | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`・`TargetDbSecretLeakIT.java` |

### 3.2 U2 → U3・U4（DSL の読み込みと検証）

| 境界（契約） | 確かめること | 受け持つテスト |
|---|---|---|
| C2 U2 → U3 | 生成した YAML を `DslReader.read` で検証し、通らなければ想定外の失敗 | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/TargetSchemaDslGeneratorTest.java`、C1 の3つの IT |
| C4 U2 → U4（`DslReader`・`DslFormat`・`ActiveDslModelHolder`） | API から投入した本文をすべてサーバー側で検証する（画面を通さない直接の呼び出しを含む）、誤りの一覧の形、適用で適用中のモデルが差し替わる、起動時に適用中の DSL を読めなくても起動を続ける | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`・`DslConcurrencyIT.java`、`backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java` |
| C8 U2 → 後続の Intent（`ActiveDslModelProvider`） | 適用中のモデルを読み取りだけで提供する。`dsl` が `dslmanage` に依存しない | `backend/src/test/java/cherry/mastersmith/dsl/service/ActiveDslModelStoreTest.java`、`backend/src/test/java/cherry/mastersmith/dsl/DslBoundaryArchitectureTest.java`、`DslAdminApiIT.java`（適用の後の提供口） |
| U2 の公開 | `GET /dsl/dsl-schema-v1.json` がログインなしで取れ、`nosniff` が付く | `backend/src/test/java/cherry/mastersmith/dsl/DslSchemaPublicationIT.java` |

### 3.3 U4 → 既存の AuditLog・共通のエラー応答（C7）

| 境界（契約） | 確かめること | 受け持つテスト |
|---|---|---|
| C7 U4 → AuditLog | 操作ごとの監査の行（V6 の列）、`DSL_BUSY` で断った要求は出来事を出さない、監査の書き込みに失敗しても操作は成功しアプリのログに1件 | `DslAdminApiIT.java`・`DslConcurrencyIT.java`・`DslAuditWriteFailureIT.java`（いずれも `backend/src/test/java/cherry/mastersmith/dslmanage/web/`）、`backend/src/test/java/cherry/mastersmith/audit/service/AuditSecretLeakIT.java`（V6 の4列を足した） |
| 共通のエラー応答 | Problem Details と安定した `code`、`DSL_BUSY` を 503 で登録し起動時の重複の検査を通る、ログの重さ（4xx・503 は WARN、500 は ERROR） | `backend/src/test/java/cherry/mastersmith/common/error/web/BusinessExceptionPropertiesTest.java`、`DslConcurrencyIT.java`、`DslAdminApiIT.java` |
| 同時の実行 | 重い処理はアプリ全体で同時に1つ（重なりは 503 `DSL_BUSY`）、同時の適用は1件だけ成功、適用は1つのトランザクション、プレビューの行は1つ | `DslConcurrencyIT.java`、`backend/src/test/java/cherry/mastersmith/dslmanage/repository/DslManageRepositoryIT.java` |

### 3.4 U4 → U5（画面と API、C6）

| 境界（契約） | 確かめること | 受け持つテスト |
|---|---|---|
| C6 の認可（10 本の API） | 各 API で 未認証 401・管理者でない 403（監査の行が1件増える）・管理者の結果。401・403 で状態が変わらない（31 件） | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java` |
| C6 の要求と応答の形 | 投入の上限 10MB（ちょうどは受け付け、超えると 413 `DSL_TOO_LARGE`）、ほかは 1MB、ダウンロードの応答ヘッダー、エラー応答の形 | `DslAdminApiIT.java` |
| 画面の側の受け渡し | ApiClient（Problem Details の本文とダウンロードの口）、`dslApi` の要求の形、`code` から文言を選ぶ | `frontend/src/features/dsl/api/dslApi.test.ts`・`frontend/src/features/dsl/api/saveFile.test.ts`、`frontend/src/shared/api-client/apiClient.download.test.ts`・`apiError.problem.test.ts`、`frontend/src/features/dsl/DslAdminPage.test.tsx` |
| ビルドした WAR での通し | 画面から投入・プレビュー・適用が通る（CSP 違反なし） | `frontend/e2e/040-dsl-admin.e2e.ts` |

- 設計の「11 本の API」は、契約 C6 と実装に合わせて 10 本として扱った（U5 の Code Generation の記録）。

## 4. テストデータと環境

- **対象DB**: テストのクラスごとに、固定のイメージのコンテナで起動し、名前の重ならないスキーマを作ってデータを入れ、終わったら消す（`TargetDbFixture`・`TargetDbTestDatabase`）。読み取りの権限だけのアカウントも作って確かめる。DDL がその場で確定する MySQL・MariaDB でも、巻き戻しではなくスキーマの作り直しで独立させる（team.md の Testing Posture）。
- **内部DB**: 組み込みの H2（本番と同じ）。テストごとに用意して巻き戻すか、クラスごとの一時の場所を使う。
- **DSL**: `backend/src/test/java/cherry/mastersmith/dsl/testsupport/DslSamples.java`・`backend/src/test/java/cherry/mastersmith/dslmanage/testsupport/DslYaml.java` で組み立てる。10MB ちょうど・1 バイト超えの本文はテストの中で作る。
- **応答しない対象DB**: テストの中で開いた `ServerSocket`（受け付けて何も返さない。`SilentServer`）。本物の DB の遅延ではない（project.md の Testing Posture の記録）。
- **外部の `$ref`**: テストの中の HTTP の受け口（`CountingHttpServer`）で要求が0件であることを数える。
- **性質ベースのテスト**: jqwik（`SqlIdentifiersTest`・`SafeYamlParserPropertyTest`・`DslGenerationPropertyTest`）。失敗時の乱数の種は jqwik の報告に出る。
- **時刻**: 注入可能な時計を使い、`sleep` と実時刻に頼らない。同時の実行は待ち合わせの仕組み（`DslManageTestHooks`）で重ねる。
- **E2E**: 一時ディレクトリの内部DB と仮の初期管理者（テストの中で作る値）。対象DB は設定しない。

## 5. 求めるカバレッジ

| 区分 | 下限 | 実測（2026-09-24、`test-results.md` 2.3） |
|---|---|---|
| バックエンド全体 | 行 80%・分岐 70% | 行 98.1%（3884/3960）・分岐 94.1%（1349/1433） |
| バックエンドの新しいパッケージ（13 個）ごと | 行 80%・分岐 70% | すべて以上。最低は `dslmanage.web` の分岐 86.7%（26/30） |
| バックエンドの既存のパッケージ | 全体の合計で判定（`packagesJudgedByTotal`） | 単独で下回るのは `auth.repository`（分岐 50.0%、1/2）と `common.health`（行 79.2%、42/53）。Q9: A で今のまま |
| 画面全体 | 行 80%・分岐 70% | 行 97.86%（961/982）・分岐 93.6%（585/625） |

- Test Strategy Standard の量（部品ごとに 5〜8 件、単体と結合）は、どの単位も上回っている（単位ごとの件数は `test-results.md` 2.2）。
- カバレッジの除外は増やしていない（`backend/build.gradle.kts` の差は依存の除外と `packagesJudgedByTotal` だけ）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/contract-design/contract-summary.md`（C1〜C8）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/aidlc-state.md`（Test Strategy）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-questions.md`（Q1・Q5・Q9）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/cross-unit-traceability.md`
- `backend/src/test/java/cherry/mastersmith/`（`targetdb`・`dsl`・`dslmanage`・`audit`・`common/error` のテストのクラス）、`frontend/src/features/dsl/`・`frontend/src/shared/api-client/` のテスト
- `frontend/e2e/*.e2e.ts`・`frontend/playwright.config.ts`・`README.md`（E2E、対象DB の結合テスト）
- `backend/build/test-results/`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`（実測）
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

None.
