# 部品の一覧（mastersmith2）

## 読み方

見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回と同じ 36 個で、今回は足しも名前の変更もしていない。

- 状態: healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。
- 読みの深さ: 今回深く読んだ部品は「深い（今回）」と書く。ほかは前回（`e68f54d`）までの記録のままで、今回は確かめ直していない。
- K の番号は `business-overview.md` の所見の一覧を指す。この文書に本文を書いたのは今回の K-11（`user`）・K-12（`make-you-chic-ui`）・K-13（`frontend-e2e`）で、ほかは持ち主の文書を参照する。前回の K-1〜K-9 の扱いも `business-overview.md` の一覧にある。
- 依存はバックエンドは `import` の検索で確かめた向き（詳細は `dependencies.md`、前回の記録）。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス（`@EnableScheduling` を含む）。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて `application.yaml`・`logback-spring.xml`）
- 責務: Spring Security の連鎖の組み立て（差し込み口 `SecurityRuleContributor` を order 順に当てる）、SPA の配信、観測の設定。`application.yaml` の `management` の節は、Web に公開するのは health だけ、OTLP の指標の送信は `step: 60s`・既定で無効。`management.metrics.distribution.slo` で `http.server.requests`（100・250・500・1000・2000・5000 ms）と `mastersmith.mail.send`（同じ境界と 10000 ms）の境界だけのバケットを出す（`346c719` で足された。今回は `slo` の数行だけを流し読みで確かめた）。
- 依存: `common-security`・`common-web`・`common-observability`
- 状態: at-risk（K-15 の直しで境界に 300 ms を足すなら、この設定に手を入れる） ／ 読みの深さ: 流し読み（`application.yaml` の `slo` の行だけ）

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
- 責務: `TraceAspect`（service の層の TRACE のログ）ほか観測の共通部品。`SanitizingLogRecordExporter` は、外部エクスポートの出口でだけキー `email`・`enteredEmail`・`sourceIp`・`userAgent` の値を `[REDACTED]` に置き換える（45 行の `MASKED_KEYS`、流し読み）。標準出力のログには当たらない（K-11）。境界のバケットを確かめる `HistogramBucketsIT` がテストの側にある（`346c719` で足された、ファイル名だけ）。`packagesJudgedByTotal` の一覧のパッケージで、コードに手を入れると K-10 の作業が付く（`code-quality-assessment.md`）。
- 状態: healthy ／ 読みの深さ: 流し読み（`MASKED_KEYS` の1行だけ）

### auth

- 場所: `auth/{domain,service,repository,web}`
- 責務: ログイン・トークンの発行と更新・ロック・ログアウト、期限切れのリフレッシュトークンの定期の削除（`RefreshTokenCleanupJob`、cron）。`POST /api/auth/login`・`/api/auth/session/refresh` は p95 の警報（しきい値 1000 ms）の対象。
- 依存: `user`・`common-error`・`common-security`・`common-observability`
- 状態: healthy ／ 読みの深さ: 流し読み

### access

- 場所: `access/{domain,service,web}`
- 責務: 管理者のみの API の認可と 401・403。`GET /api/admin/check` は警報 `ms-check-p95`（しきい値 300 ms、K-15）の対象。
- 依存: `auth`・`common-error`・`common-security`・`config`
- 状態: healthy ／ 読みの深さ: 流し読み

### audit

- 場所: `audit/{domain,service,repository}`
- 責務: 業務の出来事を受け、確定の後に `audit_events` に追記する。`REGISTRATION_FAILED` を含む。README 774 行によれば、監査の書き込みの失敗の ERROR のログはメールアドレスをキーと値で載せる（コードは今回読んでいない。K-11 の範囲に入るかは要件で決める）。
- 依存: 出来事の型のため `auth`・`access`・`user`・`invitation`・`dslmanage` の domain
- 状態: healthy ／ 読みの深さ: 流し読み

### user

- 場所: `user/{domain,service,repository,web}`
- 責務: 利用者・パスワード・プリファレンス。`web` の `MeController` がプリファレンスとパスワードの変更を受ける。`service/InitialAdminInitializer` が起動時に設定の値から初期管理者を作る（既にいれば作らない。同時の起動で作成が `CreateUserResult.EmailAlreadyUsed` になったときも作らない扱い）。
- 依存: `common-error`・`common-observability`
- K-11（確かめた事実）:
  - `InitialAdminInitializer.java` の INFO のログ3か所が、キー `email` にそろえたメールアドレスを載せる。88 行（既にいる）、96 行（同時の起動で `EmailAlreadyUsed`）、99 行（作成した）。81〜84 行の WARN（設定が無い・正しくない）は `reason`・`resolution` だけで、値を載せない。
  - `backend/src/main/java` の中でキー `email` をログに渡すのは、この3か所だけ（`addKeyValue("email"` の検索）。
  - クラスの Javadoc（38〜40 行）が「ログの項目（キー `email` を含む）は前の Intent のまま（依頼者の判断で据え置き。Intent 260925-user-management の U2 のコード生成の計画 9節の決定 3）」と説明する。直すときはこの説明も書き換えが要る。
  - 外部エクスポートの出口では値が伏せられる（`common-observability`）が、標準出力のログは値のまま（README 664 行）。`project.md` の Forbidden「NEVER メールアドレスをアプリのログとエラー応答に含めず…」に反する状態で、前の Intent の配備の段で起動のログの確かめに値が出た記録がある（`project.md` の Corrections）。
  - テスト: `InitialAdminInitializerTest.java` の `creates()`（97〜121 行）は、INFO のログの本文とキー・値にメールアドレスが **含まれること** を確かめる（118〜120 行）。直すときは逆向き（含まれないこと）に変える必要がある。`existing()`（84〜95 行）・`duplicate()`（123〜137 行）はパスワードが出ないことだけを確かめる。ログの取り込みは共通の `LogEvents.capture(...)`。
  - テスト: `InitialAdminIT.java` は標準出力を取り、`JsonLogRecords.assertContainsNoSecret(..., password)`（85・112 行）でパスワードが出ないことだけを確かめる。メールアドレスの確かめは無い。83 行で「初期管理者を作成しました」「初期管理者は既にいるため」の文言を確かめる（文言を変えるとこちらも直す）。README 215 行も「初期管理者を作成しました」の INFO を起動の確かめに使う。
  - カバレッジ: `user.service` は `packagesJudgedByTotal` に無く、既にパッケージごとの下限（行 80%・分岐 70%）の対象（K-10）。
- K-11 の帰結: `project.md` の Mandated（不具合を直すときは再現するテストを同じコミットに含める）により、ログにメールアドレスが含まれないことを確かめるテストを直しと同じコミットに入れる。ほかの `*SecretLeakIT`（`MeSecretLeakIT` など）と同じ `JsonLogRecords` の形が使える（名前だけ確かめた）。`audit` の ERROR（上）を範囲に入れるかは要件で決める。
- 状態: at-risk（K-11） ／ 読みの深さ: 深い（今回。`service/InitialAdminInitializer.java` とその単体・結合テストだけ。ほかは一覧と `email` のキーの検索だけ）

### invitation

- 場所: `invitation/{domain,repository,service,web}`
- 責務: 招待・送り直し・取り消し・一覧、リンクの確かめと登録の完了（`RegistrationService`）、期限の切れた招待の定期の削除（`InvitationCleanupJob`、cron）。表は V8。招待と送り直しは送信が失敗しても 201・200 で `sendResult: FAILED` を返す。登録の完了の拒否は形の誤ったトークンでも `RegistrationFailedEvent` を出す（前回の K-8）。
- 依存: `user`・`mail`・`common-error`・`common-security`・`common-web`・`common-observability`
- 状態: healthy（前回の at-risk の K-8 は `7c2fea4` で README の側が直された。件名による、今回は確かめていない） ／ 読みの深さ: 流し読み（前回は `RegistrationService` の `complete` だけ深い）

### mail

- 場所: `mail/{config,domain,service,template,transport}`
- 責務: SMTP の送信と Mustache のテンプレート（エンジンは `java-mustache-processor`）。`SmtpMailSender` が `Observation` `mastersmith.mail.send` を作る（タグは `mail.template`・`mail.language`・`mail.outcome`・`mail.failure.kind`）。境界のバケットは `config` の `slo` で出る。
- 依存: アプリの中のほかのパッケージを import しない
- 状態: healthy（前回の at-risk の K-6 は `346c719` で直された） ／ 読みの深さ: 流し読み（前回は `service/SmtpMailSender.java` だけ深い）

### appearance

- 場所: `appearance/{config,service,web}`
- 責務: インスタンスの見た目の設定（ブランドカラーなど）を `GET /api/appearance` で画面に渡す。ブランドカラーは設定ファイルで決める項目（前の Intent の学び）。
- 依存: `common-security`
- 状態: healthy ／ 読みの深さ: 流し読み

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
- 責務: 既定の DSL の生成・投入・プレビュー・適用・履歴。`DslOperationMetrics` は `publishPercentileHistogram()` を付ける。
- 依存: `dsl`・`targetdb`・`user`・`auth`・`common-error`・`common-i18n`・`common-web`
- 状態: healthy ／ 読みの深さ: 流し読み

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/`（テストの補助と結合テスト）
- 責務: テストの補助（`targetdb/testsupport/TargetDbImages` の対象DB のイメージの digest の定数、`compose.yaml` と同じ値。`common/testsupport` の `LogEvents`・`JsonLogRecords`）と、アプリ全体を起動する結合テスト。秘密の漏えいの確かめ `*SecretLeakIT` は `AccessSecretLeakIT`・`AuditSecretLeakIT`・`AuthSecretLeakIT`・`InvitationSecretLeakIT`・`MailSecretLeakIT`・`TargetDbSecretLeakIT`・`MeSecretLeakIT` の7つ（名前だけ）。`config/H2CompactionByPoolSuspensionIT` の時間切れ（前回の K-4）は `b20bf1e` で扱われた（件名による）。
- 状態: healthy ／ 読みの深さ: 流し読み（今回はファイル名の一覧だけ）

## 画面（`frontend/src/`）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/pages/`・`app/login-handoff/`・`app/login-state/`・`shared/validation`・`shared/format`
- 責務: 画面の起動と骨組み、共通の画面。`app/pages/Page.css` の文字の色（前回の K-3）は `79a0395` で直された（件名による、今回は確かめていない）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-registry

- 場所: `app/registry/`・`app/navigation/`
- 責務: `features/<featureId>/registration.ts` を置くだけで機能を読み込む仕組み。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/routing/`
- 責務: AppShell の配置、表示言語、振り分け。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-display-settings

- 場所: `app/display-settings/`
- 責務: 利用者のプリファレンスとインスタンスの見た目の設定を解決し、make-you-chic-ui の `ThemeProvider` に渡す。
- 状態: healthy ／ 読みの深さ: 流し読み

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
- 責務: DSL の管理画面 `/admin/dsl`。画面の中のタブ（make-you-chic-ui の Tabs）が E2E の 100 の既知の違反 `dsl-schema-link` の状態の `tab-1` に当たる（K-13）。`DslSubmitForm.css` の文字の色（前回の K-3）は `79a0395` で直された（件名による）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-invitation

- 場所: `features/invitation/`
- 責務: 管理者の招待の画面（招待・送り直し・取り消し・一覧）。`InvitationAdminPage.test.tsx` の時間切れ（前回の K-5）は `b20bf1e` で扱われた（件名による）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-registration

- 場所: `features/registration/`
- 責務: 招待を受けた人のリンクの確かめと登録の完了の画面（ログインなし）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-preferences

- 場所: `features/preferences/`
- 責務: 自分のプリファレンスとパスワードの変更の画面。保存のボタン（primary の Button）が E2E の 100 の既知の違反 `primary-button-hover` の状態の `preferences-save-button` に当たる（K-13）。`PreferencesForm.css` の文字の色（前回の K-3）は `79a0395` で直された（件名による）。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-e2e

- 場所: `frontend/e2e/`（`*.e2e.ts` 10 本（010〜100）と `support/` の補助 13 ファイル）
- 責務: Playwright の E2E（`./gradlew e2eTest`）。050〜080 がブラウザの axe のアクセシビリティの検査、090 が招待から登録までの流れ、100 がアプリの画面の文字のコントラストの検査（状態ごと、ブランドカラーとテーマの組ごと）。`verify` と CI の外で、画面・認証に関わる変更の統合の前とリリースの前に手元で流す（`team.md` の Testing Posture）。
- K-13（確かめた事実）:
  - `100-app-text-contrast.e2e.ts` 55〜83 行の `STATE_KNOWN_VIOLATIONS` に2件がある。`dsl-schema-link` の状態の `color-contrast tab-1`（組は green・orange の light、blue・purple の dark。Tabs の選ばれたタブ）と、`primary-button-hover` の状態の `color-contrast preferences-save-button`（組は green・orange の light・dark。primary の Button の hover）。27〜33 行と 66〜71 行の注記が、この2件と「make-you-chic-ui が直したら外す」を説明する。
  - 145〜172 行の `checkState` は、既知の違反が一覧どおりに当たらないとき（当たらなくなったときも）失敗にする（170〜172 行）。固定先を上げて当たらなくなれば、一覧を空にしないと 100 は失敗する。
  - `support/axe.ts` の `KNOWN_VIOLATIONS`（111〜115 行）・`INVITATION_KNOWN_VIOLATIONS`（157〜164 行）・`REGISTRATION_KNOWN_VIOLATIONS`（172〜178 行）・`PREFERENCES_KNOWN_VIOLATIONS`（188〜196 行）は、どれも既に空（`310e1ec` で解消、前回の K-2）。仕組み `splitKnownViolations`（122〜150 行）は「次に既知の制約を受け入れるときのために残す」と説明されている（103〜110 行）。
  - 050〜080 の冒頭の注記（`050-display-accessibility.e2e.ts` 23〜25 行、`060-…` 29 行、`070-…` 25 行、`080-…` 26 行）は、固定先を `310e1ec` に上げて一覧を空にした経緯を書く（検索だけ）。
  - README の 158 行（100 の説明）・957 行（DSL の管理の画面のコントラスト）・990〜993 行（「残る既知の制約」の2件）が、同じ2件を説明する（K-16）。
- K-13（見立て、未検証）: `077f5b4` の値（K-12）で2件とも当たらなくなる見込みだが、組ごとの当たりは E2E の 100 を実際に流して確かめる必要がある。当たらなくなった組だけを一覧から外す形になりうる。
- 状態: at-risk（K-13） ／ 読みの深さ: 深い（今回。`100-app-text-contrast.e2e.ts` と `support/axe.ts`。050〜080 は注記の検索だけ）

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、`.gitmodules` の URL は make-you-chic-ui の GitHub のリポジトリ。固定先 `310e1ecf2fe8b4a388cb8ae098a59144dc93acc5`）。画面からは `file:` の依存で使う（`frontend/package-lock.json` では `link: true`、版の文字列は `0.0.0`）。
- 責務: デザインシステム（`ThemeProvider`・AppShell・フォームの部品・Tabs・Button など）。
- K-12（確かめた事実）:
  - 依頼の `077f5b4` はサブモジュールの手元の履歴にあり、`origin/main` に含まれ、`310e1ec` の子孫である（開発担当が `merge-base --is-ancestor` で確かめた。fetch はしていない）。サブモジュールの作業ツリーは固定先のまま、変更なし。
  - `310e1ec..077f5b4` は2コミット: `a34d611`（Tabs: active タブの文字色のコントラスト不足を直す）と `077f5b4`（Button: primary の hover・active の文字色のコントラスト不足を直す）。
  - 差は4ファイル（+44・−5）で、`package.json`・lockfile は変わらない。`src/components/Tabs/Tabs.css` の `.mycui-tab.active` の文字を `--color-primary` から `--color-primary-emphasis-text` に、`src/components/Button/Button.css` の primary の `:hover`・`:active` に `color: var(--color-primary-hover-text)` を足す。`src/theme/semantic.css` に `--color-primary-emphasis-text`（light は `--brand-700`、dark は `--brand-400`）と `--color-primary-hover-text: #fff` を足す。`src/theme/contrast.test.ts` に検査を足す（このリポジトリの CI の対象外）。
- K-12（見立て、未検証）: hover の文字が白になるため、green・orange の primary の Button の hover は濃い文字から白に替わる（前回 `310e1ec` で入った「green・orange は濃い文字」の前提と、README 976〜988 行の表の前提が変わる）。`frontend/package-lock.json` の書き換えは要らない見込み（`link: true` のため。実行はしていない）。
- K-12 の帰結: 更新は `project.md` の Mandated（承認を得た専用のコミット、更新前後のハッシュ `310e1ec` → `077f5b4` の記録）と `team.md` の Way of Working（サブモジュールの更新を含む変更は短命のブランチから fast-forward で統合してよい）に従う。確かめは手元の E2E（K-13）でだけ行える（CI は E2E を流さない）。
- 状態: at-risk（K-12） ／ 読みの深さ: 深い（今回。`310e1ec..077f5b4` の差分の4ファイルだけ。部品のファイル全体は読んでいない）

### java-mustache-processor

- 場所: `vendor/java-mustache-processor/`（Git サブモジュール、固定先 `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`、`0.1.0`）。Gradle の composite build で `cherry-mustache-core` として使う。
- 責務: メールのテンプレートの Mustache のエンジン。中身はこのリポジトリから変えない（`project.md` の Forbidden）。今回の Intent では触れない見込み。
- 状態: healthy ／ 読みの深さ: 流し読み（固定先の確認だけ）

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`config/npm-build-tools.txt`・`.github/workflows/ci.yml`・`.github/dependabot.yml`
- 責務: 1コマンドの検査 `./gradlew verify`（段の並びは `architecture.md` の Interaction Diagrams 4）、WAR の組み立て、lockfile による版の固定、カバレッジの下限（JaCoCo の全体とパッケージごと、`packagesJudgedByTotal` は 12 個、K-10）、SpotBugs・OSV-Scanner（lockfile の `dev`・`devOptional` の印で実行時と開発時を分け、開発時は警告だけ、`config/npm-build-tools.txt` の道具と `MAL-` は失敗。`build.gradle.kts` 226〜340 行）・Gitleaks、CI（`develop` へのプッシュ・`v*` のタグ・手動。Actions はコミットのハッシュで固定、Gitleaks と OSV-Scanner は版と SHA-256 を確かめて入れる）、Dependabot（K-14、`dependencies.md`）。
- Gradle のプラグイン（spotless など）は lockfile に載らず、版は `libs.versions.toml` の1行だけで決まる（`settings-gradle.lockfile` は空の項目だけ、`backend/gradle.lockfile` に spotless の行は無い）。
- 状態: at-risk（K-10・K-14） ／ 読みの深さ: 深い（今回。`settings.gradle.kts` は読んでいない）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`
- 責務: WAR をコピーするだけのイメージ（`eclipse-temurin:25.0.4_7-jre-noble`、JVM は `-XX:MaxRAMPercentage=50.0 -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1`）、`app` のサービスと、profile で起動する監視・メールの受け手（Mailpit）・見本の対象DB（イメージは digest 付き。`mysql` 26.7.0・`mariadb` 13.0.2・`postgres` 18.6）。
- 状態: healthy ／ 読みの深さ: 流し読み（今回は `compose.yaml` のイメージの行だけ）

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験と使い捨ての環境、otel-lgtm の手元の監視（`docker/monitoring/` の警報 `provisioning/alerting/mastersmith.yaml` とダッシュボード `dashboards/mastersmith-overview.json`）、`docker/otel-collector/config.yaml`（受けた値を debug に出すだけ、前回の記録）。p95 の警報3件（`ms-login-p95`・`ms-refresh-p95` は 1000 ms、`ms-check-p95` は 300 ms）は境界のバケットで値を持つ。`ms-check-p95` のしきい値が境界に無い（K-15、`architecture.md` の Interaction Diagrams 1）。しきい値 300 は警報・パネルのしきい値の段・SLI の表の3か所（とリポジトリの README の表）にある。
- 状態: at-risk（K-15） ／ 読みの深さ: 深い（今回。警報の決まりとダッシュボードだけ。`perf/`・`docker/targetdb/`・`docker/jmx/`・`hikari-pool.sh`・`docker/otel-collector/` は流し読み）
