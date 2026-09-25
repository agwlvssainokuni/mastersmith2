# API（mastersmith2）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は無い）。セッションは作らない。今回の Intent で API の形は変わらない見込みで、DSL の API は深く読み（`DslAdminController`）、ほかは流し読みで確かめた。

## 外部の API（HTTP）

| メソッド・パス | 実装 | 認可 | 要求 | 成功の応答 |
|---|---|---|---|---|
| `POST /api/auth/login` | `auth/web/AuthController` | 公開 | JSON（email・password） | 200 トークン＋リフレッシュの Cookie。失敗は理由によらず 401 `AUTHENTICATION_FAILED` |
| `POST /api/auth/session/refresh` | 同上 | 公開・Origin の確認 | リフレッシュの Cookie | 200 トークン＋新しい Cookie |
| `POST /api/auth/session/logout` | 同上 | 公開・Origin の確認 | リフレッシュの Cookie | 204＋Cookie の削除 |
| `GET /api/admin/check` | `access/web/AdminCheckController` | 管理者のみ | なし | 204 |
| `GET /api/admin/dsl/status` | `dslmanage/web/DslAdminController` | 管理者のみ | なし | 適用中の DSL とプレビューの状態 |
| `GET /api/admin/dsl/preview` | 同上（重い） | 管理者のみ | なし | 置かれているプレビュー（照合を含む） |
| `POST /api/admin/dsl/preview` | 同上（重い） | 管理者のみ | `application/yaml` の本文（`byte[]`、最大 10MB） | 検証を通ればプレビュー、通らなければ誤りの一覧の Problem Details |
| `DELETE /api/admin/dsl/preview` | 同上 | 管理者のみ | なし | プレビューの破棄 |
| `POST /api/admin/dsl/preview/generate` | 同上（重い） | 管理者のみ | なし | 対象DB のスキーマから既定の DSL を作りプレビューに置く |
| `GET /api/admin/dsl/preview/download` | 同上 | 管理者のみ | なし | プレビュー中の DSL（保存したバイト列のまま） |
| `POST /api/admin/dsl/apply` | 同上 | 管理者のみ | JSON（previewId） | 適用後の状態 |
| `GET /api/admin/dsl/history` | 同上 | 管理者のみ | なし | 適用の履歴（最大 20 件） |
| `POST /api/admin/dsl/history/{revisionId}/restore` | 同上（重い） | 管理者のみ | なし | 履歴の版をプレビューに置く |
| `GET /api/admin/dsl/applied/download` | 同上 | 管理者のみ | なし | 適用中の DSL |
| `GET /api/problems/{slug}`・`/error` | `common/error/web/` | 公開 | — | 問題の種類の説明・共通の Problem Details |
| `GET /actuator/health` | Actuator（公開は health だけ） | 公開 | なし | 200 `UP` または 503 |
| `GET /dsl/dsl-schema-v1.json` | 静的なファイル | 公開 | なし | DSL の JSON Schema |

- 「重い」の4つは `DslHeavyOperationGate` がアプリ全体で同時に1つに絞り、取れなければ 503 `DSL_BUSY` で断る。
- 投入の本文の大きさは `RequestSizeLimitFilter` が確かめる。`Content-Length` があれば本文を読まずに判定し、チャンクのときだけ上限まで読む。
- ダウンロードは、保存した本文を `DslContent.yamlBytes()`（複写）で取り出して返す（TD-2 の複写の1つ）。

## エラー応答の形

RFC 9457 Problem Details に `code` と `traceId` を足した形で、`@RestControllerAdvice` の1か所（`common/error/web/GlobalExceptionHandler`）で作る。応答に内部の例外メッセージを載せない。

## 内部の提供口（アプリの中の契約）

| 提供口 | 場所 | 使う側 |
|---|---|---|
| DSL の読み込み | `dsl/service/DslReader`（`DefaultDslReader`） | `dslmanage` |
| 適用中のモデル | `dsl/service/ActiveDslModelProvider`（読む口）・`ActiveDslModelHolder`（差し替える口）、実体は `ActiveDslModelStore` | 後続の機能・`dslmanage` |
| 対象DB のスキーマ | `targetdb/service/TargetSchemaReader` | `dslmanage` |
| 監査の出来事 | `AuthenticationEvent`・`AdminAccessDeniedEvent`・`DslOperationEvent` | `audit` |

## 結合テストから見た API の呼び方

結合テストは `common/testsupport/HttpTestClient`（JDK の `HttpClient`、接続の待ち 5 秒・要求の待ち 30 秒、版の指定なし）で `http://localhost:<番号>` に送る。認証の API は `auth/testsupport/AuthApi` が包む。TD-3 の調べの前提になる。
