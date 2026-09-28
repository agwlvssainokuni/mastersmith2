# 部品の一覧（mastersmith2）

## 読み方

見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回のコード知識ベースの 27 個をそのまま使い、前回の後に増えた部品に 9 個を足した（`invitation`・`mail`・`appearance`・`frontend-app-display-settings`・`frontend-feature-invitation`・`frontend-feature-registration`・`frontend-feature-preferences`・`frontend-e2e`・`java-mustache-processor`）。足した理由は `reverse-engineering-timestamp.md` の「部品 ID の扱い」。

- 状態: healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ: 深い（今回深く読んだ。一部のファイルだけのものは括弧で示す）・流し読み。深さ Minimal のため、多くの部品は一覧と検索だけである。流し読みの部品の責務は、前回の記録とファイルの一覧による。
- K の番号は `business-overview.md` の所見の一覧を指す。この文書に本文を書いたのは K-1（`make-you-chic-ui`）と K-2（`frontend-e2e`）だけで、ほかは持ち主の文書を参照する。
- 依存はバックエンドは `import` の検索で確かめた向き（詳細は `dependencies.md`）。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス（`@EnableScheduling` を含む）。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて `application.yaml`・`logback-spring.xml`）
- 責務: Spring Security の連鎖の組み立て（差し込み口 `SecurityRuleContributor` を order 順に当てる）、SPA の配信、観測の設定。`application.yaml` の `management` の節は、Web に公開するのは health だけ、OTLP の指標の送信は `step: 60s`・既定で無効。指標の分布（`management.metrics.distribution`）の設定は無い（K-6、`architecture.md`）。
- 依存: `common-security`・`common-web`・`common-observability`
- 状態: at-risk（K-6 の直しの置き場の候補） ／ 読みの深さ: 流し読み（`application.yaml` の `spring.datasource`・`management` の節だけ深い）

### common-error

- 場所: `common/error/{domain,service,web}`
- 責務: `ProblemType`（code・状態コード・日英の文言）、`BusinessException`、`GlobalExceptionHandler`（Problem Details）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-security

- 場所: `common/security/`
- 責務: 秘密の値の伏せ字、安全の決まりの差し込み口の型。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-web

- 場所: `common/web/`
- 責務: 本文の大きさの上限、Web の設定値（`mastersmith.web.base-url` ほか）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-health

- 場所: `common/health/`
- 責務: ヘルスチェック。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-i18n

- 場所: `common/i18n/`
- 責務: 表示言語の解決（日英）。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-observability

- 場所: `common/observability/`
- 責務: `TraceAspect`（service の層の TRACE のログ）ほか観測の共通部品。`packagesJudgedByTotal` の一覧のパッケージで、コードを足すと K-10 の作業が付く（`code-quality-assessment.md`）。
- 状態: healthy ／ 読みの深さ: 流し読み

### auth

- 場所: `auth/{domain,service,repository,web}`
- 責務: ログイン・トークンの発行と更新・ロック・ログアウト、期限切れのリフレッシュトークンの定期の削除（`RefreshTokenCleanupJob`、cron）。`POST /api/auth/login`・`/api/auth/session/refresh` は p95 の警報の対象（K-6）。
- 依存: `user`・`common-error`・`common-security`・`common-observability`
- 状態: healthy ／ 読みの深さ: 流し読み（`@Scheduled` の検索だけ）

### access

- 場所: `access/{domain,service,web}`
- 責務: 管理者のみの API の認可と 401・403。`GET /api/admin/check` は p95 の警報の対象（K-6）。
- 依存: `auth`・`common-error`・`common-security`・`config`
- 状態: healthy ／ 読みの深さ: 流し読み

### audit

- 場所: `audit/{domain,service,repository}`
- 責務: 業務の出来事を受け、確定の後に `audit_events` に追記する。`REGISTRATION_FAILED` を含む（K-8、`api-documentation.md`）。
- 依存: 出来事の型のため `auth`・`access`・`user`・`invitation`・`dslmanage` の domain
- 状態: healthy ／ 読みの深さ: 流し読み（`AuditEventListener`・`AuditEventType` は `REGISTRATION_FAILED` の検索だけ）

### user

- 場所: `user/{domain,service,repository,web}`
- 責務: 利用者・パスワード・プリファレンス。`web` の `MeController` がプリファレンスとパスワードの変更を受ける（前回の後に増えた）。
- 依存: `common-error`・`common-observability`
- 状態: healthy ／ 読みの深さ: 流し読み（一覧だけ）

### invitation

- 場所: `invitation/{domain,repository,service,web}`（前回の後に増えた）
- 責務: 招待・送り直し・取り消し・一覧、リンクの確かめと登録の完了（`RegistrationService`）、期限の切れた招待の定期の削除（`InvitationCleanupJob`、cron）。表は V8。招待と送り直しは送信が失敗しても 201・200 で `sendResult: FAILED` を返す。登録の完了の拒否は形の誤ったトークンでも `RegistrationFailedEvent` を出す（K-8）。
- 依存: `user`・`mail`・`common-error`・`common-security`・`common-web`・`common-observability`
- 状態: at-risk（K-8 の記録の誤りの対象） ／ 読みの深さ: 深い（`service/RegistrationService.java` の `complete` だけ）

### mail

- 場所: `mail/{config,domain,service,template,transport}`（前回の後に増えた）
- 責務: SMTP の送信と Mustache のテンプレート（エンジンは `java-mustache-processor`）。`SmtpMailSender` が `Observation` `mastersmith.mail.send` を作る（タグは `mail.template`・`mail.language`・`mail.outcome`・`mail.failure.kind`）。分布の設定は無い（K-6）。
- 依存: アプリの中のほかのパッケージを import しない
- 状態: at-risk（K-6） ／ 読みの深さ: 深い（`service/SmtpMailSender.java` だけ）

### appearance

- 場所: `appearance/{config,service,web}`（前回の後に増えた）
- 責務: インスタンスの見た目の設定（ブランドカラーなど）を `GET /api/appearance` で画面に渡す。
- 依存: `common-security`
- 状態: healthy ／ 読みの深さ: 流し読み（一覧だけ）

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`
- 責務: 対象DB の接続とスキーマの読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み

### dsl

- 場所: `dsl/{domain,parse,validate,service}`
- 責務: DSL の型・安全な読み込み・検証・適用中のモデル。
- 状態: healthy ／ 読みの深さ: 流し読み

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`
- 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴。`DslOperationMetrics` は `publishPercentileHistogram()` を付ける唯一の指標（K-6 の手本）。
- 依存: `dsl`・`targetdb`・`user`・`auth`・`common-error`・`common-i18n`・`common-web`
- 状態: healthy ／ 読みの深さ: 流し読み（`service/DslOperationMetrics.java` だけ深い）

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/`（テストの補助と結合テスト）
- 責務: テストの補助（`targetdb/testsupport/TargetDbImages` の対象DB のイメージの digest の定数、34・40・47 行、`compose.yaml` と同じ値）と、アプリ全体を起動する結合テスト。`config/H2CompactionByPoolSuspensionIT` はプールの一時停止による詰め直しを確かめ、CI で 10 秒の時間切れになった（K-4、`architecture.md` の Interaction Diagrams 3）。
- 状態: at-risk（K-4） ／ 読みの深さ: 深い（`H2CompactionByPoolSuspensionIT`・`TargetDbImages` だけ。ほかはファイルと注釈の数だけ）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/pages/`・`app/login-handoff/`・`app/login-state/`・`shared/validation`・`shared/format`
- 責務: 画面の起動と骨組み、共通の画面。`app/pages/Page.css` の `.page-link` は文字の色に `--color-primary` を使うが、どの `.tsx` からも使われていない（K-3）。
- 状態: healthy ／ 読みの深さ: 流し読み（`app/pages/Page.css` だけ深い）

### frontend-registry

- 場所: `app/registry/`・`app/navigation/`
- 責務: `features/<featureId>/registration.ts` を置くだけで機能を読み込む仕組み。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/routing/`
- 責務: AppShell の配置、表示言語、振り分け。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-display-settings

- 場所: `app/display-settings/`（前回の後に増えた）
- 責務: 利用者のプリファレンスとインスタンスの見た目の設定を解決し、make-you-chic-ui の `ThemeProvider` に渡す。
- 状態: healthy ／ 読みの深さ: 流し読み（一覧だけ）

### frontend-api-client

- 場所: `shared/api-client/`
- 責務: 同じオリジンの `/api/**` の呼び出し、トークンの付与、Problem Details の読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログインの画面とログインの状態。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理の入口の画面。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-dsl

- 場所: `features/dsl/`
- 責務: DSL の管理画面 `/admin/dsl`。`DslSubmitForm.css` の `.dsl-link` は文字の色に `--color-primary` を使う（K-3）。
- 状態: healthy ／ 読みの深さ: 流し読み（`DslSubmitForm.css` だけ深い）

### frontend-feature-invitation

- 場所: `features/invitation/`（前回の後に増えた）
- 責務: 管理者の招待の画面（招待・送り直し・取り消し・一覧）。`InvitationAdminPage.test.tsx` の1件（749 行）が CI で Vitest の既定の 5 秒を超えた（K-5、`code-quality-assessment.md`）。
- 状態: at-risk（K-5） ／ 読みの深さ: 流し読み（`InvitationAdminPage.test.tsx` のテストの一覧と 749〜824 行だけ深い。本体は一覧だけ）

### frontend-feature-registration

- 場所: `features/registration/`（前回の後に増えた）
- 責務: 招待を受けた人のリンクの確かめと登録の完了の画面（ログインなし）。
- 状態: healthy ／ 読みの深さ: 流し読み（一覧だけ）

### frontend-feature-preferences

- 場所: `features/preferences/`（前回の後に増えた）
- 責務: 自分のプリファレンスとパスワードの変更の画面。`PreferencesForm.css` 47 行の `.preferences-choice-error` は文字の色に `--color-danger` を使う（K-3）。
- 状態: at-risk（K-3） ／ 読みの深さ: 流し読み（`PreferencesForm.css` だけ深い）

### frontend-e2e

- 場所: `frontend/e2e/`（`*.e2e.ts` 9 本と `support/` の補助。前回は部品として分けていなかった）
- 責務: Playwright の E2E（`./gradlew e2eTest`）。050〜080 がブラウザの axe のアクセシビリティの検査（ブランドカラーとテーマの組は `support/displayCombos.ts`）、090 が招待から登録までの流れ。`verify` と CI の外で、画面・認証に関わる変更の統合の前とリリースの前に手元で流す（`team.md` の Testing Posture）。
- K-2（確かめた事実）: `support/axe.ts` は既知の違反の一覧を持つ。`KNOWN_VIOLATIONS`（green・orange の primary の Button、050）・`INVITATION_KNOWN_VIOLATIONS`（060）・`REGISTRATION_KNOWN_VIOLATIONS`（070）・`PREFERENCES_KNOWN_VIOLATIONS`・`AVATAR_KNOWN_COMBOS`・`withFormFieldErrorKnownViolation`（080）。どれも「当たるはずの違反が当たらなければ一覧と一致せず失敗する」作り（159〜288 行のコメント）。README の 112・121・129・137・864・879・893・912 行と「既知の制約（ブランドカラーのコントラスト）」の節（927〜948 行）が同じ内容を持つ。
- K-2 の帰結: K-1 の更新でコントラストの違反が消えると、E2E は一覧と一致せず失敗する。一覧と README を同じ変更で直す必要がある。E2E は `verify` と CI の外なので、CI だけでは気づけない。Build and Test の手順に `./gradlew e2eTest`（Mailpit を含む）を入れる必要がある。`.preferences-choice-error` がサーバーの誤りのときだけ出るため 080 で検査されていない可能性がある（未確認）。`.dsl-link` は 050〜080 の検査の対象の画面に入っていない（検索）。
- 状態: at-risk（K-2） ／ 読みの深さ: 深い（`support/axe.ts`・`support/displayCombos.ts` だけ。050・080 は検索だけ）

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`）。画面からは `file:` の依存で使う（`frontend/package-lock.json` では `link: true`、版の文字列は `0.0.0`）。
- 責務: デザインシステム（`ThemeProvider`・AppShell・フォームの部品・Avatar・Alert・Badge・Button など）。
- K-1（確かめた事実）:
  - 依頼の `7865c28`（2026-09-28、「テキストコントラスト不足3件(+関連2件)をWCAG AA対応」）と `310e1ec`（同日、「Badge: variant-successの白文字コントラスト不足を修正」）は、サブモジュールの手元の履歴で `735ef04 → 7865c28 → 310e1ec` と一直線に続く（`310e1ec` は `origin/main` に含まれる）。差は9ファイル・+199／−7 行で、`package.json`・`package-lock.json` は変わらない（`git diff --stat 735ef04 310e1ec`、アーキテクトも確かめた）。
  - 直しは文字用のトークンを足して部品の CSS を切り替える形: `--color-primary-text`（blue・purple は白、green・orange は `--gray-900`）、`--color-primary-subtle-text`（light `--brand-700`・dark `--brand-400`、Avatar の頭文字）、`--color-danger-subtle-text`（Alert の danger）、`--color-danger-text`（light は `--color-danger`、dark は `--red-400`。FormField の誤りの文字と必須の印）、`--color-success-text`（`--gray-900`、Badge の success）。塗りの色（`--color-primary` など）は変えていない。green・orange の primary の Button の文字は白から濃い色に変わる（見た目の変化）。回帰テスト `src/theme/contrast.test.ts` と `src/theme/contrast.ts` が足された（このリポジトリの CI の対象外）。
  - `frontend/package-lock.json` の書き換えは要らない見込み（`link: true` のため。実行はしていない）。
- K-1 の帰結: 更新は `project.md` の Mandated（承認を得た専用のコミット、更新前後のハッシュ `735ef04` → `310e1ec` の記録）と `team.md` の Way of Working（サブモジュールの更新を含む変更は短命のブランチから fast-forward で統合してよい）に従う。`vendorUnchanged` は固定先の変更は止めず、サブモジュールの中の作業フォルダの変更だけを止める。周辺の直しは K-2（E2E の一覧と README）と K-3（アプリ自身の CSS）。
- 状態: at-risk（K-1） ／ 読みの深さ: 深い（`735ef04..310e1ec` の差分だけ。`src/theme/semantic.css` と Avatar・FormField・Alert・Badge・Button の CSS の変更行、`docs/integration-guide.md` の追記。部品のファイル全体は読んでいない）

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、固定先 `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`、`0.1.0`。前回の後に増えた）。Gradle の composite build で `cherry-mustache-core` として使う。
- 責務: メールのテンプレートの Mustache のエンジン。中身はこのリポジトリから変えない（`project.md` の Forbidden）。今回の Intent では触れない見込み。
- 状態: healthy ／ 読みの深さ: 流し読み（固定先の確認だけ）

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`frontend/package.json`・`frontend/vitest.config.ts`・`frontend/vitest.setup.ts`・`config/npm-build-tools.txt`・`.github/workflows/ci.yml`・`.github/dependabot.yml`
- 責務: 1コマンドの検査 `./gradlew verify`（段の並びは `architecture.md` の Interaction Diagrams 4）、WAR の組み立て、lockfile による版の固定、カバレッジの下限（JaCoCo の全体とパッケージごと、`packagesJudgedByTotal` は今 12 個、K-10）、テストの JVM の設定（`maxHeapSize = "1g"`・`-Dh2.compactThreads=1`・`-Djava.net.preferIPv4Stack=true`）、Vitest の `thresholds`（行 80・分岐 70。`testTimeout` は置かず既定の 5 秒、K-5）、SpotBugs・OSV-Scanner（対象の lockfile は3つ）・Gitleaks、CI（`develop` へのプッシュ・`v*` のタグ・手動、`timeout-minutes: 60`）、Dependabot（K-7、`dependencies.md`）。
- 状態: at-risk（K-5・K-7・K-10） ／ 読みの深さ: 深い

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`
- 責務: WAR をコピーするだけのイメージ（`eclipse-temurin:25.0.4_7-jre-noble`、JVM は `-XX:MaxRAMPercentage=50.0 -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1`）、`app` のサービスと、profile で起動する監視・メールの受け手（Mailpit）・見本の対象DB（イメージは digest 付き）。
- 状態: at-risk（K-7 の docker・docker-compose の更新の対象） ／ 読みの深さ: 深い（`Dockerfile` の `FROM` と `ENTRYPOINT` だけ。`compose.yaml` はイメージの行だけ）

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験と使い捨ての環境、otel-lgtm の手元の監視（`docker/monitoring/` の警報 `provisioning/alerting/mastersmith.yaml` とダッシュボード `dashboards/mastersmith-overview.json`）、`docker/otel-collector/config.yaml`（受けた値を debug に出すだけ）。p95 の警報3件とパネル3つが値を持たない（K-6、`architecture.md`）。
- 状態: degraded（K-6） ／ 読みの深さ: 深い（警報・ダッシュボード・`otel-collector/config.yaml` だけ。`perf/`・`docker/targetdb/`・`docker/jmx/`・`hikari-pool.sh` は流し読み）
