# Scalability Requirements — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の同時の要求と規模の要件です。要件定義に拡張性の NFR が無いため、同時の要求で応答時間の目標（NFR6）を守る仕組みとして、NFR6 の枝番に寄せます（前の Intent の DSL の管理の単位が、同時の数の制限を NFR1（性能）の枝番に置いたのと同じ考え方）。答えは `nfr-requirements-questions.md`（Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、BR はこの単位の `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」はこの段の `nfr-requirements-questions.md` の「NFR の要点（案）」の番号。枝番はこの単位の中で振る。

## 前提

- 内部DB が組み込みの H2 のため、アプリは1インスタンスだけで動く（要件の制約、ADR-010）。
- 負荷の想定は利用者 最大 50 名・同時 10 件の要求（`performance-requirements.md` の前提）。画面を開くたびに1回呼ばれる（U4 の W3）ため、要求の数は画面を開く回数と同じ。
- U8 が保持する値は、起動時に決まってアプリが止まるまで変わらない2つの名前（BR1.6）。

## 1. 要件

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR6.3 | 保持する値は起動時に作って以後変えない値（変わらない `record` など）とし、要求の処理に待ち合わせ（ロック・同期）を置かない。同時の要求どうしが待ち合わない | コードの確かめ（コード生成のレビュー）と、`performance-requirements.md` の NFR6.1 の負荷の試験（同時 10 件） | BR1.6、要点 9 |
| NFR6.4 | 複数のインスタンスへの値の配り直し・値の共有の仕組みは持たない。設定の変更はアプリの起動し直しで当たる。将来インスタンスを増やす場合も、各インスタンスが同じ設定（環境変数）を起動時に読めば同じ値を返す | 確かめるテストは無い（持たないことの記録）。配備先が決まったときに見直す | BR1.6、要件の制約、要点 9 |

## 2. 規模の見通し

| 項目 | 見通し |
|---|---|
| 保持するデータの量 | 2つの短い名前だけで、利用者や時間で増えない |
| 要求の数 | 画面を開く回数に比例する。1要求はメモリの値を返すだけで、内部DB の接続プール（既定の上限 30）を使わない（`reliability-requirements.md` の NFR5.1） |
| 先に詰まる箇所 | U8 の中には無い。詰まるとすればアプリ全体の要求のスレッドで、ほかの API と共通 |

## 3. 上流との差

承認済みの文書と食い違う記述は無い。要件に拡張性の NFR が無いため、NFR6 の枝番に寄せたことだけを記録する（`traceability.json` の NFR6 の対象に含める）。
