# Unit Test Instructions — U1 利用停止の状態と3つの入口（u1-user-suspension）

U1 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、主な境界の結合テスト）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パスで、テストのクラスは `backend/src/test/java/cherry/mastersmith/` の下をパッケージ名で書く。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ | `backend/build.gradle.kts` の `tasks.test`（名前が `*Test`）と `integrationTest`（名前が `*IT`） |
| 差し替え | Mockito（既存） | 単体テストだけで使う（5節） |
| 内部DB | 組み込みの H2（既存の `common/testsupport/TestDatabase`） | 結合テストはテストのクラスごとに一時ディレクトリの H2 ファイルを使う。コンテナは使わない（`team.md` の Testing Posture） |
| 時計 | 既存の `auth/testsupport/MutableClock`（`AuthApiTestConfig` の `@Primary` の Bean） | 時刻の境界は時計を進めて確かめる。`sleep` と実時刻に頼らない |
| 照合と文の回数 | 既存の `auth/testsupport/CountingPasswordEncoder`・`SqlStatementCounter` | `SqlStatementCounter` は `@SpringBootTest` の `properties` に `spring.jpa.properties.hibernate.session_factory.statement_inspector=cherry.mastersmith.auth.testsupport.SqlStatementCounter` を置いて使う（既存の `LoginApiIT` と同じ） |
| 出来事の受け取り | 既存の `auth/testsupport/CapturedAuthenticationEvents`・`access/testsupport/CapturedAccessDeniedEvents` | — |
| ログの確かめ | 既存の `common/testsupport/LogEvents`・`JsonLogRecords` と Spring Boot の `OutputCaptureExtension` | TRACE はテストの起動の引数の中だけで有効にする。`application.yaml` は変えない |
| 待ちの確かめ | 新しい `auth/testsupport/H2SessionWaits`（Step 3 で `V8MigrationIT` から移す） | 接続の URL・実行中の文の絞り込み・上限の時間を引数にする |
| 停止の状態を入れる | 新しい `auth/testsupport/TestUserSuspension`（Step 14） | C1 の口（`UserAccountService#setSuspended`・`RefreshTokenRevocationService#revokeAllRefreshTokens`）を `TransactionTemplate` の中で呼ぶ |
| 構造の検査 | ArchUnit（既存） | 既存の `ArchitectureTest` と機能ごとの境界テストを変えずに使う |
| カバレッジ | JaCoCo（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification`・`packagesJudgedByTotal` |

新しいテストの設定のファイルと新しいテストの依存は足さない。既存の `test`・`integrationTest` のタスクと名前の決まり（`XxxTest`・`XxxIT`）をそのまま使う。性質ベースのテスト（jqwik）を当てる純粋な関数は、この単位には無い（`tech-stack-decisions.md`）。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どのコマンドも U1 で作る・手を入れるテストのクラスだけを名指しする（プロジェクト全体のコマンドは、統合の前の関門の Step 23 だけで使う）。

### 2.1 最初のテストより前の確かめ（Step 2）

作業ブランチの上で、既存の単体テストと結合テストの道具が動くことを確かめる。どちらも U1 で手を入れる既存のクラスで、変更の前に通る:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.access.domain.AccessDeniedReasonTest'
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT'
```

まだ作っていない新しいテストのクラスを `--tests` に名指しすると、Gradle の「一致するテストが無い」で失敗する。これは想定どおりで、そのクラスを作った Step からコマンドが通る。

### 2.2 移行のテストの片付け（Step 3〜6）

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT' \
  --tests 'cherry.mastersmith.invitation.repository.InvitationSchemaIT' \
  --tests 'cherry.mastersmith.invitation.service.InvitationConcurrencyIT'
```

Step 6 で V7・V8 のテストを消した後の確かめ（消したテストと同じパッケージの残りの結合テスト）:

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.repository.*' \
  --tests 'cherry.mastersmith.invitation.repository.*'
```

### 2.3 ドメインの層（Step 10）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.auth.domain.TokenAuthenticationExceptionTest' \
  --tests 'cherry.mastersmith.auth.domain.SecretTypesTest' \
  --tests 'cherry.mastersmith.access.domain.AccessDeniedReasonTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventFactoryTest' \
  --tests 'cherry.mastersmith.auth.web.TokenAuthenticationEntryPointTest'
```

### 2.4 スキーマと DB アクセスの層（Step 12）

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.repository.UserSchemaIT' \
  --tests 'cherry.mastersmith.user.repository.UserRepositoryIT' \
  --tests 'cherry.mastersmith.auth.repository.RefreshTokenRepositoryIT'
```

### 2.5 業務処理の層（Step 14）

単体:

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LoginServiceTest' \
  --tests 'cherry.mastersmith.auth.service.TokenRefreshServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LogoutServiceTest' \
  --tests 'cherry.mastersmith.auth.service.RefreshTokenRevocationServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LoginCommandTest' \
  --tests 'cherry.mastersmith.dslmanage.service.DslLifecycleTest'
```

結合:

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.service.UserSuspensionIT' \
  --tests 'cherry.mastersmith.auth.service.RefreshTokenRevocationServiceIT'
```

### 2.6 認証の3つの入口（Step 16・17）

単体:

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.auth.web.AccessTokenAuthenticationProviderTest' \
  --tests 'cherry.mastersmith.auth.web.WebSecretTypesTest'
```

結合:

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.auth.web.SuspendedUserAuthenticationIT' \
  --tests 'cherry.mastersmith.auth.web.LoginApiIT' \
  --tests 'cherry.mastersmith.auth.web.AccessTokenApiIT' \
  --tests 'cherry.mastersmith.access.web.AccessDeniedEventsIT' \
  --tests 'cherry.mastersmith.user.web.MePreferencesApiIT' \
  --tests 'cherry.mastersmith.user.web.MePasswordApiIT' \
  --tests 'cherry.mastersmith.invitation.web.InvitationAdminApiIT'
```

### 2.7 監査の層（Step 19）

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.audit.service.AuditAuthenticationEventsIT' \
  --tests 'cherry.mastersmith.audit.service.AuditWriteFailureIT'
```

### 2.8 漏えいのテスト（Step 20）

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.web.AuthSuspensionSecretLeakIT'
```

### 2.9 構造の検査（Step 21。どれも変えずに流す）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.invitation.InvitationBoundaryArchitectureTest'
```

### 2.10 U1 のテストをまとめて流す

上の単体のクラスと結合のクラスを、1回の Gradle の実行にまとめる（Build and Test がこの単位のテストを流すときもこれを使う）:

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest \
  :backend:test \
  --tests 'cherry.mastersmith.auth.domain.TokenAuthenticationExceptionTest' \
  --tests 'cherry.mastersmith.auth.domain.SecretTypesTest' \
  --tests 'cherry.mastersmith.access.domain.AccessDeniedReasonTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventFactoryTest' \
  --tests 'cherry.mastersmith.auth.web.TokenAuthenticationEntryPointTest' \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LoginServiceTest' \
  --tests 'cherry.mastersmith.auth.service.TokenRefreshServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LogoutServiceTest' \
  --tests 'cherry.mastersmith.auth.service.RefreshTokenRevocationServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LoginCommandTest' \
  --tests 'cherry.mastersmith.dslmanage.service.DslLifecycleTest' \
  --tests 'cherry.mastersmith.auth.web.AccessTokenAuthenticationProviderTest' \
  --tests 'cherry.mastersmith.auth.web.WebSecretTypesTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest' \
  :backend:integrationTest \
  --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT' \
  --tests 'cherry.mastersmith.auth.repository.RefreshTokenRepositoryIT' \
  --tests 'cherry.mastersmith.invitation.repository.InvitationSchemaIT' \
  --tests 'cherry.mastersmith.user.repository.UserSchemaIT' \
  --tests 'cherry.mastersmith.user.repository.UserRepositoryIT' \
  --tests 'cherry.mastersmith.user.service.UserSuspensionIT' \
  --tests 'cherry.mastersmith.auth.service.RefreshTokenRevocationServiceIT' \
  --tests 'cherry.mastersmith.auth.web.SuspendedUserAuthenticationIT' \
  --tests 'cherry.mastersmith.auth.web.LoginApiIT' \
  --tests 'cherry.mastersmith.auth.web.AccessTokenApiIT' \
  --tests 'cherry.mastersmith.access.web.AccessDeniedEventsIT' \
  --tests 'cherry.mastersmith.audit.service.AuditAuthenticationEventsIT' \
  --tests 'cherry.mastersmith.audit.service.AuditWriteFailureIT' \
  --tests 'cherry.mastersmith.user.web.MePreferencesApiIT' \
  --tests 'cherry.mastersmith.user.web.MePasswordApiIT' \
  --tests 'cherry.mastersmith.invitation.web.InvitationAdminApiIT' \
  --tests 'cherry.mastersmith.auth.web.AuthSuspensionSecretLeakIT'
```

- Gradle の `--tests` は、その直前のタスク（`:backend:test` か `:backend:integrationTest`）にだけ効く。
- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest`・`:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（`project.md` の Testing Posture）。
- U1 のテストはコンテナを使わないため、コンテナの実行環境（colima）が無くても動き、飛ばされない。統合の前の `./gradlew verify`（Step 23）は対象DB のテストを含むため、colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して実行する（統合の前の関門で、この単位だけのコマンドではない）:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 3. テストの一覧（Standard の量）

| 部品 | 単体（`*Test`） | 結合（`*IT`） |
|---|---|---|
| 停止の区分と変換（`auth.domain`・`access.domain`・`audit.domain`） | `TokenAuthenticationExceptionTest`（区分の数を直す）、`AccessDeniedReasonTest`（`USER_SUSPENDED` は理由なし、尽くす変換の期待を直す）、`AuditEventFactoryTest`（`ACCOUNT_SUSPENDED` の監査の行 1 件）、`SecretTypesTest`（`AuthenticationEvent`・`AuthenticatedUser` の伏せ字 3 件） | `AccessDeniedEventsIT`（停止中は出来事なし 1 件） |
| 停止の状態（`user.domain`・`user.repository`・`user.service`） | `UserAccountServiceTest`（要約への写し・`isSuspended`・`setSuspended` の 0 行・`UserSummary` と `PasswordVerification` の `toString`、6〜7 件） | `UserSchemaIT`（既定と読み書き 2 件）、`UserRepositoryIT`（停止の列だけ・0 行・書いた後の読み取り 3 件）、`UserSuspensionIT`（MANDATORY・巻き戻し・書いた後の読み取り・いない利用者・ほかの列が変わらない、5〜6 件） |
| まとめての無効化（`auth.repository`・`auth.service`） | `RefreshTokenRevocationServiceTest`（時計・件数・0 件・ログのキー・負の数の拒否、5 件） | `RefreshTokenRepositoryIT`（未無効 100 件・無効とほかの利用者の行は変わらない・0 件、3 件）、`RefreshTokenRevocationServiceIT`（MANDATORY・停止と一緒の巻き戻し・確定・解いても戻らない、4 件） |
| ログインの照合（`auth.service`） | `LoginServiceTest`（停止中の4つの場合・停止とロックの順・失敗回数に数えない・解いた後は続きから、6〜7 件）、`LoginCommandTest`（1 件） | `LoginApiIT`（停止中の応答・文の並び・照合の回数がパスワードの誤りと同じ、1 件） |
| トークンの更新（`auth.service`） | `TokenRefreshServiceTest`（停止中は `REFRESH_FAILED` で発行しない、1〜2 件） | `SuspendedUserAuthenticationIT` の一部（巻き戻し・解いた後の拒否） |
| アクセストークンの認証（`auth.web`） | `AccessTokenAuthenticationProviderTest`（有効・停止中・いない・supports・読み取り1回、5 件）、`WebSecretTypesTest`（既存の `LoginRequest` の確かめを「メールアドレスも伏せる」に直す 1 件、`CurrentUserResponse`・`TokenResponse` の伏せ字 2 件）、`TokenAuthenticationEntryPointTest`（既存の `@EnumSource` が新しい区分を含む） | `AccessTokenApiIT`（区分 `USER_SUSPENDED` 1 件）、`SuspendedUserAuthenticationIT`（入口ごとの拒否・応答の同じさ・解いた直後・時刻の例・停止中の管理者・問い合わせの回数、7〜8 件） |
| 監査 | — | `AuditAuthenticationEventsIT`（`ACCOUNT_SUSPENDED` の行・更新と認証は行なし、1〜2 件）、`AuditWriteFailureIT`（書き込みの失敗でも応答が変わらない 1 件） |
| 一括代入の防止・招待の回帰 | — | `MePreferencesApiIT`・`MePasswordApiIT`（本文の `suspended`・`admin` が無視される 1 件ずつ）、`InvitationAdminApiIT`（停止中の利用者のメールアドレスは登録済みの拒否 1 件） |
| 漏えい | — | `AuthSuspensionSecretLeakIT`（伏せ字にした6つの型（`AuthenticationEvent`・`LoginCommand`・`AuthenticatedUser`・`CurrentUserResponse`・`LoginRequest`・`PasswordVerification`）が出る TRACE の行にメールアドレスが無いことと、秘密の値が出力全体に無いこと、1〜2 件。後の Intent に回した `verifyPassword(String, …)`・`existsByEmail(String)` の引数の行は対象の外） |
| 移行のテストの片付け | — | `LoginAttemptStateRepositoryIT`（ダミーの行8つの排他 1 件）、`InvitationSchemaIT`（(a) を強める・(b) を足す） |
| 構造の検査 | 既存の ArchUnit のテスト（変えない） | — |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`@DisplayName`・メソッド名）は英語で書く。停止中のログインが失敗回数に数えられないことのテストは、説明文に `suspended login attempts do not increase the failure count` を入れる（NFR 設計の R-06）。

`team.md` の「利用停止」の必須のテストと、確かめるテストの対応は `code-generation-plan.md` の7節のとおり。

## 4. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上（既存の `jacocoTestCoverageVerification`）。
- パッケージごと（行 80%・分岐 70%）:
  - 一覧から外すパッケージ（見込み）: `cherry.mastersmith.auth.domain`（今 行 97.9%・分岐 100%）・`auth.repository`（今 行 93.9%・分岐 50.0%、分岐は2つで、足りないのは `LoginAttemptStateRepository#lockDummyForUpdate` の「空いたダミーの行が無い」側。Step 4 のテストで通す）・`access.domain`（今 行・分岐とも 100%）。今の値は Delivery Planning の実測（2026-10-01）で、Step 1 でもう一度実測する。外す対象は Step 22 で実際に `src/main` を変えたパッケージと突き合わせて決める（`team.md` の Testing Posture）。
  - 一覧の外で手を入れるパッケージ（今も下限の対象）: `auth.service`（新しい `RefreshTokenRevocationService`・`RevokeAllResult` を含む）・`auth.web`・`user.domain`・`user.repository`・`user.service`・`audit.domain`。手を入れた後も下限を満たすことを Step 23 で実測する。
  - テストを消すパッケージ: `user.repository`・`invitation.repository`（と `invitation.*`）は、V7・V8 のテストを消した後も下限を満たすことを Step 23 で実測する。下回ったら、消したテストが担っていた分をテストで足す。
  - 一覧に残るパッケージ: `access.service` ほか（手を入れない）。
- 計測から外すのは既存の除外（起動クラスと設定値だけのクラス）だけで、U1 のために除外を足さない。`packagesJudgedByTotal` の一覧を増やさない。下限の値を変えない（Testing Contract の coverage の決まり、`team.md` の Testing Posture）。
- この単位のテストだけのカバレッジを見るときは、2.10 のコマンドの後に報告を作る:

```bash
./gradlew :backend:jacocoTestReport
```

  報告は `backend/build/reports/jacoco/test/html/` と `backend/build/reports/jacoco/test/jacocoTestReport.xml`。単位のテストだけでは、ほかの単位のテストが通る経路の分だけ低く出る。下限の判定と記録する値は、Step 23 の `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` の実測とする（`team.md` の Testing Posture）。

## 5. 差し替え（モック・スタブ）・待ち合わせ・時計の方針

- **内部DB は差し替えない**: 停止の列の更新・まとめての無効化・ロックの状態の行の排他・監査の行は、組み込みの H2 の結合テストで確かめる（`team.md` の Testing Posture。内部DB はコンテナでなく本番と同じ組み込みの H2）。
- **単体テストでの差し替え**: `LoginServiceTest`・`TokenRefreshServiceTest`・`UserAccountServiceTest`・`RefreshTokenRevocationServiceTest`・`AccessTokenAuthenticationProviderTest` に限り、DB アクセスの部品（`LoginAttemptStateRepository`・`RefreshTokenRepository`・`UserRepository`）と `UserAccountService`・`AccessTokenService` を Mockito で差し替え、呼ばれる回数（停止中の `update` が1回、読んだ値のまま）と出来事を確かめる。トランザクションは既存の `LoginServiceTest` と同じく `PlatformTransactionManager` の差し替えで扱う。
- **停止の状態を入れる口**: 結合テストで利用者を止める・解くときは `auth/testsupport/TestUserSuspension`（C1 の口）を使い、JDBC で `users.suspended` を直接書き換えない（U3 の止める操作と同じ組で、口の振る舞いも同時に確かめるため）。M8 B の隙の形（停止の列だけを確定し、トークンを無効にしない）を作るときだけ、`UserAccountService#setSuspended` を `TransactionTemplate` で直接呼ぶ。
- **待ち合わせ**: 同時の重なり（ダミーの行8つの排他の間の `lockDummyForUpdate`）は、`CountDownLatch` の合図と `auth/testsupport/H2SessionWaits`（`INFORMATION_SCHEMA.SESSIONS` の実行中の文を上限の時間つきで見る）で確実に作る。スレッドの数や `sleep` に頼らない。待ちの確かめの上限は `lockDummyForUpdate` の待ちの上限（3 秒）より短くし、確かめを 3 秒より前に終える（NFR 設計の R-04）。
- **注入した時計**: アクセストークンの有効期限の境界（AC3.2.7 の t0＋5 分の1ミリ秒前は受け付け、ちょうどは拒否）と、まとめての無効化の時刻は、`MutableClock` を進めて確かめる。実時刻と `sleep` に頼らない（NFR9.3）。
- **ログ**: `LogEvents` でロガーごとに捕まえ、キーの名前と値の有無を確かめる（まとめての無効化の DEBUG は `userId`・`revoked` だけ）。TRACE の確かめは、`AuthSuspensionSecretLeakIT` でアプリを起動するときの引数（`--logging.level.cherry.mastersmith.auth=TRACE` など）でだけ有効にし、`application.yaml` とほかのテストのログのレベルは変えない。行の文字（`ENTER LoginService#login` など）は実際の出力で確かめてから書く。
- **応答の比べ方**: 応答の同じさは、状態コードと、本文の JSON から毎回変わる値（`traceId` など）を除いた項目で比べる（既存の `LoginApiIT` の `comparable` と同じ形）。

## 6. テストのデータ

- **メールアドレス**: `example.com` などの予約されたドメインのアドレスだけを使い、テストごとに `UUID` を含めて作る（例 `suspended-<UUID>@example.com`）。実在しそうな氏名・宛先を置かない（`team.md` の Testing Posture、リポジトリは公開）。
- **パスワード**: 既存のテストと同じく、テストの中の固定の文字列（例 `正しいパスワード-1234`）か `TestDatabase.randomSecret()` で作る。漏えいのテストでは乱数の値を使い、出力の中を検索する。Gitleaks に当たる値を置かない。
- **署名鍵**: 既存の `TestSigningKeyEnvironmentPostProcessor` の乱数の鍵を使う。
- **利用者の作成**: 既存の `user/testsupport/TestUserAccounts`・`access/testsupport/AdminTestUsers` で作る。V9 の前の形の追記（`suspended` を書かない JDBC の `INSERT`）をしている既存のテスト（`LoginAttemptStateRepositoryIT` など）はそのまま動き、既定の false が入る。
- **リフレッシュトークンの行**: `RefreshTokenRepositoryIT` の件数の確かめは、対象の利用者に未無効の行 100 件（期限切れを含む）・無効の行と、ほかの利用者の未無効の行を JPA で保存して作る。値は乱数のハッシュで、トークンの値はテストの外に出さない。
- **内部DB と実行順**: 結合テストはテストのクラスごとに一時ディレクトリの H2 を使い、テストごとに新しい利用者を作る。前のテストが作った状態に頼らない。止める・解くはそのテストで作った利用者だけに行う。
- **E2E**: U1 は E2E の流れを足さない。統合の前の `./gradlew e2eTest`（Step 24）の後は、`frontend/test-results/e2e-results.json` から結果を記録してから `frontend/playwright-report/`・`frontend/test-results/` を消す（`gate-decisions.md` の決定）。
