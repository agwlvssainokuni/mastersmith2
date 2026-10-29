# 業務の決まり — U1 u1-reference-read

出典の表記: FR・NFR・A は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、AC・X は `inception/user-stories/stories.md`、ADR は `inception/domain-design/decisions.md`、C1〜C7 は `inception/contract-design/contract-summary.md`、Q1〜Q4 はこの段の `functional-design-questions.md` の答え（すべて A）、QD は同じファイルの「決まっていること」、TP は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`、前 U1 は前の Intent の `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/functional-design/rules.md`（既存の決まり。この単位の ID とは別）。

この単位の ID は BR1.1 から振り直した。code は新しく作らず、既存の code を使い回す（1つの code に状態コードは1つだけ。TP）。時間の上限の値（`REFERENCE` の問い合わせ1回の上限と、定義の取得の全体の上限）は NFR 設計で決める。

```yaml
rules:
  # ── BR1 判定の順と応答 ──
  - id: BR1.1
    statement: "定義の API は、既存の判定の順のあとに参照の読み取りを置き、参照先の権限の不足では 403 にしない"
    category: policy
    applies_to: "POST /api/tables/definition"
    trigger: "要求を受けたとき"
    logic: "① 未認証 401 → ② 本文の形（purpose が VIEW・CREATE・UPDATE に無い）400 VALIDATION_FAILED → ③ 既存の判定（DSL が無い 404・DSL に無い名前 404・参照元のテーブルが NONE 403。前の Intent 261016-business-data-views の U1 の決まり 1.1）→ ④ purpose が CREATE・UPDATE のときだけ参照の項目を作る（BR4・BR5）→ ⑤ 対象DB の失敗（BR1.5、定義の取得ごと）。参照先を読めない・候補が多いは ④ の項目の状態で返し、要求は 200"
    violation: "参照先の権限の不足を 403 にしない（AC1.3.1）"
    source: [FR1.3, C2, AC1.3.1, AC1.1.4, AC1.2.3]
  - id: BR1.2
    statement: "参照先の検索 API は、401 → 本文の形の 400 → 同じ応答の 403 → 値の型の 400 → 対象DB の失敗 → 200 の順で判定する"
    category: policy
    applies_to: "POST /api/tables/reference/rows"
    trigger: "要求を受けたとき"
    logic: "① 未認証 401（停止中の利用者も既存の認証の入口で拒否）→ ② 本文の形の誤り 400（BR1.4。DSL に依らない）→ ③ 同じ応答の 403（BR1.3。名前・権限・DSL）→ ④ SEARCH の条件の値を型として読めない 400（BR5.6）→ ⑤ 対象DB の失敗（BR1.5）→ ⑥ VALUE で行が無い 403（BR6.4）→ 200"
    violation: "後ろの段の判定を先に返さない（たとえば DSL に無い名前で値が長すぎるときは 400、値が正しく名前が無いときは 403）"
    source: [C2, FR2.2, AC2.1.10, AC2.1.11, QD]
  - id: BR1.3
    statement: "名前・権限・DSL・行の有無に関わる拒否は、すべて 403 ACCESS_DENIED の同じ応答にする"
    category: authorization
    applies_to: "参照先の検索 API"
    trigger: "判定の順の ③ と ⑥"
    logic: "IF 次のどれか: DSL が適用されていない／参照元のスキーマ・テーブル・カラムが写しの DSL に無い／参照元のカラムが REFERENCE・LOOKUP でない／参照元のカラムの主権限が FULL でない／参照の項目の状態が NO_PERMISSION（BR3.1）／SEARCH を REFERENCE のカラムに送った（BR5.2）／条件のカラムが searchFields に無い（BR5.3）／VALUE の値を型として読めない（BR6.2）／VALUE で行が無い（BR6.4）THEN 403 ACCESS_DENIED。状態コード・code・本文の構造・メッセージを同一にし、fieldErrors を付けない"
    violation: "403 ACCESS_DENIED（共通の code。重ねて定義しない）。対象DB に問い合わせない（⑥ を除く）。状態は変わらない"
    source: [FR2.2, FR2.2a, C2, X9, X13, AC2.1.8, AC2.1.9, AC2.1.10, AC2.1.11, AC1.3.5, AC2.3.2]
  - id: BR1.4
    statement: "本文の形の誤りは、DSL を見る前に 400 VALIDATION_FAILED で返す"
    category: validation
    applies_to: "参照先の検索 API の本文"
    trigger: "判定の順の ②"
    logic: "IF schema・table・column のどれかが無い／conditions と value の両方がある・どちらも無い／conditions の要素に column が無い・同じ column が2回ある／conditions の値（空を除く）が1つも無い／条件の値か value が 1,000 文字（Unicode のコードポイント。既存の SearchRequestValidator.MAX_VALUE_LENGTH と同じ）を超える／conditions のときに page が無い・1〜1,000,000 の外 THEN 400 VALIDATION_FAILED（fieldErrors を付けない）。value のときの page は読まない。本文の知らない項目（参照先の表・カラムの名前など）は無視し、結果を変えない"
    violation: "400 VALIDATION_FAILED。DSL を見ず、対象DB に問い合わせない"
    source: [QD, FR2.2, FR2.6, AC2.1.1, AC2.1.3, AC2.1.4, AC2.1.6]
  - id: BR1.5
    statement: "参照先の読み取りの失敗は、既存の業務データの読み取りと同じ code に写す"
    category: policy
    applies_to: "定義の API（purpose が CREATE・UPDATE）と参照先の検索 API"
    trigger: "対象DB の読み取りが失敗したとき"
    logic: "TIMEOUT・全体の時間の上限の超過（BR8.4）→ 503 BUSINESS_TARGET_DB_TIMEOUT。UNAVAILABLE・読み取りの設定が無い・接続を借りられない → 503 BUSINESS_TARGET_DB_UNAVAILABLE。MISMATCH・READ_CONVERSION（DSL と対象DB の型の食い違い）→ 409 BUSINESS_TARGET_DB_MISMATCH。想定外 → 既存の BusinessDataUnexpectedException（500）。定義の API では、どの項目の失敗でも定義の取得ごとこの応答にし、項目ごとの 503 にしない"
    violation: "応答と WARN に JDBC の例外のメッセージを含めない（BR10.2）"
    source: [FR1.5, C2, AC1.1.4, AC2.1.13, AC3.5.3]
  - id: BR1.6
    statement: "この単位は新しい code を作らない"
    category: constraint
    applies_to: "BusinessDataProblemTypes・BusinessDataProblemTypeCatalog"
    trigger: "コード生成"
    logic: "使う code は ACCESS_DENIED（共通）・VALIDATION_FAILED（共通）・BUSINESS_TARGET_DB_TIMEOUT・BUSINESS_TARGET_DB_UNAVAILABLE・BUSINESS_TARGET_DB_MISMATCH（既存）だけ。候補が多い（TOO_MANY）は code ではなく項目の状態で返す"
    violation: "—"
    source: [TP, C2, NFR6]
  - id: BR1.7
    statement: "参照先の読み取り（選択肢・検索・ラベル・保存前の確かめ）は監査に残さない"
    category: policy
    applies_to: "この単位のすべての読み取り"
    trigger: "読み取りのとき"
    logic: "監査の出来事を出さない（前の Intent 261016-business-data-views の U1 の決まり 1.10 と同じ）。保存前の確かめの拒否を残すのは U2（C4）"
    violation: "—"
    source: [FR3.4, C4]

  # ── BR2 参照先の特定と名前の照合 ──
  - id: BR2.1
    statement: "参照先は、参照元のカラムの DSL の options から引き、利用者の入力から受け取らない"
    category: constraint
    applies_to: "ReferenceTarget"
    trigger: "定義・検索・ラベル・確かめのたび"
    logic: "参照元のスキーマ・テーブル・カラムを写しの DSL と照らしてから、そのカラムの options（source が REFERENCE・LOOKUP）の table・valueColumn・labelColumn・lookupSearch・lookupList で ReferenceTarget を作る。DSL を持ち続けず、読み出しのたびに引く"
    violation: "参照でないカラムは BR1.3（検索 API）・reference null（定義）"
    source: [FR2.2, C1, AC1.4.1, AC2.1.1]
  - id: BR2.2
    statement: "1要求で権限の写しを1回だけ取り、参照元と参照先の判定と SQL の照合に同じ写しを使う"
    category: constraint
    applies_to: "定義・検索・ラベル・確かめ"
    trigger: "要求を受けたとき"
    logic: "既存の TableAccessResolver が取った PermissionSnapshot を、参照元の判定の材料と一緒に ReferenceLookup へ渡す（取り直さない）。参照先のテーブルの判定の材料（TableAccessView）は、同じ写しと同じ写しの DSL で既存の TableAccessPolicy から作る"
    violation: "写しを2回取らない（作業ロールの切り替えの途中で食い違わない）"
    source: [C6, FR1.4, ADR-005]
  - id: BR2.3
    statement: "参照先のテーブル・カラムの名前は、写しの DSL と照らしてから既存の種類ごとの名前の一覧で引用し、値はバインド変数で渡す"
    category: constraint
    applies_to: "参照先の SQL"
    trigger: "参照先を読むとき"
    logic: "参照先のテーブルを参照元と同じスキーマの写しの DSL で引く。無ければ（DSL の検証で起きないはずだが）参照を提供しない（NO_PERMISSION・403 の同じ応答・NOT_FOUND_OR_DENIED）。SQL の識別子は参照先の TableAccessView の KnownIdentifiers で引用する（既存の quoteKnown）。値は既存の型ごとのセッターでバインドする"
    violation: "DSL に無い名前で読まない（PM の Forbidden）"
    source: [NFR1, AC1.4.1, AC1.1.9, AC2.1.16, PM]
  - id: BR2.4
    statement: "参照先の形（複合の主キー・主キーの無いテーブル・ビュー・自己参照・一意でない valueColumn）では拒否しない"
    category: policy
    applies_to: "ReferenceTarget"
    trigger: "参照先を読むとき"
    logic: "形で拒否せずそのまま読む。同じ値の行が2行以上ありうることを、選択肢（BR4.4）・ラベル（BR6.3）・確かめ（BR9.4）で扱う。自己参照は同じ写しで読むだけで、特別に扱わない"
    violation: "500 にしない。行の値をログに出さない"
    source: [Q1, AC1.4.4, X6]
  - id: BR2.5
    statement: "ラベルに使うカラムは、REFERENCE は labelColumn、LOOKUP は labelColumn があればそれ、無ければ lookupList の最初のカラムとする"
    category: calculation
    applies_to: "ReferenceTarget.labelColumn"
    trigger: "ReferenceTarget を作るとき"
    logic: "IF kind = REFERENCE THEN options.labelColumn。IF kind = LOOKUP AND options.labelColumn がある THEN それ ELSE lookupList を order の昇順（同じ order は DSL の並び）に並べた最初のカラム"
    violation: "—"
    source: [Q4, AC2.1.5, C2]

  # ── BR3 権限の当て方 ──
  - id: BR3.1
    statement: "参照先を読めるかは、写しで判定する純粋な関数 accessOf が決め、1つでも欠けたら NO_PERMISSION とする"
    category: authorization
    applies_to: "ReferenceTarget"
    trigger: "定義・検索・ラベル・確かめのたび"
    logic: "IF 参照先のテーブルの主権限が NONE／valueColumn の主権限が NONE／ラベルに使うカラム（BR2.5）の主権限が NONE／LOOKUP で lookupList のカラムのどれかの主権限が NONE／LOOKUP で使える検索の条件（BR3.2）が残らない／valueColumn がバイナリか password のカラム（値を文字で渡せない）THEN NO_PERMISSION ELSE AVAILABLE。部分的に落として提供しない"
    violation: "定義では項目の状態 NO_PERMISSION、検索 API では 403 の同じ応答、確かめでは NOT_FOUND_OR_DENIED"
    source: [FR1.3, FR2.2, X4, C1, AC1.3.1, AC1.3.2, AC2.1.8]
  - id: BR3.2
    statement: "使える検索の条件は、読めるカラムで、演算子が EQUALS か CONTAINS（文字の型だけ）のものに限る"
    category: constraint
    applies_to: "LOOKUP の lookupSearch"
    trigger: "accessOf とピッカーの情報を作るとき"
    logic: "lookupSearch の各条件について、IF カラムの主権限が NONE でない AND バイナリでも password でもない AND（演算子が EQUALS OR（演算子が CONTAINS AND カラムが文字の型））THEN 使える ELSE 使えない（searchFields に出さない）。RANGE・CHOICE・IN は、契約 C2 の条件の形（column と value の1組）で表せないため使わない"
    violation: "使えない条件を指定した要求は 403 の同じ応答（BR5.3）。NONE の列で検索させない（値の有無から内容を推測できるため）"
    source: [X4, AC2.1.8, C2]
  - id: BR3.3
    statement: "参照元のカラムは、定義では用途で入力できるとき、検索 API では主権限が FULL のときだけ参照先を読む"
    category: authorization
    applies_to: "参照元のカラム"
    trigger: "定義・検索・ラベルのとき"
    logic: "定義: IF purpose = CREATE AND 参照元のカラムが editableOnCreate、または purpose = UPDATE AND editableOnUpdate THEN 参照先を判定して読む ELSE 参照先を読まず状態を NO_PERMISSION にする（入力できない項目に選択肢は要らない）。検索 API: IF 参照元のカラムの主権限が FULL でない THEN 403 の同じ応答。U3 への引き継ぎ: 画面は reference.status を見る前に、そのカラムの既存の editableOnCreate・editableOnUpdate を先に確かめ、入力できないカラムには既存の「入力できない」表示を出し、権限の文言（参照先を読む権限が無い旨）を決して出さない（NO_PERMISSION が権限の不足と入力不可の両方を含むため）"
    violation: "入力できない参照元のカラムのために参照先を読まない"
    source: [FR2.2, C2, AC2.1.10]
  - id: BR3.4
    statement: "参照先の判定は API ごと要求ごとにサーバー側で行い、作業ロールの切り替え・割り当ての変更は次の要求で効く"
    category: authorization
    applies_to: "定義・検索・ラベル・確かめ"
    trigger: "要求を受けたとき"
    logic: "毎回その要求の写し（BR2.2）で accessOf を求める。画面の出し分けを代わりにしない。停止中の利用者は既存の認証の入口で拒否される"
    violation: "画面が持つ古い権限で判定しない"
    source: [FR1.4, PM, AC1.3.8, AC2.1.10, AC2.1.12]
  - id: BR3.5
    statement: "主権限が NONE のカラムは、どの応答にも含めない（応答の組み立ての部品でも必ず落とす）"
    category: authorization
    applies_to: "PickerInfo・ReferenceRow"
    trigger: "応答を組み立てるとき"
    logic: "accessOf が AVAILABLE なら NONE のカラムは無いはずだが、応答の組み立ての部品は防御として、参照先の判定の材料に無いカラム（NONE を含む）の列とセルを必ず落とす。部品を直接呼ぶ単体テストで確かめる"
    violation: "—"
    source: [FR2.3, PM, AC2.1.2, AC2.1.8]
  - id: BR3.6
    statement: "状態が AVAILABLE でないときは選択肢もピッカーの情報も返さず、ピッカーの情報には参照先のテーブルの名前を載せない"
    category: authorization
    applies_to: "ReferenceField"
    trigger: "定義の項目を作るとき"
    logic: "IF status != AVAILABLE THEN options・picker を返さない。picker は searchFields と listColumns（参照先のカラム名と表示名）だけを持ち、参照先のスキーマ名・テーブル名を持たない"
    violation: "—"
    source: [FR1.3, FR2.1, AC1.3.1, AC1.3.2, AC1.3.4, AC1.3.5]

  # ── BR4 REFERENCE の選択肢 ──
  - id: BR4.1
    statement: "REFERENCE の選択肢は、定義の purpose が CREATE・UPDATE のときだけ読む"
    category: policy
    applies_to: "定義の API"
    trigger: "定義の項目を作るとき"
    logic: "IF purpose = VIEW（本文に無いときを含む）THEN 参照先を読まず、すべてのカラムの reference を null にする ELSE 参照の項目ごとに BR3.3・BR3.1 の後で読む"
    violation: "一覧・詳細の定義（VIEW）で参照先を読まない"
    source: [C2, X2, FR4.1, AC3.4.2, AC1.1.1]
  - id: BR4.2
    statement: "選択肢は、valueColumn が NULL でない行を、ラベルの昇順・値の昇順で、最大 201 行読む"
    category: calculation
    applies_to: "REFERENCE の選択肢"
    trigger: "選択肢を読むとき"
    logic: "出すカラムは valueColumn とラベルに使うカラムだけ。条件は valueColumn IS NOT NULL。並びはラベルの昇順（NULL は最後。DB ごとの書き方の差は既存の方言のクラスに閉じる）→ valueColumn の昇順 → 参照先に主キーがあれば主キーの昇順。この並びは利用者の sortKey からも既存の view.orderKeys からも作らず、BR11.4 の固定の並びとして ReferenceReadSpec.order に入れる。文字の並びは各 DB の照合順序に従う（3種類で同じ並びは求めない）。読む行は 201 まで（BR8.6）"
    violation: "—"
    source: [FR1.1, FR1.2, A1, X5, AC1.1.1, AC1.1.2, AC1.2.5]
  - id: BR4.3
    statement: "201 行目を読んだら TOO_MANY とし選択肢を返さず、200 行以下ならすべてを選択肢として返す"
    category: constraint
    applies_to: "REFERENCE の選択肢"
    trigger: "読み終えたとき"
    logic: "IF 読んだ行の数（同じ値を落とす前）が 201 THEN status = TOO_MANY、options なし ELSE status = AVAILABLE、options = BR4.4 の後の行。境界: 199・200 は AVAILABLE、201 は TOO_MANY"
    violation: "TOO_MANY は項目の状態で、code ではない（ピッカーへの案内の文言は U3）"
    source: [FR1.2, Q1, AC1.2.1, AC1.2.2, AC1.2.4, AC1.1.9]
  - id: BR4.4
    statement: "同じ値の行が2行以上あるときは、並びの最初の行だけを選択肢にする"
    category: calculation
    applies_to: "REFERENCE の選択肢"
    trigger: "AVAILABLE の選択肢を作るとき"
    logic: "読んだ行を BR4.2 の並びの順に見て、すでに出した値と同じ値の行を落とす（最初のラベルを使う）。値の同じさは、check と同じ型つきの値の厳密な一致（BR7.6）で決める（文字は大文字と小文字・末尾の空白を区別し、小数は数として比べ 1.0 と 1 を同じとする）。純粋な関数で、jqwik の性質（出力の値に重なりが無い・入力の最初の出現の順を保つ）で確かめる"
    violation: "—"
    source: [Q1, AC1.4.4]
  - id: BR4.5
    statement: "参照先が空のときは、空の選択肢で AVAILABLE を返し、誤りにしない"
    category: policy
    applies_to: "REFERENCE の選択肢"
    trigger: "0 行のとき"
    logic: "IF 読んだ行が 0 THEN status = AVAILABLE、options = 空。必須の項目で保存できないことの扱いは U2・U3"
    violation: "—"
    source: [AC1.1.6]
  - id: BR4.6
    statement: "選択肢の状態は項目ごとで、ほかの項目と定義の取得を失敗にしない"
    category: policy
    applies_to: "定義の API"
    trigger: "参照の項目が NO_PERMISSION・TOO_MANY のとき"
    logic: "各項目の状態を独立に決め、ほかの項目は通常どおり返す。対象DB の失敗（BR1.5）だけは定義の取得ごと失敗にする"
    violation: "—"
    source: [FR1.2, A2, AC1.2.3]
  - id: BR4.7
    statement: "同じ参照先（テーブル・valueColumn・ラベルに使うカラム）を指す項目が複数あるときは、要求の中で1回だけ読む"
    category: calculation
    applies_to: "定義の API"
    trigger: "選択肢を読むとき"
    logic: "要求の中だけで、読んだ結果を参照先の組で使い回す。権限の判定は項目ごとに行う（同じ参照先なら同じ結果）。要求を越えて持たない（キャッシュは範囲の外）"
    violation: "—"
    source: [NFR3, AC1.1.5]
  - id: BR4.8
    statement: "選択肢は reference.options に入れ、既存の choices は DSL の固定の選択肢（FIXED）だけのままにする"
    category: constraint
    applies_to: "ColumnDefinition"
    trigger: "応答を作るとき"
    logic: "options の要素は value（BR7.2・BR7.3）と label（BR7.3〜BR7.5）。choices には参照の選択肢を入れない"
    violation: "—"
    source: [C2, FR1.1, AC1.1.1]

  # ── BR5 LOOKUP のピッカー ──
  - id: BR5.1
    statement: "ピッカーの情報は、使える検索の条件と、一覧の列（lookupList の順）だけを返す"
    category: constraint
    applies_to: "PickerInfo"
    trigger: "LOOKUP で AVAILABLE のとき"
    logic: "searchFields = BR3.2 で使える条件（DSL の並び。表示名は参照先の DSL の表示名）。listColumns = lookupList を order の昇順（同じ order は DSL の並び）。searchFields と listColumns の各項目に、そのカラムの valueType（既存の ColumnDefinitionResponse.valueType と同じ語彙。businessdata.domain.ValueType の値）を載せ、画面が入力の種類を選べるようにする。参照先の名前は載せない（BR3.6）。LOOKUP は件数を数えず、TOO_MANY にならない"
    violation: "—"
    source: [FR2.1, FR2.3, C2, AC1.3.4]
  - id: BR5.2
    statement: "検索（conditions）は LOOKUP のカラムだけに受け付ける"
    category: validation
    applies_to: "参照先の検索 API"
    trigger: "SEARCH の要求のとき"
    logic: "IF 参照元のカラムが REFERENCE THEN 403 の同じ応答（REFERENCE の候補は定義の選択肢で返す）。VALUE は REFERENCE・LOOKUP の両方に受け付ける"
    violation: "403 ACCESS_DENIED（BR1.3）"
    source: [C2, FR2.2]
  - id: BR5.3
    statement: "条件のカラムは、その要求の写しで作ったピッカーの searchFields と文字どおり一致しなければならない"
    category: validation
    applies_to: "ReferenceCondition.column"
    trigger: "SEARCH の要求のとき"
    logic: "IF 条件のカラムが searchFields に無い（lookupSearch に無い・DSL に無い・主権限が NONE・使えない演算子のカラム）THEN 403 の同じ応答。参照先は読まない"
    violation: "403 ACCESS_DENIED（BR1.3）。状態は変わらない"
    source: [FR2.2, AC2.1.8, AC2.1.9]
  - id: BR5.4
    statement: "値が空の条件は使わず、残った条件をすべて AND で当てる"
    category: calculation
    applies_to: "SEARCH の条件"
    trigger: "条件を組むとき"
    logic: "値が空文字の条件は条件として使わない。残りが0なら BR1.4 の 400（サーバーは検索しない）。残りは AND"
    violation: "—"
    source: [X8, AC2.1.4]
  - id: BR5.5
    statement: "部分一致（CONTAINS）は既存の部分一致の書き方で、%・_・エスケープの文字を文字として扱う"
    category: constraint
    applies_to: "CONTAINS の条件"
    trigger: "SQL を組むとき"
    logic: "既存の ContainsPattern と LIKE ? ESCAPE で渡す（MySQL・MariaDB の \\ も文字として扱う既存の扱いのまま）。EQUALS はエスケープしない。値はバインド変数で渡す"
    violation: "—"
    source: [FR2.6, AC2.1.7]
  - id: BR5.6
    statement: "EQUALS の値はカラムの型として読み、読めなければ 400 VALIDATION_FAILED を返す"
    category: validation
    applies_to: "SEARCH の条件の値"
    trigger: "判定の順の ④"
    logic: "既存の型の読み取り（BR7.1 と同じ部品）で読む。IF 読めない THEN 400 VALIDATION_FAILED、fieldErrors = {field: 条件のカラム, reason: INVALID_VALUE}。カラムは searchFields で利用者に見せたものなので、存在を漏らさない"
    violation: "400 VALIDATION_FAILED。対象DB に問い合わせない"
    source: [Q2, FR2.6]
  - id: BR5.7
    statement: "検索は1ページ 20 件で、件数を数えず、21 行目まで読んで次の有無を返す"
    category: calculation
    applies_to: "SEARCH の結果"
    trigger: "検索するとき"
    logic: "読み飛ばす数 = (page-1)×20、読む行 = 21 まで。rows = 最初の 20 行、hasNext = 21 行目があったか。最後のページより後は rows = 空・hasNext = false の 200。件数の文は流さない"
    violation: "—"
    source: [FR2.3, QD, C2, AC2.1.2, AC2.1.3]
  - id: BR5.8
    statement: "検索の並びは、ラベルの昇順・値の昇順・参照先の主キーの昇順で、valueColumn が NULL の行は出さない"
    category: calculation
    applies_to: "SEARCH の結果と VALUE の1件読み"
    trigger: "SQL を組むとき"
    logic: "BR4.2 と同じ並び（NULL のラベルは最後。ReferenceReadSpec.order の固定の並び、BR11.4。利用者は並びを選べない）と、valueColumn IS NOT NULL の条件。同じラベル・同じ値の行の順は主キーで決め、主キーの無い参照先では約束しない"
    violation: "—"
    source: [FR2.3, X5, AC2.1.16]
  - id: BR5.9
    statement: "検索の行は、値・ラベル・lookupList の列の値だけを返し、同じ値の行を落とさない"
    category: constraint
    applies_to: "ReferenceRow（SEARCH）"
    trigger: "応答を作るとき"
    logic: "value（BR7.2）・label（BR2.5）・cells（listColumns の並び。バイナリ・password は null）。応答に出すのは lookupList のカラムと valueColumn とラベルのカラムだけ。ページをまたいで並びが割れないよう、同じ値の行も落とさない（どれを選んでも同じ値）"
    violation: "—"
    source: [FR2.3, AC2.1.2, Q1]

  # ── BR6 ラベルの1件読み ──
  - id: BR6.1
    statement: "ラベルの1件読み（value）は、REFERENCE・LOOKUP の両方に、検索と同じ権限の判定で受け付ける"
    category: authorization
    applies_to: "VALUE の要求"
    trigger: "判定の順の ③"
    logic: "参照元のカラムが FULL（BR3.3）で accessOf が AVAILABLE（BR3.1）のときだけ読む。ほかは 403 の同じ応答"
    violation: "403 ACCESS_DENIED（BR1.3）"
    source: [FR2.5, C2, AC2.1.5]
  - id: BR6.2
    statement: "ラベルを引く値を valueColumn の型として読めないときは、問い合わせずに 403 の同じ応答にする"
    category: validation
    applies_to: "VALUE の値"
    trigger: "値を読むとき"
    logic: "BR7.1 で読む。IF 読めない THEN 403 ACCESS_DENIED（行が無いときと同じ。対象DB に問い合わせない）"
    violation: "403 ACCESS_DENIED（BR1.3）"
    source: [Q2, AC1.4.5, AC2.3.2]
  - id: BR6.3
    statement: "ラベルの1件読みは、値の一致する行を BR5.8 の並びで1行だけ読み、その行のラベルを返す"
    category: calculation
    applies_to: "VALUE の結果"
    trigger: "値を読めたとき"
    logic: "条件 valueColumn = 値、並び BR5.8、読む行は1（BR8.6）。同じ値の行が2行以上でも、並びの最初の行のラベルを返す。rows = [{value, label, cells: 空}]、page = null、hasNext = false。一致は DB の比べ方（照合順序）に従う決めた動作とする: MySQL・MariaDB の大文字と小文字を区別しない照合では、abc の値で ABC の行のラベルが返りうる（ラベルの表示だけで、保存の可否は check の厳密な一致 BR9.4 が決める）。3種類の DB の結合テストで、大文字と小文字だけが違う値でこの動作を明示して確かめる"
    violation: "—"
    source: [Q1, FR2.5, AC2.1.5, AC2.3.1, AC1.4.4]
  - id: BR6.4
    statement: "ラベルの1件読みで行が無いときは、200 の 0 件にせず 403 の同じ応答にする"
    category: authorization
    applies_to: "VALUE の結果"
    trigger: "0 行のとき"
    logic: "IF 読んだ行が 0 THEN 403 ACCESS_DENIED（権限が無いときと区別しない）"
    violation: "403 ACCESS_DENIED（BR1.3）"
    source: [C2, FR2.2a, AC2.1.11, AC2.3.2]

  # ── BR7 値の型 ──
  - id: BR7.1
    statement: "受け取った参照の値は、参照先の valueColumn の DSL の型に、サーバー側で先に変えてから束ねる"
    category: validation
    applies_to: "VALUE の値・保存前の確かめの値・EQUALS の条件の値"
    trigger: "束ねる前"
    logic: "既存の型の読み取り（ColumnValueCodec の型ごとの読み取りと範囲の確かめ。前の Intent 261016-business-data-views の U1 の決まり 4.x・7.8）で型つきの値にし、型ごとのセッターで束ねる。IF 変えられない（数の型に数でない文字・範囲の外など）THEN 対象DB に問い合わせない（VALUE は BR6.2、確かめは BR9.3、条件は BR5.6）。3種類の DB で同じ結果になる"
    violation: "文字のまま束ねて DB ごとに結果を割らない"
    source: [Q2, AC1.4.5]
  - id: BR7.2
    statement: "返す値とラベルは、既存の1か所の型の対応で文字にする"
    category: calculation
    applies_to: "ReferenceOption・ReferenceRow"
    trigger: "行を読むとき"
    logic: "既存の ColumnValueCodec の対応のまま（BIGINT・DECIMAL は文字、日時はタイムゾーンを足さずに DB の値のまま）。値の同じさ（BR4.4・BR9.4）はこの文字ではなく、型つきの値の厳密な一致（BR7.6）で比べる"
    violation: "—"
    source: [TP, C2, AC1.1.9, AC2.1.16]
  - id: BR7.3
    statement: "ラベルと列の値は既存の一覧の文字数の上限で切り、値は切らない"
    category: constraint
    applies_to: "ReferenceOption・ReferenceRow"
    trigger: "行を読むとき"
    logic: "label と cells は既存の一覧の上限（ColumnValueCodec.LIST_TEXT_LIMIT、100）で切る（切ったことの印は契約に無いため返さない）。value は主キーと同じく切らない（保存と照合に使うため）。SQL の選び方への対応: 既存の RowSqlBuilder の一覧の選び方（listSelection）は、主キーのカラムを dialect.keyExpression・上限 Integer.MAX_VALUE（Selected の key = true）で切らずに選び、それ以外の一覧のカラムを dialect.selectExpression（STRING は SUBSTRING(列, 1, 上限+1)）・上限 LIST_TEXT_LIMIT で切って選ぶ。参照先の読み取りでは、ReferenceReadSpec（BR11.4）が valueColumn を主キーと同じ選び方（keyExpression・上限なし）で、主キーかどうかに関わらず切らずに選び、ラベルのカラムと lookupList の列だけを selectExpression・LIST_TEXT_LIMIT で選ぶ。valueColumn がラベルや一覧の列を兼ねるときも、値としては切らない選び方の方を使い、label・cells の側だけを上限で切る。確かめ: 値が 100 文字を超える（101 文字・上限ちょうどの 100 文字の両方を含む）主キーでない文字の valueColumn を持つ参照先で、選択肢・検索・ラベルの value が切れずに全文で返り、その値をそのまま check に渡すと FOUND になる境界のテストを置く（3種類の DB）"
    violation: "—"
    source: [Q2, TP]
  - id: BR7.4
    statement: "ラベルのカラムや列がバイナリか password のときは、値を読まず null を返す"
    category: constraint
    applies_to: "ReferenceOption.label・ReferenceRow.label・cells"
    trigger: "行を読むとき"
    logic: "IF カラムがバイナリ OR password THEN null（値そのものは返す。画面は値を出す）"
    violation: "—"
    source: [Q2, AC1.4.5]
  - id: BR7.5
    statement: "ラベルが NULL の行は label を null で返す"
    category: calculation
    applies_to: "ReferenceOption・ReferenceRow"
    trigger: "行を読むとき"
    logic: "IF ラベルが NULL THEN label = null（並びは最後。画面は値を出す）"
    violation: "—"
    source: [FR1.2, AC1.1.2]
  - id: BR7.6
    statement: "参照の値の同じさは、valueColumn の型つきの値の厳密な一致で決め、3種類の DB で同じ結果にする"
    category: calculation
    applies_to: "check（BR9.4）・選択肢の重複の除去（BR4.4）"
    trigger: "値を比べるとき"
    logic: "読んだ行の値と比べる相手を、既存の型の読み取り（BR7.1）で valueColumn の型つきの値にしてから比べる純粋な関数 sameReferenceValue で決める。文字（STRING）はコードポイントの完全な一致（大文字と小文字・末尾の空白を区別する。DB の照合順序に頼らない）。小数（DECIMAL）は数として比べる（1.0 と 1 は同じ）。整数・長い整数は数の一致。真偽値・日付・日時・時刻は型つきの値の一致（時差つきは UTC にそろえた値）。型として読めない行の値は一致しないとみなす。jqwik の性質（反射的・対称的、文字の大文字と小文字・末尾の空白の違いは一致しない、小数の末尾の 0 の違いは一致する）で確かめる"
    violation: "—"
    source: [Q1, Q2, AC1.4.4, AC1.4.5]

  # ── BR8 接続と時間 ──
  - id: BR8.1
    statement: "参照先の読み取りは、ReadPurpose に足した REFERENCE の時間の上限で行う"
    category: constraint
    applies_to: "選択肢・検索・ラベル・保存前の確かめの読み取り"
    trigger: "読み取りの口を借りるとき"
    logic: "targetdb.domain の ReadPurpose に REFERENCE を足し、TargetDbProperties.QueryTimeout に値を足す（値は NFR 設計。既存の LIST・DETAIL の既定 5 秒が目安）。4つの読み取りで共通の目的にする"
    violation: "時間切れは BR1.5"
    source: [QD, C5, FR1.5, AC2.1.13, AC3.5.3]
  - id: BR8.2
    statement: "1要求で借りる対象DB の読み取りの接続は1回までで、定義では読む REFERENCE をすべて同じ接続で順に読む"
    category: constraint
    applies_to: "定義・検索・ラベル"
    trigger: "読み取りのとき"
    logic: "定義: 読む項目（BR3.3・BR3.1 で AVAILABLE になりうる REFERENCE）があるときだけ1回借り、その中で順に読む。読む項目が無ければ借りない。検索・ラベル: 1回借りて文を1つだけ流す（件数の文は流さない）。書き込みの接続は使わない"
    violation: "—"
    source: [NFR3, C5, AC1.1.5, AC3.5.1]
  - id: BR8.3
    statement: "権限の写しを取り終えてから対象DB の接続を借り、内部DB の接続を持ったまま対象DB を待たない"
    category: constraint
    applies_to: "定義・検索・ラベル"
    trigger: "要求を受けたとき"
    logic: "写し（内部DB の短い読み取り）→ 内部DB の接続を返す → 対象DB の読み取りの口を借りる。内部DB のトランザクションを開かない"
    violation: "—"
    source: [NFR3, TP, AC3.5.2]
  - id: BR8.4
    statement: "定義の取得で参照先を読む全体に時間の上限を置き、超えたら残りを読まず定義の取得ごと 503 にする"
    category: constraint
    applies_to: "定義の API（purpose が CREATE・UPDATE）"
    trigger: "選択肢を読むとき"
    logic: "注入した時計で、最初の選択肢を読む前に締め切りを決める（全体の上限の値は NFR 設計）。各問い合わせの前に残りの時間を求め、秒に切り上げる（例: 残り 2.3 秒は 3 秒）。IF 残りが 1 秒未満 THEN その文を流さず、定義の取得ごと 503 BUSINESS_TARGET_DB_TIMEOUT にする。ELSE その文の上限を min(REFERENCE の上限の秒, 切り上げた残りの秒) に絞る。絞る置き場: ReferenceLookup（service）が、借りた TargetReadSession から、同じ product・connection・brokenMark（同じ AtomicBoolean）を持ち queryTimeoutSeconds だけを絞った値にした TargetReadSession を、既存の公開の4引数のコンストラクターで文ごとに作り直して search に渡す。search の引数は足さない。理由: RowSqlExecutor は文ごとに session.queryTimeoutSeconds() をそのまま setQueryTimeout に当てる作りで、絞った session を渡すだけで既存の search の中身を変えずに効く。brokenMark を共有するため、絞った session で立てた壊れた印は、借りた口が返すときに見る元の session にもそのまま見える。search に秒の引数を足す案は、search の形を一覧・詳細と分けることになり、ほかのメソッドの上限の扱いとも食い違うため選ばない。targetdb の型（TargetReadSession・ReadPurpose の貸し出し）は変えない。切り上げのため、1文が全体の締め切りを最大 1 秒未満超えうる（全体の上限の値を NFR 設計で決めるときにこの幅を見込む）。REFERENCE の数には上限を置かない（DSL のカラムの数の既存の上限が実際の上限になる）。確かめ: 注入した時計で残りを 0.5 秒・1.0 秒・2.3 秒にした単体テストで、それぞれ文を流さず 503、上限 1 秒、上限 min(REFERENCE の上限, 3) 秒になること"
    violation: "503 BUSINESS_TARGET_DB_TIMEOUT（BR1.5）"
    source: [Q3, AC1.1.4, AC1.1.5]
  - id: BR8.5
    statement: "時間切れ・接続の失敗の後は、同じ接続で続けて読まない"
    category: constraint
    applies_to: "定義の API"
    trigger: "1つの項目の読み取りが失敗したとき"
    logic: "既存の印（接続を壊れたとみなす）を立て、残りの項目を読まずに BR1.5 で定義の取得ごと失敗にする"
    violation: "—"
    source: [AC1.1.4, X2]
  - id: BR8.6
    statement: "読む行の数は、問い合わせの上限と JDBC の取り出しの上限の両方で絞る"
    category: constraint
    applies_to: "参照先の SQL"
    trigger: "SQL を流すとき"
    logic: "REFERENCE の選択肢と countState は 201、SEARCH は 21（読み飛ばす数は問い合わせの OFFSET）、VALUE は 1、保存前の確かめ（check）は 201（比べる 200 行と、まだあるかを知るだけの 1 行）。問い合わせの上限（LIMIT）と取り出しの上限（setMaxRows）を同じ値にし、上限を超える行を内部で読まない（10,000 行の参照先でも読む行は 201）"
    violation: "—"
    source: [FR1.2, AC1.2.5, AC2.1.3, TP]
  - id: BR8.7
    statement: "対象DB の失敗は、code に写す所で WARN を1行だけ出し、名前・値・メッセージを出さない"
    category: policy
    applies_to: "参照先の読み取りの失敗"
    trigger: "失敗を code に写すとき"
    logic: "既存と同じキー（purpose = REFERENCE・reason・sqlState・vendorCode・exceptionClass）だけを出す"
    violation: "—"
    source: [NFR1, AC1.4.3]

  # ── BR9 保存前の確かめの口（U2 が呼ぶ） ──
  - id: BR9.1
    statement: "保存前の確かめの口 check は、写しから作った参照元の材料・カラム・値・借りている読み取りの接続を受け、結果の型で返す"
    category: constraint
    applies_to: "ReferenceLookup.check（契約 C1）"
    trigger: "U2 が保存の前に呼ぶとき"
    logic: "入力: 参照元の判定の材料と同じ写し（BR2.2）・参照元のカラム・ReferenceValue・U2 が REFERENCE の目的で借りた読み取りの接続。出力: FOUND・NOT_FOUND_OR_DENIED・FAILED(理由)。check は自分で接続を借りず、書き込みの接続を使わない。複数の参照は U2 が同じ接続で順に呼ぶ。U2 に向けた口は check と countState（BR9.7）の2つ"
    violation: "—"
    source: [C1, ADR-003, NFR3]
  - id: BR9.2
    statement: "参照でないカラム、または accessOf が NO_PERMISSION の参照は、問い合わせずに NOT_FOUND_OR_DENIED を返す"
    category: authorization
    applies_to: "check"
    trigger: "check の始め"
    logic: "IF カラムが REFERENCE・LOOKUP でない OR accessOf = NO_PERMISSION THEN NOT_FOUND_OR_DENIED。参照元のカラムの書ける・書けないは U2 の既存の決まりで先に判定される"
    violation: "—"
    source: [FR3.1, C1]
  - id: BR9.3
    statement: "check の値を valueColumn の型として読めないときは、問い合わせずに NOT_FOUND_OR_DENIED を返す"
    category: validation
    applies_to: "check"
    trigger: "値を読むとき"
    logic: "BR7.1 で読む。IF 読めない THEN NOT_FOUND_OR_DENIED（U2 は REFERENCE_NOT_FOUND に写す）"
    violation: "—"
    source: [Q2, AC1.4.5]
  - id: BR9.4
    statement: "check は DB の比べ方で値の一致する行を 200 行まで読み、型つきの値として要求の値と厳密に一致する行が1行以上あるときだけ FOUND とする"
    category: calculation
    applies_to: "check"
    trigger: "値を読めたとき"
    logic: "条件 valueColumn = 変えた型つきの値（DB の比べ方・照合順序での一致）、出すのは valueColumn だけ（切らない選び方。BR7.3）、並びは BR5.8、読む行は 201（BR8.6）。比べるのは最初の 200 行で、201 行目はまだ行があることを知るためだけに読み、比べない（結果を変えない）。各行の値を valueColumn の型つきの値に変え、要求の値と BR7.6 で比べる。IF 厳密に一致する行が1行以上 THEN FOUND ELSE NOT_FOUND_OR_DENIED（DB の比べ方では一致したが厳密には一致しない、たとえば MySQL・MariaDB の大文字と小文字を区別しない照合で ABC しか無い参照先に abc を送ったときや、PAD SPACE の照合で末尾の空白だけが違うときも NOT_FOUND_OR_DENIED）。同じ値の行が2行以上あっても FOUND。文字は大文字と小文字・末尾の空白を区別し、小数は数として比べる（1.0 は 1 と一致）。これで3種類の DB で結果が同じになる。DB の比べ方で一致する行が 200 行を超え、その中に厳密な一致が無いときは NOT_FOUND_OR_DENIED（既知の制約。実際には照合で同じとみなす値が 200 行を超えるときだけ起きる）。FAILED は理由（TIMEOUT・UNAVAILABLE・MISMATCH）を保つ（BR9.5）。確かめ: 3種類の DB の結合テストで、大文字と小文字だけが違う値・末尾の空白だけが違う値・1.0 と 1 の小数の値を送り、どの DB でも同じ結果（前の2つは NOT_FOUND_OR_DENIED、小数は FOUND）になること"
    violation: "—"
    source: [Q1, Q2, FR3.1, AC1.4.4]
  - id: BR9.5
    statement: "check の対象DB の失敗は、理由つきの FAILED で返し、写しは U2 が行う"
    category: policy
    applies_to: "check"
    trigger: "読み取りが失敗したとき"
    logic: "TIMEOUT・UNAVAILABLE・MISMATCH（READ_CONVERSION を含む）を FAILED の理由にする（BR1.5 と同じ分け方で U2 が既存の code に写す）。時間切れ・断絶では接続に壊れた印を立てる（BR8.5）"
    violation: "—"
    source: [C1, C3, AC3.5.3]
  - id: BR9.6
    statement: "check は候補の多さ（TOO_MANY）を判定せず、件数は countState が返し、どちらも値をログ・監査に出さない"
    category: constraint
    applies_to: "check・countState"
    trigger: "常に"
    logic: "check は値の存在と読む権限だけを見る（BR9.4 で読む 201 行目は件数の判定に使わない）。参照先の候補の多さは countState（BR9.7）が選択肢と同じ読み方で返す。どの参照を確かめるか（作成のすべて・更新の変わったものだけ）、NULL の扱い、countState を使った候補が多い必須の項目の拒否、409 と監査への写しは U2 が持つ"
    violation: "—"
    source: [C1, C3, C4, AC1.4.2]
  - id: BR9.7
    statement: "U2 に向けた2つ目の口 countState(target) は、呼び出し側が借りた読み取りの接続で、REFERENCE の選択肢と同じ読み方で 201 行まで読み、AVAILABLE・TOO_MANY・NO_PERMISSION・FAILED(理由) を返す"
    category: calculation
    applies_to: "ReferenceLookup.countState（契約 C1 に足す口）"
    trigger: "U2 が保存の前に参照先の候補の多さを知りたいとき"
    logic: "入力 target: 参照元の判定の材料と同じ写し（BR2.2）・参照元のカラム。あわせて U2 が REFERENCE の目的で借りた読み取りの接続を受け、自分では借りず、書き込みの接続を使わない（check と同じ）。IF 参照元のカラムが REFERENCE・LOOKUP でない OR accessOf = NO_PERMISSION（BR3.1）THEN 問い合わせずに NO_PERMISSION。IF kind = LOOKUP THEN 問い合わせずに AVAILABLE（LOOKUP は件数を数えない。BR5.1）。ELSE REFERENCE の選択肢と同じ ReferenceReadSpec（valueColumn が NULL の行を除く・件数の文を流さない・固定の並び・読む行 201。BR4.2・BR8.6）で読み、IF 読んだ行（同じ値を落とす前）が 201 THEN TOO_MANY ELSE AVAILABLE（境界: 200 は AVAILABLE、201 は TOO_MANY。BR4.3 と同じ）。対象DB の失敗は FAILED（TIMEOUT・UNAVAILABLE・MISMATCH）で返し、時間切れ・断絶では接続に壊れた印を立てる（BR9.5 と同じ）。参照元のカラムが用途で入力できるか（BR3.3）は見ない（U2 の既存の決まりで先に判定される）。値・ラベルを返さず、ログ・監査に出さない（BR9.6）"
    violation: "—"
    source: [C1, C3, FR1.2, ADR-003, NFR3]

  # ── BR10 漏えい ──
  - id: BR10.1
    statement: "参照先の値を持つ型は、文字列にするときに値を伏せ、行を汎用の Map のまま層をまたいで渡さない"
    category: constraint
    applies_to: "ReferenceOption・ReferenceRow・ReferenceRows・ReferenceRowsQuery・ReferenceCondition・ReferenceValue・ReferenceField・ReferenceTarget"
    trigger: "TraceAspect が web・service・domain・repository の引数と戻り値を TRACE に出すとき"
    logic: "toString を上書きし、値・ラベル・セル・条件の値を出さない（件数や種類だけ）。ReferenceTarget はテーブルの名前と kind だけ。TRACE を有効にした XxxSecretLeakIT で、定義・検索・ラベル・確かめの4つの経路を確かめる"
    violation: "—"
    source: [TP, PM, AC1.4.2, AC1.4.6]
  - id: BR10.2
    statement: "JDBC の例外のメッセージは、応答にもアプリのログにも出さない"
    category: constraint
    applies_to: "参照先の読み取りの失敗"
    trigger: "失敗のとき"
    logic: "既存の RowQueryFailure（メッセージと原因を捨てた形）だけを上へ渡す。ログは BR8.7 のキーだけ"
    violation: "—"
    source: [PM, AC1.1.4, AC1.4.3, AC2.1.13]
  - id: BR10.3
    statement: "参照の値と検索の値は本文で運び、道（パス・クエリ）に載せない"
    category: constraint
    applies_to: "参照先の検索 API"
    trigger: "API の形"
    logic: "道は固定の /api/tables/reference/rows。値・条件・名前はすべて本文。トレースの道の属性に値が残らないことを、外部へ送る設定を有効にした結合テストで確かめる"
    violation: "—"
    source: [TP, PM, AC2.1.15]
  - id: BR10.4
    statement: "誤りの応答に、参照先の名前・値・接続情報を含めない"
    category: constraint
    applies_to: "この単位の誤りの応答"
    trigger: "誤りを返すとき"
    logic: "403 は fieldErrors を付けない同じ本文。400 の fieldErrors は利用者が送ったカラム名だけ（BR5.6）。503・409 は既存の本文"
    violation: "—"
    source: [PM, NFR1, AC1.4.2, AC1.3.5]

  # ── BR11 構造 ──
  - id: BR11.1
    statement: "参照先の検索 API は「ログインだけ」に分類し、権限は業務処理の層で判定する"
    category: authorization
    applies_to: "POST /api/tables/reference/rows"
    trigger: "コード生成"
    logic: "@ApiAccess(AUTHENTICATED) を付ける。分類を足し忘れると既存の ApiAccessArchitectureTest が落ちる"
    violation: "—"
    source: [C2, TP, AC2.1.14]
  - id: BR11.2
    statement: "行の SQL の新しいメソッドを作らず、既存の search に参照先の読み取りの指定を渡せるようにする"
    category: constraint
    applies_to: "RowSqlGateway（RowSqlExecutor・RowSqlBuilder・RowDialect）"
    trigger: "コード生成"
    logic: "既存の search に ReferenceReadSpec（中身は BR11.4）を渡せるようにし、選択肢・検索・ラベル・check・countState のすべてが同じ search を使う。指定が無い（既定の）ときは今の一覧と同じ文になる。文ごとの時間の上限は search の引数ではなく、渡す TargetReadSession の queryTimeoutSeconds で絞る（BR8.4）。prepareStatement・createStatement を呼ぶ場所と、SpotBugs の SQL_ の除外は増やさない"
    violation: "新しいメソッドが要るとわかったときは、ADR-006 の切り替え先として、除外の追加をコード生成の計画に明記して承認を得る"
    source: [ADR-002, ADR-006, TP, AC1.4.7]
  - id: BR11.3
    statement: "既存の境界テストを緩めず、新しいクラスはコード生成の計画で承認を得る"
    category: constraint
    applies_to: "BusinessDataBoundaryArchitectureTest・TargetDbBoundaryArchitectureTest・RoleBoundaryArchitectureTest"
    trigger: "コード生成"
    logic: "差分は足す方向だけにする。ReferenceLookup などの新しいクラスは計画に明記する（ADR-001）。ReadPurpose の値の追加は targetdb の境界（書き込み・DSL・web を知らない）を変えない"
    violation: "—"
    source: [ADR-001, C5, C6, TP, AC1.4.7]
  - id: BR11.4
    statement: "ReferenceReadSpec は、切らない値のカラム・切る表示の列・NULL の値の除外・固定の並び・件数の文の有無・読む行・読み飛ばす数を持ち、RowSqlBuilder はそれで選ぶ列と並びを組む"
    category: constraint
    applies_to: "ReferenceReadSpec・RowSqlBuilder"
    trigger: "参照先の SQL を組むとき"
    logic: "valueColumn: 切らずに選ぶ列（主キーと同じ dialect.keyExpression・上限なし。BR7.3）。displayColumns: ラベルのカラムと LOOKUP の lookupList の列で、dialect.selectExpression・LIST_TEXT_LIMIT で切って選ぶ（既存の listSelection の一覧の列と同じ選び方。バイナリ・password は選ばない。BR7.4）。excludeNullValue: valueColumn IS NOT NULL を条件に足す。order: 固定の並びのキーの並び（ラベルの昇順 → valueColumn の昇順 → 参照先の主キーのカラムの昇順。ラベルのカラムが valueColumn と同じなら重ねない）。ReferenceLookup が参照先の判定の材料から純粋な関数で作り、利用者の sortKey と既存の view.orderKeys からは作らない。RowSqlBuilder は spec があれば page(where, keys) の keys に spec.order を渡し、項は既存の dialect.orderTerm（昇順は NULL を最後。MySQL・MariaDB は IS NULL を先に置き、PostgreSQL は NULLS LAST）で作る。count: 件数の文を流すか（参照先では常に偽）。maxRows: LIMIT と setMaxRows の値（BR8.6）。offset: OFFSET の値。値の一致の条件（VALUE・check）と SEARCH の条件は、既存の where と同じく comparisonTarget = ? と既存の部分一致の書き方でバインド変数として渡す。既定の spec（無いとき）は今の listSelection・view.orderKeys・件数の文のままにする（BR12.4）"
    violation: "—"
    source: [ADR-002, X5, FR1.2, FR2.3, AC1.1.9, AC2.1.16]

  # ── BR12 互換 ──
  - id: BR12.1
    statement: "purpose が無い（VIEW）定義の応答は、今と同じ中身に reference = null を足しただけにする"
    category: constraint
    applies_to: "定義の API"
    trigger: "purpose が VIEW のとき"
    logic: "参照先を読まず、すべてのカラムの reference は null、formPart の lookup は今のまま text で返す。一覧・詳細の画面は purpose を付けない"
    violation: "—"
    source: [NFR7, X2, FR4.1, AC3.4.2, AC2.3.4, AC1.3.5]
  - id: BR12.2
    statement: "purpose が CREATE・UPDATE の定義では、formPart を DSL の値（lookup を含む）で返す"
    category: constraint
    applies_to: "ColumnDefinition.formPart"
    trigger: "purpose が CREATE・UPDATE のとき"
    logic: "lookup を text に置き換えない。書き込みの検証の材料（ColumnWriteRules）は lookup を今までどおり text として扱う（C3。U2）"
    violation: "—"
    source: [FR2.1, C2, C3]
  - id: BR12.3
    statement: "応答に足す欄は読み落としても壊れない形にし、DSL の書式と内部DB のスキーマを変えない"
    category: constraint
    applies_to: "定義の応答・DSL・移行ファイル"
    trigger: "コード生成"
    logic: "reference は任意の欄、purpose の既定は VIEW。backend/src/main/resources の移行ファイルと DSL の JSON Schema に差を作らない"
    violation: "—"
    source: [NFR7, A7, AC3.4.3, AC3.4.4]
  - id: BR12.4
    statement: "一覧・詳細の API の振る舞いは変えない"
    category: constraint
    applies_to: "POST /api/tables/rows/search・POST /api/tables/rows/get"
    trigger: "コード生成"
    logic: "search の指定の既定の値で今と同じ文・結果になる。一覧・詳細は参照先を読まず、参照している値を今のとおり値のまま返す。既存の結合テストがすべて通る"
    violation: "—"
    source: [FR4.1, ADR-002, AC3.4.1]
```

## 要約の表

| ID | 群 | 要点 |
|---|---|---|
| BR1.1 | 判定の順 | 定義: 401 → 400（purpose）→ 既存の 404・403 → 参照の項目 → 対象DB の失敗。参照先の権限の不足は 403 にしない |
| BR1.2 | 判定の順 | 検索 API: 401 → 400（形）→ 403（同じ応答）→ 400（値の型）→ 対象DB の失敗 → 403（VALUE で行が無い）→ 200 |
| BR1.3 | 認可 | 名前・権限・DSL・行の有無の拒否はすべて 403 `ACCESS_DENIED` の同じ応答 |
| BR1.4 | 入力 | 本文の形の誤りは DSL を見る前に 400（両方・どちらも無い、空、1,000 文字、page 1〜1,000,000） |
| BR1.5 | code | TIMEOUT 503・UNAVAILABLE 503・MISMATCH 409。定義は取得ごと失敗 |
| BR1.6・BR1.7 | code・監査 | 新しい code を作らない。読み取りは監査に残さない |
| BR2.1〜BR2.5 | 参照先 | DSL から引く、写しは1回、名前は DSL で照らす、形で拒否しない、ラベルのカラム（Q4） |
| BR3.1〜BR3.6 | 権限 | accessOf（1つでも欠けたら NO_PERMISSION）、使える条件は EQUALS・CONTAINS、参照元の条件（入力できないカラムは U3 が既存の入力不可の表示を先に出す）、要求ごと、NONE を出さない、名前を載せない |
| BR4.1〜BR4.8 | 選択肢 | CREATE・UPDATE だけ、201 行まで読む（固定の並び）、201 行目で TOO_MANY、同じ値は最初だけ（型つきの厳密な一致）、空は AVAILABLE、項目ごと、同じ参照先は1回、options に入れる |
| BR5.1〜BR5.9 | ピッカー | 情報の形（各項目に valueType）、LOOKUP だけ、searchFields と照らす、空の条件、エスケープ、値の型の 400、20 件・21 行目、固定の並び、行の形 |
| BR6.1〜BR6.4 | ラベル | 両方の種類、型で読めなければ 403、並びの最初の1行（DB の比べ方での一致）、行が無ければ 403 |
| BR7.1〜BR7.6 | 値 | DSL の型に先に変える（Q2）、既存の型の対応、ラベルと列だけを一覧の上限で切り値は切らずに選ぶ、バイナリは null、NULL は null、型つきの値の厳密な一致 |
| BR8.1〜BR8.7 | 接続と時間 | `REFERENCE` の目的、借りるのは1回、内部DB を持たない、全体の上限（Q3。残りを秒に切り上げ、1 秒未満なら流さず 503、絞った session を渡す）、失敗の後は読まない、行の上限、WARN |
| BR9.1〜BR9.7 | 確かめの口 | U2 が呼ぶ check の入出力、問い合わせない場合、200 行まで読み型つきの厳密な一致で FOUND、FAILED の理由、check は件数を見ない、countState が件数を返す |
| BR10.1〜BR10.4 | 漏えい | 伏せ字、JDBC のメッセージ、道に載せない、誤りの応答 |
| BR11.1〜BR11.4 | 構造 | API の分類、新しい SQL のメソッドを作らない、境界テスト、ReferenceReadSpec の中身 |
| BR12.1〜BR12.4 | 互換 | VIEW は今のまま、CREATE・UPDATE は formPart を DSL の値で、足すだけ、一覧・詳細を変えない |
