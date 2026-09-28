# Unit Test Instructions — U3 招待と登録の完了（u3-invitation）

U3 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の結合テスト）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パス。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ、Spring Boot Test | `backend/build.gradle.kts` の `tasks.test`（名前が `*Test`）と `integrationTest`（名前が `*IT`）。テストの JVM のヒープは既存の 1g |
| 性質ベースのテスト | jqwik（既存） | `backend/src/test/resources/junit-platform.properties`（失敗した例の記録は `build/jqwik-database`）。失敗時の乱数の種はテストの出力に残る |
| 構造の検査 | ArchUnit（既存） | 新しい `backend/src/test/java/cherry/mastersmith/invitation/InvitationBoundaryArchitectureTest.java`。既存の `ArchitectureTest`・`mail/MailBoundaryArchitectureTest`・`auth/AuthBoundaryArchitectureTest`・`audit/AuditBoundaryArchitectureTest` はそのまま（緩めない） |
| 内部DB | 組み込みの H2（既存の `common/testsupport/TestDatabase.java`） | Spring を起動する結合テストは、テストごとに一時ディレクトリの H2 を使う。コンテナは使わない（`team.md` の Testing Posture） |
| スキーマの変更の確かめ | Flyway の API（既存の依存） | `V8MigrationIT`・`V8BackwardCompatibilityIT` は Spring を起動せず、組み込みの H2 の一時のファイルに直接当てる。V1〜V7 の複写は `backend/src/test/resources/db/migration-through-v7/` |
| メールの受け手 | SubEtha SMTP（U1 が足したテストの依存、Apache 2.0）の既存の `mail/testsupport/SmtpTestServer.java`（宛先を拒む設定を含む）、何も返さない受け手 `mail/testsupport/SilentSmtpServer.java`、閉じた番号 `MailTestApplication.closedPort()` | JVM の中で起動して実際に受ける。送信の部品をモックにしない（`team.md` の Testing Posture）。時間切れはテストの設定で 1000 ミリ秒 |
| アプリの起動（U3 の結合テスト） | 新しい `invitation/testsupport/`（本番のテンプレートのまま、ベース URL・受け手の番号・差出人・短い時間切れ・bcrypt の cost 4 で起動する部品） | U1 の `MailTestApplication.start` はテスト用のテンプレートに差し替えるため、招待のテンプレートを確かめるテストでは使わない |
| HTTP の確かめ | 既存の `common/testsupport/HttpTestClient.java`・`auth/testsupport/AuthApi.java`・`auth/testsupport/AuthTestTokens.java`・`access/testsupport/AdminTestUsers.java`・`user/testsupport/TestUserAccounts.java` | 実際のアクセストークンで呼ぶ。招待の ID・招待のメールアドレスを主体にしたトークンは `AuthTestTokens` で正しい鍵で作る |
| ログの確かめ | 既存の `common/testsupport/LogEvents.java`・`JsonLogRecords.java` | 漏えいのテストは `logging.level.cherry.mastersmith=TRACE`（既存の `user/web/MeSecretLeakIT.java` と同じ形） |
| 監査の行の確かめ | 既存の `audit/testsupport/AuditRows.java`・`FailingAuditEventRepositoryConfig.java` | 書き込みの失敗は既存の差し替えの形で作る |
| 時計 | 既存の `auth/testsupport/MutableClock.java` | 有効期限・保存の日数の境界を実時刻に頼らず進める |
| 待ち合わせ | 新しい `invitation/testsupport/` の `InvitationBarrier` の差し替え（`CountDownLatch`・`CyclicBarrier`、上限の時間つき） | 同時の招待・同時の完了と取り消しと送り直しを確実に重ねる。`Thread.sleep` と実時刻に頼らない |
| 差し替え | Mockito（Spring Boot の BOM、既存） | 単体テストだけで使う（5節） |
| カバレッジ | JaCoCo（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification` と `packagesJudgedByTotal` |

新しいテストの依存と、新しいテストの設定のファイルは足さない。既存の `test`・`integrationTest` のタスクと名前の決まり（`XxxTest`・`XxxIT`）をそのまま使う。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U3 のパッケージ `cherry.mastersmith.invitation` と、U3 が手を入れるパッケージ（`audit`・`mail`・`user`）、U3 が決まりを足すフィルターの連鎖の既存の確かめ（`ArchitectureTest`、差し込み口の検査 `common.security`、order の並びの `AppearanceSecurityContributorTest`、`config.SecurityExtensionIT`、`access.web.ApiDefaultAccessIT`）だけに絞る。

単体テスト（`*Test`。値の型と純粋な関数、テンプレートの検査と中身、設定・トークン・送信の入口、監査の組み立てと受け取り、招待の管理と登録の完了と定期の削除の業務処理、構造の検査）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.invitation.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.mail.*' --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.common.security.*' --tests 'cherry.mastersmith.appearance.web.AppearanceSecurityContributorTest'
```

結合テスト（`*IT`。Spring と組み込みの H2 と JVM の中の受け手を起動する API・メール・監査・漏えい・同時の操作・定期の削除と、Flyway の API で当てる V8 の確かめ）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.invitation.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.mail.*' --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.config.SecurityExtensionIT' --tests 'cherry.mastersmith.access.web.ApiDefaultAccessIT'
```

V8 と生成列の確かめだけ（Step 7）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.invitation.repository.V8MigrationIT' --tests 'cherry.mastersmith.invitation.repository.V8BackwardCompatibilityIT' --tests 'cherry.mastersmith.invitation.repository.InvitationSchemaIT'
```

招待メールのテンプレートの検査だけ（Step 11）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.mail.template.*'
```

U3 の API の結合テストだけ（Step 23）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.invitation.web.*'
```

構造の検査だけ（新しい境界と、変えていない全体の決まりと機能ごとの境界）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.invitation.InvitationBoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.mail.MailBoundaryArchitectureTest' --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest' --tests 'cherry.mastersmith.audit.AuditBoundaryArchitectureTest'
```

最初のテストより前の確かめ（Step 3）: 上の単体テストと結合テストのコマンドを変更の前の状態で実行し、既存のテストが通ることを確かめる。`invitation` のテストがまだ無くても、ほかの指定に当たるため、Gradle の「当たるテストが無い」の失敗にならない。

- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest` または `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（`project.md` の Testing Posture）。例: `./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.invitation.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.mail.*' --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.common.security.*' --tests 'cherry.mastersmith.appearance.web.AppearanceSecurityContributorTest'`
- U3 のテストはコンテナを使わないため、コンテナの実行環境（colima）が無くても動き、飛ばされない。パッケージごとのカバレッジの実測（Step 25）と統合の前の `./gradlew verify`（Step 30）は対象DB のテストを含むため、colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して実行する（渡さないと対象DB のテストが SKIPPED になり、パッケージごとの下限の判定が崩れる）:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- 既存の E2E（Step 29、`verify` と CI の外）は Mailpit を起動してから流す:

```bash
docker compose --profile mail up -d mailpit
./gradlew e2eTest
docker compose --profile mail rm -sf mailpit
```

## 3. テストの一覧（Standard の量）

| 部品 | 単体（`*Test`） | 結合（`*IT`） |
|---|---|---|
| トークン・URL・ベース URL（`invitation.domain`） | `InvitationTokenTest`（7 件＋jqwik 2 件）、`BaseUrlRuleTest`（7 件）、`RegistrationUrlTest`（3 件） | — |
| 有効の判定・拒否の理由・ページ（`invitation.domain`） | `InvitationValidityTest`（6〜8 件＋jqwik 1 件）、`InvitationPagingTest`（6 件＋jqwik 1 件） | — |
| 入力の検証（`invitation.domain`） | `InvitationRequestValidationTest`（8 件＋jqwik 1 件）、`RegistrationValidationTest`（8 件） | — |
| 値の型と code（`invitation.domain`） | `InvitationAvailabilityTest`・`InvitationProblemTypesTest`・出来事の文字列化・`SendResultTest`（合わせて 6〜8 件） | — |
| スキーマ（V8） | — | `V8MigrationIT`（5 件）、`InvitationSchemaIT`（3 件）、`V8BackwardCompatibilityIT`（3 件） |
| DB アクセス（`invitation.repository`） | — | `InvitationRepositoryIT`（8 件） |
| テンプレート（`mail.template`） | 既存の `MailTemplateLintTest`（本番の `invitation` が自動で対象）、既存の `MailTemplateRegistryTest` の直し、`InvitationTemplateContentTest`（5 件） | — |
| 設定・トークン・送信の入口（`invitation.service`） | `InvitationSettingsTest`（8 件）、`InvitationMailDispatcherTest`（6 件）、`InvitationTokenIssuerTest`・`InvitationProblemTypeCatalogTest`（3 件） | — |
| 監査（`audit.domain`・`audit.service`） | 既存の `AuditEventFactoryTest` に足す（5〜6 件）、既存の `AuditEventListenerTest` に足す（5〜6 件）、既存の `AuditEventTest` の長さの確かめ（自動） | `InvitationAuditIT`（6〜8 件）、`InvitationAuditWriteFailureIT`（5 件） |
| 招待の管理（`invitation.service`） | `InvitationServiceTest`（8 件） | `InvitationConcurrencyIT`（1〜2 件）、`InvitationSendResultIT`（2 件） |
| 登録の完了（`invitation.service`） | `RegistrationServiceTest`（6〜8 件） | `RegistrationRollbackIT`（2 件）、`RegistrationConcurrencyIT`（4 件） |
| 定期の削除（`invitation.service`） | `InvitationCleanupJobTest`（4 件） | `InvitationCleanupIT`（5 件） |
| API（`invitation.web`） | — | `InvitationAdminApiIT`（8 件）、`InvitationMailIT`（6〜8 件）、`InvitationSendFailureIT`（5 件）、`InvitationSendConnectionIT`（1 件）、`RegistrationApiIT`（8 件）、`RegistrationPublicScopeIT`（3 件）、`InvitedPersonAuthenticationIT`（3 件）、`InvitationSecretLeakIT`（1〜2 件、場面をまとめる）、`InvitationFlowIT`（1 件） |
| `user` の伏せ字の口（計画の9節の決定 3） | 既存の `UserAccountServiceTest` の `findDisplayName`・`existsByEmail` の直しと伏せ字の文字列化 | 既存の `UserRepositoryIT` に伏せ字の引数の検索を足す（2 件） |
| 構造の検査 | `InvitationBoundaryArchitectureTest`（6 件）、既存の境界テストをそのまま通す | — |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`@DisplayName`・メソッド名）は英語で書く。テストのデータは日本語でよい。テストのクラスは対象と同じパッケージに置く（例: `backend/src/test/java/cherry/mastersmith/invitation/web/RegistrationApiIT.java`）。

`team.md` の必須のテストのうち U3 が受け持つもの:

| 必須のテスト | 確かめるテスト |
|---|---|
| 招待と登録の完了: 有効期限の境界（直前は有効、ちょうどと直後は無効） | `InvitationValidityTest`（jqwik を含む）・`RegistrationApiIT` |
| 招待と登録の完了: 使い終えた・取り消した招待の再使用の拒否 | `RegistrationApiIT`・`RegistrationConcurrencyIT` |
| 招待と登録の完了: 改ざん・存在しない招待の拒否と、応答から利用者の存在を推測できないこと（理由によらず同じ応答） | `RegistrationApiIT`・`InvitationTokenTest` |
| 招待と登録の完了: 招待中の人のログイン・トークンの更新・アクセストークンの認証の拒否 | `InvitedPersonAuthenticationIT` |
| 認可: 未認証 401・管理者でない 403・管理者の成功（4本）と、公開の2つの POST の範囲 | `InvitationAdminApiIT`・`RegistrationPublicScopeIT` |
| 監査ログ: 5つの出来事の必須の項目、記録しない場合、書き込みの失敗で応答が変わらない、巻き戻しで成功の監査が残らない | `InvitationAuditIT`・`InvitationAuditWriteFailureIT`・`RegistrationRollbackIT` |
| 秘密情報の漏えい: トークン・ハッシュ・URL・パスワード・メールアドレスがログ（TRACE を含む）・監査・応答に無い | `InvitationSecretLeakIT`・`InvitationTokenTest`・`RegistrationUrlTest` |
| メール: 本文のエスケープ（`registrationUrl`・`validityHours`）とテンプレートの描画（ja・en、件名、`{{` の残り、ライセンスヘッダー） | `MailTemplateLintTest`（自動）・`InvitationTemplateContentTest`・`InvitationMailIT` |
| メール: 招待の URL はベース URL だけから（Host を変えても同じ、ベース URL が無いときは Host を変えても送らない） | `InvitationMailIT` |
| メール: ヘッダーへの差し込み（宛先の CR・LF の拒否） | `InvitationRequestValidationTest`・`InvitationMailIT` |
| メール: 送信の失敗（拒む・応答しない・宛先の拒否）で宛先・SMTP の応答・資格情報が応答とログに無い | `InvitationSendFailureIT` |
| 有効期限の長さの設定の境界と、48 時間の設定で本文に「48」、`expiresAt` が＋48 時間 | `InvitationSettingsTest`・`InvitationTemplateContentTest`・`InvitationMailIT` |

## 4. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上（既存の `jacocoTestCoverageVerification`）。
- パッケージごと: 新しい `cherry.mastersmith.invitation.web`・`invitation.service`・`invitation.domain`・`invitation.repository`（一覧に無いため自動で対象）と、手を入れる `audit.domain`・`audit.service`・`mail.template`と、計画の9節の決定 3 で手を入れる `user.domain`・`user.service`のそれぞれで行 80%・分岐 70% 以上（`team.md` の Testing Posture、NFR9.6）。どれも `packagesJudgedByTotal` に無い。`InvitationProperties` は既存の除外（設定値だけのクラス `**/*Properties.class`）に当たる。
- 一覧に足さない。一度外したパッケージは戻さない。U3 のために計測の除外を足さない。
- 単位だけのカバレッジは、単位のテストを実行した後に報告を作って見る:

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.invitation.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.mail.*' --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.common.security.*' --tests 'cherry.mastersmith.appearance.web.AppearanceSecurityContributorTest' :backend:integrationTest --tests 'cherry.mastersmith.invitation.*' --tests 'cherry.mastersmith.audit.*' --tests 'cherry.mastersmith.mail.*' --tests 'cherry.mastersmith.user.*' --tests 'cherry.mastersmith.config.SecurityExtensionIT' --tests 'cherry.mastersmith.access.web.ApiDefaultAccessIT' :backend:jacocoTestReport
```

  報告は `backend/build/reports/jacoco/test/html/` の各パッケージのページ。ほかの機能のテストが通る分を含まないため目安で、下限の判定は `./gradlew verify` の段（`jacocoTestCoverageVerification`）で行い、値は Step 25・Step 30 で実測して記録する。

## 5. 差し替え（モック・スタブ）の方針

- **内部DB はモックにしない**: V8・生成列と一意の制約・行の排他・一覧の並び・送信の結果の条件つきの更新・削除・利用者の作成の巻き戻しは、本番と同じ組み込みの H2 で確かめる（`team.md` の Testing Posture）。
- **メールはモックにしない**: 結合テストは U1 の本物の送信の部品と本番のテンプレートで、JVM の中の受け手に送る。受け手が拒む・応答しない・宛先を拒む場合は、閉じた番号・`SilentSmtpServer`・`SmtpTestServer` の宛先を拒む設定で作る。応答しない受け手を待つときは、受け手が接続を受け付けたことを上限の時間つきで待つ（実時刻で待たない）。
- **業務処理の単体テスト**: `InvitationService`・`RegistrationService`・`InvitationCleanupJob` の単体テストに限り、`InvitationRepository`・`UserAccountService`・`InvitationMailDispatcher`・`ApplicationEventPublisher` を Mockito で差し替え、呼ばれる・呼ばれない、順、結果の型を確かめる。`TransactionTemplate` は渡した処理をそのまま実行する差し替えか、実際の H2 の結合テストで確かめる。
- **送信の入口の確かめ**: `InvitationMailDispatcherTest` では `TransactionSynchronizationManager` の実際のトランザクションの有無を、テストの中で `TransactionTemplate`（テスト用のトランザクションの管理）で作って確かめる。`MailSender` は単体テストだけ差し替える。
- **同時の操作**: `InvitationBarrier` をテストの設定で差し替え、招待の追記の直前と行の排他を得た直後で待ち合わせる。待ち合わせには上限の時間を置く。
- **監査の書き込みの失敗**: 既存の `audit/testsupport/FailingAuditEventRepositoryConfig.java` と同じ形で、5つの出来事それぞれで記録を失敗させる。既存の `AuditWriteFailureIT` の期待は変えない。
- **時刻**: 有効期限と保存の日数は `MutableClock` で進める。定期の削除の cron はテストの設定で止め（`mastersmith.invitation.cleanup.cron=-`）、削除はテストの中から直接呼ぶ。
- **V8 の確かめ**: Spring を起動せず、Flyway の API に移行の置き場（本番の `classpath:db/migration` と、テストの資源の V1〜V7 の複写）を渡して当てる。同時の追記の確かめ（生成列の (d)）は JDBC の2つの接続とテストの中の待ち合わせで行う。

## 6. テストのデータ

- **メールアドレス**: `example.com` などの予約されたドメインだけを使う（例 `hanako@example.com`・`invitee-01@example.com`）。実在の宛先を書かない。漏えいのテストでは見分けやすい値（例 `leak-check-invitee@example.com`）を使い、短い文字列の偶然の一致で確かめが崩れないようにする。
- **管理者と利用者**: 既存の `AdminTestUsers`・`TestUserAccounts` の形でテストの中で作る。登録済みの確かめは、招待の前に同じメールアドレスの利用者を作って行う。
- **パスワード**: テストの中の定数でよいが、Gitleaks に当たらない仮の値にする（既存の `AdminTestUsers.PASSWORD` の形）。境界の値は 11・12 コードポイント、72・73 バイト、絵文字 11・12 文字。
- **トークン**: 本物のトークンは受け手で受けたメールから取り出す。改ざんは1文字を変えた値、形の誤りは 42・44 文字と使えない文字（`+`・`/`・`=`・空白）を含む値、存在しないトークンは新しく作った正しい形の値。
- **ベース URL**: テストの設定で `http://localhost:<番号>`・パスつき（`https://example.com/app/`）・末尾の `/` つきを使い、形の誤り（`ftp://`・`http://user@example.com`・`?`・`#`）を別に確かめる。
- **有効期限の長さ**: 既定の 24 時間、1 時間、48 時間、起動を止める値（`0h`・`-1h`・`90m`）。
- **氏名**: 日本語の氏名（例「山田 花子」）、254・255 コードポイント、空白だけ、Cc・Cf を含む値。
- **内部DB**: Spring を起動する結合テストは、テストごと（またはテストのクラスごと）に一時ディレクトリの H2 を使う（`TestDatabase`）。V8 の確かめは、テストごとに一時のファイルを作って消す。前のテストの招待・利用者・監査の行に頼らない。
- **V1〜V7 の複写**: `backend/src/test/resources/db/migration-through-v7/` に置き、本番のファイルと1バイトも違わないことをテストで確かめる。
- **受けたメール**: 受け手のメールの本文・トークンをテストの出力や記録に写さない（確かめは中身の比較だけ）。

## 7. 性質ベースのテストの種

- jqwik の失敗時の乱数の種は、テストの出力（失敗の詳細）に出る。再現するときは、その種を `@Property(seed = "...")` に一時的に書いて同じ単位のコマンド（2節）で実行し、直した後に外す。
- 対象は純粋な関数だけ: `InvitationToken`（読み取れるのは形が合うときだけ、作った値はいつも読み取れる）、`InvitationValidity`（有効 ⇔ PENDING かつ今が有効期限より前）、`InvitationPaging`（位置と page の関係）、`InvitationRequestValidation`（CR・LF を含む値はいつも拒否）（`team.md` の Testing Posture、NFR9.7）。
