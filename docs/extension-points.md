# 機能が使う差し込み口

機能を足すときに、骨格のファイルを書き換えずに使う型と口（サーバーと画面の差し込み口）をまとめた文書です。

## 機能が使う差し込み口

機能は、骨格のファイルを書き換えずに、次の型を使います。

| 差し込み口 | 形 | 使う機能（例） |
|---|---|---|
| 追加のアクセスの決まり | `cherry.mastersmith.common.security.SecurityRuleContributor`（`Ordered`）の Bean。order は単位の番号ではなく機能の名前で 100 台ずつ割り当てる（`auth` は 100 台で本番 110、`access` は 200 台で本番 210、`invitation` は 300 台で本番 310、`appearance` は 400 台で本番 410。本番の決まりは x10、x00・x50 はテストの決まりが使う）。同じ order が2つあると起動が失敗する。足してよいのはアクセスの決まり、トークンの検証、認証の入口と拒否の処理、要求の検査の拒否の処理で、ヘッダー・セッション・CSRF の設定は変えない | 認証、アクセス制御、招待、インスタンスの見た目の設定（`appearance`） |
| API の既定の扱い | `cherry.mastersmith.common.security.ApiDefaultAccess` の Bean（0個か1個。2個以上は起動の失敗）。無ければ `/api/**` は許可、`requireAuthentication()` が true ならログイン必須 | アクセス制御 |
| フィルターの段階のエラー応答 | `cherry.mastersmith.common.security.ErrorResponseWriter`。401・403 などを共通の ErrorResponse の形で書く | 認証、アクセス制御 |
| 想定内のエラー | `cherry.mastersmith.common.error.domain.BusinessException` を起こす。問題の種類（`ProblemType`、日英の説明つき）は自分のパッケージの `ProblemTypeCatalog` の Bean に置く（code・slug の重複は起動の失敗） | 各機能 |
| 要求中のトレースID | `cherry.mastersmith.common.observability.TraceIdProvider`（無ければ空） | 監査ログ |
| ログに秘密情報が出ないことのテストの補助 | `backend/src/test/java` の `cherry.mastersmith.common.testsupport`（`JsonLogRecords` など） | 個人に関する値・秘密を扱う機能 |
| 表示の設定の口（画面の表示の設定が提供） | `frontend/src/app/display-settings/` の `useDisplaySettings`・`saveBrowserDisplaySettings`・`LANGUAGE_NAMES`、`frontend/src/app/login-handoff/` の `handOffToLogin`。機能の画面は make-you-chic-ui の `useTheme` と localStorage を直接触らない | 機能の画面 |
| 表示のタイムゾーンと時刻の変換（画面の表示の設定が提供） | `frontend/src/app/display-settings/` の `useDisplayTimeZone`（`status` が `loading`・`error`・`ready`、`ready` のときだけ名前 `timeZone` と由来 `source`）・`DisplayTime`・`useDisplayTimeText`（管理の画面の `YYYY-MM-DD HH:mm ±HH:mm`）・`DisplayTimeZoneErrorBar`（読み込みの失敗の帯）と、`frontend/src/shared/format/timeZone/` の純粋な関数（`formatInstant`・`parseLocalDateTime`・`compareInstants`・`preserveInstant`・`timeZoneOffsetText`・`canInterpretTimeZone`）。タイムゾーンはいつも明示に渡し、ブラウザのタイムゾーンを読むのは `frontend/src/app/display-settings/browserTimeZone.ts` だけです。`timeZone` を渡さない `Intl.DateTimeFormat`・`toLocaleDateString`・`toLocaleTimeString`・日時の `toLocaleString` は ESLint（`frontend/eslint-rules/timeZoneSelectors.js`）が止めます | 管理の画面の時刻、業務データの画面 |
| 画面の差し込み口 | `frontend/src/features/<featureId>/registration.ts` に `FeatureRegistration`（`frontend/src/app/registry/types.ts`）を `registration` という名前でエクスポートする。画面・サイドバーの項目・ユーザーメニューの項目・ログイン状態の提供元・文言（鍵は `<featureId>.` で始める）を登録できる。サイドバーの項目は任意の `icon`（`frontend/src/app/registry/allowedIcons.ts` の許した名前。無ければ既定のアイコン）を持て、管理の区画（`section` が `ADMIN`）の項目は、ホームの管理のカードにも同じ名前・アイコン・`order` の順で出る。ユーザーメニューの項目は `action`（操作）か `path`（登録済みの画面の URL、読み込み直しなしで移る）のどちらか一方を持つ（`frontend/src/features/README.md`）。重複は画面の起動の失敗 | 機能の画面 |
| ログイン用レイアウト | `frontend/src/app/layout/LoginLayout.tsx`（role=LOGIN の画面が、入力欄とボタンを子として置く） | 認証 |
| スキーマの変更 | `docs/deployment.md` の「スキーマの変更（Flyway）」の決まり | 内部DB に表を持つ機能 |
| 検証済みの利用者（認証が提供） | `cherry.mastersmith.auth.domain.AuthenticatedUser`（`userId`・`email`・`admin`）。要求ごとに DB から読んだ値で、Spring Security の認証の結果の主体に置く。文字列化ではメールアドレスを伏せる | アクセス制御 |
| トークンの認証の失敗（認証が提供） | `cherry.mastersmith.auth.domain.TokenAuthenticationException`（`AuthenticationException` の子）と `TokenFailureReason`（`TOKEN_MALFORMED`・`TOKEN_INVALID`・`TOKEN_EXPIRED`・`USER_NOT_FOUND`・`USER_SUSPENDED`）。`USER_SUSPENDED`（利用停止中の利用者）は入口の DEBUG のログにだけ出る区分で、応答はほかの失敗と同じ 401 になり、有効期限切れと同じくアクセスの拒否の出来事にも監査にも出ない。トークンが無い要求は認証の検証を通らず、Spring Security の「認証が足りない」の例外が届く | アクセス制御 |
| 認証の出来事（認証が提供） | `cherry.mastersmith.auth.domain.AuthenticationEvent` を認証のトランザクションの中で知らせる。受け取りは `@TransactionalEventListener(phase = AFTER_COMMIT)` で確定の後に同じスレッドで行う | 監査ログ |
| 時計（認証が提供） | `java.time.Clock` の Bean（UTC、`cherry.mastersmith.auth.service.AuthClockConfig`）。ほかの機能は別に定義せずこれを使う | 各機能 |
| API 呼び出しの共通部分（認証が提供） | `frontend/src/shared/api-client/` の `apiFetch`・`apiRequest`（アクセストークンの付与、401 での更新と送り直し）と `{ status, code }` の形のエラー | 機能の画面 |
| アクセス拒否の出来事（アクセス制御が提供） | `cherry.mastersmith.access.domain.AdminAccessDeniedEvent`（`eventType`＝`ACCESS_DENIED`・`occurredAt`・`result`＝`FAILURE`・`failureReason`・`enteredEmail`（分かるときだけ）・`sourceIp`・`userAgent`・`requestPath`・`traceId`）を `ApplicationEventPublisher` で知らせる。受け取りは `@EventListener` で**要求と同じスレッド・応答を書く前**に行う（内部DBの更新を伴わないため、認証の出来事と異なり確定の後ではない）。受け取り側の失敗で 401／403 の応答は変わらない | 監査ログ |
| 役割・権限の判定の置き場所（アクセス制御が提供） | `cherry.mastersmith.access.web.AdminAuthorizationManager`。役割・権限の判定を足すときはここに足す | アクセス制御 |

- 秘密情報を持つ型は、文字列化（`toString`）でその項目を伏せ字にしてください（メソッドの呼び出しの追跡が引数と戻り値を文字列にするため）。
- 画面での表示の制御はサーバー側の権限の確認の代わりになりません。データは API の側で守ります。
