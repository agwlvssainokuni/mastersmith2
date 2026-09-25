# コードの構成（mastersmith2）

各部品の責務と状態は `component-inventory.md`、部品の間の依存は `dependencies.md` に書き、ここでは置き場と決まりだけを書く。

## リポジトリの最上位

| パス | 分類 | 内容 |
|---|---|---|
| `backend/` | バックエンド | Gradle のサブプロジェクト。Java のソース・テスト・設定・Flyway のスキーマ変更・SpotBugs の除外 |
| `frontend/` | 画面 | npm のプロジェクト（React＋TypeScript、Vite）。ルートの Gradle から呼ぶ |
| `vendor/make-you-chic-ui/` | 外部のデザインシステム | Git サブモジュール。このリポジトリからは変更しない |
| `gradle/`・`settings.gradle.kts`・`build.gradle.kts` | ビルド | 版の一覧（`gradle/libs.versions.toml`）、Wrapper、1コマンドの検査 `verify` |
| `config/` | ビルドの設定 | ライセンスヘッダーのひな形、OSV の判定で止める道具の一覧 |
| `.github/` | CI | `workflows/ci.yml`・`dependabot.yml` |
| `Dockerfile`・`compose.yaml`・`.env.example`・`.dockerignore` | コンテナ | アプリのイメージとサービス、profile で起動する監視と見本の対象DB |
| `docker/` | コンテナの設定 | 使い捨ての負荷試験の環境（`perf/compose.yaml`）、OTLP の受け手、監視、見本の対象DB の初期化 |
| `perf/` | 負荷と時間の試験 | k6 の台本（`perf/k6/scenarios.js`）、DSL の時間と保存の測定（`dsl-timing.sh`）、手順（`perf/README.md`） |
| `README.md` | 文書 | 起動・環境変数・既知の制約・戻し方 |
| `aidlc/`・`.claude/` | ワークフローの記録と枠組み | アプリのソースではない |

## バックエンドのパッケージ（`backend/src/main/java/cherry/mastersmith/`、279 ファイル）

```
cherry.mastersmith
├── MastersmithApplication
├── config                                   Security・画面の配信・外部エクスポート
├── common/{error,health,i18n,observability,security,web}
├── auth/{domain,service,repository,web}     ログイン・トークン・ロック・ログアウト
├── access/{domain,service,web}              管理者のみの API の認可と 401・403
├── audit/{domain,service,repository}        監査イベントの追記
├── user/{domain,service,repository}         利用者・照合・初期管理者
├── targetdb/{config,domain,repository,service}   対象DB の接続とスキーマの読み取り
├── dsl/{domain,parse,validate,service}      DSL の型・安全な読み込み・検証・適用中のモデル
└── dslmanage/{domain,generate,repository,service,web}   既定の DSL の生成、投入・プレビュー・適用・履歴
```

- 層は `web`（HTTP の受け渡し）・`service`（業務処理とトランザクション）・`domain`（値・判定・エンティティ）・`repository`（DB アクセス）。名前の決まりは `team.md` の Code Style のとおり。

### 今回の依頼に関わるファイル（深く読んだもの）

| 場所 | 役割 |
|---|---|
| `dslmanage/domain/DslContent.java` | 識別と本文の組。作るときと `yamlBytes()` のたびに本文を `clone()` する |
| `dslmanage/domain/DslPreviewRecord.java`・`DslAppliedRevisionRecord.java` | JPA のエンティティ（読み取り用）。`toContent` で `DslContent` を作る |
| `dslmanage/repository/DslPreviewRepository.java` | プレビューの `MERGE`（`PLACE_SQL`）・読み取り・削除 |
| `dslmanage/repository/DslAppliedRevisionRepository.java` | 履歴への写し（`COPY_SQL` の `INSERT ... SELECT`）・読み取り・古い履歴の削除 |
| `dslmanage/service/DslRecordStore.java` | トランザクションの境界（`apply` は写し・プレビューの削除・古い履歴の削除を1つで行う） |
| `dslmanage/service/DslLifecycle.java` | 生成・投入・戻し・表示・破棄・適用・状態・履歴・ダウンロード（462 行） |
| `dslmanage/service/DslPreviewCache.java` | プレビューのモデルを1件だけ持つ |
| `dslmanage/web/DslAdminController.java`・`DslHeavyOperationGate.java`・`DslWebConfig.java` | 10 本の API、重い操作の同時の数の制限 |
| `common/web/RequestSizeLimitFilter.java` | 投入の本文の大きさの上限 |
| `dsl/parse/SafeYamlParser.java`・`PositionMap.java` | 安全な読み込み（`LimitingParser`＋`Composer`）、節ごとの位置の対応表 |
| `dsl/service/DefaultDslReader.java`・`ActiveDslModelStore.java` | 読み込みの入口、適用中のモデルの保持 |

### 設定とスキーマ（`backend/src/main/resources/`）

- `application.yaml`: 独自の設定は `mastersmith.*`（DSL の投入の上限 `max-submit-size` 既定 10MB は 104〜108 行）。内部DB の接続先と Hikari は 130〜145 行。
- `db/migration/`: Flyway、前進のみ。V1 基準線、V2 `users`、V3 `login_attempt_states`・`refresh_tokens`、V4 `audit_events`、V5 `dsl_previews`・`dsl_applied_revisions`、V6 `audit_events` への DSL の列の追加。

## テストの構成

- `backend/src/test/java/cherry/mastersmith/`: 本体と同じパッケージ構成。単体は `*Test`（100 ファイル、`test`）、Spring と H2 を起動する結合は `*IT`（69 ファイル、`integrationTest`）。対象DB の結合テストは Testcontainers。
- 共通の補助: `common/testsupport/`（`HttpTestClient`・`TestDatabase` ほか）、`auth/testsupport/AuthApi`、`targetdb/testsupport/`（`SilentServer`：受け付けて何も返さない ServerSocket）。
- 画面は対象と同じ場所の `*.test.ts(x)`（47 ファイル）、E2E は `frontend/e2e/`（4 ファイル）。

件数はファイルを数えた値で、実行の件数ではない。テストの道具と品質は `code-quality-assessment.md`。

## 画面の構成（`frontend/src/`、流し読み）

```
frontend/src
├── main.tsx
├── app/                     骨組み（登録・振り分け・レイアウト・表示言語・ログイン状態）
├── shared/api-client/       同じオリジンの API の呼び出し
└── features/{auth,admin,dsl}
```

新しい機能は `features/<featureId>/registration.ts` を置くだけで読み込まれる。
