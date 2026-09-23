# Entities — U2 DSL の定義（u2-dsl-definition）

U2 が持つデータは、DSL の書式（JSON Schema と書式の版）と、DSL を解釈したモデル（変更できない値の木）、読み込みの結果（誤りの一覧を含む）である。保存はしない（保存は U4）。確定した形は `functional-design-questions.md` の「DSL の形（案）」。

```yaml
entities:
  - name: DslModel
    description: 検証を通った DSL を解釈した、変更できない値の木。後続の Intent I・J・K が使う
    attributes:
      - { name: dslHash, type: string, required: true, constraints: "YAML の本文のバイト列のハッシュ値（アルゴリズムは NFR 設計）" }
      - { name: formatVersion, type: integer, required: true, allowed: [1] }
      - { name: menus, type: list<DslMenuItem>, required: true, constraints: "0件以上。並びは DSL の順" }
      - { name: tables, type: map<string, DslTable>, required: true, constraints: "キーは物理名。DSL の順を保つ" }
    relationships:
      - "DslModel 1 → 0..* DslMenuItem（最上位のメニュー）"
      - "DslModel 1 → 0..* DslTable"

  - name: DslMenuItem
    description: メニューの項目（N 階層）。テーブルに紐付く項目か、子を持つまとまり
    attributes:
      - { name: label, type: DisplayName, required: true }
      - { name: icon, type: string, required: false }
      - { name: table, type: string, required: false, references: "DslTable（物理名）" }
      - { name: items, type: list<DslMenuItem>, required: false }
    constraints:
      - "table と items の少なくとも一方を持つ"
    relationships:
      - "DslMenuItem 0..1 → 1 DslTable（table を持つとき）"
      - "DslMenuItem 1 → 0..* DslMenuItem（子）"

  - name: DisplayName
    description: 表示名（値）
    attributes:
      - { name: ja, type: string, required: true, constraints: "空の文字列を許す（未設定として扱う）" }
      - { name: en, type: string, required: true, constraints: "空の文字列を許す" }

  - name: DslTable
    description: テーブルまたはビュー1つの定義
    attributes:
      - { name: name, type: string, required: true, unique: "DSL の中で", constraints: "物理名（対応表のキー）" }
      - { name: label, type: DisplayName, required: true }
      - { name: view, type: boolean, required: true, default: false }
      - { name: primaryKey, type: list<string>, required: true, constraints: "columns のキー。無ければ空" }
      - { name: foreignKeys, type: list<DslForeignKey>, required: true }
      - { name: columns, type: map<string, DslColumn>, required: true, constraints: "キーは物理名。1件以上。DSL の順を保つ" }

  - name: DslForeignKey
    description: 外部キー（値）
    attributes:
      - { name: columns, type: list<string>, required: true, references: "同じテーブルの DslColumn" }
      - { name: referencedTable, type: string, required: true, references: "DslTable" }
      - { name: referencedColumns, type: list<string>, required: true, references: "参照先の DslColumn" }

  - name: DslColumn
    description: カラム1つの定義
    attributes:
      - { name: name, type: string, required: true, unique: "テーブルの中で" }
      - { name: label, type: DisplayName, required: true }
      - { name: dbType, type: DbType, required: true }
      - { name: formPart, type: enum, required: true, allowed: [text, textarea, checkbox, switch, radio, select, date, datetime, number, password, lookup] }
      - { name: search, type: SearchSetting, required: true }
      - { name: list, type: ListSetting, required: true }
      - { name: detail, type: DetailSetting, required: true }
      - { name: validations, type: list<Validation>, required: true, constraints: "0件以上" }
      - { name: options, type: OptionSource, required: false }

  - name: DbType
    description: DB 上の型（値）
    attributes:
      - { name: name, type: string, required: true }
      - { name: length, type: integer, required: false, min: 0 }
      - { name: precision, type: integer, required: false, min: 0 }
      - { name: scale, type: integer, required: false, min: 0 }
      - { name: nullable, type: boolean, required: true }

  - name: SearchSetting
    description: 検索条件（値）
    attributes:
      - { name: enabled, type: boolean, required: true }
      - { name: operator, type: enum, required: "enabled が true のとき", allowed: [EQUALS, CONTAINS, RANGE, CHOICE, IN] }
      - { name: default, type: string, required: false }
      - { name: collapsed, type: boolean, required: true, default: false }

  - name: ListSetting
    description: 一覧表示（値）
    attributes:
      - { name: visible, type: boolean, required: true }
      - { name: order, type: integer, required: "visible が true のとき", min: 1, constraints: "テーブルの中で重ならない" }
      - { name: width, type: integer, required: false, min: 1 }
      - { name: sortable, type: boolean, required: true }
      - { name: defaultSort, type: enum, required: false, allowed: [ASC, DESC] }
      - { name: format, type: enum, required: false, allowed: [DATE, DATETIME, TIME, NUMBER_GROUPED, BOOLEAN_YES_NO, BOOLEAN_ON_OFF], constraints: "表示の書式の種類（一覧は機能設計で確定、書式の中身は画面の Intent で決める）" }

  - name: DetailSetting
    description: 詳細・編集の画面に出すか（値）
    attributes:
      - { name: visible, type: boolean, required: true }

  - name: Validation
    description: バリデーション1つ（値）
    attributes:
      - { name: type, type: enum, required: true, allowed: [required, minLength, maxLength, min, max, pattern, unique] }
      - { name: value, type: "integer | number | string", required: "type が required・unique 以外のとき" }
      - { name: origin, type: enum, required: true, allowed: [DB, MANUAL] }
      - { name: message, type: DisplayName, required: false }

  - name: OptionSource
    description: 選択肢の出どころ（値）
    attributes:
      - { name: source, type: enum, required: true, allowed: [FIXED, REFERENCE, LOOKUP] }
      - { name: items, type: "list<{value: string, label: DisplayName}>", required: "source が FIXED のとき", constraints: "1件以上。value は重ならない" }
      - { name: table, type: string, required: "source が REFERENCE・LOOKUP のとき", references: "DslTable" }
      - { name: valueColumn, type: string, required: "source が REFERENCE・LOOKUP のとき", references: "参照先の DslColumn" }
      - { name: labelColumn, type: string, required: "source が REFERENCE のとき", references: "参照先の DslColumn" }
      - { name: lookupSearch, type: "list<{column: string, operator: SearchOperator}>", required: "source が LOOKUP のとき", constraints: "参照ピッカー専用の検索条件。1件以上" }
      - { name: lookupList, type: "list<{column: string, order: integer}>", required: "source が LOOKUP のとき", constraints: "参照ピッカー専用の一覧表示。1件以上" }

  - name: DslReadResult
    description: 読み込みの結果（契約 C4）
    attributes:
      - { name: kind, type: enum, required: true, allowed: [VALID, INVALID] }
      - { name: model, type: DslModel, required: "VALID のとき" }
      - { name: errors, type: list<DslError>, required: "INVALID のとき", constraints: "1件以上。件数の上限を設けない（絞るのは U4）" }

  - name: DslError
    description: 誤り1つ（値）
    attributes:
      - { name: kind, type: enum, required: true, allowed: [SIZE_LIMIT, DEPTH_LIMIT, ALIAS_LIMIT, FORBIDDEN_TAG, DUPLICATE_KEY, UNSUPPORTED_VERSION, SYNTAX, SEMANTIC] }
      - { name: line, type: integer, required: false, min: 1 }
      - { name: column, type: integer, required: false, min: 1 }
      - { name: path, type: string, required: false, constraints: "DSL の中の場所（例: tables.dept_mst.columns.dept_name.list.order）" }
      - { name: messageKey, type: string, required: true }
      - { name: messageArgs, type: list<string>, required: true, constraints: "DSL の中の名前・値だけ。部品の例外のメッセージを入れない" }

  - name: ActiveDsl
    description: 適用中のモデルの提供口の結果（契約 C8）
    attributes:
      - { name: kind, type: enum, required: true, allowed: [PRESENT, ABSENT] }
      - { name: model, type: DslModel, required: "PRESENT のとき" }
      - { name: dslHash, type: string, required: "PRESENT のとき", constraints: "適用中の DSL の識別（YAML の本文のバイト列から。BR5.1）。契約 C8 の Present の dslHash" }
```

## まとめ

| エンティティ | 役割 |
|---|---|
| DslModel と、その中の DslMenuItem・DslTable・DslColumn などの値 | 検証を通った DSL の解釈の結果。変更できない値の木。接続先・権限を持たない |
| DslReadResult・DslError | 読み込みの結果と誤り。誤りは文言の鍵と埋める値で持つ |
| ActiveDsl | 適用中のモデルの提供口の結果（あり・なし） |

DSL の書式（JSON Schema）は、この値の木と同じ形を定める（追加の項目を許さない）。保存（YAML の本文・識別）は U4 が持つ。
