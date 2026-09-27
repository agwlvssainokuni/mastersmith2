# NFR Requirements の質問 — u1-mail（メールの描画と送信、library）

単位 U1（`aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`、種類 library）の NFR 要件のための質問です。次を読んで確かめました。

- この単位の承認済みの機能設計 `construction/u1-mail/functional-design/`（`rules.md` の BR1.1〜BR6.4・`functional-spec.md`・`entities.md`・`traceability.json`）
- 要件 `inception/requirements-analysis/requirements.md` の NFR1〜NFR11（NFR6 の SMTP の時間切れの値は、この段で決める持ち越し）、契約 `inception/contract-design/contract-summary.md` の C1・C10 と Open questions（C1 の時間切れの値、テスト用の SMTP の受け手の道具とライセンス）、`inception/domain-design/decisions.md` の ADR-009・ADR-010、`inception/delivery-planning/bolt-plan.md` の B1 の完了の条件、`inception/delivery-planning/external-dependency-map.md`
- コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（`technology-stack.md` のメールの仕組みが無いこと）
- 既存のコード: `settings.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`backend/config/spotbugs-exclude.xml`・`backend/src/main/resources/application.yaml`・`compose.yaml`・`.env.example`・`backend/src/main/java/cherry/mastersmith/common/observability/TraceAspect.java`
- java-mustache-processor（`vendor/java-mustache-processor` はまだ無いため、隣のリポジトリ `../java-mustache-processor` を読み取りだけで確かめた）
- Maven Central の版と POM（ライセンスと推移依存）、手元の受け手のイメージの版（読み取りだけ）

library の単位のため、成果物は `security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` です（性能・拡張性・信頼性・観測の文書は service・ui の単位だけ。段の定義の `produces_kinds`）。

## 設計の要点（案）

上流の決定とコード・部品の確認から導ける、この単位の NFR 要件の見通しです。質問の答えで決まる点は（→ Qn）と書きます。Q1〜Q4 の答え（Q1 A・Q2 B・Q3 X・Q4 A）を受けて書き直しました（Step 4 の分析）。Spring Boot 4.1.1 の `spring-boot-mail` の中身（`MailSenderAutoConfiguration`・`MailSenderPropertiesConfiguration`・`MailHealthContributorAutoConfiguration`・`MailSenderValidatorAutoConfiguration`・`MailProperties`）と、自動設定の条件の判定（`spring-boot-autoconfigure` の `OnPropertyCondition`）は、Maven Central のソースの JAR で確かめました。答えだけでは決まらない点は、追加の質問 F1〜F4 にしました。

### 当てる NFR と ID

1. **当てる NFR**: NFR1（招待のトークンと URL を U1 のログ・トレースの属性・結果に出さない）・NFR2（メールアドレス・SMTP の応答・部品の例外・`mail.debug`）・NFR5（U1 は内部DB に触れない。BR5.4 で構造で守る）・NFR6（SMTP の接続・読み取り・書き込みの時間切れ）・NFR8（テンプレートの ja・en）・NFR9（JVM の中のテスト用の受け手でのメールのテスト）・NFR11（手元でメールを見る受け手）を、枝番（例: NFR2.1）で `security-requirements.md` と `tech-stack-decisions.md` に書く。NFR3（列挙の防止）・NFR4（API の認可）・NFR7（画面のアクセシビリティ）・NFR10（スキーマの変更）は U1 に API・画面・表が無いため N/A とし、理由を `traceability.json` に書く。NFR6 の応答時間（95 パーセンタイル 5 秒）そのものは、API を持つ U3 の NFR 要件で扱い、U1 はその中に収める時間切れの値を持つ（契約 C1 の「値は U1 の NFR 要件」）。

### セキュリティ（`security-requirements.md`）

2. **Spring Boot のメールの自動設定を使う（Q3 X）**: `spring-boot-starter-mail` を依存に足し、送信の部品（`JavaMailSenderImpl`）は Spring Boot の自動設定に作らせる。コードで確かめた動き:
   - `MailSenderAutoConfiguration` は、`spring.mail.host`（または `spring.mail.jndi-name`）の設定があるときだけ有効になり、`MailProperties`（接続先・ポート（`Integer`）・ユーザー名・パスワード・`protocol`（既定 `smtp`）・`default-encoding`（既定 UTF-8）・任意の部品の設定の `properties`・`ssl.enabled`・`ssl.verify-hostname`（既定 true）・`ssl.bundle`）から `JavaMailSenderImpl` を1つ作る。設定が無いときは `MailProperties` の結び付けも行われず、送信の部品は作られない。
   - 設定の有無の判定（`OnPropertyCondition`）は、値が `false` でなければ「ある」とする。`spring.mail.host` が空の文字列でも送信の部品が作られる。そのため `application.yaml` に `spring.mail.host: ${...:}` のような空の既定を置かず、接続先は環境変数 `SPRING_MAIL_HOST` から直接受ける。
   - **「送らない」の満たし方**: U1 は送信の部品を「あれば使う」形（`ObjectProvider`）で受ける。起動のときに、送信の部品が無い、接続先が空白だけ、差出人が無い、または BR1.3・BR1.4 に当たるときは「設定がない」とし、送信は SMTP に接続せずに NOT_CONFIGURED を返す（BR1.7 はそのまま）。
3. **ヘルスチェックに SMTP を入れない（Q3 X）**: `MailHealthContributorAutoConfiguration` は、送信の部品があり、メールのヘルスチェックが無効にされていないときに、SMTP に接続して確かめるヘルスチェックを足す。`application.yaml` に `management.health.mail.enabled: false` を置き、SMTP の状態で `/actuator/health` を DOWN にしない（SMTP が無くてもアプリは動く、FR1.8）。起動のときに SMTP へ接続を試す `spring.mail.test-connection`（既定 false。true だと接続できないときに起動を止める）は設定しない。
4. **暗号化の確かめ**: STARTTLS と SMTPS では、JVM の既定の信頼できる証明書の一覧で受け手の証明書を確かめ、接続先の名前の一致も確かめ、TLS は 1.2 以上とする。Spring Boot の `spring.mail.ssl.verify-hostname` は `ssl.enabled` か `ssl.bundle` を使うときだけ効き、STARTTLS には効かない。そのため `application.yaml` の `spring.mail.properties` に `mail.smtp.ssl.checkserveridentity: true`・`mail.smtp.ssl.protocols: TLSv1.3 TLSv1.2`（`mail.smtps.*` も同じ）を置く。暗号化の方式は Spring Boot の項目だけで指定する（F1 B）。
   - **NONE**: 何も指定しない（`spring.mail.protocol` は既定の `smtp`、STARTTLS・SSL の指定なし）。既定の方式（BR1.5 の既定 NONE は保つ）。
   - **STARTTLS（必須）**: `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` と `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true` の2つを書く（`required` が true だと、受け手が STARTTLS を受け付けないときに部品が暗号化なしで続けずに失敗する）。ポートは `SPRING_MAIL_PORT=587` を書く。省くと部品の既定の 25 になる（F1 B で 587 の既定は無くなった）。
   - **SMTPS**: `SPRING_MAIL_PROTOCOL=smtps`、または `SPRING_MAIL_SSL_ENABLED=true`（部品に `mail.smtp.ssl.enable=true` が入る）のどちらか。ポートを省くと、どちらも部品の既定の 465 になる。
   - **U1 の分類**: 起動のときに送信の部品の `protocol` と `getJavaMailProperties` を読み、`protocol` が `smtps`、または使う方式の `ssl.enable` が true なら SMTPS（STARTTLS の指定があっても SMTPS とする）、そうでなく `starttls.enable` と `starttls.required` がどちらも true なら STARTTLS、そのほかは NONE とする。`protocol` が `smtp`・`smtps` のどちらでもなければ、項目の名前だけの WARN で「設定がない」とする。`starttls.enable` が true で `required` が true でない組の扱いは（→ F5）。
   - テストの自己署名の証明書は、テストの設定の中だけで信頼させる（本番の設定では `ssl.bundle` を使わない）。`spring.mail.properties` から証明書の確かめを弱める値を入れられる口には、U1 の守りを足さず、運用で設定しないことを README に書く（F3 A、要点 8）。
5. **資格情報と設定の名前**: 資格情報は環境変数だけから受け取り（BR1.1、`project.md` の Mandated）、要点 4 の分類で NONE になる設定との組は「設定がない」とする（BR1.4。F1 B の答えどおり、U1 が部品の設定を読んで点検する）。設定の名前は、接続先・ポート・資格情報・暗号化の方式が Spring Boot の `spring.mail.*`（環境変数は `SPRING_MAIL_HOST`・`SPRING_MAIL_PORT`・`SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD`・`SPRING_MAIL_PROTOCOL`・`SPRING_MAIL_SSL_ENABLED`・`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_*`）、差出人と表示名は `MailProperties` に項目が無いため U1 の `mastersmith.mail.from`・`mastersmith.mail.from-name`（`MASTERSMITH_MAIL_FROM`・`MASTERSMITH_MAIL_FROM_NAME`）とする。ポートは、数でない値なら Spring Boot の結び付けの失敗で起動が止まり（F2 A）、範囲の外の数（1〜65535 の外）は U1 が点検して項目の名前だけの WARN で「設定がない」とする。`.env.example` には、空の値の `SPRING_MAIL_HOST=` の行を写すだけで送信の部品が作られてしまうため、SMTP の行はコメントの形で置く（空白だけの接続先でも U1 は「設定がない」とする）。`MailProperties` は `toString` を持たず（値を出さない）、U1 の設定の型は資格情報を持たない。
6. **引数のログへの漏れ**: 既存のメソッドの追跡（`TraceAspect`）は `service` などのパッケージのメソッドの引数を TRACE で出す。`MailSender` は `cherry.mastersmith.mail.service` に置くため、`MailRequest` の `toString` は宛先と差し込む値（招待の URL とトークン）を伏せ、templateId と language だけを出す（既存の `LoginRequest`・`TokenResponse` と同じ形）。
7. **メールの部品自身のログ**: メールの部品のロガー（`org.eclipse.angus`・`jakarta.mail`）を `application.yaml` の `logging.level` で OFF に固定する（対象DB の3つのドライバーのログを OFF にした既存の形と同じ）。部品は詳しいログに宛先や SMTP の応答を出しうるため（NFR2）。環境変数（`LOGGING_LEVEL_...`）で上書きできる点は既存のドライバーと同じで、運用で変えない。
8. **`mail.debug` の読み方（Q3 X、`project.md` の Forbidden）**: `spring.mail.properties.mail.debug` は環境変数（`SPRING_MAIL_PROPERTIES_MAIL_DEBUG`）から有効にできる口が残る（依頼者が受け入れた）。Forbidden の「有効にしない」は、(a) 既定で無効（`application.yaml` に `mail.debug: false` を明示して置く）、(b) 運用で有効にしない（README の SMTP の設定の説明に書く）、(c) 既定の設定で作った送信の部品のメールのセッションが debug でないことをテストで確かめる、の3つで満たす。口を使われたときに送らない U1 の守りは足さない（F3 A）。承認済みの BR1.8 の「有効にする口を作らない」との差は 18 に書く。
   - **README に書く運用の決まり（F3 A）**: 次の値を環境変数・`.env` で設定しない。`spring.mail.properties` の `mail.debug`（true にしない）、`mail.smtp.ssl.trust`・`mail.smtps.ssl.trust`（証明書を無条件に信じる指定）、`mail.smtp.ssl.checkserveridentity`・`mail.smtps.ssl.checkserveridentity`（false にしない）、`mail.smtp.ssl.protocols`・`mail.smtps.ssl.protocols`（1.2 より古い TLS を足さない）、`mail.smtp.starttls.required`（STARTTLS のときに false にしない）、`spring.mail.test-connection`（true にしない）、`management.health.mail.enabled`（true にしない）、メールの部品のロガーの水準（`LOGGING_LEVEL_ORG_ECLIPSE_ANGUS` など。OFF のまま）。あわせて、方式ごとの書き方（要点 4）と、STARTTLS ではポート 587 を書くことを載せる。
9. **静的解析の関門**: SpotBugs の関門（`spotbugsGate`）に `PREDICTABLE_RANDOM` と `SMTP_HEADER_INJECTION` を priority にかかわらず止めるものとして足し、既存の `PREDICTABLE_RANDOM` の1件（`auth/repository/LoginAttemptStateRepository`）が誤検知かを確かめる（`team.md`、B1 の完了の条件）。件名は描いたテンプレートの title から作るため `SMTP_HEADER_INJECTION` で指摘されうる。改行は BR3.2（差し込む値）と BR4.2（件名の空白のまとめ）で入らないことをテストで確かめたうえで、誤検知なら理由を書いて除外の設定に入れる。
10. **ログ・結果・トレースの属性**: BR6.1〜BR6.3 のとおり、templateId・language・失敗の種類・例外の型の名前だけにする。確かめは既存の `*SecretLeakIT` と同じ形で、宛先・差し込んだ値・SMTP の応答・資格情報がアプリのログに出ないことを、送信の成功・失敗（拒む・応答しない・拒否の応答）それぞれで確かめる。

### 技術の選定（`tech-stack-decisions.md`）

11. **SMTP の送信の部品（Q3 X）**: `spring-boot-starter-mail`（中身は `spring-boot-mail` 4.1.1・`spring-context-support`・`jakarta.mail-api` 2.1.5・`angus-mail` 2.0.5（実行時））を使い、版は Spring Boot 4.1.1 の BOM に任せる。Jakarta Mail と Angus Mail のライセンスはどちらも EPL 2.0・GPL2 w/ CPE・EDL 1.0 の選べる形で、Apache License 2.0 と異なるため採用の理由を記録する（`team.md`）。`jakarta.activation-api`（2.1.4）と `angus-activation`（2.0.3）はすでに lockfile にある。
12. **時間切れ（Q1 A・Q2 B・Q3 X）**: 接続・読み取り・書き込みの3つをどれも 3 秒とする（BR5.2）。部品の設定の名前は `mail.smtp.connectiontimeout`・`mail.smtp.timeout`・`mail.smtp.writetimeout`（SMTPS は `mail.smtps.*`）で、値はミリ秒の整数のため、`application.yaml` の `spring.mail.properties` に6つとも `3000` を置く。環境変数（例: `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT`）で上書きできる（環境変数から `spring.mail.properties` の点を含む鍵へ結び付くことは B1 で確かめる）。**不正な値のときに送らない確かめ**: 部品は読めない値を時間切れなし（無制限）として扱うおそれがあるため、U1 が起動のときに送信の部品の設定（`getJavaMailProperties`）から、使う `protocol` の3つの値（`smtps` なら `mail.smtps.*`、`smtp`（`ssl.enable` の SMTPS を含む）なら `mail.smtp.*`）を読み、1 以上の整数でなければ、項目の名前だけの WARN を1件出して「設定がない」とする（BR1.3 への追加、18 に書く）。送信の全体の上限は作らない（全体を打ち切るには別のスレッドが要り、ADR-009 の「同じ要求の中で送る」と合わない）。そのため、1つの応答ごとには時間切れで打ち切るが、応答が時間切れの手前で遅れ続ける受け手では全体が時間切れの値の数倍になりうる。接続先の名前の解決（DNS）の待ちも接続の時間切れの外にある。この2点は既知の限界として記録する。
13. **テンプレートの描画の性能**: 起動のときにすべてのテンプレートを準備して持ち、動いている間は差し替えない（BR2.3、機能設計の2節）。描画は準備済みのテンプレートに差し込むだけで、送信の時間に比べて小さいため、ほかのキャッシュは置かない。準備したテンプレートは読むだけなので同時に呼ばれてもよい（機能設計の4節）。
14. **java-mustache-processor の取り込み**: サブモジュール `vendor/java-mustache-processor` をタグ `0.1.0`（コミット `8d44c36`、今の既定のブランチの先頭と同じ）に固定し、ルートの `settings.gradle.kts` の `includeBuild` で `cherry.mustache:cherry-mustache-core` を組む。確かめた事実: core の実行時の依存は `slf4j-api` だけで、アプリの `slf4j-api`（2.0.18、lockfile にある）に合わせて解決される。部品のビルドは Java 25（アプリと同じ）で、部品側の Gradle は 9.6.1（アプリは 9.7.1）。部品のビルドの設定は Gradle Plugin Portal のプラグイン（`org.owasp.dependencycheck` 10.0.4・`com.gradleup.shadow` 9.6.0）を使う。これはビルドのときだけの道具で WAR に入らず、アプリ自身のプラグイン（Spring Boot・Spotless・SpotBugs）も今すでに Plugin Portal から取っている（ルートの設定に `pluginManagement` が無い）ため、「依存の取得元は Maven Central だけ」を `dependencyResolutionManagement` の決まり（依存の解決）と読めば崩れない。依頼者はこの読み方を確かめた（F4 A: 決まりは依存の解決だけに当て、ビルドのプラグインは java-mustache-processor のものも含めて Gradle Plugin Portal から取ってよい）。この読み方を `tech-stack-decisions.md` に記録する。
15. **B1 で確かめる条件（ADR-010）**: (a) 取り込んだ状態で `./gradlew verify` が通り、`settings.gradle.kts` の依存の取得元が Maven Central だけのまま、(b) 部品の推移依存（`slf4j-api`）が `backend/gradle.lockfile` に載り、OSV-Scanner の検査の対象に入る、(c) WAR に core の JAR が入る、(d) CI がサブモジュールを固定先で取得してビルドできる、(e) 部品側の Gradle のプラグインがアプリの Gradle 9.7.1 で動く。1つでも満たせなければ、ADR-010 のとおり Maven Central への公開に切り替える判断をこの Bolt で記録する（部品のリポジトリの変更は部品のリポジトリ側で行う、`project.md` の Forbidden）。
16. **手元でメールを見る受け手**: Mailpit のイメージ `axllent/mailpit:v1.31.2`（ダイジェスト `sha256:74d609a42ec279aa63c6b4622a6fa9b5408d1ad5b1d76a1c4be40a265ce0863d` で固定、MIT ライセンスで、コードの依存ではないが記録する）を、compose の profile `mail` で見たいときだけ起動する（NFR11、`team.md` の Deployment）。アプリからは暗号化 NONE・資格情報なしで compose のネットワークの中の SMTP（`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`）に送り、画面（8025）は `127.0.0.1` だけに公開する。既存のイメージと同じく版とダイジェストで固定する。

### テスト

17. **テストの形（Q4 A）**: 送信のテストは JVM の中で起動する SubEtha SMTP 7.2.2（`testImplementation`、lockfile と OSV-Scanner の対象）で実際に受け、宛先・件名・本文（HTML）・言語を確かめる（`team.md`）。STARTTLS（受け付ける・受け付けない）・SMTPS・認証の拒否（REJECTED）を受け手の側で作る。受け手が接続を拒むは閉じたポート、応答しないは受け付けて何も返さない `ServerSocket`（既存の対象DB の TIMEOUT の確かめと同じ形）で作り、テストの設定では時間切れを短い値にする（Q2 B で設定から変えられるため、テストだけの口は要らない）。Q3 X に伴い、次のテストを足す: 既定の設定で送信の部品のセッションが debug でない（8）、メールのヘルスチェックが `/actuator/health` に含まれない（3）、`SPRING_MAIL_HOST` が無い・空白だけのときに NOT_CONFIGURED で接続しない（2）、時間切れの値が不正なときに項目の名前だけの WARN と NOT_CONFIGURED になり値がログに出ない（12）。F1 B・F2 A に伴い、方式の分類（何も無い→NONE、STARTTLS の2つ→STARTTLS、`protocol` が `smtps`→SMTPS、`ssl.enabled`→SMTPS、両方の指定→SMTPS、知らない `protocol`→NOT_CONFIGURED）、資格情報と NONE の組が NOT_CONFIGURED になること（BR1.4）、範囲の外のポートが NOT_CONFIGURED になること、STARTTLS（必須）で受け手が STARTTLS を受け付けないときに CONNECTION_FAILED で平文で送らないこと、を確かめるテストを足す。数でないポートで起動が止まることは Spring Boot の動きのため、U1 のテストの対象にしない。

### 承認済みの機能設計との差（上流との差）

18. Q2 B・Q3 X・F1 B・F2 A・F3 A の答えにより、承認済みの U1 の機能設計（`rules.md`）と次の点が違う。設計の文書は書き換えず、この段の成果物（`security-requirements.md`・`tech-stack-decisions.md`）に差として書く（`project.md` の決まり）。
   - **BR1.1（設定の名前と読み方）**: 「環境変数だけから受け取る」は保つが、接続先・ポート・資格情報・暗号化の方式は Spring Boot の `spring.mail.*` を Spring Boot が読み、U1 は出来上がった送信の部品を受け取って読む形になる。差出人と表示名だけが U1 の `mastersmith.mail.*`。`spring.mail.properties.*` から部品の任意の設定を受ける口ができる（Q3 X で受け入れ）。
   - **BR1.2（「設定がある」の意味）**: 「接続先がある」は「送信の部品が作られ、接続先が空白だけでない」と読む。
   - **BR1.3（不正な値）**: 時間切れの値（使う方式の3つ）を点検の項目に足す（Q2 B）。「暗号化が NONE・STARTTLS・SMTPS のどれでもない」は「`protocol` が `smtp`・`smtps` のどちらでもない」に読み替える（F1 B）。ポートは、範囲の外の数は BR1.3 のとおり WARN と「設定がない」だが、数でない値は起動が止まる（F2 A。BR1.3 の「起動を続ける」と違う）。
   - **BR1.4（資格情報と暗号化なし）**: 決まりは保つが、「暗号化が NONE」は1つの項目の値ではなく、U1 が部品の設定から分類した結果（要点 4）で判定する（F1 B）。
   - **BR1.5（暗号化の方式とポートの既定）**: 方式は1つの項目ではなく Spring Boot の項目の組み合わせで指定する（NONE は指定なし、STARTTLS（必須）は `mail.smtp.starttls.enable` と `mail.smtp.starttls.required` を true、SMTPS は `protocol: smtps` か `ssl.enabled: true`）。既定は NONE のまま。ポートを省いたときの既定は部品の既定で、NONE・STARTTLS は 25、SMTPS は 465 になり、STARTTLS の 587 の既定は無くなる（STARTTLS ではポート 587 を書くことを README に載せる）。STARTTLS を受け付けない受け手で平文で続けないことは `starttls.required: true` で部品が守る。
   - **BR1.8（`mail.debug`）**: 「有効にする口を作らない」から「既定で無効・運用で有効にしない（README に書く）・既定が無効であることをテストで確かめる」に変わる（Q3 X・F3 A）。同じく、TLS を弱める値（`ssl.trust`・`checkserveridentity: false`・古い TLS・STARTTLS の `required: false`）も U1 は守らず、README の運用の決まり（要点 8）で扱う（F3 A）。
   - **ヘルスチェック**: 機能設計に無かった `management.health.mail.enabled: false` を足す（Q3 X）。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| SMTP の設定（接続先・ポート・暗号化・資格情報・差出人・表示名）は環境変数だけから受け取り、接続先と差出人がそろわなければ送らない（設定の名前と読み方は Q3 X により要点 5・18 のとおり変わる） | BR1.1〜BR1.3、`project.md` の Mandated |
| 暗号化は NONE・STARTTLS（必須）・SMTPS、既定は NONE。資格情報と NONE の組は「設定がない」 | BR1.4・BR1.5（機能設計の Q3 A） |
| `mail.debug` は既定で無効のまま運用で有効にしない。環境変数から有効にできる口が残ることは受け入れる（承認済みの BR1.8 の「口を作らない」から変わる） | `project.md` の Forbidden、Q3 X（BR1.8 との差は要点 18） |
| Spring Boot のメールの自動設定を使い、設定は `spring.mail.*`。`management.health.mail.enabled: false` で SMTP をヘルスチェックから外す | Q3 X |
| 時間切れは接続・読み取り・書き込みともに 3 秒。環境変数で変えられ、不正な値は BR1.3 に足して「設定がない」（送らない） | Q1 A・Q2 B |
| テスト用の SMTP の受け手は SubEtha SMTP 7.2.2（Apache License 2.0） | Q4 A |
| 暗号化の方式は Spring Boot の項目だけで指定し、U1 は部品の設定を読んで分類・点検する。STARTTLS のポートの既定 587 は無くなる | F1 B |
| 数でない `SPRING_MAIL_PORT` は結び付けの失敗で起動が止まるのを受け入れ、範囲の外の数は U1 が点検する（BR1.3 との差） | F2 A |
| TLS を弱める値や `mail.debug` への U1 の守りは足さず、運用と README で扱う | F3 A |
| 「依存の取得元は Maven Central だけ」は依存の解決だけに当て、ビルドのプラグイン（java-mustache-processor のものを含む）は Gradle Plugin Portal から取ってよい | F4 A |
| 送信は1回だけで自動の再試行をしない。接続と読み取り（書き込みを含む）に時間切れを持ち、超えたら TIMEOUT | BR5.2・BR5.3、契約 C1 |
| U1 は内部DB に触れず、呼び出し元がトランザクションの外で呼ぶ。要求の中で送り、結果を待って応答する | BR5.4、ADR-009 |
| 招待・送り直しの API は SMTP の送信を含めて 95 パーセンタイルで 5 秒以内。受け手が応答しないときは時間切れの後に送信の失敗として応答する | NFR6（値の持ち主は U3。U1 は時間切れの値を持つ） |
| テンプレートは起動のときに準備し、欠け・壊れで起動を止める。動いている間は差し替えない | BR2.3 |
| 結果・ログ・トレースの属性に宛先・SMTP の応答・例外のメッセージ・資格情報・差し込んだ値を入れない。監査ログに書かない | BR6.1〜BR6.4、NFR1・NFR2 |
| メールのテストは JVM の中のテスト用の SMTP の受け手で実際に受ける。受け手にコンテナを使わない。送信の失敗（拒む・応答しない）のテストを入れる。道具はライセンスの確認とあわせて設計の段で決める | `team.md` の Testing Posture、NFR9 |
| SpotBugs の関門に `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を加え、既存の1件を確かめる | `team.md` の Code Style、B1 の完了の条件 |
| java-mustache-processor はサブモジュールと composite build で取り込み、中身をこのリポジトリから変えない。推移依存を lockfile と OSV-Scanner の対象に含め、満たせなければ Maven Central への公開に切り替える | `team.md` の Code Style、`project.md` の Forbidden、ADR-010 |
| 新しい依存は採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは理由を記録する。推移依存で既存の版を引き上げないかを依存の木で確かめる | `team.md` の Code Style、`project.md` の学び |
| 手元でメールを見る受け手は compose の profile で見たいときだけ起動する。実在の宛先・外部の SMTP へは送らない | NFR11、`team.md` の Deployment、`project.md` の Forbidden |

---

## Q1. SMTP の接続・読み取り・書き込みの時間切れを、それぞれ何秒にしますか？

理由: NFR6 と契約 C1 で「値は U1 の NFR 要件で決める」とされています。ADR-009 で送信は要求の中で行うため、時間切れの値が、受け手が応答しないときの招待・送り直しの API の応答時間をほぼ決めます。今の送り先は手元の受け手だけで、配備先の SMTP は決まっていません。部品（Angus Mail）の既定はどれも「無制限」のため、値を決めないと応答しない受け手で要求が止まり続けます。

A. 接続 3 秒・読み取り 3 秒・書き込み 3 秒（接続した後に何も返さない受け手でも、約 3 秒で失敗を返し、NFR6 の 5 秒の中に収まる。遠い SMTP の一時的な遅れで TIMEOUT になりやすく、配備先が決まったときに見直す）
B. 接続 5 秒・読み取り 10 秒・書き込み 10 秒（遠い SMTP の遅れに余裕がある。何も返さない受け手では約 10 秒で失敗を返し、NFR6 の 5 秒を超える（失敗の場合として許す））
C. 接続 10 秒・読み取り 30 秒・書き込み 30 秒（外部の SMTP でよく使われる長めの値。何も返さない受け手では約 30 秒、要求を処理するスレッドを長く持つ）
X. Other (please specify)

[Answer]: A

## Q2. 時間切れの値を、環境変数で変えられるようにしますか？

理由: 既存の設定は `application.yaml` で `${MASTERSMITH_...:既定}` の形にして環境変数で変えられるものが多い一方、承認済みの機能設計の SMTP の設定の項目（BR1.1）と、不正な値の扱い（BR1.3）には時間切れが入っていません。変えられるようにすると、不正な値（0・負・数でない）の扱いを決め、BR1.3 の項目に足す必要があります（承認済みの設計との差として記録します）。

A. 変えられない（Q1 の値をコードの定数で持つ。配備先が決まって値を見直すときにコードを変える。承認済みの BR1.1・BR1.3 はそのまま）
B. 環境変数で変えられる（既定は Q1 の値。不正な値は BR1.3 の項目に足し、項目の名前だけの WARN を出して「設定がない」とする）
C. 環境変数で変えられる（既定は Q1 の値。不正な値は起動を止める）
X. Other (please specify)

[Answer]: B

## Q3. SMTP の送信の部品を、どの形で組み込みますか？

理由: どの形でも中身は Jakarta Mail と Angus Mail（版は Spring Boot の BOM）ですが、Spring Boot のメールの自動設定を使うかで、設定の口とヘルスチェックの扱いが変わります（設計の要点 2・3）。自動設定は `spring.mail.*` と `spring.mail.properties.*`（任意の部品の設定）を環境変数から受け、送信の部品があるとメールのヘルスチェックが SMTP に接続します。

A. Spring の送信の抽象（`spring-context-support` の `JavaMailSenderImpl`）と `jakarta.mail-api`・`angus-mail` を直接の依存にし、U1 が自分の設定の型から送信の部品を組み立てる。Spring Boot のメールの自動設定は依存に入らない（`spring.mail.*` の口とメールのヘルスチェックが最初から無い）
B. `spring-boot-starter-mail` を使い、メールの自動設定を除外し、メールのヘルスチェックを無効にする設定を置く（依存は標準の形だが、除外と無効の設定が漏れると口ができる。それをテストで確かめる）
C. Spring の抽象を使わず、Jakarta Mail の API（`Session`・`Transport`）を U1 が直接使う（依存は最も少ないが、送信と例外の扱いを自前で書く量が増える）
X. Other (please specify)

[Answer]: X. Other (please specify): spring-boot-starter-mail と Spring Boot のメールの自動設定を使う（設定は spring.mail.*）。management.health.mail.enabled: false で SMTP をヘルスチェックから外す。mail.debug は既定で無効のまま運用で有効にしない（環境変数から有効にできる口が残ることは受け入れる）

## Q4. JVM の中で起動するテスト用の SMTP の受け手の部品は、どれにしますか？

理由: `team.md` で「受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める」とされ、契約 C1 の Open questions でもこの段の論点です。Maven Central の POM で版・ライセンス・推移依存を確かめました。BR1.5 の STARTTLS（必須）と SMTPS の成功と失敗、BR5.3 の認証の拒否（REJECTED）を確かめられるかが違います。

A. SubEtha SMTP（`com.github.davidmoten:subethasmtp` 7.2.2、2026-02 公開、Apache License 2.0）。受けたメールを持つ仕組み（Wiser）があり、STARTTLS（受け付ける・必須にする）と SMTPS と認証を受け手の側で作れる。推移依存は `slf4j-api`（アプリの版に合わせて解決）・`jsr305`・`guava-mini`（どちらも Apache License 2.0）で、Jakarta Mail の API はアプリのものを使い、Angus Mail は Spring Boot と同じ 2.0.5
B. GreenMail（`com.icegreen:greenmail-junit5` 2.1.14、2026-09 公開、Apache License 2.0）。JUnit 5 の拡張があり、SMTPS と認証を作れるが、STARTTLS の受け付けの仕組みが無く、STARTTLS の成功の場合を確かめられない。推移依存に JUnit 4（`junit:junit` 4.13.2、EPL 1.0、理由の記録が要る）が入り、Angus Mail をまとめた JAR（`org.eclipse.angus:jakarta.mail`）がアプリの `angus-mail` と同じクラスを重ねて持つおそれがあり、テストのクラスパスで除外の設定が要る見込み
X. Other (please specify)

[Answer]: A

---

## 追加の質問（答えの分析で残った点）

Q1〜Q4 の答えを、承認済みの機能設計（BR1.1〜BR1.8）と Spring Boot 4.1.1 の自動設定のコードに照らして確かめたところ、答えだけでは決まらない点が次の4つ残りました（F1〜F4）。F1〜F4 の答えを確かめたところ、F1 B と F3 A が1つの組でぶつかるため、F5 を足しました。

## F1. 暗号化の方式（NONE・STARTTLS・SMTPS）を、どの設定で指定しますか？

理由: 承認済みの BR1.5 は「暗号化」を1つの項目（NONE・STARTTLS（必須）・SMTPS、既定 NONE、ポートを省いたら 25・587・465）とし、BR1.4 は「資格情報と NONE の組は送らない」としています。Spring Boot の `MailProperties` にはこの1つの項目が無く、SMTPS は `spring.mail.protocol: smtps` か `spring.mail.ssl.enabled: true`、STARTTLS は `spring.mail.properties` の `mail.smtp.starttls.enable` と `mail.smtp.starttls.required` の2つで表します。ポートを省いたときは部品の既定（smtp は 25、smtps は 465）になり、STARTTLS の 587 は自動では選ばれません。

A. U1 の項目 `mastersmith.mail.encryption`（環境変数 `MASTERSMITH_MAIL_ENCRYPTION`、NONE・STARTTLS・SMTPS、既定 NONE）を残す。U1 が起動のときに、Spring Boot が作った送信の部品に方式どおりの設定（SMTPS は `smtps`、STARTTLS は `starttls.enable` と `starttls.required` を true、ポートが無ければ 587）を書き足す。BR1.4・BR1.5 はそのまま守れる（差出人と同じく、`spring.mail.*` の外に U1 の項目が1つ増える）
B. Spring Boot の項目だけで指定する（SMTPS は `SPRING_MAIL_PROTOCOL=smtps`、STARTTLS は `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` と `..._REQUIRED`）。U1 は起動のときに送信の部品の設定を読んで NONE・STARTTLS・SMTPS に分け、STARTTLS なのに必須になっていない、資格情報と NONE の組、などは項目の名前だけの WARN で「設定がない」とする。STARTTLS のポートの既定 587 は無くなり、`SPRING_MAIL_PORT` を必ず書く（BR1.5 との差）
X. Other (please specify)

[Answer]: B

## F2. `SPRING_MAIL_PORT` に数でない値（例: `abc`）が入ったとき、どう扱いますか？

理由: 承認済みの BR1.3 は「ポートが 1〜65535 の数でないときは、項目の名前だけの WARN を出し、起動を続けて送らない」としています。Spring Boot の `MailProperties` はポートを `Integer` で受けるため、接続先があるときに数でない値だと、設定の結び付けの失敗でアプリの起動が止まります（前の Intent の対象DB でも同じ理由でポートを文字列で受けた、`project.md` の学び）。範囲の外の数（例: 70000）は結び付けを通るため、U1 が点検して BR1.3 のとおり扱えます。

A. 数でない値で起動が止まることを受け入れる（設定の誤りに起動の時点で気づける。範囲の外の数だけを U1 が点検する。BR1.3 との差として記録する）
B. ポートだけは `spring.mail.port` ではなく U1 の項目（`MASTERSMITH_MAIL_PORT`、文字列で受ける）にし、U1 が点検して送信の部品に入れる（BR1.3 のとおり起動を続けるが、「設定は `spring.mail.*`」から1項目外れる）
X. Other (please specify)

[Answer]: A

## F3. `spring.mail.properties` から、証明書の確かめを弱める値や `mail.debug` を入れられた場合、U1 は送らないようにしますか？

理由: Q3 X で `spring.mail.properties.*` の口が残るため、環境変数から `mail.debug=true` だけでなく、TLS を弱める値（すべての証明書を信じる `mail.smtp.ssl.trust=*`、名前の一致を確かめない `mail.smtp.ssl.checkserveridentity=false`、STARTTLS を任意にする `mail.smtp.starttls.required=false` など）も入れられます。弱められると資格情報が守られない経路で流れるおそれがあります（BR1.4 の趣旨）。Q3 の答えで `mail.debug` の口が残ることは受け入れられているため、ここで問うのは「口を使われたときに送らない守り」を足すかどうかです。

A. 足さない（`mail.debug` と同じく、運用でこれらを設定しないことを README に書き、既定の値をテストで確かめる）
B. U1 が起動のときに送信の部品の設定を読み、TLS を弱める値があれば、項目の名前だけの WARN を出して「設定がない」とする（送らない）。`mail.debug` は A のまま
C. B に加えて、`mail.debug` が true のときも同じく「設定がない」とする（口は残るが、使われたらメールを送らず、デバッグ出力が出る経路を通らない。Forbidden を運用だけでなく仕組みでも守る）
X. Other (please specify)

[Answer]: A

## F4. 「Gradle の依存の取得元は Maven Central だけ」の決まりは、ビルドのプラグインにも当てますか？

理由: `team.md` の決まりは `settings.gradle.kts` の `dependencyResolutionManagement`（`RepositoriesMode.FAIL_ON_PROJECT_REPOS`）で守られています。一方、ルートの設定に `pluginManagement` が無いため、アプリ自身のプラグイン（Spring Boot・Spotless・SpotBugs）は今すでに Gradle Plugin Portal から取っています。java-mustache-processor を composite build で組むと、部品のビルドの設定が使うプラグイン（`org.owasp.dependencycheck` 10.0.4（Maven Central に無い）・`com.gradleup.shadow` 9.6.0）も Plugin Portal から取ります。どれもビルドのときだけの道具で WAR には入りません。私はこの決まりを依存の解決だけのものと読みました（要点 14）が、依頼者の意図を確かめます。

A. 依存の解決（アプリと WAR に入る部品）だけの決まりと読む。ビルドのプラグインは今のアプリと同じく Plugin Portal から取ってよく、java-mustache-processor のプラグインも同じ扱いとする（team.md の読み方を tech-stack-decisions.md に記録する）
B. プラグインにも当てる。ルートの設定に `pluginManagement` を置いて Maven Central だけにし、既存のプラグインが Maven Central から取れるかを B1 で確かめる。java-mustache-processor の `org.owasp.dependencycheck` は Maven Central に無いため、部品のリポジトリ側でプラグインを外すか、Maven Central への公開（ADR-010 の切り替え先）に切り替える
X. Other (please specify)

[Answer]: A

## F5. `starttls.enable` が true で `starttls.required` が true でない設定（STARTTLS を試すが、できなければ平文で続ける）を、U1 はどう分類しますか？

理由: F1 で選んだ B の説明には「STARTTLS なのに必須になっていない設定は WARN で『設定がない』」とありました。一方、F3 の A は「TLS を弱める値（例に `mail.smtp.starttls.required=false` を挙げた）への守りは足さず、運用と README で扱う」です。2つの答えがこの組でぶつかるため、どちらを優先するか確かめます。どれでも、SMTPS の指定があるときは SMTPS とし、STARTTLS の2つがどちらも true なら STARTTLS（必須）とする点は変わりません。

A. NONE と同じに分類する。資格情報が無ければ送り（受け手が STARTTLS を受け付けなければ平文で届く）、資格情報があれば BR1.4 のとおり「設定がない」とする（資格情報だけは平文の経路に流さない。それ以外は F3 A のとおり運用で扱う）
B. 設定の誤りとして、資格情報の有無にかかわらず項目の名前だけの WARN を出して「設定がない」とする（F1 B の説明を優先する。STARTTLS を必須でなく使う設定は受け付けない）
C. STARTTLS として扱い、そのまま送る（F3 A を優先する。資格情報が平文で流れうることも運用で防ぐ）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の 18 件のとおり（Q3 X に合わせて、Spring Boot のメールの自動設定を使う形・ヘルスチェックから外す・`mail.debug` の読み方・時間切れを `spring.mail.properties` に置き U1 が起動時に点検する形に書き直した。F1〜F4 に合わせて、方式の書き方と U1 の分類（要点 4）・ポートの扱い（要点 5）・README の運用の決まり（要点 8）・プラグインの取得元（要点 14）・テスト（要点 17）・承認済みの機能設計との差（要点 18）を直した）
- Q1 A: 時間切れは接続・読み取り・書き込みともに 3 秒（`spring.mail.properties` の `mail.smtp.*` と `mail.smtps.*` に 3000 ミリ秒）。送信の全体の上限は作らず、遅れ続ける受け手と DNS の待ちは既知の限界として記録する
- Q2 B: 時間切れは環境変数で変えられる。不正な値（1 以上の整数でない）は BR1.3 に足し、項目の名前だけの WARN を出して「設定がない」（送らない）とする
- Q3 X: `spring-boot-starter-mail` と Spring Boot のメールの自動設定を使い、設定は `spring.mail.*`（差出人と表示名は U1 の `mastersmith.mail.*`）。`management.health.mail.enabled: false` で SMTP をヘルスチェックから外す。`mail.debug` は既定で無効のまま運用で有効にせず、既定が無効であることをテストで確かめる。環境変数から有効にできる口が残ることは受け入れる
- Q4 A: テスト用の SMTP の受け手は SubEtha SMTP 7.2.2（Apache License 2.0、`testImplementation`）
- F1 B: 暗号化の方式は Spring Boot の項目だけで指定する（NONE は指定なし、STARTTLS（必須）は `mail.smtp.starttls.enable` と `mail.smtp.starttls.required` を true、SMTPS は `protocol: smtps` か `ssl.enabled: true`）。U1 は起動のときに部品の設定を読んで分類し、BR1.4（資格情報と NONE）と知らない `protocol` を点検する。STARTTLS のポートの既定 587 は無くなり、ポートを省くと NONE・STARTTLS は 25、SMTPS は 465
- F2 A: 数でない `SPRING_MAIL_PORT` は結び付けの失敗で起動が止まるのを受け入れる。範囲の外の数は U1 が点検し、項目の名前だけの WARN で「設定がない」とする（BR1.3 との差として記録）
- F3 A: TLS を弱める値や `mail.debug` への U1 の守りは足さない。運用で設定しない値の一覧を README に書く（要点 8）
- F4 A: 「依存の取得元は Maven Central だけ」は依存の解決だけに当てる。ビルドのプラグインは、java-mustache-processor のものも含めて Gradle Plugin Portal から取ってよい
- F5 A: `starttls.enable` が true で `starttls.required` が true でない組は NONE と同じに分類し、資格情報があるときだけ BR1.4 で送らない（資格情報を暗号化なしで流さない）。それ以外は F3 A のとおり運用で扱う

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
