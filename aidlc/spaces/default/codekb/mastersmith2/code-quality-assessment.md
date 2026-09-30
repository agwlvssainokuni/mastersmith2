# コードの質の評価（mastersmith2）

件数はファイルを数えた値・検索で数えた値で、テストを実行した値ではない（Gradle・npm・Docker は実行していない）。この文書に本文を書いた所見は K-7 で、ほかは持ち主の文書を参照する（一覧は `business-overview.md`）。パスは `backend/src/main/java/cherry/mastersmith/` の下のものはそれを省いて書く。

## テスト

| 対象 | 置き場と数（2026-09-30 にファイルを数えた値） | 道具 |
|---|---|---|
| バックエンドの単体 | `backend/src/test/java/`、`*Test` 151 ファイル（タスク `test`） | JUnit Jupiter・AssertJ・jqwik・ArchUnit |
| バックエンドの結合 | 同上、`*IT` 109 ファイル（タスク `integrationTest`）。内部DB は組み込みの H2 | Spring Boot Test・Testcontainers（対象DB だけ）・SubEthaSMTP |
| 画面 | `frontend/src/**/*.test.ts(x)` 91 ファイル | Vitest・Testing Library・user-event・vitest-axe・fast-check |
| E2E | `frontend/e2e/*.e2e.ts` 10 ファイル。`verify` と CI の外 | Playwright・axe-core |

### 今回の Intent に関わる既存のテスト（ファイル名で確かめた）

| 機能 | テスト | 補助 |
|---|---|---|
| `user` | `UserAccountServiceTest`・`UserCreationIT`・`UserRepositoryIT`・`UserSchemaIT`・`V7MigrationIT`・`V7BackwardCompatibilityIT`・`InitialAdminInitializerTest`・`InitialAdminIT`・`MePasswordApiIT`・`MePreferencesApiIT`・`MeSecretLeakIT` | `user/testsupport/TestUserAccounts`・`MeApi`・`TestPasswordChangeBarrier` |
| `auth` | `LockPolicyTest`・`LoginServiceTest`・`LoginConcurrencyIT`・`LoginAttemptStateRepositoryIT`・`RefreshTokenRepositoryIT`・`TokenRefreshServiceTest`・`RefreshConcurrencyIT`・`AccessTokenApiIT`・`LoginApiIT`・`TokenApiIT`・`AuthEventsIT`・`AuthSecretLeakIT`・`AuthBoundaryArchitectureTest` | `auth/testsupport/MutableClock`・`AuthApi`・`AuthTestTokens`・`CapturedAuthenticationEvents` |
| `access` | `AdminAccessIT`（401・403・200）・`AdminPathBoundaryIT`・`ApiDefaultAccessIT`・`AccessDeniedEventsIT`・`AccessSecretLeakIT` | `access/testsupport/AdminTestUsers`・`PublicApiTestRules` |
| `audit` | `AuditEventFactoryTest`・`AuditEventListenerTest`・`AuditAuthenticationEventsIT`・`AuditRollbackIT`・`AuditWriteFailureIT`・`AuditTraceIdIT`・`AuditSecretLeakIT`・`PasswordChangedAuditIT`・`AuditBoundaryArchitectureTest` | `audit/testsupport/AuditRows` |
| `invitation` | `InvitationAdminApiIT`・`InvitationAuditIT`・`InvitationConcurrencyIT`・`InvitationPagingTest`・`InvitedPersonAuthenticationIT`・`InvitationSecretLeakIT`・`InvitationBoundaryArchitectureTest` | — |
| 全体 | `ArchitectureTest` | `common/testsupport/`（`LogEvents`・`JsonLogRecords`・`HttpTestClient`・`TestDatabase` ほか） |

`team.md` の Testing Posture が認証・認可・監査に求めるテスト（401・403・200、ロックの境界、監査の必須項目、秘密の漏えい）は、上の既存のテストの形（`AdminAccessIT`・`LockPolicyTest`・`AuditRows`・`*SecretLeakIT`）に足せる見込み。時刻は `auth/testsupport/MutableClock` で動かせる（ロックの解除時刻の境界、K-2）。

## カバレッジ

- バックエンド: JaCoCo。単体と結合を合わせた全体で行 80%・分岐 70%、加えてパッケージごとに同じ下限（`backend/build.gradle.kts` 236〜270 行）。
- 画面: `frontend/vitest.config.ts` の `coverage.thresholds`（`lines: 80`・`branches: 70`）。除外は `src/main.tsx`・`*.d.ts`・テスト自身。
- 計測から外すのは起動クラス・設定値だけのクラス・自動生成・`vendor/` に限る（`team.md` の Testing Posture）。

### K-7 触るとカバレッジの下限を満たす作業が付くパッケージ

確かめた事実（アーキテクトが `backend/build.gradle.kts` 209〜234 行で確かめ直した）:

- `packagesJudgedByTotal`（パッケージごとの下限から外し、全体の合計で判定する既存のパッケージ）は 12 個: `access.domain`・`access.service`・`audit.repository`・`auth.domain`・`auth.repository`・`common.error.domain`・`common.error.service`・`common.error.web`・`common.health`・`common.i18n.domain`・`common.observability`・`common.web`。`team.md` の記述（12 パッケージ）と一致する。
- 一覧を作った時点の実測で `auth.repository` は単独で分岐 50.0% だった（同ファイル 210〜212 行の注記。今の値は測っていない）。
- `user.*`・`invitation.*`・`auth.service`・`auth.web`・`access.web`・`audit.domain`・`audit.service`・`config`・`common.security` は既にパッケージごとの下限の対象。新しく作るパッケージは一覧に無いので自動で対象になる。
- `team.md` の Testing Posture により、一覧のパッケージに手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外す。カバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する。一覧を増やす変更はしない。

見立て（未検証）:

- 利用停止（K-1）とロックの解除（K-2）は `auth.domain`（`LoginFailureReason`・`TokenFailureReason`）・`auth.repository`（ロックの解除、リフレッシュトークンをまとめて無効にする問い合わせ）・`access.domain`（`AccessDeniedReason`）に及びうる。監査の記録を変えるなら `audit.repository` もありうる。とくに `auth.repository` は分岐の不足が大きかったため、手を入れる Bolt の作業が重くなりうる。
- どのパッケージに手が入るかは設計（K-3 の置き場）で決まる。コード生成の計画で、手を入れる一覧のパッケージを洗い出す必要がある。

## 検査と CI

- 形と静的検査: Spotless（palantir-java-format、インデント4・1行120文字、`/* */` のライセンスヘッダー）、SpotBugs ＋ FindSecBugs と関門 `spotbugsGate`（priority 1 と、`SQL_` で始まるもの・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらず失敗。`backend/build.gradle.kts` 295〜368 行）、ArchUnit の構造の検査（全体の `ArchitectureTest` と機能ごとの `*BoundaryArchitectureTest` 9 本、K-3）。
- 画面: Prettier・oxlint（correctness を error）・ESLint（react-hooks）・Stylelint・`tsc --noEmit`・ライセンスヘッダーの検査（`frontend/scripts/check-license-header.mjs`）・バンドルの大きさ（`scripts/check-bundle-size.mjs`）。
- 秘密情報: Gitleaks（`.gitleaks.toml`、pre-commit と `verify`）。依存: OSV-Scanner（`verify` の `osvScan`）と Dependabot。
- SpotBugs の除外は理由つきの1件だけ（`backend/config/spotbugs-exclude.xml`: `LoginAttemptStateRepository.lockDummyForUpdate` の `PREDICTABLE_RANDOM`）。
- CI: `.github/workflows/ci.yml` の1本。`develop` へのプッシュ・タグ `v*`・手動で `./gradlew verify` を流し、WAR を成果物として保存する（タグのときはリリースに添付）。Actions はコミットのハッシュで固定。E2E は CI の外（`team.md`）。

## 文書

- `README.md`（1,082 行）: 取得と準備、1コマンドの検査、E2E、起動、戻し方、環境変数（ロックのしきい値 `MASTERSMITH_AUTH_LOCK_THRESHOLD` 既定 5・時間 `MASTERSMITH_AUTH_LOCK_DURATION` 既定 30m、407〜408 行）、API のアクセス制御、監査ログ、各機能の節、ライセンス。利用者の管理（一覧・停止・解除）の節は無い。
- コードのコメント: Javadoc・JSDoc は日本語で、設計の文書の番号（BR・NFR・契約 C・ADR）と決定の経緯を丁寧に書く。`TODO`・`FIXME`・`HACK` は本体（`backend/src/main`・`frontend/src`）に 0 件（開発担当の検索）。
- `frontend/src/features/README.md`: 機能の登録・表示の設定・入力の確かめの関数・ユーザーメニューの使い方。

## 技術的負債の一覧

| 項目 | 内容（場所） | 所見 | 重さ |
|---|---|---|---|
| 利用者の状態を見る場所が3つに分かれる | ログインの照合・トークンの更新・アクセストークンの認証がどれも「いるか」だけを見る | K-1（`architecture.md`） | 高（今回の Intent で必ず当たる） |
| 最後の管理者を守る仕組みが無い | 印を外す・停止する操作を止める口が無く、初期管理者の自動作成も回復の道にならない | K-4（`architecture.md`） | 高（要件で決める） |
| ロックの状態の行の無い利用者がありうる | 表 `login_attempt_states` は外部キーを持たず、行は作成の知らせか初めてのログインで作られる | K-2（`component-inventory.md` の `auth`） | 中 |
| 監査が機能を足すたびに大きくなる | `audit/service/AuditEventListener.java` 406 行・`audit/domain/AuditEvent.java` 374 行・`audit/domain/AuditEventFactory.java` 363 行が、機能ごとの出来事の受け取りと写し取りを1か所に積む。網羅の `switch` で足し漏れはコンパイルで気づける | K-6（`component-inventory.md` の `audit`） | 中 |
| リフレッシュトークンをまとめて無効にする問い合わせが無い | `auth/repository/RefreshTokenRepository.java` は1件ずつの `revokeIfActive` と期限切れの削除だけ。パスワードの変更（`user/service/UserPreferencesService.java`）もほかの端末のリフレッシュトークンを無効にしない（`PasswordChangedEvent` を受けるのは `audit` だけ、開発担当の検索） | K-1 の関連 | 中 |
| カバレッジの作業が付くパッケージ | `packagesJudgedByTotal` の 12 個 | K-7（この文書） | 中 |
| 要求の文脈の読み取りの複製 | `user/web/MeRequestContextResolver.java`（111 行）・`invitation/web/InvitationRequestContextResolver.java`（98 行）が同じ形（`Authentication#getName()` を数として読む、送り手の情報を作る、401 の問題の種類を起動時に引く）。`dslmanage/web/DslRequestContextResolver.java` もある（アーキテクトがファイル名で確かめた。中身は読んでいない）。`invitation` は `auth` に依存できない境界のため複製している | K-3 の関連 | 低〜中（足すと4つ目） |
| 画面の管理の機能の大きめの hook | `frontend/src/features/invitation/useInvitationAdmin.ts` 472 行・`frontend/src/features/dsl/useDslAdmin.ts` 492 行。同じ型の画面を足すと状態の管理を複製しやすい（見立て） | K-9 の関連 | 低 |
| 画面の管理者の印が古いまま残る | `frontend/src/features/auth/authSession.ts` のモジュールの変数 | K-4 | 低（サーバー側の判定は正しい） |
| 単一インスタンス前提 | 内部DB が組み込みの H2、ロックの判定は行の排他と待ちの上限 3 秒 | 前の Intent からの既知の決定 | 低（今の配備先では問題にならない） |
| 抑止の注記 | `@SuppressWarnings` が本体に2件（`common/observability/SanitizingLogRecordExporter.java` 141 行の `deprecation`、`common/error/web/DefaultErrorResponseWriter.java` 79 行の `unchecked`）、`oxlint-disable` が1件（`frontend/src/types/vitest-axe-matchers.d.ts` 23 行）。どれも理由が明らか | — | 低 |
