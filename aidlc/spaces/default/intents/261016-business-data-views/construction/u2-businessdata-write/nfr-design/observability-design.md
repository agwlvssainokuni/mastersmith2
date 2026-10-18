# 監視の設計 — U2 u2-businessdata-write

## 捨ての試しの結果（要点）

試しの全体（版・設定）は reliability-design.md の 1節にある。監視に関わるものは次の4つ。

- **W3・W7（失敗の手がかり）**
  - SQLState・業者の番号・例外のクラスの名前があれば、メッセージを読まなくても、失敗の理由を分けられた。
  - MySQL は 1264・1292 の SQLState が 22001 で、1406 と同じになる。ログの `vendorCode` が、見分けの鍵になる。
  - 止まった対象DB の例外は、PG が 08006、MySQL が 08S01、MariaDB が 08000 で、原因はどれも `SocketTimeoutException`。管理者の巻き戻しの失敗は `TransactionSystemException` に包まれた。
- **W7（止まったとき）**
  - 書き込みのプールの接続は、PG 10〜15 秒・MySQL 20 秒・MariaDB 10 秒のあいだ、借りられたままになる。失敗の後は、プールが捨てる。
  - 確定の途中で止まると、応答は失敗でも、行は保存されていた。監査の `UNKNOWN` が、この場合の手がかりになる（Q5: A）。
- **W11（H2）**: 監査の書き込みが長さで失敗すると、例外の文に `detail` の値が入った。業務データの出来事の ERROR に例外を付けない根拠になる。
- **W10（対象DB の側）**: perf の MySQL（768m）は、4 MiB 近い同時の書き込みが 10 本になると OOMKilled になった。これは対象DB の側の出来事で、アプリの指標には「503 と、プールの使用中の数」として現れる（F1: A）。

## 対応表

| U2 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR8.4 失敗のログの形 | 1節 | そのまま。キーに `stage` を足した |
| NFR8.5 ダッシュボードの区画（部分。式は Observability Setup） | 2節 | 区画の中身を決めた |
| NFR8.6 既存の警報で拾えることの確かめ（部分。実測は Observability Setup） | 3節 | 見立てと確かめ方を決めた |

## 1. ログ（NFR8.4）

ログを出すのは、`BusinessDataWriteService` が結果の型を code に変える1か所だけにする（U1 と同じ形）。

| 場合 | レベル | キーと値 |
|---|---|---|
| 503 UNAVAILABLE・TIMEOUT、409 MISMATCH・SAVE_REJECTED（DB が拒んだもの）・ROW_NOT_UNIQUE、違反の後の確かめの失敗 | WARN（スタックトレースなし） | `purpose`（WRITE）・`stage`（借りる・事前の問い合わせ・書き込みの文・確定・戻し・確かめ・巻き戻し）・`reason`・`sqlState`（無ければ出さない）・`vendorCode`・`exceptionClass`（単純名） |
| 409 DUPLICATE_VALUE・REFERENCE_NOT_FOUND（業務の失敗） | ログを出さない（監査に残る） | — |
| 想定外（500） | 既存の共通の仕組みが ERROR で出す | 投げる例外はメッセージも原因も持たない |
| 入力の誤り（400）・403・404・413 | ログを出さない（共通の仕組みの WARN 以下に任せる） | — |

- 出さないもの: 値・主キー・スキーマ名とテーブル名以外の識別子・接続先・例外のメッセージ（U1 と同じ）。
- 書き込みの口（`JdbcTargetWriteAccess`）と `RowSqlExecutor` は、ログを出さずに結果を返す。例外は接続を捨てる処理と閉じる処理の失敗だけで、口が例外のクラスの名前だけを WARN で出す。
- 1つの失敗のログは1行だけにする。

**監査の書き込みの失敗の ERROR**
- 文は今のまま（「監査イベントの記録に失敗しました」）にする。
- 業務データの出来事では、`detail`・主キーの値・**例外（cause）**を外す。出すのは、主キーのカラムの数・`keyTruncated`・例外のクラスの名前だけ（W11）。
- ほかの出来事の ERROR は変えない（`AuditEventListener` の `logFailure` を、出来事の種類で分ける）。

**確かめ方**: `BusinessDataWriteSecretLeakIT` でログの JSON を読み、キーの名前と、値が出ていないことを確かめる。監査の書き込みをテストで失敗させ、ERROR の項目に `exception` が無いことも確かめる。

## 2. 指標とダッシュボード（NFR8.5）

U1 の「業務データ」の区画に、次の3つを足す。警報は足さない。

| パネル | 元の指標 | 確かめること（Observability Setup） |
|---|---|---|
| 書き込みの3本の p95 | `http.server.requests`（`uri` が `/api/tables/rows`（POST・PATCH）と `/api/tables/rows/delete`、`method`） | 1000 ms の境界のバケットがあるか（`+Inf` だけでないか） |
| 409・503 の数（code ごと） | 応答の code のラベルが無ければ、`status` と `uri` で数える | code のラベルの有無。TIMEOUT と UNAVAILABLE を見分けられるか |
| 書き込みのプールの使用中・待ち・待ちの時間切れの累計 | `hikaricp.connections.*`（プールの名前は `mastersmith-target-db-write`） | 名前とラベル |

- 式は、書く前に使い捨てのアプリで指標の名前とラベルを確かめ、書いた後にすべての式を流して確かめる（project.md）。
- code のラベルが無く TIMEOUT と UNAVAILABLE を見分けられない場合は、監査の `UNKNOWN`（Q5: A）の件数で、確定したか分からない書き込みを数える（内部DB の監査の確かめの手順で、値は出さない）。

## 3. 警報（NFR8.6）

| 既存の警報 | 業務データの書き込みの何が入るか | 見立て |
|---|---|---|
| `ms-pool-pending`（全プールの最大） | 書き込みのプールの待ち | 入る見込み。式がプールで絞っていないため。止まったときは、使用中が上限に張りつき、待ちが増える形で現れる |
| `ms-5xx-ratio` | 書き込みの 503 | 入る。ほかの API の要求が多いと、割合が小さく鳴らないことがある |
| `ms-forbidden` | 書き込みの 403 | 入る。しきい値（20）の見直しは、U1 の NFR8.1 と同じ回 |
| `ms-audit-fail`・`ms-audit-slow` | 業務データの出来事の監査の失敗と遅れ | 入る。ERROR の文は変えないため |

**確かめ方（Observability Setup）**
- 使い捨ての環境で、次の4つを起こし、式の値の動きを確かめる。
  - 書き込みのプールの待ち（`bizWritePoolLimit` の形）
  - 監査の書き込みの失敗
  - 503（対象DB を `docker pause` で止める）
  - 403
- 要求は、指標の送信の周期（1 分）を複数またいで送る。
- 拾えなければ、警報を足すかを、その段の判断事項にする。

## 4. トレース

- 道は固定し、名前と値は本文で運ぶ。トレースの `uri` の属性に値は載らない（U1 と同じ）。
- 書き込みの口の中の文は、トレースの属性に SQL を載せない（今の構成のまま。JDBC の計装を足さない）。

## 5. 上流との差

- **BR6.1 の表に足す**（Q6: A・F3: A）: 足した番号も、同じキー（`sqlState`・`vendorCode`）でログに出る。
- **BR6.2 の切り替え先を先に使う**（Q2: A）: 確かめの失敗のログの `stage` は「戻し」か「確かめ」になる。
- **BR3.5 に日時の秒の端数を足す**（Q6: A）: 入力の誤り（400）なので、ログは出さない。
- **U1 の「貸すたびに付ける」との差**（Q3: A）: 通信の待ちを戻す処理が無いため、戻す処理の失敗のログ（U1 にあるもの）は U2 に無い。
- **NFR1.6・NFR4.7 の確かめの入力**（F4: A）。
- **「およそ 18 秒」の読み替え**（Q4: A）: 止まったときに、プールの接続が借りられたままになる時間は 10〜20 秒になる。
- **NFR8.4**: ログのキーに `stage` を足した。監査の書き込みの失敗の ERROR から、業務データの出来事に限って例外も外した（W11）。
- **R-05（時間切れの監査）**: 結果の `UNKNOWN` で見分ける（Q5: A。reliability-design.md の 4節）。

## 6. 後の段に回すこと

- **Infrastructure Design**
  - F1: A の perf の MySQL のメモリの上限
  - F2: A の `REVOKE TEMPORARY` と README
  - 機能設計のレビューの R-06 の表ごとの GRANT
- **コード生成の計画（B4）**
  - SpotBugs の除外の承認
  - 名前つきの管理者の注入の結合テスト
  - NFR 要件のレビューの R-06: 監査の移行の後方互換の結合テスト
  - `AuditMigrationCompatibilityIT` などの洗い出し
  - `AuditEventListener` の `logFailure` を出来事の種類で分ける変更と、`AuditSecretLeakIT` などへの影響
- **Observability Setup**
  - NFR 要件のレビューの R-03: 内部DB の大きさの基準の値（監査の行の増え方）
  - `ms-pool-pending` が書き込みのプールを拾うかの実測
  - 2節の区画と、3節の確かめ
  - `ms-forbidden` のしきい値の見直し（U1 と同じ回）
- **Performance Validation**: 負荷の試験の間に、区画の式の値を並べて記録する（任意）。

## 7. U3 とそろえる点

- ダッシュボードの業務データの区画は1つにまとめる。U3 の画面の時間の測りを足すときも、同じ区画に置く。
- 503 の出し分け: `BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」、`UNAVAILABLE` は「保存されていない」。
- 監査の `UNKNOWN` は画面に出ない。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-requirements/observability-requirements.md`（NFR8.4〜NFR8.6）・`reliability-requirements.md`（NFR4.15）
- この段の答え
  - `construction/u2-businessdata-write/nfr-design/nfr-design-questions.md`（Q3・Q5・Q7: A）
  - `construction/u2-businessdata-write/nfr-design/nfr-design-followup-questions.md`（F1: A）
- U1 の NFR 設計: `construction/u1-businessdata-read/nfr-design/observability-design.md`（1節〜3節）
- 機能設計: `construction/u2-businessdata-write/functional-design/rules.md`（BR11.5・BR12.2）
- コード
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（`logFailure` の `setCause`）
  - `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-pool-pending` などの式）
  - `docker/monitoring/dashboards/mastersmith-overview.json`
- 決まり: `aidlc/spaces/default/memory/project.md`（式は書く前に名前を確かめ書いた後に流す、p95 のバケット、送信の周期をまたぐ）
- 捨ての試し: リポジトリの外の試しのコードと結果（W3・W7・W10・W11）

## Assumptions & Open Questions

- 前提: `ms-pool-pending` の式がプールで絞っていないことは、警報の決まりのファイルで確かめた。書き込みのプールでの値の動きは、まだ確かめていない（Observability Setup で実測する）。
- 依頼者の決定 Q5: A は、2節と5節に反映した。
- この文書に固有の、諮る点は無い。
