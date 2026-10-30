# 規模の設計 — U2 u2-reference-write-check

保存前の参照の段（⑩）が使う接続・読む行・同時の数の作りを決める。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/scalability-requirements.md`（NFR2.26〜NFR2.28・NFR3.21〜NFR3.23）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md`（BR1.2・BR4.1・BR4.2・BR4.7）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C5）、ADR-003（`inception/domain-design/decisions.md`）
- この段の試しの結果 `trial-results.md`（2.3）
- U1 の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/scalability-design.md`（読み取りのプールの共有）

## 1. 接続の使い方（NFR3.21・NFR3.22）

```text
⑨ を通った
  読むものが無い → 読み取りの接続を借りずに ⑪
  読み取りの接続を1回借りる（REFERENCE の目的）
    [更新] detail → [作成] countState（参照先ごと1回）→ check（参照ごとに順に）
  読み取りの接続を返す
  拒否・失敗 → 書き込みの接続を借りずに応答
  進む → 書き込みの接続を1回借りる → ⑪（既存）→ 返す
  監査の出来事（内部DB、別の短いトランザクション）
```

- 1要求で借りる対象DB の読み取りの接続は 0 か 1 回、書き込みの接続は 0 か 1 回で、2本を同時に持たない（BR4.1・ADR-003）。読み取りの接続は `TargetReadAccess.withRead(REFERENCE, …)` の中だけで使い、その処理から戻ってから `withWrite` を呼ぶ。
- 内部DB の接続は、①〜④ の権限の写しの読み取りで返し終え、⑩・⑪ の間は持たない。監査の書き込みは対象DB の接続を返した後（BR4.2）。
- 確かめの段は書き込みの接続を持つ時間を増やさない。増えるのは読み取りの接続を持つ時間だけで、試しでは参照 5 個で約 3〜4 ms（`trial-results.md` 2.2）。

## 2. 読む行の上限（NFR2.26・NFR2.27）

- U1 の口の上限（文の `LIMIT 201` と `setMaxRows(201)`）をそのまま使い、U2 は上限を緩めない。更新前の行は既存の `detail`（最大 2 行）。
- 1要求の最大の行の数: 更新前の行 2 ＋ 必須の参照項目の参照先の数 × 201 ＋ 確かめる参照の数 × 201。順に呼ぶため、同時にメモリに持つのは1文分（最大 201 行、約 4 MB）だけ（`performance-design.md` 4節）。
- 1つの表に置ける参照のカラムの数には上限を置かない。数が多いときの時間は ⑩ の全体の上限 10 秒で抑える（`reliability-design.md` 1節）。

## 3. 同時の数（NFR3.23）

| 資源 | 上限 | 共有する相手 | 詰まったとき |
|---|---|---|---|
| 対象DB の読み取りのプール | 既定 10（使い捨ての負荷の環境は 5） | 一覧・詳細・U1 の定義の取得とピッカー・DSL の生成と照合 | 借りる待ち 3 秒を超えると 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（保存しない） |
| 対象DB の書き込みのプール | 5 | 作成・更新・削除 | 既存のまま |

- 1要求が同時に持つ対象DB の接続は1本だけのため、詰まりは「同時の要求の数 > それぞれのプールの上限」のときだけ起きる。試しでは、10 本の同時でプール 5・5 を取り合っても 3種類とも失敗 0、p95 は最大 25.70 ms（`trial-results.md` 2.3）。
- 読み取りのプールの上限を下げた k6 の場面は置かない。理由: 1要求1本・2本を同時に持たない形のため、プールの見積もりは「同時の要求の数」だけで決まり、接続の数の誤りは結合テスト（4節）で決定的に見つかる（`project.md` の学び）。書き込みのプールの上限に届く確かめは既存の `bizWritePoolLimit` のまま（NFR2.23）。Performance Validation の新しい場面で、読み取りと書き込みのプールの待ちの時間切れの累計が 0 であることを並べて記録する。

## 4. 確かめのテスト（`team.md` の (j)）

既存の `BusinessDataConnectionUsageIT` と同じ形（`CountingDataSourceConfig` で内部DB・読み取り・書き込みの3つを包む）で、次を決定的に数える。

| 場合 | 読み取りを借りた回数 | 書き込みを借りた回数 | ほか |
|---|---|---|---|
| 参照の無い作成・更新・削除 | 0 | 1 | 今と同じ数 |
| 参照 1・3・5 個の作成 | 1 | 1 | 2本を同時に持った瞬間が無い（`peerInUseAtBorrow` が 0） |
| 値の変わらない更新・値の変わった更新 | 1 | 1 | 同上 |
| 参照の拒否・`TIMEOUT`・`UNAVAILABLE` | 1（借りられない場合は失敗の1回） | 0 | 行が増えない・変わらない |
| 応答しない対象DB（`ServerSocket`）で待たせる間 | — | — | 内部DB の使用中の接続が 0（AC3.5.2） |
| 同じ参照先を指す必須の参照項目 2 つ | 1 | 1 | `countState` の文が1回だけ（文の記録で数える） |

## 上流との差

なし（NFR 要件の形のまま。NFR3.23 の「k6 の場面を置かない」を、3節の理由で確定した）。

## 後の段に回すこと

- **Code Generation**: 4節の結合テスト。
- **Performance Validation**: プールの待ちの時間切れの累計の記録（使い捨てのアプリにだけ公開した metrics）。
