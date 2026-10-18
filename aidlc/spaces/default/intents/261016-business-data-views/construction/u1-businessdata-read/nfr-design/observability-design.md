# 監視の設計 — U1 u1-businessdata-read

## 捨ての試しの結果（要点）

試しの全体は reliability-design.md の 1節にある。監視に関わるものは次の4つ。

- **T3・T7・T8（失敗の手がかり）**
  - SQLState・業者の番号・例外のクラスの名前があれば、メッセージを読まなくても失敗の理由を分けられた。
  - MySQL の文の時間切れ（`MySQLTimeoutException`）は SQLState を持たない。そのため、ログの `sqlState` は空になりうる。
  - 借りる失敗の SQLState は、原因が無いときは空になる。確かめの失敗のときは、DB ごとに違う（08003・42000 など）。
- **F2（応答しない対象DB）**
  - 貸すときに `setNetworkTimeout`（10 秒）を付けると、つないだ後に相手が止まったときに、MySQL 20 秒・PG 15 秒・MariaDB 10 秒で失敗した。
  - その間、プールの接続は借りられたまま。
  - 指標では、プールの「使用中」と「待ち」が増える形で現れる。
  - 失敗した接続はプールが捨て、新しい接続に替わる。
- **T6（上限に届いたとき）**: 借りる待ちの時間切れは、HikariCP の待ちの時間切れの累計に現れる。
- **F1（重さ）**: 対象DB の重さは、3本の API の p95 と、プールの待ちに現れる。Performance Validation では、表が入る設定で測る。

## 対応表

| U1 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR8.1 `ms-forbidden` の見直し（部分。しきい値は Observability Setup） | 3節 | 業務データの 403 が数に入ることだけを、この設計で決めた |
| NFR8.2 失敗のログの形 | 1節 | そのまま |
| NFR8.3 ダッシュボードの区画（部分。式と R-05 の実測は Observability Setup） | 2節・3節 | 区画の中身を決めた |

## 1. ログ（NFR8.2）

ログを出すのは、`BusinessDataReadService` が結果の型を code に変える1か所だけ（決まっていること）。

| 場合 | レベル | キーと値 |
|---|---|---|
| 503 UNAVAILABLE・TIMEOUT、409 MISMATCH・ROW_NOT_UNIQUE、値の型の誤りの 400 | WARN（スタックトレースなし） | `purpose`（LIST・DETAIL）・`reason`・`sqlState`（無ければ出さない）・`vendorCode`・`exceptionClass`（単純名） |
| 想定外（500） | 既存の共通の仕組みが ERROR で出す | 投げる例外は、メッセージも原因も持たない。持つのは SQLState と例外のクラスの名前だけ |
| 入力の誤り（400）・403・404 | ログを出さない（既存の共通の仕組みの WARN 以下に任せる） | — |

出さないもの:

- 値・主キー・検索の値
- スキーマ名とテーブル名以外の識別子
- 接続先
- 例外のメッセージ

補足:

- スキーマ名とテーブル名も、キーに出さない。DSL の名前は秘密ではないが、行を見分ける手がかりにしないため。テーブルごとの遅さは見分けない（承認済みの、受け入れた制約）。
- 口（`JdbcTargetReadAccess`）と `RowSqlExecutor` は、ログを出さずに結果を返す。例外は次の2つで、口が例外のクラスの名前だけを WARN で出す（reliability-design.md の 2節）。
  - 接続を閉じる処理の失敗
  - 通信の待ちを戻す処理の失敗
- 1つの失敗のログは、1行だけにする。

## 2. 指標とダッシュボード（NFR8.3）

手元の監視（grafana/otel-lgtm）のダッシュボードのファイルに、「業務データ」の区画を1つ足す。警報は足さない（決まっていること）。

| パネル | 元の指標 | 確かめること（Observability Setup） |
|---|---|---|
| 3本の API の p95 | `http.server.requests`（`uri` が `/api/tables/definition`・`/api/tables/rows/search`・`/api/tables/rows/get`） | 1000ms の境界のバケットがあるか（`+Inf` だけでないか）を、書く前に確かめる |
| 503 の数（code ごと） | 応答の code のラベルが無ければ、`status=503` と `uri` で数える | code のラベルの有無を、実際に起動して確かめる |
| 対象DB のプールの使用中・待ち・待ちの時間切れの累計 | `hikaricp.connections.*`（プールの名前は `mastersmith-target-db`） | 名前とラベルを、起動して確かめる |

- 式は、書く前に使い捨てのアプリで指標の名前とラベルを確かめ、書いた後にすべての式を流して確かめる（project.md）。

## 3. 警報（NFR8.1・R-05 は Q6: A）

既存の警報が業務データの失敗を拾うかの見立て:

| 既存の警報 | 業務データの何が入るか | 見立て |
|---|---|---|
| `ms-forbidden` | 403（テーブルの主権限が NONE） | 数に入る。しきい値（20）の見直しは Observability Setup で行う |
| `ms-5xx-ratio`（全要求に対する割合） | 503 | 入る。ただし、ほかの API の要求が多いと割合が小さく、鳴らないことがある |
| `ms-pool-pending`（全プールの最大） | プールの待ち | 入る。ただし、接続が作れないとき（3 秒で切れる）は pending が増えにくい |

**R-05 の手当て（Q6: A）**:
1. Observability Setup で、使い捨ての環境の対象DB を次の2つの状態にして、業務データの API を送る。
   - 止めた状態
   - つないだ後に止めた状態（`docker pause`）
2. そのうえで、`ms-5xx-ratio`・`ms-pool-pending` の式の値を実測する。
3. 拾えなければ、業務データの 503 に絞った警報を足すかを、その段の判断事項にする。

## 4. トレース

- 道は固定し、値は本文で運ぶ。そのため、トレースの `uri`・`http.route` の属性に値は載らない（BR10.4）。
- JDBC の文は、トレースの属性に載せない（JDBC の観測の仕組みを足さない）。
- 外部へ送る設定を有効にした結合テストで、属性に値が無いことを確かめる（security-design.md の 3節）。

## 5. 上流との差

- 無し。
  - R-05 の手当ては Q6: A で取り込み、判断は Observability Setup に置いた（承認済みの NFR8.3 の持ち主の段のとおり）。
  - F1〜F3 の依頼者の決定は、ログの形と区画を変えない。

## 6. 後の段に回すこと

- **Observability Setup**:
  - NFR8.1 のしきい値の見直し
  - NFR8.3 の区画の式（バケットとラベルの確かめ）
  - R-05 の実測と、警報を足すかの判断
- **コード生成の計画（B2）**:
  - ログのキーの名前を決める。
  - `BusinessDataSecretLeakIT` に、ログの JSON のキーと値が無いことの確かめを入れる。
- **Performance Validation**:
  - 負荷の試験の間の区画の値を、並べて記録する（任意）。
  - その回の対象DB の設定（F1: A）も、あわせて記録する。

## 7. U2・U3 とそろえる点

- U2 の書き込みの失敗のログも、同じキー（`purpose`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`）と WARN にそろえる。監査の書き込みの失敗のログは既存の形を使い、主キーの値を含めない。
- ダッシュボードの業務データの区画は、1つにまとめる。U2 は、書き込みの3本の API の p95 と、書き込みのプールのパネルを足す。
- U3 の画面の誤りは、サーバーのログに出ない（画面の側の扱い）。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/observability-requirements.md`（NFR8.1〜NFR8.3）
- NFR 要件のレビュー: `.aidlc-reviews/nfr-requirements/units/u1-businessdata-read/00d44a7a2452e6b7/1.json`（R-05）
- この段の答え
  - `construction/u1-businessdata-read/nfr-design/nfr-design-questions.md`（Q6: A）
  - `construction/u1-businessdata-read/nfr-design/nfr-design-followup-questions.md`（F1・F2: A）
- コード
  - `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-forbidden`・`ms-5xx-ratio`・`ms-pool-pending`）
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`（プールの名前）
- 決まり
  - `aidlc/spaces/default/memory/team.md`（構造化ログはキーと値、例外のログは境界で1回）
  - `project.md`（式は書く前に起動して確かめる、p95 のバケットを確かめる、監視の確かめは送信の周期を複数またぐ）
- 捨ての試し: リポジトリの外の試しのコードと結果（T3・T6・T7・T8・F2）

## Assumptions & Open Questions

- 前提: 今の `http.server.requests` に、応答の code ごとのラベルは無いと見立てた（確かめていない）。無ければ、503 は状態コードと道で数える。
- **依頼者の決定（F1〜F3: A）**: この文書の設計は変えない。F2 の失敗までの時間（10〜20 秒）は、1節の見立てに反映した。
- 諮る点は無い。R-05 の判断は、Observability Setup に置いた。
