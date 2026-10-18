# 監視の要件 — U2 u2-businessdata-write

## 出典

- 要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR8、前提 A6、FR12.3・FR13.3）
- この単位の機能設計 `construction/u2-businessdata-write/functional-design/`（`rules.md` の BR11.5・BR12.2）
- U1 の NFR 要件 `construction/u1-businessdata-read/nfr-requirements/observability-requirements.md`（NFR8.1〜NFR8.3、「U2・U3 とそろえる点」）
- この段の答え `construction/u2-businessdata-write/nfr-requirements/nfr-requirements-questions.md`（まとめの確認 Looks correct）
- 警報の決まり `docker/monitoring/provisioning/alerting/mastersmith.yaml`
  - `ms-pool-pending` の式は `max(hikaricp_connections_pending{service_name="mastersmith"})` で、プールで絞っていない
  - `ms-audit-fail` はログの文「監査イベントの記録に失敗しました」
  - `ms-audit-slow`・`ms-5xx-ratio`・`ms-forbidden`
- ダッシュボード `docker/monitoring/dashboards/mastersmith-overview.json`
- `team.md` の Code Style（構造化ログはキーと値。想定内の失敗は WARN 以下）
- `project.md` の学び
  - 式は書く前に名前を確かめ、書いた後にすべて流す
  - p95 は `+Inf` 以外のバケットの有無を確かめる
  - 数の値は送信の周期をまたいで確かめる

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR8 運用 | 当たる | NFR8.4〜NFR8.6 |
| NFR4.2 行の値の漏えい | 部分（ログの形） | NFR8.4 |

## 要件

### NFR8.4 失敗のログの形

**要件**

- 書き込みの失敗と制約の違反のログは、U1 の NFR8.2 と同じく WARN・スタックトレースなしとする。
  - キーと値で出すのは `reason`・`sqlState`・`vendorCode`・`exceptionClass` だけ。
  - 違反の後の確かめの失敗も同じ形にする。
- 監査の書き込みの失敗の ERROR は、今の文（「監査イベントの記録に失敗しました」）を保つ。
  - 業務データの出来事では、項目から `detail` と主キーの値を外す。
  - 出すのは、主キーのカラムの数と `keyTruncated` だけ（BR11.5、FR12.3）。
- 値・主キー・接続先・例外のメッセージは、どのログにも出さない。

**確かめ方**

- ログの JSON を読み、キーの名前と、値が出ていないことを確かめる結合テスト（`BusinessDataWriteSecretLeakIT` の中）。
- 監査の書き込みをテストで失敗させ、ERROR の項目を確かめる（AC3.4.6）。

**持ち主の段**: Code Generation（B4）

### NFR8.5 ダッシュボードの区画

**要件**

U1 の業務データの区画（NFR8.3）に、次を足す。警報は足さない。

- 書き込みの3本（`/api/tables/rows` の POST・PATCH、`/api/tables/rows/delete`）の p95（1000 ms の境界のバケット）
- 409・503 の数（code ごと）
- 書き込みのプールの待ちの数

ダッシュボードはファイルでリポジトリに置く。

**確かめ方**

- 書く前に、使い捨てのアプリを起動して確かめる。
  - 指標とラベルの名前（`uri`・`method`・`status`・プールの名前）
  - `+Inf` 以外のバケットの有無
- 書いた後に、すべての式を流す。

**持ち主の段**: Observability Setup

### NFR8.6 既存の警報で拾えることの確かめ

**要件**

既存の警報で次を拾えることを確かめる。

- `ms-pool-pending`: 書き込みのプールの待ちも拾う。
- `ms-audit-fail`・`ms-audit-slow`: 業務データの出来事の監査の失敗と遅れも拾う。
- `ms-5xx-ratio`: 書き込みの 503 も数える。
- `ms-forbidden`: 書き込みの 403 も数える。
  - しきい値（20）の見直しは、U1 の NFR8.1 と同じ回で行う（要件 A6）。

拾えないときは、警報を足すかを依頼者に諮る。

**確かめ方**

- 使い捨ての環境で、書き込みのプールの待ち・監査の書き込みの失敗・503・403 を起こし、式の値の動きを確かめる。
- 要求は、指標の送信の周期（1 分）を複数またいで送る。

**持ち主の段**: Observability Setup

## 補足

- 道は固定し、名前と値は本文で運ぶ。そのため `http.server.requests` の `uri` のラベルは2つの道だけになり、テーブルごとに系列が増えない（U1 と同じく受け入れた制約）。
- 監査の行の増え方（`scalability-requirements.md` の NFR2.9）には、専用の指標も警報も置かない。
  - 主キー・ビューの 409 の監査は補助権限を持つ人だけに限るため（`security-requirements.md` の NFR4.14）、増え方は使い方に比例する。
  - 内部DB の大きさは、配備先が決まるまでは振り返りの時点の値で見る（`project.md`）。

## 上流との差

- 承認の場の決定（`nullable` は U2 が足す、`formPart` から `lookup` を外す、`required`・`nullable` はすべてのカラムで返す）は、この文書の監視の要件に差を生まない。
- 要件 FR12.3・ADR-003 の「今の監査の仕組みをそのまま使う」との差（`functional-spec.md` X7）: 監査の書き込みの失敗の ERROR から項目を外す変更は、NFR8.4 で要件にした。
- R-05（時間切れの監査の読み方）は `reliability-requirements.md` の NFR4.15 で受ける。監視の区画は、503 の code ごとの数で TIMEOUT を見分ける。
- U1 の文書が「U3 は NFR1.4 以降」としていることとの差は、`performance-requirements.md` に書いた。監視の枝番は、U1 の「NFR8.4 以降」に従う。

## 後の段に回すこと

- **NFR 設計**: 失敗のログを出す1か所の置き場（書き込みの口で理由に変える所。R-08 の型の置き場と同じ所）。
- **Performance Validation**: 負荷の試験の間に、NFR8.5 の区画の式の値を並べて記録する（任意）。
- **Observability Setup**: NFR8.5 の区画、NFR8.6 の確かめ、`ms-forbidden` のしきい値の見直し。
- **基盤の設計**: この文書の要件には持ち主が無い。

## U3 とそろえる点

- 番号: U3 は NFR1.7・2.10・3.7・4.20・7.7・8.7 から振る（監視は NFR8.7 から）。
- ダッシュボードの業務データの区画は1つにまとめる。U3 の画面の時間の測りを足すときも、同じ区画に置く。
- 定義の項目は平らに置く。`required`・`nullable` は必ず値を持ち、`formPart` に `lookup` は来ない。
- 値の長さの上限 10,000 字は、画面の `maxLength` にも当てる（U3 の D19 は画面だけの守り）。
- pattern は画面で当てない。`PATTERN_TIMEOUT` と 413 の文は画面が用意する。
- 503 の出し分け: `BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」、`UNAVAILABLE` は「保存されていない」。
- E2E は代表の流れ1本まで。U2 は E2E を足さない。
