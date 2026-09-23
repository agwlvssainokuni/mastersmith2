# Infrastructure Design — U3 既定の DSL の生成（u3-default-dsl-generation）— 設計の要点の確認

U3 は新しい依存も外部への接続も足さない library で、基盤に足すものはほとんど無い。新しく依頼者に尋ねる論点は無いため、要点を案として確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。成果物は cicd-pipeline.md と traceability.json。

## 設計の要点（案）

- **依存**: 足さない（SnakeYAML は U2 で直接の依存にしたものを使う）
- **検査**: 既存の `./gradlew verify` のまま。U3 の単体テスト（型の分類の対応、書き出しの形の固定、同じ写しから同じバイト列、任意の文字列で構造が壊れない性質ベースのテスト、制御文字の除去、10MB を超えたら失敗）は `test` の段で動く。3種類の DB の写しから生成して U2 の検証を通る結合テストは、U1 の Testcontainers の DB を使って `integrationTest` の段で動く
- **時間の測定**: 100 テーブル × 100 カラムの生成の全体（30 秒）と内訳（U1 の読み取り・組み立てと書き出し・U2 の検証・保存と応答）は Build and Test で、U1 の compose の profile の見本の DB（100 × 100 を作る SQL）を使って3種類それぞれ測る。内訳は U3 の DEBUG のログで見る
- **成果物**: cicd-pipeline.md と traceability.json

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U3 の基盤設計の文書を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
