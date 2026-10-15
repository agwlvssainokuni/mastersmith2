# コードの構成（mastersmith2）

各部品の責務と読みの深さは `component-inventory.md`、部品の間の依存は `dependencies.md` に書き、ここでは置き場と決まりだけを書く。構成はディレクトリとファイルの名前の単位で確かめた（流し読み）。件数は開発担当がファイルを数えた値で、実行の件数ではない。

> 2026-10-15（コミット `4f6c27d`）の更新: バックエンドと画面の構成の図に `261004-role-menu` で増えたパッケージ・ディレクトリを足し（`ls` で確かめた）、K-48 と今回深く読んだファイルの表を足した。ファイルの件数（480・380・110 など）と移行の一覧は前回（コミット `d5aea52`）の値のままで、今回は数え直していない。

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway の移行・SpotBugs の除外 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から呼ぶ。E2E は `frontend/e2e/` |
| `vendor/make-you-chic-ui/` | デザインシステム | Git サブモジュール（固定先 `5bf1ffe`、npm の `file:` の依存）。このリポジトリからは変えない |
| `vendor/java-mustache-processor/` | Mustache のエンジン | Git サブモジュール（固定先 `8d44c36`、Gradle の composite build）。このリポジトリからは変えない |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`）、依存の取得元の固定と composite build、1コマンドの検査 `verify` と `e2eTest` |
| `backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package-lock.json` | 版の固定 | Gradle と npm の依存の実際の版 |
| `config/` | 検査の設定 | ライセンスヘッダーのひな形、OSV の判定で止める画面の道具の一覧 |
| `.github/` | CI と更新の通知 | `workflows/ci.yml`・`dependabot.yml` |
| `Dockerfile`・`compose.yaml`・`.env.example` | コンテナ | アプリのイメージとサービス、profile で起動する監視・メールの受け手・見本の対象DB（今回は読み直していない） |
| `docker/`・`perf/` | 付属の環境 | 負荷の試験の使い捨ての環境、手元の監視、k6 の台本と手順（今回は読み直していない） |
| `README.md` | 文書 | 起動・配備・設定の一覧・既知の制約（今回は読み直していない） |
| `aidlc/`・`.claude/` | ワークフローの記録と枠組み | アプリのソースではない |

## バックエンド（`backend/src/main/java/cherry/mastersmith/`、`src/main` に 480 ファイル）

```
cherry.mastersmith
├── MastersmithApplication
├── config                                   Security の連鎖（SecurityConfig）・画面の配信・観測・転送ヘッダー
├── common/{error,health,i18n,observability,paging,persistence,security,web}
├── auth/{domain,service,repository,web}     ログイン・ロック・トークン・ログアウト・初期管理者の救済の受け手
├── access/{domain,service,web}              /api/admin/** の認可、/api/** の既定、401・403
├── audit/{domain,service,repository}        出来事を確定の後に audit_events へ追記（Web の層なし）
├── user/{domain,service,repository,web}     利用者・管理者の印と停止の列・表示の設定・/api/me/**・初期管理者
├── useradmin/{domain,service,web}           利用者の管理 /api/admin/users
├── invitation/{domain,lock,repository,service,web}   招待の管理と登録の完了
├── mail/{config,domain,service,template,transport}   SMTP の送信と Mustache のテンプレート
├── appearance/{config,service,web}          インスタンスの見た目の設定
├── targetdb/{config,domain,repository,service}       対象DB のスキーマの読み取り
├── dsl/{domain,parse,validate,service}      DSL の型・読み込み・検証・適用中のモデル（メニューの木を含む）
├── dslmanage/{domain,generate,repository,service,web} DSL の管理の API と既定の DSL の生成
├── role/{domain,repository,service,store,transfer,web} ロール・権限・割り当て・作業ロール・権限の YAML（261004-role-menu）
├── group/{domain,repository,service,store,web}         グループ（261004-role-menu）
└── navigation/{domain,service,web}                     権限で枝を落とした N 階層のメニュー（261004-role-menu）
```

- 層の決まりは `team.md` の Code Style のとおりで、ArchUnit で確かめる（全体の `ArchitectureTest` と機能ごとの `*BoundaryArchitectureTest`。`access` には無い。K-38）。
- 4つの層に当たらない用途名の下位パッケージ（`invitation/lock`・`mail/transport`・`dsl/parse`・`dsl/validate`・`dslmanage/generate` など）を機能の中に置いてよい。機能ごとにエラーの code の一覧（`XxxProblemTypes`・`XxxProblemTypeCatalog`）を置き、1つの code に1つの状態コードを固定する（`access` は `access/domain/AccessProblemTypes.java`）。
- 安全の決まりの差し込み口は各機能の `web` の `*SecurityContributor`（order は機能の名前で 100 台ずつ: auth 110・access 210・invitation 310・appearance 410）と、`/api/**` の既定 `access/web/AdminApiDefaultAccess.java`。型は `common/security/`（`SecurityRuleContributor`・`ApiDefaultAccess`・`SecurityExtensionValidator`・`ErrorResponseWriter`）。

### 設定とスキーマ（`backend/src/main/resources/`）

- `application.yaml`（独自の設定は `mastersmith.*`）、`logback-spring.xml`、`mail/`、`dsl/dsl-schema-v1.json`（DSL の JSON Schema。`menus`・`menuItem` の定義を含む。K-37）。
- `db/migration/`（Flyway、前進のみ）: V1（土台）・V2（`users`、`admin_flag` を含む）・V3（ロックの状態とリフレッシュトークン）・V4（`audit_events`、`event_type VARCHAR(32)`）・V5（DSL）・V6（監査の DSL の列と `actor_user_id`）・V7（利用者の表示の設定、監査の対象の列）・V8（`invitations`）・V9（利用停止）。役割・権限の表は無く（K-32）、次に足す移行は V10 になる。

### 今回の Intent に関わるファイル（2026-10-04 に深く読んだもの）

| 場所 | 役割 | 所見 |
|---|---|---|
| `auth/domain/AuthenticatedUser.java`・`auth/web/AuthenticatedUserToken.java`・`AccessTokenAuthenticationProvider.java`・`AuthSecurityContributor.java`・`CurrentUserResponse.java`・`TokenResponse.java`・`auth/service/AccessTokenService.java`、`user/domain/User.java`・`user/service/UserSummary.java`・`user/repository/UserRepository.java`、`db/migration/` | 主体・トークン・利用者の列 | K-32 |
| `access/domain/AdminPaths.java`・`AccessDeniedReason.java`・`AccessProblemTypes.java`、`access/web/AdminAuthorizationManager.java`・`AdminSecurityContributor.java`・`AdminApiDefaultAccess.java`・`AdminCheckController.java`・`AdminAccessDeniedHandler.java`、`common/security/`、`config/SecurityConfig.java`、`appearance/web/AppearanceSecurityContributor.java`・`invitation/web/InvitationSecurityContributor.java` | URL の決まりと判定、401・403 | K-33 |
| `useradmin/domain/AdminOperation.java`・`RejectionReason.java`、`useradmin/service/UserAdminService.java`・`useradmin/web/AdminUser.java`、`backend/src/test/java/cherry/mastersmith/useradmin/UserAdminBoundaryArchitectureTest.java` | 管理者の印の業務の決まり | K-34 |
| `frontend/src/app/registry/`（`types.ts`・`registrationModules.ts`・`loadRegistrations.ts`・`validateRegistrations.ts`）・`app/navigation/navigationItems.ts`・`app/layout/ShellLayout.tsx`・`app/routing/decideRoute.ts`・`AppRouter.tsx`・`app/admin-forbidden/AdminForbiddenProvider.tsx`・`app/pages/HomePage.tsx`・`shared/api-client/adminForbidden.ts`・`features/auth/loginStateProvider.ts`・`features/admin/registration.ts` | 画面の出し分けと 403 | K-35 |
| `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/AppShell.tsx`・`Sidebar.tsx`・`Icon/registry.ts`、`frontend/src/features/dsl/DslMenuTree.tsx` | サイドバーとアイコン、自前の木の前例 | K-36 |
| `dsl/domain/DslMenuItem.java`・`DslModel.java`・`DslFormat.java`、`dsl/service/ActiveDslModelProvider.java`・`dsl/validate/DslSemanticValidator.java`・`dslmanage/generate/DslTreeBuilder.java`、`backend/src/main/resources/dsl/dsl-schema-v1.json`、`frontend/src/features/dsl/api/types.ts` | DSL のメニューの定義と提供口 | K-37 |
| `audit/domain/AuditEventType.java`・`audit/service/AuditEventListener.java`、`backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`・`auth/AuthBoundaryArchitectureTest.java`、`backend/build.gradle.kts`・`gradle/libs.versions.toml` | 監査の種類・構造の検査・カバレッジ | K-38 |

## テストの構成

- `backend/src/test/java/cherry/mastersmith/`（380 ファイル）: 本体と同じパッケージ構成。単体は `*Test`（タスク `test`）、Spring と組み込みの H2 を起動する結合は `*IT`（タスク `integrationTest`）。機能ごとの補助は `<機能>/testsupport/`（認可には `access/testsupport/PublicApiTestRules`・`AdminTestUsers`）、共通の補助は `common/testsupport/`。
- 構造の検査: `ArchitectureTest`（全体の層）と、機能ごとの境界テスト 10 個（`appearance`・`audit`・`auth`・`dsl`・`dslmanage`（`DslManageBoundaryArchitectureTest`・`DslManageGenerateBoundaryArchitectureTest`）・`invitation`・`mail`・`targetdb`・`useradmin`）。
- 対象DB の結合テストは Testcontainers（MySQL・MariaDB・PostgreSQL）。
- 画面は対象と同じ場所の `*.test.ts(x)`（110 件）。
- E2E は `frontend/e2e/`（`*.e2e.ts` 13 本、010〜130）。`verify` と CI の外。

## 画面の構成（`frontend/src/`）

```
frontend/src
├── main.tsx
├── app/        registry・navigation・layout・routing・admin-forbidden・login-state・login-handoff・display-settings・i18n・pages・work-role・testing
├── shared/     api-client・format・modal・paging・tree・validation
└── features/   admin・auth・dsl・groupadmin・invitation・mypermissions・preferences・registration・roleadmin・roletransfer・tables・useradmin（各 registration.ts で登録）
```

- 機能は `features/<featureId>/registration.ts` を置くだけで読み込まれ（`app/registry/registrationModules.ts`・`loadRegistrations.ts`、検査は `validateRegistrations.ts`）、骨組みのファイルは書き換えない。機能どうしは直接 import し合わず、共有するものは `shared/` に置く（`team.md` の Code Style、`frontend/src/features/README.md`）。
- 差し込み口は4つ: 画面（ルート、`access: AccessLevel`）・サイドバーの項目（`visibleWhen: VisibleWhen`・`order`・`labelKey`・`path`）・ユーザーメニューの項目・ログイン状態の提供元（`frontend/src/app/registry/types.ts`）。
- N 階層の木を描く部品は `features/dsl/DslMenuTree.tsx` だけで、機能の中にある（`shared/` にも骨組みにも無い。K-36）。これは前回（`d5aea52`）の記録で、`261004-role-menu` で共有の木 `shared/tree/` が加わった（K-47）。
- CSS は部品と同じ場所に素の CSS で置く。

## 2026-10-15 に深く読んだファイル（Intent `261015-deps-ui-carryover`）

| 場所 | 役割 | 所見（束の項目） |
|---|---|---|
| `gradle/libs.versions.toml`・`settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`.github/dependabot.yml`・`.github/workflows/ci.yml`・`config/npm-build-tools.txt`・`Dockerfile` | 版の定義・検査の並び・更新の通知・土台のイメージ | K-39（U1）・K-43（P7） |
| `role/store/RoleStore.java`・`role/service/RoleStoreTransactions.java` | ロールの書き込みと行の排他 | K-40（P1・P4） |
| `dsl/parse/SafeYamlParser.java`・`dsl/parse/YamlLimits.java` | 信頼できない YAML の読み込み | K-41（P2） |
| `perf/k6/scenarios.js` | k6 の台本 | K-42（P3・P5） |
| `frontend/src/app/work-role/WorkRoleSwitcher.css` | Dropdown の幅の上書き | K-44（K1） |
| `dsl/domain/DslError.java` | DSL の誤り | K-45（K2 R-05） |
| `navigation/domain/MenuFilter.java` | 権限でメニューの枝を落とす | K-46（K3 R-04）、`navigation` の record は K-45（K3 R-03） |
| `frontend/src/shared/tree/SharedTreeView.tsx`・`SharedTreeNodeItem.tsx` | 共有の木 | K-47（K4 R-02） |
| `frontend/src/features/{groupadmin,roleadmin,roletransfer}/useSlowNotice.ts` | 遅いときの知らせ | K-48（K5） |

### K-48 `useSlowNotice` が3つの機能に複製されている

確かめた事実:

- `useSlowNotice.ts` は `frontend/src/features/groupadmin/`・`roleadmin/`・`roletransfer/` の **3つ** にあり（振り返りの K5 の文言は groupadmin・roletransfer の2つ）、違いはコメントの1行（ほかの2つの名前）だけ。テスト `useSlowNotice.test.tsx` も3つにある（`ls` と開発担当の差分の確かめ）。
- `frontend/src/shared/` には `api-client`・`format`・`modal`・`paging`・`tree`・`validation` があり、機能の間で共有するものの置き場の形はそろっている。機能どうしの import は ESLint の `no-restricted-imports` で止める決まり（`team.md` の Code Style）。

見立て（未検証）:

- `shared/` の1つにまとめ、3つの機能の import を替え、テストを1つにまとめる直しになる（role-admin-ui R-02）。テストを1つにすると画面のカバレッジ（`frontend/vitest.config.ts` の `thresholds`）の分母が変わるが、同じ中身の重複が減るだけで下限に響かない見込み。
