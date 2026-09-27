# Rules — U3 招待と登録の完了（u3-invitation）

U3 の決まりの正本。値の形は `entities.md`、手順と状態の移り変わりは `functional-spec.md`。出典の Q1〜Q4 は `functional-design-questions.md` の答え（Q1: C・Q2: C・Q3: A・Q4: A）、「要点 n」は同じファイルの「設計の要点（案）」の番号、C1〜C10 は契約 `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`、U1 BRx.y・U2 BRx.y は依存する単位の `rules.md` の決まり。具体の値のうち、SMTP の時間切れ・登録の完了の公開の API の回数の制限は NFR 要件、同じメールアドレスの招待中を1件に限る仕組みと行の排他の作りは NFR 設計、設定の項目の名前・定期の処理の既定の時刻・削除の件数の上限はコード生成で決める。

```yaml
rules:
  # ---- BR1: 招待の入力と、招待を使える設定 ----
  - id: BR1.1
    statement: 招待のメールアドレスは、既存の決まりで正規化し、改行を含まない・254 文字まで・形式に合うものだけを受け付ける
    category: validation
    applies_to: InvitationRequest.email
    trigger: 招待の要求
    logic: "IF 入力の値（正規化の前）に CR か LF を1つでも含む THEN 拒否する（前後の改行が正規化で除かれて通るのを防ぐ）。ELSE 既存の EmailAddress.normalize（前後の空白を除き小文字にそろえる）でそろえ、IF そろえた値が空・254 文字を超える・既存の形式（空白と @ を含まない文字列 @ 空白と @ を含まない文字列 . 空白と @ を含まない文字列）に合わない THEN 拒否する。以降の比較（登録済み・招待中）と保存はそろえた値で行う"
    violation: 400 VALIDATION_FAILED（項目 email）。招待は作られず、メールは送られない。応答にメールアドレスの値を載せない
    source: FR1.2、AC1.1.3、AC1.1.10、要点 8
  - id: BR1.2
    statement: 招待の言語は ja・en の完全な一致だけを受け付ける
    category: validation
    applies_to: InvitationRequest.language
    trigger: 招待の要求
    logic: "IF language が ja・en のどちらでもない（空・大文字・前後の空白を含む）THEN 拒否する。email と language の誤りはまとめて集めて返す"
    violation: 400 VALIDATION_FAILED（項目 language）。招待は作られず、メールは送られない
    source: FR1.2、AC1.1.3
  - id: BR1.3
    statement: ベース URL は、http か https の絶対 URL で、ホストがあり、問い合わせ・フラグメント・利用者情報を含まないものだけを使える値とする
    category: validation
    applies_to: ベース URL の設定（mastersmith.web.base-url）
    trigger: アプリの起動
    logic: "IF 値が無い・空白だけ THEN 使える値が無いとし、警告を出さない（招待を使わない使い方を許す。U1 の BR1.2 と同じ考え方）。ELSE 前後の空白を除き、IF スキームが http・https のどちらでもない、またはホストが無い、または問い合わせ（?）・フラグメント（#）・利用者情報（user@）を含む、または URL として解釈できない THEN 使える値が無いとし、起動のときに項目の名前だけの WARN を1件出す（値は出さない）。パスは許し、末尾の / は除いて使う。どちらの場合も起動は止めない。既存のエラー応答の type の URL の扱い（ProblemBaseUrlResolver）は変えない"
    violation: 起動は止めない。招待を使える設定かの判定で BASE_URL_NOT_CONFIGURED（BR1.4）
    source: Q3、FR1.7、FR1.8、要点 6・13
  - id: BR1.4
    statement: ベース URL に使える値があり、かつメールの送信の設定がある（U1 の isConfigured が真）ときだけ、招待を使える設定とする
    category: validation
    applies_to: InvitationAvailability
    trigger: アプリの起動（判定は1回で、動いている間は変わらない）
    logic: "IF BR1.3 で使える値が無い THEN 理由に BASE_URL_NOT_CONFIGURED を加える。IF U1 の isConfigured が偽 THEN 理由に SMTP_NOT_CONFIGURED を加える。理由が1つも無いときだけ enabled とする。理由は BASE_URL_NOT_CONFIGURED、SMTP_NOT_CONFIGURED の順に並べ、設定の値そのものは返さない"
    violation: —（判定の結果を BR1.5・BR5.4 が使う）
    source: FR1.8、C5、要点 13
  - id: BR1.5
    statement: 招待を使えない設定のときは、招待の作成と送り直しだけを拒否し、一覧・取り消し・リンクの確かめ・登録の完了は行える
    category: constraint
    applies_to: 招待の作成・送り直し
    trigger: 招待の要求（入力の検証の後）、送り直しの要求（最初）
    logic: "IF InvitationAvailability が enabled でない THEN 招待の作成は BR1.1・BR1.2 の入力の検証の後に、送り直しは対象を探す前に、内部DB を変えずメールを送らずに拒否する。応答に unavailableReasons（1つ以上）を付ける。送り直しでは招待のトークン・有効期限・送信の結果を変えず、前のリンクは有効なまま"
    violation: 503 INVITATION_NOT_CONFIGURED（unavailableReasons 付き）。テスト用の受け手が受けるメールは0通
    source: FR1.8、AC1.1.6、AC1.1.11、AC2.2.9、C5、要点 9・13
  - id: BR1.6
    statement: 招待の有効期限の長さと、終わった招待の保存の日数は設定で変えられ、既定は 24 時間と 90 日とする
    category: policy
    applies_to: 招待の設定
    trigger: アプリの起動
    logic: "有効期限の長さ（★既定 24 時間）と保存の日数（★既定 90 日）と定期の削除の時刻を設定から読む。IF 値が無い THEN 既定を使う。IF 0 以下など不正 THEN 起動を止める（アプリに同梱する既定の値の誤りで、利用者の設定の不足とは違うため）。招待メールの本文の「24 時間有効です」の文はテンプレートの固定の文面で、長さの設定を変えたときはテンプレートも合わせて直す"
    violation: 起動の失敗（不正な値のとき）
    source: FR1.5、Q1、要点 3

  # ---- BR2: 招待の作成 ----
  - id: BR2.1
    statement: 招待しようとしたメールアドレスの利用者がすでにいれば拒否する
    category: constraint
    applies_to: 招待の作成
    trigger: 招待の要求（BR1.5 の後、招待のトランザクションの中）
    logic: "IF そろえたメールアドレスで UserAccount の existsByEmail（C2）が真 THEN 拒否する。この判定は招待中の判定（BR2.2）より先に行う。この API は管理者だけが呼べるため、登録済みと招待中を区別して返してよい（NFR3 の対象外）"
    violation: 409 INVITATION_EMAIL_REGISTERED。招待は作られず、メールは送られない。応答にメールアドレスを載せない
    source: FR1.4、AC1.1.4、AC1.1.10、C2、C5
  - id: BR2.2
    statement: 同じメールアドレスの期限内の招待中があれば拒否し、その招待の ID と一覧のページを返す
    category: constraint
    applies_to: 招待の作成
    trigger: 招待の要求（招待のトランザクションの中）
    logic: "IF 同じ email で state が PENDING かつ有効（BR3.3）の招待がある THEN 拒否し、応答にその invitationId と BR2.3 の page を付ける"
    violation: 409 INVITATION_ALREADY_PENDING（invitationId・page 付き）。招待は作られず、メールは送られない
    source: FR1.4、AC1.1.4、C5、要点 4
  - id: BR2.3
    statement: 招待中の案内の page は、一覧と同じ並びでのその招待の位置から求める
    category: calculation
    applies_to: 409 INVITATION_ALREADY_PENDING の page
    trigger: BR2.2 の拒否
    logic: "一覧の対象（BR5.1）を一覧の並び（BR5.2）に並べたときのその招待の位置を 1 から数えて p とし、page ＝ p ÷ 20 の切り上げとする（例: p が 1〜20 なら 1、21 なら 2）"
    violation: —
    source: AC1.1.4、C5、要点 12
  - id: BR2.4
    statement: 同じメールアドレスの期限切れの招待中は、新しい招待と同じトランザクションで置き換え済みにし、一覧から消す
    category: policy
    applies_to: 招待の作成
    trigger: 招待の要求（招待のトランザクションの中）
    logic: "IF 同じ email で state が PENDING かつ期限切れ（BR3.3 で無効）の招待がある THEN その行の state を REPLACED、endedAt を時計の今にしてから、新しい招待を作る。置き換えた招待のリンクは以降 INVITATION_EXPIRED の理由で拒否される（BR7.6）。置き換えは監査の出来事を持たず、新しい招待の INVITATION_ISSUED の1件だけを記録する"
    violation: —
    source: AC2.2.6、要点 3・4
  - id: BR2.5
    statement: 同じメールアドレスの state が PENDING の招待は常に1件までとし、同時の招待では1件だけを作る
    category: constraint
    applies_to: Invitation
    trigger: 招待の作成の確定
    logic: "IF 2つの要求が同時に同じメールアドレスを招待する THEN 招待は1件だけ作られ、もう一方は BR2.2 と同じ 409 INVITATION_ALREADY_PENDING（先に確定した招待の invitationId・page）で拒否される。もう一方の要求ではメールを送らず、INVITATION_ISSUED を記録しない。1件に限る仕組み（索引か行の排他か）は NFR 設計で決める（ADR-010）"
    violation: 409 INVITATION_ALREADY_PENDING
    source: AC1.1.13、ADR-010、要点 4
  - id: BR2.6
    statement: 新しい招待は、正規化したメールアドレス・言語・操作した管理者・時計の今と有効期限・新しいトークンのハッシュで作り、状態と送信の結果を PENDING にする
    category: policy
    applies_to: 招待の作成
    trigger: BR2.1・BR2.2 を通った招待の要求
    logic: "email はそろえた値、language は入力の値、invitedByUserId は操作した管理者、invitedAt は時計の今、expiresAt は invitedAt ＋ 有効期限の長さ（BR1.6）、tokenHash は BR3.1 で作ったトークンのハッシュ、state は PENDING、sendResult は PENDING（BR4.4）とし、BR2.1〜BR2.4 と同じ1つの短いトランザクションで確定する。確定の後に INVITATION_ISSUED を知らせ（BR8.1）、BR4.1 の送信へ進む"
    violation: —
    source: FR1.1、FR1.5、FR1.6、AC1.1.2、要点 8
  - id: BR2.7
    statement: 取り消した招待・登録を完了した招待と同じメールアドレスへの新しい招待を許す
    category: policy
    applies_to: 招待の作成
    trigger: 招待の要求
    logic: "state が CANCELLED・COMPLETED・REPLACED の行は BR2.2 の招待中に数えない。IF 取り消し・置き換えの後で利用者がいない THEN 新しく招待できる（完了の後は利用者がいるため BR2.1 で拒否される）"
    violation: —
    source: AC2.2.5、要件の前提 A4

  # ---- BR3: トークン・有効の判定・招待の URL ----
  - id: BR3.1
    statement: 招待のトークンは暗号学的な乱数 32 バイトから作り、内部DB にはその SHA-256 のハッシュだけを一意の値として保存する
    category: constraint
    applies_to: InvitationToken・Invitation.tokenHash
    trigger: 招待の作成・送り直し
    logic: "暗号学的に安全な乱数 32 バイト（256 ビット）を、URL で使える Base64（埋め草なし、43 文字）にしてトークンとする（既存のリフレッシュトークンと同じ作り方）。保存するのは UTF-8 のトークンの SHA-256（32 バイト）だけで、トークンの値は招待メールの依頼（BR4.2）にだけ渡し、保存・応答・ログに出さない"
    violation: —（構造で守る）
    source: FR1.5、NFR1、project.md の Mandated、要点 5
  - id: BR3.2
    statement: 形の合わないトークンは、ハッシュを引かずに「見つからない」とする
    category: validation
    applies_to: TokenRequest.token・CompleteRequest.token
    trigger: リンクの確かめ・登録の完了
    logic: "IF トークンが無い・空・43 文字でない・URL で使える Base64 の文字（英大小文字・数字・- ・_）以外を含む THEN 内部DB を引かずに見つからない（INVITATION_NOT_FOUND）とする。ELSE SHA-256 のハッシュで tokenHash を引き、無ければ見つからない（送り直しで古くなったトークン・改ざんを含む）"
    violation: 404 REGISTRATION_LINK_INVALID（BR7.5）
    source: NFR1、NFR3、AC3.2.2、要点 5・16
  - id: BR3.3
    statement: 招待は、state が PENDING で、時計の今が有効期限より前のときだけ有効とする（時刻ちょうどは無効）
    category: calculation
    applies_to: Invitation
    trigger: 一覧の expired・招待中の判定・リンクの確かめ・登録の完了
    logic: "今は注入した時計から得る。IF state が PENDING かつ 今 < expiresAt THEN 有効。IF state が PENDING かつ 今 ≧ expiresAt THEN 期限切れ（保存しない状態）。state がほかの値なら有効でない。この判定は DB を使わない純粋な関数とし、性質ベースのテストの対象にする"
    violation: —（判定の結果を各流れが使う）
    source: FR1.5、AC2.1.2、AC3.2.3、stories.md の合否の判定の仕方、要点 2・24
  - id: BR3.4
    statement: 招待の URL は、使えるベース URL の値だけから「ベース URL ＋ /register#token= ＋ トークン」で組み立て、要求の Host ヘッダーから組み立てない
    category: constraint
    applies_to: 招待メールの registrationUrl
    trigger: 招待の作成・送り直しの送信の前
    logic: "BR1.3 で末尾の / を除いたベース URL に /register#token= とトークンをつなぐ。要求の Host・スキーム・ポートは使わない（既存の ProblemBaseUrlResolver は設定が無いと要求から組み立てるため使わない）。招待を使える設定のとき（BR1.5 を通ったとき）だけ組み立てるため、registrationUrl は常に空でなく #token= を含む。空・#token= の欠けた URL で送信を頼まない"
    violation: —（構造で守る。使える値が無いときは BR1.5 で送信の前に拒否される）
    source: FR1.7、AC1.1.11、AC3.1.2、AC3.1.3、C6、C10、project.md の Forbidden、要点 6・7
  - id: BR3.5
    statement: トークンは URL のフラグメントにだけ載せ、確かめと完了の API は要求の本文でトークンを受け取る
    category: constraint
    applies_to: 招待の URL・登録の完了の API
    trigger: リンクの確かめ・登録の完了
    logic: "トークンは URL の #token= の後にだけ置き（画面の配信の要求にはサーバーへ送られない）、verify・complete はトークンを要求の本文（JSON の token）で受け取る。URL のパス・問い合わせではトークンを受け取らない"
    violation: —（構造で守る）
    source: AC3.2.15、C6、NFR1

  # ---- BR4: 招待メールの送信と送信の結果 ----
  - id: BR4.1
    statement: 招待メールは、招待（送り直し）の確定の後にトランザクションの外で1回だけ送り、要求はその結果を待って応答する
    category: policy
    applies_to: 招待の作成・送り直し
    trigger: BR2.6・BR6.1 の確定の後
    logic: "内部DB の接続を持たない状態で U1 の send（C1）を1回呼ぶ。自動の再試行はしない。送信の結果（BR4.3 の記録の後）を待ってから応答する。送信を待つ間に内部DB を使う別の要求は、この要求の送信の時間切れを待たずに応答できる"
    violation: —
    source: FR2.3、NFR5、ADR-009、C1、AC1.1.7、要点 8
  - id: BR4.2
    statement: 送信の依頼は、テンプレート invitation・招待の言語・招待のメールアドレスと、差し込み registrationUrl だけで行う
    category: policy
    applies_to: U1 への送信の依頼（MailRequest）
    trigger: BR4.1 の送信
    logic: "templateId は invitation、language は招待の language（招待した管理者の言語ではない）、to は招待の email、variables は registrationUrl（BR3.4）の1つだけとする。招待した管理者の氏名・メールアドレス・有効期限の日時は差し込まない（エスケープのテストの対象の利用者の値は registrationUrl だけ）"
    violation: —
    source: FR2.1、FR7.3、CR1.4、AC3.1.4、C10、U1 の BR2.2・BR3.3、要点 23
  - id: BR4.3
    statement: 送信の結果は別の短いトランザクションで、送ったトークンのハッシュを今も持つ行にだけ記録する
    category: constraint
    applies_to: Invitation.sendResult
    trigger: BR4.1 の送信の結果
    logic: "IF U1 の結果が Sent THEN SENT、IF Failed（種類によらず）THEN FAILED を、送信とは別の短いトランザクションで書く。IF 記録の時点でその招待の行が無い、または tokenHash が送ったトークンのハッシュと違う（同時にもう一度送り直された）THEN 書かない（新しい送信の結果を古い送信の結果で上書きしない）。状態（state）は記録の条件にしない。応答の sendResult はこの要求の送信の結果とする"
    violation: —
    source: FR2.4、ADR-009、AC2.2.3、要点 9
  - id: BR4.4
    statement: 送信の結果は、確定の時点で PENDING（送信中・結果不明）として保存し、API では PENDING を FAILED として返す
    category: policy
    applies_to: Invitation.sendResult・C5 の sendResult
    trigger: 招待の作成・送り直しの確定、一覧・招待・送り直しの応答
    logic: "確定の時点で sendResult を PENDING にする（BR2.6・BR6.1）。BR4.3 で SENT か FAILED に書き換える。IF 送信の途中でアプリが止まった THEN PENDING のまま残る。API の応答（C5）では PENDING を FAILED に置き換えて渡し、契約の値（SENT・FAILED）は変えない。記録の上では「止まった（PENDING）」と「失敗した（FAILED）」を区別できる"
    violation: —
    source: Q2、ADR-009 の悪い点、C5 の Open question、要点 8
  - id: BR4.5
    statement: 送信に失敗しても招待（送り直し）は取り消さず、招待は 201・送り直しは 200 で送信の結果 FAILED を返す
    category: policy
    applies_to: 招待の作成・送り直し
    trigger: U1 の結果が Failed（NOT_CONFIGURED・CONNECTION_FAILED・TIMEOUT・REJECTED・INVALID_INPUT・TEMPLATE_ERROR）
    logic: "招待と新しいトークン・有効期限は確定したままにし、sendResult を FAILED にして成功の状態コードで返す（エラーにしない）。応答・ログに宛先のメールアドレス・SMTP の応答の文面・資格情報・失敗の種類の細部を載せない（ログは BR9.4 の項目だけ）。送信の失敗は監査しない（BR8.1）。管理者は一覧で失敗を確かめ、送り直せる"
    violation: —
    source: "FR2.4、FR2.6、FR9.2、AC1.1.5、AC1.1.7、AC1.1.12、AC2.2.8、C5、契約の答え Q2: A"

  # ---- BR5: 一覧 ----
  - id: BR5.1
    statement: 一覧の対象は state が PENDING の招待（期限切れを含む）だけとする
    category: policy
    applies_to: GET /api/admin/invitations
    trigger: 一覧の要求
    logic: "state が PENDING の行だけを返す。COMPLETED・CANCELLED・REPLACED の行は返さない"
    violation: —
    source: FR3.1、要件の前提 A1、AC2.1.3、AC2.2.4、AC2.2.6、AC2.2.10、要点 11
  - id: BR5.2
    statement: 一覧は招待した日時の新しい順（同じ時刻は invitationId の大きい順）に 20 件ずつ返し、ページが最後を超えたら空の items と total を返す
    category: calculation
    applies_to: GET /api/admin/invitations の page
    trigger: 一覧の要求
    logic: "page は 1 以上の整数（既定 1）。IF page が 1 未満・整数でない THEN 拒否する。ELSE invitedAt の降順、同じなら invitationId の降順に並べ、(page−1)×20 番目から 20 件を items にし、size は 20、total は対象の件数とする。IF (page−1)×20 ≧ total THEN items を空にする"
    violation: 400 VALIDATION_FAILED（項目 page）
    source: FR3.1、AC2.1.5、C5、要点 11
  - id: BR5.3
    statement: 一覧の各行は、送信の結果を SENT・FAILED で、期限切れかを時計で、招待した管理者を氏名（無ければメールアドレス）で示す
    category: calculation
    applies_to: InvitationSummary
    trigger: 一覧・招待・送り直しの応答
    logic: "sendResult は BR4.4 のとおり（PENDING は FAILED）。expired は BR3.3 で期限切れなら true。invitedBy は UserAccount の findDisplayName（C2）で得た氏名とし、IF 得られない THEN その管理者の利用者の要約（U2 の BR5.5）のメールアドレスとする。IF 利用者の行そのものが無い THEN 空の文字列とする（今は利用者を消す操作が無いため起きない）。日時は ISO 8601 の UTC で渡す"
    violation: —
    source: "FR1.6、FR3.1、AC2.1.1、AC2.1.2、AC2.1.8、C2、C5、user-stories-questions.md の M9: A、要点 11"
  - id: BR5.4
    statement: 一覧・招待・送り直しの応答に、招待のトークン・トークンのハッシュ・招待の URL を含めず、招待を使える設定かと理由を付ける
    category: constraint
    applies_to: InvitationSummary・InvitationPage
    trigger: 一覧・招待・送り直しの応答
    logic: "応答の項目は C5 の Invitation・InvitationPage の項目だけとし、token・tokenHash・registrationUrl を持たない。一覧には BR1.4 の invitationEnabled と unavailableReasons を付ける"
    violation: —
    source: NFR1、AC2.1.7、AC1.1.6、AC2.2.9、C5、要点 11

  # ---- BR6: 送り直し・取り消し ----
  - id: BR6.1
    statement: 送り直しは、state が PENDING の招待（期限切れを含む）のトークン・有効期限・送信の結果だけを新しい値に置き換え、招待した日時と管理者は変えない
    category: policy
    applies_to: POST /api/admin/invitations/{invitationId}/resend
    trigger: 送り直しの要求（BR1.5 を通った後）
    logic: "短いトランザクションで対象の行を排他して読み、IF state が PENDING THEN BR3.1 で新しいトークンを作り、tokenHash を新しいハッシュ、expiresAt を時計の今 ＋ 有効期限の長さ、sendResult を PENDING に置き換えて確定する（前のトークンはこの確定の時点で見つからなくなる）。invitedAt・invitedByUserId・email・language・state は変えない。確定の後に INVITATION_RESENT を知らせ（BR8.1）、BR4.1 の送信へ進み、200 で招待の要約を返す"
    violation: 対象が PENDING でなければ BR6.3
    source: FR3.2、AC2.2.1、AC2.2.2、AC2.2.3、AC2.2.8、AC2.2.12、要点 3・9
  - id: BR6.2
    statement: 取り消しは、招待を使える設定でなくても、state が PENDING の招待（期限切れを含む）を取り消し済みにする
    category: policy
    applies_to: POST /api/admin/invitations/{invitationId}/cancel
    trigger: 取り消しの要求
    logic: "BR1.5 の設定の確かめをしない。短いトランザクションで対象の行を排他して読み、IF state が PENDING THEN state を CANCELLED、endedAt を時計の今にして確定する。tokenHash は残す（取り消し済みのリンクの理由を分けるため）。確定の後に INVITATION_CANCELLED を知らせ（BR8.1）、204 を返す。メールは送らない"
    violation: 対象が PENDING でなければ BR6.3
    source: FR3.3、AC2.2.4、AC2.2.9、AC2.2.10、要点 10
  - id: BR6.3
    statement: 送り直し・取り消しの対象が無い・PENDING でないときは、理由によらず同じ 404 で拒否する
    category: validation
    applies_to: 送り直し・取り消し
    trigger: 送り直し・取り消しの要求
    logic: "IF invitationId の行が無い（定期の削除で消えた場合を含む）、または state が COMPLETED・CANCELLED・REPLACED THEN 内部DB を変えずに拒否する。状態コード・code・説明文は3つ（取り消し済み・完了済み・存在しない）で同じ"
    violation: 404 INVITATION_NOT_FOUND。監査しない
    source: AC2.2.7、C5、要点 9・10
  - id: BR6.4
    statement: 同じ招待への送り直し・取り消し・登録の完了が同時に来たときは、行の排他で1つずつ行い、取り消した招待で利用者が作られることを起こさない
    category: constraint
    applies_to: Invitation の行
    trigger: 同時の操作
    logic: "送り直し・取り消し・登録の完了は、どれも同じ招待の行を排他して読み、読んだ時点の state で判定する。IF 登録の完了が先に確定 THEN 取り消し・送り直しは BR6.3 の 404。IF 取り消しが先に確定 THEN 登録の完了は BR7.5 の 404 で利用者は作られない。IF 送り直しが先に確定 THEN 前のトークンでの登録の完了は見つからない（BR3.2）。排他の作りの細部は NFR 設計で決める"
    violation: 後に来た操作が BR6.3 または BR7.5 で拒否される
    source: AC2.2.13、AC3.2.7、FR4.6、要点 15

  # ---- BR7: リンクの確かめと登録の完了 ----
  - id: BR7.1
    statement: リンクの確かめは、有効な招待があり、そのメールアドレスの利用者がいないときだけ、招待のメールアドレスと言語を返す。招待を消費せず、監査しない
    category: validation
    applies_to: POST /api/registration/verify
    trigger: リンクの確かめの要求（ログインなし）
    logic: "BR3.2 で招待を探し、IF 見つからない、または BR3.3 で有効でない、または UserAccount の existsByEmail（C2）がその email で真 THEN BR7.5 で拒否する。ELSE 200 で email と language だけを返す。内部DB を変えない。成功も失敗も監査しない"
    violation: 404 REGISTRATION_LINK_INVALID（BR7.5）。監査しない
    source: FR4.1、NFR3、AC3.2.2、AC3.2.3、C6、stories.md の CR3、要点 14
  - id: BR7.2
    statement: 登録の完了の入力は、内部DB を使う前にすべて検証し、誤りがあれば招待を消費せずに項目ごとの誤りで拒否する
    category: validation
    applies_to: POST /api/registration/complete の入力
    trigger: 登録の完了の要求（ログインなし）
    logic: "氏名は U2 の DisplayName の関数（U2 の BR1.6、前後の空白を除いて 1〜254 コードポイント、Cc・Cf を拒否、空白だけは空として拒否）、言語・テーマ・文字の大きさは U2 の BR2.1 の完全な一致、パスワードは既存の PasswordPolicy の作成時の規則（12 コードポイント以上、UTF-8 で 72 バイト以内）、passwordConfirmation は password との完全な一致をサーバーでも確かめる。誤りのある項目をすべて集め、IF 1つ以上 THEN トークンを引かずに拒否する。トークンはこの検証の項目にしない（BR3.2 で扱う）"
    violation: 400 VALIDATION_FAILED（項目ごと）。招待は消費されず、利用者は作られず、監査しない。入れたパスワードの値を応答・ログに出さない
    source: FR4.2、FR4.4、AC3.2.4、AC3.2.9、AC3.2.10、U2 の BR1.6・BR2.1・BR5.1、stories.md の後の段に回す点、要点 15
  - id: BR7.3
    statement: 登録の完了は、1つのトランザクションで招待の行を排他して確かめ、利用者（管理者フラグなし）を作り、招待を完了済みにする
    category: policy
    applies_to: 登録の完了
    trigger: BR7.2 を通った登録の完了の要求
    logic: "1つのトランザクションで BR3.2 のとおり招待の行を排他して読み、IF 見つからない・有効でない（BR3.3）THEN BR7.5 で拒否する。ELSE UserAccount の createUser（C2）を同じトランザクションで、email は招待の email、displayName・password・language・theme・fontSize は入力の値、admin は false で呼ぶ（既存の UserCreatedEvent でロックの状態が用意される、U2 の BR5.3）。IF Created THEN 招待の state を COMPLETED、endedAt を時計の今、completedUserId を作った利用者にして確定し、確定の後に REGISTRATION_COMPLETED を知らせ（BR8.2）、204 を返す。自動ではログインしない（トークンを発行しない）"
    violation: 拒否は BR7.4・BR7.5
    source: FR4.5、FR4.7、AC3.2.5、AC3.2.6、AC3.2.12、AC3.2.13、C2、U2 の BR5.3、要点 15
  - id: BR7.4
    statement: 完了の時点で同じメールアドレスの利用者がいれば、巻き戻して同じ応答で拒否し、招待は使用済みにしない
    category: constraint
    applies_to: 登録の完了
    trigger: createUser の結果が EmailAlreadyUsed
    logic: "トランザクションを巻き戻し（利用者は増えず、招待は PENDING のまま）、BR7.5 の応答で拒否する。監査の理由は EMAIL_ALREADY_REGISTERED（BR7.6・BR8.3）"
    violation: 404 REGISTRATION_LINK_INVALID
    source: FR4.6、AC3.2.11、U2 の BR5.2、Q4、要点 15
  - id: BR7.5
    statement: リンクの確かめ・登録の完了の拒否は、理由によらず同じ応答にする
    category: constraint
    applies_to: verify・complete の拒否
    trigger: BR3.2・BR3.3・BR7.1・BR7.3・BR7.4・BR6.4 の拒否
    logic: "期限切れ・使用済み・取り消し済み・置き換え済み・存在しない・改ざん・形の誤り・同時の完了・同じメールアドレスの利用者がいる、のどれでも、状態コード 404・code REGISTRATION_LINK_INVALID・説明文（要求の Accept-Language の言語）と、応答の本文の項目の並びを同じにする（余分な項目を足さない）。応答から利用者・招待の有無を推測できない"
    violation: 404 REGISTRATION_LINK_INVALID
    source: FR4.1、NFR3、AC3.2.2、AC3.2.3、AC3.2.6、AC3.2.7、AC3.2.11、AC2.2.1、AC2.2.4、C6、要点 14・15
  - id: BR7.6
    statement: 拒否の理由は、トークンと招待の状態から決めて監査の失敗の理由にだけ使う
    category: calculation
    applies_to: LinkRejection
    trigger: 登録の完了の拒否（BR7.5）
    logic: "IF トークンの形が合わない・ハッシュが無い（送り直しで古くなった・改ざん・定期の削除で消えたを含む）THEN INVITATION_NOT_FOUND。IF state が COMPLETED THEN INVITATION_ALREADY_USED。IF state が CANCELLED THEN INVITATION_CANCELLED。IF state が REPLACED、または PENDING で期限切れ THEN INVITATION_EXPIRED。IF 招待は有効で createUser が EmailAlreadyUsed THEN EMAIL_ALREADY_REGISTERED。理由は応答に出さない"
    violation: —
    source: C8、Q4、Q1、要点 16
  - id: BR7.7
    statement: 招待中の人は利用者の表にいないため、ログイン・トークンの更新・アクセストークンの認証のどれでも拒否される
    category: authorization
    applies_to: 既存のログイン・更新・アクセストークンの認証
    trigger: 招待中の人のメールアドレス・招待の ID を使った認証の試み
    logic: "招待は利用者を作らない（ADR-001）ため、既存の3つの経路に手を入れずに拒否される。IF 招待中の人のメールアドレスでログインする THEN 存在しない利用者と同じ 401。IF どの利用者の ID とも重ならない招待の ID、または招待のメールアドレスを主体にした正しい鍵のアクセストークンで API を呼ぶ THEN 401。招待中の人に発行されるリフレッシュトークンの経路は無く、招待中の人を主体にしたリフレッシュトークンでの更新も拒否される。招待の ID を主体にしたトークンを発行する経路は作らない"
    violation: 401（既存の認証の拒否）
    source: FR1.3、NFR3、AC3.2.8、AC3.2.14、ADR-001、要点 17

  # ---- BR8: 監査 ----
  - id: BR8.1
    statement: 招待・送り直し・取り消しは、それぞれの確定の後に、操作した管理者と対象の招待と結果 SUCCESS で1件ずつ記録し、拒否と送信の失敗は記録しない
    category: policy
    applies_to: InvitationEvent（INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED）
    trigger: BR2.6・BR6.1・BR6.2 の確定
    logic: "eventType はそれぞれ INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED、actorUserId は操作した管理者、targetInvitationId は対象の招待、result は SUCCESS、failureReason・targetUserId・enteredEmail は空。送り直しで送信だけが失敗しても SUCCESS のままで、送信の失敗の出来事は出さない。400・404・409・503 の拒否では出さない（契約 C8 の結果が SUCCESS だけのため）。置き換え（BR2.4）は出来事を持たない"
    violation: —
    source: FR9.1、FR9.2、C8、stories.md の CR3、要点 18
  - id: BR8.2
    statement: 登録の完了は、確定の後に、操作した人を空、対象の招待と作った利用者で記録する
    category: policy
    applies_to: InvitationEvent（REGISTRATION_COMPLETED）
    trigger: BR7.3 の確定
    logic: "eventType REGISTRATION_COMPLETED、actorUserId は空（未ログインの操作）、targetInvitationId は完了した招待、targetUserId は作った利用者、result は SUCCESS。巻き戻ったときは記録しない"
    violation: —
    source: FR9.1、C8、stories.md の CR3、要点 19
  - id: BR8.3
    statement: 登録の完了の要求のリンクの拒否だけを REGISTRATION_FAILED で記録し、リンクの確かめの失敗と入力の誤りは記録しない
    category: policy
    applies_to: InvitationEvent（REGISTRATION_FAILED）
    trigger: 登録の完了の BR7.5 の拒否
    logic: "eventType REGISTRATION_FAILED、actorUserId は空、targetInvitationId は招待が見つかったときだけ（見つからない・改ざん・形の誤りでは空）、result は FAILURE、failureReason は BR7.6 の理由。トランザクションが巻き戻った後（または何も書かなかった後）に要求の中で記録する（U2 の BR7.3 と同じ扱い）。verify の失敗（BR7.1）と入力の誤り（BR7.2）では記録しない"
    violation: —
    source: FR9.1、C8、Q4、stories.md の CR3、U2 の BR7.3、要点 19
  - id: BR8.4
    statement: 監査の出来事の種類5つと失敗の理由5つを既存の列挙に足し、名前は既存の列の長さ 32 に収まる
    category: constraint
    applies_to: AuditEventType・AuditFailureReason
    trigger: 値の追加
    logic: "種類は INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED・REGISTRATION_COMPLETED・REGISTRATION_FAILED、失敗の理由は INVITATION_EXPIRED・INVITATION_ALREADY_USED・INVITATION_CANCELLED・INVITATION_NOT_FOUND・EMAIL_ALREADY_REGISTERED（Q4 A で契約 C8 に足す）。最長は 22 文字と 24 文字で、列の長さを変えない。列は U2 が足した targetUserId・targetInvitationId と既存の result・failureReason・actorUserId を使い、列を足さない"
    violation: —
    source: C8、Q4、U2 の BR7.1・BR7.5
  - id: BR8.5
    statement: 監査の書き込みは既存の AuditLog の決まりに従い、失敗しても元の操作の応答を変えない
    category: policy
    applies_to: InvitationEvent
    trigger: 出来事を出したとき
    logic: "記録は別のトランザクションで追記し、失敗はアプリのログに ERROR で残し、応答は変えない（既存の決まり）。新しい出来事ごとに書き込みの失敗の場合を1件ずつテストで確かめる"
    violation: —（監査の書き込みの失敗は応答に影響しない）
    source: stories.md の CR3、既存の AuditEventListener、要点 19
  - id: BR8.6
    statement: 監査の出来事と記録に、招待のトークン・トークンのハッシュ・招待の URL・パスワード・メールアドレスを入れない
    category: constraint
    applies_to: InvitationEvent・AuditEvent
    trigger: 記録
    logic: "出来事の項目にも監査の列（enteredEmail を含む）にも、上の値を持たせない。対象は targetInvitationId と targetUserId で示す"
    violation: —
    source: FR9.1、NFR1、project.md の Forbidden、C8、要点 19

  # ---- BR9: 認可・エラー応答・秘密 ----
  - id: BR9.1
    statement: 招待の管理の API は管理者だけが呼べる
    category: authorization
    applies_to: /api/admin/invitations の下のすべて
    trigger: 要求
    logic: "既存の AccessControl の決まりにそのまま乗せる。IF 未認証 THEN 401 AUTHENTICATION_REQUIRED。IF 管理者でない THEN 403 ACCESS_DENIED。管理者なら処理する。画面で隠すことをサーバーの判定の代わりにしない。登録を完了した直後の利用者は管理者でないため 403"
    violation: 401・403（既存の扱い）
    source: FR10.1、NFR4、CR4、AC3.2.12、要点 20
  - id: BR9.2
    statement: リンクの確かめと登録の完了の2つの POST だけを、Invitation の差し込み口で認証なしに呼べるようにする
    category: authorization
    applies_to: POST /api/registration/verify・POST /api/registration/complete
    trigger: アプリの起動（決まりの登録）
    logic: "Invitation の差し込み口（SecurityRuleContributor）で、この2つの道の POST だけを認証なしにする。/api/registration/ のほかの道・ほかのメソッドは /api/ の既定（ログイン必須）のまま。/api/auth/ の下には置かない。差し込み口の順番の値は既存（110・210）と U8 の値に重ならないものをコード生成で決める"
    violation: 重なりは既存の SecurityExtensionValidator で起動が止まる
    source: FR10.3、NFR4、ADR-011、CR4、C6、要点 20
  - id: BR9.3
    statement: エラーの code は Invitation の一覧に1つの code に1つの状態コードで置き、説明文は要求の言語で返し、内部の例外のメッセージを載せない
    category: policy
    applies_to: INVITATION_EMAIL_REGISTERED・INVITATION_ALREADY_PENDING・INVITATION_NOT_CONFIGURED・INVITATION_NOT_FOUND・REGISTRATION_LINK_INVALID
    trigger: 拒否の応答
    logic: "code と状態コード（409・409・503・404・404）を機能の一覧（InvitationProblemTypeCatalog）に固定し、ja・en の説明文を持つ。説明文は要求の Accept-Language で選ぶ（ログインの前の画面は画面の言語を送る、CR1.2）。INVITATION_ALREADY_PENDING に invitationId・page、INVITATION_NOT_CONFIGURED に unavailableReasons を足し、ほかの項目は既存の Problem Details の形のまま。部品の間の想定内の失敗は結果の型で返し、API の層で Problem Details に変える。メールの部品の例外・SMTP の応答・内部の例外のメッセージを載せない"
    violation: —
    source: C5、C6、契約の共通の決まり、CR1.2、NFR2、team.md の Code Style、要点 21
  - id: BR9.4
    statement: アプリのログ・トレースの属性・エラー応答に、招待のトークン・招待の URL・メールアドレス・パスワードを出さない
    category: constraint
    applies_to: U3 のすべての流れ
    trigger: ログ・トレース・応答を出すとき
    logic: "ログに出すのは invitationId・言語・送信の結果の種類（SENT・FAILED と U1 の失敗の種類）・状態までとし、キーと値で渡す。トークン・トークンのハッシュ・URL・メールアドレス（招待先・招待した管理者）・パスワード・SMTP の応答を、ログ・トレースの属性・エラー応答に入れない。一覧・招待・送り直しの正常の応答のメールアドレス（管理者だけが見る C5 の email・invitedBy）は除く。リンクの確かめ・登録の完了の要求の道にトークンが載らないことは BR3.5 で守る"
    violation: —
    source: NFR1、NFR2、CR5、AC1.1.5、AC1.1.12、AC2.1.7、AC3.2.15、project.md の Forbidden、要点 22

  # ---- BR10: 招待メールのテンプレート ----
  - id: BR10.1
    statement: U1 のテンプレートの一覧に invitation（差し込みは registrationUrl だけ）を足し、ja・en のテンプレートを置く
    category: policy
    applies_to: 招待メールのテンプレート
    trigger: テンプレートを足すとき
    logic: "U1 の一覧に templateId invitation と差し込みの名前 registrationUrl を足し、mail/templates/invitation_ja.html と invitation_en.html を置く。U1 の決まり（エスケープされる差し込みだけ・属性の値は二重引用符・Mustache のコメントのライセンスヘッダー・title が件名・html の lang が言語）に従い、U1 のテンプレートのテストの対象に自動で入る"
    violation: U1 の起動時の検査・テストの失敗（U1 の BR2.3・BR2.6）
    source: FR2.1、C10、U1 の BR2.1〜BR2.6・BR4.2・BR4.3、要点 23
  - id: BR10.2
    statement: 招待メールの本文は、招待された旨・登録を終えるとログインできる旨・心当たりが無ければ何もしなくてよい旨と、リンクが 24 時間有効である旨を、招待の言語で載せ、有効期限の日時と招待した管理者の氏名は載せない
    category: constraint
    applies_to: invitation_ja.html・invitation_en.html の本文
    trigger: テンプレートを書くとき、テスト用の受け手で受けたメールの確かめ
    logic: "本文に次を載せる: MasterSmith から利用者として招待されたこと、リンクから登録を終えるとログインできること、心当たりが無ければ何もしなくてよいこと、ja は「このリンクは 24 時間有効です」・en は同じ意味の文。有効期限の日時と招待した管理者の氏名は載せない。テスト用の受け手で受けたメールの本文で確かめる"
    violation: テストの失敗（統合しない）
    source: "FR2.2、AC3.1.2、AC3.1.8、C10、user-stories-questions.md の M1: C・M5: A、mockups.md の E1、要点 23"
  - id: BR10.3
    statement: 招待メールには、行き先の分かる文言のボタンの形のリンクと、URL の文字の両方を載せる
    category: constraint
    applies_to: invitation_ja.html・invitation_en.html の本文
    trigger: テンプレートを書くとき、テスト用の受け手で受けたメールの確かめ
    logic: "registrationUrl を href（二重引用符で囲む）に差し込んだリンクの文言を、行き先の分かる文（例: 登録を完了する。「こちら」だけにしない）にする。リンクを押せない環境のため、同じ registrationUrl を文字として本文にも載せる。受けたメールの本文に、BR3.4 のとおりベース URL で始まり #token= を含むリンクがあることを確かめる"
    violation: テストの失敗（統合しない）
    source: AC3.1.2、AC3.1.3、AC3.1.9、C10、要点 7・23

  # ---- BR11: 保存期間と定期の削除・スキーマの変更 ----
  - id: BR11.1
    statement: 終わった招待は終わった日時から、期限切れの招待中は有効期限から、保存の日数を過ぎたら定期の処理で消す
    category: policy
    applies_to: Invitation
    trigger: 定期の削除（設定の時刻）
    logic: "境目を 時計の今 − 保存の日数（BR1.6、★既定 90 日）とする。IF state が COMPLETED・CANCELLED・REPLACED で endedAt ≦ 境目 THEN 消す。IF state が PENDING で expiresAt ≦ 境目 THEN 消す（一覧から消える）。判定の条件を消す操作の中で確かめ、同時に送り直された行（expiresAt が新しくなった行）は消さない。件数の上限ごとに別のトランザクションで消し、消した件数を INFO で出し、失敗は ERROR（スタックトレース付き）で出して次の回に任せる（既存のリフレッシュトークンの削除と同じ形）。ログにメールアドレス・ハッシュを出さない"
    violation: —（失敗は次の回に任せる）
    source: Q1、要件の未解決の点（招待の表の保存期間）、既存の RefreshTokenCleanupJob
  - id: BR11.2
    statement: 定期の削除で消えた招待のリンクと ID は、存在しないものと同じに扱い、監査の記録は残す
    category: policy
    applies_to: 消えた招待
    trigger: 消えた招待のリンク・ID の使用
    logic: "リンクの確かめ・登録の完了は BR7.5 の同じ応答で、監査の理由は INVITATION_NOT_FOUND（BR7.6）。送り直し・取り消しは BR6.3 の 404。監査の記録の targetInvitationId は参照の制約を持たないため、招待の行が消えても残る"
    violation: 404（REGISTRATION_LINK_INVALID または INVITATION_NOT_FOUND）
    source: Q1、U2 の BR9.2
  - id: BR11.3
    statement: 招待の表は U2 の V7 の後の番号で足し、スキーマの変更は前進のみで1つ前の版のアプリが動く形にする
    category: constraint
    applies_to: 内部DB のスキーマ
    trigger: スキーマの変更
    logic: "V8 以降の1つの前進の変更で招待の表を新しく足す（既存の表は変えない）。1つ前の版のアプリは招待の表を読まないため、そのまま起動できる"
    violation: —
    source: NFR10、team.md の Deployment、要点 24
```

## 決まりの一覧

| ID | 決まり | 分類 | 出典 |
|---|---|---|---|
| BR1.1 | メールアドレスは改行を拒否し正規化・254 文字・形式 | 検証 | FR1.2、AC1.1.3、AC1.1.10 |
| BR1.2 | 言語は ja・en の完全な一致 | 検証 | FR1.2、AC1.1.3 |
| BR1.3 | ベース URL は http・https の絶対 URL（問い合わせ・#・利用者情報なし）、不正は WARN で未設定扱い | 検証 | Q3、FR1.7、FR1.8 |
| BR1.4 | 使える設定はベース URL と SMTP の両方、足りない理由を並べる | 検証 | FR1.8、C5 |
| BR1.5 | 使えない設定では招待と送り直しだけ 503、ほかは動く | 制約 | FR1.8、AC1.1.6、AC2.2.9 |
| BR1.6 | 有効期限 24 時間・保存 90 日は設定で変えられる | 方針 | FR1.5、Q1 |
| BR2.1 | 登録済みは 409 INVITATION_EMAIL_REGISTERED | 制約 | FR1.4、AC1.1.4、AC1.1.10 |
| BR2.2 | 期限内の招待中は 409 INVITATION_ALREADY_PENDING（invitationId・page） | 制約 | FR1.4、AC1.1.4 |
| BR2.3 | page は一覧の位置 ÷ 20 の切り上げ | 計算 | AC1.1.4 |
| BR2.4 | 期限切れの招待中は同じトランザクションで REPLACED | 方針 | AC2.2.6 |
| BR2.5 | 同じメールアドレスの PENDING は1件まで、同時の招待は1件だけ | 制約 | AC1.1.13、ADR-010 |
| BR2.6 | 新しい招待の値（状態と送信の結果は PENDING） | 方針 | FR1.5、FR1.6、AC1.1.2 |
| BR2.7 | 取り消し・完了の後の再招待を許す | 方針 | AC2.2.5 |
| BR3.1 | トークンは乱数 32 バイト、SHA-256 のハッシュだけを保存 | 制約 | FR1.5、NFR1 |
| BR3.2 | 形の合わないトークンは引かずに見つからない | 検証 | NFR1、NFR3 |
| BR3.3 | 有効は PENDING かつ 今 < 有効期限 | 計算 | FR1.5、AC2.1.2、AC3.2.3 |
| BR3.4 | URL はベース URL ＋ /register#token= ＋ トークン、Host から作らない | 制約 | FR1.7、AC1.1.11、AC3.1.3 |
| BR3.5 | トークンはフラグメントだけ、API は本文で受け取る | 制約 | AC3.2.15、C6 |
| BR4.1 | 確定の後にトランザクションの外で1回送り、結果を待って応答 | 方針 | FR2.3、NFR5、ADR-009 |
| BR4.2 | 依頼は invitation・招待の言語・registrationUrl だけ | 方針 | FR2.1、CR1.4、AC3.1.4 |
| BR4.3 | 結果は別のトランザクションで、同じトークンの行にだけ記録 | 制約 | FR2.4、AC2.2.3 |
| BR4.4 | 確定の時点で PENDING、API では PENDING を FAILED | 方針 | Q2 |
| BR4.5 | 送信の失敗でも 201・200 と FAILED、宛先と SMTP の応答を出さない | 方針 | FR2.4、FR2.6、AC1.1.5、AC1.1.7、AC1.1.12、AC2.2.8 |
| BR5.1 | 一覧は PENDING（期限切れを含む）だけ | 方針 | FR3.1、AC2.1.3 |
| BR5.2 | 新しい順・20 件ずつ、最後を超えたら空 | 計算 | FR3.1、AC2.1.5 |
| BR5.3 | 送信の結果・期限切れ・招待した管理者（氏名、無ければメールアドレス） | 計算 | AC2.1.1、AC2.1.2、AC2.1.8 |
| BR5.4 | 応答にトークン・ハッシュ・URL を含めず、使える設定かを付ける | 制約 | NFR1、AC2.1.7 |
| BR6.1 | 送り直しはトークン・有効期限・送信の結果だけを置き換え | 方針 | FR3.2、AC2.2.1、AC2.2.2、AC2.2.8 |
| BR6.2 | 取り消しは設定なしでも PENDING を CANCELLED | 方針 | FR3.3、AC2.2.4、AC2.2.10 |
| BR6.3 | 対象が無い・PENDING でなければ同じ 404 | 検証 | AC2.2.7 |
| BR6.4 | 同じ招待の同時の操作は行の排他で1つずつ | 制約 | AC2.2.13、AC3.2.7 |
| BR7.1 | リンクの確かめは有効で利用者がいないときだけ email・language、消費せず監査なし | 検証 | FR4.1、AC3.2.2 |
| BR7.2 | 完了の入力は DB の前にすべて検証、誤りは消費せず 400 | 検証 | FR4.2、FR4.4、AC3.2.4、AC3.2.9、AC3.2.10 |
| BR7.3 | 1つのトランザクションで排他・利用者の作成・完了済み、204、自動ログインなし | 方針 | FR4.5、FR4.7、AC3.2.5 |
| BR7.4 | 完了の時点の同じメールアドレスの利用者は巻き戻して同じ拒否 | 制約 | FR4.6、AC3.2.11 |
| BR7.5 | 拒否は理由によらず同じ 404 REGISTRATION_LINK_INVALID | 制約 | FR4.1、NFR3、AC3.2.2 |
| BR7.6 | 拒否の理由（監査だけ）の決め方 | 計算 | C8、Q4 |
| BR7.7 | 招待中の人は認証の3経路で構造的に拒否 | 認可 | FR1.3、AC3.2.8、AC3.2.14 |
| BR8.1 | 招待・送り直し・取り消しは確定の後に SUCCESS、拒否と送信の失敗は記録なし | 方針 | FR9.1、FR9.2、C8 |
| BR8.2 | 登録の完了は actor 空・対象の招待と利用者 | 方針 | FR9.1、C8 |
| BR8.3 | 完了の要求のリンクの拒否だけ REGISTRATION_FAILED | 方針 | FR9.1、C8、Q4 |
| BR8.4 | 種類5つ・理由5つを足す、32 に収まる | 制約 | C8、Q4 |
| BR8.5 | 監査の書き込みの失敗で応答を変えない | 方針 | CR3 |
| BR8.6 | 監査にトークン・ハッシュ・URL・パスワード・メールアドレスを入れない | 制約 | FR9.1、NFR1 |
| BR9.1 | 招待の管理の API は管理者だけ（401・403） | 認可 | FR10.1、NFR4 |
| BR9.2 | verify・complete の POST だけを差し込み口で公開 | 認可 | FR10.3、ADR-011 |
| BR9.3 | code の一覧と状態コードの固定、説明文は要求の言語 | 方針 | C5、C6、CR1.2 |
| BR9.4 | ログ・トレース・エラー応答にトークン・URL・メールアドレス・パスワードを出さない | 制約 | NFR1、NFR2、CR5 |
| BR10.1 | U1 の一覧に invitation を足し ja・en を置く | 方針 | FR2.1、C10 |
| BR10.2 | 本文の文面（招待・ログイン・心当たり・24 時間有効）、日時と管理者の氏名なし | 制約 | AC3.1.2、AC3.1.8 |
| BR10.3 | 行き先の分かるリンクと URL の文字 | 制約 | AC3.1.3、AC3.1.9 |
| BR11.1 | 保存の日数を過ぎた終わった招待と期限切れの招待中を定期に消す | 方針 | Q1 |
| BR11.2 | 消えた招待は存在しないと同じ、監査は残る | 方針 | Q1 |
| BR11.3 | V8 以降の前進のみ・後方互換 | 制約 | NFR10 |
