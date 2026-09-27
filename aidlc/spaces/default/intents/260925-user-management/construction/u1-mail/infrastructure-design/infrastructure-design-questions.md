# Infrastructure Design の質問 — u1-mail（メールの描画と送信、library）

U1 は、アプリの中の部品（パッケージ `cherry.mastersmith.mail`）として契約 C1（`MailSender` の `isConfigured` と `send`）を提供する library の単位です。library の単位のため、この段の成果物は `cicd-pipeline.md`・`traceability.json` だけです（`infrastructure-specification.md`・`monitoring-design.md` は service・ui の単位だけ。段の定義の `produces_kinds`）。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

U1 の基盤の作りのほとんどは、承認済みの NFR 要件・NFR 設計・決まりと、既にある仕組み（`.github/workflows/ci.yml`・`build.gradle.kts`・`backend/build.gradle.kts`・`compose.yaml`・`.env.example`・`frontend/playwright.config.ts`）で決まっています。そのため、まず要点（案）を示し、上流から1つに決まらない3点（E2E の WAR から Mailpit への届け方、テストの TLS の証明書の置き方、Mailpit のイメージの更新の知らせ）だけを質問にしました。

読んだ上流と既にある仕組み:

- この単位の承認済みの NFR 設計 `aidlc/spaces/default/intents/260925-user-management/construction/u1-mail/nfr-design/`（`security-design.md` の1〜10節、`logical-components.md` の1〜11節、`traceability.json`、`nfr-design-questions.md` の Q1 A・Q2 B）
- この単位の承認済みの NFR 要件 `aidlc/spaces/default/intents/260925-user-management/construction/u1-mail/nfr-requirements/`（`tech-stack-decisions.md` の1.1〜1.4節と NFR5.1・NFR6.1〜NFR6.5・NFR8.1〜NFR8.3・NFR9.1〜NFR9.5・NFR11.1・NFR11.2、`security-requirements.md`）
- この単位の承認済みの機能設計 `aidlc/spaces/default/intents/260925-user-management/construction/u1-mail/functional-design/functional-spec.md`
- `aidlc/spaces/default/intents/260925-user-management/inception/domain-design/components.md`（Mail）・`decisions.md`（ADR-002・ADR-009・ADR-010）、`aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`（C1・C10）
- NFR Design の承認の場の決定（監査ログの `GATE_APPROVED`・`DECISION_RECORDED`、2026-09-27）: U1 の R-01（B1 の計画に、環境変数の結び付きが成り立たないときの代わりの設定の口の候補を書く）・R-02（B1 で `MailUnexpectedException` を作る部品を1か所にする）はコード生成で拾う。上流との差 A1〜A10 は受け入れ済み。設計の文書と違う U1 の基盤に関わる決定は無い
- 既にある仕組み: `.github/workflows/ci.yml`（`submodules: true`、秘密を使わない、`./gradlew verify`、制限時間 60 分）、`settings.gradle.kts`（`RepositoriesMode.FAIL_ON_PROJECT_REPOS`・Maven Central だけ）、`.gitmodules`（今は make-you-chic-ui だけ）、`.github/dependabot.yml`（gradle・npm・github-actions・docker。サブモジュールは対象外）、`build.gradle.kts`（verify の段 0〜9、ライセンスヘッダーは Java・Kotlin DSL が Spotless、画面は検査スクリプト、`e2eTest`）、`backend/build.gradle.kts`（`spotbugsGate`、`dependencyLocking`）、`compose.yaml`（profile の前例 `monitoring`・`targetdb-*`、画面だけを `127.0.0.1` に公開）、`.env.example`、`.gitignore`（`*.pem`・`*.key`・`*.p12`・`*.jks` を管理外）、`.gitleaks.toml`、`frontend/playwright.config.ts`（WAR を PC の上で `java -jar` で起動し、環境変数を渡す）
- 手元の環境（読み取りだけ、2026-09-27）: colima は CPU 4・メモリ 6GiB で動いている。Mailpit のイメージは `v1.30`・`v1.21`・`latest` があり、設計の `v1.31.2` はまだ無い。PC の 1025・8025 番は使われていない

## Infrastructure Design の要点（案）

### 成果物と範囲

1. **成果物**: `cicd-pipeline.md`（CI・1コマンドの検査・手元の受け手・E2E への設定の渡し方・B1 で確かめること）と `traceability.json`（基盤に関わる NFR の枝番を、設定・タスク・ファイルに対応づける）。U1 は API・内部DB・独自の配備の単位を持たないため、配備の流れ（Dockerfile・イメージ・戻し方）は既存のまま変えない。
2. **監視**: 送信の Observation（`mastersmith.mail.send`、NFR 設計の Q2 B）は、外部エクスポートを有効にして profile `monitoring` を起動したときだけ手元の監視に届く（既定は無効のまま）。ダッシュボードの行と警報の決まりはこの段では作らず、observability-setup の段に引き継ぐ（承認の場の U3 R-02 の引き継ぎと同じ扱い）。

### CI と1コマンドの検査

3. **CI（`ci.yml`）は変えない**: `actions/checkout` の `submodules: true` が、新しいサブモジュール `vendor/java-mustache-processor` も固定先のコミットで取得する（NFR8.3 の (d)）。再帰の取得はしないため、部品のリポジトリの中にサブモジュールが無いことを B1 で確かめる。CI は秘密を使わず、SMTP の接続先・資格情報を CI に渡さない（テストは JVM の中の受け手だけに送る）。
4. **composite build**: ルートの `settings.gradle.kts` に `includeBuild("vendor/java-mustache-processor")` を足し、`dependencyResolutionManagement` の Maven Central だけの決まりは変えない。部品側のビルドのプラグイン（`org.owasp.dependencycheck`・`com.gradleup.shadow`）は Gradle Plugin Portal から取る（F4 A の読み方）。サブモジュールはタグ `0.1.0`（コミット `8d44c36`）に固定し、承認を得た専用のコミットで足して前後のハッシュを記録し、短命のブランチから `develop` へ fast-forward で統合してよい（`team.md` の Way of Working、NFR8.2）。
5. **lockfile と OSV-Scanner**: `spring-boot-starter-mail` の推移依存（`jakarta.mail-api`・`angus-mail` など）と SubEtha SMTP の推移依存は `resolveAndLockAll --write-locks` で `backend/gradle.lockfile` に載り、既存の `osvScan` の対象になる。部品の推移依存 `slf4j-api` は既に lockfile にある（2.0.18）。composite build で置き換えた部品そのもの（`cherry-mustache-core`）は Gradle の依存の固定では lockfile に載らない見込みで、版はサブモジュールの固定先のコミットで固定する。載るもの・載らないものを B1 で実測して記録する（NFR8.3 の (b)）。
6. **Dependabot**: 新しいサブモジュールは既存のコメントどおり対象にしない（固定先の更新は承認を得た専用のコミット）。新しい Maven の依存は既存の gradle の項目で知らせが来る。compose のイメージの知らせは → Q3。
7. **verify の段への入り方**（段は増やさない）:
   - 段 1（フォーマット）: メールのテンプレートのライセンスヘッダーの検査（要点 8）
   - 段 5（単体テスト）: 依頼の確かめ（jqwik）・失敗の分類の表・テンプレートの検査（`{{{`・`{{&`・`{{>`・`{{=`）・Observation のタグ・`MailBoundaryArchitectureTest`
   - 段 6（結合テスト）: SubEtha SMTP 7.2.2 を JVM の中で起動する `XxxIT`（成功・閉じたポート・何も返さない `ServerSocket`・STARTTLS を受け付けない・認証と宛先の拒否・名前の一致しない証明書・漏えい・TRACE・ヘルスチェック）。コンテナを使わないため、コンテナの実行環境が無いときも飛ばさない（`team.md` の Testing Posture）。時間切れはテストの設定で短くし、CI の制限時間 60 分への影響は小さい見込み（時間は Build and Test で測る）
   - 段 7（カバレッジ）: 新しいパッケージ `mail` の5つの下位パッケージは、パッケージごとの下限（行 80%・分岐 70%）の対象で、除外を足さない（NFR9.5）
   - 段 8（安全の検査）: `spotbugsGate` に `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を priority にかかわらず止めるものとして足し、既存の `PREDICTABLE_RANDOM` の1件（`auth/repository/LoginAttemptStateRepository`）を確かめる（NFR2.9）。Gitleaks・OSV-Scanner は既存のまま
   - 段 9（成果物）: WAR に core の JAR が入ることと、WAR の中で `mail/templates/*.html` を数え上げられることを B1 で確かめる（NFR8.3 の (c)、`logical-components.md` の10節）
8. **テンプレートのライセンスヘッダーの検査**: `backend/build.gradle.kts` の Spotless に、メールのテンプレート（`src/main/resources/mail/templates/*.html` とテスト用の `src/test/resources/mail/test-templates/*.html`）を対象にした `format` を足す。ヘッダーは既存の `config/license-header.txt` から Mustache のコメント `{{! ... }}` の形を作り、区切りはヘッダーの後の最初の行（`<!DOCTYPE`）とする。段 1 の `spotlessCheck` で確かめ、段 3 の説明文にメールのテンプレートを加える（`team.md` の Code Style、BR2.5、B1 の完了の条件）。Spotless でこの形を作れないと分かったときは、同じ確かめをする小さな Gradle のタスクに替え、段 3 に入れる（B1 で決めて記録する）。
9. **テストの秘密の値**: テストの宛先は `example.com` などの予約されたドメインだけにする。認証の確かめに使う資格情報は、固定の文字列をコミットせずテストの中で作る値にする（Gitleaks の誤検知と、公開のリポジトリに秘密らしい値を置くことを避ける）。証明書の置き方は → Q2。

### 手元の受け手（Mailpit）と設定

10. **compose の profile `mail`**: サービス `mailpit` を足す。イメージは `axllent/mailpit:v1.31.2@sha256:74d609a42ec279aa63c6b4622a6fa9b5408d1ad5b1d76a1c4be40a265ce0863d`（NFR11.1）、画面（8025）は `127.0.0.1` だけに公開、SMTP（1025）の公開は → Q1。受けたメールを残すボリュームは置かない（コンテナを消すとメールも消える）。メモリの上限は小さく（例 `256m`）し、アプリ（既定 2g）・手元の監視（1536m）と同時に VM 6GiB の中で動かせるようにする。`restart: "no"`・ログの上限は既存のサービスにそろえる。起動は `docker compose --profile mail up -d mailpit`、見終えたら止める。
11. **アプリから手元の受け手へ**: アプリのコンテナは compose のネットワークの中の名前で `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`、暗号化 NONE・資格情報なしで送る。差出人は `MASTERSMITH_MAIL_FROM`（見本の値は `example.com` の宛先）。`app` は既に `.env` を `env_file` で読むため、compose の `app` の `environment` には足さない（前の Intent の学び）。
12. **`.env.example` の SMTP の項目**: 「メール（U1）」の節を足し、すべてコメントの形で置く（接続先の設定が無ければ送らない、NFR2.5）。項目は `SPRING_MAIL_HOST`・`SPRING_MAIL_PORT`・`SPRING_MAIL_PROTOCOL`・`SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD`（秘密）・STARTTLS（必須）と SMTPS の書き方・時間切れの3つ（例 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT`）・`MASTERSMITH_MAIL_FROM`・`MASTERSMITH_MAIL_FROM_NAME`。見本の接続先は手元の受け手（`mailpit`・`1025`）だけにし、実在の宛先・外部の SMTP を書かない（NFR11.2）。`application.yaml` には空の既定の `spring.mail.host` を置かない。
13. **README のメールの節**（B1 の完了の条件）: 設定の項目と方式ごとの書き方、STARTTLS ではポート 587、`starttls.enable` だけの設定の平文の危険と「配備先が決まったら STARTTLS（必須）か SMTPS」（承認の場の R-01）、運用で設定しない値の一覧（`mail.debug`、`ssl.trust`、`checkserveridentity` の false、1.2 より古い TLS、`required` の false、`spring.mail.test-connection` の true、`management.health.mail.enabled` の true、メールの部品のロガーの水準）、Mailpit の profile の起動と止め方、配備先が決まるまで実在の宛先・外部の SMTP へ送らないこと（NFR2.4、`security-design.md` の5節）。
14. **E2E（`./gradlew e2eTest`、verify と CI の外）**: `frontend/playwright.config.ts` の `webServer.env` に、SMTP の接続先（Mailpit）と差出人を足す。既存の E2E（010〜040）はメールを送らず、SMTP の接続先があっても起動のときに接続しないため影響しない（NFR6.4）。Mailpit への届け方と起動の前提は → Q1。Mailpit の API（`127.0.0.1:8025`）から招待のリンクを取り出す方法と、招待のベース URL の渡し方は、使う側の U3・U5〜U7 の段で決める。

### 戻し方と秘密

15. **戻し方**: U1 は DB スキーマを変えない。前の版のイメージへ戻しても、`.env` に残った SMTP の項目は前の版が読まないだけで害は無い。Mailpit は profile を止めるだけでよい（既存の戻しの手順は変えない）。
16. **秘密**: SMTP の資格情報は `.env` だけから受け取る（コミットしない、NFR2.5）。Mailpit の画面は認証なしのため `127.0.0.1` だけに公開し、ほかの端末から見られないようにする。Mailpit は受けたメールを外へ中継しない。

### B1 で確かめること（`logical-components.md` の10節の再掲と、この段の追加）

17. 取り込みの5条件（NFR8.3 の (a)〜(e)、成り立たなければ ADR-010 の切り替え）、環境変数から `spring.mail.properties` の点を含む鍵への結び付き（承認の場の NFR 要件の R-02、成り立たなければ代わりの設定の口を依頼者に諮る。NFR 設計の U1 R-01）、WAR の中のテンプレートの数え上げ、README の記載、SpotBugs の関門、テンプレートのライセンスヘッダーの検査に加えて、この段の追加として (i) 部品のリポジトリの中にサブモジュールが無いこと、(ii) lockfile に載るもの・載らないものの記録、(iii) profile `mail` の起動と、Mailpit を指す設定で `isConfigured` が真になること（招待のメールを画面で見る確かめは B3、`logical-components.md` の9節）。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・警報の通知先は作らない | `project.md` の Deployment |
| 1コマンドの検査は `./gradlew verify` で、CI も同じタスクを呼ぶ。E2E は verify と CI の外 | `project.md` の Way of Working、`team.md` の Testing Posture |
| SMTP のテストは JVM の中の SubEtha SMTP 7.2.2 で実際に受け、コンテナを使わない。失敗のテストを必ず入れる | `team.md` の Testing Posture、NFR9.1・NFR9.2 |
| 手元の受け手は Mailpit v1.31.2（ダイジェストで固定）を profile `mail` で見たいときだけ起動し、画面は `127.0.0.1` だけ | `team.md` の Deployment、NFR11.1 |
| SMTP の接続先と資格情報は `.env` だけ、接続先が無ければ送らない。配備先が決まるまで実在の宛先・外部の SMTP へ送らない | `project.md` の Mandated・Forbidden、NFR11.2 |
| java-mustache-processor はタグ `0.1.0` をサブモジュールと composite build で取り込み、中身を変えない。固定先の更新は専用のコミット | `team.md` の Code Style、`project.md` の Mandated・Forbidden、NFR8.2 |
| 依存の取得元の Maven Central だけは依存の解決に当て、ビルドのプラグインは Plugin Portal でよい | `project.md` の Corrections（F4 A） |
| `spotbugsGate` に `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を足す | `team.md` の Code Style、NFR2.9 |
| メールのテンプレートをライセンスヘッダーの検査の対象に加え、ヘッダーは `{{! ... }}` | `team.md` の Code Style、BR2.5 |
| 新しいパッケージ `mail` はパッケージごとのカバレッジの下限の対象で、除外を足さない | `team.md` の Testing Posture、NFR9.5 |

---

## Q1. E2E で使う WAR から、手元の受け手（Mailpit）へどう届けますか？

理由: E2E（`./gradlew e2eTest`）は、WAR を PC の上で `java -jar` で起動します（`frontend/playwright.config.ts`、コンテナの中ではない）。一方、NFR11.1 は Mailpit の SMTP（1025）を PC に公開しないとしていて、そのままでは E2E の WAR から Mailpit に送れません。招待の E2E（U5〜U7 が頼る、Intent ごとに1本）は、招待のメールを Mailpit で受けて API（`127.0.0.1:8025`）からリンクを取り出す必要があります。既存の E2E（010〜040）はメールを送らないため、どの形でも影響しません。どの形でも、Mailpit に届かないときは招待の E2E が送信の失敗（CONNECTION_FAILED）で落ちるため、黙って進まないように前提を確かめます。

A. profile `mail` の Mailpit の SMTP（1025）も `127.0.0.1` だけに公開し、E2E の WAR は `localhost:1025` に送る（`webServer.env` に `SPRING_MAIL_HOST=localhost`・`SPRING_MAIL_PORT=1025`・差出人を足す）。起動は開発者が `docker compose --profile mail up -d mailpit` で行い、`e2eTest` は始める前に `127.0.0.1:8025` の API に届くかを確かめ、届かなければ起動の手順を示して失敗させる（黙って飛ばさない。既存の道具の確かめ `requireTool` と同じ考え方）。開発で見るメールと E2E のメールは同じ受け手に入るが、E2E は実行ごとに違う宛先を使って取り出す。NFR11.1 の「SMTP は PC に公開しない」との差を記録する（公開は `127.0.0.1` だけで、Mailpit は外へ中継しない）（推奨）
B. A と同じく 1025 を `127.0.0.1` に公開し、`e2eTest` が Mailpit を自動で起動する（`docker compose --profile mail up -d --wait mailpit`）。手順は1つ減るが、`e2eTest` がコンテナの実行環境に依存し、終わった後に止めるかどうかの扱いが要る
C. 開発で見る `mail` とは別に、E2E 専用のサービス（例 `mailpit-e2e`、profile `e2e`、SMTP と API を別の番号で `127.0.0.1` に公開）を足し、`e2eTest` が起動と停止を行う。profile `mail` の形（NFR11.1）は変えずに済み、開発のメールと混ざらないが、compose のサービスが1つ増え、同じイメージを2つの設定で持つ
X. Other (please specify)

[Answer]: A

## Q2. SubEtha SMTP の STARTTLS・SMTPS の結合テストで使う TLS の証明書を、どう用意しますか？

理由: NFR2.7・NFR9.2 のテスト（STARTTLS・SMTPS で送れる、名前の一致しない証明書で CONNECTION_FAILED になる）には、受け手の側の自己署名の証明書と鍵が要ります。NFR 設計（`logical-components.md` の8節）は「`src/test/resources` に置き、Gitleaks の誤検知になれば理由を書いて除外する」としていますが、基盤を確かめたところ、`.gitignore` が `*.pem`・`*.key`・`*.p12`・`*.jks` を管理外にしていて、`project.md` の Forbidden も「鍵ファイルなど秘密情報を含む設定ファイルをコミットしない」としています。試験用の鍵は秘密ではありませんが、公開のリポジトリで鍵ファイルの例外を作るかどうかは、決まりの読み方に関わります。

A. テストの実行のときに作る: テストの支え（例 `backend/src/test/java/cherry/mastersmith/mail/testsupport/`）が、JDK に入っている `keytool`（`java.home` の `bin`）を呼んで、名前の合う証明書と合わない証明書を一時のディレクトリに作り、テストの設定の中だけで信頼させる。何もコミットせず、`.gitignore`・Gitleaks の例外も新しい依存も足さない。代わりに、テストが JDK の `keytool` に頼り（手元・CI とも JDK 25 の Temurin で入っている）、作るのに少し時間がかかる。NFR 設計の8節との差として記録する（推奨）
B. 試験用の証明書と鍵をコミットする: `backend/src/test/resources/mail/tls/` に置き、`.gitignore` にその場所だけの例外（`!` の行）と、`.gitleaks.toml` にその場所だけの除外を理由つきで足す。テストは簡単になるが、公開のリポジトリに鍵ファイルが載り、Forbidden の読み方（試験用の鍵は秘密ではないので除く）を記録する必要がある
C. テストの依存に BouncyCastle（`bcpkix`、MIT 系のライセンス）を足し、証明書と鍵をメモリの中で作る。ファイルも外部の道具も要らないが、新しい依存（ライセンスの確かめ・lockfile・OSV の対象）が増える
X. Other (please specify)

[Answer]: A

## Q3. compose のイメージ（Mailpit など）の更新の知らせを、どう受けますか？

理由: Mailpit のイメージは既存の対象DB のイメージと同じく版とダイジェストで固定します（NFR11.1）。今の `.github/dependabot.yml` の `docker` の項目は Dockerfile のベースのイメージの知らせを受ける設定で、`compose.yaml` のイメージは GitHub の Dependabot では別の `docker-compose` の項目で扱う仕組みになっています（今は設定が無く、`compose.yaml` のイメージの知らせは来ていない見込み）。Mailpit は手元の確かめだけに使い WAR に入りませんが、画面を `127.0.0.1` に公開するコンテナです。

A. `.github/dependabot.yml` に `docker-compose` の項目（`directory: /`、週ごと）を足す。Mailpit に加えて、既存の `compose.yaml` のイメージ（手元の監視・受け手の確認用・見本の対象DB）の知らせも来るようになる。知らせは `team.md` の Dependabot の受け方（画面でマージせず、手元で更新して verify を通してから統合）で扱い、見本の対象DB のイメージを上げるときは結合テストのイメージの定数（`TargetDbImages`）と一緒に上げる。B1 で実際に知らせの対象になったかを確かめて記録する（推奨）
B. Dependabot は変えず、Mailpit の版は手で見直す（リリースの前や、関わる Bolt のときに確かめる。README に見直しの手順を書く）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（回答の後に書き直します）:

- 要点（案）は冒頭の 17 件のとおり（成果物と監視の範囲、CI は変えずサブモジュールを固定先で取得、composite build と取得元の決まり、lockfile と OSV-Scanner、Dependabot、verify の段への入り方、テンプレートのライセンスヘッダーの検査、テストの秘密の値、profile `mail` の Mailpit、アプリから受け手への設定、`.env.example` の SMTP の項目、README のメールの節、E2E への設定の渡し方、戻し方、秘密、B1 で確かめること）
- Q1: A — profile `mail` の Mailpit の SMTP（1025）も `127.0.0.1` だけに公開し、E2E の WAR は `webServer.env` で `localhost:1025` に送る。Mailpit の起動は開発者が行い、`e2eTest` は始める前に `127.0.0.1:8025` の API に届くかを確かめ、届かなければ起動の手順を示して失敗させる。NFR11.1 との差として記録する
- Q2: A — STARTTLS・SMTPS の結合テストの証明書は、テストの実行のときに JDK の `keytool` で一時のディレクトリに作る。何もコミットせず、`.gitignore`・Gitleaks の例外も新しい依存も足さない。NFR 設計の8節との差として記録する
- Q3: A — `.github/dependabot.yml` に `docker-compose` の項目（週ごと）を足し、`compose.yaml` のイメージの知らせも `team.md` の受け方で扱う。B1 で実際に知らせの対象になったかを確かめて記録する

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
