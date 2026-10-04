## Developer Code Scan Results

- 対象: Intent 261004-safety-carryover（scope bugfix・深さ Minimal）。走査の時点の develop の HEAD は `47ec27b3efb54a60187d7f47ad9ce72041088154`（依頼の記載。git は実行していない）。
- 走査の方法: Read・Grep と読み取りだけの `ls`・`cat`・`sed`・`grep`。git・`./gradlew`・npm・docker は実行していない。`.env` は開いていない。秘密情報・メールアドレスの実値は記録していない。
- 記号: 「事実」はソースで確かめたこと、「見立て（未検証）」は確かめていない仮説。部品の ID は前回の `aidlc/spaces/default/codekb/mastersmith2/component-inventory.md` の 38 個を使う。所見の番号は前回の K-24 に続けて K-25 から振る。

### Scan Coverage

依頼者は Full rescan（スナップショットの paths は `./`）を選んだが、深さ Minimal のため、全体の構成はディレクトリの単位で把握し、深く読んだのは今回の Intent の6件（S2・P1・BUSY の traceId・Tomcat の ERROR・言語の欄のフォーカス・片付け2件）に関わる範囲だけである。記録上の範囲は partial が正しい。

- **Analyzed deeply**:
  - backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java
  - backend/src/main/java/cherry/mastersmith/user/service/InitialAdminProperties.java
  - backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java（`verifyPassword`・`createUser`・`existsByEmail`・`lockAdminRowsInIdOrder`・`lockUserRow` の範囲）
  - backend/src/main/java/cherry/mastersmith/user/service/UserCreatedEvent.java
  - backend/src/main/java/cherry/mastersmith/user/service/UserSummary.java
  - backend/src/main/java/cherry/mastersmith/user/service/UserAccountConfig.java
  - backend/src/main/java/cherry/mastersmith/user/service/PasswordProperties.java
  - backend/src/main/java/cherry/mastersmith/user/repository/UserRowLockRepository.java
  - backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java（`findByEmail`・`existsByRedactedEmail` の範囲）
  - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java
  - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java
  - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java（`from(...)` の一覧と `sourceIp` の受け渡しの範囲）
  - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java
  - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java
  - backend/src/main/resources/db/migration/V4__u4_audit_event.sql（あわせて V6・V7 の `audit_events` の列の追加）
  - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
  - backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptStateInitializer.java
  - backend/src/main/java/cherry/mastersmith/auth/web/AccessTokenAuthenticationProvider.java
  - backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java（停止の判定の範囲）
  - backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminService.java（Busy の作り方の範囲）
  - backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminBarrier.java
  - backend/src/main/java/cherry/mastersmith/useradmin/web/UserAdminController.java（Busy から 409 への変換の範囲）
  - backend/src/main/java/cherry/mastersmith/common/persistence/RowLockFailures.java
  - backend/src/main/java/cherry/mastersmith/common/persistence/RowLockAttempt.java
  - backend/src/main/java/cherry/mastersmith/common/error/web/GlobalExceptionHandler.java（例外の受け口とログの範囲）
  - backend/src/main/java/cherry/mastersmith/common/error/web/ErrorPathController.java
  - backend/src/main/resources/application.yaml（初期管理者・bcrypt・接続プールの範囲）
  - backend/src/main/resources/logback-spring.xml（MDC の項目の範囲）
  - backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java（テストの名前の範囲）
  - backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyApiIT.java（テストの名前と確かめの範囲）
  - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java
  - backend/build.gradle.kts（`packagesJudgedByTotal` とカバレッジの検証の範囲）
  - frontend/src/features/useradmin/EditProfileDialog.tsx
  - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/RadioGroup/RadioGroup.tsx
  - compose.yaml（イメージの行）
  - docker/perf/compose.yaml（イメージの行）
  - Dockerfile
  - .github/dependabot.yml
  - perf/k6/scenarios.js（場面の一覧と利用者の管理の場面の定義の範囲）
  - .env.example（初期管理者の項目の名前だけ。値は空）
  - README.md（初期管理者の記述の範囲）
  - gradle/libs.versions.toml（`[versions]` の範囲）
- **Skimmed only**:
  - backend/src/main/java/cherry/mastersmith/ のそのほかの機能（access・appearance・common の health・i18n・observability・paging・security・web、config、dsl、dslmanage、invitation、mail、targetdb）はディレクトリとファイルの名前の単位
  - backend/src/main/java/cherry/mastersmith/auth/ の domain・repository（`LoginAttemptStateRepository` の排他の時間の定数だけを grep で見た）
  - backend/src/test/java/cherry/mastersmith/（`*IT.java` 133 件・`*Test.java` 173 件、`*SecretLeakIT` 12 件の名前の一覧）
  - frontend/src/（`app/`・`features/`・`shared/` の構成、`*.test.ts(x)` 110 件の件数）、frontend/e2e/（13 本の名前）、frontend/package.json（主な版）
  - perf/README.md（`docker run` のイメージの行だけを grep）、perf/ui/
  - docker/（monitoring・otel-collector・targetdb・jmx・hikari-pool.sh などの名前の単位）
  - .github/workflows/ci.yml（`uses:` の行だけを grep）
  - vendor/java-mustache-processor/、vendor/make-you-chic-ui/ の RadioGroup 以外
  - 参考として、前の Intent の記録（260930-user-admin の incident-plan.md・nfr-validation-matrix.md・test-results.md、261003-user-admin-followup の requirements.md・t1-load-test-results.md）の該当の節。コードの事実ではないため、下の所見では「記録による」と書き分ける
- **深く読んだ部品の ID**: `user`・`audit`・`auth`・`useradmin`・`common-error`・`common-observability`（ログの項目の設定の範囲。`TraceAspect` は読んでいない）・`frontend-feature-useradmin`・`make-you-chic-ui`（RadioGroup だけ）・`container-runtime`・`build-and-verify`・`perf-and-monitoring`・`backend-test-support`（`TargetDbImages` だけ）
- **部品の ID の提案**: `common/persistence`（行の排他の結果の型と失敗の判定）と `common/paging` は、前回の inventory では `useradmin` の依存として名前だけ出てくるが、独立した見出しが無い。`common-persistence` を足すことを提案する（今回の BUSY の L4 の持ち主のため）。`common/paging` は小さいため `common-web` に寄せてもよい。

### Packages Found

- `backend`（Gradle のサブプロジェクト）— 実行可能 WAR のアプリ — Java 25・Spring Boot 4.1.1 — ルートパッケージ `cherry.mastersmith`。機能ごとのパッケージ: `access`・`appearance`・`audit`・`auth`・`common`（`error`・`health`・`i18n`・`observability`・`paging`・`persistence`・`security`・`web`）・`config`・`dsl`・`dslmanage`・`invitation`・`mail`・`targetdb`・`user`・`useradmin`。各機能の中は `web`・`service`・`domain`・`repository` と用途名の下位パッケージ（`invitation/lock`・`mail/transport` など）
- `frontend` — SPA — TypeScript・React 19.2 — `src/app/`（骨組み・表示の設定・i18n・登録の仕組み・403 の画面など）、`src/features/`（`admin`・`auth`・`dsl`・`invitation`・`preferences`・`registration`・`useradmin`）、`src/shared/`、E2E は `e2e/`（Playwright、13 本）
- `vendor/make-you-chic-ui` — Git サブモジュール — TypeScript・React — デザインシステム。npm の `file:` の依存として frontend が使う
- `vendor/java-mustache-processor` — Git サブモジュール — Java — メールのテンプレートの Mustache のエンジン。Gradle の composite build（`settings.gradle.kts` の `includeBuild`）で組む
- `perf/` — 負荷の試験の道具 — JavaScript（k6）・Node・シェル — `perf/k6/scenarios.js` に場面を `SCENARIO` で選ぶ形でまとめてある
- `docker/` — 実行環境の付属 — YAML・シェル — 負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`）、手元の監視（`docker/monitoring`・`docker/otel-collector`）、見本の対象DB の初期化（`docker/targetdb`）、接続プールの運用の道具（`docker/hikari-pool.sh`・`docker/jmx`）

### Build System

- **Type**: Gradle（Kotlin DSL）。ルートのタスク `verify` が1コマンドの検査の入口（フロントエンドの検査・Gitleaks・OSV-Scanner も Gradle の `Exec` で呼ぶ）。E2E は `e2eTest`（`verify` の外）
- **Config Files**: `settings.gradle.kts`（`include("backend")`・`includeBuild("vendor/java-mustache-processor")`）、`build.gradle.kts`（ルート。`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`mustacheVendorUnchanged`・`frontendInstall`・`gitleaksScan`・`osvScan`・`verify`・`e2eTest`）、`backend/build.gradle.kts`（JaCoCo の下限・Spotless・SpotBugs）、`gradle/libs.versions.toml`、`backend/gradle.lockfile`・`settings-gradle.lockfile`、`frontend/package.json`・`frontend/package-lock.json`、`Dockerfile`、`compose.yaml`、`docker/perf/compose.yaml`
- **Build Dependencies**: `backend` → `vendor/java-mustache-processor`（composite build）、ルートの WAR の組み立て → `frontend` のビルド結果（`dist`）→ `vendor/make-you-chic-ui` のビルド

### APIs Discovered

- REST（Spring MVC の `@RestController`）— `backend/src/main/java/cherry/mastersmith/*/web/` — 対応づけの注釈の数（クラスの単位の `@RequestMapping` を含む概数）: `useradmin` 8・`dslmanage` 10・`invitation` 8（管理 5・登録 3）・`auth` 4・`user`（`/api/me`）4・`access` 1・`appearance` 1・`common/error`（問題の種類の説明 1・`/error` 2）
- 監査は API を持たず、Spring の出来事（`@TransactionalEventListener` の `AFTER_COMMIT`・`fallbackExecution = true`）で受ける
- 運用の口: Actuator の health、HikariCP の JMX（`docker/hikari-pool.sh`）

### Frameworks & Libraries

- Spring Boot — 4.1.1 — アプリの基盤（Web・Security・Data JPA・Actuator・Flyway）
- 組み込みの Tomcat — 11.0.26 — Spring Boot の管理の版を脆弱性のため上書き（`gradle/libs.versions.toml` のコメント）
- Jackson — 3.1.7 — BOM（platform）で Spring Boot の管理の版を上書き
- H2 — 内部DB（`jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE`）
- HikariCP — 接続プール（上限の既定 30、借りる待ちの上限 5000 ms、`application.yaml`）
- Spring Security の `BCryptPasswordEncoder` — パスワードのハッシュ（cost の既定 12、`MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST`）
- logstash-logback-encoder — 9.0 — 1行1件の JSON のログ（MDC のうち `traceId`・`spanId` だけを出す、`logback-spring.xml`）
- OpenTelemetry（logback の出力 2.28.1-alpha に固定）・Micrometer — トレースと指標
- SnakeYAML 2.7・networknt json-schema-validator 3.0.6 — DSL の読み込みと検証
- java-mustache-processor 0.1.0（サブモジュール）— メールのテンプレート
- React 19.2・Vite 8.3・TypeScript 6.0・make-you-chic-ui（サブモジュール）— 画面
- k6 `grafana/k6:2.3.0` — 負荷の試験（`perf/README.md` の `docker run`）

### Test Coverage

- **Test Directories**: `backend/src/test/java`（対象と同じパッケージ構成、`*Test` 173 件・`*IT` 133 件。テストの手伝いは `<機能>/testsupport`）、`frontend/src/**/*.test.ts(x)`（110 件、対象と同じ場所）、`frontend/e2e/`（13 本）
- **Test Frameworks**: JUnit 5・Spring Boot Test・Testcontainers（対象DB の MySQL・MariaDB・PostgreSQL）・ArchUnit 1.5.1・jqwik 1.10.1・subethasmtp 7.2.2（JVM の中の SMTP の受け手）、Vitest 5・Testing Library・vitest-axe・fast-check、Playwright 1.63
- **Coverage Config**: present。`backend/build.gradle.kts` の `jacocoTestCoverageVerification` が、全体の合計と、`packagesJudgedByTotal` 以外のすべてのパッケージごとに行 80%・分岐 70% を検証する。フロントエンドは `frontend/vitest.config.ts` の `thresholds`（今回は読んでいない）

### Code Quality Indicators

- **Linting**: バックエンドは Spotless（palantir-java-format・ライセンスヘッダー）と SpotBugs ＋ FindSecBugs（除外は `backend/config/spotbugs-exclude.xml`）。フロントエンドは Prettier・oxlint・ESLint（`frontend/eslint.config.js`）・Stylelint。秘密情報は Gitleaks（`.gitleaks.toml`、pre-commit と `verify`）。依存の脆弱性は OSV-Scanner（`verify`）
- **CI/CD**: `.github/workflows/ci.yml`（GitHub Actions。`uses:` はすべてコミットのハッシュで固定し、版をコメントに書いている）。Dependabot は `.github/dependabot.yml`（gradle・npm・github-actions・docker・docker-compose）
- **Documentation**: `README.md`（1,188 行。起動・配備・設定の一覧・既知の制約）、`perf/README.md`（495 行）。Java のコメントは日本語の Javadoc で、要件・設計の ID（BR・NFR・FR）へのつながりが細かく書かれている

### Technical Debt Signals

今回の Intent の6件に関わる所見を K-25〜K-31 とする。各所見は「事実」と「見立て（未検証）」を分けて書く。

#### K-25 初期管理者の作成は監査に残らず、救済の口も無い（S2。部品 `user`・`audit`）

- 事実:
  - 初期管理者は `InitialAdminInitializer`（`SmartInitializingSingleton`。Flyway の後、Web が受け付けを始める前）が、設定（`mastersmith.auth.initial-admin.email`・`password`、環境変数 `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`）から作る。記録はアプリのログの INFO（キー `maskedEmail`）だけである（`InitialAdminInitializer.java` 92〜105 行）。
  - 作成の判定は `userAccountService.existsByEmail(...)` で、**そのメールアドレスの利用者がいれば、停止中でも管理者でなくても何もしない**（同 92〜95 行）。このため、今の救済（RB-22、記録による）は「別のメールアドレスに替えて新しい管理者を作る」形しか取れず、既にいる利用者の停止を解く・管理者の印を付ける口はアプリに無い。
  - `createUser` は同じトランザクションで `UserCreatedEvent(userId)` を知らせるが（`UserAccountService.java` 385 行）、受け取るのは `auth.service.LoginAttemptStateInitializer`（ロックの状態の行を作る `@EventListener`）だけで、`audit` は受け取らない。招待からの登録の完了は別の出来事（`REGISTRATION_COMPLETED`）で監査に残る。
  - `AuditEventType` に初期管理者の作成・救済に当たる種類は無い（20 種類。`LOGIN_SUCCEEDED`〜`LOGIN_FAILURES_RESET`）。
  - `audit_events.source_ip` は `NOT NULL`（`V4__u4_audit_event.sql`、`AuditEvent` の構築子も `requireNonNull`）。起動時の出来事には要求が無く接続元 IP が無いため、記録するには固定の値（例: 起動を表す印）を決めるか、列の扱いを変える移行が要る。`event_type` は `VARCHAR(32)` で CHECK の制約は無い（種類を足すのに表の変更は要らない）。
  - 監査の受け取り（`AuditEventListener`）は `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` で、書き込みは `AuditEventRecorder.record`（`REQUIRES_NEW`）。失敗は受け止めて ERROR を1回出し、呼び出し元へ伝えない。起動の途中（Web の受け付けの前）に知らせても、この仕組みのまま記録できる形である。
  - 依存の向きは `audit` → `user`（`audit` が `user` の出来事を受け取る）。`PasswordChangedEvent` は `user.domain` にあり、同じ形で新しい出来事を `user` 側に置けば境界テスト（`AuditBoundaryArchitectureTest` など）の向きは変わらない。
  - `InitialAdminProperties.toString()` はパスワードを `***` で伏せるが、**メールアドレスはそのまま文字列にする**（`InitialAdminProperties.java` 33 行）。
- 見立て（未検証）:
  - `InitialAdminProperties` が `TraceAspect` の対象の層の引数・戻り値として渡る経路は見当たらない（設定の部品のため）が、Spring の設定の結び付けの失敗のメッセージなどで文字列にされると、メールアドレスがログに出うる。救済の口を設けるときは、この `toString` も `EmailAddress.mask` の形にそろえるか確かめたい。
  - 救済を「起動時の設定で既存の利用者の停止を解く・印を付ける」形にするなら、`project.md` の Forbidden（最後の有効な管理者を無くす操作を受け付けない）とはぶつからない（管理者を増やす向きのため）。ただし、設定（`.env`）に残したまま再起動するたびに働く・働かないの決まりが要る。

#### K-26 ログインでの停止の判定は DB の読み取りを増やしていない（P1。部品 `auth`・`user`）

- 事実:
  - ログイン（`LoginService.login`）の流れ: (1) トランザクションと排他の外で `UserAccountService.verifyPassword`（`users` を `findByEmail` で1回読み、bcrypt で照合。利用者がいないときもダミーのハッシュで照合する）→ (2) `TransactionTemplate` の中で `login_attempt_state` の行を排他（待ちの上限 3000 ms）・判定・書き込み・トークンの発行（リフレッシュトークンの保存）→ (3) 確定の後に監査を `REQUIRES_NEW` で追記。
  - 停止の判定（`if (user.suspended())`、`LoginService.java` 189 行）は、(1) で読んだ `UserSummary` の値を見るだけで、**問い合わせを増やしていない**。`suspended` は `users` の列（V9）で、同じ1行の読み取りに含まれる。
  - bcrypt の cost は既定 12（`PasswordProperties`、`application.yaml` の `bcrypt-cost`）。
  - `perf/k6/scenarios.js` の `loginSuccess` は「別々の利用者 10 名のログイン」で、記録によれば台本に閾値を持たず p95 を k6 の結果から読んで判定している（260930-user-admin の test-results.md 2節）。
- 見立て（未検証）:
  - ログインの時間の大半は bcrypt（cost 12）の計算で、停止の判定の追加は CPU の時間に対して無視できる大きさと見る。939.6 ms と前の Intent の 904 ms の差（36 ms）は、PC の負荷やコンテナの CPU の割り当てのぶれの範囲の可能性が高い。
  - 同じ条件で切り分けるには、停止の判定を入れる前の版のイメージ（前の Intent の配備の記録にある直前の版）と今の版を、同じ使い捨ての環境・同じ上限・同じ `VUS`・`DURATION` で交互に複数回流し、p95 の分布を比べる形が考えられる（1回ずつの比較ではぶれと区別できない）。

#### K-27 409 USER_ADMIN_BUSY は行の排他の待ちが 3 秒を超えたときだけ起き、負荷だけでは起きにくい（持ち越し FR4.2-c。部品 `useradmin`・`user`・`common-error`・`common-persistence`）

- 事実:
  - BUSY は、`UserRowLockRepository` の排他つきの読み取り（`PESSIMISTIC_WRITE`、`jakarta.persistence.lock.timeout` 3000 ms）が待ちの上限切れ・行き詰まりになったときに、`RowLockFailures.isLockFailure` で見分けて `RowLockAttempt.Busy` を返し、`UserAdminService` が巻き戻しの印を付けて `OperationResult.Busy` を返し、`UserAdminController` が `BusinessException(UserAdminProblemTypes.BUSY)` に変える（409）。失敗回数を戻す操作は `LoginAttemptStateRepository`（同じく 3000 ms）を通る。
  - BUSY の2行のログ（記録の呼び名 L3・L4）は、コードでは次の2つに当たる。
    - L4: `RowLockFailures.warn` の WARN「行の排他を取れませんでした」（キー `lockKind`・`exceptionClass`）。排他を試みた要求のスレッドで出る。
    - L3: `GlobalExceptionHandler` の WARN「要求をエラー応答に変換しました」（`code` に `USER_ADMIN_BUSY`）。同じ要求のスレッドで出る。
  - どちらも同じ要求のスレッドで出て、`logback-spring.xml` は MDC の `traceId`・`spanId` を各行に載せる。
  - 本番の待ち合わせの口（`UserAdminBarrier`）は何もしない部品（`NoOpUserAdminBarrier`）で、結合テストだけが差し替える。
  - 既存の `UserAdminBusyApiIT` は、別のトランザクションで対象の行を持ち続けて5つの操作の 409 を作るが、ログの L3・L4 の `traceId` の結び付きは確かめていない（grep で `traceId` が無い）。
  - 記録によれば、T1（261003-user-admin-followup）の接続の時間切れの負荷では 409 の 128 件の内訳は `USER_ADMIN_NO_CHANGE` 70・`USER_ADMIN_TARGET_SUSPENDED` 58・`USER_ADMIN_BUSY` 0 だった。
- 見立て（未検証）:
  - 5つの操作は1件 1 ms 前後（記録による）で、排他を持つ時間が短いため、本番のコードのまま k6 の負荷で 3 秒の待ちを作るのは難しい。接続の待ち（5 秒）が先に尽きて 500 になる経路の方が起きやすい。
  - 負荷の場面で BUSY を起こすには、同じ対象の行を長く持つ別の経路が要る（例: H2 の道具で同じ内部DB に別の接続から `SELECT ... FOR UPDATE` で行を持ち続ける。ただし組み込みの H2 のファイルは1つのプロセスからしか開けず、`AUTO_SERVER` も使っていないため、使い捨ての環境でも外から行を持つのは難しい）。結合テスト（`UserAdminBusyApiIT` と同じ作り方に、既存のテストの手伝い `common/testsupport/RowLockHolder.java`・`JsonLogRecords.java`（名前だけ確かめた）などでログの JSON を捕まえて L3 と L4 の `traceId` が1対1で一致することを確かめる）の方が確実と見る。

#### K-28 フィルターの中の例外は Tomcat の ERROR と `/error` の ERROR の2回出る（持ち越し: Servlet.service() の ERROR 239 件。部品 `common-error`・`auth`・`config`）

- 事実:
  - `GlobalExceptionHandler`（`@RestControllerAdvice`）は `Exception.class` まで受けて、5xx は ERROR「想定外のエラーが起きました」を1回出す。ただし、これは Spring MVC（`DispatcherServlet`）の中の例外だけを受ける。
  - フィルター（Spring Security など）やサーブレットコンテナで起きた例外は `/error` に回り、`ErrorPathController` が `RequestDispatcher.ERROR_EXCEPTION` を原因に付けて ERROR「想定外のエラーが起きました」を出す（`ErrorPathController.java` 84〜91 行）。
  - アクセストークンの認証（`AccessTokenAuthenticationProvider.authenticate`）は、フィルターの中で `userAccountService.findById(userId)`（`@Transactional(readOnly = true)`）を呼び、DB の接続を借りる。接続を借りられないときの例外（`CannotCreateTransactionException` など）は認証の失敗の例外（`TokenAuthenticationException`）に変えていない。
  - 記録によれば、T1 の ERROR の内訳は「想定外のエラーが起きました」456・Tomcat の `dispatcherServlet` のロガーの `Servlet.service() … threw exception` 239・監査の記録の失敗 206 だった。
- 見立て（未検証）:
  - 接続の時間切れの負荷では、アクセストークンの認証のフィルターで `findById` が接続を借りられずに例外になり、その例外がフィルターの連なりの外へ出て、Tomcat（`StandardWrapperValve`）が「Servlet.service() … threw exception」を ERROR とスタックトレースで出し、続いて `/error` の `ErrorPathController` が同じ例外でもう1回 ERROR を出す、と見る。そうなら 239 件は「想定外のエラーが起きました」456 件のうちの `/error` 経由の分と重なり、`team.md` の「例外のログは変換する境界で1回だけ出す」に反する二重の出力である。
  - 確かめ方の候補: 結合テストで、認証の要るAPIへの要求の間に `findById` の接続の取得を失敗させ（プールの上限を 1 にして持ち続けるなど）、Tomcat のロガー（`org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]`）の ERROR と `ErrorPathController` の ERROR が同じ `traceId` で2行出るかを数える。ログの JSON の `logger` の項目で区別できる。

#### K-29 言語の欄で Enter で送信すると、押せなくした選択肢からフォーカスが外れる（持ち越し。部品 `frontend-feature-useradmin`・`make-you-chic-ui`）

- 事実:
  - `frontend/src/features/useradmin/EditProfileDialog.tsx` は、送信中に氏名の欄を `readOnly={submitting}` にし（フォーカスは残る）、言語の `RadioGroup` を `disabled={submitting}` にする（前の Intent の FR3.1）。フォームは `onSubmit` で送るため、言語の選択肢にフォーカスがある状態で Enter を押すと送信になり、その選択肢自身が押せなくなる。
  - make-you-chic-ui の `RadioGroup`（`packages/make-you-chic-ui/src/components/RadioGroup/RadioGroup.tsx`）の props は `name`・`options`・`value`・`defaultValue`・`onChange`・`disabled`・`className`・`style`・`legend` だけで、`readOnly` に当たる口は無い。`disabled` は各 `Radio` にそのまま渡る。
  - `vendor/make-you-chic-ui` はこのリポジトリから直接変えられない（`project.md` の Forbidden）。
- 見立て（未検証）:
  - ブラウザは押せなくなった（`disabled`）要素にフォーカスを残さないため、フォーカスは `body` に落ちる。直し方の候補は、(a) 送信中も `disabled` にせず `onChange` で値の変更を受け付けない（見た目で押せないことが伝わらない）、(b) 送信を始めるときに保存のボタンなど押せるままの要素へフォーカスを移す、(c) make-you-chic-ui に読み取り専用の口を足してもらう（依頼者への相談が要る）。
  - 同じ形（送信中に `disabled` にする選択肢の欄）がほかの画面（プリファレンス・登録の完了など）にもあるかは、今回は確かめていない。

#### K-30 team.md の「12 パッケージ」は古く、一覧は今 7 個（片付け。部品 `build-and-verify`）

- 事実: `backend/build.gradle.kts` の `packagesJudgedByTotal`（225〜233 行）は次の **7 個**である: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`（いずれも `cherry.mastersmith.` の下）。`team.md` の Testing Posture は「2026-09-29 の時点で 12 パッケージ」と書いている。
- 今回の Intent への関わり: S2・Tomcat の ERROR の直しで手を入れそうなパッケージのうち、`user.service`・`audit.*`（`audit.repository` を除く）・`auth.*`・`useradmin.*`・`common.error.web`・`common.persistence` は一覧に無く、すでにパッケージごとの下限の対象である。`common.error.service`・`common.error.domain`・`common.web` に手を入れると、`team.md` の決まりでテストを足して一覧から外す作業が付く。

#### K-31 対象DB のイメージの固定先が3か所にあり、Dependabot は1か所しか見ない（片付け。部品 `container-runtime`・`backend-test-support`・`perf-and-monitoring`）

- 事実:
  - 同じ版とダイジェストが3か所に手で書かれている: `compose.yaml`（`postgres:18.6@sha256:5a5a…`・`mysql:26.7.0@sha256:ade0…`・`mariadb:13.0.2@sha256:d4fd…`）、`docker/perf/compose.yaml`（同じ3つ）、`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`（版の定数とダイジェストの定数。Testcontainers は `イメージ名@ダイジェスト` だけで起動し、版の定数は使っていない）。
  - Mailpit（`axllent/mailpit:v1.31.2@sha256:…`）も `compose.yaml` と `docker/perf/compose.yaml` の2か所にある。
  - ダイジェストの無いイメージ: `Dockerfile` の `FROM eclipse-temurin:25.0.4_7-jre-noble`、`compose.yaml` の `otel/opentelemetry-collector:0.162.0`・`grafana/otel-lgtm:0.34.0`、`perf/README.md` の `grafana/k6:2.3.0`・`eclipse-temurin:25.0.4_7-jdk-noble`。アプリのイメージ（`mastersmith:${MASTERSMITH_IMAGE_TAG:-local}`）は手元で作るもの。
  - `.github/dependabot.yml` の `docker`・`docker-compose` はどちらも `directory: /` だけで、`docker/perf/compose.yaml` と Java の定数（`TargetDbImages`）は対象に入っていない。`docker` の `eclipse-temurin` は大きな版の更新だけを見送っている。コメントに「見本の対象DB のイメージを上げるときは、結合テストのイメージの定数（TargetDbImages）と一緒に上げる」とある。
  - GitHub Actions の `uses:` はコミットのハッシュで固定済み（`.github/workflows/ci.yml`）。
- 見立て（未検証）:
  - 手での揃えを減らす形の候補: (a) 版とダイジェストを1つのファイル（例: `.env` 形式の固定のファイルや compose の `x-` の共通の定義）に置き、2つの compose と `TargetDbImages` がそれを読む、(b) 3か所が一致することを `verify` の中の小さな検査（または単体テスト）で確かめる、(c) Dependabot の `docker-compose` に `docker/perf` を足す（Java の定数は Dependabot では更新されないため、(b) と組み合わせる必要がある）。
  - `Dockerfile` の `FROM` にダイジェストを付けると、Dependabot の `docker` はダイジェストの更新も知らせる見込みで、小さな版の更新の知らせの頻度が上がりうる。

#### そのほかの兆し（今回の Intent の外。記録だけ）

- `GlobalExceptionHandler` と `ErrorPathController` に同じ文の ERROR があり、ログの件数だけでは経路を区別できない（`logger` の項目で区別する必要がある）。
- `audit_events` の列は追記のたびに NULL を許す列が増えており（V6・V7）、出来事ごとにどの列を使うかの決まりはコメントにしか無い。

## Handoff Summary

- **Intent-relevant finding**:
  - S2: 初期管理者の作成は監査に残らない。`AuditEventType` に種類が無く（20 種類）、`createUser` が知らせる `UserCreatedEvent` は `auth.service.LoginAttemptStateInitializer` しか受け取らない（`backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java` 385 行、`InitialAdminInitializer.java` 92〜105 行）。作成の判定は「そのメールアドレスの利用者がいれば何もしない」で、既にいる利用者の停止を解く・印を付ける救済の口は無い。監査に残すには、`audit_events.source_ip` が `NOT NULL`（`V4__u4_audit_event.sql`）のため、要求の無い起動時の出来事の接続元の値を決める必要がある（K-25）。
  - P1: ログインの停止の判定は、照合の前に読んだ `users` の1行の値を見るだけで、問い合わせを増やしていない（`LoginService.java` 189 行）。時間の大半は bcrypt（cost 12）と見る（K-26、見立て）。
  - BUSY: 409 `USER_ADMIN_BUSY` は行の排他の待ち 3000 ms の上限切れでだけ起き、L4（`RowLockFailures` の WARN）と L3（`GlobalExceptionHandler` の WARN）は同じ要求のスレッドで出て、どちらの行にも MDC の `traceId` が載る。既存の `UserAdminBusyApiIT` は BUSY を作れるがログの結び付きは確かめていない（K-27）。
  - Tomcat の ERROR: フィルターの中（アクセストークンの認証の `findById`）の例外は `@RestControllerAdvice` に届かず、Tomcat の ERROR と `/error`（`ErrorPathController`）の ERROR の2回になりうる（K-28、見立て）。
  - 言語の欄: `EditProfileDialog.tsx` の `RadioGroup` は送信中 `disabled`、make-you-chic-ui の `RadioGroup` には `readOnly` の口が無い（K-29）。
  - 片付け: `packagesJudgedByTotal` は 7 個（`team.md` は 12 と記述）（K-30）。対象DB のイメージの版とダイジェストは `compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages.java` の3か所に手で書かれ、Dependabot は `directory: /` だけを見る。`Dockerfile` の `FROM` と監視の2つのイメージはダイジェストなし（K-31）。
- **Risks / follow-up**:
  - S2 で監査の種類を足すときは、`audit` → `user` の依存の向き（出来事は `user` 側に置き、`audit` が受け取る）と、`AuditEventListener` の「失敗を受け止めて呼び出し元に伝えない」決まりを保つこと。起動の途中で監査の書き込みが失敗しても起動は続く形になる。
  - 救済の口を設定で動かす形にするときは、`.env` に残したまま再起動するたびに働くか（一度だけか）の決まりと、`InitialAdminProperties.toString()` がメールアドレスをそのまま出す点（K-25）を扱うこと。`project.md` の Forbidden（メールアドレスをアプリのログに含めない）とそろえる。
  - P1 の切り分けは、前の版のイメージと今の版を同じ条件で複数回交互に流さないと、ぶれと区別できない見込み（K-26）。負荷の試験は使い捨ての環境で行い、`caffeinate -i` で台本全体を包む（`project.md` の学び）。
  - BUSY の L3・L4 の確かめを負荷だけで行うのは難しい見込みで、本番のコードを変えない結合テスト（行を別のトランザクションで持ち続ける形）の方が確実と見る。どちらで確かめるかは要件で決める必要がある（K-27）。
  - Tomcat の ERROR の見立てはログの `logger` の項目と `traceId` で裏付けが要る。直すときは `common.error.web`（一覧の外）か `auth.web`（一覧の外）に手が入る見込みで、`common.error.service`・`common.error.domain`・`common.web`（一覧の中）に手を入れると、テストを足して一覧から外す作業が付く（K-28・K-30）。
  - 言語の欄の直しで make-you-chic-ui の変更が要るなら、サブモジュールの固定先の更新は承認を得た専用のコミットになる（`team.md`・`project.md`）。ほかの画面に同じ形があるかは確かめていない（K-29）。
  - イメージの固定先をまとめる・ダイジェストを足すときは、`project.md` の学び（`compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages` を一緒に上げる）と `.github/dependabot.yml` のコメントも合わせて直すこと（K-31）。
