# 監視の設計 — U5 navigation

承認済みの `observability-design.md`（NFR 設計）を、手元の監視（`grafana/otel-lgtm` を compose の profile で見たいときだけ起動する形）の上でどう見るかを表にします。

group の Q1 A にそろえて、次のようにします。

- 独自の指標と新しい警報は足さない。
- group が足すダッシュボードの区画に、navigation の `uri` を足す。区画を書いて式を確かめるのは Observability Setup で、B7 は `docker/monitoring/` に触れない。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/construction/navigation/` の下）:
  - `nfr-design/observability-design.md`（1〜5節）
  - `nfr-design/performance-design.md`（3節）
  - `nfr-design/security-design.md`（7節）
  - `nfr-requirements/observability-requirements.md`
- group・role の `monitoring-design.md`（5節の区画）
- 既存のもの（正とする。読むだけ）:
  - `docker/monitoring/provisioning/alerting/mastersmith.yaml`
  - `docker/monitoring/dashboards/mastersmith-overview.json`
  - `application.yaml`（`slo` の `http.server.requests`）

## 1. Metrics & KPIs

指標の名前は、手元の監視（Prometheus）での名前です。実際の名前・ラベル・`le` のバケットは、Observability Setup で起動して確かめます（`project.md` の学び）。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| メニューと置き場の要求の数（`http_server_requests_milliseconds_count`、`uri="/api/me/navigation"`・`uri="/api/me/table-access"`、`status` ごと） | Spring Boot の `http.server.requests`（既存）。問い合わせの部分はタグに入らない（`UrlQueryStrippingObservationFilter`） | なし（見るだけ） | 画面の移動ごとの読み取りの量と、置き場の 403・400 の傾向を見る |
| 2本の API の p95 | 同上（`slo` のバケット、1000 ms の境界あり） | 目安の線 1000 ms | SLO の基準の値を見る。判定は k6（3節） |
| 2本の API の 5xx の数 | 同上 | なし（割合は既存の全体の警報が拾う） | 解決の口の DB の誤り（500）と、正しくない `%` の並びの 500（受け入れた制約）を見る |

## 2. Alerts

新しい警報は足しません。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-5xx-ratio`（5xx の割合の増加） | 全体の 5xx の割合。navigation の 500 を含む | 既存のとおり | 手元の Grafana の画面だけ（通知の先は作らない） |
| `ms-error-logs`（ERROR のログの増加） | 想定外の失敗の ERROR を数える。正しくない `%` の並びの 500 の ERROR（引数の名前と値が例外の文に入る。受け入れた制約）もここで数えられる | 既存のとおり | 同上 |
| `ms-forbidden`（管理画面への拒否の増加） | 全体の 403 の件数。置き場の 403 を含む | 既存のとおり | 同上。置き場の 403 は業務の結果で、多くても障害とは限らない |
| `ms-pool-pending` | 既存のとおり | 既存のとおり | 同上 |
| （拾わない）2本の API の p95 | 既存の p95 の警報は `uri` を1つの道に固定しており、navigation を含まない | — | 足さない（承認済みの `observability-design.md` 5節の受け入れた制約） |

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| メニューと置き場の応答時間（k6 の `iteration_duration{scenario:…}`、場面 `navMenu`・`tableAccessVisible`・`tableAccessDenied`・`navMenuBaseline`） | p95 < 1000 ms、`checks` の率 = 1（`tableAccessDenied` は 403 を合格にする） | Performance Validation の場面ごと（10 VU・3 分、場面ごとに別の実行）。`Unverified` から始め、Performance Validation の値で判定する。置き場の p95 の実測は基準の値として記録する |
| 1要求で同時に持つ接続 | 1 | 結合テスト `NavigationConnectionUsageIT`（`verify` の段 6 で毎回） |
| 手元の監視の2本の API の p95（`http_server_requests`） | 目安 1000 ms（判定に使わない） | 手元の監視を常に動かしていない間は `Unverified`。配備の直後・監視の確かめ・負荷の試験・振り返りの時点の値を基準の値として並べる（`project.md` の学び） |

SLO の値は緩めません。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| アプリのログ | 業務のログ・アイコンの置き換えのログは出さない。4xx は既存の `GlobalExceptionHandler` の WARN の1行（code・状態・例外の型）だけ |
| 問い合わせの値 | スパンの属性・`http.server.requests` のタグ・INFO 以上のログ・応答の本文に出さない（`NavigationQueryExposureIT` が `verify` で毎回守る） |
| 受け入れた制約 | 正しくない `%` の並びの要求は 500 になり、ERROR のログの例外の文に引数の名前と値が入る（`observability-design.md` 3節）。アプリ全体の直しは後の Intent。TRACE のログにスキーマ名・テーブル名が出る（承認済み） |
| トレース | 変えない（既存の仕組み） |
| 監査 | 足さない（置き場の 403 も残さない） |

## 5. ダッシュボードの区画（group の Q1 A にそろえる）

group が `docker/monitoring/dashboards/mastersmith-overview.json` に足す区画「ロール・グループ・メニュー（Intent 261004-role-menu）」の3つのパネル（API ごとの要求の数・p95・5xx の件数）の `uri` の条件に、次を足します。持ち主は Observability Setup です。

| 足す `uri` | 中身 |
|---|---|
| `/api/me/navigation` | 業務のメニュー |
| `/api/me/table-access` | テーブルの置き場の問い合わせ（問い合わせの部分はタグに入らない） |

- 式の形は group の `monitoring-design.md` 5節と同じで、正規表現に `/api/me/(navigation|table-access)` を足します。
- 承認済みの `observability-design.md` 4節が名指しした2つの式（メニューと置き場の p95）は、区画の p95 のパネルに含まれます。名前は 7節のとおり、起動して確かめてから書きます。
- Observability Setup での確かめは group と同じです。
  - `+Inf` 以外の `le` があることを確かめる。
  - 要求を指標の送信の周期（1 分）を複数またいで送ってから、すべての式を流す。
  - NaN を、要求が無いためと決めつけない。
  - メニューを読むには、版 2 の DSL・ロール・作業ロールの用意が要る。用意の操作は監査に残るため、前もって依頼者に伝える（使い捨ての環境なら配備した内部DB を汚さない）。

## 6. 運用の手順への引き継ぎ

- 置き場の 403 の多さや、正しくない `%` の並びの ERROR の見方を README の「警報と対応の手順」に足すかは、incident-response の段で決めます。

## 7. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `observability-design.md` 4節 | 名指しの式は `http_server_requests_seconds_bucket{uri="/api/me/navigation"}` と同じ形の `uri="/api/me/table-access"` | 手元の監視の既存の警報とダッシュボードの式は、`http_server_requests_milliseconds_bucket` の名前を使っている。区画の式は、Observability Setup で起動して実際の名前（`_milliseconds_` の付き方と `uri` の値）を確かめてから書く | 既存の式（`mastersmith.yaml`・`mastersmith-overview.json`）を読み取りで確かめた事実との食い違い。単位の付く名前の違いで、確かめる2つの道は変わらない |
| `observability-design.md` 4節 | Observability Setup で確かめる式を2つ名指しする | 2つを group の区画の p95 のパネルに含める | group の Q1 A にそろえた（この段のまとめの確認） |
