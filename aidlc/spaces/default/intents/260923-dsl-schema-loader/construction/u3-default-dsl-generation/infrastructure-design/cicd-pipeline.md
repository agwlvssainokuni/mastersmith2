# CI/CD Pipeline — U3 既定の DSL の生成（u3-default-dsl-generation）

U3 の検査の流れと、時間の測り方を示す。U3 は新しい依存も外部への接続も足さない library で、配備先の基盤は増やさない。要点の確認は `infrastructure-design-questions.md`。

## 1. 依存

足さない。YAML の書き出しは、U2 で直接の依存にした SnakeYAML 2.6 を使う。

## 2. 検査の流れ（既存の `./gradlew verify`）

| 段 | U3 のテスト |
|---|---|
| `test`（単体） | 型の名前から分類への対応（3種類の DB）、分類からフォーム部品・検索・一覧・詳細の初期値、書き出しの形の固定、同じ写しから同じバイト列（性質ベース、jqwik）、任意の文字列のコメント・名前で構造が壊れず U2 の検証を通る（性質ベース、jqwik）、制御文字の除去、10MB を超えたら失敗し部分的な DSL を返さない |
| `integrationTest`（結合） | U1 の Testcontainers の3種類の DB に見本のスキーマを作り、読み取り → 生成 → U2 の検証を通ることを確かめる（U1 の結合テストの DB の仕組みを使う） |

性質ベースのテストは、失敗したときの乱数の種をログに残して再現できるようにする（team.md の Testing Posture）。

## 3. 時間の測定（NFR1.6・NFR1.7）

- Build and Test で、U1 の compose の profile の見本の DB（100 テーブル × 100 カラムを作る SQL）を3種類それぞれ起動し、画面または API からスキーマの読み込みを行って測る。
- 全体（30 秒以内）は U4 の指標（`operation=generate`）、内訳（U1 の読み取り 15 秒・組み立てと書き出し 5 秒・U2 の検証 3 秒・保存と応答 2 秒）は U3 の DEBUG のログで見る。
- 測定は配備した環境とは別の使い捨ての環境で行う（`aidlc/spaces/default/memory/project.md` の Testing Posture）。

## 4. 配備・戻し・秘密情報

対象外。U3 はアプリの中の部品で、U3 だけの配備・戻しは無い（WAR 全体の既存の手順に従う）。秘密情報は扱わない（対象DB の接続は U1 が持ち、U3 は写しだけを受け取る）。
