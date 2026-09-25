# 部品の一覧（mastersmith2）

## 読み方

見出しの名前（`###` の直後）は、`reverse-engineering-timestamp.md` の Scope of Analysis の `analyzed.components` と文字どおりに照合される。ID は前回のコード知識ベースに合わせ、今回新しく `backend-test-support`（結合テストの基盤）を足した。

状態は healthy（問題なし）・at-risk（今回の Intent で手を入れる見込みか、懸念あり）・degraded（不具合あり）。TD の番号は `code-quality-assessment.md` の「技術的負債」を指す。「読みの深さ」の深いは、一部のファイルだけを深く読んだものを含む。

## バックエンド（`backend/src/main/java/cherry/mastersmith/`）

### app-bootstrap

- 場所: `MastersmithApplication.java`
- 責務: 起動クラス。
- 状態: healthy ／ 読みの深さ: 流し読み

### config

- 場所: `config/`（あわせて設定の `application.yaml`・`logback-spring.xml`）
- 責務: Spring Security のフィルターの連鎖、画面の配信、外部エクスポート。内部DB の接続先（`DEFRAG_ALWAYS=TRUE`）と Hikari（上限 30）、DSL の投入の上限（10MB）の設定。
- 依存: `common-*`
- 状態: at-risk（TD-1 内部DB の詰め直しは閉じるときだけ） ／ 読みの深さ: 深い（`application.yaml` だけ）

### common-error

- 場所: `common/error/`
- 責務: Problem Details、`BusinessException`、`GlobalExceptionHandler`。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-security

- 場所: `common/security/`
- 責務: セキュリティの差し込み口と起動時の検査。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-web

- 場所: `common/web/`
- 責務: キャッシュの指定、要求の本文の大きさの上限（`RequestSizeLimitFilter`）。
- 依存: `common-security`
- 状態: healthy ／ 読みの深さ: 深い（`RequestSizeLimitFilter.java` だけ）

### common-health

- 場所: `common/health/`
- 責務: 制限時間付きの内部DB の確認。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-i18n

- 場所: `common/i18n/`
- 責務: 表示言語（ja・en）を決める。
- 状態: healthy ／ 読みの深さ: 流し読み

### common-observability

- 場所: `common/observability/`
- 責務: トレースIDの参照、送るトレースとログの消毒。
- 状態: healthy ／ 読みの深さ: 流し読み

### auth

- 場所: `auth/{domain,service,repository,web}`
- 責務: ログイン、アクセストークンとリフレッシュトークン、ロック、ログアウト。
- 依存: `user`・`common-*`・内部DB
- 状態: healthy（テストの `AccessTokenApiIT` の一時的な失敗は `backend-test-support` の TD-3） ／ 読みの深さ: 流し読み

### access

- 場所: `access/{domain,service,web}`
- 責務: `/api/admin/**` を管理者のみにする決まり、401・403。
- 状態: healthy ／ 読みの深さ: 流し読み

### audit

- 場所: `audit/{domain,service,repository}`
- 責務: 出来事を受け、確定の後に `audit_events` へ別トランザクションで追記する。
- 状態: healthy ／ 読みの深さ: 流し読み

### user

- 場所: `user/{domain,service,repository}`
- 責務: 利用者、パスワードの照合、初期管理者の自動作成。
- 状態: healthy ／ 読みの深さ: 流し読み

### targetdb

- 場所: `targetdb/{config,domain,repository,service}`
- 責務: 対象DB の接続とスキーマの読み取り。
- 状態: healthy ／ 読みの深さ: 流し読み

### dsl

- 場所: `dsl/{domain,parse,validate,service}`
- 責務: DSL のモデルの型、YAML の安全な読み込み（`SafeYamlParser`：大きさ・深さ・別名・タグの上限、位置の対応表 `PositionMap`）、JSON Schema と意味の検証、適用中のモデルの保持（`ActiveDslModelStore`）。
- 依存: アプリの中の依存は無い（SnakeYAML・JSON Schema の検証の部品・Jackson）
- 状態: at-risk（TD-2 読み込みの途中の形が同時に生き、適用中のモデルを持ち続ける） ／ 読みの深さ: 深い（`SafeYamlParser`・`PositionMap`・`DefaultDslReader`・`ActiveDslModelStore`）

### dslmanage

- 場所: `dslmanage/{domain,generate,repository,service,web}`
- 責務: 既定の DSL の生成、投入・プレビュー・適用・破棄・履歴・ダウンロード（`DslLifecycle`）、トランザクションの境界（`DslRecordStore`）、本文の保存（`DslPreviewRepository`・`DslAppliedRevisionRepository`）、プレビューのモデルの保持（`DslPreviewCache`）、重い操作の同時の数の制限（`DslHeavyOperationGate`）。
- 依存: `dsl`・`targetdb`・`user`・`auth`・`common-*`・内部DB（`dsl_previews`・`dsl_applied_revisions`）
- 状態: degraded（TD-1 動いている間にファイルが伸び続ける、TD-2 本文の複写とモデルの保持） ／ 読みの深さ: 深い（保存・トランザクション・API の各ファイル。`generate/` は流し読み）

### backend-test-support

- 場所: `backend/src/test/java/cherry/mastersmith/` の `common/testsupport/`（`HttpTestClient`・`TestDatabase`）・`auth/testsupport/AuthApi`・`targetdb/testsupport/SilentServer`、今回調べた `auth/web/AccessTokenApiIT`、テストのタスクの設定（`backend/build.gradle.kts` 111〜128 行）
- 責務: 結合テストの HTTP の送り手、内部DB のテストデータの用意と片付け、応答しない相手の模擬。
- 依存: JUnit 5・Spring Boot Test・JDK の `HttpClient`・Testcontainers
- 状態: at-risk（TD-3 `AccessTokenApiIT` の一時的な失敗、原因未確認） ／ 読みの深さ: 深い（上のファイルだけ。ほかのテストは流し読み）

## 画面（`frontend/src/`、すべて流し読み）

### frontend-app-core

- 場所: `main.tsx`・`app/App.tsx`・`app/routing/`・`app/navigation/`
- 責務: 起動、URL の振り分け、サイドバーの組み立て。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-registry

- 場所: `app/registry/`
- 責務: 機能の登録の型と自動の読み込み。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-app-layout-i18n

- 場所: `app/layout/`・`app/i18n/`・`app/login-state/`・`app/pages/`
- 責務: レイアウト、表示言語、ログイン状態、共通の画面。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-api-client

- 場所: `shared/api-client/`
- 責務: 同じオリジンの API の呼び出し、トークンの付与と更新。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-auth

- 場所: `features/auth/`
- 責務: ログイン画面、トークンの保持、ログアウト。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-admin

- 場所: `features/admin/`
- 責務: 管理者向け領域 `/admin`。
- 状態: healthy ／ 読みの深さ: 流し読み

### frontend-feature-dsl

- 場所: `features/dsl/`
- 責務: DSL の管理画面（状態・投入・プレビュー・違いの表・誤りの一覧・履歴・確かめの表示）。
- 状態: healthy ／ 読みの深さ: 流し読み

### make-you-chic-ui

- 場所: `vendor/make-you-chic-ui/`（Git サブモジュール、固定先 `edb1f943c0e66293494fa974605f34fcd7e258d7`）
- 責務: デザインシステム。
- 状態: healthy ／ 読みの深さ: 流し読み（固定先の確認だけ）

## ビルド・実行環境

### build-and-verify

- 場所: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/`・`config/`・`.github/`
- 責務: 1コマンドの検査 `./gradlew verify`、WAR の組み立て、lockfile による版の固定、テストのタスク（`test`・`integrationTest`）、カバレッジの下限、CI。
- 状態: healthy ／ 読みの深さ: 深い（`backend/build.gradle.kts` の 20〜160 行、`gradle/libs.versions.toml`・`backend/gradle.lockfile` の該当の行。ルートは流し読み）

### container-runtime

- 場所: `Dockerfile`・`compose.yaml`・`.env.example`・`.dockerignore`
- 責務: WAR をコピーするだけのイメージ（JVM は `-XX:MaxRAMPercentage=75.0`、設定の口 `MASTERSMITH_JAVA_OPTIONS`）、`app` のサービス（CPU 既定 4、メモリ 既定 2g）。
- 状態: at-risk（TD-2 メモリの上限に近づく） ／ 読みの深さ: 深い（`Dockerfile` だけ。compose は該当の行の流し読み）

### perf-and-monitoring

- 場所: `perf/`・`docker/`
- 責務: k6 の負荷の試験（`dslMixed` など）と使い捨ての環境、DSL の時間と保存の測定（`perf/dsl-timing.sh`、`--storage`）、監視。
- 状態: at-risk（TD-4 説明の文の食い違い） ／ 読みの深さ: 深い（`perf/dsl-timing.sh` だけ）
