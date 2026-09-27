# Code Generation Plan — U1 メールの描画と送信（u1-mail）

U1 のコード生成の計画を示す。作るものは、テンプレートを描いて HTML のメールを作り SMTP で送る共通の部品（パッケージ `cherry.mastersmith.mail`、契約 C1 の `MailSender`）と、その土台（java-mustache-processor のサブモジュールと Gradle の composite build、テスト用の SMTP の受け手、SpotBugs の関門、テンプレートのライセンスヘッダーの検査、手元の受け手 Mailpit）である。Bolt は B1（この Intent の最初の Bolt、`inception/delivery-planning/bolt-plan.md`）。U1 は library の単位で、API・画面・内部DB の表を持たない。

この計画の中のパスは、特に断らない限りリポジトリのルートからの相対パスで、記録のディレクトリは `aidlc/spaces/default/intents/260925-user-management/`（以下「記録」）とする。

## 1. 入力にした設計

| 文書（記録の下） | 使うところ |
|---|---|
| `construction/u1-mail/functional-design/functional-spec.md`・`rules.md`・`entities.md`・`functional-design-questions.md` | 起動時の点検とテンプレートの準備、送信の流れ、値の型、決まり BR1.1〜BR6.4、答え Q1 C・Q2 B・Q3 A・Q4 A、承認の場の Request Changes（11節） |
| `construction/u1-mail/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`・`nfr-requirements-questions.md` | NFR1.1〜NFR2.9、NFR5.1・NFR6.1〜NFR6.5・NFR8.1〜NFR8.3・NFR9.1〜NFR9.5・NFR11.1・NFR11.2、部品の版とライセンス、機能設計との差（5節） |
| `construction/u1-mail/nfr-design/security-design.md`・`logical-components.md`・`nfr-design-questions.md` | 5つの下位パッケージと部品、点検の表・方式の分類の表・失敗の分類の表、秘密の扱い、Observation、テストの形、B1 で確かめること（10節）、上流との差（10節・11節） |
| `construction/u1-mail/infrastructure-design/cicd-pipeline.md`・`infrastructure-design-questions.md` | verify の段への入り方、テンプレートのヘッダーの検査、composite build と lockfile、テストの証明書（Q2 A）、Mailpit（Q1 A）、E2E への渡し方、Dependabot（Q3 A）、B1 で確かめること（12節） |
| `inception/contract-design/contract-summary.md` の C1・C10 | `MailSender` の形（`isConfigured`・`send`）、招待メールの形（中身は U3） |
| `inception/domain-design/components.md`（Mail）・`decisions.md`（ADR-009・ADR-010） | 依存を持たない部品であること、トランザクションの外で呼ばれること、取り込みの見通しと切り替え先 |
| `inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md` | U1 の責務と境界、US3.1 が主、US1.1・US2.2 に関わる |
| `inception/requirements-analysis/requirements.md`・`inception/user-stories/stories.md` | FR2.1〜FR2.6・FR1.8・FR7.3・FR9.2、NFR1・NFR2・NFR5・NFR6・NFR8・NFR9・NFR11、US3.1 の AC3.1.1〜AC3.1.10 |
| `inception/delivery-planning/bolt-plan.md` の B1 | 完了の条件、統合の形（fast-forward） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログから洗い出したもの）

`project.md` の学び（計画の前に承認の場の決定を洗い出す）に従い、監査ログの `DECISION_RECORDED`（承認の場の決定）・`GATE_APPROVED`・`GATE_REJECTED` と、レビューの記録（`.aidlc-reviews/*/units/u1-mail/*/1.json`）の指摘を読んだ。U1 と B1 に関わるものと、この計画での扱いは次のとおり。

| 段 | 決定・指摘 | この計画での扱い | 手順 |
|---|---|---|---|
| 機能設計（Request Changes） | U1 R-01: 空の文字列・空白だけの差し込む値も拒否する | BR3.3 の境界（null・`""`・半角の空白・タブ・全角の空白は拒否、`"a"` と `" a "` は受け付ける）をそのまま実装しテストする | Step 5・6 |
| 機能設計（Request Changes） | U3 R-02 に伴う変更: 招待の差し込みは `registrationUrl`・`validityHours` | 招待の一覧の行とテンプレートは B3（U3）で足す。B1 の本番の一覧は空（`logical-components.md` 4節） | Step 9 |
| 機能設計（Approve、G6） | 契約への反映（C10 の有効期限の文言ほか）は遅くともコード生成の計画で確かめる | C10 の「24 時間」の固定の文言と `validityHours` の差は U3 の持ち物で、B1 では招待のテンプレートを作らないため反映しない。B3 の計画で確かめることとして10節に書く | 10節 |
| 機能設計（Approve） | U1 R-02（宛先の決まり BR3.1 を U1 の中に独自に持つ）は依頼者が受け入れた | 既存の `user.domain.EmailAddress` に依存せず `mail.domain` に同じ決まりを持つ | Step 5 |
| NFR 要件（Approve） | R-01: `starttls.enable` だけの設定の平文の危険を README に書くことを B1 の完了の条件にする | README のメールの節に書き、書けていなければ B1 を完了としない | Step 23 |
| NFR 要件（Approve） | R-02: 環境変数の結び付きと取り込みの5条件は B1 で実際に確かめ、成り立たなければ切り替える | Step 8（結び付き）・Step 2・24・25（5条件）で確かめ、記録する | Step 2・8・24・25 |
| NFR 要件（Approve の Minor） | U1 の README の運用の決まりと ADR-010 の切り替えの確かめはコード生成の計画で拾う | README の運用で設定しない値の一覧（NFR2.4）と、5条件のどれかが成り立たないときの判断の記録を手順に入れる | Step 23・25 |
| NFR 設計（Approve） | U1 R-01: 環境変数が点を含む鍵に結び付かなかったときの代わりの設定の口の候補を B1 の計画に書く | 候補を 8節に書き、結び付かなかったときだけ依頼者に諮る | Step 8、8節 |
| NFR 設計（Approve） | U1 R-02: 想定外の分類を判定して例外を組み立てて投げる部品を1か所に決める | 例外を組み立てるのは `MailUnexpectedException.of(Throwable)` の1つだけ。送信の失敗の判定は `SendFailureClassifier`（投げない純粋な関数）、投げるのは `SmtpMailTransport` だけ。`SmtpMailSender` は包まれていない実行時の例外（U1 の不具合）を同じ `of` で包む最後の守りだけを持つ（8節） | Step 11・13 |
| NFR 設計（Approve） | U3 R-01（Major）: U1 の設計を正として、コード生成で `MailSendResult` の形を合わせる | `MailSendResult` は `entities.md` のとおり outcome（SENT・FAILED）と failureKind の record で作る。U3 の側は B3 で合わせる | Step 5 |
| 基盤の設計（Approve、D1〜D3） | Mailpit の SMTP を `127.0.0.1` に公開、テストの証明書を実行時に `keytool` で作る、Dependabot に `docker-compose` | そのまま作る | Step 15・20・22 |
| 基盤の設計（Approve、N8） | Mailpit の保持の上限を B1 で確かめる | 実際のイメージで上限の値を確かめ、README に書く | Step 20・23 |
| 基盤の設計（Approve、N2・N6） | 050 だけでも Mailpit が要る（B4・B5）、送信の指標の名前の確かめ（observability-setup） | B1 では `e2eTest` の前提の確かめを作る（すべての E2E の実行で Mailpit が要る）。指標の名前の確かめは observability-setup に引き継ぐ | Step 21、10節 |

### 2.2 この計画での読み方

- **設計の文書どおりに作るもの**: 部品の構成（`logical-components.md` 1節）、点検の表（`security-design.md` 2.2）、方式の分類の表（2.3）、失敗の分類の表（`logical-components.md` 5.3）、出してよい項目と伏せ方（`security-design.md` 4節）、Observation（`logical-components.md` 6節）、テンプレートの検査に `{{>`・`{{=` を足すこと（`security-design.md` 7節）。
- **B1 の本番の一覧は空**: 本番の置き場 `backend/src/main/resources/mail/templates/` にファイルは置かず、一覧も空で起動する（`logical-components.md` 4節）。U1 のテストはテスト用の一覧と置き場で行う。本番の置き場を数え上げるテンプレートの検査は、B1 では対象が0件でも通り、B3 で U3 が招待のテンプレートを足すと自動で対象になる。
- **宛先の型**: 契約 C1 は宛先を `EmailAddress` としているが、機能設計（`functional-spec.md` 10節）のとおり、正規化済みの文字列で受け、`mail.domain` の中の決まり（BR3.1）で確かめる。
- **一覧の引き当てと依頼の確かめ**: `mail.domain` は `mail.template` に依存しない（`logical-components.md` 3節の境界）。そのため、templateId から差し込みの名前の集合を引くのは `mail.service` が行い、`MailRequestValidation` には「一覧に有るか」と「期待する名前の集合」を引数で渡す。確かめの順序（言語 → templateId → 改行 → 宛先の形 → 差し込み）は `functional-spec.md` 4節のとおり。
- **SMTP の送信の部品**: Spring Boot のメールの自動設定（`spring-boot-starter-mail`）が作る `JavaMailSenderImpl` を `ObjectProvider` で受け、U1 は設定を読むだけで書き換えない（`security-design.md` 2.1）。
- **既存の ArchUnit を緩めない**: 全体の決まり（`ArchitectureTest`）と既存の機能ごとの境界テストは変えない。U1 の境界は `MailBoundaryArchitectureTest` を足して守る（`team.md` の Code Style）。

## 3. 作業の場とコミットの区切り

- **作業のブランチ**: `develop` から短命のブランチ `feature/260925-user-management-b1` を作る（`team.md` の Way of Working、9節の決定 1）。
- **統合の形**: このブランチはサブモジュール `vendor/java-mustache-processor` の固定先の追加を含むため、固定先の追加を専用のコミットとして残すよう、squash ではなく短命のブランチから `develop` へ fast-forward で統合する（`team.md` の Way of Working、`bolt-plan.md` の共通の完了の条件）。統合の前に `./gradlew verify` を通す（Step 26）。
- **サブモジュールの固定先**: タグ `0.1.0`、コミット `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`。前の固定先は無い（新しいサブモジュール）。前後のハッシュを `code-summary.md` に記録する（`project.md` の Mandated）。中身はこのリポジトリから変えない（`project.md` の Forbidden）。
- **git の操作の順**: ブランチの作成とサブモジュールの追加（`git submodule add` は `.gitmodules` と gitlink を索引に載せる）は、ビルドの前に要るため Step 1 で行う（依頼者が承認した。9節の決定 1）。
- **コミット**: 生成の担当はコミットしない。生成の後に、依頼者の承認を得て、次の区切りでまとめてコミットする（`project.md` の Change Control の学び）。メッセージは日本語。

| 区切り | 中身 | 手順 |
|---|---|---|
| C1 | サブモジュール `vendor/java-mustache-processor` の追加（`.gitmodules` と gitlink だけ。専用のコミット、固定先 `8d44c36` を本文に書く） | Step 1 |
| C2 | composite build と依存の追加（`settings.gradle.kts`・`gradle/libs.versions.toml`・`backend/build.gradle.kts` の依存・`backend/gradle.lockfile`） | Step 2 |
| C3 | `mail` パッケージの本番のコードと `application.yaml` | Step 3・5・7・9・11・13 |
| C4 | `mail` のテスト（単体・結合・構造の検査・テストの支え・テスト用のテンプレート） | Step 4・6・8・10・12・14・15・16 |
| C5 | ビルドの関門（SpotBugs の関門・除外の設定・テンプレートのヘッダーの検査・サブモジュールの変更の検査・verify の段の説明） | Step 17・18・19 |
| C6 | 基盤と文書（`compose.yaml`・`.env.example`・`frontend/playwright.config.ts`・`build.gradle.kts` の `e2eTest`・`.github/dependabot.yml`・`README.md`） | Step 20〜23 |
| C7 | この段の記録（記録の `construction/u1-mail/code-generation/` の下） | Step 27 |

- `origin` への `git push` は依頼者が行う。AI はプッシュしない。

## 4. 作るもの（パッケージ `cherry.mastersmith.mail`）

| パッケージ | 部品 | 役割 |
|---|---|---|
| `mail.domain` | `MailRequest`（record、`toString` は templateId・language だけ）、`MailSendResult`（record: `outcome`・`failureKind`、`sent()`・`failed(kind)`）、`MailOutcome`（SENT・FAILED）、`MailFailureKind`（NOT_CONFIGURED・CONNECTION_FAILED・TIMEOUT・REJECTED・INVALID_INPUT・TEMPLATE_ERROR）、`MailUnexpectedException`（実行時の例外。固定の文言と原因の連なりの型の名前だけを持ち、原因を付けない。作るのは `of(Throwable)` だけ） | 契約 C1 の値の型（BR6.1、NFR1.2、`security-design.md` 4.3・4.4） |
| `mail.domain` | `MailRequestValidation`（純粋な関数、Bean でない）、`MailAddressRule`（BR3.1 の決まり。差出人の点検でも使う） | 依頼の確かめ（BR3.1〜BR3.4）。最初に当たったもので止める |
| `mail.config` | `MastersmithMailProperties`（`@ConfigurationProperties("mastersmith.mail")`、`from`・`fromName` を文字列で受け `@Validated` なし、`toString` は有無だけ）、`EncryptionMode`（NONE・STARTTLS・SMTPS）と分類の純粋な関数、`MailSettings`（sealed: `NotConfigured`・`Invalid(problemItems)`・`Usable(sender, from, mode)`、`toString` は状態と方式だけ）と `inspect`、`MailConfig`（`@Configuration`、`MailSettings` を1回だけ作り WARN を1件だけ出す） | 起動時の点検と方式の分類（`security-design.md` 2節、BR1.1〜BR1.8、NFR2.5・NFR2.6・NFR6.2） |
| `mail.template` | `MailTemplateCatalog`（一覧の定数。B1 の本番は空）、`MailTemplateDefinition`（templateId と差し込みの名前の集合）、`MailTemplateRegistry`（Bean。置き場の数え上げ・名前の照合・ja と en の準備・描画）、`TemplatePreparationException`（templateId・language・原因の種類 MISSING・PARSE_ERROR・INVALID_NAME・UNKNOWN_TEMPLATE だけを持つ）、`RenderedMail`（`toString` は templateId・language だけ）、`RenderedMailInspector`（title の文面と html の lang の取り出し） | テンプレートの一覧・準備・描画・件名と lang（BR2.1〜BR2.3、BR4.1〜BR4.4、NFR8.1、NFR6.5）。`TraceAspect` の対象の外（Q1 A） |
| `mail.transport` | `SmtpMailTransport`（Bean。`MimeMessageHelper`（UTF-8）で組み立て、`JavaMailSender.send` を1回だけ呼び、失敗を分類する。想定外なら `MailUnexpectedException.of` で包んで投げる唯一の地点）、`SendFailureClassifier`（純粋な関数。例外の原因の連なりの型だけで分類し、分類の結果を返す。投げない） | メールの組み立てと1回だけの送信と失敗の分類（BR5.1〜BR5.3、NFR6.1・NFR6.3、NFR2.8）。`TraceAspect` の対象の外 |
| `mail.service` | `MailSender`（インターフェース、契約 C1: `isConfigured`・`send`）、`SmtpMailSender`（Bean。状態 → 確かめ → 描画 → 件名と lang → 送信の順に呼び、ログを1件出し、Observation `mastersmith.mail.send` を1つ作る。未知の templateId・language はログとタグに `unknown` と出す） | 入口（契約 C1、BR1.7、BR6.2・BR6.3、Q2 B） |

U3（呼び出し元）が使うのは `mail.service` の `MailSender` と `mail.domain` の型だけとし、`mail.config`・`mail.template`・`mail.transport` は外から使わせない（`MailBoundaryArchitectureTest`）。

## 5. 手順

各層で実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む（test-after、Testing Contract の `ordering`）。U1 は API・画面・内部DB を持たないため、層は「値の型と確かめ（domain）→ 設定（config）→ 描画（template）→ 送信（transport）→ 入口（service）→ 境界の結合テスト」の順とする。

### Step 1: 作業の場の用意と、変更の前の基準（依頼者が承認した git の操作。9節の決定 1）

- [x] `develop` の先頭のハッシュを記録し、短命のブランチ `feature/260925-user-management-b1` を作る
- [x] 変更の前の基準をとる: colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、テストの件数（単体・結合、失敗・飛ばした）と全体のカバレッジを記録する（brownfield の Test Baseline、`project.md` の学び）
- [x] `git submodule add https://github.com/agwlvssainokuni/java-mustache-processor vendor/java-mustache-processor` の後、サブモジュールの中でタグ `0.1.0`（コミット `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`）を取り出して固定する。コミットはしない（C1 は生成の後）
- [x] サブモジュールの中に入れ子のサブモジュール（`.gitmodules`）が無いことを確かめ、記録する（CI の `submodules: true` は再帰しないため。`cicd-pipeline.md` 12節）
- [x] 対応: NFR8.2、NFR8.3 の (d) の前提、B1 の完了の条件（取り込み）

### Step 2: composite build と依存の追加

- [x] `settings.gradle.kts` に `includeBuild("vendor/java-mustache-processor")` を足す。`dependencyResolutionManagement` の Maven Central だけの決まり（`FAIL_ON_PROJECT_REPOS`）は変えない。サブモジュールが無いと composite build の解決で失敗することをコメントに書く
- [x] `gradle/libs.versions.toml` に `spring-boot-starter-mail`（版は Spring Boot の BOM）・`cherry-mustache-core`（`cherry.mustache:cherry-mustache-core`、版 `0.1.0`。実体は composite build で置き換える）・`subethasmtp`（`com.github.davidmoten:subethasmtp` 7.2.2）を足し、理由をコメントに書く
- [x] `backend/build.gradle.kts` で starter-mail と core を `implementation`、SubEtha SMTP を `testImplementation` に足す
- [x] `./gradlew :backend:resolveAndLockAll --write-locks` で `backend/gradle.lockfile` を更新し、差を記録する。載るはずのもの: `jakarta.mail-api` 2.1.5・`angus-mail` 2.0.5・`spring-context-support`・`jsr305` 3.0.2・`guava-mini` 0.1.7 ほか。core の推移依存 `slf4j-api` が `runtimeClasspath` の行に載ること、composite build で置き換えた `cherry-mustache-core` 自体は載らないことを実測して記録する（NFR8.3 の (b)）
- [x] `./gradlew :backend:dependencies --configuration runtimeClasspath`（と `testRuntimeClasspath`）で依存の木を見て、推移依存が既存の部品の版を引き上げていないことを確かめる（`project.md` の学び）
- [x] `settings-gradle.lockfile` が変わるかを確かめ、変わったら理由を記録する
- [x] 部品側のプラグイン（`org.owasp.dependencycheck` 10.0.4・`com.gradleup.shadow` 9.6.0）が Gradle Plugin Portal から取られ、アプリの Gradle 9.7.1 で構成の評価が通ることを確かめる（NFR8.3 の (e)、F4 A の読み方）
- [x] 新しい部品のライセンスを jar の中の文書と POM で確かめる（Jakarta Mail・Angus Mail は EPL 2.0・GPL2 w/ CPE・EDL 1.0 から選べる形、SubEtha SMTP・guava-mini・jsr305・java-mustache-processor は Apache 2.0）。Apache 2.0 と違うものの理由は `tech-stack-decisions.md` 1.2 の記録を README のライセンスの節に写す（`team.md` の Code Style）
- [x] **(b) が成り立たないとき**（`slf4j-api` が lockfile に載らない）: 部品の依存を検査の対象に加える仕組み（例: included build の実行時の依存を lockfile の形で `build/` に書き出す Gradle のタスクを作り、`osvLockfiles` に加える）を足す案を記録し、依頼者に諮る（`team.md` の Code Style）
- [x] 対応: NFR8.2・NFR8.3 の (a)(b)(e)、ADR-010、B1 の完了の条件（取り込み）

### Step 3: 骨組みと本番の設定

- [x] `mail`・`mail.service`・`mail.domain`・`mail.config`・`mail.template`・`mail.transport` の `package-info.java`（日本語の説明、ライセンスヘッダー）を作る
- [x] `backend/src/main/resources/application.yaml` に次を理由のコメントつきで足す:
  - `spring.mail.properties`: `mail.smtp.connectiontimeout`・`mail.smtp.timeout`・`mail.smtp.writetimeout` と `mail.smtps.*` の同じ3つを `3000`、`mail.debug: false`、`mail.smtp.ssl.checkserveridentity: true`・`mail.smtp.ssl.protocols: TLSv1.3 TLSv1.2`・`mail.smtps.ssl.checkserveridentity: true`・`mail.smtps.ssl.protocols: TLSv1.3 TLSv1.2`（NFR6.1・NFR2.3・NFR2.7）
  - `spring.mail.host` は置かない（空の既定でも自動設定が送信の部品を作るため。NFR2.5）。`spring.mail.test-connection` も置かない（NFR6.4）
  - `mastersmith.mail.from: ${MASTERSMITH_MAIL_FROM:}`・`mastersmith.mail.from-name: ${MASTERSMITH_MAIL_FROM_NAME:}`（U1 の設定の型。NFR2.5）
  - `management.health.mail.enabled: false`（NFR6.4）
  - `logging.level` の `org.eclipse.angus: OFF`・`jakarta.mail: OFF`（対象DB のドライバーと同じ置き方と、理由と調べ方のコメント。NFR2.2）
- [x] 対応: BR1.1・BR1.8、NFR2.2〜NFR2.5・NFR2.7・NFR6.1・NFR6.4

### Step 4: テストの実行の準備（最初のテストより前）

- [x] `unit-test-instructions.md` のコマンドが、composite build と依存を足した状態で動くことを確かめる。まだ `mail` のテストが無いため、既存の構造の検査で道具が動くことを確かめる: `./gradlew :backend:test --tests 'cherry.mastersmith.ArchitectureTest'`
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.mail.*'` は、`mail` のテストが無い間は Gradle の「一致するテストが無い」で失敗し、Step 6 から通ることを確かめて `unit-test-instructions.md` の記述と合わせる
- [x] `backend/src/test/resources/junit-platform.properties` の jqwik の設定（失敗時の乱数の種の記録）が既存のまま使えることを確かめる
- [x] 対応: Testing Contract の `runner_step`、NFR9.1

### Step 5: 値の型と依頼の確かめ（mail.domain）— 実装

- [x] `MailRequest`（変えられない `Map` の写し、`toString` は templateId・language だけ）、`MailSendResult`（outcome と failureKind の record。SENT のときの failureKind は無し、FAILED のときは必須を生成時に確かめる）、`MailOutcome`、`MailFailureKind`（6つ）
- [x] `MailUnexpectedException`: 固定の文言と原因の連なり（`getCause`・`MessagingException.getNextException`・Spring の `MailSendException.getMessageExceptions`）の型の名前の一覧だけを持ち、原因を付けない。作る口は `of(Throwable)` の1つだけ（NFR 設計の U1 R-02）。連なりが輪になっても止まるよう、たどる数に上限を置く
- [x] `MailAddressRule`（BR3.1: 空・前後の空白・大文字・254 文字を超える・形に合わない、は不可）と `MailRequestValidation`（言語 → templateId（一覧に有るかは引数）→ 宛先・差し込む値の CR/LF → 宛先の形 → 差し込みの名前の完全一致と null・空・空白だけ（`String.strip` と同じ判定）の順。結果は通るか失敗の種類）
- [x] 対応: US3.1（AC3.1.7）、BR3.1〜BR3.4・BR6.1、NFR1.2、NFR2.8、契約 C1

### Step 6: 値の型と依頼の確かめ — テスト（単体）

- [x] `MailRequestTest`: `toString` に宛先・差し込む値が無い、`Map` を外から変えられない（NFR1.2）
- [x] `MailSendResultTest`: SENT・FAILED の作り方、FAILED の種類の必須、`toString` が種類だけ（BR6.1）
- [x] `MailUnexpectedExceptionTest`: 文言が固定で宛先・部品の文言を含まない、原因が付かない、型の名前の連なり（`getNextException`・`getMessageExceptions` を含む）、輪になった連なりで止まる（`security-design.md` 4.3）
- [x] `MailRequestValidationTest`: 確かめの順序（最初に当たったもの）、言語の誤り → INVALID_INPUT、一覧に無い templateId → TEMPLATE_ERROR、宛先・値の CR/LF → INVALID_INPUT、宛先の形（254・255 文字の境界、大文字、前後の空白）、差し込みの欠け・余分、BR3.3 の境界（null・`""`・`" "`・`"\t"`・全角の空白だけは拒否、`"a"`・`" a "` は受け付ける）を2つの名前で確かめる（BR3.1〜BR3.4、承認の場の U1 R-01）
- [x] 性質ベースのテスト（jqwik、失敗時の種を記録）: 任意の文字列に CR か LF を1つ入れた宛先・値はいつも INVALID_INPUT、空白だけの任意の文字列の値はいつも INVALID_INPUT、`MailAddressRule` を通る宛先はいつも CR/LF を含まず 254 文字以内（`team.md` の Testing Posture）
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.mail.*'` を実行して通す

### Step 7: 設定と点検（mail.config）— 実装

- [x] `MastersmithMailProperties`（文字列で受け、`toString` は有無だけ）
- [x] `EncryptionMode` の分類の純粋な関数（`security-design.md` 2.3 の表: `protocol` が `smtps` か使う方式の `ssl.enable` が true → SMTPS、`mail.smtp.starttls.enable` と `required` が両方 true → STARTTLS、そのほか → NONE）
- [x] `MailSettings.inspect(senderOrNull, props)`: `security-design.md` 2.2 の9行の表の条件をすべて集め、1つでもあれば `Invalid`（項目の名前だけの一覧）。差出人は `MailAddressRule` で確かめ、表示名（無ければ `MasterSmith`）つきの `InternetAddress`（UTF-8）に組み立てて `Usable` に持つ。資格情報の値は持たない
- [x] `MailConfig`: `ObjectProvider<JavaMailSenderImpl>` で送信の部品を「あれば使う」形で受け、`MailSettings` を1回だけ作り、`Invalid` のときだけキー `items` に項目の名前を並べた WARN を1件出す（値は出さない）。あわせて、起動の時に設定の状態（設定がない・不正・ある）と方式（NONE・STARTTLS・SMTPS）だけを INFO で1行出す（接続先・差出人・資格情報の値は出さない。8節の P2、9節の決定 3）。U1 は送信の部品の設定を読むだけで書き換えない
- [x] 対応: BR1.1〜BR1.7、NFR2.1・NFR2.5・NFR2.6・NFR6.2、F1 B・F2 A・F5 A

### Step 8: 設定と点検 — テスト（単体・結合）

- [x] `EncryptionModeTest`: 表のすべての行（何も無い → NONE、STARTTLS の2つ → STARTTLS、`starttls.enable` だけ → NONE、`protocol` が `smtps` → SMTPS、`ssl.enable` → SMTPS、SMTPS と STARTTLS の両方 → SMTPS）（NFR2.6）
- [x] `MailSettingsTest`: 点検の表の9行（部品も差出人も無い → `NotConfigured`、接続先が空白だけ、差出人の欠け・形・改行、表示名の改行、資格情報の片方、ポートの範囲の外、知らない `protocol`、時間切れの 0・負・数でない・空、資格情報と NONE（`starttls.enable` だけの組を含む））と、複数の問題を1つの一覧に集めること、表示名の既定 `MasterSmith`、`toString` に値が無いこと（NFR2.1・NFR2.5・NFR2.6・NFR6.2）
- [x] 環境変数の結び付きの確かめ: `SystemEnvironmentPropertySource`（名前 `systemEnvironment`、`SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT=1234` など）を置いた `ApplicationContextRunner` で、Spring Boot の自動設定が作る送信の部品の `getJavaMailProperties` に `mail.smtp.connectiontimeout=1234` が届くことを確かめる（NFR6.2、`logical-components.md` 10節）
  - **結び付かなかったとき**: 既定の 3 秒で動くことを確かめたうえで、この手順で生成を止め、8節の候補（推奨 A）を示して依頼者に諮る（NFR 設計の U1 R-01、9節の決定 4）。依頼者が決めた形で作り直してから次へ進む
- [x] 結合 `MailConfigurationIT`（組み込みの H2 の内部DB、コンテナは使わない）: 接続先が無い → `isConfigured` が偽・WARN なし・送信は NOT_CONFIGURED で接続しない（受け手の接続の数 0）、接続先が空白だけ・差出人が無い・資格情報と NONE・時間切れの不正な値・範囲の外のポート → 項目の名前だけの WARN が1件で値がログに無い、既定の設定で送信の部品のメールのセッションが debug でない、`org.eclipse.angus`・`jakarta.mail` のロガーが OFF、送信の部品があり受け手が止まっていても `/actuator/health` が 200 で UP、メールのヘルスチェックの Bean が無い、起動時の INFO（P2）が設定の状態と方式・テンプレートの件数だけを出し、接続先・差出人・資格情報の値を含まない（NFR2.2・NFR2.3・NFR2.5・NFR6.2・NFR6.4・NFR9.4、9節の決定 3）
- [x] 単位の単体・結合のコマンドを実行して通す

### Step 9: テンプレート（mail.template）— 実装

- [x] `MailTemplateCatalog`（本番の一覧。B1 は空）、`MailTemplateDefinition`（templateId の形: 英小文字で始まり英小文字・数字・ハイフンだけ、差し込みの名前: 英字で始まり英数字だけ）
- [x] `MailTemplateRegistry`（一覧と置き場を受け取って作る。本番の置き場は `classpath:mail/templates/`）: `ResourcePatternResolver` で `*.html` を数え上げ、`<templateId>_<language>.html` の名前と一覧を照らし、一覧の templateId ごとに ja・en を UTF-8 で読み、空の部分テンプレートの解決（`MapPartialResolver` の空）で `Mustache.compile` する。欠け・壊れ・名前の誤り・一覧に無い templateId は `TemplatePreparationException`（templateId・language・原因の種類だけ、部品の例外の文言を持たない）で Bean の作成を失敗させ、起動を止める。置き場が無い・0件でも一覧が空なら起動する。準備した `Template` は変わらない表に持つ
- [x] 描画: 依頼の language のテンプレートだけで描き、ほかの言語へ切り替えない。部品の描画の例外（`MustacheException` の仲間）は TEMPLATE_ERROR にする（BR4.4）
- [x] `RenderedMailInspector`: 描いた本文の最初の `title` 要素の文面を決まった形で取り出し、`HtmlUtils.htmlUnescape` で文字参照を戻し、空白・タブ・改行を1つの空白にまとめ、前後を除く。title が無い・空、`html` 要素の `lang` 属性が無い・依頼の language と違う → TEMPLATE_ERROR（BR4.2・BR4.3）
- [x] 起動の時に、準備したテンプレートの件数を INFO で1行出す（templateId・language の一覧と件数だけ。8節の P2、9節の決定 3）。設定の状態と方式の INFO は Step 7 の `MailConfig` で出す
- [x] 対応: US3.1（AC3.1.1・AC3.1.5・AC3.1.10）、BR2.1〜BR2.3・BR4.1〜BR4.4、NFR6.5、NFR8.1

### Step 10: テンプレート — テスト（単体）

- [x] テスト用のテンプレートを `backend/src/test/resources/mail/test-templates/` に置く（例 `sample_ja.html`・`sample_en.html`。先頭に Mustache のコメント `{{! ... }}` のライセンスヘッダー、本文の文面・二重引用符で囲んだ属性の値・title の3か所への差し込み、`html` の `lang`）。準備の失敗を作るテンプレートは場合ごとの置き場（例 `mail/test-templates-invalid/<場合>/`: en の欠け・Mustache の壊れ・名前の誤り・一覧に無い templateId）に置く
- [x] `MailTemplateRegistryTest`: ja・en の準備と描画、依頼の言語だけで描く、4つの原因の種類で失敗し例外に部品の文言・テンプレートの中身が無い、置き場が無い・空の一覧で作れる、同じ準備済みのテンプレートを複数のスレッドで描いて結果が崩れない（BR2.3・BR4.1、NFR6.5、NFR8.1）
- [x] `RenderedMailInspectorTest`: 文字参照を戻す、空白と改行のまとめ、前後の除去、title が無い・空・空白だけ → TEMPLATE_ERROR、lang が無い・違う → TEMPLATE_ERROR。性質ベースのテスト（jqwik）: 任意の title の文面から作った件名は CR/LF を含まず、前後に空白が無く、空白が続かない（BR4.2・BR4.3、NFR2.8）
- [x] `RenderedMailTest`: `toString` に件名・本文が無い（NFR1.2）
- [x] `MailTemplateLintTest`（本番の一覧と置き場、テスト用の一覧と置き場の両方を数え上げる）: `{{{`・`{{&`・`{{>`・`{{=` が無い、属性の値の差し込みがすべて二重引用符で囲まれている、テンプレートの中の差し込みの名前の集合が一覧と一致する、一覧の名前すべてに `<`・`>`・`&`・`"`・`'` を含む値を入れて描くと本文と件名のどこにもタグや属性として出ずエスケープされた形で出る、件名が空でない、本文と件名に `{{` の残りとライセンスヘッダーの文面が無い、lang が合う（BR2.4・BR2.6、AC3.1.4〜AC3.1.6、`security-design.md` 7節）。B1 の本番の置き場は0件で、B3 で招待のテンプレートが自動で対象になることをテストのコメントに書く
- [x] 単位の単体のコマンドを実行して通す

### Step 11: 送信（mail.transport）— 実装

- [x] `SmtpMailTransport`: `MimeMessageHelper`（マルチパートなし、UTF-8）で差出人（`Usable` の `InternetAddress`）・宛先1人・件名・HTML の本文（`setText(html, true)`）を入れる。ヘッダーを文字列で直接足す API（`addHeader` など）は使わない。`JavaMailSender.send` を1回だけ呼び、自動でやり直さない（BR5.1・BR5.2、NFR2.8・NFR6.3）
- [x] 失敗は `SendFailureClassifier` で分類し、分類できたものは失敗の種類で返す。想定外（`logical-components.md` 5.3 の4行目）のときだけ `MailUnexpectedException.of(e)` で包んで投げる。この部品が送信の失敗の例外を投げる唯一の地点（NFR 設計の U1 R-02）
- [x] `SendFailureClassifier`: 原因の連なりの型だけを見て、1 `SocketTimeoutException` → TIMEOUT、2 Spring の `MailAuthenticationException`・`AuthenticationFailedException`・`SendFailedException` → REJECTED、3 1・2 に当たらない `MailSendException` → CONNECTION_FAILED、4 そのほか → 想定外、を返す。文言は見ない。Angus Mail の型は使わない
- [x] 対応: US3.1（AC3.1.1・AC3.1.7）、BR5.1〜BR5.3、NFR2.8・NFR6.1・NFR6.3

### Step 12: 送信 — テスト（単体）

- [x] `SendFailureClassifierTest`: 表の4行を、組み立てた例外で確かめる（接続の時間切れを包んだもの、`getNextException` の奥の型、`getMessageExceptions` の中の型、文言に宛先を入れても分類が変わらないこと）（BR5.3、NFR9.2）
- [x] `SmtpMailTransportTest`: 送信の部品の `createMimeMessage` で組み立てた結果が `text/html; charset=UTF-8`、宛先が1人、件名と表示名が規格どおりに符号化されている（日本語）、表示名の既定 `MasterSmith`。送信の部品の `send` が1回だけ呼ばれ、想定外の例外が `MailUnexpectedException` に包まれ原因と文言を持たない（BR5.1・BR5.2、NFR6.3）。SMTP での実際の送信の確かめは Step 15 の結合テストで行う
- [x] 単位の単体のコマンドを実行して通す

### Step 13: 入口（mail.service）— 実装

- [x] `MailSender`（契約 C1）と `SmtpMailSender`: `MailSettings` が `Usable` でなければ確かめず・描かず・接続せずに NOT_CONFIGURED。そうでなければ一覧の引き当て → `MailRequestValidation` → `MailTemplateRegistry` → `RenderedMailInspector` → `SmtpMailTransport` の順に呼ぶ。`isConfigured` は `Usable` かだけを返す（BR1.7）
- [x] Observation `mastersmith.mail.send` を送信ごとに1つ作り、`send` の全体を覆う。タグは `mail.template`（一覧にある templateId、なければ `unknown`）・`mail.language`（`ja`・`en`、そうでなければ `unknown`）・`mail.outcome`（`sent`・`failed`）・`mail.failure.kind`（種類の小文字、成功は `none`）の4つだけ。想定外の失敗だけ `Observation.error` に包んだ例外を渡す（`logical-components.md` 6節、Q2 B）。`ObservationRegistry` はコンストラクター注入
- [x] ログは送信ごとに1件（成功は INFO、失敗は WARN）、キーと値で templateId・language（未知は `unknown`）・failureKind・exceptionType だけ。例外の物をロガーに渡さない（BR6.2、NFR1.1・NFR2.1）
- [x] 最後の守り: 確かめ・描画・件名の取り出しの中で包まれていない実行時の例外（U1 の不具合）が出たら、`MailUnexpectedException.of` で包んで投げる（文言と原因を出さないため）
- [x] 監査ログには書かない（BR6.4）。`@Transactional` を付けない（BR5.4、NFR5.1）
- [x] 対応: US3.1、契約 C1、BR1.7・BR5.4・BR6.1〜BR6.4、NFR1.1・NFR2.1・NFR5.1

### Step 14: 入口 — テスト（単体）

- [x] `SmtpMailSenderTest`（`SmtpMailTransport` と `MailTemplateRegistry` はテスト用の一覧と Mockito の差し替えで作る。実際の送信は Step 15）: NOT_CONFIGURED のとき描画と送信を呼ばない、確かめの失敗で描画と送信を呼ばない、TEMPLATE_ERROR で送信を呼ばない、送信の結果をそのまま返す、ログが1件でキーが4つだけ・未知の値は `unknown`、想定外の失敗で包んだ例外が投げられ文言に宛先が無い
- [x] Observation の確かめ（新しいテストの依存を足さない）: `ObservationRegistry.create()` に文脈を集める小さな `ObservationHandler` を付け、名前が `mastersmith.mail.send`、タグが4つの鍵だけ、宛先・差し込んだ値・件名が値に無い、未知の templateId・language が `unknown`、FAILED では error にならず想定外だけ error で包んだ例外が渡る（BR6.3、Q2 B、`security-design.md` 10節の差）
- [x] 単位の単体のコマンドを実行して通す

### Step 15: 境界の結合テスト（JVM の中の SubEtha SMTP）

- [x] テストの支え（`backend/src/test/java/cherry/mastersmith/mail/testsupport/`）:
  - SubEtha SMTP 7.2.2 の受け手（受けたメールを持つ仕組み）を空いている番号で起動・停止する部品。STARTTLS を受け付ける・必須にする・受け付けない、SMTPS、認証（テストの中で作る資格情報）、宛先の拒否を作れる。受けた接続の数を数える
  - `keytool`（`java.home` の `bin`）で、名前の合う証明書（`localhost`・`127.0.0.1`）と合わない証明書を、テストの JVM ごとに一時のディレクトリの PKCS12 に作り、終わったら消す部品。`keytool` が無い・失敗したら原因の分かる文言でテストを失敗させる（黙って飛ばさない。Q2 A）
  - 受け付けて何も返さない `ServerSocket` と、閉じた番号（開いてすぐ閉じた番号）
  - 送る側の信頼の設定はテストの設定の中だけで行う（候補: テストだけの `spring.ssl.bundle` と `spring.mail.ssl.bundle`。本番の設定では使わない。`security-design.md` 3節）。Spring を起動する結合テストでは、テスト用の `MailTemplateRegistry`（テスト用の一覧と置き場）に置き換える
- [x] `MailSendIT`: ja・en のテスト用のテンプレートで送り、受け手に届いたメールの宛先（`example.com`）・件名（title の文面）・本文（HTML）・`lang`・`Content-Type`（text/html、UTF-8）・差出人の表示名を確かめる（NFR9.1、AC3.1.1・AC3.1.10、B1 の見せ方）
- [x] `MailSendFailureIT`: 閉じた番号 → CONNECTION_FAILED、何も返さない `ServerSocket` → TIMEOUT（テストの設定で時間切れを短くする）、STARTTLS（必須）で受け手が STARTTLS を受け付けない → CONNECTION_FAILED でメールは0通、認証の拒否・宛先の拒否 → REJECTED、どの失敗でも受け手が受けた接続は1回だけ（NFR9.2・NFR6.1・NFR6.3、BR5.3）
- [x] `MailTlsIT`: STARTTLS（必須）と SMTPS の受け手へ送れる、名前の一致しない証明書の受け手 → CONNECTION_FAILED（NFR2.7）
- [x] `MailHeaderInjectionIT`: 宛先・差し込む値の CR/LF → INVALID_INPUT で受け手のメールは0通、title に改行を含むテンプレート → 件名は1行にまとまりヘッダーは増えない（NFR2.8、AC3.1.7）
- [x] `MailSecretLeakIT`（既存の `*SecretLeakIT` と同じ形、`LogEvents` を使う）: 成功と、拒む・応答しない・拒否の応答・入力の誤りのそれぞれで、宛先・差し込んだ値・資格情報・SMTP の応答の文面がアプリのログに出ない。`cherry.mastersmith` のロガーを TRACE にしてメソッドの追跡を有効にしても、TRACE のログに差し込んだ値・本文・件名が出ない（NFR1.1・NFR2.1、Q1 A、`team.md` のメールの必須のテスト）
- [x] 単位の結合のコマンドを実行して通す。コンテナの実行環境が無くても飛ばされない（コンテナを使わない）ことを確かめる

### Step 16: 構造の検査

- [x] `backend/src/test/java/cherry/mastersmith/mail/MailBoundaryArchitectureTest.java`（既存の `DslBoundaryArchitectureTest` と同じ形）:
  - `mail` はほかの機能（`user`・`auth`・`audit`・`access`・`dsl`・`dslmanage`・`targetdb` と、これから足す `invitation` など）と `web`・`repository` の層に依存しない（`common` は使ってよい）
  - `mail` の下は `mail.service`・`mail.domain`・`mail.config`・`mail.template`・`mail.transport` だけ
  - `mail` の外から使えるのは `mail.service` と `mail.domain` だけ
  - `mail` の中で `@Transactional` を使わない
  - `cherry.mustache` を使うのは `mail.template` だけ、`jakarta.mail`・`org.springframework.mail` を使うのは `mail.transport` と `mail.config` だけ
- [x] 既存の `ArchitectureTest` と既存の機能ごとの境界テストを変えずに通す（緩めない）
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.mail.*' --tests 'cherry.mastersmith.ArchitectureTest'` を実行して通す
- [x] 対応: NFR5.1、`logical-components.md` 3節、`team.md` の Code Style（層の境界）

### Step 17: 静的解析の関門（SpotBugs）

- [x] `backend/build.gradle.kts` の `spotbugsGate` に、`PREDICTABLE_RANDOM` と `SMTP_HEADER_INJECTION` の指摘を priority にかかわらず失敗にする判定を足す（既存の `SQL_` と同じ形。説明文も直す）
- [x] 既存の `PREDICTABLE_RANDOM` の1件（`backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java` の `ThreadLocalRandom` によるダミーの行の選び方、今は priority 2）が誤検知かを、その値の使われ方（秘密・推測されて困る値に使われていないか）で確かめる。誤検知なら `backend/config/spotbugs-exclude.xml` に理由を書いて、その1か所だけを外す。そうでなければ `SecureRandom` などに直す。判断と根拠を `code-summary.md` に記録する
- [x] `mail` のコードの指摘を確かめる。件名を設定する箇所が `SMTP_HEADER_INJECTION` で指摘されたら、Step 15 の `MailHeaderInjectionIT` で改行が入らないことを確かめたうえで、理由を書いてその1か所だけを除外の設定に入れる
- [x] `./gradlew :backend:spotbugsGate` を実行して通す
- [x] 対応: NFR2.8・NFR2.9、`team.md` の Code Style、B1 の完了の条件（SpotBugs の関門と既存の1件）

### Step 18: メールのテンプレートのライセンスヘッダーの検査

- [x] `config/license-header.txt` から、Mustache のコメント `{{! ... }}` の形のヘッダーを作る（HTML のコメントは使わない。BR2.5）
- [x] `backend/build.gradle.kts` の Spotless に `format`（例 `mailTemplates`）を足し、対象を `src/main/resources/mail/templates/*.html` と `src/test/resources/mail/test-templates/**/*.html`・`src/test/resources/mail/test-templates-invalid/**/*.html` とし、区切りをヘッダーの後の最初の行（`<!DOCTYPE`）にする。verify の段 1 の `spotlessCheck` で確かめる
- [x] ヘッダーの無いテンプレートで `spotlessCheck` が失敗することを一度確かめ（ファイルは残さない）、結果を記録する
- [x] Spotless でこの形を作れないと分かったときは、同じ確かめをする小さな Gradle のタスクに替えて段 3（`verifyLicense`）に入れ、どちらにしたかを記録する（`cicd-pipeline.md` 3節）
- [x] ルートの `build.gradle.kts` の `verifyLicense` の段の説明文に、メールのテンプレートを加える
- [x] 対応: BR2.5、AC3.1.5、`team.md` の Code Style、B1 の完了の条件（テンプレートのライセンスヘッダーの検査）

### Step 19: サブモジュールを変えていないことの検査（設計に無い追加。8節の P3、9節の決定 3）

- [x] ルートの `build.gradle.kts` に、`vendor/java-mustache-processor` の追跡されるファイルが変わっていないことを `git status --porcelain` で確かめるタスクを足し、verify の段 0（`verifyPrepare`）に入れる（既存の `vendorUnchanged` と同じ形。make-you-chic-ui の npm のビルドには依存させない）
- [x] 対応: `project.md` の Forbidden（`vendor/java-mustache-processor` の中身を変えない）

### Step 20: 手元の受け手（Mailpit、compose の profile `mail`）と設定の見本

- [x] `compose.yaml` に `mailpit`（profile `mail`、イメージ `axllent/mailpit:v1.31.2@sha256:74d609a42ec279aa63c6b4622a6fa9b5408d1ad5b1d76a1c4be40a265ce0863d`、ポート `127.0.0.1:8025:8025` と `127.0.0.1:1025:1025`、ボリュームなし、`mem_limit: 256m`、`restart: "no"`、ログは `json-file` の 10m・3つ）を理由のコメントつきで足す（NFR11.1、基盤の設計の D1）。`app` の定義は変えない（`.env` を `env_file` で読むため）
- [x] `.env.example` に「メール（U1）」の節をすべてコメントの形で足す: `SPRING_MAIL_HOST`・`SPRING_MAIL_PORT`（例は手元の受け手 `mailpit`・`1025` だけ）、`SPRING_MAIL_PROTOCOL` と STARTTLS（必須）・SMTPS の書き方の案内、`SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD`（秘密、値は空）、時間切れの3つ（既定 3000 ミリ秒、1 以上の整数）、`MASTERSMITH_MAIL_FROM`・`MASTERSMITH_MAIL_FROM_NAME`。空の `SPRING_MAIL_HOST=` の行は置かない（NFR2.5・NFR11.2）
- [x] `docker compose --profile mail config` で定義が正しいことを確かめる
- [x] `docker compose --profile mail up -d mailpit` で起動し、画面（`http://127.0.0.1:8025`）と API が開くこと、1025 と 8025 が `127.0.0.1` だけで待ち受けていることを確かめる。Mailpit の保持の上限（受けたメールを持つ件数の既定）を、実際のイメージ（起動の引数の案内・API の情報）で確かめて記録する（基盤の設計の N8）
- [x] Mailpit を指す設定（`SPRING_MAIL_HOST=localhost`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM`）でアプリ（WAR、使い捨ての内部DB と仮の署名鍵）を起動し、設定の点検の WARN が出ず、起動時の INFO（8節の P2）で設定の状態が「ある」・方式が NONE になる（`isConfigured` が真）ことを確かめる。配備したアプリの `.env` は変えない。招待のメールを Mailpit の画面で見る確かめは B3 に回す（NFR11.1 との差、9節の決定 2）
- [x] `docker compose stop mailpit` と `docker compose rm -f mailpit` で止めて消す
- [x] 対応: NFR11.1・NFR11.2、B1 の完了の条件（受け手を compose の profile で起動できる）、`logical-components.md` 9節

### Step 21: E2E（`./gradlew e2eTest`）への設定と前提の確かめ

- [x] `frontend/playwright.config.ts` の `webServer.env` に `SPRING_MAIL_HOST=localhost`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM`（`example.com` の宛先）を足す（暗号化 NONE・資格情報なし）
- [x] ルートの `build.gradle.kts` の `e2eTest` が始める前に `127.0.0.1:8025` の Mailpit の API に届くかを確かめ、届かなければ起動の手順（`docker compose --profile mail up -d mailpit`）を示して失敗させる（黙って飛ばさない。既存の `requireTool` と同じ考え方）。`e2eTest` は Mailpit を起動・停止しない。確かめに使う API の道は実際のイメージで確かめてから決める
- [x] Mailpit を止めた状態で `./gradlew e2eTest` が手順を示して失敗すること、起動した状態で既存の E2E（010〜040）が通ることを確かめる（既存の E2E はメールを送らない）
- [x] 対応: 基盤の設計の Q1 A、NFR11.1、`team.md` の Testing Posture（E2E）

### Step 22: Dependabot

- [x] `.github/dependabot.yml` に `docker-compose` の項目（`directory: /`、週ごと）を足し、Mailpit と既存の compose のイメージの知らせを受けることと、受け方（`team.md` の Way of Working）をコメントに書く。新しいサブモジュールは対象にしない（既存のコメントどおり）
- [x] 実際に知らせの対象になったかは、依頼者のプッシュの後に GitHub の Dependabot の画面で確かめる（10節に引き継ぐ）
- [x] 対応: 基盤の設計の Q3 A（D3）

### Step 23: README

- [x] 「取得と準備」に、サブモジュールが2つになったこと（`vendor/java-mustache-processor` を含む）と、サブモジュールが無いと Gradle の構成が失敗することを書く。`vendor/java-mustache-processor` の中身を変えないこと、固定先の更新の決まりを書く
- [x] 「メール（U1）」の節を足す（B1 の完了の条件。NFR2.4、承認の場の NFR 要件 R-01）:
  - 設定の項目（接続先・ポート・資格情報・差出人・表示名・時間切れ）と、接続先の設定が無ければ送らないこと
  - 暗号化の方式ごとの書き方（指定なし → NONE、`mail.smtp.starttls.enable` と `required` を true → STARTTLS（必須）、`protocol: smtps` か `ssl.enabled: true` → SMTPS）と、STARTTLS ではポート 587 を書くこと
  - `starttls.enable` だけの設定では、受け手が STARTTLS を受け付けないとメールが平文で届き、本文の招待の URL（トークン）が守られないこと。配備先が決まったら STARTTLS（必須）か SMTPS を使うこと
  - 運用で設定しない値の一覧（`mail.debug`、`mail.smtp.ssl.trust`・`mail.smtps.ssl.trust`、`checkserveridentity` の false、1.2 より古い TLS、STARTTLS の `required` の false、`spring.mail.test-connection` の true、`management.health.mail.enabled` の true、メールの部品のロガーの水準）
  - 資格情報と暗号化 NONE の組は送らない（設定がない扱い）こと、設定の不正は項目の名前だけの WARN になること、数でないポートは起動が止まること
  - Mailpit の profile の起動・止め方・消し方、ボリュームが無く消すと受けたメールも消えること、保持の上限の値（Step 20 で確かめた値）、E2E の前に起動すること
  - 配備先が決まるまで実在の宛先・外部の SMTP へ送らないこと
- [x] 「環境変数」の表に `SPRING_MAIL_*`・`MASTERSMITH_MAIL_*` を足す
- [x] 「ビルドした WAR での画面の確認（E2E）」に、Mailpit の起動が前提になったことを書く
- [x] 「1コマンドの検査」に、メールのテストはコンテナを使わず、コンテナの実行環境が無くても飛ばされないことを書く
- [x] 「ライセンス」の節に、Jakarta Mail・Angus Mail（EDL 1.0 を選ぶ理由）、java-mustache-processor（Apache 2.0）、SubEtha SMTP（Apache 2.0、テストだけ）、Mailpit（MIT、手元の確かめだけ）を足す
- [x] 対応: NFR2.4・NFR11.1・NFR11.2、`tech-stack-decisions.md` 1.2、B1 の完了の条件

### Step 24: WAR の確かめ（ADR-010 の (c) と、WAR の中の数え上げ）

- [x] `./gradlew :backend:bootWar` で作った WAR の `WEB-INF/lib` に `cherry-mustache-core` の JAR が入っていることを確かめる（NFR8.3 の (c)）
- [x] WAR を使い捨ての内部DB と仮の署名鍵で起動し、本番の一覧が空・置き場が0件でも起動することを確かめる
- [x] WAR を起動したときの INFO（8節の P2）で、準備したテンプレートの件数が 0 であることを確かめる
- [x] WAR の中の `mail/templates/*.html` の数え上げの確かめ（8節の P1、9節の決定 3）: 作業用の場所（スクラッチ）に写した WAR の `WEB-INF/classes/mail/templates/` に一覧に無い名前のテンプレートを1つ入れて起動し、UNKNOWN_TEMPLATE で起動が止まる（数え上げでファイルが見えた）ことを確かめる。写しは確かめの後に消し、リポジトリには入れない
- [x] 数え上げが WAR の中で動かないと分かったときは、数え上げをやめ、一覧からファイル名を組み立てて直接読む形に替え、「一覧に無いファイル」の検査はテスト（`MailTemplateLintTest`）で行う（`logical-components.md` 10節）
- [x] 対応: NFR8.3 の (c)、BR2.1〜BR2.3

### Step 25: 取り込みの5条件の記録（ADR-010）

- [x] 作業用の場所に `git clone --recurse-submodules` でこのブランチを取り出し（CI の `actions/checkout` の `submodules: true` と同じく固定先で取得）、`./gradlew :backend:compileJava` が通ることを確かめる（NFR8.3 の (d) の手元での確かめ。CI での確かめは 10節に引き継ぐ）
- [x] (a)〜(e) の結果（(a) verify と取得元、(b) lockfile と OSV-Scanner、(c) WAR、(d) 固定先での取得、(e) プラグインと Gradle 9.7.1）を `code-summary.md` に表で記録する
- [x] 1つでも満たせなければ、ADR-010 のとおり Maven Central への公開に切り替える判断を記録し、依頼者に諮る（部品のリポジトリの変更は部品のリポジトリ側で行う）
- [x] 対応: NFR8.3、ADR-010、承認の場の NFR 要件 R-02、B1 の完了の条件（取り込み）

### Step 26: 1コマンドの検査（統合の前の関門）

- [x] colima が動いていることを確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行し、すべての段が通ることを確かめる（`project.md` の学び）。対象DB のテストが SKIPPED になっていないことを確かめる
- [x] テストの件数（単体・結合）と、全体のカバレッジ、`mail` の5つの下位パッケージごとの行・分岐のカバレッジを実測の数字で記録し、Step 1 の基準と比べる（既存のテストが減っていない・失敗していない）。パッケージごとの下限（行 80%・分岐 70%）を下回ったらテストを足す。除外は足さない（NFR9.5、`team.md` の Testing Posture）
- [x] OSV-Scanner の結果に新しい依存（Jakarta Mail・Angus Mail・SubEtha SMTP の推移依存）の High 以上が無いことを確かめる
- [x] 対応: B1 の共通の完了の条件、NFR9.5、`project.md` の Mandated（統合の前の確認）

### Step 27: 記録とコミットの提案

- [x] `code-summary.md`（作ったもの、上流との差、Step 1 と Step 26 の実測、5条件、環境変数の結び付き、`PREDICTABLE_RANDOM` の判断、Mailpit の保持の上限、lockfile の差、ライセンス）、`source-manifest.json`、`traceability.json` を作る（コード生成の段の手順）
- [ ] 3節の C1〜C7 の区切りで、コミットの内容とメッセージの案を依頼者に示して承認を得る（生成の担当はコミットしない）
- [ ] 対応: 段の記録、`project.md` の Change Control

## 6. ストーリー・要件と手順の対応

| ストーリー・要件 | 受け入れ基準・決まり・NFR | 手順 |
|---|---|---|
| US3.1 自分の言語で招待メールを受け取る（主） | AC3.1.1（HTML・UTF-8・件名は title） | Step 9〜15 |
| US3.1 | AC3.1.4（エスケープ）・AC3.1.5（すべてのテンプレートを描ける・件名が空でない・ヘッダーと `{{` の残りが無い）・AC3.1.6（エスケープしない差し込みが無い・属性は二重引用符） | Step 10・18 |
| US3.1 | AC3.1.7（改行でヘッダーが増えない・0通） | Step 5・6・11・15 |
| US3.1 | AC3.1.10（`lang`） | Step 9・10・15 |
| US3.1 | AC3.1.2・AC3.1.3・AC3.1.8・AC3.1.9（招待のテンプレートの中身と URL） | Deferred（U3、B3） |
| US1.1・US2.2（関わる） | 送信の失敗の種類の返し方・漏らさない決まり（主の単位は U3） | Step 11〜15 |
| 設定 | BR1.1〜BR1.8、NFR2.3〜NFR2.6・NFR6.2・NFR6.4 | Step 3・7・8 |
| テンプレート | BR2.1〜BR2.6、BR4.1〜BR4.4、NFR8.1・NFR6.5 | Step 9・10・18・24 |
| 依頼の確かめ | BR3.1〜BR3.4、NFR2.8 | Step 5・6・15 |
| 送信 | BR5.1〜BR5.4、NFR5.1・NFR6.1・NFR6.3 | Step 11〜16 |
| 秘密と記録 | BR6.1〜BR6.4、NFR1.1・NFR1.2・NFR2.1・NFR2.2 | Step 3・5・13〜15 |
| 取り込み | NFR8.2・NFR8.3、ADR-010 | Step 1・2・24・25 |
| テスト | NFR9.1〜NFR9.5 | Step 4〜16・26 |
| 静的解析 | NFR2.8・NFR2.9 | Step 17 |
| 手元の確かめ | NFR11.1・NFR11.2 | Step 20・21・23 |
| B1 の完了の条件 | 取り込み・描画と送信の確かめ・SpotBugs の関門・ヘッダーの検査・受け手の profile | Step 2・15・17・18・20・25・26 |

## 7. テストの量（Standard）

部品ごとに 5〜8 件の単体テストと、境界の結合テストを置く。

| 部品 | 単体 | 結合 |
|---|---|---|
| `mail.domain`（値の型・依頼の確かめ・例外） | `MailRequestValidationTest` 8 件＋性質ベース 3 件、値の型と例外で 6〜8 件 | — |
| `mail.config`（方式の分類・点検） | `EncryptionModeTest` 6〜7 件、`MailSettingsTest` 8 件（点検の表の9行をパラメーターで） | `MailConfigurationIT` 6〜8 件 |
| `mail.template`（準備・描画・件名と lang・検査） | `MailTemplateRegistryTest` 7〜8 件、`RenderedMailInspectorTest` 6〜8 件（性質ベースを含む）、`MailTemplateLintTest` 6〜8 件 | — |
| `mail.transport`（組み立て・分類） | `SendFailureClassifierTest` 6〜8 件、`SmtpMailTransportTest` 5〜6 件 | `MailSendIT` 3〜4 件、`MailSendFailureIT` 5〜6 件、`MailTlsIT` 3 件、`MailHeaderInjectionIT` 3 件 |
| `mail.service`（入口・ログ・Observation） | `SmtpMailSenderTest` 6〜8 件（Observation を含む） | `MailSecretLeakIT` 5〜6 件 |
| 構造の検査 | `MailBoundaryArchitectureTest` 5 件 | — |

## 8. この計画で決めたこと・承認済みの文書との差

承認済みの文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この計画での扱い | 理由 |
|---|---|---|---|
| 想定外の例外を投げる場所（NFR 設計の U1 R-02） | 「U1 が包んで投げる」とだけ書かれていた | 組み立ては `MailUnexpectedException.of` の1つ、送信の失敗の判定は `SendFailureClassifier`（投げない）、投げるのは `SmtpMailTransport`。`SmtpMailSender` は包まれていない実行時の例外を同じ `of` で包む最後の守りだけ | 承認の場の決定。組み立ての規則（文言と原因を持たない）を1か所に閉じる |
| 一覧の引き当て | `MailRequestValidation`（`mail.domain`）が templateId を確かめる | 一覧の引き当ては `mail.service`、確かめの関数には「一覧に有るか」と期待する名前の集合を渡す | `mail.domain` を `mail.template` に依存させない（境界の決まり）。確かめの順序と結果は同じ |
| テスト用のテンプレートの置き場 | `backend/src/test/resources/mail/test-templates/` | 加えて、準備の失敗の場合ごとの置き場 `mail/test-templates-invalid/<場合>/` を置き、ヘッダーの検査の対象にも入れる | 失敗の4種類を別々の置き場で作るため |
| テストの信頼の設定 | 「テストの設定の中だけで信頼させる」 | 候補はテストだけの `spring.ssl.bundle` と `spring.mail.ssl.bundle`。働かなければ、テストの設定で送る側の部品の設定に信頼の仕組みを渡す形にする | 本番の設定では bundle を使わない決まり（`security-design.md` 3節）はそのまま |
| P1: WAR の中の数え上げの確かめ方（依頼者が取り入れた。9節の決定 3） | 「手元で WAR を起動し、テンプレートの数をログの件数などで確かめる」 | B1 は本番の一覧も置き場も空のため、写した WAR に一覧に無い名前のファイルを1つ入れて起動が止まることで確かめる（Step 24）。招待のテンプレートでの確かめは B3 でもう一度行う | 0件では数え上げが働いたかを見分けられない |
| P2: 起動時の INFO（設計に無い追加。依頼者が取り入れた。9節の決定 3） | 設計に無い | 起動の時に、設定の状態（設定がない・不正・ある）と方式、準備したテンプレートの件数（templateId・language の一覧）を INFO で1行ずつ出す。値（接続先・差出人・資格情報）は出さない | B1 の確かめ（Mailpit を指す設定で `isConfigured` が真、WAR の中のテンプレートの件数）を、配備したアプリでも見られるようにするため |
| P3: サブモジュールの変更の検査（設計に無い追加。依頼者が取り入れた。9節の決定 3） | 設計に無い | `vendor/java-mustache-processor` の追跡されるファイルが変わっていないことを verify の段 0 で確かめる（Step 19） | `project.md` の Forbidden を make-you-chic-ui と同じく機械で守るため |
| NFR11.1 の確かめ方 | 招待のメールが受け手の画面に出ることを B1 の完了の条件とする | B1 では profile の起動と、Mailpit を指す設定で `isConfigured` が真になることまで。画面での確かめは B3 に引き継ぐ（依頼者が決めた。9節の決定 2） | B1 の時点で招待のテンプレートが無い（`logical-components.md` 9節・11節で「B1 の計画で依頼者に確かめる」とされた） |

**環境変数が点を含む鍵に結び付かなかったときの代わりの口の候補**（NFR 設計の U1 R-01。Step 8 で結び付かないと分かったら生成を止めて依頼者に諮る。9節の決定 4）:

| 候補 | 形 | 良い点 | 悪い点 |
|---|---|---|---|
| A（推奨） | `application.yaml` の `spring.mail.properties.mail.smtp.connectiontimeout: ${MASTERSMITH_MAIL_CONNECTION_TIMEOUT:3000}` のように、値だけを点を含まない U1 の環境変数で受ける（6つの鍵を3つの環境変数で受ける） | 設定の鍵は Spring Boot のまま、U1 は部品の設定を書き換えない（`security-design.md` 2.1 と合う）。点検の規則も変わらない | 環境変数の名前が `SPRING_MAIL_*` と `MASTERSMITH_MAIL_*` の2系統になる |
| B | `SPRING_APPLICATION_JSON` で `{"spring.mail.properties.mail.smtp.connectiontimeout":"5000"}` を渡す | コードも設定も変えない | `.env` に JSON を書く必要があり、誤りに気づきにくい |
| C | U1 の設定の型に時間切れの項目（`Duration`）を持ち、起動時に送信の部品の設定へ書き込む | 型で値を確かめられる | U1 が部品の設定を書き換えない、という NFR 設計の形と違う |

## 9. 依頼者の決定

計画の承認の前に諮った論点について、依頼者が次のとおり決めた。計画の各 Step と8節はこの決定に合わせてある。

1. **git の操作の順**: 案のとおり。Step 1 でブランチ `feature/260925-user-management-b1` を `develop` から作り、サブモジュール `vendor/java-mustache-processor` をタグ `0.1.0`（コミット `8d44c36`）で追加する。コミットは生成の後に、依頼者の承認を得て C1〜C7 でまとめて行う（3節、Step 1・Step 27）。
2. **NFR11.1 との差**: 招待のメールを Mailpit の画面で見る確かめは B3 に回す。B1 は profile `mail` の起動と、Mailpit を指す設定で `isConfigured` が真になることまでとする。NFR11.1 との差として8節と `code-summary.md` に記録する（Step 20、Build and Test に引き継ぐこと）。
3. **P1・P2・P3 はすべて取り入れる**: 設計に無い追加として8節に記録する。
   - P1: 写した WAR に一覧に無い名前のテンプレートを入れ、UNKNOWN_TEMPLATE で起動が止まることで、WAR の中の数え上げを確かめる（Step 24）
   - P2: 起動時の INFO で、設定の状態・方式・準備したテンプレートの件数を出す。値（接続先・差出人・資格情報）は出さない（Step 7・9・20・24）
   - P3: verify の段 0 で `vendor/java-mustache-processor` の追跡されるファイルが変わっていないことを確かめる（Step 19）
4. **環境変数の結び付き**: 点を含む鍵が環境変数から結び付かないと分かったら、Step 8 で生成を止めて依頼者に諮る。候補と推奨（A）は8節のとおり。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:591505487a7fb6a8983ed2b2b5ed40524251e26ce603ffd5e0b713cb57a870cd",
  "contract_sha256": "sha256:b0e0f1eed9e80e43e2a774e6087e20f8a66c419412203955de70d736ee76c67f"
}
```

Testing Contract の `plan_profile.steps` との対応: 骨組みと本番の設定は Step 1〜3、テストの実行の準備は Step 4（最初のテストの Step 6 より前）、データの形（値の型と確かめ）は Step 5・6、データの持ち方に当たる設定は Step 7・8、業務処理（描画・送信・入口）は Step 9〜14、境界の結合テストと構造の検査は Step 15・16、環境とビルドの設定は Step 17〜22・24〜26、文書と記録は Step 23・27。U1 は内部DB の表・DB アクセス・API・画面を持たないため、その層の手順は無い。

## Build and Test に引き継ぐこと

| 項目 | 引き継ぐ内容 | 持ち主 |
|---|---|---|
| verify の時間 | SubEtha SMTP の結合テストを足した後の `./gradlew verify` の時間を測り、前の Intent の実測と比べる | Build and Test |
| カバレッジの実測 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify で、全体と `mail` の5つの下位パッケージの値をもう一度実測して記録する | Build and Test |
| 取り込みの (d) | 依頼者のプッシュの後、CI（`submodules: true`）が固定先の `vendor/java-mustache-processor` を取得して `./gradlew verify` が通ることを確かめる。通らなければ ADR-010 の切り替えを検討する | Build and Test（CI の結果の確かめ） |
| Dependabot の `docker-compose` | プッシュの後、GitHub の Dependabot の画面で compose のイメージ（Mailpit ほか）が知らせの対象になったかを確かめて記録する。対象にならなければ README に手で見直す手順を書く | Build and Test |
| 環境変数の結び付き | Step 8 の結果（結び付いたか、代わりの口にしたか）を前提として、`.env.example` と README の書き方が合っていることを確かめる | Build and Test |
| E2E の前提 | Mailpit を止めた・起動した状態の `./gradlew e2eTest` の結果を確かめる（画面・認証に関わる変更ではないが、`e2eTest` の前提が変わったため） | Build and Test |
| 招待のメールを画面で見る確かめ | Mailpit の画面で招待のメールを見る確かめ（NFR11.1）と、WAR の中の招待のテンプレートの数え上げのもう一度の確かめは、招待のテンプレートを足す B3 に引き継ぐ | B3（U3 のコード生成と Build and Test） |
| 契約 C10 の文言 | 「このリンクは 24 時間有効です」（固定）と `validityHours` の差し込みの差の反映（機能設計の承認の場の G6）は B3 の計画で確かめる | B3（U3 のコード生成の計画） |
| `MailSendResult` の形 | U3 の設計の断片の形を U1 の実装（outcome と failureKind の record）に合わせる（NFR 設計の承認の場の U3 R-01） | B3（U3 のコード生成） |
| 応答時間 | 招待・送り直しの API の 95 パーセンタイル 5 秒（NFR6）は API を持つ U3 で確かめる。U1 はその中に収まる時間切れ（3 秒）を持つ | B3（U3 の Build and Test） |
| 送信の指標 | Observation `mastersmith.mail.send` の指標とタグの名前を、実際に起動して確かめてからダッシュボードと警報を書く（基盤の設計の N6） | observability-setup |
| 残る危険 | `spring.mail.properties.*` の口から守りを弱められる・`starttls.enable` だけで平文になりうる・部品のロガーを上げられる、は README の運用の決まりで扱う（受け入れ済み） | 受け入れ済み（記録だけ） |
