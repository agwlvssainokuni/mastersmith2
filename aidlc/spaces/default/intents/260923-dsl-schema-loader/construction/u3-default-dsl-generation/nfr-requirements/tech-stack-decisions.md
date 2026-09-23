# Tech Stack Decisions — U3 既定の DSL の生成（u3-default-dsl-generation）

U3 の技術の選定と数値、そして DSL の大きさの上限を 10MB に上げる決定（Q1: B）の差の一覧を示す。答えは `nfr-requirements-questions.md`（Q1・Q2）。

## 1. 選定

| 対象 | 選定 | ライセンス | 理由 |
|---|---|---|---|
| YAML の書き出し | SnakeYAML 2.6（U2 と同じ部品。新しい依存は足さない） | Apache 2.0 | 引用・改行の扱いを部品に任せ、構造を壊さない（NFR4.7）。書き出しの形（ブロックの形・字下げ・項目の並び）を固定し、同じ写しからは同じ本文にする（BR5.1） |
| 型の分類の対応 | 表の形のコード（新しい部品は使わない） | — | 機能設計の BR2.2〜BR2.5 |

## 2. 数値

NFR1 の枝番は単位の間で通しで振る（U1 は NFR1.1〜1.3、U2 は 1.4〜1.5、U3 は 1.6〜1.7、U4 は 1.8〜1.17、U5 は 1.18〜1.21）。NFR9 は U5 が 9.1、U3 が 9.2。

| ID | 項目 | 値 | 理由 |
|---|---|---|---|
| NFR1.6 | スキーマの読み込み（生成の全体）の時間 | 想定の規模（100 テーブル × 100 カラム）で 30 秒以内（要件の NFR1） | Build and Test で3種類の DB それぞれ測る（ストーリーの AC1.2.3） |
| NFR1.7 | 30 秒の内訳 | U1 の読み取り 15 秒・U3 の組み立てと書き出し 5 秒・U2 の検証 3 秒・保存と応答 2 秒・余裕 5 秒 | Q2: A。内訳ごとに測り、どこが遅いかを分かるようにする。U2 の 3 秒は想定の規模の既定の DSL（約 5.3MB）の検証で、U2 の「10MB で 10 秒」（NFR1.5）は上限の大きさの投入の目標（別のもの）。U1 の 15 秒は正常なときの目標で、U1 の待ち時間の上限（接続 5 秒・問い合わせ 20 秒）は応答しないときの打ち切り（別のもの） |
| NFR9.2 | 表示名の ja・en | ja はコメントがあればコメント、無ければ物理名。en は物理名（機能設計の BR1.2）。コメントの扱いは NFR4.8 | 要件の NFR9。画面の文言の日英は U5（NFR9.1） |
| NFR2.5 | 生成した DSL の大きさ | 10MB の内（100 × 100 でコメントが無いとき約 5.3MB の試算） | Q1: B |

## 3. DSL の大きさの上限を 10MB に上げる決定と、承認済みの文書との差

### 3.1 きっかけ

想定の規模（100 テーブル × 100 カラム）で、U2 の機能設計の書式の例のとおりにすべての項目を書き出すと、コメントの無い状態で約 5.3MB（1つのカラムが約 550 バイト）になり、要件の NFR2 の上限 5MB を超える。このままでは、生成した DSL が U2 の検証を通らずプレビューに置けず（BR5.2）、ダウンロードした既定の DSL を投入し直せない（ストーリーの AC3.4.2）。

### 3.2 決定（2026-09-23、依頼者、Q1: B）

投入できる DSL の大きさの上限を **10MB（10,485,760 バイト）** に上げる。書式はそのまま（既定の値の項目も書き出す）。あわせて、U2 の別名を展開した後の節の数の上限を 1,000,000 にする（U2 の NFR 要件）。

### 3.3 承認済みの文書との差

承認済みの文書は書き換えない（`aidlc/spaces/default/memory/project.md` の Way of Working）。実装・README・画面の案内は、コード生成で 10MB にそろえる。

| 文書 | 5MB と書いてある箇所 | 実装での扱い |
|---|---|---|
| `inception/requirements-analysis/requirements.md` | FR4.4（上限 5MB）、NFR2（5MB） | 10MB とする |
| `inception/user-stories/stories.md` | AC2.1.5（5MB を超えるファイル）、AC2.3.1（ちょうど 5MB）ほか | 10MB の境界で確かめる（ちょうど 10MB は受け付け、超えたら拒否） |
| `inception/refined-mockups/`（mockups.md・interaction-spec.md・accessibility-checklist.md） | 入力の欄の案内「YAML、5MB まで」、送る前の案内 | 画面の文言は「10MB まで」 |
| `inception/contract-design/contract-summary.md` | C4 の読み込みの上限、C6 の投入の本文「5MB まで」と `DSL_TOO_LARGE` の説明 | 10MB |
| `construction/u2-dsl-definition/functional-design/`（rules.md の BR1.1、functional-spec.md） | 5MB を超えたら SIZE_LIMIT | 10MB（U2 の NFR 要件の NFR2.1） |
| `construction/u4-dsl-management/functional-design/`（rules.md の BR1.2・BR1.6、functional-spec.md） | 投入の API の本文の上限 5MB | 10MB。既存の本文の上限（1MB）はほかの API でそのまま |
| `construction/u5-dsl-admin-ui/functional-design/`（functional-spec.md・frontend-components.md の BR2.3、`DSL_TOO_LARGE` の文言） | 送る前の案内 5MB（5 × 1024 × 1024 バイト） | 10MB（10 × 1024 × 1024 バイト） |

### 3.4 これで増えるもの

- 1回の投入で読むメモリ（本文・節の木・検証用の形）。10MB の DSL の読み込みと検証は 10 秒以内（U2 の NFR1.5）で、メモリとあわせて Build and Test で測る。
- 内部DB に保存する量。プレビュー1件と履歴 20 件がすべて 10MB なら、最大約 210MB。Build and Test で H2 のファイルの大きさと、コンテナのメモリの上限の内に収まるかを確かめる。
