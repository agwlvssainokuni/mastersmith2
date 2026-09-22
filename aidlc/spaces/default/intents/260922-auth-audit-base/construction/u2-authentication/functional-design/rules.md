# Business Rules — U2 認証（u2-authentication）

U2 の決まり。出典は要件定義書の FR・NFR、チームの進め方（TP）、`functional-design-questions.md` の確定回答（Q1〜Q11）、U1 の決まり（「U1 の決まり x.y」と書く）である。「現在時刻」は、注入できる時計から得る（TP：時刻に依存する処理はテストで実時間に依存しない）。

```yaml
rules:
  # ---- BR1 初期管理者の自動作成 ----
  - id: BR1.1
    statement: 起動時に、設定で指定したメールアドレスの利用者が内部DBに無く、設定が正しければ、管理者フラグを持つ利用者を作る
    category: policy
    applies_to: User
    trigger: アプリの起動（内部DBの準備の後）
    logic: IF 初期管理者のメールアドレスとパスワードが設定されている AND メールアドレスの形式が正しい AND パスワードが12文字以上 AND そのメールアドレス（小文字にそろえた値）の利用者がいない THEN adminFlag=true で作る
    violation: —
    source: FR3.1、NFR2、要件定義の前提 A1
  - id: BR1.2
    statement: そのメールアドレスの利用者が既にいれば、何もしない（パスワードや管理者フラグを上書きしない）
    category: constraint
    applies_to: User
    trigger: アプリの起動
    logic: IF 利用者が既にいる THEN 作らない・変えない
    violation: —
    source: FR3.2
  - id: BR1.3
    statement: 初期管理者の設定が無い、または正しくないときは、作らずに起動を続け、警告をログに出す。警告には、どの項目が足りないか・正しくないかと、設定を直して再起動すると作成されることを載せる
    category: policy
    applies_to: User
    trigger: アプリの起動
    logic: IF メールアドレスかパスワードが無い OR メールアドレスの形式が正しくない OR パスワードが12文字未満 THEN 作らない、WARN で理由と直し方を出す、起動は続ける
    violation: —
    source: FR3.3、Q1
  - id: BR1.4
    statement: 初期管理者のパスワードの値は、作る場合も作らない場合もログに出さない
    category: constraint
    applies_to: 初期管理者の設定
    trigger: アプリの起動
    logic: 警告やエラーには、パスワードの値も長さ以外の中身も載せない
    violation: テストでログにパスワードの値が含まれないことを確かめる
    source: FR3.4、NFR3
  - id: BR1.5
    statement: パスワードは一方向のハッシュにして保存し、平文では保存しない
    category: constraint
    applies_to: User.passwordHash
    trigger: 利用者の作成
    logic: ハッシュの方式は NFR の段階で決めたものを使う
    violation: —
    source: 要件定義の前提 A2、NFR3

  # ---- BR2 ログイン ----
  - id: BR2.1
    statement: メールアドレスは、前後の空白を除き小文字にそろえてから照合する
    category: calculation
    applies_to: User.email
    trigger: ログイン・利用者の作成
    logic: 照合の前に trim と小文字化を行う
    violation: —
    source: Q11
  - id: BR2.2
    statement: ログインの要求でメールアドレスかパスワードが空のときは、400、code VALIDATION_FAILED を返す。それ以外の形式（パスワードの長さなど）はログインでは検査しない
    category: validation
    applies_to: ログインの要求
    trigger: ログインの要求
    logic: IF メールアドレスまたはパスワードが無い・空 THEN 400 / VALIDATION_FAILED。長さの規則（12文字以上）は作成時の規則であり、ログインでは照合するだけ
    violation: —
    source: U1 の決まり 5.7、FR4.1
  - id: BR2.3
    statement: 利用者がいて、ロック中でなく、パスワードが一致すればログインは成功し、アクセストークンと CurrentUserView を応答で返し、リフレッシュトークンを Cookie で渡す
    category: policy
    applies_to: User、RefreshToken
    trigger: ログインの要求
    logic: IF 利用者がいる AND ロック中でない（BR3.4 の解除を先に適用）AND パスワードが一致 THEN 失敗回数を0にし（BR3.6）、アクセストークン（BR4.1）とリフレッシュトークン（BR5.1）を発行する
    violation: —
    source: FR4.1
  - id: BR2.4
    statement: ログインの失敗は、理由（存在しないメールアドレス・パスワード誤り・ロック中）によらず、同じ状態コード 401、同じ code AUTHENTICATION_FAILED、同じ内容で返す
    category: authorization
    applies_to: ログインの応答
    trigger: ログインの失敗
    logic: 応答の title・detail・code・type を理由によらず同一にする。理由は応答に載せず、出来事にだけ載せる（BR7.1）
    violation: テストで3つの理由の応答が同じであることを確かめる
    source: FR4.5、FR2.4、NFR4
  - id: BR2.5
    statement: 存在しないメールアドレスとロック中のアカウントでも、パスワード誤りのときと同じ重さのダミーの照合を行ってから失敗を返す
    category: policy
    applies_to: ログインの処理
    trigger: 利用者がいない、またはロック中
    logic: 固定のダミーのハッシュに対して入力されたパスワードを照合し、結果は捨てる
    violation: —
    source: Q2、NFR4
  - id: BR2.7
    statement: ログインが失敗する3つの経路（存在しないメールアドレス・ロック中・パスワード誤り）では、内部DBへの読み取りと書き込みの種類と回数をそろえる
    category: policy
    applies_to: ログインの処理
    trigger: ログインの失敗
    logic: どの経路も「利用者の検索1回、ロックの状態の読み取り1回（同じ利用者について1つずつ行う形）、ロックの状態への書き込み1回、パスワードの照合1回」を行う。存在しないメールアドレスでは、利用者と結びつかないダミーの記録に対して同じ読み取りと書き込みを行う（ダミーの記録で利用者どうしの待ち合わせが起きない形にする）。ロック中では、値を変えない書き込みを1回行う。パスワード誤りでは、失敗回数の更新（BR3.1）がその書き込みにあたる
    violation: テストで、3つの経路の内部DBへの読み取り・書き込みの回数が同じであることを確かめる（応答時間そのものは実行環境で揺れるため、テストでは比べない）
    source: レビュー指摘 R-01、NFR4、team.md（ユーザーIDの存在を推測できないことのテスト）
  - id: BR2.6
    statement: ログインが成功したら、LOGIN_SUCCEEDED の出来事を知らせる。失敗したら、理由を添えて LOGIN_FAILED を知らせる
    category: policy
    applies_to: AuthenticationEvent
    trigger: ログインの成功・失敗
    logic: BR7.1 の項目で知らせる
    violation: —
    source: FR9.1（記録は U4）

  # ---- BR3 アカウントロック ----
  - id: BR3.1
    statement: 利用者がいてロック中でないとき、パスワードが一致しなければ、連続失敗回数を1増やす
    category: calculation
    applies_to: LoginAttemptState
    trigger: パスワードの不一致
    logic: consecutiveFailures=consecutiveFailures＋1
    violation: —
    source: FR7.1
  - id: BR3.2
    statement: 連続失敗回数がしきい値（既定5回）に達したら、ロックする。ロックの解除時刻は、ロックした時刻＋ロックの時間（既定30分）
    category: policy
    applies_to: LoginAttemptState
    trigger: BR3.1 の後
    logic: IF consecutiveFailures ≥ しきい値 THEN lockedUntil=現在時刻＋ロックの時間
    violation: —
    source: FR7.1、FR7.3
  - id: BR3.3
    statement: ロック中は、パスワードが正しくてもログインを拒否し、失敗回数もロックの解除時刻も変えない
    category: authorization
    applies_to: LoginAttemptState
    trigger: ロック中のログインの試み
    logic: IF 現在時刻 < lockedUntil THEN 失敗（BR2.4）、状態の値は変えない（読み書きの回数は BR2.7 に従う）、ダミーの照合を行う（BR2.5）
    violation: —
    source: FR7.2、Q3
  - id: BR3.4
    statement: ロックの解除時刻を過ぎたら、ロックは解けたものとし、失敗回数を0に戻してから、その試みを通常どおり判定する
    category: policy
    applies_to: LoginAttemptState
    trigger: ログインの試み
    logic: IF lockedUntil がある AND 現在時刻 ≥ lockedUntil THEN lockedUntil を消し consecutiveFailures=0 にしてから BR2.3・BR3.1 を適用
    violation: —
    source: FR7.3、Q4
  - id: BR3.5
    statement: 連続失敗回数は時間の区切りなしに数え、成功するかロックが解けるまで戻さない
    category: policy
    applies_to: LoginAttemptState
    trigger: —
    logic: 経過時間では失敗回数を戻さない
    violation: —
    source: Q5
  - id: BR3.6
    statement: ログインに成功したら、連続失敗回数を0に戻す
    category: calculation
    applies_to: LoginAttemptState
    trigger: ログインの成功
    logic: consecutiveFailures=0
    violation: —
    source: FR7.4
  - id: BR3.7
    statement: ロックのしきい値（既定5回）とロックの時間（既定30分）は設定で変えられる
    category: policy
    applies_to: LoginAttemptState
    trigger: アプリの起動
    logic: 設定が無ければ既定値を使う
    violation: —
    source: FR7.5、NFR6
  - id: BR3.8
    statement: 同じ利用者へのログインの試みが同時に来ても、失敗回数の増加とロックの判定を取りこぼさない
    category: constraint
    applies_to: LoginAttemptState
    trigger: 同時のログインの試み
    logic: 読み取りから更新までを、同じ利用者について1つずつ行う（途中で他の試みの更新を失わない）
    violation: テストで同時の失敗が正しく数えられることを確かめる
    source: FR7.1 の確実な実現
  - id: BR3.9
    statement: 存在しないメールアドレスに対しては、そのメールアドレスのロックの状態を作らない
    category: constraint
    applies_to: LoginAttemptState
    trigger: 存在しないメールアドレスでの失敗
    logic: そのメールアドレスの状態は作らず、ダミーの照合（BR2.5）と、ダミーの記録への読み書き（BR2.7）を行う
    violation: —
    source: Domain Design（LoginAttemptState は利用者ごと）

  # ---- BR4 アクセストークン ----
  - id: BR4.1
    statement: アクセストークンには、利用者ID・発行時刻・有効期限だけを載せて署名する。メールアドレス・管理者フラグ・秘密情報は載せない
    category: constraint
    applies_to: AccessTokenClaims
    trigger: アクセストークンの発行
    logic: subject=userId、issuedAt=現在時刻、expiresAt=現在時刻＋有効期限
    violation: —
    source: Q8、NFR3
  - id: BR4.2
    statement: アクセストークンの有効期限は既定5分で、設定で変えられる。現在時刻が有効期限より前のときだけ有効とする
    category: validation
    applies_to: AccessTokenClaims
    trigger: 認証が必要な API の要求
    logic: IF 現在時刻 < expiresAt THEN 有効 ELSE 無効（4分59秒は有効、5分ちょうどは無効）
    violation: 401 / AUTHENTICATION_REQUIRED
    source: FR4.2、NFR6
  - id: BR4.3
    statement: アクセストークンの署名は、決めた1つの署名方式でだけ検証する。署名の無いもの（方式が none）や、別の方式を指定したもの、署名が一致しないものは無効とする
    category: validation
    applies_to: AccessTokenClaims
    trigger: 認証が必要な API の要求
    logic: トークンが示す方式を信用せず、サーバーが決めた方式と鍵で検証する
    violation: 401 / AUTHENTICATION_REQUIRED
    source: FR4.4
  - id: BR4.4
    statement: 認証が必要な API は、アクセストークンが無い、形式が正しくない、無効な要求を、401、code AUTHENTICATION_REQUIRED で拒否する
    category: authorization
    applies_to: 認証が必要な API
    trigger: 要求
    logic: トークンは Authorization ヘッダー（Bearer）で受け取る。拒否の理由（TOKEN_MISSING・TOKEN_MALFORMED・TOKEN_INVALID・TOKEN_EXPIRED・USER_NOT_FOUND）を、要求の処理の中で U3 が参照できるようにする（U3 がアクセス拒否の記録の要否に使う）。理由は応答には載せない
    violation: 401 / AUTHENTICATION_REQUIRED
    source: FR4.4、U3 Functional Design Q5
  - id: BR4.5
    statement: 有効なアクセストークンの要求では、利用者IDで内部DBから利用者を読み、AuthenticatedUser を作る。利用者がいなければ 401 とする。管理者フラグは内部DBの値を使う
    category: authorization
    applies_to: AuthenticatedUser
    trigger: 有効なアクセストークンの要求
    logic: IF User が見つかる THEN AuthenticatedUser（userId・email・admin=adminFlag）ELSE 401 / AUTHENTICATION_REQUIRED
    violation: —
    source: Q8
  - id: BR4.6
    statement: ログアウトしても、発行済みのアクセストークンは有効期限まで使える
    category: policy
    applies_to: AccessTokenClaims
    trigger: ログアウト後の要求
    logic: アクセストークンの失効の仕組みは持たない
    violation: —
    source: FR6.2、project.md Decided

  # ---- BR5 リフレッシュトークンとトークンの更新 ----
  - id: BR5.1
    statement: リフレッシュトークンの値は推測できない乱数とし、内部DBにはハッシュだけを保存する
    category: constraint
    applies_to: RefreshToken
    trigger: リフレッシュトークンの発行
    logic: 値は十分な長さの暗号学的な乱数。保存は tokenHash だけ。値がもともと推測できない乱数のため、tokenHash にはパスワード用の遅いハッシュではなく、速い暗号学的ハッシュ（例：SHA-256）を使う。具体的な方式は NFR の段階で、パスワードのハッシュ方式とは別に決める
    violation: —
    source: NFR3
  - id: BR5.2
    statement: リフレッシュトークンは、HttpOnly・Secure・SameSite=Strict の Cookie で渡し、送り先を認証の API（トークンの更新とログアウト）に限る。Cookie の寿命はリフレッシュトークンの有効期限と同じにする
    category: constraint
    applies_to: RefreshToken
    trigger: リフレッシュトークンの発行
    logic: Cookie の path を認証の API に限定、Max-Age=有効期限。Secure のため、TLS の終端が決まるまで（NFR・インフラの設計で決める）、開発・CI・E2E テストでの動作確認は http://localhost で行う。localhost 以外のホスト名や IP への http でのアクセスでは Cookie が送られず、ログインは働かない（当面の対象外）
    violation: —
    source: FR4.1、NFR5
  - id: BR5.3
    statement: リフレッシュトークンの有効期限は既定24時間で、設定で変えられる。保存されていて、無効にされておらず、現在時刻が有効期限より前のときだけ有効とする
    category: validation
    applies_to: RefreshToken
    trigger: トークンの更新・ログアウト
    logic: IF tokenHash が一致する行がある AND revokedAt が無い AND 現在時刻 < expiresAt THEN 有効（23時間59分59秒は有効、24時間ちょうどは無効）
    violation: 401 / REFRESH_FAILED
    source: FR4.3、FR5.2、NFR6
  - id: BR5.4
    statement: トークンの更新が成功したら、使ったリフレッシュトークンを無効にし、新しいリフレッシュトークン（作り直した時刻から有効期限を数える）と新しいアクセストークン、CurrentUserView を渡す
    category: policy
    applies_to: RefreshToken
    trigger: 有効なリフレッシュトークンでの更新
    logic: 古い行の revokedAt=現在時刻、新しい行を expiresAt=現在時刻＋有効期限で作る。利用者の情報は内部DBから読む
    violation: —
    source: FR5.1、Q10
  - id: BR5.5
    statement: 無効・期限切れ・存在しない・使用済みのリフレッシュトークン、または Cookie が無い更新は、401、code REFRESH_FAILED を返し、Cookie を消す。使用済みのトークンでも、ほかのトークンは無効にしない
    category: authorization
    applies_to: RefreshToken
    trigger: トークンの更新
    logic: 失敗の理由によらず同じ応答。Cookie を消す指示を返す
    violation: —
    source: FR5.2、Q6
  - id: BR5.6
    statement: 同じリフレッシュトークンで同時に更新が来ても、成功するのは1つだけとする
    category: constraint
    applies_to: RefreshToken
    trigger: 同時の更新
    logic: 無効化（revokedAt の設定）を、まだ無効でないことを条件にして1回だけ成功させる。負けた要求は BR5.5
    violation: テストで同時の更新の片方だけが成功することを確かめる
    source: FR5.1、Q9
  - id: BR5.7
    statement: 同じ利用者が複数のブラウザ・端末からログインでき、それぞれ別のリフレッシュトークンを持つ
    category: policy
    applies_to: RefreshToken
    trigger: ログイン
    logic: ログインのたびに新しい行を作り、既存の行は変えない
    violation: —
    source: Q7
  - id: BR5.8
    statement: 同じブラウザの複数のタブがほぼ同時に更新して片方が失敗しても、特別な対策はしない（失敗したタブはログインし直し）
    category: policy
    applies_to: 画面のトークンの更新
    trigger: 同時の更新
    logic: BR5.6 により片方は 401。画面はログイン画面へ移る（BR8.5）
    violation: —
    source: Q9

  # ---- BR6 ログアウト ----
  - id: BR6.1
    statement: ログアウトでは、Cookie のリフレッシュトークンが有効ならそれだけを無効にし、Cookie を消す。Cookie が無い・無効でも、同じ成功の応答を返す
    category: policy
    applies_to: RefreshToken
    trigger: ログアウトの要求
    logic: IF 有効 THEN revokedAt=現在時刻、LOGGED_OUT を知らせる。どの場合も Cookie を消し、成功（内容なし）を返す
    violation: —
    source: FR6.1、Q7
  - id: BR6.2
    statement: 同じ利用者のほかのブラウザ・端末のリフレッシュトークンは、ログアウトで無効にしない
    category: constraint
    applies_to: RefreshToken
    trigger: ログアウトの要求
    logic: 対象は Cookie で届いたトークン1件だけ
    violation: —
    source: Q7

  # ---- BR7 出来事の通知 ----
  - id: BR7.1
    statement: ログインの成功・失敗とログアウトの出来事を、日時・種類・入力されたメールアドレス・利用者ID（分かれば）・失敗の理由・接続元IP・User-Agent・トレースIDを添えてアプリ内に知らせる
    category: policy
    applies_to: AuthenticationEvent
    trigger: BR2.6、BR6.1
    logic: 失敗の理由は USER_NOT_FOUND・PASSWORD_MISMATCH・ACCOUNT_LOCKED のいずれか。接続元IPは要求の接続元（転送元のヘッダーは、U1 の決まり 5.10 と同じく信頼する設定があるときだけ使う）
    violation: —
    source: FR9.1、FR9.2（記録は U4）、U1 の決まり 5.10
  - id: BR7.2
    statement: 出来事には、パスワード・トークン・ハッシュ値を載せない
    category: constraint
    applies_to: AuthenticationEvent
    trigger: 出来事の通知
    logic: entities.md の AuthenticationEvent の項目だけを載せる
    violation: —
    source: NFR3
  - id: BR7.3
    statement: 出来事を知らせる側は受け取る側を知らず、受け取る側の失敗でログイン・ログアウトの結果を変えない。受け取りは要求と同じスレッドで行う
    category: constraint
    applies_to: AuthenticationEvent
    trigger: 出来事の通知
    logic: 通知の後に受け取る側で起きた失敗は、ログイン・ログアウトの応答に影響させない
    violation: —
    source: Domain Design ADR-004、FR9.4、U1 functional-spec.md 6.1

  # ---- BR8 画面 ----
  - id: BR8.1
    statement: ログイン画面は、U1 のログイン用レイアウトの中にメールアドレス・パスワードの入力欄とログインボタンを置き、U1 の「画面の登録」でログイン画面（role=LOGIN、アプリシェルの外、ログイン不要）として登録する
    category: policy
    applies_to: ログイン画面
    trigger: 画面の起動
    logic: U2 の登録用ファイルで登録する。U1 のファイルは変えない
    violation: —
    source: FR2.1、U1 の決まり 7.7
  - id: BR8.2
    statement: 画面は、メールアドレスかパスワードが空のときは送らずに入力欄の近くに知らせる。サーバーが AUTHENTICATION_FAILED を返したら、理由によらず同じ1つの文言を表示する
    category: validation
    applies_to: ログイン画面
    trigger: ログインボタン
    logic: 空の項目は送信前に止める。失敗の文言は1種類だけ
    violation: —
    source: FR2.4、FR4.5
  - id: BR8.3
    statement: アクセストークンは画面のメモリ上にだけ持ち、ブラウザの保存領域（localStorage・sessionStorage）に置かない。リフレッシュトークンは画面から扱わない
    category: constraint
    applies_to: 画面のログイン状態
    trigger: ログイン・更新
    logic: 画面の再読み込みでアクセストークンは消える
    violation: テストで保存領域にトークンが無いことを確かめる
    source: NFR5
  - id: BR8.4
    statement: 画面を開いたとき（再読み込み・新しいタブを含む）は、まずトークンの更新を1回試み、成功すればログイン中、失敗すれば未ログインとする
    category: policy
    applies_to: 画面のログイン状態
    trigger: 画面の起動
    logic: 更新の結果が出るまでは画面の振り分けを待つ
    violation: —
    source: FR5.3
  - id: BR8.5
    statement: API の要求にはアクセストークンを付ける。AUTHENTICATION_REQUIRED の 401 を受けたら、トークンの更新を1回だけ行い、成功すれば元の要求を1回だけ送り直す。更新に失敗したら、ログイン状態を消してログイン画面へ移る
    category: policy
    applies_to: API 呼び出しの共通部分
    trigger: API の応答が 401
    logic: 同じタブで同時に複数の要求が 401 を受けても、更新は1回にまとめる。送り直しでまた 401 なら、ログイン画面へ移る。ログイン・トークンの更新・ログアウトの API の呼び出しは、この「401 で更新して送り直す」処理の対象外とする（code の違いに頼らず、対象外であることを明示して繰り返しを防ぐ）
    violation: —
    source: Domain Design（ApiClient）、FR5.3
  - id: BR8.6
    statement: ログアウトでは、ログアウトの API を呼び、その結果によらずアクセストークンとログイン状態を消して、ログイン画面へ移る
    category: policy
    applies_to: 画面のログイン状態
    trigger: ユーザーメニューのログアウト
    logic: API の失敗（通信エラーを含む）でも、画面側の破棄は必ず行う
    violation: —
    source: FR6.1
  - id: BR8.7
    statement: ログイン状態の提供元として、ログイン中か・管理者か・表示名（メールアドレス）を返す手段を、U1 の差し込み口に登録する。ユーザーメニューにログアウトを登録する
    category: policy
    applies_to: 画面のログイン状態
    trigger: 画面の起動
    logic: admin は CurrentUserView の値。表示の切り替えにだけ使う
    violation: —
    source: U1 の決まり 7.3、FR2.2、FR8.2
  - id: BR8.8
    statement: ログインに成功したら、ホームへ移る
    category: policy
    applies_to: ログイン画面
    trigger: ログインの成功
    logic: 移動先はホーム（/）
    violation: —
    source: FR2.2（ログイン後の画面）

  # ---- BR9 問題の種類 ----
  - id: BR9.1
    statement: U2 は、AUTHENTICATION_FAILED（401）、AUTHENTICATION_REQUIRED（401）、REFRESH_FAILED（401）の問題の種類を、日本語・英語の説明とともに自分の場所に定義する
    category: constraint
    applies_to: 問題の種類
    trigger: —
    logic: U1 の共通の業務エラーの型で起こし、U1 が起動時に定義を集める
    violation: U1 の決まり 5.14 のテストで検出する
    source: U1 の決まり 5.14、U1 の決まり 5.16
```

## 決まりの一覧

| ID | 分類 | 決まり（要約） | 出典 |
|---|---|---|---|
| BR1.1 | policy | 初期管理者が無く設定が正しければ作る | FR3.1 |
| BR1.2 | constraint | 既にいれば何もしない（上書きしない） | FR3.2 |
| BR1.3 | policy | 設定が無い・不正なら作らず、理由と直し方を警告 | FR3.3、Q1 |
| BR1.4 | constraint | 初期管理者のパスワードをログに出さない | FR3.4 |
| BR1.5 | constraint | パスワードはハッシュで保存 | A2、NFR3 |
| BR2.1 | calculation | メールアドレスは trim・小文字化して照合 | Q11 |
| BR2.2 | validation | 空の入力は 400 / VALIDATION_FAILED | U1 の決まり 5.7 |
| BR2.3 | policy | ログインの成功条件と発行するもの | FR4.1 |
| BR2.4 | authorization | 失敗は理由によらず 401 / AUTHENTICATION_FAILED で同一 | FR4.5、FR2.4 |
| BR2.5 | policy | 存在しない・ロック中でもダミーの照合 | Q2 |
| BR2.6 | policy | ログインの出来事を知らせる | FR9.1 |
| BR2.7 | policy | 失敗の3経路で DB の読み書きの種類と回数をそろえる | R-01、NFR4 |
| BR3.1 | calculation | 不一致で失敗回数＋1 | FR7.1 |
| BR3.2 | policy | しきい値でロック（解除は30分後） | FR7.1、FR7.3 |
| BR3.3 | authorization | ロック中は拒否し、状態を変えない | FR7.2、Q3 |
| BR3.4 | policy | 解除時刻を過ぎたら解除し失敗回数0 | FR7.3、Q4 |
| BR3.5 | policy | 時間の区切りなしに数える | Q5 |
| BR3.6 | calculation | 成功で失敗回数0 | FR7.4 |
| BR3.7 | policy | しきい値と時間は設定で変更可 | FR7.5 |
| BR3.8 | constraint | 同時の試みでも取りこぼさない | FR7.1 |
| BR3.9 | constraint | 存在しないメールアドレスの状態は作らない（ダミーの記録で読み書き） | Domain Design、BR2.7 |
| BR4.1 | constraint | アクセストークンは利用者ID・発行時刻・期限だけ | Q8、NFR3 |
| BR4.2 | validation | 有効期限5分（設定可）、期限ちょうどで無効 | FR4.2 |
| BR4.3 | validation | 決めた署名方式だけで検証（none・別方式は無効） | FR4.4 |
| BR4.4 | authorization | 無い・無効なら 401 / AUTHENTICATION_REQUIRED | FR4.4 |
| BR4.5 | authorization | 要求ごとに DB から利用者と管理者フラグを読む | Q8 |
| BR4.6 | policy | ログアウト後もアクセストークンは期限まで有効 | FR6.2 |
| BR5.1 | constraint | リフレッシュトークンは乱数、保存は速いハッシュだけ | NFR3、R-04 |
| BR5.2 | constraint | Cookie は HttpOnly・Secure・SameSite=Strict、送り先を限定。当面の動作確認は localhost | FR4.1、NFR5、R-02 |
| BR5.3 | validation | 有効期限24時間（設定可）、期限ちょうどで無効 | FR4.3、FR5.2 |
| BR5.4 | policy | 更新で古いものを無効にし、新しいもの（24時間）を渡す | FR5.1、Q10 |
| BR5.5 | authorization | 無効な更新は 401 / REFRESH_FAILED、ほかは無効にしない | FR5.2、Q6 |
| BR5.6 | constraint | 同じトークンの同時更新は1つだけ成功 | FR5.1、Q9 |
| BR5.7 | policy | 複数のブラウザ・端末からのログインを許す | Q7 |
| BR5.8 | policy | タブの同時更新は対策しない | Q9 |
| BR6.1 | policy | ログアウトはそのトークンだけ無効化、常に成功を返す | FR6.1 |
| BR6.2 | constraint | ほかのブラウザのトークンは無効にしない | Q7 |
| BR7.1 | policy | 出来事を監査に要る項目つきで知らせる | FR9.1、FR9.2 |
| BR7.2 | constraint | 出来事に秘密情報を載せない | NFR3 |
| BR7.3 | constraint | 受け取る側を知らず、その失敗で結果を変えない | ADR-004、FR9.4 |
| BR8.1 | policy | ログイン画面を U1 の差し込み口で登録 | FR2.1 |
| BR8.2 | validation | 空の入力は送らない、失敗の文言は1種類 | FR2.4 |
| BR8.3 | constraint | アクセストークンはメモリだけ | NFR5 |
| BR8.4 | policy | 画面を開いたら更新を1回試みてログイン状態を戻す | FR5.3 |
| BR8.5 | policy | 401 で1回だけ更新・送り直し（認証の API は対象外）、失敗ならログイン画面へ | ApiClient、R-03 |
| BR8.6 | policy | ログアウトは API の結果によらず画面側を破棄 | FR6.1 |
| BR8.7 | policy | ログイン状態の提供元とログアウトの項目を登録 | U1 の決まり 7.3 |
| BR8.8 | policy | ログイン成功でホームへ | FR2.2 |
| BR9.1 | constraint | U2 の問題の種類を日英の説明つきで定義 | U1 の決まり 5.14 |
