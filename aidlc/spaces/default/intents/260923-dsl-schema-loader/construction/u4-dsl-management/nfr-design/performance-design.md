# Performance Design — U4 DSL の管理（u4-dsl-management）

U4 の性能の要件（`construction/u4-dsl-management/nfr-requirements/performance-requirements.md`）を満たす作りの方針。要点の確認は `nfr-design-questions.md`。

## 1. プレビューの表示（NFR1.8・NFR1.9）

```text
GET /preview (heavy: permit held, see scalability-design.md):
  row  = previewRepository.find()                       // BLOB bytes
  view = previewCache.get(row.previewId)                // one entry, same previewId only
         ?: dslReader.read(row.bytes)                   // U2, up to 10 MiB
  diff = compare(view.model, activeDslHolder.current()) // applied model already in memory
  warnings = reconcile(view.model, targetSchemaReader.readSchema(COMPARE))  // U1: connect 3s, query 5s each
  -> Preview(summary, diff, warnings)
```

- 適用中のモデルは U2 の ActiveDslModelHolder が持っているものを使い、表示のたびに読み直さない。
- プレビューの読み込みの結果は、同じ `previewId` の間だけアプリの中に1つ持つ（プレビューを置き換え・破棄・適用したら捨てる）。2回目以降の表示は、読み込みを飛ばして照合と違いだけになる。
- 照合は U1 の `readSchema(COMPARE)`（接続 3 秒・問い合わせ1回 5 秒）。照合の全体の上限は置かない。対象DB が応答しないときの最悪は 23〜28 秒で、10 秒の目標を超えることを許す（U1 の NFR 設計のレビュー R-01 への依頼者の決定 B）。正常なときは 10 秒以内を目標とし、Build and Test で想定の規模と 10MB の両方を測る。

## 2. 投入・戻し・生成（NFR1.6・NFR1.8）

- 投入は、本文を1回だけバイト列で受け取り、U2 の読み込み・照合・保存（MERGE）を順に行う。本文をバイト列から文字列へ何度も変えない。
- 生成は U3 に任せる（30 秒の内訳は U3 の NFR1.7）。

## 3. 軽い API（NFR1.10・NFR1.11）

- 今の状態・履歴は、本文を読まずに識別・出どころ・人・日時の列だけを問い合わせる（本文の列を取らない射影）。
- ダウンロードは保存したバイト列をそのまま返す（10MB でも 2 秒以内）。
- 適用は本文を読み直さず、プレビューの行を履歴へ移す（機能設計の BR4.x）。

## 4. 資源（NFR1.12・NFR1.13）

- 重い処理は同時に1つ（scalability-design.md）なので、10MB の DSL の一時的なメモリは同時に1つ分だけ。プレビューの読み込みの結果の保持も1つだけ。
- 10MB の DSL を処理するときのヒープの最大を Build and Test で測り、1g のコンテナで足りなければコンテナの上限の見直しを依頼者に諮る。
