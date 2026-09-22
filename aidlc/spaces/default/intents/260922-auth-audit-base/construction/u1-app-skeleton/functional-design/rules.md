# Business Rules — U1 アプリの骨格（u1-app-skeleton）

U1 の決まり。出典は要件定義書の FR・NFR、チームの進め方（TP）、`functional-design-questions.md` の確定回答（Q1〜Q9）、Units Generation の決定（UG）である。

```yaml
rules:
  # ---- BR1 ヘルスチェック ----
  - id: BR1.1
    statement: アプリが動いていて内部DBにつながるとき、ヘルスチェックは正常（UP）と応答する
    category: policy
    applies_to: ヘルスチェックの応答
    trigger: ヘルスチェックの要求
    logic: IF 内部DBへの確認の問い合わせが成功する THEN status=UP、状態コード 200
    violation: —
    source: FR1.1、Q1
  - id: BR1.2
    statement: 内部DBにつながらないとき、ヘルスチェックは異常（DOWN）と応答する
    category: policy
    applies_to: ヘルスチェックの応答
    trigger: ヘルスチェックの要求
    logic: IF 内部DBへの確認の問い合わせが失敗する、または制限時間内に終わらない THEN status=DOWN、状態コード 503。制限時間は設定で変えられ、既定は2秒とする
    violation: —
    source: FR1.1、Q1
  - id: BR1.3
    statement: ヘルスチェックの応答には状態だけを載せ、内訳（どこが異常か、接続先など）を載せない
    category: constraint
    applies_to: ヘルスチェックの応答
    trigger: ヘルスチェックの応答
    logic: 応答は状態（UP／DOWN）だけ
    violation: 内訳が載っていればテストで失敗とする
    source: Q1、NFR3
  - id: BR1.4
    statement: ヘルスチェックはログインしていなくても呼べる
    category: authorization
    applies_to: ヘルスチェック
    trigger: ヘルスチェックの要求
    logic: 認証を求めない
    violation: —
    source: FR1.1（起動確認に使うため）

  # ---- BR2 内部DBの接続設定 ----
  - id: BR2.1
    statement: 既定では、内部DBはアプリに組み込んでファイルに保存する形で動き、再起動してもデータが残る
    category: policy
    applies_to: 内部DBの接続設定
    trigger: アプリの起動
    logic: IF 接続設定が変えられていない THEN 組み込み・ファイル保存の形で、決めた保存先のファイルを使う
    violation: —
    source: FR1.2
  - id: BR2.2
    statement: 内部DBの接続先は、設定ファイル（または環境変数）の接続設定だけで切り替えられる
    category: policy
    applies_to: 内部DBの接続設定
    trigger: アプリの起動
    logic: IF 接続設定が変えられた THEN 変更後の接続先を使う。コードの変更は要らない
    violation: —
    source: FR1.2、NFR6
  - id: BR2.3
    statement: 内部DBのパスワードはソースや設定ファイルに直接書かず、ログにも出さない
    category: constraint
    applies_to: 内部DBの接続設定（パスワード）
    trigger: 設定の読み込み・ログ出力
    logic: パスワードは環境変数などから渡す
    violation: 秘密情報の検出で統合を止める
    source: NFR3、project.md Forbidden

  # ---- BR3 アプリのログ ----
  - id: BR3.1
    statement: アプリのログは1行1件の JSON で標準出力へ出す（開発時も同じ）
    category: policy
    applies_to: アプリのログ
    trigger: ログの出力
    logic: 1件を改行を含まない1行の JSON にする
    violation: —
    source: FR10.1、NFR10
  - id: BR3.2
    statement: ログの1件には、日時・レベル・ロガー名・スレッド・メッセージを必ず載せ、トレースID・スパンID・例外・呼び出し側が渡したキーと値はあれば載せる
    category: policy
    applies_to: アプリのログ
    trigger: ログの出力
    logic: 日時・レベル・ロガー名・スレッド・メッセージは必須。トレースID・スパンID・例外・キーと値は、あるときに載せる
    violation: —
    source: Q4
  - id: BR3.3
    statement: 要求の処理中に出るログには、その要求のトレースIDとスパンIDを載せる
    category: policy
    applies_to: アプリのログ
    trigger: 要求の処理中のログ出力
    logic: IF 要求の処理中 THEN ログのトレースID=その要求のトレースID
    violation: —
    source: FR10.2
  - id: BR3.4
    statement: ログにパスワード・トークン・署名鍵を載せない
    category: constraint
    applies_to: アプリのログ
    trigger: ログの出力
    logic: 秘密情報の値をメッセージにもキーと値にも入れない
    violation: テストで秘密情報が出ないことを確かめる
    source: NFR3
  - id: BR3.5
    statement: ログの項目は文字列の連結ではなく、キーと値で渡す
    category: constraint
    applies_to: アプリのログ（キーと値の項目）
    trigger: ログの出力
    logic: 可変の値はキーと値で渡す
    violation: レビューで指摘する
    source: TP（Code Style）

  # ---- BR4 分散トレースと外部エクスポート ----
  - id: BR4.1
    statement: 受け取った要求に形式の正しい traceparent があれば、そのトレースIDを引き継ぐ
    category: policy
    applies_to: トレース
    trigger: 要求の受け取り
    logic: IF traceparent があり W3C Trace Context の形式に合う THEN traceId=ヘッダーの trace-id、新しい spanId を割り当てる
    violation: —
    source: FR10.3
  - id: BR4.2
    statement: traceparent が無い、または形式に合わないときは、新しいトレースを始める
    category: policy
    applies_to: トレース
    trigger: 要求の受け取り
    logic: IF traceparent が無い OR 形式に合わない（桁数違い・16進数でない・すべて0） THEN 新しい traceId と spanId を割り当てる。要求は拒否しない
    violation: —
    source: FR10.3
  - id: BR4.3
    statement: トレースの属性に秘密情報を載せない
    category: constraint
    applies_to: トレース
    trigger: トレースの記録・エクスポート
    logic: パスワード・トークン・署名鍵を属性に入れない
    violation: —
    source: NFR3
  - id: BR4.4
    statement: 外部エクスポートは既定で無効とする
    category: policy
    applies_to: 外部エクスポートの設定
    trigger: アプリの起動
    logic: IF 有効化の設定が無い THEN 無効とし、外部へ何も送らない
    violation: —
    source: FR10.4、NFR10
  - id: BR4.5
    statement: 外部エクスポートは設定で有効にでき、有効にしても送信の失敗は要求の処理に影響させない
    category: policy
    applies_to: 外部エクスポートの設定
    trigger: アプリの起動・エクスポート
    logic: IF 有効化の設定がある THEN 設定した送り先へ送る。送信に失敗したら警告をログに出し、要求の処理は続ける
    violation: —
    source: FR10.4、NFR6

  # ---- BR5 共通のエラー応答 ----
  - id: BR5.1
    statement: API のエラー応答は、すべて ErrorResponse の形（Problem Details＋code＋traceId）で返す
    category: policy
    applies_to: ErrorResponse
    trigger: API の処理でエラーが起きた
    logic: 例外を1か所でまとめて ErrorResponse に変換する。個々の API で応答を組み立てない
    violation: —
    source: TP（Code Style）
  - id: BR5.2
    statement: code は大文字とアンダースコアで書き、一度決めた値は変えない
    category: validation
    applies_to: ErrorResponse.code
    trigger: code の定義
    logic: code は ^[A-Z][A-Z0-9_]*$ に合う
    violation: テストで形式を確かめる
    source: Q2
  - id: BR5.3
    statement: すべてのエラー応答に、その要求のトレースIDを載せる
    category: policy
    applies_to: ErrorResponse.traceId
    trigger: エラー応答
    logic: ErrorResponse.traceId=その要求のトレースID（ログに載るものと同じ値）
    violation: —
    source: Q3
  - id: BR5.4
    statement: エラー応答に、内部の例外メッセージ・スタックトレース・秘密情報を載せない
    category: constraint
    applies_to: ErrorResponse
    trigger: エラー応答
    logic: title・detail には利用者に見せてよい説明だけを入れる
    violation: テストで確かめる
    source: TP（Code Style）、NFR3
  - id: BR5.5
    statement: 想定したエラー（4xx）は WARN 以下でスタックトレースなし、想定外のエラー（5xx）は ERROR でスタックトレース付きで、変換する場所で1回だけログに出す
    category: policy
    applies_to: アプリのログ
    trigger: エラー応答への変換
    logic: IF status が 4xx THEN WARN 以下、例外の詳細なし ELSE ERROR、例外の詳細あり
    violation: —
    source: TP（Code Style）
  - id: BR5.6
    statement: 想定外のエラーは、状態コード 500、code INTERNAL_ERROR で返す
    category: policy
    applies_to: ErrorResponse
    trigger: 変換の対象として決めていない例外
    logic: status=500、code=INTERNAL_ERROR、detail は一般的な説明だけ
    violation: —
    source: TP（Code Style）
  - id: BR5.7
    statement: 入力の検証に失敗した要求は、状態コード 400、code VALIDATION_FAILED で返す
    category: validation
    applies_to: ErrorResponse
    trigger: 要求の入力の検証失敗
    logic: status=400、code=VALIDATION_FAILED
    violation: —
    source: TP（Code Style）、construction.md（入力の検証）
  - id: BR5.8
    statement: 存在しない API を呼んだ要求は、状態コード 404、code NOT_FOUND で返す
    category: policy
    applies_to: ErrorResponse
    trigger: 存在しない API のパス
    logic: status=404、code=NOT_FOUND
    violation: —
    source: BR5.1 の適用

  - id: BR5.9
    statement: エラー応答の type には、その問題の種類の説明ページの絶対 URL を入れる。URL はベースURLに /api/problems/ と種類の slug を足したものとする
    category: policy
    applies_to: ErrorResponse.type
    trigger: エラー応答
    logic: type=ベースURL＋/api/problems/＋ProblemType.slug。slug は code を小文字にしアンダースコアをハイフンにしたもの
    violation: —
    source: Q8
  - id: BR5.10
    statement: ベースURLは、設定で固定値を与えたときはそれを使い、無ければ要求から組み立てる。転送元のヘッダー（X-Forwarded-* や Forwarded）は、信頼する設定を有効にしたときだけ使い、既定では無視する
    category: policy
    applies_to: ErrorResponse.type
    trigger: エラー応答
    logic: IF 設定にベースURLがある THEN それを使う ELSE IF 転送元のヘッダーを信頼する設定が有効 THEN 転送元のヘッダーのスキーム・ホスト・ポートを使う ELSE 要求そのもののスキーム・Host・ポートを使う。転送元のヘッダーを信頼する設定は既定で無効
    violation: —
    source: Q8、レビュー指摘 R-02（Host ヘッダーの偽装への備え）
  - id: BR5.11
    statement: type の URL へ GET すると、その問題の種類の説明（名前・状態コード・code・起きるとき・利用者がすべきこと）を返す。要求が HTML を求めれば HTML、それ以外は JSON で返し、言語は表示言語の決め方（BR6.1）に従う
    category: policy
    applies_to: ProblemType
    trigger: /api/problems/<slug> への GET
    logic: IF slug が定義済み THEN 200 で説明を返す（Accept が HTML を優先すれば HTML、そうでなければ JSON）。言語は BR6.3 で決める
    violation: —
    source: Q9
  - id: BR5.12
    statement: 説明ページはログインしていなくても見られ、個々の要求の内容（入力値・例外の詳細・秘密情報）は載せない
    category: authorization
    applies_to: ProblemType
    trigger: /api/problems/<slug> への GET
    logic: 認証を求めない。返すのは ProblemType の定義だけ
    violation: —
    source: Q9、NFR3
  - id: BR5.13
    statement: 定義されていない slug の説明ページを求められたら、404（code NOT_FOUND）のエラー応答を返す
    category: validation
    applies_to: ProblemType
    trigger: /api/problems/<slug> への GET
    logic: IF slug が定義されていない THEN BR5.8 と同じく 404 / NOT_FOUND（その type は not-found の説明ページを指す）
    violation: —
    source: Q9
  - id: BR5.14
    statement: エラー応答で使うすべての code について、日本語・英語の説明を持つ ProblemType を定義する
    category: constraint
    applies_to: ProblemType
    trigger: code の追加
    logic: code を追加するときは、同時に ProblemType（日英の title・description）を定義する
    violation: テストで、使われる code すべてに説明があることを確かめる
    source: Q8、Q9、BR6.2
  - id: BR5.15
    statement: 説明ページを HTML で返すときは、埋め込むすべての値を HTML としてエスケープする
    category: constraint
    applies_to: ProblemType
    trigger: 説明ページの HTML 生成
    logic: 名前・説明・利用者がすべきこと・code などの値は、必ずエスケープしてから埋め込む。要求から受け取った値（slug を含む）はページに埋め込まない
    violation: テストで、HTML の特殊文字を含む定義がエスケープされて表示されることを確かめる
    source: レビュー指摘 R-04、construction フェーズの入力のサニタイズの決まり
  - id: BR5.16
    statement: 後の単位は、U1 のファイルを書き換えずに、自分の想定内のエラーと問題の種類を加えられる
    category: policy
    applies_to: ErrorResponse、ProblemType
    trigger: 後の単位が想定内のエラーを加える
    logic: U1 は、問題の種類（状態コード・code・日英の説明）を持つ共通の業務エラーの型を用意する。後の単位は、その型のエラーを起こせば、共通の変換で ErrorResponse に変換される。問題の種類の定義は各機能が自分の場所に置き、U1 が起動時にすべて集める。code・slug が重複したら起動を失敗させる
    violation: 起動のテストで重複を検出する
    source: レビュー指摘 R-05、Units Generation の決定（後の単位は U1 のファイルを書き換えない）
  # ---- BR6 表示言語 ----
  - id: BR6.1
    statement: 表示言語はブラウザの言語設定で決め、日本語でも英語でもなければ日本語にする
    category: calculation
    applies_to: DisplayLanguage
    trigger: 画面の表示
    logic: ブラウザの希望言語を順に見て、最初に ja または en に当たったものを使う。どれにも当たらなければ ja
    violation: —
    source: FR2.3、NFR7
  - id: BR6.2
    statement: 画面の文言はすべて文言の鍵で扱い、日本語と英語の両方を用意する
    category: constraint
    applies_to: 画面の文言
    trigger: 画面の文言の追加
    logic: 画面に直接文言を書かない。鍵ごとに ja と en の文言がある
    violation: テストで両言語の文言がそろっていることを確かめる
    source: FR2.3、NFR7

  - id: BR6.3
    statement: サーバーが言語を決めるとき（説明ページなど）は、要求の Accept-Language から決め、日本語でも英語でもなければ日本語にする
    category: calculation
    applies_to: DisplayLanguage
    trigger: サーバーが言語つきの内容を返す
    logic: Accept-Language の各言語を q 値の大きい順に並べ（q=0 は除く、q 値が同じなら書かれた順）、先頭の言語部分（ja-JP なら ja）が最初に ja または en に当たったものを使う。ヘッダーが無い、形式が正しくない、どれにも当たらない場合は ja
    violation: —
    source: レビュー指摘 R-03、FR2.3、NFR7
  # ---- BR7 画面の骨組みと差し込み口 ----
  - id: BR7.1
    statement: 骨組みは起動時に、各機能の決まった場所・名前の登録用ファイルをすべて自動で読み込み、4つの差し込み口に登録する
    category: policy
    applies_to: FeatureRegistration
    trigger: 画面の起動
    logic: 登録用ファイルを見つけたものから順に読み込む。後の単位は U1 のファイルを変えずに登録できる
    violation: —
    source: Q5、UG（R-01）
  - id: BR7.2
    statement: 画面の URL・サイドバーとユーザーメニューの項目の id・featureId が重複する、またはログイン状態の提供元やログイン画面が2つ以上登録されたときは、起動を失敗させる
    category: validation
    applies_to: FeatureRegistration
    trigger: 画面の起動
    logic: IF 重複がある THEN どの登録が重複したかを示すエラーで起動を止める
    violation: 起動のテストで検出する
    source: BR7.1 の整合
  - id: BR7.3
    statement: ログイン状態の提供元が登録されていない間は、未ログインとして扱う
    category: policy
    applies_to: LoginStateProvider
    trigger: ログイン状態の判定
    logic: IF 提供元が無い THEN loggedIn=false、admin=false
    violation: —
    source: UG（R-01）
  - id: BR7.4
    statement: ログインしていない利用者が、access が LOGGED_IN または ADMIN の画面を開いたときは、ログイン画面へ導く。ログイン画面が登録されていなければ、ログイン画面のレイアウトだけを表示する
    category: authorization
    applies_to: RouteRegistration
    trigger: 画面を開く
    logic: IF loggedIn=false AND access≠PUBLIC THEN role=LOGIN の画面へ移る（無ければログイン用レイアウトだけを表示）
    violation: —
    source: FR2.1、Q6
  - id: BR7.5
    statement: access が ADMIN の画面は、管理者でなければ表示しない（画面での表示の制御であり、サーバー側の権限確認の代わりにはならない）
    category: authorization
    applies_to: RouteRegistration
    trigger: 画面を開く
    logic: IF loggedIn=true AND admin=false AND access=ADMIN THEN 「ページが見つかりません」の画面を表示する
    violation: —
    source: FR8.2 との整合
  - id: BR7.6
    statement: サイドバーには、ログイン後に「ホーム」を常に表示し、登録された項目は visibleWhen を満たすときだけ order の順に表示する
    category: policy
    applies_to: SidebarItemRegistration
    trigger: アプリシェルの表示
    logic: 「ホーム」を先頭に置き、残りは visibleWhen（LOGGED_IN=ログイン中、ADMIN=管理者）を満たすものだけを order の順に並べる
    violation: —
    source: FR2.2（ホーム）、UG
  - id: BR7.7
    statement: ログイン画面（role=LOGIN）はアプリシェルの外の独立したレイアウトで表示し、U1 はレイアウト（アプリ名・表示言語に応じた見出し）だけを持つ
    category: policy
    applies_to: RouteRegistration
    trigger: ログイン画面の表示
    logic: role=LOGIN の画面は layout=STANDALONE。入力欄とボタンは U2 が用意する
    violation: —
    source: FR2.1、Q6
  - id: BR7.8
    statement: 存在しない URL を開いたときは、ログイン中なら「ページが見つかりません」の画面とホームへのリンクを表示し、未ログインならログイン画面へ導く
    category: policy
    applies_to: RouteRegistration
    trigger: 登録されていない URL を開く
    logic: IF loggedIn=true THEN 見つからない画面（アプリシェルの中）ELSE BR7.4 と同じくログイン画面へ
    violation: —
    source: Q7
  - id: BR7.9
    statement: ホームの画面は U1 が用意し、ログイン後の最初の画面とする
    category: policy
    applies_to: RouteRegistration
    trigger: ログイン後、または / を開く
    logic: / はホーム（access=LOGGED_IN、layout=SHELL）
    violation: —
    source: FR2.2
```

## 決まりの一覧

| ID | 分類 | 決まり（要約） | 出典 |
|---|---|---|---|
| BR1.1 | policy | アプリ稼働＋DB接続で UP（200） | FR1.1、Q1 |
| BR1.2 | policy | DB に接続できない、または制限時間（既定2秒、設定で変更可）を超えたら DOWN（503） | FR1.1、Q1 |
| BR1.3 | constraint | ヘルスの応答は状態だけ | Q1、NFR3 |
| BR1.4 | authorization | ヘルスは未ログインで呼べる | FR1.1 |
| BR2.1 | policy | 既定は組み込み・ファイル保存で再起動後も残る | FR1.2 |
| BR2.2 | policy | 接続先は設定だけで切り替え | FR1.2、NFR6 |
| BR2.3 | constraint | DB のパスワードは直書き・ログ出力しない | NFR3 |
| BR3.1 | policy | ログは1行1件の JSON で標準出力 | FR10.1 |
| BR3.2 | policy | ログの項目 | Q4 |
| BR3.3 | policy | 要求中のログにトレースID・スパンID | FR10.2 |
| BR3.4 | constraint | ログに秘密情報を載せない | NFR3 |
| BR3.5 | constraint | ログの項目はキーと値で渡す | TP |
| BR4.1 | policy | 正しい traceparent は引き継ぐ | FR10.3 |
| BR4.2 | policy | 無い・不正な traceparent は新しいトレース | FR10.3 |
| BR4.3 | constraint | トレースの属性に秘密情報を載せない | NFR3 |
| BR4.4 | policy | 外部エクスポートは既定で無効 | FR10.4 |
| BR4.5 | policy | 設定で有効化、送信失敗は要求に影響させない | FR10.4 |
| BR5.1 | policy | エラー応答は共通の形で1か所で変換 | TP |
| BR5.2 | validation | code は大文字＋アンダースコア、不変 | Q2 |
| BR5.3 | policy | エラー応答にトレースID | Q3 |
| BR5.4 | constraint | 内部の例外・秘密情報を載せない | TP、NFR3 |
| BR5.5 | policy | 4xx は WARN、5xx は ERROR で1回だけログ | TP |
| BR5.6 | policy | 想定外は 500 / INTERNAL_ERROR | TP |
| BR5.7 | validation | 入力の検証失敗は 400 / VALIDATION_FAILED | TP |
| BR5.8 | policy | 存在しない API は 404 / NOT_FOUND | BR5.1 |
| BR5.9 | policy | type は説明ページの絶対 URL（ベースURL＋/api/problems/＋slug） | Q8 |
| BR5.10 | policy | ベースURLは設定の固定値、無ければ要求から。転送元のヘッダーは信頼の設定があるときだけ使う | Q8、R-02 |
| BR5.11 | policy | 説明ページは HTML／JSON を切り替え、日英で返す | Q9 |
| BR5.12 | authorization | 説明ページは未ログインで見られ、要求の中身を載せない | Q9、NFR3 |
| BR5.13 | validation | 未定義の slug は 404 / NOT_FOUND | Q9 |
| BR5.14 | constraint | 使うすべての code に日英の説明を定義 | Q8、Q9 |
| BR5.15 | constraint | 説明ページの HTML は値をすべてエスケープ | R-04 |
| BR5.16 | policy | 後の単位は共通の業務エラーの型と問題の種類の定義で、U1 を変えずにエラーを加える | R-05 |
| BR6.1 | calculation | 表示言語の決め方（既定は日本語） | FR2.3、NFR7 |
| BR6.2 | constraint | 文言は鍵で扱い日英をそろえる | FR2.3、NFR7 |
| BR6.3 | calculation | サーバー側は Accept-Language（q 値順）で言語を決め、既定は日本語 | R-03、FR2.3 |
| BR7.1 | policy | 登録用ファイルの自動読み込み | Q5 |
| BR7.2 | validation | 登録の重複で起動失敗 | BR7.1 |
| BR7.3 | policy | 提供元が無ければ未ログイン | UG |
| BR7.4 | authorization | 未ログインはログイン画面へ | FR2.1、Q6 |
| BR7.5 | authorization | ADMIN の画面は管理者だけ（表示の制御） | FR8.2 |
| BR7.6 | policy | サイドバーはホーム＋条件を満たす項目 | FR2.2 |
| BR7.7 | policy | ログイン画面はシェルの外、U1 はレイアウトだけ | FR2.1、Q6 |
| BR7.8 | policy | 存在しない URL の扱い | Q7 |
| BR7.9 | policy | ホームは U1 が用意 | FR2.2 |
