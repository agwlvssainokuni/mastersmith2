# エンティティ — U1 u1-reference-read

**内部DB に保存するエンティティは無い。** この単位は内部DB に新しい表を持たず、移行ファイルも変えない（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md` の「エンティティの持ち主」、要件 NFR7・A7）。参照先の行は対象DB のデータで、アプリが独自に持つデータではないため、エンティティにしない（`project.md` の学び）。

ここに書くのは、要求1件の中だけで作って使い、保存しない値の型である（前の Intent の U1 `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/functional-design/entities.md` と同じ書き方）。接続のプール・トレース・ログ・アクセス制御の設定などのフレームワークの仕組みはエンティティにせず、`rules.md` の決まりとして書く。前の Intent で作った既存の型（`TableAccessView`・`ColumnAccess`・`KnownIdentifiers`・`BoundValue`・`TypedValue`・`PageRequest`・`RowQueryFailure` など）は変えずに使い、ここには載せない（使い方は `rules.md`）。

出典: 契約 C1・C2・C5・C6（`inception/contract-design/contract-summary.md`）、ADR-001〜ADR-006、この段の答え `functional-design-questions.md`（Q1〜Q4: A と「決まっていること」）。型の名前は既存のコードの書き方にそろえた案で、コード生成で変えるときは差を記録する。新しいクラスを作ることは、コード生成の計画に明記して依頼者の承認を得る（`team.md`、BR11.3）。

```yaml
entities:
  - name: DefinitionPurpose
    description: "定義の取得の用途（契約 C2 の purpose）。CREATE・UPDATE のときだけ参照先を読む（BR4.1・BR12.1）"
    persisted: false
    attributes:
      - { name: value, type: enum, required: true, allowed_values: [VIEW, CREATE, UPDATE], default: VIEW, constraints: "本文に無ければ VIEW。列挙に無い値は 400 VALIDATION_FAILED（BR1.1）" }
    constraints: []
    relationships: []

  - name: ReferenceTarget
    description: "参照元のカラムの DSL の options（REFERENCE・LOOKUP）から引いた参照先（契約 C1）。利用者の入力からは作らない。要求1件の権限の写しの DSL から、読み出しのたびに作る（BR2.1〜BR2.3）"
    persisted: false
    attributes:
      - { name: kind, type: enum, required: true, allowed_values: [REFERENCE, LOOKUP] }
      - { name: schema, type: string, required: true, constraints: "参照元と同じスキーマ（別のスキーマは範囲の外）" }
      - { name: table, type: string, required: true, constraints: "DSL の options.table。写しの DSL のテーブルの一覧と文字どおり一致するもの" }
      - { name: valueColumn, type: string, required: true, constraints: "DSL の options.valueColumn" }
      - { name: labelColumn, type: string, required: true, constraints: "ラベルに使うカラム（BR2.5）。REFERENCE は options.labelColumn、LOOKUP は options.labelColumn があればそれ、無ければ lookupList の最初のカラム（Q4: A）" }
      - { name: lookupSearch, type: "list<{column, operator}>", required: false, constraints: "LOOKUP だけ。DSL の並び。REFERENCE では空" }
      - { name: lookupList, type: "list<{column, order}>", required: false, constraints: "LOOKUP だけ。order の昇順（同じ order は DSL の並び）。REFERENCE では空" }
    constraints:
      - "参照先の形（複合の主キー・主キーの無いテーブル・ビュー・自己参照・一意でない valueColumn）では作ることを拒まない（Q1: A、BR2.4）"
      - "文字列にするときは kind とテーブルの名前だけを出す（BR10.1）"
    relationships:
      - { to: TableAccessView, cardinality: "1", direction: "ReferenceTarget → TableAccessView（参照先のテーブルを、同じ写しで既存の TableAccessPolicy にかけた判定の材料）" }

  - name: ReferenceStatus
    description: "参照の項目の状態（契約 C2 の reference.status。C1 の ReferenceAccess に、REFERENCE の件数の判定を重ねたもの）"
    persisted: false
    attributes:
      - { name: value, type: enum, required: true, allowed_values: [AVAILABLE, NO_PERMISSION, TOO_MANY], constraints: "ReferenceAccess（AVAILABLE・NO_PERMISSION）は純粋な関数 accessOf の結果（BR3.1）。TOO_MANY は REFERENCE で 201 行目を読んだとき（BR4.3）。LOOKUP に TOO_MANY は無い" }
    constraints: []
    relationships: []

  - name: ReferenceField
    description: "定義の応答のカラムの項目に足す参照の情報（契約 C2 の ReferenceField）。purpose が VIEW のときと参照でないカラムでは作らない（null）"
    persisted: false
    attributes:
      - { name: kind, type: enum, required: true, allowed_values: [REFERENCE, LOOKUP] }
      - { name: status, type: ReferenceStatus, required: true }
      - { name: options, type: "list<ReferenceOption>", required: false, min: 0, max: 200, constraints: "REFERENCE で AVAILABLE のときだけ。並びは BR4.2、同じ値の2行目以降は落とす（BR4.4）" }
      - { name: picker, type: PickerInfo, required: false, constraints: "LOOKUP で AVAILABLE のときだけ。参照先のテーブルの名前は載せない（BR3.6）" }
    constraints:
      - "AVAILABLE 以外では options・picker を持たない（BR3.6）"
    relationships:
      - { to: ReferenceOption, cardinality: "0..200", direction: "ReferenceField → ReferenceOption" }
      - { to: PickerInfo, cardinality: "0..1", direction: "ReferenceField → PickerInfo" }

  - name: ReferenceOption
    description: "REFERENCE の選択肢1つ（契約 C2）。文字列にするときは値とラベルを伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: value, type: string, required: true, constraints: "valueColumn の値を既存の型の対応で文字にしたもの（BR7.2）。切らない（BR7.3）。NULL の行は読まない（BR4.2）" }
      - { name: label, type: "string|null", required: true, constraints: "labelColumn の値。NULL・バイナリ・password は null（BR7.4・BR7.5）。既存の一覧の文字数の上限で切る（BR7.3）" }
    constraints: []
    relationships: []

  - name: PickerInfo
    description: "LOOKUP のピッカーの情報（契約 C2 の PickerInfo）"
    persisted: false
    attributes:
      - { name: searchFields, type: "list<PickerSearchField>", required: true, min: 1, constraints: "使える検索の条件だけ（BR3.2）。DSL の lookupSearch の並び" }
      - { name: listColumns, type: "list<PickerListColumn>", required: true, min: 1, constraints: "lookupList の order の順。NONE のカラムは含めない（BR3.5）" }
    constraints: []
    relationships:
      - { to: PickerSearchField, cardinality: "1..n", direction: "PickerInfo → PickerSearchField" }
      - { to: PickerListColumn, cardinality: "1..n", direction: "PickerInfo → PickerListColumn" }

  - name: PickerSearchField
    description: "ピッカーの検索の条件1つ"
    persisted: false
    attributes:
      - { name: column, type: string, required: true, constraints: "参照先の DSL のカラム名" }
      - { name: displayName, type: "map<ja|en, string>", required: true, constraints: "参照先の DSL の表示名" }
      - { name: operator, type: enum, required: true, allowed_values: [EQUALS, CONTAINS], constraints: "DSL の SearchOperator のうち使えるもの（BR3.2）。CONTAINS は文字の型だけ" }
      - { name: valueType, type: ValueType, required: true, constraints: "そのカラムの受け渡しの型。既存の ColumnDefinitionResponse.valueType と同じ語彙（businessdata.domain.ValueType の値）。画面が入力の種類を選ぶのに使う（BR5.1）" }
    constraints: []
    relationships: []

  - name: PickerListColumn
    description: "ピッカーの一覧の列1つ"
    persisted: false
    attributes:
      - { name: column, type: string, required: true }
      - { name: displayName, type: "map<ja|en, string>", required: true }
      - { name: valueType, type: ValueType, required: true, constraints: "既存の ColumnDefinitionResponse.valueType と同じ語彙（BR5.1）" }
    constraints: []
    relationships: []

  - name: ReferenceRowsQuery
    description: "参照先の検索 API の要求を、本文の形の確かめ（BR1.4）の後に読んだもの。検索とラベルの1件読みのどちらか一方。文字列にするときは条件の値と値を伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: schema, type: string, required: true, constraints: "参照元のスキーマ（DSL と照らす前の文字。照らすまで SQL に使わない）" }
      - { name: table, type: string, required: true, constraints: "参照元のテーブル" }
      - { name: column, type: string, required: true, constraints: "参照元のカラム" }
      - { name: mode, type: enum, required: true, allowed_values: [SEARCH, VALUE] }
      - { name: conditions, type: "list<ReferenceCondition>", required: false, constraints: "SEARCH のとき1件以上。値が空の条件を除いた後に1件以上（BR1.4・BR5.4）" }
      - { name: page, type: integer, required: false, min: 1, max: 1000000, constraints: "SEARCH のとき必須（1 から数える。決まっていること、契約との差 D1）" }
      - { name: value, type: ReferenceValue, required: false, constraints: "VALUE のとき必須" }
    constraints:
      - "conditions と value はどちらか一方だけ（両方・どちらも無いは 400。BR1.4）"
    relationships:
      - { to: ReferenceCondition, cardinality: "0..n", direction: "ReferenceRowsQuery → ReferenceCondition" }
      - { to: ReferenceValue, cardinality: "0..1", direction: "ReferenceRowsQuery → ReferenceValue" }

  - name: ReferenceCondition
    description: "ピッカーの検索の条件1つ（要求の値）。文字列にするときは値を伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: column, type: string, required: true, constraints: "PickerInfo.searchFields のカラムと文字どおり一致しないと 403 の同じ応答（BR5.3）" }
      - { name: value, type: string, required: true, constraints: "1,000 文字（Unicode のコードポイント）まで（BR1.4）。空は条件として使わない（BR5.4）" }
    constraints: []
    relationships: []

  - name: ReferenceValue
    description: "参照先の値1つ（契約 C1）。ラベルの1件読みと保存前の確かめに使う。受け取ったままの文字で持ち、valueColumn の DSL の型に変えて束ねる（BR7.1）。文字列にするときは値を伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: value, type: string, required: true, constraints: "ラベルの1件読みでは 1,000 文字まで（BR1.4）" }
    constraints: []
    relationships: []

  - name: ReferenceRows
    description: "参照先の検索 API の成功の応答（契約 C2 の ReferenceRows）"
    persisted: false
    attributes:
      - { name: rows, type: "list<ReferenceRow>", required: true, constraints: "SEARCH は 0〜20 件。VALUE は必ず1件（0 件は 403 の同じ応答。BR6.4）" }
      - { name: page, type: integer, required: false, constraints: "SEARCH のとき要求の page。VALUE では null" }
      - { name: hasNext, type: boolean, required: true, constraints: "SEARCH で 21 行目を読んだら true（BR5.7）。VALUE では false" }
    constraints: []
    relationships:
      - { to: ReferenceRow, cardinality: "0..20", direction: "ReferenceRows → ReferenceRow" }

  - name: ReferenceRow
    description: "参照先の行1つ。文字列にするときは値・ラベル・セルを伏せる（BR10.1）"
    persisted: false
    attributes:
      - { name: value, type: string, required: true, constraints: "valueColumn の値（BR7.2・BR7.3）" }
      - { name: label, type: "string|null", required: true, constraints: "ラベルに使うカラムの値（BR2.5・BR7.3〜BR7.5）" }
      - { name: cells, type: "list<string|null>", required: true, constraints: "SEARCH のとき PickerInfo.listColumns の並びの値。VALUE では空（BR5.9・BR6.3）" }
    constraints: []
    relationships: []

  - name: ReferenceReadSpec
    description: "既存の行の SQL の部品（RowSqlGateway）の search に渡す、参照先の読み取りの指定（ADR-002、BR11.2・BR11.4）。指定が無いときは今の一覧と同じ動き"
    persisted: false
    attributes:
      - { name: valueColumn, type: string, required: true, constraints: "切らずに選ぶ値のカラム（主キーと同じ keyExpression・上限なし。主キーでなくても切らない。BR7.3）" }
      - { name: displayColumns, type: "list<string>", required: true, constraints: "ラベルに使うカラムと LOOKUP の lookupList の列。一覧の上限 LIST_TEXT_LIMIT で切って選ぶ（selectExpression）。参照先の判定の材料にあるカラムだけ。バイナリ・password は選ばない（BR7.4）。check・countState では空" }
      - { name: excludeNullValue, type: boolean, required: true, constraints: "valueColumn が NULL の行を読まない条件を足す（BR4.2・BR5.8）" }
      - { name: order, type: "list<SortKey>", required: true, constraints: "固定の並び（ラベルの昇順 → valueColumn の昇順 → 参照先の主キーの昇順。NULL は最後）。ReferenceLookup が純粋な関数で作り、利用者の sortKey と view.orderKeys からは作らない（BR11.4）" }
      - { name: count, type: boolean, required: true, constraints: "件数の文を流すか。参照先の読み取りでは false（BR5.7）" }
      - { name: maxRows, type: integer, required: true, constraints: "読む行の上限（REFERENCE と countState 201・SEARCH 21・VALUE 1・check 201。BR8.6）" }
      - { name: offset, type: integer, required: true, min: 0, constraints: "SEARCH だけ (page-1)×20。ほかは 0" }
    constraints:
      - "新しい SQL のメソッドは作らない（既存の search の引数を足す）"
      - "文ごとの時間の上限は持たない（渡す TargetReadSession の queryTimeoutSeconds で絞る。BR8.4）"
    relationships: []

  - name: ReferenceCheckResult
    description: "保存前の確かめの結果（契約 C1 の check。U2 が呼ぶ。結果の型）"
    persisted: false
    attributes:
      - { name: case, type: enum, required: true, allowed_values: [FOUND, NOT_FOUND_OR_DENIED, FAILED], constraints: "契約 C1 の Unavailable を、理由を持つ FAILED にした（契約との差 D3）" }
      - { name: failureReason, type: enum, required: false, allowed_values: [TIMEOUT, UNAVAILABLE, MISMATCH], constraints: "FAILED のときだけ。U2 が既存の code に写す（BR9.5）" }
    constraints:
      - "値・ラベルを持たない（文字列にしても値は出ない）"
      - "FOUND は、DB の比べ方で一致した 200 行までの中に、型つきの値として厳密に一致する行があるときだけ（BR9.4・BR7.6）"
    relationships: []

  - name: ReferenceCountState
    description: "U2 が呼ぶ2つ目の口 countState(target) の結果（契約 C1 に足す口。BR9.7）"
    persisted: false
    attributes:
      - { name: case, type: enum, required: true, allowed_values: [AVAILABLE, TOO_MANY, NO_PERMISSION, FAILED], constraints: "REFERENCE の選択肢と同じ読み方で 201 行目を読んだら TOO_MANY。参照でない・accessOf が NO_PERMISSION は NO_PERMISSION。LOOKUP は数えず AVAILABLE" }
      - { name: failureReason, type: enum, required: false, allowed_values: [TIMEOUT, UNAVAILABLE, MISMATCH], constraints: "FAILED のときだけ（ReferenceCheckResult と同じ理由）" }
    constraints:
      - "値・ラベル・件数そのものを持たない"
    relationships: []
```

`ReadPurpose`（`targetdb.domain` の既存の列挙）には `REFERENCE` を足す（契約 C5、決まっていること）。列挙の値の足し方は既存の型への変更で、新しいエンティティではないため、上の一覧には載せない（BR8.1）。

## 要約

- 保存する表は無い。すべて、要求1件の中だけで作る値である。
- `ReferenceTarget` は、参照元のカラムの DSL の options から引いた参照先で、利用者の入力からは作らない。参照先のテーブルの判定の材料は、同じ権限の写しで既存の `TableAccessView` を作って使う（名前の照合・出すカラム・型も既存のまま）。
- 定義の応答に足すのは `ReferenceField`（状態・選択肢・ピッカーの情報）だけで、`purpose` が `VIEW`（既定）のときは作らない。
- 参照先の検索 API は、`ReferenceRowsQuery` で検索（`SEARCH`）とラベルの1件読み（`VALUE`）を受け、`ReferenceRows` で返す。
- 既存の行の SQL の部品には、`ReferenceReadSpec`（切らない値のカラム・切る表示の列・NULL の値を除く・固定の並び・件数を数えない・読む行の上限・読み飛ばす数）を渡すだけで、新しい SQL のメソッドは作らない。
- 保存前の確かめ（U2 が呼ぶ）は `ReferenceCheckResult`（ある・無いか読めない・失敗）で返す。「ある」は型つきの値の厳密な一致で決める。候補の多さは2つ目の口 `countState` が `ReferenceCountState`（AVAILABLE・TOO_MANY・NO_PERMISSION・FAILED）で返す。
- ピッカーの検索の条件と一覧の列は、それぞれ `valueType`（既存の `ColumnDefinitionResponse.valueType` と同じ語彙）を持つ。
- 値を持つ型（`ReferenceOption`・`ReferenceRowsQuery`・`ReferenceCondition`・`ReferenceValue`・`ReferenceRow`）は、文字列にするときに値を伏せる（`TraceAspect` の TRACE の出力に出さない）。
