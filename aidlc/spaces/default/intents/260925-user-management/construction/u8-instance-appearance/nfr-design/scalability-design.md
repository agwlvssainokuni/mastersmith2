# Scalability Design — U8 インスタンスの見た目の設定（u8-instance-appearance）

U8 の同時の要求と規模の設計です。要件定義に拡張性の NFR が無いため、NFR 要件の段と同じく NFR6（応答時間）の枝番で扱う。答えは `nfr-design-questions.md`（Consolidated Summary Confirmation: Looks correct）。

出典の略号: NFR はこの単位の NFR 要件 `construction/u8-instance-appearance/nfr-requirements/` の枝番（枝番はこの単位の中で振る）、BR は `construction/u8-instance-appearance/functional-design/rules.md`、「要点 n」は `nfr-design-questions.md` の「NFR 設計の要点（案）」の番号。

## 1. 拡張の方式

| 観点 | 設計 | 要件 |
|---|---|---|
| 状態 | 起動時に作る変わらない `record` 1つだけ。要求の処理は読むだけで書き換えない | NFR6.3、BR1.6 |
| 待ち合わせ | ロック・同期・`volatile` の入れ替え・共有の仕組みを置かない。値は Bean の作成の中で決まり、Spring の文脈が作られた後に要求が来るため、公開の安全はフィールドを `final` にすることで足りる | NFR6.3 |
| インスタンスの数 | 内部DB が組み込みの H2 のため、アプリは1インスタンスで動く（要件の制約）。U8 の側で複数のインスタンスへの値の配り直し・共有は持たない | NFR6.4 |
| インスタンスを増やす場合 | 各インスタンスが同じ環境変数（`MASTERSMITH_APPEARANCE_BRAND_COLOR`・`MASTERSMITH_APPEARANCE_FONT_FAMILY`）を起動時に読めば同じ値を返す。U8 に変更は要らない | NFR6.4 |
| 設定の変更 | アプリの起動し直しで当たる。動いている間の切り替えは持たない | NFR6.4、BR1.6 |

## 2. 容量の見通しとしきい値

| 項目 | 見通し | しきい値・自動の拡張 |
|---|---|---|
| 保持するデータの量 | 2つの短い名前だけで、利用者や時間で増えない | 置かない |
| 要求の数 | 画面を開く回数に比例（U4 が画面を開くたびに1回呼ぶ）。利用者 最大 50 名・同時 10 件の想定 | 置かない。応答時間は `performance-design.md` の NFR6.1 で測る |
| 先に詰まる箇所 | U8 の中には無い。詰まるとすればアプリ全体の要求のスレッドで、ほかの API と共通。内部DB の接続プール（既定の上限 30）は使わない | 自動の拡張の仕組みは持たない（配備先が開発者の PC 上のコンテナのため） |

## 3. 確かめ方

- 待ち合わせを置かないことは、コード生成のレビュー（保持する値が `final` の変わらない `record` で、要求の処理に同期が無い）で確かめる（NFR6.3）。
- 同時 10 件で目標を守れることは、performance-validation の段の k6（`performance-design.md` の5節）で確かめる（NFR6.1・NFR6.3）。
- 複数のインスタンスの仕組みを持たないことは記録だけで、配備先が決まったときに見直す（NFR6.4）。

## 4. 上流との差

承認済みの文書と食い違う設計は無い。
