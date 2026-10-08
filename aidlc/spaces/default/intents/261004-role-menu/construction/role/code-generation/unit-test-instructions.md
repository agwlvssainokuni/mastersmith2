# 単体テストの手順（Unit Test Instructions）— U4 ロール（role、Bolt B4・B5・B6）

計画: `code-generation-plan.md`（同じディレクトリ）。方法は test-after（各 Bolt の中で層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流す）。Test Strategy は Standard（部品ごとに 5〜8 件、要所の結合テスト）。手順の番号は計画の Step の番号（B4 は 1〜23、B5 は 24〜45、B6 は 46〜67）。

計画の 13節の依頼者の答え（Q1〜Q9、どれも A）を反映した。この手順書に当たるのは次の5つ。
- Q1: A。`user/UserBoundaryArchitectureTest` を B4 の Step 14 で流す（Q1: A。一覧に `role` だけを足した形）。
- Q3: B5 の Step 36 で `group/web/GroupAdminAuthorizationApiIT` の 403 の主体を、全スキーマを FULL のロールを作業ロールに持ち管理者の印だけを欠く利用者に置き換える（Q3: A）。
- Q4: A。B5 の Step 34 で `group/store/StoreFailureClassifierTest`・`GroupStoreConstraintIT` を流す。
- Q6: A。`role/RoleMigrationCompatibilityIT` を B4 の Step 5 と B5 の Step 28 で流す。
- Q7: A。`role/testsupport/LargeRoleTransferYamlTest`（Java の1ファイルの生成の部品のテスト）を B6 の Step 52 で流す。

## 1. テストの道具と設定（既存のものをそのまま使う）

| 対象 | 道具 | 設定（変えない） |
|---|---|---|
| バックエンドの単体テスト | JUnit 5・AssertJ・Mockito・ArchUnit・jqwik（どれも今の lockfile の版） | `backend/build.gradle.kts` の `test` タスク（名前が `Test` で終わるクラスだけ） |
| バックエンドの結合テスト | Spring Boot Test・spring-security-test・組み込みの H2（クラスごとの一時の内部DB、`common/testsupport/TestDatabase`） | `integrationTest` タスク（名前が `IT` で終わるクラスだけ） |
| カバレッジ | JaCoCo（行 80%・分岐 70%、全体とパッケージごと） | `jacocoTestReport`・`jacocoTestCoverageVerification`（除外と `packagesJudgedByTotal` は変えない） |
| 負荷の台本の確かめ | k6（`grafana/k6:2.3.0` のコンテナで `k6 inspect`） | `perf/k6/scenarios.js` |

- テストのクラスの名前は必ず `Test`（単体）か `IT`（結合）で終わらせる。jqwik のテストは `PermissionInheritancePropertyTest`・`RoleNameKeyPropertyTest`・`WorkRoleResolutionPropertyTest`・`RoleTransferRoundTripPropertyTest`（`Properties` で終わる名前はどちらのタスクにも拾われず動かない。計画の D-12）。失敗のときの乱数の種は既存の `exceptionFormat = FULL` で出力に残る。
- テストの説明文（`@DisplayName`）は英語、テストデータは日本語でよい。メールアドレスは予約のドメイン（`example.com`）だけ、氏名は明らかな見本の値。
- U4 の結合テストは組み込みの H2 だけを使い、コンテナを使わない。下のコマンドは単位のテストを名指しするため、コンテナの実行環境が無くても流せる。統合の前の `verify`（計画の Step 21・43・65）と基準の実測（Step 3・26・48）は colima と次の環境変数が要る（`project.md` の学び）。長いコマンドは `caffeinate -i` で包む。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- colima のホームの共有の確かめ（各 Bolt の準備・`verify`・E2E の前）: `colima status` と `colima ssh -- ls <リポジトリの絶対パス>/docker`。外れていれば止めて依頼者に諮る。

## 2. テストを始める前に動くことを確かめるコマンド（最初のテストより前）

### 2.1 B4（Step 2。変更の前の既存のテストを名指しし、失敗 0 を記録する）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.role.service.RoleGroupDeletionGuardTest' \
  --tests 'cherry.mastersmith.user.UserBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventTest'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.audit.domain.AuditSchemaIT' \
  --tests 'cherry.mastersmith.audit.domain.AuditMigrationCompatibilityIT'
```

### 2.2 B5（Step 25。B4 で足したテストを名指し）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.role.*'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.RoleSchemaIT' \
  --tests 'cherry.mastersmith.role.web.RoleAdminApiIT' \
  --tests 'cherry.mastersmith.group.web.GroupAdminAuthorizationApiIT'
```

### 2.3 B6（Step 47。B4・B5 で足したテストを名指し）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.role.*'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.web.RoleAssignmentApiIT' \
  --tests 'cherry.mastersmith.role.service.EffectivePermissionConsistencyIT'
```

## 3. この単位のテストを流すコマンド（層ごと。計画の手順の番号）

### 3.1 B4

```bash
# Step 5: データの形（移行。既存の監査・移行のテストも含める）
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.RoleSchemaIT' \
  --tests 'cherry.mastersmith.role.RoleMigrationCompatibilityIT' \
  --tests 'cherry.mastersmith.audit.*'

# Step 7: ドメイン（role.domain と audit の写し方。既存の監査の単体テストをすべて含める）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.role.domain.*' \
  --tests 'cherry.mastersmith.audit.*'

# Step 9: DB アクセス（store と repository）
./gradlew :backend:test --tests 'cherry.mastersmith.role.store.*'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.store.*' \
  --tests 'cherry.mastersmith.role.repository.*'

# Step 11: 業務処理
./gradlew :backend:test --tests 'cherry.mastersmith.role.service.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.service.*'

# Step 13: API と結合（この単位の結合テストすべてと、role の監査の結合テスト）
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.*' \
  --tests 'cherry.mastersmith.audit.service.*'

# Step 14: 境界と構造の検査（B2 の検査を含む）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.*BoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessRulesTest' \
  --tests 'cherry.mastersmith.PublicApiInventoryTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT'

# Step 15: バックエンドの区切りの確かめ（全体。区切りの終わりだけ）
./gradlew :backend:spotlessApply
caffeinate -i ./gradlew :backend:spotlessCheck :backend:test :backend:integrationTest
```

### 3.2 B5

```bash
# Step 28: データの形（MERGE の確かめを含む）
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.RoleSchemaIT' \
  --tests 'cherry.mastersmith.role.RoleMigrationCompatibilityIT' \
  --tests 'cherry.mastersmith.role.store.WorkRoleSelectionMergeIT'

# Step 30: ドメイン
./gradlew :backend:test \
  --tests 'cherry.mastersmith.role.domain.*' \
  --tests 'cherry.mastersmith.audit.*'

# Step 32: DB アクセス
./gradlew :backend:test --tests 'cherry.mastersmith.role.store.*'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.store.*' \
  --tests 'cherry.mastersmith.role.repository.*'

# Step 34: 業務処理（問う口の本物と、Q4: A の group の区分のテストを含む）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.role.service.*' \
  --tests 'cherry.mastersmith.group.store.StoreFailureClassifierTest'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.service.*' \
  --tests 'cherry.mastersmith.group.store.GroupStoreConstraintIT'

# Step 36: API と結合（role・group・監査の結合テストすべて。group の 403 の書き換えを含む）
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.*' \
  --tests 'cherry.mastersmith.group.*' \
  --tests 'cherry.mastersmith.audit.service.*'

# Step 37: 境界と構造の検査
./gradlew :backend:test \
  --tests 'cherry.mastersmith.*BoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessRulesTest' \
  --tests 'cherry.mastersmith.PublicApiInventoryTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT'

# Step 38: バックエンドの区切りの確かめ
./gradlew :backend:spotlessApply
caffeinate -i ./gradlew :backend:spotlessCheck :backend:test :backend:integrationTest
```

### 3.3 B6

```bash
# Step 50: ドメイン
./gradlew :backend:test \
  --tests 'cherry.mastersmith.role.domain.*' \
  --tests 'cherry.mastersmith.audit.*'

# Step 52: YAML の部品と上限ちょうどの YAML の生成の部品
./gradlew :backend:test \
  --tests 'cherry.mastersmith.role.transfer.*' \
  --tests 'cherry.mastersmith.role.testsupport.LargeRoleTransferYamlTest'

# Step 54: DB アクセス（JDBC のバッチ）
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.store.*'

# Step 56: 業務処理（一括確定・確かめの後の変更・入口）
./gradlew :backend:test --tests 'cherry.mastersmith.role.service.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.service.*'

# Step 58: API と結合（10 MiB と Tomcat の確かめを含む）
caffeinate -i ./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.role.*' \
  --tests 'cherry.mastersmith.audit.service.*'

# Step 59: 境界と構造の検査（Step 37 と同じコマンド）

# Step 60: バックエンドの区切りの確かめ
./gradlew :backend:spotlessApply
caffeinate -i ./gradlew :backend:spotlessCheck :backend:test :backend:integrationTest
```

- 途中の Step（5・7・9・11・28・30・32・34・50・52・54・56）は、その層の名指しのコマンドだけを流し、通るまで直してから次の層へ進む（Testing Contract の `ordering`）。
- 結果（クラスの数・件数・失敗・飛ばし・終わりのコード）は手順ごとに `generation-notes.md` に記録する。

## 4. 変更の前の基準と、統合の前の関門（全体のコマンド。計画の Step の番号）

```bash
# Step 3・26・48: 変更の前の基準（件数とカバレッジ。project.md の学びの形）
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest \
  :backend:test :backend:integrationTest :backend:jacocoTestReport
# 値は backend/build/reports/jacoco/test/jacocoTestReport.xml から読む

# Step 21・43・65: 1コマンドの検査（統合の前の関門。対象DB の3種類の SKIPPED が 0 件であること）
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify

# Step 22・44・66: E2E（統合の前に手元で。全体）
docker compose --profile mail up -d mailpit
caffeinate -i ./gradlew e2eTest
rm -rf frontend/playwright-report frontend/test-results
```

この3つは単位のテストのコマンドではなく、計画の基準と関門の手順として流す（Build and Test では単位ごとに流し直さない）。

## 5. カバレッジの目標

| 対象 | 目標 | 確かめる所 |
|---|---|---|
| 新しい `role.domain`・`role.repository`・`role.store`・`role.service`・`role.web`（B4 から）、`role.transfer`（B6 から） | パッケージごとに行 80%・分岐 70% 以上（自動で対象。除外を足さない） | 各 Bolt の Step 21・43・65 |
| 手を入れる `audit.domain`・`audit.service`（Q4: A で `group.store` も） | 同じ下限。基準（Step 3・26・48）より大きく下がったときは、今回の変更で増えた枝を `jacocoTestReport.xml` の行ごとの値で洗い出し、テストを足して戻す（本体と除外は変えない。group の B3 の 3.2 と同じ扱い） | 同上 |
| 全体の合計 | 行 80%・分岐 70% 以上 | 同上 |

- `packagesJudgedByTotal` の7パッケージには手を入れない（計画 5節）。カバレッジの値は、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた実測だけを報告する。
- 設定値だけの record（`RoleTransferLimitProperties`）は既存の除外 `**/*Properties.class` に入る。ほかの本体のクラスの名前を `Properties` で終わらせない。

## 6. モックと差し替えの指針

| 層 | 方針 |
|---|---|
| ドメイン（`role.domain`）・YAML の部品（`role.transfer`）・`audit.domain` | モックを使わない純粋な関数のテスト。jqwik の性質ベースのテストを継承・名前の鍵・作業ロールの決め方・往復に当てる |
| store（`role.store`） | 単体の区分のテスト（`RoleStoreClassificationTest`）は例外の連なり（`ConstraintViolationException`・`SQLException` の SQLState と誤りの番号・制約の名前）を作って渡す。結合のテストは本物の H2 で、先の側の未確定の書き込みは `group/testsupport/UncommittedWrite` で持つ（スレッドの数に頼らない） |
| 業務処理（`role.service`） | 単体のテストは store・repository・`GroupMembershipQuery`・`UserAccountService`・`ActiveDslModelProvider`・`ApplicationEventPublisher` をモックにし、`TransactionTemplate` は既存の業務処理のテストと同じく実際に呼ぶ形の偽物（巻き戻しの印を記録する）を使う。結合のテストは本物の Spring と H2 |
| 同時の重なり | 待ち合わせの口 `RoleBarrier` を `role/testsupport/TestRoleBarrier`（`@Primary`、`hold`・`signal`、待ちの上限 20 秒）で差し替える。グループの削除との重なり（#10）は group の `TestGroupBarrier` も使う。合否は経過の時間ではなく、終わった後の内部DB の状態・負けた側の code（500 でない）・監査の行・WARN で決める |
| 問う口 | B4・B5 の role のテストは本番の Bean を使う。group のテストの `TestGroupDeletionGuard`（`@Primary`）は変えない。B5 の `RoleGroupDeletionGuardIT`・`GroupDeletionWithRoleIT` は差し替えを使わず、本物の割り当ての表に行を作る（仮の実装が残っていれば落ちる） |
| DSL | `role/testsupport/RoleDslFixture` が版 2 の DSL のモデルを `ActiveDslModelHolder` に置く・外す。DSL の適用し直し（#13）は DSL の管理の API を通す |
| 監査の書き込みの失敗 | 既存の `audit/testsupport/FailingAuditEventRepositoryConfig` の形 |
| 接続の本数・問い合わせの数 | `group/testsupport/ConnectionHoldRecorder`（同時の本数の最大）、`auth/testsupport/SqlStatementCounter`（JPA の文の数）。本番の構成は変えない |
| 適用の途中の失敗 | テストだけで内部DB に置く H2 のトリガー（`role/testsupport/FailingSettingTrigger`）で目印の行の書き込みを失敗させる。本番のコードに失敗の口を足さない |
| Tomcat の読み捨て | `RANDOM_PORT` の実際の Tomcat に、生のソケット（`role/testsupport/RawTransferPost`）で本文を書き切ってから応答を読む |
| ログ | 既存の `common/testsupport/LogEvents`・`JsonLogRecords` の形で WARN・ERROR・TRACE の行を集める。漏えいの確かめは TRACE を `cherry.mastersmith`（B6 は `org.springframework.jdbc` も）に広げる |

モックで済ませないもの: DB の振る舞い（違反・上限切れ・外部キー・`MERGE`・JDBC のバッチと Hibernate の同じトランザクションの巻き戻し）、認可（本物の Spring Security と `ApiAccess`）、`SafeYamlReader` の上限。

## 7. テストデータ

- 利用者は本番の作成の口で作る（`role/testsupport/RoleActors`）か、本番の書き込みを通さずに作る（`RoleFixtures`、group の `GroupFixtures` と同じ形）。メールアドレスは `example.com` だけ、氏名は「役割テスト 一郎」のような明らかな見本の値。パスワードはテストの定数。
- 403 の主体（B5）: `RoleActors.adminFlagMissingFullRole()` が、管理者の印を持たず、テストの DSL のすべてのスキーマに FULL・CREATE と DELETE を可にしたロールを割り当てて作業ロールに選んだ利用者を作る。
- ロールとグループの名前はテストごとに重ならないよう印を付ける（`RoleFixtures.uniqueName`）。名前の鍵は本番と同じく `RoleName` から作る。
- DSL の見本は、スキーマ2つ・テーブル数個・カラム数個の小さな版 2 の DSL と、名前に `/`・`..`・`;`・`%`・空白・`true`・`null`・`123` などを含む見本を `RoleDslFixture` に持つ。
- 大きな YAML（10 MiB ちょうど・節の数の境界・深さの境界・別名・タグ・重複キー）は、テストの中で生成する（リポジトリに大きなファイルを置かない）。上限ちょうどの YAML は計画 13節 Q7: A の生成の部品（`role/testsupport/LargeRoleTransferYaml.java`）で作る。
- 漏えいの確かめの目印は `LEAKCHECK_ROLE_7f3a`（YAML の中のロールの名前・対象の名前）と、試験の利用者のメールアドレス・氏名。
- 時刻に依存する所（`updatedAt`・`assignedAt`）は注入した時計で決め、`sleep` と実時刻に頼らない。待ちの上限切れのテストだけは、H2 の実際の上限（行 3 秒・鍵約 2 秒）まで待つ（合否は時間で決めない）。
- テストごとに一時の内部DB（`TestDatabase`）を使い、実行の順に依存させない。

## 8. 負荷の台本の確かめ（verify と CI の外。流すのは Performance Validation）

```bash
# Step 16（B4）: op のタグの確かめ（リポジトリの外の捨ての台本。要求を送らない）
caffeinate -i docker run --rm -v <スクラッチの置き場>:/s grafana/k6:2.3.0 \
  run --summary-export /s/summary.json /s/tag-check.js

# Step 17・39・61: 場面ごとの読み込みの確かめ（<場面> は計画の各部の場面の名前）
docker run --rm -v "$PWD/perf/k6:/k6" -e SCENARIO=<場面> grafana/k6:2.3.0 \
  inspect --include-system-env-vars /k6/scenarios.js
```

- 場面の名前・executor・閾値の式（`iteration_duration{scenario:…}`（op ごと）・`checks{scenario:…}`）・`setupTimeout: 10m` を記録する（`project.md` の学び）。使い捨ての環境での実行は Code Generation では行わない。
