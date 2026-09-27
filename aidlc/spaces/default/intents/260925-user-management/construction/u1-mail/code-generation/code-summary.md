# Code Summary — U1 メールの描画と送信（u1-mail）

Bolt B1 のコード生成の結果を示す。計画は同じディレクトリの `code-generation-plan.md`（Step 1〜27）、テストの手順は `unit-test-instructions.md`。パスはリポジトリのルートからの相対パス。

- 作業のブランチ: `feature/260925-user-management-b1`（`develop` の `7b7362b483473d25a0e779e8a4cdf6825bac8a8a` から作成）。コミットはしていない（C1〜C7 は依頼者の承認の後）。
- 方法: test-after（層ごとに実装 → その層のテストを書いて実行 → 通ってから次へ）。層は domain → config → template → transport → service → 境界の結合テスト → 構造の検査の順。

## 作ったもの

### アプリのコード（`backend/src/main/java/cherry/mastersmith/mail/`）

| パッケージ | 部品 | 役割 |
|---|---|---|
| `mail.domain` | `MailRequest`・`MailSendResult`・`MailOutcome`・`MailFailureKind`・`MailUnexpectedException`・`MailAddressRule`・`MailRequestValidation` | 契約 C1 の値の型、依頼の確かめ（BR3.1〜BR3.4）、想定外の例外（作る口は `of(Throwable)` だけ） |
| `mail.config` | `MastersmithMailProperties`・`EncryptionMode`・`MailSettings`・`MailConfig` | 起動時の点検（`security-design.md` 2.2 の9行）、方式の分類（2.3）、WARN 1件と起動時の INFO（P2）、テンプレートの準備の Bean |
| `mail.template` | `MailTemplateCatalog`（B1 は空）・`MailTemplateDefinition`・`MailTemplateRegistry`・`TemplatePreparationException`・`RenderedMail`・`RenderedMailInspector` | 一覧・置き場の数え上げ・ja/en の準備・描画・件名と lang |
| `mail.transport` | `SmtpMailTransport`・`SendFailureClassifier`・`SendAttempt` | 組み立て（`MimeMessageHelper`、UTF-8、HTML だけ）、1回だけの送信、失敗の分類 |
| `mail.service` | `MailSender`（契約 C1）・`SmtpMailSender` | 入口。ログ1件、Observation `mastersmith.mail.send`（タグ4つ） |

### テスト（`backend/src/test/java/cherry/mastersmith/mail/` と `backend/src/test/resources/mail/`）

- 単体（157 件）: `MailRequestTest`・`MailSendResultTest`・`MailUnexpectedExceptionTest`・`MailRequestValidationTest`・`MailRequestValidationPropertyTest`（jqwik）・`EncryptionModeTest`・`MailSettingsTest`・`MailConfigTest`・`MailTemplateRegistryTest`・`RenderedMailInspectorTest`・`RenderedMailInspectorPropertyTest`（jqwik）・`MailTemplateLintTest`・`SendFailureClassifierTest`・`SmtpMailTransportTest`・`SmtpMailSenderTest`・`MailBoundaryArchitectureTest`
- 結合（26 件、コンテナを使わない）: `MailConfigurationIT`・`MailSendIT`・`MailSendFailureIT`・`MailTlsIT`・`MailHeaderInjectionIT`・`MailSecretLeakIT`
- テストの支え（`mail/testsupport/`）: `MailTestTemplates`（テスト用の一覧）、`MailTestApplication`（アプリ全体の起動と、`mastersmith.test-fixture.mail-test-templates=true` のときだけ効くテスト用のテンプレートの差し替え）、`SmtpTestServer`（SubEtha SMTP の Wiser、接続の数を数える）、`SilentSmtpServer`（受け付けて何も返さない）、`TestCertificates`（`keytool` で実行のたびに一時のディレクトリへ証明書を作り、終わったら消す）
- テスト用のテンプレート: `mail/test-templates/`（`sample`・`multiline` の ja・en）、`mail/test-templates-invalid/<場合>/`（`missing-en`・`parse-error`・`invalid-name`・`unknown-template`）

### ビルド・基盤・文書

| ファイル | 変更 |
|---|---|
| `.gitmodules`・`vendor/java-mustache-processor` | サブモジュールの追加（タグ `0.1.0`） |
| `settings.gradle.kts` | `includeBuild("vendor/java-mustache-processor")`（Maven Central だけの決まりは変えない） |
| `gradle/libs.versions.toml`・`backend/build.gradle.kts`・`backend/gradle.lockfile` | starter-mail・cherry-mustache-core（`implementation`）、SubEtha SMTP（`testImplementation`） |
| `backend/build.gradle.kts` | `spotbugsGate` に `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`、Spotless の `mailTemplates`（Mustache のコメントのヘッダー） |
| `backend/config/spotbugs-exclude.xml` | 既存の `PREDICTABLE_RANDOM` の1件の除外（理由つき） |
| `build.gradle.kts` | `mailTemplateLicenseHeader`、`mustacheVendorUnchanged`（段 0）、`e2eTest` の Mailpit の前提の確かめ、段 3 の説明 |
| `backend/src/main/resources/application.yaml` | `mastersmith.mail.*`、`spring.mail.properties`（時間切れ 3000・`mail.debug: false`・TLS）、`management.health.mail.enabled: false`、ロガー OFF |
| `compose.yaml`・`.env.example` | Mailpit（profile `mail`）、メールの節（すべてコメント） |
| `frontend/playwright.config.ts` | `webServer.env` に Mailpit への設定 |
| `.github/dependabot.yml` | `docker-compose` の項目 |
| `README.md` | 取得と準備・1コマンドの検査・E2E・環境変数・「メール（U1）」・ライセンス |

## Step 1 と Step 26 の実測

どちらも colima を動かし、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した値。

| 項目 | Step 1（変更の前） | Step 26（変更の後） |
|---|---|---|
| 結果 | 成功（5分27秒） | 成功（4分50秒） |
| 単体テスト（`*Test`） | 733 件（失敗 0・飛ばした 0） | 890 件（失敗 0・飛ばした 0。うち `mail` 157） |
| 結合テスト（`*IT`） | 385 件（失敗 0・飛ばした 0） | 411 件（失敗 0・飛ばした 0。うち `mail` 26）。対象DB のテストは SKIPPED なし |
| 画面のテスト（Vitest） | 316 件 | 316 件（変更なし） |
| 全体のカバレッジ（行） | 98.08%（3941/4018） | 98.09%（4323/4407） |
| 全体のカバレッジ（分岐） | 93.83%（1369/1459） | 94.30%（1620/1718） |
| OSV-Scanner | — | 失敗の条件 0 件・警告 0 件 |

`mail` の下位パッケージごと（Step 26）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `mail.config` | 97.98%（97/99） | 96.19%（101/105） |
| `mail.domain` | 100.00%（68/68） | 98.33%（59/60） |
| `mail.template` | 97.20%（104/107） | 95.65%（44/46） |
| `mail.transport` | 96.30%（52/54） | 96.67%（29/30） |
| `mail.service` | 100.00%（61/61） | 100.00%（18/18） |

どれもパッケージごとの下限（行 80%・分岐 70%）を満たす。除外は足していない（`MastersmithMailProperties` は既存の除外 `**/*Properties.class` に当たる）。`packagesJudgedByTotal` は変えていない。

途中の1回目の Step 26 は `MailSecretLeakIT` の1件で失敗した。確かめの `doesNotContain("535")` が、同じ出力の中の乱数の16進数（トレースID など）に一致したためで、テストの書き方の誤り。U1 とメールの部品のロガーの記録だけを対象に「3桁の番号と空白（または `-`）」の形で確かめる書き方に直し、単独で2回通してから verify を流し直した（上の値は流し直しの結果）。

E2E（Step 21）: Mailpit を止めた状態で `./gradlew e2eTest` が起動の手順を示して失敗し、起動した状態で既存の E2E 6 件が通った（Mailpit が受けたメールは 0 通）。

## 取り込みの5条件（ADR-010、NFR8.3）

| 条件 | 結果 | 確かめたこと |
|---|---|---|
| (a) verify が通り、依存の取得元が Maven Central だけ | 成り立つ | Step 26 の verify が成功。`settings.gradle.kts` の `FAIL_ON_PROJECT_REPOS` と `mavenCentral()` は変えていない |
| (b) 推移依存 `slf4j-api` が lockfile に載り OSV-Scanner の対象 | 成り立つ | `org.slf4j:slf4j-api:2.0.18` が `runtimeClasspath` などの行に載っている（既存の行。部品の宣言 2.0.16 はアプリの 2.0.18 に合わせて解決）。composite build で置き換えた `cherry-mustache-core` 自体は lockfile に載らない（版はサブモジュールの固定先で固める）。OSV-Scanner は既存の `osvScan` で lockfile を検査し 0 件 |
| (c) WAR に core の JAR が入る | 成り立つ | `WEB-INF/lib/cherry-mustache-core-0.1.0.jar` がある |
| (d) 固定先での取得とビルド | 手元では成り立つ（CI は引き継ぎ） | 作業用の場所にこのリポジトリを取り出し、作業の差分を当て、サブモジュールを GitHub から取得して `8d44c36` に固定し、`./gradlew :backend:compileJava` が通った。まだコミットしていないため `git clone --recurse-submodules` でこのブランチをそのまま取り出す確かめはできず、上の形で代えた（計画との差）。部品のリポジトリに入れ子のサブモジュールは無い |
| (e) 部品側のプラグインが Gradle 9.7.1 で動く | 成り立つ | `org.owasp.dependencycheck` 10.0.4 などが Plugin Portal から取られ、構成の評価と部品のコンパイルが通った |

どれも満たしたため、Maven Central への公開に切り替える判断は要らない。

## 環境変数の結び付き・PREDICTABLE_RANDOM・Mailpit・lockfile・ライセンス・サブモジュール

- **環境変数の結び付き（Step 8、計画の9節の決定 4）**: 結び付く。`SystemEnvironmentPropertySource`（名前 `systemEnvironment`）に `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT=1234` などを置くと、自動設定の送信の部品の `getJavaMailProperties` に `mail.smtp.connectiontimeout=1234` が届いた（`MailConfigTest`）。STARTTLS の2つの鍵も結び付いた。生成は止めず、代わりの口（8節の候補）は使っていない。
- **既存の `PREDICTABLE_RANDOM` の1件（Step 17）**: 誤検知と判断し、`backend/config/spotbugs-exclude.xml` に理由を書いて `LoginAttemptStateRepository.lockDummyForUpdate` の1か所だけを外した。`ThreadLocalRandom` は、存在しないメールアドレスのログインで8行のダミーの行がすべて使用中のときに待つ行を選ぶだけで、選んだ番号は応答・ログ・DB の値に出ず、秘密や推測されて困る値の元にならないため。`mail` のコードに `SMTP_HEADER_INJECTION` の指摘は無かった。関門の追加の後に、この1件で `spotbugsGate` が失敗することを確かめてから外した。
- **Mailpit の保持の上限（Step 20、N8）**: イメージ `axllent/mailpit:v1.31.2` の起動の引数の案内で、`--max` の既定が 500 通（超えると古いものから消える）、`--max-age` は既定で無しと確かめた。README に書いた。画面（8025）と API（`/api/v1/info`）が開き、1025・8025 は `127.0.0.1` だけに結び付いていた。確かめの後に止めて消した。
- **Mailpit を指す設定での起動（Step 20）**: 作業用の場所（ホームの下、権限 700）に写した WAR を、使い捨ての内部DB と仮の署名鍵、`SPRING_MAIL_HOST=localhost`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM` で起動し、WARN が無く、起動時の INFO が `state=CONFIGURED`・`encryption=NONE`・テンプレート 0 件、ログに差出人の値が無いことを確かめた。配備したアプリの `.env` とコンテナは変えていない。
- **WAR の中の数え上げ（Step 24、P1）**: 写した WAR の `WEB-INF/classes/mail/templates/` に一覧に無い `stray_ja.html` を入れて起動すると、`UNKNOWN_TEMPLATE` で起動が止まった。1回目はファイルだけを足してディレクトリの項目を足さなかったため数え上げに現れず起動してしまった（写しの作り方の誤り）。Gradle が作る WAR にはディレクトリの項目がある（`WEB-INF/classes/dsl/` などで確かめた）ため、ディレクトリの項目を足してやり直した結果を正とした。写しは消した。
- **lockfile の差（`backend/gradle.lockfile`）**: 追加 `jakarta.mail:jakarta.mail-api:2.1.5`・`org.eclipse.angus:angus-mail:2.0.5`・`org.springframework.boot:spring-boot-mail:4.1.1`・`org.springframework.boot:spring-boot-starter-mail:4.1.1`・`org.springframework:spring-context-support:7.0.9`・`com.github.davidmoten:subethasmtp:7.2.2`（テスト）・`com.github.davidmoten:guava-mini:0.1.7`（テスト）。構成の追加 `jsr305:3.0.2`（テストの構成を追加）・`jakarta.activation-api:2.1.4`（`compileClasspath` を追加）。既存の部品の版の引き上げは無い（依存の木で確かめた）。`settings-gradle.lockfile` は変わらない。
- **ライセンス**: Jakarta Mail API・Angus Mail は EPL 2.0・GPL2 w/ CPE・EDL 1.0（POM と jar の `META-INF/LICENSE.md`）で、EDL 1.0 を選ぶ理由を README の「ライセンス」に写した。SubEtha SMTP・guava-mini・jsr305・spring-context-support・java-mustache-processor は Apache 2.0、Mailpit は MIT。
- **サブモジュールの固定先**: `vendor/java-mustache-processor` は新しいサブモジュールで、更新の前は無し、後は `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（タグ `0.1.0`）。`vendor/make-you-chic-ui` は `edb1f943c0e66293494fa974605f34fcd7e258d7` のまま変えていない。

## 上流・計画との差

承認済みの文書は書き換えず、差をここに記録する。

| 対象 | 承認済みの形 | この生成での作り | 理由 |
|---|---|---|---|
| `MailUnexpectedException.of` がたどる連なり（計画の Step 5・6） | `getCause`・`getNextException`・`MailSendException.getMessageExceptions` をたどる | `getCause` と抑制された例外だけをたどる（Jakarta Mail の `MessagingException.getCause()` は次の例外を返すため `getNextException` は含まれる）。`getMessageExceptions` は `SendFailureClassifier`（transport）だけがたどる | `mail.domain` から `jakarta.mail`・`org.springframework.mail` を使わない境界の決まり（`MailBoundaryArchitectureTest`、計画の Step 16）と両立させるため。`MailSendException` を連なりに持つ失敗は必ず分類される（想定外にならない）ため、想定外の例外の型の名前に抜けは出ない |
| 送信の試みの結果の型 | 設計に無い | `mail.transport.SendAttempt`（結果と例外の型の名前）を足した | ログの `exceptionType`（BR6.2）を transport から入口へ渡すため |
| 想定外の失敗のログと Observation のタグ | 送信ごとにログ1件、`mail.failure.kind` は種類の小文字か `none` | 想定外の失敗では U1 はログを出さず（BR6.2「想定外の例外のログは呼び出し元の変換の境界で出す」に従う）、タグは `mail.outcome=failed`・`mail.failure.kind=unexpected` にした | 指標のタグの鍵をいつも4つにそろえるため。`unexpected` はこの生成で足した値 |
| 起動時の INFO の方式の値（P2） | 方式（NONE・STARTTLS・SMTPS） | 設定が使えないとき（NOT_CONFIGURED・INVALID）は `UNUSED` と出す | NONE（暗号化なしで送る）と「送らない」を見分けるため |
| BR1.5 のポートの既定 | ポートが無いとき STARTTLS は 587 | U1 は送信の部品の設定を書き換えない（`security-design.md` 2.1）ため、ポートを書かない STARTTLS は Jakarta Mail の既定の 25 になる。README に「STARTTLS では 587 を書く」と書いた（計画の Step 23 のとおり） | 計画・NFR 設計の形のまま。BR1.5 の文言との差として記録する |
| テンプレートの置き場の数え上げの失敗 | 設計に無い | 置き場のディレクトリが無い（数え上げが `FileNotFoundException` などで失敗する）ときは0件として続け、一覧の templateId のファイルが無ければ `MISSING` で止める | B1 の本番の置き場は存在しないため。0件の一覧で起動する決まり（`logical-components.md` 4節）を守る |
| 取り込みの (d) の手元の確かめ（Step 25） | このブランチを `git clone --recurse-submodules` で取り出す | コミットの前のため、取り出したリポジトリに作業の差分を当て、サブモジュールを GitHub から固定先で取得して確かめた | コミットは依頼者の承認の後のため |
| 結合テストの起動 | — | すべての `mail` の結合テストはアプリ全体を起動し（`MailTestApplication`）、テンプレートだけテスト用の一覧（`@Primary`）に差し替えた。時間切れはテストの設定で 1000 ミリ秒 | unit-test-instructions.md の「Spring と組み込みの H2 を起動し」のとおり。本番の一覧（空）の Bean も作られる |
| テストの TLS の信頼 | 候補はテストだけの SSL の bundle | 候補のとおり `spring.ssl.bundle.jks.mailtest.*` と `spring.mail.ssl.bundle=mailtest` で働いた（STARTTLS・SMTPS とも） | — |
| サブモジュールの変更の検査の確かめ（Step 19） | — | 検査が働くことを確かめるため、`vendor/java-mustache-processor/README.md` に1行足して検査が失敗することを確かめ、すぐ `git checkout` で戻した（サブモジュールの状態は変更なし） | 検査が空振りしないことの確かめ。中身を変える操作をしたことを記録する |
| Mailpit の SMTP の公開 | NFR11.1 は 1025 を公開しない | 基盤の設計の D1（Q1 A）のとおり `127.0.0.1:1025` に公開 | 基盤の設計の差（記録済み）のまま |
| NFR11.1 の確かめ方 | 招待のメールが受け手の画面に出ること | B1 は profile の起動と `isConfigured` が真（`state=CONFIGURED`）まで。画面での確かめは B3 | 計画の9節の決定 2 |

## 依頼者に確かめる点

1. `MailUnexpectedException.of` が `getMessageExceptions` をたどらない形（上の表の1行目）でよいか。
2. 想定外の失敗のタグ `mail.failure.kind=unexpected` と、起動時の INFO の `encryption=UNUSED` を足したこと。
3. BR1.5 の「STARTTLS のポートの既定 587」を README の案内で代えている点（U1 は部品の設定を書き換えない）。
4. Step 19 の確かめでサブモジュールのファイルに一時的に1行足して戻したこと（変更は残っていない）。
5. コミットの区切り（C1〜C7）と、統合を fast-forward で行うこと（計画の3節）。

## Build and Test に引き継ぐこと

| 項目 | 内容 |
|---|---|
| verify の時間 | この段の実測は 4分50秒（Step 1 は 5分27秒。Step 1 は変更の前の最初の実行）。Build and Test でもう一度測る |
| 取り込みの (d) | 依頼者のプッシュの後、CI（`submodules: true`）が固定先を取得して verify が通るかを確かめる |
| Dependabot の `docker-compose` | プッシュの後、GitHub の Dependabot の画面で compose のイメージが知らせの対象になったかを確かめる |
| 招待のメールを画面で見る確かめ・WAR の中の招待のテンプレートの数え上げ | B3（U3） |
| 契約 C10 の文言・`MailSendResult` の形・応答時間 | B3（U3）。`MailSendResult` は `outcome`・`failureKind` の record で作った |
| 送信の指標 | observability-setup で、実際に起動して `mastersmith.mail.send` の指標とタグの名前を確かめる |
