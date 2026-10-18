# 業務の決まり — U2 u2-businessdata-write

出典の表記: FR・NFR は要件、AC は `stories.md` の受け入れ基準、SL・SM・SQ・X はストーリーの決定と要件との差、ADR は Domain Design、C は契約、Q はこの段の答え（`functional-design-questions.md`、Q1〜Q8: A）、RC は承認の場（2026-10-18）の Request Changes の直し（R-01〜R-03、U1 R-03・R-05）、TP は `team.md` の Testing Posture と Code Style、PM は `project.md` の Mandated・Forbidden、U1-BR は U1 の `rules.md` の決まりである。

承認の場の直しで変えた・足した決まり: BR1.8・BR6.2・BR6.4（足した）・BR9.3・BR9.4・BR9.5・BR11.3・BR11.6（足した）・BR14.1（足した）・BR14.2（足した）。BR7.1 と BR9.3 は、U1 R-05・R-03 の直した後の形を確定として書いた。

```yaml
rules:
  # ── BR1 判定の順と code ──
  - id: BR1.1
    statement: "1つの書き込みの要求が複数の拒否に当たるときは、決めた順で最初に当たったものを返す"
    category: policy
    applies_to: "作成・更新・削除の API"
    trigger: "要求を受けたとき"
    logic: "① 401 → ② 404 BUSINESS_DSL_NOT_APPLIED → ③ 404 BUSINESS_TABLE_NOT_FOUND → ④ 403 ACCESS_DENIED（テーブルが NONE）→ ⑤ 409 BUSINESS_DEFINITION_CHANGED → ⑥ 409 BUSINESS_ROW_OPERATION_NOT_ALLOWED（主キー・ビューの決まり）→ ⑦ 403 ACCESS_DENIED（CREATE・DELETE が不可、FULL のカラムが1つも無い）→ ⑧ 409 BUSINESS_WRITE_NOT_CONFIGURED → ⑨ 400 VALIDATION_FAILED → ⑩ 対象DB の結果（BR1.8）。①〜④ は U1 の accessFor を使う"
    violation: "最初に当たった拒否の code"
    source: [ADR-006, SL, AC3.3.10, AC3.3.6, U1-BR1.1]
  - id: BR1.2
    statement: "DSL が適用されていないときは 404 BUSINESS_DSL_NOT_APPLIED、写しの DSL に無いスキーマ・テーブルは 404 BUSINESS_TABLE_NOT_FOUND を返す"
    category: validation
    applies_to: "作成・更新・削除の API"
    trigger: "判定の順の②③"
    logic: "U1 の BR1.2・BR1.3 と同じ。名前は写しの DSL のスキーマの一覧・テーブルの一覧と、種類ごとに別々に文字どおり照らす（U1 R-05 の直した後の形。RC で確定）"
    violation: "404。監査に残さない"
    source: [FR13.1, FR13.2, AC3.3.1, AC3.3.3, AC3.1.10, AC3.6.1, RC]
  - id: BR1.3
    statement: "テーブルの実効の主権限が NONE なら、行の有無にかかわらず 403 ACCESS_DENIED を返す"
    category: authorization
    applies_to: "作成・更新・削除の API"
    trigger: "判定の順の④"
    logic: "U1 の BR1.4 と同じ。主キーの値を照らす前に返す"
    violation: "403 ACCESS_DENIED。監査に残さない"
    source: [FR2.1, FR13.1, AC3.3.2, AC3.3.6, AC3.5.2, AC3.1.10]
  - id: BR1.4
    statement: "主キーとビューの決まりで行えない書き込みは 409 BUSINESS_ROW_OPERATION_NOT_ALLOWED を返し、補助権限と FULL の不足より先に判定する"
    category: authorization
    applies_to: "作成（ビュー）・更新・削除"
    trigger: "判定の順の⑥"
    logic: "IF 許す操作の集合（U1 の BR2.3）に要求の操作が無く、その理由が主キーが無い・主キーのカラムに NONE がある・ビュー THEN 409。構造の理由は権限によらず同じ答えにするため、⑦ の 403 より先に置く。監査の量の見積もり（主権限が READ だけの人の繰り返し）は承認の場の R-04 として NFR 要件の段で扱う"
    violation: "409。監査に失敗として残す（監査の理由の値は BUSINESS_ROW_OP_NOT_ALLOWED。BR11.3・BR11.6）"
    source: [FR9.1, FR9.2, X4, AC3.2.1, AC3.2.2, AC3.2.3, AC3.2.4]
  - id: BR1.5
    statement: "要求の definitionId が今の定義と違えば、行を変えずに 409 BUSINESS_DEFINITION_CHANGED を返す"
    category: policy
    applies_to: "作成・更新・削除の API"
    trigger: "判定の順の⑤"
    logic: "dslHash と作業ロールの ID の組を今と比べる。違えば対象DB に触れずに拒否する。テーブルが NONE なら ④ の 403 が先（権限の変化を漏らさない）"
    violation: "409。監査に残さない（Q6: A）"
    source: [C3, Q6, FR2.5, AC3.1.3, AC3.1.4]
  - id: BR1.6
    statement: "CREATE・DELETE が不可、または更新できる FULL のカラムが1つも無いときは 403 ACCESS_DENIED を返す"
    category: authorization
    applies_to: "作成・更新・削除の API"
    trigger: "判定の順の⑦"
    logic: "IF 作成で CREATE が不可・削除で DELETE が不可・更新で editableOnUpdate のカラムが無い THEN 403。カラムごとの書けない理由は 403 にせず BR2.1 の 400 にする（C1 の decide）"
    violation: "403 ACCESS_DENIED。監査に残さない"
    source: [FR2.3, AC2.1.2, AC2.2.6, AC2.3.3, AC3.1.2, AC3.1.3, AC3.1.4, AC2.2.13]
  - id: BR1.7
    statement: "書き込みの接続の設定が無いときは 409 BUSINESS_WRITE_NOT_CONFIGURED を返す"
    category: policy
    applies_to: "作成・更新・削除の API"
    trigger: "判定の順の⑧"
    logic: "権限の確かめ（④〜⑦）を通った要求だけに返す。権限の無い人には 403 のまま。読み取り（U1）は動き続け、U1 の定義は writeConfigured: false を返す"
    violation: "409。監査に残さない（Q6: A）"
    source: [X14, Q6, AC3.3.9]
  - id: BR1.8
    statement: "対象DB の結果による拒否は、決めた順で code を分ける"
    category: policy
    applies_to: "書き込みの口の中と、その直後"
    trigger: "判定の順の⑩"
    logic: "接続を借りられない（UNAVAILABLE）→ 表・カラムが無い（MISMATCH）→ 一意の事前の問い合わせで重なり（DUPLICATE_VALUE）→ DB の制約の違反（BR6。違反のカラムの確かめが失敗・時間切れなら BR6.4 の SAVE_REJECTED）→ 当たった行が 0（ROW_NOT_FOUND）・2 以上（ROW_NOT_UNIQUE）→ 書き込みの文を送った後の時間切れ・通信の断絶（TIMEOUT）。実際に起きた順に最初の1つを返す"
    violation: "BR1.9 の code"
    source: [ADR-006, SL, AC3.3.4, AC3.3.8, AC2.2.4, AC2.3.5, AC3.2.6, RC]
  - id: BR1.9
    statement: "業務データの書き込みの code は機能の一覧に置き、1つの code に状態コードを1つだけ割り当てる"
    category: constraint
    applies_to: "BusinessDataProblemTypes・BusinessDataProblemTypeCatalog"
    trigger: "起動時"
    logic: "足す code: BUSINESS_DEFINITION_CHANGED 409、BUSINESS_WRITE_NOT_CONFIGURED 409、BUSINESS_DUPLICATE_VALUE 409、BUSINESS_REFERENCE_NOT_FOUND 409、BUSINESS_SAVE_REJECTED 409。使い回す code: U1 の BUSINESS_DSL_NOT_APPLIED 404・BUSINESS_TABLE_NOT_FOUND 404・BUSINESS_ROW_NOT_FOUND 404・BUSINESS_ROW_OPERATION_NOT_ALLOWED 409・BUSINESS_ROW_NOT_UNIQUE 409・BUSINESS_TARGET_DB_MISMATCH 409・BUSINESS_TARGET_DB_UNAVAILABLE 503・BUSINESS_TARGET_DB_TIMEOUT 503、共通の ACCESS_DENIED 403・VALIDATION_FAILED 400。応答の code は監査の理由の値とは別の列挙で、字数の縛りは無い（監査の側は BR11.6）"
    violation: "—"
    source: [TP, C3, Q6, U1-BR1.9, RC]

  # ── BR2 書けるカラム ──
  - id: BR2.1
    statement: "本文に書けないカラムを入れたら、理由を見分けられない同じ 400 で拒否する"
    category: authorization
    applies_to: "作成・更新の values と、更新・削除の key"
    trigger: "判定の順の⑨"
    logic: "values のカラムは、作成なら editableOnCreate、更新なら editableOnUpdate（U1 の BR2.4）が真のものだけを受け付ける。NONE・READ・DSL に無い・detail.visible で隠す・BINARY・OTHER・更新の主キーのカラムは、どれも fieldErrors {field: 要求に入っていた名前, reason: NOT_ALLOWED} の 400 VALIDATION_FAILED。key は主キーのカラムをちょうどそろえ、欠けと余りは KEY_MISMATCH"
    violation: "400 VALIDATION_FAILED。行は変わらず、監査に残さない"
    source: [FR2.2, FR2.4, FR7.3, X5, AC2.1.7, AC2.2.5, AC2.2.7, AC3.1.6, AC3.1.1, AC3.6.1]
  - id: BR2.2
    statement: "作成では、送られたカラムだけを入れ、送られないカラムは対象DB の既定値に任せる"
    category: policy
    applies_to: "作成"
    trigger: "INSERT を組み立てるとき"
    logic: "INSERT のカラムは values のカラムだけ。値が無いカラムは INSERT に入れない。ただし BR3.3 の必須の判定を先に当てる"
    violation: "—"
    source: [FR6.4, X9, AC2.1.9, AC2.1.8]
  - id: BR2.3
    statement: "更新では、送られたカラム（画面で変えたカラム）だけを変え、ほかのカラムは DB の値のまま残す"
    category: policy
    applies_to: "更新"
    trigger: "UPDATE を組み立てるとき"
    logic: "UPDATE の SET は values のカラムだけ。同時の更新は後から保存した人の値で上書きし、読んだときの値と照らさない（カラムごとの後勝ち）"
    violation: "—"
    source: [FR7.2, SM1, X8, AC2.2.2, AC2.2.3, AC2.2.8, AC2.2.11]
  - id: BR2.4
    statement: "変えるカラムが1つも無い更新の要求は 400 で拒否する"
    category: validation
    applies_to: "更新"
    trigger: "判定の順の⑨"
    logic: "IF values が空 THEN 400 VALIDATION_FAILED（fieldErrors {field: 空, reason: NO_CHANGE}）。今と同じ値を送った更新は比べずに保存し、成功として監査に残す"
    violation: "400。監査に残さない（Q7: A）"
    source: [Q7, AC2.2.9]

  # ── BR3 値の検証 ──
  - id: BR3.1
    statement: "作成・更新の値はサーバーで検証し、サーバーの結果を正とする。誤りはまとめてカラムごとに返す"
    category: validation
    applies_to: "values"
    trigger: "判定の順の⑨"
    logic: "BR3.2〜BR3.8 と BR4 をすべて当て、誤りのあるカラムごとに FieldViolation を1件返す（1カラムで最初に当たった1つ）。画面の検証を通ったかを前提にしない"
    violation: "400 VALIDATION_FAILED"
    source: [FR10.1, FR10.5, AC2.4.1, AC2.4.6]
  - id: BR3.2
    statement: "値はカラムの型として読めなければ入力の誤りにする"
    category: validation
    applies_to: "values"
    trigger: "検証のとき"
    logic: "INTEGER・BIG_INTEGER は整数の文字、DECIMAL は指数を含まない10進の文字、BOOLEAN は true・false、DATE・DATETIME・TIME は実在の日時の ISO の形（2026-02-30 は誤り）、時差つきの型は時差つきの ISO の形。文字の型でないカラムに空の文字列が来たら INVALID_VALUE。文字の型に null が来たら、nullable なら NULL、そうでなければ NOT_NULL"
    violation: "400（INVALID_VALUE・NOT_NULL）"
    source: [SM2, C3, AC2.1.3, AC2.2.10, X5]
  - id: BR3.3
    statement: "必須のカラムは、空や欠けを誤りにする。作成で送られない DB 由来の必須のカラムも誤りにする"
    category: validation
    applies_to: "values と、作成で送られないカラム"
    trigger: "検証のとき"
    logic: "更新で送られた必須のカラムが null か空の文字列なら REQUIRED。作成では、DSL の required が DB 由来（origin DB。NOT NULL で既定値が無い）で editableOnCreate のカラムが送られなければ REQUIRED。人が足した required（origin MANUAL）も同じ。DB の既定値に任せるのは required の無いカラムだけ（ストーリーの X9 を狭める読み方。承認の場で確かめる）。入力できないカラム（NONE・READ）の NOT NULL は、DB が拒んだときに BR6.3 で扱う"
    violation: "400 REQUIRED"
    source: [FR10.1, FR6.4, A2, X9, AC2.4.1, AC2.4.8, AC2.1.5]
  - id: BR3.4
    statement: "文字の長さはコードポイントで数え、範囲の両端を含めて当てる"
    category: validation
    applies_to: "STRING のカラム"
    trigger: "検証のとき"
    logic: "長さの上限は DSL の maxLength と DB の長さの小さい方。サロゲートペアと絵文字は1文字。上限ちょうどは受け付け、1つ超えたら TOO_LONG。minLength に足りなければ TOO_SHORT"
    violation: "400（TOO_LONG・TOO_SHORT）"
    source: [FR10.1, AC2.4.2]
  - id: BR3.5
    statement: "数の桁と範囲は DB が丸めない・切り詰めない形で確かめる"
    category: validation
    applies_to: "INTEGER・BIG_INTEGER・DECIMAL のカラム"
    trigger: "検証のとき"
    logic: "DECIMAL(p,s) は小数の桁が s を超えたら TOO_MANY_DIGITS（1.234 は誤り）、整数部が p−s を超えたら TOO_MANY_DIGITS（1000.00・1234.5 は誤り）。整数の型は DB の型の範囲を超えたら OUT_OF_RANGE（BIGINT の上限と下限ちょうどは受け付け）。DSL の min・max は両端を含み、外れたら OUT_OF_RANGE"
    violation: "400（TOO_MANY_DIGITS・OUT_OF_RANGE）"
    source: [FR10.1, AC2.4.2, AC3.6.5]
  - id: BR3.6
    statement: "DSL の検証は、カラムの型に合うものだけを当てる"
    category: policy
    applies_to: "ColumnWriteRules"
    trigger: "検証の材料を作るとき"
    logic: "minLength・maxLength・pattern は STRING だけ。min・max は INTEGER・BIG_INTEGER・DECIMAL・DATE・DATETIME・DATETIME_TZ・TIME・TIME_TZ だけ。precision・scale は DECIMAL だけ。required・unique はすべての型。表に無い組み合わせ（文字の型への min など）は当てず、定義の API の項目でも null にする（BR14.2）。U1 の R-01 の演算子と型の表と同じ考え方"
    violation: "—"
    source: [FR10.1, U1-R-01]
  - id: BR3.7
    statement: "フォーム部品の選択肢と真偽値は、決まった値だけを受け付ける"
    category: validation
    applies_to: "select・radio・checkbox・switch のカラム"
    trigger: "検証のとき"
    logic: "select・radio は FIXED の選択肢の値だけ（ほかは NOT_A_CHOICE）。checkbox・switch は true・false だけ（ほかは INVALID_VALUE）。REFERENCE・LOOKUP の選択肢を持つカラムは text として扱い、選択肢と照らさない"
    violation: "400（NOT_A_CHOICE・INVALID_VALUE）"
    source: [FR6.1, FR6.2, AC2.1.3]
  - id: BR3.8
    statement: "検証を通った値は、型つきの値にしてから対象DB に渡す"
    category: calculation
    applies_to: "ColumnValueCodec"
    trigger: "検証の後"
    logic: "型の対応は ColumnValueCodec の1か所で決める（U1 の BR7.1 と同じ表）。数は桁を保つ型、日付・日時は日時の型、真偽値は真偽値、時差つきの型は UTC として読んで渡す。文字のまま渡して DB に型を決めさせない（stringtype=unspecified に書き込みは頼らない）"
    violation: "—"
    source: [Q3, U1-R-02, SM7, AC3.6.5, AC2.2.8]

  # ── BR4 pattern ──
  - id: BR4.1
    statement: "pattern は値の全体が式に合うときだけ通し、サーバーだけで当てる"
    category: validation
    applies_to: "STRING のカラムで DSL に pattern があるもの"
    trigger: "検証のとき（長さの検証の後）"
    logic: "IF 値の全体が式に合わない THEN PATTERN_MISMATCH。DSL に message（ja・en）があれば FieldViolation に付ける。式そのものは応答に出さない（定義の API には hasPattern だけを出す。BR14.1）。null と空の文字列には当てない（必須は BR3.3）"
    violation: "400 PATTERN_MISMATCH"
    source: [FR10.1, SM4, X1, Q8, AC2.4.1]
  - id: BR4.2
    statement: "pattern の照合には時間の上限を置き、超えたら検証の誤りにする"
    category: constraint
    applies_to: "pattern の照合"
    trigger: "照合のとき"
    logic: "照合の中で文字を読むたびに締め切りを見て打ち切り、別のスレッドを残さない。締め切りは注入した時計で決める。IF 上限を超えた THEN PATTERN_TIMEOUT。上限の値と余裕は NFR 要件の段で決める"
    violation: "400 PATTERN_TIMEOUT。要求は上限と余裕の中で終わる"
    source: [FR10.4, ADR-007, Q8, AC2.4.5, TP]

  # ── BR5 一意 ──
  - id: BR5.1
    statement: "DSL の unique のカラムは、保存の前に同じ値のほかの行が無いかを問い合わせる"
    category: validation
    applies_to: "作成・更新で送られた unique のカラム"
    trigger: "書き込みの口の中、書き込みの文の前"
    logic: "書き込みと同じトランザクションの中で、同じ値の行を1件まで探す。更新では自分の行（主キー）を除く。値が NULL なら探さない。同じかどうかは対象DB の照合順序に任せる。見つかれば FieldViolation {field: カラム, reason: DUPLICATE} を付けて拒否する"
    violation: "409 BUSINESS_DUPLICATE_VALUE。巻き戻して監査に失敗として残す"
    source: [FR10.2, AC2.4.3, AC2.5.2]
  - id: BR5.2
    statement: "DB の一意の制約の違反も、同じ「同じ値がある」の誤りにする"
    category: policy
    applies_to: "書き込みの文"
    trigger: "対象DB が一意の制約で拒んだとき"
    logic: "BR6.1 で一意の違反と分かれば BUSINESS_DUPLICATE_VALUE。カラムは BR6.2 で決め、決まらなければ field を空にする（DSL で unique でないカラムの制約の違反を含む）。確かめの問い合わせそのものが失敗・時間切れなら BR6.4。同時の作成で一方が負けたときも 500 にしない"
    violation: "409 BUSINESS_DUPLICATE_VALUE。監査に失敗として残す"
    source: [FR10.2, F1, AC2.4.4, AC2.5.1, AC2.5.2]
  - id: BR5.3
    statement: "DB に一意の制約が無いカラムで、同時の書き込みが重なった値を入れうることは既知の制約とする"
    category: policy
    applies_to: "unique のカラム"
    trigger: "—"
    logic: "事前の問い合わせだけでは同時の重なりを防げない。防ぐ仕組みを足さず、要件の制約（FR10.3）として記録する"
    violation: "—"
    source: [FR10.3, F1]

  # ── BR6 DB の制約の違反 ──
  - id: BR6.1
    statement: "DB の制約の違反は、例外の文を読まずに、誤りの番号で種類を分ける"
    category: policy
    applies_to: "RowSqlExecutor の書き込み"
    trigger: "書き込みの文が拒まれたとき"
    logic: "PostgreSQL は SQLState（23505 一意・23503 外部キー・23502 NOT NULL・22001 長さ・22003 桁あふれ）。MySQL・MariaDB は SQLState が 23000 を共有するため業者の番号（1062 一意・1452 と 1451 外部キー・1048 と 1364 NOT NULL・1406 長さ・1264 桁あふれ）。ほかの番号は想定外の失敗"
    violation: "BR5.2・BR6.3 の code。想定外は 500（例外の文はログにも出さない）"
    source: [FR6.2a, ADR-007, Q5, AC2.1.6, AC2.5.1]
  - id: BR6.2
    statement: "違反したカラムは、書き込みの口が貸している同じ接続の中で、巻き戻した後に DSL の定義から問い合わせで決める。決まらなければフォーム全体の誤りにする"
    category: calculation
    applies_to: "一意・外部キーの違反"
    trigger: "BR6.1 で一意・外部キーの違反と分けた後"
    logic: "処理は口に「巻き戻した後に確かめる」を返す（BR9.3）。口は書き込みのトランザクションを巻き戻し、接続を返さずに同じ接続で確かめの問い合わせ（読み取りだけ）を行い、終わったらその問い合わせのトランザクションも巻き戻して接続を返す。確かめは口の外に出ず、対象DB の接続は1本のまま（BR9.4）。一意: 送った値のうち DSL で unique のカラムを、BR5.1 と同じ問い合わせで確かめる。外部キー: 送ったカラムを含む DSL の外部キーごとに、参照先の行の有無を確かめる。候補がちょうど1つならそのカラム、0 か 2 以上なら field を空にする。3種類の DB で同じ結果にする。同じ接続のまま2つ目のトランザクションを専用の管理者で続けられるかは NFR 設計の試しで確かめ、続けられなければ書き込みの文の直前のセーブポイントまで巻き戻して確かめ、その後に全体を巻き戻す形に切り替える（観測できる振る舞いは同じ）"
    violation: "—"
    source: [FR6.2a, X16, Q5, AC2.1.6, RC]
  - id: BR6.3
    statement: "外部キー・NOT NULL・長さ・桁の違反は、業務の言葉の code で返す"
    category: policy
    applies_to: "書き込みの文"
    trigger: "BR6.1 で分けた後"
    logic: "作成・更新の外部キーの違反（参照先に無い値）は BUSINESS_REFERENCE_NOT_FOUND（REFERENCE_NOT_FOUND）。削除の外部キーの違反（ほかの表から参照されている）は BUSINESS_SAVE_REJECTED。NOT NULL は BUSINESS_SAVE_REJECTED で、入力できないカラムが原因なら reason NOT_EDITABLE_NOT_NULL（利用者には直せない）、入力できるカラムなら NOT_NULL。長さ・桁の違反は BUSINESS_SAVE_REJECTED"
    violation: "409。監査に失敗として残す"
    source: [FR6.2a, FR6.4, FR8.2, A2, AC2.1.5, AC2.1.6, AC2.3.4, AC2.4.7]
  - id: BR6.4
    statement: "違反のカラムの確かめの問い合わせが失敗・時間切れになったら、カラムを示さず 409 BUSINESS_SAVE_REJECTED（フォーム全体）にする"
    category: policy
    applies_to: "BR6.2 の確かめの問い合わせ"
    trigger: "確かめの問い合わせが JDBC の例外か時間切れで終わったとき"
    logic: "書き込みはすでに巻き戻してあるため、保存されていないことは確か。field を空にした FieldViolation だけを付け、応答は 409 BUSINESS_SAVE_REJECTED。一意・外部キーの code（DUPLICATE_VALUE・REFERENCE_NOT_FOUND）にはしない。確かめの例外の文は読まず、SQLState・業者の番号・例外のクラスの名前だけをログに出す（BR12.2）。時間の上限は書き込みの時間の上限（BR9.5）"
    violation: "409 BUSINESS_SAVE_REJECTED。監査に失敗として残す（理由の値 BUSINESS_SAVE_REJECTED）"
    source: [FR6.2a, Q5, AC2.1.6, AC2.5.1, RC]

  # ── BR7 SQL ──
  - id: BR7.1
    statement: "書き込みの SQL は RowSqlExecutor の書き込みの側だけが組み立てて実行する"
    category: constraint
    applies_to: "RowSqlExecutor"
    trigger: "作成・更新・削除と、違反の後の確かめ"
    logic: "作成は送ったカラムだけの INSERT、更新は送ったカラムだけを SET し主キーで行を指す UPDATE、削除は主キーで行を指す DELETE。事前の一意の問い合わせと違反の後の確かめの問い合わせも同じ部品で組み立てる。名前は写しの DSL のスキーマ・テーブル・カラムの種類ごとの一覧と別々に照らしてから SqlIdentifiers.quoteKnown で引用し（U1 R-05 の直した後の形）、テーブルはスキーマ名で修飾する。値は BR3.8 の型つきの値をすべてバインドで渡す"
    violation: "—"
    source: [NFR4.1, PM, ADR-001, U1-BR3.2, U1-BR3.3, AC3.6.2, AC3.6.3, RC]
  - id: BR7.2
    statement: "作成した行の主キーの値を、自動の採番を含めて返す"
    category: calculation
    applies_to: "作成"
    trigger: "INSERT の後"
    logic: "主キーのカラムが values にあればその値、無ければ DB が生成した鍵の値を読む。DETAIL が許されるときだけ createdKey にして返し、そうでなければ空。作成の失敗で主キーが決まらないときは、監査の key を空にする"
    violation: "—"
    source: [FR6.5, AC2.1.8, AC2.1.4]

  # ── BR8 件数 ──
  - id: BR8.1
    statement: "更新・削除で当たった行が1行でなければ巻き戻す"
    category: constraint
    applies_to: "更新・削除"
    trigger: "UPDATE・DELETE の後"
    logic: "IF 件数が 0 THEN 巻き戻して 404 BUSINESS_ROW_NOT_FOUND。IF 件数が 2 以上 THEN 巻き戻して 409 BUSINESS_ROW_NOT_UNIQUE。どちらも監査に失敗として残す。外部キーの ON DELETE CASCADE で消えたほかの表の行は数えず、監査にも残さない"
    violation: "404・409"
    source: [SM8, X2, FR7.4, FR8.2, AC2.2.4, AC2.3.5, AC2.3.6, AC3.2.6]
  - id: BR8.2
    statement: "件数は「一致した行」で数え、今と同じ値の更新も1行と数える"
    category: constraint
    applies_to: "書き込みの接続の設定"
    trigger: "接続を作るとき"
    logic: "MySQL・MariaDB のドライバーの設定で、件数を変わった行ではなく一致した行にする（useAffectedRows を真にしない）ことを書き込みの接続で固定する。PostgreSQL はもとから一致した行。3種類の DB の結合テストで確かめる"
    violation: "—"
    source: [Domain-Design-R-04, Q7, AC2.2.3]

  # ── BR9 接続とトランザクション ──
  - id: BR9.1
    statement: "書き込みの接続の設定は、読み取りの接続先を使い、書き込みの資格情報とプールと時間の上限だけを足す"
    category: constraint
    applies_to: "targetdb の設定"
    trigger: "起動時"
    logic: "種類・ホスト・番号・DB の名前・スキーマは読み取りの設定をそのまま使う。書き込みのユーザー名・パスワード・プールの大きさ・書き込みの時間の上限を環境変数（.env）だけから受け取る。読み取りの設定が使えて、書き込みのユーザー名とパスワードの両方があるときだけ書き込みを「設定がある」とする。片方だけなら項目の名前だけを WARN で1回出し、書き込みは「設定が無い」。どちらの場合も起動を続け、読み取りは動く"
    violation: "書き込みは BR1.7"
    source: [Q1, ADR-008, PM, NFR4.3, AC3.3.9]
  - id: BR9.2
    statement: "書き込みの接続と専用のトランザクションの管理者は、既定の候補にしない名前つきの部品にする"
    category: constraint
    applies_to: "targetdb の設定"
    trigger: "起動時"
    logic: "書き込みの接続は読み取り専用にしない別のプールにする。専用のトランザクションの管理者はその接続だけを扱う。どちらも型で注入される内部DB の接続とトランザクションの管理者の候補にしない。ドライバーの通信の待ちは書き込みの時間の上限に余裕を足して決める"
    violation: "—"
    source: [Q2, ADR-008, Domain-Design-R-01]
  - id: BR9.3
    statement: "targetdb の書き込みの口が接続を貸し、処理の答えに従って確定か巻き戻しをし、巻き戻した後の確かめまで同じ接続で行ってから閉じる"
    category: constraint
    applies_to: "targetdb.service の書き込みの口"
    trigger: "作成・更新・削除"
    logic: "業務処理の層は、処理を渡して口を1回呼ぶ。口は書き込みの接続を1回だけ借り、専用の管理者でトランザクションを始めて処理に接続を貸す。処理の答えは3つ: 確定 → 確定して閉じる。巻き戻し → 巻き戻して閉じる。巻き戻した後に確かめる → 巻き戻し、同じ接続で確かめの処理（読み取りだけ）を行い、それも巻き戻して閉じる（BR6.2）。例外で終わったら巻き戻して閉じる。接続を戻り値で渡さず、口の外へ持ち出させない。読み取りの口（U1）と同じく貸す形（U1 R-03 の直した後の形。RC で確定）。businessdata は targetdb の設定のクラスに依存しない"
    violation: "—"
    source: [Q2, U1-R-03, C6, TP, RC]
  - id: BR9.4
    statement: "書き込みの API は書き込みのアカウントの接続を1要求で1本だけ使い、内部DB の接続を持ったまま対象DB を待たない"
    category: constraint
    applies_to: "業務処理の層"
    trigger: "作成・更新・削除"
    logic: "権限の写しは解決の口の短い読み取りで取り終えてから書き込みの口を呼ぶ。業務処理の層は内部DB のトランザクションを開かない。1要求で使う接続は、内部DB が写しの読み取りの分、対象DB が書き込みの接続1本（事前の問い合わせ・書き込みの文・違反の後の確かめの問い合わせをすべて含む。口を2回呼ばない）。数は NFR 要件・NFR 設計で確かめる"
    violation: "結合テストが落ちる"
    source: [TP, NFR4.3, AC3.6.6, AC3.6.7, RC]
  - id: BR9.5
    statement: "対象DB への書き込みには、書き込みの目的の時間の上限を付ける"
    category: constraint
    applies_to: "RowSqlExecutor の書き込み"
    trigger: "文を送るとき"
    logic: "事前の問い合わせ・書き込みの文・違反の後の確かめの問い合わせのそれぞれに、書き込みの時間の上限を付ける。確かめの問い合わせが上限を超えたら BR6.4（書き込みの文の時間切れの BR10.1 の TIMEOUT にはしない。書き込みはすでに巻き戻してあるため）。値と、1要求の最悪の時間（文の数 × 上限）は NFR 要件の段で決める"
    violation: "書き込みの文と事前の問い合わせは BR10.1 の TIMEOUT、確かめの問い合わせは BR6.4"
    source: [NFR4.4, TP, AC3.3.4, RC]

  # ── BR10 失敗の分け方 ──
  - id: BR10.1
    statement: "対象DB の失敗は、保存されたかが分かるかどうかで分ける"
    category: policy
    applies_to: "書き込みの口"
    trigger: "JDBC の例外を受けたとき"
    logic: "接続を借りられない・書き込みの文を送る前の失敗 → BUSINESS_TARGET_DB_UNAVAILABLE（保存されていない）。書き込みの文を送った後の時間切れ・通信の断絶・確定の失敗 → BUSINESS_TARGET_DB_TIMEOUT（確定したか分からない）。未定義の表・カラムの SQLState（U1 の BR8.5 と同じ）→ BUSINESS_TARGET_DB_MISMATCH。制約の違反は BR6。巻き戻した後の確かめの失敗は BR6.4。ほかは想定外の失敗。例外の文は読まない。TIMEOUT の監査の結果の読み方（確定の有無が不明）は承認の場の R-05 として NFR 設計の段で扱う"
    violation: "503・409。監査に失敗として残す"
    source: [FR13.3, SQ6, X6, AC3.3.4, AC3.3.5, AC3.3.8, AC3.4.3]

  # ── BR11 監査 ──
  - id: BR11.1
    statement: "監査の出来事は、書き込みの口から戻った後（対象DB の接続を返した後）に出す"
    category: constraint
    applies_to: "業務処理の層"
    trigger: "作成・更新・削除の終わり"
    logic: "対象DB のトランザクションの中で出来事を出さない（確定の後の受け取りが対象DB のトランザクションに結び付き、巻き戻した失敗の監査が消えるため）。違反の後の確かめも口の中で終わってから出す。AuditEventListener は fallbackExecution で受け、自分の短いトランザクションで内部DB に書く"
    violation: "—"
    source: [ADR-003, FR12.3, Q2]
  - id: BR11.2
    statement: "作成・更新・削除の成功を監査に残す"
    category: policy
    applies_to: "BusinessDataChangedEvent"
    trigger: "確定の後"
    logic: "種類は BUSINESS_ROW_CREATED・BUSINESS_ROW_UPDATED・BUSINESS_ROW_DELETED。操作した人・作業ロール・スキーマ・テーブル・結果・主キーの値（主キーの無いテーブルの作成では無し）を残す。主キー以外の値と更新の前後の値は残さない"
    violation: "—"
    source: [FR12.1, FR12.2, Q4, AC3.4.1, AC2.1.1, AC2.2.2, AC2.3.2]
  - id: BR11.3
    statement: "業務の失敗と対象DB の失敗を監査に残し、401・403・400 と対象DB に触れる前の拒否は残さない"
    category: policy
    applies_to: "BusinessDataChangedEvent"
    trigger: "拒否・失敗のとき"
    logic: "残す（応答の code → failure_reason の値。写しは BR11.6）: 409 BUSINESS_ROW_OPERATION_NOT_ALLOWED → BUSINESS_ROW_OP_NOT_ALLOWED、404 BUSINESS_ROW_NOT_FOUND・409 BUSINESS_ROW_NOT_UNIQUE・409 BUSINESS_DUPLICATE_VALUE・409 BUSINESS_REFERENCE_NOT_FOUND・409 BUSINESS_SAVE_REJECTED・503 BUSINESS_TARGET_DB_UNAVAILABLE・503 BUSINESS_TARGET_DB_TIMEOUT・409 BUSINESS_TARGET_DB_MISMATCH → 同じ名前の値。残さない: 401・403・400、BUSINESS_DSL_NOT_APPLIED・BUSINESS_TABLE_NOT_FOUND の 404、BUSINESS_DEFINITION_CHANGED・BUSINESS_WRITE_NOT_CONFIGURED"
    violation: "—"
    source: [FR12.4, SQ6, X6, Q6, AC3.4.2, AC3.4.3, AC3.4.4, AC2.5.2, AC2.1.5, AC2.1.6, AC2.3.4, RC]
  - id: BR11.4
    statement: "監査の行は足した3つの列と detail の JSON に置き、長い主キーは切り詰めたと分かる形で残す"
    category: calculation
    applies_to: "BusinessDataAuditRow"
    trigger: "監査の行を作るとき"
    logic: "スキーマ・テーブル・作業ロールは足した列に置く。主キーは detail に {key: {カラム: 値}, keyTruncated: 偽} で置く。detail が上限（16,384 字）を超えるなら、値を後ろから切って上限に収め、keyTruncated を真にする（黙って切らない）"
    violation: "—"
    source: [Q4, X19, AC3.4.1, AC3.4.7]
  - id: BR11.5
    statement: "監査の書き込みに失敗しても操作は成功のままとし、失敗のログに主キーの値と行の値を出さない"
    category: policy
    applies_to: "AuditEventListener"
    trigger: "監査の書き込みの失敗"
    logic: "今の扱い（操作の応答を変えず、ERROR を1回出す）を保つ。業務データの出来事では、ERROR の項目から detail と主キーの値を外し、主キーのカラムの数と keyTruncated だけを出す（今の失敗のログは detail を含む全項目を出すため、本体を直す）"
    violation: "—"
    source: [FR12.3, PM, Q4, AC3.4.6, AC3.4.8]
  - id: BR11.6
    statement: "監査の理由の値は応答の code とは別の列挙にし、すべて 32 字以内に収め、単体テストで確かめる"
    category: constraint
    applies_to: "AuditFailureReason・AuditEventType・応答の code から監査の理由の値への写し"
    trigger: "起動時・単体テスト"
    logic: "failure_reason と event_type の列は VARCHAR(32)（V4。AuditEvent の @Column length = 32、@Enumerated(STRING)）で、列は広げない。足す監査の理由の値は BUSINESS_ROW_OP_NOT_ALLOWED（27 字）・BUSINESS_ROW_NOT_FOUND（22）・BUSINESS_ROW_NOT_UNIQUE（23）・BUSINESS_DUPLICATE_VALUE（24）・BUSINESS_REFERENCE_NOT_FOUND（28）・BUSINESS_SAVE_REJECTED（22）・BUSINESS_TARGET_DB_UNAVAILABLE（30）・BUSINESS_TARGET_DB_TIMEOUT（26）・BUSINESS_TARGET_DB_MISMATCH（27）。足す種類は BUSINESS_ROW_CREATED・BUSINESS_ROW_UPDATED・BUSINESS_ROW_DELETED（どれも 20）。応答の code から理由の値への写しは businessdata の1か所の表（switch で場合を尽くす）で決め、名前が同じことに頼らない。単体テストは AuditFailureReason と AuditEventType のすべての値（既存の値を含む）の name() が 32 字以内であることと、監査に残す応答の code のすべてに写しがあることを確かめる（列挙に値を足し忘れても、長い値を足しても落ちる）"
    violation: "単体テストが落ちる。実装でこの決まりが破れると監査の書き込みが失敗し、ERROR だけが残って⑥などの監査が消える"
    source: [RC, FR12.1, FR12.4, AC3.4.2, AC3.4.3, AC3.4.4, TP]

  # ── BR12 漏えい ──
  - id: BR12.1
    statement: "行の値・主キーの値を持つ型は、文字列にするときに値を伏せる"
    category: constraint
    applies_to: "WriteRequest・WriteValues・TypedValue・RowKey・BusinessDataChangedEvent"
    trigger: "文字列にするとき"
    logic: "toString はカラムの名前と件数だけを出す（TraceAspect が web・service・domain・repository の引数と戻り値を TRACE に出すため）。行を汎用の型のまま層をまたいで渡さない"
    violation: "*SecretLeakIT が落ちる"
    source: [TP, PM, NFR4.2, AC3.6.8]
  - id: BR12.2
    statement: "JDBC の例外のメッセージは、応答・アプリのログ・監査のどれにも出さない"
    category: constraint
    applies_to: "RowSqlExecutor・書き込みの口"
    trigger: "JDBC の例外を受けたとき（違反の後の確かめの問い合わせを含む）"
    logic: "受けた所で SQLState・業者の番号・例外のクラスの名前だけを出す。例外の連なりを、TraceAspect の対象の層の外へそのまま出さない"
    violation: "*SecretLeakIT が落ちる"
    source: [PM, FR13.3, AC2.1.5, AC2.3.4, AC2.5.1, AC3.3.4, AC3.4.8]
  - id: BR12.3
    statement: "誤りの応答に、入力した値・主キーの値・接続先を入れない"
    category: constraint
    applies_to: "Problem Details（detail・instance・fieldErrors）"
    trigger: "拒否を返すとき"
    logic: "fieldErrors は field と reason と（pattern の）DSL の message だけ。detail は決まった文だけ。道は固定で値を含まない"
    violation: "—"
    source: [PM, C3, AC3.6.9, AC3.6.8]

  # ── BR13 構造と設定 ──
  - id: BR13.1
    statement: "書き込みの API 3本を「ログインだけ」に分類する"
    category: authorization
    applies_to: "BusinessDataWriteController"
    trigger: "起動時・構造の検査"
    logic: "POST /api/tables/rows・PATCH /api/tables/rows・POST /api/tables/rows/delete に AUTHENTICATED を付け、既存の API の分類の網羅のテストに載せる。権限は業務処理の層でテーブルごとに判定する。停止中の利用者は既存の認証の段で拒まれる"
    violation: "分類の網羅のテストが落ちる"
    source: [K-61, C3, X1, AC3.5.1, AC3.1.2]
  - id: BR13.2
    statement: "既存の境界テストは緩めずに、書き込みの側を足す"
    category: constraint
    applies_to: "BusinessDataBoundaryArchitectureTest・TargetDbBoundaryArchitectureTest・AuditBoundaryArchitectureTest"
    trigger: "構造の検査"
    logic: "SQL を実行してよいのは RowSqlExecutor だけ（書き込みと確かめの問い合わせにも当てる）。targetdb の中では書き込みの文を発行せず、@Transactional を使わない（管理者の部品を置くことは許す）。audit から businessdata.domain への依存は今の audit の境界テストで止められていないため、緩める変更は要らない"
    violation: "テストが落ちる"
    source: [ADR-001, C5, C6, TP]
  - id: BR13.3
    statement: "SpotBugs の SQL_ の除外を、RowSqlExecutor の書き込みのメソッドとパターン名に絞って広げる"
    category: constraint
    applies_to: "backend/config/spotbugs-exclude.xml"
    trigger: "コード生成"
    logic: "クラス・メソッド・パターン名の3つで絞り、理由（名前は DSL の照合を通ったものだけ）を書く。広げる変更はコード生成の計画に明記して依頼者の承認を得る"
    violation: "—"
    source: [TP, Units-Generation-R-04, U1-BR11.4]
  - id: BR13.4
    statement: "見本の対象DB に、DSL の対象のスキーマの行の操作だけを許す書き込みのアカウントを足す"
    category: constraint
    applies_to: "docker/targetdb の初期化・.env.example・.env.targetdb.example・README"
    trigger: "見本の対象DB の初めての起動"
    logic: "PostgreSQL はスキーマの使用、表の SELECT・INSERT・UPDATE・DELETE、自動の採番のための並びの使用だけを与え、作成の権限を与えない。MySQL・MariaDB は対象のデータベースの SELECT・INSERT・UPDATE・DELETE だけを与える。パスワードは .env.targetdb から受け取る。初期化は初めての起動だけ動くため、既存のボリュームへの足し方を README に書く。書き込みのアカウントで表を作る・消す操作は DB が拒む。MySQL・MariaDB の付与を DSL の対象のテーブルに絞れるかと、絞れないときに残る広さの書き方は、承認の場の R-06 として基盤の設計の段で扱う"
    violation: "—"
    source: [PM, NFR4.3, ADR-008, Domain-Design-R-04, AC3.6.7]
  - id: BR13.5
    statement: "要求の本文の大きさの上限は、既存の仕組みで書き込みの道ごとに決める"
    category: constraint
    applies_to: "書き込みの API の本文"
    trigger: "要求を受けたとき"
    logic: "既定は今の上限（1MB）。足りないときは道ごとの上限の決まりを足して上げ、共通の部品の本体は変えない。値は NFR 要件の段で決める"
    violation: "既存の 413 の応答"
    source: [NFR2, TP]

  # ── BR14 定義の API に足す検証の材料（R-02） ──
  - id: BR14.1
    statement: "定義の API のカラムの項目に、U2 が検証の材料を足し、画面はそこから読む"
    category: policy
    applies_to: "POST /api/tables/definition の listColumns・detailColumns の ColumnDefinition（U1 の型に置いた余地）"
    trigger: "定義の API の応答を作るとき"
    logic: "足す項目: formPart・required・nullable・minLength・maxLength・precision・scale・min・max・choices（value・label）・hasPattern。U1（B2）は読み取りの項目を返し、U2（B4）がこの項目を同じ型の余地に入れる。U1 の読み取りの項目と、定義の API の道・判定・応答の他の項目は変えない。nullable は U1 の読み取りの項目と同じ値で、重ねて持たない。式そのもの・unique・requiredOrigin は出さない。U3 は作成・更新の画面の部品と画面の検証をこの項目だけから作る（U3 Q1: A）"
    violation: "—"
    source: [RC, C2, FR10.1, FR6.1, AC2.4.1, AC2.4.8, AC2.1.3]
  - id: BR14.2
    statement: "定義の API の検証の材料は、サーバーの検証と同じ ColumnWriteRules から写し、別に計算しない"
    category: constraint
    applies_to: "ColumnWriteRules → ColumnDefinitionWriteItems"
    trigger: "定義の API の応答を作るとき"
    logic: "要求1件の写しの DSL から作った ColumnWriteRules を、検証（BR3・BR4）と定義の項目の両方に使う。型に当たらない項目は BR3.6 の表のとおり null にする。作成でも更新でも書けないカラムは formPart 以外を既定（null・偽・空）にする。画面の検証はサーバーの検証の代わりにしない（BR3.1）。定義と検証の材料が同じであることは、同じ DSL から作った定義の項目とサーバーの検証の境界の値を突き合わせる単体テストで確かめる"
    violation: "単体テストが落ちる"
    source: [RC, FR10.1, FR10.5, AC2.4.1, AC2.4.8, U1-R-01]
```

## 要約の表

| 群 | 決まり | 要点 |
|---|---|---|
| 判定の順と code | BR1.1〜BR1.9 | 401 → 404 → 403 NONE → 409 定義が古い → 409 主キー・ビュー → 403 補助権限・FULL → 409 書き込みの設定が無い → 400 → 対象DB の結果。新しい code 5つは、どれも 409。応答の code は監査の理由の値とは別の列挙 |
| 書けるカラム | BR2.1〜BR2.4 | 書けないカラムは同じ 400 `NOT_ALLOWED`。作成は送ったカラムだけ、更新は変えたカラムだけ。空の更新は 400 |
| 値の検証 | BR3.1〜BR3.8 | サーバーが正。型・必須（DB 由来の必須は作成でも誤り）・長さ（コードポイント）・桁と範囲（丸めない）・型に合う検証だけ・選択肢・型つきで渡す |
| pattern | BR4.1〜BR4.2 | 全体一致。照合を締め切りで打ち切り、`PATTERN_TIMEOUT` |
| 一意 | BR5.1〜BR5.3 | 同じトランザクションで事前に問い合わせる。制約の違反も同じ code。同時の重なりは既知の制約 |
| 制約の違反 | BR6.1〜BR6.4 | 誤りの番号で種類を分ける。違反のカラムは、口が貸している同じ接続で巻き戻した後に確かめる。確かめが失敗・時間切れならフォーム全体の `BUSINESS_SAVE_REJECTED` |
| SQL | BR7.1〜BR7.2 | RowSqlExecutor だけ。名前は種類ごとに照らして引用し、値は型つきでバインド。作成した主キーを返す |
| 件数 | BR8.1〜BR8.2 | 1行でなければ巻き戻す。件数は一致した行で数える |
| 接続とトランザクション | BR9.1〜BR9.5 | 接続先は読み取りと同じ、資格情報は別。名前つきの別の部品。口が1回だけ借りて貸し、確定・巻き戻し・巻き戻した後の確かめを同じ接続で行って閉じる。対象DB の接続は1要求で1本。時間の上限は文ごと |
| 失敗の分け方 | BR10.1 | 送る前は UNAVAILABLE、送った後は TIMEOUT（確定したか分からない）、確かめの失敗は BR6.4 |
| 監査 | BR11.1〜BR11.6 | 口から戻った後に出す。残すものと残さないもの。3つの列と `detail`。失敗のログに主キーを出さない。理由の値は 32 字以内の別の列挙（`BUSINESS_ROW_OP_NOT_ALLOWED`）で、単体テストで確かめる |
| 漏えい | BR12.1〜BR12.3 | 伏せ字、JDBC のメッセージを出さない、誤りの応答に値を入れない |
| 構造と設定 | BR13.1〜BR13.5 | API の分類、境界テストを緩めない、SpotBugs の除外、見本の書き込みのアカウント、本文の上限 |
| 定義の API の検証の材料 | BR14.1〜BR14.2 | U2 が定義のカラムの項目に検証の材料を足す（U1 の型の余地）。サーバーの検証と同じ材料から写す |

## 監査の理由の値の字数（R-01 の確かめ）

列 `failure_reason` は VARCHAR(32)。既存の `AuditFailureReason` の28の値と、足す9つの値の字数を並べた。すべて 32 字以内である。

| 区分 | 値（字数） |
|---|---|
| 既存 | `USER_NOT_FOUND`（14）・`PASSWORD_MISMATCH`（17）・`ACCOUNT_LOCKED`（14）・`ACCOUNT_SUSPENDED`（17）・`NOT_ADMIN`（9）・`TOKEN_MISSING`（13）・`TOKEN_MALFORMED`（15）・`TOKEN_INVALID`（13）・`CURRENT_PASSWORD_MISMATCH`（25）・`INVITATION_EXPIRED`（18）・`INVITATION_ALREADY_USED`（23）・`INVITATION_CANCELLED`（20）・`INVITATION_NOT_FOUND`（20）・`EMAIL_ALREADY_REGISTERED`（24）・`SELF_OPERATION`（14）・`TARGET_SUSPENDED`（16）・`NO_CHANGE`（9）・`LAST_ACTIVE_ADMIN`（17）・`GROUP_NOT_FOUND`（15）・`GROUP_NAME_DUPLICATE`（20）・`GROUP_IN_USE`（12）・`ROLE_NOT_FOUND`（14）・`ROLE_NAME_DUPLICATE`（19）・`ROLE_IN_USE`（11）・`PERMISSION_TARGET_NOT_IN_DSL`（28）・`DSL_NOT_APPLIED`（15）・`ROLE_NOT_ASSIGNED`（17）・`ROLE_TRANSFER_STALE`（19） |
| 足す | `BUSINESS_ROW_OP_NOT_ALLOWED`（27）・`BUSINESS_ROW_NOT_FOUND`（22）・`BUSINESS_ROW_NOT_UNIQUE`（23）・`BUSINESS_DUPLICATE_VALUE`（24）・`BUSINESS_REFERENCE_NOT_FOUND`（28）・`BUSINESS_SAVE_REJECTED`（22）・`BUSINESS_TARGET_DB_UNAVAILABLE`（30）・`BUSINESS_TARGET_DB_TIMEOUT`（26）・`BUSINESS_TARGET_DB_MISMATCH`（27） |
| 使わない名前 | `BUSINESS_ROW_OPERATION_NOT_ALLOWED`（34。応答の code だけに使い、監査の値にしない） |

既存の値は `backend/src/main/java/cherry/mastersmith/audit/domain/AuditFailureReason.java` の 2026-10-18 の時点の定義から数えた。足す種類（`event_type`、VARCHAR(32)）の3つは、どれも 20 字である。
