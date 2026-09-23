# コードの構成（mastersmith2）

## リポジトリの配置

| 場所 | 種類 | 内容 |
|---|---|---|
| `backend/` | Gradle サブプロジェクト（Java 25） | Spring Boot のバックエンド（前回の記録で main 142 ファイル、test 127 ファイル） |
| `frontend/` | npm パッケージ（TypeScript・React 19） | SPA（流し読み） |
| `vendor/make-you-chic-ui/` | Git サブモジュール | デザインシステム。このリポジトリから変更しない |
| `perf/` | k6 のシナリオと手順 | `perf/k6/scenarios.js`（6 場面、`constant-vus`）、`perf/README.md`（使い捨ての環境の手順） |
| `docker/perf/` | 負荷の試験の使い捨ての環境 | `compose.yaml`（プロジェクト名 `mastersmith-perf`、ボリューム `perf-data`、`127.0.0.1:18080`） |
| `docker/monitoring/` | 手元の監視の設定 | `provisioning/alerting/mastersmith.yaml`（警報）、`provisioning/dashboards/mastersmith.yaml`、`dashboards/mastersmith-overview.json` |
| `docker/otel-collector/` | OTLP の受け手の設定 | `config.yaml`（受け口 HTTP 4318、出力は debug だけ） |
| `config/` | 検査の設定 | `config/npm-build-tools.txt` など（流し読み） |
| `.github/` | CI と依存の更新 | `workflows/ci.yml`、`dependabot.yml`（流し読み） |
| ルート | ビルドと起動 | `build.gradle.kts`・`settings.gradle.kts`・`gradle/libs.versions.toml`・`Dockerfile`・`compose.yaml`・`.dockerignore`・`.env.example`・`README.md` |

ルートには管理外のファイルとして `.env` と内部DBのバックアップ `mastersmith-data-*.tgz` がある（前回の記録）。どちらも `.gitignore` で除外済みで、中身は読んでいない。コミットしないこと。

## コンテナと起動の定義（今回深く読んだ範囲）

| ファイル | 役割 | 要点 |
|---|---|---|
| `Dockerfile` | アプリのイメージ（1段） | `eclipse-temurin:25.0.4_7-jre-noble`（17 行）。専用の利用者 10001、`/app/data` は 700（20〜24 行）。WAR をコピーするだけ（27 行）。`ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=Asia/Tokyo", "-jar", "/app/mastersmith.war"]`（34 行） |
| `.dockerignore` | ビルドの送り先の制限 | すべてを除外し、`backend/build/libs/mastersmith.war` だけを送る（16〜17 行） |
| `compose.yaml` | 配備（プロジェクト名 `mastersmith`） | `app`: イメージ `mastersmith:${MASTERSMITH_IMAGE_TAG:-local}`、`127.0.0.1:8080`、`.env`（任意）、`TZ: Asia/Tokyo`、ヘルスチェックは bash で `/actuator/health`（30s 間隔）、`stop_grace_period: 45s`、`cpus: ${MASTERSMITH_CONTAINER_CPUS:-4}`（59 行）、`mem_limit: 1g`（60 行）、`restart: "no"`、ログは json-file 10m×3。`otel-collector`（profile `observability`）、`lgtm`（profile `monitoring`、`127.0.0.1:3000`、`mem_limit: 900m`） |
| `docker/perf/compose.yaml` | 負荷の試験の使い捨ての環境 | 配備と同じイメージ・同じ `cpus`（50 行）・`mem_limit: 1g`（51 行）。環境ファイルは `MASTERSMITH_PERF_ENV_FILE` で必須 |
| `.env.example` | 環境変数の見本（値は空） | `MASTERSMITH_CONTAINER_CPUS`（20〜22 行、コメント「例: colima の既定の 2」）、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`（31 行）ほか。メモリや JVM の引数の変数は無い |
| `perf/README.md` | 負荷の試験の手順 | 手順 0 で配備したアプリを止める。一時の `app.env` と `export` に `MASTERSMITH_CONTAINER_CPUS=2`（16・18 行）。末尾に F3 の注記（44 行） |
| `perf/k6/scenarios.js` | k6 の台本 | 場面は `SCENARIO`（health・loginSuccess・loginFailure・refresh・adminCheck・forbidden）、`VUS` 既定 10、`DURATION` 既定 60s、`summaryTrendStats` に p95 |
| `README.md` | 前提と手順 | colima の割り当ての例（21 行、232 行）、環境変数の表（165・169 行）、監査ログの既知の制約（281〜284 行） |

## バックエンドのパッケージ（ルート `cherry.mastersmith`）

| パッケージ | 層 | 役割 |
|---|---|---|
| `auth` | web / service / domain / repository | ログイン・トークンの更新・ログアウト・アカウントのロック（U2） |
| `user` | service / domain / repository | 利用者、パスワードの照合、初期管理者（U2）。`UserAccountConfig` が `BCryptPasswordEncoder(properties.bcryptCost())` を作る（流し読み） |
| `access` | web / service / domain | 管理者の API の認可、401/403、アクセス拒否の出来事（U3） |
| `audit` | service / domain / repository（web なし） | 監査イベントの追記（U4） |
| `common` | error / health / i18n / observability / security / web | 共通部品（U1）。Problem Details、ヘルスチェック、トレース |
| `config` | — | Spring の設定。`SecurityConfig`（フィルターの連鎖1つ、状態を持たない）、`WebConfig`（SPA の配信）、`ObservabilityConfig`（OTLP の送信の組み立て）、`ForwardedHeaderConfig`（転送元のヘッダー、既定で使わない）、`SecurityHeaderProperties`（CSP）、`package-info.java`。資源（スレッド・メモリ・実行器）に関わる設定は無い |

層の決まり（ArchUnit で確認）: `web` は `repository` を直接呼ばない、トランザクションの境界は `service` だけ、エンティティを応答に返さず `record` の DTO を使う、コンストラクター注入のみ。

## 設定ファイル（`backend/src/main/resources/application.yaml`）

- アプリの設定 `mastersmith.*`（18〜77 行）: ヘルスチェックの制限時間（28 行）、外部エクスポート（29〜34 行）、認証（48〜74 行。`bcrypt-cost` は 64 行）、CSP（77 行）。
- `spring.*`: データソースと Hikari（97〜108 行）、JPA（109〜117 行）、Flyway（118〜122 行）、H2 コンソール無効。
- `server.*`: ポート 8080、穏やかな停止、圧縮、エラーの詳細を出さない（128〜142 行）。`server.tomcat.*`（スレッドの上限など）の設定は無い。
- `management.*`: 公開は health だけ（144〜167 行）、トレースの割合（174 行）、OTLP（179〜205 行）。
- 秘密情報の値は書かず、環境変数の参照だけを置く（15〜16 行のコメント）。

## 前回に深く読んだファイル（監査と接続。今回は再確認していない）

| ファイル | 役割 |
|---|---|
| `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java` | `login`（117〜124 行）で照合（118 行）の後に `TransactionTemplate.execute`（119 行）。`decide`（127 行〜）の中で `LOGIN_FAILED`／`LOGIN_SUCCEEDED` を publish。**今回再確認した** |
| `backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java` | `@Transactional logout` の中で `LOGGED_OUT` を publish |
| `backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java` | `@Transactional refresh`。出来事は publish しない。F3 が起きた場面の経路だが、今回は読んでいない |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` | AFTER_COMMIT の受け取り、失敗の受け止め |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java` | `REQUIRES_NEW` の追記 |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditConfig.java` | 監査の部品の設定（遅い書き込みの計時の `LongSupplier` など） |
| `backend/src/main/java/cherry/mastersmith/common/health/TimeBoundedDbHealthIndicator.java` | 同じプールで `SELECT 1` |
| `backend/src/main/resources/db/migration/V1〜V4` | Flyway。表は `users`・`login_attempt_states`・`refresh_tokens`・`audit_events` |

## テストの配置

- `backend/src/test/java/cherry/mastersmith/` に対象と同じパッケージ構成。単体は `*Test`（`test` タスク）、Spring と組み込み H2 を起動する結合は `*IT`（`integrationTest` タスク）。件数は前回の記録（`code-quality-assessment.md`）を参照。今回は読んでいない。
- 結合テストの DB は `common/testsupport/TestDatabase.register`（`@TempDir` の H2 ファイル）。
- 画面は `frontend/src/**/*.test.ts(x)`、E2E は `frontend/e2e/`（Playwright、`verify` と CI の外）。
- 性能は `perf/k6/scenarios.js`（`verify` と CI の外。開発者の PC で使い捨ての環境に対して行う）。

## コードの型（パターン）

- 機能の間は `ApplicationEventPublisher` の出来事でつなぎ、受け取り側は `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`。
- 時刻は注入した `Clock`、ログは SLF4J のキー・値 API、例外の応答は `@RestControllerAdvice` の1か所。
- 設定値は `application.yaml` に `${環境変数:既定値}` の形で置き、コメントで理由を書く。コンテナの上限と JVM の引数は `compose.yaml`・`Dockerfile` 側にあり、`application.yaml` からは変えられない。
- Lombok は使わない。全クラスに日本語の Javadoc があり、設計の文書の番号（BR・NFR）を参照している。
