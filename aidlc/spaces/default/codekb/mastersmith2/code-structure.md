# コードの構成（mastersmith2）

各部品の責務と状態は `component-inventory.md`、部品の間の依存は `dependencies.md` に書き、ここでは置き場と決まりだけを書く。

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway のスキーマ変更・SpotBugs の除外 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から呼ぶ |
| `vendor/make-you-chic-ui/` | 外部のデザインシステム | Git サブモジュール（`.gitmodules`）。このリポジトリからは変更しない |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`）、Wrapper、1コマンドの検査 `verify` |
| `config/` | ビルドの設定 | ライセンスヘッダーのひな形、OSV の判定で止める道具の一覧（`npm-build-tools.txt`） |
| `.github/` | CI | `workflows/ci.yml`・`dependabot.yml` |
| `Dockerfile`・`compose.yaml`・`.env.example`・`.dockerignore` | コンテナ | アプリのイメージとサービス、profile で起動する監視と見本の対象DB |
| `docker/` | コンテナの設定 | `perf/`（使い捨ての負荷試験の環境）・`otel-collector/`・`monitoring/`（ダッシュボードと警報）・`targetdb/`（見本の対象DB の初期化）・`check-container-limits.sh` |
| `perf/` | 負荷の試験 | k6 の台本（`perf/k6/scenarios.js`）、DSL の時間の測定（`dsl-timing.sh`）、手順（`perf/README.md`） |
| `README.md` | 文書 | 約 600 行。道具・検査・起動・環境変数・対象DB・DSL・監視・監査・差し込み口 |
| `aidlc/`・`.claude/` | ワークフローの記録と枠組み | アプリのソースではない |

## バックエンドのパッケージ（`backend/src/main/java/cherry/mastersmith/`、277 ファイル）

```
cherry.mastersmith
├── MastersmithApplication
├── config                                   Security・Web・転送元ヘッダー・外部エクスポート（ObservabilityConfig）
├── common/{error,health,i18n,observability,security,web}
├── auth/{domain,service,repository,web}     ログイン・トークン・ロック・ログアウト
├── access/{domain,service,web}              管理者のみの API の認可と 401・403
├── audit/{domain,service,repository}        監査イベントの追記
├── user/{domain,service,repository}         利用者・照合・初期管理者
├── targetdb/{config,domain,repository,service}   対象DB の接続とスキーマの読み取り
├── dsl/{domain,parse,validate,service}      DSL の型・安全な読み込み・検証・適用中のモデル
└── dslmanage/{domain,generate,repository,service,web}   既定の DSL の生成、投入・プレビュー・適用・履歴
```

- 層は `web`（HTTP の受け渡し）・`service`（業務処理とトランザクション）・`domain`（値・判定・エンティティ）・`repository`（DB アクセス）。`dsl` は `parse`・`validate`、`dslmanage` は `generate`、`targetdb` は `config` の層を足している。
- 名前の決まりは `team.md` の Code Style のとおり（`XxxController`・`XxxRequest`/`XxxResponse` の `record`・`XxxService`・`XxxProperties`・`XxxProblemTypes`＋`XxxProblemTypeCatalog`・`XxxEvent`・`XxxRepository`、各パッケージに `package-info.java`）。

### 設定とスキーマ（`backend/src/main/resources/`）

- `application.yaml`: 秘密情報は環境変数の参照だけ。独自の設定は `mastersmith.*`。ログの水準（`logging.level`、246〜259 行）はルート INFO と JDBC ドライバー3つの OFF だけ。OTLP の送り先は 221〜244 行。
- `logback-spring.xml`: 1行1件の JSON のログ（logstash-logback-encoder、MDC の `traceId`・`spanId`、`<keyValuePairs/>`）。出力は1つ（56〜58 行）。
- `db/migration/`: Flyway、`V<番号>__<単位>_<内容>.sql`、前進のみ。
  - V1 基準線、V2 `users`、V3 `login_attempt_states`（ダミーの行 8 行を含む）・`refresh_tokens`、V4 `audit_events`、V5 `dsl_previews`・`dsl_applied_revisions`、V6 `audit_events` への DSL の列の追加
- `dsl/dsl-schema-v1.json`: DSL の JSON Schema（ビルドで `static/dsl/` へ複写して公開）

## テストの構成

- `backend/src/test/java/cherry/mastersmith/`: 本体と同じパッケージ構成（208 ファイル）。単体は `*Test`（`test`）、Spring と H2 を起動する結合は `*IT`（`integrationTest`）。対象DB の結合テストは Testcontainers（`targetdb/testsupport/TargetDbImages.java` で版を固定）。
- `common/testsupport/`: `TestDatabase`・`HttpTestClient`・`LogEvents`（ログの出来事を集める）ほか。
- 画面は対象と同じ場所の `*.test.ts(x)`（47 ファイル）、E2E は `frontend/e2e/`（4 本）。

件数の内訳とテストの道具は `code-quality-assessment.md`。

## 画面の構成（`frontend/src/`）

```
frontend/src
├── main.tsx
├── app/{registry,routing,navigation,layout,i18n,login-state,pages,testing}   骨組み
├── shared/api-client/                  apiFetch・apiRequest・ApiError
├── features/
│   ├── auth/                           ログイン画面・トークンの保持・ログアウト
│   ├── admin/                          管理者向け領域 /admin
│   └── dsl/                            DSL の管理画面（DslAdminPage・DslConfirmDialog・違いの表・誤りの一覧・履歴 など、api/・testing/）
└── types/
```

- 新しい機能は `features/<featureId>/registration.ts` を置くだけで読み込まれる（`frontend/src/features/README.md`）。
- 画面・部品は PascalCase の `.tsx` と同じ場所の素の CSS、純粋な関数・状態は camelCase の `.ts`、API の呼び出しは `features/*/api/` または `xxxApi.ts`。
- make-you-chic-ui は `frontend/package.json` の `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui` で取り込み、`vite.config.ts` の `resolve.dedupe` で React の二重読み込みを防ぐ。

## コードのパターン

- **差し込み口の Bean の一覧**: `SecurityRuleContributor`・`ProblemTypeCatalog` などを起動時に並べて検査する。画面の登録も同じ考え方。
- **アプリの中の出来事**: 機能は `ApplicationEventPublisher` で知らせ、`audit` が `@TransactionalEventListener` で受ける。
- **明示のトランザクション**: `LoginService` は `TransactionTemplate`（`transaction.execute`）で照合をトランザクションの外に出す。
- **結果の型**: `dsl` の読み込みは `DslReadResult`（`Valid`・`Invalid`）を返し、`dslmanage` が `switch` で分けて例外に変える。
- **構造化ログ**: `LOGGER.atInfo().addKeyValue(...)` のキーと値の API。
- **注入する時計**: `Clock` の Bean。
- **秘密情報の包み**: 値の型の `toString` で伏せ字にする。
