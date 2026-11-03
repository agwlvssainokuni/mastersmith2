# 検証: Inception → Construction の網羅の確かめ（Intent 261102-timezone-ui-polish）

**判定: 合格（PASS）** — 未解決の指摘（GAP・ORPHAN・不正な target・欠けた上流の ID）は0件。

読んだもの（Inception で実行した段の `traceability.json`）:

- `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/user-stories/traceability.json`
- `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/traceability.json`
- `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/traceability.json`

Contract Design は、要件の網羅ではなく契約を持つ段のため `traceability.json` を作らず、この確かめに入らない。

## まとめの表

| 段 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN | 判定 |
|---|---|---|---|---|---|---|---|
| user-stories（要件 FR・NFR → ストーリー） | 57 | 53 | 4 | 0 | 0 | 0 | 合格 |
| domain-design（ストーリー → 部品・エンティティ） | 17 | 17 | 0 | 0 | 0 | 0 | 合格 |
| units-generation（ストーリー → 単位） | 17 | 17 | 0 | 0 | 0 | 0 | 合格 |

## Deferred の4件（段を名指しで後へ回したもの）

| 要件の ID | 回した先 | 理由 |
|---|---|---|
| FR8.4（管理の仮の画面で確かめていた検査を別の場所へ移し、移す前と後の件数を記録する） | build-and-test | 件数の記録は、Construction の最後の段が行う（ストーリー Q4: A） |
| FR11.4（許すアイコンの名前の一覧を画面とサーバーの2か所でそろえる） | code-generation | 実装でそろえ、そろいを確かめるテストを足す |
| NFR5.2（テストのタイムゾーンを固定し、別の時差は明示に指定する） | code-generation | テストの書き方の決まりで、コード生成の計画に入れる |
| NFR5.3（足す E2E は N6 の日時の代表の流れ1本まで） | code-generation | E2E の本数の決まりで、コード生成の計画（B4）に入れる |

Deferred は、後の段が受け持つ項目として段を名指ししており、未解決の指摘には数えない（段の定義のとおり）。

## 連鎖の確かめ

要件（FR・NFR）→ ストーリー（US1.1〜US5.3）→ 部品16個・エンティティ3つ → 単位6つ（U1〜U6）の連鎖が、段ごとの `traceability.json` で切れていない。

- ストーリー17件は、すべて部品・エンティティに結ばれ（domain-design）、すべて主の単位が1つ決まっている（units-generation の `unit-of-work-story-map.md` と一致）。
- 部品16個は、すべてちょうど1つの単位に割り当てられている（`unit-of-work.md`）。

## 残っている承認済みのリスク（参考。未解決の指摘ではない）

承認の場で受け入れた指摘のうち、後の段で扱うものを参考として挙げる。

- ストーリー: R-01〜R-04（サーバー側の夏時間の拒否・管理メニューのアイコン・NULL・重なる時刻の合格の条件）
- 画面イメージ: R-01〜R-08（読めない値の見せ方・業務データ以外の画面の時刻・`Card` のリンク・タイムゾーンの一覧の並べ方 など）
- ドメイン設計: R-01〜R-03（オフセットの無い値の扱い。Contract Design の Q2: A で決定済み・管理のカードの依存・上流の取り込みの期限）
- 単位: R-01・R-02（E2E の要否を単位ごとに確かめる・U5→U6 の辺は Should の部分だけ）
- 要件との差: ADR-002（夏時間で存在しない時刻の拒否は画面の側だけ）
