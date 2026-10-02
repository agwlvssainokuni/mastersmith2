# Code Generation Plan — U1 利用停止の状態と3つの入口（u1-user-suspension）

U1 のコード生成の計画を示す。Bolt は B1 利用停止の土台（`inception/delivery-planning/bolt-plan.md`、この Intent の最初の Bolt）。U1 は library の単位で、自分の API・画面を持たない。既存の `user`・`auth`・`access`・`audit` に手を入れ、`auth.service` に部品を1つ足し、内部DB に V9 を足す。B1 の最初の手順として、V7・V8 の移行のテストの片付けを行う（NFR 設計の Q3 A）。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260930-user-admin/`（以下「記録」）とする。Java のクラスは `backend/src/main/java/cherry/mastersmith/`（テストは `backend/src/test/java/cherry/mastersmith/`）の下を、`cherry.mastersmith` からのパッケージ名で書く。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u1-user-suspension/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`traceability.json` | 状態の移り変わり、3つの入口の手順（2.1〜2.4）、C1 の口の型・置き場・属性（2.5・2.6）、V9 の方針（2.7）、失敗の場合の表（3節）、決まり BR1.1〜BR7.2、承認の場の決定 R-01〜R-05 |
| `construction/u1-user-suspension/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md` | NFR1.1〜NFR11.2、NFR9.5・NFR9.6、残る危険 R1〜R3、承認の場の決定 R-01〜R-05 と申し送り |
| `construction/u1-user-suspension/nfr-design/security-design.md`・`logical-components.md`・`nfr-design-questions.md` | 判定の置き場（2節）、C1 の口の作り（4節）、伏せ字（5節・5.1）、監査（6節）、V9 と移行のテストの片付け（8節）、カバレッジ（9.2）、テストの対応（10節）、上流との差 S-1〜S-9、答え Q1 B・Q2 A・Q3 A・Q4 A、承認の場の決定（2026-10-02）、部品の一覧と B1 の順（`logical-components.md` 1節・6節・7節） |
| `construction/u1-user-suspension/infrastructure-design/cicd-pipeline.md` と `construction/infrastructure-design/gate-decisions.md` | verify の段への入り方（2節）、統合の前の関門と E2E（3節）、片付けの順と参照の確かめ（4節）、一覧から外すパッケージ（5節）、README と `.idea/.gitignore`（7節）、承認の場の決定（R-03 の条件つきの受け入れ、R-01・R-02 と E2E の報告の扱いの申し送り） |
| `inception/contract-design/contract-summary.md` の C1・C7（と共通の決まり） | C1 の口（`isSuspended`・`setSuspended`・`revokeAllRefreshTokens`）、C7 の `ACCOUNT_SUSPENDED` と記録しないもの |
| `inception/delivery-planning/bolt-plan.md` の B1 と共通の完了の条件 | 完了の条件、E2E を統合の前に流すこと、squash の統合 |
| `inception/units-generation/unit-of-work.md`（U1）・`unit-of-work-story-map.md`（US3.2） | 単位の境界、作らないもの、確かめ方 |
| `inception/requirements-analysis/requirements.md` の FR3.1〜FR3.9・NFR1〜NFR11 と `inception/user-stories/stories.md` の US3.2（AC3.2.1〜AC3.2.10） | 要件と受け入れ基準 |
| 決まり `aidlc/spaces/default/memory/team.md`・`project.md` | 作業の場・統合・コミット、Testing Posture（利用停止の必須テスト・`packagesJudgedByTotal`・時計・テストのデータ）、Code Style（`toString` の伏せ字・ArchUnit・層）、Forbidden・Mandated |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログと各文書の「承認の場の決定」の節から洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログ `audit/sakura-local-4e42a93f87ce.md` の `GATE_REJECTED`（Request Changes の理由）・`GATE_APPROVED` と、各文書の終わりの「承認の場の決定」の節、`gate-decisions.md` を読んだ。U1 と B1 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes、R-01） | C1 の口は `UserAccountService#isSuspended`（読み取りだけ）・`#setSuspended`（MANDATORY）、`RefreshTokenRevocationService#revokeAllRefreshTokens`（MANDATORY、`RevokeAllResult(int revoked)`） | 形のとおりに作る | Step 13・15 |
| 機能設計（R-02・R-03） | 前の版へ戻した間は停止が効かない制約と、ログインの照合の後に止めた場合の隙を受け入れる | 判定のトランザクションでの読み直しを足さない。隙はテストで固定しない | Step 13 |
| 機能設計（R-04） | AC3.2.6 の回帰テストを U1 で1件 | `InvitationAdminApiIT` に1件足す | Step 18 |
| 機能設計（R-05） | 書いた後の読み取りが書いた値を返す結合テスト | `UserSuspensionIT` に入れる（先に `findById` で読み込んだ場合を含む） | Step 14 |
| NFR 要件（Request Changes） | V7・V8 の移行と後方互換のテストは消して業務の確かめだけ移し、V9 には戻しを想定したテストを作らない（承認のときの決定。NFR 設計の Q1〜Q4 に引き継がれた） | 4節・Step 3〜6・Step 11 | Step 3〜6・11 |
| NFR 要件（R-02 の申し送り） | 列を閉じた一覧で確かめるテストを、計画のときに検索し直す | 2026-10-02 に検索し直した。`INFORMATION_SCHEMA.COLUMNS` を読むのは `audit/service/AuditSecretLeakIT`（`AUDIT_EVENTS` の閉じた一覧）・`audit/domain/AuditSchemaIT`（`AUDIT_EVENTS` の2列）・`dslmanage/repository/DslManageRepositoryIT`（`AUDIT_EVENTS`・DSL の表）と、消す `V7MigrationIT`・`V8BackwardCompatibilityIT` だけで、`users` の列を閉じた一覧で確かめるテストは無い。影響の範囲に足すものは無い | Step 1 で再確認 |
| NFR 設計（Request Changes、R-01・既存の漏えい ①） | `AuthenticationEvent` に `enteredEmail` を伏せる `toString` を足し、TRACE を有効にした漏えいのテストで確かめる | 作る | Step 9・10・20 |
| NFR 設計の承認（依頼者の決定、申し送りの扱い） | `AuthenticationEvent` に加えて `LoginCommand`（`auth.service`）・`AuthenticatedUser`（`auth.domain`）・`CurrentUserResponse`（`auth.web`）も `toString` で伏せ字にし、TRACE を有効にした漏えいのテストで確かめる | 作る（`security-design.md` 5.1 の確かめの範囲との差は8節の D-1） | Step 9・10・13・14・16・17・20 |
| NFR 設計（R-03） | 監査の行の `entered_email` は既存のログインの失敗と同じく入れる（停止中だけ空にしない） | `AuditEventFactory#from` を変えず、結合テストで同じ形を確かめる | Step 19 |
| NFR 設計（R-04） | 待ちの確かめの手伝いを移すときに、文の絞り込み・接続の URL・上限の時間を引数にし、`lockDummyForUpdate` の上限 3 秒より前に確かめを終える | 作る | Step 3・4 |
| NFR 設計（R-05） | `InvitationSchemaIT` の一意の違反の確かめに SQLState 23505 と制約の名前を足す | 作る | Step 5 |
| NFR 設計（R-06） | 停止中のログインの試みは失敗回数に数えない。テストの名前にも入れる | 作る（`suspended login attempts do not increase the failure count`） | Step 14 |
| 基盤の設計（1回目、R-03） | V9 の後方互換と戻しの練習を置かないことを条件つきで受け入れる（条件は deployment-pipeline の手順） | B1 では作らない。条件は「Build and Test に引き継ぐこと」に写す | — |
| 基盤の設計（1回目、U5 R-02 の全単位の決定） | いま手元に残っている E2E の報告を B1 の始めに消す。E2E の後は json の報告から結果を記録してから消し、消したことと共有していないことを記録する | 作る | Step 1・24 |
| 基盤の設計（2回目、U1 の申し送り） | 始めの報告の片付けと E2E の後の手順、3つの record の `toString` のテスト、一覧の外の `auth.service`・`auth.web`・`RefreshTokenRevocationService` の下限の実測、`TestInvitationBarrier` と1つにまとめられるか | 作る。まとめない（9節の Q-A の決定） | Step 1・3・10・14・16・23・24 |
| コード生成の計画の承認の前（依頼者の決定、9節） | Q-A A・Q-B A・Q-C B（`LoginRequest`・`PasswordVerification` の `toString` も B1 で伏せ字にし、`WebSecretTypesTest` の確かめを直す。`String` の引数の2つは後の Intent へ） | 作る | Step 3・13〜16・20 |

### 2.2 この計画での読み方

- **設計の文書どおりに作るもの**: 判定の置き場（`security-design.md` 2.2〜2.4）、C1 の口（4.1・4.2）、V9 の1文（8.1）、片付けの順（`cicd-pipeline.md` 4節）、外す一覧（5節）、README と `.idea/.gitignore`（7節）。
- **層の順（test-after）**: Testing Contract の `ordering` のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、通ってから次の層へ進む。U1 の層は「移行のテストの片付け → ドメイン → スキーマ（V9）と DB アクセス → 業務処理 → 認証の3つの入口（web）→ 監査 → 漏えいのテスト → 構造の検査」の順とする。画面の層は無い。
- **監査のドメインの変更の位置**: `AuditEventFactory` は `LoginFailureReason` を `switch` で尽くして変換するため、`LoginFailureReason.ACCOUNT_SUSPENDED` を足すと同じ時点で `AuditFailureReason`・`AuditEventFactory` を直さないとコンパイルが止まる。そのため監査のドメインの2つの変更はドメインの層（Step 9）で行い、監査の層の手順（Step 19）は記録の結合テストだけとする。
- **V9 の確かめ**: 起動時の Flyway の `validate-on-migrate` と Hibernate の `ddl-auto: validate`、エンティティでの停止の状態の読み書きだけで確かめる（Q1 B）。移行・後方互換の自動のテストと戻しの練習は作らない（Q4 A、`security-design.md` 12節 S-1・S-2）。既存の行が false になることは V9 の1文をコードのレビューで確かめる。
- **既存の ArchUnit を緩めない**: `ArchitectureTest` と機能ごとの境界テストは変えない（`team.md` の Code Style、NFR11.1）。新しい機能のパッケージは作らないため、新しい `<機能>BoundaryArchitectureTest` は足さない。
- **既存のテストの手直し**: 区分の数や `UserSummary` の引数の数を前提にした既存のテストは、変更に合わせて直す（8節の D-8）。テストだけの変更は `packagesJudgedByTotal` の「手を入れる」に当たらない。

## 3. 作業の場とコミットの区切り

- **記録の扱い**: ブランチを作る前に、この段の計画と承認の記録（記録の `construction/u1-user-suspension/code-generation/` の下と `aidlc-state.md`・監査ログ）を、依頼者の承認を得て `develop` に記録のコミットとして入れることを提案する（今までの Intent と同じく、記録は B1 の squash に含めない。コミットは提案して承認を得てから行う、`project.md` の Change Control）。
- **作業のブランチ**: `develop` から短命のブランチ `feature/260930-user-admin-b1` を作る（`team.md` の Way of Working）。ブランチの作成は依頼者の承認を得てから行う。worktree は使わない。
- **統合の形**: `develop` へ squash の1コミットで統合する（`team.md`、`bolt-plan.md`）。サブモジュールの固定先は変えない。件名は日本語で B1 の中身が分かるものにし、件名と本文に「移行のテストの片付けを含む」と書く（NFR 設計の Q3 A）。統合は依頼者の承認を得てから行い、統合の後に作業ブランチを消す（これも承認を得る）。
- **統合の前の関門**: Step 23 の `./gradlew verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）と、Step 24 の `./gradlew e2eTest`（Mailpit を profile `mail` で起動）がどちらも通ってから統合する（`bolt-plan.md` の共通の完了の条件、`cicd-pipeline.md` 3節）。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、作業ブランチの上で次の区切りでコミットする（`project.md` の Change Control の学び）。メッセージは日本語。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | 移行のテストの片付け（待ちの確かめの手伝いの移し、`LoginAttemptStateRepositoryIT`・`InvitationSchemaIT` への追加、V7・V8 のテスト4つと複写の置き場の削除） | Step 3〜6 |
| C2 | 停止の区分・監査の理由・伏せ字（`auth.domain`・`access.domain`・`audit.domain` とテスト） | Step 9・10 |
| C3 | V9 と DB アクセス（`V9__u1_user_suspension.sql`・`User`・`UserRepository`・`RefreshTokenRepository` とテスト） | Step 11・12 |
| C4 | 業務処理（`UserSummary`・`UserAccountService`・`PasswordVerification`・`RefreshTokenRevocationService`・`RevokeAllResult`・`LoginService`・`TokenRefreshService`・`LoginCommand` とテスト、テストの手伝い） | Step 13・14 |
| C5 | 3つの入口・監査・漏えいのテスト（`AccessTokenAuthenticationProvider`・`CurrentUserResponse`・`LoginRequest` と `WebSecretTypesTest` の直し、結合テスト） | Step 15〜20 |
| C6 | 文書とビルドの設定（`README.md`・`.idea/.gitignore`・`backend/build.gradle.kts` の `packagesJudgedByTotal`） | Step 21・22 |

- `develop` への統合は、C1〜C6 を squash した1コミットとする。細かい履歴は作業ブランチと監査ログに残る（`team.md`）。
- `origin` への `git push` は依頼者が行う。AI はプッシュしない。

## 4. 作るもの・手を入れるもの・消すもの

### 4.1 本体（`src/main`）

| パッケージ | 部品 | 新しい・手を入れる | 中身 | 一覧（`packagesJudgedByTotal`） |
|---|---|---|---|---|
| `auth.domain` | `LoginFailureReason` | 手を入れる | `ACCOUNT_SUSPENDED` を足す | 一覧にある → 外す |
| `auth.domain` | `TokenFailureReason` | 手を入れる | `USER_SUSPENDED` を足す | 同上 |
| `auth.domain` | `AuthenticationEvent` | 手を入れる | `toString` を上書きし `enteredEmail` を `***` にする（ほかの項目は出す。`of`・項目・監査の行への写しは変えない） | 同上 |
| `auth.domain` | `AuthenticatedUser` | 手を入れる | `toString` を上書きし `email` を `***` にする（`userId`・`admin` は出す） | 同上 |
| `access.domain` | `AccessDeniedReason` | 手を入れる | `of(TokenFailureReason)` の `switch` に `USER_SUSPENDED -> Optional.empty()` を足す（値は足さない） | 一覧にある → 外す |
| `audit.domain` | `AuditFailureReason`・`AuditEventFactory` | 手を入れる | `ACCOUNT_SUSPENDED` と変換の1行 | 一覧の外（そのまま下限の対象） |
| `user.domain` | `User` | 手を入れる | 列 `suspended`（`@Column(name = "suspended", nullable = false)`、作るときに false）と読み取りの `isSuspended()`。外から呼べる書き換えのメソッドは作らない | 一覧の外 |
| `user.repository` | `UserRepository` | 手を入れる | `updateSuspended(long userId, boolean suspended)`（名前つきの引数の JPQL、`@Modifying(clearAutomatically = true, flushAutomatically = true)`、更新した行の数を返す） | 一覧の外 |
| `auth.repository` | `RefreshTokenRepository` | 手を入れる | `revokeAllActiveByUserId(long userId, Instant revokedAt)`（`user_id` が一致し `revoked_at` が空の行だけ、期限切れも含む、`revokeIfActive` と同じ属性、件数を返す） | 一覧にある → 外す |
| `user.service` | `UserSummary` | 手を入れる | 最後の項目に `boolean suspended` を足す。`toString` に真偽を出す（メールアドレスと氏名を伏せる形は変えない） | 一覧の外 |
| `user.service` | `UserAccountService` | 手を入れる | `toSummary` が `suspended` を写す。`isSuspended(long)`（`@Transactional(readOnly = true)`、いなければ `IllegalStateException`）、`setSuspended(long, boolean)`（`@Transactional(propagation = Propagation.MANDATORY)`、0 行なら `IllegalStateException`） | 一覧の外 |
| `auth.service` | `RefreshTokenRevocationService`・`RevokeAllResult` | 新しい | `revokeAllRefreshTokens(long userId)`（MANDATORY、注入した `Clock`、DEBUG のログに利用者 ID と件数だけ、`RevokeAllResult(int revoked)`） | 一覧の外（新しいクラスも下限の対象） |
| `auth.service` | `LoginService` | 手を入れる | `decide` で本人の行を排他つきで読んだ直後・`LockPolicy.decide` の前に停止を判定し、読んだ値のまま `update` を1回、`LOGIN_FAILED`・`ACCOUNT_SUSPENDED`（操作した人は本人）を1件知らせて失敗で終える | 一覧の外 |
| `auth.service` | `TokenRefreshService` | 手を入れる | `findById` の直後に `suspended` を見て、今の失敗と同じ `REFRESH_FAILED` を投げる（例外で巻き戻る） | 一覧の外 |
| `auth.service` | `LoginCommand` | 手を入れる | `toString` を上書きし `email` を `***` にする（パスワードは今の型のまま伏せる） | 一覧の外 |
| `user.service` | `PasswordVerification` | 手を入れる（9節の Q-C B） | `toString` を上書きし `email` を `***` にする（`user` は `UserSummary` の伏せた文字列化、`matched` は出す） | 一覧の外 |
| `auth.web` | `AccessTokenAuthenticationProvider` | 手を入れる | `findById` の直後に `suspended` を見て `TokenAuthenticationException(TokenFailureReason.USER_SUSPENDED)` を投げる | 一覧の外 |
| `auth.web` | `CurrentUserResponse` | 手を入れる | `toString` を上書きし `email`・`displayName` を `***` にする（JSON の応答の項目は変えない） | 一覧の外 |
| `auth.web` | `LoginRequest` | 手を入れる（9節の Q-C B） | 今の `toString`（`email` をそのまま出し `password` を伏せる）を、`email` も `***` にする形に直す（JSON の受け取りと入力の検証は変えない） | 一覧の外 |
| （内部DB） | `backend/src/main/resources/db/migration/V9__u1_user_suspension.sql` | 新しい | `ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL` の1文だけ（先頭に SQL のコメントでライセンスヘッダー。既存の V1〜V8 と同じ形） | — |

手を入れない: `access.service`（一覧に残る）、`audit.service`（`AuditEventListener`）、`common.security`、`application.yaml`（TRACE はテストの設定の中だけ）、`compose.yaml`・`.env.example`・Dockerfile、`.github/`、`docker/monitoring/provisioning/alerting/mastersmith.yaml`、`frontend/`。新しい依存・指標・警報は足さない。

### 4.2 テスト（`src/test`）

| 置き場 | 部品 | 新しい・手を入れる・消す |
|---|---|---|
| `auth/testsupport` | `H2SessionWaits`（待ちの確かめの手伝い。`INFORMATION_SCHEMA.SESSIONS` の実行中の文を、接続の URL・文の絞り込み・上限の時間を引数にして見る） | 新しい（`V8MigrationIT#awaitBlocked` から移す） |
| `auth/testsupport` | `TestUserSuspension`（テストで停止の状態を入れる手伝い。`TransactionTemplate` の中で C1 の `setSuspended` と、止めるときは `revokeAllRefreshTokens` を呼ぶ） | 新しい |
| `auth/repository` | `LoginAttemptStateRepositoryIT`・`RefreshTokenRepositoryIT` | 手を入れる（テストを足す） |
| `invitation/repository` | `InvitationSchemaIT` | 手を入れる（(a) の確かめを強め、(b) を1件足す） |
| `user/repository` | `V7MigrationIT`・`V7BackwardCompatibilityIT` | 消す |
| `invitation/repository` | `V8MigrationIT`・`V8BackwardCompatibilityIT` | 消す |
| `backend/src/test/resources/db/` | `migration-through-v6/`・`migration-through-v7/` | 消す |
| `auth/domain` | `SecretTypesTest`・`TokenAuthenticationExceptionTest` | 手を入れる |
| `access/domain` | `AccessDeniedReasonTest` | 手を入れる |
| `audit/domain` | `AuditEventFactoryTest` | 手を入れる（理由の値を尽くす既存のテストに `ACCOUNT_SUSPENDED` が入ることを確かめる1件） |
| `user/repository` | `UserRepositoryIT`・`UserSchemaIT` | 手を入れる |
| `user/service` | `UserAccountServiceTest`（`PasswordVerification` の伏せ字を含む） | 手を入れる |
| `user/service` | `UserSuspensionIT` | 新しい |
| `auth/service` | `LoginServiceTest`・`TokenRefreshServiceTest`・`LogoutServiceTest` | 手を入れる（`LogoutServiceTest` は `UserSummary` の引数だけ） |
| `auth/service` | `RefreshTokenRevocationServiceTest`・`RefreshTokenRevocationServiceIT`・`LoginCommandTest` | 新しい |
| `auth/web` | `AccessTokenAuthenticationProviderTest`・`SuspendedUserAuthenticationIT`・`AuthSuspensionSecretLeakIT` | 新しい |
| `auth/web` | `WebSecretTypesTest`（`LoginRequest` の確かめを直し、`CurrentUserResponse` を足す）・`LoginApiIT`・`AccessTokenApiIT` | 手を入れる |
| `access/web` | `AccessDeniedEventsIT` | 手を入れる |
| `audit/service` | `AuditAuthenticationEventsIT`・`AuditWriteFailureIT` | 手を入れる |
| `user/web` | `MePreferencesApiIT`・`MePasswordApiIT` | 手を入れる |
| `invitation/web` | `InvitationAdminApiIT` | 手を入れる（AC3.2.6 の回帰1件） |
| `dslmanage/service` | `DslLifecycleTest` | 手を入れる（`UserSummary` の引数だけ） |

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。各 Step の実行のコマンドは `unit-test-instructions.md` の2節のとおりで、U1 のテストのクラスを名指しして流す。

### Step 1: 作業の場の用意と、変更の前の基準（ブランチの作成は依頼者の承認を得てから）

- [ ] `develop` の先頭のハッシュを `git rev-parse HEAD` で記録する。アプリのソースに未コミットの変更が無いことを `git status` で確かめる（ワークフローの記録は外して判断する、`project.md` の学び）
- [ ] 依頼者の承認を得て、`develop` から `feature/260930-user-admin-b1` を作る
- [ ] いま手元に残っている `frontend/playwright-report/`・`frontend/test-results/` を消し、消したこと（中身の種類と件数だけ）と、共有していないことを記録する（`gate-decisions.md` の U5 R-02 の決定。どちらも `.gitignore` の対象で、コミットに影響しない）
- [ ] Dependabot の開いている知らせ（`origin` の `dependabot/*` のブランチ）の一覧を読み取りだけで確かめ、重大度 High 以上の脆弱性の直しがあれば B1 に入る前に取り込むかを依頼者に諮る（`team.md` の Way of Working）。無ければ記録だけ
- [ ] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（単体・結合、失敗・飛ばした）と、全体と次のパッケージの行・分岐のカバレッジを `backend/build/reports/jacoco/test/jacocoTestReport.xml` から記録する: `auth.domain`・`auth.repository`・`auth.service`・`auth.web`・`access.domain`・`audit.domain`・`user.domain`・`user.repository`・`user.service`・`invitation.repository`・`invitation.service`・`invitation.web`（brownfield の Test Baseline、`project.md` の学び）
- [ ] `INFORMATION_SCHEMA.COLUMNS` を読むテストを名前で検索し直し、2.1 の結果（`users` の列を閉じた一覧で確かめるテストが無い）が変わっていないことを確かめる（NFR3.1、NFR 要件の R-02）
- [ ] 対応: B1 の共通の完了の条件、`gate-decisions.md`、NFR3.1

### Step 2: テストの実行の準備（最初のテストより前）

- [ ] `unit-test-instructions.md` の「最初のテストより前の確かめ」のコマンドで、既存の単体テストと結合テストの道具が、作業ブランチの上で動くことを確かめる:
  - `./gradlew :backend:test --tests 'cherry.mastersmith.access.domain.AccessDeniedReasonTest'`
  - `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT'`
- [ ] 新しいテストのクラス（Step 4 以後に作るもの）は、作るまで `--tests` に名指しすると Gradle の「一致するテストが無い」で失敗する。これは想定どおりで、作った Step から通ることを `unit-test-instructions.md` の記述と合わせる
- [ ] 対応: Testing Contract の `runner_step`、NFR9.6

### Step 3: 待ちの確かめの手伝いを `auth/testsupport` へ移す（片付けの順 1）

- [ ] `invitation/repository/V8MigrationIT#awaitBlocked` の待ちの確かめを、`auth/testsupport/H2SessionWaits` に移す。今の作り（文の絞り込みが `INSERT INTO invitations%` に固定、接続が `url(tempDir)` に固定、上限が 20 秒に固定）を書き直し、次を引数にする（NFR 設計の R-04）:
  - 接続の URL（`TestDatabase.url(dir)` の値）
  - 実行中の文の絞り込み（`LIKE` の型。大文字・小文字をそろえて比べる）
  - 上限の時間（`Duration`）
  - 待っている側が先に終わったら失敗にするための判定（例: `Future#isDone`）
- [ ] 上限の時間を過ぎたら、何を待っていたか（絞り込みの文字だけ）を書いた `AssertionError` で失敗にする。実時刻の `sleep` に頼らず、`Thread.onSpinWait` で見回す（今の形のまま）
- [ ] `invitation/testsupport/TestInvitationBarrier#waitingForLock` とはまとめない（9節の Q-A の決定 A）。`TestInvitationBarrier` を変えず、まとめない理由と、`V8MigrationIT` を消した後は待ちの確かめが2か所（`auth/testsupport/H2SessionWaits`・`invitation/testsupport/TestInvitationBarrier`）になることを `code-summary.md` に記録する
- [ ] この時点では `V8MigrationIT` を消さない（移した手伝いを使う Step 4 が通るまで残す。順を逆にすると移す前に手伝いが消える、`cicd-pipeline.md` 4節）
- [ ] 対応: NFR9.6、`logical-components.md` 6節の 1、基盤の設計の R-02

### Step 4: ダミーの行8つを排他したときの `lockDummyForUpdate` の結合テスト（片付けの順 2）

- [ ] `auth/repository/LoginAttemptStateRepositoryIT` にテストを1件足す（`auth.repository` の足りない分岐「空いたダミーの行が無い」側、NFR9.5）:
  1. 別のスレッドのトランザクションで、ダミーの行8つ（`subject_id` が −1〜−8）をすべて `lockForUpdate` で排他し、合図を出して確定を待たせる
  2. もう1つのスレッドで `lockDummyForUpdate` を呼ぶ（`SKIP LOCKED` で空になり、乱数で選んだ行の排他を待つ）
  3. `H2SessionWaits` で、`login_attempt_states` の `for update` の文が実行中になったことを確かめる。上限の時間は `lockDummyForUpdate` の待ちの上限（3 秒）より短くし（例 2 秒）、確かめを 3 秒より前に終える（NFR 設計の R-04）
  4. 排他を持つ側を確定し、`lockDummyForUpdate` がダミーの行（`subject_id` が負）を1つ返すことを確かめる
- [ ] 説明文は英語（例: `when every dummy row is held, a random dummy row is waited for and returned`）。`sleep` に頼らない
- [ ] `unit-test-instructions.md` の `LoginAttemptStateRepositoryIT` のコマンドで流し、通ることを確かめる。上の手順で確かめが 3 秒に間に合わず不安定になるときは、原因（待ちに入るまでの時間）を確かめてから直す。上限の時間を延ばして済ませない（`team.md` の Testing Posture）
- [ ] 対応: NFR9.5、`security-design.md` 9.2、`cicd-pipeline.md` 4節の 2

### Step 5: `InvitationSchemaIT` へ確かめを移す（片付けの順 3）

- [ ] `stateChangesAndUniqueness` の2件目の招待中の追記の確かめ（今は例外の型だけ）に、原因の連なりの SQLState が `23505` であることと、文言が制約の名前 `UK_INVITATIONS_PENDING_EMAIL` を含むことの確かめを足す（`V8MigrationIT` の (a) を弱めない、NFR 設計の R-05）
- [ ] `V8MigrationIT` の (b) を1件足す: 招待中でない状態（`CANCELLED`・`REPLACED`・`COMPLETED`）の行は同じメールアドレスで重ねて追記でき、`pending_email` が空で、続けて同じメールアドレスの `PENDING` を1件追記できる。知らない状態の値（例 `EXPIRED`）は CHECK の制約（SQLState `23513`）で拒否される。今の `InvitationSchemaIT` の JDBC の形（Spring を起動したテストの内部DB）で書く（Q2 A）
- [ ] `V8MigrationIT` の (c) は `stateChangesAndUniqueness`（取り消した後に同じメールアドレスで招待中を作れる）が確かめ済みのため移さない。(d)（同時の追記の待ちと一意の違反）は移さず、`invitation/service/InvitationConcurrencyIT` の `two simultaneous invitations to the same email create one, the other is AlreadyPending with its id` が業務の層で確かめているとみなす。この対応を `code-summary.md` に表で記録する（Q2 A）
- [ ] `unit-test-instructions.md` の `InvitationSchemaIT` のコマンドで流す
- [ ] 対応: NFR 設計の Q2 A・R-05、`cicd-pipeline.md` 4節の 3

### Step 6: V7・V8 のテストと複写の置き場を消し、参照が残っていないことを確かめる（片付けの順 4・5）

- [ ] 消す: `user/repository/V7MigrationIT.java`・`V7BackwardCompatibilityIT.java`、`invitation/repository/V8MigrationIT.java`・`V8BackwardCompatibilityIT.java`（Q3 A、`security-design.md` 8.3）
- [ ] 消す: `backend/src/test/resources/db/migration-through-v6/`・`migration-through-v7/`（消すテストだけが使う複写）
- [ ] 参照の確かめ: 消したクラスの名前・`migration-through-v6`・`migration-through-v7` が、`backend/`・`build.gradle.kts`・`settings.gradle.kts`・`.gitleaks.toml`・`backend/config/`・`.github/` に残っていないことを検索で確かめる。`README.md` の参照は Step 21 で直す（`cicd-pipeline.md` 4節の表）。ワークフローの記録（`aidlc/` の下）の過去の Intent の記述は直さない
- [ ] `unit-test-instructions.md` の片付けの確かめのコマンド（`user.repository`・`invitation.repository` の結合テスト）で流し、消した後も通ることを確かめる
- [ ] 対応: NFR 設計の Q3 A、`cicd-pipeline.md` 4節の 4・5

### Step 7: 片付けの区切りの確かめ

- [ ] Step 3〜6 の変更がテストのソースと資源だけで、`src/main` を変えていないことを `git diff --stat` で確かめる（`packagesJudgedByTotal` の扱いに関わらない、`cicd-pipeline.md` 4節）
- [ ] C1 の区切りの内容をここで固め、生成の後のコミットの提案（Step 25）に使う

### Step 8: ドメインの層の前の確かめ

- [ ] 次の Step で区分を足すと、区分の数や `switch` を前提にした既存のテストが変わることを、影響の範囲として確かめる: `auth/domain/TokenAuthenticationExceptionTest`（`fourReasons` が `hasSize(4)`）、`access/domain/AccessDeniedReasonTest`（`everyTokenFailureReasonIsCovered` は `TOKEN_EXPIRED` 以外に理由があると期待）、`auth/web/TokenAuthenticationEntryPointTest`・`auth/domain/TokenAuthenticationExceptionTest` の `@EnumSource(TokenFailureReason.class)`、`audit/domain/AuditEventFactoryTest` の `@EnumSource(LoginFailureReason.class)`
- [ ] 対応: 8節の D-8

### Step 9: ドメイン — 実装

- [ ] `auth.domain.LoginFailureReason` に `ACCOUNT_SUSPENDED`（17 文字、説明は「利用停止中」）を足す（C7、BR2.5・BR7.2）
- [ ] `auth.domain.TokenFailureReason` に `USER_SUSPENDED`（14 文字、説明は「利用者が利用停止中」）を足す（Q3 A、BR4.1・BR7.2）
- [ ] `access.domain.AccessDeniedReason#of(TokenFailureReason)` の `switch` に `case USER_SUSPENDED -> Optional.empty();` を足し、Javadoc に「有効期限切れと同じく出来事にしない（契約 C7 の not_recorded）」を書く（BR4.2、`security-design.md` 2.4）
- [ ] `audit.domain.AuditFailureReason` に `ACCOUNT_SUSPENDED` を足し、`audit.domain.AuditEventFactory` の `LoginFailureReason` の変換に1行足す（`security-design.md` 6節。2.2 の読み方のとおり、コンパイルのためこの Step で行う）
- [ ] `auth.domain.AuthenticationEvent` に `toString` を足す: `enteredEmail` は値の有無に関わらず `***` と出し（`UserSummary` の伏せ方と同じ）、種類・日時・利用者 ID・理由・送り手の情報・トレースID は今のまま出す。`EmailAddress.mask` は使わない（`auth.domain` を `user.domain` に依存させない、`security-design.md` 5.1）
- [ ] `auth.domain.AuthenticatedUser` に `toString` を足す: `email` を `***` にし、`userId`・`admin` を出す（NFR 設計の承認の場の決定）
- [ ] 対応: BR2.5・BR4.1・BR4.2・BR7.2、NFR2.3・NFR3.1、C7

### Step 10: ドメイン — テスト（単体）

- [ ] `auth/domain/TokenAuthenticationExceptionTest`: 区分の数のテストを 5 にし（説明文も `five reasons are defined`）、`USER_SUSPENDED` が `invalid_token` の誤りになることを `@EnumSource` で含める
- [ ] `access/domain/AccessDeniedReasonTest`: `USER_SUSPENDED` が理由なし（空）になるテストを足し、`everyTokenFailureReasonIsCovered` の期待を「`TOKEN_EXPIRED`・`USER_SUSPENDED` は空、ほかは同じ名前の理由」に直す。変換が区分を尽くすことは `@EnumSource` のまま確かめる（NFR2.3）
- [ ] `audit/domain/AuditEventFactoryTest`: `ACCOUNT_SUSPENDED` の LOGIN_FAILED が、結果 FAILURE・理由 `ACCOUNT_SUSPENDED`・操作した人に利用者 ID・入れたメールアドレスが入った監査の行になることを1件足す（既存の `everyLoginFailureReasonIsCopied` も新しい値を含む）
- [ ] `auth/domain/SecretTypesTest`: `AuthenticationEvent#toString` が入れたメールアドレスを含まず `***` を含み、ほかの項目（種類・理由・送り手の IP・トレースID）を含むこと、`AuthenticatedUser#toString` がメールアドレスを含まず利用者 ID と管理者の印を含むことを足す（テストのメールアドレスは `example.com`）
- [ ] `unit-test-instructions.md` のドメインの層のコマンドで流し、通ることを確かめる
- [ ] 対応: NFR2.3・NFR3.1、BR4.2・BR7.2

### Step 11: スキーマ（V9）と DB アクセス — 実装

- [ ] `backend/src/main/resources/db/migration/V9__u1_user_suspension.sql` を作る。先頭のライセンスヘッダーは既存の V1〜V8 と同じ SQL のコメントの形、本文は `ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL;` の1文だけ（`security-design.md` 8.1、BR1.2、NFR10.1）。V1〜V8 は書き換えない
- [ ] `user.domain.User` に `@Column(name = "suspended", nullable = false) private boolean suspended;`（作るときは false）と `isSuspended()` を足す。書き換えのメソッドは作らない（NFR1.4）。Javadoc に「書き換えは `user.repository` の停止の列だけの更新の問い合わせで行う」と書く
- [ ] `user.repository.UserRepository` に `updateSuspended` を足す: `@Modifying(clearAutomatically = true, flushAutomatically = true)`、`@Query("UPDATE User u SET u.suspended = :suspended WHERE u.userId = :userId")`、更新した行の数を返す（`security-design.md` 4.1、BR1.4・BR1.5、NFR9.4）
- [ ] `auth.repository.RefreshTokenRepository` に `revokeAllActiveByUserId` を足す: `@Modifying(clearAutomatically = true, flushAutomatically = true)`、`update RefreshToken t set t.revokedAt = :revokedAt where t.userId = :userId and t.revokedAt is null`、更新した行の数を返す（期限切れも含む、BR5.1、NFR5.3・NFR9.4）
- [ ] 問い合わせはどれも名前つきの引数で、文字列をつなげない（SpotBugs の `SQL_` の関門）
- [ ] 既存の V8 までの結合テストで `users` に JDBC で追記している箇所（`suspended` を書かない）が、既定の値で動くことを Step 12 の実行で確かめる（列を知らない追記の形の、ついでの確かめ。後方互換の自動のテストの代わりにはしない）
- [ ] 対応: BR1.1・BR1.2・BR1.4・BR1.5・BR5.1、NFR1.4・NFR5.3・NFR9.4・NFR10.1

### Step 12: スキーマと DB アクセス — テスト（結合）

- [ ] `user/repository/UserSchemaIT`: エンティティで `suspended` の既定（作った直後は false）を読み戻せること、更新の問い合わせで true・false を書いて読み戻せることを足す（Q1 B。起動時の `validate-on-migrate` と `ddl-auto: validate` は、このクラスの Spring の起動で同時に確かめる）。`INFORMATION_SCHEMA` で列の定義を確かめるテストは足さない（Q1 B）
- [ ] `user/repository/UserRepositoryIT`: `updateSuspended` が `suspended` だけを書き換え、管理者の印・氏名と表示の設定の4列・パスワードのハッシュを変えないこと、いない利用者 ID で 0 を返すこと、同じトランザクションで先に `findById` で読み込んだ後に更新しても、後の `findById` が書いた値を返すこと（文脈を空にする）を足す
- [ ] `auth/repository/RefreshTokenRepositoryIT`: `revokeAllActiveByUserId` で、対象の利用者の未無効の行（期限切れを含む）がすべて指定の時刻で無効になり件数が返ること、無効の行の `revokedAt` とほかの利用者の行が変わらないこと、未無効の行が無いとき 0 を返すことを足す。件数は未無効 100 件・無効の行と、ほかの利用者の行を混ぜた形で確かめ、時間は比べない（NFR5.3、Q2 A）
- [ ] `unit-test-instructions.md` の DB アクセスの層のコマンドで流す
- [ ] 対応: NFR5.3・NFR10.1（起動と読み書きの範囲だけ）・NFR11.2、BR1.4・BR1.5・BR5.1

### Step 13: 業務処理 — 実装

- [ ] `user.service.UserSummary` の最後に `boolean suspended` を足し、`toString` に `suspended=` を出す。Javadoc の `@param` を足す（BR1.3、`security-design.md` 2.1）
- [ ] `user.service.UserAccountService`:
  - `toSummary` が `user.isSuspended()` を写す（読み取りの回数は変えない、NFR5.1）
  - `isSuspended(long userId)`: `@Transactional(readOnly = true)`、`findById` の結果の `isSuspended()`、いなければ `IllegalStateException`（文言に利用者 ID だけ）
  - `setSuspended(long userId, boolean suspended)`: `@Transactional(propagation = Propagation.MANDATORY)`、`updateSuspended` が 0 なら `IllegalStateException`。拒否の判定・監査・トークンの無効化はしない（BR1.5）。Javadoc に C1 の約束（呼ぶ前に読み込んだエンティティは切り離される、`logical-components.md` 3節）を書く
- [ ] `auth.service.RevokeAllResult`（record、`int revoked`、0 以上を生成時に確かめる）と `auth.service.RefreshTokenRevocationService` を作る: `revokeAllRefreshTokens(long userId)` は `@Transactional(propagation = Propagation.MANDATORY)`、注入した `Clock` の今の時刻で `revokeAllActiveByUserId` を1回呼び、DEBUG のログに `userId`・`revoked` をキーと値で出し、`RevokeAllResult` を返す。監査の出来事は出さない（BR5.1〜BR5.3、NFR3.2）
- [ ] `auth.service.LoginService#decide`: 本人の行を `lockForUpdate` で読んだ直後、`LockPolicy.decide` の前に、`user.suspended()` が true なら `attemptRepository.update(user.userId(), current.consecutiveFailures(), current.lockedUntil())` を1回、`publishFailure(verification.email(), user.userId(), LoginFailureReason.ACCOUNT_SUSPENDED, now, client)` を1件行い、`Decision.FAILED` を返す（`security-design.md` 2.2、BR2.1〜BR2.5）。行が無いときの「作ってやり直す」流れは今のまま停止の判定より前にある。判定のトランザクションで `users` を読み直さない（BR5.5、R-03）。停止で拒否したことのアプリのログは足さない（8節の D-9）。クラスの Javadoc の手順に停止の判定を足す
- [ ] `auth.service.TokenRefreshService#refresh`: `findById` の直後に `user.suspended()` が true なら `failed()` を投げる（例外で `revokeIfActive` も巻き戻る、監査は出さない。BR3.1・BR3.2）。Javadoc の `@throws` に停止中を足す
- [ ] `auth.service.LoginCommand` に `toString` を足し、`email` を `***` にする（パスワードは `Password` の伏せ字のまま）
- [ ] `user.service.PasswordVerification` に `toString` を足し、`email` を `***` にする（`user` は `UserSummary` の伏せた文字列化のまま、`matched` は出す）（9節の Q-C B）。`UserAccountService#verifyPassword(String, Password)`・`#existsByEmail(String)` の `String` の引数は変えない（後の Intent へ）
- [ ] `UserSummary` の引数が増えるため、テストで `new UserSummary(...)` を使う5か所（`auth/service/LoginServiceTest`・`LogoutServiceTest`・`TokenRefreshServiceTest`、`user/service/UserAccountServiceTest`、`dslmanage/service/DslLifecycleTest`）に `false` を足す
- [ ] 対応: BR1.3〜BR1.5・BR2.1〜BR2.6・BR3.1・BR3.2・BR5.1〜BR5.3、NFR1.1・NFR2.2・NFR3.1・NFR3.2・NFR5.1・NFR11.2、C1

### Step 14: 業務処理 — テスト（単体・結合）

- [ ] `auth/testsupport/TestUserSuspension` を作る: `suspend(userId)`（同じトランザクションで `setSuspended(true)` と `revokeAllRefreshTokens`、U3 の止める操作と同じ組）と `resume(userId)`（`setSuspended(false)` だけ）を `TransactionTemplate` で呼ぶ。テストの中で停止の状態を入れる口はこれだけにし、JDBC で `users` を直接書き換えない
- [ ] `user/service/UserAccountServiceTest`: `findById` と `verifyPassword` の要約に `suspended` が写ること（true・false）、`isSuspended` が値を返しいなければ `IllegalStateException`、`setSuspended` が 0 行で `IllegalStateException`、`UserSummary#toString` が真偽を出しメールアドレスと氏名を出さないこと、`PasswordVerification#toString` がメールアドレスと氏名を含まず `***` と照合の結果を含むこと（利用者がいない・いるの両方）を足す（9節の Q-C B）
- [ ] `user/service/UserSuspensionIT`（新しい。Spring と組み込みの H2）: トランザクションの外から `setSuspended` を呼ぶと `IllegalTransactionStateException`、呼び出し元を巻き戻すと停止の状態が戻ること、同じトランザクションで (1) `findById` で読み込み、(2) `setSuspended(true)` の後に `isSuspended` と `findById` の要約の `suspended` が true、(3) `setSuspended(false)` の後にどちらも false（R-05）、いない利用者 ID の `setSuspended`・`isSuspended` が `IllegalStateException` で呼び出し元が巻き戻ること、止めても管理者の印・氏名と表示の設定・ロックの状態の行が変わらないこと（BR1.5）
- [ ] `auth/service/RefreshTokenRevocationServiceTest`（単体）: 注入した時計の時刻で無効にすること、件数を `RevokeAllResult` で返すこと（0 件も成功）、DEBUG のログのキーが `userId`・`revoked` だけでトークンの値・ハッシュが無いこと（`LogEvents`）、`RevokeAllResult` が負の数を拒むこと
- [ ] `auth/service/RefreshTokenRevocationServiceIT`（新しい）: トランザクションの外から呼ぶと `IllegalTransactionStateException`、`setSuspended(true)` と同じトランザクションで呼んで巻き戻すと停止の状態とトークンの無効化の両方が戻ること、確定すると両方が残ること、停止を解いても無効にした行は戻らないこと（BR5.4）
- [ ] `auth/service/LoginServiceTest`: 停止中の4つの場合（正しいパスワード・誤ったパスワード・ロック中・本人の行が無い）で、`update` が読んだ値のまま1回（行が無いときは作ってやり直した後に1回）、出来事が `LOGIN_FAILED`・`ACCOUNT_SUSPENDED`・利用者 ID の1件、例外が `AUTHENTICATION_FAILED`、トークンの発行とリフレッシュトークンの保存が無いことを確かめる。停止中かつロック中の理由が `ACCOUNT_SUSPENDED` になること。説明文に `suspended login attempts do not increase the failure count` を入れたテストで、失敗回数 n（しきい値未満）が n のまま書かれ、しきい値−1 の状態でもロックが掛からないことを確かめる（NFR 設計の R-06、AC3.2.8）。停止を解いた後（`suspended` が false の要約）は、止める前の回数から今までどおり数えること
- [ ] `auth/service/TokenRefreshServiceTest`: 停止中の利用者のトークンで `REFRESH_FAILED` になり、`issueTokens` が呼ばれないこと（巻き戻しは結合テストで確かめる）
- [ ] `auth/service/LoginCommandTest`（新しい）: `toString` がメールアドレスとパスワードを含まないこと
- [ ] `unit-test-instructions.md` の業務処理の層のコマンドで流す
- [ ] 対応: NFR1.1・NFR1.3・NFR2.2・NFR3.1・NFR3.2・NFR9.2・NFR9.3・NFR11.2、BR1.4・BR1.5・BR2.1〜BR2.5・BR3.1・BR5.1〜BR5.4、AC3.2.3・AC3.2.8

### Step 15: 認証の3つの入口（web）— 実装

- [ ] `auth.web.AccessTokenAuthenticationProvider#authenticate`: `findById` の直後に `user.suspended()` が true なら `TokenAuthenticationException(TokenFailureReason.USER_SUSPENDED)` を投げる。入口（`TokenAuthenticationEntryPoint`）と応答（401 `AUTHENTICATION_REQUIRED`）は変えない（BR4.1・BR6.1）
- [ ] `auth.web.CurrentUserResponse` に `toString` を足し、`email`・`displayName` を `***` にする（`admin` と表示の設定は出す。JSON の応答は変えない）
- [ ] `auth.web.LoginRequest` の今の `toString`（`LoginRequest[email=<値>, password=***]`）を、`email` も `***` にする形に直す。JSON の受け取りと入力の検証は変えない（9節の Q-C B）
- [ ] ログインとトークンの更新の入口（`AuthController`）は変えない（判定は Step 13 の業務処理にある）
- [ ] 対応: BR4.1・BR4.4・BR6.1、NFR1.1・NFR2.1・NFR3.1

### Step 16: 認証の3つの入口 — テスト（単体）

- [ ] `auth/web/AccessTokenAuthenticationProviderTest`（新しい。`AccessTokenService` と `UserAccountService` を Mockito で差し替える）: 有効な利用者は `AuthenticatedUser` の主体になる、停止中は区分 `USER_SUSPENDED`、いない利用者は `USER_NOT_FOUND`、`supports` が Bearer だけを受ける、停止の判定のために `findById` を1回しか呼ばない
- [ ] `auth/web/WebSecretTypesTest`: 既存の `the login request hides the password but keeps the email` の確かめを、`LoginRequest#toString` がパスワードとメールアドレスの両方を含まず `***` を含む形に直し、説明文も合わせる（例 `the login request hides the password and the email`。9節の Q-C B、8節の D-8）。`CurrentUserResponse#toString` がメールアドレスと氏名を含まず、`TokenResponse#toString`（中に `CurrentUserResponse` を持つ）も含まないことを足す
- [ ] `unit-test-instructions.md` の入口の層の単体のコマンドで流す
- [ ] 対応: NFR1.1・NFR3.1、BR4.1

### Step 17: 認証の3つの入口 — テスト（結合）

- [ ] `auth/web/SuspendedUserAuthenticationIT`（新しい。`@SpringBootTest(webEnvironment = RANDOM_PORT)`、`AuthApiTestConfig` の `MutableClock`・`CountingPasswordEncoder`、`TestUserSuspension`）。テストの利用者はテストごとに `example.com` のメールアドレスで作り、前のテストの状態に頼らない:
  - 入口ごとの拒否（NFR1.1、AC3.2.1〜AC3.2.3）: 止めた後、止める前のアクセストークンで保護された窓口 → 401 `AUTHENTICATION_REQUIRED`、止める前のリフレッシュトークン → 401 `REFRESH_FAILED`、正しいパスワード・誤ったパスワード・ロック中（停止の前にしきい値まで失敗させた利用者）のログイン → 401 `AUTHENTICATION_FAILED` でトークンと Cookie が無い
  - 応答の同じさ（NFR2.1、BR6.1）: 入口ごとに、停止中とほかの失敗（ログインはパスワードの誤り、更新は無効にしたトークン、アクセストークンは改ざんしたトークン）の状態コード・code・本文の項目（`traceId` などの毎回変わる値を除く）が同じこと、本文に `SUSPENDED` を含まないこと
  - 停止の間の更新の巻き戻し（BR3.1、M8 B の隙を作る形）: `TestUserSuspension` を使わずに `setSuspended(true)` だけを確定させた利用者の、まだ有効なリフレッシュトークンでの更新が 401 で、そのトークンの `revoked_at` が空のまま（巻き戻った）
  - 停止を解いた直後（NFR1.2、AC3.2.4）: 新しいログイン・そのとき出たリフレッシュトークンでの更新・新しいアクセストークンでの要求がすべて受け付けられ、止める前のリフレッシュトークンでの更新は 401 `REFRESH_FAILED`。停止の前の失敗回数から続くこと（停止中のログインが数えられていない、AC3.2.8）
  - 止める前のアクセストークンの時刻の例（NFR1.3、AC3.2.7、FS の 2.4）: `MutableClock` で t0 にログイン、t0＋1 分に止め、t0＋1 分〜t0＋2 分は 401、t0＋2 分に解き、t0＋5 分の1ミリ秒前は受け付け、t0＋5 分ちょうどは 401。`sleep` と実時刻に頼らない
  - 停止中の管理者（NFR2.3、AC3.2.5、BR4.4）: 管理者を作って止め、止める前のアクセストークンで管理の API（既存の `/api/admin/` の下の確かめの窓口と招待の一覧）を呼ぶと 401 で、対象の状態が変わらず、監査の表に業務の行もアクセスの拒否の行も増えない
  - 問い合わせの回数（NFR5.1）: `SqlStatementCounter` で、停止中・有効のアクセストークンの要求がどちらも `select users` を1回だけ出すこと、停止中の更新が有効な更新と同じ数の `select users` で止まること
- [ ] `auth/web/LoginApiIT` に1件足す（NFR2.2、AC3.2.3）: 停止中の3つの場合（正しいパスワード・誤ったパスワード・ロック中）と本人の行が無い停止中の利用者のログインが、パスワードの誤りと同じ応答、同じ文の並び（`select users`・`select login_attempt_states for update`・`update login_attempt_states`・`insert audit_events`。行が無い場合は行を作る文を含めてパスワードの誤り（行なし）と同じ並び）、同じ照合の回数（1回）であること。既存の `failuresAreIndistinguishable` は変えない
- [ ] `auth/web/AccessTokenApiIT` に1件足す: 停止中の利用者のトークンが 401 で、入口の DEBUG の区分が `USER_SUSPENDED` であること
- [ ] `access/web/AccessDeniedEventsIT` に1件足す: 停止中の利用者の有効なトークンで管理の窓口を呼ぶと 401 で、アクセスの拒否の出来事が出ないこと（有効期限切れと同じ、NFR2.3、C7）
- [ ] `user/web/MePreferencesApiIT` と `user/web/MePasswordApiIT` に1件ずつ足す（NFR1.4、BR1.6、`team.md` の要求の改ざん）: 本文に `"suspended": true`（とあわせて `"admin": true`）を足して送っても、停止の状態と管理者の印が変わらず、次のアクセストークンの要求が通ること。停止中の利用者が `"suspended": false` を足して送っても、401 のまま状態が変わらないこと
- [ ] `invitation/web/InvitationAdminApiIT` に1件足す（AC3.2.6、BR6.4、R-04）: 停止中の利用者のメールアドレスへの招待が、今と同じ 409 `INVITATION_EMAIL_REGISTERED` で、招待の行とメールが作られないこと
- [ ] `unit-test-instructions.md` の入口の層の結合のコマンドで流す
- [ ] 対応: NFR1.1〜NFR1.4・NFR2.1〜NFR2.3・NFR5.1・NFR9.1〜NFR9.3、BR1.6・BR3.1・BR3.3・BR4.1〜BR4.4・BR6.1・BR6.2・BR6.4、AC3.2.1〜AC3.2.8

### Step 18: AC3.2.6 の回帰テストの記録

- [ ] AC3.2.6 の回帰テスト1件（機能設計の R-04）が `InvitationAdminApiIT` にあり、既存の招待の確かめ（登録済みの拒否）を変えていないことを `code-summary.md` に記録する
- [ ] 対応: AC3.2.6、BR6.4

### Step 19: 監査 — テスト（結合）

- [ ] `audit/service/AuditAuthenticationEventsIT` に1件足す（BR2.5、AC3.2.8、C7、NFR6.1）: 停止中の利用者の正しいパスワード・誤ったパスワードのログインが、それぞれ `LOGIN_FAILED`・結果 `FAILURE`・理由 `ACCOUNT_SUSPENDED`・操作した人に本人の利用者 ID・入れたメールアドレス（パスワードの誤りと同じ形で入る。停止中だけ空にしない、NFR 設計の R-03）・送り手の情報・トレースID の1行で残り、要求のスレッドで1回の insert であること
- [ ] 停止中の更新とアクセストークンの認証は監査の行を作らないこと（C7 の not_recorded、BR3.2・BR4.2）を同じクラスか `SuspendedUserAuthenticationIT` で確かめる（行の数が増えない）
- [ ] `audit/service/AuditWriteFailureIT` に1件足す（NFR6.2）: 監査の書き込みの失敗（`APPEND_FAILURE`・`CONNECTION_FAILURE`）でも、停止中のログインの応答（401 `AUTHENTICATION_FAILED`）が変わらないこと
- [ ] `unit-test-instructions.md` の監査の層のコマンドで流す
- [ ] 対応: NFR6.1・NFR6.2、BR2.5・BR3.2・BR4.2、C7

### Step 20: 漏えいのテスト（TRACE を有効にした結合テスト）

- [ ] `auth/web/AuthSuspensionSecretLeakIT`（新しい。既存の `AuthSecretLeakIT` と同じく `SpringApplicationBuilder` で起動し、`OutputCaptureExtension` で出力を捕まえる。9節の Q-B の決定 A）。`--logging.level.cherry.mastersmith.auth=TRACE`・`user=TRACE`・`audit=TRACE`・`dslmanage=TRACE` を渡し、アプリの既定のログのレベル（`application.yaml`）は変えない:
  - 流す操作: ログインの成功・パスワードの誤り・停止中のログイン（正しいパスワード）・トークンの更新・停止中の更新・停止中のアクセストークンの要求・まとめての無効化（`TestUserSuspension#suspend`）・管理者のトークンでの DSL のプレビューの読み取り（`DslAdminController` の引数の `AuthenticatedUser`）
  - 確かめ1（値が無い）: 出力全体に、パスワード（平文）・パスワードのハッシュ・アクセストークン・リフレッシュトークンの値・署名鍵が無いこと（`JsonLogRecords.assertContainsNoSecret`）、すべての行が JSON であること
  - 確かめ2（伏せ字）: 次の TRACE の行が出ていて、その行にテストの利用者のメールアドレス（と氏名）が無いこと: `ENTER LoginService#login`（`LoginCommand`）・`ENTER AuditEventListener#onAuthenticationEvent`（`AuthenticationEvent`）・`EXIT AuthController#login` と `EXIT AuthController#refresh`（`TokenResponse` の中の `CurrentUserResponse`）・`ENTER DslAdminController#preview`（`AuthenticatedUser`）・`ENTER RefreshTokenRevocationService#revokeAllRefreshTokens`・`EXIT` の `RevokeAllResult`、加えて9節の Q-C B の2つ: `ENTER AuthController#login`（`LoginRequest`）・`EXIT UserAccountService#verifyPassword`（戻り値の `PasswordVerification`）。行の文字は実際に出る形を Step の中で確かめてから書く。`ENTER UserAccountService#verifyPassword` の行は `String` の引数でメールアドレスを出すため（後の Intent に回した残る漏えい）、確かめの対象に入れない
  - 確かめ3（無効化のログ）: まとめての無効化の DEBUG の行のキーが `userId`・`revoked` だけで、トークンの値・ハッシュが無いこと（NFR3.2）
  - 確かめ4（応答）: 停止中の3つの入口の応答の本文に、メールアドレス・トークンの値・`SUSPENDED` が無いこと
- [ ] メールアドレスが出力全体に無いことは確かめない。9節の Q-C B の決定で後の Intent に回した2つ（`UserAccountService#verifyPassword(String, Password)`・`#existsByEmail(String)` の `String` の引数）が TRACE にメールアドレスを出すため（8節の D-10）。この2つが出る行の件数を記録し、`code-summary.md` に残る漏えいとして書く
- [ ] 既存の `AuthSecretLeakIT` は変えない
- [ ] `unit-test-instructions.md` の漏えいのテストのコマンドで流す
- [ ] 対応: NFR3.1・NFR3.2、BR2.6・BR5.3、`security-design.md` 5節・5.1、NFR 設計の承認の場の決定、9節の Q-C B

### Step 21: 構造の検査と文書

- [ ] 構造の検査: `ArchitectureTest` と機能ごとの境界テスト（`AuthBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`・`InvitationBoundaryArchitectureTest` ほか、`unit-test-instructions.md` の構造の検査のコマンド）を、変更なしで流して通ることを確かめる。ArchUnit のテストは変えない。通らないときは本体の置き場を直し、テストを緩める必要が出たら生成を止めて依頼者に諮る（NFR11.1、`team.md` の Code Style）
- [ ] `docker/monitoring/provisioning/alerting/mastersmith.yaml` に差が無いことを `git diff` で確かめる（NFR5.5）
- [ ] `README.md` の「スキーマの変更（Flyway）」の V7・V8 の行の「(1) 自動の結合テスト（…）」を、Intent 260930-user-admin の B1 で消したことと理由（依頼者の決定。マスタ管理の機能本体がまだ無いため、戻す場合を想定したテストは置かない）に置き換え、(2) の戻しの練習の記述は過去の配備で行ったこととして残す（`cicd-pipeline.md` 7節）
- [ ] 同じ節に V9 の行を足す: `V9__u1_user_suspension.sql`（Intent 260930-user-admin の U1）が `users.suspended`（既定 FALSE・必須、既存の利用者は有効）を足す前進のみの変更であること、移行のテストと戻しの練習を置かないこと、1つ前の版に戻している間は停止が効かないこと（停止中の利用者も3つの入口を通れる。止めたときに無効にしたリフレッシュトークンは無効のまま）、戻す前に停止中の利用者を確かめる手順は配備の段で決めること（NFR10.3、R2）
- [ ] `README.md` の既存の記述を変更に合わせて直す（8節の D-7）: 後続の単位の提供口の表の `TokenFailureReason` の値に `USER_SUSPENDED`（応答と監査には出ない区分）を足し、`AuthenticatedUser` の行に「文字列化でメールアドレスを伏せる」を足す。監査の節（ログインの失敗の理由）に `ACCOUNT_SUSPENDED` を足す。戻しの節（V7・V8 の注意の並び）への V9 の注意の追記は deployment-pipeline の持ち物のため、この Bolt では行わない（`cicd-pipeline.md` 6節）
- [ ] `.idea/.gitignore` に `/dataSources.xml` の1行を足す（`/dataSources/` と `/dataSources.local.xml` はすでにある。ルートの `.gitignore` は変えない、`cicd-pipeline.md` 7節）
- [ ] 対応: NFR5.5・NFR10.3・NFR11.1、UQ2 A、B1 の完了の条件

### Step 22: カバレッジの一覧（`packagesJudgedByTotal`）

- [ ] `git diff --name-only develop -- backend/src/main` で、実際に `src/main` を変えたパッケージの一覧を作り、`packagesJudgedByTotal` と突き合わせる（「手を入れる」は説明文だけの直しを含む、`team.md` の Testing Posture）。見込みは `auth.domain`・`auth.repository`・`access.domain` の3つ。ほかに一覧のパッケージに手が入っていたら、同じくテストを足して外す
- [ ] `backend/build.gradle.kts` の `packagesJudgedByTotal` から外す対象を消し、上の説明文に「Intent 260930-user-admin の B1（U1）で auth.domain・auth.repository・access.domain を外した」を足す。一覧に足さない。計測の除外（`coverageExclusions`）を増やさない
- [ ] 対応: NFR9.5、`team.md` の Testing Posture、`cicd-pipeline.md` 5節

### Step 23: 1コマンドの検査（統合の前の関門）

- [ ] colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段（フォーマット・リンタ・ライセンスヘッダー・ビルド・単体・結合・カバレッジ・安全の検査・成果物）が通ることを確かめる。対象DB のテストが SKIPPED になっていないことを確かめる（`project.md` の学び）
- [ ] テストの件数（単体・結合）と、全体と Step 1 と同じパッケージの行・分岐のカバレッジを実測の数字で記録し、Step 1 の基準と比べる（既存のテストが失敗していない。消した4つのテストの分だけ結合テストの件数が減ることを数で説明する）
- [ ] 外した `auth.domain`・`auth.repository`・`access.domain` と、一覧の外の `auth.service`（`RefreshTokenRevocationService` を含む）・`auth.web`・`user.*`・`audit.domain`・`invitation.*` が、それぞれ行 80%・分岐 70% を満たすことを確かめる。下回ったらテストを足す。下限・除外は変えない（`team.md` の Testing Posture、Testing Contract）
- [ ] SpotBugs ＋ FindSecBugs の関門を除外を足さずに通ること（`backend/config/spotbugs-exclude.xml` に差が無い）、Gitleaks・OSV-Scanner が通ることを確かめる（NFR9.4）
- [ ] `backend/gradle.lockfile`・`frontend/package-lock.json` に差が無いことを確かめる。差が出たら理由と差を記録する（`cicd-pipeline.md` 8節）
- [ ] 失敗が一時的に見えるときは、`team.md` の「不安定なテストと CI の失敗」の決まりで扱う（手元で再現したら原因を直す）
- [ ] 対応: B1 の共通の完了の条件、NFR9.4・NFR9.5、`project.md` の Mandated（統合の前の確認）

### Step 24: E2E（統合の前に手元で）

- [ ] `docker compose --profile mail up -d mailpit` で Mailpit を起動してから、`./gradlew e2eTest` を実行する（`cicd-pipeline.md` 3節。U1 は E2E の流れを足さない。B1 は認証に関わるため、統合の前に流す）
- [ ] `frontend/test-results/e2e-results.json` から、件数（成功・失敗・飛ばした）とファイルごとの結果を `code-summary.md` に記録してから、`frontend/playwright-report/`・`frontend/test-results/` を消す。消したことと、報告を共有していないことを記録する（`gate-decisions.md` の U5 R-02 の決定）
- [ ] 失敗したら原因を直してから Step 23 からやり直す。Mailpit は見終わったら止める
- [ ] 対応: B1 の共通の完了の条件、`gate-decisions.md`

### Step 25: 記録、コミットの提案、統合の提案

- [ ] `code-summary.md`（作ったもの・消したもの、Step 1 と Step 23 の実測、外したパッケージと値、(d) の対応、待ちの確かめの2か所、E2E の結果と報告を消したこと、上流との差、9節の依頼者の決定と、後の Intent に回した残る漏えい2つ）、`source-manifest.json`（作った・変えた・消したアプリのソースとテストのパスすべて）、`traceability.json` を作る（コード生成の段の手順）
- [ ] 3節の C1〜C6 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 依頼者の承認を得て、`develop` へ squash の1コミットで統合する（件名と本文に「移行のテストの片付けを含む」）。統合の後、依頼者の承認を得て作業ブランチを消す。プッシュは依頼者が行う
- [ ] 対応: 段の記録、`project.md` の Change Control、`team.md` の Way of Working

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US3.2 止められている間は使えず、解かれたら使える（主） | AC3.2.1（止める前のアクセストークンは 401） | Step 15〜17 |
| US3.2 | AC3.2.2（止める前のリフレッシュトークンは 401 `REFRESH_FAILED`） | Step 11〜14・17 |
| US3.2 | AC3.2.3（正しいパスワードでも 401、パスワードの誤りと区別できない、照合と読み書きの回数が同じ） | Step 13・14・17 |
| US3.2 | AC3.2.4（解いた直後は3つの入口で受け付け、止める前のリフレッシュトークンは拒否） | Step 14・17 |
| US3.2 | AC3.2.5（停止中の管理者は管理の API で 401、状態と監査が変わらない） | Step 9・10・15・17 |
| US3.2 | AC3.2.6（停止中の利用者のメールアドレスへの招待は登録済みの拒否のまま） | Step 17・18 |
| US3.2 | AC3.2.7（止める前のアクセストークンの時刻の例） | Step 17 |
| US3.2 | AC3.2.8（失敗回数・解除の予定・ロックが変わらず、監査に `ACCOUNT_SUSPENDED`） | Step 9・10・13・14・17・19 |
| US3.2 | AC3.2.9・AC3.2.10（画面の動き） | サーバー側の 401 だけを Step 17 で確かめる。画面の流れは U5 の E2E（Deferred、M9 A） |
| US3.1（関わる。主は U3） | C1 の口（止める・解くの土台） | Step 11〜14 |
| 停止の状態 | BR1.1〜BR1.6、NFR1.4・NFR10.1・NFR11.2 | Step 9・11〜14・17 |
| ログインの照合 | BR2.1〜BR2.6、NFR1.1・NFR2.2 | Step 13・14・17 |
| トークンの更新 | BR3.1〜BR3.3 | Step 13・14・17 |
| アクセストークンの認証 | BR4.1〜BR4.4、NFR2.3 | Step 9・10・15〜17 |
| まとめての無効化 | BR5.1〜BR5.5、NFR5.3・NFR3.2 | Step 11〜14・20 |
| 列挙の防止と解いた後 | BR6.1〜BR6.4、NFR2.1 | Step 17 |
| 構造と名前 | BR7.1・BR7.2、NFR11.1 | Step 9・21 |
| 監査 | C7、NFR6.1・NFR6.2 | Step 9・10・19 |
| 秘密と個人に関する値 | NFR3.1・NFR3.2、`AuthenticationEvent`・`LoginCommand`・`AuthenticatedUser`・`CurrentUserResponse` の伏せ字（NFR 設計の承認の場の決定）と、`LoginRequest`・`PasswordVerification` の伏せ字（9節の Q-C B） | Step 9・10・13・14・15・16・20 |
| 性能 | NFR5.1（回数）、NFR5.2・NFR5.4（U3 の止める操作と performance-validation） | Step 17、Build and Test に引き継ぐこと |
| テスト | NFR9.1〜NFR9.6 | Step 2〜6・10・12・14・16・17・19・20・23 |
| スキーマの変更 | NFR10.1（起動と読み書きだけ）・NFR10.2（Deferred）・NFR10.3（README） | Step 11・12・21 |
| 観測 | NFR5.5 | Step 21 |
| B1 の完了の条件 | V9・3つの入口・回数・監査・C1・`.idea/.gitignore`・一覧から外す | Step 11〜22 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、主な境界の結合テストを置く（Testing Contract の `strategy_volume`）。認証・認可・監査の失敗の場合のテスト（`team.md` の Testing Posture の「利用停止」の項目すべてと、`project.md` の Mandated の認証の失敗のテスト）を含める。

| 部品 | 単体（`*Test`） | 結合（`*IT`） |
|---|---|---|
| 停止の区分と変換（`auth.domain`・`access.domain`・`audit.domain`） | `TokenAuthenticationExceptionTest`（直し）・`AccessDeniedReasonTest`（2 件足す・直す）・`AuditEventFactoryTest`（1 件）・`SecretTypesTest`（3 件） | `AccessDeniedEventsIT`（1 件） |
| 停止の状態（`user.domain`・`user.repository`・`user.service`） | `UserAccountServiceTest`（6〜7 件。`PasswordVerification` の伏せ字 1 件を含む） | `UserSchemaIT`（2 件）・`UserRepositoryIT`（3 件）・`UserSuspensionIT`（5〜6 件） |
| まとめての無効化（`auth.repository`・`auth.service`） | `RefreshTokenRevocationServiceTest`（5 件） | `RefreshTokenRepositoryIT`（3 件）・`RefreshTokenRevocationServiceIT`（4 件） |
| ログインの照合（`auth.service`） | `LoginServiceTest`（6〜7 件）・`LoginCommandTest`（1 件） | `LoginApiIT`（1 件）・`SuspendedUserAuthenticationIT` の一部 |
| トークンの更新（`auth.service`） | `TokenRefreshServiceTest`（1〜2 件） | `SuspendedUserAuthenticationIT` の一部 |
| アクセストークンの認証（`auth.web`） | `AccessTokenAuthenticationProviderTest`（5 件）・`WebSecretTypesTest`（`LoginRequest` の直し 1 件と `CurrentUserResponse`・`TokenResponse` の 2 件） | `AccessTokenApiIT`（1 件）・`SuspendedUserAuthenticationIT`（7〜8 件） |
| 監査 | — | `AuditAuthenticationEventsIT`（1〜2 件）・`AuditWriteFailureIT`（1 件） |
| 一括代入の防止・招待の回帰 | — | `MePreferencesApiIT`・`MePasswordApiIT`（1 件ずつ）・`InvitationAdminApiIT`（1 件） |
| 漏えい | — | `AuthSuspensionSecretLeakIT`（1〜2 件） |
| 移行のテストの片付け | — | `LoginAttemptStateRepositoryIT`（1 件）・`InvitationSchemaIT`（1 件足す・1 件強める） |
| 構造の検査 | 既存の ArchUnit のテスト（変えない） | — |

`team.md` の「利用停止」の必須テストとの対応:

| 必須のテスト | 確かめるテスト |
|---|---|
| 停止中は3つの入口のすべてで拒否（入口ごとにサーバー側） | `SuspendedUserAuthenticationIT`・`LoginServiceTest`・`TokenRefreshServiceTest`・`AccessTokenAuthenticationProviderTest` |
| 停止を解いた直後は3つの入口のすべてで受け付け | `SuspendedUserAuthenticationIT` |
| 停止の前に出したリフレッシュトークン・アクセストークンの扱い（決めた側の動作） | `SuspendedUserAuthenticationIT`（リフレッシュは解いた後も拒否、アクセスは停止中は拒否・解いた後は期限まで、`MutableClock`）・`RefreshTokenRevocationServiceIT` |
| ★停止を応答から推測できてよいか → 推測できない（要件 FR3.4） | `SuspendedUserAuthenticationIT`（応答の同じさ）・`LoginApiIT`（回数の同じさ） |
| 停止中の管理者は管理の API を呼べない | `SuspendedUserAuthenticationIT`・`AccessDeniedEventsIT` |
| 要求の改ざん（`/api/me` から停止の状態を変えられない） | `MePreferencesApiIT`・`MePasswordApiIT` |

どのテストも成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。性質ベースのテスト（jqwik）を当てる純粋な関数は、この単位には無い（`tech-stack-decisions.md`）。

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| ID | 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|---|
| D-1 | 伏せ字の範囲（`security-design.md` 5.1） | 確かめるのは `AuthenticationEvent` の文字列化の行だけ。`LoginCommand`・`AuthenticatedUser`・`CurrentUserResponse` は決定の外で、承認の場への申し送り | 3つの record も `toString` で伏せ字にし、単体テストと `AuthSuspensionSecretLeakIT` の TRACE の行で確かめる | NFR 設計の承認の場の依頼者の決定（申し送りへの答え）と、基盤の設計の U1 R-01 の申し送り |
| D-2 | NFR10.1・NFR10.2（`security-requirements.md`） | V9 の確かめは2段（V8 と同じ形の自動の結合テストと戻しの練習）、既存の利用者が false になることを結合テストで確かめる | どちらも作らない。起動時の検証とエンティティの読み書きだけ（Step 11・12）。`traceability.json` では NFR10.1・NFR10.2 を `Deferred`（持ち主: deployment-execution のスモークテストでの事後の裏付け） | NFR 設計の Q1 B・Q4 A と承認の場の決定 R-02（`security-design.md` 12節 S-1・S-2 が、この差を B1 の計画に書くよう求めている） |
| D-3 | `bolt-plan.md` の B1 の完了の条件 | 一覧から外すのは `auth.domain`・`auth.repository` の2つ。`.idea/.gitignore` に `dataSources.xml`・`dataSources/` を足す | 外すのは `access.domain` を含む3つ（見込み。Step 22 で実際の変更と突き合わせる）。`.idea/.gitignore` は `/dataSources/` がすでにあるため `/dataSources.xml` の1行だけ足す | 機能設計の D1（Q3 A で `AccessDeniedReason` に手が入る）と、`.idea/.gitignore` の今の中身 |
| D-4 | NFR 設計の申し送り（`AuthSecretLeakIT` を広げるか新しく足すか） | 計画で決める | 新しい `AuthSuspensionSecretLeakIT` を足し、既存の `AuthSecretLeakIT` は変えない（9節の Q-B の決定） | 既存のテストは初期管理者の起動の確かめを兼ねており、停止の操作と TRACE の行ごとの確かめを足すと1つのテストの目的が混ざるため |
| D-5 | `V8MigrationIT` の (d) の対応（NFR 設計の Q2 A） | `InvitationConcurrencyIT` が業務の層で確かめているとみなし、対応を計画に書く | `InvitationConcurrencyIT` の `two simultaneous invitations to the same email create one, the other is AlreadyPending with its id` を対応とする（Step 5） | Q2 A のとおり |
| D-6 | 監査の層の手順の位置 | （手順の順は計画で決める） | `AuditFailureReason`・`AuditEventFactory` の変更はドメインの層（Step 9）で行い、監査の層（Step 19）は結合テストだけ | `AuditEventFactory` の `switch` が `LoginFailureReason` を尽くすため、同じ時点で直さないとコンパイルが止まる |
| D-7 | README の直しの範囲（`cicd-pipeline.md` 7節） | 「スキーマの変更（Flyway）」の V7・V8 の行の直しと V9 の行の追加 | 加えて、提供口の表の `TokenFailureReason` の値・`AuthenticatedUser` の伏せ字と、監査の節のログインの失敗の理由に `ACCOUNT_SUSPENDED` を足す | README の記述を実装と食い違わせないため。戻しの節の V9 の注意は deployment-pipeline の持ち物のまま |
| D-8 | 既存のテストの手直し | （設計に無い） | `TokenAuthenticationExceptionTest`（区分の数）・`AccessDeniedReasonTest`（変換の期待）を直し、`UserSummary` を作る5つのテストに引数を足す。`WebSecretTypesTest` の `LoginRequest` の確かめ（今は「パスワードを伏せ、メールアドレスは残す」）を「メールアドレスも伏せる」に直す（9節の Q-C B） | 区分と要約の項目を足すこと、`LoginRequest` の伏せ字に伴う。テストだけの変更で、`dslmanage.service` などの一覧の扱いに関わらない |
| D-9 | 停止で拒否したことのアプリのログ（BR2.6） | 出すときは利用者 ID と区分だけ（INFO より下） | ログインの照合・更新では足さない。停止中のログインは監査に残り、アクセストークンの認証は既存の入口の DEBUG に区分 `USER_SUSPENDED` が出る | 足さなくても BR2.6 に反せず、出す値を増やさないため |
| D-10 | 漏えいのテストの確かめ方と伏せ字の範囲 | TRACE を有効にして、ログに値が無いことを見る。伏せ字は D-1 の4つ | 9節の Q-C B の決定で、設計に無い `LoginRequest`（`auth.web`）・`PasswordVerification`（`user.service`）の `toString` も伏せ字にする（6つの型）。秘密（パスワード・ハッシュ・トークン・署名鍵）は出力全体で、メールアドレスは伏せ字にした6つの型が出る TRACE の行ごとに確かめる | メールアドレスを出す箇所のうち `UserAccountService#verifyPassword(String, Password)`・`#existsByEmail(String)` の `String` の引数の2つは、Q-C B の決定で後の Intent に回し、`ENTER UserAccountService#verifyPassword` などの行に残るため、出力全体での確かめはできない |
| D-11 | 待ちの確かめの手伝いの置き場 | `auth/testsupport` に移す（NFR 設計の 8.3） | そのとおり `auth/testsupport/H2SessionWaits` に置く。`TestInvitationBarrier` とはまとめない（9節の Q-A の決定） | 招待のテストの手伝いを `auth/testsupport` に依存させると、テストの手伝いの置き場の決まり（`<機能>/testsupport`）をまたぐ。`V8MigrationIT` を消すため、2か所で止まる |

## 9. 依頼者の決定

計画の承認の前に諮った3つの論点について、依頼者が次のとおり決めた。計画の各 Step と8節はこの決定に合わせてある。

**Q-A 待ちの確かめの手伝いを `TestInvitationBarrier` と1つにまとめるか**（基盤の設計の U1 R-02 の申し送り）

- 依頼者の答え: **A（まとめない）**
- 決定: `auth/testsupport/H2SessionWaits` を新しく置き、`invitation/testsupport/TestInvitationBarrier#waitingForLock` は変えない。`V8MigrationIT` を消すため、同じ形の確かめは2か所になる。機能ごとのテストの手伝いの置き場をまたがない（Step 3、D-11）
- 選ばなかった案: B（共通の置き場 `common/testsupport` に置いて1か所にする。NFR 設計の 8.3 と違う置き場になり、招待のテストの手伝いにも手が入る）

**Q-B 伏せ字と停止の漏えいのテストの形**（NFR 設計の 5.1 の申し送り）

- 依頼者の答え: **A（新しい `AuthSuspensionSecretLeakIT`）**
- 決定: 新しい `auth/web/AuthSuspensionSecretLeakIT` を足し、既存の `AuthSecretLeakIT` は変えない（Step 20、D-4）
- 選ばなかった案: B（既存の `AuthSecretLeakIT` を広げる）

**Q-C この決定の外で TRACE にメールアドレスを出す既存の箇所の扱い**（D-10）

対象は4つで、どれも `project.md` の Forbidden「メールアドレスをアプリのログに含めない」に当たる既存の漏えい（TRACE を有効にしたときだけ出る）: `auth.web.LoginRequest#toString`（既存の `WebSecretTypesTest` が「メールアドレスは残す」と確かめている）、`user.service.PasswordVerification`（既定の文字列化でメールアドレスを出す）、`UserAccountService#verifyPassword(String, Password)`・`#existsByEmail(String)` の `String` の引数（`TraceAspect` が文字列に出す）。

- 依頼者の答え: **B**
- 決定: `toString` で直せる2つ（`LoginRequest`・`PasswordVerification`）を B1 で伏せ字にし、既存の `WebSecretTypesTest` の「メールアドレスを残す」という確かめも「メールアドレスを伏せる」に直す（Step 13〜16・20）。`String` の引数の2つ（`verifyPassword(String, …)`・`existsByEmail(String)`。型を `RedactedText` に変える直し）は、残る漏えいとして `code-summary.md` に記録し、後の Intent の候補にする（「Build and Test に引き継ぐこと」）
- 選ばなかった案: A（記録だけにして4つとも後の Intent へ）、C（4つすべてを B1 で直す）
- 両者はどちらも一覧の外のパッケージ（`auth.web`・`user.service`）で、`packagesJudgedByTotal` の扱いは変わらない

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-09-29 の時点で 12 パッケージ。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。利用者の状態を変える操作（利用停止・管理者の印の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理者の印の変更: 印を付けた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い印に頼らない）。印を外す前に出したトークンの扱いと、自分の印を外す操作の扱いは、決めた側の動作を明示したテストにする。★印を外す前に出したトークンの扱い、自分の印を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者の印を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの印を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・管理者フラグなし（403）・管理者（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理者の印や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（印の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01)"
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
  "input_sha256": "sha256:1d19ce9bfd0f0e6eba516940c5111b116a2d58af04709ea78cc47cbad0196f0a",
  "contract_sha256": "sha256:25c76d4e2f9a91edd5d55fbff38287bcce006bace671ccf3da4e81c3a6393f2a"
}
```

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は U1 では新しい骨組みが無いため Step 1、テストの実行の準備は Step 2（最初のテストの Step 4 より前）、データの形と DB の振る舞いは Step 9〜12（ドメインと V9）、DB アクセスは Step 11・12、業務処理は Step 13・14、API（認証の3つの入口）は Step 15〜17、監査と漏えいは Step 19・20、環境とビルドの設定は Step 21〜24、文書と記録は Step 21・25。移行のテストの片付け（Step 3〜6）は、V9 で落ちるテストを先に片付けるため、ドメインの層の前に置く（NFR 設計の Q3 A）。画面（Frontend behavior）の層は U1 に無い。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、全体と、外した `auth.domain`・`auth.repository`・`access.domain`、一覧の外の `auth.service`・`auth.web`・`user.*`・`audit.domain`・`invitation.*` の値をもう一度実測して記録する | Build and Test |
| verify の時間 | V7・V8 の移行のテスト4つを消し、停止の結合テストを足した後の `./gradlew verify` の時間を測り、前の Intent の実測と比べる | Build and Test |
| CI | 依頼者のプッシュの後、CI（`./gradlew verify`）が通ることを確かめる。失敗したら `team.md` の「不安定なテストと CI の失敗」の決まりで扱う | Build and Test（CI の結果の確かめ） |
| `team.md` の一覧の数の記述 | `team.md` の Testing Posture の「2026-09-29 の時点で 12 パッケージ」は、B1 で3つ外すと 9 になる。直すかどうかと直し方（学びの記録の手順）は依頼者が決める。コード生成では `team.md` を書き換えない | Build and Test（依頼者の判断） |
| V9 の事後の裏付け | 配備の後のスモークテストで、既存の初期管理者でログインでき、利用者の読み取り（`/api/me` など）が通ることを記録する（`security-design.md` 8.2、R4） | deployment-pipeline・deployment-execution |
| 戻しの条件（基盤の設計の R-03 の条件つきの受け入れ） | 1つ前の版に戻す前に停止中の利用者がいるかを確かめ、いれば扱い（先に再開する・戻さない など）を依頼者に確かめる手順を必ず決める。README の戻しの節に V9 の注意を足す | deployment-pipeline |
| 性能 | 3つの入口の既存の目標（同時 10 件で p95 1 秒）と、まとめての無効化を含む U3 の止める操作（未無効 100 件・無効 1,000 件の悪い側の条件）を測る | performance-validation |
| 画面の流れ | 停止中の利用者の画面の動き（AC3.2.9・AC3.2.10）は U5 の E2E で確かめる | B5（U5） |
| C1 の約束の呼び出し元の確かめ | 口を呼んだ後に、先に読み込んだエンティティを使わないことの確かめ（`logical-components.md` 3節） | B4（U3 のコード生成の計画） |
| 残る漏えい（2つ） | 9節の Q-C B の決定で後の Intent に回した `UserAccountService#verifyPassword(String, Password)`・`#existsByEmail(String)` の `String` の引数（TRACE を有効にしたときにメールアドレスが出る。直し方は型を `RedactedText` に変える案）。`project.md` の Forbidden との差として記録する | 後の Intent の候補（記録だけ） |
