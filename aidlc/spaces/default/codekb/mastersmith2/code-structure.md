# コードの構成（mastersmith2）

各部品の責務と読みの深さは `component-inventory.md`、部品の間の依存は `dependencies.md` に書き、ここでは置き場と決まりだけを書く。構成はディレクトリとファイルの名前の単位で確かめた（流し読み）。件数は開発担当とアーキテクトがファイルを数えた値で、実行の件数ではない。

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway の移行・SpotBugs の除外 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から呼ぶ。E2E は `frontend/e2e/` |
| `vendor/make-you-chic-ui/` | デザインシステム | Git サブモジュール（npm の `file:` の依存）。このリポジトリからは変えない |
| `vendor/java-mustache-processor/` | Mustache のエンジン | Git サブモジュール（Gradle の composite build）。このリポジトリからは変えない |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`）、依存の取得元の固定と composite build、1コマンドの検査 `verify` と `e2eTest` |
| `backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package-lock.json` | 版の固定 | Gradle と npm の依存の実際の版 |
| `config/` | 検査の設定 | ライセンスヘッダーのひな形、OSV の判定で止める画面の道具の一覧 |
| `.github/` | CI と更新の通知 | `workflows/ci.yml`・`dependabot.yml` |
| `Dockerfile`・`compose.yaml`・`.env.example` | コンテナ | アプリのイメージとサービス、profile で起動する監視・メールの受け手・見本の対象DB |
| `docker/`・`perf/` | 付属の環境 | 負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`）、手元の監視、見本の対象DB の初期化、接続プールの道具、k6 の台本と手順 |
| `README.md` | 文書 | 起動・配備・設定の一覧・既知の制約（1,188 行） |
| `aidlc/`・`.claude/` | ワークフローの記録と枠組み | アプリのソースではない |

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

```
cherry.mastersmith
├── MastersmithApplication
├── config                                   Security の連鎖・画面の配信・観測の設定
├── common/{error,health,i18n,observability,paging,persistence,security,web}
├── auth/{domain,service,repository,web}     ログイン・ロック・トークン・ログアウト・ロックの解除
├── access/{domain,service,web}              /api/admin/** の認可と 401・403
├── audit/{domain,service,repository}        出来事を確定の後に audit_events へ追記（Web の層なし）
├── user/{domain,service,repository,web}     利用者・パスワード・表示の設定・/api/me/**・初期管理者
├── useradmin/{domain,service,web}           利用者の管理 /api/admin/users
├── invitation/{domain,lock,repository,service,web}   招待の管理と登録の完了
├── mail/{config,domain,service,template,transport}   SMTP の送信と Mustache のテンプレート
├── appearance/{config,service,web}          インスタンスの見た目の設定
└── targetdb・dsl・dslmanage                  対象DB の読み取り、DSL の読み込み・検証・管理
```

- 層の決まりは `team.md` の Code Style のとおりで、ArchUnit で確かめる（全体の `ArchitectureTest` と機能ごとの `*BoundaryArchitectureTest`）。
- 4つの層に当たらない用途名の下位パッケージ（`invitation/lock`・`mail/transport`・`dslmanage/generate` など）を機能の中に置いてよい。機能ごとにエラーの code の一覧（`XxxProblemTypes`・`XxxProblemTypeCatalog`）を置き、1つの code に1つの状態コードを固定する。
- `common/persistence`（行の排他の結果の型と失敗の判定）と `common/paging`（ページ送り）は Intent `260930-user-admin` で足された共通の部品で、`common/persistence` は今回の部品 ID `common-persistence` になった。
- 設定とスキーマ（`backend/src/main/resources/`）: `application.yaml`（独自の設定は `mastersmith.*`）、`logback-spring.xml`、`mail/`・`dsl/`、`db/migration/`（Flyway、前進のみ）。移行は V1（土台）・V2（`users`）・V3（ロックの状態とリフレッシュトークン）・V4（`audit_events`）・V5（DSL）・V6（監査の DSL の列と `actor_user_id`）・V7（利用者の表示の設定、監査の対象の列）・V8（`invitations`）・V9（利用停止）。次に足す移行は V10 になる（K-25 で `audit_events` の列の扱いを変える場合）。

### 今回の Intent に関わるファイル（2026-10-04 に深く読んだもの）

| 場所 | 役割 | 所見 |
|---|---|---|
| `user/service/InitialAdminInitializer.java`・`InitialAdminProperties.java`・`UserAccountService.java`・`UserCreatedEvent.java`・`UserAccountConfig.java`、`.env.example`・`README.md` の初期管理者の記述 | 初期管理者の作成と設定、作成の知らせ | K-25 |
| `audit/domain/AuditEvent.java`・`AuditEventType.java`・`AuditEventFactory.java`・`audit/service/AuditEventListener.java`・`AuditEventRecorder.java`・`db/migration/V4__u4_audit_event.sql` | 監査の種類・行の形・記録の流れ | K-25 |
| `auth/service/LoginService.java`・`TokenRefreshService.java`・`LoginAttemptStateInitializer.java`、`user/service/UserSummary.java`・`PasswordProperties.java`、`user/repository/UserRepository.java`、`perf/k6/scenarios.js` | ログインの流れと停止の判定、bcrypt の cost、負荷の場面 | K-26 |
| `useradmin/service/UserAdminService.java`・`UserAdminBarrier.java`・`useradmin/web/UserAdminController.java`・`user/repository/UserRowLockRepository.java`・`common/persistence/RowLockFailures.java`・`RowLockAttempt.java`・`backend/src/main/resources/logback-spring.xml`・`backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyApiIT.java` | BUSY の作り方・L3 と L4 のログ・既存のテスト | K-27 |
| `common/error/web/GlobalExceptionHandler.java`・`ErrorPathController.java`・`auth/web/AccessTokenAuthenticationProvider.java`・`backend/src/main/resources/application.yaml` | 例外の受け口と ERROR、フィルターの中の接続の取得、プールの設定 | K-28 |
| `frontend/src/features/useradmin/EditProfileDialog.tsx`・`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/RadioGroup/RadioGroup.tsx` | 送信中の言語の欄 | K-29 |
| `backend/build.gradle.kts` | `packagesJudgedByTotal` とカバレッジの検証 | K-30 |
| `compose.yaml`・`docker/perf/compose.yaml`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`・`Dockerfile`・`.github/dependabot.yml`・`gradle/libs.versions.toml` | イメージの版とダイジェスト、更新の通知 | K-31 |

## テストの構成

- `backend/src/test/java/cherry/mastersmith/`: 本体と同じパッケージ構成。単体は `*Test`（173 件、タスク `test`）、Spring と組み込みの H2 を起動する結合は `*IT`（133 件、タスク `integrationTest`）。漏えいの確かめ `*SecretLeakIT` は 12 件。機能ごとの補助は `<機能>/testsupport/`、共通の補助は `common/testsupport/`（`JsonLogRecords`・`RowLockHolder`・`TestDatabase` ほか、名前だけ確かめた）。
- 対象DB の結合テストは Testcontainers（MySQL・MariaDB・PostgreSQL）。イメージは `targetdb/testsupport/TargetDbImages.java` の `イメージ名@ダイジェスト` で固定する（K-31）。
- 画面は対象と同じ場所の `*.test.ts(x)`（110 件）。
- E2E は `frontend/e2e/`（`*.e2e.ts` 13 本）。`verify` と CI の外。

## 画面の構成（`frontend/src/`）

```
frontend/src
├── main.tsx
├── app/        骨組み・registry・navigation・routing・layout・i18n・display-settings・login-state・login-handoff・pages
├── shared/     api-client・format・validation
└── features/   admin・auth・dsl・invitation・preferences・registration・useradmin（各 registration.ts で登録）
```

- 機能は `features/<featureId>/registration.ts` を置くだけで読み込まれ、骨組みのファイルは書き換えない。機能どうしは直接 import し合わず、共有するものは `shared/` に置く（`team.md` の Code Style）。
- CSS は部品と同じ場所に素の CSS で置く。
