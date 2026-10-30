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

- 値の出どころ: `sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded` は、メッセージを捨てた失敗の値 `ReferenceReadFailure`（`businessdata.domain`。`reliability-design.md` 5.2）から出す。`check`・`countState` の `FAILED` も同じ値を持ち、U2 が自分の診断の WARN に出す（U2 から受けた継ぎ目）。`check`・`countState` の中では WARN を出さない（BR9.6。出すのは写す側の U2）。
- 出さないもの: スキーマ・テーブル・カラムの名前、値・ラベル・セル、接続先、例外のメッセージ（既存の `RowQueryFailure` と `ReferenceReadFailure` はメッセージを持たない）。
- 想定外（`UNEXPECTED`、500）は既存の `@RestControllerAdvice` が ERROR で出す（既存のまま）。MySQL の 1038 はこの単位の設計では起きない（`performance-design.md` 2節）が、起きたときは `exceptionClass` と `sqlState=HY001`・`vendorCode=1038` で見分けられる。
- 読み取りは監査に残さない（BR1.7）。
- `budgetExceeded` を足すため、全体の上限と文1回の上限のどちらで切れたかをログで見分けられる。応答の code は同じ（新しい code を作らない）。

### 2.1 1つの失敗のログの行の数（レビュー R-03）

コードで確かめた今の 503 の経路は次のとおり。

- 既存の業務データの読み取りでは、`BusinessDataReadService` が `failure`・`unavailable` の中で診断の WARN を1行（キー `purpose`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`、ロガーは `BusinessDataReadService`）出し、web の層が結果の型を `BusinessException` に写す。`GlobalExceptionHandler.handleBusiness` は `log(ex, type, true)` で、すべての `BusinessException` に変換の WARN を1行（キー `code`・`status`・`exceptionType` だけ。値・メッセージ・スタックトレースなし、ロガーは `GlobalExceptionHandler`）出す。つまり今の 503 でも、1つの失敗に診断の行と変換の行の2種類が1行ずつ出る。
- 参照の読み取りも同じ形にする。診断の WARN は `ReferenceLookup` の失敗を code に写す1か所だけで1行出し（`RowSqlExecutor`・`RowSqlFailureClassifier`・`ReferenceReadDeadline`・`check`・`countState` は出さない）、web の層は出さない。`GlobalExceptionHandler` は全機能で共有するため変えず、変換の WARN はそのまま出る。
- そのため「同じ失敗のログは1回」を、**`ReferenceLookup` のロガーの診断の WARN が、1つの失敗についてちょうど1行**と読み、`GlobalExceptionHandler` のロガーの変換の WARN は既存の別の種類の行として数える。U2 の診断の WARN の読み方（U2 の `observability-design.md` 2.2、依頼者が受け入れた読み方）と同じ。Code Style の「変換する境界で1回だけ」は、例外を捕まえて値や原因を出すログ（ERROR・スタックトレース付き）を重ねないことと読み、この2行はどちらも値・メッセージを持たない。
- 確かめ: 時間切れ・接続の失敗・型の食い違い・全体の上限の各経路で、要求1件の後の `ReferenceLookup` のロガーの WARN がちょうど1行（0・2 行以上で失敗）、`GlobalExceptionHandler` のロガーの WARN が1行で値・メッセージの目印が無いことを数える（`ReferenceSecretLeakIT` の中。既存の `BusinessDataWriteSecretLeakIT` と同じくロガーで絞って数える）。

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
| Code Style「例外のログは変換する境界で1回だけ」 | 診断の WARN（`ReferenceLookup`）と既存の変換の WARN（`GlobalExceptionHandler`）の2種類が1行ずつ出る | 2.1節。診断の行がちょうど1行と読む（U2 と同じ読み方、レビュー R-03） |

## 後の段に回すこと

- **Code Generation**: 2節のログの1行と、そのテスト。
- **Observability Setup**: 区画の説明の追記、警報の確かめ、3,000 ms のバケットの要否を依頼者に諮る。
