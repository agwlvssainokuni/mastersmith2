# NFR Requirements — U3 既定の DSL の生成（u3-default-dsl-generation）— 質問

この段では、U3 の非機能の要件を決めます。U3 の受け持ちは、要件の NFR1 の一部（想定の規模で既定の DSL の生成を 30 秒以内）です（`inception/units-generation/unit-of-work.md`）。

すでに決まっていること:
- 既定の DSL は、対象DB の写し（U1）から機械的に作り、U2 の検証を通してからプレビューに置く（`construction/u3-default-dsl-generation/functional-design/rules.md` の BR5.2）。同じ写しからは同じ本文を作る（BR5.1）。接続先を入れない（BR5.3）。
- U1 の読み取りの待ち時間は、接続 5 秒・問い合わせ 1回 20 秒（U1 の NFR 要件）。U2 の読み込みと検証は、想定の規模で 3 秒以内（U2 の NFR 要件、試しでは 0.2 秒）。
- 投入できる DSL の大きさの上限は 5MB（NFR2）。U2 の検証は、生成した DSL にも同じ上限を当てる。

## 見つかった食い違い

想定の規模（100 テーブル × 100 カラム）で、機能設計の書式の例（`construction/u2-dsl-definition/functional-design/functional-design-questions.md` の「DSL の形（案）」）のとおりにすべての項目を書き出すと、コメントの無い状態で約 **5.3MB** になり、5MB の上限を超えます（1つのカラムが約 550 バイト。試算）。このままでは次のことが起きます。

- 生成した DSL が U2 の検証（5MB の上限）を通らず、プレビューに置けない（BR5.2）
- ダウンロードした既定の DSL を、そのまま投入し直せない（ストーリーの AC3.4.2）

既定の値と同じ項目（検索・一覧・詳細の初期値、null の項目）を書き出さない形にすると、約 **3.3MB** に収まります（試算）。

---

## Q1. 生成した DSL の大きさと 5MB の上限の食い違いの直し方

A. 生成する DSL では、既定の値と同じ項目を書き出さない。DSL の書式（JSON Schema）に既定の値を持たせ、省いた項目は既定の値として読む（U2 の書式の決まりが変わる。承認済みの U2 の機能設計は書き換えず、差を U2・U3 の NFR 要件とコード生成で扱う）。約 3.3MB で上限に収まる
B. 上限を 10MB に上げる（NFR2 の変更。U2・U4 の上限、投入の API、画面の案内の値が変わる。書式はそのまま）
C. A と B の両方（余裕は大きいが、変更の範囲も両方）
X. Other (please specify)

[Answer]: B

## Q2. 30 秒の内訳（生成の全体）

A. U1 の読み取り 15 秒・U3 の組み立てと書き出し 5 秒・U2 の検証 3 秒・保存と応答 2 秒（残り 5 秒は余裕）。それぞれ Build and Test で測る
B. 内訳は決めず、全体の 30 秒だけを測る
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- **DSL の大きさの上限**（Q1: B）: 5MB から **10MB**（10,485,760 バイト）に上げる。生成した既定の DSL（100 × 100 で約 5.3MB、コメントがあると増える）も、ダウンロードして投入し直せる
  - これは承認済みの要件 NFR2（5MB）の変更になる。承認済みの文書（要件・ストーリー・画面イメージ・契約 C6・U2・U4・U5 の機能設計）は書き換えず、差の一覧を U3 の tech-stack-decisions.md に記録し、コード生成で実装・README・画面の案内を 10MB にそろえる（`aidlc/spaces/default/memory/project.md` の Way of Working）
  - この段の U2 の NFR 要件（まだ承認前）は 10MB に直す。あわせて、別名を展開した後の節の数の上限を 500,000 から **1,000,000** に上げる（10MB の DSL を想定の書き方で読める大きさ）。10MB の DSL の読み込みと検証も 10 秒以内（U2 の NFR1.5）
  - 10MB の DSL を読むときのメモリ（本文・節の木・検証用の形）と、内部DB に保存する量（プレビュー1件と履歴 20 件で最大約 210MB）は、Build and Test で測る
- **30 秒の内訳**（Q2: A）: U1 の読み取り 15 秒・U3 の組み立てと書き出し 5 秒・U2 の検証 3 秒・保存と応答 2 秒、残り 5 秒は余裕。それぞれ Build and Test で測る
- **書き出し**: YAML の書き出しは SnakeYAML（U2 と同じ部品）で行い、文字列の連結で YAML を組み立てない（DB のコメントに YAML の記号や改行があっても壊れない）。項目の並びと書き方を固定する（BR5.1）。新しい依存は足さない
- **コメントの扱い**: DB のコメントは信頼できない文字列として扱い、YAML の部品で引用して書き出す。制御文字は取り除く。長さは切り詰めない（DB が許す長さのまま）
- **成果物**: U3 は library のため、security-requirements.md・tech-stack-decisions.md（上の値と差の一覧、NFR1・NFR2 の枝番）・traceability.json を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
