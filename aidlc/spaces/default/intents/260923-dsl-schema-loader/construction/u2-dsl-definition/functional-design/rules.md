# Rules — U2 DSL の定義（u2-dsl-definition）

```yaml
rules:
  - id: BR1.1
    statement: 大きさが 5MB を超える YAML は、読む前に止める
    category: validation
    applies_to: 読み込み
    trigger: 読み込み
    logic: "IF 本文（UTF-8）のバイト数が 5MB を超える THEN 読まずに SIZE_LIMIT の誤り（行・列なし）を1件返す。ちょうど 5MB は止めない（1MB を何バイトとするかは NFR 設計）"
    violation: INVALID（SIZE_LIMIT）
    source: FR4.4、NFR2
  - id: BR1.2
    statement: 入れ子の深さが上限を超える YAML は止める
    category: validation
    applies_to: 読み込み
    trigger: 読み込み
    logic: "入れ子の深さは、YAML の文書の根から数えた、対応表と並びの入れ子の段の数（DSL の意味ではなく YAML の形で数える）。メニューの階層の数とは別で、メニューを N 階層にすると YAML の深さは N より大きくなる。IF その深さが上限を超える THEN 読み込みを打ち切り DEPTH_LIMIT の誤りを返す。ちょうど上限は止めない（数値は NFR 設計で、想定するメニューの階層とテーブル・カラムの定義が収まる値にする）"
    violation: INVALID（DEPTH_LIMIT）
    source: FR4.4、NFR3
  - id: BR1.3
    statement: 別名（アンカー）の数が上限を超える YAML は止め、展開の爆発で処理を止めない
    category: validation
    applies_to: 読み込み
    trigger: 読み込み
    logic: "IF 別名の数が上限を超える THEN 展開する前に打ち切り ALIAS_LIMIT の誤りを返す。ちょうど上限は止めない（数値は NFR 設計）"
    violation: INVALID（ALIAS_LIMIT）
    source: FR4.4、NFR3
  - id: BR1.4
    statement: 任意の型を作るタグを拒否する
    category: validation
    applies_to: 読み込み
    trigger: 読み込み
    logic: "IF 標準の型（文字列・数・真偽値・null・並び・対応表）以外を作るタグがある THEN 型を作らずに FORBIDDEN_TAG の誤りを返す"
    violation: INVALID（FORBIDDEN_TAG）
    source: FR4.4、NFR3
  - id: BR1.5
    statement: 同じキーが重なる対応表は誤りにする
    category: validation
    applies_to: 読み込み
    trigger: 読み込み
    logic: "IF 1つの対応表の中に同じキーが2回以上現れる THEN 後の値で上書きせず DUPLICATE_KEY の誤りを返す（テーブル・カラムの二重の定義もこれで止まる）"
    violation: INVALID（DUPLICATE_KEY）
    source: FR4.2、FR4.4
  - id: BR1.6
    statement: JSON Schema はアプリに同梱したものだけを使い、外部の URL を取りに行かない
    category: constraint
    applies_to: 構文の検証
    trigger: 検証の部品の用意
    logic: "IF JSON Schema を読む THEN 同梱したものだけを読み、外部の参照の取得を無効にする。DSL の中の $ref は知らない項目として構文の誤りにする"
    violation: 外部を指す参照を含むスキーマは取りに行かずに失敗させる
    source: FR4.4、NFR3
  - id: BR2.1
    statement: 書式の版がアプリの対応する版でなければ止める
    category: validation
    applies_to: 検証
    trigger: 読み込みの上限を通った後
    logic: "IF version が無い、または対応する版（1）でない THEN UNSUPPORTED_VERSION の誤りを返し、構文と意味の検証をしない"
    violation: INVALID（UNSUPPORTED_VERSION）
    source: FR1.5
  - id: BR2.2
    statement: 構文は同梱の JSON Schema で検証し、知らない項目を許さない
    category: validation
    applies_to: 検証
    trigger: 版を通った後
    logic: "IF JSON Schema に合わない（必須の欠け・型の違い・許されない値・知らない項目）THEN 見つかった構文の誤りをすべて返し、意味の検証をしない"
    violation: INVALID（SYNTAX）
    source: FR1.1、FR1.6、FR4.2
  - id: BR2.3
    statement: 検証の段は、読み込みの上限 → 版 → 構文 → 意味 の順で、前の段に誤りがあれば後の段を行わない
    category: policy
    applies_to: 検証
    trigger: 読み込み
    logic: "IF ある段で誤りが見つかる THEN その段の誤りだけを返して終える"
    violation: —
    source: FR4.2、FR4.3
  - id: BR3.1
    statement: メニューは DSL にあるテーブルだけを指す
    category: validation
    applies_to: 意味の検証
    trigger: 構文を通った後
    logic: "IF メニューの項目の table が tables に無い THEN その項目の場所の SEMANTIC の誤り"
    violation: INVALID（SEMANTIC）
    source: FR4.2
  - id: BR3.2
    statement: メニューの項目は、テーブルか子の少なくとも一方を持つ
    category: validation
    applies_to: 意味の検証
    trigger: 構文を通った後
    logic: "IF メニューの項目に table も items も無い THEN SEMANTIC の誤り"
    violation: INVALID（SEMANTIC）
    source: FR1.2
  - id: BR3.3
    statement: 主キー・外部キー・参照の先のテーブルが DSL にあり、指すカラムがそのテーブルにある
    category: validation
    applies_to: 意味の検証
    trigger: 構文を通った後
    logic: "IF primaryKey・foreignKeys・options（REFERENCE・LOOKUP）・lookupSearch・lookupList が指すテーブルが DSL に無い THEN その場所の SEMANTIC の誤り。IF テーブルはあるが、指すカラムがそのテーブル（参照の先なら参照の先のテーブル、主キーと外部キー自身のカラム（foreignKeys の columns）なら自分のテーブル）の columns に無い THEN その場所の SEMANTIC の誤り"
    violation: INVALID（SEMANTIC）
    source: FR4.2
  - id: BR3.4
    statement: 一覧の並び順はテーブルの中で重ならない
    category: validation
    applies_to: 意味の検証
    trigger: 構文を通った後
    logic: "IF 同じテーブルの中で list.order が重なる THEN 後の方の場所の SEMANTIC の誤り"
    violation: INVALID（SEMANTIC）
    source: FR1.3
  - id: BR3.5
    statement: フォーム部品と選択肢の出どころが合っている
    category: validation
    applies_to: 意味の検証
    trigger: 構文を通った後
    logic: "IF formPart が select・radio で options が FIXED・REFERENCE でない、または lookup で options が LOOKUP でない、またはそれ以外の部品で options がある THEN SEMANTIC の誤り"
    violation: INVALID（SEMANTIC）
    source: FR1.3
  - id: BR3.6
    statement: バリデーションの値が矛盾しない
    category: validation
    applies_to: 意味の検証
    trigger: 構文を通った後
    logic: "IF min > max、または minLength > maxLength、または pattern が正規表現として正しくない THEN SEMANTIC の誤り（pattern の検査は時間の上限つきで行う）"
    violation: INVALID（SEMANTIC）
    source: FR1.3
  - id: BR3.7
    statement: 意味の誤りは、見つかったものをすべて返す
    category: policy
    applies_to: 意味の検証
    trigger: 意味の検証
    logic: "IF 意味の誤りが複数ある THEN すべてを返す（件数の上限を設けない。絞るのは U4）"
    violation: —
    source: FR4.3
  - id: BR4.1
    statement: 誤りには、YAML の行・列と DSL の中の場所を付ける
    category: policy
    applies_to: 誤りの一覧
    trigger: 誤りを返すとき
    logic: "IF 誤りの場所の位置が位置の対応表にある THEN 行・列（1から）と場所を付ける。位置が得られない誤りは行・列を付けない。種類ごとの扱いは functional-spec.md の 7.1 の表のとおり"
    violation: —
    source: FR4.3
  - id: BR4.2
    statement: 別名を参照した値の誤りは、参照を書いた場所の行・列を示す
    category: policy
    applies_to: 誤りの一覧
    trigger: 誤りを返すとき
    logic: "IF 誤りの値が別名の参照（*）から来ている THEN 参照を書いた場所の行・列を示す"
    violation: —
    source: FR4.3
  - id: BR4.3
    statement: 誤りは文言の鍵と埋める値で持ち、部品の例外のメッセージを含めない
    category: constraint
    applies_to: 誤りの一覧
    trigger: 誤りを返すとき
    logic: "IF 部品が誤りを知らせる THEN その種類を文言の鍵に置き換え、DSL の中の名前・値だけを埋める値にする"
    violation: —
    source: NFR5
  - id: BR5.1
    statement: DSL の識別は、YAML の本文のバイト列から求める
    category: calculation
    applies_to: 識別
    trigger: 識別を求めるとき
    logic: "IF 本文のバイト列が同じ THEN 識別は同じ。1バイトでも違えば別（アルゴリズムは NFR 設計）"
    violation: —
    source: FR7.1、FR10.2（ADR-003）
  - id: BR5.2
    statement: 検証を通った DSL は、変更できない値の木に変える
    category: policy
    applies_to: モデル
    trigger: 検証を通ったとき
    logic: "IF 検証を通る THEN メニュー・テーブル・カラムの値の木を作り、識別を付けて返す。モデルは接続先・権限を持たない"
    violation: —
    source: FR6.4
  - id: BR5.3
    statement: 適用中のモデルの差し替えは、読み手から見て一度に切り替わる
    category: constraint
    applies_to: 適用中のモデルの保持
    trigger: 差し替え
    logic: "IF 差し替える THEN 読み手は、前のモデルか新しいモデルのどちらかだけを得る（途中の状態を得ない）。取り出した後のモデルは差し替えの影響を受けない"
    violation: —
    source: FR6.4、NFR8
  - id: BR5.4
    statement: 適用中の DSL が無いときは、提供口は「無い」を返す
    category: policy
    applies_to: 提供口
    trigger: 取り出し
    logic: "IF 保持しているモデルが無い THEN ABSENT を返す"
    violation: —
    source: FR6.4
```

## 決まりの一覧

| ID | 決まり | 分類 | 出典 |
|---|---|---|---|
| BR1.1 | 5MB を超えたら読む前に止める | 検証 | FR4.4、NFR2 |
| BR1.2 | 入れ子の深さの上限 | 検証 | FR4.4、NFR3 |
| BR1.3 | 別名の数の上限、展開の爆発を防ぐ | 検証 | FR4.4、NFR3 |
| BR1.4 | 任意の型を作るタグを拒否 | 検証 | FR4.4、NFR3 |
| BR1.5 | 重複キーは誤り | 検証 | FR4.2、FR4.4 |
| BR1.6 | JSON Schema は同梱だけ、外部を取りに行かない | 制約 | FR4.4、NFR3 |
| BR2.1 | 対応していない版は止める | 検証 | FR1.5 |
| BR2.2 | 構文は JSON Schema、知らない項目は誤り | 検証 | FR1.1、FR1.6、FR4.2 |
| BR2.3 | 上限 → 版 → 構文 → 意味、前の段で誤りなら止める | 方針 | FR4.2、FR4.3 |
| BR3.1 | メニューは DSL のテーブルだけを指す | 検証 | FR4.2 |
| BR3.2 | メニューの項目はテーブルか子を持つ | 検証 | FR1.2 |
| BR3.3 | 主キー・外部キー・参照の先が DSL にある | 検証 | FR4.2 |
| BR3.4 | 一覧の並び順が重ならない | 検証 | FR1.3 |
| BR3.5 | フォーム部品と選択肢の出どころが合う | 検証 | FR1.3 |
| BR3.6 | バリデーションの値が矛盾しない | 検証 | FR1.3 |
| BR3.7 | 意味の誤りはすべて返す | 方針 | FR4.3 |
| BR4.1 | 誤りに行・列と場所を付ける | 方針 | FR4.3 |
| BR4.2 | 別名の参照は参照の場所で示す | 方針 | FR4.3 |
| BR4.3 | 誤りは文言の鍵で、部品の例外の文言を含めない | 制約 | NFR5 |
| BR5.1 | 識別は本文のバイト列から | 計算 | FR7.1、FR10.2 |
| BR5.2 | 検証を通った DSL を変更できない値の木に | 方針 | FR6.4 |
| BR5.3 | 差し替えは一度に切り替わる | 制約 | FR6.4、NFR8 |
| BR5.4 | 適用中が無ければ「無い」 | 方針 | FR6.4 |
