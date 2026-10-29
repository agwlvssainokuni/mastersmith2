# 業務の決まり — U2 u2-reference-write-check

出典の表記: FR・NFR・A は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、AC・X は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`、ADR は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/decisions.md`、C1〜C7 は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`、Q1・Q2 はこの段の `functional-design-questions.md` の答え（どちらも A）、QD は同じファイルの「決まっていること」、TP は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`、U1 は U1 の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（U2 が使う2つの口 `check` と `countState`。形は functional-spec.md の 10.1 節）、前 U2 は前の Intent の U2 `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/functional-design/rules.md`（既存の書き込みの決まり。この単位の ID とは別）。

この単位の ID は BR1.1 から振り直した。新しい code・項目の誤りの理由・監査の理由は作らず、既存のものを使い回す（1つの code に状態コードは1つだけ。TP）。時間の上限の値は U1 と同じく NFR 設計で決める。

```yaml
rules:
  # ── BR1 判定の順と応答 ──
  - id: BR1.1
    statement: "作成・更新の判定の順は、既存の①〜⑨の後、書き込みの前に、⑩ 保存前の参照の段を置く"
    category: policy
    applies_to: "作成・更新の API（POST・PATCH /api/tables/rows）"
    trigger: "要求を受けたとき"
    logic: "① 401 → ②③ 404 → ④ 403 → ⑤ 409 定義が古い → ⑥ 409 主キー・ビューの決まり → ⑦ 403 補助権限・FULL → ⑧ 409 書き込みの設定が無い → ⑨ 400 入力の誤り（BR3.6 で保留する誤りを除く）→ ⑩ 保存前の参照の段（読み取りの接続）→ ⑪ 書き込み（既存の一意の事前の問い合わせ・書き込み・違反の変換）。①〜⑨ で拒んだときは今のとおり対象DB に触れない。削除は ⑩ を通らない"
    violation: "最初に当たった拒否の code"
    source: [C3, ADR-003, FR3.1]
  - id: BR1.2
    statement: "保存前の参照の段は、確かめる・読むものがあるときだけ読み取りの接続を借り、無ければ借りない"
    category: constraint
    applies_to: "⑩ 保存前の参照の段"
    trigger: "⑨ を通ったとき"
    logic: "IF 作成で、本文に NULL でない値の参照のカラムがある、または必須の参照項目（BR3.1）がある OR 更新で、本文に NULL でない値の参照のカラムがある THEN 読み取りの接続を1回借りる ELSE 借りずに ⑪ へ進む（参照の無いテーブルの作成・更新の接続の数は今と同じ）"
    violation: "—"
    source: [NFR3, AC3.2.7]
  - id: BR1.3
    statement: "⑩ の中で複数の結果が出たときは、対象DB の失敗 → 行が無い → 400 → 409 の順で、最初のものを返す"
    category: policy
    applies_to: "⑩ 保存前の参照の段"
    trigger: "⑩ の結果をまとめるとき"
    logic: "接続を借りられない・読み取りの失敗（FAILED）はその場で止めて返す（以後の読み取りをしない）。更新前の行が無い・2行以上（BR2.6）は参照の確かめより先に返す。必須の参照項目の保留した誤りを戻したもの（BR3.5）があれば 400 VALIDATION_FAILED（それらの誤りだけ）。無ければ、確かめで拒否した項目（REFERENCE_NOT_FOUND・NOT_EDITABLE_NOT_NULL）をすべて並べて 409。どれも無ければ ⑪ へ進む"
    violation: "BR1.4 の写し"
    source: [AC3.2.6, AC3.1.8, C3]
  - id: BR1.4
    statement: "⑩ の結果は、既存の拒否と code に写し、新しい code を作らない"
    category: policy
    applies_to: "⑩ の結果と応答"
    trigger: "⑩ が拒否で終わったとき"
    logic: "確かめで拒否・必須の参照項目の行き止まり → WriteRejection.REFERENCE_NOT_FOUND（409 BUSINESS_REFERENCE_NOT_FOUND、項目の誤りつき）。必須の参照項目の 400 → VALIDATION_FAILED。行が無い → ROW_NOT_FOUND（404）、2行以上 → ROW_NOT_UNIQUE。FAILED の TIMEOUT → TARGET_DB_TIMEOUT（503）、UNAVAILABLE と接続を借りられない → TARGET_DB_UNAVAILABLE（503）、MISMATCH → TARGET_DB_MISMATCH（409）"
    violation: "写した code の Problem Details"
    source: [C3, QD, AC3.1.2, AC3.1.8]

  # ── BR2 確かめる参照の選び方 ──
  - id: BR2.1
    statement: "確かめる参照と参照先は、要求1件の権限の写しの DSL から引き、本文の名前や値から参照先を作らない"
    category: authorization
    applies_to: "⑩ の確かめる参照の選び方"
    trigger: "⑩ を始めるとき"
    logic: "参照のカラムは DSL の options が REFERENCE・LOOKUP のカラム。参照先（テーブル・valueColumn）は U1 の targetOf で同じ写しの DSL から引く。本文に足した参照先のテーブルの名前・許していないカラムは、既存の検証（⑨ の NOT_ALLOWED）で拒否され、反映されない。本文の値を権限の無い参照先の行を指す値に差し替えても、確かめで同じ 409 になる"
    violation: "400 NOT_ALLOWED（足した名前）または 409 REFERENCE_NOT_FOUND（差し替えた値）"
    source: [FR3.1, NFR1, AC3.1.4, C1]
  - id: BR2.2
    statement: "作成では、本文に NULL でない値がある参照のカラムをすべて確かめる"
    category: validation
    applies_to: "作成"
    trigger: "⑩"
    logic: "IF 作成 AND 参照のカラム AND 本文の値が NULL でない THEN 確かめる（ReferenceCheckItem、origin CREATE）。必須の参照項目（BR3.1）は BR3.2〜BR3.5 の扱いを先に当てる"
    violation: "BR4.4"
    source: [FR3.1, AC3.1.1, AC3.1.2]
  - id: BR2.3
    statement: "値が NULL で、カラムが NULL を許すときは確かめない。空文字列 \"\" は、既存の書き込みの検証（⑨）の結果にそのまま従い、⑨ が誤りにすれば「空」、通せば「値」として扱う（扱いは1つで、参照の状態によって変えない）"
    category: validation
    applies_to: "作成・更新の参照のカラム"
    trigger: "確かめる参照を選ぶとき"
    logic: "IF 値が NULL AND nullable AND NOT required THEN 確かめずに保存へ進む。\"\" の扱いは既存の WriteRequestValidator のまま変えない: (a) DSL の検証に REQUIRED があるカラム（NOT NULL から作った DB 由来の必須を含む）の \"\" → REQUIRED の誤り、(b) 文字でない型の \"\" → INVALID_VALUE の誤り、(c) 文字の型で REQUIRED が無いカラムの \"\" → 検証を通った文字の値 \"\"。(a)(b) は「空」で、⑨ の 400（作成の必須の参照項目は BR3.6 で保留）。(c) は「値」で、ほかの値と同じく確かめに回る（参照先の valueColumn に \"\" と文字どおり一致する行が無ければ 409 REFERENCE_NOT_FOUND）。lookup は今までどおり text として検証する（ColumnWriteRules を変えない）。状態ごとの期待結果は functional-spec.md の 4.3 節の表"
    violation: "400 REQUIRED・INVALID_VALUE（空）、または 409 REFERENCE_NOT_FOUND（値の \"\" が参照先に無い）"
    source: [FR3.2, AC3.1.5, C3, QD]
  - id: BR2.4
    statement: "更新では、本文の値が更新前の値と違う参照だけを確かめ、同じ値の参照は確かめない"
    category: validation
    applies_to: "更新"
    trigger: "⑩"
    logic: "更新前の行（BR2.6）の値と本文の値を BR2.5 で比べる。IF 同じ THEN 確かめない（参照先を読めない利用者の更新・入力不可の項目が同じ値で本文に入っている場合・保存済みの参照がすでに宙に浮いている場合も成功する）ELSE IF 本文の値が NULL THEN BR2.3 ELSE 確かめる（origin UPDATE_CHANGED）。値を別の存在しない値へ変えたら 409"
    violation: "BR4.4"
    source: [FR3.1a, AC3.2.1, AC3.2.2, AC3.2.3, AC3.2.5, AC1.5.4]
  - id: BR2.5
    statement: "値が同じかは、型つきの値（TypedValue）にしてから、型ごとの決まりで比べる"
    category: calculation
    applies_to: "更新の変わったかの判定（U1 の check の一致の比べ方も同じ決まりで、3種類の DB で同じ結果になる。BR4.3）"
    trigger: "2つの値を比べるとき"
    logic: "両方をカラムの型の読み方（既存の書き込みの検証と同じ ColumnValueReader）で型つきの値にする。整数は数として比べる（\"007\" と \"7\" は同じ）。小数は数の大きさで比べる（1.0 と 1 は同じ）。文字はコードポイントの並びが文字どおり同じときだけ同じ（大文字と小文字・末尾の空白が違えば違う）。日時は値として同じとき。真偽はそのまま。更新前の値が読めない・上限で切られているときは「違う」とする（安全側）"
    violation: "—"
    source: [AC3.2.4, ADR-006, Q2]
  - id: BR2.6
    statement: "更新前の行は、同じ読み取りの接続で主キーで1行を読み、無ければ参照の拒否より先に行が無い応答にする"
    category: validation
    applies_to: "更新"
    trigger: "⑩ で、本文に NULL でない値の参照のカラムがあるとき"
    logic: "既存の行の SQL の部品の detail（主キーの条件・最大2行）で読む。新しい SQL のメソッドは作らない。IF 0 行 THEN ROW_NOT_FOUND ELSE IF 2行以上 THEN ROW_NOT_UNIQUE ELSE 本文に入っている参照のカラムの値を PreviousReferenceValues にする。行の有無を参照の拒否で漏らさない"
    violation: "404 BUSINESS_ROW_NOT_FOUND、ROW_NOT_UNIQUE の既存の code"
    source: [AC3.2.6, ADR-003, ADR-006]
  - id: BR2.7
    statement: "確かめる参照はすべて確かめ、拒否された参照ごとに項目の誤りを1件ずつ並べる"
    category: policy
    applies_to: "⑩ の確かめ"
    trigger: "確かめる参照が2つ以上あるとき"
    logic: "DSL のカラムの並びで順に確かめる。NOT_FOUND_OR_DENIED では止めずに次へ進む。FAILED ではその場で止める（BR4.5）。項目の誤りは field にカラムの名前、reason に REFERENCE_NOT_FOUND を入れ、値を入れない"
    violation: "409 BUSINESS_REFERENCE_NOT_FOUND（項目の誤りを並べる）"
    source: [QD, C3, AC3.1.2, AC3.2.3]

  # ── BR3 作成の必須の参照項目（Q1: A） ──
  - id: BR3.1
    statement: "作成の必須の参照項目は、参照のカラムで、作成で入力でき、NULL を許さないもの（NOT NULL）とする"
    category: constraint
    applies_to: "作成"
    trigger: "⑨ の前"
    logic: "IF 作成 AND 参照のカラム AND ColumnAccess.editableOnCreate AND NOT nullable THEN 必須の参照項目。本文の状態は BR2.3 の扱いに合わせて決める: 本文に無い・null、または \"\" で ⑨ の既存の検証が空として誤り（REQUIRED・NOT_NULL・INVALID_VALUE）にしたもの → EMPTY。⑨ の既存の検証を通った値（文字の型で REQUIRED が無いカラムの \"\" を含む）→ VALUE。同じ本文は、参照の状態（BR3.2）によらず同じ bodyState になる。作成で入力できないカラムは今のまま（DB の NOT NULL 違反は既存の SAVE_REJECTED）"
    violation: "—"
    source: [AC1.5.2, X1, Q1]
  - id: BR3.2
    statement: "必須の参照項目の状態は、読めない（NO_PERMISSION）・多すぎる（TOO_MANY）・使える（AVAILABLE）の3つで決める"
    category: calculation
    applies_to: "作成の必須の参照項目"
    trigger: "⑩"
    logic: "U1 の countState(target) に、⑩ で借りた読み取りの接続を渡して状態を得る。countState は REFERENCE の選択肢と同じ読み方（参照先の読む権限を先に判定し、valueColumn が NULL でない行を 201 行目まで読む）で AVAILABLE・TOO_MANY・NO_PERMISSION・FAILED(理由) を返す。NO_PERMISSION は問い合わせない。LOOKUP のカラムは countState を呼ばず、U1 の accessOf が NO_PERMISSION なら NO_PERMISSION、そうでなければ AVAILABLE とする（LOOKUP は TOO_MANY にならない）。FAILED は BR4.5"
    violation: "FAILED(理由) は BR4.5 の 503・409 MISMATCH"
    source: [Q1, AC1.5.3, X1, ADR-005]
  - id: BR3.3
    statement: "必須の参照項目が NO_PERMISSION・TOO_MANY で本文に値があるときは、保存せず、権限の無い値と同じ 409 にする"
    category: authorization
    applies_to: "作成の必須の参照項目"
    trigger: "状態が NO_PERMISSION・TOO_MANY で、本文が VALUE"
    logic: "THEN 項目の誤り REFERENCE_NOT_FOUND（AC3.1.3 と同じ応答）。TOO_MANY では値が参照先にあっても拒否する（画面で入力できない項目に値を入れる経路を閉じる）"
    violation: "409 BUSINESS_REFERENCE_NOT_FOUND、reason REFERENCE_NOT_FOUND"
    source: [Q1, AC1.5.2, AC1.5.3, AC3.1.3]
  - id: BR3.4
    statement: "必須の参照項目が NO_PERMISSION・TOO_MANY で本文が空のときは、保存せず、入力できないカラムの NOT NULL の誤りにする"
    category: validation
    applies_to: "作成の必須の参照項目"
    trigger: "状態が NO_PERMISSION・TOO_MANY で、本文が EMPTY"
    logic: "THEN 項目の誤り NOT_EDITABLE_NOT_NULL（field はそのカラムの名前）。画面は US1.5 の文言を出し分ける。利用者は定義の状態ですでに読めない・多すぎると分かっており、値を送っていないため、参照先の存在は漏れない"
    violation: "409 BUSINESS_REFERENCE_NOT_FOUND、reason NOT_EDITABLE_NOT_NULL"
    source: [Q1, AC1.5.2, AC1.5.3, C3, X1]
  - id: BR3.5
    statement: "必須の参照項目が AVAILABLE のときは、本文が空なら保留した既存の検証の結果を戻し、値があれば通常の確かめに回す"
    category: validation
    applies_to: "作成の必須の参照項目"
    trigger: "状態が AVAILABLE"
    logic: "IF EMPTY AND ⑨ で保留した誤り（REQUIRED・NOT_NULL・INVALID_VALUE）がある THEN 400 VALIDATION_FAILED として返す（BR1.3）。ELSE IF EMPTY（保留した誤りが無いのは、本文に無く DSL に REQUIRED も無いときだけ。\"\" は BR3.1 で誤りがあれば EMPTY、無ければ VALUE になるため、ここに来ない）THEN 確かめずに既存の流れのまま ⑪ へ進む（DB の NOT NULL 違反は既存の変換）。ELSE（VALUE。文字の型で REQUIRED が無いカラムの \"\" を含む）BR2.2 のとおり確かめる"
    violation: "400 VALIDATION_FAILED（既存の理由）"
    source: [Q1, AC1.5.2, AC3.1.5]
  - id: BR3.6
    statement: "⑨ では、本文が EMPTY の必須の参照項目の誤りを保留し、ほかに誤りがあれば対象DB に触れずに 400 を返す"
    category: policy
    applies_to: "作成の ⑨"
    trigger: "⑨ の検証の結果を返すとき"
    logic: "必須の参照項目で EMPTY のカラムの誤り（REQUIRED・NOT_NULL・INVALID_VALUE）は ⑨ で返さず ⑩ へ回す。IF ほかのカラムに誤りがある THEN 400 VALIDATION_FAILED（ほかの誤り＋保留した誤りのうち accessOf が AVAILABLE のカラムのもの。NO_PERMISSION のカラムの保留した誤りは落とす）。この 400 では対象DB を読まないため、TOO_MANY のカラムが REQUIRED として並ぶことがある"
    violation: "400 VALIDATION_FAILED"
    source: [Q1, C3, AC1.5.2]
  - id: BR3.7
    statement: "更新では、必須の参照項目の行き止まりの拒否を当てず、値が変わらなければ成功させる"
    category: policy
    applies_to: "更新"
    trigger: "⑩"
    logic: "更新は BR2.4 だけで選ぶ。NO_PERMISSION の参照の値を変えれば確かめで 409（U1 の check は読めない参照先を問い合わせずに NOT_FOUND_OR_DENIED を返す）。TOO_MANY の参照の値を変えたときは、存在と読む権限だけを確かめる（サーバー側では件数で拒否しない。入力不可は画面の扱い）。NOT NULL の参照を空にした更新は既存の検証の 400"
    violation: "BR4.4"
    source: [AC1.5.4, AC3.2.1, X1, FR3.1a]

  # ── BR4 確かめの口の使い方と接続 ──
  - id: BR4.1
    statement: "⑩ の読み取りは、REFERENCE の目的で借りた読み取りの接続1本で順に行い、返してから書き込みの接続を借りる"
    category: constraint
    applies_to: "⑩・⑪"
    trigger: "⑩ を始めるとき"
    logic: "読み取りの接続を1回だけ借り、更新前の行（BR2.6）→ 必須の参照項目の状態（U1 の countState。BR3.2・BR4.7）→ 確かめ（U1 の check を参照ごとに）の順に同じ接続で行う。⑩ が PROCEED で終わったら読み取りの接続を返し、その後で書き込みの接続を1回借りる。書き込みのアカウントで参照先を読まない。1要求で借りる読み取りの接続は 0 か 1 回、書き込みの接続は 0 か 1 回"
    violation: "—"
    source: [ADR-003, NFR3, AC3.2.7, QD, C5]
  - id: BR4.2
    statement: "判定に使う権限の写しは解決の口で取った1つを使い回し、内部DB の接続を持ったまま対象DB を待たない"
    category: constraint
    applies_to: "⑩"
    trigger: "常に"
    logic: "参照元と参照先の判定に、①〜④ で取った同じ PermissionSnapshot を使う（取り直さない。C6）。⑩ の間、内部DB の接続を持たず、内部DB のトランザクションを開かない。監査の書き込みは ⑩・⑪ が終わって対象DB の接続を返した後（BR5.1）"
    violation: "—"
    source: [C6, NFR3, FR3.4, AC3.2.7]
  - id: BR4.3
    statement: "確かめの「ある」は、U1 の check が返す FOUND（参照先に本文の値と型つきの値として完全に一致する行があるとき）だけとし、U2 は check の結果をそのまま使う"
    category: validation
    applies_to: "U1 の check の結果の読み方"
    trigger: "check が返ったとき"
    logic: "U1 の check は、DB の比べ方で valueColumn が変換した値に当たる行を 200 行まで読み（ほかにあるかを知るためだけに 1 行多く読む）、各行の値を型つきの値にして、本文の値と完全に一致する行が1行以上あるときだけ FOUND を返す。比べ方は BR2.5 と同じ（文字は大文字と小文字・末尾の空白を区別する。小数は数で比べ、1.0 と 1 は同じ）。一致が無ければ NOT_FOUND_OR_DENIED、失敗は FAILED(TIMEOUT・UNAVAILABLE・MISMATCH)。結果は MySQL・MariaDB・PostgreSQL で同じになる。U2 は自分で比べ直さない。照合順序で当たる行が 200 行を超え、その中に完全一致の行が無いときは NOT_FOUND_OR_DENIED になる（U1 の読み方の決まりに従う）"
    violation: "409 REFERENCE_NOT_FOUND"
    source: [Q2, FR3.1, AC3.2.4, C1]
  - id: BR4.4
    statement: "確かめの結果を、拒否・項目の誤り・既存の code に写す"
    category: policy
    applies_to: "⑩ の確かめ"
    trigger: "check が返ったとき"
    logic: "FOUND → 次へ。NOT_FOUND_OR_DENIED → 項目の誤り REFERENCE_NOT_FOUND（存在しない値と読めない値で、状態コード・code・項目の誤りの構造・メッセージが同一）。FAILED(TIMEOUT) → 503 BUSINESS_TARGET_DB_TIMEOUT、FAILED(UNAVAILABLE) → 503 BUSINESS_TARGET_DB_UNAVAILABLE、FAILED(MISMATCH) → 409 BUSINESS_TARGET_DB_MISMATCH"
    violation: "409 BUSINESS_REFERENCE_NOT_FOUND・503・409 MISMATCH"
    source: [QD, FR3.1, AC3.1.2, AC3.1.3, AC3.1.8, X9]
  - id: BR4.5
    statement: "⑩ で対象DB の失敗が起きたら、その場で止め、何も保存せず、書き込みの接続を借りない"
    category: policy
    applies_to: "⑩"
    trigger: "接続を借りられない、または更新前の行の読み取り・countState・check が失敗したとき"
    logic: "以後の読み取りをせずに読み取りの接続を返し（時間切れ・断絶では U1 が壊れた印を立てる）、BR1.4 で写す。応答は「保存されていない」503（MISMATCH は 409）。WARN を1行だけ出す（BR7.1）"
    violation: "503 BUSINESS_TARGET_DB_TIMEOUT・BUSINESS_TARGET_DB_UNAVAILABLE、409 BUSINESS_TARGET_DB_MISMATCH"
    source: [AC3.1.8, FR3.4, C3, C5]
  - id: BR4.6
    statement: "check には、検証を通った型つきの値から作った ReferenceValue を渡す"
    category: constraint
    applies_to: "⑩ の確かめ"
    trigger: "check を呼ぶとき"
    logic: "本文の文字ではなく ⑨ を通った型つきの値を、文字の形（既存の書き込みの値の形）にして ReferenceValue にする。valueColumn の型として読めなければ U1 の check が問い合わせずに NOT_FOUND_OR_DENIED を返す"
    violation: "409 REFERENCE_NOT_FOUND"
    source: [C1, AC3.1.2, QD]
  - id: BR4.7
    statement: "同じ参照先を指す必須の参照項目が複数あるとき、countState は要求1件の中で参照先ごとに1回だけ呼び、結果を使い回す"
    category: constraint
    applies_to: "作成の必須の参照項目の状態（BR3.2）"
    trigger: "⑩ で必須の参照項目の状態を決めるとき"
    logic: "参照先（スキーマ・テーブル・valueColumn）が同じ必須の参照項目は、同じ countState の結果を使う（U1 の「要求の中で同じ参照先は1回」と同じ考え方）。check は値ごとに結果が違うため、値の違う check を1つの問い合わせに束ねるかは NFR 設計で決める（functional-spec.md の 9 節）"
    violation: "—"
    source: [NFR3, AC1.5.3, AC3.2.7]

  # ── BR5 監査 ──
  - id: BR5.1
    statement: "参照の確かめの拒否は、既存の出来事に結果 FAILURE・理由 BUSINESS_REFERENCE_NOT_FOUND で残す"
    category: policy
    applies_to: "⑩ の 409 BUSINESS_REFERENCE_NOT_FOUND"
    trigger: "⑩ が REFERENCE_NOT_FOUND で終わったとき"
    logic: "読み取りの接続を返した後に BusinessDataChangedEvent（BUSINESS_ROW_CREATED・BUSINESS_ROW_UPDATED、操作した人・作業ロール・スキーマ・テーブル・操作）を出す。既存の聞き手が別の短いトランザクションで追記する。項目の誤りの理由（REFERENCE_NOT_FOUND・NOT_EDITABLE_NOT_NULL）によらず理由は1つ。新しい出来事の種類・理由を足さない"
    violation: "—"
    source: [FR3.2a, C4, AC3.3.1]
  - id: BR5.2
    statement: "監査の行に、参照先の行の値・ラベル・本文の参照の値を含めない"
    category: constraint
    applies_to: "⑩ の拒否の監査"
    trigger: "出来事を出すとき"
    logic: "detail は既存の形のまま（作成は空、更新は参照元の行の主キーだけ）。参照先の値・ラベル・参照先の主キー・本文の参照の値・項目の誤りの中身を入れない"
    violation: "—"
    source: [FR3.2a, C4, AC3.3.2, NFR1]
  - id: BR5.3
    statement: "確かめのための読み取りそのものは監査に残さず、⑩ のほかの結果は既存の決まりで残す"
    category: policy
    applies_to: "⑩"
    trigger: "⑩ が終わったとき"
    logic: "更新前の行・件数・check の読み取りは残さない。⑩ の結果のうち、行が無い・2行以上・TIMEOUT・UNAVAILABLE・MISMATCH は既存の BusinessDataAuditReason.of の写しで残し（既存の書き込みの失敗と同じ）、400 VALIDATION_FAILED は残さない"
    violation: "—"
    source: [FR3.4, C4, AC3.3.3]

  # ── BR6 最後の守りと既存の振る舞い ──
  - id: BR6.1
    statement: "確かめと保存の間に参照先が消えたときは、既存の外部キー違反の変換を最後の守りにする"
    category: constraint
    applies_to: "⑪ 書き込み"
    trigger: "書き込みの文が外部キー違反で失敗したとき"
    logic: "既存の変換（セーブポイントへ戻して違反のカラムを確かめ、409 BUSINESS_REFERENCE_NOT_FOUND・reason REFERENCE_NOT_FOUND、監査の理由 BUSINESS_REFERENCE_NOT_FOUND）をそのまま使う。外部キーを持たない対象DB のカラムでは、すき間で消えた参照先の値が保存されうる（既知の制約）"
    violation: "409 BUSINESS_REFERENCE_NOT_FOUND"
    source: [FR3.3, ADR-003, AC3.1.6]
  - id: BR6.2
    statement: "参照の無い作成・更新と削除、一意の事前の問い合わせ・違反の変換は、今の振る舞いを変えない"
    category: constraint
    applies_to: "作成・更新・削除"
    trigger: "常に"
    logic: "⑩ で借りる接続が無い要求は、接続の数・判定の順・応答・監査が今と同じ。⑪ の中身（一意の事前の問い合わせ、書き込み、件数による行が無い・2行以上、違反の変換、確定の失敗の写し）は変えない。結合テストで既存の振る舞いを確かめる"
    violation: "—"
    source: [FR3.3, NFR7]

  # ── BR7 漏えいと応答の形 ──
  - id: BR7.1
    statement: "参照の値と行の値を、ログ・トレースの属性・誤りの応答に出さず、JDBC の例外のメッセージを出さない"
    category: constraint
    applies_to: "⑩ のすべての経路"
    trigger: "常に"
    logic: "値を持つ型（ReferenceCheckItem・PreviousReferenceValues・U1 の ReferenceValue・既存の TypedValue）は文字列にするとき値を伏せ、Map のまま層をまたがない。⑩ の対象DB の失敗は、変換する1か所で WARN を1行（目的・段・理由・SQLState・業者の番号・例外のクラスの名前）だけ出す。誤りの応答の項目の誤りはカラムの名前と理由だけ"
    violation: "—"
    source: [NFR1, AC3.1.8, AC3.3.2]
  - id: BR7.2
    statement: "参照先に無い値と、読めない参照先の値の拒否は、応答で区別できない同じ応答にする"
    category: authorization
    applies_to: "⑩ の 409"
    trigger: "確かめで拒否したとき"
    logic: "状態コード 409・code BUSINESS_REFERENCE_NOT_FOUND・項目の誤りの構造（field・reason REFERENCE_NOT_FOUND）・メッセージを同一にする。拒否までにかかる問い合わせの有無（NO_PERMISSION は問い合わせない）は応答に出さない"
    violation: "409 BUSINESS_REFERENCE_NOT_FOUND"
    source: [FR3.1, X9, AC3.1.3, AC3.1.4, ADR-005]
```

## 要約の表

| 群 | 決まり | 要点 |
|---|---|---|
| 判定の順と応答 | BR1.1〜BR1.4 | 既存の①〜⑨ の後、書き込みの前に ⑩ 保存前の参照の段。読むものが無ければ接続を借りない。⑩ の中は 失敗 → 行が無い → 400 → 409 の順。新しい code は作らない |
| 確かめる参照の選び方 | BR2.1〜BR2.7 | 参照先は DSL から引く。作成は NULL でない参照をすべて、更新は値が変わった参照だけ。比べ方は型つきの値で型ごと（文字は文字どおり）。更新前の行は同じ接続で読み、無ければ先に 404。拒否はすべて項目ごとに並べる |
| 作成の必須の参照項目 | BR3.1〜BR3.7 | NOT NULL の参照で、状態は U1 の `countState`。読めない・201 件以上なら、値ありは `REFERENCE_NOT_FOUND`、空は `NOT_EDITABLE_NOT_NULL`（どちらも 409）。使えるなら既存の検証の結果。`""` は ⑨ の結果で空か値かが1つに決まる。⑨ の 400 では対象DB を読まない。更新には当てない |
| 確かめの口と接続 | BR4.1〜BR4.7 | 読み取りの接続1本で順に、返してから書き込みの接続。写しは1つ。「ある」は U1 の `check` の FOUND（型つきの完全一致）。結果の写し。失敗ならその場で止める。同じ参照先の `countState` は1回 |
| 監査 | BR5.1〜BR5.3 | 既存の出来事に `BUSINESS_REFERENCE_NOT_FOUND`。参照の値を入れない。確かめの読み取りは残さない |
| 最後の守り・既存 | BR6.1〜BR6.2 | 外部キー違反の変換が最後の守り（外部キーの無いカラムは既知の制約）。参照の無い要求は今のまま |
| 漏えいと応答 | BR7.1〜BR7.2 | 値を伏せる、JDBC のメッセージを出さない、無い値と読めない値は同じ応答 |
