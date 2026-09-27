# Security Design — U3 招待と登録の完了（u3-invitation）

U3 のセキュリティの設計です。承認済みの `construction/u3-invitation/nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.6・NFR2.1・NFR2.2・NFR3.1〜NFR3.3・NFR4.1〜NFR4.5・NFR8.1・NFR8.2・NFR9.1〜NFR9.3・NFR9.12）を満たす作りを決めます。出典の略号は `performance-design.md` と同じ。加えて、PM は `aidlc/spaces/default/memory/project.md`、TM は `aidlc/spaces/default/memory/team.md`。

## 1. 守りの全体

| 層 | 守り | 節 |
|---|---|---|
| 入口（SecurityFilterChain） | 招待の管理は既存の `/api/admin/**` の管理者の決まり、公開は2つの POST だけ（order 310） | 2節・3節 |
| 画面入出力（`invitation.web`） | 本人と送り手の取り方、文字列で受ける DTO、項目ごとの誤り、拒否の応答の同一 | 2節・6節・7節 |
| 業務処理（`invitation.service`） | トークンの作成と形の確かめ、有効の判定、URL の組み立て、秘密を持つ値の型 | 4節・5節 |
| 内部DB | トークンのハッシュだけ、招待中の一意、行の排他 | 4節、`reliability-design.md` 2節 |
| 送信（U1） | ヘッダーへの差し込みの拒否・エスケープ・秘密を出さないログ（U1 の設計に任せる） | 9節 |
| 出力（応答・ログ・監査・トレース） | 秘密と個人情報を出さない、code の固定 | 5節・8節 |

## 2. 認可と、本人・送り手の取り方（NFR4.1、Q1 A）

- `/api/admin/invitations` の下の4本は、既存の `access` の `/api/admin/**` の決まり（未認証 401 AUTHENTICATION_REQUIRED、管理者でなければ 403 ACCESS_DENIED）にそのまま乗る。新しい決まりは足さない。拒否では業務処理に届かないため、何も読み書きせずメールを送らない。
- 操作した管理者の利用者 ID は、U2 と同じ考え方（U2 の `security-design.md` 2節）で `invitation.web` の中で取る。

| 値 | 取り方 | 置き場 |
|---|---|---|
| 管理者の利用者 ID | Spring Security の標準の `Authentication#getName()` を数として読む。認証が無い・匿名・数として読めないときは 401 AUTHENTICATION_REQUIRED（想定外の経路の守り）。401 の問題の種類は `common.error.service.ProblemTypeRegistry#findByCode` で起動時に1回だけ引き、無ければ起動を止める | `invitation.web` の要求の文脈の読み取り（例: `InvitationRequestContextResolver`） |
| 送り手の情報（接続元 IP・User-Agent・トレースID） | 既存の `ClientInfoResolver` と同じ取り方（`request.getRemoteAddr()`、User-Agent は 512 文字で切る、トレースIDは `common.observability.TraceIdProvider`）で、U2 の `user.domain.RequestOrigin` を作る | 同上。登録の完了（ログインなし）でも同じ部品で送り手の情報だけを作る |

- `invitation` は `auth` に依存しない（承認済みの `components.md` の Invitation の `depends_on` は UserAccount と Mail だけ）。`invitation` → `user.domain`（`RequestOrigin`・`FieldError`）の依存は、UserAccount への依存の範囲に入る。
- 取り方が `auth`・`user.web`・`invitation.web` の3か所に並ぶため、結合テストで (1) 実際のアクセストークンで呼んだとき監査の actorUserId がその管理者になること、(2) 監査の sourceIp・userAgent（513 文字の User-Agent が 512 文字になる）・traceId がログインの監査と同じ取り方になることを確かめ、ずれを固定する。

## 3. 公開の決まり（NFR4.2・NFR4.3、ADR-011、Q3 A）

### 3.1 差し込み口

| 項目 | 内容 |
|---|---|
| 部品 | `InvitationSecurityContributor`（`invitation.web`、`SecurityRuleContributor` を実装） |
| order | **310**（U8 の `security-design.md` 2.3 の割り当て: 機能の名前で 100 台ずつ、invitation は 300 台） |
| 決まり | `HttpMethod.POST` の `/api/registration/verify` と `/api/registration/complete` の2つの道だけを `permitAll` にする |
| それ以外 | `/api/registration/` のほかの道と、2つの道のほかのメソッド（例: GET `/api/registration/verify`）は、決まりに当たらず `/api/**` の既定のログイン必須に落ちる（401） |
| 足さないもの | Origin の確かめ・CSRF・クッキー（公開の2つの API は要求の本文でトークンを受け取り、クッキーを使わない） |

```java
@Override
public void contribute(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authorize -> authorize
            .requestMatchers(HttpMethod.POST, "/api/registration/verify", "/api/registration/complete")
            .permitAll());
}
```

- order の重なりは既存の `SecurityExtensionValidator` が起動時に検出する。U8（410）・Auth（110）と重ならない。

### 3.2 公開の道に届いたアクセストークン（Q3 A）

- 既存の `AuthSecurityContributor` は `/api/auth/` の下を除くすべての道でアクセストークンを読む。サーバーはこの扱いを変えない。公開の2つの道でも、`Authorization` の見出しに壊れた・期限切れのトークンが付いていれば、`permitAll` の判定の前に 401 になる。
- 画面の側は、U4 の ApiClient が公開の API のパスにトークンを付けない（U4 の NFR9.1、U6 の NFR9.1）。この前提が崩れると、ログインしたままの利用者が登録の画面を開いたときに 401 になりうる。
- 401 は招待のトークンと関係なく決まるため、招待・利用者の有無は漏れない（NFR3.1 を崩さない）。
- 結合テストで次を固定する: トークンなしの2つの POST は処理される、壊れたトークン付きの POST は 401、GET `/api/registration/verify` と `/api/registration/other` は 401。
- `auth` と `common.security` には手を入れない。

## 4. トークンと招待の URL（NFR1.1〜NFR1.4・NFR1.6、BR3.1〜BR3.5）

| 決まり | 作り |
|---|---|
| 作り方 | Bean の `SecureRandom` から 32 バイトを読み、URL で使える Base64（埋め草なし、43 文字）にする。作成と送り直しのたびに新しく作る。既存の `RefreshTokenValues` と同じ作り |
| 保存 | UTF-8 のトークンの SHA-256（32 バイト、`MessageDigest`）だけを `token_hash` に一意の値として保存する。鍵（HMAC）と塩を使わない |
| 形の確かめ | 43 文字で、URL で使える Base64 の文字（`A-Z`・`a-z`・`0-9`・`-`・`_`）だけ。純粋な関数で、合わなければ内部DB を引かずに「見つからない」とする |
| 有効の判定 | state が PENDING で、注入した `Clock` の今が有効期限より前（時刻ちょうどは無効）。純粋な関数 |
| URL | 起動時に確かめたベース URL（末尾の / を除いた値）＋ `/register#token=` ＋ トークン。要求の Host・スキーム・ポートを読まない。ベース URL が使えないときは招待と送り直しが 503 で、URL を組み立てない |
| 受け取り | リンクの確かめと完了は要求の本文の `token` だけで受け取り、URL のパス・問い合わせで受け取らない |

```java
record InvitationToken(String value) {
    static Optional<InvitationToken> parse(String raw) {          // 形の誤りは空（DB を引かない）
        return raw != null && FORMAT.matcher(raw).matches() ? Optional.of(new InvitationToken(raw)) : Optional.empty();
    }
    byte[] hash() { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    @Override public String toString() { return "InvitationToken[****]"; }   // 5節
}
```

## 5. 追跡のログ・ログ・トレースへの漏れ（NFR1.5・NFR2.1、要点 10）

- 既存の `TraceAspect` は、`invitation` の `web`・`service`・`domain`・`repository` の Bean のメソッドの引数と戻り値を、そのロガーが TRACE のときに文字列にして出す。そのため、秘密を持つ値は文字列のまま Bean の引数・戻り値にしない。

| 値 | 受け渡しの型 | 文字列化 |
|---|---|---|
| 招待のトークン | `InvitationToken`（4節） | 伏せ字 |
| 招待の URL | `RegistrationUrl`（`invitation.domain`） | 伏せ字。U1 への依頼（`MailRequest`）には差し込みの値として渡し、U1 の `MailRequest` も伏せた文字列化を持つ（U1 の `security-design.md`） |
| トークンのハッシュ | バイト列（既存のリフレッシュトークンと同じ） | 配列の文字列化は中身を出さない。応答・監査・ログに入れない |
| 要求の DTO（`TokenRequest`・`CompleteRequest`） | `record` | `toString` を上書きし、`token`・`password`・`passwordConfirmation` を伏せる |
| パスワード | 既存の `Password` の型 | 既存のとおり伏せ字 |
| 招待の行（エンティティ） | `Invitation` | `toString` を上書きせず `Object` の既定のまま（項目を出さない） |

- トレースの属性は、既存の `TraceAspect` が引数の値を span の属性にしないこと（ログにだけ出す）を前提にする。U1 の Observation のタグは低い基数の4つだけ（U1 の設計）で、宛先・URL を持たない。
- 既存の `*SecretLeakIT` と同じ形で、招待・送り直し・送信の失敗・リンクの確かめ・登録の完了の成功と拒否のそれぞれで、TRACE を有効にしたロガーの出力・監査の行（U2 が足した後の列の一覧）・応答に、トークン・ハッシュの16進・URL・パスワード・招待先のメールアドレス（ログと応答）が無いことを確かめる（NFR1.5・NFR2.1）。

## 6. 入力の受け取りと項目ごとの誤り（NFR9.12、BR1.1・BR1.2・BR7.2）

- 要求の DTO の項目はすべて文字列で受け、Java の列挙と Bean Validation の注釈で受けない（U2 と同じ理由: `EN` などが JSON の読み取りの失敗になり、項目ごとの誤りを返せない）。本文が JSON として読めないときは既存の 400 MALFORMED_REQUEST のまま。要求の本文の上限は既存の 1MB のまま。
- 登録の完了の 400 VALIDATION_FAILED には、U2 の形（U2 の `security-design.md` 3節）の追加の項目 `fieldErrors: [{field, reason}]` を載せる。`field` は C6 の項目名、`reason` は U2 の一覧の値で、`user.domain` の `FieldError` と理由の列挙を使う。

| field（C6） | 決まり | reason |
|---|---|---|
| `displayName` | U2 の DisplayName（前後の空白を除いて 1〜254 コードポイント、Cc・Cf なし） | REQUIRED・TOO_LONG・INVALID_CHARACTER |
| `password` | 既存の PasswordPolicy（12 コードポイント以上、UTF-8 で 72 バイト以内） | REQUIRED・TOO_SHORT・TOO_LONG |
| `passwordConfirmation` | `password` と一致 | REQUIRED・MISMATCH |
| `language`・`theme`・`fontSize` | U2 の BR2.1 の値に完全に一致 | REQUIRED・INVALID_VALUE |

- `token` は項目ごとの誤りにしない。形の誤りは承認どおり 404 REGISTRATION_LINK_INVALID の拒否の経路へ進む（BR3.2・BR7.5）。入力の誤りとトークンの誤りが同時にあるときは、機能設計の順（入力の検証が先、BR7.2）で 400 になる。
- 招待の入力の誤り（`email`: 改行・正規化の後の 254 文字・形式、`language`: ja・en）にも同じ形を載せる（`email` は REQUIRED・TOO_LONG・INVALID_CHARACTER（CR・LF）・INVALID_VALUE（形式）、`language` は REQUIRED・INVALID_VALUE）。安全な追加で、U5 の画面は形に頼らない。
- 一覧の `page` の誤りは今の VALIDATION_FAILED のまま（項目ごとの誤りなし）。
- `fieldErrors` は項目の名前と理由の値だけを載せ、入れた値・長さの実数・内部の例外のメッセージを載せない。

## 7. 拒否の応答の同一と列挙の防止（NFR3.1〜NFR3.3）

- リンクの確かめと登録の完了の拒否は、理由（期限切れ・使用済み・取り消し済み・置き換え済み・存在しない・改ざん・形の誤り・同時の完了・同じメールアドレスの利用者がいる）によらず、1つの経路（`BusinessException(REGISTRATION_LINK_INVALID)`、追加の項目なし）で応答を作る。理由は業務処理の結果の型の中だけで持ち、出来事（監査の failureReason）にだけ渡す。
- 結合テストで、理由ごとの応答の状態コード・`code`・本文の項目の名前と並び（`traceId` の値を除く）が一致することを確かめる。
- 応答の時間の差はそろえない（NFR3.2）。既存の `DummyPasswordHash` は変えない。
- 招待中の人は利用者の表にいないため、ログイン・トークンの更新・アクセストークンの認証は既存の仕組みのまま拒否される。招待の ID を主体にしたトークンを発行する経路を作らない（NFR3.3）。登録の完了はトークンを発行しない（NFR4.4）。

## 8. 秘密と個人情報（NFR2.1・NFR2.2、BR9.4）

| 出力 | 載せないもの | 例外 |
|---|---|---|
| アプリのログ | トークン・ハッシュ・URL・メールアドレス・パスワード・SMTP の応答の文面・メールの部品の例外のメッセージ | invitationId・言語・状態・送信の結果の種類・failureKind（`observability-design.md` 2節） |
| エラー応答 | 同上。送信の失敗は応答に出ない（201・200 の `sendResult` FAILED だけ） | — |
| 監査ログ | トークン・ハッシュ・URL・パスワード・メールアドレス | 既存の列（actorUserId・targetUserId・targetInvitationId・sourceIp・userAgent・traceId） |
| 正常の応答（C5） | トークン・ハッシュ・URL | 管理者だけが見る `email`・`invitedBy`（氏名） |

- 送信の失敗の種類の細部（TIMEOUT・REJECTED など）は応答に載せず、ログにだけ出す（NFR2.2）。
- 監査の失敗の ERROR（既存の `AuditEventListener`）に足す項目は、U2 が足した対象の2列だけで、トークン・メールアドレスを載せない。

## 9. エラーの code と招待メール（NFR8.1・NFR8.2）

- `InvitationProblemTypes`（`invitation.domain`）と `InvitationProblemTypeCatalog`（`invitation.service`）に、INVITATION_EMAIL_REGISTERED（409）・INVITATION_ALREADY_PENDING（409）・INVITATION_NOT_CONFIGURED（503）・INVITATION_NOT_FOUND（404）・REGISTRATION_LINK_INVALID（404）を1つの code に1つの状態コードで置き、ja・en の説明文を持つ。説明文は要求の Accept-Language で選ぶ（既存の仕組み）。重複は既存の起動時の検査で止まる。
- 409 INVITATION_ALREADY_PENDING の追加の項目は `invitationId`・`page` だけ、503 は `unavailableReasons` だけ。
- 招待メールは招待の言語の ja・en のテンプレート（U1 の置き場 `backend/src/main/resources/mail/templates/invitation_ja.html`・`invitation_en.html`、一覧の行は差し込み `registrationUrl`・`validityHours`）で描く。エスケープ・属性の値の二重引用符・Mustache のコメントのライセンスヘッダー・ヘッダーへの差し込みの拒否は U1 の決まりと検査に任せる（U1 の `security-design.md`）。U3 は宛先に CR・LF を含む招待の入力を自分でも拒否する（BR1.1）。

## 10. 静的解析と必須のテスト（NFR9.1〜NFR9.3）

- 既存の関門（SpotBugs ＋ FindSecBugs の priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず）・Gitleaks・OSV-Scanner をそのまま通し、除外を足さない。乱数は `SecureRandom`、SQL は名前付きの引数だけで組み立てる（生成列と削除の問い合わせも文字列の連結をしない）。

| 必須のテスト（TM の Testing Posture） | 形 | 要件 |
|---|---|---|
| 有効期限の境界（直前は有効・ちょうどと直後は無効） | 注入した時計の単体テストと jqwik | NFR1.3・NFR9.1 |
| 使用済み・取り消し済み・置き換え済み・送り直しの前のトークンの再使用の拒否 | 結合テスト | NFR1.3・NFR9.1 |
| 改ざん・存在しない・形の誤りのトークンの拒否と応答の同一 | 結合テスト、形は境界（42・43・44 文字、使えない文字）と jqwik | NFR1.6・NFR3.1・NFR9.1 |
| 招待中の人のログイン・更新・アクセストークンの拒否 | 結合テスト（招待の ID と招待のメールアドレスを主体にした正しい鍵のトークンを含む） | NFR3.3・NFR9.1 |
| 招待の URL（Host を変えてもベース URL、ベース URL が無ければ 503 で送らない） | 結合テスト | NFR1.4・NFR9.2 |
| トークンと URL の漏えい | `InvitationSecretLeakIT`（5節） | NFR1.5・NFR9.2 |
| 本文のエスケープ・テンプレートの描画（ja・en、件名、差し込み漏れ、ライセンスヘッダー）・48 時間の設定で本文に 48 | 結合テスト（SubEtha SMTP で受ける） | NFR8.2・NFR9.2 |
| ヘッダーへの差し込み（宛先の CR・LF の拒否） | 単体・結合テスト | NFR9.2 |
| 送信の失敗（拒む・応答しない・宛先の拒否）の応答とログ | 結合テスト（SubEtha SMTP・`ServerSocket`） | NFR2.2・NFR9.2 |
| 認可（4本の 401・403・成功）、公開の範囲（3.2 のテスト） | 結合テスト | NFR4.1・NFR4.2 |
| 完了の応答にトークンが無く、完了した利用者は招待の管理で 403 | 結合テスト | NFR4.4 |
| 入力の上限（254・255 文字、CR・LF、11・12 コードポイント、72・73 バイト） | 境界の単体テストと jqwik | NFR9.12 |

## 11. 残る危険

| ID | 危険 | 受け入れる根拠 | 見直す時点 |
|---|---|---|---|
| R1 | 登録の完了の公開の API に誤ったトークンを送るたびに、監査に REGISTRATION_FAILED が1行増え、回数の制限が無いため未認証の要求で監査の表と内部DB のファイルを増やせる（NFR 要件の R1 のとおり）。**加えて、監査の REGISTRATION_FAILED の急な増えに自動で気づく仕組み（警報）は無い。** 気づくのは、管理者が監査ログを数える問い合わせ（`observability-design.md` 3節）を流したときだけで、増えてから気づくまでに間が空きうる | NFR 要件の R1 の根拠のとおり（Q2 A、ログインと同じ性質、256 ビットのトークン、本文 1MB まで）。警報を作らないのは、手元の監視を常に動かしていない間は警報を受ける先が無く（`project.md` の Deployment）、新しい指標・警報を足さない承認（NFR6.9）と合わせたため | 社外に公開する配備先が決まったとき（前段のリバースプロキシ・WAF での制限と、監査の件数の警報を配備の段で考える）、または監査で急な増えが見つかったとき |
| R2 | 有効期限の長さに上限が無い（NFR 要件の R2 のとおり） | Q3 C（NFR 要件）。README の設定の説明に長くしすぎない旨を書く（code-generation） | 配備先が決まったとき |
| R3 | 公開の2つの道でもアクセストークンを読むため、画面の側がトークンを付けると登録の完了が 401 になりうる（3.2） | Q3 A。U4 の ApiClient が付けない前提で、サーバー側の範囲を変えずに済む。情報は漏れない | U4 の ApiClient の公開のパスの扱いを変えるとき |

## 上流との差

| ID | 上流 | 上流の記載 | この設計 | 理由と扱い |
|---|---|---|---|---|
| SD-D1 | 機能設計 `rules.md` の BR9.2・機能設計の7節、NFR 要件の NFR4.2 | 差し込み口の順番の値はコード生成で決める | order を 310 にした（3.1） | U8 の NFR 設計（`construction/u8-instance-appearance/nfr-design/security-design.md` 2.3）が機能の名前で 100 台ずつ割り当て、u3-invitation を 310 と先に決めたため、その割り当てを採った。承認済みの U3 の文書は書き換えない |
| SD-D2 | NFR 要件の `security-requirements.md` の R1 | 回数の制限が無いことを受け入れ、増え方は監査の件数で見つけられる | 監査の急な増えに自動で気づく仕組み（警報）が無いことを R1 に書き足した（11節） | 承認の場の決定（2026-09-27 Approve の Minor）。要件の文書は書き換えない |
| SD-D3 | 契約 C5・C6（`inception/contract-design/contract-summary.md`） | 400 は VALIDATION_FAILED とだけ書き、項目ごとの誤りの形が無い | 登録の完了と招待の 400 に U2 の `fieldErrors` を載せた（6節） | U2 の Q2 A の形を使う（U2 の設計で U3 も使うと決めた）。項目の追加は安全な変更で、契約の持ち主は U3。契約の文書は書き換えず、C5・C6 への反映は後の段（遅くとも U3 のコード生成の計画）で行う |
| SD-D4 | 部品の一覧（`components.md`） | Invitation の `depends_on` は UserAccount と Mail | 変えない。`auth` に依存せず、送り手の情報に `user.domain.RequestOrigin` を使う（2節） | Q1 A。差ではなく、承認どおりであることの記録 |
| SD-D5 | NFR 要件の「残る危険」 | R1・R2 | R3（公開の道のアクセストークン）を足した（11節） | Q3 A で受け入れた点を記録した |
