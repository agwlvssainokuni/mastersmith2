# Rules — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

出典の略号: FR・NFR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、AC・CR は `aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`、C2・C3・C4・C8 は `aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`、ADR は `aidlc/spaces/default/intents/260925-user-management/inception/domain-design/decisions.md`、Q1〜Q4 はこの段の `functional-design-questions.md`。

```yaml
rules:
  # --- BR1 氏名の決まり（DisplayName）
  - id: BR1.1
    statement: 氏名は、前後の空白を除いてから判定し、除いた値を保存する
    category: validation
    applies_to: 氏名（プリファレンスの保存・利用者の作成）
    trigger: 氏名を受け取ったとき
    logic: "前後から、Unicode の White_Space の性質を持つ文字（半角の空白 U+0020、全角の空白 U+3000、タブ・改行、U+00A0 などを含む）を除く。IF 値が無い（項目が無い・null）THEN 空として BR1.2 で拒否する。除いた後の値で BR1.2〜BR1.4 を判定し、通れば除いた後の値を保存する。内側の空白は除かない・まとめない"
    violation: —（判定の前処理）
    source: FR5.2、Q3 A
  - id: BR1.2
    statement: 前後の空白を除いた後の氏名が空なら拒否する
    category: validation
    applies_to: 氏名
    trigger: BR1.1 の後
    logic: "IF 除いた後の長さが 0（空の文字列、または空白の文字だけの値）THEN 入力の誤りとして拒否する"
    violation: 400 VALIDATION_FAILED（項目 displayName）。利用者の作成では BR5.1
    source: FR5.2、Q3 A、AC4.1.7、AC3.2.9
  - id: BR1.3
    statement: 氏名の長さは、前後の空白を除いた後のコードポイントの数で 254 までとする
    category: validation
    applies_to: 氏名
    trigger: BR1.1 の後
    logic: "IF 除いた後のコードポイントの数が 254 を超える THEN 拒否する。254 ちょうどは通る（UTF-16 の単位ではなくコードポイントで数えるため、絵文字 254 文字も通る）。上限はメールアドレスの上限と同じで、初期値のメールアドレスがいつでもそのまま収まる"
    violation: 400 VALIDATION_FAILED（項目 displayName）。利用者の作成では BR5.1
    source: FR5.2、Q1 A、AC4.1.7、AC3.2.9
  - id: BR1.4
    statement: 制御文字（Cc）と見えない書式の文字（Cf）を含む氏名を拒否する
    category: validation
    applies_to: 氏名
    trigger: BR1.1 の後
    logic: "IF 除いた後の値に、Unicode の一般分類が Cc（改行・タブ・NUL など）または Cf（ゼロ幅の空白 U+200B、ゼロ幅の接合子 U+200D、表示の向きの上書き U+202E、U+FEFF など）の文字が1つでもある THEN 拒否する。ほかの文字（漢字・かな・英数字・記号・絵文字・内側の空白など）は許す。前後の改行・タブは BR1.1 で除かれるため、拒否されるのは内側にある場合"
    violation: 400 VALIDATION_FAILED（項目 displayName）。利用者の作成では BR5.1
    source: Q2 B
  - id: BR1.5
    statement: 氏名は Unicode の正規化をせず、除いた後の値をそのまま保存する
    category: policy
    applies_to: 氏名
    trigger: 保存
    logic: "NFC などの正規化・大文字小文字の変換・内側の空白の置き換えをしない。同じ見た目の異なる文字の並びは別の値として扱う（氏名は一意でないため、照合には使わない）"
    violation: —
    source: 設計の要点 4（functional-design-questions.md）
  - id: BR1.6
    statement: 氏名の決まりは1つの純粋な関数にまとめ、プリファレンスの保存と利用者の作成で同じものを使う
    category: constraint
    applies_to: DisplayName
    trigger: 氏名の検証
    logic: "BR1.1〜BR1.5 を DB を使わない1つの関数（DisplayName）にし、C4 の保存・C2 の作成・U3 の登録の完了の入力の検証が同じ関数を呼ぶ。性質ベースのテストの対象にする（team.md）"
    violation: —
    source: 設計の要点 4、team.md の Testing Posture

  # --- BR2 表示の設定の値
  - id: BR2.1
    statement: 言語・テーマ・文字の大きさは、決めた値のどれかに一致するときだけ受け付ける
    category: validation
    applies_to: language・theme・fontSize（プリファレンスの保存・利用者の作成）
    trigger: 値を受け取ったとき
    logic: "IF language が ja・en のどれでもない、theme が light・dark・system のどれでもない、fontSize が sm・md・lg のどれでもない、または値が無い（項目が無い・null・空）THEN 拒否する。一致は小文字の文字列の完全な一致とし、大文字（例: EN）や前後の空白を含む値はそろえずに拒否する"
    violation: 400 VALIDATION_FAILED（該当の項目）。利用者の作成では BR5.1
    source: FR5.2、AC4.1.7
  - id: BR2.2
    statement: 氏名と表示の設定の初期値は、氏名がメールアドレス・言語 ja・テーマ system・文字の大きさ md とする
    category: policy
    applies_to: スキーマの変更の前からいる利用者（BR9.1）、初期管理者の自動作成（BR5.4）
    trigger: スキーマの変更の適用、初期管理者の作成
    logic: "既存の利用者と初期管理者には、氏名に保存済み（またはそろえた）メールアドレス、language ja、theme system、fontSize md を入れる。招待からの作成は招待された人が選んだ値（U3 が渡す）を使い、この初期値を使わない"
    violation: —
    source: 要件の前提 A3、NFR10、AC4.1.10

  # --- BR3 プリファレンスの読み書き（C4 GET・PUT /api/me/preferences）
  - id: BR3.1
    statement: プリファレンスの取得は、要求ごとに内部DB から本人の4つを読んで返す
    category: policy
    applies_to: GET /api/me/preferences
    trigger: 取得の要求
    logic: "認証された本人の利用者 ID（BR8.1）で利用者を読み、displayName・language・theme・fontSize を保存された値のまま返す（theme が system なら system を返し、ライト・ダークに解決しない）。応答に email・admin・パスワードのハッシュを含めない。IF 利用者が読めない（認証の後に消えた）THEN 401 AUTHENTICATION_REQUIRED"
    violation: 401
    source: FR5.1、C4、AC4.1.1、AC4.1.10
  - id: BR3.2
    statement: プリファレンスの保存は4つをまとめて検証し、1つでも誤りがあれば何も変えずに拒否する
    category: validation
    applies_to: PUT /api/me/preferences
    trigger: 保存の要求
    logic: "BR1.1〜BR1.4 と BR2.1 を4つすべてに当て、誤りのある項目をすべて集める。IF 誤りが1つ以上 THEN 誤りの項目の一覧を付けて拒否し、内部DB の4つの値はどれも変わらない（一部だけを保存しない）。ELSE BR3.3 へ進む"
    violation: 400 VALIDATION_FAILED（項目ごとの誤り）。内部DB は変わらない
    source: FR5.1、FR5.2、C4、AC4.1.7
  - id: BR3.3
    statement: 検証を通った4つを1つのトランザクションで置き換え、保存した値を返す
    category: policy
    applies_to: PUT /api/me/preferences
    trigger: BR3.2 を通ったとき
    logic: "本人の利用者の displayName（前後の空白を除いた値）・language・theme・fontSize を1つのトランザクションで置き換え、確定の後に保存した4つを 200 で返す。前と同じ値でも成功として扱う。以降の取得（BR3.1）とログイン・更新の応答（BR6.1）は保存した値を返す"
    violation: —
    source: FR5.1、FR5.3、FR5.4、C4、AC4.1.2、AC4.1.8
  - id: BR3.4
    statement: プリファレンスの保存は4つの列だけを書き換え、同じ列の同時の書き込みは後に確定したものが勝つ
    category: constraint
    applies_to: 利用者の行の書き換え
    trigger: BR3.3・BR4.3
    logic: "プリファレンスの保存は displayName・language・theme・fontSize だけを、パスワードの変更は passwordHash だけを書き換え、email・admin・createdAt と相手の列を、先に読んだ古い値で上書きしない（同時にパスワードの変更と保存が起きても、どちらの結果も残る）。版による排他は持たず、同じ4つの同時の保存は後に確定した方が残る（本人だけが変える値のため）"
    violation: —
    source: 設計の要点 6、ADR-003・ADR-004
  - id: BR3.5
    statement: 氏名・言語・テーマ・文字の大きさの変更は監査ログに残さない
    category: policy
    applies_to: PUT /api/me/preferences
    trigger: 保存の成功・拒否
    logic: "保存の成功でも拒否でも、監査の出来事を出さず、監査ログの件数を増やさない"
    violation: —
    source: FR9.2、CR3、AC4.1.13

  # --- BR4 パスワードの変更（C4 POST /api/me/password、ADR-004）
  - id: BR4.1
    statement: パスワードの変更は、まず入力を検証し、誤りがあれば今のパスワードを照合せずに拒否する
    category: validation
    applies_to: POST /api/me/password
    trigger: 変更の要求
    logic: "次をすべて確かめ、誤りのある項目をすべて集める。(a) currentPassword が有る（空でない）。(b) newPassword が既存の PasswordPolicy の作成時の規則（12 コードポイント以上かつ UTF-8 で 72 バイト以内）に合う。(c) newPasswordConfirmation が newPassword と文字の並びとして完全に一致する（正規化・前後の空白の除去をしない）。IF 誤りが1つ以上 THEN 拒否し、今のパスワードの照合（BR4.2）をせず、パスワードは変わらず、監査の出来事も出さない"
    violation: 400 VALIDATION_FAILED（項目ごとの誤り）
    source: FR6.1、FR6.2、FR4.4、C4、AC5.1.3
  - id: BR4.2
    statement: 今のパスワードが誤っていれば、パスワードの誤りの code で拒否し、失敗を監査に残す
    category: authorization
    applies_to: POST /api/me/password
    trigger: BR4.1 を通ったとき
    logic: "本人の保存されたハッシュと currentPassword を照合する。IF currentPassword が UTF-8 で 72 バイトを超える THEN 照合の仕組みに渡さず不一致とする（既存のログインの照合と同じ扱い）。IF 不一致 THEN PASSWORD_CURRENT_MISMATCH（400）で拒否し、パスワードは変わらず、PasswordChangedEvent（FAILURE・CURRENT_PASSWORD_MISMATCH）を出す（BR7.2）。401 にせず、ログインの状態・トークンに触れない（BR4.5）"
    violation: 400 PASSWORD_CURRENT_MISMATCH。内部DB のパスワードは変わらない
    source: FR6.2、C4、ADR-004、AC5.1.2、AC5.1.6
  - id: BR4.3
    statement: 照合が一致すれば、新しいパスワードのハッシュを保存し、成功を監査に残す
    category: policy
    applies_to: POST /api/me/password
    trigger: BR4.2 で一致したとき
    logic: "newPassword を既存の作成と同じハッシュの仕組み（bcrypt）でハッシュにし、本人の passwordHash だけを1つのトランザクションで書き換える（BR3.4）。確定の後に PasswordChangedEvent（SUCCESS）が記録される（BR7.3）。応答は 204（本文なし）。以降のログインは新しいパスワードだけで通り、前のパスワードでは通らない"
    violation: —
    source: FR6.1、C4、AC5.1.1
  - id: BR4.4
    statement: 新しいパスワードが今のパスワードと同じでも、規則に合えば変更として扱う
    category: policy
    applies_to: POST /api/me/password
    trigger: BR4.2 で一致したとき
    logic: "新しいパスワードを今のハッシュと照合し直さない。同じ値でも BR4.3 のとおり新しいハッシュを保存し、成功の監査を残す"
    violation: —
    source: Q4 A、FR6.2
  - id: BR4.5
    statement: パスワードの変更は、発行済みのリフレッシュトークンとアクセストークンに触れない
    category: policy
    applies_to: POST /api/me/password
    trigger: 変更の成功・拒否
    logic: "成功しても拒否されても、本人のリフレッシュトークン（変更した端末・ほかの端末とも）を無効にせず、発行済みのアクセストークンも有効期限まで使える。応答でトークンやクッキーを出し直さない・消さない。今のパスワードの誤りをログインの失敗回数（アカウントのロック）に数えない。誤りが続いたときの制限はこの段では持たず、NFR 要件の段で決める"
    violation: —
    source: FR6.3、project.md の DECIDED、AC5.1.4、AC5.1.5、AC5.1.6

  # --- BR5 利用者の作成と読み取り（C2）
  - id: BR5.1
    statement: 利用者の作成は、受け取った値が決まりに合うことを前提とし、合わなければ作らずに想定外の誤りとする
    category: constraint
    applies_to: 利用者の作成（createUser）
    trigger: 作成の呼び出し
    logic: "氏名は BR1.1〜BR1.4、言語・テーマ・文字の大きさは BR2.1、パスワードは PasswordPolicy の作成時の規則で確かめる。呼び出し元（U3 の登録の完了、初期管理者の自動作成）は先に同じ関数（BR1.6）で入力を検証し、利用者に項目ごとの誤りを返す。IF 作成の時点で決まりに合わない値が来た THEN 作らず、想定外の誤り（例外）とする（呼び出し元の入力の検証の代わりにしない）"
    violation: 想定外の誤り（呼び出し元のトランザクションは巻き戻る）
    source: C2、FR4.4、FR4.5
  - id: BR5.2
    statement: 登録済みのメールアドレスでの作成は、結果の型の EmailAlreadyUsed で返し、利用者を作らない
    category: constraint
    applies_to: 利用者の作成
    trigger: 作成の呼び出し
    logic: "メールアドレスは EmailAddress.normalize でそろえてから判定する。IF そろえた値の利用者がすでにいる（同時の作成で一意の制約に当たった場合を含む）THEN EmailAlreadyUsed を返し、例外にしない。ELSE 作って Created（userId）を返す"
    violation: EmailAlreadyUsed（利用者は増えない）
    source: C2、契約の共通の決まり、AC3.2.11
  - id: BR5.3
    statement: 利用者の作成は呼び出し元のトランザクションに参加し、同じトランザクションで UserCreatedEvent を知らせる
    category: policy
    applies_to: 利用者の作成
    trigger: 作成の成功
    logic: "呼び出し元のトランザクションが有ればそれに参加し（U3 の招待の使用済みの記録と同じ確定）、無ければ新しく始める。email（そろえた値）・パスワードのハッシュ・admin・createdAt（時計の値）・displayName（前後の空白を除いた値）・language・theme・fontSize を保存し、同じトランザクションで既存の UserCreatedEvent を知らせる（Authentication がロックの状態の行を作る）。呼び出し元が巻き戻れば利用者も作られない"
    violation: —
    source: C2、FR4.5、AC3.2.5、AC3.2.13
  - id: BR5.4
    statement: 初期管理者の自動作成も同じ作成の操作を通し、初期値を入れる
    category: policy
    applies_to: 初期管理者の自動作成
    trigger: 起動時の初期管理者の作成
    logic: "既存の決まり（すでにいれば作らない、設定が無い・不正のときの扱い、パスワードをログに出さない）は変えない。作るときは admin true、氏名はそろえたメールアドレス、language ja、theme system、fontSize md（BR2.2）で BR5.3 の作成を呼ぶ"
    violation: —
    source: 要件の前提 A3、設計の要点 5
  - id: BR5.5
    statement: 利用者の読み取りの操作は、パスワードのハッシュを含まない値だけを返す
    category: constraint
    applies_to: existsByEmail・findDisplayName・findLanguage・利用者の要約
    trigger: 呼び出し
    logic: "existsByEmail はそろえたメールアドレスで有無を返す。findDisplayName・findLanguage は利用者 ID で氏名・言語を返し、いなければ空。利用者の要約（UserSummary）は userId・email・admin・displayName・language・theme・fontSize を持ち、ハッシュを含まない"
    violation: —
    source: C2、既存の ADR-001

  # --- BR6 ログインと更新の応答（C3）
  - id: BR6.1
    statement: ログインとトークンの更新の応答の利用者の情報に、氏名と表示の設定の4つを載せる
    category: policy
    applies_to: POST /api/auth/login・POST /api/auth/session/refresh の応答の user
    trigger: ログイン・更新の成功
    logic: "user に既存の email・admin に加えて displayName・language・theme・fontSize を、応答を作る時点の内部DB の値で載せる（theme は system のまま）。ログイン・更新・ログアウト・アクセストークンの認証の判定と、ほかの応答の項目は変えない"
    violation: —
    source: C3、FR5.4、AC4.1.2、AC4.1.8、AC4.1.9

  # --- BR7 監査（C8、ADR-008）
  - id: BR7.1
    statement: 監査の記録に対象の2列を足し、足す列の一覧の正を U2 が持つ
    category: constraint
    applies_to: AuditEvent
    trigger: スキーマの変更（BR9.2）
    logic: "足す列は targetUserId・targetInvitationId の2つだけで、どちらも空を許す。結果は既存の result（SUCCESS・FAILURE）、失敗の理由は既存の failureReason、操作した人は既存の actorUserId を使う。DSL の受け付けなかった理由の列 rejectionKind は今回の出来事で使わない。既存の出来事（認証・アクセスの拒否・DSL）は2列を空のまま記録する"
    violation: —
    source: C8、ADR-008、CR3
  - id: BR7.2
    statement: パスワードの変更の成功と今のパスワードの誤りを、種類 PASSWORD_CHANGED で1件ずつ記録する
    category: policy
    applies_to: PasswordChangedEvent → AuditEvent
    trigger: BR4.2 の不一致、BR4.3 の成功
    logic: "eventType PASSWORD_CHANGED、result は成功なら SUCCESS・不一致なら FAILURE、failureReason は FAILURE のとき CURRENT_PASSWORD_MISMATCH（SUCCESS では空）、actorUserId と targetUserId に本人、sourceIp・userAgent・traceId は既存の出来事と同じ取り方、occurredAt は時計の値。enteredEmail・requestPath・targetInvitationId・DSL の列は空。入力の誤り（BR4.1）とプリファレンスの保存（BR3.5）では記録しない。契約 C8 の PASSWORD_CHANGED の項目は actor_user_id・result・failure_reason だけを挙げるが、この単位は target_user_id（本人と同じ値）も記録する。契約の文書は書き換えず、差を functional-spec.md の7節に記録し、後の段で C8 に反映する（承認の場の Request Changes R-03、2026-09-27）"
    violation: —
    source: FR9.1、C8、CR3
  - id: BR7.3
    statement: パスワードの変更の監査は確定の後に記録し、書き込みの失敗で元の操作を失敗させない
    category: policy
    applies_to: PasswordChangedEvent
    trigger: 出来事を出したとき
    logic: "成功は変更のトランザクションの確定の後に記録する（巻き戻ったときは記録しない）。不一致はトランザクションで何も書かないため、要求の中でその場で記録する（既存の fallbackExecution と同じ扱い）。記録は既存の AuditLog の決まり（別のトランザクションで追記、失敗はアプリのログに ERROR で残し、応答は変えない）に従う"
    violation: —（監査の書き込みの失敗は応答に影響しない）
    source: ADR-008、既存の AuditEventListener、CR3
  - id: BR7.4
    statement: 監査の記録にパスワードの値・ハッシュ・トークンを入れない
    category: constraint
    applies_to: PasswordChangedEvent・AuditEvent
    trigger: 記録
    logic: "今のパスワード・新しいパスワード・確かめの値・そのハッシュ・アクセストークン・リフレッシュトークンを、出来事の項目にも監査の列にも持たせない"
    violation: —
    source: FR9.1、project.md の Forbidden、CR3
  - id: BR7.5
    statement: 足す出来事の種類と失敗の理由の名前は、既存の列の長さ 32 に収まる
    category: constraint
    applies_to: AuditEventType・AuditFailureReason
    trigger: 値の追加
    logic: "PASSWORD_CHANGED（16 文字）と CURRENT_PASSWORD_MISMATCH（25 文字）は event_type・failure_reason の 32 に収まるため、列の長さを変えない。U3 が足す値（最長 REGISTRATION_COMPLETED 22 文字・INVITATION_ALREADY_USED 23 文字）も収まる"
    violation: —
    source: C8 の未解決の点

  # --- BR8 認可・エラー応答・秘密
  - id: BR8.1
    statement: /api/me/ の下の API は、ログインした利用者なら誰でも本人の分だけを使える
    category: authorization
    applies_to: GET・PUT /api/me/preferences、POST /api/me/password
    trigger: すべての要求
    logic: "既存の /api/ の既定のログイン必須に乗る。IF アクセストークンが無い・無効 THEN 401 AUTHENTICATION_REQUIRED。ログインした利用者は管理者でなくても処理する。対象は認証された本人の利用者 ID だけで決め、URL・本文で利用者 ID やメールアドレスを受け取らない。/api/auth/ の下には置かない。管理者の権限を要しない API のため、管理者でないことによる 403 の場面は無い。要件 NFR4 の「401・403・200 を確かめる」は、この3本では 403 が当てはまらないと読み、サーバー側のテストで未認証の 401 と、管理者でないログインした利用者の 200（パスワードの変更は 204）を確かめる（CR4 の「プリファレンスの取得と保存・パスワードの変更: 未認証 401、ログインした利用者は成功」と同じ。承認の場の Request Changes R-01、2026-09-27）"
    violation: 401
    source: FR10.2、NFR4、CR4、契約の共通の決まり
  - id: BR8.2
    statement: 今のパスワードの誤りの code PASSWORD_CURRENT_MISMATCH を、状態コード 400 に固定して user の機能の一覧に置く
    category: constraint
    applies_to: エラーの code の一覧（UserProblemTypes）
    trigger: 起動
    logic: "新しい user の機能の code の一覧に PASSWORD_CURRENT_MISMATCH（400）を ja・en の説明つきで登録する。ほかは既存の VALIDATION_FAILED（400）・AUTHENTICATION_REQUIRED（401）を使う。1つの code に1つの状態コード（既存の重複の検査）"
    violation: 起動の失敗（既存の重複の検査）
    source: C4、契約のエラーの code の一覧
  - id: BR8.3
    statement: エラーの説明文の言語は、要求の Accept-Language で決める
    category: policy
    applies_to: U2 の API のエラー応答
    trigger: エラー応答を作るとき
    logic: "既存のとおり、説明文を要求の Accept-Language（ja・en、それ以外は既定の ja）で選ぶ。ログインした利用者の言語は画面（ApiClient）が Accept-Language に入れて送る（ADR-005）。サーバーで利用者の保存された言語から言語を決め直す仕組みは足さない"
    violation: —
    source: FR7.2、ADR-005、CR1.1
  - id: BR8.4
    statement: パスワードとメールアドレスを、アプリのログ・トレースの属性・エラー応答に含めない
    category: constraint
    applies_to: U2 の API と業務処理
    trigger: ログ・トレース・エラー応答を出すとき
    logic: "今・新しい・確かめのパスワードは既存の Password の型（文字列にすると伏せる）で扱い、ログ・トレースの属性・エラー応答・入力の誤りの項目ごとの説明に値を載せない。メールアドレスはアプリのログとエラー応答に入れない。入力の誤りの応答は項目の名前と理由だけを返し、入れた値を返さない"
    violation: —
    source: NFR2、project.md の Forbidden、CR5

  # --- BR9 スキーマの変更（NFR10）
  - id: BR9.1
    statement: 利用者の表に4つの列を足す変更は、既存の利用者に初期値を入れ、前進のみ・後方互換とする
    category: constraint
    applies_to: 内部DB のスキーマの変更 V7
    trigger: 起動時のスキーマの変更の適用
    logic: "V7 で displayName・language・theme・fontSize を足す。language・theme・fontSize は既定の値（ja・system・md）を持ち、1つ前の版のアプリの追記（4つを渡さない）でも入る。displayName は既存の行に保存済みのメールアドレスを入れてから必須にする。既存の行の値は BR2.2。適用済みの V1〜V6 は書き換えない。1つ前の版のアプリが利用者を作るのは初期管理者の自動作成（利用者がいれば動かない）だけのため、後方互換を保てる見込みで、細部は NFR 設計・基盤の設計で確かめる"
    violation: 起動の失敗（スキーマの変更の失敗は既存の Flyway の扱い）
    source: NFR10、要件の前提 A3、team.md の Deployment、AC4.1.10
  - id: BR9.2
    statement: 監査の表への対象の2列の追加は、同じ V7 で空を許す列として足す
    category: constraint
    applies_to: 内部DB のスキーマの変更 V7
    trigger: 起動時のスキーマの変更の適用
    logic: "audit_events に targetUserId・targetInvitationId（どちらも空を許す整数、参照の制約と索引は置かない）を足す。既存の行は空のまま。1つ前の版のアプリも今までどおり追記できる"
    violation: 起動の失敗
    source: C8、ADR-008、NFR10
```

## 決まりの一覧

| 群 | ID | 要点 |
|---|---|---|
| 氏名 | BR1.1〜BR1.6 | 前後の空白（Unicode の White_Space）を除いて判定・保存、除いた後が空なら拒否、254 コードポイントまで、Cc・Cf を拒否、正規化しない、1つの純粋な関数で保存と作成に共通 |
| 表示の設定の値 | BR2.1・BR2.2 | ja・en／light・dark・system／sm・md・lg の完全な一致だけ、初期値はメールアドレス・ja・system・md |
| プリファレンスの読み書き | BR3.1〜BR3.5 | 要求ごとに内部DB から読む、4つをまとめて検証し一部だけ保存しない、1つのトランザクションで置き換え、4列だけを書き換え後勝ち、監査しない |
| パスワードの変更 | BR4.1〜BR4.5 | 入力の検証が先（誤りは照合せず監査なし）、今のパスワードの誤りは 400 PASSWORD_CURRENT_MISMATCH と失敗の監査、一致なら新しいハッシュと成功の監査で 204、同じパスワードも許す、トークンに触れない |
| 利用者の作成と読み取り | BR5.1〜BR5.5 | 値は決まりに合う前提、登録済みは EmailAlreadyUsed、呼び出し元のトランザクションと UserCreatedEvent、初期管理者も同じ操作で初期値、ハッシュを出さない |
| 認証の応答 | BR6.1 | ログインと更新の user に displayName・language・theme・fontSize |
| 監査 | BR7.1〜BR7.5 | 対象の2列だけを足す、PASSWORD_CHANGED の成功と失敗、確定の後に記録し失敗で操作を止めない、パスワードを入れない、名前は 32 に収まる |
| 認可・応答・秘密 | BR8.1〜BR8.4 | /api/me/ はログインした本人だけ（403 の場面は無く、確かめるのは 401 と 200）、PASSWORD_CURRENT_MISMATCH は 400、説明文は Accept-Language、パスワードとメールアドレスを出さない |
| スキーマの変更 | BR9.1・BR9.2 | V7 で4列（既定の値と既存の行の初期値）と監査の2列、前進のみ・後方互換 |
