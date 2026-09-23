# Unit Test Instructions — U4 監査ログ（u4-audit-log）

本書は、U4 のテストの枠組み、この単位に絞った実行のコマンド、テストの一覧、カバレッジの目標、モックとテストデータの扱いを定める。方法は Testing Contract の test-after（層ごとに実装してから、その層のテストを書いて実行する）、テストの量はワークフローの Test Strategy の Standard（部品ごとに 5〜8 件、主な境界に結合テスト）に、チームの進め方の必須の監査ログのテストを加えたものである。手順の番号は `code-generation-plan.md` の Step を指す。

U4 は画面を持たないため、フロントエンドのテストと新しい E2E は無い（計画の 3章）。U4 は API を持たないため、API の段のテストの代わりに、呼び出し元の境界（U2 のログイン・ログアウト、U3 の 401／403）から記録までの結合テストを Step 9 に置く。

## 1. テストの枠組みと設定

テストの枠組みは U1 が、認証のテストの補助は U2 が、アクセス拒否のテストの補助は U3 が用意済みで、U4 は新しい道具を入れない（Step 2 でこの単位のコマンドが動くことを確かめる）。

| 対象 | 道具 | 設定の場所（U1 が用意済み） |
|---|---|---|
| バックエンドの単体テスト（`*Test`） | JUnit 5、AssertJ、Mockito、jqwik（性質ベース）、ArchUnit | `backend/build.gradle.kts` の `test` タスク（名前が `Test` で終わるクラスだけ） |
| バックエンドの結合テスト（`*IT`） | Spring Boot Test、組み込みの H2（ファイル保存、一時ディレクトリ）、U1 の `HttpTestClient`・`TestDatabase` | `backend/build.gradle.kts` の `integrationTest` タスク（名前が `IT` で終わるクラスだけ） |
| バックエンドのカバレッジ | JaCoCo（`test` と `integrationTest` の実行記録を合わせる） | `:backend:jacocoTestReport`、`:backend:jacocoTestCoverageVerification`（行 80%・分岐 70%） |
| ログの確認 | U1 の `JsonLogRecords`・`LogEvents` | `backend/src/test/java/cherry/mastersmith/common/testsupport/` |
| 発行した SQL の回数 | U2 の `SqlStatementCounter`（`spring.jpa.properties.hibernate.session_factory.statement_inspector`） | `backend/src/test/java/cherry/mastersmith/auth/testsupport/` |

U2・U3 が用意したテストの補助のうち、U4 が使うもの:

| 補助 | 用途 |
|---|---|
| `auth.testsupport.AuthApi`・`AuthApiTestConfig` | ログイン・トークンの更新・ログアウトを実行して監査の記録を起こす |
| `auth.testsupport.MutableClock` | 時刻を進める（ロック中の失敗など。実時間に頼らない） |
| `auth.testsupport.SqlStatementCounter` | 1件の記録で発行される SQL が挿入1回だけであることの確認（NFR1.3） |
| `auth.testsupport.TestSigningKeyEnvironmentPostProcessor` | Spring を起動するテストで署名鍵を自動で用意する |
| `auth.testsupport.AuthTestTokens` | 形の崩れたトークン・別の鍵の署名・`alg: none` で 401 を起こす（理由ごとの記録） |
| `access.testsupport.AdminAccessTestConfig`・`AdminTestUsers` | 管理者・管理者でない利用者を作り、403・401 を起こす |
| `access.testsupport.PublicApiTestRules` | U4 の結合テストで `/api/**` の既定の拒否が邪魔になる場合に、テストの中だけで公開にする（本番の設定には無い） |
| `user.service.UserAccountService` | 利用者の作成（テストデータ） |

U4 が Step 2 で足すテストの補助（テストのソースの中だけ。`cherry.mastersmith.audit.testsupport`）:

| 補助 | 用途 |
|---|---|
| `AuditRows` | `JdbcTemplate` で `audit_events` の行を読む（監査を見る API が無いため。計画の C8）。件数・最後の1件・日時の順の一覧 |
| `FailingAuditEventRepositoryConfig` | 追記で必ず例外を投げる保存の部品を `@Primary` で差し替える（追記の失敗と接続を借りる失敗の再現。計画の C9） |
| `SlowAuditWriteConfig` | 書き込みの時間の測り方（`LongSupplier`）を差し替え、200 ミリ秒を超えた場合を実時間に頼らず再現する（計画の C5） |

- テストの説明文（`@DisplayName`、テストのメソッド名）は英語で書く。テストデータは日本語でよい。
- Java のテストは `backend/src/test/java` の `cherry.mastersmith.audit` の下に、対象と同じパッケージで置く。
- 常に通るだけのテストは書かない。各部品のテストは、正しく動く場合に加えて、誤りや境界の場合を少なくとも2件含める。

## 2. この単位のテストの実行

すべてリポジトリのルートで実行する。どのコマンドも U4 のテストだけに絞っている（Step 10 の回帰だけは全体を実行する）。Gradle の `--tests` は、その直前に書いたタスクにだけ効く。`--tests 'cherry.mastersmith.audit.*'` は下のパッケージ（`audit.domain` など）も含む。

### 2.1 前提の用意（初回と依存関係の更新のとき）

```bash
git submodule update --init
(cd vendor/make-you-chic-ui && npm ci && npm run build)
(cd frontend && npm ci)
```

フロントエンドの用意は、`./gradlew verify`（Step 11）でフロントエンドの検査とビルドが動くために要る。U4 のテストそのものには要らない。

### 2.2 実行の枠組みの確認（Step 2 で実行する。U4 のテストが無い段階でも動く）

```bash
./gradlew :backend:testClasses
```

テストのソースのコンパイル（U4 のテストの補助を含む）を確かめる。テストの成否を判定するものではない。

### 2.3 手順ごとのコマンド

| 手順 | コマンド |
|---|---|
| Step 4 表・エンティティ・切り詰め・写し取り | `./gradlew :backend:test --tests 'cherry.mastersmith.audit.domain.*' :backend:integrationTest --tests 'cherry.mastersmith.audit.domain.AuditSchemaIT'` |
| Step 6 保存の部品と構造の検査 | `./gradlew :backend:test --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest' :backend:integrationTest --tests 'cherry.mastersmith.audit.repository.AuditEventRepositoryIT'` |
| Step 8 受け取りと記録（単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.audit.service.*'` |
| Step 9 2つの経路の結合 | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.audit.service.*IT'` |
| Step 10 全体の回帰（U1〜U3 を含む） | `./gradlew :backend:test :backend:integrationTest` と `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run)` |
| Step 11 全体の検査と E2E | `./gradlew verify` と `./gradlew e2eTest`（U1〜U3 の3本。U4 は E2E を増やさない） |

### 2.4 U4 のテストをまとめて実行する（カバレッジの報告を含む）

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.audit.*' \
  :backend:integrationTest --tests 'cherry.mastersmith.audit.*' \
  :backend:jacocoTestReport
```

- 報告は `backend/build/reports/jacoco/test/html/index.html`（パッケージごとの表で `cherry.mastersmith.audit.*` を見る）。このコマンドは U4 のテストだけを実行するため、アプリ全体に対する `jacocoTestCoverageVerification` は含めない（U1〜U3 のクラスが測られず、下限の判定が意味を持たないため）。
- 全体の下限の判定（行 80%・分岐 70%）は、統合の前の関門 `./gradlew verify`（全単位の全検査）で行う（Step 11 と Build and Test）。

## 3. テストの一覧（部品ごと）

件数は目安（Standard: 部品ごとに 5〜8 件）。性質ベースのテストは1つのプロパティを1件と数える。

### 3.1 バックエンドの単体テスト（`*Test`）

| テスト | 対象 | 主な確認 | 件数 |
|---|---|---|---|
| `audit.domain.AuditTextTest` | 切り詰め | jqwik: 結果は上限以下のコードポイント数、元の値の先頭と一致する、壊れたサロゲートペアを含まない、上限以下の値はそのまま。明示の例: 300 文字のメールアドレス（先頭 254 文字）、600 文字の要求のパス、512 文字目がサロゲートペアの途中になる User-Agent、null と空文字、改行と制御文字を含む値を加工しないこと | 8 |
| `audit.domain.AuditEventFactoryTest` | 出来事からの写し取り | 4つの種類と結果の対応（BR1.2）、U2 の `LoginFailureReason` と U3 の `AccessDeniedReason` の全値の変換、日時が出来事の日時（BR1.5）、存在しないメールアドレスもそのまま（BR1.6）、アクセス拒否だけ要求のパスが入り認証の出来事では空、問い合わせの部分が入らない、長い値が切り詰められる、U3 の出来事から取るメールアドレスが伏せ字にならない（`enteredEmail()` を使う）、U2 の `userId` を記録しない | 8 |
| `audit.domain.AuditEventTest` | 監査イベント | 必須の項目がそろう、必須でない項目が空でも作れる、`toString()` にメールアドレスが出ない、パスワード・トークンの項目を持たない、値を変える手段が無い | 5 |
| `audit.service.AuditEventListenerTest` | 受け取りと記録 | 認証の出来事で1件追記される、アクセス拒否の出来事で1件追記される、追記の例外が呼び出し元へ伝わらない、そのとき ERROR が1回だけ出て全項目のキーと値が載る、ERROR のメッセージが固定の文で例外のメッセージを使わない、ログにパスワード・トークンの値が出ない、200 ミリ秒を超えたら遅れの WARN が1回・以下なら出ない、成功のときに監査の内容をログに出さない、再試行しない（追記の呼び出しが1回） | 8 |
| `audit.service.AuditEventRecorderTest` | 追記の部品 | 新しいトランザクションの指定があること、保存を1回だけ呼ぶこと、保存の例外をそのまま投げ返すこと（受け止めは受け取り側の役目） | 3 |
| `audit.AuditBoundaryArchitectureTest` | 構造の検査（ArchUnit） | 保存の部品に `delete`・`update`・`remove` で始まる操作が無い、`@Modifying` の問い合わせが無い、`audit` の外から `audit.repository` を使わない、トランザクションの指定が `audit.service` にだけある、`audit` に `web` の層が無い（API を持たない） | 5 |

### 3.2 バックエンドの結合テスト（`*IT`、組み込みの H2）

| テスト | 主な確認 |
|---|---|
| `audit.domain.AuditSchemaIT` | V4 の移行が起動時に成功している（`flyway_schema_history`）、監査イベントを保存して読み戻せる、必須でない列が空でも保存できる、日時がタイムゾーンに依存しない時点として保存される、発生の日時の索引がある、切り詰めの上限いっぱい（サロゲートペアを含む）の値が列に収まる |
| `audit.repository.AuditEventRepositoryIT` | 追記して読み戻す、複数件を日時の順に読む、1件の追記で発行される SQL が `insert audit_events` 1回だけ（`SqlStatementCounter`。NFR1.3）、保存した行に更新の SQL が出ない |
| `audit.service.AuditAuthenticationEventsIT` | `LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT` のそれぞれで必須の項目がそろって1件記録される（日時・種類・結果・メールアドレス・失敗の理由・接続元IP・User-Agent・トレースID）、存在しないメールアドレスでのログインの失敗が入力された値と `USER_NOT_FOUND` で記録される、ロック中の失敗が `ACCOUNT_LOCKED` で記録される、認証の出来事では要求のパスが空、記録が要求と同じスレッドで行われる |
| `audit.service.AuditAccessDeniedIT` | 管理者でない利用者の 403 が `ACCESS_DENIED`／`NOT_ADMIN`／メールアドレスあり で記録される、トークン無し・形の崩れ・改ざん・利用者が DB にいない の 401 がそれぞれの理由で記録されメールアドレスは空、要求のパスが正規化済み・問い合わせの部分なしで記録される、有効期限切れの 401 では記録が無い（U3 が出来事を作らない）、応答が返った時点で行が存在する（応答の前に記録する） |
| `audit.service.AuditRollbackIT` | 元の操作の内部DBの更新が取り消されたとき、監査イベントが記録されない（BR1.4、NFR10.2） |
| `audit.service.AuditWriteFailureIT` | 2つの経路それぞれで、追記の失敗と接続を借りる失敗を起こしても、ログイン・ログアウト・401／403 の応答（状態コード・`code`・本文）が変わらない、ERROR が1回だけ出て記録しようとした全項目（メールアドレスを含む）が載る、例外の型が載る、再試行しない |
| `audit.service.AuditTraceIdIT` | traceparent つきの要求と無い要求のそれぞれで、監査イベントのトレースIDが同じ要求のアプリのログのトレースIDと一致する（`JsonLogRecords`） |
| `audit.service.AuditSecretLeakIT` | `cherry.mastersmith.audit` を TRACE にしてログイン成功・失敗・ログアウト・403・書き込みの失敗を起こし、監査イベントの行とアプリのログにパスワード（平文・ハッシュ `$2a$`）・アクセストークン・リフレッシュトークン・Authorization ヘッダーが出ない、すべての行が JSON でトレースIDが付く |
| `audit.service.AuditWriteTimingIT` | 書き込みが 200 ミリ秒を超えたときに遅れの WARN が1回出る（時間の測り方の差し替えで再現。実時間・`sleep` に頼らない） |
| `audit.service.AuditNotInAppLogIT` | 成功した記録がアプリのログに出ず、内部DBの表にだけ1件増える（BR3.2、NFR9.2） |

## 4. カバレッジの目標

| 対象 | 目標 | 確かめ方 |
|---|---|---|
| バックエンドの U4 のパッケージ（`cherry.mastersmith.audit`） | 行 80% 以上・分岐 70% 以上 | 2.4 の JaCoCo の報告のパッケージごとの値 |
| アプリ全体 | 行 80% 以上・分岐 70% 以上（U1〜U3 の値を下回らない） | `./gradlew verify` の 7 の段 |

- 計測から外すのは、U1 が決めた範囲（起動クラス・設定値だけの record（`*Properties`）・型の宣言）に限る。U4 は除外を増やさない。U4 は設定の型を持たない。
- 下限に届かないときは、下限を下げずにテストを足すか、差を依頼者に示す。

## 5. モックとスタブの扱い

- 単体テストでは、保存の部品（`AuditEventRepository`）と時間の測り方（`LongSupplier`）を Mockito で置き換える。出来事は実物の `AuthenticationEvent`・`AdminAccessDeniedEvent` を組み立てて渡す（項目のずれを見落とさないため）。
- 結合テストでは、受け取りの仕組み・トランザクション・DB を実物で動かし、モックにしない（2つの受け取りの経路の違いを見落とさないため）。実際の番号で待ち受けるアプリに U1 の `HttpTestClient` と U2 の `AuthApi` で送る。
- 書き込みの失敗だけは、テスト用に失敗する保存の部品を `@Primary` の Bean で差し替えて起こす（本物の DB を壊さない）。接続を借りる失敗も同じ差し替えで別の例外として起こす。
- 元の操作の取り消しは、U2 の更新を失敗させる差し替えで再現する。
- 時刻は出来事の日時をそのまま記録するため、U4 は時計に依存しない。ロック中の失敗など U2 の振る舞いが要る場合だけ `MutableClock` を `@Primary` で差し替える。書き込みの時間は `LongSupplier` の差し替えで進め、`sleep` や実時刻に頼らない。
- 監査イベントの読み取りは `JdbcTemplate`（`AuditRows`）で行う。監査を見る API を作らないため、テストのためだけの API・操作を本番のコードに足さない。

## 6. テストデータの扱い

- 利用者は、テストの中で `user.service.UserAccountService.createUser` により作る（メールアドレスは一意になる値、パスワードは明らかにテスト用と分かる 12 文字以上の値）。本物らしい秘密情報をソースに書かない。
- 結合テストはクラスごとに一時ディレクトリの H2 を使い、テストごとに必要な利用者と記録を作る。実行の順番に依存させない（件数の確認は、そのテストで起こした操作の前後の差で見る）。
- 署名鍵はテストの実行のたびに作られる（U2 の仕組み）。テストに `.env` などの秘密情報の設定は要らない。
- 切り詰めのテストの長い値・絵文字・改行を含む値は、Gitleaks の誤検出を招かない形の文字列で書く（`.gitleaks.toml` の除外は増やさない）。
- 改ざんしたトークンで 401 を起こすときは、U3 と同じく `hs256WithOtherKey`（別の鍵で署名）と `algNone` を使う（U2 の `tamperedSignature` の落とし穴の記録は U3 の `code-summary.md` にある）。
- jqwik の失敗時の乱数の種はテストの出力に出る（U1 の設定）。再現は `@Property(seed = "...")` で行う。

## 7. チームの必須のテストとの対応（U4 に関わるもの）

| team.md の必須のテスト | テスト |
|---|---|
| 監査ログ: 対象イベントごとに必須の項目が記録されること | `AuditAuthenticationEventsIT`（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`）、`AuditAccessDeniedIT`（`ACCESS_DENIED`） |
| 監査ログ: 存在しないユーザーIDでのログイン失敗も記録されること | `AuditAuthenticationEventsIT` |
| 監査ログ: ★書き込みに失敗したときに操作を失敗させるか（U4 の BR3.1・NFR10.1 で「失敗させない」と決着済み） | `AuditWriteFailureIT`、`AuditEventListenerTest` |
| 秘密情報の漏えい: ログ・監査ログにパスワード・トークンの値が含まれないこと | `AuditSecretLeakIT`、`AuditEventTest`、`AuditEventListenerTest` |
| 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる | `AuditTraceIdIT`、`AuditSecretLeakIT`（すべての行が JSON） |
| 認可: 管理者フラグなし 403（U4 の観点はその記録） | `AuditAccessDeniedIT` |
| トークン: 改ざん・`alg: none`・期限切れの扱い（U4 の観点は記録の要否） | `AuditAccessDeniedIT`（理由ごとの記録、期限切れは記録しない） |
| 時刻に依存する処理を実時刻に頼らない | `AuditWriteTimingIT`（測り方の差し替え）、`MutableClock` の利用 |
| 性質ベースのテスト（純粋な関数） | `AuditTextTest`（jqwik） |
| ロック・ログイン・初期管理者・画面に関する必須のテスト | U1〜U3 の単位で実施済み（U4 の対象外。U4 は画面を持たない） |

## 8. 前提条件

- Java 25（Temurin）、Node.js 24、Git のサブモジュール（`vendor/make-you-chic-ui`）の取得。Node.js とサブモジュールは `./gradlew verify`（Step 11）に要る。U4 のテストだけなら Java だけでよい。
- 内部DBのテストは組み込みの H2 を使い、コンテナの実行環境は要らない（team.md の学び）。
- テストに秘密情報の設定（`.env` など）は要らない。署名鍵と初期管理者の値はテストの実行のたびに作る（U1・U2 の仕組み）。
- Step 10 の回帰は U1〜U3 のテストをすべて含むため、実行に時間がかかる。手順の途中では 2.3 の絞り込んだコマンドを使う。
