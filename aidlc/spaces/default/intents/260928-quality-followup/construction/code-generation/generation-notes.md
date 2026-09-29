# 生成のメモ（Intent 260928-quality-followup）

`code-summary.md` にまとめるための、手順ごとの実測・確かめ・計画との差のメモ。

## Step 1: 作業ブランチと前提の確かめ（2026-09-29）

- `develop`（`f7902e7`）から `fix/260928-quality-followup` を作った。
- `git status`: アプリのソースに未コミットの変更なし（変更はワークフローの記録 `aidlc/spaces/default/intents/260928-quality-followup/` の監査の断片と `construction/` だけ）。
- `git submodule status`: `vendor/make-you-chic-ui` = `735ef04`、`vendor/java-mustache-processor` = `8d44c36`（計画どおり）。
- colima: Running（aarch64・CPU 4・メモリ 6GiB・runtime docker）。以降のコマンドは README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けて流す。
- `.env`・鍵ファイル・`reference/` は開いていない。

## Step 3: Dependabot の本数の確かめ直し（2026-09-29 17:2x）

- 依頼者の了承（依頼の文）のうえで `git fetch origin --prune` を行った。取得の前後とも `origin/dependabot/*` は **15 本**で、増減なし（計画の 8節の一覧と同じ 15 本）。`origin/develop` は `e68f54d`（手元の `develop` の `f7902e7` より 2 つ前。手元の2コミットはまだ push されていない）。
- GitHub で開いているプルリクエストの数は AI では確かめていない（`gh` を使わない）。**依頼者に画面で確かめてもらう**（前の記録の 11 件との差の確かめ。R-03）。
- ブランチごとの元のコミット（`develop` との merge-base）・先端・変わるファイル:

| ブランチ（`origin/dependabot/` を省く） | 先端 | 元 | 変わるファイル |
|---|---|---|---|
| docker/eclipse-temurin-26.0.2_10-jre-noble | 95ecfad | 0d72ab8 | Dockerfile |
| docker_compose/grafana/otel-lgtm-0.34.0 | 1b61af9 | 37a3a4f | compose.yaml |
| docker_compose/mariadb-13.0.2 | 75b4b46 | fd44e79 | compose.yaml |
| docker_compose/mysql-26.7.0 | 670b3e0 | fd44e79 | compose.yaml |
| docker_compose/postgres-18.6 | 9420367 | fd44e79 | compose.yaml |
| gradle/com.networknt-json-schema-validator-3.0.7 | a5fb93e | fd44e79 | gradle/libs.versions.toml |
| gradle/com.tngtech.archunit-archunit-junit5-1.5.1 | 056f11e | 37a3a4f | gradle/libs.versions.toml |
| gradle/gradle-wrapper-9.8.0 | 112f12e | 37a3a4f | gradle/wrapper/gradle-wrapper.jar・gradle/wrapper/gradle-wrapper.properties・gradlew.bat |
| gradle/io.opentelemetry.instrumentation-opentelemetry-logback-appender-1.0-2.31.1-alpha | b212d2a | 0d72ab8 | backend/gradle.lockfile・gradle/libs.versions.toml |
| gradle/org.yaml-snakeyaml-2.7 | 915f650 | 37a3a4f | gradle/libs.versions.toml |
| npm_and_yarn/frontend/prettier-3.9.9 | e2fe1c2 | fd44e79 | frontend/package-lock.json・frontend/package.json |
| npm_and_yarn/frontend/typescript-7.0.2 | 0bc23cb | fd44e79 | frontend/package-lock.json・frontend/package.json |
| npm_and_yarn/frontend/vite-8.3.1 | d874bbc | fd44e79 | frontend/package-lock.json・frontend/package.json |
| npm_and_yarn/frontend/vitest-5.0.2 | dd354b0 | 37a3a4f | frontend/package-lock.json・frontend/package.json |
| npm_and_yarn/frontend/vitest/coverage-v8-5.0.2 | 979b453 | 37a3a4f | frontend/package-lock.json・frontend/package.json |

- 気づき: Gradle wrapper のブランチは `gradlew`（sh）を変えず `gradlew.bat` だけを変える。Gradle の3本（networknt・archunit・snakeyaml）は `backend/gradle.lockfile` を変えていないため、lockfile は手元で作り直す要がある（Step 18 の計画どおり）。
- どのブランチもそのまま統合しない（Step 18 で版の変更だけを当て直す）。

## Step 2（前半）: 絞ったコマンドの確かめ（変更の前）

- バックエンド（`unit-test-instructions.md` 2.1 から `HistogramBucketsIT` を除いた4つ）: BUILD SUCCESSFUL。`ExposureIT` 11・`H2CompactionByPoolSuspensionIT` 3・`InvitationAuditIT` 3・`RegistrationApiIT` 5、失敗 0・SKIPPED 0。
- 画面の単体テスト（2.3 の5ファイル）: Test Files 5 passed・Tests 77 passed（Vitest 4.1.11）。
- Stylelint（2.4 の3ファイル）: 指摘なし（終了コード 0）。

## Step 4: README に書く内容のソースでの確かめ

読んだ場所（計画を書いた時点 `f7902e7` と同じソース）:

- **登録の完了の拒否**（`POST /api/registration/complete`）
  - `backend/src/main/java/cherry/mastersmith/invitation/service/RegistrationService.java` の `complete`（152〜159 行）: `InvitationToken.parse` が空（形の誤ったトークン）でも `Outcome.Refused(null, INVITATION_NOT_FOUND)` として `RegistrationFailedEvent` を出す。トランザクションの中の拒否（見つからない・期限切れ・使用済み・取り消し・同じメールアドレスの登録済み）も同じ出来事。入力の誤り（`CompleteResult.Invalid`）は出来事を出さず 400 `VALIDATION_FAILED`。
  - `audit/service/AuditEventListener.java` の `onRegistrationFailedEvent`（208 行）が `REGISTRATION_FAILED`（`AuditResult.FAILURE`）を記録する。
  - `invitation/web/RegistrationController.java`（109・113〜115 行）: `CompleteResult.Rejected` を `BusinessException(REGISTRATION_LINK_INVALID)` にする。`InvitationProblemTypes.REGISTRATION_LINK_INVALID` は 404。
  - ログ: `common/error/web/GlobalExceptionHandler.java` の `log`（219〜233 行）が業務エラーを **WARN**「要求をエラー応答に変換しました」（`code`・`status`・`exceptionType`）で1件出す。ERROR ではない。
  - 実行での裏付け: `RegistrationApiIT` の「every rejection reason gives the same 404 body and the audit records the reason of BR7.6」（42・44 文字・使えない文字の形の誤ったトークンを含めて 404 と監査の理由を確かめる）が Step 2 で通った。
- **リンクの確かめの拒否**（`POST /api/registration/verify`、R-01）
  - `RegistrationService.verify`（118〜132 行）は出来事を出さない（`eventPublisher` を呼ばない）。拒否は `VerifyResult.Rejected`。
  - `RegistrationController.verify`（82〜85 行）が同じ `REGISTRATION_LINK_INVALID`（404）にする。ログは上と同じ WARN の1件。
  - 実行での裏付け: `RegistrationApiIT`「verify returns only the email and language, changes nothing and is not audited」（`api.verify("A".repeat(43))` が 404 で監査の件数が変わらない）と、`InvitationAuditIT`「refusals, send failures are not audited; verify, input errors and replacement are not audited」（有効なトークンと 43 文字の誤ったトークンの verify の前後で監査の件数が同じ）が Step 2 で通った。→ **監査に残らない**（計画の時点の読みどおり）。
- **招待メールの送信の失敗**
  - `invitation/web/InvitationAdminController.java`: 招待は 201（93 行、送信に失敗しても 201 で `sendResult: FAILED`。47 行の Javadoc）、送り直しは 200（127 行、`ResendResult.Resent` の本文に `sendResult`）。
  - ログ: `mail/service/SmtpMailSender.java` の `log`（155〜171 行）が失敗を **WARN**「メールを送信できませんでした」（`templateId`・`language`・`failureKind`・あれば `exceptionType`）で出す。`invitation/service/InvitationMailDispatcher.java`（95〜101 行）が **INFO**「招待メールを送れませんでした。一覧から送り直せます」（`invitationId`・`operation`・`failureKind`）を出す。ERROR のログは出ない（想定外の `MailUnexpectedException` のときだけは変換の境界で扱いが別）。
  - 監査: `audit/domain/AuditEventType.java` にメールの送信の失敗に当たる種類は無い（招待の種類は `INVITATION_ISSUED`・`INVITATION_RESENT`・`INVITATION_CANCELLED` だけ）。送信に失敗した招待も `INVITATION_ISSUED` は記録される（`InvitationAuditIT` の notRecorded は「送信の失敗に別の監査が無い」ことを確かめる）。
  - 警報の式（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）: `ms-5xx-ratio`（75 行）は `status=~"5.."` の割合、`ms-error-logs`（107 行）は `logback_events_total{level="error"}`。201・200 の応答と WARN・INFO のログはどちらにも当たらない → **既存の警報では拾わない**。
- 前の Intent の記録（`alarms.md`・`log-queries.md`・`runbooks.md` の RB-17）は書き換えていない。

## Step 2（後半）: 基準の測定（変更の前、`f7902e7` の上の作業ブランチ）

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima・`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` あり）: **BUILD SUCCESSFUL in 6m 56s**。
  - バックエンドの単体テスト: 1234 件（失敗 0・エラー 0・SKIPPED 0）
  - バックエンドの結合テスト: 562 件（失敗 0・エラー 0・SKIPPED 0。対象DB のテストも実行された）
  - 画面の単体テスト: Test Files 91・Tests 732 passed
  - カバレッジ（全体の合計）: バックエンド（JaCoCo `jacocoTestReport.xml`）行 98.78%（5600/5669）・分岐 94.38%（2050/2172）。画面（coverage-v8）Lines 97.44%（2322/2383）・Branches 92.67%（1443/1557）・Statements 97.21%。
- `./gradlew e2eTest`（Mailpit を `docker compose --profile mail up -d mailpit` で起動）: **90 passed（3.3m）**、BUILD SUCCESSFUL in 3m 22s。ファイルごとの件数（出力の行から）: 010 が 2・020 が 2・030 が 1・040 が 1・050 が 21・060 が 21・070 が 20・080 が 21・090 が 1。050〜080 は今の既知の違反の一覧のまま通る（Step 10 の比べる元）。
- Mailpit はこの時点から起動したまま（`mastersmith-mailpit-1`。Step 10・12 の E2E でも使う）。

## Step 5: 指標のバケットの設定

- `backend/src/main/resources/application.yaml` の `management.metrics` に `distribution.slo` を足した（`http.server.requests`: 100ms〜5000ms の6つ、`mastersmith.mail.send`: 100ms〜10000ms の7つ）。`percentiles-histogram` は使わない。日本語のコメントで境界の理由（しきい値 1000 ms・送信の時間切れ 10000 ms）と系列を絞る理由（NFR3）を書いた。
- `management.endpoints.web.exposure.include: health` は変えていない。
- 設定だけで当たった（Step 6 の結合テストで確かめた）。Java のコード（`MeterFilter` など）は足していない → `packagesJudgedByTotal` の作業は付かない。

## Step 6: バケットの結合テスト

- 新規 `backend/src/test/java/cherry/mastersmith/common/observability/HistogramBucketsIT.java`（3件。Spotless で整形済み）。
  - login の API へ存在しない利用者・誤ったパスワードで要求（401）→ `http.server.requests{uri=/api/auth/login}` の Timer の `takeSnapshot().histogramCounts()` の境界がちょうど 100・250・500・1000・2000・5000 ms。
  - Spring の文脈の `MailSender` に1件送る（テストの既定は SMTP の接続先が無く NOT_CONFIGURED で終わり、実際には送らない。観測は記録される）→ `mastersmith.mail.send` の境界がちょうど 7 つ（〜10000 ms）。
  - `http.server.requests` のすべての Timer の境界がちょうど6つ（既定のバケットが出ていない）。
- 計画との差: mail の Timer は「登録先の MeterFilter を通る Timer を作る」のではなく、実際の送信の入口（`SmtpMailSender` の Observation）を1回通して記録された Timer を読む形にした（モックや手で作った Timer より実際の経路に近いため）。宛先は `example.test`、送信は行われない。
- 絞ったコマンド（`unit-test-instructions.md` 2.1 の5つ）: BUILD SUCCESSFUL。`HistogramBucketsIT` 3・`ExposureIT` 11・`H2CompactionByPoolSuspensionIT` 3・`InvitationAuditIT` 3・`RegistrationApiIT` 5、失敗 0・SKIPPED 0。公開の範囲（`ExposureIT` の `/actuator/metrics` などが 404）は変わっていない。

## Step 7: 手元の監視で実際の名前と式を確かめる（2026-09-29 17:33〜17:42）

- 環境（Q8 の決定どおり）: 配備したアプリを `docker compose stop app` で止め（17:33:07）、使い捨ての compose プロジェクト `mastersmith-obscheck`（アプリ `mastersmith:quality-followup`＝Step 5 の設定を含む今の作業ツリーから作った別のタグのイメージ、`grafana/otel-lgtm:0.34.0`、Mailpit）を起動した。仮の署名鍵・仮の管理者（`obs-admin@example.test`、パスワードは乱数で表示しない）の一時の環境ファイルはホームの下の権限 700 のディレクトリに置いた。外部エクスポートは使い捨てのアプリにだけ有効（送り先 `http://lgtm:4318`）。メールは使い捨ての Mailpit だけへ送った（宛先 `example.com`）。`mastersmith:local` のタグは作り直していない（配備したアプリのイメージは変えていない）。
- 要求: 約 8 秒ごとに「管理者のログイン・誤ったログイン（401）・トークンの更新（200）・`/api/admin/check`（204）・招待（201）」を 4 分余り（試しの 3 回 ＋ 本番 15 回、送信の周期 1 分を 4 回以上またぐ）。状態コードはすべて期待どおり（`401/200/204/201` が 18 回）。送信の結果は `mastersmith_mail_send_milliseconds_count{mail_outcome="sent"}` = 18（失敗 0）。監査は使い捨ての内部DB にだけ残り、消した。
- 実際の名前（Prometheus の `__name__`）: `http_server_requests_milliseconds_bucket`・`mastersmith_mail_send_milliseconds_bucket`（警報とダッシュボードの式の名前と同じ。式の名前の直しは不要だった）。
- `le` の値:
  - `http_server_requests_milliseconds_bucket`: 100・250・500・1000・2000・5000・+Inf（系列 42 本 = 6 系列 × 7）
  - `mastersmith_mail_send_milliseconds_bucket`: 100・250・500・1000・2000・5000・10000・+Inf（系列 8 本 = 1 系列 × 8）
  - **気づき（NFR3 に関わる）**: 同じ名前の前方一致で、Observation が作る長い処理の Timer（`http_server_requests_active_milliseconds_bucket`・`mastersmith_mail_send_active_milliseconds_bucket`）にも同じ境界のバケットが付いた（`le` は同じ境界と +Inf だけ、系列 14 本・8 本）。既定のバケット（数十個）は出ていない。`management.metrics.distribution.slo` の設定の名前は前方一致のため、`.active` だけを外すには Java の `MeterFilter` が要る（計画の Step 5 の「Java を足す前に止める」に当たる）ため、足していない。依頼者に確かめたいこと（系列が `.active` の分だけ増えるのを受け入れるか）。
- 式の値（トラフィックの直後、`[5m]`）:
  - 警報 `ms-login-p95` = 487.5（ms）、`ms-refresh-p95` = 95、`ms-check-p95` = 95。いずれも NaN ではない。
  - ダッシュボードのパネル 9（ログインの API）487.5・10（トークンの更新の API）95・11（確認用 API）95。
  - パネル 34（送信の時間）: 替える前の式（`traces_spanmetrics_latency_bucket`）0.01498…（秒）→ 替えた後の式（`mastersmith_mail_send_milliseconds_bucket`）95（ms）。
- O2（Q2: A）の反映: `docker/monitoring/dashboards/mastersmith-overview.json` のパネル 34 を `histogram_quantile(0.95, sum by (le) (rate(mastersmith_mail_send_milliseconds_bucket{service_name="mastersmith"}[5m])))` にし、単位を `s` → `ms`、目標の線を 3 → 3000、説明文を今の作りに直した。
- 計画との差: パネル 29（招待と登録の API ごとの時間）の説明文にあった「アプリの指標 http.server.requests はバケットを持たず p95 を出せない（次の Intent で直す）」が事実でなくなったため、説明文だけを直した（式はトレースの値のまま。Q2 の C は選ばれていないため）。
- 警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）は変えていない（名前が同じだったため）。
- `grafana/otel-lgtm` 0.34.0 の試し: Grafana（ダッシュボード「MasterSmith の概要」とフォルダーの読み込み、パネル 29・34 の式が直した形で読まれた）、警報の決まり 16 件の読み込み（すべて health ok）、Prometheus（OTLP の指標）、Loki（`{service_name="mastersmith"} |= "メールを送信しました"` が 18 件）、Tempo（`traces_spanmetrics_latency_bucket` が値を持つ）がすべて動いた → **取り込み**。`compose.yaml` の `lgtm` を `0.34.0` にした（Dependabot のブランチ `docker_compose/grafana/otel-lgtm-0.34.0` と同じ1行。コミットは計画の C9）。
- 片付け: 結果を見てから `down -v`（ボリューム2つとネットワークを消した）と一時ディレクトリの削除。配備したアプリを `docker compose start app` で起動し直し、healthy を確かめた（17:41:50。止めていたのは約 9 分）。イメージ `mastersmith:quality-followup` は残している（消すかは後で決める。秘密情報は含まない）。

## Step 8: README の手元の監視の記述

- `README.md` の「手元の監視（Grafana）」の「既知の欠け。次の Intent でアプリの設定で直す」の段落を、3つの項目に書き直した: (1) 2つの指標のバケットの境界と Prometheus での名前（`.active` にも同じ境界が付くことを含む。Step 7 の実測）、(2) p95 の警報3件とパネルがこのバケットで値を出すこと（境界の間は按分の見積もり）、(3) メールの送信の p95 のパネルは `mastersmith.mail.send` のバケット（Q2: A）、招待と登録の API ごとの時間のパネルはトレースの値のまま。
- README のほかの `95 パーセンタイル` の記述（DSL の操作、625 行）は変えていない（別の指標 `mastersmith.dsl.operation` で、この Intent の変更の外）。

## Step 9: make-you-chic-ui の固定先を上げる

- `vendor/make-you-chic-ui` の中で `git checkout 310e1ec` を行った（中身は変えていない。`vendor/make-you-chic-ui` の作業ツリーに変更なし）。
  - 更新の前: `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`（CatalogPage: 直近追加機能の使用例を追加）
  - 更新の後: `310e1ecf2fe8b4a388cb8ae098a59144dc93acc5`（Badge: variant-successの白文字コントラスト不足を修正）
  - 間のコミット: `7865c28`（テキストコントラスト不足3件(+関連2件)をWCAG AA対応）。一直線の履歴。
- 更新の中身（CSS だけ。部品の API は変わらない）: 新しいトークン `--color-primary-text`（blue・purple は `#fff`、green・orange は `gray-900`）・`--color-success-text`（`gray-900`）・`--color-primary-subtle-text`（ライト `brand-700`、ダーク `brand-400`）・`--color-danger-subtle-text`（ライト `red-600`、ダーク `red-400`）・`--color-danger-text`（ライト `--color-danger`、ダーク `red-400`）。Button・Badge（primary・success）・Avatar・Alert（danger）・FormField（誤りの文字・必須の印）がこれを使う。
- `./gradlew verifyPrepare`: BUILD SUCCESSFUL（`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`mustacheVendorUnchanged`）。`frontend/package.json`・`frontend/package-lock.json` は書き換わらなかった（`git diff` で差なし。A5 のとおり）。`frontend/node_modules/make-you-chic-ui` は `vendor` へのシンボリックリンクのため、作り直した `dist` がそのまま使われる（`dist/index.css` に `--color-primary-text` があることを確かめた）。
- コミットは C1（固定先だけ）として後で提案する。この手順ではコミットしていない。

## Step 10: E2E を流して既知の違反の一覧を合わせる

- 1回目（固定先 `310e1ec`、一覧は変更の前のまま。`./gradlew :backend:bootWar` で `frontendBuild` が作り直された WAR）: 050〜080 の 83 件のうち **52 passed・31 failed**。失敗はすべて「既知の違反が一覧と違います」で、一覧にある違反が **当たらなくなった** もの（想定外の違反は 0 件）。
  - 050: green・orange の light・dark の4組（`login-form-submit-button`・`login-language-switch-ja` の primary の Button）が消えた。
  - 060: green・orange の4組の `list`（`invitation-invite-button`）が消えた（その後の `inviteDialog` の `invitation-invite-submit` も、下の2回目で当たらないことを確かめた）。
  - 070: green・orange の4組の `ready`（`registration-submit-button`）が消えた。
  - 080: 20 組のうち 19 組が `preferences-ready` で失敗（アバターの `color-contrast avatar` が AVATAR_KNOWN_COMBOS の組で消えた、green・orange は `preferences-save-button` も消えた）。purple の light の組（アバターの既知が元から無い組）は、ready・invalid とも元から違反なしで通った。
- 変更（`frontend/e2e/support/axe.ts`）:
  - `KNOWN_VIOLATIONS` を空の一覧にした（仕組みの `splitKnownViolations` と、状態ごとの名前で置き換える引数はそのまま残す）。
  - `INVITATION_KNOWN_VIOLATIONS`・`REGISTRATION_KNOWN_VIOLATIONS`・`PREFERENCES_KNOWN_VIOLATIONS` の名前をすべての状態で空にした（型と状態の一覧は 060〜080 が使うため残す）。
  - 空になった専用の仕組みを外した: `AVATAR_KNOWN_COMBOS`・`AVATAR_KNOWN_VIOLATION`・`withAvatarKnownViolation`・`FORM_FIELD_ERROR_KNOWN_STATES`・`formFieldErrorTargets`・`withFormFieldErrorKnownViolation`。使われない関数は残っていない（`tsc`・oxlint・ESLint が通る）。
  - コメントに、U4 の既知の制約が make-you-chic-ui の `310e1ec` で当たらなくなったことを書いた。
- 変更（050〜080）: 冒頭のコメントを今の一覧（空）に合わせ、失敗の知らせの「make-you-chic-ui が直したなら」を外した。080 は `withAvatarKnownViolation`・`withFormFieldErrorKnownViolation`・`formFieldErrorTargets` の呼び出しと import を外し、`splitKnownViolations` だけにした。
- 2回目（直した一覧）: 050〜080 **83 passed**（050 が 21・060 が 21・070 が 20・080 が 21）、終了コード 0。この実行は `--reporter=list` を付けたため設定の報告の部品（json・秘密情報の確かめ）が動いていなかった。Step 12 の後に報告の部品を付けたまま 050〜100 を流し直した（下の Step 12 の記録）。→ 20 組・すべての状態（誤りの状態・2語の氏名のアバターを含む）で違反なし。**残った既知の違反は無い**。
- 090 は Step 12 の後に 100 と合わせて流す（下に記録）。
- 確かめ: Prettier（`e2e/`）・`npm run typecheck`・`npm run lint`（oxlint・ESLint）が通った。

## Step 11: アプリ独自の CSS の文字の色

- `frontend/src/features/preferences/PreferencesForm.css`: `.preferences-choice-error` を `color: var(--color-danger-text)` にした（make-you-chic-ui の `310e1ec` の新しいトークン。light は `--color-danger`＝red-500、dark は red-400）。
- `frontend/src/features/dsl/DslSubmitForm.css`: `.dsl-link` を `color: var(--color-primary-subtle-text)` にした（計画の候補。light は brand-700、dark は brand-400）。
- `frontend/src/app/pages/Page.css`: `.page-link` は残し（Q3: A）、`.dsl-link` と同じ `var(--color-primary-subtle-text)` にした。`NotFoundPage.tsx` は変えていない。
- 各規則の前に日本語の短いコメント（理由と FR の番号）を置いた。
- 選んだ色の計算の見込み（WCAG の相対輝度で計算。背景は light の `--color-bg` #fafafa・`--color-surface` #fff、dark の `--color-bg` #0b0f19・`--color-surface` #1f2937）:
  - `--color-primary-subtle-text`: light の blue 8.36/8.72・green 6.83/7.13・purple 8.35/8.72・orange 7.00/7.31、dark の blue 7.53/5.77・green 10.99/8.42・purple 7.25/5.56・orange 8.46/6.49。すべて 4.5:1 以上のため、代わりの `--color-text` は使わない（Step 12 の実際のブラウザの検査で確かめる）。
  - `--color-danger-text`: light の red-500 4.63/4.83、dark の red-400 6.92/5.31。
- 確かめ: Stylelint（2.4 の3ファイル）指摘なし、Prettier 通過、画面の単体テスト（`PreferencesForm`・`PreferencesPage`・`DslSubmitForm`・`NotFoundPage`）4 ファイル 48 件すべて通過。

## Step 12: アプリ独自の CSS のコントラストの E2E（100）

- 新規 `frontend/e2e/100-app-text-contrast.e2e.ts`（Apache License 2.0 のヘッダー、説明文は英語、コメントは日本語。流れではない検査のため E2E の本数に数えない）。20 組（`DISPLAY_COMBOS`）ごとに1つのテストで、次の3つの状態を axe（`runAxe`、WCAG 2.0・2.1 の A・AA）で検査し、違反が無いことを確かめる。対象の要素が出ていること（と `page-link`・`dsl-link` のクラス）を確かめてから検査する。
  1. プリファレンスの画面で「保存する」を押し、`PUT /api/me/preferences` の 400 の見本で、テーマの選択のまとまりの誤り（`.preferences-choice-error`）が出た状態。
  2. DSL の管理の画面の「投入」のタブの JSON Schema のリンク（`.dsl-link`、`dsl-submit-schema-link`）。
  3. 見つからない画面の「ホームへ」のリンク（`.page-link`、`not-found-home-link`。Q3: A）。
- 400 の見本は `frontend/e2e/support/preferencesFixtures.ts` の1か所に置き（`preferencesValidationProblem`、型 `PreferencesValidationProblem`）、画面の側の型（`ProblemDetails`・`ReadFieldError<PreferencesField>`）を付けた。形は `MeController.validationFailed`（code `VALIDATION_FAILED`、`fieldErrors: [{ field, reason }]`、reason は `FieldErrorReason` の名前）と画面の `readFieldErrors` で確かめて書いた（`theme`・`INVALID_VALUE`）。
- 書き込みの要求: ログイン以外の書き込みは差し替えた PUT の1件だけ（サーバーへ届かない）であることを数えて確かめる。投入・保存は送らない。
- 計画との差（書いてみて分かったこと）:
  - **組の当て方と移り方**: ログインの状態は利用者の保存した設定を持ち、ページを読み込み直すとそれに戻るため、ログインの直後や `page.goto` の後では組（テーマ・文字の大きさ）が当たらなかった（1回目: 20 組のうち 16 組が失敗。原因の確かめのための失敗）。080 と同じく先にプリファレンスの画面を開いて組を当て、その後は読み込み直さずに画面の中で移る（サイドバーの「DSL」、見つからない画面へは `history.pushState` と `popstate`）形にした。計画の状態の順（プリファレンス → DSL → 見つからない画面）は変えていないが、プリファレンスを最初に置いた。
  - **hover の見た目**: 「保存する」を押した後にマウスがボタンの上に残り、green・orange の組で primary の Button の hover（背景 brand-600 に文字 gray-900。計算で green 3.54:1・orange 3.43:1）が違反になった。検査は止まっている状態の色を見るため、押した後にマウスを (0,0) へ移す（`moveMouseAway`）ことにした。**make-you-chic-ui の Button の hover の文字のコントラスト不足は、この Intent の対象の外の新しい所見**（依頼者に確かめたいこと）。
  - **make-you-chic-ui の Tabs の新しい所見**: DSL の管理の画面の選ばれたタブ（make-you-chic-ui の Tabs、`.mycui-tab.active`、文字は `--color-primary`＝brand-500）が、**green・orange の light と blue・purple の dark の組** でコントラスト不足（実測。画面の中身の背景は `--color-bg` で、計算では light の #fafafa に対して green 3.16・orange 3.41、dark の #0b0f19 に対して blue 3.71・purple 3.56）。`310e1ec` の直しの外で、今までどの E2E も DSL の画面を axe で見ていなかったため見つからなかった。アプリ独自の CSS ではなくこのリポジトリから直せない（`vendor/` の変更は禁止）ため、100 の中だけで、その組・その状態（`dsl-schema-link`）・`color-contrast tab-1` だけを既知の違反（`TAB_KNOWN_VIOLATION`）として扱い、一覧どおりに当たることも確かめる（当たらなくなれば失敗）。`support/axe.ts` の一覧（primary の Button 用）には入れていない。**make-you-chic-ui 側への直しの依頼と、既知の違反として受け入れるかを依頼者に確かめたい**。
- 100 がアプリの CSS の誤りを捕まえることの確かめ（コードに残さない1回の確かめ）: 3つの CSS を一時的に変更の前の色（`--color-danger`・`--color-primary`）に戻して WAR を作り直し 100 を流すと、12 failed・8 passed。落ちたのは dark のすべての組の `.preferences-choice-error`（10 組）と、green・orange の light の組の `dsl-submit-schema-link`（2 組）。確かめの後に3つの CSS を直した後の内容へ戻し（写しと一致を `cmp` で確かめた）、WAR を作り直した。
- 結果（直した CSS、作り直した WAR）:
  - 100 だけ: 20 passed。
  - 報告の部品を付けたまま 050・060・070・080・090・100 を流した: **104 passed（3.6m）**、終了コード 0。秘密情報の確かめの報告の部品は「E2E の json の結果に仮の資格情報は含まれていません（3 項目を確かめた）」。json の文字列の検索でも、初期管理者のメールアドレスは 0 件（`example.com` の1件は既存の `webServer.env` の送信元 `e2e-noreply@example.com`、`password` は状態の名前 `password-ready` などと測りの項目の名前）。
- 確かめ: Prettier・`npm run typecheck`・`npm run lint`（oxlint・ESLint）が通った。

## Step 13: README の画面の記述

- 「画面の表示の設定（U4）」の「既知の制約（ブランドカラーのコントラスト）」を書き直した: 前の Intent で受け入れた3つ（primary のボタン・アバター・dark の誤りの文字）が `310e1ec` で解消したこと（050〜080 の実測）、`310e1ec` の primary のボタンの文字の色とコントラスト比の表（計算の値。blue 5.17・purple 5.38 は白、green 5.38・orange 4.98 は濃い灰 #111827）、アバターと誤りの文字のトークン、アプリ独自の CSS の3つの色（Step 11）。残る既知の制約として、100 で見つかった **Tabs の選ばれたタブ**（green・orange の light、blue・purple の dark）と、検査の外の **primary のボタンの hover**（green・orange）を書いた（どちらも依頼者の受け入れは未確認。下の「依頼者に確かめたいこと」）。
- 「ビルドした WAR での画面の確認（E2E）」: 表に 100 を足し、050〜080 の既知の違反の記述を今の一覧（空）に合わせ、節「100 について」を足した。
- `.preferences-choice-error` の色の記述（計画の「949 行付近」、前の U4 の既知の制約の「ダークのテーマの項目の誤りの文字」の項）は、書き直した節の中で `--color-danger-text` に直したと書いた。
- 計画との差（計画の Step 13 の列挙より広い）: 画面ごとの節（U5・U6・U7 の「既知の制約」、U8 の「コントラストの既知の制約」）も、解消したことと残る既知の制約に合わせて直した（そのままでは解消した制約を「足りない」と書いたまま残るため）。
- `README.md` はもともと Prettier の対象の外（変更の前から Prettier の書式に合っていない）で、書式の検査はしていない。

## Step 14: 接続が 0 本になるまでの待ちの延長と診断

- `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java`:
  - `ZERO_CONNECTIONS_WAIT` を `Duration.ofSeconds(10)` → `Duration.ofSeconds(30)` にした（Q5）。定数を使う4か所（変更の前の 196・235・251・264 行、変更の後の 205・244・260・274 行）すべてが 30 秒になる。Javadoc に、運用の道具（`docker/hikari-pool.sh` の `--zero-wait`、既定 10 秒）とテストの上限が違うこと・その理由（CI の時間切れ、FR3.1。原因は確かめていない、F1: B）を書いた。道具の既定は変えていない。
  - 接続の数を待つ3か所（変更の前の 196・251・264 行）を、新しい private の補助 `awaitPool(String expectation, Callable<Boolean> condition)` に通した。Awaitility の `ConditionTimeoutException` を受けたら、`HikariPoolMXBean` の `getTotalConnections`・`getActiveConnections`・`getIdleConnections`・`getThreadsAwaitingConnection` と待ちの上限（ミリ秒）・期待の説明（英語の固定の文）をキーと値で ERROR のログに出し、同じ値を文言に入れた `AssertionError`（元の例外を原因に付ける）を投げ直す。握りつぶさない。
  - 失敗の知らせの形: `pool condition not met within 30000 ms (all connections closed after suspend and evict): total=…, active=…, idle=…, threadsAwaiting=…`。`exceptionFormat` が FULL のため、CI の Gradle の出力に文言がそのまま出る。
  - 出す値は数と固定の文だけ（NFR5）。接続先・利用者・SQL・スレッドの名前は出さない。
  - 235 行（変更の後の 244 行）の「一時停止の待ちに入るまで」の待ちは接続の数の待ちではないため補助に通さず、定数の変更で 30 秒になるだけにした（計画どおり。診断は3か所）。
- 計画との差: なし。K-4 が勧めていた「スレッドの状態」は、計画と要件（NFR5: 数だけ）に合わせて出していない。
- 確かめ: `./gradlew :backend:spotlessApply` の後に `spotlessCheck` が通った（差は上の1ファイルだけ）。ライセンスヘッダーはそのまま。

## Step 15: 接続の待ちのテストを流す

- `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.config.H2CompactionByPoolSuspensionIT'`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）: BUILD SUCCESSFUL。結果の XML で tests=3・skipped=0・failures=0・errors=0、クラス全体 8.355 秒（3件は 0.671・0.201・0.277 秒）。
- 診断がわざと時間切れのときに出ることの確かめは、計画の 7節どおり Build and Test で1回だけ行う（コードに残さない）。この段では行っていない。

## Step 16: 招待の画面のテストの1件の上限と診断

- `frontend/src/features/invitation/InvitationAdminPage.test.tsx`:
  - ファイルの先頭に定数 `LEAK_CHECK_TIMEOUT_MS = 15_000` を置き、「keeps addresses and names out of storage, the URL and the console in every flow」の `it` の第3引数に渡した。ほかのテストと `vitest.config.ts` の既定（5 秒）は変えていない。
  - テストの始めに `performance.now()` で時刻を取り、Vitest の `onTestFailed` で、失敗したときに `process.stderr.write` で `[diagnostic] "<テストの名前>" failed after <ms> ms (timeout 15000 ms)` を1行出す。`console` は見張りの対象のため使わない。メールアドレス・氏名は出さない（NFR5）。`vitest` の import に `onTestFailed` を足した。
  - `it` の第3引数を足したため、Prettier がテストの本体を1段深く字下げし直した（差分の行数は大きいが、本体の中身は変えていない）。
- 確かめ: Prettier（`--check` 通過）・`npm run typecheck`・`npm run lint`（oxlint・ESLint）が通った。絞ったコマンド（`NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/invitation/InvitationAdminPage.test.tsx`）で 29 件すべて通過（全体 5.13 秒）。該当の1件だけを流すと 1,442 ms（手元）。
- 計画との差: なし。

## Step 17: team.md の決まりとの差（code-summary.md にそのまま移す節）

以下を、Step 26 で `code-summary.md` の節「team.md の決まりとの差（FR3.4）」として移す。Build and Test の `test-results.md` にも同じ内容に CI の結果を足して書く（計画の 7節）。

### team.md の決まりとの差（FR3.4）

- **対象の決まり（`team.md`）**
  1. Testing Posture: 「不安定なテストは放置せず、原因を直すまで統合しない。」
  2. Way of Working: 「CI は統合後の再確認として動かす。CI が失敗したら、次の Bolt に進む前に原因を直す。」
- **今回の判断**: 前の Intent（260925-user-management）の CI（`66fe981`）で `verify` が2回とも別々のテストの時間切れで失敗した2件について、原因は確かめず、待ちの上限を延ばすことを直しとして統合する（要件の Q1・F1: B、FR3.4）。
  - `H2CompactionByPoolSuspensionIT`: 接続の数の待ちの上限 `ZERO_CONNECTIONS_WAIT` を 10 秒 → 30 秒（4か所すべて。FR3.1）。
  - `InvitationAdminPage.test.tsx` の1件: 上限を Vitest の既定の 5 秒 → 15 秒（この1件だけ。FR3.2）。
  - どちらも、次に落ちたときに原因を切り分ける手がかりとして、失敗の知らせに診断を入れた（FR3.3）。
- **差**: 決まり1は「原因を直すまで統合しない」、決まり2は「原因を直す」ことを求めるが、今回は原因を確かめないまま統合する。上限を延ばすことは、原因が「上限が CI の runner の速さに対して短すぎた」ことである場合に限って原因の直しになる。それ以外の原因であれば、失敗の頻度を下げるだけで原因は残る。
- **前提 A1（要件）**: CI の2件の時間切れは、上限が CI の runner の速さに対して短すぎたことによる、とみなす。原因は確かめない（F1: B）。確かめられるのは、上限を延ばした後に通ること（NFR2。持ち主は Build and Test）と、次に落ちたときの診断だけである。
- **直さない（確かめていない）原因の候補**
  - `H2CompactionByPoolSuspensionIT`（K-4、コード知識ベース `architecture.md` の Interaction Diagrams 3。どれも未検証）:
    - (a) 一時停止の直前に動いていた HikariCP の接続の補充が、破棄の後に接続を1本足し、0 本にならない。
    - (b) 同じ文脈の定期の処理や起動の後の処理が接続を借りていて、返すのが遅れた（破棄の印を付けられた接続は、返されるまで数に残る）。
    - (c) CI の runner が遅く、最後の接続を閉じるときの H2 の処理が 10 秒を超えた。
  - `InvitationAdminPage.test.tsx` の1件（K-5、`code-quality-assessment.md`。未検証）: 1つのテストの操作が多く（招待3回で 36 文字のメールアドレスを `user.type` で1文字ずつ入力・送り直し2回・取り消し2回・ページ送り）、CI の runner で既定の 5 秒すれすれになる（前の Intent の CI の1回目は 4,583 ms で通り、2回目で 5 秒を超えた。手元では該当の1件が 1,442 ms）。上限の延長のほかの直し方の候補（`user.paste` への置き換え、流れを複数のテストに分ける）は採っていない。
- **次に落ちたときの手がかり（FR3.3）**
  - `H2CompactionByPoolSuspensionIT`: 失敗の知らせ（`AssertionError` の文言。CI の Gradle の出力に FULL で出る）と ERROR のログに、待ちの上限（ミリ秒）・期待の説明と、プールの全体・使用中・空きの接続の数・接続を待っているスレッドの数が出る。見方の目安: 使用中が 1 以上なら (b)（借りたまま返っていない）、空きが 1 以上なら (a)（補充で足された接続が残った）、全体が 0 に近いのに時間切れなら (c) を疑う（どれも目安で、確かめたものではない）。
  - `InvitationAdminPage.test.tsx`: 失敗したときに標準エラーへ、テストの名前とかかった時間（ミリ秒）と上限が1行出る。15 秒に近い時間で落ちたなら、上限をさらに延ばすのではなく、操作の数を減らす直し（K-5 の候補）を検討する材料になる。
- **確かめの持ち主**: 診断がわざと時間切れのときに出ることの1回の確かめと、依頼者の `git push` の後の CI の `verify` が通ること（NFR2）は Build and Test（計画の 7節）。

## 依頼者の決定 G2: C による追加（primary のボタンの hover の検査。計画に無い変更）

- **計画との差**: 計画の Step 12 は hover の状態を検査していなかった（Step 12 の記録のとおり、押した後にマウスを外して止まっている状態の色だけを見た）。依頼者は、make-you-chic-ui の `310e1ec` の直しの外に残る2件（所見1 Tabs の選ばれたタブ、所見2 primary のボタンの hover）を **2件とも既知の制約として受け入れ**、あわせて **ボタンの hover も E2E で検査する** と決めた（G2: C）。この節の変更はその決定による計画の外の追加で、承認済みの計画と `unit-test-instructions.md` は書き換えていない。
- **変更**:
  - `frontend/e2e/100-app-text-contrast.e2e.ts`: 状態 `primary-button-hover` を足した。プリファレンスの画面で選択のまとまりの誤りを検査した後、「保存する」（`preferences-save-button`、primary の Button）にマウスを重ね、計算された背景の色が止まっているときの色から変わったこと（hover の背景 `--color-primary-hover` が当たったこと。make-you-chic-ui の Button に遷移は無い）を確かめてから axe で検査し、マウスを外して背景が戻ることも確かめる。要求は送らない（書き込みの要求の数の確かめは変わらず 1 件）。
  - 既知の違反の扱いを一般化した: `TAB_KNOWN_VIOLATION`（1件）を、組・状態・名前の一覧 `STATE_KNOWN_VIOLATIONS`（Tabs と hover の2件）に替えた。当たるはずの組で当たらなければ（当たらなくなったときも）失敗する作りは同じ。
  - `README.md`: 「100 について」、U8 の「コントラスト」、U4 の「残る既知の制約」の hover の項を、検査するようになったこと・受け入れたこと・当たる組・make-you-chic-ui への直しの依頼が次の Intent の候補であることに合わせて直した。`TAB_KNOWN_VIOLATION` の名前の記述を `STATE_KNOWN_VIOLATIONS` に直した。
- **実測の違反の組**（100 の json の結果の注記 `axe` から。hover の状態）: `color-contrast preferences-save-button` が **green の light・dark、orange の light・dark の4組**（組の名前では `(b) green light md`・`(b) green dark md`・`(b) orange light md`・`(b) orange dark md`）で当たり、ほかの規則・ほかの要素の違反は無かった。blue・purple（`(a)`・`(c)` の blue の 12 組と `(b)` の blue・purple の4組）は hover でも違反なし。計算の見込み（背景 brand-600 に文字 gray-900: green 3.54:1・orange 3.43:1、テーマに依らない）と一致した。`--color-primary-hover` は make-you-chic-ui の `src/theme/semantic.css` でテーマに依らず `var(--brand-600)` の1か所だけで決まっている。
- Tabs の既知の違反の組（green・orange の light、blue・purple の dark の `dsl-schema-link` の `color-contrast tab-1`）は前の記録と同じで変わっていない。
- **次の Intent の候補**: make-you-chic-ui のリポジトリ側への直しの依頼（所見1: Tabs の選ばれたタブの文字の色、所見2: primary の Button の hover の背景と文字の組）。直った版に固定先を上げたら、100 の `STATE_KNOWN_VIOLATIONS` から外す（当たらなくなると 100 が失敗して気づける）。`vendor/` はこのリポジトリから変えない。
- **確かめ**:
  - Prettier・`npm run typecheck`・`npm run lint`（oxlint・ESLint）が通った。
  - 100 だけ（`./gradlew :backend:bootWar` の後）: **20 passed（42.6s）**。
  - 報告の部品を付けたまま 050・060・070・080・090・100 を流した（Mailpit は起動したまま）: **104 passed（3.7m）**。秘密情報の確かめの報告の部品は「E2E の json の結果に仮の資格情報は含まれていません（3 項目を確かめた）」。json の文字列の検索で、初期管理者のメールアドレス（`admin@`）・`Bearer`・`eyJ` は 0 件、メールアドレスの形は既存の送信元 `e2e-noreply@example.com` の1件だけ。資格情報は `frontend/playwright.config.ts` がプロセスの環境変数で作って渡す（`webServer.env` には置かない）。

## Step 18: 試し方と判定の基準（当てたもの）

- ブランチは統合せず、各ブランチの版の変更だけを `fix/260928-quality-followup` の上に手元で当て直す。当てる前のファイル（`gradle/libs.versions.toml`・wrapper の4つ・`backend/gradle.lockfile`・`frontend/package.json`・`package-lock.json`・`compose.yaml`・`TargetDbImages.java`・`.github/dependabot.yml`）の写しを scratchpad に取り、通らなかったときはその写しから戻す（`git stash` などは使わない）。
- lockfile の作り直しは `backend/build.gradle.kts` の注記どおり `./gradlew :backend:resolveAndLockAll --write-locks`（計画の `:backend:dependencies --write-locks` と同じ役目の、このリポジトリの入口）。
- 判定の区分と関門は計画の Step 18 のとおり。関門の OSV-Scanner は計画の文言どおり「**新しい** High 以上が無い」（その更新が持ち込んだ High が無い）で判定する（下の事情のため）。

### 着手の時点で見つかった、更新と関係の無い OSV-Scanner の High（依頼者に確かめたいこと）

- `verify` の `osvScan` が、既存の `tools.jackson.core:jackson-databind 3.1.5`（Spring Boot 4.1.1 の管理の版。lockfile の行は変更の前から同じ）に **GHSA-q4xh-88c3-wmh7（CVSS 7.5、High。Duration・XMLGregorianCalendar の数の解析の DoS）** を見つけて失敗した。あわせて警告（Medium）の GHSA-gx83-3vf8-gh7j・GHSA-wjgm-6hv5-3cvf。3件とも公開は 2026-09-28（20:19Z〜20:44Z）で、直った版は 3.1.6・3.2.2。
- 変更の前の lockfile の写し（`develop` の `backend/gradle.lockfile` とバイト単位で同じ）を `osv-scanner scan source -L` で直接検査しても同じ3件が出た → **今回の更新が持ち込んだものではなく、`develop` の上でも今は `verify` が通らない**。Step 2 の基準の `verify` が通った後に OSV のデータベースに載ったと見られる。
- 関門を緩める・除外することはしていない。この High のために、この後の手順（Step 25 の最後の `verify`）と統合は、これを取り込むまで通らない。`team.md` の「重大度 High 以上の知らせは、次の Bolt に入る前に取り込む」に当たる。取り込み方（例: Jackson を 3.1.6 に上げる版の指定、または Spring Boot の修正版を待つ）は計画の外のため、この段では行わず、依頼者に確かめる。
- そのため、この後の更新の判定は「その更新が OSV-Scanner に新しい High を持ち込まない、かつ `osvScan` のほかの関門（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト（対象DB を含み SKIPPED なし）・カバレッジの下限・SpotBugs・Gitleaks）がすべて通る」で行う。`osvScan` の失敗で Gradle が止まり `spotbugsGate` が流れないため、`--continue` を付けて流し直して SpotBugs の関門の結果も取る。

## Step 19（1）: Gradle の小さな更新（archunit 1.5.1・snakeyaml 2.7・Gradle wrapper 9.8.0）

- 当てたもの: `gradle/libs.versions.toml` の `archunit = "1.5.1"`・`snakeyaml = "2.7"`。wrapper は `./gradlew wrapper --gradle-version 9.8.0` を2回（1回目は 9.7.1 が properties だけを書き換え、2回目に 9.8.0 が自分の jar とスクリプトを書く）。結果の `gradle-wrapper.jar`・`gradle-wrapper.properties`・`gradlew`・`gradlew.bat` は Dependabot のブランチの4つとバイト単位で同じ（今の properties の書き方 `networkTimeout`・`retries` などはそのまま）。
- lockfile の差（`resolveAndLockAll --write-locks`）: archunit の5行が 1.5.0 → 1.5.1、snakeyaml 2.6 → 2.7。**推移依存の引き上げ**: archunit 1.5.1 がテストのクラスパスの `org.slf4j:slf4j-api` を 2.0.18 → 2.0.19 に引き上げた（`dependencyInsight` で確かめた。本番のクラスパス `productionRuntimeClasspath`・`runtimeClasspath` と `spotbugs` は 2.0.18 のまま。修正版の差でテストだけ）。`settings-gradle.lockfile` は Gradle 9.8.0 が生成の案内のコメント行（`# To regenerate this file, run: ./gradlew dependencies --write-locks`）を書かなくなった1行の差だけ。
- 新しく入る依存は無い（ライセンスの確かめは不要。archunit・snakeyaml は Apache License 2.0 のまま）。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima・環境変数あり）: `osvScan` の前のすべての段が通った。単体 1234・結合 565（失敗 0・SKIPPED 0。対象DB を含む）、画面 732 件（Test Files 91）、カバレッジはバックエンド行 98.78%・分岐 94.38%、画面 Lines 97.44%・Branches 92.67%（Step 2 と同じ。結合テストは Step 6 の `HistogramBucketsIT` の3件で 562 → 565）。Gitleaks は leaks なし。`osvScan` は上の既存の jackson-databind の High だけで失敗（新しい指摘は無し。npm の警告 undici も既存の vendor の開発用）。`--continue` での流し直しで `spotbugsGate` も通った（警告の行数は Step 2 と同じ 155）。
- **判定: 取り込み**（その更新が持ち込んだ失敗は無い。`verify` 全体は既存の High のため通っていないことを明記する）。

## Step 19（2）: networknt-json-schema-validator 3.0.7（決まりとぶつかる更新）

- 当てたもの: `networknt-json-schema-validator = "3.0.7"`、lockfile を作り直した。
- lockfile の差（推移依存の引き上げ）: `tools.jackson.core:jackson-core`・`jackson-databind`・`tools.jackson:jackson-bom` が **3.1.5 → 3.2.1**、`com.fasterxml.jackson.core:jackson-annotations` が **2.21 → 2.22**。本番のクラスパス（`productionRuntimeClasspath`）も含めて、アプリ全体の Jackson が Spring Boot 4.1.1 の管理の版（3.1.5）から外れる（`dependencyInsight`: 3.2.1 は networknt 3.0.7 と jackson-bom 3.2.1 から。logstash-logback-encoder の 3.0.1・flyway-core の 3.1.1 の要求も 3.2.1 に寄せられる）。新しい部品は無い（Jackson・networknt は Apache License 2.0 のまま）。
- OSV-Scanner: jackson-databind 3.2.1 にも同じ3件（GHSA-q4xh-88c3-wmh7 High・ほか Medium 2件）が当たる（直った版は 3.2.2・3.1.6）。**新しい指摘は無いが、この更新で既存の High は直らない**。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify --continue`: バックエンドは単体 1234・結合 565（失敗 0・SKIPPED 0）、カバレッジは Step 2 と同じ（行 98.78%・分岐 94.38%）。画面の単体テストで **1件だけ失敗**: `src/app/display-settings/DisplaySettingsProvider.test.tsx` の「applies saved preferences at once: text, lang, request language and name」（325 行、`act` の直後に同期で `Home` を期待し `ホーム` のままだった）。networknt はバックエンドだけの依存で画面の依存と成果物は変わっていないため、この更新と関係の無い失敗と見立てた。そのファイルだけを8回流すとすべて通り（15 件）、`verify --continue` を流し直すと画面 732 件すべて通った（Test Files 91）。原因は確かめていない（見立て: 全体の並行の負荷のもとで、言語の切り替えの反映が `act` の後の同期の確かめに間に合わなかった）。**不安定なテストの疑いとして依頼者に確かめたいこと**（`team.md` の「不安定なテストは原因を直すまで統合しない」。直すのは計画の外）。
- 流し直しで Gitleaks（leaks なし）・`spotbugsGate`（警告の行数は Step 2 と同じ 155）が通った。`osvScan` は既存の High だけで失敗。
- **判定: 取り込み（決まりを変える）**。変える決まり: `project.md` の学び（`260923-dsl-schema-loader:nfr-requirements`）「networknt の最新の版 3.0.7 はアプリ全体の Jackson を Spring Boot の版から引き上げるため、Spring Boot と同じ 3.1 系で動く 3.0.6 を選んだ」。取り込むと、アプリ全体の Jackson が Spring Boot 4.1.1 の管理の版 3.1.5 から 3.2.1 に上がる（全テストは通った）。学びの手順で `project.md` に反映するかを依頼者に確かめる（メモリは直接書き換えていない）。**注意**: Jackson の High を 3.1.6 で直す場合（上の「着手の時点で見つかった High」）、この更新を取り込んでいると 3.1 系には戻せず、3.2.2 以上が要る。取り込みの判定は、この High の直し方と合わせて依頼者に確かめたい。

## Step 20（1）: npm の小さな更新（prettier 3.9.9・vite 8.3.1）

- 当てたもの: `frontend` で `npm install --save-dev prettier@^3.9.9 vite@^8.3.1`（`package.json` の範囲は Dependabot のブランチと同じ `^3.9.9`・`^8.3.1`）。`package-lock.json` の差は prettier 3.9.8 → 3.9.9、vite 8.3.0 → 8.3.1 と、vite の要求の rolldown の範囲（`~1.2.6` → `~1.2.9`。lockfile は既に 1.2.9）だけ。新しく入る部品は無い（ライセンスは MIT のまま）。`npm ci` で入ることを確かめた。
- prettier の上げでフォーマットの差は出なかった（`npx prettier --check .` 通過）。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify --continue`: 単体 1234・結合 565（失敗 0・SKIPPED 0）、画面 732 件、カバレッジは Step 2 と同じ。Gitleaks・`spotbugsGate`（警告の行数 155）が通った。`osvScan` は既存の jackson-databind の High だけ（新しい指摘なし。`config/npm-build-tools.txt` の道具の指摘も無い）。
- vite は成果物を作る道具のため、E2E（`./gradlew e2eTest` 相当）を npm の更新の後にまとめて流す（下に記録）。
- **判定: 取り込み**。

## Step 20（2）: typescript 7.0.2

- 当てたもの: `npm install --save-dev typescript@^7.0.2`。ERESOLVE（peer の不一致の上書き）の警告が出て、`@typescript-eslint/parser` の peer の範囲 `>=4.8.4 <6.1.0` に対して `typescript@7.0.2` が invalid になった（`npm ls`）。
- `npm run typecheck`（`tsc --noEmit`）は通ったが、`npm run lint` の ESLint が「typescript-eslint does not support TS 7.0.」で止まった。npm の最新の `@typescript-eslint/parser` 8.71.0 も peer は `<6.1.0` で、typescript-eslint を上げても直らない（2026-09-29 の時点）。
- 計画どおりコードと設定を直さず、`verify` を流す前に止めた。`frontend/package.json`・`package-lock.json` を小さな更新の後の写しに戻し、`npm ci` で typescript 6.0.3 に戻ったこと・lint が通ることを確かめた。
- **判定: 通らずに見送り**（段: リンタ。理由: typescript-eslint が TS 7 に未対応）。`dependabot.yml` の `ignore` には入れない（typescript-eslint が対応すれば通りうるため）。

## Step 20（3）: vitest 5.0.2 と @vitest/coverage-v8 5.0.2（1組）

- 当てたもの: `npm install --save-dev vitest@^5.0.2 @vitest/coverage-v8@^5.0.2`（範囲は Dependabot のブランチと同じ）。入れる途中に、古い木（vitest 4.1.11 の peerOptional `@vitest/coverage-v8@4.1.11`）からの ERESOLVE の警告が出たが、入った後の `npm ls` に invalid・エラーは無く、`npm ci` で入った。
- 新しく入る部品: `@vitest/istanbul-lib-coverage@1.0.2`・`@vitest/istanbul-lib-report@1.0.2`（どちらも MIT。16 個が外れた）。開発時だけの依存。
- 設定（`vitest.config.ts`・`vitest.setup.ts`）は書き換えずに動いた。下限（`thresholds` の行 80・分岐 70）が効いていることを、ファイルを変えずにコマンド行で下限を 99% にして失敗する（`Coverage for lines (97.44%) does not meet global threshold (99%)`）ことで確かめた。
- `npm run test:coverage`: Test Files 91・Tests 732 すべて通過、Lines 97.44%（2322/2383）・Branches 92.67%（1443/1557）・Statements 97.21%（Step 2 と同じ）。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify --continue`: 単体 1234・結合 565（失敗 0・SKIPPED 0）、画面 732 件、カバレッジは Step 2 と同じ。Gitleaks・`spotbugsGate`（155）が通った。`osvScan` は既存の jackson-databind の High だけ。
- **判定: 取り込み**（2本のブランチ `vitest-5.0.2`・`vitest/coverage-v8-5.0.2` を1つの判定にする）。

## Step 20（4）: npm の更新の後の E2E

- 取り込んだ npm の更新（prettier 3.9.9・vite 8.3.1・vitest 5.0.2・@vitest/coverage-v8 5.0.2）と Gradle の更新を入れた状態で `./gradlew e2eTest`（Mailpit は起動したまま）: **110 passed（3.9m）**、BUILD SUCCESSFUL（010〜090 の 90 件と 100 の 20 件）。秘密情報の確かめの報告の部品は「含まれていません（3 項目）」、json の文字列の検索で初期管理者のメールアドレス・`Bearer`・`eyJ` は 0 件。

## Step 21: 対象DB のイメージの更新（postgres 18.6 の digest・mysql 26.7.0・mariadb 13.0.2）

- 新しい版と digest は Dependabot のブランチの `compose.yaml` の差から読んだ（どれも複数の CPU をまとめた一覧の digest）。`compose.yaml` と `TargetDbImages` の `*_VERSION`・`*_DIGEST` を一緒に上げた。
  - postgres: `18.6@sha256:86c951e0…` → `18.6@sha256:5a5a84b1…`（版は同じ、digest だけ）。
  - mysql: `8.4.11@sha256:0744ee5e…` → `26.7.0@sha256:ade067ae…`。
  - mariadb: `11.8.9@sha256:79d59758…` → `13.0.2@sha256:d4fdec05…`。
- 1つずつ当てて、そのたびに対象DB の結合テスト（`./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'`）を流した: postgres の digest だけ → 50 件、mysql を足して → 50 件、mariadb を足して → 50 件（どれも失敗 0・SKIPPED 0。MySQL・MariaDB・PostgreSQL のそれぞれの `*SchemaQueriesIT`・`*TargetSchemaReaderIT` と `TargetDbStartupIT`・`TargetDbSecretLeakIT`）。取得したイメージの環境変数で、使われた版が `PG_VERSION=18.6-1.pgdg13+2`・`MYSQL_VERSION=26.7.0-1.el9`・`MARIADB_VERSION=1:13.0.2+maria~ubu2604` であることを確かめた。
- 計画との差（試し方）: `verify` は3つを1つずつ流さず、3つの結合テストを1つずつ通した後に、3つを入れた状態で1回流した（1つずつの切り分けは対象DB の結合テストで済んでいるため）。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify --continue`: 単体 1234・結合 565（失敗 0・SKIPPED 0）、画面 732 件、カバレッジは Step 2 と同じ。Gitleaks・`spotbugsGate`（155）が通った。`osvScan` は既存の jackson-databind の High だけ（イメージは OSV-Scanner の対象の外）。
- 見本の対象DB（compose の profile）での起動の確かめは、計画どおり結合テストで代えた（起動していない）。
- **決まりを変えた（FR5.2）**: `TargetDbImages` の Javadoc の「長く支援される版（8.4・11.8 の系列）」を、今の版の系列（26.7・13.0）と、長く支援される版に限る決まりを外して結合テストで確かめて上げたことに書き直した。26.7・13.0 が長く支援される版かどうかは確かめていない（そう書いていない）。学びの手順で依頼者に確かめる。
- **判定: 3つとも取り込み**（mysql・mariadb は決まりを変える）。

### 依頼者の指示による計画との差: `docker/perf/compose.yaml` も同じ値にそろえた

- 依頼者の指示（進行役を通じて受けた。依頼者の確認は済み）で、負荷の試験の使い捨ての環境 `docker/perf/compose.yaml` の対象DB のイメージの行（postgres・mysql・mariadb）も、`compose.yaml` と `TargetDbImages` と同じ値に上げた。計画の 3節の影響の範囲に無いファイルで、Dependabot も更新していなかった（`dependabot.yml` の docker-compose は `directory: /` だけを見る）。
- 取り込んだものだけを上げた（3つとも取り込みのため3行とも）。ほかのイメージ: `docker/perf/compose.yaml` には lgtm が無く（`grafana/otel-lgtm` 0.34.0 は `compose.yaml` だけの行）、mailpit は両方とも `axllent/mailpit:v1.31.2@sha256:74d609a4…` で同じだった。アプリのイメージ（`mastersmith:${MASTERSMITH_IMAGE_TAG:-local}`）はそろえる対象の外。
- `TargetDbImages` のクラスの Javadoc に「負荷の試験の使い捨ての環境（docker/perf/compose.yaml）も同じ値にそろえる」を足した。
- 3つのファイルの値の一致を、`TargetDbImages` の定数から `<版>@<ダイジェスト>` を組み立てて2つの compose の `image:` の行と比べて確かめた: postgres・mysql・mariadb の3つとも、`compose.yaml`・`docker/perf/compose.yaml` の両方で一致。mailpit も一致。
- `docker/perf/compose.yaml` は一時の環境ファイルの場所（`MASTERSMITH_PERF_ENV_FILE`）が無いと `docker compose config` で読めないため、行の置き換えだけで、起動はしていない（負荷の試験は行っていない）。

## Step 22: 見送る更新と `dependabot.yml`（Q1: A）

- eclipse-temurin 26.0.2_10 と opentelemetry-logback-appender 2.31.1-alpha は試さずに見送った（FR5.4、F2: C。理由は計画の Step 22 のとおり）。`Dockerfile` と `libs.versions.toml` の `opentelemetry-instrumentation` は変えていない。
- `.github/dependabot.yml` に `ignore` を足した（外す時期をコメントに書いた）:
  - gradle: `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`（すべての更新）。外す時期: Spring Boot を上げるとき。`opentelemetry-instrumentation` の版の参照を使う部品はこの1つだけ（`libs.versions.toml` の 79 行）。
  - docker: `eclipse-temurin` の `version-update:semver-major`（25 の中の小さな更新は知らせる）。外す時期: JDK をビルド・CI とそろえて上げるとき。
- 「通らずに見送り」の typescript 7.0.2 は `ignore` に入れていない。
- YAML として読めることを確かめた（gradle・docker の2つに `ignore`）。

## Step 23: 更新の一覧（15 本）

Step 3 で確かめ直した `origin/dependabot/*` は 15 本（計画の 8節と同じ）。GitHub で開いているプルリクエストの数は AI では確かめていない（依頼者に画面で確かめてもらう。前の記録の 11 件との差。R-03）。「関門」は、フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト（対象DB を含み SKIPPED なし）・カバレッジの下限・Gitleaks・SpotBugs が通り、OSV-Scanner にその更新が持ち込んだ新しい High が無いこと。どの `verify` も、更新と関係の無い既存の jackson-databind の High（GHSA-q4xh-88c3-wmh7）で `osvScan` だけが失敗している（上の Step 18 の節）。

| ブランチ（`origin/dependabot/` を省く） | 更新 | 判定 | 理由・確かめた関門 |
|---|---|---|---|
| docker/eclipse-temurin-26.0.2_10-jre-noble | eclipse-temurin 25.0.4_7 → 26.0.2_10 | 見送り | JDK 25 のビルド・CI と版が分かれ、`verify` はイメージを作らず確かめられない（F2: C）。`ignore` に大きな版を入れた |
| docker_compose/grafana/otel-lgtm-0.34.0 | grafana/otel-lgtm 0.33.1 → 0.34.0 | 取り込み | Step 7 の手元の監視の確かめ（Grafana・警報 16 件・Prometheus・Loki・Tempo）が通った。`compose.yaml` だけの行 |
| docker_compose/mariadb-13.0.2 | mariadb 11.8.9 → 13.0.2 | 取り込み（決まりを変える） | 対象DB の結合テスト 50 件と `verify` の関門が通った。`compose.yaml`・`TargetDbImages`・`docker/perf/compose.yaml` を同じ値に。長く支援される版の決まりを外した |
| docker_compose/mysql-26.7.0 | mysql 8.4.11 → 26.7.0 | 取り込み（決まりを変える） | 同上 |
| docker_compose/postgres-18.6 | postgres 18.6 の digest | 取り込み | 同上（版は同じで digest だけ） |
| gradle/com.networknt-json-schema-validator-3.0.7 | networknt 3.0.6 → 3.0.7 | 取り込み（決まりを変える） | `verify` の関門が通った（画面の1件の失敗は無関係と見立て、流し直しで通った）。アプリ全体の Jackson が 3.1.5 → 3.2.1（Spring Boot の管理の版から外れる）。Jackson の High の直し方と合わせて依頼者に確かめる |
| gradle/com.tngtech.archunit-archunit-junit5-1.5.1 | archunit 1.5.0 → 1.5.1 | 取り込み | Gradle の小さな更新とまとめた `verify` の関門が通った。テストだけの slf4j-api 2.0.18 → 2.0.19 の引き上げ |
| gradle/gradle-wrapper-9.8.0 | Gradle wrapper 9.7.1 → 9.8.0 | 取り込み | 同上。作り直した4つのファイルは Dependabot のブランチと同じ |
| gradle/io.opentelemetry.instrumentation-opentelemetry-logback-appender-1.0-2.31.1-alpha | logback-appender 2.28.1-alpha → 2.31.1-alpha | 見送り | 固定の理由（外部エクスポートの有効時の失敗）が `verify` では確かめられない（F2: C）。`ignore` に入れた |
| gradle/org.yaml-snakeyaml-2.7 | snakeyaml 2.6 → 2.7 | 取り込み | Gradle の小さな更新とまとめた `verify` の関門が通った |
| npm_and_yarn/frontend/prettier-3.9.9 | prettier 3.9.8 → 3.9.9 | 取り込み | npm の小さな更新とまとめた `verify` の関門が通った。フォーマットの差なし |
| npm_and_yarn/frontend/typescript-7.0.2 | typescript 6.0.3 → 7.0.2 | 通らずに見送り | リンタで止まった（typescript-eslint が TS 7 に未対応。最新の 8.71.0 も peer は `<6.1.0`）。元に戻した。`ignore` には入れない |
| npm_and_yarn/frontend/vite-8.3.1 | vite 8.3.0 → 8.3.1 | 取り込み | npm の小さな更新とまとめた `verify` の関門と `e2eTest`（110 件）が通った |
| npm_and_yarn/frontend/vitest-5.0.2 | vitest 4.1.11 → 5.0.2 | 取り込み | coverage-v8 と1組で `verify` の関門と `e2eTest` が通った。設定は変えず、下限の判定が効くことを確かめた |
| npm_and_yarn/frontend/vitest/coverage-v8-5.0.2 | @vitest/coverage-v8 4.1.11 → 5.0.2 | 取り込み | vitest と1組（同上） |

- 集計: 取り込み 12 本（うち決まりを変える 3 本）、見送り 2 本、通らずに見送り 1 本。
- プルリクエストを閉じる操作と `git push` は依頼者が行う。

## 依頼者の決定 G4: A（Jackson 3.1.6・networknt 3.0.6 に戻す。計画に無い変更）

- `gradle/libs.versions.toml`: `networknt-json-schema-validator` を 3.0.6 に戻した（コメントはもとのまま）。`jackson = "3.1.6"` とライブラリ `jackson-bom`（`tools.jackson:jackson-bom`）を足した。
- `backend/build.gradle.kts`: Spring Boot の BOM と同じ4つの構成（`implementation`・`providedRuntime`・`testImplementation`・`testRuntimeOnly`）に `platform(libs.jackson.bom)` を足した。このリポジトリは Spring Boot の BOM を `platform()` で読み込み、依存の管理のプラグインを使わないため、BOM の版のプロパティの上書きの口が無い。Spring Boot が Jackson の版を決めるのと同じ Jackson の BOM を読み込む形にした（Gradle は高い方の版を選ぶ）。
- `./gradlew :backend:resolveAndLockAll --write-locks`: networknt 3.0.7 を入れる前の lockfile（`lock-after-small.lockfile`）との差は `jackson-core`・`jackson-databind`・`jackson-bom` の3行（3.1.5 → 3.1.6。`jackson-bom` の行に構成 `providedRuntime` が足された）だけ。`jackson-annotations` は 2.21、networknt は 3.0.6。`dependencyInsight`（`productionRuntimeClasspath`）で `jackson-databind` 3.1.6 が Jackson の BOM によることを確かめた。
- OSV-Scanner: `osv-scanner scan source -L backend/gradle.lockfile` が「No issues found」。`./gradlew osvScan` が「失敗の条件に当たるもの 0 件、警告 1 件」（既存の vendor の開発用 undici）。
- `.github/dependabot.yml`: gradle の `ignore` に `com.networknt:json-schema-validator` を足した（外す時期: Spring Boot の管理の Jackson が 3.2 系以上になったとき）。
- 更新の一覧の networknt の行は「見送り」。

## Step 24: README の「警報と対応の手順」

- 「手元の監視（Grafana）」の後に節を足した: p95 の警報3件の表（しきい値と 5 分は警報の決まりのファイルから）、登録の完了の拒否、リンクの確かめの拒否、招待メールの送信の失敗。内容は Step 4 の確かめだけで書いた。
- エンドポイントのメソッドは `AuthController`（`@PostMapping` `/session/refresh`）と `AdminCheckController`（`@GetMapping`）で確かめた。`INVITATION_RESENT` は `InvitationService`（340 行）と `AuditEventListener`（166 行）で確かめた。
- ログの問い合わせは、文言の絞り込み（`|=`）で項目 `code` の値を拾えないため、実際に流していない形（項目で絞る式）は書かず、「項目 `code` の値で見分ける」とした。

## 依頼者の決定 G5: B（`DisplaySettingsProvider.test.tsx` の再現の試み）

- 負荷（`yes` 8）でそのファイルを繰り返し、19 回目までに1回再現した（「keeps previews unsaved…」、同期の `act` の直後の同期の確かめ）。一時の計測で、保存先の値は変わっており画面が 4 ms 後に追い付くことを確かめた（購読の効果がまだ流れていない）。
- 直し: テストの側に `waitForEffects()`（Probe の `useEffect` の印を待つ）を足し、提供元を渡す5つのテストで Probe を待った直後に呼ぶ。負荷（`yes` 8）で 40 回流し、39 回通過・失敗 0（1 回は Vitest の起動の時間切れで数から外した）。
- 詳しくは `code-summary.md` の 11節。
