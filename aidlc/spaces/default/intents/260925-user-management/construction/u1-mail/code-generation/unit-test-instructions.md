# Unit Test Instructions — U1 メールの描画と送信（u1-mail）

U1 のテストの道具・実行のしかた・カバレッジの目標・差し替えの方針・テストのデータの扱いを示す。テストの量は Standard（部品ごとに 5〜8 件の単体テストと、境界の結合テスト）。手順の番号は `code-generation-plan.md` の Step を指す。パスはリポジトリのルートからの相対パス。

## 1. 道具と設定

| 用途 | 道具 | 設定の場所 |
|---|---|---|
| テストの実行 | JUnit 5（Spring Boot の BOM の版）、AssertJ | `backend/build.gradle.kts` の `tasks.test`（名前が `*Test`）と `integrationTest`（名前が `*IT`） |
| 性質ベースのテスト | jqwik 1.10.1（既存） | `backend/src/test/resources/junit-platform.properties`。失敗時の乱数の種はテストの出力に残る（`exceptionFormat = FULL`） |
| 構造の検査 | ArchUnit 1.5.0（既存） | `backend/src/test/resources/archunit.properties` |
| テスト用の SMTP の受け手 | SubEtha SMTP 7.2.2（`com.github.davidmoten:subethasmtp`、Apache 2.0、Step 2 で `testImplementation` に足す） | JVM の中で起動する。コンテナは使わない（`team.md` の Testing Posture） |
| TLS の証明書 | JDK 25 の `keytool`（`java.home` の `bin`） | テストの支え（`backend/src/test/java/cherry/mastersmith/mail/testsupport/`）が実行のときに一時のディレクトリへ作る。コミットしない |
| ログの確かめ | 既存の `backend/src/test/java/cherry/mastersmith/common/testsupport/LogEvents.java` | — |
| Observation の確かめ | Micrometer の `ObservationRegistry.create()` と、文脈を集める小さな `ObservationHandler`（テストの中に書く） | 新しいテストの依存（`micrometer-observation-test`）は足さない |
| 差し替え | Mockito（Spring Boot の BOM に含まれる、既存） | 単体テストだけで使う（5節） |
| 内部DB | 組み込みの H2（既存の `TestDatabase`） | Spring を起動する結合テストで使う。U1 は内部DB に触れない |
| カバレッジ | JaCoCo 0.8.15（既存） | `backend/build.gradle.kts` の `jacocoTestReport`・`jacocoTestCoverageVerification` |

新しいテストの設定のファイル（`vitest.config` などに当たるもの）は足さない。既存の `test`・`integrationTest` のタスクと名前の決まり（`XxxTest`・`XxxIT`）をそのまま使う。

## 2. この単位のテストの実行のしかた

リポジトリのルートで実行する。どれも U1 のパッケージ `cherry.mastersmith.mail` だけに絞る。

単体テスト（`*Test`。値の型と依頼の確かめ・方式の分類と点検・テンプレートの準備と検査・失敗の分類・入口・構造の検査）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.mail.*'
```

結合テスト（`*IT`。Spring と組み込みの H2 を起動し、JVM の中の SubEtha SMTP で実際に受ける）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.mail.*'
```

構造の検査だけ（U1 の境界と、変えていない全体の決まり）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.mail.MailBoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest'
```

1つの結合テストのクラスだけ（例: 送信の失敗）:

```bash
./gradlew :backend:integrationTest --tests 'cherry.mastersmith.mail.*MailSendFailureIT'
```

最初のテストより前の確かめ（Step 4）:

```bash
./gradlew :backend:test --tests 'cherry.mastersmith.ArchitectureTest'
```

- Step 4 の時点では `mail` のテストが無いため、`--tests 'cherry.mastersmith.mail.*'` は Gradle の「一致するテストが無い」で失敗する。これは想定どおりで、最初のテスト（Step 6）からコマンドが通る。Step 4 では、composite build と依存を足した状態で既存の構造の検査が動くこと（テストの道具が動くこと）を上のコマンドで確かめる。
- テストの件数を報告するときは、UP-TO-DATE で飛ばされないよう `:backend:cleanTest` または `:backend:cleanIntegrationTest` を先に付けて実行し、実測の数字だけを報告する（`project.md` の Testing Posture）。例: `./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.mail.*'`
- U1 のテストはコンテナを使わないため、コンテナの実行環境（colima）が無くても動き、飛ばされない。統合の前の `./gradlew verify`（Step 26）は対象DB のテストを含むため、colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して実行する:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

## 3. テストの一覧（Standard の量）

| 部品 | 単体（`*Test`） | 結合（`*IT`） |
|---|---|---|
| `mail.domain` | `MailRequestTest`・`MailSendResultTest`・`MailUnexpectedExceptionTest`（合わせて 6〜8 件）、`MailRequestValidationTest`（8 件＋jqwik 3 件） | — |
| `mail.config` | `EncryptionModeTest`（6〜7 件）、`MailSettingsTest`（8 件。点検の表の9行をパラメーターで） | `MailConfigurationIT`（6〜8 件） |
| `mail.template` | `MailTemplateRegistryTest`（7〜8 件）、`RenderedMailInspectorTest`（6〜8 件、jqwik を含む）、`RenderedMailTest`、`MailTemplateLintTest`（6〜8 件） | — |
| `mail.transport` | `SendFailureClassifierTest`（6〜8 件）、`SmtpMailTransportTest`（5〜6 件） | `MailSendIT`（3〜4 件）、`MailSendFailureIT`（5〜6 件）、`MailTlsIT`（3 件）、`MailHeaderInjectionIT`（3 件） |
| `mail.service` | `SmtpMailSenderTest`（6〜8 件、Observation を含む） | `MailSecretLeakIT`（5〜6 件） |
| 境界 | `MailBoundaryArchitectureTest`（5 件） | — |

どのテストも、成功の場合と、少なくとも2つの失敗・境目の場合を含める（`phases/construction.md` の Testing Standards）。テストの説明文（`@DisplayName`・メソッド名）は英語で書く。

`team.md` のメールの必須のテストのうち U1 が受け持つもの:

| 必須のテスト | 確かめるテスト |
|---|---|
| 本文のエスケープ（`<`・`>`・`&`・`"`・`'`、エスケープしない差し込みが無い、属性は二重引用符） | `MailTemplateLintTest` |
| テンプレートの描画（日英、件名が空でない、`{{` の残りが無い、ライセンスヘッダーが出ない） | `MailTemplateLintTest`・`MailTemplateRegistryTest`・`MailSendIT` |
| ヘッダーへの差し込み（改行でヘッダーが増えず拒否） | `MailRequestValidationTest`・`MailHeaderInjectionIT` |
| 送信の失敗（拒む・応答しない）で宛先・SMTP の応答・資格情報が結果とログに出ない | `MailSendFailureIT`・`MailSecretLeakIT` |
| 招待の URL の組み立て・トークンの漏えいの API の側 | U3（B3）が受け持つ |

## 4. カバレッジの目標

- 全体: 行 80% 以上・分岐 70% 以上（既存の `jacocoTestCoverageVerification`）。
- パッケージごと: 新しいパッケージ `cherry.mastersmith.mail.service`・`mail.domain`・`mail.config`・`mail.template`・`mail.transport` のそれぞれで行 80%・分岐 70% 以上。どれも `packagesJudgedByTotal` の一覧に無いため自動で対象になる。一覧は変えない（`team.md` の Testing Posture）。
- 計測から外すのは既存の除外（起動クラスと `*Properties`）だけで、U1 のために除外を足さない。`MastersmithMailProperties` は既存の除外 `**/*Properties.class` に当たる（設定値だけの型のため）。java-mustache-processor は `vendor/` 配下の別のビルドで、アプリのクラスの計測に入らない。
- 単位だけのカバレッジは、単位のテストを実行した後に報告を作って見る:

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.mail.*' :backend:integrationTest --tests 'cherry.mastersmith.mail.*' :backend:jacocoTestReport
```

  報告は `backend/build/reports/jacoco/test/html/` の `cherry.mastersmith.mail.*` のページ。下限の判定は `./gradlew verify` の段 7 で行い、値は Step 26 で実測して記録する。

## 5. 差し替え（モック・スタブ）の方針

- **SMTP の送信はモックで済ませない**: 送信の成功・失敗・TLS・ヘッダーへの差し込み・漏えいは、JVM の中で起動する SubEtha SMTP で実際に受けて確かめる（`team.md` の Testing Posture、NFR9.1）。実在の宛先・外部の SMTP へは送らない。
- **受け手の側で作る失敗**: 接続の拒否は閉じた番号（開いてすぐ閉じた番号）、応答しない受け手は受け付けて何も返さない `ServerSocket`、STARTTLS を受け付けない・必須にする・SMTPS・認証の拒否・宛先の拒否は SubEtha SMTP の設定で作る。受け手が受けた接続の数を数え、1回だけであることを確かめる。
- **時間切れ**: 結合テストの設定（`@SpringBootTest` の `properties` など）で `spring.mail.properties.mail.smtp.*timeout` を短い値にし、`Thread.sleep` や実時刻に頼らない。本番の既定値（3000 ミリ秒）は `MailConfigurationIT` で送信の部品の設定を読んで確かめる。
- **単体テストでの差し替え**: 入口（`SmtpMailSender`）の流れの単体テストに限り、`SmtpMailTransport` を Mockito で差し替えて、呼ばれる・呼ばれないと結果の受け渡しを確かめる。失敗の分類（`SendFailureClassifier`）は、Spring と Jakarta Mail の例外を組み立てて確かめる（Angus Mail の型は使わない）。
- **設定の点検**: `MailSettings.inspect` は純粋な関数として、組み立てた `JavaMailSenderImpl`（送らない）と設定の型で確かめる。環境変数の結び付きは、名前 `systemEnvironment` の `SystemEnvironmentPropertySource` を置いた `ApplicationContextRunner` で確かめる（プロセスの環境変数は変えない）。
- **テンプレート**: Spring を起動する結合テストでは、テストの設定でテスト用の一覧と置き場の `MailTemplateRegistry` に置き換える。本番の一覧と置き場は `MailTemplateLintTest` がそのまま数え上げる。
- **TLS の信頼**: 自己署名の証明書は、テストの設定の中だけで信頼させる（候補: テストだけの `spring.ssl.bundle` と `spring.mail.ssl.bundle`）。JVM 全体の信頼の設定（`javax.net.ssl.trustStore`）は変えない。証明書を無条件に信じる指定（`mail.smtp.ssl.trust` など）はテストでも使わない。
- **ログ**: `LogEvents` でロガーごとに捕まえ、件数・水準・キーの名前と、宛先・差し込んだ値・資格情報・SMTP の応答の文面が無いことを確かめる。起動時の INFO（設定の状態・方式・テンプレートの件数。計画の9節の決定 3 の P2）も同じく、値を含まないことを `MailConfigurationIT` で確かめる。TRACE の確かめでは `cherry.mastersmith` のロガーを TRACE にしてメソッドの追跡を有効にする（既存の `*SecretLeakIT` と同じ形）。

## 6. テストのデータ

- **宛先と差出人**: `example.com` などの予約されたドメインのアドレスだけを使う（例 `taro@example.com`）。実在の宛先を書かない。
- **資格情報**: 認証の確かめに使うユーザー名とパスワードは、テストの中で乱数から作る。コードやファイルに固定の値を書かない（Gitleaks に当たる値を置かない）。
- **TLS の証明書と鍵**: テストの JVM ごとに `keytool` で一時のディレクトリの PKCS12 に作り、終わったら消す。名前の合う証明書（`localhost`・`127.0.0.1`）と合わない証明書（別の名前）を作る。リポジトリに置かず、`.gitignore`・`.gitleaks.toml` に例外を足さない。`keytool` が見つからない・失敗したときは、原因の分かる文言でテストを失敗させる（黙って飛ばさない）。
- **テスト用のテンプレート**: `backend/src/test/resources/mail/test-templates/`（例 `sample_ja.html`・`sample_en.html`）に置き、テスト用の一覧（templateId と差し込みの名前）とともに使う。先頭のライセンスヘッダーは Mustache のコメント `{{! ... }}` で書く（ヘッダーの検査の対象）。差し込みは本文の文面・二重引用符で囲んだ属性の値・title の3か所に置く。準備の失敗を作るテンプレートは、場合ごとの置き場（例 `backend/src/test/resources/mail/test-templates-invalid/<場合>/`: en の欠け・Mustache の壊れ・名前の誤り・一覧に無い templateId）に置く。
- **差し込む値**: 日本語の文面、`<`・`>`・`&`・`"`・`'` を含む値、CR・LF を含む値、BR3.3 の境界の値（null・`""`・`" "`・`"\t"`・全角の空白だけ・`"a"`・`" a "`）を用意する。
- **内部DB**: Spring を起動する結合テストは、既存のとおりテストごとに一時ディレクトリの H2 を使う（`TestDatabase`）。U1 は内部DB を読み書きしない。
- **実行順**: 受け手はテストのクラスごと（またはテストごと）に空いている番号で起動し、受けたメールをテストの初めに空にする。前のテストのメールに頼らない。

## 7. 性質ベースのテストの種

- jqwik の失敗時の乱数の種は、テストの出力（失敗の詳細）に出る。再現するときは、その種を `@Property(seed = "...")` に一時的に書いて同じ単位のコマンド（2節）で実行し、直した後に外す。
- 対象は純粋な関数だけ: `MailRequestValidation`（改行・空白だけの値の拒否）、`MailAddressRule`（通る宛先の性質）、`RenderedMailInspector` の件名のまとめ（改行を含まない・前後に空白が無い・空白が続かない）。
