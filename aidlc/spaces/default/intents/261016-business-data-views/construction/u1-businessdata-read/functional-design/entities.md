# エンティティ — U1 u1-businessdata-read

**内部DB に保存するエンティティは無い。** この単位は内部DB に新しい表を持たない（`aidlc/spaces/default/intents/261016-business-data-views/inception/domain-design/components.md` の Entity Ownership）。業務データの行は対象DB のデータで、アプリが独自に持つデータではないため、エンティティにしない（`project.md` の学び）。

ここに書くのは、要求1件の中だけで作って使い、保存しない値の型である。フレームワークの仕組み（接続のプール、トレース、ログの1件、アクセス制御の設定など）はエンティティにせず、`rules.md` の決まりとして書く。

出典: 契約 C1・C2（`inception/contract-design/contract-summary.md`）、ADR-005・ADR-006、この段の答え `functional-design-questions.md`（Q1〜Q8: A）と、承認の場の Request Changes（同じファイルの Requested Changes Feedback。R-01〜R-06・U2 R-02）。型の名前は既存のコードの書き方にそろえた案で、コード生成で変えるときは差を記録する。

```yaml
entities:
  - name: DefinitionId
    description: "テーブルの定義の識別。適用中の DSL の識別と作業ロールの組（契約 C2）。画面が古い定義を使っているかを見分ける"
    persisted: false
    attributes:
      - { name: dslHash, type: string, required: true, constraints: "16 進数の小文字 64 文字（DslModel の識別と同じ）" }
      - { name: workRoleId, type: integer, required: false, constraints: "作業ロールが無ければ null" }
    constraints: []
    relationships: []

  - name: TableAccessView
    description: "1要求の判定の材料（契約 C1 の accessFor の結果）。権限の写し1つと、その写しが持つ DSL から作る（BR2・BR3。Q2: A）"
    persisted: false
    attributes:
      - { name: schema, type: string, required: true, constraints: "写しの DSL にあるスキーマ名" }
      - { name: table, type: string, required: true, constraints: "写しの DSL にあるテーブル名" }
      - { name: definitionId, type: DefinitionId, required: true }
      - { name: view, type: boolean, required: true }
      - { name: primaryKey, type: "list<string>", required: true, constraints: "DSL の主キーのカラム名（無ければ空）" }
      - { name: tableMain, type: enum, required: true, allowed_values: [NONE, READ, FULL], constraints: "テーブルの実効の主権限" }
      - { name: canCreate, type: boolean, required: true, constraints: "補助権限 CREATE" }
      - { name: canDelete, type: boolean, required: true, constraints: "補助権限 DELETE" }
      - { name: columns, type: "list<ColumnAccess>", required: true, constraints: "主権限が NONE のカラムは含めない" }
      - { name: operations, type: "set<AllowedOperation>", required: true }
      - { name: restrictionReason, type: enum, required: false, allowed_values: [NO_PRIMARY_KEY, PRIMARY_KEY_HIDDEN, VIEW], constraints: "行えない操作の理由。無ければ null" }
      - { name: writeConfigured, type: boolean, required: true, constraints: "書き込みの接続の設定があるか（値の判定は U2）" }
      - { name: rowLabelColumns, type: "list<string>", required: true, constraints: "行を見分ける値のカラム（BR2.6。Q8: A）" }
      - { name: knownIdentifiers, type: KnownIdentifiers, required: true, constraints: "SQL の部品に渡す名前の一覧（BR3.3。R-05）" }
    constraints:
      - "operations は BR2.3 の関数だけで求める（ほかの場所で組み立てない）"
    relationships:
      - { to: ColumnAccess, cardinality: "1..n", direction: "TableAccessView → ColumnAccess" }
      - { to: DefinitionId, cardinality: "1", direction: "TableAccessView → DefinitionId" }
      - { to: KnownIdentifiers, cardinality: "1", direction: "TableAccessView → KnownIdentifiers" }

  - name: ColumnAccess
    description: "カラム1つの見せ方と入力の可否（契約 C1）。DSL の設定とカラムの実効の主権限から求める。定義の応答のカラムの項目（契約 C2 の ColumnDefinition）の元になる（BR2.8）"
    persisted: false
    attributes:
      - { name: column, type: string, required: true }
      - { name: displayName, type: "map<ja|en, string>", required: true, constraints: "DSL の表示名" }
      - { name: main, type: enum, required: true, allowed_values: [READ, FULL], constraints: "NONE のカラムは ColumnAccess を作らない" }
      - { name: valueType, type: enum, required: true, allowed_values: [STRING, INTEGER, BIG_INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, DATETIME_TZ, TIME, TIME_TZ, BINARY, OTHER], constraints: "BR7.1 の対応" }
      - { name: listVisible, type: boolean, required: true }
      - { name: listOrder, type: integer, required: false }
      - { name: width, type: integer, required: false, constraints: "DSL の list.width" }
      - { name: listFormat, type: enum, required: false, allowed_values: [DATE, DATETIME, TIME, NUMBER_GROUPED, BOOLEAN_YES_NO, BOOLEAN_ON_OFF], constraints: "BOOLEAN_ON_OFF は DSL v2 に既にあり、契約 C2 の列挙に足す（Q3: A、X2）" }
      - { name: detailVisible, type: boolean, required: true }
      - { name: searchOperator, type: enum, required: false, allowed_values: [EQUALS, CONTAINS, RANGE, CHOICE], constraints: "読み替え（BR4.6）の後の演算子。BR4.9 の表に無い組なら null（検索の項目に出さない。BR4.10）" }
      - { name: sortable, type: boolean, required: true, constraints: "BR5.2" }
      - { name: editableOnCreate, type: boolean, required: true }
      - { name: editableOnUpdate, type: boolean, required: true }
      - { name: masked, type: boolean, required: true, constraints: "フォーム部品が password" }
      - { name: validation, type: ColumnValidation, required: false, constraints: "U2（B4）が埋める余地。U1 は作らず null のまま（U2 R-02）" }
    constraints:
      - "BINARY・masked のカラムは、どの演算子の検索にも並べ替えにも使わない。OTHER は EQUALS と FIXED の選択肢の CHOICE だけ（BR4.9・BR5.2）"
    relationships:
      - { to: ColumnValidation, cardinality: "0..1", direction: "ColumnAccess → ColumnValidation" }

  - name: ColumnValidation
    description: "入力の検証の材料（契約 C2 の ColumnDefinition の検証の項目）。持ち主は U2。U1 は型と置き場だけを用意し、値を入れない（U2 R-02、U3 Q1: A）"
    persisted: false
    attributes:
      - { name: formPart, type: enum, required: true, allowed_values: [text, textarea, checkbox, switch, radio, select, date, datetime, number, password, lookup] }
      - { name: required, type: boolean, required: true }
      - { name: nullable, type: boolean, required: true }
      - { name: minLength, type: integer, required: false }
      - { name: maxLength, type: integer, required: false }
      - { name: precision, type: integer, required: false }
      - { name: scale, type: integer, required: false }
      - { name: min, type: string, required: false }
      - { name: max, type: string, required: false }
      - { name: choices, type: "list<Option>", required: false, constraints: "FIXED の選択肢だけ" }
      - { name: hasPattern, type: boolean, required: true, constraints: "式そのものは返さない" }
    constraints:
      - "項目の中身と当て方は U2 の rules.md が決める"
    relationships: []

  - name: KnownIdentifiers
    description: "SQL の部品が引用してよい名前の一覧。スキーマ・テーブル・カラムの種類ごとに別の集合で持つ（BR3.3。R-05）"
    persisted: false
    attributes:
      - { name: schemas, type: "set<string>", required: true, constraints: "写しの DSL のスキーマ名" }
      - { name: tables, type: "set<string>", required: true, constraints: "照らしたスキーマのテーブル名" }
      - { name: columns, type: "set<string>", required: true, constraints: "照らしたテーブルのカラム名だけ（ほかのテーブルのカラムを含めない）" }
    constraints:
      - "テーブル名をカラムの一覧で照らすことはない（種類ごとに別の集合で照らす）"
    relationships: []

  - name: AllowedOperation
    description: "許す操作（ADR-005 の純粋な関数の結果）"
    persisted: false
    attributes:
      - { name: value, type: enum, required: true, allowed_values: [LIST, DETAIL, CREATE, UPDATE, DELETE] }
    constraints: []
    relationships: []

  - name: BoundValue
    description: "型として読んだ後の値1つ。検索の値と主キーの値を型つきでバインドするときに使う（BR7.8。R-02）。文字列にするときは値を伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: valueType, type: enum, required: true, constraints: "ColumnAccess.valueType と同じ列挙（BINARY を除く）" }
      - { name: value, type: object, required: false, constraints: "型ごとの Java の値（BR7.8 の表）。NULL は null" }
    constraints:
      - "値の読み取りは ColumnValueCodec の1か所だけで行う（U2 も同じ部品を使う）"
    relationships: []

  - name: RowKey
    description: "行を指す主キーのカラムと値の組。文字列にするときは値を伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: values, type: "map<string, BoundValue>", required: true, constraints: "DSL の主キーのカラムをちょうどそろえる（BR6.2）。応答では文字の形に戻す（BR7.2）" }
    constraints: []
    relationships: []

  - name: RowValues
    description: "1行のカラムと値の組。値は文字か NULL。文字列にするときは値を伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: values, type: "map<string, string|null>", required: true, constraints: "応答に出してよいカラムだけ（BR2.2）。password と BINARY のカラムは値を持たない（BR7.4・BR7.5）" }
      - { name: truncatedColumns, type: "set<string>", required: true, constraints: "上限の文字数で切ったカラム（BR7.6。Q5: A）" }
    constraints: []
    relationships: []

  - name: SearchCondition
    description: "検索の条件1つ。値は型として読んだ後の形で持つ。文字列にするときは値を伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: column, type: string, required: true }
      - { name: operator, type: enum, required: true, allowed_values: [EQUALS, CONTAINS, RANGE, CHOICE], constraints: "要求からではなく ColumnAccess.searchOperator から決める" }
      - { name: value, type: BoundValue, required: false, constraints: "EQUALS・CONTAINS（CONTAINS は STRING だけ）" }
      - { name: from, type: BoundValue, required: false, constraints: "RANGE の下限" }
      - { name: to, type: BoundValue, required: false, constraints: "RANGE の上限" }
      - { name: values, type: "list<BoundValue>", required: false, constraints: "CHOICE" }
    constraints:
      - "演算子と型の組は BR4.9 の表にあるものだけ"
    relationships:
      - { to: BoundValue, cardinality: "0..n", direction: "SearchCondition → BoundValue" }

  - name: PageRequest
    description: "一覧の1ページの指定"
    persisted: false
    attributes:
      - { name: page, type: integer, required: true, min: 1 }
      - { name: size, type: integer, required: true, allowed_values: [20, 50, 100], default: 20 }
      - { name: sort, type: "SortKey", required: false, constraints: "0 か 1 個" }
    constraints: []
    relationships: []

  - name: SortKey
    description: "並べ替えのカラムと向き"
    persisted: false
    attributes:
      - { name: column, type: string, required: true }
      - { name: direction, type: enum, required: true, allowed_values: [ASC, DESC] }
    constraints: []
    relationships: []

  - name: TargetReadSession
    description: "読み取りの口が貸している間だけ使える、対象DB の接続と DB の種類の組（BR8.1。R-03）。口の外へ持ち出さない。文字列にするときは DB の種類だけを出す（BR10.1）"
    persisted: false
    attributes:
      - { name: product, type: enum, required: true, allowed_values: [MYSQL, MARIADB, POSTGRESQL] }
      - { name: connection, type: "java.sql.Connection", required: true, constraints: "閉じるのは貸した口だけ。RowSqlExecutor の外で文を作らない" }
    constraints:
      - "貸し出しの処理が戻ったら使えない（保存しない・戻り値にしない）"
    relationships: []
```

## 要約

- 保存する表は無い。すべて、要求1件の中だけで作る値である。
- `TableAccessView` は、権限の写し1つとその写しの DSL から作る判定の材料である（Q2: A）。出すカラム（`ColumnAccess`）、許す操作（`AllowedOperation`）、行を見分ける値のカラム、SQL に渡す名前の一覧（`KnownIdentifiers`）を持つ。
- `ColumnAccess` は、定義の応答のカラムの項目（契約 C2 の `ColumnDefinition`）の元になる。U1 は読み取りの項目（表示名・幅・書式・型・`masked`・`editableOn*`）を埋め、検証の材料の置き場（`ColumnValidation`）は U2 が埋める（U2 R-02）。
- 名前の一覧はスキーマ・テーブル・カラムの種類ごとに分ける（R-05）。
- 検索の値と主キーの値は、型として読んだ後の `BoundValue` で持ち、型つきでバインドする（R-02）。
- 対象DB の接続は、貸している間だけ使える `TargetReadSession` で受け取り、口の外へ出さない（R-03）。
- `RowKey`・`RowValues`・`SearchCondition`・`BoundValue`・`TargetReadSession` は、文字列にするときに値（接続の中身）を伏せる（TraceAspect の TRACE の出力に出さない）。
