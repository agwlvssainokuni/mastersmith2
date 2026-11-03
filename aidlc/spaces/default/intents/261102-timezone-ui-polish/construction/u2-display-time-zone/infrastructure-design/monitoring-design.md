# 監視の設計 — u2-display-time-zone

出典: NFR Design（`construction/u2-display-time-zone/nfr-design/observability-design.md`・`scalability-design.md`・`reliability-design.md`）、既存の実物（読み取りだけ: `docker/monitoring/provisioning/alerting/mastersmith.yaml`）、`project.md`（Deployment: 手元の監視、新しい警報は足す理由があるときだけ）。

新しい経路は、既存の `/api/me/preferences` の項目を 1 つ足すことと、画面のログイン後の読み取り 1 回だけ。既存の HTTP の指標・警報・ダッシュボード・構造化ログ・トレースが新しい項目を含めてそのまま効くため、**新しい指標・警報・ダッシュボードの決まりを足さない**。警報の式・指標の名前・ラベルは変えない。手元の監視（otel-lgtm）は常に動かしていないため、既存の警報が新しい項目に効くことの実機の確かめはこの Intent では行わない（式が変わらないことを差分で確かめる）。

## Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| `GET`・`PUT /api/me/preferences` の件数・時間・状態コード | 既存の HTTP の指標（`http.server.requests` 系） | 変更なし | 新しい項目を含む要求にもそのまま効く |
| `GET /api/me/preferences` の件数 | 同上 | しきい値なし | ログインの回数に比例して増える（ログイン後に 1 回）。想定内の増加で、警報のしきい値は変えない（`SC1`） |
| 新しい指標 | なし | なし | 専用の指標は置かない（表示のタイムゾーンの設定・読み取りの失敗の指標を足さない） |

## Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 新しい警報 | なし | なし | 配備先が決まるまで通知の先も作らない（`project.md`） |
| 既存: 5xx の割合の増加（`ms-5xx-ratio`） | 変更なし | 既存のまま | 既存のまま。保存の排他の待ちの上限切れ（500 `INTERNAL_ERROR`）などの想定外の失敗を拾う |
| 既存: ERROR のログの増加 | 変更なし | 既存のまま | 既存のまま |
| 既存: コネクションプールの待ち（`ms-pool-pending`） | 変更なし | 既存のまま | 内部DB の接続は 1 要求で 1 本のまま |

400 の拒否（集合に無い名前）は想定内の業務エラーで、5xx の警報に含まれない。画面の読み込みの失敗（`error` 状態）はサーバーから観測できない（ビーコンを送らない）。サーバー側で気づく手段は、既存の `GET /api/me/preferences` の 5xx・401 の指標と警報だけ（`OD3`）。

## SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| `PUT /api/me/preferences` の応答時間（新しい場面 `preferencesSaveTimeZone` と既存の 3 場面） | 新しい SLO は置かない。既存の場面の目標（p95 1 秒）のまま | 負荷の試験のとき（Build and Test）。手元の監視を常に動かしていない間の判定は Unverified として基準の値を並べる |
| 配備の健全性（ヘルスチェック・Flyway が V14 を 1 件適用） | アプリ全体のヘルスチェックの SLO に含まれる | 配備のたび（スモークテスト S1） |

## Logs & Tracing

- ログ: 変更なし。タイムゾーンの **名前の値を載せない**（検証の前の値は型の伏せ字で TRACE にも出ず、検証を通った名前も項目として出さない）。400 は WARN 以下・スタックトレースなしで、境界で 1 回だけ、項目の名前（`displayTimeZone`）と誤りの種類（`INVALID_VALUE`）だけをキーと値で出す。想定外の例外は例外のクラスの名前だけを ERROR に出す（既存の扱い）。画面は `console` に値や応答を出さない。
- トレース: 変更なし。名前は本文で運び、道・問い合わせに載せない。外へ送る設定は既定で無効のまま（配備の設定で環境ごとに切り替える）。
- 監査: 残さない（監査の種類・列を足さない）。保存の操作で監査の行が増えないことを、スモークテスト S8 で確かめる。
- ダッシュボード: 新しい区画を足さない。
- 見えない点: 画面の表示（選択・時刻の欄の時差）はサーバーから観測できない。依頼者が実際の画面で確かめる（スモークテスト S2・S3・S5。持ち主: Deployment Execution）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-design/observability-design.md`・`scalability-design.md`・`reliability-design.md`
- 既存の実物（読み取り）: `docker/monitoring/provisioning/alerting/mastersmith.yaml`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] ログインごとの `GET` の増加（ログインの回数に比例）は、既存の警報（5xx の割合・p95）のしきい値に影響しない規模である。Build and Test の負荷の試験の既存の場面の値で確かめる。
- [assumption] 手元の監視を動かして新しい項目に警報が効くことを確かめる段がこの Intent の流れに無く、式が変わらないことの差分の確かめで足りるとみなす。
- 未解決の点は無い。
