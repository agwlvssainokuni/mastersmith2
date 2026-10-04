# コード生成の記録（261004-safety-carryover）

- 範囲: bugfix（深さ Minimal、Test Strategy は Minimal）。zero-Unit。作業ブランチ `fix/261004-safety-carryover`（`develop` の `25ec1b9` から）。
- 計画: `code-generation-plan.md`（Step 1〜18 は済み。Step 19 のコミットと統合はオーケストレーターが依頼者の承認を得て行う）。生成の途中の判断と結果の細かい記録は `generation-notes.md`。
- 進め方: 計画に埋め込まれた Testing Contract（test-after）のとおり、層ごとに実装を書いてから同じ層のテストを書いて流した。

## 1. 変えたファイル

本番のコード（Java）:

| ファイル | 変更 | 要件 |
|---|---|---|
| `backend/src/main/java/cherry/mastersmith/user/domain/InitialAdminRescueCondition.java`（新規） | 条件の列挙 `SUSPENDED`・`NO_ADMIN`・`PASSWORD` と、決まった順に `+` でつなぐ `code(Set)` | FR1.1・FR1.6a |
| `backend/src/main/java/cherry/mastersmith/user/domain/InitialAdminRescuedEvent.java`（新規） | 救済の出来事（利用者 ID・条件・日時だけ） | FR1.2・FR1.5 |
| `backend/src/main/java/cherry/mastersmith/user/domain/InitialAdminCreatedEvent.java`（新規） | 作成の出来事（利用者 ID・日時だけ） | FR1.4・FR1.5 |
| `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminRescueResult.java`（新規） | 救済の結果の型（`NotFound`・`NotNeeded`・`Rescued`） | FR1.1・FR1.3 |
| `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java` | `rescueInitialAdmin(RedactedText, Password)`（1つのトランザクションで判定・書き換え・出来事） | FR1.1〜FR1.3・FR1.2a・NFR3 |
| `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` | 救済の判定 → 救済・何もしない・作成。救済の WARN・救済の失敗の ERROR・作成の出来事 | FR1.1〜FR1.5・FR1.7・FR1.2a |
| `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminProperties.java` | `toString` でメールアドレスを伏せ字にする | NFR1 |
| `backend/src/main/java/cherry/mastersmith/auth/service/InitialAdminRescueListener.java`（新規） | 同じトランザクションで失敗回数 0・ロックの解除・リフレッシュトークンの無効化 | FR1.2(c)(e)・FR1.2a |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` | `INITIAL_ADMIN_CREATED`・`INITIAL_ADMIN_RESCUED` | FR1.5・FR1.6a |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java` | 起動時の行の組み立て `ofStartup`（列は足さない） | FR1.6・FR1.6a |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java` | `SYSTEM_SOURCE_IP = "system"`、2つの出来事の `from(...)` | FR1.5・FR1.6・FR1.6a |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` | `onInitialAdminCreated`・`onInitialAdminRescued`（確定の後・`fallbackExecution = true`） | FR1.5・FR1.8 |

設定・環境・文書:

| ファイル | 変更 | 要件 |
|---|---|---|
| `backend/src/main/resources/application.yaml` | Tomcat の `dispatcherServlet` のロガー（`[Tomcat]`）を `OFF`（理由・調べ方・失うものをコメントに） | FR4.2 |
| `Dockerfile`・`compose.yaml`・`perf/README.md`・`README.md`・`docker/hikari-pool.sh` | イメージを `タグ@sha256:<index のダイジェスト>` で固定（20 か所） | FR7.2・FR7.4 |
| `.github/dependabot.yml` | ダイジェストでの固定と、Dependabot が見ない固定先を手で揃える決まりのコメント | FR7.3 |
| `README.md` | 初期管理者の救済の手順、例外のログ、利用者の管理の画面の既知の点、監査の2つの種類 | FR1.9・FR4.2・FR5.1・R-08 |

テスト:

- 新規: `user/domain/InitialAdminRescueConditionTest`、`user/service/InitialAdminPropertiesTest`・`InitialAdminRescueIT`・`InitialAdminSecretLeakIT`、`user/testsupport/FailingInitialAdminRescueConfig`（手伝い）、`auth/service/InitialAdminRescueListenerTest`、`useradmin/web/UserAdminBusyLogTraceIT`、`common/error/web/FilterExceptionErrorLogIT`（すべて `backend/src/test/java/cherry/mastersmith/` の下）。
- 変更: `user/service/InitialAdminInitializerTest`・`UserAccountServiceTest`・`InitialAdminIT`、`audit/domain/AuditEventFactoryTest`、`audit/service/AuditEventListenerTest`、計画の外の `auth/domain/SecretTypesTest`・`auth/web/AuthSuspensionSecretLeakIT`（G1）。

一覧のパッケージ（`packagesJudgedByTotal` の 7 個）の `src/main` は変えていない。一覧も変えていない（7 個のまま）。移行（`db/migration`）・画面（`frontend/`）・`vendor/` は変えていない。

## 2. 主な判断

- 救済の置き場（P2・D2: A）: `user` が書き換えて `InitialAdminRescuedEvent` を知らせ、`auth.service.InitialAdminRescueListener` が `@EventListener`・`MANDATORY` で同じトランザクションの中で受ける。書き換えの口（`setAdmin`・`setSuspended`・`RefreshTokenRevocationService`・`LockAdministrationService`）は呼ばずにリポジトリを直接使う。依存の向きは変わらず、境界テストは変えていない。ただし `useradmin` を通らない2つ目の書き換えの経路になる（`UserAdminBoundaryArchitectureTest` の趣旨との差。依頼者が受け入れた）。
- 救済の条件の列（P1・D1: A）: `rejection_kind` に決まった順の `+` つなぎ（最大 27 文字）。作成の行は空。
- パスワードは条件 `PASSWORD` に当たらない救済でも設定の値で書き直す（D6: A）。照合は bcrypt 1回（NFR3。72 バイトを超えるなら照合せず不一致）。
- 救済の失敗の ERROR（FR1.2a）はキー `maskedEmail`・`exceptionClass` だけで、例外そのもの（スタックトレース）を渡さない。H2 の例外の文に行の値（メールアドレスを含む）が入りうるため。team.md の「想定外は ERROR でスタックトレース付き」との差。
- FR4.2 の直し（P4・D3: A）: `application.yaml` で Tomcat の `dispatcherServlet` のロガーを `OFF`。応答を書き始めた後にサーブレットの外へ出た例外のログは無くなる（`application.yaml` のコメントと README に明記）。
- `findByEmail`（Spring Data のインターフェースの口）は TRACE の追跡の行が出ず、TRACE でもメールアドレスが出ないことを `InitialAdminSecretLeakIT` で確かめた。

## 3. テストの結果（実測）

### 3.1 変更の前の基準（Step 2）

- 単体（`unit-test-instructions.md` 3.1 の「変更の前」、`--rerun`）: BUILD SUCCESSFUL。9 クラス 145 件、失敗 0・飛ばし 0（`ArchitectureTest` 5、`AuditBoundaryArchitectureTest` 6、`AuditEventFactoryTest` 50、`AuditEventTest` 6、`AuditEventListenerTest` 24、`AuthBoundaryArchitectureTest` 4、`InitialAdminInitializerTest` 8、`UserAccountServiceTest` 34、`UserAdminBoundaryArchitectureTest` 8）。
- 結合（3.2 の「変更の前」、`--rerun`）: BUILD SUCCESSFUL。2 クラス 9 件、失敗 0（`InitialAdminIT` 3、`UserAdminBusyApiIT` 6）。

### 3.2 段の途中の確かめ

- Step 4: 8 クラス 117 件、失敗 0。jqwik の種 `-4893468421919841831`（失敗時は `@Property(seed = "...")` で再現）。
- Step 9: 単体 13 クラス 187 件（`SecretTypesTest` 7 を含む）、失敗 0。結合 15 件（`InitialAdminIT` 3、`InitialAdminRescueIT` 9、`InitialAdminSecretLeakIT` 2、`AuthSuspensionSecretLeakIT` 1）、失敗 0。
- Step 10: `UserAdminBusyLogTraceIT` 1 件、失敗 0。L3 がちょうど2行（`ADMIN_ROWS`・`REFRESH_TOKEN_ROWS`）、各 L3 の `traceId`（応答と一致）に L4 がちょうど1行。
- 既存の `UserAdminSecretLeakIT` も、同じ `traceId` のアプリの WARN が2行あることを確かめている。FR3.1 の形（L3 ごとに L4 がちょうど1行）で確かめるのは `UserAdminBusyLogTraceIT`。

### 3.3 直す前の再現（Step 11）と直した後（Step 12・G2）

- 直す前（Step 11）: `FilterExceptionErrorLogIT` が落ちた（BUILD FAILED、10 件中 1 件失敗。`ErrorResponseIT` 9 件は通った）。見立てどおり、要求1本で ERROR が2行出た。
  1. `org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]` の「Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception」（`traceId` なし）
  2. `cherry.mastersmith.common.error.web.ErrorPathController` の「想定外のエラーが起きました」（`code` `INTERNAL_ERROR`、応答と同じ `traceId`、`exception` あり）
  - どちらも同じ連なり: `CannotCreateTransactionException: Could not open JPA EntityManager for transaction` ← `JDBCConnectionException: Unable to acquire JDBC Connection` ← `SQLTransientConnectionException: mastersmith-db - Connection is not available, request timed out after 252ms (total=1, active=1, idle=0, waiting=0)`。スタックに `AccessTokenAuthenticationProvider.authenticate(AccessTokenAuthenticationProvider.java:62)` ← `UserAccountService.findById` があり、原因も見立てどおり（FR4.1b・FR4.3 には当たらない）。
  - 落ちた確かめ: `[Tomcat のロガーの ERROR は出ない（同じ例外の二重の ERROR にならない）] Expecting empty but was: [{"exception"="org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction ...`
  - 応答（500・`application/problem+json`・`INTERNAL_ERROR`・32 桁の `traceId`・内部の文なし）の確かめは直す前から通った。
  - Tomcat の行は `traceId` を持たないため、要求を1本だけ送った区間の中の行と、同じ例外の連なりで結び付けて数えた。
- 直した後（Step 12）: 単独で `FilterExceptionErrorLogIT` 1 件・`ErrorResponseIT` 9 件、失敗 0。直しを一時的に外すと落ちることを確かめ、戻した（`git diff` が同じ）。
- G2 の後: 単独で通った（エンジン名 `Tomcat`）。`ErrorResponseIT`・`UserAdminBusyLogTraceIT` と同じ JVM でも通った（エンジン名 `Tomcat-3`）。直しを一時的に外すと「`application.yaml` の `logging.level` に Tomcat の `dispatcherServlet` のロガーの設定がある」の確かめで落ちることを確かめ、戻した（`git diff` が同じ）。

### 3.4 統合の前の1コマンドの検査（Step 16）

- 1回目（2026-10-04 11:51〜12:01、`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、colima・`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` あり）: BUILD FAILED。結合 707 件中 1 件失敗（`FilterExceptionErrorLogIT`）。原因と扱いは 4.1 の G2。
- 2回目（G2 の直しの後、同じコマンドと環境）: **BUILD SUCCESSFUL（9 分 46 秒）**。
  - 単体（`:backend:test`）: 176 クラス 1547 件、失敗 0・飛ばし 0。
  - 結合（`:backend:integrationTest`）: 142 クラス 707 件、失敗 0・飛ばし 0（対象DB の結合テストは SKIPPED になっていない）。
  - フォーマット（`verifyFormat`）・リンタ（`verifyLint`）・ライセンスヘッダー（`verifyLicense`）・ビルドと型の検査（`verifyBuild`）・フロントエンドのテスト・カバレッジの下限（`jacocoTestCoverageVerification`・`frontendCoverage`）・Gitleaks（no leaks found）・SpotBugs の関門（`spotbugsGate`）・成果物の確かめ（`bootWar`・`verifyDslSchemaInWar`）: すべて通った。
  - OSV-Scanner: この verify の中では `osvScan` が `UP-TO-DATE`（入力が前の実行と同じ）で実際には走らなかったため、続けて `./gradlew osvScan --rerun` を流した。BUILD SUCCESSFUL、止める条件に当たるもの 0 件・警告 16 件（例: `vendor/make-you-chic-ui/package-lock.json` の開発用の `undici@8.10.0`。どれも警告の扱い）。
  - SpotBugs: 関門で止める指摘は無い。今回のコードで新しく出た priority 2 の警告が3件ある（警告の扱い。直していない）: `EI_EXPOSE_REP2`（`InitialAdminRescueListener`・`InitialAdminInitializer` の構築子で注入を受ける形。既存の `AccessRequestRejectedHandler` などと同じ形）、`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`（`UserAccountService.rescueInitialAdmin` の `passwordEncoder.encode` の戻り値。既存の `createUser` にも同じ警告がある）。既存の `AuditEvent` の `CT_CONSTRUCTOR_THROW` は、今回の `ofStartup` の分も含めて2件。

### 3.5 カバレッジ（Step 16 の2回目の verify の実測）

`backend/build/reports/jacoco/test/jacocoTestReport.xml` から読んだ値。下限（行 80%・分岐 70%）・除外・一覧は変えていない。

| 範囲 | 行 | 分岐 |
|---|---|---|
| バックエンド全体 | 98.9%（6416/6486） | 94.8%（2381/2511） |
| `user.domain` | 99.6%（264/265） | 98.0%（147/150） |
| `user.service` | 99.7%（370/371） | 95.7%（135/141） |
| `auth.service` | 99.3%（280/282） | 93.0%（80/86） |
| `audit.domain` | 99.6%（262/263） | 98.4%（61/62） |
| `audit.service` | 100.0%（178/178） | 82.5%（33/40） |

- フロントエンド（変更なし、`frontendCoverage` の実測）: Statements 97.27%、Branches 92.77%、Functions 97.92%、Lines 97.44%。
- `packagesJudgedByTotal` は 7 個のまま（計画どおり、一覧のパッケージの `src/main` を変えていない）。

### 3.6 環境の確かめ（Step 13）

- 読んだ日時: 2026-10-04 11:48:55 +0900（`docker buildx imagetools inspect <タグ>`）。どれも index で、`linux/amd64` と `linux/arm64` の両方を含む。

| イメージ（タグ） | index のダイジェスト | amd64 | arm64 |
|---|---|---|---|
| `eclipse-temurin:25.0.4_7-jre-noble` | `sha256:b573af9e331196fbc42e246da4df24df9b6c556c73e7efddfde0511f1c9508c5` | あり | あり（`linux/arm64/v8`） |
| `eclipse-temurin:25.0.4_7-jdk-noble` | `sha256:2feab631bffce6236d8bb5261a4abe19a8d6f85bad1c01166f74686c983d011f` | あり | あり（`linux/arm64/v8`） |
| `otel/opentelemetry-collector:0.162.0` | `sha256:310a800ad69ee430e7c541796852a242c9c7db97aaad4daa5ccf843c525fbdb2` | あり | あり |
| `grafana/otel-lgtm:0.34.0` | `sha256:b966ea107831d526d9eb8fe4d2d86c9e5731392fad9dce8296bcf2072031f07c` | あり | あり |
| `grafana/k6:2.3.0` | `sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34` | あり | あり |

- 付けた所は計 20 か所（`Dockerfile` 1、`compose.yaml` 2、`perf/README.md` 11、`README.md` 5、`docker/hikari-pool.sh` 1）。タグだけの出現は 0 件。既存のダイジェスト付きのイメージ（対象DB・Mailpit）は変えていない。
- `docker compose config -q`、`imagetools inspect <タグ@ダイジェスト>`（5つとも同じ index を返した）、k6 の `inspect`（`loginSuccess` が出た）、`bash -n docker/hikari-pool.sh`: 通った。`docker compose -f docker/perf/compose.yaml config -q` は 4.2 のとおり。

## 4. 計画との差

### 4.1 依頼者が決めたこと（生成の途中、2026-10-04）

- **G1: A**（`code-generation-questions.md` の G1）: 計画の変更（NFR1 の伏せ字、初期化が `existsByEmail` の代わりに `rescueInitialAdmin` を呼ぶこと）の結果として、計画の影響の範囲に無い既存のテスト2件が落ちたため直した。テストの趣旨は変えていない。
  - `backend/src/test/java/cherry/mastersmith/auth/domain/SecretTypesTest.java`: 「メールアドレスは見せる」を「メールアドレスそのものは出ず、伏せ字 `a***@example.com` が出る」に替えた（表示名も替えた）。
  - `backend/src/test/java/cherry/mastersmith/auth/web/AuthSuspensionSecretLeakIT.java`: 伏せ字の型の口の TRACE の行の確かめを `ENTER UserAccountService#existsByEmail(` から `ENTER UserAccountService#rescueInitialAdmin(`（`***` を含む）に替えた。
- **G2: A**（同 G2）: Step 16 の1回目の verify で `FilterExceptionErrorLogIT` だけが落ちた。Spring Boot 4.1.1 の `TomcatWebServer` は、同じ JVM で2つ目以降の組み込みの Tomcat のエンジン名に `-<番号>` を付ける（静的な `containerCounter`）。そのため結合テストの JVM では名前が `Tomcat-32` などになり、`application.yaml` の `[Tomcat]` の鍵が当たらなかった（本番は1つの JVM に Tomcat が1つで `Tomcat`）。`application.yaml` はそのままにし、テストだけを次の形にした。
  - `Binder` で `logging.level` の `[Tomcat]` の鍵が `OFF` であることを確かめる（設定が無ければ落ちる）。
  - 動いている Tomcat の実際のエンジン名のロガーに同じレベルを当ててから要求を送り、後で外す。
  - 結果として、直しを外したときに落ちる確かめは、二重の ERROR の確かめではなく設定の鍵の確かめになった。直す前の二重の ERROR の再現は 3.3 の Step 11 の記録のとおり。

### 4.2 生成の中で決めた小さな差

- `FilterExceptionErrorLogIT`（P3）: 計画どおり `maximum-pool-size=1`・`connection-timeout=250` にすると、Flyway が起動時に接続を2本同時に使うためアプリが起動できなかった。プールは1本のまま、このクラスだけ `spring.flyway.url`・`user`・`password` を同じ H2 のファイルに向け、Flyway にだけプールの外の接続を使わせた。本番の設定とコードは変えていない。
- Step 3: `AuditEventType` を足すと `AuditEventListener` の網羅の `switch`（`isDslOperation`）がコンパイルできないため、Step 3 の時点で2つの種類を DSL の操作でない側に足した（受け取りの本体は Step 8）。
- `InitialAdminRescuedEvent` の null の確かめは、`Set.of(...)` の `contains(null)` が `NullPointerException` を投げるため、要素を順に見る形にした。
- 救済で条件があるときは、停止の解除・印・ハッシュの置き換えの3つを、値が同じでも書く（分岐を減らすため。結果は同じ）。
- Step 13: `docker compose -f docker/perf/compose.yaml config -q` は必須の `MASTERSMITH_PERF_ENV_FILE` が無いと誤りで止まる（このファイルは変えていない）。スクラッチの空のファイルを渡して流し、通った。
- Step 16: verify の中の `osvScan` が `UP-TO-DATE` だったため、`./gradlew osvScan --rerun` を別に流して結果を確かめた。
- `UserAdminBusyLogTraceIT` は、2つの経路を1つのテストの中で起こす形にした（L3 がちょうど2行を確かめるため）。

### 4.3 計画で決めていた差（要件・決まりとの差。計画 8節）

- D1: A — 条件の列は `rejection_kind`（列の名前と使い方がずれる。`AuditEvent` の説明と README に書いた）。
- D2: A — 救済は `useradmin` を通らない2つ目の書き換えの経路（`InitialAdminRescueListener` と `rescueInitialAdmin` の説明に書いた）。境界テストは変えていない。
- D3: A — Tomcat のロガーを止めたことで、応答を書き始めた後にサーブレットの外へ出た例外のログは無くなる（`application.yaml` のコメントと README）。
- D4: A — ダイジェストを付けた範囲は要件 FR7.2 の5か所より広い（同じイメージのほかの出現すべて）。
- D5・D7: B — 統合の前の E2E は流していない（Step 17）。team.md は「E2E は、画面・認証に関わる変更を統合する前に手元で実行する」としているが、この変更は画面に触れない。救済は E2E の初期管理者では働かない（E2E は実行ごとに新しい内部DB で初期管理者を作るため、有効な管理者でパスワードも一致する）。そのため「認証に関わる変更」に当たらないと読んだ。これは team.md の文言との差。E2E はリリース（配備）の前に流す（計画 6.3）。この段では E2E の生成物を作っていない・消していない。
- FR1.2a の ERROR はスタックトレースを付けない（2節）。

## 5. 依頼者に確かめたいこと

- SpotBugs の priority 2 の警告3件（3.4）は、関門の基準に当たらないため直していない。既存のコードと同じ形のため、このままでよいか。
- G2 の形では、直しを外したときに落ちるのは設定の鍵の確かめになった（4.1）。二重の ERROR そのものの再現は Step 11 の記録だけで残る。この形でよいか。

## 6. Build and Test に引き継ぐこと

- 計画 6節のとおり: FR2（ログインの p95 を3回以上。R-09 の手当て）、FR6（team.md の「12 パッケージ」を 7 個に直す。一覧はこの段で変えていない）、`Dockerfile` のダイジェスト付きの `FROM` からイメージを作り使い捨ての環境で起動できること（FR7 の判定）、`develop` に統合した版の CI の確かめ、配備の前の E2E（救済の WARN が0件であることも件数で確かめる）、NFR3 の起動の時間の比べ。
- NFR4・NFR5 は、3.4・3.5 の実測を Build and Test が判定する。
