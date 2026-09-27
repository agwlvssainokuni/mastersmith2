# Security Design — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 のセキュリティの設計です。承認済みの `construction/u2-user-preferences/nfr-requirements/security-requirements.md`（NFR2.1〜NFR2.3・NFR4.1〜NFR4.5・NFR8.1・NFR9.1〜NFR9.3）を満たす作りを決めます。出典の略号は `performance-design.md` と同じ。加えて、TM は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。

2節（本人と送り手の取り方）と3節（項目ごとの誤りの形）は、後の単位（U3 の登録の完了、U7 の画面）が頼る形です。後の単位はこの2つの節だけを読めば足りるように書きます。

## 1. 認可（NFR4.1〜NFR4.4、BR8.1）

- `/api/me/preferences`（GET・PUT）と `/api/me/password`（POST）は、既存の SecurityFilterChain の `/api/` の既定のログイン必須（`common.security.ApiDefaultAccess`）にそのまま乗る。新しい公開の決まり（`SecurityRuleContributor`）・Origin の確かめ・CSRF の設定は足さない。3本はクッキーではなく `Authorization` のベアラーで認証するため、ブラウザが自動で付ける資格に頼らない（NFR4.4）。
- 未認証・無効なトークンは、既存の入口で 401 AUTHENTICATION_REQUIRED になり、コントローラーまで届かない（NFR4.1）。`/api/me/` は管理者だけのパスではないため、アクセスの拒否の監査（ACCESS_DENIED）は残らない（既存の `AdminAuthenticationEntryPoint` の扱い）。
- 管理者の権限を確かめないため、管理者でないログインした利用者も 200・204 になる（NFR4.2）。
- 対象の利用者は2節の取り方で認証の結果だけから決める。要求の DTO（`PreferencesRequest`・`PasswordChangeRequest`）は C4 の項目だけを持ち、利用者 ID・メールアドレスの項目を持たない。JSON の知らない項目は Jackson の既定で読み捨てる（本文に `userId` を入れても使われない、NFR4.3）。
- 確かめ: 結合テストで 401（トークンなし・改ざん）・管理者でない利用者の 200／204・本文に別の利用者の識別を入れても本人の値だけが変わること。SecurityFilterChain の設定を変えないことはコード生成のレビューで確かめる。

## 2. 本人の利用者 ID と送り手の情報の取り方（Q1 A、後の単位も使う形）

既存の構造の検査「user does not depend on auth」を保つため、`user` の中で取ります（`AuthenticatedUser`・`ClientInfo`・`ClientInfoResolver` は `auth` にあり、使えない）。

| 取るもの | 取り方 | 置き場 |
|---|---|---|
| 本人の利用者 ID | Spring Security の標準の `Authentication#getName()` を読む。既存の `AuthenticatedUserToken` は利用者 ID の10進の文字列を返す。認証が無い・匿名・数として読めないときは 401 AUTHENTICATION_REQUIRED（想定外の経路の守り） | `user.web` の要求の文脈の読み取り（例: `MeRequestContextResolver`） |
| 送り手の情報 | 既存の `ClientInfoResolver` と同じ取り方: 接続元 IP は `request.getRemoteAddr()`（転送元のヘッダーは既存の `ForwardedHeaderFilter` が反映したものだけ）、User-Agent は 512 文字で切る、トレースIDは `common.observability.TraceIdProvider` | 値の型は `user.domain` の小さな `record`（例: `RequestOrigin(sourceIp, userAgent, traceId)`）、作るのは `user.web` |

```java
// authenticationRequired は起動時に ProblemTypeRegistry.findByCode("AUTHENTICATION_REQUIRED") で得る（無ければ起動を止める）
long currentUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
        throw new BusinessException(authenticationRequired);
    }
    try { return Long.parseLong(auth.getName()); }
    catch (NumberFormatException e) { throw new BusinessException(authenticationRequired); }
}
```

- 401 の問題の種類 AUTHENTICATION_REQUIRED は `auth.domain.AuthProblemTypes` にあり、`user` からは参照できない。同じ code を `user` の一覧に重ねて置くと、起動時の重複の検査（`ProblemTypeRegistry`）で起動が止まる。そのため `user.web` は、共通の `common.error.service.ProblemTypeRegistry` の `findByCode` で起動時に1回だけ引き、見つからなければ起動を失敗させる（`auth` の定義をそのまま使い、code・状態コード・説明文が1か所に保たれる）。
- 認証の後に本人の行が消えた場合（BR3.1・機能設計の2節の「本人の行が無ければ 401」）は、業務処理が結果の型（例: 本人がいない）で返し、`user.web` が同じ問題の種類で 401 にする。業務処理の層は HTTP の状態を知らない（契約の共通の決まり「想定内の失敗は結果の型」）。

- 取り方が `auth` の `ClientInfoResolver` と2か所に並ぶ。ずれを防ぐため、結合テストで (1) 実際のアクセストークンで呼んだときに本人の値が変わること（`getName()` が利用者 ID であることの固定）、(2) 監査の sourceIp・userAgent（513 文字の User-Agent が 512 文字になる）・traceId が、ログインの監査と同じ値の取り方になることを確かめる。
- `AuthenticatedUserToken#getName()` の意味を変える変更は、この取り方を壊す。`auth` の側のコメントに「user.web がこの値を利用者 ID として読む」ことを書き足す（コメントだけの変更）。
- 後の単位で `user` の中に API を置くときも同じ部品を使う。U3（`invitation`）は `auth` に依存してよいかをその単位の設計で決め、依存できるなら既存の `ClientInfoResolver` を使ってよい。

## 3. 入力の誤りの項目ごとの誤りの形（Q2 A、後の単位も使う形）

400 VALIDATION_FAILED に、追加の項目 `fieldErrors` を載せます。既存の `BusinessException(ProblemType, detail, properties)` の追加の項目で載せ、`GlobalExceptionHandler` は変えません（既存の API の VALIDATION_FAILED は今のまま、項目ごとの誤りを持たない）。

```json
{ "type": "...", "title": "...", "status": 400, "detail": "...", "code": "VALIDATION_FAILED", "traceId": "...",
  "fieldErrors": [ { "field": "displayName", "reason": "TOO_LONG" },
                   { "field": "theme", "reason": "INVALID_VALUE" } ] }
```

| 決まり | 内容 |
|---|---|
| 項目の名前 `field` | 契約の要求の項目名そのまま（C4: `displayName`・`language`・`theme`・`fontSize`・`currentPassword`・`newPassword`・`newPasswordConfirmation`。U3 の登録の完了は C6 の項目名） |
| 理由 `reason` | 次の決まった値のどれか。値を足すのは安全な変更（受け側は知らない値を一般の文言で出す）、名前の変更・削除は壊れる変更 |
| 1つの項目に1つ | 決まりの順で最初に当たった理由だけを載せる |
| 並び | 要求の項目の並び（C4 の properties の順）。画面は並びに頼らない |
| 載せないもの | 入れた値、長さの実数、内部の例外のメッセージ（NFR2.3） |
| 誤りが無い項目 | 載せない。`fieldErrors` は誤りが1つ以上のときだけ付き、空の配列を送らない |

| reason | 当たる決まり |
|---|---|
| `REQUIRED` | 値が無い（項目が無い・null・空の文字列）。氏名は前後の空白を除いた後の空（BR1.2）。今のパスワードの空（BR4.1 の (a)） |
| `TOO_SHORT` | 新しいパスワードが 12 コードポイント未満（BR4.1 の (b)） |
| `TOO_LONG` | 氏名が 254 コードポイントを超える（BR1.3）、新しいパスワードが UTF-8 で 72 バイトを超える（BR4.1 の (b)） |
| `INVALID_CHARACTER` | 氏名に Cc・Cf がある（BR1.4） |
| `INVALID_VALUE` | language・theme・fontSize が決めた値に完全に一致しない（BR2.1） |
| `MISMATCH` | 確かめの値が新しいパスワードと一致しない（BR4.1 の (c)） |

- 理由の決め方は、DB を使わない純粋な関数（`DisplayName`・表示の設定の値・`PasswordPolicy`）が返す理由の種類に対応づける。項目ごとの誤りの型（例: `FieldError(field, reason)` と理由の列挙）は `user.domain` に置き、U3 も同じ型を使う（`invitation` は `user` に依存してよい）。
- 要求の DTO の項目はすべて文字列で受け、Java の列挙・`@NotNull` などの Bean Validation の注釈で受けない。列挙で受けると `EN` などが JSON の読み取りの失敗（400 MALFORMED_REQUEST）になり、項目ごとの誤りを返せず、まとめて検証する決まり（BR3.2・BR4.1）を守れないため。本文が JSON として読めないときは、既存の 400 MALFORMED_REQUEST のまま（項目ごとの誤りなし）。
- `detail` は ja・en の一般の説明（既存の VALIDATION_FAILED の説明）で、項目の内容を含めない。画面は `detail` を使わず `field` と `reason` から文言を選ぶ（U7 の D13）。

## 4. パスワードと秘密の扱い（NFR2.1〜NFR2.3、BR7.4・BR8.4）

- 要求の DTO はパスワードの項目を文字列で受け、業務処理へ渡す前に既存の `Password` の型（文字列にすると伏せる）へ変える。DTO の `record` は `toString` を上書きしてパスワードの3項目を伏せる（既存の `LoginRequest` と同じ扱い）。
- ハッシュは `user` の外へ出さない。既存の ArchUnit「only classes inside user read the password hash of User」を保つ。`UserSummary` に足すのは4つの表示の値だけ。
- メールアドレスはプリファレンスの応答・項目ごとの誤り・アプリのログに載せない。U2 の新しいログ（4節の監査の失敗の ERROR を含む）にメールアドレスの項目を足さない。
- 監査の失敗の ERROR（`AuditEventListener` の記録しようとした項目）に足すのは targetUserId・targetInvitationId だけ。出来事 `PasswordChangedEvent` はパスワード・ハッシュ・メールアドレスを持たない `record` にする。
- 確かめ: 既存の `*SecretLeakIT` と同じ形で、パスワードの変更の成功・今のパスワードの誤り・入力の誤り、プリファレンスの保存の成功・誤りのそれぞれで、アプリのログ・監査の行・トレースの属性・応答にパスワード（3つの値）・ハッシュ・トークン・メールアドレスが無いことを確かめる。`AuditSecretLeakIT` の列の一覧に target_user_id・target_invitation_id を足す。

## 5. 入力の上限（NFR9.1・NFR9.2）

- 要求の本文の上限は既存の 1MB（`RequestSizeLimitFilter`）のまま。氏名は 254 コードポイント、新しいパスワードは UTF-8 で 72 バイト、今のパスワードは 72 バイトを超えれば照合せず不一致（BR4.2）。
- 今のパスワードの長さの上限を入力の誤りにしない（72 バイトを超えても `TOO_LONG` を返さない）。今のパスワードについては照合の結果（PASSWORD_CURRENT_MISMATCH）だけを返し、決まりの違いを手がかりにさせないため。
- 確かめ: 境界のテスト（氏名 254・255、パスワード 11・12 コードポイント、72・73 バイト）と jqwik の性質ベースのテスト（DisplayName・PasswordPolicy・表示の設定の値、失敗時の種を記録）。

## 6. エラーの code と説明文（NFR8.1、BR8.2・BR8.3）

- 新しい `UserProblemTypes`（`user.domain`）に PASSWORD_CURRENT_MISMATCH（400）を ja・en の説明つきで置き、新しい `UserProblemTypeCatalog` で登録する。1つの code に1つの状態コードは既存の起動時の重複の検査（`ProblemTypeRegistry`）で守る。
- VALIDATION_FAILED・AUTHENTICATION_REQUIRED は既存の `CommonProblemTypes` を使う。説明文は既存の Accept-Language の決め方（ja・en、それ以外は ja）。

## 7. 今のパスワードの総当たり（NFR4.5、残る危険 R1）

- 回数の制限・ロックの仕組みは作らない。今のパスワードの誤りをログインの失敗回数に数えない（`auth` の `LoginAttemptState` に触れない）。
- 見つけ方は `observability-design.md` 3節の監査ログの問い合わせ。条件つきの更新（Q3 A）により、漏れたトークンで古いパスワードを知る人が、本人が先に変えた新しいパスワードを上書きすることも防ぐ（`reliability-design.md` 2節）。

## 8. 静的解析と依存（NFR9.3）

- 新しい依存を足さない。SpotBugs ＋ FindSecBugs の関門（priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）を除外を足さずに通す。更新の問い合わせは JPQL の名前つきの引数で書き、文字列の連結で組み立てない（`SQL_` の指摘を出さない）。

## 9. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| S-D1 | 契約 C4（`inception/contract-design/contract-summary.md`） | 400 は「入力の誤り（既存の VALIDATION_FAILED）」とだけ書き、項目ごとの誤りの形が無い | VALIDATION_FAILED に追加の項目 `fieldErrors: [{field, reason}]` を載せ、reason の値の一覧を決めた（3節） | 依頼者の決定（Q2 A）。機能設計の BR3.2・BR4.1 の「項目ごとの誤り」の形をこの段で決めた。項目の追加は安全な変更で、契約の持ち主は U2。契約の文書は書き換えず、C4 への反映は後の段（遅くとも U2 のコード生成の計画）で行う。U3（C6）・U7 は3節の形に合わせる |
| S-D2 | 機能設計 `entities.md` の PasswordChangedEvent | sourceIp は「既存の送り手の情報（ClientInfo）と同じ取り方」 | `ClientInfo` 自体は使わず、同じ取り方で `user.domain` の小さな型に作る（2節） | 依頼者の決定（Q1 A）。`user` は `auth` に依存しない既存の構造の検査を保つため。取り方が同じことは結合テストで確かめる |
| S-D3 | この段の設計の要点（案）14 | 出来事 `PasswordChangedEvent` は `user.service` に置く | `user.domain` に置く（`logical-components.md` 1節） | 監査の組み立て（`audit.domain.AuditEventFactory`）は既存の出来事をどれも各機能の `domain` から読んでおり（`auth.domain.AuthenticationEvent`・`access.domain.AdminAccessDeniedEvent`・`dslmanage.domain.DslOperationEvent`）、`user.service` に置くと `audit.domain` が業務処理の層に頼るため。依頼者が Looks correct とした要約との差として記録する（`project.md` の Way of Working） |
