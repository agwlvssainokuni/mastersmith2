# Entities — U1 アプリの骨格（u1-app-skeleton）

U1 は業務のデータ（利用者・監査イベントなど）を持たない。本書は、U1 がアプリとして独自に定める情報の形を扱う。対象は、API のエラー応答とその問題の種類、画面の表示言語、画面の骨組みの差し込み口に登録されるものである。いずれも内部DBには保存しない。判断の元は `functional-design-questions.md` の確定回答（Q1〜Q9）と、要件定義書の FR2.3 である。

アプリのログ、トレースの情報、内部DBの接続設定、外部エクスポートの設定、ヘルスチェックの応答は、フレームワークの仕組みと設定で実現する。そのため、エンティティとしては扱わない。これらに求める振る舞い（FR1.1、FR1.2、FR10.1〜FR10.4）は `rules.md` の決まりとして定める。

```yaml
entities:
  - name: ErrorResponse
    description: API のエラー応答。RFC 9457 Problem Details の項目に、画面が分岐に使う code とトレースIDを足したもの
    attributes:
      - name: type
        type: uri
        required: true
        constraints: 問題の種類の説明ページの絶対 URL。ベースURL＋/api/problems/＋ProblemType.slug（Q8）。画面はこの値で分岐しない
      - name: title
        type: string
        required: true
        constraints: 状態コードに対応する短い説明。内部の例外メッセージを入れない
      - name: status
        type: integer
        required: true
        min: 400
        max: 599
      - name: detail
        type: string
        required: false
        constraints: 利用者に見せてよい説明だけ。内部の例外メッセージ・スタックトレース・秘密情報を入れない
      - name: instance
        type: string
        required: false
        constraints: 要求のパス
      - name: code
        type: string
        required: true
        constraints: 大文字とアンダースコアだけ（正規表現 ^[A-Z][A-Z0-9_]*$）。一度決めた値は変えない（Q2）
      - name: traceId
        type: string
        required: true
        constraints: その要求のトレースID（ログに載るトレースIDと同じ値）（Q3）
    constraints:
      - パスワード・トークン・署名鍵を含めない（NFR3）
      - type と code は同じ ProblemType を指す
    relationships:
      - target: ProblemType
        cardinality: many-to-one
        direction: ErrorResponse → ProblemType
        description: 応答の type と code は、1つの問題の種類から決まる

  - name: ProblemType
    description: 問題の種類。エラー応答の code と type の元であり、type の URL を開いたときに返す説明の内容（Q8、Q9）。各機能が自分の場所に定義を置き、U1 が起動時に集める（BR5.16）。内部DBには保存しない
    attributes:
      - name: code
        type: string
        required: true
        unique: true
        constraints: 大文字とアンダースコアだけ（BR5.2）
      - name: slug
        type: string
        required: true
        unique: true
        constraints: code を小文字にし、アンダースコアをハイフンにしたもの（例：VALIDATION_FAILED → validation-failed）
      - name: status
        type: integer
        required: true
        min: 400
        max: 599
      - name: title
        type: localized_text
        required: true
        constraints: 日本語・英語の両方がある
      - name: description
        type: localized_text
        required: true
        constraints: どんなときに起きるか。日本語・英語の両方がある。個々の要求の内容（入力値・例外の詳細）は含めない
      - name: resolution
        type: localized_text
        required: false
        constraints: 利用者がすべきこと。日本語・英語
    relationships: []

  - name: DisplayLanguage
    description: 画面の表示言語（FR2.3、NFR7）
    attributes:
      - name: code
        type: enum
        required: true
        allowed_values: [ja, en]
        default: ja
    relationships: []

  - name: FeatureRegistration
    description: 1つの機能が画面の骨組みに差し込む中身。各機能の決まった場所・名前の登録用ファイル1つに対応し、骨組みが起動時に自動で読み込む（Q5）
    attributes:
      - name: featureId
        type: string
        required: true
        unique: true
        constraints: 小文字の英字・数字・ハイフン
      - name: routes
        type: list<RouteRegistration>
        required: false
      - name: sidebarItems
        type: list<SidebarItemRegistration>
        required: false
      - name: userMenuItems
        type: list<UserMenuItemRegistration>
        required: false
      - name: loginStateProvider
        type: LoginStateProvider
        required: false
        constraints: 全機能を通じて最大1つ
    relationships:
      - target: RouteRegistration
        cardinality: one-to-many
        direction: FeatureRegistration → RouteRegistration
      - target: SidebarItemRegistration
        cardinality: one-to-many
        direction: FeatureRegistration → SidebarItemRegistration
      - target: UserMenuItemRegistration
        cardinality: one-to-many
        direction: FeatureRegistration → UserMenuItemRegistration
      - target: LoginStateProvider
        cardinality: one-to-zero-or-one
        direction: FeatureRegistration → LoginStateProvider

  - name: RouteRegistration
    description: 差し込み口1「画面（ルート）の登録」。画面とその URL
    attributes:
      - name: path
        type: string
        required: true
        unique: true
        constraints: / で始まる。全機能を通じて重複しない
      - name: screen
        type: reference
        required: true
        constraints: 表示する画面
      - name: layout
        type: enum
        required: true
        allowed_values: [SHELL, STANDALONE]
        constraints: SHELL はアプリシェルの中、STANDALONE はアプリシェルの外（ログイン画面など）
      - name: access
        type: enum
        required: true
        allowed_values: [PUBLIC, LOGGED_IN, ADMIN]
        default: LOGGED_IN
      - name: role
        type: enum
        required: false
        allowed_values: [LOGIN]
        constraints: LOGIN はログイン画面であることを示す。全機能を通じて最大1つ。LOGIN の画面は layout STANDALONE、access PUBLIC
    relationships: []

  - name: SidebarItemRegistration
    description: 差し込み口2「サイドバーの項目の登録」
    attributes:
      - name: id
        type: string
        required: true
        unique: true
      - name: labelKey
        type: string
        required: true
        constraints: 表示言語ごとの文言の鍵（日本語・英語の両方に文言がある）
      - name: path
        type: string
        required: true
        constraints: 登録済みの RouteRegistration.path
      - name: order
        type: integer
        required: true
      - name: visibleWhen
        type: enum
        required: true
        allowed_values: [LOGGED_IN, ADMIN]
        default: LOGGED_IN
    relationships: []

  - name: UserMenuItemRegistration
    description: 差し込み口3「ユーザーメニューの項目の登録」
    attributes:
      - name: id
        type: string
        required: true
        unique: true
      - name: labelKey
        type: string
        required: true
      - name: action
        type: reference
        required: true
        constraints: 選んだときに行う操作（例：ログアウト）
      - name: order
        type: integer
        required: true
    relationships: []

  - name: LoginStateProvider
    description: 差し込み口4「ログイン状態の提供元の登録」。ログインしているか、管理者かを返す手段
    attributes:
      - name: loggedIn
        type: boolean
        required: true
      - name: admin
        type: boolean
        required: true
        constraints: loggedIn が false のときは常に false
      - name: displayName
        type: string
        required: false
        constraints: ユーザーメニューに表示する名前（例：メールアドレス）
    relationships: []
```

## まとめ

| 情報 | 役割 | 保存先 |
|---|---|---|
| ErrorResponse | API のエラー応答（Problem Details＋`code`＋`traceId`） | なし（応答のみ） |
| ProblemType | 問題の種類（`code`・`type` の元、説明ページの内容） | アプリの定義（日本語・英語の文言） |
| DisplayLanguage | 画面の表示言語（ja／en） | なし（ブラウザの設定から毎回決める） |
| FeatureRegistration ほか4種 | 画面の骨組みの4つの差し込み口に登録される中身 | なし（起動時に読み込む） |
