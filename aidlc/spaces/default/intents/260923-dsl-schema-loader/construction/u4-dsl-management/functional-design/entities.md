# Entities — U4 DSL の管理（u4-dsl-management）

U4 が内部DB に保存するのは、プレビュー（最大1件）と適用の履歴である。要約・違い・照合の警告は、表示のたびに求める値で保存しない。監査の記録は既存の AuditLog の持ち物で、U4 は出来事を出し、監査の表の列を足す。

```yaml
entities:
  - name: DslPreview
    description: 今のプレビュー。管理者全員で共有し、最大1件
    attributes:
      - { name: previewId, type: uuid, required: true, unique: true, constraints: "置くたびに新しく振る" }
      - { name: yamlText, type: text, required: true, constraints: "YAML の本文（UTF-8）。5MB まで" }
      - { name: dslHash, type: string, required: true, constraints: "yamlText のバイト列の識別（U2）" }
      - { name: source, type: enum, required: true, allowed: [GENERATED, UPLOAD, PASTE, RESTORE] }
      - { name: placedByUserId, type: uuid, required: true, references: "User（UserAccount）" }
      - { name: placedAt, type: timestamp, required: true, constraints: "UTC" }
    constraints:
      - "表の行は最大1行（行の鍵を固定の値にし、置き換えは1文の MERGE で行う。BR1.4）"
      - "U2 の検証を通った DSL だけ"
    relationships:
      - "DslPreview * → 1 User（置いた人）"

  - name: DslAppliedRevision
    description: 適用の履歴の1件。適用中の DSL は、適用した日時が最も新しい1件（同じ日時なら追加の順）
    attributes:
      - { name: revisionId, type: uuid, required: true, unique: true }
      - { name: yamlText, type: text, required: true }
      - { name: dslHash, type: string, required: true }
      - { name: source, type: enum, required: true, allowed: [GENERATED, UPLOAD, PASTE, RESTORE] }
      - { name: appliedByUserId, type: uuid, required: true, references: "User（UserAccount）" }
      - { name: appliedAt, type: timestamp, required: true, constraints: "UTC" }
      - { name: sequence, type: long, required: true, unique: true, constraints: "追加の順（同じ日時の並べ替えに使う）" }
    constraints:
      - "行の数は mastersmith.dsl.history-limit（既定 20、1以上）まで"
    relationships:
      - "DslAppliedRevision * → 1 User（適用した人）"

  - name: PreviewView
    description: プレビューの表示の中身（値）。表示のたびに求め、保存しない
    attributes:
      - { name: preview, type: DslPreview, required: true }
      - { name: summary, type: "{tableCount, viewCount, columnCount, menuTree, missingDisplayNames(先頭100件), missingDisplayNameTotal}", required: true }
      - { name: diff, type: "{appliedExists, tables[{name, change: ADDED/REMOVED/CHANGED/UNCHANGED, columns[{name, change: ADDED/REMOVED/CHANGED, changedItems}]}]}（変わらないカラムは並べない）", required: true }
      - { name: warnings, type: "list<{kind: TABLE_MISSING/COLUMN_MISSING/TYPE_MISMATCH/TARGET_UNCONFIGURED/TARGET_UNAVAILABLE, path, message}>", required: true }

  - name: DslOperationEvent
    description: DSL の操作の出来事（アプリの中、契約 C7）。AuditLog が確定の後に受けて記録する
    attributes:
      - { name: type, type: enum, required: true, allowed: [DSL_GENERATED, DSL_SUBMITTED, DSL_SUBMISSION_REJECTED, DSL_APPLIED, DSL_PREVIEW_DISCARDED] }
      - { name: actorUserId, type: uuid, required: true }
      - { name: occurredAt, type: timestamp, required: true }
      - { name: dslHash, type: string, required: false, constraints: "読む前に止めた投入では無し" }
      - { name: source, type: enum, required: false, allowed: [GENERATED, UPLOAD, PASTE, RESTORE] }
      - { name: rejectionKind, type: string, required: false, constraints: "受け付けなかった投入のときだけ。最初の誤りの種類、または SIZE_LIMIT" }
      - { name: traceId, type: string, required: false }
    constraints:
      - "DSL の本文・対象DB の接続先を持たない"
```

## まとめ

| エンティティ | 役割 | 保存 |
|---|---|---|
| DslPreview | 今のプレビュー（最大1件、共有） | 内部DB |
| DslAppliedRevision | 適用の履歴（最新が適用中、上限の件数まで） | 内部DB |
| PreviewView | 要約・違い・照合の警告 | しない（表示のたびに求める） |
| DslOperationEvent | 監査への出来事 | しない（AuditLog が監査の表に記録する） |

監査の表（既存の `audit_events`）には、操作した人・DSL の識別・出どころ・理由の種類の列を Flyway の移行で足す。
