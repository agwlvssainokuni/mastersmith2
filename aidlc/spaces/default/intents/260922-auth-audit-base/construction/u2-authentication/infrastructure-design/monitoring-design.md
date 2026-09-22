# Monitoring Design — U2 認証（u2-authentication）

U2 の `observability-design.md`（NFR Design）の方針を、当面の配備先（開発者の PC 上のコンテナ）で実現する設計。性能・信頼性は `performance-design.md`・`reliability-design.md`、安全は `security-design.md`、容量は `scalability-design.md`、部品は `logical-components.md` と Domain Design の `components.md`、処理の流れは `functional-spec.md` に従う。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。仕組み（収集・確認の手順）は U1 の `infrastructure-design/monitoring-design.md` のとおりで、本書は U2 に固有の指標・警報の候補だけを書く。ログインの API の応答時間の指標と警報は U1 の `monitoring-design.md` 1章・2章で定義しており、ここでは繰り返さない（しきい値は U2 の NFR1.1 の 1 秒で同じ）。

## 1. 指標（Metrics & KPIs）

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| トークンの更新の API の応答時間 | `http.server.requests`（`/api/auth/session/refresh` の 95 パーセンタイル） | 1 秒超 | U2 の NFR1.2 |
| 起動時の照合の時間 | アプリのログ（`bcryptCost`・`elapsedMs` の INFO／WARN） | 100〜500 ミリ秒の範囲外（WARN） | cost の見直しの合図（U2 の NFR2.1） |
| ロックの発生 | アプリのログ（「ロックした」の INFO） | 1時間に 5 件超 | 総当たりやロックの悪用の兆し（U2 の NFR4.4 は受け入れた危険だが、気づけるようにする） |
| 使い終わったトークンの削除 | アプリのログ（削除の件数の INFO、失敗の ERROR） | ERROR が出る、または 2 日続けて INFO が無い | 内部DBの肥大の防止（U2 の NFR1.7） |
| Origin の不一致 | アプリのログ（403 の WARN） | 1時間に 10 件超 | CSRF の試みや設定の誤り（ベースURL）の兆し |

### 監査ログから数える見方（指標は作らない）

ログインの失敗の割合（監査ログの LOGIN_FAILED ÷ ログインの試行）は、U2 の NFR10.7 のとおり本Intentでは指標（計器）として作らず、数える仕組みもアプリには作らない。依頼者が監査ログを開いたときに数える（U4 の `infrastructure-design/monitoring-design.md` の確認の手順）。目安は1日で 30% 以下で、大きく超えていれば、同じメールアドレス・接続元IP の失敗が続いていないかを確かめる。

## 2. 警報（Alerts）

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 照合の時間の範囲外 | 起動時に照合の時間の WARN が出る | 低 | 当面: 開発者が確認（cost か CPU の上限を見直す）。配備先が決まったら通知の先を決める |
| ロックの多発 | ロックの INFO が 1 時間に 5 件超 | 中 | 同上（監査ログで接続元IP を確かめる） |
| 削除の失敗 | 削除の ERROR | 低 | 同上（翌日に再び行われる） |

## 3. 目標（SLIs / SLOs）

| SLI | SLO target | Measurement window |
|---|---|---|
| ログインの API の応答時間（95 パーセンタイル） | 1 秒以内（同時 10 件）。U1 の `monitoring-design.md` 3章の SLI と同じもの | Performance Validation の試験の間、配備先では 30 日 |
| トークンの更新の API の応答時間（95 パーセンタイル） | 1 秒以内（同時 10 件） | 同上 |

## 4. ログとトレース

- U2 のログ・トレースは U1 の仕組み（標準出力の JSON、トレースID、外部エクスポートの有効時の送信）に乗る。U2 に固有の集め方は持たない。
- 秘密情報（パスワード・トークン・鍵）がログ・トレース・外部エクスポートに出ないことを、U1 の確認の手順（OTLP の受け手を profile で起動する）でログインと更新を行って確かめる。
- ログの時刻は `Asia/Tokyo`（`+09:00`）で表記される（`infrastructure-specification.md` 1章）。
- ダッシュボードは配備先が決まったときに作る。U2 の面としては「ログインの応答時間・失敗の割合・ロックの件数」を候補とする。
