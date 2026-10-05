# エンティティ — U2 dsl-v2

出典: 単位 `unit-of-work.md`（U2）・`unit-of-work-story-map.md`、要件 `requirements.md`、部品 `components.md`（DslDefinition・DslManagement・DslAdminUi）、契約 `contract-summary.md`（C3）、ADR-005・ADR-007、ストーリー US5.2・US6.1、この段の答え `functional-design-questions.md`（Q1〜Q7、すべて A）。

この単位のエンティティは、アプリが独自に持つ値の形だけを書く（`project.md` の学び）。内部DB の表は変えない（`dsl_previews`・`dsl_applied_revisions` は本文と識別だけを持つ）。DSL のモデルは内部DB の行ではなく、保存した本文を読んで作る、変更できない値の木である。既存の項目（カラムの型・フォーム部品・検索・一覧・詳細・バリデーション・選択肢）は版 1 から変えないので、ここでは版 2 で変わる・足すものだけを詳しく書く。

```yaml
entities:
  - name: DslDocumentV2
    description: 利用者が投入する DSL の YAML の根（書式の版 2）。JSON Schema `dsl/dsl-schema-v2.json` が形の正。どの対応表もここに無い項目を許さない。
    attributes:
      - {name: version, type: integer, required: true, allowed: [2], constraints: "2 以外（1・無し・整数でない）は書式の版の誤り（BR1.1）"}
      - {name: menus, type: list<DslMenuItemV2>, required: true, constraints: "深さ 5 段まで（BR2.1）"}
      - {name: schemas, type: "map<schemaName, DslSchemaV2>", required: true, constraints: "キーはスキーマ名（1文字以上）。中身はちょうど1つ（BR1.3）。キーの重なりは重複キーの誤り（BR1.4）"}
    relationships:
      - {to: DslSchemaV2, cardinality: "1..1（形は 1..n を受け付け、意味の検証で 1 に絞る）", direction: owns}
      - {to: DslMenuItemV2, cardinality: "0..n", direction: owns}

  - name: DslSchemaV2
    description: スキーマ1つ。権限の対象の最上位の階層（契約 C3 の DslSchema）。
    attributes:
      - {name: name, type: string, required: true, unique: "DSL の中で一意（対応表のキー）", min_length: 1}
      - {name: label, type: DisplayName, required: true, constraints: "ja・en とも必須（空の文字列は未設定として要約で数える）。契約 C3 の displayName: string との差（functional-spec.md の 9節）"}
      - {name: tables, type: "map<tableName, DslTable>", required: true, constraints: "キーはテーブル名で、このスキーマの中で一意。0 件を許す（空の対象DB から生成した DSL）"}
    relationships:
      - {to: DslTable, cardinality: "0..n", direction: owns}

  - name: DslTable
    description: テーブルまたはビュー1つ（既存の形のまま。版 2 ではスキーマの下に置く）。
    attributes:
      - {name: name, type: string, required: true, unique: "スキーマの中で一意"}
      - {name: label, type: DisplayName, required: true}
      - {name: view, type: boolean, required: true}
      - {name: primaryKey, type: list<string>, required: true}
      - {name: foreignKeys, type: list<DslForeignKey>, required: true, constraints: "referencedTable は同じスキーマの中のテーブル名（BR1.5）"}
      - {name: columns, type: "map<columnName, DslColumn>", required: true, min: 1}
    relationships:
      - {to: DslColumn, cardinality: "1..n", direction: owns}

  - name: DslColumn
    description: カラム1つ（既存の形のまま。選択肢の出どころ options.table は同じスキーマの中のテーブル名、BR1.5）。
    attributes:
      - {name: name, type: string, required: true, unique: "テーブルの中で一意"}
      - {name: label, type: DisplayName, required: true}
      - {name: "dbType・formPart・search・list・detail・validations・options", type: 既存の値の型, required: "既存のとおり", constraints: "版 1 から変えない"}

  - name: TableRef
    description: メニューの項目がテーブルを指す組（版 2 で足す）。Navigation・テーブルの画面の置き場の鍵にも使う（契約 C3・C9）。
    attributes:
      - {name: schema, type: string, required: true, min_length: 1, references: DslSchemaV2.name}
      - {name: name, type: string, required: true, min_length: 1, references: "DslSchemaV2.tables のキー"}
    constraints:
      - "指す先のスキーマとテーブルが DSL にあること（BR1.6）"

  - name: DslMenuItemV2
    description: メニューの項目（N 階層）。テーブルを指す項目か、子を持つまとまりか、その両方。
    attributes:
      - {name: label, type: DisplayName, required: true}
      - {name: icon, type: "string or null", required: false, default: null, constraints: "dsl では名前を問わない（許す名前との照らし合わせは U5 navigation、契約 C9）"}
      - {name: table, type: "TableRef or null", required: false, default: null}
      - {name: items, type: "list<DslMenuItemV2> or null", required: false, default: "空"}
    constraints:
      - "table と items の少なくとも一方を持つ（既存の決まり）"
      - "深さ（menus の直下を 1 段目とし、項目1つを1段と数える）は 5 以下（BR2.1）"
    relationships:
      - {to: DslMenuItemV2, cardinality: "0..n", direction: "owns（子）"}
      - {to: TableRef, cardinality: "0..1", direction: references}

  - name: DslModel
    description: 検証を通った DSL から作る、変更できない値の木（既存の record を版 2 に広げる）。`ActiveDslModelProvider.current()` の Present が持つ（契約 C3）。
    attributes:
      - {name: dslHash, type: string, required: true, constraints: "本文のバイト列の SHA-256 の 16 進数の小文字 64 文字（既存）"}
      - {name: formatVersion, type: integer, required: true, allowed: [2], constraints: "DslFormat.CURRENT_VERSION を 2 にする"}
      - {name: schemas, type: list<DslSchema>, required: true, constraints: "DSL の順を保つ。版 1 の平らな tables は持たない"}
      - {name: menus, type: list<DslMenuItem>, required: true, constraints: "起動時の読み直しでは深すぎる枝を落とした後の木（BR2.3）"}
    relationships:
      - {to: DslSchema, cardinality: "1..1", direction: owns}

  - name: DslMenuDepthLimit
    description: メニューの深さの上限（定数。DslFormat に置く）。
    attributes:
      - {name: maxMenuDepth, type: integer, required: true, default: 5, constraints: "5 段目まで受け付け、6 段目の項目があれば拒否（Q3: A）"}

  - name: SafeYamlLimits
    description: 上限つきの安全な YAML の読み込みの口（SafeYamlReader、dsl.service）に呼ぶ側が渡す上限（契約 C3 の limits に展開後の節の数を足した形）。
    attributes:
      - {name: maxBytes, type: integer, required: true, min: 1}
      - {name: maxDepth, type: integer, required: true, min: 1, constraints: "対応表と並びの段の数（既存の数え方）"}
      - {name: maxAliases, type: integer, required: true, min: 0, constraints: "コレクションを指す別名の数"}
      - {name: maxExpandedNodes, type: integer, required: true, min: 1, constraints: "別名を展開した後の節の数（契約 C3 に足す項目）"}

  - name: SafeYamlResult
    description: 安全な読み込みの口の結果（sealed）。DSL の誤りの型（DslError）に依らない。
    variants:
      - name: Parsed
        attributes:
          - {name: tree, type: JSON の木, required: true, constraints: "空の文書は null の節"}
          - {name: positions, type: "位置を引く口（JSON Pointer → 行・列）", required: true, constraints: "dsl.service の型で返し、dsl.parse の型を外へ出さない（契約 C3 との差）"}
      - name: Rejected
        attributes:
          - {name: kind, type: SafeYamlRejectionKind, required: true}
          - {name: line, type: "integer or null", required: false, min: 1}
          - {name: column, type: "integer or null", required: false, min: 1, constraints: "line と column は両方あるか両方無いか"}
          - {name: path, type: "string or null", required: false, constraints: "誤りの場所（JSON Pointer を道の形にしたもの）"}
    constraints:
      - "文字列にしたときは、区分・位置・節の数（Parsed）だけを出し、木の中身を出さない（BR6.5）"

  - name: SafeYamlRejectionKind
    description: 読み込みの拒否の区分。
    allowed: [TOO_LARGE, TOO_DEEP, TOO_MANY_ALIASES, TAG_NOT_ALLOWED, DUPLICATE_KEY, SYNTAX]

  - name: DslStatusV2
    description: DSL の管理の「今の状態」（既存の DslStatus に項目を1つ足す）。
    attributes:
      - {name: applied, type: "Applied or null", required: false, constraints: 既存}
      - {name: preview, type: "Preview or null", required: false, constraints: 既存}
      - {name: appliedUnreadable, type: boolean, required: true, default: false, constraints: "applied があり、提供口が Absent（または提供口の識別が applied の識別と違う）とき true。新しいデータは持たない（BR3.6）"}

  - name: PreviewDiffV2
    description: プレビューと適用中の DSL の違い（既存の Diff をスキーマの階層に広げる）。
    attributes:
      - {name: appliedExists, type: boolean, required: true}
      - {name: schemas, type: list<SchemaDiff>, required: true}
  - name: SchemaDiff
    attributes:
      - {name: name, type: string, required: true}
      - {name: label, type: DisplayName, required: true, constraints: "プレビューの表示名（減ったスキーマは適用中の表示名）"}
      - {name: change, type: enum, allowed: [ADDED, REMOVED, CHANGED, UNCHANGED], required: true, constraints: "CHANGED は表示名の違いだけ（テーブルの違いはテーブルの行で示す）"}
      - {name: tables, type: list<TableDiff>, required: true, constraints: "既存の TableDiff の形のまま"}

  - name: PreviewSummaryV2
    description: プレビューの要約（既存の Summary に項目を足す）。
    attributes:
      - {name: schemaCount, type: integer, required: true, min: 0}
      - {name: "tableCount・viewCount・columnCount・missingDisplayNames・missingDisplayNameTotal", type: 既存, constraints: "表示名の未設定にスキーマの表示名も数える"}
      - {name: menuTree, type: list<MenuNode>, required: true, constraints: "MenuNode.table を TableRef（schema・name）か null にする"}

  - name: PreviewWarningKindV2
    description: 照合の警告の種類（既存の列挙に1つ足す）。
    allowed: [TABLE_MISSING, COLUMN_MISSING, TYPE_MISMATCH, TARGET_UNCONFIGURED, TARGET_UNAVAILABLE, SCHEMA_MISMATCH]
```

## 要約

- **DSL の書式（版 2）**: 根に `version: 2`・`menus`・`schemas` を持つ。`schemas` はスキーマ名をキーにした対応表で、スキーマごとに表示名 `label`（ja・en）と、今と同じ形の `tables` を持つ（Q1: A）。形は複数のスキーマを受け付けるが、意味の検証でちょうど1つに絞る（Q2: A）。
- **テーブルを指す組**: メニューの項目は `table: {schema, name}` でテーブルを指す。外部キーの参照先と選択肢の出どころは、同じスキーマの中のテーブル名（文字列）のまま（Q1: A）。
- **モデル**: `DslModel` は `formatVersion` 2 で `schemas` と `menus` を持ち、版 1 の平らな `tables` は持たない。提供口 `ActiveDslModelProvider` と `ActiveDsl` の形は変えない（契約 C3）。
- **メニューの深さ**: 上限は 5 段で、`menus` の直下を 1 段目と数える（Q3: A）。
- **安全な読み込みの口**: 呼ぶ側が4つの上限を渡し、結果は木と位置を引く口か、区分と位置だけの拒否（ADR-007）。
- **画面への応答**: 今の状態に `appliedUnreadable` を足し（Q5: A）、プレビューの違いをスキーマの階層に、要約にスキーマの数を足し（Q6: A）、照合の警告に `SCHEMA_MISMATCH` を足す（Q2: A）。
