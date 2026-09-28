# Build and Test のまとめ（build-and-test-summary）

Intent `260925-user-management`（U1〜U8）の Build and Test の結果です。実測の詳細は `test-results.md` にあります。

## 1. 全体の状態

| 観点 | 状態 |
|---|---|
| ビルド（手元） | 準備済み。`./gradlew verify` が成功（6分28秒） |
| テスト（手元） | 準備済み。バックエンド 1,796 件・フロントエンド 732 件・E2E 90 件がすべて通過 |
| CI | **未達**。`66fe981` で2回とも失敗（別々のテストの時間切れ） |
| 配備 | **未達**。CI の失敗と Not Met の目標が残っている |

前提は `build-instructions.md` のとおりです。
- colima（CPU 4・メモリ 6GiB）と `DOCKER_HOST`
- 2つのサブモジュール
- E2E の前の Mailpit

## 2. 作った手順書とテストの種類

| 文書 | 中身 |
|---|---|
| `build-instructions.md` | 道具・取得・環境・ビルド・確認・手当て（サブモジュールが2つ、SubEtha SMTP、Mailpit） |
| `integration-test-instructions.md` | 単位の境目の結合テスト（U1〜U3・U8）と E2E（010〜090）・実際のブラウザの検査 |
| `performance-test-instructions.md` | 画面の時間の測り（この段）と、k6 の場面（U2 の6・U3 の8・U8 の1。測定は performance-validation） |
| `security-test-instructions.md` | 静的な検査、必ず書くテストの確かめ先、E2E の報告の確かめ |
| `test-results.md` | この段の実測 |
| `cross-unit-traceability.md` | 要件 138 件の網羅（未網羅 0 件、持ち越しあり） |

## 3. 単位ごとのカバレッジ（この段の実測）

- バックエンドの全体：行 98.8%・分岐 94.4%
- フロントエンドの全体：行 97.44%・分岐 92.67%
- パッケージごとの下限を当てる 35 パッケージは、すべて行 80%・分岐 70% 以上です。
- 各単位の新しいパッケージの値：

| 単位 | パッケージ（行・分岐） |
|---|---|
| U1 | `mail.config` 98.0%・96.2%、`mail.domain` 100.0%・98.3%、`mail.service` 100.0%・100.0%、`mail.template` 97.2%・95.7%、`mail.transport` 96.3%・96.7% |
| U2 | `user.domain` 99.5%・97.5%、`user.service` 100.0%・95.5%、`user.web` 96.5%・84.6%、`audit.domain` 99.5%・97.7%、`audit.service` 100.0%・84.4% |
| U3 | `invitation.domain` 98.4%・96.7%、`invitation.service` 100.0%・93.5%、`invitation.web` 97.3%・87.0%、`invitation.repository` 100.0%・分岐なし |
| U8 | `appearance.service` 100.0%・100.0%、`appearance.web` 100.0%・分岐なし |
| U4〜U7 | フロントエンドの全体に含む（行 80%・分岐 70% の `thresholds`、verify で通過） |

## Target Verification Matrix

略記: Source は `aidlc/spaces/default/intents/260925-user-management/` を省いた `construction/` からの相対パスで書く。「verify」は `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-09-28、develop 66fe981、BUILD SUCCESSFUL 388 秒、バックエンド 単体 1234 件・結合 562 件 失敗 0・飛ばし 0、フロントエンド 732 件通過）。「e2eTest」は Mailpit を起動した `./gradlew e2eTest`（90 件通過・飛ばし 0・想定外 0・不安定 0、`frontend/test-results/e2e-results.json`）。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| U1-NFR1.1 | construction/u1-mail/nfr-requirements/security-requirements.md 2.1 | U1 のログ・MailSendResult・トレースの属性に差し込んだ値（招待の URL・トークン）・件名・本文を入れない | verify で MailSecretLeakIT が通過（成功と失敗の各場面、TRACE でも出ない） | verify・MailSecretLeakIT | build-and-test | Met |
| U1-NFR1.2 | construction/u1-mail/nfr-requirements/security-requirements.md 2.1・nfr-design/security-design.md 4.4 | MailRequest・RenderedMail の toString が宛先・差し込む値・件名・本文を伏せる | verify で通過。RenderedMail の toString の確かめは RenderedMailInspectorTest の「RenderedMail prints neither the subject nor the body」にある（出典の RenderedMailTest というクラスは無い） | verify・MailRequestTest・RenderedMailInspectorTest | build-and-test | Met |
| U1-NFR2.1 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2 | 宛先・差出人・SMTP の応答・部品の例外の文言・資格情報をログと結果に入れない。点検の WARN は項目の名前だけ | verify で通過 | verify・MailSecretLeakIT・MailConfigurationIT | build-and-test | Met |
| U1-SD4.3 | construction/u1-mail/nfr-design/security-design.md 4.3・8節 | 分類できない失敗は固定の文言と型の名前だけの MailUnexpectedException に包む | verify で通過。getMessageExceptions をたどらない差は Q5: A で受け入れ済み | verify・MailUnexpectedExceptionTest・SmtpMailSenderTest・build-and-test-questions.md Q5 | build-and-test | Met |
| U1-NFR2.2 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2 | org.eclipse.angus・jakarta.mail のロガーを OFF、送信の後に部品のロガーのログが出ない | verify で通過（ロガーの水準と記録の無さを確かめる） | verify・MailConfigurationIT（ロガーの確かめ）・MailSecretLeakIT | build-and-test | Met |
| U1-NFR2.3 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2 | mail.debug は既定で無効、送信の部品のセッションが debug でない | verify で通過（mail.debug=false を確かめる） | verify・MailConfigurationIT | build-and-test | Met |
| U1-NFR2.4 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2・nfr-design/logical-components.md 10節 | README に運用で設定しない値・方式ごとの書き方・STARTTLS は 587・starttls.enable だけの平文の危険を書く | README の「メール（U1）」に4点すべてあることを読んで確かめた | README.md（SPRING_MAIL_PORT の行・方式の表・「starttls.enable だけの設定に注意」・「運用で設定しない値」） | build-and-test | Met |
| U1-NFR2.5 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2 | SMTP の設定は環境変数だけから。空の既定の host を置かない。.env.example はコメント。host が無い・空白で NOT_CONFIGURED | verify で通過。.env.example のメールの節はすべてコメント行 | verify・MailSettingsTest・MailConfigurationIT・Gitleaks・.env.example | build-and-test | Met |
| U1-NFR2.6 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2 | 暗号化の方式の分類の表どおり。資格情報と NONE の組で NOT_CONFIGURED | verify で通過。mail.config 行 98.0%・分岐 96.2% | verify・EncryptionModeTest・MailSettingsTest・MailConfigurationIT | build-and-test | Met |
| U1-NFR2.7 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2 | STARTTLS・SMTPS で証明書と名前を確かめ TLS 1.2 以上。受け付けない・名前が合わない受け手で CONNECTION_FAILED | verify で通過 | verify・MailTlsIT | build-and-test | Met |
| U1-NFR2.8 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2 | 改行を含めてもヘッダーが増えない。SMTP_HEADER_INJECTION を priority にかかわらず止める | verify で通過。spotbugsGate の指摘なし | verify・MailHeaderInjectionIT・MailRequestValidationTest・spotbugsGate | build-and-test | Met |
| U1-NFR2.9 | construction/u1-mail/nfr-requirements/security-requirements.md 2.2 | spotbugsGate に PREDICTABLE_RANDOM を足し既存の1件を確かめる | verify の spotbugsGate 通過。既存の1件は理由つきで除外済み | verify・spotbugsGate・backend/config/spotbugs-exclude.xml | build-and-test | Met |
| U1-BR2.4 | construction/u1-mail/nfr-design/security-design.md 7節・8節 | テンプレートに `{{{`・`{{&`・`{{>`・`{{=` が無い、属性は二重引用符 | verify で通過 | verify・MailTemplateLintTest | build-and-test | Met |
| U1-BR6.3 | construction/u1-mail/nfr-design/security-design.md 8節・logical-components.md 6.1 | Observation の属性は4つの鍵だけで値が入らない、未知は unknown | verify で通過。unexpected のタグの追加は Q5: A で受け入れ済み | verify・SmtpMailSenderTest | build-and-test | Met |
| U1-NFR5.1 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 2節 | mail は DB アクセスの型と @Transactional を使わない | verify で通過 | verify・MailBoundaryArchitectureTest | build-and-test | Met |
| U1-NFR6.1 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 2節 | SMTP の接続・読み取り・書き込みの時間切れ 3000 ミリ秒。応答しない相手で TIMEOUT | verify で通過 | verify・MailConfigurationIT・MailSendFailureIT | build-and-test | Met |
| U1-NFR6.2 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 2節 | 時間切れは環境変数で上書き可。不正な値で項目の名前だけの WARN と NOT_CONFIGURED | verify で通過 | verify・MailConfigTest・MailSettingsTest・MailConfigurationIT | build-and-test | Met |
| U1-NFR6.3 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 2節 | 送信は1回だけ、失敗の後の接続が1回だけ | verify で通過 | verify・MailSendFailureIT | build-and-test | Met |
| U1-NFR6.4 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 2節 | 受け手が止まっていても /actuator/health が UP でメールの項目が無い | verify で通過（閉じたポートを指して status だけ・UP） | verify・MailConfigurationIT「keeps health UP」 | build-and-test | Met |
| U1-NFR6.5 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 2節 | 同じテンプレートを複数のスレッドで描いて崩れない | verify で通過 | verify・MailTemplateRegistryTest「rendered by many threads」 | build-and-test | Met |
| U1-NFR8.1 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 3節 | すべてのテンプレートを ja・en で描ける、件名が空でない、lang が合う、欠けたら起動を止める | verify で通過。WAR に invitation_ja.html・invitation_en.html | verify・MailTemplateLintTest・MailTemplateRegistryTest・WAR の中身 | build-and-test | Met |
| U1-NFR8.2 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 3節 | java-mustache-processor を 0.1.0・8d44c36 に固定し中身を変えない | git submodule status で 8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4（0.1.0）。verify の mustacheVendorUnchanged 通過 | git submodule status・verify | build-and-test | Met |
| U1-NFR8.3 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 3節 | 取り込みの5条件 (a)〜(e)。(d) は CI がサブモジュールを固定先で取得してビルドできる | (a) verify 通過、(b) slf4j-api 2.0.18 が backend/gradle.lockfile に載る、(c) WAR に cherry-mustache-core-0.1.0.jar、(e) verify 通過。(d) CI run 36433076151 はサブモジュールの取得は成功したが、1回目は H2CompactionByPoolSuspensionIT の1件（接続が 0 本になるのを 10 秒待つところで時間切れ）、2回目（依頼者の手での流し直し）は InvitationAdminPage.test.tsx の1件（Vitest の既定の 5 秒で時間切れ）で、どちらも verify が失敗 | verify・backend/gradle.lockfile・unzip -l mastersmith.war・gh の CI run 36433076151 | build-and-test | Not Met |
| U1-NFR9.1 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 4節 | JVM の中の SubEtha SMTP で受け、宛先・件名・本文・言語・Content-Type を確かめる | verify で通過 | verify・MailSendIT | build-and-test | Met |
| U1-NFR9.2 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 4節 | 閉じたポート・無応答・STARTTLS 拒否・認証と宛先の拒否をそれぞれ分類し、秘密を出さない | verify で通過 | verify・MailSendFailureIT・MailTlsIT・MailSecretLeakIT | build-and-test | Met |
| U1-NFR9.3 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 4節 | team.md のメールの必須テストのうち U1 の分 | verify で対応表のテストがすべて通過 | verify・u1-mail/code-generation/unit-test-instructions.md 3節の対応表 | build-and-test | Met |
| U1-NFR9.4 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 4節 | debug でない・ヘルスに含まれない・NOT_CONFIGURED・不正な時間切れ・方式の分類などの確かめ | verify で通過 | verify・MailSettingsTest・EncryptionModeTest・MailConfigurationIT | build-and-test | Met |
| U1-NFR9.5 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 4節 | mail の5つの下位パッケージがそれぞれ行 80%・分岐 70% 以上、全体も同じ、除外を足さない | mail.config 98.0%・96.2%、mail.domain 100.0%・98.3%、mail.template 97.2%・95.7%、mail.transport 96.3%・96.7%、mail.service 100.0%・100.0%。全体 行 98.8%（5600/5669）・分岐 94.4%（2050/2172） | verify の JaCoCo（パッケージごとの一覧） | build-and-test | Met |
| U1-NFR11.1 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 5節 | Mailpit を profile mail で起動、画面 8025 は 127.0.0.1 だけ、招待のメールが受け手の画面に出る | この段の e2eTest で Mailpit（profile mail）を起動し 090 が Mailpit の API でメールを受けた。画面での目視は依頼者の決定（U3 の決定 6）で API の確かめに代えたため行っていない。1025 の公開は基盤の設計 D1 との差 | e2eTest・090-invitation-registration-flow.e2e.ts・frontend/e2e/support/mailpit.ts | deployment-execution（画面の目視が要るとき） | Unverified |
| U1-NFR11.2 | construction/u1-mail/nfr-requirements/tech-stack-decisions.md 5節 | 実在の宛先・外部の SMTP へ送らない。見本の接続先は手元の受け手だけ | .env.example の見本は `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`（コメント）だけ。README の見本も Mailpit だけ | .env.example 135〜155 行・README.md | build-and-test | Met |
| U2-NFR6.1 | construction/u2-user-preferences/nfr-requirements/performance-requirements.md 要件 | GET /api/me/preferences の同時 10 件で p95 1 秒以内 | 未測定。この段では k6 の場面を足し k6 inspect で読み込めることだけを確かめた | perf/k6/scenarios.js・k6 inspect（grafana/k6:2.3.0、24 場面 rc=0） | performance-validation | Unverified |
| U2-NFR6.2 | construction/u2-user-preferences/nfr-requirements/performance-requirements.md 要件 | PUT /api/me/preferences の同時 10 件で p95 1 秒以内（成功・入力の誤り） | 未測定（場面の用意と k6 inspect だけ） | perf/k6/scenarios.js・k6 inspect | performance-validation | Unverified |
| U2-NFR6.3 | construction/u2-user-preferences/nfr-requirements/performance-requirements.md 要件 | パスワードの変更の成功の同時 10 件で p95 2 秒以内 | 未測定（場面の用意と k6 inspect だけ） | perf/k6/scenarios.js・perf/README.md・k6 inspect | performance-validation | Unverified |
| U2-NFR6.4 | construction/u2-user-preferences/nfr-requirements/performance-requirements.md 要件 | 今のパスワードの誤り・入力の誤りの同時 10 件で p95 1 秒以内 | 未測定（場面の用意と k6 inspect だけ） | perf/k6/scenarios.js・k6 inspect | performance-validation | Unverified |
| U2-NFR6.5 | construction/u2-user-preferences/nfr-requirements/performance-requirements.md 要件・nfr-design/performance-design.md 4節 | ログインと更新の応答に4つの値を足しても p95 1 秒以内を保ち、問い合わせを増やさない | SQL の数（ログイン5件・更新4件）は verify の LoginResponsePreferencesIT で通過。p95 は未測定（一部だけ確かめた） | verify・LoginResponsePreferencesIT | performance-validation（loginSuccess・refresh の p95） | Unverified |
| U2-NFR6.6 | construction/u2-user-preferences/nfr-requirements/performance-requirements.md 要件 | bcrypt の cost は既定の 12 のまま | application.yaml の既定は `${MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST:12}` | backend/src/main/resources/application.yaml 64 行 | build-and-test | Met |
| U2-NFR5.1 | construction/u2-user-preferences/nfr-requirements/performance-requirements.md 要件 | 照合と新しいハッシュはトランザクションの外で計算する | verify で通過 | verify・UserPreferencesServiceTest | build-and-test | Met |
| U2-NFR5.2 | construction/u2-user-preferences/nfr-requirements/reliability-requirements.md 要件 | 同時 10 件で hikaricp の待ちの時間切れ 0、待ちの最大が 5 秒より十分小さい、成功と監査の件数が一致 | 未測定 | なし（k6 と /actuator/metrics は後の段） | performance-validation | Unverified |
| U2-NFR5.3 | construction/u2-user-preferences/nfr-requirements/scalability-requirements.md 要件 | 同時 10 件で最大 20 本、上限 30 に収まる | 見積もりだけ、未測定 | なし | performance-validation | Unverified |
| U2-NFR6.7 | construction/u2-user-preferences/nfr-requirements/scalability-requirements.md 要件 | 想定の規模を1台で処理し NFR6.1〜6.4 を満たす | 未測定 | なし | performance-validation | Unverified |
| U2-NFR6.8 | construction/u2-user-preferences/nfr-requirements/observability-requirements.md 要件 | 3本の API が http.server.requests で取れ uri が決まった値、新しい指標を足さない | 未確認（手元の監視の起動はこの段の範囲外） | なし | observability-setup | Unverified |
| U2-NFR6.9 | construction/u2-user-preferences/nfr-requirements/observability-requirements.md 要件 | 新しい警報・ダッシュボードを足さない、既存の ms-login-p95 をそのまま使う | 未確認 | なし | observability-setup | Unverified |
| U2-NFR2.4 | construction/u2-user-preferences/nfr-requirements/observability-requirements.md 要件 | 構造化ログ・トレースID・4xx WARN・5xx ERROR・秘密を出さない | verify で通過 | verify・MeSecretLeakIT・既存のログの形のテスト | build-and-test | Met |
| U2-NFR9.5 | construction/u2-user-preferences/nfr-requirements/observability-requirements.md 要件 | 成功と今のパスワードの誤りを監査に1件ずつ必須の項目つきで、入力の誤りと保存は記録しない | verify で通過 | verify・PasswordChangedAuditIT・MePreferencesApiIT | build-and-test | Met |
| U2-NFR9.4 | construction/u2-user-preferences/nfr-requirements/reliability-requirements.md 要件 | 監査の書き込みが失敗しても応答は変わらず ERROR にパスワードを含めない | verify で通過 | verify・PasswordChangedAuditIT・AuditEventListenerTest | build-and-test | Met |
| U2-NFR9.8 | construction/u2-user-preferences/nfr-requirements/reliability-requirements.md 要件 | 1つのトランザクションで確定か何も変えない、巻き戻りで成功の監査なし | verify で通過 | verify・PreferencesPartialUpdateIT・PasswordChangedAuditIT | build-and-test | Met |
| U2-NFR10.1 | construction/u2-user-preferences/nfr-requirements/reliability-requirements.md 要件 | 変更は V7 の1つで前進のみ、V1〜V6 を書き換えない | verify で通過（migration-through-v6 の複写が本番と一致） | verify・V7MigrationIT・validate-on-migrate | build-and-test | Met |
| U2-NFR10.2 | construction/u2-user-preferences/nfr-requirements/reliability-requirements.md 要件 | V7 の後の既存の利用者の既定の値、V7 は1回だけ | verify で通過 | verify・V7MigrationIT | build-and-test | Met |
| U2-NFR10.3 | construction/u2-user-preferences/nfr-requirements/reliability-requirements.md 要件・nfr-design/reliability-design.md 6.2 | 1つ前の版が V7 の後の内部DB で起動し今までどおり動く | (1) V7BackwardCompatibilityIT は verify で通過。(2) 戻しの練習は未実施（一部だけ確かめた） | verify・V7BackwardCompatibilityIT | deployment-execution（戻しの練習） | Unverified |
| U2-NFR10.4 | construction/u2-user-preferences/nfr-requirements/reliability-requirements.md 要件 | 既知の限界（display_name を渡さない追記の失敗など）、1つ前の版が初期管理者を作らない | 自動の確かめの部分は V7BackwardCompatibilityIT で通過。戻しの練習の部分は未実施 | verify・V7BackwardCompatibilityIT | deployment-execution（戻しの練習） | Unverified |
| U2-NFR10.5 | construction/u2-user-preferences/nfr-requirements/reliability-requirements.md 要件 | V7 の前にバックアップを取り、戻しの手順に第一・第二の手を書く | README の「戻し方」に V7 の注意を足しただけ。バックアップと手順は後の段 | README.md「戻し方」 | deployment-pipeline・deployment-execution | Unverified |
| U2-NFR4.1 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | トークンが無い・無効なら 401 AUTHENTICATION_REQUIRED で何も読み書きしない | verify で通過 | verify・MePreferencesApiIT・MePasswordApiIT | build-and-test | Met |
| U2-NFR4.2 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | 管理者でない利用者でも取得・保存 200、変更 204 | verify で通過 | verify・MePreferencesApiIT・MePasswordApiIT | build-and-test | Met |
| U2-NFR4.3 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件・nfr-design/security-design.md 2節 | 対象はトークンの本人だけ。userAgent は 512 文字に切る | verify で通過 | verify・MePreferencesApiIT・MePasswordApiIT・MeRequestContextResolverTest | build-and-test | Met |
| U2-NFR4.4 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | Origin の確かめを足さない | SecurityFilterChain は変えていない（コード生成の記録を読んで確かめた）。ArchitectureTest も verify で通過 | u2-user-preferences/code-generation/code-summary.md・verify | build-and-test | Met |
| U2-NFR4.5 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | 今のパスワードの誤りを6回以上でも7回目に変更でき、ロックの状態が変わらない | verify で通過 | verify・MePasswordApiIT | build-and-test | Met |
| U2-NFR2.1 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | メールアドレスをログ・エラー応答・プリファレンスの応答に含めない | U2 が足した範囲で verify 通過。既存の InitialAdminInitializer・AuditEventListener のキーは依頼者の判断で据え置き | verify・MeSecretLeakIT | build-and-test | Met |
| U2-NFR2.2 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | パスワード・トークンをログ・監査・トレース・エクスポート・エラー応答に含めない、V7 の2列も確かめる | verify で通過 | verify・MeSecretLeakIT・AuditSecretLeakIT | build-and-test | Met |
| U2-NFR2.3 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | 入力の誤りの応答は項目の名前と理由だけ | verify で通過 | verify・MePreferencesApiIT・MePasswordApiIT | build-and-test | Met |
| U2-SD3 | construction/u2-user-preferences/nfr-design/security-design.md 3節 | 400 VALIDATION_FAILED の fieldErrors の形、MALFORMED_REQUEST | verify で通過 | verify・PreferencesValidationTest・PasswordChangeValidationTest・MePreferencesApiIT | build-and-test | Met |
| U2-NFR8.1 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | 説明文は ja・en、PASSWORD_CURRENT_MISMATCH は 400、1つの code に1つの状態コード | verify で通過 | verify・UserProblemTypesTest・UserProblemTypeCatalogTest・ProblemTypeDuplicateStartupIT | build-and-test | Met |
| U2-NFR9.1 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | 12 コードポイント以上・72 バイト以内、境界 11・12・72・73 | verify で通過 | verify・PasswordChangeValidationTest（jqwik）・MePasswordApiIT | build-and-test | Met |
| U2-NFR9.2 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | 氏名 1〜254 コードポイント、Cc・Cf を拒否、境界 254・255 | verify で通過 | verify・DisplayNameTest（jqwik） | build-and-test | Met |
| U2-NFR9.3 | construction/u2-user-preferences/nfr-requirements/security-requirements.md 要件 | SpotBugs の関門・Gitleaks・OSV-Scanner を通す、除外を足さない | verify の verifySecurity 通過 | verify（SpotBugs・Gitleaks・OSV-Scanner） | build-and-test | Met |
| U2-NFR9.6 | construction/u2-user-preferences/nfr-requirements/tech-stack-decisions.md テストの道具と品質の関門 | 外したパッケージと user.web がそれぞれ行 80%・分岐 70% 以上、一覧と除外を増やさない | user.domain 99.5%・97.5%、user.repository 行・分岐なし、user.service 100.0%・95.5%、user.web 96.5%・84.6%、audit.domain 99.5%・97.7%、audit.service 100.0%・84.4%、auth.service 99.0%・91.7%、auth.web 100.0%・95.0%。全体 98.8%・94.4% | verify の JaCoCo（パッケージごとの一覧） | build-and-test | Met |
| U2-NFR9.7 | construction/u2-user-preferences/nfr-requirements/tech-stack-decisions.md テストの道具と品質の関門 | 必須のテスト（今のパスワード・規則の境界・変更の後のトークン・認可・監査・漏えい）、jqwik | verify で対応表のテストがすべて通過 | verify・MePasswordApiIT ほか unit-test-instructions.md 3節の対応表 | build-and-test | Met |
| U2-RD2 | construction/u2-user-preferences/nfr-design/reliability-design.md 2節 | 同時のパスワードの変更で後の要求が 400、監査 SUCCESS 1件・FAILURE 1件 | verify で通過 | verify・PasswordChangeConcurrencyIT | build-and-test | Met |
| U2-RD4 | construction/u2-user-preferences/nfr-design/reliability-design.md 4節 | 同じメールアドレスの同時の作成で利用者1人、出来事は1回 | verify で通過 | verify・UserCreationIT | build-and-test | Met |
| U3-NFR6.1 | construction/u3-invitation/nfr-requirements/performance-requirements.md 要件 | 招待・送り直しは SMTP を含めて同時 10 件で p95 5 秒以内 | 未測定。場面を足し k6 inspect で読み込めることだけを確かめた | perf/k6/scenarios.js・perf/README.md・k6 inspect | performance-validation | Unverified |
| U3-NFR6.2 | construction/u3-invitation/nfr-requirements/performance-requirements.md 要件 | 受け手が応答しない・拒むときも FAILED で応答、1件で 5 秒以内 | ふるまいは verify の InvitationSendFailureIT で通過。5 秒以内の時間は未測定（一部だけ確かめた） | verify・InvitationSendFailureIT | performance-validation（時間） | Unverified |
| U3-NFR6.3 | construction/u3-invitation/nfr-requirements/performance-requirements.md 要件 | 一覧・取り消し・リンクの確かめの同時 10 件で p95 1 秒以内 | 未測定（場面の用意と k6 inspect だけ） | perf/k6/scenarios.js・k6 inspect | performance-validation | Unverified |
| U3-NFR6.4 | construction/u3-invitation/nfr-requirements/performance-requirements.md 要件 | 登録の完了の成功の同時 10 件で p95 1 秒以内 | 未測定。トークンは Mailpit の API から取り出す手順にした（Q2: A） | perf/k6/scenarios.js・perf/README.md・k6 inspect | performance-validation | Unverified |
| U3-NFR6.5 | construction/u3-invitation/nfr-requirements/performance-requirements.md 要件・nfr-design/performance-design.md 4節 | 入力の誤りとリンクの拒否の同時 10 件で p95 1 秒以内（BR7.4 は対象外） | 未測定（場面の用意と k6 inspect だけ） | perf/k6/scenarios.js・k6 inspect | performance-validation | Unverified |
| U3-NFR6.6 | construction/u3-invitation/nfr-requirements/performance-requirements.md 要件 | bcrypt の cost は既定の 12 のまま | application.yaml の既定は 12 | backend/src/main/resources/application.yaml 64 行 | build-and-test | Met |
| U3-NFR5.1 | construction/u3-invitation/nfr-requirements/performance-requirements.md 要件・nfr-design/performance-design.md 2.1 | 送信を待つ間に接続を持たない、トランザクションの中の送信で IllegalStateException | verify で通過 | verify・InvitationSendConnectionIT・InvitationMailDispatcherTest | build-and-test | Met |
| U3-NFR5.2 | construction/u3-invitation/nfr-requirements/performance-requirements.md 要件 | 登録の完了は1つのトランザクションで排他して読み作成する。接続の待ちは NFR5.3 で確かめる | 作りは verify で通過。接続の待ちは未測定（一部だけ確かめた） | verify・RegistrationServiceTest・RegistrationRollbackIT | performance-validation（接続の待ち） | Unverified |
| U3-NFR5.3 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件 | 同時 10 件で hikaricp の待ちの時間切れ 0、待ちの最大が 5 秒より十分小さい、成功と監査の件数が一致 | 未測定 | なし | performance-validation | Unverified |
| U3-NFR5.4 | construction/u3-invitation/nfr-requirements/scalability-requirements.md 要件 | 同時 10 件で最大 20 本、上限 30 に収まる | 見積もりだけ | なし | performance-validation | Unverified |
| U3-NFR6.7 | construction/u3-invitation/nfr-requirements/scalability-requirements.md 要件 | 想定の規模を1台で処理し NFR6.1・6.3〜6.5 を満たす | 未測定 | なし | performance-validation | Unverified |
| U3-NFR6.10 | construction/u3-invitation/nfr-requirements/scalability-requirements.md 要件・nfr-design/reliability-design.md 2.1 | 招待中を1件に限る生成列 (a)〜(d) | verify で通過 | verify・V8MigrationIT・InvitationSchemaIT・InvitationConcurrencyIT | build-and-test | Met |
| U3-NFR6.8 | construction/u3-invitation/nfr-requirements/observability-requirements.md 要件 | U3 の API が http.server.requests で取れ uri に ID・トークン・メールアドレスを含まない | 未確認 | なし | observability-setup | Unverified |
| U3-NFR6.9 | construction/u3-invitation/nfr-requirements/observability-requirements.md 要件 | 新しい警報・ダッシュボードを足さない、既存の式が変わらない | 未確認 | なし | observability-setup | Unverified |
| U3-NFR2.3 | construction/u3-invitation/nfr-requirements/observability-requirements.md 要件・nfr-design/observability-design.md 2節 | ログは invitationId など決めた項目だけ、送信の失敗で U3 の INFO が1件 | verify で通過 | verify・InvitationSecretLeakIT | build-and-test | Met |
| U3-NFR9.5 | construction/u3-invitation/nfr-requirements/observability-requirements.md 要件 | 5つの出来事が監査に1件ずつ、拒否などは記録しない | verify で通過 | verify・InvitationAuditIT | build-and-test | Met |
| U3-NFR9.4 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件 | 監査の書き込みの失敗で応答が変わらず ERROR に秘密を含めない | verify で通過 | verify・InvitationAuditWriteFailureIT | build-and-test | Met |
| U3-NFR9.8 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件 | 同時の招待は1件だけ、もう一方は 409。同じ招待への操作は1つずつ判定 | verify で通過 | verify・InvitationConcurrencyIT・RegistrationConcurrencyIT | build-and-test | Met |
| U3-NFR9.9 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件 | 送信は1回だけ、失敗しても招待は確定し FAILED、PENDING は API で FAILED | verify で通過 | verify・InvitationSendFailureIT・InvitationSendResultIT | build-and-test | Met |
| U3-NFR9.10 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件・nfr-design/reliability-design.md 4節 | 保存の日数を過ぎた招待を定期に消す（90d・cron・上限 1000） | verify で通過 | verify・InvitationCleanupIT・InvitationCleanupJobTest | build-and-test | Met |
| U3-NFR9.13 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件 | 巻き戻ったとき利用者を作らず招待は PENDING のまま、成功の監査なし | verify で通過 | verify・RegistrationRollbackIT | build-and-test | Met |
| U3-NFR10.1 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件 | V8 の1つの前進の変更、既存の表と V1〜V7 を変えない | verify で通過 | verify・V8MigrationIT・validate-on-migrate | build-and-test | Met |
| U3-NFR10.2 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件・nfr-design/reliability-design.md 8節 | 1つ前の版が V8 の後の内部DB で起動し動く | (1) V8BackwardCompatibilityIT は verify で通過。(2) 戻しの練習は未実施（一部だけ確かめた） | verify・V8BackwardCompatibilityIT | deployment-execution（戻しの練習） | Unverified |
| U3-NFR10.3 | construction/u3-invitation/nfr-requirements/reliability-requirements.md 要件 | 戻し直したとき招待の表はそのまま使われ、期限を過ぎた招待は期限切れ | README の「戻し方」に書いただけ、実地の確かめは無い | README.md「戻し方」 | deployment-pipeline | Unverified |
| U3-NFR1.1 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | SecureRandom の 32 バイトの Base64（43 文字）、作成と送り直しのたびに新しく | verify で通過。PREDICTABLE_RANDOM の指摘なし | verify・InvitationTokenTest（jqwik）・spotbugsGate | build-and-test | Met |
| U3-NFR1.2 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 内部DB には SHA-256 のハッシュだけを一意の値で保存、トークンを保存しない | verify で通過（ハッシュの計算、一意、応答と行にトークンが無い） | verify・InvitationTokenTest「the hash is the SHA-256」・V8MigrationIT・InvitationAdminApiIT | build-and-test | Met |
| U3-NFR1.3 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 有効は PENDING で有効期限より前だけ、使ったトークンは使えない、既定 24 時間 | verify で通過 | verify・InvitationValidityTest（jqwik）・RegistrationApiIT・RegistrationConcurrencyIT | build-and-test | Met |
| U3-BR1.6 | construction/u3-invitation/code-generation/code-generation-plan.md 8節・9節の決定 7 | validity・retention の不正な値で起動を止める | verify で通過 | verify・InvitationSettingsTest | build-and-test | Met |
| U3-NFR1.4 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 招待の URL はベース URL だけから、Host を使わない、ベース URL が無ければ 503 | verify で通過 | verify・InvitationMailIT（RawHttp で Host を変える） | build-and-test | Met |
| U3-NFR1.5 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | トークン・ハッシュ・URL をログ・監査・トレース・エラー応答・応答に含めない | verify で通過。e2eTest の json にも `/register#token=` は 0 件 | verify・InvitationSecretLeakIT・e2e-results.json の検索 | build-and-test | Met |
| U3-NFR1.6 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 形の合わないトークンは DB を引かずに見つからない、境界 42・43・44 | verify で通過 | verify・InvitationTokenTest（jqwik） | build-and-test | Met |
| U3-NFR2.1 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | メールアドレスとパスワードをログとエラー応答に含めない | U3 の経路で verify 通過。既存のログインの経路と初期管理者は据え置き（出典で範囲外） | verify・InvitationSecretLeakIT | build-and-test | Met |
| U3-NFR2.2 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 送信の失敗で宛先・SMTP の応答・例外の文言・資格情報を含めない | verify で通過 | verify・InvitationSendFailureIT | build-and-test | Met |
| U3-NFR3.1 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 拒否は理由によらず 404・REGISTRATION_LINK_INVALID・同じ本文 | verify で通過 | verify・RegistrationApiIT | build-and-test | Met |
| U3-NFR3.3 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 招待中の人はログイン・更新・アクセストークンの認証で拒否 | verify で通過 | verify・InvitedPersonAuthenticationIT | build-and-test | Met |
| U3-NFR4.1 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 管理の4本は未認証 401・管理者でなければ 403・管理者は成功 | verify で通過 | verify・InvitationAdminApiIT | build-and-test | Met |
| U3-NFR4.2 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件・nfr-design/security-design.md 3.1 | 認証なしは POST の2本だけ、ほかは 401、order 310 | verify で通過 | verify・RegistrationPublicScopeIT・SecurityExtensionValidator | build-and-test | Met |
| U3-SD3.2 | construction/u3-invitation/nfr-design/security-design.md 3.2 | トークンなしの2つの POST は処理、壊れたトークン付きは 401、GET と別の道は 401 | verify で通過 | verify・RegistrationPublicScopeIT | build-and-test | Met |
| U3-NFR4.3 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 公開の2つの API に Origin の確かめを足さない | auth・common.security に手を入れていない（コード生成の記録を読んで確かめた） | u3-invitation/code-generation/code-summary.md | build-and-test | Met |
| U3-NFR4.4 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 完了の応答にトークンが無く、完了した利用者は管理者でなく管理の API が 403 | verify で通過（「登録した利用者は管理者でない」で 403） | verify・RegistrationApiIT | build-and-test | Met |
| U3-NFR8.1 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 5つの code を1つの状態コードに固定、ja・en、内部の文言を載せない | verify で通過。code と状態コードと ja・en の確かめは InvitationDomainValuesTest「the five problem types have fixed codes, statuses and non-blank ja and en texts」にある（出典の InvitationProblemTypesTest というクラスは無い） | verify・InvitationDomainValuesTest・InvitationAdminApiIT・ProblemTypeDuplicateStartupIT | build-and-test | Met |
| U3-NFR8.2 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 招待メールを招待の言語の ja・en で描き、件名・本文・lang が合う | verify で通過。e2eTest の 090 でも Mailpit でメールを受けた | verify・InvitationMailIT・e2eTest（090） | build-and-test | Met |
| U3-NFR9.1 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | team.md の招待と登録の完了の必須テスト | verify で対応表のテストがすべて通過 | verify・u3-invitation/code-generation/unit-test-instructions.md 3節の対応表 | build-and-test | Met |
| U3-NFR9.2 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | team.md のメールの必須テスト、48 時間の設定で本文に「48 時間」 | verify で通過 | verify・InvitationTemplateContentTest・InvitationMailIT・InvitationSettingsTest | build-and-test | Met |
| U3-NFR9.3 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | SpotBugs の関門・Gitleaks・OSV-Scanner を通す、除外を足さない | verify の verifySecurity 通過 | verify（SpotBugs・Gitleaks・OSV-Scanner） | build-and-test | Met |
| U3-NFR9.12 | construction/u3-invitation/nfr-requirements/security-requirements.md 要件 | 入力の境界（254・255 文字、CR・LF、11・12 コードポイント、72・73 バイト、43 文字） | verify で通過 | verify・InvitationRequestValidationTest（jqwik）・RegistrationValidationTest | build-and-test | Met |
| U3-NFR9.6 | construction/u3-invitation/nfr-requirements/tech-stack-decisions.md テストの道具と品質の関門 | invitation の各パッケージと手を入れたパッケージが行 80%・分岐 70% 以上 | invitation.web 97.3%・87.0%、invitation.service 100.0%・93.5%、invitation.domain 98.4%・96.7%、invitation.repository 100.0%・分岐なし、audit.domain 99.5%・97.7%、audit.service 100.0%・84.4%、mail.template 97.2%・95.7%、user.domain 99.5%・97.5%、user.service 100.0%・95.5%。全体 98.8%・94.4% | verify の JaCoCo（パッケージごとの一覧） | build-and-test | Met |
| U3-NFR9.7 | construction/u3-invitation/nfr-requirements/tech-stack-decisions.md テストの道具と品質の関門 | 有効の判定・トークンの形・ページの計算を jqwik で確かめ種を記録 | verify で通過 | verify・InvitationValidityTest・InvitationTokenTest・InvitationPagingTest | build-and-test | Met |
| U3-NFR9.11 | construction/u3-invitation/nfr-requirements/tech-stack-decisions.md テストの道具と品質の関門 | 招待から登録の完了までの E2E を1本、verify と CI の外 | e2eTest で 090 が通過（1件、9 秒） | e2eTest・090-invitation-registration-flow.e2e.ts | build-and-test | Met |
| U4-NFR6.1 | construction/u4-display-foundation/nfr-requirements/performance-requirements.md 1節・2節 | 最初の画面が出るまで 2 秒以内、5 回すべて（関門にしない） | 108・104・103・103・76 ミリ秒、5/5 が 2000 ミリ秒以内 | e2eTest・050-display-accessibility.e2e.ts の注記 TIME | build-and-test | Met |
| U4-NFR6.2 | construction/u4-display-foundation/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 2.3 | 見た目の設定とセッションの復元を並べて始め、片方だけでは描かない、要求は1回 | verify で通過。このファイルは 50 回の繰り返しでも 50 回通過 | verify・DisplaySettingsProvider.test.tsx・npx vitest run の 50 回 | build-and-test | Met |
| U4-NFR6.3 | construction/u4-display-foundation/nfr-requirements/performance-requirements.md 1節 | 初回の JavaScript は gzip で 500KB 以内を目安 | 123.1 KB（index 98.7・useTranslation 16.6・hooks 7.8）、警告なし | verify・frontend/scripts/check-bundle-size.mjs | build-and-test | Met |
| U4-NFR6.4 | construction/u4-display-foundation/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 3.3・5節 | sans のとき Noto Serif JP のフォントへの要求が無い、font-display swap | e2eTest の 050 の測定のテスト（要求があれば失敗する条件）が通過 | e2eTest・050-display-accessibility.e2e.ts「without CSP violations or serif fonts」 | build-and-test | Met |
| U4-NFR6.5 | construction/u4-display-foundation/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 6節 | 配信物の上限は置かない、増えた量を測って記録 | コード生成の値（noto-serif-jp 13,245,592 B、dist 23,495,427 B、WAR 101,566,201 B）を写した。この段の WAR は 101,595,012 B | u4-display-foundation/code-generation/code-summary.md 101〜108 行・ls -l backend/build/libs/mastersmith.war | build-and-test | Met |
| U4-NFR2.1 | construction/u4-display-foundation/nfr-requirements/security-requirements.md 1節 | 登録の完了からログインの画面へメールアドレスだけをメモリで1回、URL と保存に載せない | verify で通過 | verify・LoginForm.test.tsx・loginHandoff.test.ts | build-and-test | Met |
| U4-NFR2.2 | construction/u4-display-foundation/nfr-requirements/security-requirements.md 1節・nfr-design/security-design.md 1節 | localStorage は決めた鍵と値だけ、秘密や氏名を置かない、例外を外へ出さない | verify で通過 | verify・DisplaySettingsProvider.test.tsx・browserStorage.test.ts・resolveDisplaySettings.test.ts | build-and-test | Met |
| U4-NFR9.1 | construction/u4-display-foundation/nfr-requirements/security-requirements.md 2節・nfr-design/security-design.md 3節 | 公開の3つのパスにトークンを付けない、完全な一致で判定 | verify で通過 | verify・apiClient.test.ts・appearanceLoad.test.ts | build-and-test | Met |
| U4-NFR9.2 | construction/u4-display-foundation/nfr-requirements/security-requirements.md 2節・nfr-design/security-design.md 4節 | Accept-Language は ja・en だけ、呼び出し側の指定を上書きしない | verify で通過 | verify・apiClient.test.ts・resolveDisplaySettings.test.ts | build-and-test | Met |
| U4-NFR9.3 | construction/u4-display-foundation/nfr-requirements/security-requirements.md 2節・nfr-design/security-design.md 4節 | 見た目の設定は許される値だけを当てる、HTML に直接差し込まない | verify で通過（リンタを含む） | verify・DisplaySettingsProvider.test.tsx・appearanceLoad.test.ts・resolveDisplaySettings.test.ts・frontendLint | build-and-test | Met |
| U4-NFR9.4 | construction/u4-display-foundation/nfr-requirements/security-requirements.md 2節・nfr-design/security-design.md 5節 | CSP を変えない、index.html に埋め込みが無い、CSP の違反 0 件 | 66fe981 と比べ application.yaml に差分なし。dist/index.html は src つきの script 1つ・style 0。050 の CSP の違反 0 件（違反があれば失敗する条件）で通過 | git diff・frontend/dist/index.html・e2eTest（050） | build-and-test | Met |
| U4-NFR9.5 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 2節 | @fontsource/noto-serif-jp 5.3.0 を lockfile で固定、High 以上で止める | package-lock.json に 5.3.0。verify の OSV-Scanner 通過 | frontend/package-lock.json・verify（OSV-Scanner） | build-and-test | Met |
| U4-NFR9.6 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 2節・2.1 | axe-core 4.13.0 を1つの版に、dist に入らない | package-lock.json の node_modules/axe-core は 4.13.0 の1つ。dist で「Deque Systems」を含むファイル 0 件 | frontend/package-lock.json・grep -rl "Deque Systems" frontend/dist | build-and-test | Met |
| U4-NFR9.7 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 2節 | make-you-chic-ui を 735ef04 に固定、中身を変えない、同梱版でビルドとテストが通る | git submodule status で 735ef04ce6eb618cb875f5c4b31c1645a1f84c28。verify の vendorUnchanged・ビルド・テスト通過 | git submodule status・verify | build-and-test | Met |
| U4-NFR7.1 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 3節 | WCAG 2.1 AA、文字 4.5:1、どの組み合わせでも満たす | green・orange の primary の Button が 4.5:1 に届かない（3.30:1・3.56:1）。050 の既知の違反 color-contrast の login-form-submit-button 4 件・login-language-switch-ja 4 件。make-you-chic-ui 側は 7865c28 で直ったが、固定先の更新は Intent の後（Q7: A・F1: A） | e2eTest（050 の KNOWN）・u4-display-foundation/code-generation/code-summary.md 114・121 行・build-and-test-questions.md Q7・F1 | Intent の後の別の作業（make-you-chic-ui の固定先の更新） | Not Met |
| U4-NFR7.2 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 3節 | 3つの部品ごとに vitest-axe を1件、違反 0 件 | verify で通過 | verify・LoginLanguageSwitch.test.tsx・LoginForm.test.tsx・ShellLayout.test.tsx | build-and-test | Met |
| U4-NFR7.3 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 3節・nfr-design/logical-components.md 5節 | 実際のブラウザの axe で 20 組、違反 0 件、はみ出しなし | 20 組すべて通過、想定外の違反 0 件（UNEXPECTED なし）、はみ出しなし（OVERFLOW なし）。既知の違反 color-contrast の login-form-submit-button 4 件・login-language-switch-ja 4 件（green・orange） | e2eTest・050 の注記 AXE・KNOWN・UNEXPECTED・OVERFLOW | build-and-test | Met |
| U4-SD6.4 | construction/u4-display-foundation/nfr-design/security-design.md 6.4 | axe の結果に color-contrast と scrollable-region-focusable が含まれる（含まれなければ失敗） | 050 の 20 組が通過 | e2eTest・frontend/e2e/support/axe.ts | build-and-test | Met |
| U4-NFR7.4 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 3節 | 言語の選択肢の lang、フォーカスを残す、html lang を変える、reduced-motion | verify で通過 | verify・LoginLanguageSwitch.test.tsx・DisplaySettingsProvider.test.tsx | build-and-test | Met |
| U4-NFR7.5 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 3節・nfr-design/logical-components.md 5.5 | 検査をログインの画面から始め、U5〜U7 の画面は B5 で足す | 050（ログイン）に加え 060・070・080 が e2eTest で通過 | e2eTest（050・060・070・080） | build-and-test | Met |
| U4-NFR8.1 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 4節 | U4 の文言の鍵が ja・en にあり空でない | verify で通過 | verify・messages.test.ts・src/features/auth/registration.test.ts | build-and-test | Met |
| U4-NFR8.2 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 4節 | 言語の名前は訳さない固定の値 | verify で通過 | verify・LoginLanguageSwitch.test.tsx | build-and-test | Met |
| U4-NFR9.8 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 5節 | フロントエンドの行 80%・分岐 70% 以上、除外を増やさない | 全体 行 97.44%（2322/2383）・分岐 92.67%（1443/1557）。app/display-settings の合計 98.05%・95.71% | verify の frontendCoverage・frontend/coverage/coverage-summary.json | build-and-test | Met |
| U4-NFR9.9 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 5節 | fast-check を4つの関数に、性質3つ、種を記録 | verify で通過 | verify・resolveDisplaySettings.test.ts | build-and-test | Met |
| U4-NFR9.10 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 5節 | 既存の画面のテスト・既存の E2E 010〜040 が通り続ける | verify でフロントエンド 732 件通過。e2eTest で 010〜040 の 6 件通過 | verify・e2eTest | build-and-test | Met |
| U4-NFR9.11 | construction/u4-display-foundation/nfr-requirements/tech-stack-decisions.md 5節 | 050 は流れの E2E と別のファイルに置き、前のテストの状態に頼らない | 050-display-accessibility.e2e.ts は 010〜040 と別のファイルで、単独で通過 | frontend/e2e/050-display-accessibility.e2e.ts・e2eTest | build-and-test | Met |
| U4-計画528 | construction/u4-display-foundation/code-generation/code-generation-plan.md 528 行 | json にトークン・パスワード・メールアドレス（e2e-admin@example.com など）が入らない | e2e-results.json でアクセストークンの形 0 件、/register#token= 0 件、メールアドレスは差出人の e2e-noreply@example.com だけ。報告の部品が失敗させずに通過 | e2e-results.json の検索・frontend/playwright-secret-check-reporter.ts | build-and-test | Met |
| U4-計画Step19 | construction/u4-display-foundation/code-generation/code-generation-plan.md Step 19 | 050 を2回続けて流して組の成否が同じ | コード生成で2回以上同じ結果（記録を読んで確かめた）。この段の e2eTest でも 20 組すべて同じく通過、不安定 0 | u4-display-foundation/code-generation/code-summary.md 80 行・e2eTest（flaky 0） | build-and-test | Met |
| U4-計画Step18 | construction/u4-display-foundation/code-generation/code-generation-plan.md Step 18・8節 | 本物の GET /api/appearance の応答の形が見本と一致（一致しなければ失敗） | 050 の測定のテストが通過 | e2eTest・frontend/e2e/support/appearanceFixture.ts | build-and-test | Met |
| U8-NFR6.1 | construction/u8-instance-appearance/nfr-requirements/performance-requirements.md 1節 | GET /api/appearance の同時 10 件で p95 300 ミリ秒以内 | 未測定。台本と閾値の読み込みだけ確かめた | perf/k6/scenarios.js・k6 inspect | performance-validation | Unverified |
| U8-NFR6.2 | construction/u8-instance-appearance/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 1節 | 要求の中で設定を読み直さない、起動時の値をそのまま返す | verify で通過 | verify・AppearanceServiceTest | build-and-test | Met |
| U8-NFR6.3 | construction/u8-instance-appearance/nfr-requirements/scalability-requirements.md 1節・nfr-design/scalability-design.md 3節 | 保持する値は起動時に作って変えず、待ち合わせを置かない | appearance.service の保持は final の record で待ち合わせなし（コードを読んで確かめた） | backend/src/main/java/cherry/mastersmith/appearance/service/AppearanceService.java・ResolvedAppearance.java | build-and-test | Met |
| U8-NFR6.4 | construction/u8-instance-appearance/nfr-requirements/scalability-requirements.md 1節 | 複数のインスタンスへの値の配り直し・共有の仕組みを持たない | 確かめるテストが無く、記録だけの要件 | なし | 持ち主の段なし（記録だけ。配備先が決まったときに見直す） | Unverified |
| U8-NFR6.5 | construction/u8-instance-appearance/nfr-requirements/observability-requirements.md 1節 | 独自の指標を足さず http.server.requests で見る、既存の式で値が出る | 未確認 | なし | observability-setup | Unverified |
| U8-NFR6.6 | construction/u8-instance-appearance/nfr-requirements/observability-requirements.md 1節 | 独自のスパン・属性を足さない、外部エクスポートは既定で無効 | 既存のトレースのテストを含め verify で通過 | verify（既存のトレースのテスト） | build-and-test | Met |
| U8-NFR4.1 | construction/u8-instance-appearance/nfr-requirements/security-requirements.md 1節・nfr-design/security-design.md 3節 | トークンなしの GET は 200 と C7 の応答 | verify で通過 | verify・AppearanceApiIT | build-and-test | Met |
| U8-NFR4.2 | construction/u8-instance-appearance/nfr-requirements/security-requirements.md 1節 | GET 以外は未認証 401、トークン付きは 405 で Allow に GET | verify で通過 | verify・AppearanceApiIT | build-and-test | Met |
| U8-NFR4.3 | construction/u8-instance-appearance/nfr-requirements/security-requirements.md 1節 | HEAD・OPTIONS は公開にしない、未認証の HEAD は 401 | verify で通過 | verify・AppearanceApiIT | build-and-test | Met |
| U8-NFR4.4 | construction/u8-instance-appearance/nfr-requirements/security-requirements.md 1節 | 使えないトークン付きの GET は 401、使えるトークン付きは 200 | verify で通過 | verify・AppearanceApiIT | build-and-test | Met |
| U8-NFR4.5 | construction/u8-instance-appearance/nfr-requirements/security-requirements.md 1節 | 応答は2項目だけ、設定された元の文字列が出ない | verify で通過 | verify・AppearanceApiIT・AppearanceStartupIT | build-and-test | Met |
| U8-NFR4.6 | construction/u8-instance-appearance/nfr-requirements/security-requirements.md 1節 | ログインなしの API の回数の制限を設けない | 確かめるテストが無く、記録だけの要件（残る危険として受け入れ済み） | なし | 持ち主の段なし（記録だけ。配備先が決まったときに前段の制限で扱う） | Unverified |
| U8-NFR4.7 | construction/u8-instance-appearance/nfr-requirements/security-requirements.md 1節・nfr-design/security-design.md 2.3 | 差し込み口の order 410 が重ならない、重なれば起動が止まる | verify で通過 | verify・AppearanceSecurityContributorTest | build-and-test | Met |
| U8-NFR4.8 | construction/u8-instance-appearance/nfr-requirements/security-requirements.md 2節 | 未認証の GET に共通のヘッダー（CSP・nosniff・DENY・Referrer-Policy・no-store） | verify で通過 | verify・AppearanceApiIT | build-and-test | Met |
| U8-NFR5.1 | construction/u8-instance-appearance/nfr-requirements/reliability-requirements.md 1節・nfr-design/reliability-design.md 3.1 | appearance は DB アクセスとほかの機能に依存しない | verify で通過 | verify・AppearanceBoundaryArchitectureTest・ArchitectureTest | build-and-test | Met |
| U8-NFR5.2 | construction/u8-instance-appearance/nfr-requirements/reliability-requirements.md 1節・nfr-design/reliability-design.md 3.2 | GET の前後で接続を借りた回数が増えない | verify で通過 | verify・AppearanceApiIT・appearance/testsupport/ConnectionAcquireCounter | build-and-test | Met |
| U8-NFR9.1 | construction/u8-instance-appearance/nfr-requirements/reliability-requirements.md 1節 | 設定が無い・空・許されない値で起動を止めず既定を返す | verify で通過 | verify・AppearanceStartupIT・AppearancePropertiesTest | build-and-test | Met |
| U8-NFR9.2 | construction/u8-instance-appearance/nfr-requirements/reliability-requirements.md 1節 | GET は常に 200 と許される値 | verify で通過 | verify・AppearanceResolverTest（jqwik） | build-and-test | Met |
| U8-NFR9.3 | construction/u8-instance-appearance/nfr-requirements/reliability-requirements.md 1節 | U8 だけの SLO は置かない、監視を常に動かしていない間は Unverified | 判定の扱いの記録で、この段では確かめない | なし | observability-setup・feedback-optimization | Unverified |
| U8-NFR9.4 | construction/u8-instance-appearance/nfr-requirements/observability-requirements.md 2節・nfr-design/observability-design.md 3.1 | 許されない値で項目ごとに WARN 1件、値を出さない、要求では出さない | verify で通過 | verify・AppearanceStartupIT・AppearanceServiceTest | build-and-test | Met |
| U8-NFR9.5 | construction/u8-instance-appearance/nfr-requirements/observability-requirements.md 2節 | GET は監査の出来事を出さない | verify で通過 | verify・AppearanceApiIT・audit/testsupport/AuditRows | build-and-test | Met |
| U8-NFR9.6 | construction/u8-instance-appearance/nfr-requirements/observability-requirements.md 3節 | HealthIndicator を足さない、/actuator/health は status だけ | verify で通過 | verify・AppearanceApiIT・既存の健全性のテスト | build-and-test | Met |
| U8-NFR9.7 | construction/u8-instance-appearance/nfr-requirements/tech-stack-decisions.md 2節・code-generation-plan.md 9節の決定 3 | 新しいパッケージと外したパッケージが行 80%・分岐 70% 以上、全体も同じ | appearance.service 100.0%・100.0%、appearance.web 100.0%・分岐なし、common.security 100.0%・100.0%、config 98.8%・76.7%、access.web 100.0%・93.8%。全体 98.8%・94.4% | verify の JaCoCo（パッケージごとの一覧） | build-and-test | Met |
| U8-NFR9.8 | construction/u8-instance-appearance/nfr-requirements/tech-stack-decisions.md 2節 | 判定の関数に jqwik の性質3つ、種を記録 | verify で通過 | verify・AppearanceResolverTest | build-and-test | Met |
| U8-計画358 | construction/u8-instance-appearance/code-generation/code-generation-plan.md 358 行 | 場面 appearance が k6 inspect で読み込め、閾値 p(95)<300 が載る | grafana/k6:2.3.0 の k6 inspect --include-system-env-vars で 24 場面すべて rc=0、閾値は表のとおり | k6 inspect（--network none）・perf/k6/scenarios.js | build-and-test | Met |
| U5-NFR6.1 | construction/u5-invitation-ui/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 4節 | 一覧の1ページ目が出るまで 2 秒以内、5 回すべて | 招待 21 件を用意（sent 21・failed 0）。108・113・111・121・139 ミリ秒、5/5 が 2000 以内 | e2eTest・060-invitation-accessibility.e2e.ts の注記 TIME | build-and-test | Met |
| U5-NFR6.2 | construction/u5-invitation-ui/nfr-requirements/performance-requirements.md 1節 | 2ページ目の行が出るまで 1.5 秒以内、5 回すべて | 79・76・83・63・54 ミリ秒、5/5 が 1500 以内 | e2eTest・060 の注記 TIME（nextPage） | build-and-test | Met |
| U5-NFR6.3 | construction/u5-invitation-ui/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 2節 | 1ページ 20 行、並べ替えない、重なった読み直しは最後の答えだけ | verify で通過 | verify・paging.test.ts・InvitationList.test.tsx・InvitationAdminPage.test.tsx | build-and-test | Met |
| U5-NFR6.4 | construction/u5-invitation-ui/nfr-requirements/performance-requirements.md 1節 | 要求の間はボタンを loading に、Modal を閉じない | verify で通過 | verify・InviteDialog.test.tsx・CancelConfirmDialog.test.tsx・InvitationList.test.tsx | build-and-test | Met |
| U5-NFR6.5 | construction/u5-invitation-ui/nfr-requirements/performance-requirements.md 1節 | 独自の時間切れを置かない、遅れ続ける間は Modal が閉じられない | 出典が確かめの対象にしないと決めた既知の限界の記録 | なし（README・計画 Step 19 の記録） | 持ち主の段なし（配備先が決まり U1 の NFR6.3 を見直すとき） | Unverified |
| U5-NFR6.6 | construction/u5-invitation-ui/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 5節 | 初回の JavaScript は gzip で 500KB 以内の目安、新しい実行時の依存なし | 123.1 KB、警告なし | verify・check-bundle-size.mjs | build-and-test | Met |
| U5-NFR1.1 | construction/u5-invitation-ui/nfr-requirements/security-requirements.md 1節・nfr-design/security-design.md 1節 | 画面はトークンと招待の URL を扱わない | verify で通過 | verify・InvitationList.test.tsx | build-and-test | Met |
| U5-NFR2.1 | construction/u5-invitation-ui/nfr-requirements/security-requirements.md 1節・nfr-design/security-design.md 2節 | 応答の値を保存・URL・console に出さない | verify で通過 | verify・InvitationAdminPage.test.tsx | build-and-test | Met |
| U5-NFR9.1 | construction/u5-invitation-ui/nfr-requirements/security-requirements.md 2節・nfr-design/security-design.md 3節 | detail を使わず code から文言、HTML を直接埋め込まない | verify で通過（リンタを含む） | verify・InvitationAdminPage.test.tsx・InviteDialog.test.tsx・invitationApi.test.ts・frontendLint | build-and-test | Met |
| U5-NFR9.2 | construction/u5-invitation-ui/nfr-requirements/security-requirements.md 2節 | 画面で隠すことをサーバー側の判定の代わりにしない、401・403 の扱い | verify で通過（サーバー側は InvitationAdminApiIT） | verify・InvitationAdminPage.test.tsx・registration.test.tsx（invitation）・InvitationAdminApiIT | build-and-test | Met |
| U5-NFR9.3 | construction/u5-invitation-ui/nfr-requirements/security-requirements.md 2節・nfr-design/security-design.md 5節 | CSP を変えない、埋め込みなし、CSP の違反なし | application.yaml に差分なし、index.html に埋め込みなし。060 の各組と測定で CSP の違反 0 件（違反で失敗する条件）で通過 | e2eTest（060「without CSP violations」）・frontend/dist/index.html | build-and-test | Met |
| U5-NFR9.4 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 2節 | U5 は新しい依存を足さない | コード生成の差分の確かめ（package.json・lockfile の差分 0）を読んで確かめた | u5-invitation-ui/code-generation/code-summary.md 4.3 | build-and-test | Met |
| U5-NFR9.5 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 2節 | 735ef04 の版を使い、中身を変えない | 固定先 735ef04、verify の vendorUnchanged 通過 | git submodule status・verify | build-and-test | Met |
| U5-NFR7.1 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 3節 | WCAG 2.1 AA（U4 の NFR7.1 のまま）と、状態を文字で・名前にメールアドレス・alertdialog などの守ること | 部品の守ることは verify で通過。ただし 060 で green・orange の primary の Button が 4.5:1 に届かない（既知の違反 invitation-invite-button 4 件・invitation-invite-submit 4 件）。固定先の更新は Intent の後（Q7: A・F1: A） | verify・e2eTest（060 の KNOWN）・build-and-test-questions.md Q7・F1 | Intent の後の別の作業（make-you-chic-ui の固定先の更新） | Not Met |
| U5-NFR7.2 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 3節 | 部品5つごとに vitest-axe 1件、違反 0 件 | verify で通過 | verify・InvitationList・InvitationUnavailableAlert・InviteDialog・CancelConfirmDialog・InvitationAdminPage の各 .test.tsx | build-and-test | Met |
| U5-NFR7.3 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 3節・nfr-design/logical-components.md 5節 | (a)6組・(b)8組で違反 0 件、はみ出しなし（既知の違反は primary の Button に限る、計画 9節の決定 4） | 74 状態で想定外の違反 0 件、はみ出しなし。既知の違反 invitation-invite-button 4 件・invitation-invite-submit 4 件。incomplete は color-contrast・bypass を記録（失敗にしない） | e2eTest・060 の注記 AXE・KNOWN・UNEXPECTED・INCOMPLETE・OVERFLOW | build-and-test | Met |
| U5-NFR7.4 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 3節 | 375px で6組、はみ出しなし、scrollable-region-focusable を含む違反 0 件 | 060 の (c) の組が通過、想定外の違反 0 件、OVERFLOW なし | e2eTest・060 の (c) の組 | build-and-test | Met |
| U5-NFR8.1 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 4節 | ja・en の文言がそろい既定は日本語 | verify で通過 | verify・InvitationAdminPage.test.tsx | build-and-test | Met |
| U5-NFR8.2 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 4節 | Table の labels と closeLabel を画面の言語で渡す | verify で通過 | verify・InvitationList.test.tsx・InviteDialog.test.tsx・CancelConfirmDialog.test.tsx | build-and-test | Met |
| U5-NFR9.6 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 5節 | 行 80%・分岐 70%、除外を増やさない、新しいファイルも目安 80%・70% | 全体 97.44%・92.67%。features/invitation の合計 96.19%・92.38%、最低は useInvitationAdmin.ts の分岐 82.4% | verify の frontendCoverage・frontend/coverage/coverage-summary.json | build-and-test | Met |
| U5-NFR9.7 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 5節 | ページの計算の性質（fast-check）、種を記録 | verify で通過 | verify・paging.test.ts | build-and-test | Met |
| U5-NFR9.8 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 5節 | formatDateTime を移した先のテストと DSL の画面のテストが通る | verify で通過 | verify・formatDateTime.test.ts・DslStatusPanel・DslConfirmDialog・DslHistoryTable の各 .test.tsx | build-and-test | Met |
| U5-NFR9.9 | construction/u5-invitation-ui/nfr-requirements/tech-stack-decisions.md 5節 | E2E-1 と既存の E2E が通る、060 は本数に数えない | e2eTest で 010〜040 の 6 件・090 の 1 件を含む 90 件通過 | e2eTest | build-and-test | Met |
| U5-前提 | construction/u5-invitation-ui/nfr-requirements/performance-requirements.md 前提 | 一覧・取り消し p95 1 秒、招待・送り直し p95 5 秒（同時 10 件） | 未測定（場面の用意と k6 inspect だけ） | perf/k6/scenarios.js・k6 inspect | performance-validation | Unverified |
| U6-NFR6.1 | construction/u6-registration-ui/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 2節 | 招待のリンクを開いてから登録のフォームが出るまで 2 秒以内、5 回すべて | 102・111・102・105・105 ミリ秒、5/5 が 2000 以内 | e2eTest・090-invitation-registration-flow.e2e.ts の注記 TIME | build-and-test | Met |
| U6-NFR6.2 | construction/u6-registration-ui/nfr-requirements/performance-requirements.md 1節 | 確かめ中は role=status、送信中は aria-busy、上限を置かない | verify で通過 | verify・RegistrationPage.test.tsx・RegistrationStatus.test.tsx・RegistrationForm.test.tsx | build-and-test | Met |
| U6-NFR6.3 | construction/u6-registration-ui/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 3節 | 登録の完了の画面は遅延読み込み、初回は 500KB 以内の目安 | 初回 123.1 KB、警告なし（U6 の画面は動的な塊） | verify・check-bundle-size.mjs | build-and-test | Met |
| U6-NFR1.1 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 1節 | トークンはフラグメントからだけ取り、1回だけ消す、本文にだけ入れる | verify で通過 | verify・RegistrationPage.test.tsx・registrationToken.test.ts | build-and-test | Met |
| U6-NFR1.2 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 1節・nfr-design/performance-design.md 2.1 (c) | トークンを保存・文言・console に出さない、実際のブラウザの保存にも無い | verify と e2eTest（090 の5回の確かめ (c)）で通過 | verify・e2eTest（090） | build-and-test | Met |
| U6-NFR1.3 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 1節・nfr-design/performance-design.md 2.1 (a) | アドレス欄に #token= が残らない（5回すべて） | 090 の5回すべてで通過 | e2eTest（090 の page.url の確かめ） | build-and-test | Met |
| U6-NFR1.4 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 1節 | Referrer-Policy と CSP を変えない、外部へのリンクと読み込みを置かない | コード生成の差分の確かめ（application.yaml・SecurityConfig.java の差分なし、外部の URL なし）を読んで確かめた。66fe981 と比べても差分なし | u6-registration-ui/code-generation/code-summary.md 4.3・git diff | build-and-test | Met |
| U6-NFR1.5 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 1節 | テストのトークンは本物にしない、報告をコミットしない、Gitleaks を通す | verify の Gitleaks 通過。e2e-results.json に a11y-sample-token・e2e-a11y-invitee・/register#token=・eyJ の形は 0 件 | verify（Gitleaks）・e2e-results.json の検索・.gitignore | build-and-test | Met |
| U6-NFR2.1 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 1節 | 招待のメールアドレスを URL・保存・console に出さない | verify と e2eTest（090 の完了の後の確かめ）で通過 | verify・e2eTest（090） | build-and-test | Met |
| U6-NFR3.1 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 1節 | 404 は理由によらず同じ表示、detail を出さない | verify で通過 | verify・failureKind.test.ts・RegistrationUnavailable.test.tsx | build-and-test | Met |
| U6-NFR9.1 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 2節 | ApiClient を通し、Authorization と Accept-Language を付けない | verify で通過 | verify・registrationApi.test.ts | build-and-test | Met |
| U6-NFR9.2 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 2節 | 想定外の応答（429 など）はリンクが使えないと見せない | verify で通過 | verify・failureKind.test.ts | build-and-test | Met |
| U6-NFR9.3 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 2節 | パスワードの欄の型と autocomplete、境界の値で送られない | verify で通過 | verify・RegistrationForm.test.tsx | build-and-test | Met |
| U6-NFR9.4 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 2節 | 続けて押しても完了の要求は1回だけ | verify で通過 | verify・RegistrationForm.test.tsx・RegistrationPage.test.tsx | build-and-test | Met |
| U6-NFR9.5 | construction/u6-registration-ui/nfr-requirements/security-requirements.md 2節・nfr-design/performance-design.md 2.1 (b) | CSP を変えず、E2E で CSP の違反が出ない | 070 の 40 状態と 090 の5回で CSP の違反 0 件（違反で失敗する条件）で通過 | e2eTest（070・090） | build-and-test | Met |
| U6-NFR9.6 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 2節 | U6 は新しい依存を足さない | コード生成の差分の確かめ（差分なし）を読んで確かめた | u6-registration-ui/code-generation/code-summary.md 4.3 | build-and-test | Met |
| U6-NFR9.7 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 2節 | 735ef04 の後に進め、中身を変えない | 固定先 735ef04、verify の vendorUnchanged 通過 | git submodule status・verify | build-and-test | Met |
| U6-NFR7.1 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 3節 | WCAG 2.1 AA、4.5:1・3:1、どの組み合わせでも、狭い幅でも崩れない | はみ出しは無いが、070 で green・orange の primary の Button が 4.5:1 に届かない（既知の違反 registration-submit-button 4 件）。固定先の更新は Intent の後（Q7: A・F1: A） | e2eTest（070 の KNOWN）・build-and-test-questions.md Q7・F1 | Intent の後の別の作業（make-you-chic-ui の固定先の更新） | Not Met |
| U6-NFR7.2 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 3節 | 部品5つごとに vitest-axe 1件、違反 0 件 | verify で通過 | verify・RegistrationPage・RegistrationStatus・RegistrationUnavailable・RegistrationLoggedInNotice・RegistrationForm の各 .test.tsx | build-and-test | Met |
| U6-NFR7.3 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 3節 | ready と unavailable で (a)(b)(c) の組、違反 0 件、はみ出しなし | 40 状態で想定外の違反 0 件、はみ出しなし。既知の違反は registration-submit-button 4 件（green・orange の ready） | e2eTest・070-registration-accessibility.e2e.ts の注記 AXE・KNOWN・UNEXPECTED・OVERFLOW | build-and-test | Met |
| U6-NFR7.4 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 3節 | lang・fieldset・矢印キー・role=alert・aria の各属性・フォーカス | verify で通過 | verify・RegistrationForm.test.tsx・RegistrationPage.test.tsx | build-and-test | Met |
| U6-NFR7.5 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 3節 | フォームの状態は確かめの API だけ差し替え、E2E-1 と分ける | 070 と 090 は別のファイルで通過。090 で本物の確かめの応答と見本の形が一致（realResponseShape） | e2eTest（070・090） | build-and-test | Met |
| U6-NFR8.1 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 4節 | registration. の鍵が ja・en でそろう、en でも「日本語」 | verify で通過 | verify・src/features/registration/registration.test.tsx | build-and-test | Met |
| U6-NFR8.2 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 4節 | 言語 en の招待では文言・html lang・Accept-Language が en | verify で通過 | verify・RegistrationPage.test.tsx | build-and-test | Met |
| U6-NFR9.8 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 5節 | 行 80%・分岐 70%、除外を増やさない | 全体 97.44%・92.67%。features/registration の合計 96.68%・86.79%、shared/validation 100.0%・97.22%、最低は RegistrationForm.tsx の分岐 78.57% | verify の frontendCoverage・frontend/coverage/coverage-summary.json | build-and-test | Met |
| U6-NFR9.9 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 5節 | コードポイントとバイトの関数の性質と境界、種を記録 | verify で通過 | verify・src/shared/validation/codePoints.test.ts・validateDisplayName.test.ts・validatePassword.test.ts（fast-check） | build-and-test | Met |
| U6-NFR9.10 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 5節 | E2E-1 を1本足し e2eTest で動かす、前のテストの状態に頼らない | 090 が通過（1件） | e2eTest・090-invitation-registration-flow.e2e.ts | build-and-test | Met |
| U6-NFR9.11 | construction/u6-registration-ui/nfr-requirements/tech-stack-decisions.md 5節 | 既存の画面のテストと既存の E2E が通り続ける | verify でフロントエンド 732 件通過、e2eTest 90 件通過 | verify・e2eTest | build-and-test | Met |
| U6-前提 | construction/u6-registration-ui/nfr-requirements/performance-requirements.md 前提 | リンクの確かめ・登録の完了の成功とも同時 10 件で p95 1 秒 | 未測定（場面の用意と k6 inspect だけ） | perf/k6/scenarios.js・k6 inspect | performance-validation | Unverified |
| U7-NFR6.1 | construction/u7-preferences-ui/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 4節 | プリファレンスのフォーム・パスワードの変更の画面が出るまで各 2 秒以内、5 回すべて | プリファレンス 103・68・70・104・120、パスワードの変更 51・49・49・59・49 ミリ秒、どちらも 5/5。E2E の WAR（PC の上、空の内部DB）での値 | e2eTest・080-preferences-accessibility.e2e.ts の注記 TIME | build-and-test | Met |
| U7-NFR6.2 | construction/u7-preferences-ui/nfr-requirements/performance-requirements.md 1節 | 保存を押してから Toast まで 1.5 秒以内 | 61・55・58・56・61 ミリ秒、5/5 | e2eTest・080 の注記 TIME（save） | build-and-test | Met |
| U7-NFR6.3 | construction/u7-preferences-ui/nfr-requirements/performance-requirements.md 1節 | 変更を押してから Toast まで 2.5 秒以内 | 813・818・812・816・828 ミリ秒、5/5 | e2eTest・080 の注記 TIME（passwordChange） | build-and-test | Met |
| U7-NFR6.4（要求の回数） | construction/u7-preferences-ui/nfr-requirements/performance-requirements.md 1節 | 要求の回数: 開くたびに GET 1回、パスワードの画面は API を呼ばない | verify で通過 | verify・PreferencesPage.test.tsx・PasswordChangePage.test.tsx | build-and-test | Met |
| U7-NFR6.4（API の時間） | construction/u7-preferences-ui/nfr-requirements/performance-requirements.md 前提・1節 | API の時間: 取得・保存 p95 1 秒、変更の成功 p95 2 秒、誤り p95 1 秒（同時 10 件） | 未測定（場面の用意と k6 inspect だけ） | perf/k6/scenarios.js・k6 inspect | performance-validation | Unverified |
| U7-NFR6.5 | construction/u7-preferences-ui/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 3節 | 送信の間は loading、要求は1回だけ、readOnly | verify で通過 | verify・PreferencesForm.test.tsx・PasswordChangeForm.test.tsx | build-and-test | Met |
| U7-NFR6.6 | construction/u7-preferences-ui/nfr-requirements/performance-requirements.md 1節・nfr-design/performance-design.md 5節 | 2つの画面は遅延読み込み、初回は 500KB 以内の目安 | 123.1 KB、警告なし | verify・check-bundle-size.mjs | build-and-test | Met |
| U7-NFR2.1 | construction/u7-preferences-ui/nfr-requirements/security-requirements.md 1節・nfr-design/security-design.md 1節 | 氏名を console・保存・URL に出さない | verify で通過 | verify・PreferencesPage.test.tsx | build-and-test | Met |
| U7-NFR9.1 | construction/u7-preferences-ui/nfr-requirements/security-requirements.md 2節 | パスワードはメモリだけ、型と autocomplete、成功で空に戻す | verify で通過 | verify・PasswordChangePage.test.tsx | build-and-test | Met |
| U7-NFR9.2 | construction/u7-preferences-ui/nfr-requirements/security-requirements.md 2節 | detail を使わず code から文言、HTML として差し込まない | verify で通過（リンタを含む） | verify・errorMessages.test.ts・frontendLint | build-and-test | Met |
| U7-NFR9.3 | construction/u7-preferences-ui/nfr-requirements/security-requirements.md 2節 | 今のパスワードの誤りで更新が呼ばれずログイン状態が変わらない | verify で通過（サーバー側は MePasswordApiIT） | verify・PasswordChangePage.test.tsx・MePasswordApiIT | build-and-test | Met |
| U7-NFR9.4 | construction/u7-preferences-ui/nfr-requirements/security-requirements.md 2節・nfr-design/security-design.md 5節 | 登録された画面の URL だけ、不正な項目で起動を止める、ログアウトは動く | verify で通過。e2eTest で 020・030・040・090 が通過 | verify・validateRegistrations.test.ts・ShellLayout.test.tsx・navigationItems.test.ts・e2eTest | build-and-test | Met |
| U7-NFR7.1 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 2節 | WCAG 2.1 AA、4.5:1・3:1、どの組み合わせでも満たす | 080 でアバター（76 件）・dark の FormField の誤りの文字（#_r_8_ など4つ各 10 件）・primary の Button（preferences-save-button 8 件・preferences-password-submit-button 8 件）が 4.5:1 に届かない（既知の違反）。make-you-chic-ui 側は 7865c28 で直ったが固定先の更新は Intent の後（Q7: A・F1: A） | e2eTest（080 の KNOWN）・build-and-test-questions.md Q7・F1 | Intent の後の別の作業（make-you-chic-ui の固定先の更新） | Not Met |
| U7-NFR7.2 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 2節 | 2つの画面（と計画で足した部品）ごとに vitest-axe 1件、違反 0 件 | verify で通過 | verify・PreferencesPage・PasswordChangePage・PreferencesLoadFailure・PreferencesForm・PasswordChangeForm・ShellLayout の各 .test.tsx | build-and-test | Met |
| U7-NFR7.3 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 2節・nfr-design/logical-components.md 5節 | 2つの画面ごとに (a)(b) の組、違反 0 件、はみ出しなし | 80 状態で想定外の違反 0 件、はみ出しなし。既知の違反はアバター 76 件・FormField の誤りの文字 40 件・primary の Button 16 件 | e2eTest・080 の注記 AXE・KNOWN・UNEXPECTED・OVERFLOW | build-and-test | Met |
| U7-NFR7.4 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 2節 | 375px で2つの画面ごとに6組、はみ出しなし、違反 0 件 | 080 の (c) の組が通過、想定外の違反 0 件、OVERFLOW なし | e2eTest・080 の (c) の組 | build-and-test | Met |
| U7-NFR7.5 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 2節 | fieldset・lang・aria の各属性・フォーカス・Toast・role=alert | verify で通過 | verify・PreferencesForm.test.tsx・PasswordChangeForm.test.tsx | build-and-test | Met |
| U7-NFR8.1 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 3節 | preferences. の鍵が ja・en でそろう | verify で通過 | verify・src/features/preferences/registration.test.tsx | build-and-test | Met |
| U7-NFR8.2 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 3節 | 理由と項目の名前のすべての組が ja・en の文言を持つ | verify で通過 | verify・errorMessages.test.ts | build-and-test | Met |
| U7-NFR9.5 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 4節 | 行 80%・分岐 70%、除外を増やさない | 全体 97.44%・92.67%。features/preferences の合計 96.51%・90.04%、最低は PreferencesForm.tsx の分岐 82.14% | verify の frontendCoverage・frontend/coverage/coverage-summary.json | build-and-test | Met |
| U7-NFR9.6 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 4節 | fieldErrors にどんな値でも例外を出さない（fast-check）、種を記録 | verify で通過 | verify・fieldErrors.test.ts | build-and-test | Met |
| U7-NFR9.7 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 4節 | 新しいパスワードの境界などを画面のテストで確かめる | verify で通過 | verify・PasswordChangePage.test.tsx | build-and-test | Met |
| U7-NFR9.8 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 4節 | 既存の画面のテスト・骨組みのテスト・既存の E2E が通り続ける | verify でフロントエンド 732 件通過、e2eTest 90 件通過 | verify・e2eTest | build-and-test | Met |
| U7-NFR9.9 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 4節 | 検査と測りは流れの本数に数えず、前提を自分で作る | 080 は別のファイルで、招待から作った利用者で測り通過 | e2eTest・080-preferences-accessibility.e2e.ts・frontend/e2e/support/registeredUser.ts | build-and-test | Met |
| U7-NFR9.10 | construction/u7-preferences-ui/nfr-requirements/tech-stack-decisions.md 4節 | 新しい依存を足さない、固定先 735ef04 | 固定先 735ef04、verify の vendorUnchanged 通過。依存の差分なしはコード生成の記録で確かめた | git submodule status・verify・u7-preferences-ui/code-generation/code-summary.md 3.4 | build-and-test | Met |

### 集計

| 単位 | 件数 | Met | Not Met | Unverified |
|---|---|---|---|---|
| U1 | 30 | 28 | 1 | 1 |
| U2 | 38 | 25 | 0 | 13 |
| U3 | 49 | 36 | 0 | 13 |
| U4 | 29 | 28 | 1 | 0 |
| U5 | 24 | 21 | 1 | 2 |
| U6 | 29 | 27 | 1 | 1 |
| U7 | 25 | 23 | 1 | 1 |
| U8 | 25 | 20 | 0 | 5 |
| 合計 | 249 | 208 | 5 | 36 |

- Unverified の持ち主の内訳: performance-validation 21 件（うち一部だけ確かめたもの U2-NFR6.5・U3-NFR6.2・U3-NFR5.2）、observability-setup 6 件（U8-NFR9.3 は feedback-optimization と共同）、deployment-pipeline・deployment-execution 6 件、持ち主の段なし（記録だけの要件・確かめの対象外）3 件。
- 下書きの「不可」29 件はすべて Unverified。下書きの「一部可」8 件のうち U1-NFR8.3 を除く7件（U1-NFR11.1・U2-NFR6.5・U2-NFR10.3・U2-NFR10.4・U3-NFR6.2・U3-NFR5.2・U3-NFR10.2）も Unverified とした。

### 判定に迷ったもの

- U4-NFR7.1・U5-NFR7.1・U6-NFR7.1・U7-NFR7.1（Not Met）: 目標は「どの組み合わせでもコントラスト 4.5:1」で、U4 の記録は既知の制約を受け入れたうえで「目標そのものは緩めていない」としている。既知の違反の扱いは実際のブラウザの検査（NFR7.3 系）の合否の条件として決めたもので、傘の目標 NFR7.1 を既知の違反で満たしたとは読めないため、実測（3.30:1 など）どおり Not Met とした。直しは make-you-chic-ui の 7865c28・310e1ec にあり、固定先の更新は Q7: A・F1: A で Intent の後。
- U4-NFR7.3・U5-NFR7.3・U6-NFR7.3・U7-NFR7.3（Met）: 出典で既知の違反（KNOWN_VIOLATIONS・AVATAR_KNOWN_COMBOS など）を許容すると決めた検査のため、依頼の決まりどおり想定外の違反 0 件をもって Met とし、既知の違反の件数と場所を Actual に書いた。
- U1-NFR1.2（Met）: 出典の RenderedMailTest は実在しない。RenderedMail の toString を伏せる確かめが RenderedMailInspectorTest（mail/template）の1件として実在し verify で通ったため Met とし、クラス名の差を Actual に書いた。
- U3-NFR8.1（Met）: 出典の InvitationProblemTypesTest は実在しない。5つの code と状態コードと ja・en の確かめが InvitationDomainValuesTest に実在し verify で通ったため Met とし、クラス名の差を Actual に書いた。
- U1-NFR8.3（Not Met）: (a)(b)(c)(e) は確かめた。(d) の CI はサブモジュールの取得は成功したが、2回とも別のテストの時間切れで verify が失敗したため Not Met とした（test-results.md の CI の節）。
- U1-NFR11.1（Unverified）: 画面の目視は依頼者の決定で Mailpit の API の確かめに代えたため、目標の「受け手の画面に出る」は一部しか確かめていない。持ち主は画面の目視が要るときの deployment-execution とした。
- U2-NFR2.1・U3-NFR2.1（Met）: 既存の InitialAdminInitializer・AuditEventListener のメールアドレスのキーは依頼者の判断で据え置き、出典で U2・U3 が通る経路に範囲を絞っているため、その範囲の verify の通過で Met とした。
- U8-NFR6.3（Met）: 目標は「起動時に作って変えない値で待ち合わせを置かない」という作りで、コードを読んで確かめた。確かめ方に挙がる同時 10 件の負荷の試験は U8-NFR6.1 と同じく performance-validation の持ち物で、この目標の合否には使っていない。
- U8-NFR6.4・U8-NFR4.6・U5-NFR6.5（Unverified）: テストの無い記録だけの要件、または出典が確かめの対象にしないと決めた既知の限界のため、持ち主の段は無く、見直しの時期を Owning Stage に書いた。
- U4-NFR6.5（Met）: 上限を置かず「測って記録する」目標のため、コード生成の値を写し、この段の WAR の大きさ（101,595,012 B）を添えて Met とした。
- U4-計画Step19（Met）: 「2回続けて流して同じ」はコード生成で確かめ済みの記録を読み、この段の e2eTest でも同じ結果（不安定 0）だったため Met とした。この段では 050 を2回は流していない。
- U4-NFR9.4・U5-NFR9.3・U6-NFR1.4 の CSP の差分: application.yaml と SecurityConfig.java を develop の 66fe981 と比べて差分が無いことで確かめた（この段の作業ツリーの変更は README・perf/ だけ）。
- U5-NFR9.4・U6-NFR9.6・U7-NFR9.10 の依存の差分なし: この段では package.json の差分を単位ごとに取り直していないため、コード生成の記録を読んで確かめた（文書の確かめとして Met）。
- U6-NFR9.9: 出典は単体テストのファイル名を挙げていないため、src/shared/validation の3つのテスト（codePoints・validateDisplayName・validatePassword、カバレッジ 100.0%・97.22%）の実在と verify の通過で Met とした。

## 4. 準備の判断

| 観点 | 判断 |
|---|---|
| build-ready | はい（手元の verify が成功） |
| test-ready | はい（手元のテスト・E2E がすべて通過。k6 の場面を用意） |
| deployment-ready | **いいえ**。理由は次の2つです |

- CI の verify が、2回とも時間切れで失敗しました。
  - `H2CompactionByPoolSuspensionIT` の接続の待ち 10 秒
  - `InvitationAdminPage.test.tsx` の1件の既定の 5 秒
- Not Met の目標が5件あります。
  - U1-NFR8.3 の (d)
  - U4〜U7 の NFR7.1（コントラスト 4.5:1。既知の違反）

**依頼者の決定（2026-09-28）**：「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」

- CI の2つの時間切れと NFR7.1 の Not Met は、この Intent では受け入れた失敗とします。
- 次の Intent で、make-you-chic-ui の固定先の更新と一緒に直します。
- team.md の「CI が失敗したら次に進む前に原因を直す」「不安定なテストは原因を直すまで統合しない」との差は、`test-results.md` の 8.1 節に記録しました。

## 5. 既知の制約と、後の段へ持ち越すもの

- **コントラストの既知の違反**：make-you-chic-ui の側で直っています（`7865c28`・`310e1ec`）。固定先の更新と既知の違反の扱いの取り外しは、この Intent の後の別の作業とします（Q7: A・F1: A）。
- **U7 のときに1回だけ落ちたテスト**：50 回の繰り返しで再現しませんでした。「不安定と確かめられていない」とします（Q1: A）。
- **Unverified の 36 件**：持ち主は次の段です。
  - performance-validation：21 件
  - observability-setup：6 件
  - deployment-pipeline・deployment-execution：6 件
  - 持ち主の段なし：3 件
- **この段で受け入れたこと**：
  - U1 の設計との差の4点（Q5: A）
  - U5・U6 の計画との差（Q6: A）
  - 初回の JavaScript の増加（123.1 KB、Q4: A）
- **この段で足したもの**：
  - k6 の場面 14 と `perf/README.md` の手順
  - README の差し込み口の表への U8（Q8: A）
- **k6 の場面について、承認の場で確かめたいこと**：
  - 登録の完了と取り消しの場面の回数の既定 100（決定の文言は「VU の数だけ」）
  - パスワードの変更の場面で、前の実行の続きから始めると最初のログインの失敗が監査に残ること（使い捨ての環境の中だけ）
  - `perf/k6/scenarios.js` の既存の行も Prettier の形に整えたこと（動きは変わらない）
- **記録の食い違い**：
  - U4〜U7 の traceability.json の target は、テストではなく本番のソースを指しています（`cross-unit-traceability.md` の「気づいた食い違い」）。
  - U1-NFR1.2・U3-NFR8.1 の出典のテストのクラス名は、実在するクラスと違います。同じ確かめが、別の名前のクラスで通っています。

## Sources

- 各単位の `code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`、`nfr-requirements/`・`nfr-design/` の文書（`construction/u1-mail/` 〜 `construction/u8-instance-appearance/`）
- `construction/build-and-test/test-results.md`・`cross-unit-traceability.md`・`build-and-test-questions.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- CI の2つの時間切れの原因は確かめていません（`test-results.md` の 6 節）。
