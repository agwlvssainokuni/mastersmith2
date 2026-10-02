# Unit Test Instructions — U3 利用者の管理の API（u3-user-admin-api）

U3 のテストの道具・実行のしかた・カバレッジの目標・差し替えと待ち合わせと時計の方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、主な境界の結合テスト）。U3 は B3（一覧と氏名・言語の変更）と B4（5つの操作・最後の管理者の保護・監査・上限切れ・既存の経路の漏えいの直し）の2つの Bolt に分けて作るため、実行のしかたも B3・B4 に分けて書く。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パスで、テストのクラスは `backend/src/test/java/cherry/mastersmith/` の下をパッケージ名で書く。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ | `backend/build.gradle.kts` の `tasks.test`（名前が `*Test`）と `integrationTest`（名前が `*IT`） |
| 性質ベースのテスト | jqwik（既存） | `backend/src/test/resources/junit-platform.properties`（失敗した例は `build/jqwik-database`、失敗時の乱数の種は報告に出るため `@Property(seed = "...")` に与えて再現する）。`*Test` のクラスに置く（既存の `LockPolicyTest` と同じ） |
| 差し替え | Mockito（既存） | 単体テストだけで使う（5節） |
| 内部DB | 組み込みの H2（既存の `common/testsupport/TestDatabase`） | 結合テストはテストのクラスごとに一時ディレクトリの H2 ファイルを使う。コンテナは使わない（`team.md` の Testing Posture）。U3 のテストはどれも対象DB を使わないため、Step ごとの実行に colima の設定は要らない |
| 時計 | 既存の `auth/testsupport/MutableClock`（`AuthApiTestConfig` の `@Primary` の Bean） | ロックの判定と解除の予定の境界は時計を進めて確かめる。`sleep` と実時刻に頼らない |
| 文の回数と記録した SQL | 既存の `auth/testsupport/SqlStatementCounter` | `@SpringBootTest` の `properties` に `spring.jpa.properties.hibernate.session_factory.statement_inspector=cherry.mastersmith.auth.testsupport.SqlStatementCounter` を置いて使う（既存の `LoginApiIT` と同じ） |
| 排他の待ちに入ったことの確かめ | 既存の `auth/testsupport/H2SessionWaits` | `INFORMATION_SCHEMA.SESSIONS` の実行中の文を、接続の URL・文の絞り込み（例 `%from users%for update%`）・上限の時間・待つ側の終わりの判定で見る |
| 待ち合わせの口 | 新しい `useradmin/testsupport/TestUserAdminBarrier`（B4）・`auth/testsupport/TestLoginAttemptBarrier`（B4） | 本番の何もしない部品をテストの設定で差し替える（既存の `TestInvitationBarrier`・`TestPasswordChangeBarrier` と同じ形） |
| 別の接続で行を持ち続ける | 新しい `RowLockHolder`（B4。置き場は `code-generation-plan.md` 4.4 と Step 25） | JDBC の別の接続で `autoCommit=false` にし、`SELECT ... FOR UPDATE` で行を持ち、終わったら巻き戻して閉じる |
| 巻き戻しの印の記録 | 新しい `useradmin/testsupport/RecordingTransactionManager`（B4） | `TransactionTemplate` に渡す偽の `PlatformTransactionManager`。`setRollbackOnly()` の回数と確定・巻き戻しを記録する |
| 要求の手伝い | 既存の `auth/testsupport/AuthApi`・`AuthTestTokens`・`common/testsupport/HttpTestClient`・`access/testsupport/AdminTestUsers`、新しい `useradmin/testsupport/UserAdminApi`・`UserAdminFixtures`（B3） | — |
| 停止の状態を入れる | 既存の `auth/testsupport/TestUserSuspension` | C1 の口を `TransactionTemplate` の中で呼ぶ（JDBC で `users` を書き換えない） |
| 監査の行 | 既存の `audit/testsupport/AuditRows`・`FailingAuditEventRepositoryConfig` | 監査の結合テストは `audit/service` に置く（`code-generation-plan.md` 8節の D-9） |
| ログの確かめ | 既存の `common/testsupport/LogEvents`・`JsonLogRecords` と Spring Boot の `OutputCaptureExtension` | TRACE はテストの中だけで有効にする（Spring Boot の `LoggingSystem` で切り替え、終わったら戻す。8節の D-10）。`application.yaml` は変えない |
| 構造の検査 | ArchUnit（既存） | 既存の `ArchitectureTest` と機能ごとの境界テストを変えずに使い、`useradmin/UserAdminBoundaryArchitectureTest` を足す |
| カバレッジ | JaCoCo（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification`・`packagesJudgedByTotal` |

新しいテストの依存と設定のファイルは足さない。既存の `test`・`integrationTest` のタスクと名前の決まり（`XxxTest`・`XxxIT`）をそのまま使う。テストの説明文（`@DisplayName`）は英語で書く。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どのコマンドも U3 で作る・手を入れるテストのクラスだけを名指しする（プロジェクト全体のコマンドは、統合の前の関門の Step 15・41 だけで使う）。まだ作っていない新しいテストのクラスを `--tests` に名指しすると、Gradle の「一致するテストが無い」で失敗する。これは想定どおりで、そのクラスを作った Step からコマンドが通る。

### B3

#### 2.1 最初のテストより前の確かめ（Step 2）

作業ブランチの上で、既存の単体テストと結合テストの道具が動くことを確かめる。どちらも B3 で手を入れる・近くに置く既存のクラスで、変更の前に通る:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.user.service.UserAccountServiceTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.repository.UserRepositoryIT'
```

#### 2.2 一覧の検索の問い合わせの確かめ（Step 3）

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.repository.UserAdminQueriesIT'
```

#### 2.3 ドメインの層（Step 5）と DB アクセスの層（Step 7）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.user.domain.SearchTextTest' \
  --tests 'cherry.mastersmith.user.domain.ProfileValidationTest' \
  --tests 'cherry.mastersmith.auth.domain.LockViewTest' \
  --tests 'cherry.mastersmith.useradmin.domain.UserAdminProblemTypesTest'

./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.repository.UserAdminQueriesIT' \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT'
```

#### 2.4 業務処理の層（Step 9）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LockAdministrationServiceTest' \
  --tests 'cherry.mastersmith.useradmin.service.UserAdminServiceTest' \
  --tests 'cherry.mastersmith.useradmin.service.UserAdminProblemTypeCatalogTest'

./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.service.UserAdminAccountIT'
```

#### 2.5 web の層（Step 11）と漏えい（Step 12）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.useradmin.web.SearchTextConverterTest' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminWebTypesTest' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminRequestContextResolverTest'

./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminListApiIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminListQueryCountIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminProfileApiIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminSecretLeakIT'
```

#### 2.6 構造の検査（Step 13）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.useradmin.UserAdminBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest'
```

#### 2.7 B3 のテストをまとめて流す

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.useradmin.*' \
  --tests 'cherry.mastersmith.user.domain.SearchTextTest' \
  --tests 'cherry.mastersmith.user.domain.ProfileValidationTest' \
  --tests 'cherry.mastersmith.auth.domain.LockViewTest' \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LockAdministrationServiceTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest'

./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.useradmin.*' \
  --tests 'cherry.mastersmith.user.repository.UserAdminQueriesIT' \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT' \
  --tests 'cherry.mastersmith.user.service.UserAdminAccountIT'
```

### B4

#### 2.8 最初のテストより前の確かめ（Step 18）

B4 で手を入れる既存のテストが、作業ブランチの上で変更の前に通ることを確かめる:

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.common.error.web.GlobalExceptionHandlerTest' \
  --tests 'cherry.mastersmith.invitation.service.InvitationServiceTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.invitation.repository.InvitationRepositoryIT'
```

#### 2.9 既存の経路の上限切れの漏えいの直し（Step 20・22・24・25）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.common.persistence.*' \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryTest' \
  --tests 'cherry.mastersmith.invitation.repository.InvitationLockQueriesImplTest' \
  --tests 'cherry.mastersmith.common.observability.LockFailureSafeTraceInterceptorTest' \
  --tests 'cherry.mastersmith.common.error.web.GlobalExceptionHandlerTest' \
  --tests 'cherry.mastersmith.invitation.service.InvitationServiceTest' \
  --tests 'cherry.mastersmith.invitation.service.RegistrationServiceTest'

./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT' \
  --tests 'cherry.mastersmith.invitation.repository.InvitationRepositoryIT' \
  --tests 'cherry.mastersmith.invitation.service.InvitationConcurrencyIT' \
  --tests 'cherry.mastersmith.invitation.service.RegistrationConcurrencyIT' \
  --tests 'cherry.mastersmith.common.observability.TraceAspectIT' \
  --tests 'cherry.mastersmith.common.error.web.ErrorResponseIT' \
  --tests 'cherry.mastersmith.auth.web.AuthLockTimeoutLeakIT' \
  --tests 'cherry.mastersmith.invitation.web.InvitationLockTimeoutLeakIT' \
  --tests 'cherry.mastersmith.user.web.MePreferencesLockTimeoutLeakIT'
```

Step 25 の再現の確かめ（直しの本体だけを一時的に戻した状態）では、3つの `*LockTimeoutLeakIT` だけを上の結合テストのコマンドで名指しして流し、落ちることを確かめる。

#### 2.10 ドメインの層（Step 27）と DB アクセスの層（Step 29）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.useradmin.domain.*' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventFactoryTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventTest' \
  --tests 'cherry.mastersmith.user.repository.UserRowLockRepositoryTest'

./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.repository.UserRowLockRepositoryIT' \
  --tests 'cherry.mastersmith.user.repository.UserAdminQueriesIT'
```

#### 2.11 業務処理の層（Step 31・32）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.useradmin.service.UserAdminServiceTest' \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LockAdministrationServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LoginServiceTest' \
  --tests 'cherry.mastersmith.audit.service.AuditEventListenerTest'

./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.useradmin.service.UserAdminOperationsIT' \
  --tests 'cherry.mastersmith.useradmin.service.UserAdminConcurrencyIT' \
  --tests 'cherry.mastersmith.useradmin.service.ResetLoginConcurrencyIT' \
  --tests 'cherry.mastersmith.useradmin.service.SuspendWhileLoginIT' \
  --tests 'cherry.mastersmith.user.service.UserAdminLockPortsIT' \
  --tests 'cherry.mastersmith.auth.service.FailureResetPortIT' \
  --tests 'cherry.mastersmith.auth.service.LoginConcurrencyIT'
```

#### 2.12 web の層（Step 34）・監査（Step 35）・漏えい（Step 36）

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminOperationsApiIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminResetLoginFailuresApiIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminBusyApiIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminMassAssignmentIT' \
  --tests 'cherry.mastersmith.audit.service.UserAdminAuditIT' \
  --tests 'cherry.mastersmith.audit.service.UserAdminAuditWriteFailureIT' \
  --tests 'cherry.mastersmith.audit.service.AuditSecretLeakIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminSecretLeakIT'
```

#### 2.13 構造の検査（Step 37。既存の境界テストは変えずに流す）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.useradmin.UserAdminBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.invitation.InvitationBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest'
```

#### 2.14 負荷の試験の台本の読み込み（Step 39。測定はしない）

`perf/README.md` の既存の形（`grafana/k6:2.3.0` のコンテナ）で、足した場面ごとに `k6 inspect` を流す。`--include-system-env-vars` を付けないと場面の名前が undefined になり確かめにならない（`project.md` の学び）:

```bash
for s in userAdminList userAdminProfile userAdminOps userAdminSuspendWorst userAdminPool; do
  docker run --rm -e SCENARIO="$s" -v "$PWD/perf/k6:/scripts:ro" grafana/k6:2.3.0 \
    inspect --include-system-env-vars /scripts/scenarios.js
done
```

場面の名前は Step 39 で決めたものに合わせる。

#### 2.15 U3 のテストをまとめて流す（B4 の後）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.useradmin.*' \
  --tests 'cherry.mastersmith.common.persistence.*' \
  --tests 'cherry.mastersmith.user.domain.SearchTextTest' \
  --tests 'cherry.mastersmith.user.domain.ProfileValidationTest' \
  --tests 'cherry.mastersmith.auth.domain.LockViewTest' \
  --tests 'cherry.mastersmith.user.repository.UserRowLockRepositoryTest' \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryTest' \
  --tests 'cherry.mastersmith.auth.service.LockAdministrationServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LoginServiceTest' \
  --tests 'cherry.mastersmith.invitation.repository.InvitationLockQueriesImplTest' \
  --tests 'cherry.mastersmith.common.observability.LockFailureSafeTraceInterceptorTest' \
  --tests 'cherry.mastersmith.common.error.web.GlobalExceptionHandlerTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventFactoryTest' \
  --tests 'cherry.mastersmith.audit.service.AuditEventListenerTest' \
  --tests 'cherry.mastersmith.ArchitectureTest'

./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.useradmin.*' \
  --tests 'cherry.mastersmith.user.repository.UserAdminQueriesIT' \
  --tests 'cherry.mastersmith.user.repository.UserRowLockRepositoryIT' \
  --tests 'cherry.mastersmith.user.service.UserAdminAccountIT' \
  --tests 'cherry.mastersmith.user.service.UserAdminLockPortsIT' \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT' \
  --tests 'cherry.mastersmith.auth.service.FailureResetPortIT' \
  --tests 'cherry.mastersmith.invitation.repository.InvitationRepositoryIT' \
  --tests 'cherry.mastersmith.auth.web.AuthLockTimeoutLeakIT' \
  --tests 'cherry.mastersmith.invitation.web.InvitationLockTimeoutLeakIT' \
  --tests 'cherry.mastersmith.user.web.MePreferencesLockTimeoutLeakIT' \
  --tests 'cherry.mastersmith.audit.service.UserAdminAuditIT' \
  --tests 'cherry.mastersmith.audit.service.UserAdminAuditWriteFailureIT'
```

統合の前の関門（Step 15・41）だけは、colima の設定（README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と `./gradlew osvScan --rerun-tasks` を流す。

## 3. テストの一覧（Standard の量）

部品ごとの件数の目安は `code-generation-plan.md` の7節の表のとおり。どのテストも成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。常に通るだけのテスト（何も確かめないテスト）は書かない。

| Bolt | 種類 | 主なクラス | 見込みの件数 |
|---|---|---|---|
| B3 | 単体 | `SearchTextTest`・`ProfileValidationTest`・`LockViewTest`・`UserAdminProblemTypesTest`・`UserAccountServiceTest`（足す分）・`LockAdministrationServiceTest`・`UserAdminServiceTest`・`UserAdminProblemTypeCatalogTest`・`SearchTextConverterTest`・`UserAdminWebTypesTest`・`UserAdminRequestContextResolverTest`・`UserAdminBoundaryArchitectureTest` | 約 50〜65（性質ベースのテスト 3 を含む） |
| B3 | 結合 | `UserAdminQueriesIT`・`LoginAttemptStateRepositoryIT`（足す分）・`UserAdminAccountIT`・`UserAdminListApiIT`・`UserAdminListQueryCountIT`・`UserAdminProfileApiIT`・`UserAdminSecretLeakIT` | 約 40〜50 |
| B4 | 単体 | `RowLockFailuresTest`・`RowLockUnavailableExceptionTest`・`LoginAttemptStateRepositoryTest`・`InvitationLockQueriesImplTest`・`LockFailureSafeTraceInterceptorTest`・`GlobalExceptionHandlerTest`（足す分）・`RejectionPolicyTest`・`UserAdminAuditEventTest`・`AuditEventFactoryTest`（足す分）・`UserRowLockRepositoryTest`・`UserAdminServiceTest`（足す分）・`UserAccountServiceTest`（足す分）・`LockAdministrationServiceTest`（足す分）・`LoginServiceTest`（足す分）・`AuditEventListenerTest`（足す分）・`UserAdminBoundaryArchitectureTest`（足す分） | 約 75〜95（性質ベースのテスト 3 を含む） |
| B4 | 結合 | `LoginAttemptStateRepositoryIT`・`InvitationRepositoryIT`（足す分）・3つの `*LockTimeoutLeakIT`・`UserRowLockRepositoryIT`・`UserAdminQueriesIT`（足す分）・`UserAdminLockPortsIT`・`FailureResetPortIT`・`UserAdminOperationsIT`・`UserAdminConcurrencyIT`・`ResetLoginConcurrencyIT`・`SuspendWhileLoginIT`・`UserAdminOperationsApiIT`・`UserAdminResetLoginFailuresApiIT`・`UserAdminBusyApiIT`・`UserAdminMassAssignmentIT`・`UserAdminAuditIT`・`UserAdminAuditWriteFailureIT`・`UserAdminSecretLeakIT`（足す分） | 約 90〜115 |

`team.md` の Testing Posture の必須テスト（管理者の印の変更・ロックの解除・最後の管理者の保護・管理の API の認可・要求の改ざん・管理の操作の監査・利用者の管理の漏えい）とテストのクラスの対応は `code-generation-plan.md` の7節の2つ目の表のとおり。E2E の流れは足さない（代表の流れは B5）。

## 4. カバレッジの目標

| 対象 | 目標 | 確かめ方 |
|---|---|---|
| 全体 | 行 80% 以上・分岐 70% 以上（今は 行 98.8%・分岐 94.5%。下げない） | Step 15・41 の `verify`（`:backend:cleanTest :backend:cleanIntegrationTest` を付ける）の `jacocoTestReport.xml` |
| 新しいパッケージ `useradmin.web`・`useradmin.service`・`useradmin.domain`・`common.persistence` | パッケージごとに 行 80%・分岐 70% 以上（自動でパッケージごとの下限の対象） | 同上。U3 のテストだけで下限を満たす見込みで書く（上限切れの catch の分岐は EntityManager を差し替えた単体テストで通す） |
| B4 で一覧から外す `common.observability`・`common.error.web` | パッケージごとに 行 80%・分岐 70% 以上（今は 97.5%・94.4% と 96.9%・88.2%） | 同上。足した分岐（排他の失敗のときとそうでないとき）を単体テストで通す |
| U3 が手を入れる既存のパッケージ `user.*`・`auth.domain`・`auth.repository`・`auth.service`・`audit.domain`・`audit.service`・`invitation.repository` | パッケージごとの下限を満たし続ける | 同上。`user.repository` は B4 で `UserRowLockRepository`・`UserAdminRow` が入り計測の対象になるため、そのクラスの単体テストと結合テストで満たす |

- 下限と計測の除外は変えない。足りないときはテストを足す（Testing Contract、`team.md` の Testing Posture）。
- Step ごとの実行（2節）は単体と結合を分けて流すため、パッケージごとの値は Step 15・41 の `verify` の結果だけで判定する。途中で見たいときは `./gradlew :backend:test :backend:integrationTest :backend:jacocoTestReport` を流し、`backend/build/reports/jacoco/test/jacocoTestReport.xml` から読む（`project.md` の学び）。

## 5. 差し替え（モック・スタブ）・待ち合わせ・時計の方針

- **単体テスト**: 業務処理の層は、C8・C1 の口（`UserAccountService`・`LockAdministrationService`・`RefreshTokenRevocationService`）、待ち合わせの口、出来事の知らせを Mockito で差し替え、`TransactionTemplate` には `RecordingTransactionManager` を渡す（`reliability-design.md` 5.3 の単体テスト）。repository の上限切れの分岐は、`EntityManager` と `TypedQuery` を差し替えて、JPA の `LockTimeoutException`・`PessimisticLockException`・`QueryTimeoutException`、誤りの番号 50200・40001 の `SQLException` を原因に持つ例外、ほかの例外を投げさせる。
- **結合テスト**: 内部DB と Spring の部品は本物を使う（差し替えるのは時計・待ち合わせの口・監査の記録の失敗の部品だけ）。排他の上限切れは、`RowLockHolder` が別の接続で行を `FOR UPDATE` で持ち続けて起こす。排他の待ちの上限（3000 ミリ秒）は変えない（NFR4.4）。
- **待ち合わせ**: 同時の重なりはスレッドの数に頼らず、待ち合わせの口で1つ目を止め、2つ目が排他の待ちに入ったことを `H2SessionWaits.awaitExecuting`（上限の時間つき）で確かめてから1つ目を進める。待ち合わせの口で止める時間の上限は 3000 ミリ秒より短くする（例: 2 秒。止めすぎると2つ目が 409 `USER_ADMIN_BUSY` になり、意図した重なりにならない。機能設計の R-03）。上限を過ぎたら何を待っていたか（区分と利用者 ID だけ）を書いて失敗にする。`sleep` で順番を作らない。
- **時計**: ロックの判定（ロック中か・解除の予定ちょうど）と、失敗回数を戻した後のしきい値の境界は `MutableClock` を進めて確かめる。上限切れの時間（3000 ミリ秒以上）だけは実時間で測る（排他の上限そのものの確かめのため）。
- **ログのレベル**: TRACE と既定の INFO の両方を確かめるテストは、1つの Spring の文脈の中で `LoggingSystem` でロガーのレベルを切り替え、テストの終わりに元へ戻す（ほかのテストに漏らさない）。切り替えが追跡（`TraceAspect`）に効かないと分かったときは、2つのクラスに分けて差を記録する。
- **一時的な変更**: Step 25 の再現の確かめで直しの本体を一時的に戻したときは、確かめの後に元へ戻し、`git diff` で戻したことを確かめる（`code-generation-plan.md` 9節の Q-F）。

## 6. テストのデータ

- メールアドレスは予約のドメイン（`example.com`・`example.org`）だけにする。氏名は実在しそうでないもの（例: `テスト 太郎`・`ＡＢＣ Taro`・`ÉCOLE Ärger`・`100% off_sale\x` のように確かめたい文字の形を表すもの）にする（`team.md` の Testing Posture）。
- 漏えいのテストでは、出力の中で見分けやすい値（例: `leak-check-7f3a@example.com`、氏名 `漏れ確認 花子`、解除の予定の時刻の特定の秒）を入れ、その値が出力のどの行にも無いことを確かめる。記録やまとめには値そのものを写さず、件数と種類だけを書く。
- 利用者は各テストで自分で作り、ほかのテストが作った状態に頼らない。利用者の状態を変える操作（印・停止・ロック）は、そのテストで作った利用者だけを対象にする。初期管理者を5つの操作の対象にしない。
- 結合テストはテストのクラスごとに一時ディレクトリの内部DB を使い、テストの間で状態を持ち越さないよう、各テストで必要な利用者と行を用意する（排他を持つ別の接続は、テストの終わりに巻き戻して閉じる）。
- 一覧の並びのテストは、登録した日時と利用者 ID の向きをずらしたデータと、同じ登録した日時の行を含める。全体が 0 件・最後のページより後・20 件ちょうど・21 件を用意する。
- 負荷の試験のデータ（1,000 名など）は、テストではなく `perf/README.md` の手順で使い捨ての環境にだけ入れる（Step 39）。
