# コードの構成（mastersmith2）

各部品の責務と状態は `component-inventory.md`、部品の間の依存は `dependencies.md` に書き、ここでは置き場と決まりだけを書く。ファイルの数は 2026-09-30（コミット `31b980b`）にファイルを数えた値で、行の数は開発担当の走査の値である。2026-10-04（コミット `47541e3`）には、利用者の管理のパッケージ・画面の機能・移行 V9・E2E の一覧・サブモジュールの固定先だけを確かめ直した（ほかの数は前回のまま）。

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway の移行・SpotBugs の除外 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から呼ぶ。E2E は `frontend/e2e/` |
| `vendor/make-you-chic-ui/` | デザインシステム | Git サブモジュール（npm の `file:` の依存）。固定先 `3d9521a`（2026-10-04。前回の記録は `077f5b4`）。このリポジトリからは変えない |
| `vendor/java-mustache-processor/` | Mustache のエンジン | Git サブモジュール（Gradle の composite build）。固定先 `8d44c36`（`0.1.0`）。このリポジトリからは変えない |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`）、依存の取得元の固定と composite build（`settings.gradle.kts`）、1コマンドの検査 `verify` と `e2eTest` |
| `backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package-lock.json` | 版の固定 | Gradle と npm の依存の実際の版 |
| `config/` | 検査の設定 | ライセンスヘッダーのひな形、OSV の判定で止める画面の道具の一覧（`npm-build-tools.txt`） |
| `.github/` | CI と更新の通知 | `workflows/ci.yml`・`dependabot.yml` |
| `Dockerfile`・`compose.yaml`・`.env.example` | コンテナ | アプリのイメージとサービス、profile で起動する監視・メールの受け手・見本の対象DB |
| `docker/`・`perf/` | 付属の環境 | 手元の監視・OTLP の受け手・見本の対象DB・プールの道具、k6 の負荷の試験（2026-10-04 に `perf/README.md` の接続プールの節・`perf/k6/scenarios.js` の利用者の管理の場面・警報の3件を読んだ、K-20・K-23） |
| `README.md` | 文書 | 起動・環境変数・E2E・監視・警報と対応の手順・監査・招待・既知の制約・戻し方（1,082 行）。利用者の管理の節の有無は今回確かめていない（前回は無かった） |
| `aidlc/`・`.claude/` | ワークフローの記録と枠組み | アプリのソースではない |

## バックエンド（`backend/src/main/java/cherry/mastersmith/`、421 ファイル・約 30,100 行）

```
cherry.mastersmith
├── MastersmithApplication
├── config                                         Security の連鎖（SecurityConfig）・画面の配信・観測の設定（約 440 行）
├── common/{error,health,i18n,observability,paging,persistence,security,web}   Problem Details・ヘルスチェック・言語・TraceAspect・差し込み口の型・本文の上限（約 3,070 行）
├── auth/{domain,service,repository,web}           ログイン・ロック・トークン・ログアウト・定期の削除（約 3,020 行）
├── access/{domain,service,web}                    /api/admin/** の認可と 401・403・拒否の出来事（約 1,040 行、DB を読まない）
├── audit/{domain,service,repository}              出来事を確定の後に audit_events へ追記（約 1,590 行、Web の層なし）
├── user/{domain,service,repository,web}           利用者・パスワード・氏名と表示の設定・/api/me/**・初期管理者（約 3,370 行）
├── invitation/{domain,repository,service,web}     招待の管理 /api/admin/invitations と登録の完了（約 4,340 行）
├── useradmin/{domain,service,web}                 利用者の管理 /api/admin/users（Intent 260930-user-admin で作られた、28 ファイル）
├── mail/{config,domain,service,template,transport}   SMTP の送信と Mustache のテンプレート（約 1,880 行）
├── appearance/{config,service,web}                インスタンスの見た目の設定（約 580 行）
├── targetdb・dsl・dslmanage                        対象DB の読み取り、DSL の読み込み・検証・管理（流し読み）
```

- 層の決まりは `team.md` の Code Style のとおりで、ArchUnit で確かめる（全体の `ArchitectureTest` と機能ごとの `*BoundaryArchitectureTest` 9 本）。境界の中身は `dependencies.md`（K-3）。
- 4つの層に当たらない用途名の下位パッケージ（`mail/template`・`dslmanage/generate` など）を機能の中に置いてよい。機能ごとにエラーの code の一覧（`XxxProblemTypes`・`XxxProblemTypeCatalog`）を置き、1つの code に1つの状態コードを固定する。
- 設定とスキーマ（`backend/src/main/resources/`）: `application.yaml`（独自の設定は `mastersmith.*`）、`logback-spring.xml`、`mail/`・`dsl/`、`db/migration/`（Flyway、前進のみ）。移行は V1（空の土台）・V2（`users`）・V3（`login_attempt_states`・`refresh_tokens`）・V4（`audit_events`）・V5（DSL の表）・V6（監査の DSL の列と `actor_user_id`）・V7（利用者の氏名と表示の設定、監査の `target_user_id`・`target_invitation_id`）・V8（`invitations`）・V9（`V9__u1_user_suspension.sql`、利用停止。Intent `260930-user-admin` で足された。中身は読んでいない）。次に足す移行は V10 になる。`common/paging`・`common/persistence` も同じ Intent で足された（`useradmin` の import で確かめた。中身は読んでいない）。

### Intent `261003-user-admin-followup` に関わるファイル（2026-10-04 に深く読んだもの）

| 場所 | 役割 | 所見 |
|---|---|---|
| `frontend/src/features/useradmin/{ConfirmActionDialog,EditProfileDialog,UserRowActions,UserAdminPage}.tsx`・`useUserAdmin.ts`・`focusTarget.ts`・`UserAdminPage.test.tsx`・`EditProfileDialog.test.tsx` | 確かめの表示・入力・行の「操作」・閉じた後と成功の後のフォーカス・送信中の欄 | K-17・K-18・K-19 |
| `frontend/e2e/110-user-admin-flow.e2e.ts`・`120-user-admin-accessibility.e2e.ts`・`support/overflow.ts` | 利用者の管理の代表の流れ・axe・はみ出しの判定 | K-18 |
| `backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java`・`UserAdminListApiIT.java` | 印の変更の直後の 403 と監査の行の読み方 | K-21 |
| `audit/service/AuditEventListener.java` | 監査の記録の失敗の ERROR | K-20 |
| `user/service/UserAccountService.java`・`invitation/service/InvitationService.java` | 一意の制約の違反の扱い | K-24 |
| `backend/src/test/java/cherry/mastersmith/mail/config/MailConfigurationIT.java` | 起動時のメールの設定のログの確かめ | K-22 |
| `perf/README.md`・`perf/k6/scenarios.js`・`docker/monitoring/provisioning/alerting/mastersmith.yaml` | プールの上限 10 の手順・利用者の管理の場面・警報 | K-20・K-23 |

### 前回の Intent に関わったファイル（2026-09-30 に深く読んだもの。今回は確かめ直していない）

| 場所 | 役割 | 所見 |
|---|---|---|
| `user/domain/User.java`・`user/repository/UserRepository.java`・`db/migration/V2`・`V7` | 利用者のエンティティと問い合わせ（一覧・件数・印の変更・状態の問い合わせは無い） | K-1・K-4・K-5 |
| `user/service/UserAccountService.java`・`UserSummary.java`・`PasswordVerification.java` | ほかの機能に見せる利用者の口と、伏せ字の `toString` | K-1・K-8 |
| `user/service/InitialAdminInitializer.java` | 初期管理者の作成（メールアドレスの有無だけを見る） | K-4 |
| `auth/domain/LockPolicy.java`・`LockState.java`・`LoginAttemptState.java`・`auth/repository/LoginAttemptStateRepository.java`・`db/migration/V3` | ロックの判定と状態の表 | K-2 |
| `auth/service/LoginService.java`・`TokenRefreshService.java`・`auth/web/AccessTokenAuthenticationProvider.java` | 認証の3つの入口 | K-1 |
| `auth/domain/LoginFailureReason.java`・`TokenFailureReason.java`・`access/domain/AccessDeniedReason.java`・`audit/domain/AuditFailureReason.java` | 失敗の理由の網羅の列挙 | K-1・K-6 |
| `access/web/AdminAuthorizationManager.java`・`AdminSecurityContributor.java`・`access/domain/AdminPaths.java` | 管理者の判定と `/api/admin/**` | K-4・K-5 |
| `audit/domain/AuditEventType.java`・`AuditEventFactory.java`・`audit/service/AuditEventListener.java`・`AuditEventRecorder.java` | 監査の種類・写し取り・記録 | K-6 |
| `invitation/web/InvitationAdminController.java`・`InvitationPageResponse.java`・`InvitationRequestContextResolver.java`・`invitation/domain/InvitationPaging.java`・`invitation/service/InvitationService.java` | 管理の一覧と操作の見本 | K-5 |
| `backend/src/test/java/cherry/mastersmith/{ArchitectureTest,auth/AuthBoundaryArchitectureTest,audit/AuditBoundaryArchitectureTest,invitation/InvitationBoundaryArchitectureTest}.java` | 層と機能の間の境界の検査 | K-3 |
| `backend/build.gradle.kts` | テストの分け方・カバレッジの下限・`packagesJudgedByTotal`・SpotBugs の関門 | K-7 |
| `frontend/src/app/registry/types.ts`・`app/navigation/navigationItems.ts`・`features/*/registration.ts`・`features/invitation/api/invitationApi.ts`・`features/auth/authSession.ts` | 画面の差し込み口・管理の画面の見本・ログインの状態 | K-4・K-9 |

## テストの構成

- `backend/src/test/java/cherry/mastersmith/`（318 ファイル。`*Test` 151・`*IT` 109、ほかは補助）: 本体と同じパッケージ構成。単体は `*Test`（タスク `test`）、Spring と組み込みの H2 を起動する結合は `*IT`（タスク `integrationTest`）。機能ごとの補助は `<機能>/testsupport/`（例 `auth/testsupport/MutableClock`・`access/testsupport/AdminTestUsers`・`audit/testsupport/AuditRows`・`user/testsupport/TestUserAccounts`）、共通の補助は `common/testsupport/`（`LogEvents`・`JsonLogRecords`・`HttpTestClient`・`TestDatabase` ほか）。今回の Intent に関わるテストの一覧は `code-quality-assessment.md`。
- 対象DB の結合テストは Testcontainers（今回は流し読み）。
- 画面は対象と同じ場所の `*.test.ts(x)`（91 ファイル）。
- E2E は `frontend/e2e/`（2026-10-04 の時点で `*.e2e.ts` 13 本（010〜130）と補助 19 ファイル。前回は 10 本と 13 ファイル）。`backend/src/test/java/cherry/mastersmith/useradmin/` に利用者の管理のテスト（`domain`・`service`・`web`・`testsupport` と `UserAdminBoundaryArchitectureTest`）がある。

件数はファイルを数えた値で、実行の件数ではない。

## 画面の構成（`frontend/src/`、テストを除き 152 ファイル・約 13,600 行）

```
frontend/src
├── main.tsx
├── app/
│   ├── App.tsx・registry/・navigation/・routing/・layout/・i18n/
│   ├── display-settings/    利用者とインスタンスの見た目の設定の解決と保存
│   ├── login-state/・login-handoff/
│   └── pages/               共通の画面（NotFoundPage など）
├── shared/{api-client,format,validation}
└── features/{auth,admin,dsl,invitation,registration,preferences,useradmin}   機能ごとの画面と registration.ts（useradmin は order 230）
```

CSS は部品と同じ場所に素の CSS で置く（`team.md` の Code Style）。

### K-9 画面の差し込み口と、同じ型の管理の画面の見本

確かめた事実:

- 機能は `frontend/src/features/<featureId>/registration.ts` に `registration: FeatureRegistration` を名前付きで置くだけで読み込まれ、骨組みのファイルは書き換えない（`frontend/src/app/registry/types.ts`、`frontend/src/features/README.md`）。管理の画面は `access: 'ADMIN'`・`layout: 'SHELL'`、サイドバーの項目は `visibleWhen: 'ADMIN'`。表示を隠すのは表示の切り替えだけで、判定はサーバー（`types.ts` の注記）。
- サイドバーの順番（`order`）は、自分の設定 80・90（`features/preferences`）、ログイン 100（`features/auth`）、管理 200（`features/admin`）、DSL 210（`features/dsl`）、利用者の招待 220（`features/invitation`）、利用者の管理 230（`features/useradmin`、2026-10-04 に確かめた）。
- 文言は機能ごとに日英をそろえ、鍵は `<featureId>.` で始める（`FeatureMessages`）。
- 招待の管理の画面が同じ型の見本で、make-you-chic-ui の `Table`（ページ送りつき）・`Badge`・`Button`・`Modal`（確かめ）・`Toast`・`Alert` を使う（`frontend/src/features/invitation/InvitationList.tsx` 25 行ほか、流し読み）。`Switch`・`Checkbox`・`Select` も make-you-chic-ui にある。
- API の呼び出しは `frontend/src/shared/api-client/apiClient.ts` の `apiRequest`（401 `AUTHENTICATION_REQUIRED` で1回だけ更新して送り直す）。応答は項目を1つずつ確かめて決まった形の値にする（`frontend/src/features/invitation/api/invitationApi.ts` の `toInvitation`）。
- E2E の代表の流れは Intent ごとに1本まで（`team.md`）。管理者のログインと利用者を作る補助がある（`frontend/e2e/support/adminLogin.ts`・`invitationSeed.ts`・`registeredUser.ts`、名前と冒頭だけ）。axe の検査とコントラストの検査は流れの本数に数えない（`project.md` の Corrections）。

見立て（未検証）:

- 利用者の管理の画面は、`features/<新しい featureId>/` に招待の管理と同じ形（registration・api・一覧・確かめの Modal）で置ける。サイドバーの順番は 220 の近く（例 230）が候補になる。
- 招待の管理の hook（`features/invitation/useInvitationAdmin.ts` 472 行）と同じ形をまた書くと、読み込み・ページ送り・トースト・フォーカスの行き先の状態の管理が複製されやすい（`code-quality-assessment.md` の技術的負債）。
- 管理者の印の変更の後の画面の古い値の扱いは K-4（`architecture.md` の Interaction Diagrams 2）。
