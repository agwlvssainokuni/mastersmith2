# 業務の決まり — U2 dsl-v2

出典の表記: FR・NFR・C は `requirements.md`、AC・US は `stories.md`、ADR は `decisions.md`、C3 は `contract-summary.md`、Q1〜Q7 は `functional-design-questions.md` の答え（すべて A）、「細部」は同じファイルの「設計で決める細部」（まとめの確認で承認）、PM は `project.md`、TP は `team.md`。

```yaml
rules:
  # BR1 書式の版 2 の形（投入・復元・プレビューの読み直しの検証）
  - id: BR1.1
    statement: 書式の版は整数の 2 だけを受け付ける。版 1 は読み替えない。
    category: validation
    applies_to: DslDocumentV2.version
    trigger: DSL の読み込み（投入・復元・プレビューの表示と適用の読み直し・起動時の読み直し）
    logic: IF version が無い、または整数の 2 でない THEN 種類 UNSUPPORTED_VERSION の誤りを1件返し、構文と意味の検証は行わない
    violation: 投入・復元は既存の DSL_INVALID（422、誤りの一覧つき）で拒否し、プレビューと適用中は変わらない。文言は「書式の版 1 は使えません。書式の版 2（スキーマの階層あり）で書いてください。既定の DSL を生成し直すと版 2 で得られます。」（版 1 のとき。BR7.1）
    source: [FR2.3, AC6.1.2, AC6.1.7, ADR-005, C3]
  - id: BR1.2
    statement: 根は version・menus・schemas だけを持ち、schemas はスキーマ名をキーにした対応表で、各スキーマは label（ja・en）と tables を持つ。tables の形は版 1 のまま。
    category: validation
    applies_to: DslDocumentV2・DslSchemaV2
    trigger: DSL の構文の検証（JSON Schema dsl-schema-v2.json）
    logic: IF 根・スキーマ・テーブル・カラムに書式に無い項目がある、または必須の項目が無い THEN 構文の誤り（SYNTAX）
    violation: DSL_INVALID（422）で拒否
    source: [Q1, AC6.1.1, C3]
  - id: BR1.3
    statement: スキーマはちょうど1つだけを受け付ける。名前は問わない。
    category: constraint
    applies_to: DslDocumentV2.schemas
    trigger: DSL の意味の検証
    logic: IF schemas の数が 0、または 2 以上 THEN 意味の誤り（SEMANTIC）を1件、場所 schemas で返す
    violation: DSL_INVALID（422）で拒否
    source: [Q2, AC6.1.4, ADR-005]
  - id: BR1.4
    statement: スキーマ名と、同じスキーマの中のテーブル名の重なりは誤りにし、後の値で黙って上書きしない。
    category: validation
    applies_to: DslDocumentV2.schemas・DslSchemaV2.tables
    trigger: YAML の読み込み
    logic: IF 1つの対応表の中で同じキーが重なる THEN 種類 DUPLICATE_KEY の誤り（既存の読み込みの段）
    violation: DSL_INVALID（422）で拒否
    source: [AC6.1.5, TP（重複キー）]
  - id: BR1.5
    statement: 外部キーの参照先（referencedTable）と選択肢の出どころ（options.table）は、同じスキーマの中のテーブル名として照らす。
    category: validation
    applies_to: DslTable.foreignKeys・DslColumn.options
    trigger: DSL の意味の検証
    logic: IF 参照先のテーブル名が、そのテーブルと同じスキーマの tables に無い THEN 既存の意味の誤り（無いテーブル）。カラムの照らし合わせも既存のとおり同じスキーマの中で行う
    violation: DSL_INVALID（422）で拒否
    source: [Q1]
  - id: BR1.6
    statement: メニューの項目はテーブルを {schema, name} の組で指し、指す先のスキーマとテーブルが DSL にあること。
    category: validation
    applies_to: DslMenuItemV2.table（TableRef）
    trigger: DSL の構文と意味の検証
    logic: IF table が対象の組の形でない THEN 構文の誤り。IF schema が schemas に無い、または name がそのスキーマの tables に無い THEN 既存の意味の誤り（メニューの無いテーブル）、場所は menus の道の table
    violation: DSL_INVALID（422）で拒否
    source: [C3, Q1, AC6.1.1]
  - id: BR1.7
    statement: 版 2 の書式を dsl/dsl-schema-v2.json として同梱し、同じ形の道で公開する。版 1 の書式のファイルは置かない。書式の外部の参照（$ref の URL）は取りに行かない。
    category: policy
    applies_to: JSON Schema の同梱と公開
    trigger: 構文の検証・JSON Schema の公開
    logic: IF 構文の検証を行う THEN 同梱の版 2 の書式で行い、外部の $ref を解決しない
    violation: 該当なし（作りの決まり）。外部の取得が起きればテストで落ちる
    source: [細部, AC6.1.10, TP（$ref）]

  # BR2 メニューの深さ
  - id: BR2.1
    statement: メニューの深さの上限は 5 段。menus の直下の項目を 1 段目とし、まとまりかテーブルを指す項目かによらず項目1つを1段と数える。
    category: constraint
    applies_to: DslMenuItemV2
    trigger: DSL の意味の検証（投入・復元・プレビューの読み直し）
    logic: IF 項目の深さが 6 以上 THEN 上限を超えた最初の段（6 段目）の項目ごとに意味の誤りを1件返し、その下の子孫には重ねて出さない。誤りは位置（行・列・menus の道）と上限の値を持つ
    violation: DSL_INVALID（422）で拒否。文言は「メニューの深さが上限（{0} 段）を超えています。」（en「The menu depth exceeds the limit ({0} levels).」）
    source: [FR9.4, AC5.2.1, AC5.2.2, Q3, 細部]
  - id: BR2.2
    statement: 深さの上限は、新しい投入・履歴からの復元・プレビューの表示と適用の前の読み直しで当てる。
    category: policy
    applies_to: DSL の読み込みの入口
    trigger: 投入・復元・プレビューの表示・適用
    logic: IF 入口が起動時の読み直しでない THEN BR2.1 を含むすべての検証を当てる
    violation: DSL_INVALID（422）。今の適用中の DSL とプレビューは変わらず、500 にしない
    source: [AC5.2.4, ADR-005, Q4]
  - id: BR2.3
    statement: 起動時の読み直しでは深さだけを検証から外し、深すぎる枝を落としたモデルを適用中にして、警告のログを1件出す。
    category: policy
    applies_to: 起動時の読み直し（DslStartupLoader）
    trigger: アプリの起動（要求を受ける前）
    logic: IF 適用中の DSL が深さ以外のすべての検証を通る THEN 深さが 5 を超える項目をその子ごと落とし、落とした結果テーブルも子も持たないまとまりを上へさかのぼって落としてモデルを作り、Present にする。落とした項目が1つ以上なら WARN を1件（識別の先頭12文字・落とした項目の数・上限の値だけ。本文・接続情報は出さない）
    violation: 起動は止めない。深さ以外の誤りがあれば BR3.1
    source: [AC5.2.3, ADR-005, M3, 細部]

  # BR3 版を上げた後に残る DSL の扱い
  - id: BR3.1
    statement: 起動時に適用中の DSL が版 1（または深さ以外の今の検証を通らない）なら、適用中の DSL は無い扱いにする。
    category: policy
    applies_to: 起動時の読み直し
    trigger: アプリの起動
    logic: IF 適用中の本文が BR2.3 の読み方でも通らない THEN 提供口を Absent にし、既存の ERROR を1件（識別の先頭12文字と誤りの種類だけ）出して起動を続ける
    violation: 起動は止めない。内部DB を読めないとき（想定外）は今までどおり起動を止める
    source: [AC6.1.6, ADR-005, C3]
  - id: BR3.2
    statement: 履歴から版 1 の DSL や深すぎる DSL を復元しようとしたら拒否する。
    category: validation
    applies_to: 履歴の復元
    trigger: 復元の API
    logic: IF 履歴の本文が今の検証（BR1・BR2.1）を通らない THEN 拒否する
    violation: 既存の DSL_INVALID（422、誤りの一覧つき）。プレビューと適用中は変わらず、500 にしない
    source: [AC5.2.4, AC6.1.7]
  - id: BR3.3
    statement: プレビューの表示の前に、保存した本文を今の検証で読み直し、通らなければ拒否してプレビューは残す。
    category: validation
    applies_to: プレビューの表示
    trigger: プレビューを読む API（キャッシュにモデルが無いとき）
    logic: IF 保存したプレビューの本文が今の検証を通らない THEN DSL_INVALID（422、誤りの一覧つき）を返し、プレビューの行は消さない
    violation: 422。500 にしない。破棄とプレビューのダウンロードは今までどおりできる
    source: [Q4]
  - id: BR3.4
    statement: 適用は、プレビューの本文を履歴へ移す前に今の検証で確かめ、通らなければ拒否する。
    category: validation
    applies_to: 適用
    trigger: 適用の API
    logic: IF 見たプレビューのモデルがキャッシュに無く、保存した本文が今の検証を通らない THEN 内部DB を変えずに DSL_INVALID（422、誤りの一覧つき）で拒否する
    violation: 422。適用中とプレビューは変わらない。500 にしない
    source: [Q4]
  - id: BR3.5
    statement: 適用中の DSL とプレビューのダウンロードは、検証せずに保存した本文をそのまま返す。
    category: policy
    applies_to: ダウンロード（適用中・プレビュー）
    trigger: ダウンロードの API
    logic: IF 本文がある THEN 版 1 や深すぎる本文でもそのまま返す（今の作りのまま）
    violation: 本文が無いときは既存の 404（DSL_APPLIED_NOT_FOUND・DSL_PREVIEW_NOT_FOUND）
    source: [AC5.2.5, AC6.1.7, Q5]
  - id: BR3.6
    statement: 今の状態の応答に、適用中の DSL を今の書式で読めず使われていないことを示す印 appliedUnreadable を足す。
    category: calculation
    applies_to: DslStatusV2.appliedUnreadable
    trigger: 今の状態を返す API
    logic: IF 内部DB に適用中の版があり、提供口が Absent、または提供口の識別が適用中の識別と違う THEN true。ELSE false
    violation: 該当なし（計算）
    source: [Q5, AC6.1.6]

  # BR4 既定の DSL の生成
  - id: BR4.1
    statement: 既定の DSL は書式の版 2 で生成し、スキーマ名は対象DB の設定のスキーマ名（読み取った写しのスキーマ名）、表示名は ja・en ともスキーマ名にする。
    category: calculation
    applies_to: 既定の DSL の生成
    trigger: 生成の API
    logic: IF 対象DB の写しを読めた THEN version 2・schemas に設定のスキーマ1つ・その下に今の規則でテーブルを並べた木を作る
    violation: 対象DB の設定が無い・使えないときは既存の TARGET_DB_UNCONFIGURED・TARGET_DB_UNAVAILABLE
    source: [AC6.1.3, FR2.3, 細部, D1]
  - id: BR4.2
    statement: 生成するメニューは今と同じ平らな一覧（深さ 1）で、各項目は {schema, name} でテーブルを指す。
    category: calculation
    applies_to: 既定の DSL の生成の menus
    trigger: 生成
    logic: IF テーブルを並べる THEN テーブルごとにメニューの項目を1つ、table に {設定のスキーマ名, テーブル名} を入れる
    violation: 該当なし
    source: [AC6.1.3, C3]
  - id: BR4.3
    statement: 3 種類の DB とも、設定のスキーマ名（MySQL・MariaDB ではデータベース名と同じ値）は DSL に書くが、接続先・ポート・ユーザー名・パスワード・JDBC の URL と PostgreSQL の database の項目は、生成した DSL・プレビューの応答・ログに含めない。
    category: policy
    applies_to: 既定の DSL の生成・プレビューの応答・ログ
    trigger: 生成・プレビュー
    logic: IF 値が接続の項目（スキーマ名を除く）THEN DSL と応答に書かない
    violation: 既存の漏えいのテストで落ちる
    source: [Q7, PM（Forbidden）, AC6.1.3, D1]
  - id: BR4.4
    statement: 生成した DSL は、そのまま投入すると版 2 の検証を通る。
    category: constraint
    applies_to: 既定の DSL の生成
    trigger: 生成
    logic: IF 生成した本文を DSL の読み込みにかける THEN 誤りが無い（通らないのは想定外の失敗）
    violation: 想定外の失敗（500。既存の扱い）
    source: [AC6.1.3]

  # BR5 照合・違い・要約
  - id: BR5.1
    statement: 照合は、DSL のスキーマ名が対象DB の設定のスキーマ名と同じときだけテーブルとカラムを比べ、違うときは警告 SCHEMA_MISMATCH を1件出す。
    category: calculation
    applies_to: 照合（DslReconciler）
    trigger: プレビューの表示・投入・生成・復元
    logic: IF 対象DB を読めて、DSL のスキーマ名が写しのスキーマ名と同じ THEN 既存の規則でテーブル・カラム・型を比べ、警告の場所は schemas.<スキーマ>.tables.<テーブル>… の道。IF 名前が違う THEN SCHEMA_MISMATCH を1件（DSL のスキーマ名だけを埋める）でテーブルは比べない。対象DB の設定が無い・接続できないときは既存の警告1件
    violation: どれも失敗にしない（警告）
    source: [Q2, AC6.1.4, AC6.1.9]
  - id: BR5.2
    statement: 違いはスキーマの階層で求め、スキーマごとに区分（ADDED・REMOVED・CHANGED・UNCHANGED）を持ち、その下のテーブルの違いは既存の規則で求める。
    category: calculation
    applies_to: 違い（DslDiffCalculator）
    trigger: プレビューの表示
    logic: IF 適用中のモデルが無い THEN すべてのスキーマとテーブルを ADDED。ELSE スキーマを名前で突き合わせ、片方だけにあれば ADDED・REMOVED（その下のテーブルもすべて同じ区分）、両方にあれば表示名が違えば CHANGED、同じなら UNCHANGED とし、テーブルは同じスキーマの中で名前で突き合わせる
    violation: 該当なし
    source: [Q6, AC6.1.9]
  - id: BR5.3
    statement: 要約にスキーマの数を足し、表示名の未設定にスキーマの表示名も数える。メニューの木の節はテーブルを {schema, name} で持つ。
    category: calculation
    applies_to: 要約（DslSummaryCalculator）
    trigger: プレビューの表示
    logic: IF 要約を求める THEN schemaCount を数え、表示名の未設定はスキーマ・メニュー・テーブル・カラム・固定の選択肢の順に数え、場所は schemas.<スキーマ>… の道で示す
    violation: 該当なし
    source: [Q6, AC6.1.9]

  # BR6 上限つきの安全な YAML の読み込みの口（SafeYamlReader）
  - id: BR6.1
    statement: 上限（大きさ・入れ子の深さ・別名の数・展開後の節の数）は呼ぶ側が渡し、大きさは読む前に確かめる。
    category: validation
    applies_to: SafeYamlReader・SafeYamlLimits
    trigger: 口の呼び出し
    logic: IF 本文のバイト数が maxBytes を超える THEN 読まずに TOO_LARGE。上限ちょうどは受け付ける。IF 上限の値が範囲外（maxBytes・maxDepth・maxExpandedNodes が 1 未満、maxAliases が 0 未満）THEN 呼ぶ側の誤り（想定外の失敗）
    violation: Rejected(TOO_LARGE)
    source: [ADR-007, C3, AC3.1.7, FR7.4]
  - id: BR6.2
    statement: 深さ・別名・展開後の節の数の上限、タグ、重複キー、YAML として読めない本文を、区分に写して拒否する。任意の型を作らず、外部の参照を取りに行かない。
    category: validation
    applies_to: SafeYamlReader
    trigger: 口の呼び出し
    logic: IF 深さの超過 THEN TOO_DEEP。IF 別名の数の超過・展開後の節の数の超過・自分を含む別名 THEN TOO_MANY_ALIASES。IF タグ THEN TAG_NOT_ALLOWED。IF 重複キー THEN DUPLICATE_KEY。IF 文字の誤り・YAML として読めない THEN SYNTAX。どれも最初の1件で止める
    violation: Rejected(区分, 位置)
    source: [ADR-007, C3, AC3.1.7, AC3.1.13, FR7.4, TP]
  - id: BR6.3
    statement: 口の結果に、部品の例外の文を含めない。拒否は区分と位置（行・列・場所）だけを持つ。
    category: policy
    applies_to: SafeYamlResult.Rejected
    trigger: 拒否
    logic: IF 部品の例外を受けた THEN 種類だけを区分に写し、文は捨てる
    violation: 例外の文が外へ出たらテストで落ちる
    source: [PM（Forbidden）, AC3.1.13, C3]
  - id: BR6.4
    statement: 口は JSON の木と位置を引く口を返し、DSL の JSON Schema の検証を含めない。位置を引く口は dsl.service の型にし、dsl.parse の型を外へ出さない。
    category: policy
    applies_to: SafeYamlResult.Parsed
    trigger: 読めたとき
    logic: IF 読めた THEN 木と、JSON Pointer から行・列を引く口を返す
    violation: 該当なし（境界テスト parseAndValidateStayInside で dsl.parse の型の漏れを落とす）
    source: [ADR-007, C3, AC3.1.12]
  - id: BR6.5
    statement: 口の結果を文字列にしたときは、区分・位置・節の数だけを出し、木の中身を出さない。
    category: policy
    applies_to: SafeYamlResult
    trigger: TraceAspect が戻り値を TRACE のログに出すとき
    logic: IF 結果を文字列にする THEN 木の中身を含めない
    violation: TRACE のログに中身が出たらテストで落ちる
    source: [TP（Code Style の TraceAspect）, 細部]
  - id: BR6.6
    statement: DSL の読み込みは、今の DslFormat の上限の値で同じ部品を使い、DSL の読み込みの結果（誤りの種類・文言・順）は今と変えない。
    category: policy
    applies_to: DefaultDslReader
    trigger: DSL の読み込み
    logic: IF DSL を読む THEN 上限の値を DslFormat から部品へ渡し、既存の段の順（大きさ→読み込み→版→構文→意味）を保つ
    violation: 既存の DSL のテスト（上限・タグ・重複キー・$ref）を版 2 の形に書き換えて通すことで確かめる
    source: [ADR-007, AC6.1.10]

  # BR7 画面（features/dsl、S10）
  - id: BR7.1
    statement: 版の誤りは既存の誤りの一覧に、版 2 で書く必要と既定の DSL の生成で得られることを示す文言で出す。
    category: policy
    applies_to: DSL の誤りの文言（サーバーの DslErrorMessages）と誤りの一覧の画面
    trigger: DSL_INVALID の応答に UNSUPPORTED_VERSION がある
    logic: IF 書かれた版が 1 THEN 「書式の版 1 は使えません。書式の版 2（スキーマの階層あり）で書いてください。既定の DSL を生成し直すと版 2 で得られます。」。IF 版が無い・ほかの値 THEN 対応する版が 2 であることと、既定の DSL の生成で得られることを示す
    violation: 該当なし
    source: [AC6.1.2, mockups.md 10節]
  - id: BR7.2
    statement: 今の状態の区画で appliedUnreadable が真なら、適用中の DSL が使われていないことと、既定の DSL を生成し直して適用する案内を注意として出す。
    category: policy
    applies_to: DslStatusPanel
    trigger: 今の状態の表示
    logic: IF appliedUnreadable THEN Alert（warning）で注意と案内を出し、「スキーマを読み込む」の操作をそのまま使える
    violation: 該当なし
    source: [Q5, AC6.1.6]
  - id: BR7.3
    statement: プレビューを読む応答が DSL_INVALID なら、プレビューの区画に誤りの一覧と、破棄して投入し直す案内を出し、破棄とダウンロードの操作を残す。適用は出さない。
    category: policy
    applies_to: DslPreviewPanel
    trigger: プレビューを読む応答・適用の応答が DSL_INVALID
    logic: IF プレビューの読み直しが 422 THEN 状態を「読めないプレビュー」にし、誤りの一覧と案内を出す
    violation: 該当なし
    source: [Q4]
  - id: BR7.4
    statement: メニューの木のテーブルを指す項目は、括弧の中に「スキーマ名.テーブル名」を出す。
    category: policy
    applies_to: DslMenuTree
    trigger: プレビューの要約の表示
    logic: IF 節が table を持つ THEN 「（スキーマ名.テーブル名）」と出す
    violation: 該当なし
    source: [Q6, AC6.1.9]
  - id: BR7.5
    statement: 違いの表は、スキーマごとに見出しの行（スキーマ名・表示名・区分）を置き、その下にテーブルの行を並べる。開閉はテーブルの行だけ。要約にスキーマの数を出す。
    category: policy
    applies_to: DslDiffTable・DslPreviewPanel の要約
    trigger: プレビューの表示
    logic: IF 違いを出す THEN スキーマの見出しの行（th scope=rowgroup 相当）を置き、「すべて表示」が切られているときは UNCHANGED のスキーマも、変わったテーブルがあれば見出しを出す
    violation: 該当なし
    source: [Q6, AC6.1.9, mockups.md 10節]

  # BR8 提供口
  - id: BR8.1
    statement: 提供口 ActiveDslModelProvider と ActiveDsl の形は変えず、Present は版 2 のモデル（schemas・menus）を持つ。Absent は適用中が無い・今の書式で読めないとき。
    category: policy
    applies_to: ActiveDslModelProvider・DslModel
    trigger: U4 role・U5 navigation・dslmanage が適用中のモデルを読む
    logic: IF 適用・起動時の読み直しでモデルができた THEN Present に差し替える。IF できない THEN Absent
    violation: 該当なし
    source: [C3, ADR-004, AC6.1.1, AC6.1.6]
  - id: BR8.2
    statement: モデルはスキーマ名とテーブル名の組でテーブルを引ける。名前は大文字・小文字を区別する。
    category: calculation
    applies_to: DslModel
    trigger: 呼ぶ側がテーブルを探す
    logic: IF 組に当たるスキーマとテーブルがある THEN そのテーブル。ELSE 無い
    violation: 該当なし
    source: [C3, AC1.2.1, AC5.1.14]
```

## 要約

| ID | 種類 | 決まり（要旨） | 出典 |
|---|---|---|---|
| BR1.1 | validation | 版は整数の 2 だけ。版 1 は DSL_INVALID（422） | AC6.1.2・AC6.1.7 |
| BR1.2 | validation | 根は version・menus・schemas。schemas はスキーマ名の対応表で label と tables を持つ | Q1・AC6.1.1 |
| BR1.3 | constraint | スキーマはちょうど1つ。名前は問わない | Q2・AC6.1.4 |
| BR1.4 | validation | スキーマ名・スキーマの中のテーブル名の重なりは重複キー | AC6.1.5 |
| BR1.5 | validation | 外部キー・選択肢の参照は同じスキーマの中のテーブル名 | Q1 |
| BR1.6 | validation | メニューは {schema, name} でテーブルを指し、指す先があること | C3・Q1 |
| BR1.7 | policy | dsl-schema-v2.json を同梱・公開し、v1 のファイルは置かない | 細部・AC6.1.10 |
| BR2.1 | constraint | メニューの深さは 5 段まで（直下を 1 段目） | FR9.4・Q3 |
| BR2.2 | policy | 投入・復元・プレビューの読み直しで深さを当てる | AC5.2.4・Q4 |
| BR2.3 | policy | 起動時は深さだけ外し、深すぎる枝を落として WARN 1件 | AC5.2.3 |
| BR3.1 | policy | 起動時に読めない適用中の DSL は Absent | AC6.1.6 |
| BR3.2 | validation | 版 1・深すぎる履歴の復元は 422 | AC5.2.4・AC6.1.7 |
| BR3.3 | validation | プレビューの表示の前に読み直し、通らなければ 422 でプレビューは残す | Q4 |
| BR3.4 | validation | 適用は履歴へ移す前に確かめる | Q4 |
| BR3.5 | policy | ダウンロードは検証せず本文をそのまま返す | AC5.2.5・AC6.1.7・Q5 |
| BR3.6 | calculation | 今の状態に appliedUnreadable を足す | Q5 |
| BR4.1 | calculation | 既定の DSL は版 2、設定のスキーマ名、表示名はスキーマ名 | AC6.1.3 |
| BR4.2 | calculation | 生成するメニューは平らで {schema, name} | AC6.1.3 |
| BR4.3 | policy | スキーマ名は書き、接続の項目は書かない | Q7・PM |
| BR4.4 | constraint | 生成した DSL は版 2 の検証を通る | AC6.1.3 |
| BR5.1 | calculation | 照合は名前が同じときだけ比べ、違えば SCHEMA_MISMATCH 1件 | Q2・AC6.1.9 |
| BR5.2 | calculation | 違いはスキーマの階層で求める | Q6 |
| BR5.3 | calculation | 要約にスキーマの数、未設定にスキーマの表示名 | Q6 |
| BR6.1 | validation | 上限は呼ぶ側が渡し、大きさは読む前に確かめる | ADR-007・AC3.1.7 |
| BR6.2 | validation | 深さ・別名・展開後の節・タグ・重複キー・構文を区分に写す | ADR-007・AC3.1.13 |
| BR6.3 | policy | 例外の文を出さない | PM・AC3.1.13 |
| BR6.4 | policy | 木と位置を引く口を返し、JSON Schema の検証を含めない | ADR-007・C3 |
| BR6.5 | policy | 文字列にしても木の中身を出さない | TP（TraceAspect） |
| BR6.6 | policy | DSL の読み込みは今の上限の値と段の順のまま | AC6.1.10 |
| BR7.1 | policy | 版の誤りの文言 | AC6.1.2 |
| BR7.2 | policy | 今の状態に読めない適用中の注意と案内 | Q5 |
| BR7.3 | policy | 読めないプレビューの誤りの一覧と破棄の案内 | Q4 |
| BR7.4 | policy | メニューの木は「スキーマ名.テーブル名」 | Q6 |
| BR7.5 | policy | 違いの表にスキーマの見出しの行、要約にスキーマの数 | Q6 |
| BR8.1 | policy | 提供口の形は変えず Present は版 2 のモデル | C3 |
| BR8.2 | calculation | 組でテーブルを引ける | C3 |
