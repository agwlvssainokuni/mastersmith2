# Scalability Design — U4 DSL の管理（u4-dsl-management）

U4 の拡張性の要件（`construction/u4-dsl-management/nfr-requirements/scalability-requirements.md`）を満たす作りの方針。

## 1. 重い処理の同時の数（NFR1.13・NFR1.14・NFR1.15、Q2: A）

```text
DslHeavyOperationGate (HandlerInterceptor on heavy DSL routes):
  preHandle:        if !semaphore.tryAcquire() -> 503 DSL_BUSY (body not read, no audit, no state change)
  afterCompletion:  semaphore.release()   // always, also on error
```

- 重い道: `POST /preview/generate`・`POST /preview`・`POST /history/{revisionId}/restore`・`GET /preview`。
- 許可はアプリ全体で1つ（`Semaphore(1)`、待たない）。画面入出力の層の手前（ハンドラーの引数を作る＝本文を読む前）で取るため、断る要求の本文を読まない。
- 許可は応答を返し終えたとき（例外のときも）に必ず返す。返し忘れを防ぐため、取れたかどうかを要求の属性に持ち、取れたときだけ返す。
- 本文の上限は、この仕組みより前（認証・認可の後）の既存の `RequestSizeLimitFilter`（道ごとの上限）で確かめる（security-design.md 1節）。`Content-Length` がある送り方では、Filter も本文を読まないため、断る要求の本文は読まない。chunked の送り方では Filter が先に読む（security-design.md 5節の残る危険、読むのはログインした管理者の要求だけ）。
- 軽い道（今の状態・履歴・ダウンロード・破棄・適用）は許可を取らない。適用の同時の実行は機能設計の BR4.3（削除の件数）で1件だけ成功する。

## 2. 規模の前提

- アプリは1つ（ADR-004）。許可はアプリの中だけで効く。アプリを複数にするときは、内部DB の変更と合わせて見直す。
- 内部DB の保存は最大約 210MB（プレビュー1件と履歴 20 件が 10MB のとき）。上限の件数は `mastersmith.dsl.history-limit`（機能設計の BR4.4）。
