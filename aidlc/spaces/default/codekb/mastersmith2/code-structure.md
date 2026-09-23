# コードの構成（mastersmith2）

## リポジトリの配置

| 場所 | 種類 | 内容 |
|---|---|---|
| `backend/` | Gradle サブプロジェクト（Java 25） | Spring Boot のバックエンド。main 142 ファイル、test 127 ファイル |
| `frontend/` | npm パッケージ（TypeScript・React 19） | SPA。非テスト 36 ファイル、テスト 29 ファイル（流し読み） |
| `vendor/make-you-chic-ui/` | Git サブモジュール（固定先 `5258c8b`） | デザインシステム。このリポジトリから変更しない |
| `perf/` | k6 のシナリオと手順 | `perf/k6/scenarios.js`（`constant-vus`）、`perf/README.md` |
| `docker/` | 監視と負荷の試験の設定 | grafana/otel-lgtm のダッシュボード・警報、OTel Collector |
| `config/` | 検査の設定 | `config/npm-build-tools.txt` など |
| `.github/` | CI と依存の更新 | `workflows/ci.yml`、`dependabot.yml` |
| ルート | ビルドと起動 | `build.gradle.kts`・`settings.gradle.kts`・`gradle/libs.versions.toml`・`Dockerfile`・`compose.yaml`・`README.md` |

ルートには管理外のファイルとして `.env` と内部DBのバックアップ `mastersmith-data-*.tgz`（3つ）がある。どちらも `.gitignore` で除外済み（中身は読んでいない。コミットしないこと）。

## バックエンドのパッケージ（ルート `cherry.mastersmith`）

| パッケージ | 層 | 役割 |
|---|---|---|
| `auth` | web / service / domain / repository | ログイン・トークンの更新・ログアウト・アカウントのロック（前の Intent の U2） |
| `user` | service / domain / repository | 利用者、パスワードの照合、初期管理者（U2） |
| `access` | web / service / domain | 管理者の API の認可、401/403、アクセス拒否の出来事（U3） |
| `audit` | service / domain / repository（web なし） | 監査イベントの追記（U4） |
| `common` | error / health / i18n / observability / security / web | 共通部品（U1）。Problem Details、ヘルスチェック、トレース |
| `config` | — | Spring の設定（セキュリティ・Web・観測） |

層の決まり（ArchUnit で確認）: `web` は `repository` を直接呼ばない、トランザクションの境界は `service` だけ、エンティティを応答に返さず `record` の DTO を使う、コンストラクター注入のみ。

## 本 Intent に関わるファイル

| ファイル | 役割 |
|---|---|
| `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java` | `login`（117 行〜）で照合の後に `TransactionTemplate.execute`（119 行）。`decide`（127 行〜）の中で `LOGIN_FAILED`（134・152 行）／`LOGIN_SUCCEEDED`（156〜157 行）を publish |
| `backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java` | `@Transactional logout`（76〜101 行）の中で `LOGGED_OUT` を publish（93 行） |
| `backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java` | `@Transactional refresh`（74 行）。出来事は publish しない |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` | AFTER_COMMIT の受け取り（87〜91 行・98〜102 行）、失敗の受け止め（110〜127 行） |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java` | `REQUIRES_NEW` の追記（51〜54 行） |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditConfig.java` | 監査の部品の設定（遅い書き込みの計時の `LongSupplier` など） |
| `backend/src/main/java/cherry/mastersmith/common/health/TimeBoundedDbHealthIndicator.java` | 同じプールで `SELECT 1`（127 行、流し読み） |
| `backend/src/main/resources/application.yaml` | データソースと Hikari（97〜105 行）、JPA（106〜114 行） |
| `backend/src/main/resources/db/migration/V1〜V4` | Flyway。表は `users`・`login_attempt_states`・`refresh_tokens`・`audit_events` |

## テストの配置

- `backend/src/test/java/cherry/mastersmith/` に対象と同じパッケージ構成。単体は `*Test`（60 件、`test` タスク）、Spring と組み込み H2 を起動する結合は `*IT`（44 件、`integrationTest` タスク）。
- 結合テストの DB は `common/testsupport/TestDatabase.register`（`@TempDir` の H2 ファイル。プールの設定は本番のまま）。
- 監査の結合テストの差し替え部品: `audit/testsupport/FailingAuditEventRepositoryConfig.java`（失敗の模擬）、`SlowAuditWriteConfig.java`（遅い書き込みの模擬）。
- 画面は `frontend/src/**/*.test.ts(x)`（29 件）、E2E は `frontend/e2e/`（Playwright 3 件、`verify` と CI の外）。

## コードの型（パターン）

- 機能の間は `ApplicationEventPublisher` の出来事でつなぎ、受け取り側は `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`。
- 時刻は注入した `Clock`、ログは SLF4J のキー・値 API、例外の応答は `@RestControllerAdvice` の1か所。
- Lombok は使わない。全クラスに日本語の Javadoc があり、設計の文書の番号（BR・NFR）を参照している。
