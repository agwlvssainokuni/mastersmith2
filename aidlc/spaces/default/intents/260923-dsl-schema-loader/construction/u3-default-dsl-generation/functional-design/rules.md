# Rules — U3 既定の DSL の生成（u3-default-dsl-generation）

```yaml
rules:
  - id: BR1.1
    statement: 対象DB を読めないときは、生成せずにその結果を返す
    category: policy
    applies_to: 生成
    trigger: 生成の要求
    logic: "IF U1 の読み取りが UNCONFIGURED THEN TARGET_UNCONFIGURED。IF UNAVAILABLE THEN TARGET_UNAVAILABLE（理由をそのまま）"
    violation: —
    source: FR2.4
  - id: BR1.2
    statement: 表示名は ja・en の両方に物理名、コメントがあれば ja はコメント
    category: calculation
    applies_to: テーブル・カラムの表示名
    trigger: 生成
    logic: "IF コメントがある THEN ja はコメント、en は物理名。ELSE ja・en とも物理名"
    violation: —
    source: FR3.3
  - id: BR1.3
    statement: メニューは1階層で、テーブル（ビューを含む）ごとに1項目を、物理名の順に並べる
    category: calculation
    applies_to: メニュー
    trigger: 生成
    logic: "テーブルごとに、表示名をテーブルの表示名と同じにした項目を1つ作る。並びは、物理名を大文字・小文字を区別せずに Unicode の符号の順で比べ、同じになるものは元の名前の符号の順"
    violation: —
    source: FR3.4
  - id: BR1.4
    statement: テーブルの並びもメニューと同じ順にする
    category: calculation
    applies_to: tables
    trigger: 生成
    logic: "tables の対応表は BR1.3 と同じ順に並べる"
    violation: —
    source: FR3.4
  - id: BR1.5
    statement: ビューは読み取り専用として記録し、主キーと外部キーを持たない
    category: calculation
    applies_to: ビュー
    trigger: 生成
    logic: "IF 写しのテーブルがビュー THEN view: true、primaryKey・foreignKeys は空"
    violation: —
    source: FR1.4、FR3.2
  - id: BR2.1
    statement: DB 上の型は、DB が返した名前・長さ・精度・桁・NULL を許すかをそのまま DSL に入れる
    category: calculation
    applies_to: dbType
    trigger: 生成
    logic: "写しの型の情報を変換せずに dbType に入れる（分類は入れない）"
    violation: —
    source: FR3.5（ADR-005）
  - id: BR2.2
    statement: 型の名前を分類に対応させる
    category: calculation
    applies_to: カラム
    trigger: 生成
    logic: "型の名前（大文字・小文字を区別しない）と長さから TypeCategory を決める。文字列は長さ 255 以下で SHORT_TEXT、256 以上または text・mediumtext・longtext・clob で LONG_TEXT。整数・小数は NUMBER。boolean・bool と、MySQL・MariaDB の tinyint(1)・bit(1) は BOOLEAN。date は DATE。timestamp・datetime（時差つきを含む）は DATETIME。time は TIME。それ以外・分からないものは UNSUPPORTED"
    violation: —
    source: FR3.5
  - id: BR2.3
    statement: 分類からフォーム部品を決める
    category: calculation
    applies_to: formPart
    trigger: 生成
    logic: "SHORT_TEXT・TIME・UNSUPPORTED は text、LONG_TEXT は textarea、NUMBER は number、BOOLEAN は checkbox、DATE は date、DATETIME は datetime。ただし BR3.2 の外部キーのカラムは select"
    violation: —
    source: FR3.5
  - id: BR2.4
    statement: 分類から検索の初期値を決める
    category: calculation
    applies_to: search
    trigger: 生成
    logic: "UNSUPPORTED は enabled: false。ほかは enabled: true で、演算子は SHORT_TEXT・LONG_TEXT が CONTAINS、NUMBER・DATE・DATETIME が RANGE、BOOLEAN が CHOICE、TIME が EQUALS。主キーのカラムは EQUALS。外部キーの select は CHOICE。初期値は無し、畳まない"
    violation: —
    source: FR3.5
  - id: BR2.5
    statement: 分類から一覧・詳細の初期値を決める
    category: calculation
    applies_to: list・detail
    trigger: 生成
    logic: "LONG_TEXT・UNSUPPORTED は一覧に表示しない・並べ替え不可。UNSUPPORTED は詳細にも出さない。ほかは一覧に表示・並べ替え可で、表示するカラムに定義の順で 1 から並び順を付ける。書式は NUMBER が NUMBER_GROUPED、BOOLEAN が BOOLEAN_YES_NO、DATE が DATE、DATETIME が DATETIME、TIME が TIME。列幅は無し。既定の並べ替えは主キーの最初のカラムの ASC（主キーが無ければ無し）"
    violation: —
    source: FR3.5
  - id: BR3.1
    statement: 主キーを DSL に写す
    category: calculation
    applies_to: primaryKey
    trigger: 生成
    logic: "写しの主キーのカラム名を、主キーの中の順で primaryKey に入れる"
    violation: —
    source: FR3.2
  - id: BR3.2
    statement: 1つのカラムだけの外部キーは、select と参照の選択肢にする
    category: calculation
    applies_to: 外部キーのカラム
    trigger: 生成
    logic: "前提: U1 の BR2.6 により、写しの外部キーは参照先が同じスキーマのものだけ（別のスキーマを参照する外部キーは写しに無い）。IF 外部キーのカラムが1つ THEN そのカラムの formPart を select にし、options を REFERENCE（table は参照先、valueColumn と labelColumn は参照先のカラム）にする。複数のカラムの外部キーは foreignKeys に写すだけで、部品は変えない"
    violation: —
    source: FR3.5
  - id: BR4.1
    statement: DB から導けるバリデーションだけを作り、origin を DB にする
    category: calculation
    applies_to: validations
    trigger: 生成
    logic: "IF NOT NULL で既定値が無い THEN required。IF 文字列で長さがある THEN maxLength（長さ）。数値の範囲・一意は作らない。すべて origin: DB"
    violation: —
    source: FR3.5
  - id: BR5.1
    statement: 同じスキーマの写しからは、いつも同じ本文を作る
    category: constraint
    applies_to: YAML の書き出し
    trigger: 生成
    logic: "項目の並びを U2 の書式の例の順に固定し、日時など実行のたびに変わる値を入れない。先頭に生成したことを示すコメントを1行入れる"
    violation: —
    source: FR3.6、FR5.1（生成し直したときに、プレビューの違いに見かけの差が出ないため）
  - id: BR5.2
    statement: 生成した DSL は U2 で検証し、通らなければ想定外の失敗とする
    category: constraint
    applies_to: 生成の結果
    trigger: 生成の後
    logic: "IF U2 の検証が INVALID THEN 作りの誤りとして想定外の失敗（例外）にする。VALID なら GENERATED と本文・識別を返す"
    violation: 想定外の失敗
    source: FR3.6
  - id: BR5.3
    statement: 生成した DSL に接続先の値を入れない
    category: constraint
    applies_to: 生成の結果
    trigger: 生成
    logic: "接続先・ユーザー名・パスワード・スキーマ名以外の設定の値を、DSL に入れない"
    violation: —
    source: NFR4
```

## 決まりの一覧

| ID | 決まり | 分類 | 出典 |
|---|---|---|---|
| BR1.1 | 読めないときは生成せず結果を返す | 方針 | FR2.4 |
| BR1.2 | 表示名は物理名、コメントがあれば ja はコメント | 計算 | FR3.3 |
| BR1.3 | メニューは1階層、物理名の順 | 計算 | FR3.4 |
| BR1.4 | テーブルもメニューと同じ順 | 計算 | FR3.4 |
| BR1.5 | ビューは読み取り専用、キーを持たない | 計算 | FR1.4、FR3.2 |
| BR2.1 | DB 上の型をそのまま入れる | 計算 | FR3.5 |
| BR2.2 | 型の名前を分類に対応させる | 計算 | FR3.5 |
| BR2.3 | 分類からフォーム部品 | 計算 | FR3.5 |
| BR2.4 | 分類から検索の初期値 | 計算 | FR3.5 |
| BR2.5 | 分類から一覧・詳細の初期値 | 計算 | FR3.5 |
| BR3.1 | 主キーを写す | 計算 | FR3.2 |
| BR3.2 | 1カラムの外部キーは select と参照 | 計算 | FR3.5 |
| BR4.1 | DB から導けるバリデーションだけ | 計算 | FR3.5 |
| BR5.1 | 同じ写しからは同じ本文 | 制約 | FR3.6、FR5.1 |
| BR5.2 | 生成した DSL を検証する | 制約 | FR3.6 |
| BR5.3 | 接続先を入れない | 制約 | NFR4 |
