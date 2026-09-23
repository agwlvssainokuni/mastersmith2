# Logical Components — U3 既定の DSL の生成（u3-default-dsl-generation）

U3 の論理的な部品と、障害の範囲を示す。

## 1. 部品

| 部品 | 役割 | 置き場（パッケージ） |
|---|---|---|
| DefaultDslGenerator | 生成の口（契約 C5）。U1 の読み取り → 組み立て → 書き出し → U2 の検証を順に呼び、結果の型で返す | `cherry.mastersmith.dslmanage.generate` |
| DslTreeBuilder | 写しから DSL の値の木（順序を持つ対応表）を組み立てる（機能設計の BR1.x〜BR4.x） | `cherry.mastersmith.dslmanage.generate` |
| TypeCategoryMapping | 3種類の DB の型の名前を分類に対応させる表（BR2.2〜BR2.5） | `cherry.mastersmith.dslmanage.generate` |
| DslYamlWriter | 固定の `DumperOptions` で YAML の本文に書き出す。コメントの制御文字を取り除く | `cherry.mastersmith.dslmanage.generate` |

`dslmanage` の中に置く（ADR-001）。U3 は `targetdb`（U1）と `dsl`（U2）に依存し、U4 から呼ばれる。

## 2. 障害の範囲

- U3 は外部に直接は接続しない。対象DB の停止・遅延は U1 の結果（UNCONFIGURED・UNAVAILABLE）として受け、そのまま U4 に返す。
- 生成した本文の大きさは最大 10MB で、組み立ての木・本文・U2 の検証の分のメモリを一時的に使う。重い処理の同時の数は U4 が1つに絞る（U4 の NFR1.13）。
- 失敗しても内部DB の状態を変えない（保存は U4 が検証を通った後に行う）。
