# 監視の要件 — U1 u1-businessdata-read

## 出典

- 要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR8、前提 A6、FR13.3）
- この単位の機能設計 `construction/u1-businessdata-read/functional-design/`（`rules.md` の BR8.5・BR8.6・BR10.2）
- この段の答え `construction/u1-businessdata-read/nfr-requirements/nfr-requirements-questions.md`（Q8: A、まとめの確認 Looks correct）
- コード: `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-5xx-ratio`・`ms-error-logs`・`ms-pool-pending`・`ms-forbidden`、p95 の警報は道を固定した式）、`backend/src/main/java/cherry/mastersmith/targetdb/service/JdbcTargetSchemaReader.java`（対象DB の失敗を WARN と `reason`・`sqlState` のキーで出す今の形）
- `team.md` の Code Style（構造化ログはキーと値で渡す、想定内の失敗は WARN 以下）と `project.md` の学び（警報とダッシュボードの式は、書く前に起動して名前を確かめ、書いた後にすべての式を流す。p95 の式は `+Inf` 以外のバケットがあるかを確かめる）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR8 運用 | 当たる | NFR8.1〜NFR8.3 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR8.1 | 業務データの API の 403 が増えたときの警報 `ms-forbidden` のしきい値（20）を見直す（要件 A6）。この単位の3本の API の 403（テーブルの主権限が NONE）も、この警報の数に入る | 使い捨ての環境で 403 を送り、式の値の動きを確かめる。指標の送信の周期（1 分）を複数またいで送る（`project.md`） | Observability Setup |
| NFR8.2 | 対象DB の失敗と値の型の誤りのログは、WARN・スタックトレースなしとし、キーと値で次だけを出す: `reason`・`sqlState`・`vendorCode`・`exceptionClass`。値・主キー・検索の値・接続先・例外のメッセージは出さない（NFR4.2）。想定外の失敗（5xx の残り）は、既存の共通の仕組みのとおり ERROR とするが、`SQLException` のメッセージを持つ例外をそのまま渡さない | ログの JSON を読み、キーの名前と、値が出ていないことを確かめる結合テスト（`BusinessDataSecretLeakIT` の中） | Code Generation（B2） |
| NFR8.3 | 手元の監視（grafana/otel-lgtm）のダッシュボードに業務データの区画を足し、警報は足さない（Q8: A）。区画に置くもの:<br>・3本の API（`/api/tables/definition`・`/api/tables/rows/search`・`/api/tables/rows/get`）の p95（1000 ms の境界のバケット）<br>・503 の数（code ごと）<br>・対象DB のプールの待ちの数<br>既存の `ms-5xx-ratio`（503 を含む）・`ms-error-logs`・`ms-pool-pending`（プールで絞っていない式）で拾えることを確かめる。ダッシュボードはファイルでリポジトリに置く | 書く前に使い捨てのアプリを起動して、指標・ラベル（`uri`・`status`・プールの名前）の名前と、`+Inf` 以外のバケットの有無を確かめる。書いた後にすべての式を流す | Observability Setup |

## 補足

- 道は固定し、スキーマ名・テーブル名・行の値は本文で運ぶため、`http.server.requests` の `uri` のラベルは3つの値だけになり、テーブルごとに系列が増えない。テーブルごとの遅さは区画では見分けない（受け入れた制約）。
- 監査は、読み取りでは残さない（FR12.2）。この単位に監査の監視は無い。

## 上流との差

- 承認の場の決定（`formPart` の値域から `lookup` を外す、`nullable` は U2 が足す）は、この単位の監視の要件に差を生まない。
- R-07（例外の通り道）は `reliability-requirements.md` の NFR4.8 で受ける。ログの形は NFR8.2 のとおりで、口の置き場によらない。R-08 は `reliability-requirements.md` の NFR3.3 で受ける。
- 要件の前提 A6 は、NFR8.1 で Observability Setup に引き継いだ。

## 後の段に回すこと

- **NFR 設計**: ログを出す1か所の置き場（失敗を理由に変える所。R-07 の口の置き場と同じ所）。
- **Performance Validation**: 負荷の試験の間に、NFR8.3 の区画の式の値を並べて記録する（任意）。
- **Observability Setup**: NFR8.1 のしきい値の見直し、NFR8.3 の区画、`ms-pool-pending` が対象DB のプールも拾うことの確かめ。

## U2・U3 とそろえる点

- U2 の書き込みの失敗のログも、NFR8.2 と同じキー（`reason`・`sqlState`・`vendorCode`・`exceptionClass`）と WARN にそろえる。監査の書き込みの失敗のログは、既存の「監査イベントの記録に失敗しました」の形を使い、主キーの値を含めない（FR12.3）。
- ダッシュボードの業務データの区画は1つにし、U2 が書き込みの3本の API の p95 と、書き込みのプールの待ちを同じ区画に足す。
- 枝番: U2・U3 の監視の要件は NFR8.4 以降に振り、U1 と重ねない。
