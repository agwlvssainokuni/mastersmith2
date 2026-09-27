# CI/CD Pipeline — U1 メールの描画と送信（u1-mail）

U1 の検査の流れ（CI と1コマンドの検査）、手元でメールを見る受け手（Mailpit）、E2E への設定の渡し方、B1 で確かめることを示す。U1 は library の単位で、API・内部DB・独自の配備の単位を持たないため、配備の流れ（Dockerfile・イメージ・戻しの手順）は変えない。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作らない（`aidlc/spaces/default/memory/project.md` の Deployment）。

- 答え: `infrastructure-design-questions.md`（要点 17 件、Q1 A: 1025 を `127.0.0.1` だけに公開し `e2eTest` が前提を確かめる、Q2 A: テストの証明書は実行のときに `keytool` で作る、Q3 A: Dependabot に `docker-compose` を足す、まとめの確認は Looks correct）
- 上流: `construction/u1-mail/nfr-design/logical-components.md`・`security-design.md`、`construction/u1-mail/nfr-requirements/tech-stack-decisions.md`・`security-requirements.md`、`construction/u1-mail/functional-design/functional-spec.md`、`inception/domain-design/components.md`・`decisions.md`（ADR-002・ADR-009・ADR-010）、`inception/contract-design/contract-summary.md`（C1・C10）（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）
- 既にある仕組み（正とする）: `.github/workflows/ci.yml`・`.github/dependabot.yml`・`settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`compose.yaml`・`.env.example`・`.gitignore`・`.gitleaks.toml`・`frontend/playwright.config.ts`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えない。

| 項目 | 今の形 | U1 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| サブモジュール | `actions/checkout` の `submodules: true` で固定先のコミットを取得（再帰しない） | 新しい `vendor/java-mustache-processor` も同じ設定で固定先を取得する（NFR8.3 の (d)）。部品のリポジトリの中にサブモジュールが無いことを B1 で確かめる |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。SubEtha SMTP の結合テストは JVM の中で動き、コンテナも外部の通信も要らない |
| 秘密 | CI は秘密を使わない | SMTP の接続先・資格情報を CI に渡さない。テストは JVM の中の受け手だけに送る |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない（WAR に `cherry-mustache-core` の JAR が入ることを B1 で確かめる） |

時間切れはテストの設定で短い値にするため、CI の時間への影響は小さい見込み。`verify` の時間は Build and Test で測る。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段は増やさず、既存の段に次を足す。どれか1つでも失敗したら全体を失敗とする（既存のとおり）。

| 段 | 足すもの | 関門（失敗の条件） | 場所 |
|---|---|---|---|
| 0 準備 | サブモジュールの取得を前提にする（無ければ composite build の解決で失敗する） | `vendor/java-mustache-processor` が無い | `settings.gradle.kts` |
| 1 フォーマット | メールのテンプレートのライセンスヘッダーの検査（3節） | ヘッダーが無い・形が違う | `backend/build.gradle.kts` の Spotless |
| 3 ライセンスヘッダー | 段の説明文にメールのテンプレートを加える（検査そのものは段 1 の Spotless） | 同上 | `build.gradle.kts` |
| 4 ビルド | `spring-boot-starter-mail`・`cherry-mustache-core`（composite build）・SubEtha SMTP 7.2.2（テスト）を依存に足す | コンパイルの失敗、取得元が Maven Central の外になる | `backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile` |
| 5 単体テスト | 依頼の確かめ（jqwik）・失敗の分類の表・テンプレートの検査（`{{{`・`{{&`・`{{>`・`{{=`）・Observation のタグ・`toString`・`MailBoundaryArchitectureTest` | 1件でも失敗 | `backend/src/test/java/cherry/mastersmith/mail/` |
| 6 結合テスト | SubEtha SMTP の `XxxIT`（成功・閉じたポート・何も返さない `ServerSocket`・STARTTLS を受け付けない・認証と宛先の拒否・名前の一致しない証明書・STARTTLS と SMTPS で送れる・漏えい・TRACE・ヘルスチェック・既定の debug） | 1件でも失敗。コンテナを使わないため、コンテナの実行環境が無いときも飛ばさない | 同上 |
| 7 カバレッジ | 新しいパッケージ `mail` の5つの下位パッケージ（`service`・`domain`・`config`・`template`・`transport`） | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | `backend/build.gradle.kts` の JaCoCo |
| 8 安全の検査 | `spotbugsGate` に `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を足す | その2つの指摘は priority にかかわらず失敗。誤検知は理由を書いて `backend/config/spotbugs-exclude.xml` で外す | `backend/build.gradle.kts` |
| 8 安全の検査 | OSV-Scanner・Gitleaks は既存のまま（4節・5節） | 既存の基準（重大度 High 以上） | `build.gradle.kts` |
| 9 成果物 | 既存の `bootWar` | WAR が作れない | 既存 |

補足:

- 既存の `PREDICTABLE_RANDOM` の1件（`auth/repository/LoginAttemptStateRepository`）は、関門に入れる B1 で誤検知かを確かめ、誤検知なら理由を書いて除外の設定で外し、そうでなければ直す（NFR2.9）。
- パッケージごとのカバレッジの値は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する（`project.md` の学び）。

## 3. メールのテンプレートのライセンスヘッダーの検査

- 対象: `backend/src/main/resources/mail/templates/*.html` と、テスト用の `backend/src/test/resources/mail/test-templates/*.html`。B1 の時点で本番の置き場は空でも、テスト用のテンプレートがあるため検査は空振りしない。
- 形: 既存の `config/license-header.txt` から、送るメールに出ない Mustache のコメント `{{! ... }}` の形のヘッダーを作る（HTML のコメントは使わない。BR2.5）。区切りはヘッダーの後の最初の行（`<!DOCTYPE`）。
- 作り: `backend/build.gradle.kts` の Spotless に `format`（例 `mailTemplates`）を足し、段 1 の `spotlessCheck` で確かめる。Spotless でこの形を作れないと分かったときは、同じ確かめをする小さな Gradle のタスクに替えて段 3 に入れ、どちらにしたかを B1 で記録する。
- 描いた本文にヘッダーの文面が出ないことは BR2.6 の単体テストで確かめる。

## 4. サブモジュールと composite build、lockfile

| 項目 | 作り | 出典 |
|---|---|---|
| サブモジュール | `.gitmodules` に `vendor/java-mustache-processor` を足し、タグ `0.1.0`（コミット `8d44c36`）に固定する。承認を得た専用のコミットで行い、前後のハッシュを記録する。中身はこのリポジトリから変えない | NFR8.2、`project.md` の Mandated・Forbidden |
| 統合の形 | 固定先の更新を含むため、短命のブランチから `develop` へ fast-forward で統合してよい（ほかの変更は squash）。どちらでも統合の前に `./gradlew verify` を通す | `team.md` の Way of Working |
| composite build | ルートの `settings.gradle.kts` に `includeBuild("vendor/java-mustache-processor")` を足す。`dependencyResolutionManagement` の Maven Central だけの決まり（`FAIL_ON_PROJECT_REPOS`）は変えない | NFR8.3 の (a)、`team.md` の Code Style |
| ビルドのプラグイン | 部品側のプラグイン（`org.owasp.dependencycheck`・`com.gradleup.shadow`）は Gradle Plugin Portal から取る。WAR には入らない | `project.md` の Corrections（F4 A） |
| lockfile | 新しい依存の推移依存（`jakarta.mail-api`・`angus-mail`、SubEtha SMTP の `jsr305`・`guava-mini` など）を `resolveAndLockAll --write-locks` で `backend/gradle.lockfile` に載せる。部品の推移依存 `slf4j-api` は既に載っている（2.0.18）。composite build で置き換えた `cherry-mustache-core` そのものは lockfile に載らない見込みで、版はサブモジュールの固定先のコミットで固定する。載るもの・載らないものを B1 で実測して記録する | NFR8.3 の (b)、`project.md` の Mandated |
| OSV-Scanner | lockfile に載ったものは既存の `osvScan` の対象になる。検査の対象の lockfile の一覧は変えない | NFR8.3 の (b) |

## 5. テストの証明書と秘密の値（Q2 A）

- **証明書は実行のときに作る**: テストの支え（例 `backend/src/test/java/cherry/mastersmith/mail/testsupport/`）が、JDK に入っている `keytool`（`java.home` の `bin`）を呼び、名前の合う証明書（`localhost`）と合わない証明書を、テストの JVM ごとに一時のディレクトリの PKCS12 に作る。受け手（SubEtha SMTP）の `SSLContext` と、送る側の信頼の設定（テストの設定の中だけ）に使い、終わったら消す。
- **コミットしない**: 証明書・鍵のファイルはリポジトリに置かず、`.gitignore`（`*.pem`・`*.key`・`*.p12`・`*.jks`）と `.gitleaks.toml` に例外を足さない。新しい依存も足さない。
- **前提**: 手元・CI とも JDK 25（Temurin）の `keytool` がある。`keytool` が見つからない・失敗したときは、原因の分かる文言でテストを失敗させる（黙って飛ばさない）。
- **宛先と資格情報**: テストの宛先は `example.com` などの予約されたドメインだけにする。認証の確かめに使う資格情報は、固定の文字列をコミットせずテストの中で作る値にする。

## 6. 手元の受け手（Mailpit、compose の profile `mail`）

| 項目 | 値 | 理由 |
|---|---|---|
| サービス | `mailpit`（profile `mail`） | 見たいときだけ起動する（NFR11.1、`team.md` の Deployment） |
| イメージ | `axllent/mailpit:v1.31.2@sha256:74d609a42ec279aa63c6b4622a6fa9b5408d1ad5b1d76a1c4be40a265ce0863d` | 版とダイジェストで固定（NFR11.1、`tech-stack-decisions.md` の1.1） |
| 画面と API（8025） | `127.0.0.1:8025:8025` | 認証の無い画面をほかの端末から見せない |
| SMTP（1025） | `127.0.0.1:1025:1025`（Q1 A） | E2E の WAR（PC の上で動く）から送るため。`127.0.0.1` だけに公開する（7節、上流との差） |
| ボリューム | 置かない | コンテナを消すと受けたメールも消える。個人に関する値を残さない |
| メモリの上限 | `256m` | アプリ（既定 2g）・手元の監視（1536m）と同時に colima の VM（6GiB）で動かす |
| 再起動・ログ | `restart: "no"`、`json-file`（10m・3つ） | 既存のサービスにそろえる |

- アプリのコンテナからは compose のネットワークの中の名前で送る: `.env` に `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`（暗号化 NONE・資格情報なし）と `MASTERSMITH_MAIL_FROM`。`app` は既に `.env` を `env_file` で読むため、`compose.yaml` の `app` の `environment` には足さない（前の Intent の学び）。
- 起動は `docker compose --profile mail up -d mailpit`、見終えたら `docker compose stop mailpit`。Mailpit は受けたメールを外へ中継しない。

## 7. E2E（`./gradlew e2eTest`、verify と CI の外）への設定の渡し方（Q1 A）

- `frontend/playwright.config.ts` の `webServer.env` に `SPRING_MAIL_HOST=localhost`・`SPRING_MAIL_PORT=1025`・差出人（`example.com` の宛先）を足す。暗号化 NONE・資格情報なし。
- 既存の E2E（010〜040）はメールを送らず、アプリは起動のときに SMTP へ接続しない（NFR6.4）ため、Mailpit が無くても影響しない。
- **前提の確かめ**: `e2eTest` は始める前に `127.0.0.1:8025` の Mailpit の API に届くかを確かめ、届かなければ起動の手順（`docker compose --profile mail up -d mailpit`）を示して失敗させる（黙って飛ばさない。既存の道具の確かめ `requireTool` と同じ考え方）。起動は開発者が行い、`e2eTest` は Mailpit を起動・停止しない。
- 開発で見るメールと E2E のメールは同じ受け手に入る。E2E は実行ごとに違う宛先を使い、その宛先で取り出す。Mailpit の API から招待のリンクを取り出す方法と、招待のベース URL の渡し方は、使う側の U3・U5〜U7 の段で決める。

## 8. 設定の見本（`.env.example`）と README

`.env.example` に「メール（U1）」の節を足し、すべてコメントの形で置く（接続先の設定が無ければ送らない、NFR2.5）。

| 項目 | 見本の書き方 |
|---|---|
| `SPRING_MAIL_HOST`・`SPRING_MAIL_PORT` | 手元の受け手（`mailpit`・`1025`）だけを例に書く。実在の宛先・外部の SMTP を書かない（NFR11.2） |
| `SPRING_MAIL_PROTOCOL`・STARTTLS（必須）と SMTPS の書き方 | 方式ごとの書き方と、STARTTLS はポート 587 を README に書く |
| `SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD` | 秘密。値を空のまま |
| 時間切れの3つ（例 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT`） | 既定 3 秒（3000 ミリ秒）、1 以上の整数（NFR6.2） |
| `MASTERSMITH_MAIL_FROM`・`MASTERSMITH_MAIL_FROM_NAME` | 差出人と表示名 |

`application.yaml` には空の既定の `spring.mail.host` を置かない。

README のメールの節（B1 の完了の条件。NFR2.4、`security-design.md` の5節）:

- 設定の項目と方式ごとの書き方、STARTTLS ではポート 587
- `starttls.enable` だけの設定では受け手が STARTTLS を受け付けないとメールが平文で届き、招待の URL（トークン）が守られないこと、配備先が決まったら STARTTLS（必須）か SMTPS を使うこと（承認の場の R-01）
- 運用で設定しない値の一覧（`mail.debug`、`mail.smtp.ssl.trust`・`mail.smtps.ssl.trust`、`checkserveridentity` の false、1.2 より古い TLS、STARTTLS の `required` の false、`spring.mail.test-connection` の true、`management.health.mail.enabled` の true、メールの部品のロガーの水準）
- Mailpit の profile の起動と止め方、E2E の前に起動すること
- 配備先が決まるまで実在の宛先・外部の SMTP へ送らないこと

## 9. Dependabot（Q3 A）

- `.github/dependabot.yml` に `docker-compose` の項目（`directory: /`、週ごと）を足す。Mailpit と、既存の `compose.yaml` のイメージ（`grafana/otel-lgtm`・`otel/opentelemetry-collector`・見本の対象DB）の更新の知らせが来るようになる。
- 知らせは `team.md` の受け方で扱う（GitHub の画面でマージせず、手元で更新して `./gradlew verify` を通してから `develop` に統合し、プルリクエストは閉じる）。見本の対象DB のイメージを上げるときは、結合テストのイメージの定数（`TargetDbImages`）と一緒に上げる。
- 新しいサブモジュールは既存のコメントどおり対象にしない（固定先の更新は承認を得た専用のコミット）。新しい Maven の依存は既存の `gradle` の項目で知らせが来る。
- 実際に `compose.yaml` のイメージが知らせの対象になったかを、足した後に GitHub の Dependabot の画面で確かめて記録する（B1）。

## 10. 監視

- 送信の Observation（`mastersmith.mail.send`、タグは `mail.template`・`mail.language`・`mail.outcome`・`mail.failure.kind`）は、外部エクスポートを有効にして profile `monitoring` を起動したときだけ手元の監視に届く。既定は無効のまま（`team.md` の Deployment）。
- ダッシュボードの行と警報の決まりはこの段では作らず、observability-setup の段に引き継ぐ（NFR Design の承認の場の U3 R-02 の引き継ぎと同じ扱い）。書くときは、実際に起動して指標とタグの名前を確かめる（`project.md` の学び）。

## 11. 戻し方と秘密

| 項目 | 扱い |
|---|---|
| DB スキーマ | U1 は変えない（内部DB に触れない、NFR5.1） |
| 前の版への戻し | 既存の手順（前の版のイメージで起動し直す）のまま。`.env` に残った SMTP の項目は前の版が読まないだけで害は無い |
| Mailpit | profile を止めるだけ（`docker compose stop mailpit`）。ボリュームが無いため消すものは無い |
| composite build の不成立 | B1 で5条件のどれかを満たせなければ、ADR-010 のとおり Maven Central への公開に切り替える判断を記録する（部品のリポジトリの変更は部品のリポジトリ側で行う） |
| SMTP の資格情報 | `.env` だけから受け取り、コミットしない。CI には渡さない（NFR2.5、`project.md` の Forbidden） |
| テストの鍵 | 実行のときに一時のディレクトリに作り、コミットしない（5節） |
| Mailpit の画面 | 認証が無いため `127.0.0.1` だけに公開する |

## 12. B1 で確かめること

| 確かめ | 成り立たないとき | 出典 |
|---|---|---|
| 取り込みの5条件（NFR8.3 の (a)〜(e)） | ADR-010 の切り替えの判断を記録する | NFR8.3、承認の場の R-02 |
| 環境変数から `spring.mail.properties` の点を含む鍵への結び付き | 既定の 3 秒で動くことを確かめ、代わりの設定の口の候補を計画に書いて依頼者に諮る | NFR6.2、NFR 要件の R-02、NFR 設計の U1 R-01 |
| 実行可能 WAR の中で `mail/templates/*.html` を数え上げられる | 一覧からファイル名を組み立てて読む形に替える | `logical-components.md` の10節 |
| README のメールの節（8節） | B1 を完了としない | NFR2.4、承認の場の R-01 |
| SpotBugs の関門の追加と既存の1件 | 直すか、理由を書いて除外する | NFR2.9 |
| テンプレートのライセンスヘッダーの検査（3節。Spotless か Gradle のタスクか） | B1 を完了としない | BR2.5 |
| 部品のリポジトリの中にサブモジュールが無い（この段の追加） | CI の取得の設定を見直す | NFR8.3 の (d) |
| lockfile に載るもの・載らないものの記録（この段の追加） | 部品の依存を検査の対象に加える仕組みを足す（`team.md` の Code Style） | NFR8.3 の (b) |
| profile `mail` の起動と、Mailpit を指す設定で `isConfigured` が真になること（この段の追加。招待のメールを画面で見る確かめは B3） | 設定の見本と README を直す | NFR11.1、`logical-components.md` の9節 |
| `e2eTest` の前提の確かめ（Mailpit に届かなければ失敗） | B1 を完了としない | Q1 A |
| Dependabot の `docker-compose` の知らせの対象になったか | 手で見直す手順を README に書く | Q3 A |

## 13. 上流との差

承認済みの文書は書き換えず、差をここに記録する（`project.md` の決まり）。

| 対象 | 承認済みの形 | この段の設計 | 理由 |
|---|---|---|---|
| NFR11.1（Mailpit の公開） | SMTP（1025）は PC に公開しない | SMTP も `127.0.0.1:1025` だけに公開する（Q1 A） | E2E の WAR は PC の上で動き、そのままでは Mailpit に送れない。公開は自分の PC からだけで、Mailpit は外へ中継しない |
| NFR 設計 `logical-components.md` の8節（テストの証明書） | `src/test/resources` に置き、Gitleaks の誤検知なら理由を書いて除外する | テストの実行のときに `keytool` で一時のディレクトリに作り、コミットしない（Q2 A） | `.gitignore` が鍵ファイルを管理外にしていて、`project.md` の Forbidden（鍵ファイルをコミットしない）とぶつかる |
| Dependabot（既存の設定） | `docker` の項目だけ（Dockerfile） | `docker-compose` の項目を足す（Q3 A） | Mailpit を含む `compose.yaml` のイメージの知らせを受けるため。既存の compose のイメージにも及ぶ |
| 既存の前例で埋めた細部 | 上流に値が無い | Mailpit のメモリの上限 `256m`・ボリュームを置かない・`restart` とログの上限、テンプレートのヘッダーの検査を Spotless の `format` で行い段 1 で確かめる、`e2eTest` の前提の確かめ | 既存の `compose.yaml` のサービスと `build.gradle.kts` の前例にそろえた |
