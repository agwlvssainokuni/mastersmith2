# 監視の設計 — U1 u1-reference-read

参照先の読み取りの失敗を、値を漏らさずにログ・指標・警報で見分ける作りを決める。手元の監視（grafana/otel-lgtm）は見たいときだけ起動し、SLO は新しく置かない（`project.md` の学び）。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/observability-requirements.md`（NFR1.15・NFR2.10・NFR3.8）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md`（BR1.7・BR8.7・BR10.2・BR10.3）
- 試しの結果 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/trial-results.md`
- 設定（読み取りだけ）: `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-biz-target-db`）、`docker/monitoring/dashboards/mastersmith-overview.json`、`backend/src/main/resources/application.yaml`（`http.server.requests` のバケットの境界 100・250・500・1000・2000・5000 ms）

## 1. 何を見るか

| 見たいこと | 見る所 | 新しく足すもの |
|---|---|---|
| 参照の読み取りの失敗（時間切れ・借りられない） | 既存の警報 `ms-biz-target-db`（`/api/tables/` の 503 が 5 分に1件以上） | なし。新しい道 `/api/tables/reference/rows` と定義の道 `/api/tables/definition` の 503 が同じ式に入る |
| 失敗の原因の種類 | アプリのログの WARN の1行（2節） | ログの1行の形 |
| 応答時間 | ダッシュボードの業務データの区画（`uri=~"/api/tables/.*"` の要求の数と p95） | 区画の説明に新しい道を足すだけ |
| 対象DB のプールの待ち | 既存のプールの区画 | なし |

警報は足さない（NFR2.10）。道は固定で値は本文で運ぶため、`uri` のラベルは1つ増えるだけで、参照先ごとに系列が増えない。参照先ごとの遅さは区画では見分けない（受け入れた制約）。

## 2. ログの設計（NFR1.15）

- 対象DB の失敗を code に写す所（`ReferenceLookup` の失敗の扱い）で WARN を1行だけ、スタックトレースなしで出す。キーと値（SLF4J のキー・値 API）で次だけを出す。

| キー | 値 |
|---|---|
| `purpose` | `REFERENCE` |
| `reason` | `TIMEOUT`・`UNAVAILABLE`・`MISMATCH`（全体の上限の超過は `TIMEOUT` に `budgetExceeded=true` を足す） |
| `sqlState` | 例外の SQLState（無ければ出さない。試しで MySQL の時間切れは SQLState が無かった） |
| `vendorCode` | 業者の番号 |
| `exceptionClass` | 例外のクラスの名前 |

- 出さないもの: スキーマ・テーブル・カラムの名前、値・ラベル・セル、接続先、例外のメッセージ（既存の `RowQueryFailure` はメッセージを持たない）。
- 想定外（`UNEXPECTED`、500）は既存の `@RestControllerAdvice` が ERROR で出す（既存のまま）。MySQL の 1038 はこの単位の設計では起きない（`performance-design.md` 2節）が、起きたときは `exceptionClass` と `sqlState=HY001`・`vendorCode=1038` で見分けられる。
- 読み取りは監査に残さない（BR1.7）。
- `budgetExceeded` を足すため、全体の上限と文1回の上限のどちらで切れたかをログで見分けられる。応答の code は同じ（新しい code を作らない）。

## 3. トレース

- 既存の Micrometer Tracing の範囲のまま、新しいスパンは足さない。道の属性は固定の道だけで値を持たない（BR10.3）。外部へ送る設定を有効にした結合テストで、属性に値が無いことを確かめる（`security-design.md` 6節の (d)）。

## 4. 指標と目盛り

- `http.server.requests` の新しい道の系列は既存の式に入る。部分一致の線 3,000 ms はバケットの境界（2,000 と 5,000 の間）に無いため、サーバー側の p95 は参考とし、合否は k6 の `iteration_duration` で決める。3,000 ms のバケットを足すかは Observability Setup で依頼者に諮る（NFR 設計の質問の「決まっていること」）。
- 試しの値（`trial-results.md` 2.2）では、平常の読み取りは 1 本の接続で 0.5〜200 ms で、既存のバケット 250・500 ms の境界で見分けられる。

## 5. 確かめ方

| 確かめ | 段 |
|---|---|
| ログの JSON のキーの名前と、値・メッセージの目印が出ないこと（`ReferenceSecretLeakIT` の中）、監査の行が増えないこと | Code Generation |
| 書く前に使い捨てのアプリで `uri` のラベルの値と `+Inf` 以外のバケットを確かめ、書いた後にすべての式を流す。要求は指標の送信の周期（1 分）を複数またいで、待ちを入れた少ない数で送る | Observability Setup |
| 使い捨ての環境で、新しい道と定義の道の 503 で `ms-biz-target-db` が firing になる。`docker pause` では借りる待ちの `UNAVAILABLE` になり、`TIMEOUT` は問い合わせの最中に止めないと起きない（`project.md` の学び） | Observability Setup |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| 機能設計 BR8.7（WARN のキー） | 全体の上限の超過を見分ける `budgetExceeded` を足した | ログの項目を足すだけで、値は出さない。応答は変えない |

## 後の段に回すこと

- **Code Generation**: 2節のログの1行と、そのテスト。
- **Observability Setup**: 区画の説明の追記、警報の確かめ、3,000 ms のバケットの要否を依頼者に諮る。
