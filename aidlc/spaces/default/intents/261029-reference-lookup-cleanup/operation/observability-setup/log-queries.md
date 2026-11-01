# ログと監査ログの見方（log-queries）

前の Intent の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/log-queries.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。ログの収集の設定は変えていません（アプリの JSON のログを otel-collector 経由で Loki へ。外部エクスポートは既定で無効）。

値（スキーマ・テーブル・カラムの名前、参照の値・ラベル、行の値、接続先、メールアドレス、トークン）は、この段の確かめでも表示していません。数えたのは、ロガーの名前・キーの名前・列挙の値・件数だけです。

## 1. Loki に届く今回のログ（使い捨ての環境で確かめた。`dashboards.md` 2.1節）

### 1.1 構造化メタデータのキー

Loki のラベルは `service_name` だけで、ほかは行の構造化メタデータです。`| json` は付けません（2節の注）。

| ロガー（`scope_name`） | 出る場面 | キー（値は列挙だけ） |
|---|---|---|
| `cherry.mastersmith.businessdata.service.ReferenceLookup` | 参照の読み取り（定義の取得の `CREATE`・`UPDATE`、ピッカー、ラベル）の対象DB の失敗 | `purpose`（`REFERENCE`）・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`。`stage` は無い |
| `cherry.mastersmith.businessdata.service.BusinessDataWriteService`（保存前の確かめ、⑩） | 作成・更新の保存前の参照の確かめの対象DB の失敗 | `purpose`（`REFERENCE`）・`stage`（例 `BORROW`）・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded` |
| 同じロガー（書き込み、⑪） | 作成・更新・削除の書き込みの対象DB の失敗 | `purpose`（`WRITE`）・`stage`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`。`budgetExceeded` は無い |
| `cherry.mastersmith.common.error.web.GlobalExceptionHandler` | すべての業務の誤りの変換（409・503 など） | `code`・`status`・`exceptionType` |

- 参照の読み取りと保存前の確かめの WARN は、**`purpose` の値がどちらも `REFERENCE`** です。見分けはロガー（`scope_name`）か、`stage` のキーの有無で行います（U2 の `monitoring-design.md` 5節の見込みのとおり）。
- 409（参照先に無い値の拒否）では診断の WARN は出ず、`GlobalExceptionHandler` の変換の WARN だけが出ます（U2 の `monitoring-design.md` 5節「出さない場合」のとおり）。

### 1.2 確かめの回の件数（07:24〜07:50 UTC。使い捨てのアプリのコンテナのログをロガーの名前で数えた）

| レベル | ロガー | 件数 | 中身（列挙の値だけ） |
|---|---|---|---|
| WARN | `ReferenceLookup` | 24 | `reason=UNAVAILABLE`・`budgetExceeded=false`・`exceptionClass=java.sql.SQLTransientConnectionException`・`sqlState=08001`（定義 6・参照の道 18 の 503 と一致） |
| WARN | `BusinessDataWriteService` | 21 | `purpose=REFERENCE`・`stage=BORROW`・`reason=UNAVAILABLE` が 14（保存前の確かめつきの作成の 503 と一致）、`purpose=WRITE`・`stage=BORROW`・`reason=UNAVAILABLE` が 7（参照の無い作成の 503 と一致） |
| WARN | `GlobalExceptionHandler` | 120 | 409 `BUSINESS_REFERENCE_NOT_FOUND`・503 `BUSINESS_TARGET_DB_UNAVAILABLE`・409 `ROLE_NO_CHANGE`（用意の場面の再実行の分） |
| WARN | `com.zaxxer.hikari.pool.PoolBase` | 8 | HikariCP 自身の接続の作り直しの失敗（3節の所見） |
| WARN | `org.flywaydb…Database`・`…PostProcessorRegistrationDelegate$BeanPostProcessorChecker` | 2・2 | 起動の時の既存の WARN（2回の起動の分） |
| ERROR | — | **0** | lgtm を網につないだ後にアプリを起動したため、送信の失敗の ERROR も無い |

- JSON でない行は 0 行。試験用の利用者のメールアドレスの形（`perf-biz-`）・目印の語・台本の値の形・JWT の形（`eyJ`）は、ログの中に 0 件でした。

## 2. Explore（Loki）で使う問い合わせ

どれも、確かめの回の後に実際に流して値が返ることを確かめました（Grafana の代理 `/api/datasources/proxy/uid/loki/loki/api/v1/query`）。

| ID | 目的 | 問い合わせ | 確かめの回の結果 |
|---|---|---|---|
| R1 | 参照の読み取りの失敗を理由ごとに数える | `sum by (reason, budgetExceeded) (count_over_time({service_name="mastersmith"} \| scope_name="cherry.mastersmith.businessdata.service.ReferenceLookup" [1h]))` | `UNAVAILABLE`・`false` 24 |
| R2 | 保存前の確かめ（⑩）と書き込み（⑪）の失敗を分けて数える | `sum by (purpose, stage, reason) (count_over_time({service_name="mastersmith"} \| scope_name="cherry.mastersmith.businessdata.service.BusinessDataWriteService" [1h]))` | `REFERENCE`・`BORROW`・`UNAVAILABLE` 14、`WRITE`・`BORROW`・`UNAVAILABLE` 7 |
| R3 | 業務データの 409・503 を code ごとに数える | `sum by (code, status) (count_over_time({service_name="mastersmith"} \| scope_name="cherry.mastersmith.common.error.web.GlobalExceptionHandler" \| status=~"409\|503" [1h]))` | 409 `BUSINESS_REFERENCE_NOT_FOUND` 34・503 `BUSINESS_TARGET_DB_UNAVAILABLE` 45・409 `ROLE_NO_CHANGE` 7（1 時間の窓の時点の値） |
| R4 | `purpose=REFERENCE` の WARN をロガーごとに見る | `sum by (scope_name, stage) (count_over_time({service_name="mastersmith"} \| purpose="REFERENCE" [1h]))` | `ReferenceLookup`（`stage` 無し）24・`BusinessDataWriteService`（`stage=BORROW`）14 |
| R5 | 1件ずつ見る（行を開いて `trace_id` からトレースへ） | `{service_name="mastersmith"} \| purpose="REFERENCE"` | — |

- 表の中の `\|` は Markdown の表のための書き方で、問い合わせの中では `|` 1つです。
- **設計の問い合わせ `{service_name="mastersmith"} | json | purpose="REFERENCE"`（U1・U2 の `monitoring-design.md` 5節）は使えません。** 行の本文は JSON ではない文（「対象DB の参照先を読めませんでした」など）のため、`| json` が `__error__="JSONParserErr"` になります（確かめの回で 38 行すべて）。キーは構造化メタデータに入っているため、`| json` を付けずに `| purpose="REFERENCE"` で絞ります（前の Intent の `log-queries.md` 2節と同じ形）。
- 503 の code（`UNAVAILABLE`・`TIMEOUT`）は R3 で、どの読み取りで起きたかは R1・R2 の `reason`・`stage` で見分けます。`budgetExceeded=true` は参照の読み取りの全体の上限（既定 10 秒）を超えたことを表します（今回は 0 件）。

## 3. 所見: HikariCP の WARN の例外に対象DB の名前が入る（この段では直していない）

- PG を止めた間（`docker stop` でコンテナの名前が引けない）、HikariCP 自身のロガー `com.zaxxer.hikari.pool.PoolBase` が「Pool is empty, failed to create/setup connection」の WARN を 6 行出しました（読み取りのプール 3・書き込みのプール 3）。
- その行の例外のスタックトレース（コンテナのログのキー `exception`、Loki の `exception_stacktrace`）の `Caused by: java.net.UnknownHostException: …` に、**対象DB の接続先のホストの名前**（ここでは使い捨ての環境のコンテナの名前）が入っていました。数えたのは真偽と件数だけで、値は表示していません。メッセージの本文と Loki の `exception_message` には入っていません。ユーザー名・パスワードは入っていません。
- これは `project.md` の Forbidden「対象DB の接続情報（接続先…）を…ログ…に含めない」と食い違いうるものです。アプリのコードではなく接続プールの部品自身のログで、この Intent の変更で生じたものではありません（前の Intent の確かめの回でも同じ行が出ていた見込みで、その記録は host を数えていない）。
- この段の範囲（監視のファイル）の外のため、直していません。扱い（`com.zaxxer.hikari.pool.PoolBase` のログのレベル・例外の出し方の見直し、`XxxSecretLeakIT` の確かめの範囲）は、依頼者の判断として Incident Response または Feedback & Optimization の段に申し送ります。
- **依頼者の決定（2026-11-01、C: 許容する）**: 対象DB のホスト名が HikariCP の WARN のスタックトレースに出ることは許容し、記録だけにする（直さない・次の Intent の項目にもしない）。ユーザー名・パスワード・資格情報を含む JDBC の URL は出ていないことを数えて確かめた。`project.md` の Forbidden「対象DB の接続情報（接続先）をログに含めない」との差として、ここに記録する。

## 4. 業務データの監査（使い捨ての環境で数えた）

使い捨てのアプリを止めた後、内部DB を読み取り（`./perf/h2-shell.sh --read-only`）で開き、07:26 UTC 以降の `BUSINESS_%` の行を種類・結果・理由ごとに数えました（値は数えていない）。

| `event_type` | `result` | `failure_reason` | 件数 | 台本の数との比べ |
|---|---|---|---|---|
| `BUSINESS_ROW_CREATED` | `SUCCESS` | — | 136 | 作成の 201（保存前の確かめつき 68・参照の無い 68）と一致 |
| `BUSINESS_ROW_CREATED` | `FAILURE` | `BUSINESS_REFERENCE_NOT_FOUND` | 68 | `K999` の 409（68）と一致。**保存前の確かめの拒否は監査に残る**（U2 の決め） |
| `BUSINESS_ROW_CREATED` | `FAILURE` | `BUSINESS_TARGET_DB_UNAVAILABLE` | 21 | 作成の 503（21）と一致 |
| `BUSINESS_ROW_UPDATED` | `SUCCESS` | — | 68 | 更新の 200（68）と一致 |

- 参照の読み取り（定義の取得・ピッカー・ラベル）の行は監査に無いことも、`BUSINESS_%` の種類が上の2つだけであることで確かめました（U1 の BR1.7）。
- 配備した内部DB には何も送っていません（配備したアプリへの要求は 0 件）。

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/observability-setup-questions.md`（1.3節、Q2: A・Q3: A）
- この段の `dashboards.md`（2.1節）・`alarms.md`
- 基盤の設計: `construction/u1-reference-read/infrastructure-design/monitoring-design.md`（1節・5節）・`construction/u2-reference-write-check/infrastructure-design/monitoring-design.md`（1節・5節）・`construction/u3-reference-screens/infrastructure-design/monitoring-design.md`（3節）
- NFR 設計: U1・U2 の `observability-design.md`（診断の WARN）・`security-design.md`（出さないもの）・`reliability-design.md`（失敗の写し）
- 前の Intent の同じ段: `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/log-queries.md`
- コード（読み取り）: `backend/src/main/java/cherry/mastersmith/businessdata/service/ReferenceLookup.java`（`warn`）・`BusinessDataWriteService.java`（`PURPOSE`・`REFERENCE_PURPOSE`・`warnReference`）
- `aidlc/spaces/default/memory/project.md`（Forbidden: 対象DB の接続情報）
- 実行したコマンドの出力: Loki の代理の `query`・`query_range`（リポジトリの外の `~/.mastersmith-tools/lq.sh`・`lmq.sh`・`lkeys.sh`・`poolbase.sh`。キーの名前・列挙の値・件数・真偽だけを出す）、使い捨てのアプリのコンテナのログ（ロガーとレベルごとの件数）、`./perf/h2-shell.sh --read-only`（件数だけ）

## Assumptions & Open Questions

- 3節の所見の扱い（直すか、どこで直すか）は依頼者の判断を待ちます。
- 前の Intent の確かめの回で同じホストの名前が出ていたかは、前の記録が数えていないため確かめていません（見込み）。
