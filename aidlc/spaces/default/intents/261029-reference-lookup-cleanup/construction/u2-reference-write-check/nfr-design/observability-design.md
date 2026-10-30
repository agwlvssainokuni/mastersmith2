# 監視の設計 — U2 u2-reference-write-check

保存前の参照の段（⑩）の失敗と時間を、値を漏らさずにログ・指標・警報で見分ける作りを決める。手元の監視（grafana/otel-lgtm）は見たいときだけ起動し、SLO は新しく置かない（`project.md` の学び）。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/observability-requirements.md`（NFR1.32・NFR2.29・NFR3.28）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/`（`rules.md` の BR4.5・BR5・BR7.1、`functional-spec.md` の 9節「ログの目的の値」）
- U1 の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/observability-design.md`（キーの形と `budgetExceeded`）
- コード（読み取りだけ）: `businessdata/service/BusinessDataWriteService.java`（`warn`）、`common/error/web/GlobalExceptionHandler.java`（`log`）、`businessdata/web/BusinessDataWriteSecretLeakIT.java`（WARN の数え方）

## 1. 何を見るか

| 見たいこと | 見る所 | 新しく足すもの |
|---|---|---|
| ⑩ の失敗（時間切れ・借りられない） | 既存の警報 `ms-biz-target-db`（業務データの API の 503 が 5 分に1件以上） | なし。作成・更新の道 `/api/tables/rows` の 503 が同じ式に入る |
| 失敗の原因の種類 | `BusinessDataWriteService` のロガーの WARN の1行（2節） | ⑩ の目的の値 |
| 作成の時間 | ダッシュボードの業務データの区画（`uri=~"/api/tables/.*"`） | なし（参考。合否は k6） |
| 読み取り・書き込みのプールの待ち | 既存のプールの区画 | なし |
| 参照の拒否 | 監査の行（結果 `FAILURE`・理由 `BUSINESS_REFERENCE_NOT_FOUND`） | なし。監査は監視の代わりにしない |

警報と区画は足さない（NFR2.29・NFR3.28）。

## 2. ログの設計（NFR1.32）

### 2.1 診断の WARN の1行

- 出す所: ⑩ の対象DB の失敗（U1 の口の `FAILED`、`detail` の失敗、借りられない・読み取りの設定が無い）を code に写す1か所。`BusinessDataWriteService` の ⑩ の写しの中で、既存の `warn` と同じロガー・同じ書き方（SLF4J のキー・値 API、スタックトレースなし）。U1 の `check`・`countState` はログを出さない（U1 の BR9.6）。
- キー:

| キー | 値 |
|---|---|
| `purpose` | ⑩ の目的の値（候補 `REFERENCE`。既存の書き込みの `WRITE` と見分ける。名前はコード生成で決める） |
| `stage` | どの読み取りか（候補 `BORROW`・`PREVIOUS_ROW`・`COUNT_STATE`・`CHECK`。既存の書き込みの WARN にもあるキー） |
| `reason` | `TIMEOUT`・`UNAVAILABLE`・`MISMATCH` |
| `sqlState`・`vendorCode`・`exceptionClass` | `ReferenceReadFailure` の同じ名前の値（無ければ出さない） |
| `budgetExceeded` | `ReferenceReadFailure.budgetExceeded()` の値。全体の上限で流さずに止めたときだけ `true`（U1 と同じ） |

- 出さないもの: スキーマ・テーブル・カラムの名前、参照の値・行の値、接続先、例外のメッセージ。
- `reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded` は、U1 の `ReferenceCheckResult.Failed(ReferenceReadFailure failure)`・`ReferenceCountState.Failed(ReferenceReadFailure failure)` の `failure()`（`cherry.mastersmith.businessdata.domain.ReferenceReadFailure`。メッセージ・行の値を持たない）から取る。更新前の行の `detail` の失敗は、既存の `RowQueryFailure` を `ReferenceReadFailure.of` で写してから同じ形で出す。借りられない・読み取りの設定が無いときは例外の値が無いため、`reason=UNAVAILABLE` と `stage=BORROW` だけを出す。U1 はこの形を同じ段の直しで受け入れた（U1 の `reliability-design.md` 5.2・`logical-components.md` 5節）。
- 参照の拒否（409 `BUSINESS_REFERENCE_NOT_FOUND`）と 400 は、この診断の WARN を出さない（既存の業務の誤りと同じ）。

### 2.2 既存の変換の WARN との関係

- `GlobalExceptionHandler` は、すべての `BusinessException` について、変換の WARN を1行（キーは `code`・`status`・`exceptionType` だけ。値・メッセージを出さない）出す。これは今の書き込みの 503 でも同じで、`GlobalExceptionHandler` は全機能で共有するため変えない。
- そのため NFR1.32 の「WARN の行がちょうど1行」は、**同じ失敗についての U2 の業務処理のロガー（`BusinessDataWriteService` のロガー）の診断の WARN がちょうど1行**と読み、`GlobalExceptionHandler` の変換の WARN は既存の別の行として数える（依頼者が承認の場で受け入れた読み方。「承認の場の決定」）。既存の `BusinessDataWriteSecretLeakIT` もロガーで絞って数えている。
- ⑩ の失敗は、書き込みの口の中の結果（`Attempt`）の経路を通さずに応答にするため、既存の `finish` の WARN（`purpose=WRITE`）と重ならない。

### 2.3 確かめのテスト

| テスト | 期待 |
|---|---|
| NFR1.32 の確かめ: 時間切れ・接続の失敗・型の食い違い・全体の上限の各経路で、要求1件の後の `BusinessDataWriteService` のロガーの WARN を数える | 診断の WARN がちょうど1行（0・2 行以上で失敗）。`purpose` は ⑩ の値で、`WRITE` の行が無い |
| 同じ経路で `GlobalExceptionHandler` のロガーの WARN を数える | 1行（既存の形）で、値・メッセージの目印が無い |
| キーの一覧 | 診断の行のキーは 2.1 の一覧だけ（`security-design.md` 4.3 の漏えいのテストの中） |
| 参照の拒否・400 | 診断の WARN が 0 行 |

## 3. 指標と警報

- 作成・更新の道は既存の区画に入る（`uri` のラベルは増えない）。サーバー側の p95 は参考で、合否は k6 の `iteration_duration`（NFR2.21）。
- 503 は `ms-biz-target-db` で拾う。Observability Setup で、使い捨ての環境で対象DB を止め、参照つきの作成の 503 で警報が firing になることを確かめる。`docker pause` では借りる待ちの `UNAVAILABLE` になり、`TIMEOUT` は問い合わせの最中に止めないと起きない（`project.md` の学び）ため、`TIMEOUT` の経路は結合テスト（`reliability-design.md` 6節の T-T1）で確かめ、警報は `UNAVAILABLE` で確かめる。
- 要求は待ちを入れた少ない数で、指標の送信の周期（1 分）を複数またいで送る（`project.md` の学び）。式を書く前に `+Inf` 以外のバケットの有無を確かめる。

## 4. トレース

新しいスパンは足さない。道の属性は固定の道だけで値を持たない（`security-design.md` 4.2）。

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| NFR 要件 NFR1.32（同じ失敗について WARN がちょうど1行） | U2 の業務処理のロガーの診断の WARN をちょうど1行とし、既存の `GlobalExceptionHandler` の変換の WARN は別の行として残す | 2.2。全機能で共有する部品を変えないため。依頼者が承認の場で受け入れた（「承認の場の決定」） |
| NFR 要件 NFR1.32（キーは目的・reason・sqlState・vendorCode・exceptionClass） | 既存の書き込みの WARN と同じく `stage` を足し、U1 と同じく `budgetExceeded` を足した | 値は出さない |
| U1 の機能設計 BR9.5（`FAILED` は理由を保つ） | `Failed(ReferenceReadFailure failure)` の失敗の値（`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`）からキーを取る | U1 が同じ段の直しで受け入れた継ぎ目（U1 の `reliability-design.md` 5節「U2 から受けた継ぎ目」・`logical-components.md` 5節、レビュー R-01） |

## 承認の場の決定

- レビュー R-02: 依頼者は、NFR1.32 の「同じ失敗について WARN がちょうど1行」を「U2 の業務処理のロガー（`BusinessDataWriteService` のロガー）の診断の WARN がちょうど1行で、`GlobalExceptionHandler` の変換の WARN は既存の別の行」と読む形を受け入れた。NFR1.32 の確かめ方は「診断の WARN がちょうど1行」（2.3 の1行目）とし、`traceability.json` の NFR1.32 の target にこの読み方を含めた。
- レビュー R-01: U1 が継ぎ目を受け入れたため、2.1 のキーは `ReferenceReadFailure` から必ず出せる。キーを緩める代わりの手は置かない。

## 後の段に回すこと

- **Code Generation**: 2.1 のログの1行と 2.3 のテスト、目的と段の値の名前。
- **Observability Setup**: 3節の警報の確かめ。
