# 単体テストの手順（Unit Test Instructions）— U3 グループ（group）

計画: `code-generation-plan.md`（同じディレクトリ）。方法は test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流す）。Test Strategy は Standard。

計画の 11節の依頼者の答え（Q1〜Q5、どれも A）を反映した。この手順書に当たるのは次の4つ。
- Q1: `role/service/RoleGroupDeletionGuardTest` と `role/RoleBoundaryArchitectureTest` を B3 で足す。
- Q2: 既存の `user/UserBoundaryArchitectureTest` は、許可の一覧に `group` を足すだけにし、ほかの規則は変えない（Step 16 で流す）。
- Q4: `audit/domain/AuditMigrationCompatibilityIT` を足す（Step 7）。前の版のイメージでの起動は配備の段。
- Q5: `StoreFailureClassifierTest` は、包み直した例外がクラスの名前だけを持つこと（元の文・SQLState・制約の名前を持たないこと）を確かめる。

## 1. テストの道具と設定（既存のものをそのまま使う）

| 対象 | 道具 | 設定（変えない） |
|---|---|---|
| バックエンドの単体テスト | JUnit 5・AssertJ・Mockito・ArchUnit 1.5.1・jqwik 1.10.1 | `backend/build.gradle.kts` の `test` タスク（名前が `Test` で終わるクラスだけ） |
| バックエンドの結合テスト | Spring Boot Test・spring-security-test・組み込みの H2（クラスごとの一時の内部DB、`common/testsupport/TestDatabase`） | `integrationTest` タスク（名前が `IT` で終わるクラスだけ） |
| カバレッジ | JaCoCo（行 80%・分岐 70%、全体とパッケージごと） | `jacocoTestReport`・`jacocoTestCoverageVerification`（除外と `packagesJudgedByTotal` は変えない） |
| 負荷の台本の確かめ | k6（`k6 inspect`） | `perf/k6/scenarios.js` |

- テストのクラスの名前は必ず `Test`（単体）か `IT`（結合）で終わらせる。`GroupNameProperties` のような名前はどちらのタスクにも拾われず動かない（Infrastructure Design の承認の場の決定 (2)。jqwik のテストは `GroupNamePropertyTest`）。
- テストの説明文（`@DisplayName`）は英語、テストデータは日本語でよい。メールアドレスは予約のドメイン（`example.com`）だけ。
- U3 の結合テストは組み込みの H2 だけを使い、コンテナを使わない。下のコマンドは単位のテストだけを名指しするため、コンテナの実行環境が無くても流せる。統合の前の `verify`（計画の Step 22）だけは colima と次の環境変数が要る（`project.md` の学び）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 2. テストを始める前に動くことを確かめるコマンド（計画の Step 2）

既存のテストで、名指しのコマンドが動くことを確かめる（変更の前。失敗 0 を記録する）。

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.user.domain.UserProblemTypesTest' \
  --tests 'cherry.mastersmith.useradmin.domain.UserAdminProblemTypesTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventTest'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.audit.domain.AuditSchemaIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminListQueryCountIT'
```

## 3. この単位のテストを流すコマンド（層ごと。計画の手順の番号）

```bash
# Step 5: 境界の口の移し替え（user・useradmin）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.user.domain.UserProblemTypesTest' \
  --tests 'cherry.mastersmith.user.service.UserProblemTypeCatalogTest' \
  --tests 'cherry.mastersmith.useradmin.domain.UserAdminProblemTypesTest' \
  --tests 'cherry.mastersmith.useradmin.service.UserAdminProblemTypeCatalogTest'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.service.UserSummariesByIdsIT' \
  --tests 'cherry.mastersmith.useradmin.*'

# Step 7: データの形（移行と監査の列。既存の監査のテストをすべて含める）
./gradlew :backend:test --tests 'cherry.mastersmith.audit.*'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.audit.*'

# Step 9: group のドメイン
./gradlew :backend:test --tests 'cherry.mastersmith.group.domain.*'

# Step 11: DB アクセス（store と repository）
./gradlew :backend:test --tests 'cherry.mastersmith.group.store.*'
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.group.store.*' \
  --tests 'cherry.mastersmith.group.repository.*'

# Step 13: 業務処理（group.service・仮の問う口・監査の受け取り）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.group.service.*' \
  --tests 'cherry.mastersmith.role.service.*' \
  --tests 'cherry.mastersmith.audit.domain.AuditGroupEventFactoryTest' \
  --tests 'cherry.mastersmith.audit.service.AuditGroupEventListenerTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.group.service.*'

# Step 15: API と結合（この単位の結合テストすべて）
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.group.*' \
  --tests 'cherry.mastersmith.audit.service.GroupAuditIT' \
  --tests 'cherry.mastersmith.audit.service.GroupAuditWriteFailureIT' \
  --tests 'cherry.mastersmith.audit.service.AuditSecretLeakIT'

# Step 16: 境界と構造の検査（B2 の検査を含む）
./gradlew :backend:test \
  --tests 'cherry.mastersmith.group.GroupBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.*BoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessArchitectureTest' \
  --tests 'cherry.mastersmith.ApiAccessRulesTest' \
  --tests 'cherry.mastersmith.PublicApiInventoryTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT'

# Step 18: 負荷の台本の読み込みの確かめ（流さない。場面ごとに）
for s in groupListFirst groupListLast groupDetail groupRename groupCreate groupDelete groupMemberAdd groupMemberRemove groupPoolLimit; do
  SCENARIO=$s k6 inspect --include-system-env-vars perf/k6/scenarios.js
done
```

- 長いコマンドは `caffeinate -i` で包む（PC のスリープで止まらないように）。
- 統合の前の全体（Step 22）は `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、E2E（Step 23）は `docker compose --profile mail up -d mailpit` の後に `caffeinate -i ./gradlew e2eTest`。どちらも単位のコマンドではなく統合の前の関門として流す。

## 4. テストの中身（Standard、部品ごと）

| 部品 | テスト | 確かめる中身 |
|---|---|---|
| `GroupName` | `GroupNameTest`・`GroupNamePropertyTest` | 64 コードポイントちょうど（サロゲートペアを含む）は受け付け 65 は拒否、空・空白だけ（全角を含む）・改行・タブ・制御文字の拒否、大文字と小文字だけの違いは同じ鍵、全角は別の鍵。性質: 前後に空白が残らない、1〜64、鍵の一致、鍵は 256・名前は 128（UTF-16）に収まる |
| 出来事・detail・code | `GroupAuditEventTest`・`GroupAuditDetailTest`・`GroupProblemTypesTest`・`GroupMemberTest` | 成功と失敗の項目、detail の4つの形、code と状態コードの固定、toString に氏名・メールアドレスが出ない |
| `group.store` | `StoreFailureClassifierTest`・`GroupStoreConstraintIT` | 区分の順（上限切れ → 23505・23503 と制約の名前 → 想定外）、包み直した例外がクラスの名前だけを持つ（元の文・SQLState・制約の名前を持たない。Q5: A）、主キーの待たない違反と上限切れ、外部キーの削除と追加の2経路、上限切れの WARN の `lockKind` |
| `group.repository` | `GroupRepositoryIT` | ID の順のページ、メンバーの数、詳細の並び（`added_at`・`user_id`）、`groupIdsOfUser`・`memberUserIds`（存在しないグループを含めない）、索引と制約の存在 |
| `group.service` | `GroupAdminServiceTest`・`GroupStoreTransactionsIT`・`GroupMembershipQueryIT`・`GroupMembershipQueryCountIT`・`GroupProblemTypeCatalogTest` | 判定の順、拒否と出来事、`Referenced` の操作ごとの読み替え、`Busy` で出来事なし、`Done` 以外で巻き戻しの印（T5）、`lockForAssignment` の Locked と Busy、外した直後の所属、問い合わせ 1 回 |
| 仮の問う口 | `RoleGroupDeletionGuardTest` | 渡した ID のすべてに 0、`canDelete` は Allowed |
| `group.web` | `GroupAdminApiIT`・`GroupAdminAuthorizationApiIT`・`GroupAdminMassAssignmentIT`・`GroupAdminIdorApiIT` | 7つの口の成功と拒否の code・応答の形、28 行の認可の表（403 の後に状態が変わらない）、足した項目を反映しない、0・負・存在しない ID・招待中の人 |
| 同時の重なり | `GroupConcurrencyIT`・`GroupNameConflictIT`・`GroupConflictAuditIT`・`GroupBusyLogIT` | #1〜#6（待ち合わせの口で作り、経過の時間で判定しない）、負けた側が 4xx で 500 にならない、監査の FAILURE が1行、`GROUP_BUSY` は監査なしで WARN が1件 |
| 監査・指標・接続・漏えい | `GroupAuditIT`・`GroupAuditWriteFailureIT`・`GroupAdminMetricsIT`・`GroupAdminListQueryCountIT`・`GroupConnectionUsageIT`・`GroupSecretLeakIT`・`GroupUniqueViolationSecretLeakIT`、`AuditSecretLeakIT`（書き換え） | 操作ごと・拒否ごとの行、400・BUSY・読み取りで行が増えない、監査の失敗でも操作は成功、`uri` が道の型、一覧 4 回・詳細 3 回、書き込み 2 本・読み取り 1 本、TRACE でもメールアドレス・氏名・違反の文が出ない |
| データの形 | `AuditEventGroupTargetTest`・`AuditSchemaIT`（書き換え）・`AuditMigrationCompatibilityIT` | detail の 16,384 ちょうどと超え（切り詰めない）、3列の NULL 可、前の版の列だけの INSERT、V1〜V9 だけの Flyway が止まらない |
| 境界 | `GroupBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`UserBoundaryArchitectureTest`（既存、許可の一覧に `group` だけを足す） | `logical-components.md` 2節の6項目、role がアプリの中で依存してよいのは `group.service` と `common` だけ（Q1: A）、user に依存してよい機能に `group` が入りほかは今のまま（Q2: A）、規則が依存を見分けていること |

## 5. カバレッジの目標

- 下限: 行 80%・分岐 70%（全体とパッケージごと）。新しいパッケージ（`group.domain`・`group.repository`・`group.store`・`group.service`・`group.web`・`role.service`）と、手を入れるパッケージ（`audit.domain`・`audit.service`・`user.domain`・`user.service`・`useradmin.domain`・`useradmin.web`）は、どれも `packagesJudgedByTotal` に無く、パッケージごとの下限が当たる。
- 計測の除外は増やさない。本体に名前が `Properties` で終わるクラスを作らない（`**/*Properties.class` が計測から外れるため）。
- 実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify`（計画の Step 22）で行い、計画の Step 3 の基準と並べて記録する。

## 6. モック・差し替えの指針

- 業務処理の単体テスト（`GroupAdminServiceTest`）は `GroupStore`・repository・`GroupDeletionGuard`・`GroupBarrier`・`ApplicationEventPublisher` をモックにし、トランザクションは既存の `useradmin/testsupport/RecordingTransactionManager` と同じ形で記録する。
- 結合テストは本物の H2 と本物の部品を使う。差し替えるのは次の `@Primary` のテスト用の部品だけ（`group/testsupport`）:
  - `TestGroupBarrier`: 4つの点（`beforeLock`・`afterLock`・`afterCheck`・`afterWrite`）で止める・合図を待つ。待ちの上限は 20 秒で、上限に届いたらテストの作りの誤りとして落とす。スレッドの数や `sleep` で重なりを作らない。
  - `TestGroupDeletionGuard`: グループごとの割り当ての数と Blocked を決められる（B3 では割り当ての表が無いため）。
  - `ConnectionHoldRecorder`: テストだけで `DataSource` を包み、同時に借りている本数の最大を記録する。確定の後の監査の途中は既存の `audit/testsupport/AuditWriteBarrierConfig` で止める。
- 監査の書き込みの失敗は既存の `audit/testsupport/FailingAuditEventRepositoryConfig` を使う。問い合わせの数は既存の `auth/testsupport/SqlStatementCounter` で数える。
- 時刻はテストの中で注入した時計を使い、実時刻と `sleep` に頼らない。上限切れの経路だけは H2 の上限（行 3 秒・鍵約 2 秒）を実際に待つが、合否は経過の時間ではなく応答・状態・監査・WARN で決める。

## 7. テストデータ

- 利用者は既存の手伝い（`access/testsupport/AdminTestUsers`・`useradmin/testsupport/UserAdminFixtures`）で、テスト（またはテストのクラス）ごとに作る。メールアドレスは `example.com` だけ。氏名は「営業 太郎」のような明らかな見本の値にし、実在しそうな名前を置かない。
- グループはテストごとに名前を重ならないように作る（例: `営業部-<テストの印>`）。内部DB はクラスごとの一時の H2 のため、ほかのテストの状態に頼らない。
- 招待中の人は招待の行だけを作り、`users` の行を作らない（IDOR の確かめ）。
- 負荷の台本のデータ（`perf-gr-0001`〜`perf-gr-1000`・`perf-graop01`、`example.test`）は使い捨ての環境だけで入れ、テストでは使わない（手順は `perf/README.md` のグループの節）。
