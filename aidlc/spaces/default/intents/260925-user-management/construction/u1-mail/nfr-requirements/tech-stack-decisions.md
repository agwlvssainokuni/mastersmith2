# Tech Stack Decisions — U1 メールの描画と送信（u1-mail）

U1 で足す技術と、その版・ライセンス・推移依存・選定の理由を示す。あわせて、library の単位のため性能・信頼性・観測の文書を作らない代わりに、それに当たる要件（NFR5・NFR6）と、テンプレート（NFR8）・テスト（NFR9）・手元の確かめ（NFR11）の要件を、この文書の枝番つきの節に置く。

- セキュリティの要件（NFR1・NFR2）と承認済みの機能設計との差: `security-requirements.md`
- 既存の構成: `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`（メールの仕組みと Mustache のエンジンは今の依存に無い）
- 答え: `nfr-requirements-questions.md`（Q1 A・Q2 B・Q3 X・Q4 A・F1 B・F2 A・F3 A・F4 A・F5 A）
- 版はコード生成で `backend/gradle.lockfile` に固定する。版と POM は Maven Central で、Spring Boot のメールの自動設定の動きは `spring-boot-mail` 4.1.1 のソースで確かめた（2026-09-27）

| 要件の節 | この文書の場所 |
|---|---|
| NFR5（接続の使い方）・NFR6（時間切れと応答）: 性能・信頼性に当たる要件 | 2節 |
| NFR8（テンプレートの ja・en と描画のエンジン） | 3節 |
| NFR9（テスト） | 4節 |
| NFR11（手元の確かめ） | 5節 |

## 1. 選定

### 1.1 採用する部品

| 対象 | 選定と版 | 範囲 | ライセンス | 理由 |
|---|---|---|---|---|
| SMTP の送信 | `org.springframework.boot:spring-boot-starter-mail`（Spring Boot 4.1.1 の BOM）。中身は `spring-boot-mail` 4.1.1・`spring-context-support`（Spring Boot 4.1.1 の BOM の版）・`jakarta.mail:jakarta.mail-api` 2.1.5・`org.eclipse.angus:angus-mail` 2.0.5（実行時） | `implementation` | Spring の部品は Apache 2.0。Jakarta Mail API と Angus Mail は EPL 2.0・GPL2 w/ CPE・EDL 1.0 から選べる形 | 依頼者の決定（Q3 X）。Spring Boot のメールの自動設定（`MailSenderAutoConfiguration`）で送信の部品（`JavaMailSenderImpl`）を作り、設定は `spring.mail.*` とする。自動設定の動き（接続先があるときだけ部品を作る、ヘルスチェックの足し方）は2節・`security-requirements.md` の NFR2.5 に書いた |
| 既存の推移依存（変わらない） | `jakarta.activation:jakarta.activation-api` 2.1.4、`org.eclipse.angus:angus-activation` 2.0.3 | すでに lockfile にある | EDL 1.0 など | 版は変わらない |
| テンプレートの描画 | java-mustache-processor（`cherry.mustache:cherry-mustache-core`、タグ `0.1.0`、コミット `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`）。サブモジュール `vendor/java-mustache-processor`、ルートの `settings.gradle.kts` の `includeBuild` で組む | `implementation` | Apache 2.0 | 依頼者の自前のエンジン（要件 FR2.1、`team.md` の Code Style）。実行時の依存は `slf4j-api` だけ（部品側の宣言は 2.0.16、アプリの 2.0.18 に合わせて解決され、版を引き上げない）。タグ `0.1.0` は部品のリポジトリの既定のブランチの先頭と同じコミット。部品は Java 25（アプリと同じ）、部品側の Gradle は 9.6.1（アプリは 9.7.1） |
| テスト用の SMTP の受け手 | SubEtha SMTP（`com.github.davidmoten:subethasmtp` 7.2.2、2026-02 公開） | `testImplementation` | Apache 2.0 | 依頼者の決定（Q4 A）。受けたメールを持つ仕組み（Wiser）があり、STARTTLS（受け付ける・必須にする）・SMTPS・認証を受け手の側で作れ、BR1.5 と BR5.3 の成功と失敗を確かめられる。推移依存は `slf4j-api`（宣言は 1.7.36、アプリの 2.0.18 に合わせて解決）・`com.google.code.findbugs:jsr305` 3.0.2（Apache 2.0）・`com.github.davidmoten:guava-mini` 0.1.7（Apache 2.0）・`angus-mail` 2.0.5（実行時、Spring Boot と同じ版）。Jakarta Mail の API は `provided` で、アプリのものを使う |
| 手元でメールを見る受け手 | Mailpit のイメージ `axllent/mailpit:v1.31.2@sha256:74d609a42ec279aa63c6b4622a6fa9b5408d1ad5b1d76a1c4be40a265ce0863d`（2026-09-19 公開） | compose の profile `mail`（5節） | MIT | NFR11。メールを受けて画面で見せるだけの受け手で、送り先の外部の SMTP を持たない。コードの依存ではなく WAR にも入らないが、既存のイメージと同じく版とダイジェストで固定し、ライセンスを記録する |

どれも、推移依存で既存の部品の版を引き上げない見込み（POM の宣言の版はアプリの版以下か同じ）。コード生成で依存の木（`./gradlew :backend:dependencies`）を確かめ、lockfile の差を記録する（`project.md` の学び）。

### 1.2 Apache License 2.0 と異なるライセンスの採用の理由（`team.md` の Code Style）

- **Jakarta Mail API・Angus Mail（EPL 2.0・GPL2 w/ CPE・EDL 1.0）**: Java で SMTP を送る標準の API とその実装で、Spring Boot のメールの自動設定と `JavaMailSenderImpl` が前提とする。実用になる代わりの実装が無い。3つのライセンスから選べ、EDL 1.0（BSD-3-Clause と同じ形）は Apache License 2.0 のアプリで使える寛容なライセンスである。部品は変えずにライブラリとして使う。既存の `jakarta.activation-api`・`angus-activation`（同じ Eclipse の部品）もすでに使っている。
- **Mailpit（MIT）**: 手元の確かめだけに compose の profile で起動する別のコンテナで、アプリに組み込まない。MIT は寛容なライセンスである。

### 1.3 使わないもの

| 候補 | 使わない理由 |
|---|---|
| `spring-context-support` と Jakarta Mail を直接の依存にして U1 が送信の部品を組み立てる形（質問の Q3 A） | 依頼者は Spring Boot の自動設定を使う形を選んだ（Q3 X）。`spring.mail.properties.*` の口が残ることは受け入れた |
| 自動設定を除外する形（Q3 B）・Jakarta Mail の API だけを使う形（Q3 C） | 同上 |
| GreenMail 2.1.14（Apache 2.0） | STARTTLS の受け付けの仕組みが無く、BR1.5 の STARTTLS の成功を確かめられない。推移依存に JUnit 4（EPL 1.0）と、Angus Mail をまとめた JAR（`org.eclipse.angus:jakarta.mail`）が入り、`angus-mail` と同じクラスが重なるおそれがある（Q4 で B を選ばなかった） |
| 暗号化の方式の U1 の項目（`MASTERSMITH_MAIL_ENCRYPTION`） | 依頼者は Spring Boot の項目だけで指定する形を選んだ（F1 B） |

### 1.4 依存の取得元の決まりの読み方（F4 A）

`team.md` の「Gradle の依存の取得元は Maven Central だけに固定する（`settings.gradle.kts` の `RepositoriesMode.FAIL_ON_PROJECT_REPOS`）」は、**依存の解決（アプリと WAR に入る部品、テストの部品）だけ**に当てる決まりと読む（依頼者が F4 A で確かめた）。

- ビルドのプラグインは Gradle Plugin Portal から取ってよい。今のアプリのプラグイン（Spring Boot・Spotless・SpotBugs）も、ルートの設定に `pluginManagement` が無いため Plugin Portal から取っている。
- java-mustache-processor を composite build で組むと、部品のビルドの設定が使うプラグイン（`org.owasp.dependencycheck` 10.0.4（Maven Central に無い）・`com.gradleup.shadow` 9.6.0）も Plugin Portal から取る。これも同じ扱いとする。どれもビルドのときだけの道具で WAR に入らない。
- 部品の実行時の依存（`slf4j-api`）はアプリの依存の解決で Maven Central から取り、lockfile と OSV-Scanner の対象に入る。

## 2. 性能と信頼性に当たる要件（NFR5・NFR6）

| ID | 要件 | 値と作り | 確かめ方 | 出典 |
|---|---|---|---|---|
| NFR5.1 | U1 は内部DB に触れず、トランザクションを持たない。呼び出し元（U3）はトランザクションの外で呼ぶ。送信を待つ間に内部DB の接続を持たない | `mail` パッケージは DB アクセスの層を持たない | ArchUnit（`mail` から DB アクセスの型と `@Transactional` を使わない）。呼び出しの順序は U3 のテスト | NFR5、BR5.4、ADR-009 |
| NFR6.1 | SMTP の接続・読み取り・書き込みに時間切れを持つ | どれも 3 秒。`application.yaml` の `spring.mail.properties` に `mail.smtp.connectiontimeout`・`mail.smtp.timeout`・`mail.smtp.writetimeout`・`mail.smtps.connectiontimeout`・`mail.smtps.timeout`・`mail.smtps.writetimeout` を `3000`（ミリ秒）で置く。接続した後に何も返さない受け手でも約 3 秒で TIMEOUT を返し、招待・送り直しの API の 95 パーセンタイル 5 秒（NFR6、U3 の要件）の中に収まる | 結合テスト: 受け付けて何も返さない `ServerSocket` で TIMEOUT になること（テストの設定では時間切れを短い値にする） | NFR6、BR5.2・BR5.3、Q1 A |
| NFR6.2 | 時間切れの値は環境変数で変えられ、不正な値のときは送らない | 環境変数（例: `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT`）で上書きできる。U1 は起動のときに送信の部品の設定（`getJavaMailProperties`）から、使う `protocol` の3つの値（`smtps` なら `mail.smtps.*`、`smtp`（`ssl.enable` の SMTPS を含む）なら `mail.smtp.*`）を読み、1 以上の整数でなければ項目の名前だけの WARN を1件出して「設定がない」とする（部品は読めない値を時間切れなし（無制限）として扱うおそれがあるため）。環境変数から点を含む鍵（`mail.smtp.connectiontimeout`）へ結び付くことは B1 で確かめる | 結合テスト: 0・負・数でない・空の値で、項目の名前だけの WARN と NOT_CONFIGURED になり、値がログに出ないこと。環境変数での上書きが部品に届くこと | NFR6、BR1.3（差は `security-requirements.md` の5節）、Q2 B |
| NFR6.3 | 送信は1回だけで、自動の再試行と全体の上限を持たない | 失敗しても自動でやり直さない（やり直しは管理者の送り直し）。全体を打ち切るには別のスレッドが要り、ADR-009 の「同じ要求の中で送る」と合わないため、全体の上限は作らない。**既知の限界**: (a) 1つの応答ごとには 3 秒で打ち切るが、応答が時間切れの手前で遅れ続ける受け手では、全体が時間切れの値の数倍になりうる。(b) 接続先の名前の解決（DNS）の待ちは接続の時間切れの外にある。配備先が決まったら値と限界を見直す | 結合テスト: 失敗の後に受け手が受けた接続が1回だけであること | NFR6、BR5.2、FR2.4 |
| NFR6.4 | SMTP の状態がアプリの起動とヘルスチェックを左右しない | `application.yaml` に `management.health.mail.enabled: false` を置く（`MailHealthContributorAutoConfiguration` は、送信の部品があり、メールのヘルスチェックが無効でないときに SMTP に接続するヘルスチェックを足すため）。起動のときに SMTP へ接続を試す `spring.mail.test-connection`（true だと接続できないときに起動を止める）は設定しない。SMTP の設定が無い・不正なときも起動を続ける（数でないポートを除く、F2 A） | 結合テスト: 接続先があり受け手が止まっているときも `/actuator/health` が UP で、応答にメールの項目が無いこと | NFR6、FR1.8、Q3 X |
| NFR6.5 | テンプレートの描画が送信の時間を大きくしない | 起動のときにすべてのテンプレートを準備して持ち、動いている間は差し替えない（BR2.3）。描画は準備済みのテンプレートに差し込むだけで、送信の時間に比べて小さいため、ほかのキャッシュは置かない。準備したテンプレートと設定は読むだけなので、同時に呼ばれてもよい | 単体テスト: 同じ準備済みのテンプレートを複数のスレッドで描いて結果が崩れないこと | NFR6、BR2.3、機能設計の4節 |

## 3. テンプレート（NFR8）

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR8.1 | すべてのテンプレートを ja と en の両方で用意し、どちらかが欠ける・壊れているときは起動を止める。依頼の言語のテンプレートだけで描き、ほかの言語へ切り替えない | 単体テスト: 置き場のすべてのテンプレートを ja・en で描けること、件名が空でないこと、`lang` が合うこと（BR2.6）。欠けたときに起動が止まること | NFR8、BR2.3・BR2.6・BR4.1・BR4.3 |
| NFR8.2 | テンプレートは java-mustache-processor（タグ `0.1.0`、コミット `8d44c36`）で描く。サブモジュールの固定先の更新は、承認を得た専用のコミットで行い、更新の前後のコミットのハッシュを記録する。中身はこのリポジトリから変えない | コード生成の点検（`.gitmodules` と固定先のコミット） | NFR8、FR2.1、`project.md` の Mandated・Forbidden |
| NFR8.3 | 取り込みの形を B1 で確かめる（ADR-010）: (a) 取り込んだ状態で `./gradlew verify` が通り、`settings.gradle.kts` の依存の取得元が Maven Central だけのまま、(b) 部品の推移依存（`slf4j-api`）が `backend/gradle.lockfile` に載り、OSV-Scanner の検査の対象に入る、(c) WAR に core の JAR が入る、(d) CI がサブモジュールを固定先で取得してビルドできる、(e) 部品側の Gradle のプラグインがアプリの Gradle 9.7.1 で動く。1つでも満たせなければ、ADR-010 のとおり Maven Central への公開に切り替える判断を B1 で記録する（部品のリポジトリの変更は部品のリポジトリ側で行う） | B1 のコード生成と Build and Test の記録 | NFR8、ADR-010、`team.md` の Code Style、B1 の完了の条件 |

## 4. テスト（NFR9）

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR9.1 | 送信のテストは、送信の部品をモックで置き換えず、JVM の中で起動する SubEtha SMTP 7.2.2 で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナを使わない。実在の宛先へは送らない（テストの宛先は `example.com` などの予約されたドメイン） | 結合テスト（`XxxIT`）: ja・en の招待のテンプレートで、受け手に届いたメールの宛先・件名・本文・`lang`・Content-Type（text/html、UTF-8） | NFR9、`team.md` の Testing Posture |
| NFR9.2 | 送信の失敗のテストを必ず入れる: 受け手が接続を拒む（閉じたポート）→CONNECTION_FAILED、受け付けて何も返さない（`ServerSocket`）→TIMEOUT、STARTTLS（必須）で受け手が STARTTLS を受け付けない→CONNECTION_FAILED、認証・宛先の拒否→REJECTED。どの場合も、宛先・SMTP の応答・資格情報が結果とログに出ない | 結合テスト | NFR9、BR5.3、`team.md` の Testing Posture |
| NFR9.3 | `team.md` のメールの必須のテスト（本文のエスケープ・テンプレートの描画・ヘッダーへの差し込み・送信の失敗・漏えい）のうち U1 が受け持つものを書く。招待の URL の組み立て（ベース URL だけから）とトークンの漏えいの API の側は U3 が受け持つ | 単体・結合テスト（`security-requirements.md` の NFR1.1・NFR2.8 と、機能設計の BR2.4・BR2.6・BR3.2 の確かめ） | NFR9、`team.md` の Testing Posture |
| NFR9.4 | Q3 X・F1 B・F2 A・F5 A に伴うテストを書く: 既定の設定で送信の部品のセッションが debug でない、メールのヘルスチェックがヘルスチェックに含まれない、接続先が無い・空白だけで NOT_CONFIGURED、時間切れの不正な値、暗号化の方式の分類、資格情報と NONE の組、範囲の外のポート。数でないポートで起動が止まることは Spring Boot の動きのため U1 のテストの対象にしない | 単体・結合テスト（`security-requirements.md` の NFR2.3・NFR2.5・NFR2.6、この文書の NFR6.2・NFR6.4） | NFR9、Q3 X、F1 B、F2 A、F5 A |
| NFR9.5 | 新しいパッケージ `mail` は、パッケージごとのカバレッジの下限（行 80%・分岐 70%）の対象になる。カバレッジの計測から除外を足さない（java-mustache-processor は `vendor/` 配下で、アプリのパッケージではない） | `./gradlew verify` の JaCoCo の関門。値は `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測して記録する | NFR9、`team.md` の Testing Posture、`project.md` の学び |

## 5. 手元の確かめ（NFR11）

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR11.1 | 手元でメールを見る受け手（Mailpit、1.1節の版とダイジェスト）を、compose の profile `mail` で見たいときだけ起動する。アプリからは暗号化 NONE・資格情報なしで compose のネットワークの中の受け手（`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`）に送る。受け手の画面（8025）は `127.0.0.1` だけに公開し、SMTP（1025）はホストに公開しない。手順を README に書く | 手元で profile を起動し、招待のメールが受け手の画面に出ること（B1 の完了の条件） | NFR11、`team.md` の Deployment、B1 の完了の条件 |
| NFR11.2 | 配備先が決まるまで、実在の宛先・外部の SMTP へ送らない。`.env.example` と README の見本の接続先は、手元の受け手だけにする | コード生成の点検 | NFR11、FR2.5、`project.md` の Forbidden |

## 6. 上流との差

承認済みの機能設計（BR1.1〜BR1.8）との差は、`security-requirements.md` の5節にまとめた。この文書に関わるものは次の2つ。

- 時間切れの値を環境変数で変えられるようにし、不正な値を BR1.3 の点検に足した（NFR6.2、Q2 B）。
- SMTP をヘルスチェックから外す設定は、機能設計に無い追加である（NFR6.4、Q3 X）。
