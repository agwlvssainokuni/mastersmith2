# API（mastersmith2）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は無い）。セッションは作らない。今回の Intent で API の形は変わらない見込みで、下の一覧は流し読み（エンドポイントの一覧とパスの定義）で確かめたものである。

## 外部の API（HTTP）

| メソッド・パス | 実装 | 認可 | 要求 | 成功の応答 |
|---|---|---|---|---|
| `POST /api/auth/login` | `auth/web/AuthController` | 公開 | JSON（email・password） | 200 `TokenResponse`＋リフレッシュの Cookie。失敗は理由によらず 401 `AUTHENTICATION_FAILED` |
| `POST /api/auth/session/refresh` | `AuthController` | 公開・Origin の確認 | リフレッシュの Cookie | 200 `TokenResponse`＋新しい Cookie |
| `POST /api/auth/session/logout` | `AuthController` | 公開・Origin の確認 | リフレッシュの Cookie | 204＋Cookie の削除 |
| `GET /api/admin/check` | `access/web/AdminCheckController` | 管理者のみ | なし | 204 |
| `GET /api/admin/dsl/status` | `dslmanage/web/DslAdminController` | 管理者のみ | なし | 適用中の DSL と状態 |
| `GET /api/admin/dsl/preview` | 同上 | 管理者のみ | なし | 置かれているプレビュー |
| `POST /api/admin/dsl/preview` | 同上 | 管理者のみ | `application/yaml` の本文 | 検証を通ればプレビュー（違い・警告）、通らなければ誤りの一覧の Problem Details |
| `DELETE /api/admin/dsl/preview` | 同上 | 管理者のみ | なし | プレビューの破棄 |
| `POST /api/admin/dsl/preview/generate` | 同上 | 管理者のみ | なし | 対象DB のスキーマから既定の DSL を作りプレビューに置く |
| `GET /api/admin/dsl/preview/download` | 同上 | 管理者のみ | なし | プレビュー中の DSL（YAML） |
| `POST /api/admin/dsl/apply` | 同上 | 管理者のみ | JSON `ApplyRequest`（previewId） | 適用後の状態 |
| `GET /api/admin/dsl/history` | 同上 | 管理者のみ | なし | 適用の履歴 |
| `POST /api/admin/dsl/history/{revisionId}/restore` | 同上 | 管理者のみ | なし | 履歴の版をプレビューに置く |
| `GET /api/admin/dsl/applied/download` | 同上 | 管理者のみ | なし | 適用中の DSL（YAML） |
| `GET /api/problems/{slug}` | `common/error/web/ProblemTypeController` | 公開 | `Accept`・`Accept-Language` | 問題の種類の説明 |
| `/error` | `common/error/web/ErrorPathController` | 公開 | サーブレットの段階のエラー | 共通の Problem Details |
| `GET /actuator/health` | Actuator（公開は health だけ） | 公開 | なし | 200 `UP` または 503 |
| `GET /dsl/dsl-schema-v1.json` | 静的なファイル | 公開 | なし | DSL の JSON Schema |
| `/**`（画面） | `config` の画面の配信 | 許可 | — | SPA のファイル。見つからない URL は `index.html` |

- DSL のパスは `dslmanage/web/DslAdminPaths.java` が持つ。重い操作（プレビューの表示・投入・生成・戻し。`@HeavyDslOperation` の印）は `DslHeavyOperationGate` がアプリ全体で同時に1つに絞り、取れなければ 503 `DSL_BUSY` で断る。
- `/api/admin/**` は `access` の決まりで管理者のみになり、`/api/**` の残りはログインが必要である。

## エラー応答の形

RFC 9457 Problem Details（`application/problem+json`）に `code`（画面が分岐に使う安定したコード）と `traceId` を足した形で、`@RestControllerAdvice` の1か所（`common/error/web/GlobalExceptionHandler`）で作る。問題の種類は機能ごとの `ProblemTypeCatalog`（DSL は `dslmanage/service/DslProblemTypeCatalog`、定義は `dslmanage/domain/DslProblemTypes`。例 `DSL_REVISION_NOT_FOUND`・`DSL_PREVIEW_CHANGED`）で登録する。応答に内部の例外メッセージを載せない。

## 画面から呼んでいる API

| 呼び出し元 | API |
|---|---|
| `frontend/src/features/auth/` | ログイン・更新・ログアウト |
| `frontend/src/features/admin/` | `GET /api/admin/check` |
| `frontend/src/features/dsl/api/` | `/api/admin/dsl/**` の 10 本 |

## 内部の提供口（アプリの中の契約）

| 提供口 | 場所 | 使う側 |
|---|---|---|
| DSL の読み込み | `dsl/service/DslReader`（`DefaultDslReader`） | `dslmanage` |
| 適用中のモデル | `dsl/service/ActiveDslModelProvider`（読む口）・`ActiveDslModelHolder`（差し替える口） | 後続の機能・`dslmanage` |
| 対象DB のスキーマ | `targetdb/service/TargetSchemaReader`（`JdbcTargetSchemaReader`） | `dslmanage` |
| 利用者の照合と要約 | `user/service/UserAccountService`・`UserSummary` | `auth`・`dslmanage` |
| 監査の出来事 | `auth.domain.AuthenticationEvent`・`access.domain.AdminAccessDeniedEvent`・`dslmanage.domain.DslOperationEvent` | `audit` |
| セキュリティ・エラー・トレースIDの差し込み口 | `common/security`・`common/error`・`common/observability` | 各機能 |

境界は ArchUnit の `*BoundaryArchitectureTest` で守っている。

## 外への送信（API ではないが外に出るもの）

外部エクスポートを有効にしたとき（`mastersmith.observability.export.enabled`）だけ、トレース・ログ・指標を OTLP で送る（送り先は `application.yaml` 221〜244 行）。ログの送信に何が含まれるかは `code-quality-assessment.md` の TD-1。
