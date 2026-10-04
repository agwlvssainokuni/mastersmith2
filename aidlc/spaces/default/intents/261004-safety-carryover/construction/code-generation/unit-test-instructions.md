# 単体・結合テストの手順（261004-safety-carryover）

- 範囲: bugfix（深さ Minimal、Test Strategy は Minimal）。zero-Unit のため、この Intent の変更だけを対象にする。
- 進め方: test-after（`code-generation-plan.md` の Testing Contract）。層ごとに実装を書いた後、その層のテストを書いて流し、通ってから次へ進む。
- テストの量: 要件ごとに1件以上の確かめと、部品ごとの正常系の床（Minimal）に、不具合（FR4 の二重の ERROR）の再現のテストを、再現できる最も狭い段（結合テスト）で足す（bugfix の下限）。足すテストはおよそ 25 件（単体 約 15・結合 約 12）。救済は認証・利用者の状態・監査に関わるため、失敗の場合のテストを含める（project.md の Mandated）。
- コマンドはすべて、この Intent のファイルだけに絞ったもの。リポジトリのルート（`mastersmith2/`）で流す。`./gradlew verify` の全体の実行は計画の Step 16 で行う。E2E（`./gradlew e2eTest`）は統合の前には流さず、リリース（配備）の前に流す（依頼者の決定 D5・D7: B。計画の Step 17・6.3）。

## 1. 道具と前提

| 対象 | 道具 | 置き場 |
|---|---|---|
| バックエンドの単体 | JUnit 5 ＋ AssertJ ＋ Mockito、jqwik（性質ベース） | `backend/src/test/java` の対象と同じパッケージ。名前は `XxxTest` |
| バックエンドの結合 | JUnit 5 ＋ AssertJ、Spring Boot（`SpringApplicationBuilder` で起動を2回、または `@SpringBootTest`）、組み込みの H2（コンテナは使わない）、`OutputCaptureExtension`、`JsonLogRecords` | 同上。名前は `XxxIT` |
| 境界 | ArchUnit（既存の境界テストを変えずに流す） | `backend/src/test/java/cherry/mastersmith/` |
| イメージ | `docker buildx imagetools inspect`、`docker compose config`、k6（`grafana/k6:2.3.0` のコンテナ） | — |

- 設定は既存のまま使う（`backend/build.gradle.kts` の `test`・`integrationTest`）。テストの設定の変更は無い。
- 結合テストはテストのタスクの結果を使い回すことがあるため、`--rerun` を付けて流す。
- colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡してから流す（この Intent の結合テストは対象DB を使わないが、Step 16 の verify と同じ環境にそろえる）。
- 配備したアプリ（compose のプロジェクト `mastersmith`）と、その内部DB には触れない。

## 2. 足すテストと要件の対応

| ファイル | テスト | 要件 |
|---|---|---|
| `user/domain/InitialAdminRescueConditionTest.java`（新規） | 3つすべてで `SUSPENDED+NO_ADMIN+PASSWORD`、1つずつで各値、渡す順に依らない、空の集まりは誤り | FR1.6a |
| 同上 | jqwik: どの空でない部分集合でも 32 文字以内・決まった順・分けて戻すと元の集まり（失敗時の種を記録） | FR1.6a |
| 同上（または同じパッケージの小さなテスト） | `InitialAdminRescuedEvent` は空の条件・null を受け付けず、条件を変えられない写しで持つ | FR1.5 |
| `audit/domain/AuditEventFactoryTest.java`（変更） | 作成の行: `INITIAL_ADMIN_CREATED`・`SUCCESS`・対象の利用者・操作した人が空・接続元 `system`・条件が空・`entered_email`・`user_agent`・`request_path`・`trace_id` が空 | FR1.5・FR1.6・FR1.6a |
| 同上 | 救済の行: `INITIAL_ADMIN_RESCUED`・条件の列（`rejection_kind`）が決まった順の値、ほかは作成と同じ | FR1.5・FR1.6a |
| `user/service/UserAccountServiceTest.java`（変更） | 3つの条件の判定と書き込み（停止を解く・印を付ける・ハッシュの置き換え）と出来事、照合が1回だけ | FR1.1・FR1.2・NFR3 |
| 同上 | 条件が無いときは書かず知らせず `NotNeeded`、利用者がいないときは `NotFound` | FR1.3 |
| `user/service/InitialAdminInitializerTest.java`（変更） | `Rescued` で WARN が1行（キーは `maskedEmail`・`conditions` だけ） | FR1.7 |
| 同上 | `NotNeeded` で今の INFO、`NotFound` で作成と `InitialAdminCreatedEvent` | FR1.3・FR1.4・FR1.5 |
| 同上 | 救済の例外で ERROR が1行（キーは `maskedEmail`・`exceptionClass` だけ。スタックトレースなし）、例外を外へ出さない | FR1.2a |
| 同上 | 設定の問題（無い・不正）では救済も作成も呼ばない | NFR2 |
| `user/service/InitialAdminPropertiesTest.java`（新規） | `toString` にメールアドレスそのものとパスワードが出ず、伏せ字が出る | NFR1 |
| `auth/service/InitialAdminRescueListenerTest.java`（新規） | ロックの状態の行の作成と失敗回数 0・解除、リフレッシュトークンのまとめての無効化が呼ばれる | FR1.2(c)(e) |
| `audit/service/AuditEventListenerTest.java`（変更） | 2つの受け取りが記録を頼む、組み立ての失敗で ERROR を1回出して外へ出さない | FR1.5・FR1.8 |
| `user/service/InitialAdminRescueIT.java`（新規） | 3つの条件＋ロック中＋起動の前のリフレッシュトークン → 救済、トークンの更新が 401、設定のパスワードでログイン 200、監査の行がちょうど1行、WARN がちょうど1行 | FR1.1・FR1.2・FR1.5〜FR1.7・NFR2 |
| 同上 | 1つだけに当たる3通り（パラメーター化）→ 条件の列が各値 | FR1.1・FR1.6b |
| 同上 | 有効な管理者でパスワードも一致 → 何も変わらず、監査の行が増えない | FR1.3・NFR2 |
| 同上 | 途中の失敗（`FailingInitialAdminRescueConfig`）→ すべて起動の前のまま、救済の行なし、ERROR が1行、起動が続く | FR1.2a |
| 同上 | 監査の書き込みの失敗（`FailingAuditEventRepositoryConfig`）→ 救済は確定、監査の失敗の ERROR が1回、起動が続く | FR1.8 |
| 同上 | 停止中・印なしの初期管理者がいて、設定のパスワードが 12 文字未満 → 救済されず、WARN、監査の行が増えない | R-08・NFR2 |
| 同上 | 設定が無い → 停止中の利用者がいても何もしない | NFR2 |
| `user/service/InitialAdminIT.java`（変更） | 1回目の起動で `INITIAL_ADMIN_CREATED` の行がちょうど1行、2回目で増えない | FR1.4・FR1.5 |
| `user/service/InitialAdminSecretLeakIT.java`（新規） | 既定の INFO と TRACE で、作成と救済のログ・監査の行にメールアドレス・パスワード・トークン・`$2a$` が出ない（TRACE の追跡の行が出ていることも確かめる） | NFR1 |
| `useradmin/web/UserAdminBusyLogTraceIT.java`（新規） | 409 `USER_ADMIN_BUSY`（`ADMIN_ROWS`・`REFRESH_TOKEN_ROWS`）の L3 の各行に、同じ空でない `traceId` の L4 がちょうど1行 | FR3.1 |
| `common/error/web/FilterExceptionErrorLogIT.java`（新規） | 認証の途中で接続を借りられない要求が 500 の Problem Details（`INTERNAL_ERROR`・内部のメッセージなし）で、その要求の ERROR がちょうど1行（`ErrorPathController`、原因の例外と `traceId` を持つ）、Tomcat のロガーの ERROR が0行 | FR4.1・FR4.1a・FR4.2・NFR6 |

- テストの説明文（`@DisplayName`）は英語。テストの中のデータは日本語でよい。
- `user/testsupport/FailingInitialAdminRescueConfig.java`（新規）はテストの手伝いで、テストの数には数えない。

## 3. コマンド

### 3.1 単体テスト（境界テストを含む）

変更の前（計画の Step 2）は既存のものだけを流す。

```bash
./gradlew :backend:test --rerun \
  --tests 'cherry.mastersmith.user.service.InitialAdminInitializerTest' \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventFactoryTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventTest' \
  --tests 'cherry.mastersmith.audit.service.AuditEventListenerTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.useradmin.UserAdminBoundaryArchitectureTest'
```

Step 4・9 の後は、足した3つを加えて流す（Step 4 の時点で未作成のファイルの行は外してよい）。

```bash
./gradlew :backend:test --rerun \
  --tests 'cherry.mastersmith.user.domain.InitialAdminRescueConditionTest' \
  --tests 'cherry.mastersmith.user.service.InitialAdminPropertiesTest' \
  --tests 'cherry.mastersmith.user.service.InitialAdminInitializerTest' \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.auth.service.InitialAdminRescueListenerTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventFactoryTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventTest' \
  --tests 'cherry.mastersmith.audit.service.AuditEventListenerTest' \
  --tests 'cherry.mastersmith.ArchitectureTest' \
  --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest' \
  --tests 'cherry.mastersmith.useradmin.UserAdminBoundaryArchitectureTest'
```

### 3.2 結合テスト

変更の前（Step 2）は既存の2つだけを流す。

```bash
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.user.service.InitialAdminIT' \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminBusyApiIT'
```

FR1（Step 9）:

```bash
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.user.service.InitialAdminIT' \
  --tests 'cherry.mastersmith.user.service.InitialAdminRescueIT' \
  --tests 'cherry.mastersmith.user.service.InitialAdminSecretLeakIT'
```

FR3（Step 10）:

```bash
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.useradmin.web.UserAdminBusyLogTraceIT'
```

FR4（Step 11 の直す前の再現と、Step 12 の直した後。既存のエラー応答の結合テストも一緒に流す）:

```bash
./gradlew :backend:integrationTest --rerun \
  --tests 'cherry.mastersmith.common.error.web.FilterExceptionErrorLogIT' \
  --tests 'cherry.mastersmith.common.error.web.ErrorResponseIT'
```

- Step 11 では `FilterExceptionErrorLogIT` が落ちる（ERROR が2行）ことを確かめ、出力ごと code-summary.md に記録する。落ちなかった・500 にならなかったときは止める（計画の P3。FR4.1b・FR4.3）。
- Step 12 の直しは `application.yaml` の1行（Tomcat の `dispatcherServlet` のロガーを `OFF`。依頼者の決定 D3: A）。再現の確かめ: 直しを一時的に外して上のコマンドを流し、`FilterExceptionErrorLogIT` が落ちることを1回確かめ、戻す（`git diff` で戻ったことを確かめる）。

## 4. 作り方の決まり

### 4.1 起動を2回行う結合テスト（FR1）

- 既存の `InitialAdminIT` の形にそろえる: `@TempDir` の下の H2 のファイル（`TestDatabase.url(dir)`）、`--server.port=0`、`SpringApplicationBuilder(MastersmithApplication.class)` で起動する。1回目の起動で初期管理者を作り、状態を整えてから閉じ、2回目の起動で確かめる。
- 状態の整え方: 1回目の起動の中で、ログインの API でリフレッシュトークンを得てから、`JdbcTemplate` で `users` の `suspended`・`admin_flag`・`password_hash`（別の値の bcrypt）と、`login_attempt_states` の失敗回数・解除の予定の時刻を書き換える。テストのための SQL の書き換えは、テストの準備に限る。
- 救済の後の確かめは2回目の起動の中で、HTTP（`HttpTestClient`・`auth/testsupport/AuthApi`）でトークンの更新（401）とログイン（200）を送り、`JdbcTemplate` で利用者・ロックの状態・リフレッシュトークン（`revoked_at`）・`audit_events` の行を読む。監査の行は種類・結果・対象の利用者・条件の列の値で数える（FR1.6b。`audit/testsupport/AuditRows` を使ってよい）。
- 速さのため、新しい結合テストでは `--mastersmith.auth.password.bcrypt-cost=4` を渡す（既存の `InitialAdminIT` は既定の 12 のまま。ハッシュの cost の確かめがあるため）。
- 途中の失敗（FR1.2a）: 2回目の起動の元に `user/testsupport/FailingInitialAdminRescueConfig` を足す（`new SpringApplicationBuilder(MastersmithApplication.class, FailingInitialAdminRescueConfig.class)`）。その中の `@EventListener`（`@Order(Ordered.LOWEST_PRECEDENCE)`）は auth の受け手（`@Order(0)`）の後に呼ばれ、同じトランザクションの中で失敗回数とトークンが書き換わったことを確かめてから例外を投げる（巻き戻しが auth の書き換えも覆うことを確かめるため）。
- 監査の書き込みの失敗（FR1.8）: 既存の `audit/testsupport/FailingAuditEventRepositoryConfig` を同じように起動の元に足す。
- ログの数え方: 起動の前の `output.getOut().length()` の位置から後の出力を `JsonLogRecords.parse` で読み、`logger` と `message` で数える。値の無いことは `JsonLogRecords.assertContainsNoSecret` で確かめる（設定のメールアドレス・そろえた値・大文字にした形、設定のパスワード、起動の前のパスワード、リフレッシュトークンの値）。
- TRACE の漏えいの確かめ（`InitialAdminSecretLeakIT`）は `--logging.level.cherry.mastersmith=TRACE` を渡した起動で行い、`TraceAspect` の行が出ていること（確かめになっていること）も確かめる。

### 4.2 接続を借りられない状況（FR4、計画の P3）

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` に、このクラスだけの設定 `spring.datasource.hikari.maximum-pool-size=1`・`spring.datasource.hikari.connection-timeout=250` を渡す。本番の設定とコードは変えない。
- 順序: (1) 利用者を JDBC で1人入れ、`auth/testsupport/AuthTestTokens` でテストの署名鍵からアクセストークンを作る。(2) テストのスレッドで `DataSource.getConnection()` を呼び、ただ1本の接続を try-with-resources で持ったままにする。(3) その中で `GET /api/me/preferences` に Bearer つきの要求を1本送る。持つ操作が終わってから送るため、`sleep` も待ち合わせも使わない。
- 要求の前の出力の位置から後を読み、`logger` が `org.apache.catalina.core.ContainerBase.` で始まる ERROR と、`cherry.mastersmith.common.error.web.ErrorPathController` の ERROR を数える。Tomcat の行が `traceId` を持たないときは、要求を1本だけ送った区間の中の行として数え、そのことを code-summary.md に記録する。
- 定期の処理などが同じ区間で接続を借りられずに出す ERROR は、上の2つのロガーに当たらないため数えない。

### 4.3 BUSY の結び付き（FR3）

- `UserAdminBusyApiIT` と同じく、`RowLockHolder` で対象の行を別の接続から `FOR UPDATE` で持ち続け、`UserAdminFixtures`・`UserAdminApi` で操作を送る。排他の待ちの上限（3000 ミリ秒）は変えない。
- 要求の前の出力の位置から後を `JsonLogRecords.parse` で読み、L3（`GlobalExceptionHandler` の WARN、`code` が `USER_ADMIN_BUSY`）と L4（`RowLockFailures` の WARN、`lockKind`・`exceptionClass` を持つ）を `traceId` で結び付ける。

### 4.4 モックと差し替えの決まり

- 単体テスト（`UserAccountServiceTest`・`InitialAdminInitializerTest`・`InitialAdminRescueListenerTest`・`AuditEventListenerTest`）は、既存のテストと同じく Mockito でリポジトリ・業務処理・出来事の知らせ・パスワードのハッシュの仕組みを差し替える。時刻は固定の `Clock` を渡し、実時刻と `sleep` に頼らない（team.md）。
- 結合テストでは DB を組み込みの H2 のまま使い、モックにしない（team.md の内部DB の決まり）。差し替えてよいのは、失敗を起こすテストの部品（`FailingInitialAdminRescueConfig`・`FailingAuditEventRepositoryConfig`）だけ。業務処理・Hibernate・監査の受け手は本物を動かす。
- メールは送らない（この Intent の経路にメールは無い）。

## 5. 環境の確かめ（FR7、計画の Step 13）

```bash
docker buildx imagetools inspect eclipse-temurin:25.0.4_7-jre-noble
docker buildx imagetools inspect eclipse-temurin:25.0.4_7-jdk-noble
docker buildx imagetools inspect otel/opentelemetry-collector:0.162.0
docker buildx imagetools inspect grafana/otel-lgtm:0.34.0
docker buildx imagetools inspect grafana/k6:2.3.0
```

- 各出力の先頭の `Digest:`（index のダイジェスト）と、`Manifests:` に `linux/arm64` と `linux/amd64` があることを記録する。

```bash
docker compose config -q
docker compose -f docker/perf/compose.yaml config -q
docker run --rm -e SCENARIO=loginSuccess -v "$PWD/perf/k6:/scripts:ro" grafana/k6:2.3.0@sha256:<記録した index のダイジェスト> \
  inspect --include-system-env-vars /scripts/scenarios.js
```

- k6 の `inspect` は、ダイジェスト付きのイメージで台本が読み込め、場面 `loginSuccess` が出ることだけを確かめる（負荷は流さない。Build and Test の持ち主）。
- 同じイメージのほかの出現（`perf/README.md`・`README.md`・`docker/hikari-pool.sh`。依頼者の決定 D4: A）にも同じダイジェストを付けたことを `grep` で確かめ、`bash -n docker/hikari-pool.sh` で文法を確かめる（配備したアプリには触れない）。

## 6. テストのデータ

- メールアドレスは予約のドメインだけにする（`example.com`。例: `"rescue-" + UUID.randomUUID() + "@example.com"`）。実在しそうな氏名・宛先は置かない（team.md・リポジトリは公開）。
- パスワードは実行ごとに作る（`TestDatabase.randomSecret()` などで 12 文字以上・72 バイト以内）。起動の前のパスワード（不一致の条件を作る値）も別に作り、どちらもログ・監査の行に出ないことを確かめる。
- R-08 の判定に使う規則外のパスワードは、既存の `InitialAdminIT` の `invalidPassword` と同じ形（12 文字未満）で作る。
- テストのクラスごとに一時のフォルダの H2 を使い、ほかのテストと内部DB を共有しない。

## 7. カバレッジの目標

- バックエンド: 全体の合計と、パッケージごと（`packagesJudgedByTotal` の 7 個を除く）に、行 80%・分岐 70% 以上（team.md）。この計画で変える `user.domain`・`user.service`・`auth.service`・`audit.domain`・`audit.service` はすべてパッケージごとの下限の対象で、足す分岐は 2節の単体テストで覆う。一覧のパッケージの `src/main` は変えない。
- フロントエンド: 変更が無いため、既存の `thresholds` のまま。
- 測るのは計画の Step 16 の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）だけで、その実測の値を code-summary.md に記録する。下限・除外の設定は変えない。
