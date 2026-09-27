# Unit Test Instructions — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の結合テスト）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パス。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ、Spring Boot Test | `backend/build.gradle.kts` の `tasks.test`（名前が `*Test`）と `integrationTest`（名前が `*IT`）。テストの JVM のヒープは既存の 1g |
| 性質ベースのテスト | jqwik（既存） | `backend/src/test/resources/junit-platform.properties`（失敗した例の記録は `build/jqwik-database`）。失敗時の乱数の種はテストの出力に残る |
| 構造の検査 | ArchUnit（既存） | 既存の `ArchitectureTest`・`auth/AuthBoundaryArchitectureTest`・`audit/AuditBoundaryArchitectureTest` をそのまま使う（緩めない） |
| 内部DB | 組み込みの H2（既存の `backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java`） | Spring を起動する結合テストは、テストごとに一時ディレクトリの H2 を使う。コンテナは使わない（`team.md` の Testing Posture） |
| スキーマの変更の確かめ | Flyway 12.4.0 の API（既存の依存） | `V7MigrationIT`・`V7BackwardCompatibilityIT` は Spring を起動せず、組み込みの H2 の一時のファイルに直接当てる。V1〜V6 の複写は `backend/src/test/resources/db/migration-through-v6/` |
| HTTP の確かめ | 既存の `common/testsupport/HttpTestClient.java`・`auth/testsupport/AuthApi.java`・`auth/testsupport/AuthTestTokens.java`・`access/testsupport/AdminTestUsers.java` | 実際のアクセストークンとリフレッシュトークンで呼ぶ |
| ログの確かめ | 既存の `common/testsupport/LogEvents.java` | — |
| 監査の行の確かめ | 既存の `audit/testsupport/AuditRows.java`・`FailingAuditEventRepositoryConfig.java` | 書き込みの失敗は既存の差し替えの形で作る |
| SQL の数 | 既存の `auth/testsupport/SqlStatementCounter.java` | ログイン・更新の処理が発行する SQL の数を比べる |
| 時計 | 既存の `auth/testsupport/MutableClock.java` | アクセストークンの有効期限などを実時刻に頼らず進める |
| 差し替え | Mockito（Spring Boot の BOM に含まれる、既存） | 単体テストだけで使う（5節） |
| カバレッジ | JaCoCo（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification` と `packagesJudgedByTotal` |

新しいテストの依存と、新しいテストの設定のファイルは足さない。既存の `test`・`integrationTest` のタスクと名前の決まり（`XxxTest`・`XxxIT`）をそのまま使う。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U2 が手を入れるパッケージ `cherry.mastersmith.user`・`cherry.mastersmith.audit`・`cherry.mastersmith.auth` と、全体の構造の検査 `cherry.mastersmith.ArchitectureTest` だけに絞る（`audit`・`auth` は U2 が手を入れるため、既存のテストが U2 の回帰の確かめになる）。

単体テスト（`*Test`。値の型と決まり、項目ごとの誤り、利用者の作成、監査の組み立てと受け取り、プリファレンスとパスワードの変更の業務処理、要求の文脈の読み取り、構造の検査）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.ArchitectureTest'
```

結合テスト（`*IT`。Spring と組み込みの H2 を起動する API・監査・漏えい・同時の操作と、Flyway の API で当てる V7 の確かめ）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.auth.*'
```

V7 の確かめだけ（Step 7）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.repository.V7MigrationIT' --tests 'cherry.mastersmith.user.repository.V7BackwardCompatibilityIT'
```

`/api/me/` の API の結合テストだけ（Step 17）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.user.web.*'
```

構造の検査だけ（変えていない全体の決まりと機能ごとの境界）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest' --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest'
```

最初のテストより前の確かめ（Step 3）: 上の単体テストと結合テストのコマンドを変更の前の状態で実行し、既存のテストが通ることを確かめる（`user`・`audit`・`auth` には既存のテストがあるため、コマンドは最初から通る）。

- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest` または `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（`project.md` の Testing Posture）。例: `./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.ArchitectureTest'`
- U2 のテストはコンテナを使わないため、コンテナの実行環境（colima）が無くても動き、飛ばされない。パッケージごとのカバレッジの実測（Step 19）と統合の前の `./gradlew verify`（Step 21）は対象DB のテストを含むため、colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して実行する（渡さないと対象DB のテストが SKIPPED になり、パッケージごとの下限の判定が崩れる）:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 3. テストの一覧（Standard の量）

| 部品 | 単体（`*Test`） | 結合（`*IT`） |
|---|---|---|
| 氏名と値の決まり（`user.domain`） | `DisplayNameTest`（7〜8 件＋jqwik 3 件）、`PreferenceValuesTest`（6 件＋jqwik 1 件） | — |
| 項目ごとの誤り（`user.domain`） | `PreferencesValidationTest`（5〜6 件）、`PasswordChangeValidationTest`（8 件＋jqwik 2 件） | — |
| 値の型と code（`user.domain`・`audit.domain`） | `RequestOriginTest`・`PasswordChangedEventTest`・`UserProblemTypesTest`・監査の値の長さ（合わせて 6〜8 件） | — |
| スキーマ（V7） | — | `V7MigrationIT`（3〜4 件）、`V7BackwardCompatibilityIT`（4〜5 件）、既存の `UserSchemaIT`・`AuditSchemaIT` に足す（3〜4 件） |
| DB アクセス（`user.repository`） | — | 既存の `UserRepositoryIT` に足す（5〜6 件） |
| 利用者の作成と要約（`user.service`） | 既存の `UserAccountServiceTest` に足す（6〜8 件）、既存の `InitialAdminInitializerTest` の直し、`UserProblemTypeCatalogTest` | `UserCreationIT`（3〜4 件）、既存の `InitialAdminIT` の直し |
| 監査（`audit.domain`・`audit.service`） | 既存の `AuditEventFactoryTest` に足す（3〜4 件）、既存の `AuditEventListenerTest` に足す（4〜5 件） | `PasswordChangedAuditIT`（6〜8 件） |
| プリファレンスとパスワードの変更（`user.service`） | `UserPreferencesServiceTest`（8 件） | `PasswordChangeConcurrencyIT`（1〜2 件）、`PreferencesPartialUpdateIT`（3 件） |
| API（`user.web`・`auth.web`） | `MeRequestContextResolverTest`（5 件） | `MePreferencesApiIT`（8 件）、`MePasswordApiIT`（8 件）、`MeSecretLeakIT`（5〜6 件）、`LoginResponsePreferencesIT`（3〜4 件）、既存の `AuditSecretLeakIT` の列の一覧の直し |
| 構造の検査 | 既存の境界テストをそのまま通す | — |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`@DisplayName`・メソッド名）は英語で書く。テストのデータは日本語でよい。テストのクラスは対象と同じパッケージに置く（例: `backend/src/test/java/cherry/mastersmith/user/web/MePasswordApiIT.java`）。

`team.md` の認証・認可・監査の必須のテストのうち U2 が受け持つもの:

| 必須のテスト | 確かめるテスト |
|---|---|
| パスワードの変更: 今のパスワードの確かめ | `UserPreferencesServiceTest`・`MePasswordApiIT` |
| パスワードの変更: 規則の境界（11・12 文字、空、72・73 バイト、絵文字 11・12 文字、2回の入力の不一致） | `PasswordChangeValidationTest`（jqwik を含む）・`MePasswordApiIT` |
| パスワードの変更: 変更の後のリフレッシュトークンの扱い（ほかの端末も更新できる、決めた動作として明示）と、発行済みのアクセストークンが有効期限まで使えること | `MePasswordApiIT` |
| 認可: 未認証 401、管理者でないログインした利用者の 200・204（`/api/me/` には 403 の場面が無い。機能設計の D1） | `MePreferencesApiIT`・`MePasswordApiIT` |
| 監査ログ: PASSWORD_CHANGED の成功と失敗の必須の項目、入力の誤りとプリファレンスの保存では記録しない、書き込みの失敗で応答が変わらない、巻き戻しで記録しない | `PasswordChangedAuditIT`・`MePreferencesApiIT`・`AuditEventListenerTest` |
| 秘密情報の漏えい: ログ・監査ログ・応答にパスワード・ハッシュ・トークン・メールアドレスが無い（TRACE の追跡を含む） | `MeSecretLeakIT`・`AuditSecretLeakIT`・`PasswordChangedEventTest`・`UserAccountServiceTest` |
| 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い・不正のときの動き、パスワードがログに出ない（初期値を足した後も保つ） | `InitialAdminInitializerTest`・`InitialAdminIT` |
| 本人の行が消えたときの 401（NFR 設計の承認の場の U2 R-01） | `UserPreferencesServiceTest`・`MePreferencesApiIT` |

## 4. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上（既存の `jacocoTestCoverageVerification`）。
- パッケージごと: Step 19 で `packagesJudgedByTotal` から外す `cherry.mastersmith.user.domain`・`user.repository`・`user.service`・`audit.domain`・`audit.service`・`auth.service`・`auth.web`（と、ほかに実際に手を入れた既存のパッケージ）と、新しい `user.web` のそれぞれで行 80%・分岐 70% 以上（`team.md` の Testing Posture、NFR9.6）。前の記録で単独では下回っていた `audit.service`（行 77.2%）はテストを足して上げる。
- 一覧に足さない。一度外したパッケージは戻さない。計測から外すのは既存の除外（起動クラスと設定値だけのクラス）だけで、U2 のために除外を足さない。
- 単位だけのカバレッジは、単位のテストを実行した後に報告を作って見る:

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.ArchitectureTest' :backend:integrationTest --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.auth.*' :backend:jacocoTestReport
```

  報告は `backend/build/reports/jacoco/test/html/` の各パッケージのページ。ほかの機能のテストが通る分を含まないため、この値は目安で、下限の判定は `./gradlew verify` の段（`jacocoTestCoverageVerification`）で行い、値は Step 19・Step 21 で実測して記録する。

## 5. 差し替え（モック・スタブ）の方針

- **内部DB はモックにしない**: DB アクセス・V7・部分の書き換え・条件つきの更新・監査の行・利用者の作成の一意の制約は、本番と同じ組み込みの H2 で確かめる（`team.md` の Testing Posture）。
- **パスワードのハッシュ**: 結合テストは本番の `PasswordEncoder`（bcrypt）を使う（照合1回 約 0.3 秒のため、1つのテストで回数を増やしすぎない）。単体テストで照合の回数やトランザクションとの関係を確かめるときは、既存の `auth/testsupport/CountingPasswordEncoder.java` の形で呼ばれた回数と、呼ばれたときにトランザクションが動いていないこと（`TransactionSynchronizationManager.isActualTransactionActive()` が偽）を記録する（NFR5.1）。bcrypt の cost の設定は変えない。
- **業務処理の単体テスト**: `UserPreferencesService` と `UserAccountService` の単体テストに限り、`UserRepository`・`ApplicationEventPublisher` を Mockito で差し替え、呼ばれる・呼ばれない、知らせた出来事の中身、結果の型を確かめる。`TransactionTemplate` は、渡した処理をそのまま実行する差し替えか、実際の H2 を使う結合テストで確かめる。
- **同時の操作**: 照合の後・書き込みの前の重なりは、`PasswordChangeBarrier` をテストの設定で差し替え、`CountDownLatch` などで待ち合わせて確実に作る。待ち合わせには上限の時間を置き、`Thread.sleep` や実時刻に頼らない。同じメールアドレスの作成の重なりも、テストの中の待ち合わせで作る。
- **監査の書き込みの失敗**: 既存の `audit/testsupport/FailingAuditEventRepositoryConfig.java` と同じ形で、成功と今のパスワードの誤りの両方で記録を失敗させる。既存の `AuditWriteFailureIT` の期待（ERROR に `enteredEmail` が載ること）と、`InitialAdminInitializerTest`・`InitialAdminIT` のログの項目の期待は変えない（依頼者の判断で据え置き。計画の9節の決定 3）。U2 の漏えいのテストで「メールアドレスが無い」を確かめるのは、U2 が足すログ・応答・監査の行の範囲とする。
- **時刻**: アクセストークンの有効期限は既存の `MutableClock` で進める。
- **V7 の確かめ**: Spring を起動せず、Flyway の API に移行の置き場（本番の `classpath:db/migration` と、テストの資源の V1〜V6 の複写）を渡して当てる。1つ前の版の追記の形は JDBC で直接書く。

## 6. テストのデータ

- **利用者**: テストの中で作る。メールアドレスは `example.com` などの予約されたドメインだけを使う（例 `hanako@example.com`）。実在の宛先を書かない。管理者でない利用者と管理者は既存の `AdminTestUsers` の形で用意する。
- **パスワード**: テストの中の定数でよいが、既存のテストと同じく Gitleaks に当たらない仮の値にする（既存の `AdminTestUsers.PASSWORD` の形）。漏えいのテストでは、見分けやすい値（例 今・新しい・確かめで別々の値）を使い、ログ・監査・応答にその値が無いことを確かめる。
- **氏名の境界の値**: 254 コードポイントちょうど・255 コードポイント（ASCII と絵文字の両方）、前後の半角・全角の空白・タブ・U+00A0、内側の改行・タブ・NUL・U+200B・U+200D・U+202E・U+FEFF、日本語の氏名（例 「山田 花子」）。
- **表示の設定の値**: 決めた値（ja・en、light・dark・system、sm・md・lg）と、拒否する値（`EN`・`Dark`・`" md"`・空・null・`xl`）。
- **パスワードの規則の境界**: AC3.2.4 と同じ一覧（11 文字・12 文字・空・72 バイト・73 バイト・絵文字 11 文字と 12 文字・2回の入力の不一致）。今のパスワードの 73 バイトの値。
- **内部DB**: Spring を起動する結合テストは、既存のとおりテストごと（またはテストのクラスごと）に一時ディレクトリの H2 を使う（`TestDatabase`）。V7 の確かめは、テストごとに一時のファイルを作って消す。前のテストの利用者・監査の行に頼らない。
- **V1〜V6 の複写**: `backend/src/test/resources/db/migration-through-v6/` に置き、本番のファイルと1バイトも違わないことをテストで確かめる（片方だけの変更を見逃さないため）。

## 7. 性質ベースのテストの種

- jqwik の失敗時の乱数の種は、テストの出力（失敗の詳細）に出る。再現するときは、その種を `@Property(seed = "...")` に一時的に書いて同じ単位のコマンド（2節）で実行し、直した後に外す。
- 対象は純粋な関数だけ: `DisplayName`（通った値の性質・冪等）、`Language`・`Theme`・`FontSize`（決めた値以外の拒否）、`PasswordChangeValidation`（新しいパスワードの判定が `PasswordPolicy` と一致、確かめの一致）（`team.md` の Testing Posture、NFR9.1・NFR9.2）。
