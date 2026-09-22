# Monitoring Design — U3 管理画面のアクセス制御（u3-access-control）

U3 の `observability-design.md`（NFR Design）の方針を、当面の配備先（開発者の PC 上のコンテナ）で実現する設計。性能・信頼性は `performance-design.md`・`reliability-design.md`、安全は `security-design.md`、容量は `scalability-design.md`、部品は `logical-components.md` と Domain Design の `components.md`、処理の流れは `functional-spec.md` に従う。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。収集・確認の仕組みは U1 の `infrastructure-design/monitoring-design.md` のとおりで、本書は U3 に固有の指標・警報の候補だけを書く（U1 と重なる指標は U1 の定義を使い、ここでは繰り返さない）。

## 1. 指標（Metrics & KPIs）

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 確認用 API の応答時間 | `http.server.requests`（`/api/admin/check` の 95 パーセンタイル） | 300 ミリ秒超 | U3 の NFR1.1 |
| 管理者のみの API での 403 の件数 | `http.server.requests`（状態コード 403）と、アプリのログの WARN（`ACCESS_DENIED`） | 1時間に 20 件超 | 権限の無い利用者による管理画面へのアクセスの試み |
| 細工されたパスの拒否の件数 | アプリのログの WARN（`REQUEST_REJECTED`） | 1時間に 10 件超 | 判定の回り込みの試み（U3 の NFR3.3） |

しきい値は想定の規模（利用者 50 名）から置いた見積もりで、配備先が決まったら実際の量を見て見直す。

### 監査ログから数える見方（指標は作らない）

管理画面へのアクセス拒否の件数（監査ログの ACCESS_DENIED の件数）は、U3 の NFR10.5 のとおり本Intentでは指標（計器）として作らない。数える仕組みもアプリには作らず、依頼者が監査ログを開いたときに件数を数える（U4 の `infrastructure-design/monitoring-design.md` の確認の手順）。目安は1日数十件以内（U3 の NFR1.5）で、大きく超えていれば監査ログで利用者と接続元IP を確かめる。配備先が決まったときに、指標にするかを Observability Setup で決める。

## 2. 警報（Alerts）

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 管理画面への拒否の増加 | 403 の件数（HTTP の指標、外部エクスポートの有効時）が 1 時間に 20 件超 | 中 | 当面: 開発者が確認（監査ログで利用者・接続元IP を確かめる）。配備先が決まったら通知の先を決める |
| 回り込みの試み | `REQUEST_REJECTED` が 1 時間に 10 件超 | 中 | 同上 |
| 確認用 API の遅れ | 95 パーセンタイルが 300 ミリ秒超 | 低 | 同上（U4 の記録の時間と DB の待ちを確かめる） |

## 3. 目標（SLIs / SLOs）

| SLI | SLO target | Measurement window |
|---|---|---|
| 確認用 API の応答時間（95 パーセンタイル、成功・403 とも） | 300 ミリ秒以内（同時 10 件） | Performance Validation の試験の間、配備先では 30 日 |
| 拒否の記録で応答が遅れる時間 | 100 ミリ秒以内（U3 の NFR1.3） | Performance Validation の試験の間 |

## 4. ログとトレース

- U3 のログ・トレースは U1 の仕組みに乗る。403・400 の WARN のログにはメールアドレスとパスを出さない（U3 の `observability-design.md` 2章）。利用者の特定は監査ログで行う。
- フィルターの段階の応答（401／403／400）にもトレースIDが付き、監査ログとアプリのログを突き合わせられる。
- ダッシュボードは配備先が決まったときに作る。U3 の面としては「403 の件数・拒否の件数・確認用 API の応答時間」を候補とする。
