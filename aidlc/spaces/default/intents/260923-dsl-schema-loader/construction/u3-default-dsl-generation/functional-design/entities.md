# Entities — U3 既定の DSL の生成（u3-default-dsl-generation）

U3 は保存するデータを持たない。U1 のスキーマの写し（U1 の `TargetSchema`）を受け、U2 の書式の DSL（YAML の本文）を作って返す。作る DSL の形は U2 の `DslModel` と同じ（U2 の entities.md が正本）。U3 が独自に持つのは、型の分類と生成の結果だけである。

```yaml
entities:
  - name: TypeCategory
    description: DB 上の型の分類（値）。3種類の DB の型の名前を対応させる
    attributes:
      - { name: value, type: enum, required: true, allowed: [SHORT_TEXT, LONG_TEXT, NUMBER, BOOLEAN, DATE, DATETIME, TIME, UNSUPPORTED] }
    constraints:
      - "型の名前が分からないものは UNSUPPORTED"
      - "文字列の型は長さ 255 以下なら SHORT_TEXT、256 以上または長さの無い長い文字列の型なら LONG_TEXT"

  - name: DefaultDslResult
    description: 既定の DSL の生成の結果（契約 C5）
    attributes:
      - { name: kind, type: enum, required: true, allowed: [GENERATED, TARGET_UNCONFIGURED, TARGET_UNAVAILABLE] }
      - { name: yamlBytes, type: bytes, required: false, constraints: "kind が GENERATED のときだけ。UTF-8 の YAML の本文。U2 の検証を通ったもの" }
      - { name: dslHash, type: string, required: false, constraints: "kind が GENERATED のときだけ。yamlBytes の識別（U2 で求める）" }
      - { name: reason, type: enum, required: false, allowed: [TIMEOUT, CONNECTION_FAILED], constraints: "kind が TARGET_UNAVAILABLE のときだけ" }
    constraints:
      - "接続先・ユーザー名・パスワード・内部の例外のメッセージを持たない"
```

## まとめ

| エンティティ | 役割 | 保存 |
|---|---|---|
| TypeCategory | 型の分類。フォーム部品・検索・一覧・詳細の初期値を決める元 | しない |
| DefaultDslResult | 生成の結果（生成できた・設定が無い・接続できない） | しない（保存と監査は U4） |
