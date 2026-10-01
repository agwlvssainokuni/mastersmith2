# Rules — U1 利用停止の状態と3つの入口（u1-user-suspension）

出典の略号: FR・NFR は `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md`、AC と「差」「M」は `aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md`、C1・C7 は `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`、ADR は `aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/decisions.md`、Q1〜Q3 と「要点 n」はこの段の `functional-design-questions.md`、PM は `aidlc/spaces/default/memory/project.md`。

```yaml
rules:
  # --- BR1 停止の状態（UserAccount、C1）
  - id: BR1.1
    statement: 停止の状態は利用者ごとの真偽で持ち、既定は有効（停止していない）とする。停止の理由は持たない
    category: policy
    applies_to: User.suspended
    trigger: 利用者の作成、スキーマの変更の適用
    logic: "suspended は true（停止中）か false（有効）のどちらか。利用者を作る経路（初期管理者の自動作成・登録の完了）は値を渡さず、既定の false で入る。止めた理由・止めた人・止めた時刻は利用者の行に持たない（誰がいつ止めたかは U3 の監査に残る）"
    violation: —
    source: FR3.1、ADR-002、C1、要点 1
  - id: BR1.2
    statement: 停止の列を足すスキーマの変更は V9 で行い、前進のみ・1つ前の版のアプリが動く後方互換とする
    category: constraint
    applies_to: 内部DB のスキーマの変更 V9
    trigger: 起動時のスキーマの変更の適用
    logic: "users に suspended（真偽、必須、既定 false）を足す1つの変更にする。既存の行は既定の false（有効）になる。適用済みの V1〜V8 は書き換えない。1つ前の版のアプリは列を知らずに動き、利用者の作成でも既定の値で入るため後方互換を保てる。ただし1つ前の版のイメージへ戻した間は停止が効かない（1つ前の版は停止を見ないため、停止中の利用者もログインで新しいトークンを取れる。止めたときに無効にしたリフレッシュトークンは無効のまま）。戻すのは問題が起きたときの短い間で、配備先は手元の PC だけのため、依頼者の決定でこの制約を受け入れる（承認の場の決定 R-02）。前の版に戻す前に停止中の利用者を確かめる手順を deployment-pipeline の戻し方の手順に書く。V9 は前進のみのため、戻すときも列は残り、前の版のイメージで動かす選択肢は残る"
    violation: 起動の失敗（スキーマの変更の失敗は既存の Flyway の扱い）
    source: NFR10、ADR-002、team.md の Deployment、要点 2、承認の場の決定 R-02
  - id: BR1.3
    statement: 3つの入口が読む利用者の要約に停止の状態を含め、どの入口も同じ値で判定する
    category: constraint
    applies_to: UserSummary（ログインの照合・トークンの更新・アクセストークンの認証）
    trigger: 入口が利用者を読むとき
    logic: "利用者の要約を作る UserAccount の1か所で suspended を載せる。ログインの照合はパスワードの照合の結果に付く要約を、トークンの更新とアクセストークンの認証は利用者 ID で読んだ要約を使う。停止の判定のために利用者の読み取りを増やさない（今と同じ口で読む）"
    violation: —
    source: ADR-002、FR3.2、要点 3
  - id: BR1.4
    statement: isSuspended は、利用者 ID から今の停止の状態を返す
    category: policy
    applies_to: C1 の UserAccount.isSuspended（`cherry.mastersmith.user.service.UserAccountService#isSuspended(long userId)`、戻り値 boolean）
    trigger: U3 などの呼び出し
    logic: "置き場は既存の UserAccountService（user.service）とし、トランザクションの属性は読み取りだけ（`@Transactional(readOnly = true)`、伝わり方は既定の REQUIRED。呼び出し元のトランザクションがあれば入る）。その時点で内部DB にある値を返す。同じトランザクションの中で setSuspended で書いた後に呼んでも、書いた後の値を返す（古い値を返さない）。そのため setSuspended の更新の問い合わせは、実行の前に未反映の変更を内部DB へ書き出し（flushAutomatically）、実行の後に永続化の文脈を空にする（clearAutomatically）。後の isSuspended・findById・U3 の要約の読み取りは、文脈に残った古いエンティティではなく内部DB から読み直す。既存の updatePreferences・RefreshTokenRepository.revokeIfActive と同じ形にする。確かめは、同じトランザクションで止める・解くを書いてから isSuspended と findById の要約の suspended を読む結合テスト（書く前に findById でエンティティを文脈に読み込んでおく場合を含む）で行う（承認の場の決定 R-05）。IF 利用者がいない THEN 想定外の誤り（IllegalStateException。呼び出し元は先に利用者の有無を確かめる。U3 は行の排他の口で有無を確かめてから使う）"
    violation: 想定外の誤り（IllegalStateException。呼び出し元のトランザクションは巻き戻る）
    source: C1、要点 4、承認の場の決定 R-01・R-05
  - id: BR1.5
    statement: setSuspended は呼び出し元のトランザクションに入り、拒否の判定をせずに停止の列だけを書き換える
    category: constraint
    applies_to: C1 の UserAccount.setSuspended（`cherry.mastersmith.user.service.UserAccountService#setSuspended(long userId, boolean suspended)`、戻り値 void）
    trigger: U3 の止める・解く処理からの呼び出し
    logic: "置き場は既存の UserAccountService（user.service）で、トランザクションの属性は `@Transactional(propagation = Propagation.MANDATORY)` とし、呼び出し元のトランザクションに参加する（無ければ想定外の誤り IllegalTransactionStateException）。書き換えは UserRepository に足す列を絞った更新の問い合わせ（`@Modifying(clearAutomatically = true, flushAutomatically = true)`、users の suspended だけを SET し、更新した行の数を返す）で行い、書いた後の読み取りが古い値を返さないことは BR1.4 のとおり。更新した行の数が 0 なら利用者がいないとして想定外の誤り（IllegalStateException）。拒否の判定（自分自身・最後の管理者・変えるものが無い など）はしない（判定は U3 の UserAdministration）。suspended だけを書き換え、ほかの列（管理者の印・氏名・表示の設定・パスワード）を読み直した値で上書きしない。ロックの状態・リフレッシュトークンには触れない（無効化は呼び出し元が BR5.1 を別に呼ぶ）。監査の出来事は出さない（止める・解く監査は U3、C6）。引数は利用者 ID と真偽だけで、TRACE のログに出ても個人に関する値は出ない"
    violation: 想定外の誤り（呼び出し元のトランザクションは巻き戻る）
    source: C1、ADR-003、要点 4、承認の場の決定 R-01
  - id: BR1.6
    statement: 停止の状態を変えられるのは C1 の setSuspended だけで、利用者自身の API の要求からは変えられない
    category: authorization
    applies_to: 停止の状態の書き換え
    trigger: すべての要求
    logic: "setSuspended を呼ぶのは U3 の UserAdministration の止める・解く処理だけとする（C1）。利用者自身の API（/api/me/ の下、プリファレンスの保存など）の要求の本文に停止を表す項目を足しても無視され、停止の状態は変わらない（要求の型に停止の項目を持たない）"
    violation: —（項目は無視される）
    source: C1、FR8.3、team.md の Testing Posture の要求の改ざん

  # --- BR2 ログインの照合（ADR-008、Q1 A）
  - id: BR2.1
    statement: ログインの照合では、停止の確かめをロックの判定より前に行い、停止中ならパスワードの正誤とロックにかかわらず「停止中」の失敗とする
    category: authorization
    applies_to: ログイン（POST /api/auth/login）
    trigger: メールアドレスに当たる利用者がいて、本人のロックの状態の行を排他つきで読んだ後
    logic: "IF 利用者の要約の suspended が true THEN パスワードの照合の結果（一致・不一致）とロックの状態（ロック中か）を判定に使わず、失敗の理由を ACCOUNT_SUSPENDED とする（BR2.2〜BR2.5）。ELSE 今までどおりロックの判定に進む。存在しないメールアドレスは今までどおり USER_NOT_FOUND の流れ"
    violation: 401 AUTHENTICATION_FAILED（BR2.4）
    source: ADR-008、FR3.2、差5（M3 A）、AC3.2.3、AC3.2.8
  - id: BR2.2
    statement: 停止中のログインでは、本人のロックの状態の行を読んだ値のまま書き戻し、失敗回数・解除の予定の時刻・ロックの状態を変えない
    category: constraint
    applies_to: LoginAttemptState（本人の行）
    trigger: BR2.1 で停止中と判定したとき
    logic: "排他つきで読んだ本人の行の consecutiveFailures と lockedUntil を、そのままの値で1回更新する（Q1 A）。解除の予定の時刻を過ぎていても数え直さず、読んだ値のまま書く。ダミーの行には触れない"
    violation: —
    source: Q1 A、ADR-008、差5、AC3.2.8
  - id: BR2.3
    statement: 停止中のログインのパスワードの照合とロックの状態の行の読み書きの回数は、パスワードを誤ったときと同じにする
    category: constraint
    applies_to: ログインの照合の流れ（列挙の防止）
    trigger: 停止中の利用者のログイン
    logic: "パスワードの照合はトランザクションの外で停止の状態にかかわらず必ず1回行う。判定のトランザクションでは、本人の行の排他つきの読み取り1回・更新1回・出来事1件にする。本人の行が無いとき（古い利用者など）は、パスワードの誤りと同じく何も書かず出来事も出さずに終え、別の短いトランザクションで行を作ってから判定をやり直す（作る行は失敗回数 0・ロックなしで、状態は変わらない）。成功の場合と違い、トークンの発行とリフレッシュトークンの保存は行わない"
    violation: —
    source: ADR-008、ADR-007 の項目4、NFR2、AC3.2.3、Q1 A
  - id: BR2.4
    statement: 停止中のログインの応答は、ほかのログインの失敗と同じ 401 AUTHENTICATION_FAILED とし、トークンを出さない
    category: policy
    applies_to: ログインの応答
    trigger: BR2.1 で停止中と判定したとき
    logic: "状態コード・code・説明文を、存在しないメールアドレス・パスワードの誤り・ロック中と同じにする。応答に停止を示す値を載せない。アクセストークン・リフレッシュトークン・リフレッシュの Cookie を出さない"
    violation: 401 AUTHENTICATION_FAILED
    source: FR3.4、NFR2、AC3.2.3、AC3.2.10
  - id: BR2.5
    statement: 停止中のログインの失敗は、監査に出来事 LOGIN_FAILED・理由 ACCOUNT_SUSPENDED で残す
    category: policy
    applies_to: 監査（C7）
    trigger: BR2.1 で停止中と判定したとき
    logic: "判定のトランザクションの中でログインの失敗の出来事を1件知らせ、AuditLog が確定の後に1行追記する。種類 LOGIN_FAILED、結果 FAILURE、理由 ACCOUNT_SUSPENDED、操作した人は本人の利用者 ID、入れたメールアドレス・送り手の情報・トレースID は既存のログインの失敗と同じ扱い。パスワードの正誤で理由を分けない。監査の書き込みが失敗しても応答は変えない（既存の決まり）"
    violation: —
    source: C7、差5（M3 A）、AC3.2.8
  - id: BR2.6
    statement: 停止中のログインで、メールアドレスとパスワードをアプリのログに出さない
    category: constraint
    applies_to: ログインの照合のアプリのログ
    trigger: 停止中の判定
    logic: "停止で拒否したことをアプリのログに出すときは、利用者 ID と区分だけをキーと値で出す（INFO より下）。メールアドレス・パスワード・トークンの値は出さない"
    violation: —
    source: PM の Forbidden、NFR3

  # --- BR3 トークンの更新（Q2 A）
  - id: BR3.1
    statement: トークンの更新では、利用者を読んだ直後に停止を確かめ、停止中ならほかの更新の失敗と同じ 401 REFRESH_FAILED で拒否して巻き戻す
    category: authorization
    applies_to: トークンの更新（POST /api/auth/session/refresh）
    trigger: 出されたリフレッシュトークンが有効と確かめ、条件つきで無効にし、利用者を利用者 ID で読んだ後
    logic: "IF 利用者の要約の suspended が true THEN 今の更新の失敗と同じく例外で拒否し、トランザクションを巻き戻す。出されたリフレッシュトークンの無効化も巻き戻り、トークンは変わらない（Q2 A。止めたときにまとめて無効にしているため、残るのは M8 B の隙のトークンか無効のトークンだけ）。新しいアクセストークン・リフレッシュトークンは出さない"
    violation: 401 REFRESH_FAILED
    source: FR3.2、FR3.4、Q2 A、AC3.2.2
  - id: BR3.2
    statement: トークンの更新で停止中だったことは監査に残さない
    category: policy
    applies_to: 監査
    trigger: BR3.1 の拒否
    logic: "今の更新の失敗と同じく、監査の出来事を出さない（C7 の not_recorded）"
    violation: —
    source: C7
  - id: BR3.3
    statement: 停止を解いた後も、止める前に出したリフレッシュトークンでの更新は拒否される
    category: policy
    applies_to: トークンの更新
    trigger: 停止を解いた後の、止める前のリフレッシュトークンでの更新
    logic: "止めたときに BR5.1 で無効にした行は戻らない（BR5.4）ため、今の決まり（無効にしたトークンは拒否）で 401 REFRESH_FAILED になる。停止を解いた後の新しいログインで出たリフレッシュトークンは受け付ける（BR6.2）"
    violation: 401 REFRESH_FAILED
    source: FR3.3、AC3.2.4

  # --- BR4 アクセストークンの認証（Q3 A）
  - id: BR4.1
    statement: アクセストークンの認証では、署名と有効期限を確かめて利用者を読んだ直後に停止を確かめ、停止中なら区分 USER_SUSPENDED の認証の失敗とする
    category: authorization
    applies_to: アクセストークンの認証（Authorization ヘッダーの付いたすべての API）
    trigger: アクセストークンが有効で、利用者を利用者 ID で読んだ後
    logic: "IF 利用者の要約の suspended が true THEN 認証の失敗の区分 USER_SUSPENDED で拒否する。応答は今の 401 の入口のまま、無効・期限切れのアクセストークンと同じ 401 AUTHENTICATION_REQUIRED になる。利用者の状態は要求ごとに内部DB から読むため、止めた確定の後の次の要求から拒否する"
    violation: 401 AUTHENTICATION_REQUIRED
    source: FR3.2、FR3.4、Q3 A、AC3.2.1
  - id: BR4.2
    statement: 区分 USER_SUSPENDED の拒否は、管理の API のアクセスの拒否の監査に残さない
    category: policy
    applies_to: 管理の API（/api/admin/ の下）の 401 の監査
    trigger: BR4.1 の拒否が管理の API で起きたとき
    logic: "アクセスの拒否の理由への変換で、USER_SUSPENDED は有効期限切れ TOKEN_EXPIRED と同じく「理由なし」とし、アクセスの拒否の出来事を出さない（C7 の not_recorded）。拒否の理由の値は足さない。網羅の変換に1行を足すため access.domain に手が入る（functional-spec.md 8節）"
    violation: —
    source: Q3 A、C7
  - id: BR4.3
    statement: アクセストークンに失効の仕組みは持たず、停止を解いた後は止める前のアクセストークンも有効期限まで受け付ける
    category: policy
    applies_to: アクセストークンの認証
    trigger: 停止を解いた後の、止める前に出したアクセストークンでの要求
    logic: "停止中の拒否は BR4.1 の都度の判定だけで行い、トークンを失効させない。そのため停止を解いた後は、止める前のアクセストークンも有効期限の直前まで受け付け、有効期限ちょうど以後は今までどおり 401 にする。時刻は注入した時計で判定する"
    violation: 有効期限ちょうど以後は 401 AUTHENTICATION_REQUIRED
    source: PM の DECIDED、差1（M1 A）、AC3.2.7
  - id: BR4.4
    statement: 停止中の管理者は、管理の API の業務の処理に届かない
    category: authorization
    applies_to: 管理の API（/api/admin/ の下）
    trigger: 停止中の管理者のアクセストークンでの要求
    logic: "BR4.1 の認証の失敗で 401 になり、認可（管理者の印の確かめ）と業務の処理は動かない。対象の利用者の状態は変わらず、業務の操作の監査の行は作られない（BR4.2 によりアクセスの拒否の行も作られない）"
    violation: 401 AUTHENTICATION_REQUIRED
    source: FR3.8、AC3.2.5

  # --- BR5 リフレッシュトークンのまとめての無効化（C1 の revokeAllRefreshTokens、ADR-003）
  - id: BR5.1
    statement: 利用者のまだ無効にしていないリフレッシュトークンをすべて、今の時刻で無効にし、無効にした件数を返す
    category: calculation
    applies_to: C1 の Authentication.revokeAllRefreshTokens（`cherry.mastersmith.auth.service.RefreshTokenRevocationService#revokeAllRefreshTokens(long userId)`、戻り値 `RevokeAllResult`）
    trigger: U3 の止める処理からの呼び出し
    logic: "置き場は auth.service に新しく作る RefreshTokenRevocationService とする（user は auth を知らないため user には置かない、BR7.1。useradmin から auth への向きは ADR-001・C8 と同じ）。結果は auth.service に置く record `RevokeAllResult(int revoked)` で返す。更新は RefreshTokenRepository に足す問い合わせ（`@Modifying(clearAutomatically = true, flushAutomatically = true)`、user_id が一致し revoked_at が空の行に今の時刻を入れ、更新した行の数を返す）で行う。利用者 ID の行のうち revokedAt が空のものすべてに、注入した時計の今の時刻を入れる1回の更新を行う。期限切れの行も対象に含める（害が無く、条件が単純になる）。revokedAt が入っている行は書き換えない。無効にした件数（0 以上）を返し、0 件でも成功とする。利用者がいない ID でも 0 件で成功する"
    violation: —
    source: FR3.3、ADR-003、C1、ADR-007 の項目2、要点 8、承認の場の決定 R-01
  - id: BR5.2
    statement: まとめての無効化は、呼び出し元のトランザクションの中でだけ動く
    category: constraint
    applies_to: C1 の Authentication.revokeAllRefreshTokens
    trigger: 呼び出し
    logic: "トランザクションの属性は `@Transactional(propagation = Propagation.MANDATORY)` とし、呼び出し元のトランザクションに参加し、止める操作と同じ確定に入る。呼び出し元が巻き戻れば無効化も巻き戻る。IF トランザクションの外から呼ばれる THEN 想定外の誤り（IllegalTransactionStateException。自分で新しく始めない）"
    violation: 想定外の誤り
    source: C1（MANDATORY）、ADR-003
  - id: BR5.3
    statement: まとめての無効化は監査の出来事を出さず、アプリのログには利用者 ID と件数だけを出す
    category: policy
    applies_to: C1 の Authentication.revokeAllRefreshTokens
    trigger: 無効化の後
    logic: "監査は止める操作の監査（U3 の USER_SUSPENDED、C6）に含めて扱う。アプリのログは DEBUG で利用者 ID と件数をキーと値で出す。トークンの値とハッシュは出さない"
    violation: —
    source: C1、C6、PM の Forbidden、要点 8
  - id: BR5.4
    statement: 停止を解いても、無効にしたリフレッシュトークンは有効に戻さない
    category: policy
    applies_to: RefreshToken.revokedAt
    trigger: 停止を解く操作
    logic: "停止を解く処理はリフレッシュトークンに触れない。一度入れた revokedAt を空に戻す書き方は作らない"
    violation: —
    source: FR3.3、C1、AC3.2.4
  - id: BR5.5
    statement: 止める操作とトークンの更新・ログインの照合が同時に重なったときの隙は塞がない
    category: policy
    applies_to: まとめての無効化とトークンの更新・ログインの照合
    trigger: 止める処理の確定の前に、同じ利用者のトークンの更新が新しいリフレッシュトークンを作ったとき。または、同じ利用者のログインの照合（トランザクションの外で停止の状態を読む手順）の後、判定の前に止める処理が確定したとき
    logic: "トークンの更新の経路: その新しいリフレッシュトークンは無効化を逃れて残りうる。ログインの経路: ログインの停止の判定は照合のときに読んだ利用者の要約の suspended を使う（判定のトランザクションで排他して読むのはロックの状態の行で、users の行ではない）ため、照合の後・判定の前に止める処理が確定すると、そのログインは通り、無効化の後に新しいリフレッシュトークンが作られて残りうる。どちらの経路も、停止中は次の要求で BR4.1（アクセストークン）と BR3.1（リフレッシュトークン）により拒否されるため停止の間は害が無いが、停止を解いた後はそのリフレッシュトークンが使えうる。ログインの経路はパスワードを知る本人に限られ、停止を解けば同じログインができる。依頼者の決定として、M8 B と同じ種類の隙として両方の経路を受け入れ、排他も判定のトランザクションでの読み直しも足さない（読み直すと読み取りが増え、BR1.3 と BR2.3 の回数の一致を崩すため。承認の場の決定 R-03）"
    violation: —
    source: M8 B、ADR-003、C1、承認の場の決定 R-03

  # --- BR6 列挙の防止と、停止を解いた後
  - id: BR6.1
    statement: 3つの入口の停止中の拒否は、入口ごとのほかの失敗と同じ応答にし、応答から停止を推測できないようにする
    category: policy
    applies_to: ログイン・トークンの更新・アクセストークンの認証の応答
    trigger: 停止中の拒否
    logic: "ログインは 401 AUTHENTICATION_FAILED、トークンの更新は 401 REFRESH_FAILED、アクセストークンの認証は 401 AUTHENTICATION_REQUIRED で、状態コード・code・説明文をほかの失敗と同じにする。利用者の要約の suspended は、認証の成功の応答（ログイン・更新・自分の情報）にも載せない"
    violation: —
    source: FR3.4、NFR2、PM の Mandated、AC3.2.1〜AC3.2.3
  - id: BR6.2
    statement: 停止を解いた直後から、3つの入口のすべてで受け付ける
    category: policy
    applies_to: ログイン・トークンの更新・アクセストークンの認証
    trigger: 停止を解いた確定の後の要求
    logic: "3つの入口は要求ごとに内部DB の suspended を読むため、false に戻った確定の後の次の要求から、ほかの決まりに合えば受け付ける。停止中のログインで失敗回数とロックの状態を変えていない（BR2.2）ため、停止を解いた直後に停止によるロックは残らない。止める前のリフレッシュトークンは BR3.3 のとおり拒否する"
    violation: —
    source: FR3.5、AC3.2.4
  - id: BR6.3
    statement: 画面は変えず、停止中の 401 を今のログインの切れと同じに扱う
    category: policy
    applies_to: 画面の 401 の扱い
    trigger: 停止中の利用者の画面の操作・ログイン
    logic: "応答がほかの失敗と同じため、今の画面の動き（401 を受けたらトークンの更新を試み、更新も 401 ならログインの画面へ移る。ログインの失敗はパスワードの誤りと同じ文言）のままで、停止を示す文言は出さない。画面の代表の流れは U5 の E2E で確かめる"
    violation: —
    source: FR3.4、AC3.2.9、AC3.2.10、M9 A
  - id: BR6.4
    statement: 停止中の利用者のメールアドレスは登録済みのまま扱う
    category: policy
    applies_to: 招待の登録済みの確かめ
    trigger: 停止中の利用者のメールアドレスへの招待
    logic: "停止中でも利用者の行は残るため、今の招待の確かめ（メールアドレスの利用者がいれば登録済みとして拒否）のまま拒否される。停止の状態で招待の確かめを変えない。U1 に新しい決まりは無いため、確かめは既存の動きの回帰テスト（停止中の利用者のメールアドレスへの招待が今の登録済みの拒否になること）を1件、U1 のコード生成の計画に入れて行う（承認の場の決定 R-04）"
    violation: 今の招待の登録済みの拒否のまま
    source: FR3.9、要件の前提 A1、AC3.2.6

  # --- BR7 構造と名前
  - id: BR7.1
    statement: user は auth を知らないまま、停止の判定は auth が行う
    category: constraint
    applies_to: パッケージの境界（NFR11）
    trigger: 設計と実装
    logic: "user には列と口（isSuspended・setSuspended）と要約の値だけを足し、停止で拒否する判定は auth の3つの入口に置く。既存の ArchUnit の境界テストを緩めない・消さない"
    violation: —（境界テストの失敗）
    source: NFR11、ADR-002、要点 13
  - id: BR7.2
    statement: 足す区分と理由の名前は、監査の列の長さ 32 文字に収める
    category: constraint
    applies_to: ACCOUNT_SUSPENDED・USER_SUSPENDED
    trigger: 設計と実装
    logic: "ACCOUNT_SUSPENDED は 17 文字、USER_SUSPENDED は 14 文字で、どちらも 32 文字に収まる。既存の区分の名前は変えない"
    violation: —
    source: C7、要点 9
```

## 決まりの一覧

| 群 | ID | 要点 |
|---|---|---|
| 停止の状態 | BR1.1〜BR1.6 | 真偽で既定は有効・理由は持たない、V9 で前進のみ・後方互換（前の版へ戻した間は停止が効かない制約を受け入れる）、3つの入口は同じ要約の値を読む、isSuspended と setSuspended は UserAccountService に置く（読み取りだけ・MANDATORY・判定しない・停止の列だけ・書いた後の読み取りは書いた値を返す）、変えられるのは C1 の口だけ |
| ログインの照合 | BR2.1〜BR2.6 | ロックの判定より前に停止を確かめる、本人の行を読んだ値のまま書き戻す、照合1回・排他つきの読み取り1回・更新1回・出来事1件、401 AUTHENTICATION_FAILED、監査は LOGIN_FAILED・ACCOUNT_SUSPENDED、ログにメールアドレスを出さない |
| トークンの更新 | BR3.1〜BR3.3 | 利用者を読んだ直後に確かめ 401 REFRESH_FAILED で巻き戻す（出されたトークンは変えない）、監査しない、止める前のトークンは解いた後も拒否 |
| アクセストークンの認証 | BR4.1〜BR4.4 | 区分 USER_SUSPENDED で 401 AUTHENTICATION_REQUIRED、管理の API の監査に残さない、失効の仕組みは持たず解いた後は期限まで使える、停止中の管理者は業務に届かない |
| まとめての無効化 | BR5.1〜BR5.5 | auth.service の RefreshTokenRevocationService が未無効の行すべてを今の時刻で無効にし RevokeAllResult で件数を返す、呼び出し元のトランザクションだけ（MANDATORY）、監査しない、解いても戻さない、トークンの更新とログインの照合との同時の重なりの隙は塞がない |
| 列挙の防止と解いた後 | BR6.1〜BR6.4 | 入口ごとにほかの失敗と同じ応答、解いた直後から受け付ける、画面は変えない、メールアドレスは登録済みのまま |
| 構造と名前 | BR7.1・BR7.2 | user は auth を知らない、名前は 32 文字に収まる |
