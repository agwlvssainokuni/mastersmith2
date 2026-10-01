# Rules — U3 利用者の管理の API（u3-user-admin-api）

出典の略号: FR・NFR は `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md`、AC と「差」「M」は `aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md`、C1〜C8 は `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`、ADR は `aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/decisions.md`、Q1〜Q7 と「要点 n」はこの段の `functional-design-questions.md`、PM は `aidlc/spaces/default/memory/project.md`、TP は `aidlc/spaces/default/memory/team.md`。既存の定義は `entities.md` の冒頭のコードで確かめた。

決まりは7つの群に分ける。BR1 は一覧と検索、BR2 は5つの操作に共通の拒否の判定、BR3 は行の排他と最後の有効な管理者の保護、BR4 は5つの操作それぞれの実行、BR5 は氏名と言語の変更、BR6 は監査、BR7 は認可・改ざん・漏えい・構造である。5つの操作は、管理者の印を付ける（grant-admin）・外す（revoke-admin）・止める（suspend）・停止を解く（resume）・失敗回数を戻す（reset-login-failures）を指す。

```yaml
rules:
  # --- BR1 一覧と検索（GET /api/admin/users、C2・C3・C8）
  - id: BR1.1
    statement: 一覧は登録した日時の古い順、同じ日時は利用者 ID の小さい順に、1ページ 20 件で並べる
    category: constraint
    applies_to: 一覧の行の読み出し（C8 findAdminPage）
    trigger: 一覧の要求を受け、ページの番号と検索の文字の検証を通った後
    logic: "並びは users.created_at の昇順、同じ値なら user_id の昇順で一意に決める。読み始めの位置は共通の部品 Paging の offsetOf で求め、1ページの件数は Paging の PAGE_SIZE（20）。ページの番号は Paging の parsePage で検証し（指定が無ければ 1）、結果が空なら 400 VALIDATION_FAILED（監査なし）。読み始めの位置が全体の件数以上なら行を読まず、items が空で total つきの 200 を返す（最後のページより後、差7）。ページ送りの計算を useradmin に重ねて書かない"
    violation: ページの番号の誤りは 400 VALIDATION_FAILED（項目ごとの誤りは載せない。招待の一覧と同じ形）
    source: FR1.3、FR1.5、AC1.1.1、AC1.1.5、AC1.1.7、C2、C3、差7、要点 2
  - id: BR1.2
    statement: 一覧の対象は利用者の表のすべての行で、停止中の利用者と初期管理者を含み、招待中の人を含まない
    category: policy
    applies_to: 一覧の行の読み出し（C8 findAdminPage）
    trigger: 一覧の要求
    logic: "users の行だけを読む。状態（管理者・停止中・ロック中）で絞らない。招待中の人は招待の表にだけあり users に行が無いため一覧に出ない（前提 A4）。見ている管理者自身も必ず含まれる"
    violation: —
    source: FR1.1、FR1.4、要件の前提 A4、AC1.1.7
  - id: BR1.3
    statement: 検索の文字 q は、controller の引数の時点から伏せ字の型 SearchText で受け、型の変換では包むだけにする
    category: constraint
    applies_to: 要求の q から SearchText への型の変換（useradmin.web）
    trigger: 一覧の要求の結び付け
    logic: "文字列から SearchText への変換の部品は Spring の Bean にせず、useradmin.web の設定のクラス（Web MVC の設定、例 UserAdminWebConfig）で new して登録する（既存の DslWebConfig と同じ形）。変換は値を包むだけで、検証・空白の除去・ログの出力をしない（変換で例外を出さない）。q が無ければ検索なし。SearchText は user.domain の RedactedText の隣に置き、業務処理と UserAccount へもこの型のまま渡す。トレースの仕組み（TraceAspect）の式は変えない"
    violation: —
    source: C3（R-01）、Q1 A、Q2 A（R-06）、要点 12、AC1.1.6
  - id: BR1.4
    statement: 検索の文字は前後の空白を除いてから 254 コードポイントまでとし、除いた後が空なら絞らない
    category: validation
    applies_to: SearchText の検証（user.domain に置き、controller が受けた後に呼ぶ）
    trigger: 一覧の要求で、ページの番号の検証を通った後
    logic: "前後から Unicode の White_Space の文字（半角・全角の空白を含む、氏名の保存と同じ範囲）を除く。IF 除いた後のコードポイントの数が 254 を超える THEN 400 VALIDATION_FAILED（項目ごとの誤り q・TOO_LONG。入れた値は載せない）。ELSE IF 除いた後が空 THEN 検索なし（全件と同じ）。ELSE 除いた後の値で検索する。使える文字は制限しない。検証の誤りは監査に残さず、アプリのログに値を出さない（例外のログは code とクラスの名前だけ）。ページの番号とどちらも誤りのときは、ページの番号の誤りを返す"
    violation: 400 VALIDATION_FAILED（fieldErrors に q と TOO_LONG）
    source: FR1.5、AC1.1.4、AC1.1.5、Q3 A
  - id: BR1.5
    statement: 検索はメールアドレスか氏名の部分一致で、大文字と小文字を区別せず、% と _ と \ を文字どおりに扱う
    category: calculation
    applies_to: 一覧の検索の条件（C8 findAdminPage）
    trigger: BR1.4 で検索ありと決まったとき
    logic: "検索の文字と氏名を小文字にそろえて比べる（メールアドレスは保存のときに小文字にそろえてある）。メールアドレス OR 氏名に、検索の文字を含む行を当てる。検索の文字の中の \\ と % と _ を、エスケープの文字を決めてエスケープし、ワイルドカードとして働かせない。問い合わせは名前つきの引数だけで組み立て、検索の文字を問い合わせの文に連結しない。ASCII の英字の大文字と小文字は必ず区別しない。小文字にそろえる範囲を Unicode まで広げるかは NFR 設計で確かめる。全体の件数も同じ条件で数える"
    violation: —
    source: FR1.4、AC1.1.4、要点 2
  - id: BR1.6
    statement: 全体の件数と行は、同じ読み取りだけのトランザクションの中で読む
    category: constraint
    applies_to: 一覧の読み出し（C8 findAdminPage と lockViewsOf）
    trigger: 一覧の要求
    logic: "全体の件数（条件に当たる行の数）と、そのページの行と、行の利用者のロックの判定の結果を、1つの読み取りだけのトランザクションの中で読む。行の排他は取らない。パスワードのハッシュ値の列は読まない"
    violation: —
    source: FR1.3、C8、要点 2
  - id: BR1.7
    statement: 一覧のロックの判定の結果は、ロック中か・解除の予定の時刻・失敗回数を戻せるかの3つだけを、今の時刻で判定して返す
    category: calculation
    applies_to: C8 lockViewsOf と LockView の判定（auth.domain の純粋な関数）
    trigger: 一覧のページの行が決まった後
    logic: "ページの利用者 ID（最大 20 件）のロックの状態の行を、排他なしの1回の問い合わせで読む（ダミーの行は対象の ID に入らないため読まない）。今の時刻は注入した時計から取る。locked は「解除の予定の時刻があり、今の時刻 < 解除の予定の時刻」（ちょうどはロック中でない。既存の LockPolicy と同じ境界）。lockedUntil は locked が真のときだけ解除の予定の時刻。resettable は「表の失敗回数が 1 以上」（解除の予定の時刻を過ぎた利用者を含む、M2 B）。IF 行が無い THEN (false, 無し, false)。失敗回数そのもの・ダミーの行を useradmin へ渡さない。判定の関数は性質ベースのテスト（jqwik）の対象"
    violation: —
    source: FR1.2、FR1.6、AC1.1.2、AC1.1.3、M2 B、差4、要点 3
  - id: BR1.8
    statement: 一覧の行の応答は決めた11の項目だけを持ち、self は操作している管理者自身の行かを表す
    category: constraint
    applies_to: 一覧の応答（AdminUser・AdminUserPage）
    trigger: 一覧の応答を作るとき
    logic: "行は userId・email・displayName・language・admin・suspended・locked・lockedUntil・resettable・registeredAt・self だけを持つ。self は行の userId と要求の文脈の操作した人の利用者 ID が等しいときに真。registeredAt は users.created_at。日時は ISO 8601 の UTC（Z 付き）。ページは items・page（受けたページの番号）・size（20）・total を持つ"
    violation: —
    source: FR1.1、FR1.6、AC1.1.1、AC1.1.3、C3、要点 4
  - id: BR1.9
    statement: 一覧の閲覧と、一覧の入力の誤りは監査に残さない
    category: policy
    applies_to: 一覧の API
    trigger: 一覧の要求（成功・入力の誤りとも）
    logic: "一覧の業務処理は監査の出来事を出さない。管理者でない利用者の 403 は既存の認可の入口が「アクセスの拒否」として残す（BR7.1）"
    violation: —
    source: FR7.3、AC1.1.5、AC1.1.13

  # --- BR2 5つの操作に共通の拒否の判定（FR7.5、差3、C3）
  - id: BR2.1
    statement: 業務の拒否は「対象がいない → 自分自身 → 対象が停止中 → 変えるものが無い → 最後の有効な管理者」の順に判定し、最初に当たった理由1つで拒否する
    category: policy
    applies_to: 5つの操作の拒否の判定（useradmin.domain の純粋な関数）
    trigger: 操作の対象を排他（または読み取り）した後
    logic: "関数は操作の区分（AdminOperation）と事実の組（OperationFacts）を受け、その操作に当たりうる理由（BR2.2）だけを上の順に調べ、最初に当たった理由を1つ返す。どれにも当たらなければ「拒否しない」を返す。DB・時刻に触れない純粋な関数とし、単体テストと性質ベースのテストで、順と操作ごとの当てはまりを確かめる。応答の code（BR2.3）と監査の理由（BR6.1）はこの1つにそろえる"
    violation: 理由ごとの code で拒否（BR2.3）
    source: FR7.5、差3（M7 B）、C3、要点 5
  - id: BR2.2
    statement: 操作ごとに当たりうる理由を決める
    category: policy
    applies_to: BR2.1 の関数の操作ごとの表
    trigger: BR2.1 の判定
    logic: "印を付ける: 対象がいない・自分自身・対象が停止中・変えるものが無い（すでに管理者）。印を外す: 対象がいない・自分自身・対象が停止中・変えるものが無い（管理者でない）・最後の有効な管理者。止める: 対象がいない・自分自身・変えるものが無い（すでに停止中）・最後の有効な管理者（対象が停止中の理由は当てない）。停止を解く: 対象がいない・自分自身・変えるものが無い（停止中でない）。失敗回数を戻す: 対象がいない・変えるものが無い（戻せない利用者）（自分自身と対象が停止中は当てず、許す）"
    violation: 理由ごとの code で拒否（BR2.3）
    source: FR2.4、FR2.5、FR3.6、FR3.7、FR5.2、差3、stories.md の前提の表、AC4.1.7、AC4.1.8
  - id: BR2.3
    statement: 理由と結果を、1つの code に1つの状態コードで応答に写す
    category: policy
    applies_to: 5つの操作の応答（useradmin.web）
    trigger: 業務処理の結果を受けたとき
    logic: "業務処理は想定内の失敗を結果の型（OperationResult）で返し、controller が場合を尽くす switch で応答か業務の例外に変える。対象がいない → 404 USER_NOT_FOUND。自分自身 → 409 USER_ADMIN_SELF_OPERATION。対象が停止中 → 409 USER_ADMIN_TARGET_SUSPENDED。変えるものが無い → 409 USER_ADMIN_NO_CHANGE。最後の有効な管理者 → 409 USER_ADMIN_LAST_ADMIN。排他の待ちの上限切れ → 409 USER_ADMIN_BUSY。操作した人の確かめ直しで外れていた → 403 ACCESS_DENIED（既存）。成功 → 本文なしの 204。業務の拒否を 5xx にしない。code は useradmin.domain の UserAdminProblemTypes に置き、一覧を useradmin.service の UserAdminProblemTypeCatalog で集める（ACCESS_DENIED と VALIDATION_FAILED は既存の定義を使い、重ねて定義しない）"
    violation: —（写し方の決まり）
    source: C3、エラーの code の一覧、Q6 A、TP の Code Style、AC2.1.6
  - id: BR2.4
    statement: 拒否したときは、対象の利用者の状態を何も変えず、トランザクションを書き込みなしで確定させる
    category: constraint
    applies_to: 5つの操作の業務処理のトランザクション
    trigger: 業務の理由（BR2.1）か操作した人の確かめ直し（BR2.5）で拒否したとき
    logic: "印・停止・失敗回数・解除の予定の時刻・ロックの状態の行の有無・その利用者のリフレッシュトークンを変えない。書き込みをせずにトランザクションを確定させ、確定の後に監査の出来事が記録されるようにする（BR6.3）。排他の待ちの上限切れ（BR3.5）だけは巻き戻す"
    violation: —
    source: FR4.2、stories.md の前提（状態が変わらない）、要点 5、ADR-006
  - id: BR2.5
    statement: 状態を変える5つの操作は、業務の理由の判定をすべて通った後、状態を変える直前に、操作する管理者が今も有効な管理者かを確かめ直す
    category: authorization
    applies_to: 5つの操作（氏名と言語の変更は含めない）
    trigger: BR2.1 で拒否しないと決まった直後
    logic: "印を付ける・外す・止める: 行の排他（BR3.1）で得た排他の後の有効な管理者の集合に、操作した人の利用者 ID が含まれるか。停止を解く・失敗回数を戻す: 操作した人の利用者の行を排他なしで読み直し、いて・印を持ち・停止していないか（BR3.3・BR3.4 のとおり、どの操作も users の行とロックの状態の行を同時に排他しないため読むだけにする）。IF 含まれない（有効でない）THEN BR2.6。判定の順の中でこの確かめを業務の理由の後に置くことで、同時の重なりで負けた側の理由は「最後の有効な管理者」のままになる（AC2.1.6・AC2.1.12）"
    violation: 403 ACCESS_DENIED（BR2.6）
    source: Q5 A、ADR-007、C8、AC2.1.6、AC2.1.12、要点 6
  - id: BR2.6
    statement: 確かめ直しで操作した人が有効な管理者でなかったら、403 ACCESS_DENIED で拒否し、その操作の失敗として理由 NOT_ADMIN で監査に残す
    category: authorization
    applies_to: BR2.5 で外れていた操作
    trigger: BR2.5
    logic: "状態を変えない（BR2.4）。応答は既存の 403 ACCESS_DENIED（access.domain の AccessProblemTypes の定義を useradmin.web から使う）。監査は、その操作の出来事の種類・結果 FAILURE・理由 NOT_ADMIN・操作した人・対象は要求の利用者 ID で1行残す（BR6.1）。既存のアクセスの拒否の出来事（ACCESS_DENIED の種類）は出さない。画面は既存の 403 の共通の扱いで権限の無い表示に移る"
    violation: 403 ACCESS_DENIED
    source: Q6 A、C8、FR8.2
  - id: BR2.7
    statement: 利用者 ID が整数として読めないときは入力の誤り、整数だがいないときは対象がいないとする
    category: validation
    applies_to: 5つの操作と氏名と言語の変更の userId
    trigger: 要求の結び付け
    logic: "userId は整数（long）で受ける。数でない・空・桁あふれは既存の型の誤りの扱いで 400 VALIDATION_FAILED になり、業務処理を呼ばず監査に残さない。整数として正しく、利用者の表に行が無いとき（0 や負の値を含む）は「対象がいない」とする。失敗回数を戻す操作でも、先に利用者の表で有無を確かめるため、負の ID のダミーの行に触れない"
    violation: 400 VALIDATION_FAILED（監査なし）、または 404 USER_NOT_FOUND（監査あり。氏名と言語の変更は監査なし）
    source: stories.md の前提（利用者 ID の形の誤り）、C3 の BadUserId、FR7.6、AC2.1.5
  - id: BR2.8
    statement: 操作した人は要求の文脈（認証の主体）から読み、要求の引数や本文から受け取らない
    category: authorization
    applies_to: 一覧と6つの変更の API
    trigger: 要求を受けたとき
    logic: "操作した管理者の利用者 ID は、認証の主体から読む（既存の招待の管理の API と同じ形）。読めないときは既存の 401 AUTHENTICATION_REQUIRED。self の判定（BR1.8）・自分自身の判定（BR2.1）・確かめ直し（BR2.5）・監査の操作した人（BR6.2）は、すべてこの ID を使う。要求の送り手の情報（IP・User-Agent・トレースID）も要求の文脈から作る"
    violation: 401 AUTHENTICATION_REQUIRED
    source: TP の Code Style、要点 4、C6

  # --- BR3 行の排他と最後の有効な管理者の保護（ADR-007、C8）
  - id: BR3.1
    statement: 印を付ける・外す・止めるは、管理者の印を持つ行と対象の行を、利用者 ID の小さい順にまとめて排他してから判定する
    category: constraint
    applies_to: C8 lockAdminRowsInIdOrder（印を付ける・外す・止める）
    trigger: 業務処理のトランザクションの最初
    logic: "管理者の印を持つすべての利用者の行（停止中の管理者を含む）と対象の利用者 ID の行を、利用者 ID の昇順に PESSIMISTIC_WRITE で排他する。待ちの上限は既存の排他と同じ 3000 ミリ秒。排他の後に、対象の要約（いなければ無し）と、有効な管理者（印あり・停止中でない）の利用者 ID の集合を読んで返す。すべての操作が同じ順で取るため、互いの行を待ち合っても行き詰まらない。待った後の問い合わせが、排他の間に確定した変更を数えに含めることは NFR 設計で確かめる"
    violation: 上限切れは Busy（BR3.5）
    source: ADR-007、C8、AC2.1.6、AC2.1.12、AC3.1.5、要点 6
  - id: BR3.2
    statement: 有効な管理者は印を持ち停止していない利用者とし、印を外す・止めるで対象を除くと 0 人になるなら拒否する
    category: constraint
    applies_to: 印を外す・止めるの「最後の有効な管理者」の判定
    trigger: BR3.1 の排他の後
    logic: "有効な管理者の集合は BR3.1 の排他の後に数えたもの。ロック中の管理者も数える。IF 対象が有効な管理者に含まれ、集合から対象を除くと空 THEN 最後の有効な管理者として拒否する（BR2.1 の順の最後）。印を付ける・停止を解くは有効な管理者を減らさないため判定しない。1件ずつの API の操作では、操作する管理者が有効な管理者のため起きないが、同時の重なりとほかの経路の変化に備えて操作ごとに必ず判定する"
    violation: 409 USER_ADMIN_LAST_ADMIN
    source: FR4.1、FR4.2、FR4.3、FR4.4、PM の Forbidden、AC2.1.11、AC3.1.9
  - id: BR3.3
    statement: 停止を解くは、対象の行だけを排他する
    category: constraint
    applies_to: C8 lockUserRow（停止を解く）
    trigger: 業務処理のトランザクションの最初
    logic: "対象の利用者 ID の行だけを PESSIMISTIC_WRITE・上限 3000 ミリ秒で排他し、対象の要約（いなければ無し）を返す。停止を解くは有効な管理者を減らさないため、管理者の行は排他しない。操作した人の確かめ直しは行を読むだけ（BR2.5）"
    violation: 上限切れは Busy（BR3.5）
    source: C8、Q4 A、要点 6
  - id: BR3.4
    statement: 失敗回数を戻すは、ロックの状態の行だけを排他し、利用者の行は排他しない
    category: constraint
    applies_to: 失敗回数を戻す操作の排他（R-07）
    trigger: 業務処理のトランザクションの中
    logic: "対象の有無と操作した人の確かめ直しは、利用者の行を排他なしで読む。排他するのは対象のロックの状態の行だけで、ログインの判定と同じ行・同じ PESSIMISTIC_WRITE・同じ上限 3000 ミリ秒で順番をそろえる（FR5.4）。どの操作も利用者の行とロックの状態の行を同時に排他しないため、2種類の行を取る順の決まりは要らず、ログインとの行き詰まりが起きない。止める操作のトークンの無効化とログインのトークンの追記が利用者の行の外部キーで待つ場合も、待つ向きは一方だけになる（NFR 設計で確かめる）"
    violation: 上限切れは Busy（BR3.5）
    source: Q4 A（R-07）、FR5.4、AC4.1.5
  - id: BR3.5
    statement: 排他の待ちの上限切れは Busy として返し、409 USER_ADMIN_BUSY にし、状態を変えず、監査に残さない
    category: policy
    applies_to: 5つの操作の排他（BR3.1・BR3.3・BR3.4）
    trigger: 行の排他が待ちの上限を超えたとき
    logic: "排他の口は上限切れを例外のまま投げず Busy を返す（既存の LoginAttemptStateRepository の上限切れの例外は auth.service の中で受けて Busy に変える）。業務処理は判定に進まず、トランザクションを巻き戻して 409 USER_ADMIN_BUSY を返す。業務の理由の判定の外にあり、印を付ける・外す・止める・停止を解くでは対象がいないかの判定より前、失敗回数を戻すでは対象がいないかの判定の後（BR3.4 で利用者の行を排他しないため）に起きうる。監査には残さない（C6 の not_recorded に確定、Q7 A）。既存のエラー応答の仕組みがアプリのログに WARN で code を出す。例外を受けた後にトランザクションが巻き戻しの印で確定できない場合の扱いは NFR 設計で確かめる"
    violation: 409 USER_ADMIN_BUSY
    source: C3、C8、Q7 A（R-04）、AC4.1.11、要点 6
  - id: BR3.6
    statement: 同時の重なりのテストのため、本番では何もしない待ち合わせの口を2か所に置く
    category: policy
    applies_to: useradmin.service と auth.service の待ち合わせの口
    trigger: 行を排他した直後
    logic: "1つ目は useradmin.service の「有効な管理者を数える直前」（BR3.1 の排他の後、BR2.1 の判定の前）。2つ目は auth.service の「ロックの状態の行を排他した直後」で、失敗回数を戻す操作とログインの判定の両方から呼ぶ。本番の部品は何もしない既定の部品とし（既存の InvitationBarrier と同じ形）、テストだけが差し替えて重なりを確実に作る。本番の流れと順を変えない"
    violation: —
    source: TP の Testing Posture（同時の重なりは待ち合わせで作る）、NFR4、要点 7、AC2.1.6、AC2.1.12、AC3.1.5、AC4.1.5

  # --- BR4 5つの操作の実行（C1・C3・C8）
  - id: BR4.1
    statement: 印を付けるは、管理者の印だけを付け、トークンを無効にしない
    category: policy
    applies_to: POST /api/admin/users/{userId}/grant-admin
    trigger: BR2.1 と BR2.5 を通ったとき
    logic: "C8 setAdmin(userId, true) を呼び、印の列だけを書き換える。アクセストークン・リフレッシュトークンには触れない。管理者の印は要求ごとに内部DB から読まれるため、確定の後の対象の次の要求から管理の API が許される"
    violation: —
    source: FR2.1、FR2.2、要件の前提 A5、AC2.1.1
  - id: BR4.2
    statement: 印を外すは、管理者の印だけを外し、トークンを無効にしない
    category: policy
    applies_to: POST /api/admin/users/{userId}/revoke-admin
    trigger: BR2.1 と BR2.5 を通ったとき
    logic: "C8 setAdmin(userId, false) を呼び、印の列だけを書き換える。トークンには触れない（FR2.3）。確定の後の対象の次の要求から管理の API は 403 になる"
    violation: —
    source: FR2.1、FR2.2、FR2.3、AC2.1.2
  - id: BR4.3
    statement: 止めるは、停止の状態を真にし、同じトランザクションで対象のリフレッシュトークンをすべて無効にする
    category: policy
    applies_to: POST /api/admin/users/{userId}/suspend
    trigger: BR2.1 と BR2.5 を通ったとき
    logic: "C1 setSuspended(userId, true) と C1 revokeAllRefreshTokens(userId) を同じトランザクションで呼ぶ（対象の有無は BR3.1 で確かめた後に呼ぶ。いない ID では呼ばない）。失敗回数とロックの状態は変えない。止める操作とトークンの更新が重なったときの隙は塞がない（M8 B）。止めた理由は記録しない"
    violation: —
    source: FR3.1、FR3.3、ADR-003、C1、M8 B、AC3.1.1、AC3.1.10
  - id: BR4.4
    statement: 停止を解くは、停止の状態だけを偽にし、無効にしたトークンを戻さない
    category: policy
    applies_to: POST /api/admin/users/{userId}/resume
    trigger: BR2.1 と BR2.5 を通ったとき
    logic: "C1 setSuspended(userId, false) だけを呼ぶ。リフレッシュトークン・ロックの状態には触れない。止める前のリフレッシュトークンは使えないまま（止める前のアクセストークンの扱いは U1 の入口の決まりのとおり）"
    violation: —
    source: FR3.1、FR3.3、FR3.5、AC3.1.2
  - id: BR4.5
    statement: 失敗回数を戻すは、ロックの状態の行を排他して戻せるかを判定し、戻せるときだけ失敗回数を 0・解除の予定の時刻を無しに書く
    category: policy
    applies_to: POST /api/admin/users/{userId}/reset-login-failures（C8 の auth.service の2段の口）
    trigger: 対象がいると確かめた後
    logic: "1段目: 対象のロックの状態の行を排他つきで読む。IF 上限切れ THEN Busy。IF 行が無い、または失敗回数が 0 THEN 戻せない（変えるものが無い）。ELSE 戻せる。行が無くても行を作らない。2段目（BR2.5 を通った後、同じトランザクション）: 明示の更新の問い合わせ1回で、失敗回数 0・解除の予定の時刻 無し を書く（回数だけを 0 にしてロックが残る形にしない）。戻せるかの定義は BR1.7 の resettable と同じ。自分自身と停止中の利用者にも許し、停止の状態は変えない。戻した後のログインは、しきい値（設定の値、既定 5）まで失敗できる"
    violation: 戻せなければ 409 USER_ADMIN_NO_CHANGE、上限切れは 409 USER_ADMIN_BUSY
    source: FR5.1〜FR5.4、M2 B、AC4.1.1〜AC4.1.4、AC4.1.7、AC4.1.8、AC4.1.10、Q4 A、Q5 A、要点 9
  - id: BR4.6
    statement: 5つの操作は、それぞれ業務処理の1つのトランザクションで行い、成功は本文なしの 204 で返す
    category: constraint
    applies_to: useradmin.service の5つの操作
    trigger: 操作の要求
    logic: "トランザクションの境界は useradmin.service にだけ置き、C1・C8 の書き換えの口は呼び出し元のトランザクションに入る（外から呼ばれたら想定外の誤り）。排他・判定・確かめ直し・書き換え・監査の出来事の発行を1つのトランザクションの中で行う。成功の応答は本文なしの 204。画面は成功・失敗とも一覧を読み直す"
    violation: —
    source: C1、C3（Q2 A）、C8、TP の Code Style、要点 13

  # --- BR5 氏名と言語の変更（PUT /api/admin/users/{userId}/profile、C8 updateProfile）
  - id: BR5.1
    statement: 氏名と言語は、自分のプリファレンスの保存と同じ規則で検証する
    category: validation
    applies_to: 氏名と言語の変更の要求
    trigger: 要求を受けたとき（対象の有無より前）
    logic: "氏名は前後の Unicode の空白を除いた後で、空なら REQUIRED、254 コードポイントを超えれば TOO_LONG、制御文字・書式の文字があれば INVALID_CHARACTER。言語は小文字の ja か en に完全に一致しなければ INVALID_VALUE（JA・fr などは誤り）。項目が無い・null は REQUIRED。誤りがあれば、項目の名前（displayName・language）と理由だけを載せた 400 VALIDATION_FAILED にし、入れた値を載せず、値を変えず、監査に残さない。既存の DisplayName・Language の判定をそのまま使う"
    violation: 400 VALIDATION_FAILED（fieldErrors）
    source: FR6.2、AC5.1.4、要点 10
  - id: BR5.2
    statement: 検証を通った氏名と言語だけを書き換え、対象がいなければ対象がいないとし、同じ値でも成功とする
    category: policy
    applies_to: C8 updateProfile
    trigger: BR5.1 を通ったとき
    logic: "前後の空白を除いた氏名と言語の2つの列だけを更新の問い合わせで書く（テーマ・文字の大きさ・メールアドレス・パスワード・印・停止は書かない）。IF 更新した行が 0 THEN 404 USER_NOT_FOUND。ELSE 204（今と同じ値でも成功）。行の排他はせず、同時の変更は後に確定したものが勝つ（自分のプリファレンスの保存と同じ）。自分自身も変えられる。操作した人の確かめ直しはしない（Q5 A）。言語は利用者の言語として保存し、その利用者の次の画面の読み直し・ログイン・トークンの更新から当たる"
    violation: 404 USER_NOT_FOUND
    source: FR6.1、FR6.3、FR6.5、FR6.6、AC5.1.1、AC5.1.5、Q5 A、要点 10
  - id: BR5.3
    statement: 氏名と言語の変更は、成功も失敗も監査に残さない
    category: policy
    applies_to: 氏名と言語の変更
    trigger: 変更の要求（成功・入力の誤り・対象がいない）
    logic: "業務処理は監査の出来事を出さない"
    violation: —
    source: FR6.4、FR7.3、AC5.1.1、AC5.1.4、AC5.1.5
  - id: BR5.4
    statement: 氏名と言語の変更の要求の本文は displayName と language だけを読み、知らない項目を無視する
    category: authorization
    applies_to: ProfileRequest
    trigger: 要求の本文の結び付け
    logic: "要求の型に displayName と language の2つだけを持ち、メールアドレス・パスワード・テーマ・文字の大きさ・管理者の印・停止の状態・失敗回数などの知らない項目は 400 にせず無視する。この API から印・停止・失敗回数を変えて、監査と最後の管理者の保護をすり抜けられない"
    violation: —（無視する）
    source: FR6.5、FR8.3、AC5.1.6

  # --- BR6 監査（C6、ADR-006）
  - id: BR6.1
    statement: 5つの操作は、業務の判定に届いた要求ごとに監査の出来事を1件出し、成功か、理由つきの失敗として残す
    category: policy
    applies_to: 5つの操作の監査の出来事（UserAdminAuditEvent）
    trigger: 業務処理が成功・業務の拒否（BR2.1）・確かめ直しの拒否（BR2.6）の結果を決めたとき
    logic: "出来事の種類は操作ごとに 印を付ける USER_ADMIN_GRANTED・印を外す USER_ADMIN_REVOKED・止める USER_SUSPENDED・停止を解く USER_RESUMED・失敗回数を戻す LOGIN_FAILURES_RESET。成功も失敗も同じ種類で、結果で分ける。失敗の理由は 対象がいない USER_NOT_FOUND（既存の値）・自分自身 SELF_OPERATION・対象が停止中 TARGET_SUSPENDED・変えるものが無い NO_CHANGE・最後の有効な管理者 LAST_ACTIVE_ADMIN・確かめ直しで外れていた NOT_ADMIN（既存の値）。名前はどれも 32 文字以内（最長 20 文字）。応答の code と監査の理由は同じ判定の結果から作る"
    violation: —
    source: FR7.1、FR7.2、FR7.5、C6、Q6 A、PM の Mandated、要点 11
  - id: BR6.2
    statement: 監査の行には、操作した人・要求の利用者 ID・結果・理由・送り手の情報だけを残す
    category: constraint
    applies_to: UserAdminAuditEvent と、それを受けて作る監査の行
    trigger: BR6.1 の出来事
    logic: "操作した人は要求の文脈の利用者 ID（BR2.8）。対象は要求の userId をそのまま（いない ID でも。target_user_id は参照の制約を持たない）。日時は注入した時計。送り手の情報（IP・User-Agent・トレースID）は既存の形。メールアドレス（enteredEmail は空）・氏名・検索の文字・失敗回数・パスワード・ハッシュ値・トークンを入れない"
    violation: —
    source: FR7.4、FR7.6、C6 の never_recorded、AC2.1.5、AC3.1.6、AC4.1.6
  - id: BR6.3
    statement: 監査の出来事は業務のトランザクションの中で出し、確定の後に記録され、監査の書き込みの失敗で操作を失敗させない
    category: constraint
    applies_to: 監査の出来事の発行と記録
    trigger: BR6.1
    logic: "出来事は業務処理のトランザクションの中で出す。AuditLog が確定の後に別のトランザクションで1行追記する（既存の仕組み）。拒否のときも BR2.4 のとおり書き込みなしで確定させるため、失敗の行が残る。監査の書き込みが失敗しても応答は変えない（既存の AuditWriteFailureIT の決まり。失敗はアプリのログに ERROR）。AuditLog は useradmin.domain の出来事の型を受け、既存の種類と理由の列挙に写す"
    violation: —
    source: ADR-006、C6、stories.md の監査の読み方、要点 5・11
  - id: BR6.4
    statement: 一覧の閲覧・入力の誤り・氏名と言語の変更・排他の待ちの上限切れ・認可の入口の 401 と 403 では、管理の操作の監査を残さない
    category: policy
    applies_to: 管理の操作の監査
    trigger: それぞれの要求
    logic: "これらでは UserAdminAuditEvent を出さない。管理者でない利用者の 403 は既存のアクセスの拒否（ACCESS_DENIED・NOT_ADMIN）として認可の入口が残す。停止中の管理者の 401 は U1 の入口の決まりのとおり"
    violation: —
    source: FR6.4、FR7.3、C6 の not_recorded、Q7 A、AC2.1.14、AC3.1.12、AC4.1.12

  # --- BR7 認可・改ざん・漏えい・構造
  - id: BR7.1
    statement: 足す7つの API は既存の決まりで管理者だけに許し、未認証は 401、管理者でないは 403 とする
    category: authorization
    applies_to: /api/admin/users の下の一覧と6つの変更の API
    trigger: すべての要求
    logic: "既存の AccessControl（要求ごとに内部DB の管理者の印を見る）をそのまま使う。未認証・無効なトークン・停止中の利用者は 401 AUTHENTICATION_REQUIRED、管理者でない利用者は 403 ACCESS_DENIED（監査にアクセスの拒否が残る）、有効な管理者は業務処理に進む。401・403 のときは業務処理を呼ばず、対象の状態は変わらない。画面で隠すことをこの判定の代わりにしない"
    violation: 401 AUTHENTICATION_REQUIRED、403 ACCESS_DENIED
    source: FR8.2、FR3.8、NFR1、PM の Mandated、AC1.1.13、AC2.1.14、AC3.1.12、AC4.1.12、AC5.1.8
  - id: BR7.2
    statement: 管理の API の外からは、要求の本文で管理者の印・停止・失敗回数を変えられない
    category: authorization
    applies_to: 管理の API の外（/api/me・/api/me/preferences・/api/me/password など）
    trigger: 管理の API の外への要求
    logic: "それらの要求の型に印・停止・失敗回数の項目を持たず、本文に入れても無視される（/api/me/preferences は今までどおり 200）。印を変える C8 setAdmin・停止を変える C1 setSuspended・失敗回数を戻す C8 の口は、useradmin の5つの操作からだけ呼ぶ（BR7.3）"
    violation: —（無視する）
    source: FR8.3、TP の Testing Posture の要求の改ざん、AC2.1.7
  - id: BR7.3
    statement: 印・停止・失敗回数を書き換える口を呼ぶのは UserAdministration の操作だけとする
    category: constraint
    applies_to: C8 setAdmin・resetLoginFailures の2段の口、C1 setSuspended・revokeAllRefreshTokens
    trigger: 設計と実装
    logic: "これらの口を呼ぶのは useradmin.service だけにし、ほかの機能から呼ばない。境界テストで確かめる（BR7.6）"
    violation: —（境界テストの失敗）
    source: C1、C8、FR8.3
  - id: BR7.4
    statement: 個人に関する値と検索の文字を、アプリのログ・トレースの属性・監査に出さない
    category: constraint
    applies_to: useradmin と、U3 が user・auth に足す型と口
    trigger: すべての受け渡し
    logic: "SearchText・UserAdminSummary・AdminUser・ProfileRequest は、文字列にするとメールアドレス・氏名・検索の文字を伏せる（TraceAspect が web・service・domain・repository の層の引数と戻り値を TRACE で文字列にするため）。検索の文字は controller の引数の時点から SearchText で受ける（BR1.3）。URL の問い合わせの部分は既存の仕組みがトレースの属性とアクセスの拒否の監査のパスから除く。業務のログを出すときは利用者 ID と区分だけをキーと値で出す。TRACE を有効にした漏えいのテスト（UserAdminSecretLeakIT）で、7つの API すべてと q を付けた要求で確かめる"
    violation: —
    source: NFR3、PM の Forbidden、TP の Code Style、AC1.1.6、要点 12
  - id: BR7.5
    statement: 応答には、パスワードのハッシュ値・招待のトークンのハッシュ値・失敗回数の生の値・ダミーの行の値・トークンを含めない
    category: constraint
    applies_to: 7つの API の応答（成功・失敗とも）
    trigger: 応答を作るとき
    logic: "一覧の行は BR1.8 の項目だけで、決めた項目のほかを持たない。操作の成功は本文なし。エラー応答は既存の Problem Details で、説明文（ja・en）に対象の利用者のメールアドレス・氏名・ID を載せず、内部の例外のメッセージを載せない"
    violation: —
    source: FR1.6、PM の Forbidden（API の応答）、C3、AC1.1.3、AC1.1.6
  - id: BR7.6
    statement: 新しいパッケージ useradmin の依存の向きを境界テストで固定する
    category: constraint
    applies_to: パッケージ useradmin（web・service・domain）
    trigger: 設計と実装
    logic: "useradmin が依存してよいのは、user と auth の service の口と値の型、common.paging、common（エラー・送り手の情報などの共通の部品）、access.domain の AccessProblemTypes（ACCESS_DENIED の定義を使うためだけ、Q6 A）。invitation・dsl・dslmanage には依存しない。useradmin に依存してよいのは audit だけで、audit からは監査の出来事の型だけ。user は auth を知らないまま（利用者とロックの状態を合わせるのは useradmin）。新しい境界テスト UserAdminBoundaryArchitectureTest を足し、既存の ArchitectureTest とほかの機能の境界テストは書き換えず緩めない"
    violation: —（境界テストの失敗）
    source: ADR-001、NFR11、TP の Code Style、Q6 A、要点 1
```

## 決まりの一覧

| 群 | ID | 要点 |
|---|---|---|
| 一覧と検索 | BR1.1〜BR1.9 | 登録した日時の古い順・同じ日時は ID 順・20 件、停止中と初期管理者を含み招待中は含まない、q は SearchText で受け変換は包むだけ、前後の空白を除き 254 コードポイントまで・空なら全件、小文字にそろえた部分一致で % と _ と \ は文字どおり、件数と行は同じ読み取りのトランザクション、ロックは3つの判定の結果だけ、応答の11項目と self、閲覧は監査なし |
| 拒否の判定 | BR2.1〜BR2.8 | 判定の順と最初の理由1つ、操作ごとの当てはまり、code と状態の写し、拒否は書き込みなしで確定、5つの操作は判定の後・書き換えの前に操作した人を確かめ直す、外れたら 403 ACCESS_DENIED と NOT_ADMIN の監査、ID の形の誤りは 400・いなければ 404、操作した人は認証の主体から |
| 排他と最後の管理者 | BR3.1〜BR3.6 | 印の操作と止めるは管理者の行と対象の行を ID 順に排他、有効な管理者は印あり・停止なしでロック中も数える、停止を解くは対象の行だけ、失敗回数を戻すはロックの状態の行だけ（2種類を同時に排他しない）、上限切れは 409 BUSY で監査なし、待ち合わせの口2か所 |
| 5つの操作の実行 | BR4.1〜BR4.6 | 印はトークンに触れない、止めるはトークンをまとめて無効にしロックを変えない、解くはトークンを戻さない、失敗回数は2段で戻し行を作らない、1つのトランザクションで成功は 204 |
| 氏名と言語 | BR5.1〜BR5.4 | プリファレンスと同じ検証、2つの列だけを後勝ちで書き同じ値も成功・いなければ 404、監査なし、知らない項目は無視 |
| 監査 | BR6.1〜BR6.4 | 種類5つ・理由6つ（足すのは4つ）、操作した人・要求の ID・結果・理由・送り手だけ、確定の後に記録し書き込みの失敗で操作を失敗させない、残さないもの |
| 認可・改ざん・漏えい・構造 | BR7.1〜BR7.6 | 401・403・2xx、管理の API の外から変えられない、書き換えの口は useradmin だけ、伏せ字の型、応答に含めない値、境界テスト |
