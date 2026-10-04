# 生成のメモ（261004-safety-carryover）

コード生成の段の生成の担当が、判断・計画との差・流したテストの結果を書き足していくメモ。次の依頼の担当はこれを読んでから続ける。

## Step 1（準備）

- E2E の生成物（`frontend/playwright-report`・`frontend/test-results`）は無かった（消すものは無し）。
- `develop`（`25ec1b9`）から `fix/261004-safety-carryover` を作った。`aidlc/` の下の未コミットの変更（監査ログの追記と `construction/`）はそのまま作業ブランチに持ち越している（コミットはオーケストレーターが行う）。
- colima は動いている（aarch64、docker）。Gradle は README の `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を渡して流す。

## Step 2（テストの実行の準備。変更の前の基準）

計画は「結果を code-summary.md に記録する」としているが、code-summary.md は Step 18 でまとめて書くため、ここに記録した。Step 18 の担当はこの節を code-summary.md の「Step 2 の基準の結果」へ写す。

- 単体（`unit-test-instructions.md` 3.1 の「変更の前」のコマンド、`--rerun`）: BUILD SUCCESSFUL。9 クラス 145 件、失敗 0・誤り 0・飛ばし 0。
  - `ArchitectureTest` 5、`AuditBoundaryArchitectureTest` 6、`AuditEventFactoryTest` 50、`AuditEventTest` 6、`AuditEventListenerTest` 24、`AuthBoundaryArchitectureTest` 4、`InitialAdminInitializerTest` 8、`UserAccountServiceTest` 34、`UserAdminBoundaryArchitectureTest` 8
- 結合（3.2 の「変更の前」のコマンド、`--rerun`）: BUILD SUCCESSFUL（41 秒）。2 クラス 9 件、失敗 0。
  - `InitialAdminIT` 3、`UserAdminBusyApiIT` 6
- 件数は `backend/build/test-results/{test,integrationTest}/*.xml` の実測。

## Step 3（ドメインの型）

- 作った: `user/domain/InitialAdminRescueCondition.java`（列挙と `code(Set)`、区切りの定数 `SEPARATOR`）・`InitialAdminRescuedEvent.java`・`InitialAdminCreatedEvent.java`。
- 変えた: `audit/domain/AuditEventType.java`（2つの種類）、`audit/domain/AuditEvent.java`（静的な組み立て `ofStartup(occurredAt, eventType, sourceIp, targetUserId, rejectionKind)`。名前は `set` で始めない。クラスの説明に列の使い方 P1 を書いた）、`audit/domain/AuditEventFactory.java`（`SYSTEM_SOURCE_IP = "system"`、`from(InitialAdminCreatedEvent)`・`from(InitialAdminRescuedEvent)`、`resultOf` の網羅の `switch` に2つを `SUCCESS` として足した）。
- 計画との差（小さい）: `AuditEventType` を足すと `audit/service/AuditEventListener.java` の網羅の `switch`（`isDslOperation`）がコンパイルできないため、Step 3 の時点で2つの種類を `false`（DSL の操作ではない）の側に足した。受け取りの本体（`onInitialAdminCreated`・`onInitialAdminRescued`）は計画どおり Step 8 で足す。`audit.service` は `packagesJudgedByTotal` の外。
- 判断: 空でない・null を含まない確かめは `conditions.contains(null)` ではなく要素を順に見る形にした。`Set.of(...)` の `contains(null)` が `NullPointerException` を投げ、1つの条件の集まりが誤って拒まれたため（最初のテストの実行で見つかった）。

## Step 4（ドメインのテスト）

- 作った: `backend/src/test/java/cherry/mastersmith/user/domain/InitialAdminRescueConditionTest.java`（10 件。うち jqwik の性質ベース1件。`InitialAdminRescuedEvent`・`InitialAdminCreatedEvent` の構築子の確かめも同じクラスに入れた）。
- 変えた: `audit/domain/AuditEventFactoryTest.java`（4 件足して 54 件。作成の行・救済の行・3つの条件で 32 文字以内・2つの種類の結果と `system` の長さ）。`AuditEventTest` は変えていない（既存の「すべての種類が 32 文字以内」が2つを覆う）。
- 実行（`unit-test-instructions.md` 3.1 の後のコマンドから、未作成のクラスの行を外したもの）: BUILD SUCCESSFUL。8 クラス 117 件、失敗 0。
  - `InitialAdminRescueConditionTest` 10、`AuditEventFactoryTest` 54、`AuditEventTest` 6、`AuditEventListenerTest` 24、境界テスト 4 クラス（5・4・6・8）
- jqwik: 4 つの boolean の全組み合わせ（tries 16・checks 14、空の組 2 つは `Assume` で外す）。乱数の種は `-4893468421919841831`（報告の XML）。失敗時は `@Property(seed = "...")` で再現する。

## Step 5〜8（業務処理の層の実装）

- Step 5: `user/service/InitialAdminRescueResult.java`（新規。`NotFound`・`NotNeeded`・`Rescued(userId, conditions)`）、`UserAccountService.rescueInitialAdmin(RedactedText, Password)` と小さな手伝い `requireOneRow`。
  - 停止の解除・印・ハッシュの置き換えは、条件があれば3つとも書く（停止・印は値が同じでも書く。分岐を減らすため。結果は同じ）。どれも1行でなければ `IllegalStateException`（メッセージは利用者 ID だけ）。
  - 72 バイトを超えるパスワードは照合せず不一致（`PASSWORD`）とした（計画どおり）。
  - 確かめたこと: 計画の `findByEmail(String)` は `UserRepository`（Spring Data のインターフェース）の口で、`cherry.mastersmith.*` を TRACE にしても追跡の行が出ない（既存の `AuthSecretLeakIT` の出力に `UserRepository#…` の行が0件。動的なロガーが実体のクラスの名前になるためと見ている）。`InitialAdminSecretLeakIT` の TRACE でもメールアドレスは出なかった。計画どおり `findByEmail` を使った。
- Step 6: `InitialAdminInitializer`（構築子に `ApplicationEventPublisher`・`Clock`、救済→分岐→作成、`RESCUED_MESSAGE`・`RESCUE_FAILED_MESSAGE` の定数、結果の型は網羅の `switch`）。`createIfNeeded()` の戻り値は「作ったら true」のまま（救済・何もしないは false）。`InitialAdminProperties.toString()` を `EmailAddress.mask` に。
  - 救済の失敗の ERROR はキー `maskedEmail`・`exceptionClass` だけで、例外（原因・スタックトレース）を渡さない（計画どおり。team.md の「想定外は ERROR でスタックトレース付き」との差。code-summary.md に書くこと）。
- Step 7: `auth/service/InitialAdminRescueListener.java`（新規。`@Order(0)`・`@EventListener`・`MANDATORY`、`createIfAbsent` → `update(userId, 0, null)` → `revokeAllActiveByUserId`、DEBUG はキー `userId`・`revoked` だけ）。
- Step 8: `AuditEventListener` に `onInitialAdminCreated`・`onInitialAdminRescued`。組み立ての失敗のときの項目は `auditEventType`・`targetUserId` だけ（`initialAdminFields`）。書き込みの失敗のときは既存どおり組み立てた行の全項目（`sourceIp=system`、メールアドレスは null）。

## Step 9（業務処理の層のテスト）— 途中で止めた

### 作った・変えたテスト

- 単体: `UserAccountServiceTest`（9 件足して 43 件）、`InitialAdminInitializerTest`（既存5件を新しい流れに直し、3 件足して 11 件）、`InitialAdminPropertiesTest`（新規 3 件）、`auth/service/InitialAdminRescueListenerTest`（新規 3 件）、`AuditEventListenerTest`（3 件足して 27 件）。
- 結合: `InitialAdminRescueIT`（新規 9 件: 3つの条件＋ロック＋トークン、1つずつ3通り、何もしない、途中の失敗、監査の書き込みの失敗、R-08、設定なし）、`InitialAdminSecretLeakIT`（新規 2 件: INFO・TRACE）、`InitialAdminIT`（作成の監査の行がちょうど1行・2回目で行が増えないを足した。3 件のまま）。
- 手伝い: `user/testsupport/FailingInitialAdminRescueConfig.java`（`Probe` に投げる前に同じトランザクションで読んだ失敗回数・有効なトークンの数を残す）。途中の失敗のテストで、投げる前は失敗回数 0・有効なトークン 0 と読め（同じトランザクションの書き換えが見えている）、起動の後はすべて元のままだった。
- `InitialAdminSecretLeakIT` は、1回目の起動の後のログイン（トークンを得る準備）の区間を数えない（この Intent の変更の外のため）。数える区間は1回目の起動と2回目の起動。

### 流したコマンドと結果

- `unit-test-instructions.md` 3.1（Step 4・9 の後のコマンド）: BUILD SUCCESSFUL。12 クラス 180 件、失敗 0。
  - `InitialAdminRescueConditionTest` 10、`InitialAdminPropertiesTest` 3、`InitialAdminInitializerTest` 11、`UserAccountServiceTest` 43、`InitialAdminRescueListenerTest` 3、`AuditEventFactoryTest` 54、`AuditEventTest` 6、`AuditEventListenerTest` 27、境界テスト 4 クラス（5・4・6・8）
- 3.2 の FR1（Step 9）: BUILD SUCCESSFUL（30 秒）。`InitialAdminIT` 3、`InitialAdminRescueIT` 9、`InitialAdminSecretLeakIT` 2、計 14 件、失敗 0。
- 回帰の確かめ（計画には無い追加の実行）:
  - `./gradlew :backend:test --rerun`（単体の全体）: 1547 件中 **1 件失敗** — `auth/domain/SecretTypesTest` の「initial admin settings hide the password but show the email address」。
  - `./gradlew :backend:integrationTest --rerun --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.audit.*'`: 273 件中 **1 件失敗** — `auth/web/AuthSuspensionSecretLeakIT`（209 行の `ENTER UserAccountService#existsByEmail(` の TRACE の行が1件以上あることの確かめが空）。

### 止めた理由（計画との差。依頼者の判断が要る）

計画の変更そのもの（NFR1 の `InitialAdminProperties.toString()` の伏せ字、FR1 で初期化が `existsByEmail` の代わりに `rescueInitialAdmin` を呼ぶこと）の結果として、計画の影響の範囲（1節の表・7.2）に無い既存のテスト2件が落ちた。直すには計画に無いファイルを変える必要があるため、直さずに止めた。

1. `backend/src/test/java/cherry/mastersmith/auth/domain/SecretTypesTest.java` 115〜120 行: 「メールアドレスは見せる」ことを確かめている。NFR1（K-25）で意図して変えた振る舞いと正面から食い違う。
2. `backend/src/test/java/cherry/mastersmith/auth/web/AuthSuspensionSecretLeakIT.java` 208 行: 「伏せ字の型で受ける口の TRACE の行が出ていること」の確かめに、初期化が呼んでいた `UserAccountService#existsByEmail(` を使っている。初期化はもう呼ばない（`createUser` の中の存在の確かめはリポジトリの `existsByRedactedEmail` で、サービスの口ではない）。このテストの趣旨（伏せ字の型の口が追跡されメールアドレスが出ない）は変わっていない。

候補:
- A（推奨）: 2件のテストを直す。(1) `SecretTypesTest` を「メールアドレスそのものは出ず伏せ字 `a***@example.com` が出る」に替える（表示名も "hide the password and mask the email address" に）。(2) `AuthSuspensionSecretLeakIT` の確かめを、同じく `RedactedText` で受ける `ENTER UserAccountService#rescueInitialAdmin(` の行（`***` を含む）に替える。どちらも計画に無い変更として code-summary.md に記録する。
- B: (2) だけは、初期化で `existsByEmail` を先に呼ぶ形に戻してテストを変えない。DB の読み取りが1回増え、計画の流れ（救済の判定で1回読む）と違うため勧めない。(1) は NFR1 と両立しないため A と同じ直しが要る。
- X: そのほか。

作業の状態: Step 9 のチェックは付けていない。上の2件のほかの Step 9 のテストは書けて通っている。Step 10 以降は手を付けていない。カバレッジは測っていない（Step 16）。

## Step 9 の続き（後半の担当）

- 依頼者の決定 G1: A を受けて、計画の影響の範囲に無い既存のテスト2件を直した（テストの趣旨は変えない。計画との差として code-summary.md に書く）。
  - `backend/src/test/java/cherry/mastersmith/auth/domain/SecretTypesTest.java`: 表示名を "initial admin settings hide the password and mask the email address" にし、メールアドレスそのものが出ず伏せ字 `a***@example.com` が出ることを確かめる形にした。
  - `backend/src/test/java/cherry/mastersmith/auth/web/AuthSuspensionSecretLeakIT.java`: 伏せ字の型の口の TRACE の行の確かめを `ENTER UserAccountService#existsByEmail(` から `ENTER UserAccountService#rescueInitialAdmin(`（`***` を含む）に替えた。
- 実行（3.1 の後のコマンドに `SecretTypesTest` を足したもの、`--rerun`）: BUILD SUCCESSFUL。13 クラス 187 件、失敗 0（`SecretTypesTest` 7 を含む。ほかの件数は前の担当の記録と同じ）。
- 実行（3.2 の FR1 のコマンドに `AuthSuspensionSecretLeakIT` を足したもの、`--rerun`）: BUILD SUCCESSFUL。`InitialAdminIT` 3、`InitialAdminRescueIT` 9、`InitialAdminSecretLeakIT` 2、`AuthSuspensionSecretLeakIT` 1、計 15 件、失敗 0。

## Step 10（FR3.1）

- 作った: `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyLogTraceIT.java`（1 件。1つのテストの中で `grant-admin`＋利用者の行（`ADMIN_ROWS`）と `suspend`＋リフレッシュトークンの行（`REFRESH_TOKEN_ROWS`）の2つの 409 を起こす）。本番のコードは変えていない。
- L3 は `GlobalExceptionHandler` の WARN で `code` が `USER_ADMIN_BUSY` の行、L4 は `RowLockFailures` の決まった文「行の排他を取れませんでした」の WARN（ロガーは呼び出し元のクラス）として数えた。L3 がちょうど2行、各 L3 の `traceId`（応答の `traceId` と一致・空でない）に L4 がちょうど1行、L4 の `lockKind` が経路どおり、`exceptionClass` を持つ。
- 実行（3.2 の FR3 のコマンド、`--rerun`）: BUILD SUCCESSFUL。1 件、失敗 0（約 13 秒）。

## Step 11（FR4.1・FR4.1a。直す前の再現）

- 作った: `backend/src/test/java/cherry/mastersmith/common/error/web/FilterExceptionErrorLogIT.java`（1 件。最終の形「その要求の ERROR は `ErrorPathController` の1行だけ、Tomcat のロガーの ERROR は0行」で書いた）。
- 計画との差（テストの作り方だけ。本番の設定とコードは変えていない）: P3 のとおり `maximum-pool-size=1`・`connection-timeout=250` にすると、Flyway が起動時に接続を2本同時に使うため、アプリが起動できなかった（`FlywaySqlUnableToConnectToDbException: ... Connection is not available, request timed out after 255ms (total=1, active=1, idle=0, waiting=0)`）。プールの大きさは計画どおり1本のまま、このクラスの `@DynamicPropertySource` で `spring.flyway.url`・`user`・`password` を同じ H2 のファイルに向け、Flyway にだけプールの外の接続を使わせた。code-summary.md の計画との差に書く。
- 結果（1回目の実行。`./gradlew :backend:integrationTest --rerun --tests '...FilterExceptionErrorLogIT' --tests '...ErrorResponseIT'`）: BUILD FAILED、10 件中 1 件失敗（`FilterExceptionErrorLogIT`。`ErrorResponseIT` 9 件は通った）。**見立てどおり二重の ERROR を再現した**（P3 の「2行出た」→ Step 12 へ進む）。
  - 応答: 500、`application/problem+json`、`code` が `INTERNAL_ERROR`、`traceId` は 32 桁の16進、本文に `Exception`・`Connection is not available`・スタックトレース・メールアドレスなし（ここまでの確かめは通った）。
  - その要求の区間の ERROR は次の2行（同じ例外）:
    1. `org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]` の「Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception」。`traceId` を持たない（見立てどおり）。
    2. `cherry.mastersmith.common.error.web.ErrorPathController` の「想定外のエラーが起きました」。`code` `INTERNAL_ERROR`、`traceId` が応答と同じ（`76b01d4d96d7ab828e0a0e7a8b7eaca4`）、`exception` を持つ。
  - どちらの例外も `org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction` ← `org.hibernate.exception.JDBCConnectionException: Unable to acquire JDBC Connection` ← `java.sql.SQLTransientConnectionException: mastersmith-db - Connection is not available, request timed out after 252ms (total=1, active=1, idle=0, waiting=0)`。スタックに `UserAccountService.findById` ← `auth.web.AccessTokenAuthenticationProvider.authenticate(AccessTokenAuthenticationProvider.java:62)` ← `common.web.CacheControlFilter` があり、見立て（認証のフィルターの中で利用者を読む問い合わせが接続を借りられない）と同じ原因と確かめた。
  - 落ちた確かめ: `[Tomcat のロガーの ERROR は出ない（同じ例外の二重の ERROR にならない）] Expecting empty but was: [{"exception"="org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction ...`
  - Tomcat の行は `traceId` を持たないため、「同じ `traceId` で2行」は、要求を1本だけ送った区間の中の行と、例外のクラスの名前（同じ連なり）で結び付けて数えた（計画の P3 の想定どおり）。

## Step 12（FR4.2・NFR6。D3: A の直し）

- 変えた: `backend/src/main/resources/application.yaml` の `logging.level` の末尾に `"[org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]]": OFF` と、理由・障害の調べ方・失うもののコメントを足した（Spring Boot の角かっこの書き方で、そのまま効いた）。
- 実行（直した後。3.2 の FR4 のコマンド、`--rerun`）: BUILD SUCCESSFUL。`FilterExceptionErrorLogIT` 1、`ErrorResponseIT` 9（入れ子のクラスを含む: 本体 7・2つの入れ子 各1）、計 10 件、失敗 0。その要求の ERROR は `ErrorPathController` の1行だけ（原因の例外・応答と同じ `traceId`）、Tomcat のロガーの ERROR は0行、応答は直す前と同じ（500・`INTERNAL_ERROR`・内部のメッセージなし）。
- 再現の確かめ: 直しを一時的に外して（`git checkout` で `application.yaml` を戻して）`FilterExceptionErrorLogIT` を流し、落ちる（BUILD FAILED、Tomcat の ERROR が出る）ことを1回確かめた。その後、保存しておいた直した版に戻し、`git diff` が外す前の差と同じであることを確かめた。

## Step 13（FR7.2・FR7.4。イメージのダイジェスト）

- 読んだ日時: 2026-10-04 11:48:55 +0900（`docker buildx imagetools inspect <タグ>`）。どれも複数のアーキテクチャをまとめた index（manifest list）で、`linux/amd64` と `linux/arm64` の両方を含む。

| イメージ（タグ） | index のダイジェスト | 種類 | amd64 | arm64 |
|---|---|---|---|---|
| `eclipse-temurin:25.0.4_7-jre-noble` | `sha256:b573af9e331196fbc42e246da4df24df9b6c556c73e7efddfde0511f1c9508c5` | OCI index | あり | あり（`linux/arm64/v8`） |
| `eclipse-temurin:25.0.4_7-jdk-noble` | `sha256:2feab631bffce6236d8bb5261a4abe19a8d6f85bad1c01166f74686c983d011f` | OCI index | あり | あり（`linux/arm64/v8`） |
| `otel/opentelemetry-collector:0.162.0` | `sha256:310a800ad69ee430e7c541796852a242c9c7db97aaad4daa5ccf843c525fbdb2` | Docker manifest list | あり | あり |
| `grafana/otel-lgtm:0.34.0` | `sha256:b966ea107831d526d9eb8fe4d2d86c9e5731392fad9dce8296bcf2072031f07c` | OCI index | あり | あり |
| `grafana/k6:2.3.0` | `sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34` | OCI index | あり | あり |

- 付けた所（`タグ@sha256:…`、版のタグは残した。計 20 か所。D4: A の範囲と一致）: `Dockerfile` の `FROM`（jre）、`compose.yaml` の otel-collector・otel-lgtm、`perf/README.md` の k6 6・jre 3・jdk 1・otel-lgtm 1、`README.md` の jre 4・jdk 1（説明の中）、`docker/hikari-pool.sh` の `JDK_IMAGE`。タグだけの出現は 0 件（`grep` で確かめた）。既存のダイジェスト付きのイメージ（対象DB・Mailpit）と `docker/perf/compose.yaml`・`TargetDbImages.java` は変えていない。
- 確かめ:
  - `docker compose config -q`: 通った。
  - `docker compose -f docker/perf/compose.yaml config -q`: そのままでは `MASTERSMITH_PERF_ENV_FILE` の必須の値が無いという誤りで止まった（このファイルは変えていない。手順書どおり一時の環境ファイルを渡す前提のため）。スクラッチの空のファイルを `MASTERSMITH_PERF_ENV_FILE` に渡して流し直し、通った（計画のコマンドとの差）。
  - `docker buildx imagetools inspect <タグ@ダイジェスト>`: 5つとも同じ index のダイジェストを返した。
  - k6 の `inspect`（`unit-test-instructions.md` 5節のコマンド、ダイジェスト付きのイメージ）: 台本が読み込め、場面 `loginSuccess` が出た。
  - `bash -n docker/hikari-pool.sh`: 通った（配備したアプリには触れていない）。

## Step 14（FR7.3）

- 変えた: `.github/dependabot.yml`。`docker` の項の前に「ダイジェストでのイメージの固定」のコメント（固定しているイメージ、index のダイジェストを使うこと、Dependabot が見ない固定先の一覧、上げるときに手で揃えること）を置き、`docker-compose` の項のコメントにその決まりを指す2行を足した。既存の `ignore` とその理由・外す時期は変えていない。YAML として読めることを確かめた。

## Step 15（FR1.9・FR5.1・R-08・FR4.2 の文書）

- 変えた: `README.md`。
  - 「コンテナでの起動と確認」の初回の起動の箇条に、2回目以降の起動で条件に当たれば救済し WARN が出ることを足した。
  - 同じ節に小見出し「使える管理者がいなくなったとき（初期管理者の救済）」を足した（条件 (a)〜(c)、行うこと (a)〜(e)、WARN・INFO・ERROR のログとキー、監査 `INITIAL_ADMIN_RESCUED`、先に `.env` を替える手順（FR1.9・F5）、設定のパスワードが規則外のときの WARN と直し方（R-08）、値を出さない確かめ方）。置き場は計画 P7 のとおり「戻し方」の前。
  - 「利用者の管理の API」の節の後に小見出し「利用者の管理の画面の既知の点」（FR5.1。症状・直さない理由・候補 (a)〜(c)）。
  - 「監査ログ（U4）」の前に節「例外のログ（フィルターの中の想定外の例外）」（D3: A の直し、Tomcat のロガーを止めたこと、調べ方、失うもの）。`application.yaml` のコメントの参照先「例外のログ」はこの節。
  - 「監査ログ（U4）」に `INITIAL_ADMIN_CREATED`・`INITIAL_ADMIN_RESCUED` の箇条と列の使い方（接続元 `system`、操作した人は空、条件は `rejection_kind`、`failure_reason` は空、記録の失敗の ERROR）。
- 書いた値は、ソースで確かめた（ログの文・キー、`PasswordPolicy` の 12 文字・72 バイト、監査の失敗の文）。メールアドレスの実値・パスワード・`.env` の値は書いていない（例は `a***@example.com` の伏せ字だけ）。

## Step 16（統合の前の1コマンドの検査）— 失敗したため止めた

- 実行: colima が動いていることを確かめ、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（11:51〜12:01、9 分 22 秒）。**BUILD FAILED**。
  - 単体（`test`）: 176 クラス 1547 件、失敗 0・飛ばし 0。
  - 結合（`integrationTest`）: 142 クラス 707 件、**失敗 1**（`FilterExceptionErrorLogIT`）、飛ばし 0（対象DB のテストは SKIPPED になっていない）。
  - 結合テストのタスクで止まったため、カバレッジの下限の検証とその後の検査（SpotBugs・Gitleaks・OSV-Scanner など）までは届いていない。参考として、出力された `backend/build/reports/jacoco/test/jacocoTestReport.xml` の値（下限の判定ではない。結合テストが途中で止まった回のもの）: 全体 行 98.9%（6287/6357）・分岐 94.8%（2348/2476）、`user.domain` 行 99.6%・分岐 97.8%、`user.service` 行 99.7%・分岐 95.1%、`auth.service` 行 99.3%・分岐 93.0%、`audit.domain` 行 99.6%・分岐 98.4%、`audit.service` 行 100.0%・分岐 86.1%。
- 失敗の中身: Step 12 で止めたはずの Tomcat の ERROR が出た。ロガーの名前が `org.apache.catalina.core.ContainerBase.[Tomcat-32].[localhost].[/].[dispatcherServlet]` だった（`application.yaml` で止めたのは `[Tomcat]` の名前）。ERROR は2行（Tomcat-32 の行と `ErrorPathController` の行、同じ例外の連なり）。
- 原因（確かめた）: Spring Boot 4.1.1 の `TomcatWebServer`（`spring-boot-tomcat-4.1.1-sources.jar` の 67・164〜168 行）は、同じ JVM で2つ目以降に作る組み込みの Tomcat のエンジンの名前に `-<番号>`（静的な `containerCounter`）を付ける。Step 11・12 で単独で流したときは、このテストの Tomcat が JVM の最初の1つで名前が `Tomcat` だったため通った。verify では結合テストが1つの JVM で多くの Spring の文脈を起動するため 33 個目になり `Tomcat-32` になった。本番（1つの JVM に Tomcat が1つ）では名前は `Tomcat` のため、`application.yaml` の直しは本番では効く見込みだが、テストの JVM では名前が実行順で変わり、直しを確かめるテストが順に依存して落ちる。
- team.md の「不安定なテスト」の決まりで、手元で原因まで確かめた失敗のため、直すまで先へ進まない。直し方は計画（P4・Step 12 の `application.yaml` の1行）と違う形になりうるため、ここで止めて候補をオーケストレーターに返す。Step 16・17・18 のチェックは付けていない。
- 先に書いた記録: `traceability.json`・`source-manifest.json` は verify の前に下書きした（Step 18 は終えていない。直し方が決まった後に見直す）。`code-summary.md` はまだ書いていない。

## 依頼者の決定 G2: A（FilterExceptionErrorLogIT の直し）

- 変えた: `backend/src/test/java/cherry/mastersmith/common/error/web/FilterExceptionErrorLogIT.java` だけ。`application.yaml` は変えていない。
  - 要求を送る前に、`Binder` で `logging.level` を読み、`[Tomcat]` の鍵（`org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]`）が `OFF` であることを確かめる。
  - 動いている `TomcatWebServer` からエンジン名を読み、`Tomcat` でなければ、`[Tomcat]` をそのエンジン名に替えたロガーに同じレベルを `LoggingSystem` で当てる。要求の後に `finally` で外す。
  - テストの趣旨（その要求の ERROR がちょうど1行、`ErrorPathController` の行が原因の例外と `traceId` を持つ、Tomcat のロガーの ERROR は0行、応答は 500 で内部の文を含まない）は変えていない。
- 実行:
  - 単独（`--tests '...FilterExceptionErrorLogIT'`、`--rerun`）: 通った（1 件。エンジン名は `Tomcat`）。
  - 同じ JVM でほかの結合テストと一緒（`ErrorResponseIT`・`UserAdminBusyLogTraceIT`・`FilterExceptionErrorLogIT`）: 通った。出力のロガーの名前ではエンジン名が `Tomcat-3` だった。
  - 直しを一時的に外したとき（`git checkout` で `application.yaml` を戻した）: 単独で落ちた。落ちたのは「`application.yaml` の `logging.level` に Tomcat の `dispatcherServlet` のロガーの設定がある」の確かめ。保存しておいた版に戻し、`git diff` が外す前の差と同じであることを確かめた。
- 計画との差: 直しを外したときに落ちる確かめが、二重の ERROR の確かめではなく、設定の鍵の確かめになった。G2 の決定どおり、名前に依らない形にしたため。直す前の二重の ERROR の再現は Step 11 の記録のとおり。

## Step 16（2回目）〜18

- Step 16 の2回目（G2 の後）: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が BUILD SUCCESSFUL（9 分 46 秒）。単体 1547 件・結合 707 件、失敗 0・飛ばし 0。`osvScan` が `UP-TO-DATE` だったため `./gradlew osvScan --rerun` を別に流して通った（止める条件 0 件・警告 16 件）。カバレッジと SpotBugs の警告は code-summary.md の 3.4・3.5。
- Step 17: E2E は流していない（D5・D7: B）。読み方と team.md との差は code-summary.md の 4.3。
- Step 18: `code-summary.md` を書いた。`traceability.json`（41 件）と `source-manifest.json`（34 ファイル）を最終の状態で確かめ直した。どちらも下書きから中身は変わらず、`OK` の target はすべて実在のファイル。
