# コード生成のまとめ — U3 利用者の管理の API（u3-user-admin-api）

U3 の最終の版（B3 と B4 を合わせたもの）。B3（一覧と氏名・言語の変更、計画の Step 1〜16）は `develop` に統合済み（squash f6bf385、記録 c13e817）。B4（5つの操作と最後の管理者の保護、Step 17〜43）は作業ブランチ `feature/260930-user-admin-b4` の上でコミットせずにあり、この版の時点で Step 41（関門）・Step 42（E2E）が通った。コミット・統合はまだしていない（8節の区切りで依頼者の承認を得て行う）。

パスはリポジトリのルートからの相対パス。経過の詳しい記録は同じディレクトリの `generation-notes.md`（G-1〜G-50）。ファイルの一覧は `source-manifest.json`（123 件。本体 62・テスト 57・ほか 4）。

## 1. 作ったもの・変えたもの

### 本体（`backend/src/main`）

| パッケージ | 部品 | Bolt | 新しい・手を入れた |
|---|---|---|---|
| `user.domain` | `SearchText`・`ProfileUpdate`・`ProfileValidation` | B3 | 新しい |
| `user.repository` | `UserAdminRow`（投影、Q-A）、`UserRepository` に一覧・検索・件数・`updateProfile`（B3）と `findAdminRow`・`findActiveAdminIds`・`updateAdminFlag`（B4）、`UserRowLockRepository`（EntityManager の排他の口、B4） | B3・B4 | 新しい・手を入れた（既存のメソッドの本文は変えていない） |
| `user.service` | `UserAdminSummary`・`UserAdminSlice`・`ProfileCommand`・`ProfileUpdateResult`、`UserAccountService#findAdminPage`・`#updateProfile`（B3）、`AdminRowsLock`・`UserRowLock`、`#lockAdminRowsInIdOrder`・`#lockUserRow`・`#findAdminSummary`・`#setAdmin`（B4） | B3・B4 | 新しい・手を入れた |
| `auth.domain` | `LockView` | B3 | 新しい |
| `auth.repository` | `LoginAttemptStateRepository#findBySubjectIds`（B3）、`#lockForUpdate` の上限切れの受け（E1）と `#tryLockForUpdate`（B4） | B3・B4 | 手を入れた |
| `auth.service` | `LockAdministrationService#lockViewsOf`（B3）、`#prepareFailureReset`・`#completeFailureReset`・`LoginFailureResetPreparation`、待ち合わせの口 `LoginAttemptBarrier`・`NoOpLoginAttemptBarrier`、`LoginService` の口の呼び出し（B4） | B3・B4 | 新しい・手を入れた |
| `useradmin`・`useradmin.domain` | `UserAdminProblemTypes`（404 `USER_NOT_FOUND`、B4 で 409 の5つと `of(RejectionReason)`）、`AdminOperation`・`RejectionReason`・`OperationFacts`・`RejectionPolicy`・`UserAdminAuditFailure`・`UserAdminAuditEvent`（B4） | B3・B4 | 新しい |
| `useradmin.service` | `UserAdminService`（一覧・氏名と言語、B4 で5つの操作）・`UserAdminListResult`・`UserAdminPage`・`UserAdminEntry`・`UserAdminProblemTypeCatalog`、`OperationResult`・`UserAdminBarrier`・`NoOpUserAdminBarrier`（B4） | B3・B4 | 新しい |
| `useradmin.web` | `UserAdminController`（GET 一覧・PUT 氏名と言語、B4 で5つの POST）・`AdminUser`・`AdminUserPage`・`ProfileRequest`・`SearchTextConverter`・`UserAdminWebConfig`・`UserAdminRequestContextResolver`・`UserAdminFieldErrors` | B3・B4 | 新しい |
| `common.persistence` | `RowLockFailures`・`RowLockUnavailableException`・`RowLockAttempt` | B4 | 新しい（パッケージも新しい） |
| `common.observability` | `LockFailureSafeTraceInterceptor`、`TraceAspect#createInterceptor` | B4 | 新しい・手を入れた（一覧から外した） |
| `common.error.web` | `GlobalExceptionHandler` の想定外の誤りの ERROR（排他の失敗では原因をつながず `exceptionClass`） | B4 | 手を入れた（一覧から外した） |
| `invitation.lock` | `InvitationLockQueries`・`InvitationLockQueriesImpl`（E2〜E4、Spring Data の独自の断片。案 1′、G-23） | B4 | 新しい（パッケージも新しい） |
| `invitation.repository` | `InvitationRepository`（断片を継ぎ、3つの `@Lock` の宣言を消した。口の名前・引数・戻り値は同じ） | B4 | 手を入れた |
| `audit.domain` | `AuditEventType`（5つ）・`AuditFailureReason`（4つ）・`AuditEventFactory#from(UserAdminAuditEvent)` | B4 | 手を入れた |
| `audit.service` | `AuditEventListener#onUserAdminAuditEvent` | B4 | 手を入れた |

手を入れていない: `access.*`（`access.domain.AccessProblemTypes` は使うだけ）・`common.security`・`config`・`common.web`・`common.error.domain`・`common.error.service`・`auth.web`・`frontend/`・`application.yaml`・`logback-spring.xml`・`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`.env.example`・`docker/monitoring/`・`backend/src/main/resources/db`。新しい依存・設定の項目・移行・指標・警報は足していない。排他の待ちの上限は既存の 3000 ミリ秒の定数のまま。

### ビルド・文書・負荷の試験

- `backend/build.gradle.kts`: `packagesJudgedByTotal` から `common.error.web`・`common.observability` を消し（9 → 7）、説明文に B4（U3）で外したことを足した。計測の除外は増やしていない。
- `README.md`: 「利用者の管理の API（Intent 260930-user-admin の U3）」の節（B3 で一覧と氏名・言語、B4 で5つの操作・拒否の順・戻すものが無いとき・最後の有効な管理者の保護・印の変更の効き方）、「監査ログ（U4）」に種類5つと理由6つ・残らない場合・記録の失敗、「既知の制約（同時の要求と接続プール）」に1件に2本と上限 30 を超えうること。
- `perf/k6/scenarios.js`（`userAdminList`・`userAdminProfile`・`userAdminOps`・`userAdminSuspendWorst`・`userAdminPool`）と `perf/README.md`（「利用者の管理の場面」）。`k6 inspect --include-system-env-vars` で読み込みまで確かめた（測定はしていない）。

### テスト（`backend/src/test`）

| 置き場 | テスト（件数は最後に名指しで流した時点） | Bolt |
|---|---|---|
| `user/domain` | `SearchTextTest`（10、性質 2）・`ProfileValidationTest`（8） | B3 |
| `auth/domain` | `LockViewTest`（9、性質 1） | B3 |
| `useradmin/domain` | `UserAdminProblemTypesTest`（3）・`RejectionPolicyTest`（15、性質 3）・`UserAdminAuditEventTest`（8） | B3・B4 |
| `audit/domain` | `AuditEventFactoryTest`（50、足した 13） | B4 |
| `common/persistence` | `RowLockFailuresTest`（10）・`RowLockUnavailableExceptionTest`（3） | B4 |
| `common/observability`・`common/error/web` | `LockFailureSafeTraceInterceptorTest`（6）・`GlobalExceptionHandlerTest`（9、足した 2） | B4 |
| `auth/repository` | `LoginAttemptStateRepositoryTest`（10）・`LoginAttemptStateRepositoryIT`（13） | B3・B4 |
| `invitation/lock`・`invitation/repository` | `InvitationLockQueriesImplTest`（23）・`InvitationRepositoryIT`（6） | B4 |
| `user/repository` | `UserAdminQueriesIT`（12）・`UserRowLockRepositoryTest`（9）・`UserRowLockRepositoryIT`（5） | B3・B4 |
| `user/service` | `UserAccountServiceTest`（34）・`UserAdminAccountIT`（3）・`UserAdminLockPortsIT`（3） | B3・B4 |
| `auth/service` | `LockAdministrationServiceTest`（10）・`LoginServiceTest`（20、足した 2）・`FailureResetPortIT`（3） | B3・B4 |
| `audit/service` | `AuditEventListenerTest`（21、足した 2）・`UserAdminAuditIT`（4）・`UserAdminAuditWriteFailureIT`（4） | B4 |
| `useradmin/service` | `UserAdminServiceTest`（34）・`UserAdminProblemTypeCatalogTest`（1）・`UserAdminOperationsIT`（5）・`UserAdminConcurrencyIT`（7）・`ResetLoginConcurrencyIT`（2）・`SuspendWhileLoginIT`（1） | B3・B4 |
| `useradmin/web` | `SearchTextConverterTest`（3）・`UserAdminWebTypesTest`（3）・`UserAdminRequestContextResolverTest`（5）・`UserAdminListApiIT`（8）・`UserAdminListQueryCountIT`（3）・`UserAdminProfileApiIT`（6）・`UserAdminOperationsApiIT`（6）・`UserAdminResetLoginFailuresApiIT`（5）・`UserAdminBusyApiIT`（5）・`UserAdminMassAssignmentIT`（3）・`UserAdminSecretLeakIT`（4） | B3・B4 |
| 既存の経路の漏えい | `auth/web/AuthLockTimeoutLeakIT`（2）・`invitation/web/InvitationLockTimeoutLeakIT`（2）・`user/web/MePreferencesLockTimeoutLeakIT`（2）（どれも TRACE と INFO） | B4 |
| `useradmin` | `UserAdminBoundaryArchitectureTest`（8） | B3・B4 |
| 手伝い | `useradmin/testsupport` の `UserAdminApi`・`UserAdminFixtures`・`RecordingTransactionManager`・`TestUserAdminBarrier`、`auth/testsupport/TestLoginAttemptBarrier`、`common/testsupport/RowLockHolder`（G-16）、`auth/testsupport/SqlStatementCounter` に `recorded()`（G-2） | B3・B4 |
| 待ち合わせの直し（C4b） | `invitation/testsupport/TestInvitationBarrier`（自分の問い合わせを数えない形）・`invitation/service/RegistrationConcurrencyIT`（続けた時点で後の操作が待ちに入っていたことの確かめ） | B4 |

既存の `ArchitectureTest` と機能ごとの境界テストは変えていない。既存のテストの期待を変えたのは `LoginAttemptStateRepositoryIT#lockTimeout`（G-17、依頼者の了承済み）と、B3 の `UserAdminServiceTest#noAuditEvents`（G-29、予告どおり）だけ。

## 2. 実測

どれも colima の設定（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）を渡し、`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` で測った。Step 17 は実測し直さず B3 の Step 15 を B4 の基準とした（`develop` のアプリのソースが Step 15 の作業ブランチと同じため）。

| 項目 | Step 1（変更の前） | Step 15（B3 の関門 = B4 の基準） | Step 41（B4 の関門） | Step 41 − Step 15 |
|---|---|---|---|---|
| 結果 | BUILD SUCCESSFUL | BUILD SUCCESSFUL | BUILD SUCCESSFUL（1回目で通過） | — |
| 時間（壁時計） | 6 分 23 秒（383 秒） | 6 分 37 秒（397 秒） | 8 分 55 秒（535 秒） | +2 分 18 秒 |
| 単体（`test`） | 1,287 件 | 1,355 件 | 1,503 件（失敗・誤り・飛ばし 0） | +148 |
| 結合（`integrationTest`） | 587 件 | 620 件 | 688 件（失敗・誤り・飛ばし 0。対象DB のテストも SKIPPED なし） | +68 |
| 画面（Vitest） | 95 ファイル・801 件 | 同じ | 同じ | 0 |
| 全体のカバレッジ | 行 98.8%（5652/5719）・分岐 94.5%（2069/2190） | 行 98.9%（5883/5951）・分岐 94.6%（2156/2280） | 行 98.7%（6257/6338）・分岐 94.8%（2326/2454） | — |

パッケージごと（Step 41 の `jacocoTestReport.xml`。括弧の中は Step 15 の値）:

| パッケージ | 行 | 分岐 | 下限の判定 |
|---|---|---|---|
| `useradmin.web` | 98.8%（83/84）（100.0%） | 93.8%（15/16）（100.0%） | パッケージごと（新しい） |
| `useradmin.service` | 99.3%（133/134）（100.0%） | 95.6%（43/45）（100.0%） | パッケージごと（新しい） |
| `useradmin.domain` | 100.0%（83/83）（100.0%） | 100.0%（52/52）（分岐なし） | パッケージごと（新しい） |
| `common.persistence` | 100.0%（26/26） | 100.0%（20/20） | パッケージごと（新しい） |
| `invitation.lock` | 100.0%（21/21） | 100.0%（2/2） | パッケージごと（新しい） |
| `common.observability` | 97.7%（126/129）（97.5%） | 95.5%（42/44）（94.4%） | パッケージごと（B4 で一覧から外した） |
| `common.error.web` | 97.0%（196/202）（96.9%） | 88.9%（88/99）（88.2%） | パッケージごと（B4 で一覧から外した） |
| `user.domain` | 99.6%（238/239） | 97.8%（135/138） | パッケージごと |
| `user.repository` | 100.0%（28/28）（100.0%） | 100.0%（6/6）（分岐なし） | パッケージごと |
| `user.service` | 99.7%（306/307）（99.6%） | 95.1%（116/122）（94.7%） | パッケージごと |
| `auth.domain` | 98.2%（112/114） | 100.0%（38/38） | パッケージごと |
| `auth.repository` | 100.0%（49/49）（100.0%） | 100.0%（8/8）（100.0%） | パッケージごと |
| `auth.service` | 99.3%（265/267）（99.2%） | 93.0%（80/86）（92.1%） | パッケージごと |
| `audit.domain` | 99.6%（248/249）（99.5%） | 98.3%（59/60）（97.8%） | パッケージごと |
| `audit.service` | 93.4%（155/166）（100.0%） | 82.4%（28/34）（84.4%） | パッケージごと |
| `invitation.repository` | 100.0%（2/2） | 分岐なし | パッケージごと |
| `access.domain` | 100.0%（53/53） | 100.0%（29/29） | パッケージごと（変えていない） |

- どのパッケージも下限（行 80%・分岐 70%）を満たした。最も低いのは `audit.service`（行 93.4%・分岐 82.4%。Step 15 の 100.0%・84.4% から下がった）。
- **`audit.service` が下がった理由（レビューの R-03 で確かめた）**: 通らない行・分岐はすべて `AuditEventListener.java`。行 11（238〜250）と分岐 1（232）は、B4 で足した `fields(UserAdminAuditEvent)` の「出来事が null でなく組み立てに失敗したとき」の経路で、通るテストが無かった。残りの分岐 5（157・170・184・198・213、招待と登録の受け取りの `event == null ? …`）は B3 の前からある未通過で、B3 の 84.4%（27/32）と同じ。B4 の分には `AuditEventListenerTest#unbuildableUserAdminEventLogsItsFields` を足した（除外は足していない）。足した後の値は、下の関門の流し直しで測る（まだ測れていない）。
- **レビューの指摘への対応の後の関門（2026-10-03 01:18〜01:26、Gradle の表示 8m 22s）**: **BUILD FAILED**。結合テスト 689 件のうち `mail/config/MailConfigurationIT` の1件（credentials without encryption）が、別のテストのクラスの文脈が出した OTLP の指標の送信の WARN（`127.0.0.1` を含む）を取り込んで落ちた（今回の変更の経路の外。`MailConfigurationIT` だけの流し直しでは 7 件通過）。依頼者の決定で `team.md` の「不安定なテストと CI の失敗」の決まりで扱い（手元で再現せず、見立てと試みの範囲を `generation-notes.md` に記録）、verify を流し直した。上の表の Step 41 の値は直しの前のもの。詳しくは `generation-notes.md` の「B4（コード生成のレビューの指摘への対応、7回目の依頼）」。
- **流し直した関門（2026-10-03 01:31:23〜01:41:57）**: BUILD SUCCESSFUL（1回目で通過、`MailConfigurationIT` も通った）。時間 10 分 34 秒（634 秒。基準 Step 15 から +3 分 57 秒、Step 41 から +1 分 39 秒。許容 +5 分以内に収まる。Step 41 との差の大半は PC の負荷の揺れと見る（未検証））。単体 1,506 件（基準 +151）・結合 689 件（基準 +69）、失敗・誤り・飛ばし 0（対象DB も SKIPPED なし）。画面 95 ファイル通過。全体 行 98.9%（6276/6346）・分岐 94.8%（2331/2458）。パッケージごと: `useradmin.web` 98.8%・93.8%、`useradmin.service` 99.3%（141/142）・95.9%（47/49）、`useradmin.domain` 100.0%・100.0%、`audit.service` **100.0%（166/166）・85.3%（29/34）**（通らないのは B3 の前からある5分岐だけ）、ほかのパッケージは上の表の Step 41 の値と同じ。どのパッケージも下限を満たした。SpotBugs・Gitleaks は通過、`osvScan` は lockfile が変わらず UP-TO-DATE（Step 41 の結果のまま）。CI の見込みは約 13〜14 分で 30 分以内。
- **流し直した E2E（01:42:19〜01:46:56、4m 36s）**: 130 件通過（expected 130・unexpected 0・flaky 0・skipped 0、約 259.6 秒）。ファイルごとの件数は Step 42 と同じ。結果を記録した後、報告を中を開かずに消した。共有していない。
- **時間の許容（Q-C A）**: 手元の増加 +2 分 18 秒は許容（+5 分以内）に収まる。CI は B3 の統合（37015756225）で 9 分 21 秒。同じ増加を足すと約 12 分前後で、30 分以内に収まる見込み（実測は依頼者の push の後、Build and Test）。
- `./gradlew osvScan --rerun-tasks`: 失敗の条件に当たるもの 0 件、警告 14 件（B3 と同じ。どれも `vendor/make-you-chic-ui/package-lock.json` の開発用の `brace-expansion@5.0.9`・`undici@8.10.0`）。
- SpotBugs ＋ FindSecBugs・Gitleaks（no leaks found）は除外を足さずに通った。`backend/config/spotbugs-exclude.xml`・`.gitleaks.toml`・`backend/gradle.lockfile`・`frontend/package-lock.json`・`docker/monitoring/`・`backend/src/main/resources/db` に差は無い。新しいコードの SpotBugs の指摘は priority 2・3 の警告だけ（`EI_EXPOSE_REP2`・`CT_CONSTRUCTOR_THROW`・`SPRING_ENDPOINT`・`SERVLET_HEADER_USER_AGENT`）。
- **E2E（Step 42）**: `caffeinate -i ./gradlew e2eTest`（4 分 12 秒）で、いまある 11 ファイル（010〜100・130）の 130 件が通過（unexpected 0・flaky 0・skipped 0）。仮の資格情報が json に入っていないことの確かめも通った。ファイルごとの件数は `generation-notes.md` の Step 42。結果を記録した後、`frontend/playwright-report/`・`frontend/test-results/` を中を開かずに消した。報告は共有していない。B3 は E2E の条件に当たらず流していない。
- **再現の確かめ（Q-F B、Step 25）**: 直しの本体だけを一時的に戻すと、3つの `*LockTimeoutLeakIT` の 6 件すべてが落ちた（出た値の種類: 解除の予定の時刻・リフレッシュトークンのハッシュ値・メールアドレス・招待のトークンのハッシュ値・パスワードのハッシュ値。値そのものは記録していない）。既定の INFO でも `GlobalExceptionHandler` の ERROR の行に値が出ていた。戻した後に流し直して通った。
- **書き込みの問い合わせの待ちの上限**（計画 4.6 で未測定だった値）: 約 2.0 秒（トークンの更新 2,015 ミリ秒・表示の設定の保存 2,038 ミリ秒）。上限切れは `CannotAcquireLockException`（誤りの番号 50200・SQLState HYT00）。Hibernate の誤りのログ（`org.hibernate.orm.jdbc.error` の WARN 2行）に行の値は入っていなかった。

## 3. 主な実装の決定

- **SD-4**: 検索は HQL の `ilike :#{#pattern.value()} escape '\'` のまま受け付けられた（native への切り替えなし）。
- **伏せ字の経路**: 検索の文字は controller の引数の時点から `SearchText`、repository の口には `RedactedText`、氏名は `ProfileRequest` → `ProfileCommand` → `ProfileUpdate`。個人に関する値を `String` で受け渡す Bean の口は無い（B3 の page の文字列は受け入れた残る危険 R3）。5つの操作の controller は本文を読まず、`userId` は `long`。
- **最後の有効な管理者の保護**: 印を付ける・外す・止めるは、管理者の行と対象の行を利用者 ID の昇順に `PESSIMISTIC_WRITE`（上限 3000 ミリ秒）でまとめて排他してから、別の問い合わせで対象の要約と有効な管理者（印あり・停止なし、ロック中も数える）を読み直して判定する。停止を解くは対象の行だけ、失敗回数を戻すはロックの状態の行だけを排他する。どの操作も排他より前に書かない。
- **BUSY**: 排他の上限切れは repository の中で受けて `RowLockAttempt.Busy` にし、業務処理が先に `setRollbackOnly()` を付けて DB に触れずに `Busy` を返し、controller が 409 `USER_ADMIN_BUSY` にする。監査に残さない。WARN は排他の種類とクラスの名前だけ。
- **確かめ直し**: 判定の後・書き換えの前に操作した人が今も有効な管理者かを読み直し、外れていれば 403 `ACCESS_DENIED`（`AccessProblemTypes`）と監査 `NOT_ADMIN`。
- **監査**: 業務の判定に届いた要求ごとに1件（成功・業務の拒否・確かめ直しの 403）。確定の後に記録し、書き込みの失敗で操作を失敗させない。BUSY・入力の誤り・認可の入口の 401／403・一覧・氏名と言語は残さない。
- **U1 R-03**: `useradmin` は JPA のエンティティに依存せず（境界テスト）、排他の結果も要約も投影の値と ID だけで持つ。止める操作は `setSuspended` の後に `revokeAllRefreshTokens` を呼ぶだけで、先に読んだ値で書かない（`UserAdminOperationsIT#suspendKeepsOtherColumns`）。
- **既存の経路の上限切れの漏えいの直し**: 行の排他の読み取り（E1 ログイン、E2〜E4 招待・送り直しと取り消し・登録の完了）は repository の中で受けて原因をつながない `RowLockUnavailableException` にし、書き込みの問い合わせは `TraceAspect`（`LockFailureSafeTraceInterceptor`）と `GlobalExceptionHandler` の中央の手当てで、排他の失敗の連なりをクラスの名前だけにする（I-D1）。応答（500 `INTERNAL_ERROR`）・巻き戻し・監査は今までどおり。
- **E2〜E4 の置き場（案 1′）**: 断片の口と実装を追跡の対象の層の外の `invitation.lock` に置き、`InvitationRepository` が継ぐ。`..repository..` に置くと `findByTokenHashForUpdate(byte[])` の引数の招待のトークンのハッシュ値が TRACE に出るため。Spring Data は断片の実装を口のパッケージとその下からしか探さないため口も移した。
- **同時の重なりのテスト**: 待ち合わせの口で1つ目を止め、2つ目が行の排他の待ちに入ったことを H2 のセッションの一覧（自分のセッションを除き、絞り込みを引数で渡す）で確かめてから進める。止める上限は 2000 ミリ秒。`sleep` は使っていない。

## 4. 計画・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `generation-notes.md` に記録した（`project.md` の決まり）。計画 8節の D-1〜D-10 は計画のとおり。

| ID | 計画・設計の形 | 実際 | 理由・依頼者の扱い |
|---|---|---|---|
| G-1 | R1 は作業ブランチの最初のコミット | Plan Approval の記録 493b4dc は `develop` の上にあった | 依頼者の側で先に行われていた（了承済み） |
| G-2 | 記録した SQL の文で確かめる | `SqlStatementCounter` に `recorded()` を足した（テストだけ） | 了承済み |
| G-3〜G-8 | 名前・細部（`LockView.of`・`findAdminPage` の位置・`lockViewsOf`・`updateProfile` の戻り値・出来事を出さないことの確かめ方・ダミーの行） | `generation-notes.md` の B3 の表 | 細部 |
| G-9 | `AdminUserPage` の `toString`（計画に無い） | 件数だけを出す | 行の値を追跡に出さないため |
| G-10 | 認可のテストで管理の操作の監査の行が増えない（403 は既存のアクセスの拒否だけ） | 未認証の 401 も既存のアクセスの拒否（`TOKEN_MISSING`）として1行残る動作をテストで固定 | 既存の決まり（受け入れ済み） |
| G-11 | 追跡の行 `UserRepository#…` | 行は `SimpleJpaRepository#…`。漏えいのテストの中だけ Spring Data のロガーも TRACE にした | 受け入れ済み |
| G-12 | 404 の説明文に利用者 ID を載せない | `title`・`detail` には載らない。`instance`（要求のパス）には入る | 既存の共通の動作 |
| G-13・G-14 | 送り手の情報・操作した人の読み方 | B3 で `origin` を置いた。氏名と言語では操作した人を読まない | 招待と同じ形、BR5.2・BR5.3 |
| G-15 | `user.repository` は B4 で計測の対象になる | B3 の `UserAdminRow` で計測の対象になった | Q-A |
| G-16 | `RowLockHolder` の置き場は Step 25 で決める | `common/testsupport/RowLockHolder` に1つ | 受け入れ済み |
| G-17 | `LoginAttemptStateRepositoryIT` は足すだけ | 既存の `lockTimeout` の期待を `RowLockUnavailableException` に変えた | E1 の直しの確かめ（受け入れ済み） |
| G-18〜G-22 | `common.persistence`・中央の手当て・E1 の細部 | 定数 `LOCK_TIMEOUT_MILLIS` の移動、原因を付けられない例外、Spring 7.0.9 の `replacePlaceholders`・`writeToLog` の上書き、ERROR の `exceptionClass`、`lockForUpdate` は `tryLockForUpdate` の結果を写す | 細部 |
| G-23 | E2〜E4 の断片の置き場は `invitation.repository`（計画 Step 21・4.3、NFR 設計の2回目のレビューの R-02） | 口も実装も `invitation.lock`（案 1′）。`InvitationRepository` の口・呼び出し元・既存の単体テストは変えていない。カバレッジは `invitation.lock` が新しいパッケージとして下限の対象 | 3節。依頼者の決定（案 1 は起動しないと分かった後の案 1′） |
| G-24〜G-37 | 5つの操作の名前と細部（`of(RejectionReason)`・`Optional` の判定・ID の一覧の排他の結果・`leavesNoActiveAdmin` の読み方・行が無いときは待ち合わせの口を通らない・テストの差し替えの形・待ち合わせの絞り込みを引数で渡す ほか） | `generation-notes.md` の B4（4回目）の表 | 受け入れ済み |
| G-38〜G-47 | 待ち合わせの直しの範囲（`InvitationConcurrencyIT` は変えない）、行の無い利用者の作り方、監査の結合テストの手伝い、Hibernate の WARN 2行を許す確かめ方、失敗回数の確かめ方、書き換えの口の規則の書き方、改ざんのテストの送り方、確かめ直しの 403 を業務処理の結合テストに任せたこと、負荷の試験の場面（上限 10 は `userAdminOps` を `VUS=5`・`VUS=10`）、README | `generation-notes.md` の B4（5回目）の表 | 受け入れ済み |
| G-48 | `traceability.json` を仕上げる | OK の target を実在する1つのファイルに絞った。AC4.1.2 の「設定で変えたしきい値」は U3 では既定 5 だけを確かめ、任意のしきい値は既存の `LockPolicyTest` の性質ベースのテストが受け持つ | センサーの確かめに合わせるため。計画 Step 34 も既定 5 |
| G-49 | Step 42「Mailpit は見終わったら止める」 | 止めていない | 実行の前から動いていた（但し書きどおり） |
| G-50 | upstream_ids は U3 の AC・BR・NFR の 153 件 | NFR2.3・NFR6.7 を N/A で足した（155 件） | センサーが出典の列に引いた他の単位の ID を拾うため |
| G-51 | 上限切れは問い合わせを実行する repository のメソッドの本体で受ける（`reliability-design.md` 5.2、`security-design.md` 7節） | 止める操作のリフレッシュトークンの無効化（書き込みの問い合わせ）の待ちの上限切れは、業務処理の層（`UserAdminService` の止める操作）で受けて Busy（409 `USER_ADMIN_BUSY`）にした。WARN は `lockKind=REFRESH_TOKEN_ROWS` とクラスの名前だけ | レビューの R-01 への依頼者の決定（直す）とレビューの推奨 (a)。書き込みの問い合わせは I-D1 で個別に直さず中央の手当てに任せており、C1 の口の形も変えずに済むため。例外は repository と C1 の口の代理を通るが、TRACE の出力は中央の手当てでクラスの名前だけになる（`UserAdminSecretLeakIT` で確かめた） |
| G-52 | BUSY の時間は上限 3000 ミリ秒以上（Step 34） | リフレッシュトークンの行の重なりの BUSY は、待ちの下限 1000 ミリ秒で確かめた（実測 2.069 秒） | 書き込みの問い合わせの待ちの上限は H2 の既定（約 2 秒、設定ではない）で、行の排他の読み取りの 3000 ミリ秒より短いため |

## 5. 依頼者の決定

- **計画 9節**: Q-A A（投影 `UserAdminRow` を `user.repository` に）、Q-B A（B3 の終わりに途中の版、B4 の終わりに仕上げ）、Q-C A（手元の verify は基準から +5 分以内・CI は 30 分以内。実測 +2 分 18 秒）、Q-D A（台本と手順書を B4 で書き `k6 inspect` まで）、Q-E A（`İ`・`ß` の既知の差を `UserAdminQueriesIT#knownDifferences` に固定）、Q-F B（直しの本体を一時的に戻して落ちることを確かめた。2節）、Q-G A（ダミーの行をすべて持ったときのログインを `AuthLockTimeoutLeakIT` に入れた）、Q-H（一意の制約の違反の例外の文の件は直さず後の Intent へ。7節）。
- **B3 の関門の後**: G-10・G-11 を受け入れ、OSV-Scanner の警告 14 件は関門の基準どおり警告のまま進める。
- **B4**: 止めた理由への答えは案 1、試した結果を受けて **案 1′**（G-23）。G-16・G-17・G-24〜G-47 を受け入れ。`invitation/testsupport/TestInvitationBarrier#waitingForLock` の弱点を B4 で直し、テストだけの専用の区切り **C4b** にする（直す前の確かめで5回のうち1回は重なりを作れていなかった）。`UserAdminFixtures.java` は C6 の区切りに入れる。

## 6. 網羅（`traceability.json`）

155 件: OK 116・Deferred 36・N/A 3（GAP 0）。`aidlc engine sensor-traceability` で pass（findings 0）を確かめた。OK の target は実在する1つのファイルで、どれも存在することを確かめた。

- Deferred: `u5-user-admin-ui` 15 件（画面の AC）、`u1-user-suspension` 10 件（AC3.2.x。U3 は止める操作を `UserAdminOperationsApiIT` で結合の確かめに出した）、`performance-validation` 8 件（NFR5.1・5.3〜5.6・5.8・6.2・6.3。台本は Step 39）、`observability-setup` 3 件（NFR5.9〜5.11）。
- N/A: NFR10.2（索引・列・移行を足していない）。NFR2.3・NFR6.7（U3 の NFR 要件の文書が出典として引いた U1・前の Intent の U2 の ID をセンサーが拾うため、U3 の要件ではない旨を書いて足した。G-50）。
- target を1つに絞ったため、ほかに確かめているファイルの主なもの: 監査の行は `audit/service/UserAdminAuditIT`（AC2.1.1〜2.1.5・AC3.1.1〜3.1.6・AC4.1.1・AC4.1.3・AC4.1.7 の「監査ログに残る」部分）、状態が変わらないことと拒否の順は `useradmin/domain/RejectionPolicyTest`・`useradmin/service/UserAdminServiceTest`、漏えいは `UserAdminSecretLeakIT` と3つの `*LockTimeoutLeakIT`、BUSY は `UserAdminBusyApiIT`・`UserAdminServiceTest`・`UserAdminLockPortsIT`・`FailureResetPortIT`、構造は `UserAdminBoundaryArchitectureTest`。
- 要件の FR・NFR からは、機能設計の BR と NFR 要件の枝番を経てこの表へたどる（`project.md` の学び）。

## 7. 残る危険

- **一意の制約の違反の例外の文（Q-H）**: H2 の一意の制約の違反（23505）の例外の文に重なった値（メールアドレスなど）が入りうり、repository の外へ出ると TRACE の `TraceAspect` が出しうる（未検証）。今回の中央の手当ては排他の失敗の連なりだけが対象。後の Intent へ。
- **検索の既知の差（R5）**: `İ` を含む検索は `İ` を含む氏名に当たらない、`ß` と `SS` は別。決めた側の動作としてテストに固定し、README に書いた。
- **止める操作とログインの隙（M8 B）**: 止める操作の待ち合わせの間に同じ利用者のログインがトークンを追記できる。止める操作の確定でまとめて無効になることを `SuspendWhileLoginIT` で確かめたが、確定より後に出たトークンは対象外（決めた側の動作）。
- **接続プール**: 5つの操作は1件に2本の接続を使い、管理の操作とログインの失敗が重なると上限 30 を超えうる（README の既知の制約）。見積もりの確かめは performance-validation（NFR6.2・NFR6.3）。
- **page の文字列（U2 の R3）**: `UserAdminController#list`・`UserAdminService#list` の引数として TRACE に出うる（受け入れた危険）。
- **Hibernate の誤りのログ**: 上限切れのたびに `org.hibernate.orm.jdbc.error` の WARN が2行（誤りの番号と SQLState、`?` のままの SQL の文）出る。値は入らないことを確かめた（Hibernate の版を上げるときに確かめ直す）。
- **書き込みの問い合わせの待ちの上限**: H2 の既定で約 2 秒（行の排他の読み取りの 3 秒より短い）。設定にしていない。
- **戻しの条件（I-D4）**: 戻し先の版は停止の列を知らない（deployment-pipeline へ）。
- **停止を解く・失敗回数を戻すの確かめ直しの隙（レビューの R-02、依頼者が受け入れ）**: この2つの操作は、操作した人の確かめ直しを排他なしの読み取りで行う。確かめた直後から確定までの間に別の管理者がその人の印を外すと、外された人の操作が1件通りうる（確かめ直しは取った排他の範囲でだけ最新）。この2つの操作は有効な管理者の数を減らさないため、最後の管理者の保護は崩れない。テストは確定の前に印が外れる順（`UserAdminConcurrencyIT`）だけを確かめている。
- **止める操作のトークンの書き込みの上限切れ（レビューの R-01、直した）**: リフレッシュトークンの行がトークンの更新・ログインの書き込みと重なって待ちが上限切れ（約 2 秒）になったときも、409 `USER_ADMIN_BUSY` で停止もトークンの無効化も残らない（G-51）。待ちの上限が設定でない点は「書き込みの問い合わせの待ちの上限」のとおり。
- **一意の制約の違反の例外の文（レビューの R-04）**: 上の Q-H のとおり後の Intent。持ち主は後の Intent の要件定義・コード生成で、直すときは先に再現するテスト（一意の制約に当たる要求を TRACE と INFO で送り、出力に重なった値が無いこと）を書いてから直す（`project.md` の Mandated）。

## 8. 承認の場で確かめること

- G-23（E2〜E4 の断片の置き場が承認済みの設計の `invitation.repository` と違い `invitation.lock` になったこと）を、計画・NFR 設計との差として受け入れたことの確認。
- G-48（traceability の target を1つのファイルに絞ったこと、AC4.1.2 の設定で変えたしきい値を U3 で確かめていないこと）。
- G-49（Mailpit を止めていないこと）・G-50（traceability に他の単位の ID 2つを N/A で足したこと）。
- Step 41 の実測（+2 分 18 秒、全パッケージが下限を満たす）と、CI の時間は push の後に確かめること。
- OSV-Scanner の警告 14 件（B3 と同じ。`vendor/make-you-chic-ui` の開発用の依存）。
- 8節のコミットの区切りと、squash の件名・本文の案。
- レビューの指摘への対応: R-01 を直したこと（G-51・G-52）、R-02 を残る危険として受け入れたこと、R-03 の確かめの結果、R-04 を後の Intent へ回したこと、R-05（AC4.1.2 の任意のしきい値は既存の `auth/domain/LockPolicyTest` の性質ベースのテスト（しきい値 1〜20）で確かめられているとして受け入れ）。
- `AuditEventListener` の組み立てに失敗したときの `fields(UserAdminAuditEvent)` が `auditEventType` のキーに操作の区分（例 `SUSPEND`）を載せること（本番では起きない経路。直していない。直すかを確かめる）。
- 関門で1回落ちた `MailConfigurationIT` は、依頼者の決定で `team.md` の決まりで扱い、流し直して通った（不安定と確かめられていない扱い。出力を捕まえる範囲の弱さは9節で後の Intent へ）。CI で同じテストが落ちたら二度目として原因を直すまで進まない。

## 9. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」の表のとおり。この段の値を足すと:

| 項目 | 内容 | 持ち主 |
|---|---|---|
| カバレッジの実測 | B4 の統合の後の `develop` で `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify。作業ブランチの値は2節 | Build and Test |
| verify の時間と CI | 手元 6 分 37 秒 → 8 分 55 秒（+2 分 18 秒）。CI の実測（B3 の統合は 9 分 21 秒、見込み 12 分前後）を push の後に確かめる | Build and Test |
| CI | 依頼者の push の後に通ることを確かめ、失敗したら `team.md` の決まりで扱う | Build and Test |
| 負荷の試験の台本 | `k6 inspect --include-system-env-vars` を流し直す | Build and Test |
| 性能と接続プール | NFR5.1・5.3〜5.6・5.8・6.2・6.3 | performance-validation |
| 観測・SLO | NFR5.9〜5.11、7つの API の `uri`・`le` の値、監査の種類5つと理由の名前（README と `AuditEventType`・`AuditFailureReason`） | observability-setup |
| 戻しの条件・スモークテスト | 停止中の利用者の確かめ（I-D4）、5つの操作は監査に残るため送る前に依頼者に伝える、初期管理者だけでは `LAST_ACTIVE_ADMIN` を見せられない | deployment-pipeline |
| 画面 | U5 の AC（6節の Deferred）と 8KB を超える要求の HTML の 400 の画面の扱い | B5（U5） |
| 一意の制約の違反の例外の文（Q-H） | 7節 | 後の Intent |
| `ms-pool-pending` の式の見直し | NFR5.10 の申し送り | 配備先が決まったとき |
| 出力を捕まえるテストの範囲の弱さ | `CapturedOutput`（`output.getAll()`）は、捕まえている間のすべてのスレッドの出力を含む。テストの文脈の使い回しで残った別の文脈（`ExternalExportIT` が有効にした OTLP の指標の送信）の背景のスレッドの WARN（`127.0.0.1` を含む）が `MailConfigurationIT#invalidSettingWarnsOnce` に紛れ込み、レビューの指摘への対応の後の関門で1回落ちた（流し直しで通過、不安定と確かめられていない扱い。見立ては未検証）。ほかの `*SecretLeakIT` などの出力の全体を確かめるテストにも同じ弱さがありうる。直し方（そのテストの文脈の出力だけを見る、`ExternalExportIT` の文脈を閉じる、送信の間隔を延ばす など）は後で決める | 後の Intent |

## 10. コミットの区切りの案（B4。依頼者の承認を得てから行う）

計画 3.3 の C4〜C8 に、依頼者の決定の C4b を足した。レビューの R-01・R-03 への対応は、ファイルが入っている区切りにそのまま入れる: 直しの本体 `UserAdminService.java` と再現する単体テスト `UserAdminServiceTest.java`、R-03 の `AuditEventListenerTest.java` は C6、再現する結合テスト `UserAdminBusyApiIT.java`・`UserAdminSecretLeakIT.java` は C7（新しいファイルは無く、区切りのファイルの数は変わらない）。直しと再現する結合テストを同じコミットにしたいときは、2つの結合テストを C6 へ移す（依頼者に確かめる）。メッセージは日本語で、末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。どれも `aidlc/` の下を含まない。途中のコミットで verify を流し直さない（通ることは、すべての区切りを含む作業ブランチで Step 41 に確かめた）。7つの区切りで、作業ブランチの B4 の変更 87 ファイルがちょうど1回ずつに入ることを確かめた（重なり 0・漏れ 0）。

### C4（Step 19〜25、24 ファイル）

件名の案「B4 既存の経路の排他の待ちの上限切れで行の値をログに出さない（E1〜E4・書き込みの問い合わせ）」

- `backend/src/main/java/cherry/mastersmith/common/persistence/package-info.java`
- `backend/src/main/java/cherry/mastersmith/common/persistence/RowLockFailures.java`
- `backend/src/main/java/cherry/mastersmith/common/persistence/RowLockUnavailableException.java`
- `backend/src/main/java/cherry/mastersmith/common/persistence/RowLockAttempt.java`
- `backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java`
- `backend/src/main/java/cherry/mastersmith/invitation/repository/InvitationRepository.java`
- `backend/src/main/java/cherry/mastersmith/invitation/lock/InvitationLockQueries.java`
- `backend/src/main/java/cherry/mastersmith/invitation/lock/InvitationLockQueriesImpl.java`
- `backend/src/main/java/cherry/mastersmith/invitation/lock/package-info.java`
- `backend/src/main/java/cherry/mastersmith/common/observability/LockFailureSafeTraceInterceptor.java`
- `backend/src/main/java/cherry/mastersmith/common/observability/TraceAspect.java`
- `backend/src/main/java/cherry/mastersmith/common/error/web/GlobalExceptionHandler.java`
- `backend/src/test/java/cherry/mastersmith/common/persistence/RowLockFailuresTest.java`
- `backend/src/test/java/cherry/mastersmith/common/persistence/RowLockUnavailableExceptionTest.java`
- `backend/src/test/java/cherry/mastersmith/common/testsupport/RowLockHolder.java`
- `backend/src/test/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepositoryTest.java`
- `backend/src/test/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepositoryIT.java`
- `backend/src/test/java/cherry/mastersmith/invitation/lock/InvitationLockQueriesImplTest.java`
- `backend/src/test/java/cherry/mastersmith/invitation/repository/InvitationRepositoryIT.java`
- `backend/src/test/java/cherry/mastersmith/common/observability/LockFailureSafeTraceInterceptorTest.java`
- `backend/src/test/java/cherry/mastersmith/common/error/web/GlobalExceptionHandlerTest.java`
- `backend/src/test/java/cherry/mastersmith/auth/web/AuthLockTimeoutLeakIT.java`
- `backend/src/test/java/cherry/mastersmith/invitation/web/InvitationLockTimeoutLeakIT.java`
- `backend/src/test/java/cherry/mastersmith/user/web/MePreferencesLockTimeoutLeakIT.java`

### C4b（待ち合わせの直し、2 ファイル）

件名の案「B4 招待の待ち合わせの手伝いが自分の問い合わせを数えていたのを直す（テストだけ）」

- `backend/src/test/java/cherry/mastersmith/invitation/testsupport/TestInvitationBarrier.java`
- `backend/src/test/java/cherry/mastersmith/invitation/service/RegistrationConcurrencyIT.java`

### C5（Step 26・27、14 ファイル）

件名の案「B4 利用者の管理の操作の拒否の判定と監査の種類・理由（useradmin.domain・audit.domain）」

- `backend/src/main/java/cherry/mastersmith/useradmin/domain/AdminOperation.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/domain/RejectionReason.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/domain/OperationFacts.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/domain/RejectionPolicy.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/domain/UserAdminAuditFailure.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/domain/UserAdminAuditEvent.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/domain/UserAdminProblemTypes.java`
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditFailureReason.java`
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/domain/RejectionPolicyTest.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/domain/UserAdminAuditEventTest.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/domain/UserAdminProblemTypesTest.java`
- `backend/src/test/java/cherry/mastersmith/audit/domain/AuditEventFactoryTest.java`

### C6（Step 28〜32、33 ファイル）

件名の案「B4 利用者の管理の5つの操作の業務処理と行の排他（最後の有効な管理者の保護・失敗回数を戻す）」

- `backend/src/main/java/cherry/mastersmith/user/repository/UserRowLockRepository.java`
- `backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java`
- `backend/src/main/java/cherry/mastersmith/user/service/AdminRowsLock.java`
- `backend/src/main/java/cherry/mastersmith/user/service/UserRowLock.java`
- `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java`
- `backend/src/main/java/cherry/mastersmith/auth/service/LoginFailureResetPreparation.java`
- `backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptBarrier.java`
- `backend/src/main/java/cherry/mastersmith/auth/service/NoOpLoginAttemptBarrier.java`
- `backend/src/main/java/cherry/mastersmith/auth/service/LockAdministrationService.java`
- `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/service/OperationResult.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminBarrier.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/service/NoOpUserAdminBarrier.java`
- `backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminService.java`
- `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`
- `backend/src/test/java/cherry/mastersmith/user/repository/UserRowLockRepositoryTest.java`
- `backend/src/test/java/cherry/mastersmith/user/repository/UserRowLockRepositoryIT.java`
- `backend/src/test/java/cherry/mastersmith/user/repository/UserAdminQueriesIT.java`
- `backend/src/test/java/cherry/mastersmith/user/service/UserAccountServiceTest.java`
- `backend/src/test/java/cherry/mastersmith/user/service/UserAdminLockPortsIT.java`
- `backend/src/test/java/cherry/mastersmith/auth/service/LockAdministrationServiceTest.java`
- `backend/src/test/java/cherry/mastersmith/auth/service/LoginServiceTest.java`
- `backend/src/test/java/cherry/mastersmith/auth/service/FailureResetPortIT.java`
- `backend/src/test/java/cherry/mastersmith/auth/testsupport/TestLoginAttemptBarrier.java`
- `backend/src/test/java/cherry/mastersmith/audit/service/AuditEventListenerTest.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminServiceTest.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminOperationsIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/service/UserAdminConcurrencyIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/service/ResetLoginConcurrencyIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/service/SuspendWhileLoginIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/testsupport/RecordingTransactionManager.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/testsupport/TestUserAdminBarrier.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/testsupport/UserAdminFixtures.java`

### C7（Step 33〜36、9 ファイル）

件名の案「B4 利用者の管理の5つの API と、監査・改ざん・上限切れ・漏えいの結合テスト」

- `backend/src/main/java/cherry/mastersmith/useradmin/web/UserAdminController.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminResetLoginFailuresApiIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyApiIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminMassAssignmentIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminSecretLeakIT.java`
- `backend/src/test/java/cherry/mastersmith/useradmin/testsupport/UserAdminApi.java`
- `backend/src/test/java/cherry/mastersmith/audit/service/UserAdminAuditIT.java`
- `backend/src/test/java/cherry/mastersmith/audit/service/UserAdminAuditWriteFailureIT.java`

### C8（Step 37〜39、5 ファイル）

件名の案「B4 useradmin の境界テスト・packagesJudgedByTotal・README・負荷の試験の台本」

- `backend/src/test/java/cherry/mastersmith/useradmin/UserAdminBoundaryArchitectureTest.java`
- `backend/build.gradle.kts`
- `README.md`
- `perf/k6/scenarios.js`
- `perf/README.md`

### 記録のコミット（`aidlc/` の下だけ）

- **R4**（C4〜C8 の後）: 件名の案「B4（U3 後半）のコード生成の記録（生成の記録・まとめ・網羅の記録・計画のチェック）」。中身: `generation-notes.md` の B4 の節、`code-summary.md`・`source-manifest.json`・`traceability.json`、計画の Step 16（後から付けた）・17〜42 のチェックと Step 43 の記録の項目のチェック。
- **R5**（統合の前）: 件名の案「B4（U3 後半）の関門の記録（verify・OSV・E2E の実測と報告を消したこと）」。中身: Step 41・42 の記録、その時点の `aidlc-state.md`・監査ログ。R4 と同じ時点で作るなら1つにまとめてよい。
- どちらも `git show --stat` で `aidlc/` の外のファイルが入っていないことを確かめる。

### `develop` への squash の統合の案

手順は計画 3.2（`aidlc/` を squash から外し、`git diff --cached --stat` が `source-manifest.json` の B4 の分（87 件）と一致することを確かめる）。

```
B4 利用者の管理の操作と最後の管理者の保護（U3 後半）: 5つの操作・監査・上限切れ・既存の経路の上限切れの漏えいの直し

Intent 260930-user-admin の Bolt B4（U3 u3-user-admin-api の後半）。

- POST /api/admin/users/{userId}/grant-admin・revoke-admin・suspend・resume・reset-login-failures:
  成功は 204。拒否は「対象がいない → 自分自身 → 対象が停止中 → 変えるものが無い → 最後の有効な管理者」
  の順で最初の理由1つ（404 USER_NOT_FOUND・409 USER_ADMIN_*）
- 最後の有効な管理者の保護: 管理者の行と対象の行を利用者 ID の昇順にまとめて排他してから数え直す。
  判定の後・書き換えの前に操作した人を確かめ直し、外れていれば 403 ACCESS_DENIED
- 排他の待ちの上限切れ（3000 ミリ秒）は巻き戻しの印を付けて 409 USER_ADMIN_BUSY。監査に残さない
- 止める操作は停止とリフレッシュトークンのまとめての無効化を同じトランザクションで行う
- 監査に種類5つ（USER_ADMIN_GRANTED・USER_ADMIN_REVOKED・USER_SUSPENDED・USER_RESUMED・
  LOGIN_FAILURES_RESET）と理由4つ（SELF_OPERATION・TARGET_SUSPENDED・NO_CHANGE・LAST_ACTIVE_ADMIN）を足した
- 既存のログイン・招待・送り直しと取り消し・登録の完了と書き込みの問い合わせで、排他の待ちの上限切れの
  例外の文に入る行の値をログに出さないよう直した（common.persistence、招待の排他の口は invitation.lock、
  TraceAspect と GlobalExceptionHandler の中央の手当て）。再現するテストを含む
- 招待の待ち合わせの手伝いが自分の問い合わせを数えていたのを直した（テストだけ）
- packagesJudgedByTotal から common.error.web・common.observability を外した（9 → 7）
- README に5つの操作・監査の種類と理由・接続の既知の制約を、perf に利用者の管理の場面を足した

統合の前に ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
（単体 1503・結合 688・画面 801、8 分 55 秒）と ./gradlew osvScan --rerun-tasks、
./gradlew e2eTest（130 件）が通った。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

統合の後の `develop` の記録のコミットの件名の案「B4 のコード生成の記録（生成の記録・まとめ・網羅の記録・関門・統合 <ハッシュ>）」（計画 3.2 の手順 3）。
