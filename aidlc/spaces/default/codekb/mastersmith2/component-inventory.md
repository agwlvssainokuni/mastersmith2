# 部品の一覧（mastersmith2）

## 読み方

見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回の 36 個に、今回（Intent `261003-user-admin-followup`）`useradmin`・`frontend-feature-useradmin` の2つを足して 38 個になった（名前の変更は無い）。

- 状態: healthy（問題なし）・at-risk（今の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ（2つの走査の記録が混ざっている）:
  - 「深い（今回）」は今回の範囲を絞った走査（Focused scan・深さ Minimal、コミット `47541e3`）で深く読んだ部品で、`analyzed.components` に入れた 8 個（`useradmin`・`frontend-feature-useradmin`・`frontend-e2e`・`audit`・`user`・`invitation`・`mail`・`perf-and-monitoring`）。どれも今回の束に関わるファイルだけを読んだ「一部」の深さである。
  - 「深い（前回）」は前回（Intent `260930-user-admin` の始め、コミット `31b980b`、利用者の管理を作る前）に深く読んだもの。今回は確かめ直していないため、記録上は流し読みに下げた（`shallow.paths`）。とくに「利用者の管理が無い」「利用停止の状態が無い」などの文は、その後 Intent `260930-user-admin` で作られた（今回 `useradmin` のファイルと移行 `V9__u1_user_suspension.sql` の存在を確かめた）ため、当時の記録として読む。
  - 「流し読み」はファイル名・見出し・検索だけで、責務の文はその範囲と前回までの記録による。
- K の番号は `business-overview.md` の所見の一覧を指す。K-1〜K-9 は前回の所見、K-17〜K-24 は今回の所見（番号の振り方は `business-overview.md`）。この文書に本文を書いたのは K-2（`auth`）・K-6（`audit`）・K-8（`user`）・K-19・K-21（`useradmin` と `frontend-feature-useradmin`）・K-18（`frontend-e2e`）・K-20・K-23（`perf-and-monitoring`）・K-22（`mail`）で、ほかは持ち主の文書を参照する。
- パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省いて書く。依存の向きは `dependencies.md`。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス（`@EnableScheduling` を含む）。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて `application.yaml`・`logback-spring.xml`）
- 責務: Spring Security の連鎖（`config/SecurityConfig.java`）。セッションなし・CSRF の仕組みなしで、各機能の差し込み口 `SecurityRuleContributor` を order 順に当てる。SPA の配信と観測の設定。
- 依存: `common-security`・`common-web`・`common-observability`
- 状態: healthy ／ 読みの深さ: 流し読み（前回 `31b980b` は深い。今回は確かめ直していない。前回の範囲: 一部。`SecurityConfig.java` だけ。`application.yaml` と観測の設定は一覧だけ）

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: `ProblemType`（code・状態コード・日英の文言）、`BusinessException`、`@RestControllerAdvice` による Problem Details、`GET /api/problems/{slug}`。
- 2026-10-04 の流し読み（開発担当）: 想定外の例外は `error/web/GlobalExceptionHandler.java` で 500 `INTERNAL_ERROR` にし、ERROR を原因の連なり付き（`setCause(ex)`）で出す。応答には例外の文を載せない。行の排他の失敗だけはクラスの名前だけを出す（K-24）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-security

- 場所: `common/security/`
- 責務: 秘密の値の伏せ字（`RedactedText` など）、安全の決まりの差し込み口の型 `SecurityRuleContributor`。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-web

- 場所: `common/web/`
- 責務: 本文の大きさの上限、Web の設定値（`mastersmith.web.base-url` ほか）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-health

- 場所: `common/health/`
- 責務: ヘルスチェック。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-i18n

- 場所: `common/i18n/`
- 責務: 表示言語の解決（日英）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-observability

- 場所: `common/observability/`
- 責務: `TraceAspect`（`web`・`service`・`domain`・`repository` の層のメソッドの引数と戻り値を、ロガーが TRACE のときに文字列にして出す。`record` 自身・設定のクラス・起動クラスは対象外。`TraceAspect.java` 47〜57 行、アーキテクトが確かめた）、外部エクスポートの出口の伏せ字（`SanitizingLogRecordExporter`）ほか。一覧の型への影響は K-8（`user`）。
- 2026-10-04 の流し読み（開発担当）: `TraceAspect` は例外のときに `EXCEPTION ...: $[exception]`（例外の文字列）を TRACE で出し、`log-exception-stack-trace: true`（既定）で原因の連なりも出す。例外の文を伏せるのは `LockFailureSafeTraceInterceptor` で、行の排他の失敗（`RowLockFailures.isLockFailure`）だけ。一意の制約の違反の文がログに出る経路への関わりは K-24（`architecture.md` の Interaction Diagrams 6）。
- 状態: at-risk（K-24 の経路の候補） ／ 読みの深さ: 流し読み（`TraceAspect` の対象の指定と、例外の出し方）

### auth

- 場所: `auth/{domain,service,repository,web}`（約 3,020 行）
- 責務: ログインとアカウントロック、アクセストークン（HS256 の JWT、クレームは `sub`・`iat`・`exp` だけ）とリフレッシュトークン（表 `refresh_tokens`、使うたびに無効にして作り直す）、ログアウト、期限切れのリフレッシュトークンの定期の削除。要求ごとのアクセストークンの認証（`web/AccessTokenAuthenticationProvider.java`）で DB から利用者を読み直す。設定は `service/AuthProperties.java`（アクセストークン 5 分・リフレッシュトークン 24 時間・ロックのしきい値 5・ロックの時間 30 分が既定）。
- 依存: `user`（service の口と domain）・`common-error`・`common-security`・`common-observability`
- 利用停止を効かせる3つの入口はこの部品にある（K-1、`architecture.md` の Interaction Diagrams 1）。
- K-2 ロックの状態は `auth` の表にあり、時刻で決まる（確かめた事実）:
  - 表 `login_attempt_states`（V3）: `subject_id`（利用者 ID、または -1〜-8 のダミー）・`consecutive_failures`・`locked_until`。`users` への外部キーは無く、ダミーの行（V3 で作成。存在しない利用者でのログインの時間をそろえるため）と同じ表にある。
  - ロック中の判定は `now` が `locked_until` より前か（`domain/LockPolicy.java` 52 行）。解除時刻を過ぎた後は、次の試みで失敗回数を 0 から数え直す（58 行）。したがって解除時刻を過ぎた行も、次のログインまで `locked_until` と失敗回数が残る。表の値だけで「ロック中」とすると誤り、注入した時計（`Clock`）と比べる必要がある。
  - 書き込みは明示の `update(subjectId, failures, lockedUntil)`（`repository/LoginAttemptStateRepository.java` 120 行〜）と、行の排他つきの読み取り `lockForUpdate`（65 行〜、待ちの上限 3 秒）。行が無い場合の作成は `createIfAbsent`（102 行〜）。
  - 利用者ごとの行は、`UserCreatedEvent` を受けた `service/LoginAttemptStateInitializer.java`（`Propagation.MANDATORY`、同じトランザクション）が作る。ログインの判定で行が無ければ `Decision.ROW_MISSING` になり、行を作ってやり直す（`service/LoginService.java` 157〜198 行と `createRow`）。行の無い利用者がありうる。
  - ロックしたときは INFO のログ（キー `userId`）を出す（`LoginService.java`）。既存の解除は時間の経過だけで、管理者による解除の操作と監査の種類は無い。
- K-2（見立て、未検証）:
  - ロックの解除は既存の口で「`lockForUpdate` で排他つきで読み、`update(id, 0, null)`」と書ける見込み。ログインの判定と同じ行の排他で順番がそろう。
  - 一覧・解除は「行が無い＝ロックしていない」と扱う必要がある。
  - 解除の口を足すと `auth.repository`（と理由を足すなら `auth.domain`）に手が入り、K-7 のカバレッジの作業が付く。
- 状態: at-risk（K-1・K-2） ／ 読みの深さ: 流し読み（前回 `31b980b` は深い。今回は確かめ直していない。前回の範囲: `domain` は10ファイル、`repository` のすべて、`service` の6ファイル、`web` の4ファイル）

### access

- 場所: `access/{domain,service,web}`（約 1,040 行、DB を読まない）
- 責務: `/api/admin` と `/api/admin/**` を管理者だけにする決まり（`domain/AdminPaths.java`・`web/AdminSecurityContributor.java` order 210）、`/api/**` の既定をログイン必須にする決まり（`web/AdminApiDefaultAccess.java`）、管理者の判定（`web/AdminAuthorizationManager.java`、K-4）、401・403 の応答と拒否の出来事 `AdminAccessDeniedEvent`、`GET /api/admin/check`。拒否の理由 `domain/AccessDeniedReason.java` は `TokenFailureReason` を網羅の `switch` で写す（K-1）。
- 依存: `auth`（domain・web）・`common-error`・`common-security`・`config`
- 状態: at-risk（停止の区分を足すと `AccessDeniedReason` に波及しうる。K-1・K-7） ／ 読みの深さ: 流し読み（前回 `31b980b` は深い。今回は確かめ直していない。前回の範囲: `domain` の2ファイルと `web` の5ファイル。`service` はファイル名だけ）

### audit

- 場所: `audit/{domain,service,repository}`（約 1,590 行、Web の層なし）
- 責務: 各機能の出来事を受け、確定の後に `audit_events` に追記する（流れは `architecture.md` の Interaction Diagrams 3）。監査を見る画面・API は無い（`AuditBoundaryArchitectureTest` 125 行で Web の層を持たない決まり）。
- 依存: 出来事の型のため `auth`・`access`・`user`・`invitation`・`dslmanage` の domain
- K-6 監査の列は足りているが、管理の操作の種類と出来事の型は無い（確かめた事実）:
  - `audit_events` は `actor_user_id`（V6）と `target_user_id`・`target_invitation_id`（V7）を持ち、操作した人と対象の利用者を記録できる（パスワードの変更・招待の写し取りが使う、`domain/AuditEventFactory.java`）。
  - `domain/AuditEventType.java` の種類は `LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED`・DSL の5つ・`PASSWORD_CHANGED`・`INVITATION_ISSUED`・`INVITATION_RESENT`・`INVITATION_CANCELLED`・`REGISTRATION_COMPLETED`・`REGISTRATION_FAILED`。管理者の印の変更・利用停止・停止の解除・ロックの解除の種類は無い。
  - `domain/AuditFailureReason.java` は `USER_NOT_FOUND`・`PASSWORD_MISMATCH`・`ACCOUNT_LOCKED`・`NOT_ADMIN`・`TOKEN_*` の3つ・`CURRENT_PASSWORD_MISMATCH`・招待の4つ・`EMAIL_ALREADY_REGISTERED`。結果は `AuditResult`（`SUCCESS`・`FAILURE`）。
  - 列 `event_type` と `failure_reason` は `VARCHAR(32)`（V4）で、名前の長さに上限がある。
  - 記録は `service/AuditEventListener.java`（`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`、98 行）→ `service/AuditEventRecorder.java`（`REQUIRES_NEW`、51 行）。要求1件で接続を2本使う。
- K-6（見立て、未検証）: 管理の操作を足すと、出来事の型（持ち主の機能の `domain`）、`AuditEventType`（と理由を足すなら `AuditFailureReason`）、`AuditEventListener`・`AuditEvent`・`AuditEventFactory` の3ファイル、`AuditBoundaryArchitectureTest` の許す依存を直すことになる。列を足す移行は要らない見込み。`audit.repository` に手を入れると K-7 の作業が付く。
- 上の K-6 は Intent `260930-user-admin` の前の記録。その後、利用者の管理の出来事 `useradmin.domain.UserAdminAuditEvent` を `service/AuditEventListener.java` と `domain/AuditEventFactory.java` が import するようになった（今回の import の検索で確かめた）。追加された監査の種類の名前は今回読んでいない。
- 監査の記録の失敗のログ（2026-10-04 に確かめた事実）: `service/AuditEventListener.java` の `FAILURE_MESSAGE`（72 行、「監査イベントの記録に失敗しました」）を ERROR で、原因の例外を付けて（`setCause(e)`、297〜301 行）出す。手元の警報 `ms-audit-fail` はこの文言を Loki で数える（K-20、`perf-and-monitoring`）。接続プールの待ちが `connection-timeout`（5 秒）を超えて2本目を借りられないとこの ERROR になる見込み（見立て）。
- 状態: at-risk（K-6。今回は警報の確かめ K-20 で間接に関わる） ／ 読みの深さ: 深い（今回は `service/AuditEventListener.java` の監査の失敗のログだけ）。前回の深い範囲（`domain` の4ファイル・`repository` のすべて・`service` の3ファイル）は流し読みに下げた

### user

- 場所: `user/{domain,service,repository,web}`（約 3,370 行）
- 責務: 利用者（表 `users`、V2・V7）、パスワード、氏名と表示の設定、自分の設定の API（`web/MeController.java`、`/api/me/**`）、起動時の初期管理者の作成（`service/InitialAdminInitializer.java`。INFO のキーは `maskedEmail` の伏せ字）。ほかの機能には `service/UserAccountService.java` の口だけを見せる（`auth` は `user.repository` を使えない、K-3）。
- 依存: `common-error`・`common-observability`
- 利用停止の状態の列が無いこと（K-1）と、管理者の印を変える口が無いこと（K-4）は `architecture.md`。一覧の問い合わせが無いことは `api-documentation.md`（K-5）。
- K-8 一覧の型と応答は、TRACE のログとメールアドレスの決まりに当たる（確かめた事実）:
  - `TraceAspect`（`common-observability`）は `service`・`repository` などの層の引数と戻り値を TRACE で文字列にして出す。そのため既存の型は `toString` でメールアドレスと氏名を伏せる（`service/UserSummary.java` 38 行・`service/NewUser.java`）か、`RedactedText` で受け渡す（`repository/UserRepository.java` の `existsByRedactedEmail`、`UserAccountService.findDisplayName`）。
  - `project.md` の Forbidden: メールアドレスをアプリのログとエラー応答に含めない（先頭の1文字＋`***`＋`@`＋ドメインの伏せ字 `EmailAddress.mask` は可、`project.md` の Corrections）。管理者に見せる一覧の応答にメールアドレスを載せる前例は、招待の一覧にある（`invitation/web/InvitationPageResponse.java` の `email`）。
  - 漏えいの確かめは `*SecretLeakIT`（`AuthSecretLeakIT`・`AccessSecretLeakIT`・`AuditSecretLeakIT`・`InvitationSecretLeakIT`・`MeSecretLeakIT` ほか）と `common/testsupport/JsonLogRecords` の形が使える（ファイル名だけ確かめた）。
- K-8（見立て、未検証）: 利用者の一覧の要約・ページの型も、`toString` で伏せるか `record` の外の伏せる型で持つ必要がある。`record` は `TraceAspect` の対象外だが、引数・戻り値として渡ると `toString` が出るため、`record` の `toString` を上書きする形（`UserSummary` と同じ）が前例になる。
- 上の K-1・K-4・K-5 は Intent `260930-user-admin` の前の記録で、その Intent で利用停止の列（移行 V9）・管理の口が足された（今回はファイルの存在だけを確かめた）。
- 一意の制約の違反の扱い（2026-10-04 に確かめた事実、K-24 の一部）: `service/UserAccountService.java` の `createUser` は先に `existsByRedactedEmail` で確かめ、同時の作成で `saveAndFlush` が `DataIntegrityViolationException` を投げたら、原因の連なりの Hibernate の `ConstraintViolationException` の制約の名前が `UK_USERS_EMAIL` を含むときだけ結果の型 `EmailAlreadyUsed` にし、ほかは投げ直す。登録の完了（`invitation`）もこの口を使う。違反の例外の文がログに出る経路は `architecture.md` の Interaction Diagrams 6。
- 状態: at-risk（K-8・K-24） ／ 読みの深さ: 深い（今回は `service/UserAccountService.java` の一意の違反の扱いだけ）。前回の深い範囲（`domain` の2ファイル・`repository` のすべて・`service` の8ファイル）は流し読みに下げた

### invitation

- 場所: `invitation/{domain,repository,service,web}`（約 4,340 行）
- 責務: 招待・送り直し・取り消し・一覧（`/api/admin/invitations`）、リンクの確かめと登録の完了（`/api/registration/**`、ログインなし）、期限の切れた招待の定期の削除。表は V8（`invitations`、`users` への外部キー2つ）。招待先のメールアドレスの利用者がいれば 409 `INVITATION_EMAIL_REGISTERED`（状態を見ない、K-1）。
- 管理の一覧と操作の見本（K-5、`api-documentation.md`）。`auth`・`audit` に依存できない境界（`InvitationBoundaryArchitectureTest` 50 行）のため、要求の文脈の読み取りを自前で持つ（`web/InvitationRequestContextResolver.java`）。
- 依存: `user`（domain と service の口）・`mail`・`common-error`・`common-security`・`common-web`・`common-observability`
- 一意の制約の違反の扱い（2026-10-04 に確かめた事実、K-24 の一部）: `service/InvitationService.java` の `issueWithOneRetry` は `UK_INVITATIONS_PENDING_EMAIL` の違反だけを受けて勝った側を読み直し、2回目は `IllegalStateException`（原因に元の例外を付ける）を投げる。ほかの制約は投げ直す。投げ直した例外は `GlobalExceptionHandler` の ERROR（原因の連なり付き）に載りうる（`architecture.md` の Interaction Diagrams 6）。
- 状態: at-risk（K-24） ／ 読みの深さ: 深い（今回は `service/InvitationService.java` の一意の違反の扱いだけ）。前回の深い範囲（`web` の4ファイル・`domain/InvitationPaging.java`・`service/InvitationBarrier.java`・`InvitationService.java` の一覧の部分）は流し読みに下げた

### useradmin

- 場所: `useradmin/{domain,service,web}`（Intent `260930-user-admin` で作られた。本体 28 ファイル、テストは `backend/src/test/java/cherry/mastersmith/useradmin/` の `domain`・`service`・`web`・`testsupport` と `UserAdminBoundaryArchitectureTest`）
- 責務: 利用者の管理の API（`/api/admin/users` の一覧と、氏名・言語の変更・管理者の印の付け外し・利用停止と再開・ログインの失敗回数の取り消し。一覧は `api-documentation.md`）。業務処理は結果の型（`service/OperationResult.java`・`UserAdminListResult.java`）を返し、controller が業務エラーに変える形（K-5 の型）。監査の出来事 `domain/UserAdminAuditEvent.java` を知らせる。
- 依存（2026-10-04 に import の検索で確かめた）: `user`（domain・service）・`auth`（domain・service）・`access.domain`・`common`（`error`・`observability`・`paging`・`persistence`）。外から import するのは `audit`（`AuditEventListener`・`AuditEventFactory`）だけ。
- 一意の制約（2026-10-04 に確かめた事実、K-24 の一部）: 本体に `DataIntegrityViolationException` を扱う処理は無く、氏名・言語の変更と5つの操作は一意の制約のある列に書かない。
- K-21 印を外した直後の 403 と監査を1つのテストで確かめていない（2026-10-04 に確かめた事実）:
  - `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java` の `flagChangeTakesEffectOnTheNextRequest`（264〜287 行）は、印を付ける・外す直後の一覧の 200 と 403 `ACCESS_DENIED` を確かめるが、監査の行を見ない。
  - 監査の行の読み方の前例は `useradmin/web/UserAdminListApiIT.java` 360〜368 行（`audit_events` を `ORDER BY audit_event_id OFFSET ? ROWS` で読み、`ACCESS_DENIED NOT_ADMIN <path>` の形で比べる）。`UserAdminOperationsApiIT` も `audit_events` の件数を数える問い合わせ（65 行）を持つ。
  - 見立て: 同じクラスに監査の確かめを足せば済み、本体の変更は要らない見込み。
- 状態: at-risk（K-21 はテストだけ。K-24 の確かめの対象の外） ／ 読みの深さ: 深い（今回は一意の制約に当たる処理が無いことと、テスト2クラスの該当の部分だけ）

### mail

- 場所: `mail/{config,domain,service,template,transport}`（約 1,880 行）
- 責務: SMTP の送信と Mustache のテンプレート（エンジンは `java-mustache-processor`）。アプリの中のほかのパッケージを import しない。
- K-22 `MailConfigurationIT` は JVM 全体の標準出力を捕まえる（2026-10-04 に確かめた事実）:
  - `backend/src/test/java/cherry/mastersmith/mail/config/MailConfigurationIT.java` は `@ExtendWith(OutputCaptureExtension.class)` で、`CapturedOutput.getOut()` を `JsonLogRecords.parse` で JSON として読み、`MailConfig` のロガーの WARN・INFO を `singleElement()` で確かめ、`getAll()` に秘密の値が無いことを確かめる。
  - `CapturedOutput` は JVM 全体の標準出力を捕まえる。前の Intent の B4 の関門で、別のテストの文脈の背景のスレッドが出した OTLP の指標の送信の WARN を取り込んで1回落ちた（その Intent の `test-results.md` の記録。単独では通過）。
  - `OutputCaptureExtension` を使うテストのファイルは `backend/src/test/java` に 31 ある（`JsonLogRecords` を含む。アーキテクトが検索で数えた。中身は読んでいない）。
- K-22（見立て、未検証）: どの確かめ（JSON でない行での `parse` か、`singleElement` か）で落ちたかは記録に無い。捕まえた行を自分の文脈のもの（スレッドの名前やロガー）に絞るか、別の文脈が止まるのを待つ形が考えられる。同じ形のほかのテストにも同じ危険がありうる（数だけ確かめた）。
- 状態: at-risk（K-22、テストの不安定さ） ／ 読みの深さ: 深い（今回は `mail/config/MailConfigurationIT.java` だけ）。本体は流し読み

### appearance

- 場所: `appearance/{config,service,web}`（約 580 行）
- 責務: インスタンスの見た目の設定を `GET /api/appearance` で画面に渡す（差し込み口 order 410）。
- 依存: `common-security`
- 状態: healthy ／ 読みの深さ: 流し読み

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`
- 責務: 対象DB の接続とスキーマの読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み

### dsl

- 場所: `dsl/{domain,parse,validate,service}`
- 責務: DSL の型・安全な読み込み・検証・適用中のモデル。
- 状態: healthy ／ 読みの深さ: 流し読み

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`
- 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴（`/api/admin/dsl`）。要求の文脈の読み取り `web/DslRequestContextResolver.java` を持つ（ファイル名だけ）。
- 依存: `dsl`・`targetdb`・`user`（service の口）・`auth`（domain・web）・`common-error`・`common-i18n`・`common-web`
- 状態: healthy ／ 読みの深さ: 流し読み

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/`（318 ファイル）
- 責務: 機能ごとの `testsupport`（`auth/testsupport/MutableClock`・`AuthApi`・`AuthTestTokens`、`access/testsupport/AdminTestUsers`・`PublicApiTestRules`、`audit/testsupport/AuditRows`、`user/testsupport/TestUserAccounts`・`MeApi` ほか）と共通の `common/testsupport`（`LogEvents`・`JsonLogRecords`・`HttpTestClient`・`TestDatabase` ほか）、アプリ全体を起動する結合テスト。境界の検査（`ArchitectureTest`・`*BoundaryArchitectureTest`）の中身は `build-and-verify` と K-3。
- 状態: healthy ／ 読みの深さ: 流し読み（ファイル名の一覧だけ）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/pages/`・`app/login-handoff/`・`app/login-state/`・`shared/validation`・`shared/format`
- 責務: 画面の起動と骨組み、共通の画面（NotFoundPage など）、日時の書式、氏名とパスワードの確かめ。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-registry

- 場所: `app/registry/`・`app/navigation/`
- 責務: `features/<featureId>/registration.ts` を置くだけで機能を読み込む仕組みと、サイドバー・ユーザーメニューの項目。本文は K-9（`code-structure.md`）。
- 状態: healthy ／ 読みの深さ: 流し読み（前回 `31b980b` は深い。今回は確かめ直していない。前回の範囲: `app/registry/types.ts`・`app/navigation/navigationItems.ts`・`features/README.md`）

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/routing/`
- 責務: AppShell の配置、表示言語、振り分け。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-display-settings

- 場所: `app/display-settings/`
- 責務: 利用者の表示の設定とインスタンスの見た目の設定を解決し、make-you-chic-ui の `ThemeProvider` に渡す。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`
- 責務: 同じオリジンの `/api/**` の呼び出し、トークンの付与、401 `AUTHENTICATION_REQUIRED` で1回だけ更新して送り直す、Problem Details の読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み（401・403 の検索だけ）

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログインの画面とログインの状態。`authSession.ts` がログインと更新の応答から `LoginState`（`admin` を含む）を作ってモジュールの変数に持つ（K-4）。
- 状態: at-risk（管理者の印の変更の後に古い値が残る。K-4） ／ 読みの深さ: 流し読み（前回 `31b980b` は深い。今回は確かめ直していない。前回の範囲: `authSession.ts` だけ）

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理の入口の画面 `/admin`（`registration.ts` の order 200）。確認用 API の 403 を「ページが見つかりません」の表示にする（`adminAreaStatus.ts` 23〜27 行）。
- 状態: healthy ／ 読みの深さ: 流し読み（前回 `31b980b` は深い。今回は確かめ直していない。前回の範囲: `registration.ts`。`adminAreaStatus.ts` は 403 の検索だけ）

### frontend-feature-dsl

- 場所: `features/dsl/`
- 責務: DSL の管理画面 `/admin/dsl`（order 210）。`useDslAdmin.ts` は 492 行。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-invitation

- 場所: `features/invitation/`
- 責務: 管理者の招待の画面 `/admin/invitations`（order 220）。make-you-chic-ui の `Table`・`Badge`・`Button`・`Modal`・`Toast`・`Alert` を使う、利用者の管理の画面の見本（K-9）。状態の管理は `useInvitationAdmin.ts`（472 行）。
- 状態: healthy ／ 読みの深さ: 流し読み（前回 `31b980b` は深い。今回は確かめ直していない。前回の範囲: 一部。`registration.ts` と `api/invitationApi.ts` の 1〜80 行。`InvitationList.tsx` は冒頭と `import` だけ）

### frontend-feature-useradmin

- 場所: `features/useradmin/`（Intent `260930-user-admin` で作られた。`registration.ts` の order 230）
- 責務: 利用者の管理の画面（一覧 `UserTable.tsx`・検索 `UserSearchBox.tsx`・行の「操作」`UserRowActions.tsx`・確かめの表示 `ConfirmActionDialog.tsx`・氏名と言語の入力 `EditProfileDialog.tsx`）。状態の管理は `useUserAdmin.ts`、画面の組み立ては `UserAdminPage.tsx`、フォーカスの行き先は `focusTarget.ts`。
- K-17 閉じた後のフォーカスの戻し（2026-10-04 に確かめた事実。流れは `architecture.md` の Interaction Diagrams 5）:
  - `ConfirmActionDialog.tsx`・`EditProfileDialog.tsx` は状態が `null` のとき `null` を返して Modal ごと外す（`open` を偽にするのではない）。どちらも `initialFocusRef` だけを渡し、閉じた後の行き先は渡していない。
  - 行の「操作」（`UserRowActions.tsx`）は make-you-chic-ui の `Dropdown` の `trigger` に `Button` を渡す。`Dropdown` は trigger を `cloneElement` し、`ref` を自分の関数に、`data-testid` を `dropdown-trigger` に置き換える。項目を選ぶと `item.onClick` の後に `close(true)` で trigger へフォーカスを戻す。
  - 成功したとき（`useUserAdmin.ts`）は `reload({ focus: { kind: 'row', userId } })` で、読み直しの後に `UserAdminPage.tsx` の効果が `actionRefs`（行の「操作」の `span`）の `querySelector('button')` へフォーカスを当てる。やめる・閉じる（`closeConfirm`・`closeEdit`）は状態を `null` にするだけで、行き先を Modal の戻しに任せる。
  - 単体テスト `UserAdminPage.test.tsx`（600〜618 行）は、やめた後に行の「操作」へ戻ることを `waitFor` で確かめて通っている。E2E は閉じた後の行き先を確かめていない（K-18、`frontend-e2e`）。
- K-17（見立て、未検証）: jsdom は `inert` でフォーカスを止めないため単体テストでは戻り、実際のブラウザだけで body に落ちていた。make-you-chic-ui の固定先を `e82b651` に上げれば、やめる・閉じる場合は開く前の要素へ戻る見込み。`finalFocusRef` を使うなら、`Dropdown` が trigger の `ref` を置き換えるため上流の手引きの例（`<Button ref={menuTriggerRef}>`）では埋まらないおそれがあり、既存の `actionRefs` から button を引く、描画ごとに作り直さない ref（`useRef` で持ち `.current` を更新）を渡す必要がある（`useFocusTrap` の効果は `[active]` だけに依存し、効果を張った時点の `onDeactivate` が後始末で呼ばれるため）。成功の場合は Modal の戻しと読み直しの後の効果の2つがフォーカスを動かすため、最後の行き先を E2E で確かめる。
- K-19 送信中も言語の欄を選び直せる（2026-10-04 に確かめた事実）: `EditProfileDialog.tsx` は送信中（`status === 'submitting'`）に氏名の `TextInput` を `readOnly` にするが、言語の `RadioGroup` には何も渡さない。`onChangeLanguage` は `useUserAdmin.ts` の `setEdit` で状態を書き換える。make-you-chic-ui の `RadioGroup` は `disabled?: boolean` を受けて各 `Radio` に渡す（固定先 `3d9521a` で確かめた）。`EditProfileDialog.test.tsx` には氏名だけの「送信中は読み取りだけ」のテスト（187〜193 行）があり、言語のテストは無い。
- K-19（見立て、未検証）: `disabled` にすると送信中に言語の欄にあったフォーカスが外れうる（`ConfirmActionDialog` の「やめる」の `disabled` と同じ扱い）。`disabled` か、送信中の `onChange` を無視する形かは設計で決める。
- K-18 のメニューのはみ出しの直し（`placement`）もこの部品の `UserRowActions.tsx` に当たる（本文は `frontend-e2e`）。
- 状態: at-risk（K-17・K-18・K-19） ／ 読みの深さ: 深い（一部。上に挙げたファイルの該当の部分と、テスト2本の該当の部分）

### frontend-feature-registration

- 場所: `features/registration/`
- 責務: 招待を受けた人のリンクの確かめと登録の完了の画面（ログインなし）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-preferences

- 場所: `features/preferences/`
- 責務: 自分の設定とパスワードの変更の画面（ユーザーメニューの order 80・90）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-e2e

- 場所: `frontend/e2e/`（2026-10-04 の時点で `*.e2e.ts` 13 本（010〜130）と `support/` の補助 19 ファイル。ファイルの一覧で数えた）
- 責務: Playwright の E2E（`./gradlew e2eTest`）。`030-admin-access` が管理の入口、`060-invitation-accessibility` が招待の管理の画面の axe、`090-invitation-registration-flow` が代表の流れ、`100-app-text-contrast` が文字のコントラスト、`110-user-admin-flow` が利用者の管理の代表の流れ、`120-user-admin-accessibility` が利用者の管理の axe と画面の時間、`130-admin-forbidden-accessibility` が管理の画面の 403（名前だけ）。`verify` と CI の外。補助 `support/adminLogin.ts`・`invitationSeed.ts`・`registeredUser.ts`・`userAdminFixtures.ts`・`overflow.ts` ほか。
- K-18 閉じた後のフォーカスと、開いたメニューのはみ出しを確かめていない（2026-10-04 に確かめた事実）:
  - `110-user-admin-flow.e2e.ts` の `confirmAction`（124〜133 行）と `120-user-admin-accessibility.e2e.ts` の `expectBackgroundInteractive`（173〜178 行）は、閉じた後に `body > [inert]` が 0 件になるのを待つだけで、閉じた後のフォーカスの行き先を確かめない（`toBeFocused` は「やめる」に対してだけ）。K-17 の不具合（`frontend-feature-useradmin`）を拾えなかった。
  - はみ出しの判定 `support/overflow.ts` は `document.documentElement.scrollWidth > window.innerWidth` だけを見る。メニューを開いた状態（`02-self-menu-open`・`11-disabled-item-hover`・`11-disabled-item-focus`）でも同じ判定。
  - 行の「操作」の `Dropdown`（`frontend/src/features/useradmin/UserRowActions.tsx`）は `placement` を渡さず既定の `bottom-start` になる（`'bottom-start' | 'bottom-end'` を受ける）。メニューは `createPortal` で body に置かれ `position: fixed`（make-you-chic-ui の `Dropdown.css`）、位置は `computeFloatingPosition(triggerRect, menuRect, placement)` で決まる。`frontend/src` で `Dropdown` を使うのは `UserRowActions.tsx` だけ。
- K-18（見立て、未検証）: `position: fixed` の要素は `scrollWidth` に入らないため、メニューが右へはみ出しても今の判定は通る。開いたメニューの `getBoundingClientRect()` の左右の端が画面の中にあることを確かめる関数を `support/overflow.ts` に足し、閉じた後に行の「操作」が `toBeFocused` であることを 110 と 120 の閉じる場面で確かめる形が要る。`computeFloatingPosition` が端で寄せるか（clamp・flip）は読んでいない。`project.md` の Testing Posture の学び（Dropdown は `bottom-end`）と合う。
- 状態: at-risk（K-18） ／ 読みの深さ: 深い（今回は `110-user-admin-flow.e2e.ts`・`120-user-admin-accessibility.e2e.ts`・`support/overflow.ts`）。ほかは流し読み

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `3d9521aa54b1d6277de473f9e935a496fb56ac1b`、2026-10-04 に `git ls-files -s` で確かめた。前回の記録の `077f5b4` は古い）。画面からは `file:` の依存で使う。
- 責務: デザインシステム。部品は Alert・AppShell・Avatar・Badge・Button・Card・Checkbox・Dropdown・FormField・Icon・Modal・RadioGroup・Select・Switch・Table・Tabs・Textarea・TextInput・Toast・Tooltip。中身はこのリポジトリから変えない（`project.md` の Forbidden）。
- 上流のコミット `e82b651`（「Modal: 閉じた後にフォーカスがbodyに落ちる不具合を修正、finalFocusRefを追加」）は手元のサブモジュールに取得済みで、固定していない（2026-10-04 に `git -C vendor/make-you-chic-ui log -1` で確かめた。上流に公開済みかは確かめていない）。開発担当が読んだ差（流し読み）: 固定先の `useFocusTrap` は後始末で `previouslyFocused?.focus()` を直接呼ぶ。`e82b651` はこれをやめ、`onDeactivate` で開く前の要素を Modal に渡し、Modal が `ModalStackContext` の inert を外す効果の中で `finalFocusRef?.current ?? previouslyFocused` へ戻す。`finalFocusRef` は新しい任意の prop。変えたのは `Modal.tsx`・`ModalStackContext.tsx`・`useFocusTrap.ts`・`Modal.test.tsx`・`docs/integration-guide.md` だけで、`Dropdown.tsx` は同じ。使う側の注意は K-17（`frontend-feature-useradmin`）。
- 固定先の更新は専用のコミットで、更新前後のハッシュを記録する（`project.md` の Mandated、`team.md` の Way of Working の fast-forward の統合）。
- 状態: at-risk（K-17 の直しで固定先を上げる見込み） ／ 読みの深さ: 流し読み（固定先と、`Modal`・`Dropdown`・`RadioGroup`・`Dropdown.css` と `e82b651` の差）

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、固定先 `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`、`0.1.0`）。Gradle の composite build で `cherry-mustache-core` として使う。
- 責務: メールのテンプレートの Mustache のエンジン。今回の Intent では触れない見込み。
- 状態: healthy ／ 読みの深さ: 流し読み（固定先だけ）

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`backend/config/spotbugs-exclude.xml`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vitest.config.ts`・`config/npm-build-tools.txt`・`.github/workflows/ci.yml`・`.github/dependabot.yml`、境界の検査 `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java` と `*BoundaryArchitectureTest`
- 責務: 1コマンドの検査 `./gradlew verify`（`architecture.md` の Interaction Diagrams 4）、E2E の `./gradlew e2eTest`、WAR の組み立て（画面の `dist` を同梱）、依存の取得元を Maven Central だけにする `RepositoriesMode.FAIL_ON_PROJECT_REPOS` と composite build（`settings.gradle.kts`）、lockfile による版の固定、テストの分け方（`test` と `integrationTest`）、カバレッジの下限（JaCoCo の全体とパッケージごと、`packagesJudgedByTotal`、K-7）、SpotBugs の関門 `spotbugsGate`（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`、除外は理由つきの1件）、Gitleaks・OSV-Scanner、Dependabot（gradle・npm・github-actions・docker）。
- 状態: at-risk（K-7） ／ 読みの深さ: 流し読み（前回 `31b980b` は深い。今回は確かめ直していない。前回の範囲: `backend/build.gradle.kts`・`spotbugs-exclude.xml`・`libs.versions.toml`・`settings.gradle.kts`・`dependabot.yml`・`frontend/package.json`・`vitest.config.ts` の下限の行と、境界の検査の決まりの名前と対象。ルートの `build.gradle.kts` と `ci.yml` は検索だけ）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`
- 責務: WAR をコピーするだけのイメージ（`eclipse-temurin:25.0.4_7-jre-noble`）、`app` のサービスと、profile で起動する監視・メールの受け手・見本の対象DB。
- 状態: healthy ／ 読みの深さ: 流し読み（イメージの行だけ）

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験（`perf/k6/scenarios.js`、イメージ `grafana/k6:2.3.0`）と使い捨ての環境（`docker/perf/compose.yaml`）、手順（`perf/README.md`）、手元の監視の警報とダッシュボード（`docker/monitoring/`）。
- K-20 プールの上限 10 の (B) が上限に届かず、警報3件が鳴らなかった（2026-10-04 に確かめた事実）:
  - `perf/k6/scenarios.js` の `userAdminOpsRound`（867〜879 行）は、印を付ける → 外す → 止める → 解く → 準備のログインの失敗（`/api/auth/login` に誤ったパスワード、`tags.name = userAdminPrepLogin`。`check` に数えないが要求は送る）→ 失敗回数を戻す、の順。
  - `perf/README.md`（321〜337 行）の上限 10 の手順は、使い捨てのアプリの `app.env` に `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` を足し、`SCENARIO=userAdminOps` を `VUS=5`（A）・`VUS=10`（B）で流し、`/actuator/metrics` の `hikaricp.connections.timeout`・`hikaricp.connections.acquire` を読む。
  - `application.yaml`: `maximum-pool-size: ${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}`・`connection-timeout: 5000`、指標の送信の周期 `step: 60s`（306 行）。
  - 警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）: `ms-error-logs` は `sum(increase(logback_events_total{service_name="mastersmith",level="error"}[5m])) > 5`（`for: 0s`）、`ms-audit-fail` は Loki の `sum(count_over_time({service_name="mastersmith"} |= "監査イベントの記録に失敗しました" [1h])) > 0`（`for: 0s`）、`ms-pool-pending` は `max(hikaricp_connections_pending{service_name="mastersmith"}) > 0`（`for: 1m`）。
  - 前回の (B) は時間切れ 0・待ちの最大 6.0 ms で、警報3件は 22 回すべて inactive だった（Intent `260930-user-admin` の `nfr-validation-matrix.md`）。
- K-20（見立て、未検証）: 1組の時間の大半が準備のログインの bcrypt で、操作の同時の数が上限に届かなかった。準備のログインを外す新しい場面か、ロックの状態の行を `setup` で作る形、または上限をさらに下げる形（4〜6）で同時の数が上がる見込み。`ms-pool-pending` は 1 分ごとのゲージの瞬間の値で `for: 1m` を求めるため、待ちが送信の時点をまたいで 1 分以上続く負荷でないと鳴りにくい（式の見直しは前の Intent で「配備先が決まったとき」に持ち越し）。接続の待ちが 5 秒を超えて監査の2本目を借りられないと、監査の失敗の ERROR（`audit`）が出て `ms-error-logs`・`ms-audit-fail` が鳴る見込み。409 `USER_ADMIN_BUSY` は行の排他の待ちの上限切れで出るもので、プールの待ちだけでは出ないかもしれない。
- K-23 `perf/README.md` の hikaricp の単位（2026-10-04 に確かめた事実）: 259・307・330 行は `hikaricp.connections.acquire` を「借りるまでの待ちの最大」として読むが、単位（`/actuator/metrics` の応答の `baseUnit`）の注意書きが無い。前の Intent の Performance Validation で、外部エクスポートの有無で秒とミリ秒が変わったと記録されている。直す場所は 259 行の段落と 307・330 行の近く。
- 状態: at-risk（K-20・K-23） ／ 読みの深さ: 深い（今回は `perf/README.md` の接続プールの節、`perf/k6/scenarios.js` の利用者の管理の場面、警報の3件だけ）。ほかは流し読み
