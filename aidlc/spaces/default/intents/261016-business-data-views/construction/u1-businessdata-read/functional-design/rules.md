# 業務の決まり — U1 u1-businessdata-read

出典の表記: FR・NFR は `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`、AC・SL・SM・SQ は `inception/user-stories/stories.md`、ADR は `inception/domain-design/decisions.md`、C1〜C7・CQ は `inception/contract-design/contract-summary.md`・`contract-design-questions.md`、Q1〜Q8 はこの段の `functional-design-questions.md`、RC-R01〜RC-R06・RC-U2R02 は同じファイルの Requested Changes Feedback（承認の場の Request Changes、2026-10-18）、TP は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。

code の名前と状態コードは、この段で確定した（契約で仮だったもの）。1つの code に状態コードは1つだけで、code は一度決めたら変えない（TP の Code Style）。上限の数値（一覧と詳細の文字数・時間の上限）は NFR 要件の段で決める。

承認の場の Request Changes で足した決まりは BR2.8・BR4.9・BR4.10・BR7.8・BR7.9・BR8.6・BR9.2 である。直した決まりは BR1.1・BR1.3・BR1.6・BR3.3・BR3.4・BR3.5・BR4.1・BR4.3〜BR4.7・BR5.6・BR6.2・BR7.1・BR7.2・BR7.7・BR8.1・BR8.3〜BR8.5・BR9.1・BR10.1・BR10.3・BR11.4 である。指摘ごとの対応は `functional-spec.md` の 10節を正とする。ID は変えず、前の版の ID の意味も変えていない。

```yaml
rules:
  # ── BR1 判定の順と code ──
  - id: BR1.1
    statement: "1つの要求が複数の拒否に当たるときは、決めた順で最初に当たったものを返す"
    category: policy
    applies_to: "定義・一覧と検索・詳細の API"
    trigger: "要求を受けたとき"
    logic: "① 未認証 401 → ② DSL が適用されていない 404 → ③ DSL に無いスキーマかテーブル 404 → ④ テーブルの主権限が NONE 403 → ⑤ 主キーとビューの決まり 409（詳細だけ）→ ⑥ 入力の誤り 400 → ⑦ 行が無い 404 → ⑧ 対象DB とうまくいかないとき・行が1つに決まらない（BR1.8 の順）。⑧ の中で DB が値の型の誤りを返したときだけは、⑥ と同じ 400 に写す（BR8.6。値は先に型として確かめるため、通常は起きない）"
    violation: "後ろの段の判定を先に返すことはしない（たとえば NONE のテーブルで入力が誤っていても 403）"
    source: [ADR-006, SL, AC3.3.10, AC3.3.6, RC-R01]
  - id: BR1.2
    statement: "DSL が適用されていないときは 404 BUSINESS_DSL_NOT_APPLIED を返す"
    category: policy
    applies_to: "読み取りの API すべて"
    trigger: "権限の写しが DSL を持たないとき"
    logic: "IF 写しの DSL が無い THEN 404 BUSINESS_DSL_NOT_APPLIED"
    violation: "404 BUSINESS_DSL_NOT_APPLIED"
    source: [FR13.2, AC3.3.3]
  - id: BR1.3
    statement: "写しの DSL に無いスキーマ名・テーブル名は 404 BUSINESS_TABLE_NOT_FOUND を返す"
    category: validation
    applies_to: "要求の本文の schema・table"
    trigger: "名前を DSL と照らしたとき"
    logic: "IF スキーマ名が写しの DSL のスキーマの一覧と、テーブル名がそのスキーマのテーブルの一覧と、文字どおり一致しない THEN 404 BUSINESS_TABLE_NOT_FOUND（引用符・;・改行などを含む名前も同じ）"
    violation: "404 BUSINESS_TABLE_NOT_FOUND。対象DB に問い合わせない"
    source: [FR13.1, AC3.3.1, AC1.1.4, AC1.1.7, AC3.1.10, AC3.6.1, Q1, RC-R05]
  - id: BR1.4
    statement: "テーブルの実効の主権限が NONE なら、行の有無にかかわらず 403 ACCESS_DENIED を返す"
    category: authorization
    applies_to: "読み取りの API すべて"
    trigger: "テーブルを照らした後"
    logic: "IF テーブルの主権限が NONE THEN 403 ACCESS_DENIED（共通の code を ProblemTypeRegistry から code で引く。access に依存しない）"
    violation: "403 ACCESS_DENIED。対象DB に問い合わせない"
    source: [FR1.3, FR2.3, FR13.1, AC3.3.2, AC3.3.6, AC1.1.3, AC1.1.5, AC1.1.8, AC3.1.2, AC3.1.5, AC3.5.2]
  - id: BR1.5
    statement: "主キーとビューの決まりで詳細が許されないときは 409 BUSINESS_ROW_OPERATION_NOT_ALLOWED を返す"
    category: constraint
    applies_to: "詳細の API"
    trigger: "テーブルの主権限が READ 以上のとき"
    logic: "IF 許す操作（BR2.3）に DETAIL が無い THEN 409 BUSINESS_ROW_OPERATION_NOT_ALLOWED（主キーの値を照らす前に返す）"
    violation: "409。監査に残さない（読み取り）"
    source: [FR9.1, ADR-006, SL, AC1.4.4, AC3.2.1, AC3.2.2, AC3.2.3]
  - id: BR1.6
    statement: "入力の誤りは 400 VALIDATION_FAILED で返し、カラムに関わる誤りは fieldErrors に同じ形で並べる"
    category: validation
    applies_to: "一覧と検索・詳細の本文"
    trigger: "本文を確かめたとき"
    logic: "IF 誤りがある THEN 400 VALIDATION_FAILED。fieldErrors は {field: 要求に入っていた名前, reason}。reason は NOT_ALLOWED（NONE・DSL に無い・検索や並べ替えに使えないカラム・BR4.9 の表に無い演算子と型の組。どれも同じ値）・INVALID_VALUE（型として読めない）・RANGE_REVERSED（下限が上限より大きい）・NOT_A_CHOICE（選択肢に無い）・KEY_MISMATCH（主キーの欠けと余り）。page・size の誤りは、既存の一覧と同じく fieldErrors を付けない。400 には definitionChanged も付ける（BR9.2）"
    violation: "400 VALIDATION_FAILED（共通の code を使い回す）"
    source: [FR2.4, FR3.5, C2, AC1.2.5, AC1.2.9, AC1.2.14, AC1.3.6, AC3.1.6, AC3.6.9]
  - id: BR1.7
    statement: "主キーで指した行が無いときは 404 BUSINESS_ROW_NOT_FOUND を返す"
    category: policy
    applies_to: "詳細の API"
    trigger: "対象DB を読んで 0 行だったとき"
    logic: "IF 当たる行が 0 行 THEN 404 BUSINESS_ROW_NOT_FOUND"
    violation: "404 BUSINESS_ROW_NOT_FOUND"
    source: [FR13.1, AC1.4.3]
  - id: BR1.8
    statement: "対象DB とうまくいかないときは理由ごとの code で返し、その中の順も決める"
    category: policy
    applies_to: "一覧と検索・詳細の API"
    trigger: "対象DB に接続・問い合わせしたとき"
    logic: "実行の順に、接続できない（読み取りの接続の設定が無いときを含む）→ 503 BUSINESS_TARGET_DB_UNAVAILABLE、時間切れ → 503 BUSINESS_TARGET_DB_TIMEOUT、DSL にあるテーブルやカラムが対象DB に無い → 409 BUSINESS_TARGET_DB_MISMATCH、詳細で2行以上が当たった → 409 BUSINESS_ROW_NOT_UNIQUE"
    violation: "その理由の code。500 にしない"
    source: [FR13.3, Q7, AC3.3.4, AC3.3.8, AC3.2.6]
  - id: BR1.9
    statement: "業務データの code は機能の一覧に置き、1つの code に状態コードを1つだけ割り当てる"
    category: constraint
    applies_to: "BusinessDataProblemTypes・BusinessDataProblemTypeCatalog"
    trigger: "起動時に問題の種類を集めるとき"
    logic: "BUSINESS_DSL_NOT_APPLIED 404、BUSINESS_TABLE_NOT_FOUND 404、BUSINESS_ROW_NOT_FOUND 404、BUSINESS_ROW_OPERATION_NOT_ALLOWED 409、BUSINESS_ROW_NOT_UNIQUE 409、BUSINESS_TARGET_DB_MISMATCH 409、BUSINESS_TARGET_DB_UNAVAILABLE 503、BUSINESS_TARGET_DB_TIMEOUT 503。403 と 400 は共通の ACCESS_DENIED・VALIDATION_FAILED を使い回し、重ねて定義しない。U2 は書き込みの code を足し、BUSINESS_TARGET_DB_* を使い回す"
    violation: "起動時の一覧の確かめで重なりがあれば起動を止める（既存の仕組み）"
    source: [FR13.4, TP, C2, Q7]
  - id: BR1.10
    statement: "読み取りの要求とその拒否は監査に残さない"
    category: policy
    applies_to: "定義・一覧と検索・詳細の API"
    trigger: "応答を返すとき"
    logic: "読み取りでは監査の出来事を出さない"
    violation: "—"
    source: [FR12.2, AC3.2.1, AC1.4.4]

  # ── BR2 出し分けと許す操作 ──
  - id: BR2.1
    statement: "主権限が NONE のカラムは、定義・一覧・詳細のどの応答にも名前も値も含めない"
    category: authorization
    applies_to: "TableAccessView・応答"
    trigger: "判定の材料を作るとき"
    logic: "IF カラムの実効の主権限が NONE THEN そのカラムの ColumnAccess を作らない"
    violation: "—（作らないので出ようがない）"
    source: [FR2.2, PM, AC3.1.1, AC1.2.9]
  - id: BR2.2
    statement: "一覧の応答は list.visible のカラムだけ、詳細の応答は detail.visible のカラムだけを含め、行を指す主キーは key で別に返す"
    category: authorization
    applies_to: "一覧・詳細の応答と定義の listColumns・detailColumns"
    trigger: "応答を作るとき"
    logic: "一覧の列は list.visible かつ READ 以上を list.order の順に並べ、幅は list.width。詳細は detail.visible かつ READ 以上。DETAIL が許されるときだけ、主キーのカラムの値を list.visible にかかわらず key に入れる（画面の列には出さない）"
    violation: "—"
    source: [FR2.2a, FR3.1, FR5.1, AC1.1.9, AC1.4.1, AC1.4.5, AC3.1.8]
  - id: BR2.3
    statement: "許す操作の集合は、主キーの有無・ビューか・主キーのカラムの主権限・FULL のカラムの有無・CREATE・DELETE・書き込みの接続の設定から、1つの純粋な関数で求める"
    category: calculation
    applies_to: "TableAccessView.operations"
    trigger: "判定の材料を作るとき"
    logic: "テーブルが READ 以上なら LIST。DETAIL は「主キーがあり（ビューは主キーの設定があり）、主キーのカラムがすべて READ 以上」のとき。CREATE は CREATE が可でビューでなく書き込みの設定があるとき。UPDATE は DETAIL が許され、ビューでなく、更新できる FULL のカラムが1つ以上あり、書き込みの設定があるとき。DELETE は DETAIL が許され、ビューでなく、DELETE が可で、書き込みの設定があるとき（FR9.1 の表）"
    violation: "API はこの結果で受け付け・拒否を判定し、応答の可否と食い違わない"
    source: [FR9.1, FR9.2, FR9.3, ADR-005, AC3.2.4, AC3.1.9, AC3.3.9, AC3.2.1, AC3.2.2, AC3.2.3]
  - id: BR2.4
    statement: "入力できるカラムは detail.visible の FULL のカラムとし、更新では主キーのカラムを除く"
    category: authorization
    applies_to: "ColumnAccess.editableOnCreate・editableOnUpdate"
    trigger: "判定の材料を作るとき"
    logic: "editableOnCreate = detail.visible かつ FULL かつ BINARY・OTHER でない。editableOnUpdate = editableOnCreate かつ主キーでない。値の受け付けは U2 が同じ結果で判定する"
    violation: "—"
    source: [FR2.2, FR7.3, SQ7, AC3.1.1]
  - id: BR2.5
    statement: "出し分けの関数は、権限を足しても出るカラムと許す操作が減らない"
    category: calculation
    applies_to: "BR2.1〜BR2.4 の関数"
    trigger: "性質ベースのテスト（jqwik）"
    logic: "任意の権限と DSL の設定の組で、NONE のカラムが出ない・FULL でないカラムが入力にならない・主キーの無いテーブルと主キーのカラムが NONE のテーブルに DETAIL・UPDATE・DELETE が出ない・ビューに CREATE・UPDATE・DELETE が出ない・権限を上げても減らない。失敗時の乱数の種を記録する"
    violation: "テストが落ちる"
    source: [TP, AC3.1.7, AC3.2.7]
  - id: BR2.6
    statement: "行を見分ける値のカラムはサーバーが決めて定義で返す"
    category: calculation
    applies_to: "TableAccessView.rowLabelColumns"
    trigger: "DETAIL が許されるとき"
    logic: "IF 主キーのカラムがすべて一覧の列（BR2.2）にある THEN 主キーのカラム ELSE 一覧の列のうち list.order が最初のカラム（password・BINARY を除く）。DETAIL が許されなければ空"
    violation: "—（値は行ごとに重なりうる。既知の制約）"
    source: [Q8, AC1.4.7]
  - id: BR2.7
    statement: "行えない操作があるときは、その理由を定義で返す"
    category: calculation
    applies_to: "TableAccessView.restrictionReason"
    trigger: "許す操作を求めた後"
    logic: "ビュー → VIEW、主キーが無い → NO_PRIMARY_KEY、主キーのカラムのどれかが NONE → PRIMARY_KEY_HIDDEN、それ以外は null。どのカラムが NONE かは返さない"
    violation: "—"
    source: [SL, AC3.2.5]
  - id: BR2.8
    statement: "定義の応答のカラムの項目は契約 C2 の ColumnDefinition の形にし、U1 は読み取りの項目を返し、検証の材料は U2 が同じ項目に足す"
    category: policy
    applies_to: "定義の応答の listColumns・detailColumns と ColumnAccess.validation"
    trigger: "定義を返すとき"
    logic: "U1（B2）が返すのは column・displayName・valueType・listFormat・width・editableOnCreate・editableOnUpdate・masked。検証の材料（formPart・required・nullable・minLength・maxLength・precision・scale・min・max・choices・hasPattern）は U2（B4）が ColumnAccess.validation を埋めて同じカラムの項目に足す。U1 は validation を null のままにし、応答にその項目を出さない。U1 の応答の組み立ては、validation があれば項目に写すだけの形にして、U2 が組み立てを書き換えずに済むようにする"
    violation: "—（ColumnDefinition の別の形を作らない）"
    source: [C2, RC-U2R02, AC1.1.9, AC1.5.1]

  # ── BR3 名前の照合と SQL ──
  - id: BR3.1
    statement: "要求1件で権限の写しを1回だけ取り、その写しの DSL を判定と名前の照合の両方に使う"
    category: policy
    applies_to: "業務処理の層"
    trigger: "要求を受けたとき"
    logic: "snapshotFor を1回呼ぶ。DSL は写しが持つものだけを使い（PermissionSnapshot に読み取りだけの口を足す）、DSL の提供口を別に読まない。写しは要求をまたいで持たない。管理者の印は使わない"
    violation: "—"
    source: [FR2.1, FR2.5, FR2.6, C7, Q2, AC1.1.5, AC3.1.4, AC3.1.5]
  - id: BR3.2
    statement: "スキーマ・テーブル・カラムの名前は、写しの DSL と照らしてから SQL に使う"
    category: validation
    applies_to: "RowSqlExecutor に渡す名前"
    trigger: "SQL を組み立てる前"
    logic: "照らす前の名前を SQL の部品に渡さない。照らして無いテーブルは BR1.3、無いカラムは BR1.6 の NOT_ALLOWED"
    violation: "BR1.3・BR1.6"
    source: [NFR4.1, PM, AC3.6.1, AC3.6.4]
  - id: BR3.3
    statement: "名前は既存の SqlIdentifiers.quoteKnown で DB の種類ごとに引用し、名前の一覧はスキーマ・テーブル・カラムの種類ごとに分けて渡す"
    category: constraint
    applies_to: "RowSqlExecutor・KnownIdentifiers"
    trigger: "SQL を組み立てるとき"
    logic: "スキーマ名はスキーマの一覧、テーブル名はそのスキーマのテーブルの一覧、カラム名はそのテーブルのカラムの一覧だけを渡して quoteKnown で引用する（種類をまたいで照合を通さない）。テーブルはスキーマ名で修飾する。引用の入り口を2つにしない。大文字と小文字・空白・予約語の名前も同じ扱い"
    violation: "一覧に無い名前は想定外の失敗（問い合わせをしない）"
    source: [NFR4.1, AC3.6.3, R-02, RC-R05]
  - id: BR3.4
    statement: "検索の値と主キーの値は、すべて型つきのバインド変数で渡す"
    category: constraint
    applies_to: "RowSqlExecutor"
    trigger: "SQL を組み立てるとき"
    logic: "値を文字列として SQL に組み込まない。値は BR4.7・BR6.2 で型として読んだ BoundValue を、BR7.8 の型ごとのセッターでバインドする。引用符・;・--・改行を含む値もその文字として扱う"
    violation: "—"
    source: [NFR4.1, PM, AC3.6.2, RC-R02]
  - id: BR3.5
    statement: "行の SQL を組み立てて実行するのは RowSqlExecutor だけで、DB の種類ごとの差は方言の部品に閉じる"
    category: constraint
    applies_to: "businessdata.repository"
    trigger: "常に"
    logic: "RowDialect（MySQL・MariaDB は同じ、PostgreSQL は別）が NULL の並び・部分一致・型の受け渡しの差（OTHER の渡し方を含む。BR7.7）を持つ。業務処理の層は DB の種類で分岐しない"
    violation: "境界テスト（BR11.2）が落ちる"
    source: [ADR-001, ADR-007, TP, NFR3]

  # ── BR4 検索 ──
  - id: BR4.1
    statement: "検索の条件はすべて AND で組み、検索に使えるカラムだけを受け付ける"
    category: validation
    applies_to: "一覧と検索の本文の conditions"
    trigger: "本文を確かめたとき"
    logic: "検索に使えるのは ColumnAccess.searchOperator があるカラムだけ（BR4.10）。ほかのカラム・同じカラムの重なりは BR1.6 の NOT_ALLOWED"
    violation: "400 VALIDATION_FAILED"
    source: [FR3.2, AC1.2.1, AC1.2.9, AC3.1.6]
  - id: BR4.2
    statement: "EQUALS は値が等しい行だけを選ぶ"
    category: calculation
    applies_to: "searchOperator = EQUALS"
    trigger: "検索のとき"
    logic: "値をカラムの型として読み（BR4.7）、等しい行。値が空なら条件にしない"
    violation: "—"
    source: [FR3.2, AC1.2.1]
  - id: BR4.3
    statement: "CONTAINS は値を含む行を選び、%・_・エスケープの文字を文字として扱う"
    category: calculation
    applies_to: "searchOperator = CONTAINS（STRING のカラムだけ。BR4.9）"
    trigger: "検索のとき"
    logic: "LIKE とエスケープの文字 ! を明示し、値の中の !・%・_ に ! を前置する（バックスラッシュは文字のまま）。3種類の DB で同じ。大文字と小文字は照合順序に任せる。文字の型にだけ使うので、数や日付への暗黙の型変換は起きない"
    violation: "—"
    source: [FR3.4, ADR-007, AC1.2.4, RC-R01]
  - id: BR4.4
    statement: "RANGE は両端を含み、下限・上限のどちらか一方だけでもよい"
    category: validation
    applies_to: "searchOperator = RANGE（数・日付・日時・時刻のカラムだけ。BR4.9）"
    trigger: "検索のとき"
    logic: "IF 下限と上限がどちらもあり、型として比べて下限 > 上限 THEN BR1.6 の RANGE_REVERSED"
    violation: "400 VALIDATION_FAILED"
    source: [FR3.2, SL, AC1.2.2, RC-R01]
  - id: BR4.5
    statement: "CHOICE は選んだ値のどれかに等しい行を選ぶ"
    category: validation
    applies_to: "searchOperator = CHOICE"
    trigger: "検索のとき"
    logic: "固定の選択肢（FIXED）があれば、選んだ値は選択肢の値だけ（ほかは NOT_A_CHOICE）。何も選ばなければ条件にしない。選択肢が無く真偽値の型なら true・false を選択肢として返す。選択肢が無く真偽値でない型は EQUALS に読み替える（BR4.6）"
    violation: "400 VALIDATION_FAILED"
    source: [FR3.2, SL, AC1.2.3, AC1.2.13]
  - id: BR4.6
    statement: "DSL の IN は CHOICE と同じに扱い、REFERENCE・LOOKUP の選択肢を持つカラムと、選択肢が無く真偽値でないカラムの CHOICE・IN は EQUALS に読み替える"
    category: policy
    applies_to: "定義の searchFields"
    trigger: "判定の材料を作るとき（BR4.9 の表を当てる前）"
    logic: "IN → CHOICE（BR4.5）。選択肢が REFERENCE・LOOKUP なら選択肢を出さず EQUALS。選択肢が無く真偽値でないなら EQUALS。読み替えた後の演算子に BR4.9 の表を当てる"
    violation: "—"
    source: [FR3.6, Q3, AC1.2.8]
  - id: BR4.7
    statement: "検索の値はカラムの型として読み、読めない値は入力の誤りにする"
    category: validation
    applies_to: "検索の値"
    trigger: "本文を確かめたとき"
    logic: "INTEGER・BIG_INTEGER・DECIMAL・DATE・DATETIME・DATETIME_TZ・TIME・TIME_TZ・BOOLEAN は ColumnValueCodec で型として読み（BR7.8）、読めなければ INVALID_VALUE。桁あふれ（DB の型の範囲を超える整数など）も INVALID_VALUE。STRING・OTHER は文字のまま。読んだ値（BoundValue）をそのままバインドに使い、読み直さない。どの型のカラムでどの演算子を使えるかは BR4.9"
    violation: "400 VALIDATION_FAILED"
    source: [FR3.5, SL, SM3, AC1.2.5, AC1.2.14, RC-R01, RC-R02]
  - id: BR4.8
    statement: "検索の既定の値と折りたたみの設定は、DSL のまま定義で返す"
    category: policy
    applies_to: "定義の searchFields"
    trigger: "定義を作るとき"
    logic: "search.default を defaultValue、search.collapsed を collapsed として返す。既定の値を当てるのは画面"
    violation: "—"
    source: [FR3.3, AC1.2.6, AC1.2.7]
  - id: BR4.9
    statement: "検索の演算子とカラムの型の組は、決めた表にあるものだけを使う"
    category: constraint
    applies_to: "ColumnAccess.searchOperator（読み替えの後）"
    trigger: "判定の材料を作るとき"
    logic: "CONTAINS は STRING だけ。RANGE は INTEGER・BIG_INTEGER・DECIMAL・DATE・DATETIME・DATETIME_TZ・TIME・TIME_TZ だけ。EQUALS は password（masked）と BINARY を除くすべての型（OTHER を含む。OTHER の渡し方は BR7.7）。CHOICE（IN を含む）は固定の選択肢（FIXED）を持つカラムか BOOLEAN のカラムだけ（password・BINARY を除く）。password・BINARY のカラムはどの演算子にも使わない。表は domain の純粋な関数に置き、DB の種類によらない"
    violation: "表に無い組は BR4.10"
    source: [FR3.2, FR3.5, SM3, Q5, Q6, RC-R01, AC1.2.1, AC1.2.2, AC1.2.4, AC1.2.14]
  - id: BR4.10
    statement: "表に無い演算子と型の組のカラムは、定義の検索の項目から外し、要求に入っていれば 400 にする"
    category: validation
    applies_to: "定義の searchFields と一覧と検索の本文の conditions"
    trigger: "判定の材料を作るとき・本文を確かめたとき"
    logic: "IF DSL の検索の設定の演算子（読み替えの後）とカラムの型の組が BR4.9 の表に無い THEN ColumnAccess.searchOperator を null にし、searchFields に出さない。IF そのカラムが要求の conditions にある THEN BR1.6 の NOT_ALLOWED（DSL の設定に無いカラムと同じ答え）。DSL の検証は変えない（DSL 側で組を拒まない）"
    violation: "400 VALIDATION_FAILED（fieldErrors NOT_ALLOWED）"
    source: [RC-R01, FR3.2, AC1.2.9, AC3.1.6]

  # ── BR5 ページと並べ替え ──
  - id: BR5.1
    statement: "ページの番号は 1 以上の整数、1ページの件数は 20・50・100 だけを受け付ける"
    category: validation
    applies_to: "一覧と検索の本文の page・size"
    trigger: "本文を確かめたとき"
    logic: "IF page が 1 未満・整数でない、または size が 20・50・100 以外 THEN 400 VALIDATION_FAILED（fieldErrors なし）"
    violation: "400 VALIDATION_FAILED"
    source: [FR4.1, SL, AC1.3.2, AC1.3.8, AC1.3.10]
  - id: BR5.2
    statement: "並べ替えのカラムは1つまでで、並べ替えに使えるカラムだけを受け付ける"
    category: validation
    applies_to: "一覧と検索の本文の sort"
    trigger: "本文を確かめたとき"
    logic: "使えるのは list.sortable かつ READ 以上で、password・BINARY・OTHER でないカラム。ほかは BR1.6 の NOT_ALLOWED"
    violation: "400 VALIDATION_FAILED"
    source: [FR4.3, SL, AC1.3.3, AC1.3.6]
  - id: BR5.3
    statement: "並びは、要求の並べ替え → DSL の defaultSort → 主キーの順で決める"
    category: calculation
    applies_to: "一覧の並び"
    trigger: "一覧を読むとき"
    logic: "要求に sort があればそれ。無ければ defaultSort のカラムを list.order の順にキーにする（NONE のカラムは除く）。それも無ければ主キーの順。主キーの無いテーブルと主キーの設定の無いビューは順を約束しない（既知の制約）"
    violation: "—"
    source: [FR4.3, AC1.3.4, AC1.3.11]
  - id: BR5.4
    statement: "主キーのあるテーブルでは、並べ替えのキーの後に主キーの順を足して、ページの境で行が重ならないようにする"
    category: calculation
    applies_to: "一覧の並び"
    trigger: "一覧を読むとき"
    logic: "主キー（ビューは DSL の主キーの設定）があれば、まだキーに無い主キーのカラムを昇順で足す"
    violation: "—"
    source: [FR4.4, AC1.3.5, AC1.3.11]
  - id: BR5.5
    statement: "NULL は昇順で最後、降順で最初に並べ、3種類の DB でそろえる"
    category: calculation
    applies_to: "並べ替えのキー"
    trigger: "一覧を読むとき"
    logic: "PostgreSQL は NULLS LAST・NULLS FIRST、MySQL・MariaDB は「NULL か」のキーを先に足す（方言の部品）"
    violation: "—"
    source: [SL, NFR3, ADR-007, AC1.3.9]
  - id: BR5.6
    statement: "全体の件数は検索のたびに正確に数え、数えるのが時間切れなら要求全体を時間切れにする"
    category: policy
    applies_to: "一覧と検索"
    trigger: "一覧を読むとき"
    logic: "ページの行を読む前に、同じ条件で件数を数える（一覧の目的の時間の上限の中、ページと同じ1回の貸し出しの中。BR8.4）。時間切れなら BR1.8 の BUSINESS_TARGET_DB_TIMEOUT。件数を欠いた応答は返さない"
    violation: "503 BUSINESS_TARGET_DB_TIMEOUT"
    source: [FR4.2, ADR-007, Q4, AC1.3.1, AC1.3.7]
  - id: BR5.7
    statement: "最後のページを超える番号には、行を読まずに空のページと全体の件数を返す"
    category: calculation
    applies_to: "一覧と検索"
    trigger: "件数を数えた後"
    logic: "読み飛ばす行の数は (page − 1) × size を桁あふれの無い整数で求める。IF それが件数以上 THEN 空のページと件数を返す"
    violation: "—（誤りにしない）"
    source: [SL, Q4, AC1.3.10, AC1.3.14]
  - id: BR5.8
    statement: "1回の問い合わせで対象DB から読む行の数は、1ページの件数を超えない"
    category: constraint
    applies_to: "RowSqlExecutor の一覧の読み取り"
    trigger: "ページを読むとき"
    logic: "問い合わせの上限（ページの件数）と、取り出しの上限（JDBC の最大の行の数）の両方を置く"
    violation: "—"
    source: [NFR2, TP, AC1.3.16]

  # ── BR6 詳細 ──
  - id: BR6.1
    statement: "詳細は主キーの値を本文で受け、DETAIL が許されるテーブルだけで行う"
    category: authorization
    applies_to: "詳細の API"
    trigger: "要求を受けたとき"
    logic: "DETAIL が許されなければ BR1.5。主キーの値は道にも問い合わせの部分にも載せない"
    violation: "409 BUSINESS_ROW_OPERATION_NOT_ALLOWED"
    source: [FR5.2, FR5.3, CQ1, AC1.4.4]
  - id: BR6.2
    statement: "key は主キーのカラムをちょうどそろえ、値はカラムの型として読む"
    category: validation
    applies_to: "詳細の本文の key"
    trigger: "本文を確かめたとき"
    logic: "IF 主キーのカラムの欠け・余りがある THEN KEY_MISMATCH。IF 値を型として読めない（BR4.7 と同じ ColumnValueCodec）THEN INVALID_VALUE。読んだ値（BoundValue）を型つきでバインドする（BR7.8）。複合の主キー・記号・空白・非 ASCII の文字を含む値も、カラムごとに分けて照らす（区切りの文字で連結しない）"
    violation: "400 VALIDATION_FAILED"
    source: [FR5.2, AC1.4.2, AC1.4.8, AC3.6.2]
  - id: BR6.3
    statement: "詳細では最大2行まで読み、0 行なら行が無い、2 行なら行が1つに決まらない誤りにする"
    category: constraint
    applies_to: "詳細の読み取り"
    trigger: "対象DB を読んだとき"
    logic: "取り出しの上限を 2 にする。0 行 → BR1.7。2 行 → 409 BUSINESS_ROW_NOT_UNIQUE（監査に残さない）"
    violation: "404 BUSINESS_ROW_NOT_FOUND・409 BUSINESS_ROW_NOT_UNIQUE"
    source: [SM8, AC1.4.3, AC3.2.6]
  - id: BR6.4
    statement: "詳細の応答は、更新の画面の値の読み取りにも使う"
    category: policy
    applies_to: "詳細の応答"
    trigger: "詳細を返すとき"
    logic: "detail.visible で READ 以上のカラムの値と key を返す（BR2.2）。更新の画面の値はこの API で読む"
    violation: "—"
    source: [SQ7, FR7.1, AC1.4.1]

  # ── BR7 値の受け渡し ──
  - id: BR7.1
    statement: "DB の型と受け渡しの型（valueType）の対応は、repository の層の1か所で決める"
    category: calculation
    applies_to: "ColumnValueCodec"
    trigger: "定義を作るとき・値を読むとき・値をバインドするとき"
    logic: "DSL の dbType の名前（大文字と小文字を区別しない）を、STRING・INTEGER・BIG_INTEGER・DECIMAL・BOOLEAN・DATE・DATETIME・DATETIME_TZ・TIME・TIME_TZ・BINARY・OTHER に分ける。MySQL・MariaDB の tinyint(1)・bit(1) は BOOLEAN。表に無い型は OTHER。dslmanage の型の分類には依存しない。U2 も同じ部品を使う"
    violation: "—"
    source: [TP, C2, Q6]
  - id: BR7.2
    statement: "値はすべて文字か NULL で返し、桁を失わない"
    category: constraint
    applies_to: "一覧・詳細の値"
    trigger: "値を応答にするとき"
    logic: "BIG_INTEGER・DECIMAL は DB の値の文字（指数の形にしない）。BOOLEAN は true・false。日付・時刻は ISO の形。NULL は null、空の文字列は空の文字列で返し、見分けがつく。受け取る側（要求の値）は文字で受け、BR4.7・BR6.2 で型として読む"
    violation: "—"
    source: [TP, FR11.5, AC3.6.5, AC1.5.5]
  - id: BR7.3
    statement: "時差の無い日時は DB の値のまま、時差つきの型は UTC にそろえて返す"
    category: calculation
    applies_to: "DATETIME・TIME・DATETIME_TZ・TIME_TZ"
    trigger: "値を読むとき"
    logic: "時差の無い型はタイムゾーンを足さず、秒未満の桁も DB の値のまま。時差つきの型（PostgreSQL の timestamptz・timetz、MySQL・MariaDB の timestamp）は UTC にして +00:00 を付ける。アプリ・接続のタイムゾーンに左右されない（やり方は NFR 設計の試しで決める）"
    violation: "—"
    source: [FR11.3, SM7, AC1.5.3, AC1.5.7]
  - id: BR7.4
    statement: "フォーム部品が password のカラムは、値を応答に入れない"
    category: authorization
    applies_to: "一覧・詳細の値"
    trigger: "値を応答にするとき"
    logic: "values にそのカラムの値を入れず、定義で masked: true を返す（画面は伏せて出す）"
    violation: "—"
    source: [SM3, AC1.5.8]
  - id: BR7.5
    statement: "バイナリの型の値は対象DB から読まず、応答にも入れない"
    category: constraint
    applies_to: "valueType = BINARY"
    trigger: "SQL を組み立てるとき"
    logic: "選ぶカラムに BINARY を入れない。定義では valueType: BINARY だけを返す（画面は「（バイナリ）」のように出す）"
    violation: "—"
    source: [Q5, TP]
  - id: BR7.6
    statement: "大きな文字列は、一覧と詳細でそれぞれの上限の文字数で切り、切ったことの印を付ける"
    category: constraint
    applies_to: "STRING・OTHER の値"
    trigger: "値を応答にするとき"
    logic: "一覧は上限 N 字、詳細は上限 M 字（N・M は NFR 要件の段で決める）。上限を超えたら先頭の上限の分だけを返し、truncatedColumns にカラム名を入れる"
    violation: "—"
    source: [Q5, TP, AC1.5.6]
  - id: BR7.7
    statement: "型の分類の外の型は、値を文字として読み、読み取りだけの OTHER として返し、主キーと EQUALS の値は DB の種類ごとの形で渡す"
    category: policy
    applies_to: "valueType = OTHER"
    trigger: "値を読むとき・主キーや検索の値を渡すとき"
    logic: "値は文字で読む。接続の設定（stringtype=unspecified など）は変えない（共有の targetDataSource と、既存のスキーマの読み取りに効かせない）。PostgreSQL では OTHER の値だけを setObject(位置, 文字, Types.OTHER) で渡し、その値の型を DB に決めさせる。MySQL・MariaDB では setString で渡す。渡し方は RowDialect に閉じる（BR3.5）。入力にはしない（BR2.4）。3種類の DB での確かめは NFR 設計の試しで行い、だめな DB があれば、その DB で OTHER を主キーの指し方と EQUALS から外す（BR7.9）"
    violation: "—"
    source: [Q6, AC1.4.8, RC-R02]
  - id: BR7.8
    statement: "検索の値と主キーの値は、ColumnValueCodec で型として読んだ値を、型ごとのセッターでバインドする"
    category: constraint
    applies_to: "ColumnValueCodec・RowSqlExecutor"
    trigger: "値をバインドするとき"
    logic: "STRING → setString。INTEGER → setLong。BIG_INTEGER・DECIMAL → setBigDecimal（桁を保つ。符号なしの BIGINT の最大も入る）。BOOLEAN → setBoolean。DATE → LocalDate、DATETIME → LocalDateTime、TIME → LocalTime を setObject。DATETIME_TZ → UTC の OffsetDateTime、TIME_TZ → UTC の OffsetTime を setObject（MySQL・MariaDB の timestamp の渡し方は BR7.3 と同じ NFR 設計の試しで決める）。OTHER は BR7.7。NULL は setNull（型の番号は同じ表）。文字のまま渡して DB に型を決めさせない（U2 Q3: A と同じ形）"
    violation: "—"
    source: [RC-R02, TP, AC3.6.2, AC1.4.8, AC3.6.5]
  - id: BR7.9
    statement: "OTHER の渡し方が確かめられなかった DB では、OTHER を主キーの指し方と EQUALS から外す"
    category: policy
    applies_to: "BR4.9 の表・BR2.3 の DETAIL"
    trigger: "NFR 設計の試しの結果がだめだったとき（切り替え先）"
    logic: "その DB では、OTHER のカラムを BR4.9 の EQUALS から外し（検索の項目に出さない）、主キーに OTHER のカラムを持つテーブルでは DETAIL を許さない（BR1.5 の 409。理由の値は NFR 設計で決める）。AC1.4.8 との差として functional-spec.md に記録する。試しが通れば、この決まりは使わない"
    violation: "—"
    source: [RC-R02, AC1.4.8]

  # ── BR8 接続と時間の上限 ──
  - id: BR8.1
    statement: "対象DB の読み取りの接続は、targetdb.service の口から貸す形で受け取る"
    category: constraint
    applies_to: "業務処理の層・targetdb.service の読み取りの口"
    trigger: "一覧・詳細を読むとき"
    logic: "口は処理（TargetReadSession を受け取る関数）を受け取り、既存の読み取りの接続（既定の候補にしない targetDataSource）から1本借りて処理に貸し、処理が戻ったら（失敗しても）閉じて、処理の結果だけを返す。接続を戻り値で渡さない・保存しない。設定が無ければ処理を呼ばずに「無い」と答え、BR1.8 の BUSINESS_TARGET_DB_UNAVAILABLE にする。読み取りの API は読み取りのアカウントの接続だけを使う。新しい接続の Bean は作らない。U2 の書き込みの口も同じ貸す形にする"
    violation: "503 BUSINESS_TARGET_DB_UNAVAILABLE"
    source: [C6, ADR-008, R-01, NFR4.3, AC3.6.7, AC3.3.9, RC-R03]
  - id: BR8.2
    statement: "対象DB への問い合わせには、一覧と詳細の目的ごとに時間の上限を付ける"
    category: constraint
    applies_to: "RowSqlExecutor"
    trigger: "問い合わせのとき"
    logic: "ReadPurpose に一覧と詳細の目的を足し、QueryTimeout に値を足す（値は NFR 要件）。ドライバーの通信の待ちは、すべての目的のうち最も長い上限に余裕を足して決める"
    violation: "上限を超えたら BUSINESS_TARGET_DB_TIMEOUT"
    source: [NFR4.4, TP, R-02, AC3.3.4]
  - id: BR8.3
    statement: "内部DB の接続を持ったまま、対象DB を待たない"
    category: constraint
    applies_to: "業務処理の層"
    trigger: "要求を受けたとき"
    logic: "権限の写しは解決の口の短い読み取りで取り終えてから、対象DB の口を呼ぶ。業務処理の層は内部DB のトランザクションを開かない"
    violation: "—"
    source: [TP, AC3.6.6, AC3.6.7]
  - id: BR8.4
    statement: "1要求で使う接続の数を決め、結合テストで数える"
    category: constraint
    applies_to: "定義・一覧と検索・詳細"
    trigger: "要求ごと"
    logic: "定義は対象DB に接続しない。一覧は件数とページを、読み取りの口の1回の貸し出し（接続1本）の中で読む。詳細も1回の貸し出しの中で読む。内部DB は写しの読み取りの分だけ（数は NFR 要件・NFR 設計で確かめる）"
    violation: "結合テストが落ちる"
    source: [TP, AC3.6.6, RC-R03]
  - id: BR8.5
    statement: "対象DB の失敗は、例外のメッセージを読まずに理由を分ける"
    category: policy
    applies_to: "RowSqlExecutor"
    trigger: "JDBC の例外を受けたとき"
    logic: "接続の失敗 → UNAVAILABLE。時間切れ → TIMEOUT。未定義の表・カラムの SQLState（例: 42P01・42703・42S02・42S22）→ MISMATCH。値の型の誤りの SQLState → BR8.6。ほかは想定外の失敗"
    violation: "BR1.8 の code"
    source: [FR13.3, AC3.3.4, AC3.3.8, RC-R01]
  - id: BR8.6
    statement: "DB が値の型の誤りを返したときは、値を出さずに 400 VALIDATION_FAILED に写す"
    category: policy
    applies_to: "RowSqlExecutor と誤りの変換"
    trigger: "JDBC の例外の SQLState が値の型の誤りのとき"
    logic: "値の型の誤りの SQLState（PostgreSQL の 22P02・22003・22007・22008・42883、MySQL・MariaDB の 22001・22003・22007・22018 と、HY000 のうち業者のエラーの番号が 1366 のもの。表はコード生成の計画で既存の SchemaQueries の分け方と合わせて確定する）→ 400 VALIDATION_FAILED。どのカラムかを例外の文から読まないため fieldErrors は付けない。definitionChanged は付ける（BR9.2）。ログは WARN で SQLState・業者のエラーの番号・例外のクラスの名前だけ（BR10.2）。値は BR4.7・BR6.2 で先に型として確かめるため通常は起きない。3種類の DB の境界の値の結合テストで、起きないこと（と、起こしたときに 400 になること）を確かめる"
    violation: "400 VALIDATION_FAILED（500 にしない）"
    source: [RC-R01, FR13.3, AC1.2.5, AC1.2.14]

  # ── BR9 定義の識別 ──
  - id: BR9.1
    statement: "読み取りの成功の応答は今の定義の識別を返し、要求の識別が違えば印を付ける"
    category: policy
    applies_to: "定義・一覧と検索・詳細の成功の応答"
    trigger: "応答を返すとき"
    logic: "応答に今の definitionId を入れる。IF 要求の definitionId があり、今と違う THEN 今の権限で判定した結果に definitionChanged: true を付ける（古い定義のカラムは返さない）。要求に definitionId が無ければ false"
    violation: "—"
    source: [C2, CQ3, FR2.5, AC1.1.5, AC3.1.4]
  - id: BR9.2
    statement: "読み取りの 400 の誤りの応答にも definitionChanged を付ける"
    category: policy
    applies_to: "一覧と検索・詳細の 400 VALIDATION_FAILED（BR1.6・BR5.1・BR8.6）"
    trigger: "400 を返すとき"
    logic: "契約 C2 の誤りの型のとおり、Problem Details に definitionChanged（真偽値）を足す。IF 要求の definitionId があり、今と違う THEN true ELSE false。画面は true なら定義を読み直し、利用者の入力の誤りと見分ける。403・404・409・503 には付けない（それぞれ code で画面の動きが決まる）。付け方は既存の GlobalExceptionHandler の拡張の項目の口（fieldErrors と同じ仕組み）をコード生成の計画で確かめて決める"
    violation: "—"
    source: [C2, RC-R04, AC1.1.5, AC3.1.4]

  # ── BR10 漏えいの防止 ──
  - id: BR10.1
    statement: "行の値・主キーの値・検索の値と、対象DB の接続を持つ型は、文字列にするときに値（中身）を伏せる"
    category: constraint
    applies_to: "RowKey・RowValues・SearchCondition・BoundValue・TargetReadSession・要求と応答の record"
    trigger: "TRACE のログ・例外の文字列"
    logic: "値を持つ型の toString はカラムの名前と件数だけを出す。TargetReadSession の toString は DB の種類だけを出し、接続の文字列（接続先・ユーザー名を含みうる）を出さない。RowSqlExecutor（repository の層で TraceAspect の対象）の引数は TargetReadSession にし、java.sql.Connection を直接受けない。行を Map などの汎用の型のまま層をまたいで渡さない"
    violation: "*SecretLeakIT が落ちる"
    source: [NFR4.2, PM, TP, AC3.6.8, RC-R03]
  - id: BR10.2
    statement: "JDBC の例外のメッセージは、応答にもアプリのログにも出さない"
    category: constraint
    applies_to: "RowSqlExecutor と誤りの変換"
    trigger: "例外を受けたとき"
    logic: "ログには SQLState・業者のエラーの番号・例外のクラスの名前だけを出す。例外は受けた所で code に変え、メッセージを持つ例外を TraceAspect の対象の層の外へ出さない"
    violation: "—"
    source: [PM, TP, FR13.3, AC3.3.4, AC3.3.8]
  - id: BR10.3
    statement: "誤りの応答に、入力した値・主キーの値・接続先を入れない"
    category: constraint
    applies_to: "Problem Details"
    trigger: "誤りを返すとき"
    logic: "detail・instance に値を入れない。fieldErrors はカラムの名前と reason だけ。definitionChanged は真偽値だけ"
    violation: "—"
    source: [PM, NFR4.2, AC3.6.9]
  - id: BR10.4
    statement: "要求の道には、名前も行の値も載せない"
    category: constraint
    applies_to: "読み取りの API の道"
    trigger: "API を定めるとき"
    logic: "道は /api/tables/definition・/api/tables/rows/search・/api/tables/rows/get に固定し、スキーマ名・テーブル名・主キー・検索の値は本文で運ぶ。トレースの道の属性に値が残らない"
    violation: "—"
    source: [Q1, CQ1, PM, AC3.6.8]

  # ── BR11 分類と構造の検査 ──
  - id: BR11.1
    statement: "読み取りの API 3本は「ログインだけ」に分類する"
    category: authorization
    applies_to: "BusinessDataReadController"
    trigger: "起動時・構造の検査"
    logic: "@ApiAccess(AUTHENTICATED) を付け、既存の API の分類の網羅のテストに載せる。権限は業務処理の層でテーブルごとに判定する。停止中の利用者は既存の認証の段で拒まれる"
    violation: "分類の網羅のテストが落ちる"
    source: [K-61, C2, AC3.5.1, AC3.1.2]
  - id: BR11.2
    statement: "businessdata の境界テストで、依存してよい相手と SQL を実行してよい場所を固定する"
    category: constraint
    applies_to: "BusinessDataBoundaryArchitectureTest"
    trigger: "構造の検査"
    logic: "依存してよいのは targetdb.service・targetdb.domain・role.service・role.domain・dsl の提供口と domain・common。java.sql.Connection の prepareStatement・createStatement を呼んでよいのは RowSqlExecutor だけ"
    violation: "テストが落ちる"
    source: [ADR-001, TP, R-04]
  - id: BR11.3
    statement: "role の境界テストに businessdata を足し、PermissionSnapshot に DSL を渡す口を足す"
    category: constraint
    applies_to: "RoleBoundaryArchitectureTest・PermissionSnapshot"
    trigger: "コード生成の計画"
    logic: "businessdata を role.service・role.domain に依存してよい相手として足す（緩める変更のため、コード生成の計画に明記して依頼者の承認を得る）。DSL を渡す口は読み取りだけ"
    violation: "—"
    source: [ADR-002, Q2]
  - id: BR11.4
    statement: "SpotBugs の SQL_ の除外は、RowSqlExecutor の読み取りのメソッドとパターン名に絞る"
    category: constraint
    applies_to: "backend/config/spotbugs-exclude.xml"
    trigger: "コード生成"
    logic: "クラス・メソッド・パターン名の3つで絞り、理由（名前は種類ごとの DSL の一覧で照らしたものだけ。BR3.3）を書く。足すときは計画で承認を得る。U2 は書き込みのメソッドを足す"
    violation: "—"
    source: [TP, R-04]
```

## 演算子と型の表（BR4.9）

読み替え（BR4.6）の後の演算子に当てる。〇は使える組、—は検索の項目から外す組（BR4.10）。

| valueType | EQUALS | CONTAINS | RANGE | CHOICE（IN を含む） |
|---|---|---|---|---|
| STRING | 〇 | 〇 | — | FIXED の選択肢があるとき 〇 |
| INTEGER・BIG_INTEGER・DECIMAL | 〇 | — | 〇 | FIXED の選択肢があるとき 〇 |
| DATE・DATETIME・DATETIME_TZ・TIME・TIME_TZ | 〇 | — | 〇 | FIXED の選択肢があるとき 〇 |
| BOOLEAN | 〇 | — | — | 〇（選択肢が無ければ true・false） |
| OTHER | 〇（渡し方は BR7.7。だめな DB では BR7.9） | — | — | FIXED の選択肢があるとき 〇 |
| BINARY | — | — | — | — |
| password（masked）のカラム | — | — | — | — |

## 要約の表

| ID | 群 | 要点 |
|---|---|---|
| BR1.1 | 判定の順 | 401 → 404（DSL）→ 404（テーブル）→ 403 → 409 → 400 → 404（行）→ 対象DB の失敗。DB の型の誤りだけは 400 に写す |
| BR1.2 | code | DSL が無い: 404 `BUSINESS_DSL_NOT_APPLIED` |
| BR1.3 | code | DSL に無い名前: 404 `BUSINESS_TABLE_NOT_FOUND`（種類ごとの一覧で照らす） |
| BR1.4 | 認可 | テーブルが NONE: 403 `ACCESS_DENIED`（行の有無によらない） |
| BR1.5 | code | 詳細が許されない: 409 `BUSINESS_ROW_OPERATION_NOT_ALLOWED` |
| BR1.6 | 入力 | 400 `VALIDATION_FAILED`、fieldErrors の reason を決める |
| BR1.7 | code | 行が無い: 404 `BUSINESS_ROW_NOT_FOUND` |
| BR1.8 | code | 503 `UNAVAILABLE`・503 `TIMEOUT`・409 `MISMATCH`・409 `ROW_NOT_UNIQUE` の順 |
| BR1.9 | code | 1 code 1 状態コード、機能の一覧に置く |
| BR1.10 | 監査 | 読み取りは監査に残さない |
| BR2.1〜BR2.7 | 出し分け | NONE を出さない、list・detail の見せ方、許す操作の関数、入力の可否、性質、行を見分ける値、理由 |
| BR2.8 | 出し分け | 定義のカラムの項目は `ColumnDefinition` の形。読み取りの項目は U1、検証の材料は U2 が足す |
| BR3.1〜BR3.5 | SQL | 写し1つ、DSL で照らす、`quoteKnown` に種類ごとの一覧、型つきのバインド、SQL は1つの部品 |
| BR4.1〜BR4.8 | 検索 | AND、各演算子、IN の読み替え、型として読む、既定の値 |
| BR4.9・BR4.10 | 検索 | 演算子と型の表。表に無い組は検索の項目から外し、要求にあれば 400 |
| BR5.1〜BR5.8 | ページ | 件数とページの範囲、並べ替え、安定、NULL の並び、正確な件数、上限 |
| BR6.1〜BR6.4 | 詳細 | 本文の主キー、そろえ方、2行まで読む、更新の画面にも使う |
| BR7.1〜BR7.6 | 値 | 型の対応、文字で受け渡す、UTC、password、バイナリ、切り詰め |
| BR7.7〜BR7.9 | 値 | OTHER は接続の設定を変えず PostgreSQL で `Types.OTHER`、型ごとのセッター、だめなときの切り替え先 |
| BR8.1〜BR8.6 | 接続 | 貸す形の読み取りの口、時間の上限、内部DB の接続、接続の数、失敗の分け方、型の誤りは 400 |
| BR9.1・BR9.2 | 定義 | `definitionId` と `definitionChanged`（成功と 400 の両方） |
| BR10.1〜BR10.4 | 漏えい | 伏せ字（接続の型も）、JDBC のメッセージ、誤りの応答、道に値を載せない |
| BR11.1〜BR11.4 | 構造 | API の分類、境界テスト、role の変更、SpotBugs の除外 |
