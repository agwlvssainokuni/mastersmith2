# Entities — U1 対象DB（u1-target-db）

U1 が持つデータは、対象DB から読み取ったスキーマの写しだけである。保存はせず、読み取りの要求のたびに作る値（変更できない値）とする。対象DB の接続先の設定は Spring Boot の設定の仕組みの値なので、エンティティにせず `rules.md` の決まり（BR1.x）として書く（`aidlc/spaces/default/memory/project.md` の Code Style）。

```yaml
entities:
  - name: TargetSchema
    description: 対象DB の設定したスキーマの写し。読み取りのたびに作り、保存しない
    attributes:
      - { name: databaseProduct, type: enum, required: true, allowed: [MYSQL, MARIADB, POSTGRESQL] }
      - { name: schemaName, type: string, required: true, constraints: "設定したスキーマ名。DB が返した大文字・小文字のまま" }
      - { name: tables, type: list<TargetTable>, required: true, constraints: "テーブルとビュー。0件もありうる。並びは問わない" }
    constraints:
      - "tables の name はスキーマの中で重ならない"
    relationships:
      - "TargetSchema 1 → 0..* TargetTable（持つ）"

  - name: TargetTable
    description: テーブルまたはビュー1つ
    attributes:
      - { name: name, type: string, required: true, unique: "スキーマの中で", constraints: "物理名。DB が返した大文字・小文字のまま" }
      - { name: view, type: boolean, required: true, default: false }
      - { name: comment, type: string, required: false, constraints: "コメントが無ければ無し。空の文字列は無しとして扱う" }
      - { name: columns, type: list<TargetColumn>, required: true, constraints: "定義の順。1件以上" }
      - { name: primaryKey, type: list<string>, required: true, constraints: "主キーのカラム名を、主キーの中の順に。無ければ空。ビューは空" }
      - { name: foreignKeys, type: list<TargetForeignKey>, required: true, constraints: "無ければ空" }
    constraints:
      - "columns の name はテーブルの中で重ならない"
      - "primaryKey・foreignKeys.columns の名前は columns にある"
    relationships:
      - "TargetTable 1 → 1..* TargetColumn（持つ）"
      - "TargetTable 1 → 0..* TargetForeignKey（持つ）"

  - name: TargetColumn
    description: カラム1つ
    attributes:
      - { name: name, type: string, required: true, unique: "テーブルの中で" }
      - { name: dbType, type: TargetDbType, required: true }
      - { name: nullable, type: boolean, required: true }
      - { name: defaultValue, type: string, required: false, constraints: "DB が返した既定値の式をそのまま。無ければ無し" }
      - { name: comment, type: string, required: false, constraints: "空の文字列は無しとして扱う" }

  - name: TargetDbType
    description: カラムの DB 上の型（値）
    attributes:
      - { name: typeName, type: string, required: true, constraints: "DB が返した型の名前（例: varchar・int4・DATETIME）" }
      - { name: length, type: integer, required: false, min: 0, constraints: "文字列の長さ。無ければ無し" }
      - { name: precision, type: integer, required: false, min: 0 }
      - { name: scale, type: integer, required: false, min: 0 }

  - name: TargetForeignKey
    description: 外部キー1つ
    attributes:
      - { name: name, type: string, required: false }
      - { name: columns, type: list<string>, required: true, constraints: "1件以上" }
      - { name: referencedTable, type: string, required: true, constraints: "参照先のテーブル名。別のスキーマのテーブルなら、その外部キーは写しに含めない" }
      - { name: referencedColumns, type: list<string>, required: true, constraints: "columns と同じ件数" }

  - name: TargetSchemaResult
    description: 読み取りの結果（契約 C1）。成功・設定が無い・接続できない のどれか
    attributes:
      - { name: kind, type: enum, required: true, allowed: [SUCCESS, UNCONFIGURED, UNAVAILABLE] }
      - { name: schema, type: TargetSchema, required: "kind が SUCCESS のときだけ" }
      - { name: reason, type: enum, required: "kind が UNAVAILABLE のときだけ", allowed: [TIMEOUT, CONNECTION_FAILED] }
    constraints:
      - "接続先・ユーザー名・パスワード・内部の例外のメッセージを持たない"
```

## まとめ

| エンティティ | 役割 | 保存 |
|---|---|---|
| TargetSchema | 設定したスキーマの写し | しない（読み取りのたびに作る） |
| TargetTable | テーブル・ビュー | しない |
| TargetColumn・TargetDbType | カラムと DB 上の型 | しない |
| TargetForeignKey | 外部キー（同じスキーマの中の参照だけ） | しない |
| TargetSchemaResult | 読み取りの結果（成功・設定が無い・接続できない） | しない |

いずれも変更できない値で、秘密情報（接続先・ユーザー名・パスワード）を持たない。
