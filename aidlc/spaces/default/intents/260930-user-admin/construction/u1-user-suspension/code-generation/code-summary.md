# コード生成のまとめ — U1 利用停止の状態と3つの入口（u1-user-suspension）

> 2026-10-02、Step 1〜24 の後に Step 25 の記録として仕上げ、同じ日にコード生成のレビューの R-01・R-02 の直し（依頼者の決定）を足した（2節の終わり・7節の G-10・8節・9節）。コミット・統合は、依頼者の承認を得てから行う（まだ行っていない）。経過の詳しい記録は同じディレクトリの `generation-notes.md`、作った・変えた・消したパスの一覧は `source-manifest.json`、要件との対応は `traceability.json`。

Bolt は B1 利用停止の土台。作業ブランチは `feature/260930-user-admin-b1`（`develop` 782a9f6 から）。パスはリポジトリのルートからの相対パス。

## 1. 作ったもの・変えたもの・消したもの

### 本体（`backend/src/main`）

| パッケージ | ファイル | 新しい・変えた | 中身 |
|---|---|---|---|
| `auth.domain` | `LoginFailureReason`・`TokenFailureReason` | 変えた | `ACCOUNT_SUSPENDED`・`USER_SUSPENDED` を足した |
| `auth.domain` | `AuthenticationEvent`・`AuthenticatedUser` | 変えた | `toString` でメールアドレスを `***` にした |
| `access.domain` | `AccessDeniedReason` | 変えた | `USER_SUSPENDED` は出来事にしない（`Optional.empty()`） |
| `audit.domain` | `AuditFailureReason`・`AuditEventFactory` | 変えた | `ACCOUNT_SUSPENDED` と変換の1行 |
| `user.domain` | `User` | 変えた | 列 `suspended` と読み取りの `isSuspended()`（書き換えのメソッドは無い） |
| `user.repository` | `UserRepository` | 変えた | `updateSuspended(long, boolean)`。`existsByRedactedEmail` の説明文の使い手に初期管理者を足した（R-01 の直し） |
| `auth.repository` | `RefreshTokenRepository` | 変えた | `revokeAllActiveByUserId(long, Instant)` |
| `user.service` | `UserSummary`・`UserAccountService`・`PasswordVerification` | 変えた | 要約の `suspended`、C1 の `isSuspended`・`setSuspended`（MANDATORY）、`PasswordVerification#toString` の伏せ字。R-01 の直しで `verifyPassword` のメールアドレスの引数を `RedactedText` にし、`existsByEmail(String)` を消した |
| `user.service` | `InitialAdminInitializer` | 変えた（R-01 の直し） | 初期管理者がいるかの確かめを `existsByEmail(RedactedText)` に替えた |
| `auth.service` | `RefreshTokenRevocationService`・`RevokeAllResult` | 新しい | C1 の `revokeAllRefreshTokens`（MANDATORY、DEBUG に `userId`・`revoked` だけ） |
| `auth.service` | `LoginService`・`TokenRefreshService`・`LoginCommand` | 変えた | ログインの照合・トークンの更新での停止の判定、`LoginCommand#toString` の伏せ字。R-01 の直しで `verifyPassword` に `RedactedText` で渡す |
| `auth.web` | `AccessTokenAuthenticationProvider` | 変えた | アクセストークンの認証での停止の判定（`USER_SUSPENDED`） |
| `auth.web` | `CurrentUserResponse`・`LoginRequest` | 変えた | `toString` でメールアドレス（と氏名）を伏せた |
| 内部DB | `backend/src/main/resources/db/migration/V9__u1_user_suspension.sql` | 新しい | `ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL;` の1文 |

### テスト（`backend/src/test`）

- 新しい: `auth/testsupport/H2SessionWaits`・`TestUserSuspension`、`auth/service/RefreshTokenRevocationServiceTest`・`RefreshTokenRevocationServiceIT`・`LoginCommandTest`、`auth/web/AccessTokenAuthenticationProviderTest`・`SuspendedUserAuthenticationIT`・`AuthSuspensionSecretLeakIT`、`user/service/UserSuspensionIT`。
- 変えた: `access/domain/AccessDeniedReasonTest`、`access/web/AccessDeniedEventsIT`、`audit/domain/AuditEventFactoryTest`、`audit/service/AuditAuthenticationEventsIT`・`AuditWriteFailureIT`、`auth/domain/SecretTypesTest`・`TokenAuthenticationExceptionTest`、`auth/repository/LoginAttemptStateRepositoryIT`・`RefreshTokenRepositoryIT`、`auth/service/LoginServiceTest`・`LogoutServiceTest`・`TokenRefreshServiceTest`、`auth/web/AccessTokenApiIT`・`LoginApiIT`・`WebSecretTypesTest`、`dslmanage/service/DslLifecycleTest`、`invitation/repository/InvitationSchemaIT`、`invitation/web/InvitationAdminApiIT`、`user/repository/UserRepositoryIT`・`UserSchemaIT`、`user/service/UserAccountServiceTest`、`user/web/MePasswordApiIT`・`MePreferencesApiIT`。R-01 の直しで、さらに `user/service/InitialAdminInitializerTest`（モックの引数）と `invitation/web/InvitationSecretLeakIT`（説明文だけ）を変えた。
- 消した: `user/repository/V7MigrationIT`・`V7BackwardCompatibilityIT`、`invitation/repository/V8MigrationIT`・`V8BackwardCompatibilityIT`、`backend/src/test/resources/db/migration-through-v6/`・`migration-through-v7/`（移行のテストの片付け）。

### 文書とビルドの設定

- `README.md`（V7・V8 の確かめの記述、V9 の行、提供口の表の `AuthenticatedUser`・`TokenFailureReason`、監査の節の停止中のログイン。R-02 の直しで戻しの節に V9 の注意、R-01 の直しで U3 の C2 の記述（`existsByEmail(String)` と `verifyPassword` の据え置き）を直した）。
- `.idea/.gitignore`（`/dataSources.xml`）。
- `backend/build.gradle.kts`（`packagesJudgedByTotal` から `auth.domain`・`auth.repository`・`access.domain` を外した）。

## 2. 実測（Step 1 の基準と Step 23）

`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -i` で包んだ）。

| 項目 | 基準（782a9f6） | Step 23 |
|---|---|---|
| 結果・時間 | 成功・6 分 26 秒 | 成功・6 分 20 秒 |
| 単体 | 1243 | 1282（失敗・飛ばした 0） |
| 結合 | 566 | 587（失敗・飛ばした 0。V7・V8 の 4 クラス 17 件を消し、38 件を足した） |
| 全体のカバレッジ | 行 98.8%・分岐 94.4% | 行 98.8%・分岐 94.5% |

パッケージごとの値（下限は行 80%・分岐 70%）:

| パッケージ | 基準 行・分岐 | Step 23 行・分岐 | 扱い |
|---|---|---|---|
| `auth.domain` | 97.9%・100.0% | 98.0%（98/100）・100.0%（18/18） | 一覧から外した |
| `auth.repository` | 93.9%・50.0% | 100.0%（33/33）・100.0%（2/2） | 一覧から外した（Step 4 のテストで足りない分岐を通した） |
| `access.domain` | 100.0%・100.0% | 100.0%（53/53）・100.0%（29/29） | 一覧から外した |
| `auth.service` | 99.0%・91.7% | 99.1%（221/223）・92.4%（61/66） | 一覧の外（`RefreshTokenRevocationService` 行 100%、`RevokeAllResult` 行・分岐 100%） |
| `auth.web` | 100.0%・95.0% | 100.0%（123/123）・95.5%（21/22） | 一覧の外 |
| `audit.domain` | 99.5%・97.7% | 99.5%（214/215）・97.8%（44/45） | 一覧の外 |
| `user.domain` | 99.5%・97.6% | 99.5%（208/209）・97.6%（121/124） | 一覧の外 |
| `user.repository` | 計数なし | 計数なし（インターフェースだけ） | 一覧の外 |
| `user.service` | 100.0%・95.5% | 100.0%（236/236）・95.6%（86/90） | 一覧の外 |
| `invitation.repository`・`invitation.service`・`invitation.web` | 100.0%・—／100.0%・93.5%／97.3%・87.0% | 変わらず | テストを消したパッケージ。下限を満たす |

- 下限を下回るパッケージは `common.health`（行 79.2%、一覧に残る。手を入れていない）だけ。計測の除外・下限の値は変えていない。`packagesJudgedByTotal` は 12 から 9 になった。
- `backend/config/spotbugs-exclude.xml`・`backend/gradle.lockfile`・`frontend/package-lock.json` に差は無い。
- OSV-Scanner: Step 23 では UP-TO-DATE（前の通過の結果）だったため、統合の前に `--rerun-tasks` で流し直し、通った（2026-10-02。走査したパッケージは backend 252・frontend 382・make-you-chic-ui 404）。
- フロントエンド（Vitest）は変更なしで 91 ファイル・732 件すべて通過。

### R-01・R-02 の直しの後の実測（2026-10-02）

同じコマンド（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`）で流し直した。

| 項目 | Step 23 | 直しの後 |
|---|---|---|
| 結果・時間 | 成功・6 分 20 秒 | 成功・6 分 14 秒（すべての段が通った。`osvScan` は UP-TO-DATE） |
| 単体 | 1282 | 1283（失敗・飛ばした 0。`UserAccountServiceTest` で `existsByEmail(String)` の1件を消し、2件を足した） |
| 結合 | 587 | 587（失敗・飛ばした 0。`AuthSuspensionSecretLeakIT` は1件のまま確かめを強めた） |
| フロントエンド（Vitest） | 91 ファイル・732 件 | 91 ファイル・732 件 |
| 全体のカバレッジ | 行 98.8%（5652/5719）・分岐 94.5%（2069/2190） | 行 98.8%（5653/5720）・分岐 94.5%（2069/2190） |
| `auth.service` | 99.1%（221/223）・92.4%（61/66） | 99.1%（222/224）・92.4%（61/66） |
| `auth.web` | 100.0%（123/123）・95.5%（21/22） | 変わらず |
| `auth.domain`・`auth.repository`・`access.domain` | 98.0%・100.0%／100.0%・100.0%／100.0%・100.0% | 変わらず |
| `user.service` | 100.0%（236/236）・95.6%（86/90） | 100.0%（236/236）・95.6%（86/90） |
| `user.domain` | 99.5%（208/209）・97.6%（121/124） | 変わらず |
| `user.web` | （記録なし） | 96.5%（55/57）・84.6%（11/13） |
| `user.repository` | 計数なし | 計数なし（インターフェースだけ） |
| `audit.domain`・`invitation.*` | 2節のとおり | 変わらず |

- `user.config` というパッケージは無い。手を入れたパッケージは、すべて行 80%・分岐 70% を満たす。
- OSV-Scanner は `./gradlew osvScan --rerun-tasks` で流し直し、通った（失敗の条件に当たるもの 0 件、警告 14 件。警告はすべて `vendor/make-you-chic-ui/package-lock.json` の開発用の npm の部品）。
- `backend/config/spotbugs-exclude.xml`・`backend/gradle.lockfile`・`frontend/package-lock.json` に差は無い。

## 3. 移行のテストの片付け

### `V8MigrationIT` の (a)〜(d) の行き先（NFR 設計の Q2 A）

| 消した確かめ | 行き先 |
|---|---|
| (a) 同じメールアドレスの2件目の招待中は一意の違反 | `InvitationSchemaIT#stateChangesAndUniqueness`（SQLState 23505 と制約の名前の確かめを足して強めた） |
| (b) 招待中でない行は重ねられ、知らない状態は CHECK で拒否 | `InvitationSchemaIT#endedRowsMayRepeat`（新しい） |
| (c) 取り消した後に同じメールアドレスで招待中を作れる | `InvitationSchemaIT#stateChangesAndUniqueness`（確かめ済み） |
| (d) 同時の追記の待ちと一意の違反 | `InvitationConcurrencyIT` の `two simultaneous invitations to the same email create one, the other is AlreadyPending with its id`（移さない） |

### 待ちの確かめの2か所（Q-A A）

`V8MigrationIT` を消した後、同じ形の待ちの確かめは `auth/testsupport/H2SessionWaits`（接続の URL・絞り込み・上限・待つ側の終わりの判定を引数で受ける）と `invitation/testsupport/TestInvitationBarrier#waitingForLock`（変えていない）の2か所になる。招待のテストの手伝いを `auth/testsupport` に依存させると、テストの手伝いの置き場の決まりをまたぐため、まとめない。

## 4. AC3.2.6 の回帰テスト（Step 18）

`InvitationAdminApiIT#suspendedUserEmailIsRegistered` を1件足した（停止中の利用者のメールアドレスへの招待は 409 `INVITATION_EMAIL_REGISTERED`、招待の行とメールが増えない）。既存の登録済みの拒否の確かめ（`conflicts`）は変えていない。

## 5. E2E（Step 24）

`caffeinate -i ./gradlew e2eTest`: 成功 110・失敗 0・飛ばした 0・不安定 0（10 ファイルすべて成功。ファイルごとの件数は `generation-notes.md` の Step 24）。結果を記録した後、`frontend/test-results/` と `frontend/playwright-report/` を消した（ファイル 3 件）。報告は誰にも共有していない。Mailpit はこのセッションの前から動いていたため、起動も停止もしていない。

R-01 の直し（ログインの照合の経路に手を入れた）の後に、Mailpit が動いている（`/api/v1/info` が 200）ことを確かめて流し直した: 成功 110・失敗 0・飛ばした 0・不安定 0（225 秒、10 ファイルすべて成功、ファイルごとの件数は Step 24 と同じ）。結果を記録した後、`frontend/test-results/`・`frontend/playwright-report/` を消した（ファイル 3 件、ディレクトリ 106 個）。報告は誰にも共有していない。


## 6. 主な実装の決定

- **判定の置き場**: 3つの入口は、すでに読んでいる利用者の要約（`UserSummary#suspended`）を見るだけで、内部DB の問い合わせを増やさない（`SuspendedUserAuthenticationIT#noExtraQueries`・`LoginApiIT` で回数を確かめた）。
  - ログインの照合（`LoginService#decide`）: 本人の行を排他つきで読んだ直後、`LockPolicy.decide` の前に判定し、読んだ値のまま `update` を1回、`LOGIN_FAILED`・`ACCOUNT_SUSPENDED` の出来事を1件出して失敗で終える。停止中の試みは失敗回数に数えない。パスワードの誤りと同じ照合・文の並び・応答になる。判定のトランザクションで `users` を読み直さない（R-03、BR5.5）。
  - トークンの更新（`TokenRefreshService#refresh`）: `findById` の直後に `REFRESH_FAILED` を投げ、例外で `revokeIfActive` も巻き戻す。監査は出さない。
  - アクセストークンの認証（`AccessTokenAuthenticationProvider`）: 区分 `USER_SUSPENDED` の `TokenAuthenticationException`。応答は既存の 401 `AUTHENTICATION_REQUIRED`、アクセスの拒否の出来事は出さない（`AccessDeniedReason#of` が `Optional.empty()`）。
- **C1 の口**: `UserAccountService#isSuspended`（読み取りだけ）・`#setSuspended`（MANDATORY、0 行なら `IllegalStateException`）、`RefreshTokenRevocationService#revokeAllRefreshTokens`（MANDATORY、注入した `Clock`、`RevokeAllResult(int revoked)`、DEBUG に `userId`・`revoked` だけ）。`User` に書き換えのメソッドは作らず、停止の列だけの更新の問い合わせ（`@Modifying(clearAutomatically = true, flushAutomatically = true)`）で書くため、同じトランザクションで先に読み込んだ後でも、後の読み取りは書いた値を返す（R-05）。
- **V9**: `ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL;` の1文。確かめは起動時の `validate-on-migrate`・`ddl-auto: validate` とエンティティの読み書きだけ（Q1 B・Q4 A）。
- **監査の形**: 停止中のログインの監査の行は、既存のログインの失敗と同じ形（理由 `ACCOUNT_SUSPENDED`・結果 FAILURE・入れたメールアドレスあり・操作した人と対象の利用者は空）。`AuditEventFactory#from` の写し方は変えていない（G-1）。
- **伏せ字**: `AuthenticationEvent`・`AuthenticatedUser`・`LoginCommand`・`CurrentUserResponse`・`LoginRequest`・`PasswordVerification` の6つの型の `toString` で、メールアドレス（と氏名）を `***` にした（NFR 設計の承認の場の決定と Q-C B）。JSON の受け取り・応答の項目は変えていない。
- **テストの手伝い**: `auth/testsupport/TestUserSuspension`（停止の状態は C1 の口だけで入れ、JDBC で `users` を書き換えない）と `auth/testsupport/H2SessionWaits`（待ちの確かめ）。
- **構造**: 新しい機能のパッケージは作らず、既存の ArchUnit のテスト（`ArchitectureTest` と機能ごとの境界テスト9つ）を変えずに通した。

## 7. 計画・承認済みの文書との差

計画 8節の D-1〜D-11（承認済みの計画に書いた差）に加えて、生成の中で次の差が出た。すべて依頼者が受け入れた（2026-10-02）。

| ID | 差 | 扱い |
|---|---|---|
| G-1 | 計画 Step 10 は「操作した人に利用者 ID」。実装は停止中のログインの監査の行の操作した人・対象の利用者を空のまま（`AuditEventFactory` を変えない） | 依頼者の決定で受け入れ。承認済みの設計（NFR 設計の R-03、`security-design.md` 5.1・6節）と既存のログインの失敗にそろえた |
| G-2 | `TestUserSuspension#suspend` が無効にした件数を返す | 受け入れ |
| G-3 | `TokenRefreshServiceTest` に「要約が無い（利用者が消えた）」の1件を足した | 受け入れ |
| G-4 | `RefreshTokenRevocationServiceTest` に「INFO の既定では何も出ない」を足した（6件） | 受け入れ |
| G-5 | `AccessTokenAuthenticationProviderTest` を計画の 5 件から 7 件にした（停止中の管理者、検証の失敗では読まない） | 受け入れ |
| G-6 | 計画 Step 17 の「トークンと Cookie が無い」を、「新しいリフレッシュトークンを渡さない（更新の失敗は今までどおり Cookie を消す指示だけを返す）」として確かめた。実装は変えていない | 受け入れ |
| G-7 | `/api/admin/check` の成功は 204（既存の作り）。テストは 204 を前提にした | 受け入れ |
| G-8 | `MePreferencesApiIT`・`MePasswordApiIT` を計画の 1 件ずつから 2 件ずつにした（通る場合と停止中の場合） | 受け入れ |
| G-9 | `AuthSuspensionSecretLeakIT` で、残る漏えいを件数の記録だけでなく「メールアドレスが出る行は `verifyPassword`・`existsByEmail` の ENTER の行に限る」の確かめにした | 受け入れ。G-10 で置き換えた |
| G-10 | 計画 9節の Q-C B（`verifyPassword(String, …)`・`existsByEmail(String)` は後の Intent へ）と D-10・Step 20 の「メールアドレスが出力全体に無いことは確かめない」から変えた。コード生成のレビューの R-01 を受けた依頼者の決定で、B1 の中で `verifyPassword` のメールアドレスの引数を `RedactedText` にし（`LoginService` は `new RedactedText(command.email())` で渡す）、`existsByEmail(String)` を消して `InitialAdminInitializer` を `existsByEmail(RedactedText)` に替えた。ほかの呼び出しは無いことを検索で確かめた。`AuthSuspensionSecretLeakIT` は、2つの口の TRACE の行が出て `***` を含むことと、起動（初期管理者の作成の確かめ）から最後の要求までの出力（標準出力と標準エラー）のどの行にも、利用者と初期管理者のメールアドレスが無いことを確かめる形に強めた。ほかに出る行は無かった。`UserAccountServiceTest` に、`verifyPassword` が null を拒むことと、2つの口がメールアドレスを `String` で受けないこと（リフレクション）の2件を足した | 依頼者の決定（2026-10-02）。計画の本文は書き換えず、差をここに記録する |

`traceability.json` の Deferred は6つ: AC3.2.9・AC3.2.10（画面の動き。U5 の E2E、M9 A）、NFR5.2・NFR5.4（performance-validation）、NFR10.1・NFR10.2（計画 8節の D-2。依頼者の決定 Q1 B・Q4 A で移行・後方互換の自動のテストと戻しの練習を置かず、事後の裏付けは deployment-execution）。

## 8. 依頼者の決定

| 決定 | 中身 |
|---|---|
| Q-A A（計画の承認の前） | 待ちの確かめの手伝いを `TestInvitationBarrier` とまとめない（3節） |
| Q-B A（計画の承認の前） | 新しい `AuthSuspensionSecretLeakIT` を足し、既存の `AuthSecretLeakIT` は変えない |
| Q-C B（計画の承認の前） | `LoginRequest`・`PasswordVerification` の `toString` も B1 で伏せ字にし、`WebSecretTypesTest` の確かめを「メールアドレスも伏せる」に直す。`String` の引数の2つは後の Intent へ（9節）。後にレビューの R-01 への決定で、この2つも B1 で直すことに変えた（7節の G-10） |
| Jackson・Gitleaks（Step 1） | 変更の前の基準の OSV-Scanner が Jackson 3.1.6 の High 4 件（GHSA-7hhh-6rmp-j9qf・GHSA-p6pp-m3f8-5c89・GHSA-cxp5-3px4-pw24・GHSA-wv8q-qhhj-9h54）で失敗したため、B1 の前に別のブランチで Jackson の BOM を 3.1.7 に上げ、あわせて監査ログの Gitleaks の誤検知（レビューの記録のディレクトリの名前）を `.gitleaks.toml` でパスと値の形の両方で絞って除外し、`develop` に統合した（f299400、記録 782a9f6）。作業ブランチは 782a9f6 まで fast-forward した。この2つは B1 の squash に含まない |
| 基準の値（Step 1） | カバレッジの基準は 1 回目（f226bc6）の実測を使う（782a9f6 との差は依存の版と `.gitleaks.toml` だけ） |
| G-1（Step 14 の後） | 停止中のログインの監査の行の操作した人・対象の利用者は空のまま |
| G-2〜G-9 | 受け入れ（7節） |
| レビュー R-01（コード生成のレビュー、Major） | B1 で直す（計画の Q-C B からの変更）。`verifyPassword` の引数を `RedactedText` にし、`existsByEmail(String)` を消す（7節の G-10、9節） |
| レビュー R-02（Major） | README の戻しの節に、V9 の後に1つ前の版へ戻すと停止の判定が効かなくなること（停止中の利用者が3つの入口で受け付けられる）の注意を足す。手順は deployment-pipeline で決める |
| レビュー R-03（Minor） | B4（U3）のコード生成の計画で、C1 の口を呼んだ後に先に読み込んだエンティティを使わないことの確かめを必須の項目にする（11節） |
| レビュー R-04（Minor） | `team.md` の「2026-09-29 の時点で 12 パッケージ」は日付つきの記述のため書き換えない |
| レビュー R-05（Minor） | `develop` への統合は squash の1コミットのため、区切りのコミットはそのまま（区切りごとの verify は流さない） |
| Step 1 の最後のチェックボックス | 付ける（B1 の共通の完了の条件は Step 23・24 で満たした） |

## 9. 残る漏えい（無くなった）

計画 9節の Q-C B では、`UserAccountService#verifyPassword(String, Password)`・`#existsByEmail(String)` の `String` の引数（TRACE を有効にするとメールアドレスをそのまま出す。`project.md` の Forbidden「メールアドレスをアプリのログに含めない」との差）を後の Intent に回し、`AuthSuspensionSecretLeakIT` は出る行をこの2つの ENTER の行に限る形にしていた（直しの前の実測では `ENTER UserAccountService#verifyPassword(...)` の 3 行、管理者のログインを合わせて 4 行）。

コード生成のレビューの R-01 を受けた依頼者の決定で、B1 の中で2つの口を `RedactedText` で受ける形にした（7節の G-10）。直しの後、`AuthSuspensionSecretLeakIT` は起動から最後の要求までの出力のどの行にも利用者と初期管理者のメールアドレスが無いことを確かめて通った。この単位に残る漏えいは無い（ログインの照合の経路の DB アクセスの `UserRepository#findByEmail(String)` は、追跡の行にメールアドレスを出さないことを同じテストの出力の全体の確かめで確かめた）。

## 10. 承認の場で確かめること

- 6つの Deferred（7節の終わり）の持ち主と扱い。
- レビューの R-01・R-02 の直し（7節の G-10、2節の終わりの実測、README の戻しの節の V9 の注意）。
- `team.md` の Testing Posture の「2026-09-29 の時点で 12 パッケージ」は、B1 で3つ外して 9 になった。依頼者の決定（レビューの R-04）で、日付つきの記述のため書き換えない。

## 11. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」の表のとおり。要点:

| 項目 | 内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | 統合の後の `develop` で `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、2節と同じパッケージの値を測り直す | Build and Test |
| verify の時間 | V7・V8 のテストを消し停止のテストを足した後の時間（Step 23 は 6 分 20 秒、基準 6 分 26 秒）を前の Intent の実測と比べる | Build and Test |
| CI | 依頼者のプッシュの後、CI が通ることを確かめる | Build and Test |
| `team.md` の一覧の数 | 12 → 9。依頼者の決定（R-04）で書き換えない（日付つきの記述のため） | — |
| V9 の事後の裏付け | 配備の後のスモークテストで、既存の初期管理者でログインでき `/api/me` などが通ること（NFR10.1・NFR10.2 の Deferred） | deployment-pipeline・deployment-execution |
| 戻しの条件 | 1つ前の版に戻す前に停止中の利用者を確かめる手順を決める（README の戻しの節の V9 の注意は、R-02 の直しで B1 で足した。手順は配備の段で決めると書いた） | deployment-pipeline |
| 性能 | 3つの入口の既存の目標と、まとめての無効化を含む U3 の止める操作（未無効 100 件・無効 1,000 件） | performance-validation |
| 画面の流れ | AC3.2.9・AC3.2.10 | B5（U5 の E2E） |
| C1 の約束の呼び出し元 | 口を呼んだ後に先に読み込んだエンティティを使わないことの確かめを、B4 のコード生成の計画の必須の項目にする（レビューの R-03 への依頼者の決定） | B4（U3 のコード生成の計画） |

## 12. コミットの区切りの案（依頼者の承認を得てから行う）

作業ブランチ `feature/260930-user-admin-b1` の上で、計画 3節の区切りでコミットする。パスは `backend/src/main/java/cherry/mastersmith/` を「本体」、`backend/src/test/java/cherry/mastersmith/` を「テスト」と略す。ワークフローの記録（`aidlc/` の下）は含めず、別の記録のコミットにする。

### C1 移行のテストの片付け

- 件名の案: `B1: V7・V8 の移行のテストを片付け、待ちの確かめの手伝いと招待の表の確かめを移す`
- 新しい: テスト `auth/testsupport/H2SessionWaits.java`
- 変えた: テスト `auth/repository/LoginAttemptStateRepositoryIT.java`・`invitation/repository/InvitationSchemaIT.java`
- 消した: テスト `user/repository/V7MigrationIT.java`・`user/repository/V7BackwardCompatibilityIT.java`・`invitation/repository/V8MigrationIT.java`・`invitation/repository/V8BackwardCompatibilityIT.java`、`backend/src/test/resources/db/migration-through-v6/`（6 ファイル）・`backend/src/test/resources/db/migration-through-v7/`（7 ファイル）

### C2 停止の区分・監査の理由・伏せ字（ドメイン）

- 件名の案: `B1: 利用停止の区分と監査の理由を足し、認証の出来事と主体の文字列化でメールアドレスを伏せる`
- 変えた: 本体 `auth/domain/LoginFailureReason.java`・`auth/domain/TokenFailureReason.java`・`auth/domain/AuthenticationEvent.java`・`auth/domain/AuthenticatedUser.java`・`access/domain/AccessDeniedReason.java`・`audit/domain/AuditFailureReason.java`・`audit/domain/AuditEventFactory.java`、テスト `auth/domain/SecretTypesTest.java`・`auth/domain/TokenAuthenticationExceptionTest.java`・`access/domain/AccessDeniedReasonTest.java`・`audit/domain/AuditEventFactoryTest.java`

### C3 V9 と DB アクセス

- 件名の案: `B1: V9 で users.suspended を足し、停止の列の更新とリフレッシュトークンのまとめての無効化の問い合わせを足す`
- 新しい: `backend/src/main/resources/db/migration/V9__u1_user_suspension.sql`
- 変えた: 本体 `user/domain/User.java`・`user/repository/UserRepository.java`（R-01 の直しの説明文を含む）・`auth/repository/RefreshTokenRepository.java`、テスト `user/repository/UserSchemaIT.java`・`user/repository/UserRepositoryIT.java`・`auth/repository/RefreshTokenRepositoryIT.java`

### C4 業務処理

- 件名の案: `B1: 停止の口とまとめての無効化を足し、ログインの照合とトークンの更新で停止中を拒否する`
- 新しい: 本体 `auth/service/RefreshTokenRevocationService.java`・`auth/service/RevokeAllResult.java`、テスト `auth/testsupport/TestUserSuspension.java`・`auth/service/RefreshTokenRevocationServiceTest.java`・`auth/service/RefreshTokenRevocationServiceIT.java`・`auth/service/LoginCommandTest.java`・`user/service/UserSuspensionIT.java`
- 変えた: 本体 `user/service/UserSummary.java`・`user/service/UserAccountService.java`・`user/service/PasswordVerification.java`・`user/service/InitialAdminInitializer.java`・`auth/service/LoginService.java`・`auth/service/TokenRefreshService.java`・`auth/service/LoginCommand.java`、テスト `user/service/UserAccountServiceTest.java`・`user/service/InitialAdminInitializerTest.java`・`auth/service/LoginServiceTest.java`・`auth/service/TokenRefreshServiceTest.java`・`auth/service/LogoutServiceTest.java`・`dslmanage/service/DslLifecycleTest.java`
- R-01 の直し（`verifyPassword`・`existsByEmail` を `RedactedText` で受ける形、`InitialAdminInitializer` の呼び出しの替え）はこの区切りに入れる

### C5 3つの入口・監査・漏えいのテスト

- 件名の案: `B1: アクセストークンの認証で停止中を拒否し、3つの入口・監査・漏えいの結合テストを足す`
- 新しい: テスト `auth/web/AccessTokenAuthenticationProviderTest.java`・`auth/web/SuspendedUserAuthenticationIT.java`・`auth/web/AuthSuspensionSecretLeakIT.java`
- 変えた: 本体 `auth/web/AccessTokenAuthenticationProvider.java`・`auth/web/CurrentUserResponse.java`・`auth/web/LoginRequest.java`、テスト `auth/web/WebSecretTypesTest.java`・`auth/web/LoginApiIT.java`・`auth/web/AccessTokenApiIT.java`・`access/web/AccessDeniedEventsIT.java`・`audit/service/AuditAuthenticationEventsIT.java`・`audit/service/AuditWriteFailureIT.java`・`user/web/MePreferencesApiIT.java`・`user/web/MePasswordApiIT.java`・`invitation/web/InvitationAdminApiIT.java`・`invitation/web/InvitationSecretLeakIT.java`（説明文だけ）
- R-01 の直しの `AuthSuspensionSecretLeakIT` の確かめの強化はこの区切りに入れる（C4 の直しに頼る）

### C6 文書とビルドの設定

- 件名の案: `B1: README に V9 と停止の扱いを書き、カバレッジの一覧から3つのパッケージを外す`
- 変えた: `README.md`・`.idea/.gitignore`・`backend/build.gradle.kts`
- R-02 の直し（README の戻しの節の V9 の注意）と、R-01 の直しに合わせた README の U3 の C2 の記述の直しはこの区切りに入れる

各コミットの中身は、C2〜C5 の順で前の区切りに頼る（C4 は C3 の問い合わせ、C5 は C4 の判定と手伝いを使う）。途中のコミットで verify を流し直すことはしておらず、通ることを確かめたのは C1〜C6 をすべて含む作業フォルダ（Step 23・24 と、R-01・R-02 の直しの後の verify・E2E）である。依頼者の決定（レビューの R-05）で、`develop` へは squash の1コミットで統合し、区切りのコミットは `develop` に残さない。

### `develop` への squash の統合の案

- 件名の案: `B1 利用停止の土台（U1）: 停止の状態と3つの入口の判定・まとめての無効化・V9（移行のテストの片付けを含む）`
- 本文の案:

```text
Intent 260930-user-admin の Bolt B1（U1 u1-user-suspension）。移行のテストの片付けを含む。

- 利用停止の状態（users.suspended、V9 の前進のみの変更）と、C1 の口
  （UserAccountService#isSuspended・#setSuspended、
  RefreshTokenRevocationService#revokeAllRefreshTokens）を足した
- ログインの照合・トークンの更新・アクセストークンの認証の3つの入口で、
  停止中の利用者を既存の失敗と同じ応答で拒否する（停止中のログインは
  失敗回数に数えず、監査に LOGIN_FAILED・ACCOUNT_SUSPENDED を残す）
- 認証の出来事・主体・ログインの要求と応答・照合の結果の文字列化で
  メールアドレスを伏せ、照合と初期管理者の確かめの口はメールアドレスを
  伏せ字の型（RedactedText）で受ける形にした（existsByEmail(String) を消した）
- 移行のテストの片付け: V7・V8 の移行と後方互換のテスト4つと複写の置き場を
  消し、待ちの確かめの手伝いを auth/testsupport/H2SessionWaits に移し、
  招待の表の確かめを InvitationSchemaIT に移した
- packagesJudgedByTotal から auth.domain・auth.repository・access.domain を外した
- README に V9 と停止の扱い（戻している間は停止が効かない注意を含む）、
  .idea/.gitignore に dataSources.xml を足した

統合の前に ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
（単体 1283・結合 587）と ./gradlew e2eTest（110 件）が通った。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

統合の後、依頼者の承認を得て作業ブランチを消す。`origin` へのプッシュは依頼者が行う。
