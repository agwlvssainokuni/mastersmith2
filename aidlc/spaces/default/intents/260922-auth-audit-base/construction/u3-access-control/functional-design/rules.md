# Business Rules — U3 管理画面のアクセス制御（u3-access-control）

U3 の決まり。出典は要件定義書の FR・NFR、`functional-design-questions.md` の確定回答（Q1〜Q5）、U1・U2 の決まり（「U1 の決まり x.y」「U2 の決まり x.y」と書く）である。

```yaml
rules:
  # ---- BR1 アクセスの決まり ----
  - id: BR1.1
    statement: /api/admin/ の下の API は、すべて管理者のみとする
    category: authorization
    applies_to: AccessRule
    trigger: 要求
    logic: IF パスが /api/admin/ の下 THEN requirement=ADMIN（個々の API での宣言に頼らない）
    violation: —
    source: FR8.1、Q1
  - id: BR1.2
    statement: 公開する API は、ヘルスチェック、問題の種類の説明ページ、ログイン・トークンの更新・ログアウトだけとし、明示した一覧で持つ
    category: authorization
    applies_to: AccessRule
    trigger: 要求
    logic: 一覧に当たれば requirement=PUBLIC。一覧に /api/admin/ の下を含めない
    violation: —
    source: Q2、U1 の決まり 1.4・5.12、U2 の決まり 2.3・5.4・6.1
  - id: BR1.3
    statement: どの決まりにも当たらない /api/ の下の API は、ログインを求める（既定で拒否）
    category: authorization
    applies_to: AccessRule
    trigger: 要求
    logic: requirement=AUTHENTICATED
    violation: —
    source: Q2
  - id: BR1.4
    statement: 決まりは ADMIN、PUBLIC、AUTHENTICATED の順に判定し、最初に当たったものを使う
    category: authorization
    applies_to: AccessRule
    trigger: 要求
    logic: priority の小さい順に判定する
    violation: —
    source: BR1.1〜BR1.3 の整合
  - id: BR1.5
    statement: /api/ の外（画面の配信）は、ログインを求めない
    category: authorization
    applies_to: AccessRule
    trigger: 画面のファイルの要求
    logic: 画面の表示の制御は画面側（U1 の決まり 7.4・7.5）が行い、データは /api/ の下の API で守る
    violation: —
    source: 同じ配信元で画面を配る構成（チームの進め方）

  # ---- BR2 判定 ----
  - id: BR2.1
    statement: 管理者のみの API で、ログインしていない（有効なアクセストークンが無い）要求は 401、code AUTHENTICATION_REQUIRED で拒否する
    category: authorization
    applies_to: 管理者のみの API
    trigger: 要求
    logic: U2 の判定（U2 の決まり 4.4）の結果に従う
    violation: 401 / AUTHENTICATION_REQUIRED
    source: FR8.1
  - id: BR2.2
    statement: 管理者のみの API で、ログイン中だが管理者でない利用者の要求は 403、code ACCESS_DENIED で拒否する
    category: authorization
    applies_to: 管理者のみの API
    trigger: 要求
    logic: IF AuthenticatedUser.admin=false THEN 403 / ACCESS_DENIED
    violation: 403 / ACCESS_DENIED
    source: FR8.1
  - id: BR2.3
    statement: 管理者のみの API で、管理者の要求は受け付ける
    category: authorization
    applies_to: 管理者のみの API
    trigger: 要求
    logic: IF AuthenticatedUser.admin=true THEN 処理へ進む
    violation: —
    source: FR8.1
  - id: BR2.4
    statement: 管理者かどうかは、要求ごとに U2 が内部DBから読んだ AuthenticatedUser の値で判断する
    category: authorization
    applies_to: 管理者のみの API
    trigger: 要求
    logic: 画面から送られた値やアクセストークンの中身では判断しない
    violation: —
    source: U2 の決まり 4.5
  - id: BR2.5
    statement: 管理者でない利用者が /api/admin/ の下の存在しない API を呼んだ場合も、403 で拒否する（存在しない API かどうかを明かさない）
    category: authorization
    applies_to: 管理者のみの API
    trigger: 要求
    logic: 判定は API の有無を調べる前に行う。管理者が存在しない API を呼んだ場合は 404 / NOT_FOUND
    violation: —
    source: BR1.1 の適用
  - id: BR2.6
    statement: 画面で「管理」を隠すことは、サーバー側の判定の代わりにしない
    category: authorization
    applies_to: 管理者のみの API
    trigger: —
    logic: 画面の表示にかかわらず、BR2.1〜BR2.3 を必ず適用する
    violation: テストで、画面を介さずに API を呼んでも 401／403 になることを確かめる
    source: FR8.2

  # ---- BR3 アクセス拒否の出来事 ----
  - id: BR3.1
    statement: 管理者のみの API で 403 を返したときは、理由 NOT_ADMIN のアクセス拒否の出来事を知らせる
    category: policy
    applies_to: AccessDeniedEvent
    trigger: BR2.2
    logic: userId と enteredEmail（その利用者のメールアドレス）を添える
    violation: —
    source: FR9.1、Q3
  - id: BR3.2
    statement: 管理者のみの API で 401 を返したときは、有効期限切れの場合を除き、理由 NOT_AUTHENTICATED のアクセス拒否の出来事を知らせる
    category: policy
    applies_to: AccessDeniedEvent
    trigger: BR2.1
    logic: IF U2 の判定した 401 の理由が TOKEN_EXPIRED THEN 知らせない ELSE authenticationFailure にその理由を添えて知らせる
    violation: —
    source: Q3、Q5
  - id: BR3.3
    statement: 管理者のみの API 以外での 401 は、アクセス拒否の出来事として知らせない
    category: policy
    applies_to: AccessDeniedEvent
    trigger: 要求
    logic: 出来事を知らせるのは /api/admin/ の下だけ
    violation: —
    source: Q3（対象は管理画面へのアクセス拒否）
  - id: BR3.4
    statement: 出来事には、日時・結果・理由・利用者（分かれば）・要求のメソッドとパス（クエリ文字列を除く）・接続元IP・User-Agent・トレースIDを載せ、秘密情報を載せない
    category: constraint
    applies_to: AccessDeniedEvent
    trigger: 出来事の通知
    logic: 接続元IP は要求の接続元（転送元のヘッダーは、信頼する設定があるときだけ使う。U1 の決まり 5.10）
    violation: —
    source: FR9.2、NFR3
  - id: BR3.5
    statement: 出来事を知らせる側は受け取る側を知らず、受け取る側の失敗で 401／403 の応答を変えない。受け取りは要求と同じスレッドで行う
    category: constraint
    applies_to: AccessDeniedEvent
    trigger: 出来事の通知
    logic: 通知の後に受け取る側で起きた失敗は、応答に影響させない
    violation: —
    source: Domain Design ADR-004、FR9.4、U1 functional-spec.md 6.1

  # ---- BR4 確認用 API ----
  - id: BR4.1
    statement: 管理者向け領域の表示可否を確かめる確認用 API を、/api/admin/ の下に置く。管理者なら成功（内容なし）を返す
    category: policy
    applies_to: 確認用 API
    trigger: 管理者向け領域の表示
    logic: 判定は BR2.1〜BR2.3 に任せ、API 自体は成功を返すだけ
    violation: —
    source: FR8.1、Domain Design（AccessControl の受け持ち）

  # ---- BR5 画面 ----
  - id: BR5.1
    statement: 管理者向け領域の画面を、U1 の「画面の登録」で access=ADMIN、アプリシェルの中として登録し、サイドバーの「管理」を visibleWhen=ADMIN でホームの後に登録する
    category: policy
    applies_to: 画面の登録
    trigger: 画面の起動
    logic: U3 の登録用ファイルで登録する。U1 のファイルは変えない
    violation: —
    source: FR2.2、U1 の決まり 7.1・7.6
  - id: BR5.2
    statement: 管理者向け領域は、表示するたびに確認用 API を呼び、成功したときだけ中身を表示する。403 のときは「ページが見つかりません」を表示する
    category: authorization
    applies_to: 管理者向け領域
    trigger: 管理者向け領域の表示
    logic: 確認が終わるまでは中身を表示しない。403 → 見つからない画面。401 は API 呼び出しの共通部分（U2 の決まり 8.5）が扱う。それ以外のエラー → 一般的なエラーの文言
    violation: —
    source: FR2.2、Q4
  - id: BR5.3
    statement: 管理者向け領域の中身は、本Intentでは見出しと「今後の管理機能がここに加わる」旨の説明だけとする
    category: policy
    applies_to: 管理者向け領域
    trigger: 管理者向け領域の表示
    logic: 文言は日本語・英語をそろえる（U1 の決まり 6.2）
    violation: —
    source: FR2.2（プレースホルダ）
  - id: BR5.4
    statement: 画面の「管理」の表示・非表示は表示の切り替えにすぎず、判定はサーバー側で行う
    category: authorization
    applies_to: 画面
    trigger: —
    logic: 画面の admin の値（U2 の CurrentUserView）は表示の切り替えにだけ使う
    violation: —
    source: FR8.2

  # ---- BR6 問題の種類 ----
  - id: BR6.1
    statement: U3 は、ACCESS_DENIED（403）の問題の種類を、日本語・英語の説明とともに自分の場所に定義する
    category: constraint
    applies_to: 問題の種類
    trigger: —
    logic: U1 の共通の業務エラーの型で起こし、U1 が起動時に定義を集める
    violation: U1 の決まり 5.14 のテストで検出する
    source: U1 の決まり 5.14・5.16
```

## 決まりの一覧

| ID | 分類 | 決まり（要約） | 出典 |
|---|---|---|---|
| BR1.1 | authorization | /api/admin/ の下はすべて管理者のみ | FR8.1、Q1 |
| BR1.2 | authorization | 公開する API は明示した一覧だけ | Q2 |
| BR1.3 | authorization | それ以外の /api/ はログイン必須（既定で拒否） | Q2 |
| BR1.4 | authorization | ADMIN → PUBLIC → AUTHENTICATED の順に判定 | BR1.1〜1.3 |
| BR1.5 | authorization | /api/ の外（画面の配信）はログイン不要 | 構成 |
| BR2.1 | authorization | 管理者のみの API で未ログインは 401 | FR8.1 |
| BR2.2 | authorization | 管理者でなければ 403 / ACCESS_DENIED | FR8.1 |
| BR2.3 | authorization | 管理者は受け付ける | FR8.1 |
| BR2.4 | authorization | 管理者かは要求ごとの DB の値で判断 | U2 の決まり 4.5 |
| BR2.5 | authorization | 非管理者には存在しない管理 API も 403 | BR1.1 |
| BR2.6 | authorization | 画面で隠すことを判定の代わりにしない | FR8.2 |
| BR3.1 | policy | 403 でアクセス拒否（NOT_ADMIN）を知らせる | FR9.1、Q3 |
| BR3.2 | policy | 期限切れ以外の 401 でアクセス拒否（NOT_AUTHENTICATED）を知らせる | Q3、Q5 |
| BR3.3 | policy | 管理者のみの API 以外の 401 は知らせない | Q3 |
| BR3.4 | constraint | 出来事の項目、秘密情報を載せない | FR9.2、NFR3 |
| BR3.5 | constraint | 受け取る側を知らず、その失敗で応答を変えない | ADR-004、FR9.4 |
| BR4.1 | policy | 確認用 API を /api/admin/ の下に置く | FR8.1 |
| BR5.1 | policy | 管理者向け領域と「管理」を U1 の差し込み口で登録 | FR2.2 |
| BR5.2 | authorization | 表示のたびに確認し、403 なら見つからない画面 | FR2.2、Q4 |
| BR5.3 | policy | 中身は見出しと説明だけ | FR2.2 |
| BR5.4 | authorization | 「管理」の表示は切り替えにすぎない | FR8.2 |
| BR6.1 | constraint | ACCESS_DENIED の問題の種類を日英の説明つきで定義 | U1 の決まり 5.14 |
