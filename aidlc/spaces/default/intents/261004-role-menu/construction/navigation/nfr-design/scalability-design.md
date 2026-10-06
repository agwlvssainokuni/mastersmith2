# 規模の設計 — U5 navigation

## 出典

- この単位の承認済みの NFR 要件 `construction/navigation/nfr-requirements/scalability-requirements.md`（NFR2.1・NFR2.6）と、その読み直しの記録の R-07
- この単位の承認済みの機能設計 `construction/navigation/functional-design/rules.md`（BR1.4・BR6.2）
- この段の答え `nfr-design-questions.md`（Q2 A と、まとめの確認）
- 統合の点: group・role の NFR 設計の読み直しの R-01（`pending` の瞬間値）・R-02（時間に頼るテスト）
- コード: `backend/src/main/resources/application.yaml`（`open-in-view: false`、接続プールの上限 30・接続の待ち 5000 ms）

## 1. 規模のデータ（NFR2.1）

- 悪い側の DSL は、既存の `perf/make-large-dsl.mjs` に倣った生成の手順（Node の標準の部品だけ）で作る。menus は 1,000 項目で深さ 5 段まで使い、100 テーブルを平均 10 回ずつ別々のまとまりから指す。目安の DSL（menus 100 項目・深さ 1）は既定の DSL の生成で作る。
- ロール・グループ・割り当て・1万カラムの明示の設定は、使い捨ての環境の内部DB に SQL で直接入れる。直接入れた利用者は、同時のログインの前に1人ずつログインさせてロックの状態の行を作る（`project.md` の学び）。
- Performance Validation の結果に、使った規模（項目の数・深さ・明示の行の数・グループの数・利用者の数）を記録する。

## 2. 1要求で同時に持つ接続（NFR2.6、Q2 A）

- 前提: `open-in-view: false` のため、要求の間ずっと接続を持つことは無い（読み直しの R-07）。navigation の業務処理はトランザクションを開かず、接続は role の解決の口の読み取りの間だけ使う。
- 数え方: テストだけの `DataSource` の包み `ConnectionCountingDataSource`（`navigation/testsupport`）を、テストだけの `BeanPostProcessor` で本物の `DataSource` にかぶせる。借りるたびにそのスレッドの借りている数を1増やし、返すと1減らし、スレッドごとの最大を記録する。止める必要が無く、時間と瞬間値に頼らない（group の R-01・R-02 の指摘を避ける）。

```java
// 説明用の断片（navigation/testsupport）
Connection getConnection() throws SQLException {
    int now = inUse.merge(threadName(), 1, Integer::sum);
    peak.merge(threadName(), now, Math::max);
    return wrapOnClose(target.getConnection(), () -> inUse.merge(threadName(), -1, Integer::sum));
}
```

- `NavigationConnectionUsageIT`: 2本の API（メニュー、置き場の 200 と 403）をそれぞれ1回送り、その要求を処理したスレッドの最大が 1 であることを確かめる。最大が 0 のとき（解決の口が DB を読まない場合）は、テストの用意の誤りとして落とす。
- 測る要求の分け方（承認の場の直し R-01）:
  - 数えは、測る要求ごとに空にする。包みに `reset()` を持たせ、ログイン（`setup` の中のトークンの取得。監査の記録で2本目を借りうる）をすべて終えた後、測る要求を送る直前に呼ぶ。`reset()` は、借りている数と最大の両方を、すべてのスレッドについて消す。
  - どのスレッドの数かは、要求ごとに決めた印で残す。テストだけの部品（`navigation/testsupport` のサーブレットのフィルター）が、要求の頭 `X-Test-Measure-Id`（テストが要求ごとに作る値）を受けたときだけ、処理したスレッドの名前をその印と組で記録する。包みは、スレッドの名前ごとの最大に加えて、「印がある要求の処理中に借りた数」の最大を印ごとに持つ。印の要求の始まりで、そのスレッドの借りている数が 0 であることも確かめる。
  - 確かめるのは、印ごとの最大が 1 であること。同じ Tomcat のスレッドが前にログインで2本を使っていても、`reset()` と印ごとの数えで最大は残らない。
  - 測る要求は1つずつ順に送り、ほかの要求と重ねない。使い捨ての内部DB の健全性の確かめなどほかの要求が同じ時に借りた接続は、印が無いため数えない。
- 上限を下げた k6 の場面は置かない（承認済み）。

## 3. 補足

- 内部DB は組み込みの H2 で、アプリは1つ（単一インスタンス）。横に増やす設計は無い。
- 写しは要求をまたいで持たないため、利用者と要求の数に比例するメモリは増えない。

## 承認の場の決定と直し

- 決定: 依頼者は承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」（各単位の読み直しの Major と、単位の間でそろえる3点）。navigation で直すのは R-01・R-02 の2件。
- R-01（Major）: `ConnectionCountingDataSource` の数えを測る要求の前に空にする手順と、どのスレッドの数かを残す手段が無く、ログインで監査の2本を使った同じ Tomcat のスレッドが後でメニューを処理すると、最大の 2 が残って誤って落ちうる。直し: 2節に、測る直前の `reset()`、要求の頭 `X-Test-Measure-Id` とテストだけのフィルターで印ごとに数える形、印の要求の始まりで借りている数が 0 であることの確かめ、測る要求を1つずつ送ることを足した。テストの部品は `logical-components.md` の T1 に足した。
